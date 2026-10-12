package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.protocol.{Argument, Property, Request, Switch}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
final class WorkflowJsonCommandRequestSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val wire = "{\"schemaVersion\":\"example.v1\",\"input\":\" unchanged \"}"
  private def request(arguments: List[Argument] = Nil, properties: List[Property] = Nil): Request =
    Request.of(component = "org.simplemodeling.textus.SmWorkflow", service = "GoalPhaseService",
      operation = "startGoalPhase", arguments = arguments, switches = Nil, properties = properties)

  "Shared Workflow JSON transport" should {
    "current public input" which {
      "consume positional or named JSON while ignoring unused Request attributes" in {
        Given("normal JSON business input with unused diagnostic and format properties, not execution grants")
        val cases = Vector(request(List(Argument("arg1", wire)), List(Property("unusedCommandGrant", true, None)))
            .copy(switches = List(Switch("unusedDiagnostic", true, None))),
          request(properties = List(Property("arg1", wire, None), Property("textus.format", "yaml", None))))
        When("the current public reader consumes only arg1")
        val extracted = cases.map(WorkflowJsonCommandRequest.inputC)
        Then("each input is available unchanged; unconsumed properties do not reject or replace it")
        extracted.foreach(_.toOption shouldBe Some(Some(wire)))
      }

      "distinguish optional absence from an invalid supplied value or ambiguous actual carriers" in {
        Given("absent optional input and supplied non-string, null or two actual arg1 values")
        val absent = request(properties = List(Property("unusedDiagnostic", "inert", None)))
        val invalid = Vector(request(List(Argument("arg1", 42))), request(List(Argument("arg1", null))),
          request(List(Argument("arg1", wire)), List(Property("arg1", wire, None))))
        When("the owning reader resolves the actual input carrier")
        val noInput = WorkflowJsonCommandRequest.inputC(absent)
        val failures = invalid.map(WorkflowJsonCommandRequest.inputC)
        Then("only absence becomes None; invalid supplied input cannot silently advance without input")
        noInput.toOption shouldBe Some(None)
        failures.foreach(_.isSuccess shouldBe false)
      }
    }

  }
}
