package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.generic.auto.*
import org.goldenport.Consequence
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhaseDependencyValidation as D
import GoalPhaseDependencyValidation.given
import GoalPhaseDependencyScope as Scope

/** Explicit small same-Session fixture; installed old-to-new qualification is separate. */
@org.scalatest.tags.Slow
@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalValidationSubjectOrdinalsCliSpec extends AnyWordSpec with Matchers with ThreeWorkflowManagedRepositoryFixture {
  import NativeCallerExecutionFixture.json

  /** Only fresh specification-owned repository inputs and public host declarations. */
  private def sixCheckScenario(): F.Scenario = {
    import WorkflowSbtValidation.Kind
    val initial = F.freshDependency("subject-ordinals", bindingTransform = _.copy(step = "step-2"))
    val primary = Vector(GoalPhaseNativeValidation.Check("primary-compile", 1, Kind.Compile),
      GoalPhaseNativeValidation.Check("primary-lint", 2, Kind.FixCheck),
      GoalPhaseNativeValidation.Check("primary-test", 4, Kind.TestOnly))
    val dependency = Vector(GoalPhaseNativeValidation.Check("dependency-compile", 1, Kind.Compile),
      GoalPhaseNativeValidation.Check("dependency-lint", 2, Kind.FixCheck),
      GoalPhaseNativeValidation.Check("dependency-test", 3, Kind.TestOnly))
    val primaryRoot = Path.of(initial.host.target.root)
    val dependencyRoot = Path.of(initial.host.goal.dependencyValidation.get.root)
    val build = """scalaVersion := "2.12.20"
      |libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
      |lazy val fixCheck = taskKey[Unit]("Fixture-owned input check")
      |fixCheck := { assert(IO.read(baseDirectory.value / "value.txt") == "expected\n") }
      |Global / onLoad := { val before = (Global / onLoad).value; s => {
      |  val root = Project.extract(s).get(baseDirectory)
      |  IO.append(root.getParentFile / "sbt-starts.txt", java.lang.ProcessHandle.current().pid().toString + "\n")
      |  before(s)
      |}}
      |""".stripMargin
    val test = """package fixture
      |import org.scalatest.funsuite.AnyFunSuite
      |final class SubjectCheckSpec extends AnyFunSuite {
      |  test("fixture-owned expected newline") { assert(scala.io.Source.fromFile("value.txt").mkString == "expected\n") }
      |}
      |""".stripMargin
    Vector(primaryRoot, dependencyRoot).foreach { root =>
      Files.writeString(root.resolve("build.sbt"), build)
      Files.writeString(root.resolve("src/test/scala/SubjectCheckSpec.scala"), test)
      SplitPhaseExplicitPartitionFixture.take(_run_setup_command_(Vector("-C", root.toString, "add", "--",
        "build.sbt", "src/test/scala/SubjectCheckSpec.scala")))
      SplitPhaseExplicitPartitionFixture.take(_run_setup_command_(Vector("-C", root.toString,
        "-c", "user.name=Specification Fixture", "-c", "user.email=fixture@example.invalid", "commit",
        "--no-verify", "--no-gpg-sign", "-m", "Prepare six-check fixture")))
    }
    SplitPhaseExplicitPartitionFixture.take(_run_setup_command_(Vector("-C", primaryRoot.toString,
      "push", "--no-verify", "origin", "HEAD:refs/heads/main")))
    def observe(root: Path, argument: String): String = {
      val process = new ProcessBuilder("/usr/bin/git", "-C", root.toString, "rev-parse", argument).redirectErrorStream(true).start()
      val value = new String(process.getInputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim
      process.waitFor() shouldBe 0
      value
    }
    val base = GoalPhaseOrderedProgression.Snapshot(observe(dependencyRoot, "HEAD"), observe(dependencyRoot, "HEAD^{tree}"))
    val binding = initial.host.goal.dependencyValidation.get.copy(base = base, checks = dependency)
    val host = initial.host.copy(goal = initial.host.goal.copy(
      stepValidation = initial.host.goal.stepValidation.copy(checks = primary), dependencyValidation = Some(binding)))
    val plan = initial.plan.copy(steps = initial.plan.steps.map(_.copy(checks = primary.map(_.identity))))
    Files.writeString(initial.root.resolve("goal-plan.json"), plan.asJson.noSpaces)
    val prepared = SplitPhaseExplicitPartitionFixture.take(
      org.simplemodeling.textus.smworkflow.preparation.NativeGoalStartInput.prepareC(Map(
        "input" -> initial.root.resolve("goal-input.json").toUri.toASCIIString,
        "plan" -> initial.root.resolve("goal-plan.json").toUri.toASCIIString,
        "selection-id" -> "native-goal-selection", "participant" -> "participant", "origin" -> "ExplicitHuman",
        "idempotency-key" -> "native-specification-input", "selection-collection" -> "entity:smworkflow.cli.goal_selection",
        "plan-collection" -> "entity:smworkflow.cli.goal_plans"), reference =>
        Consequence(Files.readString(Path.of(java.net.URI.create(reference))))))
    Files.writeString(initial.root.resolve("selected-input.json"), SplitPhaseExplicitPartitionFixture.take(prepared.toJsonStringC))
    val configPath = initial.root.resolve(".textus/config.json")
    val config = parse(Files.readString(configPath)).toOption.get
    val process = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    def array(value: Json, name: String): Vector[Json] = value.hcursor.get[Vector[Json]](name).toOption.get
    val template = array(process, "sbtPrograms").head
    val component = "org.simplemodeling.textus.SmWorkflow"
    val definitions = Vector(("sm-workflow-repository-validation-1", "compile", "fixture-primary-compile"),
      ("sm-workflow-repository-validation-2", "fix-check", "fixture-primary-fix-check"),
      ("sm-workflow-repository-validation-4", "test-only", "fixture-primary-test-only"),
      (binding.dependency.workspace, "compile", "fixture-dependency-compile"),
      (binding.dependency.workspace, "fix-check", "fixture-dependency-fix-check"),
      (binding.dependency.workspace, "test-only", "fixture-dependency-test-only"))
    val programs = definitions.map { (target, operation, identity) =>
      val value = template.mapObject(_.add("target", target.asJson).add("operation", operation.asJson)
        .add("identity", identity.asJson).add("capability", identity.asJson).remove("testClasses"))
      if (operation == "test-only") value.mapObject(_.add("testClasses", Vector("fixture.SubjectCheckSpec").asJson)) else value
    }
    val extraTargets = Vector("sm-workflow-repository-validation-2", "sm-workflow-repository-validation-4")
    val workspaces = array(process, "workspaces") ++ extraTargets.map(target =>
      Json.obj("identity" -> target.asJson, "root" -> primaryRoot.toString.asJson))
    val workspaceGrants = array(process, "workspaceGrants") ++ extraTargets.map(target =>
      Json.obj("componentId" -> component.asJson, "workspaceId" -> target.asJson, "access" -> "manage".asJson))
    val gitCapabilities = array(process, "gitPrograms").map(_.hcursor.get[String]("capability").toOption.get)
    val grants = Json.arr(Json.obj("componentId" -> component.asJson,
      "capabilities" -> (gitCapabilities ++ definitions.map(_._3)).distinct.asJson))
    val updated = process.mapObject(_.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues(workspaces)).add("workspaceGrants", Json.fromValues(workspaceGrants))
      .add("grants", grants))
    Files.writeString(configPath, config.mapObject(_.add("sm-workflow.runtime.definition", ThreeWorkflowRuntimeParameters.encode(host).noSpaces.asJson)
      .add("textus.subsystem.process-execution.plan", updated.noSpaces.asJson)).noSpaces)
    F.reopenInstalledRepository(initial.root, host, plan, 1000)
  }

  "A dependency check sharing a primary ordinal" should {
    "continue an ordinary Session without rerunning its previously closed Step" in {
      val scenario = sixCheckScenario()
      val task = scenario.ownTask("subject-ordinals")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      def save(name: String, value: String): String = {
        val file = scenario.root.resolve(name + ".json")
        Files.writeString(file, value, StandardOpenOption.CREATE_NEW)
        file.toUri.toASCIIString
      }
      def submit(request: V.GoalPhaseAdvanceResult, result: Json, name: String): F.Result =
        scenario.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
          "--result", result.noSpaces, "--resultReference", json(V.WorkflowArtifactReference(name, "1").toRecord()).noSpaces))
      def evidence(output: F.Result, purpose: String, result: Json, reference: V.WorkflowArtifactReference,
        name: String, participant: Option[String]): V.WorkflowExecutionEvidence = {
        val worker = if (purpose == "human") "participant" else "fixture-independent-reviewer"
        val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(worker), None, None)
        val input = V.WorkflowEvidencePreparationInput("GoalPhase", purpose,
          save(name + "-request", Files.readString(output.invocation.stdout)), save(name + "-result", result.noSpaces),
          save(name + "-reference", json(reference.toRecord()).noSpaces),
          save(name + "-execution", json(execution.toRecord()).noSpaces), "normal-development-host",
          Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence", participant)
        val prepared = scenario.prepareHostInput("prepareEvidence", input.toRecord())
        prepared.exitCode shouldBe 0
        scenario.selectPreparedImport(prepared)
        execution
      }
      def executions(root: Path): Vector[String] = {
        val file = root.resolve("sbt-starts.txt")
        if (Files.exists(file)) Files.readString(file).linesIterator.toVector else Vector.empty
      }
      val first = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      first.work.get.stepIdentity shouldBe Some("step-1")
      Files.writeString(Path.of(scenario.host.target.root).resolve("value.txt"), "expected\n")
      val completed = Json.obj("outcome" -> "completed".asJson, "summary" -> "Fixture first step".asJson,
        "findings" -> Json.arr())
      submit(first, completed, "fixture-first").business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val reviewOutput = scenario.advance(task)
      val review = reviewOutput.business[V.GoalPhaseAdvanceResult]
      review.work.get.kind shouldBe "ReviewStep"
      val reviewed = Json.obj("outcome" -> "accept".asJson, "summary" -> "Fixture independent review".asJson,
        "findings" -> Json.arr())
      val reviewRef = V.WorkflowArtifactReference("fixture-first-review", "1")
      val reviewEvidence = evidence(reviewOutput, "review", reviewed, reviewRef, "first-review", None)
      scenario.call("command", review.submitOperation.get, Vector("--sessionId", review.submitSessionId.get,
        "--result", reviewed.noSpaces, "--resultReference", json(reviewRef.toRecord()).noSpaces,
        "--executionEvidence", json(reviewEvidence.toRecord()).noSpaces))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val closing = scenario.advance(task)
      val closeRef = V.WorkflowArtifactReference("fixture-first-closure", "1")
      val authority = V.WorkflowAuthorityPreparationInput(closing.invocation.stdout.toUri.toASCIIString,
        save("first-closure-reference", json(closeRef.toRecord()).noSpaces), "participant", "normal-development-host",
        Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence")
      val prepared = scenario.prepareHostInput("prepareAuthority", authority.toRecord())
      prepared.exitCode shouldBe 0
      scenario.selectPreparedImport(prepared)
      val second = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      second.work.get.stepIdentity shouldBe Some("step-2")
      second.work.get.completed.map(_.step) shouldBe Vector("step-1")
      val firstRuns = executions(scenario.root)
      firstRuns should not be empty
      val binding = scenario.host.goal.dependencyValidation.get
      val proposal = Scope.Proposal("fixture-scope", "1", "IMPLEMENTATION", binding.requester, binding.dependency,
        ContextReference("fixture-usage", "1"), "Fixture second-step dependency", binding.paths,
        Vector("Preserve fixture behavior"), Vector(ContextReference("step-2", "1")), Vector.empty)
      val direction = Json.obj("outcome" -> "decision-needed".asJson, "summary" -> "Fixture scope".asJson,
        "findings" -> Json.arr(), "dependencyProposal" -> proposal.asJson)
      submit(second, direction, "fixture-proposal").business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val questionOutput = scenario.advance(task)
      val question = questionOutput.business[V.GoalPhaseAdvanceResult]
      question.kind shouldBe "Human"
      val answer = V.GoalPhaseOrderedDecisionAnswer("ApproveDependencyScope", "Fixture-owned approval")
      val answerRef = V.WorkflowArtifactReference("fixture-approval", "1")
      val answerEvidence = evidence(questionOutput, "human", json(answer.toRecord()), answerRef, "approval", Some("participant"))
      scenario.call("command", question.submitOperation.get, Vector("--sessionId", question.submitSessionId.get,
        "--answer", json(answer.toRecord()).noSpaces, "--resultReference", json(answerRef.toRecord()).noSpaces,
        "--executionEvidence", json(answerEvidence.toRecord()).noSpaces))
        .business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
      val work = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      val selection = D.Selection(ContextReference(binding.identity, binding.revision.toString),
        Vector(D.ApprovalReference(ContextReference(proposal.identity, proposal.revision),
          ContextReference(answerRef.identity, answerRef.revision))))
      Files.writeString(Path.of(binding.root).resolve("value.txt"), "expected\n")
      Files.writeString(Path.of(scenario.host.target.root).resolve("docs/phase.md"), "\nFixture second step\n", StandardOpenOption.APPEND)
      val result = Json.obj("outcome" -> "completed".asJson, "summary" -> "Fixture second step".asJson,
        "findings" -> Json.arr(), "dependencyValidation" -> selection.asJson)
      submit(work, result, "fixture-second").business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val nextOutput = scenario.advance(task)
      val next = nextOutput.business[V.GoalPhaseAdvanceResult]
      val events = Files.readString(nextOutput.invocation.stderr).linesIterator.filter(_.startsWith("[cncf-sbt-event] "))
        .map(line => parse(line.stripPrefix("[cncf-sbt-event] ")).toOption.get)
        .filter(_.hcursor.get[String]("state").toOption.contains("launching"))
        .map { event => val c = event.hcursor; (c.get[String]("operation").toOption.get,
          c.get[String]("workspaceId").toOption.get, c.get[String]("programIdentity").toOption.get) }.toVector
      events.toSet shouldBe Set(("compile", "sm-workflow-repository-validation-1", "fixture-primary-compile"),
        ("fix-check", "sm-workflow-repository-validation-2", "fixture-primary-fix-check"),
        ("test-only", "sm-workflow-repository-validation-4", "fixture-primary-test-only"),
        ("compile", "cncf-source", "fixture-dependency-compile"),
        ("fix-check", "cncf-source", "fixture-dependency-fix-check"),
        ("test-only", "cncf-source", "fixture-dependency-test-only"))
      events.size shouldBe 6
      val receipts = next.validation.get.checks
      val focused = receipts.filter(_.sbt.exists(_.kind == "test-only"))
      focused.size shouldBe 1
      focused.foreach { receipt =>
        receipt.sbt.get.outcome shouldBe "succeeded"
        receipt.sbt.get.selection.get.testClasses shouldBe Vector("fixture.SubjectCheckSpec")
        receipt.sbt.get.selection.get.completedSuites should be > 0
        receipt.sbt.get.selection.get.testsRun should be > 0L
      }
      val dependencyView = GoalPhaseNativeMetadata.normalizePublicJson(json(next.validation.get.dependencyValidation.get))
        .fold(reason => fail(reason), identity)
      val dependencyReceipts = dependencyView.hcursor.get[Vector[Json]]("checks").toOption.get
      val dependencyFocused = dependencyReceipts.filter(_.hcursor.downField("sbt").get[String]("kind").toOption.contains("test-only"))
      dependencyFocused.size shouldBe 1
      val facts = dependencyFocused.head.hcursor.downField("sbt")
      facts.get[String]("outcome").toOption shouldBe Some("succeeded")
      facts.downField("selection").get[Vector[String]]("testClasses").toOption shouldBe Some(Vector("fixture.SubjectCheckSpec"))
      facts.downField("selection").get[Int]("completedSuites").toOption.get should be > 0
      facts.downField("selection").get[Long]("testsRun").toOption.get should be > 0L
      next.sessionId shouldBe started.sessionId
      next.work.get.kind shouldBe "ReviewStep"
      next.work.get.completed.map(_.step) shouldBe Vector("step-1")
      next.work.get.originalBase shouldBe first.work.get.originalBase
      val currentRuns = executions(scenario.root)
      currentRuns.size shouldBe firstRuns.size + 3
      val dependencyRuns = executions(scenario.root.resolve("dependency"))
      dependencyRuns.size shouldBe 3
      val again = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      again.issuedWorkContext shouldBe next.issuedWorkContext
      executions(scenario.root) shouldBe currentRuns
      executions(scenario.root.resolve("dependency")) shouldBe dependencyRuns
    }
    "refuse an identity collision at fresh completion before candidate effects" in {
      val checks = Vector(GoalPhaseNativeValidation.Check("test", 1, WorkflowSbtValidation.Kind.Compile))
      val scenario = F.freshDependency("subject-collision", dependencyChecks = Some(checks))
      val task = scenario.ownTask("subject-collision")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      def save(name: String, value: String): String = {
        val file = scenario.root.resolve(name + ".json")
        Files.writeString(file, value, StandardOpenOption.CREATE_NEW)
        file.toUri.toASCIIString
      }
      def submit(request: V.GoalPhaseAdvanceResult, result: Json, name: String): F.Result =
        scenario.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
          "--result", result.noSpaces, "--resultReference", json(V.WorkflowArtifactReference(name, "1").toRecord()).noSpaces))
      def evidence(output: F.Result, purpose: String, result: Json, reference: V.WorkflowArtifactReference,
        name: String, participant: Option[String]): V.WorkflowExecutionEvidence = {
        val worker = if (purpose == "human") "participant" else "fixture-independent-reviewer"
        val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(worker), None, None)
        val input = V.WorkflowEvidencePreparationInput("GoalPhase", purpose,
          save(name + "-request", Files.readString(output.invocation.stdout)), save(name + "-result", result.noSpaces),
          save(name + "-reference", json(reference.toRecord()).noSpaces),
          save(name + "-execution", json(execution.toRecord()).noSpaces), "normal-development-host",
          Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence", participant)
        val prepared = scenario.prepareHostInput("prepareEvidence", input.toRecord())
        prepared.exitCode shouldBe 0
        scenario.selectPreparedImport(prepared)
        execution
      }
      def executions(root: Path): Vector[String] = {
        val file = root.resolve("sbt-executions.txt")
        if (Files.exists(file)) Files.readString(file).linesIterator.toVector else Vector.empty
      }
      val first = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      first.work.get.stepIdentity shouldBe Some("step-1")
      val binding = scenario.host.goal.dependencyValidation.get
      val proposal = Scope.Proposal("fixture-scope", "1", "IMPLEMENTATION", binding.requester, binding.dependency,
        ContextReference("fixture-usage", "1"), "Fixture second-step dependency", binding.paths,
        Vector("Preserve fixture behavior"), Vector(ContextReference("step-1", "1")), Vector.empty)
      val direction = Json.obj("outcome" -> "decision-needed".asJson, "summary" -> "Fixture scope".asJson,
        "findings" -> Json.arr(), "dependencyProposal" -> proposal.asJson)
      submit(first, direction, "fixture-proposal").business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val questionOutput = scenario.advance(task)
      val question = questionOutput.business[V.GoalPhaseAdvanceResult]
      question.kind shouldBe "Human"
      val answer = V.GoalPhaseOrderedDecisionAnswer("ApproveDependencyScope", "Fixture-owned approval")
      val answerRef = V.WorkflowArtifactReference("fixture-approval", "1")
      val answerEvidence = evidence(questionOutput, "human", json(answer.toRecord()), answerRef, "approval", Some("participant"))
      scenario.call("command", question.submitOperation.get, Vector("--sessionId", question.submitSessionId.get,
        "--answer", json(answer.toRecord()).noSpaces, "--resultReference", json(answerRef.toRecord()).noSpaces,
        "--executionEvidence", json(answerEvidence.toRecord()).noSpaces))
        .business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
      val work = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      val selection = D.Selection(ContextReference(binding.identity, binding.revision.toString),
        Vector(D.ApprovalReference(ContextReference(proposal.identity, proposal.revision),
          ContextReference(answerRef.identity, answerRef.revision))))
      Files.writeString(Path.of(binding.root).resolve("value.txt"), "expected\n")
      Files.writeString(Path.of(scenario.host.target.root).resolve("value.txt"), "expected\n")
      def repositoryState(root: Path): Vector[String] = {
        def read(args: String*): String = {
          val process = new ProcessBuilder((Vector("git", "-C", root.toString) ++ args).toArray*).redirectErrorStream(true).start()
          val output = new String(process.getInputStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
          process.waitFor() shouldBe 0
          output
        }
        Vector(read("rev-parse", "HEAD^{tree}"), read("ls-files", "--stage"), read("status", "--porcelain=v1"))
      }
      val primaryRoot = Path.of(scenario.host.target.root)
      val dependencyRoot = Path.of(binding.root)
      val primaryBefore = repositoryState(primaryRoot)
      val dependencyBefore = repositoryState(dependencyRoot)
      val primaryRuns = executions(scenario.root)
      val dependencyRuns = executions(scenario.root.resolve("dependency"))
      val result = Json.obj("outcome" -> "completed".asJson, "summary" -> "Collision fixture".asJson,
        "findings" -> Json.arr(), "dependencyValidation" -> selection.asJson)
      submit(work, result, "fixture-collision").exitCode should not be 0
      val pending = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      pending.work shouldBe work.work
      pending.issuedWorkContext shouldBe work.issuedWorkContext
      repositoryState(primaryRoot) shouldBe primaryBefore
      repositoryState(dependencyRoot) shouldBe dependencyBefore
      executions(scenario.root) shouldBe primaryRuns
      executions(scenario.root.resolve("dependency")) shouldBe dependencyRuns
    }
  }
}
