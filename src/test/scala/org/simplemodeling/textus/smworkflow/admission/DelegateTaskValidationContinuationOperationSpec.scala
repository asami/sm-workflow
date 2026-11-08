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
import org.simplemodeling.textus.smworkflow.preparation.{NativeDelegateTaskStartInput, NativeGoalTaskStartInput, NativeReviewPreparation}
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import BoundedWorkRuntime.given
import StandaloneFixExecution as S
import StandaloneFixExecution.given

/** Normal managed Operations on isolated Git/SQLite with real typed SBT.
  * Prior receipts and Human direction are explicitly specification-owned.
  * No actual user's Session, consent or delivery is represented by this fixture.
  * @since   Nov.  8, 2026
  *  version Nov.  8, 2026
  * @version Nov.  9, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class DelegateTaskValidationContinuationOperationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private val _suite = "org.simplemodeling.textus.smworkflow.p62authored.LineWriterSpec"
  private val _suite_path = "src/test/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriterSpec.scala"
  private val _owned = Vector("docs/phase.md", _suite_path)
  private var _zero_tests = false
  private val _checks = SelectedValidationFixture.checks
  private def _take[A](x: Consequence[A]): A = x.toOption.getOrElse(fail(s"Fixture operation refused: $x"))
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    TypedSbtHostPlanFixture.buildAuthoring(root, discoverAllTests = true)
    val path = root.resolve("repository/src/test/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriterSpec.scala")
    Files.createDirectories(path.getParent)
    Files.writeString(path, """package org.simplemodeling.textus.smworkflow.p62authored
      |import org.scalatest.wordspec.AnyWordSpec
      |__CLASSIFICATION__
      |final class LineWriterSpec extends AnyWordSpec {
      |  "selected fixture" should { "retain its ordinary assertion" in { assert(2 + 2 == 4) } }
      |}
      |""".stripMargin.replace("__CLASSIFICATION__", if (_zero_tests) "@org.scalatest.DoNotDiscover" else ""))
    Files.writeString(path.resolveSibling("OrdinarySpec.scala"), """package org.simplemodeling.textus.smworkflow.p62authored
      |import org.scalatest.wordspec.AnyWordSpec
      |import org.scalatest.matchers.should.Matchers
      |final class OrdinarySpec extends AnyWordSpec with Matchers {
      |  "ordinary full validation" should { "keep its positive assertion" in { (2 + 2) shouldBe 4 } }
      |}
      |""".stripMargin)
    Files.writeString(root.resolve("repository/build.sbt"),
      "\nTest / testOptions += Tests.Argument(\"-l\", \"org.scalatest.tags.Slow\")\n" +
        "Test / testOnly / testOptions := Seq.empty\n",
      java.nio.file.StandardOpenOption.APPEND)
    Files.writeString(root.resolve("repository/docs/phase.md"), "Specification source\n")
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] =
    super._setup_commands_(root, scenario).patch(2, Vector(Vector("-C", root.resolve("repository").toString,
      "--literal-pathspecs", "add", "--", ".gitignore", "build.sbt", "project", "src", "docs", "value.txt")), 0)
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations ++
      Vector(GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff), goalPaths = _owned)
    val compile = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1),
      operation = SbtOperation.Compile)
    val focused = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(2), Some(git),
      operation = SbtOperation.TestOnly, testClasses = Vector(_suite))
    val full = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(3), operation = SbtOperation.Test)
    def _array_(v: Json, key: String) = v.hcursor.get[Vector[Json]](key).toOption.get
    val programs = _array_(compile, "sbtPrograms") ++ _array_(focused, "sbtPrograms") ++ _array_(full, "sbtPrograms")
    val caps = (_array_(focused, "gitPrograms") ++ programs).map(_.hcursor.get[Json]("capability").toOption.get)
    focused.mapObject(_.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(_array_(compile, "workspaces") ++ _array_(focused, "workspaces") ++ _array_(full, "workspaces")))
      .add("workspaceGrants", Json.fromValues(_array_(compile, "workspaceGrants") ++ _array_(focused, "workspaceGrants") ++ _array_(full, "workspaceGrants")))
      .add("grants", Json.arr(Json.obj("componentId" -> "org.simplemodeling.textus.SmWorkflow".asJson,
        "capabilities" -> Json.fromValues(caps)))))
  }

  "The common selected-check public continuation" should {
    Vector("DelegateTask" -> false, "DelegateTask" -> true, "GoalTask" -> true).foreach { case (family, zero) =>
    s"retain the $family history and qualify a new selected attempt after ${if (zero) "ZeroTests source correction" else "an ended canceled test"}" in {
      Given("an isolated destination with specification-owned prior Compile and exited-zero unverified focused facts")
      _zero_tests = zero
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/delegate-validation").toAbsolutePath), "episode-").toRealPath()
      val initial = ThreeWorkflowNormalHostFixture.definition(root)
      val host = initial.copy(goal = initial.goal.copy(candidatePaths = _owned,
        stepValidation = initial.goal.stepValidation.copy(checks = _checks)))
      _with_runtime_(root, "Equal", Some(host), BoundedWorkExecutionAdmissionFixture.parameters) { (owner, component, actor, project) =>
        project(_.setupC(_setup_commands_(root, "Equal").size))
        def _call_(name: String, properties: List[Property]): Consequence[OperationResponse] =
          owner.executeOperationResponseInContext(Request.of(component = component.name, service = family + "Service",
            operation = name, properties = properties), actor)
        def _value_[A: org.goldenport.convert.ValueReader](name: String, properties: List[Property]): A =
          read_value[A](_take(_call_(name, properties)))
        val contract = V.DelegateTaskContract("fixture-requester", "fixture-destination", "fixture-requester", "repository",
          "destination", "localized", NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"),
          Vector("push"), NonEmptyVector.one(V.WorkflowArtifactReference("specification-human", "1")), None, Vector.empty,
          "Isolated fixture only")
        val input = V.DelegateTaskInput("sm-workflow", "selected-continuation-fixture", "Retain original behavior",
          NonEmptyVector.of("docs/phase.md", _suite_path), NonEmptyVector.of("compile", "focused", "test"), NonEmptyVector.one("behavior"),
          NonEmptyVector.one(V.WorkflowArtifactReference("specification-source", "1")), true, "code", "off", 10,
          "direct-instruction", Vector.empty, contract, Vector.empty)
        val record = if (family == "GoalTask") V.GoalTaskInput("sm-workflow", "selected-continuation-fixture", "Retain original behavior",
          NonEmptyVector.of("docs/phase.md", _suite_path), NonEmptyVector.of("compile", "focused", "test"), NonEmptyVector.one("behavior"),
          NonEmptyVector.one(V.WorkflowArtifactReference("specification-source", "1")), true, "code", "off", 10,
          "direct-instruction", Vector.empty).toRecord() else input.toRecord()
        val parameters = Map("input" -> "input", "selection-id" -> "selection",
          "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "start",
          "selection-collection" -> "entity:smworkflow.cli.goal_selection")
        val prepared = _take(if (family == "GoalTask") NativeGoalTaskStartInput.prepareC(parameters, _ => record.toJsonStringC)
          else NativeDelegateTaskStartInput.prepareC(parameters, _ => record.toJsonStringC))
        project { c =>
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          c.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        }
        val startarguments = List(Property("input", record, None), Property("selectionId", "selection", None),
          Property("participant", "participant", None), Property("idempotencyKey", "start", None))
        val session = if (family == "GoalTask") _value_[V.GoalTaskView]("startGoalTask", startarguments).sessionId
          else _value_[V.DelegateTaskView]("startDelegateTask", startarguments).sessionId
        val n: BoundedWorkNativeRun = if (family == "GoalTask") GoalTaskNativeRun else DelegateTaskNativeRun
        var initialreceipt = SelectedValidationFixture.receipt()
        if (zero) project(c => for {
          result <- WorkflowManagedProcessExecution.repositoryValidationC(c.core, 2, WorkflowSbtValidation.Kind.TestOnly)
          facts <- WorkflowSbtValidation.factsC(WorkflowSbtValidation.Kind.TestOnly, result)
          process = result.processResult
          _ = facts.reason shouldBe Some("ZeroTests")
          _ = facts.outcome shouldBe WorkflowSbtValidation.Outcome.Unverified
          _ = { initialreceipt = RepositorySyncNativeMergeValidation.Receipt("focused", WorkflowSbtCapture.termination(process),
            WorkflowSbtCapture.exitCode(process), WorkflowSbtCapture.text(process.stdout).value,
            WorkflowSbtCapture.text(process.stderr).value, Some(facts), Some(process)) }
        } yield ())
        var oldrevision = 0L
        var original: Option[BoundedWorkRuntime.Payload] = None
        def _inspect_(c: Setup, run: (WorkflowComponentStorage, org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord,
          BoundedWorkRuntime.Payload) => Consequence[Unit]): Consequence[Unit] = for {
          definition <- BoundedTaskRuntimeCatalog.loadC(family == "DelegateTask")
          h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", host.goal.candidatePaths, _checks, None, None, None, None, None)
          store <- n.storageC(h, definition, c.core)
          instances <- store.bindInstancesC()(using c.executionContext)
          current <- instances.loadC(InstanceIdentity(session))(using c.executionContext).map(_.get)
          payload <- n.loadPayloadC(store, session,
            current.revision.value - (if (current.suspension.nonEmpty) 1 else 0), c.core)
          _ <- run(store, current, payload)
        } yield ()
        project(c => _inspect_(c, (store, current, payload) => for {
          observed <- c.observeC()
          state = SelectedValidationFixture.state.copy(input = payload.state.input, selected = _checks,
            conditionReport = payload.state.input.conditions.map(id => S.Condition(id, family == "GoalTask",
              "Specification-owned semantic behavior; actual validation and review remain pending")),
            receipts = SelectedValidationFixture.state.receipts.dropRight(1) :+ initialreceipt,
            candidate = Some(S.Candidate(observed.head, observed.tree, Vector.empty, "")))
          waiting = payload.copy(state = state)
          h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", waiting.root, waiting.branch, host.goal.candidatePaths, _checks, None, None, None, None, None)
          saved <- n.saveC(h, store, current, waiting, c.core)
          _ = { oldrevision = saved.revision.value; original = Some(waiting) }
        } yield ()))
        if (zero) {
          val path = root.resolve("repository").resolve(_suite_path)
          Files.writeString(path, Files.readString(path).replace("@org.scalatest.DoNotDiscover", "@org.scalatest.tags.Slow"))
        }
        val descriptionname = BoundedSelectedValidationContinuation.describeName(n)
        val adoptname = BoundedSelectedValidationContinuation.adoptName(n)
        val description = _value_[V.BoundedValidationContinuationView](descriptionname,
          List(Property("input", V.BoundedValidationContinuationDescriptionInput(session, Vector.empty, zero), None)))
        val reference = V.WorkflowArtifactReference("specification-selection-direction", "1")
        val answer = V.BoundedValidationContinuationInput(session, description.target,
          if (zero) "AdoptCurrentOwnedCandidate" else "RetrySelectedCheck",
          "The fixture selection now contains its automatic assertion", "participant", reference)
        When("missing independent admission is refused, then actual fixture-owned host evidence admits the new attempt")
        _call_(adoptname, List(Property("input", answer, None))).isSuccess shouldBe false
        project(c => for {
          row <- NativeReviewPreparation.selectedValidationContinuationC(description, answer, reference,
            V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None), "participant", "normal-development-host",
            c.executionContext.clock.instant().minusSeconds(1))
          _ <- c.createC("host_evidence", row.recordIdentity, row.record)
        } yield ())
        _value_[V.BoundedValidationContinuationView](adoptname,
          List(Property("input", answer, None))).outcome shouldBe "resumed-awaiting-advance"
        project(c => _inspect_(c, (store, current, payload) => for {
          old <- n.loadPayloadC(store, session, oldrevision, c.core)
          _ = old shouldBe original.get
          _ = payload.state.receipts shouldBe (if (zero) original.get.state.receipts else original.get.state.receipts.take(1))
          _ = payload.state.ordinaryCount shouldBe 0
          _ = payload.state.automaticCount shouldBe 0
        } yield ()))
        if (!zero) Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        val reviewwork = if (family == "GoalTask") _value_[V.GoalTaskView]("advanceGoalTask",
          List(Property("sessionId", session, None))).work else _value_[V.DelegateTaskView]("advanceDelegateTask",
          List(Property("sessionId", session, None))).work
        Then("the same Session reaches Review only after positive fresh selected-check evidence and required remaining checks")
        reviewwork.get.getString("kind") shouldBe Some("Review")
        project(c => _inspect_(c, (store, current, payload) => for {
          old <- n.loadPayloadC(store, session, oldrevision, c.core)
          _ = old.state.receipts.last.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Unverified
          _ = if (zero) payload.state.receipts.head should not be original.get.state.receipts.head
            else payload.state.receipts.head shouldBe original.get.state.receipts.head
          _ = payload.state.receipts.size shouldBe 3
          _ = payload.state.receipts.drop(1).foreach(r => r.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded)
          _ = payload.state.receipts(1).sbt.get.selection.get.testsRun shouldBe 1L
          _ = payload.state.receipts(1).sbt.get.selection.get.completedSuites shouldBe 1
          _ = if (zero) {
            old.state.receipts.last.sbt.get.reason shouldBe Some("ZeroTests")
            payload.state.receipts.last.stdout should include ("OrdinarySpec:")
            payload.state.receipts.last.stdout should include ("Total number of tests run: 1")
            payload.state.receipts.last.stdout should include ("should keep its positive assertion")
            payload.state.receipts.last.stdout should not include "should retain its ordinary assertion"
          }
          _ = payload.state.selected shouldBe _checks
          _ = payload.state.ordinaryCount shouldBe 0
          _ = payload.state.automaticCount shouldBe 0
        } yield ()))
        Files.readString(root.resolve("sbt-executions.txt")).linesIterator.size shouldBe (if (zero) 4 else 2)
      }
    }
    }
  }
}
