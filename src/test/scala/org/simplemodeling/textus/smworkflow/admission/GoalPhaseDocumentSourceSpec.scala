package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseDocumentSource as D
import GoalPhaseOrderedProgression as O

/** Passive formats are data, independently of File authority or native Start.
  * @since   Oct. 17, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseDocumentSourceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _source = D.Source("docs/phase.md", "docs/checklist.md")
  private val _selection = D.Selection("selected-phase", "participant", _source)
  private val _declaration = Json.obj("schemaVersion" -> Json.fromString(D.declarationSchema),
    "phasePath" -> Json.fromString(_source.phasePath), "checklistPath" -> Json.fromString(_source.checklistPath))
  private val _plan = O.Plan("selected-phase", "1", "phase-2", "sm-workflow",
    Vector(O.Step("step-1", "Implement selected documents", Vector("one.scala"), Vector.empty, Vector("test"))),
    Vector("test"))

  "Passive GoalPhase document selection" should {
    "roundtrip an explicit source and its finite storage envelope without acquiring documents" in {
      Given("an exact declaration and independently selected participant and selection identities")
      When("the declaration is decoded and its passive selection is stored and reloaded")
      val source = D.decodeDeclarationC(_declaration.spaces2)
      val stored = D.encodeStorageC(_selection).toOption.get
      val loaded = D.decodeStorageC(stored)
      Then("the existing typed storage field retains only the selected relative filenames and identities")
      source.toOption shouldBe Some(_source)
      loaded.toOption shouldBe Some(_selection)
      D.storageField shouldBe GoalPhaseSelectedPlanJsonV1.storageField
      D.isStorageEncoding(stored) shouldBe true
      D.isStorageEncoding("sm-workflow.goal-phase-document-selection.v10:{}") shouldBe false
      D.isStorageEncoding(null) shouldBe false
      D.loadForCallC(_source, null).isSuccess shouldBe false
    }

    "reject mixed declarations, unsupported schemas and unsafe or missing filenames" in {
      Given("closed declaration variants with wrong types, missing fields, extra Plan or permission data and unsafe paths")
      val shapes = Vector("null", "[]", "{", "{}", _declaration.mapObject(_.remove("phasePath")).noSpaces,
        _declaration.mapObject(_.add("schemaVersion", Json.Null)).noSpaces,
        _declaration.mapObject(_.add("schemaVersion", Json.fromString("unknown"))).noSpaces,
        _declaration.mapObject(_.add("plan", Json.obj())).noSpaces,
        _declaration.mapObject(_.add("permission", Json.True)).noSpaces,
        _declaration.mapObject(_.add("checklistPath", Json.fromInt(1))).noSpaces)
      val paths = Vector[String](null, "", " ", "/etc/passwd", "../checklist.md", "docs/../checklist.md",
        "docs//checklist.md", "./checklist.md", "file:checklist.md", "https://example.invalid/phase", "C:/phase.md",
        _source.checklistPath)
      When("each declaration or copied Source is validated as passive input")
      val results = shapes.map(D.decodeDeclarationC) ++ paths.map(path => D.decodeDeclarationC(
        _declaration.mapObject(_.add("phasePath", Option(path).fold(Json.Null)(Json.fromString))).noSpaces))
      Then("none supplies an accepted source or alternative Plan")
      results.foreach(_.isSuccess shouldBe false)
      D.validateC(null).isSuccess shouldBe false
      paths.foreach(path => D.validateC(_source.copy(phasePath = path)).isSuccess shouldBe false)
    }

    "reject malformed storage and inexact identities without interpreting legacy or unknown formats" in {
      Given("an encoded selection with invalid closed envelopes, source types and selection identities")
      val prefix = "sm-workflow.goal-phase-document-selection.v1:"
      val wire = io.circe.parser.parse(D.encodeStorageC(_selection).toOption.get.stripPrefix(prefix)).toOption.get
      val payload = wire.hcursor.downField("payload").focus.get
      val variants = Vector(wire.mapObject(_.add("extra", Json.True)),
        wire.mapObject(_.add("schemaVersion", Json.fromString("unknown"))),
        wire.mapObject(_.remove("payload")), wire.mapObject(_.add("payload", Json.Null))) ++
        Vector(payload.mapObject(_.add("permission", Json.True)), payload.mapObject(_.remove("selectionIdentity")),
          payload.mapObject(_.add("source", Json.Null)), payload.mapObject(_.add("participantIdentity", Json.fromInt(1))),
          payload.mapObject(_.add("selectionIdentity", Json.fromString(" selected-phase"))),
          payload.mapObject(_.add("source", Json.obj("phasePath" -> Json.fromString("../phase.md"),
            "checklistPath" -> Json.fromString("docs/checklist.md")))),
          payload.mapObject(_.add("source", Json.obj("phasePath" -> Json.fromString("docs/phase.md"),
            "checklistPath" -> Json.fromString("docs/checklist.md"), "workspace" -> Json.fromString("foreign")))))
          .map(value => wire.mapObject(_.add("payload", value)))
      val identities = Vector[String](null, "", " ", " participant", "participant\n")
      When("the finite document-selection decoder receives those values and foreign prefixes")
      val decoded = variants.map(value => D.decodeStorageC(prefix + value.noSpaces)) ++
        Vector[String](null, "", prefix + "{", wire.noSpaces, "unknown:" + wire.noSpaces,
          "sm-workflow.goal-phase-selected-plan.v1:" + wire.noSpaces).map(D.decodeStorageC)
      Then("all invalid storage fails and exact identities are required before storage")
      decoded.foreach(_.isSuccess shouldBe false)
      identities.foreach { value =>
        D.encodeStorageC(_selection.copy(selectionIdentity = value)).isSuccess shouldBe false
        D.encodeStorageC(_selection.copy(participantIdentity = value)).isSuccess shouldBe false
      }
      D.encodeStorageC(null).isSuccess shouldBe false
      D.encodeStorageC(_selection.copy(source = null)).isSuccess shouldBe false
    }

    "retain legacy selected Plan and native Start payload shapes when document data is absent" in {
      Given("legacy selected Plan and native Start data, plus an additive captured document binding")
      val selected = GoalPhaseNativeRun.SelectedPlan("selected-phase", "participant", _plan)
      val native = GoalPhaseNativeRun.Payload("participant", "/owned/repository", "main", _plan,
        O.Snapshot("a" * 40, "b" * 40))
      val bound = D.Bound(_source, Vector(GoalPhaseDocumentPlan.Binding("step-1", Vector("ITEM-1"))),
        Vector(GoalPhaseDocumentPlan.Item("ITEM-1", "Retain semantic acceptance", true)))
      When("legacy and additive data pass their existing versioned codecs")
      val selectedwire = GoalPhaseSelectedPlanJsonV1.encodeC(selected).toOption.get
      val nativewire = GoalPhaseNativeRun.codec.encode(native)
      val capturedselected = GoalPhaseSelectedPlanJsonV1.decodeC(
        GoalPhaseSelectedPlanJsonV1.encodeC(selected.copy(documents = Some(bound))).toOption.get)
      val capturednative = GoalPhaseNativeRun.codec.decode(GoalPhaseNativeRun.codec.encode(native.copy(documents = Some(bound))))
      Then("old payloads omit documents and decode to None while captured data roundtrips unchanged")
      io.circe.parser.parse(selectedwire).toOption.get.hcursor.downField("payload").focus.get.asObject.get
        .keys.toSet shouldBe Set("selectionIdentity", "participantIdentity", "plan")
      nativewire.hcursor.downField("payload").focus.get.asObject.get.keys.toSet shouldBe
        Set("participantIdentity", "root", "branch", "plan", "base")
      GoalPhaseSelectedPlanJsonV1.decodeC(selectedwire).toOption shouldBe Some(selected)
      GoalPhaseNativeRun.codec.decode(nativewire) shouldBe Right(native)
      capturedselected.toOption.get.documents shouldBe Some(bound)
      capturednative.toOption.get.documents shouldBe Some(bound)
    }
  }
}
