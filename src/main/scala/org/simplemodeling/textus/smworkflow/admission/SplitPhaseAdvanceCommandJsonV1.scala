package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import io.circe.generic.auto.*
import io.circe.syntax.*
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand

/** Generated command data only; unused diagnostics are not route selectors.
  * Decoding neither admits a current call
  * nor installs an action or selects a Workflow route.
  * @since   Oct.  2, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object SplitPhaseAdvanceCommandJsonV1 {
  val schema = "sm-workflow.split-phase-advance-command.v1"
  private val _prefix = schema + ":"

  def encodeC(command: SplitPhaseAdvanceCommand): Consequence[String] = _guarded {
    for {
      _ <- _shape_c(command)
      raw = _prefix + _render(command).noSpaces
    } yield raw
  }

  def decodeC(raw: String): Consequence[SplitPhaseAdvanceCommand] = _guarded {
    for {
      _ <- _frame_c(raw)
      json <- parser.parse(raw.substring(_prefix.length)).fold(
        _ => _invalid("JSON is malformed"), Consequence.success)
      root <- json.asObject.map(Consequence.success).getOrElse(_invalid("envelope is not an object"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit
        else _invalid("schema differs")
      command <- root("payload").getOrElse(Json.Null).as[SplitPhaseAdvanceCommand].fold(
        _ => _invalid("generated data is incomplete"), Consequence.success)
    } yield command
  }

  private def _render(command: SplitPhaseAdvanceCommand): Json =
    Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> command.asJson)

  private def _shape_c(command: SplitPhaseAdvanceCommand): Consequence[Unit] =
    if (command == null || command.context == null || command.instanceIdentity == null ||
        command.context.identity == null || command.context.revision == null)
      _invalid("required command data is absent")
    else Consequence.unit

  private def _frame_c(raw: String): Consequence[Unit] =
    if (raw != null && raw.startsWith(_prefix)) Consequence.unit
    else _invalid("frame prefix differs")

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase advance command " + reason)
  private def _guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => _invalid("data cannot be encoded")
  }
}
