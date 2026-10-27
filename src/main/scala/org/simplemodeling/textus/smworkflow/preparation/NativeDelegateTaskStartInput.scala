package org.simplemodeling.textus.smworkflow.preparation

import org.simplemodeling.textus.smworkflow.admission.StandaloneFixExecution.given

import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.textus.smworkflow.admission.*
import org.simplemodeling.textus.smworkflow.value as V

/** Passive preparation; returned data does not itself authorize Start.
  * @since   Oct. 26, 2026
  * @version Oct. 27, 2026
  */
private[smworkflow] object NativeDelegateTaskStartInput {
  def prepareC(supplied: Map[String, String], read: String => Consequence[String]): Consequence[Record] = for {
    options <- NativePreparationInput.optionsC(supplied, Set("input", "selection-id", "participant", "origin", "idempotency-key", "selection-collection"), read)
    _ <- if (options("origin") == "ExplicitHuman") Consequence.unit else Consequence.stateConflict("Task Start requires ExplicitHuman selection")
    _ <- NativePreparationInput.collectionC(options("selection-collection"))
    text <- read(options("input"))
    record <- new RecordDecoder().json(text)
    value <- summon[ValueReader[V.DelegateTaskInput]].readC(record)
    input <- DelegateTask.inputC(value)
    definition <- DelegateTaskRuntimeCatalog.loadC()
    selection = ProfileStartAdmission.Selection(options("selection-id"), DelegateTaskNativeRun.startOperation,
      definition.workflowIdentity, definition.workflowRevision, options("participant"), ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
      options("idempotency-key"), None, Some(TypedValue("DelegateTaskInput", input.asJson)))
    payload <- ProfileSelectionJsonV1.encodeStorageC(selection)
  } yield Record.data("datastore" -> Vector(Record.data("collection" -> options("selection-collection"), "records" ->
    Vector(Record.data("id" -> options("selection-id"), ProfileSelectionJsonV1.storageField -> payload)))))
}
