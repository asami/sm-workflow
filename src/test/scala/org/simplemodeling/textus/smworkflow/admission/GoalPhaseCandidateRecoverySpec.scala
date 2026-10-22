package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.cncf.context.RuntimeContext
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.datastore.DataStore
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.preparation.NativeCandidateRecoveryPreparation as P
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseCandidateRecovery as R
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeCandidate as C
import SplitPhaseExplicitPartitionFixture.take

/** Pure contract and passive preparation evidence; these inputs grant no real Human authority.
  * @since   Oct. 22, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseCandidateRecoverySpec extends AnyWordSpec with NativeGoalSourceHistoryCliFixture {
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private def _target: R.RecoveryTarget = {
    val paths = Vector(sourcePath, "docs/guide.md")
    val plan = O.Plan("candidate-plan", "2", "phase-116", "fixture-repository",
      Vector(O.Step("step-1", "Complete the owned candidate", paths, Vector.empty, Vector("test"))), Vector("test"))
    val initial = take(O.startC(plan, _base))
    val state = take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key, "Fixture implementation")))
    val inspection = C.Payload("50", "step-1", paths, Vector.empty, None, Vector.empty, None)
    val history = GoalPhaseNativeSourceHistory.Payload("50", "step-1", sourcePath, "goldenport",
      "native-cli-external-goldenport-v1", "2026-10-22", true, None)
    def _id_(key: String) = "fixture-session:ordered-candidate:50:" + key
    R.RecoveryTarget("fixture-session", R.controllerSnapshot(state), "goal-phase", "1", "participant", "50", "57",
      "/private/tmp/fixture-candidate-repository", "main", paths, Vector.empty,
      _id_("inspection"), "1", "51", inspection,
      WorkflowSourceHistoryRule.Selection("goldenport", Some(take(WorkflowSourceHistoryRule.decodeC(enabledSettings(ruleKey))))),
      Vector(R.HistoryPair(0, _id_("source-history-intent-0"), "1", "52", _id_("source-history-result-0"), "1", "53",
        history, history.copy(outcome = Some("updated")))),
      paths.indices.map(i => R.StagePair(i, _id_("stage-intent-" + i), "1", (54 + i * 2).toString,
        _id_("stage-result-" + i), "1", (55 + i * 2).toString, inspection.copy(stagedPaths = paths.take(i)),
        inspection.copy(stagedPaths = paths.take(i + 1)))).toVector, "UNKNOWN", "termination-unconfirmed")
  }
  private val _reference = V.WorkflowArtifactReference("fresh-independent-human", "1")
  private val _answer = V.GoalPhaseCandidateRecoveryAnswer("AuthorizeNewObservation", "Accept one new observation while old termination remains unconfirmed")
  private val _execution = V.WorkflowExecutionEvidence(Vector(_reference), Some("participant"), None, None)
  private val _now = Instant.parse("2026-10-22T00:00:00Z")
  private def _description(target: R.RecoveryTarget): V.GoalPhaseCandidateRecoveryDescriptionResult =
    V.GoalPhaseCandidateRecoveryDescriptionResult(target.sessionId, "decision-required", R.message, "component",
      R.targetRecord(target), R.choices, R.recoveryOperation)

  "GoalPhase candidate recovery E9 retained Phase evidence" should afterWord("in spec:goal-phase-candidate-recovery, example:E9, rules:R1,R4, phase:CB-CC04-CAND-02") {
    "validate exact closed Step structure without consulting current Git" in {
      Given("CB-CC04-CAND-02: pure accepted Step facts with an explicit descendant candidate and scoped Phase evidence")
      val plan = _target.controller.plan
      val initial = take(O.startC(plan, _base))
      val ready = take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key, "Ready")))
      val parent = O.Snapshot("c" * 40, "d" * 40)
      val proof = O.CandidateContinuation(parent, Vector(O.HistoryCommit(parent.head, parent.tree, Vector(_base.head)),
        O.HistoryCommit(_base.head, _base.tree, Vector.empty)))
      val candidate = O.Snapshot(parent.head, "e" * 40)
      val seen = take(ready.acceptC(O.CandidateContinuationObserved(candidate, proof)))
      val checked = take(seen.acceptC(O.ChecksObserved(candidate, plan.steps.head.checks, "checks", true)))
      val reviewed = take(checked.acceptC(O.ReviewAdmitted(checked.next.asInstanceOf[O.Semantic].key,
        candidate, "checks", "independent-review", O.ReviewOutcome.Accept)))
      val pending = take(reviewed.acceptC(O.ClosureIntentSaved(candidate, "closure")))
      val commit = O.Commit("f" * 40, parent.head, candidate.tree)
      val closed = take(pending.acceptC(O.StepCommitObserved("closure", commit)))
      val selected = C.Payload("10", "step-1", Vector(sourcePath), Vector(sourcePath), Some(candidate),
        Vector(sourcePath), Some("selected source diff"), Some(proof))
      val evidence = C.StepEvidence("step-1", candidate, selected.changedPaths, selected.diff.get, commit, Vector.empty)
      val phase = C.Payload("20", "phase:" + plan.phase, Vector.empty, Vector.empty, Some(closed.tipSnapshot),
        selected.changedPaths, Some("Phase aggregate\n" + evidence.diff), stepEvidence = Some(Vector(evidence)))
      def _with_(value: C.StepEvidence) = phase.copy(stepEvidence = Some(Vector(value)))
      val invalid = Vector(_with_(evidence.copy(step = "crossed-step")),
        _with_(evidence.copy(commit = commit.copy(identity = "1" * 40))),
        _with_(evidence.copy(candidate = candidate.copy(tree = "2" * 40))),
        _with_(evidence.copy(changedPaths = Vector("foreign.txt"))),
        _with_(evidence.copy(diff = "invented diff")),
        _with_(evidence.copy(eofCorrections = Vector("foreign.txt"))),
        _with_(evidence.copy(eofCorrections = Vector(sourcePath))),
        phase.copy(changedPaths = Vector("foreign.txt")),
        phase.copy(diff = Some("missing selected diff")),
        phase.copy(changedPaths = selected.changedPaths :+ "docs/management.md"))
      When("the same pure structural admission used by both retained and live loading checks the payloads")
      val valid = C.retainedEvidenceC(phase, closed, Vector(selected), Vector("docs/management.md"))
      val refused = invalid.map(C.retainedEvidenceC(_, closed, Vector(selected), Vector("docs/management.md")))
      Then("exact historical facts admit while crossed commits candidates paths diffs and correction shapes refuse")
      valid.isSuccess shouldBe true
      refused.foreach(_.isSuccess shouldBe false)
      C.retainedEvidenceC(phase, closed, Vector(selected.copy(diff = Some("crossed saved diff"))), Vector.empty).isSuccess shouldBe false
      C.retainedEvidenceC(phase, closed, Vector.empty, Vector.empty).isSuccess shouldBe false
      C.retainedEvidenceC(phase, ready, Vector(selected), Vector.empty).isSuccess shouldBe false
      val managed = phase.copy(stagedPaths = Vector("docs/management.md"),
        changedPaths = selected.changedPaths :+ "docs/management.md")
      C.retainedEvidenceC(managed, closed, Vector(selected), Vector("docs/management.md")).isSuccess shouldBe true
    }
  }

  "GoalPhase candidate recovery E1 target codec" should afterWord("in spec:goal-phase-candidate-recovery, example:E1, rules:R1,R2, phase:CC04-CAND-01") {
    "retain the entire Plan controller native prefix and old uncertainty through public naming" in {
      Given("CC04-CAND-01: a full original inspection, successful history pair and two successful staging pairs")
      val target = _target
      val record = RuntimeContext.PropertyNameContext.default.transformRecord(R.targetRecord(target))
      val direction = R.Direction(target, ContextReference(_reference.identity, _reference.revision), "participant", _answer.rationale)
      When("the public target and dedicated durable direction roundtrip")
      val restored = R.targetC(record)
      val replayed = R.codec.decode(R.codec.encode(direction))
      Then("every bound fact survives and old observation stays UNKNOWN with termination unconfirmed")
      take(restored) shouldBe target
      replayed shouldBe Right(direction)
      target.oldObservationOutcome shouldBe "UNKNOWN"
      target.oldObservationTermination shouldBe "termination-unconfirmed"
      R.choices shouldBe Vector("AuthorizeNewObservation", "Defer")
    }
  }
  "GoalPhase candidate recovery E2 prefix admission" should afterWord("in spec:goal-phase-candidate-recovery, example:E2, rules:R1, phase:CC04-CAND-01") {
    "refuse incomplete gapped crossed unknown or already observed target shapes" in {
      Given("CC04-CAND-01: an otherwise complete target and exact receipt payloads")
      val target = _target
      val stage = target.stagingPrefix.head
      val history = target.historyPrefix.head
      val invalid = Vector(target.copy(sourceRevision = "49"), target.copy(frontierRevision = "58"),
        target.copy(inspectionRecordRevision = "52"), target.copy(inspectionRevision = "2"),
        target.copy(stagingPrefix = target.stagingPrefix.dropRight(1)), target.copy(stagingPrefix = target.stagingPrefix.reverse),
        target.copy(stagingPrefix = target.stagingPrefix.updated(0, stage.copy(resultRecordRevision = "56"))),
        target.copy(stagingPrefix = target.stagingPrefix.updated(0, stage.copy(resultIdentity = "crossed-receipt"))),
        target.copy(stagingPrefix = target.stagingPrefix.updated(0, stage.copy(result = stage.intent))),
        target.copy(stagingPrefix = target.stagingPrefix.updated(0, stage.copy(result = stage.result.copy(diff = Some("invented"))))),
        target.copy(historyPrefix = Vector(history.copy(result = history.result.copy(outcome = None)))),
        target.copy(historyPrefix = Vector(history.copy(result = history.result.copy(outcome = Some("missing"))))),
        target.copy(historyPrefix = Vector(history.copy(result = history.result.copy(outcome = Some("excluded"))))),
        target.copy(historyPrefix = Vector(history.copy(intentIdentity = "crossed-history"))),
        target.copy(inspection = target.inspection.copy(candidate = Some(_base))),
        target.copy(inspection = target.inspection.copy(selectedPaths = Vector.empty)),
        target.copy(candidatePaths = Vector.empty), target.copy(oldObservationOutcome = "NotExecuted"),
        target.copy(oldObservationTermination = "exited"), target.copy(sourceRevision = Long.MaxValue.toString),
        target.copy(controller = target.controller.copy(stage = "PhaseInspection", cursor = 1)),
        target.copy(controller = target.controller.copy(stage = "Validation")))
      When("each target is decoded for eligibility")
      val refused = invalid.map(t => R.decodeTarget(t.asJson))
      Then("none can authorize observation or turn unknown state into confirmed execution facts")
      R.decodeTarget(target.asJson) shouldBe Right(target)
      refused.foreach(_.isLeft shouldBe true)
      R.decodeTarget(target.asJson.mapObject(_.remove("controller"))).isLeft shouldBe true
      R.decodeTarget(target.asJson.mapObject(_.remove("historySelection"))).isLeft shouldBe true
    }
  }
  "GoalPhase candidate recovery E3 independent provenance" should afterWord("in spec:goal-phase-candidate-recovery, example:E3, rules:R2,R3, phase:CC04-CAND-01") {
    "bind full controller prefix base participant and reference independently" in {
      Given("CC04-CAND-01: separately issued evidence for a complete immutable target")
      val target = _target
      val reference = ContextReference(_reference.identity, _reference.revision)
      val binding = take(R.bindingC("component", target, _answer.choice, _answer.rationale, "participant", reference))
      val declaration = NativeExternalEvidence.Declaration("workflow", DataStore.CollectionId.EntityStore(
        EntityCollectionId("smworkflow", "cli", "host_evidence")), "test-host")
      val evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, R.purpose, "lookup", reference.identity,
        reference.revision, declaration.issuer, "participant", binding, _now.toString)
      val targets = Vector(target.copy(controller = target.controller.copy(plan = target.controller.plan.copy(revision = "3"))),
        target.copy(controller = target.controller.copy(generation = target.controller.generation + 1)),
        target.copy(controller = target.controller.copy(repairs = 1, ordinaryRepairs = 1)),
        target.copy(controller = target.controller.copy(base = O.Snapshot("c" * 40, "d" * 40), tip = O.Snapshot("c" * 40, "d" * 40))),
        target.copy(participant = "another-participant"), target.copy(definitionRevision = "2"), target.copy(branch = "another-branch"),
        target.copy(historyPrefix = target.historyPrefix.map(p => p.copy(result = p.result.copy(outcome = Some("unchanged"))))))
      When("the provider compares the actual binding with crossed complete targets and a crossed answer reference")
      val accepted = NativeExternalEvidence.observeC(evidence, declaration, NativeExternalEvidence.Purpose.CandidateRecovery, "lookup", binding, _now)
      val crossed = targets.map { t =>
        val requested = take(R.bindingC("component", t, _answer.choice, _answer.rationale, t.participant, reference))
        NativeExternalEvidence.observeC(evidence, declaration, NativeExternalEvidence.Purpose.CandidateRecovery, "lookup", requested, _now)
      }
      val crossedreference = take(R.bindingC("component", target, _answer.choice, _answer.rationale, "participant", ContextReference("different-answer", "1")))
      Then("only the exact independently supplied target and actual participant/reference admit")
      accepted.isSuccess shouldBe true
      R.selectBinding(binding) shouldBe Right(binding)
      crossed.foreach(_.isSuccess shouldBe false)
      NativeExternalEvidence.observeC(evidence, declaration, NativeExternalEvidence.Purpose.CandidateRecovery, "lookup", crossedreference, _now)
        .isSuccess shouldBe false
      R.bindingC("component", target, "AuthorizeNewAttempt", _answer.rationale, "participant", reference).isSuccess shouldBe false
      R.bindingC("component", target, _answer.choice, "", "participant", reference).isSuccess shouldBe false
    }
  }
  "GoalPhase candidate recovery E4 passive preparation" should afterWord("in spec:goal-phase-candidate-recovery, example:E4, rules:R2,R3, phase:CC04-CAND-01") {
    "prepare its own Human purpose for authorization and Defer while rejecting crossed supplied facts" in {
      Given("CC04-CAND-01: actual target description, explicit answer, matching worker and real reference")
      val description = _description(_target)
      When("normal passive preparation encodes authorization and separately authenticated Defer")
      val prepared = take(P.prepareC(description, _answer, _reference, _execution, "participant", "test-host", _now))
      val deferred = take(P.prepareC(description, _answer.copy(choice = "Defer"), _reference, _execution, "participant", "test-host", _now))
      Then("the dedicated evidence purpose binds the exact answer without a workflow direction or effect")
      val actual = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      actual.purpose shouldBe R.purpose
      actual.binding.hcursor.downField("candidateRecovery").downField("answer").get[String]("choice").toOption shouldBe Some("AuthorizeNewObservation")
      val deferredevidence = take(NativeExternalEvidence.decodeStorageC(deferred.record.getString(NativeExternalEvidence.storageField).get))
      deferredevidence.binding.hcursor.downField("candidateRecovery").downField("answer").get[String]("choice").toOption shouldBe Some("Defer")
      prepared.recordIdentity should startWith(R.purpose + ":")
      Given("a crossed session, participant, worker, operation or missing answer reference")
      When("each contradictory description or supplied Human fact is prepared")
      val refused = Vector(P.prepareC(description.copy(sessionId = "foreign-session"), _answer, _reference, _execution, "participant", "test-host", _now),
        P.prepareC(description, _answer, _reference, _execution, "foreign-participant", "test-host", _now),
        P.prepareC(description, _answer, _reference, _execution.copy(workerIdentity = None), "participant", "test-host", _now),
        P.prepareC(description.copy(recoveryOperation = "recoverGoalPhaseSourceHistory"), _answer, _reference, _execution, "participant", "test-host", _now),
        P.prepareC(description, _answer, null, _execution, "participant", "test-host", _now))
      Then("passive conversion refuses every mismatch")
      refused.foreach(_.isSuccess shouldBe false)
    }
  }
}
