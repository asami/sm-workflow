package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{DelegateTaskRequestReconstruction as R,
  NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive importer preparation; only the independent host owns provisioning
  * and the actual Human/transport observation. No requester transition occurs.
  * @since   Nov. 19, 2026
  * @version Nov. 27, 2026
  */
private[smworkflow] object NativeDelegateTaskRequestReconstructionPreparation {
  final case class Prepared(recordIdentity: String, record: Record)

  def prepareC(description: V.DelegateTaskRequestReconstructionDescriptionResult,
    answer: V.DelegateTaskRequestReconstructionInput, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String,
    occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (Set("decision-required", "clarification-available")(description.outcome) &&
      description.choices.contains(answer.choice) &&
      description.issues.isEmpty && reference == answer.reference &&
      participant == answer.participant && NativePreparationInput.exact(issuer) && occurredAt != null)
      Consequence.unit else Consequence.stateConflict("Requester reconstruction preparation requires its actual description and source provenance")
    target <- R.targetC(description.target)
    direction <- R.directionC(answer)
    actor = if (direction.choice == R.clarifyFocusedTest) direction.observation.observerIdentity else Some(participant)
    _ <- if (actor.nonEmpty && execution.workerIdentity == actor) Consequence.unit
      else Consequence.stateConflict("Requester reconstruction evidence crosses its actual observer or Human participant")
    _ <- if (target == direction.target && description.sessionId == target.sessionId &&
      answer.sessionId == target.sessionId && participant == target.participant) Consequence.unit
      else Consequence.stateConflict("Requester reconstruction preparation crosses the described owner, Session or target")
    binding <- R.bindingC(direction)
    purpose = NativeExternalEvidence.Purpose.DelegateTaskRequestReconstruction
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    encoded <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString))
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity),
    Record.data(NativeExternalEvidence.storageField -> encoded))

  def prepareSuppliedC(supplied: Map[String, String], readText: String => Consequence[String]): Consequence[Record] = for {
    options <- NativePreparationInput.optionsC(supplied, Set("family", "kind", "request", "result", "reference",
      "execution-evidence", "issuer", "occurred-at", "collection"), readText, Set("participant"))
    _ <- if (options("family") == "DelegateTask" && options("kind") == "request-reconstruction") Consequence.unit
      else Consequence.stateConflict("Requester reconstruction preparation requires its exact family and kind")
    participant <- options.get("participant").map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Requester reconstruction requires the actual participant"))
    occurred <- NativePreparationInput.instantC(options("occurred-at"))
    _ <- NativePreparationInput.collectionC(options("collection"))
    description <- NativePreparationInput.readC(options("request"), readText)
      .flatMap(summon[ValueReader[V.DelegateTaskRequestReconstructionDescriptionResult]].readC)
    answer <- NativePreparationInput.readC(options("result"), readText)
      .flatMap(summon[ValueReader[V.DelegateTaskRequestReconstructionInput]].readC)
    reference <- NativePreparationInput.readC(options("reference"), readText)
      .flatMap(summon[ValueReader[V.WorkflowArtifactReference]].readC)
    execution <- NativePreparationInput.readC(options("execution-evidence"), readText)
      .flatMap(summon[ValueReader[V.WorkflowExecutionEvidence]].readC)
    prepared <- prepareC(description, answer, reference, execution, participant, options("issuer"), occurred)
    payload <- prepared.record.getString(NativeExternalEvidence.storageField).map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Prepared requester reconstruction has no native payload"))
    row = Record.data("id" -> prepared.recordIdentity, NativeExternalEvidence.storageField -> payload)
  } yield Record.data("datastore" -> Vector(Record.data("collection" -> options("collection"), "records" -> Vector(row))))
}
