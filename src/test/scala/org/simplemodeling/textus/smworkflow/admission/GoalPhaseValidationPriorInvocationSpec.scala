package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseValidationPriorInvocation as P
import SplitPhaseExplicitPartitionFixture.take

/** Shaped public pages establish parser behavior only, never genuine provenance.
  * @since Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseValidationPriorInvocationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _policy = P.CallerSourcePolicy("sm-workflow.validation-repair-caller-source-policy.v1", "fixture-thread",
    "/private/tmp/installed/sm-workflow", "/private/tmp/runtime", "/private/tmp/runtime/target/sm-workflow/tasks/repair")
  private val _locator = V.WorkflowCodexSourceLocator("fixture-thread", "fixture-turn", "fixture-item")
  private val _selector = P.PriorInvocationSelector("sm-workflow.validation-prior-invocation-selector.v1",
    _locator, "file:///private/tmp/public-page.json", "old-advance")
  private val _item = Json.obj("type" -> "commandExecution".asJson, "id" -> _locator.itemId.asJson,
    "command" -> P.command(_policy, "fixture-session", _selector.outputName).asJson,
    "cwd" -> _policy.runtimeRoot.asJson, "status" -> "failed".asJson, "exitCode" -> 1.asJson, "durationMs" -> 4000.asJson)
  private def _page(item: Json = _item): Json = Json.obj("thread" -> Json.obj("id" -> _locator.threadId.asJson),
    "turns" -> Json.arr(Json.obj("id" -> _locator.turnId.asJson, "items" -> Json.arr(item))))
  private def _observe(page: Json) = P.observeC(_selector, _policy, "fixture-session", Instant.parse("2026-10-24T00:00:00Z"),
    _ => Consequence.success(page.noSpaces))
  "Original caller invocation parser" should afterWord("in spec:goal-phase-validation-repair-adoption, example:E3, rules:R3,R5") {
    "retain the exact original item clock locator comparison policy and limited proof scope" in {
      Given("a structural fixture page with one exact failed installed Advance item")
      When("the source adapter observes and rereads it")
      val observed = take(_observe(_page()))
      Then("only caller termination is represented and the original selected value is retained")
      observed.selectedItemJson shouldBe _item.noSpaces
      observed.proofScope shouldBe "caller-invocation-terminated-only"
      observed.policy shouldBe _policy
      observed.asJson.as[P.PriorInvocationSnapshot] shouldBe Right(observed)
      P.revalidateC(observed, _policy, "fixture-session", _ => Consequence.success(_page().noSpaces)).isSuccess shouldBe true
      P.revalidateC(observed, _policy, "fixture-session", _ => Consequence.success(_page(_item.mapObject(_.add("durationMs", 4001.asJson))).noSpaces)).isSuccess shouldBe false
    }
    "refuse outer commands different invocation renderings paths terminal facts partial and duplicate items" in {
      Given("independent mutations of the exact original public item")
      val changed = Vector(
        _item.mapObject(_.add("command", "sbt testOnly outer.Spec".asJson)),
        _item.mapObject(_.add("command", P.command(_policy, "another-session", _selector.outputName).asJson)),
        _item.mapObject(_.add("cwd", "/private/tmp/other".asJson)),
        _item.mapObject(_.add("status", "running".asJson)),
        _item.mapObject(_.add("status", "completed".asJson)),
        _item.mapObject(_.add("durationMs", Json.fromDoubleOrNull(1.5))),
        _item.mapObject(_.add("exitCode", Json.Null)), _item.mapObject(_.remove("command")),
        _item.mapObject(_.add("extra", Json.True)), _item.mapObject(_.add("type", "userMessage".asJson)))
      When("each structural page is selected")
      val refused = changed.map(item => _observe(_page(item)))
      Then("no mutation proves historical association or passes the exact invocation selector")
      refused.foreach(_.isSuccess shouldBe false)
      val duplicate = _page().mapObject(_.add("turns", Json.arr(Json.obj("id" -> _locator.turnId.asJson,
        "items" -> Json.arr(_item, _item)))))
      _observe(duplicate).isSuccess shouldBe false
      _observe(_page().mapObject(_.add("thread", Json.obj("id" -> "other-thread".asJson)))).isSuccess shouldBe false
    }
    "keep the optional caller policy closed and refuse unsafe or out-of-task destinations" in {
      Given("the complete optional policy and selector")
      When("new readers decode exact and extended configuration")
      val exact = _policy.asJson.as[P.CallerSourcePolicy]
      Then("only the exact finite supported policy and local selector survive")
      exact shouldBe Right(_policy)
      _policy.asJson.mapObject(_.add("extra", Json.True)).as[P.CallerSourcePolicy].isLeft shouldBe true
      Vector(_policy.copy(taskRoot = _policy.runtimeRoot), _policy.copy(taskRoot = _policy.runtimeRoot + "/target/sm-workflow/tasks"),
        _policy.copy(installedExecutable = "/tmp/a'quoted"), _policy.copy(runtimeRoot = "/tmp/../runtime"))
        .foreach(P.policyValid(_) shouldBe false)
      _selector.copy(sourceResource = "https://example.test/page").asJson.as[P.PriorInvocationSelector].isLeft shouldBe true
      _selector.asJson.mapObject(_.add("sourceLocator", Json.Null)).as[P.PriorInvocationSelector].isLeft shouldBe true
    }
  }
}
