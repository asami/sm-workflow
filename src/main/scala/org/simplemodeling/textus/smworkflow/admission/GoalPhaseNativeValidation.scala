package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
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

/** Selected native checks only. Executables/argv are fixed in runtime programs;
  * plan check identities select host bindings, never commands from AI. Intent
  * commits precede each external effect. Unknown results are not replayed.
  * @since   Oct.  6, 2026
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeValidation {
  final case class Check(identity: String, ordinal: Int, kind: WorkflowSbtValidation.Kind = WorkflowSbtValidation.Kind.Test)
  object Check {
    given Encoder[Check] = Encoder.instance(value => {
      val fields = Json.obj("identity" -> value.identity.asJson, "ordinal" -> value.ordinal.asJson)
      if (value.kind == WorkflowSbtValidation.Kind.Test) fields else fields.mapObject(_.add("kind", value.kind.asJson))
    })
    given Decoder[Check] = Decoder.instance(cursor => for {
      identity <- cursor.get[String]("identity")
      ordinal <- cursor.get[Int]("ordinal")
      kind <- WorkflowSbtValidation.checkKind(cursor)
    } yield Check(identity, ordinal, kind))
  }
  final case class Policy(identity: String, revision: Int, checks: Vector[Check]) {
    def validateC: Consequence[Unit] =
      if (id(identity) && revision > 0 && checks != null && checks.nonEmpty && checks.size <= 16 &&
          checks.forall(c => c != null && id(c.identity) && c.kind != null && c.ordinal > 0 && c.ordinal <= 16) &&
          checks.map(_.identity).distinct.size == checks.size && checks.map(_.ordinal).distinct.size == checks.size)
        Consequence.unit else F.invalid("native validation host bindings are incomplete")
  }
  final case class Payload(sourceRevision: String, step: String, candidate: O.Snapshot,
    selected: Vector[Check], receipts: Vector[V.Receipt], outcome: Option[String])
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseNativeValidationReceipt"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-validation.v2"),
      "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("native Goal validation is missing")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-native-validation.v2", (), "native Goal validation schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
    } yield payload
  }
  final case class Saved(record: InstanceRecord, payload: Payload)

  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State,
    from: Option[InstanceRecord] = None,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy] = None): Consequence[Option[Saved]] = for {
    store <- _store_c(session, d, core, candidate, controller, _ => Consequence.unit, from, documentPolicy)
    retained <- store.loadC("validation")
    actual <- retained.fold(Consequence.success(Option.empty[Saved]))(x => store.completedC(x).map(Some(_)))
  } yield actual

  def preconditionsC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State,
    from: Option[InstanceRecord] = None,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy] = None): Consequence[Unit] =
    _store_c(session, d, core, candidate, controller, _ => Consequence.unit, from, documentPolicy).flatMap(_.preconditionsC)

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State, acknowledgement: String => Consequence[Unit],
    from: Option[InstanceRecord] = None,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy] = None): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = for {
    store <- _store_c(session, d, core, candidate, controller, acknowledgement, from, documentPolicy)
    retained <- store.loadC("validation")
    actual <- retained match {
      case Some(value) => store.completedC(value)
      case None => _checks_c(store)
    }
    wire <- F.waitC(actual.record, if (controller.cursor == controller.plan.steps.size)
      "Whole-Phase native validation" else "Ordered Step native validation", actual.payload.outcome match {
      case Some("passed") => "Actual required native checks passed. Independent review work, acceptance and Step/Phase closure remain unfinished."
      case Some("repair-required") => "Actual terminated selected Step check failure is retained for assigned semantic repair. Fresh native inspection, checks and independent review remain unfinished."
      case Some("failed") => "Actual failed or indeterminate checks are retained at Decision. No automatic replay or review acceptance."
      case _ => "A native check intent has no recorded result. Observe or request a genuine decision; checks are not automatically replayed."
    })
  } yield wire.copy(validation = Some(NativeWorkflowOperationOutput.goalValidation(actual.payload)))

  private def _store_c(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State, acknowledgement: String => Consequence[Unit],
    from: Option[InstanceRecord], documentpolicy: Option[GoalPhaseDocumentProgress.Policy]): Consequence[Store] = for {
    whole <- Consequence.success(controller.cursor == controller.plan.steps.size)
    _ <- documentpolicy.fold(Consequence.unit)(_.validateC(controller.plan, d.candidatePaths))
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
    policy <- (if (whole) d.phaseValidationPolicy else d.validationPolicy)
      .fold[Consequence[Policy]](F.invalid("selected host validation policy is absent"))(Consequence.success)
    _ <- policy.validateC
    checks = if (whole) controller.plan.phaseChecks else controller.plan.steps(controller.cursor).checks
    selected <- checks.foldLeft(Consequence.success(Vector.empty[Check])) { (prior, identity) => prior.flatMap { values =>
      policy.checks.find(_.identity == identity).fold[Consequence[Vector[Check]]](
        F.invalid("required plan check has no fixed host program: " + identity))(c => Consequence.success(values :+ c))
    }}
    scope = if (whole) "phase:" + controller.plan.phase else controller.plan.steps(controller.cursor).identity
    initial = Payload(frontier.revision.value.toString, scope, candidate.payload.candidate.get,
      selected, Vector.empty, None)
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
  } yield new Store(adapter, session, d, core, candidate, frontier, controller, initial, acknowledgement, documentpolicy)

  private def _checks_c(store: Store): Consequence[Saved] = {
    val initial = Saved(store.frontier, store.initial)
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
            actual <- E.repositoryValidationC(store.core, check.ordinal, check.kind)
            _ <- store.acknowledgeC("goal-check-executed:" + check.identity)
            receipt <- V.sbtReceiptC(check.identity, check.kind, actual)
            saved <- store.saveC("check-result-" + index, prepared.record,
              current.payload.copy(receipts = current.payload.receipts :+ receipt))
            _ <- store.acknowledgeC("goal-check-recorded:" + check.identity)
          } yield saved
        }
      }
    }}.flatMap { current =>
      if (current.payload.receipts.size == store.initial.selected.size || !successful(current.payload.receipts, store.initial.selected)) for {
        _ <- store.preconditionsC
        passed = successful(current.payload.receipts, store.initial.selected)
        inspected <- if (store.controller.stage.toString == store.validationStage &&
            store.controller.candidate.contains(store.initial.candidate)) Consequence.success(store.controller)
          else GoalPhaseNativeCandidate.observedStateC(store.controller, store.candidate.payload, store.documentPolicy)
        outcome = if (passed) "passed" else if (!store.whole && inspected.repairs < inspected.plan.repairLimit &&
          inspected.generation < Long.MaxValue && repairable(current.payload)) "repair-required" else "failed"
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
    }
  }

  private final class Store(adapter: WorkflowManagedMechanicalTransition[Payload], session: F.Session,
    d: F.Declaration, val core: ActionCall.Core, val candidate: GoalPhaseNativeCandidate.Saved,
    val frontier: InstanceRecord,
    val controller: O.State, val initial: Payload, acknowledgement: String => Consequence[Unit],
    val documentPolicy: Option[GoalPhaseDocumentProgress.Policy]) {
    val plan = controller.plan
    val base = controller.tipSnapshot
    val whole = controller.cursor == plan.steps.size
    val checks = if (whole) plan.phaseChecks else plan.steps(controller.cursor).checks
    val ownedPaths = if (whole) plan.steps.flatMap(_.ownedPaths).distinct else plan.steps(controller.cursor).ownedPaths
    val validationStage = if (whole) "PhaseValidation" else "Validation"
    val reviewStage = if (whole) "PhaseReview" else "Review"
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
      _valid_payload(payload) && payload.sourceRevision == initial.sourceRevision && payload.step == initial.step &&
        payload.candidate == initial.candidate && payload.selected == initial.selected &&
        payload.receipts.size <= payload.selected.size && payload.receipts.zip(payload.selected).forall((receipt, check) =>
          receipt != null && receipt.identity == check.identity && receipt.sbt != null &&
            receipt.sbt.fold(check.kind == WorkflowSbtValidation.Kind.Test)(facts =>
              facts != null && facts.valid && facts.kind == check.kind))
    def completedC(saved: Saved): Consequence[Saved] = for {
      _ <- if (saved != null && compatible(saved.payload)) Consequence.unit
        else F.invalid("retained check outcome crosses its selected candidate or binding")
      inspected <- if (controller.stage.toString == validationStage && controller.candidate.contains(initial.candidate))
        Consequence.success(controller)
        else GoalPhaseNativeCandidate.observedStateC(controller, candidate.payload, documentPolicy)
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
            else documentPolicy.exists(policy => candidate.payload.selectedPaths == candidate.payload.stagedPaths &&
              candidate.payload.selectedPaths.distinct == candidate.payload.selectedPaths &&
              candidate.payload.selectedPaths.forall(policy.paths.contains) && actual.dirty.forall(p =>
                !p.unmerged && p.worktree == ' ' && p.index != '?' && p.index != '!' &&
                candidate.payload.selectedPaths.contains(p.path) &&
                p.renamedFrom.forall(candidate.payload.selectedPaths.contains)))
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
    else payload.outcome match {
      case Some("passed") if payload.receipts.size == payload.selected.size && successful(payload.receipts, payload.selected) =>
        controller.acceptC(O.ChecksObserved(payload.candidate, payload.selected.map(_.identity), receiptIdentity, true))
      case Some("failed") if payload.receipts.nonEmpty && !successful(payload.receipts, payload.selected) =>
        controller.acceptC(O.ChecksObserved(payload.candidate, payload.selected.map(_.identity), receiptIdentity, false))
      case Some("repair-required") if repairable(payload) =>
        val failed = payload.selected(payload.receipts.size - 1)
        val exit = payload.receipts.last.exitCode.get
        val directive = O.RepairDirective("Correct selected Step check " + failed.identity + " (" + failed.kind.wire +
          ") after actual exit " + exit,
          Vector("Correct semantic source within selected Step " + payload.step + " using the retained actual diagnostics for " +
            failed.identity + "; runtime must inspect the fresh candidate and rerun selected checks before independent review."))
        controller.acceptC(O.RepairableChecksObserved(payload.candidate, payload.selected.map(_.identity), receiptIdentity, directive))
      case _ => F.invalid("native check outcome is incomplete or contradicts its actual selected receipts")
    }
  }

  private def _valid_payload(payload: Payload): Boolean = payload != null && _text(payload.sourceRevision) &&
    _text(payload.step) && payload.candidate != null &&
    Vector(payload.candidate.head, payload.candidate.tree).forall(value => value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) &&
    payload.selected != null && payload.selected.nonEmpty && payload.selected.size <= 16 &&
    payload.selected.forall(check => check != null && id(check.identity) && check.kind != null && check.ordinal > 0 && check.ordinal <= 16) &&
    payload.selected.map(_.identity).distinct.size == payload.selected.size &&
    payload.selected.map(_.ordinal).distinct.size == payload.selected.size &&
    payload.receipts != null && payload.receipts.size <= payload.selected.size && payload.outcome != null &&
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
