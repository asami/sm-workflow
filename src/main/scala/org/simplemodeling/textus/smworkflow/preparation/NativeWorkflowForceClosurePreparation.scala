/**
 * @since   Oct. 30, 2026
 * @version Oct. 30, 2026
 */
package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.*
import org.simplemodeling.textus.smworkflow.value as V

/** Passive format conversion. Only the independent host can attest an actual
  * Human choice and provision it; this method grants no closing authority.
  */
private[smworkflow] object NativeWorkflowForceClosurePreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(description: V.WorkflowForceClosureView, input: V.WorkflowForceClosureAnswer,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence,
    participant: String, issuer: String, occurred: Instant): Consequence[Prepared] = for {
    described <- WorkflowForceClosure.targetC(description.target)
    direction <- WorkflowForceClosureService.directionC(input)
    _ <- if (description.kind == "Description" && !description.normalCompletion && description.closure.isEmpty &&
        description.sessionId == described.sessionId && description.family == described.family &&
        direction.target == described && input.reference == reference && input.participant == participant &&
        execution.workerIdentity.contains(participant) && execution.references.contains(reference)) Consequence.unit
      else Consequence.valueInvalid("Force closure preparation crosses its actual described target or Human source")
    purpose = NativeExternalEvidence.Purpose.WorkflowForceClosure
    lookup = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity, lookup,
      reference.identity, reference.revision, issuer, participant, WorkflowForceClosure.binding(direction), occurred.toString)
    wire <- NativeExternalEvidence.encodeStorageC(evidence)
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, lookup), Record.data(NativeExternalEvidence.storageField -> wire))

  def prepareSuppliedC(supplied: Map[String, String], readText: String => Consequence[String]): Consequence[Record] = for {
    options <- NativePreparationInput.optionsC(supplied, Set("family", "kind", "request", "result", "reference",
      "execution-evidence", "issuer", "occurred-at", "collection", "participant"), readText)
    _ <- if (WorkflowForceClosure.families(options("family")) && options("kind") == "force-closure") Consequence.unit
      else Consequence.valueInvalid("Unsupported force closure family/kind")
    occurred <- NativePreparationInput.instantC(options("occurred-at"))
    _ <- NativePreparationInput.collectionC(options("collection"))
    request <- NativePreparationInput.readC(options("request"), readText)
    description <- summon[ValueReader[V.WorkflowForceClosureView]].readC(request)
    _ <- if (description.family == options("family")) Consequence.unit
      else Consequence.valueInvalid("Force closure preparation family differs")
    result <- NativePreparationInput.readC(options("result"), readText)
    answer <- summon[ValueReader[V.WorkflowForceClosureAnswer]].readC(result)
    ref <- NativePreparationInput.readC(options("reference"), readText).flatMap(summon[ValueReader[V.WorkflowArtifactReference]].readC)
    execution <- NativePreparationInput.readC(options("execution-evidence"), readText).flatMap(summon[ValueReader[V.WorkflowExecutionEvidence]].readC)
    prepared <- prepareC(description, answer, ref, execution, options("participant"), options("issuer"), occurred)
    wire <- prepared.record.getString(NativeExternalEvidence.storageField).map(Consequence.success)
      .getOrElse(Consequence.valueInvalid("Prepared force closure direction has no native payload"))
  } yield Record.data("datastore" -> Vector(Record.data("collection" -> options("collection"), "records" -> Vector(
    Record.data("id" -> prepared.recordIdentity, NativeExternalEvidence.storageField -> wire)))))
}
