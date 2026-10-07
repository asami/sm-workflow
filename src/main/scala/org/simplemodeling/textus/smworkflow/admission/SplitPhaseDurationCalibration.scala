package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence

/** Known-work calibration only. Novel work remains unresolved for a separate
  * semantic boundary; this result is not a partition or proposal admission.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseDurationCalibration {
  final case class Observation(
    observationId: String,
    workClassKey: String,
    repositoryClass: String,
    plannedMinutes: Option[Int],
    actualMinutes: Int,
    normalizedUnits: Int,
    completedAtMillis: Long,
    authorityReference: String,
    evidenceDigest: String
  )

  final case class Policy(
    version: String,
    minimumSampleCount: Int,
    windowStartMillis: Long,
    windowEndMillis: Long,
    maximumRatio: BigDecimal,
    maximumMinutesPerUnit: BigDecimal,
    maximumUnitsMultiplier: BigDecimal,
    roundingQuantumMinutes: Int,
    overheadMinutesByRepositoryClass: Map[String, Int]
  )

  final case class WorkItem(
    identity: String,
    workClassKey: String,
    repositoryClass: String,
    normalizedUnits: Int,
    explicitBaselineMinutes: Option[Int]
  )

  final case class Request(
    policyVersion: String,
    evidenceSnapshotDigest: String,
    policy: Policy,
    observations: Vector[Observation],
    workItem: WorkItem
  )

  final case class Exclusion(observationId: String, reason: String)
  sealed trait Outcome {
    def policyVersion: String
    def evidenceSnapshotDigest: String
    def acceptedObservationIds: Vector[String]
    def exclusions: Vector[Exclusion]
  }
  object Outcome {
    final case class Known(
      estimateMinutes: Int,
      method: String,
      policyVersion: String,
      evidenceSnapshotDigest: String,
      acceptedObservationIds: Vector[String],
      exclusions: Vector[Exclusion]
    ) extends Outcome

    final case class UnresolvedNovel(
      reason: String,
      policyVersion: String,
      evidenceSnapshotDigest: String,
      acceptedObservationIds: Vector[String],
      exclusions: Vector[Exclusion]
    ) extends Outcome
  }

  def calibrateC(request: Request): Consequence[Outcome] =
    if (!_complete(request))
      Consequence.stateConflict("SplitPhase calibration input or policy is incomplete")
    else if (request.policyVersion != request.policy.version ||
        request.evidenceSnapshotDigest != snapshotDigestOf(request.observations))
      Consequence.stateConflict("SplitPhase calibration policy or evidence snapshot differs")
    else {
      val policy = request.policy
      val item = request.workItem
      val duplicateIds = request.observations.groupBy(_.observationId)
        .collect { case (id, rows) if rows.size > 1 => id }.toSet
      val classified = request.observations.sortBy(_.observationId).map { observation =>
        observation -> _exclusion(observation, item, policy, duplicateIds)
      }
      val comparable = classified.collect { case (observation, None) => observation }
      val excluded = classified.collect { case (observation, Some(reason)) =>
        Exclusion(observation.observationId, reason)
      }
      val pairs = if (item.explicitBaselineMinutes.isDefined)
        comparable.filter(_.plannedMinutes.exists(_ > 0))
      else comparable
      val acceptedIds = pairs.map(_.observationId)
      val pairExclusions = if (item.explicitBaselineMinutes.isDefined)
        comparable.filter(_.plannedMinutes.isEmpty).map(o =>
          Exclusion(o.observationId, "missing-planned-baseline-pair"))
      else Vector.empty
      val allExclusions = (excluded ++ pairExclusions).sortBy(_.observationId)
      def unresolved(reason: String): Consequence[Outcome] =
        Consequence.success(Outcome.UnresolvedNovel(reason, policy.version,
          request.evidenceSnapshotDigest, acceptedIds, allExclusions))

      if (pairs.size < policy.minimumSampleCount)
        unresolved("insufficient-exact-class-evidence")
      else if (BigDecimal(item.normalizedUnits) >
          BigDecimal(pairs.map(_.normalizedUnits).max) * policy.maximumUnitsMultiplier)
        unresolved("outside-comparable-closure-envelope")
      else {
        val values = item.explicitBaselineMinutes match {
          case Some(_) => pairs.map(o => BigDecimal(o.actualMinutes) /
            BigDecimal(o.plannedMinutes.get))
          case None => pairs.map(o => BigDecimal(o.actualMinutes) /
            BigDecimal(o.normalizedUnits))
        }
        val basis = item.explicitBaselineMinutes match {
          case Some(minutes) => _median(values) * BigDecimal(minutes)
          case None => _median(values) * BigDecimal(item.normalizedUnits)
        }
        val quantum = BigDecimal(policy.roundingQuantumMinutes)
        val rounded = (basis / quantum).setScale(0, BigDecimal.RoundingMode.HALF_UP) * quantum
        val total = rounded + policy.overheadMinutesByRepositoryClass(item.repositoryClass)
        if (total > BigDecimal(Int.MaxValue) || total <= 0)
          Consequence.stateConflict("SplitPhase calibrated duration is outside integer range")
        else Consequence.success(Outcome.Known(total.toInt,
          if (item.explicitBaselineMinutes.isDefined) "median-planned-actual-ratio"
          else "median-actual-per-unit", policy.version,
          request.evidenceSnapshotDigest, acceptedIds, allExclusions))
      }
    }

  def observationDigestOf(observation: Observation): String = _sha(Vector(
    observation.observationId, observation.workClassKey, observation.repositoryClass,
    observation.plannedMinutes.fold("")(_.toString), observation.actualMinutes.toString,
    observation.normalizedUnits.toString, observation.completedAtMillis.toString,
    observation.authorityReference
  ))

  def snapshotDigestOf(observations: Vector[Observation]): String =
    _sha(observations.sortBy(o => (o.observationId, o.evidenceDigest)).flatMap(o =>
      Vector(o.observationId, o.evidenceDigest)))

  def policyDigestOf(policy: Policy): String = _sha(Vector(
    policy.version, policy.minimumSampleCount.toString,
    policy.windowStartMillis.toString, policy.windowEndMillis.toString,
    policy.maximumRatio.bigDecimal.toPlainString,
    policy.maximumMinutesPerUnit.bigDecimal.toPlainString,
    policy.maximumUnitsMultiplier.bigDecimal.toPlainString,
    policy.roundingQuantumMinutes.toString
  ) ++ policy.overheadMinutesByRepositoryClass.toVector.sortBy(_._1).flatMap {
    case (repositoryClass, minutes) => Vector(repositoryClass, minutes.toString)
  })

  private def _exclusion(
    o: Observation,
    item: WorkItem,
    policy: Policy,
    duplicates: Set[String]
  ): Option[String] =
    if (duplicates.contains(o.observationId)) Some("duplicate-observation")
    else if (!_name(o.workClassKey) || !_name(o.repositoryClass) ||
        !_name(o.authorityReference) || o.actualMinutes <= 0 ||
        o.normalizedUnits <= 0 || o.plannedMinutes == null ||
        o.plannedMinutes.exists(_ <= 0) || o.evidenceDigest != observationDigestOf(o))
      Some("incomplete-or-invalid-evidence")
    else if (o.workClassKey != item.workClassKey ||
        o.repositoryClass != item.repositoryClass) Some("different-work-class")
    else if (o.completedAtMillis < policy.windowStartMillis ||
        o.completedAtMillis > policy.windowEndMillis) Some("outside-history-window")
    else if (BigDecimal(o.actualMinutes) / BigDecimal(o.normalizedUnits) >
        policy.maximumMinutesPerUnit || o.plannedMinutes.exists(minutes =>
          BigDecimal(o.actualMinutes) / BigDecimal(minutes) > policy.maximumRatio))
      Some("policy-outlier")
    else None

  private def _median(values: Vector[BigDecimal]): BigDecimal = {
    val ordered = values.sorted
    if (ordered.size % 2 == 1) ordered(ordered.size / 2)
    else (ordered(ordered.size / 2 - 1) + ordered(ordered.size / 2)) / BigDecimal(2)
  }

  private def _complete(r: Request): Boolean =
    r != null && _name(r.policyVersion) && _digest(r.evidenceSnapshotDigest) &&
      r.policy != null && _name(r.policy.version) && r.policy.minimumSampleCount > 0 &&
      r.policy.windowStartMillis >= 0 && r.policy.windowEndMillis >= r.policy.windowStartMillis &&
      r.policy.maximumRatio != null && r.policy.maximumRatio > 0 &&
      r.policy.maximumMinutesPerUnit != null && r.policy.maximumMinutesPerUnit > 0 &&
      r.policy.maximumUnitsMultiplier != null && r.policy.maximumUnitsMultiplier >= 1 &&
      r.policy.roundingQuantumMinutes > 0 &&
      r.policy.overheadMinutesByRepositoryClass != null &&
      r.policy.overheadMinutesByRepositoryClass.nonEmpty &&
      r.policy.overheadMinutesByRepositoryClass.forall { case (key, minutes) =>
        _name(key) && minutes >= 0
      } && r.observations != null && r.observations.forall(o =>
        o != null && _name(o.observationId) && o.evidenceDigest != null) &&
      r.workItem != null && _name(r.workItem.identity) &&
      _name(r.workItem.workClassKey) && _name(r.workItem.repositoryClass) &&
      r.workItem.normalizedUnits > 0 && r.workItem.explicitBaselineMinutes != null &&
      r.workItem.explicitBaselineMinutes.forall(_ > 0) &&
      r.policy.overheadMinutesByRepositoryClass.contains(r.workItem.repositoryClass)

  private def _sha(fields: Vector[String]): String = {
    val bytes = fields.map(s => s"${s.length}:$s").mkString.getBytes(StandardCharsets.UTF_8)
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
  }
  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
  private def _digest(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")
}
