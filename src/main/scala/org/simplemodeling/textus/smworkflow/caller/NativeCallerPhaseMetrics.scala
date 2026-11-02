package org.simplemodeling.textus.smworkflow.caller

import io.circe.parser
import org.goldenport.cncf.workflow.WorkflowExecutionHarness as H
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseNativeMetadata, GoalPhasePhaseMetrics as M, GoalPhaseWorkComparison as C}
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

  /** Resources already retained by the new adapter, with provider model unknown
    * unless separately observed. Execution identity is the real launch, not a role.
    */
  final case class DispatchExecution(issued: V.WorkflowIssuedWorkContext, launchIdentity: String,
    destination: String, role: String, observedModel: Option[String], intervalStart: String, intervalEnd: String,
    inputTokens: Option[Long], outputTokens: Option[Long], inclusion: String = "unknown",
    coveredExecutions: Vector[String] = Vector.empty, completePhaseCoverage: Boolean = false, status: String = "unknown")
  private[smworkflow] def dispatchObservations(phaseSession: String,
    retained: Vector[DispatchExecution]): Vector[M.UsageObservation] = retained.filter(value =>
      value.issued != null && value.issued.runId == phaseSession && value.launchIdentity != null &&
        value.launchIdentity.nonEmpty && NativeWorkflowCallerExecution.validateIssuedC(value.issued).isSuccess).map { value =>
      val execution = value.issued.continuationId + ":launch:" + value.launchIdentity
      M.UsageObservation("caller-dispatch:" + execution, 1L, phaseSession, execution,
        if (Set("codex", "local", "other")(value.destination)) value.destination else "unknown", value.role,
        value.observedModel, value.intervalStart, value.intervalEnd, value.inputTokens, value.outputTokens,
        inclusion = value.inclusion, coveredExecutions = value.coveredExecutions,
        coverage = if (value.completePhaseCoverage) "complete" else "partial", status = value.status)
    }

  final case class ComparableCohort(identity: String, unit: String, workClass: String,
    windowStart: Option[String], windowEnd: String, sourceRevision: String)
  final case class ComparableWork(identity: String, unit: String, workClass: String, outcome: String,
    firstPass: Option[Boolean], startedAt: Option[String], terminalAt: Option[String],
    profiles: Vector[String], executions: Vector[String])

  /** The caller declares a comparable cohort before aggregation and supplies only
    * already-retained work/attempt facts. This never acquires usage or closes work.
    */
  def compare(phaseSession: String, completion: V.GoalPhaseCompletionResult,
    cohort: ComparableCohort, work: Vector[ComparableWork], retained: Vector[RetainedExecution],
    dispatch: Vector[DispatchExecution] = Vector.empty): V.GoalPhaseCompletionResult = {
    val original = completion.metrics.flatMap(record => record.toJsonStringC.toOption
      .flatMap(text => parser.parse(text).toOption).flatMap(json => GoalPhaseNativeMetadata.normalizePublicJson(json).toOption)
      .flatMap(_.as[M.Summary].toOption))
    original.filter(_.phaseSession == phaseSession).map { summary =>
      val resources = summary.usage.flatMap(_.observations) ++ observations(phaseSession, retained) ++ dispatchObservations(phaseSession, dispatch)
      val comparison = C.project(phaseSession, C.Cohort(cohort.identity, cohort.unit, cohort.workClass,
        cohort.windowStart, cohort.windowEnd, cohort.sourceRevision), work.map(w => C.Work(w.identity, w.unit,
          w.workClass, w.outcome, w.firstPass, w.startedAt, w.terminalAt, w.profiles, w.executions)), resources)
      completion.copy(metrics = Some(M.record(summary.copy(comparison = summary.comparison.filterNot(_.cohort.identity == cohort.identity) :+ comparison))))
    }.getOrElse(completion)
  }

  /** Reprojects only a completion with its native retained endpoint record. The
    * caller retains this output alongside its original harness resources. A
    * later recollection uses those same records, not later account usage.
    * Missing/invalid optional metrics leave the native completion unchanged.
    */
  def project(phaseSession: String, completion: V.GoalPhaseCompletionResult,
    retained: Vector[RetainedExecution], dispatch: Vector[DispatchExecution] = Vector.empty): V.GoalPhaseCompletionResult = {
    val summary = completion.metrics.flatMap(record => record.toJsonStringC.toOption
      .flatMap(text => parser.parse(text).toOption)
      .flatMap(json => GoalPhaseNativeMetadata.normalizePublicJson(json).toOption)
      .flatMap(_.as[M.Summary].toOption))
    summary.filter(_.phaseSession == phaseSession).map { original =>
      val observed = M.summarize(phaseSession, original.start, original.end,
        original.usage.flatMap(_.observations) ++ observations(phaseSession, retained) ++ dispatchObservations(phaseSession, dispatch), original.profile)
        .copy(issuedProfiles = original.issuedProfiles, comparison = original.comparison, retainedObservations = original.retainedObservations)
      completion.copy(metrics = Some(M.record(observed)))
    }.getOrElse(completion)
  }
}
