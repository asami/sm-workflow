package org.simplemodeling.textus.smworkflow

import java.nio.file.{Files, Paths}
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentDescriptorLoader, ComponentId}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
final class ComponentPackagingDescriptorSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def readJson(path: String): Json =
    parse(Files.readString(Paths.get(path))).fold(error => fail(error.message), identity)

  private def array(value: Json, field: String): Vector[Json] =
    value.hcursor.get[Vector[Json]](field).fold(error => fail(error.message), identity)

  private def text(value: Json, field: String): String =
    value.hcursor.get[String](field).fold(error => fail(error.message), identity)

  "The authored packaging descriptor" should {
    "provide the canonical project identity without granting runtime authority" in {
      Given("the style-less CML project and its canonical schema-3 CAR identity descriptor")
      val source = Paths.get("src/main/car/component-descriptor.json")

      When("the framework loads the actual descriptor through its standard loader")
      val descriptors = ComponentDescriptorLoader.load(source) match {
        case Consequence.Success(value) => value
        case failure => fail(s"Packaging descriptor rejected: $failure")
      }

      Then("exactly the project Component identity and version are retained")
      descriptors.size shouldBe 1
      val descriptor = descriptors.head
      descriptor.schemaVersion shouldBe Some(3)
      descriptor.componentId shouldBe Some(ComponentId("org.simplemodeling.textus.SmWorkflow"))
      descriptor.version shouldBe Some("0.1.0-SNAPSHOT")

      And("the descriptor supplies no settings or extensions that manufacture storage or user authority")
      descriptor.config shouldBe Map.empty
      descriptor.extensions shouldBe Map.empty
      descriptor.extensionBindings.fields shouldBe empty
      descriptor.componentStyleSnapshot shouldBe None
    }
  }

  "The authored ABI manifest" should {
    "describe the minimal participant Operations and genuine capability contracts" in {
      Given("the source-managed schema-v2 ABI used for CAR distribution")
      val abi = readJson("src/main/car/abi-manifest.json")

      When("a consumer reads the exported Operations and value fields")
      val exports = abi.hcursor.downField("abi").downField("exports").focus.getOrElse(fail("Missing exports"))
      val operations = array(exports, "operations")
      val types = array(exports, "types")
      def names(service: String): Vector[String] =
        operations.filter(op => text(op, "service") == service).map(op => text(op, "name"))
      def valueFields(name: String): Vector[String] =
        array(types.find(value => text(value, "name") == name && text(value, "kind") == "value")
          .getOrElse(fail(s"Missing value $name")), "fields").map(field => text(field, "name"))
      def operation(service: String, name: String): Json =
        operations.find(op => text(op, "service") == service && text(op, "name") == name)
          .getOrElse(fail(s"Missing Operation $service.$name"))

      Then("the distribution identifies this component and exactly the normal four, six and five participant Operations")
      text(abi, "format") shouldBe "cozy.car.abi-manifest.v2"
      abi.hcursor.downField("component").get[String]("namespace") shouldBe Right("org.simplemodeling.textus")
      abi.hcursor.downField("component").get[String]("id") shouldBe Right("SmWorkflow")
      abi.hcursor.downField("component").get[String]("version") shouldBe Right("0.1.0-SNAPSHOT")
      abi.hcursor.downField("abi").get[Int]("version") shouldBe Right(1)
      names("GoalPhaseService") should contain theSameElementsAs Vector(
        "startGoalPhase", "advanceGoalPhase", "submitGoalPhaseWorkResult", "submitGoalPhaseOrderedDecision")
      names("SplitPhaseService") should contain theSameElementsAs Vector(
        "startSplitPhase", "advanceSplitPhase", "submitPartitionSemanticResult",
        "submitHandoffScopeResult", "submitReasoningResult", "submitSplitHumanDirection")
      names("RepositorySyncService") should contain theSameElementsAs Vector(
        "startRepositorySync", "advanceRepositorySync", "submitRepositorySyncWorkResult",
        "submitNativeMergeReview", "submitRepositoryConflictDecision")

      And("nominal input types describe the consumed business values, not additional control envelopes")
      val participantServices = Set("GoalPhaseService", "SplitPhaseService", "RepositorySyncService")
      val participantInputs = operations.filter(op => participantServices.contains(text(op, "service")))
        .map(op => text(op, "name") -> op.hcursor.get[Option[String]]("input")
          .fold(error => fail(error.message), identity)).toMap
      participantInputs shouldBe Map(
        "advanceGoalPhase" -> "GoalPhaseAdvanceInput",
        "advanceSplitPhase" -> "SplitPhaseAdvanceInput",
        "advanceRepositorySync" -> "RepositorySyncAdvanceInput",
        "startGoalPhase" -> "GoalPhaseStartInput",
        "submitGoalPhaseWorkResult" -> "GoalPhaseWorkResult",
        "submitGoalPhaseOrderedDecision" -> "GoalPhaseOrderedDecisionAnswer",
        "startSplitPhase" -> "SplitPhaseStartInput",
        "submitPartitionSemanticResult" -> "SplitPhasePartitionSemanticResult",
        "submitHandoffScopeResult" -> "SplitPhaseScopeSemanticResult",
        "submitReasoningResult" -> "SplitPhaseReasoningSemanticResult",
        "submitSplitHumanDirection" -> "SplitPhaseHumanDirectionAnswer",
        "startRepositorySync" -> "RepositorySyncStartInput",
        "submitRepositorySyncWorkResult" -> "RepositorySyncConflictResult",
        "submitNativeMergeReview" -> "RepositorySyncNativeMergeReviewResult",
        "submitRepositoryConflictDecision" -> "RepositorySyncHumanConflictAnswer")
          .view.mapValues(value => Some(value)).toMap
      valueFields("GoalPhaseAdvanceInput") should contain theSameElementsAs Vector("sessionIdentity", "instanceIdentity")
      valueFields("SplitPhaseAdvanceInput") should contain theSameElementsAs Vector("sessionIdentity", "instanceIdentity", "contextIdentity")
      valueFields("RepositorySyncAdvanceInput") should contain theSameElementsAs Vector("sessionIdentity", "instanceIdentity")
      valueFields("GoalPhaseStartInput") should contain theSameElementsAs Vector("phaseIdentity", "repositoryIdentity")
      valueFields("SplitPhaseStartInput") should contain theSameElementsAs Vector(
        "sourcePhaseIdentity", "invocationMode", "proposalReference")
      valueFields("RepositorySyncStartInput") should contain theSameElementsAs Vector(
        "repositoryIdentity", "localBranch", "trackingRemote", "trackingBranch", "remoteEndpointIdentity", "syncMode")
      types.map(value => text(value, "name")) should not contain "GoalPhaseStartCommand"
      types.map(value => text(value, "name")) should not contain "SplitPhaseStartCommand"
      types.map(value => text(value, "name")) should not contain "RepositorySyncStartCommand"

      And("semantic, Human and deterministic admission contracts retain their actual request and result types")
      val semantic = operation("SplitSemanticService", "assessPartitionSemanticInputs")
      text(semantic, "input") shouldBe "SplitPhasePartitionSemanticRequest"
      text(semantic, "output") shouldBe "SplitPhasePartitionSemanticResult"
      text(operation("SplitHumanService", "presentReasoningHumanDirection"), "input") shouldBe "SplitPhaseReasoningHumanDirectionRequest"
      text(operation("RepositoryConflictService", "resolveRepositoryMergeConflicts"), "output") shouldBe "RepositorySyncConflictResult"
      text(operation("RepositorySyncSemanticAdmissionService", "admitNativeMergeReview"), "input") shouldBe "RepositorySyncNativeMergeReviewRequest"
      text(operation("GoalPhaseSemanticAdmissionService", "admitOrderedStepReview"), "output") shouldBe "GoalPhaseWorkSubmissionResult"
    }
  }
}
