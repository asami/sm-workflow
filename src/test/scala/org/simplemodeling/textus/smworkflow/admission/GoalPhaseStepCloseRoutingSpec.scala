package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.CandidateAdmissionGapRouting.Target
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.{StepCloseRequest, StepReviewEvidence}

/**
 * @since   Sep. 26, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseStepCloseRoutingSpec extends AnyWordSpec with Matchers {
  private val now = Instant.parse("2026-09-26T00:00:00Z")
  private val request = StepCloseRequest("close-1", "step-1", "a" * 64, "b" * 64, Vector.empty)

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"GoalPhase fixture was rejected: $failure")
  }

  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString.stripSuffix("\n") finally source.close()
  }

  private val sidecar = take(CandidateAdmissionProducerAbi.parseC(
    resource("/workflow/goal-phase-candidate-admission-producer-abi.json")))
  private val definition = take(CandidateWorkflowAbi.parseC(
    resource("/workflow/goal-phase-workflow-abi.json"), sidecar, "src/main/cozy/sm-workflow.cml"))
  private val binding = take(WorkflowInstancePersistence.bindCandidateDefinitionC(definition))
  private val spi = sidecar.models.head.requiredSpi.head
  private val reviewOperation = StateMachineRequiredOperation(
    StateMachineRequiredOperationIdentity(spi.identity.value),
    spi.actionIdentity.value,
    StateMachineOperationIdentity(spi.operation.service.value, spi.operation.name.value),
    spi.operation.inputType.map(x => StateMachineInputTypeReference(x.value)),
    spi.operation.resultType.map(x => StateMachineResultTypeReference(x.value)),
    StateMachineRequiredOperationMetadata(
      ContextContract("goal-phase-review-context", Vector.empty, Vector.empty),
      CompletionContract("goal-phase-review-completion", Vector.empty),
      EvidenceContract("goal-phase-review-evidence", Vector.empty),
      Vector.empty
    )
  )
  private val initial = InstanceRecord(
    InstanceIdentity("goal-phase-instance-1"), binding, InstanceRevision(0L), Lifecycle.NotStarted,
    None, CorrelationReference("goal-phase-correlation"), CausationReference("goal-phase-causation"),
    Some(DerivedCompositeOccurrenceReference("goal-phase-occurrence")),
    Some(CommittedEntityTransitionReference("step-candidate-committed")), None, Vector.empty
  )
  private val active = take(initial.appendC(InstanceRevision(0L), HistoryEntry(
    HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
    Some(ProgressionReference("step.Awaiting")),
    CorrelationReference("goal-phase-correlation"), CausationReference("goal-phase-causation"),
    Some(DerivedCompositeOccurrenceReference("goal-phase-occurrence")),
    Some(CommittedEntityTransitionReference("step-candidate-committed")), None
  )))

  "GoalPhase Step-close gap routing" should {
    "route a missing full review only to the CML-declared semantic Required SPI" in {
      val assessed = take(GoalPhaseStepCloseRouting.assessC(request, now, active, reviewOperation))
      assessed.admissionSatisfied shouldBe false
      assessed.review.map(_.target) shouldBe Some(Target.SemanticAction(reviewOperation))
      assessed.review.map(_.gap.requirement.identity) shouldBe Some(StepClosureAdmission.fullReviewRequirement)
    }

    "need no semantic action when a fresh full review is supplied" in {
      val reference = take(StepClosureAdmission.candidateRefC(
        StepClosureAdmission.StepSnapshot(request.stepIdentity, request.programDigest, request.planningDigest)))
      val evidence = StepReviewEvidence("review-1", reference.revision,
        "2026-09-25T23:59:00Z", "full-step", "reviewed", "receipt")
      val assessed = take(GoalPhaseStepCloseRouting.assessC(
        request.copy(reviewEvidence = Vector(evidence)), now, active, reviewOperation))
      assessed.admissionSatisfied shouldBe true
      assessed.review shouldBe None
    }

    "reject an undeclared review operation and a suspended instance" in {
      val wrong = reviewOperation.copy(operation = StateMachineOperationIdentity("OtherService", "reviewStep"))
      GoalPhaseStepCloseRouting.assessC(request, now, active, wrong) shouldBe a[Consequence.Failure[?]]
      val reviewed = request.copy(reviewEvidence = Vector(StepReviewEvidence(
        "review-1", take(StepClosureAdmission.candidateRefC(StepClosureAdmission.StepSnapshot(
          request.stepIdentity, request.programDigest, request.planningDigest))).revision,
        "2026-09-25T23:59:00Z", "full-step", "reviewed", "receipt")))
      GoalPhaseStepCloseRouting.assessC(reviewed, now, active, wrong) shouldBe a[Consequence.Failure[?]]
      val suspended = active.copy(suspension = Some(SuspensionBoundary(
        org.goldenport.cncf.workflow.WorkflowInstancePersistence.ContinuationIdentity("review-1"), active.revision,
        ContextSnapshotReference("snapshot-1"), CompletionReference("review-completion"),
        EvidenceReference("review-evidence"), CorrelationReference("resume-1")
      )))
      GoalPhaseStepCloseRouting.assessC(request, now, suspended, reviewOperation) shouldBe
        a[Consequence.Failure[?]]
    }
  }
}
