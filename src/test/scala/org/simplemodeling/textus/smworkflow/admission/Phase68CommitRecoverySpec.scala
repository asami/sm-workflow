/**
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import GoalPhaseInventoryAuthority as A
import GoalPhaseRemainingPlanAdoption as R
import GoalPhaseOrderedProgression as O

/** Focused C1/C2 specification. Reuses only the changed managed SQL recovery
  * episodes. All authority and negative
  * observation records are specification-owned, never live consent or evidence.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class Phase68CommitRecoverySpec extends org.scalatest.wordspec.AnyWordSpec
    with org.scalatest.matchers.should.Matchers with org.scalatest.GivenWhenThen {
  private def take[A](value: org.goldenport.Consequence[A]): A = SplitPhaseExplicitPartitionFixture.take(value)
  private val raw = DependencyValidationSpecificationFixture.plan.copy(acceptance = Some(
    GoalPhaseAcceptanceBoundary.Adoption("Deliver the selected API", Vector(
      GoalPhaseAcceptanceBoundary.Condition("behavior", "phase", true, "Preserve the selected API")))))
  private val plan = take(GoalPhaseAcceptanceBoundary.captureC(None, raw))
  private val append = "src/main/car/abi-manifest.json"
  private val proposed = raw.copy(revision = "2", steps = raw.steps.updated(0,
    raw.steps.head.copy(ownedPaths = raw.steps.head.ownedPaths :+ append)))
  private val captured = proposed.copy(effectiveAcceptance = plan.effectiveAcceptance)
  private val target = R.Target("fixture-session", "fixture-old-human", "2", "3", plan,
    DependencyValidationSpecificationFixture.primaryBase, Vector.empty, 1,
    GoalPhaseRepairProgress.snapshot(take(O.startC(plan, DependencyValidationSpecificationFixture.primaryBase))),
    ContextReference("fixture-inventory-result", "1"), GoalPhasePlanRevision.Captured(
      GoalPhasePlanRevision.Proposal("inventory-append", "1", "Package the selected API", proposed), captured),
    currentInventory = Some(R.CurrentInventoryTarget("fixture-component", plan.steps.head.identity,
      Vector(append), Vector("screen.scala", append))))
  private val boundary = A.Boundary("fixture-component", target.sessionId, "fixture-participant", plan.repository,
    plan.identity, plan.steps.head.identity, plan.steps.head.purpose, ContextReference("fixture-original-purpose", "1"),
    plan.effectiveAcceptance.get.usage.get, plan.effectiveAcceptance.get.conditions.asJson,
    A.operations, Vector("secrets"))

  "Explicit original-purpose inventory authority" should {
    "continue covered nonexecution once through the public managed recovery route after reopening" in {
      new GoalTaskMalformedIssuedWorkRecoverySpec().qualifyCoveringNonexecution()
    }
    "retain prior native claim-release history as a refusal even after a fresh description request" in {
      new GoalTaskMalformedIssuedWorkRecoverySpec().qualifyPriorClaimRefusal()
    }
    "supersede an inventory-only Human wait and connect the ABI append to one runtime-owned commit" in {
      new GoalPhaseNativeFindingRecordingSpec().qualifyRetainedInventoryAuthority(this)
    }
    "carry an ABI append without using a document SHA, inventory count or another Human answer" in {
      take(O.currentInventoryPathsC(plan, captured, plan.steps.head.identity)) shouldBe Vector(append)
      A.coversC(boundary, target, "fixture-component", "fixture-participant").isSuccess shouldBe true
      val binding = Json.obj("componentInstance" -> boundary.componentInstance.asJson, "scope" -> boundary.asJson)
      A.select(binding) shouldBe Right(binding)
      A.select(binding.mapObject(_.add("approved", Json.True))).isLeft shouldBe true
      A.select(binding.mapObject(_.add("scope", boundary.asJson.mapObject(_.add("notExecuted", Json.True))))).isLeft shouldBe true
    }

    "retain genuine boundary changes, literal exclusions and legacy absence rather than infer authority from inventory" in {
      Vector(boundary.copy(componentInstance = "another-component"), boundary.copy(sessionId = "another-session"),
        boundary.copy(stepPurpose = "A new effect"), boundary.copy(usage = "A different Usage Contract"),
        boundary.copy(conditions = Json.arr()), boundary.copy(permittedOperations = Vector("edit-source")),
        boundary.copy(excludedPaths = Vector("src/main/car"))).foreach { changed =>
        A.coversC(changed, target, "fixture-component", "fixture-participant").isSuccess shouldBe false
      }
      A.select(Json.obj("componentInstance" -> "fixture-component".asJson,
        "scope" -> Json.obj("candidatePaths" -> Vector(append).asJson))).isLeft shouldBe true
    }

    "preserve the distinction between Human UNKNOWN and independently covering nonexecution checkpoints" in {
      import GoalTaskMalformedIssuedWorkRecoveryFixture.*
      val human = GoalTaskMalformedIssuedWorkRecovery.Checkpoint(
        GoalTaskMalformedIssuedWorkRecovery.Direction(GoalTaskMalformedIssuedWorkRecoveryFixture.target,
          GoalTaskMalformedIssuedWorkRecovery.choices.head, "Fixture Human direction", "fixture-human", reference), "UNKNOWN")
      GoalTaskMalformedIssuedWorkRecovery.checkpointAdmitted(human) shouldBe true
      GoalTaskMalformedIssuedWorkRecovery.checkpointAdmitted(human.copy(externalEffects = "NOT_EXECUTED")) shouldBe false
      val observed = human.copy(direction = human.direction.copy(choice = GoalTaskMalformedIssuedWorkRecovery.nonexecutionChoice),
        externalEffects = "NOT_EXECUTED")
      GoalTaskMalformedIssuedWorkRecovery.checkpointAdmitted(observed) shouldBe true
      GoalTaskMalformedIssuedWorkRecovery.checkpointAdmitted(observed.copy(externalEffects = "UNKNOWN")) shouldBe false
    }
  }
}
