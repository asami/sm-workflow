package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import java.nio.file.Path

/** Default evidence is selected only from the actual admitted host plans. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class ProjectInitializationBootstrapSpec extends AnyWordSpec with Matchers {
  "The standard project evidence binding" should {
    "use the admitted project workspace and storage collection, and decline incomplete custom plans" in {
      val descriptor = SplitPhaseExplicitPartitionFixture.take(
        GenericSubsystemDescriptor.loadComponentArchive(Path.of("src/main/car").toAbsolutePath))
      val storage = io.circe.parser.parse(descriptor.config("textus.subsystem.storage.plan")).toOption.get
      val template = io.circe.parser.parse(descriptor.config("textus.subsystem.process-execution.plan")).toOption.get
      def expand(value: Json): Json = value.mapString(_.replace("${repository.root}", "/tmp/selected-project"))
        .mapArray(_.map(expand)).mapObject(_.mapValues(expand))
      val process = expand(template)
      val selected = CommonEvidence.defaultPolicy(storage, process).get
      selected.workspaceIdentity shouldBe "sm-workflow-repository"
      selected.directory shouldBe ".codex-workflow/evidence"
      CommonEvidence.defaultPolicy(storage.mapObject(_.add("records", Json.arr())), process) shouldBe None
      CommonEvidence.defaultPolicy(storage, process.mapObject(_.add("workspaceGrants", Json.arr()))) shouldBe None
      CommonEvidence.defaultPolicy(storage, template) shouldBe None
    }
  }
}

/** End-to-end source fixture: a fresh repository and the actual component-owned
  * CAR declarations, with no project configuration or hand-written grants. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.PackagedRuntimeTestDeclaration])
final class ProjectInitializationEmptyDefaultSpec extends AnyWordSpec with Matchers {
  import io.circe.syntax.*
  import java.nio.file.Files
  import java.util.concurrent.TimeUnit
  import scala.jdk.CollectionConverters.*

  private def run(cwd: Path, args: Vector[String]): (Int, String, String) = {
    val out = Files.createTempFile(cwd.getParent, "bootstrap-stdout-", ".log")
    val err = Files.createTempFile(cwd.getParent, "bootstrap-stderr-", ".log")
    val builder = new ProcessBuilder(args.asJava).directory(cwd.toFile)
      .redirectOutput(out.toFile).redirectError(err.toFile)
    builder.environment().put("PATH", cwd.getParent.resolve("bin").toString + java.io.File.pathSeparator +
      sys.env.getOrElse("PATH", ""))
    builder.environment().put("GIT_CONFIG_NOSYSTEM", "1")
    builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null")
    val process = builder.start()
    process.getOutputStream.close()
    try {
      if (!process.waitFor(240, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        process.waitFor(10, TimeUnit.SECONDS)
        fail("Owned fixture process timed out: " + args.mkString(" "))
      }
      (process.exitValue(), Files.readString(out), Files.readString(err))
    } finally {
      if (process.isAlive) {
        process.destroyForcibly()
        process.waitFor(10, TimeUnit.SECONDS)
      }
    }
  }

  private def successful(cwd: Path, args: Vector[String]): String = {
    val (status, out, err) = run(cwd, args)
    withClue(args.mkString(" ") + "\n" + err + "\n" + out) { status shouldBe 0 }
    out
  }

  "The empty component-default environment" should {
    "prepare evidence and start, reuse and resume one actual Session without manual grants" in {
      exerciseStart(prepareFirst = true)
    }

    "start first in a fresh environment and resume its retained Session" in {
      exerciseStart(prepareFirst = false)
    }
  }

  private def exerciseStart(prepareFirst: Boolean): Unit = {
      // Given a fresh isolated repository and the packaged component defaults.
      val producer = Path.of("").toAbsolutePath.normalize
      val root = Files.createTempDirectory(Files.createDirectories(producer.resolve("target/empty-default-spec")), "case-").toRealPath()
      val repository = Files.createDirectory(root.resolve("project"))
      val bin = Files.createDirectory(root.resolve("bin"))
      val sbt = bin.resolve("sbt")
      Files.writeString(sbt, "#!/bin/sh\ntouch '" + repository.resolve("sbt-executions.txt") + "'\nexit 77\n")
      sbt.toFile.setExecutable(true) shouldBe true
      val remote = root.resolve("remote.git")
      def git(args: String*): String = successful(repository, Vector("git") ++ args)
      git("init", "--bare", "--initial-branch=main", remote.toString)
      git("init", "--initial-branch=main", repository.toString)
      git("config", "user.name", "Specification")
      git("config", "user.email", "specification@example.invalid")
      git("remote", "add", "origin", remote.toString)
      git("config", "branch.main.remote", "origin")
      git("config", "branch.main.merge", "refs/heads/main")
      Files.writeString(repository.resolve(".gitignore"), ".codex-workflow/\n")
      val input = org.simplemodeling.textus.smworkflow.value.GoalPhaseStartInput("PHASE-EMPTY", "fixture-project")
      val plan = GoalPhaseOrderedProgression.Plan("empty-plan", "1", "PHASE-EMPTY", "fixture-project", Vector(
        GoalPhaseOrderedProgression.Step("step-1", "Review the selected fixture", Vector("src/Fixture.scala"), Vector.empty, Vector("test"))), Vector("test"))
      val inputPath = repository.resolve("start-input.json")
      val planPath = repository.resolve("start-plan.json")
      Files.writeString(inputPath, SplitPhaseExplicitPartitionFixture.take(input.toRecord().toJsonStringC))
      Files.writeString(planPath, plan.asJson.noSpaces)
      git("add", ".gitignore", "start-input.json", "start-plan.json")
      git("commit", "-m", "fixture input")
      git("push", "origin", "HEAD:refs/heads/main")
      git("fetch", "origin", "refs/heads/main:refs/remotes/origin/main")
      val originalHead = git("rev-parse", "HEAD").trim
      val originalIndex = git("write-tree").trim
      val car = Path.of(Files.readString(producer.resolve("target/sm-workflow/packaged-test-car.txt")).trim)
      Files.isRegularFile(car) shouldBe true
      Files.exists(repository.resolve(".textus")) shouldBe false
      Files.exists(repository.resolve(".codex-workflow")) shouldBe false
      val launcher = Vector(producer.resolve("bin/launcher").toString, "--classpath-file",
        producer.resolve("target/cncf.d/runtime-classpath.txt").toString, "--main-class",
        "org.goldenport.cncf.CncfMain", "--", "--force-exit", "--no-default-components",
        "--component-file", car.toString, "command")
      def call(operation: String, args: Vector[String]): io.circe.Json = {
        val text = successful(repository, launcher ++ Vector("org.simplemodeling.textus.SmWorkflow." + operation) ++ args ++ Vector("--json"))
        io.circe.parser.parse(text).fold(error => fail(error.message + "\n" + text), identity)
      }
      def value[A: org.goldenport.convert.ValueReader](json: Json): A = {
        val record = SplitPhaseExplicitPartitionFixture.take(new org.goldenport.record.io.RecordDecoder().json(json.noSpaces))
        SplitPhaseExplicitPartitionFixture.take(summon[org.goldenport.convert.ValueReader[A]].readC(record))
      }
      val participant = "sm-workflow-local" // actual principal in the candidate CAR descriptor
      val intent = Json.obj("plan" -> planPath.toUri.toASCIIString.asJson, "selectionId" -> "empty-selection".asJson,
        "participant" -> participant.asJson, "origin" -> "ExplicitHuman".asJson, "idempotencyKey" -> "empty-start".asJson)
      def verifyEvidencePreparation(): Unit = {
      val preparation = Json.obj("input" -> inputPath.toUri.toASCIIString.asJson, "plan" -> planPath.toUri.toASCIIString.asJson,
        "selectionId" -> "preview-selection".asJson, "participant" -> participant.asJson, "origin" -> "ExplicitHuman".asJson,
        "idempotencyKey" -> "preview-key".asJson, "selectionCollection" -> "entity:smworkflow.local.goal_selection".asJson,
        "planCollection" -> "entity:smworkflow.local.goal_plans".asJson)
      val first = value[org.simplemodeling.textus.smworkflow.value.WorkflowPreparationResult](
        call("NativeWorkflowPreparationService.prepareGoalStart", Vector("--input", preparation.noSpaces)))
      withClue(first.message.toString) { first.status shouldBe Some("prepared") }
      first.retainedImport.nonEmpty shouldBe true
      Files.isDirectory(repository.resolve(".codex-workflow/evidence")) shouldBe true
      val again = value[org.simplemodeling.textus.smworkflow.value.WorkflowPreparationResult](
        call("NativeWorkflowPreparationService.prepareGoalStart", Vector("--input", preparation.noSpaces)))
      again.status shouldBe Some("prepared")
      again.retainedImport shouldBe first.retainedImport
      val retainedInput = SplitPhaseExplicitPartitionFixture.take(first.retainedImport.get.toRecord().toJsonStringC)
      val retained = call("NativeWorkflowPreparationService.readPreparedImport", Vector("--input", retainedInput))
      retained.hcursor.downField("datastore").focus.nonEmpty shouldBe true
      Files.isRegularFile(repository.resolve(".codex-workflow/storage/workflow.sqlite")) shouldBe true
      }
      if (prepareFirst) verifyEvidencePreparation()
      // When ordinary Start is the first workflow operation in the fresh case.
      if (!prepareFirst) {
        Files.exists(repository.resolve(".textus")) shouldBe false
        Files.exists(repository.resolve(".codex-workflow")) shouldBe false
      }
      val started = value[org.simplemodeling.textus.smworkflow.value.GoalPhaseStartResult](
        call("GoalPhaseService.startGoalPhase", Vector("--input",
          SplitPhaseExplicitPartitionFixture.take(input.toRecord().toJsonStringC), "--intent", intent.noSpaces)))
      // Then its actual Session and selected Plan are retained through the public path.
      started.outcome shouldBe "started"
      Files.isRegularFile(repository.resolve(".codex-workflow/storage/workflow.sqlite")) shouldBe true
      val session = started.sessionId
      session.nonEmpty shouldBe true
      if (!prepareFirst) verifyEvidencePreparation()
      git("rev-parse", "HEAD").trim shouldBe originalHead
      git("write-tree").trim shouldBe originalIndex
      git("status", "--porcelain").trim shouldBe ""
      Files.exists(repository.resolve(".textus")) shouldBe false
      Files.exists(repository.resolve("sbt-executions.txt")) shouldBe false
      val issued = call("GoalPhaseService.advanceGoalPhase", Vector("--sessionId", session))
      issued.hcursor.get[String]("kind").toOption shouldBe Some("Request")
      val reference = issued.hcursor.downField("retained_work").focus.getOrElse(fail("Missing retained work reference"))
      val detail = call("WorkflowRetainedWorkService.readWorkflowRetainedWork", Vector("--input", reference.noSpaces))
      detail.hcursor.get[String]("availability").toOption shouldBe Some("available")
      val selected = detail.hcursor.downField("response").downField("work").downField("selected_plan")
      selected.get[String]("identity").toOption shouldBe Some(plan.identity)
      selected.get[String]("phase").toOption shouldBe Some(plan.phase)
      val reopened = call("GoalPhaseService.advanceGoalPhase", Vector("--sessionId", session))
      reopened shouldBe issued
      git("rev-parse", "HEAD").trim shouldBe originalHead
      git("write-tree").trim shouldBe originalIndex
      Files.exists(repository.resolve("sbt-executions.txt")) shouldBe false
      git("status", "--porcelain").trim shouldBe ""
      Files.exists(repository.resolve(".textus")) shouldBe false
  }
}
