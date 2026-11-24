/**
 * @since   Nov. 24, 2026
 * @version Nov. 24, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import io.circe.Json
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceFilePath

/** Bounded, fixed-provider Scala formatter preview. A preview is not an edit:
  * the caller must bind and admit the original bytes and the result before use.
  */
private[smworkflow] object WorkflowScalaToolProcess {
  val capability = "sm-workflow-scalafmt-preview"
  val scalafixCapability = "sm-workflow-scalafix-preview"
  private val _limit = 1048576

  final case class Input(path: String, source: String, configFiles: Map[String, String])
  enum PreviewStatus {
    case Applied, Unchanged, Unavailable, FormalFailure, Uncertain, MetadataRequired
  }
  final case class Preview(status: PreviewStatus, path: String, inputHash: String,
    engineVersion: String, programIdentity: Option[String], replacement: Option[String], diagnostic: Option[String],
    rule: Option[String] = None, metadataHash: Option[String] = None)

  private def _input_json(input: Input): Json = Json.obj(
    "schemaVersion" -> "sm-workflow.scalafmt-preview-input.v1".asJson,
    "path" -> input.path.asJson, "source" -> input.source.asJson,
    "configFiles" -> Json.obj(input.configFiles.toVector.sortBy(_._1).map((key, value) => key -> value.asJson)*))
  private def _hash(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _valid_input(input: Input): Boolean = Option(input).exists { value =>
    Option(value.path).exists(_.endsWith(".scala")) && Option(value.source).isDefined &&
      Option(value.configFiles).exists(files => files.size <= 32 && files.forall { (path, content) =>
        Option(path).exists(p => WorkspaceFilePath.parseC(p).isSuccess) && Option(content).isDefined
      })
  }

  enum Attempt {
    case Observed(result: ProcessExecutionResult)
    case AdmissionRejected(reason: org.goldenport.Conclusion)
    case Uncertain(reason: org.goldenport.Conclusion)
  }

  def requestC(input: Input): Consequence[ProcessExecutionRequest] = for {
    _ <- if (_valid_input(input) && input.configFiles.contains(".scalafmt.conf")) Consequence.unit
      else Consequence.stateConflict("Invalid Scala formatter preview input")
    _ <- WorkspaceFilePath.parseC(input.path)
    data = _input_json(input).noSpaces.getBytes(UTF_8).toVector
    _ <- if (data.size <= _limit) Consequence.unit else Consequence.stateConflict("Scala formatter input exceeds bound")
    id <- ProcessCapabilityId.parseC(capability)
  } yield ProcessExecutionRequest(id, input = ProcessExecutionInput.Bytes(data), requestedLimits = ProcessExecutionLimits(
    launchTimeoutMillis = Some(5000), executionTimeoutMillis = Some(70000), terminationGraceMillis = Some(1000),
    stdinBytes = Some(_limit.toLong), stdoutBytes = Some(_limit.toLong), stderrBytes = Some(65536),
    argumentCount = Some(16), argumentBytes = Some(4096), artifactCount = Some(1), artifactBytes = Some(1),
    workAreaBytes = Some(4194304)))

  def attemptC(core: ActionCall.Core, request: ProcessExecutionRequest): Consequence[Attempt] =
    _attempt_c(core, request, capability)

  private def _attempt_c(core: ActionCall.Core, request: ProcessExecutionRequest, selected: String): Consequence[Attempt] =
    if (Option(request).forall(value => value.capability.value != selected || value.arguments.nonEmpty ||
        value.workingDirectory.nonEmpty || value.inputFiles.nonEmpty || value.resourceTrees.nonEmpty || value.outputs.nonEmpty))
      Consequence.stateConflict("Scala formatter request crosses its fixed process recipe")
    else ProcessExecutionAdmission.resolveC(core.executionContext.scope, request) match {
      case Consequence.Failure(reason) => Consequence.success(Attempt.AdmissionRejected(reason))
      case Consequence.Success(execution) => new Execution(core).runC(execution) match {
        case Consequence.Success(result) => Consequence.success(Attempt.Observed(result))
        case Consequence.Failure(reason) => Consequence.success(Attempt.Uncertain(reason))
      }
    }

  /** An observed preview remains separate from any candidate write. The hash
    * binds the exact canonical input, including source and selected config. */
  def previewC(core: ActionCall.Core, input: Input): Consequence[Preview] = for {
    request <- requestC(input)
    outcome <- attemptC(core, request)
    digest = _hash(_input_json(input).noSpaces.getBytes(UTF_8))
    base = Preview(PreviewStatus.FormalFailure, input.path, digest, "3.8.6", None, None, None)
  } yield outcome match {
    case Attempt.AdmissionRejected(reason) => base.copy(status = PreviewStatus.Unavailable,
      diagnostic = Some(reason.show.take(4096)))
    case Attempt.Uncertain(reason) => base.copy(status = PreviewStatus.Uncertain,
      diagnostic = Some(reason.show.take(4096)))
    case Attempt.Observed(result) =>
      val observed = base.copy(programIdentity = Some(result.safeProgramIdentity))
      result.termination match {
        case ProcessExecutionTermination.TerminationUnconfirmed => observed.copy(status = PreviewStatus.Uncertain)
        case ProcessExecutionTermination.NotStarted | ProcessExecutionTermination.LaunchFailed =>
          observed.copy(status = PreviewStatus.Unavailable)
        case ProcessExecutionTermination.Exited(0) => replacement(input, result) match {
          case Some(text) if text == input.source => observed.copy(status = PreviewStatus.Unchanged)
          case Some(text) => observed.copy(status = PreviewStatus.Applied, replacement = Some(text))
          case None => observed.copy(diagnostic = Some("Malformed or unbound formatter response"))
        }
        case _ => observed.copy(diagnostic = Some(new String(result.stderr.content.toArray, UTF_8).take(4096)))
      }
  }

  /** The caller authenticates the effective selected rule and its supported
    * options. The companion runs only that fixed syntactic rule; it does not
    * run other project rules or infer SemanticDB availability. */
  def scalafixC(core: ActionCall.Core, input: Input, rule: String, scalaVersion: String): Consequence[Preview] = for {
    _ <- if (_valid_input(input)) Consequence.unit
      else Consequence.stateConflict("Invalid Scalafix preview input")
    _ <- WorkspaceFilePath.parseC(input.path)
    _ <- if (Set[String]("RedundantSyntax.finalObject", "ProcedureSyntax").contains(rule) &&
      Set[String]("2.12.18", "3.3.8").contains(scalaVersion))
      Consequence.unit else Consequence.stateConflict("Unsupported Scalafix preview rule or dialect")
    json = _input_json(input).mapObject(_.add("schemaVersion", "sm-workflow.scalafix-preview-input.v1".asJson)
      .add("rule", rule.asJson).add("scalaVersion", scalaVersion.asJson))
    data = json.noSpaces.getBytes(UTF_8).toVector
    _ <- if (data.size <= _limit) Consequence.unit else Consequence.stateConflict("Scalafix input exceeds bound")
    id <- ProcessCapabilityId.parseC(scalafixCapability)
    request = ProcessExecutionRequest(id, input = ProcessExecutionInput.Bytes(data), requestedLimits = ProcessExecutionLimits(
      launchTimeoutMillis = Some(5000), executionTimeoutMillis = Some(70000), terminationGraceMillis = Some(1000),
      stdinBytes = Some(_limit.toLong), stdoutBytes = Some(_limit.toLong), stderrBytes = Some(65536),
      argumentCount = Some(16), argumentBytes = Some(4096), artifactCount = Some(1), artifactBytes = Some(1),
      workAreaBytes = Some(4194304)))
    outcome <- _attempt_c(core, request, scalafixCapability)
    base = Preview(PreviewStatus.FormalFailure, input.path, _hash(data.toArray), "0.12.1", None, None, None, Some(rule))
  } yield outcome match {
    case Attempt.AdmissionRejected(reason) => base.copy(status = PreviewStatus.Unavailable, diagnostic = Some(reason.show.take(4096)))
    case Attempt.Uncertain(reason) => base.copy(status = PreviewStatus.Uncertain, diagnostic = Some(reason.show.take(4096)))
    case Attempt.Observed(result) =>
      val observed = base.copy(programIdentity = Some(result.safeProgramIdentity))
      result.termination match {
        case ProcessExecutionTermination.TerminationUnconfirmed => observed.copy(status = PreviewStatus.Uncertain)
        case ProcessExecutionTermination.NotStarted | ProcessExecutionTermination.LaunchFailed => observed.copy(status = PreviewStatus.Unavailable)
        case ProcessExecutionTermination.Exited(0) =>
          val output = if (result.stdout.truncated || result.stderr.truncated || result.stdout.content.size > _limit) None
            else scala.util.Try(UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(result.stdout.content.toArray)).toString).toOption
              .flatMap(text => io.circe.jawn.JawnParser(allowDuplicateKeys = false).parse(text).toOption)
              .filter(json => json.asObject.exists(o => o.keys.toSet == Set[String]("schemaVersion", "status", "path", "rule", "source", "diagnostic")) &&
                json.hcursor.get[String]("schemaVersion").contains("sm-workflow.scalafix-preview-output.v1") &&
                json.hcursor.get[String]("status").contains("completed") && json.hcursor.get[String]("path").contains(input.path) &&
                json.hcursor.get[String]("rule").contains(rule) && json.hcursor.get[String]("diagnostic").isRight)
              .flatMap(_.hcursor.get[String]("source").toOption).filter(_.getBytes(UTF_8).length <= _limit)
          output match {
            case Some(text) if text == input.source => observed.copy(status = PreviewStatus.Unchanged)
            case Some(text) => observed.copy(status = PreviewStatus.Applied, replacement = Some(text))
            case None => observed.copy(diagnostic = Some("Malformed or unbound Scalafix response"))
          }
        case _ => observed.copy(diagnostic = Some(new String(result.stderr.content.toArray, UTF_8).take(4096)))
      }
  }

  /** Run a single pinned semantic preview using exactly the supplied SemanticDB.
    * Metadata freshness is established by the provider; the caller owns its
    * candidate and compiler provenance and admits any proposed edit. */
  def semanticScalafixC(core: ActionCall.Core, input: Input, scalaVersion: String,
    metadata: Vector[Byte]): Consequence[Preview] = for {
    _ <- if (_valid_input(input) && Option(metadata).exists(_.size <= _limit) && scalaVersion == "3.3.8") Consequence.unit
      else Consequence.stateConflict("Invalid semantic Scalafix preview input")
    _ <- WorkspaceFilePath.parseC(input.path)
    encoded = java.util.Base64.getEncoder.encodeToString(metadata.toArray)
    json = _input_json(input).mapObject(_.add("schemaVersion", "sm-workflow.scalafix-semantic-preview-input.v1".asJson)
      .add("rule", "OrganizeImports".asJson).add("scalaVersion", scalaVersion.asJson).add("metadata", encoded.asJson))
    data = json.noSpaces.getBytes(UTF_8).toVector
    _ <- if (data.size <= _limit) Consequence.unit else Consequence.stateConflict("Semantic Scalafix input exceeds bound")
    id <- ProcessCapabilityId.parseC(scalafixCapability)
    request = ProcessExecutionRequest(id, input = ProcessExecutionInput.Bytes(data), requestedLimits = ProcessExecutionLimits(
      launchTimeoutMillis = Some(5000), executionTimeoutMillis = Some(70000), terminationGraceMillis = Some(1000),
      stdinBytes = Some(_limit.toLong), stdoutBytes = Some(_limit.toLong), stderrBytes = Some(65536),
      argumentCount = Some(16), argumentBytes = Some(4096), artifactCount = Some(1), artifactBytes = Some(1),
      workAreaBytes = Some(4194304)))
    outcome <- _attempt_c(core, request, scalafixCapability)
    base = Preview(PreviewStatus.FormalFailure, input.path, _hash(data.toArray), "0.12.1", None, None, None,
      Some("OrganizeImports"), Some(_hash(metadata.toArray)))
  } yield outcome match {
    case Attempt.AdmissionRejected(reason) => base.copy(status = PreviewStatus.Unavailable, diagnostic = Some(reason.show.take(4096)))
    case Attempt.Uncertain(reason) => base.copy(status = PreviewStatus.Uncertain, diagnostic = Some(reason.show.take(4096)))
    case Attempt.Observed(result) =>
      val observed = base.copy(programIdentity = Some(result.safeProgramIdentity))
      result.termination match {
        case ProcessExecutionTermination.TerminationUnconfirmed => observed.copy(status = PreviewStatus.Uncertain)
        case ProcessExecutionTermination.NotStarted | ProcessExecutionTermination.LaunchFailed => observed.copy(status = PreviewStatus.Unavailable)
        case ProcessExecutionTermination.Exited(0) =>
          val output = if (result.stdout.truncated || result.stderr.truncated || result.stdout.content.size > _limit) None
            else scala.util.Try(UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(result.stdout.content.toArray)).toString).toOption
              .flatMap(text => io.circe.jawn.JawnParser(allowDuplicateKeys = false).parse(text).toOption)
              .filter(json => json.asObject.exists(o => o.keys.toSet == Set[String]("schemaVersion", "status", "path", "rule", "source", "diagnostic", "exitCode")) &&
                json.hcursor.get[String]("schemaVersion").contains("sm-workflow.scalafix-preview-output.v1") &&
                json.hcursor.get[String]("path").contains(input.path) && json.hcursor.get[String]("rule").contains("OrganizeImports") &&
                json.hcursor.get[String]("diagnostic").isRight && json.hcursor.get[Int]("exitCode").isRight)
          output match {
            case Some(json) if json.hcursor.get[String]("status").contains("metadata-required") &&
                json.hcursor.downField("source").focus.contains(Json.Null) &&
                ((json.hcursor.get[Int]("exitCode").contains(16) &&
                  json.hcursor.get[String]("diagnostic").exists(_.contains("error: Stale SemanticDB"))) ||
                 (json.hcursor.get[Int]("exitCode").contains(8) &&
                  json.hcursor.get[String]("diagnostic").exists(_.contains("error: SemanticDB not found:")))) =>
              observed.copy(status = PreviewStatus.MetadataRequired, diagnostic = json.hcursor.get[String]("diagnostic").toOption.map(_.take(4096)))
            case Some(json) if json.hcursor.get[String]("status").contains("completed") &&
                json.hcursor.get[Int]("exitCode").contains(0) =>
              json.hcursor.get[String]("source").toOption.filter(_.getBytes(UTF_8).length <= _limit) match {
                case Some(text) if text == input.source => observed.copy(status = PreviewStatus.Unchanged)
                case Some(text) => observed.copy(status = PreviewStatus.Applied, replacement = Some(text))
                case None => observed.copy(diagnostic = Some("Malformed semantic preview source"))
              }
            case Some(json) => observed.copy(diagnostic = json.hcursor.get[String]("diagnostic").toOption.map(_.take(4096)))
            case None => observed.copy(diagnostic = Some("Malformed or unbound semantic preview response"))
          }
        case _ => observed.copy(diagnostic = Some(new String(result.stderr.content.toArray, UTF_8).take(4096)))
      }
  }

  /** Strictly associate the single returned source with its literal requested path. */
  def replacement(input: Input, result: ProcessExecutionResult): Option[String] = {
    if (Option(input).isEmpty || Option(result).forall(value => value.termination != ProcessExecutionTermination.Exited(0) ||
        value.stdout.truncated || value.stderr.truncated || value.stdout.content.size > _limit)) None
    else scala.util.Try(UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(result.stdout.content.toArray)).toString).toOption
      .flatMap(text => io.circe.jawn.JawnParser(allowDuplicateKeys = false).parse(text).toOption)
      .filter(json => json.asObject.exists(o => o.keys.toSet == Set[String]("schemaVersion", "status", "path", "source", "diagnostic")) &&
        json.hcursor.get[String]("schemaVersion").contains("sm-workflow.scalafmt-preview-output.v1") &&
        json.hcursor.get[String]("status").contains("completed") && json.hcursor.get[String]("path").contains(input.path) &&
        json.hcursor.get[String]("diagnostic").isRight)
      .flatMap(_.hcursor.get[String]("source").toOption)
      .filter(_.getBytes(UTF_8).length <= _limit)
  }

  private final class Execution(val core: ActionCall.Core) extends ProcedureActionCall {
    def runC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionResult] = executeProgram(process_exec(execution))
    def execute(): Consequence[org.goldenport.protocol.operation.OperationResponse] =
      Consequence.stateConflict("Scala tool preview is not a public Operation")
  }
}
