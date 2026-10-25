package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.record.Record

/** Safe application observation vocabulary. No native controller or effect input.
  * @since Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object DevelopmentOperationalView {
  val schema = "sm-workflow.development-observation.v1"
  final case class Car(group: String, artifact: String, version: String)
  final case class Subject(car: Car, producerComponentId: String, producerInstanceId: String,
    workflow: String, sessionId: String, repositoryIdentity: String, phaseIdentity: String)
  final case class Configuration(schemaVersion: String, subject: Subject, ownerDetailsUrl: Option[String] = None)
  final case class Request(schemaVersion: String, subject: Subject, limit: Int = 20)
  final case class State(revision: String, workflowState: String, stage: Option[String],
    attention: String, stepIdentity: Option[String] = None, workKind: Option[String] = None)
  final case class Event(schemaVersion: String, subject: Subject, eventType: String,
    nativeRevision: String, occurredAt: String, causation: String)

  given Encoder[Car] = deriveEncoder
  given Decoder[Car] = strict(deriveDecoder[Car], Set("group", "artifact", "version"))
  given Encoder[Subject] = deriveEncoder
  given Decoder[Subject] = strict(deriveDecoder[Subject], Set("car", "producerComponentId", "producerInstanceId",
    "workflow", "sessionId", "repositoryIdentity", "phaseIdentity"))
  given Encoder[Configuration] = deriveEncoder
  given Decoder[Configuration] = strict(deriveDecoder[Configuration], Set("schemaVersion", "subject", "ownerDetailsUrl"))
  given Encoder[State] = deriveEncoder[State].mapJson(_.dropNullValues)
  given Encoder[Event] = deriveEncoder
  given Decoder[Event] = strict(deriveDecoder[Event], Set("schemaVersion", "subject", "eventType", "nativeRevision", "occurredAt", "causation"))

  def strict[A](decoder: Decoder[A], keys: Set[String]): Decoder[A] = Decoder.instance { cursor =>
    cursor.value.asObject.filter(_.keys.toSet.subsetOf(keys))
      .toRight(io.circe.DecodingFailure("Unknown observation field", cursor.history))
      .flatMap(_ => decoder(cursor))
  }
  def exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value &&
    !value.headOption.exists(c => Character.isWhitespace(c) || Character.isSpaceChar(c)) &&
    !value.lastOption.exists(c => Character.isWhitespace(c) || Character.isSpaceChar(c)) &&
    UTF_8.newEncoder().canEncode(value) && value.getBytes(UTF_8).length <= 1024 && !value.exists(Character.isISOControl)
  def valid(subject: Subject): Boolean = subject != null && subject.car != null &&
    Vector(subject.car.group, subject.car.artifact, subject.car.version, subject.producerComponentId,
      subject.producerInstanceId, subject.workflow, subject.sessionId, subject.repositoryIdentity, subject.phaseIdentity).forall(exact)
  def decodeConfigurationC(value: Json): Consequence[Configuration] =
    Option(value).toRight("Missing observation configuration").flatMap(_.as[Configuration].left.map(_ => "Invalid observation configuration"))
      .flatMap(c => Either.cond(c.schemaVersion == schema && valid(c.subject) && c.ownerDetailsUrl != null &&
        c.ownerDetailsUrl.forall(safeDetailsUrl), c, "Invalid observation configuration"))
      .fold(Consequence.configurationInvalid(_), Consequence.success)
  def safeDetailsUrl(value: String): Boolean = try {
    val uri = new URI(value)
    exact(value) && uri.isAbsolute && Set("https", "http")(uri.getScheme) && uri.getHost != null &&
      uri.getRawUserInfo == null && uri.getRawQuery == null && uri.getRawFragment == null &&
      (uri.getScheme == "https" || Set("localhost", "127.0.0.1", "::1")(uri.getHost))
  } catch { case _: IllegalArgumentException | _: java.net.URISyntaxException => false }

  def decodeRequestC(value: Json, history: Boolean): Consequence[Request] = {
    val decoded = for {
      wire <- Option(value).toRight("Missing input")
      fields <- wire.asObject.toRight("Input requires an object")
      _ <- Either.cond(fields.keys.toSet.subsetOf(if (history) Set("schemaVersion", "subject", "limit") else Set("schemaVersion", "subject")), (), "Unknown input field")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_ => "Missing schema")
      subject <- wire.hcursor.get[Subject]("subject").left.map(_ => "Invalid subject")
      limit <- fields("limit").map(_.asNumber.flatMap(_.toInt).toRight("Invalid limit")).getOrElse(Right(20))
      _ <- Either.cond(version == schema && valid(subject) && limit >= 1 && limit <= 100, (), "Invalid input")
    } yield Request(version, subject, limit)
    decoded.fold(_ => Consequence.valueInvalid("Development observation input is invalid"), Consequence.success)
  }
  def json(value: Any): Json = value match {
    case value: Record => Json.obj(value.fields.map(field => field.key -> json(field.value.single))* )
    case value: Json => value
    case value: String => Json.fromString(value)
    case value: Boolean => Json.fromBoolean(value)
    case value: BigDecimal => Json.fromBigDecimal(value)
    case value: Int => Json.fromInt(value)
    case value: Long => Json.fromLong(value)
    case values: Seq[?] => Json.fromValues(values.map(json))
    case null => Json.Null
    case _ => Json.Null
  }
  /** Record-to-JSON conversion must not erase a duplicate nested wire identity. */
  def uniqueFields(value: Any): Boolean = value match {
    case value: Record => value.fields.map(_.key).distinct.size == value.fields.size &&
      value.fields.forall(field => uniqueFields(field.value.single))
    case values: Seq[?] => values.forall(uniqueFields)
    case _ => true
  }
  def response(status: String, observedAt: Instant, subject: Option[Subject] = None,
    state: Option[State] = None, events: Option[Vector[Json]] = None, truncated: Boolean = false,
    details: Option[String] = None): Json = Json.obj(
      "schemaVersion" -> Json.fromString(schema), "status" -> Json.fromString(status),
      "observedAt" -> Json.fromString(observedAt.toString), "subject" -> subject.map(_.asJson).getOrElse(Json.Null),
      "state" -> state.map(_.asJson).getOrElse(Json.Null), "events" -> events.map(Json.fromValues).getOrElse(Json.Null),
      "coverage" -> events.map(_ => Json.fromString("retained-v1-window")).getOrElse(Json.Null),
      "returned" -> events.map(v => Json.fromInt(v.size)).getOrElse(Json.Null),
      "truncated" -> events.map(_ => Json.fromBoolean(truncated)).getOrElse(Json.Null),
      "ownerDetailsUrl" -> details.map(Json.fromString).getOrElse(Json.Null)).dropNullValues
}
