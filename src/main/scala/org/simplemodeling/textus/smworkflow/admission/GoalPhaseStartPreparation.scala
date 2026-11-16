package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.resource.ResourceReference
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.textus.smworkflow.preparation.{NativeGoalStartInput, NativeGoalTaskStartInput}
import org.simplemodeling.textus.smworkflow.value as V

/** Installs only the explicitly supplied Start selection and Plan into the
  * existing managed store. This supplies no Human or execution authority. */
private[smworkflow] object GoalPhaseStartPreparation {
  def prepareC(core: ActionCall.Core, input: Record, intent: V.GoalPhaseStartIntent): Consequence[Unit] = for {
    _ <- GoalPhaseParticipantAdmission.admitC(intent.participant, core.executionContext)
    component <- core.component.map(Consequence.success).getOrElse(invalid("component is unavailable"))
    host <- component.subsystem.map(Consequence.success).getOrElse(invalid("subsystem is unavailable"))
    records <- GoalPhaseInputRecordReadPolicy.decodeC(host.configuration)
    plans <- GoalPhaseNativeInputReadPolicy.decodeC(host.configuration)
    selection <- records.collections.find(_._1 == GoalPhaseInputRecordReadPolicy.Purpose.Selection)
      .map(value => Consequence.success(value._2)).getOrElse(invalid("selection collection is absent"))
    inputText <- input.toJsonStringC
    prepared <- NativeGoalStartInput.prepareC(Map("input" -> "selected-input", "plan" -> intent.plan,
      "selection-id" -> intent.selectionId, "participant" -> intent.participant, "origin" -> intent.origin,
      "idempotency-key" -> intent.idempotencyKey, "selection-collection" -> "entity:smworkflow.local.selection",
      "plan-collection" -> "entity:smworkflow.local.plans"), value =>
      if (value == "selected-input") Consequence.success(inputText)
      else ResourceReference.parseC(value).flatMap {
        case reference: ResourceReference.Url if reference.scheme == "file" =>
          WorkflowArtifactBudget.readTextC(core, reference, Some(UTF_8))
        case _ => invalid("Plan requires an absolute local file URI")
      })
    text <- prepared.toJsonStringC
    json <- io.circe.parser.parse(text).fold(error => invalid(error.message), Consequence.success)
    groups <- json.hcursor.get[Vector[Json]]("datastore").fold(error => invalid(error.message), Consequence.success)
    _ <- if (groups.size == 2) Consequence.unit else invalid("prepared selection and Plan are incomplete")
    selectedRecord <- prepareRecordC(core, records.storeName, selection, groups(0))
    selectedPlan <- prepareRecordC(core, plans.storeName, plans.plans, groups(1))
    _ <- selectedRecord()
    _ <- selectedPlan()
  } yield ()

  def prepareTaskC(core: ActionCall.Core, input: Record, selectionId: String, participant: String,
    idempotencyKey: String, intent: V.GoalTaskStartIntent): Consequence[Unit] = for {
    _ <- GoalPhaseParticipantAdmission.admitC(participant, core.executionContext)
    component <- core.component.map(Consequence.success).getOrElse(invalid("component is unavailable"))
    host <- component.subsystem.map(Consequence.success).getOrElse(invalid("subsystem is unavailable"))
    records <- GoalPhaseInputRecordReadPolicy.decodeC(host.configuration)
    selection <- records.collections.find(_._1 == GoalPhaseInputRecordReadPolicy.Purpose.Selection)
      .map(value => Consequence.success(value._2)).getOrElse(invalid("selection collection is absent"))
    inputText <- input.toJsonStringC
    prepared <- NativeGoalTaskStartInput.prepareC(Map("input" -> "selected-input", "selection-id" -> selectionId,
      "participant" -> participant, "origin" -> intent.origin, "idempotency-key" -> idempotencyKey,
      "selection-collection" -> "entity:smworkflow.local.selection"), _ => Consequence.success(inputText))
    text <- prepared.toJsonStringC
    json <- io.circe.parser.parse(text).fold(error => invalid(error.message), Consequence.success)
    groups <- json.hcursor.get[Vector[Json]]("datastore").fold(error => invalid(error.message), Consequence.success)
    pending <- if (groups.size == 1) prepareRecordC(core, records.storeName, selection, groups.head)
      else invalid("prepared Task selection is incomplete")
    _ <- pending()
  } yield ()

  private def prepareRecordC(core: ActionCall.Core, store: String, collection: DataStore.CollectionId,
    group: Json): Consequence[() => Consequence[Unit]] = for {
    rows <- group.hcursor.get[Vector[Json]]("records").fold(error => invalid(error.message), Consequence.success)
    row <- if (rows.size == 1) Consequence.success(rows.head) else invalid("prepared record count differs")
    id <- row.hcursor.get[String]("id").fold(error => invalid(error.message), Consequence.success)
    payload <- new RecordDecoder().json(row.mapObject(_.remove("id")).noSpaces)
    target <- ComponentRecordStorage.bindForCallC(core, store, collection, DataStore.StringEntryId(id))
    existing <- target.loadC()
    pending <- existing match {
      case None => Consequence.success(() => target.createC(payload))
      case Some(value) => for {
        expected <- payload.toJsonStringC
        actual <- value.toJsonStringC
        a <- io.circe.parser.parse(actual).fold(error => invalid(error.message), Consequence.success)
        e <- io.circe.parser.parse(expected).fold(error => invalid(error.message), Consequence.success)
        _ <- if (a.mapObject(_.remove("id")) == e) Consequence.unit
          else invalid("existing selected input or Plan conflicts with the supplied intent")
      } yield (() => Consequence.unit)
    }
  } yield pending

  private def invalid[A](message: String): Consequence[A] =
    Consequence.stateConflict("GoalPhase Start preparation: " + message)
}
