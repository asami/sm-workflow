/**
 * @since   Nov.  6, 2026
 * @version Nov.  8, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import GoalPhaseDependencyValidation as V
import GoalPhaseNativeWork as W

/** Ephemeral projection built only while traversing authenticated native completions.
  * External evidence references are deliberately not completion identities. */
private[smworkflow] object GoalPhaseDependencyApprovalLineage {
  final case class Key(session: String, plan: ContextReference, step: String, continuation: String, revision: Long)
  final case class Entry(key: Key, original: V.Approval, context: W.DecisionContext,
    effective: V.Approval, effectiveContext: W.DecisionContext, active: Boolean)
  final case class State(entries: Vector[Entry] = Vector.empty) {
    def approvals: Vector[V.Approval] = entries.filter(_.active).map(_.effective)
    def decisions: Vector[W.DecisionContext] = entries.filter(_.active).map(_.effectiveContext)
  }

  private def invalid[A](message: String): Consequence[A] =
    GoalPhaseNativeRun.invalid("dependency approval lineage: " + message)

  private def _valid_key(key: Key): Boolean = key != null && key.session != null && key.session.nonEmpty &&
    key.plan != null && key.plan.identity != null && key.plan.identity.nonEmpty &&
    key.plan.revision != null && key.plan.revision.nonEmpty && key.step != null && key.step.nonEmpty &&
    key.continuation != null && key.continuation.nonEmpty && key.revision > 0

  private def _valid_context(context: W.DecisionContext): Boolean = context != null &&
    context.requestingResult != null && context.requestingResult.dependencyProposal != null &&
    context.decisionReference != null && context.inventoryContinuation != null

  private def _valid_state(state: State): Boolean = state != null && state.entries != null &&
    state.entries.forall(e => e != null && _valid_key(e.key) && V.approvalValid(e.original) &&
      V.approvalValid(e.effective) && _valid_context(e.context) && _valid_context(e.effectiveContext)) &&
    state.entries.map(_.key.session).distinct.size <= 1 &&
    state.entries.map(_.key.continuation).distinct.size == state.entries.size &&
    state.entries.map(_.key.revision).sliding(2).forall(pair => pair.size < 2 || pair.head < pair.last)

  def appendC(state: State, key: Key, approval: V.Approval, context: W.DecisionContext,
    issued: Option[W.DecisionContext]): Consequence[State] = {
    if (!_valid_state(state) || !_valid_key(key) || !V.approvalValid(approval) || !_valid_context(context) ||
        issued == null || issued.exists(c => !_valid_context(c)) || context.choice != "ApproveDependencyScope" ||
        context.decisionReference != Some(approval.decision) ||
        !context.requestingResult.dependencyProposal.contains(approval.proposal) || approval.step != key.step ||
        state.entries.exists(e => e.key.session != key.session || e.key.continuation == key.continuation ||
          e.key.revision >= key.revision))
      invalid("invalid, crossed or out-of-order native completion")
    else {
      val entry = Entry(key, approval, context, approval, context, active = true)
      approval.proposal.scopeUpdate match {
        case None => Consequence.success(state.copy(entries = state.entries :+ entry))
        case Some(update) =>
          val candidates = state.entries.zipWithIndex.filter { case (parent, _) =>
            parent.active && parent.key.session == key.session && parent.key.plan == key.plan &&
              parent.key.step == key.step && parent.key.revision < key.revision &&
              parent.effective.decision == update.approval && issued.contains(parent.effectiveContext)
          }
          candidates match {
            case Vector((parent, index)) if _compatible(parent.effective.proposal, approval.proposal) =>
              Consequence.success(state.copy(entries = state.entries.updated(index, parent.copy(active = false)) :+ entry))
            case Vector((parent, _)) if parent.effective.decision != approval.decision &&
                _same_subject(parent.effective.proposal, approval.proposal) &&
                approval.proposal.authorizationBoundary.nonEmpty &&
                _parent_purpose(parent.effective.proposal) != approval.proposal.authorizationBoundary.map(_.purpose).get =>
              // A separately accepted different-purpose answer is independent, not inherited.
              Consequence.success(state.copy(entries = state.entries :+ entry))
            case _ => invalid("scope update has no unique compatible earlier native parent in the issued context")
          }
      }
    }
  }

  private def _same_subject(parent: GoalPhaseDependencyScope.Proposal, child: GoalPhaseDependencyScope.Proposal): Boolean =
    parent.requester == child.requester && parent.dependency == child.dependency &&
      parent.usageContract == child.usageContract && parent.acceptance == child.acceptance &&
      parent.dependentWork == child.dependentWork && parent.paths.forall(child.paths.contains)

  private def _parent_purpose(parent: GoalPhaseDependencyScope.Proposal): ContextReference =
    parent.authorizationBoundary.map(_.purpose).getOrElse(ContextReference(parent.identity, parent.revision))

  private def _compatible(parent: GoalPhaseDependencyScope.Proposal, child: GoalPhaseDependencyScope.Proposal): Boolean =
    _same_subject(parent, child) && child.authorizationBoundary.exists(_.purpose == _parent_purpose(parent))

  /** Inventory is a projection of one actual completion; raw entries stay intact. */
  def inventoryC(state: State, saved: GoalPhaseDependencyInventoryContinuation.Saved): Consequence[State] = {
    if (!_valid_state(state) || saved == null || saved.payload == null || !V.approvalValid(saved.payload.approval) ||
        !_valid_context(saved.context) || !V.approvalValid(saved.approval))
      return invalid("invalid native inventory attribution")
    val matches = state.entries.zipWithIndex.filter { case (entry, _) =>
      val current = entry.effectiveContext
      entry.active && entry.original.decision == saved.payload.approval.decision &&
        entry.original == saved.payload.approval &&
        current.copy(inventoryContinuation = None) == saved.context.copy(inventoryContinuation = None) &&
        current.inventoryContinuation.map(_.proposal).orElse(current.requestingResult.dependencyProposal)
          .contains(entry.effective.proposal)
    }
    matches match {
      case Vector((entry, index)) => Consequence.success(state.copy(entries = state.entries.updated(index,
        entry.copy(effective = saved.approval, effectiveContext = saved.context))))
      case _ => invalid("inventory receipt does not identify exactly one native completion")
    }
  }
}
