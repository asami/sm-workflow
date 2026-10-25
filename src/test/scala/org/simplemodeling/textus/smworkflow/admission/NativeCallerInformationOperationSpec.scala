package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V

/** Supplied public-source examples are fixture observations, not actual operator
  * answers or proof of the real Session's interrupted process.
  * @since   Oct. 23, 2026
  * @version Oct. 24, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeCallerInformationOperationSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativePreparationOperationFixture {
  import NativeCallerExecutionFixture.{issued, take, record}
  private val _source = V.WorkflowCodexSourceLocator("fixture-thread", "fixture-turn", "fixture-item")
  private val _now = "2026-10-23T00:00:00Z"
  private def _business[A: ValueReader](value: Consequence[OperationResponse]): A = take(value) match {
    case OperationResponse.Json(json) => take(summon[ValueReader[A]].readC(record(json)))
    case other => fail("Expected normal generated output: " + other)
  }
  private def _page(status: String = "failed", code: Option[Int] = Some(2)): String = Json.obj(
    "thread" -> Json.obj("id" -> Json.fromString(_source.threadId)),
    "turns" -> Json.arr(Json.obj("id" -> Json.fromString(_source.turnId), "items" -> Json.arr(Json.obj(
      "type" -> Json.fromString("commandExecution"), "id" -> Json.fromString(_source.itemId),
      "status" -> Json.fromString(status), "exitCode" -> code.map(Json.fromInt).getOrElse(Json.Null),
      "command" -> Json.fromString("fixture-owned already completed command"),
      "output" -> Json.obj("text" -> Json.fromString("Original fixture diagnostics\n"), "truncated" -> Json.False)))))).noSpaces
  private def _prepared(e: PreparationEpisode): V.WorkflowCallerInformationRequest =
    _business(e.invoke("prepareCallerInformation", V.WorkflowCallerInformationPrepareInput(
      issued(), "execution-terminal", _source, "fixture-caller").toRecord()))
  private def _returned(e: PreparationEpisode, request: V.WorkflowCallerInformationRequest,
    outcome: String, raw: Option[String], current: Option[V.WorkflowIssuedWorkContext] = None): Consequence[OperationResponse] = {
    val observation = V.WorkflowCallerInformationObservation(request, outcome, _now, raw, "Actual fixture scope; no effect authority")
    e.invoke("recordCallerInformation", V.WorkflowCallerInformationRecordInput(current.getOrElse(request.issuedWorkContext),
      e.saveValue("information-request", request), e.saveValue("returned-observation", observation)).toRecord())
  }

  "Caller information through normal preparation Operations" should {
    "retain failed terminal facts and the exact issued continuation on repeated return without semantic completion" in {
      withPreparation() { e =>
        Given("an actual-shaped issued scope and a selected existing caller source, without a provider or new executor")
        val request = _prepared(e)
        When("the caller returns original failed tool bytes twice to the same passive operation")
        val first = _business[V.WorkflowCallerInformationResult](_returned(e, request, "available", Some(_page())))
        val duplicate = _business[V.WorkflowCallerInformationResult](_returned(e, request, "available", Some(_page())))
        Then("both retain the same Session/continuation, source and failure; neither produces Human or semantic completion evidence")
        first shouldBe duplicate
        first.request shouldBe request
        io.circe.parser.parse(first.terminalObservation.get).toOption.get.hcursor.get[Int]("exitCode").toOption shouldBe Some(2)
        first.rawObservation shouldBe Some(_page())
        first.sourceReference.identity shouldBe "codex:fixture-thread:fixture-turn:fixture-item"
        first.toRecord().keySet should not contain "completionEvidence"
        first.toRecord().keySet should not contain "datastore"
        org.simplemodeling.textus.smworkflow.caller.NativeCallerInformationSource.selectC(
          _source, "commandExecution", _page()).isSuccess shouldBe true
      }
    }

    "retain unavailable and partial information without inventing terminal success or changing the continuation" in {
      withPreparation() { e =>
        Given("the original request while the caller lacks terminal proof")
        val request = _prepared(e)
        When("unavailable and partial public source are explicitly returned")
        val missing = _business[V.WorkflowCallerInformationResult](_returned(e, request, "unavailable", None))
        val partial = _business[V.WorkflowCallerInformationResult](_returned(e, request, "partial", Some(_page("running", None))))
        Then("availability remains separate from the retained issued binding and no terminal or retry permission is manufactured")
        missing.request shouldBe request
        missing.terminalObservation shouldBe None
        partial.terminalObservation shouldBe None
        partial.outcome shouldBe "partial"
        _returned(e, request, "available", Some(_page("running", None))) shouldBe a[Consequence.Failure[?]]
      }
    }

    "reject crossed issued revisions and source items before producing a returned observation" in {
      withPreparation() { e =>
        Given("a retained request tied to one exact issued revision and public item")
        val request = _prepared(e)
        When("a later revision or a different source item is supplied")
        val crossed = _returned(e, request, "available", Some(_page()), Some(request.issuedWorkContext.copy(expectedRevision = "8")))
        val other = _returned(e, request, "available", Some(_page().replace("fixture-item", "foreign-item")))
        Then("the current call refuses without completing, recovering or advancing any workflow")
        crossed shouldBe a[Consequence.Failure[?]]
        other shouldBe a[Consequence.Failure[?]]
      }
    }
  }
}
