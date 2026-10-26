package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.Json
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.processexecution.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseInitialPhaseCheckRepair as A
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeValidation as V
import GoalPhaseNativeWork as W
import GoalPhaseRuntimeRepairControl as R
import SplitPhaseExplicitPartitionFixture.take

/** Pure actual-shaped source, codec and canonical successor evidence. Fixtures
  * confer no real Session, command, Human or independent-review authority.
  * @since   Oct. 26, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseInitialPhaseCheckRepairSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private val _paths = Vector("value.txt")
  private val _checks = Vector(V.Check("phase-compile", 1, WorkflowSbtValidation.Kind.Compile),
    V.Check("phase-test", 2, WorkflowSbtValidation.Kind.Test))
  private val _plan = O.Plan("first-phase-check-plan", "1", "phase-1", "fixture-repository",
    Vector(O.Step("S1", "Complete only the selected input", _paths, Vector.empty, Vector("step-check"))),
    _checks.map(_.identity))
  private def _append(from: InstanceRecord, stage: String, cause: String): InstanceRecord =
    take(from.appendC(from.revision, GoalPhaseNativeRun.history(from, "phase." + stage, cause)))
  private def _initial: InstanceRecord = take(InstanceRecord(InstanceIdentity("first-phase-check-native"),
    take(GoalPhaseRuntimeCatalog.loadC()).definition, initialRevision, Lifecycle.NotStarted, None,
    CorrelationReference("first-phase-check-correlation"), CausationReference("first-phase-check-start"),
    None, None, None, Vector.empty).validateC)
  private def _key(state: O.State) = state.next.asInstanceOf[O.Semantic].key
  private def _phase(plan: O.Plan, policy: Option[String]): O.State = {
    val initial = take(O.startC(plan, _base, policy))
    val implementation = take(initial.acceptC(O.SemanticCompleted(_key(initial), "Fixture input completed")))
    val candidate = _base.copy(tree = "c" * 40)
    val inspected = take(implementation.acceptC(O.CandidateObserved(candidate)))
    val checked = take(inspected.acceptC(O.ChecksObserved(candidate, Vector("step-check"), "step-checks", true)))
    val reviewed = take(checked.acceptC(O.ReviewAdmitted(_key(checked), candidate, "step-checks", "step-review", O.ReviewOutcome.Accept)))
    val closing = take(reviewed.acceptC(O.ClosureIntentSaved(candidate, "step-closure")))
    val committed = take(closing.acceptC(O.StepCommitObserved("step-closure", O.Commit("d" * 40, _base.head, candidate.tree))))
    take(committed.acceptC(O.CandidateObserved(committed.tipSnapshot)))
  }
  private def _receipt(check: V.Check, passed: Boolean): RepositorySyncNativeMergeValidation.Receipt =
    RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(if (passed) 0 else 1),
      if (passed) "Actual-shaped selected success" else "Actual-shaped ordinary assertion failure", "",
      Some(WorkflowSbtValidation.Facts(check.kind,
        if (passed) WorkflowSbtValidation.Outcome.Succeeded else WorkflowSbtValidation.Outcome.Failed, None, None)))
  private final case class Fixture(from: InstanceRecord, controller: O.State,
    candidate: GoalPhaseNativeCandidate.Saved, validation: V.Saved) {
    def selectC = A.selectC(from, controller, Some(candidate), Some(validation), _checks)
  }
  private def _fixture(limit: Int = 10, policy: Option[String] = Some(R.version), prefix: Boolean = false,
    adoptBeforeFailure: Boolean = false): Fixture = {
    val beforepolicy = _phase(_plan.copy(repairLimit = limit), if (adoptBeforeFailure) None else policy)
    val phase = if (adoptBeforeFailure) take(beforepolicy.adoptRepairPolicyC("AdoptRuntimePolicy", "fixture-eligible-policy-direction"))
      else beforepolicy
    val inspection = _append(_initial, "PhaseInspection", "fixture-closed-Step")
    val candidaterecord = _append(inspection, "PhaseValidation", "ordered-candidate:candidate")
    val candidate = GoalPhaseNativeCandidate.Saved(candidaterecord, GoalPhaseNativeCandidate.Payload(
      inspection.revision.value.toString, "phase:" + phase.plan.phase, Vector.empty, Vector.empty,
      phase.candidate, _paths, Some("Actual-shaped complete original Phase diff")))
    val selected = if (prefix) _checks.take(1) else _checks
    val receipts = selected.zipWithIndex.map((check, index) => _receipt(check, index < selected.size - 1))
    val validationsource = if (adoptBeforeFailure) _append(candidaterecord, "PhaseValidation", "ordered-repair-policy-adoption:fixture")
      else candidaterecord
    val validationrecord = _append(validationsource, "Decision", "ordered-validation:validation")
    val validation = V.Saved(validationrecord, V.Payload(validationsource.revision.value.toString,
      candidate.payload.step, phase.candidate.get, selected, receipts, Some("failed")))
    val reference = validationrecord.identity.value + ":ordered-validation:" + validation.payload.sourceRevision + ":validation"
    val failed = take(V.observedStateC(phase, validation.payload, reference))
    Fixture(validationrecord, failed, candidate, validation)
  }
  private def _source(fixture: Fixture): A.Source = take(fixture.selectC).get
  private def _change(wire: Json, field: String, value: Json): Json = wire.mapObject(_.add("payload",
    wire.hcursor.downField("payload").focus.get.mapObject(_.add(field, value))))

  "First whole-Phase check repair admission" should {
    "reserve one ordinary Release batch after retaining the historical failed Decision exactly" in {
      Given("RRC-PHASE-FIRST-FAILURE-01: all selected Steps are closed and the first actual-shaped whole-Phase check failed")
      val fixture = _fixture()
      val source = _source(fixture)
      val original = fixture.validation.payload
      When("the native owner generates its exact payload and reconstructs the same admission twice")
      val payload = A.payload(source)
      val first = take(A.admittedStateC(source, payload))
      val reread = take(A.admittedStateC(source, payload))
      Then("each reconstruction has one charged batch while the old failure, accepted Plan and original base remain intact")
      fixture.controller.stage shouldBe O.Stage.Decision
      fixture.controller.repairs shouldBe 0
      fixture.controller.decisionSource shouldBe Some(O.Stage.PhaseValidation)
      original.outcome shouldBe Some("failed")
      fixture.validation.payload shouldBe original
      first.stage shouldBe O.Stage.Repair
      first.repairs shouldBe 1
      first.ordinaryRepairs shouldBe 1
      first.generation shouldBe fixture.controller.generation + 1
      first.plan shouldBe fixture.controller.plan
      first.base shouldBe _base
      first.completed shouldBe fixture.controller.completed
      first.candidate shouldBe None
      first.checksReceipt shouldBe None
      first.repairControl.get.observations shouldBe empty
      reread.repairs shouldBe first.repairs
      reread.generation shouldBe first.generation
      first.continueInitialPhaseCheckRepairC(payload.validationReference.identity, payload.directive).isSuccess shouldBe false
      source.reference.identity shouldBe fixture.from.identity.value + ":ordered-initial-phase-check-repair:" + fixture.from.revision.value
    }

    "admit a complete Phase diff larger than the identifier text bound without truncating it" in {
      Given("CB-RRC-DIFF-01: the actual-shaped first failed Phase retains a complete valid diff above 16384 UTF-8 bytes")
      val original = _fixture()
      val diff = "Actual-shaped complete original Phase diff\n" + ("x" * 16385)
      val fixture = original.copy(candidate = original.candidate.copy(payload = original.candidate.payload.copy(diff = Some(diff))))
      When("the owning candidate codec roundtrips the full payload and native selection admits its same-Plan repair")
      val roundtrip = GoalPhaseNativeCandidate.codec.decode(GoalPhaseNativeCandidate.codec.encode(fixture.candidate.payload))
      val source = _source(fixture)
      val admitted = take(A.admittedStateC(source, A.payload(source)))
      Then("the full original diff survives unchanged and exactly one ordinary repair batch is charged")
      diff.getBytes(UTF_8).length should be > 16384
      roundtrip shouldBe Right(fixture.candidate.payload)
      source.candidate.payload.diff shouldBe Some(diff)
      fixture.candidate.payload.diff shouldBe Some(diff)
      admitted.stage shouldBe O.Stage.Repair
      admitted.repairs shouldBe 1
      admitted.ordinaryRepairs shouldBe 1
      admitted.plan shouldBe original.controller.plan
      admitted.base shouldBe original.controller.base
      admitted.completed shouldBe original.controller.completed
      fixture.controller.repairs shouldBe 0
    }

    "accept the ordinary fail-fast prefix and policy genuinely eligible before the original failure" in {
      Given("the original first failed prefix and an actual-shaped policy adoption at the existing eligible PhaseValidation frontier")
      val prefix = _fixture(prefix = true)
      val adopted = _fixture(adoptBeforeFailure = true)
      When("native selection proves each actual historical candidate and failure prefix")
      val prefixsource = _source(prefix)
      val adoptedsource = _source(adopted)
      Then("both select the original failed source without adding policy-adoption eligibility to a no-policy failed Decision")
      A.payload(prefixsource).directive.reason should include ("phase-compile")
      A.payload(adoptedsource).sourceRevision shouldBe adopted.from.revision.value.toString
      adopted.controller.repairControl.get.policy shouldBe R.version
      adopted.validation.payload.outcome shouldBe Some("failed")
      _fixture(policy = None).controller.adoptRepairPolicyC("AdoptRuntimePolicy", "new-direction-after-failure").isSuccess shouldBe false
    }

    "leave no-policy exhausted timeout unknown incomplete and stopped cases outside ordinary first-check repair" in {
      Given("the original first failed Phase plus excluded native terminal and admission variants")
      val fixture = _fixture()
      val value = fixture.validation.payload
      val timeout = value.copy(receipts = value.receipts.updated(value.receipts.size - 1,
        value.receipts.last.copy(termination = "timed-out", exitCode = None,
          sbt = Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Test, WorkflowSbtValidation.Outcome.Incomplete, None, None)))))
      val raw = ProcessExecutionResult(ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr),
        ProcessExecutionCapture(Vector.empty, 0L, false), ProcessExecutionCapture(Vector('x'.toByte), 1L, true),
        Vector.empty, 100L, "actual-shaped-first-phase-check")
      val truncated = value.copy(outcome = Some("incomplete"), receipts = value.receipts.updated(value.receipts.size - 1,
        RepositorySyncNativeMergeValidation.Receipt(value.selected.last.identity, "output-limit-exceeded", None, "", "x",
          Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Test, WorkflowSbtValidation.Outcome.Incomplete, None, None)), Some(raw))))
      val stopped = value.copy(stopped = Vector(RepositorySyncNativeMergeValidation.Receipt(value.selected.head.identity,
        "not-started", None, "", "", Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Compile,
          WorkflowSbtValidation.Outcome.NotExecuted, None, None)))))
      val excluded = Vector(timeout, value.copy(outcome = None), value.copy(outcome = Some("incomplete")),
        truncated, stopped,
        value.copy(admissionFailures = Vector(V.AdmissionFailure(value.selected.last.identity, 0, "Actual admission failure"))),
        value.copy(uncertainties = Vector(V.Uncertainty(value.selected.last.identity, 0, "Actual unknown effect"))))
      When("ordinary selection sees no policy, no allowance or a nonordinary native result")
      val nopolicy = _fixture(policy = None).selectC
      val exhausted = _fixture(limit = 0).selectC
      val denied = excluded.map(payload => fixture.copy(validation = fixture.validation.copy(payload = payload)).selectC)
      Then("none issues or reserves a first-check repair, and malformed contradictory facts fail closed")
      take(nopolicy) shouldBe None
      take(exhausted) shouldBe None
      denied.foreach(result => result.toOption.forall(_.isEmpty) shouldBe true)
      fixture.controller.repairs shouldBe 0
    }

    "refuse crossed candidates foreign records invalid check coverage and a changed failed Decision identity" in {
      Given("one complete actual-shaped native prefix with its exact original candidate and failed identity")
      val fixture = _fixture()
      val crossed = fixture.copy(candidate = fixture.candidate.copy(payload = fixture.candidate.payload.copy(
        candidate = Some(fixture.validation.payload.candidate.copy(tree = "e" * 40)))))
      val foreign = fixture.copy(validation = fixture.validation.copy(record = fixture.validation.record.copy(identity = InstanceIdentity("foreign"))))
      val alteredchecks = fixture.validation.payload.copy(selected = Vector(V.Check("foreign", 1)),
        receipts = Vector(_receipt(V.Check("foreign", 1), false)))
      val otherphase = _phase(fixture.controller.plan, Some(R.version))
      val differentdecision = take(otherphase.acceptC(O.ChecksObserved(otherphase.candidate.get, otherphase.plan.phaseChecks, "different-failure", false)))
      When("selection receives contradictory native material or a payload no longer bound to the Decision")
      val results = Vector(crossed.selectC, foreign.selectC,
        fixture.copy(validation = fixture.validation.copy(payload = alteredchecks)).selectC,
        fixture.copy(controller = differentdecision).selectC)
      Then("every contradictory proof fails explicitly")
      results.foreach(_.isSuccess shouldBe false)
    }

    "roundtrip the exact closed receipt and reject unknown missing null nested and counter changes" in {
      Given("the payload generated solely from the selected original failure")
      val source = _source(_fixture())
      val payload = A.payload(source)
      val wire = A.codec.encode(payload)
      val fields = wire.hcursor.downField("payload").focus.get.asObject.get.keys.toVector
      val unknown = wire.mapObject(_.add("unknown", Json.True))
      val nested = _change(wire, "candidate", wire.hcursor.downField("payload").downField("candidate").focus.get
        .mapObject(_.add("unknown", Json.True)))
      val invalid = Vector(unknown, nested, _change(wire, "unknown", Json.True),
        _change(wire, "automaticCount", Json.fromInt(1)), _change(wire, "generation", Json.fromLong(Long.MaxValue)),
        _change(wire, "policy", Json.fromString("unknown-policy"))) ++ fields.flatMap { field =>
        Vector(_change(wire, field, Json.Null), wire.mapObject(_.add("payload",
          wire.hcursor.downField("payload").focus.get.mapObject(_.remove(field)))))
      }
      When("the strict receipt codec reads the valid and malformed wires")
      val decoded = A.codec.decode(wire)
      val rejected = invalid.map(A.codec.decode)
      Then("only the complete exact non-null receipt survives")
      decoded shouldBe Right(payload)
      rejected.foreach(_.isLeft shouldBe true)
      A.admittedStateC(source, payload.copy(directive = payload.directive.copy(reason = "Changed assigned reason"))).isSuccess shouldBe false
    }

    "require the exact adjacent full native successor and the original source revision CAS" in {
      Given("the actual-shaped Decision source and its canonical pure CML admission edge")
      val fixture = _fixture()
      val source = _source(fixture)
      val admitted = _append(fixture.from, "Repair", A.causation)
      val extra = _append(admitted, "Repair", "another-transition")
      val changed = admitted.copy(causation = CausationReference("changed-admission"))
      When("the owner checks the full successor and a stale caller attempts to reuse its old source revision")
      val successor = A.successorC(fixture.from, admitted)
      val stale = admitted.appendC(fixture.from.revision, GoalPhaseNativeRun.history(admitted, "phase.Repair", A.causation))
      Then("only the canonical adjacent admission passes and the stable reference selects that one source")
      successor.isSuccess shouldBe true
      A.successorC(fixture.from, extra).isSuccess shouldBe false
      A.successorC(fixture.from, changed).isSuccess shouldBe false
      stale.isSuccess shouldBe false
      source.reference.revision shouldBe "1"
      take(GoalPhaseNativeHistory.prefixC(admitted, fixture.from.revision)) shouldBe fixture.from
    }

    "carry the actual admission in a no-review Release request and retain legacy check-context absence" in {
      Given("one admitted first-check repair and its original actual-shaped failed candidate diagnostics")
      val fixture = _fixture()
      val source = _source(fixture)
      val payload = A.payload(source)
      val state = take(A.admittedStateC(source, payload))
      val record = _append(fixture.from, "Repair", A.causation)
      val saved = A.Saved(record, payload, state)
      val failure = take(W.checkFailureContextC(_paths, fixture.candidate.payload.diff.get, fixture.validation.payload, Some(saved.reference)))
      val semantic = state.next.asInstanceOf[O.Semantic]
      val request = W.Request(record.identity.value, record.revision.value.toString, "RepairStep", None,
        state.generation, "Repair the first whole-Phase checks", state.plan, state.base, state.completed,
        None, None, semantic.repairDirective, checkFailure = Some(failure))
      val oldpayload = fixture.validation.payload.copy(step = "S1", outcome = Some("repair-required"))
      val oldcontext = take(W.checkFailureContextC(_paths, "Actual old Step diff", oldpayload))
      val legacy = request.copy(stepIdentity = Some("S1"), checkFailure = Some(oldcontext))
      When("the ordinary request/result gates and public projection receive the marked Release and old unmarked Step contexts")
      val result = W.Result("completed", "Fixture-owned semantic correction", Vector.empty)
      val admitted = W.admitSemanticResultC(request, result)
      val wire = W.inputCodec.encode(request)
      val oldwire = W.inputCodec.encode(legacy)
      val publicwork = NativeWorkflowOperationOutput.goalWork(request)
      Then("the exact native reference is public, no PhaseReview is manufactured, and only marked failed Phase context admits no-review Release repair")
      admitted.isSuccess shouldBe true
      W.inputCodec.decode(wire) shouldBe Right(request)
      publicwork.review shouldBe None
      publicwork.checkFailure.get.initialPhaseRepair.get.identity shouldBe saved.reference.identity
      publicwork.checkFailure.get.initialPhaseRepair.get.revision shouldBe "1"
      W.admitSemanticResultC(request.copy(checkFailure = Some(failure.copy(initialPhaseRepair = None))), result).isSuccess shouldBe false
      W.admitSemanticResultC(request.copy(completed = Vector.empty), result).isSuccess shouldBe false
      W.admitSemanticResultC(request.copy(checkFailure = Some(failure.copy(validation = oldpayload))), result).isSuccess shouldBe false
      oldwire.hcursor.downField("payload").downField("checkFailure").downField("initialPhaseRepair").focus shouldBe None
      W.inputCodec.decode(oldwire) shouldBe Right(legacy)
      val nullmarker = wire.mapObject(_.add("payload", wire.hcursor.downField("payload").focus.get.mapObject(_.add("checkFailure",
        wire.hcursor.downField("payload").downField("checkFailure").focus.get.mapObject(_.add("initialPhaseRepair", Json.Null))))))
      W.inputCodec.decode(nullmarker).isLeft shouldBe true
    }

    "retain initial provenance across a second fresh failed check and require the first real PhaseReview after success" in {
      Given("an admitted first ordinary Phase failure, its original native reference and one completed semantic repair")
      val fixture = _fixture()
      val source = _source(fixture)
      val payload = A.payload(source)
      val first = take(A.admittedStateC(source, payload))
      val admissionrecord = _append(fixture.from, "Repair", A.causation)
      val provenance = A.Saved(admissionrecord, payload, first).reference
      val implemented = take(first.acceptC(O.SemanticCompleted(_key(first), "First actual-shaped semantic repair")))
      val candidate = fixture.validation.payload.candidate.copy(tree = "e" * 40)
      val inspected = take(implemented.acceptC(O.CandidateObserved(candidate)))
      val failed = fixture.validation.payload.copy(sourceRevision = "20", candidate = candidate, outcome = Some("repair-required"))
      When("normal active Release validation observes another ordinary failure before any integrated review")
      val second = take(V.observedStateC(inspected, failed, "second-actual-shaped-phase-failure"))
      val context = take(W.checkFailureContextC(_paths, "Actual-shaped second repair delta", failed, Some(provenance)))
      val request = W.Request(admissionrecord.identity.value, "22", "RepairStep", None, second.generation,
        "Repair the ordinary pre-first-review check chain", second.plan, second.base, second.completed,
        None, None, second.repairDirective, checkFailure = Some(context))
      val result = W.Result("completed", "Second actual-shaped semantic correction", Vector.empty)
      Then("the original admission supports current failed diagnostics while the ordinary budget charges exactly two batches")
      second.stage shouldBe O.Stage.Repair
      second.repairs shouldBe 2
      second.ordinaryRepairs shouldBe 2
      second.completed shouldBe first.completed
      second.plan shouldBe first.plan
      second.repairObservations shouldBe empty
      context.initialPhaseRepair shouldBe Some(provenance)
      context.validation shouldBe failed
      W.admitSemanticResultC(request, result).isSuccess shouldBe true
      W.inputCodec.decode(W.inputCodec.encode(request)) shouldBe Right(request)
      Given("the second repair completes and its fresh selected Phase checks really pass")
      val completed = take(second.acceptC(O.SemanticCompleted(_key(second), result.summary)))
      val observed = take(completed.acceptC(O.CandidateObserved(candidate.copy(tree = "f" * 40))))
      val passed = failed.copy(candidate = observed.candidate.get,
        receipts = _checks.map(check => _receipt(check, true)), outcome = Some("passed"))
      When("the native validation owner interprets those fresh complete passed receipts")
      val reviewed = take(V.observedStateC(observed, passed, "fresh-passed-phase-checks"))
      Then("the first actual integrated review remains assigned independently and no acceptance is manufactured")
      reviewed.stage shouldBe O.Stage.PhaseReview
      reviewed.next.asInstanceOf[O.Semantic].key.kind shouldBe O.WorkKind.ReviewPhase
      reviewed.reviewEvidence shouldBe None
      reviewed.repairs shouldBe 2
      reviewed.completed shouldBe fixture.controller.completed
    }

    "the exact purpose of the first independent Phase review" which {
      "admits the canonical issued request after fresh successful checks and rejects crossed request fields" in {
        Given("SBR-RRC-FIRST-REVIEW-PURPOSE-02: an actual-shaped first failed Phase admitted to one ordinary repair batch")
        val fixture = _fixture()
        val source = _source(fixture)
        val first = take(A.admittedStateC(source, A.payload(source)))
        val completed = take(first.acceptC(O.SemanticCompleted(_key(first), "Completed the first selected semantic repair")))
        val candidate = fixture.validation.payload.candidate.copy(tree = "e" * 40)
        val observed = take(completed.acceptC(O.CandidateObserved(candidate)))
        val passed = fixture.validation.payload.copy(sourceRevision = "20", candidate = candidate,
          receipts = _checks.map(check => _receipt(check, true)), outcome = Some("passed"))
        When("the native validation owner interprets the fresh complete successful Phase receipts")
        val reviewed = take(V.observedStateC(observed, passed, "fresh-first-phase-review-checks"))
        Then("the assigned first Phase review retains its exact Plan, base and completed Steps without a prior review")
        reviewed.stage shouldBe O.Stage.PhaseReview
        reviewed.reviewEvidence shouldBe None
        reviewed.plan shouldBe fixture.controller.plan
        reviewed.base shouldBe fixture.controller.base
        reviewed.completed shouldBe fixture.controller.completed

        Given("that exact controller frontier and its actual-shaped review context with no prior reports")
        val semantic = reviewed.next.asInstanceOf[O.Semantic]
        val context = GoalPhaseNativeReview.Context(_paths, "Actual-shaped first-check repair delta", passed.receipts)
        val purpose = GoalPhaseNativeReview.requestPurpose(semantic, Some(context))
        val request = W.Request(fixture.from.identity.value, "22", semantic.key.kind.toString, semantic.key.step,
          reviewed.generation, purpose, reviewed.plan, reviewed.base, reviewed.completed,
          semantic.candidate, semantic.checksReceipt, semantic.repairDirective, review = Some(context),
          releaseRepairContract = Some(W.RELEASE_REPAIR_CONTRACT))
        val result = W.Result("accept", "The whole selected Phase and first-check repair delta satisfy the selected contract", Vector.empty)
        val crossed = Vector(request.copy(purpose = purpose + " altered"), request.copy(purpose = semantic.purpose),
          request.copy(candidate = Some(candidate.copy(tree = "f" * 40))),
          request.copy(checksReceipt = Some("different-phase-checks")), request.copy(generation = reviewed.generation + 1))
        When("native review completion admits the ordinary accept result against the issued request and each crossed variant")
        val closed = take(GoalPhaseNativeReview.completedRequestC(request, result, "first-actual-phase-review", reviewed, Some(context)))
        val rejected = crossed.map(value => GoalPhaseNativeReview.completedRequestC(value, result, "crossed-phase-review", reviewed, Some(context)))
        Then("only the exact canonical purpose reaches PhaseClosing while every crossed purpose or target is refused")
        purpose shouldBe "Independently review the whole selected Phase and first-check repair delta; no prior Phase review exists."
        purpose should not be semantic.purpose
        context.priorReviews shouldBe empty
        request.review.get.priorReviews shouldBe empty
        semantic.key.kind shouldBe O.WorkKind.ReviewPhase
        semantic.repairDirective should not be empty
        closed.stage shouldBe O.Stage.PhaseClosing
        closed.plan shouldBe fixture.controller.plan
        closed.base shouldBe fixture.controller.base
        closed.completed shouldBe fixture.controller.completed
        closed.candidate shouldBe reviewed.candidate
        closed.checksReceipt shouldBe reviewed.checksReceipt
        closed.generation shouldBe reviewed.generation
        closed.repairs shouldBe reviewed.repairs
        rejected.foreach(_.isSuccess shouldBe false)
      }

      "uses reconstructed prior reports for both historical empty-prior and current integrated re-review requests" in {
        Given("CB-RRC-PURPOSE-CONTEXT-04: an actual-shaped completed Plan with an integrated Phase repair report and fresh successful rechecks")
        val phase = _phase(_plan, Some(R.version))
        val original = take(phase.acceptC(O.ChecksObserved(phase.candidate.get, phase.plan.phaseChecks, "original-integrated-checks", true)))
        val report = W.Result("repair", "Correct the selected integrated Phase defect", Vector("Correct the selected Phase input"))
        val prior = GoalPhaseNativeReview.Prior("ReviewPhase", None, original.generation, original.candidate.get,
          original.checksReceipt.get, "actual-shaped-integrated-review", "1", report)
        val repair = take(original.acceptC(O.ReviewAdmitted(_key(original), original.candidate.get,
          original.checksReceipt.get, prior.resultIdentity, O.ReviewOutcome.Repair,
          Some(O.RepairDirective(report.summary, report.findings)), allowPhaseRepair = true)))
        val completed = take(repair.acceptC(O.SemanticCompleted(_key(repair), "Completed the selected integrated Phase correction")))
        val candidate = original.candidate.get.copy(tree = "e" * 40)
        val observed = take(completed.acceptC(O.CandidateObserved(candidate)))
        val passed = V.Payload("20", "phase:" + observed.plan.phase, candidate, _checks,
          _checks.map(check => _receipt(check, true)), Some("passed"))
        val reviewed = take(V.observedStateC(observed, passed, "fresh-integrated-phase-checks"))
        val semantic = reviewed.next.asInstanceOf[O.Semantic]
        val context = GoalPhaseNativeReview.Context(_paths, "Actual-shaped integrated Phase repair delta", passed.receipts,
          priorReviews = Vector(prior))
        val historical = context.copy(priorReviews = Vector.empty)
        val purpose = GoalPhaseNativeReview.requestPurpose(semantic, Some(context))
        val request = W.Request("actual-shaped-integrated-session", "22", semantic.key.kind.toString, semantic.key.step,
          reviewed.generation, purpose, reviewed.plan, reviewed.base, reviewed.completed,
          semantic.candidate, semantic.checksReceipt, semantic.repairDirective, review = Some(historical),
          releaseRepairContract = Some(W.RELEASE_REPAIR_CONTRACT))
        val result = W.Result("accept", "The integrated Phase correction satisfies the selected contract", Vector.empty)
        val firstpurpose = GoalPhaseNativeReview.requestPurpose(semantic, Some(historical))
        val crossed = Vector(request.copy(purpose = firstpurpose), request.copy(purpose = purpose + " altered"),
          request.copy(candidate = Some(candidate.copy(tree = "f" * 40))),
          request.copy(checksReceipt = Some("different-integrated-checks")), request.copy(generation = reviewed.generation + 1),
          request.copy(review = Some(historical.copy(diff = "Changed retained diff"))),
          request.copy(review = Some(historical.copy(changedPaths = Vector("foreign.txt")))),
          request.copy(review = Some(historical.copy(checks = Vector.empty))))
        When("the pure native admission interprets saved historical and full current requests against the same reconstructed context")
        val compatible = GoalPhaseNativeReview.sameIssuedContext(request.review, Some(context))
        val full = request.copy(review = Some(context))
        val currentcompatible = GoalPhaseNativeReview.sameIssuedContext(full.review, Some(context))
        val closed = Vector(request, full).map(value => take(GoalPhaseNativeReview.completedRequestC(value,
          result, "actual-shaped-integrated-rereview", reviewed, Some(context))))
        val rejected = crossed.map(value => GoalPhaseNativeReview.completedRequestC(value,
          result, "crossed-integrated-rereview", reviewed, Some(context)))
        Then("both authenticated context shapes reach PhaseClosing while first-review reinterpretation and every crossed request are refused")
        compatible shouldBe true
        currentcompatible shouldBe true
        purpose shouldBe semantic.purpose
        purpose shouldBe "Review the unresolved Release findings, repair delta and affected meaning; retain the prior integrated Phase review"
        firstpurpose shouldBe "Independently review the whole selected Phase and first-check repair delta; no prior Phase review exists."
        request.review.get.priorReviews shouldBe empty
        context.priorReviews shouldBe Vector(prior)
        prior.result shouldBe report
        semantic.key.kind shouldBe O.WorkKind.ReviewPhase
        semantic.repairDirective should not be empty
        closed.foreach { state =>
          state.stage shouldBe O.Stage.PhaseClosing
          state.plan shouldBe original.plan
          state.base shouldBe original.base
          state.completed shouldBe original.completed
          state.candidate shouldBe reviewed.candidate
          state.checksReceipt shouldBe reviewed.checksReceipt
          state.generation shouldBe reviewed.generation
          state.repairs shouldBe reviewed.repairs
          state.ordinaryRepairs shouldBe reviewed.ordinaryRepairs
        }
        rejected.foreach(_.isSuccess shouldBe false)
      }

      "retains the ordinary no-repair Phase review purpose" in {
        Given("the completed selected Steps and ordinary Phase checks without any repair directive")
        val phase = _phase(_plan, Some(R.version))
        val reviewed = take(phase.acceptC(O.ChecksObserved(phase.candidate.get, phase.plan.phaseChecks, "ordinary-phase-checks", true)))
        val semantic = reviewed.next.asInstanceOf[O.Semantic]
        val context = GoalPhaseNativeReview.Context(_paths, "Actual-shaped ordinary whole-Phase diff", _checks.map(check => _receipt(check, true)))
        When("the native owner projects the ordinary purpose with and without optional review context")
        val withcontext = GoalPhaseNativeReview.requestPurpose(semantic, Some(context))
        val withoutcontext = GoalPhaseNativeReview.requestPurpose(semantic, None)
        Then("the existing ordinary controller purpose survives exactly")
        semantic.key.kind shouldBe O.WorkKind.ReviewPhase
        semantic.repairDirective shouldBe None
        semantic.purpose shouldBe "Review the whole selected Phase"
        withcontext shouldBe semantic.purpose
        withoutcontext shouldBe semantic.purpose
        context.priorReviews shouldBe empty
      }
    }

    "stop the pre-first-review chain at its real selected allowance and retain timeout as Decision" in {
      Given("one initially admitted Release batch under an explicitly selected allowance of one")
      val fixture = _fixture(limit = 1)
      val source = _source(fixture)
      val first = take(A.admittedStateC(source, A.payload(source)))
      val completed = take(first.acceptC(O.SemanticCompleted(_key(first), "Completed the selected one-batch repair")))
      val inspected = take(completed.acceptC(O.CandidateObserved(fixture.validation.payload.candidate.copy(tree = "e" * 40))))
      val failed = fixture.validation.payload.copy(sourceRevision = "20", candidate = inspected.candidate.get,
        outcome = Some("repair-limit-reached"))
      val timeout = failed.copy(outcome = Some("failed"), receipts = failed.receipts.updated(failed.receipts.size - 1,
        failed.receipts.last.copy(termination = "timed-out", exitCode = None,
          sbt = Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Test, WorkflowSbtValidation.Outcome.Incomplete, None, None)))))
      When("fresh validation reports either the next real ordinary failure or a confirmed timeout")
      val exhausted = take(V.observedStateC(inspected, failed, "exhausted-phase-checks"))
      val timedout = take(V.observedStateC(inspected, timeout, "actual-timeout-phase-checks"))
      Then("both retain Decision without reserving another batch or reentering first-check admission")
      exhausted.stage shouldBe O.Stage.Decision
      exhausted.repairs shouldBe 1
      exhausted.pendingRepair.nonEmpty shouldBe true
      exhausted.next.isInstanceOf[O.Decision] shouldBe true
      timedout.stage shouldBe O.Stage.Decision
      timedout.repairs shouldBe 1
      timedout.pendingRepair shouldBe None
      take(A.selectC(fixture.from, exhausted, None, None, _checks)) shouldBe None
      take(A.selectC(fixture.from, timedout, None, None, _checks)) shouldBe None
      A.repairContextValid(timeout) shouldBe false
    }
  }
}
