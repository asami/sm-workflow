package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take

/** Pure native append/projection inputs, not product effects or provider evidence.
  * @since   Oct. 17, 2026
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseNativeHistorySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _initial: InstanceRecord = take(InstanceRecord(InstanceIdentity("verification-history"),
    take(GoalPhaseRuntimeCatalog.loadC()).definition, initialRevision, Lifecycle.NotStarted, None,
    CorrelationReference("verification-correlation"), CausationReference("verification-start"),
    None, None, None, Vector.empty).validateC)
  private def _append(from: InstanceRecord, stage: String, cause: String): InstanceRecord =
    take(from.appendC(from.revision, GoalPhaseNativeRun.history(from, "phase." + stage, cause)))
  private def _pending(from: InstanceRecord): InstanceRecord = {
    val entry = GoalPhaseNativeRun.history(from, "phase.Implementation", "verification-issued").copy(
      suspension = Some(SuspensionBoundary(ContinuationIdentity("verification-work"), from.revision.next,
        ContextSnapshotReference("verification-context"), CompletionReference("verification-completion"),
        EvidenceReference("verification-evidence"), from.correlation)))
    take(from.appendC(from.revision, entry))
  }

  "Actual GoalPhase native history" should {
    "project every frontier fact from the selected actual append entry" in {
      Given("two genuine appendC unit entries with different native linkage facts")
      val initial = _initial
      val firstentry = GoalPhaseNativeRun.history(initial, "phase.Implementation", "first-unit-cause").copy(
        derivedCompositeOccurrence = Some(DerivedCompositeOccurrenceReference("first-unit-occurrence")),
        committedPredecessor = Some(CommittedEntityTransitionReference("first-unit-predecessor")))
      val first = take(initial.appendC(initial.revision, firstentry))
      val lastentry = GoalPhaseNativeRun.history(first, "phase.InspectCandidate", "last-unit-cause").copy(
        correlation = CorrelationReference("last-unit-correlation"),
        derivedCompositeOccurrence = Some(DerivedCompositeOccurrenceReference("last-unit-occurrence")),
        committedPredecessor = Some(CommittedEntityTransitionReference("last-unit-predecessor")))
      val current = take(first.appendC(first.revision, lastentry))
      When("the first actual revision is projected from the later record")
      val prefix = take(GoalPhaseNativeHistory.prefixC(current, first.revision))
      Then("the selected entry owns revision, lifecycle, progression, causal linkage and history")
      prefix.revision shouldBe firstentry.revision
      prefix.lifecycle shouldBe firstentry.lifecycle
      prefix.currentProgression shouldBe firstentry.currentProgression
      prefix.correlation shouldBe firstentry.correlation
      prefix.causation shouldBe firstentry.causation
      prefix.derivedCompositeOccurrence shouldBe firstentry.derivedCompositeOccurrence
      prefix.committedPredecessor shouldBe firstentry.committedPredecessor
      prefix.suspension shouldBe firstentry.suspension
      prefix.history shouldBe Vector(firstentry)
      prefix.identity shouldBe current.identity
      prefix.definition shouldBe current.definition
      current.history shouldBe Vector(firstentry, lastentry)
    }

    "return only the immediately following actual contiguous entry" in {
      Given("a source, a pending suspension and two later native append entries")
      val source = _append(_initial, "Implementation", "selected-unit-plan")
      val pending = _pending(source)
      val completed = _append(pending, "InspectCandidate", "unit-result")
      val current = _append(completed, "Validation", "ordered-candidate:candidate")
      When("next is selected from the actual source and from the current tip")
      val next = take(GoalPhaseNativeHistory.nextC(current, source))
      val end = take(GoalPhaseNativeHistory.nextC(current, current))
      Then("next remains the actual suspension rather than a later completion or stage")
      next shouldBe Some(pending.history.last)
      next.get.revision shouldBe source.revision.next
      end shouldBe None
      take(GoalPhaseNativeHistory.prefixC(current, pending.revision)).suspension shouldBe pending.suspension
    }

    "consume the exact actual paired completion without appending historical work" in {
      Given("an actual pending unit work followed by its result and a later candidate observation")
      val source = _append(_initial, "Implementation", "selected-unit-plan")
      val pending = _pending(source)
      val completed = _append(pending, "InspectCandidate", "unit-result")
      val current = _append(completed, "Validation", "ordered-candidate:candidate")
      val before = current.history
      When("the owning result reference selects its exact adjacent completion")
      val selected = take(GoalPhaseNativeHistory.completionC(current, pending,
        ContextReference("unit-result", "1"), "InspectCandidate"))
      Then("the actual prefix is returned once and the supplied current history remains unchanged")
      selected.history shouldBe completed.history
      selected.revision shouldBe pending.revision.next
      selected.causation shouldBe CausationReference("unit-result")
      selected.suspension shouldBe None
      current.history shouldBe before
      take(GoalPhaseNativeHistory.completionC(current, pending,
        ContextReference("unit-result", "1"), "InspectCandidate")).history shouldBe completed.history
    }

    "reject missing revisions, foreign owners and crossed completion stage or cause" in {
      Given("native-valid unit records and malformed or unrelated projection requests")
      val source = _append(_initial, "Implementation", "selected-unit-plan")
      val pending = _pending(source)
      val current = _append(pending, "InspectCandidate", "unit-result")
      val foreign = take(source.copy(identity = InstanceIdentity("other-verification-history")).validateC)
      val definition = take(source.copy(definition = take(RepositorySyncDefinitionBinding.bindC)).validateC)
      When("projection attempts to cross an absent revision, instance, definition or paired result")
      val rejected = Vector(
        GoalPhaseNativeHistory.prefixC(current, InstanceRevision(99)),
        GoalPhaseNativeHistory.prefixC(current.copy(history = current.history :+ current.history.last), current.revision),
        GoalPhaseNativeHistory.nextC(current, foreign),
        GoalPhaseNativeHistory.nextC(current, definition),
        GoalPhaseNativeHistory.nextC(current, source.copy(currentProgression = Some(ProgressionReference("phase.Repair")))),
        GoalPhaseNativeHistory.completionC(current, pending, ContextReference("other-result", "1"), "InspectCandidate"),
        GoalPhaseNativeHistory.completionC(current, pending, ContextReference("unit-result", "1"), "Review"),
        GoalPhaseNativeHistory.completionC(current, source, ContextReference("unit-result", "1"), "InspectCandidate"))
      Then("each request fails instead of searching ahead or constructing a replacement completion")
      rejected.foreach(_.isSuccess shouldBe false)
      current.history.last.causation shouldBe CausationReference("unit-result")
      current.history.size shouldBe 3
    }
  }
}
