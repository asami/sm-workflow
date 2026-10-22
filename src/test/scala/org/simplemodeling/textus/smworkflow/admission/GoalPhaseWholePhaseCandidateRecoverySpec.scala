package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.ConfigurationValue
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.preparation.NativeWholePhaseCandidateRecoveryPreparation as P
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseWholePhaseCandidateRecovery as W
import GoalPhaseCandidateRecovery as R
import GoalPhaseNativeCandidate as C
import GoalPhaseCandidateObservationContinuation as K
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Pure codec and independently supplied fixture evidence; no real invocation or Human proof.
  * @since   Oct. 22, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseWholePhaseCandidateRecoverySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private val _candidate = O.Snapshot(_base.head, "c" * 40)
  private val _commit = O.Commit("d" * 40, _candidate.head, _candidate.tree)
  private val _reference = V.WorkflowArtifactReference("whole-independent-human", "1")
  private val _now = Instant.parse("2026-10-22T00:00:00Z")
  private val _execution = V.WorkflowExecutionEvidence(Vector(_reference), Some("participant"), None, None)
  private def _closed: O.State = {
    val plan = O.Plan("whole-plan", "3", "phase-6.4", "fixture-repository",
      Vector(O.Step("step-1", "Complete the owned Compile Step", Vector("value.txt"), Vector.empty, Vector("compile"))),
      Vector("test"), repairLimit = 2)
    val initial = take(O.startC(plan, _base))
    val ready = take(initial.acceptC(O.SemanticCompleted(initial.next.asInstanceOf[O.Semantic].key, "Completed")))
    val observed = take(ready.acceptC(O.CandidateObserved(_candidate)))
    val checked = take(observed.acceptC(O.ChecksObserved(_candidate, Vector("compile"), "compile-receipt", true)))
    val reviewed = take(checked.acceptC(O.ReviewAdmitted(checked.next.asInstanceOf[O.Semantic].key,
      _candidate, "compile-receipt", "independent-step-review", O.ReviewOutcome.Accept)))
    val prepared = take(reviewed.acceptC(O.ClosureIntentSaved(_candidate, "closure")))
    take(prepared.acceptC(O.StepCommitObserved("closure", _commit)))
  }
  private def _target: W.Target = {
    val closed = _closed
    val plan = closed.plan
    val payload = C.Payload("10", "step-1", Vector("value.txt"), Vector("value.txt"),
      Some(_candidate), Vector("value.txt"), Some("actual fixture candidate diff"))
    val inspection = C.Payload("20", "phase:" + plan.phase, Vector.empty, Vector.empty, None, Vector.empty, None)
    W.Target("whole-session", R.controllerSnapshot(closed), "goal-phase", "1", "participant", "20", "21",
      "/private/tmp/whole-fixture-repository", "main", Vector("value.txt"), Vector.empty, None, Vector.empty,
      Vector(payload), "whole-session:ordered-candidate:20:inspection", "1", "21", inspection, Vector.empty,
      "UNKNOWN", "termination-unconfirmed", K.RepositorySnapshot("/private/tmp/whole-fixture-repository", "main",
        _commit.identity, _commit.tree, Vector.empty, _commit.tree),
      W.Policy(GoalPhaseNativeValidation.Policy("phase-test", 1, Vector(GoalPhaseNativeValidation.Check("test", 2))),
        1048576L, Json.obj("sbtPrograms" -> Json.arr(Json.obj("fixture" -> Json.fromString("opaque-host-definition"))))))
  }
  private def _description(t: W.Target): V.GoalPhaseWholePhaseCandidateRecoveryDescriptionResult =
    V.GoalPhaseWholePhaseCandidateRecoveryDescriptionResult(t.sessionId, "decision-required", W.message,
      "component", W.targetRecord(t), W.choices, W.recoveryOperation)
  private def _binding(t: W.Target, choice: String = "AuthorizeNewObservation", ended: Boolean = true,
    rationale: String = "Fresh fixture Human answer"): Json =
    take(W.bindingC("component", t, choice, rationale, "participant",
      ContextReference(_reference.identity, _reference.revision), ended))

  "Whole-Phase recovery E1 original all-closed empty selection" should afterWord(
    "in spec:goal-phase-whole-phase-candidate-recovery, example:E1, rules:R1,R3,R4, phase:6.4") {
    "round trip the distinct full target and strict direction while preserving controller and completed facts" in {
      Given("SM-WHOLE-CAND-01 R1: every ordered Step closed and the original whole inspection selected no management paths")
      val target = _target
      val direction = W.Direction(target, ContextReference(_reference.identity, "1"), "participant", "One fresh remaining observation", true)
      val wire = W.codec.encode(direction)
      When("the whole-specific public target and retained v1 direction are decoded")
      val decoded = take(W.targetC(W.targetRecord(target)))
      val saved = W.codec.decode(wire)
      Then("the exact Plan revision base counters original UNKNOWN and completed candidate/commit remain bound")
      W.validTarget(target) shouldBe true
      decoded shouldBe target
      saved shouldBe Right(direction)
      decoded.controller.plan.revision shouldBe "3"
      decoded.controller.base shouldBe _base
      decoded.controller.tip shouldBe O.Snapshot(_commit.identity, _commit.tree)
      decoded.controller.completed.map(_.commit) shouldBe Vector(_commit)
      decoded.controller.repairs shouldBe 0
      decoded.controller.ordinaryRepairs shouldBe 0
      decoded.inspection.selectedPaths shouldBe empty
      decoded.stagingPrefix shouldBe empty
      decoded.oldObservationOutcome shouldBe "UNKNOWN"
      decoded.oldObservationTermination shouldBe "termination-unconfirmed"
      W.codec.decode(wire.mapObject(_.add("extra", Json.True))).isLeft shouldBe true
      W.decodeTarget(target.asJson.mapObject(_.add("extra", Json.True))).isLeft shouldBe true
      W.decodeTarget(target.asJson.mapObject(_.add("extra", Json.Null))).isLeft shouldBe true
      W.codec.decode(wire.mapObject(_.add("schemaVersion", Json.fromString("selected-step-v1")))).isLeft shouldBe true
      W.codec.decode(W.codec.encode(direction.copy(priorExecutionEnded = false))).isLeft shouldBe true
      R.decodeTarget(target.asJson).isLeft shouldBe true
    }
  }

  "Whole-Phase recovery E2 prefix and snapshot refusal" should afterWord(
    "in spec:goal-phase-whole-phase-candidate-recovery, example:E2, rules:R1,R3, phase:6.4") {
    "refuse selected-Step incomplete gapped crossed and malformed full targets" in {
      Given("SM-WHOLE-CAND-01 R1: the exact pure all-closed target and its pinned repository/index")
      val target = _target
      val changed = Vector(target.copy(controller = target.controller.copy(stage = "InspectCandidate")),
        target.copy(controller = target.controller.copy(cursor = 0)),
        target.copy(controller = target.controller.copy(completed = Vector.empty)),
        target.copy(controller = target.controller.copy(checksReceipt = Some("late-check"))),
        target.copy(frontierRevision = "22"), target.copy(inspectionRecordRevision = "22"),
        target.copy(sourceRevision = "020"), target.copy(inspectionIdentity = "crossed-inspection"),
        target.copy(inspection = target.inspection.copy(step = "step-1")),
        target.copy(inspection = target.inspection.copy(selectedPaths = Vector("foreign.txt"))),
        target.copy(inspection = target.inspection.copy(candidate = Some(_candidate))),
        target.copy(completedCandidates = Vector.empty),
        target.copy(completedCandidates = target.completedCandidates.map(_.copy(step = "crossed-step"))),
        target.copy(completedCandidates = target.completedCandidates.map(_.copy(candidate = Some(_base.copy(head = "e" * 40))))),
        target.copy(repository = target.repository.copy(head = "e" * 40)),
        target.copy(repository = target.repository.copy(indexTree = "invalid-object")),
        target.copy(repository = target.repository.copy(dirty = Vector(K.Dirty("M", " ", "foreign.txt", None)))),
        target.copy(policy = target.policy.copy(maximumDiffBytes = 0)),
        target.copy(policy = target.policy.copy(processPlan = Json.arr())),
        target.copy(policy = target.policy.copy(processPlan = Json.obj())),
        target.copy(oldObservationOutcome = "PASS"))
      When("each changed prefix or snapshot is decoded under the separate whole-Phase contract")
      val refused = changed.map(t => W.decodeTarget(t.asJson))
      Then("no changed target can authorize a remaining observation")
      refused.foreach(_.isLeft shouldBe true)
      W.validTarget(target.copy(controller = null)) shouldBe false
      W.configurationJson(ConfigurationValue.NullValue) shouldBe Right(Json.Null)
      W.configurationJson(ConfigurationValue.StringValue("opaque text")) shouldBe Right(Json.fromString("opaque text"))

      Given("R1: management recording has one fully completed adjacent staging pair after the original inspection")
      val path = "docs/management.md"
      val inspection = target.inspection.copy(selectedPaths = Vector(path))
      val pair = R.StagePair(0, "whole-session:ordered-candidate:20:stage-intent-0", "1", "22",
        "whole-session:ordered-candidate:20:stage-result-0", "1", "23", inspection,
        inspection.copy(stagedPaths = Vector(path)))
      val plan = target.controller.plan.copy(steps = target.controller.plan.steps.map(s => s.copy(ownedPaths = s.ownedPaths :+ path)))
      val managed = target.copy(controller = target.controller.copy(plan = plan), frontierRevision = "23",
        candidatePaths = target.candidatePaths :+ path, managementPaths = Vector(path), recordingPaths = Vector(path),
        inspection = inspection, stagingPrefix = Vector(pair), repository = target.repository.copy(indexTree = "e" * 40,
          dirty = Vector(K.Dirty("M", " ", path, None))))
      When("the completed pair and its gapped incomplete or unsuccessful variants are decoded")
      val valid = W.decodeTarget(managed.asJson)
      val invalid = Vector(managed.copy(stagingPrefix = Vector.empty),
        managed.copy(stagingPrefix = Vector(pair.copy(resultRecordRevision = "24"))),
        managed.copy(stagingPrefix = Vector(pair.copy(result = inspection))),
        managed.copy(stagingPrefix = Vector(pair.copy(intentIdentity = "crossed-intent"))),
        managed.copy(repository = managed.repository.copy(dirty = Vector(K.Dirty("M", "M", path, None)))))
      Then("only the complete successful adjacent prefix permits the remaining observation")
      valid shouldBe Right(managed)
      invalid.foreach(t => W.decodeTarget(t.asJson).isLeft shouldBe true)
      W.decodeTarget(managed.asJson.mapObject(_.add("historyPrefix", Json.arr()))).isLeft shouldBe true
    }
  }

  "Whole-Phase recovery E3 independent evidence and ended provenance" should afterWord(
    "in spec:goal-phase-whole-phase-candidate-recovery, example:E3, rules:R2,R3, phase:6.4") {
    "require exact current target component participant reference policy and affirmative ended attestation" in {
      Given("SM-WHOLE-CAND-01 R2: separately supplied fixture evidence; it is no real Human or terminal process proof")
      val target = _target
      val binding = _binding(target)
      val purpose = NativeExternalEvidence.Purpose.WholePhaseCandidateRecovery
      val declaration = NativeExternalEvidence.Declaration("workflow", DataStore.CollectionId.EntityStore(
        EntityCollectionId("smworkflow", "cli", "host_evidence")), "fixture-independent-issuer")
      val evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity, "lookup",
        _reference.identity, _reference.revision, declaration.issuer, "participant", binding, _now.toString)
      val changedpolicy = target.copy(policy = target.policy.copy(processPlan = Json.obj("sbtPrograms" -> Json.arr("changed-host".asJson))))
      val crossed = Vector(_binding(target.copy(controller = target.controller.copy(generation = target.controller.generation + 1))),
        _binding(changedpolicy), _binding(target.copy(policy = target.policy.copy(maximumDiffBytes = target.policy.maximumDiffBytes - 1))),
        _binding(target.copy(policy = target.policy.copy(phaseValidation = target.policy.phaseValidation.copy(revision = 2)))),
        binding.mapObject(_.add("componentInstance", "crossed-component".asJson)))
      When("the independent reader admits exact evidence and compares every crossed or stale binding")
      val admitted = NativeExternalEvidence.observeC(evidence, declaration, purpose, "lookup", binding, _now)
      val refused = crossed.map(NativeExternalEvidence.observeC(evidence, declaration, purpose, "lookup", _, _now))
      Then("only the exact binding admits and ended=false authorization cannot even form a binding")
      admitted.isSuccess shouldBe true
      refused.foreach(_.isSuccess shouldBe false)
      W.bindingC("component", target, "AuthorizeNewObservation", "Fresh Human", "participant",
        ContextReference(_reference.identity, "1"), false).isSuccess shouldBe false
      W.bindingC("component", target, "AuthorizeNewObservation", "Fresh Human", "another-participant",
        ContextReference(_reference.identity, "1"), true).isSuccess shouldBe false
      NativeExternalEvidence.observeC(evidence.copy(reference = "crossed"), declaration, purpose, "lookup", binding, _now).isSuccess shouldBe false
      NativeExternalEvidence.observeC(evidence, declaration, NativeExternalEvidence.Purpose.CandidateRecovery, "lookup", binding, _now).isSuccess shouldBe false
      W.selectBinding(binding.mapObject(_.add("extra", Json.True))).isLeft shouldBe true
      W.selectBinding(Json.obj()).isLeft shouldBe true
    }
  }

  "Whole-Phase recovery E6 source after normal document progress" should afterWord(
    "in spec:goal-phase-whole-phase-candidate-recovery, example:E6, rules:R1,R3, phase:6.4") {
    "admit all-closed PhaseInspection sources before and after pure native progress appends while refusing other or suspended sources" in {
      Given("CB-SM-WHOLE-CAND-02 R1: pure native history/source admission inputs, without actual document File/provider or prior-process proof")
      val controller = _closed
      val initial = take(InstanceRecord(InstanceIdentity("whole-source-admission"),
        take(GoalPhaseRuntimeCatalog.loadC()).definition, initialRevision, Lifecycle.NotStarted, None,
        CorrelationReference("whole-source-correlation"), CausationReference("whole-source-start"),
        None, None, None, Vector.empty).validateC)
      def _append_(from: InstanceRecord, stage: String, cause: String): InstanceRecord =
        take(from.appendC(from.revision, GoalPhaseNativeRun.history(from, "phase." + stage, cause)))
      val committed = _append_(initial, "PhaseInspection", "ordered-step-closure:committed")
      val intent = _append_(committed, "PhaseInspection", "ordered-document-progress-intent")
      val completed = _append_(intent, "PhaseInspection", "ordered-document-progress-result")
      val before = completed.history
      val other = _append_(completed, "PhaseValidation", "ordered-candidate:candidate")
      val incomplete = take(O.startC(controller.plan, _base))
      val suspendedentry = GoalPhaseNativeRun.history(completed, "phase.PhaseInspection", "whole-source-issued").copy(
        suspension = Some(SuspensionBoundary(ContinuationIdentity("whole-source-work"), completed.revision.next,
          ContextSnapshotReference("whole-source-context"), CompletionReference("whole-source-completion"),
          EvidenceReference("whole-source-evidence"), completed.correlation)))
      val suspended = take(completed.appendC(completed.revision, suspendedentry))
      When("source admission checks the actual pure append records and the same all-closed controller")
      val admitted = Vector(W.originalSourceC(controller, committed), W.originalSourceC(controller, completed))
      val refused = Vector(W.originalSourceC(controller, other), W.originalSourceC(incomplete, completed),
        W.originalSourceC(controller, suspended), W.originalSourceC(null, completed), W.originalSourceC(controller, null))
      val prefix = take(GoalPhaseNativeHistory.prefixC(completed, committed.revision))
      Then("document progress does not invalidate the original whole scope and exact history remains unchanged while invalid sources refuse")
      admitted.foreach(_.isSuccess shouldBe true)
      refused.foreach(_.isSuccess shouldBe false)
      prefix shouldBe committed
      prefix.history shouldBe committed.history
      take(GoalPhaseNativeHistory.prefixC(completed, intent.revision)) shouldBe intent
      completed.history shouldBe before
      completed.history.dropRight(2) shouldBe committed.history
      completed.history.map(_.causation.value) shouldBe Vector("ordered-step-closure:committed",
        "ordered-document-progress-intent", "ordered-document-progress-result")
      R.controllerSnapshot(controller) shouldBe _target.controller
      controller.completed.map(_.commit) shouldBe Vector(_commit)
      controller.cursor shouldBe controller.plan.steps.size
      suspended.suspension shouldBe suspendedentry.suspension
    }
  }

  "Whole-Phase recovery E4 passive preparation and Defer" should afterWord(
    "in spec:goal-phase-whole-phase-candidate-recovery, example:E4, rules:R2,R3, phase:6.4") {
    "serialize only exact independently supplied facts and admit Defer without an ended assertion" in {
      Given("SM-WHOLE-CAND-01 R2: a passive description and fixture-owned explicit Human answer")
      val target = _target
      val description = _description(target)
      val defer = V.GoalPhaseWholePhaseCandidateRecoveryAnswer("Defer", "Wait for real terminal evidence", false)
      val denied = V.GoalPhaseWholePhaseCandidateRecoveryAnswer("AuthorizeNewObservation", "No ended evidence", false)
      When("the passive helper receives Defer and an authorization missing the required affirmative attestation")
      val prepared = P.prepareC(description, defer, _reference, _execution, "participant", "issuer", _now)
      val refused = P.prepareC(description, denied, _reference, _execution, "participant", "issuer", _now)
      Then("Defer yields only an external record value while authorization without ended evidence refuses")
      prepared.isSuccess shouldBe true
      refused.isSuccess shouldBe false
      val row = take(prepared)
      val evidence = take(NativeExternalEvidence.decodeStorageC(row.record.getString(NativeExternalEvidence.storageField).get))
      evidence.binding shouldBe _binding(target, "Defer", false, defer.rationale)
      evidence.purpose shouldBe W.purpose
      P.prepareC(description.copy(sessionId = "crossed"), defer, _reference, _execution, "participant", "issuer", _now).isSuccess shouldBe false
      P.prepareC(description, defer, _reference, _execution.copy(workerIdentity = Some("another-worker")),
        "participant", "issuer", _now).isSuccess shouldBe false
      W.message should include("UNKNOWN")
      W.message should include("write-tree")
      W.message should include("Boolean alone")
    }
  }
}
