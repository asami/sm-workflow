package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ContinuationResult, TypedValue}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.convert.ValueReader
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhaseOrderedHumanDecision as H
import GoalPhaseRuntimeRepairControl as R

/** Independently admitted same-Session policy correction. Retained history,
  * accepted work and unknown effects are never rewritten or replayed.
  * @since   Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseRepairPolicyAdoption {
  val purpose = "goal-phase-repair-policy-adoption"
  val describeOperation = "describeGoalPhaseRepairPolicy"
  val adoptionOperation = "adoptGoalPhaseRepairPolicy"
  val choices = Vector("AdoptRuntimePolicy", "RetainSelectedLimit", "Defer")
  val schema = "sm-workflow.goal-phase-repair-policy-adoption.v1"
  val message = "Independent Human direction adopts " + R.version + " in this existing Session. " +
    "AdoptRuntimePolicy corrects the selected allowance to 10; RetainSelectedLimit retains an explicitly selected allowance at most 10. " +
    "Counts, original Start/Plan, accepted work and prior questions remain. Adoption opens no new repair window. " +
    "An eligible unanswered question is superseded-by-policy-adoption, without an answer. Defer writes nothing. " +
    "Caller origin and unseen caller restrictions are unknown. A restoration link is business context only."

  final case class Question(identity: String, sourceRevision: String, pendingRevision: String,
    requestingResult: ContextReference)
  final case class Target(sessionId: String, definitionIdentity: String, definitionRevision: String,
    frontierRevision: String, frontierCause: String, participant: String, plan: O.Plan,
    base: O.Snapshot, completed: Vector[O.ClosedStep], cursor: Int, generation: Long,
    repairProgress: GoalPhaseRepairProgress.Snapshot, stage: String, decision: Option[String],
    decisionSource: Option[String], candidate: Option[O.Snapshot], checksReceipt: Option[String],
    currentPolicy: Option[String], selectedLimit: Int, effectiveLimit: Int, proposedPolicy: String,
    pendingQuestion: Option[Question] = None, requestingResult: Option[ContextReference] = None,
    restoration: Option[R.RestorationLink] = None, repairDirective: Option[O.RepairDirective] = None,
    pendingRepair: Option[O.RepairDirective] = None)
  object Target {
    given Encoder[Target] = Encoder.instance(value => Json.obj(
      "sessionId" -> value.sessionId.asJson,
      "definitionIdentity" -> value.definitionIdentity.asJson,
      "definitionRevision" -> value.definitionRevision.asJson,
      "frontierRevision" -> value.frontierRevision.asJson,
      "frontierCause" -> value.frontierCause.asJson,
      "participant" -> value.participant.asJson,
      "plan" -> value.plan.asJson,
      "base" -> value.base.asJson,
      "completed" -> value.completed.asJson,
      "cursor" -> value.cursor.asJson,
      "generation" -> value.generation.asJson,
      "repairProgress" -> value.repairProgress.asJson,
      "stage" -> value.stage.asJson,
      "decision" -> value.decision.asJson,
      "decisionSource" -> value.decisionSource.asJson,
      "candidate" -> value.candidate.asJson,
      "checksReceipt" -> value.checksReceipt.asJson,
      "currentPolicy" -> value.currentPolicy.asJson,
      "selectedLimit" -> value.selectedLimit.asJson,
      "effectiveLimit" -> value.effectiveLimit.asJson,
      "proposedPolicy" -> value.proposedPolicy.asJson,
      "pendingQuestion" -> value.pendingQuestion.asJson,
      "requestingResult" -> value.requestingResult.asJson,
      "restoration" -> value.restoration.asJson,
      "repairDirective" -> value.repairDirective.asJson,
      "pendingRepair" -> value.pendingRepair.asJson).mapObject(_.filter {
      case ("decision" | "decisionSource" | "candidate" | "checksReceipt" | "currentPolicy" |
          "pendingQuestion" | "requestingResult" | "restoration" | "repairDirective" | "pendingRepair", value) => !value.isNull
      case _ => true
    }))
    private val _raw: Decoder[Target] = Decoder.instance(cursor => for {
      sessionid <- cursor.get[String]("sessionId")
      definitionidentity <- cursor.get[String]("definitionIdentity")
      definitionrevision <- cursor.get[String]("definitionRevision")
      frontierrevision <- cursor.get[String]("frontierRevision")
      frontiercause <- cursor.get[String]("frontierCause")
      participant <- cursor.get[String]("participant")
      plan <- cursor.get[O.Plan]("plan")
      base <- cursor.get[O.Snapshot]("base")
      completed <- cursor.get[Vector[O.ClosedStep]]("completed")
      position <- cursor.get[Int]("cursor")
      generation <- cursor.get[Long]("generation")
      repairprogress <- cursor.get[GoalPhaseRepairProgress.Snapshot]("repairProgress")
      stage <- cursor.get[String]("stage")
      decision <- cursor.get[Option[String]]("decision")
      decisionsource <- cursor.get[Option[String]]("decisionSource")
      candidate <- cursor.get[Option[O.Snapshot]]("candidate")
      checksreceipt <- cursor.get[Option[String]]("checksReceipt")
      currentpolicy <- cursor.get[Option[String]]("currentPolicy")
      selectedlimit <- cursor.get[Int]("selectedLimit")
      effectivelimit <- cursor.get[Int]("effectiveLimit")
      proposedpolicy <- cursor.get[String]("proposedPolicy")
      pendingquestion <- cursor.get[Option[Question]]("pendingQuestion")
      requestingresult <- cursor.get[Option[ContextReference]]("requestingResult")
      restoration <- cursor.get[Option[R.RestorationLink]]("restoration")
      repairdirective <- cursor.get[Option[O.RepairDirective]]("repairDirective")
      pendingrepair <- cursor.get[Option[O.RepairDirective]]("pendingRepair")
    } yield Target(sessionid, definitionidentity, definitionrevision, frontierrevision, frontiercause,
      participant, plan, base, completed, position, generation, repairprogress, stage, decision,
      decisionsource, candidate, checksreceipt, currentpolicy, selectedlimit, effectivelimit,
      proposedpolicy, pendingquestion, requestingresult, restoration, repairdirective, pendingrepair))
    given Decoder[Target] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (value.asJson == cursor.value && _valid_target_c(value).isSuccess) Right(value)
      else Left(io.circe.DecodingFailure("Repair policy target requires its exact complete contract", cursor.history))))
  }
  final case class Direction(target: Target, choice: String, reference: ContextReference, rationale: String)
  final case class Source(session: F.Session, from: InstanceRecord, controller: O.State,
    human: Option[H.Source] = None,
    lastResult: Option[(GoalPhaseNativeWork.Request, ContinuationResult[GoalPhaseNativeWork.Result])] = None)
  final case class Resumed(record: InstanceRecord, controller: O.State, direction: Direction)
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else F.invalid("Repair policy adoption requires independent Human evidence")
  }
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Repair policy target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def targetC(value: Record): Consequence[Target] = if (value == null) F.invalid("Repair policy target is absent")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(decodeTarget).fold(F.invalid, Consequence.success))
  def restorationC(value: Option[Record]): Consequence[Option[R.RestorationLink]] =
    if (value == null) F.invalid("Restoration selection is null") else value match {
      case None => Consequence.success(None)
      case Some(record) if record != null => record.toJsonStringC.flatMap(text =>
        io.circe.parser.parse(text).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(json =>
          json.as[R.RestorationLink].left.map(_.message).flatMap(link =>
            Either.cond(R.validLink(link) && link.asJson == json, Some(link), "Restoration link is incomplete or malformed")))
          .fold(F.invalid, Consequence.success))
      case _ => F.invalid("Restoration selection is null")
    }

  def bindingC(component: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] = for {
    _ <- _valid_target_c(target)
    _ <- if (R.exact(component) && choices.contains(choice) && _text(rationale) && participant == target.participant &&
        reference != null && R.exact(reference.identity) && R.exact(reference.revision)) Consequence.unit
      else F.invalid("Repair policy direction requires its actual participant, choice, rationale and reference")
  } yield Json.obj("componentInstance" -> Json.fromString(component), "repairPolicyAdoption" -> Json.obj(
    "target" -> target.asJson, "answer" -> Json.obj("choice" -> Json.fromString(choice),
      "rationale" -> Json.fromString(rationale), "participant" -> Json.fromString(participant), "reference" -> reference.asJson)))
  def selectBinding(value: Json): Either[String, Json] = for {
    supplied <- Option(value).toRight("Repair policy binding is absent")
    component <- supplied.hcursor.get[String]("componentInstance").left.map(_.message)
    body <- supplied.hcursor.get[Json]("repairPolicyAdoption").left.map(_.message)
    wire <- body.hcursor.get[Json]("target").left.map(_.message)
    target <- decodeTarget(wire)
    answer = body.hcursor.downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    selected <- bindingC(component, target, choice, rationale, participant, reference)
      .map[Either[String, Json]](Right(_)).fold(error => Left(error.toString), identity)
    _ <- Either.cond(selected == supplied, (), "Repair policy binding fields differ")
  } yield selected
  val codec: WorkflowResultJsonV1.PayloadCodec[Direction] = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "GoalPhaseRepairPolicyDirection"
    def encode(value: Direction): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, Direction] = for {
      wire <- Option(value).toRight("Repair policy direction is absent")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      direction <- wire.hcursor.get[Direction]("payload").left.map(_.message)
      _ <- Either.cond(version == schema && encode(direction) == wire && _valid_target_c(direction.target).isSuccess &&
        Set("AdoptRuntimePolicy", "RetainSelectedLimit")(direction.choice) && direction.reference != null &&
        R.exact(direction.reference.identity) && R.exact(direction.reference.revision) && _text(direction.rationale), (),
        "Repair policy direction fields differ")
    } yield direction
  }

  private def _eligible_c(source: Source): Consequence[Unit] = {
    val state = source.controller
    val repairdecision = source.lastResult.exists { (input, result) =>
      input.kind == "RepairStep" && input.instanceIdentity == source.from.identity.value &&
        input.selectedPlan == state.plan && input.originalBase == state.base && input.completed == state.completed &&
        input.generation == state.generation && input.stepIdentity == state.plan.steps.lift(state.cursor).map(_.identity) &&
        result.result.value.outcome == "decision-needed" && result.result.value.planRevision.isEmpty &&
        result.result.value.dependencyProposal.isEmpty && state.decisionSource.contains(O.Stage.Repair)
    }
    val budget = state.pendingRepair.nonEmpty && state.effectiveRepairLimit > 0 &&
      state.repairs >= state.effectiveRepairLimit &&
      state.decisionSource.exists(Set(O.Stage.Validation, O.Stage.Review, O.Stage.PhaseValidation, O.Stage.PhaseReview))
    if (source.from.lifecycle == Lifecycle.Active &&
        source.human != null && source.lastResult != null && state.closureIntent.isEmpty && !state.documentDecision &&
        state.stage != O.Stage.Closed && (state.stage != O.Stage.Decision || budget || repairdecision) &&
        ((source.human.isEmpty && source.from.suspension.isEmpty) || source.human.exists(h =>
          h.from.identity == source.from.identity && h.controller == state && h.sourceHistoryRecovery.isEmpty &&
          source.from.revision == h.from.revision.next && source.from.suspension.exists(b =>
            b.continuationIdentity.value == H.identity(h).value && b.completion.value == "goal-phase-ordered-human-completion-v1"))))
      Consequence.unit else F.invalid("Repair policy adoption requires an idle native frontier, exhausted budget question or completed unchanged RepairStep decision; effects and unrelated decisions refuse")
  }
  def targetC(source: Source, restoration: Option[R.RestorationLink]): Consequence[Target] = for {
    _ <- _eligible_c(source)
    state = source.controller
    _ <- if (restoration != null && restoration.forall(link => R.validLink(link) &&
        link.producerSession == source.from.identity.value && link.producerRepository == state.plan.repository))
      Consequence.unit else F.invalid("Restoration producer must equal the actual execution owner")
    target = Target(source.from.identity.value, source.from.definition.workflowIdentity.value,
      source.from.definition.workflowRevision.value, source.from.revision.value.toString, source.from.causation.value,
      source.session.participantIdentity, state.plan, state.base, state.completed, state.cursor, state.generation,
      GoalPhaseRepairProgress.snapshot(state), state.stage.toString, state.decision, state.decisionSource.map(_.toString),
      state.candidate, state.checksReceipt, state.repairControl.map(_.policy), state.selectedRepairLimit,
      state.effectiveRepairLimit, R.version, source.human.map(h => Question(H.identity(h).value,
        h.from.revision.value.toString, source.from.revision.value.toString, h.result.resultReference)),
      source.lastResult.map(_._2.resultReference), restoration, state.repairDirective, state.pendingRepair)
    _ <- _valid_target_c(target)
  } yield target
  private def _current_target_c(source: Source, restoration: Option[R.RestorationLink], core: ActionCall.Core): Consequence[Target] = for {
    _ <- if (source.from == source.session.current && source.from.lifecycle == Lifecycle.Active) Consequence.unit
      else F.invalid("Repair policy frontier is stale or closed")
    target <- targetC(source, restoration)
  } yield target
  def describeC(source: Source, restoration: Option[Record], core: ActionCall.Core): Consequence[V.GoalPhaseRepairPolicyDescriptionResult] = for {
    link <- restorationC(restoration)
    target <- _current_target_c(source, link.orElse(source.controller.repairControl.flatMap(_.restoration)), core)
  } yield V.GoalPhaseRepairPolicyDescriptionResult(source.from.identity.value, "adoption-required", message,
    core.component.get.core.instanceId.canonicalKey, targetRecord(target), choices, adoptionOperation)
  def adoptC(source: Source, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhaseRepairPolicyAdoptionInput): Consequence[V.GoalPhaseRepairPolicyAdoptionResult] = for {
    supplied <- targetC(input.target)
    actual <- _current_target_c(source, supplied.restoration.orElse(source.controller.repairControl.flatMap(_.restoration)), core)
    _ <- if (input.sessionId == actual.sessionId && supplied == actual) Consequence.unit
      else F.invalid("Repair policy adoption crosses its actual current target")
    installation <- d.repairPolicyAdoption.map(Consequence.success).getOrElse(
      Consequence.securityPermissionDenied("Repair policy adoption has no independent Human evidence provider"))
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, actual, input.choice, input.rationale,
      source.session.participantIdentity, reference)
    _ <- installation.evidence.admitC(core, reference, binding)
    evidence <- if (input.choice == "Defer") Consequence.success(Vector.empty[V.EvidenceRecordingState]) else for {
      state <- source.controller.adoptRepairPolicyC(input.choice, reference.identity, actual.restoration)
      event = if (source.controller.stage == state.stage) "repair-policy-adopted" else "repair-policy-resumed"
      _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits(source.controller.stage.toString, event, state.stage.toString))
        Consequence.unit else F.invalid("Repair policy adoption is absent from CML")
      direction = Direction(actual, input.choice, reference, input.rationale)
      _ <- source.human match {
        case Some(human) => for {
          adapter <- source.session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec, codec)
          _ <- adapter.executeC(H.identity(human), source.from.revision, source.from,
            F.history(source.from, "phase." + state.stage, cause(source.from)),
            TypedValue(codec.typeIdentity, direction))(using core.executionContext)
        } yield ()
        case None => for {
          adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
          _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference_for(source.from), source.from,
            source.from.revision, F.history(source.from, "phase." + state.stage, cause(source.from)),
            TypedValue(codec.typeIdentity, direction)),
            ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
        } yield ()
      }
      recorded <- R.recordC(d, core, actual.sessionId, reference, "passed", R.facts(state).deepMerge(Json.obj(
        "direction" -> direction.asJson, "oldPolicy" -> actual.currentPolicy.asJson,
        "oldSelectedLimit" -> Json.fromInt(actual.selectedLimit), "oldEffectiveLimit" -> Json.fromInt(actual.effectiveLimit),
        "provenance" -> Json.fromString("independently-admitted-Human-direction"))))
    } yield recorded
  } yield V.GoalPhaseRepairPolicyAdoptionResult(input.sessionId, true,
    if (input.choice == "Defer") "deferred" else "repair-policy-adopted", message, evidence)

  def retainedC(source: Source, core: ActionCall.Core): Consequence[Resumed] = for {
    directionandrecord <- source.human match {
      case Some(human) => for {
        adapter <- source.session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec, codec)
        retained <- adapter.loadRetainedC(H.identity(human), source.from.identity)(using core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(F.invalid("Repair policy supersession has no retained direction")))
        _ <- if (retained.receipt.sourceRevision == source.from.revision && retained.receipt.nextRevision == source.from.revision.next)
          Consequence.unit else F.invalid("Repair policy supersession crosses its native question revisions")
        saved <- GoalPhaseNativeHistory.prefixC(source.session.current, retained.receipt.nextRevision)
      } yield (retained.receipt.payload, saved)
      case None => for {
        adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
        retained <- adapter.loadRetainedC(_reference_for(source.from))(using core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(F.invalid("Repair policy history has no retained direction")))
      } yield (retained.receipt.payload.value, retained.receipt.next)
    }
    (direction, saved) = directionandrecord
    actual <- targetC(source, direction.target.restoration)
    state <- source.controller.adoptRepairPolicyC(direction.choice, direction.reference.identity, actual.restoration)
    prefix <- GoalPhaseNativeHistory.prefixC(source.session.current, saved.revision)
    _ <- if (actual == direction.target && prefix == saved && saved.identity == source.from.identity &&
        saved.definition == source.from.definition && saved.revision == source.from.revision.next &&
        saved.history.dropRight(1) == source.from.history && saved.causation.value == cause(source.from) &&
        saved.currentProgression.contains(ProgressionReference("phase." + state.stage)) && saved.lifecycle == Lifecycle.Active &&
        saved.suspension.isEmpty) Consequence.unit else F.invalid("Repair policy direction has a crossed predecessor")
  } yield Resumed(saved, state, direction)

  private def _valid_target_c(value: Target): Consequence[Unit] = for {
    _ <- if (value != null && Vector(value.sessionId, value.definitionIdentity, value.definitionRevision,
        value.frontierCause, value.participant).forall(R.exact) && _positive(value.frontierRevision) &&
        value.plan != null && value.plan.steps != null && value.base != null && value.completed != null && value.cursor == value.completed.size &&
        value.cursor >= 0 && value.cursor <= value.plan.steps.size && value.generation > 0 && value.generation < Long.MaxValue &&
        O.Stage.values.map(_.toString).contains(value.stage) && value.stage != "Closed" && value.decision != null &&
        value.decisionSource != null && value.candidate != null && value.checksReceipt != null && value.currentPolicy != null &&
        value.currentPolicy.forall(_ == R.version) && value.selectedLimit >= 0 && value.selectedLimit <= 64 &&
        value.effectiveLimit == (if (value.currentPolicy.nonEmpty) math.min(value.selectedLimit, R.ceiling) else value.selectedLimit) &&
        value.proposedPolicy == R.version && value.pendingQuestion != null && value.requestingResult != null &&
        value.repairDirective != null && value.pendingRepair != null &&
        value.repairDirective.forall(_valid_directive) && value.pendingRepair.forall(_valid_directive) &&
        value.restoration != null && value.restoration.forall(link => R.validLink(link) &&
          link.producerSession == value.sessionId && link.producerRepository == value.plan.repository) &&
        value.pendingQuestion.forall(q => q != null && R.exact(q.identity) && _positive(q.sourceRevision) &&
          _positive(q.pendingRevision) && q.pendingRevision == value.frontierRevision &&
          BigInt(q.pendingRevision) == BigInt(q.sourceRevision) + 1 && _valid_reference(q.requestingResult)) &&
        value.requestingResult.forall(_valid_reference)) Consequence.unit else F.invalid("Repair policy target is incomplete")
    _ <- O.validatePlanC(value.plan)
    _ <- GoalPhaseRepairProgress.validateC(value.repairProgress)
    _ <- if (value.repairProgress.limit == value.plan.repairLimit &&
        value.completed.map(_.step) == value.plan.steps.take(value.cursor).map(_.identity) &&
        value.completed.forall(step => O.validStepAcceptance(step) && step.commit != null &&
          _valid_snapshot(O.Snapshot(step.commit.identity, step.commit.tree)) &&
          _valid_snapshot(O.Snapshot(step.commit.parent, step.commit.tree))) &&
        _valid_snapshot(value.base) && value.candidate.forall(_valid_snapshot) &&
        value.decision.forall(_text) && value.decisionSource.forall(s => O.Stage.values.map(_.toString).contains(s)) &&
        value.checksReceipt.forall(R.exact) && value.currentPolicy == value.repairProgress.runtimePolicy &&
        value.selectedLimit == value.repairProgress.selectedLimit.getOrElse(value.plan.repairLimit) &&
        value.effectiveLimit == value.repairProgress.effectiveLimit.getOrElse(value.plan.repairLimit)) Consequence.unit
      else F.invalid("Repair policy target counters or saved scope differ")
  } yield ()
  private def _valid_snapshot(value: O.Snapshot): Boolean = value != null && Vector(value.head, value.tree).forall(s =>
    s != null && s.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})"))
  private def _valid_directive(value: O.RepairDirective): Boolean = value != null && _text(value.reason) &&
    value.findings != null && value.findings.nonEmpty && value.findings.forall(_text)
  private def _valid_reference(value: ContextReference): Boolean = value != null && R.exact(value.identity) && R.exact(value.revision)
  private def _positive(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536
  private def _reference_for(from: InstanceRecord) = WorkflowMechanicalTransition.Reference(
    from.identity.value + ":ordered-repair-policy-adoption:" + from.revision.value, "1")
  def cause(from: InstanceRecord): String = "ordered-repair-policy-adoption:" + from.revision.value +
    (if (from.suspension.nonEmpty) ":superseded-by-policy-adoption" else "")

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseRepairPolicyOperation
      else SmWorkflowComponent.GoalPhaseServiceService.AdoptGoalPhaseRepairPolicyOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") ||
          request.operation != (if (describe) describeOperation else adoptionOperation)) F.invalid("Repair policy Operation identity differs")
      else if (describe) _input_c[V.GoalPhaseRepairPolicyDescriptionInput](request, Set("sessionId"), Set("restoration"))
        .map(value => Action(request, value.sessionId, Some(value), None, port))
      else _input_c[V.GoalPhaseRepairPolicyAdoptionInput](request, Set("sessionId", "target", "choice", "rationale", "reference"))
        .flatMap(value => if (value.reference != null && R.exact(value.reference.identity) && R.exact(value.reference.revision) &&
          choices.contains(value.choice) && _text(value.rationale)) Consequence.success(Action(request, value.sessionId, None, Some(value), port))
          else F.invalid("Repair policy input requires its actual Human choice and reference"))
  }
  private def _input_c[A: ValueReader](request: Request, required: Set[String], optional: Set[String] = Set.empty): Consequence[A] = for {
    record <- NativeWorkflowOperationInput.recordC(request, "input")
    wire <- record.toJsonStringC
    json <- io.circe.parser.parse(wire).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
      .fold(F.invalid, Consequence.success)
    _ <- if (json.asObject.exists(fields => required.subsetOf(fields.keys.toSet) &&
        fields.keys.toSet.subsetOf(required ++ optional) && fields.values.forall(value => !value.isNull))) Consequence.unit
      else F.invalid("Repair policy input requires exactly its complete non-null business fields")
    value <- summon[ValueReader[A]].readC(record)
  } yield value
  private final case class Action(request: Request, sessionId: String,
    description: Option[V.GoalPhaseRepairPolicyDescriptionInput], adoption: Option[V.GoalPhaseRepairPolicyAdoptionInput],
    port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(sessionId))
        selected = core.copy(executionContext = context)
        result <- description match {
          case Some(input) => port.describeRepairPolicyC(input, selected).map(NativeWorkflowOperationOutput.response(_))
          case None => port.adoptRepairPolicyC(adoption.get, selected).map(NativeWorkflowOperationOutput.response(_))
        }
      } yield result
    }
  }
}
