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
  * @version Oct. 27, 2026
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
  private val _completed_target = {
    val policy = GoalPhaseStepAssessment.Policy("conditional-code", "code", "conditional", "off", "off", Vector.empty)
    val plan = _plan.copy(steps = Vector("S1", "S2", "S3").map(name =>
      _plan.steps.head.copy(identity = name, purpose = "Preserve boundary " + name, actionPolicy = Some(policy))))
    val completed = Vector(("S1", "c", "a"), ("S2", "d", "c")).map { (step, head, parent) =>
      val candidate = O.Snapshot(head * 40, "e" * 40)
      val assessment = GoalPhaseStepAssessment.Result(policy, candidate, step + "-checks", Vector("check"),
        "admit", "Selected checks passed with no immediate obligation", "not-selected", "not-executed")
      O.ClosedStep(step, O.Commit(candidate.head, parent * 40, candidate.tree), None,
        Some(GoalPhaseStepAssessment.admission(assessment)))
    }
    _target.copy(plan = plan, completed = completed, cursor = completed.size, generation = 4,
      repairProgress = GoalPhaseRepairProgress.Snapshot(2, 1, 3), stage = "Closing",
      candidate = Some(O.Snapshot("f" * 40, "1" * 40)), checksReceipt = Some("current-step-checks"))
  }
  private def _change(value: Json, path: Vector[String], change: Json => Json): Json =
    if (path.isEmpty) change(value) else value.asObject match {
      case Some(fields) => value.mapObject(_.add(path.head, _change(fields(path.head).get, path.tail, change)))
      case None => value.mapArray(items => items.updated(path.head.toInt, _change(items(path.head.toInt), path.tail, change)))
    }
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
    "E67 retain a partial-chain completed prefix and consumed repair counters in policy targets and directions" in {
      Given("docs/spec/goal-phase-closure-recovery.md R8 E67: a formally admitted completed prefix with actual P/C/D metadata")
      val target = _completed_target
      val first = target.completed.head
      val partial = O.Commit("9" * 40, _base.head, "8" * 40)
      val completion = first.commit.copy(parent = partial.identity)
      val chain = O.StepCommitChain(_base, partial, completion)
      val retained = target.copy(completed = target.completed.updated(0, first.copy(commit = completion, partialCompletion = Some(chain))))
      val direction = A.Direction(retained, "AdoptRuntimePolicy", _reference, "Keep exact completed prefix")
      When("the existing policy target public projection and immutable direction roundtrip")
      val decoded = take(A.targetC(A.targetRecord(retained)))
      val saved = A.codec.decode(A.codec.encode(direction))
      Then("the chain and the original actual parent survive without a counter reset or invented direct parent")
      decoded shouldBe retained
      saved shouldBe Right(direction)
      decoded.repairProgress shouldBe target.repairProgress
      decoded.completed.head.commit.parent shouldBe partial.identity
      decoded.completed.head.partialCompletion shouldBe Some(chain)
      val forged = retained.copy(completed = retained.completed.updated(0,
        retained.completed.head.copy(partialCompletion = Some(chain.copy(completion = completion.copy(parent = _base.head))))))
      A.decodeTarget(forged.asJson).isLeft shouldBe true
    }

    "project a reviewless completed prefix publicly while retaining exact internal Target Direction and evidence binding bytes" in {
      Given("D4-PUBLIC-TARGET-01: two formally admitted completed Steps, a later current Step, nonzero counts and current candidate checks")
      val target = _completed_target
      val retained = target.asJson
      val direction = A.Direction(target, "AdoptRuntimePolicy", _reference, "Independent fixture direction")
      val directionwire = A.codec.encode(direction)
      val binding = take(A.bindingC("fixture-component", target, direction.choice, direction.rationale, target.participant, _reference))
      When("the retained codecs read their original complete representation")
      val decoded = A.decodeTarget(retained)
      val decodeddirection = A.codec.decode(directionwire)
      Then("review nulls and every retained value remain byte-meaning identical")
      target.completed.forall(step => step.review.isEmpty && step.admission.exists(GoalPhaseStepAssessment.validAdmission)) shouldBe true
      retained.hcursor.downField("completed").downN(0).downField("review").focus shouldBe Some(Json.Null)
      decoded shouldBe Right(target)
      decoded.map(_.asJson) shouldBe Right(retained)
      decodeddirection shouldBe Right(direction)
      decodeddirection.map(A.codec.encode) shouldBe Right(directionwire)
      A.selectBinding(binding) shouldBe Right(binding)

      When("the public Record omits object nulls and its untouched description is prepared with independent fixture direction")
      val record = A.targetRecord(target)
      val publicjson = GoalPhaseNativeMetadata.normalizePublicJson(
        io.circe.parser.parse(take(record.toJsonStringC)).toOption.get).fold(message => fail(message), identity)
      val description = V.GoalPhaseRepairPolicyDescriptionResult(target.sessionId, "adoption-required", A.message,
        "fixture-component", record, A.choices, A.adoptionOperation)
      val reference = V.WorkflowArtifactReference(_reference.identity, _reference.revision)
      val input = V.GoalPhaseRepairPolicyAdoptionInput(target.sessionId, description.target, direction.choice, direction.rationale, reference)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(target.participant), None, None)
      val prepared = take(NativeRepairPolicyPreparation.prepareC(description, input, reference, execution,
        target.participant, "independent-fixture-issuer", Instant.parse("2026-10-25T00:00:00Z")))
      val evidence = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      Then("the public target reconstructs exactly and preparation binds the unchanged retained representation")
      publicjson shouldBe retained.deepDropNullValues
      publicjson.hcursor.downField("completed").downN(0).downField("review").focus shouldBe None
      take(A.targetC(record)) shouldBe target
      A.Target.publicRecordDecoder.decodeJson(publicjson) shouldBe Right(target)
      A.decodeTarget(publicjson).isLeft shouldBe true
      evidence.binding shouldBe binding
      evidence.binding.hcursor.downField("repairPolicyAdoption").downField("target").focus shouldBe Some(retained)
      target.asJson shouldBe retained
    }

    "reject incomplete public Plan completed admission assessment candidate counts and target fields without relaxing retained decoding" in {
      Given("D4-PUBLIC-TARGET-01: the complete public projection with a valid reviewless formal admission")
      val target = _completed_target
      val retained = target.asJson
      val projected = retained.deepDropNullValues
      val required = Vector(
        Vector.empty[String] -> Vector("sessionId", "definitionIdentity", "definitionRevision", "frontierRevision", "frontierCause",
          "participant", "plan", "base", "completed", "cursor", "generation", "repairProgress", "stage", "selectedLimit", "effectiveLimit", "proposedPolicy"),
        Vector("plan") -> Vector("identity", "revision", "phase", "repository", "steps", "phaseChecks", "repairLimit"),
        Vector("plan", "steps", "0") -> Vector("identity", "purpose", "ownedPaths", "dependencies", "checks"),
        Vector("completed", "0") -> Vector("step", "commit", "admission"),
        Vector("completed", "0", "commit") -> Vector("identity", "parent", "tree"),
        Vector("completed", "0", "admission") -> Vector("reference", "assessment"),
        Vector("completed", "0", "admission", "assessment") -> Vector("policy", "candidate", "validationReference", "checks", "branch", "reason", "triage", "review"),
        Vector("completed", "0", "admission", "assessment", "policy") -> Vector("identity", "kind", "detailedReview", "scalaLint", "carLint", "observations"),
        Vector("completed", "0", "admission", "assessment", "candidate") -> Vector("head", "tree"),
        Vector("candidate") -> Vector("tree"),
        Vector("repairProgress") -> Vector("automaticCount", "ordinaryCount", "limit"))
      When("any required field is omitted from the public target")
      val incomplete = required.flatMap { (path, fields) => fields.map(field =>
        (path :+ field).mkString(".") -> _change(projected, path, _.mapObject(_.remove(field)))) }
      Then("every typed public read refuses the incomplete contract")
      incomplete.foreach { (name, wire) => withClue(name) {
        A.Target.publicRecordDecoder.decodeJson(wire).isLeft shouldBe true
        A.targetC(GoalPhaseNativeMetadata.record(wire)).isSuccess shouldBe false
      } }

      When("unknown fields, explicit nulls, a null array entry or an invalid admission are supplied")
      val invalid = Vector(
        projected.mapObject(_.add("unknownTarget", Json.fromBoolean(true))),
        _change(projected, Vector("plan"), _.mapObject(_.add("unknownPlan", Json.fromBoolean(true)))),
        _change(projected, Vector("completed", "0"), _.mapObject(_.add("unknownStep", Json.fromBoolean(true)))),
        _change(projected, Vector("completed", "0", "admission", "assessment"), _.mapObject(_.add("unknownAssessment", Json.fromBoolean(true)))),
        projected.mapObject(_.add("restoration", Json.Null)),
        _change(projected, Vector("completed", "0"), _.mapObject(_.add("review", Json.Null))),
        _change(projected, Vector("completed"), _.mapArray(_ :+ Json.Null)),
        _change(projected, Vector("completed", "0", "admission"), _.mapObject(_.add("reference", Json.fromString("different-admission")))),
        _change(projected, Vector("completed", "0", "admission", "assessment"), _.mapObject(_.add("branch", Json.fromString("detailed-review")))))
      Then("public decoding never erases supplied nulls or unknown fields and omission requires a valid formal admission")
      invalid.foreach(wire => A.Target.publicRecordDecoder.decodeJson(wire).isLeft shouldBe true)
      A.decodeTarget(retained.mapObject(_.add("restoration", Json.Null))).isLeft shouldBe true
      A.decodeTarget(retained.mapObject(_.add("unknownTarget", Json.fromBoolean(true)))).isLeft shouldBe true
      A.decodeTarget(_change(retained, Vector("completed", "0"), _.mapObject(_.remove("review")))).isLeft shouldBe true
      A.Target.publicRecordDecoder.decodeJson(projected) shouldBe Right(target)
    }

    "refuse independently valid changed candidate counts Plan or frontier against the genuine completed-prefix description" in {
      Given("D4-PUBLIC-TARGET-01: a genuine public description and independent fixture direction for its complete target")
      val target = _completed_target
      val retained = target.asJson
      val description = V.GoalPhaseRepairPolicyDescriptionResult(target.sessionId, "adoption-required", A.message,
        "fixture-component", A.targetRecord(target), A.choices, A.adoptionOperation)
      val reference = V.WorkflowArtifactReference(_reference.identity, _reference.revision)
      val input = V.GoalPhaseRepairPolicyAdoptionInput(target.sessionId, description.target, "AdoptRuntimePolicy", "Independent fixture direction", reference)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(target.participant), None, None)
      val changed = Vector(target.copy(candidate = Some(target.candidate.get.copy(tree = "2" * 40))),
        target.copy(repairProgress = target.repairProgress.copy(automaticCount = 3, ordinaryCount = 2)),
        target.copy(plan = target.plan.copy(steps = target.plan.steps.updated(2, target.plan.steps(2).copy(purpose = "Different current purpose")))),
        target.copy(frontierRevision = "13"))
      When("a different complete target is supplied beside that unchanged description")
      val decoded = changed.map(value => A.decodeTarget(value.asJson))
      val publictargets = changed.map(value => take(A.targetC(A.targetRecord(value))))
      val results = changed.map { value =>
        NativeRepairPolicyPreparation.prepareC(description, input.copy(target = A.targetRecord(value)), reference, execution,
          target.participant, "independent-fixture-issuer", Instant.parse("2026-10-25T00:00:00Z"))
      }
      Then("preparation rejects the crossed target while the genuine target remains unchanged")
      decoded shouldBe changed.map(Right(_))
      publictargets shouldBe changed
      results.foreach(_.isSuccess shouldBe false)
      target.asJson shouldBe retained
      take(A.targetC(description.target)) shouldBe target
    }

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
