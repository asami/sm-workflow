/**
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.*
import org.simplemodeling.textus.smworkflow.value as V

/** Passive projection only. The configured host evidence owner must independently
  * admit actual covering authority for this exact native/business target.
  */
private[smworkflow] object NativeCompletionRecordClosurePreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(description: V.CompletionRecordClosureView, input: V.CompletionRecordClosureInput,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence, participant: String,
    issuer: String, occurred: Instant): Consequence[Prepared] = for {
    described <- CompletionRecordContinuation.publicTargetC(description.target)
    selected <- input.target.map(CompletionRecordContinuation.publicTargetC)
      .getOrElse(Consequence.valueInvalid("The exact described terminal target is required"))
    _ <- if (description.kind == "Description" && input.sessionId == description.sessionId && input.family == description.scope &&
      input.terminalRevision.contains(description.terminalRevision) && input.authorityReference.contains(reference) && selected == described &&
      described.hcursor.downField("source").get[String]("participant").contains(participant) &&
      execution.workerIdentity.contains(participant) && execution.references.contains(reference)) Consequence.unit
      else Consequence.valueInvalid("Preparation crosses the exact owner, terminal or attributable authority source")
    binding = CompletionRecordContinuation.binding(described, participant, reference)
    _ <- CompletionRecordContinuation.selectBinding(binding).fold(Consequence.valueInvalid(_), _ => Consequence.unit)
    purpose = NativeExternalEvidence.Purpose.CompletionRecordClosure
    lookup = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity, lookup,
      reference.identity, reference.revision, issuer, participant, binding, occurred.toString)
    wire <- NativeExternalEvidence.encodeStorageC(evidence)
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, lookup), Record.data(NativeExternalEvidence.storageField -> wire))
}
