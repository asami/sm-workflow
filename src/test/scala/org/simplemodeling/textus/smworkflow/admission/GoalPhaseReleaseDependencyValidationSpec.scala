package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.Path
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseDependencyValidation as D
import GoalPhaseReleaseDependencyValidation as R
import DependencyValidationSpecificationFixture as F
import SplitPhaseExplicitPartitionFixture.take
import D.given

/** Pure structural examples never manufacture native closure or Human authority.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseReleaseDependencyValidationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _binding = F.binding.copy(identity = "release-help-validation", step = "phase:" + F.plan.phase,
    paths = Vector("src/main/scala/Help.scala"))
  private val _prefix = D.AcceptedPrefix(D.acceptedPrefixSchema, "same-session",
    ContextReference(F.plan.identity, F.plan.revision), F.binding.step, "30", F.capture.candidate, F.paths)
  private val _approval = F.approvals.head.copy(step = _binding.step,
    proposal = F.approvals.head.proposal.copy(identity = "release-help-proposal", paths = _binding.paths,
      usageContract = ContextReference("release-help-usage", "1")), decision = ContextReference("phase-help-answer", "1"))
  private val _admission = D.Admission(_binding, Vector(_approval), Some(_prefix))
  private val _payload = R.Payload("same-session", _prefix.plan, F.primaryBase, 2,
    GoalPhaseRepairProgress.Snapshot(1, 1, 2), "40", "38", "35", F.primaryBase,
    _admission, Vector.empty)

  "Optional explicit phase dependency host binding" should {
    "preserve old absence and round trip an exact phase binding without aliasing the Step field" in {
      Given("an ordinary host definition and a phase binding for its actual Phase and requester")
      val host = ThreeWorkflowNormalHostFixture.definition(Path.of("/specification/host"))
      val binding = _binding.copy(step = "phase:" + host.goal.input.phaseIdentity,
        requester = _binding.requester.copy(repository = host.goal.input.repositoryIdentity))
      val selected = host.copy(goal = host.goal.copy(phaseDependencyValidation = Some(binding)))
      When("legacy and explicitly selected definitions use the normal runtime codec")
      val old = ThreeWorkflowRuntimeParameters.encode(host)
      val wire = ThreeWorkflowRuntimeParameters.encode(selected)
      val decoded = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(wire))
      Then("absence stays absent and the new field preserves only its own exact binding")
      old.hcursor.downField("goal").downField("phaseDependencyValidation").focus shouldBe None
      take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(old)).goal.phaseDependencyValidation shouldBe None
      decoded.goal.phaseDependencyValidation shouldBe Some(binding)
      decoded.goal.dependencyValidation shouldBe host.goal.dependencyValidation
    }

    "reject explicit null, malformed objects and a different phase in the new field" in {
      Given("the ordinary versioned host configuration")
      val host = ThreeWorkflowNormalHostFixture.definition(Path.of("/specification/host"))
      val wire = ThreeWorkflowRuntimeParameters.encode(host)
      val values = Vector(Json.Null, Json.obj(), _binding.copy(step = "phase:different").asJson)
      When("each invalid new field is decoded through the ExecutionContext-owned runtime decoder")
      val results = values.map(value => ThreeWorkflowRuntimeParameters.decodeDefinitionC(
        wire.mapObject(_.add("goal", wire.hcursor.downField("goal").focus.get.mapObject(
          _.add("phaseDependencyValidation", value))))).isSuccess)
      Then("none falls back to the old Step binding")
      results shouldBe Vector(false, false, false)
    }
  }

  "Phase admission and immutable closed-Step prefix" should {
    "retain a separate exact approval union and the original accepted prefix" in {
      Given("structurally distinct old paths and an actual-phase approval supplied by the native owner")
      When("the structural admission gate compares purpose, component and exact union")
      val admitted = take(D.phaseAdmissionC(_binding, F.approvals :+ _approval, F.plan, Some(_prefix)))
      Then("only the new phase approval is retained and old Step authority remains separate")
      admitted shouldBe _admission
      admitted.approvals should not contain F.approvals.head
      admitted.acceptedPrefix shouldBe Some(_prefix)
      F.capture.admission shouldBe F.admission
    }

    "refuse old approvals, replayed decisions, crossed purposes and overlapping new paths" in {
      Given("one valid phase proposal and one immutable accepted Step prefix")
      val crossed = Vector(
        Vector.empty[D.Approval], F.approvals, Vector(_approval, _approval),
        Vector(_approval.copy(step = "phase:other")),
        Vector(_approval.copy(proposal = _approval.proposal.copy(requester = F.dependency))),
        Vector(_approval.copy(proposal = _approval.proposal.copy(paths = _binding.paths :+ "unapproved.txt"))))
      When("admission requires the exact matching current phase proposal union")
      val denied = crossed.map(D.phaseAdmissionC(_binding, _, F.plan, Some(_prefix)).isSuccess)
      val overlap = _binding.copy(paths = F.paths)
      val overlapapproval = _approval.copy(proposal = _approval.proposal.copy(paths = F.paths))
      val overlapped = D.phaseAdmissionC(overlap, Vector(overlapapproval), F.plan, Some(_prefix))
      val absent = D.phaseAdmissionC(_binding, Vector(_approval), F.plan, None)
      Then("no old, duplicated, crossed, unapproved or overlapping authority is admitted")
      denied.foreach(_ shouldBe false)
      overlapped.isSuccess shouldBe false
      absent.isSuccess shouldBe false
    }
  }

  "Versioned Release mechanical receipt" should {
    "round trip the original primary reference and distinct new capture source" in {
      Given("a structural Release source and its fully staged new dependency candidate")
      val capture = D.Capture(_payload.session, _payload.plan, _binding.step, _payload.generation, "44",
        _payload.base, _admission, GoalPhaseOrderedProgression.Snapshot(F.base.head, "f" * 40), _binding.paths,
        "diff --git a/Help.scala b/Help.scala\n+help\n")
      val completed = _payload.copy(staged = _binding.paths, capture = Some(capture))
      When("the closed versioned codec serializes the new owner only")
      val decoded = R.codec.decode(R.codec.encode(completed))
      Then("the immutable primary source and the actual dependency observation source remain distinct")
      decoded shouldBe Right(completed)
      capture.sourceRevision should not be completed.primarySourceRevision
      R.codec.encode(completed).hcursor.get[String]("schemaVersion") shouldBe Right(R.schema)
    }

    "reject changed source identities, counters, nulls and unknown nested fields" in {
      Given("a valid structural Release frontier")
      val valid = R.codec.encode(_payload)
      R.payloadValid(_payload) shouldBe true
      val invalid = Vector(
        _payload.copy(session = "other-session"),
        _payload.copy(plan = ContextReference("other-plan", "1")),
        _payload.copy(primarySourceRevision = "41"),
        _payload.copy(frontierRevision = "37"),
        _payload.copy(generation = 0),
        _payload.copy(repairProgress = GoalPhaseRepairProgress.Snapshot(0, 0, 2)),
        _payload.copy(staged = Vector("outside.txt")))
      When("the codec sees crossed source fields or additional caller claims")
      val decoded = invalid.map(value => R.codec.decode(R.codec.encode(value)).isLeft)
      val injected = Vector(
        valid.mapObject(_.add("schemaVersion", "other-version".asJson)),
        valid.mapObject(_.add("claimedAuthority", true.asJson)),
        valid.mapObject(_.add("historicalPhaseValidation", Json.obj("passed" -> true.asJson,
          "reviewReturned" -> "repair".asJson, "source" -> R.codec.encode(_payload)))),
        valid.mapObject(_.add("payload", valid.hcursor.downField("payload").focus.get.mapObject(_.add("capture", Json.Null)))),
        valid.mapObject(_.add("payload", valid.hcursor.downField("payload").focus.get.mapObject(o =>
          o.add("plan", o("plan").get.mapObject(_.add("claimedNativeClosure", true.asJson)))))))
      Then("all crossed sources and extra fields refuse rather than becoming reconstructed chain authority")
      decoded.foreach(_ shouldBe true)
      injected.foreach(value => R.codec.decode(value).isLeft shouldBe true)
      D.fromPublicC(Some(GoalPhaseNativeMetadata.record(_admission.asJson))).isSuccess shouldBe false
    }
  }
}
