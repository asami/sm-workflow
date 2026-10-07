package org.simplemodeling.textus.smworkflow

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import io.circe.Json
import io.circe.parser.parse

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{CandidateAdmissionProducerAbi, CandidateWorkflowAbi, WorkflowInstancePersistence}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseCncfConsumerSpec extends AnyWordSpec with Matchers {
  private def resource(path: String): String = {
    Files.readString(Paths.get("src/test/resources" + path), StandardCharsets.UTF_8)
  }

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"CNCF rejected the generated GoalPhase ABI: $failure")
  }

  private def json(value: String): Json =
    parse(value).fold(error => fail(s"Invalid generated ABI JSON: $error"), identity)

  private def selectedArtifact(value: String, field: String, identityPath: Vector[String]): Json = {
    val root = json(value)
    val entries = root.hcursor.downField(field).as[Vector[Json]]
      .fold(error => fail(s"Missing generated $field: $error"), identity)
    val selected = entries.filter { entry =>
      identityPath.foldLeft(entry.hcursor)((cursor, name) => cursor.downField(name).success
        .getOrElse(fail(s"Missing generated identity field: $name"))).as[String]
        .fold(error => fail(s"Invalid generated identity: $error"), identity) == "GoalPhaseWorkflow"
    }
    selected.size shouldBe 1
    root.mapObject(_.add(field, Json.fromValues(selected)))
  }

  private val workflowJson = resource("/workflow/goal-phase-workflow-abi.json")
  private val sidecarJson = resource("/workflow/goal-phase-candidate-admission-producer-abi.json")

  "GoalPhase Cozy ABI fixtures" should {
    "match the current producer's complete selected GoalPhase output including source provenance" in {
      // Other workflows and services may evolve without changing this fixture.
      // Compare every selected field, not a digest of the entire CML file.
      val officialWorkflow = Files.readString(Paths.get("target/cozy/statemachine-workflow-abi.json"),
        StandardCharsets.UTF_8)
      val officialProducer = Files.readString(Paths.get("target/cozy/candidate-admission-producer-abi.json"),
        StandardCharsets.UTF_8)
      selectedArtifact(officialWorkflow, "workflows", Vector("identity")) shouldBe json(workflowJson)
      selectedArtifact(officialProducer, "models", Vector("model", "compositeStateMachineIdentity")) shouldBe
        json(sidecarJson)
    }

    "retain selected policy and source changes rather than hiding them during projection" in {
      val workflowPath = Vector("identity")
      val producerPath = Vector("model", "compositeStateMachineIdentity")
      selectedArtifact(workflowJson.replace("\"Pending\"", "\"ForeignPending\""),
        "workflows", workflowPath) should not be json(workflowJson)
      selectedArtifact(sidecarJson.replace("\"step-closure-review\"", "\"foreign-policy\""),
        "models", producerPath) should not be json(sidecarJson)
      selectedArtifact(sidecarJson.replaceAll("\"line\":\\d+", "\"line\":999"),
        "models", producerPath) should not be json(sidecarJson)
    }

    "be admitted together by CNCF and bind to its WorkflowInstance identity" in {
      val sidecar = take(CandidateAdmissionProducerAbi.parseC(sidecarJson))
      val workflow = take(CandidateWorkflowAbi.parseC(
        workflowJson, sidecar, "src/main/cozy/sm-workflow.cml"))
      workflow.workflow.identity shouldBe "GoalPhaseWorkflow"
      workflow.workflow.requiredSpi.map(_.capability) shouldBe Vector("review-step-capability", "repair-step-capability",
        "human-decision-capability", "goal-phase-implementation-capability", "goal-phase-repair-capability",
        "goal-phase-step-review-capability", "goal-phase-review-capability", "goal-phase-ordered-human-capability")
      val binding = take(WorkflowInstancePersistence.bindCandidateDefinitionC(workflow))
      binding.workflowIdentity.value shouldBe "GoalPhaseWorkflow"
      binding.workflowRevision.value shouldBe "goal-phase-ordered-run-v4"
      binding.validateC shouldBe Consequence.success(binding)
    }

    "reject a mismatched Workflow/sidecar pair" in {
      val sidecar = take(CandidateAdmissionProducerAbi.parseC(sidecarJson))
      CandidateWorkflowAbi.parseC(
        workflowJson.replace("StepReviewService", "UnknownReviewService"),
        sidecar,
        "src/main/cozy/sm-workflow.cml"
      ) shouldBe a[Consequence.Failure[?]]
    }
  }
}
