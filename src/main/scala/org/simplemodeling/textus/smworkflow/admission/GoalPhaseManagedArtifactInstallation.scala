package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.blob.BlobPayloadSupport
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseBlobArtifactSnapshot.{References, verifyReadC}
import GoalPhaseCallScopedSnapshot.{Scope, Purpose}

/** Initial candidate registration, not startup, refresh or a transition.
  * Uses the same opaque Create-only capability as other admitted Component
  * records. Stored submission claims are checked against retained Start and
  * actual ingress provenance; reference claims are checked against Blob bytes.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseManagedArtifactInstallation {
  def installForCallC(scope: Scope, selected: References, core: ActionCall.Core,
    declaration: GoalPhaseManagedStartAssembly.Declaration,
    selection: ProfileStartAdmission.CallScopedSelectionPort,
    associationCollection: DataStore.CollectionId, artifactCollection: DataStore.CollectionId,
    maximumBytes: Int): Consequence[References] =
    if (scope == null || core == null || core.executionContext == null || declaration == null ||
        declaration.configuration == null || declaration.instances == null || declaration.continuations == null ||
        selection == null || associationCollection == null || artifactCollection == null ||
        !core.component.exists(value => value.componentId == SmWorkflowComponent.componentId && value.subsystem.nonEmpty))
      Consequence.stateConflict("Artifact installation requires its complete owning current call")
    else for {
      key <- GoalPhaseArtifactReferenceRecord.keyC(scope)
      _ <- GoalPhaseArtifactReferenceRecord.encodeStorageC(scope, selected, maximumBytes).map(_ => ())
      _ <- if (scope.purpose == Purpose.Candidate && selected.program.digest == scope.request.programDigest &&
          selected.planning.digest == scope.request.planningDigest) Consequence.unit
        else Consequence.stateConflict("Initial artifact registration requires exact candidate content, not a repair successor")
      _ <- declaration.configuration.validateC
      _ <- if (associationCollection != artifactCollection &&
          !Vector(declaration.instances, declaration.continuations).exists(value =>
            value == artifactCollection || value == associationCollection)) Consequence.unit
        else Consequence.stateConflict("Artifact registration aliases Workflow or submission infrastructure")
      storeName = declaration.configuration.workflowStore.name
      _ <- GoalPhaseArtifactReadPolicy.admitC(core.component.get.subsystem.get.configuration, storeName, artifactCollection)
      storage <- ComponentRecordStorage.bindForCallC(core, storeName, artifactCollection, DataStore.StringEntryId(key))
      storedRegistration <- ManagedHostRecordRead.loadC(core, storeName, associationCollection,
        scope.request.submissionIdentity, GoalPhaseHostRecordReadPolicy.Purpose.Association)
      raw <- storedRegistration.flatMap(_.getString(GoalPhaseSubmissionRegistration.storageField))
        .map(Consequence.success(_)).getOrElse(Consequence.stateConflict("Artifact submission registration is missing"))
      registration <- GoalPhaseSubmissionRegistration.decodeStorageC(scope.request.submissionIdentity, raw)
      _ <- if (registration.originalStart.handle == scope.handle) Consequence.unit
        else Consequence.stateConflict("Artifact scope differs from its registered Start")
      _ <- GoalPhaseParticipantAdmission.admitC(registration.request.start.participantIdentity, core.executionContext)
      retained <- GoalPhaseManagedStartAssembly.loadRetainedForCallC(registration.request.start, core, declaration, selection)
      _ <- GoalPhaseSubmissionRegistration.matchesRetainedC(registration, registration.request, retained)
      existing <- storage.loadC()
      _ <- existing.fold(Consequence.unit)(record =>
        GoalPhaseArtifactCatalogRetention.compareC(scope, selected, maximumBytes, record).map(_ => ()))
      _ <- new ReadBytes(core, storeName, selected, maximumBytes).readC()
      result <- GoalPhaseArtifactCatalogRetention.retainC(scope, selected, maximumBytes,
        new GoalPhaseArtifactCatalogRetention.Port {
          def loadC() = storage.loadC()
          def createC(record: org.goldenport.record.Record) = storage.createC(record)
        })
    } yield result

  private final class ReadBytes(val core: ActionCall.Core, storeName: String, selected: References,
    maximumBytes: Int) extends ProcedureActionCall {
    def readC(): Consequence[Unit] = {
      given ExecutionContext = executionContext
      val space = executionContext.dataStoreSpace
      val prior = space.captureBinding()
      try for {
        _ <- bind_component_application_datastore_c(storeName)
        service <- BlobPayloadSupport.service(core.component.get)
        _ <- service.blobStore.get(selected.program.storageRef).flatMap(verifyReadC(selected.program, _, maximumBytes))
        _ <- service.blobStore.get(selected.planning.storageRef).flatMap(verifyReadC(selected.planning, _, maximumBytes))
      } yield ()
      finally space.restoreBinding(prior)
    }
    def execute(): Consequence[OperationResponse] = Consequence.stateConflict("Artifact byte verification is not an Operation")
  }
}
