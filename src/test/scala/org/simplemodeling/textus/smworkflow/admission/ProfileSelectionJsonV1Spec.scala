package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import ProfileStartAdmission.{Binding, Selection, SelectionOrigin, SelectionPort}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class ProfileSelectionJsonV1Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val selection = Selection("selection-1", StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"),
    WorkflowDefinitionIdentity("GoalPhaseWorkflow"), WorkflowDefinitionRevision("1"), "human-1",
    SelectionOrigin.ExplicitHuman, "start-1", Some("recommendation-1"))
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"Selection codec failed: $other")
  }
  private def json = parse(take(ProfileSelectionJsonV1.encodeC(selection))).toOption.get

  "Profile selection host record codec" should {
    "retain explicit typed input facts without reconstructing them from workflow identity" in {
      Given("a human selection with explicit input facts")
      val original = selection.copy(selectedInput = Some(TypedValue("GoalPhaseStartInput", Json.obj(
        "phaseIdentity" -> Json.fromString("phase-2"), "repositoryIdentity" -> Json.fromString("repository")))))
      When("the host records and reads the selection")
      val restored = take(ProfileSelectionJsonV1.decodeStorageC(take(ProfileSelectionJsonV1.encodeStorageC(original))))
      val absent = take(ProfileSelectionJsonV1.decodeC(take(ProfileSelectionJsonV1.encodeC(selection))))
      Then("explicit typed facts survive and absent input remains absent")
      restored shouldBe original
      absent.selectedInput shouldBe None
    }

    "reject missing or type-invalid consumed typed payloads" in {
      Given("a record with missing, empty, null or nonobject typed-input facts")
      val malformed = Vector(Json.obj("typeIdentity" -> Json.fromString("GoalPhaseStartInput")),
        Json.obj("typeIdentity" -> Json.fromString(" "), "value" -> Json.obj()),
        Json.obj("typeIdentity" -> Json.fromString("GoalPhaseStartInput"), "value" -> Json.Null), Json.fromString("input"))
      val incomplete = Vector(selection.copy(selectedInput = null), selection.copy(selectedInput = Some(null)),
        selection.copy(selectedInput = Some(TypedValue("Input", Json.Null))))
      When("the codec consumes or writes those incomplete facts")
      val decoded = malformed.map(value => ProfileSelectionJsonV1.decodeC(json.mapObject(_.add("selectedInput", value)).noSpaces))
      val encoded = incomplete.map(ProfileSelectionJsonV1.encodeC)
      Then("consumed missing and invalid types fail without using diagnostic fields as controls")
      decoded.foreach(_ shouldBe a[Consequence.Failure[?]])
      encoded.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "retain every origin and optional recommendation without upgrading authority" in {
      Given("recorded selection facts from every supported origin")
      for (origin <- SelectionOrigin.values; recommendation <- Vector(None, Some("recommendation-1"))) {
        val original = selection.copy(origin = origin, recommendationReference = recommendation)
        When("the host serializes and reloads the record")
        val restored = take(ProfileSelectionJsonV1.decodeC(take(ProfileSelectionJsonV1.encodeC(original))))
        Then("all facts, including the original nonhuman origin, survive exactly")
        restored shouldBe original
        take(ProfileSelectionJsonV1.decodeStorageC(take(ProfileSelectionJsonV1.encodeStorageC(original)))) shouldBe original
      }
    }

    "leave human authority evaluation to the existing admission gate" in {
      Given("a decoded host record rather than authority inferred by deserialization")
      val binding = Binding(selection.startOperation, selection.workflowIdentity, selection.workflowRevision, "Input")
      SelectionOrigin.values.foreach { origin =>
        val restored = take(ProfileSelectionJsonV1.decodeC(take(ProfileSelectionJsonV1.encodeC(selection.copy(origin = origin)))))
        val port = new SelectionPort {
          def loadC(id: String) = Consequence.success(Some(restored))
        }
        When("Start evaluates the exact workflow, participant and idempotency correlation")
        val admitted = ProfileStartAdmission.admitC(binding, TypedValue("Input", "payload"),
          restored.selectionId, restored.participantIdentity, restored.idempotencyKey, port)
        Then("only an explicitly human record can authorize that invocation")
        if (origin == SelectionOrigin.ExplicitHuman) admitted shouldBe a[Consequence.Success[?]]
        else admitted shouldBe a[Consequence.Failure[?]]
      }
    }

    "reject missing consumed fields and incompatible schema" in {
      Given("a valid persisted host record")
      val root = json.asObject.get
      When("each required field is omitted or the consumed schema or operation changes shape")
      val variants = root.keys.toVector.map(name => Json.fromJsonObject(root.remove(name))) ++ Vector(
        json.mapObject(_.add("schemaVersion", Json.fromString("foreign"))),
        json.mapObject(_.add("startOperation", Json.obj("service" -> Json.fromString("GoalPhaseService")))))
      Then("none can be interpreted as a valid selection record")
      variants.foreach(value => ProfileSelectionJsonV1.decodeC(value.noSpaces) shouldBe a[Consequence.Failure[?]])
    }

    "ignore extra record and typed-input fields without upgrading human authority" in {
      Given("a nonhuman selection carrying unused approval-looking diagnostics")
      val input = Json.obj("phaseIdentity" -> Json.fromString("phase-2"), "repositoryIdentity" -> Json.fromString("repository"))
      val original = selection.copy(origin = SelectionOrigin.Skill,
        selectedInput = Some(TypedValue("GoalPhaseStartInput", input)))
      val decorated = parse(take(ProfileSelectionJsonV1.encodeC(original))).toOption.get.mapObject(_.add("authority", Json.True)
        .add("startOperation", Json.obj("service" -> Json.fromString("GoalPhaseService"),
          "operation" -> Json.fromString("startGoalPhase"), "diagnostic" -> Json.arr()))
        .add("selectedInput", Json.obj("typeIdentity" -> Json.fromString("GoalPhaseStartInput"),
          "value" -> input, "approved" -> Json.True)))
      When("the codec consumes declared fields and Start evaluates the actual origin")
      val restored = take(ProfileSelectionJsonV1.decodeC(decorated.noSpaces))
      val binding = Binding(selection.startOperation, selection.workflowIdentity, selection.workflowRevision, "GoalPhaseStartInput")
      val port = new SelectionPort {
        def loadC(id: String) = Consequence.success(Some(restored))
      }
      val admitted = ProfileStartAdmission.admitC(binding, TypedValue("GoalPhaseStartInput", input), restored.selectionId,
        restored.participantIdentity, restored.idempotencyKey, port)
      Then("diagnostics are ignored and the actual nonhuman origin cannot acquire Start authority")
      restored shouldBe original
      admitted shouldBe a[Consequence.Failure[?]]
    }

    "reject coerced values, unknown origins and malformed optional references" in {
      Given("a valid record whose values must remain typed")
      When("string facts are blank or nonstrings and optional references have invalid types")
      val names = Vector("selectionId", "workflowIdentity", "workflowRevision", "participantIdentity", "idempotencyKey")
      val variants = names.flatMap(name => Vector(Json.Null, Json.fromInt(1), Json.fromBoolean(true), Json.fromString(" "))
        .map(value => json.mapObject(_.add(name, value)))) ++ Vector(
        json.mapObject(_.add("origin", Json.fromString("Approved"))),
        json.mapObject(_.add("recommendationReference", Json.fromString(" "))),
        json.mapObject(_.add("recommendationReference", Json.fromInt(1))))
      Then("decoding fails without defaults or normalization")
      variants.foreach(value => ProfileSelectionJsonV1.decodeC(value.noSpaces) shouldBe a[Consequence.Failure[?]])
      Vector(null, "", "[]", "null").foreach(raw =>
        ProfileSelectionJsonV1.decodeC(raw) shouldBe a[Consequence.Failure[?]])
      Vector(null, "", take(ProfileSelectionJsonV1.encodeC(selection)),
        "foreign:" + take(ProfileSelectionJsonV1.encodeC(selection))).foreach(raw =>
        ProfileSelectionJsonV1.decodeStorageC(raw) shouldBe a[Consequence.Failure[?]])
    }

    "reject incomplete in-memory records before encoding" in {
      Given("absent or malformed host facts")
      val invalid = Vector(null, selection.copy(selectionId = " "), selection.copy(startOperation = null),
        selection.copy(workflowIdentity = null), selection.copy(workflowRevision = null),
        selection.copy(participantIdentity = ""), selection.copy(origin = null),
        selection.copy(idempotencyKey = null), selection.copy(recommendationReference = null),
        selection.copy(recommendationReference = Some(" ")))
      When("the host attempts to persist them")
      Then("no partial JSON or inferred human authority is produced")
      invalid.foreach(value => ProfileSelectionJsonV1.encodeC(value) shouldBe a[Consequence.Failure[?]])
    }
  }
}
