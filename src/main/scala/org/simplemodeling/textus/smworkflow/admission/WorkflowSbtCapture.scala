package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.syntax.*
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.util.Base64
import org.goldenport.cncf.processexecution.*

/** Closed persistence adapter for actual CNCF results. Captures are stored once.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowSbtCapture {
  val schema = "sm-workflow.native-sbt-validation-receipt.v2"
  final case class Text(value: String, textStatus: String, trailingUndecodedBytes: Int)

  def text(capture: ProcessExecutionCapture): Text = {
    if (!_capture_valid(capture)) return Text("", "unavailable", 0)
    val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
    val input = ByteBuffer.wrap(capture.content.toArray)
    val output = CharBuffer.allocate(capture.content.size)
    val result = decoder.decode(input, output, !capture.truncated)
    output.flip()
    val decoded = output.toString
    if (result.isError || decoded.contains('\u0000') || input.remaining > 3 ||
        (!capture.truncated && input.hasRemaining)) Text("", "unavailable", 0)
    else Text(decoded, if (capture.truncated) "prefix" else "complete", input.remaining)
  }

  def termination(result: ProcessExecutionResult): String = result.termination match {
    case ProcessExecutionTermination.Exited(_) => "exited"
    case ProcessExecutionTermination.NotStarted => "not-started"
    case ProcessExecutionTermination.LaunchFailed => "launch-failed"
    case ProcessExecutionTermination.TimedOut => "timed-out"
    case ProcessExecutionTermination.Cancelled => "cancelled"
    case ProcessExecutionTermination.TerminationUnconfirmed => "termination-unconfirmed"
    case ProcessExecutionTermination.OutputLimitExceeded(_) => "output-limit-exceeded"
    case ProcessExecutionTermination.ArtifactLimitExceeded => "artifact-limit-exceeded"
  }
  def exitCode(result: ProcessExecutionResult): Option[Int] = result.termination match {
    case ProcessExecutionTermination.Exited(code) => Some(code)
    case _ => None
  }

  def valid(result: ProcessExecutionResult, facts: WorkflowSbtValidation.Facts): Boolean =
    result != null && facts != null && facts.valid && result.termination != null &&
      _capture_valid(result.stdout) && _capture_valid(result.stderr) && result.artifacts != null &&
      result.artifacts.isEmpty && result.elapsedMillis >= 0 && result.safeProgramIdentity != null &&
      result.safeProgramIdentity.matches("[a-z][a-z0-9-]{0,63}") && {
        val incomplete = facts.outcome == WorkflowSbtValidation.Outcome.Incomplete
        val truncation = result.stdout.truncated || result.stderr.truncated
        val captures = Vector(result.stdout, result.stderr)
        val terminal = result.termination match {
          case ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout) => incomplete && result.stdout.truncated
          case ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr) => incomplete && result.stderr.truncated
          case ProcessExecutionTermination.TerminationUnconfirmed => incomplete
          case ProcessExecutionTermination.Exited(code) => !truncation && (facts.outcome match {
            case WorkflowSbtValidation.Outcome.Succeeded => code == 0
            case WorkflowSbtValidation.Outcome.Failed => code != 0
            case WorkflowSbtValidation.Outcome.Unverified => code == 0
            case _ => false
          })
          case ProcessExecutionTermination.NotStarted => !truncation && facts.outcome == WorkflowSbtValidation.Outcome.NotExecuted
          case _ => !truncation && incomplete
        }
        terminal && captures.forall(c => c.truncated || text(c).textStatus == "complete")
      }

  def encode(result: ProcessExecutionResult): Json = Json.obj(
    "termination" -> _termination_json(result.termination),
    "stdout" -> _capture_json(result.stdout), "stderr" -> _capture_json(result.stderr),
    "artifacts" -> Json.arr(), "elapsedMillis" -> result.elapsedMillis.asJson,
    "safeProgramIdentity" -> result.safeProgramIdentity.asJson)

  def projection(result: ProcessExecutionResult): Json = encode(result).mapObject { fields =>
    Vector("stdout" -> result.stdout, "stderr" -> result.stderr).foldLeft(fields) { case (prior, (key, capture)) =>
      val derived = text(capture)
      prior.add(key, _capture_json(capture).mapObject(_.add("textStatus", derived.textStatus.asJson)
        .add("trailingUndecodedBytes", derived.trailingUndecodedBytes.asJson)))
    }
  }

  def decode(value: Json): Either[String, ProcessExecutionResult] = for {
    _ <- _keys(value, Set("termination", "stdout", "stderr", "artifacts", "elapsedMillis", "safeProgramIdentity"))
    terminal <- value.hcursor.get[Json]("termination").left.map(_.message).flatMap(_decode_termination)
    out <- value.hcursor.get[Json]("stdout").left.map(_.message).flatMap(_decode_capture)
    err <- value.hcursor.get[Json]("stderr").left.map(_.message).flatMap(_decode_capture)
    artifacts <- value.hcursor.get[Vector[Json]]("artifacts").left.map(_.message)
    _ <- Either.cond(artifacts.isEmpty, (), "Tagged SBT artifacts must be empty")
    elapsed <- _long(value.hcursor, "elapsedMillis")
    program <- value.hcursor.get[String]("safeProgramIdentity").left.map(_.message)
    result = ProcessExecutionResult(terminal, out, err, Vector.empty, elapsed, program)
    _ <- Either.cond(encode(result) == value, (), "Noncanonical process observation")
  } yield result

  private def _capture_valid(value: ProcessExecutionCapture): Boolean = value != null && value.content != null &&
    value.byteCount >= 0 && value.byteCount == value.content.size.toLong
  private def _capture_json(value: ProcessExecutionCapture): Json = Json.obj(
    "contentBase64" -> Base64.getEncoder.encodeToString(value.content.toArray).asJson,
    "byteCount" -> value.byteCount.asJson, "truncated" -> value.truncated.asJson)
  private def _decode_capture(value: Json): Either[String, ProcessExecutionCapture] = for {
    _ <- _keys(value, Set("contentBase64", "byteCount", "truncated"))
    encoded <- value.hcursor.get[String]("contentBase64").left.map(_.message)
    bytes <- scala.util.Try(Base64.getDecoder.decode(encoded).toVector).toEither.left.map(_ => "Invalid Base64 capture")
    count <- _long(value.hcursor, "byteCount")
    truncated <- value.hcursor.get[Boolean]("truncated").left.map(_.message)
    result = ProcessExecutionCapture(bytes, count, truncated)
    _ <- Either.cond(_capture_valid(result) && _capture_json(result) == value, (), "Invalid or noncanonical capture/count")
  } yield result
  private def _termination_json(value: ProcessExecutionTermination): Json = value match {
    case ProcessExecutionTermination.Exited(code) => Json.obj("kind" -> "exited".asJson, "exitCode" -> code.asJson)
    case ProcessExecutionTermination.OutputLimitExceeded(stream) => Json.obj("kind" -> "output-limit-exceeded".asJson,
      "stream" -> (if (stream == ProcessExecutionStream.Stdout) "stdout" else "stderr").asJson)
    case other => Json.obj("kind" -> termination(ProcessExecutionResult(other, null, null, Vector.empty, 0, "")).asJson)
  }
  private def _decode_termination(value: Json): Either[String, ProcessExecutionTermination] = for {
    kind <- value.hcursor.get[String]("kind").left.map(_.message)
    terminal <- kind match {
      case "exited" => value.hcursor.get[Int]("exitCode").left.map(_.message).map(ProcessExecutionTermination.Exited(_))
      case "output-limit-exceeded" => value.hcursor.get[String]("stream").left.map(_.message).flatMap {
        case "stdout" => Right(ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout))
        case "stderr" => Right(ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr))
        case _ => Left("Unknown output-limit stream")
      }
      case "not-started" => Right(ProcessExecutionTermination.NotStarted)
      case "launch-failed" => Right(ProcessExecutionTermination.LaunchFailed)
      case "timed-out" => Right(ProcessExecutionTermination.TimedOut)
      case "cancelled" => Right(ProcessExecutionTermination.Cancelled)
      case "termination-unconfirmed" => Right(ProcessExecutionTermination.TerminationUnconfirmed)
      case "artifact-limit-exceeded" => Right(ProcessExecutionTermination.ArtifactLimitExceeded)
      case _ => Left("Unknown process termination")
    }
    _ <- Either.cond(_termination_json(terminal) == value, (), "Invalid closed termination")
  } yield terminal
  private def _keys(value: Json, keys: Set[String]): Either[String, Unit] =
    Either.cond(value != null && value.asObject.exists(o => o.keys.toSet == keys && o.values.forall(!_.isNull)),
      (), "Process observation requires its exact non-null fields")
  private def _long(cursor: HCursor, key: String): Either[String, Long] =
    cursor.downField(key).focus.flatMap(_.asNumber).flatMap(_.toLong).filter(_ >= 0)
      .toRight("Process observation requires a nonnegative integer " + key)
}
