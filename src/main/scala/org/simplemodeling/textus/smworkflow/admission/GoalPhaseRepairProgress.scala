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
  final case class Snapshot(automaticCount: Int, ordinaryCount: Int, limit: Int,
    runtimePolicy: Option[String] = None, selectedLimit: Option[Int] = None, effectiveLimit: Option[Int] = None)
  object Snapshot {
    given Encoder[Snapshot] = deriveEncoder[Snapshot].mapJson(_.mapObject(_.filter {
      case ("runtimePolicy" | "selectedLimit" | "effectiveLimit", value) => !value.isNull
      case _ => true
    }))
    private val _decoder: Decoder[Snapshot] = deriveDecoder
    given Decoder[Snapshot] = Decoder.instance(cursor => _decoder.tryDecode(cursor).flatMap { value =>
      if (Vector("runtimePolicy", "selectedLimit", "effectiveLimit").forall(key =>
          !cursor.downField(key).focus.contains(Json.Null)) && validateC(value).isSuccess) Right(value)
      else Left(DecodingFailure("Invalid selected repair progress", cursor.history))
    })
  }

  def snapshot(controller: GoalPhaseOrderedProgression.State): Snapshot =
    Snapshot(controller.repairs, controller.ordinaryRepairs, controller.plan.repairLimit,
      controller.repairControl.map(_.policy), controller.repairControl.map(_.selectedLimit),
      controller.repairControl.map(_.effectiveLimit))

  def validateC(value: Snapshot): Consequence[Unit] =
    if (value != null && 0 <= value.ordinaryCount && value.ordinaryCount <= value.automaticCount &&
        value.limit >= 0 && value.limit <= 64 && value.runtimePolicy != null && value.selectedLimit != null &&
        value.effectiveLimit != null && ((value.runtimePolicy.isEmpty && value.selectedLimit.isEmpty &&
          value.effectiveLimit.isEmpty && value.automaticCount <= value.limit) ||
          (value.runtimePolicy.contains(GoalPhaseRuntimeRepairControl.version) &&
            value.selectedLimit.exists(n => n >= 0 && n <= 64) &&
            value.effectiveLimit.contains(math.min(value.selectedLimit.get, GoalPhaseRuntimeRepairControl.ceiling)) &&
            value.automaticCount <= math.max(value.limit, value.selectedLimit.get)))) Consequence.unit
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
    V.GoalPhaseRepairProgress(value.automaticCount, value.ordinaryCount, value.limit,
      value.runtimePolicy, value.selectedLimit, value.effectiveLimit)

  def publicOptionC(value: Option[Snapshot]): Consequence[Option[V.GoalPhaseRepairProgress]] =
    validateOptionC(value).map(_ => value.map(publicSnapshot))
}
