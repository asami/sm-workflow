package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import scala.collection.mutable
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{ReviewStepResult, StepCloseRequest}

/** Fixture-only retained accepted review. Completion is one-shot; projection
  * recovery reads the exact retained proof instead of submitting Result again.
  * It is not a human Result/resume engine or a shared transaction.
  * @since   Sep. 29, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseReviewedClosure {
  final case class Record(issued: WorkflowInteraction[StepCloseRequest, Nothing],
    submitted: ContinuationResult[ReviewStepResult], reviewed: InstanceRecord, proofDigest: String)
  trait ReceiptPort {
    /** Persist before returning. Exact retries are idempotent; never replace
      * a different record for the same instance/revision.
      */
    def putIfAbsentC(record: Record): Consequence[Record]
    def loadC(instance: InstanceIdentity, revision: InstanceRevision): Consequence[Option[Record]]
  }
  final class InMemory extends ReceiptPort {
    private val records = mutable.Map.empty[(InstanceIdentity, InstanceRevision), Record]
    def putIfAbsentC(record: Record): Consequence[Record] = synchronized {
      _admitRecordC(record).flatMap { _ =>
        val key = (record.reviewed.identity, record.reviewed.revision)
        records.get(key) match {
          case Some(saved) if sameRecord(saved, record) => Consequence.success(saved)
          case Some(_) => Consequence.stateConflict("GoalPhase accepted review receipt cannot be replaced")
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
      plan <- GoalPhaseStepClosureBoundary.planReviewedC(artifact, issued, submitted, asOf, current)
      scoped <- GoalPhaseCallScopedSnapshot.forIssuedC(snapshot, current, issued)
      bytes <- scoped.loadC(plan.commitRequest.stepIdentity)
      _ <- GoalPhaseStepClosureBoundary.admitSnapshotC(plan.commitRequest, bytes)
      digest <- _proofDigestC(issued, submitted)
      proposed = Record(issued, submitted, current, digest)
      saved <- ports.receipts.putIfAbsentC(proposed)
      _ <- if (sameRecord(saved, proposed)) Consequence.unit
        else Consequence.stateConflict("GoalPhase retained accepted review differs from the committed completion")
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
    if (!_complete(saved) || !GoalPhaseStepClosureBoundary.sameRecord(saved.reviewed, current))
      Consequence.stateConflict("GoalPhase retained accepted review differs from the current instance")
    else _admitRecordC(saved).flatMap { _ =>
      GoalPhaseClosureAuthority.closeReviewedOrDecisionC(artifact, saved.issued, saved.submitted,
        asOf, current, instances, configuration, snapshot, commit, ports.authority, allowCommit,
        () => ports.receipts.loadC(current.identity, current.revision).flatMap {
          case Some(latest) if sameRecord(latest, saved) => Consequence.unit
          case _ => Consequence.stateConflict("GoalPhase retained accepted review changed during authority lookup")
        })
    }

  private[admission] def sameRecord(left: Record, right: Record): Boolean =
    _complete(left) && _complete(right) && left.issued == right.issued &&
      left.submitted == right.submitted && left.proofDigest == right.proofDigest &&
      GoalPhaseStepClosureBoundary.sameRecord(left.reviewed, right.reviewed)
  private def _complete(value: Record): Boolean = value != null && value.issued != null &&
    value.submitted != null && value.reviewed != null && value.proofDigest != null

  /** Integrity check, not a signature or authority grant. The host receipt port
    * remains the trusted immutable source of the consumed Result proof.
    */
  private def _admitRecordC(value: Record): Consequence[Unit] =
    if (!_complete(value)) Consequence.stateConflict("GoalPhase accepted review receipt is incomplete")
    else _proofDigestC(value.issued, value.submitted).flatMap { digest =>
      if (value.proofDigest == digest) Consequence.unit
      else Consequence.stateConflict("GoalPhase accepted review proof digest differs from its payload")
    }
  private def _proofDigestC(issued: WorkflowInteraction[StepCloseRequest, Nothing],
    submitted: ContinuationResult[ReviewStepResult]): Consequence[String] = for {
      workWire <- WorkflowWorkOrderJsonV1.encodeC(issued, GoalPhaseStepCloseJson.payloadCodec)
      resultWire <- WorkflowResultJsonV1.encodeC(submitted, GoalPhaseReviewCompletionOperation.payloadCodec)
    } yield MessageDigest.getInstance("SHA-256").digest(
      s"${workWire.length}:$workWire${resultWire.length}:$resultWire".getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}
