package org.simplemodeling.textus.smworkflow.admission

import cats.syntax.all.*
import io.circe.Json
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore, DataStoreConditionalTransitionFailure}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedReasoningWorkIssue as I,
  SplitPhaseExplicitReasoningSemantic as S, SplitPhaseExplicitReasoningSemanticJson as J,
  SplitPhaseTypedReasoningCompletion as P, SplitPhaseRetainedReasoningSource as D,
  SplitPhasePlanningCheckpointPreparation as K, SplitPhasePlanningCheckpointPublication as U,
  SplitPhasePlanningSnapshotJsonV1 as SJ}

/** Canonical result Operation: the participant returns only the assigned
  * Judgment. Native completion and the deterministic successor are one UoW.
  * Metadata alone survives calls; neither Core nor a provider/pool is retained.
  * @since   Oct.  6, 2026
  * @version Oct. 12, 2026
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
  def pendingForAdvanceC(current: InstanceRecord,
    declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core):
      Consequence[WorkflowInteraction[S.Request, Nothing]] = for {
    identity <- current.suspension.map(value => Consequence.success(
      ContinuationIdentity(value.continuationIdentity.value)))
      .getOrElse(_invalid("pending query has no selected native suspension"))
    work <- _work_c(identity, declaration, catalog, core)
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
      o.name == I.completionOperation.operation).map(Consequence.success)
      .getOrElse(_invalid("reasoning result Operation is absent"))
    declared <- _operation_c(None, op, declaration, catalog, native)
  } yield CallScopedOperationBinding.wrapResult(declared, (core, result) =>
    _native_c(core, declaration, catalog).flatMap(native =>
      _operation_c(Some(core), op, declaration, catalog, native, Some(result))))

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
        current <- CallScopedWorkflowInstances.managed(declaration.configuration, catalog.definition,
          declaration.instances, declaration.continuations)
          .loadForCallC(declaration.configuration, work.handle.instanceIdentity, core)
          .flatMap(_.map(Consequence.success).getOrElse(_invalid("native reasoning instance is absent")))
        // Select the business frontier named by the actual completion. This is
        // consumed to issue the following Human task, not compared with a
        // reconstructed instance or used to restore an interrupted execution.
        history = current.history.takeWhile(_.revision.value <= actual.completion.instanceRevision.value)
        frontier <- history.lastOption.map(Consequence.success)
          .getOrElse(_invalid("reasoning completion frontier is absent"))
        next = current.copy(revision = frontier.revision, lifecycle = frontier.lifecycle,
          currentProgression = frontier.currentProgression, correlation = frontier.correlation,
          causation = frontier.causation, derivedCompositeOccurrence = frontier.derivedCompositeOccurrence,
          committedPredecessor = frontier.committedPredecessor, suspension = frontier.suspension, history = history)
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
        saved <- json.hcursor.downField("downstream").focus match {
          case Some(value) if value.isNull => Consequence.success(None)
          case Some(value) if value.asString.nonEmpty => SJ.decodeC(value.asString.get).map(Some(_))
          case _ => _invalid("successor planning data is absent or malformed")
        }
      } yield Some(new Observed(source, work, request, actual.completion, saved, next, current))
    }
  } yield observed

  private def _operation_c(core: Option[ActionCall.Core], op: GeneratedProvidedApiAbi.Operation,
    declaration: SplitPhaseManagedStartAssembly.Declaration, catalog: SplitPhaseRuntimeCatalog.Catalog,
    native: WorkflowManagedCompletion[S.Judgment], result: Option[ContinuationResult[NativeWorkflowResultInput.Value]] = None): Consequence[spec.OperationDefinition] = {
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
      def decodeC(request: Request) = result.map(NativeWorkflowResultInput.typedC(_, J.resultCodec))
        .getOrElse(_invalid("reasoning completion has no resolved semantic result"))
      def encodeC(resume: ContinuationRuntime.Resume) = _invalid("acknowledgement requires committed readback")
      override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, submitted: ContinuationResult[S.Judgment],
        completed: Option[InstanceRecord]): Consequence[OperationResponse] = {
        val plan = selected
        selected = None
        // The owning CNCF completion has already committed and returned its
        // actual instance. A second read or copied Plan cannot certify it.
        (plan, completed) match {
          case (Some(value), Some(actual)) =>
            Consequence.success(NativeWorkflowOperationOutput.response(NativeWorkflowOperationOutput.accepted(value.submitted, actual)))
          case _ => _uncertain("completion response lacks its selected disposition or actual committed instance")
        }
      }
    }
    val closing = new WorkflowCompletionServiceOperation.ClosingProgram[S.Judgment] {
      def selectC(submitted: ContinuationResult[S.Judgment], current: InstanceRecord) = core match {
        case None => _invalid("closing requires the actual result Operation")
        case Some(call) => for {
          work <- _work_c(submitted.continuationId, declaration, catalog, call)
          source <- _source_c(work, declaration, catalog, call)
          pending <- P.pendingC(source, current, work)
          plan <- P.admitC(pending, submitted)
          targets <- U.bindTargetsC(call.component.get.subsystem.get.configuration, declaration)
          row <- _row_c(plan, targets)
          storage <- ComponentRecordStorage.bindForCallC(call, targets.storeName, row.collection, DataStore.StringEntryId(row.identity))
          staged <- storage.stageCreateC(row.payload)
          frontier <- SplitPhaseExecutionFrontierDataStorePort.bindForCallC(call, declaration)
          nextFrontier <- frontier.stageCreateForCallC(plan.next.identity, plan.next.revision,
            SplitPhaseExecutionFrontier.Value.ReasoningCompletion(submitted.continuationId), call)
          program = staged.flatMap(_ => nextFrontier).flatMap(_ => ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
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
    WorkflowCompletionServiceOperation.bindC(SplitPhaseDefinitionBinding.providedDefinition, op, SmWorkflowComponent.SplitPhaseServiceService.SubmitReasoningResultOperation.specification.request,
      SmWorkflowComponent.SplitPhaseServiceService.SubmitReasoningResultOperation.specification.response, codec, ContinuationRuntime.empty, issued, currentinstances, declaration.configuration,
      () => throw new IllegalStateException("Reasoning completion requires canonical managed UoW"), Some(closing), Some(native),
      descriptiveAttributes = SmWorkflowComponent.SplitPhaseServiceService.SubmitReasoningResultOperation.specification.content.descriptiveAttributes)
  }

  /** The issued work supplies its actual pre-issue revision. The business
    * reference at that revision selects its source without a history scan.
    */
  private def _source_c(work: WorkflowInteraction[S.Request, Nothing], declaration: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog, core: ActionCall.Core): Consequence[D.Source] = for {
    command <- SplitPhaseReasoningInvocation.queryC(work)
    completioncall = core.action.request.service.contains(I.completionOperation.service) &&
      Set(I.completionOperation.operation, SplitPhaseReasoningHumanDirection.completionOperation.operation)
        .contains(core.action.request.operation)
    request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
    revision <- request.expectedRevision.value.toLongOption.map(value => Consequence.success(
      org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRevision(value - 1L)))
      .getOrElse(_invalid("issued reasoning revision is not numeric"))
    frontierReader <- SplitPhaseExecutionFrontierDataStorePort.bindForCallC(core, declaration)
    before <- frontierReader.loadForCallC(work.handle.instanceIdentity, revision, core).flatMap(
      _.map(Consequence.success).getOrElse(_invalid("issued reasoning business source is absent")))
    host = core.component.get.subsystem.get.configuration
    source <- before match {
      case _: SplitPhaseExecutionFrontier.Value.HumanResolution => for {
        reader <- SplitPhaseManagedHumanResolutionCheckpoint.bindC(host, declaration, catalog)
        observed <- (if (completioncall) reader.loadForReasoningCompletionC(core) else reader.loadForCallC(command, core))
          .flatMap(_.map(Consequence.success).getOrElse(_invalid("selected Human successor is absent")))
        source <- D.humanResolutionC(observed)
      } yield source
      case _: SplitPhaseExecutionFrontier.Value.ScopePlanning => for {
        reader <- SplitPhaseManagedScopePlanningCheckpoint.bindC(host, declaration, catalog)
        observed <- (if (completioncall) reader.loadForReasoningCompletionC(core) else reader.loadForCallC(command, core))
          .flatMap(_.map(Consequence.success).getOrElse(_invalid("selected scope successor is absent")))
        source <- D.scopePlanningC(observed)
      } yield source
      case SplitPhaseExecutionFrontier.Value.MechanicalPlanning(reference) => for {
        reader <- SplitPhaseManagedPlanningCheckpoint.bindC(host, declaration, catalog)
        observed <- reader.loadMechanicalForReferenceC(command, reference, core)
        source <- D.mechanicalC(observed)
      } yield source
      case SplitPhaseExecutionFrontier.Value.PartitionCompletion(identity) =>
        SplitPhaseManagedTypedPartitionCompletion.observeForCallC(identity, declaration, catalog, core).flatMap {
          case Some(value) => D.partitionCompletionC(value)
          case None => _invalid("selected partition completion is absent")
        }
      case _ => _invalid("issued reasoning reference has no reasoning source")
    }
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
      "downstream" -> snapshot).noSpaces
    row = U.Row(targets.publication, key, org.goldenport.record.Record.data(_field -> wire))
  } yield row

  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase managed reasoning completion " + reason)
  private def _uncertain[A](reason: String): Consequence[A] = DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
}
