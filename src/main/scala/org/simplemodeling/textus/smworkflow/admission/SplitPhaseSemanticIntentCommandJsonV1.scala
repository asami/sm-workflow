package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.value.SplitPhaseProposalAssessmentCommand

/**
 * Original command data, not a new invocation, authority or issued witness.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseSemanticIntentCommandJsonV1 {
  val schema = "sm-workflow.split-phase-semantic-intent-command.v1"
  val storageField = "splitPhaseSemanticIntentCommandPayload"
  def encodeC(reference: String, command: SplitPhaseProposalAssessmentCommand): Consequence[String] =
    if (reference == null || reference.isEmpty || reference.trim != reference || command == null || command.startCommand == null)
      Consequence.stateConflict("SplitPhase original assessment command is absent")
    else for {
      admitted <- SplitPhaseProposalAssessmentServiceOperation.decodeCommandC(
        SplitPhaseProposalAssessmentServiceOperation.encodeCommand(command))
      commandJson <- parser.parse(SplitPhaseProposalAssessmentServiceOperation.encodeCommand(admitted))
        .fold(_ => Consequence.stateConflict("SplitPhase original command JSON is invalid"), Consequence.success)
      raw = schema + ":" + Json.obj("schemaVersion" -> Json.fromString(schema),
        "reference" -> Json.fromString(reference), "command" -> commandJson).noSpaces
      _ <- decodeC(reference, raw)
    } yield raw

  def decodeC(reference: String, raw: String): Consequence[SplitPhaseProposalAssessmentCommand] = {
    val prefix = schema + ":"
    val parsed = for {
      _ <- Either.cond(reference != null && reference.nonEmpty && reference.trim == reference &&
        raw != null && raw.length <= 256 * 1024 && raw.startsWith(prefix), (), "SplitPhase original command record is unsupported")
      json <- parser.parse(raw.substring(prefix.length)).left.map(_ => "SplitPhase original command JSON is invalid")
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "reference", "command"))
        .toRight("SplitPhase original command envelope differs")
      _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(schema) &&
        root("reference").flatMap(_.asString).contains(reference), (), "SplitPhase original command provenance differs")
      command <- root("command").filter(_.isObject).toRight("SplitPhase original command is absent")
    } yield command.noSpaces
    parsed.fold(Consequence.stateConflict(_), SplitPhaseProposalAssessmentServiceOperation.decodeCommandC)
  }
}
