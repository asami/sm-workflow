package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import java.util.zip.ZipFile
import org.goldenport.cncf.processexecution.SbtOperation
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import RepositorySyncPurpose as P

/** A fixture-owned host resource is supplied after the real setup wait. */
@org.scalatest.tags.Slow
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.PackagedRuntimeTestDeclaration])
final class NativeRepositorySessionWorkspaceCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeRepositoryContinuationCliFixture {
  private def candidateMinimumRuntime(car: Path): String = {
    val archive = new ZipFile(car.toFile)
    try {
      val entry = Option(archive.getEntry("car-runtime-manifest.json"))
        .getOrElse(throw new IllegalArgumentException(s"Missing runtime manifest in $car"))
      val stream = archive.getInputStream(entry)
      val content = try new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
        finally stream.close()
      val manifest = parse(content).fold(error => throw new IllegalArgumentException(s"Invalid runtime manifest in $car", error), identity)
      manifest.hcursor.downField("runtime").downField("cncf").get[String]("minimum")
        .fold(error => throw new IllegalArgumentException(s"Missing CNCF minimum runtime in $car", error), identity)
    } finally archive.close()
  }

  private def success(value: F.Result): F.Result = {
    withClue(value.invocation.logs) { value.exitCode shouldBe 0 }
    value
  }

