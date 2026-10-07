package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentInstanceId}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request as ProtocolRequest}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import RepositorySyncManagedFetch as F
import RepositorySyncNativeConflictInput as I
import RepositorySyncConflictSemantic as S
import RepositorySyncNativeProgressStore as N

/** Genuine Decision after a retained AI DecisionNeeded. The source reader is
  * the owning conflict port, not a caller-provided snapshot or workflow stage.
  * Answers are native atomic completion; only a subsequent advance runs effects.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncHumanConflict {
  val completionOperation = StateMachineOperationIdentity("RepositorySyncService", "submitRepositoryConflictDecision")
  val closingType = StateMachineResultTypeReference("RepositorySyncHumanConflictSubmissionResult")
  val choices = Vector("ProvideResolution", "Defer", "Cancel")
  val purpose = "repository-sync-human-conflict-answer"
  final case class Input(conflict: I.Request, requestingResultIdentity: String,
    requestingResultRevision: String, rationale: String)
  final case class Answer(choice: String, resolutions: Vector[S.Resolution], rationale: String)
  final case class Binding(componentInstance: ComponentInstanceId, question: WorkflowInteraction[Input, Nothing],
    result: ContinuationResult[Answer], submittingParticipant: String)
  final case class Receipt(reference: ContextReference, issuer: String, humanParticipant: String,
    purposeIdentity: String, binding: Binding, occurredAt: Instant, validUntil: Instant)
  trait Verifier {
    def issuerIdentity: String
    def verifyForCallC(reference: ContextReference, binding: Binding, core: ActionCall.Core): Consequence[Option[Receipt]]
  }
  final case class Installation(verifier: Verifier) {
    def validateC: Consequence[Unit] = if (verifier != null) Consequence.unit
      else invalid("paired Human installation requires an independent host verifier")
  }
  final case class Source(session: F.Session, store: N.Store, input: I.Request, decision: ContextReference,
    rationale: String, from: InstanceRecord, current: InstanceRecord)
  private final case class Plan(source: Source, intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent,
    next: InstanceRecord, question: WorkflowInteraction[Input, Nothing])
  private final class Admitted(receipt: Receipt, admittedCall: ActionCall.Core) {
    def forCallC(core: ActionCall.Core): Consequence[Unit] =
      if (core eq admittedCall) Consequence.unit else invalid("Human admission belongs to another call")
  }
  private final class Codec[A](val typeIdentity: String, schema: String)(using Encoder[A], Decoder[A])
      extends WorkflowResultJsonV1.PayloadCodec[A] {
    def encode(value: A): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, A] = for {
      root <- Option(value).filter(_.noSpaces.getBytes(UTF_8).length <= 16 * 1024 * 1024).flatMap(_.asObject)
        .filter(_.keys.toSet == Set("schemaVersion", "payload")).toRight("Human conflict envelope is incomplete or oversized")
      _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(schema), (), "Human conflict schema differs")
      parsed <- root("payload").get.as[A].left.map(_.message)
      _ <- Either.cond(encode(parsed) == value, (), "Human conflict has undeclared nested fields")
    } yield parsed
  }
  val inputCodec: WorkflowResultJsonV1.PayloadCodec[Input] =
    new Codec[Input]("RepositorySyncHumanConflictRequest", "sm-workflow.repository-human-conflict-input.v1")
  val answerCodec: WorkflowResultJsonV1.PayloadCodec[Answer] =
    new Codec[Answer]("RepositorySyncHumanConflictAnswer", "sm-workflow.repository-human-conflict-answer.v1")

  def proposal(answer: Answer): S.Result = S.Result(
    if (answer.choice == "ProvideResolution") "resolved" else "decision-needed", answer.resolutions, answer.rationale)
  def admitAnswerC(input: Input, answer: Answer, policy: I.Policy): Consequence[Unit] =
    if (input == null || answer == null || !choices.contains(answer.choice)) invalid("Human answer choice is unknown")
    else S.admitC(input.conflict, proposal(answer), policy)

  final class Port(declaration: F.Declaration, definition: DefinitionBinding, policy: I.Policy,
    installation: Installation, sourceReader: (String, ActionCall.Core) => Consequence[Source],
    acknowledgement: String => Consequence[Unit], sourceValidation: Option[RepositorySyncNativeMergeValidation.Policy],
    sourceReview: Option[RepositorySyncNativeMergeReviewContinuation.Port]) {
    def advanceC(source: Source): Consequence[String] = for {
      plan <- prepareC(source)
      questions <- source.session.storage.bindDecisionC(completionOperation, inputCodec)
      existing <- questions.loadC(identity(source))(using source.store.core.executionContext)
      _ <- existing match {
        case Some(value) => if (value == plan.question && prefix(source.current, plan.next)) Consequence.unit
          else invalid("retained Human question differs from its genuine conflict or native prefix")
        case None => for {
          _ <- if (same(source.current, source.from)) Consequence.unit
            else invalid("advanced conflict history has no genuine Human issue")
          saved <- questions.suspendC(plan.intent, plan.question)(using source.store.core.executionContext)
          _ <- if (same(saved.instance, plan.next)) Consequence.unit
            else uncertain("Human suspension committed readback differs; observe without retry")
          actual <- questions.loadC(identity(source))(using source.store.core.executionContext)
          _ <- if (actual.contains(plan.question)) Consequence.unit
            else uncertain("Human question committed readback differs; observe without retry")
          _ <- acknowledgement("human-conflict-issued")
        } yield ()
      }
      native <- completionC(source.session.storage)
      retained <- native.loadRetainedC(identity(source))(using source.store.core.executionContext)
      wire <- retained match {
        case None => WorkflowDecisionJsonV1.encodeC(plan.question, inputCodec)
        case Some(value) => for {
          next <- observeC(plan, value.completion, value.completion.result)
          answer = value.completion.result.result.value
          wire <- if (answer.choice == "ProvideResolution") for {
            pending <- RepositorySyncConflictMaterialization.advanceC(source.store, source.input, proposal(answer),
              source.current, next, policy)
            result <- RepositorySyncNativeMergeValidation.waitC(source.store, pending, sourceValidation, sourceReview)
          } yield result else waitC(next, value.completion.result)
        } yield wire
      }
    } yield wire

    def bindCompletionC(params: ComponentCreate): Consequence[spec.OperationDefinition] = for {
      _ <- installation.validateC
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
        Vector(definition), declaration.instances, declaration.continuations)
      _ <- storage.bindDecisionC(completionOperation, inputCodec)
      _ <- completionC(storage)
      provided <- RepositorySyncDefinitionBinding.providedDefinition.providedOperations.find(o =>
        o.service == completionOperation.service && o.name == completionOperation.operation &&
          o.inputType.contains(answerCodec.typeIdentity) && o.resultType.contains(closingType.value))
        .map(Consequence.success).getOrElse(invalid("generated Human Provided Operation differs"))
    } yield new spec.OperationDefinition {
      val specification = NativeWorkflowDescriptionMetadata.operation(
        SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositoryConflictDecisionOperation)
      def createOperationRequest(incoming: ProtocolRequest): Consequence[OperationRequest] = decodeC(incoming).map { submitted =>
        new CommandAction {
          val request = incoming
          override def commandExecutionMode = CommandExecutionMode.Sync
          def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
            val core = c
            private var delegate = Option.empty[ActionCall]
            def execute(): Consequence[OperationResponse] = for {
              source <- sourceReader(submitted.handle.instanceIdentity.value, core)
              plan <- prepareC(source)
              actualStorage = source.session.storage
              questions <- actualStorage.bindDecisionC(completionOperation, inputCodec)
              issued <- questions.loadC(submitted.continuationId)(using core.executionContext)
              _ <- if (issued.contains(plan.question) && prefix(source.current, plan.next)) Consequence.unit
                else invalid("answer lacks its actual issued Human question or complete native prefix")
              _ <- WorkflowProtocolV1.admitDecisionResultC(plan.question, submitted).map(_ => ())
              native <- completionC(actualStorage)
              retained <- native.loadRetainedC(submitted.continuationId)(using core.executionContext)
              response <- retained match {
                case Some(value) => observeC(plan, value.completion, submitted).flatMap(waitC(_, submitted)).map(OperationResponse.Scalar(_))
                case None => for {
                  _ <- admitAnswerC(plan.question.current.asInstanceOf[WorkflowContinuation.Decision[Input]].request.input.get.value,
                    submitted.result.value, policy)
                  admitted <- verifyC(core, plan.question, submitted, source.session.command.participantIdentity, installation.verifier)
                  bound <- freshC(core, provided, plan, submitted, admitted, actualStorage)
                  action <- bound.createOperationRequest(core.action.request).flatMap {
                    case value: CommandAction => Consequence.success(value)
                    case _ => invalid("canonical Human completion is not a CommandAction")
                  }
                  call = action.createCall(core.copy(action = action))
                  result <- { delegate = Some(call); call.execute() }
                } yield result
              }
            } yield response
            override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
              val selected = delegate; delegate = None
              selected.fold(Consequence.success(response))(_.afterCommitResponseC(response))
            }
          }
        }
      }
    }

    private def freshC(core: ActionCall.Core, provided: GeneratedProvidedApiAbi.Operation, plan: Plan,
      submitted: ContinuationResult[Answer], admitted: Admitted, storage: WorkflowComponentStorage): Consequence[spec.OperationDefinition] = for {
      questions <- storage.bindDecisionC(completionOperation, inputCodec)
      instances <- storage.bindInstancesC()(using core.executionContext)
      native <- completionC(storage)
      next <- completedC(plan, submitted)
      bound <- {
        val codec = new WorkflowCompletionServiceOperation.Codec[Answer] {
          val resultTypeIdentity = answerCodec.typeIdentity
          def decodeC(request: ProtocolRequest) = RepositorySyncHumanConflict.decodeC(request)
          def encodeC(resume: ContinuationRuntime.Resume) = invalid("Human acknowledgement requires committed readback")
          override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, result: ContinuationResult[Answer],
            completed: Option[InstanceRecord]): Consequence[OperationResponse] = for {
            _ <- admitted.forCallC(core)
            _ <- if (result == submitted && completed.exists(same(_, next)) && resume != null &&
                resume.result == StateMachineOperationResult(closingType, submitted.resultReference)) Consequence.unit
              else uncertain("Human completion lacks its actual native closing result")
            _ <- acknowledgement("human-conflict-completed")
            actual <- native.loadRetainedC(submitted.continuationId)(using core.executionContext).flatMap {
              case Some(value) => Consequence.success(value.completion)
              case None => uncertain("Human completion committed readback is absent; observe without retry")
            }
            current <- sourceReader(submitted.handle.instanceIdentity.value, core)
            selected <- prepareC(current)
            observed <- observeC(selected, actual, submitted)
            wire <- waitC(observed, submitted)
          } yield OperationResponse.Scalar(wire)
        }
        val closing = new WorkflowCompletionServiceOperation.ClosingProgram[Answer] {
          def selectC(result: ContinuationResult[Answer], current: InstanceRecord) = for {
            _ <- admitted.forCallC(core)
            _ <- if (result == submitted && same(current, plan.next) && same(plan.source.current, current)) Consequence.unit
              else invalid("Human closing crosses its actual question or current suspension")
            calculated <- completedC(plan, result)
            program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              ActionExecution.Completed(StateMachineOperationResult(closingType, result.resultReference)))
          } yield WorkflowCompletionServiceOperation.SelectedClosing(program, Some(calculated))
        }
        WorkflowDecisionCompletionServiceOperation.bindC(RepositorySyncDefinitionBinding.providedDefinition, provided,
          WorkflowJsonCommandRequest.definition, spec.ResponseDefinition.void, codec, questions, instances, closing, native)
      }
    } yield bound

    private def prepareC(source: Source): Consequence[Plan] = for {
      _ <- installation.validateC
      _ <- policy.validateC
      _ <- if (source != null && source.from != null && source.current != null && source.input != null &&
          source.from.lifecycle == Lifecycle.Active && source.from.suspension.isEmpty &&
          source.from.currentProgression.contains(ProgressionReference("sync.ConflictDecisionRequired")) &&
          prefix(source.current, source.from) && source.input.instanceIdentity == source.from.identity.value &&
          source.decision != null && source.rationale != null && source.rationale.trim.nonEmpty) Consequence.unit
        else invalid("Human source is not a genuine retained conflict DecisionNeeded")
      required <- RepositorySyncDefinitionBinding.humanConflictOperationC
      input = Input(source.input, source.decision.identity, source.decision.revision, source.rationale)
      bundle = ContextBundle("Human resolution of the original native repository conflict",
        Vector(source.decision), Vector.empty,
        ContextSnapshot(source.from.definition.workflowRevision.value, evidenceRevision = Some(source.input.conflictRevision)))
      continuation = Continuation(StateMachineRunIdentity(source.from.identity.value), identity(source),
        StateMachineRevision(source.from.revision.next.value.toString), required, bundle)
      entry = HistoryEntry(HistorySequence(source.from.history.size.toLong + 1), source.from.revision.next, Lifecycle.Active,
        source.from.currentProgression, source.from.correlation, CausationReference(identity(source).value + ":issued"),
        source.from.derivedCompositeOccurrence, source.from.committedPredecessor,
        Some(SuspensionBoundary(NativeIdentity(identity(source).value), source.from.revision.next,
          ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(required.metadata.completionContract.identity),
          EvidenceReference(required.metadata.evidenceContract.identity), source.from.correlation)))
      intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
        declaration.configuration, source.from, source.from.revision, entry,
        ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available))
      admitted <- WorkflowInstanceAtomicTransitionV1.admitSuspensionC(intent)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, source.from)
      question = WorkflowInteraction[Input, Nothing](handle, WorkflowContinuation.Decision(
        ContinuationRequest(continuation.runId, continuation.continuationId, continuation.expectedRevision, required.identity,
          required.operation, Some(TypedValue(inputCodec.typeIdentity, input)), required.resultType, bundle,
          required.metadata.completionContract, required.metadata.evidenceContract, completionOperation),
        MinimalPresentation("Repository conflict needs Human direction", Json.obj("choices" -> Json.arr(choices.map(Json.fromString)*),
          "notice" -> Json.fromString("Answer only the issued conflict. Workflow retains command authority; Defer/Cancel do not resolve files.")).noSpaces)))
    } yield Plan(source, intent, admitted.next, question)

    private def completedC(plan: Plan, result: ContinuationResult[Answer]): Consequence[InstanceRecord] = for {
      _ <- WorkflowProtocolV1.admitDecisionResultC(plan.question, result).map(_ => ())
      _ <- admitAnswerC(plan.question.current.asInstanceOf[WorkflowContinuation.Decision[Input]].request.input.get.value, result.result.value, policy)
      choice = result.result.value.choice
      stage = if (choice == "ProvideResolution") "MergeResolutionAdmitted" else "ConflictDecisionRequired"
      event = if (choice == "ProvideResolution") "human-conflict-resolution-admitted" else if (choice == "Defer") "human-conflict-deferred" else "human-conflict-cancelled"
      _ <- if (domain.statemachine.RepositorySyncBoundaryLifecycle.permits("ConflictDecisionRequired", event, stage)) Consequence.unit
        else invalid("Human successor is not declared by CML")
      from = plan.next
      next <- from.appendC(from.revision, HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next,
        Lifecycle.Active, Some(ProgressionReference("sync." + stage)), from.correlation,
        CausationReference(result.resultReference.identity), from.derivedCompositeOccurrence, from.committedPredecessor, None))
    } yield next
    private def observeC(plan: Plan, completion: WorkflowCompletionRecordJsonV1.Record[Answer], result: ContinuationResult[Answer]): Consequence[InstanceRecord] = for {
      next <- completedC(plan, result)
      _ <- if (completion.result == result && completion.instanceRevision == next.revision &&
          completion.closingResult.contains(StateMachineOperationResult(closingType, result.resultReference)) &&
          prefix(plan.source.current, next)) Consequence.unit
        else invalid("retained Human answer crosses its actual completion or native history")
    } yield next
  }

  private def verifyC(core: ActionCall.Core, question: WorkflowInteraction[Input, Nothing], result: ContinuationResult[Answer],
    participant: String, verifier: Verifier): Consequence[Admitted] = try {
    if (core == null || core.executionContext == null || core.executionContext.clock == null || verifier == null ||
        !core.component.exists(c => c.componentId == SmWorkflowComponent.componentId && c.subsystem.nonEmpty) ||
        !core.action.request.service.contains(completionOperation.service) || core.action.request.operation != completionOperation.operation)
      invalid("Human verification requires the actual owning completion call")
    else for {
      _ <- GoalPhaseParticipantAdmission.admitC(participant, core.executionContext)
      binding = Binding(core.component.get.core.instanceId, question, result, participant)
      issuer = verifier.issuerIdentity
      _ <- if (canonical(issuer)) Consequence.unit else invalid("Human issuer is absent")
      observed <- verifier.verifyForCallC(result.resultReference, binding, core)
      now = core.executionContext.clock.instant()
      receipt <- observed match {
        case Some(value) if value != null && value.reference == result.resultReference && value.binding == binding &&
            value.issuer == issuer && canonical(value.humanParticipant) && value.purposeIdentity == purpose &&
            value.occurredAt != null && value.validUntil != null && !value.occurredAt.isAfter(now) && now.isBefore(value.validUntil) &&
            verifier.issuerIdentity == issuer => Consequence.success(value)
        case _ => invalid("independent Human evidence is absent stale or differs from the entire question/result")
      }
    } yield new Admitted(receipt, core)
  } catch { case NonFatal(_) => Consequence.serviceUnavailable("Repository Human evidence observation is indeterminate; no answer admitted") }
  private def completionC(storage: WorkflowComponentStorage): Consequence[WorkflowManagedDecisionCompletion[Answer]] =
    storage.bindDecisionCompletionC(completionOperation, inputCodec, answerCodec, closingType, (_, _) => Consequence.success(Vector.empty))
  private def identity(source: Source): ContinuationIdentity =
    ContinuationIdentity(source.from.identity.value + ":human-conflict:" + source.from.revision.value)
  private def decodeC(request: ProtocolRequest): Consequence[ContinuationResult[Answer]] =
    if (request == null || !request.service.contains(completionOperation.service) || request.operation != completionOperation.operation)
      invalid("answer selects another Operation")
    else WorkflowJsonCommandRequest.commandArguments(request) match {
      case List(Argument("arg1", wire: String, _)) => WorkflowResultJsonV1.decodeC(wire, answerCodec)
      case _ => invalid("Human completion requires one complete typed result")
    }
  private def waitC(next: InstanceRecord, result: ContinuationResult[Answer]): Consequence[String] = for {
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, next)
    wire <- WorkflowWaitJsonV1.encodeC(WorkflowInteraction[Nothing, Nothing](handle,
      WorkflowContinuation.Wait(StateMachineRevision(next.revision.value.toString), result.contextSnapshot,
        MinimalPresentation("Repository Human answer retained", result.result.value.choice match {
          case "ProvideResolution" => "Resolution retained; owning advance still requires materialization, validation, review and authorized convergence."
          case "Defer" => "Human deferred the conflict; no automatic question, file choice, command or closure follows."
          case _ => "Human cancelled this decision path; pending repository state is preserved, not automatically aborted or converged."
        }))))
  } yield wire
  private def same(a: InstanceRecord, b: InstanceRecord) = SplitPhaseDefinitionBinding.sameRecord(a, b)
  private def prefix(current: InstanceRecord, prior: InstanceRecord) = current.identity == prior.identity &&
    SplitPhaseDefinitionBinding.sameDefinition(current.definition, prior.definition) && current.history.take(prior.history.size) == prior.history
  private def canonical(v: String) = v != null && v.nonEmpty && v.trim == v && !v.exists(_.isControl)
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync Human conflict " + reason)
  private def uncertain[A](reason: String): Consequence[A] = org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
}
