/**
 * @since   Nov. 17, 2026
 * @version Nov. 23, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import org.simplemodeling.textus.smworkflow.preparation.NativeDelegateTaskStartInput
import SplitPhaseExplicitPartitionFixture.take

/** Specification-owned native Start only: no implementation or check execution. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class DelegateTaskDeferredLintSpec extends AnyWordSpec with Phase69ConfiguredRepositoryFixture {
  override protected def standard_runtime: Boolean = true
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val base = super.process_plan(root, scenario, goal)
    val lint = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable,
      Vector(3), operation = SbtOperation.FixCheck)
    val compile = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable,
      Vector(1), operation = SbtOperation.Compile)
    val focused = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable,
      Vector(2), operation = SbtOperation.TestOnly, testClasses = Vector("org.example.SpecificationOwnedBehaviorSpec"))
    def array(plan: Json, key: String) = plan.hcursor.get[Vector[Json]](key).toOption.get
    val programs = array(compile, "sbtPrograms") ++ array(focused, "sbtPrograms") ++ array(lint, "sbtPrograms")
    val caps = programs.map(_.hcursor.get[Json]("capability").toOption.get)
    val aliases = Set(1, 2, 3).map(i => "sm-workflow-repository-validation-" + i)
    val workspaces = array(base, "workspaces").filterNot(w => w.hcursor.get[String]("identity").toOption.exists(aliases)) ++
      array(compile, "workspaces") ++ array(focused, "workspaces") ++ array(lint, "workspaces")
    val grants = array(base, "workspaceGrants").filterNot(w => w.hcursor.get[String]("workspaceId").toOption.exists(aliases)) ++
      array(compile, "workspaceGrants") ++ array(focused, "workspaceGrants") ++ array(lint, "workspaceGrants")
    base.mapObject(_.add("sbtPrograms", programs.asJson)
      .add("workspaces", workspaces.asJson)
      .add("workspaceGrants", grants.asJson)
      .add("grants", array(base, "grants").map { grant =>
        if (grant.hcursor.get[String]("componentId").contains(SmWorkflowComponent.componentId.name))
          grant.mapObject(_.add("capabilities", (array(grant, "capabilities") ++ caps).distinct.asJson))
        else grant
      }.asJson))
  }

  "Deferred standard delegation" should {
    "admit localized required lint from its actual host without revising explicit requester checks" in {
      val root = Files.createTempDirectory(Path.of("target").toAbsolutePath, "deferred-lint-").toRealPath()
      val request = V.DelegateTaskInput("sm-workflow", "deferred-lint", "Retain the required lint obligation",
        NonEmptyVector.of("src/main"), NonEmptyVector.of("compile", "test-only"), NonEmptyVector.of("behavior"),
        NonEmptyVector.of(V.WorkflowArtifactReference("SPECIFICATION-OWNED-direction", "1")), false, "code", "required", 10,
        "direct-instruction", Vector.empty, V.DelegateTaskContract("requester", "destination", "requester", "org.example.Fixture",
          "destination", "localized", NonEmptyVector.of("dispatch", "implement", "validate", "commit", "return", "adopt", "review"),
          Vector.empty, NonEmptyVector.of(V.WorkflowArtifactReference("SPECIFICATION-OWNED-authority", "1")), None, Vector.empty,
          "Return the unchanged request and actual destination results", None, None, None), Vector.empty, None)
      _with_runtime_(root, "UpToDate", hostParameters = BoundedWorkExecutionAdmissionFixture.parameters) { (owner, component, actor, prepare) =>
        prepare(_.setupC(_setup_commands_(root, "UpToDate").size))
        val prepared = take(NativeDelegateTaskStartInput.prepareC(Map("input" -> "selected.json", "selection-id" -> "lint-selection",
          "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "lint-key",
          "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => request.toRecord().toJsonStringC))
        prepare { call =>
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          call.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        }
        val started = read_value[V.DelegateTaskView](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "DelegateTaskService", operation = "startDelegateTask", properties = List(Property("input", request, None),
            Property("selectionId", "lint-selection", None), Property("participant", "participant", None), Property("idempotencyKey", "lint-key", None))), actor)))
        val json = io.circe.parser.parse(take(started.state.get.toJsonStringC)).toOption.get
        val normalized = GoalPhaseNativeMetadata.normalizePublicJson(json).toOption.get
        import StandaloneFixExecution.given
        val state = normalized.as[StandaloneFixExecution.State].toOption.get
        state.input shouldBe take(DelegateTask.inputC(request))
        state.selected.map(_.identity) shouldBe Vector("compile", "test-only", "fix-check")
        state.selected.map(_.ordinal) shouldBe Vector(1, 2, 3)
        state.receipts shouldBe empty
        state.candidate shouldBe None
        state.commit shouldBe None
      }
    }

    "refuse absent or ambiguous relevant lint bindings and preserve lint-off assembly" in {
      val root = "/tmp/SPECIFICATION-OWNED-repository"
      val workspace = Json.obj("identity" -> "sm-workflow-repository-validation-7".asJson, "root" -> root.asJson)
      val lint = Json.obj("target" -> "sm-workflow-repository-validation-7".asJson, "operation" -> "fix-check".asJson)
      def plan(programs: Vector[Json], workspaces: Vector[Json] = Vector(workspace)) =
        Json.obj("sbtPrograms" -> programs.asJson, "workspaces" -> workspaces.asJson)
      val ids = Vector("compile", "test-only")
      val checks = take(StandardWorkflowRuntime.boundedChecksC(ids, true, plan(Vector(lint)), root))
      checks.map(_.ordinal) shouldBe Vector(1, 2, 7)
      take(StandardWorkflowRuntime.boundedChecksC(Vector("fix-check") ++ ids, true, plan(Vector(lint)), root))
        .map(_.identity) shouldBe Vector("fix-check", "compile", "test-only")
      StandardWorkflowRuntime.boundedChecksC(ids, true, plan(Vector.empty), root).isSuccess shouldBe false
      StandardWorkflowRuntime.boundedChecksC(ids, true, plan(Vector(lint, lint)), root).isSuccess shouldBe false
      StandardWorkflowRuntime.boundedChecksC(ids, true, plan(Vector(lint), Vector(workspace, workspace)), root).isSuccess shouldBe false
      StandardWorkflowRuntime.boundedChecksC(ids, true, plan(Vector(lint)), root + "-other").isSuccess shouldBe false
      take(StandardWorkflowRuntime.boundedChecksC(ids, false, Json.obj(), root)) shouldBe take(StandardWorkflowRuntime.standardChecksC(ids))
    }
  }
}
