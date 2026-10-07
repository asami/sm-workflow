package org.simplemodeling.textus.smworkflow.admission

import java.security.MessageDigest
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.blob.{BlobPayloadSupport, BlobReadResult, BlobRepository, BlobStorageRef, BlobStoreSupport}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.ComponentDataStore
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Bytes from host-selected managed references, not Blob metadata admission.
  * The host must resolve the current versioned pair; an immutable Factory-time
  * pair would not represent repaired artifacts in a long-lived application.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseBlobArtifactSnapshot {
  final case class Reference(id: EntityId, storageRef: BlobStorageRef, byteSize: Long, digest: String)
  final case class References(selectionIdentity: String, selectionRevision: Long,
    program: Reference, planning: Reference)
  trait ReferencePort {
    /** Actual storage lease verification, never merely a datastore binding.
      * The host reader owns this capability; no permissive default is supplied.
      */
    def verifyForCallC(scope: GoalPhaseCallScopedSnapshot.Scope, core: ActionCall.Core): Consequence[Unit]
    def resolveForCallC(scope: GoalPhaseCallScopedSnapshot.Scope, core: ActionCall.Core): Consequence[References]
    /** Re-admit the exact selected pair and its scope after reading bytes.
      * Lease liveness alone cannot establish that reference selection survived.
      * No permissive default or implicit second selection is supplied.
      */
    def verifySelectionForCallC(scope: GoalPhaseCallScopedSnapshot.Scope, selected: References,
      core: ActionCall.Core): Consequence[Unit]
  }

  def bindC(storeName: String, references: ReferencePort, maximumArtifactBytes: Int):
      Consequence[GoalPhaseCallScopedSnapshot.ScopedPort] =
    if (!_text(storeName) || references == null || maximumArtifactBytes <= 0 ||
        maximumArtifactBytes == Int.MaxValue ||
        ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, storeName).normalizedName != storeName)
      Consequence.configurationInvalid("GoalPhase Blob snapshot requires its exact host binding and byte limit")
    else Consequence.success(new Bound(storeName, references, maximumArtifactBytes))

  private def _text(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
  private def _valid(value: Reference, limit: Int): Boolean = value != null && value.id != null &&
    value.id.collection == BlobRepository.CollectionId && value.storageRef != null &&
    _text(value.storageRef.store) && _text(value.storageRef.container) &&
    value.storageRef.key != null && BlobStoreSupport.validKey(value.storageRef.key) &&
    value.byteSize >= 0 && value.byteSize <= limit && value.digest != null && value.digest.matches("[0-9a-f]{64}")

  private[admission] def validateReferencesC(pair: References, limit: Int): Consequence[Unit] =
    if (limit > 0 && limit < Int.MaxValue && pair != null && _text(pair.selectionIdentity) &&
        pair.selectionRevision >= 0 && _valid(pair.program, limit) && _valid(pair.planning, limit))
      Consequence.unit
    else Consequence.stateConflict("GoalPhase host Blob selection is incomplete, invalid or unbounded")

  private final class Bound(storeName: String, references: ReferencePort, limit: Int)
      extends GoalPhaseCallScopedSnapshot.ScopedPort {
    def loadForScopeCallC(scope: GoalPhaseCallScopedSnapshot.Scope, core: ActionCall.Core):
        Consequence[GoalPhaseStepClosureBoundary.ArtifactSnapshot] =
      if (scope == null || core == null || core.executionContext == null ||
          !core.component.exists(value => value.componentId == SmWorkflowComponent.componentId && value.subsystem.nonEmpty))
        Consequence.stateConflict("GoalPhase Blob snapshot requires its owning current call")
      else new ReadCall(core, scope).readC()

    private final class ReadCall(val core: ActionCall.Core, scope: GoalPhaseCallScopedSnapshot.Scope) extends ProcedureActionCall {
      def readC(): Consequence[GoalPhaseStepClosureBoundary.ArtifactSnapshot] = {
        given ExecutionContext = executionContext
        val space = executionContext.dataStoreSpace
        val prior = space.captureBinding()
        try for {
          _ <- references.verifyForCallC(scope, core)
          _ <- bind_component_application_datastore_c(storeName)
          pair <- references.resolveForCallC(scope, core)
          _ <- validateReferencesC(pair, limit)
          service <- BlobPayloadSupport.service(core.component.get)
          program <- service.blobStore.get(pair.program.storageRef).flatMap(verifyReadC(pair.program, _, limit))
          planning <- service.blobStore.get(pair.planning.storageRef).flatMap(verifyReadC(pair.planning, _, limit))
          _ <- references.verifyForCallC(scope, core)
          _ <- references.verifySelectionForCallC(scope, pair, core)
        } yield GoalPhaseStepClosureBoundary.ArtifactSnapshot(program, planning)
        finally space.restoreBinding(prior)
      }
      def execute(): Consequence[OperationResponse] = Consequence.stateConflict("Blob snapshot is not a transport Operation")
    }
  }

  private[admission] def verifyReadC(expected: Reference, actual: BlobReadResult, limit: Int): Consequence[Vector[Byte]] =
    if (limit <= 0 || limit == Int.MaxValue || !_valid(expected, limit) || actual == null || actual.payload == null ||
        actual.id != expected.id || actual.storageRef != expected.storageRef || actual.byteSize != expected.byteSize ||
        actual.digest != expected.digest)
      Consequence.stateConflict("GoalPhase Blob result differs from its host reference")
    else try {
      val stream = actual.payload.openInputStream()
      val bytes = try stream.readNBytes(expected.byteSize.toInt + 1) finally stream.close()
      val digest = MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString
      if (bytes.length.toLong == expected.byteSize && digest == expected.digest) Consequence.success(bytes.toVector)
      else Consequence.stateConflict("GoalPhase actual Blob bytes differ from their host reference")
    } catch {
      case NonFatal(_) => Consequence.stateConflict("GoalPhase bounded Blob bytes are unavailable")
    }
}
