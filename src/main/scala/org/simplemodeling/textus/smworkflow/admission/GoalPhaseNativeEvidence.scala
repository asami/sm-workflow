package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.ContextReference
import org.simplemodeling.textus.smworkflow.value as V

/** Native producer of every completed Test/TestOnly captured stdout
  * in a native validation batch. Neither incomplete results nor Compile are logs
  * selected for archival. Action outcomes and repair accounting are unchanged.
  * @since   Oct. 20, 2026
  * @version Oct. 21, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeEvidence {
  final case class Selected(receipt: RepositorySyncNativeMergeValidation.Receipt, source: CommonEvidence.Source)
  def selected(payload: GoalPhaseNativeValidation.Payload, session: String, repository: String, phase: String): Option[Selected] =
    selectedAll(payload, session, repository, phase).headOption
  def selectedAll(payload: GoalPhaseNativeValidation.Payload, session: String, repository: String, phase: String): Vector[Selected] = {
    val selected = payload.receipts.zipWithIndex.filter { (r, i) =>
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
    (d.evidencePolicy, selectedAll(payload, session, d.input.repositoryIdentity, d.input.phaseIdentity)) match {
      case (Some(p), outputs) if outputs.nonEmpty => outputs.foldLeft(Consequence.success(Vector.empty[V.EvidenceRecordingState])) { (acc, output) =>
        acc.flatMap { states =>
        val Selected(receipt, source) = output
        try {
          CommonEvidence.archiveManagedC(CommonEvidence.storage(core, p), p, source, receipt.stdout, "identity", core.executionContext.clock).flatMap {
            case (state, Some((allocation, observation))) =>
              val unavailable = V.JournalRecordingState("unavailable", "Evidence Journal recording unavailable; storage observation retained",
                None, V.WorkflowArtifactReference(observation.identity + ":storage", "1"))
              val journal = try DevelopmentJournalEvidence.recordC(d.journalPolicy, core, allocation, observation)
                .fold(_ => unavailable, value => value)
                catch { case scala.util.control.NonFatal(_) => unavailable }
              Consequence.success(states :+ state.copy(journal = Some(journal)))
            case (state, None) => Consequence.success(states :+ state)
          }
        } catch { case scala.util.control.NonFatal(_) => Consequence.success(states :+
          V.EvidenceRecordingState("indeterminate", "Evidence producer interrupted; original Test result retained", None, None, None)) }
        }
      }
      case (Some(_), outputs) if outputs.isEmpty && payload.selected.exists(c =>
          c.kind == WorkflowSbtValidation.Kind.Test || c.kind == WorkflowSbtValidation.Kind.TestOnly) =>
        Consequence.success(Vector(V.EvidenceRecordingState("pending",
          "Selected Test has no completed released stdout; no archive effect", None, None, None)))
      case _ => Consequence.success(Vector.empty)
    }
  }
}
