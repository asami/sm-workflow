package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workspace.{FileWriteConflict, FileWriteOutcome}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseDocumentProgress as P
import GoalPhaseDocumentSource as D
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Pure controller/merge inputs are executable business specifications, not
  * native persistence, provider, Git or SBT evidence.
  * @since   Oct. 17, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseDocumentProgressSpec extends AnyWordSpec with GivenWhenThen with Matchers {
  private def _oid(value: Char): String = value.toString * 40
  private val _source = D.Source("docs/phase.md", "docs/checklist.md")
  private val _paths = Vector(_source.phasePath, _source.checklistPath)
  private val _plan = O.Plan("progress-plan", "1", "PHASE-6.1", "sm-workflow",
    Vector(O.Step("S1", "First boundary", _paths :+ "one.txt", Vector.empty, Vector("test")),
      O.Step("S2", "Second boundary", _paths :+ "two.txt", Vector("S1"), Vector("test"))), Vector("test"))
  private val _documents = D.Bound(_source,
    Vector(GoalPhaseDocumentPlan.Binding("S1", Vector("VERIFY-B", "VERIFY-A")),
      GoalPhaseDocumentPlan.Binding("S2", Vector("VERIFY-C"))),
    Vector(GoalPhaseDocumentPlan.Item("VERIFY-A", "Accept A", false),
      GoalPhaseDocumentPlan.Item("VERIFY-B", "Accept B", true),
      GoalPhaseDocumentPlan.Item("VERIFY-C", "Accept C", false)))
  private def _initial: O.State = take(O.startC(_plan, O.Snapshot(_oid('a'), _oid('b'))))
  private def _key(state: O.State): O.WorkKey = state.next.asInstanceOf[O.Semantic].key
  private def _close(state: O.State, identity: Char, tree: Char): O.State = {
    val implemented = take(state.acceptC(O.SemanticCompleted(_key(state), "Assigned implementation complete")))
    val snapshot = O.Snapshot(state.tipSnapshot.head, _oid(tree))
    val inspected = take(implemented.acceptC(O.CandidateObserved(snapshot)))
    val checked = take(inspected.acceptC(O.ChecksObserved(snapshot, Vector("test"), "checks-" + identity, true)))
    val reviewed = take(checked.acceptC(O.ReviewAdmitted(_key(checked), snapshot, "checks-" + identity,
      "review-" + identity, O.ReviewOutcome.Accept)))
    val intent = "intent-" + identity
    val prepared = take(reviewed.acceptC(O.ClosureIntentSaved(snapshot, intent)))
    take(prepared.acceptC(O.StepCommitObserved(intent, O.Commit(_oid(identity), state.tipSnapshot.head, _oid(tree)))))
  }
  private def _block(value: P.Projection, newline: String = "\n"): String =
    "```sm-workflow-progress" + newline + value.asJson.spaces2.replace("\n", newline) + newline + "```" + newline
  private def _empty: P.Projection = P.Projection(_plan.phase, Vector.empty)
  private def _reject(value: Consequence[?]): Unit = value shouldBe a[Consequence.Failure[?]]

  "Document progress business projection" should {
    "project only event-derived completed Steps in native order with stable sorted item membership" in {
      Given("a two-Step controller with actual pure semantic/check/review/commit events and captured bindings")
      val state = _close(_close(_initial, 'c', 'd'), 'e', 'f')
      val reordered = _documents.copy(bindings = _documents.bindings.reverse.map(row => row.copy(items = row.items.reverse)),
        items = _documents.items.reverse.map(item => item.copy(checked = !item.checked)))
      When("progress is projected from completed controller facts with either presentation")
      val desired = take(P.projectionC(state, _documents))
      val neutral = take(P.projectionC(state, reordered))
      Then("only native completion order, protected membership, commit and independent review appear")
      desired shouldBe neutral
      desired.steps.map(_.step) shouldBe Vector("S1", "S2")
      desired.steps.head.items shouldBe Vector("VERIFY-A", "VERIFY-B")
      desired.steps.map(_.commit) shouldBe state.completed.map(_.commit)
      desired.steps.map(_.review) shouldBe Vector(Some("review-c"), Some("review-e"))
      take(P.projectionC(_initial, _documents)).steps shouldBe empty
      desired.asJson.as[P.Projection] shouldBe Right(desired)
    }

    "merge into the latest CRLF checklist while preserving all independently edited bytes outside the body" in {
      Given("a prior genuine projection inside a latest checklist with reordered items, checkmarks and narrative")
      val one = _close(_initial, 'c', 'd')
      val previous = take(P.projectionC(one, _documents))
      val desired = take(P.projectionC(_close(one, 'e', 'f'), _documents))
      val prefix = "# Revised narrative\r\n\r\n- [x] VERIFY-C: Accept C\r\n- [ ] VERIFY-B: Accept B\r\n" +
        "~~~markdown\r\n```sm-workflow-progress\r\nexample only\r\n```\r\n~~~\r\n```sm-workflow-progress\r\n"
      val suffix = "```\r\n- [x] VERIFY-A: Accept A\r\nIndependent tail remains.\r\n"
      val current = prefix + previous.asJson.noSpaces + "\r\n" + suffix
      When("the next completed prefix is merged using the current document boundary offsets")
      val merged = take(P.mergeC(current, _plan.phase, Some(previous), desired))
      Then("the entire latest prefix and suffix survive byte-for-byte and only the body contains new facts")
      merged.enabled shouldBe true
      merged.replacement shouldBe Some(prefix + desired.asJson.spaces2.replace("\n", "\r\n") + "\r\n" + suffix)
      take(P.mergeC(merged.replacement.get, _plan.phase, Some(desired), desired)).replacement shouldBe None
    }

    "preserve legacy read-only documents and empty first opt-in formatting without a mechanical no-op" in {
      Given("no actual prior projection and documents either without an opt-in or with an empty formatted block")
      val legacy = "Narrative only.\n~~~~markdown\n" + _block(_empty) + "~~~~\n"
      val empty = "```sm-workflow-progress\r\n{\"steps\":[],\"phase\":\"PHASE-6.1\",\"schemaVersion\":\"sm-workflow.phase-progress.v1\"}\r\n```\r\n"
      When("the actual empty completion prefix is merged")
      val absent = take(P.mergeC(legacy, _plan.phase, None, _empty))
      val opted = take(P.mergeC(empty, _plan.phase, None, _empty))
      Then("legacy mode is disabled and opt-in mode retains its exact existing formatting without a write")
      absent shouldBe P.Merged(false, None)
      opted shouldBe P.Merged(true, None)
      When("a first genuine Step completion arrives at the empty initial block")
      val desired = take(P.projectionC(_close(_initial, 'c', 'd'), _documents))
      val updated = take(P.mergeC(empty, _plan.phase, None, desired))
      Then("the empty opt-in receives precisely the controller-derived Step")
      updated.replacement shouldBe Some(_block(desired, "\r\n"))
    }

    "reject duplicate malformed unknown contradictory and input-supplied progress fields" in {
      Given("an initial empty opt-in and a genuine one-Step desired projection")
      val desired = take(P.projectionC(_close(_initial, 'c', 'd'), _documents))
      val unknown = _empty.asJson.mapObject(_.add("claim", Json.True))
      val baditems = desired.steps.head.copy(items = Vector(""))
      val badcommit = desired.steps.head.copy(commit = desired.steps.head.commit.copy(tree = "bad"))
      val invalid = Vector(_block(_empty) + _block(_empty), "```sm-workflow-progress\n{\n```\n",
        "```sm-workflow-progress\n" + unknown.noSpaces + "\n```\n",
        _block(_empty.copy(phase = "PHASE-other")), _block(desired),
        _block(desired.copy(steps = Vector(baditems))),
        _block(desired.copy(steps = desired.steps ++ desired.steps)),
        _block(desired.copy(steps = Vector(badcommit))),
        "```sm-workflow-progress\nnull\n```\n", "```sm-workflow-progress\n{}\n")
      When("each current text is merged without an actual prior successful projection")
      val rejected = invalid.map(P.mergeC(_, _plan.phase, None, desired))
      Then("ordinary failures reject every attempted initial execution claim or malformed owned block")
      rejected.foreach(_reject)
    }

    "detect owned overlap and removal even when the altered field already equals the new desired value" in {
      Given("a genuine previous Step prefix and a new completed second Step")
      val one = _close(_initial, 'c', 'd')
      val previous = take(P.projectionC(one, _documents))
      val desired = take(P.projectionC(_close(one, 'e', 'f'), _documents))
      val altered = previous.copy(steps = previous.steps.map(_.copy(review = Some("different-review"))))
      When("the block is removed, its review is edited, or desired facts are pasted over the previous prefix")
      val missing = P.mergeC("Independent narrative only.\n", _plan.phase, Some(previous), desired)
      val overlap = P.mergeC(_block(altered), _plan.phase, Some(previous), desired)
      val pasted = P.mergeC(_block(desired), _plan.phase, Some(previous), desired)
      Then("typed comparison requires the previous actual successful projection in every case")
      Vector(missing, overlap, pasted).foreach(_reject)
    }

    "require both exact source filenames in every Step and the fixed host staging policy" in {
      Given("valid source bindings with two explicitly owned filenames")
      val implicitcoverage = _plan.copy(steps = _plan.steps.map(_.copy(ownedPaths = Vector("docs"))))
      val missingstep = _plan.copy(steps = _plan.steps.updated(1, _plan.steps(1).copy(ownedPaths = Vector(_source.phasePath))))
      When("policy is obtained for complete coverage and for each missing explicit source grant")
      val valid = take(P.policyC(_plan, _documents, _paths))
      val failures = Vector(P.policyC(implicitcoverage, _documents, _paths),
        P.policyC(missingstep, _documents, _paths), P.policyC(_plan, _documents, Vector(_source.checklistPath)))
      Then("only literal all-Step and host membership admits deterministic Phase/checklist order")
      valid.paths shouldBe _paths
      failures.foreach(_reject)
    }

    "admit a bounded management candidate while preserving the general same-tree guard" in {
      Given("all ordered Steps closed through pure events and a different staged whole-Phase document tree")
      val state = _close(_close(_initial, 'c', 'd'), 'e', 'f')
      val snapshot = O.Snapshot(state.tipSnapshot.head, _oid('1'))
      When("the dedicated bounded event or the old general event observes that changed tree")
      val admitted = take(state.acceptC(O.PhaseManagementCandidateObserved(snapshot, _paths)))
      val general = state.acceptC(O.CandidateObserved(snapshot))
      val invalid = Vector(Vector.empty[String], Vector("../escape"), Vector("other.txt"), _paths ++ _paths)
        .map(paths => state.acceptC(O.PhaseManagementCandidateObserved(snapshot, paths)))
      Then("only distinct valid explicitly all-Step owned management paths enter PhaseValidation")
      admitted.stage shouldBe O.Stage.PhaseValidation
      admitted.candidate shouldBe Some(snapshot)
      admitted.base shouldBe state.base
      admitted.completed shouldBe state.completed
      _reject(general)
      invalid.foreach(_reject)
      _reject(state.acceptC(O.PhaseManagementCandidateObserved(state.tipSnapshot, _paths)))
      take(state.acceptC(O.CandidateObserved(state.tipSnapshot))).stage shouldBe O.Stage.PhaseValidation
    }

    "classify actual File outcomes without converting creation or conflicts into successful updates" in {
      Given("typed File outcomes at the subordinate current-text update boundary")
      val outcomes = Vector(FileWriteOutcome.Updated, FileWriteOutcome.Conflict(FileWriteConflict.Changed),
        FileWriteOutcome.Conflict(FileWriteConflict.Missing))
      When("the intent owner consumes those outcomes and unsupported creation outcomes")
      val recorded = outcomes.map(value => take(GoalPhaseNativeDocumentProgress.outcomeC(value)))
      val unexpected = Vector(FileWriteOutcome.Created, FileWriteOutcome.Conflict(FileWriteConflict.AlreadyExists))
        .map(GoalPhaseNativeDocumentProgress.outcomeC)
      Then("updated changed and missing remain distinct and unknown effects fail without success")
      recorded shouldBe Vector("updated", "changed", "missing")
      unexpected.foreach(_reject)
    }
  }
}
