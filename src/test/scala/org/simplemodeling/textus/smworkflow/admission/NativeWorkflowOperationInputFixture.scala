package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.protocol.Property
import org.goldenport.record.RecordPresentable
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.value as V

/** Named public business parameters, never a native continuation envelope.
  * The one TypedValue[Json] helper below is host selection data only.
  * @since   Oct. 11, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeWorkflowOperationInputFixture {
  def selectedGoalInput(input: V.GoalPhaseStartInput): TypedValue[Json] = TypedValue("GoalPhaseStartInput", Json.obj(
    "phaseIdentity" -> Json.fromString(input.phaseIdentity), "repositoryIdentity" -> Json.fromString(input.repositoryIdentity)))

  def goal(input: V.GoalPhaseStartInput, session: Option[String] = None): List[Property] = _start(input, session)
  def split(input: V.SplitPhaseStartInput, session: Option[String] = None): List[Property] = _start(input, session)
  def repository(input: V.RepositorySyncStartInput, session: Option[String] = None): List[Property] = _start(input, session)

  def start(input: NativeWorkflowStartInput.BusinessInput, session: Option[String] = None): List[Property] = input match {
    case NativeWorkflowStartInput.Goal(value) => goal(value, session)
    case NativeWorkflowStartInput.Split(phase, mode, proposal) => split(V.SplitPhaseStartInput(phase, mode, proposal), session)
    case value: NativeWorkflowStartInput.Repository => repository(V.RepositorySyncStartInput(value.repositoryIdentity,
      value.localBranch, value.trackingRemote, value.trackingBranch, value.remoteEndpointIdentity, value.syncMode), session)
  }

  private def _start(input: RecordPresentable, session: Option[String]): List[Property] =
    Property("input", input, None) :: session.toList.map(value => Property("sessionId", value, None))

  def advance(session: String): List[Property] = List(Property("sessionId", session, None))

  def submissionC[A <: NativeWorkflowResultInput.Value](result: ContinuationResult[A],
    omitReference: Boolean = false): Consequence[List[Property]] = for {
    business <- _business_c(result.result.value)
    (name, value) = business
    evidence = V.WorkflowExecutionEvidence(result.evidence.references.map(_reference),
      result.evidence.workerIdentity, None, result.evidence.execution.map(e => V.WorkflowExecutionObservation(e.placement.value,
        e.originContextIdentity, e.executorContextIdentity, e.profileIdentity)))
  } yield List(Property("sessionId", result.handle.instanceIdentity.value, None), Property(name, value, None),
    Property("completionFacts", result.completionFacts.map(_reference), None), Property("executionEvidence", evidence, None)) ++
    (if (omitReference) Nil else List(Property("resultReference", _reference(result.resultReference), None)))

  def goalWorkResult(result: GoalPhaseNativeWork.Result): V.GoalPhaseWorkResult =
    V.GoalPhaseWorkResult(result.outcome, result.summary, result.findings,
      GoalPhaseAcceptanceBoundary.publicReport(result.acceptance), GoalPhaseFixAssessment.publicReport(result.fixAssessment),
      GoalPhaseFixIssues.publicReport(result.issueReport), GoalPhasePlanRevision.publicProposal(result.planRevision),
      GoalPhaseFindingRecording.publicObservations(result.reviewObservations), GoalPhaseDependencyScope.publicProposal(result.dependencyProposal),
      result.completionConditions.map(value => GoalPhaseNativeMetadata.record(value.asJson)),
      GoalPhasePurposeRepair.publicSources(result.repairFindingSources),
      dependencyValidation = GoalPhaseDependencyValidation.publicSelection(result.dependencyValidation), diagnosticRepair = None)

  private def _business_c(value: NativeWorkflowResultInput.Value): Consequence[(String, RecordPresentable)] = value match {
    case result: GoalPhaseFindingRecording.Result => Consequence.success("result" -> NativeWorkflowOperationOutput.goalRecordingResult(result))
    case result: GoalPhaseNativeWork.Result => Consequence.success("result" -> goalWorkResult(result))
    case answer: GoalPhaseOrderedHumanDecision.Answer => Consequence.success("answer" ->
      V.GoalPhaseOrderedDecisionAnswer(answer.choice, answer.rationale))
    case result: SplitPhaseExplicitPartitionSemantic.Judgment => Consequence.success("result" ->
      V.SplitPhasePartitionSemanticResult(result.decision.toString, result.rationale, result.estimates.map(e =>
        V.SplitPhasePartitionNovelEstimate(V.SplitPhasePartitionWorkItemIdentity(e.identity.value), e.minimumMinutes,
          e.maximumMinutes, e.assumptions, e.noveltyBasis)), result.boundaries.map(b =>
        V.SplitPhasePartitionBoundaryJudgment(V.SplitPhasePartitionWorkItemIdentity(b.afterItem.value), b.disposition.toString,
          b.rationale)), result.evidence.map(_partition_evidence)))
    case result: SplitPhaseScopeSemantic.Judgment => for {
      scopes <- result.scopes.foldLeft(Consequence.success(Vector.empty[V.SplitPhaseResolvedHandoffScope])) { (prior, scope) => for {
        values <- prior
        producing <- _nonempty_c(scope.producingItems.map(i => V.SplitPhasePartitionWorkItemIdentity(i.value)))
        receiving <- _nonempty_c(scope.receivingItems.map(i => V.SplitPhasePartitionWorkItemIdentity(i.value)))
      } yield values :+ V.SplitPhaseResolvedHandoffScope(V.SplitPhaseScopeHandoffIdentity(scope.handoff.value),
        producing, receiving, scope.rationale) }
      evidence <- _nonempty_c(result.evidence.map(_partition_evidence))
    } yield "result" -> V.SplitPhaseScopeSemanticResult(result.decision.toString, result.rationale, scopes, evidence)
    case result: SplitPhaseExplicitReasoningSemantic.Judgment => Consequence.success("result" ->
      V.SplitPhaseReasoningSemanticResult(result.decision.toString, result.rationale, result.assignments.map(a =>
        V.SplitPhaseReasoningAssignment(V.SplitPhasePartitionWorkItemIdentity(a.identity.value), a.classification.toString,
          a.rationale)), result.evidence.map(_partition_evidence)))
    case answer: SplitPhaseHumanDirectionAnswerContract.Answer =>
      val (choice, resolution) = answer match {
        case SplitPhaseHumanDirectionAnswerContract.Answer.ProvideResolution(value) =>
          "ProvideResolution" -> Some(V.SplitPhasePartitionContextReference(value.identity, value.revision))
        case SplitPhaseHumanDirectionAnswerContract.Answer.Defer => "Defer" -> None
        case SplitPhaseHumanDirectionAnswerContract.Answer.Cancel => "Cancel" -> None
      }
      Consequence.success("answer" -> V.SplitPhaseHumanDirectionAnswer(V.SplitPhaseHumanDirectionChoice(choice), resolution))
    case result: RepositorySyncPurpose.Result =>
      import io.circe.generic.auto.*
      Consequence.success("result" -> V.RepositorySyncPurposeResult(result.decision, result.rationale,
        result.groups.map(group => GoalPhaseNativeMetadata.record(group.asJson)), result.excludedPaths))
    case result: RepositorySyncConflictSemantic.Result => Consequence.success("result" ->
      V.RepositorySyncConflictResult(result.decision, result.resolutions.map(_resolution), result.rationale))
    case result: RepositorySyncNativeMergeReview.Result => Consequence.success("result" ->
      V.RepositorySyncNativeMergeReviewResult(result.decision, result.rationale, result.findings.map(f =>
        V.RepositorySyncNativeMergeReviewFinding(f.identity, f.affectedPaths, f.rationale))))
    case result: RepositorySyncNativeMergeRepair.Result => Consequence.success("result" ->
      V.RepositorySyncNativeMergeRepairResult(result.decision, result.rationale,
        result.replacements.map(file => V.RepositorySyncNativeMergeRepairFile(file.path, file.text))))
    case answer: RepositorySyncHumanConflict.Answer => Consequence.success("answer" ->
      V.RepositorySyncHumanConflictAnswer(answer.choice, answer.resolutions.map(_resolution), answer.rationale))
  }

  private def _reference(value: ContextReference): V.WorkflowArtifactReference =
    V.WorkflowArtifactReference(value.identity, value.revision)
  private def _partition_evidence(value: SplitPhaseExplicitPartitionSemantic.EvidenceReference): V.SplitPhasePartitionEvidenceReference =
    V.SplitPhasePartitionEvidenceReference(value.identity, value.revision)
  private def _resolution(value: RepositorySyncConflictSemantic.Resolution): V.RepositorySyncConflictResolution =
    V.RepositorySyncConflictResolution(value.conflictIdentity, value.text)
  private def _nonempty_c[A](values: Vector[A]): Consequence[NonEmptyVector[A]] =
    NonEmptyVector.fromVector(values).map(Consequence.success).getOrElse(
      Consequence.stateConflict("Fixture business value requires its declared nonempty field"))
}
