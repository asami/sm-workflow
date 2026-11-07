package org.simplemodeling.textus.smworkflow.admission

/** Structural uniqueness and authentication are separate checks. */
private[smworkflow] object GoalPhaseValidationSubjects {
  import GoalPhaseNativeValidation.Check

  def structurallyValid(checks: Vector[Check]): Boolean =
    checks != null && checks.forall(c => c != null && c.dependency != null) &&
      checks.map(_.identity).distinct.size == checks.size &&
      checks.groupBy(_.dependency).values.forall(group => group.map(_.ordinal).distinct.size == group.size)

  def valid(checks: Vector[Check], authenticated: Vector[Check]): Boolean =
    structurallyValid(checks) && authenticated != null &&
      checks.filter(_.dependency.nonEmpty).forall(authenticated.contains)
}
