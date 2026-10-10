package org.simplemodeling.textus.smworkflow.admission

import cats.syntax.all.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore, DataStoreConditionalTransitionFailure}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedReasoningWorkIssue as I,
  SplitPhaseExplicitReasoningSemantic as S, SplitPhaseExplicitReasoningSemanticJson as J,
  SplitPhaseTypedReasoningCompletion as P, SplitPhaseRetainedReasoningSource as D,
  SplitPhasePlanningCheckpointPreparation as K, SplitPhasePlanningCheckpointPublication as U,
  SplitPhasePlanningSnapshotJsonV1 as SJ}

/** Canonical result Operation: the participant returns only the assigned
  * Judgment. Native completion and the deterministic successor are one UoW.
  * Metadata alone survives calls; neither Core nor a provider/pool is retained.
  * @since   Oct.  6, 2026
  * @version Oct. 11, 2026
  */
private[smworkflow] object SplitPhaseManagedReasoningCompletion {
  final class Observed private[SplitPhaseManagedReasoningCompletion] (
    val source: D.Source, val work: WorkflowInteraction[S.Request, Nothing],
    val request: S.Request, val completion: WorkflowCompletionRecordJsonV1.Record[S.Judgment],
    val downstream: Option[SplitPhasePlanningSnapshot.Record],
    val next: InstanceRecord, val current: InstanceRecord)

  /** Advance reads the assigned native task, never reconstructs a fresh issue
    * from a suspended frontier or treats it as partition/scope work.
    */
  def pendingForAdvanceC(command: SplitPhaseAdvanceCommand, current: InstanceRecord,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core):
      Consequence[WorkflowInteraction[S.Request, Nothing]] = for {
    _ <- if (core != null && core.action != null && core.action.request != null &&
        core.action.request.service.contains(K.advanceOperation.service) &&
        core.action.request.operation == K.advanceOperation.operation) Consequence.unit
      else Consequence.securityPermissionDenied("Pending reasoning requires its actual advance Operation")
    identity <- current.suspension.map(value => Consequence.success(
      ContinuationIdentity(value.continuationIdentity.value)))
      .getOrElse(_invalid("pending query has no selected native suspension"))
    work <- _work_c(identity, declaration, catalog, core)
    owning <- SplitPhaseReasoningInvocation.queryC(work)
    _ <- if (owning == command) Consequence.unit else _invalid("pending work belongs to another query context")
    // Native storage resolves this saved issue. This query consumes its current
    // assignment, not a freshly reconstructed source, Context or presentation.
    _ <- work.current match {
      case order: WorkflowContinuation.WorkOrder[?] if
          order.request.expectedRevision.value == current.revision.value.toString => Consequence.unit
      case _ => _invalid("saved reasoning work is not the current native assignment")
    }
  } yield work

  def bindHostC(params: ComponentCreate, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[spec.OperationDefinition] = for {
    storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
      Vector(catalog.definition), declaration.instances, declaration.continuations)
    native <- storage.bindCompletionC(I.completionOperation, J.requestCodec, J.resultCodec, I.closingType,
      (_, _) => Consequence.success(Vector.empty))
    _ <- U.bindTargetsC(params.configuration, declaration)
    provided = SplitPhaseDefinitionBinding.providedDefinition
    op <- provided.providedOperations.find(o => o.service == I.completionOperation.service &&
      o.name == I.completionOperation.operation && o.inputType.contains(J.resultCodec.typeIdentity) &&
      o.resultType.contains(I.closingType.value)).map(Consequence.success)
      .getOrElse(_invalid("generated result Operation differs from reasoning codecs"))
    declared <- _operation_c(None, op, declaration, catalog, native)
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
              submitted <- WorkflowJsonCommandRequest.commandArguments(core.action.request) match {
                case List(Argument("arg1", wire: String, _)) => WorkflowResultJsonV1.decodeC(wire, J.resultCodec)
                case _ => _invalid("result requires one typed frame")
              }
              _ <- SplitPhaseReasoningInvocation.completionQueryC(core, declaration, catalog)
              retained <- observeForCallC(submitted.continuationId, declaration, catalog, core)
              response <- retained match {
                case Some(actual) if actual.completion.result == submitted =>
                  _ack_c(actual.completion.result, actual.completion.instanceRevision.value.toString).map(OperationResponse.Scalar(_))
                case Some(_) => _invalid("submitted result differs from the already committed answer")
                case None => for {
                  native <- _native_c(core, declaration, catalog)
                  bound <- _operation_c(Some(core), op, declaration, catalog, native)
                  action <- bound.createOperationRequest(core.action.request).flatMap {
                    case value: CommandAction => Consequence.success(value)
                    case _ => _invalid("native result Operation is not a command")
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
        case _ => _invalid("reasoning result declaration is not a command")
      }
  }

  /** Authoritative retained observation. Does not resubmit the result, run AI,
    * regenerate earlier documents, or allocate a new planning version.
    */
  def observeForCallC(identity: ContinuationIdentity, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[Option[Observed]] = for {
    work <- _work_c(identity, declaration, catalog, core)
    native <- _native_c(core, declaration, catalog)
    retained <- native.loadRetainedC(identity)(using core.executionContext)
    observed <- retained match {
      case None => Consequence.success(None)
      case Some(actual) => for {
        source <- _source_c(work, declaration, catalog, core)
        next <- _completed_c(source.current, actual.completion.instanceRevision)
        request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
        targets <- U.bindTargetsC(core.component.get.subsystem.get.configuration, declaration)
        key <- _key_c(actual.completion.result)
        storage <- ComponentRecordStorage.bindForCallC(core, targets.storeName, targets.publication, DataStore.StringEntryId(key))
        raw <- storage.loadC().flatMap {
          case Some(value) => Consequence.success(value)
          case None => _invalid("completed reasoning has no committed successor")
        }
        wire <- raw.getString(_field).map(Consequence.success).getOrElse(_invalid("successor frame is absent"))
        _ <- if (wire.startsWith(_prefix)) Consequence.unit else _invalid("successor storage frame is unsupported")
        json <- io.circe.parser.parse(wire.substring(_prefix.length)).fold(_ => _invalid("successor frame is malformed"), Consequence.success)
        _ <- if (json.hcursor.get[String]("schema").contains("sm-workflow.reasoning-successor.v1") &&
            json.hcursor.get[Vector[String]]("result").contains(Vector(actual.completion.result.resultReference.identity,
              actual.completion.result.resultReference.revision))) Consequence.unit else _invalid("successor result assignment differs")
        saved <- json.hcursor.downField("downstream").focus match {
          case Some(value) if value.isNull => Consequence.success(None)
          case Some(value) if value.asString.nonEmpty => SJ.decodeC(value.asString.get).map(Some(_))
          case _ => _invalid("successor planning data is absent or malformed")
        }
      } yield Some(new Observed(source, work, request, actual.completion, saved, next, source.current))
    }
  } yield observed

  private def _operation_c(core: Option[ActionCall.Core], op: GeneratedProvidedApiAbi.Operation,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    native: WorkflowManagedCompletion[S.Judgment]): Consequence[spec.OperationDefinition] = {
    var selected = Option.empty[P.Plan]
    val issued = new IssuedWorkOrderPersistence[S.Request] {
      def putIfAbsentC(value: WorkflowInteraction[S.Request, Nothing]) = _invalid("completion cannot issue work")
      def loadC(identity: ContinuationIdentity) = core match {
        case Some(call) => _work_c(identity, declaration, catalog, call).map(Some(_))
        case None => _invalid("completion requires the actual result Operation")
      }
    }
    val codec = new WorkflowCompletionServiceOperation.Codec[S.Judgment] {
      val resultTypeIdentity = J.resultCodec.typeIdentity
      def decodeC(request: Request) = WorkflowJsonCommandRequest.commandArguments(request) match {
        case List(Argument("arg1", wire: String, _)) => WorkflowResultJsonV1.decodeC(wire, J.resultCodec)
        case _ => _invalid("completion requires one typed JSON result")
      }
      def encodeC(resume: ContinuationRuntime.Resume) = _invalid("acknowledgement requires committed readback")
      override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, submitted: ContinuationResult[S.Judgment],
        completed: Option[InstanceRecord]): Consequence[OperationResponse] = {
        val plan = selected
        selected = None
        // The owning CNCF completion has already committed and returned its
        // actual instance. A second read or copied Plan cannot certify it.
        (plan, completed) match {
          case (Some(value), Some(actual)) =>
            _ack_c(value.submitted, actual.revision.value.toString).map(OperationResponse.Scalar(_))
          case _ => _uncertain("completion response lacks its selected disposition or actual committed instance")
        }
      }
    }
    val closing = new WorkflowCompletionServiceOperation.ClosingProgram[S.Judgment] {
      def selectC(submitted: ContinuationResult[S.Judgment], current: InstanceRecord) = core match {
        case None => _invalid("closing requires the actual result Operation")
        case Some(call) => for {
          command <- SplitPhaseReasoningInvocation.completionQueryC(call, declaration, catalog)
          work <- _work_c(submitted.continuationId, declaration, catalog, call)
          owning <- SplitPhaseReasoningInvocation.queryC(work)
          _ <- if (command == owning) Consequence.unit else _invalid("completion query crosses its native issued work")
          source <- _source_c(work, declaration, catalog, call)
          pending <- P.pendingC(source, current, work)
          plan <- P.admitC(pending, submitted)
          targets <- U.bindTargetsC(call.component.get.subsystem.get.configuration, declaration)
          row <- _row_c(plan, targets)
          storage <- ComponentRecordStorage.bindForCallC(call, targets.storeName, row.collection, DataStore.StringEntryId(row.identity))
          staged <- storage.stageCreateC(row.payload)
          program = staged.flatMap(_ => ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Completed(plan.closing)))
        } yield {
          selected = Some(plan)
          WorkflowCompletionServiceOperation.SelectedClosing(program, Some(plan.next))
        }
      }
    }
    val instances = CallScopedWorkflowInstances.managed(declaration.configuration, catalog.definition,
      declaration.instances, declaration.continuations)
    val currentinstances = core.fold[WorkflowInstancePersistence](instances)(CallScopedWorkflowInstances.forCall(instances, _))
    WorkflowCompletionServiceOperation.bindC(SplitPhaseDefinitionBinding.providedDefinition, op, WorkflowJsonCommandRequest.definition,
      spec.ResponseDefinition.void, codec, ContinuationRuntime.empty, issued, currentinstances, declaration.configuration,
      () => throw new IllegalStateException("Reasoning completion requires canonical managed UoW"), Some(closing), Some(native),
      descriptiveAttributes = SmWorkflowComponent.SplitPhaseServiceService.SubmitReasoningResultOperation.specification.content.descriptiveAttributes)
  }

  /** The actual native pre-issue history selects exactly one original reader.
    * Purpose names are only routing, not proof: each reader checks its complete
    * canonical receipt and original input; errors do not fall back to another.
    */
  private def _source_c(work: WorkflowInteraction[S.Request, Nothing], declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[D.Source] = for {
    _ <- SplitPhaseReasoningInvocation.actorC(core, None)
    command <- SplitPhaseReasoningInvocation.queryC(work)
    completioncall = core.action.request.service.contains(I.completionOperation.service) &&
      Set(I.completionOperation.operation, SplitPhaseReasoningHumanDirection.completionOperation.operation)
        .contains(core.action.request.operation)
    owning <- if (completioncall) SplitPhaseReasoningInvocation.completionQueryC(core, declaration, catalog)
      else if (core.action.request.service.contains(K.advanceOperation.service) && core.action.request.operation == K.advanceOperation.operation)
        WorkflowJsonCommandRequest.commandArguments(core.action.request) match {
          case List(Argument("arg1", wire: String, _)) => SplitPhaseAdvanceCommandJsonV1.decodeC(wire)
          case _ => _invalid("advance query is absent")
        }
      else Consequence.securityPermissionDenied("Reasoning observation requires advance or its exact result Operation")
    _ <- if (owning == command) Consequence.unit else _invalid("owning query crosses original issued context")
    current <- CallScopedWorkflowInstances.managed(declaration.configuration, catalog.definition, declaration.instances, declaration.continuations)
      .loadForCallC(declaration.configuration, work.handle.instanceIdentity, core).flatMap {
        case Some(value) => value.validateC
        case None => _invalid("canonical reasoning instance is absent")
      }
    identity = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.continuationId
    issueindex = current.history.indexWhere(_.suspension.exists(_.continuationIdentity.value == identity.value))
    _ <- if (issueindex > 0) Consequence.unit else _invalid("native issued reasoning prefix is absent")
    before = current.history(issueindex - 1)
    host = core.component.get.subsystem.get.configuration
    source <- if (before.causation.value.startsWith("human-resolution:")) for {
      reader <- SplitPhaseManagedHumanResolutionCheckpoint.bindC(host, declaration, catalog)
      observed <- (if (completioncall) reader.loadForReasoningCompletionC(core) else reader.loadForCallC(command, core))
        .flatMap(_.map(Consequence.success).getOrElse(_invalid("selected Human successor is absent")))
      source <- D.humanResolutionC(observed)
    } yield source
    else if (before.causation.value.startsWith("scope-planning:")) for {
      reader <- SplitPhaseManagedScopePlanningCheckpoint.bindC(host, declaration, catalog)
      observed <- (if (completioncall) reader.loadForReasoningCompletionC(core) else reader.loadForCallC(command, core))
        .flatMap(_.map(Consequence.success).getOrElse(_invalid("selected scope successor is absent")))
      source <- D.scopePlanningC(observed)
    } yield source
    else for {
      reads <- SplitPhasePlanningCheckpointDataStorePort.bindC(host, declaration, catalog)
      mapping <- reads.loadMappingForCallC(command, core).flatMap {
        case Some((_, saved)) => Consequence.success(saved)
        case _ => _invalid("selected original mapping is absent")
      }
      reader <- SplitPhaseManagedPlanningCheckpoint.bindC(host, declaration, catalog)
      source <- mapping.origin match {
        case _: K.Origin.Mechanical => (if (completioncall) reader.loadMechanicalForReasoningCompletionC(core)
          else reader.loadMechanicalForCallC(command, core)).flatMap {
            case Some(value) => D.mechanicalC(value)
            case None => _invalid("selected mechanical receipt is absent")
          }
        case _: K.Origin.PartitionCompletion => (if (completioncall) reader.loadPartitionCompletionForReasoningCompletionC(core)
          else reader.loadPartitionCompletionForCallC(command, core)).flatMap {
            case Some(value) => D.partitionCompletionC(value)
            case None => _invalid("selected partition completion is absent")
          }
      }
    } yield source
    _ <- SplitPhaseReasoningInvocation.actorC(core, Some(source.checkpoint.originalStart.command.participantIdentity))
  } yield source

  private def _storage_c(core: ActionCall.Core, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[WorkflowComponentStorage] = for {
    _ <- SplitPhaseReasoningInvocation.actorC(core, None)
    _ <- if (core.action.request.service.contains(I.completionOperation.service) &&
        Set(K.advanceOperation.operation, I.completionOperation.operation,
          SplitPhaseReasoningHumanDirection.completionOperation.operation).contains(core.action.request.operation)) Consequence.unit
      else Consequence.securityPermissionDenied("Reasoning storage observation requires its owning advance or result Operation")
    storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(catalog.definition), declaration.instances, declaration.continuations)
  } yield storage
  private def _work_c(identity: ContinuationIdentity, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[WorkflowInteraction[S.Request, Nothing]] = for {
    storage <- _storage_c(core, declaration, catalog)
    native <- storage.bindIssuedC(I.completionOperation, J.requestCodec)
    work <- native.loadC(identity)(using core.executionContext).flatMap {
      case Some(value) => Consequence.success(value)
      case None => _invalid("actual issued reasoning work is absent")
    }
  } yield work
  private def _native_c(core: ActionCall.Core, declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[WorkflowManagedCompletion[S.Judgment]] =
    _storage_c(core, declaration, catalog).flatMap(_.bindCompletionC(I.completionOperation, J.requestCodec,
      J.resultCodec, I.closingType, (_, _) => Consequence.success(Vector.empty)))

  private val _field = "splitPhaseReasoningSuccessorPayload"
  private val _prefix = "sm-workflow.reasoning-successor.v1:"
  private def _ack_c(submitted: ContinuationResult[S.Judgment], revision: String): Consequence[String] =
    WorkflowWaitJsonV1.encodeC(WorkflowInteraction[Nothing, Nothing](submitted.handle,
      WorkflowContinuation.Wait(StateMachineRevision(revision), submitted.contextSnapshot,
        MinimalPresentation("ReasoningResultAdmitted", "Assigned reasoning is saved; Workflow owns the next planning or Human action."))))
  private def _completed_c(current: InstanceRecord, revision: org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRevision):
      Consequence[InstanceRecord] =
    current.history.find(_.revision == revision).map { entry =>
      current.copy(revision = entry.revision, lifecycle = entry.lifecycle, currentProgression = entry.currentProgression,
        correlation = entry.correlation, causation = entry.causation, derivedCompositeOccurrence = entry.derivedCompositeOccurrence,
        committedPredecessor = entry.committedPredecessor, suspension = entry.suspension,
        history = current.history.takeWhile(_.revision.value <= entry.revision.value))
    }.map(Consequence.success).getOrElse(_invalid("native completed revision is absent from actual history"))
  private def _key_c(submitted: ContinuationResult[S.Judgment]): Consequence[String] =
    WorkflowMechanicalTransition.keyC(WorkflowMechanicalTransition.Reference(submitted.continuationId.value,
      submitted.resultReference.revision)).map("reasoning-successor:" + _)
  private def _row_c(plan: P.Plan, targets: U.Targets): Consequence[U.Row] = for {
    key <- _key_c(plan.submitted)
    snapshot <- plan.downstream match {
      case Some(value) => SJ.encodeC(value).map(io.circe.Json.fromString)
      case None => Consequence.success(io.circe.Json.Null)
    }
    // The shared SQL provider decodes a bare object-valued text cell as a
    // Record. Use the same versioned string-frame convention as other saved
    // SplitPhase payloads, without changing provider/readback semantics.
    wire = _prefix + io.circe.Json.obj("schema" -> io.circe.Json.fromString("sm-workflow.reasoning-successor.v1"),
      "result" -> io.circe.Json.arr(io.circe.Json.fromString(plan.submitted.resultReference.identity),
        io.circe.Json.fromString(plan.submitted.resultReference.revision)), "downstream" -> snapshot).noSpaces
    row = U.Row(targets.publication, key, org.goldenport.record.Record.data(_field -> wire))
  } yield row

  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase managed reasoning completion " + reason)
  private def _uncertain[A](reason: String): Consequence[A] = DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
}
