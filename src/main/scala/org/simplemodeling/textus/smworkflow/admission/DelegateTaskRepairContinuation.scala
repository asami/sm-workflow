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
import DelegateTaskNativeRun as N
import StandaloneFixExecution as S

/** Human continuations of actual retained trend or known-ended incomplete stops. The normal native
  * checkpoint transaction owns persistence; ordinary Advance owns all work.
  * @since Nov.  5, 2026
  * @version Nov. 20, 2026
  */
private[smworkflow] object DelegateTaskRepairContinuation {
  val purpose = "delegate-task-repair-continuation"
  val resumeOperation = "resumeDelegateTaskRepair"
  val choices = Vector("ContinueRepair", "Defer")
  enum Source {
    case RecordedIncomplete
  }
  private given Encoder[Source] = Encoder.encodeString.contramap {
    case Source.RecordedIncomplete => "recorded-incomplete.v1"
  }
  private given Decoder[Source] = Decoder.decodeString.emap {
    case "recorded-incomplete.v1" => Right(Source.RecordedIncomplete)
    case _ => Left("Unsupported Task repair continuation source")
  }
  final case class Target(componentInstance: String, sessionId: String, participant: String,
    frontierRevision: Long, frontier: Json, business: Payload, source: Option[Source] = None)
  def choicesFor(target: Target): Vector[String] = target.source match {
    case Some(Source.RecordedIncomplete) => Vector("RepairBeforeRetry", "Defer")
    case None => choices
  }
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
    "frontier" -> target.frontier, "business" -> target.business.asJson).mapObject(fields =>
      target.source.fold(fields)(source => fields.add("source", source.asJson))))
  given Decoder[Target] = Decoder.instance(cursor => for {
    owner <- cursor.get[String]("componentInstance")
    session <- cursor.get[String]("sessionId")
    participant <- cursor.get[String]("participant")
    revision <- cursor.get[Long]("frontierRevision")
    frontier <- cursor.get[Json]("frontier")
    business <- cursor.get[Payload]("business")
    source <- cursor.get[Option[Source]]("source")
  } yield Target(owner, session, participant, revision, frontier, business, source))
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  private def _exact(value: String): Boolean = GoalPhaseRuntimeRepairControl.exact(value)

  /** Formal failed trend stops and genuine known-ended Incomplete observations
    * have distinct choices. Pending intents and exhausted budgets stay stopped.
    */
  def eligible(payload: Payload): Boolean = _eligible(payload, metadata = false) ||
    _incomplete_eligible(payload, metadata = false)

  private def _incomplete_eligible(payload: Payload, metadata: Boolean): Boolean = {
    val state = payload.state
    val control = state.repairControl
    val ended = if (!metadata) S.validationRecoveryIndexC(state).isSuccess
    else state.receipts.nonEmpty && state.receipts.size <= state.selected.size &&
      state.receipts.dropRight(1).zip(state.selected).forall((receipt, check) =>
        _terminal_receipt(receipt, check, passed = true, metadata = true)) && {
        val receipt = state.receipts.last
        val check = state.selected(state.receipts.size - 1)
        receipt.identity == check.identity && Set(WorkflowSbtValidation.Kind.Test, WorkflowSbtValidation.Kind.TestOnly)(check.kind) &&
          receipt.sbt.exists(facts => facts.kind == check.kind && facts.outcome == WorkflowSbtValidation.Outcome.Incomplete) &&
          (receipt.externalCapture match {
            case Some(reference) => WorkflowSbtCapture.externalReferenceValid(reference) && reference.exitCode.isEmpty &&
              Set("output-limit-exceeded", "timed-out", "cancelled", "artifact-limit-exceeded")(reference.termination)
            // The prefix was checked as metadata above. Only the final inline
            // process is projected here; no external receipt is called loaded.
            case None => S.validationRecoveryIndexC(state.copy(selected = Vector(check), receipts = Vector(receipt))).isSuccess
          })
      }
    state.input.task.nonEmpty && state.input.delegation.exists(_.role == "destination") && state.input.kind != "validation" &&
      state.stage == "Wait" && state.pendingCheck.isEmpty && state.candidate.nonEmpty && ended &&
      state.commit.isEmpty && state.commitExecution.isEmpty && state.commitReconciliation.isEmpty &&
      payload.supersededWork.isEmpty && payload.hygiene.isEmpty && payload.records.isEmpty &&
      payload.validationCaptureReservation.forall(reservation => state.receipts.lastOption.exists(receipt =>
        receipt.externalCapture.exists(reference => reference.association == reservation.association &&
          reference.workspace == reservation.workspace && reference.path == reservation.path))) &&
      control.policy == GoalPhaseRuntimeRepairControl.version && control.selectedLimit == state.input.repairLimit &&
      state.automaticCount >= 0 && state.ordinaryCount >= 0 && state.ordinaryCount < Int.MaxValue &&
      state.automaticCount < control.effectiveLimit && state.generation < Long.MaxValue
  }

  private def _capture_bound(target: Target): Boolean = target.business.state.receipts.forall(receipt =>
    receipt.externalCapture.forall { reference =>
      val association = reference.association
      association.component == target.componentInstance && association.session == target.sessionId &&
        association.participant == target.participant && association.root == target.business.root &&
        association.branch == target.business.branch && association.intentRevision <= target.frontierRevision &&
        target.business.state.selected.exists(check => check.identity == association.check &&
          check.ordinal == association.ordinal && receipt.identity == check.identity) &&
        target.business.state.candidate.exists(candidate => candidate.head == association.baseHead &&
          candidate.tree == association.candidateTree)
    })

  /** A decoded external reference describes a retained observation; the native
    * loader must reopen its actual File before executable eligibility applies.
    */
  private def _terminal_receipt(receipt: RepositorySyncNativeMergeValidation.Receipt,
    check: GoalPhaseNativeValidation.Check, passed: Boolean, metadata: Boolean): Boolean = {
    val terminal = if (metadata && receipt.externalCapture.nonEmpty) receipt.externalCapture.exists(reference =>
      WorkflowSbtCapture.externalReferenceValid(reference) && reference.termination == "exited" &&
        (if (passed) reference.exitCode.contains(0) else reference.exitCode.exists(_ != 0)))
      else receipt.termination == "exited" &&
        (if (passed) receipt.exitCode.contains(0) else receipt.exitCode.exists(_ != 0)) &&
        (receipt.externalCapture.isEmpty || RepositorySyncNativeMergeValidation.storedProductOutputValid(receipt))
    terminal && receipt.identity == check.identity && receipt.sbt.exists(facts => facts.kind == check.kind &&
      facts.outcome == (if (passed) WorkflowSbtValidation.Outcome.Succeeded else WorkflowSbtValidation.Outcome.Failed))
  }

  private def _eligible(payload: Payload, metadata: Boolean): Boolean = {
    val state = payload.state
    val control = state.repairControl
    state.input.task.nonEmpty && state.input.delegation.exists(_.role == "destination") && state.input.kind != "validation" &&
      state.stage == "Human" && state.pendingCheck.isEmpty && state.commit.isEmpty && state.commitExecution.isEmpty &&
      state.commitReconciliation.isEmpty && payload.supersededWork.isEmpty && payload.hygiene.isEmpty && payload.records.isEmpty &&
      control.policy == GoalPhaseRuntimeRepairControl.version && control.selectedLimit == state.input.repairLimit &&
      control.stop.contains(state.message) && state.candidate.nonEmpty && state.receipts.nonEmpty &&
      state.receipts.size <= state.selected.size && state.receipts.zip(state.selected).forall((receipt, check) => receipt.identity == check.identity) &&
      state.receipts.dropRight(1).zip(state.selected).forall((receipt, check) =>
        _terminal_receipt(receipt, check, passed = true, metadata = metadata)) &&
      _terminal_receipt(state.receipts.last, state.selected(state.receipts.size - 1), passed = false, metadata = metadata) &&
      control.observations.lastOption.exists(observation => observation.scope == state.input.requestIdentity &&
        observation.reference == state.input.requestIdentity + ":generation:" + state.generation + ":" + state.receipts.last.identity) &&
      state.automaticCount >= 0 && state.ordinaryCount >= 0 && state.ordinaryCount < Int.MaxValue &&
      state.automaticCount < control.effectiveLimit && state.generation < Long.MaxValue
  }

  private def _decode_target(value: Json): Either[String, Target] = for {
    target <- value.as[Target].left.map(_.message)
    _ <- Either.cond(target.asJson == value &&
      Vector(target.componentInstance, target.sessionId, target.participant).forall(_exact) &&
      target.frontierRevision > 0 && target.frontier.isObject &&
      target.participant == target.business.state.participant && (target.source match {
        case None => _eligible(target.business, metadata = true)
        case Some(Source.RecordedIncomplete) => _incomplete_eligible(target.business, metadata = true) && _capture_bound(target)
      }), (), "Task continuation target is incomplete, unsupported or not an eligible retained stop")
  } yield target

  // Each exact pre-resume business already contains the previous direction.
  // Decode this native ancestry once at the boundary, with the native allowance
  // as its finite bound; no second episode store or reconstructed target is used.
  private def _ancestry(target: Target, limit: Int): Either[String, Vector[(Direction, Target)]] = {
    @scala.annotation.tailrec
    def _walk_(current: Target, remaining: Int, collected: Vector[(Direction, Target)]): Either[String, Vector[(Direction, Target)]] =
      current.business.delegateRepairContinuation match {
        case None => Right(collected)
        case Some(direction) if remaining <= 0 => Left("Task continuation ancestry exceeds its native repair limit")
        case Some(direction) => _decode_target(direction.target) match {
          case Left(error) => Left(error)
          case Right(previous) =>
            if (previous.componentInstance != target.componentInstance || previous.sessionId != target.sessionId ||
              previous.participant != target.participant || previous.business.state.input != target.business.state.input ||
              previous.frontierRevision >= current.frontierRevision || direction.choice != choicesFor(previous).head ||
              direction.participant != target.participant || !_exact(direction.rationale) ||
              !_exact(direction.reference.identity) || !_exact(direction.reference.revision))
              Left("Task continuation ancestry crosses its owner, request, revision or admitted direction")
            else _walk_(previous, remaining - 1, collected :+ (direction -> previous))
        }
      }
    _walk_(target, limit, Vector.empty)
  }

  def decodeTarget(value: Json): Either[String, Target] = for {
    target <- _decode_target(value)
    _ <- _ancestry(target, target.business.state.repairControl.effectiveLimit - 1)
  } yield target

  private def _retained(payload: Payload, owner: String, session: String, revision: Long): Either[String, Vector[(Direction, Target)]] = {
    // The current payload need not itself be Human. Its latest original target
    // and all older targets must still be canonical eligible stops.
    val current = Target(owner, session, payload.state.participant, revision, Json.obj(), payload)
    _ancestry(current, payload.state.repairControl.effectiveLimit)
  }
  def targetC(text: String): Consequence[Target] =
    if (text == null) N.invalid("Task continuation target is absent")
    else io.circe.parser.parse(text).left.map(_.message).flatMap(decodeTarget).fold(N.invalid(_), Consequence.success)

  def bindingC(componentInstance: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (componentInstance != target.componentInstance || participant != target.participant || !choicesFor(target).contains(choice) ||
      !_exact(rationale) || reference == null || !_exact(reference.identity) || !_exact(reference.revision))
      N.invalid("Task continuation requires exact owner, participant, direction, rationale and Human reference")
    else Consequence.success(Json.obj("componentInstance" -> componentInstance.asJson,
      "delegateTaskRepairContinuation" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("source" -> "Human".asJson, "choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> Json.obj("identity" -> reference.identity.asJson,
            "revision" -> reference.revision.asJson)))))

  def selectBinding(value: Json): Either[String, Json] = for {
    owner <- value.hcursor.get[String]("componentInstance").left.map(_.message)
    raw <- value.hcursor.downField("delegateTaskRepairContinuation").get[Json]("target").left.map(_.message)
    target <- decodeTarget(raw)
    answer = value.hcursor.downField("delegateTaskRepairContinuation").downField("answer")
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
      SmWorkflowComponent.DelegateTaskServiceService.ResumeDelegateTaskRepairOperation
      else SmWorkflowComponent.DelegateTaskServiceService.DescribeDelegateTaskRepairContinuationOperation)
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
            else N.invalid("Task continuation requires its exact active DelegateTask definition")
          payload <- _payload_c(storage, current, selected)
          _ <- if (!Set("Completed", "NoChange")(payload.state.stage)) Consequence.unit
            else N.invalid("Task continuation cannot reopen a terminal Task")
          _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, context)
          retained <- _retained(payload, _owner(selected), session, current.revision.value).fold(N.invalid(_), Consequence.success)
          requested <- input.fold(i => targetC(i.target).map(Some(_)), _ => Consequence.success(None))
          previous = requested.flatMap(target => retained.find(_._2.asJson == target.asJson))
          result <- previous match {
            case Some((direction, target)) => input match {
              case Left(answer) =>
                if (Direction(target.asJson, answer.choice, answer.rationale, answer.participant,
                  ContextReference(answer.reference.identity, answer.reference.revision)) == direction)
                  Consequence.success(_output(target, "already-resumed", "Original admitted continuation retained; no work or checkpoint appended"))
                else N.invalid("Repeated Task continuation differs from its original admitted direction")
              case Right(_) => N.invalid("Task continuation collection requires its original direction")
            }
            case None if input.isRight && payload.state.stage != "Human" && retained.nonEmpty &&
                !(current.suspension.isEmpty && _incomplete_eligible(payload, metadata = false)) =>
              Consequence.success(_output(retained.head._2, "already-resumed", "Latest admitted continuation retained; no work or checkpoint appended"))
            case None => for {
              _ <- if (current.suspension.isEmpty && eligible(payload)) Consequence.unit
                else N.invalid("Task has pending work/effects, no eligible retained repair stop, or no remaining native allowance")
              frontier <- WorkflowInstanceRecordJsonV1.encodeC(current).flatMap(s =>
                io.circe.parser.parse(s).fold(e => N.invalid(e.message), Consequence.success))
              target = Target(_owner(selected), session, payload.state.participant, current.revision.value, frontier, payload,
                Option.when(payload.state.stage == "Wait")(Source.RecordedIncomplete))
              result <- input match {
                case Right(_) => Consequence.success(_output(target, "decision-required", "Separate exact Human admission is required"))
                case Left(answer) => for {
                  _ <- if (requested.exists(_.asJson == target.asJson)) Consequence.unit else N.invalid("Task continuation target changed; describe and admit fresh Human direction")
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
                      _ <- target.source match {
                        case None => N.freshC(payload, selected)
                        case Some(Source.RecordedIncomplete) => for {
                          source <- N.semanticSourceAssessmentC(payload, selected)
                          _ <- WorkflowManagementReconstruction.requireReconstructibleC(source)
                          selection <- WorkflowManagementReconstruction.selectedC(source, Some(reference), adoptProposed = true)
                          _ <- if (selection.adoptProposed && selection.reasons.isEmpty &&
                            Set(WorkflowManagementReconstruction.ResumePoint.FreshCapture,
                              WorkflowManagementReconstruction.ResumePoint.CurrentBoundary)(selection.resumePoint)) Consequence.unit
                            else N.invalid("Current recorded Incomplete source remains unresolved")
                        } yield ()
                      }
                      direction = Direction(target.asJson, answer.choice, answer.rationale, answer.participant, reference)
                      next = payload.copy(state = payload.state.copy(stage = "Repair"), delegateRepairContinuation = Some(direction),
                        validationRecovery = if (target.source.nonEmpty) None else payload.validationRecovery,
                        validationCaptureReservation = if (target.source.nonEmpty) None else payload.validationCaptureReservation)
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
  private def _output(target: Target, outcome: String, message: String): OperationResponse = {
    val state = target.business.state
    val prefix = state.receipts.dropRight(1).map(_.identity).mkString(", ")
    val detail = s"$message Retained repair batches: automatic=${state.automaticCount}, ordinary=${state.ordinaryCount}, " +
      s"limit=${state.repairControl.effectiveLimit}, generation=${state.generation}; successful check prefix=[$prefix]; " +
      s"retained receipts=${state.receipts.size}, trend observations=${state.repairControl.observations.size}. " +
      "The target contains the exact receipts and observations. Recurring check or test names are coarse trend evidence, not proof of a repeated root cause."
    NativeWorkflowOperationOutput.response(V.GoalTaskRepairContinuationDescriptionResult(target.sessionId,
      target.componentInstance, outcome, detail, target.asJson.noSpaces, choicesFor(target), resumeOperation))
  }
}
