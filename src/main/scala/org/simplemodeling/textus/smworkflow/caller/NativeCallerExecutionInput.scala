package org.simplemodeling.textus.smworkflow.caller

import io.circe.Json
import java.net.URI
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{WorkflowExecutionHarness as H, WorkflowExecutionProfileParameters}
import org.goldenport.convert.ValueReader
import org.simplemodeling.textus.smworkflow.admission.GoalPhaseNativeExecution
import org.simplemodeling.textus.smworkflow.preparation.NativePreparationInput
import org.simplemodeling.textus.smworkflow.value as V

/** Passive conversions borrow only the executing call's bounded resource reader.
  * Supplied observations and configuration authenticate no executor or authority.
  * @since   Oct. 16, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeCallerExecutionInput {
  def prepareC(input: V.WorkflowCallerExecutionPrepareInput,
    readText: String => Consequence[String]): Consequence[V.WorkflowCallerExecutionState] =
    if (input == null || input.issuedWorkContext == null || readText == null)
      _invalid("requires its actual typed input and current reader")
    else for {
      _ <- _uris_c(Vector(input.mapping, input.origin))
      mappingjson <- _read_json_c(input.mapping, readText)
      mapping <- WorkflowExecutionProfileParameters.decodeC(mappingjson)
      originjson <- _read_json_c(input.origin, readText)
      origin <- GoalPhaseNativeExecution.decodeC(originjson)
      prepared <- NativeWorkflowCallerExecution.prepareC(input.issuedWorkContext, mapping, origin.current)
      state <- _state_c(prepared, None)
    } yield state

  def recordC(input: V.WorkflowCallerExecutionRecordInput,
    readText: String => Consequence[String]): Consequence[V.WorkflowCallerExecutionState] =
    if (input == null || input.issuedWorkContext == null || input.reference == null || readText == null)
      _invalid("requires its actual typed input and current reader")
    else for {
      _ <- _uris_c(Vector(input.session, input.observation) ++ input.reference.toVector)
      raw <- readText(input.session)
      session <- H.decodeC(raw)
      prepared <- NativeWorkflowCallerExecution.restoreC(input.issuedWorkContext, session)
      observation <- _read_json_c(input.observation, readText)
      attempt <- H.decodeAttemptC(observation)
      recorded <- prepared.recordC(attempt.selection, attempt.outcome)
      evidence <- if (attempt.outcome.status == H.Status.Completed) input.reference match {
        case Some(reference) => for {
          record <- NativePreparationInput.readC(reference, readText)
          artifact <- summon[ValueReader[V.WorkflowArtifactReference]].readC(record)
          completed <- recorded.completionEvidenceC(Vector(artifact))
        } yield Some(completed)
        case None => _invalid("COMPLETED requires one supplied artifact reference URI")
      } else if (input.reference.nonEmpty) _invalid("noncompleted observations forbid a completion reference")
        else Consequence.success(Option.empty[V.WorkflowExecutionEvidence])
      state <- _state_c(recorded, evidence)
    } yield state

  private def _state_c(prepared: NativeWorkflowCallerExecution.Prepared,
    evidence: Option[V.WorkflowExecutionEvidence]): Consequence[V.WorkflowCallerExecutionState] = for {
    session <- H.encodeC(prepared.session)
    selection <- prepared.session.nextSelection match {
      case Some(value) => H.encodeSelectionC(value).map(Some(_))
      case None => Consequence.success(Option.empty[String])
    }
  } yield V.WorkflowCallerExecutionState(session, selection, evidence)

  private def _read_json_c(reference: String, reader: String => Consequence[String]): Consequence[Json] =
    reader(reference).flatMap(text =>
      io.circe.parser.parse(text).fold(error => _invalid(error.message), Consequence.success))

  private def _uris_c(references: Vector[String]): Consequence[Unit] =
    references.foldLeft(Consequence.unit)((prior, reference) => prior.flatMap { _ =>
      if (!NativePreparationInput.exact(reference)) _invalid("requires exact absolute local file: URI metadata")
      else try {
        val uri = URI.create(reference)
        if (uri.isAbsolute && uri.getScheme == "file" && !uri.isOpaque &&
            Option(uri.getRawAuthority).forall(_.isEmpty) && Option(uri.getPath).exists(_.startsWith("/")) &&
            uri.getRawQuery == null && uri.getRawFragment == null) Consequence.unit
        else _invalid("requires absolute local file: URI metadata")
      } catch { case NonFatal(_) => _invalid("requires valid absolute local file: URI metadata") }
    })

  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Caller execution input " + reason)
}
