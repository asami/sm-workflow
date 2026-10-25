package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.time.Instant
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.event.{EventId, EventLane, EventRecord}
import org.goldenport.cncf.workflow.WorkflowEventSelection
import DevelopmentOperationalView as V

/** Pure records for the existing managed transition's event argument. Neither
  * publication nor persistence selection is performed by this constructor.
  * @since Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseDevelopmentEvents {
  val schema = "sm-workflow.development-event.v1"
  val names = Vector("GoalPhaseStarted", "GoalPhaseCompleted", "GoalPhaseFailed", "ApprovalRequested", "ApprovalAccepted", "ApprovalRejected")
  def recordsC(configuration: Option[V.Configuration], session: String, revision: Long,
    eventType: String, causation: String, core: ActionCall.Core,
    occurredAt: Option[Instant] = None): Consequence[Vector[EventRecord]] = configuration match {
    case None => Consequence.success(Vector.empty)
    case Some(config) => for {
      _ <- if (bound(config.subject, core) && config.subject.sessionId == session)
        Consequence.unit else Consequence.stateConflict("Development event association differs")
      record <- createC(config.subject, revision, eventType, causation, occurredAt.getOrElse(core.executionContext.clock.instant()),
        core.correlationId.map(_.toString))
      selection <- WorkflowEventSelection.prepareFromStoreC(core.component.get.subsystem.get.eventStore, Vector(record))
      _ <- if (selection.persistent.size == 1 && selection.transient.isEmpty) Consequence.unit
        else Consequence.configurationInvalid("Development events require the host's persistent event policy")
    } yield selection.events
  }
  def bound(subject: V.Subject, core: ActionCall.Core): Boolean = V.valid(subject) && core != null &&
    core.component.exists(c => c.componentId.name == subject.producerComponentId && c.instanceId.canonicalKey == subject.producerInstanceId)
  def createC(subject: V.Subject, revision: Long, eventType: String, causation: String,
    at: Instant, trace: Option[String] = None): Consequence[EventRecord] = for {
    _ <- if (V.valid(subject) && subject.workflow == "GoalPhase" && revision > 0 && names.contains(eventType) &&
        V.exact(causation) && at != null) Consequence.unit else Consequence.stateConflict("Development event source is invalid")
    attributes <- DevelopmentEventCorrelation.attributesC(subject)
    key <- DevelopmentEventCorrelation.keyC(subject)
    envelope = V.Event(schema, subject, eventType, revision.toString, at.toString, _public_causation(eventType, revision.toString))
    entropy = MessageDigest.getInstance("SHA-256").digest(Json.arr(Json.fromString(key), Json.fromLong(revision),
      Json.fromString(eventType), Json.fromString(causation)).noSpaces.getBytes(UTF_8)).map(b => f"${b & 255}%02x").mkString
  } yield EventRecord(EventId("smworkflow", "development", Some(at), Some(entropy)), eventType, "development",
    Map("development" -> envelope.asJson.noSpaces), attributes ++ trace.map("correlationId" -> _), at,
    persistent = true, EventRecord.Status.Stored, EventLane.Transactional)

  /** No row is filtered: one invalid full envelope invalidates the observation. */
  def decodeC(subject: V.Subject, records: Vector[EventRecord]): Consequence[Vector[Json]] =
    records.foldLeft(Consequence.success(Vector.empty[Json])) { (prior, record) => for {
      values <- prior
      expected <- DevelopmentEventCorrelation.attributesC(subject)
      wire <- record.payload.get("development") match {
        case Some(value: String) => io.circe.parser.parse(value).fold(_ => _invalid, Consequence.success)
        case _ => _invalid
      }
      envelope <- wire.as[V.Event].fold(_ => _invalid, Consequence.success)
      _ <- if (envelope.schemaVersion == schema && envelope.subject == subject && names.contains(envelope.eventType) &&
          envelope.eventType == record.name && envelope.nativeRevision.matches("[1-9][0-9]{0,18}") &&
          scala.util.Try(envelope.nativeRevision.toLong).isSuccess &&
          envelope.causation == _public_causation(envelope.eventType, envelope.nativeRevision) &&
          scala.util.Try(Instant.parse(envelope.occurredAt)).toOption.contains(record.createdAt) &&
          record.sequence > 0 && expected.forall((key, value) => record.attributes.get(key).contains(value)) &&
          record.payload.keySet == Set("development") &&
          record.payload.get("development").contains(envelope.asJson.noSpaces)) Consequence.unit else _invalid
    } yield values :+ envelope.asJson.deepMerge(Json.obj("eventId" -> Json.fromString(record.id.value),
      "sequence" -> Json.fromString(record.sequence.toString))) }
  private def _invalid[A]: Consequence[A] = Consequence.stateConflict("Invalid development event source")
  private def _public_causation(eventtype: String, revision: String): String = {
    val operation = eventtype match {
      case "GoalPhaseStarted" => "startGoalPhase"
      case "ApprovalAccepted" | "ApprovalRejected" => "submitGoalPhaseOrderedDecision"
      case _ => "advanceGoalPhase"
    }
    "GoalPhaseService." + operation + "@" + revision
  }
}
