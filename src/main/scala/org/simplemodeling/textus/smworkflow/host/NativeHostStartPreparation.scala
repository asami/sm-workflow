package org.simplemodeling.textus.smworkflow.host

import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{NativeHostEvidence, NativeHostSplitStartScope,
  SplitPhaseExplicitPlanningInputJsonV1, SplitPhaseTypedStartJsonV1, SplitPhaseTypedStartPreparation,
  ThreeWorkflowHostEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive input for consent to the actual selected Split Start target.
  * The owning host supplies genuine provenance and separately provisions it.
  * @since   Oct. 14, 2026
  * @version Oct. 14, 2026
  */
object NativeHostStartPreparation {
  final case class Prepared(recordIdentity: String, record: Record)

  def splitC(componentInstance: String, allocationReference: V.WorkflowArtifactReference,
    allocationRow: Record, sourceRow: Record, reference: V.WorkflowArtifactReference,
    participant: String, issuer: String, occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (_exact(componentInstance) && _reference(allocationReference) && _reference(reference) &&
        allocationRow != null && sourceRow != null && _exact(participant) && _exact(issuer) && occurredAt != null) {
      Consequence.unit
    } else { _invalid("requires explicit owner, selected allocation, rows, reference, operator, issuer and time") }
    allocationPayload <- _payload_c(allocationRow, SplitPhaseTypedStartJsonV1.storageField)
    allocation <- SplitPhaseTypedStartJsonV1.decodeAllocationC(
      SplitPhaseTypedStartPreparation.Reference(allocationReference.identity, allocationReference.revision), allocationPayload)
    sourcePayload <- _payload_c(sourceRow, SplitPhaseExplicitPlanningInputJsonV1.storageField)
    source <- SplitPhaseExplicitPlanningInputJsonV1.decodeSourceC(allocation.source, sourcePayload)
    binding <- NativeHostSplitStartScope.bindingFromInputsC(componentInstance, allocation.participantIdentity,
      allocation.input, source, allocation.proposal)
    identity = ThreeWorkflowHostEvidencePorts.key(allocationReference.identity, allocationReference.revision)
    purpose = NativeHostEvidence.Purpose.SplitStart
    evidence = NativeHostEvidence.Evidence(NativeHostEvidence.schema, purpose.identity, identity,
      reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString)
    encoded <- NativeHostEvidence.encodeStorageC(evidence)
  } yield Prepared(NativeHostEvidence.recordIdentity(purpose, identity), Record.data(NativeHostEvidence.storageField -> encoded))

  private def _payload_c(row: Record, field: String): Consequence[String] = {
    row.getAny(field).collect { case value: String => value }.map(Consequence.success)
      .getOrElse(_invalid("requires String " + field))
  }
  private def _reference(value: V.WorkflowArtifactReference): Boolean = {
    value != null && _exact(value.identity) && _exact(value.revision)
  }
  private def _exact(value: String): Boolean = {
    value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  }
  private def _invalid[A](reason: String): Consequence[A] = { Consequence.stateConflict("Host Start preparation " + reason) }
}
