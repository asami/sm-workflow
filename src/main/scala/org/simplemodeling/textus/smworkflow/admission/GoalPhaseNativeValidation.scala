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
  * @version Oct. 19, 2026
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
    selected: Vector[Check], receipts: Vector[V.Receipt], outcome: Option[String], metadata: Vector[N.Batch] = Vector.empty,
    stopped: Vector[V.Receipt] = Vector.empty)
  object Payload {
    given Encoder[Payload] = deriveEncoder[Payload].mapJson(value => value.mapObject(fields =>
      Vector("metadata", "stopped").foldLeft(fields) { (prior, key) =>
        if (prior(key).exists(_.asArray.exists(_.isEmpty))) prior.remove(key) else prior
      }))
    private val _decoder: Decoder[Payload] = deriveDecoder
    given Decoder[Payload] = Decoder.instance { cursor =>
      val normalized = Vector("metadata", "stopped").foldLeft(cursor.value) { (prior, key) =>
        if (cursor.downField(key).focus.isEmpty) prior.mapObject(_.add(key, Json.arr())) else prior
      }
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
  private[admission] final case class Attempt(intent: Option[Saved], result: Option[Saved])
  private[admission] final case class CheckProgress(saved: Saved, nextAttempt: Option[Int])

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
      case _ if continuable(actual) && actual.payload.stopped.lastOption.exists(r =>
          actual.payload.selected.lift(actual.payload.receipts.size).exists(_.identity == r.identity)) =>
        "The selected native check is confirmed not-executed; no product result exists. After preparation correction, the next ordinary Advance uses the standing selected scope."
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
        admitted == frontier && admitted.suspension.isEmpty &&
        frontier.suspension.isEmpty && frontier.revision.value >= candidate.record.revision.value &&
        prior == candidate.record &&
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
    _ <- store.pendingSelectionC
    current <- _check_loop_c(store, initial)
    selected <- if (store._selection_pending && current.payload.receipts.size == store.initial.selected.size &&
        successful(current.payload.receipts, store.initial.selected)) for {
      prepared <- store.prepareSelectionC
      (selectedstore, source) = prepared
      checked <- _check_loop_c(selectedstore, source)
    } yield (selectedstore, checked) else Consequence.success((store, current))
    saved <- _outcome_c(selected._1, selected._2)
  } yield saved

  private def _check_loop_c(store: Store, initial: Saved): Consequence[Saved] =
    store.initial.selected.zipWithIndex.foldLeft(Consequence.success(initial)) { case (prior, (check, index)) => prior.flatMap { current =>
      if (current.payload.receipts.size != index || !successful(current.payload.receipts, store.initial.selected)) Consequence.success(current)
      else store._check_progress_c(current, index).flatMap { progress => progress.nextAttempt match {
          case None => Consequence.success(progress.saved)
          case Some(attempt) => for {
            _ <- store.preconditionsC
            prepared <- store.saveC(_attempt_key("intent", index, attempt), progress.saved.record, progress.saved.payload)
            _ <- store.acknowledgeC("goal-check-prepared:" + check.identity)
            actual <- if (check.operation.isEmpty) E.repositoryValidationC(store.core, check.ordinal, check.kind)
              else N.operationC(check, store.initial.metadata).flatMap(E.validationOperationC(store.core, _))
            _ <- store.acknowledgeC("goal-check-executed:" + check.identity)
            receipt <- V.sbtReceiptC(check.identity, check.kind, actual)
            payload = if (_not_executed(receipt)) progress.saved.payload.copy(stopped = progress.saved.payload.stopped :+ receipt)
              else progress.saved.payload.copy(receipts = progress.saved.payload.receipts :+ receipt)
            saved <- store.saveC(_attempt_key("result", index, attempt), prepared.record, payload)
            _ <- store.acknowledgeC("goal-check-recorded:" + check.identity)
          } yield saved
      }}
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
    def _selection_pending: Boolean = selectionRecord.isEmpty && requested.exists(_.metadata.nonEmpty)
    private def _prefix_saved_c(complete: Boolean): Consequence[Saved] =
      if (_prefix.isEmpty) Consequence.success(Saved(frontier, initial))
      else for {
        saved <- _chain_saved_c(Saved(frontier, initial.copy(selected = _prefix, receipts = Vector.empty,
          outcome = None, metadata = Vector.empty, stopped = Vector.empty)))
        _ <- if (!complete || (saved.payload.receipts.size == _prefix.size && successful(saved.payload.receipts, _prefix)))
          Consequence.unit else F.invalid("Metadata selection requires the actual complete successful ordinary prefix")
      } yield saved
    def retainedSelectionC(saved: Saved): Consequence[Store] = for {
      prefix <- _prefix_saved_c(true)
      _ <- if (requested.exists(_.metadata.nonEmpty) && saved != null && saved.record != null && saved.payload != null &&
          saved.payload.sourceRevision == frontier.revision.value.toString && saved.payload.step == initial.step &&
          saved.payload.candidate == initial.candidate && saved.payload.receipts == prefix.payload.receipts &&
          saved.payload.stopped == prefix.payload.stopped && saved.payload.outcome.isEmpty)
        Consequence.unit else F.invalid("Retained native metadata selection crosses its Validation frontier")
      _ <- _adjacent_record_c(prefix.record, saved.record, "ordered-validation:selection", frontier.currentProgression)
      _ <- N.validateRetainedC(requested, saved.payload.selected, saved.payload.metadata)
    } yield new Store(adapter, session, d, core, candidate, frontier, controller, requested,
      saved.payload, Some(saved.record), acknowledgement, documentPolicy, recordingPaths)
    def prepareSelectionC: Consequence[(Store, Saved)] =
      if (selectionRecord.nonEmpty || !requested.exists(_.metadata.nonEmpty))
        Consequence.success((this, Saved(selectionRecord.getOrElse(frontier), initial)))
      else for {
        retained <- _prefix_saved_c(false)
        _ <- requested.indices.drop(_prefix.size).foldLeft(Consequence.unit) { (prior, index) => for {
          _ <- prior
          intent <- loadC(_attempt_key("intent", index, 0))
          result <- loadC(_attempt_key("result", index, 0))
          _ <- if (intent.isEmpty && result.isEmpty) Consequence.unit
            else F.invalid("Metadata checks precede their actual retained selection")
        } yield () }
        prepared <- if (_prefix.nonEmpty && (retained.payload.receipts.size != _prefix.size ||
            !successful(retained.payload.receipts, _prefix))) Consequence.success((this, Saved(frontier, initial)))
          else for {
            _ <- _latest_c(retained)
            _ <- preconditionsC
            parent <- Option(classOf[D.Inventory].getClassLoader).map(Consequence.success)
              .getOrElse(F.invalid("Native metadata selection requires the CNCF discovery ABI ClassLoader"))
            captured <- L.captureC(requested, core, initial.step, ownedPaths, d.candidatePaths)
            selected <- N.prepareC(requested, parent, captured)
            payload = retained.payload.copy(selected = selected._1, metadata = selected._2)
            saved <- saveC("selection", retained.record, payload)
            store <- retainedSelectionC(saved)
          } yield (store, saved)
      } yield prepared
    private def _chain_saved_c(source: Saved): Consequence[Saved] =
      source.payload.selected.indices.drop(source.payload.receipts.size).foldLeft(Consequence.success(source)) { (prior, index) =>
        prior.flatMap { current =>
          if (current.payload.receipts.size == index && successful(current.payload.receipts, source.payload.selected))
            _check_progress_c(current, index, source.payload).map(_.saved)
          else for {
            intent <- loadC(_attempt_key("intent", index, 0))
            result <- loadC(_attempt_key("result", index, 0))
            _ <- if (intent.isEmpty && result.isEmpty) Consequence.unit
              else F.invalid("Retained checks have a future noncontiguous original attempt")
          } yield current
        }
      }
    def _check_progress_c(previous: Saved, index: Int, selection: Payload = initial): Consequence[CheckProgress] = {
      def _load_(attempt: Int, attempts: Vector[Attempt]): Consequence[CheckProgress] = for {
        intent <- loadC(_attempt_key("intent", index, attempt))
        result <- loadC(_attempt_key("result", index, attempt))
        pairs = attempts :+ Attempt(intent, result)
        progress <- reconcileAttemptsC(previous, selection, index, pairs)
        latest <- if (intent.nonEmpty && result.nonEmpty && progress.nextAttempt.nonEmpty)
          _load_(progress.nextAttempt.get, pairs) else Consequence.success(progress)
      } yield latest
      _load_(0, Vector.empty)
    }
    def pendingSelectionC: Consequence[Option[Saved]] =
      for {
        saved <- _chain_saved_c(Saved(selectionRecord.getOrElse(frontier), initial))
        _ <- _latest_c(saved)
      } yield Option.when(saved.record.revision.value > frontier.revision.value)(saved)
    private def _actual_record_c: Consequence[InstanceRecord] = for {
      instances <- session.storage.bindInstancesC()(using core.executionContext)
      current <- instances.loadC(frontier.identity)(using core.executionContext).flatMap(
        _.map(Consequence.success).getOrElse(F.invalid("Retained validation instance is absent")))
      _ <- current.validateC
    } yield current
    private def _latest_c(saved: Saved): Consequence[Unit] = _actual_record_c.flatMap { current =>
      if (current == saved.record) Consequence.unit
      else F.invalid("Retained validation chain omits an actual later native record")
    }
    def reference(key: String) = WorkflowMechanicalTransition.Reference(frontier.identity.value +
      ":ordered-validation:" + frontier.revision.value + ":" + key, "1")
    def loadC(key: String): Consequence[Option[Saved]] = adapter.loadRetainedC(reference(key))(using core.executionContext).flatMap {
      case None => Consequence.success(None)
      case Some(value) =>
        val p = value.receipt.payload.value
        for {
          current <- _actual_record_c
          admitted <- GoalPhaseNativeHistory.prefixC(current, value.receipt.next.revision)
          _ <- if (admitted == value.receipt.next && admitted.identity == frontier.identity &&
              admitted.definition == frontier.definition) Consequence.unit
            else F.invalid("Retained validation record is not its complete actual history prefix")
        } yield Some(Saved(value.receipt.next, p))
    }
    def saveC(key: String, from: InstanceRecord, payload: Payload): Consequence[Saved] = for {
      _ <- if (_valid_payload(payload) && from.suspension.isEmpty &&
          from.currentProgression.contains(ProgressionReference("phase." + validationStage))) Consequence.unit
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
      prefix <- _chain_saved_c(Saved(selectionRecord.getOrElse(frontier), initial))
      _ <- if (saved != null && compatible(saved.payload)) Consequence.unit
        else F.invalid("retained check outcome crosses its selected candidate or binding")
      _ <- if (saved.payload.receipts == prefix.payload.receipts && saved.payload.stopped == prefix.payload.stopped &&
          (prefix.payload.receipts.size == initial.selected.size || !successful(prefix.payload.receipts, initial.selected)))
        Consequence.unit else F.invalid("retained outcome has no actual complete or failed check chain")
      inspected <- if (controller.stage.toString == validationStage && controller.candidate.contains(initial.candidate))
        Consequence.success(controller)
        else GoalPhaseNativeCandidate.observedStateC(controller, candidate.payload, documentPolicy, recordingPaths)
      state <- observedStateC(inspected, saved.payload, reference("validation").identity)
      _ <- _adjacent_record_c(prefix.record, saved.record, "ordered-validation:validation",
        Some(ProgressionReference("phase." + state.stage.toString)))
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
  /** Reconcile actual immutable attempts only; missing results never establish execution facts. */
  private[admission] def reconcileAttemptsC(previous: Saved, initial: Payload, index: Int,
    attempts: Vector[Attempt]): Consequence[CheckProgress] = for {
    _ <- if (previous != null && previous.record != null && _valid_payload(initial) &&
        _compatible_payload(initial, previous.payload) && initial.outcome.isEmpty && previous.payload.outcome.isEmpty &&
        index >= 0 && index < initial.selected.size && previous.payload.receipts.size == index &&
        successful(previous.payload.receipts, initial.selected) && initial.receipts.size <= index &&
        previous.payload.receipts.take(initial.receipts.size) == initial.receipts &&
        previous.payload.stopped.take(initial.stopped.size) == initial.stopped &&
        previous.payload.stopped.forall(_.identity != initial.selected(index).identity) &&
        previous.record.suspension.isEmpty && previous.record.currentProgression.exists(p =>
          p == ProgressionReference("phase.Validation") || p == ProgressionReference("phase.PhaseValidation")) &&
        initial.sourceRevision.toLongOption.exists(v => v > 0 && v.toString == initial.sourceRevision &&
          v <= previous.record.revision.value) &&
        attempts != null) Consequence.unit else F.invalid("Retained attempts have no exact selected Validation input")
    _ <- previous.record.validateC
    progress <- attempts.zipWithIndex.foldLeft(Consequence.success(CheckProgress(previous, Some(0)))) {
      case (prior, (attempt, ordinal)) => prior.flatMap { current =>
        if (attempt == null || attempt.intent == null || attempt.result == null || !current.nextAttempt.contains(ordinal))
          F.invalid("Retained check attempts follow an unresolved or completed attempt")
        else attempt match {
          case Attempt(None, None) =>
            if (ordinal == attempts.size - 1) Consequence.success(current)
            else F.invalid("Retained check attempts have a missing pair before a later attempt")
          case Attempt(None, Some(_)) => F.invalid("Retained check result has no matching intent")
          case Attempt(Some(intent), result) => for {
            _ <- if (intent != null && intent.payload == current.saved.payload) Consequence.unit
              else F.invalid("Retained intent changes its actual prior payload")
            _ <- _adjacent_record_c(current.saved.record, intent.record,
              "ordered-validation:" + _attempt_key("intent", index, ordinal), current.saved.record.currentProgression)
            latest <- result match {
              case None => Consequence.success(CheckProgress(intent, None))
              case Some(saved) => for {
                _ <- if (saved != null && _compatible_payload(initial, saved.payload) && saved.payload.outcome.isEmpty)
                  Consequence.unit else F.invalid("Retained result crosses its selected metadata or candidate")
                _ <- _adjacent_record_c(intent.record, saved.record,
                  "ordered-validation:" + _attempt_key("result", index, ordinal), intent.record.currentProgression)
                stopped = saved.payload.stopped.size == current.saved.payload.stopped.size + 1 &&
                  saved.payload.stopped.dropRight(1) == current.saved.payload.stopped &&
                  saved.payload.receipts == current.saved.payload.receipts &&
                  saved.payload.stopped.lastOption.exists(r => r.identity == initial.selected(index).identity && _not_executed(r))
                completed = saved.payload.receipts.size == index + 1 &&
                  saved.payload.receipts.dropRight(1) == current.saved.payload.receipts &&
                  saved.payload.stopped == current.saved.payload.stopped
                _ <- if (stopped != completed) Consequence.unit
                  else F.invalid("Retained result rewrites its prefix or contradicts its single terminal receipt")
                next <- if (!stopped) Consequence.success(Option.empty[Int])
                  else if (ordinal == Int.MaxValue) F.invalid("Retained check attempt ordinal is exhausted")
                  else Consequence.success(Some(ordinal + 1))
              } yield CheckProgress(saved, next)
            }
          } yield latest
        }
      }
    }
  } yield progress

  private def _attempt_key(role: String, index: Int, attempt: Int): String =
    "check-" + role + "-" + index + (if (attempt == 0) "" else "-retry-" + attempt)

  private def _adjacent_record_c(previous: InstanceRecord, saved: InstanceRecord, cause: String,
    progression: Option[ProgressionReference]): Consequence[Unit] = for {
    _ <- if (previous != null && saved != null && previous.revision.value < Long.MaxValue &&
        saved.identity == previous.identity && saved.definition == previous.definition &&
        saved.revision.value == previous.revision.value + 1 && saved.history.size == previous.history.size + 1 &&
        saved.history.dropRight(1) == previous.history && saved.suspension.isEmpty &&
        saved.currentProgression == progression && saved.causation == CausationReference(cause) &&
        saved.lifecycle == Lifecycle.Active && saved.correlation == previous.correlation &&
        saved.derivedCompositeOccurrence == previous.derivedCompositeOccurrence &&
        saved.committedPredecessor == previous.committedPredecessor) Consequence.unit
      else F.invalid("Retained validation record is not its canonical contiguous native successor")
    _ <- previous.validateC
    _ <- saved.validateC
  } yield ()

  /** The Store first admits actual history. This classification supplies no execution authority. */
  private[admission] def continuable(saved: Saved): Boolean =
    saved != null && saved.record != null && _valid_payload(saved.payload) && saved.payload.outcome.isEmpty &&
      saved.record.validateC.isSuccess && saved.record.suspension.isEmpty &&
      saved.payload.sourceRevision.toLongOption.exists(v => v > 0 && v.toString == saved.payload.sourceRevision &&
        v < saved.record.revision.value && saved.record.history.exists(entry => entry.revision.value == v &&
          entry.currentProgression == saved.record.currentProgression && entry.suspension.isEmpty)) &&
      saved.record.currentProgression.exists(p => p == ProgressionReference("phase.Validation") ||
        p == ProgressionReference("phase.PhaseValidation")) && successful(saved.payload.receipts, saved.payload.selected) && {
        val payload = saved.payload
        val index = if (payload.stopped.lastOption.exists(r =>
            payload.selected.lift(payload.receipts.size).exists(_.identity == r.identity))) payload.receipts.size
          else payload.receipts.size - 1
        if (index < 0 || index >= payload.selected.size) false
        else {
          val count = payload.stopped.count(_.identity == payload.selected(index).identity)
          val attempt = if (index == payload.receipts.size) count - 1 else count
          attempt >= 0 && saved.record.causation.value == "ordered-validation:" + _attempt_key("result", index, attempt)
        }
      }

  private def _not_executed(receipt: V.Receipt): Boolean = receipt != null && receipt.termination == "not-started" &&
    receipt.exitCode != null && receipt.exitCode.isEmpty && receipt.stdout == "" && receipt.stderr == "" &&
    receipt.sbt != null && receipt.sbt.exists(f => f != null && f.valid && f.outcome == WorkflowSbtValidation.Outcome.NotExecuted)

  /** Reconcile ordinary records only; this never discovers metadata or executes a check. */
  private[admission] def reconcilePrefixC(frontier: InstanceRecord, initial: Payload,
    records: Vector[(Option[Saved], Option[Saved])], complete: Boolean): Consequence[Saved] = for {
    _ <- frontier.validateC
    _ <- if (_valid_payload(initial) && frontier.suspension.isEmpty &&
        frontier.currentProgression.exists(p => p == ProgressionReference("phase.Validation") ||
          p == ProgressionReference("phase.PhaseValidation")) && initial.sourceRevision == frontier.revision.value.toString &&
        initial.receipts.isEmpty && initial.stopped.isEmpty && initial.outcome.isEmpty && initial.metadata.isEmpty &&
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
        saved.payload.outcome.isEmpty && saved.payload.receipts == receipts && saved.payload.stopped == previous.payload.stopped &&
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
        receipt.termination != "not-started" &&
        receipt.sbt.forall(facts => facts != null && facts.valid && facts.kind == check.kind &&
          facts.outcome != WorkflowSbtValidation.Outcome.NotExecuted) &&
        (receipt.sbt.nonEmpty || check.kind == WorkflowSbtValidation.Kind.Test)
    } && payload.stopped != null && {
      val indices = payload.stopped.map(receipt =>
        if (receipt == null) -1 else payload.selected.indexWhere(_.identity == receipt.identity))
      indices.forall(index => index >= 0 && index <= payload.receipts.size &&
        (index < payload.receipts.size || (payload.outcome.isEmpty && successful(payload.receipts, payload.selected)))) &&
        indices.sliding(2).forall(pair => pair.size < 2 || pair.head <= pair.last) &&
        payload.stopped.zip(indices).forall { (receipt, index) => _not_executed(receipt) &&
          receipt.sbt.exists(_.kind == payload.selected(index).kind) }
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
