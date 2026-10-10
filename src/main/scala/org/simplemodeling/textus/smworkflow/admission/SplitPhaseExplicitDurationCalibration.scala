package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseDurationCalibration.{Policy, WorkItem}

/** Hash-free deterministic calibration. References and stored facts are data,
  * not authority; current records must come from independently authorized readers.
  * Reuses only hash-free value types, never the historical calibration functions.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseExplicitDurationCalibration {
  final case class Reference(identity: String, revision: String)
  final case class Observation(reference: Reference, workClassKey: String,
    repositoryClass: String, plannedMinutes: Option[Int], actualMinutes: Int,
    normalizedUnits: Int, completedAtMillis: Long, authorityReference: String)
  final case class Snapshot(reference: Reference, observations: Vector[Observation])
  final case class PolicyRecord(reference: Reference, policy: Policy)
  final case class Request(snapshot: Snapshot, policy: PolicyRecord, workItem: WorkItem)
  /** Constructed once at a load boundary, never decoded or copied as data.
    * Immutability lets internal calculation trust the construction rules.
    */
  final class Input private[SplitPhaseExplicitDurationCalibration] (
    private[SplitPhaseExplicitDurationCalibration] val request: Request
  )
  final case class Exclusion(reference: Reference, reason: String)
  sealed trait Outcome {
    def snapshotReference: Reference
    def policyReference: Reference
    def acceptedObservations: Vector[Reference]
    def exclusions: Vector[Exclusion]
  }
  object Outcome {
    final case class Known(estimateMinutes: Int, method: String,
      snapshotReference: Reference, policyReference: Reference,
      acceptedObservations: Vector[Reference], exclusions: Vector[Exclusion]) extends Outcome
    final case class UnresolvedNovel(reason: String,
      snapshotReference: Reference, policyReference: Reference,
      acceptedObservations: Vector[Reference], exclusions: Vector[Exclusion]) extends Outcome
  }

  def loadC(current: Request): Consequence[Input] =
    if (!complete(current))
      Consequence.stateConflict("SplitPhase explicit calibration input or policy is incomplete")
    else Consequence.success(new Input(current))

  /** Internal numerical work consumes only a constructed Input. There is no
    * record polling, repeated invariant check, lock, claim or contention retry.
    */
  def calculateC(input: Input): Consequence[Outcome] = {
      val request = input.request
      val policy = request.policy.policy
      val item = request.workItem
      val observations = request.snapshot.observations
      val duplicates = observations.groupBy(_.reference.identity)
        .collect { case (id, rows) if rows.size > 1 => id }.toSet
      val classified = observations.sortBy(o => (o.reference.identity, o.reference.revision))
        .map(o => o -> exclusion(o, item, policy, duplicates))
      val comparable = classified.collect { case (o, None) => o }
      val excluded = classified.collect { case (o, Some(reason)) => Exclusion(o.reference, reason) }
      val pairs = if (item.explicitBaselineMinutes.isDefined)
        comparable.filter(_.plannedMinutes.exists(_ > 0)) else comparable
      val accepted = pairs.map(_.reference)
      val missingPairs = if (item.explicitBaselineMinutes.isDefined)
        comparable.filter(_.plannedMinutes.isEmpty).map(o =>
          Exclusion(o.reference, "missing-planned-baseline-pair")) else Vector.empty
      val exclusions = (excluded ++ missingPairs).sortBy(e => (e.reference.identity, e.reference.revision))
      def unresolved(reason: String): Consequence[Outcome] = Consequence.success(
        Outcome.UnresolvedNovel(reason, request.snapshot.reference, request.policy.reference, accepted, exclusions))
      if (pairs.size < policy.minimumSampleCount)
        unresolved("insufficient-exact-class-evidence")
      else if (BigDecimal(item.normalizedUnits) >
          BigDecimal(pairs.map(_.normalizedUnits).max) * policy.maximumUnitsMultiplier)
        unresolved("outside-comparable-closure-envelope")
      else {
        val values = item.explicitBaselineMinutes match {
          case Some(_) => pairs.map(o => BigDecimal(o.actualMinutes) / BigDecimal(o.plannedMinutes.get))
          case None => pairs.map(o => BigDecimal(o.actualMinutes) / BigDecimal(o.normalizedUnits))
        }
        val ordered = values.sorted
        val median = if (ordered.size % 2 == 1) ordered(ordered.size / 2)
          else (ordered(ordered.size / 2 - 1) + ordered(ordered.size / 2)) / BigDecimal(2)
        val basis = median * BigDecimal(item.explicitBaselineMinutes.getOrElse(item.normalizedUnits))
        val quantum = BigDecimal(policy.roundingQuantumMinutes)
        val rounded = (basis / quantum).setScale(0, BigDecimal.RoundingMode.HALF_UP) * quantum
        val total = rounded + policy.overheadMinutesByRepositoryClass(item.repositoryClass)
        if (total > BigDecimal(Int.MaxValue) || total <= 0)
          Consequence.stateConflict("SplitPhase explicit calibrated duration is outside integer range")
        else Consequence.success(Outcome.Known(total.toInt,
          if (item.explicitBaselineMinutes.isDefined) "median-planned-actual-ratio" else "median-actual-per-unit",
          request.snapshot.reference, request.policy.reference, accepted, exclusions))
      }
    }

  private def exclusion(o: Observation, item: WorkItem, policy: Policy,
    duplicates: Set[String]): Option[String] =
    if (duplicates.contains(o.reference.identity)) Some("duplicate-observation")
    else if (!name(o.workClassKey) || !name(o.repositoryClass) || !name(o.authorityReference) ||
        o.actualMinutes <= 0 || o.normalizedUnits <= 0 || o.plannedMinutes == null ||
        o.plannedMinutes.exists(_ <= 0)) Some("incomplete-or-invalid-evidence")
    else if (o.workClassKey != item.workClassKey || o.repositoryClass != item.repositoryClass)
      Some("different-work-class")
    else if (o.completedAtMillis < policy.windowStartMillis || o.completedAtMillis > policy.windowEndMillis)
      Some("outside-history-window")
    else if (BigDecimal(o.actualMinutes) / BigDecimal(o.normalizedUnits) > policy.maximumMinutesPerUnit ||
        o.plannedMinutes.exists(minutes => BigDecimal(o.actualMinutes) / BigDecimal(minutes) > policy.maximumRatio))
      Some("policy-outlier")
    else None

  private def complete(r: Request): Boolean =
    r != null && r.snapshot != null && reference(r.snapshot.reference) &&
      r.snapshot.observations != null && r.snapshot.observations.forall(o => o != null && reference(o.reference)) &&
      r.policy != null && reference(r.policy.reference) && r.policy.policy != null && {
        val p = r.policy.policy
        val item = r.workItem
        name(p.version) && p.minimumSampleCount > 0 && p.windowStartMillis >= 0 &&
          p.windowEndMillis >= p.windowStartMillis && p.maximumRatio != null && p.maximumRatio > 0 &&
          p.maximumMinutesPerUnit != null && p.maximumMinutesPerUnit > 0 &&
          p.maximumUnitsMultiplier != null && p.maximumUnitsMultiplier >= 1 && p.roundingQuantumMinutes > 0 &&
          p.overheadMinutesByRepositoryClass != null && p.overheadMinutesByRepositoryClass.nonEmpty &&
          p.overheadMinutesByRepositoryClass.forall((key, minutes) => name(key) && minutes >= 0) &&
          item != null && name(item.identity) && name(item.workClassKey) && name(item.repositoryClass) &&
          item.normalizedUnits > 0 && item.explicitBaselineMinutes != null &&
          item.explicitBaselineMinutes.forall(_ > 0) && p.overheadMinutesByRepositoryClass.contains(item.repositoryClass)
      }
  private def reference(value: Reference): Boolean = value != null && name(value.identity) && name(value.revision)
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
