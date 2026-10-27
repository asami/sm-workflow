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
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseClosureRecoverySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _parent = O.Snapshot("a" * 40, "b" * 40)
  private val _candidate = O.Snapshot(_parent.head, "c" * 40)
  private val _paths = Vector("src/one.scala")
  private val _plan = O.Plan("closure-plan", "2", "PHASE", "sm-workflow", Vector(
    O.Step("S1", "Close selected work", _paths, Vector.empty, Vector("test"))), Vector("test"), repairLimit = 10)
  private val _controller = {
    val started = (1 to 4).foldLeft(take(O.startC(_plan, _parent))) { (current, index) =>
      val implemented = take(current.acceptC(O.SemanticCompleted(current.next.asInstanceOf[O.Semantic].key, "implemented")))
      val candidate = take(implemented.acceptC(O.CandidateObserved(_candidate)))
      val checked = take(candidate.acceptC(O.ChecksObserved(_candidate, Vector("test"), "prior-test-" + index, true)))
      take(checked.acceptC(O.ReviewAdmitted(checked.next.asInstanceOf[O.Semantic].key, _candidate,
        "prior-test-" + index, "prior-review-" + index, O.ReviewOutcome.Repair,
        Some(O.RepairDirective("Fixture-owned earlier review", Vector("Repair fixture work"))))))
    }
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
    "E67 bind the complete partial proof and retain ordinary absent-field compatibility" in {
      Given("docs/spec/goal-phase-closure-recovery.md R7,R8 E67: the exact original intent and its actual terminal successful execution")
      val (_, input, attempt) = _pending
      val result = R.ExecutionSaved(_append(attempt.intent.get.record, "execution-result"),
        _execution(input).copy(outcome = "succeeded", termination = Some("Exited(0)"), exitCode = Some(0), stderr = ""))
      val changes = Vector("docs/one.md", "docs/two.md", _paths.head).sorted.map(path =>
        GoalPhasePartialCommitClosure.Change(path, "100644", "100644", Some("1" * 40), Some("2" * 40)))
      val partial = O.Commit("d" * 40, _parent.head, "e" * 40)
      val proof = GoalPhasePartialCommitClosure.Proof(_parent, partial, _candidate.tree, Vector.empty,
        changes, changes.filter(_.path == _paths.head), changes.filterNot(_.path == _paths.head), Vector("docs"))
      val observation = _observation.copy(head = partial.identity, tree = partial.tree, selectedPaths = Vector("docs"),
        pendingPaths = changes.map(_.path), dirty = proof.remainder.map(c => G.DirtyPath('M', ' ', c.path, None)),
        commit = G.Commit(partial.identity, partial.tree, Vector(_parent.head)))
      val target = _target(input, attempt.intent.get, Some(result)).copy(ownedPaths = Vector("docs", "src"),
        observation = observation, partialCompletion = Some(proof))
      val reference = ContextReference("exact-partial-human", "1")
      When("the new public target direction and Human binding use the complete proof")
      val decoded = take(R.targetC(R.targetRecord(target)))
      val binding = take(R.bindingC("component", target, "CompletePartialCommit", "Finish only the accepted remainder", "participant", reference, true))
      val direction = R.Direction(target, "CompletePartialCommit", "Finish only the accepted remainder", "participant", reference, true)
      Then("all native facts survive and stale selection participant reference and unknown original outcomes cannot substitute")
      decoded shouldBe target
      R.selectBinding(binding) shouldBe Right(binding)
      R.directionCodec.decode(R.directionCodec.encode(direction)) shouldBe Right(direction)
      R.choicesFor(target) should contain("CompletePartialCommit")
      R.choiceC(target, "AuthorizeNewAttempt").isSuccess shouldBe false
      R.choiceC(target, "ReconcileCommit").isSuccess shouldBe false
      R.bindingC("component", target, direction.choice, direction.rationale, "other", reference, true).isSuccess shouldBe false
      R.bindingC("component", target, direction.choice, direction.rationale, "participant", ContextReference("", "1"), true).isSuccess shouldBe false
      R.bindingC("component", target, direction.choice, direction.rationale, "participant", reference, false).isSuccess shouldBe false
      R.validTarget(target.copy(observation = observation.copy(selectedPaths = Vector("docs/one.md")))) shouldBe false
      R.validTarget(target.copy(execution = Some(result.payload.copy(outcome = "failed", termination = Some("Exited(1)"), exitCode = Some(1))))) shouldBe false
      val ordinary = _target(input, attempt.intent.get)
      ordinary.asJson.hcursor.downField("partialCompletion").focus shouldBe None
      R.decodeTarget(ordinary.asJson) shouldBe Right(ordinary)
      R.choicesFor(ordinary) should not contain "CompletePartialCommit"
    }

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
      val crossed = take(R.bindingC("component", target.copy(reviewEvidence = Some("foreign-review")), "AuthorizeNewAttempt",
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
        "component", R.targetRecord(target), R.choicesFor(target), R.recoveryOperation)
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
        unknown.copy(payload = unknown.payload.copy(target = unknown.payload.target.copy(reviewEvidence = Some("foreign")))),
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

  "Observed selected Step closure continuation" should {
    "terminal eligibility and immutable history" which {
    "retain one observed continuation and a fresh attempt without Human direction or refunded repair counts" in {
      Given("a bound actual failed Exited result, exact unchanged parent/index, and accepted generation five at four of ten repairs")
      val (from, input, attempt) = _pending
      val execution = R.ExecutionSaved(_append(attempt.intent.get.record, "execution-result"), _execution(input))
      val target = _target(input, attempt.intent.get, Some(execution))
      val receipt = R.ContinuationSaved(_append(execution.record, "continuation"), R.Continuation(target, "retry-preparation"))
      val original = attempt.copy(execution = Some(execution), continuation = Some(receipt))
      val payload = attempt.intent.get.payload.copy(attempt = 1)
      val check = K.Saved(_append(receipt.record, "check-result-retry-1"), payload, _controller)
      val intent = K.Saved(_append(check.record, "intent-retry-1"), payload, _controller)
      When("the private codec and native history reconstruct continuation before and after the one fresh intent")
      val decoded = R.continuationCodec.decode(R.continuationCodec.encode(receipt.payload))
      val ready = take(K.reconcileAttemptsC(from, input, 1, Vector(original, K.Attempt(None, None))))
      val pending = take(K.reconcileAttemptsC(from, input, 1, Vector(original, K.Attempt(Some(check), Some(intent)))))
      Then("the automatic receipt is distinct from Human direction and every accepted target and cumulative count survives")
      decoded.toOption shouldBe Some(receipt.payload)
      R.continuationAction(target) shouldBe Some("retry-preparation")
      ready.nextAttempt shouldBe Some(1)
      ready.automatic shouldBe Some(receipt)
      ready.direction shouldBe None
      ready.recovery shouldBe None
      pending.pending shouldBe Some(intent)
      pending.automatic shouldBe Some(receipt)
      pending.execution shouldBe None
      intent.record.history.take(execution.record.history.size) shouldBe execution.record.history
      intent.payload.input shouldBe input
      input.planRevision shouldBe "2"
      input.generation shouldBe 5L
      intent.controller.base shouldBe _parent
      intent.controller.checksReceipt shouldBe Some("accepted-test")
      intent.controller.reviewEvidence shouldBe Some("accepted-review")
      GoalPhaseRepairProgress.snapshot(intent.controller) shouldBe GoalPhaseRepairProgress.Snapshot(4, 4, 10)
    }

    "require a complete consistent finite execution result and current exact ownership before automatic continuation" in {
      Given("one saved terminal failed execution and separately observed original Git state")
      val (_, input, attempt) = _pending
      val execution = R.ExecutionSaved(_append(attempt.intent.get.record, "execution-result"), _execution(input))
      val target = _target(input, attempt.intent.get, Some(execution))
      val summaries = Vector(execution.payload.copy(termination = None),
        execution.payload.copy(termination = Some("Running")), execution.payload.copy(termination = Some("TimedOut")),
        execution.payload.copy(termination = Some("Cancelled")), execution.payload.copy(exitCode = None),
        execution.payload.copy(exitCode = Some(128)), execution.payload.copy(outcome = "incomplete"),
        execution.payload.copy(outcome = "succeeded"), execution.payload.copy(termination = Some("Exited(0)"), exitCode = Some(0)),
        execution.payload.copy(input = input.copy(reviewEvidence = Some("foreign-review"))),
        execution.payload.copy(attempt = 1), execution.payload.copy(operation = "push"))
      val observations = Vector(target.copy(observation = _observation.copy(indexTree = "f" * 40)),
        target.copy(observation = _observation.copy(selectedChangesEmpty = false)),
        target.copy(observation = _observation.copy(mergeHead = Some("f" * 40))),
        target.copy(observation = _observation.copy(pendingPaths = Vector("unowned.scala"))),
        target.copy(observation = _observation.copy(dirty = Vector(G.DirtyPath('M', 'M', _paths.head, None)))),
        target.copy(observation = _observation.copy(selectedPaths = Vector("unowned.scala"))))
      When("eligibility considers the saved result and all typed Git facts together")
      val refused = summaries.map(value => target.copy(execution = Some(value))) ++ observations ++
        Vector(_target(input, attempt.intent.get), target.copy(candidate = _parent), target.copy(attempt = 255))
      Then("unchanged HEAD, nonzero status alone, missing receipts, live or inconsistent owners and candidate drift grant no continuation")
      refused.foreach(value => R.continuationAction(value) shouldBe None)
      R.continuationAction(target) shouldBe Some("retry-preparation")
      val succeeded = target.copy(execution = Some(execution.payload.copy(outcome = "succeeded",
        termination = Some("Exited(0)"), exitCode = Some(0))))
      R.continuationAction(succeeded) shouldBe None
    }

    "reconstruct matching-commit continuation without selecting a second attempt" in {
      Given("an ended actual execution and an observed clean single-parent child of the original reviewed candidate")
      val (from, input, attempt) = _pending
      val execution = R.ExecutionSaved(_append(attempt.intent.get.record, "execution-result"), _execution(input))
      val target = _target(input, attempt.intent.get, Some(execution)).copy(observation = _observation.copy(
        head = "e" * 40, tree = _candidate.tree, dirty = Vector.empty,
        commit = G.Commit("e" * 40, _candidate.tree, Vector(_parent.head))))
      val receipt = R.ContinuationSaved(_append(execution.record, "continuation"), R.Continuation(target, "reconcile-commit"))
      When("the adjacent automatic continuation is reconstructed from saved facts alone")
      val retained = take(K.reconcileAttemptsC(from, input, 1, Vector(attempt.copy(execution = Some(execution),
        continuation = Some(receipt)))))
      Then("the original intent remains the closure target and no fresh commit attempt is selected")
      R.continuationAction(target) shouldBe Some("reconcile-commit")
      retained.nextAttempt shouldBe None
      retained.pending shouldBe attempt.intent
      retained.continuation shouldBe Some(receipt)
      retained.direction shouldBe None
      R.continuationAction(target.copy(execution = None, frontierRevision = attempt.intent.get.record.revision.value.toString)) shouldBe None
      val succeeded = target.copy(execution = Some(execution.payload.copy(outcome = "succeeded",
        termination = Some("Exited(0)"), exitCode = Some(0))))
      R.continuationAction(succeeded) shouldBe Some("reconcile-commit")
    }

    "reject automatic receipts crossing an execution frontier Human direction or canonical native history" in {
      Given("one exact failed result followed by its immutable continuation")
      val (from, input, attempt) = _pending
      val execution = R.ExecutionSaved(_append(attempt.intent.get.record, "execution-result"), _execution(input))
      val target = _target(input, attempt.intent.get, Some(execution))
      val receipt = R.ContinuationSaved(_append(execution.record, "continuation"), R.Continuation(target, "retry-preparation"))
      val malformed = Vector(receipt.copy(record = _append(receipt.record, "continuation")),
        receipt.copy(record = receipt.record.copy(history = receipt.record.history.drop(1))),
        receipt.copy(payload = receipt.payload.copy(action = "AuthorizeNewAttempt")),
        receipt.copy(payload = receipt.payload.copy(target = target.copy(frontierRevision = "99"))),
        receipt.copy(payload = receipt.payload.copy(target = target.copy(definitionRevision = "foreign"))),
        receipt.copy(payload = receipt.payload.copy(target = target.copy(reviewEvidence = Some("foreign")))))
      When("reconstruction binds each receipt to the exact original execution and adjacent history edge")
      val refused = malformed.map(value => K.reconcileAttemptsC(from, input, 1, Vector(attempt.copy(
        execution = Some(execution), continuation = Some(value))))) ++ Vector(
        K.reconcileAttemptsC(from, input, 1, Vector(attempt.copy(continuation = Some(receipt)))),
        K.reconcileAttemptsC(from, input, 1, Vector(attempt.copy(execution = Some(execution), continuation = Some(receipt),
          direction = Some(_direction(target, execution.record))))),
        K.reconcileAttemptsC(from, input, 1, Vector(attempt.copy(execution = Some(execution), continuation = Some(receipt)),
          attempt.copy(execution = Some(execution), continuation = Some(receipt)))))
      Then("stale duplicate gapped and mixed Human/automatic continuations fail closed")
      refused.foreach(_.isSuccess shouldBe false)
      R.continuationCodec.decode(R.directionCodec.encode(_direction(target, execution.record).payload)).isRight shouldBe false
      R.directionCodec.decode(R.continuationCodec.encode(receipt.payload)).isRight shouldBe false
    }
    }
  }
}
