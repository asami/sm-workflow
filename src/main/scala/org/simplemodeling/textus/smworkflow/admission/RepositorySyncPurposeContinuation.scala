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
import RepositorySyncPurpose as I
import RepositorySyncPurpose as S
import RepositorySyncNativeProgressStore as N

/** Actual paired dirty-purpose grouping. A semantic proposal grants no effect or review acceptance.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  */
private[smworkflow] object RepositorySyncPurposeContinuation {
  val completionOperation = StateMachineOperationIdentity("RepositorySyncService", "submitRepositoryPurposeResult")
  val closingType = StateMachineResultTypeReference("RepositorySyncConflictSubmissionResult")
  private final case class Source(session: F.Session, store: N.Store, inventory: N.Stage, current: InstanceRecord)
  private final case class Plan(source: Source, input: I.Request,
    intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent, next: InstanceRecord,
    work: WorkflowInteraction[I.Request, Nothing])

  final class Port private[RepositorySyncPurposeContinuation](declaration: F.Declaration, definition: DefinitionBinding,
    acknowledgement: String => Consequence[Unit]) {
    def advanceC(identity: String, core: ActionCall.Core): Consequence[Either[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult, N.Stage]] = for {
      source <- sourceC(identity, core)
      native <- source.session.storage.bindIssuedC(completionOperation, S.inputCodec)
      existing <- native.loadC(continuationId(source.inventory))(using core.executionContext)
      input = source.inventory.payload.purposes.get.request
      prepared <- prepareC(source, input)
      work <- existing match {
        case Some(value) => Consequence.success(value)
        case None => for {
          bound <- RepositorySyncNativeExecution.bindIssuedC(prepared.work, RepositorySyncNativeExecution.Work.Conflict,
            declaration.executionClassifications, declaration.executionProfiles, declaration.executionOrigin)
          _ <- native.suspendC(prepared.intent, bound)(using core.executionContext)
        } yield bound
      }
      completion <- nativeCompletionC(source.session.storage)
      retained <- completion.loadRetainedC(continuationId(source.inventory))(using core.executionContext)
      wire <- retained match {
        case None => NativeWorkflowOperationOutput.repositoryPurposeRequestC(work).map(Left(_))
        case Some(value) => for {
          _ <- S.admitC(input, value.completion.result.result.value)
          next <- N.completionNextC(source.current, source.inventory.record.revision.next.next,
            value.completion.result.resultReference, "PurposeGrouped")
          result = value.completion.result.result.value
        } yield if (result.decision != "resolved") Left(NativeWorkflowOperationOutput.repositoryWait(identity,
          "Purpose intent remains unresolved; original work is preserved and no acceptance/effect is inferred.", "purpose-decision-needed"))
          else Right(N.Stage(next, source.inventory.payload.copy(stage = "PurposeGrouped",
            purposes = Some(source.inventory.payload.purposes.get.copy(plan = Some(result))))))
      }
    } yield wire

    private def sourceC(identity: String, core: ActionCall.Core): Consequence[Source] = for {
      session <- F.sessionC(identity, declaration, definition, core)
      store <- N.bindC(session, declaration, core, acknowledgement)
      inventory <- store.loadC("purpose-inventory").flatMap(_.map(Consequence.success)
        .getOrElse(invalid("actual dirty inventory is absent")))
      _ <- if (inventory.payload.purposes.nonEmpty) Consequence.unit else invalid("inventory has no actual purpose input")
    } yield Source(session, store, inventory, session.started.current)

    private def prepareC(source: Source, input: I.Request): Consequence[Plan] = for {
      required <- RepositorySyncDefinitionBinding.purposeOperationC
      from = source.inventory.record
      bundle = ContextBundle("Resolve only uncovered dirty purpose and dependency intent",
        Vector(ContextReference("native-purpose:" + from.identity.value, source.inventory.record.revision.value.toString)), Vector.empty,
        ContextSnapshot(from.definition.workflowRevision.value, evidenceRevision = Some(source.inventory.record.revision.value.toString)))
      continuation = Continuation(StateMachineRunIdentity(from.identity.value), continuationId(source.inventory),
        StateMachineRevision(from.revision.next.value.toString), required, bundle)
      entry = HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next, Lifecycle.Active,
        from.currentProgression, from.correlation, CausationReference("native-purpose-issued:" + from.identity.value),
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
        ExecutionRequirement(Vector(CapabilityRequirement(required.identity.capability)), RiskLevel("SOURCE_PURPOSE"),
          ReasoningLevel.Standard, false), MinimalPresentation("Dirty repository purposes",
          "Return only purpose content/dependency grouping; native checks, assessment, review and effects retain their own owners.")))
    } yield Plan(source, input, intent, next, work)

    private[RepositorySyncPurposeContinuation] def operationC(core: Option[ActionCall.Core], native: WorkflowManagedCompletion[S.Result],
      result: Option[ContinuationResult[NativeWorkflowResultInput.Value]] = None):
        Consequence[spec.OperationDefinition] = {
      val provided = RepositorySyncDefinitionBinding.providedDefinition
      val op = provided.providedOperations.find(o => o.service == completionOperation.service && o.name == completionOperation.operation)
      if (op.isEmpty) return invalid("purpose completion is absent from generated provided ABI")
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
        def decodeC(request: ProtocolRequest) = result.map(NativeWorkflowResultInput.typedC(_, S.resultCodec))
          .getOrElse(invalid("purpose completion has no resolved semantic result"))
        def encodeC(resume: ContinuationRuntime.Resume) = invalid("completion acknowledgement requires actual native commit")
        override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, submitted: ContinuationResult[S.Result],
          completed: Option[InstanceRecord]): Consequence[OperationResponse] =
          completed.map(record => Consequence.success(NativeWorkflowOperationOutput.response(NativeWorkflowOperationOutput.accepted(submitted, record))))
            .getOrElse(uncertain("purpose completion has no native committed state"))
      }
      val closing = new WorkflowCompletionServiceOperation.ClosingProgram[S.Result] {
        def selectC(submitted: ContinuationResult[S.Result], current: InstanceRecord) = core match {
          case None => invalid("purpose closing selection requires its owning call")
          case Some(call) => for {
            work <- issued.loadC(submitted.continuationId).flatMap {
              case Some(value) => Consequence.success(value)
              case None => invalid("purpose result has no actual issued WorkOrder")
            }
            input <- inputC(work)
            source <- sourceC(work.handle.instanceIdentity.value, call)
            plan <- prepareC(source, input)
            next <- completedC(plan, submitted)
          } yield {
            val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              ActionExecution.Completed(StateMachineOperationResult(closingType, submitted.resultReference)))
            WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next))
          }
        }
      }
      val actualInstances = core.fold[WorkflowInstancePersistence](instances)(CallScopedWorkflowInstances.forCall(instances, _))
      WorkflowCompletionServiceOperation.bindC(provided, op.get, SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositoryPurposeResultOperation.specification.request, SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositoryPurposeResultOperation.specification.response,
        codec, ContinuationRuntime.empty, issued, actualInstances, declaration.configuration,
        () => throw new IllegalStateException("Conflict completion requires canonical managed UoW"), Some(closing), Some(native),
        descriptiveAttributes = SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositoryPurposeResultOperation.specification.content.descriptiveAttributes)
    }
  }

  def bindC(declaration: F.Declaration, definition: DefinitionBinding,
    acknowledgement: String => Consequence[Unit] = _ => Consequence.unit): Consequence[Port] =
    F.validateDeclarationC(declaration).map(_ => new Port(declaration, definition, acknowledgement))

  def bindCompletionC(params: ComponentCreate, declaration: F.Declaration,
    definition: DefinitionBinding): Consequence[spec.OperationDefinition] = for {
    port <- bindC(declaration, definition)
    storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
      Vector(definition), declaration.instances, declaration.continuations)
    native <- nativeCompletionC(storage)
    declared <- port.operationC(None, native)
  } yield CallScopedOperationBinding.wrapResult(declared, (core, result) => F.bindC(declaration, definition, core)
    .flatMap(nativeCompletionC).flatMap(actual => port.operationC(Some(core), actual, Some(result))))

  private def completedC(plan: Plan, submitted: ContinuationResult[S.Result]): Consequence[InstanceRecord] = for {
    _ <- S.admitC(plan.input, submitted.result.value)
    current = plan.next
    entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1), current.revision.next, Lifecycle.Active,
      Some(ProgressionReference("sync.PurposeGrouped")), current.correlation, CausationReference(submitted.resultReference.identity),
      current.derivedCompositeOccurrence, current.committedPredecessor, None)
    next <- current.appendC(current.revision, entry)
  } yield next
  private def nativeCompletionC(storage: WorkflowComponentStorage) = storage.bindCompletionC(completionOperation,
    S.inputCodec, S.resultCodec, closingType, (_, _) => Consequence.success(Vector.empty))
  private def inputC(work: WorkflowInteraction[I.Request, Nothing]): Consequence[I.Request] = work.current match {
    case order: WorkflowContinuation.WorkOrder[I.Request] => order.request.input.map(value => Consequence.success(value.value))
      .getOrElse(invalid("issued purpose work has no actual input"))
    case _ => invalid("purpose continuation requires its actual WorkOrder")
  }
  private def continuationId(source: N.Stage): ContinuationIdentity =
    ContinuationIdentity(source.record.identity.value + ":native-purpose:" + source.record.revision.value)
  private def invalid[A](reason: String): Consequence[A] = N.invalid("purpose continuation " + reason)
  private def uncertain[A](reason: String): Consequence[A] = N.uncertain("purpose continuation " + reason)
}
