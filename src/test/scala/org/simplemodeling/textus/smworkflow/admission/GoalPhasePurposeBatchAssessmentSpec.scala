/**
 * @since   Nov. 19, 2026
 * @version Nov. 19, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.validation.{ValidationDiscoveryV1 as D, ValidationExecutionModeV1 as M}
import org.goldenport.cncf.workspace.WorkspaceId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take
import RepositorySyncNativeMergeValidation as V
import RepositorySyncProgramImpact as P
import WorkflowSbtValidation as S

/** Regression over native discovery and exact mode-batch constructors; no process is launched. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhasePurposeBatchAssessmentSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val tree = "b" * 40
  private val snapshot = GoalPhaseOrderedProgression.Snapshot("a" * 40, tree)
  private val policy = GoalPhaseStepAssessment.Policy("purpose-code", "code", "conditional", "off", "off", Vector.empty)
  private val selected = Vector(GoalPhaseNativeValidation.Check("ordinary-project-full", 1, S.Kind.TestOnly))
  private val structural = V.Receipt("index-local", "exited", Some(0), "", "")
  private val workspace = "purpose-slot-1"
  private val variant = SbtValidationMode.Case(SbtScenarioSelection("fixture.sessioncheck.SessionCheck", "candidate value"),
    "test", Some("fixture"))
  private val observation = M.Observation(variant.selector.suiteClassName, variant.selector.testName, "test", "fixture")
  private def capture(value: String): ProcessExecutionCapture = {
    val bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
    ProcessExecutionCapture(bytes, bytes.size.toLong, false)
  }
  private def process(stdout: String, termination: ProcessExecutionTermination = ProcessExecutionTermination.Exited(0)) =
    ProcessExecutionResult(termination, capture(stdout), capture(""), Vector.empty, 1L, "purpose-batch")
  private def batch(termination: ProcessExecutionTermination = ProcessExecutionTermination.Exited(0),
    status: String = "passed", observed: Option[M.Observation] = Some(observation)): P.BatchRecord = {
    val result = SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC(workspace)), validationBatch = Vector(variant)),
      SbtOperation.TestOnly, process(observed.map(_.line + "\n").getOrElse(""), termination),
      batch = Some(Vector(SbtValidationMode.CaseResult(variant, status, observed))))
    P.BatchRecord(Vector(variant), take(S.factsC(S.Kind.TestOnly, result)), result.processResult)
  }
  private def group(sourceTree: String = tree): P.Group = {
    val product = Path.of("/fixture/target/test-classes")
    val binding = SbtValidationDiscovery.Binding("fixture-product", "fixture:fixture-product", "0.0.1-SNAPSHOT", "fixture.sessioncheck")
    val build = P.BuildRecord(workspace, Some(binding), process(product.toString + "\n" + product.toString + "\n"))
    val scope = D.DiscoveryScope(binding.artifactIdentity, binding.artifactRevision,
      Vector(D.ArtifactScope(binding.componentIdentity, binding.artifactIdentity, binding.artifactRevision,
        sourceTree, product, binding.packagePrefix)))
    val operation = D.Operation(D.OperationReference("fixture-product", "SessionCheck", "candidate-value"),
      D.Provenance(binding.artifactIdentity, binding.artifactRevision, sourceTree, "1"),
      D.ScalaTestSelector(variant.selector.suiteClassName, variant.selector.testName), None, Vector.empty,
      Some(D.FrameworkFacts(Set.empty, false)))
    P.Group(sourceTree, workspace, Some(build), Some(D.Inventory(scope, D.Completeness.COMPLETE, Vector(operation), Vector.empty)),
      Vector(variant), Vector(Vector(variant)), Vector(batch()))
  }
  private def assess(value: P.Group) = GoalPhaseStepAssessment.assessPurposeC(policy, snapshot, "purpose-assessment",
    structural, selected, Vector(P.groupReceipt(selected.head.identity, value)))

  "Purpose ordinary batch assessment" should {
    "accept a nonempty reported static FULL Test only for ordinary Purpose selection" in {
      Given("an ordinary static Compile and Test batch with a complete ScalaTest report")
      val target = SbtTarget(take(WorkspaceId.parseC(workspace)))
      val checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, S.Kind.Compile),
        GoalPhaseNativeValidation.Check("test", 2, S.Kind.Test))
      val compile = take(V.sbtReceiptC("compile", S.Kind.Compile,
        SbtExecutionResult(target, SbtOperation.Compile, process(""))))
      val report = """[info] ProductCheckSpec:
        |[info] Total number of tests run: 1
        |[info] Suites: completed 1, aborted 0
        |[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0
        |[info] All tests passed.
        |""".stripMargin
      def _test_receipt_(observation: ProcessExecutionResult): V.Receipt =
        take(V.sbtReceiptC("test", S.Kind.Test, SbtExecutionResult(target, SbtOperation.Test, observation)))
      def _assess_(receipt: V.Receipt, ordinary: Boolean, lint: String = "off") =
        GoalPhaseStepAssessment.assessPurposeC(policy.copy(scalaLint = lint), snapshot, "ordinary-full",
          structural, checks, Vector(compile, receipt), ordinaryFull = ordinary)

      When("the same batch is evaluated in the ordinary FULL, focused and lint-required contexts")
      val passed = _test_receipt_(process(report))
      Then("only actual matching evidence satisfies ordinary FULL code assessment")
      take(_assess_(passed, true)).branch shouldBe "admit"
      _assess_(passed, false).isSuccess shouldBe false
      _assess_(passed, true, "required").isSuccess shouldBe false
      _assess_(passed.copy(processObservation = None), true).isSuccess shouldBe false

      When("zero-exit Test results lack a successful, nonempty, complete report")
      val unsupported = Vector("", report.replace("tests run: 1", "tests run: 0").replace("succeeded 1", "succeeded 0"),
        report.replace("aborted 0", "aborted 1"), report.replace("failed 0", "failed 1"),
        report.replace("canceled 0", "canceled 1"), report.replace("succeeded 1", "succeeded 2"))
      Then("none can satisfy Purpose assessment")
      unsupported.foreach(stdout => _assess_(_test_receipt_(process(stdout)), true).isSuccess shouldBe false)
    }

    "reuse complete passing reports from multiple projects without broadening focused acceptance" in {
      Given("a materialized Purpose with successful Compile and one retained FULL process")
      val target = SbtTarget(take(WorkspaceId.parseC(workspace)))
      val checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, S.Kind.Compile),
        GoalPhaseNativeValidation.Check("test", 2, S.Kind.Test))
      val compile = take(V.sbtReceiptC("compile", S.Kind.Compile,
        SbtExecutionResult(target, SbtOperation.Compile, process(""))))
      def _report_(suite: String, count: Int): String =
        s"""[info] $suite:
           |[info] Run completed in 1 second.
           |[info] Total number of tests run: $count
           |[info] Suites: completed 1, aborted 0
           |[info] Tests: succeeded $count, failed 0, canceled 0, ignored 0, pending 0
           |[info] All tests passed.
           |""".stripMargin
      val first = _report_("FirstProjectSpec", 1)
      val second = _report_("SecondProjectSpec", 2)
      def _receipt_(actual: ProcessExecutionResult): V.Receipt =
        take(V.sbtReceiptC("test", S.Kind.Test, SbtExecutionResult(target, SbtOperation.Test, actual)))
      def _assess_(actual: ProcessExecutionResult, ordinary: Boolean = true) =
        GoalPhaseStepAssessment.assessPurposeC(policy, snapshot, "ordinary-full", structural,
          checks, Vector(compile, _receipt_(actual)), ordinaryFull = ordinary)
      When("the ordinary Purpose assessment reads the complete retained reports")
      val complete = process(first + second)
      Then("both reports satisfy FULL while the focused gate still requires TestOnly")
      _assess_(complete).isSuccess shouldBe true
      _assess_(complete, ordinary = false).isSuccess shouldBe false
      WorkflowRepairAssessment.checksC("code", "off", checks, Vector(compile, _receipt_(complete))).isSuccess shouldBe false

      When("the second report or process fails to supply independent complete success")
      val invalid = Vector(
        process(first + first),
        process(first + second.replace("All tests passed.\n", "")),
        process(first + second.replace("canceled 0", "canceled 1")),
        process(first + second.replace("aborted 0", "aborted 1")),
        process(first + second.replace("failed 0", "failed 1")),
        process(first + _report_("EmptyProjectSpec", 0)),
        process(first + second, ProcessExecutionTermination.Exited(1)),
        complete.copy(termination = ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout),
          stdout = complete.stdout.copy(truncated = true)),
        complete.copy(termination = ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr),
          stderr = complete.stderr.copy(truncated = true)))
      Then("a passing prefix cannot make the batch eligible")
      invalid.foreach(actual => _assess_(actual).isSuccess shouldBe false)
    }

    "offer a fixed TestOnly only after a complete original batch with actual cancellations" in {
      Given("typed Compile and full Test receipts at the assessment boundary")
      val target = SbtTarget(take(WorkspaceId.parseC(workspace)))
      val checks = Vector(V.Check("compile", 1, S.Kind.Compile), V.Check("test", 2, S.Kind.Test))
      val compile = take(V.sbtReceiptC("compile", S.Kind.Compile,
        SbtExecutionResult(target, SbtOperation.Compile, process(""))))
      val report = """[info] ProductCheckSpec:
        |[info] Total number of tests run: 1
        |[info] Suites: completed 1, aborted 0
        |[info] Tests: succeeded 1, failed 0, canceled 1, ignored 0, pending 0
        |[info] All tests passed.
        |""".stripMargin
      def _receipt_(value: ProcessExecutionResult): V.Receipt =
        take(V.sbtReceiptC("test", S.Kind.Test, SbtExecutionResult(target, SbtOperation.Test, value)))
      val original = Vector(structural, compile, _receipt_(process(report)))
      When("the runtime evaluates whether the only missing result is the additional fixed check")
      val incomplete = process(report).copy(stdout = capture(report).copy(truncated = true))
      val invalid = Vector(process(""), process(report.replace("failed 0", "failed 1")),
        process(report.replace("aborted 0", "aborted 1")), process(report.replace("canceled 1", "canceled 0")),
        process(report, ProcessExecutionTermination.Exited(1)),
        process(report, ProcessExecutionTermination.TerminationUnconfirmed))
      val continuation = RepositorySyncPurposeTestOnlyContinuation
      Then("the real cancellation report qualifies and failed, unobserved and already-selected checks remain ineligible")
      continuation.prefixEligible(original, checks) shouldBe true
      invalid.foreach(value => continuation.prefixEligible(original.updated(2, _receipt_(value)), checks) shouldBe false)
      continuation.prefixEligible(original.updated(2, original.last.copy(processObservation = Some(incomplete))), checks) shouldBe false
      continuation.prefixEligible(original.dropRight(1), checks) shouldBe false
      continuation.prefixEligible(original, checks :+ V.Check("unfinished", 3, S.Kind.FixCheck)) shouldBe false
      continuation.prefixEligible(original :+ original.last, checks :+ V.Check("focused", 3, S.Kind.TestOnly)) shouldBe false
    }

    "admit the successful compiled discovery batch without legacy suite counters" in {
      Given("a complete native discovery build and one exact successful mode/provider batch")
      val actual = group()
      actual.build.get.available should not be empty
      actual.successful shouldBe true
      actual.batches.head.facts.selection shouldBe None
      actual.batches.head.facts.batch.get.map(_.status) shouldBe Vector("passed")
      When("the old legacy-counter predicate and the purpose admission boundary observe the same receipt")
      val legacy = actual.successful && actual.batches.exists(_.facts.selection.exists(s => s.completedSuites > 0 && s.testsRun > 0))
      val admitted = take(assess(actual))
      Then("the old predicate refuses this valid representation and the corrected boundary admits its real batch evidence")
      legacy shouldBe false
      admitted.candidate shouldBe snapshot
      admitted.branch shouldBe "admit"
      admitted.checks shouldBe selected.map(_.identity)
    }

    "refuse empty incomplete failed and unobserved batches even when a process exited zero" in {
      Given("the same selected discovery and exact batch with incomplete or negative observations")
      val actual = group()
      val noObservation = batch(status = "unverified", observed = None)
      val failed = batch(ProcessExecutionTermination.Exited(7), "failed", None)
      val incomplete = batch(ProcessExecutionTermination.TimedOut, "not-executed", None)
      val wrongObservation = actual.batches.head.copy(facts = actual.batches.head.facts.copy(
        batch = Some(Vector(SbtValidationMode.CaseResult(variant, "passed", Some(observation.copy(test = "foreign test")))))))
      val negatives = Vector(actual.copy(batches = Vector.empty), actual.copy(cases = Vector.empty, planned = Vector.empty, batches = Vector.empty),
        actual.copy(pending = Some("batch-0")), actual.copy(refusal = Some("discovery unavailable")),
        actual.copy(build = None), actual.copy(inventory = None),
        actual.copy(inventory = actual.inventory.map(_.copy(completeness = D.Completeness.PARTIAL))),
        actual.copy(batches = Vector(noObservation)),
        actual.copy(batches = Vector(failed)), actual.copy(batches = Vector(incomplete)),
        actual.copy(batches = Vector(wrongObservation)),
        actual.copy(planned = Vector(Vector(variant), Vector(variant)), cases = Vector(variant, variant)))
      When("each actual group is assessed without inventing legacy counters")
      Then("none becomes purpose acceptance, including the zero-exit unverified batch")
      noObservation.process.termination shouldBe ProcessExecutionTermination.Exited(0)
      negatives.foreach { value =>
        withClue(value.toString) {
          value.successful shouldBe false
          assess(value).isSuccess shouldBe false
        }
      }
      val zeroOnly = V.Receipt(selected.head.identity, "exited", Some(0), "", "")
      GoalPhaseStepAssessment.assessPurposeC(policy, snapshot, "zero-only", structural, selected, Vector(zeroOnly)).isSuccess shouldBe false
      GoalPhaseStepAssessment.assessPurposeC(policy.copy(scalaLint = "required"), snapshot, "missing-lint", structural,
        selected, Vector(P.groupReceipt(selected.head.identity, actual))).isSuccess shouldBe false
    }

    "bind a grouped receipt to this candidate or the exact currently qualified reuse evidence" in {
      Given("a successful group for another tree and one retained input-equivalent reuse reference")
      val foreign = P.groupReceipt(selected.head.identity, group("c" * 40))
      val check = V.Check(selected.head.identity, 1, S.Kind.TestOnly)
      val reference = P.Reuse(check.identity, "prior-validation", "prior-closure", None)
      val retained = P.Selection("FULL", "Ordinary selected validation", Vector(check), Vector(reference))
      val qualified = retained -> Map(check.identity -> foreign)
      When("the actual Purpose runtime receipt-binding predicate checks current owner-qualified evidence")
      Then("a foreign tree alone refuses, exact qualified reuse passes, and identity alone never authenticates altered evidence")
      foreign.group.get.successful shouldBe true
      RepositorySyncPurposeRuntime.groupReceiptBound(foreign, tree, retained, retained.copy(reused = Vector.empty) -> Map.empty) shouldBe false
      RepositorySyncPurposeRuntime.groupReceiptBound(foreign, tree, retained, qualified) shouldBe true
      RepositorySyncPurposeRuntime.groupReceiptBound(foreign, tree, retained.copy(reused = Vector.empty), qualified) shouldBe false
      RepositorySyncPurposeRuntime.groupReceiptBound(foreign, tree, retained,
        retained.copy(reused = Vector(reference.copy(validation = "foreign-validation"))) -> qualified._2) shouldBe false
      val altered = foreign.copy(group = Some(foreign.group.get.copy(workspace = "another-workspace",
        build = foreign.group.get.build.map(_.copy(workspace = "another-workspace")))))
      altered.group.get.successful shouldBe true
      RepositorySyncPurposeRuntime.groupReceiptBound(altered, tree, retained, qualified) shouldBe false
      val local = P.groupReceipt(check.identity, group())
      RepositorySyncPurposeRuntime.groupReceiptBound(local, tree, retained.copy(reused = Vector.empty),
        retained.copy(reused = Vector.empty) -> Map.empty) shouldBe true
    }
  }
}
