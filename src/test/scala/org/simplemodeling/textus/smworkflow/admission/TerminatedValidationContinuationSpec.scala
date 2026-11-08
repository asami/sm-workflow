package org.simplemodeling.textus.smworkflow.admission

import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import StandaloneFixExecution as S
import StandaloneFixExecution.given
import WorkflowSbtValidation as V
import RepositorySyncNativeMergeValidation as B

/** Fixture observations, not installed execution or acceptance evidence.
  * @since   Nov.  6, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
private[admission] object TerminatedValidationFixture {
  def receipt(check: GoalPhaseNativeValidation.Check,
    termination: ProcessExecutionTermination = ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout)): B.Receipt = {
    val bytes = "fixture retained prefix".getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
    val out = ProcessExecutionCapture(bytes, bytes.size.toLong,
      termination == ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout))
    val err = ProcessExecutionCapture(Vector.empty, 0L,
      termination == ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr))
    val process = ProcessExecutionResult(termination, out, err, Vector.empty, 1L, "fixture-sbt")
    B.Receipt(check.identity, WorkflowSbtCapture.termination(process), WorkflowSbtCapture.exitCode(process),
      WorkflowSbtCapture.text(out).value, WorkflowSbtCapture.text(err).value,
      Some(V.Facts(check.kind, V.Outcome.Incomplete, None, None)), Some(process))
  }
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class TerminatedValidationContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail("Fixture admission failed: " + other)
  }
  private def _waiting(prefix: Int, task: Boolean): S.State = {
    val checks = Vector.tabulate(prefix + 1)(i => GoalPhaseNativeValidation.Check("check-" + i, i + 1, V.Kind.Compile))
    val input = S.Input("repo", "request", "Preserve selected behavior", Vector("src"), checks.map(_.identity),
      Vector("behavior"), Vector(ContextReference("source", "1")), task,
      task = Option.when(task)(S.Task("direct-instruction", Vector.empty)))
    val started = _take(S.startC(input, "participant", checks)).copy(stage = "Validate",
      candidate = Some(S.Candidate("a" * 40, "b" * 40, Vector("src/value.scala"), "fixture delta")))
    val successful = checks.dropRight(1).map(check => B.Receipt(check.identity, "exited", Some(0), "", "",
      Some(V.Facts(check.kind, V.Outcome.Succeeded, None, None))))
    _take(S.observedC(_take(S.intentC(started.copy(receipts = successful))), TerminatedValidationFixture.receipt(checks.last)))
  }

  "Explicit selected-validation continuation" should {
    "distinguish an ended unverified test from process uncertainty and failed assertions" in {
      Given("an actual-shaped specification-owned exited-zero canceled-test report")
      val state = SelectedValidationFixture.state
      When("the common owner classifies it without changing its old result")
      val resumed = _take(S.continueUnverifiedValidationC(state))
      Then("the successful prefix and limits remain while only the affected check becomes runnable")
      TerminatedValidationContinuation.unverified(state) shouldBe true
      TerminatedValidationContinuation.eligible(state) shouldBe false
      resumed.receipts shouldBe state.receipts.dropRight(1)
      resumed.selected shouldBe state.selected
      resumed.candidate shouldBe state.candidate
      resumed.ordinaryCount shouldBe 0
      resumed.automaticCount shouldBe 0
      state.receipts.last.sbt.get.outcome shouldBe V.Outcome.Unverified
      TerminatedValidationContinuation.unverified(state.copy(receipts = state.receipts.dropRight(1) :+
        SelectedValidationFixture.receipt(failed = 1))) shouldBe false
      val last = state.receipts.last
      val unknown = last.copy(processObservation = last.processObservation.map(_.copy(
        termination = ProcessExecutionTermination.TerminationUnconfirmed)))
      TerminatedValidationContinuation.unverified(state.copy(receipts = state.receipts.dropRight(1) :+ unknown)) shouldBe false
      TerminatedValidationContinuation.unverified(state.copy(receipts = state.receipts.dropRight(1) :+
        last.copy(processObservation = None))) shouldBe false
    }
  }

  "Confirmed terminated validation continuation" should {
    "retain source and all policy data while selecting only the affected check over shrinking prefixes and counters" in {
      Given("Task and Fix fixture histories with zero or more successful checks before an output-limit capture")
      val property = Prop.forAll(Gen.choose(0, 8), Gen.choose(0, 9), Gen.oneOf(true, false)) { (prefix, count, task) =>
        val waiting = _waiting(prefix, task).copy(automaticCount = count, ordinaryCount = count,
          evidenceReferences = Vector(ContextReference("retained-evidence", "3")),
          priorReviewCandidate = Some(S.Candidate("c" * 40, "d" * 40, Vector("src/value.scala"), "prior")))
        val retained = waiting.asJson.as[S.State].fold(error => fail(error.message), identity)
        val resumed = _take(S.continueTerminatedValidationC(retained))
        val intent = _take(S.intentC(resumed))
        resumed.copy(stage = waiting.stage, receipts = waiting.receipts, message = waiting.message) == waiting &&
          resumed.receipts == waiting.receipts.dropRight(1) && intent.pendingCheck.contains(waiting.selected.last.identity) &&
          retained.receipts.last == waiting.receipts.last && !TerminatedValidationContinuation.eligible(resumed) &&
          !TerminatedValidationContinuation.eligible(waiting, 1)
      }
      When("ordinary continuation computes the fresh intent without changing the retained checkpoint")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("only the affected check is selected and counters, source, history and attribution are unchanged")
      checked.passed shouldBe true
    }

    "require valid typed confirmed terminal evidence rather than a termination string or incomplete fact alone" in {
      Given("a retained Wait fixture and the finite native termination vocabulary")
      val waiting = _waiting(1, true)
      val terminal = Vector(ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout),
        ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr), ProcessExecutionTermination.TimedOut,
        ProcessExecutionTermination.Cancelled, ProcessExecutionTermination.ArtifactLimitExceeded)
      val unsupported = Vector(ProcessExecutionTermination.TerminationUnconfirmed, ProcessExecutionTermination.NotStarted,
        ProcessExecutionTermination.LaunchFailed, ProcessExecutionTermination.Exited(0), ProcessExecutionTermination.Exited(1))
      When("the eligibility owner reads the exact original capture and facts")
      val eligible = (terminal ++ unsupported).map(t => TerminatedValidationContinuation.eligible(
        waiting.copy(receipts = waiting.receipts.dropRight(1) :+ TerminatedValidationFixture.receipt(waiting.selected.last, t))))
      Then("confirmed terminal incomplete captures qualify and unknown, nonstarted and exited alternatives do not")
      eligible shouldBe Vector.fill(terminal.size)(true) ++ Vector.fill(unsupported.size)(false)
      val raw = waiting.receipts.last
      val process = raw.processObservation.get
      Vector(raw.copy(processObservation = None), raw.copy(stdout = "forged text"), raw.copy(identity = "other"),
        raw.copy(processObservation = Some(process.copy(stdout = process.stdout.copy(byteCount = 0L)))))
        .foreach(r => TerminatedValidationContinuation.eligible(waiting.copy(receipts = waiting.receipts.dropRight(1) :+ r)) shouldBe false)
      Vector("ValidationIntent", "AdmissionBlocked", "Repair", "Assess").foreach(stage =>
        TerminatedValidationContinuation.eligible(waiting.copy(stage = stage)) shouldBe false)
      TerminatedValidationContinuation.eligible(waiting.copy(pendingCheck = Some("unresolved"))) shouldBe false
      TerminatedValidationContinuation.eligible(waiting.copy(candidate = None)) shouldBe false
      val failedprefix = waiting.receipts.head.copy(exitCode = Some(1), sbt = Some(V.Facts(V.Kind.Compile, V.Outcome.Failed, None, None)))
      TerminatedValidationContinuation.eligible(waiting.copy(receipts = Vector(failedprefix, raw))) shouldBe false
    }

    "decode historical Wait payloads without granting missing captures execution permission" in {
      Given("a legacy Wait with typed facts but no original process observation")
      val waiting = _waiting(0, false)
      val legacy = waiting.copy(receipts = waiting.receipts.map(_.copy(processObservation = None)))
      When("the unchanged stored state codec reads the historical payload")
      val restored = legacy.asJson.as[S.State]
      Then("old state remains readable and continuation remains refused")
      restored shouldBe Right(legacy)
      S.continueTerminatedValidationC(legacy).isSuccess shouldBe false
    }
  }
}

/** Specification-owned terminated observations; never a real check receipt. */
private[admission] object SelectedValidationFixture {
  val checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, V.Kind.Compile),
    GoalPhaseNativeValidation.Check("focused", 2, V.Kind.TestOnly),
    GoalPhaseNativeValidation.Check("test", 3, V.Kind.Test))
  def receipt(failed: Int = 0): B.Receipt = {
    val text = s"[info] FixtureSpec:\n[info] - explicit stage !!! CANCELED !!!\n" +
      s"[info] Total number of tests run: 3\n[info] Suites: completed 1, aborted 0\n" +
      s"[info] Tests: succeeded ${3-failed}, failed $failed, canceled 1, ignored 0, pending 0\n"
    val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
    val process = ProcessExecutionResult(ProcessExecutionTermination.Exited(0),
      ProcessExecutionCapture(bytes, bytes.size.toLong, false), ProcessExecutionCapture(Vector.empty, 0L, false),
      Vector.empty, 1L, "fixture-sbt")
    B.Receipt("focused", "exited", Some(0), text, "",
      Some(V.Facts(V.Kind.TestOnly, V.Outcome.Unverified, None, Some("FailedTests"))), Some(process))
  }
  def state: S.State = {
    val input = S.Input("fixture", "selected-continuation", "Retain ordinary behavior", Vector("src"),
      checks.map(_.identity), Vector("behavior"), Vector(ContextReference("specification", "1")), true,
      task = Some(S.Task("direct-instruction", Vector.empty)))
    val initial = S.startC(input, "participant", checks).toOption.get
    initial.copy(stage = "Wait", candidate = Some(S.Candidate("a"*40, "b"*40, Vector("src/Value.scala"), "fixture")),
      semanticReference = Some(ContextReference("specification-implementation", "1")),
      receipts = Vector(B.Receipt("compile", "exited", Some(0), "", "", Some(V.Facts(V.Kind.Compile, V.Outcome.Succeeded, None, None))), receipt()))
  }
}
