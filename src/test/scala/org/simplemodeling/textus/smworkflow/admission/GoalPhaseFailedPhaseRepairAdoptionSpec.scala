package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.cncf.workflow.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeFailedPhaseRepairPreparation
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseFailedPhaseRepairAdoption as A
import GoalPhaseValidationRecovery as R
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Pure wire/preparation evidence. The native suite owns real failure proof.
  * @since   Oct. 23, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseFailedPhaseRepairAdoptionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _oid(value: Char): String = value.toString * 40
  private val _base = O.Snapshot(_oid('a'), _oid('b'))
  private val _commit = O.Commit(_oid('c'), _base.head, _oid('d'))
  private val _plan = O.Plan("repair-plan", "1", "phase-fixture", "fixture-repository",
    Vector(O.Step("S1", "Accepted source", Vector("src/main"), Vector.empty, Vector("compile"))), Vector("test"), 2)
  private val _proposed = _plan.copy(revision = "2", steps = _plan.steps :+
    O.Step("S2", "Correct the product check", Vector("src/test/ProductCheck.scala"), Vector("S1"), Vector("compile")))
  private val _proposal = GoalPhasePlanRevision.Proposal("corrective-direction", "1", "Repair the failed product check", _proposed)
  private val _failure = R.RecoveryTarget("fixture-session", _plan.identity, _plan.revision, _base,
    O.Snapshot(_commit.identity, _commit.tree), "10", "13", "phase:" + _plan.phase, "test", 2, 0, "11",
    Some(R.KnownFailedPhase(R.KnownFailedPhase.schema, "GoalPhaseWorkflow", "1", "participant",
      "fixture-session:ordered-validation:10:validation", "13", 1)))
  private val _target = A.Target(_failure, _plan, Vector(O.ClosedStep("S1", _commit, "independent-review")),
    GoalPhaseRepairProgress.Snapshot(1, 1, 2), 4, Vector("src/main", "src/test/ProductCheck.scala"),
    GoalPhaseNativeClosure.Policy("fixture-closure", 1, Vector(
      GoalPhaseNativeClosure.StepProgram("S1", 1), GoalPhaseNativeClosure.StepProgram("S2", 2))),
    GoalPhasePlanRevision.Captured(_proposal, _proposed), Vector(GoalPhaseNativeValidation.Check("compile", 1,
      WorkflowSbtValidation.Kind.Compile)))
  private val _reference = ContextReference("actual-human-direction", "1")
  private val _direction = A.Direction(_target, _reference, "participant", "Adopt one corrective Step")
  private val _description = V.GoalPhaseFailedPhaseRepairDescriptionResult("fixture-session", "adoption-required", A.message,
    "fixture-component", A.targetRecord(_target), A.choices, A.adoptionOperation)
  private val _input = V.GoalPhaseFailedPhaseRepairAdoptionInput("fixture-session", A.targetRecord(_target),
    GoalPhasePlanRevision.publicProposal(Some(_proposal)).get, "AdoptRepairPlan", _direction.rationale,
    V.WorkflowArtifactReference(_reference.identity, _reference.revision))
  private val _execution = V.WorkflowExecutionEvidence(Vector(_input.reference), Some("participant"), None, None)
  private def _prepare(input: V.GoalPhaseFailedPhaseRepairAdoptionInput = _input,
    execution: V.WorkflowExecutionEvidence = _execution, participant: String = "participant") =
    NativeFailedPhaseRepairPreparation.prepareC(_description, input, _input.reference, execution,
      participant, "fixture-independent-issuer", Instant.parse("2026-10-23T00:00:00Z"))

  "E2 exact failed Phase repair target and independent evidence" should afterWord(
    "in spec:goal-phase-failed-phase-repair-adoption, example:E2, rules:R2,R4,R5, phase:P116-CONT-01") {
    "retain the complete failure and captured proposal while refusing missing extended or explicitly null fields" in {
      Given("docs/spec/goal-phase-failed-phase-repair-adoption.md R2,R5 E2: a complete passive target and Direction")
      val target = _target.asJson
      val direction = A.codec.encode(_direction)
      val invalidtargets = Vector(target.mapObject(_.remove("generation")), target.mapObject(_.add("extra", Json.True)),
        target.mapObject(_.add("generation", Json.Null)), target.mapObject(_.add("candidatePaths", Json.Null)),
        target.mapObject(_.remove("closurePolicy")), target.mapObject(_.add("closurePolicy", Json.Null)),
        _target.copy(closurePolicy = _target.closurePolicy.copy(revision = 0)).asJson,
        _target.copy(closurePolicy = _target.closurePolicy.copy(programs = Vector(GoalPhaseNativeClosure.StepProgram("S1", 1)))).asJson,
        _target.copy(generation = Long.MaxValue).asJson,
        target.mapObject(_.add("recovery", _failure.asJson.mapObject(_.add("unknown", Json.True)))),
        target.mapObject(_.add("captured", _target.captured.asJson.mapObject(_.add("documents", Json.Null)))))
      val invaliddirections = Vector(direction.mapObject(_.add("extra", Json.True)), direction.mapObject(_.remove("schemaVersion")),
        direction.mapObject(_.add("payload", direction.hcursor.downField("payload").focus.get.mapObject(_.add("rationale", Json.Null)))),
        direction.mapObject(_.add("payload", direction.hcursor.downField("payload").focus.get.mapObject(_.add("unknown", Json.True)))))
      When("new wire boundaries decode those values")
      val decoded = A.decodeTarget(target)
      val restored = A.codec.decode(direction)
      Then("only complete exact non-null contracts round trip")
      decoded shouldBe Right(_target)
      restored shouldBe Right(_direction)
      invalidtargets.foreach(A.decodeTarget(_).isLeft shouldBe true)
      invaliddirections.foreach(A.codec.decode(_).isLeft shouldBe true)
    }

    "prepare exact AdoptRepairPlan and Defer scope while refusing crossed provenance and proposal" in {
      Given("the actual generated description, adoption input and matching independently observed worker/reference")
      val binding = take(A.bindingC("fixture-component", _target, _input.choice, _input.rationale, "participant", _reference))
      val changedproposal = _proposal.copy(reason = "Different captured proposal")
      val changedpolicy = _target.copy(closurePolicy = _target.closurePolicy.copy(revision = 2))
      When("passive preparation converts each actual choice and receives crossed inputs")
      val adopted = take(_prepare())
      val deferred = take(_prepare(_input.copy(choice = "Defer")))
      val refused = Vector(_prepare(_input.copy(choice = "AuthorizeNewAttempt")),
        _prepare(_input.copy(sessionId = "other-session")), _prepare(_input.copy(reference = _input.reference.copy(revision = "2"))),
        _prepare(_input.copy(proposal = GoalPhasePlanRevision.publicProposal(Some(changedproposal)).get)),
        _prepare(_input.copy(target = A.targetRecord(changedpolicy))),
        _prepare(execution = _execution.copy(workerIdentity = Some("other-worker"))), _prepare(participant = "other-participant"))
      Then("the rows bind different genuine answers without provisioning or granting adoption")
      adopted.recordIdentity shouldBe deferred.recordIdentity
      adopted.record should not be deferred.record
      A.selectBinding(binding) shouldBe Right(binding)
      A.selectBinding(binding.mapObject(_.add("unknown", Json.True))).isLeft shouldBe true
      val body = binding.hcursor.downField("failedPhaseRepairAdoption").focus.get
      A.selectBinding(binding.mapObject(_.add("failedPhaseRepairAdoption", body.mapObject(_.add("unknown", Json.True))))).isLeft shouldBe true
      refused.foreach(_.isSuccess shouldBe false)
      val encoded = adopted.record.getString(NativeExternalEvidence.storageField).get
      val evidence = take(NativeExternalEvidence.decodeStorageC(encoded))
      evidence.purpose shouldBe A.purpose
      evidence.binding shouldBe binding
      val declaration = NativeExternalEvidence.Declaration("workflow", org.goldenport.cncf.datastore.DataStore.CollectionId.EntityStore(
        org.simplemodeling.model.datatype.EntityCollectionId("fixture", "host", "evidence")),
        "fixture-independent-issuer")
      NativeExternalEvidence.observeC(evidence, declaration, NativeExternalEvidence.Purpose.FailedPhaseRepairAdoption,
        evidence.lookupIdentity, binding, Instant.parse("2026-10-23T00:00:01Z")).isSuccess shouldBe true
      Vector(evidence.copy(participant = "other"), evidence.copy(issuer = "other"), evidence.copy(revision = "2"),
        evidence.copy(occurredAt = "2026-10-24T00:00:00Z")).foreach { crossed =>
        NativeExternalEvidence.observeC(crossed, declaration, NativeExternalEvidence.Purpose.FailedPhaseRepairAdoption,
          evidence.lookupIdentity, binding, Instant.parse("2026-10-23T00:00:01Z")).isSuccess shouldBe false
      }
    }
  }
}
