package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject}
import org.goldenport.Consequence
import NativeHostEvidence.Purpose
import SplitPhaseTypedStartHumanVerification.StartContext

/** Human consent to the actual Start target, not a stored command/definition
  * or unchanged source-body certificate. No later Apply/command grant follows.
  * @since   Oct.  8, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object NativeHostSplitStartScope {
  def bindingC(value: StartContext): Consequence[Json] = {
    if (value == null || value.componentInstance == null || value.start == null)
      return Consequence.stateConflict("Human Start has no actual owner/input")
    val r = value.start
    if (r.input == null ||
        r.source == null || r.source.reference == null || r.source.phase == null ||
        r.source.authority == null || r.proposal == null || r.proposal.exists(p => p == null || p.reference == null))
      return Consequence.stateConflict("Human Start has no actual invocation/source target")
    Consequence.success(Json.obj("componentInstance" -> Json.fromString(value.componentInstance.canonicalKey),
      "startAttestation" -> Json.obj(
        "participantIdentity" -> Json.fromString(r.participantIdentity),
        "input" -> SplitPhaseTypedStartJsonV1.inputJson(r.input),
        "source" -> Json.obj("reference" -> reference(r.source.reference.identity, r.source.reference.revision),
          "phase" -> Json.fromString(r.source.phase.value),
          "authority" -> reference(r.source.authority.identity, r.source.authority.revision)),
        "proposal" -> r.proposal.map(p => reference(p.reference.identity, p.reference.revision)).getOrElse(Json.Null))))
  }

  def select(purpose: Purpose, binding: Json): Either[String, Json] =
    if (purpose != Purpose.SplitStart) Right(binding)
    else for {
      root <- obj(binding)
      owner <- text(root, "componentInstance")
      target <- at(root, "startAttestation")
      fields <- strings(target, Vector("participantIdentity"))
      request <- at(target, "input")
      requestFields <- strings(request, Vector("sourcePhaseIdentity", "invocationMode"))
      proposalInput <- optionalText(request, "proposalReference")
      source <- at(target, "source")
      phase <- text(source, "phase")
      sourceRef <- at(source, "reference").flatMap(NativeHostHumanScope.referenceTarget)
      authority <- at(source, "authority").flatMap(NativeHostHumanScope.referenceTarget)
      proposal <- target("proposal").filterNot(_.isNull) match {
        case None => Right(Json.Null)
        case Some(value) => obj(value).flatMap(NativeHostHumanScope.referenceTarget)
      }
    } yield Json.obj("componentInstance" -> Json.fromString(owner), "startAttestation" -> Json.fromJsonObject(
      fields.add("input", Json.fromJsonObject(
        requestFields.add("proposalReference", proposalInput.map(Json.fromString).getOrElse(Json.Null))))
        .add("source", Json.obj("reference" -> sourceRef, "phase" -> Json.fromString(phase), "authority" -> authority))
        .add("proposal", proposal)))

  private def reference(identity: String, revision: String): Json =
    Json.obj("identity" -> Json.fromString(identity), "revision" -> Json.fromString(revision))
  private def obj(value: Json): Either[String, JsonObject] =
    Option(value).flatMap(_.asObject).toRight("Human Start scope must be an object")
  private def at(value: JsonObject, key: String): Either[String, JsonObject] =
    value(key).toRight("Human Start requires " + key).flatMap(obj)
  private def text(value: JsonObject, key: String): Either[String, String] =
    value(key).flatMap(_.asString).toRight("Human Start requires String " + key)
  private def optionalText(value: JsonObject, key: String): Either[String, Option[String]] =
    value(key).filterNot(_.isNull) match {
      case None => Right(None)
      case Some(_) => text(value, key).map(Some(_))
    }
  private def strings(value: JsonObject, keys: Vector[String]): Either[String, JsonObject] =
    keys.foldLeft[Either[String, JsonObject]](Right(JsonObject.empty))((prior, key) => for {
      fields <- prior
      value <- text(value, key)
    } yield fields.add(key, Json.fromString(value)))
}
