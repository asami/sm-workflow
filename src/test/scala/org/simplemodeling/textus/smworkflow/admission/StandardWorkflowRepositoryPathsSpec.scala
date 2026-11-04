package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take

/** Authored typed history predicates only; no repository operation, native
  * adoption, independent evidence or conflict-path observation is performed.
  * @since Nov. 4, 2026
  * @version Nov. 4, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class StandardWorkflowRepositoryPathsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _definition = take(RepositorySyncDefinitionBinding.bindC)
  private val _correlation = CorrelationReference("fixture-repository-paths")
  private def _boundary(identity: String, revision: Long): SuspensionBoundary = SuspensionBoundary(
    ContinuationIdentity(identity), InstanceRevision(revision), ContextSnapshotReference("fixture-snapshot"),
    CompletionReference("fixture-completion"), EvidenceReference("fixture-evidence"), _correlation)
  private def _entry(revision: Long, stage: String,
    suspension: Option[SuspensionBoundary] = None): HistoryEntry = HistoryEntry(
    HistorySequence(revision), InstanceRevision(revision), Lifecycle.Active,
    Some(ProgressionReference("sync." + stage)), _correlation,
    CausationReference("fixture-repository-paths:" + revision), None, None, suspension)
  private def _record(history: Vector[HistoryEntry], identity: String = "session"): InstanceRecord = {
    val last = history.last
    InstanceRecord(InstanceIdentity(identity), _definition, last.revision, last.lifecycle, last.currentProgression,
      last.correlation, last.causation, last.derivedCompositeOccurrence, last.committedPredecessor, last.suspension, history)
  }
  private val _old_boundary = _boundary("session:native-review:3", 4)
  private val _old_history = Vector(_entry(3, "MergeValidated"), _entry(4, "MergeValidated", Some(_old_boundary)),
    _entry(5, "NativePushFailed"), _entry(6, "PushContinuationAdmitted"), _entry(7, "MergeConflicted"))

  "CB-ID11-01 RepositorySync review path lineage" should {
    "leave fresh merge conflicts to current path observation when only the pre-adoption review exists" in {
      Given("an old candidate review followed by push failure, a separate push adoption and a fresh merge conflict")
      val current = _record(_old_history)
      When("path composition selects a review boundary strictly after the authenticated adoption revision")
      val selected = StandardWorkflowRuntime.repositoryReviewBoundary(current, Some(6L))
      Then("the old reviewed candidate supplies no boundary and the ordinary conflict observation branch remains selected")
      selected shouldBe None
    }

    "select the fresh validated candidate review or triage while retaining old behavior without adoption" in {
      Given("the old reviewed candidate and a new validated candidate after push adoption")
      val fresh = Vector("native-review", "native-triage").map(kind => _boundary("session:" + kind + ":8", 9))
      val records = fresh.map(boundary => _record(_old_history ++
        Vector(_entry(8, "MergeValidated"), _entry(9, "MergeValidated", Some(boundary)))))
      val original = _record(_old_history)
      When("selection uses the adopted lineage or the historical unrestricted lineage")
      val selected = records.map(StandardWorkflowRuntime.repositoryReviewBoundary(_, Some(6L)))
      val retained = StandardWorkflowRuntime.repositoryReviewBoundary(original, None)
      Then("new review and triage boundaries are selected exactly and absence of adoption preserves the original review")
      selected shouldBe fresh.map(Some(_))
      retained shouldBe Some(_old_boundary)
    }

    "refuse malformed crossed wrong-source and same-floor review boundaries" in {
      Given("authored review boundaries with invalid source identities, source stages or adoption-relative revisions")
      val malformed = Vector("session:native-review:08", "session:native-review:9223372036854775808",
        "session:native-review:unknown", "foreign-session:native-review:8", "session:native-review:99")
        .map(name => _record(_old_history ++ Vector(_entry(8, "MergeValidated"),
          _entry(9, "MergeValidated", Some(_boundary(name, 9))))))
      val wrongsource = _record(_old_history ++ Vector(_entry(8, "MergeConflicted"),
        _entry(9, "MergeConflicted", Some(_boundary("session:native-review:8", 9)))))
      val sourceatfloor = _record(Vector(_entry(6, "MergeValidated"),
        _entry(7, "MergeValidated", Some(_boundary("session:native-review:6", 7)))))
      val suspensionatfloor = _record(Vector(_entry(5, "MergeValidated"),
        _entry(6, "MergeValidated", Some(_boundary("session:native-review:5", 6)))))
      When("selection requires both the suspension entry and its exact MergeValidated source strictly after revision six")
      val selected = (malformed ++ Vector(wrongsource, sourceatfloor, suspensionatfloor))
        .map(StandardWorkflowRuntime.repositoryReviewBoundary(_, Some(6L)))
      Then("none can mask current conflict paths with an unrelated or earlier reviewed candidate")
      selected.foreach(_ shouldBe None)
    }

    "derive the continuation prefix from the actual current instance identity" in {
      Given("a different current instance with one matching review and a later crossed review name")
      val matching = _boundary("actual-session:native-review:8", 9)
      val current = _record(Vector(_entry(8, "MergeValidated"), _entry(9, "MergeValidated", Some(matching)),
        _entry(10, "MergeValidated", Some(_boundary("session:native-review:8", 10)))), "actual-session")
      When("selection resolves review identities from the current instance")
      val selected = StandardWorkflowRuntime.repositoryReviewBoundary(current, Some(6L))
      Then("the crossed later suspension is skipped and the exact current-instance boundary is selected")
      selected shouldBe Some(matching)
    }
  }
}
