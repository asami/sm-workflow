package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration => HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentOrigin}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseAcceptanceBoundary as B
import GoalPhaseNativeWork as S
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Application quality examples with explicit fixture provenance. Native effects,
  * real providers and production independent review are not proved by these tests.
  * @since   Oct. 16, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseAcceptanceBoundarySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _behavior = B.Condition("behavior", "all", true, "Implement selected behavior")
  private val _extra = B.Condition("extra", "all", true, "Additional robustness")
  private val _phase = B.Condition("closure", "phase", true, "Review whole Phase")
  private val _project = B.Project(Vector(_behavior, _extra, _phase))
  private val _adoption = B.Adoption("Selected usage excludes extra robustness", Vector(
    _extra.copy(scope = "step", required = false, description = "Extra robustness remains advisory"),
    B.Condition("adopted", "step", true, "Explicit adopted condition")))
  private val _plain = NativeHostHumanEvidenceFixture.plan.copy(repairLimit = 1)
  private val _plan = take(B.captureC(Some(_project), _plain.copy(acceptance = Some(_adoption))))
  private val _base = NativeHostHumanEvidenceFixture.snapshot
  private val _request = NativeHostHumanEvidenceFixture.work.copy(selectedPlan = _plan)
  private def _report(kind: String = "ImplementStep", status: String = "satisfied"): B.Report = B.Report(
    B.applicable(_plan.effectiveAcceptance.get, kind).map(c => B.Observation(c.identity,
      if (c.required) status else "unsatisfied", if (c.required) "Observed " + c.identity else "Excluded idea")),
    Vector("Consider additional robustness separately"))
  private def _result(outcome: String = "completed", report: B.Report = _report()): S.Result =
    S.Result(outcome, "Specification result", Vector.empty, Some(report))
  private def _configuration(value: Json): ConfigurationValue = value.fold(ConfigurationValue.NullValue,
    ConfigurationValue.BooleanValue.apply, n => ConfigurationValue.NumberValue(n.toBigDecimal.get),
    ConfigurationValue.StringValue.apply, values => ConfigurationValue.ListValue(values.map(_configuration).toList),
    fields => ConfigurationValue.ObjectValue(fields.toMap.view.mapValues(_configuration).toMap))
  private def _resolve(value: Option[ConfigurationValue]): Consequence[Option[B.Project]] = {
    var selected = Option.empty[B.Project]
    val id = SmWorkflowComponent.componentId
    val values = value.map(B.declaration.name -> _).toMap
    val creation = ComponentCreate(RuntimeBindingAdmissionFixture.admit(Subsystem(name = "spec-acceptance", version = None,
      configuration = ResolvedConfiguration(HostConfiguration(values), ConfigurationTrace.empty),
      aliasResolver = AliasResolver.empty, runMode = RunMode.Command)), ComponentOrigin.Builtin,
      Vector(ComponentDescriptor(name = Some(id.name), componentName = Some(id.name), version = Some("0.1.0"),
        schemaVersion = Some(3), componentId = Some(id))))
    val factory = new SmWorkflowConfiguredComponentFactory(Vector(B.declaration), Vector.empty,
      (_, parameters, _) => B.resolveProjectC(parameters).map { value =>
        selected = value
        Vector(SmWorkflowComponent.GoalPhaseStepReviewServiceService)
      })
    factory.primaryFactory.createPrimaryC(creation).map(_ => selected)
  }
  private def _project_json(value: B.Project): Json = value.asJson.mapObject(
    _.add("schemaVersion", Json.fromString(B.schemaVersion)))
  private def _key(state: O.State): O.WorkKey = state.next.asInstanceOf[O.Semantic].key
  private def _review(state: O.State): O.State = {
    val ready = take(state.acceptC(O.SemanticCompleted(_key(state), "Assigned work completed")))
    val snapshot = O.Snapshot(ready.tipSnapshot.head, "d" * 40)
    val observed = take(ready.acceptC(O.CandidateObserved(snapshot)))
    take(observed.acceptC(O.ChecksObserved(snapshot, Vector("focused"), "actual-fixture-check", true)))
  }

  "E1 project resolution and explicit adoption" should {
    "retain defaults in order and replace the whole explicitly adopted condition including exclusion and scope" in {
      Given("spec:goal-phase-acceptance-boundary E1 R1,R2; a typed Component parameter and an explicit adopted Usage Contract")
      val json = _project_json(_project)
      When("the existing Component factory resolves object and JSON forms and Start captures the selected adoption")
      val actual = Vector(_configuration(json), ConfigurationValue.StringValue(json.noSpaces)).map(v =>
        take(B.captureC(take(_resolve(Some(v))), _plain.copy(acceptance = Some(_adoption)))))
      Then("untouched defaults retain full meaning, replacements win without strengthening, and new identities append")
      actual.foreach { plan =>
        plan.effectiveAcceptance.get shouldBe B.Effective(Some(_adoption.usage), Vector(
          B.EffectiveCondition("behavior", "all", true, _behavior.description, "project"),
          B.EffectiveCondition("extra", "step", false, "Extra robustness remains advisory", "adopted"),
          B.EffectiveCondition("closure", "phase", true, _phase.description, "project"),
          B.EffectiveCondition("adopted", "step", true, "Explicit adopted condition", "adopted")))
      }
      ThreeWorkflowHostParameters.declarations.count(_.name == B.declaration.name) shouldBe 1
      Given("explicitly configured empty project conditions")
      When("Start captures that configuration without an adoption")
      val empty = take(B.captureC(Some(B.Project(Vector.empty)), _plain))
      Then("an explicit empty effective boundary is retained and still requires an empty report")
      empty.effectiveAcceptance shouldBe Some(B.Effective(None, Vector.empty))
      B.admitC(empty, "ImplementStep", S.Result("completed", "Done", Vector.empty)).isSuccess shouldBe false
      B.admitC(empty, "ImplementStep", S.Result("completed", "Done", Vector.empty,
        Some(B.Report(Vector.empty, Vector.empty)))).isSuccess shouldBe true
    }
  }

  "E2 malformed declarations and scoped reports" should {
    "reject duplicate contradictory malformed and null configuration or adoption before capture" in {
      val conditions = Vector(_behavior.copy(identity = " padded "), _behavior.copy(identity = "x" * 129),
        _behavior.copy(scope = "unknown"), _behavior.copy(description = " "),
        _behavior.copy(description = "a\u0000b"), _behavior.copy(description = "あ" * 2731), null)
      val projects = Vector(null, B.Project(null), B.Project(Vector(_behavior, _behavior)),
        B.Project(Vector.tabulate(65)(i => _behavior.copy(identity = "condition" + i)))) ++ conditions.map(c => B.Project(Vector(c)))
      projects.foreach { project =>
        Given("spec:goal-phase-acceptance-boundary E2 R1,R2; a malformed typed project declaration")
        When("Start validates the project before capture")
        val result = B.captureC(Some(project), _plain)
        Then("the declaration fails without inventing conditions")
        result.isSuccess shouldBe false
      }
      val adoptions = Vector(null, _adoption.copy(usage = null), _adoption.copy(usage = " "),
        _adoption.copy(usage = "x" * 8193), _adoption.copy(conditions = null),
        _adoption.copy(conditions = Vector(_extra, _extra.copy(required = false)))) ++
        conditions.map(c => _adoption.copy(conditions = Vector(c)))
      adoptions.foreach { adoption =>
        Given("a duplicate conflicting null or otherwise invalid explicit adoption")
        When("the selected Plan is validated and captured")
        val result = B.captureC(Some(_project), _plain.copy(acceptance = Some(adoption)))
        Then("planning must settle the declaration rather than unioning stronger conditions")
        result.isSuccess shouldBe false
      }
      Given("missing or wrong project schema, invalid JSON and invalid typed option containers")
      val json = _project_json(_project)
      val duplicateconfig = _project_json(B.Project(Vector(_behavior, _behavior.copy(required = false))))
      val unknownscopeconfig = _project_json(B.Project(Vector(_behavior.copy(scope = "unknown"))))
      When("the existing decoder and typed Plan admission consume those values")
      val failures = Vector(_resolve(Some(ConfigurationValue.StringValue("not-json"))),
        _resolve(Some(_configuration(json.mapObject(_.remove("schemaVersion"))))),
        _resolve(Some(_configuration(json.mapObject(_.add("schemaVersion", Json.fromString("other")))))),
        _resolve(Some(ConfigurationValue.BooleanValue(true))), B.decodeProjectC(null), B.resolveProjectC(null),
        _resolve(Some(_configuration(duplicateconfig))), _resolve(Some(_configuration(unknownscopeconfig))),
        B.captureC(null, _plain), B.captureC(None, _plain.copy(acceptance = null)),
        B.captureC(None, _plain.copy(effectiveAcceptance = null)), B.captureC(None, _plan),
        B.validateEffectiveC(B.Effective(None, Vector(B.EffectiveCondition("valid", "all", true, "Valid", "unknown")))))
      Then("all consumed invalid inputs fail and a pre-captured Start Plan is refused")
      failures.foreach(_.isSuccess shouldBe false)
    }

    "reject missing required unknown inapplicable duplicate or malformed observations and invalid advisories" in {
      val good = _report()
      val malformed = Vector(null, good.copy(conditions = null), good.copy(advisories = null),
        good.copy(conditions = good.conditions :+ good.conditions.head),
        good.copy(conditions = good.conditions.filterNot(_.identity == "behavior")),
        good.copy(conditions = good.conditions :+ B.Observation("unknown", "satisfied", "Unknown idea")),
        good.copy(conditions = good.conditions :+ B.Observation("closure", "satisfied", "Phase only")),
        good.copy(conditions = good.conditions.updated(0, good.conditions.head.copy(status = "unknown"))),
        good.copy(conditions = good.conditions.updated(0, good.conditions.head.copy(detail = " "))),
        good.copy(conditions = good.conditions.updated(0, good.conditions.head.copy(detail = "x\u0000y"))),
        good.copy(conditions = good.conditions.updated(0, good.conditions.head.copy(detail = "あ" * 2731))),
        good.copy(conditions = Vector(null)), good.copy(advisories = Vector(null)),
        good.copy(advisories = Vector(" ")), good.copy(advisories = Vector("x" * 8193)),
        good.copy(advisories = Vector.fill(129)("idea")),
        good.copy(conditions = Vector.tabulate(65)(i => B.Observation("c" + i, "satisfied", "Detail"))))
      malformed.foreach { report =>
        Given("spec:goal-phase-acceptance-boundary E2 R3; a configured assignment and malformed scoped report")
        When("shared typed quality admission consumes that report")
        val result = B.admitC(_plan, "ImplementStep", _result(report = report))
        Then("invalid required coverage, shape, status, scope or bounds cannot complete the assignment")
        result.isSuccess shouldBe false
      }
      Given("a configured assignment with absent report or a null report option")
      When("shared quality admission consumes those missing forms")
      val failures = Vector(B.admitC(_plan, "ImplementStep", _result().copy(acceptance = None)),
        B.admitC(_plan, "ImplementStep", _result().copy(acceptance = null)))
      Then("the configured report requirement cannot be bypassed")
      failures.foreach(_.isSuccess shouldBe false)
    }
  }

  "E3 saved and generated value codecs" should {
    "retain adoption effective conditions reports and advisories through saved Start Work Result and public Record JSON" in {
      Given("spec:goal-phase-acceptance-boundary E3 R2,R5; a captured Plan and actual typed report")
      val payload = GoalPhaseNativeRun.Payload("participant", "/owned/root", "main", _plan, _base)
      val result = _result()
      val publicwork = NativeWorkflowOperationOutput.goalWork(_request)
      val publicresult = V.GoalPhaseWorkResult(result.outcome, result.summary, result.findings, B.publicReport(result.acceptance))
      When("saved codecs and normal strict generated Record readers consume their own structured JSON")
      val start = GoalPhaseNativeRun.codec.decode(GoalPhaseNativeRun.codec.encode(payload))
      val work = S.inputCodec.decode(S.inputCodec.encode(_request))
      val completed = S.resultCodec.decode(S.resultCodec.encode(result))
      val wireplan = take(V.GoalPhaseOrderedPlan.createC(take(new RecordDecoder().json(take(publicwork.selectedPlan.toRecord().toJsonStringC)))))
      val wireresult = take(V.GoalPhaseWorkResult.createC(take(new RecordDecoder().json(take(publicresult.toRecord().toJsonStringC)))))
      Then("each retained value includes complete scopes, provenance, explicit false and report details")
      start shouldBe Right(payload)
      _plan.asJson.as[O.Plan] shouldBe Right(_plan)
      work shouldBe Right(_request)
      completed shouldBe Right(result)
      B.internalAdoption(wireplan.acceptance) shouldBe _plan.acceptance
      B.internalEffective(wireplan.effectiveAcceptance) shouldBe _plan.effectiveAcceptance
      B.internalReport(wireresult.acceptance) shouldBe result.acceptance
      Given("invalid nested nulls in pure typed public/internal projections")
      val invalid = Some(B.Report(Vector(null), null))
      When("the boundary owner projects them without converting them into valid defaults")
      val roundtrip = B.internalReport(B.publicReport(invalid))
      Then("nulls are retained for typed validation and never throw during projection")
      roundtrip shouldBe invalid
      B.validateReportC(roundtrip.get).isSuccess shouldBe false
      B.publicReport(null) shouldBe null
      val invalidadoption = Some(B.Adoption(null, Vector(null)))
      val invalideffective = Some(B.Effective(null, Vector(null)))
      B.internalAdoption(B.publicAdoption(invalidadoption)) shouldBe invalidadoption
      B.internalEffective(B.publicEffective(invalideffective)) shouldBe invalideffective
    }
  }

  "E4 implementation and assigned repair completion" should {
    Vector("ImplementStep", "RepairStep").foreach { kind =>
      s"require satisfied applicable conditions while retaining excluded observations and advisories for $kind" in {
        Given("spec:goal-phase-acceptance-boundary E4 R3,R4; a saved configured " + kind + " assignment")
        val request = if (kind == "ImplementStep") _request else _request.copy(kind = kind,
          repairDirective = Some(O.RepairDirective("Repair selected failure", Vector("Missing behavior"))),
          review = Some(GoalPhaseNativeReview.Context(Vector("one.scala"), "Fixture diff", Vector.empty)))
        When("normal semantic admission consumes satisfied required conditions and excluded unsatisfied observations")
        val accepted = S.admitSemanticResultC(request, _result())
        Then("excluded ideas remain advisory without blocking semantic completion or becoming findings")
        accepted.isSuccess shouldBe true
        Vector("unsatisfied", "not-evaluated").foreach { status =>
          Given("a required condition reported " + status + " for the same assignment")
          When("the result claims completed")
          val result = S.admitSemanticResultC(request, _result(report = _report(status = status)))
          Then("completion fails before a review or state transition can be inferred")
          result.isSuccess shouldBe false
        }
        Given("honest required not-evaluated observations and an explicit decision-needed result")
        When("semantic admission consumes that result")
        val decision = S.admitSemanticResultC(request, _result("decision-needed", _report(status = "not-evaluated")))
        Then("the existing explicit Human route remains available")
        decision.isSuccess shouldBe true
        Given("an otherwise valid quality report with malformed existing summary or findings")
        val invalid = Vector(_result().copy(summary = "x\u0000y"), _result().copy(summary = "x" * 16385),
          _result("decision-needed").copy(findings = Vector.fill(129)("Finding")),
          _result("decision-needed").copy(findings = Vector("x" * 16385)))
        When("the owning semantic result admission validates the existing bounded fields")
        val failed = invalid.map(S.admitSemanticResultC(request, _))
        Then("the quality report does not bypass existing result validity")
        failed.foreach(_.isSuccess shouldBe false)
      }
    }
  }

  "E5 independent Step and Phase review semantics" should {
    "bound repair to ordered unsatisfied required details and preserve the existing repair allowance" in {
      Given("spec:goal-phase-acceptance-boundary E5 R3,R4; an independently admitted fixture Step review frontier")
      val controller = _review(take(O.startC(_plan, _base)))
      val report = _report("ReviewStep").copy(conditions = _report("ReviewStep").conditions.map {
        case o if o.identity == "behavior" => o.copy(status = "unsatisfied", detail = "Missing selected behavior")
        case o if o.identity == "adopted" => o.copy(status = "not-evaluated")
        case o => o
      })
      val result = S.Result("repair", "Repair only known required failure", Vector("Missing selected behavior"), Some(report))
      When("owning review admission consumes the known required failure even while another required observation is not evaluated")
      val repaired = take(GoalPhaseNativeReview.completedStateC(result, "fixture-independent-review", controller))
      Then("only the actual required detail becomes the existing bounded repair directive")
      repaired.stage shouldBe O.Stage.Repair
      repaired.repairs shouldBe 1
      repaired.plan shouldBe _plan
      repaired.repairDirective.get.findings shouldBe result.findings
      val invalid = Vector(result.copy(findings = Vector("Excluded idea")),
        result.copy(acceptance = Some(_report("ReviewStep"))),
        result.copy(findings = result.findings :+ "Unselected proposal"))
      invalid.foreach { value =>
        Given("an independent fixture result proposing excluded, mismatched or additional repair")
        When("the same owning review frontier consumes that result")
        val failed = GoalPhaseNativeReview.completedStateC(value, "fixture-independent-review", controller)
        Then("the proposal cannot change the frontier or allocate repair")
        failed.isSuccess shouldBe false
        controller.repairs shouldBe 0
        controller.stage shouldBe O.Stage.Review
      }
    }

    "apply phase conditions only at ReviewPhase and retain accept or explicit Human dispositions" in {
      Given("spec:goal-phase-acceptance-boundary E5 R1,R3,R4; actual pure controller Step review followed by Phase review")
      val step = _review(take(O.startC(_plan, _base)))
      When("independent Step acceptance satisfies only all and step conditions")
      val accepted = take(GoalPhaseNativeReview.completedStateC(_result("accept", _report("ReviewStep")), "step-review", step))
      val intended = take(accepted.acceptC(O.ClosureIntentSaved(accepted.candidate.get, "fixture-commit")))
      val closed = take(intended.acceptC(O.StepCommitObserved("fixture-commit", O.Commit("c" * 40, _base.head, "d" * 40))))
      val snapshot = O.Snapshot("c" * 40, "d" * 40)
      val observed = take(closed.acceptC(O.CandidateObserved(snapshot)))
      val phase = take(observed.acceptC(O.ChecksObserved(snapshot, Vector("focused"), "phase-check", true)))
      Then("Phase review selects all and phase conditions with the existing closed Step intact")
      B.applicable(_plan.effectiveAcceptance.get, "ReviewStep").map(_.identity) shouldBe Vector("behavior", "extra", "adopted")
      B.applicable(_plan.effectiveAcceptance.get, "ReviewPhase").map(_.identity) shouldBe Vector("behavior", "closure")
      phase.completed.size shouldBe 1
      Given("a Phase report whose required closure is not evaluated")
      val report = _report("ReviewPhase").copy(conditions = _report("ReviewPhase").conditions.map(o =>
        if (o.identity == "closure") o.copy(status = "not-evaluated") else o))
      When("the Phase review claims acceptance or explicitly requests a decision")
      val failure = GoalPhaseNativeReview.completedStateC(_result("accept", report), "phase-review", phase)
      val human = take(GoalPhaseNativeReview.completedStateC(_result("decision-needed", report), "phase-review", phase))
      val success = take(GoalPhaseNativeReview.completedStateC(_result("accept", _report("ReviewPhase")), "phase-review", phase))
      Then("not-evaluated prevents acceptance, permits Human, and satisfied conditions reach only existing PhaseClosing")
      failure.isSuccess shouldBe false
      human.stage shouldBe O.Stage.Decision
      human.repairs shouldBe 0
      success.stage shouldBe O.Stage.PhaseClosing
    }
  }

  "E6 unconfigured compatibility" should {
    "retain absence without a forged report and refuse an adopted but uncaptured bypass" in {
      Given("spec:goal-phase-acceptance-boundary E6 R2,R3; an existing Plan with no project or adoption")
      When("Start captures absence and normal initial admission receives an unchanged result")
      val plan = take(B.captureC(take(_resolve(None)), _plain))
      val request = _request.copy(selectedPlan = plan)
      val result = S.Result("completed", "Assigned work completed", Vector.empty)
      Then("absence remains absent in saved codecs and no quality boundary is claimed")
      plan.effectiveAcceptance shouldBe None
      plan.acceptance shouldBe None
      plan.asJson.hcursor.downField("acceptance").focus shouldBe None
      plan.asJson.hcursor.downField("effectiveAcceptance").focus shouldBe None
      S.resultCodec.encode(result).hcursor.downField("payload").downField("acceptance").focus shouldBe None
      S.admitInitialResultC(request, result).isSuccess shouldBe true
      Given("a supplied report for that unconfigured Plan or an adopted Plan with no Start capture")
      When("typed quality admission consumes either attempted claim")
      val forged = S.admitInitialResultC(request, _result())
      val uncaptured = S.admitInitialResultC(request.copy(selectedPlan = _plain.copy(acceptance = Some(_adoption))), result)
      Then("neither report nor missing capture invents a boundary")
      forged.isSuccess shouldBe false
      uncaptured.isSuccess shouldBe false
    }
  }
}
