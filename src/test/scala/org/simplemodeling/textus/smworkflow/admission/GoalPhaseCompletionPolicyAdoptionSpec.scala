package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseCompletionPolicyAdoption as A
import SplitPhaseExplicitPartitionFixture.take

/** Strict normal Starts and legacy codec evidence, without store fabrication.
  * @since   Oct. 23, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseCompletionPolicyAdoptionSpec extends AnyWordSpec with GivenWhenThen with GoalPhasePostCompletionSyncFixture {
  override protected def goal_completion_policy: String = GoalPhasePostCompletionSync.strictPolicy

  "Explicit completion policy adoption" should {
    "keep a genuine missing legacy field strict and reject unknown or null policy values" in {
      Given("GPSYNC-06: a historical Start codec fixture with no completionPolicy field")
      val snapshot = GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40)
      val payload = GoalPhaseNativeRun.Payload("participant", "/fixture/repository", "main", _recovery_plan, snapshot)
      val historical = GoalPhaseNativeRun.codec.encode(payload)
      val body = historical.hcursor.downField("payload").focus.get
      body.hcursor.downField("completionPolicy").focus shouldBe None
      val unknown = historical.mapObject(_.add("payload", body.mapObject(_.add("completionPolicy", Json.fromString("unknown")))))
      val explicitnull = historical.mapObject(_.add("payload", body.mapObject(_.add("completionPolicy", Json.Null))))
      When("the normal Start codec reads historical and unsupported policy selections")
      val legacy = GoalPhaseNativeRun.codec.decode(historical)
      Then("missing means strict while explicit unsupported selections refuse; no persisted Start is changed")
      legacy shouldBe Right(payload)
      legacy.toOption.get.completionPolicy.getOrElse(GoalPhasePostCompletionSync.strictPolicy) shouldBe GoalPhasePostCompletionSync.strictPolicy
      GoalPhaseNativeRun.codec.decode(unknown).isLeft shouldBe true
      GoalPhaseNativeRun.codec.decode(explicitnull).isLeft shouldBe true
    }

    "preserve strict closure until exact independent adoption and retain all accepted identities history and counters" in {
      Given("GPSYNC-06: a normal explicitly strict Start whose required development is accepted before child Start")
      val root = freshRoot("strict-policy-adoption")
      var identity = ""
      var original = Option.empty[GoalPhaseNativeRepositoryClosure.Input]
      var directed = Option.empty[InstanceRecord]
      var target = Option.empty[A.Target]
      episode(root, initialize = true) { e =>
        val accepted = e.closeDevelopment()
        identity = accepted._1
        original = Some(accepted._2)
        val before = e.current(identity)
        When("normal strict Advance lacks independently granted closure authority")
        val waiting = e.advance(identity)
        Then("the Phase remains Active with the strict authority request and unchanged accepted frontier")
        waiting.kind shouldBe "Wait"
        waiting.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.PhaseClosure.identity
        sameInstance(e.current(identity), before)
        val description = e.describe(identity)
        target = Some(take(A.targetC(description.target)))
        target.get.input shouldBe original.get
        val noauthority = V.GoalPhaseCompletionPolicyAdoptionInput(identity, description.target, "AdoptLocalCompletion",
          "Unauthenticated direction", V.WorkflowArtifactReference("missing-direction", "1"))
        val missing = e.call(A.adoptionOperation, noauthority.toRecord())
        val defer = e.direction(description, "Defer", "policy-defer")
        When("a missing proof and then a genuine independent Defer are submitted")
        val deferred = e.adopt(defer)
        Then("neither changes native history or silently adopts local completion")
        missing.isSuccess shouldBe false
        deferred.outcome shouldBe "deferred"
        sameInstance(e.current(identity), before)
        val direction = e.direction(description, "AdoptLocalCompletion", "policy-adopt")
        val crossed = direction.copy(sessionId = "different-session")
        e.call(A.adoptionOperation, crossed.toRecord()).isSuccess shouldBe false
        When("the exact independent AdoptLocalCompletion direction is appended")
        val result = e.adopt(direction)
        directed = Some(e.current(identity))
        Then("one self-transition retains the original accepted input and rejects repeated adoption")
        result.outcome shouldBe "local-completion-adopted"
        directed.get.history.dropRight(1) shouldBe before.history
        directed.get.currentProgression shouldBe before.currentProgression
        directed.get.lifecycle shouldBe Lifecycle.Active
        directed.get.revision shouldBe before.revision.next
        e.call(A.adoptionOperation, direction.toRecord()).isSuccess shouldBe false
      }
      val executions = phaseExecutions(root)
      episode(root) { e =>
        When("ordinary Advance reconstructs the saved policy direction after reopening")
        val result = e.advance(identity)
        Then("the same Phase completes without accepted work replay or different Plan base candidate Step or repair facts")
        result.kind shouldBe "Terminal"
        result.completion.get.selectedPlan.identity shouldBe original.get.plan.identity
        result.completion.get.originalBase.head shouldBe original.get.base.head
        result.completion.get.reviewedCandidate.head shouldBe original.get.candidate.head
        result.completion.get.completed.map(_.commit.identity) shouldBe original.get.completed.map(_.commit.identity)
        e.current(identity).history.take(directed.get.history.size) shouldBe directed.get.history
        phaseExecutions(root) shouldBe executions
        target.get.repairProgress shouldBe GoalPhaseRepairProgress.Snapshot(0, 0, _recovery_plan.repairLimit)
      }
    }

    "retain an already linked child without restarting or advancing it during adopted parent completion" in {
      Given("GPSYNC-04,05,06: an explicitly strict Phase starts its real child with no push authority")
      val root = freshRoot("linked-child-adoption")
      var identity = ""
      var child = ""
      var oldchild = Option.empty[InstanceRecord]
      var original = Option.empty[GoalPhaseNativeRepositoryClosure.Input]
      episode(root, initialize = true) { e =>
        val accepted = e.closeDevelopment()
        identity = accepted._1
        original = Some(accepted._2)
        e.authorize(accepted._2, effects = false)
        When("strict closure reaches the real child's missing effect authority")
        val failed = e.native.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(identity))
        Then("the parent remains open and normal policy description retains the existing child and original input")
        failed.isSuccess shouldBe false
        val described = e.describe(identity)
        val selected = take(A.targetC(described.target))
        selected.input shouldBe original.get
        child = selected.childSessionId.get
        oldchild = Some(e.child(child))
        oldchild.get.lifecycle shouldBe Lifecycle.Active
        val direction = e.direction(described, "AdoptLocalCompletion", "linked-policy-adopt")
        e.adopt(direction).outcome shouldBe "local-completion-adopted"
      }
      val executions = phaseExecutions(root)
      episode(root) { e =>
        When("the adopted parent completes and is reopened again")
        val result = e.advance(identity)
        val again = e.advance(identity)
        Then("Phase completion is independent while the original nonterminal child keeps its exact native history and own continuation")
        result.kind shouldBe "Terminal"
        again shouldBe result
        result.completion.get.synchronization.childSessionId shouldBe Some(child)
        result.completion.get.synchronization.advanceOperation shouldBe Some("advanceRepositorySync")
        result.completion.get.synchronization.state should not be "succeeded"
        sameInstance(e.child(child), oldchild.get)
        e.current(identity).lifecycle shouldBe Lifecycle.Completed
        result.completion.get.reviewedCandidate.head shouldBe original.get.candidate.head
        phaseExecutions(root) shouldBe executions
      }
    }

    "preserve a reserved legacy child whose Start was interrupted without guessing that it ran" in {
      Given("GPSYNC-05,06: strict closure saves the child intent and fails before actual Start")
      val root = freshRoot("missing-legacy-child")
      var identity = ""
      var child = ""
      val stop: String => Consequence[Unit] = point => if (point == "goal-repository-prepared")
        Consequence.stateConflict("fixture before strict child Start") else Consequence.unit
      episode(root, initialize = true, acknowledgement = Some(stop)) { e =>
        val accepted = e.closeDevelopment()
        identity = accepted._1
        e.authorize(accepted._2)
        e.native.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(identity)).isSuccess shouldBe false
        val description = e.describe(identity)
        child = take(A.targetC(description.target)).childSessionId.get
        e.childOption(child) shouldBe None
        e.adopt(e.direction(description, "AdoptLocalCompletion", "missing-child-adopt")).accepted shouldBe true
      }
      episode(root) { e =>
        When("ordinary Advance completes only the parent and reads the reserved child identity")
        val result = e.advance(identity)
        val repeated = e.advance(identity)
        Then("the parent is completed and the unconfirmed child stays absent with no Start retry")
        result.kind shouldBe "Terminal"
        result.completion.get.synchronization.state shouldBe "unknown"
        result.completion.get.synchronization.childSessionId shouldBe Some(child)
        repeated shouldBe result
        e.childOption(child) shouldBe None
        e.current(identity).lifecycle shouldBe Lifecycle.Completed
      }
    }
  }
}
