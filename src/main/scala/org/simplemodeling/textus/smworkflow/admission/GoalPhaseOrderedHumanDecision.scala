package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request as ProtocolRequest}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseNativeRun as F
import GoalPhaseNativeWork as S
import GoalPhaseOrderedProgression as O

/** Human direction for a genuine retained ordered semantic decision. Answer
  * completion records direction only; owning advance issues any subsequent work.
  * The older closure-Human contract is deliberately not used for ordered work.
  * @since   Oct.  7, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object GoalPhaseOrderedHumanDecision {
  val completionOperation = StateMachineOperationIdentity("GoalPhaseService", "submitGoalPhaseOrderedDecision")
  val closingType = StateMachineResultTypeReference("GoalPhaseOrderedDecisionSubmissionResult")
  val purpose = "goal-phase-ordered-human-answer"
  val choices = Vector("ResumeAssignedWork", "Defer", "Cancel")
  final case class Input(work: S.Request, requestingResult: ContextReference, result: S.Result, reason: String)
  final case class Answer(choice: String, rationale: String)
  trait EvidencePort {
    def admitC(core: ActionCall.Core, question: WorkflowInteraction[Input, Nothing],
      result: ContinuationResult[Answer], participant: String): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else F.invalid("ordered Human answer requires its independent host evidence port")
  }
  final case class Source(session: F.Session, input: S.Request, controller: O.State,
    from: InstanceRecord, result: ContinuationResult[S.Result], candidate: Option[GoalPhaseNativeCandidate.Saved])
  private final case class Prepared(source: Source, question: WorkflowInteraction[Input, Nothing],
    intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent, pending: InstanceRecord)

  val inputCodec: WorkflowResultJsonV1.PayloadCodec[Input] = new WorkflowResultJsonV1.PayloadCodec[Input] {
    val typeIdentity = "GoalPhaseOrderedDecisionRequest"
    def encode(value: Input) = Json.obj("work" -> S.inputCodec.encode(value.work),
      "requestingResult" -> Json.obj("identity" -> Json.fromString(value.requestingResult.identity),
        "revision" -> Json.fromString(value.requestingResult.revision)),
      "result" -> S.resultCodec.encode(value.result), "reason" -> Json.fromString(value.reason))
    def decode(value: Json): Either[String, Input] = for {
      fields <- Option(value).flatMap(_.asObject)
        .toRight("ordered Decision input fields differ")
      workJson <- fields("work").toRight("ordered Decision work is absent")
      work <- S.inputCodec.decode(workJson)
      reference <- fields("requestingResult").flatMap(_.asObject)
        .toRight("ordered Decision result reference differs")
      identity <- reference("identity").flatMap(_.asString).filter(text).toRight("requesting result identity is absent")
      revision <- reference("revision").flatMap(_.asString).filter(text).toRight("requesting result revision is absent")
      resultJson <- fields("result").toRight("ordered Decision result is absent")
      result <- S.resultCodec.decode(resultJson)
      reason <- fields("reason").flatMap(_.asString).filter(text).toRight("ordered Decision reason is absent")
    } yield Input(work, ContextReference(identity, revision), result, reason)
  }
  val answerCodec: WorkflowResultJsonV1.PayloadCodec[Answer] = new WorkflowResultJsonV1.PayloadCodec[Answer] {
    val typeIdentity = "GoalPhaseOrderedDecisionAnswer"
    def encode(value: Answer) = Json.obj("choice" -> Json.fromString(value.choice), "rationale" -> Json.fromString(value.rationale))
    def decode(value: Json): Either[String, Answer] = for {
      fields <- Option(value).flatMap(_.asObject)
        .toRight("ordered Human answer fields differ")
      choice <- fields("choice").flatMap(_.asString).filter(choices.contains).toRight("ordered Human choice is unknown")
      rationale <- fields("rationale").flatMap(_.asString).filter(text).toRight("ordered Human rationale is absent")
    } yield Answer(choice, rationale)
  }

  def identity(source: Source): ContinuationIdentity =
    ContinuationIdentity(source.from.identity.value + ":ordered-human:" + source.from.revision.value)
  def resumedC(source: Source, result: ContinuationResult[Answer]): Consequence[O.State] =
    if (result.result.value.choice != "ResumeAssignedWork") F.invalid("a deferred or cancelled decision cannot select new work")
    else source.controller.acceptC(O.DecisionResumptionAdmitted(result.resultReference.identity))

  final class Port(d: F.Declaration, definition: DefinitionBinding,
    installation: Installation,
    sourceReader: (String, ContinuationIdentity, ActionCall.Core) => Consequence[Source],
    resume: (Source, O.State, InstanceRecord, Answer, ActionCall.Core) => Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult],
    acknowledgement: String => Consequence[Unit]) {
    def advanceC(source: Source, core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = for {
      projected <- prepareC(source)
      questions <- source.session.storage.bindDecisionC(completionOperation, inputCodec)
      existing <- questions.loadC(identity(source))(using core.executionContext)
      plan <- existing match {
        case Some(value) => Consequence.success(projected.copy(question = value))
        case None => for {
          saved <- questions.suspendC(projected.intent, projected.question)(using core.executionContext)
          _ <- acknowledgement("ordered-human-issued")
        } yield projected.copy(pending = saved.instance)
      }
      retained <- retainedForPlanC(plan, core)
      wire <- retained match {
        case None => NativeWorkflowOperationOutput.goalHumanC(plan.question)
        case Some((state, next, result)) => if (result.result.value.choice == "ResumeAssignedWork")
          resume(source, state, next, result.result.value, core)
        else waitC(next, result)
      }
    } yield wire

    /** Read-only traversal of the actual answer, never publication or effects. */
    def retainedC(source: Source, core: ActionCall.Core):
        Consequence[Option[(O.State, InstanceRecord, ContinuationResult[Answer])]] = for {
      projected <- prepareC(source)
      questions <- source.session.storage.bindDecisionC(completionOperation, inputCodec)
      issued <- questions.loadC(identity(source))(using core.executionContext).flatMap {
        case Some(value) => Consequence.success(value)
        case None => F.invalid("ordered Human answer has no actual issued question")
      }
      plan = projected.copy(question = issued)
      observed <- retainedForPlanC(plan, core)
    } yield observed

    private def retainedForPlanC(plan: Prepared, core: ActionCall.Core):
        Consequence[Option[(O.State, InstanceRecord, ContinuationResult[Answer])]] = for {
      native <- completionC(plan.source.session.storage)
      saved <- native.loadRetainedC(identity(plan.source))(using core.executionContext)
      observed <- saved match {
        case None => Consequence.success(None)
        case Some(value) => for {
          next <- completedC(plan, value.completion.result)
          state <- if (value.completion.result.result.value.choice == "ResumeAssignedWork") resumedC(plan.source, value.completion.result)
            else Consequence.success(plan.source.controller)
        } yield Some((state, next, value.completion.result))
      }
    } yield observed

    def bindCompletionC(params: ComponentCreate): Consequence[spec.OperationDefinition] = for {
      _ <- installation.validateC
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, d.configuration,
        Vector(definition), d.instances, d.continuations)
      _ <- storage.bindDecisionC(completionOperation, inputCodec)
      _ <- completionC(storage)
      provided <- S.providedDefinition.providedOperations.find(o => o.service == completionOperation.service &&
        o.name == completionOperation.operation)
        .map(Consequence.success).getOrElse(F.invalid("ordered Human completion Operation is absent"))
    } yield new NativeWorkflowResultInput.Receiver {
      val specification = NativeWorkflowDescriptionMetadata.operation(
        SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseOrderedDecisionOperation)
      def createResultOperationRequestC(incoming: ProtocolRequest,
        result: ContinuationResult[NativeWorkflowResultInput.Value]): Consequence[OperationRequest] = NativeWorkflowResultInput.typedC(result, answerCodec).map { submitted =>
        new CommandAction {
          val request = incoming
          override def commandExecutionMode = CommandExecutionMode.Sync
          def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
            val core = c
            private var delegate = Option.empty[ActionCall]
            def execute(): Consequence[OperationResponse] = for {
              source <- sourceReader(submitted.handle.instanceIdentity.value, submitted.continuationId, core)
              projected <- prepareC(source)
              questions <- source.session.storage.bindDecisionC(completionOperation, inputCodec)
              issued <- questions.loadC(submitted.continuationId)(using core.executionContext)
              question <- issued.map(Consequence.success).getOrElse(F.invalid("ordered Human answer has no actual issued question"))
              plan = projected.copy(question = question)
              response <- for {
                  _ <- GoalPhaseParticipantAdmission.admitC(source.session.participantIdentity, core.executionContext)
                  _ <- installation.evidence.admitC(core, plan.question, submitted, source.session.participantIdentity)
                  bound <- freshC(core, provided, plan, submitted)
                  action <- bound.createOperationRequest(core.action.request).flatMap {
                    case value: CommandAction => Consequence.success(value)
                    case _ => F.invalid("ordered Human completion is not its canonical CommandAction")
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

    private def freshC(core: ActionCall.Core, provided: GeneratedProvidedApiAbi.Operation,
      plan: Prepared, submitted: ContinuationResult[Answer]): Consequence[spec.OperationDefinition] = for {
      questions <- plan.source.session.storage.bindDecisionC(completionOperation, inputCodec)
      instances <- plan.source.session.storage.bindInstancesC()(using core.executionContext)
      native <- completionC(plan.source.session.storage)
      expected <- completedC(plan, submitted)
      bound <- {
        val codec = new WorkflowCompletionServiceOperation.Codec[Answer] {
          val resultTypeIdentity = answerCodec.typeIdentity
          def decodeC(request: ProtocolRequest) = Consequence.success(submitted)
          def encodeC(resume: ContinuationRuntime.Resume) = F.invalid("ordered Human acknowledgement requires committed readback")
          override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, result: ContinuationResult[Answer],
            completed: Option[InstanceRecord]): Consequence[OperationResponse] = for {
            actual <- completed.map(Consequence.success).getOrElse(uncertain("ordered Human completion has no committed instance"))
            _ <- acknowledgement("ordered-human-completed")
            // Managed completion supplies authoritative committed state. The
            // response performs no second store/body/semantic admission.
          } yield NativeWorkflowOperationOutput.response(NativeWorkflowOperationOutput.accepted(result, actual))
        }
        val closing = new WorkflowCompletionServiceOperation.ClosingProgram[Answer] {
          def selectC(result: ContinuationResult[Answer], current: InstanceRecord) = {
            val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              ActionExecution.Completed(StateMachineOperationResult(closingType, result.resultReference)))
            Consequence.success(WorkflowCompletionServiceOperation.SelectedClosing(program, Some(expected)))
          }
        }
        WorkflowDecisionCompletionServiceOperation.bindC(S.providedDefinition, provided, SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseOrderedDecisionOperation.specification.request,
          SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseOrderedDecisionOperation.specification.response, codec, questions, instances, closing, native,
          SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseOrderedDecisionOperation.specification.content.descriptiveAttributes)
      }
    } yield bound

    private def prepareC(source: Source): Consequence[Prepared] = for {
      _ <- installation.validateC
      _ <- if (source.controller.stage == O.Stage.Decision && source.controller.decisionSource.exists(
          Set(O.Stage.Implementation, O.Stage.Repair, O.Stage.Review, O.Stage.PhaseReview)) &&
          source.from.currentProgression.contains(ProgressionReference("phase.Decision")) && source.from.suspension.isEmpty) Consequence.unit
        else F.invalid("ordered Human source is not a retained semantic decision")
      required <- requiredC
      input = Input(source.input, source.result.resultReference, source.result.result.value, source.controller.decision.get)
      bundle = ContextBundle("Answer only the retained ordered semantic decision", Vector(source.result.resultReference), Vector.empty,
        ContextSnapshot(source.from.definition.workflowRevision.value, evidenceRevision = Some(source.from.revision.value.toString)))
      continuation = Continuation(StateMachineRunIdentity(source.from.identity.value), identity(source),
        StateMachineRevision(source.from.revision.next.value.toString), required, bundle)
      entry = HistoryEntry(HistorySequence(source.from.history.size.toLong + 1), source.from.revision.next, Lifecycle.Active,
        source.from.currentProgression, source.from.correlation, CausationReference(identity(source).value + ":issued"),
        source.from.derivedCompositeOccurrence, source.from.committedPredecessor,
        Some(SuspensionBoundary(NativeIdentity(identity(source).value), source.from.revision.next,
          ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(required.metadata.completionContract.identity),
          EvidenceReference(required.metadata.evidenceContract.identity), source.from.correlation)))
      intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
        d.configuration, source.from, source.from.revision, entry,
        ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available))
      pending <- source.from.appendC(source.from.revision, entry)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, source.from)
      question = WorkflowInteraction[Input, Nothing](handle, WorkflowContinuation.Decision(
        ContinuationRequest(continuation.runId, continuation.continuationId, continuation.expectedRevision, required.identity,
          required.operation, Some(TypedValue(inputCodec.typeIdentity, input)), required.resultType, bundle,
          required.metadata.completionContract, required.metadata.evidenceContract, completionOperation),
        MinimalPresentation("Ordered GoalPhase needs Human direction", Json.obj("choices" -> Json.arr(choices.map(Json.fromString)*),
          "notice" -> Json.fromString("Resume requests the same semantic work; it grants no commands, review acceptance, Plan amendment or repair allowance.")).noSpaces)))
    } yield Prepared(source, question, intent, pending)

    private def completedC(plan: Prepared, result: ContinuationResult[Answer]): Consequence[InstanceRecord] = for {
      _ <- admitAnswerC(result.result.value)
      state <- if (result.result.value.choice == "ResumeAssignedWork") resumedC(plan.source, result)
        else Consequence.success(plan.source.controller)
      next <- plan.pending.appendC(plan.pending.revision, F.history(plan.pending, "phase." + state.stage.toString,
        result.resultReference.identity))
    } yield next
  }

  def requiredC: Consequence[StateMachineRequiredOperation] = {
    val matches = domain.statemachine.workflow.GoalPhaseWorkflowStateMachineWorkflow1.workflow.requiredOperations
      .filter(_.identity.capability == "goal-phase-ordered-human-capability")
    if (matches.size != 1)
      F.invalid("ordered Human Required capability is absent or ambiguous")
    else {
      val r = matches.head
      Consequence.success(StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(r.identity.capability), r.actionIdentity,
        StateMachineOperationIdentity(r.operation.service, r.operation.operation), r.inputType.map(v => StateMachineInputTypeReference(v.value)),
        r.resultType.map(v => StateMachineResultTypeReference(v.value)), StateMachineRequiredOperationMetadata(
          ContextContract("goal-phase-ordered-human-context-v1", Vector.empty, Vector.empty),
          CompletionContract("goal-phase-ordered-human-completion-v1", Vector.empty),
          EvidenceContract("independent-ordered-human-answer-v1", Vector.empty), Vector.empty)))
    }
  }
  private def completionC(storage: WorkflowComponentStorage): Consequence[WorkflowManagedDecisionCompletion[Answer]] =
    storage.bindDecisionCompletionC(completionOperation, inputCodec, answerCodec, closingType, (_, _) => Consequence.success(Vector.empty))
  private def admitAnswerC(answer: Answer): Consequence[Unit] =
    if (answer != null && choices.contains(answer.choice) && text(answer.rationale)) Consequence.unit
    else F.invalid("ordered Human answer choice or rationale is invalid")
  private def waitC(next: InstanceRecord, result: ContinuationResult[Answer]) = F.waitC(next,
    "Ordered Human answer retained", result.result.value.choice match {
      case "ResumeAssignedWork" => "Direction retained; only owning advance may issue the same semantic work. No command or closure is authorized."
      case "Defer" => "Human deferred this decision. No automatic question, work, command or closure follows."
      case _ => "Human cancelled this decision path. Repository state and history remain; the Phase is not completed."
    })
  private def text(value: String) = value != null && value.trim.nonEmpty && value.length <= 16384 && !value.contains('\u0000')
  private def uncertain[A](reason: String): Consequence[A] =
    org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
}
