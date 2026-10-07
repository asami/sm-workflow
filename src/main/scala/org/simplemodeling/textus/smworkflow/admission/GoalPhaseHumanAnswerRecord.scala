package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.time.Instant
import scala.util.control.NonFatal
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentInstanceId
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.record.Record

/**
 * Structural receipt codec only. Decoding never issues human admission.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseHumanAnswerRecord {
  import GoalPhaseHumanDecisionContract.*
  import GoalPhaseHumanInteractionVerification.*
  val schemaVersion = "sm-workflow.goal-phase-human-answer-record.v1"
  val storageField = "humanAnswerPayload"
  private val prefix = "sm-workflow.goal-phase-human-answer-json.v1:"
  private val maximumBytes = 2 * 1024 * 1024

  def validateC(value: Receipt): Consequence[Unit] =
    if (value == null || value.binding == null || value.binding.componentInstance == null ||
        value.binding.question == null || !canonical(value.reference, 1024) || !canonical(value.issuer, 256) ||
        !canonical(value.humanParticipant, 256) || !canonical(value.binding.submittingParticipant, 256) ||
        value.purposeIdentity != purpose || value.occurredAt == null || value.validUntil == null ||
        value.validUntil.isBefore(value.occurredAt))
      Consequence.stateConflict("Human answer receipt differs from its storage contract")
    else value.binding.question.admitAnswerC(value.binding.answer).flatMap { _ =>
      if (value.binding.componentInstance.componentId == value.binding.question.intent.handle.componentIdentity) Consequence.unit
      else Consequence.stateConflict("Human answer belongs to another Component")
    }

  def encodeStorageC(value: Receipt): Consequence[String] = encodePayloadC(value, storageQuestion = true).flatMap { json =>
    val raw = prefix + json
    if (raw.getBytes(StandardCharsets.UTF_8).length <= maximumBytes) Consequence.success(raw)
    else Consequence.stateConflict("Human answer storage exceeds its bound")
  }

  def encodeJsonC(value: Receipt): Consequence[String] = encodePayloadC(value, storageQuestion = false)

  private def encodePayloadC(value: Receipt, storageQuestion: Boolean): Consequence[String] = for {
    _ <- validateC(value)
    question <- if (storageQuestion) GoalPhaseHumanQuestionRecord.encodeStorageC(value.binding.question)
      else GoalPhaseHumanQuestionRecord.encodeJsonC(value.binding.question)
    answer <- encodeAnswerC(value.binding.answer)
    raw = Json.obj("schemaVersion" -> Json.fromString(schemaVersion), "question" -> Json.fromString(question),
      "answer" -> Json.fromString(answer), "componentLabel" -> Json.fromString(value.binding.componentInstance.instance),
      "submittingParticipant" -> Json.fromString(value.binding.submittingParticipant), "reference" -> Json.fromString(value.reference),
      "issuer" -> Json.fromString(value.issuer), "humanParticipant" -> Json.fromString(value.humanParticipant),
      "purpose" -> Json.fromString(value.purposeIdentity), "occurredAt" -> Json.fromString(value.occurredAt.toString),
      "validUntil" -> Json.fromString(value.validUntil.toString)).noSpaces
    _ <- if (raw.getBytes(StandardCharsets.UTF_8).length <= maximumBytes) Consequence.unit
      else Consequence.stateConflict("Human answer record exceeds its bound")
  } yield raw

  def decodeJsonC(raw: String): Consequence[Receipt] =
    if (raw == null) Consequence.stateConflict("Human answer JSON is absent")
    else decodePayloadC(raw, storageQuestion = false)

  def decodeStorageC(raw: String): Consequence[Receipt] =
    if (raw == null || !raw.startsWith(prefix) || raw.length > maximumBytes || raw.getBytes(StandardCharsets.UTF_8).length > maximumBytes)
      Consequence.stateConflict("Human answer storage encoding is unsupported")
    else decodePayloadC(raw.substring(prefix.length), storageQuestion = true)

  private def decodePayloadC(raw: String, storageQuestion: Boolean): Consequence[Receipt] = {
    val names = Set("schemaVersion", "question", "answer", "componentLabel", "submittingParticipant", "reference",
      "issuer", "humanParticipant", "purpose", "occurredAt", "validUntil")
    val parsed = for {
      text <- Option(raw).filter(value => value.length <= maximumBytes && value.getBytes(StandardCharsets.UTF_8).length <= maximumBytes)
        .toRight("Human answer record is absent or unbounded")
      json <- parse(text).left.map(_ => "Human answer record JSON is invalid")
      fields <- json.asObject.filter(_.keys.toSet == names).toRight("Human answer record fields differ")
      values <- names.toVector.foldLeft[Either[String, Map[String, String]]](Right(Map.empty)) { (prior, key) => for {
        map <- prior
        value <- fields(key).flatMap(_.asString).toRight("Human answer record field is not text")
      } yield map.updated(key, value) }
      _ <- Either.cond(values("schemaVersion") == schemaVersion, (), "Human answer record schema differs")
    } yield values
    parsed.fold(Consequence.stateConflict(_), values => for {
      question <- if (storageQuestion) GoalPhaseHumanQuestionRecord.decodeStorageC(values("question"))
        else GoalPhaseHumanQuestionRecord.decodeJsonC(values("question"))
      answer <- decodeAnswerC(values("answer"))
      instance <- ComponentInstanceId.createC(question.intent.handle.componentIdentity, values("componentLabel"))
      occurred <- instantC(values("occurredAt"))
      until <- instantC(values("validUntil"))
      receipt = Receipt(values("reference"), values("issuer"), values("humanParticipant"), values("purpose"),
        Binding(instance, question, answer, values("submittingParticipant")), occurred, until)
      _ <- validateC(receipt)
    } yield receipt)
  }

  /** Verify only the absent row. Equal retry observes the original receipt,
    * including its original time interval; it does not renew any authority.
    */
  def retainC(expected: Binding, reference: String, storage: GoalPhaseClosureDecisionRetention.Port)
    (verified: => Consequence[Receipt]): Consequence[Receipt] =
    if (expected == null || expected.question == null || expected.componentInstance == null || storage == null ||
        !canonical(reference, 1024) || !canonical(expected.submittingParticipant, 256))
      Consequence.stateConflict("Human answer retention scope is absent")
    else for {
      _ <- expected.question.admitAnswerC(expected.answer)
      existing <- try storage.loadC() catch { case NonFatal(_) => Consequence.stateConflict("Initial answer observation failed") }
      result <- existing match {
        case Some(row) => compareC(expected, reference, row)
        case None => for {
          receipt <- try verified catch { case NonFatal(_) => Consequence.serviceUnavailable("Human verification failed before Create") }
          _ <- if (receipt != null && receipt.binding == expected && receipt.reference == reference) Consequence.unit
            else Consequence.securityPermissionDenied("Verified answer differs from the retained question")
          raw <- encodeStorageC(receipt)
          observed <- try storage.createC(Record.data(storageField -> raw)) match {
            case Consequence.Success(_) => storage.loadC() match {
              case Consequence.Success(Some(row)) => compareC(expected, reference, row) match {
                case Consequence.Success(value) if value == receipt => Consequence.success(value)
                case _ => uncertainC("Committed answer differs from verified receipt")
              }
              case _ => uncertainC("Committed answer observation is unavailable")
            }
            case _ => uncertainC("Answer Create outcome is uncertain")
          } catch { case NonFatal(_) => uncertainC("Answer Create or readback threw") }
        } yield observed
      }
    } yield result

  private def compareC(expected: Binding, reference: String, row: Record): Consequence[Receipt] =
    if (row == null) Consequence.stateConflict("Stored answer row is null")
    else row.getString(storageField).map(raw => decodeStorageC(raw).flatMap { value =>
      if (value.binding == expected && value.reference == reference) Consequence.success(value)
      else Consequence.stateConflict("Question already has a different immutable answer or interaction reference")
    }).getOrElse(Consequence.stateConflict("Stored answer has no closed payload"))
  private def instantC(value: String): Consequence[Instant] =
    try Consequence.success(Instant.parse(value)) catch { case NonFatal(_) => Consequence.stateConflict("Human receipt time is invalid") }
  private def uncertainC(message: String): Consequence[Receipt] = DataStoreConditionalTransitionFailure.transactionIndeterminate(message)
}
