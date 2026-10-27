package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceId, WorkspaceFilePath}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V

/** NeedInformation is a read within the outstanding work, not its completion.
  * Common Evidence retains immutable delivered batches; its Journal supplies a
  * paged case history. No work assignment, repair charge or execution occurs.
  * @since   Oct. 25, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object IncrementalSbtDiagnosisInformation {
  val operation = "resolveGoalPhaseDiagnosticInformation"
  private val _output = "diagnostic-information:"
  final case class Selection(identity: String, kind: String, reason: String, check: Option[String],
    stream: Option[String], occurrenceLine: Option[Int], path: Option[String])
  final case class Item(selection: Selection, status: String, coverage: String, sourceLoss: String,
    locator: String, text: Option[String], message: String)
  final case class Batch(schemaVersion: String, work: String, revision: String,
    requestIdentity: String, candidate: Option[GoalPhaseOrderedProgression.Snapshot],
    observedAt: String, resolution: String, items: Vector[Item])
  given Encoder[Selection] = deriveEncoder
  given Decoder[Selection] = deriveDecoder
  given Encoder[Item] = deriveEncoder
  given Decoder[Item] = deriveDecoder
  given Encoder[Batch] = deriveEncoder
  given Decoder[Batch] = deriveDecoder

  /** Neutral retained failure context shared with standalone WorkOrders. */
  final case class FailureContext(sourceRevision: String, candidate: GoalPhaseOrderedProgression.Snapshot,
    selected: Vector[GoalPhaseNativeValidation.Check], receipts: Vector[RepositorySyncNativeMergeValidation.Receipt])
  final case class WorkContext(ownedPaths: Vector[String], failure: Option[FailureContext],
    candidate: Option[GoalPhaseOrderedProgression.Snapshot])
  private def context(work: GoalPhaseNativeWork.Request): WorkContext = WorkContext(
    work.stepIdentity.flatMap(id => work.selectedPlan.steps.find(_.identity == id)).map(_.ownedPaths)
      .getOrElse(work.selectedPlan.steps.flatMap(_.ownedPaths)),
    work.checkFailure.map(f => FailureContext(f.validation.sourceRevision, f.validation.candidate,
      f.validation.selected, f.validation.receipts)), failedCandidate(work))

  trait SourceReader {
    def readC(path: String): Consequence[Option[String]]
  }
  private def _selection(v: V.GoalPhaseDiagnosticSelection): Selection =
    Selection(v.identity, v.kind, v.reason, v.check, v.stream, v.occurrenceLine, v.path)
  private def _exact(v: String): Boolean = v != null && v.trim.nonEmpty && v.trim == v &&
    !v.exists(_.isControl) && java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(v)
  def validateC(input: V.GoalPhaseDiagnosticInformationInput): Consequence[Unit] = {
    val valid = try {
      input != null && input.issuedWorkContext != null && _exact(input.requestIdentity) &&
        input.selections != null && input.selections.size <= 20 &&
        input.selections.map(_.identity).distinct.size == input.selections.size &&
        input.selections.forall(s => s != null && Vector(s.identity, s.kind, s.reason).forall(_exact) &&
          Vector(s.check, s.stream, s.path).forall(x => x != null && x.forall(_exact)) &&
          s.occurrenceLine != null && s.occurrenceLine.forall(_ > 0)) &&
        input.knownEvidence != null && input.knownEvidence.forall(e => e != null &&
          CommonEvidence.validIdentity(e.identity) && e.revision == "1") &&
        input.historyAfter != null && input.historyAfter.forall(DevelopmentJournalEvidence.validIdentity)
    } catch { case scala.util.control.NonFatal(_) => false }
    if (valid) Consequence.unit else Consequence.stateConflict("Diagnostic information requires a unique reasoned batch and its issued work context")
  }

  /** Equality consumes every canonical issued-work field, including the handle,
    * completion route and execution requirement, rather than caller assertions.
    */
  def sameWorkC(actual: V.WorkflowIssuedWorkContext, supplied: V.WorkflowIssuedWorkContext): Consequence[Unit] = for {
    expected <- actual.toRecord().toJsonStringC
    received <- supplied.toRecord().toJsonStringC
    a <- _context_json_c(expected)
    b <- _context_json_c(received)
    _ <- if (a == b) Consequence.unit else Consequence.stateConflict(
      "Diagnostic request differs from the currently issued work at " + _difference(a, b, "issuedWorkContext"))
  } yield ()

  // Generic CNCF Records cross the public snake_case naming projection while
  // generated value fields use camelCase. Compare that existing public codec's
  // canonical representation, rejecting ambiguous aliases rather than dropping
  // fields, values, array members or completion/authority requirements.
  private def _context_json_c(text: String): Consequence[Json] =
    io.circe.parser.parse(text).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
      .fold(Consequence.stateConflict(_), Consequence.success)

  private def _difference(actual: Json, supplied: Json, path: String): String =
    (actual.asObject, supplied.asObject) match {
      case (Some(a), Some(b)) =>
        (a.keys.toVector ++ b.keys.toVector).distinct.sorted.find(k => a(k) != b(k))
          .map(k => (a(k), b(k)) match {
            case (Some(x), Some(y)) => _difference(x, y, path + "." + k)
            case _ => path + "." + k
          }).getOrElse(path)
      case _ => (actual.asArray, supplied.asArray) match {
        case (Some(a), Some(b)) =>
          a.zip(b).indexWhere((x, y) => x != y) match {
            case -1 => path + ".length"
            case i => _difference(a(i), b(i), path + "[" + i + "]")
          }
        case _ => path
      }
    }

  private def _missing(s: Selection, why: String): Item =
    Item(s, "unavailable", "none", "unknown", "unavailable", None, why)
  private def _valid_shape(s: Selection): Boolean = s.kind match {
    case "source-file" => s.path.nonEmpty && s.check.isEmpty && s.stream.isEmpty && s.occurrenceLine.isEmpty
    case "full-output" | "execution-context" => s.check.nonEmpty && s.stream.exists(Set("stdout", "stderr")) &&
      s.path.isEmpty && s.occurrenceLine.isEmpty
    case "failure-context" | "cause-chain" | "suite-context" => s.check.nonEmpty &&
      s.stream.exists(Set("stdout", "stderr")) && s.occurrenceLine.nonEmpty && s.path.isEmpty
    case _ => false
  }

  /** Pure retained-log selection and injected managed File access make the
    * evidence semantics testable without launching a command or a second AI.
    */
  def itemsC(work: GoalPhaseNativeWork.Request, selections: Vector[Selection], files: SourceReader): Consequence[Vector[Item]] =
    itemsC(context(work), selections, files)

  def itemsC(work: WorkContext, selections: Vector[Selection], files: SourceReader): Consequence[Vector[Item]] = {
    val observed = scala.collection.mutable.Map.empty[String, Consequence[Option[String]]]
    val samecall = new SourceReader {
      def readC(path: String) = observed.getOrElseUpdate(path, files.readC(path))
    }
    selections.foldLeft(Consequence.success(Vector.empty[Item])) { (acc, selection) => for {
      items <- acc
      item <- _item_c(work, selection, samecall)
    } yield items :+ item }
  }

  private def _item_c(work: WorkContext, s: Selection, files: SourceReader): Consequence[Item] = {
    if (!_valid_shape(s)) Consequence.success(_missing(s, "Unsupported semantic selection or incompatible selector fields"))
    else if (s.kind == "source-file") {
      val owners = work.ownedPaths
      val path = s.path.get
      if (!WorkspaceFilePath.parseC(path).isSuccess || !owners.exists(p => path == p || path.startsWith(p + "/")))
        Consequence.success(_missing(s, "Source is outside this work's selected repository ownership; no read attempted"))
      else Consequence.success(files.readC(path).map {
        case None => _missing(s, "Managed File reports no text")
        case Some(text) => Item(s, "available", "whole-observed-file", "none", "repository:" + path, Some(text),
          "Current managed File observation; candidate applicability unverified. Retained batch never asserts current file contents after edits.")
      }.fold(_ => _missing(s, "Managed File read unavailable"), v => v))
    } else {
      val result = for {
        failure <- work.failure
        check <- failure.selected.find(_.identity == s.check.get)
        receipt <- failure.receipts.find(_.identity == check.identity)
        view = IncrementalSbtFailureDiagnosis.extract(receipt, check.kind, failure.sourceRevision, failure.candidate)
        source <- view.sources.find(_.stream == s.stream.get)
      } yield {
        val raw = if (s.stream.contains("stdout")) receipt.stdout else receipt.stderr
        val lines = if (raw.isEmpty) Vector.empty else raw.split("\\r?\\n", -1).toVector
        val locator = s"check:${check.identity}@${failure.sourceRevision}/${s.stream.get}"
        if (Set("unavailable", "undecodable")(source.textStatus) || lines.isEmpty)
          _missing(s, "Selected retained stream has no available decodable text").copy(sourceLoss = source.sourceLoss, locator = locator)
        else if (Set("full-output", "execution-context")(s.kind))
          Item(s, if (source.sourceLoss == "none-observed") "available" else "partial", "full-retained-stream", source.sourceLoss,
            locator + s":1-${lines.size}", Some(raw), "Full available retained stream; missing source bytes are not reconstructed")
        else view.groups.flatMap(_.occurrences).find(o => o.stream == s.stream.get && o.firstLine == s.occurrenceLine.get) match {
          case None => _missing(s, "No displayed diagnostic occurrence at this locator; request execution-context/full-output for unmatched or omitted material")
          case Some(o) if s.kind == "cause-chain" =>
            if (o.causes.isEmpty && o.stack.isEmpty) _missing(s, "No recognized cause/stack in this occurrence; broader context remains selectable")
            else Item(s, "partial", "recognized-cause-and-stack-lines", source.sourceLoss,
              locator + s":${o.firstLine}-${o.lastLine}", Some((o.causes ++ o.stack).mkString("\n")),
              "Rendered lines only; extraction does not prove a complete causal chain")
          case Some(o) if s.kind == "suite-context" =>
            // A preceding header is context, not attribution under parallel SBT.
            // Execution-wide output is the honest expansion when no structured
            // attributable suite interval was retained.
            Item(s, "partial", "execution-context-suite-attribution-unknown", source.sourceLoss,
              locator + s":1-${lines.size}", Some(raw),
              "No attributable structured suite interval retained; supplied execution-wide context, including any setup and shared output")
          case Some(o) => Item(s, if (source.sourceLoss == "none-observed") "available" else "partial",
            "original-occurrence-context", source.sourceLoss, locator + s":${o.contextFirstLine}-${o.contextLastLine}",
            Some(lines.slice(o.contextFirstLine - 1, o.contextLastLine).mkString("\n")),
            "Original available context without the initial diagnostic display omission; grouping is not causal")
        }
      }
      Consequence.success(result.getOrElse(_missing(s, "Check/stream is absent from the retained failure")))
    }
  }

  def source(input: V.GoalPhaseDiagnosticInformationInput, work: GoalPhaseNativeWork.Request,
    repository: String, phase: String): CommonEvidence.Source = CommonEvidence.Source(
    ContextReference(input.issuedWorkContext.continuationId, input.issuedWorkContext.expectedRevision),
    repository, phase, work.stepIdentity, work.instanceIdentity, _output + input.requestIdentity, "failed")

  private def _batch_c(text: String): Consequence[Batch] = {
    val decoded = for {
      json <- io.circe.parser.parse(text).left.map(_.message)
      batch <- json.as[Batch].left.map(_.message)
      _ <- Either.cond(batch.schemaVersion == "sm-workflow.diagnostic-information.v1" && batch.asJson == json,
        (), "Diagnostic batch schema or shape differs")
    } yield batch
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  def failedCandidate(work: GoalPhaseNativeWork.Request): Option[GoalPhaseOrderedProgression.Snapshot] =
    work.checkFailure.map(_.validation.candidate).orElse(work.candidate)

  /** The storage boundary is shared by production and focused tests. Loaded
    * batches never cause another source read; unknown archive intents retain
    * Common Evidence's existing no-replay behavior.
    */
  def deliverC(store: CommonEvidence.Storage, policy: CommonEvidence.Policy, selectedsource: CommonEvidence.Source,
    work: GoalPhaseNativeWork.Request, input: V.GoalPhaseDiagnosticInformationInput, files: SourceReader,
    clock: java.time.Clock,
    journal: (CommonEvidence.Allocation, CommonEvidence.Observation) => Consequence[V.JournalRecordingState]):
    Consequence[(Batch, V.EvidenceRecordingState)] =
    deliverC(store, policy, selectedsource, context(work), input, files, clock, journal)

  def deliverC(store: CommonEvidence.Storage, policy: CommonEvidence.Policy, selectedsource: CommonEvidence.Source,
    work: WorkContext, input: V.GoalPhaseDiagnosticInformationInput, files: SourceReader, clock: java.time.Clock,
    journal: (CommonEvidence.Allocation, CommonEvidence.Observation) => Consequence[V.JournalRecordingState]):
    Consequence[(Batch, V.EvidenceRecordingState)] = {
    val id = CommonEvidence.identity(selectedsource)
    val selections = input.selections.map(_selection)
    def recordedC(batch: Batch, state: V.EvidenceRecordingState,
      saved: Option[(CommonEvidence.Allocation, CommonEvidence.Observation)]) = saved match {
      case Some((a, o)) => journal(a, o).map(j => batch -> state.copy(journal = Some(j)))
      case None => Consequence.success(batch -> state)
    }
    CommonEvidence.loadedC(store, id).flatMap {
      case Some((allocation, observation)) => for {
        _ <- if (allocation.source == selectedsource && allocation.host == policy.hostIdentity) Consequence.unit
          else Consequence.stateConflict("Delivered batch belongs to another source/host")
        observed <- observation.filter(_.status == "stored").map(Consequence.success)
          .getOrElse(Consequence.stateConflict("Delivered batch has no confirmed storage outcome; no repeat source read"))
        text <- CommonEvidence.payloadC(store, allocation).flatMap(_.map(Consequence.success)
          .getOrElse(Consequence.stateConflict("Delivered batch payload unavailable; no repeat source read")))
        batch <- _batch_c(text)
        _ <- if (batch.work == input.issuedWorkContext.continuationId && batch.revision == input.issuedWorkContext.expectedRevision &&
            batch.requestIdentity == input.requestIdentity && batch.candidate == work.candidate && batch.items.map(_.selection) == selections)
          Consequence.unit else Consequence.stateConflict("Request identity already names a different delivered batch")
        state = V.EvidenceRecordingState(observed.status, "Retained delivered batch; no source refresh",
          Some(V.EvidenceReference(id, "1")), Some(CommonEvidence.observation(allocation, observed)), None)
        result <- recordedC(batch, state, Some(allocation -> observed))
      } yield result
      case None => for {
        items <- itemsC(work, selections, files)
        resolution = if (items.forall(_.status == "unavailable")) "no-new-information"
          else if (items.exists(_.status != "available")) "partial-information" else "information-available"
        batch = Batch("sm-workflow.diagnostic-information.v1", input.issuedWorkContext.continuationId,
          input.issuedWorkContext.expectedRevision, input.requestIdentity, work.candidate,
          clock.instant().toString, resolution, items)
        saved <- CommonEvidence.archiveManagedC(store, policy, selectedsource, batch.asJson.noSpaces, "identity", clock)
        result <- recordedC(batch, saved._1, saved._2)
      } yield result
    }
  }

  def resolveC(d: GoalPhaseNativeRun.Declaration, core: ActionCall.Core,
    work: GoalPhaseNativeWork.Request, input: V.GoalPhaseDiagnosticInformationInput): Consequence[V.GoalPhaseDiagnosticInformationResult] = {
    def result(status: String, message: String, batch: Option[Batch], recording: Option[V.EvidenceRecordingState],
      history: DevelopmentJournalEvidence.ProducerPage) = {
      val known = recording.flatMap(_.evidence).exists(input.knownEvidence.contains)
      val items = batch.toVector.flatMap(_.items).map { item =>
        val json = item.asJson.deepMerge(Json.obj("observedAt" -> Json.fromString(batch.get.observedAt),
          "candidateApplicability" -> Json.fromString(if (item.selection.kind == "source-file") "unverified" else "retained-check"),
          "delivery" -> Json.fromString(if (known) "reference-already-known" else "payload")))
        GoalPhaseNativeMetadata.record(if (known) json.mapObject(_.remove("text")) else json)
      }
      val summaries = history.entries.map(e => GoalPhaseNativeMetadata.record(Json.obj(
        "evidence" -> Json.obj("identity" -> Json.fromString(e.metadata.evidence.identity), "revision" -> Json.fromString("1")),
        "requestIdentity" -> Json.fromString(e.metadata.outputIdentity.stripPrefix(_output)),
        "observedAt" -> Json.fromString(e.metadata.allocatedAt), "storageStatus" -> Json.fromString(e.observation.status),
        "details" -> Json.fromString("EvidenceService.readEvidence returns the delivered selections, reasons, facts and unavailable items"))))
      V.GoalPhaseDiagnosticInformationResult(input.issuedWorkContext, input.requestIdentity, status, message,
        items, recording, summaries, history.status, history.hasMore, history.next)
    }
    val selectedsource = source(input, work, d.input.repositoryIdentity, d.input.phaseIdentity)
    val emptyhistory = DevelopmentJournalEvidence.ProducerPage("unavailable", Vector.empty, false, None)
    def historyC() = DevelopmentJournalEvidence.producerPageC(d.journalPolicy, core,
      d.input.repositoryIdentity, work.instanceIdentity, selectedsource.reference, input.historyAfter)
    if (input.selections.isEmpty) historyC().map(h => result("history", "Delivered context references; no work completion or repair charge", None, None, h))
    else d.evidencePolicy match {
      case None => historyC().map(h => result("unavailable", "Common Evidence is disabled; no source read or delivery recording attempted", None, None, h))
      case Some(policy) =>
        val store = CommonEvidence.storage(core, policy)
        val attempted = for {
          delivered <- deliverC(store, policy, selectedsource, work, input, new SourceReader {
            def readC(path: String) = for {
              workspace <- WorkspaceId.parseC("sm-workflow-repository")
              selected <- WorkspaceFilePath.parseC(path)
              observed <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), selected)
            } yield observed.text
          }, core.executionContext.clock, (a, o) => DevelopmentJournalEvidence.recordC(d.journalPolicy, core, a, o))
          history <- historyC()
        } yield result(if (delivered._2.status != "stored") "recording-incomplete"
          else if (delivered._1.resolution == "no-new-information") "no-new-information" else "delivered",
          "Information only; original work, validation outcomes and repair counters remain unchanged", Some(delivered._1), Some(delivered._2), history)
        Consequence.success(attempted.fold(_ => result("unavailable", "Managed delivery unavailable or request conflicts; no work completion, replay or success inferred",
          None, None, emptyhistory), v => v))
    }
  }

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] = Vector(new Operation(port))
  private final class Operation(port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      SmWorkflowComponent.GoalPhaseServiceService.ResolveGoalPhaseDiagnosticInformationOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") || request.operation != operation)
        Consequence.stateConflict("Diagnostic information requires its exact owning Operation")
      else NativeWorkflowOperationInput.valueC[V.GoalPhaseDiagnosticInformationInput](request, "input")
        .flatMap(input => validateC(input).map(_ => Action(request, input, port)))
  }
  private final case class Action(request: Request, input: V.GoalPhaseDiagnosticInformationInput,
    port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(input.issuedWorkContext.runId))
        result <- port.diagnosticInformationC(input, core.copy(executionContext = context))
      } yield NativeWorkflowOperationOutput.response(result)
    }
  }
}
