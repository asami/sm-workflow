package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhasePostCompletionSync.Synchronization
import RepositorySyncManagedFetch as F
import RepositorySyncNativeProgressStore as N

/** Observe one known paired child through its ordinary Workflow bindings.
  * This reader never starts, advances or repairs the child.
  * @since   Oct. 23, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncPostCompletionObservation {
  def observeC(parent: WorkflowComponentStorage, declaration: GoalPhaseNativeRun.Declaration,
    core: ActionCall.Core, identity: String): Consequence[(Synchronization, Option[V.RepositorySyncNativeConvergenceResult])] = for {
    installation <- declaration.repositoryClosure.map(Consequence.success).getOrElse(
      GoalPhaseNativeRun.invalid("Paired RepositorySync observation installation is absent"))
    _ <- installation.validateC(declaration)
    definition <- RepositorySyncDefinitionBinding.bindC
    storage <- F.bindC(installation.declaration, definition, core)
    instances <- storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(InstanceIdentity(identity))(using core.executionContext)
    result <- current match {
      case None => Consequence.success(Synchronization("unknown",
        "Reserved child identity has no confirmed saved instance; Start is not retried.", Some(identity), None) -> None)
      case Some(record) => for {
        starts <- storage.bindMechanicalC(F.startOperation, F.codec(true))
        started <- starts.loadRetainedC(F.reference(identity, "start"))(using core.executionContext)
        observed <- started match {
          case None => Consequence.success(Synchronization("unknown",
            "Child instance exists but its original Start is unconfirmed; Start is not retried.", Some(identity), None) -> None)
          case Some(value) => for {
            context <- value.receipt.payload.value.context.map(Consequence.success).getOrElse(N.invalid("Child Start context is absent"))
            _ <- GoalPhaseParticipantAdmission.admitC(context.participantIdentity, core.executionContext)
            _ <- if (value.receipt.next.identity == record.identity && context.localBranch == declaration.target.localBranch &&
                value.receipt.payload.value.repositoryRoot == declaration.target.root.toString &&
                context.repositoryIdentity == installation.declaration.input.repositoryIdentity &&
                context.trackingRemote == installation.declaration.target.remote &&
                context.trackingBranch == installation.declaration.target.remoteBranch &&
                context.remoteEndpointIdentity == installation.declaration.input.remoteEndpointIdentity &&
                context.syncMode == installation.declaration.input.syncMode)
              Consequence.unit else N.invalid("Known child crosses its paired repository target")
            session = F.Session(storage, value, context)
            store <- N.bindC(session, installation.declaration, core, _ => Consequence.unit)
            output <- if (record.lifecycle == Lifecycle.Completed) _completed_c(store, record)
              else _pending_c(store, record)
          } yield output
        }
      } yield observed
    }
  } yield result

  private def _completed_c(store: N.Store, current: InstanceRecord):
    Consequence[(Synchronization, Option[V.RepositorySyncNativeConvergenceResult])] = for {
    saved <- store.loadC("converged").flatMap(_.map(Consequence.success).getOrElse(N.invalid("Completed child has no saved convergence")))
    payload = saved.payload
    tip <- payload.finalTip.map(Consequence.success).getOrElse(N.invalid("Saved convergence has no tip"))
    tree <- payload.indexTree.map(Consequence.success).getOrElse(N.invalid("Saved convergence has no tree"))
    after <- payload.after.map(Consequence.success).getOrElse(N.invalid("Saved convergence has no observation"))
    _ <- if (payload.stage == "Converged" && current.suspension.isEmpty && current.currentProgression.isEmpty &&
        current.causation.value == "native-repository-completed:" + store.identity &&
        current.revision == saved.record.revision.next && current.history.dropRight(1) == saved.record.history &&
        after.head == tip && after.tree == tree && after.dirty.isEmpty &&
        after.counts.contains(RepositorySyncManagedGitObservation.Counts(0, 0)) &&
        after.trackingTip.contains(tip) && after.mergeBase.contains(tip)) Consequence.unit
      else N.invalid("Child completion contradicts its saved convergence")
    _ <- org.goldenport.cncf.processexecution.GitObjectId.parseC(tip)
    _ <- org.goldenport.cncf.processexecution.GitObjectId.parseC(tree)
    result = NativeWorkflowOperationOutput.repositoryConvergence(RepositorySyncNativeConvergence.Result(store.identity,
      payload.root, store.context.localBranch, store.context.trackingRemote, store.context.trackingBranch, tip, tree))
  } yield Synchronization("succeeded", "Actual saved child convergence and native completion are retained.",
    Some(store.identity), None) -> Some(result)

  private def _pending_c(store: N.Store, current: InstanceRecord):
    Consequence[(Synchronization, Option[V.RepositorySyncNativeConvergenceResult])] = for {
    human <- RepositorySyncDefinitionBinding.humanConflictOperationC
    stage = current.currentProgression.map(_.value.stripPrefix("sync.")).getOrElse("unconfirmed")
    key = "native-repository:" + store.identity + ":"
    saved <- if (current.causation.value.startsWith(key)) store.loadC(current.causation.value.stripPrefix(key))
      else Consequence.success(Option.empty[N.Stage])
    _ <- if (saved.forall(_.record == current)) Consequence.unit else N.invalid("Child progress differs from its actual frontier")
    receipts = saved.toVector.flatMap(_.payload.validation).flatMap(_.receipts)
    failed = receipts.exists(receipt => receipt.termination == "exited" && receipt.exitCode.exists(_ != 0))
    uncertain = Set("Fetching", "Preserving", "IndexCaptured", "CheckpointCreating", "CheckpointInstalling",
      "Integrating", "NativeCommitPreparing", "NativePushPreparing", "NativeFinalFetching")(stage) ||
      (current.causation.value.startsWith(key) && current.causation.value.stripPrefix(key).contains("validation-intent-")) ||
      receipts.exists(receipt => receipt.termination != "exited" || receipt.exitCode.isEmpty)
    state = if (current.suspension.exists(_.completion.value == human.metadata.completionContract.identity) ||
        stage == "RepairDecisionRequired") "human"
      else if (uncertain) "unknown"
      else if (failed || stage.contains("Failed")) "failed"
      else if (current.suspension.nonEmpty) "request"
      else "in-progress"
  } yield Synchronization(state, "Saved RepositorySync frontier: " + stage +
    ". Continue only through the child's owning operation; Phase completion remains retained.",
    Some(store.identity), Some("advanceRepositorySync")) -> None
}
