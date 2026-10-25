package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseDocumentPlan.*
import GoalPhaseOrderedProgression as O

/** Pure document examples; no source authentication, execution or progress evidence.
  * @since   Oct. 17, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseDocumentPlanSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _plan = O.Plan("explicit-plan", "1", "PHASE-6.1", "sm-workflow",
    Vector(O.Step("S1", "Decode explicit documents", Vector("src/one.scala"), Vector.empty, Vector("focused-one")),
      O.Step("S2", "Connect the selected input", Vector("src/two.scala"), Vector("S1"), Vector("focused-two"))),
    Vector("whole-phase"))
  private val _bindings = Vector(Binding("S1", Vector("P61-A", "P61-B")), Binding("S2", Vector("P61-C")))
  private def _envelope(p: O.Plan = _plan, rows: Vector[Binding] = _bindings): Json = Json.obj(
    "schemaVersion" -> Json.fromString("sm-workflow.phase-plan.v1"), "plan" -> p.asJson,
    "bindings" -> Json.fromValues(rows.map(b => Json.obj(
      "step" -> Json.fromString(b.step), "items" -> b.items.asJson))))
  private def _phase(json: Json): String = "# Selected Phase\n\n```sm-workflow-plan\n" + json.spaces2 + "\n```\n"
  private val _checklist = """# Shared checklist
    |
    |Narrative does not declare a Plan or complete a Step.
    |- [ ] P61-C: Connect selected source data.
    |- [ ] P61-A: Decode the declared plan
    |  with stable membership.
    |
    |  Preserve acceptance paragraphs.
    |This unindented paragraph ends acceptance.
    |  This unrelated indented text is not acceptance.
    |- [ ] P61-B: Keep checkmarks presentation only.
    |- [x] UNSELECTED: Other Phase work may coexist.
    |""".stripMargin

  "GoalPhase explicit document plan (P61-PLAN-A)" should {
    "import two declared Steps and ordered semantic acceptance through surrounding narrative" in {
      Given("a two-Step declaration, reversed binding rows and shared checklist paragraphs")
      val document = _phase(_envelope(rows = _bindings.reverse)) + "\nRun nothing from this narrative.\n"
      When("the explicit documents are decoded")
      val result = decodeC(document, _checklist)
      Then("the existing Plan and ordered membership are preserved without unrelated items")
      result.isSuccess shouldBe true
      val imported = result.toOption.get
      imported.plan shouldBe _plan
      imported.bindings shouldBe _bindings
      imported.items shouldBe Vector(
        Item("P61-A", "Decode the declared plan with stable membership. Preserve acceptance paragraphs.", false),
        Item("P61-B", "Keep checkmarks presentation only.", false),
        Item("P61-C", "Connect selected source data.", false))
    }

    "keep checked and unchecked documents semantically equivalent (checkbox rule)" in {
      Given("otherwise identical unchecked, lowercase checked and uppercase checked documents")
      val documents = Vector(_checklist, _checklist.replace("[ ]", "[x]"), _checklist.replace("[ ]", "[X]"))
      When("each presentation is imported")
      val results = documents.map(value => decodeC(_phase(_envelope()), value))
      Then("only the checked presentation flag changes; the Plan and acceptance remain equal")
      results.foreach(_.isSuccess shouldBe true)
      val imported = results.map(_.toOption.get)
      imported.map(_.plan).distinct shouldBe Vector(_plan)
      imported.map(_.bindings).distinct shouldBe Vector(_bindings)
      imported.map(_.items.map(i => i.identity -> i.acceptance)).distinct.size shouldBe 1
      imported.head.items.map(_.checked) shouldBe Vector(false, false, false)
      imported.tail.foreach(_.items.map(_.checked) shouldBe Vector(true, true, true))
    }

    "follow explicit input addition and reordering with valid earlier dependencies" in {
      Given("an added Step and reordered independent Steps with explicitly revised dependencies and bindings")
      val added = O.Step("S3", "Document the imported data", Vector("docs/three.md"), Vector("S2"), Vector("focused-three"))
      val extended = _plan.copy(steps = _plan.steps :+ added)
      val reordered = _plan.copy(steps = Vector(_plan.steps(1).copy(dependencies = Vector.empty),
        _plan.steps.head.copy(dependencies = Vector("S2"))))
      val extendedrows = _bindings :+ Binding("S3", Vector("P61-D"))
      When("each changed explicit declaration is decoded")
      val addition = decodeC(_phase(_envelope(extended, extendedrows.reverse)), _checklist + "- [ ] P61-D: Describe the new Step.\n")
      val reordering = decodeC(_phase(_envelope(reordered)), _checklist)
      Then("returned data follows the declared Step order and retains its valid dependencies")
      addition.toOption.get.plan shouldBe extended
      addition.toOption.get.bindings shouldBe extendedrows
      addition.toOption.get.items.map(_.identity) shouldBe Vector("P61-A", "P61-B", "P61-C", "P61-D")
      reordering.toOption.get.plan shouldBe reordered
      reordering.toOption.get.bindings shouldBe _bindings.reverse
      reordering.toOption.get.items.map(_.identity) shouldBe Vector("P61-C", "P61-A", "P61-B")
    }

    "reject duplicate, missing and extra Step bindings (exact coverage rule)" in {
      Given("bindings that duplicate a Step, omit a Step or introduce an unknown Step")
      val rows = Vector(_bindings :+ _bindings.head, _bindings.take(1),
        _bindings :+ Binding("S3", Vector("UNSELECTED")), Vector.empty)
      When("the contradictory declarations are decoded")
      val results = rows.map(value => decodeC(_phase(_envelope(rows = value)), _checklist))
      Then("no partial or ambiguous Step coverage is imported")
      results.foreach(_.isSuccess shouldBe false)
    }

    "reject empty, unstable and repeated referenced item membership" in {
      Given("empty membership, invalid stable IDs and repetitions within or across bindings")
      val rows = Vector(
        _bindings.updated(0, Binding("S1", Vector.empty)),
        _bindings.updated(0, Binding("S1", Vector("bad id"))),
        _bindings.updated(0, Binding("S1", Vector("a" * 129))),
        _bindings.updated(0, Binding("S1", Vector("P61-A", "P61-A"))),
        _bindings.updated(1, Binding("S2", Vector("P61-A"))),
        _bindings.updated(0, Binding("bad step", Vector("P61-A"))))
      When("the explicit memberships are decoded")
      val results = rows.map(value => decodeC(_phase(_envelope(rows = value)), _checklist))
      Then("every invalid or multiply referenced stable member is refused")
      results.foreach(_.isSuccess shouldBe false)
    }

    "reject missing and repeated actual checklist IDs even for unselected items" in {
      Given("a missing referenced item and duplicate actual selected or unselected checklist bullets")
      val documents = Vector(
        _checklist.replace("- [ ] P61-C: Connect selected source data.\n", ""),
        _checklist + "- [X] P61-A: Duplicate selected ID.\n",
        _checklist + "- [ ] UNSELECTED: Duplicate unselected ID.\n")
      When("the checklist membership is resolved")
      val results = documents.map(value => decodeC(_phase(_envelope()), value))
      Then("each reference must resolve exactly once and actual stable IDs cannot repeat")
      results.foreach(_.isSuccess shouldBe false)
    }

    "reject empty referenced acceptance while allowing acceptance supplied by continuation" in {
      Given("whitespace-only acceptance, an empty unselected item and a continuation-only referenced item")
      val empty = _checklist.replace("P61-C: Connect selected source data.", "P61-C:   \n  \t\n")
      val continuation = _checklist.replace("P61-C: Connect selected source data.", "P61-C:\n  Acceptance from continuation.") +
        "- [ ] UNUSED-EMPTY:\n"
      When("acceptance text is collected from actual item content")
      val refused = decodeC(_phase(_envelope()), empty)
      val accepted = decodeC(_phase(_envelope()), continuation)
      Then("only referenced items require nonempty semantic acceptance")
      refused.isSuccess shouldBe false
      accepted.toOption.get.items.last.acceptance shouldBe "Acceptance from continuation."
      accepted.toOption.get.items.map(_.identity) shouldBe Vector("P61-A", "P61-B", "P61-C")
    }

    "retain the existing Plan dependency, acceptance and work classification validation" in {
      Given("Plans with forward, absent or duplicate Steps and invalid existing quality or classification data")
      val classification = GoalPhaseWorkClassification.Classification(
        GoalPhaseWorkClassification.Kind.CODING, GoalPhaseWorkClassification.Complexity.STANDARD,
        GoalPhaseWorkClassification.Domain.PROGRAMMING, GoalPhaseWorkClassification.Footprint.SMALL)
      val invalid = Vector(
        _plan.copy(steps = _plan.steps.updated(0, _plan.steps.head.copy(dependencies = Vector("S2")))),
        _plan.copy(steps = _plan.steps.updated(1, _plan.steps(1).copy(dependencies = Vector("missing")))),
        _plan.copy(steps = _plan.steps :+ _plan.steps.head),
        _plan.copy(acceptance = Some(GoalPhaseAcceptanceBoundary.Adoption("", Vector.empty))),
        _plan.copy(phaseReviewClassification = Some(classification)),
        _plan.copy(steps = _plan.steps.updated(0, _plan.steps.head.copy(workClassification = Some(
          GoalPhaseWorkClassification.Assignment(classification, classification, classification))))))
      When("the declarations use the existing ordered Plan decoder and validator")
      val results = invalid.map(value => decodeC(_phase(_envelope(value)), _checklist))
      Then("document import cannot bypass any of those existing Plan contracts")
      results.foreach(_.isSuccess shouldBe false)
    }

    "preserve valid acceptance and work classification fields as Plan data" in {
      Given("an explicit Plan with valid adopted quality conditions and work classifications")
      val coding = GoalPhaseWorkClassification.Classification(
        GoalPhaseWorkClassification.Kind.CODING, GoalPhaseWorkClassification.Complexity.COMPLEX,
        GoalPhaseWorkClassification.Domain.PROGRAMMING, GoalPhaseWorkClassification.Footprint.MEDIUM)
      val review = coding.copy(kind = GoalPhaseWorkClassification.Kind.REVIEW)
      val declared = _plan.copy(steps = _plan.steps.map(_.copy(workClassification = Some(
        GoalPhaseWorkClassification.Assignment(coding, coding, review)))), phaseReviewClassification = Some(review),
        acceptance = Some(GoalPhaseAcceptanceBoundary.Adoption("Use the explicit source data", Vector(
          GoalPhaseAcceptanceBoundary.Condition("QUALITY-1", "all", true, "Preserve stable membership")))))
      When("the document is decoded")
      val result = decodeC(_phase(_envelope(declared)), _checklist)
      Then("all explicit Plan fields retain their existing meaning and values")
      result.toOption.get.plan shouldBe declared
    }

    "reject malformed, unsupported and nonclosed JSON envelopes or binding objects" in {
      Given("malformed JSON and envelopes with absent, extra, null or wrongly typed data")
      val json = _envelope()
      val bad = Vector(
        json.mapObject(_.remove("schemaVersion")),
        json.mapObject(_.add("schemaVersion", Json.fromString("other"))),
        json.mapObject(_.add("command", Json.fromString("run"))),
        json.mapObject(_.add("plan", Json.Null)),
        json.mapObject(_.add("bindings", Json.Null)),
        json.mapObject(_.add("bindings", Json.obj("S1" -> Json.arr()))),
        json.mapObject(_.add("bindings", Json.arr(Json.obj("step" -> Json.fromString("S1"))))),
        json.mapObject(_.add("bindings", Json.arr(Json.obj("step" -> Json.fromString("S1"),
          "items" -> Json.arr(Json.fromString("P61-A")), "extra" -> Json.True)))),
        json.mapObject(_.add("bindings", Json.arr(Json.obj("step" -> Json.Null, "items" -> Json.arr())))),
        json.mapObject(_.add("bindings", Json.arr(Json.obj("step" -> Json.fromString("S1"), "items" -> Json.arr(Json.Null))))))
      val malformed = "```sm-workflow-plan\n{broken-json\n```"
      When("the selected JSON data is decoded")
      val results = bad.map(value => decodeC(_phase(value), _checklist)) :+ decodeC(malformed, _checklist)
      Then("all invalid input configurations return failures")
      results.foreach(_.isSuccess shouldBe false)
    }

    "require exactly one closed block with the exact top-level opening and closing" in {
      Given("absent, duplicate, unclosed and inexact selected-looking block delimiters")
      val selected = _phase(_envelope())
      val documents = Vector("# Prose only", selected + selected,
        "```sm-workflow-plan\n" + _envelope().noSpaces,
        "```sm-workflow-plan \n" + _envelope().noSpaces + "\n```",
        " ```sm-workflow-plan\n" + _envelope().noSpaces + "\n```",
        "```sm-workflow-plan\n" + _envelope().noSpaces + "\n``` ")
      When("the Phase Markdown is decoded")
      val results = documents.map(value => decodeC(value, _checklist))
      Then("missing, ambiguous and unclosed executable declarations fail as input")
      results.foreach(_.isSuccess shouldBe false)
    }

    "ignore selected-looking and checkbox examples inside ordinary fences" in {
      Given("backtick and tilde examples containing invalid selected data and duplicate-looking checkbox IDs")
      val examples = "````markdown\n```sm-workflow-plan\nnot JSON\n```\n````\n" +
        "~~~markdown\n```sm-workflow-plan\nnot JSON\n```\n~~~\n"
      val checkboxexamples = "```markdown\n- [x] P61-A: Example only.\n```\n" +
        "~~~~\n- [ ] P61-C: Another example.\n~~~~\n"
      When("a real declaration and checklist are decoded alongside those examples")
      val accepted = decodeC(examples + _phase(_envelope()), checkboxexamples + _checklist)
      val absent = decodeC(examples, _checklist)
      val onlyexample = decodeC(_phase(_envelope()), "```\n" + _checklist + "\n```\n")
      Then("only unfenced declarations and supported actual checklist bullets supply data")
      accepted.toOption.get.plan shouldBe _plan
      accepted.toOption.get.items.map(_.identity) shouldBe Vector("P61-A", "P61-B", "P61-C")
      accepted.toOption.get.items.forall(!_.checked) shouldBe true
      absent.isSuccess shouldBe false
      onlyexample.isSuccess shouldBe false
    }

    "preserve CRLF semantics and refuse null input without throwing" in {
      Given("equivalent LF and CRLF documents and null document inputs")
      val document = _phase(_envelope())
      When("the document pairs are decoded")
      val lf = decodeC(document, _checklist)
      val crlf = decodeC(document.replace("\n", "\r\n"), _checklist.replace("\n", "\r\n"))
      val absent = Vector(decodeC(null, _checklist), decodeC(document, null), decodeC(null, null))
      Then("line-ending presentation has no semantic effect and absent inputs are failures")
      lf.isSuccess shouldBe true
      crlf.toOption.get shouldBe lf.toOption.get
      absent.foreach(_.isSuccess shouldBe false)
    }
  }
}
