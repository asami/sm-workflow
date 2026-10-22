package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import io.circe.generic.auto.*
import java.time.Instant
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeClosureRecoveryPreparation
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseClosureRecovery as R
import GoalPhaseNativeClosure as K
import GoalPhaseOrderedProgression as O
import RepositorySyncManagedGitObservation as G
import SplitPhaseExplicitPartitionFixture.take

/** Pure native history and independent host evidence contracts. Installed
  * command behavior is exercised separately against a fresh test Session.
  * @since   Oct. 22, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseClosureRecoverySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _parent = O.Snapshot("a" * 40, "b" * 40)
  private val _candidate = O.Snapshot(_parent.head, "c" * 40)
  private val _paths = Vector("src/one.scala")
  private val _plan = O.Plan("closure-plan", "1", "PHASE", "sm-workflow", Vector(
    O.Step("S1", "Close selected work", _paths, Vector.empty, Vector("test"))), Vector("test"))
  private val _controller = {
    val started = take(O.startC(_plan, _parent))
    val implemented = take(started.acceptC(O.SemanticCompleted(started.next.asInstanceOf[O.Semantic].key, "implemented")))
    val candidate = take(implemented.acceptC(O.CandidateObserved(_candidate)))
    val checked = take(candidate.acceptC(O.ChecksObserved(_candidate, Vector("test"), "accepted-test", true)))
    take(checked.acceptC(O.ReviewAdmitted(checked.next.asInstanceOf[O.Semantic].key, _candidate,
      "accepted-test", "accepted-review", O.ReviewOutcome.Accept)))
  }
  private def _from: InstanceRecord = {
    val initial = take(InstanceRecord(InstanceIdentity("closure-recovery-test"), take(GoalPhaseRuntimeCatalog.loadC()).definition,
      initialRevision, Lifecycle.NotStarted, None, CorrelationReference("closure-correlation"),
      CausationReference("closure-start"), None, None, None, Vector.empty).validateC)
    _append(initial, "review-admitted")
  }
  private def _append(from: InstanceRecord, role: String): InstanceRecord =
    take(from.appendC(from.revision, GoalPhaseNativeRun.history(from, "phase.Closing", "ordered-step-closure:" + role)))
  private def _pending: (InstanceRecord, K.Input, K.Attempt) = {
    val from = _from
    val input = take(K.inputC(from, _controller))
    val payload = K.Payload(input, 1, None, check = Some(K.Precheck(Vector.empty, Vector.empty, false)))
    val check = K.Saved(_append(from, "check-result"), payload, _controller)
    val intent = K.Saved(_append(check.record, "intent"), payload, _controller)
    (from, input, K.Attempt(Some(check), Some(intent)))
  }
  private def _observation: R.Observation = R.Observation("/test-owned/repository", "main", _parent.head,
    _parent.tree, _candidate.tree, None, Vector(G.DirtyPath('M', ' ', _paths.head, None)), _paths, true,
    _paths, G.Commit(_parent.head, _parent.tree, Vector("d" * 40)))
  private def _target(input: K.Input, intent: K.Saved, execution: Option[R.ExecutionSaved] = None): R.RecoveryTarget =
    R.RecoveryTarget(input.instance, intent.record.definition.workflowIdentity.value,
      intent.record.definition.workflowRevision.value, "participant", input.plan, input.planRevision, input.step,
      input.generation, input.sourceRevision, execution.map(_.record).getOrElse(intent.record).revision.value.toString,
      R.reference(input, "intent", 0).identity, "1", intent.record.revision.value.toString,
      1, 0, input.parent, input.candidate, input.reviewEvidence, _paths, _observation, execution.map(_.payload))
  private def _execution(input: K.Input): R.Execution = R.Execution(input, 1, 0, "commit-selected",
    "sm-workflow-repository", "failed", Some("Exited(1)"), Some(1), "pathspec 'missing' did not match any file(s) known to git", _paths)
  private def _direction(target: R.RecoveryTarget, frontier: InstanceRecord, choice: String = "AuthorizeNewAttempt"): R.DirectionSaved =
    R.DirectionSaved(_append(frontier, "recovery"), R.Direction(target, choice,
      "Independent test Human accepts a new attempt after terminal old owner evidence", "participant",
      ContextReference("actual-human", "1"), true))

  "Selected Step closure recovery" should {
    "preserve legacy v3 receipt bytes and distinguish absent execution observations from an actual failed result" in {
      Given("a legacy pending intent without a terminal summary and a separate actual-shaped failed summary")
      val (from, input, attempt) = _pending
      val legacy = K.Payload(input, 1, None)
      val encoded = K.codec.encode(legacy)
      val execution = R.ExecutionSaved(_append(attempt.intent.get.record, "execution-result"), _execution(input))
      When("the unchanged v3 codec and the separate execution codec roundtrip their own payloads")
      val old = K.codec.decode(encoded).toOption.get
      val observed = R.executionCodec.decode(R.executionCodec.encode(execution.payload)).toOption.get
      val unknown = take(K.reconcileAttemptsC(from, input, 1, Vector(attempt)))
      val failed = take(K.reconcileAttemptsC(from, input, 1, Vector(attempt.copy(execution = Some(execution)))))
      Then("no terminal observation is invented for the old intent and actual failure retains its exact status and diagnostics")
      old shouldBe legacy
      encoded.hcursor.get[String]("schemaVersion").toOption shouldBe Some("sm-workflow.goal-phase-native-step-closure.v3")
      encoded.hcursor.downField("payload").focus.get.asObject.get.keys.toSet shouldBe Set("input", "ordinal", "commit")
      unknown.execution shouldBe None
      unknown.nextAttempt shouldBe None
      unknown.pending shouldBe attempt.intent
      failed.execution shouldBe Some(execution)
      failed.nextAttempt shouldBe None
      observed.outcome shouldBe "failed"
      observed.exitCode shouldBe Some(1)
      observed.stderr should include("pathspec")
      K.executionMessage(observed) should include("Actual selected commit outcome: failed")
      K.executionMessage(observed) should not include "index check failed"
    }

    "roundtrip exact public target names including Long generation and reject malformed or crossed target facts" in {
      Given("the complete unresolved intent target with an exact physical selection and a large native generation")
      val (_, input, attempt) = _pending
      val target = _target(input, attempt.intent.get).copy(generation = Int.MaxValue.toLong + 1)
      When("the public Record decoder reads normal normalized JSON field names")
      val decoded = take(R.targetC(R.targetRecord(target)))
      val normalized = take(R.targetC(GoalPhaseNativeMetadata.record(target.asJson.mapObject(fields =>
        fields.remove("sessionId").add("session-id", Json.fromString(target.sessionId))))))
      val crossed = Vector(target.copy(intentIdentity = "foreign"), target.copy(sourceRevision = "0"),
        target.copy(frontierRevision = "99"), target.copy(ownedPaths = Vector("foreign")),
        target.copy(observation = _observation.copy(selectedPaths = Vector.empty)),
        target.copy(observation = _observation.copy(selectedPaths = _paths ++ _paths)))
      Then("the exact target survives while malformed and foreign ownership contracts fail closed")
      decoded shouldBe target
      normalized shouldBe target
      target.asJson.hcursor.get[Long]("generation").toOption shouldBe Some(Int.MaxValue.toLong + 1)
      R.decodeTarget(target.asJson.mapObject(_.add("ordinal", Json.fromString("1")))).isRight shouldBe false
      crossed.foreach(value => R.validTarget(value) shouldBe false)
    }

    "require independent purpose participant reference target and prior-ended attestation for effect-bearing choices" in {
      Given("a complete target and separately issued trusted-host Human evidence")
      val (_, input, attempt) = _pending
      val target = _target(input, attempt.intent.get)
      val reference = ContextReference("human-reference", "1")
      val binding = take(R.bindingC("component", target, "AuthorizeNewAttempt", "Explicit direction", "participant", reference, true))
      val evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, R.purpose,
        ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision), reference.identity, reference.revision,
        "trusted-host", "participant", binding, "2026-10-22T00:00:00Z")
      val declaration = NativeExternalEvidence.Declaration("workflow", org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(
        org.simplemodeling.model.datatype.EntityCollectionId("smworkflow", "cli", "host_evidence")), "trusted-host")
      When("the normal host admits the exact binding and rejects false prior-ended or crossed bindings")
      val actual = NativeExternalEvidence.observeC(evidence, declaration, NativeExternalEvidence.Purpose.ClosureRecovery,
        evidence.lookupIdentity, binding, Instant.parse("2026-10-22T01:00:00Z"))
      Then("a Boolean alone never grants execution authority and no unrelated Human purpose can substitute")
      actual.isSuccess shouldBe true
      R.bindingC("component", target, "AuthorizeNewAttempt", "Explicit", "participant", reference, false).isSuccess shouldBe false
      R.bindingC("component", target, "ReconcileCommit", "Explicit", "foreign", reference, true).isSuccess shouldBe false
      Vector(evidence.copy(purpose = NativeExternalEvidence.Purpose.ValidationRecovery.identity),
        evidence.copy(participant = "foreign"), evidence.copy(reference = "foreign"), evidence.copy(issuer = "foreign"))
        .foreach(value => NativeExternalEvidence.observeC(value, declaration, NativeExternalEvidence.Purpose.ClosureRecovery,
          evidence.lookupIdentity, binding, Instant.parse("2026-10-22T01:00:00Z")).isSuccess shouldBe false)
      val crossed = take(R.bindingC("component", target.copy(reviewEvidence = "foreign-review"), "AuthorizeNewAttempt",
        "Explicit", "participant", reference, true))
      NativeExternalEvidence.observeC(evidence, declaration, NativeExternalEvidence.Purpose.ClosureRecovery,
        evidence.lookupIdentity, crossed, Instant.parse("2026-10-22T01:00:00Z")).isSuccess shouldBe false
    }

    "allow Defer without a prior-ended assertion while excluding it from immutable executable direction records" in {
      Given("an unresolved old intent whose execution termination remains unknown")
      val (_, input, attempt) = _pending
      val target = _target(input, attempt.intent.get)
      When("the Human defers without asserting termination")
      val binding = R.bindingC("component", target, "Defer", "Leave the old execution unknown", "participant",
        ContextReference("defer", "1"), false)
      val direction = _direction(target, attempt.intent.get.record, "Defer").payload.copy(priorExecutionEnded = false)
      Then("the passive choice is valid but cannot be decoded as an executable direction")
      binding.isSuccess shouldBe true
      R.choiceC(target, "Defer").isSuccess shouldBe true
      R.directionCodec.decode(R.directionCodec.encode(direction)).isRight shouldBe false
    }

    "prepare only the declared complete closure Human binding and reject absent prior-ended provenance or a different worker" in {
      Given("the actual generated description, supplied Human answer and independent fixture-host execution reference")
      val (_, input, attempt) = _pending
      val target = _target(input, attempt.intent.get)
      val description = V.GoalPhaseClosureRecoveryDescriptionResult(target.sessionId, "decision-required", R.message,
        "component", R.targetRecord(target), R.choices, R.recoveryOperation)
      val answer = V.GoalPhaseClosureRecoveryAnswer("AuthorizeNewAttempt", "Prior owner terminal evidence retained by trusted fixture host", true)
      val reference = V.WorkflowArtifactReference("closure-human-preparation", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val now = Instant.parse("2026-10-22T00:00:00Z")
      When("passive preparation encodes the exact supplied binding")
      val prepared = take(NativeClosureRecoveryPreparation.prepareC(description, answer, reference, execution,
        "participant", "trusted-host", now))
      val encoded = prepared.record.getString(NativeExternalEvidence.storageField).get
      val evidence = take(NativeExternalEvidence.decodeStorageC(encoded))
      Then("the independent evidence purpose and affirmative attestation are explicit and crossed inputs refuse")
      evidence.purpose shouldBe R.purpose
      evidence.participant shouldBe "participant"
      evidence.binding.hcursor.downField("closureRecovery").downField("answer").get[Boolean]("priorExecutionEnded").toOption shouldBe Some(true)
      NativeClosureRecoveryPreparation.prepareC(description, answer.copy(priorExecutionEnded = false), reference,
        execution, "participant", "trusted-host", now).isSuccess shouldBe false
      NativeClosureRecoveryPreparation.prepareC(description, answer, reference,
        execution.copy(workerIdentity = Some("foreign")), "participant", "trusted-host", now).isSuccess shouldBe false
      NativeClosureRecoveryPreparation.prepareC(description.copy(sessionId = "foreign"), answer, reference,
        execution, "participant", "trusted-host", now).isSuccess shouldBe false
      NativeClosureRecoveryPreparation.prepareC(description, answer.copy(choice = "Defer", priorExecutionEnded = false),
        reference, execution, "participant", "trusted-host", now).isSuccess shouldBe true
    }

    "traverse unknown intent then explicit direction then one fresh attempt without changing accepted work or counters" in {
      Given("a historical unknown intent followed by one exact independently authorized new attempt")
      val (from, input, attempt) = _pending
      val direction = _direction(_target(input, attempt.intent.get), attempt.intent.get.record)
      val authorized = attempt.copy(direction = Some(direction))
      val payload = attempt.intent.get.payload.copy(attempt = 1)
      val check = K.Saved(_append(direction.record, "check-result-retry-1"), payload, _controller)
      val intent = K.Saved(_append(check.record, "intent-retry-1"), payload, _controller)
      When("the canonical native history is reconstructed before and after the fresh intent")
      val ready = take(K.reconcileAttemptsC(from, input, 1, Vector(authorized, K.Attempt(None, None))))
      val pending = take(K.reconcileAttemptsC(from, input, 1, Vector(authorized, K.Attempt(Some(check), Some(intent)))))
      Then("only explicit direction selects attempt one and the original unknown intent and accepted controller remain")
      ready.nextAttempt shouldBe Some(1)
      pending.nextAttempt shouldBe None
      pending.pending shouldBe Some(intent)
      pending.execution shouldBe None
      pending.recovery shouldBe Some(direction)
      intent.record.history.take(attempt.intent.get.record.history.size) shouldBe attempt.intent.get.record.history
      intent.payload.input shouldBe input
      intent.controller.checksReceipt shouldBe Some("accepted-test")
      intent.controller.reviewEvidence shouldBe Some("accepted-review")
      intent.controller.generation shouldBe _controller.generation
      intent.controller.repairs shouldBe _controller.repairs
      intent.controller.ordinaryRepairs shouldBe _controller.ordinaryRepairs
    }

    "retain both interruption windows and reject stale duplicated crossed or nonadjacent recovery directions" in {
      Given("a commit intent before terminal observation and the same intent with a saved actual result")
      val (from, input, attempt) = _pending
      val summary = R.ExecutionSaved(_append(attempt.intent.get.record, "execution-result"), _execution(input))
      val unknown = _direction(_target(input, attempt.intent.get), attempt.intent.get.record)
      val observed = _direction(_target(input, attempt.intent.get, Some(summary)), summary.record)
      val malformed = Vector(unknown.copy(record = _append(unknown.record, "recovery")),
        unknown.copy(payload = unknown.payload.copy(target = unknown.payload.target.copy(frontierRevision = "99"))),
        unknown.copy(payload = unknown.payload.copy(target = unknown.payload.target.copy(reviewEvidence = "foreign"))),
        unknown.copy(payload = unknown.payload.copy(participant = "foreign")),
        unknown.copy(payload = unknown.payload.copy(priorExecutionEnded = false)))
      When("history reconstruction checks each exact intent summary and direction edge")
      val before = K.reconcileAttemptsC(from, input, 1, Vector(attempt.copy(direction = Some(unknown))))
      val after = K.reconcileAttemptsC(from, input, 1, Vector(attempt.copy(execution = Some(summary), direction = Some(observed))))
      Then("either interruption remains recoverable only through its own adjacent exact target")
      before.isSuccess shouldBe true
      after.isSuccess shouldBe true
      malformed.foreach(direction => K.reconcileAttemptsC(from, input, 1,
        Vector(attempt.copy(direction = Some(direction)))).isSuccess shouldBe false)
      K.reconcileAttemptsC(from, input, 1, Vector(attempt.copy(execution = Some(summary), direction = Some(unknown)))).isSuccess shouldBe false
      K.reconcileAttemptsC(from, input, 1, Vector(attempt.copy(direction = Some(unknown)),
        attempt.copy(direction = Some(unknown)))).isSuccess shouldBe false
    }

    "admit only the exact reviewed single-parent commit for reconciliation and reject changed Git observations" in {
      Given("the current actual commit is a single-parent child with the exact reviewed candidate tree")
      val (_, input, attempt) = _pending
      val target = _target(input, attempt.intent.get)
      val committed = target.copy(observation = _observation.copy(head = "e" * 40, tree = _candidate.tree,
        dirty = Vector.empty, commit = G.Commit("e" * 40, _candidate.tree, Vector(_parent.head))))
      val drifted = Vector(target.copy(observation = _observation.copy(indexTree = "f" * 40)),
        target.copy(observation = _observation.copy(mergeHead = Some("e" * 40))),
        target.copy(observation = _observation.copy(selectedChangesEmpty = false)),
        target.copy(observation = _observation.copy(pendingPaths = Vector("foreign.scala"))))
      When("effect-bearing choices are checked against those typed actual facts")
      val reconciled = R.choiceC(committed, "ReconcileCommit")
      Then("reconciliation cannot execute another commit and a drifted new-attempt target grants no effect")
      reconciled.isSuccess shouldBe true
      R.choiceC(committed, "AuthorizeNewAttempt").isSuccess shouldBe false
      R.choiceC(target, "ReconcileCommit").isSuccess shouldBe false
      R.choiceC(committed.copy(observation = committed.observation.copy(commit =
        committed.observation.commit.copy(parents = Vector(_parent.head, "f" * 40)))), "ReconcileCommit").isSuccess shouldBe false
      drifted.foreach(value => R.choiceC(value, "AuthorizeNewAttempt").isSuccess shouldBe false)
    }
  }
}
