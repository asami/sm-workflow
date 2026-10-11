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
import RepositorySyncNativeMergeReview as I
import RepositorySyncNativeMergeReview as S
import RepositorySyncNativeProgressStore as N

/** Paired native issue/completion. Durable proposals are deliberately not
  * file writes, validation acceptance or commit/push authority. No pool,
  * provider, execution-context lease or semantic routing is retained by host.
  * @since   Oct.  6, 2026
  * @version Oct.  9, 2026
  */
private[smworkflow] object RepositorySyncNativeMergeReviewContinuation {
  val completionOperation = StateMachineOperationIdentity("RepositorySyncService", "submitNativeMergeReview")
  val closingType = StateMachineResultTypeReference("RepositorySyncNativeMergeReviewSubmissionResult")
  private final case class Source(session: F.Session, store: N.Store, validated: N.Stage, current: InstanceRecord)
  private final case class Plan(source: Source, input: I.Request,
    intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent, next: InstanceRecord,
    work: WorkflowInteraction[I.Request, Nothing])

  final class Port private[RepositorySyncNativeMergeReviewContinuation] (
    declaration: F.Declaration, definition: DefinitionBinding, policy: I.Policy,
    acknowledgement: String => Consequence[Unit], evidence: S.EvidencePort) {

    def advanceC(identity: String, core: ActionCall.Core): Consequence[String] = for {
      source <- sourceC(identity, core)
      native <- source.session.storage.bindIssuedC(completionOperation, S.inputCodec)
      existing <- native.loadC(continuationId(source.validated))(using core.executionContext)
      plan <- existing match {
        case Some(work) => for {
          input <- inputC(work)
          selected <- prepareC(source, input)
          original <- selectIssuedC(selected.work, work)
        } yield selected.copy(work = original)
        case None => for {
          input <- I.captureC(source.store, source.validated, policy)
          selected <- prepareC(source, input)
          saved <- native.suspendC(selected.intent, selected.work)(using core.executionContext)
        } yield selected
      }
      wire <- if (same(source.current, plan.next) || same(source.current, source.validated.record))
        WorkflowWorkOrderJsonV1.encodeC(plan.work, S.inputCodec)
        else for {
          completion <- nativeCompletionC(source.session.storage)
          actual <- completion.loadRetainedC(continuationId(source.validated))(using core.executionContext).flatMap {
            case Some(value) => Consequence.success(value.completion)
            case None => invalid("unsuspended review history has no actual committed completion")
          }
          next <- completedC(plan, actual.result, policy)
          wire <- waitC(next, actual.result)
        } yield wire
    } yield wire

    /** Observe only the actual paired committed acceptance. This is not a
      * caller-selected disposition and does not itself grant any Git effect.
      * Pending-input checks still apply at issue and completion; a committed
      * successor instead belongs to the convergence postimage checks.
      */
    def acceptedC(identity: String, core: ActionCall.Core): Consequence[Option[N.Stage]] = for {
      session <- F.sessionC(identity, declaration, definition, core)
      store <- N.bindC(session, declaration, core, acknowledgement)
      validated <- store.loadC("merge-validated")
      selected <- validated match {
        case None => Consequence.success(None)
        case Some(_) => for {
          source <- sourceC(identity, core, false)
          issued <- source.session.storage.bindIssuedC(completionOperation, S.inputCodec)
          work <- issued.loadC(continuationId(source.validated))(using core.executionContext)
          completion <- nativeCompletionC(source.session.storage)
          retained <- completion.loadRetainedC(continuationId(source.validated))(using core.executionContext)
          result <- retained match {
            case Some(value) if value.completion.result.result.value.decision == "accept" => for {
              input <- work.map(inputC).getOrElse(invalid("accepted review has no actual paired issue"))
              projected <- prepareC(source, input)
              original <- selectIssuedC(projected.work, work.get)
              plan = projected.copy(work = original)
              next <- completedC(plan, value.completion.result, policy)
            } yield Some(N.Stage(next, source.validated.payload.copy(stage = "ReviewAccepted")))
            case _ => Consequence.success(None)
          }
        } yield result
      }
    } yield selected

    private def sourceC(identity: String, core: ActionCall.Core, pendingChecks: Boolean = true): Consequence[Source] = for {
      _ <- if (core != null && core.action != null && core.action.request != null &&
          core.action.request.service.contains(completionOperation.service) &&
          Set(F.advanceOperation.operation, completionOperation.operation)(core.action.request.operation)) Consequence.unit
        else invalid("review continuation requires its actual owning Operation")
      session <- F.sessionC(identity, declaration, definition, core)
      store <- N.bindC(session, declaration, core, acknowledgement)
      validated <- store.loadC("merge-validated").flatMap {
        case Some(value) if value.payload.stage == "MergeValidated" => Consequence.success(value)
        case _ => invalid("semantic review selection has no genuine native review")
      }
      instances <- session.storage.bindInstancesC()(using core.executionContext)
      current <- instances.loadC(InstanceIdentity(identity))(using core.executionContext).flatMap {
        case Some(value) => value.validateC
        case None => invalid("native review instance is missing")
      }
      _ <- if (pendingChecks) RepositorySyncNativeMergeValidation.preconditionsC(store, validated) else Consequence.unit
    } yield Source(session, store, validated, current)

    private def prepareC(source: Source, input: I.Request): Consequence[Plan] = for {
      _ <- I.inputC(input, policy)
      state <- source.validated.payload.validation.map(Consequence.success)
        .getOrElse(invalid("review has no actual selected validation"))
      _ <- if (input.instanceIdentity == source.current.identity.value &&
          input.validatedRevision == source.validated.record.revision.value.toString &&
          input.localTip == source.validated.payload.before.head &&
          source.validated.payload.mergeHead.contains(input.remoteTip) &&
          source.validated.payload.indexTree.contains(input.indexTree) &&
          input.validationMode == state.mode && input.selectedChecks == state.selected.map(_.identity))
        Consequence.unit else invalid("review input differs from actual selected native validation")
      required <- RepositorySyncDefinitionBinding.nativeReviewOperationC
      from = source.validated.record
      bundle = ContextBundle("Independently review the full pending merge against both parents and its actual validation",
        Vector(ContextReference("native-review:" + from.identity.value, input.validatedRevision)), Vector.empty,
        ContextSnapshot(from.definition.workflowRevision.value, evidenceRevision = Some(input.validatedRevision)))
      continuation = Continuation(StateMachineRunIdentity(from.identity.value), continuationId(source.validated),
        StateMachineRevision(from.revision.next.value.toString), required, bundle)
      entry = HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next, Lifecycle.Active,
        from.currentProgression, from.correlation, CausationReference("native-review-issued:" + from.identity.value),
        from.derivedCompositeOccurrence, from.committedPredecessor,
        Some(SuspensionBoundary(NativeIdentity(continuation.continuationId.value), from.revision.next,
          ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(required.metadata.completionContract.identity),
          EvidenceReference(required.metadata.evidenceContract.identity), from.correlation)))
      intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
        declaration.configuration, from, from.revision, entry,
        ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available))
      next <- from.appendC(from.revision, entry)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, from)
      work = WorkflowInteraction[I.Request, Nothing](handle, WorkflowContinuation.WorkOrder(
        ContinuationRequest(continuation.runId, continuation.continuationId, continuation.expectedRevision,
          required.identity, required.operation, Some(TypedValue(S.inputCodec.typeIdentity, input)), required.resultType,
          bundle, required.metadata.completionContract, required.metadata.evidenceContract, completionOperation),
        ExecutionRequirement(Vector(CapabilityRequirement(required.identity.capability)), RiskLevel("SOURCE_MERGE"),
          ReasoningLevel.Standard, true), MinimalPresentation("Independent native merge review",
          "Return only the assigned semantic review. Workflow owns result admission and all repository effects.")))
    } yield Plan(source, input, intent, next, work)

    private[RepositorySyncNativeMergeReviewContinuation] def operationC(core: Option[ActionCall.Core], native: WorkflowManagedCompletion[S.Result]):
        Consequence[spec.OperationDefinition] = {
      val provided = RepositorySyncDefinitionBinding.providedDefinition
      val op = provided.providedOperations.find(o => o.service == completionOperation.service && o.name == completionOperation.operation)
      if (op.isEmpty) return invalid("review completion is absent from generated provided ABI")
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
            .getOrElse(uncertain("review completion has no native committed state"))
      }
      val closing = new WorkflowCompletionServiceOperation.ClosingProgram[S.Result] {
        def selectC(submitted: ContinuationResult[S.Result], current: InstanceRecord) = core match {
          case None => invalid("review closing selection requires its owning call")
          case Some(call) => for {
            work <- issued.loadC(submitted.continuationId).flatMap {
              case Some(value) => Consequence.success(value)
              case None => invalid("review result has no actual issued WorkOrder")
            }
            input <- inputC(work)
            source <- sourceC(work.handle.instanceIdentity.value, call)
            projected <- prepareC(source, input)
            original <- selectIssuedC(projected.work, work)
            plan = projected.copy(work = original)
            _ <- evidence.admitC(call, input, submitted)
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
        () => throw new IllegalStateException("Review completion requires canonical managed UoW"), Some(closing), Some(native),
        descriptiveAttributes = SmWorkflowComponent.RepositorySyncServiceService.SubmitNativeMergeReviewOperation.specification.content.descriptiveAttributes)
    }
  }

  def bindC(declaration: F.Declaration, definition: DefinitionBinding, installation: S.Installation,
    acknowledgement: String => Consequence[Unit] = _ => Consequence.unit): Consequence[Port] = for {
    _ <- F.validateDeclarationC(declaration)
    _ <- if (installation != null) installation.validateC else invalid("review installation is absent")
    _ <- if (definition != null) Consequence.unit else invalid("review definition is absent")
  } yield new Port(declaration, definition, installation.policy, acknowledgement, installation.evidence)

  def bindCompletionC(params: ComponentCreate, declaration: F.Declaration,
    definition: DefinitionBinding, installation: S.Installation): Consequence[spec.OperationDefinition] = for {
    port <- bindC(declaration, definition, installation)
    storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
      Vector(definition), declaration.instances, declaration.continuations)
    native <- nativeCompletionC(storage)
    declared <- port.operationC(None, native)
  } yield CallScopedOperationBinding.wrap(declared, core => F.bindC(declaration, definition, core)
    .flatMap(nativeCompletionC).flatMap(actual => port.operationC(Some(core), actual)))

  private def completedC(plan: Plan, submitted: ContinuationResult[S.Result], policy: I.Policy): Consequence[InstanceRecord] = for {
    _ <- S.admitC(plan.input, submitted.result.value, policy)
    current = plan.next
    stage = submitted.result.value.decision match {
      case "accept" => "ReviewAccepted"
      case "repair" => "ReviewRepairRequired"
      case "decision-needed" => "ReviewDecisionRequired"
      case "reject" => "ReviewRejected"
    }
    event = submitted.result.value.decision match {
      case "accept" => "native-review-accepted"
      case "repair" => "native-review-repair-required"
      case "decision-needed" => "native-review-decision-required"
      case "reject" => "native-review-rejected"
    }
    _ <- if (domain.statemachine.RepositorySyncBoundaryLifecycle.permits("MergeValidated", event, stage)) Consequence.unit
      else invalid("review successor is not declared by CML")
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
    case _ => invalid("issued review work has no assigned native input")
  }
  /** Current executable route and owning slot resolve; original meaning and
    * presentation are consumed, never compared with a regenerated WorkOrder.
    */
  private[admission] def selectIssuedC(expected: WorkflowInteraction[I.Request, Nothing],
    saved: WorkflowInteraction[I.Request, Nothing]): Consequence[WorkflowInteraction[I.Request, Nothing]] =
    if (expected == null || saved == null || saved.handle != expected.handle) invalid("review issue has another execution reference")
    else (expected.current, saved.current) match {
      case (planned: WorkflowContinuation.WorkOrder[I.Request], original: WorkflowContinuation.WorkOrder[I.Request]) => for {
        _ <- saved.handle.validateC
        requirement <- original.requirement.validateC
        _ <- inputC(saved)
        e = planned.request
        q = original.request
        _ <- if (q.runId == e.runId && q.continuationId == e.continuationId && q.expectedRevision == e.expectedRevision &&
          q.requiredOperation == e.requiredOperation && q.operation == e.operation && q.resultType == e.resultType &&
          q.completionOperation == e.completionOperation && q.completion == e.completion && q.evidence == e.evidence &&
          GoalPhaseClosureAuthority.sameContextScope(q.context.snapshot, e.context.snapshot) &&
          requirement.reviewRequired && requirement.capabilities.contains(CapabilityRequirement(e.requiredOperation.capability))) Consequence.unit
          else invalid("review issue crosses its slot, Required contract, model scope or independent-review requirement")
      } yield saved
      case _ => invalid("review issue is not its actual WorkOrder")
    }
  private[admission] def sameResultTarget(left: ContinuationResult[S.Result], right: ContinuationResult[S.Result]): Boolean =
    left != null && right != null && left.result != null && right.result != null &&
      left.handle == right.handle && left.runId == right.runId && left.continuationId == right.continuationId &&
      left.expectedRevision == right.expectedRevision && left.resultReference == right.resultReference &&
      left.result.typeIdentity == right.result.typeIdentity
  private def continuationId(source: N.Stage): ContinuationIdentity =
    ContinuationIdentity(source.record.identity.value + ":native-review:" + source.record.revision.value)
  private def waitC(next: InstanceRecord, result: ContinuationResult[S.Result]): Consequence[String] = for {
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, next)
    wire <- WorkflowWaitJsonV1.encodeC(WorkflowInteraction[Nothing, Nothing](handle,
      WorkflowContinuation.Wait(StateMachineRevision(next.revision.value.toString), result.contextSnapshot,
        MinimalPresentation("Native merge review retained",
          "The independent review is saved. Repair, Human decision or authorized final convergence remains incomplete."))))
  } yield wire
  private def same(a: InstanceRecord, b: InstanceRecord) = SplitPhaseDefinitionBinding.sameRecord(a, b)
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync native review continuation " + reason)
  private def uncertain[A](reason: String): Consequence[A] =
    org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
}
