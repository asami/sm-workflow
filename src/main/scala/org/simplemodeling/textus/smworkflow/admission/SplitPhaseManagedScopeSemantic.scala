package org.simplemodeling.textus.smworkflow.admission

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
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseAdvanceCommand, SplitPhasePartitionContextReference}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseScopeSemantic as S, SplitPhaseScopeSemanticJson as J,
  SplitPhaseTypedScopeWorkIssue as I, SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningCheckpointJsonV1 as KJ, SplitPhasePlanningCheckpointObservation as O,
  SplitPhasePlanningCheckpointDataStorePort as D, SplitPhasePlanningSnapshot as R}

/** Actual generated scope issue/completion. Only metadata is retained; native
  * transitions borrow the owning call's canonical Subsystem storage.
  *
  * @since Oct.  4, 2026
  * @version Oct.  7, 2026
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
    def routeForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core): Consequence[Route] = for {
      _ <- _actor_c(core, None)
      _ <- if (core.action.request.operation == K.advanceOperation.operation &&
          core.action.request.service.contains(K.advanceOperation.service)) Consequence.unit
        else _invalid("scope routing requires its actual advance Operation")
      owning <- WorkflowJsonCommandRequest.commandArguments(core.action.request) match {
        case List(Argument("arg1", wire: String, _)) => SplitPhaseAdvanceCommandJsonV1.decodeC(wire)
        case _ => _invalid("scope routing has no owning advance command")
      }
      _ <- if (owning == command) Consequence.unit else _invalid("scope routing command differs")
      reads <- D.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)
      mapping <- reads.loadMappingForCallC(command, core)
      routed <- mapping match {
        case Some((_, saved)) if saved.snapshot.projection.exists {
            case need: R.ProjectionView.Requirements => need.candidates.exists(_.isInstanceOf[R.CandidateProjectionView.SemanticView])
            case _ => false
          } => for {
          source <- _source_c(command, declaration, catalog, core)
          plan <- I.prepareC(source)
          published <- loadForCallC(I.identity(source), core)
          result <- published match {
            case Some(work) => for {
              _ <- if (work == plan.work && source.current.history.take(plan.next.history.size) == plan.next.history)
                Consequence.unit else _invalid("retained scope work or native issue prefix differs")
              result <- source.current.suspension match {
                case Some(_) if SplitPhaseDefinitionBinding.sameRecord(source.current, plan.next) =>
                  Consequence.success[Route](new Pending(work))
                case Some(_) => _invalid("scope issue is not the exact current suspension")
                case None => for {
                  actual <- _completed_source_c(plan, declaration, catalog, core).flatMap {
                    case Some(value) => Consequence.success(value)
                    case None => _invalid("unsuspended scope issue has no actual retained completion")
                  }
                } yield (new Completed(actual): Route)
              }
            } yield result
            case None if SplitPhaseDefinitionBinding.sameRecord(source.current, source.predecessor) =>
              _with_work_c(core) { (native, issued, instances) =>
                given ExecutionContext = core.executionContext
                instances.load(declaration.configuration, source.current.identity).flatMap {
                  case Some(current) if SplitPhaseDefinitionBinding.sameRecord(current, source.current) =>
                    native.suspendC(plan.intent, plan.work) match {
                      case Consequence.Success(committed) if SplitPhaseDefinitionBinding.sameRecord(committed.instance, plan.next) =>
                        issued.loadC(I.identity(source)) match {
                          case Consequence.Success(Some(actual)) if actual == plan.work =>
                            Consequence.success[Route](new Pending(actual))
                          case _ => _uncertain("scope committed issue readback differs; observe without retry")
                        }
                      case _ => _uncertain("scope suspension commit is not established; observe without retry")
                    }
                  case _ => _invalid("scope native predecessor differs at issue")
                }
              }
            case None => _invalid("advanced scope history lacks its actual published issue")
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
    def prepareProjectionForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core):
        Consequence[PreparedProjection] = for {
      completed <- completedForCallC(command, core)
      original <- SplitPhaseExplicitPlanningAdvance.restoreCompletedScopeC(completed)
      outcome <- SplitPhaseExplicitCandidateProjection.applyScopeC(original, completed.source.record, completed.admitted)
    } yield new PreparedProjection(completed, original, outcome)

    def completedForCallC(command: SplitPhaseAdvanceCommand, core: ActionCall.Core): Consequence[CompletedSource] = for {
      _ <- _actor_c(core, None)
      owning <- _completed_command_c(core, declaration, catalog)
      _ <- if (owning == command) Consequence.unit else _invalid("completed scope selection command differs")
      source <- _source_c(command, declaration, catalog, core)
      plan <- I.prepareC(source)
      issued <- loadForCallC(I.identity(source), core)
      _ <- if (issued.contains(plan.work) && source.current.history.take(plan.next.history.size) == plan.next.history)
        Consequence.unit else _invalid("completed scope retained issue or complete native prefix differs")
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
    required <- SplitPhaseDefinitionBinding.scopeSemanticOperationC
    _ <- if (required.inputType.exists(_.value == J.requestCodec.typeIdentity) &&
        required.resultType.exists(_.value == J.resultCodec.typeIdentity)) Consequence.unit
      else _invalid("scope codecs differ from generated required SPI")
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
      operation.service == I.completionOperation.service && operation.name == I.completionOperation.operation &&
        operation.inputType.contains(J.resultCodec.typeIdentity) && operation.resultType.contains(I.closingType.value))
      .map(Consequence.success).getOrElse(_invalid("scope completion differs from its generated ABI"))
    declared <- _operation_c(None, op, port, declaration, catalog, native)
  } yield new spec.OperationDefinition {
    val specification = declared.specification
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      declared.createOperationRequest(request).flatMap {
        case command: CommandAction => Consequence.success(new CommandAction {
          val request = command.request
          override def commandExecutionMode = command.commandExecutionMode
          def createCall(capsule: ActionCall.Core): ActionCall = new ProcedureActionCall {
            val core = capsule
            private var delegate = Option.empty[ActionCall]
            def execute(): Consequence[OperationResponse] = for {
              _ <- _actor_c(core, None)
              submitted <- WorkflowJsonCommandRequest.commandArguments(core.action.request) match {
                case List(Argument("arg1", wire: String, _)) => WorkflowResultJsonV1.decodeC(wire, J.resultCodec)
                case _ => _invalid("scope completion requires one canonical typed result frame")
              }
              work <- port.loadForCallC(submitted.continuationId, core).flatMap {
                case Some(value) => Consequence.success(value)
                case None => _invalid("scope completion has no actual issued work")
              }
              input <- _request_c(work)
              source <- _source_c(_command(input, work.handle.instanceIdentity.value), declaration, catalog, core)
              plan <- I.prepareC(source)
              _ <- if (work == plan.work) Consequence.unit else _invalid("scope retained issue differs from its original checkpoint")
              retained <- _completed_source_c(plan, declaration, catalog, core)
              response <- retained match {
                // A historical acknowledgement is a read, not another adjacent
                // completion. Preserve the original revision even after advance.
                case Some(actual) if actual.completion.result == submitted =>
                  _ack_c(plan, submitted, actual.next)
                case Some(_) => _invalid("scope result differs from its already committed completion")
                case None => for {
                  native <- _native_c(core, declaration, catalog)
                  bound <- _operation_c(Some(core), op, port, declaration, catalog, native)
                  action <- bound.createOperationRequest(core.action.request).flatMap {
                    case value: CommandAction => Consequence.success(value)
                    case _ => _invalid("scope completion is not a canonical command")
                  }
                  call = action.createCall(core.copy(action = action))
                  response <- { delegate = Some(call); call.execute() }
                } yield response
              }
            } yield response
            override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
              val actual = delegate
              delegate = None
              actual.fold(Consequence.success(response))(_.afterCommitResponseC(response))
            }
          }
        })
        case _ => _invalid("scope completion declaration is not a command")
      }
  }

  private def _ack_c(plan: I.Plan, submitted: ContinuationResult[S.Judgment], next: InstanceRecord):
      Consequence[OperationResponse] =
    WorkflowWaitJsonV1.encodeC(WorkflowInteraction[Nothing, Nothing](plan.work.handle,
      WorkflowContinuation.Wait(StateMachineRevision(next.revision.value.toString), submitted.contextSnapshot,
        MinimalPresentation("HandoffScopeResultAdmitted", "Only the assigned scope reply is committed; planning/application remain unfinished."))))
      .map(OperationResponse.Scalar(_))

  private def _operation_c(core: Option[ActionCall.Core], op: GeneratedProvidedApiAbi.Operation, port: Port,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    native: WorkflowManagedCompletion[S.Judgment]): Consequence[spec.OperationDefinition] = {
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
      def decodeC(request: Request) = WorkflowJsonCommandRequest.commandArguments(request) match {
        case List(Argument("arg1", wire: String, _)) => WorkflowResultJsonV1.decodeC(wire, J.resultCodec)
        case _ => _invalid("scope completion requires one canonical typed result frame")
      }
      def encodeC(resume: ContinuationRuntime.Resume) = _invalid("scope acknowledgement requires actual verified commit")
      override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, submitted: ContinuationResult[S.Judgment],
        completed: Option[InstanceRecord]): Consequence[OperationResponse] = {
        val captured = selected
        selected = None
        captured match {
          case Some((plan, result, next)) if submitted == result && completed.exists(actual =>
              SplitPhaseDefinitionBinding.sameRecord(actual, next)) && resume != null &&
              resume.result == StateMachineOperationResult(I.closingType, submitted.resultReference) && core.nonEmpty =>
            _source_c(_command(plan.request, submitted.handle.instanceIdentity.value), declaration, catalog, core.get)
              .flatMap(I.prepareC).flatMap { observed =>
                if (observed.work != plan.work) _invalid("scope committed source differs")
                else _observe_completion_c(observed, declaration, catalog, core.get)
              } match {
                case Consequence.Success(Some(actual)) if actual.result == submitted =>
                  _ack_c(plan, submitted, next)
                case _ => _uncertain("scope completion committed readback differs; observe without retry")
              }
          case _ => _uncertain("scope completion lacks its matching committed instance and closing result")
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
          _ <- if (work == plan.work && SplitPhaseDefinitionBinding.sameRecord(current, plan.next) &&
              SplitPhaseDefinitionBinding.sameRecord(source.current, current)) Consequence.unit
            else _invalid("scope completion differs from actual issue, current native suspension or checkpoint")
          next <- I.completedC(plan, submitted)
        } yield {
          selected = Some((plan, submitted, next))
          val result = StateMachineOperationResult(I.closingType, submitted.resultReference)
          val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Completed(result))
          WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next))
        }
      }
    }
    val currentinstances = core.fold[WorkflowInstancePersistence](instances)(CallScopedWorkflowInstances.forCall(instances, _))
    WorkflowCompletionServiceOperation.bindC(SplitPhaseDefinitionBinding.providedDefinition, op,
      WorkflowJsonCommandRequest.definition, spec.ResponseDefinition.void, codec, ContinuationRuntime.empty,
      issued, currentinstances, declaration.configuration,
      () => throw new IllegalStateException("Scope completion must use canonical managed UoW"), Some(closing), Some(native),
      descriptiveAttributes = SmWorkflowComponent.SplitPhaseServiceService.SubmitHandoffScopeResultOperation.specification.content.descriptiveAttributes)
  }

  /** Complete independent checkpoint/native provenance admission, even on
    * reopen after later history. No private planner constructors are invoked.
    */
  private def _source_c(command: SplitPhaseAdvanceCommand, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[Source] = for {
    _ <- _actor_c(core, None)
    reads <- D.bindC(core.component.get.subsystem.get.configuration, declaration, catalog)
    original <- reads.loadOriginalForCommandC(command, core)
    _ <- _actor_c(core, Some(original.start.command.participantIdentity))
    instances = CallScopedWorkflowInstances.managed(declaration.configuration, catalog.definition,
      declaration.instances, declaration.continuations)
    current <- instances.loadForCallC(declaration.configuration, original.start.instanceIdentity, core).flatMap {
      case Some(value) => value.validateC
      case None => _invalid("scope native current instance is absent")
    }
    _ <- O.verifyStartLineageC(original, current)
    _ <- O.verifyInvocationC(original)
    _ <- if (original.start.configuration == declaration.configuration && original.start.instanceIdentity.value == command.instanceIdentity &&
        original.context.reference.identity == command.context.identity && original.context.reference.revision == command.context.revision &&
        SplitPhaseDefinitionBinding.sameDefinition(current.definition, catalog.definition)) Consequence.unit
      else _invalid("scope original query, declaration or native definition differs")
    mapping <- reads.loadMappingForCallC(command, core).flatMap {
      case Some(value) => Consequence.success(value)
      case None => _invalid("scope immutable checkpoint index is absent")
    }
    (index, record) = mapping
    data <- reads.loadDataForCallC(index, record, original, core)
    predecessor <- _prefix_c(current, record.committedRevision)
    _ <- record.origin match {
      case K.Origin.Mechanical(anchor) => for {
        storage <- _storage_c(core, declaration, catalog)
        native <- storage.bindMechanicalC(K.advanceOperation, KJ.payloadCodec)
        actual <- native.loadRetainedC(anchor)(using core.executionContext).flatMap {
          case Some(value) => Consequence.success(value)
          case None => _invalid("scope original actual mechanical receipt is absent")
        }
        _ <- O.verifyMechanicalC(data, actual.receipt)
        _ <- if (SplitPhaseDefinitionBinding.sameRecord(actual.current, current) &&
            SplitPhaseDefinitionBinding.sameRecord(actual.receipt.next, predecessor)) Consequence.unit
          else _invalid("scope original mechanical native prefix differs")
      } yield ()
      case K.Origin.PartitionCompletion(identity, _) => for {
        purposes <- SplitPhasePartitionCompletionCheckpointDataStorePort.capturePurposesC(core.component.get.subsystem.get.configuration)
        actual <- SplitPhaseManagedTypedPartitionCompletion.observeForCallC(identity, declaration, catalog, reads.targets, purposes, core)
        _ <- actual match {
          case Some(value) if value.record.checkpoint.contains(record) && value.record.context == original.context &&
              value.record.start == original.start && SplitPhaseDefinitionBinding.sameRecord(value.current, current) &&
              SplitPhaseDefinitionBinding.sameRecord(value.record.next, predecessor) => Consequence.unit
          case _ => _invalid("scope original actual partition completion or full native prefix differs")
        }
      } yield ()
    }
  } yield new Source(record, predecessor, current)

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
        _ <- if (actual.completion.instanceRevision == projected.next.revision &&
            actual.completion.closingResult.contains(StateMachineOperationResult(I.closingType, actual.completion.result.resultReference)) &&
            plan.source.current.history.take(projected.next.history.size) == projected.next.history) Consequence.unit
          else _invalid("scope retained completion or full native committed prefix differs")
      } yield Some(new CompletedSource(plan.source, plan, actual.completion, projected.admitted, projected.next))
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
  private def _command(request: S.Request, instance: String): SplitPhaseAdvanceCommand = SplitPhaseAdvanceCommand(instance,
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

  /** Human completion observes the real issued Decision. It does not substitute
    * an advance request/Core or accept an independently supplied context query.
    */
  private def _completed_command_c(core: ActionCall.Core, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[SplitPhaseAdvanceCommand] = {
    val request = core.action.request
    WorkflowJsonCommandRequest.commandArguments(request) match {
      case List(Argument("arg1", wire: String, _)) if request.operation == K.advanceOperation.operation =>
        SplitPhaseAdvanceCommandJsonV1.decodeC(wire)
      case List(Argument("arg1", _: String, _)) if request.operation == SplitPhaseTypedReasoningWorkIssue.completionOperation.operation =>
        SplitPhaseReasoningInvocation.completionQueryC(core, declaration, catalog)
      case List(Argument("arg1", wire: String, _)) if request.operation == SplitPhaseNativeHumanDirection.completionOperation.operation =>
        WorkflowResultJsonV1.decodeC(wire, SplitPhaseHumanDirectionAnswerContract.payloadCodec).flatMap { submitted =>
        if (SplitPhaseManagedReasoningHumanCompletion.isReasoning(submitted.continuationId))
          SplitPhaseReasoningInvocation.completionQueryC(core, declaration, catalog)
        else for {
          storage <- _storage_c(core, declaration, catalog)
          decisions <- storage.bindDecisionC(SplitPhaseNativeHumanDirection.completionOperation, SplitPhaseNativeHumanDirection.inputCodec)
          question <- decisions.loadC(submitted.continuationId)(using core.executionContext).flatMap {
            case Some(value) => Consequence.success(value)
            case None => _invalid("Human completion has no actual issued Decision")
          }
          _ <- WorkflowProtocolV1.admitDecisionResultC(question, submitted).map(_ => ())
          input <- question.current match {
            case decision: WorkflowContinuation.Decision[?] if decision.request.input.exists(value =>
                value.typeIdentity == SplitPhaseNativeHumanDirection.inputCodec.typeIdentity &&
                value.value.isInstanceOf[SplitPhaseNativeHumanDirection.Input]) =>
              Consequence.success(decision.request.input.get.value.asInstanceOf[SplitPhaseNativeHumanDirection.Input])
            case _ => _invalid("Human completion has no complete issued input")
          }
          _ <- SplitPhaseNativeHumanDirection.frontierC(input)
        } yield SplitPhaseAdvanceCommand(question.handle.instanceIdentity.value,
          SplitPhasePartitionContextReference(input.context.identity, input.context.revision))
        }
      case _ => _invalid("completed scope selection requires its actual advance or Human completion command")
    }
  }
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
