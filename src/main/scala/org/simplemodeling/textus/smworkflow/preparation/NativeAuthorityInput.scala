package org.simplemodeling.textus.smworkflow.preparation

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.NativeExternalEvidence
import org.simplemodeling.textus.smworkflow.value as V

/** Finite conversion of explicit closing Wait and operator metadata to standard
  * StartupImport input. Encoding authenticates no origin and grants no effect.
  * @since   Oct. 14, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeAuthorityInput {
  private val _required = Set("request", "reference", "participant", "issuer", "occurred-at", "collection")

  def prepareC(supplied: Map[String, String], readText: String => Consequence[String]): Consequence[Record] = {
    try {
      for {
        options <- NativePreparationInput.optionsC(supplied, _required, readText)
        occurred <- NativePreparationInput.instantC(options("occurred-at"))
        _ <- NativePreparationInput.collectionC(options("collection"))
        request <- NativePreparationInput.readC(options("request"), readText)
        context <- summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(request).toOption match {
          case Some(output) if output.kind == "Wait" && output.authorityContext != null && output.authorityContext.size == 1 =>
            Consequence.success(output.authorityContext.get)
          case _ => summon[ValueReader[V.WorkflowAuthorityContext]].readC(request).flatMap { value =>
            if (value.purpose == org.simplemodeling.textus.smworkflow.admission.GoalPhaseInventoryAuthority.purpose)
              Consequence.success(value)
            else _invalid("request must be a closing Wait or an explicit original-purpose inventory authority context")
          }
        }
        referenceRecord <- NativePreparationInput.readC(options("reference"), readText)
        reference <- summon[ValueReader[V.WorkflowArtifactReference]].readC(referenceRecord)
        prepared <- NativeAuthorityPreparation.goalC(context, reference,
          options("participant"), options("issuer"), occurred)
        payload <- prepared.record.getString(NativeExternalEvidence.storageField).map(Consequence.success)
          .getOrElse(_invalid("prepared row has no native payload"))
        row = Record.data("id" -> prepared.recordIdentity, NativeExternalEvidence.storageField -> payload)
        bootstrap = Record.data("datastore" -> Vector(Record.data("collection" -> options("collection"), "records" -> Vector(row))))
      } yield bootstrap
    } catch { case NonFatal(_) => _invalid("invalid explicit argument, file or typed input") }
  }

  private def _invalid[A](reason: String): Consequence[A] = { Consequence.stateConflict("Authority input " + reason) }
}
