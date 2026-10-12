package org.simplemodeling.textus.smworkflow.admission

/** Shared current calibration inputs. The explicit calibration owns computation.
  * @since   Sep. 27, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhaseDurationCalibration {
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
}
