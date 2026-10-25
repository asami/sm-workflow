package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Request as ProtocolRequest
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import RepositorySyncFileEffect as P
import RepositorySyncManagedFetch as F
import RepositorySyncNativeConflictInput as I
import RepositorySyncNativeMergeRepair as M
import RepositorySyncNativeProgressStore as N

/** Genuine paired review -> assigned affected-file proposal. Submit owns only
  * proposal admission; explicit Advance owns fixed effects and fresh review.
  * @since   Oct. 15, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncNativeMergeRepairContinuation {
  val completionOperation = StateMachineOperationIdentity("RepositorySyncService", "submitNativeMergeRepair")
  val closingType = StateMachineResultTypeReference("RepositorySyncNativeMergeRepairSubmissionResult")
  private final case class Source(session: F.Session, store: N.Store,
    basis: M.Basis)

  final class Port private[RepositorySyncNativeMergeRepairContinuation] (
    declaration: F.Declaration, definition: DefinitionBinding, policy: I.Policy,
    validation: RepositorySyncNativeMergeValidation.Policy,
    review: RepositorySyncNativeMergeReviewContinuation.Port, convergence: Option[RepositorySyncNativeConvergence.AuthorityPort], acknowledgement: String => Consequence[Unit]) {

    def advanceC(identity: String, core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
      source <- _source_c(identity, core)
      issued <- source.session.storage.bindIssuedC(completionOperation, M.inputCodec)
      existing <- issued.loadC(_continuation_id(source))(using core.executionContext)
      native <- _native_completion_c(source.session.storage)
      retained <- native.loadRetainedC(_continuation_id(source))(using core.executionContext)
      output <- retained match {
        case Some(value) => for {
          work <- existing.map(Consequence.success).getOrElse(N.invalid("repair completion has no actual paired issue"))
          input <- NativeWorkflowOperationOutput.workInputC(work)
          result = value.completion.result
          next <- N.completionNextC(source.session.started.current, value.completion.instanceRevision,
            result.resultReference, if (result.result.value.decision == "repaired") "RepairProposalAdmitted" else "RepairDecisionRequired")
          output <- if (result.result.value.decision == "repaired") for {
            pending <- RepositorySyncNativeMergeRepairMaterialization.advanceC(source.store, source.basis,
              input, result.result.value, next, policy)
            output <- RepositorySyncNativeProgression.pendingMergeC(source.store, pending, Some(validation), Some(review),
              convergence, _namespace(source), Some(this))
          } yield output else Consequence.success(NativeWorkflowOperationOutput.repositoryWait(identity,
            "The repair requires separate actual Human handling; no proposal effects or consent are inferred.", "RepairDecisionRequired"))
        } yield output
        case None => existing match {
          case Some(work) => NativeWorkflowOperationOutput.repositoryRepairRequestC(work)
          case None if _budget_exhausted(source) => Consequence.success(NativeWorkflowOperationOutput.repositoryWait(identity,
            "Selected merge repair budget is exhausted; actual Human handling is required. No new repair or counter reset.", "native-repair-limit"))
          case None => for {
            _ <- if (source.session.started.current.currentProgression.exists(p => Set("sync.ReviewRepairRequired", "sync.ValidationRepairRequired")(p.value)) &&
                source.session.started.current.revision == source.basis.next.revision) Consequence.unit
              else N.invalid("repair issuance requires the actual current repair review completion")
            input <- _capture_c(source)
            work <- _issue_c(source, input, issued)
            output <- NativeWorkflowOperationOutput.repositoryRepairRequestC(work)
          } yield output
        }
      }
    } yield output

    private def _source_c(identity: String, core: ActionCall.Core): Consequence[Source] = for {
      _ <- if (core != null && core.action != null && core.action.request != null &&
          core.action.request.service.contains(completionOperation.service) &&
          Set(F.advanceOperation.operation, completionOperation.operation)(core.action.request.operation)) Consequence.unit
        else N.invalid("repair requires its actual owning Operation")
      session <- F.sessionC(identity, declaration, definition, core)
      store <- N.bindC(session, declaration, core, acknowledgement)
      reviewed <- review.savedReviewC(identity, core)
      saved <- reviewed match {
        case Some(value) if value.result.result.value.decision == "repair" &&
            value.next.currentProgression.contains(ProgressionReference("sync.ReviewRepairRequired")) =>
          Consequence.success(M.basis(value))
        case _ => for {
          candidate <- store.currentMergeCandidateC(session.started.current)
          failure <- RepositorySyncNativeMergeValidation.retainedFailureC(store, candidate.pending, validation, candidate.namespace)
            .flatMap(_.map(Consequence.success).getOrElse(N.invalid("repair has no actual retained failed check or repair review")))
          next <- store.loadC(N.candidateKey(candidate.namespace, "validation-repair-required"))
            .flatMap(_.filter(_.payload == failure.payload.copy(stage = "ValidationRepairRequired"))
              .map(Consequence.success).getOrElse(N.invalid("check-origin repair has no actual admitted source")))
          context <- RepositorySyncNativeMergeReview.captureCandidateC(store, failure,
            RepositorySyncNativeMergeReview.Policy("check-origin-repair", 1))
        } yield M.Basis(failure, context, Vector.empty, next.record,
          Some(failure.payload.validation.get.receipts.last.identity))
      }
    } yield Source(session, store, saved)

    private def _capture_c(source: Source): Consequence[M.Request] = for {
      _ <- policy.validateC
      findings = source.basis.findings
      paths = if (source.basis.validationFailure.isEmpty) findings.flatMap(_.affectedPaths).distinct.sorted
      else source.basis.input.changedPaths.filter(policy.semanticPaths).distinct.sorted
      _ <- M.admitRequestC(M.Request(source.basis.input, findings, paths.map(M.File(_, "")), source.basis.validationFailure), policy)
      _ <- RepositorySyncNativeMergeValidation.preconditionsC(source.store, source.basis.validated)
      request = P.Request(source.store.identity, source.basis.next.revision.value.toString,
        paths.map(path => P.File(path, None, "")))
      captured <- P.captureC(source.store.core, policy, request)
      _ <- if (captured.instance == request.instance && captured.revision == request.revision &&
          captured.status == "captured" && captured.files.map(_.copy(expected = None)) == request.files &&
          captured.files.forall(_.expected.nonEmpty)) Consequence.unit
        else N.invalid("repair capture lacks the assigned application instance/revision/path/text facts")
      input = M.Request(source.basis.input, findings, captured.files.map(file => M.File(file.path, file.expected.get)), source.basis.validationFailure)
      _ <- M.admitRequestC(input, policy)
      _ <- RepositorySyncNativeMergeValidation.preconditionsC(source.store, source.basis.validated)
    } yield input

    private def _budget_exhausted(source: Source): Boolean = {
      val used = source.session.started.current.history.flatMap(_.suspension.map(_.continuationIdentity.value))
        .filter(_.startsWith(source.store.identity + ":native-repair:")).distinct.size
      validation.assessment.exists(policy => used >= policy.repairLimit)
    }

    private def _issue_c(source: Source, input: M.Request,
      issued: WorkflowManagedIssuedWork[M.Request]): Consequence[WorkflowInteraction[M.Request, Nothing]] = for {
      required <- RepositorySyncDefinitionBinding.nativeRepairOperationC
      from = source.session.started.current
      used = from.history.flatMap(_.suspension.map(_.continuationIdentity.value)).filter(_.startsWith(
        source.store.identity + ":native-repair:")).distinct.size
      _ <- if (validation.assessment.forall(policy => used < policy.repairLimit)) Consequence.unit
        else N.invalid("selected merge repair budget is exhausted; no new repair issue or counter reset")
      bundle = ContextBundle("Repair only the actual retained review findings or failed selected checks and assigned captured files",
        Vector(ContextReference((if (source.basis.validationFailure.nonEmpty) "native-repair-check:" else "native-repair-review:") + from.identity.value, source.basis.next.revision.value.toString)),
        Vector.empty, ContextSnapshot(from.definition.workflowRevision.value,
          evidenceRevision = Some(source.basis.next.revision.value.toString)))
      continuation = Continuation(StateMachineRunIdentity(from.identity.value), _continuation_id(source),
        StateMachineRevision(from.revision.next.value.toString), required, bundle)
      entry = HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next, Lifecycle.Active,
        from.currentProgression, from.correlation, CausationReference("native-repair-issued:" + from.identity.value),
        from.derivedCompositeOccurrence, from.committedPredecessor,
        Some(SuspensionBoundary(NativeIdentity(continuation.continuationId.value), from.revision.next,
          ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(required.metadata.completionContract.identity),
          EvidenceReference(required.metadata.evidenceContract.identity), from.correlation)))
      intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
        declaration.configuration, from, from.revision, entry,
        ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available))
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, from)
      work = WorkflowInteraction[M.Request, Nothing](handle, WorkflowContinuation.WorkOrder(
        ContinuationRequest(continuation.runId, continuation.continuationId, continuation.expectedRevision,
          required.identity, required.operation, Some(TypedValue(M.inputCodec.typeIdentity, input)), required.resultType,
          bundle, required.metadata.completionContract, required.metadata.evidenceContract, completionOperation),
        ExecutionRequirement(Vector(CapabilityRequirement(required.identity.capability)), RiskLevel("SOURCE_MERGE"),
          ReasoningLevel.Standard, false), MinimalPresentation("Native affected-file merge repair",
          "Return only the assigned repair proposal. Workflow owns file effects, fresh checks and independent review.")))
      selected <- RepositorySyncNativeExecution.bindIssuedC(work, RepositorySyncNativeExecution.Work.Repair,
        declaration.executionClassifications, declaration.executionProfiles, declaration.executionOrigin)
      _ <- issued.suspendC(intent, selected)(using source.store.core.executionContext)
    } yield selected

    private[RepositorySyncNativeMergeRepairContinuation] def _operation_c(core: Option[ActionCall.Core],
      native: WorkflowManagedCompletion[M.Result], result: Option[ContinuationResult[NativeWorkflowResultInput.Value]] = None):
        Consequence[spec.OperationDefinition] = {
      val provided = RepositorySyncDefinitionBinding.providedDefinition
      val operation = provided.providedOperations.find(o => o.service == completionOperation.service && o.name == completionOperation.operation)
      if (operation.isEmpty) return N.invalid("repair completion is absent from generated provided ABI")
      val instances = CallScopedWorkflowInstances.managed(declaration.configuration, definition, declaration.instances, declaration.continuations)
      val issued = new IssuedWorkOrderPersistence[M.Request] {
        def putIfAbsentC(work: WorkflowInteraction[M.Request, Nothing]) = N.invalid("repair completion cannot issue work")
        def loadC(identity: ContinuationIdentity) = core match {
          case None => N.invalid("repair issue lookup requires the owning call")
          case Some(call) => F.bindC(declaration, definition, call).flatMap(_.bindIssuedC(completionOperation, M.inputCodec))
            .flatMap(_.loadC(identity)(using call.executionContext))
        }
      }
      val codec = new WorkflowCompletionServiceOperation.Codec[M.Result] {
        val resultTypeIdentity = M.resultCodec.typeIdentity
        def decodeC(request: ProtocolRequest) = result.map(NativeWorkflowResultInput.typedC(_, M.resultCodec))
          .getOrElse(N.invalid("repair completion has no resolved semantic result"))
        def encodeC(resume: ContinuationRuntime.Resume) = N.invalid("repair acknowledgement requires actual native commit")
        override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, submitted: ContinuationResult[M.Result],
          completed: Option[InstanceRecord]): Consequence[OperationResponse] =
          completed.map(record => Consequence.success(NativeWorkflowOperationOutput.response(NativeWorkflowOperationOutput.accepted(submitted, record))))
            .getOrElse(N.uncertain("repair completion has no native committed state"))
      }
      val closing = new WorkflowCompletionServiceOperation.ClosingProgram[M.Result] {
        def selectC(submitted: ContinuationResult[M.Result], current: InstanceRecord) = core match {
          case None => N.invalid("repair closing requires the owning call")
          case Some(call) => for {
            source <- _source_c(current.identity.value, call)
            _ <- if (submitted.continuationId == _continuation_id(source) &&
                current.currentProgression.exists(p => Set("sync.ReviewRepairRequired", "sync.ValidationRepairRequired")(p.value))) Consequence.unit
              else N.invalid("repair result crosses the actual current review assignment")
            work <- issued.loadC(submitted.continuationId).flatMap(_.map(Consequence.success)
              .getOrElse(N.invalid("repair result has no actual issued WorkOrder")))
            input <- NativeWorkflowOperationOutput.workInputC(work)
            _ <- M.admitC(input, submitted.result.value, policy)
            stage = if (submitted.result.value.decision == "repaired") "RepairProposalAdmitted" else "RepairDecisionRequired"
            entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1), current.revision.next, Lifecycle.Active,
              Some(ProgressionReference("sync." + stage)), current.correlation, CausationReference(submitted.resultReference.identity),
              current.derivedCompositeOccurrence, current.committedPredecessor, None)
            next <- current.appendC(current.revision, entry)
          } yield WorkflowCompletionServiceOperation.SelectedClosing(
            ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              ActionExecution.Completed(StateMachineOperationResult(closingType, submitted.resultReference))), Some(next))
        }
      }
      val actualinstances = core.fold[WorkflowInstancePersistence](instances)(CallScopedWorkflowInstances.forCall(instances, _))
      val generated = SmWorkflowComponent.RepositorySyncServiceService.SubmitNativeMergeRepairOperation.specification
      WorkflowCompletionServiceOperation.bindC(provided, operation.get, generated.request, generated.response,
        codec, ContinuationRuntime.empty, issued, actualinstances, declaration.configuration,
        () => throw new IllegalStateException("Repair completion requires canonical managed UoW"), Some(closing), Some(native),
        descriptiveAttributes = generated.content.descriptiveAttributes)
    }
  }

  def bindC(declaration: F.Declaration, definition: DefinitionBinding, policy: I.Policy,
    validation: RepositorySyncNativeMergeValidation.Policy, review: RepositorySyncNativeMergeReviewContinuation.Port,
    acknowledgement: String => Consequence[Unit] = _ => Consequence.unit,
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort] = None): Consequence[Port] = for {
    _ <- F.validateDeclarationC(declaration)
    _ <- if (definition != null && policy != null && policy.semanticPaths.nonEmpty && validation != null &&
        review != null && acknowledgement != null) Consequence.unit else N.invalid("repair installation is incomplete")
    _ <- policy.validateC
    _ <- validation.validateC
  } yield new Port(declaration, definition, policy, validation, review, convergence, acknowledgement)

  def bindCompletionC(params: ComponentCreate, declaration: F.Declaration, definition: DefinitionBinding,
    policy: I.Policy, validation: RepositorySyncNativeMergeValidation.Policy,
    review: RepositorySyncNativeMergeReviewContinuation.Port): Consequence[spec.OperationDefinition] = for {
    port <- bindC(declaration, definition, policy, validation, review)
    storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
      Vector(definition), declaration.instances, declaration.continuations)
    native <- _native_completion_c(storage)
    declared <- port._operation_c(None, native)
  } yield CallScopedOperationBinding.wrapResult(declared, (core, result) => F.bindC(declaration, definition, core)
    .flatMap(_native_completion_c).flatMap(actual => port._operation_c(Some(core), actual, Some(result))))

  private def _native_completion_c(storage: WorkflowComponentStorage) = storage.bindCompletionC(completionOperation,
    M.inputCodec, M.resultCodec, closingType, (_, _) => Consequence.success(Vector.empty))
  private def _continuation_id(source: Source): ContinuationIdentity =
    ContinuationIdentity(source.store.identity + ":native-repair:" + source.basis.next.revision.value)
  private def _namespace(source: Source): String = "repair-" + source.basis.next.revision.value
}
