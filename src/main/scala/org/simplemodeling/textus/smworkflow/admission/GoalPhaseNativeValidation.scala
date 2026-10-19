package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E
import RepositorySyncNativeMergeValidation as V
import GoalPhaseNativeMetadata as N
import GoalPhaseSliceValidationPlan as L
import org.goldenport.cncf.validation.ValidationDiscoveryV1 as D

/** Selected native checks only. Executables/argv are fixed in runtime programs;
  * plan check identities select host bindings, never commands from AI. Intent
  * commits precede each external effect. Unknown results are not replayed.
  * @since   Oct.  6, 2026
  * @version Oct. 18, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeValidation {
  final case class Check(identity: String, ordinal: Int, kind: WorkflowSbtValidation.Kind = WorkflowSbtValidation.Kind.Test,
    metadata: Option[N.Profile] = None, hostIdentity: Option[String] = None,
    operation: Option[D.OperationReference] = None)
  object Check {
    import N.given
    given Encoder[Check] = Encoder.instance(value => {
      val fields = Json.obj("identity" -> value.identity.asJson, "ordinal" -> value.ordinal.asJson)
      val kind = if (value.kind == WorkflowSbtValidation.Kind.Test) fields else fields.mapObject(_.add("kind", value.kind.asJson))
      val metadata = value.metadata.fold(kind)(m => kind.mapObject(_.add("metadata", m.asJson)))
      val host = value.hostIdentity.fold(metadata)(h => metadata.mapObject(_.add("hostIdentity", h.asJson)))
      value.operation.fold(host)(o => host.mapObject(_.add("operation", o.asJson)))
    })
    given Decoder[Check] = Decoder.instance(cursor => for {
      identity <- cursor.get[String]("identity")
      ordinal <- cursor.get[Int]("ordinal")
      kind <- WorkflowSbtValidation.checkKind(cursor)
      metadata <- N.optional[N.Profile](cursor, "metadata")
      host <- N.optional[String](cursor, "hostIdentity")
      operation <- N.optional[D.OperationReference](cursor, "operation")
    } yield Check(identity, ordinal, kind, metadata, host, operation))
  }
  final case class Policy(identity: String, revision: Int, checks: Vector[Check]) {
    def validateC: Consequence[Unit] =
      if (id(identity) && revision > 0 && checks != null && checks.nonEmpty && checks.size <= 16 &&
          checks.forall(c => c != null && id(c.identity) && c.kind != null && c.ordinal > 0 && c.ordinal <= 16 &&
            c.metadata != null && c.hostIdentity != null && c.operation != null && c.hostIdentity.isEmpty && c.operation.isEmpty &&
            c.metadata.forall(p => c.kind == WorkflowSbtValidation.Kind.TestOnly && N.profileValid(p))) &&
          checks.map(_.identity).distinct.size == checks.size && checks.map(_.ordinal).distinct.size == checks.size)
        Consequence.unit else F.invalid("native validation host bindings are incomplete")
  }
  final case class Payload(sourceRevision: String, step: String, candidate: O.Snapshot,
    selected: Vector[Check], receipts: Vector[V.Receipt], outcome: Option[String], metadata: Vector[N.Batch] = Vector.empty)
  object Payload {
    given Encoder[Payload] = deriveEncoder[Payload].mapJson(value => value.mapObject(fields =>
      if (fields("metadata").exists(_.asArray.exists(_.isEmpty))) fields.remove("metadata") else fields))
    private val _decoder: Decoder[Payload] = deriveDecoder
    given Decoder[Payload] = Decoder.instance { cursor =>
      val normalized = if (cursor.downField("metadata").focus.isEmpty)
        cursor.value.mapObject(_.add("metadata", Json.arr())) else cursor.value
      _decoder.decodeJson(normalized).flatMap(value => if (_valid_payload(value)) Right(value)
        else Left(DecodingFailure("Invalid native validation payload", cursor.history)))
    }
  }
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseNativeValidationReceipt"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-validation.v2"),
      "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("native Goal validation is missing")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-native-validation.v2", (), "native Goal validation schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
      _ <- Either.cond(_valid_payload(payload), (), "native Goal validation payload is invalid")
    } yield payload
  }
  final case class Saved(record: InstanceRecord, payload: Payload)

  /** Same host-policy selection for fresh Validation and issued semantic context. */
  private[admission] def selectedChecksC(d: F.Declaration, plan: O.Plan,
    step: Option[String]): Consequence[Vector[Check]] = for {
    policy <- (if (step.isEmpty) d.phaseValidationPolicy else d.validationPolicy)
      .fold[Consequence[Policy]](F.invalid("selected host validation policy is absent"))(Consequence.success)
    _ <- policy.validateC
    identities <- step match {
      case None => Consequence.success(plan.phaseChecks)
      case Some(identity) => plan.steps.find(_.identity == identity).map(s => Consequence.success(s.checks))
        .getOrElse(F.invalid("selected validation Step is absent from the actual Plan"))
    }
    selected <- identities.foldLeft(Consequence.success(Vector.empty[Check])) { (prior, identity) => prior.flatMap { values =>
      policy.checks.find(_.identity == identity).fold[Consequence[Vector[Check]]](
        F.invalid("required plan check has no fixed host program: " + identity))(c => Consequence.success(values :+ c))
    }}
  } yield selected

  private[admission] def sourceInstructionsC(d: F.Declaration, plan: O.Plan,
    step: Option[String]): Consequence[String] = {
    val policy = if (step.isEmpty) d.phaseValidationPolicy else d.validationPolicy
    if (!policy.exists(_.checks.exists(_.metadata.exists(_.source.nonEmpty)))) Consequence.success("")
    else for {
      selected <- selectedChecksC(d, plan, step)
      owned = step.fold(plan.steps.flatMap(_.ownedPaths).distinct)(identity => plan.steps.find(_.identity == identity).get.ownedPaths)
      instructions <- L.instructionsC(selected, step.getOrElse("phase:" + plan.phase), owned, d.candidatePaths)
    } yield instructions
  }

  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State,
    from: Option[InstanceRecord] = None,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy] = None,
    recordingPaths: Vector[String] = Vector.empty): Consequence[Option[Saved]] = for {
    store <- _store_c(session, d, core, candidate, controller, _ => Consequence.unit, from, documentPolicy, recordingPaths)
    retained <- store.loadC("validation")
    actual <- retained.fold(store.pendingSelectionC)(x => store.completedC(x).map(Some(_)))
  } yield actual

  def preconditionsC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State,
    from: Option[InstanceRecord] = None,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy] = None,
    recordingPaths: Vector[String] = Vector.empty): Consequence[Unit] =
    _store_c(session, d, core, candidate, controller, _ => Consequence.unit, from, documentPolicy, recordingPaths).flatMap(_.preconditionsC)

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State, acknowledgement: String => Consequence[Unit],
    from: Option[InstanceRecord] = None,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy] = None,
    recordingPaths: Vector[String] = Vector.empty): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = for {
    store <- _store_c(session, d, core, candidate, controller, acknowledgement, from, documentPolicy, recordingPaths)
    retained <- store.loadC("validation")
    actual <- retained match {
      case Some(value) => store.completedC(value)
      case None => _checks_c(store)
    }
    wire <- F.waitC(actual.record, if (controller.cursor == controller.plan.steps.size)
      "Whole-Phase native validation" else "Ordered Step native validation", actual.payload.outcome match {
      case Some("passed") => "Actual required native checks passed. Independent review work, acceptance and Step/Phase closure remain unfinished."
      case Some("repair-required") => "Actual terminated selected Step check failure is retained for assigned semantic repair. Fresh native inspection, checks and independent review remain unfinished."
      case Some("repair-limit-reached") => "Actual terminated selected Step check failure exhausted the positive repair window. Only verified Human ContinueRepairs can renew that window; the original diagnostics remain retained."
      case Some("failed") => "Actual failed or indeterminate checks are retained at Decision. No automatic replay or review acceptance."
      case _ => "A native check intent has no recorded result. Observe or request a genuine decision; checks are not automatically replayed."
    })
  } yield wire.copy(validation = Some(NativeWorkflowOperationOutput.goalValidation(actual.payload)))

  private def _store_c(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State, acknowledgement: String => Consequence[Unit],
    from: Option[InstanceRecord], documentpolicy: Option[GoalPhaseDocumentProgress.Policy], recordingpaths: Vector[String]): Consequence[Store] = for {
    whole <- Consequence.success(controller.cursor == controller.plan.steps.size)
    _ <- documentpolicy.fold(Consequence.unit)(_.validateC(controller.plan, d.candidatePaths))
    _ <- GoalPhaseNativeFindingRecording.validatePathsC(recordingpaths, controller.plan, d.candidatePaths)
    step = if (whole) None else Some(controller.plan.steps(controller.cursor).identity)
    selected <- selectedChecksC(d, controller.plan, step)
    scope = step.getOrElse("phase:" + controller.plan.phase)
    owned = if (whole) controller.plan.steps.flatMap(_.ownedPaths).distinct else controller.plan.steps(controller.cursor).ownedPaths
    _ <- L.bindingsC(selected, scope, owned, d.candidatePaths)
    frontier = from.getOrElse(candidate.record)
    _ <- frontier.validateC
    _ <- candidate.record.validateC
    instances <- session.storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(session.current.identity)(using core.executionContext).flatMap(
      _.map(Consequence.success).getOrElse(F.invalid("native Validation instance is absent")))
    admitted <- GoalPhaseNativeHistory.prefixC(current, frontier.revision)
    prior <- GoalPhaseNativeHistory.prefixC(current, candidate.record.revision)
    _ <- if (frontier.identity == current.identity &&
        frontier.definition.workflowIdentity == current.definition.workflowIdentity &&
        frontier.definition.workflowRevision == current.definition.workflowRevision &&
        candidate.record.identity == frontier.identity &&
        candidate.record.definition.workflowIdentity == frontier.definition.workflowIdentity &&
        candidate.record.definition.workflowRevision == frontier.definition.workflowRevision &&
        admitted.currentProgression == frontier.currentProgression && admitted.suspension.isEmpty &&
        frontier.suspension.isEmpty && frontier.revision.value >= candidate.record.revision.value &&
        prior.currentProgression == candidate.record.currentProgression &&
        frontier.currentProgression.contains(ProgressionReference(if (whole) "phase.PhaseValidation" else "phase.Validation")))
      Consequence.unit else F.invalid("native checks require their actual admitted Validation source")
    _ <- if (candidate.record.currentProgression.contains(ProgressionReference(
        if (whole) "phase.PhaseValidation" else "phase.Validation")) &&
        candidate.payload.candidate.nonEmpty && candidate.payload.diff.nonEmpty) Consequence.unit
      else F.invalid("native checks require the actual saved candidate")
    prefix = if (selected.exists(_.metadata.nonEmpty)) selected.takeWhile(_.metadata.isEmpty) else Vector.empty
    initial = Payload(frontier.revision.value.toString, scope, candidate.payload.candidate.get,
      if (prefix.nonEmpty) prefix else selected, Vector.empty, None)
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    store = new Store(adapter, session, d, core, candidate, frontier, controller, selected, initial,
      None, acknowledgement, documentpolicy, recordingpaths)
    retained <- store.loadC("selection")
    actual <- retained match {
      case Some(saved) => store.retainedSelectionC(saved)
      case None if selected.exists(_.metadata.nonEmpty) && prefix.isEmpty => for {
        validation <- store.loadC("validation")
        intent <- store.loadC("check-intent-0")
        _ <- if (validation.isEmpty && intent.isEmpty) Consequence.unit
          else F.invalid("Retained metadata validation has no actual saved selection")
      } yield store
      case None => Consequence.success(store)
    }
  } yield actual

  private def _checks_c(unprepared: Store): Consequence[Saved] = for {
    prepared <- unprepared.prepareSelectionC
    (store, initial) = prepared
    current <- _check_loop_c(store, initial)
    saved <- _outcome_c(store, current)
  } yield saved

  private def _check_loop_c(store: Store, initial: Saved): Consequence[Saved] =
    store.initial.selected.zipWithIndex.foldLeft(Consequence.success(initial)) { case (prior, (check, index)) => prior.flatMap { current =>
      if (current.payload.receipts.size != index || !successful(current.payload.receipts, store.initial.selected)) Consequence.success(current)
      else store.loadC("check-result-" + index).flatMap {
        case Some(saved) =>
          if (store.compatible(saved.payload) && saved.payload.receipts.size == index + 1)
            Consequence.success(saved)
          else F.invalid("retained check result crosses its selected candidate or binding")
        case None => store.loadC("check-intent-" + index).flatMap {
          case Some(saved) =>
            if (store.compatible(saved.payload) && saved.payload.receipts.size == index) Consequence.success(saved)
            else F.invalid("retained check intent crosses its selected candidate or binding")
          case None => for {
            _ <- store.preconditionsC
            prepared <- store.saveC("check-intent-" + index, current.record, current.payload)
            _ <- store.acknowledgeC("goal-check-prepared:" + check.identity)
            actual <- if (check.operation.isEmpty) E.repositoryValidationC(store.core, check.ordinal, check.kind)
              else N.operationC(check, store.initial.metadata).flatMap(E.validationOperationC(store.core, _))
            _ <- store.acknowledgeC("goal-check-executed:" + check.identity)
            receipt <- V.sbtReceiptC(check.identity, check.kind, actual)
            saved <- store.saveC("check-result-" + index, prepared.record,
              current.payload.copy(receipts = current.payload.receipts :+ receipt))
            _ <- store.acknowledgeC("goal-check-recorded:" + check.identity)
          } yield saved
        }
      }
    }}

  private def _outcome_c(store: Store, current: Saved): Consequence[Saved] =
      if (current.payload.receipts.size == store.initial.selected.size || !successful(current.payload.receipts, store.initial.selected)) for {
        _ <- store.preconditionsC
        passed = successful(current.payload.receipts, store.initial.selected)
        inspected <- if (store.controller.stage.toString == store.validationStage &&
            store.controller.candidate.contains(store.initial.candidate)) Consequence.success(store.controller)
          else GoalPhaseNativeCandidate.observedStateC(store.controller, store.candidate.payload, store.documentPolicy, store.recordingPaths)
        outcome = if (passed) "passed" else if (!store.whole && inspected.plan.repairLimit > 0 &&
          inspected.generation < Long.MaxValue && repairable(current.payload)) {
          if (inspected.repairs < inspected.plan.repairLimit) "repair-required" else "repair-limit-reached"
        } else "failed"
        payload = current.payload.copy(outcome = Some(outcome))
        validated <- observedStateC(inspected, payload, store.reference("validation").identity)
        target = validated.stage.toString
        event = (if (store.whole) "phase-" else "") + (outcome match {
          case "passed" => "checks-passed"
          case "repair-required" => "checks-repair-required"
          case _ => "checks-failed"
        })
        _ <- if (validated.stage.toString == target && domain.statemachine.GoalPhaseRunLifecycle.permits(store.validationStage, event, target))
          Consequence.unit else F.invalid("native check outcome differs from CML/controller")
        saved <- store.saveC("validation", current.record, payload)
        _ <- store.acknowledgeC("goal-checks-admitted")
      } yield saved else Consequence.success(current)

  private final class Store(adapter: WorkflowManagedMechanicalTransition[Payload], session: F.Session,
    d: F.Declaration, val core: ActionCall.Core, val candidate: GoalPhaseNativeCandidate.Saved,
    val frontier: InstanceRecord,
    val controller: O.State, val requested: Vector[Check], val initial: Payload,
    val selectionRecord: Option[InstanceRecord], acknowledgement: String => Consequence[Unit],
    val documentPolicy: Option[GoalPhaseDocumentProgress.Policy], val recordingPaths: Vector[String]) {
    val plan = controller.plan
    val base = controller.tipSnapshot
    val whole = controller.cursor == plan.steps.size
    val checks = if (whole) plan.phaseChecks else plan.steps(controller.cursor).checks
    val ownedPaths = if (whole) plan.steps.flatMap(_.ownedPaths).distinct else plan.steps(controller.cursor).ownedPaths
    val managementPaths = (documentPolicy.toVector.flatMap(_.paths) ++ recordingPaths).distinct
    val validationStage = if (whole) "PhaseValidation" else "Validation"
    val reviewStage = if (whole) "PhaseReview" else "Review"
    private val _prefix = if (requested.exists(_.metadata.nonEmpty)) requested.takeWhile(_.metadata.isEmpty) else Vector.empty
    private def _prefix_saved_c(complete: Boolean): Consequence[Saved] =
      if (_prefix.isEmpty) Consequence.success(Saved(frontier, initial))
      else for {
        records <- _prefix.indices.foldLeft(Consequence.success(Vector.empty[(Option[Saved], Option[Saved])])) { (prior, index) => for {
          saved <- prior
          intent <- loadC("check-intent-" + index)
          result <- loadC("check-result-" + index)
        } yield saved :+ (intent, result) }
        saved <- reconcilePrefixC(frontier,
          initial.copy(selected = _prefix, receipts = Vector.empty, outcome = None, metadata = Vector.empty), records, complete)
      } yield saved
    def retainedSelectionC(saved: Saved): Consequence[Store] = for {
      prefix <- _prefix_saved_c(true)
      _ <- if (requested.exists(_.metadata.nonEmpty) && saved != null && saved.record != null && saved.payload != null &&
          saved.payload.sourceRevision == frontier.revision.value.toString && saved.payload.step == initial.step &&
          saved.payload.candidate == initial.candidate && saved.payload.receipts == prefix.payload.receipts && saved.payload.outcome.isEmpty &&
          saved.record.identity == frontier.identity && saved.record.definition == frontier.definition &&
          saved.record.revision.value == prefix.record.revision.value + 1 &&
          saved.record.history.size == prefix.record.history.size + 1 &&
          saved.record.history.dropRight(1) == prefix.record.history &&
          saved.record.currentProgression == frontier.currentProgression && saved.record.suspension.isEmpty)
        Consequence.unit else F.invalid("Retained native metadata selection crosses its Validation frontier")
      _ <- saved.record.validateC
      _ <- N.validateRetainedC(requested, saved.payload.selected, saved.payload.metadata)
    } yield new Store(adapter, session, d, core, candidate, frontier, controller, requested,
      saved.payload, Some(saved.record), acknowledgement, documentPolicy, recordingPaths)
    def prepareSelectionC: Consequence[(Store, Saved)] =
      if (selectionRecord.nonEmpty || !requested.exists(_.metadata.nonEmpty))
        Consequence.success((this, Saved(selectionRecord.getOrElse(frontier), initial)))
      else for {
        retained <- _prefix_saved_c(false)
        current <- if (_prefix.isEmpty) Consequence.success(retained) else _check_loop_c(this, retained)
        prepared <- if (_prefix.nonEmpty && (current.payload.receipts.size != _prefix.size ||
            !successful(current.payload.receipts, _prefix))) Consequence.success((this, current))
          else for {
            _ <- preconditionsC
            parent <- Option(classOf[D.Inventory].getClassLoader).map(Consequence.success)
              .getOrElse(F.invalid("Native metadata selection requires the CNCF discovery ABI ClassLoader"))
            captured <- L.captureC(requested, core, initial.step, ownedPaths, d.candidatePaths)
            selected <- N.prepareC(requested, parent, captured)
            payload = current.payload.copy(selected = selected._1, metadata = selected._2)
            saved <- saveC("selection", current.record, payload)
            store <- retainedSelectionC(saved)
          } yield (store, saved)
      } yield prepared
    def pendingSelectionC: Consequence[Option[Saved]] = selectionRecord match {
      case None if _prefix.nonEmpty => _prefix_saved_c(false).map(saved =>
        Option.when(saved.record.revision.value > frontier.revision.value)(saved))
      case None => Consequence.success(None)
      case Some(record) => initial.selected.indices.drop(initial.receipts.size).foldLeft(
          Consequence.success(Saved(record, initial))) { (prior, index) => for {
        current <- prior
        intent <- loadC("check-intent-" + index)
        result <- loadC("check-result-" + index)
        latest <- (intent.toVector ++ result.toVector).foldLeft(Consequence.success(current)) { (prior, saved) => prior.flatMap { previous =>
          val intentrevision = frontier.revision.value + 2L + index * 2L
          val expected = if (result.contains(saved)) intentrevision + 1L else intentrevision
          if (compatible(saved.payload) && saved.payload.outcome.isEmpty && saved.record.identity == frontier.identity &&
              saved.record.definition == frontier.definition && saved.record.revision.value == expected &&
              saved.record.currentProgression == frontier.currentProgression && saved.record.suspension.isEmpty &&
              saved.payload.receipts.size == (if (result.contains(saved)) index + 1 else index) &&
              saved.record.revision.value == previous.record.revision.value + 1L)
            Consequence.success(saved)
          else F.invalid("Retained metadata check prefix is not its contiguous selected input")
        }}
      } yield latest }.map(Some(_))
    }
    def reference(key: String) = WorkflowMechanicalTransition.Reference(frontier.identity.value +
      ":ordered-validation:" + frontier.revision.value + ":" + key, "1")
    def loadC(key: String): Consequence[Option[Saved]] = adapter.loadRetainedC(reference(key))(using core.executionContext).flatMap {
      case None => Consequence.success(None)
      case Some(value) =>
        val p = value.receipt.payload.value
        Consequence.success(Some(Saved(value.receipt.next, p)))
    }
    def saveC(key: String, from: InstanceRecord, payload: Payload): Consequence[Saved] = for {
      _ <- if (from.currentProgression.contains(ProgressionReference("phase." + validationStage))) Consequence.unit
        else F.invalid("check intent/result has no actual unsuspended Validation frontier")
      target = payload.outcome.map {
        case "passed" => reviewStage
        case "repair-required" => "Repair"
        case _ => "Decision"
      }.getOrElse(validationStage)
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(key), from, from.revision,
        F.history(from, "phase." + target, "ordered-validation:" + key), TypedValue(codec.typeIdentity, payload)),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield Saved(saved.receipt.next, saved.receipt.payload.value)
    def compatible(payload: Payload): Boolean =
      _compatible_payload(initial, payload)
    def completedC(saved: Saved): Consequence[Saved] = for {
      prefix <- if (selectionRecord.isEmpty && _prefix.nonEmpty) _prefix_saved_c(false).map(Option(_))
        else Consequence.success(Option.empty[Saved])
      _ <- if (saved != null && compatible(saved.payload)) Consequence.unit
        else F.invalid("retained check outcome crosses its selected candidate or binding")
      _ <- if (prefix.forall(value => saved.payload.receipts == value.payload.receipts &&
          !successful(value.payload.receipts, initial.selected) && saved.record.identity == frontier.identity &&
          saved.record.definition == frontier.definition && saved.record.revision.value == value.record.revision.value + 1 &&
          saved.record.history.size == value.record.history.size + 1 &&
          saved.record.history.dropRight(1) == value.record.history)) Consequence.unit
        else F.invalid("retained prefix outcome has no actual terminated check result")
      _ <- saved.record.validateC
      inspected <- if (controller.stage.toString == validationStage && controller.candidate.contains(initial.candidate))
        Consequence.success(controller)
        else GoalPhaseNativeCandidate.observedStateC(controller, candidate.payload, documentPolicy, recordingPaths)
      state <- observedStateC(inspected, saved.payload, reference("validation").identity)
      _ <- if (saved.record.currentProgression.contains(ProgressionReference("phase." + state.stage.toString))) Consequence.unit
        else F.invalid("retained check outcome is not its actual native successor")
    } yield saved
    def preconditionsC: Consequence[Unit] = for {
      actual <- G.observeC(core)
      tree <- G.indexTreeC(core)
      _ <- if (actual.branch == d.target.localBranch && actual.head == initial.candidate.head && actual.tree == base.tree &&
          tree == initial.candidate.tree && (if (whole) {
            if (candidate.payload.selectedPaths.isEmpty) actual.dirty.isEmpty
            else candidate.payload.selectedPaths == candidate.payload.stagedPaths &&
              candidate.payload.selectedPaths.distinct == candidate.payload.selectedPaths &&
              candidate.payload.selectedPaths.forall(managementPaths.contains) && actual.dirty.forall(p =>
                !p.unmerged && p.worktree == ' ' && p.index != '?' && p.index != '!' &&
                candidate.payload.selectedPaths.contains(p.path) &&
                p.renamedFrom.forall(candidate.payload.selectedPaths.contains))
          } else actual.dirty.forall(p => !p.unmerged && p.worktree == ' ' &&
            p.index != '?' && p.index != '!' && ownedPaths.exists(covers(_, p.path)) &&
            p.renamedFrom.forall(x => ownedPaths.exists(covers(_, x)))))) Consequence.unit
        else F.invalid("actual native candidate cannot establish the selected check preconditions")
    } yield ()
    def acknowledgeC(stage: String): Consequence[Unit] = acknowledgement(stage) match {
      case Consequence.Success(_) => Consequence.unit
      case _ => org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "GoalPhase validation acknowledgement interrupted; retained intent is not replayed: " + stage)
    }
  }
  /** Reconcile ordinary records only; this never discovers metadata or executes a check. */
  private[admission] def reconcilePrefixC(frontier: InstanceRecord, initial: Payload,
    records: Vector[(Option[Saved], Option[Saved])], complete: Boolean): Consequence[Saved] = for {
    _ <- frontier.validateC
    _ <- if (_valid_payload(initial) && frontier.suspension.isEmpty &&
        frontier.currentProgression.exists(p => p == ProgressionReference("phase.Validation") ||
          p == ProgressionReference("phase.PhaseValidation")) && initial.sourceRevision == frontier.revision.value.toString &&
        initial.receipts.isEmpty && initial.outcome.isEmpty && initial.metadata.isEmpty &&
        initial.selected.forall(c => c.metadata.isEmpty && c.hostIdentity.isEmpty && c.operation.isEmpty) &&
        records != null && records.size == initial.selected.size) Consequence.unit
      else F.invalid("Retained ordinary prefix has no exact Validation input")
    reconciled <- records.zipWithIndex.foldLeft(Consequence.success((Saved(frontier, initial), false))) {
      case (prior, ((intent, result), index)) => prior.flatMap { case (current, stopped) =>
        if (intent.isEmpty && result.isEmpty) Consequence.success((current, true))
        else if (stopped || intent.isEmpty || current.payload.receipts.size != index ||
            !successful(current.payload.receipts, initial.selected))
          F.invalid("Retained ordinary prefix has a missing or noncontiguous intent/result pair")
        else for {
          prepared <- _prefix_record_c(current, intent.get, initial, current.payload.receipts)
          actual <- result match {
            case None => Consequence.success((prepared, true))
            case Some(saved) =>
              if (saved == null || saved.payload == null || saved.payload.receipts == null ||
                  saved.payload.receipts.size != index + 1 || saved.payload.receipts.dropRight(1) != current.payload.receipts)
                F.invalid("Retained ordinary result replaces its actual prior receipts")
              else _prefix_record_c(prepared, saved, initial, saved.payload.receipts).map(value =>
                (value, !successful(value.payload.receipts, initial.selected)))
          }
        } yield actual
      }
    }
    saved = reconciled._1
    _ <- if (!complete || (saved.payload.receipts.size == initial.selected.size &&
        successful(saved.payload.receipts, initial.selected))) Consequence.unit
      else F.invalid("Metadata selection requires the actual complete successful ordinary prefix")
  } yield saved

  private def _prefix_record_c(previous: Saved, saved: Saved, initial: Payload,
    receipts: Vector[V.Receipt]): Consequence[Saved] = for {
    _ <- if (saved != null && saved.record != null && _compatible_payload(initial, saved.payload) &&
        saved.payload.outcome.isEmpty && saved.payload.receipts == receipts &&
        saved.record.identity == previous.record.identity && saved.record.definition == previous.record.definition &&
        saved.record.revision.value == previous.record.revision.value + 1 &&
        saved.record.currentProgression == previous.record.currentProgression && saved.record.suspension.isEmpty &&
        saved.record.history.size == previous.record.history.size + 1 &&
        saved.record.history.dropRight(1) == previous.record.history) Consequence.unit
      else F.invalid("Retained ordinary prefix crosses its actual source, candidate or native record")
    _ <- saved.record.validateC
  } yield saved

  private def _compatible_payload(initial: Payload, payload: Payload): Boolean =
    _valid_payload(payload) && payload.sourceRevision == initial.sourceRevision && payload.step == initial.step &&
      payload.candidate == initial.candidate && payload.selected == initial.selected && payload.metadata == initial.metadata

  private def _returned_failure(payload: Payload): Boolean =
    _valid_payload(payload) && payload.receipts.nonEmpty &&
      payload.receipts.forall(_.sbt.nonEmpty) && successful(payload.receipts.dropRight(1), payload.selected) && {
        val last = payload.receipts.last
        val facts = last.sbt.get
        (last.termination, last.exitCode, facts.outcome) match {
          case ("exited", Some(exit), WorkflowSbtValidation.Outcome.Failed) => exit != 0
          case ("indeterminate", None, WorkflowSbtValidation.Outcome.Incomplete) => true
          case ("exited", Some(0), WorkflowSbtValidation.Outcome.Unverified) => facts.kind == WorkflowSbtValidation.Kind.TestOnly
          case _ => false
        }
      }

  /** Classification of retained typed facts only; this grants no effect or retry authority. */
  private[admission] def repairable(payload: Payload): Boolean =
    _valid_payload(payload) && payload.receipts.nonEmpty &&
      payload.receipts.zip(payload.selected).forall { (receipt, check) =>
        receipt.sbt.exists(facts => facts != null && facts.valid && facts.kind == check.kind)
      } && successful(payload.receipts.dropRight(1), payload.selected) && {
        val last = payload.receipts.last
        last.termination == "exited" && last.exitCode.exists(_ != 0) &&
          last.sbt.exists(_.outcome == WorkflowSbtValidation.Outcome.Failed)
      }

  /** One owner reconstructs new and retained outcomes; old failed records stay Decision. */
  private[admission] def observedStateC(controller: O.State, payload: Payload,
    receiptIdentity: String): Consequence[O.State] = {
    if (controller == null || !_valid_payload(payload) || !_text(receiptIdentity) ||
        !(controller.stage == O.Stage.Validation || controller.stage == O.Stage.PhaseValidation) ||
        payload.step != (if (controller.cursor == controller.plan.steps.size) "phase:" + controller.plan.phase
          else controller.plan.steps(controller.cursor).identity))
      F.invalid("native check outcome has no matching selected Validation frontier")
    else {
      val required = if (controller.cursor == controller.plan.steps.size) controller.plan.phaseChecks
        else controller.plan.steps(controller.cursor).checks
      val declared = N.declaredIdentities(payload.selected)
      val full = declared == required
      val failurecoverage = full || (declared.nonEmpty && declared == required.take(declared.size) &&
        payload.selected.forall(c => c.metadata.isEmpty && c.hostIdentity.isEmpty && c.operation.isEmpty) && _returned_failure(payload))
      payload.outcome match {
      case Some("passed") if full && payload.receipts.size == payload.selected.size && successful(payload.receipts, payload.selected) =>
        controller.acceptC(O.ChecksObserved(payload.candidate, required, receiptIdentity, true))
      case Some("failed") if failurecoverage && payload.receipts.nonEmpty && !successful(payload.receipts, payload.selected) =>
        controller.acceptC(O.ChecksObserved(payload.candidate, required, receiptIdentity, false))
      case Some(outcome) if failurecoverage && repairable(payload) && controller.cursor < controller.plan.steps.size &&
          controller.plan.repairLimit > 0 && controller.generation < Long.MaxValue &&
          ((outcome == "repair-required" && controller.repairs < controller.plan.repairLimit) ||
            (outcome == "repair-limit-reached" && controller.repairs >= controller.plan.repairLimit)) =>
        val failed = payload.selected(payload.receipts.size - 1)
        val exit = payload.receipts.last.exitCode.get
        val directive = O.RepairDirective("Correct selected Step check " + failed.identity + " (" + failed.kind.wire +
          ") after actual exit " + exit,
          Vector("Correct semantic source within selected Step " + payload.step + " using the retained actual diagnostics for " +
            failed.identity + "; runtime must inspect the fresh candidate and rerun selected checks before independent review."))
        controller.acceptC(O.RepairableChecksObserved(payload.candidate, required, receiptIdentity, directive))
      case _ => F.invalid("native check outcome is incomplete or contradicts its actual selected receipts")
      }
    }
  }

  private def _valid_payload(payload: Payload): Boolean = payload != null && _text(payload.sourceRevision) &&
    _text(payload.step) && payload.candidate != null &&
    Vector(payload.candidate.head, payload.candidate.tree).forall(value => value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) &&
    payload.selected != null && payload.selected.nonEmpty && payload.selected.size <= 16 &&
    N.validateRuntimeC(payload.selected, payload.metadata).isSuccess &&
    payload.receipts != null && payload.receipts.size <= payload.selected.size && payload.outcome != null &&
    payload.outcome.forall(Set("passed", "failed", "repair-required", "repair-limit-reached")) &&
    payload.receipts.zip(payload.selected).forall { (receipt, check) =>
      receipt != null && receipt.identity == check.identity && receipt.exitCode != null && receipt.sbt != null &&
        _stored_text(receipt.stdout) && _stored_text(receipt.stderr) &&
        receipt.sbt.forall(facts => facts != null && facts.valid && facts.kind == check.kind) &&
        (receipt.sbt.nonEmpty || check.kind == WorkflowSbtValidation.Kind.Test)
    }
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty &&
    !value.exists(c => c == '\u0000' || c == '\r' || c == '\n')
  private def _stored_text(value: String): Boolean = value != null && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) &&
    value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536
  private[admission] def successful(receipts: Vector[V.Receipt], selected: Vector[Check]): Boolean =
    receipts != null && selected != null && receipts.size <= selected.size && receipts.zip(selected).forall((receipt, check) =>
      check != null && receipt != null && receipt.identity == check.identity && V.productSuccessful(receipt, check.kind))
  private def covers(owner: String, path: String): Boolean = path == owner || path.startsWith(owner + "/")
  private def id(value: String): Boolean = value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
}
