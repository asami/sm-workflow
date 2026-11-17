package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference

/** Standalone recovery binds a single Human decision to the actual UNKNOWN and
  * clean source. It records no claim about the earlier writer's lifecycle.
  * @since   Nov. 17, 2026
  * @version Nov. 17, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object LocalPublicationRecovery {
  val purpose = "standalone-local-publication-recovery"
  val choice = "AuthorizeSuccessorWithUnknownEffects"
  final case class Target(componentInstance: String, unknown: LocalPublication.Result,
    source: LocalPublication.Source, successorIdentity: String)
  final case class Answer(target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference)
  final case class Direction(answer: Answer)
  private def exact(s: String): Boolean = s != null && s.nonEmpty && s == s.trim && !s.exists(_.isControl)
  private def validTarget(t: Target): Boolean = t != null && exact(t.componentInstance) &&
    exact(t.successorIdentity) && t.unknown != null && t.unknown.status == "uncertain" &&
    t.unknown.request != null && t.unknown.request.input != null &&
    t.unknown.request.input.ownerSessionId == None && t.unknown.request.input.requestIdentity != t.successorIdentity &&
    t.source != null && t.source.clean
  def validAnswer(a: Answer): Boolean = a != null && validTarget(a.target) && a.choice == choice &&
    exact(a.rationale) && exact(a.participant) && a.target.unknown.request.participant == a.participant &&
    a.reference != null && exact(a.reference.identity) && exact(a.reference.revision)
  def binding(a: Answer): Json = Json.obj("standaloneLocalPublicationRecovery" -> Json.obj(
    "target" -> a.target.asJson, "answer" -> a.asJson))
  def selectBinding(value: Json): Either[String, Json] = for {
    _ <- Either.cond(value != null && value.asObject.exists(_.keys.toSet == Set("standaloneLocalPublicationRecovery")), (), "Invalid standalone recovery binding")
    body <- value.hcursor.get[Json]("standaloneLocalPublicationRecovery").left.map(_.message)
    _ <- Either.cond(body.asObject.exists(_.keys.toSet == Set("target", "answer")), (), "Invalid standalone recovery fields")
    answer <- body.hcursor.get[Answer]("answer").left.map(_.message)
    target <- body.hcursor.get[Target]("target").left.map(_.message)
    _ <- Either.cond(validAnswer(answer) && answer.target == target, (), "Crossed standalone recovery binding")
  } yield value
  private def key(participant: String, identity: String): String = "local-publication-standalone-direction:" +
    java.net.URLEncoder.encode(participant, "UTF-8") + ":" + java.net.URLEncoder.encode(identity, "UTF-8")
  def describeC(component: String, input: LocalPublication.Input, successor: String, target: LocalPublication.Target,
    participant: String, port: LocalPublication.Port): Consequence[Target] = for {
    _ <- if (exact(component) && exact(successor) && input != null && successor != input.requestIdentity) Consequence.unit
      else Consequence.stateConflict("Standalone recovery requires a distinct successor identity")
    pair <- LocalPublication.describeStandaloneUnknownC(input, target, participant, port)
  } yield Target(component, pair._1, pair._2, successor)
  def admitC(answer: Answer, target: LocalPublication.Target, participant: String,
    component: String, port: LocalPublication.Port, admit: Json => Consequence[Unit]): Consequence[Direction] = for {
    _ <- if (validAnswer(answer) && answer.participant == participant && answer.target.componentInstance == component &&
      answer.target.unknown.request.target == target) Consequence.unit else Consequence.stateConflict("Crossed standalone recovery answer")
    _ <- admit(binding(answer))
    current <- describeC(component, answer.target.unknown.request.input, answer.target.successorIdentity, target, participant, port)
    _ <- if (current == answer.target) Consequence.unit else Consequence.stateConflict("Standalone recovery target or source changed")
    direction = Direction(answer)
    saved <- port.loadC(key(participant, answer.target.successorIdentity))
    _ <- saved match {
      case None => port.createC(key(participant, answer.target.successorIdentity), direction.asJson)
      case Some(value) => if (value == direction.asJson) Consequence.unit else Consequence.stateConflict("Successor identity has another direction")
    }
  } yield direction
  def advanceC(identity: String, original: LocalPublication.Input, target: LocalPublication.Target, participant: String, component: String,
    port: LocalPublication.Port): Consequence[LocalPublication.Result] = for {
    _ <- if (exact(identity)) Consequence.unit else Consequence.stateConflict("Missing standalone successor identity")
    value <- port.loadC(key(participant, identity)).flatMap(_.map(Consequence.success)
      .getOrElse(Consequence.stateConflict("No admitted standalone direction")))
    direction <- value.as[Direction].fold(e => Consequence.stateConflict(e.message), Consequence.success)
    answer = direction.answer
    _ <- if (validAnswer(answer) && answer.target.successorIdentity == identity && answer.participant == participant &&
      answer.target.componentInstance == component && answer.target.unknown.request.target == target &&
      answer.target.unknown.request.input == original)
      Consequence.unit else Consequence.stateConflict("Retained standalone direction is crossed")
    input = LocalPublication.Input(identity, target.identity)
    permit = LocalPublication.QualifiedSuccessor(answer.reference, answer.target.unknown.request, answer.target.source)
    result <- LocalPublication.ensureQualifiedSuccessorC(input, target, participant, permit, port)
  } yield result
}
