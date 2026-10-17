package org.simplemodeling.textus.smworkflow.host

import scala.util.control.NonFatal
import io.circe.parser.decode
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.textus.smworkflow.admission.*
import org.simplemodeling.textus.smworkflow.value as V

/** Finite preparation of explicitly selected Goal business input and Plan.
  * Supplied origin metadata authenticates no Human and grants no permission.
  * @since   Oct. 14, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeHostGoalStartInput {
  private val _required = Set("input", "plan", "selection-id", "participant", "origin", "idempotency-key",
    "selection-collection", "plan-collection")

  def prepareC(supplied: Map[String, String], readText: String => Consequence[String]): Consequence[Record] = {
    try {
      for {
        options <- NativeHostPreparationInput.optionsC(supplied, _required, readText)
        _ <- if (options("origin") == ProfileStartAdmission.SelectionOrigin.ExplicitHuman.toString) Consequence.unit
          else _invalid("origin must be ExplicitHuman")
        _ <- NativeHostPreparationInput.collectionC(options("selection-collection"))
        _ <- NativeHostPreparationInput.collectionC(options("plan-collection"))
        _ <- if (options("selection-collection") != options("plan-collection")) Consequence.unit
          else _invalid("selected collections must be distinct")
        inputText <- readText(options("input"))
        record <- new RecordDecoder().json(inputText)
        input <- summon[ValueReader[V.GoalPhaseStartInput]].readC(record)
        planText <- readText(options("plan"))
        plan <- decode[GoalPhaseOrderedProgression.Plan](planText).fold(
          _ => _invalid("selected Plan JSON is structurally invalid"), Consequence.success)
        catalog <- GoalPhaseRuntimeCatalog.loadC()
        definition = catalog.definition
        selection = ProfileStartAdmission.Selection(options("selection-id"), GoalPhaseNativeRun.startOperation,
          definition.workflowIdentity, definition.workflowRevision, options("participant"),
          ProfileStartAdmission.SelectionOrigin.ExplicitHuman, options("idempotency-key"), None,
          Some(TypedValue(NativeWorkflowStartInput.Kind.GoalPhase.inputType, input.asJson)))
        selectionPayload <- ProfileSelectionJsonV1.encodeStorageC(selection)
        planPayload <- GoalPhaseSelectedPlanJsonV1.encodeStorageC(
          GoalPhaseNativeRun.SelectedPlan(selection.selectionId, options("participant"), plan))
        selectionRow = Record.data("id" -> NativeWorkflowStartBusinessKey(
          NativeWorkflowStartInput.Goal(input), options("participant")), ProfileSelectionJsonV1.storageField -> selectionPayload)
        planRow = Record.data("id" -> selection.selectionId, GoalPhaseSelectedPlanJsonV1.storageField -> planPayload)
        bootstrap = Record.data("datastore" -> Vector(
          Record.data("collection" -> options("selection-collection"), "records" -> Vector(selectionRow)),
          Record.data("collection" -> options("plan-collection"), "records" -> Vector(planRow))))
      } yield bootstrap
    } catch { case NonFatal(_) => _invalid("invalid explicit argument, file or typed input") }
  }

  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Host Goal Start input " + reason)
}
