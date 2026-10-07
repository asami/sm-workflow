package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import GoalPhaseNativeRun.SelectedPlan

/**
 * Selected host input, not a grant, command or native Start receipt.
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseSelectedPlanJsonV1 {
  val schemaVersion = "sm-workflow.goal-phase-selected-plan.v1"
  val storageField = "selectedPlanPayload"
  private val storagePrefix = schemaVersion + ":"

  private def validateC(value: SelectedPlan): Consequence[Unit] =
    if (value == null || !Vector(value.selectionIdentity, value.participantIdentity).forall(text) ||
        value.input == null || !Vector(value.input.phaseIdentity, value.input.repositoryIdentity).forall(text))
      Consequence.stateConflict("GoalPhase selected Plan identity or input is incomplete")
    else GoalPhaseOrderedProgression.validatePlanC(value.plan)

  def encodeC(value: SelectedPlan): Consequence[String] = validateC(value).map(_ => envelope(value).noSpaces)
  def encodeStorageC(value: SelectedPlan): Consequence[String] = encodeC(value).map(storagePrefix + _)
  def decodeStorageC(raw: String): Consequence[SelectedPlan] =
    if (raw != null && raw.startsWith(storagePrefix)) decodeC(raw.substring(storagePrefix.length))
    else Consequence.stateConflict("GoalPhase selected Plan storage encoding is unsupported")

  def decodeC(raw: String): Consequence[SelectedPlan] = {
    val decoded = for {
      json <- io.circe.parser.parse(Option(raw).getOrElse("")).left.map(_ => "Selected Plan JSON is invalid")
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .toRight("Selected Plan envelope fields differ")
      _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(schemaVersion), (), "Selected Plan schema differs")
      value <- root("payload").get.as[SelectedPlan].left.map(_ => "Selected Plan payload is invalid")
      _ <- Either.cond(envelope(value) == json, (), "Selected Plan has undeclared or coerced fields")
    } yield value
    decoded.fold(Consequence.stateConflict(_), value => validateC(value).map(_ => value))
  }

  private def envelope(value: SelectedPlan): Json = Json.obj(
    "schemaVersion" -> Json.fromString(schemaVersion), "payload" -> value.asJson)
  private def text(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
