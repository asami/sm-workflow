package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import SplitPhaseExplicitStartPreparation.{Record, Reference}

/** Closed data projection only. Neither Prepared nor human/issue authority
  * can be decoded from this record.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseExplicitStartRecordJsonV1 {
  val schema = "sm-workflow.split-phase-explicit-start-record.v1"
  val storageField = "splitPhaseExplicitStartPayload"
  private val prefix = schema + ":"
  private final case class Payload(reference: Reference, instanceIdentity: String,
    workflowIdentity: String, workflowRevision: String, configuration: Configuration,
    command: Json, selection: Json, assessment: SplitPhaseEntryAssessment.Assessment)

  def encodeC(value: Record): Consequence[String] = jsonC(value).flatMap { json =>
    val raw = prefix + json.noSpaces
    if (raw.length <= 1024 * 1024) Consequence.success(raw)
    else Consequence.stateConflict("SplitPhase explicit Start record exceeds its storage limit")
  }

  def decodeC(expected: Reference, raw: String): Consequence[Record] =
    if (!validReference(expected) || raw == null || raw.length > 1024 * 1024 || !raw.startsWith(prefix))
      Consequence.stateConflict("SplitPhase explicit Start storage reference/encoding is unsupported")
    else for {
      json <- parseC(raw.substring(prefix.length))
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .map(Consequence.success).getOrElse(Consequence.stateConflict("SplitPhase explicit Start envelope differs"))
      _ <- if (root("schemaVersion").flatMap(_.asString).contains(schema)) Consequence.unit
        else Consequence.stateConflict("SplitPhase explicit Start storage schema differs")
      payload <- root("payload").get.as[Payload].fold(
        _ => Consequence.stateConflict("SplitPhase explicit Start payload is invalid"), Consequence.success)
      command <- SplitPhaseStartServiceOperation.decodeCommandC(payload.command.noSpaces)
      selection <- ProfileSelectionJsonV1.decodeC(payload.selection.noSpaces)
      value = Record(payload.reference, InstanceIdentity(payload.instanceIdentity),
        WorkflowDefinitionIdentity(payload.workflowIdentity), WorkflowDefinitionRevision(payload.workflowRevision),
        payload.configuration, command, selection, payload.assessment)
      canonical <- jsonC(value)
      _ <- if (canonical == json && value.reference == expected) Consequence.unit
        else Consequence.stateConflict("SplitPhase explicit Start nested facts or exact reference/revision differ")
    } yield value

  private def jsonC(value: Record): Consequence[Json] =
    if (value == null || !validReference(value.reference) || value.instanceIdentity == null ||
        !name(value.instanceIdentity.value) || value.workflowIdentity == null || !name(value.workflowIdentity.value) ||
        value.workflowRevision == null || !name(value.workflowRevision.value) || value.configuration == null ||
        value.command == null || !name(value.command.selectionIdentity) || !name(value.command.participantIdentity) ||
        !name(value.command.idempotencyKey) || value.command.input == null ||
        !name(value.command.input.sourcePhaseIdentity) || !name(value.command.input.invocationMode) ||
        !name(value.command.input.sourceAuthorityReference) || value.command.input.sourceSnapshotReference == null ||
        value.command.input.originGoalPhaseTerminalResultReference == null || value.command.input.proposalReference == null ||
        value.assessment == null || value.assessment.source == null || value.assessment.proposalCandidate == null)
      Consequence.stateConflict("SplitPhase explicit Start data record is incomplete")
    else for {
      _ <- value.configuration.validateC
      commandRaw = SplitPhaseStartServiceOperation.encodeCommand(value.command)
      _ <- SplitPhaseStartServiceOperation.decodeCommandC(commandRaw)
      command <- parseC(commandRaw)
      selectionRaw <- ProfileSelectionJsonV1.encodeC(value.selection)
      selection <- parseC(selectionRaw)
      _ <- SplitPhaseEntryRecordJsonV1.decodeSourceStorageC(SplitPhaseEntryRecordJsonV1.sourcePrefix +
        value.assessment.source.asJson.mapObject(_.add("schemaVersion",
          Json.fromString(SplitPhaseEntryRecordJsonV1.sourceSchema))).noSpaces)
      _ <- value.assessment.proposalCandidate match {
        case None => Consequence.unit
        case Some(proposal) if proposal != null =>
          SplitPhaseEntryRecordJsonV1.decodeProposalStorageC(SplitPhaseEntryRecordJsonV1.proposalPrefix +
            proposal.asJson.mapObject(_.add("schemaVersion", Json.fromString(SplitPhaseEntryRecordJsonV1.proposalSchema)))
              .noSpaces).map(_ => ())
        case _ => Consequence.stateConflict("SplitPhase explicit Start proposal is invalid")
      }
      payload = Payload(value.reference, value.instanceIdentity.value, value.workflowIdentity.value,
        value.workflowRevision.value, value.configuration, command, selection, value.assessment)
    } yield Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> payload.asJson)

  private def parseC(raw: String): Consequence[Json] = parser.parse(raw).fold(
    _ => Consequence.stateConflict("SplitPhase explicit Start JSON is invalid"), Consequence.success)
  private def validReference(value: Reference): Boolean =
    value != null && name(value.identity) && name(value.revision)
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
