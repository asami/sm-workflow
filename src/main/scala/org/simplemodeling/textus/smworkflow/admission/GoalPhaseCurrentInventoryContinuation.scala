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
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import GoalPhaseNativeRun as F
import GoalPhaseNativeWork as W
import GoalPhaseOrderedProgression as O
import GoalPhaseOrderedHumanDecision as H
import GoalPhasePlanRevision as R

/** Automatic inventory under the retained original-purpose execution. A decoded
  * proposal is data; only the native issue/completion walker constructs Source.
  * @since   Nov. 3, 2026
  * @version Nov. 3, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseCurrentInventoryContinuation {
  val schema = "sm-workflow.current-inventory.v1"
  val causation = "ordered-current-inventory"
  val humanCausation = "ordered-current-inventory-supersession"
  val event = "current-inventory-continued"
  val decision = "AUTO original-purpose inventory"
  val message = "The exact current-Step file inventory was appended under the retained original purpose. " +
    "Original acceptance, base, accepted Steps and repair counts remain; fresh candidate checks and independent review remain required."
  private val _namespace = ":ordered-current-inventory:"

  final class Source private[GoalPhaseCurrentInventoryContinuation](val session: F.Session,
    val from: InstanceRecord, val controller: O.State, val input: W.Request,
    val result: ContinuationResult[W.Result], val human: Option[H.Source]) {
    def frontierC: Consequence[InstanceRecord] = human.fold(Consequence.success(from))(_pending_c)
    def reference: WorkflowMechanicalTransition.Reference =
      WorkflowMechanicalTransition.Reference(from.identity.value + _namespace + from.revision.value, "1")
  }

  /** A negative assessment is an ordinary concern, not an adopted business delta. */
  private[admission] def pureProposal(controller: O.State, input: W.Request, result: W.Result): Boolean =
    controller != null && input != null && result != null && controller.stage == O.Stage.Decision &&
      controller.cursor == controller.completed.size && controller.cursor < controller.plan.steps.size &&
      controller.decisionSource.exists(Set(O.Stage.Implementation, O.Stage.Repair)) &&
      !controller.releaseRepairActive && !controller.documentDecision && !controller.policyRepairPending &&
      controller.pendingRepair.isEmpty && controller.closureIntent.isEmpty && controller.releaseCommit.isEmpty &&
      controller.phaseReviewedCandidate.isEmpty && controller.repairControl.flatMap(_.stop).isEmpty &&
      Set("ImplementStep", "RepairStep")(input.kind) && input.stepIdentity.contains(controller.step.identity) &&
      input.review.isEmpty && input.publicationFailure.isEmpty &&
      input.checkFailure.forall(failure => failure.validation.dependency.isEmpty && failure.validation.dependencySource.isEmpty) &&
      result.outcome == "decision-needed" && result.findings != null && result.findings.isEmpty &&
      _absent(result.acceptance) && _concern_free_assessment(result.fixAssessment) && _absent(result.issueReport) &&
      _absent(result.dependencyProposal) && _absent(result.dependencyAdmission) &&
      _absent(result.dependencyValidation) && _absent(result.diagnosticRepair) && _absent(result.completionConditions) &&
      _absent(result.repairFindingSources) && _absent(result.reviewObservations) &&
      result.planRevision != null && result.planRevision.exists { proposal =>
        R.validateProposalC(proposal).isSuccess && O.currentInventoryPathsC(controller.plan,
          proposal.plan.copy(effectiveAcceptance = controller.plan.effectiveAcceptance), controller.step.identity).isSuccess
      }

  private[admission] def selectC(session: F.Session, from: InstanceRecord, controller: O.State,
    last: Option[(W.Request, ContinuationResult[W.Result])], human: Option[H.Source] = None): Consequence[Option[Source]] =
    if (last == null || human == null) F.invalid("current inventory source Option is absent")
    else last match {
      case Some((input, result)) if result != null && result.result != null &&
          pureProposal(controller, input, result.result.value) => sourceC(session, from, controller, input, result, human).map(Some(_))
      case _ => Consequence.success(None)
    }

  private[admission] def sourceC(session: F.Session, from: InstanceRecord, controller: O.State,
    input: W.Request, result: ContinuationResult[W.Result], human: Option[H.Source] = None): Consequence[Source] = for {
    _ <- _check(session != null && session.storage != null && session.started != null && from != null &&
      result != null && result.result != null && human != null && pureProposal(controller, input, result.result.value),
      "source is not an exact current-Step inventory-only semantic Decision")
    _ <- W.admitSemanticResultC(input, result.result.value)
    _ <- GoalPhaseRepairProgress.retainedC(input.repairProgress, controller)
    _ <- _check(input.instanceIdentity == from.identity.value && input.selectedPlan == controller.plan &&
      input.originalBase == controller.base && input.completed == controller.completed &&
      input.generation == controller.generation && result.result.typeIdentity == W.resultCodec.typeIdentity &&
      controller.completed.map(_.step) == controller.plan.steps.take(controller.cursor).map(_.identity) &&
      controller.completed.forall(O.validStepAcceptance) &&
      W.inputCodec.decode(W.inputCodec.encode(input)).contains(input) &&
      W.resultCodec.decode(W.resultCodec.encode(result.result.value)).contains(result.result.value),
      "source crosses its actual work, original base, accepted prefix or semantic result")
    revision <- input.sourceRevision.toLongOption.filter(n => n > 0 && n.toString == input.sourceRevision)
      .map(n => Consequence.success(InstanceRevision(n))).getOrElse(F.invalid("current inventory work revision is invalid"))
    issued <- GoalPhaseNativeHistory.prefixC(session.current, revision)
    pending <- GoalPhaseNativeHistory.prefixC(session.current, revision.next)
    completed <- GoalPhaseNativeHistory.completionC(session.current, pending, result.resultReference, "Decision")
    _ <- _check(completed == from && issued.suspension.isEmpty &&
      issued.currentProgression.contains(ProgressionReference("phase." +
        (if (input.kind == "ImplementStep") "Implementation" else "Repair"))) &&
      pending.suspension.exists(_.continuationIdentity.value == result.continuationId.value) &&
      from.lifecycle == Lifecycle.Active && from.suspension.isEmpty,
      "source is not its contiguous actual native issue/suspension/completion")
    _ <- human.fold(Consequence.unit) { value => for {
      _ <- _check(value.from == from && value.controller == controller && value.result == result &&
        value.input == input.copy(issues = value.input.issues) && value.sourceHistoryRecovery.isEmpty &&
        value.dependencyInventoryReason.isEmpty && value.session.current == session.current,
        "supersession crosses the actual unanswered inventory-only question")
      _ <- _pending_c(value)
    } yield () }
  } yield new Source(session, from, controller, input, result, human)

  final case class Target(schemaVersion: String, componentInstance: String, sessionId: String,
    sourceRevision: String, frontierRevision: String, plan: O.Plan, base: O.Snapshot,
    completed: Vector[O.ClosedStep], generation: Long, repairProgress: GoalPhaseRepairProgress.Snapshot,
    requestingWork: ContextReference, requestingResult: ContextReference, stepIdentity: String,
    questionIdentity: Option[String], candidatePaths: Vector[String], appendedPaths: Vector[String], captured: R.Captured)
  final case class Payload(target: Target, decision: String, participant: String, rationale: String)
  object Payload {
    given Encoder[Payload] = deriveEncoder
    private val _decoder: Decoder[Payload] = deriveDecoder
    given Decoder[Payload] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (value != null && value.decision == decision && _exact(value.participant) &&
          targetValid(value.target) && value.rationale == value.target.captured.proposal.reason && cursor.value == value.asJson) Right(value)
      else Left(DecodingFailure("current inventory receipt is incomplete or crossed", cursor.history))))
  }
  val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseCurrentInventoryContinuationReceipt"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> schema.asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("current inventory receipt is absent")
      _ <- Either.cond(wire.asObject.exists(_.keys.toSet == Set("schemaVersion", "payload")) &&
        wire.hcursor.get[String]("schemaVersion").contains(schema), (), "current inventory receipt schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
    } yield payload
  }
  final case class Saved(record: InstanceRecord, controller: O.State, payload: Payload)

  def targetValid(value: Target): Boolean = value != null && value.schemaVersion == schema &&
    Vector(value.componentInstance, value.sessionId, value.stepIdentity).forall(_exact) &&
    _revision(value.sourceRevision) && _revision(value.frontierRevision) &&
    O.validatePlanC(value.plan).isSuccess && GoalPhaseAcceptanceBoundary.savedPlanC(value.plan).isSuccess &&
    value.base != null && Vector(value.base.head, value.base.tree).forall(value =>
      value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) &&
    value.completed != null && value.completed.forall(O.validStepAcceptance) &&
    value.completed.map(_.step) == value.plan.steps.take(value.completed.size).map(_.identity) &&
    value.plan.steps.lift(value.completed.size).exists(_.identity == value.stepIdentity) &&
    value.generation > 0 && value.generation < Long.MaxValue &&
    GoalPhaseRepairProgress.validateOptionC(Some(value.repairProgress)).isSuccess &&
    _reference(value.requestingWork) && _reference(value.requestingResult) && _revision(value.requestingWork.revision) &&
    Set(":ordered-implementation:", ":ordered-repair:").exists(kind =>
      value.requestingWork.identity == value.sessionId + kind + value.requestingWork.revision) &&
    BigInt(value.sourceRevision) == BigInt(value.requestingWork.revision) + 2 &&
    value.questionIdentity != null && value.questionIdentity.forall(_ == value.sessionId + ":ordered-human:" + value.sourceRevision) &&
    BigInt(value.frontierRevision) == BigInt(value.sourceRevision) + (if (value.questionIdentity.nonEmpty) 1 else 0) &&
    _paths(value.candidatePaths) && value.appendedPaths != null &&
    R.validateCapturedC(value.captured).isSuccess && value.captured.revisionScope.isEmpty &&
    O.currentInventoryPathsC(value.plan, value.captured.plan, value.stepIdentity).toOption.contains(value.appendedPaths) &&
    value.appendedPaths.forall(value.candidatePaths.contains)

  private[admission] def targetForC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Target] = for {
    _ <- sourceC(source.session, source.from, source.controller, source.input, source.result, source.human)
    proposal <- source.result.result.value.planRevision.map(Consequence.success).getOrElse(F.invalid("inventory proposal is absent"))
    captured = R.Captured(proposal, proposal.plan.copy(effectiveAcceptance = source.controller.plan.effectiveAcceptance), source.input.documents)
    _ <- R.validateCapturedC(captured)
    added <- O.currentInventoryPathsC(source.controller.plan, captured.plan, source.controller.step.identity)
    _ <- _check(d.input.repositoryIdentity == source.controller.plan.repository && _paths(d.candidatePaths) &&
      added.forall(d.candidatePaths.contains), "host capability does not cover every selected literal file")
    frontier <- source.frontierC
    target = Target(schema, core.component.get.core.instanceId.canonicalKey, source.from.identity.value,
      source.from.revision.value.toString, frontier.revision.value.toString, source.controller.plan, source.controller.base,
      source.controller.completed, source.controller.generation, GoalPhaseRepairProgress.snapshot(source.controller),
      ContextReference(source.result.continuationId.value, source.input.sourceRevision), source.result.resultReference,
      source.controller.step.identity, source.human.map(H.identity(_).value), d.candidatePaths, added, captured)
    _ <- _check(targetValid(target), "captured target is invalid")
  } yield target

  private[admission] def advanceC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Saved] = for {
    target <- targetForC(source, d, core)
    frontier <- source.frontierC
    _ <- _check(frontier == source.session.current, "source is stale or an old work/answer remains ongoing")
    _ <- source.controller.adoptCurrentInventoryRevisionC(target.captured.plan, source.result.resultReference.identity)
    payload = Payload(target, decision, source.session.participantIdentity, target.captured.proposal.reason)
    _ <- _check(domain.statemachine.GoalPhaseRunLifecycle.permits("Decision", event, "Implementation"),
      "CML current inventory continuation edge is absent")
    next <- source.human match {
      case None => for {
        adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
        actual <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, source.reference, frontier,
          frontier.revision, F.history(frontier, "phase.Implementation", causation), TypedValue(codec.typeIdentity, payload)),
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
      } yield actual.receipt.next
      case Some(human) => for {
        _ <- _question_c(source, core)
        adapter <- source.session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec, codec)
        _ <- adapter.executeC(H.identity(human), frontier.revision, frontier,
          F.history(frontier, "phase.Implementation", humanCausation), TypedValue(codec.typeIdentity, payload))(using core.executionContext)
        // The adapter persists the successor; restoration authenticates it below.
        next <- frontier.appendC(frontier.revision, F.history(frontier, "phase.Implementation", humanCausation))
      } yield next
    }
    saved <- _reconstruct_c(source, d, core, payload, next, fresh = true)
  } yield saved

  private[admission] def retainedC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Option[Saved]] =
    source.human match {
      case None => for {
        adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
        retained <- adapter.loadRetainedC(source.reference)(using core.executionContext)
        saved <- retained.fold(Consequence.success(Option.empty[Saved]))(value =>
          _reconstruct_c(source, d, core, value.receipt.payload.value, value.receipt.next).map(Some(_)))
      } yield saved
      case Some(human) => for {
        adapter <- source.session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec, codec)
        retained <- adapter.loadRetainedC(H.identity(human), source.session.current.identity)(using core.executionContext)
        saved <- retained.fold(Consequence.success(Option.empty[Saved])) { value => for {
          next <- GoalPhaseNativeHistory.prefixC(source.session.current, value.receipt.nextRevision)
          saved <- _reconstruct_c(source, d, core, value.receipt.payload, next)
        } yield Some(saved) }
      } yield saved
    }

  private def _reconstruct_c(source: Source, d: F.Declaration, core: ActionCall.Core,
    value: Payload, next: InstanceRecord, fresh: Boolean = false): Consequence[Saved] = for {
    paths <- GoalPhaseRepairInventoryContinuation.retainedCandidatePathsC(value.target.candidatePaths, d.candidatePaths)
    expected <- targetForC(source, d.copy(candidatePaths = paths), core)
    _ <- _check(value.target == expected && value.decision == decision &&
      value.participant == source.session.participantIdentity && value.rationale == expected.captured.proposal.reason,
      "receipt changes its actual semantic proposal, participant or original-purpose lineage")
    _ <- source.human.fold(Consequence.unit)(_ => _question_c(source, core))
    frontier <- source.frontierC
    actual <- frontier.appendC(frontier.revision, F.history(frontier, "phase.Implementation",
      if (source.human.isEmpty) causation else humanCausation))
    _ <- _check(actual == next && next.revision == frontier.revision.next, "receipt is not its adjacent native successor")
    _ <- if (fresh) _check(source.session.current == frontier, "fresh source omits later history")
      else GoalPhaseNativeHistory.prefixC(source.session.current, next.revision).flatMap(retained =>
        _check(retained == next, "successor is outside actual native history"))
    state <- source.controller.adoptCurrentInventoryRevisionC(expected.captured.plan, source.result.resultReference.identity)
  } yield Saved(next, state, value)

  private def _question_c(source: Source, core: ActionCall.Core): Consequence[Unit] = for {
    human <- source.human.map(Consequence.success).getOrElse(F.invalid("inventory question is absent"))
    adapter <- source.session.storage.bindDecisionC(H.completionOperation, H.inputCodec)
    question <- adapter.loadC(H.identity(human))(using core.executionContext).flatMap(_.map(Consequence.success)
      .getOrElse(F.invalid("inventory has no actual issued question")))
    input <- NativeWorkflowOperationOutput.decisionInputC(question)
    _ <- _check(input.work == human.input && input.requestingResult == source.result.resultReference &&
      input.result == source.result.result.value && input.sourceHistoryRecovery.isEmpty &&
      input.planRevision.exists(_.proposal == source.result.result.value.planRevision.get),
      "question includes another judgment or differs from its actual inventory proposal")
  } yield ()

  private def _pending_c(human: H.Source): Consequence[InstanceRecord] = for {
    following <- GoalPhaseNativeHistory.nextC(human.session.current, human.from)
    entry <- following match {
      case Some(value) if value.revision == human.from.revision.next && value.suspension.exists(boundary =>
        boundary.continuationIdentity.value == H.identity(human).value &&
        boundary.completion.value == "goal-phase-ordered-human-completion-v1") => Consequence.success(value)
      case _ => F.invalid("inventory supersession requires its exact adjacent ordinary Human suspension")
    }
    pending <- GoalPhaseNativeHistory.prefixC(human.session.current, entry.revision)
  } yield pending

  /** Data bootstrap only; the sole native walker subsequently authenticates the
    * complete source, semantic pair and successor before executable binding.
    */
  private[admission] def runtimeEntryPathsC(session: F.Session, paths: Vector[String], entry: HistoryEntry,
    core: ActionCall.Core): Consequence[Vector[String]] = for {
    before <- GoalPhaseNativeHistory.prefixC(session.current, InstanceRevision(entry.revision.value - 1))
    value <- if (entry.causation.value == causation) for {
      adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
      retained <- adapter.loadRetainedC(WorkflowMechanicalTransition.Reference(
        session.current.identity.value + _namespace + before.revision.value, "1"))(using core.executionContext)
      receipt <- retained.map(Consequence.success).getOrElse(F.invalid("runtime current inventory receipt is absent"))
      _ <- _check(receipt.receipt.next.revision == entry.revision, "runtime current inventory crosses its successor")
    } yield receipt.receipt.payload.value
    else for {
      identity <- before.suspension.map(value => Consequence.success(org.goldenport.cncf.workflow.ContinuationIdentity(value.continuationIdentity.value)))
        .getOrElse(F.invalid("runtime inventory has no original Human suspension"))
      adapter <- session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec, codec)
      retained <- adapter.loadRetainedC(identity, session.current.identity)(using core.executionContext)
      receipt <- retained.map(Consequence.success).getOrElse(F.invalid("runtime inventory supersession receipt is absent"))
      _ <- _check(receipt.receipt.nextRevision == entry.revision, "runtime inventory supersession crosses its successor")
    } yield receipt.receipt.payload
    _ <- _check(targetValid(value.target) && value.target.sessionId == session.current.identity.value,
      "runtime inventory belongs to another native Session")
    _ <- GoalPhaseRepairInventoryContinuation.retainedCandidatePathsC(paths, value.target.candidatePaths)
  } yield value.target.candidatePaths

  private def _paths(values: Vector[String]): Boolean = values != null && values.nonEmpty &&
    values.distinct == values && values.forall(value => _exact(value) && RepositorySyncNativeConflictInput.relative(value) &&
      !value.exists(c => "*?[]{}".contains(c)))
  private def _absent(value: Option[?]): Boolean = value != null && value.isEmpty
  private def _concern_free_assessment(value: Option[GoalPhaseFixAssessment.Report]): Boolean =
    value != null && value.forall(report => GoalPhaseFixAssessment.validateC(report).isSuccess &&
      !report.change.requirementChanged && !report.change.contractChanged && report.convergence.status == "PROGRESSING")
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _reference(value: ContextReference): Boolean = value != null && _exact(value.identity) && _exact(value.revision)
  private def _exact(value: String): Boolean = GoalPhasePurposeRepair.text(value) && value.trim == value && !value.exists(_.isControl)
  private def _check(valid: Boolean, reason: String): Consequence[Unit] =
    if (valid) Consequence.unit else F.invalid("Current inventory: " + reason)
}
