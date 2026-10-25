package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.GoalPhaseStartInput
import NativeWorkflowStartInput.*
import ProfileStartAdmission.{Selection, SelectionOrigin}

/**
 * @since   Oct. 11, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class NativeWorkflowStartInputSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"Start input failed: $other")
  }
  private val goalJson = Json.obj("phaseIdentity" -> Json.fromString("phase-2"), "repositoryIdentity" -> Json.fromString("repository"))
  private val splitJson = Json.obj("sourcePhaseIdentity" -> Json.fromString("phase-2"), "invocationMode" -> Json.fromString("PREVIEW"))
  private val repositoryJson = Json.obj("repositoryIdentity" -> Json.fromString("repository"), "localBranch" -> Json.fromString("main"),
    "trackingRemote" -> Json.fromString("origin"), "trackingBranch" -> Json.fromString("main"),
    "remoteEndpointIdentity" -> Json.fromString("upstream"), "syncMode" -> Json.fromString("FULL_STATE"))
  private val business: Vector[(Kind, Json, BusinessInput)] = Vector(
    (Kind.GoalPhase, goalJson, Goal(GoalPhaseStartInput("phase-2", "repository"))),
    (Kind.SplitPhase, splitJson, Split("phase-2", "PREVIEW", None)),
    (Kind.RepositorySync, repositoryJson, Repository("repository", "main", "origin", "main", "upstream", "FULL_STATE")))
  private def definition(kind: Kind): DefinitionBinding = kind match {
    case Kind.GoalPhase => take(GoalPhaseRuntimeCatalog.loadC()).definition
    case Kind.SplitPhase => take(SplitPhaseDefinitionBinding.bindC)
    case Kind.RepositorySync => take(RepositorySyncDefinitionBinding.bindC)
  }
  private def record(kind: Kind, value: Json, binding: DefinitionBinding, identity: String = "selected-1"): Selection =
    Selection(identity, kind.operation, binding.workflowIdentity, binding.workflowRevision, "actual-principal",
      SelectionOrigin.ExplicitHuman, "stored-invocation", None, Some(TypedValue(kind.inputType, value)))

  "Minimal Start business inputs" should {
    "consume all three business shapes without participant, key or saved control context" in {
      Given("Goal, Split and Repository business parameters with no command or control envelope")
      val cases = business
      When("the selected Start decodes its consumed values")
      val actual = cases.map { (kind, json, _) => take(decodeC(kind, json)) }
      Then("the resulting inputs contain only the requested business choices")
      actual shouldBe cases.map(_._3)
    }

    "ignore unused legacy-looking fields rather than requiring or interpreting them" in {
      Given("business inputs carrying unused actor, key, authority and policy-version diagnostics")
      val cases = business.map { (kind, json, expected) => (kind, json.mapObject(_.add("participantIdentity", Json.False)
        .add("idempotencyKey", Json.arr()).add("sourceAuthorityReference", Json.Null)
        .add("repositoryPolicyVersion", Json.Null)), expected) }
      When("each current business codec consumes its required fields")
      val actual = cases.map { (kind, json, _) => take(decodeC(kind, json)) }
      Then("unused values neither reject the input nor change the requested target")
      actual shouldBe business.map(_._3)
    }

    "report missing or type-invalid consumed business values" in {
      Given("one genuinely required target field for each Start")
      val fields = Vector("phaseIdentity", "sourcePhaseIdentity", "repositoryIdentity")
      val invalid = business.zip(fields).flatMap { (entry, field) =>
        val (kind, json, _) = entry
        Vector(Json.fromJsonObject(json.asObject.get.remove(field)), json.mapObject(_.add(field, Json.fromInt(1))),
          json.mapObject(_.add(field, Json.fromString(" ")))).map(kind -> _)
      }
      When("the operation consumes the missing or malformed target")
      val actual = invalid.map { (kind, json) => decodeC(kind, json) }
      Then("each fails as an input error without a second unchanged-state check")
      actual.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }

  "Current Start selection resolution" should {
    "resolve each actual target and principal without caller selection, participant or key echoes" in {
      Given("one recorded human choice per workflow with independent diagnostic and saved-context fields")
      val cases = business.map { (kind, json, input) =>
        val binding = definition(kind)
        val stored = json.mapObject(_.add("diagnostic", Json.arr()).add("sourceAuthorityReference", Json.fromString("stored-authority")))
        (input, binding, record(kind, stored, binding))
      }
      When("the current principal requests the business input without a selector")
      val actual = cases.map { (input, binding, selected) =>
        take(admitSelectedC(selected, input, "actual-principal", binding))
      }
      Then("each direct business entry authorizes the actual requested target without caller control echoes")
      actual shouldBe Vector.fill(cases.size)(())
    }

    "not select another actor, target, workflow or a diagnostic claim of human consent" in {
      Given("foreign or nonhuman records instead of the requested Goal selection")
      val binding = definition(Kind.GoalPhase)
      val selected = record(Kind.GoalPhase, goalJson, binding)
      val records = Vector(selected.copy(participantIdentity = "another-principal"),
        selected.copy(workflowIdentity = WorkflowDefinitionIdentity("AnotherWorkflow")),
        selected.copy(origin = SelectionOrigin.Skill),
        selected.copy(selectedInput = Some(TypedValue(Kind.GoalPhase.inputType,
          goalJson.mapObject(_.add("phaseIdentity", Json.fromString("another-phase")))))))
      When("the actual principal requests the original Goal business target")
      val actual = records.map(admitSelectedC(_, business.head._3, "actual-principal", binding))
      Then("none is an associated Start selection")
      actual.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "address each family, participant and business target with a direct host-entry key" in {
      Given("the three actual business inputs and another participant requesting the same target")
      val keys = business.map { (_, _, input) => NativeWorkflowStartBusinessKey(input, "actual-principal") }
      When("Workflow constructs the host-entry address from its consumed business values")
      val anotheractor = NativeWorkflowStartBusinessKey(business.head._3, "another-principal")
      val anothertarget = NativeWorkflowStartBusinessKey(Goal(GoalPhaseStartInput("phase-3", "repository")), "actual-principal")
      Then("separate families, participants and targets do not require candidate lookup or a public selector")
      keys.distinct.size shouldBe 3
      keys.head should not be anotheractor
      keys.head should not be anothertarget
      NativeWorkflowStartBusinessKey(business.head._3, "actual-principal") shouldBe keys.head
    }

    "use a genuine proposal choice without requiring its saved authority or source snapshot" in {
      Given("Split selections for different proposal choices with the same source phase")
      val binding = definition(Kind.SplitPhase)
      val first = record(Kind.SplitPhase, splitJson, binding)
      val proposal = splitJson.mapObject(_.add("proposalReference", Json.fromString("proposal-2")))
      val second = record(Kind.SplitPhase, proposal, binding, "proposal-selection")
      val input = take(decodeC(Kind.SplitPhase, proposal))
      val malformed = splitJson.mapObject(_.add("proposalReference", Json.fromBoolean(true)))
      When("the caller requests that actual proposal without an internal allocation command")
      val chosen = admitSelectedC(second, input, "actual-principal", binding)
      val wrongtarget = admitSelectedC(first, input, "actual-principal", binding)
      val invalid = decodeC(Kind.SplitPhase, malformed)
      Then("the proposal selects its owning record without redundant control input, and its consumed type must be valid")
      chosen.isSuccess shouldBe true
      wrongtarget shouldBe a[Consequence.Failure[?]]
      invalid shouldBe a[Consequence.Failure[?]]
    }
  }
}
