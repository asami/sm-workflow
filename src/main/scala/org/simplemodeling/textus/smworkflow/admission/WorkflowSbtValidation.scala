package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*

/** Actual typed SBT facts, without command or host-selection authority.
  * @since   Oct. 15, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowSbtValidation {
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
  final case class Facts(kind: Kind, outcome: Outcome, selection: Option[Selection], reason: Option[String],
    estimate: Option[SbtValidationEstimate] = None) {
    def valid: Boolean = kind != null && outcome != null && selection != null && reason != null &&
      estimate != null && estimate.forall(e => e != null && kind == Kind.TestOnly && e.timeoutMillisC.isSuccess) &&
      (outcome match {
        case Outcome.Succeeded if kind == Kind.TestOnly => selection.exists(x => x != null && x.valid) && reason.isEmpty
        case Outcome.Unverified => kind == Kind.TestOnly && selection.isEmpty &&
          reason.exists(x => SbtTestSelectionFailure.values.exists(_.toString == x))
        case _ => selection.isEmpty && reason.isEmpty
      })
    def validateC: Consequence[Unit] = if (valid) Consequence.unit
      else Consequence.stateConflict("Invalid typed SBT validation facts")
  }
  object Facts {
    given Encoder[Facts] = Encoder.instance(value => Json.obj("kind" -> value.kind.asJson,
      "outcome" -> value.outcome.asJson, "selection" -> value.selection.asJson, "reason" -> value.reason.asJson,
      "estimate" -> value.estimate.asJson))
    given Decoder[Facts] = Decoder.instance(cursor => for {
      kind <- cursor.get[Kind]("kind")
      outcome <- cursor.get[Outcome]("outcome")
      selection <- cursor.get[Option[Selection]]("selection")
      reason <- cursor.get[Option[String]]("reason")
      estimate <- cursor.get[Option[SbtValidationEstimate]]("estimate")
      value = Facts(kind, outcome, selection, reason, estimate)
      _ <- Either.cond(value.valid, (), DecodingFailure("Invalid typed SBT validation facts", cursor.history))
    } yield value)
  }

  /** Only an absent Check kind has historical Test meaning. */
  def checkKind(cursor: HCursor): Decoder.Result[Kind] =
    if (cursor.keys.exists(_.exists(_ == "kind"))) cursor.get[Kind]("kind") else Right(Kind.Test)

  def factsC(kind: Kind, result: SbtExecutionResult): Consequence[Facts] = for {
    _ <- if (kind != null && result != null && result.processResult != null &&
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
    retained = facts.copy(estimate = result.target.validationEstimate)
    _ <- retained.validateC
  } yield retained
}
