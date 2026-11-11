package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.simplemodeling.textus.smworkflow.value as V

/** Read-only current selected-window context owned by the ordered controller.
  * @since   Oct. 18, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseRepairProgress {
  /** Ephemeral evidence reconstructed only while traversing a retained Human answer. */
  final class WindowContinuation private[GoalPhaseRepairProgress] (val direction: GoalPhaseDependencySourceRevision.Direction,
    private val after: GoalPhaseOrderedProgression.State,
    private val boundary: InstanceRecord) {
    def permits(selected: GoalPhaseDependencySourceRevision.Direction, current: GoalPhaseOrderedProgression.State, from: InstanceRecord): Boolean =
      selected == direction && from.identity == boundary.identity && from.revision.value >= boundary.revision.value &&
        from.history.take(boundary.history.size) == boundary.history &&
        current.plan == after.plan && current.base == after.base && current.completed == after.completed &&
        current.cursor == after.cursor && current.generation >= after.generation &&
        current.plan.steps.lift(current.cursor).exists(_.identity == direction.target.step) &&
        historical(snapshot(after), snapshot(current))
  }

  def historical(previous: Snapshot, current: Snapshot): Boolean =
    validateC(previous).isSuccess && validateC(current).isSuccess &&
      current.limit == previous.limit && current.runtimePolicy == previous.runtimePolicy &&
      current.selectedLimit == previous.selectedLimit && current.effectiveLimit == previous.effectiveLimit &&
      current.automaticCount >= previous.automaticCount && current.ordinaryCount >= previous.ordinaryCount

  /** Caller must already have authenticated the retained Human answer and native boundary. */
  def retainedWindowC(direction: GoalPhaseDependencySourceRevision.Direction, before: GoalPhaseOrderedProgression.State,
    after: GoalPhaseOrderedProgression.State, from: InstanceRecord, next: InstanceRecord,
    evidence: String, previous: Option[WindowContinuation] = None): Consequence[Option[WindowContinuation]] =
    before.acceptC(GoalPhaseOrderedProgression.RepairContinuationAdmitted(evidence)).map { expected =>
      val target = direction.target
      val same = GoalPhaseCandidateRecovery.controllerSnapshot(expected) == GoalPhaseCandidateRecovery.controllerSnapshot(after) &&
        expected.repairControl == after.repairControl && expected.repairObservations == after.repairObservations &&
        GoalPhaseDependencySourceRevision.directionValid(direction) && from.identity == next.identity &&
        from.identity.value == target.sessionId && next.revision.value > from.revision.value &&
        next.history.take(from.history.size) == from.history &&
        target.sourceRevision.toLongOption.exists(_ <= from.revision.value) &&
        target.plan == before.plan && target.primaryBase == before.base && target.completed == before.completed &&
        before.plan.steps.lift(before.cursor).exists(_.identity == target.step) &&
        target.generation < after.generation &&
        (historical(target.repairProgress, snapshot(before)) || previous.exists(_.permits(direction, before, from))) &&
        snapshot(after).automaticCount == 1 && snapshot(after).ordinaryCount == 1 &&
        snapshot(before).copy(automaticCount = 1, ordinaryCount = 1) == snapshot(after) &&
        validateC(snapshot(after)).isSuccess
      Option.when(same)(new WindowContinuation(direction, after, next))
    }
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
