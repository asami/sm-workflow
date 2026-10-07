package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionSemantic as S}

/** Closed generated command data only. Decoding neither admits a current call
  * nor installs an action or selects a Workflow route.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseAdvanceCommandJsonV1 {
  val schema = "sm-workflow.split-phase-advance-command.v1"
  val maximumPayloadBytes = 16 * 1024
  private val _prefix = schema + ":"

  def encodeC(command: SplitPhaseAdvanceCommand): Consequence[String] = _guarded {
    for {
      _ <- _shape_c(command)
      raw = _prefix + _render(command).noSpaces
      _ <- _frame_c(raw)
    } yield raw
  }

  def decodeC(raw: String): Consequence[SplitPhaseAdvanceCommand] = _guarded {
    for {
      _ <- _frame_c(raw)
      json <- parser.parse(raw.substring(_prefix.length)).fold(
        _ => _invalid("JSON is malformed"), Consequence.success)
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .map(Consequence.success).getOrElse(_invalid("envelope fields differ"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit
        else _invalid("schema differs")
      command <- root("payload").get.as[SplitPhaseAdvanceCommand].fold(
        _ => _invalid("generated data is incomplete"), Consequence.success)
      _ <- _shape_c(command)
      _ <- if (_render(command) == json) Consequence.unit else _invalid("nested fields differ")
    } yield command
  }

  private def _render(command: SplitPhaseAdvanceCommand): Json =
    Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> command.asJson)

  private def _shape_c(command: SplitPhaseAdvanceCommand): Consequence[Unit] =
    if (command == null || command.context == null) _invalid("command or context is absent")
    else SplitPhasePlanningCheckpointPublication.indexKeyC(InstanceIdentity(command.instanceIdentity),
      S.ContextReference(command.context.identity, command.context.revision)).map(_ => ())

  private def _frame_c(raw: String): Consequence[Unit] =
    if (raw != null && UTF_8.newEncoder().canEncode(raw) &&
        raw.getBytes(UTF_8).length <= maximumPayloadBytes && raw.startsWith(_prefix)) Consequence.unit
    else _invalid("encoding or whole UTF-8 limit differs")

  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase advance command " + reason)
  private def _guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => _invalid("data cannot be encoded")
  }
}
