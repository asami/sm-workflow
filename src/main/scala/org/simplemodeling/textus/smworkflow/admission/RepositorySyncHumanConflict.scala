package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
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
  * @version Oct. 14, 2026
  */
private[smworkflow] object RepositorySyncHumanConflict {
  val completionOperation = StateMachineOperationIdentity("RepositorySyncService", "submitRepositoryConflictDecision")
  val closingType = StateMachineResultTypeReference("RepositorySyncHumanConflictSubmissionResult")
  val choices = Vector("ProvideResolution", "Defer", "Cancel")
  val purpose = "repository-sync-human-conflict-answer"
  final case class Input(conflict: I.Request, requestingResultIdentity: String,
    requestingResultRevision: String, rationale: String)
  final case class Answer(choice: String, resolutions: Vector[S.Resolution], rationale: String)
  final case class AnswerContext(componentInstance: ComponentInstanceId, question: WorkflowInteraction[Input, Nothing],
    result: ContinuationResult[Answer], submittingParticipant: String)
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, target: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else invalid("paired Human installation requires independent host evidence")
  }
  final case class Source(session: F.Session, store: N.Store, input: I.Request, decision: ContextReference,
    rationale: String, from: InstanceRecord, current: InstanceRecord)
  private final case class Plan(source: Source, intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent,
    next: InstanceRecord, question: WorkflowInteraction[Input, Nothing])
  private final class Codec[A](val typeIdentity: String, schema: String)(using Encoder[A], Decoder[A])
      extends WorkflowResultJsonV1.PayloadCodec[A] {
    def encode(value: A): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, A] = for {
      json <- Option(value).toRight("Current typed payload is missing")
      version <- json.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(version == schema, (), "Current typed payload schema differs")
      parsed <- json.hcursor.get[A]("payload").left.map(_.message)
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
    def advanceC(source: Source): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
      projected <- prepareC(source)
      questions <- source.session.storage.bindDecisionC(completionOperation, inputCodec)
      existing <- questions.loadC(identity(source))(using source.store.core.executionContext)
      plan <- existing match {
        case Some(value) => Consequence.success(projected.copy(question = value))
        case None => for {
          saved <- questions.suspendC(projected.intent, projected.question)(using source.store.core.executionContext)
          _ <- acknowledgement("human-conflict-issued")
        } yield projected.copy(next = saved.instance)
      }
      native <- completionC(source.session.storage)
      retained <- native.loadRetainedC(identity(source))(using source.store.core.executionContext)
      wire <- retained match {
        case None => NativeWorkflowOperationOutput.repositoryHumanC(plan.question, source.store.core)
        case Some(value) => for {
          next <- completedC(plan, value.completion.result)
          answer = value.completion.result.result.value
          wire <- if (answer.choice == "ProvideResolution") for {
            pending <- RepositorySyncConflictMaterialization.advanceC(source.store,
              plan.question.current.asInstanceOf[WorkflowContinuation.Decision[Input]].request.input.get.value.conflict, proposal(answer),
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
        o.service == completionOperation.service && o.name == completionOperation.operation)
        .map(Consequence.success).getOrElse(invalid("Human completion Operation is absent"))
    } yield new NativeWorkflowResultInput.Receiver {
      val specification = NativeWorkflowDescriptionMetadata.operation(
        SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositoryConflictDecisionOperation)
      def createResultOperationRequestC(incoming: ProtocolRequest,
        result: ContinuationResult[NativeWorkflowResultInput.Value]): Consequence[OperationRequest] = NativeWorkflowResultInput.typedC(result, answerCodec).map { submitted =>
        new CommandAction {
          val request = incoming
          override def commandExecutionMode = CommandExecutionMode.Sync
          def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
            val core = c
            private var delegate = Option.empty[ActionCall]
            def execute(): Consequence[OperationResponse] = for {
              source <- sourceReader(submitted.handle.instanceIdentity.value, core)
              projected <- prepareC(source)
              actualStorage = source.session.storage
              questions <- actualStorage.bindDecisionC(completionOperation, inputCodec)
              issued <- questions.loadC(submitted.continuationId)(using core.executionContext)
              question <- issued.map(Consequence.success).getOrElse(invalid("answer has no actual issued Human question"))
              plan = projected.copy(question = question)
              response <- for {
                  _ <- verifyC(core, plan.question, submitted, source.session.context.participantIdentity, installation.evidence)
                  bound <- freshC(core, provided, plan, submitted, actualStorage)
                  action <- bound.createOperationRequest(core.action.request).flatMap {
                    case value: CommandAction => Consequence.success(value)
                    case _ => invalid("canonical Human completion is not a CommandAction")
                  }
                  call = action.createCall(core.copy(action = action))
                  result <- { delegate = Some(call); call.execute() }
                } yield result
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
      submitted: ContinuationResult[Answer], storage: WorkflowComponentStorage): Consequence[spec.OperationDefinition] = for {
      questions <- storage.bindDecisionC(completionOperation, inputCodec)
      instances <- storage.bindInstancesC()(using core.executionContext)
      native <- completionC(storage)
      next <- completedC(plan, submitted)
      bound <- {
        val codec = new WorkflowCompletionServiceOperation.Codec[Answer] {
          val resultTypeIdentity = answerCodec.typeIdentity
          def decodeC(request: ProtocolRequest) = Consequence.success(submitted)
          def encodeC(resume: ContinuationRuntime.Resume) = invalid("Human acknowledgement requires committed readback")
          override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, result: ContinuationResult[Answer],
            completed: Option[InstanceRecord]): Consequence[OperationResponse] = for {
            actual <- completed.map(Consequence.success).getOrElse(uncertain("Human completion has no committed instance"))
            _ <- acknowledgement("human-conflict-completed")
            // The managed completion already supplies committed state; do not
            // reread the source, question and answer as a body certificate.
          } yield NativeWorkflowOperationOutput.response(NativeWorkflowOperationOutput.accepted(result, actual))
        }
        val closing = new WorkflowCompletionServiceOperation.ClosingProgram[Answer] {
          def selectC(result: ContinuationResult[Answer], current: InstanceRecord) = {
            val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              ActionExecution.Completed(StateMachineOperationResult(closingType, result.resultReference)))
            Consequence.success(WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next)))
          }
        }
        WorkflowDecisionCompletionServiceOperation.bindC(RepositorySyncDefinitionBinding.providedDefinition, provided,
          SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositoryConflictDecisionOperation.specification.request, SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositoryConflictDecisionOperation.specification.response, codec, questions, instances, closing, native)
      }
    } yield bound

    private def prepareC(source: Source): Consequence[Plan] = for {
      _ <- installation.validateC
      _ <- policy.validateC
      _ <- if (source != null && source.from != null && source.current != null && source.input != null &&
          source.from.lifecycle == Lifecycle.Active && source.from.suspension.isEmpty &&
          source.from.currentProgression.contains(ProgressionReference("sync.ConflictDecisionRequired")) &&
          source.input.instanceIdentity == source.from.identity.value &&
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
      next <- source.from.appendC(source.from.revision, entry)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, source.from)
      question = WorkflowInteraction[Input, Nothing](handle, WorkflowContinuation.Decision(
        ContinuationRequest(continuation.runId, continuation.continuationId, continuation.expectedRevision, required.identity,
          required.operation, Some(TypedValue(inputCodec.typeIdentity, input)), required.resultType, bundle,
          required.metadata.completionContract, required.metadata.evidenceContract, completionOperation),
        MinimalPresentation("Repository conflict needs Human direction", Json.obj("choices" -> Json.arr(choices.map(Json.fromString)*),
          "notice" -> Json.fromString("Answer only the issued conflict. Workflow retains command authority; Defer/Cancel do not resolve files.")).noSpaces)))
    } yield Plan(source, intent, next, question)

    private def completedC(plan: Plan, result: ContinuationResult[Answer]): Consequence[InstanceRecord] = for {
      _ <- admitAnswerC(plan.question.current.asInstanceOf[WorkflowContinuation.Decision[Input]].request.input.get.value, result.result.value, policy)
      choice = result.result.value.choice
      stage = if (choice == "ProvideResolution") "MergeResolutionAdmitted" else "ConflictDecisionRequired"
      from = plan.next
      next <- from.appendC(from.revision, HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next,
        Lifecycle.Active, Some(ProgressionReference("sync." + stage)), from.correlation,
        CausationReference(result.resultReference.identity), from.derivedCompositeOccurrence, from.committedPredecessor, None))
    } yield next
  }

  private def verifyC(core: ActionCall.Core, question: WorkflowInteraction[Input, Nothing], result: ContinuationResult[Answer],
    participant: String, evidence: EvidencePort): Consequence[Unit] = try {
    if (core == null || core.executionContext == null || core.executionContext.clock == null || evidence == null ||
        !core.component.exists(c => c.componentId == SmWorkflowComponent.componentId && c.subsystem.nonEmpty) ||
        !core.action.request.service.contains(completionOperation.service) || core.action.request.operation != completionOperation.operation)
      invalid("Human verification requires the actual owning completion call")
    else for {
      _ <- GoalPhaseParticipantAdmission.admitC(participant, core.executionContext)
      target <- ThreeWorkflowHostEvidencePorts.repositoryHumanBindingC(AnswerContext(core.component.get.core.instanceId,
        question, result, participant))
      _ <- Option(evidence.admitC(core, result.resultReference, target)).getOrElse(
        Consequence.serviceUnavailable("Repository Human host evidence returned no outcome"))
    } yield ()
  } catch { case NonFatal(_) => Consequence.serviceUnavailable("Repository Human evidence observation is indeterminate; no answer admitted") }
  private def completionC(storage: WorkflowComponentStorage): Consequence[WorkflowManagedDecisionCompletion[Answer]] =
    storage.bindDecisionCompletionC(completionOperation, inputCodec, answerCodec, closingType, (_, _) => Consequence.success(Vector.empty))
  private def identity(source: Source): ContinuationIdentity =
    ContinuationIdentity(source.from.identity.value + ":human-conflict:" + source.from.revision.value)
  private def waitC(next: InstanceRecord, result: ContinuationResult[Answer]): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = {
    val detail = result.result.value.choice match {
          case "ProvideResolution" => "Resolution retained; owning advance still requires materialization, validation, review and authorized convergence."
          case "Defer" => "Human deferred the conflict; no automatic question, file choice, command or closure follows."
          case _ => "Human cancelled this decision path; pending repository state is preserved, not automatically aborted or converged."
        }
    Consequence.success(NativeWorkflowOperationOutput.repositoryWait(next.identity.value, detail, result.result.value.choice))
  }
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync Human conflict " + reason)
  private def uncertain[A](reason: String): Consequence[A] = org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
}
