package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{WorkflowHandle, WorkflowInteraction, WorkflowContinuation}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.simplemodeling.textus.smworkflow.value.StepCloseRequest
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseStepClosureBoundary.{ArtifactSnapshot, SnapshotPort}

/** Current execution routing only, never a grant or a Factory-held context.
  * Concrete readers must verify their lease and configured destination on each
  * access; a publicly constructible ActionCall is not evidence of either.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseCallScopedSnapshot {
  enum Purpose {
    case Candidate, RepairSuccessor
  }
  /** Correlation from already admitted facts; not an authentication or storage
    * capability. RepairSuccessor anchors lineage to the old issued candidate,
    * not an assertion that repaired bytes still have the old digests.
    */
  final class Scope private[GoalPhaseCallScopedSnapshot] (
    val handle: WorkflowHandle, val request: StepCloseRequest,
    val candidateRevision: String, val purpose: Purpose
  )

  trait ScopePort extends SnapshotPort {
    final def loadC(stepIdentity: String): Consequence[ArtifactSnapshot] =
      Consequence.stateConflict("GoalPhase artifact snapshot requires admitted scope")
    def loadForScopeC(scope: Scope): Consequence[ArtifactSnapshot]
  }

  trait Port extends SnapshotPort {
    final def loadC(stepIdentity: String): Consequence[ArtifactSnapshot] =
      Consequence.stateConflict("GoalPhase artifact snapshot requires a current Operation call")
    def loadForCallC(stepIdentity: String, core: ActionCall.Core): Consequence[ArtifactSnapshot]
  }

  trait ScopedPort extends Port {
    final def loadForCallC(stepIdentity: String, core: ActionCall.Core): Consequence[ArtifactSnapshot] =
      Consequence.stateConflict("GoalPhase artifact call requires Workflow and candidate scope")
    def loadForScopeCallC(scope: Scope, core: ActionCall.Core): Consequence[ArtifactSnapshot]
  }

  def scopeC(handle: WorkflowHandle, request: StepCloseRequest, purpose: Purpose): Consequence[Scope] =
    if (handle == null || handle.componentIdentity != SmWorkflowComponent.componentId || purpose == null)
      Consequence.stateConflict("GoalPhase artifact scope is incomplete or foreign")
    else for {
      _ <- handle.validateC
      submission <- StepCloseRequestAdapter.submissionC(request)
    } yield new Scope(handle, request, submission.candidate.reference.revision, purpose)

  def forScopeC(snapshot: SnapshotPort, handle: WorkflowHandle, request: StepCloseRequest,
    purpose: Purpose = Purpose.Candidate): Consequence[SnapshotPort] =
    if (snapshot == null) Consequence.stateConflict("GoalPhase artifact reader is missing")
    else scopeC(handle, request, purpose).flatMap { scope => snapshot match {
      case scoped: ScopePort => Consequence.success(new SnapshotPort {
        def loadC(step: String): Consequence[ArtifactSnapshot] =
          if (step == scope.request.stepIdentity) scoped.loadForScopeC(scope)
          else Consequence.stateConflict("GoalPhase artifact Step differs from the bound scope")
      })
      case _: ScopedPort => Consequence.stateConflict("GoalPhase artifact scope has no current call")
      case existing => Consequence.success(existing)
    }}

  def forCurrentC(snapshot: SnapshotPort, current: InstanceRecord, request: StepCloseRequest):
      Consequence[SnapshotPort] =
    if (current == null) Consequence.stateConflict("GoalPhase artifact scope has no retained instance")
    else for {
      admitted <- current.validateC
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, admitted)
      bound <- forScopeC(snapshot, handle, request)
    } yield bound

  def forIssuedC(snapshot: SnapshotPort, current: InstanceRecord,
    issued: WorkflowInteraction[StepCloseRequest, Nothing]): Consequence[SnapshotPort] =
    if (issued == null) Consequence.stateConflict("GoalPhase artifact scope has no issued work")
    else issued.current match {
      case WorkflowContinuation.WorkOrder(work, _, _) => work.input match {
        case Some(value) if value != null && value.typeIdentity == "StepCloseRequest" =>
          forCurrentC(snapshot, current, value.value)
        case _ => Consequence.stateConflict("GoalPhase artifact scope has no issued candidate")
      }
      case _ => Consequence.stateConflict("GoalPhase artifact scope is not issued work")
    }

  def forCall(snapshot: SnapshotPort, core: ActionCall.Core): SnapshotPort = snapshot match {
    case scoped: ScopedPort => new ScopePort {
      def loadForScopeC(scope: Scope): Consequence[ArtifactSnapshot] =
        if (scope == null || !_validCore(core))
          Consequence.stateConflict("GoalPhase artifact scoped call is incomplete or foreign")
        else scoped.loadForScopeCallC(scope, core)
    }
    case scoped: Port => new SnapshotPort {
      def loadC(stepIdentity: String): Consequence[ArtifactSnapshot] =
        if (core == null || core.executionContext == null ||
            !core.component.exists(value => value.componentId == SmWorkflowComponent.componentId &&
              value.subsystem.nonEmpty))
          Consequence.stateConflict("GoalPhase artifact call is incomplete or foreign")
        else scoped.loadForCallC(stepIdentity, core)
    }
    case existing => existing
  }

  private def _validCore(core: ActionCall.Core): Boolean = core != null && core.executionContext != null &&
    core.component.exists(value => value.componentId == SmWorkflowComponent.componentId && value.subsystem.nonEmpty)
}
