package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.workflow.{WorkflowMechanicalTransition as M, StateMachineOperationIdentity}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningCheckpointObservation as O, SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionContext as C,
  SplitPhaseTypedStartPreparation as T}
import SplitPhasePlanningCheckpointFixture.{f, knownFacts, allocated, anchor, reference}
import SplitPhaseObservedCheckpointFixture.*
import SplitPhaseExplicitPartitionFixture.take

/** Original fixture data is privately constructed at the actual typed Start
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  * position, not the older semantic fixture's proposal.Awaiting position. */
private[admission] object SplitPhaseObservedCheckpointFixture {
  val current = take(f.initial.appendC(initialRevision, f.instance.history.head.copy(
    currentProgression = Some(ProgressionReference("entry.EntryAssessment")))))
  def bound(facts: C.Facts = knownFacts): T.PlanningBound = SplitPhasePlanningCheckpointFixture.bound(facts, current)
  def capture(selected: T.PlanningBound = bound()): K.Captured = SplitPhasePlanningCheckpointFixture.capture(selected)
  def forBoundary(base: SplitPhaseExplicitPartitionFixture.Base, boundary: B.Validated,
    objectives: SplitPhaseExplicitObjectiveSelection.Records = SplitPhaseExplicitObjectiveSelection.Records(None, None, None)): K.Captured = {
    val proposal = boundary.shape.records.proposal
    val command = f.command.copy(input = f.command.input.copy(proposalReference = Some(proposal.reference.identity)))
    val raw = f.record.copy(source = base.source, proposal = Some(proposal), command = command, selection = f.selectionFor(command))
    val start = take(T.prepareC(raw, raw.source, raw.proposal, raw.selection, f.catalog, f.configuration))
    val facts = C.Facts(boundary.shape.records, boundary.records, base.records,
      knownFacts.planning.copy(source = base.source.reference, authority = base.source.authority, objectives = objectives))
    val context = f.context.copy(facts = facts)
    capture(take(T.bindPlanningC(start, take(C.preparePlanningC(context, facts, current, f.configuration, f.catalog)))))
  }
}

