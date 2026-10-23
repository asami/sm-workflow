package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeValidation as N
import GoalPhaseNativeWork as W
import WorkflowSbtValidation as T
import RepositorySyncNativeMergeValidation as V
import GoalPhaseAcceptanceBoundary as A

/** Pure actual-shaped typed receipt and codec examples only. These are not
  * external command, installed task or real AI/provider execution evidence.
  * @since   Oct. 17, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseNativeCheckRepairSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail("Unexpected fixture admission: " + failure)
  }
  private def _oid(value: Char): String = value.toString * 40
  private val _base = O.Snapshot(_oid('a'), _oid('b'))
  private val _candidate = O.Snapshot(_base.head, _oid('c'))
  private val _checks = Vector("prepare", "selected", "after")
  private val _plan = O.Plan("selected-plan", "1", "PHASE-6.1", "owned-repository",
    Vector(O.Step("S1", "Correct the selected boundary", Vector("src/one.scala"), Vector.empty, _checks)),
    _checks, repairLimit = 1)
  private def _key(state: O.State): O.WorkKey = state.next.asInstanceOf[O.Semantic].key
  private def _state(plan: O.Plan = _plan): O.State = {
    val initial = _take(O.startC(plan, _base))
    val ready = _take(initial.acceptC(O.SemanticCompleted(_key(initial), "Implementation ready")))
    _take(ready.acceptC(O.CandidateObserved(_candidate)))
  }
  private def _receipt(identity: String, kind: T.Kind, outcome: T.Outcome, exit: Int): V.Receipt = {
    val selection = if (kind == T.Kind.TestOnly && outcome == T.Outcome.Succeeded)
      Some(T.Selection(Vector("example.SelectedSpec"), 1, 2L)) else None
    V.Receipt(identity, "exited", Some(exit), "actual-shaped raw stdout", "actual-shaped raw diagnostic",
      Some(T.Facts(kind, outcome, selection, None)))
  }
  private def _payload(kind: T.Kind = T.Kind.Compile): N.Payload = N.Payload("12", "S1", _candidate,
    Vector(N.Check("prepare", 1, T.Kind.Compile), N.Check("selected", 2, kind), N.Check("after", 3, T.Kind.Test)),
    Vector(_receipt("prepare", T.Kind.Compile, T.Outcome.Succeeded, 0),
      _receipt("selected", kind, T.Outcome.Failed, 1)), Some("repair-required"))
  private def _context(payload: N.Payload = _payload()): W.CheckFailureContext =
    _take(W.checkFailureContextC(Vector("src/one.scala"), "diff --git a/src/one.scala b/src/one.scala\n+correct source", payload))
  private def _request(context: Option[W.CheckFailureContext] = Some(_context())): W.Request =
    W.Request("actual-instance", "14", "RepairStep", Some("S1"), 2, "Correct the selected boundary", _plan,
      _base, Vector.empty, None, None, Some(O.RepairDirective("Correct selected failure", Vector("Fix actual diagnostics"))),
      checkFailure = context)
  private val _result = W.Result("completed", "Selected source correction completed", Vector.empty)
  private val _review = GoalPhaseNativeReview.Context(Vector("src/one.scala"), "Actual reviewed diff", Vector.empty)
  private def _replace_payload(wire: Json, payload: Json): Json = wire.mapObject(_.add("payload", payload))

  "Structured issues on the selected native work path" should {
    "account for TEST and independent REVIEW issues together while FIXED waits for their actual evidence" in {
      Given("an actual-shaped selected typed failure followed by an independently admitted required finding")
      val test = _take(GoalPhaseFixIssues.validationC("actual-instance", _plan.repository, None, _payload(), "selected-failed"))
      val reviewrequest = _request(None).copy(kind = "ReviewStep", issues = test, review = Some(_review))
      val both = _take(GoalPhaseFixIssues.completedC(reviewrequest,
        W.Result("repair", "Required independent finding", Vector("Repair required review behavior")), "independent-review"))
      val request = _request().copy(issues = both)
      val report = GoalPhaseFixIssues.Report(GoalPhaseFixIssues.assigned(request).map(issue =>
        GoalPhaseFixIssues.Disposition(issue.identity, "FIXED", "Specification-owned correction of this issued issue", None)))
      val result = _result.copy(issueReport = Some(report))
      When("the actual-shaped repair Request and explicit Result cross codecs and native semantic admission")
      val saved = W.inputCodec.decode(W.inputCodec.encode(request))
      val admitted = W.admitSemanticResultC(request, result)
      val fixed = _take(GoalPhaseFixIssues.completedC(request, result, "repair-result"))
      Then("both sources use one exact accounting path and claims alone resolve neither issue")
      saved shouldBe Right(request)
      admitted shouldBe Consequence.unit
      report.dispositions.map(_.issueIdentity) shouldBe Vector("issue-1", "issue-2")
      fixed.get.issues.map(_.source) shouldBe Vector("TEST", "REVIEW")
      fixed.get.issues.map(_.status) shouldBe Vector("OPEN", "OPEN")
      fixed.get.movement.get.resolvedIssues shouldBe 0

      Given("fresh complete selected positive checks after that repair")
      val positive = _payload().copy(receipts = _payload().selected.map(c =>
        _receipt(c.identity, c.kind, T.Outcome.Succeeded, 0)), outcome = Some("passed"))
      When("the validation boundary is traversed and independent re-review explicitly covers its REVIEW issue")
      val checked = _take(GoalPhaseFixIssues.validationC("actual-instance", _plan.repository, fixed, positive, "fresh-validation"))
      val rereview = reviewrequest.copy(issues = checked)
      val coverage = W.Result("accept", "Independently observed required behavior", Vector.empty,
        issueReport = Some(GoalPhaseFixIssues.Report(Vector(GoalPhaseFixIssues.Disposition(
          "issue-2", "RESOLVED", "Observed actual corrected required behavior", None)))))
      val completed = _take(GoalPhaseFixIssues.completedC(rereview, coverage, "fresh-independent-review"))
      Then("fresh TEST resolves only its matching issue and independent coverage resolves the remaining REVIEW issue")
      checked.get.issues.map(_.status) shouldBe Vector("RESOLVED", "OPEN")
      checked.get.issues.map(_.identity) shouldBe both.get.issues.map(_.identity)
      completed.get.issues.map(_.status) shouldBe Vector("RESOLVED", "RESOLVED")
      completed.get.history.take(both.get.history.size) shouldBe both.get.history
      GoalPhaseFixIssues.completedC(rereview, coverage.copy(issueReport = None), "missing-coverage") shouldBe
        a[Consequence.Failure[?]]
    }
  }

  "Known typed Step check failure classification" should {
    Vector(T.Kind.Compile, T.Kind.Test).foreach { kind =>
      s"retain a terminated leading $kind failure before the remaining declared checks are selected" in {
        Given("an ordinary leading check with a real-shaped typed failure and a larger declared Step list")
        val payload = _payload().copy(selected = Vector(N.Check("prepare", 1, kind)),
          receipts = Vector(_receipt("prepare", kind, T.Outcome.Failed, 1)))
        When("the native owner admits that terminated prefix and retains its repair source")
        val repaired = _take(N.observedStateC(_state(), payload, "leading-failure"))
        val context = _context(payload)
        Then("ordinary Repair consumes one batch while the exact failed candidate and diagnostics remain in context")
        repaired.stage shouldBe O.Stage.Repair
        repaired.repairs shouldBe 1
        repaired.ordinaryRepairs shouldBe 1
        repaired.completed shouldBe empty
        repaired.reviewEvidence shouldBe empty
        context.validation shouldBe payload
        context.validation.candidate shouldBe _candidate
        context.validation.selected.map(_.identity) shouldBe Vector("prepare")
        context.validation.receipts shouldBe payload.receipts

        Given("the same Step after its ordinary repair and fresh candidate inspection")
        val corrected = _take(repaired.acceptC(O.SemanticCompleted(_key(repaired), "Corrected leading check source")))
        val exhausted = _take(corrected.acceptC(O.CandidateObserved(_candidate)))
        When("the next terminated leading failure exhausts the positive repair window")
        val limited = _take(N.observedStateC(exhausted,
          payload.copy(outcome = Some("repair-limit-reached")), "leading-limit"))
        Then("Decision retains that candidate and prior repair count without selecting later checks")
        limited.stage shouldBe O.Stage.Decision
        limited.candidate shouldBe Some(_candidate)
        limited.repairs shouldBe repaired.repairs
        limited.ordinaryRepairs shouldBe repaired.ordinaryRepairs
        limited.completed shouldBe repaired.completed
        limited.pendingRepair should not be empty
        limited.checksReceipt shouldBe Some("leading-limit")
      }
    }

    "retain actual incomplete and unverified prefix results at Decision without granting Repair" in {
      Given("returned ordinary-prefix receipts with complete captures and valid matching Incomplete or Unverified facts")
      val incomplete = N.Payload("12", "S1", _candidate, Vector(N.Check("prepare", 1, T.Kind.Test)),
        Vector(V.Receipt("prepare", "indeterminate", None, "actual-shaped partial output", "actual-shaped interruption",
          Some(T.Facts(T.Kind.Test, T.Outcome.Incomplete, None, None)))), Some("failed"))
      val unverified = N.Payload("12", "S1", _candidate, Vector(N.Check("prepare", 1, T.Kind.TestOnly)),
        Vector(V.Receipt("prepare", "exited", Some(0), "actual-shaped output", "",
          Some(T.Facts(T.Kind.TestOnly, T.Outcome.Unverified, None, Some("MissingSelection"))))), Some("failed"))
      When("the owning controller consumes each recorded non-success prefix")
      val decisions = Vector(incomplete, unverified).map(payload =>
        _take(N.observedStateC(_state(), payload, "recorded-prefix-non-success")))
      Then("both results reach Decision with their candidate and zero allocated repairs, without later metadata or Review")
      decisions.foreach { state =>
        state.stage shouldBe O.Stage.Decision
        state.candidate shouldBe Some(_candidate)
        state.repairs shouldBe 0
        state.pendingRepair shouldBe empty
        state.reviewEvidence shouldBe empty
      }
      Vector(incomplete, unverified).foreach { payload =>
        N.repairable(payload) shouldBe false
        N.observedStateC(_state(), payload.copy(outcome = Some("repair-required")), "invalid-prefix-repair").isSuccess shouldBe false
        N.codec.decode(N.codec.encode(payload)) shouldBe Right(payload)
      }

      Given("contradictory termination, exit, outcome, missing typed facts or an earlier unsuccessful receipt")
      val nonzero = unverified.copy(receipts = Vector(unverified.receipts.head.copy(exitCode = Some(1))))
      val wrongexit = incomplete.copy(receipts = Vector(incomplete.receipts.head.copy(exitCode = Some(1))))
      val wrongtermination = incomplete.copy(receipts = Vector(incomplete.receipts.head.copy(termination = "exited", exitCode = Some(0))))
      val missingfacts = incomplete.copy(receipts = Vector(incomplete.receipts.head.copy(sbt = None)))
      val falsesuccess = incomplete.copy(receipts = Vector(_receipt("prepare", T.Kind.Test, T.Outcome.Succeeded, 1)))
      val falsefailed = incomplete.copy(receipts = Vector(_receipt("prepare", T.Kind.Test, T.Outcome.Failed, 0)))
      val priorfailed = _payload().copy(selected = _payload().selected.take(2),
        receipts = Vector(_receipt("prepare", T.Kind.Compile, T.Outcome.Failed, 1),
          V.Receipt("selected", "indeterminate", None, "", "", Some(T.Facts(T.Kind.Compile, T.Outcome.Incomplete, None, None)))),
        outcome = Some("failed"))
      When("those malformed prefix results claim a completed failure")
      val rejected = Vector(nonzero, wrongexit, wrongtermination, missingfacts, falsesuccess, falsefailed, priorfailed)
        .map(payload => N.observedStateC(_state(), payload, "contradictory-prefix"))
      Then("a returned receipt cannot bypass the exact typed non-success shapes or the successful prior-prefix requirement")
      rejected.foreach(_.isSuccess shouldBe false)
    }

    "retain the complete ordinary selection when its first check fails before later ordinary checks" in {
      Given("two selected ordinary checks before later metadata and an actual first-check failure")
      val payload = _payload().copy(selected = _payload().selected.take(2),
        receipts = Vector(_receipt("prepare", T.Kind.Compile, T.Outcome.Failed, 1)))
      When("the prefix outcome is admitted and projected for public validation")
      val repair = _take(N.observedStateC(_state(), payload, "first-ordinary-failed"))
      val view = NativeWorkflowOperationOutput.goalValidation(payload)
      Then("the ordinary selected list stays intact while only the performed check has a receipt")
      repair.stage shouldBe O.Stage.Repair
      view.selectedChecks.map(_.identity) shouldBe Vector("prepare", "selected")
      view.checks.map(_.identity) shouldBe Vector("prepare")
      payload.metadata shouldBe empty
    }

    "refuse positive partial coverage and nonleading or pending prefix outcomes" in {
      Given("one successful ordinary leading check against three declared checks")
      val original = _payload()
      val prefix = original.copy(selected = original.selected.take(1),
        receipts = Vector(_receipt("prepare", T.Kind.Compile, T.Outcome.Succeeded, 0)), outcome = Some("passed"))
      When("partial success and completed outcomes from an intent without a result are offered")
      val positive = N.observedStateC(_state(), prefix, "partial-success")
      val pending = prefix.copy(receipts = Vector.empty, outcome = None)
      val pendingoutcomes = Vector(None, Some("passed"), Some("failed"), Some("repair-required"), Some("repair-limit-reached"))
        .map(outcome => N.observedStateC(_state(), pending.copy(outcome = outcome), "pending-prefix"))
      Then("neither omitted metadata nor an unrecorded result can become Review, Repair or Decision")
      positive.isSuccess shouldBe false
      pendingoutcomes.foreach(_.isSuccess shouldBe false)

      Given("terminated typed failure payloads with wrong, reordered, nonleading, extra, empty or crossed candidate selection")
      val failed = prefix.copy(receipts = Vector(_receipt("prepare", T.Kind.Compile, T.Outcome.Failed, 1)),
        outcome = Some("repair-required"))
      val nonleading = failed.copy(selected = Vector(N.Check("selected", 2, T.Kind.Compile)),
        receipts = Vector(_receipt("selected", T.Kind.Compile, T.Outcome.Failed, 1)))
      val reordered = original.copy(selected = original.selected.take(2).reverse,
        receipts = Vector(_receipt("selected", T.Kind.Compile, T.Outcome.Failed, 1)))
      val wrong = failed.copy(selected = Vector(N.Check("wrong", 1, T.Kind.Compile)),
        receipts = Vector(_receipt("wrong", T.Kind.Compile, T.Outcome.Failed, 1)))
      val extra = original.copy(selected = original.selected :+ N.Check("extra", 4))
      val invalid = Vector(nonleading, reordered, wrong, extra, failed.copy(selected = Vector.empty, receipts = Vector.empty),
        failed.copy(candidate = O.Snapshot(_base.head, _oid('e'))))
      When("the owner reconstructs those purported prefix failures")
      val rejected = invalid.map(N.observedStateC(_state(), _, "invalid-prefix"))
      Then("only an exact nonempty leading selection for the actual candidate can terminate before metadata selection")
      rejected.foreach(_.isSuccess shouldBe false)
    }

    "send a terminated whole-Phase prefix failure to Decision with its closed Step history" in {
      Given("an accepted Step followed by the actual whole-Phase candidate frontier")
      val passed = _take(_state().acceptC(O.ChecksObserved(_candidate, _checks, "checks", true)))
      val reviewed = _take(passed.acceptC(O.ReviewAdmitted(_key(passed), _candidate, "checks", "review", O.ReviewOutcome.Accept)))
      val intent = _take(reviewed.acceptC(O.ClosureIntentSaved(_candidate, "close")))
      val closed = _take(intent.acceptC(O.StepCommitObserved("close", O.Commit(_oid('d'), _base.head, _candidate.tree))))
      val snapshot = O.Snapshot(_oid('d'), _candidate.tree)
      val phase = _take(closed.acceptC(O.CandidateObserved(snapshot)))
      val payload = N.Payload("12", "phase:" + _plan.phase, snapshot,
        Vector(N.Check("prepare", 1, T.Kind.Test)), Vector(_receipt("prepare", T.Kind.Test, T.Outcome.Failed, 1)), Some("failed"))
      When("only that actual terminated leading check is retained as a failure")
      val decision = _take(N.observedStateC(phase, payload, "phase-leading-failure"))
      Then("Decision retains the Phase candidate and closed Step history while later checks remain unfinished")
      decision.stage shouldBe O.Stage.Decision
      decision.candidate shouldBe Some(snapshot)
      decision.completed shouldBe closed.completed
      decision.repairs shouldBe 0
      decision.reviewEvidence shouldBe empty
      val repair = _take(N.observedStateC(phase, payload.copy(outcome = Some("repair-required")), "phase-leading-repair"))
      repair.stage shouldBe O.Stage.Repair
      repair.completed shouldBe closed.completed
      repair.repairs shouldBe 1
      repair.cursor shouldBe repair.plan.steps.size
    }

    T.Kind.values.foreach { kind =>
      s"admit a terminated $kind failure after its positive typed prefix" in {
        Given("pure actual-shaped " + kind + " facts after a positive Compile receipt, with a later check unexecuted")
        val payload = _payload(kind)
        When("the owning pure classifier and outcome transition consume those facts")
        val repairable = N.repairable(payload)
        val state = _take(N.observedStateC(_state(), payload, "actual-validation-reference"))
        Then("exactly one semantic repair is assigned with actual identity, kind and exit, without review acceptance")
        repairable shouldBe true
        state.stage shouldBe O.Stage.Repair
        state.repairs shouldBe 1
        state.generation shouldBe 2
        state.candidate shouldBe empty
        state.checksReceipt shouldBe empty
        state.reviewEvidence shouldBe empty
        state.repairDirective.get.reason should include("selected")
        state.repairDirective.get.reason should include(kind.wire)
        state.repairDirective.get.reason should include("exit 1")
        state.repairDirective.get.findings.head should include("runtime must inspect the fresh candidate")
        payload.receipts.size shouldBe 2
        payload.selected.size shouldBe 3
      }
    }

    "admit a first-check failure with no fabricated positive result" in {
      Given("a typed failure on the first selected check")
      val original = _payload()
      val payload = original.copy(receipts = Vector(_receipt("prepare", T.Kind.Compile, T.Outcome.Failed, 2)))
      When("known failure facts are classified")
      val result = N.repairable(payload)
      Then("the nonempty negative prefix qualifies without inventing any later result")
      result shouldBe true
      payload.receipts.size shouldBe 1
    }

    val invalid: Vector[(String, N.Payload => N.Payload)] = Vector(
      "legacy final facts" -> (p => p.copy(receipts = p.receipts.updated(1, p.receipts(1).copy(sbt = None)))),
      "legacy positive prefix" -> (p => p.copy(selected = p.selected.updated(0, p.selected.head.copy(kind = T.Kind.Test)),
        receipts = p.receipts.updated(0, p.receipts.head.copy(sbt = None)))),
      "zero exit failed" -> (p => p.copy(receipts = p.receipts.updated(1, p.receipts(1).copy(exitCode = Some(0))))),
      "missing exit" -> (p => p.copy(receipts = p.receipts.updated(1, p.receipts(1).copy(exitCode = None)))),
      "timeout" -> (p => p.copy(receipts = p.receipts.updated(1, p.receipts(1).copy(termination = "timed-out")))),
      "incomplete" -> (p => p.copy(receipts = p.receipts.updated(1, _receipt("selected", T.Kind.Compile, T.Outcome.Incomplete, 1)))),
      "unverified tests" -> (p => p.copy(selected = p.selected.updated(1, p.selected(1).copy(kind = T.Kind.TestOnly)),
        receipts = p.receipts.updated(1, V.Receipt("selected", "exited", Some(0), "raw", "raw",
          Some(T.Facts(T.Kind.TestOnly, T.Outcome.Unverified, None, Some("MissingSelection"))))))),
      "crossed identity" -> (p => p.copy(receipts = p.receipts.updated(1, p.receipts(1).copy(identity = "after")))),
      "crossed kind" -> (p => p.copy(receipts = p.receipts.updated(1, _receipt("selected", T.Kind.Test, T.Outcome.Failed, 1)))),
      "reordered receipts" -> (p => p.copy(receipts = p.receipts.reverse)),
      "empty results" -> (p => p.copy(receipts = Vector.empty)),
      "partial positive only" -> (p => p.copy(receipts = p.receipts.take(1))),
      "negative prefix" -> (p => p.copy(receipts = p.receipts.updated(0, _receipt("prepare", T.Kind.Compile, T.Outcome.Failed, 1)))),
      "result after failure" -> (p => p.copy(receipts = p.receipts :+ _receipt("after", T.Kind.Test, T.Outcome.Succeeded, 0))),
      "malformed typed facts" -> (p => p.copy(receipts = p.receipts.updated(1,
        p.receipts(1).copy(sbt = Some(T.Facts(T.Kind.Compile, T.Outcome.Failed, None, Some("unexpected"))))))),
      "malformed raw capture" -> (p => p.copy(receipts = p.receipts.updated(1, p.receipts(1).copy(stderr = null)))),
      "null receipt" -> (p => p.copy(receipts = Vector(null))),
      "null selected" -> (p => p.copy(selected = null)),
      "duplicate selected" -> (p => p.copy(selected = p.selected.updated(1, p.selected.head))),
      "null typed kind" -> (p => p.copy(selected = p.selected.updated(1, p.selected(1).copy(kind = null)))),
      "missing receipts" -> (p => p.copy(receipts = null)))
    invalid.foreach { (label, change) =>
      s"refuse repair classification for $label" in {
        Given("pure actual-shaped selected facts altered to contain " + label)
        val payload = change(_payload())
        When("classification and a repair-required transition are requested")
        val repairable = N.repairable(payload)
        val observed = N.observedStateC(_state(), payload, "receipt")
        Then("unknown, missing, crossed or contradictory facts cannot authorize semantic repair")
        repairable shouldBe false
        observed.isSuccess shouldBe false
      }
    }

    "ignore diagnostic prose when typed facts do not establish failure" in {
      Given("raw stdout claiming a compiler failure but only positive typed receipts")
      val original = _payload()
      val payload = original.copy(receipts = Vector(_receipt("prepare", T.Kind.Compile, T.Outcome.Succeeded, 0)
        .copy(stdout = "[error] fabricated-looking failure prose")))
      When("the typed classifier consumes those receipts")
      val result = N.repairable(payload)
      Then("stdout text creates neither diagnosis nor repair authority")
      result shouldBe false
    }

    "keep saved failed outcomes at Decision and reject missing result outcomes" in {
      Given("known typed failure facts retained with the historical failed outcome")
      val payload = _payload().copy(outcome = Some("failed"))
      When("the shared owner reconstructs that saved outcome")
      val state = _take(N.observedStateC(_state(), payload, "saved-reference"))
      Then("historical failure stays Decision and consumes no repair")
      N.repairable(payload) shouldBe true
      state.stage shouldBe O.Stage.Decision
      state.repairs shouldBe 0
      state.pendingRepair shouldBe empty
      state.acceptC(O.RepairContinuationAdmitted("cannot-retrofit-consent")).isSuccess shouldBe false
      Given("the same facts without a completed result outcome")
      When("reconstruction is attempted")
      val unknown = N.observedStateC(_state(), payload.copy(outcome = None), "intent-only")
      Then("an unknown result cannot become repair or review")
      unknown.isSuccess shouldBe false
    }

    "require complete positive selected checks before independent review" in {
      Given("the full selected typed positive receipt set")
      val original = _payload(T.Kind.TestOnly)
      val passed = original.copy(receipts = original.selected.map(c => _receipt(c.identity, c.kind, T.Outcome.Succeeded, 0)),
        outcome = Some("passed"))
      When("the shared owner consumes the complete and partial positive sets")
      val complete = _take(N.observedStateC(_state(), passed, "positive-reference"))
      val partial = N.observedStateC(_state(), passed.copy(receipts = passed.receipts.take(1)), "partial")
      val falserepair = N.observedStateC(_state(), passed.copy(outcome = Some("repair-required")), "false-repair")
      Then("only the complete positive set enters Review")
      complete.stage shouldBe O.Stage.Review
      complete.checksReceipt shouldBe Some("positive-reference")
      complete.reviewEvidence shouldBe empty
      partial.isSuccess shouldBe false
      falserepair.isSuccess shouldBe false
    }

    "retain only genuine exhausted positive Step failures for bounded continuation" in {
      Given("a known failure with no configured repair allowance")
      val payload = _payload()
      val zero = _state(_plan.copy(repairLimit = 0))
      When("repair-required and ordinary failed outcomes are reconstructed")
      val norepair = N.observedStateC(zero, payload, "zero-budget")
      val decision = _take(N.observedStateC(zero, payload.copy(outcome = Some("failed")), "zero-failed"))
      Then("only Decision is available without an allowance")
      norepair.isSuccess shouldBe false
      decision.stage shouldBe O.Stage.Decision
      decision.pendingRepair shouldBe empty
      N.observedStateC(zero, payload.copy(outcome = Some("repair-limit-reached")), "zero-continuation").isSuccess shouldBe false
      Given("the same selected Step after its sole repair and fresh inspection")
      val repair = _take(N.observedStateC(_state(), payload, "first"))
      val corrected = _take(repair.acceptC(O.SemanticCompleted(_key(repair), "Corrected source")))
      val exhausted = _take(corrected.acceptC(O.CandidateObserved(_candidate)))
      When("another known failure requests repair")
      val again = N.observedStateC(exhausted, payload, "second")
      val limitedpayload = payload.copy(outcome = Some("repair-limit-reached"))
      val limited = _take(N.observedStateC(exhausted, limitedpayload, "exhausted-checks"))
      Then("the distinct exhausted outcome retains original facts without allocating another batch")
      again.isSuccess shouldBe false
      exhausted.repairs shouldBe 1
      limited.stage shouldBe O.Stage.Decision
      limited.repairs shouldBe 1
      limited.ordinaryRepairs shouldBe 1
      limited.pendingRepair shouldBe limited.repairDirective
      limited.pendingRepair should not be empty
      limited.candidate shouldBe Some(payload.candidate)
      limited.checksReceipt shouldBe Some("exhausted-checks")
      limited.acceptC(O.DecisionResumptionAdmitted("wrong-choice")).isSuccess shouldBe false
      NativeWorkflowOperationOutput.goalValidation(limitedpayload).outcome shouldBe Some("repair-limit-reached")
      When("the existing codec retains the new outcome and independently admitted consent renews the window")
      val restored = N.codec.decode(N.codec.encode(limitedpayload))
      val resumed = _take(limited.acceptC(O.RepairContinuationAdmitted("verified-continue")))
      Then("the same raw diagnostics survive and the first renewed repair is already counted")
      restored shouldBe Right(limitedpayload)
      resumed.repairs shouldBe 1
      resumed.ordinaryRepairs shouldBe 1
      resumed.stage shouldBe O.Stage.Repair
      resumed.generation shouldBe limited.generation + 1
      resumed.candidate shouldBe empty
      resumed.reviewEvidence shouldBe empty
      resumed.acceptC(O.RepairContinuationAdmitted("same-answer")).isSuccess shouldBe false
      Given("the Step closes through passing checks and independent review before whole-Phase validation")
      val passed = _take(_state().acceptC(O.ChecksObserved(_candidate, _checks, "checks", true)))
      val accepted = _take(passed.acceptC(O.ReviewAdmitted(_key(passed), _candidate, "checks", "review", O.ReviewOutcome.Accept)))
      val intent = _take(accepted.acceptC(O.ClosureIntentSaved(_candidate, "close")))
      val closedstep = _take(intent.acceptC(O.StepCommitObserved("close", O.Commit(_oid('d'), _base.head, _candidate.tree))))
      val phasesnapshot = O.Snapshot(_oid('d'), _candidate.tree)
      val phase = _take(closedstep.acceptC(O.CandidateObserved(phasesnapshot)))
      val phasepayload = payload.copy(step = "phase:" + _plan.phase, candidate = phasesnapshot)
      When("whole-Phase known failure is reconstructed")
      val phaserepair = N.observedStateC(phase, phasepayload, "phase-repair")
      val phasedecision = _take(N.observedStateC(phase, phasepayload.copy(outcome = Some("failed")), "phase-failed"))
      Then("explicit known repair retains closed Steps while the legacy failed outcome remains Decision")
      val release = _take(phaserepair)
      release.stage shouldBe O.Stage.Repair
      release.completed shouldBe closedstep.completed
      release.repairs shouldBe 1
      N.observedStateC(phase, phasepayload.copy(outcome = Some("repair-limit-reached")), "phase-limit").isSuccess shouldBe false
      phasedecision.stage shouldBe O.Stage.Decision
      phasedecision.completed shouldBe closedstep.completed
      phasedecision.repairs shouldBe 0
    }

    "reject fabricated exhausted outcomes without the actual exhausted matching typed frontier" in {
      Given("typed Step failure facts and a new repair-limit-reached outcome below the selected limit")
      val payload = _payload().copy(outcome = Some("repair-limit-reached"))
      When("the new outcome is offered before any repair is reserved")
      val early = N.observedStateC(_state(), payload, "premature-limit")
      Then("an outcome string alone cannot mint continuation authority")
      early.isSuccess shouldBe false
      val repair = _take(N.observedStateC(_state(), _payload(), "first"))
      val exhausted = _take(_take(repair.acceptC(O.SemanticCompleted(_key(repair), "Corrected")))
        .acceptC(O.CandidateObserved(_candidate)))
      invalid.foreach { (label, change) =>
        Given("the actual exhausted frontier but " + label)
        When("that malformed or unknown receipt claims repair-limit-reached")
        val result = N.observedStateC(exhausted, change(payload), "false-limit")
        Then("only genuine matching typed failure facts can create the question")
        result.isSuccess shouldBe false
      }
      Given("an unknown saved outcome string with otherwise genuine typed failure facts")
      When("the strict retained codec and observation consume it")
      val unknown = payload.copy(outcome = Some("unknown"))
      Then("unknown outcomes are rejected without a decision authority upgrade")
      N.codec.decode(N.codec.encode(unknown)).isLeft shouldBe true
      N.observedStateC(exhausted, unknown, "unknown").isSuccess shouldBe false
    }

    "reject mismatched selected frontier identities and missing receipt identity" in {
      val alterations = Vector(
        _payload().copy(candidate = O.Snapshot(_base.head, _oid('e'))),
        _payload().copy(step = "S2"),
        _payload().copy(selected = _payload().selected.updated(1, N.Check("another", 2, T.Kind.Compile))))
      alterations.foreach { payload =>
        Given("a payload that crosses the selected candidate, Step or checks")
        When("the shared owner reconstructs it")
        val result = N.observedStateC(_state(), payload, "reference")
        Then("the assigned Validation frontier cannot be replaced")
        result.isSuccess shouldBe false
      }
      Given("otherwise qualified failure facts with no receipt reference")
      When("reconstruction is attempted")
      val missing = N.observedStateC(_state(), _payload(), "")
      Then("no repair is assigned from an unnamed result")
      missing.isSuccess shouldBe false
    }
  }

  "Selected check repair progress" should {
    "retain actual first-batch counts alongside the existing typed failure context and semantic result" in {
      Given("an actual-shaped selected check failure consumed by its owning ordered controller")
      val controller = _take(N.observedStateC(_state(), _payload(), "actual-selected-failure"))
      val request = _request().copy(repairProgress = Some(GoalPhaseRepairProgress.snapshot(controller)))
      When("that assignment crosses the unchanged Work codec and semantic result admission")
      val saved = W.inputCodec.decode(W.inputCodec.encode(request))
      val admitted = W.admitSemanticResultC(request, _result)
      Then("selected progress is read-only context and does not replace issues, checks or semantic completion")
      saved shouldBe Right(request)
      saved.toOption.get.repairProgress shouldBe Some(GoalPhaseRepairProgress.Snapshot(1, 1, 1))
      saved.toOption.get.checkFailure shouldBe request.checkFailure
      admitted shouldBe Consequence.unit
      W.resultCodec.encode(_result).hcursor.downField("payload").downField("repairProgress").focus shouldBe None
      Given("a present invalid assignment snapshot rather than an absent optional context")
      When("normal semantic completion consumes the assignment")
      val invalid = Vector(request.copy(repairProgress = null), request.copy(repairProgress = Some(null)),
        request.copy(repairProgress = Some(GoalPhaseRepairProgress.Snapshot(2, 1, 1))))
        .map(W.admitSemanticResultC(_, _result))
      Then("every invalid progress admission fails before semantic work is accepted")
      invalid.foreach(_.isSuccess shouldBe false)
    }
  }

  "Separate retained check failure work context" should {
    "accept exhausted context only with its matching typed failure facts" in {
      Given("the retained exhausted Step failure and original diff")
      val payload = _payload().copy(outcome = Some("repair-limit-reached"))
      val request = _request(Some(_context(payload)))
      When("that repair request crosses the saved codec and semantic completion boundary")
      val decoded = W.inputCodec.decode(W.inputCodec.encode(request))
      val completed = W.admitSemanticResultC(request, _result)
      Then("the exact exhausted diagnostics remain the repair source without changing schemas")
      decoded shouldBe Right(request)
      completed.isSuccess shouldBe true
      decoded.toOption.get.checkFailure.get.validation shouldBe payload
      Given("the same context with missing typed failure, an old failed outcome or a crossed Step")
      val malformed = payload.copy(receipts = payload.receipts.updated(1, payload.receipts.last.copy(sbt = None)))
      When("context or semantic result admission is attempted")
      Then("the new outcome does not excuse any actual context requirement")
      W.checkFailureContextC(Vector("src/one.scala"), "actual diff", malformed).isSuccess shouldBe false
      W.checkFailureContextC(Vector("src/one.scala"), "actual diff", payload.copy(outcome = Some("failed"))).isSuccess shouldBe false
      W.admitSemanticResultC(request.copy(stepIdentity = Some("another-step")), _result).isSuccess shouldBe false
    }
    "roundtrip old absent and new context requests without changing the saved schema" in {
      Given("an old Request with no new context field and a new failed-check Request")
      val old = _request(None).copy(kind = "ImplementStep", repairDirective = None)
      val fresh = _request()
      When("the existing v2 codec encodes and decodes both")
      val oldwire = W.inputCodec.encode(old)
      val newwire = W.inputCodec.encode(fresh)
      val olddecoded = W.inputCodec.decode(oldwire)
      val newdecoded = W.inputCodec.decode(newwire)
      Then("absent remains absent and actual retained nested facts survive exactly")
      oldwire.hcursor.downField("payload").downField("checkFailure").focus shouldBe None
      oldwire.hcursor.get[String]("schemaVersion").toOption shouldBe Some("sm-workflow.goal-phase-native-work-request.v2")
      olddecoded shouldBe Right(old)
      newdecoded shouldBe Right(fresh)
      W.inputCodec.encode(olddecoded.toOption.get) shouldBe oldwire
      W.inputCodec.encode(newdecoded.toOption.get) shouldBe newwire
    }

    "reject explicit null malformed and invalid new contexts" in {
      Given("a valid new-context Request wire")
      val wire = W.inputCodec.encode(_request())
      val payload = wire.hcursor.downField("payload").focus.get
      val context = payload.hcursor.downField("checkFailure").focus.get
      val invalid = Vector(Json.Null, Json.fromString("not-context"), Json.obj(),
        context.mapObject(_.add("diff", Json.fromString(""))),
        context.mapObject(_.add("changedPaths", Json.arr())),
        context.mapObject(_.add("validation", Json.Null)))
      invalid.foreach { value =>
        Given("the new field explicitly contains null, malformed data or an invalid retained context")
        When("the unchanged saved Request codec decodes that field")
        val result = W.inputCodec.decode(_replace_payload(wire, payload.mapObject(_.add("checkFailure", value))))
        Then("the new field cannot silently become absent or valid")
        result.isLeft shouldBe true
      }
    }

    "project raw diagnostics selected check types and failed candidate without review context" in {
      Given("an assigned failed-check repair with retained changed paths, diff and typed raw receipts")
      val request = _request(Some(_context(_payload(T.Kind.TestOnly))))
      When("the business work view is projected")
      val view = NativeWorkflowOperationOutput.goalWork(request)
      val context = view.checkFailure.get
      Then("the public failed-check context retains actual validation separately from independent review")
      view.review shouldBe None
      view.candidate shouldBe None
      context.changedPaths shouldBe request.checkFailure.get.changedPaths
      context.diff shouldBe request.checkFailure.get.diff
      context.validation.candidate.head shouldBe _candidate.head
      context.validation.candidate.tree shouldBe _candidate.tree
      context.validation.selectedChecks.map(_.kind) shouldBe Vector("compile", "test-only", "test")
      context.validation.checks.map(_.stdout) shouldBe request.checkFailure.get.validation.receipts.map(_.stdout)
      context.validation.checks.last.stderr shouldBe "actual-shaped raw diagnostic"
      context.validation.checks.last.sbt.get.outcome shouldBe "failed"
      context.validation.outcome shouldBe Some("repair-required")
    }

    "require exactly one valid repair source and keep completion semantic only" in {
      val sources = Vector(_request(), _request(None).copy(review = Some(_review)))
      sources.foreach { request =>
        Given("assigned RepairStep with exactly one valid retained review or check failure source")
        When("only semantic completed or decision-needed results are admitted")
        val complete = W.admitSemanticResultC(request, _result)
        val decision = W.admitSemanticResultC(request, _result.copy(outcome = "decision-needed", findings = Vector("Needs direction")))
        Then("both semantic dispositions preserve the existing source obligations")
        complete.isSuccess shouldBe true
        decision.isSuccess shouldBe true
      }
      val invalid = Vector(_request().copy(review = Some(_review)), _request(None),
        _request().copy(checkFailure = Some(_context().copy(diff = ""))),
        _request().copy(stepIdentity = Some("S2")),
        _request().copy(candidate = Some(_candidate)), _request().copy(checksReceipt = Some("old-checks")),
        _request().copy(repairDirective = None),
        _request().copy(kind = "ImplementStep"))
      invalid.foreach { request =>
        Given("both, neither, malformed or mismatched repair sources, stale acceptance facts, or ImplementStep with failure context")
        When("semantic completion is submitted")
        val result = W.admitSemanticResultC(request, _result)
        Then("the assignment cannot bypass its one valid repair source and cleared facts")
        result.isSuccess shouldBe false
      }
      val invalidresults = Vector(_result.copy(outcome = "passed"), _result.copy(outcome = "accept"),
        _result.copy(summary = "x" * 16385), _result.copy(summary = "x\u0000y"),
        _result.copy(findings = Vector("unresolved")),
        _result.copy(outcome = "decision-needed", findings = Vector.fill(129)("finding")),
        _result.copy(outcome = "decision-needed", findings = Vector("x" * 16385)))
      invalidresults.foreach { result =>
        Given("a valid repair assignment with a command/review outcome or unbounded semantic fields")
        When("result admission consumes that reply")
        val admitted = W.admitSemanticResultC(_request(), result)
        Then("check failure context never grants check, review or malformed-result admission")
        admitted.isSuccess shouldBe false
      }
    }

    "retain acceptance report obligations for either repair source" in {
      Given("a selected Plan with one required Step quality condition")
      val condition = A.EffectiveCondition("behavior", "step", true, "Correct selected behavior", "project")
      val plan = _plan.copy(effectiveAcceptance = Some(A.Effective(None, Vector(condition))))
      val satisfied = A.Report(Vector(A.Observation("behavior", "satisfied", "Selected behavior corrected")), Vector.empty)
      Vector(_request(), _request(None).copy(review = Some(_review))).foreach { source =>
        Given("exactly one valid source and the saved required acceptance boundary")
        val request = source.copy(selectedPlan = plan)
        When("completion omits, fails or satisfies its required report")
        val missing = W.admitSemanticResultC(request, _result)
        val failed = W.admitSemanticResultC(request, _result.copy(acceptance = Some(satisfied.copy(
          conditions = Vector(A.Observation("behavior", "unsatisfied", "Still missing"))))))
        val accepted = W.admitSemanticResultC(request, _result.copy(acceptance = Some(satisfied)))
        Then("only satisfied required observations permit semantic completion")
        missing.isSuccess shouldBe false
        failed.isSuccess shouldBe false
        accepted.isSuccess shouldBe true
      }
    }
  }
}
