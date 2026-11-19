package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.convert.ValueReader
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import BoundedWorkRuntime.*
import StandaloneFixExecution as S
import DelegateTaskNativeRun as N
import WorkflowManagementReconstruction as M

/** Thin native adapter for correcting an unstarted request's derived policy.
  * The native bounded owner retains all prior checkpoints and owns Advance.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  */
private[smworkflow] object DelegateTaskRequestReconstruction {
  val purpose = "delegate-task-request-reconstruction"
  val describeOperation = "describeDelegateTaskRequestReconstruction"
  val reconstructOperation = "reconstructDelegateTaskRequest"
  val choices = Vector("AdoptDerivedValidationPolicy", "Defer")
  final case class Policy(checks: Vector[String], scalaLint: String)
  final case class Target(componentInstance: String, sessionId: String, sourceRevision: String,
    participant: String, requestIdentity: String, policy: Policy)
  final case class Observation(destinationIdentity: String, notificationStatus: String,
    intakeStatus: String, handlerStatus: String, destinationSessionId: Option[String],
    sourceReferences: Vector[ContextReference])
  final case class Direction(target: Target, policy: Policy, choice: String, rationale: String,
    participant: String, reference: ContextReference, observation: Observation)
  final case class Checkpoint(direction: Direction)
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  given Encoder[Policy] = deriveEncoder
  given Decoder[Policy] = deriveDecoder
  given Encoder[Target] = deriveEncoder
  given Decoder[Target] = deriveDecoder
  given Encoder[Observation] = deriveEncoder
  given Decoder[Observation] = deriveDecoder
  given Encoder[Direction] = deriveEncoder
  given Decoder[Direction] = deriveDecoder
  given Encoder[Checkpoint] = deriveEncoder
  given Decoder[Checkpoint] = deriveDecoder
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }

  def targetC(text: String): Consequence[Target] = for {
    json <- io.circe.parser.parse(Option(text).getOrElse("")).fold(e => _invalid(e.message), Consequence.success)
    target <- json.as[Target].fold(e => _invalid(e.message), Consequence.success)
    _ <- _check(target.asJson == json && Vector(target.componentInstance, target.sessionId,
      target.participant, target.requestIdentity).forall(_exact) &&
      target.sourceRevision.toLongOption.exists(value => value > 0 && value.toString == target.sourceRevision) &&
      _policy_valid(target.policy), "target is incomplete or contains unsupported fields")
  } yield target

  def directionC(input: V.DelegateTaskRequestReconstructionInput): Consequence[Direction] = for {
    target <- targetC(input.target)
    observation = input.observation
    direction = Direction(target, Policy(input.checks.toVector, input.scalaLint), input.choice, input.rationale,
      input.participant, ContextReference(input.reference.identity, input.reference.revision),
      Observation(observation.destinationIdentity, observation.notificationStatus, observation.intakeStatus,
        observation.handlerStatus, observation.destinationSessionId,
        observation.sourceReferences.toVector.map(r => ContextReference(r.identity, r.revision))))
    _ <- _check(input.sessionId == target.sessionId, "input crosses its target Session")
    _ <- bindingC(direction)
  } yield direction

  def bindingC(direction: Direction): Consequence[Json] = for {
    _ <- targetC(direction.target.asJson.noSpaces)
    _ <- _check(_policy_valid(direction.policy) && choices.contains(direction.choice) &&
      _exact(direction.rationale) && direction.participant == direction.target.participant &&
      _reference(direction.reference) && _observation_valid(direction.observation),
      "complete policy, genuine Human reference, original participant and attributable intake observation are required")
  } yield Json.obj("componentInstance" -> direction.target.componentInstance.asJson,
    "delegateTaskRequestReconstruction" -> Json.obj("target" -> direction.target.asJson,
      "answer" -> Json.obj("source" -> "Human".asJson, "policy" -> direction.policy.asJson,
        "choice" -> direction.choice.asJson, "rationale" -> direction.rationale.asJson,
        "participant" -> direction.participant.asJson, "reference" -> direction.reference.asJson,
        "observation" -> direction.observation.asJson)))

  def selectBinding(value: Json): Either[String, Json] = for {
    owner <- value.hcursor.get[String]("componentInstance").left.map(_.message)
    entry = value.hcursor.downField("delegateTaskRequestReconstruction")
    target <- entry.get[Target]("target").left.map(_.message)
    answer = entry.downField("answer")
    policy <- answer.get[Policy]("policy").left.map(_.message)
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    observation <- answer.get[Observation]("observation").left.map(_.message)
    selected <- bindingC(Direction(target, policy, choice, rationale, participant, reference, observation))
      .toOption.toRight("Invalid requester reconstruction direction")
    _ <- Either.cond(owner == target.componentInstance && selected == value, (),
      "requester reconstruction evidence crosses its exact Human binding")
  } yield selected

  /** Structural eligibility does not infer any destination effect from absence. */
  def assess(payload: Payload, observation: Option[Observation] = None): M.Assessment = {
    val state = payload.state
    val incompatible = Vector(
      Option.when(!DelegateTask.requester(state.input))("only the retained requester may reconstruct this request"),
      Option.when(state.stage != "DispatchReady")("requester is outside its normal DispatchReady boundary"),
      Option.when(state.candidate.nonEmpty || state.selected.nonEmpty || state.receipts.nonEmpty ||
        state.pendingCheck.nonEmpty || state.semanticReference.nonEmpty || state.reviewReference.nonEmpty ||
        state.priorReview.nonEmpty || state.priorReviewCandidate.nonEmpty || state.findings.nonEmpty ||
        state.unresolvedQuestions.nonEmpty || state.conditionReport.nonEmpty || state.commit.nonEmpty ||
        state.commitExecution.nonEmpty || state.commitReconciliation.nonEmpty || state.recoveredValidation.nonEmpty ||
        payload.implementationWorker.nonEmpty || payload.implementationWorkers.exists(_.nonEmpty) ||
        payload.supersededWork.nonEmpty || payload.hygiene.nonEmpty || payload.records.nonEmpty ||
        payload.validationCompilations.nonEmpty || payload.candidateContinuation.nonEmpty ||
        payload.repairContinuation.nonEmpty || payload.delegateRepairContinuation.nonEmpty ||
        payload.delegateSemanticContinuation.nonEmpty || payload.delegateReviewContinuation.nonEmpty ||
        payload.validationContinuation.nonEmpty || payload.validationRecovery.nonEmpty ||
        payload.validationCaptureReservation.nonEmpty || payload.managedDependency.nonEmpty)(
        "semantic, validation, commit, publication or continuation facts already exist"),
      Option.when(state.delivery.exists(d => d.destinationSessionId.nonEmpty || d.returned.nonEmpty ||
        d.artifactSelection.nonEmpty || d.adoptionScope.nonEmpty || d.publication.nonEmpty ||
        d.managedPublication.nonEmpty || d.managedConsumer.nonEmpty || d.managedContinuation.nonEmpty ||
        d.observations.nonEmpty))("retained delivery facts already leave the unstarted requester boundary"),
      Option.when(observation.exists(o => !_observation_valid(o) ||
        !state.input.delegation.exists(_.destinationIdentity == o.destinationIdentity)))(
        "intake observation is invalid or belongs to another destination")
    ).flatten
    val uncertain = observation.toVector.flatMap { facts =>
      Vector(Option.when(facts.destinationSessionId.nonEmpty ||
        !Set("rejected-before-start", "not-submitted")(facts.intakeStatus))(
        "destination Start is accepted, started, unknown or has a Session"),
        Option.when(facts.handlerStatus != "ended")("destination handler is live or unknown"),
        Option.when(!((facts.notificationStatus == "delivered" && facts.intakeStatus == "rejected-before-start") ||
          (facts.notificationStatus == "not-delivered" && facts.intakeStatus == "not-submitted")))(
          "notification and pre-Start intake facts do not establish an ended unstarted route")).flatten
    }
    M.Assessment(incompatible, Vector("actual Human direction must cover the corrected policy and intake source references"),
      uncertain, freshCapture = false, retainEvidence = false)
  }

  def selectedPayloadC(payload: Payload, direction: Direction,
    actualDirection: ContextReference): Consequence[Payload] = for {
    _ <- bindingC(direction)
    _ <- _check(direction.choice == "AdoptDerivedValidationPolicy" && actualDirection == direction.reference &&
      payload.state.input.requestIdentity == direction.target.requestIdentity &&
      payload.state.participant == direction.target.participant &&
      Policy(payload.state.input.checks, payload.state.input.scalaLint) == direction.target.policy,
      "adoption crosses the retained request, original policy or actual Human direction")
    assessment = assess(payload, Some(direction.observation))
    _ <- M.requireReconstructibleC(assessment)
    selection <- M.selectedC(assessment, Some(actualDirection), adoptProposed = true)
    _ <- _check(selection.adoptProposed && selection.resumePoint == M.ResumePoint.CurrentBoundary,
      "reconstruction has no normal requester resume point")
    corrected = payload.state.input.copy(checks = direction.policy.checks, scalaLint = direction.policy.scalaLint)
    _ <- DelegateTask.requesterInputC(corrected)
    // Logical declarations only: destination Start owns actual host resolution and execution.
    identities = (corrected.checks ++ Option.when(corrected.scalaLint == "required")("fix-check") ++
      Option.when(corrected.delegation.exists(_.validationScope == "broad"))("test")).distinct
    logical <- StandardWorkflowRuntime.standardChecksC(identities)
    checks <- DelegateTask.selectChecksC(corrected, logical)
    _ <- DelegateTask.checksC(corrected, checks)
  } yield payload.copy(state = payload.state.copy(input = corrected),
    requestReconstruction = Some(Checkpoint(direction)))

  def inputForCallC(incoming: Request, call: ActionCall.Core): Consequence[V.DelegateTaskRequestReconstructionInput] = {
    final class Reader extends ProcedureActionCall {
      val core = call
      def readInputC: Consequence[V.DelegateTaskRequestReconstructionInput] =
        NativeWorkflowResourceInput.selectC[org.goldenport.record.Record](incoming).flatMap(source =>
          NativeWorkflowResourceInput.readC(source, reference => read_resource_text(reference, Some(UTF_8))))
          .flatMap { record => for {
            raw <- record.toJsonStringC
            json <- io.circe.parser.parse(raw).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
              .fold(_invalid(_), Consequence.success)
            _ <- _check(json.asObject.exists(_.keys.toSet == Set("sessionId", "target", "checks", "scalaLint",
              "choice", "rationale", "participant", "reference", "observation")), "unsupported reconstruction input fields")
            input <- summon[ValueReader[V.DelegateTaskRequestReconstructionInput]].readC(record)
            _ <- directionC(input)
          } yield input }
      def execute(): Consequence[OperationResponse] = _invalid("input reader cannot execute an Operation")
    }
    new Reader().readInputC
  }

  def operations(host: Host, definition: DefinitionBinding, evidence: EvidencePort): Vector[spec.OperationDefinition] =
    Vector(new Operation(false, host, definition, evidence), new Operation(true, host, definition, evidence))

  private final class Operation(reconstruct: Boolean, host: Host, definition: DefinitionBinding,
    evidence: EvidencePort) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (reconstruct)
      SmWorkflowComponent.DelegateTaskServiceService.ReconstructDelegateTaskRequestOperation
      else SmWorkflowComponent.DelegateTaskServiceService.DescribeDelegateTaskRequestReconstructionOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          input <- if (reconstruct) inputForCallC(incoming, core).map(Some(_))
            else Consequence.success(Option.empty[V.DelegateTaskRequestReconstructionInput])
          session <- input.fold(NativeWorkflowOperationInput.textC(incoming, "sessionId"))(i => Consequence.success(i.sessionId))
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
          selected = core.copy(executionContext = context)
          storage <- N.storageC(host, definition, selected)
          instances <- storage.bindInstancesC()(using context)
          current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
            _.map(Consequence.success).getOrElse(_invalid("requester Session is absent")))
          _ <- WorkflowForceClosure.requireOpenC(current)
          _ <- _check(current.definition == definition && current.lifecycle == Lifecycle.Active,
            "requires the active original DelegateTask definition")
          payload <- N.retainedPayloadC(storage, session, selected)
          _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, context)
          owner = selected.component.get.core.instanceId.canonicalKey
          target = Target(owner, session, current.revision.value.toString, payload.state.participant,
            payload.state.input.requestIdentity, Policy(payload.state.input.checks, payload.state.input.scalaLint))
          result <- input match {
            case None =>
              val assessment = assess(payload)
              val issues = assessment.incompatible ++ assessment.uncertainEffects ++
                Option.when(current.suspension.nonEmpty)("pending native work cannot be reconstructed").toVector
              Consequence.success(_output(target, if (issues.isEmpty) "decision-required" else "ineligible",
                "Describe supplies no authority; actual Human admission and ended pre-Start intake facts are required", issues))
            case Some(answer) => for {
              direction <- directionC(answer)
              response <- payload.requestReconstruction match {
                case Some(checkpoint) => for {
                  _ <- _check(checkpoint.direction == direction && direction.target.componentInstance == owner &&
                    direction.target.sessionId == session, "repeated adoption differs from its original target or direction")
                  binding <- bindingC(direction)
                  _ <- evidence.admitC(selected, direction.reference, binding)
                } yield _output(direction.target, "already-reconstructed", "Original adoption retained; no checkpoint or effects repeated")
                case None => for {
                  _ <- _check(current.suspension.isEmpty && direction.target == target,
                    "current target changed or native work is pending; describe again")
                  _ <- M.requireReconstructibleC(assess(payload, Some(direction.observation)))
                  binding <- bindingC(direction)
                  _ <- evidence.admitC(selected, direction.reference, binding)
                  response <- if (direction.choice == "Defer") Consequence.success(
                    _output(target, "deferred", "Requester and all history retained unchanged")) else for {
                    corrected <- selectedPayloadC(payload, direction, direction.reference)
                    _ <- N.saveC(host, storage, current, corrected, selected)
                  } yield _output(target, "reconstructed-awaiting-advance",
                    "Corrected policy retained in the same Session; ordinary Advance supplies destination input")
                } yield response
              }
            } yield response
          }
        } yield NativeWorkflowOperationOutput.response(result)
      }
    })
  }
  private def _output(target: Target, outcome: String, message: String,
    issues: Vector[String] = Vector.empty): V.DelegateTaskRequestReconstructionDescriptionResult =
    V.DelegateTaskRequestReconstructionDescriptionResult(target.sessionId, outcome, message, target.asJson.noSpaces,
      if (issues.isEmpty) choices else Vector("Defer"), issues)
  private def _policy_valid(policy: Policy): Boolean = policy != null && policy.checks != null &&
    policy.checks.nonEmpty && policy.checks.forall(_exact) && policy.checks.distinct == policy.checks && Set("off", "required")(policy.scalaLint)
  private def _observation_valid(observation: Observation): Boolean = observation != null &&
    _exact(observation.destinationIdentity) && Set("delivered", "not-delivered", "unknown")(observation.notificationStatus) &&
    Set("rejected-before-start", "not-submitted", "accepted", "started", "unknown")(observation.intakeStatus) &&
    Set("ended", "live", "unknown")(observation.handlerStatus) && observation.destinationSessionId != null &&
    observation.destinationSessionId.forall(_exact) && observation.sourceReferences != null &&
    observation.sourceReferences.nonEmpty && observation.sourceReferences.forall(_reference) &&
    observation.sourceReferences.distinct == observation.sourceReferences
  private def _reference(reference: ContextReference): Boolean = reference != null &&
    _exact(reference.identity) && _exact(reference.revision)
  private def _exact(text: String): Boolean = text != null && text.nonEmpty && text.trim == text && !text.exists(_.isControl)
  private def _check(valid: Boolean, message: String): Consequence[Unit] = if (valid) Consequence.unit else _invalid(message)
  private def _invalid[A](message: String): Consequence[A] = Consequence.stateConflict("DelegateTask request reconstruction: " + message)
}
