package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.SbtTestReassessment

/** Shared evidence gate for a bounded repair and a formal Phase Step.
  * This selects no command, grants no authority and creates no review waiver.
  * @since   Oct. 26, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowRepairAssessment {
  import WorkflowSbtValidation as T
  import RepositorySyncNativeMergeValidation as V

  def checksC(kind: String, scalaLint: String, selected: Vector[GoalPhaseNativeValidation.Check],
    receipts: Vector[V.Receipt]): Consequence[Unit] =
    _checks_c(kind, scalaLint, selected, receipts, false)

  def purposeFullChecksC(scalaLint: String, selected: Vector[GoalPhaseNativeValidation.Check],
    receipts: Vector[V.Receipt]): Consequence[Unit] =
    _checks_c("code", scalaLint, selected, receipts, true)

  private def _checks_c(kind: String, scalalint: String, selected: Vector[GoalPhaseNativeValidation.Check],
    receipts: Vector[V.Receipt], allowfulltest: Boolean): Consequence[Unit] = {
    val complete = selected != null && receipts != null && selected.nonEmpty && selected.forall(_ != null) && receipts.forall(_ != null) &&
      selected.map(_.identity).distinct == selected.map(_.identity) &&
      receipts.map(_.identity) == selected.map(_.identity) &&
      selected.zip(receipts).forall((check, receipt) => V.productSuccessful(receipt, check.kind))
    val behavior = kind != "code" || (complete &&
      selected.exists(_.kind == T.Kind.Compile) && selected.zip(receipts).exists((check, receipt) =>
        (check.kind == T.Kind.TestOnly && receipt.sbt.flatMap(_.selection).exists(s =>
          s.completedSuites > 0 && s.testsRun > 0)) ||
          (allowfulltest && check.kind == T.Kind.Test && _nonempty_full_test(receipt))))
    val lint = kind == "validation" || scalalint != "required" || (complete && selected.exists(_.kind == T.Kind.FixCheck))
    if (!Set("code", "documentation", "validation")(kind) || !Set("off", "required")(scalalint))
      Consequence.stateConflict("Repair assessment requires its adopted code and lint policy")
    else if (!complete) Consequence.stateConflict("Repair assessment requires the exact complete successful typed check batch")
    else if (!behavior && allowfulltest)
      Consequence.stateConflict("Purpose code assessment requires compilation and a complete successful report for at least one executed test")
    else if (!behavior) Consequence.stateConflict("Code admission requires compilation and actually executed nonempty focused behavioral tests")
    else if (!lint) Consequence.stateConflict("Required Scala lint has no actual selected fix-check receipt")
    else Consequence.unit
  }

  private def _nonempty_full_test(receipt: V.Receipt): Boolean =
    receipt.processObservation.exists(process => SbtTestReassessment.assessFull(process).isRight)

  def standaloneSelectionC(input: StandaloneFixExecution.Input,
    selected: Vector[GoalPhaseNativeValidation.Check]): Consequence[Unit] =
    if (input.kind != "code" || (selected != null && selected.forall(_ != null) &&
        selected.exists(_.kind == T.Kind.Compile) && selected.exists(_.kind == T.Kind.TestOnly))) Consequence.unit
    else Consequence.stateConflict("New standalone code requests require selected Compile and TestOnly before execution")
}
