package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as _, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import BoundedWorkRuntime.*
import BoundedWorkRuntime.given
import GoalTaskNativeRun as N

/** One Human continuation of an actual retained trend stop. The normal native
  * checkpoint transaction owns persistence; ordinary Advance owns all work.
  */
private[smworkflow] object GoalTaskRepairContinuation {
  val purpose = "goal-task-repair-continuation"
  val resumeOperation = "resumeGoalTaskRepair"
  val choices = Vector("ContinueRepair", "Defer")
  final case class Target(componentInstance: String, sessionId: String, participant: String,
    frontierRevision: Long, frontier: Json, business: Payload)
  // The opaque original target avoids recursive Payload encoders and preserves
  // the exact stop, including its message and all original native history.
  final case class Direction(target: Json, choice: String, rationale: String,
    participant: String, reference: ContextReference)
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  given Encoder[Direction] = deriveEncoder
  given Decoder[Direction] = deriveDecoder
  given Encoder[Target] = Encoder.instance(target => Json.obj(
    "componentInstance" -> target.componentInstance.asJson, "sessionId" -> target.sessionId.asJson,
    "participant" -> target.participant.asJson, "frontierRevision" -> target.frontierRevision.asJson,
    "frontier" -> target.frontier, "business" -> target.business.asJson))
  given Decoder[Target] = Decoder.instance(cursor => for {
    owner <- cursor.get[String]("componentInstance")
    session <- cursor.get[String]("sessionId")
    participant <- cursor.get[String]("participant")
    revision <- cursor.get[Long]("frontierRevision")
    frontier <- cursor.get[Json]("frontier")
    business <- cursor.get[Payload]("business")
  } yield Target(owner, session, participant, revision, frontier, business))
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  private def _exact(value: String): Boolean = GoalPhaseRuntimeRepairControl.exact(value)

  /** Only terminal failed selected qualification can enter this continuation.
    * Other Human reasons, pending intents and exhausted budgets remain stopped.
    */
  def eligible(payload: Payload): Boolean = {
    val state = payload.state
    val control = state.repairControl
    state.input.task.nonEmpty && state.input.delegation.isEmpty && state.input.kind != "validation" &&
      state.stage == "Human" && state.pendingCheck.isEmpty && state.commit.isEmpty && state.commitExecution.isEmpty &&
      state.commitReconciliation.isEmpty && payload.supersededWork.isEmpty && payload.hygiene.isEmpty && payload.records.isEmpty &&
      control.policy == GoalPhaseRuntimeRepairControl.version && control.selectedLimit == state.input.repairLimit &&
      control.stop.contains(state.message) && state.candidate.nonEmpty && state.receipts.nonEmpty &&
      state.receipts.size <= state.selected.size && state.receipts.zip(state.selected).forall((receipt, check) => receipt.identity == check.identity) &&
      state.receipts.last.termination == "exited" && state.receipts.last.exitCode.exists(_ != 0) &&
      state.receipts.last.sbt.exists(facts => facts.outcome == WorkflowSbtValidation.Outcome.Failed &&
        facts.kind == state.selected(state.receipts.size - 1).kind) &&
      control.observations.lastOption.exists(observation => observation.scope == state.input.requestIdentity &&
        observation.reference == state.input.requestIdentity + ":generation:" + state.generation + ":" + state.receipts.last.identity) &&
      state.automaticCount >= 0 && state.ordinaryCount >= 0 && state.ordinaryCount < Int.MaxValue &&
      state.automaticCount < control.effectiveLimit && state.generation < Long.MaxValue
  }

  def decodeTarget(value: Json): Either[String, Target] = for {
    target <- value.as[Target].left.map(_.message)
    _ <- Either.cond(target.asJson == value &&
      Vector(target.componentInstance, target.sessionId, target.participant).forall(_exact) &&
      target.frontierRevision > 0 && target.frontier.isObject &&
      target.participant == target.business.state.participant && target.business.repairContinuation.isEmpty && eligible(target.business),
      (), "Task continuation target is incomplete, unsupported or not an eligible trend stop")
  } yield target
  def targetC(text: String): Consequence[Target] =
    if (text == null) N.invalid("Task continuation target is absent")
    else io.circe.parser.parse(text).left.map(_.message).flatMap(decodeTarget).fold(N.invalid(_), Consequence.success)

  def bindingC(componentInstance: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (componentInstance != target.componentInstance || participant != target.participant || !choices.contains(choice) ||
      !_exact(rationale) || reference == null || !_exact(reference.identity) || !_exact(reference.revision))
      N.invalid("Task continuation requires exact owner, participant, direction, rationale and Human reference")
    else Consequence.success(Json.obj("componentInstance" -> componentInstance.asJson,
      "goalTaskRepairContinuation" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("source" -> "Human".asJson, "choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> Json.obj("identity" -> reference.identity.asJson,
            "revision" -> reference.revision.asJson)))))

  def selectBinding(value: Json): Either[String, Json] = for {
    owner <- value.hcursor.get[String]("componentInstance").left.map(_.message)
    raw <- value.hcursor.downField("goalTaskRepairContinuation").get[Json]("target").left.map(_.message)
    target <- decodeTarget(raw)
    answer = value.hcursor.downField("goalTaskRepairContinuation").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    identity <- answer.downField("reference").get[String]("identity").left.map(_.message)
    revision <- answer.downField("reference").get[String]("revision").left.map(_.message)
    selected <- bindingC(owner, target, choice, rationale, participant, ContextReference(identity, revision)).toOption
      .toRight("Invalid Task continuation Human direction")
    _ <- Either.cond(selected == value, (), "Task continuation evidence has unsupported fields or a foreign Human source")
  } yield selected

  private def _owner(core: ActionCall.Core): String = core.component.get.core.instanceId.canonicalKey
  private def _payload_c(storage: WorkflowComponentStorage, current: InstanceRecord,
    core: ActionCall.Core): Consequence[Payload] = {
    // These are native checkpoint addresses, never an application-side ledger.
    val source = if (current.suspension.nonEmpty) Some(current.revision.value - 1L)
      else if (current.causation.value.startsWith("standalone-completed:"))
        current.causation.value.stripPrefix("standalone-completed:").takeWhile(_ != ':').toLongOption
      else Some(current.revision.value)
    source.map(N.loadPayloadC(storage, current.identity.value, _, core))
      .getOrElse(N.invalid("Task continuation has no canonical source checkpoint"))
  }

  def operations(host: Host, definition: DefinitionBinding, evidence: EvidencePort): Vector[spec.OperationDefinition] =
    Vector(new Operation(false, host, definition, evidence), new Operation(true, host, definition, evidence))

  private final class Operation(resume: Boolean, host: Host, definition: DefinitionBinding,
    evidence: EvidencePort) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (resume)
      SmWorkflowComponent.GoalTaskServiceService.ResumeGoalTaskRepairOperation
      else SmWorkflowComponent.GoalTaskServiceService.DescribeGoalTaskRepairContinuationOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          input <- if (resume) NativeWorkflowOperationInput.valueC[V.GoalTaskRepairContinuationInput](incoming, "input")
            .map[Either[V.GoalTaskRepairContinuationInput, V.GoalTaskRepairContinuationDescriptionInput]](Left(_))
            else NativeWorkflowOperationInput.valueC[V.GoalTaskRepairContinuationDescriptionInput](incoming, "input")
              .map[Either[V.GoalTaskRepairContinuationInput, V.GoalTaskRepairContinuationDescriptionInput]](Right(_))
          session = input.fold(_.sessionId, _.sessionId)
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
          selected = core.copy(executionContext = context)
          storage <- N.storageC(host, definition, selected)
          instances <- storage.bindInstancesC()(using context)
          current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
            _.map(Consequence.success).getOrElse(N.invalid("Task continuation Session is absent")))
          _ <- WorkflowForceClosure.requireOpenC(current)
          _ <- if (current.definition == definition && current.lifecycle == Lifecycle.Active) Consequence.unit
            else N.invalid("Task continuation requires its exact active GoalTask definition")
          payload <- _payload_c(storage, current, selected)
          _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, context)
          result <- payload.repairContinuation match {
            case Some(retained) => for {
              target <- decodeTarget(retained.target).fold(N.invalid(_), Consequence.success)
              _ <- if (target.componentInstance == _owner(selected) && target.sessionId == session &&
                target.business.state.input == payload.state.input && target.participant == payload.state.participant &&
                retained.choice == choices.head) Consequence.unit else N.invalid("Retained Task continuation crosses its owner or request")
              _ <- input.fold(i => for {
                expected <- targetC(i.target)
                _ <- if (expected == target && Direction(expected.asJson, i.choice, i.rationale, i.participant,
                  ContextReference(i.reference.identity, i.reference.revision)) == retained) Consequence.unit
                  else N.invalid("Repeated Task continuation differs from its original admitted direction")
              } yield (), _ => Consequence.unit)
            } yield _output(target, "already-resumed", "Original admitted continuation retained; no work or checkpoint appended")
            case None => for {
              _ <- if (current.suspension.isEmpty && eligible(payload)) Consequence.unit
                else N.invalid("Task has pending work/effects, no retained repair-trend stop, or no remaining native allowance")
              frontier <- WorkflowInstanceRecordJsonV1.encodeC(current).flatMap(s =>
                io.circe.parser.parse(s).fold(e => N.invalid(e.message), Consequence.success))
              target = Target(_owner(selected), session, payload.state.participant, current.revision.value, frontier, payload)
              result <- input match {
                case Right(_) => Consequence.success(_output(target, "decision-required", "Separate exact Human admission is required"))
                case Left(answer) => for {
                  expected <- targetC(answer.target)
                  _ <- if (expected == target) Consequence.unit else N.invalid("Task continuation target changed; describe and admit fresh Human direction")
                  reference = ContextReference(answer.reference.identity, answer.reference.revision)
                  binding <- bindingC(_owner(selected), target, answer.choice, answer.rationale, answer.participant, reference)
                  _ <- evidence.admitC(selected, reference, binding)
                  result <- if (answer.choice == "Defer") Consequence.success(_output(target, "deferred", "Human deferred; native state unchanged"))
                    else for {
                      _ <- if (host.root == payload.root && host.branch == payload.branch &&
                        host.repository == payload.state.input.repositoryIdentity) Consequence.unit
                        else N.invalid("Current host crosses the retained Task source root, branch or repository")
                      // Native read-only root/HEAD/index/status observation. This
                      // grants no source mutation, staging, work or check effect.
                      _ <- N.freshC(payload, selected)
                      direction = Direction(target.asJson, answer.choice, answer.rationale, answer.participant, reference)
                      next = payload.copy(state = payload.state.copy(stage = "Repair"), repairContinuation = Some(direction))
                      _ <- N.saveC(host, storage, current, next, selected)
                    } yield _output(target, "resumed-awaiting-advance",
                      "Same Task Repair checkpoint retained; separate ordinary Advance owns the next Repair")
                } yield result
              }
            } yield result
          }
        } yield result
      }
    })
  }
  private def _output(target: Target, outcome: String, message: String): OperationResponse =
    NativeWorkflowOperationOutput.response(V.GoalTaskRepairContinuationDescriptionResult(target.sessionId,
      target.componentInstance, outcome, message, target.asJson.noSpaces, choices, resumeOperation))
}
