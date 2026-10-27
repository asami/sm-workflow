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
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseNativeWork as W
import GoalPhaseOrderedProgression as O
import GoalPhaseOrderedHumanDecision as H
import GoalPhasePlanRevision as R

/** Native provenance for an inventory-only continuation of the already reserved
  * Phase repair. Decoded business values alone never construct a Source.
  * @since   Oct. 27, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseRepairInventoryContinuation {
  val schema = "sm-workflow.phase-repair-inventory.v2"
  val causation = "ordered-phase-repair-inventory"
  val humanCausationPrefix = "ordered-phase-repair-inventory-human:"
  val event = "phase-repair-inventory-continued"
  private val _namespace = ":ordered-phase-repair-inventory:"
  val message = "Continue the same reserved Phase repair with a finite literal inventory append. " +
    "Every accepted Step, original base, acceptance condition and repair count remains. " +
    "Fresh candidate checks, independent Phase review and release closure remain required."

  /** This ephemeral value is created only after the native Human owner has
    * authenticated the actual accepted Defer and its adjacent completion.
    */
  final class Deferred private[GoalPhaseRepairInventoryContinuation](val human: H.Source,
    val record: InstanceRecord, val answer: ContinuationResult[H.Answer])

  private[admission] def deferredC(human: H.Source, record: InstanceRecord,
    answer: ContinuationResult[H.Answer]): Consequence[Deferred] = for {
    _ <- _check(answer != null && answer.result != null && answer.result.typeIdentity == H.answerCodec.typeIdentity &&
      answer.result.value != null && answer.result.value.choice == "Defer" &&
      answer.continuationId == H.identity(human) && H.answerCodec.decode(H.answerCodec.encode(answer.result.value))
        .contains(answer.result.value), "only an actual ordinary Human Defer can expose deferred inventory recovery")
    pending <- _pending_c(human)
    actual <- GoalPhaseNativeHistory.completionC(human.session.current, pending, answer.resultReference, "Decision")
    _ <- _check(record == actual && actual.causation.value == answer.resultReference.identity,
      "deferred inventory source is not the complete adjacent accepted Human prefix")
  } yield new Deferred(human, record, answer)

  final class Source private[GoalPhaseRepairInventoryContinuation](val session: F.Session,
    val from: InstanceRecord, val controller: O.State, val input: W.Request,
    val result: ContinuationResult[W.Result], val human: Option[H.Source], val deferred: Option[Deferred]) {
    def frontier: InstanceRecord = deferred.map(_.record).orElse(human.map(_ => _pending_value)).getOrElse(from)
    private def _pending_value: InstanceRecord = GoalPhaseNativeHistory.prefixC(session.current, from.revision.next).toOption.get
    def reference: WorkflowMechanicalTransition.Reference =
      WorkflowMechanicalTransition.Reference(from.identity.value + _namespace + frontier.revision.value, "1")
  }

  /** Only the native walker supplies the authenticated semantic pair. */
  private[admission] def sourceC(session: F.Session, from: InstanceRecord, controller: O.State,
    input: W.Request, result: ContinuationResult[W.Result], human: Option[H.Source] = None,
    deferred: Option[Deferred] = None): Consequence[Source] = for {
    _ <- _check(session != null && session.storage != null && session.started != null &&
      from != null && controller != null && input != null && result != null &&
      result.result != null && result.result.value != null && human != null && deferred != null,
      "native source material is absent")
    _ <- _check(controller.stage == O.Stage.Decision && controller.decisionSource.contains(O.Stage.Repair) &&
      controller.releaseRepairActive && controller.cursor == controller.completed.size &&
      controller.completed.map(_.step) == controller.plan.steps.map(_.identity) &&
      controller.completed.forall(O.validStepAcceptance) && controller.pendingRepair.isEmpty &&
      !controller.policyRepairPending && !controller.documentDecision && controller.closureIntent.isEmpty &&
      controller.phaseReviewedCandidate.isEmpty && controller.phaseRecordingPaths.isEmpty && controller.releaseCommit.isEmpty &&
      controller.repairs <= controller.effectiveRepairLimit && controller.repairControl.flatMap(_.stop).isEmpty &&
      controller.generation < Long.MaxValue, "source is not an unexhausted all-closed reserved Phase repair Decision")
    _ <- W.admitSemanticResultC(input, result.result.value)
    _ <- _check(input.kind == "RepairStep" && input.stepIdentity.isEmpty && input.review.isEmpty &&
      input.instanceIdentity == from.identity.value && input.selectedPlan == controller.plan &&
      input.originalBase == controller.base && input.completed == controller.completed &&
      input.generation == controller.generation && input.repairDirective == controller.repairDirective &&
      input.repairProgress.contains(GoalPhaseRepairProgress.snapshot(controller)) &&
      input.checkFailure.exists(failure => W.admitCheckFailureContextC(failure).isSuccess &&
        failure.validation.step == "phase:" + controller.plan.phase && failure.validation.dependency.isEmpty &&
        failure.validation.dependencySource.isEmpty && failure.initialPhaseRepair.nonEmpty) &&
      result.result.typeIdentity == W.resultCodec.typeIdentity && result.result.value.outcome == "decision-needed" &&
      result.result.value.dependencyProposal.isEmpty && result.result.value.dependencyAdmission.isEmpty &&
      result.result.value.dependencyValidation.isEmpty && result.result.value.diagnosticRepair.isEmpty &&
      W.inputCodec.decode(W.inputCodec.encode(input)).contains(input) &&
      W.resultCodec.decode(W.resultCodec.encode(result.result.value)).contains(result.result.value),
      "source crosses its actual phase repair request, failure, result, base, progress or generation")
    revision <- input.sourceRevision.toLongOption.filter(n => n > 0 && n.toString == input.sourceRevision)
      .map(n => Consequence.success(InstanceRevision(n))).getOrElse(F.invalid("Phase inventory work revision is invalid"))
    issuedfrom <- GoalPhaseNativeHistory.prefixC(session.current, revision)
    pending <- GoalPhaseNativeHistory.prefixC(session.current, revision.next)
    completed <- GoalPhaseNativeHistory.completionC(session.current, pending, result.resultReference, "Decision")
    _ <- _check(completed == from && issuedfrom.suspension.isEmpty &&
      issuedfrom.currentProgression.contains(ProgressionReference("phase.Repair")) &&
      pending.suspension.exists(_.continuationIdentity.value == result.continuationId.value) &&
      from.lifecycle == Lifecycle.Active && from.suspension.isEmpty,
      "semantic source is not its complete contiguous native issue/suspension/completion")
    _ <- human.fold(Consequence.unit) { selected => for {
      _ <- _check(selected.from == from && selected.controller == controller &&
        selected.input == input.copy(issues = selected.input.issues) &&
        selected.result == result && selected.sourceHistoryRecovery.isEmpty &&
        selected.dependencyInventoryReason.isEmpty && selected.session.current == session.current,
        "inventory recovery crosses the actual ordinary Human source")
      _ <- _pending_c(selected)
    } yield () }
    _ <- deferred.fold(Consequence.unit) { selected => for {
      _ <- _check(human.contains(selected.human), "Defer belongs to another Human source")
      _ <- deferredC(selected.human, selected.record, selected.answer)
    } yield () }
  } yield new Source(session, from, controller, input, result, human, deferred)

  private[admission] def selectC(session: F.Session, from: InstanceRecord, controller: O.State,
    last: Option[(W.Request, ContinuationResult[W.Result])]): Consequence[Option[Source]] =
    if (last == null) F.invalid("Phase inventory source Option is null")
    else last.flatMap(_._2.result.value.planRevision) match {
      case Some(proposal) if proposal.plan.phaseRepairInventory != controller.plan.phaseRepairInventory =>
        sourceC(session, from, controller, last.get._1, last.get._2).map(Some(_))
      case _ => Consequence.success(None)
    }

  final case class Target(schemaVersion: String, componentInstance: String, sessionId: String,
    sourceRevision: String, frontierRevision: String, plan: O.Plan, base: O.Snapshot,
    completed: Vector[O.ClosedStep], generation: Long, repairProgress: GoalPhaseRepairProgress.Snapshot,
    requestingWork: ContextReference, requestingResult: ContextReference,
    questionIdentity: Option[String], pendingRevision: Option[String], deferredResult: Option[ContextReference],
    deferredChoice: Option[String], candidatePaths: Vector[String], captured: R.Captured)
  object Target {
    given Encoder[Target] = deriveEncoder[Target].mapJson(_.mapObject(_.filter {
      case ("questionIdentity" | "pendingRevision" | "deferredResult" | "deferredChoice", value) => !value.isNull
      case _ => true
    }))
    private val _decoder: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (targetValid(value) && cursor.value == value.asJson) Right(value)
      else Left(DecodingFailure("Phase repair inventory requires its exact closed target", cursor.history))))
  }
  final case class Payload(target: Target, directive: Option[ContextReference], participant: String, rationale: String)
  object Payload {
    given Encoder[Payload] = deriveEncoder[Payload].mapJson(_.mapObject(_.filter {
      case ("directive", value) => !value.isNull
      case _ => true
    }))
    private val _decoder: Decoder[Payload] = deriveDecoder
    given Decoder[Payload] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (value != null && targetValid(value.target) && value.directive != null && value.directive.forall(_reference) &&
          _exact(value.participant) && _text(value.rationale) &&
          (value.target.questionIdentity.nonEmpty == value.directive.nonEmpty) && cursor.value == value.asJson) Right(value)
      else Left(DecodingFailure("Phase inventory receipt contains crossed or incomplete direction", cursor.history))))
  }
  val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseRepairInventoryContinuationReceipt"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> schema.asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("Phase inventory receipt is absent")
      _ <- Either.cond(wire.asObject.exists(_.keys.toSet == Set("schemaVersion", "payload")) &&
        wire.hcursor.get[String]("schemaVersion").contains(schema), (), "Phase inventory receipt schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
    } yield payload
  }
  final case class Saved(record: InstanceRecord, controller: O.State, payload: Payload, actualRequest: W.Request)

  def selectsProposalC(value: Record): Consequence[Boolean] = R.fromPublicProposalC(Some(value)).map(
    _.exists(_.plan.phaseRepairInventory.nonEmpty))
  def selectsTarget(value: Record): Boolean = value != null && value.toJsonStringC.toOption.flatMap(
    text => io.circe.parser.parse(text).toOption).flatMap(GoalPhaseNativeMetadata.normalizePublicJson(_).toOption)
    .exists(_.hcursor.downField("schemaVersion").succeeded)
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def targetC(value: Record): Consequence[Target] = if (value == null) F.invalid("Phase inventory target is absent")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(_.as[Target].left.map(_.message))
      .fold(F.invalid, Consequence.success))

  private[admission] def targetForC(source: Source, d: F.Declaration, core: ActionCall.Core,
    proposal: R.Proposal): Consequence[Target] = for {
    _ <- sourceC(source.session, source.from, source.controller, source.input, source.result, source.human, source.deferred)
    _ <- _check(source.human.nonEmpty || source.result.result.value.planRevision.contains(proposal),
      "automatic inventory differs from its actual semantic proposal")
    captured <- R.captureSemanticProposalC(source.controller, proposal, None, source.input.documents)
    _ <- _check(captured.revisionScope.contains(R.phaseRepairInventoryScope), "proposal has no explicit Phase inventory append")
    added <- O.phaseRepairInventoryPathsC(source.controller.plan, captured.plan)
    _ <- _check(d.input.repositoryIdentity == captured.plan.repository && _paths(d.candidatePaths) &&
      added.forall(d.candidatePaths.contains) && source.controller.plan.phaseRepairInventory.forall(d.candidatePaths.contains),
      "every Phase inventory path must retain an exact finite host candidatePaths registration")
    pending <- source.human.fold(Consequence.success(Option.empty[String]))(human =>
      _pending_c(human).map(value => Some(value.revision.value.toString)))
    target = Target(schema, core.component.get.core.instanceId.canonicalKey, source.from.identity.value,
      source.from.revision.value.toString, source.frontier.revision.value.toString, source.controller.plan,
      source.controller.base, source.controller.completed, source.controller.generation,
      GoalPhaseRepairProgress.snapshot(source.controller), ContextReference(source.result.continuationId.value, source.input.sourceRevision),
      source.result.resultReference,
      source.human.map(H.identity(_).value), pending, source.deferred.map(_.answer.resultReference),
      source.deferred.map(_.answer.result.value.choice), d.candidatePaths, captured)
    _ <- _check(targetValid(target), "captured target is invalid")
  } yield target

  def targetValid(value: Target): Boolean = value != null && value.schemaVersion == schema &&
    _exact(value.componentInstance) && _exact(value.sessionId) && _revision(value.sourceRevision) && _revision(value.frontierRevision) &&
    O.validatePlanC(value.plan).isSuccess && value.base != null && value.completed != null &&
    value.completed.map(_.step) == value.plan.steps.map(_.identity) && value.completed.forall(O.validStepAcceptance) &&
    value.generation > 0 && value.generation < Long.MaxValue &&
    GoalPhaseRepairProgress.validateOptionC(Some(value.repairProgress)).isSuccess &&
    _reference(value.requestingWork) && _revision(value.requestingWork.revision) && _reference(value.requestingResult) &&
    value.requestingWork.identity == value.sessionId + ":ordered-repair:" + value.requestingWork.revision &&
    BigInt(value.sourceRevision) == BigInt(value.requestingWork.revision) + 2 &&
    value.questionIdentity != null && value.pendingRevision != null && value.deferredResult != null && value.deferredChoice != null &&
    value.questionIdentity.forall(_ == value.sessionId + ":ordered-human:" + value.sourceRevision) &&
    value.pendingRevision.forall(revision => _revision(revision) && BigInt(revision) == BigInt(value.sourceRevision) + 1) &&
    value.questionIdentity.nonEmpty == value.pendingRevision.nonEmpty &&
    value.deferredResult.forall(_reference) && value.deferredResult.nonEmpty == value.deferredChoice.nonEmpty &&
    (value.deferredResult.isEmpty || value.questionIdentity.nonEmpty) && value.deferredChoice.forall(_ == "Defer") &&
    value.frontierRevision == value.pendingRevision.map(revision =>
      if (value.deferredResult.nonEmpty) (BigInt(revision) + 1).toString else revision).getOrElse(value.sourceRevision) &&
    _paths(value.candidatePaths) &&
    R.validateCapturedC(value.captured).isSuccess && value.captured.revisionScope.contains(R.phaseRepairInventoryScope) &&
    O.phaseRepairInventoryPathsC(value.plan, value.captured.plan).toOption.exists(_.forall(value.candidatePaths.contains))

  def bindingC(component: String, target: Target, rationale: String, participant: String,
    reference: ContextReference): Consequence[Json] = for {
    _ <- _check(targetValid(target) && target.questionIdentity.nonEmpty && target.componentInstance == component &&
      _exact(participant) && _reference(reference) && _text(rationale), "direction crosses its complete target or actual participant")
  } yield Json.obj("componentInstance" -> component.asJson, "remainingPlanAdoption" -> Json.obj("target" -> target.asJson,
    "directive" -> Json.obj("rationale" -> rationale.asJson, "participant" -> participant.asJson, "reference" -> reference.asJson)))
  def selectBinding(value: Json): Either[String, Json] = for {
    supplied <- Option(value).toRight("Phase inventory direction is absent")
    component <- supplied.hcursor.get[String]("componentInstance").left.map(_.message)
    target <- supplied.hcursor.downField("remainingPlanAdoption").get[Target]("target").left.map(_.message)
    directive = supplied.hcursor.downField("remainingPlanAdoption").downField("directive")
    rationale <- directive.get[String]("rationale").left.map(_.message)
    participant <- directive.get[String]("participant").left.map(_.message)
    reference <- directive.get[ContextReference]("reference").left.map(_.message)
    actual <- bindingC(component, target, rationale, participant, reference).map[Either[String, Json]](Right(_))
      .fold(error => Left(error.toString), identity)
    _ <- Either.cond(actual == supplied, (), "Phase inventory direction contains extra or crossed fields")
  } yield actual

  def preparationBindingC(description: V.GoalPhaseRemainingPlanDescriptionResult,
    input: V.GoalPhaseRemainingPlanAdoptionInput, participant: String, reference: ContextReference): Consequence[Json] = for {
    described <- targetC(description.target)
    supplied <- targetC(input.target)
    proposal <- R.fromPublicProposalC(Some(input.proposal))
    _ <- _check(described == supplied && described.sessionId == description.sessionId &&
      input.sessionId == described.sessionId && proposal.contains(described.captured.proposal),
      "prepared direction crosses its described target or proposal")
    binding <- bindingC(description.componentInstance, described, input.rationale, participant, reference)
  } yield binding

  def describeC(source: Source, d: F.Declaration, core: ActionCall.Core, raw: Record):
      Consequence[V.GoalPhaseRemainingPlanDescriptionResult] = for {
    proposal <- R.fromPublicProposalC(Some(raw)).flatMap(_.map(Consequence.success).getOrElse(F.invalid("inventory proposal is absent")))
    target <- targetForC(source, d, core, proposal)
    _ <- _check(source.human.nonEmpty && source.frontier == source.session.current,
      "description requires the exact current unanswered or accepted-Defer frontier")
    _ <- _question_c(source, core)
  } yield V.GoalPhaseRemainingPlanDescriptionResult(target.sessionId, "adoption-required", message,
    target.componentInstance, targetRecord(target), GoalPhaseRemainingPlanAdoption.adoptionOperation)

  def adoptC(source: Source, d: F.Declaration, core: ActionCall.Core, input: V.GoalPhaseRemainingPlanAdoptionInput):
      Consequence[V.GoalPhaseRemainingPlanAdoptionResult] = for {
    supplied <- targetC(input.target)
    description <- describeC(source, d, core, input.proposal)
    actual <- targetC(description.target)
    _ <- _check(supplied == actual && input.sessionId == actual.sessionId, "adoption target is stale or crossed")
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(actual.componentInstance, actual, input.rationale, source.session.participantIdentity, reference)
    installation <- d.remainingPlanAdoption.map(Consequence.success).getOrElse(F.invalid("remaining Plan direction provider is absent"))
    _ <- installation.validateC
    _ <- installation.evidence.admitC(core, reference, binding)
    payload = Payload(actual, Some(reference), source.session.participantIdentity, input.rationale)
    state <- source.controller.adoptPhaseRepairInventoryC(actual.captured.plan, reference.identity)
    _ <- source.deferred match {
      case None => for {
        adapter <- source.session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec, codec)
        _ <- adapter.executeC(H.identity(source.human.get), source.frontier.revision, source.frontier,
          F.history(source.frontier, "phase.Repair", humanCausationPrefix + reference.identity), TypedValue(codec.typeIdentity, payload))(using core.executionContext)
      } yield ()
      case Some(_) => _mechanical_c(source, d, core, payload, state).map(_ => ())
    }
  } yield V.GoalPhaseRemainingPlanAdoptionResult(input.sessionId, true, "phase-repair-inventory-adopted", message)

  private[admission] def advanceC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Saved] = for {
    proposal <- source.result.result.value.planRevision.map(Consequence.success).getOrElse(F.invalid("automatic inventory proposal is absent"))
    target <- targetForC(source, d, core, proposal)
    _ <- _check(source.human.isEmpty && source.frontier == source.session.current, "automatic inventory source is stale or already questioned")
    state <- source.controller.adoptPhaseRepairInventoryC(target.captured.plan, source.result.resultReference.identity)
    saved <- _mechanical_c(source, d, core, Payload(target, None, source.session.participantIdentity, proposal.reason), state)
  } yield saved

  private def _mechanical_c(source: Source, d: F.Declaration, core: ActionCall.Core,
    value: Payload, state: O.State): Consequence[Saved] = for {
    _ <- _check(domain.statemachine.GoalPhaseRunLifecycle.permits("Decision", event, "Repair"), "CML inventory continuation edge is absent")
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    actual <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, source.reference, source.frontier,
      source.frontier.revision, F.history(source.frontier, "phase.Repair", causation), TypedValue(codec.typeIdentity, value)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    saved <- _reconstruct_c(source, d, core, value, actual.receipt.next, fresh = true)
  } yield saved

  private[admission] def retainedC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Option[Saved]] =
    if (source.human.nonEmpty && source.deferred.isEmpty) for {
      adapter <- source.session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec, codec)
      retained <- adapter.loadRetainedC(H.identity(source.human.get), source.session.current.identity)(using core.executionContext)
      saved <- retained match {
        case None => Consequence.success(Option.empty[Saved])
        case Some(value) => for {
          next <- GoalPhaseNativeHistory.prefixC(source.session.current, value.receipt.nextRevision)
          _ <- _check(value.receipt.sourceRevision == source.frontier.revision, "supersession crosses its pending prefix")
          selected <- _reconstruct_c(source, d, core, value.receipt.payload, next)
        } yield Some(selected)
      }
    } yield saved
    else for {
      adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
      retained <- adapter.loadRetainedC(source.reference)(using core.executionContext)
      saved <- retained match {
        case None => Consequence.success(Option.empty[Saved])
        case Some(value) => _reconstruct_c(source, d, core, value.receipt.payload.value, value.receipt.next).map(Some(_))
      }
    } yield saved

  private def _reconstruct_c(source: Source, d: F.Declaration, core: ActionCall.Core,
    value: Payload, next: InstanceRecord, fresh: Boolean = false): Consequence[Saved] = for {
    paths <- retainedCandidatePathsC(value.target.candidatePaths, d.candidatePaths)
    expected <- targetForC(source, d.copy(candidatePaths = paths), core, value.target.captured.proposal)
    _ <- _check(expected == value.target && value.participant == source.session.participantIdentity,
      "retained inventory changes its complete actual native lineage or participant")
    _ <- source.human.fold(Consequence.unit)(_ => _question_c(source, core))
    state <- source.controller.adoptPhaseRepairInventoryC(expected.captured.plan,
      value.directive.map(_.identity).getOrElse(source.result.resultReference.identity))
    actual <- if (source.human.nonEmpty && source.deferred.isEmpty)
      GoalPhaseNativeHistory.completionC(source.session.current, source.frontier,
        ContextReference(humanCausationPrefix + value.directive.get.identity, value.directive.get.revision), "Repair")
    else source.frontier.appendC(source.frontier.revision, F.history(source.frontier, "phase.Repair", causation))
    _ <- _check(actual == next && next.revision == source.frontier.revision.next,
      "inventory receipt is not its exact adjacent native successor")
    _ <- if (fresh) _check(source.session.current == source.frontier, "fresh inventory source omits later native history")
      else GoalPhaseNativeHistory.prefixC(source.session.current, next.revision).flatMap(retained =>
        _check(retained == next, "inventory successor is outside the actual retained history"))
  } yield Saved(next, state, value, source.input)

  private def _question_c(source: Source, core: ActionCall.Core): Consequence[Unit] = for {
    human <- source.human.map(Consequence.success).getOrElse(F.invalid("inventory recovery has no actual Human source"))
    adapter <- source.session.storage.bindDecisionC(H.completionOperation, H.inputCodec)
    question <- adapter.loadC(H.identity(human))(using core.executionContext).flatMap(_.map(Consequence.success)
      .getOrElse(F.invalid("inventory recovery has no actual issued question")))
    input <- NativeWorkflowOperationOutput.decisionInputC(question)
    _ <- _check(input.work == human.input && input.requestingResult == source.result.resultReference &&
      input.result == source.result.result.value && input.sourceHistoryRecovery.isEmpty &&
      input.repairProgress.contains(GoalPhaseRepairProgress.snapshot(source.controller)), "question crosses the actual semantic source")
  } yield ()

  private def _pending_c(human: H.Source): Consequence[InstanceRecord] = for {
    following <- GoalPhaseNativeHistory.nextC(human.session.current, human.from)
    entry <- following match {
      case Some(value) if value.revision == human.from.revision.next && value.suspension.exists(boundary =>
        boundary.continuationIdentity.value == H.identity(human).value &&
        boundary.completion.value == "goal-phase-ordered-human-completion-v1") => Consequence.success(value)
      case _ => F.invalid("Phase inventory requires its exact adjacent ordinary Human pending prefix")
    }
    pending <- GoalPhaseNativeHistory.prefixC(human.session.current, entry.revision)
  } yield pending

  private[admission] def retainedCandidatePathsC(recorded: Vector[String], current: Vector[String]): Consequence[Vector[String]] =
    _check(_paths(recorded) && _paths(current) && current.take(recorded.size) == recorded,
      "current host changes the complete recorded candidatePaths ordinal prefix").map(_ => recorded)

  /** Bootstrap only standard composition's finite data. The caller must then
    * traverse the sole native walker, which authenticates every receipt and
    * its complete semantic/Human lineage before binding an executable service.
    */
  private[admission] def runtimeCandidatePathsC(session: F.Session, initial: Vector[String],
    core: ActionCall.Core): Consequence[Vector[String]] =
    session.current.history.filter(entry => entry.causation.value == causation ||
      entry.causation.value.startsWith(humanCausationPrefix)).foldLeft(Consequence.success(initial)) { (prior, entry) => for {
      paths <- prior
      before <- GoalPhaseNativeHistory.prefixC(session.current, InstanceRevision(entry.revision.value - 1))
      value <- if (entry.causation.value == causation) for {
        adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
        retained <- adapter.loadRetainedC(WorkflowMechanicalTransition.Reference(
          session.current.identity.value + _namespace + before.revision.value, "1"))(using core.executionContext)
        receipt <- retained.map(Consequence.success).getOrElse(F.invalid("runtime inventory receipt is absent"))
        _ <- _check(receipt.receipt.next.revision == entry.revision, "runtime inventory crosses its native successor")
      } yield receipt.receipt.payload.value
      else for {
        identity <- before.suspension.map(value => Consequence.success(org.goldenport.cncf.workflow.ContinuationIdentity(value.continuationIdentity.value)))
          .getOrElse(F.invalid("runtime inventory has no original Human suspension"))
        adapter <- session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec, codec)
        retained <- adapter.loadRetainedC(identity, session.current.identity)(using core.executionContext)
        receipt <- retained.map(Consequence.success).getOrElse(F.invalid("runtime inventory Human receipt is absent"))
        _ <- _check(receipt.receipt.nextRevision == entry.revision, "runtime Human inventory crosses its native successor")
      } yield receipt.receipt.payload
      _ <- _check(value.target.sessionId == session.current.identity.value && targetValid(value.target),
        "runtime inventory belongs to another native Session")
      _ <- retainedCandidatePathsC(paths, value.target.candidatePaths)
    } yield value.target.candidatePaths }
  private def _paths(values: Vector[String]): Boolean = values != null && values.nonEmpty && values.size <= 256 &&
    values.distinct == values && values.forall(value => _exact(value) && RepositorySyncNativeConflictInput.relative(value) &&
      !value.exists(c => "*?[]{}".contains(c)))
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _reference(value: ContextReference): Boolean = value != null && _exact(value.identity) && _exact(value.revision)
  private def _exact(value: String): Boolean = _text(value) && value.trim == value && !value.exists(_.isControl)
  private def _text(value: String): Boolean = GoalPhasePurposeRepair.text(value)
  private def _check(valid: Boolean, reason: String): Consequence[Unit] =
    if (valid) Consequence.unit else F.invalid("Phase repair inventory: " + reason)
}
