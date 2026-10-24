package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.{WorkflowInstancePersistence, ContextReference}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ContinuationResult, WorkflowInteraction, WorkflowContinuation}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.RecordPresentable
import org.simplemodeling.textus.smworkflow.value as V

/** Concrete public business output projections from their actual owning
  * outcome. No extra store read, result verifier or transport envelope.
  * @since   Oct. 12, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeWorkflowOperationOutput {
  import SplitPhaseExplicitProposalPrecheck as P
  import SplitPhaseExplicitBoundaryValidation as B
  import SplitPhaseExplicitPartitionSemantic as S
  import SplitPhasePlanningSnapshot as R

  type SubmissionValue = V.GoalPhaseWorkSubmissionResult | V.GoalPhaseOrderedDecisionSubmissionResult |
    V.GoalPhaseFindingRecordingSubmissionResult |
    V.SplitPhaseSemanticSubmissionResult | V.SplitPhaseScopeSubmissionResult | V.SplitPhaseReasoningSubmissionResult |
    V.SplitPhaseHumanDirectionSubmissionResult | V.RepositorySyncConflictSubmissionResult |
    V.RepositorySyncNativeMergeReviewSubmissionResult | V.RepositorySyncHumanConflictSubmissionResult |
    V.RepositorySyncNativeMergeRepairSubmissionResult

  /** Existing structured protocol egress. Domain DTOs are not primitive
    * scalars; carry their declared business record without a String envelope. */
  def response(value: RecordPresentable): OperationResponse =
    OperationResponse.RecordResponse(value.toRecord())

  def goalWork(value: GoalPhaseNativeWork.Request, reviewContext: Option[V.WorkflowReviewContext] = None): V.GoalPhaseWorkView =
    V.GoalPhaseWorkView(value.kind, value.purpose, value.stepIdentity, _goal_plan(value.selectedPlan),
      _goal_snapshot(value.originalBase), value.completed.map(_goal_closed), value.candidate.map(_goal_snapshot),
      value.checksReceipt, value.repairDirective.map(d => V.GoalPhaseRepairDirective(d.reason, d.findings)),
      value.review.map(r => V.GoalPhaseNativeReviewContext(r.changedPaths, r.diff, r.checks.map(_goal_check), reviewContext,
        r.metadata.map(batch => GoalPhaseNativeMetadata.record(batch.asJson)),
        r.priorReviews.map(prior => GoalPhaseNativeMetadata.record(prior.asJson)),
        r.triageJudgment.map(j => GoalPhaseNativeMetadata.record(j.asJson)))),
      value.decisionContext.map(d => V.GoalPhaseOrderedDecisionContext(_goal_result(d.requestingResult), d.choice, d.rationale,
        d.decisionReference.map(reference => V.WorkflowArtifactReference(reference.identity, reference.revision)))),
      value.checkFailure.map(c => V.GoalPhaseCheckFailureContext(c.changedPaths, c.diff, goalValidation(c.validation))),
      GoalPhaseFixIssues.publicState(value.issues), value.purposeRepair.map(contract => GoalPhaseNativeMetadata.record(contract.asJson)),
      value.triage.map(t => GoalPhaseNativeMetadata.record(t.asJson)))

  def goalValidation(payload: GoalPhaseNativeValidation.Payload): V.GoalPhaseNativeValidationView = {
    val stopped = if (payload.outcome.isEmpty) payload.stopped.lastOption.filter(receipt =>
      payload.selected.lift(payload.receipts.size).exists(_.identity == receipt.identity)).toVector else Vector.empty
    V.GoalPhaseNativeValidationView(payload.sourceRevision, payload.step, _goal_snapshot(payload.candidate),
      payload.selected.map(c => V.GoalPhaseNativeValidationCheck(c.identity, c.kind.wire, c.hostIdentity,
        c.operation.map { operation =>
          import GoalPhaseNativeMetadata.given
          GoalPhaseNativeMetadata.record(operation.asJson)
        })), (payload.receipts ++ stopped).map(_goal_check), payload.outcome,
      payload.metadata.map(batch => GoalPhaseNativeMetadata.record(batch.asJson)), Vector.empty,
      payload.assessment.map(a => GoalPhaseNativeMetadata.record(a.asJson)), None)
  }

  def goalWorkRequestC(work: WorkflowInteraction[GoalPhaseNativeWork.Request, Nothing], core: ActionCall.Core): Consequence[V.GoalPhaseAdvanceResult] = for {
    value <- workInputC(work)
    _ <- if (value != null) Consequence.unit else Consequence.stateConflict("Issued Goal work has no business input")
    progress <- GoalPhaseRepairProgress.publicOptionC(value.repairProgress)
    mode <- NativeWorkflowIssuedWorkContext.goalModeC(value.kind)
    issuedContext <- NativeWorkflowIssuedWorkContext.fromIssuedC(work, mode, GoalPhaseNativeWork.inputCodec)
    reviewContext <- NativeReviewContext.goalC(core, work)
  } yield V.GoalPhaseAdvanceResult(work.handle.instanceIdentity.value, "Request", value.purpose,
      Some(GoalPhaseNativeRun.completionOperation.operation), Some(work.handle.instanceIdentity.value), Some("GoalPhaseWorkResult"),
      None, None, Some(goalWork(value, reviewContext)), None, None, None, None, None, None, Some(issuedContext), None, None, progress, None)

  def goalHumanC(question: WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing], core: ActionCall.Core):
    Consequence[V.GoalPhaseAdvanceResult] = for {
    value <- decisionInputC(question)
    _ <- if (value != null && value.work != null) Consequence.unit
      else Consequence.stateConflict("Issued Goal Human has no business input")
    progress <- GoalPhaseRepairProgress.publicOptionC(value.repairProgress)
    _ <- GoalPhaseRepairProgress.validateOptionC(value.work.repairProgress)
    context <- NativeHumanDecisionContext.goalC(core, question)
    choices <- GoalPhaseOrderedHumanDecision.issuedChoicesC(question)
    _ <- if (value != null && value.work != null && value.result != null && value.work.completed != null &&
        value.work.review != null && value.work.decisionContext != null && value.work.repairDirective != null &&
        value.work.checkFailure != null) Consequence.unit
      else Consequence.stateConflict("Issued Goal Human has no business display input")
  } yield V.GoalPhaseAdvanceResult(question.handle.instanceIdentity.value, "Human", value.reason,
      Some(GoalPhaseOrderedHumanDecision.completionOperation.operation), Some(question.handle.instanceIdentity.value),
      Some("GoalPhaseOrderedDecisionAnswer"), None, None, None,
      Some(V.GoalPhaseHumanQuestion(goalWork(value.work), _goal_result(value.result), value.reason,
        choices, Some(context), GoalPhasePlanRevision.publicCaptured(value.planRevision),
        value.sourceHistoryRecovery.map(GoalPhaseSourceHistoryRecovery.targetRecord))), None, None, None, None, None, None, None, None, progress, None)

  def goalWait(session: String, message: String, condition: String): V.GoalPhaseAdvanceResult =
    V.GoalPhaseAdvanceResult(session, "Wait", message, None, None, None, Some(condition), None,
      None, None, None, None, None, None, None, None, None, None, None, None)

  def goalChild(session: String, child: V.RepositorySyncAdvanceResult): V.GoalPhaseAdvanceResult =
    V.GoalPhaseAdvanceResult(session, child.kind, child.message, child.submitOperation, child.submitSessionId,
      child.resultType, child.condition, child.outcome, None, None, child.conflict, child.review, child.human, None, None,
      child.issuedWorkContext, child.repair, None, None, None)

  def goalComplete(session: String, plan: GoalPhaseOrderedProgression.Plan, base: GoalPhaseOrderedProgression.Snapshot,
    completed: Vector[GoalPhaseOrderedProgression.ClosedStep], candidate: GoalPhaseOrderedProgression.Snapshot,
    repository: V.RepositorySyncNativeConvergenceResult): V.GoalPhaseAdvanceResult =
    V.GoalPhaseAdvanceResult(session, "Terminal", "The ordered Phase and repository synchronization completed.",
      None, None, None, None, Some("completed"), None, None, None, None, None,
      Some(V.GoalPhaseCompletionResult(_goal_plan(plan), _goal_snapshot(base), completed.map(_goal_closed),
        _goal_snapshot(candidate), Some(repository), V.GoalPhaseSynchronizationResult("succeeded",
          "Actual RepositorySync convergence and native completion are retained.", None, None))), None, None, None, None, None, None)

  def goalLocallyComplete(session: String, plan: GoalPhaseOrderedProgression.Plan, base: GoalPhaseOrderedProgression.Snapshot,
    completed: Vector[GoalPhaseOrderedProgression.ClosedStep], candidate: GoalPhaseOrderedProgression.Snapshot,
    synchronization: GoalPhasePostCompletionSync.Synchronization,
    repository: Option[V.RepositorySyncNativeConvergenceResult]): V.GoalPhaseAdvanceResult =
    V.GoalPhaseAdvanceResult(session, "Terminal", "The ordered Phase completed. Repository synchronization: " +
      synchronization.state + ". " + synchronization.reason, None, None, None, None, Some("completed"),
      None, None, None, None, None, Some(V.GoalPhaseCompletionResult(_goal_plan(plan), _goal_snapshot(base),
        completed.map(_goal_closed), _goal_snapshot(candidate), repository,
        V.GoalPhaseSynchronizationResult(synchronization.state, synchronization.reason,
          synchronization.childSessionId, synchronization.advanceOperation))), None, None, None, None, None, None)

  def goalRecordingResult(value: GoalPhaseFindingRecording.Result): V.GoalPhaseFindingRecordingResult =
    V.GoalPhaseFindingRecordingResult(value.outcome, value.summary, value.items.map(item =>
      V.GoalPhaseFindingRecordingItemResult(V.WorkflowArtifactReference(item.sourceReference.identity, item.sourceReference.revision),
        item.identity, item.outcome, item.records.map(r => V.GoalPhaseFindingSavedSection(r.file, r.section)), item.rationale)))

  def goalRecordingRequest(value: GoalPhaseFindingRecording.Request): V.GoalPhaseFindingRecordingRequest =
    V.GoalPhaseFindingRecordingRequest(value.instanceIdentity, value.sourceRevision, value.batchIdentity,
      _goal_plan(value.selectedPlan), _goal_snapshot(value.originalBase), value.completed.map(_goal_closed), value.stepIdentity,
      value.items.map(item => V.GoalPhaseFindingRecordingItem(
        V.WorkflowArtifactReference(item.sourceReference.identity, item.sourceReference.revision), item.sourceKind, item.stepIdentity,
        V.GoalPhaseReviewObservation(item.observation.identity, item.observation.category, item.observation.summary, item.observation.rationale),
        item.destination)), GoalPhaseFixIssues.publicState(value.requiredIssues), value.repairProgress.map(GoalPhaseRepairProgress.publicSnapshot))

  def goalFindingRecording(state: GoalPhaseNativeFindingRecording.State,
    request: Option[GoalPhaseFindingRecording.Request] = None): Option[V.GoalPhaseFindingRecordingState] =
    Option.when(state.pending.nonEmpty || state.completed.nonEmpty || state.journaled.nonEmpty || request.nonEmpty)(V.GoalPhaseFindingRecordingState(
      state.pending.map(item => V.GoalPhaseFindingObservationSource(
        V.WorkflowArtifactReference(item.sourceReference.identity, item.sourceReference.revision), item.sourceKind, item.stepIdentity,
        V.GoalPhaseReviewObservation(item.observation.identity, item.observation.category, item.observation.summary, item.observation.rationale))),
      request.map(goalRecordingRequest), state.completed.map(value => V.GoalPhaseFindingRecordingCompleted(goalRecordingRequest(value.request),
        V.WorkflowArtifactReference(value.resultReference.identity, value.resultReference.revision), goalRecordingResult(value.result))),
      state.journaled.map(value => V.JournalFindingBatch(value.sourceRevision, value.committedRevision,
        value.pending.map(item => V.GoalPhaseFindingObservationSource(
          V.WorkflowArtifactReference(item.sourceReference.identity, item.sourceReference.revision), item.sourceKind, item.stepIdentity,
          V.GoalPhaseReviewObservation(item.observation.identity, item.observation.category, item.observation.summary, item.observation.rationale))),
        value.outcomes.map(_.publicValue)))))

  def withFindingRecording(value: V.GoalPhaseAdvanceResult, state: GoalPhaseNativeFindingRecording.State): V.GoalPhaseAdvanceResult =
    value.copy(findingRecording = goalFindingRecording(state))

  def goalRecordingRequestC(work: WorkflowInteraction[GoalPhaseFindingRecording.Request, Nothing],
    state: GoalPhaseNativeFindingRecording.State): Consequence[V.GoalPhaseAdvanceResult] = for {
    request <- workInputC(work)
    issued <- NativeWorkflowIssuedWorkContext.fromIssuedC(work, NativeWorkflowIssuedWorkContext.Mode.Implementation,
      GoalPhaseFindingRecording.inputCodec)
  } yield goalWait(work.handle.instanceIdentity.value, "Record the finite assigned review observations in their exact Markdown destinations.",
    "phase.finding-recording").copy(kind = "Request", condition = None,
      submitOperation = Some(GoalPhaseNativeFindingRecording.completionOperation.operation),
      submitSessionId = Some(work.handle.instanceIdentity.value), resultType = Some(GoalPhaseFindingRecording.resultCodec.typeIdentity),
      issuedWorkContext = Some(issued), repairProgress = request.repairProgress.map(GoalPhaseRepairProgress.publicSnapshot),
      findingRecording = goalFindingRecording(state, Some(request)))

  def repositoryConflict(value: RepositorySyncNativeConflictInput.Request): V.RepositorySyncConflictWorkView =
    V.RepositorySyncConflictWorkView(value.localTip, value.remoteTip, value.conflicts.map(c =>
      V.RepositorySyncNativeConflict(c.identity, c.path, V.RepositorySyncConflictBlob(c.base.identity, c.base.text),
        V.RepositorySyncConflictBlob(c.local.identity, c.local.text), V.RepositorySyncConflictBlob(c.remote.identity, c.remote.text),
        c.mode, c.worktree)))

  def repositoryConflictRequestC(work: WorkflowInteraction[RepositorySyncNativeConflictInput.Request, Nothing]):
    Consequence[V.RepositorySyncAdvanceResult] = for {
    value <- workInputC(work)
    issuedContext <- NativeWorkflowIssuedWorkContext.fromIssuedC(work, NativeWorkflowIssuedWorkContext.Mode.Implementation,
      RepositorySyncConflictSemantic.inputCodec)
  } yield V.RepositorySyncAdvanceResult(work.handle.instanceIdentity.value, "Request", "Resolve only the selected repository conflicts.",
        Some(RepositorySyncNativeConflictContinuation.completionOperation.operation), Some(work.handle.instanceIdentity.value),
        Some("RepositorySyncConflictResult"), None, None, Some(repositoryConflict(value)), None, None, None, Some(issuedContext), None, None)

  def repositoryReviewRequestC(work: WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing], core: ActionCall.Core):
    Consequence[V.RepositorySyncAdvanceResult] = for {
    value <- workInputC(work)
    issuedContext <- NativeWorkflowIssuedWorkContext.fromIssuedC(work,
      if (value.judgmentMode == "triage") NativeWorkflowIssuedWorkContext.Mode.Analysis else NativeWorkflowIssuedWorkContext.Mode.Review,
      RepositorySyncNativeMergeReview.inputCodec)
    reviewContext <- NativeReviewContext.repositoryC(core, work)
    receipts <- value.checks.foldLeft(Consequence.success(Vector.empty[V.RepositorySyncNativeValidationReceipt])) { (prior, receipt) => for {
      values <- prior
      code <- receipt.exitCode.map(Consequence.success).getOrElse(
        Consequence.stateConflict("Merge review has no completed check exit status"))
    } yield values :+ V.RepositorySyncNativeValidationReceipt(receipt.identity, receipt.termination, code, receipt.stdout, receipt.stderr) }
    view = V.RepositorySyncMergeReviewWorkView(value.localTip, value.remoteTip, value.indexTree, value.changedPaths,
      value.localDiff, value.remoteDiff, value.validationMode, value.validationReason, value.selectedChecks, receipts, Some(reviewContext),
      if (value.judgmentMode == "triage") Some("triage") else None, value.uncertainties, value.priorReview.map(p => GoalPhaseNativeMetadata.record(
        RepositorySyncNativeMergeReview.inputCodec.encode(value).hcursor.downField("payload").downField("priorReview").focus.get)))
  } yield V.RepositorySyncAdvanceResult(work.handle.instanceIdentity.value, "Request",
    if (value.judgmentMode == "triage") "Independently triage only the captured merge uncertainties; return clean, suspicious or decline without review findings."
      else "Independently review the selected merge.",
    Some(RepositorySyncNativeMergeReviewContinuation.completionOperation.operation), Some(work.handle.instanceIdentity.value),
    Some("RepositorySyncNativeMergeReviewResult"), None, None, None, Some(view), None, None, Some(issuedContext), None, None)

  def repositoryRepairRequestC(work: WorkflowInteraction[RepositorySyncNativeMergeRepair.Request, Nothing]):
    Consequence[V.RepositorySyncAdvanceResult] = for {
    value <- workInputC(work)
    issuedContext <- NativeWorkflowIssuedWorkContext.fromIssuedC(work, NativeWorkflowIssuedWorkContext.Mode.Implementation,
      RepositorySyncNativeMergeRepair.inputCodec)
    receipts <- value.review.checks.foldLeft(Consequence.success(Vector.empty[V.RepositorySyncNativeValidationReceipt])) { (prior, receipt) => for {
      values <- prior
      code <- receipt.exitCode.map(Consequence.success).getOrElse(Consequence.stateConflict("Repair source has no completed check status"))
    } yield values :+ V.RepositorySyncNativeValidationReceipt(receipt.identity, receipt.termination, code, receipt.stdout, receipt.stderr) }
    review = value.review
    input = V.RepositorySyncNativeMergeRepairRequest(V.RepositorySyncNativeMergeReviewRequest(review.instanceIdentity,
      review.validatedRevision, review.localTip, review.remoteTip, review.indexTree, review.changedPaths, review.localDiff,
      review.remoteDiff, review.validationMode, review.validationReason, review.selectedChecks, receipts),
      value.findings.map(f => V.RepositorySyncNativeMergeReviewFinding(f.identity, f.affectedPaths, f.rationale)),
      value.files.map(f => V.RepositorySyncNativeMergeRepairFile(f.path, f.text)), value.validationFailure)
  } yield V.RepositorySyncAdvanceResult(work.handle.instanceIdentity.value, "Request",
    if (value.validationFailure.nonEmpty) "Repair only the actual failed selected merge check within the assigned files."
      else "Repair only the assigned native merge findings.",
    Some(RepositorySyncNativeMergeRepairContinuation.completionOperation.operation), Some(work.handle.instanceIdentity.value),
    Some("RepositorySyncNativeMergeRepairResult"), None, None, None, None, None, None, Some(issuedContext), Some(input), None)

  def repositoryHumanC(question: WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing], core: ActionCall.Core):
    Consequence[V.RepositorySyncAdvanceResult] = for {
    context <- NativeHumanDecisionContext.repositoryC(core, question)
    value <- decisionInputC(question)
  } yield V.RepositorySyncAdvanceResult(question.handle.instanceIdentity.value, "Human", value.rationale,
      Some(RepositorySyncHumanConflict.completionOperation.operation), Some(question.handle.instanceIdentity.value),
      Some("RepositorySyncHumanConflictAnswer"), None, None, None, None,
      Some(V.RepositorySyncHumanQuestion(repositoryConflict(value.conflict), value.rationale,
        RepositorySyncHumanConflict.choices, Some(context))), None, None, None, None)

  def repositoryWait(session: String, message: String, condition: String): V.RepositorySyncAdvanceResult =
    V.RepositorySyncAdvanceResult(session, "Wait", message, None, None, None, Some(condition), None, None, None, None, None, None, None, None)

  def repositoryConvergence(value: RepositorySyncNativeConvergence.Result): V.RepositorySyncNativeConvergenceResult =
    V.RepositorySyncNativeConvergenceResult(value.repositoryRoot, value.localBranch, value.remote, value.remoteBranch, value.tip, value.tree)

  def repositoryComplete(session: String, value: RepositorySyncNativeConvergence.Result): V.RepositorySyncAdvanceResult =
    V.RepositorySyncAdvanceResult(session, "Terminal", "Actual local, tracking and remote tips converged with a clean repository.",
      None, None, None, None, Some("converged"), None, None, None, Some(repositoryConvergence(value)), None, None, None)

  private def _goal_plan(value: GoalPhaseOrderedProgression.Plan): V.GoalPhaseOrderedPlan =
    V.GoalPhaseOrderedPlan(value.identity, value.revision, value.phase, value.repository,
      value.steps.map(s => V.GoalPhaseOrderedStep(s.identity, s.purpose, s.ownedPaths, s.dependencies, s.checks,
        s.actionPolicy.map(p => GoalPhaseNativeMetadata.record(p.asJson)))),
      value.phaseChecks, value.repairLimit, GoalPhaseAcceptanceBoundary.publicAdoption(value.acceptance),
      GoalPhaseAcceptanceBoundary.publicEffective(value.effectiveAcceptance))
  private def _goal_snapshot(value: GoalPhaseOrderedProgression.Snapshot): V.GoalPhaseNativeSnapshot =
    V.GoalPhaseNativeSnapshot(value.head, value.tree)
  private def _goal_check(value: RepositorySyncNativeMergeValidation.Receipt): V.GoalPhaseNativeCheckReceipt =
    V.GoalPhaseNativeCheckReceipt(value.identity, value.termination, value.exitCode, value.stdout, value.stderr,
      value.sbt.map(_sbt_facts))
  private def _sbt_facts(value: WorkflowSbtValidation.Facts): V.WorkflowSbtValidationFacts =
    V.WorkflowSbtValidationFacts(value.kind.wire, value.outcome.wire,
      value.selection.map(s => V.WorkflowSbtTestSelection(s.testClasses, s.completedSuites, s.testsRun)), value.reason)
  private def _goal_closed(value: GoalPhaseOrderedProgression.ClosedStep): V.GoalPhaseClosedStep =
    V.GoalPhaseClosedStep(value.step, V.GoalPhaseNativeCommit(value.commit.identity, value.commit.parent, value.commit.tree),
      value.review, value.admission.map(a => GoalPhaseNativeMetadata.record(a.asJson)))
  private def _goal_result(value: GoalPhaseNativeWork.Result): V.GoalPhaseWorkResult =
    V.GoalPhaseWorkResult(value.outcome, value.summary, value.findings, GoalPhaseAcceptanceBoundary.publicReport(value.acceptance),
      GoalPhaseFixAssessment.publicReport(value.fixAssessment), GoalPhaseFixIssues.publicReport(value.issueReport),
      GoalPhasePlanRevision.publicProposal(value.planRevision), GoalPhaseFindingRecording.publicObservations(value.reviewObservations),
      GoalPhaseDependencyScope.publicProposal(value.dependencyProposal),
      value.completionConditions.map(conditions => GoalPhaseNativeMetadata.record(conditions.asJson)),
      GoalPhasePurposeRepair.publicSources(value.repairFindingSources))

  /** Called only by the owning completion's after-commit projection. The
    * native closing result remains its real type/reference pair; it is not
    * an empty business DTO or a substitute for this public result. */
  def accepted[A <: NativeWorkflowResultInput.Value](submitted: ContinuationResult[A],
    completed: WorkflowInstancePersistence.InstanceRecord,
    journalRecording: Option[V.JournalRecordingState] = None): SubmissionValue = {
    val session = completed.identity.value
    submitted.result.value match {
      case value: GoalPhaseNativeWork.Result =>
        V.GoalPhaseWorkSubmissionResult(session, true, "accepted", "Accepted " + value.outcome + ": " + value.summary, journalRecording)
      case value: GoalPhaseOrderedHumanDecision.Answer =>
        V.GoalPhaseOrderedDecisionSubmissionResult(session, true, "accepted", "Accepted Human " + value.choice + ": " + value.rationale)
      case value: GoalPhaseFindingRecording.Result =>
        V.GoalPhaseFindingRecordingSubmissionResult(session, true, "accepted", "Accepted recording " + value.outcome + ": " + value.summary,
          journalRecording)
      case value: SplitPhaseExplicitPartitionSemantic.Judgment =>
        V.SplitPhaseSemanticSubmissionResult(session, true, "accepted", "Accepted partition " + value.decision + ": " + value.rationale)
      case value: SplitPhaseScopeSemantic.Judgment =>
        V.SplitPhaseScopeSubmissionResult(session, true, "accepted", "Accepted handoff scope " + value.decision + ": " + value.rationale)
      case value: SplitPhaseExplicitReasoningSemantic.Judgment =>
        V.SplitPhaseReasoningSubmissionResult(session, true, "accepted", "Accepted reasoning " + value.decision + ": " + value.rationale)
      case value: SplitPhaseHumanDirectionAnswerContract.Answer =>
        val choice = value match {
          case SplitPhaseHumanDirectionAnswerContract.Answer.ProvideResolution(reference) =>
            "ProvideResolution " + reference.identity + "@" + reference.revision
          case SplitPhaseHumanDirectionAnswerContract.Answer.Defer => "Defer"
          case SplitPhaseHumanDirectionAnswerContract.Answer.Cancel => "Cancel"
        }
        V.SplitPhaseHumanDirectionSubmissionResult(session, true, "accepted", "Accepted Human " + choice + ".")
      case value: RepositorySyncConflictSemantic.Result =>
        V.RepositorySyncConflictSubmissionResult(session, true, "accepted", "Accepted conflict " + value.decision + ": " + value.rationale)
      case value: RepositorySyncNativeMergeReview.Result =>
        V.RepositorySyncNativeMergeReviewSubmissionResult(session, true, "accepted", "Accepted merge review " + value.decision + ": " + value.rationale)
      case value: RepositorySyncNativeMergeRepair.Result =>
        V.RepositorySyncNativeMergeRepairSubmissionResult(session, true, "accepted", "Accepted merge repair " + value.decision + ": " + value.rationale)
      case value: RepositorySyncHumanConflict.Answer =>
        V.RepositorySyncHumanConflictSubmissionResult(session, true, "accepted", "Accepted Human " + value.choice + ": " + value.rationale)
    }
  }

  def splitPartition(value: S.Request): V.SplitPhasePartitionWorkView =
    V.SplitPhasePartitionWorkView(_source(value.source), value.items.map(item =>
      V.SplitPhasePartitionNovelWork(_item_identity(item.identity), item.reason, _item(item.closure))),
      value.boundaries.map(boundary => V.SplitPhasePartitionUnknownBoundary(
        V.SplitPhasePartitionEdge(_item_identity(boundary.edge.afterItem), boundary.edge.disposition.toString,
          _authority(boundary.edge.authority)), _item(boundary.left), _item(boundary.right))))

  def splitScopeC(value: SplitPhaseScopeSemantic.Request): Consequence[V.SplitPhaseScopeWorkView] = for {
    actual <- value.gaps.foldLeft(Consequence.success(Vector.empty[V.SplitPhaseUnresolvedHandoffScope])) { (prior, gap) => for {
      values <- prior
      producing <- _nonempty_c(gap.producingItems.map(_item_identity), "scope producing items")
      receiving <- _nonempty_c(gap.receivingItems.map(_item_identity), "scope receiving items")
    } yield values :+ V.SplitPhaseUnresolvedHandoffScope(_handoff(gap.original), producing, receiving) }
    gaps <- _nonempty_c(actual, "scope gaps")
    items <- _nonempty_c(value.items.map(_item), "scope source items")
  } yield V.SplitPhaseScopeWorkView(_source(value.source), gaps, items)

  def splitReasoning(value: SplitPhaseExplicitReasoningSemantic.Request): V.SplitPhaseReasoningWorkView =
    V.SplitPhaseReasoningWorkView(_source(value.source), _authority(value.authority),
      V.SplitPhaseReasoningDemandReference(value.demand.identity, value.demand.revision), value.items.map(item =>
        V.SplitPhaseReasoningUnknownItem(_item_identity(item.identity), item.reason, _item(item.closure))))

  def splitPartitionRequestC(work: WorkflowInteraction[S.Request, Nothing]): Consequence[V.SplitPhaseAdvanceResult] = for {
    value <- workInputC(work)
    issuedContext <- NativeWorkflowIssuedWorkContext.fromIssuedC(work, NativeWorkflowIssuedWorkContext.Mode.Design,
      SplitPhaseExplicitPartitionSemanticJson.requestCodec)
  } yield splitRequest(work.handle.instanceIdentity.value, "submitPartitionSemanticResult",
      "SplitPhasePartitionSemanticResult", "Assess only the unresolved partition work and boundaries.",
      partition = Some(splitPartition(value)), issuedWorkContext = Some(issuedContext))

  def splitScopeRequestC(work: WorkflowInteraction[SplitPhaseScopeSemantic.Request, Nothing]):
    Consequence[V.SplitPhaseAdvanceResult] = for {
    input <- workInputC(work)
    issuedContext <- NativeWorkflowIssuedWorkContext.fromIssuedC(work, NativeWorkflowIssuedWorkContext.Mode.Design,
      SplitPhaseScopeSemanticJson.requestCodec)
    value <- splitScopeC(input)
  } yield splitRequest(work.handle.instanceIdentity.value, "submitHandoffScopeResult", "SplitPhaseScopeSemanticResult",
    "Resolve only the requested handoff scopes.", scope = Some(value), issuedWorkContext = Some(issuedContext))

  def splitReasoningRequestC(work: WorkflowInteraction[SplitPhaseExplicitReasoningSemantic.Request, Nothing]):
    Consequence[V.SplitPhaseAdvanceResult] = for {
    value <- workInputC(work)
    issuedContext <- NativeWorkflowIssuedWorkContext.fromIssuedC(work, NativeWorkflowIssuedWorkContext.Mode.Analysis,
      SplitPhaseExplicitReasoningSemanticJson.requestCodec)
  } yield splitRequest(work.handle.instanceIdentity.value,
      "submitReasoningResult", "SplitPhaseReasoningSemanticResult", "Classify only the unresolved reasoning items.",
      reasoning = Some(splitReasoning(value)), issuedWorkContext = Some(issuedContext))

  /** The private issued input still owns its saved business frame. Consume it
    * once for the public question; never reissue or consult candidate stores. */
  def splitScopeHumanC(question: WorkflowInteraction[SplitPhaseNativeHumanDirection.Input, Nothing], core: ActionCall.Core):
    Consequence[V.SplitPhaseAdvanceResult] = for {
    context <- NativeHumanDecisionContext.splitC(core, question)
    input <- decisionInputC(question)
    snapshot = input.frontier
    request <- SplitPhaseScopeSemantic.requestC(snapshot.first)
    scope <- splitScopeC(request)
    judgment <- _scope_judgment_c(snapshot.judgment)
    source = snapshot.first.snapshot.selection.proposal.source
    value = V.SplitPhaseScopeHumanQuestion(snapshot.judgment.rationale, SplitPhaseHumanDirectionAnswerContract.choices,
      source.phase.value, V.WorkflowArtifactReference(source.authority.identity, source.authority.revision),
      snapshot.first.snapshot.selection.boundary.boundary.items.map(_selected_item),
      _human_candidates(snapshot.first.snapshot), scope.gaps.toVector, judgment, Some(context))
  } yield splitRequest(question.handle.instanceIdentity.value, "submitSplitHumanDirection", "SplitPhaseHumanDirectionAnswer",
    value.rationale, scopeHuman = Some(value))

  def splitReasoningHumanC(question: WorkflowInteraction[SplitPhaseReasoningHumanDirection.Input, Nothing], core: ActionCall.Core):
    Consequence[V.SplitPhaseAdvanceResult] = for {
    context <- NativeHumanDecisionContext.splitC(core, question)
    input <- decisionInputC(question)
    request = input.request
    snapshot = input.frontier
    source = snapshot.selection.proposal.source
    judgment = input.judgment
    value = V.SplitPhaseReasoningHumanQuestion(judgment.rationale, SplitPhaseHumanDirectionAnswerContract.choices,
      source.phase.value, V.WorkflowArtifactReference(source.authority.identity, source.authority.revision),
      splitReasoning(request), V.SplitPhaseReasoningSemanticResult(judgment.decision.toString, judgment.rationale,
        judgment.assignments.map(a => V.SplitPhaseReasoningAssignment(_item_identity(a.identity), a.classification.toString,
          a.rationale)), judgment.evidence.map(e => V.SplitPhasePartitionEvidenceReference(e.identity, e.revision))),
      snapshot.selection.boundary.boundary.items.map(_selected_item), _human_candidates(snapshot), Some(context))
  } yield splitRequest(question.handle.instanceIdentity.value, "submitSplitHumanDirection", "SplitPhaseHumanDirectionAnswer",
    value.rationale, reasoningHuman = Some(value))

  def workInputC[A](value: WorkflowInteraction[A, Nothing]): Consequence[A] = value.current match {
    case work: WorkflowContinuation.WorkOrder[A] => work.request.input.map(_.value).map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Issued work has no business input"))
    case _ => Consequence.stateConflict("Requested work is not a WorkOrder")
  }
  def decisionInputC[A](value: WorkflowInteraction[A, Nothing]): Consequence[A] = value.current match {
    case question: WorkflowContinuation.Decision[A] => question.request.input.map(_.value).map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Issued Human question has no business input"))
    case _ => Consequence.stateConflict("Requested Human question is not a Decision")
  }

  private def _scope_judgment_c(value: SplitPhaseScopeSemantic.Judgment): Consequence[V.SplitPhaseScopeSemanticResult] = for {
    scopes <- value.scopes.foldLeft(Consequence.success(Vector.empty[V.SplitPhaseResolvedHandoffScope])) { (prior, scope) => for {
      accumulated <- prior
      producing <- _nonempty_c(scope.producingItems.map(_item_identity), "submitted scope producing items")
      receiving <- _nonempty_c(scope.receivingItems.map(_item_identity), "submitted scope receiving items")
    } yield accumulated :+ V.SplitPhaseResolvedHandoffScope(V.SplitPhaseScopeHandoffIdentity(scope.handoff.value),
      producing, receiving, scope.rationale) }
    evidence <- _nonempty_c(value.evidence.map(e => V.SplitPhasePartitionEvidenceReference(e.identity, e.revision)),
      "submitted scope evidence")
  } yield V.SplitPhaseScopeSemanticResult(value.decision.toString, value.rationale, scopes, evidence)

  private def _human_candidates(snapshot: R.Record): Vector[V.SplitPhaseHumanCandidate] = snapshot.partition match {
    case R.PartitionView.Enumerated(_, _, candidates) =>
      val projections = snapshot.projection.toVector.flatMap(_.candidates).map(c => c.key -> c).toMap
      candidates.map { candidate =>
        val projected = projections.get(candidate.key)
        val gaps = projected.toVector.collect { case value: R.CandidateProjectionView.SemanticView => value.gaps }.flatten
        V.SplitPhaseHumanCandidate(candidate.key.cutAfterIndices, candidate.children.map(child =>
          V.SplitPhaseHumanChild(child.ownedItems.map(_.value), child.expectedMinutes, snapshot.selection.boundary.ownership.children
            .filter(c => child.ownedItems.exists(id => snapshot.selection.proposal.proposal.children.find(_.phase == c.phase)
              .exists(_.ownedUnfinishedItems.contains(id)))).map(_.repository.value).distinct)),
          projected.map {
            case _: R.CandidateProjectionView.BuiltView => "Built"
            case _: R.CandidateProjectionView.SemanticView => "ScopeRequired"
            case _: R.CandidateProjectionView.DecisionView => "DecisionNeeded"
          }.getOrElse("Unprojected"), projected.toVector.collect { case value: R.CandidateProjectionView.DecisionView =>
            value.reason.toString }, gaps.map(gap => V.SplitPhaseUnresolvedHandoffScope(_handoff(gap.original),
            NonEmptyVector.fromVectorUnsafe(gap.producingItems.map(_item_identity)),
            NonEmptyVector.fromVectorUnsafe(gap.receivingItems.map(_item_identity)))))
      }
    case _ => Vector.empty
  }

  /** Only the chosen Built candidate is consumed, not a second planning pass
    * or a round trip through a JSON transport envelope. Documents are already
    * produced by their owning deterministic document projection. */
  def splitSelectedC(snapshot: R.Record, documents: SplitPhaseSelectedDocuments.Projection,
    mode: String, effect: Option[String]): Consequence[V.SplitPhaseSelectedPlanView] = for {
    selected <- snapshot.objective match {
      case Some(value: R.ObjectiveView.Selected) => Consequence.success(value)
      case _ => Consequence.stateConflict("Split selected output has no selected objective")
    }
    built <- snapshot.projection.toVector.flatMap(_.candidates).collectFirst {
      case value: R.CandidateProjectionView.BuiltView if value.key == selected.key => value
    }.map(Consequence.success).getOrElse(Consequence.stateConflict("Split selected output has no Built candidate"))
    items = snapshot.selection.boundary.boundary.items.map(item => item.identity -> item).toMap
    children = built.proposal.children.zip(built.ownership.children).map { (child, owner) =>
      V.SplitPhaseSelectedChild(child.phase.value, child.ownedUnfinishedItems.map(id => _selected_item(items(id))),
        child.expectedMinutes, owner.repository.value, owner.closureCriteria.map(_.value), owner.validationOwner.value,
        owner.dependencies.map(_.value), built.handoffs.incomingByChild(child.phase).map(_.value))
    }
  } yield V.SplitPhaseSelectedPlanView(mode, built.proposal.phase.value,
    V.WorkflowArtifactReference(built.proposal.source.identity, built.proposal.source.revision),
    V.WorkflowArtifactReference(built.proposal.authority.identity, built.proposal.authority.revision),
    V.WorkflowArtifactReference(built.proposal.reference.identity, built.proposal.reference.revision),
    selected.key.cutAfterIndices, selected.stage.toString, built.ownership.timing.toString, built.ownership.method,
    built.ownership.driver, built.ownership.finalValidationOwner.value, children, built.handoffs.handoffs.map(_handoff),
    built.localHandoffs.map(value => V.SplitPhaseLocalHandoff(value.original.identity.value, value.phase.value,
      value.producingItems.map(_.value), value.receivingItems.map(_.value))), documents.documents.map(value =>
      V.SplitPhaseSelectedDocument(value.relativePath, value.phase.value,
        if (value.updateSourceSections) "updateSourceSections" else "createChild", value.ownedItems.map(_.value),
        value.sections, value.childDocument)), effect)

  def splitRequest(session: String, operation: String, resultType: String, message: String,
    partition: Option[V.SplitPhasePartitionWorkView] = None, scope: Option[V.SplitPhaseScopeWorkView] = None,
    reasoning: Option[V.SplitPhaseReasoningWorkView] = None,
    scopeHuman: Option[V.SplitPhaseScopeHumanQuestion] = None,
    reasoningHuman: Option[V.SplitPhaseReasoningHumanQuestion] = None,
    issuedWorkContext: Option[V.WorkflowIssuedWorkContext] = None): V.SplitPhaseAdvanceResult =
    V.SplitPhaseAdvanceResult(session, if (scopeHuman.nonEmpty || reasoningHuman.nonEmpty) "Human" else "Request",
      message, Some(operation), Some(session), Some(resultType), None, None, partition, scope, reasoning,
      scopeHuman, reasoningHuman, None, None, issuedWorkContext, None)

  def splitSelected(session: String, value: V.SplitPhaseSelectedPlanView): V.SplitPhaseAdvanceResult =
    V.SplitPhaseAdvanceResult(session, "Terminal", "Split plan " + value.mode + " completed.", None, None, None,
      None, Some(if (value.mode == "APPLY") "Applied" else "Previewed"), None, None, None, None, None, Some(value), None, None, None)

  def splitStopped(session: String, outcome: String, answer: ContextReference): V.SplitPhaseAdvanceResult =
    V.SplitPhaseAdvanceResult(session, "Terminal", "The recorded Human direction ended this split run: " + outcome,
      None, None, None, None, Some(outcome), None, None, None, None, None, None,
      Some(V.SplitPhaseStoppedResult(outcome, V.WorkflowArtifactReference(answer.identity, answer.revision))), None, None)

  private def _source(value: P.SourceReference): V.SplitPhasePartitionSourceReference =
    V.SplitPhasePartitionSourceReference(value.identity, value.revision)
  private def _authority(value: P.AuthorityReference): V.SplitPhasePartitionAuthorityReference =
    V.SplitPhasePartitionAuthorityReference(value.identity, value.revision)
  private def _item_identity(value: P.WorkItemIdentity): V.SplitPhasePartitionWorkItemIdentity =
    V.SplitPhasePartitionWorkItemIdentity(value.value)
  private def _item(value: B.Item): V.SplitPhasePartitionSourceItem =
    V.SplitPhasePartitionSourceItem(_item_identity(value.identity),
      value.closureCriteria.map(c => V.SplitPhasePartitionClosureCriterion(c.value)),
      value.prerequisiteItems.map(_item_identity), _authority(value.authority))
  private def _selected_item(value: B.Item): V.SplitPhaseSelectedWorkItem =
    V.SplitPhaseSelectedWorkItem(value.identity.value, value.closureCriteria.map(_.value),
      value.prerequisiteItems.map(_.value), V.WorkflowArtifactReference(value.authority.identity, value.authority.revision))
  private def _handoff(value: B.Handoff): V.SplitPhaseScopeHandoff =
    V.SplitPhaseScopeHandoff(V.SplitPhaseScopeHandoffIdentity(value.identity.value),
      V.SplitPhaseScopePhaseIdentity(value.producingChild.value), V.SplitPhaseScopePhaseIdentity(value.receivingChild.value),
      value.kind, value.input, value.action, value.output, V.SplitPhaseScopePhaseIdentity(value.owner.value),
      value.invalidationReason, _authority(value.authority))
  private def _nonempty_c[A](values: Vector[A], field: String): Consequence[NonEmptyVector[A]] =
    NonEmptyVector.fromVector(values).map(Consequence.success).getOrElse(
      Consequence.stateConflict("Split output requires " + field))

}
