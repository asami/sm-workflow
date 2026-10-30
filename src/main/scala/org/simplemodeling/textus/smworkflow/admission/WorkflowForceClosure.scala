/**
 * @since   Oct. 30, 2026
 * @version Oct. 30, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.net.{URLDecoder, URLEncoder}
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1

/** A Human-directed terminal append, never ordinary completion or recovery.
  * The independently retained direction contains the reason, unfinished work
  * and optional Human acceptance. The native history points to that direction;
  * original business checkpoints and issued interactions are not rewritten.
  */
private[smworkflow] object WorkflowForceClosure {
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  val schema = "sm-workflow.force-closure-target.v1"
  val purpose = "workflow-force-closure"
  val causePrefix = "workflow-forced-close:"
  val families = Set("GoalPhase", "GoalTask", "DelegateTask")
  val acceptances = Set("NotAccepted", "AcceptedByHuman")
  val limitation = "Forced closure is not normal completion or validation success. It executes no Git, SBT, " +
    "commit, publication or recovery and does not terminate an already running external process. " +
    "All prior incomplete, failed and UNKNOWN effects remain as recorded."

  final case class Target(schemaVersion: String, componentInstance: String, family: String,
    sessionId: String, participant: String, frontier: Json)
  object Target { given Encoder[Target] = deriveEncoder; given Decoder[Target] = deriveDecoder }
  final case class Direction(target: Target, choice: String, reason: String, unfinished: Vector[String],
    acceptance: String, acknowledgeUncertainEffects: Boolean, participant: String, reference: ContextReference)
  object Direction { given Encoder[Direction] = deriveEncoder; given Decoder[Direction] = deriveDecoder }
  final case class Admitted(schemaVersion: String, direction: Direction, issuer: String,
    sourceOccurredAt: String, admittedAt: String)
  object Admitted { given Encoder[Admitted] = deriveEncoder; given Decoder[Admitted] = deriveDecoder }
  val admittedSchema = "sm-workflow.force-closure-admitted.v1"
  val admittedField = "workflowForceClosureAdmittedPayload"
  def admittedKey(target: Target, revision: Long, reference: ContextReference): String = "force-closure-admitted:" +
    URLEncoder.encode(Json.arr(target.componentInstance.asJson, target.family.asJson, target.sessionId.asJson,
      revision.asJson, reference.identity.asJson, reference.revision.asJson).noSpaces, UTF_8)
  def admittedC(raw: String): Consequence[Admitted] = {
    val decoded = for {
      json <- io.circe.parser.parse(raw).left.map(_.message)
      value <- json.as[Admitted].left.map(_.message)
      _ <- Either.cond(json == value.asJson && value.schemaVersion == admittedSchema && exact(value.issuer) &&
        scala.util.Try(java.time.Instant.parse(value.sourceOccurredAt)).isSuccess &&
        scala.util.Try(java.time.Instant.parse(value.admittedAt)).isSuccess,
        (), "Retained force closure direction is noncanonical")
      _ <- validateC(value.direction).toOption.toRight("Retained force closure direction is invalid")
    } yield value
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }

  private def exact(s: String): Boolean = s != null && s.nonEmpty && s.trim == s && !s.exists(_.isControl)
  private def text(s: String): Boolean = s != null && s.trim.nonEmpty && UTF_8.newEncoder().canEncode(s) &&
    !s.contains('\u0000') && s.getBytes(UTF_8).length <= 16384
  def decodeTarget(value: Json): Either[String, Target] = for {
    target <- value.as[Target].left.map(_.message)
    _ <- Either.cond(target.asJson == value && target.schemaVersion == schema && families(target.family) &&
      Vector(target.componentInstance, target.sessionId, target.participant).forall(exact) && target.frontier.isObject,
      (), "Force closure requires its complete canonical native target")
  } yield target
  def targetC(text: String): Consequence[Target] = io.circe.parser.parse(Option(text).getOrElse(""))
    .left.map(_.message).flatMap(decodeTarget).fold(Consequence.valueInvalid(_), Consequence.success)

  def validateC(direction: Direction): Consequence[Unit] =
    if (direction == null || direction.target == null || decodeTarget(direction.target.asJson).isLeft ||
        direction.choice != "ForceClose" || direction.participant != direction.target.participant ||
        !text(direction.reason) || direction.unfinished == null || direction.unfinished.size > 128 ||
        !direction.unfinished.forall(text) || !acceptances(direction.acceptance) ||
        !direction.acknowledgeUncertainEffects || direction.reference == null ||
        !Vector(direction.reference.identity, direction.reference.revision).forall(exact))
      Consequence.valueInvalid("Force closure needs an exact Human direction, reason, unfinished work, acceptance and retained uncertainty")
    else Consequence.unit

  def binding(direction: Direction): Json = Json.obj("workflowForceClosure" -> direction.asJson)
  def selectBinding(value: Json): Either[String, Json] = for {
    direction <- value.hcursor.get[Direction]("workflowForceClosure").left.map(_.message)
    _ <- validateC(direction).toOption.toRight("Invalid force closure Human direction")
    _ <- Either.cond(binding(direction) == value, (), "Force closure Human evidence has unsupported fields")
  } yield value

  def targetC(owner: String, family: String, participant: String, current: InstanceRecord): Consequence[Target] = for {
    _ <- if (families(family) && Vector(owner, participant).forall(exact) && current != null &&
        current.lifecycle == Lifecycle.Active) Consequence.unit
      else Consequence.stateConflict("Only an active native workflow may be force-closed")
    wire <- WorkflowInstanceRecordJsonV1.encodeC(current)
    frontier <- io.circe.parser.parse(wire).fold(e => Consequence.valueInvalid(e.message), Consequence.success)
  } yield Target(schema, owner, family, current.identity.value, participant, frontier)

  /** Only a terminal append changes; nothing is repaired or declared successful. */
  def appendC(current: InstanceRecord, direction: Direction, actual: Target): Consequence[InstanceRecord] = for {
    _ <- validateC(direction)
    _ <- if (direction.target == actual && actual.sessionId == current.identity.value &&
        current.lifecycle == Lifecycle.Active) Consequence.unit
      else Consequence.stateConflict("Force closure direction is stale or crosses its native owner")
    currentWire <- WorkflowInstanceRecordJsonV1.encodeC(current)
    frontier <- io.circe.parser.parse(currentWire).fold(e => Consequence.valueInvalid(e.message), Consequence.success)
    _ <- if (frontier == actual.frontier) Consequence.unit
      else Consequence.stateConflict("Force closure native frontier changed")
    next <- current.appendC(current.revision, HistoryEntry(HistorySequence(current.history.size.toLong + 1),
      current.revision.next, Lifecycle.Completed, None, current.correlation,
      CausationReference(cause(direction.reference)), current.derivedCompositeOccurrence, current.committedPredecessor, None))
  } yield next

  def cause(reference: ContextReference): String = causePrefix + URLEncoder.encode(
    Json.arr(reference.identity.asJson, reference.revision.asJson).noSpaces, UTF_8)
  def referenceC(current: InstanceRecord): Consequence[ContextReference] = {
    val decoded = for {
      _ <- Either.cond(isClosed(current), (), "Workflow is not force-closed")
      wire <- scala.util.Try(URLDecoder.decode(current.causation.value.stripPrefix(causePrefix), UTF_8)).toEither.left.map(_.getMessage)
      value <- io.circe.parser.parse(wire).left.map(_.message)
      parts <- value.as[Vector[String]].left.map(_.message)
      _ <- Either.cond(parts.size == 2 && parts.forall(exact), (), "Force closure direction reference is invalid")
      reference = ContextReference(parts(0), parts(1))
      _ <- Either.cond(cause(reference) == current.causation.value, (), "Force closure reference is noncanonical")
    } yield reference
    decoded.fold(Consequence.stateConflict(_), Consequence.success)
  }
  def isClosed(current: InstanceRecord): Boolean = current != null && current.lifecycle == Lifecycle.Completed &&
    current.causation.value.startsWith(causePrefix)
  def requireOpenC(current: InstanceRecord): Consequence[Unit] = if (isClosed(current))
    Consequence.stateConflict("Workflow was force-closed by Human direction; use describeWorkflowForceClosure. " + limitation)
    else Consequence.unit
}
