package org.simplemodeling.textus.smworkflow.caller

import io.circe.parser
import org.goldenport.cncf.workflow.WorkflowExecutionHarness as H
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseNativeMetadata, GoalPhasePhaseMetrics as M}
import org.simplemodeling.textus.smworkflow.value as V
import M.given

/** Passive projection of caller-retained execution resources. Nothing here
  * acquires usage, invokes a provider, reads a file, advances a Workflow or
  * certifies Phase-wide coverage from a final invocation.
  * @since   Oct. 29, 2026
  * @version Nov.  2, 2026
  * @author  ASAMI, Tomoharu
  */
object NativeCallerPhaseMetrics {
  /** Destination/inclusion are the host's retained adapter observations, never
    * inferred from model names, localhost or executor placement. Unknown remains
    * explicit. Complete coverage must be established independently for the Phase.
    */
  final case class RetainedExecution(session: H.Session, destination: String, role: String,
    intervalStart: String, intervalEnd: String, inclusion: String = "unknown",
    coveredExecutions: Vector[String] = Vector.empty, completePhaseCoverage: Boolean = false)

  private[smworkflow] def observations(phaseSession: String, retained: Vector[RetainedExecution]): Vector[M.UsageObservation] =
    retained.filter(value => value.session != null && value.session.workReference.handle.instanceIdentity.value == phaseSession)
      .flatMap { value =>
        val reference = value.session.workReference
        value.session.attempts.map { attempt =>
          val execution = reference.continuationId.value + ":attempt:" + attempt.selection.attempt
          val resources = attempt.outcome.resources
          M.UsageObservation("caller-execution:" + execution, 1L, phaseSession, execution,
            if (Set("codex", "local", "other")(value.destination)) value.destination else "unknown", value.role,
            attempt.outcome.executor.map(_.profile.model), value.intervalStart, value.intervalEnd,
            resources.inputTokens, resources.outputTokens, inclusion = value.inclusion,
            coveredExecutions = value.coveredExecutions, coverage = if (value.completePhaseCoverage) "complete" else "partial",
            status = attempt.outcome.status.value)
        }
      }

  /** Reprojects only a completion with its native retained endpoint record. The
    * caller retains this output alongside its original harness resources. A
    * later recollection uses those same records, not later account usage.
    * Missing/invalid optional metrics leave the native completion unchanged.
    */
  def project(phaseSession: String, completion: V.GoalPhaseCompletionResult,
    retained: Vector[RetainedExecution]): V.GoalPhaseCompletionResult = {
    val summary = completion.metrics.flatMap(record => record.toJsonStringC.toOption
      .flatMap(text => parser.parse(text).toOption)
      .flatMap(json => GoalPhaseNativeMetadata.normalizePublicJson(json).toOption)
      .flatMap(_.as[M.Summary].toOption))
    summary.filter(_.phaseSession == phaseSession).map { original =>
      val observed = M.summarize(phaseSession, original.start, original.end,
        original.usage.flatMap(_.observations) ++ observations(phaseSession, retained), original.profile)
        .copy(issuedProfiles = original.issuedProfiles)
      completion.copy(metrics = Some(M.record(observed)))
    }.getOrElse(completion)
  }
}
