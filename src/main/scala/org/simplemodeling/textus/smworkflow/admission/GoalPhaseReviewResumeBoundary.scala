package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant

import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentId
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.value.{ReviewStepResult, StepCloseRequest}

/** Consumes an admitted review result through CNCF's persisted, one-shot
  * Continuation boundary. The resulting instance remains Active: Step Closed
  * requires a separate deterministic commit and its evidence.
  *
  * Continuation resume and WorkflowInstance append are deliberately separate
  * durable operations under the current loose transaction contract.
  * @since   Sep. 26, 2026
  * @version Oct.  8, 2026
  */
object GoalPhaseReviewResumeBoundary {
  final case class Resumed(
    continuation: ContinuationRuntime.Resume,
    assessment: GoalPhaseReviewResultAdapter.Assessed,
    instance: InstanceRecord
  ) {
    def closureReady: Boolean = assessment.closureReady
    def repairRequested: Boolean = assessment.repairRequested
  }

  def resumeC(
    artifact: CandidateAdmissionProducerAbi.Artifact,
    submitted: ContinuationResult[ReviewStepResult],
    asOf: Instant,
    component: ComponentId,
    issuedPersistence: IssuedWorkOrderPersistence[StepCloseRequest],
    runtime: ContinuationRuntime,
    instancePersistence: WorkflowInstancePersistence,
    configuration: Configuration,
    freshUnitOfWork: () => UnitOfWork
  ): Consequence[Resumed] =
    if (submitted == null || submitted.continuationId == null || component == null ||
        issuedPersistence == null || runtime == null || instancePersistence == null ||
        configuration == null || freshUnitOfWork == null)
      Consequence.stateConflict("GoalPhase review resume input is incomplete")
    else for {
      acceptedConfiguration <- configuration.validateC
      loaded <- issuedPersistence.loadC(submitted.continuationId)
      issued <- loaded match {
        case Some(value) if value != null => Consequence.success(value)
        case _ => Consequence.stateConflict("GoalPhase review has no issued WorkOrder")
      }
      assessment <- GoalPhaseReviewResultAdapter.assessC(artifact, issued, submitted, asOf)
      loadedInstance <- instancePersistence.load(acceptedConfiguration, issued.handle.instanceIdentity)
      current <- loadedInstance match {
        case Some(value) if value != null => value.validateC
        case _ => Consequence.stateConflict("GoalPhase review WorkflowInstance is unavailable")
      }
      next <- reviewedRecordC(current, submitted)
      resumed <- WorkflowProtocolV1.resumeIssuedWithInstanceGuardC(
        component, issued, submitted, runtime, instancePersistence, acceptedConfiguration,
        freshUnitOfWork
      )
      persisted <- instancePersistence.append(
        acceptedConfiguration, current.identity, current.revision, next.history.last
      )
      _ <- if (persisted == next) Consequence.unit
           else Consequence.stateConflict("GoalPhase review WorkflowInstance append diverged")
    } yield Resumed(resumed, assessment, persisted)

  private[admission] def reviewedRecordC(
    current: InstanceRecord,
    submitted: ContinuationResult[ReviewStepResult]
  ): Consequence[InstanceRecord] =
    if (current.lifecycle != Lifecycle.Active || current.suspension.isEmpty ||
        current.suspension.exists(_.continuationIdentity.value != submitted.continuationId.value) ||
        current.currentProgression.isEmpty || submitted.resultReference == null)
      Consequence.stateConflict("GoalPhase review is not suspended on the submitted Continuation")
    else current.appendC(current.revision, HistoryEntry(
      HistorySequence(current.history.size.toLong + 1L), current.revision.next,
      Lifecycle.Active, current.currentProgression, current.correlation,
      CausationReference(
        s"review-result:${submitted.resultReference.identity}@${submitted.resultReference.revision}"
      ), current.derivedCompositeOccurrence, current.committedPredecessor, None
    ))
}
