package org.simplemodeling.textus.smworkflow.preparation

import scala.util.control.NonFatal
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.textus.smworkflow.admission.*
import org.simplemodeling.textus.smworkflow.value as V

/** Finite preparation of explicitly selected Repository business input.
  * Supplied origin metadata authenticates no Human and grants no permission.
  * @since   Oct. 14, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeRepositoryStartInput {
  private val _required = Set("input", "selection-id", "participant", "origin", "idempotency-key",
    "selection-collection")

  def prepareC(supplied: Map[String, String], readText: String => Consequence[String]): Consequence[Record] = {
    try {
      for {
        options <- NativePreparationInput.optionsC(supplied, _required, readText)
        _ <- if (options("origin") == ProfileStartAdmission.SelectionOrigin.ExplicitHuman.toString) Consequence.unit
          else _invalid("origin must be ExplicitHuman")
        _ <- NativePreparationInput.collectionC(options("selection-collection"))
        inputText <- readText(options("input"))
        record <- new RecordDecoder().json(inputText)
        input <- summon[ValueReader[V.RepositorySyncStartInput]].readC(record)
        definition <- RepositorySyncDefinitionBinding.bindC
        selection = ProfileStartAdmission.Selection(options("selection-id"), RepositorySyncManagedFetch.startOperation,
          definition.workflowIdentity, definition.workflowRevision, options("participant"),
          ProfileStartAdmission.SelectionOrigin.ExplicitHuman, options("idempotency-key"), None,
          Some(TypedValue(NativeWorkflowStartInput.Kind.RepositorySync.inputType, input.asJson)))
        selectionPayload <- ProfileSelectionJsonV1.encodeStorageC(selection)
        business = NativeWorkflowStartInput.Repository(input.repositoryIdentity, input.localBranch,
          input.trackingRemote, input.trackingBranch, input.remoteEndpointIdentity, input.syncMode)
        row = Record.data("id" -> NativeWorkflowStartBusinessKey(business, options("participant")),
          ProfileSelectionJsonV1.storageField -> selectionPayload)
        bootstrap = Record.data("datastore" -> Vector(
          Record.data("collection" -> options("selection-collection"), "records" -> Vector(row))))
      } yield bootstrap
    } catch { case NonFatal(_) => _invalid("invalid explicit argument, file or typed input") }
  }

  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Repository Start preparation input " + reason)
}
