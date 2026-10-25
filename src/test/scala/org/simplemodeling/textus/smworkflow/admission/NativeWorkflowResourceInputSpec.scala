package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.protocol.{Argument, Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take

/** Shared transport regressions, without business authority or Resource I/O.
  * @since Oct. 24, 2026
  * @version Oct. 25, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeWorkflowResourceInputSpec extends AnyWordSpec with Matchers with GivenWhenThen with NativePreparationOperationFixture {
  private def _request(values: (String, Any)*): Request = Request.of(
    component = "org.simplemodeling.textus.SmWorkflow", service = "GoalPhaseService",
    operation = "describeGoalPhaseValidationRepair", arguments = values.toList.map((n, v) => Argument(n, v)),
    switches = Nil, properties = Nil)
  private val _input = V.GoalPhaseAdvanceInput("fixture-session")
  "Shared current Resource input" should {
    "permit both actual validation repair owning calls while refusing a Session without native Start" in {
      withPreparation() { e =>
        Given("the normal generated Operations, valid business keys and an absent fixture Session without business authority")
        val session = "absent-validation-repair-session"
        val proposal = Record.data("fixture" -> "nonempty-proposal")
        val target = Record.data("fixture" -> "nonempty-target")
        val description = V.GoalPhaseValidationRepairDescriptionInput(session, proposal, None)
        val adoption = V.GoalPhaseValidationRepairAdoptionInput(session, target, proposal, "Defer",
          "Specification-only absent Session refusal", V.WorkflowArtifactReference("fixture-reference", "1"))
        val inputs = Vector(GoalPhaseValidationRepairAdoption.describeOperation -> description.toRecord(),
          GoalPhaseValidationRepairAdoption.adoptionOperation -> adoption.toRecord())
        When("both complete DTOs enter their generated public handlers through normal inline transport")
        val refused = inputs.map { (operation, input) =>
          operation -> e.owner.executeOperationResponseInContext(Request.of(component = e.component.name,
            service = "GoalPhaseService", operation = operation,
            properties = List(Property("input", take(input.toJsonStringC), None))), e.actor)
        }
        Then("owning call binding permits each Operation and retained Session admission refuses the missing native Start")
        refused.foreach { (operation, result) =>
          withClue(operation) {
            result match {
              case Consequence.Failure(conclusion) =>
                conclusion.toString should include ("owning advance has no actual native ordered Start")
                conclusion.toString should not include ("native ordered entry requires its actual owning call")
              case _ => fail("An absent fixture Session must not receive business admission")
            }
          }
        }
      }
    }
    "enforce XOR exact business keys and alias collisions in both actual new public handlers" in {
      withPreparation() { e =>
        Given("the normal generated service and no supplied Session effect authority")
        val absent = Vector.empty[Property]
        val ambiguous = Vector(Property("input", "{}", None), Property("inputResource", "file:///tmp/absent.json", None))
        val business = Vector("{}", "null", "[]", "{\"sessionId\":\"fixture\",\"proposal\":{},\"extra\":true}",
          "{\"sessionId\":\"fixture\",\"session-id\":\"other\",\"proposal\":{}}", "{\"sessionId\":null,\"proposal\":{}}")
        When("each actual public handler receives an absent ambiguous malformed or extended business input")
        val refused = Vector("describeGoalPhaseValidationRepair", "adoptGoalPhaseValidationRepair").flatMap { operation =>
          (Vector(absent, ambiguous) ++ business.map(text => Vector(Property("input", text, None)))).map { properties =>
            e.owner.executeOperationResponseInContext(Request.of(component = e.component.name, service = "GoalPhaseService",
              operation = operation, properties = properties.toList), e.actor)
          }
        }
        Then("every call refuses before any native adoption or Resource fallback")
        refused.foreach(_.isSuccess shouldBe false)
      }
    }
    "decode the same complete generated business value through either exclusive transport" in {
      Given("one exact generated DTO and an escaped local file reference")
      val text = take(_input.toRecord().toJsonStringC)
      val inline = take(NativeWorkflowResourceInput.selectC[V.GoalPhaseAdvanceInput](_request("input" -> text)))
      val resource = take(NativeWorkflowResourceInput.selectC[V.GoalPhaseAdvanceInput](_request("inputResource" -> "file:///private/tmp/input%20value.json")))
      var reads = 0
      When("the owning execution context resolves the selected source")
      val a = take(NativeWorkflowResourceInput.readC(inline, _ => fail("inline must not acquire a Resource")))
      val b = take(NativeWorkflowResourceInput.readC(resource, _ => { reads += 1; Consequence.success(text) }))
      Then("there is exactly one Resource read and both retain the identical business value")
      a shouldBe _input
      b shouldBe _input
      reads shouldBe 1
    }
    "refuse missing conflicting nonlocal and malformed inputs before business admission" in {
      Given("absent ambiguous or unsafe transport selectors")
      val requests = Vector(_request(), _request("input" -> "{}", "inputResource" -> "file:///private/tmp/a")) ++
        Vector[Any](null, 4, "", "relative.json", "file:relative", "file://host/tmp/a", "https://example.test/a",
          "file:///tmp/a?q=1", "file:///tmp/a#part", " file:///tmp/a").map(x => _request("inputResource" -> x))
      When("the shared selector runs")
      val results = requests.map(NativeWorkflowResourceInput.selectC[V.GoalPhaseAdvanceInput])
      Then("every malformed selector refuses")
      results.foreach(_.isSuccess shouldBe false)
      val resource = take(NativeWorkflowResourceInput.selectC[V.GoalPhaseAdvanceInput](_request("inputResource" -> "file:///tmp/a")))
      Vector("{", "null", "[]", "{}", "{\"sessionId\":null}").foreach { text =>
        NativeWorkflowResourceInput.readC(resource, _ => Consequence.success(text)).isSuccess shouldBe false
      }
      NativeWorkflowResourceInput.readC(resource, _ => Consequence.resourceInvalid("normal denied/oversized/invalid UTF-8 Resource")).isSuccess shouldBe false
    }
  }
}
