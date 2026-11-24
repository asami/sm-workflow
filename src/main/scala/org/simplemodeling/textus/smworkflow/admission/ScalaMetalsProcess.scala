package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.{Json, Encoder, Decoder}
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.resource.ResourceReadLimits
import org.goldenport.cncf.workspace.{WorkspaceId, WorkspaceFilePath, FileTarget, FileReadPolicy}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import DeterministicRepair.*

/** One finite request to an admitted fixed companion. The companion owns LSP;
  * CNCF owns process admission, WorkArea, limits and process-tree termination.
  * @since   Nov. 24, 2026
  * @version Nov. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object ScalaMetalsProcess {
  def repairC(source: GoalPhaseNativeRun.Session, frontier: InstanceRecord,
    controller: GoalPhaseOrderedProgression.State, candidate: Option[GoalPhaseNativeCandidate.Saved],
    validation: Option[GoalPhaseNativeValidation.Saved], repaired: Set[String], core: ActionCall.Core,
    declaration: GoalPhaseNativeRun.Declaration,
    fallback: => Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult],
    after: org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult => Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult]):
    Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = {
    val context = DeterministicRepair.stepContext(controller, candidate, validation)
      .filterNot(value => repaired.contains(value._1.identity))
    context match {
      case None => fallback
      case Some((step, selected, checked)) => DeterministicRepair.compilerLocator(checked.payload) match {
        case None => fallback
        case Some(finding) => attemptSinceC(source, core, frontier, checked.record, step.identity,
          checked.payload.candidate.tree, checked.record.revision.value.toString + ":" + finding.checkIdentity).flatMap {
          case Some(saved) if saved.value.outcome.contains("completed") && java.util.Objects.equals(saved.record, frontier) => for {
            material <- DeterministicRepair.compilerMaterialC(source, declaration, core, frontier, selected, checked, step.ownedPaths.toSet)
            proposal <- saved.value.proposal.map(Consequence.success).getOrElse(GoalPhaseNativeRun.invalid("Metals proposal absent"))
            _ <- if (material.exists(current => java.util.Objects.equals(current.document.text, proposal.expected) && java.util.Objects.equals(current.path, proposal.path) &&
              java.util.Objects.equals(current.workspace, proposal.workspace) && _rendered(current, saved.value.tool).contains(proposal.patched)))
              Consequence.unit else GoalPhaseNativeRun.invalid("Scala diagnostic proposal is no longer bound to its staged source")
            applied <- DeterministicRepair.applyC(source, declaration, core, frontier,
              proposal.copy(sourceRevision = frontier.revision.value.toString), step.ownedPaths.toSet)
            title = if (saved.value.tool.isEmpty) "Metals correction observed" else "Scalafix diagnostic correction observed"
            result <- GoalPhaseNativeRun.waitC(applied.record, title, "Continue with normal candidate inspection and validation.").flatMap(after)
          } yield result
          case Some(_) => fallback
          case None => for {
            material <- DeterministicRepair.compilerMaterialC(source, declaration, core, frontier, selected, checked, step.ownedPaths.toSet)
            result <- material match {
              case Some(value) if ScalaMetalsRepair.productionDiagnostic(value).nonEmpty =>
                executeC(source, declaration, core, frontier, step.identity, value).flatMap(saved =>
                GoalPhaseNativeRun.waitC(saved.record, "Metals attempt retained", "Continue using the actual bounded process outcome.").flatMap(after))
              case Some(value) if ScalaLintRepair.procedureRendered(value).nonEmpty =>
                _procedure_c(source, declaration, core, frontier, step.identity, value).flatMap {
                  case None => fallback
                  case Some(saved) => GoalPhaseNativeRun.waitC(saved.record, "Scalafix diagnostic attempt retained",
                    "Continue using the actual bounded process outcome.").flatMap(after)
                }
              case _ => fallback
            }
          } yield result
        }
      }
    }
  }

  enum MetalsAttempt {
    case Observed(result: ProcessExecutionResult)
    case AdmissionRejected(reason: org.goldenport.Conclusion)
    case Uncertain(reason: org.goldenport.Conclusion)
  }

  /** The component submits finite data to one host-fixed, argument-free recipe.
    * Admission and unknown execution remain separate observations. */
  def metalsAttemptC(core: ActionCall.Core, request: ProcessExecutionRequest): Consequence[MetalsAttempt] =
    if (!java.util.Objects.equals(request.capability.value, ScalaMetalsProcess.capability) || request.arguments.nonEmpty ||
        request.workingDirectory.nonEmpty || request.inputFiles.nonEmpty || request.resourceTrees.nonEmpty || request.outputs.nonEmpty)
      Consequence.stateConflict("Metals request crosses its fixed process recipe")
    else ProcessExecutionAdmission.resolveC(core.executionContext.scope, request) match {
      case Consequence.Failure(reason) => Consequence.success(MetalsAttempt.AdmissionRejected(reason))
      case Consequence.Success(execution) => new MetalsExecution(core).runC(execution) match {
        case Consequence.Success(result) => Consequence.success(MetalsAttempt.Observed(result))
        case Consequence.Failure(reason) => Consequence.success(MetalsAttempt.Uncertain(reason))
      }
    }
  private final class MetalsExecution(val core: ActionCall.Core) extends ProcedureActionCall {
    def runC(execution: ResolvedProcessExecution): Consequence[ProcessExecutionResult] =
      executeProgram(process_exec(execution))
    def execute(): Consequence[org.goldenport.protocol.operation.OperationResponse] =
      Consequence.stateConflict("Metals process projection is not a public Operation")
  }

  val capability = "sm-workflow-metals"
  val serverIdentity = "scala-metals-1-5-3"
  val intentCause = "ordered-scala-metals:intent"
  private val _result_cause = "ordered-scala-metals:result"
  private val _max_bytes = 1048576

  final case class Observation(termination: String, program: String, elapsedMillis: Long,
    stdout: String, stderr: String, truncated: Boolean)
  final case class DiagnosticTool(name: String, inputHash: String, engine: String, program: Option[String])
  final case class Attempt(session: String, sourceRevision: String, step: String, candidate: String,
    validationReference: String, outcome: Option[String], diagnostic: Option[String],
    proposal: Option[Transaction], process: Option[Observation], tool: Option[DiagnosticTool] = None)
  final case class Saved(record: InstanceRecord, value: Attempt)
  private given Encoder[Observation] = deriveEncoder
  private given Decoder[Observation] = deriveDecoder
  private given Encoder[DiagnosticTool] = deriveEncoder
  private given Decoder[DiagnosticTool] = deriveDecoder
  private given Encoder[Attempt] = deriveEncoder[Attempt].mapJson(_.mapObject(fields =>
    if (fields("tool").exists(_.isNull)) fields.remove("tool") else fields))
  private given Decoder[Attempt] = deriveDecoder

  val codec: WorkflowResultJsonV1.PayloadCodec[Attempt] = new WorkflowResultJsonV1.PayloadCodec[Attempt] {
    val typeIdentity = "ScalaMetalsAttempt"
    def encode(value: Attempt): Json = Json.obj("schemaVersion" -> Json.fromString(
      if (value.tool.isEmpty) "sm-workflow.scala-metals-attempt.v1" else "sm-workflow.scala-diagnostic-tool-attempt.v2"),
      "payload" -> value.asJson)
    def decode(value: Json): Either[String, Attempt] = for {
      result <- value.hcursor.get[Attempt]("payload").left.map(_.message)
      _ <- Either.cond(encode(result) == value && Vector(result.session, result.step, result.validationReference)
        .forall(text => text.nonEmpty && text.length <= 4096 && !text.exists(_.isControl)) &&
        result.sourceRevision.toLongOption.exists(_ > 0) && GitObjectId.parseC(result.candidate).isSuccess &&
        result.outcome.forall(Set("completed", "disabled", "unavailable", "timed-out", "incompatible", "no-action", "unknown")) &&
        result.outcome.contains("completed") == result.proposal.nonEmpty &&
        result.proposal.forall(p => validTransaction(p) && p.session == result.session && p.candidate == result.candidate &&
          p.sourceRevision == result.sourceRevision && p.validationReference == result.validationReference) &&
        result.diagnostic.forall(_.length <= 4096) && result.process.forall(p => p.stdout.getBytes(UTF_8).length <= _max_bytes &&
          p.stderr.getBytes(UTF_8).length <= 65536 && p.elapsedMillis >= 0) &&
        (result.outcome.nonEmpty || (result.proposal.isEmpty && result.diagnostic.isEmpty && result.process.isEmpty)) &&
        result.tool.forall(tool => tool.name == "scalafix-procedure-syntax" && result.process.isEmpty &&
          (result.outcome.isEmpty && tool.inputHash.isEmpty && tool.engine.isEmpty && tool.program.isEmpty ||
            result.outcome.nonEmpty && tool.inputHash.matches("[0-9a-f]{64}") && tool.engine == "0.12.1" &&
              tool.program.forall(program => program.nonEmpty && program.length <= 256 && !program.exists(_.isControl)) &&
              (!result.outcome.contains("completed") || tool.program.nonEmpty))),
        (), "invalid retained Metals attempt")
    } yield result
  }

  private def _reference(source: InstanceRecord, kind: String): WorkflowMechanicalTransition.Reference =
    WorkflowMechanicalTransition.Reference(source.identity.value + ":scala-metals:" + source.revision.value + ":" + kind, "1")

  private def _save_c(adapter: WorkflowManagedMechanicalTransition[Attempt], declaration: GoalPhaseNativeRun.Declaration,
    core: ActionCall.Core, source: InstanceRecord, previous: InstanceRecord, payload: Attempt, kind: String): Consequence[InstanceRecord] = {
    val pure = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
    adapter.executeC(WorkflowMechanicalTransition.Intent(declaration.configuration, _reference(source, kind), previous,
      previous.revision, GoalPhaseNativeRun.history(previous, "phase.Repair", if (kind == "intent") intentCause else _result_cause),
      TypedValue(codec.typeIdentity, payload)), pure)(using core.executionContext).map(_.receipt.next)
  }

  def retainedC(session: GoalPhaseNativeRun.Session, core: ActionCall.Core, source: InstanceRecord): Consequence[Saved] = for {
    adapter <- session.storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, codec)
    intent <- adapter.loadRetainedC(_reference(source, "intent"))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(GoalPhaseNativeRun.invalid("Metals intent absent")))
    initial <- codec.decode(codec.encode(intent.receipt.payload.value)).fold(GoalPhaseNativeRun.invalid, Consequence.success)
    _ <- if (java.util.Objects.equals(initial.session, source.identity.value) && initial.sourceRevision == source.revision.value.toString && initial.outcome.isEmpty)
      Consequence.unit else GoalPhaseNativeRun.invalid("Metals intent crosses its source")
    intended <- GoalPhaseSourceHistoryRecovery._adjacent_c(session.current, source, intent.receipt.next, intentCause)
    result <- adapter.loadRetainedC(_reference(source, "result"))(using core.executionContext)
    saved <- result match {
      case None => Consequence.success(Saved(intended, initial))
      case Some(found) => for {
        value <- codec.decode(codec.encode(found.receipt.payload.value)).fold(GoalPhaseNativeRun.invalid, Consequence.success)
        initialtool = value.tool.map(_.copy(inputHash = "", engine = "", program = None))
        _ <- if (value.copy(outcome = None, diagnostic = None, proposal = None, process = None, tool = initialtool) == initial && value.outcome.nonEmpty)
          Consequence.unit else GoalPhaseNativeRun.invalid("Metals result crosses its intent")
        next <- GoalPhaseSourceHistoryRecovery._adjacent_c(session.current, intended, found.receipt.next, _result_cause)
      } yield Saved(next, value)
    }
  } yield saved

  private def _rendered(material: CompilerMaterial, tool: Option[DiagnosticTool]): Option[String] =
    if (tool.isEmpty) ScalaMetalsRepair.productionRendered(material) else ScalaLintRepair.procedureRendered(material)

  private def _procedure_c(session: GoalPhaseNativeRun.Session, declaration: GoalPhaseNativeRun.Declaration,
    core: ActionCall.Core, frontier: InstanceRecord, step: String, material: CompilerMaterial): Consequence[Option[Saved]] = for {
    selected <- WorkflowScalaLintAction.rewriteC(core, declaration.target.root, material.workspace, "ProcedureSyntax")
    result <- selected.fold(Consequence.success(Option.empty[Saved])) { config => for {
      adapter <- session.storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, codec)
      initial = Attempt(material.session, frontier.revision.value.toString, step, material.candidate,
        material.validationReference, None, None, None, None, Some(DiagnosticTool("scalafix-procedure-syntax", "", "", None)))
      intended <- _save_c(adapter, declaration, core, frontier, frontier, initial, "intent")
      preview <- WorkflowScalaToolProcess.scalafixC(core, WorkflowScalaToolProcess.Input(material.path,
        material.document.text, Map(".scalafix.conf" -> config.configuration)), "ProcedureSyntax", config.scalaVersion)
      proposal = for {
        replacement <- preview.replacement
        if ScalaLintRepair.procedureRendered(material).contains(replacement)
        transaction = Transaction(material.session, frontier.revision.value.toString, material.candidate,
          material.validationReference, material.path, material.document.text, replacement, material.workspace)
        if validTransaction(transaction)
      } yield transaction
      outcome = preview.status match {
        case WorkflowScalaToolProcess.PreviewStatus.Applied if proposal.nonEmpty => "completed"
        case WorkflowScalaToolProcess.PreviewStatus.Unavailable => "unavailable"
        case WorkflowScalaToolProcess.PreviewStatus.Uncertain => "unknown"
        case _ => "no-action"
      }
      recorded = initial.copy(outcome = Some(outcome), diagnostic = preview.diagnostic, proposal = proposal,
        tool = initial.tool.map(_.copy(inputHash = preview.inputHash, engine = preview.engineVersion, program = preview.programIdentity)))
      next <- _save_c(adapter, declaration, core, frontier, intended, recorded, "result")
    } yield Some(Saved(next, recorded)) }
  } yield result

  /** Resolve one attempt in the authenticated history since the selected validation. */
  def attemptSinceC(session: GoalPhaseNativeRun.Session, core: ActionCall.Core, frontier: InstanceRecord,
    validation: InstanceRecord, step: String, candidate: String, reference: String): Consequence[Option[Saved]] = for {
    prefix <- GoalPhaseNativeHistory.prefixC(session.current, frontier.revision)
    _ <- if (java.util.Objects.equals(prefix, frontier) && java.util.Objects.equals(validation.identity, frontier.identity) &&
        validation.revision.value <= frontier.revision.value) Consequence.unit
      else GoalPhaseNativeRun.invalid("Metals history crosses its validation frontier")
    intents = prefix.history.filter(entry => entry.revision.value > validation.revision.value &&
      java.util.Objects.equals(entry.causation.value, intentCause))
    saved <- intents match {
      case Vector() => Consequence.success(Option.empty[Saved])
      case Vector(entry) => for {
        predecessor <- GoalPhaseNativeHistory.prefixC(session.current, InstanceRevision(entry.revision.value - 1))
        found <- retainedC(session, core, predecessor)
        _ <- if (found.value.step == step && found.value.candidate == candidate &&
            found.value.validationReference == reference && found.record.revision.value <= frontier.revision.value)
          Consequence.unit else GoalPhaseNativeRun.invalid("Metals attempt crosses its failed validation")
      } yield Some(found)
      case _ => GoalPhaseNativeRun.invalid("Duplicate Metals intents for the failed validation")
    }
  } yield saved

  def executeC(session: GoalPhaseNativeRun.Session, declaration: GoalPhaseNativeRun.Declaration, core: ActionCall.Core,
    source: InstanceRecord, step: String, material: CompilerMaterial): Consequence[Saved] = for {
    adapter <- session.storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, codec)
    initial = Attempt(material.session, source.revision.value.toString, step, material.candidate,
      material.validationReference, None, None, None, None)
    intended <- _save_c(adapter, declaration, core, source, source, initial, "intent")
    result <- policyC(core, material.workspace) match {
      case Consequence.Failure(reason) => Consequence.success(initial.copy(outcome = Some("unavailable"),
        diagnostic = Some(reason.show.take(4096))))
      case Consequence.Success(policy) => if (!policy.enabled) Consequence.success(initial.copy(outcome = Some("disabled")))
      else if (!policy.serverIdentity.contains(serverIdentity))
        Consequence.success(initial.copy(outcome = Some("unavailable"), diagnostic = Some("No supported fixed Metals provider selected")))
      else requestC(material, policy) match {
        case Consequence.Failure(reason) => Consequence.success(initial.copy(outcome = Some("unavailable"), diagnostic = Some(reason.show.take(4096))))
        case Consequence.Success(request) => metalsAttemptC(core, request).map {
        case MetalsAttempt.AdmissionRejected(reason) =>
          initial.copy(outcome = Some("unavailable"), diagnostic = Some(reason.show.take(4096)))
        case MetalsAttempt.Uncertain(reason) =>
          initial.copy(outcome = Some("unknown"), diagnostic = Some(reason.show.take(4096)))
        case MetalsAttempt.Observed(process) => complete(initial, material, policy, process)
      }
      }
    }
    next <- _save_c(adapter, declaration, core, source, intended, result, "result")
  } yield Saved(next, result)

  def policyC(core: ActionCall.Core, workspaceIdentity: String): Consequence[ScalaMetalsRepair.Policy] = for {
    workspace <- WorkspaceId.parseC(workspaceIdentity)
    path <- WorkspaceFilePath.parseC("project.yaml")
    result <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), path, FileReadPolicy(ResourceReadLimits(65536)))
    policy <- WorkflowProjectDeclaration.metalsTextC(result.text)
  } yield policy

  def requestC(material: CompilerMaterial, policy: ScalaMetalsRepair.Policy): Consequence[ProcessExecutionRequest] = {
    def _position_(position: Position) = Json.obj("line" -> position.line.asJson, "character" -> position.character.asJson)
    val diagnostic = ScalaMetalsRepair.productionDiagnostic(material).getOrElse(material.diagnostic)
    val input = Json.obj("schemaVersion" -> "sm-workflow.metals-input.v1".asJson,
      "serverIdentity" -> serverIdentity.asJson, "timeoutMillis" -> policy.timeoutMillis.asJson,
      "document" -> Json.obj("uri" -> material.document.uri.asJson, "version" -> material.document.version.asJson,
        "text" -> material.document.text.asJson),
      "diagnostic" -> Json.obj("code" -> diagnostic.code.asJson, "message" -> diagnostic.message.asJson,
        "range" -> Json.obj("start" -> _position_(diagnostic.range.start), "end" -> _position_(diagnostic.range.end))))
      .noSpaces.getBytes(UTF_8).toVector
    for {
      _ <- if (input.size <= _max_bytes) Consequence.unit else Consequence.stateConflict("Metals input exceeds finite bound")
      id <- ProcessCapabilityId.parseC(capability)
    } yield ProcessExecutionRequest(id, input = ProcessExecutionInput.Bytes(input), requestedLimits = ProcessExecutionLimits(
      launchTimeoutMillis = Some(5000), executionTimeoutMillis = Some(policy.timeoutMillis.toLong + 5000),
      terminationGraceMillis = Some(1000), stdinBytes = Some(_max_bytes.toLong), stdoutBytes = Some(_max_bytes.toLong),
      stderrBytes = Some(65536), argumentCount = Some(16), argumentBytes = Some(4096),
      artifactCount = Some(1), artifactBytes = Some(1), workAreaBytes = Some(4194304)))
  }

  def complete(initial: Attempt, material: CompilerMaterial, policy: ScalaMetalsRepair.Policy,
    result: ProcessExecutionResult): Attempt = {
    val output = new String(result.stdout.content.toArray, UTF_8)
    val error = new String(result.stderr.content.toArray, UTF_8)
    val observed = Observation(result.termination.toString, result.safeProgramIdentity, result.elapsedMillis, output, error,
      result.stdout.truncated || result.stderr.truncated)
    val base = initial.copy(process = Some(observed))
    result.termination match {
      case ProcessExecutionTermination.TerminationUnconfirmed => base.copy(outcome = Some("unknown"))
      case ProcessExecutionTermination.TimedOut => base.copy(outcome = Some("timed-out"))
      case ProcessExecutionTermination.NotStarted | ProcessExecutionTermination.LaunchFailed => base.copy(outcome = Some("unavailable"))
      case ProcessExecutionTermination.Exited(0) if !observed.truncated =>
        val parsed = scala.util.Try(UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(result.stdout.content.toArray)).toString)
          .toOption.flatMap(text => io.circe.jawn.JawnParser(allowDuplicateKeys = false).parse(text).toOption)
        val valid = parsed.filter(json => json.asObject.exists(obj => obj.size == 4 &&
          obj.contains("schemaVersion") && obj.contains("serverIdentity") && obj.contains("status") && obj.contains("actions")) &&
          json.hcursor.get[String]("schemaVersion").contains("sm-workflow.metals-output.v1") &&
          json.hcursor.get[String]("serverIdentity").contains(serverIdentity))
        valid match {
          case Some(json) if json.hcursor.get[String]("status").contains("completed") =>
            val actions = json.hcursor.downField("actions").focus.getOrElse(Json.Null)
            ScalaMetalsRepair.productionOutcome(material, policy, actions) match {
              case ScalaMetalsRepair.Malformed(reason) => base.copy(outcome = Some("incompatible"), diagnostic = Some(reason.take(4096)))
              case _ => ScalaMetalsRepair.productionTransaction(material, policy, actions) match {
                case Some(proposal) => base.copy(outcome = Some("completed"), proposal = Some(proposal))
                case None => base.copy(outcome = Some("no-action"), diagnostic = Some("No exact allowlisted edit"))
              }
            }
          case Some(json) =>
            val status = json.hcursor.get[String]("status").toOption.filter(Set("timed-out", "unavailable", "incompatible"))
            base.copy(outcome = Some(status.getOrElse("incompatible")))
          case None => base.copy(outcome = Some("incompatible"), diagnostic = Some("Malformed companion response"))
        }
      case _ => base.copy(outcome = Some("incompatible"))
    }
  }
}
