package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.WorkflowComponentStorage
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import BoundedWorkRuntime.{Host, Payload}
import RepositorySyncManagedGitObservation as G
import StandaloneFixExecution as S

/** Rebind only the unissued Implement following a managed scope correction.
  * The ordinary mechanical checkpoint retains the prior execution base in
  * history; it does not alter the adopted business input or repository.
  */
private[smworkflow] object DelegateTaskImplementationContinuation {
  private def invalid[A](message: String): Consequence[A] =
    Consequence.stateConflict("DelegateTask implementation source continuation: " + message)

  private def descendantC(core: ActionCall.Core, head: String, base: String): Consequence[Unit] = {
    def walk(pending: Vector[String], seen: Set[String]): Consequence[Unit] = pending.headOption match {
      case None => invalid("current HEAD does not descend from the retained base")
      case Some(id) if id == base => Consequence.unit
      case Some(id) if seen(id) => walk(pending.tail, seen)
      case Some(id) => G.commitC(core, id).flatMap { commit =>
        walk(pending.tail ++ commit.parents, seen + id)
      }
    }
    walk(Vector(head), Set.empty)
  }

  def advanceC(owner: BoundedWorkNativeRun, host: Host, storage: WorkflowComponentStorage,
    current: InstanceRecord, payload: Payload, core: ActionCall.Core): Consequence[InstanceRecord] = {
    val state = payload.state
    val corrected = current.suspension.isEmpty && current.causation.value.startsWith("standalone-state:") &&
      current.history.dropRight(1).lastOption.exists(_.causation.value.startsWith(DelegateTaskScopeCorrection.resumeCause))
    if (!corrected || state.stage != "Implement" || !state.input.delegation.exists(_.role == "destination"))
      Consequence.success(current)
    else for {
      _ <- if (state.candidate.isEmpty && state.receipts.isEmpty && state.pendingCheck.isEmpty &&
        state.semanticReference.isEmpty && state.reviewReference.isEmpty && state.priorReview.isEmpty &&
        state.priorReviewCandidate.isEmpty && state.commit.isEmpty && state.commitExecution.isEmpty &&
        payload.records.isEmpty && payload.candidateContinuation.isEmpty && payload.validationContinuation.isEmpty)
        Consequence.unit else invalid("implementation already has candidate or execution evidence")
      _ <- S.inputC(state.input)
      _ <- if (payload.root == host.root && payload.branch == host.branch &&
        state.input.ownedPaths.forall(path => host.paths.contains(path) && S.permitted(path, state.input))) Consequence.unit
        else invalid("configured target or ownership changed")
      root <- G.rootC(core)
      observed <- G.observeC(core)
      _ <- if (root == payload.root && observed.branch == payload.branch) Consequence.unit
        else invalid("repository root or branch changed")
      _ <- if (observed.dirty.forall { entry =>
        val endpoints = entry.path +: entry.renamedFrom.toVector
        val owned = endpoints.map(S.owned(_, state.input.ownedPaths))
        !entry.unmerged && (if (owned.contains(true)) owned.forall(identity) && endpoints.forall(S.permitted(_, state.input)) else true)
      }) Consequence.unit else invalid("dirty work crosses the adopted ownership boundary")
      _ <- descendantC(core, observed.head, payload.baseHead)
      unrelated = observed.dirty.filterNot(d => S.owned(d.path, state.input.ownedPaths))
      next <- if (observed.head == payload.baseHead && payload.unrelated.contains(unrelated)) Consequence.success(current)
        else owner.saveC(host, storage, current, payload.copy(baseHead = observed.head, unrelated = Some(unrelated)), core)
    } yield next
  }
}
