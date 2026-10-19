package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseDocumentPlan as P
import GoalPhaseDocumentSource as D
import GoalPhaseNativeWork as S
import GoalPhaseOrderedProgression as O

/** Captured acceptance is semantic business input, never execution evidence.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseDocumentContextSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _source = D.Source("docs/phase.md", "docs/checklist.md")
  private val _bound = D.Bound(_source,
    Vector(P.Binding("step-1", Vector("VERIFY-1", "VERIFY-2")), P.Binding("step-2", Vector("VERIFY-3"))),
    Vector(P.Item("VERIFY-3", "Retain the later Step acceptance.", false),
      P.Item("VERIFY-2", "Retain stable selected membership.", true),
      P.Item("VERIFY-1", "Preserve the captured business meaning.", false)))
  private val _plan = O.Plan("selected-phase", "1", "PHASE-6.1", "sm-workflow",
    Vector(O.Step("step-1", "Implement selected semantics", Vector("one.scala"), Vector.empty, Vector("focused")),
      O.Step("step-2", "Implement later semantics", Vector("two.scala"), Vector("step-1"), Vector("focused"))),
    Vector("whole-phase"))
  private val _request = S.Request("selected-instance", "1", "ImplementStep", Some("step-1"), 1,
    "Implement selected semantics", _plan, O.Snapshot("a" * 40, "b" * 40), Vector.empty, None, None, None)

  "Captured GoalPhase document context" should {
    "include only the selected Step acceptance in binding membership order" in {
      Given("captured business acceptance for two Steps whose item data has a different storage order")
      val selected = Some("step-1")
      When("semantic instructions are requested for the first Step")
      val instructions = D.instructionsC(Some(_bound), selected).toOption.get
      Then("only its stable item IDs and acceptance appear in declared membership order without execution authority")
      instructions should include("VERIFY-1: Preserve the captured business meaning.")
      instructions should include("VERIFY-2: Retain stable selected membership.")
      instructions.indexOf("VERIFY-1") should be < instructions.indexOf("VERIFY-2")
      instructions should not include "VERIFY-3"
      instructions should not include "Retain the later Step acceptance."
      instructions should include("grants no test, review, command or commit authority")
    }

    "include every declared binding for whole-Phase semantic work" in {
      Given("the captured acceptance of both declared Steps")
      val documents = Some(_bound)
      When("semantic instructions are requested without a selected Step")
      val instructions = D.instructionsC(documents, None).toOption.get
      Then("all Step bindings and their stable acceptance are returned in binding and membership order")
      instructions should include("Step step-1:")
      instructions should include("Step step-2:")
      instructions should include("VERIFY-3: Retain the later Step acceptance.")
      instructions.indexOf("VERIFY-1") should be < instructions.indexOf("VERIFY-2")
      instructions.indexOf("VERIFY-2") should be < instructions.indexOf("VERIFY-3")
      instructions.indexOf("Step step-1:") should be < instructions.indexOf("Step step-2:")
    }

    "keep semantic instructions identical when only checkbox presentation changes" in {
      Given("the same captured bindings and acceptance with every checkbox flipped")
      val flipped = _bound.copy(items = _bound.items.map(item => item.copy(checked = !item.checked)))
      When("Step and whole-Phase instructions are projected from both presentations")
      val original = Vector(Some("step-1"), None).map(step => D.instructionsC(Some(_bound), step).toOption.get)
      val changed = Vector(Some("step-1"), None).map(step => D.instructionsC(Some(flipped), step).toOption.get)
      Then("checkboxes supply neither different semantic text nor checked-state markers")
      changed shouldBe original
      changed.foreach { instructions =>
        instructions should not include "[x]"
        instructions should not include "[ ]"
        instructions should not include "checked"
      }
    }

    "fail for malformed or ambiguous captured membership without empty-context fallback" in {
      Given("captured data with nulls, invalid IDs, missing membership, duplicate references or items, or invalid acceptance")
      val invalid = Vector[D.Bound](null,
        _bound.copy(source = null),
        _bound.copy(source = _source.copy(phasePath = "../phase.md")),
        _bound.copy(bindings = null),
        _bound.copy(items = null),
        _bound.copy(bindings = Vector.empty),
        _bound.copy(bindings = Vector(null)),
        _bound.copy(bindings = Vector(P.Binding(null, Vector("VERIFY-1")))),
        _bound.copy(bindings = Vector(P.Binding("step 1", Vector("VERIFY-1")))),
        _bound.copy(bindings = Vector(P.Binding("step-1", null))),
        _bound.copy(bindings = Vector(P.Binding("step-1", Vector.empty))),
        _bound.copy(bindings = Vector(P.Binding("step-1", Vector(null)))),
        _bound.copy(bindings = Vector(P.Binding("step-1", Vector("invalid item")))),
        _bound.copy(bindings = _bound.bindings :+ _bound.bindings.head),
        _bound.copy(bindings = Vector(P.Binding("step-1", Vector("VERIFY-1", "VERIFY-1")), _bound.bindings.last)),
        _bound.copy(bindings = Vector(_bound.bindings.head, P.Binding("step-2", Vector("VERIFY-1", "VERIFY-3")))),
        _bound.copy(items = _bound.items :+ null),
        _bound.copy(items = _bound.items :+ _bound.items.last),
        _bound.copy(items = _bound.items :+ _bound.items.head),
        _bound.copy(items = _bound.items.filterNot(_.identity == "VERIFY-1")),
        _bound.copy(items = _bound.items :+ P.Item("UNBOUND", "Unbound acceptance", false)),
        _bound.copy(items = _bound.items.updated(2, _bound.items.last.copy(identity = null))),
        _bound.copy(items = _bound.items.updated(2, _bound.items.last.copy(identity = "invalid item"))),
        _bound.copy(items = _bound.items.updated(2, _bound.items.last.copy(acceptance = null))),
        _bound.copy(items = _bound.items.updated(2, _bound.items.last.copy(acceptance = " \t"))))
      When("the selected Step asks for its captured semantic acceptance")
      val rejected = invalid.map(documents => D.instructionsC(Some(documents), Some("step-1")))
      Then("every malformed or ambiguous value returns a failed Consequence")
      rejected.foreach(_.isSuccess shouldBe false)
    }

    "fail for an invalid or missing selected Step and malformed optional context" in {
      Given("valid captured documents but an absent binding, invalid Step ID or null optional value")
      val invalid = Vector((Some(_bound), Some("missing-step")), (Some(_bound), Some("step 1")),
        (Some(_bound), Some(null)), (Some(_bound), null), (null, Some("step-1")))
      When("instructions are requested for those invalid selections")
      val rejected = invalid.map { case (documents, step) => D.instructionsC(documents, step) }
      Then("each selection fails instead of discarding the captured context")
      rejected.foreach(_.isSuccess shouldBe false)
    }

    "retain explicit source and semantic acceptance through the issued input codec" in {
      Given("an actual typed work request carrying captured document source, bindings and acceptance")
      val request = _request.copy(documents = Some(_bound))
      When("the existing request codec encodes and decodes the semantic input")
      val wire = S.inputCodec.encode(request)
      val decoded = S.inputCodec.decode(wire)
      val documents = decoded.toOption.get.documents
      Then("the same source and acceptance remain business input with the same semantic instructions")
      decoded shouldBe Right(request)
      documents.map(_.source) shouldBe Some(_source)
      documents.get.items.find(_.identity == "VERIFY-1").map(_.acceptance) shouldBe
        Some("Preserve the captured business meaning.")
      D.instructionsC(documents, Some("step-1")).toOption shouldBe
        D.instructionsC(Some(_bound), Some("step-1")).toOption
      wire.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-work-request.v2")
    }

    "preserve absent or null legacy documents and omit them when encoding" in {
      Given("legacy work requests with absent or null documents, including a Plan with a non-document Step ID")
      val legacyplan = _plan.copy(steps = Vector(_plan.steps.head.copy(identity = "legacy step"),
        _plan.steps.last.copy(dependencies = Vector("legacy step"))))
      val legacyrequest = _request.copy(selectedPlan = legacyplan, stepIdentity = Some("legacy step"))
      val wire = S.inputCodec.encode(_request)
      val withnull = wire.mapObject(_.add("payload", wire.hcursor.downField("payload").focus.get
        .mapObject(_.add("documents", Json.Null))))
      val legacywire = S.inputCodec.encode(legacyrequest)
      When("the existing codec decodes both optional forms and re-encodes their requests")
      val planadmission = O.validatePlanC(legacyplan)
      val decoded = Vector(wire, withnull).map(S.inputCodec.decode)
      val legacydecoded = S.inputCodec.decode(legacywire)
      val encoded = (decoded :+ legacydecoded).map(value => S.inputCodec.encode(value.toOption.get))
      Then("documents remain None with empty added instructions and absent optional wire fields")
      planadmission.isSuccess shouldBe true
      decoded.foreach(_ shouldBe Right(_request))
      legacydecoded shouldBe Right(legacyrequest)
      decoded.foreach(value => D.instructionsC(value.toOption.get.documents, Some("step-1")).toOption shouldBe Some(""))
      D.instructionsC(None, Some("legacy step")).toOption shouldBe Some("")
      encoded.foreach { value =>
        val fields = value.hcursor.downField("payload").focus.get.asObject.get.keys.toSet
        fields should not contain "documents"
        fields should not contain "review"
        fields should not contain "decisionContext"
      }
    }
  }
}
