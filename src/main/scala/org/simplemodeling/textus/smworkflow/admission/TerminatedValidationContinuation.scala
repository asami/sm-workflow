package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.processexecution.ProcessExecutionTermination
import StandaloneFixExecution as S
import WorkflowSbtValidation as V

/** Eligibility over the original managed observation, never a process detector.
  * Old Wait checkpoints retain the complete attempt when the active prefix moves.
  * @since   Nov.  6, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object TerminatedValidationContinuation {
  /** A canceled ScalaTest case is not a canceled process. Only a separately
    * admitted direction can select another attempt of this known-ended check.
    * Actual assertion failures and ambiguous/incomplete observations stay out.
    */
  def unverified(state: S.State): Boolean = state != null && state.stage == "Wait" &&
    state.pendingCheck.isEmpty && state.candidate.nonEmpty && state.receipts.nonEmpty &&
    state.receipts.size <= state.selected.size && state.semanticReference.nonEmpty &&
    state.receipts.dropRight(1).zip(state.selected).forall((receipt, check) =>
      receipt.identity == check.identity && RepositorySyncNativeMergeValidation.productSuccessful(receipt, check.kind)) && {
      val receipt = state.receipts.last
      val check = state.selected(state.receipts.size - 1)
      receipt.identity == check.identity && check.kind == V.Kind.TestOnly &&
        RepositorySyncNativeMergeValidation.storedProductOutputValid(receipt) &&
        receipt.sbt.exists(facts => facts.kind == check.kind && facts.outcome == V.Outcome.Unverified &&
          receipt.processObservation.exists(process =>
            process.termination == ProcessExecutionTermination.Exited(0) && WorkflowSbtCapture.valid(process, facts) &&
              !process.stdout.truncated && !process.stderr.truncated)) &&
        S.diagnostics(state, "selected-validation-continuation").lastOption.exists(d =>
          d.testExecution.failedTests.contains(0) && d.testExecution.testsRun.exists(_ > 0) &&
            d.testExecution.completedSuites.exists(_ > 0) &&
            d.groups.forall(g => !Set("test-failure", "suite-abort", "compiler-error", "compilation-failure")(g.kind)))
    }
  def eligible(state: S.State, progressDepth: Int = 0): Boolean = {
    val prefix = state.receipts.dropRight(1)
    progressDepth == 0 && state.stage == "Wait" && state.pendingCheck.isEmpty && state.candidate.nonEmpty &&
      state.receipts.nonEmpty && state.receipts.size <= state.selected.size &&
      prefix.zip(state.selected).forall { case (receipt, check) =>
        receipt.identity == check.identity && RepositorySyncNativeMergeValidation.productSuccessful(receipt, check.kind)
      } && {
        val receipt = state.receipts.last
        val check = state.selected(prefix.size)
        receipt.identity == check.identity && receipt.sbt.exists(facts =>
          facts.kind == check.kind && facts.outcome == V.Outcome.Incomplete &&
            receipt.processObservation.exists(process => WorkflowSbtCapture.valid(process, facts) &&
              receipt.termination == WorkflowSbtCapture.termination(process) &&
              receipt.exitCode == WorkflowSbtCapture.exitCode(process) &&
              receipt.stdout == WorkflowSbtCapture.text(process.stdout).value &&
              receipt.stderr == WorkflowSbtCapture.text(process.stderr).value && (process.termination match {
                case ProcessExecutionTermination.OutputLimitExceeded(_) |
                    ProcessExecutionTermination.TimedOut | ProcessExecutionTermination.Cancelled |
                    ProcessExecutionTermination.ArtifactLimitExceeded => true
                case _ => false
              })))
      }
  }
}
