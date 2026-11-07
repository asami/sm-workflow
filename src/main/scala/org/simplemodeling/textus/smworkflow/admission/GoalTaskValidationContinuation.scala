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

/** Explicit supplemental validation for an actual completed legacy Assess batch.
  * The normal native checkpoint owns persistence; ordinary Advance owns checks.
  * @since Nov.  7, 2026
  * @version Nov.  7, 2026
  */
private[smworkflow] object GoalTaskValidationContinuation {
  val purpose = "goal-task-validation-continuation"
  val resumeOperation = "adoptGoalTaskValidationContinuation"
  val choices = Vector("AuthorizeMissingChecks", "Defer")
  final case class Target(componentInstance: String, sessionId: String, participant: String,
    frontierRevision: Long, frontier: Json, business: Payload, supplemental: Vector[GoalPhaseNativeValidation.Check])
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
    "frontier" -> target.frontier, "business" -> target.business.asJson, "supplemental" -> target.supplemental.asJson))
  given Decoder[Target] = Decoder.instance(cursor => for {
    owner <- cursor.get[String]("componentInstance")
    session <- cursor.get[String]("sessionId")
    participant <- cursor.get[String]("participant")
    revision <- cursor.get[Long]("frontierRevision")
    frontier <- cursor.get[Json]("frontier")
    business <- cursor.get[Payload]("business")
    supplemental <- cursor.get[Vector[GoalPhaseNativeValidation.Check]]("supplemental")
  } yield Target(owner, session, participant, revision, frontier, business, supplemental))
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  private def _exact(value: String): Boolean = GoalPhaseRuntimeRepairControl.exact(value)

  /** The completed original batch remains an exact successful prefix. */
  def missing(payload: Payload): Vector[GoalPhaseNativeValidation.Check] = {
    val s = payload.state
    val kinds = Vector(
      Option.when(!s.selected.exists(_.kind == WorkflowSbtValidation.Kind.Compile))("compile" -> WorkflowSbtValidation.Kind.Compile),
      Option.when(!s.selected.zip(s.receipts).exists((c, r) => c.kind == WorkflowSbtValidation.Kind.TestOnly &&
        r.sbt.flatMap(_.selection).exists(x => x.completedSuites > 0 && x.testsRun > 0)))("test-only" -> WorkflowSbtValidation.Kind.TestOnly),
      Option.when(s.input.scalaLint == "required" && !s.selected.exists(_.kind == WorkflowSbtValidation.Kind.FixCheck))("fix-check" -> WorkflowSbtValidation.Kind.FixCheck)).flatten
    if (s.selected.size.toLong + kinds.size.toLong > Int.MaxValue.toLong) Vector.empty
    else kinds.zipWithIndex.map { case ((name, kind), i) =>
      GoalPhaseNativeValidation.Check("supplemental-" + name, (s.selected.size.toLong + i.toLong + 1L).toInt, kind)
    }
  }
  def eligible(payload: Payload): Boolean = {
    val s = payload.state
    s.input.task.nonEmpty && s.input.delegation.isEmpty && s.input.kind == "code" && s.stage == "Assess" &&
      s.semanticReference.nonEmpty && s.candidate.nonEmpty && s.pendingCheck.isEmpty && s.commit.isEmpty &&
      s.commitExecution.isEmpty && s.commitReconciliation.isEmpty && s.recoveredValidation.isEmpty &&
      payload.supersededWork.isEmpty && payload.hygiene.isEmpty && payload.records.isEmpty &&
      payload.validationContinuation.isEmpty && s.selected.nonEmpty && s.selected.size == s.receipts.size &&
      s.selected.map(_.identity).distinct == s.selected.map(_.identity) &&
      s.selected.zipWithIndex.forall((c, i) => c.ordinal == i + 1 && c.dependency.isEmpty) &&
      s.selected.zip(s.receipts).forall((c, r) => c.identity == r.identity &&
        RepositorySyncNativeMergeValidation.productSuccessful(r, c.kind)) &&
      missing(payload).nonEmpty &&
      (s.selected ++ missing(payload)).map(_.identity).distinct.size == (s.selected ++ missing(payload)).size
  }

  private def _decode_target(value: Json): Either[String, Target] = for {
    target <- value.as[Target].left.map(_.message)
    _ <- Either.cond(target.asJson == value &&
      Vector(target.componentInstance, target.sessionId, target.participant).forall(_exact) &&
      target.frontierRevision > 0 && target.frontier.isObject &&
      target.participant == target.business.state.participant && eligible(target.business) &&
      target.supplemental == missing(target.business),
      (), "Task continuation target is incomplete, unsupported or not an eligible completed validation batch")
  } yield target

  def decodeTarget(value: Json): Either[String, Target] = _decode_target(value)

  private def _retained(payload: Payload, owner: String, session: String, revision: Long): Either[String, Vector[(Direction, Target)]] =
    payload.validationContinuation match {
      case None => Right(Vector.empty)
      case Some(direction) => decodeTarget(direction.target).flatMap { target =>
        val original = target.business.state
        val state = payload.state
        val suffix = target.supplemental
        if (target.componentInstance == owner && target.sessionId == session && target.frontierRevision < revision &&
            direction.choice == choices.head && direction.participant == target.participant && _exact(direction.rationale) &&
            direction.reference != null && _exact(direction.reference.identity) && _exact(direction.reference.revision) &&
            state.input == original.input && state.selected == original.selected ++ suffix &&
            state.generation >= original.generation && state.automaticCount >= original.automaticCount &&
            state.ordinaryCount >= original.ordinaryCount &&
            state.repairControl.policy == original.repairControl.policy &&
            state.repairControl.selectedLimit == original.repairControl.selectedLimit &&
            state.repairControl.restoration == original.repairControl.restoration &&
            (state.generation - original.generation == state.automaticCount.toLong - original.automaticCount.toLong) &&
            (state.ordinaryCount.toLong - original.ordinaryCount.toLong <= state.generation - original.generation) &&
            (if (state.generation == original.generation) state.candidate == original.candidate &&
              state.semanticReference == original.semanticReference &&
              state.receipts.take(original.receipts.size) == original.receipts &&
              state.automaticCount == original.automaticCount && state.ordinaryCount == original.ordinaryCount &&
              payload.implementationWorker == target.business.implementationWorker &&
              payload.implementationWorkers == target.business.implementationWorkers
            else {
              val before = target.business.implementationWorkers.getOrElse(Vector.empty)
              val after = payload.implementationWorkers.getOrElse(Vector.empty)
              after.take(before.size) == before && after.distinct == after &&
                payload.implementationWorker.forall(after.contains)
            }) && payload.root == target.business.root &&
            payload.branch == target.business.branch && payload.baseHead == target.business.baseHead)
          Right(Vector(direction -> target))
        else Left("Retained Task validation continuation crosses its original owner, checks, candidate or source")
      }
    }
  def retainedC(payload: Payload, owner: String, session: String, revision: Long): Consequence[Unit] =
    _retained(payload, owner, session, revision).fold(N.invalid(_), _ => Consequence.unit)
  def targetC(text: String): Consequence[Target] =
    if (text == null) N.invalid("Task continuation target is absent")
    else io.circe.parser.parse(text).left.map(_.message).flatMap(decodeTarget).fold(N.invalid(_), Consequence.success)

  def bindingC(componentInstance: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (componentInstance != target.componentInstance || participant != target.participant || !choices.contains(choice) ||
      !_exact(rationale) || reference == null || !_exact(reference.identity) || !_exact(reference.revision))
      N.invalid("Task continuation requires exact owner, participant, direction, rationale and Human reference")
    else Consequence.success(Json.obj("componentInstance" -> componentInstance.asJson,
      "goalTaskValidationContinuation" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("source" -> "Human".asJson, "choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> Json.obj("identity" -> reference.identity.asJson,
            "revision" -> reference.revision.asJson)))))

  def selectBinding(value: Json): Either[String, Json] = for {
    owner <- value.hcursor.get[String]("componentInstance").left.map(_.message)
    raw <- value.hcursor.downField("goalTaskValidationContinuation").get[Json]("target").left.map(_.message)
    target <- decodeTarget(raw)
    answer = value.hcursor.downField("goalTaskValidationContinuation").downField("answer")
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

  private def inputC(record: org.goldenport.record.Record): Consequence[V.GoalTaskValidationContinuationInput] =
    GoalPhasePrerequisitePublicationRecovery.businessRecordC[V.GoalTaskValidationContinuationInput](record,
      Set("sessionId", "target", "choice", "rationale", "participant", "reference"))
  def inputForCallC(incoming: Request, call: ActionCall.Core): Consequence[V.GoalTaskValidationContinuationInput] = {
    final class Reader extends ProcedureActionCall {
      val core = call
      def execute(): Consequence[OperationResponse] = N.invalid("Resource reader is not an operation")
      def readC: Consequence[V.GoalTaskValidationContinuationInput] = for {
        source <- GoalPhasePrerequisitePublicationRecovery.resourceInputC(incoming)
        record <- NativeWorkflowResourceInput.readC(source, reference => read_resource_text(reference,
          Some(java.nio.charset.StandardCharsets.UTF_8)).flatMap(GoalPhasePrerequisitePublicationRecovery.validateBusinessTextC))
        input <- inputC(record)
      } yield input
    }
    new Reader().readC
  }

  def operations(host: Host, definition: DefinitionBinding, evidence: EvidencePort): Vector[spec.OperationDefinition] =
    Vector(new Operation(false, host, definition, evidence), new Operation(true, host, definition, evidence))

  private final class Operation(resume: Boolean, host: Host, definition: DefinitionBinding,
    evidence: EvidencePort) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (resume)
      SmWorkflowComponent.GoalTaskServiceService.AdoptGoalTaskValidationContinuationOperation
      else SmWorkflowComponent.GoalTaskServiceService.DescribeGoalTaskValidationContinuationOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          input <- if (resume) inputForCallC(incoming, core).map[Either[V.GoalTaskValidationContinuationInput, String]](Left(_))
            else NativeWorkflowOperationInput.textC(incoming, "sessionId").map[Either[V.GoalTaskValidationContinuationInput, String]](Right(_))
          session = input.fold(_.sessionId, identity)
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
          _ <- if (!Set("Completed", "NoChange")(payload.state.stage)) Consequence.unit
            else N.invalid("Task continuation cannot reopen a terminal Task")
          _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, context)
          retained <- _retained(payload, _owner(selected), session, current.revision.value).fold(N.invalid(_), Consequence.success)
          requested <- input.fold(i => targetC(i.target).map(Some(_)), _ => Consequence.success(None))
          previous = requested.flatMap(target => retained.find(_._2 == target))
          result <- previous match {
            case Some((direction, target)) => input match {
              case Left(answer) =>
                if (Direction(target.asJson, answer.choice, answer.rationale, answer.participant,
                  ContextReference(answer.reference.identity, answer.reference.revision)) == direction)
                  Consequence.success(_output(target, "already-resumed", "Original admitted continuation retained; no work or checkpoint appended"))
                else N.invalid("Repeated Task continuation differs from its original admitted direction")
              case Right(_) => N.invalid("Task continuation collection requires its original direction")
            }
            case None if input.isRight && retained.nonEmpty =>
              Consequence.success(_output(retained.head._2, "already-resumed", "Latest admitted continuation retained; no work or checkpoint appended"))
            case None => for {
              _ <- if (current.suspension.isEmpty && eligible(payload)) Consequence.unit
                else N.invalid("Task has pending work/effects or is not an eligible completed legacy validation batch")
              frontier <- WorkflowInstanceRecordJsonV1.encodeC(current).flatMap(s =>
                io.circe.parser.parse(s).fold(e => N.invalid(e.message), Consequence.success))
              target = Target(_owner(selected), session, payload.state.participant, current.revision.value, frontier, payload, missing(payload))
              result <- input match {
                case Right(_) => Consequence.success(_output(target, "decision-required", "Separate exact Human admission is required"))
                case Left(answer) => for {
                  _ <- if (requested.contains(target)) Consequence.unit else N.invalid("Task continuation target changed; describe and admit fresh Human direction")
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
                      next = payload.copy(state = payload.state.copy(stage = "Validate", selected = payload.state.selected ++ missing(payload),
                        message = "Supplemental validation admitted; ordinary Advance owns typed binding admission and missing checks"), validationContinuation = Some(direction))
                      _ <- N.saveC(host, storage, current, next, selected)
                    } yield _output(target, "resumed-awaiting-advance",
                      "Same Task supplemental validation retained; separate ordinary Advance owns binding admission and the missing checks")
                } yield result
              }
            } yield result
          }
        } yield result
      }
    })
  }
  private def _output(target: Target, outcome: String, message: String): OperationResponse =
    NativeWorkflowOperationOutput.response(V.GoalTaskValidationContinuationDescriptionResult(target.sessionId,
      target.componentInstance, outcome, message, target.asJson.noSpaces, choices, resumeOperation))
}
