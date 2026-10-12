package org.simplemodeling.textus.smworkflow.admission

import cats.syntax.all.*

import io.circe.Json
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, SubjectKind}
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceRecord, InstanceRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhasePartitionContextReference}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseScopeSemantic as S, SplitPhaseScopeSemanticJson as J,
  SplitPhaseTypedScopeWorkIssue as I, SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningCheckpointJsonV1 as KJ, SplitPhasePlanningCheckpointObservation as O,
  SplitPhasePlanningCheckpointDataStorePort as D, SplitPhasePlanningSnapshot as R}

/** Actual generated scope issue/completion. Only metadata is retained; native
  * transitions borrow the owning call's canonical Subsystem storage.
  *
  * @since Oct.  4, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhaseManagedScopeSemantic {
  final class Source private[SplitPhaseManagedScopeSemantic] (val record: K.Record,
    val predecessor: InstanceRecord, val current: InstanceRecord)
  /** Only an independently observed canonical native completion can construct
    * this source. Pure result admission and passive JSON cannot manufacture it.
    */
  final class CompletedSource private[SplitPhaseManagedScopeSemantic] (val source: Source,
    val plan: I.Plan, val completion: WorkflowCompletionRecordJsonV1.Record[S.Judgment],
    val admitted: S.Admitted, val next: InstanceRecord)
  final class PreparedProjection private[SplitPhaseManagedScopeSemantic] (val source: CompletedSource,
    val original: SplitPhaseExplicitPlanningAdvance.ProjectionRequired,
    val outcome: SplitPhaseExplicitCandidateProjection.ScopeOutcome)

  /** Constructed routing data, not a transport response or execution grant.
    * Only the owning port can produce pending work or completed provenance.
    */
  sealed trait Route
  case object NotRequired extends Route
  final class Pending private[SplitPhaseManagedScopeSemantic] (
    val work: WorkflowInteraction[S.Request, Nothing]) extends Route
  final class Completed private[SplitPhaseManagedScopeSemantic] (
    val source: CompletedSource) extends Route

  final class Port private[SplitPhaseManagedScopeSemantic] (
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog) {
    /** NotRequired means no scope requirement in this selected root, not a
      * missing pending publication. Native scope work is never reissued.
      */
    def routeForCallC(command: SplitPhaseAdvanceTarget, core: ActionCall.Core): Consequence[Route] = for {
      _ <- _actor_c(core, None)
      reads <- D.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)
      mapping <- reads.loadMappingForCallC(command, core)
      routed <- mapping match {
        case Some(saved) if saved.snapshot.projection.exists {
            case need: R.ProjectionView.Requirements => need.candidates.exists(_.isInstanceOf[R.CandidateProjectionView.SemanticView])
            case _ => false
          } => for {
          source <- _source_c(command, declaration, catalog, core)
          plan <- I.prepareC(source)
          published <- loadForCallC(I.identity(source), core)
          result <- published match {
            case Some(work) => for {
              result <- source.current.suspension match {
                case Some(_) =>
                  Consequence.success[Route](new Pending(work))
                case None => for {
                  actual <- _completed_source_c(plan, declaration, catalog, core).flatMap {
                    case Some(value) => Consequence.success(value)
                    case None => _invalid("unsuspended scope issue has no actual retained completion")
                  }
                } yield (new Completed(actual): Route)
              }
            } yield result
            case None =>
              _with_work_c(core) { (native, _, _) =>
                given ExecutionContext = core.executionContext
                native.suspendC(plan.intent, plan.work).map(_ => new Pending(plan.work): Route)
              }
          }
        } yield result
        case _ => Consequence.success[Route](NotRequired)
      }
    } yield routed

    def loadForCallC(identity: ContinuationIdentity, core: ActionCall.Core):
        Consequence[Option[WorkflowInteraction[S.Request, Nothing]]] = for {
      _ <- _actor_c(core, None)
      loaded <- _with_work_c(core)((_, issued, _) => issued.loadC(identity))
    } yield loaded

    /** Internal native preparation; no public routing success or publication. */
    def prepareProjectionForCallC(command: SplitPhaseAdvanceTarget, core: ActionCall.Core):
        Consequence[PreparedProjection] = for {
      completed <- completedForCallC(command, core)
      original <- SplitPhaseExplicitPlanningAdvance.restoreCompletedScopeC(completed)
      outcome <- SplitPhaseExplicitCandidateProjection.applyScopeC(original, completed.source.record, completed.admitted)
    } yield new PreparedProjection(completed, original, outcome)

    def completedForCallC(command: SplitPhaseAdvanceTarget, core: ActionCall.Core): Consequence[CompletedSource] = for {
      _ <- _actor_c(core, None)
      source <- _source_c(command, declaration, catalog, core)
      plan <- I.prepareC(source)
      issued <- loadForCallC(I.identity(source), core)
      completed <- _completed_source_c(plan, declaration, catalog, core).flatMap {
        case Some(value) => Consequence.success(value)
        case None => _invalid("completed scope selection has no actual retained completion")
      }
    } yield completed

    private def _with_work_c[A](call: ActionCall.Core)(f: (WorkflowManagedIssuedWork[S.Request],
      WorkflowIssuedWorkDataStorePersistence[S.Request], WorkflowInstancePersistence) => Consequence[A]): Consequence[A] = {
      final class CurrentCall(val core: ActionCall.Core) extends ProcedureActionCall {
        def runC(): Consequence[A] = {
          given ExecutionContext = executionContext
          val space = executionContext.dataStoreSpace
          val prior = space.captureBinding()
          try for {
            _ <- bind_component_application_datastore_c(declaration.configuration.workflowStore.name)
            storage <- _storage_c(core, declaration, catalog)
            native <- storage.bindIssuedC(I.completionOperation, J.requestCodec)
            store <- space.dataStore(declaration.instances)
            issued <- WorkflowIssuedWorkDataStorePersistence.bindC(store, declaration.configuration,
              SmWorkflowComponent.componentId, I.completionOperation, Vector(catalog.definition),
              declaration.instances, declaration.continuations, J.requestCodec)
            instances <- WorkflowInstanceDataStorePersistence.bindC(store, declaration.configuration,
              Vector(catalog.definition), declaration.instances)
            value <- f(native, issued, instances)
          } yield value
          finally space.restoreBinding(prior)
        }
        def execute(): Consequence[OperationResponse] = _invalid("scope storage borrowing is not a transport Operation")
      }
      new CurrentCall(call).runC()
    }
  }

  def bindC(params: ComponentCreate, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[Port] = for {
    storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
      Vector(catalog.definition), declaration.instances, declaration.continuations)
    _ <- storage.bindIssuedC(I.completionOperation, J.requestCodec)
  } yield new Port(declaration, catalog)

  def bindCompletionC(params: ComponentCreate, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[spec.OperationDefinition] = for {
    port <- bindC(params, declaration, catalog)
    storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
      Vector(catalog.definition), declaration.instances, declaration.continuations)
    native <- storage.bindCompletionC(I.completionOperation, J.requestCodec, J.resultCodec, I.closingType,
      (_, _) => Consequence.success(Vector.empty))
    op <- SplitPhaseDefinitionBinding.providedDefinition.providedOperations.find(operation =>
      operation.service == I.completionOperation.service && operation.name == I.completionOperation.operation)
      .map(Consequence.success).getOrElse(_invalid("scope completion differs from its generated ABI"))
    declared <- _operation_c(None, op, port, declaration, catalog, native)
  } yield CallScopedOperationBinding.wrapResult(declared, (core, result) =>
    _native_c(core, declaration, catalog).flatMap(native =>
      _operation_c(Some(core), op, port, declaration, catalog, native, Some(result))))

  private def _operation_c(core: Option[ActionCall.Core], op: GeneratedProvidedApiAbi.Operation, port: Port,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    native: WorkflowManagedCompletion[S.Judgment], result: Option[ContinuationResult[NativeWorkflowResultInput.Value]] = None): Consequence[spec.OperationDefinition] = {
    val instances = CallScopedWorkflowInstances.managed(declaration.configuration, catalog.definition,
      declaration.instances, declaration.continuations)
    var selected = Option.empty[(I.Plan, ContinuationResult[S.Judgment], InstanceRecord)]
    val issued = new IssuedWorkOrderPersistence[S.Request] {
      def putIfAbsentC(value: WorkflowInteraction[S.Request, Nothing]) = _invalid("scope completion cannot issue work")
      def loadC(identity: ContinuationIdentity) = core match {
        case Some(call) => port.loadForCallC(identity, call)
        case None => _invalid("scope completion requires its actual owning call")
      }
    }
    val codec = new WorkflowCompletionServiceOperation.Codec[S.Judgment] {
      val resultTypeIdentity = J.resultCodec.typeIdentity
      def decodeC(request: Request) = result.map(NativeWorkflowResultInput.typedC(_, J.resultCodec))
        .getOrElse(_invalid("scope completion has no resolved semantic result"))
      def encodeC(resume: ContinuationRuntime.Resume) = _invalid("scope acknowledgement requires actual verified commit")
      override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, submitted: ContinuationResult[S.Judgment],
        completed: Option[InstanceRecord]): Consequence[OperationResponse] = {
        val captured = selected
        selected = None
        captured match {
          case Some((plan, _, _)) => completed match {
            case Some(actual) => Consequence.success(NativeWorkflowOperationOutput.response(NativeWorkflowOperationOutput.accepted(submitted, actual)))
            case None => _uncertain("scope completion has no committed instance")
          }
          case None => _uncertain("scope completion has no selected disposition")
        }
      }
    }
    val closing = new WorkflowCompletionServiceOperation.ClosingProgram[S.Judgment] {
      def selectC(submitted: ContinuationResult[S.Judgment], current: InstanceRecord) = core match {
        case None => _invalid("scope closing selection requires its actual call")
        case Some(call) => for {
          work <- issued.loadC(submitted.continuationId).flatMap {
            case Some(value) => Consequence.success(value)
            case None => _invalid("scope completion has no actual issued work")
          }
          request <- _request_c(work)
          source <- _source_c(_command(request, work.handle.instanceIdentity.value), declaration, catalog, call)
          plan <- I.prepareC(source)
          next <- I.completedC(plan, submitted)
          frontier <- SplitPhaseExecutionFrontierDataStorePort.bindForCallC(call, declaration)
          nextFrontier <- frontier.stageCreateForCallC(next.identity, next.revision,
            SplitPhaseExecutionFrontier.Value.ScopeCompletion(submitted.continuationId), call)
        } yield {
          selected = Some((plan, submitted, next))
          val result = StateMachineOperationResult(I.closingType, submitted.resultReference)
          val program = nextFrontier.flatMap(_ => ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Completed(result)))
          WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next))
        }
      }
    }
    val currentinstances = core.fold[WorkflowInstancePersistence](instances)(CallScopedWorkflowInstances.forCall(instances, _))
    WorkflowCompletionServiceOperation.bindC(SplitPhaseDefinitionBinding.providedDefinition, op,
      SmWorkflowComponent.SplitPhaseServiceService.SubmitHandoffScopeResultOperation.specification.request, SmWorkflowComponent.SplitPhaseServiceService.SubmitHandoffScopeResultOperation.specification.response, codec, ContinuationRuntime.empty,
      issued, currentinstances, declaration.configuration,
      () => throw new IllegalStateException("Scope completion must use canonical managed UoW"), Some(closing), Some(native),
      descriptiveAttributes = SmWorkflowComponent.SplitPhaseServiceService.SubmitHandoffScopeResultOperation.specification.content.descriptiveAttributes)
  }

  /** Resolve the selected native computation, even after later history. Native
    * storage owns mechanical receipt/state admission; do not reconstruct and
    * compare another copy of its current instance or committed prefix.
    */
  private def _source_c(command: SplitPhaseAdvanceTarget, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[Source] = for {
    _ <- _actor_c(core, None)
    reads <- D.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)
    selected <- reads.loadSelectionForCallC(command, core).flatMap {
      case Some(value) => Consequence.success(value)
      case None => _invalid("scope immutable checkpoint index is absent")
    }
    record = selected.record
    source <- record.origin match {
      case _: K.Origin.Mechanical => for {
        _ <- _actor_c(core, Some(record.originalStart.participantIdentity))
        actual <- selected.mechanical match {
          case Some(value) => Consequence.success(value)
          case None => _invalid("scope original actual mechanical receipt is absent")
        }
        _ <- reads.requireDocumentsForCallC(record, core)
      } yield new Source(record, actual.receipt.next, actual.current)
      case K.Origin.PartitionCompletion(identity, _) => for {
        _ <- reads.requireDocumentsForCallC(record, core)
        original <- reads.loadOriginalForTargetC(command, core)
        _ <- _actor_c(core, Some(original.start.participantIdentity))
        instances = CallScopedWorkflowInstances.managed(declaration.configuration, catalog.definition,
          declaration.instances, declaration.continuations)
        current <- instances.loadForCallC(declaration.configuration, original.start.instanceIdentity, core).flatMap {
          case Some(value) => value.validateC
          case None => _invalid("scope native current instance is absent")
        }
        predecessor <- _prefix_c(current, record.committedRevision)
      } yield new Source(record, predecessor, current)
    }
  } yield source

  private def _observe_completion_c(plan: I.Plan, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core):
      Consequence[Option[WorkflowCompletionRecordJsonV1.Record[S.Judgment]]] =
    _completed_source_c(plan, declaration, catalog, core).map(_.map(_.completion))

  private def _completed_source_c(plan: I.Plan, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[Option[CompletedSource]] = for {
    native <- _native_c(core, declaration, catalog)
    loaded <- native.loadRetainedC(I.identity(plan.source))(using core.executionContext)
    checked <- loaded match {
      case None => Consequence.success(None)
      case Some(actual) => for {
        projected <- I.projectCompletionC(plan, actual.completion.result)
      } yield Some(new CompletedSource(plan.source, plan, actual.completion, projected.admitted, plan.source.current))
    }
  } yield checked

  private def _prefix_c(current: InstanceRecord, revision: InstanceRevision): Consequence[InstanceRecord] = {
    val history = current.history.takeWhile(_.revision.value <= revision.value)
    if (history.isEmpty || history.last.revision != revision) _invalid("scope checkpoint native history prefix is absent")
    else {
      val last = history.last
      current.copy(revision = last.revision, lifecycle = last.lifecycle, currentProgression = last.currentProgression,
        correlation = last.correlation, causation = last.causation, derivedCompositeOccurrence = last.derivedCompositeOccurrence,
        committedPredecessor = last.committedPredecessor, suspension = last.suspension, history = history).validateC
    }
  }
  private def _request_c(work: WorkflowInteraction[S.Request, Nothing]): Consequence[S.Request] = work.current match {
    case order: WorkflowContinuation.WorkOrder[?] if order.request != null && order.request.input.exists(value =>
        value != null && value.typeIdentity == J.requestCodec.typeIdentity && value.value.isInstanceOf[S.Request]) =>
      Consequence.success(order.request.input.get.value.asInstanceOf[S.Request])
    case _ => _invalid("scope work has no actual typed request")
  }
  private def _command(request: S.Request, instance: String): SplitPhaseAdvanceTarget = SplitPhaseAdvanceTarget(instance,
    SplitPhasePartitionContextReference(request.context.identity, request.context.revision))
  private def _storage_c(core: ActionCall.Core, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[WorkflowComponentStorage] = WorkflowComponentStorage.bindC(
      ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin), SmWorkflowComponent.componentId,
      declaration.configuration, Vector(catalog.definition), declaration.instances, declaration.continuations)
  private def _native_c(core: ActionCall.Core, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[WorkflowManagedCompletion[S.Judgment]] = for {
    _ <- _actor_c(core, None)
    storage <- _storage_c(core, declaration, catalog)
    native <- storage.bindCompletionC(I.completionOperation, J.requestCodec, J.resultCodec, I.closingType,
      (_, _) => Consequence.success(Vector.empty))
  } yield native

  private def _actor_c(core: ActionCall.Core, participant: Option[String]): Consequence[Unit] =
    if (core == null || core.action == null || core.action.request == null || core.executionContext == null ||
        core.executionContext.security == null || !core.component.exists(c => c.componentId == SmWorkflowComponent.componentId && c.subsystem.nonEmpty))
      Consequence.securityPermissionDenied("Scope work requires its actual owning native call")
    else {
      val security = core.executionContext.security
      val subject = SecuritySubject.from(security)
      val request = core.action.request
      if (subject.isAuthenticated && (subject.isProviderAuthenticated || subject.isTrustedLocalAuthenticated) &&
          (security.subjectKind == SubjectKind.User || security.subjectKind == SubjectKind.Service) &&
          participant.forall(_ == subject.subjectId) && request.service.contains(I.completionOperation.service) &&
          Set(K.advanceOperation.operation, I.completionOperation.operation,
            SplitPhaseNativeHumanDirection.completionOperation.operation,
            SplitPhaseTypedReasoningWorkIssue.completionOperation.operation).contains(request.operation)) Consequence.unit
      else Consequence.securityPermissionDenied("Scope work requires its original authenticated participant and owning Operation")
    }
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase managed scope " + reason)
  private def _uncertain[A](reason: String): Consequence[A] = DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
}
