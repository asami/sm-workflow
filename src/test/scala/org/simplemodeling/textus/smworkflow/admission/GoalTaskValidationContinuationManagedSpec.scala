package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{GitOperationKind, SbtOperation}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.{NativeGoalTaskStartInput, NativeReviewPreparation}
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import BoundedWorkRuntime.given
import StandaloneFixExecution as S
import GoalTaskValidationContinuation as C

/** Specification-owned receipt facts exercise native public admission only;
  * they do not represent installed execution or a consumer's consent. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class GoalTaskValidationContinuationManagedSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  private def take[A](x: Consequence[A]): A = x.toOption.getOrElse(fail(s"Operation refused: $x"))
  private val suite = "org.simplemodeling.textus.smworkflow.p62authored.LineWriterSpec"
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations ++ Vector(
        GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff),
      goalPaths = Vector("docs/phase.md"))
    val compile = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(3),
      Some(git), operation = SbtOperation.Compile)
    val focused = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(4),
      operation = SbtOperation.TestOnly, testClasses = Vector(suite))
    def array(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
    val programs = array(compile, "sbtPrograms") ++ array(focused, "sbtPrograms")
    val capabilities = (array(compile, "gitPrograms") ++ programs).map(_.hcursor.get[Json]("capability").toOption.get)
    compile.mapObject(_.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(array(compile, "workspaces") ++ array(focused, "workspaces")))
      .add("workspaceGrants", Json.fromValues(array(compile, "workspaceGrants") ++ array(focused, "workspaceGrants")))
      .add("grants", Json.arr(Json.obj("componentId" -> "org.simplemodeling.textus.SmWorkflow".asJson,
        "capabilities" -> Json.fromValues(capabilities.distinct)))))
  }
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    TypedSbtHostPlanFixture.buildAuthoring(root, discoverAllTests = true)
    val test = root.resolve("repository/src/test/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriterSpec.scala")
    Files.createDirectories(test.getParent)
    Files.writeString(test, """package org.simplemodeling.textus.smworkflow.p62authored
      |import org.scalatest.wordspec.AnyWordSpec
      |final class LineWriterSpec extends AnyWordSpec {
      |  "LineWriter" should { "append LF" in { assert(LineWriter.render("fixture") == "fixture\n") } }
      |}
      |""".stripMargin)
    val path = root.resolve("repository/docs/phase.md")
    Files.createDirectories(path.getParent)
    Files.writeString(path, "Specification source\n")
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] =
    super._setup_commands_(root, scenario).patch(2, Vector(Vector("-C", root.resolve("repository").toString,
      "--literal-pathspecs", "add", "--", ".gitignore", "build.sbt", "project", "src", "docs")), 0)

  "Native GoalTask validation continuation" should {
    "append a direction without an SBT effect and retain the original checkpoint" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/task-validation-managed").toAbsolutePath), "episode-").toRealPath()
      Given("a test-owned legacy GoalTask and test-owned successful receipt facts")
      _with_runtime_(root, "TaskValidation", hostParameters = BoundedWorkExecutionAdmissionFixture.parameters) {
        (owner, component, actor, project) =>
        def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "GoalTaskService", operation = name, properties = properties), actor)
        def value[A: org.goldenport.convert.ValueReader](name: String, properties: List[Property]): A = read_value[A](take(call(name, properties)))
        val input = V.GoalTaskInput("sm-workflow", "fixture-validation", "Qualify specification source",
          NonEmptyVector.one("docs/phase.md"), NonEmptyVector.one("test"), NonEmptyVector.one("objective"),
          NonEmptyVector.one(V.WorkflowArtifactReference("specification-source", "1")), true,
          "code", "required", 10, "direct-instruction", Vector.empty)
        val prepared = take(NativeGoalTaskStartInput.prepareC(Map("input" -> "input", "selection-id" -> "selection",
          "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "start",
          "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
        project(c => c.setupC(_setup_commands_(root, "TaskValidation").size).flatMap { _ =>
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          c.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        })
        val started = value[V.GoalTaskView]("startGoalTask", List(Property("input", input, None),
          Property("selectionId", "selection", None), Property("participant", "participant", None), Property("idempotencyKey", "start", None)))
        project(c => for {
          storage <- c.taskStorageC()
          instances <- storage.bindInstancesC()(using c.executionContext)
          current <- instances.loadC(InstanceIdentity(started.sessionId))(using c.executionContext)
          payload <- GoalTaskNativeRun.loadPayloadC(storage, started.sessionId, current.get.revision.value, c.core)
          observed <- RepositorySyncManagedGitObservation.observeC(c.core)
          tree <- RepositorySyncManagedGitObservation.indexTreeC(c.core)
          checks = Vector(GoalPhaseNativeValidation.Check("fix-check", 1, WorkflowSbtValidation.Kind.FixCheck),
            GoalPhaseNativeValidation.Check("test", 2, WorkflowSbtValidation.Kind.Test))
          receipts = checks.map(check => RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(0), "", "",
            Some(WorkflowSbtValidation.Facts(check.kind, WorkflowSbtValidation.Outcome.Succeeded, None, None))))
          candidate = S.Candidate(observed.head, tree, Vector.empty, "")
          legacy = payload.copy(state = payload.state.copy(input = payload.state.input.copy(checks = Vector("fix-check", "test")),
            stage = "Assess", selected = checks, receipts = receipts,
            candidate = Some(candidate), semanticReference = Some(ContextReference("specification-result", "1")),
            conditionReport = Vector(S.Condition("objective", true, "Specification-owned work completed"))))
          host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", payload.root, payload.branch, payload.state.input.ownedPaths, checks, None, None, None, None, None)
          _ <- GoalTaskNativeRun.saveC(host, storage, current.get, legacy, c.core)
        } yield ())
        val described = value[V.GoalTaskValidationContinuationDescriptionResult]("describeGoalTaskValidationContinuation",
          List(Property("sessionId", started.sessionId, None)))
        val target = take(C.targetC(described.target))
        target.supplemental.map(_.ordinal) shouldBe Vector(3, 4)
        val reference = V.WorkflowArtifactReference("specification-human", "1")
        val answer = V.GoalTaskValidationContinuationInput(started.sessionId, described.target, "AuthorizeMissingChecks",
          "Specification direction", "participant", reference)
        project(c => for {
          prepared <- NativeReviewPreparation.taskValidationContinuationC(described, answer, reference,
            V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None), "participant", "normal-development-host",
            c.executionContext.clock.instant().minusSeconds(1))
          _ <- c.createC("host_evidence", prepared.recordIdentity, prepared.record)
        } yield ())
        val deferReference = V.WorkflowArtifactReference("specification-defer", "1")
        val deferred = answer.copy(choice = "Defer", reference = deferReference)
        project(c => for {
          prepared <- NativeReviewPreparation.taskValidationContinuationC(described, deferred, deferReference,
            V.WorkflowExecutionEvidence(Vector(deferReference), Some("participant"), None, None), "participant", "normal-development-host",
            c.executionContext.clock.instant().minusSeconds(1))
          _ <- c.createC("host_evidence", prepared.recordIdentity, prepared.record)
        } yield ())
        value[V.GoalTaskValidationContinuationDescriptionResult]("adoptGoalTaskValidationContinuation", List(Property("input", deferred, None))).outcome shouldBe "deferred"
        value[V.GoalTaskValidationContinuationDescriptionResult]("describeGoalTaskValidationContinuation",
          List(Property("sessionId", started.sessionId, None))).target shouldBe described.target
        When("the exact independent direction is adopted")
        val adopted = value[V.GoalTaskValidationContinuationDescriptionResult]("adoptGoalTaskValidationContinuation", List(Property("input", answer, None)))
        Then("the native checkpoint retains the successful prefix and does not run a check")
        adopted.outcome shouldBe "resumed-awaiting-advance"
        value[V.GoalTaskValidationContinuationDescriptionResult]("adoptGoalTaskValidationContinuation", List(Property("input", answer, None))).outcome shouldBe "already-resumed"
        project(c => for {
          storage <- c.taskStorageC()
          instances <- storage.bindInstancesC()(using c.executionContext)
          current <- instances.loadC(InstanceIdentity(started.sessionId))(using c.executionContext)
          payload <- GoalTaskNativeRun.loadPayloadC(storage, started.sessionId, current.get.revision.value, c.core)
          _ = payload.state.receipts shouldBe target.business.state.receipts
          _ = payload.state.selected shouldBe target.business.state.selected ++ target.supplemental
          _ = payload.state.input shouldBe target.business.state.input
        } yield ())
        When("ordinary public Advance executes the two supplemental checks")
        value[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
        Then("strict assessment reaches independent Review with the original prefix unchanged")
        project(c => for {
          storage <- c.taskStorageC()
          instances <- storage.bindInstancesC()(using c.executionContext)
          current <- instances.loadC(InstanceIdentity(started.sessionId))(using c.executionContext)
          // Review is suspended on issued work; its source checkpoint precedes that suspension.
          sourceRevision = current.get.revision.value - (if (current.get.suspension.nonEmpty) 1 else 0)
          payload <- GoalTaskNativeRun.loadPayloadC(storage, started.sessionId, sourceRevision, c.core)
          state = payload.state
          original = target.business
          _ = state.stage shouldBe "Review"
          _ = state.input shouldBe original.state.input
          _ = state.candidate shouldBe original.state.candidate
          _ = state.receipts.take(2) shouldBe original.state.receipts
          _ = state.selected shouldBe original.state.selected ++ target.supplemental
          _ = state.receipts.drop(2).map(_.sbt.get.kind) shouldBe Vector(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Kind.TestOnly)
          _ = state.receipts.drop(2).foreach(_.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded)
          _ = state.receipts.last.sbt.get.selection.get.testClasses shouldBe Vector(suite)
          _ = state.receipts.last.sbt.get.selection.get.valid shouldBe true
          _ = state.receipts.drop(2).foreach(_.processObservation.nonEmpty shouldBe true)
          _ = state.automaticCount shouldBe original.state.automaticCount
          _ = state.ordinaryCount shouldBe original.state.ordinaryCount
          _ = state.generation shouldBe original.state.generation
          _ = state.semanticReference shouldBe original.state.semanticReference
          _ = state.input.sourceReferences shouldBe original.state.input.sourceReferences
          _ = payload.implementationWorker shouldBe original.implementationWorker
          _ = payload.implementationWorkers shouldBe original.implementationWorkers
        } yield ())
        val executions = Files.readString(root.resolve("sbt-executions.txt")).linesIterator.toVector
        executions.size shouldBe 2
        value[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
        Files.readString(root.resolve("sbt-executions.txt")).linesIterator.toVector shouldBe executions
      }
    }
  }
}
