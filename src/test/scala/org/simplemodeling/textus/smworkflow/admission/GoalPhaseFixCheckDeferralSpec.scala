package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.ProgressionReference
import org.goldenport.protocol.Property
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeValidation as N
import GoalPhaseValidationRecovery as R
import SplitPhaseExplicitPartitionFixture.take

final class GoalPhaseFixCheckDeferralSpec extends AnyWordSpec with GivenWhenThen
    with GoalPhaseKnownFailedPhaseValidationFixture {
  override protected def known_phase_checks: Vector[String] = Vector("compile", "test", "fix-check")

  override protected def recoveryHost(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
    val original = super.recoveryHost(root)
    val selected = Vector(N.Check("compile", 2, WorkflowSbtValidation.Kind.Compile),
      N.Check("test", 3, WorkflowSbtValidation.Kind.Test))
    original.copy(goal = original.goal.copy(phaseValidation = N.Policy("phase-without-fix-check", 1, selected)))
  }

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), goalPaths = goal.candidatePaths,
      goalMessages = goal.closure.programs.map(p => "Goal Step " + p.step), goalSelections = Vector(goal.candidatePaths))
    val compile = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable,
      Vector(1, 2), Some(git), operation = SbtOperation.Compile)
    val test = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable,
      Vector(3), operation = SbtOperation.Test)
    def _array_(value: Json, name: String): Vector[Json] = value.hcursor.get[Vector[Json]](name).toOption.get
    val programs = _array_(compile, "sbtPrograms") ++ _array_(test, "sbtPrograms")
    compile.mapObject(_.add("sbtPrograms", programs.asJson)
      .add("workspaces", (_array_(compile, "workspaces") ++ _array_(test, "workspaces")).asJson)
      .add("workspaceGrants", (_array_(compile, "workspaceGrants") ++ _array_(test, "workspaceGrants")).asJson)
      .add("grants", Json.arr(Json.obj("componentId" -> "org.simplemodeling.textus.SmWorkflow".asJson,
        "capabilities" -> (_array_(compile, "gitPrograms") ++ programs)
          .map(_.hcursor.downField("capability").focus.get).asJson))))
  }

  "Whole-Phase FixCheck deferral" should {
    "describe only an unissued candidate and refuse missing or crossed Human evidence" in {
      Given("a Phase that closes a real Step and has Compile and FULL bindings but no whole-Phase FixCheck binding")
      val root = newKnownRoot("phase-fix-check-deferral")
      Files.writeString(root.resolve("phase-ready"), "ready\n")
      inKnownEpisode(root, initialize = true, componentInstance = "default") { episode =>
        val identity = episode.start().sessionId
        episode.call(R.describeOperation, List(Property("sessionId", identity, None))).isSuccess shouldBe false
        episode.submitImplementation(episode.advance(identity)).accepted shouldBe true
        val review = episode.advance(identity)
        review.work.get.kind shouldBe "ReviewStep"
        episode.acceptStep(review)
        When("ordinary Advance reaches the unissued whole-Phase candidate")
        episode.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(identity)).isSuccess shouldBe false
        val before = episode.current(identity)
        val description = episode.describe(identity)
        val target = take(GoalPhaseFixCheckDeferral.targetC(description.target))
        Then("only FixCheck is eligible, and the exact accepted prefix is retained without running FULL")
        before.currentProgression shouldBe Some(ProgressionReference("phase.PhaseValidation"))
        before.causation.value shouldBe "ordered-candidate:candidate"
        description.choices shouldBe Vector(GoalPhaseFixCheckDeferral.choice)
        target.phaseChecks shouldBe known_phase_checks
        target.completed.map(_.step) shouldBe Vector("step-1")
        target.sourceRevision shouldBe before.revision.value.toString
        phaseExecutions(root) shouldBe empty
        val missing = V.GoalPhaseValidationRecoveryInput(identity, description.target, GoalPhaseFixCheckDeferral.choice,
          "Retain the original Plan and defer only the missing whole-Phase check", V.WorkflowArtifactReference("missing-human", "1"))
        def _rejection_(input: V.GoalPhaseValidationRecoveryInput): String =
          episode.call(R.recoveryOperation, List(Property("input", input.toRecord(), None))) match {
            case Consequence.Failure(reason) => reason.displayMessage
            case Consequence.Success(_) => fail("The protected deferral unexpectedly succeeded")
          }
        _rejection_(missing) should include ("no independently issued host evidence")
        val admitted = episode.proof(description, GoalPhaseFixCheckDeferral.choice, missing.rationale,
          V.WorkflowArtifactReference("fixture-fix-check-deferral", "1"))
        val crossed = admitted.copy(target = GoalPhaseFixCheckDeferral.targetRecord(target.copy(sessionId = "other-session")))
        _rejection_(crossed) should include ("crosses its exact current target")
        episode.current(identity).history shouldBe before.history
        Files.writeString(root.resolve("fix-check-deferral-description.json"), take(description.toRecord().toJsonStringC))
        Files.writeString(root.resolve("fix-check-deferral-input.json"), take(admitted.toRecord().toJsonStringC))
      }
    }
  }
}