  private def withCandidatePolicy(scenario: F.Scenario, base: Json): Json = {
    val root = scenario.root
    val parent = Files.createDirectories(root.resolve("session-candidates"))
    val slot = "purpose-slot-1"
    val parentName = "purpose-parent"
    val source = TypedGitHostPlanFixture.plan(Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString)).hcursor.get[Vector[Json]]("gitPrograms").toOption.get
    val rootPrograms = source.filter(_.hcursor.get[String]("operation").contains("root")).map(_.mapObject(fields => fields
      .add("target", slot.asJson).add("identity", (fields("identity").get.asString.get + "-session-root").asJson)
      .add("capability", (fields("capability").get.asString.get + "-session-root").asJson)))
    require(rootPrograms.size == 1)
    base.mapObject(fields => fields.add("schema", "cncf.runtime-process-execution.v7".asJson)
      .add("validationChecks", Json.arr())
      .add("sessionCandidateWorkspaces", Json.arr(Json.obj("componentId" -> SmWorkflowComponent.componentId.name.asJson,
        "parent" -> parentName.asJson, "alias" -> slot.asJson)))
      .add("workspaces", fields("workspaces").get.mapArray(_ :+ Json.obj("identity" -> parentName.asJson, "root" -> parent.toString.asJson)))
      .add("workspaceGrants", fields("workspaceGrants").get.mapArray(_ :+ Json.obj(
        "componentId" -> SmWorkflowComponent.componentId.name.asJson, "workspaceId" -> parentName.asJson, "access" -> "manage".asJson)))
      .add("gitPrograms", fields("gitPrograms").get.mapArray(_ ++ rootPrograms))
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(_ ++ rootPrograms.map(_.hcursor.get[Json]("capability").toOption.get))))))))
  }

  private def withOrdinaryValidation(scenario: F.Scenario, base: Json): Json = {
    val ordinary = "project-validation"
    val alias = "purpose-slot-1"
    val template = TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable,
      Vector(1), operation = SbtOperation.Compile).hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.head
    val discovery = Json.obj("componentIdentity" -> "fixture-product".asJson,
      "artifactIdentity" -> "fixture:fixture-product".asJson, "artifactRevision" -> "0.0.1-SNAPSHOT".asJson,
      "packagePrefix" -> "fixture.sessioncheck".asJson)
    val programs = Vector(ordinary, alias).flatMap { workspace => Vector("test-compile", "test-only").map { operation =>
      val identity = workspace + "-" + operation
      val selected = template.mapObject(_.add("target", workspace.asJson).add("operation", operation.asJson)
        .add("identity", identity.asJson).add("capability", identity.asJson))
      if (operation == "test-compile") selected.mapObject(_.add("validationDiscovery", discovery))
      else selected.mapObject(_.add("testClasses", Json.arr()).add("discoveredTests", Json.True)
        .add("validationMode", Json.obj("modes" -> Vector("develop", "test").asJson,
          "provider" -> "fixture".asJson, "livePrerequisites" -> Json.arr())))
    }}
    base.mapObject(fields => fields.add("sbtPrograms", programs.asJson)
      .add("workspaces", fields("workspaces").get.mapArray(_ :+ Json.obj("identity" -> ordinary.asJson,
        "root" -> scenario.root.resolve("repository").toString.asJson)))
      .add("workspaceGrants", fields("workspaceGrants").get.mapArray(_ :+ Json.obj(
        "componentId" -> SmWorkflowComponent.componentId.name.asJson, "workspaceId" -> ordinary.asJson, "access" -> "manage".asJson)))
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(_ ++ programs.map(_.hcursor.get[Json]("capability").toOption.get))))))))
  }

  private def prepareProduct(scenario: F.Scenario): Unit = {
    TypedSbtHostPlanFixture.buildMetadata(scenario.root)
    val repository = scenario.root.resolve("repository")
    val build = repository.resolve("build.sbt")
    val original = Files.readString(build)
    val first = original.indexOf("Test / definedTests :=")
    val last = original.indexOf("Global / onLoad :=", first)
    require(first >= 0 && last > first)
    Files.writeString(build, original.substring(0, first) + original.substring(last) +
      "\norganization := \"fixture\"\nname := \"fixture-product\"\nversion := \"0.0.1-SNAPSHOT\"\nTest / fork := false\n")
    val test = repository.resolve("src/test/scala/SessionCheck.scala")
    Files.createDirectories(test.getParent)
    Files.writeString(test, """package fixture.sessioncheck
      |import org.scalatest.funsuite.AnyFunSuite
      |import org.scalatest.Outcome
      |import org.goldenport.cncf.config.OperationMode
      |import org.goldenport.cncf.validation.ValidationExecutionModeV1 as M
      |import org.goldenport.cncf.validation.ValidationDeclarationProvider
      |import org.goldenport.cncf.validation.ValidationMetadataV1.Declaration
      |import org.goldenport.cncf.validation.annotation.ValidationSpecification
      |class Modes extends ValidationDeclarationProvider {
      |  def declaration: Declaration = Declaration(requiredSemantics = Set("executionModes"), executionModes = Some(Set("test")))
      |}
      |@ValidationSpecification(declaration = classOf[Modes])
      |class SessionCheck extends AnyFunSuite {
      |  test("candidate value") {
      |    assert(java.nio.file.Files.readString(java.nio.file.Path.of("value.txt")).trim == "42")
      |    assert(sys.env.get("SESSION_FIXTURE_ENABLED").contains("true"), "fixture host prerequisite")
      |  }
      |  override protected def withFixture(test: NoArgTest): Outcome = {
      |    val mode = OperationMode.from(test.configMap(M.modeKey).toString).get
      |    val observed = M.consumeC(test.configMap.toMap, getClass.getName, test.name, mode, "fixture").toOption.get
      |    println(observed.line)
      |    super.withFixture(test)
      |  }
      |}
      |""".stripMargin)
    Files.writeString(repository.resolve("value.txt"), "41\n")
    git(scenario.root, "-C", repository.toString, "add", "--", "build.sbt", "project/build.properties", ".gitignore", "src/test/scala/SessionCheck.scala", "value.txt")
    git(scenario.root, "-C", repository.toString, "commit", "--no-verify", "--no-gpg-sign", "-m", "Prepare ordinary fixture product")
    git(scenario.root, "-C", repository.toString, "push", "--no-verify", "--", scenario.host.target.endpoint, "HEAD:refs/heads/main")
    Files.writeString(repository.resolve("value.txt"), "42\n")
  }

  private def hostPrerequisite(plan: Json, enabled: Boolean): Json = plan.mapObject(fields =>
    fields.add("sbtPrograms", fields("sbtPrograms").get.mapArray(_.map(program => program.mapObject(row =>
      row.add("environment", row("environment").get.mapObject(_.add("SESSION_FIXTURE_ENABLED", enabled.toString.asJson))))))))

  "Installed RepositorySync Session workspaces" should {
    "continue the same prepared candidate after the host supplies its missing reusable policy" in {
      Given("an installed candidate runtime and a product whose exact discovered test checks the candidate value and host prerequisite")
      val explicit = sys.props.get("smworkflow.spec.cncfCommand").orElse(sys.env.get("SM_WORKFLOW_SPEC_CNCF_COMMAND"))
      val executable = explicit.map(Path.of(_)).getOrElse {
        sys.env.getOrElse("PATH", "").split(java.io.File.pathSeparator).iterator.filter(_.nonEmpty)
          .map(directory => Path.of(directory).toAbsolutePath.normalize.resolve("cncf"))
          .find(path => Files.isRegularFile(path) && Files.isExecutable(path))
          .getOrElse(throw new IllegalArgumentException("Select the installed CNCF command or provide it on PATH"))
      }
      val pointer = Path.of("").toAbsolutePath.normalize.resolve("target/sm-workflow/packaged-test-car.txt")
      val car = Path.of(Files.readString(pointer).trim)
      val selection = F.PackagedInstalled(executable, car,
        sys.props.get("smworkflow.spec.cncfRuntime").orElse(sys.env.get("SM_WORKFLOW_SPEC_CNCF_RUNTIME"))
          .orElse(Some(candidateMinimumRuntime(car))))
      val scenario = fresh("session-workspace", checked = true)
      scenario.selectPackagedInstalled(selection)
      prepareProduct(scenario)
      val configPath = scenario.root.resolve(".textus/config.json")
      val config = parse(Files.readString(configPath)).toOption.get
      Files.writeString(configPath, config.mapObject(_.remove("sm-workflow.repository-sync.dirty-purposes")).noSpaces)
      val repository = Path.of(scenario.host.target.root)
      val path = "value.txt"
      val content = "42\n"
      val base = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
      val plan = hostPrerequisite(withOrdinaryValidation(scenario, base), false)
      configure(scenario, Map("textus.subsystem.process-execution.plan" -> plan.noSpaces))
      val session = start(scenario)
      val originalHead = git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD")
      val originalIndex = git(scenario.root, "-C", repository.toString, "ls-files", "--stage")
      success(advance(scenario, session)).business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitRepositoryPurposeResult")
      val grouping = P.Result("resolved", "One independent product change", Vector(P.Group("session-product",
        "Retain the product value", "fix: retain session product value", Vector.empty,
        Vector(P.Content(path, Some(content))), Vector.empty, "The product change is independently meaningful")), Vector.empty)
      success(submit_purpose(scenario, session, grouping)).business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
      When("the same Session prepares a candidate before its reusable workspace policy is supplied")
      val waiting = success(advance(scenario, session))
      Files.readString(waiting.invocation.stdout) should include("Session candidate setup is unavailable")
      val before = receipts(scenario, session).filter(_.payload.value.stage == "PurposePrepared")
      before.size shouldBe 1
      val retained = before.head.payload.value.purposes.get
      val admitted = withCandidatePolicy(scenario, plan)
      configure(scenario, Map("textus.subsystem.process-execution.plan" -> admitted.noSpaces))
      success(advance(scenario, session))
      val failed = receipts(scenario, session).filter(_.payload.value.stage == "PurposeRepairRequired")
      failed.size shouldBe 1
      failed.head.payload.value.purposes.get.receipts.last.exitCode.exists(_ != 0) shouldBe true
      val originalFailure = failed.head
      val failedRecord = current(scenario, session)
      val candidate = retained.candidate.get
      Then("the failed batch retains the exact candidate and leaves source HEAD index and target value intact")
      originalFailure.payload.value.purposes.get.candidate shouldBe Some(candidate)
      git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe originalHead
      git(scenario.root, "-C", repository.toString, "ls-files", "--stage") shouldBe originalIndex
      Files.readString(repository.resolve(path)) shouldBe content
      When("the host corrects its prerequisite and the existing direction owner authorizes new validation of that same candidate")
      configure(scenario, Map("textus.subsystem.process-execution.plan" -> hostPrerequisite(admitted, true).noSpaces))
      val described = success(describe(scenario, session))
      val adoption = direction(scenario, session, described, "AuthorizeNewValidationAfterHostCorrection", false, false)
      success(adopt(scenario, adoption))
      success(advance(scenario, session))
      // Reload immutable rows and the same native Session after the installing CLI has returned.
      val all = receipts(scenario, session).sortBy(_.next.revision.value)
      val installedRecord = current(scenario, session)
      Then("failed result corrected success assessment and installation survive in ordered immutable history")
      installedRecord.identity.value shouldBe session
      installedRecord.history.take(failedRecord.history.size) shouldBe failedRecord.history
      all.find(_.next.revision == originalFailure.next.revision) shouldBe Some(originalFailure)
      all.sliding(2).foreach { pair =>
        if (pair.size == 2) pair.last.next.history.take(pair.head.next.history.size) shouldBe pair.head.next.history
      }
      all.count(_.payload.value.stage == "PurposePreparing") shouldBe 1
      all.count(_.payload.value.stage == "PurposePrepared") shouldBe 1
      all.count(_.payload.value.stage == "PurposeAllocating") shouldBe 1
      all.count(_.payload.value.stage == "PurposeRepairRequired") shouldBe 1
      val allocated = all.find(_.payload.value.stage == "PurposeAllocated").get.payload.value.purposes.get
      allocated.candidate shouldBe retained.candidate
      allocated.plan shouldBe retained.plan
      val materialized = all.filter(_.payload.value.stage == "PurposeMaterialized").map(_.payload.value.purposes.get)
      materialized.exists(_.sessionWorkspace.exists(_.materialization.nonEmpty)) shouldBe true
      materialized.exists(_.sessionWorkspace.exists(w => w.root.nonEmpty && w.head.nonEmpty)) shouldBe true
      val groups = materialized.flatMap(_.receipts.flatMap(_.group))
      groups.exists(group => group.successful && group.batches.nonEmpty) shouldBe true
      val corrected = all.find(row => row.next.revision.value > originalFailure.next.revision.value &&
        row.payload.value.purposes.exists(_.receipts.exists(_.group.exists(_.successful)))).get
      val checked = all.filter(_.payload.value.stage == "PurposeChecked")
      checked.size shouldBe 1
      checked.head.payload.value.purposes.get.assessment.get.branch shouldBe "admit"
      checked.head.payload.value.purposes.get.assessment.get.candidate.tree shouldBe candidate.tree
      val installing = all.filter(_.payload.value.stage == "PurposeInstalling")
      val installed = all.filter(_.payload.value.stage == "PurposeInstalled")
      installing.size shouldBe 1
      installed.size shouldBe 1
      Vector(originalFailure.next.revision.value, corrected.next.revision.value, checked.head.next.revision.value,
        installing.head.next.revision.value, installed.head.next.revision.value).sliding(2).foreach { pair =>
          pair.last should be > pair.head
        }
      Vector(corrected, checked.head, installing.head).foreach(_.payload.value.purposes.get.candidate shouldBe Some(candidate))
      val installedProgress = installed.head.payload.value.purposes.get
      installedProgress.commits shouldBe retained.commits :+ candidate.commit
      installing.head.payload.value.purposes.get.commits shouldBe retained.commits
      git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe candidate.commit
      git(scenario.root, "-C", repository.toString, "diff", "--cached", "--name-only", "HEAD") shouldBe ""
      git(scenario.root, "-C", repository.toString, "show", ":" + path) shouldBe content.trim
      git(scenario.root, "-C", repository.toString, "show", "HEAD:" + path) shouldBe content.trim
      Files.readString(repository.resolve(path)) shouldBe content
      val repeatedRecord = current(scenario, session)
      val repeatedRows = receipts(scenario, session).sortBy(_.next.revision.value)
      repeatedRecord shouldBe installedRecord
      repeatedRows shouldBe all
      val observation = Json.obj("schemaVersion" -> "sm-workflow.session-workspace-qualification.v1".asJson,
        "session" -> session.asJson, "candidateCommit" -> candidate.commit.asJson, "candidateTree" -> candidate.tree.asJson,
        "failedRevision" -> originalFailure.next.revision.value.asJson,
        "correctedRevision" -> corrected.next.revision.value.asJson, "assessmentRevision" -> checked.head.next.revision.value.asJson,
        "installingRevision" -> installing.head.next.revision.value.asJson, "installedRevision" -> installed.head.next.revision.value.asJson,
        "commits" -> installedProgress.commits.asJson, "retainedFailure" -> true.asJson,
        "sourceHeadIndexAndContentVerified" -> true.asJson, "repeatedObservationStable" -> true.asJson)
      val observationPath = scenario.root.resolve("session-workspace-qualification.json")
      Files.writeString(observationPath, observation.spaces2)
      info("Session workspace qualification: " + observationPath.toString)
    }
  }
}
