package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import WorkflowSbtValidation.*
import RepositorySyncNativeMergeValidation as V
import SplitPhaseExplicitPartitionFixture.take

/** Typed-result projection and admission, without launching a build.
  * @since   Oct. 15, 2026
  *  version Oct. 25, 2026
  * @version Nov. 26, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class WorkflowSbtValidationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private final class AdmissionReporterFixture extends AnyWordSpec {
    "fixture" should { "pass" in { assert(true) } }
  }
  private val _classes = Vector("example.FirstSpec", "example.SecondSpec")
  private def _capture(text: String): ProcessExecutionCapture = {
    val bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
    ProcessExecutionCapture(bytes, bytes.size.toLong, false)
  }
  private def _result(operation: SbtOperation, termination: ProcessExecutionTermination = ProcessExecutionTermination.Exited(0),
    selection: Option[SbtTestSelectionResult] = None): SbtExecutionResult = SbtExecutionResult(
    SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-1"))), operation,
    ProcessExecutionResult(termination, _capture("actual output\r\n日本語\n"), _capture("actual diagnostic\n"),
      Vector.empty, 7L, "selected-sbt"), selection)

  "Actual typed SBT receipts" should {
    "emit Admission identities only for genuine ScalaTest success events" in {
      val fixture = new AdmissionReporterFixture
      val output = new java.io.ByteArrayOutputStream()
      val reporter = new org.simplemodeling.textus.smworkflow.testing.AdmissionSuccessReporter
      Console.withOut(new java.io.PrintStream(output, true, "UTF-8")) {
        fixture.run(Some("fixture should pass"), org.scalatest.Args(reporter)).waitUntilCompleted()
      }
      def token(value: String): String = java.util.Base64.getUrlEncoder.withoutPadding()
        .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      output.toString("UTF-8").linesIterator.toVector shouldBe Vector(
        "CNCF-ADMISSION-V1 succeeded " + token(fixture.getClass.getName) + ":" + token("fixture should pass"))
    }
    "retain and recheck the selected Admission scope without reusing legacy Full receipts" in {
      import GoalPhaseNativeValidation.Check
      Given("an actual-shaped producer report for one selected ScalaTest case")
      def token(value: String): String = java.util.Base64.getUrlEncoder.withoutPadding()
        .encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      val suite = "org.example.SelectedSpec"
      val test = "fixture case"
      val row = token(suite) + ":" + token(test)
      val output = Vector("CNCF-ADMISSION-V1 selected " + row,
        "CNCF-ADMISSION-V1 succeeded " + row, "[info] org.example.SelectedSpec:",
        "[info] Total number of tests run: 1", "[info] Suites: completed 1, aborted 0",
        "[info] Tests: succeeded 1, failed 0, canceled 0, ignored 0, pending 0",
        "[info] All tests passed.", "CNCF-ADMISSION-V1 completed").mkString("\n")
      val raw = _result(SbtOperation.Test)
      val process = raw.processResult.copy(stdout = _capture(output), stderr = _capture(""))
      val actual = raw.copy(target = raw.target.copy(testScope = SbtTestScope.Admission), processResult = process)
      val receipt = take(V.sbtReceiptC("test-admission", Kind.Test, actual))
      val selected = Check("test-admission", 1, Kind.Test, testScope = Some("admission"))
      val full = Check("test-admission", 1, Kind.Test)
      When("the result is persisted and compared to the selected check")
      val retained = receipt.asJson.as[V.Receipt].fold(throw _, identity)
      Then("Admission preserves exact cases and cannot satisfy or reuse Full")
      retained.sbt.map(_.testScope) shouldBe Some(Some("admission"))
      retained.sbt.map(_.admissionCases) shouldBe Some(Some(Vector(AdmissionCase(suite, test))))
      GoalPhaseNativeValidation.successful(Vector(retained), Vector(selected)) shouldBe true
      GoalPhaseNativeValidation.successful(Vector(retained), Vector(full)) shouldBe false
      val legacy = take(V.sbtReceiptC("test-admission", Kind.Test, raw))
      GoalPhaseNativeValidation.successful(Vector(legacy), Vector(selected)) shouldBe false
      val wrongOutput = output.replace("CNCF-ADMISSION-V1 succeeded " + row,
        "CNCF-ADMISSION-V1 succeeded " + token("org.example.OtherSpec") + ":" + token(test))
      val forged = retained.copy(stdout = wrongOutput,
        processObservation = Some(process.copy(stdout = _capture(wrongOutput))))
      GoalPhaseNativeValidation.successful(Vector(forged), Vector(selected)) shouldBe false
      val unavailable = actual.copy(processResult = process.copy(stdout = _capture("CNCF-ADMISSION-V1 unavailable")))
      val failed = take(V.sbtReceiptC("test-admission", Kind.Test, unavailable))
      failed.sbt.map(_.outcome) shouldBe Some(Outcome.Unverified)
      GoalPhaseNativeValidation.successful(Vector(failed), Vector(selected)) shouldBe false
      full.asJson.hcursor.downField("testScope").succeeded shouldBe false
      full.asJson.as[Check].map(_.effectiveTestScope) shouldBe Right(SbtTestScope.Full)
      selected.asJson.as[Check] shouldBe Right(selected)
      val checks = take(StandardWorkflowRuntime.standardChecksC(Vector("compile", "test-admission", "test-only")))
      checks(1).effectiveTestScope shouldBe SbtTestScope.Admission
      take(StandardWorkflowRuntime.standardChecksC(Vector("test"))).head.effectiveTestScope shouldBe SbtTestScope.Full
    }
    "satisfy empty Admission only after Compile and keep independent focused obligations" in {
      import GoalPhaseNativeValidation.Check
      val raw = _result(SbtOperation.Test)
      val admission = raw.copy(target = raw.target.copy(testScope = SbtTestScope.Admission))
      def observed(text: String, terminal: ProcessExecutionTermination = ProcessExecutionTermination.Exited(0)) =
        take(V.sbtReceiptC("test-admission", Kind.Test, admission.copy(processResult =
          admission.processResult.copy(stdout = _capture(text), termination = terminal))))
      val empty = observed("[info] CNCF-ADMISSION-V1 empty")
      val compile = take(V.sbtReceiptC("compile", Kind.Compile, _result(SbtOperation.Compile)))
      val badCompile = take(V.sbtReceiptC("compile", Kind.Compile,
        _result(SbtOperation.Compile, ProcessExecutionTermination.Exited(1))))
      val focused = take(V.sbtReceiptC("focused", Kind.TestOnly, _result(SbtOperation.TestOnly,
        selection = Some(SbtTestSelectionResult.Rejected(SbtTestSelectionFailure.ZeroTests)))))
      val compileCheck = Check("compile", 1, Kind.Compile)
      val admissionCheck = Check("test-admission", 2, Kind.Test, testScope = Some("admission"))
      val focusedCheck = Check("focused", 3, Kind.TestOnly)
      val selected = Vector(compileCheck, admissionCheck, focusedCheck)

      empty.sbt.map(_.outcome) shouldBe Some(Outcome.AdmissionEmpty)
      V.productSuccessful(empty, Kind.Test) shouldBe false
      GoalPhaseNativeValidation.successful(Vector(compile, empty), selected) shouldBe true
      GoalPhaseNativeValidation.successful(Vector(empty), Vector(admissionCheck)) shouldBe false
      GoalPhaseNativeValidation.successful(Vector(badCompile, empty), selected) shouldBe false
      GoalPhaseNativeValidation.successful(Vector(compile, empty, focused), selected) shouldBe false
      GoalPhaseNativeValidation.successful(Vector(compile, empty),
        Vector(compileCheck, admissionCheck.copy(testScope = None))) shouldBe false
      Vector(
        observed("[error] CNCF-ADMISSION-V1 unavailable"),
        observed("[error] CNCF-ADMISSION-V1 empty"),
        observed("no producer observation"),
        observed("[info] CNCF-ADMISSION-V1 empty\n[info] CNCF-ADMISSION-V1 completed"),
        observed("[info] CNCF-ADMISSION-V1 empty\n[error] CNCF-ADMISSION-V1 completed"),
        observed("[error] CNCF-ADMISSION-V1 empty", ProcessExecutionTermination.Exited(1))
      ).foreach { receipt =>
        receipt.sbt.map(_.outcome) should not be Some(Outcome.AdmissionEmpty)
        GoalPhaseNativeValidation.successful(Vector(compile, receipt), selected) shouldBe false
      }
      val mergeChecks = Vector(V.Check("compile", 1, Kind.Compile),
        V.Check("test-admission", 2, Kind.Test, testScope = Some("admission")), V.Check("focused", 3, Kind.TestOnly))
      val local = V.Receipt("index-local", "exited", Some(0), "", "")
      val remote = V.Receipt("index-remote", "exited", Some(0), "", "")
      V.successful(Vector(local, remote, compile, empty), selected = mergeChecks) shouldBe true
      V.successful(Vector(local, remote, empty), selected = mergeChecks.tail) shouldBe false
      V.successful(Vector(local, remote, compile, empty, focused), selected = mergeChecks) shouldBe false
    }
    "retain admitted immutable lint input and reject echoed, missing or crossed provenance" in {
      Given("a selected typed FixCheck result and its actual producer-admitted input")
      val input = SbtLintInput("shared-scala-lint", "1", "{\"rules\":[\"NoOptionGet\"]}")
      val raw = _result(SbtOperation.FixCheck)
      val actual = raw.copy(target = raw.target.copy(lintInput = Some(input)), lintInput = Some(input))
      val receipt = take(V.sbtReceiptC("lint", Kind.FixCheck, actual))
      receipt.sbt.get.lintInput shouldBe Some(input)
      val retained = receipt.asJson.as[V.Receipt].toOption.get
      retained shouldBe receipt
      retained.sbt.get.lintInput shouldBe Some(input)
      When("only a target echo is supplied, or actual provenance crosses the requested input")
      factsC(Kind.FixCheck, actual.copy(lintInput = None)).isSuccess shouldBe false
      factsC(Kind.FixCheck, actual.copy(lintInput = Some(input.copy(revision = "2")))).isSuccess shouldBe false
      factsC(Kind.FixCheck, raw.copy(lintInput = Some(input))).isSuccess shouldBe false
      factsC(Kind.Compile, actual.copy(operation = SbtOperation.Compile)).isSuccess shouldBe false
      Facts(Kind.FixCheck, Outcome.Succeeded, None, None, lintInput = Some(input.copy(configJson = "[]"))).valid shouldBe false
      Facts(Kind.FixCheck, Outcome.Succeeded, None, None, lintInput = Some(input.copy(configJson = "{\"rules\":[],\"rules\":[]}"))).valid shouldBe false
      Then("old receipts remain unchanged and gain no invented input")
      val old = Facts(Kind.FixCheck, Outcome.Succeeded, None, None)
      old.asJson.hcursor.downField("lintInput").succeeded shouldBe false
      old.asJson.as[Facts].toOption.get shouldBe old
      retained.sbt.get.lintInput shouldBe Some(input)
    }
    "retain TestCompile DiscoveryUnavailable as unverified zero exit without broadening other preparatory facts" in {
      Given("a typed discovery request with an actual-shaped zero-exit unavailable observation, used only as a negative fixture")
      val original = _result(SbtOperation.TestCompile)
      val unavailable = original.copy(target = original.target.copy(validationDiscovery = true),
        discovery = Some(SbtValidationDiscovery.Result.Unavailable("negative fixture: missing exports")))
      When("the receipt boundary projects the owning CNCF unavailable result")
      val receipt = take(V.sbtReceiptC("qualification-build", Kind.TestCompile, unavailable))
      Then("the original process survives without a successful product or selected test claim")
      receipt.exitCode shouldBe Some(0)
      receipt.processObservation shouldBe Some(original.processResult)
      receipt.sbt shouldBe Some(Facts(Kind.TestCompile, Outcome.Unverified, None, Some("DiscoveryUnavailable")))
      receipt.asJson.as[V.Receipt] shouldBe Right(receipt)
      V.productSuccessful(receipt, Kind.TestCompile) shouldBe false
      Vector(Kind.Compile, Kind.Test, Kind.FixCheck).foreach(kind =>
        Facts(kind, Outcome.Unverified, None, Some("DiscoveryUnavailable")).valid shouldBe false)
      Facts(Kind.TestCompile, Outcome.Unverified, None, Some("MissingSelection")).valid shouldBe false
      Facts(Kind.TestCompile, Outcome.Unverified, Some(Selection(_classes, 2, 3L)), Some("DiscoveryUnavailable")).valid shouldBe false
    }

    "retain only estimate field presence as nonsemantic wire provenance" in {
      Given("historical omitted and explicit-null estimates and a populated valid estimate")
      val fresh = Facts(Kind.TestOnly, Outcome.Incomplete, None, None)
      val absent = fresh.asJson
      val explicit = absent.mapObject(_.add("estimate", Json.Null))
      val estimate = SbtValidationEstimate("changed leaf", "candidate-tree",
        Vector(SbtValidationEstimate.Leaf("fixture.ExampleSpec", "selected", 1000000000L, true)),
        SbtValidationEstimate.Preparation(1000000000L, 0L, 0L, 1000000000L), BigDecimal("1.5"))
      val populated = fresh.copy(estimate = Some(estimate)).asJson
      When("each representation is decoded and copied")
      val decoded = Vector(absent, explicit, populated).map(_.as[Facts].toOption.get)
      Then("the exact representation survives while equivalent business facts retain equality and hash equality")
      decoded.map(_.asJson) shouldBe Vector(absent, explicit, populated)
      decoded.map(_.copy().asJson) shouldBe Vector(absent, explicit, populated)
      decoded(0) shouldBe decoded(1)
      decoded(0).hashCode shouldBe decoded(1).hashCode
      decoded(2).estimate shouldBe Some(estimate)
      decoded.foreach(_.validateC.isSuccess shouldBe true)
      Facts(Kind.Compile, Outcome.Incomplete, None, None, Some(estimate)).validateC.isSuccess shouldBe false
    }
    "preserve B2 mode and batch business facts independently of historical estimate presence" in {
      Given("source-shaped mode and exact batch facts with omitted or explicit-null estimates")
      val mode = Mode("develop", Some("develop"), Some("fixture"), None)
      val selected = SbtValidationMode.Case(SbtScenarioSelection("fixture.ExampleSpec", "selected"), "develop")
      val observation = org.goldenport.cncf.validation.ValidationExecutionModeV1.Observation(
        "fixture.ExampleSpec", "selected", "develop", "fixture")
      val batch = Vector(SbtValidationMode.CaseResult(selected, "passed", Some(observation)))
      val modern = Vector(Facts(Kind.TestOnly, Outcome.Incomplete, None, None, mode = Some(mode)),
        Facts(Kind.TestOnly, Outcome.Succeeded, None, None, batch = Some(batch)))
      When("the merged codec roundtrips both historical estimate representations")
      modern.foreach { facts =>
        val absent = facts.asJson
        val explicit = absent.mapObject(_.add("estimate", Json.Null))
        val restored = Vector(absent, explicit).map(_.as[Facts].toOption.get)
        Then("wire provenance stays exact without dropping mode or batch fields from semantic identity")
        restored.map(_.copy().asJson) shouldBe Vector(absent, explicit)
        restored.foreach { value =>
          value.mode shouldBe facts.mode
          value.batch shouldBe facts.batch
          value.validateC.isSuccess shouldBe true
        }
        restored.head shouldBe restored.last
        restored.head.hashCode shouldBe restored.last.hashCode
      }
      modern.head should not be modern.head.copy(mode = None)
      val other = batch.head.copy(selected = selected.copy(selector = selected.selector.copy(testName = "other")),
        observation = Some(observation.copy(test = "other")))
      modern.last should not be modern.last.copy(batch = Some(Vector(other)))
    }

    "retain confirmed nonexecution for every selected kind without a product result or repair" in {
      Given("actual-shaped typed NotStarted results for Compile, Test and TestOnly with no process captures")
      val kinds = Vector(Kind.Compile -> SbtOperation.Compile, Kind.Test -> SbtOperation.Test,
        Kind.TestOnly -> SbtOperation.TestOnly)
      val results = kinds.map { (kind, operation) =>
        val result = _result(operation, ProcessExecutionTermination.NotStarted)
        kind -> result.copy(processResult = result.processResult.copy(stdout = _capture(""), stderr = _capture(""), elapsedMillis = 0L))
      }
      When("the typed result boundary retains each actual stopped receipt and its canonical wire facts")
      val receipts = results.map { (kind, result) => take(V.sbtReceiptC("selected", kind, result)) }
      Then("not-executed stays distinct from failed or incomplete and cannot pass or allocate a semantic repair")
      receipts.zip(kinds).foreach { (receipt, selected) =>
        receipt.termination shouldBe "not-started"
        receipt.exitCode shouldBe None
        receipt.stdout shouldBe ""
        receipt.stderr shouldBe ""
        receipt.sbt shouldBe Some(Facts(selected._1, Outcome.NotExecuted, None, None))
        receipt.asJson.as[V.Receipt] shouldBe Right(receipt)
        receipt.sbt.get.asJson.hcursor.get[String]("outcome").toOption shouldBe Some("not-executed")
        V.productSuccessful(receipt, selected._1) shouldBe false
        val payload = GoalPhaseNativeValidation.Payload("1", "S1", GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40),
          Vector(GoalPhaseNativeValidation.Check("selected", 1, selected._1)), Vector.empty, None, stopped = Vector(receipt))
        GoalPhaseNativeValidation.repairable(payload) shouldBe false
      }
    }

    "keep launch failure cancellation timeout and unconfirmed termination incomplete for every selected kind" in {
      Given("actual-shaped LaunchFailed, Cancelled, TimedOut and TerminationUnconfirmed typed results for each selected SBT operation")
      val kinds = Vector(Kind.Compile -> SbtOperation.Compile, Kind.Test -> SbtOperation.Test,
        Kind.TestOnly -> SbtOperation.TestOnly)
      When("native receipts project those actual uncertain terminations")
      val receipts = kinds.flatMap { (kind, operation) =>
        Vector(ProcessExecutionTermination.LaunchFailed -> "launch-failed", ProcessExecutionTermination.Cancelled -> "cancelled",
          ProcessExecutionTermination.TimedOut -> "timed-out", ProcessExecutionTermination.TerminationUnconfirmed -> "termination-unconfirmed")
          .map { (termination, expected) => take(V.sbtReceiptC("selected", kind, _result(operation, termination))) -> expected }
      }
      Then("each retains its actual termination and captured text without success or confirmed nonexecution")
      receipts.foreach { (receipt, expected) =>
        receipt.termination shouldBe expected
        receipt.exitCode shouldBe None
        receipt.sbt.get.outcome shouldBe Outcome.Incomplete
        receipt.stdout shouldBe "actual output\r\n日本語\n"
        receipt.stderr shouldBe "actual diagnostic\n"
        V.productSuccessful(receipt, receipt.sbt.get.kind) shouldBe false
      }
    }

    "preserve actual Compile success failure and timeout through the canonical receipt codec" in {
      Given("actual typed Compile results with zero, nonzero and timed-out termination")
      val results = Vector(_result(SbtOperation.Compile), _result(SbtOperation.Compile, ProcessExecutionTermination.Exited(7)),
        _result(SbtOperation.Compile, ProcessExecutionTermination.TimedOut))
      When("the native boundary projects each Compile result and its receipt is encoded and decoded")
      val receipts = results.map(result => take(V.sbtReceiptC("compile", Kind.Compile, result)))
      val restored = receipts.map(_.asJson.as[V.Receipt].toOption.get)
      Then("only successful Compile passes its selected kind and all raw evidence and negative outcomes survive")
      restored shouldBe receipts
      restored.map(V.productSuccessful(_, Kind.Compile)) shouldBe Vector(true, false, false)
      restored.map(_.sbt.get.outcome) shouldBe Vector(Outcome.Succeeded, Outcome.Failed, Outcome.Incomplete)
      restored.map(_.termination) shouldBe Vector("exited", "exited", "timed-out")
      restored.map(_.exitCode) shouldBe Vector(Some(0), Some(7), None)
      restored.foreach { receipt =>
        receipt.sbt.get.kind shouldBe Kind.Compile
        receipt.sbt.get.selection shouldBe None
        receipt.sbt.get.reason shouldBe None
        receipt.stdout shouldBe "actual output\r\n日本語\n"
        receipt.stderr shouldBe "actual diagnostic\n"
        V.productSuccessful(receipt, Kind.Test) shouldBe false
        V.productSuccessful(receipt, Kind.TestOnly) shouldBe false
      }
    }

    "reject every crossed selected operation and legacy Test receipt for Compile" in {
      Given("one actual successful result for each of Compile, Test and verified TestOnly")
      val results = Vector(Kind.Compile -> _result(SbtOperation.Compile), Kind.Test -> _result(SbtOperation.Test),
        Kind.TestOnly -> _result(SbtOperation.TestOnly,
          selection = Some(SbtTestSelectionResult.Verified(_classes, 2, 9L))))
      val raw = take(V.receiptC("selected", _result(SbtOperation.Test).processResult))
      When("each selected kind consumes each actual operation and the canonical product gate consumes its receipt")
      val projected = results.map { case (actualkind, result) =>
        (actualkind, results.map { case (selectedkind, _) =>
          (selectedkind, V.sbtReceiptC("selected", selectedkind, result)) })
      }
      val receipts = results.map { case (actualkind, result) =>
        actualkind -> take(V.sbtReceiptC("selected", actualkind, result))
      }
      Then("only exact operation matches are admitted and no pair or historical absence can cross kinds")
      projected.foreach { case (actualkind, selected) => selected.foreach { case (selectedkind, receipt) =>
        receipt.isSuccess shouldBe (selectedkind == actualkind)
      }}
      receipts.foreach { case (actualkind, receipt) =>
        results.foreach { case (selectedkind, _) =>
          V.productSuccessful(receipt, selectedkind) shouldBe (selectedkind == actualkind)
        }
      }
      V.productSuccessful(raw, Kind.Test) shouldBe true
      V.productSuccessful(raw, Kind.Compile) shouldBe false
    }

    "reject Compile selection and unverified facts before admission or persistence" in {
      Given("Compile results carrying TestOnly selection and constructed Compile facts with selection reason or unverified outcome")
      val selections = Vector(SbtTestSelectionResult.Verified(_classes, 2, 9L),
        SbtTestSelectionResult.Rejected(SbtTestSelectionFailure.MissingSelection))
      val invalid = Vector(Facts(Kind.Compile, Outcome.Unverified, None, Some("MissingSelection")),
        Facts(Kind.Compile, Outcome.Succeeded, Some(Selection(_classes, 2, 9L)), None),
        Facts(Kind.Compile, Outcome.Succeeded, None, Some("MissingSelection")),
        Facts(Kind.Compile, Outcome.Unverified, None, None))
      When("the actual-result boundary and canonical facts decoder consume those contradictory values")
      val receipts = selections.map(selection => V.sbtReceiptC("compile", Kind.Compile,
        _result(SbtOperation.Compile, selection = Some(selection))))
      val decoded = invalid.map(_.asJson.as[Facts])
      Then("Compile admits neither actual selected-test metadata nor persisted selection reason or unverified facts")
      receipts.foreach(_.isSuccess shouldBe false)
      invalid.foreach(_.validateC.isSuccess shouldBe false)
      decoded.foreach(_.isLeft shouldBe true)
    }

    "preserve Test success failure and incomplete output meaning" in {
      Given("actual Test results with zero, nonzero and timed-out termination")
      val results = Vector(_result(SbtOperation.Test), _result(SbtOperation.Test, ProcessExecutionTermination.Exited(7)),
        _result(SbtOperation.Test, ProcessExecutionTermination.TimedOut))
      When("the native receipt consumes the typed result and its exact raw capture")
      val receipts = results.map(result => take(V.sbtReceiptC("test", Kind.Test, result)))
      Then("only the zero Test passes and raw text and exit semantics survive")
      receipts.map(V.productSuccessful(_)) shouldBe Vector(true, false, false)
      receipts.map(_.sbt.get.outcome) shouldBe Vector(Outcome.Succeeded, Outcome.Failed, Outcome.Incomplete)
      receipts.foreach { receipt =>
        receipt.stdout shouldBe "actual output\r\n日本語\n"
        receipt.stderr shouldBe "actual diagnostic\n"
        receipt.sbt.get.selection shouldBe None
        receipt.sbt.get.reason shouldBe None
      }
      receipts.map(_.exitCode) shouldBe Vector(Some(0), Some(7), None)
    }

    "require verified nonzero exact TestOnly selection facts for success" in {
      Given("a zero TestOnly result whose CNCF selection names two completed suites and nine actual tests")
      val result = _result(SbtOperation.TestOnly, selection = Some(SbtTestSelectionResult.Verified(_classes, 2, 9L)))
      When("the actual result is converted and roundtripped through the canonical receipt codec")
      val receipt = take(V.sbtReceiptC("focused", Kind.TestOnly, result))
      val restored = receipt.asJson.as[V.Receipt].toOption.get
      Then("the selected kind, exact counts and classes pass only the matching product gate")
      restored shouldBe receipt
      restored.sbt shouldBe Some(Facts(Kind.TestOnly, Outcome.Succeeded, Some(Selection(_classes, 2, 9L)), None))
      V.productSuccessful(restored, Kind.TestOnly) shouldBe true
      V.productSuccessful(restored, Kind.Test) shouldBe false
      restored.stdout shouldBe "actual output\r\n日本語\n"
      restored.stderr shouldBe "actual diagnostic\n"
    }

    "retain raw zero with absent or any rejected selection as durable negative facts" in {
      Given("zero TestOnly results with absent selection and every actual CNCF selection refusal reason")
      val selections = Vector(None) ++ SbtTestSelectionFailure.values.toVector.map(reason => Some(SbtTestSelectionResult.Rejected(reason)))
      When("native receipts preserve these actual results without interpreting stdout again")
      val receipts = selections.map(selection => take(V.sbtReceiptC("focused", Kind.TestOnly,
        _result(SbtOperation.TestOnly, selection = selection))))
      Then("all raw exits stay zero while rejected selection remains unverified and cannot pass")
      receipts.foreach { receipt =>
        receipt.exitCode shouldBe Some(0)
        receipt.sbt.get.outcome shouldBe Outcome.Unverified
        receipt.sbt.get.selection shouldBe None
        V.productSuccessful(receipt, Kind.TestOnly) shouldBe false
        receipt.asJson.as[V.Receipt] shouldBe Right(receipt)
      }
      receipts.map(_.sbt.get.reason.get) shouldBe Vector("MissingSelection") ++ SbtTestSelectionFailure.values.toVector.map(_.toString)
    }

    "refuse zero-exit failure without the producer's confirmed TestOnly selection reason" in {
      Given("a complete zero-exit observation and invented failure facts without a confirmed selected-test reason")
      val process = _result(SbtOperation.TestOnly).processResult
      val invalid = Vector(
        Facts(Kind.TestOnly, Outcome.Failed, None, None),
        Facts(Kind.TestOnly, Outcome.Failed, None, Some("InvalidReport")),
        Facts(Kind.TestOnly, Outcome.Failed, None, Some("ModeObservationMismatch")),
        Facts(Kind.Compile, Outcome.Failed, None, Some("FailedTests")),
        Facts(Kind.Test, Outcome.Failed, None, Some("FailedTests")))
      When("the retained receipt checks actual process termination against typed facts")
      val admitted = invalid.map(WorkflowSbtCapture.valid(process, _))
      Then("exit zero alone cannot certify a formal failure and compile or Test cannot borrow TestOnly evidence")
      admitted shouldBe Vector.fill(invalid.size)(false)
    }

    "refuse TestOnly success after failure timeout cancellation or output limit" in {
      Given("actual TestOnly negative and incomplete process terminations")
      val terminations = Vector(ProcessExecutionTermination.Exited(1), ProcessExecutionTermination.TimedOut,
        ProcessExecutionTermination.Cancelled, ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout))
      When("bounded captures with the actual output-limit truncation are retained as typed negative receipts")
      val receipts = terminations.map { termination =>
        val result = _result(SbtOperation.TestOnly, termination)
        val observed = if (termination == ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout))
          result.copy(processResult = result.processResult.copy(stdout = result.processResult.stdout.copy(truncated = true)))
        else result
        take(V.sbtReceiptC("focused", Kind.TestOnly, observed))
      }
      Then("none passes and failed or incomplete facts carry no positive selection or reason")
      receipts.map(_.sbt.get.outcome) shouldBe Vector(Outcome.Failed, Outcome.Incomplete, Outcome.Incomplete, Outcome.Incomplete)
      receipts.foreach { receipt =>
        V.productSuccessful(receipt, Kind.TestOnly) shouldBe false
        receipt.sbt.get.selection shouldBe None
        receipt.sbt.get.reason shouldBe None
      }
    }

    "reject incompatible operation malformed selections and incomplete captures before admission" in {
      Given("mismatched operations and invalid constructed actual selection facts")
      val invalid = Vector(SbtTestSelectionResult.Verified(_classes, 1, 9L),
        SbtTestSelectionResult.Verified(_classes, 2, 0L), SbtTestSelectionResult.Verified(Vector("not-a-class"), 1, 1L),
        SbtTestSelectionResult.Verified(Vector("a.Same", "b.Same"), 2, 1L),
        SbtTestSelectionResult.Verified(Vector.fill(17)("a.Spec"), 17, 1L),
        SbtTestSelectionResult.Verified(Vector("a." + "S" * 255), 1, 1L))
      val result = _result(SbtOperation.TestOnly)
      When("the typed native boundary consumes the actual operation and complete capture")
      val malformed = invalid.map(selection => V.sbtReceiptC("focused", Kind.TestOnly, result.copy(testSelection = Some(selection))))
      Then("no malformed fact, incompatible operation, null kind or incomplete raw capture is admitted")
      malformed.foreach(_.isSuccess shouldBe false)
      V.sbtReceiptC("focused", Kind.Test, result).isSuccess shouldBe false
      V.sbtReceiptC("focused", Kind.TestOnly, _result(SbtOperation.Compile)).isSuccess shouldBe false
      V.sbtReceiptC("focused", null, result).isSuccess shouldBe false
      V.sbtReceiptC("focused", Kind.TestOnly, result.copy(processResult = result.processResult.copy(
        stdout = result.processResult.stdout.copy(truncated = true)))).isSuccess shouldBe false
    }

    "validate constructed and persisted facts and never promote a raw receipt to TestOnly" in {
      Given("raw historical Test receipt and contradictory constructed typed facts")
      val raw = take(V.receiptC("focused", _result(SbtOperation.TestOnly).processResult))
      val invalid = Vector(Facts(Kind.TestOnly, Outcome.Succeeded, None, None),
        Facts(Kind.Test, Outcome.Unverified, None, Some("MissingSelection")),
        Facts(Kind.TestOnly, Outcome.Unverified, None, Some("unknown")),
        Facts(Kind.TestOnly, Outcome.Failed, Some(Selection(_classes, 2, 1L)), None))
      When("the pure product gate and canonical facts decoder consume those values")
      val accepted = invalid.map(facts => V.productSuccessful(raw.copy(sbt = Some(facts)), Kind.TestOnly))
      val valid = Facts(Kind.TestOnly, Outcome.Succeeded, Some(Selection(_classes, 2, 1L)), None).asJson
      Then("raw Test compatibility stays explicit and invalid constructed or numeric facts fail")
      V.productSuccessful(raw) shouldBe true
      V.productSuccessful(raw, Kind.TestOnly) shouldBe false
      accepted shouldBe Vector.fill(invalid.size)(false)
      invalid.foreach { facts =>
        facts.validateC.isSuccess shouldBe false
        facts.asJson.as[Facts].isLeft shouldBe true
      }
      valid.as[Facts] shouldBe Right(Facts(Kind.TestOnly, Outcome.Succeeded, Some(Selection(_classes, 2, 1L)), None))
      Vector(Json.fromLong(-1), Json.fromString("1"), Json.fromBigDecimal(BigDecimal("9223372036854775808")),
        Json.fromBoolean(true), Json.Null, Json.fromBigDecimal(BigDecimal("1.5"))).foreach { number =>
        valid.mapObject(_.add("selection", valid.hcursor.downField("selection").focus.get.mapObject(
          _.add("testsRun", number)))).as[Facts].isLeft shouldBe true
      }
      Vector(Json.fromInt(-1), Json.fromString("2"), Json.fromLong(2147483648L),
        Json.fromBoolean(true), Json.Null, Json.fromBigDecimal(BigDecimal("1.5"))).foreach { number =>
        valid.mapObject(_.add("selection", valid.hcursor.downField("selection").focus.get.mapObject(
          _.add("completedSuites", number)))).as[Facts].isLeft shouldBe true
      }
    }
  }
}
