package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.CandidateAdmissionModel as CAM
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record

/** Passive ordinary CBD Operation evidence for the existing CAM. No provider authenticity,
  * transition, execution or commit authority is issued here.
  * @since   Oct. 17, 2026
  * @version Oct. 18, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseScopedQualityAdmission {
  final case class Selection(
    reviewId: String, revision: Long, subjectId: String, subjectRevision: String,
    paths: Vector[String], modelSubjects: Vector[String], checks: Vector[String]
  )
  final case class Result(
    reviewId: String, revision: Long, subjectId: String, subjectRevision: String,
    status: String, checks: Vector[CheckResult], findings: Vector[Finding],
    providerId: String, purpose: Option[String], model: Option[String], ruleRevision: String
  )
  final case class CheckResult(
    checkId: String, status: String, paths: Vector[String], modelSubjects: Vector[String],
    limitations: Vector[String]
  )
  final case class Finding(
    identity: String, checkId: String, severity: String, path: Option[String],
    modelSubject: Option[String], message: String
  )
  final case class Scope(checkId: String, paths: Vector[String], modelSubjects: Vector[String])
  final case class Evaluation(
    admission: CAM.AdmissionEvaluation[Scope],
    findings: Vector[Finding],
    result: Option[Result] = None
  ) {
    def blockingFindings: Vector[Finding] = findings.filter(_.severity == "blocker")
  }

  private val _checks = Set("cml-contract", "semantic-consistency", "implementation-quality", "excessive-checks", "integration")
  private val _scope_type = "sm-workflow.scoped-quality-scope.v1"
  private val _evidence_type = "ScopedQualityReviewResult"
  private val _rule_revision = "cbd-scoped-quality-v1"
  private val _semantic_purpose = "car-review.semantic-consistency"
  private val _limitation_codes = Set("ai.spi.unavailable", "ai.purpose-or-provider.unavailable",
    "ai.execution.failed", "ai.response.invalid", "ai.provenance.unavailable")

  /** Internal projection of the ordinary camelCase CML response, with no encoder. */
  def decodeResultC(record: Record): Consequence[Result] = for {
    _ <- _record_c(record)
    reviewid <- _text_c(record, "reviewId")
    revision <- _revision_c(record)
    subjectid <- _text_c(record, "subjectId")
    subjectrevision <- _text_c(record, "subjectRevision")
    status <- _text_c(record, "status")
    checks <- _vector_c(record, "checks", _decode_check_c)
    findings <- _vector_c(record, "findings", _decode_finding_c)
    providerid <- _text_c(record, "providerId")
    purpose <- _optional_text_c(record, "purpose")
    model <- _optional_text_c(record, "model")
    rulerevision <- _text_c(record, "ruleRevision")
  } yield Result(reviewid, revision, subjectid, subjectrevision, status, checks,
    findings, providerid, purpose, model, rulerevision)

  private def _decode_check_c(value: Any): Consequence[CheckResult] = value match {
    case record: Record => for {
      _ <- _record_c(record)
      checkid <- _text_c(record, "checkId")
      status <- _text_c(record, "status")
      paths <- _vector_c(record, "paths", _string_c(_, "paths"))
      modelsubjects <- _vector_c(record, "modelSubjects", _string_c(_, "modelSubjects"))
      limitations <- _vector_c(record, "limitations", _string_c(_, "limitations"))
    } yield CheckResult(checkid, status, paths, modelsubjects, limitations)
    case _ => _invalid("checks")
  }

  private def _decode_finding_c(value: Any): Consequence[Finding] = value match {
    case record: Record => for {
      _ <- _record_c(record)
      identity <- _text_c(record, "identity")
      checkid <- _text_c(record, "checkId")
      severity <- _text_c(record, "severity")
      path <- _optional_text_c(record, "path")
      modelsubject <- _optional_text_c(record, "modelSubject")
      message <- _text_c(record, "message")
    } yield Finding(identity, checkid, severity, path, modelsubject, message)
    case _ => _invalid("findings")
  }

  private def _record_c(record: Record): Consequence[Unit] =
    _check(record != null && record.fields.map(_.key).distinct.size == record.fields.size,
      "record is absent or contains duplicate fields")

  // Field presence is retained even when its actual value is null.
  private def _value(record: Record, name: String): Option[Any] =
    record.fields.find(_.key == name).map(_.value.single)

  private def _text_c(record: Record, name: String): Consequence[String] =
    _value(record, name).fold(_invalid[String](name))(_string_c(_, name))

  private def _string_c(value: Any, name: String): Consequence[String] = value match {
    case text: String => Consequence.success(text)
    case _ => _invalid(name)
  }

  private def _optional_text_c(record: Record, name: String): Consequence[Option[String]] =
    _value(record, name) match {
      case None => Consequence.success(None)
      case Some(value) => _string_c(value, name).map(Some(_))
    }

  private def _revision_c(record: Record): Consequence[Long] =
    _value(record, "revision") match {
      case Some(value) if value != null =>
        summon[ValueReader[Long]].readC(value).toOption match {
          case Some(revision) => Consequence.success(revision)
          case None => _invalid("revision")
        }
      case _ => _invalid("revision")
    }

  private def _vector_c[A](record: Record, name: String, decode: Any => Consequence[A]): Consequence[Vector[A]] =
    _value(record, name) match {
      case None => Consequence.success(Vector.empty)
      case Some(values: Seq[?]) => _elements_c(values, decode)
      case Some(values: Array[?]) => _elements_c(values.toVector, decode)
      case Some(_) => _invalid(name)
    }

  private def _elements_c[A](values: Seq[?], decode: Any => Consequence[A]): Consequence[Vector[A]] =
    values.foldLeft(Consequence.success(Vector.empty[A])) { (prior, value) =>
      for {
        accumulated <- prior
        decoded <- decode(value)
      } yield accumulated :+ decoded
    }

  private def _invalid[A](name: String): Consequence[A] =
    Consequence.stateConflict("Scoped quality admission: invalid field " + name)

  def evaluateC(
    input: Selection,
    result: Option[Result]
  ): Consequence[Evaluation] = for {
    _ <- _validate_input_c(input)
    _ <- _check(result != null && result.forall(_ != null), "result option is invalid")
    _ <- result.fold(Consequence.unit)(r => _validate_result_c(input, r))
    target = CAM.CandidateRef(input.subjectId, input.subjectRevision)
    requirements = input.checks.map { check =>
      CAM.AdmissionRequirement(check,
        if (check == "cml-contract") CAM.RequirementKind.Deterministic else CAM.RequirementKind.Semantic,
        _evidence_type, TypedValue(_scope_type, _scope(input, check)))
    }
    evidence = result.toVector.flatMap { r =>
      r.checks.zipWithIndex.map { case (check, ordinal) =>
        CAM.AdmissionEvidence(r.reviewId + ":" + (ordinal + 1), check.checkId,
          CAM.CandidateRef(r.subjectId, r.subjectRevision), TypedValue(_evidence_type, r),
          TypedValue(_scope_type, Scope(check.checkId, check.paths, check.modelSubjects)), r.reviewId)
      }
    }
    submission = CAM.AdmissionSubmission(input.reviewId + ":" + input.revision,
      CAM.CandidateSnapshot(target, TypedValue("sm-workflow.scoped-quality-selection.v1", input)), evidence)
    admission <- CAM.evaluateC(submission, requirements, _policy(input))
  } yield Evaluation(admission, result.toVector.flatMap(_.findings), result)

  def requireAdmittedC(evaluation: Evaluation, outcome: String): Consequence[Unit] = for {
    _ <- _check(Set("accept", "repair", "decision-needed").contains(outcome), "outcome is invalid")
    _ <- _check(evaluation != null && evaluation.admission != null && evaluation.admission.gaps != null &&
      evaluation.findings != null && evaluation.findings.forall(_ != null), "evaluation is invalid")
    _ <- if (evaluation.admission.gaps.nonEmpty) {
      val defects = evaluation.admission.gaps.map(g =>
        g.requirement.identity + ":" + g.defects.mkString(",")).mkString(";")
      Consequence.stateConflict("Scoped quality admission pending: " + defects + _result_diagnostics(evaluation.result))
    } else Consequence.unit
    _ <- _check(outcome != "accept" || evaluation.blockingFindings.isEmpty, "blocker findings prevent acceptance")
  } yield ()

  private def _result_diagnostics(result: Option[Result]): String = result.fold("") { actual =>
    val status = Set("completed", "unavailable", "failed").find(_ == actual.status).getOrElse("redacted")
    val checks = _checks.toVector.sorted.flatMap { checkid =>
      actual.checks.find(_.checkId == checkid).map { check =>
        val checkstatus = Set("covered", "unsupported", "failed").find(_ == check.status).getOrElse("redacted")
        val codes = _limitation_codes.toVector.sorted.filter(check.limitations.contains)
        val redacted = if (check.limitations.exists(code => !_limitation_codes.contains(code))) Vector("redacted")
          else Vector.empty
        checkid + ":" + checkstatus + ":limitations=" + (codes ++ redacted).mkString(",")
      }
    }
    "; resultStatus=" + status + "; checks=" + checks.mkString(";")
  }

  private def _policy(input: Selection): CAM.EvidencePolicy[Result, Scope] =
    new CAM.EvidencePolicy[Result, Scope] {
      def isFresh(evidence: CAM.AdmissionEvidence[Result, Scope], target: CAM.CandidateRef): Boolean = {
        val result = evidence.payload.value
        result.reviewId == input.reviewId && result.revision == input.revision &&
          result.subjectId == target.identity && result.subjectRevision == target.revision &&
          result.subjectId == input.subjectId && result.subjectRevision == input.subjectRevision &&
          result.ruleRevision == _rule_revision
      }

      def covers(evidence: CAM.AdmissionEvidence[Result, Scope], requirement: CAM.AdmissionRequirement[Scope]): Boolean = {
        val result = evidence.payload.value
        val selected = requirement.scope.value
        val returned = evidence.scope.value
        result.status == "completed" && _name(result.providerId) &&
          returned.checkId == selected.checkId && returned.paths.toSet == selected.paths.toSet &&
          returned.modelSubjects.toSet == selected.modelSubjects.toSet &&
          result.checks.exists(c => c.checkId == selected.checkId && c.status == "covered" && c.limitations.isEmpty) &&
          (if (requirement.kind == CAM.RequirementKind.Deterministic) selected.modelSubjects.nonEmpty
            else result.purpose.contains(_semantic_purpose))
      }
    }

  private def _scope(input: Selection, check: String): Scope =
    Scope(check, if (check == "cml-contract") Vector.empty else input.paths, input.modelSubjects)

  private def _validate_input_c(input: Selection): Consequence[Unit] =
    _check(input != null && _name(input.reviewId) && input.revision > 0 &&
      _name(input.subjectId) && _name(input.subjectRevision) && _strings(input.paths) &&
      _strings(input.modelSubjects) && (input.paths.nonEmpty || input.modelSubjects.nonEmpty) &&
      _strings(input.checks) && input.checks.nonEmpty && input.checks.forall(_checks.contains) &&
      input.checks.distinct.size == input.checks.size,
      "selection target, selected checks or scope is invalid")

  private def _validate_result_c(input: Selection, result: Result): Consequence[Unit] = {
    val valid = _name(result.reviewId) && result.revision > 0 && _name(result.subjectId) &&
      _name(result.subjectRevision) && Set("completed", "unavailable", "failed").contains(result.status) &&
      result.providerId != null && _option(result.purpose) && _option(result.model) && _name(result.ruleRevision) &&
      result.checks != null && result.checks.forall(c => c != null && input.checks.contains(c.checkId) &&
        Set("covered", "unsupported", "failed").contains(c.status) && _strings(c.paths) &&
        _strings(c.modelSubjects) && _strings(c.limitations)) &&
      result.checks.map(_.checkId).distinct.size == result.checks.size && result.findings != null &&
      result.findings.forall(f => f != null && _name(f.identity) && input.checks.contains(f.checkId) &&
        Set("blocker", "hygiene", "development-candidate").contains(f.severity) &&
        _option(f.path) && _option(f.modelSubject) && (f.path.nonEmpty || f.modelSubject.nonEmpty) &&
        f.path.forall(_scope(input, f.checkId).paths.contains) &&
        f.modelSubject.forall(input.modelSubjects.contains) && _name(f.message))
    _check(valid, "result checks, status, findings or typed fields are invalid")
  }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
  private def _strings(values: Vector[String]): Boolean = values != null && values.forall(_name)
  private def _option(value: Option[String]): Boolean = value != null && value.forall(_name)
  private def _check(valid: Boolean, detail: String): Consequence[Unit] =
    if (valid) Consequence.unit else Consequence.stateConflict("Scoped quality admission: " + detail)
}
