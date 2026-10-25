package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionModel as CAM
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseScopedQualityAdmission as A

/** Typed input fixtures exercise the actual adapter and CAM. These examples
  * do not execute a provider or establish normal runtime installation.
  * @since   Oct. 17, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseScopedQualityAdmissionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _input = A.Selection("review-1", 3L, "step-1", "candidate-2",
    Vector("src/Selected.scala", "docs/selected.md"), Vector("SelectedModel"),
    Vector("semantic-consistency"))
  private def _result(input: A.Selection = _input): A.Result =
    A.Result(input.reviewId, input.revision, input.subjectId, input.subjectRevision,
      "completed", input.checks.map(check => A.CheckResult(check, "covered",
        if (check == "cml-contract") Vector.empty else input.paths, input.modelSubjects, Vector.empty)),
      Vector.empty, "fixture-provider", Some("car-review.semantic-consistency"), Some("fixture-model"),
      "cbd-scoped-quality-v1")
  private def _finding(severity: String, check: String = "semantic-consistency"): A.Finding =
    A.Finding("finding-" + severity + "-" + check, check, severity,
      if (check == "cml-contract") None else Some(_input.paths.head), Some(_input.modelSubjects.head),
      "Selected finding detail")
  private def _take(value: Consequence[A.Evaluation]): A.Evaluation = value.toOption.get
  private def _failure_message(value: Consequence[Unit]): String = value match {
    case Consequence.Failure(conclusion) => conclusion.displayMessage
    case _ => fail("Expected scoped quality admission failure")
  }

  // Ordinary CML Record fixtures, never actual provider execution evidence.
  private val _check_record = Record.createFull(Vector(
    "checkId" -> "semantic-consistency", "status" -> "covered",
    "paths" -> _input.paths, "modelSubjects" -> _input.modelSubjects))
  private val _result_record = Record.createFull(Vector(
    "reviewId" -> "review-1", "revision" -> 3L, "subjectId" -> "step-1",
    "subjectRevision" -> "candidate-2", "status" -> "completed",
    "checks" -> Vector(_check_record), "providerId" -> "fixture-provider",
    "purpose" -> "car-review.semantic-consistency", "ruleRevision" -> "cbd-scoped-quality-v1"))
  private val _finding_record = Record.createFull(Vector(
    "identity" -> "finding-blocker", "checkId" -> "semantic-consistency", "severity" -> "blocker",
    "path" -> _input.paths.head, "message" -> "Selected finding detail"))
  private def _replace(record: Record, name: String, value: Any): Record =
    Record(record.fields.filterNot(_.key == name) ++ Record.createFull(Vector(name -> value)).fields)
  private def _omit(record: Record, name: String): Record = Record(record.fields.filterNot(_.key == name))
  private def _decode_evaluate_c(record: Record, input: A.Selection = _input): Consequence[A.Evaluation] =
    A.decodeResultC(record).flatMap(result => A.evaluateC(input, Some(result)))

  "C1 selected typed requirements and evidence" should {
    "admit a clean selected semantic result through the existing CAM" in {
      Given("spec:goal-phase-scoped-quality-admission C1 R1,R3; a selected semantic check and clean internal fixture result")
      val result = _result()
      When("the actual adapter submits the internal selection and evidence to CAM and checks accept")
      val evaluation = _take(A.evaluateC(_input, Some(result)))
      val admitted = A.requireAdmittedC(evaluation, "accept")
      Then("CAM admits the ordinary candidate and evidence identity even with no findings")
      evaluation.admission.result shouldBe CAM.AdmissionResult.Admitted(
        CAM.CandidateRef("step-1", "candidate-2"), Vector("review-1:1"))
      evaluation.findings shouldBe Vector.empty
      evaluation.blockingFindings shouldBe Vector.empty
      evaluation.result shouldBe Some(result)
      admitted shouldBe Consequence.unit
    }

    "use only the caller's selected checks and preserve declared scopes in missing requirements" in {
      Given("spec:goal-phase-scoped-quality-admission C1 R1; selected integration and deterministic CML checks")
      val input = _input.copy(checks = Vector("integration", "cml-contract"))
      When("the actual CAM evaluates the request without evidence")
      val evaluation = _take(A.evaluateC(input, None))
      Then("the requirements retain selection order, kind, evidence type and exact application scope")
      evaluation.admission.gaps.map(_.requirement.identity) shouldBe input.checks
      evaluation.admission.gaps.map(_.requirement.kind) shouldBe
        Vector(CAM.RequirementKind.Semantic, CAM.RequirementKind.Deterministic)
      evaluation.admission.gaps.map(_.requirement.evidenceType).distinct shouldBe Vector("ScopedQualityReviewResult")
      evaluation.admission.gaps.map(_.requirement.scope.typeIdentity).distinct shouldBe
        Vector("sm-workflow.scoped-quality-scope.v1")
      evaluation.admission.gaps.map(_.requirement.scope.value) shouldBe Vector(
        A.Scope("integration", input.paths, input.modelSubjects), A.Scope("cml-contract", Vector.empty, input.modelSubjects))
    }
  }

  "C2 truthful coverage and provenance gaps" should {
    "leave genuine missing evidence and absent checks pending for every outcome" in {
      Given("spec:goal-phase-scoped-quality-admission C2 R1,R2; an absent result or selected check")
      Vector(None, Some(_result().copy(checks = Vector.empty))).foreach { result =>
        Given("spec:goal-phase-scoped-quality-admission C2 R1,R2; absent result or selected check")
        When("the actual adapter and CAM evaluate the missing evidence")
        val evaluation = _take(A.evaluateC(_input, result))
        Then("the selected semantic requirement has a genuine Missing gap and cannot pass any disposition")
        evaluation.admission.gaps.map(_.requirement.identity) shouldBe _input.checks
        evaluation.admission.gaps.head.defects shouldBe Vector(CAM.EvidenceDefect.Missing)
        Vector("accept", "repair", "decision-needed").foreach { outcome =>
          A.requireAdmittedC(evaluation, outcome) shouldBe a[Consequence.Failure[?]]
        }
      }
    }

    "retain CAM WrongTarget for returned subject mismatches and Stale for review references" in {
      Given("spec:goal-phase-scoped-quality-admission C2 R2; ordinary target and review reference mismatches")
      val original = _result()
      val cases = Vector(
        original.copy(subjectId = "other-step") -> CAM.EvidenceDefect.WrongTarget,
        original.copy(subjectRevision = "other-candidate") -> CAM.EvidenceDefect.WrongTarget,
        original.copy(reviewId = "other-review") -> CAM.EvidenceDefect.Stale,
        original.copy(revision = 4L) -> CAM.EvidenceDefect.Stale,
        original.copy(ruleRevision = "other-rule") -> CAM.EvidenceDefect.Stale)
      cases.foreach { case (result, defect) =>
        Given("spec:goal-phase-scoped-quality-admission C2 R2; a typed result with a mismatched ordinary target or review reference")
        When("the actual CAM correlates the returned target and applies freshness policy")
        val evaluation = _take(A.evaluateC(_input, Some(result)))
        Then("the mismatch remains its typed CAM defect and prevents accept")
        evaluation.admission.target shouldBe CAM.CandidateRef(_input.subjectId, _input.subjectRevision)
        evaluation.admission.gaps.head.defects shouldBe Vector(defect)
        evaluation.admission.acceptedEvidence shouldBe Vector.empty
        A.requireAdmittedC(evaluation, "accept") shouldBe a[Consequence.Failure[?]]
      }
    }

    "keep failed, unsupported, limited, partial and unattributed results pending" in {
      Given("spec:goal-phase-scoped-quality-admission C2 R2; incomplete or unattributed internal results")
      val original = _result()
      val check = original.checks.head
      val cases = Vector(
        original.copy(status = "failed"),
        original.copy(status = "unavailable"),
        original.copy(checks = Vector(check.copy(status = "failed"))),
        original.copy(checks = Vector(check.copy(status = "unsupported"))),
        original.copy(checks = Vector(check.copy(paths = check.paths.take(1)))),
        original.copy(checks = Vector(check.copy(modelSubjects = Vector.empty))),
        original.copy(checks = Vector(check.copy(limitations = Vector("Selected check unavailable")))),
        original.copy(providerId = " "),
        original.copy(purpose = None),
        original.copy(purpose = Some("other-purpose")))
      cases.foreach { result =>
        Given("spec:goal-phase-scoped-quality-admission C2 R2; failed, unsupported, incomplete or unattributed typed evidence")
        When("the actual CAM applies the adapter's coverage policy")
        val evaluation = _take(A.evaluateC(_input, Some(result)))
        Then("coverage remains insufficient regardless of empty findings")
        evaluation.admission.gaps.head.defects shouldBe Vector(CAM.EvidenceDefect.InsufficientCoverage)
        evaluation.findings shouldBe Vector.empty
        A.requireAdmittedC(evaluation, "accept") shouldBe a[Consequence.Failure[?]]
      }
    }

    "leave missing CML model scope pending and never invent it from paths" in {
      Given("spec:goal-phase-scoped-quality-admission C2 R1,R2; CML selected with paths but no model subjects")
      val input = _input.copy(checks = Vector("cml-contract"), modelSubjects = Vector.empty)
      val result = _result(input).copy(purpose = None, model = None)
      When("the actual CAM evaluates nominally covered deterministic evidence")
      val evaluation = _take(A.evaluateC(input, Some(result)))
      Then("the empty declared CML scope remains explicit and insufficient")
      evaluation.admission.gaps.head.requirement.scope.value shouldBe A.Scope("cml-contract", Vector.empty, Vector.empty)
      evaluation.admission.gaps.head.defects shouldBe Vector(CAM.EvidenceDefect.InsufficientCoverage)
      A.requireAdmittedC(evaluation, "decision-needed") shouldBe a[Consequence.Failure[?]]
    }
  }

  "C3 findings and existing dispositions" should {
    "refuse accept for covered deterministic and semantic blockers while retaining typed findings" in {
      Given("spec:goal-phase-scoped-quality-admission C3 R1,R3; covered deterministic and semantic checks with actual typed blocker findings")
      val input = _input.copy(checks = Vector("cml-contract", "semantic-consistency"))
      val findings = Vector(_finding("blocker", "cml-contract"), _finding("blocker"))
      val result = _result(input).copy(findings = findings)
      When("the actual adapter and CAM establish coverage before the disposition gate")
      val evaluation = _take(A.evaluateC(input, Some(result)))
      Then("covered evidence retains both blockers, prevents accept, and permits existing repair or Human handling")
      evaluation.admission.gaps shouldBe Vector.empty
      evaluation.admission.acceptedEvidence shouldBe Vector("review-1:1", "review-1:2")
      evaluation.findings shouldBe findings
      evaluation.blockingFindings shouldBe findings
      A.requireAdmittedC(evaluation, "accept") shouldBe a[Consequence.Failure[?]]
      A.requireAdmittedC(evaluation, "repair") shouldBe Consequence.unit
      A.requireAdmittedC(evaluation, "decision-needed") shouldBe Consequence.unit
    }

    "admit deterministic coverage without requiring semantic purpose or model" in {
      Given("spec:goal-phase-scoped-quality-admission C3 R1,R2; an explicitly selected deterministic model subject")
      val input = _input.copy(checks = Vector("cml-contract"))
      val result = _result(input).copy(providerId = "cbd-scoped-quality", purpose = None, model = None)
      When("the actual CAM evaluates the complete deterministic scope")
      val evaluation = _take(A.evaluateC(input, Some(result)))
      Then("bounded CML coverage admits without fabricating semantic execution")
      evaluation.admission.gaps shouldBe Vector.empty
      A.requireAdmittedC(evaluation, "accept") shouldBe Consequence.unit
    }
  }

  "C4 retained advisories and invalid typed data" should {
    "retain hygiene and development candidates without converting them to blockers" in {
      Given("spec:goal-phase-scoped-quality-admission C4 R3; clean covered evidence with typed nonblocking findings")
      val findings = Vector(_finding("hygiene"), _finding("development-candidate"))
      When("the actual adapter and CAM evaluate evidence and the closed disposition vocabulary")
      val evaluation = _take(A.evaluateC(_input, Some(_result().copy(findings = findings))))
      Then("nonblocking findings survive acceptance and unrecognized outcomes fail")
      evaluation.findings shouldBe findings
      evaluation.blockingFindings shouldBe Vector.empty
      A.requireAdmittedC(evaluation, "accept") shouldBe Consequence.unit
      Vector("completed", "PASS", "", null).foreach { outcome =>
        A.requireAdmittedC(evaluation, outcome) shouldBe a[Consequence.Failure[?]]
      }
    }

    "reject empty, duplicate, unknown or malformed selections and result options" in {
      Given("spec:goal-phase-scoped-quality-admission C4 R1,R2; malformed internal selections")
      val cases = Vector(null, _input.copy(reviewId = ""), _input.copy(revision = 0L),
        _input.copy(subjectId = ""), _input.copy(subjectRevision = null),
        _input.copy(paths = Vector.empty, modelSubjects = Vector.empty), _input.copy(paths = null),
        _input.copy(modelSubjects = Vector(null)), _input.copy(checks = Vector.empty),
        _input.copy(checks = Vector("semantic-consistency", "semantic-consistency")),
        _input.copy(checks = Vector("unknown")), _input.copy(checks = Vector(null)))
      cases.foreach { input =>
        Given("spec:goal-phase-scoped-quality-admission C4 R1,R2; malformed typed request target or selection")
        When("the actual adapter receives the invalid input")
        val evaluated = A.evaluateC(input, None)
        Then("typed input failure prevents an empty or invented requirement set")
        evaluated shouldBe a[Consequence.Failure[?]]
      }
      Vector(null, Some(null)).foreach { result =>
        Given("spec:goal-phase-scoped-quality-admission C4 R2; a null result option or null contained result")
        When("the actual adapter receives the invalid option")
        val evaluated = A.evaluateC(_input, result)
        Then("null remains an input error rather than missing evidence")
        evaluated shouldBe a[Consequence.Failure[?]]
      }
    }

    "reject duplicate, extra, unknown and malformed result fields without selecting one entry" in {
      Given("spec:goal-phase-scoped-quality-admission C4 R1,R2; malformed internal result projections")
      val original = _result()
      val check = original.checks.head
      val finding = _finding("blocker")
      val cases = Vector(
        original.copy(checks = Vector(check, check)),
        original.copy(checks = Vector(check, check.copy(checkId = "integration"))),
        original.copy(checks = Vector(check.copy(checkId = "unknown"))),
        original.copy(checks = Vector(check.copy(status = "PASS"))),
        original.copy(checks = Vector(check.copy(status = null))),
        original.copy(checks = Vector(null)), original.copy(checks = null),
        original.copy(checks = Vector(check.copy(paths = Vector(null)))),
        original.copy(checks = Vector(check.copy(limitations = null))),
        original.copy(status = "unknown"), original.copy(status = null),
        original.copy(purpose = null), original.copy(model = Some(null)),
        original.copy(providerId = null), original.copy(findings = null),
        original.copy(findings = Vector(null)),
        original.copy(findings = Vector(finding.copy(severity = "warning"))),
        original.copy(findings = Vector(finding.copy(severity = null))),
        original.copy(findings = Vector(finding.copy(checkId = "integration"))),
        original.copy(findings = Vector(finding.copy(path = Some("outside.scala")))),
        original.copy(findings = Vector(finding.copy(modelSubject = Some("OtherModel")))),
        original.copy(findings = Vector(finding.copy(path = null))),
        original.copy(findings = Vector(finding.copy(path = None, modelSubject = None))))
      cases.foreach { result =>
        Given("spec:goal-phase-scoped-quality-admission C4 R1,R2; duplicate, extra, unknown or malformed typed result data")
        When("the actual adapter receives the invalid result")
        val evaluated = A.evaluateC(_input, Some(result))
        Then("typed input failure prevents silent selection, dropped findings or manufactured coverage")
        evaluated shouldBe a[Consequence.Failure[?]]
      }
    }
  }

  "C6 bounded typed failure diagnostics" should {
    "retain unavailable and unsupported causes after actual CAM refusal" in {
      Given("spec:goal-phase-scoped-quality-admission C6 R2,R3; an actual typed unavailable result with an unsupported selected check")
      val original = _result()
      val result = original.copy(status = "unavailable", checks = Vector(original.checks.head.copy(
        status = "unsupported", limitations = Vector("ai.spi.unavailable"))))
      When("evaluateC retains the result and requireAdmittedC rejects its CAM coverage gap")
      val evaluation = _take(A.evaluateC(_input, Some(result)))
      val rejected = A.requireAdmittedC(evaluation, "accept")
      Then("the real failure keeps the exact CAM prefix and exposes only the fixed unavailable cause")
      evaluation.result shouldBe Some(result)
      evaluation.admission.gaps.head.defects shouldBe Vector(CAM.EvidenceDefect.InsufficientCoverage)
      rejected shouldBe a[Consequence.Failure[?]]
      _failure_message(rejected) should include (
        "Scoped quality admission pending: semantic-consistency:InsufficientCoverage; resultStatus=unavailable; checks=semantic-consistency:unsupported:limitations=ai.spi.unavailable")
    }

    "retain failed execution causes without changing any disposition gate" in {
      Given("spec:goal-phase-scoped-quality-admission C6 R2,R3; a typed failed result and selected check carry the known execution-failed code")
      val original = _result()
      val result = original.copy(status = "failed", checks = Vector(original.checks.head.copy(
        status = "failed", limitations = Vector("ai.execution.failed"))))
      Vector("accept", "repair", "decision-needed").foreach { outcome =>
        When("the actual adapter evaluates the failed result and the disposition requires admission")
        val evaluation = _take(A.evaluateC(_input, Some(result)))
        val rejected = A.requireAdmittedC(evaluation, outcome)
        Then("every outcome still fails and the actual typed execution cause survives")
        evaluation.admission.gaps.head.defects shouldBe Vector(CAM.EvidenceDefect.InsufficientCoverage)
        rejected shouldBe a[Consequence.Failure[?]]
        _failure_message(rejected) should include (
          "Scoped quality admission pending: semantic-consistency:InsufficientCoverage; resultStatus=failed; checks=semantic-consistency:failed:limitations=ai.execution.failed")
      }
    }

    "redact unknown control and source-like limitations and all other payload fields" in {
      Given("spec:goal-phase-scoped-quality-admission C6 R3; unknown limitations and ordinary result fields contain private payload")
      val limitations = Vector("unknown-private-cause", "private\ncontrol\ttext", "object PrivateSource { val secret = 42 }")
      val original = _result()
      val result = original.copy(status = "failed", providerId = "private-provider", model = Some("private-model"),
        checks = Vector(original.checks.head.copy(status = "failed", limitations = limitations)),
        findings = Vector(_finding("blocker")))
      When("the actual typed result goes through evaluateC and the failed admission diagnostic")
      val evaluation = _take(A.evaluateC(_input, Some(result)))
      val rejected = A.requireAdmittedC(evaluation, "accept")
      Then("unknown limitations remain coverage failures and are replaced by a single fixed marker without payload disclosure")
      evaluation.admission.gaps.head.defects shouldBe Vector(CAM.EvidenceDefect.InsufficientCoverage)
      rejected shouldBe a[Consequence.Failure[?]]
      val message = _failure_message(rejected)
      message should include (
        "Scoped quality admission pending: semantic-consistency:InsufficientCoverage; resultStatus=failed; checks=semantic-consistency:failed:limitations=redacted")
      (limitations ++ Vector(result.providerId, result.model.get, result.subjectId, result.subjectRevision,
        result.reviewId, result.ruleRevision, result.purpose.get) ++ _input.paths ++ _input.modelSubjects ++
        result.findings.flatMap(finding => Vector(finding.identity, finding.message))).foreach { payload =>
        message should not include (payload)
      }
    }

    "bound diagnostics to five selected checks and unique sorted known limitation codes" in {
      Given("spec:goal-phase-scoped-quality-admission C6 R1,R3; all five selected checks return duplicated known limitation codes")
      val input = _input.copy(checks = Vector("cml-contract", "semantic-consistency", "implementation-quality", "excessive-checks", "integration"))
      val codes = Vector("ai.spi.unavailable", "ai.response.invalid", "ai.provenance.unavailable",
        "ai.purpose-or-provider.unavailable", "ai.execution.failed")
      val original = _result(input)
      val result = original.copy(status = "unavailable", checks = original.checks.reverse.map(check =>
        check.copy(status = "unsupported", limitations = codes ++ codes)))
      When("the actual CAM refuses all five checks and admission formats the retained typed causes")
      val evaluation = _take(A.evaluateC(input, Some(result)))
      val rejected = A.requireAdmittedC(evaluation, "accept")
      Then("the original selection-order CAM prefix remains and the appended fixed checks and codes are unique and sorted")
      evaluation.admission.gaps.map(_.requirement.identity) shouldBe input.checks
      evaluation.admission.gaps.foreach(_.defects shouldBe Vector(CAM.EvidenceDefect.InsufficientCoverage))
      rejected shouldBe a[Consequence.Failure[?]]
      val prefix = input.checks.map(_ + ":InsufficientCoverage").mkString(";")
      val diagnostics = input.checks.sorted.map(_ + ":unsupported:limitations=" + codes.sorted.mkString(",")).mkString(";")
      val message = _failure_message(rejected)
      message should include ("Scoped quality admission pending: " + prefix + "; resultStatus=unavailable; checks=" + diagnostics)
      message.split("limitations=", -1).length shouldBe 6
      codes.foreach(code => message.sliding(code.length).count(_ == code) shouldBe 5)
    }
  }

  "C5 ordinary Record response decoding" should {
    "admit selected CML coverage with omitted optional fields and empty multiplicity fields" in {
      Given("spec:goal-phase-scoped-quality-admission C5 R1,R2,R4; camelCase CML records omit empty paths, limitations, findings, purpose and model")
      val input = _input.copy(checks = Vector("cml-contract"))
      val check = Record.createFull(Vector("checkId" -> "cml-contract", "status" -> "covered",
        "modelSubjects" -> input.modelSubjects))
      val record = _omit(_replace(_result_record, "checks", Vector(check)), "purpose")
      When("the decoder projects the ordinary response and the actual CAM evaluates the selected deterministic scope")
      val decoded = A.decodeResultC(record)
      val evaluated = _decode_evaluate_c(record, input)
      Then("omissions become empty arrays or absent optional strings and CAM admits the complete declared scope")
      decoded.toOption.get.checks shouldBe Vector(A.CheckResult("cml-contract", "covered", Vector.empty,
        input.modelSubjects, Vector.empty))
      decoded.toOption.get.findings shouldBe Vector.empty
      decoded.toOption.get.purpose shouldBe None
      decoded.toOption.get.model shouldBe None
      evaluated.toOption.get.admission.gaps shouldBe Vector.empty
      A.requireAdmittedC(evaluated.toOption.get, "accept") shouldBe Consequence.unit
    }

    "keep omitted or explicit empty checks as genuine CAM Missing after successful decoding" in {
      Given("spec:goal-phase-scoped-quality-admission C5 R2,R4; required scalars with absent or explicitly empty result arrays")
      val records = Vector(_omit(_result_record, "checks"),
        _replace(_replace(_result_record, "checks", Vector.empty), "findings", Vector.empty))
      records.foreach { record =>
        When("the ordinary record is decoded and submitted to the actual CAM")
        val evaluated = _decode_evaluate_c(record)
        Then("a valid empty multiplicity does not fabricate selected coverage")
        evaluated.toOption.get.admission.gaps.head.defects shouldBe Vector(CAM.EvidenceDefect.Missing)
        A.requireAdmittedC(evaluated.toOption.get, "accept") shouldBe a[Consequence.Failure[?]]
      }
    }

    "accept explicit empty arrays and existing Seq and Array containers without coercion" in {
      Given("spec:goal-phase-scoped-quality-admission C5 R1,R4; ordinary Record fixtures contain vector, list or array containers")
      val check = _replace(_check_record, "limitations", Vector.empty[String])
      val checks = Vector[Any](Vector(check), List(check), Array(check))
      checks.foreach { container =>
        val record = _replace(_replace(_result_record, "checks", container), "findings", Array.empty[Record])
        When("the decoder and actual CAM receive the declared sequence containers")
        val evaluated = _decode_evaluate_c(record)
        Then("the semantic scope remains complete and empty findings remain empty")
        evaluated.toOption.get.admission.gaps shouldBe Vector.empty
        evaluated.toOption.get.findings shouldBe Vector.empty
      }
      Given("spec:goal-phase-scoped-quality-admission C5 R4; nested string arrays use existing List and Array containers")
      val nested = _replace(_replace(check, "paths", _input.paths.toList),
        "modelSubjects", _input.modelSubjects.toArray)
      When("nested arrays are decoded before the actual CAM evaluates them")
      val evaluated = _decode_evaluate_c(_replace(_result_record, "checks", Vector(nested)))
      Then("nested containers preserve exact selected scope")
      evaluated.toOption.get.admission.gaps shouldBe Vector.empty
    }

    "decode omitted nested scope arrays as empty while retaining CAM insufficient coverage" in {
      Given("spec:goal-phase-scoped-quality-admission C5 R2,R4; ordinary semantic checks omit paths or modelSubjects")
      val records = Vector("paths", "modelSubjects").map(name =>
        _replace(_result_record, "checks", Vector(_omit(_check_record, name))))
      records.foreach { record =>
        When("the decoder supplies the permitted empty vector and the actual CAM compares selected scope")
        val evaluated = _decode_evaluate_c(record)
        Then("valid multiplicity omission cannot manufacture selected scope coverage")
        evaluated.toOption.get.admission.gaps.head.defects shouldBe Vector(CAM.EvidenceDefect.InsufficientCoverage)
        A.requireAdmittedC(evaluated.toOption.get, "accept") shouldBe a[Consequence.Failure[?]]
      }
    }

    "read integral BigDecimal revision through the existing reader and retain CAM freshness and positive revision gates" in {
      Given("spec:goal-phase-scoped-quality-admission C5 R2,R4; ordinary numeric revisions include current, stale and nonpositive values")
      val current = _replace(_result_record, "revision", BigDecimal(3))
      val stale = _replace(_result_record, "revision", BigDecimal(4))
      val nonpositive = _replace(_result_record, "revision", BigDecimal(0))
      When("the existing Long reader decodes revisions and the actual CAM path applies validation and freshness")
      val decoded = A.decodeResultC(current)
      val admitted = _decode_evaluate_c(current)
      val outdated = _decode_evaluate_c(stale)
      val invalid = _decode_evaluate_c(nonpositive)
      Then("integral numeric shape grants neither positive revision nor freshness on its own")
      decoded.toOption.get.revision shouldBe 3L
      admitted.toOption.get.admission.gaps shouldBe Vector.empty
      outdated.toOption.get.admission.gaps.head.defects shouldBe Vector(CAM.EvidenceDefect.Stale)
      invalid shouldBe a[Consequence.Failure[?]]
    }

    "decode actual nested finding Records and retain blocker refusal and advisory findings" in {
      Given("spec:goal-phase-scoped-quality-admission C5 R3,R4; ordinary nested blocker and hygiene records with omitted optional modelSubject")
      val hygiene = _replace(_replace(_finding_record, "identity", "finding-hygiene"), "severity", "hygiene")
      val record = _replace(_result_record, "findings", Vector(_finding_record, hygiene))
      When("the decoder projects findings and the actual CAM establishes coverage")
      val evaluated = _decode_evaluate_c(record)
      Then("the covered result preserves both findings and blocks acceptance for the actual blocker")
      evaluated.toOption.get.admission.gaps shouldBe Vector.empty
      evaluated.toOption.get.findings.map(_.severity) shouldBe Vector("blocker", "hygiene")
      evaluated.toOption.get.findings.map(_.modelSubject) shouldBe Vector(None, None)
      evaluated.toOption.get.blockingFindings.map(_.identity) shouldBe Vector("finding-blocker")
      A.requireAdmittedC(evaluated.toOption.get, "accept") shouldBe a[Consequence.Failure[?]]
      A.requireAdmittedC(evaluated.toOption.get, "repair") shouldBe Consequence.unit
    }

    "fail missing required scalars, normalized required nulls and wrong scalar types before CAM" in {
      Given("spec:goal-phase-scoped-quality-admission C5 R4; required ordinary scalars are absent after normalization or have wrong types")
      val strings = Vector("reviewId", "subjectId", "subjectRevision", "status", "providerId", "ruleRevision")
      val records = strings.flatMap(name => Vector(_omit(_result_record, name),
        _replace(_result_record, name, null), _replace(_result_record, name, 17))) ++
        Vector(_omit(_result_record, "revision"), _replace(_result_record, "revision", null),
          _replace(_result_record, "revision", Record.dataAuto("revision" -> 3L))) ++
        Vector("purpose", "model").map(name => _replace(_result_record, name, 17)) :+ null
      records.foreach { record =>
        When("the malformed scalar response enters the decoder-to-CAM path")
        val evaluated = _decode_evaluate_c(record)
        Then("typed input failure prevents a fabricated Missing evaluation or scalar coercion")
        evaluated shouldBe a[Consequence.Failure[?]]
      }
    }

    "fail nonsequence and malformed representable elements in every multiplicity field" in {
      Given("spec:goal-phase-scoped-quality-admission C5 R4; explicit arrays have invalid containers or element shapes")
      val records = Vector("checks", "findings").flatMap(name =>
        Vector[Any]("[]", Record.dataAuto("checkId" -> "semantic-consistency"),
          Vector("{}"), Vector(Map("checkId" -> "semantic-consistency"))).map(
          value => _replace(_result_record, name, value))) ++
        Vector("paths", "modelSubjects", "limitations").flatMap(name =>
          Vector[Any]("[]", 17, Vector(17), Vector(_check_record)).map(
            value => _replace(_result_record, "checks", Vector(_replace(_check_record, name, value)))))
      records.foreach { record =>
        When("the decoder-to-CAM path receives the invalid ordinary multiplicity")
        val evaluated = _decode_evaluate_c(record)
        Then("invalid arrays and elements fail without parsing strings, dropping entries or inventing Missing")
        evaluated shouldBe a[Consequence.Failure[?]]
      }
    }

    "refuse native sequence nulls through canonical Record construction before decoding" in {
      Given("spec:goal-phase-scoped-quality-admission C5 R4; null sequence members in result arrays and nested check vectors")
      val rootfields = Vector("checks", "findings")
      val nestedfields = Vector("paths", "modelSubjects", "limitations")
      When("Record.createC receives actual null members directly and at deeper sequence positions")
      val roots = rootfields.flatMap(name => Vector(
        Record.createC(Vector(name -> Vector(null))),
        Record.createC(Vector(name -> Vector(Vector(null))))))
      val nested = nestedfields.flatMap(name => Vector(Vector(null), Vector(Vector(null))).map { values =>
        Record.createC(Vector("checkId" -> "semantic-consistency", "status" -> "covered", name -> values))
          .flatMap(check => Record.createC(Vector("checks" -> Vector(check))))
      })
      Then("canonical construction returns typed failures before any invalid Record reaches the decoder")
      (roots ++ nested).foreach(_ shouldBe a[Consequence.Failure[?]])
    }

    "decode canonical absence for optional native null fields after Record normalization" in {
      Given("spec:goal-phase-scoped-quality-admission C5 R4; optional native nulls at the canonical Record stage")
      val finding = _replace(_replace(_finding_record, "path", null), "modelSubject", null)
      val record = _replace(_replace(_replace(_result_record, "purpose", null), "model", null),
        "findings", Vector(finding))
      When("canonical field absence is decoded as ordinary optional values")
      val decoded = A.decodeResultC(record)
      Then("Core omits the native null fields and the decoder receives absence rather than present null")
      Vector("purpose", "model").foreach(name => record.getAny(name) shouldBe None)
      Vector("path", "modelSubject").foreach(name => finding.getAny(name) shouldBe None)
      decoded.isSuccess shouldBe true
      decoded.toOption.get.purpose shouldBe None
      decoded.toOption.get.model shouldBe None
      decoded.toOption.get.findings.head.path shouldBe None
      decoded.toOption.get.findings.head.modelSubject shouldBe None
    }

    "reject explicit raw JSON null before ordinary response decoding and CAM" in {
      Given("spec:goal-phase-scoped-quality-admission C5 R4; valid ordinary result JSON altered with explicit scalar, container and nested nulls")
      val business = parser.parse(_result_record.toJsonStringC.toOption.get).toOption.get
      val check = business.hcursor.downField("checks").focus.get.asArray.get.head
      val finding = parser.parse(_finding_record.toJsonStringC.toOption.get).toOption.get
      val roots = Vector("reviewId", "revision", "subjectId", "subjectRevision", "status", "providerId",
        "ruleRevision", "purpose", "model", "checks", "findings").map(name =>
        business.mapObject(_.add(name, Json.Null)))
      val checkfields = Vector("checkId", "status", "paths", "modelSubjects", "limitations")
      val nestedchecks = checkfields.map(name => business.mapObject(_.add("checks",
        Json.arr(check.mapObject(_.add(name, Json.Null)))))) ++
        Vector("paths", "modelSubjects", "limitations").flatMap(name => Vector(
          Json.arr(Json.Null), Json.arr(Json.arr(Json.Null))).map(values =>
          business.mapObject(_.add("checks", Json.arr(check.mapObject(_.add(name, values)))))))
      val nestedfindings = Vector("identity", "checkId", "severity", "message", "path", "modelSubject").map(name =>
        business.mapObject(_.add("findings", Json.arr(finding.mapObject(_.add(name, Json.Null))))))
      val elements = Vector("checks", "findings").map(name =>
        business.mapObject(_.add(name, Json.arr(Json.Null))))
      val representations: Vector[Any] = (roots ++ nestedchecks ++ nestedfindings ++ elements)
        .flatMap(value => Vector(value, value.noSpaces))
      When("each Json and String named business result enters recordC before the existing decoder-to-CAM path")
      val actual = representations.map { value =>
        val request = Request.of(component = "org.simplemodeling.textus.SmWorkflow", service = "GoalPhaseService",
          operation = "submitGoalPhaseWorkResult", arguments = Nil, switches = Nil,
          properties = List(Property("result", value, None)))
        val intake = NativeWorkflowOperationInput.recordC(request, "result")
        intake -> intake.flatMap(record => A.decodeResultC(record).flatMap(result => A.evaluateC(_input, Some(result))))
      }
      Then("all explicit nulls fail at named input without normalizing away presence or reaching CAM")
      actual.foreach { (intake, evaluated) =>
        intake shouldBe a[Consequence.Failure[?]]
        evaluated shouldBe a[Consequence.Failure[?]]
      }
    }

    "return fixed field diagnostics without including rejected source payload" in {
      Given("spec:goal-phase-scoped-quality-admission C5 R4; invalid array and revision values carry sensitive fixture text")
      val source = "PRIVATE-FIXTURE-SOURCE-CONTENT"
      val records = Vector(_replace(_result_record, "checks", source),
        _replace(_result_record, "revision", source),
        _replace(_result_record, "findings", Vector(_replace(_finding_record, "path", Vector(source)))))
      records.foreach { record =>
        When("the decoder-to-CAM path rejects the invalid ordinary fields")
        val evaluated = _decode_evaluate_c(record)
        Then("the failure exposes no rejected payload and cannot become an admission evaluation")
        evaluated shouldBe a[Consequence.Failure[?]]
        evaluated.toString should not include (source)
      }
    }

    "fail missing required nested scalars, normalized required nulls and wrong optional finding types" in {
      Given("spec:goal-phase-scoped-quality-admission C5 R4; nested check and finding scalars violate the ordinary CML contract")
      val checks = Vector("checkId", "status").flatMap(name => Vector(_omit(_check_record, name),
        _replace(_check_record, name, null), _replace(_check_record, name, 17)))
      val findings = Vector("identity", "checkId", "severity", "message").flatMap(name =>
        Vector(_omit(_finding_record, name), _replace(_finding_record, name, null),
          _replace(_finding_record, name, 17))) ++ Vector("path", "modelSubject").map(name =>
        _replace(_finding_record, name, 17))
      val records = checks.map(check => _replace(_result_record, "checks", Vector(check))) ++
        findings.map(finding => _replace(_result_record, "findings", Vector(finding)))
      records.foreach { record =>
        When("nested malformed records enter the actual decoder-to-CAM path")
        val evaluated = _decode_evaluate_c(record)
        Then("invalid nested fields fail before any admission evaluation")
        evaluated shouldBe a[Consequence.Failure[?]]
      }
    }

    "reject duplicate keys in root and nested Records without selecting an occurrence" in {
      Given("spec:goal-phase-scoped-quality-admission C5 R4; root, check or finding Records contain duplicate field occurrences")
      val root = Record(_result_record.fields :+ _result_record.fields.head)
      val check = Record(_check_record.fields :+ _check_record.fields.head)
      val finding = Record(_finding_record.fields :+ _finding_record.fields.head)
      val records = Vector(root, _replace(_result_record, "checks", Vector(check)),
        _replace(_result_record, "findings", Vector(finding)))
      records.foreach { record =>
        When("the ordinary decoder encounters a duplicate rather than choosing one occurrence")
        val evaluated = _decode_evaluate_c(record)
        Then("the duplicate is an input failure rather than Missing evidence")
        evaluated shouldBe a[Consequence.Failure[?]]
      }
    }
  }
}
