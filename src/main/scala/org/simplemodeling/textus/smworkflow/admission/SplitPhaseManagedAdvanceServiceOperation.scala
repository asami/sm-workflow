package org.simplemodeling.textus.smworkflow.admission

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.workflow.{WorkflowWorkOrderJsonV1, WorkflowMechanicalTransition as M}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.Lifecycle
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitPartitionContext as C, SplitPhaseExplicitPartitionSemantic as S,
  SplitPhaseExplicitPartitionSemanticJson as J, SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseManagedPlanningCheckpoint as D, SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningSnapshot as R, SplitPhaseCandidateAllocationPolicy as H}

/** Actual generated public advance entry. Later frontier handlers are still
  * required: unavailable capability is an error, never invented WAIT/Terminal.
  * Binding retains metadata only; every effect borrows the actual owning Core.
  *
  * @since Oct.  4, 2026
  * @version Oct.  9, 2026
  */
private[smworkflow] object SplitPhaseManagedAdvanceServiceOperation {
  def bindHostC(params: ComponentCreate, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, humanDirection: Boolean = false,
    terminalObservation: () => Consequence[Unit] = () => Consequence.unit): Consequence[spec.OperationDefinition] = {
    val operations = SplitPhaseDefinitionBinding.providedDefinition.providedOperations.filter(operation =>
      operation.service == K.advanceOperation.service && operation.name == K.advanceOperation.operation)
    if (params == null || declaration == null || catalog == null || terminalObservation == null || operations.size != 1 ||
        operations.head.inputType != Some("SplitPhaseAdvanceCommand") || operations.head.resultType != Some("SplitPhaseAdvanceResult"))
      Consequence.configurationInvalid("SplitPhase public advance differs from its generated ABI or host dependencies")
    else D.bindC(params.configuration, declaration, catalog).map(port =>
      new BoundOperation(declaration, catalog, port, humanDirection, terminalObservation))
  }

  private final class BoundOperation(declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, checkpoints: D.Port, humandirection: Boolean,
    terminalobservation: () => Consequence[Unit]) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      SmWorkflowComponent.SplitPhaseServiceService.AdvanceSplitPhaseOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != K.advanceOperation.operation || !request.service.contains(K.advanceOperation.service))
        Consequence.stateConflict("SplitPhase public advance differs from its exact generated Operation")
      else WorkflowJsonCommandRequest.commandArguments(request) match {
        case List(Argument("arg1", wire: String, _)) => SplitPhaseAdvanceCommandJsonV1.decodeC(wire).map(command =>
          BoundAction(request, command, declaration, catalog, checkpoints, humandirection, terminalobservation))
        case _ => Consequence.stateConflict("SplitPhase public advance requires one complete JSON command")
      }
  }
  private final case class BoundAction(request: Request, command: SplitPhaseAdvanceCommand,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    checkpoints: D.Port, humandirection: Boolean, terminalobservation: () => Consequence[Unit]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall =
      BoundCall(core, command, declaration, catalog, checkpoints, humandirection, terminalobservation)
  }
  private final case class BoundCall(core: ActionCall.Core, command: SplitPhaseAdvanceCommand,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    checkpoints: D.Port, humandirection: Boolean, terminalobservation: () => Consequence[Unit]) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _advance_c(command, declaration, catalog, checkpoints, core, humandirection, terminalobservation)
      .map(OperationResponse.Scalar(_))
  }

  private def _advance_c(command: SplitPhaseAdvanceCommand, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, checkpoints: D.Port, core: ActionCall.Core,
    humandirection: Boolean, terminalobservation: () => Consequence[Unit]): Consequence[String] = for {
    observed <- checkpoints.loadCurrentForCallC(command, core)
    reasoning <- SplitPhaseDefinitionBinding.reasoningSemanticOperationC
    reasoninghuman <- SplitPhaseDefinitionBinding.reasoningHumanDirectionOperationC
    wire <- if (observed.current.suspension.exists(_.completion.value == reasoning.metadata.completionContract.identity))
      SplitPhaseManagedReasoningCompletion.pendingForAdvanceC(command, observed.current, declaration, catalog, core)
        .flatMap(SplitPhaseExplicitReasoningSemanticJson.encodeWorkOrderC)
    else if (observed.current.currentProgression.exists(p => Set(SplitPhaseSelectedDocumentEffect.applyingProgression,
        SplitPhaseManagedSelectedApply.appliedProgression).contains(p.value)) ||
        observed.current.causation.value.startsWith(SplitPhaseManagedSelectedApply.terminalPrefix))
      SplitPhaseManagedSelectedApply.advanceC(command, declaration, catalog, core, terminalobservation)
    else if (observed.current.currentProgression.exists(_.value == SplitPhaseManagedSelectedPreview.preparedProgression) ||
        observed.current.causation.value.startsWith(SplitPhaseManagedSelectedPreview.terminalPrefix))
      SplitPhaseManagedSelectedPreview.advanceC(command, declaration, catalog, core, terminalobservation)
    else if (observed.current.history.exists(_.suspension.exists(_.completion.value ==
        reasoninghuman.metadata.completionContract.identity)))
      if (!humandirection)
        Consequence.serviceUnavailable("SplitPhase saved reasoning Human direction requires its installed continuation handler; no native history was appended")
      else if (observed.current.suspension.nonEmpty)
        SplitPhaseReasoningHumanDirection.advanceC(command, declaration, catalog, core)
      else SplitPhaseManagedReasoningHumanCompletion.observeForAdvanceC(command, declaration, catalog, core).flatMap { human =>
        if (human.downstream.nonEmpty) _selected_c(command, declaration, catalog, core, terminalobservation)
        else SplitPhaseManagedReasoningHumanCompletion.terminalC(human)
      }
    else if (observed.current.suspension.isEmpty && observed.current.causation.value.contains(":reasoning:")) for {
      identity <- observed.current.history.reverseIterator.flatMap(_.suspension)
        .find(_.completion.value == reasoning.metadata.completionContract.identity)
        .map(v => Consequence.success(org.goldenport.cncf.workflow.ContinuationIdentity(v.continuationIdentity.value)))
        .getOrElse(Consequence.stateConflict("Saved reasoning result lacks its native assignment"))
      saved <- SplitPhaseManagedReasoningCompletion.observeForCallC(identity, declaration, catalog, core)
        .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("Saved reasoning completion is absent")))
      result <- saved.downstream match {
        case Some(snapshot) => R.dispositionC(snapshot).flatMap {
          case R.Disposition.Planned => _selected_c(command, declaration, catalog, core, terminalobservation)
          case disposition => Consequence.serviceUnavailable("SplitPhase saved reasoning successor " + disposition.toString +
            " requires its installed continuation handler; no older frontier was selected")
        }
        case None if humandirection => SplitPhaseReasoningHumanDirection.advanceC(command, declaration, catalog, core)
        case None => Consequence.serviceUnavailable("SplitPhase saved reasoning DecisionNeeded requires its installed Human continuation handler; no native history was appended")
      }
    } yield result
    else if (humandirection && observed.current.currentProgression.exists(_.value == "HumanPending"))
      _human_c(command, declaration, catalog, core, false, terminalobservation)
    else if (humandirection && (observed.current.lifecycle == Lifecycle.Completed ||
        observed.current.currentProgression.exists(_.value == "HumanAnswered")))
      _answered_human_c(command, declaration, catalog, core, terminalobservation)
    else _planning_c(command, declaration, catalog, checkpoints, observed, core, humandirection, terminalobservation)
  } yield wire

  private def _planning_c(command: SplitPhaseAdvanceCommand, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, checkpoints: D.Port, observed: D.ObservedCurrent,
    core: ActionCall.Core, humandirection: Boolean, observation: () => Consequence[Unit]): Consequence[String] = for {
    scopes <- SplitPhaseManagedScopeSemantic.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      declaration, catalog)
    routed <- scopes.routeForCallC(command, core)
    scoped <- _scope_response_c(routed, command, declaration, catalog, core, humandirection, observation)
    wire <- scoped match {
      case Some(actual) => Consequence.success(actual)
      case None => if (observed.current.suspension.nonEmpty) checkpoints.loadPendingPartitionForCallC(command, core).flatMap {
        case Some(actual) => WorkflowWorkOrderJsonV1.encodeC(actual, J.requestCodec)
        case None => Consequence.stateConflict("SplitPhase advance suspension disappeared; observe on a later call")
      } else checkpoints.loadRetainedForCallC(command, core).flatMap {
        case Some(actual: D.RetainedMechanical) => _frontier_c(actual.observed.record.snapshot,
          SplitPhaseRetainedReasoningSource.mechanicalC(actual.observed), command, declaration, catalog, core, observation)
        case Some(actual: D.RetainedPartitionCompletion) => actual.observed.record.checkpoint match {
          case Some(saved) => _frontier_c(saved.snapshot,
            SplitPhaseRetainedReasoningSource.partitionCompletionC(actual.observed), command, declaration, catalog, core, observation)
          case None => Consequence.serviceUnavailable("SplitPhase advance genuine human-direction completion awaits its typed Decision handler")
        }
        case None => _fresh_c(command, declaration, catalog, checkpoints, core, humandirection, observation)
      }
    }
  } yield wire

  private def _fresh_c(command: SplitPhaseAdvanceCommand, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, checkpoints: D.Port,
    core: ActionCall.Core, humandirection: Boolean, observation: () => Consequence[Unit]): Consequence[String] = for {
    inputs <- SplitPhaseTypedStartReadPolicy.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)
    bound <- inputs.bindPlanningForCallC(S.ContextReference(command.context.identity, command.context.revision), core).flatMap {
      case Some(value) => Consequence.success(value)
      case None => Consequence.stateConflict("SplitPhase advance fresh construction is absent")
    }
    outcome <- W.advanceC(bound.context.selection)
    wire <- outcome match {
      case need: W.PartitionSemanticRequired => for {
        semantic <- C.prepareSemanticC(bound.context, need)
        partition <- T.bindPartitionC(bound.start, semantic)
        issuer <- SplitPhaseManagedTypedPartitionIssue.bindC(ComponentCreate(core.component.get.subsystem.get,
          ComponentOrigin.Builtin), declaration, catalog)
        issued <- issuer.issueForCallC(partition, core)
      } yield issued
      case mechanical => _mechanical_c(bound, mechanical, checkpoints, declaration, catalog, core, humandirection, observation)
    }
  } yield wire

  private def _mechanical_c(bound: T.PlanningBound, outcome: W.Outcome, checkpoints: D.Port,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core, humandirection: Boolean, observation: () => Consequence[Unit]): Consequence[String] = for {
    policy <- H.decodeC(core.component.get.subsystem.get.configuration)
    anchor = _anchor(bound, policy)
    _ <- M.keyC(anchor)
    reference = K.Reference(anchor.identity + ":mapping", anchor.revision)
    captured <- outcome match {
      case need: W.AllocationRequired => for {
        allocated <- policy.allocateMechanicalC(need, anchor)
        continued <- W.continueMechanicalAllocatedC(allocated)
        actual <- K.captureAllocatedC(bound, allocated, continued, reference, anchor)
      } yield actual
      case actual => K.captureUnchangedC(bound, actual, reference, anchor)
    }
    committed <- checkpoints.executePreparedForCallC(captured, core)
    scopes <- SplitPhaseManagedScopeSemantic.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      declaration, catalog)
    command = SplitPhaseAdvanceCommand(bound.start.record.instanceIdentity.value,
      org.simplemodeling.textus.smworkflow.value.SplitPhasePartitionContextReference(
        bound.context.record.reference.identity, bound.context.record.reference.revision))
    routed <- scopes.routeForCallC(command, core)
    scoped <- _scope_response_c(routed, command, declaration, catalog, core, humandirection, observation)
    wire <- scoped.fold(_frontier_c(committed.record.snapshot,
      SplitPhaseRetainedReasoningSource.mechanicalC(committed), command, declaration, catalog, core, observation))(Consequence.success)
  } yield wire

  private def _anchor(bound: T.PlanningBound, policy: H.Policy): M.Reference = {
    val context = bound.context.record
    val identity = "sm-split:" + policy.namespace + ":policy:" + _encode(policy.identity) + "@" + _encode(policy.revision) +
      ":advance:" + _encode(bound.start.record.instanceIdentity.value) + ":context:" + _encode(context.reference.identity) +
      "@" + _encode(context.reference.revision) + ":native:" + context.owner.instanceRevision.value.toString
    M.Reference(identity, "1")
  }
  private def _encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

  /** Native completed provenance reaches the public routing owner, rather
    * than being converted into a premature failure inside the scope port.
    * The actual downstream handler is still required before emitting success.
    */
  private def _scope_response_c(route: SplitPhaseManagedScopeSemantic.Route, command: SplitPhaseAdvanceCommand,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core, humandirection: Boolean, observation: () => Consequence[Unit]): Consequence[Option[String]] = route match {
    case SplitPhaseManagedScopeSemantic.NotRequired => Consequence.success(None)
    case pending: SplitPhaseManagedScopeSemantic.Pending =>
      WorkflowWorkOrderJsonV1.encodeC(pending.work, SplitPhaseScopeSemanticJson.requestCodec).map(Some(_))
    case _: SplitPhaseManagedScopeSemantic.Completed if humandirection =>
      _human_c(command, declaration, catalog, core, true, observation).map(Some(_))
    case completed: SplitPhaseManagedScopeSemantic.Completed =>
      val decision = completed.source.completion.result.result.value.decision
      Consequence.serviceUnavailable("SplitPhase advance admitted " + decision.toString + " handoff scope result awaits its " +
        (if (decision == SplitPhaseScopeSemantic.Decision.Enriched) "whole-frontier mechanical projection" else "genuine human-direction") +
        " handler; no intermediate WAIT or Terminal was emitted")
  }

  private def _human_c(command: SplitPhaseAdvanceCommand, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core, publish: Boolean,
    observation: () => Consequence[Unit]): Consequence[String] = {
    val params = ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin)
    for {
    _ <- if (publish) SplitPhaseManagedScopeProjectionCheckpoint.bindC(params.configuration, declaration, catalog)
      .flatMap(_.executeForCallC(command, core)).map(_ => ()) else Consequence.unit
    human <- SplitPhaseManagedHumanDirection.bindC(params, declaration, catalog)
    question <- human.advanceForCallC(command, core)
    wire <- question match {
      case Some(actual) => org.goldenport.cncf.workflow.WorkflowDecisionJsonV1.encodeC(actual, SplitPhaseNativeHumanDirection.inputCodec)
      case None => for {
        reader <- SplitPhaseManagedScopePlanningCheckpoint.bindC(params.configuration, declaration, catalog)
        saved <- reader.executeForCallC(command, core)
        wire <- _frontier_c(saved.record.downstream, SplitPhaseRetainedReasoningSource.scopePlanningC(saved),
          command, declaration, catalog, core, observation)
      } yield wire
    }
    } yield wire
  }

  private def _answered_human_c(command: SplitPhaseAdvanceCommand,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core, observation: () => Consequence[Unit]): Consequence[String] = for {
    answer <- SplitPhaseManagedHumanCompletion.observeAnswerForAdvanceC(command, declaration, catalog, core)
    wire <- answer.completion.result.result.value match {
      case _: SplitPhaseHumanDirectionAnswerContract.Answer.ProvideResolution => for {
        reader <- SplitPhaseManagedHumanResolutionCheckpoint.bindC(core.component.get.subsystem.get.configuration,
          declaration, catalog)
        saved <- reader.executeForCallC(command, core)
        wire <- _frontier_c(saved.record.downstream, SplitPhaseRetainedReasoningSource.humanResolutionC(saved),
          command, declaration, catalog, core, observation)
      } yield wire
      case _ => SplitPhaseHumanTerminal.advanceC(command, declaration, catalog, core, observation)
    }
  } yield wire

  /** Only the genuine observed lineage, reobserved by the owning issuer,
    * can publish reasoning. Other frontiers still require their own handlers.
    */
  private def _frontier_c(snapshot: R.Record, source: => Consequence[SplitPhaseRetainedReasoningSource.Source],
    command: SplitPhaseAdvanceCommand,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core, observation: () => Consequence[Unit]): Consequence[String] = R.dispositionC(snapshot).flatMap {
      case R.Disposition.ReasoningRequired => for {
        selected <- source
        issuer <- SplitPhaseManagedReasoningIssue.bindC(
          ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin), declaration, catalog)
        work <- issuer.issueForCallC(selected, core)
        wire <- SplitPhaseExplicitReasoningSemanticJson.encodeWorkOrderC(work)
      } yield wire
      case R.Disposition.Planned => _selected_c(command, declaration, catalog, core, observation)
      case disposition => Consequence.serviceUnavailable("SplitPhase advance retained " + disposition.toString +
        " frontier awaits its installed continuation handler; no intermediate WAIT or Terminal was emitted")
    }
  private def _selected_c(command: SplitPhaseAdvanceCommand, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core, observation: () => Consequence[Unit]): Consequence[String] =
    SplitPhaseManagedPlanningFrontier.loadForAdvanceC(command, declaration, catalog, core).flatMap { source =>
      source.invocationMode match {
        case "PREVIEW" => SplitPhaseManagedSelectedPreview.advanceObservedC(command, source, declaration, catalog, core, observation)
        case "APPLY" => SplitPhaseManagedSelectedApply.advanceObservedC(command, source, declaration, catalog, core, observation)
        case _ => Consequence.stateConflict("SplitPhase actual original invocation mode is unsupported")
      }
    }
}
