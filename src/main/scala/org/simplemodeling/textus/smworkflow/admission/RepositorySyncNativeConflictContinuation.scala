package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request as ProtocolRequest}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import RepositorySyncManagedFetch as F
import RepositorySyncNativeConflictInput as I
import RepositorySyncConflictSemantic as S
import RepositorySyncNativeProgressStore as N

/** Paired native issue/completion. Durable proposals are deliberately not
  * file writes, validation acceptance or commit/push authority. No pool,
  * provider, execution-context lease or semantic routing is retained by host.
  * @since   Oct.  6, 2026
  * @version Oct.  9, 2026
  */
private[smworkflow] object RepositorySyncNativeConflictContinuation {
  val completionOperation = StateMachineOperationIdentity("RepositorySyncService", "submitRepositorySyncWorkResult")
  val closingType = StateMachineResultTypeReference("RepositorySyncConflictSubmissionResult")
  private final case class Source(session: F.Session, store: N.Store, conflicted: N.Stage, current: InstanceRecord)
  private final case class Plan(source: Source, input: I.Request,
    intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent, next: InstanceRecord,
    work: WorkflowInteraction[I.Request, Nothing])

  final class Port private[RepositorySyncNativeConflictContinuation] (
    declaration: F.Declaration, definition: DefinitionBinding, policy: I.Policy,
    acknowledgement: String => Consequence[Unit], validationPolicy: Option[RepositorySyncNativeMergeValidation.Policy],
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port], human: Option[RepositorySyncHumanConflict.Installation]) {

    private val humanPort = human.map(installation => new RepositorySyncHumanConflict.Port(declaration, definition, policy,
      installation, humanSourceC, acknowledgement, validationPolicy, review))
    def bindHumanCompletionC(params: ComponentCreate): Consequence[Option[spec.OperationDefinition]] = humanPort match {
      case Some(port) => port.bindCompletionC(params).map(Some(_))
      case None => Consequence.success(None)
    }

    def advanceC(identity: String, core: ActionCall.Core): Consequence[String] = for {
      source <- sourceC(identity, core)
      native <- source.session.storage.bindIssuedC(completionOperation, S.inputCodec)
      existing <- native.loadC(continuationId(source.conflicted))(using core.executionContext)
      plan <- existing match {
        case Some(work) => for {
          input <- inputC(work)
          selected <- prepareC(source, input)
        } yield selected.copy(work = work)
        case None => for {
          input <- I.captureC(source.store, source.conflicted, policy)
          selected <- prepareC(source, input)
          saved <- native.suspendC(selected.intent, selected.work)(using core.executionContext)
        } yield selected
      }
      wire <- if (same(source.current, plan.next) || same(source.current, source.conflicted.record))
        WorkflowWorkOrderJsonV1.encodeC(plan.work, S.inputCodec)
        else for {
          completion <- nativeCompletionC(source.session.storage)
          actual <- completion.loadRetainedC(continuationId(source.conflicted))(using core.executionContext).flatMap {
            case Some(value) => Consequence.success(value.completion)
            case None => invalid("unsuspended conflict history has no actual committed completion")
          }
          next <- completedC(plan, actual.result, policy)
          wire <- if (actual.result.result.value.decision == "resolved") for {
            pending <- RepositorySyncConflictMaterialization.advanceC(source.store, plan.input,
              actual.result.result.value, source.current, next, policy)
            wire <- RepositorySyncNativeMergeValidation.waitC(source.store, pending, validationPolicy, review)
          } yield wire else humanPort match {
            case Some(port) => port.advanceC(RepositorySyncHumanConflict.Source(source.session, source.store, plan.input,
              actual.result.resultReference, actual.result.result.value.rationale, next, source.current))
            case None => waitC(next, actual.result)
          }
        } yield wire
    } yield wire

    private def sourceC(identity: String, core: ActionCall.Core): Consequence[Source] = for {
      _ <- if (core != null && core.action != null && core.action.request != null &&
          core.action.request.service.contains(completionOperation.service) &&
          Set(F.advanceOperation.operation, completionOperation.operation,
            RepositorySyncHumanConflict.completionOperation.operation)(core.action.request.operation)) Consequence.unit
        else invalid("conflict continuation requires its actual owning Operation")
      session <- F.sessionC(identity, declaration, definition, core)
      store <- N.bindC(session, declaration, core, acknowledgement)
      conflicted <- store.loadC("merge-conflicted").flatMap {
        case Some(value) if value.payload.stage == "MergeConflicted" => Consequence.success(value)
        case _ => invalid("semantic conflict selection has no genuine native conflict")
      }
      instances <- session.storage.bindInstancesC()(using core.executionContext)
      current <- instances.loadC(InstanceIdentity(identity))(using core.executionContext).flatMap {
        case Some(value) => value.validateC
        case None => invalid("native conflict instance is missing")
      }
    } yield Source(session, store, conflicted, current)

    /** Reconstruct only the actual paired AI issue/completion. Decoded Human
      * input cannot supply the source or select a new conflict/repository.
      */
    private def humanSourceC(identity: String, core: ActionCall.Core): Consequence[RepositorySyncHumanConflict.Source] = for {
      source <- sourceC(identity, core)
      issued <- source.session.storage.bindIssuedC(completionOperation, S.inputCodec)
      work <- issued.loadC(continuationId(source.conflicted))(using core.executionContext).flatMap {
        case Some(value) => Consequence.success(value)
        case None => invalid("Human question has no actual preceding conflict issue")
      }
      input <- inputC(work)
      plan <- prepareC(source, input)
      native <- nativeCompletionC(source.session.storage)
      completion <- native.loadRetainedC(continuationId(source.conflicted))(using core.executionContext).flatMap {
        case Some(value) => Consequence.success(value.completion)
        case None => invalid("Human question has no actual preceding semantic completion")
      }
      next <- completedC(plan, completion.result, policy)
      _ <- if (completion.result.result.value.decision == "decision-needed") Consequence.unit
        else invalid("Human question cannot replace a resolved proposal or unrelated completion")
    } yield RepositorySyncHumanConflict.Source(source.session, source.store, input, completion.result.resultReference,
      completion.result.result.value.rationale, next, source.current)

    private def prepareC(source: Source, input: I.Request): Consequence[Plan] = for {
      _ <- policy.validateC
      _ <- if (input != null && input.instanceIdentity == source.current.identity.value &&
          input.conflictRevision == source.conflicted.record.revision.value.toString &&
          input.localTip == source.conflicted.payload.before.head &&
          source.conflicted.payload.mergeHead.contains(input.remoteTip) && input.conflicts != null &&
          input.conflicts.nonEmpty && input.conflicts.forall(c => c != null && policy.semanticPaths(c.path)) &&
          input.conflicts.map(_.identity) == input.conflicts.indices.map(i => "conflict-" + (i + 1)).toVector &&
          input.conflicts.map(_.path).toSet == source.conflicted.payload.after.get.dirty.filter(_.unmerged).map(_.path).toSet)
        Consequence.unit else invalid("conflict input differs from its native source, assigned IDs or host policy")
      required <- RepositorySyncDefinitionBinding.conflictOperationC
      from = source.conflicted.record
      bundle = ContextBundle("Resolve only the issued native text conflicts",
        Vector(ContextReference("native-conflict:" + from.identity.value, input.conflictRevision)), Vector.empty,
        ContextSnapshot(from.definition.workflowRevision.value, evidenceRevision = Some(input.conflictRevision)))
      continuation = Continuation(StateMachineRunIdentity(from.identity.value), continuationId(source.conflicted),
        StateMachineRevision(from.revision.next.value.toString), required, bundle)
      entry = HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next, Lifecycle.Active,
        from.currentProgression, from.correlation, CausationReference("native-conflict-issued:" + from.identity.value),
        from.derivedCompositeOccurrence, from.committedPredecessor,
        Some(SuspensionBoundary(NativeIdentity(continuation.continuationId.value), from.revision.next,
          ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(required.metadata.completionContract.identity),
          EvidenceReference(required.metadata.evidenceContract.identity), from.correlation)))
      intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
        declaration.configuration, from, from.revision, entry,
        ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available))
      admitted <- WorkflowInstanceAtomicTransitionV1.admitSuspensionC(intent)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, from)
      work = WorkflowInteraction[I.Request, Nothing](handle, WorkflowContinuation.WorkOrder(
        ContinuationRequest(continuation.runId, continuation.continuationId, continuation.expectedRevision,
          required.identity, required.operation, Some(TypedValue(S.inputCodec.typeIdentity, input)), required.resultType,
          bundle, required.metadata.completionContract, required.metadata.evidenceContract, completionOperation),
        ExecutionRequirement(Vector(CapabilityRequirement(required.identity.capability)), RiskLevel("SOURCE_CONFLICT"),
          ReasoningLevel.Standard, false), MinimalPresentation("Native repository conflicts",
          "Return only the assigned resolution proposal. Workflow owns materialization, validation and repository effects.")))
    } yield Plan(source, input, intent, admitted.next, work)

    private[RepositorySyncNativeConflictContinuation] def operationC(core: Option[ActionCall.Core], native: WorkflowManagedCompletion[S.Result]):
        Consequence[spec.OperationDefinition] = {
      val provided = RepositorySyncDefinitionBinding.providedDefinition
      val op = provided.providedOperations.find(o => o.service == completionOperation.service && o.name == completionOperation.operation)
      if (op.isEmpty) return invalid("conflict completion is absent from generated provided ABI")
      val instances = CallScopedWorkflowInstances.managed(declaration.configuration, definition, declaration.instances, declaration.continuations)
      val issued = new IssuedWorkOrderPersistence[I.Request] {
        def putIfAbsentC(work: WorkflowInteraction[I.Request, Nothing]) = invalid("completion cannot issue semantic work")
        def loadC(id: ContinuationIdentity) = core match {
          case None => invalid("completion issue lookup requires its current call")
          case Some(call) => F.bindC(declaration, definition, call).flatMap(_.bindIssuedC(completionOperation, S.inputCodec))
            .flatMap(_.loadC(id)(using call.executionContext))
        }
      }
      val codec = new WorkflowCompletionServiceOperation.Codec[S.Result] {
        val resultTypeIdentity = S.resultCodec.typeIdentity
        def decodeC(request: ProtocolRequest) = WorkflowJsonCommandRequest.commandArguments(request) match {
          case List(Argument("arg1", wire: String, _)) => WorkflowResultJsonV1.decodeC(wire, S.resultCodec)
          case _ => invalid("completion requires one canonical typed result envelope")
        }
        def encodeC(resume: ContinuationRuntime.Resume) = invalid("completion acknowledgement requires actual native commit")
        override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, submitted: ContinuationResult[S.Result],
          completed: Option[InstanceRecord]): Consequence[OperationResponse] =
          completed.map(record => waitC(record, submitted).map(OperationResponse.Scalar(_)))
            .getOrElse(uncertain("conflict completion has no native committed state"))
      }
      val closing = new WorkflowCompletionServiceOperation.ClosingProgram[S.Result] {
        def selectC(submitted: ContinuationResult[S.Result], current: InstanceRecord) = core match {
          case None => invalid("conflict closing selection requires its owning call")
          case Some(call) => for {
            work <- issued.loadC(submitted.continuationId).flatMap {
              case Some(value) => Consequence.success(value)
              case None => invalid("conflict result has no actual issued WorkOrder")
            }
            input <- inputC(work)
            source <- sourceC(work.handle.instanceIdentity.value, call)
            plan <- prepareC(source, input)
            next <- completedC(plan, submitted, policy)
          } yield {
            val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              ActionExecution.Completed(StateMachineOperationResult(closingType, submitted.resultReference)))
            WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next))
          }
        }
      }
      val actualInstances = core.fold[WorkflowInstancePersistence](instances)(CallScopedWorkflowInstances.forCall(instances, _))
      WorkflowCompletionServiceOperation.bindC(provided, op.get, WorkflowJsonCommandRequest.definition, spec.ResponseDefinition.void,
        codec, ContinuationRuntime.empty, issued, actualInstances, declaration.configuration,
        () => throw new IllegalStateException("Conflict completion requires canonical managed UoW"), Some(closing), Some(native),
        descriptiveAttributes = SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositorySyncWorkResultOperation.specification.content.descriptiveAttributes)
    }
  }

  def bindC(declaration: F.Declaration, definition: DefinitionBinding, policy: I.Policy,
    acknowledgement: String => Consequence[Unit] = _ => Consequence.unit,
    validationPolicy: Option[RepositorySyncNativeMergeValidation.Policy] = None,
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port] = None,
    human: Option[RepositorySyncHumanConflict.Installation] = None): Consequence[Port] = for {
    _ <- F.validateDeclarationC(declaration)
    _ <- policy.validateC
    _ <- if (definition != null) Consequence.unit else invalid("conflict definition is absent")
    _ <- if (human != null) human.fold(Consequence.unit)(_.validateC) else invalid("Human installation selection is invalid")
  } yield new Port(declaration, definition, policy, acknowledgement, validationPolicy, review, human)

  def bindCompletionC(params: ComponentCreate, declaration: F.Declaration,
    definition: DefinitionBinding, policy: I.Policy): Consequence[spec.OperationDefinition] = for {
    port <- bindC(declaration, definition, policy)
    storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
      Vector(definition), declaration.instances, declaration.continuations)
    native <- nativeCompletionC(storage)
    declared <- port.operationC(None, native)
  } yield CallScopedOperationBinding.wrap(declared, core => F.bindC(declaration, definition, core)
    .flatMap(nativeCompletionC).flatMap(actual => port.operationC(Some(core), actual)))

  private def completedC(plan: Plan, submitted: ContinuationResult[S.Result], policy: I.Policy): Consequence[InstanceRecord] = for {
    _ <- S.admitC(plan.input, submitted.result.value, policy)
    current = plan.next
    stage = if (submitted.result.value.decision == "resolved") "MergeResolutionAdmitted" else "ConflictDecisionRequired"
    event = if (submitted.result.value.decision == "resolved") "resolution-plan-admitted" else "conflict-decision-required"
    _ <- if (domain.statemachine.RepositorySyncBoundaryLifecycle.permits("MergeConflicted", event, stage)) Consequence.unit
      else invalid("conflict successor is not declared by CML")
    entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1), current.revision.next, Lifecycle.Active,
      Some(ProgressionReference("sync." + stage)), current.correlation, CausationReference(submitted.resultReference.identity),
      current.derivedCompositeOccurrence, current.committedPredecessor, None)
    next <- current.appendC(current.revision, entry)
  } yield next
  private def nativeCompletionC(storage: WorkflowComponentStorage) = storage.bindCompletionC(completionOperation,
    S.inputCodec, S.resultCodec, closingType, (_, _) => Consequence.success(Vector.empty))
  private def inputC(work: WorkflowInteraction[I.Request, Nothing]): Consequence[I.Request] = work.current match {
    case order: WorkflowContinuation.WorkOrder[?] if order.request.input.exists(v =>
        v.typeIdentity == S.inputCodec.typeIdentity && v.value.isInstanceOf[I.Request]) =>
      Consequence.success(order.request.input.get.value.asInstanceOf[I.Request])
    case _ => invalid("issued conflict work has no assigned native input")
  }
  private def continuationId(source: N.Stage): ContinuationIdentity =
    ContinuationIdentity(source.record.identity.value + ":native-conflict:" + source.record.revision.value)
  private def waitC(next: InstanceRecord, result: ContinuationResult[S.Result]): Consequence[String] = for {
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, next)
    wire <- WorkflowWaitJsonV1.encodeC(WorkflowInteraction[Nothing, Nothing](handle,
      WorkflowContinuation.Wait(StateMachineRevision(next.revision.value.toString), result.contextSnapshot,
        MinimalPresentation("Native conflict proposal retained", if (result.result.value.decision == "resolved")
          "The assigned proposal is saved; native materialization, validation, review and convergence remain incomplete."
        else "Human decision remains required. This AI result grants no consent, file change or command authority."))))
  } yield wire
  private def same(a: InstanceRecord, b: InstanceRecord) = SplitPhaseDefinitionBinding.sameRecord(a, b)
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync native conflict continuation " + reason)
  private def uncertain[A](reason: String): Consequence[A] =
    org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
}
