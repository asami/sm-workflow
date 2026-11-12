package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.util.Base64
import org.goldenport.cncf.processexecution.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.resource.ResourceReadLimits
import org.goldenport.cncf.workspace.*

/** Closed persistence adapter for actual CNCF results. Captures are stored once.
  * @since   Oct. 24, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object WorkflowSbtCapture {
  val schema = "sm-workflow.native-sbt-validation-receipt.v2"
  val externalSchema = "sm-workflow.native-sbt-validation-artifact.v1"
  val externalReceiptSchema = "sm-workflow.native-sbt-validation-receipt.v3"
  val maximumArtifactBytes = 128 * 1024 * 1024
  final case class Text(value: String, textStatus: String, trailingUndecodedBytes: Int)

  /** Stable association with one native intent; it is not process success authority. */
  final case class Association(component: String, session: String, intentRevision: Long, participant: String,
    check: String, ordinal: Int, root: String, branch: String, baseHead: String, candidateTree: String)
  object Association {
    given Encoder[Association] = deriveEncoder
    private val decoder: Decoder[Association] = deriveDecoder
    given Decoder[Association] = Decoder.instance(cursor => decoder.tryDecode(cursor).flatMap(value =>
      Either.cond(value.asJson == cursor.value && associationValid(value), value,
        DecodingFailure("Invalid closed external capture association", cursor.history))))
  }
  final case class ExternalReference(workspace: String, path: String, association: Association,
    encodedBytes: Long, contentSha256: String, stdoutBytes: Long, stderrBytes: Long,
    stdoutTruncated: Boolean, stderrTruncated: Boolean, termination: String, exitCode: Option[Int])
  object ExternalReference {
    given Encoder[ExternalReference] = deriveEncoder
    private val decoder: Decoder[ExternalReference] = deriveDecoder
    given Decoder[ExternalReference] = Decoder.instance(cursor => decoder.tryDecode(cursor).flatMap(value =>
      Either.cond(value.asJson == cursor.value && externalReferenceValid(value), value,
        DecodingFailure("Invalid closed external capture reference", cursor.history))))
  }
  private def exact(value: String, max: Int = 4096): Boolean = value != null && value.nonEmpty &&
    value.length <= max && value.trim == value && !value.exists(_.isControl)
  def associationValid(a: Association): Boolean = a != null && a.intentRevision > 0 && a.ordinal > 0 &&
    Vector(a.component, a.session, a.participant, a.check, a.root, a.branch, a.baseHead, a.candidateTree).forall(exact(_))
  def externalReferenceValid(r: ExternalReference): Boolean = r != null &&
    WorkspaceId.parseC(r.workspace).isSuccess && r.workspace != "sm-workflow-repository" &&
    WorkspaceFilePath.parseC(r.path).isSuccess && exact(r.workspace, 256) && exact(r.path) &&
    associationValid(r.association) && r.encodedBytes > 0 && r.encodedBytes <= maximumArtifactBytes &&
    r.contentSha256 != null && r.contentSha256.matches("[0-9a-f]{64}") &&
    r.stdoutBytes >= 0 && r.stderrBytes >= 0 && exact(r.termination, 64) && r.exitCode != null

  private def artifactJson(a: Association, facts: WorkflowSbtValidation.Facts, process: ProcessExecutionResult): Json =
    Json.obj("schemaVersion" -> externalSchema.asJson, "association" -> a.asJson,
      "facts" -> facts.asJson, "process" -> encode(process))
  private def hash(bytes: Array[Byte]): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(bytes).map(b => f"${b & 0xff}%02x").mkString
  def externalMatches(r: ExternalReference, facts: WorkflowSbtValidation.Facts, p: ProcessExecutionResult): Boolean =
    externalReferenceValid(r) && structurallyValid(p, facts) && r.stdoutBytes == p.stdout.byteCount &&
    r.stderrBytes == p.stderr.byteCount && r.stdoutTruncated == p.stdout.truncated &&
    r.stderrTruncated == p.stderr.truncated && r.termination == termination(p) && r.exitCode == exitCode(p) && {
      val bytes = artifactJson(r.association, facts, p).noSpaces.getBytes(StandardCharsets.UTF_8)
      bytes.length.toLong == r.encodedBytes && hash(bytes) == r.contentSha256
    }

  /** Archive with the owner File UoW, create-only, then read the same actual bytes back. */
  def archiveC(core: ActionCall.Core, workspace: String, path: String, association: Association,
    facts: WorkflowSbtValidation.Facts, process: ProcessExecutionResult,
    limits: ResourceReadLimits): Consequence[ExternalReference] = for {
    _ <- if (associationValid(association) && structurallyValid(process, facts) && facts.kind == WorkflowSbtValidation.Kind.Test && limits != null &&
      limits.maximumBytes > 0 && limits.maximumBytes <= maximumArtifactBytes && workspace != "sm-workflow-repository")
      Consequence.unit else Consequence.stateConflict("External capture requires valid actual observation and finite owned artifact policy")
    target <- WorkspaceId.parseC(workspace)
    file <- WorkspaceFilePath.parseC(path)
    bytes = artifactJson(association, facts, process).noSpaces.getBytes(StandardCharsets.UTF_8)
    _ <- if (bytes.length <= limits.maximumBytes) Consequence.unit
      else Consequence.stateConflict("Actual complete external capture exceeds its finite artifact policy")
    written <- WorkflowFileExecution.writeBytesC(core, FileTarget(target), file, bytes.toVector,
      FileRepresentation.Identity, FileWritePolicy(limits))
    _ <- if (written.outcome == FileWriteOutcome.Created) Consequence.unit
      else Consequence.stateConflict("External capture path already exists or was not created")
    observed <- WorkflowFileExecution.readBytesC(core, FileTarget(target), file,
      FileRepresentation.Identity, FileReadPolicy(limits))
    _ <- if (observed.bytes.contains(bytes.toVector)) Consequence.unit
      else Consequence.stateConflict("Created external capture could not be read back exactly")
    reference = ExternalReference(workspace, path, association, bytes.length.toLong, hash(bytes),
      process.stdout.byteCount, process.stderr.byteCount, process.stdout.truncated, process.stderr.truncated,
      termination(process), exitCode(process))
    _ <- if (externalMatches(reference, facts, process)) Consequence.unit
      else Consequence.stateConflict("External capture reference does not bind actual observation")
  } yield reference

  /** Reopen only the exact scoped canonical artifact; a missing or changed file is not success. */
  def hydrateC(core: ActionCall.Core, reference: ExternalReference, expected: Association,
    facts: WorkflowSbtValidation.Facts, limits: ResourceReadLimits): Consequence[ProcessExecutionResult] = for {
    _ <- if (externalReferenceValid(reference) && reference.association == expected && limits != null &&
      limits.maximumBytes > 0 && limits.maximumBytes <= maximumArtifactBytes && reference.encodedBytes <= limits.maximumBytes)
      Consequence.unit else Consequence.stateConflict("External capture scope or finite read policy differs")
    target <- WorkspaceId.parseC(reference.workspace)
    file <- WorkspaceFilePath.parseC(reference.path)
    read <- WorkflowFileExecution.readBytesC(core, FileTarget(target), file, FileRepresentation.Identity, FileReadPolicy(limits))
    bytes <- read.bytes.map(Consequence.success).getOrElse(Consequence.stateConflict("External capture is unavailable"))
    _ <- if (bytes.size.toLong == reference.encodedBytes && hash(bytes.toArray) == reference.contentSha256)
      Consequence.unit else Consequence.stateConflict("External capture bytes differ from their retained reference")
    json <- io.circe.parser.parse(new String(bytes.toArray, StandardCharsets.UTF_8))
      .fold(e => Consequence.stateConflict(e.message), Consequence.success)
    processJson <- json.hcursor.get[Json]("process").fold(e => Consequence.stateConflict(e.message), Consequence.success)
    process <- decode(processJson).fold(Consequence.stateConflict(_), Consequence.success)
    _ <- if (json == artifactJson(expected, facts, process) &&
      json.noSpaces.getBytes(StandardCharsets.UTF_8).toVector == bytes && externalMatches(reference, facts, process))
      Consequence.unit else Consequence.stateConflict("External capture is malformed, noncanonical or contradictory")
  } yield process

  /** A retained native intent may name a previously created artifact whose receipt save failed.
    * Discovery never runs a process; the exact association and entire closed artifact are required.
    */
  def discoverC(core: ActionCall.Core, workspace: String, path: String, expected: Association,
    limits: ResourceReadLimits): Consequence[(ExternalReference, WorkflowSbtValidation.Facts, ProcessExecutionResult)] = for {
    _ <- if (associationValid(expected) && limits != null && limits.maximumBytes > 0 &&
      limits.maximumBytes <= maximumArtifactBytes && workspace != "sm-workflow-repository") Consequence.unit
      else Consequence.stateConflict("External capture discovery requires an exact owner and finite policy")
    target <- WorkspaceId.parseC(workspace)
    file <- WorkspaceFilePath.parseC(path)
    read <- WorkflowFileExecution.readBytesC(core, FileTarget(target), file, FileRepresentation.Identity, FileReadPolicy(limits))
    bytes <- read.bytes.map(Consequence.success).getOrElse(Consequence.stateConflict("External capture is unavailable"))
    json <- io.circe.parser.parse(new String(bytes.toArray, StandardCharsets.UTF_8))
      .fold(e => Consequence.stateConflict(e.message), Consequence.success)
    facts <- json.hcursor.get[WorkflowSbtValidation.Facts]("facts").fold(e => Consequence.stateConflict(e.message), Consequence.success)
    raw <- json.hcursor.get[Json]("process").fold(e => Consequence.stateConflict(e.message), Consequence.success)
    process <- decode(raw).fold(Consequence.stateConflict(_), Consequence.success)
    _ <- if (facts.kind == WorkflowSbtValidation.Kind.Test && structurallyValid(process, facts) &&
      json == artifactJson(expected, facts, process) && json.noSpaces.getBytes(StandardCharsets.UTF_8).toVector == bytes)
      Consequence.unit else Consequence.stateConflict("External capture discovery found foreign or contradictory data")
    reference = ExternalReference(workspace, path, expected, bytes.size.toLong, hash(bytes.toArray),
      process.stdout.byteCount, process.stderr.byteCount, process.stdout.truncated, process.stderr.truncated,
      termination(process), exitCode(process))
    _ <- if (externalMatches(reference, facts, process)) Consequence.unit
      else Consequence.stateConflict("External capture discovery is noncanonical")
  } yield (reference, facts, process)

  /** Upper bound for the canonical process JSON, not for its enclosing receipt.
    * The caller must separately account for its artifact wrapper and the complete
    * current/next mechanical transition envelopes. Use arbitrary precision while
    * calculating so an oversized configured policy cannot wrap into an admission.
    */
  def maximumEncodedProcessBytes(stdoutBytes: Long, stderrBytes: Long): Option[Long] = {
    if (stdoutBytes < 0 || stderrBytes < 0) None
    else {
      def capture = Json.obj("contentBase64" -> Json.fromString(""),
        "byteCount" -> Json.fromLong(Long.MaxValue), "truncated" -> Json.False)
      val fixed = Json.obj(
        "termination" -> Json.obj("kind" -> "output-limit-exceeded".asJson, "stream" -> "stdout".asJson),
        "stdout" -> capture, "stderr" -> capture,
        "artifacts" -> Json.arr(), "elapsedMillis" -> Json.fromLong(Long.MaxValue),
        "safeProgramIdentity" -> Json.fromString("a" * 64)).noSpaces.getBytes(StandardCharsets.UTF_8).length
      def base64(n: Long): BigInt = ((BigInt(n) + 2) / 3) * 4
      val total = BigInt(fixed) + base64(stdoutBytes) + base64(stderrBytes)
      Option.when(total.isValidLong)(total.toLong)
    }
  }

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

  /** Raw structural validity permits retaining non-text bytes; it does not make
    * those bytes usable as diagnostic text or a successful observation. */
  def structurallyValid(result: ProcessExecutionResult, facts: WorkflowSbtValidation.Facts): Boolean =
    result != null && facts != null && facts.valid && result.termination != null &&
      _capture_valid(result.stdout) && _capture_valid(result.stderr) && result.artifacts != null &&
      result.artifacts.isEmpty && result.elapsedMillis >= 0 && result.safeProgramIdentity != null &&
      result.safeProgramIdentity.matches("[a-z][a-z0-9-]{0,63}") && {
        val incomplete = facts.outcome == WorkflowSbtValidation.Outcome.Incomplete
        val truncation = result.stdout.truncated || result.stderr.truncated
        val terminal = result.termination match {
          case ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stdout) => incomplete && result.stdout.truncated
          case ProcessExecutionTermination.OutputLimitExceeded(ProcessExecutionStream.Stderr) => incomplete && result.stderr.truncated
          case ProcessExecutionTermination.TerminationUnconfirmed => incomplete
          case ProcessExecutionTermination.Exited(code) => !truncation && (facts.outcome match {
            case WorkflowSbtValidation.Outcome.Succeeded => code == 0
            case WorkflowSbtValidation.Outcome.Failed => code != 0 ||
              (facts.kind == WorkflowSbtValidation.Kind.TestOnly && WorkflowSbtValidation.formalTestFailure(facts.reason))
            case WorkflowSbtValidation.Outcome.Unverified => code == 0
            case _ => false
          })
          case ProcessExecutionTermination.NotStarted => !truncation && facts.outcome == WorkflowSbtValidation.Outcome.NotExecuted
          case _ => !truncation && incomplete
        }
        terminal
      }

  def valid(result: ProcessExecutionResult, facts: WorkflowSbtValidation.Facts): Boolean =
    structurallyValid(result, facts) && Vector(result.stdout, result.stderr)
      .forall(c => c.truncated || text(c).textStatus == "complete")

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
