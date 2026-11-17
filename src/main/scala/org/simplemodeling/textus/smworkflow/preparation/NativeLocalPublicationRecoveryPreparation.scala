package org.simplemodeling.textus.smworkflow.preparation

import java.time.Instant
import io.circe.Json
import io.circe.generic.auto.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseNativeMetadata, LocalPublicationRecovery as R, NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive conversion of a separately attributable Human decision to host input.
  * @since   Nov. 17, 2026
  * @version Nov. 18, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object NativeLocalPublicationRecoveryPreparation {
  def prepareC(description: Json, result: Json, reference: V.WorkflowArtifactReference,
    execution: V.WorkflowExecutionEvidence, participant: String, issuer: String, occurred: Instant): Consequence[(String, Record)] = for {
    normalizeddescription <- GoalPhaseNativeMetadata.normalizePublicJson(description).fold(Consequence.stateConflict(_), Consequence.success)
    normalizedresult <- GoalPhaseNativeMetadata.normalizePublicJson(result).fold(Consequence.stateConflict(_), Consequence.success)
    target <- normalizeddescription.hcursor.get[R.Target]("publication").fold(e => Consequence.stateConflict(e.message), Consequence.success)
    answer <- normalizedresult.as[R.Answer].fold(e => Consequence.stateConflict(e.message), Consequence.success)
    _ <- if (normalizeddescription.hcursor.get[String]("status").contains("direction-required") && R.validAnswer(answer) &&
      answer.target == target && reference != null && answer.reference == ContextReference(reference.identity, reference.revision) &&
      answer.participant == participant && execution != null && execution.workerIdentity != null &&
      execution.workerIdentity.contains(participant) && execution.references != null && execution.references.contains(reference) &&
      issuer != null && issuer.nonEmpty && occurred != null) Consequence.unit
      else Consequence.stateConflict("Standalone recovery preparation requires the described target and actual Human evidence")
    purpose = NativeExternalEvidence.Purpose.StandalonePublicationRecovery
    identity = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
    payload <- NativeExternalEvidence.encodeStorageC(NativeExternalEvidence.Evidence(NativeExternalEvidence.schema,
      purpose.identity, identity, reference.identity, reference.revision, issuer, participant, R.binding(answer), occurred.toString))
  } yield NativeExternalEvidence.recordIdentity(purpose, identity) -> Record.data(NativeExternalEvidence.storageField -> payload)
}
