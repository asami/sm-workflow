package org.simplemodeling.textus.smworkflow.admission

import io.circe.syntax.*
import org.goldenport.cncf.processexecution.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import IncrementalSbtFailureDiagnosis as D
import RepositorySyncNativeMergeValidation as V
import WorkflowSbtValidation as S

/** Retained captures exercise the production projection/association, without
  * executing SBT to reconstruct an old failure or fabricating live adoption.
  * @since   Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class IncrementalSbtFailureDiagnosisSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _candidate = GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40)
  private def _capture(text: String, truncated: Boolean = false) = {
    val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
    ProcessExecutionCapture(bytes, bytes.size, truncated)
  }
  private def _receipt(out: String, err: String = "", kind: S.Kind = S.Kind.TestOnly): V.Receipt = {
    val p = ProcessExecutionResult(ProcessExecutionTermination.Exited(1), _capture(out), _capture(err),
      Vector.empty, 12, "retained-failed-check")
    V.Receipt("focused", "exited", Some(1), out, err, Some(S.Facts(kind, S.Outcome.Failed, None, None)), Some(p))
  }
  private def _payload(receipt: V.Receipt) = GoalPhaseNativeValidation.Payload("27", "D1", _candidate,
    Vector(GoalPhaseNativeValidation.Check("focused", 1, receipt.sbt.get.kind)), Vector(receipt), Some("repair-required"))
  private def _view(receipt: V.Receipt): D.View = D.selected(_payload(receipt)).head

  "Retained SBT diagnostic projection" should {
    "expose assertion and cause context while preserving the original failed receipt" in {
      Given("one released failure with explicit values and a cause/stack on stderr")
      val out = "[info] EvidenceSpec:\n[info] - retains identity *** FAILED ***\n[info] Expected: saved\n[info] Actual: lost\n"
      val err = "[info] RuntimeSuite *** ABORTED ***\n[info] Caused by: java.lang.IllegalStateException: missing\n[info]   at example.Reader.load(Reader.scala:42)\n"
      val receipt = _receipt(out, err)
      val original = receipt.asJson
      When("the production public validation projection reads that same receipt")
      val view = _view(receipt)
      val publicview = NativeWorkflowOperationOutput.goalValidation(_payload(receipt))
      Then("values, causes and source locators remain facts and no test count or repair is invented")
      view.check shouldBe "focused"
      view.sourceRevision shouldBe "27"
      view.candidate shouldBe _candidate
      view.groups.map(_.kind) shouldBe Vector("test-failure", "suite-abort")
      view.groups.head.occurrences.head.assertion shouldBe Some(D.Assertion("saved", "lost"))
      view.groups.head.occurrences.head.firstLine shouldBe 2
      view.groups.last.occurrences.head.causes should contain("Caused by: java.lang.IllegalStateException: missing")
      view.groups.last.occurrences.head.stack.mkString should include("Reader.scala:42")
      view.testExecution.testsRun shouldBe None
      view.testExecution.failedTests shouldBe None
      publicview.diagnostics.size shouldBe 1
      publicview.checks.head.stdout shouldBe out
      receipt.asJson shouldBe original
      GoalPhaseNativeValidation.codec.decode(GoalPhaseNativeValidation.codec.encode(_payload(receipt))) shouldBe Right(_payload(receipt))
    }

    "distinguish production and test compilation from failed or canceled tests" in {
      for (configuration <- Vector("Compile", "Test")) {
        Given("an attributable " + configuration + " compilation task failure with type/source context")
        val out = "[error] -- [E007] Type Mismatch Error: /repo/src/Example.scala:8:3\n" +
          "[error] 8 | missingValue\n[error] Found: String\n[error] Required: Int\n" +
          "[error] (app / " + configuration + " / compileIncremental) Compilation failed\n"
        When("the retained failed TestOnly is diagnosed")
        val view = _view(_receipt(out))
        Then("only the affected compilation task prevents execution; no zero/PASS/failure test counts are fabricated")
        view.groups.map(_.kind) shouldBe Vector("compiler-error", "compilation-failure")
        view.groups.head.occurrences.head.location shouldBe Some(D.Location("/repo/src/Example.scala", 8, Some(3)))
        view.groups.head.occurrences.head.context should include("Required: Int")
        view.groups.last.occurrences.head.compilerProject shouldBe Some("app")
        view.groups.last.occurrences.head.compilerConfiguration shouldBe Some(configuration)
        view.testExecution.status shouldBe "not-executed-because-compilation-failed"
        view.testExecution.scope should include("other-project-execution-unknown")
        view.testExecution.testsRun shouldBe None
        view.testExecution.failedTests shouldBe None
      }
    }

    "retain actual executed results in a mixed aggregate and leave ambiguous summaries unknown" in {
      Given("executed tests from one project and compilation failure from another")
      val executed = "[info] Total number of tests run: 3\n[info] Tests: succeeded 2, failed 1, canceled 0, ignored 0, pending 0\n"
      val failed = "[error] (other / Test / compileIncremental) Compilation failed\n"
      When("both observations occur in the same invocation")
      val mixed = _view(_receipt(executed + failed))
      Then("the observed executed tests remain present alongside the failed compilation task")
      mixed.testExecution.status shouldBe "executed-tests-observed"
      mixed.testExecution.testsRun shouldBe Some(3)
      mixed.testExecution.failedTests shouldBe Some(1)
      mixed.groups.head.occurrences.head.compilerProject shouldBe Some("other")
      When("multiple unattributable aggregate summaries appear")
      val ambiguous = _view(_receipt(executed + executed + failed))
      Then("the extractor neither sums nor deduplicates possible repeated/independent executions")
      ambiguous.testExecution.testsRun shouldBe None
      ambiguous.testExecution.failedTests shouldBe None
      ambiguous.testExecution.status shouldBe "executed-tests-observed"
      ambiguous.testExecution.compilationBlocked shouldBe Vector(D.BlockedSelection(Some("other"), "Test"))
      ambiguous.testExecution.scope should include("other-project-execution-unknown")
    }

    "keep an exit code, warning and unrecognized setup output from inventing test failures" in {
      Given("a nonzero exit containing only a compiler warning and setup text")
      val receipt = _receipt("[warn] /repo/Warn.scala:1:2: unused\n[error] project loading failed\n")
      When("no supported diagnostic boundary can be attributed")
      val view = _view(receipt)
      Then("the nonpassing outcome is retained with unknown execution and unmatched material still available")
      view.groups shouldBe empty
      view.coverage shouldBe "no-recognized-diagnostic"
      view.exitCode shouldBe Some(1)
      view.testExecution.status shouldBe "unknown"
      view.sources.forall(_.fullDetail == "retained-check-capture") shouldBe true
    }

    "group only exact rendered occurrences without claiming common causes" in {
      Given("two identical rendered failures with a third distinct failure boundary")
      val repeated = "[info] - same *** FAILED ***\n[info] 1 did not equal 2\n"
      When("the view groups the identical text")
      val view = _view(_receipt(repeated + repeated + "[info] - other *** FAILED ***\n"))
      Then("both occurrence locators survive and the grouping remains explicitly noncausal")
      val group = view.groups.find(_.occurrenceCount == 2).get
      group.grouping shouldBe "exact-text-not-causal"
      group.occurrences.map(_.firstLine) shouldBe Vector(1, 3)
      group.occurrences.forall(_.assertion.isEmpty) shouldBe true
    }

    "separate display omissions, truncated source and undecodable source" in {
      Given("a retained output-limited prefix and more diagnostics than the initial display policy")
      val out = (1 to 26).map(n => "[info] - case " + n + " *** FAILED ***\n").mkString
      val process = ProcessExecutionResult(ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout),
        _capture(out, true), ProcessExecutionCapture(Vector(0xff.toByte), 1, true), Vector.empty, 12, "limited-check")
      val receipt = V.Receipt("focused", "output-limit-exceeded", None, out, "",
        Some(S.Facts(S.Kind.TestOnly, S.Outcome.Incomplete, None, None)), Some(process))
      When("the same retained prefix is projected and selected for Common Evidence")
      val view = _view(receipt)
      val selected = GoalPhaseNativeEvidence.diagnosticSelections(_payload(receipt), "session", "repo", "6.6")
      Then("display omission has a count, lost bytes remain lost, and undecodable bytes are not empty successful evidence")
      view.omittedGroups shouldBe 2
      view.sources.head.textStatus shouldBe "prefix"
      view.sources.head.sourceLoss shouldBe "truncated"
      view.sources.last.textStatus shouldBe "unavailable"
      selected.map(_.source.output) shouldBe Vector("stdout-prefix")
      selected.head.source.reference.identity shouldBe "session:ordered-validation:27:check-result-0"
      receipt.sbt.get.outcome shouldBe S.Outcome.Incomplete
      receipt.processObservation.get.stdout.content shouldBe process.stdout.content
    }

    "reuse completed Test stdout identity and archive failed Compile stderr separately" in {
      Given("a completed failed Compile with actual stdout/stderr captures")
      val compile = _receipt("[error] /repo/A.scala:2: wrong type\n", "compiler context", S.Kind.Compile)
      When("diagnostic archival selects available outputs")
      val selected = GoalPhaseNativeEvidence.diagnosticSelections(_payload(compile), "session", "repo", "6.6")
      Then("each stream has one ordinary Common Evidence source while historical Test selection keeps its exact identity")
      selected.map(_.source.output) shouldBe Vector("stdout", "stderr")
      selected.map(_.source).forall(CommonEvidence.validSource) shouldBe true
      selected.map(s => CommonEvidence.identity(s.source)).distinct.size shouldBe 2
      val test = compile.copy(sbt = Some(S.Facts(S.Kind.TestOnly, S.Outcome.Failed, None, None)))
      val old = GoalPhaseNativeEvidence.selectedAll(_payload(test), "session", "repo", "6.6").head
      GoalPhaseNativeEvidence.diagnosticSelections(_payload(test), "session", "repo", "6.6").head shouldBe old
    }
  }
}