/** Raw receipt values below are inert relation-test data, never native Retained. */
final class SplitPhasePlanningCheckpointObservationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def data(saved: K.Captured) = O.Data(saved.index, saved.record,
    O.Original(saved.record.originalContext, saved.record.originalStart, saved.record.originalContext.facts),
    saved.record.snapshot.selection, O.builtDocuments(saved.record.snapshot))
  private def receipt(saved: K.Captured) = M.Receipt(anchor, SmWorkflowComponent.componentId,
    K.advanceOperation, saved.before, saved.next, saved.mechanicalIntent.get.payload)

  "Read-only checkpoint relations" should {
    "preserve allocated and unchanged preallocated complete values" in {
      Given("actual local captures, without native execution authority")
      val selected = bound(); val (allocation, _) = allocated(selected)
      val retained = bound(knownFacts.copy(planning = allocation.data))
      val unchanged = take(K.captureUnchangedC(retained, take(W.advanceC(retained.context.selection)), reference, anchor))
      When("complete independent data is compared without another planning call")
      val examples = Vector(capture(), unchanged)
      Then("Unit acknowledges only data agreement")
      examples.foreach(s => take(O.verifyMechanicalC(data(s), receipt(s))) shouldBe ())
      val legacy = SplitPhasePlanningCheckpointFixture.capture()
      O.verifyMechanicalC(data(legacy), receipt(legacy)).isSuccess shouldBe false
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseManagedPlanningCheckpoint.Observed(null, null)")
    }

    "retain every Built document in a mixed Requirements frontier" in {
      Given("actual scope and repository requirement projections with usable candidates")
      import SplitPhaseExplicitPartitionFixture.{base, enumerate, proof}
      val s = base(Vector.fill(4)(180)); val e = enumerate(s)
      val grouped = proof(s, e.candidates.find(_.cutAfterIndices == Vector(1)).get, "grouped", handoffPairs = Vector(0 -> 1))
      val mixed = s.initial.records.copy(ownership = s.initial.records.ownership.copy(children =
        s.initial.records.ownership.children.zipWithIndex.map((c, i) => c.copy(repository = B.RepositoryIdentity(if (i < 2) "A" else "B")))))
      When("both complete mixed mappings are observed")
      val examples = Vector(forBoundary(s, grouped), forBoundary(s, take(B.loadC(s.initial.shape, mixed, mixed))))
      Then("missing or changed candidate data is refused rather than dropping requirements")
      examples.foreach { saved =>
        val d = data(saved)
        d.documents.nonEmpty shouldBe true
        take(O.verifyMechanicalC(d, receipt(saved))) shouldBe ()
        O.verifyMechanicalC(d.copy(documents = d.documents.tail), receipt(saved)).isSuccess shouldBe false
        val changed = d.documents.head.copy(proposal = d.documents.head.proposal.copy(children =
          d.documents.head.proposal.children.updated(0, d.documents.head.proposal.children.head.copy(expectedMinutes = 1))))
        O.verifyMechanicalC(d.copy(documents = d.documents.updated(0, changed)), receipt(saved)).isSuccess shouldBe false
      }
    }

    "refuse same-reference substitutions in complete original and effective values" in {
      Given("one complete saved mapping and independent rows")
      val saved = capture(); val d = data(saved); val original = d.original; val effective = d.effective
      val source = original.start.source.copy(authority = original.start.source.authority.copy(revision = "changed"))
      When("independent Start, context, facts or documents drift without changing their locators")
      val changes = Vector(d.copy(original = original.copy(start = original.start.copy(source = source))),
        d.copy(original = original.copy(start = original.start.copy(selection = original.start.selection.copy(recommendationReference = Some("other"))))),
        d.copy(original = original.copy(context = original.context.copy(owner = original.context.owner.copy(instanceRevision = InstanceRevision(2))))),
        d.copy(original = original.copy(facts = original.facts.copy(planning = effective.planning))),
        d.copy(effective = effective.copy(proposal = effective.proposal.copy(source = source))),
        d.copy(effective = effective.copy(planning = original.facts.planning)),
        d.copy(index = d.index.copy(mapping = d.index.mapping.copy(revision = "other"))),
        d.copy(documents = Vector.empty), d.copy(original = null), d.copy(effective = null))
      Then("every changed complete relation and absent input fails closed")
      changes.foreach(v => O.verifyMechanicalC(v, receipt(saved)).isSuccess shouldBe false)
      O.verifyMechanicalC(null, receipt(saved)).isSuccess shouldBe false
      O.verifyMechanicalC(d, null).isSuccess shouldBe false
    }

    "refuse crossed native anchors, payloads and original Start history" in {
      Given("an inert receipt with complete original Start lineage")
      val saved = capture(); val d = data(saved); val r = receipt(saved)
      When("native relationship fields or the first actual history entry are crossed")
      val first = r.current.history.head
      val firstChanges = Vector(first.copy(causation = CausationReference("foreign")),
        first.copy(correlation = CorrelationReference("foreign")), first.copy(sequence = HistorySequence(2)),
        first.copy(revision = InstanceRevision(2)), first.copy(currentProgression = None), first.copy(lifecycle = Lifecycle.NotStarted))
      val changes = Vector(r.copy(reference = anchor.copy(revision = "other")), r.copy(component = ComponentId("org.example.Foreign")),
        r.copy(operation = StateMachineOperationIdentity("SplitPhaseService", "other")),
        r.copy(payload = r.payload.copy(typeIdentity = "other")), r.copy(next = r.next.copy(revision = InstanceRevision(3)))) ++
        firstChanges.map(h => r.copy(current = r.current.copy(history = r.current.history.updated(0, h))))
      Then("raw receipt decoding cannot admit foreign lineage")
      changes.foreach(v => O.verifyMechanicalC(d, v).isSuccess shouldBe false)
    }
  }
}
