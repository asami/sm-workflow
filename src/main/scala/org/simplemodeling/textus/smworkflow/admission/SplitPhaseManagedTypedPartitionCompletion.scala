package org.simplemodeling.textus.smworkflow.admission

import cats.syntax.all.*
import io.circe.Json
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.SubjectKind
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPartitionSemanticJson as J,
  SplitPhaseTypedPartitionCompletion as P, SplitPhaseExplicitPlanningAdvance as W,
  SplitPhasePartitionCompletionCheckpointPreparation as A,
  SplitPhasePartitionCompletionCheckpointDataStorePort as D}

/** Generated public completion on canonical storage. No lease/provider is
  * retained, and no historical hash-backed completion is an alternate route.
  * @since   Oct.  2, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhaseManagedTypedPartitionCompletion {
  final class ObservedCompletion private[SplitPhaseManagedTypedPartitionCompletion] (
    val record: A.Record, val current: WorkflowInstancePersistence.InstanceRecord)
  final class Binding private[SplitPhaseManagedTypedPartitionCompletion] (
    val operation: spec.OperationDefinition, issue: SplitPhaseManagedTypedPartitionIssue.Port,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog) {
    /** Observation only. Actual SQL issued-work reading enforces the current
      * native call before the canonical retained-completion query is allowed.
      */
    def loadRetainedForCallC(identity: ContinuationIdentity, core: ActionCall.Core):
        Consequence[Option[WorkflowCompletionRecordJsonV1.Record[S.Judgment]]] = for {
      _ <- actorC(core, None)
      issued <- issue.loadForCallC(identity, core)
      _ <- if (issued.nonEmpty) Consequence.unit else Consequence.stateConflict("SplitPhase completion issue is absent")
      completion <- nativeC(core, declaration, catalog)
      retained <- completion.loadRetainedC(identity)(using core.executionContext)
    } yield retained.map(_.completion)

    /** No planning reconstruction, allocation, human verifier or write. */
    def recoverForCallC(identity: ContinuationIdentity, core: ActionCall.Core):
        Consequence[Option[ObservedCompletion]] = observeForCallC(identity, declaration, catalog, core)
  }

  def observeForCallC(identity: ContinuationIdentity, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core): Consequence[Option[ObservedCompletion]] = for {
    _ <- actorC(core, None)
    params = ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin)
    issue <- SplitPhaseManagedTypedPartitionIssue.bindC(params, declaration, catalog)
    work <- issue.loadForCallC(identity, core).flatMap {
      case Some(x) => Consequence.success(x)
      case _ => Consequence.stateConflict("SplitPhase recovery issued WorkOrder is absent")
    }
    native <- nativeC(core, declaration, catalog)
    retained <- native.loadRetainedC(identity)(using core.executionContext)
    result <- retained match {
      case None => Consequence.success(None)
      case Some(actual) => for {
        reads <- currentReadsC(core, declaration, catalog)
        saved <- reads.loadForCallC(identity, core)
        _ <- actorC(core, Some(saved.start.participantIdentity))
        instances = CallScopedWorkflowInstances.managed(declaration.configuration, catalog.definition,
          declaration.instances, declaration.continuations)
        current <- instances.loadForCallC(declaration.configuration, work.handle.instanceIdentity, core).flatMap {
          case Some(x) => Consequence.success(x)
          case _ => Consequence.stateConflict("SplitPhase recovery native instance is absent")
        }
        // Native completion is authoritative. The stored calculation supplies
        // inert data, not a second completion/result certificate. No old input,
        // header or generated-document reread is needed to acknowledge it.
        observed = saved.copy(completion = actual.completion)
      } yield Some(new ObservedCompletion(observed, current))
    }
  } yield result

  def bindHostC(params: ComponentCreate, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[Binding] =
    if (params == null || declaration == null || catalog == null)
      Consequence.configurationInvalid("SplitPhase typed completion requires complete Workflow metadata")
    else for {
      starts <- SplitPhaseTypedStartReadPolicy.bindC(params.configuration, declaration, catalog)
      contexts <- SplitPhasePartitionContextReadPolicy.bindC(params.configuration, declaration)
      inputs <- SplitPhasePlanningInputReadPolicy.bindC(params.configuration, declaration)
      issue <- SplitPhaseManagedTypedPartitionIssue.bindC(params, declaration, catalog)
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
        Vector(catalog.definition), declaration.instances, declaration.continuations)
      completion <- storage.bindCompletionC(C.completionOperation, J.requestCodec, J.resultCodec, P.closingType,
        (_, _) => Consequence.success(Vector.empty))
      provided = SplitPhaseDefinitionBinding.providedDefinition
      op <- provided.providedOperations.find(o => o.service == C.completionOperation.service &&
        o.name == C.completionOperation.operation).map(Consequence.success)
        .getOrElse(Consequence.configurationInvalid("SplitPhase partition completion Operation is absent"))
      instances = CallScopedWorkflowInstances.managed(declaration.configuration, catalog.definition,
        declaration.instances, declaration.continuations)
      declared <- operationC(None, provided, op, declaration, catalog, starts, contexts, inputs, issue,
        instances, Some(completion))
      wrapped = CallScopedOperationBinding.wrapResult(declared, (core, result) =>
        nativeC(core, declaration, catalog).flatMap(native => operationC(Some(core), provided, op,
          declaration, catalog, starts, contexts, inputs, issue, instances, Some(native), Some(result))))
    } yield new Binding(wrapped, issue, declaration, catalog)

  private def operationC(core: Option[ActionCall.Core], provided: GeneratedProvidedApiAbi.Definition,
    op: GeneratedProvidedApiAbi.Operation, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, starts: SplitPhaseTypedStartDataStorePort,
    contexts: SplitPhasePartitionContextDataStorePort, inputs: SplitPhasePlanningInputDataStorePort,
    issue: SplitPhaseManagedTypedPartitionIssue.Port, instances: CallScopedWorkflowInstances.Port,
    completion: Option[WorkflowManagedCompletion[S.Judgment]],
    result: Option[ContinuationResult[Json]] = None): Consequence[spec.OperationDefinition] = {
    // These closures live only in the canonical current-call Operation binding.
    var selected = Option.empty[P.Plan]
    val issued = new IssuedWorkOrderPersistence[S.Request] {
      def putIfAbsentC(value: WorkflowInteraction[S.Request, Nothing]) =
        Consequence.stateConflict("SplitPhase completion cannot publish an issue")
      def loadC(identity: ContinuationIdentity) = core match {
        case Some(call) => issue.loadForCallC(identity, call)
        case None => Consequence.stateConflict("SplitPhase completion requires its current Operation")
      }
    }
    val codec = new WorkflowCompletionServiceOperation.Codec[S.Judgment] {
      val resultTypeIdentity = J.resultCodec.typeIdentity
      def decodeC(request: Request) = result.map(NativeWorkflowResultInput.typedC(_, J.resultCodec))
        .getOrElse(Consequence.stateConflict("SplitPhase completion has no resolved semantic result"))
      def encodeC(resume: ContinuationRuntime.Resume) =
        Consequence.stateConflict("SplitPhase completion projection requires its actual verified commit")
      override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, submitted: ContinuationResult[S.Judgment],
        completed: Option[WorkflowInstancePersistence.InstanceRecord]): Consequence[OperationResponse] = {
        val plan = selected
        selected = None
        // CNCF calls this only after the owning managed completion succeeds.
        // Consume its native result; no extra read can certify that commit.
        (plan, completed) match {
          case (Some(value), Some(actual)) =>
            P.committedWireC(value, actual).map(OperationResponse.Scalar(_))
          case _ => org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
            "SplitPhase completion response lacks its selected disposition or actual committed instance")
        }
      }
    }
    val closing = new WorkflowCompletionServiceOperation.ClosingProgram[S.Judgment] {
      def selectC(submitted: ContinuationResult[S.Judgment], current: WorkflowInstancePersistence.InstanceRecord) = core match {
        case None => Consequence.stateConflict("SplitPhase closing selection requires its current Operation")
        case Some(call) => for {
          _ <- mutationActorC(call)
          work <- issued.loadC(submitted.continuationId).flatMap {
            case Some(value) => Consequence.success(value)
            case _ => Consequence.stateConflict("SplitPhase completion issued WorkOrder is absent")
          }
          reference <- work.current match {
            case order: WorkflowContinuation.WorkOrder[?] if order.request != null && order.request.input.nonEmpty =>
              val request = order.request.input.get.value.asInstanceOf[S.Request]
              Consequence.success(request.context)
            case _ => Consequence.stateConflict("SplitPhase completion has no issued typed context")
          }
          raw <- contexts.loadForCallC(reference, call).flatMap {
            case Some(value) => Consequence.success(value)
            case _ => Consequence.stateConflict("SplitPhase completion original context is absent")
          }
          facts <- inputs.loadCurrentForCallC(raw.facts, call)
          start <- starts.prepareForCallC(raw.owner.start, call).flatMap {
            case Some(value) => Consequence.success(value)
            case _ => Consequence.stateConflict("SplitPhase completion original typed Start is absent")
          }
          _ <- actorC(call, Some(start.record.participantIdentity))
          pending <- P.pendingC(raw, facts, start, current, work, declaration.configuration, catalog)
          initial <- P.admitC(pending, submitted)
          plan <- initial.mechanical match {
            case Some(need: W.AllocationRequired) => SplitPhaseCandidateAllocationPolicy.decodeC(call.component.get.subsystem.get.configuration)
              .flatMap(_.allocateC(need, pending.allocation.result))
              .flatMap(allocated => P.continueAllocatedC(initial, allocated))
            case _ => Consequence.success(initial)
          }
          captured <- A.captureC(plan)
          reads <- currentReadsC(call, declaration, catalog)
          staged <- reads.stageForCallC(captured, call)
          program = staged.flatMap(_ => ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Completed(plan.closing)))
        } yield {
          selected = Some(plan)
          WorkflowCompletionServiceOperation.SelectedClosing(program, Some(plan.next))
        }
      }
    }
    val currentInstances = core.fold[WorkflowInstancePersistence](instances)(CallScopedWorkflowInstances.forCall(instances, _))
    WorkflowCompletionServiceOperation.bindC(provided, op, WorkflowJsonCommandRequest.definition,
      spec.ResponseDefinition.void, codec, ContinuationRuntime.empty, issued, currentInstances,
      declaration.configuration, () => throw new IllegalStateException("Typed completion must use canonical managed UoW"),
      Some(closing), completion,
      descriptiveAttributes = SmWorkflowComponent.SplitPhaseServiceService.SubmitPartitionSemanticResultOperation.specification.content.descriptiveAttributes)
  }

  private def nativeC(core: ActionCall.Core, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[WorkflowManagedCompletion[S.Judgment]] = for {
    _ <- actorC(core, None)
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition), declaration.instances, declaration.continuations)
    native <- storage.bindCompletionC(C.completionOperation, J.requestCodec, J.resultCodec, P.closingType,
      (_, _) => Consequence.success(Vector.empty))
  } yield native

  private def currentReadsC(core: ActionCall.Core, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[D] =
    D.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)

  private def mutationActorC(core: ActionCall.Core): Consequence[Unit] = actorC(core, None).flatMap { _ =>
    if (core.action != null && core.action.request != null && core.action.request.service.contains(C.completionOperation.service) &&
        core.action.request.operation == C.completionOperation.operation) Consequence.unit
    else Consequence.securityPermissionDenied("SplitPhase mutation requires its exact owning completion Operation")
  }

  private def _context_reference_c(work: WorkflowInteraction[S.Request, Nothing]): Consequence[S.ContextReference] =
    work.current match {
      case order: WorkflowContinuation.WorkOrder[?] if order.request != null && order.request.input.nonEmpty =>
        Consequence.success(order.request.input.get.value.asInstanceOf[S.Request].context)
      case _ => Consequence.stateConflict("SplitPhase recovery has no issued typed context")
    }

  private def actorC(core: ActionCall.Core, participant: Option[String]): Consequence[Unit] =
    if (core == null || core.executionContext == null || core.executionContext.security == null ||
        !core.component.exists(c => c.componentId == SmWorkflowComponent.componentId && c.subsystem.nonEmpty))
      Consequence.securityPermissionDenied("SplitPhase completion requires its current owning native call")
    else {
      val security = core.executionContext.security
      val subject = SecuritySubject.from(security)
      if (subject.isAuthenticated && (subject.isProviderAuthenticated || subject.isTrustedLocalAuthenticated) &&
          (security.subjectKind == SubjectKind.User || security.subjectKind == SubjectKind.Service) &&
          participant.forall(_ == subject.subjectId)) Consequence.unit
      else Consequence.securityPermissionDenied("SplitPhase completion requires the admitted current participant")
    }
}
