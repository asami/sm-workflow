package org.simplemodeling.textus.smworkflow.admission

import io.circe.Codec
import java.time.{Duration, Instant}

/** Observational comparable cohorts, using retained records only. Unknown time,
  * usage and first-pass history remain unknown, never zero or a success claim.
  * @since   Nov.  2, 2026
  * @version Nov.  2, 2026
  */
private[smworkflow] object GoalPhaseWorkComparison {
  final case class Cohort(identity: String, unit: String, workClass: String, windowStart: Option[String],
    windowEnd: String, sourceRevision: String) derives Codec.AsObject
  final case class Work(identity: String, unit: String, workClass: String, outcome: String,
    firstPass: Option[Boolean], startedAt: Option[String], terminalAt: Option[String],
    profiles: Vector[String], executions: Vector[String]) derives Codec.AsObject
  final case class Time(identity: String, completionMillis: Option[Long], elapsedMillis: Option[Long], reason: String) derives Codec.AsObject
  final case class Summary(cohort: Cohort, outcomes: Map[String, Int], terminal: Int, pending: Int,
    eventualSuccess: Int, firstPassSuccess: Int, unknownFirstPass: Int, eventualRate: Option[BigDecimal],
    firstPassRate: Option[BigDecimal], times: Vector[Time], profiles: Vector[String], mixed: Vector[String],
    usage: Vector[GoalPhasePhaseMetrics.Usage], limitations: Vector[String])
  object Summary {
    import io.circe.{Encoder, Decoder}
    import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
    import GoalPhasePhaseMetrics.given
    given Encoder[Summary] = deriveEncoder
    given Decoder[Summary] = deriveDecoder
  }
  final case class NativeWork(stepIdentity: Option[String], kind: String, profile: Option[GoalPhaseOperatingProfiles.Capture],
    continuationIdentity: Option[String] = None, outcome: Option[String] = None)
  private val _terminal = Set("accepted", "failed", "exhausted", "cancelled", "deferred", "abandoned")
  private def _millis(start: Option[String], end: Option[String]): Option[Long] = for {
    a <- start.flatMap(x => scala.util.Try(Instant.parse(x)).toOption)
    b <- end.flatMap(x => scala.util.Try(Instant.parse(x)).toOption)
    duration <- Option.when(!b.isBefore(a))(Duration.between(a, b).toMillis)
  } yield duration
  def project(session: String, cohort: Cohort, work: Vector[Work], usage: Vector[GoalPhasePhaseMetrics.UsageObservation]): Summary = {
    require(Set("Task", "Step")(cohort.unit), "Declare one comparable Task/Step unit")
    val scoped = work.filter(w => w.unit == cohort.unit && w.workClass == cohort.workClass)
    val groups = scoped.groupBy(_.identity)
    val conflicts = groups.collect { case (id, rows) if rows.distinct.size != 1 => id }.toVector.sorted
    val selected = groups.toVector.filterNot(x => conflicts.contains(x._1)).map(_._2.head).sortBy(_.identity)
    require(selected.forall(w => _terminal(w.outcome) || w.outcome == "pending"), "Unknown outcomes are not accepted")
    val terminal = selected.filter(w => _terminal(w.outcome))
    val accepted = terminal.count(_.outcome == "accepted")
    val first = terminal.count(w => w.outcome == "accepted" && w.firstPass.contains(true))
    val unknown = terminal.count(w => w.outcome == "accepted" && w.firstPass.isEmpty)
    val ids = selected.flatMap(_.executions).toSet
    val resources = usage.filter(o => ids(o.execution))
    val overlapping = work.filter(w => w.unit != cohort.unit || w.workClass != cohort.workClass).flatMap(_.executions).toSet.intersect(ids)
    val missing = ids -- resources.map(_.execution).toSet
    val excludedDestinations = resources.filter(o => overlapping(o.execution)).map(_.destination).toSet
    val totals = GoalPhasePhaseMetrics.aggregate(session, resources.filterNot(o => overlapping(o.execution)), None,
      "Cohort resource total; no source-line denominator").map { total =>
      if (excludedDestinations(total.destination) || missing.nonEmpty || (overlapping.nonEmpty && excludedDestinations.isEmpty))
        total.copy(coverage = if (total.coverage == "complete") "partial" else total.coverage,
          reason = total.reason + "; known execution attribution was excluded or has no retained observation; completeness is not claimed")
      else total
    }
    val times = selected.map { w =>
      val completion = if (w.outcome == "accepted") _millis(w.startedAt, w.terminalAt) else None
      val elapsed = if (w.outcome == "accepted") None else _millis(w.startedAt, w.terminalAt.orElse(Some(cohort.windowEnd)))
      Time(w.identity, completion, elapsed, if (completion.nonEmpty) "Accepted terminal completion, including recorded waits and repairs"
        else if (elapsed.nonEmpty) "Elapsed unsuccessful/open work; no completion claimed" else "Original timestamps unavailable or incompatible")
    }
    Summary(cohort, selected.groupMapReduce(_.outcome)(_ => 1)(_ + _), terminal.size, selected.count(_.outcome == "pending"),
      accepted, first, unknown, Option.when(terminal.nonEmpty)(BigDecimal(accepted) / terminal.size),
      Option.when(terminal.nonEmpty && unknown == 0)(BigDecimal(first) / terminal.size), times,
      selected.flatMap(_.profiles).distinct.sorted, selected.filter(_.profiles.distinct.size > 1).map(_.identity),
      totals,
      Vector("Ordinary-work observations do not establish model causality; missing resources are unavailable") ++
        Option.when(unknown > 0)("First-pass history is incomplete; rate unavailable") ++
        Option.when(conflicts.nonEmpty)("Contradictory repeated work records excluded: " + conflicts.mkString(",")) ++
        Option.when(missing.nonEmpty)("Referenced executions have no retained usage: " + missing.toVector.sorted.mkString(",")) ++
        Option.when(overlapping.nonEmpty)("Cross-cohort execution attribution excluded: " + overlapping.toVector.sorted.mkString(",")))
  }
  def native(plan: GoalPhaseOrderedProgression.Plan, completed: Vector[GoalPhaseOrderedProgression.ClosedStep],
    requests: Vector[NativeWork], retained: Option[GoalPhaseRetainedObservations.Retained] = None): Vector[Work] = plan.steps.map { step =>
    val records = requests.filter(_.stepIdentity.contains(step.identity))
    val profiles = records.flatMap(_.profile).map(p => p.selection.identity + "@" + p.selection.revision +
      p.mappingRevision.map("/" + _).getOrElse("")).distinct
    val repaired = records.exists(_.kind == "RepairStep")
    val accepted = completed.exists(_.step == step.identity)
    val attempts = retained.toVector.flatMap(_.attempts).filter(_.stepIdentity.contains(step.identity))
    val original = records.find(_.kind == "ImplementStep").flatMap(_.continuationIdentity)
    val initial = attempts.filter(a => original.contains(a.continuationIdentity))
    val start = if (initial.nonEmpty && initial.forall(_.startedAt.nonEmpty))
      initial.flatMap(_.startedAt).sortWith((a, b) => Instant.parse(a).isBefore(Instant.parse(b))).headOption else None
    val terminal = retained.toVector.flatMap(_.closures).find(_.stepIdentity == step.identity).map(_.endedAt)
    val completeHistory = retained.exists(_.completeHistorySteps.contains(step.identity)) && records.nonEmpty &&
      records.forall(r => r.continuationIdentity.exists(id => attempts.exists(_.continuationIdentity == id))) &&
      records.forall(_.outcome.exists(Set("completed", "accept", "clean")))
    // Multiple launches for one native assignment may be retries or related
    // execution contexts. Retained v1 facts cannot distinguish them, so they
    // cannot establish first-pass success even when every launch completed.
    val singleLaunchPerContinuation = attempts.groupBy(_.continuationIdentity).values.forall(
      _.map(_.executionIdentity).distinct.size == 1)
    val first = if (repaired || attempts.exists(a => Set("failed", "declined", "cancelled")(a.status))) Some(false)
      else Option.when(accepted && completeHistory && singleLaunchPerContinuation && attempts.forall(_.status == "completed"))(true)
    Work(step.identity, "Step", step.workClassification.map(_.implementation.toString).getOrElse("unclassified"),
      if (accepted) "accepted" else "pending", first,
      start, if (accepted) terminal else None, profiles, attempts.map(_.executionIdentity).distinct)
  }
  def english(summary: Summary): String = {
    val rate = (r: Option[BigDecimal]) => r.map(_.bigDecimal.toPlainString).getOrElse("unavailable")
    "Cohort " + summary.cohort.identity + " (" + summary.cohort.unit + ", class=" + summary.cohort.workClass +
      ", source=" + summary.cohort.sourceRevision + ", window=" + summary.cohort.windowStart.getOrElse("unknown") + ".." + summary.cohort.windowEnd +
      "): eventual=" + summary.eventualSuccess + "/" + summary.terminal + " (" + rate(summary.eventualRate) +
      "); first-pass=" + summary.firstPassSuccess + "/" + summary.terminal + " (" + rate(summary.firstPassRate) +
      "); unknown first-pass=" + summary.unknownFirstPass + "; pending=" + summary.pending +
      "; outcomes=" + summary.outcomes.toVector.sortBy(_._1).mkString(",") + "; profiles=" + summary.profiles.mkString(",") +
      "; mixed=" + summary.mixed.mkString(",") + ". " +
      summary.times.map(t => t.identity + " completion-ms=" + t.completionMillis.map(_.toString).getOrElse("unavailable") +
        " elapsed-ms=" + t.elapsedMillis.map(_.toString).getOrElse("unavailable")).mkString("; ") + ". " +
      summary.usage.map(u => u.destination + " tokens=" + u.totalTokens.map(_.toString).getOrElse("unavailable") + " " + u.coverage).mkString("; ") +
      ". " + summary.limitations.mkString("; ")
  }
}
