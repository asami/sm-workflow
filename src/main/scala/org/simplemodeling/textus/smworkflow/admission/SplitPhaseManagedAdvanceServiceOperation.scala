package org.simplemodeling.textus.smworkflow.admission

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.workflow.{WorkflowWorkOrderJsonV1, WorkflowMechanicalTransition as M}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.Lifecycle
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitPartitionContext as C, SplitPhaseExplicitPartitionSemantic as S,
  SplitPhaseExplicitPartitionSemanticJson as J, SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseManagedPlanningCheckpoint as D, SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningSnapshot as R, SplitPhaseCandidateAllocationPolicy as H}

/** Actual generated public advance entry. Later frontier handlers are still
  * required: unavailable capability is an error, never invented WAIT/Terminal.
  * Binding retains metadata only; every effect borrows the actual owning Core.
  *
  * @since   Oct.  4, 2026
  * @version Oct. 23, 2026
  */
private[smworkflow] object SplitPhaseManagedAdvanceServiceOperation {
  def bindRuntimeC(params: ComponentCreate, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, humanDirection: Boolean = false,
    terminalObservation: () => Consequence[Unit] = () => Consequence.unit): Consequence[spec.OperationDefinition] = {
    if (params == null || declaration == null || catalog == null || terminalObservation == null)
      Consequence.configurationInvalid("SplitPhase advance requires its configured native host dependencies")
    else D.bindC(params.configuration, declaration, catalog).map(port =>
      new BoundOperation(declaration, catalog, port, humanDirection, terminalObservation))
  }

  private final class BoundOperation(declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, checkpoints: D.Port, humandirection: Boolean,
    terminalobservation: () => Consequence[Unit]) extends NativeWorkflowAdvanceInput.Receiver {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      SmWorkflowComponent.SplitPhaseServiceService.AdvanceSplitPhaseOperation)
    def createAdvanceOperationRequestC(request: Request,
      input: NativeWorkflowAdvanceInput.Resolved): Consequence[OperationRequest] = input match {
      case NativeWorkflowAdvanceInput.Split(command, current) => Consequence.success(
        BoundAction(request, command, current, declaration, catalog, checkpoints, humandirection, terminalobservation))
      case _ => Consequence.stateConflict("SplitPhase advance requires its own selected target")
    }
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      Consequence.configurationInvalid("SplitPhase advance requires its session-resolved native target")
  }
  private final case class BoundAction(request: Request, command: SplitPhaseAdvanceTarget,
    current: org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    checkpoints: D.Port, humandirection: Boolean, terminalobservation: () => Consequence[Unit]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall =
      BoundCall(core, command, current, declaration, catalog, checkpoints, humandirection, terminalobservation)
  }
  private final case class BoundCall(core: ActionCall.Core, command: SplitPhaseAdvanceTarget,
    current: org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    checkpoints: D.Port, humandirection: Boolean, terminalobservation: () => Consequence[Unit]) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      _advance_c(command, current, declaration, catalog, checkpoints, core, humandirection, terminalobservation)
      .map(NativeWorkflowOperationOutput.response(_))
  }

  private def _advance_c(command: SplitPhaseAdvanceTarget,
    current: org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, checkpoints: D.Port, core: ActionCall.Core,
    humandirection: Boolean, terminalobservation: () => Consequence[Unit]): Consequence[V.SplitPhaseAdvanceResult] = for {
    storage <- org.goldenport.cncf.workflow.WorkflowComponentStorage.bindC(
      ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin), SmWorkflowComponent.componentId,
      declaration.configuration, Vector(catalog.definition), declaration.instances, declaration.continuations)
    starts <- SplitPhaseTypedStartReadPolicy.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)
    _ <- SplitPhaseReasoningInvocation.actorC(core, None)
    frontier <- current.suspension match {
      case Some(_) => Consequence.success(Option.empty[SplitPhaseExecutionFrontier.Value])
      case None => for {
        reader <- SplitPhaseExecutionFrontierDataStorePort.bindForCallC(core, declaration)
        value <- reader.loadForCallC(current.identity, current.revision, core).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("Split current business reference is absent")))
      } yield Some(value)
    }
    selectedstart <- frontier match {
      case Some(_: SplitPhaseExecutionFrontier.Value.Initial) =>
        starts.prepareForCallC(T.Reference("split-start:" + current.identity.value, "1"), core).flatMap(
          _.map(value => Consequence.success(value.record.routing -> Some(value)))
            .getOrElse(Consequence.stateConflict("Split execution Start is absent")))
      case _ => starts.observeForCallC(T.Reference("split-start:" + current.identity.value, "1"), core).flatMap(
        _.map(value => Consequence.success(value -> Option.empty[T.Prepared]))
          .getOrElse(Consequence.stateConflict("Split execution Start is absent")))
    }
    start = selectedstart._1
    _ <- SplitPhaseReasoningInvocation.actorC(core, Some(start.participantIdentity))
    wire <- current.suspension match {
      case Some(boundary) => _pending_c(boundary, storage, core)
      case None =>
        val source = frontier.get
        source match {
          case SplitPhaseExecutionFrontier.Value.Initial(_) =>
            _fresh_c(command, selectedstart._2.get, current, declaration, catalog, checkpoints, core, humandirection, terminalobservation)
          case SplitPhaseExecutionFrontier.Value.MechanicalPlanning(reference) =>
            checkpoints.loadMechanicalForReferenceC(command, reference, core).flatMap { actual =>
              _scope_or_frontier_c(SplitPhaseManagedPlanningFrontier.observed(actual.record.snapshot,
                actual.publicationPostimage, current, start.invocationMode), actual.record, SplitPhaseRetainedReasoningSource.mechanicalC(actual),
                command, declaration, catalog, core, humandirection, terminalobservation)
            }
          case SplitPhaseExecutionFrontier.Value.PartitionCompletion(work) =>
            SplitPhaseManagedTypedPartitionCompletion.observeForCallC(work, declaration, catalog, core).flatMap {
              case Some(actual) => actual.record.checkpoint match {
                case Some(saved) => for {
                  reads <- SplitPhasePlanningCheckpointDataStorePort.bindC(core.component.get.subsystem.get.configuration,
                    declaration, catalog)
                  _ <- reads.requireDocumentsForCallC(saved, core)
                  wire <- _scope_or_frontier_c(SplitPhaseManagedPlanningFrontier.observed(saved.snapshot,
                    actual.record.next, current, start.invocationMode), saved, SplitPhaseRetainedReasoningSource.partitionCompletionC(actual),
                    command, declaration, catalog, core, humandirection, terminalobservation)
                } yield wire
                case None => Consequence.serviceUnavailable("Partition DecisionNeeded requires its installed Human handler")
              }
              case None => Consequence.stateConflict("Split partition completion is absent")
            }
          case _: SplitPhaseExecutionFrontier.Value.ScopeCompletion =>
            if (humandirection) _human_c(command, start.invocationMode, declaration, catalog, core, true, terminalobservation)
            else Consequence.serviceUnavailable("Scope continuation requires its installed handler")
          case _: SplitPhaseExecutionFrontier.Value.ScopeProjection =>
            if (humandirection) _human_c(command, start.invocationMode, declaration, catalog, core, false, terminalobservation)
            else Consequence.serviceUnavailable("Scope Human continuation is not installed")
          case _: SplitPhaseExecutionFrontier.Value.ScopePlanning => for {
            reader <- SplitPhaseManagedScopePlanningCheckpoint.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)
            saved <- reader.loadForCallC(command, core).flatMap(_.map(Consequence.success).getOrElse(
              Consequence.stateConflict("Split scope calculation is absent")))
            wire <- _frontier_c(SplitPhaseManagedPlanningFrontier.observed(saved.record.downstream,
              saved.record.next, saved.current, start.invocationMode), SplitPhaseRetainedReasoningSource.scopePlanningC(saved),
              command, declaration, catalog, core, terminalobservation)
          } yield wire
          case _: SplitPhaseExecutionFrontier.Value.ScopeHumanAnswer =>
            _answered_human_c(command, start.invocationMode, declaration, catalog, core, terminalobservation)
          case _: SplitPhaseExecutionFrontier.Value.HumanResolution => for {
            reader <- SplitPhaseManagedHumanResolutionCheckpoint.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)
            saved <- reader.loadForCallC(command, core).flatMap(_.map(Consequence.success).getOrElse(
              Consequence.stateConflict("Split Human calculation is absent")))
            wire <- _frontier_c(SplitPhaseManagedPlanningFrontier.observed(saved.record.downstream,
              saved.record.next, saved.current, start.invocationMode), SplitPhaseRetainedReasoningSource.humanResolutionC(saved),
              command, declaration, catalog, core, terminalobservation)
          } yield wire
          case SplitPhaseExecutionFrontier.Value.ReasoningCompletion(work) =>
            SplitPhaseManagedReasoningCompletion.observeForCallC(work, declaration, catalog, core).flatMap {
              case Some(saved) => saved.downstream match {
                case Some(snapshot) => R.dispositionC(snapshot).flatMap {
                  case R.Disposition.Planned => _selected_c(command,
                    SplitPhaseManagedPlanningFrontier.observed(snapshot, saved.next, current, start.invocationMode),
                    declaration, catalog, core, terminalobservation)
                  case disposition => Consequence.serviceUnavailable("Reasoning successor " + disposition + " requires its handler")
                }
                case None if humandirection => SplitPhaseReasoningHumanDirection.advanceObservedC(saved, declaration, catalog, core)
                case None => Consequence.serviceUnavailable("Reasoning Human continuation is not installed")
              }
              case None => Consequence.stateConflict("Split reasoning completion is absent")
            }
          case _: SplitPhaseExecutionFrontier.Value.ReasoningHumanAnswer =>
            SplitPhaseManagedReasoningHumanCompletion.observeForAdvanceC(command, declaration, catalog, core).flatMap { human =>
              if (human.downstream.nonEmpty) _selected_c(command,
                SplitPhaseManagedPlanningFrontier.observed(human.downstream.get, human.next, current, start.invocationMode),
                declaration, catalog, core, terminalobservation)
              else SplitPhaseManagedReasoningHumanCompletion.terminalC(human)
            }
          case _: SplitPhaseExecutionFrontier.Value.SelectedPreview | _: SplitPhaseExecutionFrontier.Value.SelectedApply =>
            SplitPhaseManagedPlanningFrontier.loadSelectedForCallC(command, current, start, source,
              declaration, catalog, core).flatMap(_selected_c(command, _, declaration, catalog, core, terminalobservation))
          case SplitPhaseExecutionFrontier.Value.Stopped(answer) =>
            if (SplitPhaseManagedReasoningHumanCompletion.isReasoning(answer))
              SplitPhaseManagedReasoningHumanCompletion.observeForAdvanceC(command, declaration, catalog, core)
                .flatMap(SplitPhaseManagedReasoningHumanCompletion.terminalC)
            else SplitPhaseHumanTerminal.advanceC(command, declaration, catalog, core, terminalobservation)
        }
    }
  } yield wire

  private def _pending_c(boundary: org.goldenport.cncf.workflow.WorkflowInstancePersistence.SuspensionBoundary,
    storage: org.goldenport.cncf.workflow.WorkflowComponentStorage, core: ActionCall.Core): Consequence[V.SplitPhaseAdvanceResult] = {
    import org.goldenport.cncf.workflow.*
    val identity = ContinuationIdentity(boundary.continuationIdentity.value)
    for {
      partition <- SplitPhaseDefinitionBinding.partitionSemanticOperationC
      scope <- SplitPhaseDefinitionBinding.scopeSemanticOperationC
      reasoning <- SplitPhaseDefinitionBinding.reasoningSemanticOperationC
      human <- SplitPhaseDefinitionBinding.humanDirectionOperationC
      reasoninghuman <- SplitPhaseDefinitionBinding.reasoningHumanDirectionOperationC
      wire <- boundary.completion.value match {
        case value if value == partition.metadata.completionContract.identity => for {
          reader <- storage.bindIssuedC(C.completionOperation, J.requestCodec)
          work <- reader.loadC(identity)(using core.executionContext).flatMap(_.map(Consequence.success).getOrElse(
            Consequence.stateConflict("Split partition assignment is absent")))
          wire <- NativeWorkflowOperationOutput.splitPartitionRequestC(work)
        } yield wire
        case value if value == scope.metadata.completionContract.identity => for {
          reader <- storage.bindIssuedC(SplitPhaseTypedScopeWorkIssue.completionOperation, SplitPhaseScopeSemanticJson.requestCodec)
          work <- reader.loadC(identity)(using core.executionContext).flatMap(_.map(Consequence.success).getOrElse(
            Consequence.stateConflict("Split scope assignment is absent")))
          wire <- NativeWorkflowOperationOutput.splitScopeRequestC(work)
        } yield wire
        case value if value == reasoning.metadata.completionContract.identity => for {
          reader <- storage.bindIssuedC(SplitPhaseTypedReasoningWorkIssue.completionOperation, SplitPhaseExplicitReasoningSemanticJson.requestCodec)
          work <- reader.loadC(identity)(using core.executionContext).flatMap(_.map(Consequence.success).getOrElse(
            Consequence.stateConflict("Split reasoning assignment is absent")))
          wire <- NativeWorkflowOperationOutput.splitReasoningRequestC(work)
        } yield wire
        case value if value == human.metadata.completionContract.identity => for {
          reader <- storage.bindDecisionC(SplitPhaseNativeHumanDirection.completionOperation, SplitPhaseNativeHumanDirection.inputCodec)
          question <- reader.loadC(identity)(using core.executionContext).flatMap(_.map(Consequence.success).getOrElse(
            Consequence.stateConflict("Split Human question is absent")))
          wire <- NativeWorkflowOperationOutput.splitScopeHumanC(question, core)
        } yield wire
        case value if value == reasoninghuman.metadata.completionContract.identity => for {
          reader <- storage.bindDecisionC(SplitPhaseNativeHumanDirection.completionOperation, SplitPhaseReasoningHumanDirection.inputCodec)
          question <- reader.loadC(identity)(using core.executionContext).flatMap(_.map(Consequence.success).getOrElse(
            Consequence.stateConflict("Split reasoning Human question is absent")))
          wire <- NativeWorkflowOperationOutput.splitReasoningHumanC(question, core)
        } yield wire
        case _ => Consequence.stateConflict("Split pending assignment has no declared handler")
      }
    } yield wire
  }

  private def _scope_or_frontier_c(selected: SplitPhaseManagedPlanningFrontier.Observed,
    checkpoint: K.Record,
    source: => Consequence[SplitPhaseRetainedReasoningSource.Source], command: SplitPhaseAdvanceTarget,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core, humandirection: Boolean, observation: () => Consequence[Unit]): Consequence[V.SplitPhaseAdvanceResult] = for {
    scoped <- if (selected.snapshot.projection.exists {
      case need: R.ProjectionView.Requirements => need.candidates.exists(_.isInstanceOf[R.CandidateProjectionView.SemanticView])
      case _ => false
    }) for {
      scopes <- SplitPhaseManagedScopeSemantic.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin), declaration, catalog)
      route <- scopes.routeForCallC(checkpoint, selected.publicationPostimage, selected.current, core)
      response <- _scope_response_c(route, command, selected.invocationMode, declaration, catalog, core, humandirection, observation).map(Some(_))
    } yield response else Consequence.success(Option.empty[V.SplitPhaseAdvanceResult])
    wire <- scoped.fold(_frontier_c(selected, source, command, declaration, catalog, core, observation))(Consequence.success)
  } yield wire


  private def _fresh_c(command: SplitPhaseAdvanceTarget, start: T.Prepared,
    current: org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, checkpoints: D.Port,
    core: ActionCall.Core, humandirection: Boolean, observation: () => Consequence[Unit]): Consequence[V.SplitPhaseAdvanceResult] = for {
    inputs <- SplitPhaseTypedStartReadPolicy.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)
    bound <- inputs.bindPlanningForCurrentCallC(S.ContextReference(command.context.identity, command.context.revision), start, current, core).flatMap {
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
        work <- issuer.issueForCallC(partition, core)
        issued <- NativeWorkflowOperationOutput.splitPartitionRequestC(work)
      } yield issued
      case mechanical => _mechanical_c(bound, mechanical, checkpoints, declaration, catalog, core, humandirection, observation)
    }
  } yield wire

  private def _mechanical_c(bound: T.PlanningBound, outcome: W.Outcome, checkpoints: D.Port,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core, humandirection: Boolean, observation: () => Consequence[Unit]): Consequence[V.SplitPhaseAdvanceResult] = for {
    policy <- H.decodeC(core.component.get.subsystem.get.configuration)
    anchor = _anchor(bound, policy)
    _ <- M.keyC(anchor)
    reference = K.Reference(anchor.identity + ":mapping", anchor.revision)
    captured <- outcome match {
      case need: W.AllocationRequired => for {
        allocated <- policy.allocateMechanicalC(need, anchor)
        continued <- W.continueMechanicalAllocatedC(allocated)
        actual <- K.captureMechanicalC(bound, continued, reference, anchor)
      } yield actual
      case actual => K.captureMechanicalC(bound, actual, reference, anchor)
    }
    committed <- checkpoints.executePreparedForCallC(captured, core)
    command = SplitPhaseAdvanceTarget(bound.start.record.instanceIdentity.value,
      org.simplemodeling.textus.smworkflow.value.SplitPhasePartitionContextReference(
        bound.context.record.reference.identity, bound.context.record.reference.revision))
    wire <- _scope_or_frontier_c(SplitPhaseManagedPlanningFrontier.observed(committed.record.snapshot,
      committed.publicationPostimage, committed.current, bound.start.record.input.invocationMode), committed.record,
      SplitPhaseRetainedReasoningSource.mechanicalC(committed), command, declaration, catalog, core, humandirection, observation)
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
  private def _scope_response_c(route: SplitPhaseManagedScopeSemantic.Route, command: SplitPhaseAdvanceTarget,
    invocationmode: String, declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core, humandirection: Boolean, observation: () => Consequence[Unit]): Consequence[V.SplitPhaseAdvanceResult] = route match {
    case pending: SplitPhaseManagedScopeSemantic.Pending =>
      NativeWorkflowOperationOutput.splitScopeRequestC(pending.work)
    case _: SplitPhaseManagedScopeSemantic.Completed if humandirection =>
      _human_c(command, invocationmode, declaration, catalog, core, true, observation)
    case completed: SplitPhaseManagedScopeSemantic.Completed =>
      val decision = completed.source.completion.result.result.value.decision
      Consequence.serviceUnavailable("SplitPhase advance admitted " + decision.toString + " handoff scope result awaits its " +
        (if (decision == SplitPhaseScopeSemantic.Decision.Enriched) "whole-frontier mechanical projection" else "genuine human-direction") +
        " handler; no intermediate WAIT or Terminal was emitted")
  }

  private def _human_c(command: SplitPhaseAdvanceTarget, invocationmode: String,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core, publish: Boolean,
    observation: () => Consequence[Unit]): Consequence[V.SplitPhaseAdvanceResult] = {
    val params = ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin)
    for {
    _ <- if (publish) SplitPhaseManagedScopeProjectionCheckpoint.bindC(params.configuration, declaration, catalog)
      .flatMap(_.executeForCallC(command, core)).map(_ => ()) else Consequence.unit
    human <- SplitPhaseManagedHumanDirection.bindC(params, declaration, catalog)
    question <- human.advanceForCallC(command, core)
    wire <- question match {
      case Some(actual) => NativeWorkflowOperationOutput.splitScopeHumanC(actual, core)
      case None => for {
        reader <- SplitPhaseManagedScopePlanningCheckpoint.bindC(params.configuration, declaration, catalog)
        saved <- reader.executeForCallC(command, core)
        wire <- _frontier_c(SplitPhaseManagedPlanningFrontier.observed(saved.record.downstream,
              saved.record.next, saved.current, invocationmode), SplitPhaseRetainedReasoningSource.scopePlanningC(saved),
          command, declaration, catalog, core, observation)
      } yield wire
    }
    } yield wire
  }

  private def _answered_human_c(command: SplitPhaseAdvanceTarget, invocationmode: String,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core, observation: () => Consequence[Unit]): Consequence[V.SplitPhaseAdvanceResult] = for {
    answer <- SplitPhaseManagedHumanCompletion.observeAnswerForAdvanceC(command, declaration, catalog, core)
    wire <- answer.completion.result.result.value match {
      case _: SplitPhaseHumanDirectionAnswerContract.Answer.ProvideResolution => for {
        reader <- SplitPhaseManagedHumanResolutionCheckpoint.bindC(core.component.get.subsystem.get.configuration,
          declaration, catalog)
        saved <- reader.executeForCallC(command, core)
        wire <- _frontier_c(SplitPhaseManagedPlanningFrontier.observed(saved.record.downstream,
              saved.record.next, saved.current, invocationmode), SplitPhaseRetainedReasoningSource.humanResolutionC(saved),
          command, declaration, catalog, core, observation)
      } yield wire
      case _ => SplitPhaseHumanTerminal.advanceC(command, declaration, catalog, core, observation)
    }
  } yield wire

  /** The selected observed calculation is consumed directly by the issuer.
    * Other frontiers still require their own handlers.
    */
  private def _frontier_c(selected: SplitPhaseManagedPlanningFrontier.Observed, source: => Consequence[SplitPhaseRetainedReasoningSource.Source],
    command: SplitPhaseAdvanceTarget,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core, observation: () => Consequence[Unit]): Consequence[V.SplitPhaseAdvanceResult] = R.dispositionC(selected.snapshot).flatMap {
      case R.Disposition.ReasoningRequired => for {
        selected <- source
        issuer <- SplitPhaseManagedReasoningIssue.bindC(
          ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin), declaration, catalog)
        work <- issuer.issueForCallC(selected, core)
        wire <- NativeWorkflowOperationOutput.splitReasoningRequestC(work)
      } yield wire
      case R.Disposition.Planned => _selected_c(command, selected, declaration, catalog, core, observation)
      case disposition => Consequence.serviceUnavailable("SplitPhase advance retained " + disposition.toString +
        " frontier awaits its installed continuation handler; no intermediate WAIT or Terminal was emitted")
    }
  private def _selected_c(command: SplitPhaseAdvanceTarget, source: SplitPhaseManagedPlanningFrontier.Observed,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    core: ActionCall.Core, observation: () => Consequence[Unit]): Consequence[V.SplitPhaseAdvanceResult] =
      source.invocationMode match {
        case "PREVIEW" => SplitPhaseManagedSelectedPreview.advanceObservedC(command, source, declaration, catalog, core, observation)
        case "APPLY" => SplitPhaseManagedSelectedApply.advanceObservedC(command, source, declaration, catalog, core, observation)
        case _ => Consequence.stateConflict("SplitPhase actual original invocation mode is unsupported")
      }
}
