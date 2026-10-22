package org.simplemodeling.textus.smworkflow.preparation

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.NativeExternalEvidence
import org.simplemodeling.textus.smworkflow.value as V

/** Finite conversion of actual selected Start inputs and explicit metadata.
  * The emitted importer row authenticates no origin and grants no consent.
  * @since   Oct. 14, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeSplitStartInput {
  private val _required = Set("component-instance", "allocation-reference", "allocation", "source", "reference",
    "participant", "issuer", "occurred-at", "collection")

  def prepareC(supplied: Map[String, String], readText: String => Consequence[String]): Consequence[Record] = {
    try {
      for {
        options <- NativePreparationInput.optionsC(supplied, _required, readText)
        occurred <- NativePreparationInput.instantC(options("occurred-at"))
        _ <- NativePreparationInput.collectionC(options("collection"))
        allocationReferenceRecord <- NativePreparationInput.readC(options("allocation-reference"), readText)
        allocationReference <- summon[ValueReader[V.WorkflowArtifactReference]].readC(allocationReferenceRecord)
        allocation <- NativePreparationInput.readC(options("allocation"), readText)
        source <- NativePreparationInput.readC(options("source"), readText)
        referenceRecord <- NativePreparationInput.readC(options("reference"), readText)
        reference <- summon[ValueReader[V.WorkflowArtifactReference]].readC(referenceRecord)
        prepared <- NativeStartPreparation.splitC(options("component-instance"), allocationReference,
          allocation, source, reference, options("participant"), options("issuer"), occurred)
        payload <- prepared.record.getString(NativeExternalEvidence.storageField).map(Consequence.success)
          .getOrElse(_invalid("prepared row has no native payload"))
        row = Record.data("id" -> prepared.recordIdentity, NativeExternalEvidence.storageField -> payload)
        bootstrap = Record.data("datastore" -> Vector(Record.data("collection" -> options("collection"), "records" -> Vector(row))))
      } yield bootstrap
    } catch { case NonFatal(_) => _invalid("invalid explicit argument, file or typed input") }
  }

  private def _invalid[A](reason: String): Consequence[A] = { Consequence.stateConflict("Start preparation input " + reason) }
}
