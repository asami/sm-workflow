package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.ContextReference
import org.simplemodeling.textus.smworkflow.value as V

/** One declared producer: the first completed Test/TestOnly captured stdout
  * in a native validation batch. Neither incomplete results nor Compile are logs
  * selected by this delivery. Action outcomes and repair accounting are unchanged.
  * @since   Oct. 20, 2026
  * @version Oct. 20, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeEvidence {
  final case class Selected(receipt: RepositorySyncNativeMergeValidation.Receipt, source: CommonEvidence.Source)
  def selected(payload: GoalPhaseNativeValidation.Payload, session: String, repository: String, phase: String): Option[Selected] = {
    val selected = payload.receipts.zipWithIndex.find { (r, i) =>
      r.termination == "exited" && r.exitCode.nonEmpty && payload.selected.lift(i).exists(c =>
        c.identity == r.identity && (c.kind == WorkflowSbtValidation.Kind.Test || c.kind == WorkflowSbtValidation.Kind.TestOnly))
    }
    selected.map { (receipt, index) =>
        val attempt = payload.stopped.count(_.identity == receipt.identity) +
          payload.admissionFailures.count(_.checkIdentity == receipt.identity) + payload.newAttempts.count(_.checkIdentity == receipt.identity)
        val key = "check-result-" + index + (if (attempt == 0) "" else "-retry-" + attempt)
        val reference = ContextReference(session + ":ordered-validation:" + payload.sourceRevision + ":" + key, "1")
        val source = CommonEvidence.Source(reference, repository, phase,
          Option(payload.step).filterNot(_.startsWith("phase:")), session, "stdout",
          if (RepositorySyncNativeMergeValidation.productSuccessful(receipt, payload.selected(index).kind)) "passed" else "failed")
        Selected(receipt, source)
    }
  }
  def recordC(d: GoalPhaseNativeRun.Declaration, core: ActionCall.Core, session: String,
    payload: GoalPhaseNativeValidation.Payload): Consequence[Vector[V.EvidenceRecordingState]] = {
    (d.evidencePolicy, selected(payload, session, d.input.repositoryIdentity, d.input.phaseIdentity)) match {
      case (Some(p), Some(Selected(receipt, source))) =>
        try {
          CommonEvidence.archiveC(CommonEvidence.storage(core, p), p, source, receipt.stdout, core.executionContext.clock.instant()).flatMap {
            case (state, Some((allocation, observation))) =>
              DevelopmentJournalEvidence.recordC(d.journalPolicy, core, allocation, observation)
                .map(journal => Vector(state.copy(journal = Some(journal))))
            case (state, None) => Consequence.success(Vector(state))
          }
        } catch { case scala.util.control.NonFatal(_) => Consequence.success(Vector(
          V.EvidenceRecordingState("indeterminate", "Evidence producer interrupted; original Test result retained", None, None, None))) }
      case (Some(_), None) if payload.selected.exists(c =>
          c.kind == WorkflowSbtValidation.Kind.Test || c.kind == WorkflowSbtValidation.Kind.TestOnly) =>
        Consequence.success(Vector(V.EvidenceRecordingState("pending",
          "Selected Test has no completed released stdout; no archive effect", None, None, None)))
      case _ => Consequence.success(Vector.empty)
    }
  }
}
