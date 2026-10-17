package org.simplemodeling.textus.smworkflow.host

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.NativeHostEvidence
import org.simplemodeling.textus.smworkflow.value as V

/** Finite conversion of explicit closing Wait and operator metadata to standard
  * StartupImport input. Encoding authenticates no origin and grants no effect.
  * @since   Oct. 14, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeHostAuthorityInput {
  private val _required = Set("request", "reference", "participant", "issuer", "occurred-at", "collection")

  def prepareC(supplied: Map[String, String], readText: String => Consequence[String]): Consequence[Record] = {
    try {
      for {
        options <- NativeHostPreparationInput.optionsC(supplied, _required, readText)
        occurred <- NativeHostPreparationInput.instantC(options("occurred-at"))
        _ <- NativeHostPreparationInput.collectionC(options("collection"))
        request <- NativeHostPreparationInput.readC(options("request"), readText)
        output <- summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(request)
        _ <- if (output.kind == "Wait" && output.authorityContext != null && output.authorityContext.size == 1) {
          Consequence.unit
        } else { _invalid("request must be a Wait with exactly one closing authority context") }
        referenceRecord <- NativeHostPreparationInput.readC(options("reference"), readText)
        reference <- summon[ValueReader[V.WorkflowArtifactReference]].readC(referenceRecord)
        prepared <- NativeHostAuthorityPreparation.goalC(output.authorityContext.get, reference,
          options("participant"), options("issuer"), occurred)
        payload <- prepared.record.getString(NativeHostEvidence.storageField).map(Consequence.success)
          .getOrElse(_invalid("prepared row has no native payload"))
        row = Record.data("id" -> prepared.recordIdentity, NativeHostEvidence.storageField -> payload)
        bootstrap = Record.data("datastore" -> Vector(Record.data("collection" -> options("collection"), "records" -> Vector(row))))
      } yield bootstrap
    } catch { case NonFatal(_) => _invalid("invalid explicit argument, file or typed input") }
  }

  private def _invalid[A](reason: String): Consequence[A] = { Consequence.stateConflict("Host authority input " + reason) }
}
