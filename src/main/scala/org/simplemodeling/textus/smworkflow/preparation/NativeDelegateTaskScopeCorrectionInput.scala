package org.simplemodeling.textus.smworkflow.preparation

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.textus.smworkflow.admission.*
import org.simplemodeling.textus.smworkflow.value as V

/** Passive input preparation; only current-call exact selection grants authority.
  * @since Oct. 30, 2026
  * @version Nov. 5, 2026
  */
private[smworkflow] object NativeDelegateTaskScopeCorrectionInput {
  def prepareC(supplied: Map[String, String], read: String => Consequence[String]): Consequence[Record] = for {
    options <- NativePreparationInput.optionsC(supplied,
      Set("input", "selection-id", "participant", "origin", "idempotency-key", "selection-collection"), read)
    _ <- if (options("origin") == "ExplicitHuman") Consequence.unit
      else Consequence.stateConflict("Scope correction requires ExplicitHuman selection")
    _ <- NativePreparationInput.collectionC(options("selection-collection"))
    text <- read(options("input"))
    record <- new RecordDecoder().json(text)
    input <- summon[ValueReader[V.DelegateTaskScopeCorrectionInput]].readC(record)
    _ <- if (input.selectionId == options("selection-id") && input.participant == options("participant") &&
        input.idempotencyKey == options("idempotency-key")) Consequence.unit
      else Consequence.stateConflict("Scope preparation metadata differs from the selected input")
    normalized <- DelegateTaskScopeCorrection.selectionInputC(input)
    definition <- DelegateTaskRuntimeCatalog.loadC()
    selection = ProfileStartAdmission.Selection(options("selection-id"), DelegateTaskScopeCorrection.operation,
      definition.workflowIdentity, definition.workflowRevision, options("participant"), ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
      options("idempotency-key"), None, Some(TypedValue(DelegateTaskScopeCorrection.inputType, normalized)))
    payload <- ProfileSelectionJsonV1.encodeStorageC(selection)
  } yield Record.data("datastore" -> Vector(Record.data("collection" -> options("selection-collection"), "records" ->
    Vector(Record.data("id" -> options("selection-id"), ProfileSelectionJsonV1.storageField -> payload)))))
  def prepareCandidateC(supplied: Map[String, String], read: String => Consequence[String]): Consequence[Record] = for {
    options <- NativePreparationInput.optionsC(supplied,
      Set("input", "selection-id", "participant", "origin", "idempotency-key", "selection-collection"), read)
    _ <- if (options("origin") == "ExplicitHuman") Consequence.unit
      else Consequence.stateConflict("Scope correction requires ExplicitHuman selection")
    _ <- NativePreparationInput.collectionC(options("selection-collection"))
    text <- read(options("input"))
    record <- new RecordDecoder().json(text)
    input <- summon[ValueReader[V.DelegateTaskCandidateContinuationInput]].readC(record)
    _ <- if (input.selectionId == options("selection-id") && input.participant == options("participant") &&
        input.idempotencyKey == options("idempotency-key")) Consequence.unit
      else Consequence.stateConflict("Scope preparation metadata differs from the selected input")
    normalized <- DelegateTaskCandidateContinuation.selectionInputC(input)
    definition <- DelegateTaskRuntimeCatalog.loadC()
    selection = ProfileStartAdmission.Selection(options("selection-id"), DelegateTaskCandidateContinuation.operation,
      definition.workflowIdentity, definition.workflowRevision, options("participant"), ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
      options("idempotency-key"), None, Some(TypedValue(DelegateTaskCandidateContinuation.inputType, normalized)))
    payload <- ProfileSelectionJsonV1.encodeStorageC(selection)
  } yield Record.data("datastore" -> Vector(Record.data("collection" -> options("selection-collection"), "records" ->
    Vector(Record.data("id" -> options("selection-id"), ProfileSelectionJsonV1.storageField -> payload)))))
}
