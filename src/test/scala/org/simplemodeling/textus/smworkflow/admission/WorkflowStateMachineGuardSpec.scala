package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.{StepCloseLifecycle, SplitPhaseEntryLifecycle, SplitPhaseHumanDirectionLifecycle, RepositorySyncBoundaryLifecycle}
import domain.statemachine.candidateadmission.{GoalPhaseWorkflowCandidateAdmissionProducer1,
  SplitPhaseWorkflowCandidateAdmissionProducer2, RepositorySyncWorkflowCandidateAdmissionProducer3}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Actual CML-generated entity transitions, not a hand-authored runtime model.
  * Connected completion specs separately prove semantic/evidence admission and
  * that each public Operation invokes the guarded deterministic progression.
  *
  * @since Oct.  2, 2026
  * @version Oct.  5, 2026
  */
final class WorkflowStateMachineGuardSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private type Edge = (String, String, String)
  private def table(states: Vector[String], actual: Vector[Edge], expected: Vector[Edge],
    permits: (String, String, String) => Boolean): Unit = {
    states.distinct shouldBe states
    actual shouldBe expected
    actual.distinct shouldBe actual
    actual.foreach { (from, event, to) =>
      states should contain (from)
      states should contain (to)
      event.trim should not be empty
    }
    val domain = states ++ Vector("foreign", "accept", "repair", "reject", "decision-needed", "", null)
    val events = actual.map(_._2).distinct ++ Vector("accept", "repair", "reject", "decision-needed",
      "enriched", "force-close", "force-push", "", null)
    val allowed = expected.toSet
    for (from <- domain; event <- events; to <- domain)
      withClue(s"($from, $event, $to): ") {
        permits(from, event, to) shouldBe allowed.contains((from, event, to))
      }
  }

  "Application CML StateMachine guards" should {
    "keep SplitPhase Human issue and answer events distinct from semantic alternatives or authority grants" in {
      Given("the actual generated SplitPhase Human lifecycle")
      val lifecycle = SplitPhaseHumanDirectionLifecycle
      When("its declared native issue and answer edges are checked against arbitrary events")
      val allowed = lifecycle.permits("Idle", "human-direction-issued", "Awaiting")
      val answered = lifecycle.permits("Awaiting", "human-direction-recorded", "Answered")
      val semantic = Vector("Enriched", "DecisionNeeded", "ProvideResolution", "Defer", "Cancel", "", null)
        .map(event => lifecycle.permits("Awaiting", event, "Answered"))
      Then("only the declared native events advance, never an unadmitted choice or semantic reply")
      allowed shouldBe true
      answered shouldBe true
      semantic.foreach(_ shouldBe false)
      table(lifecycle.states.map(_.name), lifecycle.transitions.map(t => (t.from, t.event.get, t.to)),
        Vector(("Idle", "human-direction-issued", "Awaiting"), ("Awaiting", "human-direction-recorded", "Answered"),
          ("Answered", "human-deferred", "Deferred"), ("Answered", "human-cancelled", "Cancelled")), lifecycle.permits)
    }
    "permit GoalPhase closure only after application admission, never directly from a review alternative" in {
      table(StepCloseLifecycle.states.map(_.name), StepCloseLifecycle.transitions.map(t =>
        (t.from, t.event.get, t.to)), Vector(("Awaiting", "admitted", "Closed")), StepCloseLifecycle.permits)
      StepCloseLifecycle.permits("Awaiting", "repair", "Awaiting") shouldBe false
      // A retained repair result is history, not a fabricated entity transition.
    }

    "permit SplitPhase input admission and PREVIEW or APPLY only from their declared predecessor" in {
      table(SplitPhaseEntryLifecycle.states.map(_.name), SplitPhaseEntryLifecycle.transitions.map(t =>
        (t.from, t.event.get, t.to)), Vector(
        ("EntryAssessment", "shape-ready", "ProposalAssessment"),
        ("EntryAssessment", "semantic-needed", "SemanticAssessment"),
        ("SemanticAssessment", "inputs-admitted", "ProposalAssessment"),
        ("ProposalAssessment", "previewed", "Previewed"),
        ("ProposalAssessment", "applied", "Applied"),
        ("ProposalAssessment", "application-started", "ApplyingDocuments"),
        ("ApplyingDocuments", "documents-applied", "Applied")), SplitPhaseEntryLifecycle.permits)
      SplitPhaseEntryLifecycle.permits("SemanticAssessment", "previewed", "Previewed") shouldBe false
      SplitPhaseEntryLifecycle.permits("SemanticAssessment", "decision-needed", "SemanticAssessment") shouldBe false
    }

    "permit RepositorySync reviewed merge push and convergence only after their deterministic evidence gates" in {
      table(RepositorySyncBoundaryLifecycle.states.map(_.name), RepositorySyncBoundaryLifecycle.transitions.map(t =>
        (t.from, t.event.get, t.to)), Vector(
        ("BoundaryAssessment", "boundary-captured", "BoundaryCaptured"),
        ("BoundaryAssessment", "fetch-started", "Fetching"),
        ("Fetching", "fetch-observed", "FetchAdmitted"),
        ("BoundaryCaptured", "fetch-admitted", "FetchAdmitted"),
        ("FetchAdmitted", "preservation-started", "Preserving"),
        ("FetchAdmitted", "local-state-preserved", "LocalStatePreserved"),
        ("Preserving", "index-captured", "IndexCaptured"),
        ("IndexCaptured", "checkpoint-creating", "CheckpointCreating"),
        ("CheckpointCreating", "checkpoint-created", "CheckpointCreated"),
        ("CheckpointCreated", "checkpoint-installing", "CheckpointInstalling"),
        ("CheckpointInstalling", "local-state-preserved", "LocalStatePreserved"),
        ("LocalStatePreserved", "integration-started", "Integrating"),
        ("LocalStatePreserved", "relation-classified", "RelationClassified"),
        ("Integrating", "integration-observed", "Integrated"),
        ("Integrating", "pending-merge-observed", "PendingMergePrepared"),
        ("Integrating", "merge-conflicted", "MergeConflicted"),
        ("Integrated", "native-check-prepared", "IntegrationValidationChecking"),
        ("IntegrationValidationChecking", "native-check-prepared", "IntegrationValidationChecking"),
        ("IntegrationValidationChecking", "native-check-recorded", "IntegrationValidationChecking"),
        ("IntegrationValidationChecking", "native-validation-admitted", "IntegrationValidated"),
        ("IntegrationValidated", "native-push-prepared", "NativePushPreparing"),
        ("PendingMergePrepared", "native-check-prepared", "ValidationChecking"),
        ("ValidationChecking", "native-check-prepared", "ValidationChecking"),
        ("ValidationChecking", "native-check-recorded", "ValidationChecking"),
        ("ValidationChecking", "native-validation-admitted", "MergeValidated"),
        ("MergeConflicted", "merge-resolution-observed", "PendingMergePrepared"),
        ("MergeConflicted", "resolution-plan-admitted", "MergeResolutionAdmitted"),
        ("MergeConflicted", "conflict-decision-required", "ConflictDecisionRequired"),
        ("MergeResolutionAdmitted", "conflict-materialization-prepared", "MaterializingConflict"),
        ("MaterializingConflict", "merge-resolution-observed", "PendingMergePrepared"),
        ("ConflictDecisionRequired", "human-conflict-resolution-admitted", "MergeResolutionAdmitted"),
        ("ConflictDecisionRequired", "human-conflict-deferred", "ConflictDecisionRequired"),
        ("ConflictDecisionRequired", "human-conflict-cancelled", "ConflictDecisionRequired"),
        ("RelationClassified", "fast-forward-admitted", "FastForwardAdmitted"),
        ("RelationClassified", "push-admitted", "PushAdmitted"),
        ("RelationClassified", "merge-admitted", "MergeAdmitted"),
        ("RelationClassified", "convergence-admitted", "Converged"),
        ("FastForwardAdmitted", "convergence-admitted", "Converged"),
        ("PushAdmitted", "convergence-admitted", "Converged"),
        ("MergeAdmitted", "merge-validation-admitted", "MergeValidated"),
        ("MergeValidated", "reviewed-merge-push-admitted", "PushAdmitted"),
        ("MergeValidated", "native-review-accepted", "ReviewAccepted"),
        ("MergeValidated", "native-review-repair-required", "ReviewRepairRequired"),
        ("MergeValidated", "native-review-decision-required", "ReviewDecisionRequired"),
        ("MergeValidated", "native-review-rejected", "ReviewRejected"),
        ("ReviewAccepted", "native-commit-prepared", "NativeCommitPreparing"),
        ("NativeCommitPreparing", "native-commit-observed", "NativeCommitObserved"),
        ("NativeCommitObserved", "native-push-prepared", "NativePushPreparing"),
        ("NativePushPreparing", "native-push-observed", "NativePushObserved"),
        ("NativePushObserved", "native-final-fetch-prepared", "NativeFinalFetching"),
        ("NativeFinalFetching", "native-convergence-observed", "Converged")), RepositorySyncBoundaryLifecycle.permits)
      RepositorySyncBoundaryLifecycle.permits("MergeValidated", "convergence-admitted", "Converged") shouldBe false
      RepositorySyncBoundaryLifecycle.permits("ConflictDecisionRequired", "ProvideResolution", "MergeResolutionAdmitted") shouldBe false
      RepositorySyncBoundaryLifecycle.permits("ConflictDecisionRequired", "Cancel", "Converged") shouldBe false
      RepositorySyncBoundaryLifecycle.permits("ConflictDecisionRequired", "human-conflict-deferred", "Converged") shouldBe false
    }

    "keep every declared JudgmentAction alternative distinct from a state-change event or target" in {
      Given("the actual generated JudgmentAction producers and entity StateMachine declarations")
      val goal = GoalPhaseWorkflowCandidateAdmissionProducer1.producer.judgments
      val split = SplitPhaseWorkflowCandidateAdmissionProducer2.producer.judgments
      val sync = RepositorySyncWorkflowCandidateAdmissionProducer3.producer.judgments
      When("their declared alternatives, transition events and target states are extracted")
      val goalalternatives = goal.map(j => j.actionIdentity -> j.alternatives.map(_.value))
      val splitalternatives = split.map(j => j.actionIdentity -> j.alternatives.map(_.value))
      val syncalternatives = sync.map(j => j.actionIdentity -> j.alternatives.map(_.value))
      val alternatives = (goal ++ split ++ sync).flatMap(_.alternatives.map(_.value)).toSet
      val events = (StepCloseLifecycle.transitions.flatMap(_.event) ++
        SplitPhaseEntryLifecycle.transitions.flatMap(_.event) ++
        RepositorySyncBoundaryLifecycle.transitions.flatMap(_.event)).toSet
      val states = (StepCloseLifecycle.states.map(_.name) ++ SplitPhaseEntryLifecycle.states.map(_.name) ++
        RepositorySyncBoundaryLifecycle.states.map(_.name)).toSet
      Then("every exact declared alternative is retained and none is an entity event or target")
      goalalternatives shouldBe Vector("ReviewStep" -> Vector("accept", "repair"))
      splitalternatives shouldBe
        Vector("AssessNovelSplitWork" -> Vector("enriched", "decision-needed"),
          "AssessPartitionSemanticInputs" -> Vector("Enriched", "DecisionNeeded"),
          "AssessHandoffScopes" -> Vector("Enriched", "DecisionNeeded"),
          "AssessReasoningDemand" -> Vector("Enriched", "DecisionNeeded"))
      syncalternatives shouldBe
        Vector("ReviewMerge" -> Vector("accept", "repair", "decision-needed", "reject"),
          "ResolveRepositoryMergeConflicts" -> Vector("resolved", "decision-needed"),
          "ReviewNativePendingMerge" -> Vector("accept", "repair", "decision-needed", "reject"))
      alternatives.intersect(events) shouldBe empty
      alternatives.intersect(states) shouldBe empty
    }
  }
}
