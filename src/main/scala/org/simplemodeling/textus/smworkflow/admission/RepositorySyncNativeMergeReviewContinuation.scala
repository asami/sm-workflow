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
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncNativeMergeReviewContinuation {
  val completionOperation = StateMachineOperationIdentity("RepositorySyncService", "submitNativeMergeReview")
  val closingType = StateMachineResultTypeReference("RepositorySyncNativeMergeReviewSubmissionResult")
  final case class SavedReview(validated: N.Stage, work: WorkflowInteraction[I.Request, Nothing],
    input: I.Request, result: ContinuationResult[S.Result], next: InstanceRecord, current: InstanceRecord)
  private final case class Source(session: F.Session, store: N.Store, validated: N.Stage, current: InstanceRecord)
  private final case class Plan(source: Source, input: I.Request,
    intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent, next: InstanceRecord,
    work: WorkflowInteraction[I.Request, Nothing])

  final class Port private[RepositorySyncNativeMergeReviewContinuation] (
    declaration: F.Declaration, definition: DefinitionBinding, policy: I.Policy,
    acknowledgement: String => Consequence[Unit], evidence: S.EvidencePort,
    assessment: Option[RepositorySyncMergeAssessment.Policy]) {

    def advanceC(identity: String, core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
      source <- sourceC(identity, core)
      mode <- _mode_c(source)
      native <- source.session.storage.bindIssuedC(completionOperation, S.inputCodec)
      existing <- native.loadC(continuationId(source.validated, mode))(using core.executionContext)
      work <- existing match {
        case Some(work) => Consequence.success(work)
        case None => for {
          captured <- I.captureC(source.store, source.validated, policy)
          signals <- _uncertainties_c(source)
          input = captured.copy(judgmentMode = mode, uncertainties = signals)
          projected <- prepareC(source, input)
          selectedWork <- RepositorySyncNativeExecution.bindIssuedC(projected.work, RepositorySyncNativeExecution.Work.Review,
            declaration.executionClassifications, declaration.executionProfiles, declaration.executionOrigin)
          selected = projected.copy(work = selectedWork)
          saved <- native.suspendC(selected.intent, selected.work)(using core.executionContext)
        } yield selected.work
      }
      completion <- nativeCompletionC(source.session.storage)
      retained <- completion.loadRetainedC(continuationId(source.validated, mode))(using core.executionContext)
      wire <- retained match {
        case None => NativeWorkflowOperationOutput.repositoryReviewRequestC(work, core)
        case Some(value) => for {
          next <- _retained_next_c(source.current, value.completion)
          wire <- waitC(next, value.completion.result)
        } yield wire
      }
      journal <- assessment match {
        case None => Consequence.success(None)
        case Some(policy) => for {
          selected <- RepositorySyncMergeAssessment.captureC(source.store, source.validated, policy)
          prior <- _saved_c(source, "triage")
          observed = prior.fold(selected)(value => selected.copy(branch = "detailed-review",
            reason = "Actual independent triage " + value.result.result.value.decision + ": " + value.result.result.value.rationale,
            triageReference = Some(value.result.resultReference)))
          recorded <- DevelopmentJournalMergeAssessment.recordC(source.store,
            source.validated.payload.copy(assessment = Some(observed)), source.current)
        } yield recorded
      }
    } yield wire.copy(journalRecording = journal)

    /** Observe only the actual paired committed acceptance. This is not a
      * caller-selected disposition and does not itself grant any Git effect.
      * Capture consumes native review context; current Git effects belong to
      * the convergence owner, not an eager completion/readback precheck.
      */
    def savedReviewC(identity: String, core: ActionCall.Core): Consequence[Option[SavedReview]] = for {
      session <- F.sessionC(identity, declaration, definition, core)
      store <- N.bindC(session, declaration, core, acknowledgement)
      candidate <- if (session.started.current.history.exists(_.currentProgression.contains(ProgressionReference("sync.PendingMergePrepared"))))
        store.currentMergeCandidateC(session.started.current).map(Some(_)) else Consequence.success(None)
      validated <- candidate match {
        case Some(value) => store.loadC(N.candidateKey(value.namespace, "merge-validated"))
        case None => Consequence.success(None)
      }
      saved <- validated match {
        case None => Consequence.success(None)
        case Some(value) => for {
          source <- _source_from_loaded_c(session, store, value, core)
          reviewed <- _saved_c(source, "detailed-review")
          selected <- reviewed match {
            case Some(actual) => Consequence.success(Some(actual))
            case None => _saved_c(source, "triage")
          }
        } yield selected
      }
    } yield saved

    private def _saved_c(source: Source, mode: String): Consequence[Option[SavedReview]] = for {
      issued <- source.session.storage.bindIssuedC(completionOperation, S.inputCodec)
      work <- issued.loadC(continuationId(source.validated, mode))(using source.store.core.executionContext)
      completion <- nativeCompletionC(source.session.storage)
      retained <- completion.loadRetainedC(continuationId(source.validated, mode))(using source.store.core.executionContext)
      result <- retained match {
        case Some(value) => for {
          actual <- work.map(Consequence.success).getOrElse(invalid("completed judgment has no actual paired issue"))
          input <- inputC(actual)
          _ <- if (input.judgmentMode == mode) Consequence.unit else invalid("retained judgment mode crosses its issue")
          next <- _retained_next_c(source.current, value.completion)
        } yield Some(SavedReview(source.validated, actual, input, value.completion.result, next, source.current))
        case None => Consequence.success(None)
      }
    } yield result

    private def _mode_c(source: Source): Consequence[String] = assessment match {
      case None => Consequence.success("detailed-review")
      case Some(policy) => for {
        selected <- RepositorySyncMergeAssessment.captureC(source.store, source.validated, policy)
        prior <- if (selected.branch == "semantic-uncertainty") _saved_c(source, "triage")
          else Consequence.success(None)
      } yield if (selected.branch == "semantic-uncertainty" && !prior.exists(value =>
        Set("suspicious", "decline")(value.result.result.value.decision))) "triage" else "detailed-review"
    }
    private def _uncertainties_c(source: Source): Consequence[Vector[String]] = assessment match {
      case None => Consequence.success(Vector.empty)
      case Some(policy) => RepositorySyncMergeAssessment.captureC(source.store, source.validated, policy).map(value =>
        value.overlappingPaths.map("Actual parent overlap: " + _) ++ value.contractPaths.map("Selected contract path: " + _))
    }

    def acceptedC(identity: String, core: ActionCall.Core): Consequence[Option[N.Stage]] =
      savedReviewC(identity, core).flatMap {
        case Some(value) if value.input.judgmentMode == "detailed-review" && value.result.result.value.decision == "accept" =>
          Consequence.success(Some(N.Stage(value.next, value.validated.payload.copy(stage = "ReviewAccepted"))))
        case Some(value) if value.input.judgmentMode == "triage" && value.result.result.value.decision == "clean" => for {
          policy <- assessment.map(Consequence.success).getOrElse(invalid("triage admission has no selected policy"))
          session <- F.sessionC(identity, declaration, definition, core)
          store <- N.bindC(session, declaration, core, acknowledgement)
          candidate <- store.currentMergeCandidateC(session.started.current)
          admitted <- RepositorySyncMergeAssessment.admitTriagedC(store, value.validated, value.next, policy,
            candidate.namespace, value.result.resultReference)
        } yield Some(admitted)
        case _ => Consequence.success(None)
      }

    private def sourceC(identity: String, core: ActionCall.Core): Consequence[Source] = for {
      session <- F.sessionC(identity, declaration, definition, core)
      store <- N.bindC(session, declaration, core, acknowledgement)
      candidate <- store.currentMergeCandidateC(session.started.current)
      validated <- store.loadC(N.candidateKey(candidate.namespace, "merge-validated")).flatMap {
        case Some(value) => Consequence.success(value)
        case _ => invalid("semantic review selection has no genuine native review")
      }
      source <- _source_from_loaded_c(session, store, validated, core)
    } yield source

    private def _source_from_loaded_c(session: F.Session, store: N.Store, validated: N.Stage,
      core: ActionCall.Core): Consequence[Source] = for {
      _ <- if (core != null && core.action != null && core.action.request != null &&
          core.action.request.service.contains(completionOperation.service) &&
          Set(F.advanceOperation.operation, completionOperation.operation,
            RepositorySyncNativeMergeRepairContinuation.completionOperation.operation)(core.action.request.operation)) Consequence.unit
        else invalid("review continuation requires its actual owning Operation")
      _ <- if (validated.payload.stage == "MergeValidated") Consequence.unit
        else invalid("semantic review selection has no genuine native review")
      current = session.started.current
    } yield Source(session, store, validated, current)

    private def prepareC(source: Source, input: I.Request): Consequence[Plan] = for {
      required <- RepositorySyncDefinitionBinding.nativeReviewOperationC
      base = source.validated.record
      escalated = source.current.history.reverse.find(entry => entry.revision.value > base.revision.value &&
        entry.currentProgression.contains(ProgressionReference("sync.MergeReviewRequired")) && entry.suspension.isEmpty)
      from = if (input.judgmentMode != "detailed-review" || escalated.isEmpty) base else {
        val entry = escalated.get
        source.current.copy(revision = entry.revision, lifecycle = entry.lifecycle,
          currentProgression = entry.currentProgression, correlation = entry.correlation, causation = entry.causation,
          derivedCompositeOccurrence = entry.derivedCompositeOccurrence, committedPredecessor = entry.committedPredecessor,
          suspension = entry.suspension, history = source.current.history.takeWhile(_.revision.value <= entry.revision.value))
      }
      bundle = ContextBundle(if (input.judgmentMode == "triage")
        "Triage only the captured merge uncertainties using actual candidate and successful selected checks; return clean, suspicious or decline without findings. Tool failure is not semantic decline."
        else "Independently review the pending merge against both parents and actual validation. When priorReview is supplied, retain its applicable unaffected judgments and assess its original findings, actual repairDelta and affected meaning; do not invent fresh PASS results for retained conclusions.",
        Vector(ContextReference("native-review:" + from.identity.value, input.validatedRevision)), Vector.empty,
        ContextSnapshot(from.definition.workflowRevision.value, evidenceRevision = Some(input.validatedRevision)))
      continuation = Continuation(StateMachineRunIdentity(from.identity.value), continuationId(source.validated, input.judgmentMode),
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
          ReasoningLevel.Standard, true), MinimalPresentation(if (input.judgmentMode == "triage") "Independent bounded merge triage" else "Independent native merge review",
          "Return only the assigned semantic review. Workflow owns result admission and all repository effects.")))
    } yield Plan(source, input, intent, next, work)

    private[RepositorySyncNativeMergeReviewContinuation] def operationC(core: Option[ActionCall.Core], native: WorkflowManagedCompletion[S.Result],
      result: Option[ContinuationResult[NativeWorkflowResultInput.Value]] = None):
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
        def decodeC(request: ProtocolRequest) = result.map(NativeWorkflowResultInput.typedC(_, S.resultCodec))
          .getOrElse(invalid("review completion has no resolved semantic result"))
        def encodeC(resume: ContinuationRuntime.Resume) = invalid("completion acknowledgement requires actual native commit")
        override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, submitted: ContinuationResult[S.Result],
          completed: Option[InstanceRecord]): Consequence[OperationResponse] =
          completed.map(record => Consequence.success(NativeWorkflowOperationOutput.response(NativeWorkflowOperationOutput.accepted(submitted, record))))
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
            plan = projected.copy(work = work)
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
      WorkflowCompletionServiceOperation.bindC(provided, op.get, SmWorkflowComponent.RepositorySyncServiceService.SubmitNativeMergeReviewOperation.specification.request, SmWorkflowComponent.RepositorySyncServiceService.SubmitNativeMergeReviewOperation.specification.response,
        codec, ContinuationRuntime.empty, issued, actualInstances, declaration.configuration,
        () => throw new IllegalStateException("Review completion requires canonical managed UoW"), Some(closing), Some(native),
        descriptiveAttributes = SmWorkflowComponent.RepositorySyncServiceService.SubmitNativeMergeReviewOperation.specification.content.descriptiveAttributes)
    }
  }

  def bindC(declaration: F.Declaration, definition: DefinitionBinding, installation: S.Installation,
    acknowledgement: String => Consequence[Unit] = _ => Consequence.unit,
    assessment: Option[RepositorySyncMergeAssessment.Policy] = None): Consequence[Port] = for {
    _ <- F.validateDeclarationC(declaration)
    _ <- if (installation != null) installation.validateC else invalid("review installation is absent")
    _ <- if (definition != null) Consequence.unit else invalid("review definition is absent")
  } yield new Port(declaration, definition, installation.policy, acknowledgement, installation.evidence, assessment)

  def bindCompletionC(params: ComponentCreate, declaration: F.Declaration,
    definition: DefinitionBinding, installation: S.Installation,
    assessment: Option[RepositorySyncMergeAssessment.Policy] = None): Consequence[spec.OperationDefinition] = for {
    port <- bindC(declaration, definition, installation, assessment = assessment)
    storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
      Vector(definition), declaration.instances, declaration.continuations)
    native <- nativeCompletionC(storage)
    declared <- port.operationC(None, native)
  } yield CallScopedOperationBinding.wrapResult(declared, (core, result) => F.bindC(declaration, definition, core)
    .flatMap(nativeCompletionC).flatMap(actual => port.operationC(Some(core), actual, Some(result))))

  private def completedC(plan: Plan, submitted: ContinuationResult[S.Result], policy: I.Policy): Consequence[InstanceRecord] = for {
    _ <- S.admitC(plan.input, submitted.result.value, policy)
    current = plan.next
    stage = submitted.result.value.decision match {
      case "clean" => "MergeTriageClean"
      case "suspicious" | "decline" => "MergeReviewRequired"
      case "accept" => "ReviewAccepted"
      case "repair" => "ReviewRepairRequired"
      case "decision-needed" => "ReviewDecisionRequired"
      case "reject" => "ReviewRejected"
    }
    entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1), current.revision.next, Lifecycle.Active,
      Some(ProgressionReference("sync." + stage)), current.correlation, CausationReference(submitted.resultReference.identity),
      current.derivedCompositeOccurrence, current.committedPredecessor, None)
    next <- current.appendC(current.revision, entry)
  } yield next
  private def nativeCompletionC(storage: WorkflowComponentStorage) = storage.bindCompletionC(completionOperation,
    S.inputCodec, S.resultCodec, closingType, (_, _) => Consequence.success(Vector.empty))
  private def _retained_next_c(current: InstanceRecord, completion: WorkflowCompletionRecordJsonV1.Record[S.Result]): Consequence[InstanceRecord] = {
    val stage = completion.result.result.value.decision match {
      case "clean" => "MergeTriageClean"
      case "suspicious" | "decline" => "MergeReviewRequired"
      case "accept" => "ReviewAccepted"
      case "repair" => "ReviewRepairRequired"
      case "decision-needed" => "ReviewDecisionRequired"
      case "reject" => "ReviewRejected"
      case _ => return invalid("retained review decision is unknown")
    }
    N.completionNextC(current, completion.instanceRevision, completion.result.resultReference, stage)
  }
  private def inputC(work: WorkflowInteraction[I.Request, Nothing]): Consequence[I.Request] = work.current match {
    case order: WorkflowContinuation.WorkOrder[I.Request] =>
      order.request.input.map(value => Consequence.success(value.value))
        .getOrElse(invalid("issued review work has no assigned native input"))
    case _ => invalid("issued review work has no assigned native input")
  }
  private def continuationId(source: N.Stage, mode: String = "detailed-review"): ContinuationIdentity =
    ContinuationIdentity(source.record.identity.value + (if (mode == "triage") ":native-triage:" else ":native-review:") + source.record.revision.value)
  private def waitC(next: InstanceRecord, result: ContinuationResult[S.Result]): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] =
    Consequence.success(NativeWorkflowOperationOutput.repositoryWait(next.identity.value,
      "The independent review is saved. Repair, Human decision or authorized final convergence remains incomplete.",
      result.result.value.decision))
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync native review continuation " + reason)
  private def uncertain[A](reason: String): Consequence[A] =
    org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
}
