package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.{ProposalDraft, digestOf}

/** Validates authority-explicit semantic handoffs only. Unknown edges require
  * a separate semantic result and cannot be inferred here.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseSemanticHandoff {
  final case class Handoff(
    identity: String,
    producingChild: String,
    receivingChild: String,
    kind: String,
    input: String,
    action: String,
    output: String,
    owner: String,
    invalidationReason: String,
    authorityReference: String
  )

  final case class Plan(
    sourceSnapshotDigest: String,
    proposalDigest: String,
    handoffs: Vector[Handoff],
    incomingHandoffIdsByChild: Map[String, Vector[String]]
  )

  final case class Validated(planDigest: String)

  def validateC(draft: ProposalDraft, plan: Plan): Consequence[Validated] =
    if (!_complete(draft, plan))
      Consequence.stateConflict("SplitPhase semantic handoff declaration is incomplete")
    else if (plan.sourceSnapshotDigest != draft.sourceSnapshotDigest ||
        plan.proposalDigest != digestOf(draft))
      Consequence.stateConflict("SplitPhase semantic handoff differs from proposal evidence")
    else {
      val identities = draft.children.map(_.identity)
      val actualIncoming = plan.handoffs.groupBy(_.receivingChild)
        .view.mapValues(_.map(_.identity)).toMap
      val declaredIncoming = plan.incomingHandoffIdsByChild
        .filter { case (_, ids) => ids.nonEmpty }
      if (identities.distinct.size != identities.size ||
          plan.handoffs.map(_.identity).distinct.size != plan.handoffs.size ||
          plan.incomingHandoffIdsByChild.keySet != identities.toSet ||
          plan.incomingHandoffIdsByChild.values.exists(ids => ids.distinct != ids) ||
          actualIncoming != declaredIncoming ||
          plan.handoffs.exists(handoff =>
            !identities.contains(handoff.producingChild) ||
              !identities.contains(handoff.receivingChild) ||
              identities.indexOf(handoff.producingChild) >=
                identities.indexOf(handoff.receivingChild) ||
              handoff.owner != handoff.producingChild))
        Consequence.stateConflict("SplitPhase semantic handoff ownership or receipt is inconsistent")
      else Consequence.success(Validated(_digest(plan)))
    }

  private def _complete(draft: ProposalDraft, plan: Plan): Boolean =
    draft != null && _sha(draft.sourceSnapshotDigest) && draft.children != null &&
      draft.children.size >= 2 && draft.children.forall(child =>
        child != null && _name(child.identity) && child.ownedUnfinishedItems != null) &&
      plan != null && _sha(plan.sourceSnapshotDigest) && _sha(plan.proposalDigest) &&
      plan.handoffs != null && plan.handoffs.forall(handoff =>
        handoff != null && _name(handoff.identity) && _name(handoff.producingChild) &&
          _name(handoff.receivingChild) && _name(handoff.kind) &&
          _name(handoff.input) && _name(handoff.action) && _name(handoff.output) &&
          _name(handoff.owner) && _name(handoff.invalidationReason) &&
          _name(handoff.authorityReference)) &&
      plan.incomingHandoffIdsByChild != null &&
      plan.incomingHandoffIdsByChild.forall { case (child, ids) =>
        _name(child) && ids != null && ids.forall(_name)
      }

  private def _digest(plan: Plan): String = {
    val fields = Vector(plan.sourceSnapshotDigest, plan.proposalDigest,
      plan.handoffs.size.toString) ++ plan.handoffs.flatMap(h => Vector(
      h.identity, h.producingChild, h.receivingChild, h.kind, h.input,
      h.action, h.output, h.owner, h.invalidationReason, h.authorityReference)) ++
      plan.incomingHandoffIdsByChild.toVector.sortBy(_._1).flatMap { case (child, ids) =>
        Vector(child, ids.size.toString) ++ ids
      }
    val bytes = fields.map(s => s"${s.length}:$s").mkString.getBytes(StandardCharsets.UTF_8)
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
  }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
  private def _sha(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")
}
