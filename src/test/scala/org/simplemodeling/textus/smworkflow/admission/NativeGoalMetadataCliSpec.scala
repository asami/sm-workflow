package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import scala.concurrent.duration.*
import org.goldenport.convert.ValueReader
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationMetadataV1 as M}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.scalatest.{GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.metafixture.SelectedMetadataFixtureSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhaseNativeMetadata as N
import NativeCallerExecutionFixture.json
import SplitPhaseExplicitPartitionFixture.take

/** Normal separate CLI processes execute real selected ScalaTest registrations.
  * Semantic Submit is specification-owned input, never provider, Human or
  * independent review evidence. Every successful example stops pending review.
  * @since   Oct. 18, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalMetadataCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import N.given
  private val _suite = classOf[SelectedMetadataFixtureSpec].getName
  private val _unselected = "retain unannotated supplement"
  private val _reference = V.WorkflowArtifactReference("fixture-cli-metadata-implementation", "1")

  private def _lines(root: Path, name: String): Vector[String] = {
    val path = root.resolve(name)
    if (Files.exists(path)) Files.readString(path).linesIterator.filter(_.nonEmpty).toVector else Vector.empty
  }
  private def _executions(scenario: F.Scenario): Vector[Long] =
    _lines(scenario.root, "sbt-executions.txt").map(_.toLong)
  private def _commands(scenario: F.Scenario): Vector[String] = _lines(scenario.root, "sbt-command-lines.txt")
  private def _no_sbt(scenario: F.Scenario): Unit = {
    Files.exists(scenario.root.resolve("sbt-executions.txt")) shouldBe false
    Files.exists(scenario.root.resolve("sbt-command-lines.txt")) shouldBe false
  }
  private def _separate_commands(scenario: F.Scenario): Unit = {
    val pids = scenario.invocations.map(_.pid)
    pids.distinct.size shouldBe pids.size
    pids should not contain ProcessHandle.current().pid()
    scenario.invocations.foreach { invocation =>
      invocation.argv.head shouldBe Path.of("").toAbsolutePath.normalize.resolve("bin/launcher").toString
      invocation.argv should contain("command")
      invocation.argv should contain("org.goldenport.cncf.CncfMain")
      Files.isRegularFile(invocation.stdout) shouldBe true
      Files.isRegularFile(invocation.stderr) shouldBe true
    }
  }
  private def _implementation(scenario: F.Scenario, task: Path): V.GoalPhaseAdvanceResult = {
    val started = scenario.start().business[V.GoalPhaseStartResult]
    scenario.retain(task, started)
    val request = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    request.kind shouldBe "Request"
    request.sessionId shouldBe started.sessionId
    request.submitSessionId shouldBe Some(started.sessionId)
    request.submitOperation shouldBe Some(GoalPhaseNativeRun.completionOperation.operation)
    request.issuedWorkContext should not be empty
    val work = request.work.get
    work.kind shouldBe "ImplementStep"
    work.stepIdentity shouldBe Some("step-1")
    work.selectedPlan.identity shouldBe scenario.plan.identity
    work.selectedPlan.revision shouldBe scenario.plan.revision
    work.selectedPlan.phase shouldBe "1"
    work.selectedPlan.repository shouldBe "smwf-p62-cli-metadata"
    work.selectedPlan.steps.map(_.checks) shouldBe Vector(Vector("metadata-tests"))
    work.selectedPlan.steps.map(_.ownedPaths) shouldBe Vector(Vector("docs/phase.md"))
    work.selectedPlan.phaseChecks shouldBe Vector("metadata-tests")
    work.selectedPlan.repairLimit shouldBe 0
    work.originalBase.head should fullyMatch regex "[0-9a-f]{40}"
    work.originalBase.tree should fullyMatch regex "[0-9a-f]{40}"
    work.completed shouldBe empty
    work.review shouldBe None
    _no_sbt(scenario)
    request
  }
  private def _submit(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult): V.GoalPhaseWorkSubmissionResult = {
    Files.writeString(Path.of(scenario.host.target.root).resolve("docs/phase.md"),
      "# Owned native qualification candidate\n\nMetadata selected through the owning native UoW.\n")
    val semantic = V.GoalPhaseWorkResult("completed", "Specification-owned assigned docs/phase.md edit",
      Vector.empty, None, None, None, None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
    scenario.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--result", json(semantic.toRecord()).noSpaces,
      "--resultReference", json(_reference.toRecord()).noSpaces)).business[V.GoalPhaseWorkSubmissionResult]
  }
  private def _accepted(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult,
    result: V.GoalPhaseWorkSubmissionResult): Unit = {
    result.accepted shouldBe true
    result.outcome shouldBe "accepted"
    result.sessionId shouldBe request.sessionId
    result.message.trim should not be empty
    _no_sbt(scenario)
  }
  private def _candidate(scenario: F.Scenario, task: Path): Unit = {
    val request = _implementation(scenario, task)
    _accepted(scenario, request, _submit(scenario, request))
  }
  private def _truthful_retained_refusal(scenario: F.Scenario, task: Path, first: F.Result, code: String): Unit = {
    first.exitCode should not be 0
    first.invocation.logs should include(code)
    _no_sbt(scenario)
    val repeated = scenario.advance(task)
    if (repeated.exitCode == 0) {
      val retained = repeated.business[V.GoalPhaseAdvanceResult]
      retained.kind shouldBe "Wait"
      retained.completion shouldBe None
      retained.work shouldBe None
      retained.validation.foreach { validation =>
        validation.outcome should not be Some("passed")
        validation.checks.exists(_.sbt.exists(_.outcome == "succeeded")) shouldBe false
      }
    } else {
      repeated.invocation.logs.trim should not be empty
      repeated.invocation.logs should not include "\"kind\":\"ReviewStep\""
    }
    _no_sbt(scenario)
    _separate_commands(scenario)
  }

  "Normal GoalPhase metadata commands" should afterWord(
    "in spec:goal-phase-validation-metadata, example:P62-FIX-G2, rule:owning-UoW") {
    "execute the exact query and supplemental union once and retain its full pending review context" in {
      Given("an isolated ordinary host, literal metadata-tests check and real compiled three-registration ScalaTest artifact")
      val scenario = F.freshMetadata("goal-metadata-exact", TypedSbtHostPlanFixture.normalExecutable)
      val task = scenario.ownTask("metadata-exact-task")
      When("separate Start and Advance commands issue the original selected implementation")
      val implementation = _implementation(scenario, task)
      Then("the original literal check, Plan and base are retained without starting SBT")
      scenario.retrieve(task) shouldBe implementation.sessionId
      val original = implementation.work.get
      val profile = scenario.host.goal.stepValidation.checks.head.metadata.get
      scenario.host.goal.phaseValidation.checks shouldBe scenario.host.goal.stepValidation.checks
      profile.query.purposes shouldBe Set(M.Purpose.SMOKE, M.Purpose.FOCUSED)
      profile.query.features shouldBe Set(M.FeatureReference("component:sm-workflow-fixture", "feature-a"))

      Given("only the assigned guide edit and a specification-owned completed semantic result using the actual issued Submit route")
      When("a separate Submit consumes that semantic result and its explicit artifact reference")
      val submitted = _submit(scenario, implementation)
      Then("semantic completion is accepted with no test, provider, Human or review evidence supplied by the specification")
      _accepted(scenario, implementation, submitted)

      Given("the accepted docs candidate and exact host metadata profile with no execution granted by declarations")
      When("one explicit separate Advance discovers, saves selection and runs both exact TestOnly operations in its owning UoW")
      val review = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("pending independent ReviewStep contains actual successful typed receipts and the full original metadata context")
      review.kind shouldBe "Request"
      review.sessionId shouldBe implementation.sessionId
      review.completion shouldBe None
      val work = review.work.get
      work.kind shouldBe "ReviewStep"
      work.selectedPlan shouldBe original.selectedPlan
      work.originalBase shouldBe original.originalBase
      work.stepIdentity shouldBe original.stepIdentity
      work.completed shouldBe empty
      work.candidate.get.head shouldBe original.originalBase.head
      work.candidate.get.tree should not be original.originalBase.tree
      work.checksReceipt should not be empty
      val context = work.review.get
      context.changedPaths shouldBe Vector("docs/phase.md")
      context.diff should include("+Metadata selected through the owning native UoW.")
      context.metadata.size shouldBe 1
      val rawbatch = json(context.metadata.head)
      rawbatch.hcursor.get[String]("check_identity") shouldBe Right("metadata-tests")
      rawbatch.hcursor.downField("checkIdentity").focus shouldBe None
      val canonical = N.normalizeBatches(Json.fromValues(context.metadata.map(json))).fold(
        error => fail("Actual CLI validation metadata normalization failed: " + error), identity)
      val batches = canonical.as[Vector[N.Batch]].fold(
        error => fail("Canonical CLI validation metadata decoding failed: " + error.message), identity)
      batches.size shouldBe 1
      val batch = batches.head
      canonical shouldBe Vector(batch).asJson
      batch.asJson.as[N.Batch] shouldBe Right(batch)
      Json.fromValues(batches.map(value => json(N.record(value.asJson)))) shouldBe canonical
      batch.checkIdentity shouldBe "metadata-tests"
      batch.ordinal shouldBe 1
      batch.profile shouldBe profile
      batch.inventory.scope shouldBe profile.scope
      batch.inventory.completeness shouldBe D.Completeness.COMPLETE
      batch.inventory.diagnostics shouldBe empty
      batch.inventory.operations.size shouldBe 3
      batch.references.map(_.operation) shouldBe Vector("smoke-a", "focused-b")
      batch.profile.supplemental shouldBe Vector(D.OperationReference(
        "component:sm-workflow-fixture", "metadata-fixture", "focused-b"))
      val selected = take(D.selectC(batch.inventory, profile.query))
      selected.map(_.reference.operation) shouldBe Vector("smoke-a")
      val bound = take(N.bindC(batch))
      bound.map(_.operation.reference) shouldBe batch.references
      bound.foreach { operation =>
        operation.intent.target.workspaceId shouldBe take(WorkspaceId.parseC("sm-workflow-repository-validation-1"))
        operation.intent.target.scenario.get.suiteClassName shouldBe _suite
        operation.intent.target.scenario.get.testName shouldBe operation.operation.selector.testName
      }
      val focused = batch.inventory.operations.find(_.reference.operation == "focused-b").get
      focused.metadata.get.expectedDuration shouldBe Some(500.millis)
      focused.metadata.get.durationClass shouldBe M.DurationClass.NORMAL
      focused.metadata.get.executionRequirement shouldBe Some(ExecutionRequirement(
        Vector(CapabilityRequirement("metadata-fixture")), RiskLevel("local"), ReasoningLevel.Standard, reviewRequired = false))
      focused.metadata.get.origins shouldBe M.Origins(true, true)
      batch.inventory.operations.foreach { operation =>
        operation.provenance.artifactIdentity shouldBe "sm-workflow-fixture-tests"
        operation.provenance.artifactRevision shouldBe "artifact-1"
        operation.provenance.sourceRevision shouldBe "source-1"
        operation.selector.suiteClassName shouldBe _suite
      }
      val unannotated = batch.inventory.operations.find(_.selector.testName.endsWith(_unselected)).get
      unannotated.metadata.get.origins shouldBe M.Origins(true, false)
      batch.references should not contain unannotated.reference
      val identities = batch.references.map(N.logicalIdentity(batch.checkIdentity, _))
      identities.distinct.size shouldBe 2
      context.checks.map(_.identity) shouldBe identities
      context.checks.zip(bound).foreach { (receipt, operation) =>
        receipt.termination shouldBe "exited"
        receipt.exitCode shouldBe Some(0)
        val facts = receipt.sbt.get
        facts.kind shouldBe "test-only"
        facts.outcome shouldBe "succeeded"
        facts.reason shouldBe None
        val selection = facts.selection.get
        selection.testClasses shouldBe Vector(_suite)
        selection.completedSuites shouldBe 1
        selection.testsRun shouldBe 1L
        val selectedname = operation.operation.selector.testName.stripPrefix("metadata input should ")
        receipt.stdout should include(selectedname)
        receipt.stdout should not include _unselected
        bound.filterNot(_ == operation).foreach { other =>
          receipt.stdout should not include other.operation.selector.testName.stripPrefix("metadata input should ")
        }
      }
      val restored = take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(review.toRecord()))
      restored shouldBe review
      restored.work.get.review.get.metadata.map(json) shouldBe context.metadata.map(json)
      val pids = _executions(scenario)
      pids.size shouldBe 2
      pids.distinct.size shouldBe 2
      pids should not contain ProcessHandle.current().pid()
      pids.intersect(scenario.invocations.map(_.pid)) shouldBe empty
      val commands = _commands(scenario)
      val selector = "testOnly\\s+(\\S+)\\s+--\\s+-t\\s+\"([^\"]+)\"".r
      val actualselectors = commands.flatMap(line => selector.findAllMatchIn(line).map(m => (m.group(1), m.group(2))))
      actualselectors.size shouldBe 2
      actualselectors.toSet shouldBe bound.map(operation =>
        (operation.operation.selector.suiteClassName, operation.operation.selector.testName)).toSet

      Given("the actual independent ReviewStep remains pending without a review disposition or Step commit")
      When("two more ordinary separate Advance processes read that pending assignment")
      val repeated = Vector.fill(2)(scenario.advance(task).business[V.GoalPhaseAdvanceResult])
      Then("the same candidate, exact selection, receipts and issued review context remain without another SBT execution")
      repeated.foreach { retained =>
        retained shouldBe review
        retained.work.get.candidate shouldBe work.candidate
        retained.work.get.review shouldBe Some(context)
        retained.issuedWorkContext shouldBe review.issuedWorkContext
      }
      _executions(scenario) shouldBe pids
      _commands(scenario) shouldBe commands
      _separate_commands(scenario)
    }

    "refuse missing SBT capability before startup and retain truthful non-replay state" in {
      Given("an independent normal metadata fixture with only its SBT capability removed and all Git grants retained")
      val scenario = F.freshMetadata("goal-metadata-no-capability", TypedSbtHostPlanFixture.normalExecutable,
        F.MetadataDenial.MissingSbtCapability)
      val task = scenario.ownTask("metadata-no-capability-task")
      When("normal Start, assigned docs implementation and semantic Submit precede an explicit validation Advance")
      _candidate(scenario, task)
      val refused = scenario.advance(task)
      Then("process.execution.capability-grant refuses before SBT and repeated Advance cannot manufacture success or replay")
      _truthful_retained_refusal(scenario, task, refused, "process.execution.capability-grant")
    }

    "reject a process grant without workspace Manage during Start configuration before a Session exists" in {
      Given("an independent normal metadata fixture with only its SBT workspace Manage grant removed")
      val scenario = F.freshMetadata("goal-metadata-no-manage", TypedSbtHostPlanFixture.normalExecutable,
        F.MetadataDenial.MissingWorkspaceManage)
      When("a separate normal Start loads the unchanged inconsistent process and workspace grants")
      val refused = scenario.start()
      Then("bootstrap rejects the invalid runtime process declaration before a Session, Submit, Review or SBT execution")
      refused.exitCode should not be 0
      refused.invocation.logs should include("Invalid runtime process declaration")
      _no_sbt(scenario)
      scenario.invocations.filter(_.argv.exists(_.endsWith("." + GoalPhaseNativeRun.startOperation.operation))) shouldBe
        Vector(refused.invocation)
      scenario.invocations.exists(_.argv.exists(_.endsWith("." + GoalPhaseNativeRun.advanceOperation.operation))) shouldBe false
      scenario.invocations.exists(_.argv.exists(_.endsWith("." + GoalPhaseNativeRun.completionOperation.operation))) shouldBe false
      _separate_commands(scenario)
    }

    "refuse an unallowlisted suite before startup and retain truthful non-replay state" in {
      Given("an independent normal metadata fixture with a distinct valid suite allowlist and unchanged capability and workspace grants")
      val scenario = F.freshMetadata("goal-metadata-wrong-suite", TypedSbtHostPlanFixture.normalExecutable,
        F.MetadataDenial.WrongSuiteAllowlist)
      val task = scenario.ownTask("metadata-wrong-suite-task")
      When("normal Start, assigned docs implementation and semantic Submit precede an explicit validation Advance")
      _candidate(scenario, task)
      val refused = scenario.advance(task)
      Then("sbt.execution.scenario-allowlist refuses before SBT because selected metadata grants no capability or fallback")
      _truthful_retained_refusal(scenario, task, refused, "sbt.execution.scenario-allowlist")
    }
  }
}
