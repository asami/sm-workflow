package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeFailedPhaseRepairPreparation
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseInterruptedValidationRepair as I
import GoalPhaseFailedPhaseRepairAdoption as A
import GoalPhaseNativeValidation as N
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Specification-owned typed inputs, never production termination attestations.
  * The installed CLI example owns actual timeout and retained traversal proof.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseInterruptedValidationRepairSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private val _candidate = O.Snapshot("c" * 40, "d" * 40)
  private val _check = N.Check("test", 2)
  private val _plan = O.Plan("timeout-plan", "1", "phase-fixture", "fixture-repository",
    Vector(O.Step("S1", "Accepted Step", Vector("value.txt"), Vector.empty, Vector("compile"))), Vector("test"), 2)
  private val _proposed = _plan.copy(revision = "2", steps = _plan.steps :+
    O.Step("S2", "Correct the test source", Vector("src/test/ProductCheck.scala"), Vector("S1"), Vector("compile")))
  private val _proposal = GoalPhasePlanRevision.Proposal("timeout-correction", "1", "Correct the timed-out validation source", _proposed)
  private val _source = I.Source(I.kind, "fixture-session", "GoalPhaseWorkflow", "1", "participant",
    _plan.identity, _plan.revision, _base, _candidate, "10", "13", "phase:" + _plan.phase, "test", 2, 0,
    I.Receipt("fixture-session:ordered-validation:10:check-intent-0", "11"),
    I.Receipt("fixture-session:ordered-validation:10:check-result-0", "12"),
    I.Receipt("fixture-session:ordered-validation:10:validation", "13"), "timed-out", "incomplete")
  private val _target = I.Target(_source, _plan,
    Vector(O.ClosedStep("S1", O.Commit(_candidate.head, _base.head, _candidate.tree), "independent-review")),
    GoalPhaseRepairProgress.Snapshot(1, 1, 2), 4, Vector("value.txt", "src/test/ProductCheck.scala"),
    GoalPhaseNativeClosure.Policy("fixture-closure", 1, Vector(
      GoalPhaseNativeClosure.StepProgram("S1", 1), GoalPhaseNativeClosure.StepProgram("S2", 2))),
    GoalPhasePlanRevision.Captured(_proposal, _proposed), Vector(N.Check("compile", 1, WorkflowSbtValidation.Kind.Compile)))
  private val _human = ContextReference("fresh-timeout-human", "1")
  private val _direction = A.InterruptedDirection(_target, _human, "participant", "Adopt exactly one corrective Step")
  private def _observation(termination: ProcessExecutionTermination): N.Payload = {
    val capture = ProcessExecutionCapture(Vector.empty, 0L, false)
    val actual = SbtExecutionResult(SbtTarget(take(WorkspaceId.parseC("sm-workflow-repository-validation-2"))),
      SbtOperation.Test, ProcessExecutionResult(termination, capture, capture, Vector.empty, 1000L, "specification-owned-input"))
    take(N.attemptPayloadC(N.Payload("10", "phase:" + _plan.phase, _candidate, Vector(_check), Vector.empty, None),
      _check, 0, SbtExecutionAttempt.Observed(actual)))
  }

  "E4 confirmed timeout repair proof and wire" should afterWord(
    "in spec:goal-phase-failed-phase-repair-adoption, example:E4, rules:R1,R2,R4,R5,R6,R8, phase:6.5") {
    "distinguish the typed timeout from failure cancelled unknown and unconfirmed termination without granting R8 retry" in {
      Given("R1,R8 E4: typed producer observations, including a genuine exited 143 observation")
      val timeout = _observation(ProcessExecutionTermination.TimedOut)
      val exited143 = _observation(ProcessExecutionTermination.Exited(143))
      val refused = Vector("cancelled", "termination-unconfirmed", "indeterminate", "not-started").map { termination =>
        timeout.copy(receipts = timeout.receipts.map(_.copy(termination = termination)))
      } ++ Vector(timeout.copy(receipts = Vector.empty), timeout.copy(receipts = timeout.receipts.map(_.copy(exitCode = Some(143)))))
      When("the closed source classification reads retained typed facts")
      val classified = N.terminalOutcome(timeout)
      Then("only confirmed TimedOut is the timeout variant and repairable remains false")
      classified shouldBe Some(N.TerminalOutcome.TerminatedTimeout)
      N.repairable(timeout) shouldBe false
      N.terminalOutcome(exited143) shouldBe Some(N.TerminalOutcome.Failed)
      refused.foreach(value => N.terminalOutcome(value) shouldBe None)
      timeout.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Incomplete
      timeout.receipts.head.exitCode shouldBe None
    }

    "reconcile actual-shaped adjacent intent and timeout result while refusing missing duplicate crossed and late attempts" in {
      Given("R1,R8 E4: a specification-owned native PhaseValidation history and typed timeout result")
      val empty = take(InstanceRecord(InstanceIdentity("timeout-chain"), take(GoalPhaseRuntimeCatalog.loadC()).definition,
        initialRevision, Lifecycle.NotStarted, None, CorrelationReference("timeout-specification"),
        CausationReference("start"), None, None, None, Vector.empty).validateC)
      def _append_(from: InstanceRecord, cause: String): InstanceRecord = take(from.appendC(from.revision,
        GoalPhaseNativeRun.history(from, "phase.PhaseValidation", cause)))
      val frontier = _append_(empty, "source")
      val initial = N.Saved(frontier, N.Payload(frontier.revision.value.toString, "phase:" + _plan.phase,
        _candidate, Vector(_check), Vector.empty, None))
      val intent = N.Saved(_append_(frontier, "ordered-validation:check-intent-0"), initial.payload)
      val payload = _observation(ProcessExecutionTermination.TimedOut).copy(sourceRevision = initial.payload.sourceRevision)
      val result = N.Saved(_append_(intent.record, "ordered-validation:check-result-0"), payload)
      val attempts = Vector(N.Attempt(Some(intent), Some(result)))
      When("the ordinary actual-attempt reconciler sees complete and damaged chains")
      val reconciled = take(N.reconcileAttemptsC(initial, initial.payload, 0, attempts))
      val crossed = result.copy(payload = payload.copy(candidate = _base))
      val damaged = Vector(Vector(N.Attempt(None, Some(result))), attempts ++ attempts,
        Vector(N.Attempt(Some(intent), Some(crossed))),
        attempts :+ N.Attempt(Some(intent), None))
      Then("the timeout remains final without retry and every damaged chain refuses")
      reconciled.saved shouldBe result
      reconciled.nextAttempt shouldBe None
      N.continuable(result) shouldBe false
      damaged.foreach(value => N.reconcileAttemptsC(initial, initial.payload, 0, value).isSuccess shouldBe false)
      val missing = take(N.reconcileAttemptsC(initial, initial.payload, 0, Vector(N.Attempt(Some(intent), None))))
      N.terminalOutcome(missing.saved.payload) shouldBe None
    }

    "retain the complete distinct v2 target and reject unknown null missing crossed and false outcome records" in {
      Given("R2,R5,R6 E4: a complete specification-owned timeout source and captured corrective Plan")
      val wire = A.interruptedCodec.encode(_direction)
      val target = _target.asJson
      val source = target.hcursor.downField("source").focus.get
      val malformed = Vector(target.mapObject(_.add("unknown", Json.True)), target.mapObject(_.add("source", Json.Null)),
        target.mapObject(_.remove("source")), target.mapObject(_.add("source", source.mapObject(_.add("unknown", Json.True)))),
        target.mapObject(_.add("source", source.mapObject(_.add("intent", Json.Null)))))
      val crossed = Vector(_source.copy(kind = "known-failed"), _source.copy(termination = "termination-unconfirmed"),
        _source.copy(outcome = "failed"), _source.copy(decisionRevision = "14"),
        _source.copy(intent = _source.intent.copy(identity = "foreign-intent")),
        _source.copy(result = _source.result.copy(revision = "11")), _source.copy(attempt = 1))
      When("strict codecs reconstruct the canonical target and versioned Direction")
      val decoded = A.interruptedCodec.decode(wire)
      Then("v2 retains the timeout identity without manufacturing knownFailedPhase or accepting v1 schema")
      decoded shouldBe Right(_direction)
      wire.hcursor.get[String]("schemaVersion").toOption shouldBe Some(I.schema)
      A.codec.decode(wire).isLeft shouldBe true
      A.decodeBoundTarget(target) shouldBe Right(Right(_target))
      A.decodeTarget(target).isLeft shouldBe true
      target.hcursor.downField("recovery").focus shouldBe None
      malformed.foreach(value => A.decodeBoundTarget(value).isLeft shouldBe true)
      crossed.foreach(value => A.decodeBoundTarget(_target.copy(source = value).asJson).isLeft shouldBe true)
      A.interruptedCodec.decode(wire.mapObject(_.add("schemaVersion", Json.fromString(A.schema)))).isLeft shouldBe true
      A.interruptedCodec.decode(A.interruptedCodec.encode(_direction.copy(participant = "other"))).isLeft shouldBe true
    }

    "round trip complete v2 targets with 64-character Git objects while refusing invalid object lengths hex and case" in {
      Given("R2,R5 E4: a complete timeout target with existing lowercase 64-character base and candidate Git object IDs")
      val base = O.Snapshot("a" * 64, "b" * 64)
      val candidate = O.Snapshot("c" * 64, "d" * 64)
      val target = _target.copy(source = _source.copy(base = base, candidate = candidate),
        completed = Vector(O.ClosedStep("S1", O.Commit(candidate.head, base.head, candidate.tree), "independent-review")))
      val direction = _direction.copy(target = target)
      val invalid = Vector("a" * 63, "a" * 65, "g" * 64, "A" * 64)
      val malformed = invalid.flatMap(value => Vector(
        target.copy(source = target.source.copy(base = base.copy(head = value))),
        target.copy(source = target.source.copy(base = base.copy(tree = value))),
        target.copy(source = target.source.copy(candidate = candidate.copy(head = value))),
        target.copy(source = target.source.copy(candidate = candidate.copy(tree = value)))))
      When("strict target and Direction codecs decode the complete valid and malformed Git object contracts")
      val restoredtarget = A.decodeBoundTarget(target.asJson)
      val restoreddirection = A.interruptedCodec.decode(A.interruptedCodec.encode(direction))
      val refusedtargets = malformed.map(value => A.decodeBoundTarget(value.asJson))
      val refuseddirections = malformed.map(value => A.interruptedCodec.decode(
        A.interruptedCodec.encode(direction.copy(target = value))))
      Then("the existing 64-character domain round trips with the corresponding accepted commit and every invalid object refuses")
      restoredtarget shouldBe Right(Right(target))
      restoreddirection shouldBe Right(direction)
      target.completed.head.commit shouldBe O.Commit(candidate.head, base.head, candidate.tree)
      refusedtargets.foreach(_.isLeft shouldBe true)
      refuseddirections.foreach(_.isLeft shouldBe true)
    }

    "prepare only the actual v2 description proposal participant and new Human reference with exact evidence binding" in {
      Given("R4 E4: matching actual-description and independent Human inputs, owned solely by this specification")
      val description = V.GoalPhaseFailedPhaseRepairDescriptionResult(_source.sessionId, "adoption-required", A.message,
        "fixture-component", A.targetRecord(_target), A.choices, A.adoptionOperation)
      val input = V.GoalPhaseFailedPhaseRepairAdoptionInput(_source.sessionId, description.target,
        GoalPhasePlanRevision.publicProposal(Some(_proposal)).get, "AdoptRepairPlan", _direction.rationale,
        V.WorkflowArtifactReference(_human.identity, _human.revision))
      val execution = V.WorkflowExecutionEvidence(Vector(input.reference), Some("participant"), None, None)
      def _prepare_(value: V.GoalPhaseFailedPhaseRepairAdoptionInput, participant: String = "participant") =
        NativeFailedPhaseRepairPreparation.prepareC(description, value, value.reference,
          execution.copy(references = Vector(value.reference)), participant, "independent-issuer", Instant.parse("2026-10-24T00:00:00Z"))
      When("passive preparation receives canonical and crossed targets and proposals")
      val prepared = take(_prepare_(input))
      val binding = take(A.bindingC("fixture-component", _target, input.choice, input.rationale, "participant", _human))
      Then("only exact target and Human scope produce the existing evidence row without adoption")
      take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get)).binding shouldBe binding
      A.selectBinding(binding) shouldBe Right(binding)
      _prepare_(input.copy(target = A.targetRecord(_target.copy(source = _source.copy(candidate = _base))))).isSuccess shouldBe false
      _prepare_(input.copy(proposal = GoalPhasePlanRevision.publicProposal(Some(_proposal.copy(reason = "crossed"))).get)).isSuccess shouldBe false
      _prepare_(input, "other").isSuccess shouldBe false
      val oldanswer = take(A.bindingC("fixture-component", _target, input.choice, input.rationale, "participant",
        ContextReference("old-human", "1")))
      oldanswer should not be binding
      A.selectBinding(binding.mapObject(_.add("unknown", Json.True))).isLeft shouldBe true
      A.selectBinding(binding.mapObject(_.add("failedPhaseRepairAdoption", Json.Null))).isLeft shouldBe true
      take(_prepare_(input.copy(choice = "Defer"))).record should not be prepared.record
    }
  }
}
