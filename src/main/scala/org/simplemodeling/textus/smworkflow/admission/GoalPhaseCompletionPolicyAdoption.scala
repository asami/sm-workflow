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
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.convert.ValueReader
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O

/** A fresh independently attested direction changes only completion policy.
  * @since   Oct. 23, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseCompletionPolicyAdoption {
  val purpose = "goal-phase-completion-policy-adoption"
  val describeOperation = "describeGoalPhaseCompletionPolicy"
  val adoptionOperation = "adoptGoalPhaseCompletionPolicy"
  val choices = Vector("AdoptLocalCompletion", "Defer")
  val schema = "sm-workflow.goal-phase-completion-policy-adoption.v1"
  val message = "Accepted Phase development may complete locally before best-effort RepositorySync. " +
    "Independent Human direction changes only this completion policy; accepted work, history and child identity remain. " +
    "Defer writes nothing. A later ordinary advanceGoalPhase completes eligible committed development. No remote authority is granted."

  final case class Target(input: GoalPhaseNativeRepositoryClosure.Input, frontierRevision: String,
    frontierCause: String, participant: String, repairProgress: GoalPhaseRepairProgress.Snapshot,
    generation: Long, childSessionId: Option[String])
  object Target {
    given Encoder[Target] = deriveEncoder[Target].mapJson(_.mapObject(
      _.filter { case (key, value) => key != "childSessionId" || !value.isNull }))
    private val _raw: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (value.asJson == cursor.value && _valid_target_c(value).isSuccess) Right(value)
      else Left(io.circe.DecodingFailure("Completion policy target requires its exact complete contract", cursor.history))))
  }
  final case class Direction(target: Target, reference: ContextReference, rationale: String)
  final case class Source(session: F.Session, from: InstanceRecord, controller: O.State,
    legacy: Option[GoalPhaseNativeRepositoryClosure.Payload] = None)
  final case class Resumed(record: InstanceRecord, direction: Direction)
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else F.invalid("Completion policy adoption requires an independent Human evidence provider")
  }
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Completion policy target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def targetC(value: Record): Consequence[Target] = if (value == null) F.invalid("Completion policy target is absent")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(decodeTarget).fold(F.invalid, Consequence.success))

  def bindingC(component: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] = for {
    _ <- _valid_target_c(target)
    _ <- if (_exact(component) && choices.contains(choice) && _text(rationale) &&
        participant == target.participant && reference != null && _exact(reference.identity) && _exact(reference.revision))
      Consequence.unit else F.invalid("Completion policy direction requires its actual Human participant, choice and reference")
  } yield Json.obj("componentInstance" -> Json.fromString(component), "completionPolicyAdoption" -> Json.obj(
    "target" -> target.asJson, "answer" -> Json.obj("choice" -> Json.fromString(choice),
      "rationale" -> Json.fromString(rationale), "participant" -> Json.fromString(participant), "reference" -> reference.asJson)))
  def selectBinding(value: Json): Either[String, Json] = for {
    supplied <- Option(value).toRight("Completion policy binding is absent")
    component <- supplied.hcursor.get[String]("componentInstance").left.map(_.message)
    body <- supplied.hcursor.get[Json]("completionPolicyAdoption").left.map(_.message)
    targetwire <- body.hcursor.get[Json]("target").left.map(_.message)
    target <- decodeTarget(targetwire)
    answer = body.hcursor.downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    selected <- bindingC(component, target, choice, rationale, participant, reference)
      .map[Either[String, Json]](Right(_)).fold(error => Left(error.toString), identity)
    _ <- Either.cond(selected == supplied, (), "Completion policy binding fields differ")
  } yield selected
  val codec: WorkflowResultJsonV1.PayloadCodec[Direction] = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "GoalPhaseCompletionPolicyDirection"
    def encode(value: Direction): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, Direction] = for {
      wire <- Option(value).toRight("Completion policy direction is absent")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      direction <- wire.hcursor.get[Direction]("payload").left.map(_.message)
      _ <- Either.cond(version == schema && encode(direction) == wire && _valid_target_c(direction.target).isSuccess &&
        direction.reference != null && _exact(direction.reference.identity) && _exact(direction.reference.revision) &&
        _text(direction.rationale), (), "Completion policy direction fields differ")
    } yield direction
  }
  def targetC(source: Source): Consequence[Target] = for {
    _ <- if (source != null && source.session.current.lifecycle == Lifecycle.Active &&
        source.from.lifecycle == Lifecycle.Active && source.from.suspension.isEmpty &&
        source.from.currentProgression.contains(ProgressionReference("phase.PhaseClosing")) &&
        source.session.started.receipt.payload.value.completionPolicy.getOrElse(GoalPhasePostCompletionSync.strictPolicy) ==
          GoalPhasePostCompletionSync.strictPolicy) Consequence.unit
      else F.invalid("Completion policy adoption requires an unsuspended legacy PhaseClosing frontier")
    current <- GoalPhaseNativeRepositoryClosure.inputC(source.from, source.controller)
    input = source.legacy.map(_.input).getOrElse(current)
    _ <- if (input.plan == current.plan && input.base == current.base && input.completed == current.completed &&
        input.candidate == current.candidate && input.review == current.review && input.reviewedCandidate == current.reviewedCandidate &&
        source.legacy.forall(_.result.isEmpty)) Consequence.unit else F.invalid("Legacy child crosses its accepted closure input")
    target = Target(input, source.from.revision.value.toString, source.from.causation.value,
      source.session.participantIdentity, GoalPhaseRepairProgress.snapshot(source.controller), source.controller.generation,
      source.legacy.map(_.childSessionId))
    _ <- _valid_target_c(target)
  } yield target
  private def _current_target_c(source: Source, core: ActionCall.Core): Consequence[Target] = for {
    _ <- if (source.from == source.session.current) Consequence.unit else F.invalid("Completion policy frontier is stale")
    target <- targetC(source)
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    existing <- adapter.loadRetainedC(_reference(source.from))(using core.executionContext)
    _ <- if (existing.isEmpty) Consequence.unit else F.invalid("Completion policy direction already exists")
  } yield target
  def describeC(source: Source, core: ActionCall.Core): Consequence[V.GoalPhaseCompletionPolicyDescriptionResult] =
    _current_target_c(source, core).map(target => V.GoalPhaseCompletionPolicyDescriptionResult(
      source.from.identity.value, "adoption-required", message, core.component.get.core.instanceId.canonicalKey,
      targetRecord(target), choices, adoptionOperation))
  def adoptC(source: Source, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhaseCompletionPolicyAdoptionInput): Consequence[V.GoalPhaseCompletionPolicyAdoptionResult] = for {
    actual <- _current_target_c(source, core)
    supplied <- targetC(input.target)
    _ <- if (supplied == actual && input.sessionId == actual.input.instance) Consequence.unit
      else F.invalid("Completion policy adoption crosses its actual frontier")
    installation <- d.completionPolicyAdoption.map(Consequence.success).getOrElse(
      Consequence.securityPermissionDenied("Completion policy adoption has no independent Human evidence provider"))
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, actual, input.choice, input.rationale,
      source.session.participantIdentity, reference)
    _ <- installation.evidence.admitC(core, reference, binding)
    _ <- if (input.choice == "Defer") Consequence.unit else for {
      _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits("PhaseClosing", "completion-policy-adopted", "PhaseClosing"))
        Consequence.unit else F.invalid("Completion policy adoption is absent from CML")
      adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
      _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(source.from), source.from,
        source.from.revision, F.history(source.from, "phase.PhaseClosing", _cause(source.from)),
        TypedValue(codec.typeIdentity, Direction(actual, reference, input.rationale))),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield ()
  } yield V.GoalPhaseCompletionPolicyAdoptionResult(input.sessionId, true,
    if (input.choice == "Defer") "deferred" else "local-completion-adopted", message)
  def retainedC(source: Source, core: ActionCall.Core): Consequence[Resumed] = for {
    actual <- targetC(source)
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- adapter.loadRetainedC(_reference(source.from))(using core.executionContext).flatMap(
      _.map(Consequence.success).getOrElse(F.invalid("Completion policy history has no retained direction")))
    saved = retained.receipt.next
    prefix <- GoalPhaseNativeHistory.prefixC(source.session.current, saved.revision)
    direction = retained.receipt.payload.value
    _ <- if (direction.target == actual && prefix == saved && saved.identity == source.from.identity &&
        saved.definition == source.from.definition && saved.revision == source.from.revision.next &&
        saved.history.dropRight(1) == source.from.history && saved.causation.value == _cause(source.from) &&
        saved.currentProgression == source.from.currentProgression && saved.lifecycle == Lifecycle.Active &&
        saved.suspension.isEmpty) Consequence.unit else F.invalid("Completion policy direction has a crossed predecessor")
  } yield Resumed(saved, direction)
  private def _valid_target_c(value: Target): Consequence[Unit] = for {
    _ <- if (value != null && value.input != null && _exact(value.input.instance) &&
        value.input.sourceRevision.toLongOption.exists(_ > 0) && value.frontierRevision.toLongOption.exists(n =>
          n >= value.input.sourceRevision.toLong && n.toString == value.frontierRevision) && _exact(value.frontierCause) &&
        _exact(value.participant) && value.generation > 0 && value.generation < Long.MaxValue &&
        value.childSessionId != null && value.childSessionId.forall(_exact) && value.input.plan != null &&
        value.input.completed != null && value.input.completed.map(_.step) == value.input.plan.steps.map(_.identity) &&
        value.input.candidate != null && value.input.base != null && _exact(value.input.review) &&
        _exact(value.input.childInvocation) && value.input.reviewedCandidate != null) Consequence.unit
      else F.invalid("Completion policy target is incomplete")
    _ <- O.validatePlanC(value.input.plan)
    _ <- GoalPhaseRepairProgress.validateC(value.repairProgress)
    _ <- if (value.repairProgress.limit == value.input.plan.repairLimit) Consequence.unit
      else F.invalid("Completion policy target changes the repair policy")
  } yield ()
  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseCompletionPolicyOperation
      else SmWorkflowComponent.GoalPhaseServiceService.AdoptGoalPhaseCompletionPolicyOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") ||
          request.operation != (if (describe) describeOperation else adoptionOperation)) F.invalid("Completion policy Operation identity differs")
      else if (describe) _input_c[V.GoalPhaseCompletionPolicyDescriptionInput](request, Set("sessionId"))
        .map(value => Action(request, value.sessionId, true, None, port))
      else _input_c[V.GoalPhaseCompletionPolicyAdoptionInput](request,
        Set("sessionId", "target", "choice", "rationale", "reference")).flatMap(value =>
        if (value.reference != null && _exact(value.reference.identity) && _exact(value.reference.revision) &&
            choices.contains(value.choice) && _text(value.rationale))
          Consequence.success(Action(request, value.sessionId, false, Some(value), port))
        else F.invalid("Completion policy input requires its actual Human choice and reference"))
  }
  private def _input_c[A: ValueReader](request: Request, keys: Set[String]): Consequence[A] = for {
    record <- NativeWorkflowOperationInput.recordC(request, "input")
    text <- record.toJsonStringC
    json <- io.circe.parser.parse(text).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
      .fold(F.invalid, Consequence.success)
    _ <- if (json.asObject.exists(fields => fields.keys.toSet == keys && fields.values.forall(value => !value.isNull)))
      Consequence.unit else F.invalid("Completion policy input requires exactly its non-null business fields")
    value <- summon[ValueReader[A]].readC(record)
  } yield value
  private final case class Action(request: Request, sessionId: String, describe: Boolean,
    adoption: Option[V.GoalPhaseCompletionPolicyAdoptionInput], port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(sessionId))
        selected = core.copy(executionContext = context)
        result <- if (describe) port.describeCompletionPolicyC(sessionId, selected).map(NativeWorkflowOperationOutput.response(_))
          else port.adoptCompletionPolicyC(adoption.get, selected).map(NativeWorkflowOperationOutput.response(_))
      } yield result
    }
  }
  private def _reference(from: InstanceRecord) = WorkflowMechanicalTransition.Reference(
    from.identity.value + ":ordered-completion-policy-adoption:" + from.revision.value, "1")
  private def _cause(from: InstanceRecord) = "ordered-completion-policy-adoption:" + from.revision.value
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536
}
