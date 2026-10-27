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

/** Passive projection into the existing host-evidence admission owner. It
  * neither authenticates caller facts nor selects/activates an artifact.
  */
private[smworkflow] object NativeDelegateTaskAdoptionPreparation {
  final case class Prepared(recordIdentity: String, record: Record)
  def prepareC(context: V.DelegateTaskAdoptionContext, value: V.DelegateTaskObservationInput,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence,
    issuer: String, occurred: Instant): Consequence[Prepared] = for {
    _ <- if (context != null && value != null && reference != null && execution != null && occurred != null && issuer != null)
      Consequence.unit else Consequence.valueInvalid("Adoption preparation requires actual typed context and source evidence")
    raw <- context.target.toJsonStringC.flatMap(s => io.circe.parser.parse(s).fold(e => Consequence.valueInvalid(e.message), Consequence.success))
    normalized <- GoalPhaseNativeMetadata.normalizePublicJson(raw).fold(Consequence.valueInvalid(_), Consequence.success)
    target <- normalized.as[DelegateTask.AdoptionTarget].fold(e => Consequence.valueInvalid(e.message), Consequence.success)
    artifact <- value.artifactSelection.map(r => r.toJsonStringC.flatMap(s => io.circe.parser.parse(s)
      .fold(e => Consequence.valueInvalid(e.message), Consequence.success))).getOrElse(Consequence.valueInvalid("Adoption requires the actual artifact selection"))
    observation = DelegateTask.Observation(value.event, org.goldenport.cncf.workflow.ContextReference(value.sourceReference.identity, value.sourceReference.revision),
      value.destinationSessionId, None, Some(artifact), value.rationale)
    _ <- if (value.event == "adopt" && value.result.isEmpty && value.sessionId == target.sessionId &&
      context.componentInstance == target.componentInstance && context.sessionId == target.sessionId && context.sourceRevision == target.sourceRevision &&
      reference == value.sourceReference && execution.workerIdentity.contains(target.participant) && execution.references.exists(r => r == reference))
      Consequence.unit else Consequence.valueInvalid("Adoption preparation crosses the described owner, frontier or actual observation reference")
    binding = DelegateTask.adoptionBinding(target, observation)
    _ <- DelegateTask.selectAdoptionBinding(binding).fold(Consequence.valueInvalid(_), _ => Consequence.unit)
    purpose = NativeExternalEvidence.Purpose.DelegateAdoption
    lookup = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity, lookup,
      reference.identity, reference.revision, issuer, execution.workerIdentity.get, binding, occurred.toString)
    wire <- NativeExternalEvidence.encodeStorageC(evidence)
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, lookup), Record.data(NativeExternalEvidence.storageField -> wire))
}
