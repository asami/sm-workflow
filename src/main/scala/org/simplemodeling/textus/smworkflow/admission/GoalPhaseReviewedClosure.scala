package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{ReviewStepResult, StepCloseRequest}

/** Fixture-only retained completed review. Completion is one-shot; projection
  * consumes the selected receipt and current state, not a no-change proof.
  * It is not a human Result/resume engine or a shared transaction.
  * @since   Sep. 29, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseReviewedClosure {
  final case class Record(issued: WorkflowInteraction[StepCloseRequest, Nothing],
    submitted: ContinuationResult[ReviewStepResult], instanceIdentity: InstanceIdentity,
    instanceRevision: InstanceRevision)
  trait ReceiptPort {
    /** Persist before returning. An existing slot selects its retained input;
      * a later proposed body neither replaces it nor requires a no-change proof.
      */
    def putIfAbsentC(record: Record): Consequence[Record]
    def loadC(instance: InstanceIdentity, revision: InstanceRevision): Consequence[Option[Record]]
  }
  final class InMemory extends ReceiptPort {
    private val records = mutable.Map.empty[(InstanceIdentity, InstanceRevision), Record]
    def putIfAbsentC(record: Record): Consequence[Record] = synchronized {
      _admitRecordC(record).flatMap { _ =>
        val key = (record.instanceIdentity, record.instanceRevision)
        records.get(key) match {
          case Some(saved) => Consequence.success(saved)
          case None => records.update(key, record); Consequence.success(record)
        }
      }
    }
    def loadC(instance: InstanceIdentity, revision: InstanceRevision): Consequence[Option[Record]] = synchronized {
      if (instance == null || revision == null) Consequence.stateConflict("GoalPhase accepted review lookup is incomplete")
      else Consequence.success(records.get((instance, revision)))
    }
  }
  final case class Ports(authority: GoalPhaseClosureAuthority.Ports, receipts: ReceiptPort) {
    def validateC: Consequence[Unit] =
      if (authority == null || receipts == null)
        Consequence.configurationInvalid("GoalPhase reviewed authority ports are incomplete")
      else authority.validateC
  }

  def afterReviewC(artifact: CandidateAdmissionProducerAbi.Artifact,
    issued: WorkflowInteraction[StepCloseRequest, Nothing],
    submitted: ContinuationResult[ReviewStepResult], current: InstanceRecord, asOf: Instant,
    instances: WorkflowInstancePersistence, configuration: Configuration,
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
    commit: GoalPhaseStepClosureBoundary.CommitPort, ports: Ports): Consequence[String] =
    if (ports == null || snapshot == null || commit == null || instances == null || configuration == null)
      Consequence.stateConflict("GoalPhase reviewed authority context is incomplete")
    else for {
      _ <- ports.validateC
      _ <- if (current != null) Consequence.unit
        else Consequence.stateConflict("GoalPhase completed review has no current instance")
      proposed = Record(issued, submitted, current.identity, current.revision)
      saved <- ports.receipts.putIfAbsentC(proposed)
      response <- _runC(artifact, saved, current, asOf, instances, configuration, snapshot, commit, ports, true)
    } yield response

  /** Host-only read recovery. No callable Operation is added; it cannot consume
    * a Result, append history or commit even if authority has since appeared.
    */
  def projectRetainedC(handle: WorkflowHandle, artifact: CandidateAdmissionProducerAbi.Artifact,
    asOf: Instant, instances: WorkflowInstancePersistence, configuration: Configuration,
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort, ports: Ports): Consequence[String] =
    if (handle == null || ports == null || instances == null || configuration == null ||
        handle.componentIdentity != SmWorkflowComponent.componentId)
      Consequence.stateConflict("GoalPhase retained projection context is incomplete")
    else for {
      _ <- ports.validateC
      _ <- handle.validateC
      loaded <- instances.load(configuration, handle.instanceIdentity)
      current <- loaded match {
        case Some(value) if value != null => value.validateC
        case _ => Consequence.stateConflict("GoalPhase retained projection has no Workflow instance")
      }
      expected <- WorkflowHandle.fromRecordC(handle.componentIdentity, current)
      _ <- if (expected == handle) Consequence.unit
        else Consequence.stateConflict("GoalPhase retained projection Handle differs from the instance")
      found <- ports.receipts.loadC(current.identity, current.revision)
      saved <- Option(found).flatten match {
        case Some(value) if _complete(value) => Consequence.success(value)
        case _ => Consequence.stateConflict("GoalPhase retained accepted review is unavailable")
      }
      response <- _runC(artifact, saved, current, asOf, instances, configuration, snapshot, null, ports, false)
    } yield response

  private def _runC(artifact: CandidateAdmissionProducerAbi.Artifact, saved: Record,
    current: InstanceRecord, asOf: Instant, instances: WorkflowInstancePersistence, configuration: Configuration,
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort, commit: GoalPhaseStepClosureBoundary.CommitPort,
    ports: Ports, allowCommit: Boolean): Consequence[String] =
    if (!_complete(saved) || current == null || saved.instanceIdentity != current.identity ||
        saved.instanceRevision != current.revision)
      Consequence.stateConflict("GoalPhase retained accepted review differs from the current instance")
    else _admitRecordC(saved).flatMap { _ =>
      GoalPhaseClosureAuthority.closeReviewedOrDecisionC(artifact, saved.issued, saved.submitted,
        asOf, current, instances, configuration, snapshot, commit, ports.authority, allowCommit)
    }

  private def _complete(value: Record): Boolean = value != null && value.issued != null &&
    value.submitted != null && value.instanceIdentity != null && value.instanceRevision != null

  /** Actual receipt-slot placement, not a repeated Result admission.
    * Issued-result correlation, semantic review and current state are assessed
    * once by the closure plan at use, not serialized or certified by storage.
    */
  private def _admitRecordC(value: Record): Consequence[Unit] =
    if (!_complete(value)) Consequence.stateConflict("GoalPhase accepted review receipt is incomplete")
    else if (value.issued.handle == null || value.issued.handle.instanceIdentity != value.instanceIdentity)
      Consequence.stateConflict("GoalPhase completed review belongs to another receipt slot")
    else Consequence.unit
}
