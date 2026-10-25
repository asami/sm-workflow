package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseFixAssessment.*
import GoalPhaseNativeWork as W
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeValidation as N
import RepositorySyncNativeMergeValidation as R
import WorkflowSbtValidation as T

/** Pure semantic admission and real-shaped public/internal payload examples.
  * @since   Oct. 18, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseFixAssessmentSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail("Unexpected assessment fixture admission: " + failure)
  }
  private def _oid(c: Char): String = c.toString * 40
  private val _base = O.Snapshot(_oid('a'), _oid('b'))
  private val _candidate = O.Snapshot(_base.head, _oid('c'))
  private val _plan = O.Plan("selected-plan", "1", "PHASE-6.2", "owned-repository",
    Vector(O.Step("S1", "Correct the selected behavior", Vector("src/one.scala"), Vector.empty, Vector("compile"))),
    Vector("compile"))
  private val _change = Change("SIMPLE_LOGIC", Vector("one.validate"), false, false,
    "Correct the local boundary condition", false, false)
  private val _convergence = Convergence("PROGRESSING", "MEDIUM", "The retained diagnostic is addressed; selected checks remain required")
  private val _report = Report(_change, _convergence)
  private val _review = GoalPhaseNativeReview.Context(Vector("src/one.scala"), "Actual reviewed diff", Vector.empty)
  private val _failure = N.Payload("12", "S1", _candidate, Vector(N.Check("compile", 1, T.Kind.Compile)),
    Vector(R.Receipt("compile", "exited", Some(1), "actual stdout", "actual diagnostic",
      Some(T.Facts(T.Kind.Compile, T.Outcome.Failed, None, None)))), Some("repair-required"))
  private def _request(test: Boolean): W.Request = W.Request("actual-instance", "14", "RepairStep", Some("S1"), 2,
    "Correct the selected behavior", _plan, _base, Vector.empty, None, None,
    Some(O.RepairDirective("Correct actual failure", Vector("Address retained findings"))),
    review = if (test) None else Some(_review),
    checkFailure = if (test) Some(_take(W.checkFailureContextC(Vector("src/one.scala"), "Actual failed candidate diff", _failure))) else None)
  private def _result(value: Option[Report] = Some(_report)): W.Result = W.Result("completed", "Corrected source", Vector.empty,
    fixAssessment = value)
  private def _with_acceptance(value: W.Request): W.Request = value.copy(selectedPlan = _take(GoalPhaseAcceptanceBoundary.captureC(
    Some(GoalPhaseAcceptanceBoundary.Project(Vector(GoalPhaseAcceptanceBoundary.Condition(
      "behavior", "all", true, "Complete the selected behavior")))), value.selectedPlan)))
  private def _acceptance(advisory: String): GoalPhaseAcceptanceBoundary.Report = GoalPhaseAcceptanceBoundary.Report(
    Vector(GoalPhaseAcceptanceBoundary.Observation("behavior", "satisfied", "Selected behavior addressed")), Vector(advisory))
  private def _payload(wire: Json, value: Json): Json = wire.mapObject(_.add("payload", value))

  "Bounded Fix assessment" should {
    Vector("HYGIENE", "TRIVIAL_COMPILE_FIX", "SIMPLE_LOGIC", "STANDARD_LOGIC", "COMPLEX_LOGIC").foreach { kind =>
      s"admit exact $kind claims with the matching semantic coupling" in {
        Given("a bounded report describing the actual logical units")
        val value = _report.copy(change = _change.copy(changeClass = kind, crossFileLogic = kind == "COMPLEX_LOGIC"))
        When("the semantic assessment is admitted")
        val admitted = validateC(value)
        Then("the supported exact class is retained without deriving depth from file count")
        admitted shouldBe Consequence.unit
        value.change.changeClass shouldBe kind
      }
    }

    "keep independent units STANDARD and require coordinated logic to be COMPLEX" in {
      Given("three independent local units in separate source files")
      val independent = _report.copy(change = _change.copy(changeClass = "STANDARD_LOGIC",
        affectedLogicalUnits = Vector("one.local", "two.local", "three.local")))
      val coordinated = independent.copy(change = independent.change.copy(changeClass = "COMPLEX_LOGIC", crossFileLogic = true))
      When("classification and semantic coupling are admitted together")
      val accepted = Vector(independent, coordinated).map(validateC)
      val rejected = Vector(independent.copy(change = independent.change.copy(crossFileLogic = true)),
        coordinated.copy(change = coordinated.change.copy(crossFileLogic = false))).map(validateC)
      Then("independent physical files do not force coordinated depth, and inconsistent claims fail")
      accepted shouldBe Vector(Consequence.unit, Consequence.unit)
      rejected.foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    val malformed: Vector[(String, Report => Report)] = Vector(
      "unknown class" -> (r => r.copy(change = _change.copy(changeClass = "MINOR"))),
      "padded class" -> (r => r.copy(change = _change.copy(changeClass = " SIMPLE_LOGIC"))),
      "null class" -> (r => r.copy(change = _change.copy(changeClass = null))),
      "null change" -> (r => r.copy(change = null)),
      "null convergence" -> (r => r.copy(convergence = null)),
      "null units" -> (r => r.copy(change = _change.copy(affectedLogicalUnits = null))),
      "empty units" -> (r => r.copy(change = _change.copy(affectedLogicalUnits = Vector.empty))),
      "duplicate units" -> (r => r.copy(change = _change.copy(affectedLogicalUnits = Vector("one", "one")))),
      "too many units" -> (r => r.copy(change = _change.copy(affectedLogicalUnits = Vector.tabulate(129)(i => s"unit-$i")))),
      "null unit" -> (r => r.copy(change = _change.copy(affectedLogicalUnits = Vector(null)))),
      "blank unit" -> (r => r.copy(change = _change.copy(affectedLogicalUnits = Vector(" ")))),
      "NUL unit" -> (r => r.copy(change = _change.copy(affectedLogicalUnits = Vector("one\u0000unit")))),
      "oversize UTF8 unit" -> (r => r.copy(change = _change.copy(affectedLogicalUnits = Vector("界" * 342)))),
      "null change rationale" -> (r => r.copy(change = _change.copy(rationale = null))),
      "blank change rationale" -> (r => r.copy(change = _change.copy(rationale = " "))),
      "NUL change rationale" -> (r => r.copy(change = _change.copy(rationale = "why\u0000"))),
      "oversize change rationale" -> (r => r.copy(change = _change.copy(rationale = "x" * 16385))),
      "unknown status" -> (r => r.copy(convergence = _convergence.copy(status = "CONVERGING"))),
      "padded status" -> (r => r.copy(convergence = _convergence.copy(status = " PROGRESSING"))),
      "null status" -> (r => r.copy(convergence = _convergence.copy(status = null))),
      "unknown confidence" -> (r => r.copy(convergence = _convergence.copy(confidence = "CERTAIN"))),
      "padded confidence" -> (r => r.copy(convergence = _convergence.copy(confidence = "HIGH "))),
      "null confidence" -> (r => r.copy(convergence = _convergence.copy(confidence = null))),
      "null convergence rationale" -> (r => r.copy(convergence = _convergence.copy(rationale = null))),
      "blank convergence rationale" -> (r => r.copy(convergence = _convergence.copy(rationale = " "))),
      "NUL convergence rationale" -> (r => r.copy(convergence = _convergence.copy(rationale = "why\u0000"))),
      "oversize convergence rationale" -> (r => r.copy(convergence = _convergence.copy(rationale = "x" * 16385))))
    malformed.foreach { case (description, alter) =>
      s"reject $description without replacing it with an absent assessment" in {
        Given("an otherwise valid actual repair assessment")
        val invalid = alter(_report)
        When("typed assessment admission consumes the malformed value")
        val admitted = validateC(invalid)
        Then("the exact malformed report fails admission")
        admitted shouldBe a[Consequence.Failure[?]]
      }
    }

    "admit maximum bounded logical units and rationales without truncation" in {
      Given("128 distinct units and rationales at their UTF8 byte limits")
      val units = Vector.tabulate(128)(i => s"$i-" + ("x" * (1023 - i.toString.length)))
      val value = _report.copy(change = _change.copy(affectedLogicalUnits = units, rationale = "x" * 16384),
        convergence = _convergence.copy(rationale = "y" * 16384))
      When("the boundary-sized report is admitted")
      val admitted = validateC(value)
      Then("all exact text remains present")
      admitted shouldBe Consequence.unit
      value.change.affectedLogicalUnits shouldBe units
      value.change.rationale.length shouldBe 16384
    }

    "reject null report values and null or populated-invalid report options" in {
      Given("missing Option containers and nested report values")
      val options: Vector[Option[Report]] = Vector(null, Some(null), Some(_report.copy(change = null)))
      When("option and report admission consume them")
      val admitted = options.map(validateOptionC)
      val direct = validateC(null)
      Then("none is normalized to a legitimate absent optional report")
      admitted.foreach(_ shouldBe a[Consequence.Failure[?]])
      direct shouldBe a[Consequence.Failure[?]]
      validateOptionC(None) shouldBe Consequence.unit
    }
  }

  "Ordinary repair classification" should {
    val cases: Vector[(String, Change, Boolean)] = Vector(
      ("SIMPLE label alone", _change, false),
      ("explicit local minor bug", _change.copy(minorBugFix = true), true),
      ("explicit minor compile correction", _change.copy(changeClass = "TRIVIAL_COMPILE_FIX", minorBugFix = true), true),
      ("compile label alone", _change.copy(changeClass = "TRIVIAL_COMPILE_FIX"), false),
      ("pure hygiene", _change.copy(changeClass = "HYGIENE"), true),
      ("requirement change mixed with hygiene", _change.copy(changeClass = "HYGIENE", requirementChanged = true), false),
      ("contract change mixed with minor fix", _change.copy(minorBugFix = true, contractChanged = true), false),
      ("STANDARD marked minor", _change.copy(changeClass = "STANDARD_LOGIC", minorBugFix = true), false),
      ("coordinated logic marked minor", _change.copy(changeClass = "COMPLEX_LOGIC", crossFileLogic = true, minorBugFix = true), false))
    cases.foreach { case (description, classified, exempt) =>
      s"apply the settled ordinary-count rule to $description" in {
        Given("an actual review-sourced repair and its semantic change report")
        val actual = _result(Some(_report.copy(change = classified)))
        When("the completion owner derives exemption from admitted actual work")
        val observed = _take(ordinaryCountExemptC(_request(false), actual))
        Then("the semantic contents determine exemption without changing the automatic count")
        observed shouldBe exempt
      }
    }

    "conservatively count current ordinary results that omit an assessment" in {
      Given("a current repair result using the compatible defaulted constructor")
      val actual = W.Result("completed", "Source corrected", Vector.empty)
      When("the native owner derives ordinary-count treatment")
      val exempt = _take(ordinaryCountExemptC(_request(true), actual))
      Then("missing semantic claims never imply minor work")
      actual.fixAssessment shouldBe None
      exempt shouldBe false
    }
  }

  "Actual semantic result admission" should {
    Vector(true, false).foreach { test =>
      val source = if (test) "TEST" else "REVIEW"
      s"admit $source repair reports and keep BLOCKED on its real decision path" in {
        Given("a genuine " + source + " repair request with its retained failure context")
        val assigned = _request(test)
        val blocked = _result(Some(_report.copy(convergence = _convergence.copy(status = "BLOCKED"))))
        When("completed and decision-needed dispositions carry that report")
        val completed = W.admitSemanticResultC(assigned, _result())
        val rejected = W.admitSemanticResultC(assigned, blocked)
        val decision = blocked.copy(outcome = "decision-needed")
        val admitted = W.admitSemanticResultC(assigned, decision)
        val retained = W.resultCodec.decode(W.resultCodec.encode(decision))
        Then("BLOCKED cannot proceed as completed and its actual decision history retains the report")
        completed shouldBe Consequence.unit
        rejected shouldBe a[Consequence.Failure[?]]
        admitted shouldBe Consequence.unit
        retained shouldBe Right(decision)
        _take(ordinaryCountExemptC(assigned, decision)) shouldBe false
      }
    }

    Vector("PROGRESSING", "STALLED", "REGRESSING").foreach { status =>
      s"retain advisory $status without turning it into commands or acceptance" in {
        Given("a current repair result carrying one supported advisory status")
        val actual = _result(Some(_report.copy(convergence = _convergence.copy(status = status))))
        When("the actual Request and Result are admitted")
        val admitted = W.admitSemanticResultC(_request(false), actual)
        Then("the advisory status preserves the existing semantic disposition")
        admitted shouldBe Consequence.unit
        actual.outcome shouldBe "completed"
        _take(ordinaryCountExemptC(_request(false), actual)) shouldBe false
      }
    }

    "reject assessment reports on initial implementation and independent Step or Phase review" in {
      Given("an implementation frontier and independently checked review frontiers")
      val implementation = _request(false).copy(kind = "ImplementStep", generation = 1, repairDirective = None, review = None)
      val initial = _take(O.startC(_plan, _base))
      val key = initial.next.asInstanceOf[O.Semantic].key
      val inspected = _take(_take(initial.acceptC(O.SemanticCompleted(key, "Ready"))).acceptC(O.CandidateObserved(_candidate)))
      val checked = _take(inspected.acceptC(O.ChecksObserved(_candidate, Vector("compile"), "checks", true)))
      val reviewkey = checked.next.asInstanceOf[O.Semantic].key
      val accepted = _take(checked.acceptC(O.ReviewAdmitted(reviewkey, _candidate, "checks", "review", O.ReviewOutcome.Accept)))
      val closing = _take(accepted.acceptC(O.ClosureIntentSaved(_candidate, "intent")))
      val closed = _take(closing.acceptC(O.StepCommitObserved("intent", O.Commit(_oid('d'), _base.head, _candidate.tree))))
      val phasecandidate = O.Snapshot(_oid('d'), _candidate.tree)
      val phaseinspected = _take(closed.acceptC(O.CandidateObserved(phasecandidate)))
      val phasechecked = _take(phaseinspected.acceptC(O.ChecksObserved(phasecandidate, Vector("compile"), "phase-checks", true)))
      When("non-repair workers submit either nonempty or null report options")
      val options: Vector[Option[Report]] = Vector(Some(_report), null, Some(null))
      val rejectedimplementation = options.map(value => W.admitInitialResultC(implementation, _result(value)))
      val rejectedreview = options.flatMap(value => Vector(checked, phasechecked).map(state =>
        GoalPhaseNativeReview.completedStateC(_result(value).copy(outcome = "accept"), "actual-review", state)))
      Then("neither implementation nor either independent review route accepts repair claims")
      rejectedimplementation.foreach(_ shouldBe a[Consequence.Failure[?]])
      rejectedreview.foreach(_ shouldBe a[Consequence.Failure[?]])
      W.admitInitialResultC(implementation, _result(None)) shouldBe Consequence.unit
    }
  }

  "Fix assessment wire and public DTO projections" should {
    "round-trip absent and valid reports in the unchanged additive result schema" in {
      Given("current results with and without the optional semantic report")
      val absent = _result(None)
      val present = _result()
      When("the internal result codec writes and reads both")
      val absentwire = W.resultCodec.encode(absent)
      val presentwire = W.resultCodec.encode(present)
      Then("absence stays omitted and valid business fields round-trip exactly")
      absentwire.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.goal-phase-native-work-result.v1")
      absentwire.hcursor.downField("payload").downField("fixAssessment").focus shouldBe None
      W.resultCodec.decode(absentwire) shouldBe Right(absent)
      W.resultCodec.decode(presentwire) shouldBe Right(present)
      presentwire.hcursor.downField("payload").downField("fixAssessment").focus.get.asObject.get.keys.toSet shouldBe
        Set("change", "convergence")
    }

    "reject present-null and malformed wire reports instead of decoding absence" in {
      Given("a valid current repair wire result")
      val wire = W.resultCodec.encode(_result())
      val original = wire.hcursor.downField("payload").focus.get
      val invalidreports = Vector(Json.Null, Json.obj(), Json.fromString("HYGIENE"),
        Json.obj("change" -> Json.Null, "convergence" -> Json.Null),
        wire.hcursor.downField("payload").downField("fixAssessment").focus.get.mapObject(_.remove("convergence")))
      When("present invalid values are placed in the optional field")
      val decoded = invalidreports.map(value => W.resultCodec.decode(_payload(wire, original.mapObject(_.add("fixAssessment", value)))))
      Then("every malformed presence rejects without losing the claimed report")
      decoded.foreach(_ shouldBe a[Left[?, ?]])
    }

    "preserve business report fields across actual public egress and strict DTO ingress conversion" in {
      Given("an actual Decision context retaining its repair result and an independent acceptance payload")
      val blocked = _report.copy(convergence = _convergence.copy(status = "BLOCKED"))
      val actual = _result(Some(blocked)).copy(outcome = "decision-needed",
        acceptance = Some(_acceptance("Retain existing acceptance mapping")))
      val assigned = _with_acceptance(_request(false))
      val work = assigned.copy(decisionContext = Some(W.DecisionContext(actual, "ResumeAssignedWork", "Use the selected boundary")))
      When("the public work projection displays that retained requesting result")
      val displayed = NativeWorkflowOperationOutput.goalWork(work).decisionContext.get.requestingResult
      val restored = _take(internalReportC(displayed.fixAssessment))
      Then("the public DTO carries the exact report without counters or execution authority and acceptance remains explicit")
      restored shouldBe actual.fixAssessment
      _take(publicReportC(restored)) shouldBe displayed.fixAssessment
      GoalPhaseAcceptanceBoundary.internalReport(displayed.acceptance) shouldBe actual.acceptance
      displayed.fixAssessment.get.change.affectedLogicalUnits shouldBe Vector("one.validate")
      displayed.fixAssessment.get.convergence.confidence shouldBe "MEDIUM"
      displayed.fixAssessment.get.convergence.status shouldBe "BLOCKED"
      displayed.outcome shouldBe "decision-needed"
      W.admitSemanticResultC(assigned, actual) shouldBe Consequence.unit
    }

    "retain exact repair claims and acceptance through named typed Record and JSON public intake" in {
      Given("a genuine repair result projected by the normal public Decision display")
      val actual = _result().copy(acceptance = Some(_acceptance("Existing acceptance remains explicit")))
      val assigned = _with_acceptance(_request(false))
      val displayed = NativeWorkflowOperationOutput.goalWork(assigned.copy(
        decisionContext = Some(W.DecisionContext(actual, "ResumeAssignedWork", "Retain the actual repair"))))
        .decisionContext.get.requestingResult
      val json = _take(displayed.toRecord().toJsonStringC)
      val wirerecord = _take(new RecordDecoder().json(json))
      val representations: Vector[Any] = Vector(displayed, wirerecord, json)
      val requests = representations.map(value => Request.of(component = "org.simplemodeling.textus.SmWorkflow",
        service = "GoalPhaseService", operation = "submitGoalPhaseWorkResult",
        properties = List(Property("sessionId", "actual-instance", None), Property("result", value, None))))
      When("the normal named business reader and strict internal projection consume each representation")
      val supplied = requests.map(value => _take(NativeWorkflowOperationInput.valueC[V.GoalPhaseWorkResult](value, "result")))
      val restored = supplied.map(value => W.Result(value.outcome, value.summary, value.findings,
        GoalPhaseAcceptanceBoundary.internalReport(value.acceptance), _take(internalReportC(value.fixAssessment)),
        _take(GoalPhaseFixIssues.internalReportC(value.issueReport))))
      val admitted = restored.map(value => W.admitSemanticResultC(assigned, value))
      Then("every business field survives without inventing counter or execution authority")
      supplied shouldBe Vector.fill(3)(displayed)
      restored shouldBe Vector.fill(3)(actual)
      admitted shouldBe Vector.fill(3)(Consequence.unit)
      val reportjson = io.circe.parser.parse(_take(displayed.fixAssessment.get.toRecord().toJsonStringC)).toOption.get
      reportjson.asObject.get.keys.toSet shouldBe Set("change", "convergence")
      displayed.fixAssessment.get.change shouldBe V.GoalPhaseFixChangeAssessment(_change.changeClass, _change.affectedLogicalUnits,
        _change.crossFileLogic, _change.minorBugFix, _change.rationale, _change.requirementChanged, _change.contractChanged)
      displayed.fixAssessment.get.convergence shouldBe V.GoalPhaseFixConvergenceAssessment(
        _convergence.status, _convergence.confidence, _convergence.rationale)
    }

    "reject malformed public report options and nested DTO values" in {
      Given("a valid public DTO report and deliberately malformed nested claims")
      val valid = _take(publicReportC(Some(_report))).get
      val invalid: Vector[Option[V.GoalPhaseFixAssessment]] = Vector(null, Some(null),
        Some(valid.copy(change = null)), Some(valid.copy(convergence = null)),
        Some(valid.copy(change = valid.change.copy(affectedLogicalUnits = null))),
        Some(valid.copy(convergence = valid.convergence.copy(status = "READY"))))
      When("the strict public ingress converter consumes each malformed DTO")
      val converted = invalid.map(internalReportC)
      Then("invalid options and nested values cannot silently become None")
      converted.foreach(_ shouldBe a[Consequence.Failure[?]])
      _take(internalReportC(None)) shouldBe None
      publicReportC(Some(null)) shouldBe a[Consequence.Failure[?]]
    }
  }
}
