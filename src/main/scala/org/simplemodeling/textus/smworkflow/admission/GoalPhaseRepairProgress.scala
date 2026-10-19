package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.value as V

/** Read-only current selected-window context owned by the ordered controller.
  * @since   Oct. 18, 2026
  * @version Oct. 18, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseRepairProgress {
  final case class Snapshot(automaticCount: Int, ordinaryCount: Int, limit: Int)
  object Snapshot {
    given Encoder[Snapshot] = deriveEncoder
    private val _decoder: Decoder[Snapshot] = deriveDecoder
    given Decoder[Snapshot] = Decoder.instance(cursor => _decoder.tryDecode(cursor).flatMap { value =>
      if (validateC(value).isSuccess) Right(value)
      else Left(DecodingFailure("Invalid selected repair progress", cursor.history))
    })
  }

  def snapshot(controller: GoalPhaseOrderedProgression.State): Snapshot =
    Snapshot(controller.repairs, controller.ordinaryRepairs, controller.plan.repairLimit)

  def validateC(value: Snapshot): Consequence[Unit] =
    if (value != null && 0 <= value.ordinaryCount && value.ordinaryCount <= value.automaticCount &&
        value.automaticCount <= value.limit && value.limit <= 64) Consequence.unit
    else Consequence.stateConflict("GoalPhase repair progress requires 0 <= ordinary <= automatic <= limit <= 64")

  def validateOptionC(value: Option[Snapshot]): Consequence[Unit] =
    if (value == null) Consequence.stateConflict("GoalPhase repair progress option is null")
    else value.fold(Consequence.unit)(validateC)

  def retainedC(value: Option[Snapshot], controller: GoalPhaseOrderedProgression.State): Consequence[Unit] = for {
    _ <- validateOptionC(value)
    _ <- if (value.forall(_ == snapshot(controller))) Consequence.unit
      else Consequence.stateConflict("Actual issued repair progress differs from its predecessor controller")
  } yield ()

  def decodeOption(fields: JsonObject): Either[String, Option[Snapshot]] = fields("repairProgress") match {
    case None => Right(None)
    case Some(value) => value.as[Snapshot].left.map(_.message).map(Some(_))
  }

  def selectOption(fields: JsonObject): Either[String, Option[Json]] =
    decodeOption(fields).map(_.map(_.asJson))

  def publicSnapshot(value: Snapshot): V.GoalPhaseRepairProgress =
    V.GoalPhaseRepairProgress(value.automaticCount, value.ordinaryCount, value.limit)

  def publicOptionC(value: Option[Snapshot]): Consequence[Option[V.GoalPhaseRepairProgress]] =
    validateOptionC(value).map(_ => value.map(publicSnapshot))
}
