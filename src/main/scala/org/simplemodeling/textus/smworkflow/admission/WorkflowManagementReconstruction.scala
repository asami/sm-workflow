package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference

/** Shared interpretation of owner-supplied reconstruction facts. Native family
  * adapters retain provenance, immutable contracts and persistence ownership.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  */
private[smworkflow] object WorkflowManagementReconstruction {
  final case class Assessment(incompatible: Vector[String], questions: Vector[String],
    uncertainEffects: Vector[String], freshCapture: Boolean, retainEvidence: Boolean) {
    def reasons: Vector[String] = incompatible ++ questions ++ uncertainEffects
    def reconstructible: Boolean = incompatible.isEmpty && uncertainEffects.isEmpty
  }
  enum ResumePoint {
    case RetainedEvidence, FreshCapture, CurrentBoundary, Unresolved
  }
  final case class Selection(adoptProposed: Boolean, reasons: Vector[String], resumePoint: ResumePoint) {
    def freshCapture: Boolean = resumePoint == ResumePoint.FreshCapture
  }

  def requireReconstructibleC(assessment: Assessment): Consequence[Unit] =
    if (assessment.reconstructible) Consequence.unit
    else Consequence.stateConflict("Workflow reconstruction: " +
      (assessment.incompatible ++ assessment.uncertainEffects).mkString("; "))

  /** Direction has already been independently admitted by its native owner.
    * Historical unresolved selections remain readable; new mutations first
    * require requireReconstructibleC and their family-specific admission.
    */
  def selectedC(assessment: Assessment, actualDirection: Option[ContextReference],
    adoptProposed: Boolean): Consequence[Selection] = {
    val reasons = assessment.incompatible ++
      (if (actualDirection.nonEmpty) Vector.empty else assessment.questions) ++ assessment.uncertainEffects
    val resume = if (reasons.nonEmpty) ResumePoint.Unresolved
      else if (assessment.freshCapture) ResumePoint.FreshCapture
      else if (assessment.retainEvidence) ResumePoint.RetainedEvidence else ResumePoint.CurrentBoundary
    Consequence.success(Selection(adoptProposed && assessment.incompatible.isEmpty &&
      (assessment.questions.isEmpty || actualDirection.nonEmpty), reasons, resume))
  }
}
