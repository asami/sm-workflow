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
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncMergeReviewRequest, RepositorySyncMergeReviewResult}
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
      if (value != null && value.asObject.exists { fields =>
          val required = Set("candidateRevision", "reviewedAt", "decision", "rationale", "findings")
          required.subsetOf(fields.keys.toSet) && fields.keys.toSet.subsetOf(required ++ Set("validUntil", "evidenceDigest"))
        } &&
          value.hcursor.downField("findings").focus.flatMap(_.asArray).exists(_.forall(finding =>
            finding.asObject.exists(_.keys.toSet == Set("identity", "affectedPaths", "rationale")))))
        value.mapObject(_.remove("validUntil").remove("evidenceDigest"))
          .as[RepositorySyncMergeReviewResult].left.map(_.message)
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
        case work: WorkflowContinuation.WorkOrder[RepositorySyncMergeReviewRequest] if
            issued.handle.workflowIdentity.value == candidate.workflow.identity &&
            issued.handle.workflowRevision.value == candidate.workflow.version &&
            work.request.requiredOperation == StateMachineRequiredOperationIdentity("review-merge-capability") &&
            work.request.operation == StateMachineOperationIdentity("MergeReviewService", "reviewMerge") &&
            work.request.completionOperation == StateMachineOperationIdentity("RepositorySyncService", "submitMergeReview") &&
            work.request.resultType.contains(StateMachineResultTypeReference("RepositorySyncMergeReviewResult")) &&
            work.request.input.exists(value => value != null && value.typeIdentity == "RepositorySyncMergeReviewRequest" &&
              RepositorySyncMergeDecision.sameCandidateScope(value.value, expected)) => Consequence.unit
        case _ => Consequence.stateConflict("RepositorySync review Result differs from its declared WorkOrder")
      }
      value = submitted.result.value
      reviewedAt <- _instantC(value.reviewedAt)
      disposition <- value.decision match {
        case "accept" => Consequence.success(R.Disposition.Accept)
        case "repair" => Consequence.success(R.Disposition.Repair)
        case "decision-needed" => Consequence.success(R.Disposition.DecisionNeeded)
        case "reject" => Consequence.success(R.Disposition.Reject)
        case _ => Consequence.stateConflict("RepositorySync review alternative is undeclared")
      }
      _ <- if (value.findings != null && value.findings.forall(_ != null)) Consequence.unit
        else Consequence.stateConflict("RepositorySync review findings are missing")
      review = R.Review(value.candidateRevision, reviewedAt, disposition,
        value.findings.map(finding => R.Finding(finding.identity, finding.affectedPaths, finding.rationale)),
        value.rationale)
      evidence <- R.evidenceC(submitted.resultReference.identity,
        original.candidate.reference, review, R.Scope.FullMerge, "issued-work-order")
      admission <- R.assessC(original.copy(evidence = Vector.empty), asOf, Vector(evidence))
      _ <- if (admission.evaluation.gaps.isEmpty) Consequence.unit
        else Consequence.stateConflict("RepositorySync review is not applicable to the full pending merge")
      typed = Abi.TypedJudgmentResultV1(Abi.acceptedTypedJudgmentResultSchemaVersion,
        Abi.JudgmentResult(Abi.JudgmentActionIdentity("ReviewMerge"), Abi.AlternativeIdentity(value.decision),
          Abi.Rationale(value.rationale), Abi.Evidence(submitted.resultReference.identity), Abi.EvidenceScope("full-merge"),
          Abi.EvidenceFreshness("current"), Abi.EvidenceProvenance("issued-work-order")),
        Abi.TypeIdentity("RepositorySyncMergeReviewResult"), submitted.resultReference)
      judgment <- Abi.admitTypedJudgmentResultC(candidate.sidecar, typed)
    } yield Assessed(evidence, admission, judgment)

  private def _instantC(value: String): Consequence[Instant] =
    Try(Instant.parse(value)).toEither.fold(
      _ => Consequence.stateConflict("RepositorySync review timestamp is invalid"), Consequence.success)
}
