/**
 * @since   Oct. 26, 2026
 * @version Oct. 26, 2026
 */
package org.simplemodeling.textus.smworkflow.preparation

import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.textus.smworkflow.admission.*
import org.simplemodeling.textus.smworkflow.admission.StandaloneFixExecution.given
import org.simplemodeling.textus.smworkflow.value as V

/** Passive adoption of selected original review facts; no execution authority. */
private[smworkflow] object NativeStandaloneReviewFixStartInput {
  def prepareC(supplied: Map[String, String], read: String => Consequence[String]): Consequence[Record] = for {
    options <- NativePreparationInput.optionsC(supplied, Set("input", "selection-id", "participant", "origin", "idempotency-key", "selection-collection"), read)
    _ <- if (options("origin") == "ExplicitHuman") Consequence.unit
      else Consequence.stateConflict("Review-fix Start requires ExplicitHuman selection")
    _ <- NativePreparationInput.collectionC(options("selection-collection"))
    text <- read(options("input"))
    record <- new RecordDecoder().json(text)
    suppliedInput <- summon[ValueReader[V.StandaloneReviewFixInput]].readC(record)
    input <- StandaloneReviewFix.fromPublicC(suppliedInput)
    definition <- StandaloneFixRuntimeCatalog.loadC()
    selection = ProfileStartAdmission.Selection(options("selection-id"), StandaloneFixNativeRun.reviewStartOperation,
      definition.workflowIdentity, definition.workflowRevision, options("participant"), ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
      options("idempotency-key"), None, Some(TypedValue("StandaloneReviewFixInput", input.asJson)))
    payload <- ProfileSelectionJsonV1.encodeStorageC(selection)
  } yield Record.data("datastore" -> Vector(Record.data("collection" -> options("selection-collection"), "records" ->
    Vector(Record.data("id" -> options("selection-id"), ProfileSelectionJsonV1.storageField -> payload)))))
}
