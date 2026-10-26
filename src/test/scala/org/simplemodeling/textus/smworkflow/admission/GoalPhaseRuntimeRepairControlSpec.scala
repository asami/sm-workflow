package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseOrderedProgression as O
import GoalPhaseRuntimeRepairControl as R

/** Pure runtime policy evidence; no command, provider or stored Session claims.
  * @since   Oct. 25, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseRuntimeRepairControlSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail("RRC fixture admission failed: " + failure)
  }
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private val _plan = O.Plan("rrc-plan", "1", "rrc-phase", "rrc-repository",
    Vector(O.Step("S1", "Repair only this boundary", Vector("value.txt"), Vector.empty, Vector("check"))), Vector("phase-check"))
  private def _key(state: O.State): O.WorkKey = state.next.asInstanceOf[O.Semantic].key
  private def _start(limit: Int = 10, policy: Option[String] = Some(R.version)): O.State =
    _take(O.startC(_plan.copy(repairLimit = limit), _base, policy))
  private def _inspect(state: O.State, exempt: Boolean = false): O.State = {
    val ready = _take(state.acceptC(O.SemanticCompleted(_key(state), "Actual assigned work collected", exempt)))
    _take(ready.acceptC(O.CandidateObserved(_base.copy(tree = "c" * 40))))
  }
  private def _failed(state: O.State, findings: Vector[String], index: Int,
    comparable: Boolean = true, exempt: Boolean = false): O.State = {
    val inspected = _inspect(state, exempt)
    _take(inspected.acceptC(O.RepairableChecksObserved(inspected.candidate.get, Vector("check"),
      "actual-check-" + index, O.RepairDirective("Actual attributable complete fixture findings", findings), comparable)))
  }
  private def _sequence(findings: Vector[Vector[String]]): O.State = findings.zipWithIndex.foldLeft(_start()) {
    case (state, (current, index)) => _failed(state, current, index)
  }

  "Runtime-owned v1 repair policy" should {
    "retain first Phase failure replay and reserve only its separate adopted-policy continuation" in {
      Given("RRC-PHASE-FIRST-FAILURE-01: a completed original Plan and a first whole-Phase native check failure")
      def _phase_(policy: Option[String], limit: Int = 10): O.State = {
        val inspected = _inspect(_start(limit, policy))
        val passed = _take(inspected.acceptC(O.ChecksObserved(inspected.candidate.get, Vector("check"), "step-passed", true)))
        val reviewed = _take(passed.acceptC(O.ReviewAdmitted(_key(passed), passed.candidate.get,
          "step-passed", "step-review", O.ReviewOutcome.Accept)))
        val intent = _take(reviewed.acceptC(O.ClosureIntentSaved(reviewed.candidate.get, "step-closure")))
        val closed = _take(intent.acceptC(O.StepCommitObserved("step-closure", O.Commit("d" * 40, _base.head, intent.candidate.get.tree))))
        val observed = _take(closed.acceptC(O.CandidateObserved(closed.tipSnapshot)))
        _take(observed.acceptC(O.ChecksObserved(observed.candidate.get, observed.plan.phaseChecks, "original-phase-failure", false)))
      }
      val old = _phase_(None)
      val current = _phase_(Some(R.version))
      val exhausted = _phase_(Some(R.version), 0)
      val directive = O.RepairDirective("Correct first actual whole-Phase failure", Vector("Actual ordinary failed Phase check"))
      When("the original event is replayed and only the new continuation reserves an ordinary Release batch")
      val next = _take(current.continueInitialPhaseCheckRepairC("original-phase-failure", directive))
      Then("old failed semantics and all accepted work remain unchanged while one same-Plan batch is charged")
      old.stage shouldBe O.Stage.Decision
      current.stage shouldBe O.Stage.Decision
      old.repairs shouldBe 0
      current.repairs shouldBe 0
      next.stage shouldBe O.Stage.Repair
      next.repairs shouldBe 1
      next.ordinaryRepairs shouldBe 1
      next.plan shouldBe current.plan
      next.base shouldBe current.base
      next.completed shouldBe current.completed
      next.repairObservations shouldBe empty
      old.continueInitialPhaseCheckRepairC("original-phase-failure", directive).isSuccess shouldBe false
      current.continueInitialPhaseCheckRepairC("another-failure", directive).isSuccess shouldBe false
      exhausted.continueInitialPhaseCheckRepairC("original-phase-failure", directive).isSuccess shouldBe false
      next.continueInitialPhaseCheckRepairC("original-phase-failure", directive).isSuccess shouldBe false
    }

    "continue improving decline and one temporary expansion without a divergence label" in {
      Given("RRC-03: complete attributable finding sets improve with one temporary expansion")
      val findings = Vector(Vector("a", "b", "c", "d"), Vector("a", "b", "c"),
        Vector("a", "b", "c", "temporary"), Vector("a", "b"), Vector("a"))
      When("the controller admits each actual failure after its reserved batch completes")
      val state = _sequence(findings)
      Then("ordinary repair continues below ten and retains only four comparable observations")
      state.stage shouldBe O.Stage.Repair
      state.repairs shouldBe 5
      state.repairControl.get.stop shouldBe None
      state.repairControl.get.observations.size shouldBe 4
      _sequence(Vector(Vector("a"))).stage shouldBe O.Stage.Repair
    }

    "stop exact four-observation recurrence oscillation and sustained regression through Decision before ten" in {
      Given("RRC-03: three complete comparable four-observation trends")
      val trends = Vector(
        Vector.fill(4)(Vector("same")) -> "sustained recurrence/stall",
        Vector(Vector("a"), Vector("b"), Vector("a"), Vector("b")) -> "oscillation",
        Vector(Vector("a"), Vector("a", "b"), Vector("a", "b", "c"), Vector("a", "b", "c", "d")) -> "sustained regression")
      When("the fourth observation is admitted at the same Step and Plan revision")
      val stopped = trends.map { (findings, reason) => _sequence(findings) -> reason }
      Then("each real reason includes v1 and actual receipt references while batch four is not reserved")
      stopped.foreach { (state, reason) =>
        state.stage shouldBe O.Stage.Decision
        state.repairs shouldBe 3
        state.decision.get should include (R.version)
        state.decision.get should include (reason)
        state.decision.get should include ("actual-check-3")
        state.pendingRepair should not be empty
        val renewed = _take(state.acceptC(O.RepairContinuationAdmitted("explicit-new-Human-window")))
        renewed.repairs shouldBe 1
        renewed.repairControl.get.observations shouldBe empty
      }
    }

    "keep repeated coarse native checks unknown even as their real diagnostics change" in {
      Given("RRC-03: the same selected check fails while actual errors decrease; it has no typed complete finding catalog")
      When("six failures are admitted as non-comparable native-check observations")
      val state = (1 to 6).foldLeft(_start()) { (current, index) =>
        val inspected = _inspect(current)
        val check = GoalPhaseNativeValidation.Check("check", 1, WorkflowSbtValidation.Kind.Compile)
        val receipt = RepositorySyncNativeMergeValidation.Receipt("check", "exited", Some(1),
          "Actual-shaped decreasing compiler errors " + (100 / index), "Actual-shaped changing diagnostic " + index,
          Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Outcome.Failed, None, None)))
        val payload = GoalPhaseNativeValidation.Payload("12", "S1", inspected.candidate.get,
          Vector(check), Vector(receipt), Some("repair-required"))
        _take(GoalPhaseNativeValidation.observedStateC(inspected, payload, "actual-shaped-native-" + index, Vector("value.txt")))
      }
      Then("the controller uses the budget without manufacturing recurrence from coarse identities")
      state.stage shouldBe O.Stage.Repair
      state.repairs shouldBe 6
      state.repairControl.get.stop shouldBe None
      state.repairControl.get.observations shouldBe empty
    }

    "stop four comparable recurring findings after six charged batches despite interleaved coarse failures" in {
      Given("RRC-03: four complete A finding sets alternate with three non-comparable failures in the same scope")
      val failures = Vector(true, false, true, false, true, false, true)
      When("each actual failure arrives after its previously reserved batch completes")
      val state = failures.zipWithIndex.foldLeft(_start()) { case (current, (comparable, index)) =>
        _failed(current, if (comparable) Vector("a") else Vector("coarse-check"), index + 1, comparable)
      }
      Then("coarse failures charge batches without evicting comparable references and the fourth A stops before ten")
      state.stage shouldBe O.Stage.Decision
      state.repairs shouldBe 6
      state.ordinaryRepairs shouldBe 6
      state.effectiveRepairLimit shouldBe 10
      state.repairControl.get.observations.map(_.findings) shouldBe Vector.fill(4)(Set("a"))
      state.repairControl.get.observations.map(_.reference) shouldBe
        Vector("actual-check-1", "actual-check-3", "actual-check-5", "actual-check-7")
      state.decision.get should include ("sustained recurrence/stall")
      Vector(1, 3, 5, 7).foreach(index => state.decision.get should include ("actual-check-" + index))
    }

    "clear old comparable observations when an unknown failure first arrives under a new Plan revision" in {
      Given("RRC-03: three equal findings precede a genuinely revised requirement/check Plan")
      val third = _sequence(Vector.fill(3)(Vector("same")))
      val revised = _take(third.refreshedC(third.plan.copy(revision = "2"), issued = false)).toOption.get
      When("the revised scope first reports a non-comparable coarse failure")
      val state = _failed(revised, Vector("coarse-check"), 4, comparable = false)
      Then("the failure still charges a batch while the old scope is cleared without inventing an observation")
      state.stage shouldBe O.Stage.Repair
      state.repairs shouldBe 4
      state.repairObservations shouldBe empty
      state.repairControl.get.observations shouldBe empty
      state.repairControl.get.stop shouldBe None
    }

    "allow batch ten to finish and refuse automatic batch eleven even with legacy selected64 under v1" in {
      Given("RRC-04: ten progressively distinct unresolved findings with v1 and an originally readable limit64")
      val tenth = (1 to 10).foldLeft(_start(64))((state, index) => _failed(state, Vector("finding-" + index), index))
      tenth.repairs shouldBe 10
      val inspected = _inspect(tenth)
      When("the tenth batch passes its real selected checks")
      val passed = _take(inspected.acceptC(O.ChecksObserved(inspected.candidate.get, Vector("check"), "batch-ten-passed", true)))
      Then("it reaches Review and an unresolved alternative would reach Decision without another reservation")
      passed.stage shouldBe O.Stage.Review
      passed.repairs shouldBe 10
      val stopped = _take(inspected.acceptC(O.RepairableChecksObserved(inspected.candidate.get, Vector("check"),
        "batch-ten-still-failed", O.RepairDirective("Actual remaining finding", Vector("eleven")))))
      stopped.stage shouldBe O.Stage.Decision
      stopped.repairs shouldBe 10
      stopped.decision.get should include (R.version)
      stopped.effectiveRepairLimit shouldBe 10
    }

    "count hygiene and minor batches automatically while retaining the ordinary exemption and collection count" in {
      Given("RRC-04: one reserved minor repair with the existing ordinary exemption")
      val first = _failed(_start(), Vector("minor"), 1)
      When("that assigned batch completes and its fresh candidate is inspected")
      val collected = _inspect(first, exempt = true)
      Then("collection and validation reserve nothing and only the ordinary count is exempt")
      collected.repairs shouldBe 1
      collected.ordinaryRepairs shouldBe 0
      collected.repairControl shouldBe first.repairControl
      val next = _take(collected.acceptC(O.RepairableChecksObserved(collected.candidate.get, Vector("check"),
        "hygiene-check", O.RepairDirective("Actual Hygiene", Vector("hygiene")))))
      next.repairs shouldBe 2
      next.ordinaryRepairs shouldBe 1
    }

    "preserve legacy source behavior until explicit policy adoption and retain consumed1 on limit3 to10" in {
      Given("RRC-05: an immutable legacy Plan3 with one batch already reserved")
      val original = _failed(_start(3, None), Vector("legacy-finding"), 1)
      When("an independently admitted direction adopts10 without replacing the Session or Plan")
      val adopted = _take(original.adoptRepairPolicyC("AdoptRuntimePolicy", "actual-direction"))
      Then("original Plan/base/prefix, generation and count remain while the effective allowance becomes10")
      adopted.plan shouldBe original.plan
      adopted.base shouldBe original.base
      adopted.completed shouldBe original.completed
      adopted.generation shouldBe original.generation
      adopted.repairs shouldBe 1
      adopted.ordinaryRepairs shouldBe 1
      adopted.selectedRepairLimit shouldBe 10
      adopted.effectiveRepairLimit shouldBe 10
      val progress = GoalPhaseRepairProgress.snapshot(adopted)
      progress.limit shouldBe 3
      progress.selectedLimit shouldBe Some(10)
      GoalPhaseRepairProgress.validateC(progress.copy(automaticCount = 4, ordinaryCount = 4)).isSuccess shouldBe true
      val smaller = _take(original.adoptRepairPolicyC("RetainSelectedLimit", "explicit-smaller-limit"))
      smaller.effectiveRepairLimit shouldBe 3
      val retained = (2 to 4).foldLeft(original)((state, index) => _failed(state, Vector("legacy-finding"), index))
      retained.stage shouldBe O.Stage.Decision
      retained.repairs shouldBe 3
      retained.decision.get should not include R.version
    }

    "reserve an unreserved exhausted repair once after adoption and resume an interrupted reserved batch without recounting" in {
      Given("RRC-05: a legacy limit1 exhausted after one completed batch")
      val first = _failed(_start(1, None), Vector("first"), 1)
      val exhausted = _failed(first, Vector("second"), 2)
      val adopted = _take(exhausted.adoptRepairPolicyC("AdoptRuntimePolicy", "actual-budget-correction"))
      adopted.repairs shouldBe 1
      adopted.stage shouldBe O.Stage.Decision
      When("ordinary dispatch reserves the next batch, then its real repair result needs an unchanged-scope decision")
      val dispatched = _take(adopted.dispatchPolicyRepairC)
      val interrupted = _take(dispatched.acceptC(O.NativeFailure("Actual completed repair needs same-scope direction")))
      val linked = Some(R.RestorationLink("producer", _plan.repository, "consumer", "consumer-repository", "restore runtime"))
      val resumed = _take(interrupted.adoptRepairPolicyC("AdoptRuntimePolicy", "independent-resume", linked))
      Then("the next issue is a new generation of the same charged batch, with restoration context retained")
      dispatched.repairs shouldBe 2
      dispatched.policyRepairPending shouldBe false
      resumed.stage shouldBe O.Stage.Repair
      resumed.repairs shouldBe 2
      resumed.generation shouldBe interrupted.generation + 1
      resumed.repairControl.get.restoration shouldBe linked
      dispatched.dispatchPolicyRepairC.isSuccess shouldBe false
    }

    "refuse resurrection of incomplete validation unknown closure effects and a closed Phase" in {
      Given("RRC-02/05: incomplete checks and a genuinely retained unknown closure intent")
      val inspected = _inspect(_start())
      val incomplete = _take(inspected.acceptC(O.IncompleteChecksObserved(inspected.candidate.get, Vector("check"), "actual-incomplete")))
      val checked = _take(inspected.acceptC(O.ChecksObserved(inspected.candidate.get, Vector("check"), "actual-passed", true)))
      val accepted = _take(checked.acceptC(O.ReviewAdmitted(_key(checked), checked.candidate.get,
        "actual-passed", "actual-review", O.ReviewOutcome.Accept)))
      val intent = _take(accepted.acceptC(O.ClosureIntentSaved(accepted.candidate.get, "actual-closure-intent")))
      val unknown = _take(intent.acceptC(O.ClosureUnknown("actual-closure-intent")))
      When("policy adoption is attempted against these protected lifecycle boundaries")
      val incompleteadoption = incomplete.adoptRepairPolicyC("AdoptRuntimePolicy", "direction")
      val unknownadoption = unknown.adoptRepairPolicyC("AdoptRuntimePolicy", "direction")
      Then("incomplete validation and the retained unknown closure effect both refuse adoption")
      incompleteadoption.isSuccess shouldBe false
      unknownadoption.isSuccess shouldBe false
      Given("the original known closure completes and the resulting Phase receives actual checks and acceptance")
      val committed = _take(intent.acceptC(O.StepCommitObserved("actual-closure-intent",
        O.Commit("d" * 40, _base.head, intent.candidate.get.tree))))
      val phase = _take(committed.acceptC(O.CandidateObserved(committed.tipSnapshot)))
      val phasechecked = _take(phase.acceptC(O.ChecksObserved(phase.candidate.get, Vector("phase-check"), "phase-passed", true)))
      val phaseaccepted = _take(phasechecked.acceptC(O.ReviewAdmitted(_key(phasechecked), phasechecked.candidate.get,
        "phase-passed", "phase-review", O.ReviewOutcome.Accept)))
      val closed = _take(phaseaccepted.acceptC(O.PhaseLocallyCompleted(phaseaccepted.candidate.get,
        phaseaccepted.completed.map(_.commit), "phase-review")))
      When("policy adoption is attempted after the Phase has closed")
      val closedadoption = closed.adoptRepairPolicyC("AdoptRuntimePolicy", "direction")
      Then("unknown effects and closed acceptance remain intact without replay or another repair window")
      unknown.closureIntent shouldBe Some("actual-closure-intent")
      closed.stage shouldBe O.Stage.Closed
      closedadoption.isSuccess shouldBe false
    }

    "compare only unchanged Plan revision and scope and preserve legacy Start and progress codec shapes" in {
      Given("RRC-03/05: three equal scoped observations and unchanged historical three-field progress")
      val third = _sequence(Vector.fill(3)(Vector("same")))
      val revised = _take(third.refreshedC(third.plan.copy(revision = "2"), issued = false)).toOption.get
      When("the same finding appears under a genuinely revised requirement/check Plan")
      val next = _failed(revised, Vector("same"), 4)
      Then("the old scope is not compared and old omitted fields remain readable exactly")
      next.stage shouldBe O.Stage.Repair
      next.repairControl.get.observations.size shouldBe 1
      val legacy = GoalPhaseRepairProgress.Snapshot(1, 1, 3)
      legacy.asJson shouldBe Json.obj("automaticCount" -> Json.fromInt(1), "ordinaryCount" -> Json.fromInt(1), "limit" -> Json.fromInt(3))
      legacy.asJson.as[GoalPhaseRepairProgress.Snapshot] shouldBe Right(legacy)
      val payload = GoalPhaseNativeRun.Payload("participant", "/fixture/repository", "main", _plan, _base)
      val wire = GoalPhaseNativeRun.codec.encode(payload)
      wire.hcursor.downField("payload").downField("repairPolicy").focus shouldBe None
      GoalPhaseNativeRun.codec.decode(wire) shouldBe Right(payload)
      val explicitNull = wire.mapObject(_.add("payload", wire.hcursor.downField("payload").focus.get.mapObject(_.add("repairPolicy", Json.Null))))
      GoalPhaseNativeRun.codec.decode(explicitNull).isLeft shouldBe true
    }
  }
}
