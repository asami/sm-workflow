package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.processexecution.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take
import RepositorySyncNativeMergeValidation as V
import WorkflowSbtValidation.*

/** Byte preservation controls use shaped actual CNCF values, without execution.
  * @since   Oct. 24, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class WorkflowSbtCaptureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _capture(bytes: Vector[Byte], truncated: Boolean = false) = ProcessExecutionCapture(bytes, bytes.size.toLong, truncated)
  private val _empty = _capture(Vector.empty)
  private val _facts = Facts(Kind.Test, Outcome.Incomplete, None, None)
  private def _raw(bytes: Vector[Byte], terminal: ProcessExecutionTermination =
    ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr)) =
    ProcessExecutionResult(terminal, _empty, _capture(bytes, true), Vector.empty, 100L, "selected-sbt")

  "Raw SBT capture persistence" should {
    "retain split UTF-8 tails and distinguish prefix from unavailable interior text without replacement" in {
      Given("a split multibyte tail and malformed interior or NUL in an actual-shaped truncated stderr")
      val bytes = Vector(0x61.toByte, 0xe2.toByte, 0x82.toByte)
      val malformed = Vector(Vector(0xc3.toByte, 0x28.toByte), Vector(0.toByte))
      When("the exact raw process values roundtrip and diagnostic text is derived")
      val raw = _raw(bytes)
      val reopened = WorkflowSbtCapture.decode(WorkflowSbtCapture.encode(raw))
      val text = WorkflowSbtCapture.text(raw.stderr)
      Then("raw bytes remain exact and the two undecoded trailing bytes are disclosed")
      reopened shouldBe Right(raw)
      text shouldBe WorkflowSbtCapture.Text("a", "prefix", 2)
      WorkflowSbtCapture.valid(raw, _facts) shouldBe true
      malformed.foreach { bytes =>
        WorkflowSbtCapture.valid(_raw(bytes), _facts) shouldBe true
        WorkflowSbtCapture.text(_raw(bytes).stderr).textStatus shouldBe "unavailable"
      }
    }
    "reject noncanonical Base64 counts unknown fields artifacts and contradictory complete-success observations" in {
      Given("a closed canonical raw process and independently corrupted fields")
      val raw = _raw(Vector(1.toByte, 2.toByte))
      val wire = WorkflowSbtCapture.encode(raw)
      val stderr = wire.hcursor.downField("stderr").focus.get
      val corrupt = Vector(
        wire.mapObject(_.add("extra", Json.True)),
        wire.mapObject(_.add("stderr", stderr.mapObject(_.add("contentBase64", Json.fromString("AQI"))))),
        wire.mapObject(_.add("stderr", stderr.mapObject(_.add("byteCount", Json.fromInt(3))))),
        wire.mapObject(_.add("artifacts", Json.arr(Json.obj("name" -> Json.fromString("unexpected"))))))
      When("closed decoding and actual-outcome admission inspect every corruption")
      val decoded = corrupt.map(WorkflowSbtCapture.decode)
      Then("no failed new reader falls back and truncation cannot become successful or an exit 143")
      decoded.foreach(_.isLeft shouldBe true)
      WorkflowSbtCapture.valid(raw, Facts(Kind.Test, Outcome.Succeeded, None, None)) shouldBe false
      WorkflowSbtCapture.valid(raw.copy(termination = ProcessExecutionTermination.Exited(143)), _facts) shouldBe false
      WorkflowSbtCapture.exitCode(raw) shouldBe None
    }
    "retain genuine unconfirmed truncation and every actual termination without assigning a confirmed output-limit source" in {
      Given("actual-shaped unconfirmed truncation and parameterless CNCF termination variants")
      val unconfirmed = _raw(Vector(0xe2.toByte), ProcessExecutionTermination.TerminationUnconfirmed)
      val variants = Vector(ProcessExecutionTermination.NotStarted, ProcessExecutionTermination.LaunchFailed,
        ProcessExecutionTermination.TimedOut, ProcessExecutionTermination.Cancelled,
        ProcessExecutionTermination.TerminationUnconfirmed, ProcessExecutionTermination.ArtifactLimitExceeded,
        ProcessExecutionTermination.Exited(7), ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout))
      When("raw codecs reopen those typed variants")
      val decoded = variants.map(t => { val value = unconfirmed.copy(termination = t); WorkflowSbtCapture.decode(WorkflowSbtCapture.encode(value)) -> value })
      Then("the full typed termination remains exact and unconfirmed never acquires an exit or affected stream")
      decoded.foreach((result, value) => result shouldBe Right(value))
      WorkflowSbtCapture.valid(unconfirmed, _facts) shouldBe true
      WorkflowSbtCapture.termination(unconfirmed) shouldBe "termination-unconfirmed"
      WorkflowSbtCapture.exitCode(unconfirmed) shouldBe None
    }
    "dispatch legacy and raw nested receipts explicitly and preserve both enclosing native codecs" in {
      Given("a six-argument historical receipt and a byte-preserving new incomplete receipt")
      val old = V.Receipt("whole", "exited", Some(0), "old output", "")
      val process = _raw(Vector(0xe2.toByte, 0x82.toByte))
      val fresh = V.Receipt("whole", "output-limit-exceeded", None, "", "", Some(_facts), Some(process))
      val check = GoalPhaseNativeValidation.Check("whole", 1)
      val payload = GoalPhaseNativeValidation.Payload("8", "phase:phase", GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40),
        Vector(check), Vector(fresh), Some("incomplete"))
      val state = V.State("required", "fixed", Vector(V.Check("whole", 1)), "8", Vector(fresh))
      When("both generations and both enclosing codecs reopen their exact wires")
      val oldwire = old.asJson
      val newwire = fresh.asJson
      Then("legacy encoding is unchanged, raw bytes appear once and incomplete facts never pass")
      oldwire.as[V.Receipt] shouldBe Right(old)
      newwire.as[V.Receipt] shouldBe Right(fresh)
      newwire.hcursor.downField("stdout").focus shouldBe None
      GoalPhaseNativeValidation.codec.decode(GoalPhaseNativeValidation.codec.encode(payload)) shouldBe Right(payload)
      V.stateCodec.decode(V.stateCodec.encode(state)) shouldBe Right(state)
      V.productSuccessful(fresh) shouldBe false
      newwire.mapObject(_.add("schemaVersion", "unknown".asJson)).as[V.Receipt].isLeft shouldBe true
    }
    "encode the actual forty-MiB capture with representative 2162-event current and next native histories below the existing64MiB maximum" in {
      Given("the actual native definition, normal mechanical envelope and finite32MiB stdout/8MiB stderr retained prefixes")
      import org.goldenport.cncf.workflow.*
      import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
      import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
      val definition = take(GoalPhaseRuntimeCatalog.loadC()).definition
      val initial = InstanceRecord(InstanceIdentity("capacity-session"), definition, initialRevision,
        Lifecycle.NotStarted, None, CorrelationReference("capacity-correlation"), CausationReference("start"),
        None, None, None, Vector.empty)
      val current = (1 to 2161).foldLeft(initial) { (prior, index) =>
        take(prior.appendC(prior.revision, GoalPhaseNativeRun.history(prior, "phase.PhaseValidation", "retained-native-event:" + index)))
      }
      val process = ProcessExecutionResult(ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr),
        _capture(Vector.fill(33554432)('o'.toByte), true), _capture(Vector.fill(8388608)('e'.toByte), true),
        Vector.empty, 2665964L, "selected-sbt")
      val receipt = V.Receipt("whole", "output-limit-exceeded", None, "o" * 33554432, "e" * 8388608, Some(_facts), Some(process))
      val payload = GoalPhaseNativeValidation.Payload("2161", "phase:phase", GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40),
        Vector(GoalPhaseNativeValidation.Check("whole", 8)), Vector(receipt), None)
      val mechanical = WorkflowMechanicalTransition.Receipt(
        WorkflowMechanicalTransition.Reference("capacity-session:ordered-validation:2161:check-result-0", "1"),
        org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId,
        GoalPhaseNativeRun.advanceOperation, current,
        take(current.appendC(current.revision, GoalPhaseNativeRun.history(current, "phase.PhaseValidation", "ordered-validation:check-result-0"))),
        TypedValue(GoalPhaseNativeValidation.codec.typeIdentity, payload))
      When("the real existing CNCF encoder serializes current, next and the entire raw payload at its explicit hard maximum")
      val limits = take(WorkflowMechanicalReceiptLimits.createC(67108864))
      val encoded = take(WorkflowMechanicalTransitionJsonV1.encodeC(mechanical, GoalPhaseNativeValidation.codec, limits))
      Then("the full retained history and one Base64 copy of each capture fit without dropping bytes or raising the maximum")
      mechanical.current.history.size shouldBe 2161
      mechanical.next.history.size shouldBe 2162
      encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be <= 67108864
      encoded.length should be > 55924056
      WorkflowMechanicalReceiptLimits.maximumBytes shouldBe 67108864
      val catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(definition)))
      take(WorkflowMechanicalTransitionJsonV1.decodeC(encoded, catalog, GoalPhaseNativeValidation.codec, limits)) shouldBe mechanical
    }
  }
}
