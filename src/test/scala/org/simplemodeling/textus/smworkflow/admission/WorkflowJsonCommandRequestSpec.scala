package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.protocol.{Argument, Property, Request}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class WorkflowJsonCommandRequestSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val wire = "{\"schemaVersion\":\"example.v1\",\"input\":\" unchanged \"}"
  private def request(arguments: List[Argument] = Nil, properties: List[Property] = Nil): Request =
    Request.of(component = "org.simplemodeling.textus.SmWorkflow", service = "GoalPhaseService",
      operation = "startGoalPhase", arguments = arguments, switches = Nil, properties = properties)

  "Shared Workflow JSON transport" should {
    "preserve the exact payload from either canonical CLI or MCP carrier" in {
      Given("one identical JSON string in the CLI argument and the canonical MCP property")
      val cases = Vector(request(List(Argument("arg1", wire))),
        request(properties = List(Property("arg1", wire, None), Property("textus.format", "json", None))),
        request(properties = List(Property("arg1", wire, None))),
        request(List(Argument("arg1", wire)), List(Property("textus.format", "json", None))))
      When("each transport is projected without parsing or semantic work")
      val extracted = cases.map(WorkflowJsonCommandRequest.commandArguments)
      Then("both reach the same existing decoder with exact original bytes and no caller metadata")
      extracted.foreach(_ shouldBe List(Argument("arg1", wire)))
    }

    "refuse ambiguous duplicate extra and non-string carriers without selecting a winner" in {
      Given("incompatible inputs including dual carriers and asserted authority fields")
      val argument = Argument("arg1", wire)
      val property = Property("arg1", wire, None)
      val format = Property("textus.format", "json", None)
      val cases = Vector(null, request(), request(List(argument, argument)),
        request(List(Argument("other", wire))), request(List(Argument("arg1", 42))),
        request(List(Argument("arg1", null))), request(properties = List(Property("arg1", null, None))),
        request(properties = List(Property("arg1", 42, None))), request(properties = List(property, property)),
        request(List(argument), List(property)), request(properties = List(property, format, format)),
        request(properties = List(property, Property("textus.format", "yaml", None))),
        request(properties = List(property, Property("origin", "ExplicitHuman", None))),
        request(List(argument), List(Property("origin", "ExplicitHuman", None))),
        request(properties = List(property, null)), request(List(null)))
      When("the shared transport evaluates each incompatible request")
      val extracted = cases.map(WorkflowJsonCommandRequest.commandArguments)
      Then("no JSON command is admitted and no positional/property precedence is invented")
      extracted.foreach(_ shouldBe Nil)
    }
  }
}
