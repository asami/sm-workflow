package org.simplemodeling.textus.smworkflow.admission

import java.time.{Instant, DateTimeException}

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionModel.CandidateRef
import org.simplemodeling.textus.smworkflow.value.{StepCloseRequest, StepReviewEvidence}

/** Converts the CML-generated Step-close Operation input into the application
  * admission policy. This boundary evaluates evidence only; it cannot commit
  * a Step or choose the next Workflow state.
  * @since   Sep. 26, 2026
  * @version Oct.  8, 2026
  */
object StepCloseRequestAdapter {
  import StepClosureAdmission.{ReviewEvidence, ReviewScope, StepSnapshot, Submission}

  def submissionC(request: StepCloseRequest): Consequence[Submission] =
    if (request == null || request.reviewEvidence == null)
      Consequence.stateConflict("Step-close request or review evidence collection is missing")
    else {
      val snapshot = StepSnapshot(request.stepIdentity, request.programDigest, request.planningDigest)
      StepClosureAdmission.submissionC(request.submissionIdentity, snapshot).flatMap { initial =>
        request.reviewEvidence.foldLeft(Consequence.success(Vector.empty[StepClosureAdmission.Evidence])) {
          (result, declared) => for {
            accumulated <- result
            evidence <- reviewEvidenceC(request.stepIdentity, declared)
          } yield accumulated :+ evidence
        }.flatMap(evidence => initial.copy(evidence = evidence).validateC)
      }
    }

  def reviewEvidenceC(stepIdentity: String, declared: StepReviewEvidence):
    Consequence[StepClosureAdmission.Evidence] =
    if (declared == null || declared.reviewedRevision == null ||
        !declared.reviewedRevision.matches("[0-9a-f]{64}"))
      Consequence.stateConflict("Step review has no valid target revision")
    else for {
      reviewedAt <- _instantC(declared.reviewedAt)
      validUntil <- _instantC(declared.validUntil)
      scope <- _scopeC(declared.scope)
      evidence <- StepClosureAdmission.evidenceC(
        declared.evidenceIdentity,
        CandidateRef(stepIdentity, declared.reviewedRevision),
        ReviewEvidence(declared.reviewedRevision, reviewedAt, validUntil, declared.summary),
        scope,
        declared.provenance
      )
    } yield evidence

  private def _instantC(value: String): Consequence[Instant] =
    if (value == null || value.trim.isEmpty)
      Consequence.stateConflict("Step review timestamp is missing")
    else
      try Consequence.success(Instant.parse(value))
      catch {
        case _: DateTimeException => Consequence.stateConflict("Step review timestamp is invalid")
      }

  // The current CML value is a string: accept only the explicit full-Step
  // marker or one focused path. A richer scope ABI can replace this parser.
  private def _scopeC(value: String): Consequence[ReviewScope] =
    if (value == "full-step") Consequence.success(ReviewScope.FullStep)
    else if (value != null && value.startsWith("focused:") &&
        value.substring("focused:".length).trim.nonEmpty)
      Consequence.success(ReviewScope.Focused(Set(value.substring("focused:".length))))
    else Consequence.stateConflict("Step review scope is unsupported")
}
