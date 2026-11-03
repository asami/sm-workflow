package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalReceiptLimits, WorkflowMechanicalTransitionJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import RepositorySyncFormalFailureContinuation as N
import RepositorySyncNativeMergeValidation as C
import RepositorySyncPurpose as P
import SplitPhaseExplicitPartitionFixture.take

/** Tiny product inputs and ordinary fixed host registrations. Only the installed
  * runtime executes SBT or adopts the fixture's independently prepared direction.
  */
private[admission] trait NativeRepositorySelectionCorrectionFixture extends NativeRepositoryContinuationCliFixture {
  private lazy val _receipt_catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(RepositorySyncDefinitionBinding.bindC))))
  private lazy val _receipt_limits = take(WorkflowMechanicalReceiptLimits.createC(WorkflowMechanicalReceiptLimits.maximumBytes))
  override protected def receipts(scenario: F.Scenario, session: String) = stored(scenario).flatMap(raw =>
    WorkflowMechanicalTransitionJsonV1.decodeC(raw, _receipt_catalog, RepositorySyncNativeProgressStore.codec, _receipt_limits)
      .toOption).filter(_.next.identity.value == session)
  protected val originalChecks = Vector(C.Check("selection-compile", 1, WorkflowSbtValidation.Kind.Compile),
    C.Check("selection-test", 2, WorkflowSbtValidation.Kind.Test))
  protected val appendedCheck = C.Check("selection-test-only", 3, WorkflowSbtValidation.Kind.TestOnly)
  protected val selectedClass = "fixture.SelectedInputSpec"
  protected def installed(): Path = {
    val executable = Path.of(sys.props.get("smworkflow.spec.installedCommand")
      .orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND")).getOrElse(fail("Select the actual installed command")))
    require(executable.isAbsolute && executable.normalize == executable && Files.isRegularFile(executable) && Files.isExecutable(executable))
    System.setProperty("smworkflow.spec.installedCommand", executable.toString)
    executable
  }
  protected def successful(result: F.Result): F.Result = {
    withClue(result.invocation.logs) { result.exitCode shouldBe 0 }
    result.invocation.argv.head shouldBe installed().toString
    result
  }
  private def array(value: Json, name: String): Vector[Json] = value.hcursor.get[Vector[Json]](name).toOption.get
  protected def selectionParameters(corrected: Boolean): Map[String, String] = {
    val checks = originalChecks ++ Option.when(corrected)(appendedCheck)
    val paths = Vector("value.txt", "src", "build.sbt", "project")
    val impact = RepositorySyncProgramImpact.Policy(checks.map(check => RepositorySyncProgramImpact.Coverage(check,
      paths, paths, "The real product and its build require this fixed validation")), checks,
      Vector("src", "build.sbt", "project"), "Validate the actual selected product input", Vector.empty)
    val slot = P.Slot("purpose-slot-1", checks,
      GoalPhaseStepAssessment.Policy("selection-purpose", "code", "required", "off", "off", Vector.empty))
    maintenance_parameters() ++ Map("sm-workflow.repository-sync.program-inputs" -> impact.asJson.noSpaces,
      "sm-workflow.repository-sync.dirty-purposes" -> P.Policy(Vector(slot), Vector.empty).asJson.noSpaces,
      "cncf.resource.read.max-bytes" -> "67108864", "cncf.resource.write.max-bytes" -> "67108864")
  }
  protected def selectionPlan(scenario: F.Scenario, broken: Boolean, corrected: Boolean, large: Boolean = false): Json = {
    val base = process_plan(scenario.root, "Equal", scenario.host.goal)
    val kinds = Vector(SbtOperation.Compile, SbtOperation.Test) ++ Option.when(corrected)(SbtOperation.TestOnly)
    val programs = kinds.zipWithIndex.flatMap { (kind, index) =>
      val plan = TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable, Vector(index + 1),
        operation = kind, testClasses = if (kind == SbtOperation.TestOnly) Vector(selectedClass) else Vector.empty)
      array(plan, "sbtPrograms").map { program =>
        val bound = program.mapObject(_.add("target", "purpose-slot-1".asJson))
        if (broken && index == 1) bound.mapObject(fields => fields.add("environment", fields("environment").get.mapObject(
          _.add("JAVA_TOOL_OPTIONS", "-XX:SmWorkflowInvalidFixtureOption".asJson)))) else bound
      }
    }
    // Each reserved target is a real normal SBT registration with the actual
    // runtime classpath. They are configured before describe, never inserted
    // into or used to pad a described business target.
    val reserved = if (!large) Vector.empty else {
      val classpath = Files.readAllLines(Path.of("target/cncf.d/runtime-classpath.txt").toAbsolutePath)
      val entries = classpath.asScala.toVector
      require(entries.nonEmpty && entries.forall(path => Files.exists(Path.of(path))))
      val environment = entries.mkString(java.io.File.pathSeparator)
      val prototype = programs.head.mapObject(fields => fields.add("environment", fields("environment").get
        .mapObject(_.add("CLASSPATH", environment.asJson))))
      val count = (5000000 / prototype.noSpaces.getBytes(UTF_8).length) + 1
      (1 to count).toVector.map { index =>
        val identity = "resource-reserved-sbt-" + index
        prototype.mapObject(_.add("target", identity.asJson).add("identity", identity.asJson).add("capability", identity.asJson))
      }
    }
    val reservedWorkspaces = reserved.map(program => Json.obj("identity" -> program.hcursor.get[Json]("target").toOption.get,
      "root" -> scenario.host.target.root.asJson))
    val reservedGrants = reserved.map(program => Json.obj("componentId" -> org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name.asJson,
      "workspaceId" -> program.hcursor.get[Json]("target").toOption.get, "access" -> "manage".asJson))
    val all = programs ++ reserved
    base.mapObject(fields => fields.add("sbtPrograms", all.asJson)
      .add("workspaces", (array(base, "workspaces") ++ reservedWorkspaces).asJson)
      .add("workspaceGrants", (array(base, "workspaceGrants") ++ reservedGrants).asJson)
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(existing => (existing ++ all.map(_.hcursor.get[Json]("capability").toOption.get)).distinct)))))))
  }
  protected def failedSelection(name: String, large: Boolean = false): (F.Scenario, String) = {
    installed()
    val scenario = fresh(name, checked = true)
    val descriptor = scenario.root.resolve(".textus/assembly.yaml")
    val assembly = Files.readString(descriptor, UTF_8)
    assembly should include("      id: participant\n")
    Files.writeString(descriptor, assembly.replace("      id: participant\n", "      id: sm-workflow-local\n"), UTF_8)
    val repository = Path.of(scenario.host.target.root)
    val build = repository.resolve("build.sbt")
    val originalBuild = Files.readString(build)
    originalBuild should include("Test / test := (Test / runMain).toTask(\" ProductCheck\").value\n")
    Files.writeString(build, originalBuild.replace("Test / test := (Test / runMain).toTask(\" ProductCheck\").value\n", "") +
      "\nlibraryDependencies += \"org.scalatest\" %% \"scalatest\" % \"3.2.19\" % Test\n")
    val source = repository.resolve("src/test/scala/fixture/SelectedInputSpec.scala")
    Files.createDirectories(source.getParent)
    Files.writeString(source, """package fixture
      |class SelectedInputSpec extends org.scalatest.wordspec.AnyWordSpec
      |    with org.scalatest.GivenWhenThen with org.scalatest.matchers.should.Matchers {
      |  "the actual selected product input" should {
      |    "contain the complete expected value" in {
      |      Given("the actual materialized product workspace")
      |      val root = java.nio.file.Paths.get("").toAbsolutePath.normalize
      |      When("the selected test reads the product input")
      |      val value = java.nio.file.Files.readString(root.resolve("value.txt"))
      |      Then("the complete expected value is retained")
      |      value shouldBe "expected\n"
      |      java.nio.file.Files.writeString(root.getParent.resolve("selected-test-runs.txt"), "selected\n",
      |        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)
      |    }
      |  }
      |}
      |""".stripMargin)
    git(scenario.root, "-C", repository.toString, "add", "--", "build.sbt", "src/test/scala/fixture/SelectedInputSpec.scala")
    git(scenario.root, "-C", repository.toString, "commit", "-m", "Retain actual selected product specification")
    git(scenario.root, "-C", repository.toString, "push", scenario.host.target.endpoint, "HEAD:refs/heads/main")
    configure(scenario, selectionParameters(corrected = false) +
      ("textus.subsystem.process-execution.plan" -> selectionPlan(scenario, broken = true, corrected = false, large = large).noSpaces))
    Files.writeString(repository.resolve("value.txt"), "expected\n")
    val session = start(scenario)
    successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitRepositoryPurposeResult")
    val request = purpose_inputs(scenario).head
    val grouped = P.Result("resolved", "Fixture-owned actual product purpose", Vector(P.Group("selected-input",
      "Correct the actual selected product input", "fix: retain expected product input", Vector.empty,
      Vector(P.Content("value.txt", Some("expected\n"))), Vector.empty, "The real product consumes this input")),
      request.files.map(_.path).filterNot(_ == "value.txt"))
    successful(submit_purpose(scenario, session, grouped)).business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
    successful(advance(scenario, session))
    formalReceipts(scenario, session).map(_._2.identity).toSet shouldBe originalChecks.map(_.identity).toSet
    formalReceipts(scenario, session).find(_._2.identity == "selection-test").get._2.exitCode.exists(_ != 0) shouldBe true
    successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitNativeMergeRepair")
    current(scenario, session).suspension should not be empty
    configure(scenario, selectionParameters(corrected = true) +
      ("textus.subsystem.process-execution.plan" -> selectionPlan(scenario, broken = false, corrected = true, large = large).noSpaces))
    scenario -> session
  }
  protected def formalReceipts(scenario: F.Scenario, session: String): Vector[(String, C.Receipt)] =
    receipts(scenario, session).flatMap(receipt => Option.when(receipt.reference.identity.matches(".*-check-[0-9]+-result"))(
      receipt.payload.value.purposes.flatMap(_.receipts.lastOption)).flatten.filter(_.sbt.nonEmpty)
      .map(receipt.reference.identity -> _)).sortBy(_._1)
  protected def selectionTarget(result: F.Result): N.Target = take(RepositorySyncValidationContinuation.anyTargetC(
    successful(result).business[V.RepositorySyncValidationContinuationDescriptionResult].target)) match {
    case RepositorySyncValidationContinuation.FormalFailureTarget(value) => value
    case _ => fail("Expected the actual explicitly discriminated formal-failure target")
  }
  protected def assertFreshSelectedReview(scenario: F.Scenario, session: String, target: N.Target,
    old: InstanceRecord, original: Vector[(String, C.Receipt)]): Unit = {
    val first = successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
    first.submitOperation shouldBe Some("submitNativeMergeReview")
    val after = formalReceipts(scenario, session)
    original.foreach(value => after should contain(value))
    after.count(_._2.identity == "selection-compile") shouldBe 1
    after.count(_._2.identity == "selection-test") shouldBe 2
    val selected = after.filter(_._2.identity == appendedCheck.identity)
    selected.size shouldBe 1
    val facts = selected.head._2.sbt.get
    facts.kind shouldBe WorkflowSbtValidation.Kind.TestOnly
    facts.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
    facts.selection.get.testClasses shouldBe Vector(selectedClass)
    facts.selection.get.testsRun should be > 0L
    Files.readString(scenario.root.resolve("selected-test-runs.txt")) shouldBe "selected\nselected\n"
    val progress = receipts(scenario, session).flatMap(_.payload.value.purposes).filter(_.hostContinuation.exists(_.target == target))
    progress should not be empty
    progress.foreach(value => (value.cursor, value.attempt, value.slotCursor, value.candidate, value.commits) shouldBe
      ((target.purpose.get.cursor, target.purpose.get.attempt, target.purpose.get.slotCursor, target.purpose.get.candidate, target.purpose.get.commits)))
    current(scenario, session).history.take(old.history.size) shouldBe old.history
    val repeated = successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
    repeated.issuedWorkContext shouldBe first.issuedWorkContext
    formalReceipts(scenario, session) shouldBe after
    Files.readString(scenario.root.resolve("selected-test-runs.txt")) shouldBe "selected\nselected\n"
  }
}

