package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as _, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import GoalTaskNativeRun as N
import BoundedWorkRuntime.Host
import StandaloneFixExecution as S

/** Ordinary public operations around the canonical CNCF continuation engine.
  * @since   Oct. 26, 2026
  * @version Oct. 27, 2026
  */
private[smworkflow] object GoalTaskService {
  def bindC(creation: ComponentCreate, declaration: GoalPhaseNativeRun.Declaration): Consequence[spec.ServiceDefinition] = for {
    readers <- GoalPhaseNativeInputReadPolicy.bindC(creation.configuration, declaration)
    service <- bindC(creation, BoundedWorkRuntime.host(declaration), readers.selections)
  } yield service

  /** Neutral host capability view: Task callers supply no Phase input or Plan. */
  def bindC(creation: ComponentCreate, h: Host, selections: ProfileStartAdmission.CallScopedSelectionPort): Consequence[spec.ServiceDefinition] = for {
    _ <- GoalPhaseNativeExecution.validatePairC(h.profiles, h.origin)
    definition <- GoalTaskRuntimeCatalog.loadC()
    storage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, h.configuration,
      Vector(definition), h.instances, h.continuations)
    reader <- NativeExternalEvidence.bindBoundedC(creation.configuration, h)
    recovery = GoalTaskMalformedIssuedWorkRecovery.operations(h, definition,
      new ThreeWorkflowExternalEvidencePorts(reader).goalTaskMalformedIssuedWorkRecovery)
    completion <- completionC(h, definition, storage, None, None)
    wrapped = CallScopedOperationBinding.wrapResult(completion, (core, submitted) =>
      N.storageC(h, definition, core).flatMap(storage => completionC(h, definition, storage, Some(core), Some(submitted))))
    service = NativeWorkflowDescriptionMetadata.service(SmWorkflowComponent.GoalTaskServiceService,
      spec.OperationDefinitionGroup(NonEmptyVector.of(new Operation(true, h, definition, selections),
        new Operation(false, h, definition, selections), new InformationOperation(h, definition), wrapped).concat(recovery)))
  } yield NativeWorkflowResultInput.task(service, creation, h, definition)

  private final class Operation(start: Boolean, h: Host, definition: DefinitionBinding,
    selections: ProfileStartAdmission.CallScopedSelectionPort) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (start)
      SmWorkflowComponent.GoalTaskServiceService.StartGoalTaskOperation
      else SmWorkflowComponent.GoalTaskServiceService.AdvanceGoalTaskOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          session <- if (start) NativeWorkflowOperationInput.startSessionC(incoming, core.executionContext)
            else NativeWorkflowOperationInput.textC(incoming, "sessionId")
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
          selected = core.copy(executionContext = context)
          result <- if (start) for {
            input <- NativeWorkflowOperationInput.valueC[V.GoalTaskInput](incoming, "input")
            selection <- NativeWorkflowOperationInput.textC(incoming, "selectionId")
            participant <- NativeWorkflowOperationInput.textC(incoming, "participant")
            key <- NativeWorkflowOperationInput.textC(incoming, "idempotencyKey")
            result <- N.startC(S.Input(input.repositoryIdentity, input.requestIdentity, input.objective, input.ownedPaths.toVector,
              input.checks.toVector, input.conditions.toVector, input.sourceReferences.toVector.map(r => ContextReference(r.identity, r.revision)),
              input.reviewRequired, input.kind, input.scalaLint, input.repairLimit,
              Some(S.Task(input.sourceKind, input.dependencies.toVector))), participant, key, selection, selections, h, definition, selected)
          } yield result else N.advanceC(session, h, definition, selected)
        } yield NativeWorkflowOperationOutput.response(result)
      }
    })
  }

  private final class InformationOperation(h: Host, definition: DefinitionBinding) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      SmWorkflowComponent.GoalTaskServiceService.ResolveGoalTaskDiagnosticInformationOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] = for {
      input <- NativeWorkflowOperationInput.valueC[V.GoalPhaseDiagnosticInformationInput](incoming, "input")
    } yield new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(input.issuedWorkContext.runId))
          result <- StandaloneFixInformation.resolveC(h, definition, core.copy(executionContext = context), input, N)
        } yield NativeWorkflowOperationOutput.response(result)
      }
    }
  }

  private def provided: GeneratedProvidedApiAbi.Definition = {
    val p = domain.statemachine.providedapi.GoalTaskWorkflowStateMachineProvidedApi5.workflow
    GeneratedProvidedApiAbi.Definition(p.schemaVersion, p.generator, p.identity, p.version,
      GeneratedProvidedApiAbi.WorkflowSourceCorrelation(GeneratedProvidedApiAbi.SourceIdentity(p.source.root.line),
        GeneratedProvidedApiAbi.SourceIdentity(p.source.definition.line)),
      p.providedOperations.map(o => GeneratedProvidedApiAbi.Operation(o.service, o.name, o.inputType, o.resultType,
        GeneratedProvidedApiAbi.SourceIdentity(o.source.line))))
  }
  private def completionC(h: Host, definition: DefinitionBinding, storage: WorkflowComponentStorage,
    core: Option[ActionCall.Core], resolved: Option[ContinuationResult[NativeWorkflowResultInput.Value]]): Consequence[spec.OperationDefinition] = for {
    native <- storage.bindCompletionC(N.completionOperation, N.inputCodec, N.resultCodec, N.closingType,
      (_, _) => Consequence.success(Vector.empty))
    operation <- provided.providedOperations.find(o => o.service == N.completionOperation.service && o.name == N.completionOperation.operation)
      .map(Consequence.success).getOrElse(N.invalid("Task completion is absent from its provided ABI"))
    result <- {
      val issued = new IssuedWorkOrderPersistence[S.Work] {
        def putIfAbsentC(work: WorkflowInteraction[S.Work, Nothing]) = N.invalid("Submission cannot issue work")
        def loadC(id: ContinuationIdentity) = core.map(call => storage.bindIssuedC(N.completionOperation, N.inputCodec)
          .flatMap(_.loadC(id)(using call.executionContext))).getOrElse(N.invalid("Submission lookup requires its current call"))
      }
      var decodedWork = Option.empty[S.Work]
      val codec = new WorkflowCompletionServiceOperation.Codec[S.Result] {
        val resultTypeIdentity = N.resultCodec.typeIdentity
        def decodeC(request: Request) = for {
          submitted <- resolved.map(NativeWorkflowResultInput.typedC(_, N.resultCodec))
            .getOrElse(N.invalid("Completion requires the actual resolved business result"))
          actual <- issued.loadC(submitted.continuationId).flatMap(_.map(Consequence.success)
            .getOrElse(N.invalid("Actual assigned work missing")))
          work <- NativeWorkflowOperationOutput.workInputC(actual)
          _ <- core.map(c => GoalPhaseParticipantAdmission.admitC(work.state.participant, c.executionContext))
            .getOrElse(N.invalid("Completion requires its current actor"))
          _ = decodedWork = Some(work)
        } yield submitted
        def encodeC(resume: ContinuationRuntime.Resume) = N.invalid("Completion requires a canonical committed result")
        override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, submitted: ContinuationResult[S.Result],
          completed: Option[InstanceRecord]): Consequence[OperationResponse] = for {
          record <- completed.map(Consequence.success).getOrElse(N.invalid("Managed completion has no committed instance"))
          work <- decodedWork.map(Consequence.success).getOrElse(N.invalid("Completion has no actual decoded work"))
        } yield NativeWorkflowOperationOutput.response(N.view(record, work.state, "Submitted",
          "Native semantic result retained. A separate normal Advance owns fresh validation and acceptance."))
      }
      val closing = new WorkflowCompletionServiceOperation.ClosingProgram[S.Result] {
        def selectC(submitted: ContinuationResult[S.Result], current: InstanceRecord) = core.map { call => for {
          actual <- issued.loadC(submitted.continuationId).flatMap(_.map(Consequence.success).getOrElse(N.invalid("Result is not assigned")))
          work <- actual.current match {
            case order: WorkflowContinuation.WorkOrder[S.Work] => order.request.input.map(i => Consequence.success(i.value))
              .getOrElse(N.invalid("Assigned input missing"))
            case _ => N.invalid("Result is not a Task WorkOrder")
          }
          revision <- scala.util.Try(work.sourceRevision.toLong).toOption.map(Consequence.success)
            .getOrElse(N.invalid("Assigned source revision is malformed"))
          payload <- N.loadPayloadC(storage, current.identity.value, revision, call)
          _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, call.executionContext)
          _ <- if (payload.state == work.state && work.session == current.identity.value && work.kind == payload.state.stage && payload.state.input.task.nonEmpty)
            Consequence.unit else N.invalid("Result crosses the retained Task request")
          _ <- S.admitResultC(work, submitted.result.value)
          _ <- if (submitted.evidence.workerIdentity.exists(_.nonEmpty)) Consequence.unit
            else N.invalid("Actual semantic completion requires its worker identity, without inventing execution placement")
          observation <- RepositorySyncManagedGitObservation.observeC(call)
          root <- RepositorySyncManagedGitObservation.rootC(call)
          _ <- if (root == payload.root && observation.branch == payload.branch && observation.head == payload.baseHead &&
            observation.dirty.filterNot(d => S.owned(d.path, payload.state.input.ownedPaths)) == payload.unrelated.getOrElse(Vector.empty) &&
            observation.dirty.filter(d => S.owned(d.path, payload.state.input.ownedPaths)).forall(d =>
              !d.unmerged && d.renamedFrom.forall(S.owned(_, payload.state.input.ownedPaths))))
            Consequence.unit else N.invalid("Semantic completion changed HEAD or exceeded ownership")
          _ <- if (work.kind == "Review") for {
            _ <- N.freshC(payload, call)
            _ <- h.review.map(_.admitStandaloneC(call, work, submitted)).getOrElse(N.invalid("Independent review evidence installation is absent"))
            _ <- if (submitted.evidence.workerIdentity.exists(w => w.nonEmpty && !payload.implementationWorker.contains(w) && !payload.implementationWorkers.getOrElse(Vector.empty).contains(w)))
              Consequence.unit else N.invalid("Review requires an actual different worker identity")
          } yield () else Consequence.unit
          next <- current.appendC(current.revision, N.history(current,
            "standalone-completed:" + work.sourceRevision + ":" + submitted.continuationId.value))
        } yield WorkflowCompletionServiceOperation.SelectedClosing(
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Completed(StateMachineOperationResult(N.closingType, submitted.resultReference))), Some(next))
        }.getOrElse(N.invalid("Closing requires its actual native call"))
      }
      val instances = CallScopedWorkflowInstances.managed(h.configuration, definition, h.instances, h.continuations)
      val actual = core.fold[WorkflowInstancePersistence](instances)(CallScopedWorkflowInstances.forCall(instances, _))
      val declared = SmWorkflowComponent.GoalTaskServiceService.SubmitGoalTaskWorkResultOperation
      WorkflowCompletionServiceOperation.bindC(provided, operation, declared.specification.request, declared.specification.response,
        codec, ContinuationRuntime.empty, issued, actual, h.configuration,
        () => throw new IllegalStateException("Task completion requires canonical managed UoW"), Some(closing), Some(native),
        descriptiveAttributes = declared.specification.content.descriptiveAttributes)
    }
  } yield result
}
