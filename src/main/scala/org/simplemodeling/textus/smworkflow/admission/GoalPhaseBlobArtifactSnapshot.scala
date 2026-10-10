package org.simplemodeling.textus.smworkflow.admission

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.blob.{BlobPayloadSupport, BlobReadResult, BlobRepository, BlobStorageRef, BlobStoreSupport}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.ComponentDataStore
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.model.datatype.EntityId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Bytes from selected managed references, not Blob metadata certification.
  * Resolve the current pair once through its owning call; read actual bounded
  * payloads without a digest or post-read unchanged-selection gate.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseBlobArtifactSnapshot {
  final case class Reference(id: EntityId, storageRef: BlobStorageRef)
  final case class References(selectionIdentity: String, selectionRevision: Long,
    program: Reference, planning: Reference)
  trait ReferencePort {
    /** Resolve selected references through the current call's actual storage
      * permission. Selection is input, not an unchanged-content certificate.
      */
    def resolveForCallC(scope: GoalPhaseCallScopedSnapshot.Scope, core: ActionCall.Core): Consequence[References]
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
    value.storageRef.version != null && value.storageRef.etag != null &&
    value.storageRef.version.forall(_text) && value.storageRef.etag.forall(_text)

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
          _ <- bind_component_application_datastore_c(storeName)
          pair <- references.resolveForCallC(scope, core)
          _ <- validateReferencesC(pair, limit)
          service <- BlobPayloadSupport.service(core.component.get)
          program <- service.blobStore.get(pair.program.storageRef).flatMap(verifyReadC(pair.program, _, limit))
          planning <- service.blobStore.get(pair.planning.storageRef).flatMap(verifyReadC(pair.planning, _, limit))
        } yield GoalPhaseStepClosureBoundary.ArtifactSnapshot(program, planning)
        finally space.restoreBinding(prior)
      }
      def execute(): Consequence[OperationResponse] = Consequence.stateConflict("Blob snapshot is not a transport Operation")
    }
  }

  private[admission] def verifyReadC(expected: Reference, actual: BlobReadResult, limit: Int): Consequence[Vector[Byte]] =
    if (limit <= 0 || limit == Int.MaxValue || !_valid(expected, limit) || actual == null || actual.payload == null ||
        actual.id != expected.id || actual.storageRef != expected.storageRef)
      Consequence.stateConflict("GoalPhase Blob result differs from its host reference")
    else try {
      val stream = actual.payload.openInputStream()
      val bytes = try stream.readNBytes(limit + 1) finally stream.close()
      if (bytes.length <= limit) Consequence.success(bytes.toVector)
      else Consequence.stateConflict("GoalPhase actual Blob bytes exceed the configured read limit")
    } catch {
      case NonFatal(_) => Consequence.stateConflict("GoalPhase bounded Blob bytes are unavailable")
    }
}
