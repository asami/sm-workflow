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
  * @version Oct.  5, 2026
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
        command <- _command_c(core, selected.checkpoint.originalStart.command.participantIdentity)
        _ <- if (selected.checkpoint.originalStart.configuration == declaration.configuration &&
            command.instanceIdentity == selected.checkpoint.originalStart.instanceIdentity.value &&
            command.context.identity == selected.checkpoint.originalContext.reference.identity &&
            command.context.revision == selected.checkpoint.originalContext.reference.revision)
          Consequence.unit else _invalid("selected original configuration or owning command differs")
        actual <- _source_c(selected, command, core)
        _ <- if (_same_origin(actual, selected) && actual.checkpoint == selected.checkpoint &&
            actual.snapshot == selected.snapshot &&
            SplitPhaseDefinitionBinding.sameRecord(actual.publicationPostimage, selected.publicationPostimage))
          Consequence.unit else _invalid("independently observed full native source differs")
        plan <- I.prepareC(actual)
        identity <- I.identityC(actual)
        result <- _with_native_c(core) { native =>
          given ExecutionContext = core.executionContext
          for {
            issued <- native.loadC(identity)
            current <- _current_c(actual, core)
            _ <- if (SplitPhaseDefinitionBinding.sameRecord(current, actual.current)) Consequence.unit
              else _invalid("current native state differs across source and issued-work observation")
            result <- issued match {
              case Some(work) if work == plan.work && SplitPhaseDefinitionBinding.sameRecord(current, plan.next) =>
                Consequence.success(work)
              case Some(_) => _invalid("saved work or exact current reasoning suspension differs")
              case None if SplitPhaseDefinitionBinding.sameRecord(current, actual.publicationPostimage) =>
                native.suspendC(plan.intent, plan.work) match {
                  case Consequence.Success(committed) if SplitPhaseDefinitionBinding.sameRecord(committed.instance, plan.next) =>
                    observation() match {
                      case Consequence.Success(_) =>
                        (for {
                          observed <- native.loadC(identity)
                          next <- _current_c(actual, core)
                          work <- observed match {
                            case Some(work) if work == plan.work && SplitPhaseDefinitionBinding.sameRecord(next, plan.next) =>
                              Consequence.success(work)
                            case _ => _invalid("complete committed issue readback differs")
                          }
                        } yield work) match {
                          case Consequence.Success(work) => Consequence.success(work)
                          case _ => _uncertain("committed readback is unavailable or differs; observe later without retry")
                        }
                      case _ => _uncertain("postcommit observation interrupted; observe later without repeating issue")
                    }
                  case _ => _uncertain("native issue commit is not established; observe later without retry")
                }
              case None => _invalid("later native state lacks its actual published reasoning WorkOrder")
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

    private def _current_c(source: D.Source, core: ActionCall.Core): Consequence[InstanceRecord] =
      CallScopedWorkflowInstances.managed(declaration.configuration, catalog.definition,
        declaration.instances, declaration.continuations)
        .loadForCallC(declaration.configuration, source.publicationPostimage.identity, core).flatMap {
          case Some(value) => Consequence.success(value)
          case None => _invalid("actual canonical instance is absent")
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

  private def _same_origin(actual: D.Source, selected: D.Source): Boolean = (actual.origin, selected.origin) match {
    case (D.Mechanical(a), D.Mechanical(b)) => a.record == b.record &&
      SplitPhaseDefinitionBinding.sameRecord(a.publicationPostimage, b.publicationPostimage)
    case (D.PartitionCompletion(a), D.PartitionCompletion(b)) => a.record == b.record
    case (D.ScopePlanning(a), D.ScopePlanning(b)) => a.record == b.record && a.scope.record == b.scope.record
    case (D.HumanResolution(a), D.HumanResolution(b)) => a.record == b.record &&
      a.source.completion == b.source.completion && a.source.plan.question == b.source.plan.question
    case _ => false
  }

  private def _command_c(core: ActionCall.Core, participant: String): Consequence[SplitPhaseAdvanceCommand] =
    if (core == null || core.action == null || core.action.request == null || core.executionContext == null ||
        core.executionContext.security == null || !core.component.exists(component =>
          component.componentId == SmWorkflowComponent.componentId && component.subsystem.nonEmpty))
      Consequence.securityPermissionDenied("Reasoning issue requires its actual owning advance call")
    else {
      val subject = SecuritySubject.from(core.executionContext.security)
      val kind = core.executionContext.security.subjectKind
      val request = core.action.request
      if (!subject.isAuthenticated || !(subject.isProviderAuthenticated || subject.isTrustedLocalAuthenticated) ||
          !(kind == SubjectKind.User || kind == SubjectKind.Service) || subject.subjectId != participant ||
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