@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryValidationSelectionCorrectionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeRepositorySelectionCorrectionFixture {
  "RS-FORMAL-03 installed selection correction" should {
    "retain the real failure and UNKNOWN old worker while separate Advance executes nonempty TestOnly and requests independent review" in {
      Given("a tiny real Purpose with a successful Compile actual failed SBT launch and pending semantic repair")
      val (scenario, session) = failedSelection("selection-correction-public")
      val old = current(scenario, session)
      val original = formalReceipts(scenario, session)
      When("the corrected fixed host and selection are described twice")
      val described = describe(scenario, session)
      val target = selectionTarget(described)
      selectionTarget(describe(scenario, session)) shouldBe target
      target.continuationPolicy shouldBe N.selectionPolicy
      target.selectedChecks shouldBe originalChecks
      target.proposedChecks shouldBe Some(originalChecks :+ appendedCheck)
      target.pending should not be empty
      current(scenario, session) shouldBe old
      val input = direction(scenario, session, described, N.selectionChoices.head, true, true)
      val repository = Path.of(scenario.host.target.root)
      val head = git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD")
      val tree = git(scenario.root, "-C", repository.toString, "write-tree")
      val invalid = Vector(input.copy(choice = N.choices.head), input.copy(priorExecutionEnded = false),
        input.copy(sessionId = session + "-foreign"), input.copy(target = N.publicRecord(target.copy(
          proposedChecks = Some(Vector(appendedCheck))))), input.copy(target = N.publicRecord(target.copy(
          indexTree = "f" * 40))))
      invalid.foreach { value =>
        adopt(scenario, value).exitCode should not be 0
        current(scenario, session) shouldBe old
        formalReceipts(scenario, session) shouldBe original
      }
      When("the complete independent selection direction is adopted and repeated")
      successful(adopt(scenario, input)).business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "adopted"
      val admitted = current(scenario, session)
      successful(adopt(scenario, input)).business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "already-adopted"
      Then("only one direction is appended with no check or source effects")
      current(scenario, session) shouldBe admitted
      admitted.revision shouldBe old.revision.next
      formalReceipts(scenario, session) shouldBe original
      git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe head
      git(scenario.root, "-C", repository.toString, "write-tree") shouldBe tree
      stored(scenario).exists(raw => raw.contains("originalWorkerEffects") && raw.contains("UNKNOWN") && raw.contains(target.pending.get.identity)) shouldBe true
      val reference = V.WorkflowArtifactReference("stale-selection-worker-result", "1")
      scenario.repositoryCall("submitNativeMergeRepair", Vector("--sessionId", session, "--result",
        "{\"decision\":\"decision-needed\",\"rationale\":\"Stale original worker\",\"replacements\":[]}",
        "--resultReference", json(reference), "--executionEvidence", json(V.WorkflowExecutionEvidence(
          Vector(reference), Some("stale-old-worker"), None, None)))).exitCode should not be 0
      When("separate ordinary Advance runs the remaining checks")
      assertFreshSelectedReview(scenario, session, target, old, original)
      Then("the old adoption target is stale after actual new work")
      adopt(scenario, input).exitCode should not be 0
    }
  }
}
