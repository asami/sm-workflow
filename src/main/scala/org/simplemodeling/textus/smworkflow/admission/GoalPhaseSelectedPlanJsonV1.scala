package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import scala.util.control.NonFatal
import org.goldenport.Consequence
import GoalPhaseNativeRun.SelectedPlan

/**
 * Passive selected data. Start owns selection/scope; progression owns Plan meaning.
 * @since   Oct.  6, 2026
 * @version Oct. 17, 2026
 */
private[smworkflow] object GoalPhaseSelectedPlanJsonV1 {
  val schemaVersion = "sm-workflow.goal-phase-selected-plan.v1"
  val storageField = "selectedPlanPayload"
  private val _storage_prefix = schemaVersion + ":"

  def encodeC(value: SelectedPlan): Consequence[String] =
    if (value == null) Consequence.stateConflict("Selected Plan data is absent")
    else try Consequence.success(_envelope(value).noSpaces)
    catch { case NonFatal(_) => Consequence.stateConflict("Selected Plan data cannot be encoded") }
  def encodeStorageC(value: SelectedPlan): Consequence[String] = encodeC(value).map(_storage_prefix + _)
  def decodeStorageC(raw: String): Consequence[SelectedPlan] =
    if (raw != null && raw.startsWith(_storage_prefix)) decodeC(raw.substring(_storage_prefix.length))
    else Consequence.stateConflict("GoalPhase selected Plan storage encoding is unsupported")

  def decodeC(raw: String): Consequence[SelectedPlan] = {
    val decoded = for {
      json <- io.circe.parser.parse(Option(raw).getOrElse("")).left.map(_ => "Selected Plan JSON is invalid")
      schema <- json.hcursor.get[String]("schemaVersion").left.map(_ => "Selected Plan schema is missing")
      _ <- Either.cond(schema == schemaVersion, (), "Selected Plan schema differs")
      value <- json.hcursor.get[SelectedPlan]("payload").left.map(_ => "Selected Plan payload is invalid")
    } yield value
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  private def _envelope(value: SelectedPlan): Json = Json.obj(
    "schemaVersion" -> Json.fromString(schemaVersion), "payload" ->
      (if (value.documents.isEmpty) value.asJson.mapObject(_.remove("documents")) else value.asJson))
}
