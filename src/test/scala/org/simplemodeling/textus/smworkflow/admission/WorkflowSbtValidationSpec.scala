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
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
final class WorkflowSbtValidationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
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
      restored.map(_.termination) shouldBe Vector("exited", "exited", "indeterminate")
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
      Given("zero TestOnly results with absent selection and each of the six actual CNCF refusal reasons")
      val selections = Vector(None) ++ SbtTestSelectionFailure.values.toVector.map(reason => Some(SbtTestSelectionResult.Rejected(reason)))
      When("native receipts preserve these actual results without interpreting stdout again")
      val receipts = selections.map(selection => take(V.sbtReceiptC("focused", Kind.TestOnly,
        _result(SbtOperation.TestOnly, selection = selection))))
      Then("all raw exits stay zero while every typed outcome stays unverified and cannot pass")
      receipts.foreach { receipt =>
        receipt.exitCode shouldBe Some(0)
        receipt.sbt.get.outcome shouldBe Outcome.Unverified
        receipt.sbt.get.selection shouldBe None
        V.productSuccessful(receipt, Kind.TestOnly) shouldBe false
        receipt.asJson.as[V.Receipt] shouldBe Right(receipt)
      }
      receipts.map(_.sbt.get.reason.get) shouldBe Vector("MissingSelection") ++ SbtTestSelectionFailure.values.toVector.map(_.toString)
    }

    "refuse TestOnly success after failure timeout cancellation or output limit" in {
      Given("actual TestOnly negative and incomplete process terminations")
      val terminations = Vector(ProcessExecutionTermination.Exited(1), ProcessExecutionTermination.TimedOut,
        ProcessExecutionTermination.Cancelled, ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout))
      When("complete bounded captures are retained as typed negative receipts")
      val receipts = terminations.map(termination => take(V.sbtReceiptC("focused", Kind.TestOnly,
        _result(SbtOperation.TestOnly, termination))))
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
