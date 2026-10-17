package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseRetainedReasoningSource as D,
  SplitPhaseTypedReasoningWorkIssue as I, SplitPhaseExplicitReasoningSemantic as R,
  SplitPhaseExplicitReasoningSemanticJson as J}

/** Internal owning-call issue/observation boundary. Workflow selects a genuine
  * native source; neither that data nor a pure Plan grants publication. Only
  * metadata survives calls, with no provider, pool, context or borrowed lease.
  * Public routing and provided completion are distinct required boundaries.
  *
  * @since Oct.  5, 2026
  *  version Oct. 12, 2026
  * @version Oct. 16, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object SplitPhaseManagedReasoningIssue {
  final class Port private[SplitPhaseManagedReasoningIssue] (
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, observation: () => Consequence[Unit]) {

    def issueForCallC(selected: D.Source, core: ActionCall.Core):
        Consequence[WorkflowInteraction[R.Request, Nothing]] =
      if (selected == null) _invalid("actual selected native source is absent")
      else for {
        _ <- SplitPhaseReasoningInvocation.actorC(core,
          Some(selected.checkpoint.originalStart.participantIdentity))
        plan <- I.prepareC(selected)
        identity <- I.identityC(selected)
        result <- _with_native_c(core) { native =>
          given ExecutionContext = core.executionContext
          for {
            issued <- native.loadC(identity)
            result <- issued match {
              case Some(work) => Consequence.success(work)
              case None => for {
                selected <- SplitPhaseNativeExecution.bindIssuedC(plan.work, SplitPhaseNativeExecution.Work.Reasoning,
                  declaration.executionClassifications, declaration.executionProfiles, declaration.executionOrigin)
                _ <- native.suspendC(plan.intent, selected)
                _ <- observation()
              } yield selected
            }
          } yield result
        }
      } yield result

    private def _with_native_c[A](call: ActionCall.Core)(f: WorkflowManagedIssuedWork[R.Request] => Consequence[A]):
        Consequence[A] = {
      final class CurrentCall(val core: ActionCall.Core) extends ProcedureActionCall {
        def runC(): Consequence[A] = {
          given ExecutionContext = executionContext
          val space = executionContext.dataStoreSpace
          val prior = space.captureBinding()
          try for {
            _ <- bind_component_application_datastore_c(declaration.configuration.workflowStore.name)
            storage <- WorkflowComponentStorage.bindC(
              ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin), SmWorkflowComponent.componentId,
              declaration.configuration, Vector(catalog.definition), declaration.instances, declaration.continuations)
            native <- storage.bindIssuedC(I.completionOperation, J.requestCodec)
            value <- f(native)
          } yield value
          finally space.restoreBinding(prior)
        }
        def execute(): Consequence[OperationResponse] = _invalid("storage borrowing is not a transport Operation")
      }
      new CurrentCall(call).runC()
    }
  }

  def bindC(params: ComponentCreate, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[Port] =
    bindWithReadbackObservationC(params, declaration, catalog, () => Consequence.unit)

  /** Inert test-only interruption after actual commit, not a commit adapter. */
  private[admission] def bindWithReadbackObservationC(params: ComponentCreate,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    observation: () => Consequence[Unit]): Consequence[Port] =
    if (params == null || declaration == null || catalog == null || observation == null)
      _invalid("admitted component metadata or observation is absent")
    else for {
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
        Vector(catalog.definition), declaration.instances, declaration.continuations)
      _ <- storage.bindIssuedC(I.completionOperation, J.requestCodec)
    } yield new Port(declaration, catalog, observation)

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase managed reasoning issue " + reason)
  private def _uncertain[A](reason: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate("SplitPhase managed reasoning issue " + reason)
}
