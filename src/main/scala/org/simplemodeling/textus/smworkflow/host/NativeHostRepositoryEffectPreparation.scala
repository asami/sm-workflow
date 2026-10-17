package org.simplemodeling.textus.smworkflow.host

import java.nio.file.Path
import java.time.Instant
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{NativeHostEvidence, NativeHostRepositoryEffectScope,
  NativeWorkflowStartInput, ProfileSelectionJsonV1, RepositorySyncManagedFetch, RepositorySyncNativeConvergence,
  ThreeWorkflowHostEvidencePorts}
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms
import org.simplemodeling.textus.smworkflow.value as V

/** Passive preparation for one explicitly selected repository effect.
  * Genuine consent, trusted provisioning and current native admission are separate.
  * @since   Oct. 14, 2026
  * @version Oct. 14, 2026
  */
object NativeHostRepositoryEffectPreparation {
  final case class Target(root: String, localBranch: String, remote: String, remoteBranch: String, endpoint: String)
  final case class Prepared(recordIdentity: String, record: Record)

  def repositoryC(componentInstance: String, selectionRow: Record, target: Target, effect: String,
    reference: V.WorkflowArtifactReference, participant: String, issuer: String, occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (_exact(componentInstance) && selectionRow != null && target != null &&
        Vector(target.root, target.localBranch, target.remote, target.remoteBranch, target.endpoint).forall(_exact) &&
        reference != null && _exact(reference.identity) && _exact(reference.revision) &&
        _exact(participant) && _exact(issuer) && occurredAt != null) { Consequence.unit }
      else { _invalid("requires explicit owner, selection, physical target, reference, operator, issuer and time") }
    root <- _root_c(target.root)
    nativeEffect <- RepositorySyncNativeConvergence.Effect.values.find(_.toString == effect)
      .map(Consequence.success).getOrElse(_invalid("effect must be CommitPending, Push or FinalFetch"))
    payload <- selectionRow.getAny(ProfileSelectionJsonV1.storageField).collect { case value: String => value }
      .map(Consequence.success).getOrElse(_invalid("requires String selectionPayload"))
    selection <- ProfileSelectionJsonV1.decodeStorageC(payload)
    selected <- if (selection.startOperation == NativeWorkflowStartInput.Kind.RepositorySync.operation &&
        selection.selectedInput.exists(_.typeIdentity == NativeWorkflowStartInput.Kind.RepositorySync.inputType)) {
      Consequence.success(selection.selectedInput.get.value)
    } else { _invalid("requires selected RepositorySync operation and RepositorySyncStartInput") }
    decoded <- NativeWorkflowStartInput.decodeC(NativeWorkflowStartInput.Kind.RepositorySync, selected)
    input <- decoded match {
      case value: NativeWorkflowStartInput.Repository => Consequence.success(value)
      case _ => _invalid("requires repository business input")
    }
    context = RepositorySyncManagedFetch.StartContext(selection.selectionId, selection.idempotencyKey,
      selection.participantIdentity, input.repositoryIdentity, input.localBranch, input.trackingRemote,
      input.trackingBranch, input.remoteEndpointIdentity, input.syncMode)
    binding <- NativeHostRepositoryEffectScope.bindingFromInputsC(componentInstance, context,
      RepositoryGitRuntimePrograms.Target(root, target.localBranch, target.remote, target.remoteBranch, target.endpoint), nativeEffect)
    identity = ThreeWorkflowHostEvidencePorts.key(selection.selectionId, selection.idempotencyKey, nativeEffect.toString)
    purpose = NativeHostEvidence.Purpose.RepositoryEffect
    evidence = NativeHostEvidence.Evidence(NativeHostEvidence.schema, purpose.identity, identity,
      reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString)
    encoded <- NativeHostEvidence.encodeStorageC(evidence)
  } yield Prepared(NativeHostEvidence.recordIdentity(purpose, identity), Record.data(NativeHostEvidence.storageField -> encoded))

  private def _root_c(value: String): Consequence[Path] = {
    try {
      val path = Path.of(value)
      if (path.isAbsolute && path.normalize == path) { Consequence.success(path) }
      else { _invalid("root must be absolute and already normalized") }
    } catch { case NonFatal(_) => _invalid("root is not a valid path") }
  }
  private def _exact(value: String): Boolean = {
    value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  }
  private def _invalid[A](reason: String): Consequence[A] = { Consequence.stateConflict("Host repository effect preparation " + reason) }
}
