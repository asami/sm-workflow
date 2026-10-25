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
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
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
    "in spec:goal-phase-failed-phase-repair-adoption, example:E2, rules:R2,R4,R5,R6, phase:P116-CONT-01") {
    "retain the complete recorded candidate paths and historical ordinals when the host is unchanged or appends exact paths" in {
      Given("docs/spec/goal-phase-failed-phase-repair-adoption.md R2,R6 E2: complete recorded candidate paths with fixed ordinal mappings")
      val recorded = _target.candidatePaths
      val extended = recorded ++ Vector("src/main/CompileConsumer.scala", "docs/Repair.md")
      When("retained traversal reconciles the unchanged paths and their valid append-only extension")
      val unchanged = take(A.retainedCandidatePathsC(recorded, recorded))
      val historical = take(A.retainedCandidatePathsC(recorded, extended))
      Then("both return the complete original vector and every original path keeps its fixed ordinal")
      unchanged shouldBe recorded
      historical shouldBe recorded
      historical should not be extended
      recorded.zipWithIndex.foreach { case (path, index) =>
        historical.indexOf(path) + 1 shouldBe index + 1
        extended.indexOf(path) + 1 shouldBe index + 1
      }
    }

    "refuse crossed order removal insertion replacement duplicate invalid null empty and overlong candidate paths" in {
      Given("docs/spec/goal-phase-failed-phase-repair-adoption.md R2,R6 E2: recorded paths and current vectors crossing or invalidating their complete prefix")
      val recorded = _target.candidatePaths
      val future = "src/main/CompileConsumer.scala"
      val crossed = Vector(recorded.reverse, recorded.take(1), future +: recorded,
        Vector(recorded.head, future, recorded.last), Vector(recorded.head, future))
      val invalid = Vector(Vector.empty[String], null, recorded :+ recorded.head, recorded :+ null,
        recorded :+ "", recorded :+ "/absolute.scala", recorded :+ "../outside.scala",
        recorded :+ "src/../outside.scala", recorded :+ "src//Invalid.scala",
        recorded :+ "src/./Invalid.scala", recorded :+ ".git/config", recorded :+ "src/Bad\u0000.scala",
        recorded :+ "src/*.scala", recorded :+ "src/?.scala", recorded :+ "src/[A.scala",
        recorded :+ "src/A].scala", Vector.tabulate(257)(index => "src/Path" + index + ".scala"))
      When("retained traversal reconciles crossed hosts and each invalid recorded or current vector")
      val refused = crossed.map(A.retainedCandidatePathsC(recorded, _)) ++ invalid.flatMap(paths =>
        Vector(A.retainedCandidatePathsC(recorded, paths), A.retainedCandidatePathsC(paths, recorded)))
      Then("every crossed or invalid vector safely refuses before historical reconstruction")
      refused.foreach(_.isSuccess shouldBe false)
    }

    "retain the complete recorded closure policy when the current host is unchanged or only appends future programs" in {
      Given("docs/spec/goal-phase-failed-phase-repair-adoption.md R2,R6 E2: a complete historically bound closure policy")
      val recorded = _target.closurePolicy
      val extended = recorded.copy(programs = recorded.programs :+ GoalPhaseNativeClosure.StepProgram("S3", 3))
      When("retained traversal reconciles the unchanged policy and its append-only extension")
      val unchanged = take(A.retainedClosurePolicyC(recorded, recorded))
      val historical = take(A.retainedClosurePolicyC(recorded, extended))
      Then("both retain the exact complete original policy rather than the current extended policy")
      unchanged shouldBe recorded
      historical shouldBe recorded
      historical.programs shouldBe Vector(GoalPhaseNativeClosure.StepProgram("S1", 1), GoalPhaseNativeClosure.StepProgram("S2", 2))
      historical should not be extended
    }

    "refuse crossed identity revision mappings order removal insertion and invalid or null closure policies" in {
      Given("docs/spec/goal-phase-failed-phase-repair-adoption.md R2,R6 E2: the recorded policy and host policies crossing its fixed prefix")
      val recorded = _target.closurePolicy
      val future = GoalPhaseNativeClosure.StepProgram("S3", 3)
      val crossed = Vector(recorded.copy(identity = "other-closure"), recorded.copy(revision = 2),
        recorded.copy(programs = Vector(GoalPhaseNativeClosure.StepProgram("S1", 2), GoalPhaseNativeClosure.StepProgram("S2", 1))),
        recorded.copy(programs = recorded.programs.reverse), recorded.copy(programs = recorded.programs.take(1)),
        recorded.copy(programs = future +: recorded.programs),
        recorded.copy(programs = Vector(recorded.programs.head, future, recorded.programs.last)),
        recorded.copy(programs = Vector(recorded.programs.head, GoalPhaseNativeClosure.StepProgram("other-step", 2))))
      val invalid = Vector(recorded.copy(identity = ""), recorded.copy(revision = 0),
        recorded.copy(programs = Vector.empty), recorded.copy(programs = null),
        recorded.copy(programs = recorded.programs :+ recorded.programs.head),
        recorded.copy(programs = recorded.programs :+ GoalPhaseNativeClosure.StepProgram("S3", 2)),
        recorded.copy(programs = recorded.programs :+ GoalPhaseNativeClosure.StepProgram("S3", 0)),
        recorded.copy(programs = recorded.programs :+ GoalPhaseNativeClosure.StepProgram("S3", 65)),
        recorded.copy(programs = recorded.programs :+ GoalPhaseNativeClosure.StepProgram("", 3)),
        recorded.copy(programs = recorded.programs :+ null), null)
      When("retained traversal reconciles each crossed host and each invalid recorded or current policy")
      val refused = crossed.map(A.retainedClosurePolicyC(recorded, _)) ++ invalid.flatMap(policy =>
        Vector(A.retainedClosurePolicyC(recorded, policy), A.retainedClosurePolicyC(policy, recorded)))
      Then("every mutation or invalid policy refuses before historical target reconstruction")
      refused.foreach(_.isSuccess shouldBe false)
    }

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
      direction.hcursor.get[String]("schemaVersion").toOption shouldBe Some("sm-workflow.goal-phase-failed-phase-repair-adoption.v1")
      A.decodeBoundTarget(target) shouldBe Right(Left(_target))
      take(A.boundTargetC(A.targetRecord(_target))) shouldBe Left(_target)
      A.interruptedCodec.decode(direction).isLeft shouldBe true
      A.decodeBoundTarget(target.mapObject(_.add("source", Json.obj(
        "kind" -> Json.fromString("terminated-timeout"))))).isLeft shouldBe true
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
