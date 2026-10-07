package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, SubjectKind}
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowInteraction
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseTypedPartitionWorkIssue as I, SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPartitionSemanticJson as J}

/**
 * Internal host capability. No public selector, pool, provider or retained call.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseManagedTypedPartitionIssue {
  final class Port private[SplitPhaseManagedTypedPartitionIssue] (
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog) {
    def issueForCallC(binding: T.PartitionBound, core: ActionCall.Core): Consequence[String] =
      if (binding == null || binding.context.record.owner.configuration != declaration.configuration)
        Consequence.stateConflict("SplitPhase partition issue differs from its bound Workflow configuration")
      else for {
        _ <- actorC(core, Some(binding.start.record.command.participantIdentity))
        policy <- SplitPhasePartitionReplyAllocationPolicy.decodeC(core.component.get.subsystem.get.configuration)
        allocated <- policy.allocateC(binding)
        plan <- I.prepareC(binding, allocated)
        wire <- withWorkForCallC(core) { (work, issued, instances) =>
          given ExecutionContext = core.executionContext
          instances.load(declaration.configuration, plan.intent.current.identity).flatMap {
            case Some(actual) if actual == plan.intent.current => work.suspendC(plan.intent, plan.interaction) match {
            case Consequence.Failure(_) => uncertainC("SplitPhase partition issue is not established; observe on a later call")
            case Consequence.Success(retained) if retained.instance == plan.projected =>
              issued.loadC(binding.context.record.owner.continuation) match {
                case Consequence.Success(Some(actual)) if actual == plan.interaction => Consequence.success(plan.wire)
                case _ => uncertainC("SplitPhase partition committed WorkOrder readback differs or is unavailable")
              }
            case _ => uncertainC("SplitPhase partition committed suspension differs")
            }
            case _ => Consequence.stateConflict("SplitPhase partition issue predecessor differs from its current call")
          }
        }
      } yield wire

    /** Immutable observed work only; no progression, result admission or claim. */
    def loadForCallC(identity: ContinuationIdentity, core: ActionCall.Core):
        Consequence[Option[WorkflowInteraction[S.Request, Nothing]]] = for {
      _ <- actorC(core, None)
      loaded <- withWorkForCallC(core)((_, issued, _) => issued.loadC(identity))
    } yield loaded

    private def withWorkForCallC[A](current: ActionCall.Core)(
      f: (WorkflowManagedIssuedWork[S.Request], WorkflowIssuedWorkDataStorePersistence[S.Request],
        WorkflowInstancePersistence) => Consequence[A]): Consequence[A] = {
      final class CurrentCall(val core: ActionCall.Core) extends ProcedureActionCall {
        def runC(): Consequence[A] = {
          given ExecutionContext = executionContext
          val space = executionContext.dataStoreSpace
          val prior = space.captureBinding()
          try for {
            _ <- bind_component_application_datastore_c(declaration.configuration.workflowStore.name)
            storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
              SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition),
              declaration.instances, declaration.continuations)
            work <- storage.bindIssuedC(C.completionOperation, J.requestCodec)
            store <- space.dataStore(declaration.instances)
            issued <- WorkflowIssuedWorkDataStorePersistence.bindC(store, declaration.configuration,
              SmWorkflowComponent.componentId, C.completionOperation, Vector(catalog.definition),
              declaration.instances, declaration.continuations, J.requestCodec)
            instances <- WorkflowInstanceDataStorePersistence.bindC(store, declaration.configuration,
              Vector(catalog.definition), declaration.instances)
            result <- f(work, issued, instances)
          } yield result
          finally space.restoreBinding(prior)
        }
        def execute(): Consequence[OperationResponse] =
          Consequence.stateConflict("SplitPhase partition issue projection is not a transport Operation")
      }
      new CurrentCall(current).runC()
    }
  }

  def bindC(params: ComponentCreate, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[Port] =
    if (declaration == null || catalog == null)
      Consequence.configurationInvalid("SplitPhase typed partition issue composition is incomplete")
    else WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
      Vector(catalog.definition), declaration.instances, declaration.continuations)
      .flatMap(_.bindIssuedC(C.completionOperation, J.requestCodec)).map(_ => new Port(declaration, catalog))

  private def actorC(core: ActionCall.Core, participant: Option[String]): Consequence[Unit] =
    if (core == null || core.executionContext == null || core.executionContext.security == null ||
        !core.component.exists(c => c.componentId == SmWorkflowComponent.componentId && c.subsystem.nonEmpty))
      Consequence.securityPermissionDenied("SplitPhase partition issue requires its current owning native call")
    else {
      val security = core.executionContext.security
      val subject = SecuritySubject.from(security)
      if (subject.isAuthenticated && (subject.isProviderAuthenticated || subject.isTrustedLocalAuthenticated) &&
          (security.subjectKind == SubjectKind.User || security.subjectKind == SubjectKind.Service) &&
          participant.forall(_ == subject.subjectId)) Consequence.unit
      else Consequence.securityPermissionDenied("SplitPhase partition issue requires the admitted current participant")
    }
  private def uncertainC[A](message: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate(message)
}
