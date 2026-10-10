package org.simplemodeling.textus.smworkflow.admission

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.processexecution.{ProcessExecutionResult, ProcessExecutionTermination}
import org.simplemodeling.textus.smworkflow.runtime.SelectedPhaseDocumentProgram as Program
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseManagedPlanningFrontier as F,
  WorkflowManagedProcessExecution as E}

/** Connects an actual native selected APPLY source to fixed runtime effects.
  * This supplies no intent publication or native completion; the owning advance
  * must publish its durable ApplyingDocuments preparation before applyC.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseSelectedDocumentEffect {
  val applyingProgression = "entry.ApplyingDocuments"
  enum Status { case Ready, Partial, Applied, Conflict }
  final case class Observation(status: Status, payload: Json)
  final class Prepared private[SplitPhaseSelectedDocumentEffect] (
    val source: F.Observed, val input: String, val repository: String,
    val instance: String, val context: Json, val projection: SplitPhaseSelectedDocuments.Projection)

  def prepareC(source: F.Observed, context: String, revision: String): Consequence[Prepared] =
    if (source == null) Consequence.stateConflict("Selected document effect requires its actual native source")
    else for {
      _ <- if (source.invocationMode == "APPLY") Consequence.unit
        else Consequence.stateConflict("Selected PREVIEW supplies no document application authority")
      projection <- SplitPhaseSelectedDocuments.projectC(source.snapshot)
      repositories = projection.documents.map(_.contract.hcursor.downField("validation")
        .downField("repository").get[String]("value").toOption).distinct
      repository <- repositories match {
        case Vector(Some(value)) => Consequence.success(value)
        case _ => Consequence.serviceUnavailable("Selected document effect requires one configured repository")
      }
      input <- Program.requestC(repository, source.current.identity.value, context, revision, source.snapshot)
    } yield new Prepared(source, input, repository, source.current.identity.value,
      Json.obj("identity" -> Json.fromString(context), "revision" -> Json.fromString(revision)), projection)

  def observeC(prepared: Prepared, core: ActionCall.Core): Consequence[Observation] =
    executeC(prepared, core, E.Purpose.ObserveSelectedDocuments)
  def applyC(prepared: Prepared, core: ActionCall.Core): Consequence[Observation] =
    if (prepared == null || !prepared.source.current.currentProgression.exists(_.value == applyingProgression))
      Consequence.stateConflict("Selected document application requires its actual native ApplyingDocuments intent")
    else executeC(prepared, core, E.Purpose.ApplySelectedDocuments)

  private def executeC(prepared: Prepared, core: ActionCall.Core, purpose: E.Purpose): Consequence[Observation] =
    if (prepared == null) Consequence.stateConflict("Selected document effect preparation is absent")
    else E.executeC(core, purpose, prepared.input).flatMap(result => decodeC(prepared, result))

  private[admission] def decodeC(prepared: Prepared, result: ProcessExecutionResult): Consequence[Observation] = {
    if (prepared == null || result == null || result.termination != ProcessExecutionTermination.Exited(0) ||
        result.stdout.truncated || result.stdout.byteCount != result.stdout.content.size.toLong)
      DataStoreConditionalTransitionFailure.transactionIndeterminate("Selected document effect is not established; observe on a later call")
    else try {
      val text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(result.stdout.content.toArray)).toString
      io.circe.parser.parse(text).fold(_ => invalid("invalid result JSON"), payload => validatePayloadC(prepared, payload))
    } catch { case scala.util.control.NonFatal(_) => invalid("result encoding is invalid") }
  }

  private[admission] def validatePayloadC(prepared: Prepared, payload: Json): Consequence[Observation] =
    if (prepared == null || payload == null) invalid("effect preparation/result is absent")
    else {
        val c = payload.hcursor
        val expected = prepared.projection.json.hcursor
        val status = c.get[String]("status").toOption.flatMap {
          case "ready" => Some(Status.Ready)
          case "partial" => Some(Status.Partial)
          case "applied" => Some(Status.Applied)
          case "conflict" => Some(Status.Conflict)
          case _ => None
        }
        val paths = prepared.projection.documents.map(_.relativePath)
        val conflicts = c.get[Vector[String]]("conflictingPaths").toOption
        if (c.get[String]("schemaVersion").contains(Program.resultSchema) &&
            c.get[String]("repositoryIdentity").contains(prepared.repository) &&
            c.get[String]("instanceIdentity").contains(prepared.instance) &&
            c.downField("context").focus.contains(prepared.context) &&
            c.downField("source").focus == expected.downField("source").focus &&
            c.downField("proposal").focus == expected.downField("proposal").focus &&
            c.get[Vector[String]]("paths").contains(paths) && status.nonEmpty &&
            conflicts.exists(v => v.distinct == v && v.forall(paths.contains) &&
              v.nonEmpty == (status.get == Status.Conflict))) Consequence.success(Observation(status.get, payload))
        else invalid("result crosses selected references or application disposition")
    }
  private def invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("Selected document effect " + reason)
}
