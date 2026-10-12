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
import org.simplemodeling.textus.smworkflow.value.{SplitPhasePartitionContextReference}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseNativeHumanDirection as H,
  SplitPhaseNativeHumanAnswer as N, SplitPhaseHumanDirectionAnswerContract as A,
  SplitPhaseHumanAnswerVerification as V, SplitPhaseManagedScopeProjectionCheckpoint as D}

/** Declared public Decision completion. CNCF owns the actual answer/native
  * persistence. The current issued question supplies execution context;
  * no unchanged question, transport or complete-history certificate is required.
  *
  * @since Oct.  5, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhaseManagedHumanCompletion {
  val responseSchema = "sm-workflow.split-human-direction-submission.v1"

  /** Stored-fact admission, not new consent or a decoded execution permit. */
  final class ObservedAnswer private[SplitPhaseManagedHumanCompletion] (
    val plan: H.Plan, val completion: WorkflowCompletionRecordJsonV1.Record[A.Answer],
    val answered: InstanceRecord, val current: InstanceRecord)

  def bindC(params: ComponentCreate, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, evidence: V.EvidencePort): Consequence[spec.OperationDefinition] =
    bindWithReadbackObservationC(params, declaration, catalog, evidence, () => Consequence.unit)

  /** An inert test observation can refuse acknowledgement after the actual
    * commit. It cannot supply a receipt, claim, transaction or replacement state.
    */
  private[admission] def bindWithReadbackObservationC(params: ComponentCreate,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    evidence: V.EvidencePort, observation: () => Consequence[Unit]): Consequence[spec.OperationDefinition] =
    if (params == null || declaration == null || catalog == null || evidence == null || observation == null)
      Consequence.configurationInvalid("SplitPhase Human completion requires an explicit independent host evidence")
    else for {
      _ <- D.bindC(params.configuration, declaration, catalog)
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
        Vector(catalog.definition), declaration.instances, declaration.continuations)
      _ <- storage.bindDecisionC(H.completionOperation, H.inputCodec)
      _ <- storage.bindDecisionCompletionC(H.completionOperation, H.inputCodec, A.payloadCodec, H.closingType,
        (_, _) => Consequence.success(Vector.empty))
      operation <- _provided_c
    } yield new BoundOperation(operation, declaration, catalog, evidence, observation)

  private final class BoundOperation(operation: GeneratedProvidedApiAbi.Operation,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    evidence: V.EvidencePort, observation: () => Consequence[Unit]) extends NativeWorkflowResultInput.Receiver {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      SmWorkflowComponent.SplitPhaseServiceService.SubmitSplitHumanDirectionOperation)
    def createResultOperationRequestC(request: Request, result: ContinuationResult[Json]): Consequence[OperationRequest] =
      NativeWorkflowResultInput.typedC(result, A.payloadCodec).map(submitted =>
        BoundAction(request, submitted, operation, declaration, catalog, evidence, observation))
  }
  private final case class BoundAction(request: Request, submitted: ContinuationResult[A.Answer],
    operation: GeneratedProvidedApiAbi.Operation, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, evidence: V.EvidencePort,
    observation: () => Consequence[Unit]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall =
      BoundCall(core, submitted, operation, declaration, catalog, evidence, observation)
  }
  private final case class BoundCall(core: ActionCall.Core, submitted: ContinuationResult[A.Answer],
    operation: GeneratedProvidedApiAbi.Operation, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, evidence: V.EvidencePort,
    observation: () => Consequence[Unit]) extends ProcedureActionCall {
    private var _delegate = Option.empty[ActionCall]
    def execute(): Consequence[OperationResponse] = {
      _delegate = None
      if (SplitPhaseManagedReasoningHumanCompletion.isReasoning(submitted.continuationId))
        SplitPhaseManagedReasoningHumanCompletion.executeForCallC(submitted, declaration, catalog, evidence, core,
          observation, call => { _delegate = Some(call) })
      else for {
        storage <- _storage_c(core, declaration, catalog)
        questions <- storage.bindDecisionC(H.completionOperation, H.inputCodec)
        issued <- questions.loadC(submitted.continuationId)(using executionContext).flatMap {
          case Some(value) => Consequence.success(value)
          case None => _invalid("actual issued question is absent")
        }
        command <- _command_c(issued)
        plan <- _plan_c(command, declaration, catalog, core, Some(issued))
        response <- for {
            admitted <- N.admitForCallC(plan, submitted, plan.source.current, core, evidence)
            bound <- _fresh_c(core, operation, declaration, catalog, storage, plan, submitted, admitted, observation)
            action <- bound.createOperationRequest(core.action.request).flatMap {
              case value: CommandAction => Consequence.success(value)
              case _ => _invalid("canonical completion is not a CommandAction")
            }
            call = action.createCall(core.copy(action = action))
            response <- { _delegate = Some(call); call.execute() }
          } yield response
      } yield response
    }
    override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
      val selected = _delegate
      _delegate = None
      selected.fold(Consequence.success(response))(_.afterCommitResponseC(response))
    }
  }

  /** Historical observation on the actual advance call. No evidence is needed
    * to read already committed facts, and no new Human answer is admitted here.
    */
  def observeForAdvanceC(command: SplitPhaseAdvanceTarget,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core): Consequence[String] =
    observeAnswerForAdvanceC(command, declaration, catalog, core).flatMap(_acknowledgement_c)

  def observeAnswerForAdvanceC(command: SplitPhaseAdvanceTarget,
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
    observed = new ObservedAnswer(plan, retained, plan.source.current, plan.source.current)
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
        def decodeC(request: Request) = Consequence.success(submitted)
        def encodeC(resume: ContinuationRuntime.Resume) = _invalid("acknowledgement requires verified committed readback")
        override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, result: ContinuationResult[A.Answer],
          completed: Option[InstanceRecord]): Consequence[OperationResponse] = {
          val readback = for {
          actual <- completed.map(Consequence.success).getOrElse(_uncertain("canonical committed instance is absent"))
          _ <- if (resume != null && resume.result != null) Consequence.unit
            else _uncertain("canonical committed closing result is absent")
          _ <- observation()
          // The managed completion owns joint persistence and authoritative
          // readback. Project those committed facts; do not reload and certify
          // the question, frontier, Result or emitted postimage a second time.
          retained = WorkflowCompletionRecordJsonV1.Record(actual.revision, result, Some(resume.result))
          wire <- _acknowledgement_c(new ObservedAnswer(plan, retained, actual, actual))
          } yield OperationResponse.Scalar(wire)
          readback match {
            case Consequence.Success(value) => Consequence.success(value)
            case _ => _uncertain("committed answer readback is not established; observe later without retry")
          }
        }
      }
      val closing = new WorkflowCompletionServiceOperation.ClosingProgram[A.Answer] {
        def selectC(result: ContinuationResult[A.Answer], current: InstanceRecord) = {
          val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Completed(StateMachineOperationResult(H.closingType, result.resultReference)))
          Consequence.success(WorkflowCompletionServiceOperation.SelectedClosing(program, Some(admitted.next)))
        }
      }
      WorkflowDecisionCompletionServiceOperation.bindC(SplitPhaseDefinitionBinding.providedDefinition, operation,
        WorkflowJsonCommandRequest.definition, spec.ResponseDefinition.void, codec, questions, instances, closing, native)
    }
  } yield bound

  private def _acknowledgement_c(observed: ObservedAnswer): Consequence[String] = {
    val plan = observed.plan
    val retained = observed.completion
    for {
    completion <- WorkflowCompletionRecordJsonV1.encodeC(retained, A.payloadCodec)
    question <- WorkflowDecisionJsonV1.encodeC(plan.question, H.inputCodec)
  } yield Json.obj("schemaVersion" -> Json.fromString(responseSchema),
    "completingRevision" -> Json.fromLong(retained.instanceRevision.value),
    "currentRevision" -> Json.fromLong(observed.current.revision.value),
    "issuedDecision" -> Json.fromString(question), "completion" -> Json.fromString(completion)).noSpaces
  }

  private def _plan_c(command: SplitPhaseAdvanceTarget, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core,
    selected: Option[WorkflowInteraction[H.Input, Nothing]] = None): Consequence[H.Plan] = for {
    publication <- D.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)
    observed <- publication.loadForCallC(command, core).flatMap {
      case Some(value) => Consequence.success(value)
      case None => _invalid("genuine whole-frontier publication is absent")
    }
    plan <- H.prepareC(observed).flatMap {
      case Some(value) => Consequence.success(value)
      case None => _invalid("ALL-Built frontier is not a Human Decision")
    }
    issued <- selected match {
      case Some(value) => Consequence.success(value)
      case None => for {
        storage <- _storage_c(core, declaration, catalog)
        questions <- storage.bindDecisionC(H.completionOperation, H.inputCodec)
        actual <- questions.loadC(plan.question.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request.continuationId)(using core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(_invalid("actual issued question is absent")))
      } yield actual
    }
    resolved <- H.consumeIssuedC(plan, issued)
  } yield resolved

  private def _command_c(question: WorkflowInteraction[H.Input, Nothing]): Consequence[SplitPhaseAdvanceTarget] = question.current match {
    case decision: WorkflowContinuation.Decision[?] if decision.request.input.exists(value => value.value.isInstanceOf[H.Input]) =>
      val input = decision.request.input.get.value.asInstanceOf[H.Input]
      Consequence.success(SplitPhaseAdvanceTarget(question.handle.instanceIdentity.value,
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
      operation.service == H.completionOperation.service && operation.name == H.completionOperation.operation)
      .map(Consequence.success).getOrElse(_invalid("generated Provided Human operation differs"))
  private def _invalid[T](reason: String): Consequence[T] = Consequence.stateConflict("SplitPhase managed Human completion " + reason)
  private def _uncertain[T](reason: String): Consequence[T] = DataStoreConditionalTransitionFailure.transactionIndeterminate(
    "SplitPhase managed Human completion " + reason)
}
