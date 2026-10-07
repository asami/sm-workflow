package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import scala.util.Try
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.CandidateAdmissionProducerAbi as Abi
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.value.RepositorySyncMergeReviewResult
import RepositorySyncMergeReviewAdmission as R

/**
 * Admits full-merge semantic evidence, not permission to commit or push.
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object RepositorySyncMergeReviewResultAdapter {
  val payloadCodec = new WorkflowResultJsonV1.PayloadCodec[RepositorySyncMergeReviewResult] {
    val typeIdentity = "RepositorySyncMergeReviewResult"
    def encode(value: RepositorySyncMergeReviewResult): Json = value.asJson
    def decode(value: Json): Either[String, RepositorySyncMergeReviewResult] =
      if (value != null && value.asObject.exists(_.keys.toSet == Set("candidateRevision", "reviewedAt",
          "validUntil", "decision", "rationale", "evidenceDigest", "findings")) &&
          value.hcursor.downField("findings").focus.flatMap(_.asArray).exists(_.forall(finding =>
            finding.asObject.exists(_.keys.toSet == Set("identity", "affectedPaths", "rationale")))))
        value.as[RepositorySyncMergeReviewResult].left.map(_.message)
      else Left("RepositorySync merge review Result has an incompatible shape")
  }

  final case class Assessed(evidence: R.Evidence, admission: R.Assessed,
    judgment: Abi.AdmittedTypedJudgmentResultV1) {
    def accepted: Boolean = admission.accepted
  }

  def assessC(
    candidate: CandidateWorkflowAbi.Definition,
    original: R.Submission,
    issued: WorkflowInteraction[org.simplemodeling.textus.smworkflow.value.RepositorySyncMergeReviewRequest, Nothing],
    submitted: ContinuationResult[RepositorySyncMergeReviewResult],
    asOf: Instant
  ): Consequence[Assessed] =
    if (candidate == null || original == null || original.candidate == null ||
        original.candidate.payload == null || submitted == null || submitted.result == null ||
        submitted.result.value == null || asOf == null)
      Consequence.stateConflict("RepositorySync merge review Result is incomplete")
    else for {
      _ <- CandidateWorkflowAbi.admitC(candidate)
      _ <- WorkflowProtocolV1.admitResultC(issued, submitted)
      expected <- RepositorySyncMergeReviewBoundary.requestC(original)
      _ <- issued.current match {
        case work: WorkflowContinuation.WorkOrder[?] if
            issued.handle.workflowIdentity.value == candidate.workflow.identity &&
            issued.handle.workflowRevision.value == candidate.workflow.version &&
            work.request.requiredOperation == StateMachineRequiredOperationIdentity("review-merge-capability") &&
            work.request.operation == StateMachineOperationIdentity("MergeReviewService", "reviewMerge") &&
            work.request.completionOperation == StateMachineOperationIdentity("RepositorySyncService", "submitMergeReview") &&
            work.request.resultType.contains(StateMachineResultTypeReference("RepositorySyncMergeReviewResult")) &&
            work.request.input.contains(TypedValue("RepositorySyncMergeReviewRequest", expected)) => Consequence.unit
        case _ => Consequence.stateConflict("RepositorySync review Result differs from its declared WorkOrder")
      }
      value = submitted.result.value
      reviewedAt <- _instantC(value.reviewedAt)
      validUntil <- _instantC(value.validUntil)
      disposition <- value.decision match {
        case "accept" => Consequence.success(R.Disposition.Accept)
        case "repair" => Consequence.success(R.Disposition.Repair)
        case "decision-needed" => Consequence.success(R.Disposition.DecisionNeeded)
        case "reject" => Consequence.success(R.Disposition.Reject)
        case _ => Consequence.stateConflict("RepositorySync review alternative is undeclared")
      }
      _ <- if (value.findings != null && value.findings.forall(_ != null)) Consequence.unit
        else Consequence.stateConflict("RepositorySync review findings are missing")
      review = R.Review(value.candidateRevision, reviewedAt, validUntil, disposition,
        value.findings.map(finding => R.Finding(finding.identity, finding.affectedPaths, finding.rationale)),
        value.rationale, value.evidenceDigest)
      evidence <- R.evidenceC("merge-review-" + RepositorySyncMergeValidationAssessment.digest(
        Vector(submitted.resultReference.identity, submitted.resultReference.revision, value.evidenceDigest)),
        original.candidate.reference, review, R.Scope.FullMerge, "issued-work-order")
      admission <- R.assessC(original.copy(evidence = Vector.empty), asOf, Vector(evidence))
      _ <- if (admission.evaluation.gaps.isEmpty) Consequence.unit
        else Consequence.stateConflict("RepositorySync review is stale or differs from the full pending merge")
      typed = Abi.TypedJudgmentResultV1(Abi.acceptedTypedJudgmentResultSchemaVersion,
        Abi.JudgmentResult(Abi.JudgmentActionIdentity("ReviewMerge"), Abi.AlternativeIdentity(value.decision),
          Abi.Rationale(value.rationale), Abi.Evidence(value.evidenceDigest), Abi.EvidenceScope("full-merge"),
          Abi.EvidenceFreshness("current"), Abi.EvidenceProvenance("issued-work-order")),
        Abi.TypeIdentity("RepositorySyncMergeReviewResult"), submitted.resultReference)
      judgment <- Abi.admitTypedJudgmentResultC(candidate.sidecar, typed)
    } yield Assessed(evidence, admission, judgment)

  private def _instantC(value: String): Consequence[Instant] =
    Try(Instant.parse(value)).toEither.fold(
      _ => Consequence.stateConflict("RepositorySync review timestamp is invalid"), Consequence.success)
}
