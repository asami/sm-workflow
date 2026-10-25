package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take
import NativeWholePhaseRecoveryInput as I

/** Transport-only boundaries; real admission is qualified by the installed CLI.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeWholePhaseRecoveryInputSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _input = V.GoalPhaseWholePhaseCandidateRecoveryInput("fixture-session",
    Record.data("completeFixtureTarget" -> "retained"), "AuthorizeNewObservation", "Actual fixture direction",
    V.WorkflowArtifactReference("fixture-human", "1"), true)
  private def _request(values: (String, Any)*): Request = Request.of(
    component = "org.simplemodeling.textus.SmWorkflow", service = "GoalPhaseService",
    operation = GoalPhaseWholePhaseCandidateRecovery.recoveryOperation,
    arguments = values.toList.map((name, value) => Argument(name, value)), switches = Nil, properties = Nil)

  "Whole-Phase recovery Resource input" should {
    "retain inline JSON compatibility without reading a resource" in {
      Given("the existing complete generated DTO as a named inline JSON value")
      val selected = take(I.selectC(_request("input" -> take(_input.toRecord().toJsonStringC))))
      When("the selected input is resolved")
      val value = take(I.readC(selected, _ => fail("Inline input must not read a resource")))
      Then("the same full DTO including target and Human reference is retained")
      value shouldBe _input
      val generic = take(NativeWorkflowResourceInput.selectC[V.GoalPhaseWholePhaseCandidateRecoveryInput](
        _request("input" -> take(_input.toRecord().toJsonStringC))))
      take(NativeWorkflowResourceInput.readC(generic, _ => fail("Inline generic input must not read a resource"))) shouldBe value
    }
    "read one local resource and preserve a complete input larger than argv capacity" in {
      Given("a complete generated DTO exceeding one MiB and an escaped local file URI")
      val target = Record.data("completeFixtureTarget" -> ("x" * 1300000))
      val input = _input.copy(target = target)
      val text = take(input.toRecord().toJsonStringC)
      text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 1048576
      val selected = take(I.selectC(_request("inputResource" -> "file:///private/tmp/recovery%20input.json")))
      var reads = 0
      When("the execution context's resource reader returns the complete text")
      val value = take(I.readC(selected, reference => {
        reads += 1
        reference.print shouldBe "file:///private/tmp/recovery%20input.json"
        Consequence.success(text)
      }))
      Then("one read returns the exact target and authority without truncation or another business request")
      reads shouldBe 1
      value shouldBe input
    }
    "refuse absent ambiguous and nonlocal input sources before any resource read" in {
      Given("missing or conflicting sources and unsafe URI variants")
      val invalid = Vector(_request(), _request("input" -> _input.toRecord(), "inputResource" -> "file:///private/tmp/input.json")) ++
        Vector[Any](null, 1, "", " file:///private/tmp/input.json", "input.json", "file:relative.json",
          "file://remote/private/tmp/input.json", "https://example.test/input.json", "file:///private/tmp/input.json?x=1",
          "file:///private/tmp/input.json#value").map(value => _request("inputResource" -> value))
      When("transport selection runs without executing the recovery action")
      val outcomes = invalid.map(I.selectC)
      Then("every invalid source is refused")
      outcomes.foreach(_.isSuccess shouldBe false)
    }
    "propagate normal resource failures without attempting admission" in {
      Given("a selected file URI and a missing denied or oversized resource result")
      val source = take(I.selectC(_request("inputResource" -> "file:///private/tmp/absent.json")))
      When("the owning resource reader refuses")
      val result = I.readC(source, _ => Consequence.resourceInvalid("fixture normal resource refusal"))
      Then("no DTO is returned and the failure remains visible")
      result.isSuccess shouldBe false
    }
    "reject malformed incomplete and explicit-null business JSON after reading" in {
      Given("normal Resource reads returning invalid business payloads")
      val source = take(I.selectC(_request("inputResource" -> "file:///private/tmp/input.json")))
      val invalid = Vector("{", "[]", "{}", "null", "{\"sessionId\":null}")
      When("the same strict inline decoder consumes those payloads")
      val outcomes = invalid.map(text => I.readC(source, _ => Consequence.success(text)))
      Then("all refuse before the recovery port can consume Human evidence")
      outcomes.foreach(_.isSuccess shouldBe false)
    }
  }
}
