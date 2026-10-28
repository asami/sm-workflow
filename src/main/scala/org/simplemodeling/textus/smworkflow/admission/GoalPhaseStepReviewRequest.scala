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
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.convert.ValueReader
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhaseStepAssessment.given

/** One native request opens independent review before any Step closure attempt.
  * A motivating report is prose, never a native review verdict or effect grant.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseStepReviewRequest {
  val describeOperation = "describeGoalPhaseStepReview"
  val requestOperation = "requestGoalPhaseStepReview"
  val message = "Request independent review of this exact conditional Step before closure. " +
    "Ordinary Advance issues native ReviewStep; external reports are motivation only. " +
    "No repair is charged by this request and no commit, process or remote authority is granted."

  final case class Target(sessionId: String, definitionIdentity: String, definitionRevision: String,
    participant: String, sourceRevision: String, sourceCause: String, plan: O.Plan, step: String,
    generation: Long, base: O.Snapshot, completed: Vector[O.ClosedStep], candidate: O.Snapshot,
    candidateRevision: String, candidateSourceRevision: String, validation: ContextReference,
    validationSourceRevision: String, admission: GoalPhaseStepAssessment.Admission,
    repairProgress: GoalPhaseRepairProgress.Snapshot)
  object Target {
    given Encoder[Target] = deriveEncoder[Target].mapJson(_.deepDropNullValues)
    private val _decoder: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (value.asJson == cursor.value && _valid_target(value)) Right(value)
      else Left(io.circe.DecodingFailure("Step review target requires its exact complete contract", cursor.history))))
  }
  final case class Requested(target: Target, reason: String)
  final case class Source(session: F.Session, from: InstanceRecord, controller: O.State,
    candidate: GoalPhaseNativeCandidate.Saved, validation: GoalPhaseNativeValidation.Saved)
  final case class Saved(record: InstanceRecord, controller: O.State, request: Requested)

  val codec: WorkflowResultJsonV1.PayloadCodec[Requested] = new WorkflowResultJsonV1.PayloadCodec[Requested] {
    val typeIdentity = "GoalPhaseStepReviewRequested"
    def encode(value: Requested): Json = Json.obj(
      "schemaVersion" -> Json.fromString("sm-workflow.goal-phase-step-review-request.v1"), "payload" -> value.asJson)
    def decode(value: Json): Either[String, Requested] = for {
      wire <- Option(value).toRight("Step review request is absent")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      request <- wire.hcursor.get[Requested]("payload").left.map(_.message)
      _ <- Either.cond(version == "sm-workflow.goal-phase-step-review-request.v1" &&
        encode(request) == wire && validReason(request.reason), (), "Step review request fields differ")
    } yield request
  }

  def validReason(value: String): Boolean = value != null && value.trim.nonEmpty &&
    !value.exists(_.isControl) && java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) &&
    value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 16384
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Step review target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def targetC(value: Record): Consequence[Target] = if (value == null) F.invalid("Step review target is absent")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(decodeTarget).fold(F.invalid, Consequence.success))

  /** Only the native continuation owner constructs Source after walking history. */
  def targetC(source: Source): Consequence[Target] = for {
    _ <- if (source != null && source.session != null && source.from != null && source.controller != null &&
        source.candidate != null && source.validation != null && source.session.current.lifecycle == Lifecycle.Active &&
        source.from.lifecycle == Lifecycle.Active && source.from.suspension.isEmpty &&
        source.from.currentProgression.contains(ProgressionReference("phase.Closing"))) Consequence.unit
      else F.invalid("Step review requires an active unsuspended conditional Closing frontier")
    state = source.controller
    snapshot <- state.candidate.map(Consequence.success).getOrElse(F.invalid("Step review candidate is absent"))
    checks <- state.checksReceipt.map(Consequence.success).getOrElse(F.invalid("Step review checks are absent"))
    _ <- state.acceptC(O.StepReviewRequested(snapshot, checks))
    admission <- state.stepAdmission.map(Consequence.success).getOrElse(F.invalid("Conditional admission is absent"))
    step = state.plan.steps(state.cursor).identity
    candidate = source.candidate
    validation = source.validation
    actualcandidate <- GoalPhaseNativeHistory.prefixC(source.session.current, candidate.record.revision)
    actualvalidation <- GoalPhaseNativeHistory.prefixC(source.session.current, validation.record.revision)
    _ <- if (_same_record(actualcandidate, candidate.record) && _same_record(actualvalidation, validation.record) &&
        candidate.payload.step == step && validation.payload.step == step && candidate.payload.candidate.contains(snapshot) &&
        validation.payload.candidate == snapshot && validation.payload.outcome.contains("passed") &&
        validation.payload.receipts.size == validation.payload.selected.size &&
        GoalPhaseNativeValidation.successful(validation.payload.receipts, validation.payload.selected) &&
        GoalPhaseNativeMetadata.declaredIdentities(validation.payload.selected.filter(_.dependency.isEmpty)) ==
          state.plan.steps(state.cursor).checks && validation.payload.assessment.exists(a =>
          a == admission.assessment || (a.branch == "triage" && admission.assessment.triageJudgment.nonEmpty &&
            GoalPhaseStepAssessment.triaged(a, admission.assessment.triageJudgment.get) == admission.assessment)) &&
        checks == source.from.identity.value + ":ordered-validation:" + validation.payload.sourceRevision + ":validation" &&
        BigInt(candidate.record.revision.value) <= BigInt(validation.payload.sourceRevision) &&
        validation.record.revision.value <= source.from.revision.value &&
        candidate.payload.sourceRevision.toLongOption.exists(_ < candidate.record.revision.value)) Consequence.unit
      else F.invalid("Step review crosses its actual candidate, successful checks or conditional admission")
    target = Target(source.from.identity.value, source.from.definition.workflowIdentity.value,
      source.from.definition.workflowRevision.value, source.session.participantIdentity,
      source.from.revision.value.toString, source.from.causation.value, state.plan, step, state.generation,
      state.base, state.completed, snapshot, candidate.record.revision.value.toString, candidate.payload.sourceRevision,
      ContextReference(checks, validation.record.revision.value.toString), validation.payload.sourceRevision,
      admission, GoalPhaseRepairProgress.snapshot(state))
    _ <- if (_valid_target(target)) Consequence.unit else F.invalid("Step review target is incomplete")
  } yield target

  private def _current_target_c(source: Source, core: ActionCall.Core): Consequence[Target] = for {
    _ <- if (_same_record(source.from, source.session.current)) Consequence.unit else F.invalid("Step review frontier is stale")
    target <- targetC(source)
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    existing <- adapter.loadRetainedC(_reference(source.from))(using core.executionContext)
    _ <- if (existing.isEmpty) Consequence.unit else F.invalid("Step review request already exists")
  } yield target
  def describeC(source: Source, core: ActionCall.Core): Consequence[V.GoalPhaseStepReviewDescriptionResult] =
    _current_target_c(source, core).map(target => V.GoalPhaseStepReviewDescriptionResult(
      target.sessionId, "review-available", message, targetRecord(target), requestOperation))
  def requestC(source: Source, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhaseStepReviewRequestInput): Consequence[V.GoalPhaseStepReviewRequestResult] = for {
    actual <- _current_target_c(source, core)
    supplied <- targetC(input.target)
    _ <- if (input.sessionId == actual.sessionId && supplied == actual && validReason(input.reason)) Consequence.unit
      else F.invalid("Step review request crosses its actual target or bounded reason")
    _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits("Closing", "step-review-requested", "Review"))
      Consequence.unit else F.invalid("Step review request is absent from CML")
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(source.from), source.from,
      source.from.revision, F.history(source.from, "phase.Review", cause(source.from)),
      TypedValue(codec.typeIdentity, Requested(actual, input.reason))),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield V.GoalPhaseStepReviewRequestResult(input.sessionId, true, "review-requested", message)

  /** Reconstruct only from exact contiguous native prefixes and retained checks. */
  def retainedC(source: Source, core: ActionCall.Core): Consequence[Saved] = for {
    actual <- targetC(source)
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- adapter.loadRetainedC(_reference(source.from))(using core.executionContext).flatMap(
      _.map(Consequence.success).getOrElse(F.invalid("Step review history has no retained request")))
    saved = retained.receipt.next
    prefix <- GoalPhaseNativeHistory.prefixC(source.session.current, saved.revision)
    request = retained.receipt.payload.value
    expected <- source.from.appendC(source.from.revision, F.history(source.from, "phase.Review", cause(source.from)))
    _ <- if (request.target == actual && validReason(request.reason) && _same_record(prefix, saved) && _same_record(saved, expected))
      Consequence.unit else F.invalid("Step review request crosses its actual native predecessor")
    state <- source.controller.acceptC(O.StepReviewRequested(actual.candidate, actual.validation.identity))
  } yield Saved(saved, state, request)

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseStepReviewOperation
      else SmWorkflowComponent.GoalPhaseServiceService.RequestGoalPhaseStepReviewOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") ||
          request.operation != (if (describe) describeOperation else requestOperation)) F.invalid("Step review Operation identity differs")
      else if (describe) _input_c[V.GoalPhaseStepReviewDescriptionInput](request, Set("sessionId"))
        .map(value => Action(request, value.sessionId, None, port))
      else _input_c[V.GoalPhaseStepReviewRequestInput](request, Set("sessionId", "target", "reason")).flatMap(value =>
        if (validReason(value.reason)) Consequence.success(Action(request, value.sessionId, Some(value), port))
        else F.invalid("Step review reason must be nonempty well-formed UTF-8 of at most 16 KiB without controls"))
  }
  private def _input_c[A: ValueReader](request: Request, keys: Set[String]): Consequence[A] = for {
    record <- NativeWorkflowOperationInput.recordC(request, "input")
    text <- record.toJsonStringC
    json <- io.circe.parser.parse(text).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
      .fold(F.invalid, Consequence.success)
    _ <- if (json.asObject.exists(fields => fields.keys.toSet == keys && fields.values.forall(value => !value.isNull)))
      Consequence.unit else F.invalid("Step review input requires exactly its non-null business fields")
    value <- summon[ValueReader[A]].readC(record)
  } yield value
  private final case class Action(request: Request, sessionId: String,
    input: Option[V.GoalPhaseStepReviewRequestInput], port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(sessionId))
        selected = core.copy(executionContext = context)
        result <- input match {
          case None => port.describeStepReviewC(sessionId, selected).map(NativeWorkflowOperationOutput.response(_))
          case Some(value) => port.requestStepReviewC(value, selected).map(NativeWorkflowOperationOutput.response(_))
        }
      } yield result
    }
  }
  def cause(from: InstanceRecord): String = "ordered-step-review-request:" + from.revision.value
  private def _same_record(left: InstanceRecord, right: InstanceRecord): Boolean =
    (WorkflowInstanceRecordJsonV1.encodeC(left), WorkflowInstanceRecordJsonV1.encodeC(right)) match {
      case (Consequence.Success(leftencoded), Consequence.Success(rightencoded)) => leftencoded == rightencoded
      case _ => false
    }
  private def _reference(from: InstanceRecord) = WorkflowMechanicalTransition.Reference(
    from.identity.value + ":" + cause(from), "1")
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _oid(value: String): Boolean = value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def _valid_target(value: Target): Boolean = try {
    value != null && Vector(value.sessionId, value.definitionIdentity, value.definitionRevision, value.participant,
      value.sourceCause, value.step).forall(_exact) &&
      Vector(value.sourceRevision, value.candidateRevision, value.candidateSourceRevision, value.validationSourceRevision).forall(_revision) &&
      value.validation != null && _exact(value.validation.identity) && _revision(value.validation.revision) &&
      value.plan != null && O.validatePlanC(value.plan).isSuccess && value.generation > 0 && value.generation < Long.MaxValue &&
      value.base != null && value.candidate != null &&
      Vector(value.base.head, value.base.tree, value.candidate.head, value.candidate.tree).forall(_oid) &&
      value.completed != null && value.completed.forall(O.validStepAcceptance) &&
      value.completed.map(_.step) == value.plan.steps.take(value.completed.size).map(_.identity) &&
      value.plan.steps.lift(value.completed.size).exists(s => s.identity == value.step &&
        s.actionPolicy.contains(value.admission.assessment.policy) && s.checks == value.admission.assessment.checks) &&
      GoalPhaseStepAssessment.validAdmission(value.admission) && value.admission.assessment.policy.detailedReview == "conditional" &&
      value.admission.assessment.candidate == value.candidate && value.admission.assessment.validationReference == value.validation.identity &&
      value.admission.assessment.review == "not-executed" && GoalPhaseRepairProgress.validateC(value.repairProgress).isSuccess &&
      value.repairProgress.limit == value.plan.repairLimit &&
      BigInt(value.candidateSourceRevision) < BigInt(value.candidateRevision) &&
      BigInt(value.candidateRevision) <= BigInt(value.validationSourceRevision) &&
      BigInt(value.validationSourceRevision) < BigInt(value.validation.revision) &&
      BigInt(value.validation.revision) <= BigInt(value.sourceRevision) &&
      value.validation.identity == value.sessionId + ":ordered-validation:" + value.validationSourceRevision + ":validation"
  } catch { case scala.util.control.NonFatal(_) => false }
}
