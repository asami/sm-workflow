package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.syntax.*
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.cncf.validation.ValidationExecutionModeV1
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*

/** Actual typed SBT facts, without command or host-selection authority.
  * @since   Oct. 15, 2026
  *  version Oct. 25, 2026
  * @version Nov. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowSbtValidation {
  given Encoder[SbtScenarioParameters.Value] = Encoder.instance {
    case SbtScenarioParameters.Value.Text(value) => Json.fromString(value)
    case SbtScenarioParameters.Value.BooleanValue(value) => Json.fromBoolean(value)
    case SbtScenarioParameters.Value.Integer(value) => Json.fromLong(value)
  }
  given Decoder[SbtScenarioParameters.Value] = Decoder.instance(cursor =>
    cursor.value.asString.map(SbtScenarioParameters.Value.Text.apply)
      .orElse(cursor.value.asBoolean.map(SbtScenarioParameters.Value.BooleanValue.apply))
      .orElse(cursor.value.asNumber.flatMap(_.toBigDecimal).filter(value => value.isWhole && value.isValidLong)
        .map(value => SbtScenarioParameters.Value.Integer(value.toLong)))
      .toRight(DecodingFailure("Expected exact typed scenario primitive", cursor.history)))
  given Encoder[SbtScenarioSelection] = deriveEncoder
  given Decoder[SbtScenarioSelection] = deriveDecoder
  given Encoder[SbtValidationMode.Case] = deriveEncoder
  given Decoder[SbtValidationMode.Case] = deriveDecoder
  given Encoder[ValidationExecutionModeV1.Observation] = deriveEncoder
  given Decoder[ValidationExecutionModeV1.Observation] = deriveDecoder
  given Encoder[SbtValidationMode.CaseResult] = deriveEncoder
  given Decoder[SbtValidationMode.CaseResult] = deriveDecoder
  given Encoder[SbtLintInput] = deriveEncoder
  given Decoder[SbtLintInput] = deriveDecoder
  private val lintParser = new io.circe.jawn.JawnParser(maxValueSize = None, allowDuplicateKeys = false)
  private def lintValid(input: SbtLintInput): Boolean = input != null &&
    input.identity != null && input.identity.matches("[A-Za-z0-9_.-]{1,128}") &&
    input.revision != null && input.revision.matches("[A-Za-z0-9_.-]{1,128}") &&
    input.configJson != null && java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(input.configJson) &&
    input.configJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536 &&
    lintParser.parse(input.configJson).toOption.exists(_.isObject)

  enum Kind(val wire: String) {
    case Compile extends Kind("compile")
    case TestCompile extends Kind("test-compile")
    case Test extends Kind("test")
    case TestOnly extends Kind("test-only")
    case FixCheck extends Kind("fix-check")
  }
  object Kind {
    given Encoder[Kind] = Encoder.encodeString.contramap(_.wire)
    given Decoder[Kind] = Decoder.decodeString.emap(value =>
      Kind.values.find(_.wire == value).toRight("Unknown SBT validation kind"))
  }
  enum Outcome(val wire: String) {
    case Succeeded extends Outcome("succeeded")
    case NotExecuted extends Outcome("not-executed")
    case Failed extends Outcome("failed")
    case Incomplete extends Outcome("incomplete")
    case Unverified extends Outcome("unverified-tests")
  }
  object Outcome {
    given Encoder[Outcome] = Encoder.encodeString.contramap(_.wire)
    given Decoder[Outcome] = Decoder.decodeString.emap(value =>
      Outcome.values.find(_.wire == value).toRight("Unknown SBT validation outcome"))
  }
  final case class Selection(testClasses: Vector[String], completedSuites: Int, testsRun: Long) {
    def valid: Boolean = testClasses != null && testClasses.nonEmpty && testClasses.size <= 16 &&
      testClasses.forall(x => x != null && x.length <= 256 &&
        x.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) &&
      testClasses.distinct.size == testClasses.size &&
      testClasses.map(_.split('.').last).distinct.size == testClasses.size &&
      completedSuites == testClasses.size && testsRun > 0
  }
  object Selection {
    given Encoder[Selection] = Encoder.instance(value => Json.obj(
      "testClasses" -> value.testClasses.asJson, "completedSuites" -> value.completedSuites.asJson,
      "testsRun" -> value.testsRun.asJson))
    given Decoder[Selection] = Decoder.instance(cursor => for {
      classes <- cursor.get[Vector[String]]("testClasses")
      suites <- cursor.downField("completedSuites").focus.flatMap(_.asNumber).flatMap(_.toInt)
        .toRight(DecodingFailure("Selected suite count must be a JSON integer", cursor.downField("completedSuites").history))
      tests <- cursor.downField("testsRun").focus.flatMap(_.asNumber).flatMap(_.toLong)
        .toRight(DecodingFailure("Selected test count must be a JSON integer", cursor.downField("testsRun").history))
      value = Selection(classes, suites, tests)
      _ <- Either.cond(value.valid, (), DecodingFailure("Invalid selected SBT tests", cursor.history))
    } yield value)
  }
  final case class Mode(requested: String, actual: Option[String], provider: Option[String], reason: Option[String]) {
    def verified: Boolean = actual.contains(requested) && provider.exists(Set("fixture", "live")) && reason.isEmpty
    def valid: Boolean = requested != null && org.goldenport.cncf.config.OperationMode.from(requested).exists(_.name == requested) &&
      actual != null && provider != null && reason != null &&
      (verified || (actual.isEmpty && provider.isEmpty && reason.exists(_.nonEmpty)))
  }
  object Mode {
    given Encoder[Mode] = Encoder.instance(value => Json.obj("requested" -> value.requested.asJson,
      "actual" -> value.actual.asJson, "provider" -> value.provider.asJson, "reason" -> value.reason.asJson))
    given Decoder[Mode] = Decoder.instance(cursor => for {
      requested <- cursor.get[String]("requested")
      actual <- cursor.get[Option[String]]("actual")
      provider <- cursor.get[Option[String]]("provider")
      reason <- cursor.get[Option[String]]("reason")
      value = Mode(requested, actual, provider, reason)
      _ <- Either.cond(value.valid, (), DecodingFailure("Invalid child validation mode facts", cursor.history))
    } yield value)
  }
  /** CNCF distinguishes confirmed selection failure from unavailable observation. */
  def formalTestFailure(reason: Option[String]): Boolean = reason.exists(Set(
    "FailedTests", "ZeroTests", "SuiteMismatch", "ScenarioMismatch", "InventoryMismatch"))

  final case class AdmissionCase(suite: String, test: String)
  object AdmissionCase {
    given Encoder[AdmissionCase] = deriveEncoder
    given Decoder[AdmissionCase] = deriveDecoder
  }
  final case class Facts(kind: Kind, outcome: Outcome, selection: Option[Selection], reason: Option[String],
    estimate: Option[SbtValidationEstimate] = None,
    mode: Option[Mode] = None, batch: Option[Vector[SbtValidationMode.CaseResult]] = None,
    private val estimateFieldPresent: Boolean = false,
    lintInput: Option[SbtLintInput] = None, testScope: Option[String] = None,
    admissionCases: Option[Vector[AdmissionCase]] = None) {
    def effectiveTestScope: SbtTestScope = if (testScope.contains("admission")) SbtTestScope.Admission else SbtTestScope.Full
    override def equals(other: Any): Boolean = other match {
      case that: Facts => kind == that.kind && outcome == that.outcome && selection == that.selection &&
        reason == that.reason && estimate == that.estimate && mode == that.mode && batch == that.batch && lintInput == that.lintInput &&
        testScope == that.testScope && admissionCases == that.admissionCases
      case _ => false
    }
    override def hashCode: Int = (kind, outcome, selection, reason, estimate, mode, batch, lintInput, testScope, admissionCases).hashCode
    def valid: Boolean = kind != null && outcome != null && selection != null && reason != null &&
      testScope != null && testScope.forall(s => kind == Kind.Test && s == "admission") &&
      admissionCases != null && admissionCases.forall(rows => effectiveTestScope == SbtTestScope.Admission && rows.nonEmpty &&
        rows.distinct == rows && rows.forall(row => row != null && row.suite != null && row.suite.nonEmpty && row.test != null && row.test.nonEmpty)) &&
      lintInput != null && lintInput.forall(input => kind == Kind.FixCheck && lintValid(input)) &&
      estimate != null && estimate.forall(e => e != null && kind == Kind.TestOnly && e.timeoutMillisC.isSuccess) &&
      batch != null && batch.forall(values => kind == Kind.TestOnly && values.nonEmpty && values.size <= 64 &&
        values.forall(value => value != null && value.selected != null && value.selected.validateC.isSuccess &&
          Set("passed", "failed", "unverified", "not-executed")(value.status) && value.observation != null &&
          (value.status != "passed" || value.observation.exists(observed => observed.suite == value.selected.selector.suiteClassName &&
            observed.test == value.selected.selector.testName && observed.mode == value.selected.mode &&
            Set("fixture", "live")(observed.provider) && value.selected.requiredProvider.forall(_ == observed.provider)))) &&
        (outcome != Outcome.Succeeded || values.forall(_.status == "passed"))) && mode != null && mode.forall(value => value != null && value.valid &&
        (outcome != Outcome.Succeeded || value.verified)) && (outcome match {
        case Outcome.Succeeded if effectiveTestScope == SbtTestScope.Admission =>
          selection.isEmpty && admissionCases.nonEmpty && reason.isEmpty
        case Outcome.Succeeded if kind == Kind.TestOnly =>
          ((selection.exists(x => x != null && x.valid) && batch.isEmpty) || (selection.isEmpty && batch.nonEmpty)) && reason.isEmpty
        case Outcome.Failed => selection.isEmpty &&
          (reason.isEmpty || (kind == Kind.TestOnly && formalTestFailure(reason)))
        case Outcome.Unverified => selection.isEmpty &&
          ((kind == Kind.TestOnly && reason.exists(x => SbtTestSelectionFailure.values.exists(_.toString == x))) ||
            (effectiveTestScope == SbtTestScope.Admission && reason.exists(x => SbtTestSelectionFailure.values.exists(_.toString == x))) ||
            (kind == Kind.TestCompile && reason.contains("DiscoveryUnavailable") && mode.isEmpty && batch.isEmpty))
        case _ => selection.isEmpty && reason.isEmpty
      })
    def validateC: Consequence[Unit] = if (valid) Consequence.unit
      else Consequence.stateConflict("Invalid typed SBT validation facts")
  }
  object Facts {
    given Encoder[Facts] = Encoder.instance { value =>
      val legacy = Json.obj("kind" -> value.kind.asJson, "outcome" -> value.outcome.asJson,
        "selection" -> value.selection.asJson, "reason" -> value.reason.asJson)
      val estimated = if (value.estimate.nonEmpty || value.estimateFieldPresent)
        legacy.mapObject(_.add("estimate", value.estimate.asJson)) else legacy
      val mode = value.mode.fold(estimated)(mode => estimated.mapObject(_.add("mode", mode.asJson)))
      val batch = value.batch.fold(mode)(batch => mode.mapObject(_.add("batch", batch.asJson)))
      val lint = value.lintInput.fold(batch)(input => batch.mapObject(_.add("lintInput", input.asJson)))
      val scope = value.testScope.fold(lint)(s => lint.mapObject(_.add("testScope", s.asJson)))
      value.admissionCases.fold(scope)(rows => scope.mapObject(_.add("admissionCases", rows.asJson)))
    }
    given Decoder[Facts] = Decoder.instance(cursor => for {
      kind <- cursor.get[Kind]("kind")
      outcome <- cursor.get[Outcome]("outcome")
      selection <- cursor.get[Option[Selection]]("selection")
      reason <- cursor.get[Option[String]]("reason")
      estimate <- cursor.get[Option[SbtValidationEstimate]]("estimate")
      mode <- GoalPhaseNativeMetadata.optional[Mode](cursor, "mode")
      batch <- GoalPhaseNativeMetadata.optional[Vector[SbtValidationMode.CaseResult]](cursor, "batch")
      lint <- GoalPhaseNativeMetadata.optional[SbtLintInput](cursor, "lintInput")
      scope <- GoalPhaseNativeMetadata.optional[String](cursor, "testScope")
      cases <- GoalPhaseNativeMetadata.optional[Vector[AdmissionCase]](cursor, "admissionCases")
      value = Facts(kind, outcome, selection, reason, estimate, mode, batch,
        cursor.keys.exists(_.exists(_ == "estimate")), lint, scope, cases)
      _ <- Either.cond(value.valid, (), DecodingFailure("Invalid typed SBT validation facts", cursor.history))
    } yield value)
  }

  /** Only an absent Check kind has historical Test meaning. */
  def checkKind(cursor: HCursor): Decoder.Result[Kind] =
    if (cursor.keys.exists(_.exists(_ == "kind"))) cursor.get[Kind]("kind") else Right(Kind.Test)

  /** Reassess the retained native Admission report using the same CNCF reader
    * as initial selection. Historical omitted scope continues to mean Full. */
  def scopeReceiptValid(facts: Option[Facts], process: Option[ProcessExecutionResult], expected: SbtTestScope): Boolean =
    if (expected == SbtTestScope.Full) facts.forall(_.effectiveTestScope == SbtTestScope.Full)
    else expected == SbtTestScope.Admission && facts.exists { value => value.effectiveTestScope == SbtTestScope.Admission && process.exists { observed =>
      val report = SbtAdmissionTestReport.assess(observed)
      val rows = report.selected.map((suite, test) => AdmissionCase(suite, test))
      rows == value.admissionCases.getOrElse(Vector.empty) && (value.outcome match {
        case Outcome.Succeeded => report.disposition.isEmpty
        case Outcome.Unverified => report.disposition.exists(reason => value.reason.contains(reason.toString))
        case _ => true
      })
    } }

  def factsC(kind: Kind, result: SbtExecutionResult): Consequence[Facts] = for {
    _ <- if (kind != null && result != null && result.target != null && result.target.validationMode != null &&
        result.validationMode != null && result.processResult != null && result.lintInput != null &&
        result.target.lintInput != null && result.lintInput == result.target.lintInput && result.target.testScope != null &&
        (result.target.testScope == SbtTestScope.Full || kind == Kind.Test) &&
        result.lintInput.forall(input => kind == Kind.FixCheck && lintValid(input)) &&
        result.processResult.termination != null && result.testSelection != null &&
        ((kind == Kind.Compile && result.operation == SbtOperation.Compile) ||
          (kind == Kind.TestCompile && result.operation == SbtOperation.TestCompile) ||
          (kind == Kind.Test && result.operation == SbtOperation.Test) ||
          (kind == Kind.TestOnly && result.operation == SbtOperation.TestOnly) ||
          (kind == Kind.FixCheck && result.operation == SbtOperation.FixCheck)) &&
        result.testSelection.forall {
          case SbtTestSelectionResult.Verified(classes, suites, tests) =>
            kind == Kind.TestOnly && Selection(classes, suites, tests).valid
          case SbtTestSelectionResult.Rejected(reason) => kind == Kind.TestOnly && reason != null
          case _ => false
        }) Consequence.unit else Consequence.stateConflict("SBT result differs from selected typed operation or facts")
    facts = result.outcome match {
      case SbtExecutionOutcome.Succeeded => Facts(kind, Outcome.Succeeded, result.testSelection.collect {
        case SbtTestSelectionResult.Verified(classes, suites, tests) => Selection(classes, suites, tests)
      }, None)
      case SbtExecutionOutcome.Failed(_) => Facts(kind, Outcome.Failed, None, None)
      // Keep the existing consumer receipt contract for zero-exit negative test
      // verification. CNCF's finer outcome does not make a raw nonzero exit.
      case SbtExecutionOutcome.TestValidationFailed(reason) => Facts(kind, Outcome.Unverified, None, Some(reason.toString))
      case SbtExecutionOutcome.NotExecuted => Facts(kind, Outcome.NotExecuted, None, None)
      case SbtExecutionOutcome.Incomplete(_) => Facts(kind, Outcome.Incomplete, None, None)
      case SbtExecutionOutcome.UnverifiedTests(reason) => Facts(kind, Outcome.Unverified, None, Some(reason.toString))
    }
    mode = result.target.validationMode.map { requested => result.validationMode match {
      case Some(SbtValidationMode.Result.Observed(observed)) => Mode(requested, Some(observed.mode), Some(observed.provider), None)
      case Some(SbtValidationMode.Result.Unverified(reason)) => Mode(requested, None, None, Some(reason))
      case None => Mode(requested, None, None, Some("child observation unavailable"))
    } }
    report = result.admissionTestReport
    rows = report.toVector.flatMap(_.selected).map((suite, test) => AdmissionCase(suite, test))
    retained = facts.copy(estimate = result.target.validationEstimate, mode = mode, batch = result.batch, lintInput = result.lintInput,
      testScope = Option.when(result.target.testScope == SbtTestScope.Admission)("admission"),
      admissionCases = Option.when(rows.nonEmpty)(rows))
    _ <- retained.validateC
    _ <- if (scopeReceiptValid(Some(retained), Some(result.processResult), result.target.testScope)) Consequence.unit
      else Consequence.stateConflict("SBT Admission report differs from its retained selection or outcome")
  } yield retained
}
