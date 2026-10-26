package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence

/** Shared evidence gate for a bounded repair and a formal Phase Step.
  * This selects no command, grants no authority and creates no review waiver.
  * @since   Oct. 26, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowRepairAssessment {
  import WorkflowSbtValidation as T
  import RepositorySyncNativeMergeValidation as V

  def checksC(kind: String, scalaLint: String, selected: Vector[GoalPhaseNativeValidation.Check],
    receipts: Vector[V.Receipt]): Consequence[Unit] = {
    val complete = selected != null && receipts != null && selected.nonEmpty && selected.forall(_ != null) && receipts.forall(_ != null) &&
      selected.map(_.identity).distinct == selected.map(_.identity) &&
      receipts.map(_.identity) == selected.map(_.identity) &&
      selected.zip(receipts).forall((check, receipt) => V.productSuccessful(receipt, check.kind))
    val behavior = kind != "code" || (complete &&
      selected.exists(_.kind == T.Kind.Compile) && selected.zip(receipts).exists((check, receipt) =>
        check.kind == T.Kind.TestOnly && receipt.sbt.flatMap(_.selection).exists(s =>
          s.completedSuites > 0 && s.testsRun > 0)))
    val lint = scalaLint != "required" || (complete && selected.exists(_.kind == T.Kind.FixCheck))
    if (!Set("code", "documentation")(kind) || !Set("off", "required")(scalaLint))
      Consequence.stateConflict("Repair assessment requires its adopted code and lint policy")
    else if (!complete) Consequence.stateConflict("Repair assessment requires the exact complete successful typed check batch")
    else if (!behavior) Consequence.stateConflict("Code admission requires compilation and actually executed nonempty focused behavioral tests")
    else if (!lint) Consequence.stateConflict("Required Scala lint has no actual selected fix-check receipt")
    else Consequence.unit
  }
}
