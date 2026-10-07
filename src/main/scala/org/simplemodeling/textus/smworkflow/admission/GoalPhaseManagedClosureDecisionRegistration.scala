package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Immutable stop-only question metadata; never approval, resume or a Git grant.
  * Inputs are internal correlation, not public write/actor authority.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseManagedClosureDecisionRegistration {
  def registerForCallC(reviewIdentity: ContinuationIdentity, intent: GoalPhaseClosureAuthority.Intent,
    decisionWire: String, core: ActionCall.Core, declaration: GoalPhaseManagedStartAssembly.Declaration,
    collection: DataStore.CollectionId): Consequence[ContinuationRequest[GoalPhaseClosureAuthority.Input]] =
    if (reviewIdentity == null || intent == null || core == null || core.executionContext == null ||
        declaration == null || declaration.configuration == null || declaration.definition == null ||
        collection == null || !core.component.exists(value =>
          value.componentId == SmWorkflowComponent.componentId && value.subsystem.nonEmpty))
      Consequence.stateConflict("Decision registration requires its managed current call")
    else {
      given ExecutionContext = core.executionContext
      for {
        records <- GoalPhaseHostRecordReadPolicy.decodeC(core.component.get.subsystem.get.configuration)
        _ <- if (records.collections.contains(GoalPhaseHostRecordReadPolicy.Purpose.Decision -> collection)) Consequence.unit
          else Consequence.stateConflict("Decision registration destination differs from its host declaration")
        // The opaque fixed-key capability checks captured Create authority before any record access.
        write <- ComponentRecordStorage.bindForCallC(core, declaration.configuration.workflowStore.name,
          collection, DataStore.StringEntryId(intent.idempotencyKey))
        raw <- GoalPhaseClosureDecisionRecord.encodeStorageC(intent, decisionWire)
        expected <- GoalPhaseClosureDecisionRecord.decodeStorageC(intent, raw)
        _ <- if (expected.operation != null && expected.completionOperation != null &&
            expected.requiredOperation != null && expected.operation.service != "GoalPhaseService" &&
            expected.completionOperation.service != "GoalPhaseService") Consequence.unit
          else Consequence.stateConflict("Decision metadata cannot advertise semantic GoalPhase completion")
        result <- withCurrentIntentC(reviewIdentity, intent, core, declaration) {
          GoalPhaseClosureDecisionRetention.retainC(intent, decisionWire, new GoalPhaseClosureDecisionRetention.Port {
            def loadC() = write.loadC()
            def createC(record: Record) = write.createC(record)
          })
        }
      } yield result
    }

  /** Shared current reviewed-candidate guard; callback supplies its own independently admitted write capability. */
  private[admission] def withCurrentIntentC[A](reviewIdentity: ContinuationIdentity, intent: GoalPhaseClosureAuthority.Intent,
    core: ActionCall.Core, declaration: GoalPhaseManagedStartAssembly.Declaration)(operation: => Consequence[A]): Consequence[A] =
    if (intent == null) Consequence.stateConflict("Decision intent is absent")
    else withRetainedIntentC(reviewIdentity, core, declaration) { actual =>
      if (actual == intent) operation
      else Consequence.stateConflict("Decision registration intent differs from committed review")
    }

  /** Derive, never accept from command input, the current accepted-review intent. */
  private[admission] def withRetainedIntentC[A](reviewIdentity: ContinuationIdentity,
    core: ActionCall.Core, declaration: GoalPhaseManagedStartAssembly.Declaration)
    (operation: GoalPhaseClosureAuthority.Intent => Consequence[A]): Consequence[A] =
    if (reviewIdentity == null || operation == null || core == null || core.executionContext == null ||
        declaration == null || declaration.configuration == null || declaration.definition == null ||
        core.component == null || !core.component.exists(value => value.componentId == SmWorkflowComponent.componentId && value.subsystem.nonEmpty))
      Consequence.stateConflict("Decision registration requires its managed current call")
    else {
      given ExecutionContext = core.executionContext
      for {
        readers <- GoalPhaseHostRecordReaders.bindC(core.component.get.subsystem.get.configuration, declaration)
        storage <- WorkflowComponentStorage.bindC(ComponentCreate(core.component.get.subsystem.get, ComponentOrigin.Builtin),
          SmWorkflowComponent.componentId, declaration.configuration, Vector(declaration.definition),
          declaration.instances, declaration.continuations)
        completion <- storage.bindCompletionC(StateMachineOperationIdentity("GoalPhaseService", "submitStepReview"),
          GoalPhaseStepCloseJson.payloadCodec, GoalPhaseReviewCompletionOperation.payloadCodec,
          StateMachineResultTypeReference("ReviewStepResult"), (_, _) => Consequence.success(Vector.empty))
        issued <- storage.bindIssuedC(StateMachineOperationIdentity("GoalPhaseService", "submitStepReview"), GoalPhaseStepCloseJson.payloadCodec)
        instances <- storage.bindInstancesC()
        retained <- completion.loadRetainedC(reviewIdentity).flatMap {
          case Some(value) if value.completion.result.result.value.decision == "accept" => Consequence.success(value)
          case _ => Consequence.stateConflict("Decision registration has no retained accepted review")
        }
        submitted = retained.completion.result
        current <- instances.loadC(submitted.handle.instanceIdentity).flatMap {
          case Some(value) if value.revision == retained.completion.instanceRevision &&
              value.lifecycle == org.goldenport.cncf.workflow.WorkflowInstancePersistence.Lifecycle.Active && value.suspension.isEmpty =>
            Consequence.success(value)
          case _ => Consequence.stateConflict("Decision registration review is no longer current")
        }
        selected <- readers.selection.loadForCallC(current.correlation.value, core).flatMap {
          case Some(value) if value.workflowIdentity == submitted.handle.workflowIdentity &&
              value.workflowRevision == submitted.handle.workflowRevision => Consequence.success(value)
          case _ => Consequence.stateConflict("Decision registration selection differs")
        }
        _ <- GoalPhaseParticipantAdmission.admitC(selected.participantIdentity, core.executionContext)
        work <- issued.issuedPersistence.loadC(reviewIdentity).flatMap {
          case Some(value) => value.current match {
            case WorkflowContinuation.WorkOrder(order, _, _) => order.input match {
              case Some(value) if value.typeIdentity == "StepCloseRequest" => Consequence.success(value.value)
              case _ => Consequence.stateConflict("Decision registration has no typed issued candidate")
            }
            case _ => Consequence.stateConflict("Decision registration has no issued review")
          }
          case _ => Consequence.stateConflict("Decision registration has no issued review")
        }
        candidate = work.copy(reviewEvidence = (work.reviewEvidence ++ submitted.result.value.reviewEvidence).distinct)
        actual <- GoalPhaseClosureAuthority.intentC(candidate, submitted.handle, current)
        result <- operation(actual)
        latest <- instances.loadC(current.identity)
        _ <- if (latest.exists(GoalPhaseStepClosureBoundary.sameRecord(current, _))) Consequence.unit
          else Consequence.stateConflict("Decision registration Workflow changed; retained metadata cannot be published")
      } yield result
    }

}
