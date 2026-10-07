package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.simplemodeling.textus.smworkflow.value.StepCloseRequest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseReviewSnapshotSpec extends AnyWordSpec with Matchers {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"Review snapshot fixture rejected: $failure")
  }
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString.stripSuffix("\n") finally source.close()
  }
  private val artifact = take(CandidateAdmissionProducerAbi.parseC(
    resource("/workflow/goal-phase-candidate-admission-producer-abi.json")))
  private val definition = take(CandidateWorkflowAbi.parseC(
    resource("/workflow/goal-phase-workflow-abi.json"), artifact, "src/main/cozy/sm-workflow.cml"))
  private val binding = take(bindCandidateDefinitionC(definition))
  private val spi = artifact.models.head.requiredSpi.head
  private val operation = StateMachineRequiredOperation(
    StateMachineRequiredOperationIdentity(spi.identity.value), spi.actionIdentity.value,
    StateMachineOperationIdentity(spi.operation.service.value, spi.operation.name.value),
    spi.operation.inputType.map(x => StateMachineInputTypeReference(x.value)),
    spi.operation.resultType.map(x => StateMachineResultTypeReference(x.value)),
    StateMachineRequiredOperationMetadata(
      ContextContract("snapshot-context", Vector.empty, Vector.empty),
      CompletionContract("snapshot-completion", Vector.empty),
      EvidenceContract("snapshot-evidence", Vector.empty), Vector.empty))
  private val initial = InstanceRecord(InstanceIdentity("snapshot-instance"), binding,
    InstanceRevision(0L), Lifecycle.NotStarted, None, CorrelationReference("snapshot-correlation"),
    CausationReference("snapshot-start"), None, None, None, Vector.empty)
  private val active = take(initial.appendC(initial.revision, HistoryEntry(
    HistorySequence(1L), initial.revision.next, Lifecycle.Active,
    Some(ProgressionReference("step.Awaiting")), initial.correlation, initial.causation,
    None, None, None)))
  private val input = StepCloseRequest("snapshot-close", "snapshot-step", "a" * 64, "b" * 64, Vector.empty)
  private val candidate = take(StepCloseRequestAdapter.submissionC(input)).candidate.reference
  private val provided = StateMachineProvidedApiRequest(
    "GoalPhaseWorkflow", "goal-phase-ordered-run-v4", StateMachineRunIdentity("snapshot-run"),
    StateMachineOperationIdentity("GoalPhaseService", "requestStepClose"),
    Some(StateMachineOperationInput(StateMachineInputTypeReference("StepCloseRequest"),
      ContextReference(input.submissionIdentity, candidate.revision))),
    ContextBundle("Review snapshot candidate", Vector.empty, Vector.empty,
      ContextSnapshot("goal-phase-ordered-run-v4")))

  "GoalPhase persisted review snapshot" should {
    "match the continuation snapshot while retaining exact candidate authority" in {
      // Given a review of the exact candidate declared by the Provided API.
      val pending = take(GoalPhaseReviewBoundary.planC(input,
        Instant.parse("2026-10-01T00:00:00Z"), active, provided, operation)).get
      // When the application projects the persisted suspension boundary.
      val record = take(GoalPhaseReviewBoundary.suspendedRecordC(pending, active))
      // Then canonical issued-work/completion admission sees the same snapshot;
      // the candidate digest and typed input remain independent and unchanged.
      record.suspension.get.contextSnapshot.value shouldBe provided.context.snapshot.workflowRevision
      pending.input.value shouldBe input
      pending.candidateRevision shouldBe candidate.revision
      record.suspension.get.continuationIdentity.value shouldBe pending.continuation.continuationId.value
      record.revision.value.toString shouldBe pending.continuation.expectedRevision.value
    }

    "reject a foreign workflow snapshot before projecting a history entry" in {
      // Given an otherwise admitted review continuation.
      val pending = take(GoalPhaseReviewBoundary.planC(input,
        Instant.parse("2026-10-01T00:00:00Z"), active, provided, operation)).get
      val foreign = pending.copy(continuation = pending.continuation.copy(
        context = provided.context.copy(snapshot = ContextSnapshot("foreign-workflow"))))
      // When its snapshot differs from the active definition.
      val result = GoalPhaseReviewBoundary.suspendedRecordC(foreign, active)
      // Then no suspension projection is returned.
      result shouldBe a[Consequence.Failure[?]]
      active.suspension shouldBe empty
      active.history.size shouldBe 1
    }
  }
}
