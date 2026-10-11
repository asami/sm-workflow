package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, SubjectKind}
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Argument
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseRetainedReasoningSource as D,
  SplitPhaseTypedReasoningWorkIssue as I, SplitPhaseExplicitReasoningSemantic as R,
  SplitPhaseExplicitReasoningSemanticJson as J, SplitPhasePlanningCheckpointPreparation as K}

/** Internal owning-call issue/observation boundary. Workflow selects a genuine
  * native source; neither that data nor a pure Plan grants publication. Only
  * metadata survives calls, with no provider, pool, context or borrowed lease.
  * Public routing and provided completion are distinct required boundaries.
  *
  * @since Oct.  5, 2026
  * @version Oct. 11, 2026
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
        command <- _command_c(core)
        actual <- _source_c(selected, command, core)
        plan <- I.prepareC(actual)
        identity <- I.identityC(actual)
        result <- _with_native_c(core) { native =>
          given ExecutionContext = core.executionContext
          for {
            issued <- native.loadC(identity)
            result <- issued match {
              case Some(work) => Consequence.success(work)
              case None => for {
                _ <- native.suspendC(plan.intent, plan.work)
                _ <- observation()
              } yield plan.work
            }
          } yield result
        }
      } yield result

    /** Each reader keeps its actual owning purpose. No failure fallback or
      * passive-record-to-observed conversion is available here.
      */
    private def _source_c(selected: D.Source, command: SplitPhaseAdvanceCommand, core: ActionCall.Core):
        Consequence[D.Source] = {
      val host = core.component.get.subsystem.get.configuration
      selected.origin match {
        case _: D.Mechanical => for {
          reader <- SplitPhaseManagedPlanningCheckpoint.bindC(host, declaration, catalog)
          observed <- reader.loadMechanicalForCallC(command, core).flatMap {
            case Some(value) => Consequence.success(value)
            case None => _invalid("selected actual mechanical receipt is absent")
          }
          source <- D.mechanicalC(observed)
        } yield source
        case _: D.PartitionCompletion => for {
          reader <- SplitPhaseManagedPlanningCheckpoint.bindC(host, declaration, catalog)
          observed <- reader.loadPartitionCompletionForCallC(command, core).flatMap {
            case Some(value) => Consequence.success(value)
            case None => _invalid("selected actual partition completion is absent")
          }
          source <- D.partitionCompletionC(observed)
        } yield source
        case _: D.ScopePlanning => for {
          reader <- SplitPhaseManagedScopePlanningCheckpoint.bindC(host, declaration, catalog)
          observed <- reader.loadForCallC(command, core).flatMap {
            case Some(value) => Consequence.success(value)
            case None => _invalid("selected actual scope calculation is absent")
          }
          source <- D.scopePlanningC(observed)
        } yield source
        case _: D.HumanResolution => for {
          reader <- SplitPhaseManagedHumanResolutionCheckpoint.bindC(host, declaration, catalog)
          observed <- reader.loadForCallC(command, core).flatMap {
            case Some(value) => Consequence.success(value)
            case None => _invalid("selected actual Human calculation is absent")
          }
          source <- D.humanResolutionC(observed)
        } yield source
      }
    }


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
      required <- SplitPhaseDefinitionBinding.reasoningSemanticOperationC
      _ <- if (required.inputType.exists(_.value == J.requestCodec.typeIdentity) &&
          required.resultType.exists(_.value == J.resultCodec.typeIdentity)) Consequence.unit
        else _invalid("reasoning codecs differ from generated required SPI")
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
        Vector(catalog.definition), declaration.instances, declaration.continuations)
      _ <- storage.bindIssuedC(I.completionOperation, J.requestCodec)
    } yield new Port(declaration, catalog, observation)

  private def _command_c(core: ActionCall.Core): Consequence[SplitPhaseAdvanceCommand] =
    if (core == null || core.action == null || core.action.request == null || core.executionContext == null ||
        core.executionContext.security == null || !core.component.exists(component =>
          component.componentId == SmWorkflowComponent.componentId && component.subsystem.nonEmpty))
      Consequence.securityPermissionDenied("Reasoning issue requires its actual owning advance call")
    else {
      val subject = SecuritySubject.from(core.executionContext.security)
      val kind = core.executionContext.security.subjectKind
      val request = core.action.request
      if (!subject.isAuthenticated || !(subject.isProviderAuthenticated || subject.isTrustedLocalAuthenticated) ||
          !(kind == SubjectKind.User || kind == SubjectKind.Service) ||
          request.operation != K.advanceOperation.operation || !request.service.contains(K.advanceOperation.service))
        Consequence.securityPermissionDenied("Reasoning issue requires the original authenticated advance participant")
      else WorkflowJsonCommandRequest.commandArguments(request) match {
        case List(Argument("arg1", wire: String, _)) => SplitPhaseAdvanceCommandJsonV1.decodeC(wire)
        case _ => _invalid("owning advance requires one canonical generated command")
      }
    }

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase managed reasoning issue " + reason)
  private def _uncertain[A](reason: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate("SplitPhase managed reasoning issue " + reason)
}
