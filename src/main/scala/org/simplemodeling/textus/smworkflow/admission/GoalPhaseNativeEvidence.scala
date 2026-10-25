package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.ContextReference
import org.simplemodeling.textus.smworkflow.value as V

/** Native producer of completed Test stdout and available failed-check details.
  * Diagnostic prefixes have distinct output identities; neither storage nor
  * extraction changes the original action outcome or repair accounting.
  * @since   Oct. 20, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeEvidence {
  final case class Selected(receipt: RepositorySyncNativeMergeValidation.Receipt, source: CommonEvidence.Source)
  private def _source(payload: GoalPhaseNativeValidation.Payload, index: Int, session: String,
    repository: String, phase: String, output: String): CommonEvidence.Source = {
    val receipt = payload.receipts(index)
    val attempt = payload.stopped.count(_.identity == receipt.identity) +
      payload.admissionFailures.count(_.checkIdentity == receipt.identity) + payload.newAttempts.count(_.checkIdentity == receipt.identity)
    val key = "check-result-" + index + (if (attempt == 0) "" else "-retry-" + attempt)
    val reference = ContextReference(session + ":ordered-validation:" + payload.sourceRevision + ":" + key, "1")
    val actualrepository = payload.selected(index).dependency.flatMap(_ => payload.dependency)
      .map(_.admission.binding.dependency.repository).getOrElse(repository)
    CommonEvidence.Source(reference, actualrepository, phase,
      Option(payload.step).filterNot(_.startsWith("phase:")), session, output,
      if (RepositorySyncNativeMergeValidation.productSuccessful(receipt, payload.selected(index).kind)) "passed" else "failed")
  }
  def selected(payload: GoalPhaseNativeValidation.Payload, session: String, repository: String, phase: String): Option[Selected] =
    selectedAll(payload, session, repository, phase).headOption
  def selectedAll(payload: GoalPhaseNativeValidation.Payload, session: String, repository: String, phase: String): Vector[Selected] = {
    val selected = payload.receipts.zipWithIndex.filter { (r, i) =>
      r.termination == "exited" && r.exitCode.nonEmpty && payload.selected.lift(i).exists(c =>
        c.identity == r.identity && (c.kind == WorkflowSbtValidation.Kind.Test || c.kind == WorkflowSbtValidation.Kind.TestOnly))
    }
    selected.map { (receipt, index) => Selected(receipt, _source(payload, index, session, repository, phase, "stdout")) }
  }

  /** Only released typed capture data, never a live stream. Incomplete capture
    * prefixes retain that fact in their source identity and original receipt.
    * Legacy Test stdout associations remain exactly the same.
    */
  def diagnosticSelections(payload: GoalPhaseNativeValidation.Payload, session: String,
    repository: String, phase: String): Vector[Selected] = {
    val legacy = selectedAll(payload, session, repository, phase)
    val diagnostic = payload.receipts.zipWithIndex.flatMap { (receipt, index) =>
      payload.selected.lift(index).filter(c => c.identity == receipt.identity &&
        receipt.sbt.exists(f => f.outcome == WorkflowSbtValidation.Outcome.Failed ||
          f.outcome == WorkflowSbtValidation.Outcome.Incomplete)).toVector.flatMap { _ =>
        receipt.processObservation.toVector.flatMap { process =>
          Vector("stdout" -> process.stdout, "stderr" -> process.stderr).flatMap { (stream, capture) =>
            val text = WorkflowSbtCapture.text(capture)
            Option.when(text.textStatus != "unavailable" && text.value.nonEmpty)(
              Selected(receipt, _source(payload, index, session, repository, phase,
                stream + (if (capture.truncated) "-prefix" else ""))))
          }
        }
      }
    }
    (legacy ++ diagnostic).distinctBy(_.source)
  }
  def recordC(d: GoalPhaseNativeRun.Declaration, core: ActionCall.Core, session: String,
    payload: GoalPhaseNativeValidation.Payload): Consequence[Vector[V.EvidenceRecordingState]] = {
    (d.evidencePolicy, diagnosticSelections(payload, session, d.input.repositoryIdentity, d.input.phaseIdentity)) match {
      case (Some(p), outputs) if outputs.nonEmpty => outputs.foldLeft(Consequence.success(Vector.empty[V.EvidenceRecordingState])) { (acc, output) =>
        acc.flatMap { states =>
        val Selected(receipt, source) = output
        try {
          val text = if (source.output.startsWith("stderr")) receipt.stderr else receipt.stdout
          CommonEvidence.archiveManagedC(CommonEvidence.storage(core, p), p, source, text, "identity", core.executionContext.clock).flatMap {
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
          V.EvidenceRecordingState("indeterminate", "Evidence producer interrupted; original check result retained", None, None, None)) }
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
