package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.preparation.NativeLegacyPurposeRepairPreparation
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseLegacyPurposeRepairContinuation as P
import GoalPhaseReviewRepairAdoptionFixture as F
import GoalPhaseRemainingPlanAdoption as A
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as W
import SplitPhaseExplicitPartitionFixture.take

/** Specification-owned saved review and independent Human directive inputs.
  * Installed managed-history/reopen coverage lives in NativeGoalReviewRepairAdoptionCliSpec.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseLegacyPurposeRepairContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _state = F.decision()
  private val _result = F.result.copy(findings = Vector("First saved original-purpose blocker", "Second saved original-purpose blocker"))
  private val _issues = F.issues.copy(issues = _result.findings.zipWithIndex.map { (text, index) =>
    F.issue.copy(identity = "issue-" + (index + 3), summary = text)
  }, history = Vector.empty)
  private val _request = F.request(_state).copy(issues = Some(_issues))
  private val _findings = take(P.reviewFindingsC(_state, _request, _result, F.reference))
  private val _target = P.Target("isolated-session", "isolated-question", "20", "21", "actual-assignment", "19",
    F.reference, _result, _state.plan, _state.base, _state.completed, _state.generation,
    GoalPhasePurposeRepair.capture("ReviewStep", Some("current"), _state.plan).get, _state.candidate.get,
    _state.checksReceipt.get, GoalPhaseRepairProgress.snapshot(_state), _findings)
  private val _reference = V.WorkflowArtifactReference("actual-specification-human-direction", "1")
  private val _description = V.GoalPhaseReviewPurposeRepairDescriptionResult(_target.sessionId, "direction-required",
    P.message, "sm-workflow:specification", P.targetRecord(_target), P.admissionOperation)
  private val _input = V.GoalPhaseReviewPurposeRepairInput(_target.sessionId, _description.target,
    "SPECIFICATION-OWNED Human acknowledges every saved blocker as an original-purpose defect inside the unchanged Step scope",
    _reference, true)
  private val _execution = V.WorkflowExecutionEvidence(Vector(_reference), Some("participant"), None, None)
  private val _time = Instant.parse("2026-10-07T00:00:00Z")
  private val _payload = P.Payload(_target, ContextReference(_reference.identity, _reference.revision), "participant", _input.rationale, true)

  "Legacy whole-batch purpose continuation" should {
    "bind both saved OPEN review issues without reinterpreting the accepted verdict or adding a condition" in {
      Given("the original untyped decision-needed report and its two actual OPEN REVIEW issues at the unanswered Review Decision")
      When("the whole saved batch is selected and the explicitly separate ordinary repair transition is admitted")
      val selected = take(P.reviewFindingsC(_state, _request, _result, F.reference))
      val next = take(_state.continueLegacyPurposeRepairC(_reference.identity, P.repairDirective(_payload)))
      Then("the Plan conditions base committed prefix and original report stay exact while one ordinary repair is charged")
      selected shouldBe Vector(P.Finding(1, _result.findings(0), "issue-3"), P.Finding(2, _result.findings(1), "issue-4"))
      next.stage shouldBe O.Stage.Repair
      next.plan shouldBe _state.plan
      next.base shouldBe _state.base
      next.completed shouldBe _state.completed
      next.generation shouldBe _state.generation + 1
      GoalPhaseRepairProgress.snapshot(next) shouldBe GoalPhaseRepairProgress.Snapshot(4, 4, 10)
      next.repairDirective.get.findings shouldBe _result.findings
      _result.outcome shouldBe "decision-needed"
      _result.repairFindingSources shouldBe None
      _result.acceptance.get.conditions shouldBe empty
      _issues.issues.map(_.status) shouldBe Vector("OPEN", "OPEN")
      next.continueLegacyPurposeRepairC(_reference.identity, next.repairDirective.get).isSuccess shouldBe false
    }

    "retain maximum independent summary and rationale fields while continuing once with the exact summary reason and full directive context" in {
      Given("a valid 16384-byte original summary and an independently supplied valid 16384-byte Human rationale")
      val summary = "s" * 16384
      val rationale = "r" * 16384
      val original = _result.copy(summary = summary)
      val payload = _payload.copy(target = _target.copy(originalResult = original), rationale = rationale)
      When("the exact binding and storage round-trip preserve both fields and the retained projections continue the same Step")
      val binding = take(P.bindingC(_description.componentInstance, payload.target, rationale, payload.participant,
        payload.directive, true))
      val selected = P.selectBinding(binding)
      val wire = P.storageCodec.encode(P.LegacyPurposeRepair(payload))
      val decoded = P.storageCodec.decode(wire)
      val restored = decoded.toOption.get match {
        case P.LegacyPurposeRepair(value) => value
        case other => fail("Expected legacy purpose disposition, got " + other)
      }
      val directive = P.repairDirective(restored)
      val context = P.decisionContext(restored)
      val next = take(_state.continueLegacyPurposeRepairC(restored.directive.identity, directive))
      Then("the reason is the complete original summary and one charge preserves the original outcome and separate Human provenance")
      selected shouldBe Right(binding)
      binding.hcursor.downField("reviewPurposeRepair").downField("target").downField("originalResult")
        .get[String]("summary") shouldBe Right(summary)
      binding.hcursor.downField("reviewPurposeRepair").downField("directive").get[String]("rationale") shouldBe Right(rationale)
      decoded shouldBe Right(P.LegacyPurposeRepair(payload))
      restored.target.originalResult.summary shouldBe summary
      restored.rationale shouldBe rationale
      directive shouldBe O.RepairDirective(summary, _result.findings)
      next.stage shouldBe O.Stage.Repair
      next.repairDirective shouldBe Some(directive)
      GoalPhaseRepairProgress.snapshot(next) shouldBe GoalPhaseRepairProgress.Snapshot(4, 4, 10)
      context.requestingResult shouldBe original
      context.requestingResult.outcome shouldBe "decision-needed"
      context.choice shouldBe P.origin
      context.decisionReference shouldBe Some(payload.directive)
      context.rationale shouldBe rationale
    }

    "refuse skipped reordered foreign resolved duplicate or mismatched issues and stale controller bindings" in {
      Given("the same original saved report, exact assignment and candidate/check frontier")
      val requests = Vector(_request.copy(issues = Some(_issues.copy(issues = _issues.issues.take(1)))),
        _request.copy(issues = Some(_issues.copy(issues = _issues.issues.reverse))),
        _request.copy(issues = Some(_issues.copy(issues = _issues.issues.map(_.copy(status = "FIXED"))))),
        _request.copy(issues = Some(_issues.copy(issues = _issues.issues.map(_.copy(source = "TEST"))))),
        _request.copy(issues = Some(_issues.copy(issues = _issues.issues.map(_.copy(repository = "foreign"))))),
        _request.copy(issues = Some(_issues.copy(issues = _issues.issues.map(_.copy(step = "first"))))),
        _request.copy(issues = Some(_issues.copy(issues = _issues.issues.map(_.copy(evidenceReference = "other-review"))))),
        _request.copy(issues = Some(_issues.copy(issues = Vector(_issues.issues.head, _issues.issues.last.copy(identity = "issue-3"))))),
        _request.copy(generation = _request.generation + 1), _request.copy(candidate = Some(F.base)),
        _request.copy(checksReceipt = Some("other-checks")), _request.copy(purpose = "Different purpose"),
        _request.copy(originalBase = F.base.copy(tree = "e" * 40)), _request.copy(completed = Vector.empty),
        _request.copy(kind = "ReviewPhase"), _request.copy(review = None),
        _request.copy(purposeRepair = Some(_target.contract)))
      When("each incomplete or crossed batch attempts selection")
      val refused = requests.map(P.reviewFindingsC(_state, _, _result, F.reference)) ++ Vector(
        P.reviewFindingsC(_state, _request, _result.copy(findings = _result.findings.reverse), F.reference),
        P.reviewFindingsC(_state, _request, _result.copy(planRevision = Some(GoalPhasePlanRevision.Proposal("other", "1", "Other", F.proposal))), F.reference),
        P.reviewFindingsC(_state, _request, _result, F.reference.copy(identity = "other-review")))
      Then("every misuse rejects without a mutation or charge")
      refused.foreach(_.isSuccess shouldBe false)
      GoalPhaseRepairProgress.snapshot(_state) shouldBe GoalPhaseRepairProgress.Snapshot(3, 3, 10)
      val exhausted = F.decision(repairs = 10)
      P.reviewFindingsC(exhausted, F.request(exhausted).copy(issues = Some(_issues)), _result, F.reference).isSuccess shouldBe false
    }

    "preserve the old payload writer and explicitly dispatch exact new origins without fallback" in {
      Given("an existing saved RemainingPlanAdoption payload and the separately typed new Human purpose payload")
      val old = A.Payload(F.target, ContextReference("old-human-condition", "1"), "participant", "Original condition direction")
      val oldwire = A.codec.encode(old)
      val newwire = P.storageCodec.encode(P.LegacyPurposeRepair(_payload))
      When("the single closed storage-family codec reads and writes both explicit alternatives")
      val oldread = P.storageCodec.decode(oldwire)
      val newread = P.storageCodec.decode(newwire)
      Then("old native type identity bytes projection and business meaning remain unchanged")
      P.storageCodec.typeIdentity shouldBe A.codec.typeIdentity
      P.storageCodec.encode(P.ExistingRemainingPlan(old)) shouldBe oldwire
      oldread shouldBe Right(P.ExistingRemainingPlan(old))
      A.codec.decode(oldwire) shouldBe Right(old)
      oldwire.hcursor.downField("schemaVersion").focus shouldBe None
      newwire.hcursor.get[String]("schemaVersion") shouldBe Right(P.schema)
      newwire.hcursor.get[String]("origin") shouldBe Right(P.origin)
      newread shouldBe Right(P.LegacyPurposeRepair(_payload))
      A.codec.decode(newwire).isLeft shouldBe true
      val invalid = Vector(Json.Null, newwire.mapObject(_.remove("origin")), newwire.mapObject(_.remove("schemaVersion")),
        newwire.mapObject(_.add("origin", Json.Null)), newwire.mapObject(_.add("schemaVersion", Json.Null)),
        newwire.mapObject(_.add("schemaVersion", "unknown".asJson)), newwire.mapObject(_.add("origin", "remaining-plan".asJson)),
        newwire.mapObject(_.add("target", oldwire.hcursor.downField("target").focus.get)),
        oldwire.mapObject(_.add("origin", P.origin.asJson)), oldwire.mapObject(_.add("payload", Json.obj())),
        newwire.mapObject(_.add("payload", newwire.hcursor.downField("payload").focus.get.mapObject(_.add("unknown", true.asJson)))),
        newwire.mapObject(_.add("payload", newwire.hcursor.downField("payload").focus.get.mapObject(_.add("acknowledgeOriginalPurposeDefects", false.asJson)))))
      invalid.foreach(value => P.storageCodec.decode(value).isLeft shouldBe true)
    }

    "prepare only the actual full-batch acknowledged direction and bind all meaningful target fields independently" in {
      Given("a passive public description and a separately observed specification-owned Human directive")
      val prepared = take(NativeLegacyPurposeRepairPreparation.prepareC(_description, _input, _reference, _execution,
        "participant", "normal-development-host", _time.minusSeconds(60)))
      val evidence = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      val declared = NativeExternalEvidence.Declaration("workflow", DataStore.CollectionId.EntityStore(
        EntityCollectionId("test", "host", "purpose_evidence")), "normal-development-host")
      val purpose = NativeExternalEvidence.Purpose.ReviewPurposeRepair
      val key = ThreeWorkflowExternalEvidencePorts.key(_reference.identity, _reference.revision)
      val binding = take(P.bindingC(_description.componentInstance, _target, _input.rationale, "participant", _payload.directive, true))
      When("the independent provider observes the exact binding and separately crossed valid targets")
      val observed = NativeExternalEvidence.observeC(evidence, declared, purpose, key, binding, _time)
      Then("only the exact target and acknowledgment match; preparation grants no live authority")
      observed.isSuccess shouldBe true
      val targets = Vector(_target.copy(sessionId = "foreign-session"), _target.copy(generation = _target.generation + 1),
        _target.copy(candidate = _target.candidate.copy(tree = "f" * 40)), _target.copy(base = F.base.copy(tree = "e" * 40)),
        _target.copy(checksReceipt = "other-checks"), _target.copy(findings = _target.findings.updated(0, _target.findings.head.copy(issueIdentity = "issue-8"))))
      targets.foreach { target =>
        val crossed = take(P.bindingC(_description.componentInstance, target, _input.rationale, "participant", _payload.directive, true))
        NativeExternalEvidence.observeC(evidence, declared, purpose, key, crossed, _time).isSuccess shouldBe false
      }
      P.selectBinding(binding.mapObject(_.add("unobserved", true.asJson))).isLeft shouldBe true
      P.selectBinding(binding.hcursor.downField("reviewPurposeRepair").downField("directive")
        .downField("acknowledgeOriginalPurposeDefects").withFocus(_ => false.asJson).top.get).isLeft shouldBe true
      val inputs = Vector(_input.copy(acknowledgeOriginalPurposeDefects = false), _input.copy(sessionId = "foreign"),
        _input.copy(target = P.targetRecord(_target.copy(candidate = F.base))))
      inputs.foreach(input => NativeLegacyPurposeRepairPreparation.prepareC(_description, input, _reference, _execution,
        "participant", "normal-development-host", _time).isSuccess shouldBe false)
      NativeLegacyPurposeRepairPreparation.prepareC(_description, _input, _reference, _execution.copy(workerIdentity = Some("other")),
        "participant", "normal-development-host", _time).isSuccess shouldBe false
    }
  }
}
