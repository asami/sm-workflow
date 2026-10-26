package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseFixIssues.*
import GoalPhaseNativeWork as W
import GoalPhaseNativeValidation as N
import GoalPhaseOrderedProgression as O
import WorkflowSbtValidation as T
import RepositorySyncNativeMergeValidation as R

/** Pure business examples use actual-shaped receipts, never genuine host acts.
  * @since   Oct. 18, 2026
 * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseFixIssuesSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail("Unexpected specification consequence: " + failure)
  }
  private val _base = O.Snapshot("a" * 40, "b" * 40)
  private val _candidate = O.Snapshot(_base.head, "c" * 40)
  private val _plan = O.Plan("selected", "1", "PHASE-6.2", "repository",
    Vector(O.Step("S1", "Correct the selected behavior", Vector("one.scala"), Vector.empty, Vector("compile"))), Vector("compile"))
  private def _receipt(name: String = "compile", passed: Boolean = false): R.Receipt =
    R.Receipt(name, "exited", Some(if (passed) 0 else 1), "Actual-shaped stdout", "Actual-shaped diagnostic",
      Some(T.Facts(T.Kind.Compile, if (passed) T.Outcome.Succeeded else T.Outcome.Failed, None, None)))
  private def _validation(passed: Boolean = false, step: String = "S1", name: String = "compile"): N.Payload =
    N.Payload("12", step, _candidate, Vector(N.Check(name, 1, T.Kind.Compile)),
      Vector(_receipt(name, passed)), Some(if (passed) "passed" else "repair-required"))
  private def _test_state: State = _take(validationC("instance", _plan.repository, None, _validation(), "check-failed")).get
  private def _request(kind: String = "RepairStep", state: Option[State] = Some(_test_state)): W.Request =
    W.Request("instance", "14", kind, Some("S1"), 2, "Correct selected behavior", _plan, _base, Vector.empty,
      None, None, Some(O.RepairDirective("Correct actual finding", Vector("Retained finding"))),
      checkFailure = if (kind == "RepairStep") Some(_take(W.checkFailureContextC(
        Vector("one.scala"), "Actual failed candidate diff", _validation()))) else None, issues = state)
  private def _result(dispositions: Vector[Disposition], outcome: String = "completed", findings: Vector[String] = Vector.empty): W.Result =
    W.Result(outcome, "Explicit specification-owned semantic claim", findings, issueReport = Some(Report(dispositions)))
  private def _claim(id: String = "issue-1", disposition: String = "FIXED", ordinal: Option[Int] = None): Disposition =
    Disposition(id, disposition, "Explicit observation of the assigned boundary", ordinal)
  private def _review_state: State = _take(completedC(_request("ReviewStep", None),
    W.Result("repair", "Actual independent finding", Vector("Required behavior missing")), "review-first")).get
  private def _completed(req: W.Request, res: W.Result, evidence: String = "semantic-result"): State =
    _take(completedC(req, res, evidence)).get
  private def _payload(wire: Json, changed: Json): Json = wire.mapObject(_.add("payload", changed))

  "Strict tracked issue contracts" should {
    "roundtrip exact state and reports through current internal codecs and public generated DTOs" in {
      Given("a selected failed-check issue and explicit repair claim in a retained Decision")
      val req = _request()
      val res = _result(Vector(_claim()))
      val displayed = NativeWorkflowOperationOutput.goalWork(req.copy(decisionContext = Some(
        W.DecisionContext(res, "ResumeAssignedWork", "Keep the same selected task"))))
      When("current codecs and strict generated readers carry the exact structured values")
      val requestwire = W.inputCodec.encode(req)
      val resultwire = W.resultCodec.encode(res)
      val publicstate = _take(publicStateC(req.issues)).get
      val publicreport = _take(GoalPhaseFixIssues.publicReportC(res.issueReport)).get
      val readstate = _take(V.GoalPhaseIssueState.createC(_take(new RecordDecoder().json(_take(publicstate.toRecord().toJsonStringC)))))
      val readreport = _take(V.GoalPhaseIssueReport.createC(_take(new RecordDecoder().json(_take(publicreport.toRecord().toJsonStringC)))))
      Then("all business fields survive without schema fallback or lost reports")
      W.inputCodec.decode(requestwire) shouldBe Right(req)
      W.resultCodec.decode(resultwire) shouldBe Right(res)
      _take(internalStateC(Some(readstate))) shouldBe req.issues
      _take(internalReportC(Some(readreport))) shouldBe res.issueReport
      _take(internalStateC(displayed.issues)) shouldBe req.issues
      _take(internalReportC(displayed.decisionContext.get.requestingResult.issueReport)) shouldBe res.issueReport
      requestwire.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-work-request.v2")
      resultwire.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-work-result.v1")
    }

    "omit absent fields and reject present-null or malformed structured wire values" in {
      Given("current Request and Result wires without optional issue values")
      val req = W.inputCodec.encode(_request("ImplementStep", None))
      val res = W.resultCodec.encode(W.Result("completed", "Selected implementation", Vector.empty))
      val invalid = Vector(Json.Null, Json.obj(), Json.fromString("FIXED"))
      When("each malformed present value replaces an absent optional field")
      val requests = invalid.map(v => W.inputCodec.decode(_payload(req,
        req.hcursor.downField("payload").focus.get.mapObject(_.add("issues", v)))))
      val results = invalid.map(v => W.resultCodec.decode(_payload(res,
        res.hcursor.downField("payload").focus.get.mapObject(_.add("issueReport", v)))))
      Then("absence remains additive while malformed presence rejects")
      req.hcursor.downField("payload").downField("issues").focus shouldBe None
      res.hcursor.downField("payload").downField("issueReport").focus shouldBe None
      requests.foreach(_ shouldBe a[Left[?, ?]])
      results.foreach(_ shouldBe a[Left[?, ?]])
    }

    "reject null nested options closed vocabularies invalid UTF8 and oversized values" in {
      Given("one valid bounded issue state and deliberately malformed nested business values")
      val state = _test_state
      val issue = state.issues.head
      val invalid = Vector(state.copy(issues = null), state.copy(history = null), state.copy(movement = null),
        state.copy(issues = Vector(null)), state.copy(issues = Vector(issue.copy(sourceIdentity = null))),
        state.copy(issues = Vector(issue.copy(latestDisposition = Some(null)))),
        state.copy(issues = Vector(issue.copy(source = "UNKNOWN"))), state.copy(issues = Vector(issue.copy(status = "FIXED"))),
        state.copy(issues = Vector(issue.copy(identity = "x" * 4097))),
        state.copy(issues = Vector(issue.copy(summary = "x" * 16385))),
        state.copy(issues = Vector(issue.copy(summary = "bad\u0000value"))),
        state.copy(issues = Vector(issue.copy(summary = "\ud800"))), state.copy(issues = Vector.fill(16385)(issue)),
        state.copy(history = Vector.fill(65537)(state.history.head)))
      val publicvalue = _take(publicStateC(Some(state))).get
      When("strict model and DTO admission consume each malformed value")
      val rejected = invalid.map(validateC)
      val publicrejected = Vector(internalStateC(null), internalStateC(Some(null)),
        internalStateC(Some(publicvalue.copy(issues = Vector(publicvalue.issues.head.copy(sourceIdentity = null))))))
      Then("no invalid option string nested value or bound becomes absence")
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
      publicrejected.foreach(_ shouldBe a[Consequence.Failure[?]])
      state.asJson.mapObject(_.add("movement", Json.Null)).as[State] shouldBe a[Left[?, ?]]
      issue.asJson.mapObject(_.add("sourceIdentity", Json.Null)).as[Issue] shouldBe a[Left[?, ?]]
      _claim().asJson.mapObject(_.add("findingOrdinal", Json.Null)).as[Disposition] shouldBe a[Left[?, ?]]
    }
  }

  "Exact repair accounting" should {
    "keep FIXED unresolved and admit explicit partial progress without granting acceptance" in {
      Given("two current unresolved issues and a completed semantic batch reporting one FIXED and one UNRESOLVED")
      val both = _completed(_request("ReviewStep", Some(_test_state)),
        W.Result("repair", "Independent required issue", Vector("Additional required behavior")))
      val req = _request(state = Some(both))
      val res = _result(Vector(_claim("issue-1"), _claim("issue-2", "UNRESOLVED")))
      When("the assigned repair result is reconciled")
      val next = _completed(req, res)
      val admitted = W.admitSemanticResultC(req, res)
      Then("both issues remain unresolved with honest dispositions and zero invented resolution transitions")
      admitted shouldBe Consequence.unit
      next.issues.map(_.status) shouldBe Vector("OPEN", "OPEN")
      next.issues.map(_.latestDisposition) shouldBe Vector(Some("FIXED"), Some("UNRESOLVED"))
      next.history shouldBe both.history
      next.movement.get.resolvedIssues shouldBe 0
      next.movement.get.openIssues shouldBe 2
    }

    Vector("FIXED", "UNRESOLVED", "DECLINED", "BLOCKED").foreach { disposition =>
      s"retain an explicit $disposition repair disposition" in {
        Given("one issued unresolved check issue")
        val req = _request()
        val res = _result(Vector(_claim(disposition = disposition)),
          if (disposition == "BLOCKED") "decision-needed" else "completed")
        When("the matching semantic repair result is admitted")
        val next = _completed(req, res)
        Then("the exact disposition is retained and only DECLINED changes unresolved status")
        W.admitSemanticResultC(req, res) shouldBe Consequence.unit
        next.issues.head.latestDisposition shouldBe Some(disposition)
        next.issues.head.status shouldBe (if (disposition == "DECLINED") "DECLINED" else "OPEN")
        next.movement.get.resolvedIssues shouldBe 0
      }
    }

    "require BLOCKED to use the existing decision-needed route" in {
      Given("an issued issue and a BLOCKED claim incorrectly reporting completed")
      val req = _request()
      When("native semantic admission consumes the claim")
      val rejected = W.admitSemanticResultC(req, _result(Vector(_claim(disposition = "BLOCKED"))))
      Then("the typed error prevents semantic completion without inventing Human authority")
      rejected shouldBe a[Consequence.Failure[?]]
    }

    "reject missing duplicate unknown unsolicited wrong-vocabulary and ordinal-bearing repair reports" in {
      Given("one current issued unresolved ID and malformed claims against that exact assignment")
      val req = _request()
      val invalid = Vector(W.Result("completed", "No accounting", Vector.empty), _result(Vector.empty),
        _result(Vector(_claim(), _claim())), _result(Vector(_claim("unknown"))), _result(Vector(_claim(), _claim("unsolicited"))),
        _result(Vector(_claim(disposition = "RESOLVED"))), _result(Vector(_claim(ordinal = Some(1)))),
        _result(Vector(_claim().copy(rationale = " "))), _result(Vector(_claim().copy(rationale = "x" * 16385))),
        _result(Vector(_claim().copy(findingOrdinal = null))), _result(Vector(_claim(disposition = "UNKNOWN"))),
        _result(Vector.fill(16385)(_claim())), _result(null), _result(Vector(null)),
        _result(Vector(_claim())).copy(issueReport = null))
      When("native semantic admission checks every supplied report")
      val rejected = invalid.map(W.admitSemanticResultC(req, _))
      Then("absence cannot waive accounting and malformed or invented claims cannot progress")
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "reject reports on initial implementation and invented FIXED claims without issued IDs" in {
      Given("initial implementation and a repair assignment carrying no tracked issues")
      val initial = _request("ImplementStep", None).copy(repairDirective = None, checkFailure = None)
      val noissues = _request(state = None)
      When("unsolicited reports are submitted")
      val rejected = Vector(W.admitInitialResultC(initial, _result(Vector.empty)),
        W.admitSemanticResultC(noissues, _result(Vector(_claim()))))
      Then("initial work cannot claim issue outcomes and empty assignments cannot manufacture FIXED")
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
    }
  }

  "Actual selected check evidence" should {
    "retain dependency issues across source revision and resolve the same issue only through fresh matching checks" in {
      val original = DependencyValidationSpecificationFixture
      val revised = DependencySourceRevisionSpecificationFixture
      Given("an unresolved failed legacy dependency check and a new source for the same Component and check")
      val check = original.check.copy(dependency = Some(original.binding.identity))
      val failed = _validation(name = check.identity).copy(step = original.binding.step,
        selected = Vector(check), dependency = Some(original.capture),
        dependencyExecutions = Vector(GoalPhaseDependencyValidation.Execution(check.identity, 0,
          original.binding.dependency.workspace, check.kind.wire)))
      N.codec.decode(N.codec.encode(failed)) shouldBe Right(failed)
      val previous = _take(validationC("same-session", original.plan.repository, None, failed, "original-dependency-failure")).get
      val issue = previous.issues.head
      val fresh = failed.copy(dependency = None, dependencySource = Some(revised.capture),
        sourceConsumption = Some(revised.proposal.consumption),
        dependencyExecutions = Vector(GoalPhaseDependencyValidation.Execution(check.identity, 0,
          revised.binding.dependency.workspace, check.kind.wire)),
        receipts = Vector(_receipt(check.identity, passed = true)), outcome = Some("passed"))
      N.codec.decode(N.codec.encode(fresh)) shouldBe Right(fresh)
      val repair = _request(state = Some(previous)).copy(selectedPlan = original.plan,
        stepIdentity = Some(original.binding.step), checkFailure = Some(W.CheckFailureContext(
          original.paths, "Revised dependency source", fresh.copy(receipts = failed.receipts, outcome = failed.outcome))))
      val review = repair.copy(kind = "ReviewStep", checkFailure = None,
        review = Some(GoalPhaseNativeReview.Context(original.paths, "Revised source review", fresh.receipts,
          dependencyValidations = Vector(fresh))))
      When("repair and review inspect the revised source before its fresh validation is settled")
      val assignedIds = assigned(repair).map(_.identity)
      val prematureAcceptance = completedC(review, W.Result("accept", "No fresh issue settlement", Vector.empty), "premature-review")
      Then("the dependency issue remains assigned and cannot disappear from independent review")
      issue.repository shouldBe original.binding.dependency.repository
      assignedIds shouldBe Vector(issue.identity)
      relevant(review, issue) shouldBe true
      prematureAcceptance shouldBe a[Consequence.Failure[?]]
      When("fresh actual-shaped matching success and a later supported recurrence are traversed")
      val passed = _take(validationC("same-session", original.plan.repository, Some(previous), fresh, "revised-source-pass")).get
      val recurring = _take(validationC("same-session", original.plan.repository, Some(passed),
        fresh.copy(receipts = failed.receipts, outcome = failed.outcome), "revised-source-failure")).get
      Then("the original dependency identity resolves and reopens without a replacement primary issue")
      passed.issues.map(_.identity) shouldBe Vector(issue.identity)
      passed.issues.head.status shouldBe "RESOLVED"
      passed.history.take(previous.history.size) shouldBe previous.history
      recurring.issues.map(_.identity) shouldBe Vector(issue.identity)
      recurring.issues.head.status shouldBe "REOPENED"
      recurring.issues.head.repository shouldBe issue.repository
    }

    "resolve FIXED only after a fresh matching passed receipt and reopen a later supported recurrence" in {
      Given("a FIXED claim whose original failed check is still unresolved")
      val fixed = _completed(_request(), _result(Vector(_claim())))
      When("fresh same-Step selected passing evidence and then a genuine later failure are traversed")
      val passed = _take(validationC("instance", _plan.repository, Some(fixed), _validation(true), "fresh-pass")).get
      val recurring = _take(validationC("instance", _plan.repository, Some(passed), _validation(), "later-fail")).get
      Then("the same ID resolves once and then reopens with attributable transition counts")
      passed.issues.head.identity shouldBe fixed.issues.head.identity
      passed.issues.head.status shouldBe "RESOLVED"
      passed.movement.get.resolvedIssues shouldBe 1
      recurring.issues.head.status shouldBe "REOPENED"
      recurring.movement.get.reopenedIssues shouldBe 1
      recurring.movement.get.newIssues shouldBe 0
      recurring.history.last.previousStatus shouldBe Some("RESOLVED")
    }

    "leave unrelated unexecuted unknown and provider-error checks unresolved" in {
      Given("one unresolved selected check and boundaries without its actual passed receipt")
      val original = _validation()
      val unsupported = Vector(_validation(true, name = "another"),
        original.copy(receipts = Vector.empty, outcome = None), original.copy(outcome = Some("unknown")),
        original.copy(receipts = Vector(_receipt().copy(termination = "provider-error", exitCode = None, sbt = None)), outcome = Some("failed")))
      When("each boundary is traversed from the same issue state")
      val states = unsupported.map(p => _take(validationC("instance", _plan.repository, Some(_test_state), p, "unrelated")).get)
      Then("unsupported observations establish no resolution or extra transitions")
      states.foreach { state =>
        state.issues.head.status shouldBe "OPEN"
        state.history shouldBe _test_state.history
        state.movement.get.resolvedIssues shouldBe 0
      }
    }

    "resolve only the executed positive prefix while preserving a failed later check" in {
      Given("two existing check issues and a partial actual receipt set ending in the second failure")
      val second = _take(validationC("instance", _plan.repository, Some(_test_state), _validation(name = "later"), "later-failed")).get
      val partial = _validation().copy(selected = Vector(N.Check("compile", 1, T.Kind.Compile), N.Check("later", 2, T.Kind.Compile),
        N.Check("unexecuted", 3, T.Kind.Compile)), receipts = Vector(_receipt(passed = true), _receipt("later")))
      When("the actual completed boundary is traversed")
      val next = _take(validationC("instance", _plan.repository, Some(second), partial, "partial-validation")).get
      Then("only the passed matching issue resolves and the unresolved issue is not counted as new")
      next.issues.map(_.status) shouldBe Vector("RESOLVED", "OPEN")
      next.movement.get.resolvedIssues shouldBe 1
      next.movement.get.newIssues shouldBe 0
    }

    "isolate same-named checks by Step repository and kind while retaining closed-Step history" in {
      Given("one recorded issue and a same-named failure in another Step and repository")
      val first = _test_state
      val otherstep = _take(validationC("instance", _plan.repository, Some(first), _validation(step = "S2"), "step-two")).get
      val otherrepository = _take(validationC("instance", "other-repository", Some(otherstep), _validation(), "other-repository")).get
      val otherkindpayload = _validation().copy(selected = Vector(N.Check("compile", 1, T.Kind.Test)),
        receipts = Vector(_receipt().copy(sbt = Some(T.Facts(T.Kind.Test, T.Outcome.Failed, None, None)))))
      When("another kind fails and a current-Step repair is assigned")
      val next = _take(validationC("instance", _plan.repository, Some(otherrepository), otherkindpayload, "other-kind")).get
      val issued = assigned(_request(state = Some(next)))
      Then("each supported scope has its own monotonic ID and closed-Step issues remain unassigned history")
      next.issues.map(_.identity) shouldBe Vector("issue-1", "issue-2", "issue-3", "issue-4")
      issued.map(_.identity) shouldBe Vector("issue-1", "issue-4")
      next.history.take(first.history.size) shouldBe first.history
    }

    "reconstruct identical metrics and history on every traversal without inflating repeated failure" in {
      Given("the same contiguous actual failed repair and failed-again evidence sequence")
      def _replay_(): State = {
        val first = _test_state
        val fixed = _completed(_request(state = Some(first)), _result(Vector(_claim())), "repair-result")
        _take(validationC("instance", _plan.repository, Some(fixed), _validation(), "failed-again")).get
      }
      When("the complete history is independently reconstructed twice")
      val first = _replay_()
      val second = _replay_()
      Then("IDs metrics and transition history are deterministic and an already-open failure adds no transition")
      second shouldBe first
      first.history.size shouldBe 1
      first.movement.get shouldBe Movement("failed-again", 1, 0, 0, 0, true)
    }
  }

  "Explicit independent review coverage" should {
    "preserve NOT_REVIEWED and reject accept until explicit relevant resolution" in {
      Given("one existing REVIEW issue with unknown cross-observation source correspondence")
      val req = _request("ReviewStep", Some(_review_state))
      val report = _result(Vector(_claim(disposition = "NOT_REVIEWED")), "decision-needed")
      When("independent coverage preserves the issue and an accept claims no resolution")
      val preserved = _completed(req, report)
      val rejected = completedC(req, report.copy(outcome = "accept"), "review-accept")
      val resolved = _completed(req, _result(Vector(_claim(disposition = "RESOLVED")), "accept"))
      Then("omission and nonreview never imply resolution but explicit coverage can resolve")
      preserved.issues.head.status shouldBe "OPEN"
      preserved.movement.get.correspondenceKnown shouldBe false
      rejected shouldBe a[Consequence.Failure[?]]
      resolved.issues.head.status shouldBe "RESOLVED"
      completedC(req, W.Result("accept", "No implicit resolution", Vector.empty), "missing") shouldBe a[Consequence.Failure[?]]
    }

    "bind recurring findings by ordinal and create only unclaimed new findings" in {
      Given("one resolved REVIEW issue and two actual new observation findings")
      val resolved = _completed(_request("ReviewStep", Some(_review_state)), _result(Vector(_claim(disposition = "RESOLVED")), "accept"))
      val req = _request("ReviewStep", Some(resolved))
      When("the independent result binds its first finding to that exact prior issue")
      val next = _completed(req, _result(Vector(_claim(disposition = "RECURRING", ordinal = Some(1))),
        "repair", Vector("Required behavior recurred", "A different required finding")))
      Then("the resolved issue reopens and only the unclaimed ordinal creates a new ID")
      next.issues.map(_.identity) shouldBe Vector("issue-1", "issue-2")
      next.issues.map(_.status) shouldBe Vector("REOPENED", "OPEN")
      next.issues.head.summary shouldBe "Required behavior recurred"
      next.movement.get.reopenedIssues shouldBe 1
      next.movement.get.newIssues shouldBe 1
      next.movement.get.correspondenceKnown shouldBe false
    }

    "update recurring unresolved evidence without inventing new or reopened transitions" in {
      Given("one already unresolved REVIEW issue")
      val req = _request("ReviewStep", Some(_review_state))
      When("a supported actual ordinal observes that issue again")
      val next = _completed(req, _result(Vector(_claim(disposition = "RECURRING", ordinal = Some(1))), "repair", Vector("Still missing")))
      Then("the ID and status persist while actual evidence changes with no inflated transition")
      next.issues.head.status shouldBe "OPEN"
      next.issues.head.evidenceReference shouldBe "semantic-result"
      next.history shouldBe _review_state.history
      next.movement.get.newIssues shouldBe 0
      next.movement.get.reopenedIssues shouldBe 0
    }

    "reject invalid coverage unknown IDs wrong vocabulary and unsupported or reused ordinals" in {
      Given("two relevant REVIEW issues including one already resolved issue")
      val both = _completed(_request("ReviewStep", None), W.Result("repair", "Two independent findings", Vector("One", "Two")))
      val resolved = _completed(_request("ReviewStep", Some(both)), _result(Vector(
        _claim("issue-1", "RESOLVED"), _claim("issue-2", "NOT_REVIEWED")), "decision-needed"))
      val req = _request("ReviewStep", Some(resolved))
      val invalid = Vector(Vector(_claim("issue-2", "NOT_REVIEWED")),
        Vector(_claim("unknown", "RESOLVED")), Vector(_claim("issue-1", "FIXED"), _claim("issue-2", "RESOLVED")),
        Vector(_claim("issue-1", "RECURRING"), _claim("issue-2", "NOT_REVIEWED")),
        Vector(_claim("issue-1", "RESOLVED", Some(1)), _claim("issue-2", "NOT_REVIEWED")),
        Vector(_claim("issue-1", "RECURRING", Some(3)), _claim("issue-2", "NOT_REVIEWED")),
        Vector(_claim("issue-1", "RECURRING", Some(1)), _claim("issue-2", "RECURRING", Some(1))))
      When("each malformed report attempts to cover the actual findings")
      val rejected = invalid.map(entries => completedC(req, _result(entries, "repair", Vector("One", "Two")), "review"))
      Then("coverage cannot omit resolved REVIEW IDs invent correspondence or reuse findings")
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "leave omitted TEST issues unresolved and guard all Steps in ReviewPhase" in {
      Given("an unresolved TEST issue from a closed Step and a current review of another Step")
      val state = _test_state
      val current = _request("ReviewStep", Some(state)).copy(stepIdentity = Some("S2"))
      val phase = current.copy(kind = "ReviewPhase", stepIdentity = None)
      val accept = W.Result("accept", "Explicit independent observation", Vector.empty)
      When("Step and Phase reviews inspect their actual relevant issue scope")
      val step = completedC(current, accept, "step-review")
      val whole = completedC(phase, accept, "phase-review")
      val covered = completedC(phase, _result(Vector(_claim(disposition = "RESOLVED")), "accept"), "explicit-test-coverage")
      Then("old-Step history stays while only the whole-Phase unresolved guard sees all Steps")
      _take(step).get.issues shouldBe state.issues
      whole shouldBe a[Consequence.Failure[?]]
      _take(covered).get.issues.head.status shouldBe "RESOLVED"
    }
  }
}
