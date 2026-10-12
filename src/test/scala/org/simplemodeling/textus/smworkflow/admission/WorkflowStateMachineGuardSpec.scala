package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.{GoalPhaseRunLifecycle, SplitPhaseEntryLifecycle,
  SplitPhaseHumanDirectionLifecycle, RepositorySyncBoundaryLifecycle}
import domain.statemachine.candidateadmission.{GoalPhaseWorkflowCandidateAdmissionProducer1,
  SplitPhaseWorkflowCandidateAdmissionProducer2, RepositorySyncWorkflowCandidateAdmissionProducer3}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Actual application CML progression; common completion guarantees are not re-specified.
  * @since   Oct.  2, 2026
  * @version Oct. 12, 2026
  */
final class WorkflowStateMachineGuardSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Application CML progression" should {
    "keep a committed Step distinct from whole-Phase repository closure" in {
      Given("the generated ordered Goal lifecycle")
      val lifecycle = GoalPhaseRunLifecycle
      When("the normal review, Step closure and whole-Phase closure edges are selected")
      val normal = Vector(lifecycle.permits("Review", "review-accepted", "Closing"),
        lifecycle.permits("Closing", "step-next-closed", "Implementation"),
        lifecycle.permits("Closing", "last-step-closed", "PhaseInspection"),
        lifecycle.permits("PhaseReview", "phase-review-accepted", "PhaseClosing"),
        lifecycle.permits("PhaseClosing", "phase-repository-closed", "Closed"))
      val premature = Vector(lifecycle.permits("Review", "review-accepted", "Closed"),
        lifecycle.permits("Closing", "last-step-closed", "Closed"))
      Then("a Step continues the Phase, and only the declared repository closure reaches Closed")
      normal.foreach(_ shouldBe true)
      premature.foreach(_ shouldBe false)
    }

    "retain managed application and actual Human direction outcomes" in {
      Given("the generated Split entry and Human lifecycles")
      val entry = SplitPhaseEntryLifecycle
      val human = SplitPhaseHumanDirectionLifecycle
      When("the normal document-application and Human edges are selected")
      val normal = Vector(entry.permits("ProposalAssessment", "previewed", "Previewed"),
        entry.permits("ProposalAssessment", "application-started", "ApplyingDocuments"),
        entry.permits("ApplyingDocuments", "documents-applied", "Applied"),
        human.permits("Idle", "human-direction-issued", "Awaiting"),
        human.permits("Awaiting", "human-direction-recorded", "Answered"),
        human.permits("Answered", "human-deferred", "Deferred"),
        human.permits("Answered", "human-cancelled", "Cancelled"))
      val alternativeAsEvent = human.permits("Awaiting", "ProvideResolution", "Answered")
      Then("the actual owning events progress while a raw answer alternative is not itself a transition")
      normal.foreach(_ shouldBe true)
      alternativeAsEvent shouldBe false
    }

    "represent native fetch, validation, review and final observation rather than obsolete admission stages" in {
      Given("the generated native repository lifecycle")
      val lifecycle = RepositorySyncBoundaryLifecycle
      When("the normal convergence edges and a premature merge closure are selected")
      val normal = Vector(lifecycle.permits("BoundaryAssessment", "fetch-started", "Fetching"),
        lifecycle.permits("Fetching", "fetch-observed", "FetchAdmitted"),
        lifecycle.permits("FetchAdmitted", "preservation-started", "Preserving"),
        lifecycle.permits("LocalStatePreserved", "integration-started", "Integrating"),
        lifecycle.permits("MergeValidated", "native-review-accepted", "ReviewAccepted"),
        lifecycle.permits("ReviewAccepted", "native-commit-prepared", "NativeCommitPreparing"),
        lifecycle.permits("NativePushObserved", "native-final-fetch-prepared", "NativeFinalFetching"),
        lifecycle.permits("NativeFinalFetching", "native-convergence-observed", "Converged"))
      val premature = lifecycle.permits("MergeValidated", "native-convergence-observed", "Converged")
      Then("the selected native steps progress, but validation alone is not convergence")
      normal.foreach(_ shouldBe true)
      premature shouldBe false
    }

    "keep assigned semantic alternatives distinct from native progression events" in {
      Given("the three generated producers for real semantic tasks")
      val goal = GoalPhaseWorkflowCandidateAdmissionProducer1.producer.judgments
      val split = SplitPhaseWorkflowCandidateAdmissionProducer2.producer.judgments
      val sync = RepositorySyncWorkflowCandidateAdmissionProducer3.producer.judgments
      When("their requested semantic alternatives and actual lifecycle events are consumed")
      val goalAlternatives = goal.map(j => j.actionIdentity -> j.alternatives.map(_.value))
      val splitAlternatives = split.map(j => j.actionIdentity -> j.alternatives.map(_.value))
      val syncAlternatives = sync.map(j => j.actionIdentity -> j.alternatives.map(_.value))
      val alternatives = (goal ++ split ++ sync).flatMap(_.alternatives.map(_.value)).toSet
      val events = (GoalPhaseRunLifecycle.transitions.flatMap(_.event) ++
        SplitPhaseEntryLifecycle.transitions.flatMap(_.event) ++
        RepositorySyncBoundaryLifecycle.transitions.flatMap(_.event)).toSet
      Then("the real tasks remain semantic values, not caller-selected state changes")
      goalAlternatives shouldBe Vector("ReviewOrderedStep" -> Vector("accept", "repair", "decision-needed"))
      splitAlternatives shouldBe Vector(
        "AssessPartitionSemanticInputs" -> Vector("Enriched", "DecisionNeeded"),
        "AssessHandoffScopes" -> Vector("Enriched", "DecisionNeeded"),
        "AssessReasoningDemand" -> Vector("Enriched", "DecisionNeeded"))
      syncAlternatives shouldBe Vector(
        "ResolveRepositoryMergeConflicts" -> Vector("resolved", "decision-needed"),
        "ReviewNativePendingMerge" -> Vector("accept", "repair", "decision-needed", "reject"))
      alternatives.intersect(events) shouldBe empty
    }
  }
}
