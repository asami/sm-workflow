package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class WorkflowValidationOrdinalSpec extends AnyWordSpec with Matchers {
  private val checks = (1 to 18).toVector.map(i => GoalPhaseNativeValidation.Check("check-" + i, i))

  "Validation ordinal admission" should {
    "retain all selected positive checks and reject invalid or duplicate ordinals" in {
      def valid(xs: Vector[GoalPhaseNativeValidation.Check]) =
        GoalPhaseNativeValidation.Policy("policy", 1, xs).validateC.isSuccess
      valid(checks) shouldBe true
      GoalPhaseNativeMetadata.validateRetainedC(checks, checks, Vector.empty).isSuccess shouldBe true
      GoalPhaseNativeMetadata.validateRuntimeC(checks, Vector.empty).isSuccess shouldBe true
      for (n <- Vector(0, -1)) {
        valid(checks.updated(17, checks(17).copy(ordinal = n))) shouldBe false
        GoalPhaseNativeMetadata.validateRetainedC(checks.updated(17, checks(17).copy(ordinal = n)),
          checks.updated(17, checks(17).copy(ordinal = n)), Vector.empty).isSuccess shouldBe false
      }
      valid(checks.updated(17, checks(17).copy(ordinal = 17))) shouldBe false
      valid(checks.updated(17, checks(17).copy(identity = checks(16).identity))) shouldBe false
      val sync = checks.map(c => RepositorySyncNativeMergeValidation.Check(c.identity, c.ordinal, c.kind))
      RepositorySyncNativeMergeValidation.Policy("policy", 1, "required", "selected", sync).validateC.isSuccess shouldBe true
    }

    "inspect the complete selected recovery namespace and preserve ordinal 17" in {
      GoalPhaseNativeValidation.requireUnstartedPrerequisiteC(
        key => Consequence.success(key == "check-intent-17"), checks.size).isSuccess shouldBe false
      GoalPhaseNativeValidation.requireUnstartedPrerequisiteC(_ => Consequence.success(false), checks.size).isSuccess shouldBe true
      for (count <- Vector(0, -1))
        GoalPhaseNativeValidation.requireUnstartedPrerequisiteC(_ => Consequence.success(false), count).isSuccess shouldBe false
      val hash = "a" * 40
      val snapshot = GoalPhaseOrderedProgression.Snapshot(hash, hash)
      val target = GoalPhaseValidationRecovery.RecoveryTarget("session", "plan", "1", snapshot, snapshot,
        "1", "3", "step", "check-17", 17, 0, "2")
      GoalPhaseValidationRecovery.validTarget(target) shouldBe true
      GoalPhaseValidationRecovery.validTarget(target.copy(checkOrdinal = 0)) shouldBe false
      GoalPhaseValidationRecovery.validTarget(target.copy(checkOrdinal = -1)) shouldBe false
    }
  }
}
