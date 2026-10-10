package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject}
import org.goldenport.Consequence
import NativeHostEvidence.Purpose
import SplitPhaseTypedStartHumanVerification.StartContext

/** Human consent to the actual Start invocation, not an unchanged storage,
  * selection, source/proposal-body or generated-description certificate.
  * The original Start owns its proposal meaning; this evidence does not reload
  * or duplicate that proposal and grants no later Apply/command authority.
  * @since   Oct.  8, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object NativeHostSplitStartScope {
  def bindingC(value: StartContext): Consequence[Json] = {
    if (value == null || value.componentInstance == null || value.start == null)
      return Consequence.stateConflict("Human Start has no actual owner/input")
    val r = value.start
    if (r.reference == null || r.instanceIdentity == null || r.workflowIdentity == null || r.workflowRevision == null ||
        r.command == null || r.command.input == null || r.source == null || r.source.reference == null ||
        r.source.phase == null || r.source.authority == null || r.proposal == null || r.proposal.exists(p => p == null || p.reference == null))
      return Consequence.stateConflict("Human Start has no actual invocation/source target")
    val c = r.command
    val i = c.input
    if (Vector(r.reference.identity, r.reference.revision, r.instanceIdentity.value, r.workflowIdentity.value, r.workflowRevision.value,
        c.selectionIdentity, c.participantIdentity, c.idempotencyKey, i.sourcePhaseIdentity, i.invocationMode, i.sourceAuthorityReference,
        r.source.reference.identity, r.source.reference.revision, r.source.phase.value, r.source.authority.identity, r.source.authority.revision)
        .exists(_ == null) || Vector(i.sourceSnapshotReference, i.originGoalPhaseTerminalResultReference, i.proposalReference)
        .exists(v => v == null || v.exists(_ == null)) || r.proposal.exists(p => p.reference.identity == null || p.reference.revision == null))
      return Consequence.stateConflict("Human Start actual scope fields are malformed")
    val raw = Json.obj("componentInstance" -> Json.fromString(value.componentInstance.canonicalKey), "startAttestation" -> Json.obj(
      "reference" -> reference(r.reference.identity, r.reference.revision),
      "instanceIdentity" -> Json.fromString(r.instanceIdentity.value),
      "workflowIdentity" -> Json.fromString(r.workflowIdentity.value), "workflowRevision" -> Json.fromString(r.workflowRevision.value),
      "command" -> Json.obj("selectionIdentity" -> Json.fromString(c.selectionIdentity), "participantIdentity" -> Json.fromString(c.participantIdentity),
        "idempotencyKey" -> Json.fromString(c.idempotencyKey), "input" -> Json.obj(
          "sourcePhaseIdentity" -> Json.fromString(i.sourcePhaseIdentity), "invocationMode" -> Json.fromString(i.invocationMode),
          "sourceAuthorityReference" -> Json.fromString(i.sourceAuthorityReference), "sourceSnapshotReference" -> optional(i.sourceSnapshotReference),
          "originGoalPhaseTerminalResultReference" -> optional(i.originGoalPhaseTerminalResultReference), "proposalReference" -> optional(i.proposalReference))),
      "source" -> Json.obj("reference" -> reference(r.source.reference.identity, r.source.reference.revision),
        "phase" -> Json.fromString(r.source.phase.value), "authority" -> reference(r.source.authority.identity, r.source.authority.revision)),
      "proposal" -> r.proposal.map(p => reference(p.reference.identity, p.reference.revision)).getOrElse(Json.Null)))
    select(Purpose.SplitStart, raw, legacy = false).fold(Consequence.stateConflict(_), Consequence.success)
  }

  def select(purpose: Purpose, binding: Json, legacy: Boolean): Either[String, Json] =
    if (purpose != Purpose.SplitStart) Right(binding)
    else for {
      root <- obj(binding)
      owner <- text(root, "componentInstance")
      current = root("startAttestation").nonEmpty
      _ <- Either.cond(current || legacy, (), "Human Start cannot retain a complete Start copy")
      _ <- shape(root, if (current) Set("componentInstance", "startAttestation") else Set("componentInstance", "start"))
      input <- if (current) at(root, "startAttestation") else for {
        envelope <- at(root, "start")
        _ <- Either.cond(envelope("schemaVersion").flatMap(_.asString).contains(SplitPhaseTypedStartJsonV1.schema), (), "Legacy Human Start format is unsupported")
        payload <- at(envelope, "payload")
      } yield payload
      _ <- if (current) shape(input, Set("reference", "instanceIdentity", "workflowIdentity", "workflowRevision", "command", "source", "proposal")) else Right(())
      ref <- at(input, "reference").flatMap(referenceTarget)
      instance <- scalar(input, "instanceIdentity", current)
      workflow <- scalar(input, "workflowIdentity", current)
      revision <- scalar(input, "workflowRevision", current)
      _ <- Either.cond(workflow == "SplitPhaseWorkflow", (), "Human Start selects another Workflow")
      command <- at(input, "command")
      _ <- shape(command, if (current) Set("selectionIdentity", "participantIdentity", "idempotencyKey", "input") else
        Set("schemaVersion", "selectionIdentity", "participantIdentity", "idempotencyKey", "input"))
      _ <- Either.cond(current || command("schemaVersion").flatMap(_.asString).contains(SplitPhaseStartServiceOperation.wireSchema), (), "Human Start command format is unsupported")
      selection <- text(command, "selectionIdentity")
      participant <- text(command, "participantIdentity")
      key <- text(command, "idempotencyKey")
      request <- at(command, "input")
      _ <- shape(request, Set("sourcePhaseIdentity", "invocationMode", "sourceAuthorityReference", "sourceSnapshotReference", "originGoalPhaseTerminalResultReference", "proposalReference"))
      phase <- text(request, "sourcePhaseIdentity")
      mode <- text(request, "invocationMode")
      _ <- Either.cond(Set("PREVIEW", "APPLY")(mode), (), "Human Start has another invocation mode")
      authority <- text(request, "sourceAuthorityReference")
      snapshot <- optionalText(request, "sourceSnapshotReference")
      origin <- optionalText(request, "originGoalPhaseTerminalResultReference")
      proposalId <- optionalText(request, "proposalReference")
      source <- at(input, "source")
      _ <- if (current) shape(source, Set("reference", "phase", "authority")) else Right(())
      sourceRef <- at(source, "reference").flatMap(referenceTarget)
      sourcePhase <- scalar(source, "phase", current)
      sourceAuthority <- at(source, "authority").flatMap(referenceTarget)
      proposal <- input("proposal") match {
        case Some(v) if v.isNull => Right(Json.Null)
        case Some(v) => obj(v).flatMap(p => if (current) referenceTarget(p) else at(p, "reference").flatMap(referenceTarget))
        case None => Left("Human Start proposal selection is absent")
      }
      _ <- Either.cond(phase == sourcePhase && sourceAuthority.hcursor.get[String]("identity").contains(authority) &&
        snapshot.forall(s => sourceRef.hcursor.get[String]("identity").contains(s)) &&
        proposalId == proposal.hcursor.get[String]("identity").toOption, (), "Human Start has an incompatible source/authority/proposal target")
    } yield Json.obj("componentInstance" -> Json.fromString(owner), "startAttestation" -> Json.obj(
      "reference" -> ref, "instanceIdentity" -> Json.fromString(instance), "workflowIdentity" -> Json.fromString(workflow),
      "workflowRevision" -> Json.fromString(revision), "command" -> Json.obj(
        "selectionIdentity" -> Json.fromString(selection), "participantIdentity" -> Json.fromString(participant), "idempotencyKey" -> Json.fromString(key),
        "input" -> Json.obj("sourcePhaseIdentity" -> Json.fromString(phase), "invocationMode" -> Json.fromString(mode),
          "sourceAuthorityReference" -> Json.fromString(authority), "sourceSnapshotReference" -> snapshot.map(Json.fromString).getOrElse(Json.Null),
          "originGoalPhaseTerminalResultReference" -> origin.map(Json.fromString).getOrElse(Json.Null), "proposalReference" -> proposalId.map(Json.fromString).getOrElse(Json.Null))),
      "source" -> Json.obj("reference" -> sourceRef, "phase" -> Json.fromString(sourcePhase), "authority" -> sourceAuthority), "proposal" -> proposal))

  private def reference(identity: String, revision: String): Json = Json.obj("identity" -> Json.fromString(identity), "revision" -> Json.fromString(revision))
  private def optional(value: Option[String]): Json = value.map(Json.fromString).getOrElse(Json.Null)
  private def obj(value: Json): Either[String, JsonObject] = Option(value).flatMap(_.asObject).toRight("Human Start scope must be an object")
  private def at(value: JsonObject, key: String): Either[String, JsonObject] = value(key).toRight("Human Start scope lacks " + key).flatMap(obj)
  private def shape(value: JsonObject, fields: Set[String]): Either[String, Unit] =
    Either.cond(value.keys.toSet == fields, (), "Human Start scope has undeclared or missing fields")
  private def text(value: JsonObject, key: String): Either[String, String] = value(key).flatMap(_.asString)
    .filter(v => v.nonEmpty && v.trim == v && !v.exists(_.isControl)).toRight("Human Start scope lacks an actual " + key)
  private def scalar(value: JsonObject, key: String, current: Boolean): Either[String, String] =
    if (current) text(value, key) else at(value, key).flatMap(text(_, "value"))
  private def optionalText(value: JsonObject, key: String): Either[String, Option[String]] = value(key) match {
    case Some(v) if v.isNull => Right(None)
    case Some(_) => text(value, key).map(Some(_))
    case None => Left("Human Start scope lacks " + key)
  }
  private def referenceTarget(value: JsonObject): Either[String, Json] = for {
    _ <- shape(value, Set("identity", "revision"))
    identity <- text(value, "identity")
    revision <- text(value, "revision")
  } yield reference(identity, revision)
}
