package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.RepositorySyncBoundaryLifecycle

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncMergedConvergenceCommand, RepositorySyncConvergenceResult}
import RepositorySyncMergeReviewBoundary as B
import RepositorySyncMergeCommitBoundary as C

/** Host-retained evidence only: no Git/network operation or implicit commit.
  * Existing loose ordered persistence records push before terminal admission.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncMergedConvergenceExecution {
  final case class Ports(review: RepositorySyncMergeReviewServiceOperation.Ports,
    commits: C.CommitPort, committedInspection: C.InspectionPort,
    push: RepositorySyncPushAssessment.Port, convergence: RepositorySyncConvergenceAssessment.Port)

  def admitC(command: RepositorySyncMergedConvergenceCommand,
    ports: Ports): Consequence[WorkflowInteraction[Nothing, RepositorySyncConvergenceResult]] =
    if (command == null || command.expectedRevision != "10" ||
        !_text(command.reviewResultIdentity) || !_text(command.reviewResultRevision) ||
        !_text(command.pushIdempotencyKey) || !_text(command.idempotencyKey) ||
        ports == null || ports.review == null || ports.commits == null || ports.committedInspection == null ||
        ports.push == null || ports.convergence == null)
      Consequence.stateConflict("RepositorySync merged convergence command or ports are incomplete")
    else for {
      committed <- C.readCommittedC(ContextReference(command.reviewResultIdentity, command.reviewResultRevision),
        ports.review, ports.commits, ports.committedInspection)
      loaded <- ports.review.instances.load(ports.review.configuration, committed.instance.identity)
      current <- loaded match {
        case Some(value) if value != null && value.revision != null && Set(10L, 11L, 12L).contains(value.revision.value) => value.validateC
        case _ => Consequence.stateConflict("RepositorySync merged convergence history is missing")
      }
      actual <- B.prefixC(current, 10L)
      _ <- if (B._sameRecord(actual, committed.instance)) Consequence.unit
        else Consequence.stateConflict("RepositorySync merged convergence commit prefix changed")
      _ <- if (RepositorySyncBoundaryLifecycle.permits("MergeValidated", "reviewed-merge-push-admitted", "PushAdmitted") &&
          RepositorySyncBoundaryLifecycle.permits("PushAdmitted", "convergence-admitted", "Converged"))
        Consequence.unit else Consequence.stateConflict("RepositorySync reviewed merge convergence transitions are not declared")
      push <- RepositorySyncPushAssessment.assessMergedC(committed, ports.push)
      pushed <- committed.instance.appendC(InstanceRevision(10L), HistoryEntry(HistorySequence(11L), InstanceRevision(11L),
        Lifecycle.Active, Some(ProgressionReference("sync.PushAdmitted")), current.correlation,
        CausationReference(s"merged-push:${_binding(command.pushIdempotencyKey, push.digest)}"), None, None, None))
      _ <- if (current.revision == InstanceRevision(10L)) Consequence.unit
        else B.prefixC(current, 11L).flatMap { prefix =>
          if (B._sameRecord(prefix, pushed)) Consequence.unit
          else Consequence.stateConflict("RepositorySync merged push history or key differs")
        }
      savedPush <- if (current.revision == InstanceRevision(10L)) for {
        _ <- _currentC(current, ports)
        saved <- ports.review.instances.append(ports.review.configuration, current.identity,
          InstanceRevision(10L), pushed.history.last)
        _ <- if (B._sameRecord(saved, pushed)) Consequence.unit
          else Consequence.stateConflict("RepositorySync merged push append differs")
      } yield saved
      else Consequence.success(current)
      assessment <- RepositorySyncConvergenceAssessment.assessMergedC(committed, push, ports.convergence)
      state = assessment.verification.state
      result = RepositorySyncConvergenceResult("SYNCHRONIZED", state.repositoryIdentity, state.localBranch,
        state.trackingRemote, state.trackingBranch, state.remoteEndpointIdentity,
        state.localHeadOid, state.localTreeOid, state.localTreeDigest, assessment.digest)
      _ <- RepositorySyncConvergenceJson.validateC(result)
      expected <- pushed.appendC(InstanceRevision(11L), HistoryEntry(HistorySequence(12L), InstanceRevision(12L),
        Lifecycle.Completed, None, pushed.correlation,
        CausationReference(s"merged-convergence:${_binding(command.idempotencyKey, assessment.digest)}"), None, None, None))
      _ <- if (savedPush.revision == InstanceRevision(11L) || B._sameRecord(savedPush, expected)) Consequence.unit
        else Consequence.stateConflict("RepositorySync merged convergence terminal or key differs")
      _ <- _currentC(savedPush, ports)
      completed <- if (savedPush.revision == InstanceRevision(11L))
        ports.review.instances.append(ports.review.configuration, savedPush.identity,
          InstanceRevision(11L), expected.history.last)
        else Consequence.success(savedPush)
      _ <- if (B._sameRecord(completed, expected)) Consequence.unit
        else Consequence.stateConflict("RepositorySync merged convergence terminal append differs")
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, completed)
      terminal <- projectTerminalC(handle, completed, TypedValue("RepositorySyncConvergenceResult", result),
        MinimalPresentation("Repository Sync", "SYNCHRONIZED", nextAction = Some("Continue development")))
    } yield terminal

  private def _currentC(expected: InstanceRecord, ports: Ports): Consequence[Unit] =
    ports.review.instances.load(ports.review.configuration, expected.identity).flatMap {
      case Some(value) if B._sameRecord(value, expected) => Consequence.unit
      case _ => Consequence.stateConflict("RepositorySync merged convergence WorkflowInstance changed")
    }
  private def _binding(key: String, digest: String): String =
    RepositorySyncMergeValidationAssessment.digest(Vector(key, digest))
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty
}
