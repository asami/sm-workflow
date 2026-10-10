package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json

/** Declared wire structure, not a saved-value or encoder certificate.
  * Scalar decoding and numerical admission belong to their actual owners.
  * @since   Oct.  8, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseCalibrationRawShape {
  private def _object(fields: (String, Json => Boolean)*): Json => Boolean = value =>
    value.asObject.exists(objectvalue => objectvalue.keys.toSet == fields.map(_._1).toSet &&
      fields.forall { case (key, rule) => objectvalue(key).exists(rule) })
  private def _optional(rule: Json => Boolean): Json => Boolean = value => value.isNull || rule(value)
  private val _string: Json => Boolean = _.isString
  private val _number: Json => Boolean = _.isNumber
  private val _reference = _object("identity" -> _string, "revision" -> _string)
  private val _observation = _object("reference" -> _reference, "workClassKey" -> _string,
    "repositoryClass" -> _string, "plannedMinutes" -> _optional(_number), "actualMinutes" -> _number,
    "normalizedUnits" -> _number, "completedAtMillis" -> _number, "authorityReference" -> _string)
  private val _policy = _object("version" -> _string, "minimumSampleCount" -> _number,
    "windowStartMillis" -> _number, "windowEndMillis" -> _number, "maximumRatio" -> _number,
    "maximumMinutesPerUnit" -> _number, "maximumUnitsMultiplier" -> _number,
    "roundingQuantumMinutes" -> _number,
    "overheadMinutesByRepositoryClass" -> (value => value.asObject.exists(_.values.forall(_number))))
  private val _work_item = _object("identity" -> _string, "workClassKey" -> _string,
    "repositoryClass" -> _string, "normalizedUnits" -> _number,
    "explicitBaselineMinutes" -> _optional(_number))

  val snapshot: Json => Boolean = _object("reference" -> _reference,
    "observations" -> (value => value.asArray.exists(_.forall(_observation))))
  val policy: Json => Boolean = _object("reference" -> _reference, "policy" -> _policy)
  val request: Json => Boolean = _object("snapshot" -> snapshot, "policy" -> policy, "workItem" -> _work_item)
  val record: Json => Boolean = _object("reference" -> _reference, "request" -> request)
}
