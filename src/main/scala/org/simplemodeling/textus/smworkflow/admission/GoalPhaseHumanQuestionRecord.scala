package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import scala.util.control.NonFatal
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.workflow.WorkflowDecisionJsonV1
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.record.Record

/**
 * Immutable answerable question, not human evidence, an answer or authorization.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseHumanQuestionRecord {
  import GoalPhaseHumanDecisionContract.*
  val schemaVersion = "sm-workflow.goal-phase-human-question-record.v1"
  val storageField = "humanQuestionPayload"
  private val prefix = "sm-workflow.goal-phase-human-question-json.v1:"
  private val maximumBytes = 1024 * 1024

  def encodeStorageC(value: Question): Consequence[String] =
    encodeJsonC(value).flatMap { json =>
      val raw = prefix + json
      if (raw.getBytes(StandardCharsets.UTF_8).length <= maximumBytes) Consequence.success(raw)
      else Consequence.stateConflict("Human question storage exceeds its bound")
    }

  /** Same complete typed question, without a storage envelope on public transports. */
  def encodeJsonC(value: Question): Consequence[String] =
    if (value == null) Consequence.stateConflict("Human question is absent")
    else for {
      _ <- value.validateC
      wire <- WorkflowDecisionJsonV1.encodeC(WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing](value.intent.handle,
        WorkflowContinuation.Decision(value.request, MinimalPresentation("Human Decision", "Not command authorization"))),
        GoalPhaseClosureAuthority.inputCodec)
      raw = Json.obj("schemaVersion" -> Json.fromString(schemaVersion),
        "questionSchema" -> Json.fromString(value.schemaVersion), "intentIdentity" -> Json.fromString(value.intent.idempotencyKey),
        "question" -> Json.fromString(wire), "choices" -> Json.arr(value.choices.map(choice => Json.obj(
          "identity" -> Json.fromString(choice.identity), "label" -> Json.fromString(choice.label)))*)).noSpaces
      _ <- if (raw.getBytes(StandardCharsets.UTF_8).length <= maximumBytes) Consequence.unit
        else Consequence.stateConflict("Human question storage exceeds its bound")
    } yield raw

  def decodeJsonC(raw: String): Consequence[Question] =
    if (raw == null) Consequence.stateConflict("Human question JSON is absent")
    else decodeStorageC(prefix + raw)

  def decodeStorageC(raw: String): Consequence[Question] = {
    val decoded = for {
      _ <- Either.cond(raw != null && raw.startsWith(prefix) && raw.length <= maximumBytes &&
        raw.getBytes(StandardCharsets.UTF_8).length <= maximumBytes, (), "Human question storage encoding is unsupported")
      json <- parse(raw.substring(prefix.length)).left.map(_ => "Human question storage JSON is invalid")
      fields <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "questionSchema", "intentIdentity", "question", "choices"))
        .toRight("Human question storage fields differ")
      _ <- Either.cond(fields("schemaVersion").flatMap(_.asString).contains(schemaVersion) &&
        fields("questionSchema").flatMap(_.asString).contains(questionSchema), (), "Human question storage schema differs")
      identity <- fields("intentIdentity").flatMap(_.asString).filter(canonical(_, 256)).toRight("Human intent identity is invalid")
      wire <- fields("question").flatMap(_.asString).toRight("Human question wire is missing")
      items <- fields("choices").flatMap(_.asArray).filter(values => values.size >= 2 && values.size <= 16).toRight("Human choices are unbounded")
      choices <- items.foldLeft[Either[String, Vector[Choice]]](Right(Vector.empty)) { (prior, item) => for {
        accumulated <- prior
        value <- item.asObject.filter(_.keys.toSet == Set("identity", "label")).toRight("Human choice fields differ")
        id <- value("identity").flatMap(_.asString).toRight("Human choice identity is missing")
        label <- value("label").flatMap(_.asString).toRight("Human choice label is missing")
      } yield accumulated :+ Choice(id, label) }
    } yield (identity, wire, choices)
    decoded.fold(Consequence.stateConflict(_), { case (identity, wire, choices) =>
      WorkflowDecisionJsonV1.decodeC(wire, GoalPhaseClosureAuthority.inputCodec).flatMap { interaction => interaction.current match {
        case WorkflowContinuation.Decision(request, _) => request.input match {
          case Some(value) if value.typeIdentity == GoalPhaseClosureAuthority.inputCodec.typeIdentity =>
            val intent = GoalPhaseClosureAuthority.Intent(interaction.handle, request.expectedRevision, request.context.snapshot, value.value, identity)
            val question = Question(intent, request, choices)
            question.validateC.map(_ => question)
          case _ => Consequence.stateConflict("Stored human question has no typed intent")
        }
        case _ => Consequence.stateConflict("Stored human question is not a Decision")
      }}
    })
  }

  def retainC(expected: Question, storage: GoalPhaseClosureDecisionRetention.Port): Consequence[Question] =
    if (storage == null) Consequence.stateConflict("Human question storage is missing")
    else for {
      raw <- encodeStorageC(expected)
      existing <- try storage.loadC() catch { case NonFatal(_) => Consequence.stateConflict("Initial human question observation failed") }
      result <- existing match {
        case Some(record) => compareC(expected, record)
        case None => try storage.createC(Record.data(storageField -> raw)) match {
          case Consequence.Success(_) => storage.loadC() match {
            case Consequence.Success(Some(record)) => compareC(expected, record) match {
              case Consequence.Success(value) => Consequence.success(value)
              case _ => uncertainC("Human question committed observation differs")
            }
            case _ => uncertainC("Human question committed observation is unavailable")
          }
          case _ => uncertainC("Human question Create outcome is uncertain")
        } catch { case NonFatal(_) => uncertainC("Human question Create or readback threw") }
      }
    } yield result

  private def compareC(expected: Question, record: Record): Consequence[Question] =
    if (record == null) Consequence.stateConflict("Human question record is absent")
    else record.getString(storageField).map(value => decodeStorageC(value).flatMap { actual =>
      if (actual == expected) Consequence.success(actual) else Consequence.stateConflict("Human question belongs to another contract")
    }).getOrElse(Consequence.stateConflict("Human question record has no closed payload"))

  private def uncertainC(message: String): Consequence[Question] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate(message)
}
