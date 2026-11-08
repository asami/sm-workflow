/**
 * @since   Nov.  6, 2026
 * @version Nov.  8, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, StandardOpenOption}
import java.time.Instant
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhaseDependencyScope as D

/** Public-operation regression: fixture-owned answers are not genuine Human consent.
  * Cross-version installed-runtime reflection is a separate qualification. */
@org.scalatest.tags.Slow
@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalPhaseDependencyApprovalLineageCliSpec extends AnyWordSpec with Matchers {
  import NativeCallerExecutionFixture.{json, take}

  "Aliased native Human completions" should {
    "preserve each fixture answer and continue the same issued Session through a subsequent inventory receipt" in {
      val scenario = F.freshDependency("dependency-approval-lineage", bindingTransform = _.copy(
        paths = Vector("dependency.scala", "dependency-spec.scala")))
      val task = scenario.ownTask("lineage-task")
      val started = scenario.start().business[V.GoalPhaseStartResult]
      scenario.retain(task, started)
      val initial = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      val reference = V.WorkflowArtifactReference("fixture-aliased-human-source", "1")
      val internalRef = ContextReference(reference.identity, reference.revision)
      val binding = scenario.host.goal.dependencyValidation.get
      val first = D.Proposal("fixture-proposal", "1", "IMPLEMENTATION", binding.requester, binding.dependency,
        ContextReference("fixture-usage", "1"), "Fixture bounded linked work", Vector("dependency.scala"),
        Vector("Preserve fixture callers"), Vector(ContextReference("fixture-dependent-work", "1")), Vector.empty,
        authorizationBoundary = None)
      val boundary = D.AuthorizationBoundary(D.authorizationSchema, ContextReference(first.identity, first.revision),
        Vector("edit-source", "validate"), Vector.empty)
      def save(name: String, content: String): String = {
        val path = scenario.root.resolve(name + ".json")
        Files.writeString(path, content, UTF_8, StandardOpenOption.CREATE_NEW)
        path.toUri.toASCIIString
      }
      def propose(work: V.GoalPhaseAdvanceResult, proposal: D.Proposal, name: String): F.Result = {
        val result = V.GoalPhaseWorkResult("decision-needed", "Fixture dependency direction", Vector.empty,
          None, None, None, None, None, D.publicProposal(Some(proposal)), None, None,
          dependencyValidation = None, diagnosticRepair = None)
        val submitted = scenario.call("command", work.submitOperation.get, Vector("--sessionId", work.submitSessionId.get,
          "--result", json(result.toRecord()).noSpaces, "--resultReference",
          json(V.WorkflowArtifactReference("fixture-" + name, "1").toRecord()).noSpaces))
          .business[V.GoalPhaseWorkSubmissionResult]
        submitted.accepted shouldBe true
        scenario.advance(task)
      }
      def answer(question: V.GoalPhaseAdvanceResult, output: F.Result, name: String): Unit = {
        val answer = V.GoalPhaseOrderedDecisionAnswer("ApproveDependencyScope", "Fixture-owned answer " + name)
        val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-owned-human-answer"), None, None)
        val input = V.WorkflowEvidencePreparationInput("GoalPhase", "human",
          save(name + "-question", Files.readString(output.invocation.stdout, UTF_8)),
          save(name + "-answer", json(answer.toRecord()).noSpaces),
          save(name + "-reference", json(reference.toRecord()).noSpaces),
          save(name + "-execution", json(evidence.toRecord()).noSpaces),
          "normal-development-host", Instant.now().minusSeconds(60).toString,
          "entity:smworkflow.cli.host_evidence", Some("participant"))
        val prepared = scenario.prepareHostInput("prepareEvidence", input.toRecord())
        prepared.exitCode shouldBe 0
        scenario.selectPreparedImport(prepared)
        val submitted = scenario.call("command", question.submitOperation.get, Vector("--sessionId", question.submitSessionId.get,
          "--answer", json(answer.toRecord()).noSpaces, "--resultReference", json(reference.toRecord()).noSpaces,
          "--executionEvidence", json(evidence.toRecord()).noSpaces)).business[V.GoalPhaseOrderedDecisionSubmissionResult]
        submitted.accepted shouldBe true
      }
      val firstOutput = propose(initial, first, "first-proposal")
      val firstQuestion = firstOutput.business[V.GoalPhaseAdvanceResult]
      firstQuestion.kind shouldBe "Human"
      answer(firstQuestion, firstOutput, "first")
      val firstWork = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      val second = first.copy(revision = "2", paths = Vector("dependency.scala", "dependency-spec.scala"),
        authorizationBoundary = Some(boundary.copy(permittedOperations = Vector("edit-source"))),
        scopeUpdate = Some(D.ScopeUpdate(internalRef, "Fixture adoption adds its necessary test")))
      val secondOutput = propose(firstWork, second, "second-proposal")
      val secondQuestion = secondOutput.business[V.GoalPhaseAdvanceResult]
      secondQuestion.kind shouldBe "Human"
      secondQuestion.human.get.work shouldBe firstWork.work.get
      answer(secondQuestion, secondOutput, "second")
      val resumed = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      resumed.kind shouldBe "Request"
      resumed.sessionId shouldBe initial.sessionId
      resumed.work.get.decisionContext.get.decisionReference shouldBe Some(reference)
      take(D.fromPublicProposalC(resumed.work.get.decisionContext.get.requestingResult.dependencyProposal)) shouldBe Some(second)
      scenario.advance(task).business[V.GoalPhaseAdvanceResult] shouldBe resumed
      val inventory = second.copy(revision = "3", reason = "Fixture inventory refresh",
        scopeUpdate = Some(D.ScopeUpdate(internalRef, "Fixture unchanged boundary inventory")))
      val inventoryOutput = propose(resumed, inventory, "inventory")
      val continued = inventoryOutput.business[V.GoalPhaseAdvanceResult]
      continued.kind shouldBe "Request"
      continued.sessionId shouldBe initial.sessionId
      continued.work.get.decisionContext.get.decisionReference shouldBe Some(reference)
      continued.work.get.decisionContext.get.inventoryContinuation should not be empty
      scenario.advance(task).business[V.GoalPhaseAdvanceResult] shouldBe continued
    }
  }
}
