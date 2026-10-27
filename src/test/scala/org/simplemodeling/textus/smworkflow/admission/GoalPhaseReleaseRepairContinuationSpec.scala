package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, Decoder}
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.protocol.{Property, Request}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.NativeReleaseRepairPreparation
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as W
import GoalPhaseReleaseRepairContinuation as P
import GoalPhaseAcceptanceBoundary as B
import GoalPhaseLegacyPurposeRepairContinuation as L
import SplitPhaseExplicitPartitionFixture.take

/** Saved Phase review provenance and independently admitted direction only.
  * Actual old/new installed history and effects are qualified separately.
  * @since   Oct. 25, 2026
  * @version Oct. 27, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseReleaseRepairContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private val _plan = take(B.captureC(None, O.Plan("release", "1", "P65", "fixture", Vector(
    O.Step("one", "Implement the input and its usage contract", Vector("value.txt", "docs"), Vector.empty,
      Vector("focused"))), Vector("whole"), 10, acceptance = Some(B.Adoption("Exact input contract", Vector(
    B.Condition("usage-contract", "phase", true, "Describe the accepted input")))))))
  private def _key(state: O.State) = state.next.asInstanceOf[O.Semantic].key
  private def _phase_for(plan: O.Plan): O.State = {
    val initial = take(O.startC(plan, _base))
    val implemented = take(initial.acceptC(O.SemanticCompleted(_key(initial), "Implemented")))
    val candidate = O.Snapshot(_base.head, "c" * 40)
    val inspected = take(implemented.acceptC(O.CandidateObserved(candidate)))
    val checked = take(inspected.acceptC(O.ChecksObserved(candidate, Vector("focused"), "step-checks", true)))
    val reviewed = take(checked.acceptC(O.ReviewAdmitted(_key(checked), candidate, "step-checks", "step-review", O.ReviewOutcome.Accept)))
    val intent = take(reviewed.acceptC(O.ClosureIntentSaved(candidate, "step-intent")))
    val closed = take(intent.acceptC(O.StepCommitObserved("step-intent", O.Commit("d" * 40, _base.head, candidate.tree))))
    val seen = take(closed.acceptC(O.CandidateObserved(closed.tipSnapshot)))
    take(seen.acceptC(O.ChecksObserved(closed.tipSnapshot, Vector("whole"), "phase-checks", true)))
  }
  private val _phase = _phase_for(_plan)
  private val _reference = ContextReference("actual-phase-review", "2")
  private val _text = "docs/usage.md must describe the exact accepted input"
  private val _result = W.Result("repair", "Original integrated review", Vector(_text),
    Some(B.Report(Vector(B.Observation("usage-contract", "unsatisfied", _text)), Vector.empty)))
  private val _issues = GoalPhaseFixIssues.State(Vector(GoalPhaseFixIssues.Issue("issue-1", _plan.repository,
    "phase", "REVIEW", None, _text, _reference.identity, "OPEN", None)), Vector.empty, None)
  private val _request = W.Request("fixture-session", "18", "ReviewPhase", None, _phase.generation,
    _phase.next.asInstanceOf[O.Semantic].purpose, _plan, _base, _phase.completed, _phase.candidate,
    _phase.checksReceipt, None, review = Some(GoalPhaseNativeReview.Context(Vector("value.txt", "docs/usage.md"),
      "Actual integrated diff", Vector(RepositorySyncNativeMergeValidation.Receipt("whole", "Exited(0)", Some(0), "Passed", "")))),
    issues = Some(_issues))
  private val _decision = take(GoalPhaseNativeReview.completedRequestC(_request, _result, _reference.identity, _phase, _request.review))
  private val _host = P.Host("/specification/repository", "main", Vector("value.txt", "docs"), "phase-policy", 1,
    Vector(GoalPhaseNativeValidation.Check("whole", 1)), "closure-policy", 1,
    Vector(P.Program("one", 1), P.Program("phase-release", 2)))
  private val _target = P.Target("fixture-session", "fixture-question", "20", "21", "actual-assignment", "18",
    _reference, W.inputCodec.encode(_request), _result, _plan, _base, _phase.completed, _phase.generation,
    _phase.candidate.get, _phase.checksReceipt.get, GoalPhaseRepairProgress.snapshot(_phase), _issues,
    Vector(P.Finding(1, "usage-contract", _text, "issue-1")), _host)
  private val _directive = ContextReference("specification-owned-human", "1")
  private val _payload = P.Payload(_target, "ContinueReleaseRepair", _directive, "participant",
    "SPECIFICATION-OWNED Human directs this complete saved release blocker batch")

  "Captured Release repair contract" should {
    "keep absent legacy bytes and admit only the supported new Phase marker" in {
      Given("the original marker-absent ReviewPhase assignment and a genuinely new supported assignment")
      val marked = _request.copy(releaseRepairContract = Some(W.RELEASE_REPAIR_CONTRACT))
      When("their owning Request envelopes are encoded and decoded")
      val legacy = W.inputCodec.encode(_request)
      val current = W.inputCodec.encode(marked)
      Then("absence stays absent and both retain their exact original contract")
      legacy.hcursor.downField("payload").downField("releaseRepairContract").focus shouldBe None
      W.inputCodec.decode(legacy) shouldBe Right(_request)
      W.inputCodec.decode(current) shouldBe Right(marked)
      val invalid = Vector(current.hcursor.downField("payload").downField("releaseRepairContract").withFocus(_ => Json.Null).top.get,
        W.inputCodec.encode(marked.copy(releaseRepairContract = Some("unsupported"))),
        W.inputCodec.encode(marked.copy(kind = "ReviewStep", stepIdentity = Some("one"))))
      invalid.foreach(value => W.inputCodec.decode(value).isLeft shouldBe true)
    }

    "preserve the old Phase Decision and route only a captured new review into ordinary Release repair" in {
      Given("the same actual integrated required-condition repair verdict and successful native checks")
      When("the native owner admits the old and newly marked actual requests")
      val legacy = take(GoalPhaseNativeReview.completedRequestC(_request, _result, _reference.identity, _phase, _request.review))
      val current = take(GoalPhaseNativeReview.completedRequestC(_request.copy(releaseRepairContract = Some(W.RELEASE_REPAIR_CONTRACT)),
        _result, _reference.identity, _phase, _request.review))
      Then("history retains Decision while the new contract issues one phase-level RepairStep")
      legacy.stage shouldBe O.Stage.Decision
      legacy.decisionSource shouldBe Some(O.Stage.PhaseReview)
      current.stage shouldBe O.Stage.Repair
      current.next.asInstanceOf[O.Semantic].key.step shouldBe None
      current.plan shouldBe legacy.plan
      current.completed shouldBe legacy.completed
      current.ordinaryRepairs shouldBe legacy.ordinaryRepairs + 1
      take(GoalPhaseNativeReview.completedStateC(_result, _reference.identity, _phase)).stage shouldBe O.Stage.Decision
    }
  }

  "Exact saved Release continuation" should {
    "assign the original integrated REVIEW blocker with exactly the actual fresh failed Phase TEST issue" in {
      Given("a Release correction whose fresh selected Phase compile fails after the successful integrated review checks")
      val kind = WorkflowSbtValidation.Kind.Compile
      val failedreceipt = RepositorySyncNativeMergeValidation.Receipt("whole", "exited", Some(1), "Fresh compile failed", "Actual diagnostic",
        Some(WorkflowSbtValidation.Facts(kind, WorkflowSbtValidation.Outcome.Failed, None, None)))
      val failure = GoalPhaseNativeValidation.Payload("30", "phase:" + _plan.phase, _phase.candidate.get,
        Vector(GoalPhaseNativeValidation.Check("whole", 1, kind)), Vector(failedreceipt), Some("repair-required"))
      val actualstate = take(GoalPhaseFixIssues.validationC(_request.instanceIdentity, _plan.repository, Some(_issues),
        failure, "fresh-failed-phase-check")).get
      val testissue = actualstate.issues.find(_.source == "TEST").get
      val prior = GoalPhaseNativeReview.Prior("ReviewPhase", None, _request.generation, _request.candidate.get,
        _request.checksReceipt.get, _reference.identity, _reference.revision, _result)
      val context = _request.review.get.copy(priorReviews = Vector(prior))
      val repair = _request.copy(kind = "RepairStep", generation = _request.generation + 2, candidate = None,
        checksReceipt = None, repairDirective = Some(O.RepairDirective("Fresh Phase check failed", Vector("Correct the actual compile failure"))),
        review = Some(context), checkFailure = Some(take(W.checkFailureContextC(Vector("value.txt"), "Fresh failed candidate diff", failure))),
        issues = Some(actualstate))
      val unrelated = actualstate.copy(issues = actualstate.issues ++ Vector(
        testissue.copy(identity = "issue-3", repository = "foreign"),
        testissue.copy(identity = "issue-4", step = "closed-step"),
        testissue.copy(identity = "issue-5", sourceIdentity = Some("test:whole")),
        testissue.copy(identity = "issue-6", status = "RESOLVED"),
        _issues.issues.head.copy(identity = "issue-7", evidenceReference = "unrelated-review")))
      When("the actual request selects its exact issue batch and submits legitimate FIXED dispositions")
      val assigned = GoalPhaseFixIssues.assigned(repair.copy(issues = Some(unrelated)))
      val fixed = W.Result("completed", "Corrected original review and actual failed compile", Vector.empty,
        acceptance = Some(B.Report(B.applicable(_plan.effectiveAcceptance.get, "RepairStep").map(condition =>
          B.Observation(condition.identity, "satisfied", "Corrected the exact assigned condition")), Vector.empty)),
        issueReport = Some(GoalPhaseFixIssues.Report(Vector(_issues.issues.head, testissue).map(issue =>
          GoalPhaseFixIssues.Disposition(issue.identity, "FIXED", "Corrected the exact assigned issue", None)))))
      val corrected = take(GoalPhaseFixIssues.completedC(repair, fixed, "second-release-correction"))
      Then("historical success and fresh failure remain distinct while unrelated resolved and foreign issues stay unassigned")
      assigned.map(_.identity) shouldBe Vector(_issues.issues.head.identity, testissue.identity)
      context.checks.head.exitCode shouldBe Some(0)
      repair.checkFailure.get.validation.receipts.head.exitCode shouldBe Some(1)
      context.priorReviews.last.result shouldBe _result
      repair.repairDirective.get.findings should not contain (_text)
      When("the combined assigned repair and its exact owning Request round-trip cross ordinary semantic admission")
      val decoded = W.inputCodec.decode(W.inputCodec.encode(repair))
      val admitted = W.admitSemanticResultC(repair, fixed)
      val readmitted = decoded.map(value => W.admitSemanticResultC(value, fixed).isSuccess)
      Then("both the issued and round-tripped request admit the same legitimate semantic correction")
      decoded shouldBe Right(repair)
      admitted.isSuccess shouldBe true
      readmitted shouldBe Right(true)

      Given("combined contexts with crossed Step or Phase scope missing prior repair or invalid failed check facts")
      val checkcontext = repair.checkFailure.get
      val incomplete = failedreceipt.copy(termination = "timed-out", exitCode = None,
        sbt = Some(WorkflowSbtValidation.Facts(kind, WorkflowSbtValidation.Outcome.Incomplete, None, None)))
      val invalidrequests = Vector(repair.copy(stepIdentity = Some("one")),
        repair.copy(checkFailure = Some(checkcontext.copy(validation = failure.copy(step = "phase:other")))),
        repair.copy(review = Some(context.copy(priorReviews = Vector.empty))),
        repair.copy(review = Some(context.copy(priorReviews = Vector(prior.copy(result = _result.copy(outcome = "accept", findings = Vector.empty)))))),
        repair.copy(review = Some(context.copy(priorReviews = Vector(prior.copy(resultIdentity = ""))))),
        repair.copy(checkFailure = Some(checkcontext.copy(diff = ""))),
        repair.copy(checkFailure = Some(checkcontext.copy(validation = failure.copy(receipts = Vector(incomplete))))),
        repair.copy(checkFailure = Some(checkcontext.copy(validation = failure.copy(outcome = Some("unknown"))))),
        repair.copy(candidate = _phase.candidate), repair.copy(checksReceipt = _phase.checksReceipt))
      val invalidresults = Vector(fixed.copy(outcome = "accept"), fixed.copy(outcome = "passed"), fixed.copy(outcome = "closed"))
      When("ordinary semantic admission receives crossed sources or injected review check and closure outcomes")
      val refused = invalidrequests.map(W.admitSemanticResultC(_, fixed)) ++ invalidresults.map(W.admitSemanticResultC(repair, _))
      Then("neither combined context nor a retained review grants other scope check success review or closure")
      refused.zipWithIndex.foreach { (value, index) =>
        withClue(s"combined Release admission negative index=$index: ") { value.isSuccess shouldBe false }
      }
      GoalPhaseFixIssues.completedC(repair, fixed.copy(issueReport = Some(GoalPhaseFixIssues.Report(
        fixed.issueReport.get.dispositions.take(1)))), "partial-correction").isSuccess shouldBe false
      val passedreceipt = failedreceipt.copy(exitCode = Some(0), stderr = "", sbt = Some(
        WorkflowSbtValidation.Facts(kind, WorkflowSbtValidation.Outcome.Succeeded, None, None)))
      val checked = take(GoalPhaseFixIssues.validationC(_request.instanceIdentity, _plan.repository, corrected,
        failure.copy(receipts = Vector(passedreceipt), outcome = Some("passed")), "fresh-passing-phase-check"))
      val reviewed = _request.copy(review = Some(context.copy(checks = Vector(passedreceipt))), issues = checked)
      val accepted = _result.copy(outcome = "accept", findings = Vector.empty,
        acceptance = Some(B.Report(Vector(B.Observation("usage-contract", "satisfied", "The exact contract now holds")), Vector.empty)),
        issueReport = Some(GoalPhaseFixIssues.Report(Vector(GoalPhaseFixIssues.Disposition(_issues.issues.head.identity,
          "RESOLVED", "Scoped review verifies the original integrated blocker", None)))))
      val settled = take(GoalPhaseFixIssues.completedC(reviewed, accepted, "scoped-phase-review")).get
      Then("only actual successful checks settle TEST and independent scoped review settles REVIEW")
      settled.issues.map(_.status) shouldBe Vector("RESOLVED", "RESOLVED")
      reviewed.completed shouldBe _request.completed
      reviewed.originalBase shouldBe _request.originalBase
    }

    "expose both public operations through the same closed Resource transport without executing an action" in {
      Given("the generated Description and Recovery declarations and an absolute local input Resource")
      val operations = P.operations(null)
      def _request_(operation: String, properties: List[Property]) = Request.of(component = "SmWorkflow",
        service = "GoalPhaseService", operation = operation, properties = properties)
      When("each handler selects exactly one input route")
      val selected = operations.map(operation => operation.createOperationRequest(_request_(operation.name,
        List(Property("inputResource", "file:///specification/release-input.json", None)))))
      Then("both public Resource routes bind inert actions and neither both absent remote nor mistyped transport is accepted")
      selected.foreach(_.isSuccess shouldBe true)
      operations.foreach { operation =>
        val invalid = Vector(Nil, List(Property("inputResource", 42, None)),
          List(Property("inputResource", "https://example.invalid/input.json", None)),
          List(Property("input", _request, None), Property("inputResource", "file:///specification/release-input.json", None)))
        invalid.foreach(properties => operation.createOperationRequest(_request_(operation.name, properties)).isSuccess shouldBe false)
      }
    }

    "charge once while preserving the original Plan base completed prefix and report" in {
      Given("the exact marker-absent accepted Phase repair and its unanswered Decision")
      When("the controller accepts the separately admitted complete direction")
      val next = take(_decision.continueLegacyReleaseRepairC(_directive.identity, P.repairDirective(_payload)))
      Then("only the new generation and ordinary repair use change")
      next.stage shouldBe O.Stage.Repair
      next.plan shouldBe _decision.plan
      next.base shouldBe _decision.base
      next.completed shouldBe _decision.completed
      next.generation shouldBe _decision.generation + 1
      next.ordinaryRepairs shouldBe _decision.ordinaryRepairs + 1
      next.repairs shouldBe _decision.repairs + 1
      next.repairDirective.get.findings shouldBe Vector(_text)
      next.continueLegacyReleaseRepairC(_directive.identity, P.repairDirective(_payload)).isSuccess shouldBe false
      _target.originalRequest shouldBe W.inputCodec.encode(_request)
      _target.originalResult shouldBe _result
    }

    "reject missing reordered resolved foreign or unrelated condition blockers" in {
      Given("one actual failed required Phase condition and the corresponding native OPEN REVIEW issue")
      val invalid = Vector(_issues.copy(issues = Vector.empty),
        _issues.copy(issues = _issues.issues.map(_.copy(status = "RESOLVED"))),
        _issues.copy(issues = _issues.issues.map(_.copy(repository = "foreign"))),
        _issues.copy(issues = _issues.issues.map(_.copy(evidenceReference = "another-review"))),
        _issues.copy(issues = _issues.issues.map(_.copy(summary = "Unrelated observation"))))
      When("incomplete or crossed saved sources are selected")
      val refused = invalid.map(P.findingsC(_request, _result, _, _reference)) ++ Vector(
        P.findingsC(_request, _result.copy(outcome = "decision-needed"), _issues, _reference),
        P.findingsC(_request.copy(releaseRepairContract = Some(W.RELEASE_REPAIR_CONTRACT)), _result, _issues, _reference),
        P.findingsC(_request, _result.copy(acceptance = Some(B.Report(Vector.empty, Vector.empty))), _issues, _reference))
      Then("no arbitrary review prose or observation becomes an assigned Release blocker")
      refused.foreach(_.isSuccess shouldBe false)
      take(P.findingsC(_request, _result, _issues, _reference)) shouldBe _target.findings
    }

    "retain the exact original Request and binding through the public target Record round-trip" in {
      Given("the actual marker-absent owning Request encoding and its public target Record projection")
      val publicwire = io.circe.parser.parse(take(P.targetRecord(_target).toJsonStringC)).toOption.get
      val unknown = publicwire.hcursor.downField("originalRequest").downField("payload")
        .withFocus(_.mapObject(_.add("unknownReleaseField", true.asJson))).top.get
      When("the JSON-backed public target and a nested unknown non-null Request field are decoded")
      val restored = take(P.targetC(GoalPhaseNativeMetadata.record(publicwire)))
      val rejected = P.targetC(GoalPhaseNativeMetadata.record(unknown))
      val originalbinding = take(P.bindingC("sm-workflow:fixture", _target, _payload.choice, _payload.rationale,
        "participant", _directive))
      val restoredbinding = take(P.bindingC("sm-workflow:fixture", restored, _payload.choice, _payload.rationale,
        "participant", _directive))
      Then("the complete target and owning Request stay exact while extra supplied fields are refused")
      restored shouldBe _target
      restored.originalRequest shouldBe W.inputCodec.encode(_request)
      W.inputCodec.decode(restored.originalRequest) shouldBe Right(_request)
      restored.originalRequest.hcursor.downField("payload").downField("releaseRepairContract").focus shouldBe None
      restoredbinding shouldBe originalbinding
      rejected.isSuccess shouldBe false
    }

    "round-trip the distinct native disposition and refuse altered fields scope nulls or origins" in {
      Given("the complete original Request and Result with exact host policies and fixed programs")
      val wire = L.storageCodec.encode(L.ReleaseRepair(_payload))
      When("the owning strict tagged family reads the new variant and malformed alternatives")
      val decoded = L.storageCodec.decode(wire)
      Then("the new direction preserves original bytes projection and has no old-codec fallback")
      decoded shouldBe Right(L.ReleaseRepair(_payload))
      GoalPhaseRemainingPlanAdoption.codec.decode(wire).isLeft shouldBe true
      val malformed = Vector(wire.mapObject(_.add("origin", "unknown".asJson)), wire.mapObject(_.add("extra", true.asJson)),
        wire.hcursor.downField("payload").downField("target").withFocus(_.mapObject(_.add("extra", true.asJson))).top.get,
        wire.hcursor.downField("payload").downField("target").downField("host").withFocus(_ => Json.Null).top.get,
        wire.hcursor.downField("payload").downField("target").downField("completed").downArray
          .withFocus(_.mapObject(_.add("partialCompletion", Json.Null))).top.get,
        L.storageCodec.encode(L.ReleaseRepair(_payload.copy(target = _target.copy(pendingRevision = "22")))),
        L.storageCodec.encode(L.ReleaseRepair(_payload.copy(target = _target.copy(candidate = _base)))),
        L.storageCodec.encode(L.ReleaseRepair(_payload.copy(target = _target.copy(host = _host.copy(programs = Vector(P.Program("one", 1))))))),
        L.storageCodec.encode(L.ReleaseRepair(_payload.copy(choice = "Defer"))))
      malformed.foreach(value => L.storageCodec.decode(value).isLeft shouldBe true)
      val exhausted = take(GoalPhaseNativeReview.completedStateC(_result, _reference.identity, _phase_for(_plan.copy(repairLimit = 0))))
      exhausted.continueLegacyReleaseRepairC(_directive.identity,
        P.repairDirective(_payload)).isSuccess shouldBe false
    }

    "prepare only a separately observed exact Human binding and leave Defer passive" in {
      Given("the passive description and a specification-owned independent Human direction")
      val reference = V.WorkflowArtifactReference(_directive.identity, _directive.revision)
      val description = V.GoalPhaseReleaseRepairContinuationDescriptionResult(_target.sessionId, "direction-required", P.message,
        P.targetRecord(_target), P.choices, P.recoveryOperation, "sm-workflow:fixture")
      val input = V.GoalPhaseReleaseRepairContinuationRecoveryInput(_target.sessionId, description.target,
        _payload.choice, _payload.rationale, reference)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      When("the normal preparation constructs one import row and the binding is independently selected")
      val prepared = take(NativeReleaseRepairPreparation.prepareC(description, input, reference, execution, "participant",
        "normal-development-host", Instant.parse("2026-10-25T00:00:00Z")))
      val evidence = take(NativeExternalEvidence.decodeStorageC(prepared.record.getString(NativeExternalEvidence.storageField).get))
      val binding = take(P.bindingC(description.componentInstance, _target, _payload.choice, _payload.rationale, "participant", _directive))
      Then("purpose participant and the full target are independently bound without granting workflow authority")
      evidence.purpose shouldBe P.purpose
      P.selectBinding(binding) shouldBe Right(binding)
      P.selectBinding(binding.mapObject(_.add("unobserved", true.asJson))).isLeft shouldBe true
      NativeReleaseRepairPreparation.prepareC(description, input.copy(choice = "Defer"), reference, execution, "participant",
        "normal-development-host", Instant.parse("2026-10-25T00:00:00Z")).isSuccess shouldBe false
      P.choices shouldBe Vector("ContinueReleaseRepair", "Defer")
    }
  }
}
