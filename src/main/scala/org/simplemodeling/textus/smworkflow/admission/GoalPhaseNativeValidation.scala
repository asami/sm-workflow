package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
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
  * @version Oct. 12, 2026
  */
private[smworkflow] object GoalPhaseNativeValidation {
  final case class Check(identity: String, ordinal: Int)
  final case class Policy(identity: String, revision: Int, checks: Vector[Check]) {
    def validateC: Consequence[Unit] =
      if (id(identity) && revision > 0 && checks != null && checks.nonEmpty && checks.size <= 16 &&
          checks.forall(c => c != null && id(c.identity) && c.ordinal > 0 && c.ordinal <= 16) &&
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
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State): Consequence[Option[Saved]] = for {
    store <- storeC(session, d, core, candidate, controller, _ => Consequence.unit)
    retained <- store.loadC("validation")
    actual <- retained.fold(Consequence.success(Option.empty[Saved]))(x => store.completedC(x).map(Some(_)))
  } yield actual

  def preconditionsC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State): Consequence[Unit] =
    storeC(session, d, core, candidate, controller, _ => Consequence.unit).flatMap(_.preconditionsC)

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State, acknowledgement: String => Consequence[Unit]): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = for {
    store <- storeC(session, d, core, candidate, controller, acknowledgement)
    retained <- store.loadC("validation")
    actual <- retained match {
      case Some(value) => store.completedC(value)
      case None => checksC(store)
    }
    wire <- F.waitC(actual.record, if (controller.cursor == controller.plan.steps.size)
      "Whole-Phase native validation" else "Ordered Step native validation", actual.payload.outcome match {
      case Some("passed") => "Actual required native checks passed. Independent review work, acceptance and Step/Phase closure remain unfinished."
      case Some("failed") => "Actual failed or indeterminate checks are retained at Decision. No automatic replay or review acceptance."
      case _ => "A native check intent has no recorded result. Observe or request a genuine decision; checks are not automatically replayed."
    })
  } yield wire

  private def storeC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, controller: O.State, acknowledgement: String => Consequence[Unit]): Consequence[Store] = for {
    whole <- Consequence.success(controller.cursor == controller.plan.steps.size)
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
    initial = Payload(candidate.record.revision.value.toString, scope, candidate.payload.candidate.get,
      selected, Vector.empty, None)
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
  } yield new Store(adapter, session, d, core, candidate, controller, initial, acknowledgement)

  private def checksC(store: Store): Consequence[Saved] = {
    val initial = Saved(store.candidate.record, store.initial)
    store.initial.selected.zipWithIndex.foldLeft(Consequence.success(initial)) { case (prior, (check, index)) => prior.flatMap { current =>
      if (current.payload.receipts.size != index || !successful(current.payload.receipts)) Consequence.success(current)
      else for {
        _ <- store.preconditionsC
        prepared <- store.saveC("check-intent-" + index, current.record, current.payload)
        _ <- store.acknowledgeC("goal-check-prepared:" + check.identity)
        actual <- E.repositoryValidationC(store.core, check.ordinal)
        _ <- store.acknowledgeC("goal-check-executed:" + check.identity)
        receipt <- V.receiptC(check.identity, actual)
        saved <- store.saveC("check-result-" + index, prepared.record,
          current.payload.copy(receipts = current.payload.receipts :+ receipt))
        _ <- store.acknowledgeC("goal-check-recorded:" + check.identity)
      } yield saved
    }}.flatMap { current =>
      if (current.payload.receipts.size == store.initial.selected.size || !successful(current.payload.receipts)) for {
        _ <- store.preconditionsC
        passed = successful(current.payload.receipts)
        inspected <- store.controller.acceptC(O.CandidateObserved(store.initial.candidate))
        validated <- inspected.acceptC(O.ChecksObserved(store.initial.candidate, store.checks,
          store.reference("validation").identity, passed))
        target = if (passed) store.reviewStage else "Decision"
        event = (if (store.whole) "phase-" else "") + (if (passed) "checks-passed" else "checks-failed")
        _ <- if (validated.stage.toString == target && domain.statemachine.GoalPhaseRunLifecycle.permits(store.validationStage, event, target))
          Consequence.unit else F.invalid("native check outcome differs from CML/controller")
        saved <- store.saveC("validation", current.record, current.payload.copy(outcome = Some(if (passed) "passed" else "failed")))
        _ <- store.acknowledgeC("goal-checks-admitted")
      } yield saved else Consequence.success(current)
    }
  }

  private final class Store(adapter: WorkflowManagedMechanicalTransition[Payload], session: F.Session,
    d: F.Declaration, val core: ActionCall.Core, val candidate: GoalPhaseNativeCandidate.Saved,
    val controller: O.State, val initial: Payload, acknowledgement: String => Consequence[Unit]) {
    val plan = controller.plan
    val base = controller.tipSnapshot
    val whole = controller.cursor == plan.steps.size
    val checks = if (whole) plan.phaseChecks else plan.steps(controller.cursor).checks
    val ownedPaths = if (whole) plan.steps.flatMap(_.ownedPaths).distinct else plan.steps(controller.cursor).ownedPaths
    val validationStage = if (whole) "PhaseValidation" else "Validation"
    val reviewStage = if (whole) "PhaseReview" else "Review"
    def reference(key: String) = WorkflowMechanicalTransition.Reference(candidate.record.identity.value +
      ":ordered-validation:" + candidate.record.revision.value + ":" + key, "1")
    def loadC(key: String): Consequence[Option[Saved]] = adapter.loadRetainedC(reference(key))(using core.executionContext).flatMap {
      case None => Consequence.success(None)
      case Some(value) =>
        val p = value.receipt.payload.value
        Consequence.success(Some(Saved(value.receipt.next, p)))
    }
    def saveC(key: String, from: InstanceRecord, payload: Payload): Consequence[Saved] = for {
      _ <- if (from.currentProgression.contains(ProgressionReference("phase." + validationStage))) Consequence.unit
        else F.invalid("check intent/result has no actual unsuspended Validation frontier")
      target = payload.outcome.map { case "passed" => reviewStage; case _ => "Decision" }.getOrElse(validationStage)
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(key), from, from.revision,
        F.history(from, "phase." + target, "ordered-validation:" + key), TypedValue(codec.typeIdentity, payload)),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield Saved(saved.receipt.next, saved.receipt.payload.value)
    def completedC(saved: Saved): Consequence[Saved] = {
      val p = saved.payload
      val complete = p.outcome match {
        case Some("passed") => p.receipts.size == p.selected.size && successful(p.receipts) &&
          saved.record.currentProgression.contains(ProgressionReference("phase." + reviewStage))
        case Some("failed") => p.receipts.nonEmpty && !successful(p.receipts) &&
          saved.record.currentProgression.contains(ProgressionReference("phase.Decision"))
        case _ => false
      }
      if (complete) Consequence.success(saved) else F.invalid("retained check outcome is not its actual native successor")
    }
    def preconditionsC: Consequence[Unit] = for {
      actual <- G.observeC(core)
      tree <- G.indexTreeC(core)
      _ <- if (actual.branch == d.target.localBranch && actual.head == initial.candidate.head && actual.tree == base.tree &&
          tree == initial.candidate.tree && (if (whole) actual.dirty.isEmpty else actual.dirty.forall(p => !p.unmerged && p.worktree == ' ' &&
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
  private def successful(receipts: Vector[V.Receipt]) = receipts.forall(r => r.termination == "exited" && r.exitCode.contains(0))
  private def covers(owner: String, path: String): Boolean = path == owner || path.startsWith(owner + "/")
  private def id(value: String): Boolean = value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
}
