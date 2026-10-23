package org.simplemodeling.textus.smworkflow.preparation

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.NativeExternalEvidence
import org.simplemodeling.textus.smworkflow.value as V

/** Finite explicit three-file intake for one passive RepositoryEffect row.
  * No storage write, Workflow call, Git process or provenance certification.
  * @since   Oct. 14, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeRepositoryEffectInput {
  private val _required = Set("component-instance", "selection", "target", "effect", "reference", "participant",
    "issuer", "occurred-at", "collection")

  def prepareC(supplied: Map[String, String], readText: String => Consequence[String]): Consequence[Record] = {
    try {
      for {
        options <- NativePreparationInput.optionsC(supplied, _required, readText)
        occurred <- NativePreparationInput.instantC(options("occurred-at"))
        _ <- NativePreparationInput.collectionC(options("collection"))
        selection <- NativePreparationInput.readC(options("selection"), readText)
        targetRecord <- NativePreparationInput.readC(options("target"), readText)
        root <- _string_c(targetRecord, "root")
        local <- _string_c(targetRecord, "localBranch")
        remote <- _string_c(targetRecord, "remote")
        branch <- _string_c(targetRecord, "remoteBranch")
        endpoint <- _string_c(targetRecord, "endpoint")
        referenceRecord <- NativePreparationInput.readC(options("reference"), readText)
        reference <- summon[ValueReader[V.WorkflowArtifactReference]].readC(referenceRecord)
        prepared <- NativeRepositoryEffectPreparation.repositoryC(options("component-instance"), selection,
          NativeRepositoryEffectPreparation.Target(root, local, remote, branch, endpoint), options("effect"),
          reference, options("participant"), options("issuer"), occurred)
        payload <- prepared.record.getString(NativeExternalEvidence.storageField).map(Consequence.success)
          .getOrElse(_invalid("prepared row has no native payload"))
        row = Record.data("id" -> prepared.recordIdentity, NativeExternalEvidence.storageField -> payload)
        bootstrap = Record.data("datastore" -> Vector(Record.data("collection" -> options("collection"), "records" -> Vector(row))))
      } yield bootstrap
    } catch { case NonFatal(_) => _invalid("invalid explicit argument, file or typed input") }
  }

  private def _string_c(row: Record, field: String): Consequence[String] = {
    row.getAny(field).collect { case value: String => value }.map(Consequence.success)
      .getOrElse(_invalid("target requires String " + field))
  }
  private def _invalid[A](reason: String): Consequence[A] = { Consequence.stateConflict("Repository effect input " + reason) }
}
