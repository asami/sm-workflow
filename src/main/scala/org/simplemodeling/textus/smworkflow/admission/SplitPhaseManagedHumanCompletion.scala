package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseAdvanceCommand, SplitPhasePartitionContextReference}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseNativeHumanDirection as H,
  SplitPhaseNativeHumanAnswer as N, SplitPhaseHumanDirectionAnswerContract as A,
  SplitPhaseHumanAnswerVerification as V, SplitPhaseManagedScopeProjectionCheckpoint as D}

/** Declared public Decision completion. CNCF owns atomic claim/result/native
  * persistence; repeated identical results are historical observations only.
  *
  * @since Oct.  5, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseManagedHumanCompletion {
  val responseSchema = "sm-workflow.split-human-direction-submission.v1"

  /** Stored-fact admission, not new consent or a decoded execution permit. */
  final class ObservedAnswer private[SplitPhaseManagedHumanCompletion] (
    val plan: H.Plan, val completion: WorkflowCompletionRecordJsonV1.Record[A.Answer],
    val answered: InstanceRecord) {
    def current: InstanceRecord = plan.source.current
  }

  def bindC(params: ComponentCreate, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, verifier: V.Verifier): Consequence[spec.OperationDefinition] =
    bindWithReadbackObservationC(params, declaration, catalog, verifier, () => Consequence.unit)

  /** An inert test observation can refuse acknowledgement after the actual
    * commit. It cannot supply a receipt, claim, transaction or replacement state.
    */
  private[admission] def bindWithReadbackObservationC(params: ComponentCreate,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    verifier: V.Verifier, observation: () => Consequence[Unit]): Consequence[spec.OperationDefinition] =
    if (params == null || declaration == null || catalog == null || verifier == null || observation == null)
      Consequence.configurationInvalid("SplitPhase Human completion requires an explicit independent host verifier")
    else for {
      _ <- D.bindC(params.configuration, declaration, catalog)
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
        Vector(catalog.definition), declaration.instances, declaration.continuations)
      _ <- storage.bindDecisionC(H.completionOperation, H.inputCodec)
      _ <- storage.bindDecisionCompletionC(H.completionOperation, H.inputCodec, A.payloadCodec, H.closingType,
        (_, _) => Consequence.success(Vector.empty))
      operation <- _provided_c
    } yield new BoundOperation(operation, declaration, catalog, verifier, observation)

  private final class BoundOperation(operation: GeneratedProvidedApiAbi.Operation,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    verifier: V.Verifier, observation: () => Consequence[Unit]) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      SmWorkflowComponent.SplitPhaseServiceService.SubmitSplitHumanDirectionOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] = for {
      submitted <- _decode_c(request)
      _ <- if (submitted.handle.componentIdentity == SmWorkflowComponent.componentId &&
          submitted.handle.workflowIdentity == catalog.definition.workflowIdentity &&
          submitted.handle.workflowRevision == catalog.definition.workflowRevision) Consequence.unit
        else _invalid("result differs from the owning Component or Workflow")
    } yield BoundAction(request, submitted, operation, declaration, catalog, verifier, observation)
  }
  private final case class BoundAction(request: Request, submitted: ContinuationResult[A.Answer],
    operation: GeneratedProvidedApiAbi.Operation, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, verifier: V.Verifier,
    observation: () => Consequence[Unit]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall =
      BoundCall(core, submitted, operation, declaration, catalog, verifier, observation)
  }
  private final case class BoundCall(core: ActionCall.Core, submitted: ContinuationResult[A.Answer],
    operation: GeneratedProvidedApiAbi.Operation, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, verifier: V.Verifier,
    observation: () => Consequence[Unit]) extends ProcedureActionCall {
    private var _delegate = Option.empty[ActionCall]
    def execute(): Consequence[OperationResponse] = {
      _delegate = None
      if (SplitPhaseManagedReasoningHumanCompletion.isReasoning(submitted.continuationId))
        SplitPhaseManagedReasoningHumanCompletion.executeForCallC(submitted, declaration, catalog, verifier, core,
          observation, call => { _delegate = Some(call) })
      else for {
        storage <- _storage_c(core, declaration, catalog)
        questions <- storage.bindDecisionC(H.completionOperation, H.inputCodec)
        issued <- questions.loadC(submitted.continuationId)(using executionContext).flatMap {
          case Some(value) => Consequence.success(value)
          case None => _invalid("actual issued question is absent")
        }
        _ <- WorkflowProtocolV1.admitDecisionResultC(issued, submitted).map(_ => ())
        command <- _command_c(issued)
        plan <- _plan_c(command, declaration, catalog, core)
        _ <- if (plan.question == issued) Consequence.unit else _invalid("issued question differs from genuine whole frontier")
        native <- _completion_c(storage)
        retained <- native.loadRetainedC(submitted.continuationId)(using executionContext)
        response <- retained match {
          case Some(actual) => _observe_c(plan, actual.completion, submitted).map(OperationResponse.Scalar(_))
          case None => for {
            admitted <- N.admitForCallC(plan, submitted, plan.source.current, core, verifier)
            bound <- _fresh_c(core, operation, declaration, catalog, storage, plan, submitted, admitted, observation)
            action <- bound.createOperationRequest(core.action.request).flatMap {
              case value: CommandAction => Consequence.success(value)
              case _ => _invalid("canonical completion is not a CommandAction")
            }
            call = action.createCall(core.copy(action = action))
            response <- { _delegate = Some(call); call.execute() }
          } yield response
        }
      } yield response
    }
    override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
      val selected = _delegate
      _delegate = None
      selected.fold(Consequence.success(response))(_.afterCommitResponseC(response))
    }
  }

  /** Historical observation on the actual advance call. No verifier is needed
    * to read already committed facts, and no new Human answer is admitted here.
    */
  def observeForAdvanceC(command: SplitPhaseAdvanceCommand,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core): Consequence[String] =
    observeAnswerForAdvanceC(command, declaration, catalog, core).flatMap(_acknowledgement_c)

  def observeAnswerForAdvanceC(command: SplitPhaseAdvanceCommand,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core): Consequence[ObservedAnswer] = for {
    plan <- _plan_c(command, declaration, catalog, core)
    storage <- _storage_c(core, declaration, catalog)
    native <- _completion_c(storage)
    identity = plan.question.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request.continuationId
    retained <- native.loadRetainedC(identity)(using core.executionContext).flatMap {
      case Some(value) => Consequence.success(value.completion)
      case None => _invalid("answered progression has no canonical retained result")
    }
    observed <- _admit_observation_c(plan, retained, retained.result)
  } yield observed

  private def _fresh_c(core: ActionCall.Core, operation: GeneratedProvidedApiAbi.Operation,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    storage: WorkflowComponentStorage, plan: H.Plan, submitted: ContinuationResult[A.Answer],
    admitted: N.Verified, observation: () => Consequence[Unit]): Consequence[spec.OperationDefinition] = for {
    questions <- storage.bindDecisionC(H.completionOperation, H.inputCodec)
    instances <- storage.bindInstancesC()(using core.executionContext)
    native <- _completion_c(storage)
    bound <- {
      val codec = new WorkflowCompletionServiceOperation.Codec[A.Answer] {
        val resultTypeIdentity = A.resultType
        def decodeC(request: Request) = _decode_c(request)
        def encodeC(resume: ContinuationRuntime.Resume) = _invalid("acknowledgement requires verified committed readback")
        override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, result: ContinuationResult[A.Answer],
          completed: Option[InstanceRecord]): Consequence[OperationResponse] = {
          val readback = for {
          _ <- if (result == submitted && completed.exists(value => SplitPhaseDefinitionBinding.sameRecord(value, admitted.next)) &&
              resume != null && resume.result == StateMachineOperationResult(H.closingType, submitted.resultReference)) Consequence.unit
            else _uncertain("canonical committed result or instance differs")
          _ <- admitted.evidence.receiptForCallC(core).map(_ => ())
          _ <- observation()
          command <- _command_c(plan.question)
          current <- _plan_c(command, declaration, catalog, core)
          _ <- if (current.question == plan.question && SplitPhaseDefinitionBinding.sameRecord(current.source.current, admitted.next))
            Consequence.unit else _uncertain("committed whole-frontier readback differs")
          retained <- native.loadRetainedC(submitted.continuationId)(using core.executionContext).flatMap {
            case Some(value) => Consequence.success(value.completion)
            case None => _uncertain("committed canonical answer is unavailable; observe later without retry")
          }
          wire <- _observe_c(current, retained, submitted)
          } yield OperationResponse.Scalar(wire)
          readback match {
            case Consequence.Success(value) => Consequence.success(value)
            case _ => _uncertain("committed answer readback is not established; observe later without retry")
          }
        }
      }
      val closing = new WorkflowCompletionServiceOperation.ClosingProgram[A.Answer] {
        def selectC(result: ContinuationResult[A.Answer], current: InstanceRecord) = for {
          _ <- admitted.evidence.receiptForCallC(core).map(_ => ())
          _ <- if (result == submitted && SplitPhaseDefinitionBinding.sameRecord(current, plan.source.current)) Consequence.unit
            else _invalid("closing result or native predecessor changed after independent admission")
          next <- N.prepareC(plan, result, current)
          _ <- if (SplitPhaseDefinitionBinding.sameRecord(next, admitted.next)) Consequence.unit
            else _invalid("closing postimage differs from independent admission")
          program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Completed(StateMachineOperationResult(H.closingType, result.resultReference)))
        } yield WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next))
      }
      WorkflowDecisionCompletionServiceOperation.bindC(SplitPhaseDefinitionBinding.providedDefinition, operation,
        WorkflowJsonCommandRequest.definition, spec.ResponseDefinition.void, codec, questions, instances, closing, native)
    }
  } yield bound

  private def _observe_c(plan: H.Plan, retained: WorkflowCompletionRecordJsonV1.Record[A.Answer],
    submitted: ContinuationResult[A.Answer]): Consequence[String] =
    _admit_observation_c(plan, retained, submitted).flatMap(_acknowledgement_c)

  private def _admit_observation_c(plan: H.Plan, retained: WorkflowCompletionRecordJsonV1.Record[A.Answer],
    submitted: ContinuationResult[A.Answer]): Consequence[ObservedAnswer] = for {
    _ <- if (retained.result == submitted && retained.instanceRevision == plan.next.revision.next &&
        retained.closingResult.contains(StateMachineOperationResult(H.closingType, submitted.resultReference))) Consequence.unit
      else _invalid("retained complete answer, completing revision or typed closing result differs")
    next <- N.prepareC(plan, retained.result, plan.next)
    _ <- if (plan.source.current.history.take(next.history.size) == next.history) Consequence.unit
      else _invalid("retained answer lacks its complete adjacent native history prefix")
  } yield new ObservedAnswer(plan, retained, next)

  private def _acknowledgement_c(observed: ObservedAnswer): Consequence[String] = {
    val plan = observed.plan
    val retained = observed.completion
    for {
    completion <- WorkflowCompletionRecordJsonV1.encodeC(retained, A.payloadCodec)
    question <- WorkflowDecisionJsonV1.encodeC(plan.question, H.inputCodec)
  } yield Json.obj("schemaVersion" -> Json.fromString(responseSchema),
    "completingRevision" -> Json.fromLong(retained.instanceRevision.value),
    "currentRevision" -> Json.fromLong(plan.source.current.revision.value),
    "issuedDecision" -> Json.fromString(question), "completion" -> Json.fromString(completion)).noSpaces
  }

  private def _plan_c(command: SplitPhaseAdvanceCommand, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[H.Plan] = for {
    storage <- _storage_c(core, declaration, catalog)
    publication <- D.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)
    observed <- publication.loadForCallC(command, core).flatMap {
      case Some(value) => Consequence.success(value)
      case None => _invalid("genuine whole-frontier publication is absent")
    }
    plan <- H.prepareC(observed).flatMap {
      case Some(value) => Consequence.success(value)
      case None => _invalid("ALL-Built frontier is not a Human Decision")
    }
    questions <- storage.bindDecisionC(H.completionOperation, H.inputCodec)
    issued <- questions.loadC(plan.question.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request.continuationId)(using core.executionContext)
    _ <- if (issued.contains(plan.question) && observed.current.history.take(plan.next.history.size) == plan.next.history)
      Consequence.unit else _invalid("actual issued question or complete native suspension prefix differs")
  } yield plan

  private def _command_c(question: WorkflowInteraction[H.Input, Nothing]): Consequence[SplitPhaseAdvanceCommand] = question.current match {
    case decision: WorkflowContinuation.Decision[?] if decision.request.input.exists(value => value.value.isInstanceOf[H.Input]) =>
      val input = decision.request.input.get.value.asInstanceOf[H.Input]
      H.frontierC(input).map(_ => SplitPhaseAdvanceCommand(question.handle.instanceIdentity.value,
        SplitPhasePartitionContextReference(input.context.identity, input.context.revision)))
    case _ => _invalid("issued Decision input is absent")
  }
  private def _storage_c(core: ActionCall.Core, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[WorkflowComponentStorage] =
    if (core == null || !core.component.exists(value => value.componentId == SmWorkflowComponent.componentId && value.subsystem.nonEmpty))
      Consequence.securityPermissionDenied("Human completion requires its actual owning call")
    else WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition), declaration.instances, declaration.continuations)
  private def _completion_c(storage: WorkflowComponentStorage): Consequence[WorkflowManagedDecisionCompletion[A.Answer]] =
    storage.bindDecisionCompletionC(H.completionOperation, H.inputCodec, A.payloadCodec, H.closingType,
      (_, _) => Consequence.success(Vector.empty))
  private def _provided_c: Consequence[GeneratedProvidedApiAbi.Operation] =
    SplitPhaseDefinitionBinding.providedDefinition.providedOperations.find(operation =>
      operation.service == H.completionOperation.service && operation.name == H.completionOperation.operation &&
        operation.inputType.contains(A.resultType) && operation.resultType.contains(H.closingType.value))
      .map(Consequence.success).getOrElse(_invalid("generated Provided Human operation differs"))
  private def _decode_c(request: Request): Consequence[ContinuationResult[A.Answer]] =
    if (request == null || request.operation != H.completionOperation.operation || !request.service.contains(H.completionOperation.service))
      _invalid("request differs from the actual Provided Human operation")
    else WorkflowJsonCommandRequest.commandArguments(request) match {
      case List(Argument("arg1", wire: String, _)) => WorkflowResultJsonV1.decodeC(wire, A.payloadCodec)
      case _ => _invalid("one complete typed Human result is required")
    }
  private def _invalid[T](reason: String): Consequence[T] = Consequence.stateConflict("SplitPhase managed Human completion " + reason)
  private def _uncertain[T](reason: String): Consequence[T] = DataStoreConditionalTransitionFailure.transactionIndeterminate(
    "SplitPhase managed Human completion " + reason)
}
