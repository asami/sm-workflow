package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, Decoder, Encoder}
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.protocol.Property
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeRepairPolicyPreparation
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseRepairPolicyAdoption as A
import GoalPhaseOrderedProgression as O
import GoalPhaseRuntimeRepairControl as R
import SplitPhaseExplicitPartitionFixture.take

/** Exact wires and real isolated same-Session mechanical direction replay.
  * Every direction is specification-owned, never genuine live Human consent.
  * @since   Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class GoalPhaseRepairPolicyAdoptionSpec extends AnyWordSpec with GivenWhenThen with GoalPhasePostCompletionSyncFixture {
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private val _plan = O.Plan("rrc-wire-plan", "1", "rrc-phase", "rrc-repository",
    Vector(O.Step("S1", "Preserve this boundary", Vector("value.txt"), Vector.empty, Vector("check"))), Vector("phase-check"), 3)
  private val _target = A.Target("fixture-session", "GoalPhaseWorkflow", "1", "12", "actual-frontier", "participant",
    _plan, _base, Vector.empty, 0, 1, GoalPhaseRepairProgress.Snapshot(1, 1, 3), "Repair", None, None, None, None,
    None, 3, 3, R.version)
  private val _reference = ContextReference("independent-fixture-direction", "1")
  private val _link = R.RestorationLink(_target.sessionId, _plan.repository, "suspended-consumer", "consumer-repository", "restore producer runtime")
  private def _describe(e: Episode, identity: String, restoration: Option[org.goldenport.record.Record] = None): V.GoalPhaseRepairPolicyDescriptionResult =
    e.native.invoke[V.GoalPhaseRepairPolicyDescriptionResult](A.describeOperation,
      List(Property("input", V.GoalPhaseRepairPolicyDescriptionInput(identity, restoration).toRecord(), None)))
  private def _prepare(e: Episode, description: V.GoalPhaseRepairPolicyDescriptionResult,
    choice: String, name: String): V.GoalPhaseRepairPolicyAdoptionInput = {
    val ref = V.WorkflowArtifactReference(name, "1")
    val input = V.GoalPhaseRepairPolicyAdoptionInput(description.sessionId, description.target, choice,
      "Specification-owned independently admitted explicit policy direction", ref)
    val prepared = take(NativeRepairPolicyPreparation.prepareC(description, input, ref,
      V.WorkflowExecutionEvidence(Vector(ref), Some("participant"), None, None), "participant", "normal-development-host",
      e.native.actor.clock.instant().minusSeconds(60)))
    e.native.prepare(call => call.createC("host_evidence", prepared.recordIdentity, prepared.record))
    input
  }

  "Exact repair policy contracts" should {
    "roundtrip the complete target with optional omission and strictly reject null partial crossed or unknown policy fields" in {
      Given("RRC-05: a complete legacy target and the same target with an explicit producer/consumer business link")
      val linked = _target.copy(restoration = Some(_link))
      When("targets cross the canonical codec")
      A.decodeTarget(_target.asJson) shouldBe Right(_target)
      A.decodeTarget(linked.asJson) shouldBe Right(linked)
      Then("omission remains omission and explicit null or extra fields fail closed")
      _target.asJson.hcursor.downField("restoration").focus shouldBe None
      Vector("restoration", "pendingQuestion", "currentPolicy", "candidate", "requestingResult", "pendingRepair").foreach { key =>
        A.decodeTarget(_target.asJson.mapObject(_.add(key, Json.Null))).isLeft shouldBe true
      }
      A.decodeTarget(_target.asJson.mapObject(_.add("unseenOrigin", Json.fromString("Human")))).isLeft shouldBe true
      A.decodeTarget(linked.copy(restoration = Some(_link.copy(producerSession = "crossed"))).asJson).isLeft shouldBe true
      val progress = GoalPhaseRepairProgress.Snapshot(4, 3, 3, Some(R.version), Some(10), Some(10))
      progress.asJson.as[GoalPhaseRepairProgress.Snapshot] shouldBe Right(progress)
      Vector("runtimePolicy", "selectedLimit", "effectiveLimit").foreach { key =>
        progress.asJson.mapObject(_.add(key, Json.Null)).as[GoalPhaseRepairProgress.Snapshot].isLeft shouldBe true
        progress.asJson.mapObject(_.remove(key)).as[GoalPhaseRepairProgress.Snapshot].isLeft shouldBe true
      }
      progress.copy(effectiveLimit = Some(11)).asJson.as[GoalPhaseRepairProgress.Snapshot].isLeft shouldBe true
      GoalPhaseRepairProgress.publicSnapshot(progress).selectedLimit shouldBe Some(10)
      GoalPhaseRepairProgress.publicSnapshot(progress).effectiveLimit shouldBe Some(10)
    }

    "bind exact independent participant choice reference and optional restoration without authenticating an origin label" in {
      Given("RRC-05/06/07: exact described target and fixture-owned external direction facts")
      val linked = _target.copy(restoration = Some(_link))
      val binding = take(A.bindingC("fixture-component", linked, "AdoptRuntimePolicy", "Actual independent fixture direction", "participant", _reference))
      val direction = A.Direction(linked, "AdoptRuntimePolicy", _reference, "Actual independent fixture direction")
      When("direction and evidence pass their closed codecs")
      A.selectBinding(binding) shouldBe Right(binding)
      A.codec.decode(A.codec.encode(direction)) shouldBe Right(direction)
      Then("tampered answer participant/reference/choice or missing exact link is refused by the binding contract")
      A.bindingC("fixture-component", linked, "AdoptRuntimePolicy", "direction", "crossed", _reference).isSuccess shouldBe false
      A.bindingC("fixture-component", linked, "invented", "direction", "participant", _reference).isSuccess shouldBe false
      val crossed = binding.mapObject(_.add("repairPolicyAdoption", binding.hcursor.downField("repairPolicyAdoption").focus.get
        .mapObject(_.add("answer", Json.obj("choice" -> Json.fromString("AdoptRuntimePolicy"),
          "rationale" -> Json.fromString("Actual independent fixture direction"), "participant" -> Json.fromString("other"),
          "reference" -> Json.obj("identity" -> Json.fromString(_reference.identity), "revision" -> Json.fromString(_reference.revision)))))))
      A.selectBinding(crossed).isLeft shouldBe true
      val description = V.GoalPhaseRepairPolicyDescriptionResult(_target.sessionId, "adoption-required", A.message,
        "fixture-component", A.targetRecord(linked), A.choices, A.adoptionOperation)
      val ref = V.WorkflowArtifactReference(_reference.identity, _reference.revision)
      val input = V.GoalPhaseRepairPolicyAdoptionInput(_target.sessionId, description.target, "AdoptRuntimePolicy", direction.rationale, ref)
      val execution = V.WorkflowExecutionEvidence(Vector(ref), Some("participant"), None, None)
      val prepared = take(NativeRepairPolicyPreparation.prepareC(description, input, ref, execution,
        "participant", "independent-fixture-issuer", Instant.parse("2026-10-25T00:00:00Z")))
      val evidence = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      evidence.purpose shouldBe A.purpose
      evidence.binding shouldBe binding
      NativeRepairPolicyPreparation.prepareC(description, input.copy(reference = ref.copy(revision = "2")), ref,
        execution, "participant", "independent-fixture-issuer", Instant.parse("2026-10-25T00:00:00Z")).isSuccess shouldBe false
    }

    "save a real guarded same-Session adoption retain the original records and rebuild without accepted-work or check replay" in {
      Given("RRC-05: a fresh isolated normal Start with the explicitly smaller native fixture policy")
      val root = freshRoot("rrc-native-policy-adoption")
      var identity = ""
      var adopted = Option.empty[org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord]
      var originalplan = Option.empty[O.Plan]
      episode(root, initialize = true) { e =>
        identity = e.native.start().sessionId
        val before = e.current(identity)
        val described = _describe(e, identity)
        val actual = take(A.targetC(described.target))
        originalplan = Some(actual.plan)
        val missing = V.GoalPhaseRepairPolicyAdoptionInput(identity, described.target, "AdoptRuntimePolicy", "Missing fixture evidence",
          V.WorkflowArtifactReference("absent", "1"))
        When("adoption is attempted without independent evidence for the exact target")
        val missingadoption = e.call(A.adoptionOperation, missing.toRecord())
        Then("the absent evidence refuses adoption")
        missingadoption.isSuccess shouldBe false
        When("an independently evidenced Defer direction is prepared")
        val defer = _prepare(e, described, "Defer", "rrc-defer")
        val afterpreparation = e.current(identity)
        Then("preparation leaves the original Session unchanged")
        sameInstance(afterpreparation, before)
        When("the exact Defer direction is submitted")
        val deferred = e.native.invoke[V.GoalPhaseRepairPolicyAdoptionResult](A.adoptionOperation,
          List(Property("input", defer.toRecord(), None)))
        Then("Defer reports its outcome without changing the Session")
        deferred.outcome shouldBe "deferred"
        sameInstance(e.current(identity), before)
        Given("an independently evidenced adoption direction with a crossed repair count")
        val direction = _prepare(e, described, "AdoptRuntimePolicy", "rrc-adopt")
        val crossed = actual.copy(repairProgress = actual.repairProgress.copy(automaticCount = 1, ordinaryCount = 1))
        When("adoption is attempted against the crossed target")
        val crossedadoption = e.call(A.adoptionOperation, direction.copy(target = A.targetRecord(crossed)).toRecord())
        Then("the crossed target refuses adoption")
        crossedadoption.isSuccess shouldBe false
        When("the exact independently admitted adoption is executed")
        val result = e.native.invoke[V.GoalPhaseRepairPolicyAdoptionResult](A.adoptionOperation,
          List(Property("input", direction.toRecord(), None)))
        adopted = Some(e.current(identity))
        Then("one native transition preserves the original active history prefix and all counts")
        result.outcome shouldBe "repair-policy-adopted"
        adopted.get.history.dropRight(1) shouldBe before.history
        adopted.get.revision shouldBe before.revision.next
        adopted.get.currentProgression shouldBe before.currentProgression
        val after = take(A.targetC(_describe(e, identity).target))
        after.plan shouldBe actual.plan
        after.base shouldBe actual.base
        after.completed shouldBe actual.completed
        after.generation shouldBe actual.generation
        after.repairProgress.automaticCount shouldBe actual.repairProgress.automaticCount
        after.selectedLimit shouldBe 10
        e.call(A.adoptionOperation, direction.toRecord()).isSuccess shouldBe false
      }
      episode(root) { e =>
        When("the same isolated Session is rebuilt through retained history")
        val rebuilt = take(A.targetC(_describe(e, identity).target))
        Then("the original Plan and exact adopted current policy survive before ordinary first work dispatch")
        rebuilt.plan shouldBe originalplan.get
        rebuilt.currentPolicy shouldBe Some(R.version)
        rebuilt.selectedLimit shouldBe 10
        rebuilt.repairProgress.automaticCount shouldBe 0
        sameInstance(e.current(identity), adopted.get)
        val issued = e.advance(identity)
        issued.work.get.kind shouldBe "ImplementStep"
        issued.repairProgress.get.selectedLimit shouldBe Some(10)
        e.call(A.describeOperation, V.GoalPhaseRepairPolicyDescriptionInput(identity, None).toRecord()).isSuccess shouldBe false
      }
    }
  }
}
