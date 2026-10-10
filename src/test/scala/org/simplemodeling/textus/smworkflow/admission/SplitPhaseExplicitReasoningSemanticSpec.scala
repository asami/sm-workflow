package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitReasoningSemantic as R,
  SplitPhaseExplicitPlanningAdvance as W, SplitPhaseExplicitObjectiveSelection as O,
  SplitPhaseExplicitPartitionComputation as E, SplitPhaseExplicitPartitionSemantic as S}
import SplitPhaseExplicitPartitionFixture.*

/*
 * @since   Oct.  5, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class SplitPhaseExplicitReasoningSemanticSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _assigned_reply_spec = afterWord("in spec:split-phase-reasoning-interface, example:E2, rules:R2, phase:2")
  private val _context = S.ContextReference("reasoning-context", "1")
  private val _result = S.ResultReference("reasoning-result", "1")
  private val _references = R.ResolutionReferences(Some(W.DataReference("resolved-planning", "2")),
    Some(O.DemandReference("resolved-demand", "2")))

  private def _pending(unknown: Vector[Int] = Vector(1), overhead: Boolean = true): W.ReasoningRequired = {
    val source = base(Vector(260, 180, 260))
    val input = take(E.loadC(source.initial, source.records))
    val demands = source.inventory.orderedUnfinishedItems.zipWithIndex.map { (id, index) =>
      O.ItemDemand(id, if (unknown.contains(index)) O.Demand.Unresolved(s"kernel-$index")
        else if (index == 0) O.Demand.ExpensiveReasoning else O.Demand.SettledExecution)
    }
    val objectives = O.Records(
      Some(O.CostPolicy(O.CostPolicyReference("cost", "1"), source.source.reference, source.source.authority,
        O.ProfileIdentity("expensive"), O.ProfileIdentity("lower"), 2, 1, 1)),
      Some(O.DemandRecord(O.DemandReference("original-demand", "1"), source.source.reference, source.source.authority, demands)),
      if (overhead) Some(O.OverheadPolicy(O.OverheadPolicyReference("overhead", "1"),
        source.source.reference, source.source.authority, 1, 0, 0, 0, 0)) else None
    )
    val data = W.Data(W.DataReference("original-planning", "1"), source.source.reference,
      source.source.authority, None, objectives)
    val need = take(W.advanceC(take(W.selectC(input, data)))).asInstanceOf[W.AllocationRequired]
    val allocated = take(SplitPhaseCandidateAllocationFixture.policy.allocateC(need,
      S.ResultReference("reasoning-fixture-allocation", "1")))
    take(W.continueAllocatedC(allocated)).asInstanceOf[W.ReasoningRequired]
  }

  private def _judgment(prepared: R.Prepared,
    classification: R.Classification = R.Classification.SettledExecution): R.Judgment =
    R.Judgment(R.Decision.Enriched, "Only issued unknown work was classified",
      prepared.request.items.map(item => R.Assignment(item.identity, classification, "Original closure work is classified")),
      Vector(S.EvidenceReference("reasoning-evidence", "1")))

  private def _admit(prepared: R.Prepared, judgment: R.Judgment): R.Admitted =
    take(R.admitC(prepared, _result, _references, judgment)).asInstanceOf[R.Admitted]

  "E2 Workflow owns the reasoning reply assignment" must _assigned_reply_spec {
    "refuse absent or blank reply assignments without changing the computed need" in {
      Given("Spec: docs/spec/split-phase-reasoning-interface.md; Rules: R2; Example: E2; a complete unknown-only computed need without a usable Workflow-assigned reply")
      val pending = _pending()
      val original = pending.selection.data
      val invalid = Vector(null, _result.copy(identity = ""), _result.copy(revision = " "),
        _result.copy(identity = null), _result.copy(revision = null))

      When("Workflow prepares the request with each incomplete assignment and then with its explicit assignment")
      val refused = invalid.map(reference => R.prepareC(_context, pending, reference))
      val prepared = take(R.prepareC(_context, pending, _result))

      Then("only the explicitly assigned reply is representable as a prepared question, with no fabricated reference or altered original facts")
      refused.foreach(_ shouldBe a[Consequence.Failure[?]])
      prepared.request.result shouldBe _result
      prepared.pending should be theSameInstanceAs pending
      prepared.pending.selection.data shouldBe original
      prepared.request.items.map(_.identity) shouldBe pending.items.map(_.identity)
    }

    R.Decision.values.toVector.foreach { decision =>
      s"refuse a crossed reply identity or revision for $decision" in {
        Given("Spec: docs/spec/split-phase-reasoning-interface.md; Rules: R2; Example: E2; a prepared request, exact current data and otherwise valid response to the selected question")
        val prepared = take(R.prepareC(_context, _pending(), _result))
        val original = prepared.pending.selection.data
        val judgment = if (decision == R.Decision.Enriched) _judgment(prepared)
          else _judgment(prepared).copy(decision = decision, assignments = Vector.empty)
        val references = if (decision == R.Decision.Enriched) _references else R.ResolutionReferences(None, None)
        val crossed = Vector(_result.copy(identity = "another-reasoning-result"), _result.copy(revision = "2"))

        When("the same semantic response arrives under another result identity or revision, followed by the assigned reply")
        val refused = crossed.map(reference => R.admitC(prepared, reference, references, judgment))
        val admitted = take(R.admitC(prepared, _result, references, judgment))

        Then("neither crossed reply is admitted, while the exact assigned reply retains its original question and semantic alternative")
        refused.foreach(_ shouldBe a[Consequence.Failure[?]])
        prepared.request.result shouldBe _result
        prepared.pending.selection.data shouldBe original
        admitted match {
          case value: R.Admitted =>
            value.context should be theSameInstanceAs prepared
            value.result shouldBe _result
            value.judgment shouldBe judgment
            value.resolved.reference shouldBe _references.planning.get
          case value: R.DecisionNeeded =>
            value.context should be theSameInstanceAs prepared
            value.result shouldBe _result
            value.judgment shouldBe judgment
        }
      }
    }
  }

  "SplitPhase reasoning semantic boundary" should {
    "the Workflow-owned request" which {
      "ask only for unknown work with its original closure and source authority" in {
        Given("known expensive and settled demands surrounding one computed unknown reasoning item")
        val pending = _pending()

        When("Workflow prepares the semantic classification from its constructed need")
        val prepared = take(R.prepareC(_context, pending, _result))

        Then("the participant receives the original unknown item's context, not price, stage or candidate-selection inputs")
        prepared.pending shouldBe pending
        prepared.request.context shouldBe _context
        prepared.request.source shouldBe pending.selection.data.source
        prepared.request.authority shouldBe pending.selection.data.authority
        prepared.request.demand shouldBe pending.selection.data.objectives.demands.get.reference
        prepared.request.result shouldBe _result
        prepared.request.items.map(_.identity) shouldBe pending.items.map(_.identity)
        prepared.request.items.map(_.reason) shouldBe Vector("kernel-1")
        prepared.request.items.head.closure shouldBe pending.selection.partition.boundary.records.boundary.items(1)
        prepared.request.productElementNames.toVector shouldBe
          Vector("context", "source", "authority", "demand", "items", "result")
      }
    }

    "classification admission" which {
      "preserve every known demand, policy, allocation and original authority" in {
        Given("an exact unknown-only classification and fresh data references selected separately by Workflow")
        val prepared = take(R.prepareC(_context, _pending(), _result))
        val judgment = _judgment(prepared)
        val original = prepared.pending.selection.data
        val demands = original.objectives.demands.get

        When("the semantic judgment reaches the complete current planning-data boundary")
        val admitted = _admit(prepared, judgment)

        Then("only the requested unknown classification and the two host-selected record references change")
        admitted.current shouldBe prepared.pending.selection
        admitted.result shouldBe _result
        admitted.judgment shouldBe judgment
        admitted.resolved shouldBe original.copy(reference = _references.planning.get,
          objectives = original.objectives.copy(demands = Some(demands.copy(reference = _references.demands.get,
            items = demands.items.updated(1, demands.items(1).copy(demand = O.Demand.SettledExecution))))))
        admitted.resolved.objectives.demands.get.items.head shouldBe demands.items.head
        admitted.resolved.objectives.demands.get.items.last shouldBe demands.items.last
        judgment.productElementNames.toVector shouldBe Vector("decision", "rationale", "assignments", "evidence")
      }

      "refuse missing, reordered, duplicate or known-item classification coverage" in {
        Given("two ordered unknown items and responses that alter the exact computed work scope")
        val prepared = take(R.prepareC(_context, _pending(Vector(1, 2)), _result))
        val judgment = _judgment(prepared)
        val assignments = judgment.assignments
        val invalid = Vector(
          assignments.tail, assignments.reverse, Vector(assignments.head, assignments.head),
          assignments.updated(0, assignments.head.copy(identity = prepared.pending.selection.data.objectives.demands.get.items.head.identity)),
          assignments.updated(0, null), assignments.updated(0, assignments.head.copy(classification = null)),
          assignments.updated(0, assignments.head.copy(rationale = ""))
        )

        When("each invalid response is admitted without interpreting additional work")
        val results = invalid.map(values => R.admitC(prepared, _result,
          _references, judgment.copy(assignments = values)))

        Then("no partial, crossed or extra classification becomes resolved data")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
      }

      "refuse missing, malformed or unchanged Workflow-selected record versions" in {
        Given("a valid semantic judgment but unsuitable replacement planning or demand references")
        val prepared = take(R.prepareC(_context, _pending(), _result))
        val original = prepared.pending.selection.data
        val judgment = _judgment(prepared)
        val invalid = Vector(
          _references.copy(planning = None), _references.copy(demands = None),
          _references.copy(planning = Some(original.reference)),
          _references.copy(demands = Some(original.objectives.demands.get.reference)),
          _references.copy(planning = Some(W.DataReference(" ", "2"))),
          _references.copy(demands = Some(O.DemandReference("demand", ""))),
          _references.copy(planning = Some(null)), _references.copy(demands = null)
        )

        When("the separate host-selected references reach the natural response boundary")
        val results = invalid.map(refs => R.admitC(prepared, _result, refs, judgment))

        Then("the old immutable record cannot silently acquire new contents or missing host ownership")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
      }

      "consume current prices policies known demands allocations and estimates without historical-copy proof" in {
        Given("one unresolved classification and compatible current pricing, policies, known demand, allocated proposal and estimates")
        val prepared = take(R.prepareC(_context, _pending(), _result))
        val saved = prepared.pending.selection
        val objectives = saved.data.objectives
        val datasets = Vector(
          saved.data.copy(objectives = objectives.copy(cost = Some(objectives.cost.get.copy(expensiveUnitsPerMinute = 3)))),
          saved.data.copy(objectives = objectives.copy(overhead = Some(objectives.overhead.get.copy(perCommitUnits = 9)))),
          saved.data.copy(objectives = objectives.copy(demands = Some(objectives.demands.get.copy(
            items = objectives.demands.get.items.updated(0, objectives.demands.get.items.head.copy(demand = O.Demand.SettledExecution)))))),
          saved.data.copy(allocations = Some(saved.data.allocations.get.copy(allocations =
            saved.data.allocations.get.allocations.updated(0,
              saved.data.allocations.get.allocations.head.copy(proposal =
                saved.data.allocations.get.allocations.head.proposal.copy(identity = "current-candidate-proposal"))))))
        )
        val estimates = saved.partition.records.estimates
        val records = saved.partition.records.copy(estimates = estimates.copy(items = estimates.items.map(item =>
          if (item.duration == E.Duration.Known(260)) item.copy(duration = E.Duration.Known(261)) else item)))
        When("the host constructs each actual current reasoning need and consumes its one prepared input")
        val input = take(E.loadC(saved.partition.boundary, records))
        val selections = datasets.map(data => take(W.selectC(saved.partition, data))) :+ take(W.selectC(input, saved.data))
        val results = selections.map { selection =>
          val pending = take(W.advanceC(selection)).asInstanceOf[W.ReasoningRequired]
          val current = take(R.prepareC(_context, pending, _result))
          _admit(current, _judgment(current))
        }
        Then("current known facts remain actual inputs, while only the requested unresolved demand is classified")
        results.head.resolved.objectives.cost.get.expensiveUnitsPerMinute shouldBe 3
        results(1).resolved.objectives.overhead.get.perCommitUnits shouldBe 9
        results(2).resolved.objectives.demands.get.items.head.demand shouldBe O.Demand.SettledExecution
        results(3).resolved.allocations.get.allocations shouldBe datasets(3).allocations.get.allocations
        results(3).resolved.allocations.get.allocations.head.proposal.identity shouldBe "current-candidate-proposal"
        results.last.current.partition.records.estimates.items.map(_.duration) shouldBe
          Vector(E.Duration.Known(261), E.Duration.Known(180), E.Duration.Known(261))
        results.foreach(_.resolved.objectives.demands.get.items(1).demand shouldBe O.Demand.SettledExecution)
        results.foreach(_.resolved.reference shouldBe _references.planning.get)
        saved.data.objectives.cost.get.expensiveUnitsPerMinute shouldBe 2
      }

      "refuse absent evidence and duplicate evidence" in {
        Given("raw judgments with incomplete evidence for the requested semantic task")
        val prepared = take(R.prepareC(_context, _pending(), _result))
        val judgment = _judgment(prepared)
        val invalid = Vector(
          judgment.copy(evidence = Vector.empty),
          judgment.copy(evidence = judgment.evidence ++ judgment.evidence), judgment.copy(evidence = Vector(null)),
          judgment.copy(assignments = null), judgment.copy(rationale = ""), judgment.copy(decision = null), null
        )

        When("the malformed semantic responses reach their admission boundary")
        val results = invalid.map(value => R.admitC(prepared, _result, _references, value))

        Then("no response is promoted to an admitted classification")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
      }
    }

    "objective-only continuation" which {
      Vector(
        (R.Classification.SettledExecution, Vector(0)),
        (R.Classification.ExpensiveReasoning, Vector(1))
      ).foreach { case (classification, cuts) =>
        s"derive the cost objective for $classification without redoing the candidate frontier" in {
          Given("one admitted unknown-only classification and the original duration-tied whole frontier")
          val prepared = take(R.prepareC(_context, _pending(), _result))
          val admitted = _admit(prepared, _judgment(prepared, classification))
          val previous = prepared.pending.trace

          When("Workflow resumes its objective processors from the admitted semantic result")
          val continued = take(W.continueReasoningC(admitted))
          val planned = continued.outcome.asInstanceOf[W.Planned]

          Then("classification affects only the deterministic cost winner and only objective selection is newly absorbed")
          continued.mechanicalStages shouldBe Vector(W.Stage.ObjectiveSelection)
          planned.trace.partition should be theSameInstanceAs previous.partition
          planned.trace.projection.get should be theSameInstanceAs previous.projection.get
          planned.trace.admission.get should be theSameInstanceAs previous.admission.get
          planned.objective.input.duration should be theSameInstanceAs
            previous.objective.get.asInstanceOf[O.ReasoningDemandRequired].input.duration
          planned.objective.stage shouldBe O.Stage.Cost
          planned.objective.candidate.cutAfterIndices shouldBe cuts
          planned.selection.data shouldBe admitted.resolved
          planned.objective.boundary shouldBe planned.candidate.boundary
        }
      }

      "return a necessary host overhead record without asking the participant for prices or another winner" in {
        Given("two unknown items classified as expensive work, leaving a cost tie and no overhead policy")
        val prepared = take(R.prepareC(_context, _pending(Vector(1, 2), overhead = false), _result))
        val admitted = _admit(prepared, _judgment(prepared, R.Classification.ExpensiveReasoning))

        When("Workflow completes cost computation and reaches its first missing host record")
        val continued = take(W.continueReasoningC(admitted))
        val required = continued.outcome.asInstanceOf[W.ObjectiveRecordRequired]

        Then("the next need is host-owned OverheadPolicy rather than fresh AI classification or repeated candidate work")
        required.requirement shouldBe O.RequiredRecord.OverheadPolicy
        continued.mechanicalStages shouldBe Vector(W.Stage.ObjectiveSelection)
        required.trace.partition should be theSameInstanceAs prepared.pending.trace.partition
        required.trace.projection.get should be theSameInstanceAs prepared.pending.trace.projection.get
        required.trace.admission.get should be theSameInstanceAs prepared.pending.trace.admission.get
        required.selection.data.objectives.demands.get.items.map(_.demand).distinct shouldBe Vector(O.Demand.ExpensiveReasoning)
      }
    }

    "semantic DecisionNeeded" which {
      "retain the complete unresolved question without partial enrichment or a replacement data row" in {
        Given("an issued unknown-work question whose participant cannot classify it reliably")
        val prepared = take(R.prepareC(_context, _pending(Vector(1, 2)), _result))
        val judgment = _judgment(prepared).copy(decision = R.Decision.DecisionNeeded, assignments = Vector.empty)

        When("Workflow admits a complete decision-needed response with no resolution references")
        val result = take(R.admitC(prepared, _result,
          R.ResolutionReferences(None, None), judgment)).asInstanceOf[R.DecisionNeeded]

        Then("the original full request and result evidence remain available without a fabricated objective or Human interaction")
        result.context shouldBe prepared
        result.result shouldBe _result
        result.judgment shouldBe judgment
        result.context.pending.items shouldBe prepared.pending.items
      }

      "reject decision-needed responses that smuggle partial classifications or resolved references" in {
        Given("a decision-needed alternative with forbidden enrichment or host replacement references")
        val prepared = take(R.prepareC(_context, _pending(), _result))
        val judgment = _judgment(prepared).copy(decision = R.Decision.DecisionNeeded)

        When("partial assignments and replacement data are separately admitted as DecisionNeeded")
        val assignments = R.admitC(prepared, _result,
          R.ResolutionReferences(None, None), judgment)
        val references = R.admitC(prepared, _result,
          _references, judgment.copy(assignments = Vector.empty))

        Then("neither path can enter the deterministic objective continuation")
        assignments shouldBe a[Consequence.Failure[?]]
        references shouldBe a[Consequence.Failure[?]]
      }
    }

    "constructed-data ownership" which {
      "reject absent inputs and prevent caller-created semantic admission or continuation" in {
        Given("private constructed semantic and objective-continuation types rather than raw participant records")

        When("absent preparation and admitted-result values reach the pure boundaries")
        val prepared = R.prepareC(_context, null, _result)
        val continued = W.continueReasoningC(null)

        Then("a raw or missing record is not an admitted classification or execution capability")
        prepared shouldBe a[Consequence.Failure[?]]
        continued shouldBe a[Consequence.Failure[?]]
        assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitReasoningSemantic.Prepared(null, null)")
        assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitReasoningSemantic.Admitted(null, null, null, null)")
        assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPlanningAdvance.ReasoningContinued(null, null)")
      }
    }
  }
}
