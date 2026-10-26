/**
 * @since   Oct. 26, 2026
 * @version Oct. 26, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeGoalTaskStartInput
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import StandaloneFixExecution as S
import StandaloneFixExecution.given
import WorkflowSbtValidation as T
import RepositorySyncNativeMergeValidation as B

/** Bounded business admission plus public Start/Advance in an isolated native
  * repository. Check/commit facts below are explicitly fixture-owned; no installed
  * behavioral success, independent review or Task delivery is claimed by them.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalTaskWorkflowSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail("Task fixture admission failed: " + other)
  }
  private val checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, T.Kind.Compile),
    GoalPhaseNativeValidation.Check("lint", 2, T.Kind.FixCheck), GoalPhaseNativeValidation.Check("focused", 3, T.Kind.TestOnly))
  private def input(source: String = "journal") = S.Input("sm-workflow", "selected-task", "Implement adopted behavior",
    Vector("src"), checks.map(_.identity), Vector("behavior"), Vector(ContextReference("journal-source", "adopted-1")),
    false, task = Some(S.Task(source, Vector("existing-component"))))
  private val candidate = S.Candidate("a" * 40, "b" * 40, Vector("src/main/scala/Behavior.scala"), "fixture-owned Task delta")
  private val satisfied = Vector(S.Condition("behavior", true, "Adopted behavior covered by the assigned implementation"))
  private def completed = S.Result("completed", "Implemented the adopted objective", Vector.empty, satisfied, Vector.empty)
  private def implementation = {
    val state = take(S.startC(input(), "participant", checks))
    take(S.completedC(S.Work("task-fixture", "2", "Implement", state), completed, ContextReference("implementation", "1")))
  }
  private def receipt(check: GoalPhaseNativeValidation.Check, success: Boolean = true) = B.Receipt(check.identity,
    "exited", Some(if (success) 0 else 1), if (success) "" else "[error] (Compile / compileIncremental) Compilation failed", "",
    Some(T.Facts(check.kind, if (success) T.Outcome.Succeeded else T.Outcome.Failed,
      if (success && check.kind == T.Kind.TestOnly) Some(T.Selection(Vector("fixture.BehaviorSpec"), 1, 2)) else None, None)))
  private def observe(state: S.State, check: GoalPhaseNativeValidation.Check) = take(S.observedC(take(S.intentC(state)), receipt(check)))
  private def validated(c: S.Candidate = candidate) = checks.foldLeft(take(S.candidateC(implementation, c)))(observe)
  private def reviewed(state: S.State) = take(S.completedC(S.Work("task-fixture", "15", "Review", state),
    completed.copy(outcome = "accept", summary = "Integrated Task objective and conditions accepted"), ContextReference("independent-review", "1")))

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations)

  "Ad hoc Task workflow" should {
    "adopt journal and direct instruction requirements without Phase input and require initial implementation" in {
      Vector("journal", "direct-instruction").foreach { source =>
        val adopted = take(S.startC(input(source), "participant", checks))
        adopted.stage shouldBe "Implement"
        adopted.input.objective shouldBe "Implement adopted behavior"
        adopted.input.task.get.sourceKind shouldBe source
        adopted.input.conditions shouldBe Vector("behavior")
        adopted.automaticCount shouldBe 0
        S.candidateC(adopted, candidate).isSuccess shouldBe false
      }
      S.inputC(input().copy(task = Some(S.Task("superseded-journal-suggestion", Vector.empty)))).isSuccess shouldBe false
    }

    "use public passive preparation and Start and redisplay the actual initial Task WorkOrder without a Plan" in {
      Given("an isolated ordinary repository, an explicitly adopted direct instruction and passive selection import")
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/goal-task").toAbsolutePath), "episode-").toRealPath()
      val publicInput = V.GoalTaskInput("sm-workflow", "public-ad-hoc-task", "Develop the explicitly adopted direct request",
        NonEmptyVector.of("docs/phase.md"), NonEmptyVector.of("test"), NonEmptyVector.of("objective"),
        NonEmptyVector.of(V.WorkflowArtifactReference("direct-instruction", "1")), false, "documentation", "off", 10,
        "direct-instruction", Vector.empty)
      val prepared = take(NativeGoalTaskStartInput.prepareC(Map("input" -> "adopted.json", "selection-id" -> "task-selection",
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "public-task-start",
        "selection-collection" -> "entity:smworkflow.cli.goal_selection"), name =>
        if (name == "adopted.json") publicInput.toRecord().toJsonStringC
        else Consequence.stateConflict("Task preparation must read only its selected adopted input")))
      _with_runtime_(root, "TaskStart") { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(root, "TaskStart").size).flatMap { _ =>
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record]
            .getVector("records").get.head.asInstanceOf[Record]
          call.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        })
        def invoke(operation: String, properties: List[Property]): V.GoalTaskView = read_value[V.GoalTaskView](take(
          owner.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalTaskService",
            operation = operation, properties = properties), actor)))
        When("the normal public Task operations own Start and initial implementation dispatch")
        val started = invoke("startGoalTask", List(Property("input", publicInput, None), Property("selectionId", "task-selection", None),
          Property("participant", "participant", None), Property("idempotencyKey", "public-task-start", None)))
        val issued = invoke("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
        val redisplayed = invoke("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
        Then("the retained Task WorkOrder has its own definition and completion route, with no Phase document acquisition")
        started.kind shouldBe "Started"
        issued.kind shouldBe "Request"
        issued.submitOperation shouldBe Some("submitGoalTaskWorkResult")
        issued.resultType shouldBe Some("GoalTaskWorkResult")
        issued.issuedWorkContext.get.requiredOperation shouldBe "goal-task-implementation-capability"
        redisplayed.issuedWorkContext shouldBe issued.issuedWorkContext
        redisplayed.work shouldBe issued.work
        Files.exists(root.resolve("repository/docs/phase.md")) shouldBe false
      }
    }

    "require integrated Task review after the same nonempty focused checks even with no inner review requirement" in {
      val state = validated()
      state.stage shouldBe "Review"
      state.reviewReference shouldBe None
      state.automaticCount shouldBe 0
      val accepted = reviewed(state)
      accepted.stage shouldBe "CommitReady"
      accepted.commit shouldBe None
      accepted.receipts shouldBe state.receipts
      S.committedC(accepted, S.Commit("c" * 40, candidate.head, candidate.tree)).isSuccess shouldBe false
    }

    "retain compiler non-execution and UNKNOWN rather than fabricate successful Task checks" in {
      val selected = take(S.candidateC(implementation, candidate))
      val failed = take(S.observedC(take(S.intentC(selected)), receipt(checks.head, false)))
      failed.stage shouldBe "Repair"
      failed.receipts.size shouldBe 1
      S.diagnostics(failed, "selected-revision").head.testExecution.status shouldBe "not-executed-because-compilation-failed"
      val uncertain = take(S.observedC(take(S.intentC(selected)), receipt(checks.head).copy(sbt =
        Some(T.Facts(T.Kind.Compile, T.Outcome.NotExecuted, None, None)))))
      uncertain.stage shouldBe "Wait"
      S.intentC(uncertain).isSuccess shouldBe false
    }

    "repair integrated findings in the same unit and reuse unchanged validation and prior review context" in {
      val state = validated()
      val finding = completed.copy(outcome = "repair", findings = Vector("selected-boundary"),
        conditions = Vector(S.Condition("behavior", false, "A boundary needs repair")))
      val repair = take(S.completedC(S.Work("task-fixture", "15", "Review", state), finding, ContextReference("integrated-review", "1")))
      repair.stage shouldBe "Repair"
      repair.priorReview shouldBe Some(finding)
      val fixed = take(S.completedC(S.Work("task-fixture", "18", "Repair", repair), completed, ContextReference("affected-fix", "1")))
      fixed.automaticCount shouldBe 1
      fixed.ordinaryCount shouldBe 1
      val reused = take(S.candidateC(fixed, candidate))
      reused.stage shouldBe "Review"
      reused.receipts shouldBe state.receipts
      reused.priorReviewCandidate shouldBe Some(candidate)
      S.intentC(reused).isSuccess shouldBe false
      reviewed(reused).stage shouldBe "CommitReady"
    }

    "finish only with the matching actual commit facts and retain incomplete delivery without replay allowance" in {
      val ready = reviewed(validated())
      val intent = ready.copy(stage = "CommitIntent")
      val commit = S.Commit("c" * 40, candidate.head, candidate.tree)
      val done = take(S.committedC(intent, commit))
      done.stage shouldBe "Completed"
      done.commit shouldBe Some(commit)
      done.receipts shouldBe ready.receipts
      done.reviewReference shouldBe ready.reviewReference
      S.committedC(intent, commit.copy(parent = "d" * 40)).isSuccess shouldBe false
      S.committedC(intent, commit.copy(tree = "d" * 40)).isSuccess shouldBe false
      val unknown = intent.copy(stage = "CommitUnknown")
      take(S.committedC(unknown, commit)).stage shouldBe "Completed"
      val failed = intent.copy(stage = "CommitFailed")
      S.committedC(failed, commit).isSuccess shouldBe false
      S.intentC(unknown).isSuccess shouldBe false
      unknown.asJson.as[S.State].toOption shouldBe Some(unknown)
    }

    "report reviewed no-change without requesting an empty commit and keep standalone closure distinct" in {
      val unchanged = reviewed(validated(candidate.copy(paths = Vector.empty, diff = "")))
      unchanged.stage shouldBe "NoChange"
      unchanged.commit shouldBe None
      val standalone = take(S.startC(input().copy(task = None), "participant", checks))
      standalone.stage shouldBe "Inspect"
      val passed = checks.foldLeft(take(S.candidateC(standalone, candidate)))(observe)
      passed.stage shouldBe "NoChange"
      passed.reviewReference shouldBe None
      passed.commit shouldBe None
    }

    "derive its independent public definition without changing existing Phase or standalone identities" in {
      val definition = take(GoalTaskRuntimeCatalog.loadC())
      definition.workflowIdentity.value shouldBe "GoalTaskWorkflow"
      definition.workflowRevision.value shouldBe "goal-task-business-operation-v1"
      val compiled = domain.statemachine.workflow.GoalTaskWorkflowStateMachineWorkflow5.workflow
      compiled.requiredOperations.map(_.identity.capability).toSet shouldBe Set("goal-task-implementation-capability", "goal-task-repair-capability", "goal-task-review-capability")
      compiled.actions.find(_.identity == "ReviewGoalTask").map(_.kind) shouldBe Some("JUDGMENT")
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should contain("GoalTaskService.startGoalTask")
      take(StandaloneFixRuntimeCatalog.loadC()).workflowIdentity.value shouldBe "StandaloneFixWorkflow"
      take(GoalPhaseRuntimeCatalog.loadC()).definition.workflowIdentity.value shouldBe "GoalPhaseWorkflow"
    }
  }
}
