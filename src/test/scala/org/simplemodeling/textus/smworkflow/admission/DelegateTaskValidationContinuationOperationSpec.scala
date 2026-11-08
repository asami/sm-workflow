package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{GitOperationKind, SbtOperation}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.{NativeDelegateTaskStartInput, NativeReviewPreparation}
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import BoundedWorkRuntime.given
import StandaloneFixExecution as S
import StandaloneFixExecution.given

/** Normal managed Operations on isolated Git/SQLite with real typed SBT.
  * Prior receipts and Human direction are explicitly specification-owned.
  * No actual user's Session, consent or delivery is represented by this fixture.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class DelegateTaskValidationContinuationOperationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private val _suite = "org.simplemodeling.textus.smworkflow.p62authored.LineWriterSpec"
  private val _checks = SelectedValidationFixture.checks
  private def _take[A](x: Consequence[A]): A = x.toOption.getOrElse(fail(s"Fixture operation refused: $x"))
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    TypedSbtHostPlanFixture.buildAuthoring(root, discoverAllTests = true)
    val path = root.resolve("repository/src/test/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriterSpec.scala")
    Files.createDirectories(path.getParent)
    Files.writeString(path, """package org.simplemodeling.textus.smworkflow.p62authored
      |import org.scalatest.wordspec.AnyWordSpec
      |final class LineWriterSpec extends AnyWordSpec {
      |  "selected fixture" should { "retain its ordinary assertion" in { assert(2 + 2 == 4) } }
      |}
      |""".stripMargin)
    Files.writeString(root.resolve("repository/docs/phase.md"), "Specification source\n")
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] =
    super._setup_commands_(root, scenario).patch(2, Vector(Vector("-C", root.resolve("repository").toString,
      "--literal-pathspecs", "add", "--", ".gitignore", "build.sbt", "project", "src", "docs", "value.txt")), 0)
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations ++
      Vector(GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff))
    val focused = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(2), Some(git),
      operation = SbtOperation.TestOnly, testClasses = Vector(_suite))
    val full = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(3), operation = SbtOperation.Test)
    def _array_(v: Json, key: String) = v.hcursor.get[Vector[Json]](key).toOption.get
    val programs = _array_(focused, "sbtPrograms") ++ _array_(full, "sbtPrograms")
    val caps = (_array_(focused, "gitPrograms") ++ programs).map(_.hcursor.get[Json]("capability").toOption.get)
    focused.mapObject(_.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(_array_(focused, "workspaces") ++ _array_(full, "workspaces")))
      .add("workspaceGrants", Json.fromValues(_array_(focused, "workspaceGrants") ++ _array_(full, "workspaceGrants")))
      .add("grants", Json.arr(Json.obj("componentId" -> "org.simplemodeling.textus.SmWorkflow".asJson,
        "capabilities" -> Json.fromValues(caps)))))
  }

  "The common selected-check public continuation" should {
    "retain the old destination and compile while running only the new focused attempt and unstarted full check" in {
      Given("an isolated destination with specification-owned prior Compile and exited-zero unverified focused facts")
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/delegate-validation").toAbsolutePath), "episode-").toRealPath()
      val initial = ThreeWorkflowNormalHostFixture.definition(root)
      val host = initial.copy(goal = initial.goal.copy(stepValidation = initial.goal.stepValidation.copy(checks = _checks)))
      _with_runtime_(root, "Equal", Some(host), BoundedWorkExecutionAdmissionFixture.parameters) { (owner, component, actor, project) =>
        project(_.setupC(_setup_commands_(root, "Equal").size))
        def _call_(name: String, properties: List[Property]): Consequence[OperationResponse] =
          owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskService",
            operation = name, properties = properties), actor)
        def _value_[A: org.goldenport.convert.ValueReader](name: String, properties: List[Property]): A =
          read_value[A](_take(_call_(name, properties)))
        val contract = V.DelegateTaskContract("fixture-requester", "fixture-destination", "fixture-requester", "repository",
          "destination", "localized", NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"),
          Vector("push"), NonEmptyVector.one(V.WorkflowArtifactReference("specification-human", "1")), None, Vector.empty,
          "Isolated fixture only")
        val input = V.DelegateTaskInput("sm-workflow", "selected-continuation-fixture", "Retain original behavior",
          NonEmptyVector.one("docs/phase.md"), NonEmptyVector.of("compile", "focused", "test"), NonEmptyVector.one("behavior"),
          NonEmptyVector.one(V.WorkflowArtifactReference("specification-source", "1")), true, "code", "off", 10,
          "direct-instruction", Vector.empty, contract, Vector.empty)
        val prepared = _take(NativeDelegateTaskStartInput.prepareC(Map("input" -> "input", "selection-id" -> "selection",
          "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "start",
          "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
        project { c =>
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          c.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        }
        val started = _value_[V.DelegateTaskView]("startDelegateTask", List(Property("input", input, None),
          Property("selectionId", "selection", None), Property("participant", "participant", None), Property("idempotencyKey", "start", None)))
        var oldrevision = 0L
        var original: Option[BoundedWorkRuntime.Payload] = None
        def _inspect_(c: Setup, run: (WorkflowComponentStorage, org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord,
          BoundedWorkRuntime.Payload) => Consequence[Unit]): Consequence[Unit] = for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", host.goal.candidatePaths, _checks, None, None, None, None, None)
          store <- DelegateTaskNativeRun.storageC(h, definition, c.core)
          instances <- store.bindInstancesC()(using c.executionContext)
          current <- instances.loadC(InstanceIdentity(started.sessionId))(using c.executionContext).map(_.get)
          payload <- DelegateTaskNativeRun.loadPayloadC(store, started.sessionId,
            current.revision.value - (if (current.suspension.nonEmpty) 1 else 0), c.core)
          _ <- run(store, current, payload)
        } yield ()
        project(c => _inspect_(c, (store, current, payload) => for {
          observed <- c.observeC()
          state = SelectedValidationFixture.state.copy(input = payload.state.input, selected = _checks,
            conditionReport = payload.state.input.conditions.map(id => S.Condition(id, false,
              "Selected validation, review and delivery obligations remain pending")),
            candidate = Some(S.Candidate(observed.head, observed.tree, Vector.empty, "")))
          waiting = payload.copy(state = state)
          h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", waiting.root, waiting.branch, host.goal.candidatePaths, _checks, None, None, None, None, None)
          saved <- DelegateTaskNativeRun.saveC(h, store, current, waiting, c.core)
          _ = { oldrevision = saved.revision.value; original = Some(waiting) }
        } yield ()))
        val description = _value_[V.BoundedValidationContinuationView]("describeDelegateTaskValidationContinuation",
          List(Property("input", V.BoundedValidationContinuationDescriptionInput(started.sessionId, Vector.empty, false), None)))
        val reference = V.WorkflowArtifactReference("specification-selection-direction", "1")
        val answer = V.BoundedValidationContinuationInput(started.sessionId, description.target, "RetrySelectedCheck",
          "The fixture selection now contains its automatic assertion", "participant", reference)
        When("missing independent admission is refused, then actual fixture-owned host evidence admits the new attempt")
        _call_("adoptDelegateTaskValidationContinuation", List(Property("input", answer, None))).isSuccess shouldBe false
        project(c => for {
          row <- NativeReviewPreparation.selectedValidationContinuationC(description, answer, reference,
            V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None), "participant", "normal-development-host",
            c.executionContext.clock.instant().minusSeconds(1))
          _ <- c.createC("host_evidence", row.recordIdentity, row.record)
        } yield ())
        _value_[V.BoundedValidationContinuationView]("adoptDelegateTaskValidationContinuation",
          List(Property("input", answer, None))).outcome shouldBe "resumed-awaiting-advance"
        project(c => _inspect_(c, (store, current, payload) => for {
          old <- DelegateTaskNativeRun.loadPayloadC(store, started.sessionId, oldrevision, c.core)
          _ = old shouldBe original.get
          _ = payload.state.receipts shouldBe original.get.state.receipts.take(1)
          _ = payload.state.ordinaryCount shouldBe 0
          _ = payload.state.automaticCount shouldBe 0
        } yield ()))
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        val reviewed = _value_[V.DelegateTaskView]("advanceDelegateTask", List(Property("sessionId", started.sessionId, None)))
        Then("the same Session reaches Review with two real selected checks and the successful old prefix intact")
        reviewed.sessionId shouldBe started.sessionId
        reviewed.work.get.getString("kind") shouldBe Some("Review")
        project(c => _inspect_(c, (store, current, payload) => for {
          old <- DelegateTaskNativeRun.loadPayloadC(store, started.sessionId, oldrevision, c.core)
          _ = old.state.receipts.last.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Unverified
          _ = payload.state.receipts.head shouldBe original.get.state.receipts.head
          _ = payload.state.receipts.size shouldBe 3
          _ = payload.state.receipts.drop(1).foreach(r => r.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded)
          _ = payload.state.selected shouldBe _checks
          _ = payload.state.ordinaryCount shouldBe 0
          _ = payload.state.automaticCount shouldBe 0
        } yield ()))
        Files.readString(root.resolve("sbt-executions.txt")).linesIterator.size shouldBe 2
      }
    }
  }
}
