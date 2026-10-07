package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence

/** Hash-free typed source/proposal loading boundary. Shape readiness is not
  * semantic admission, human consent, validation closure, PREVIEW or APPLY.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseExplicitProposalPrecheck {
  final case class SourceReference(identity: String, revision: String)
  final case class InventoryReference(identity: String, revision: String)
  final case class ProposalReference(identity: String, revision: String)
  final case class AuthorityReference(identity: String, revision: String)
  final case class PhaseIdentity(value: String)
  final case class WorkItemIdentity(value: String)
  final case class SourceRecord(reference: SourceReference, phase: PhaseIdentity, authority: AuthorityReference)
  final case class InventoryRecord(reference: InventoryReference, source: SourceReference,
    phase: PhaseIdentity, orderedUnfinishedItems: Vector[WorkItemIdentity], occupiedPhases: Set[PhaseIdentity])
  final case class Child(phase: PhaseIdentity, ownedUnfinishedItems: Vector[WorkItemIdentity],
    expectedMinutes: Int, independentlyClosable: Boolean)
  final case class ProposalRecord(reference: ProposalReference, source: SourceReference,
    phase: PhaseIdentity, authority: AuthorityReference, children: Vector[Child])
  final case class Records(source: SourceRecord, inventory: InventoryRecord, proposal: ProposalRecord)

  enum DecisionReason {
    case NestedOrUnknownPhaseNumbering, ChildPhaseIdentityCollision
  }
  sealed trait Outcome
  final class ShapeReady private[SplitPhaseExplicitProposalPrecheck] (val records: Records) extends Outcome
  final class DecisionRequired private[SplitPhaseExplicitProposalPrecheck] (
    val reason: DecisionReason, val records: Records
  ) extends Outcome

  /** Current records are independently selected by the caller's authorized
    * readers. Validate at this load/construction boundary only. No migration
    * from digest locators, newest-version lookup, locks or contention retries.
    */
  def loadC(frozen: Records, current: Records): Consequence[Outcome] =
    if (!complete(current)) Consequence.stateConflict("SplitPhase explicit proposal input is incomplete")
    else if (frozen != current) Consequence.stateConflict("SplitPhase explicit proposal complete current records differ")
    else {
      val source = current.source
      val inventory = current.inventory
      val proposal = current.proposal
      if (inventory.source != source.reference || inventory.phase != source.phase ||
          proposal.source != source.reference || proposal.phase != source.phase || proposal.authority != source.authority)
        Consequence.stateConflict("SplitPhase explicit proposal crosses its source or authority")
      else if (proposal.children.size < 2 || proposal.children.map(_.phase).distinct.size != proposal.children.size ||
          proposal.children.exists(c => c.ownedUnfinishedItems.isEmpty || c.expectedMinutes <= 0 ||
            c.expectedMinutes > 480 || !c.independentlyClosable) ||
          proposal.children.flatMap(_.ownedUnfinishedItems) != inventory.orderedUnfinishedItems)
        Consequence.stateConflict("SplitPhase explicit proposal is not a contiguous exact closable partition")
      else if (!source.phase.value.matches("[0-9]+"))
        Consequence.success(new DecisionRequired(DecisionReason.NestedOrUnknownPhaseNumbering, current))
      else {
        val expected = Vector(source.phase) ++ (1 until proposal.children.size).map(i => PhaseIdentity(s"${source.phase.value}.$i"))
        if (proposal.children.map(_.phase) != expected)
          Consequence.stateConflict("SplitPhase explicit child identities do not follow source suffix order")
        else if (proposal.children.tail.exists(c => inventory.occupiedPhases.contains(c.phase)))
          Consequence.success(new DecisionRequired(DecisionReason.ChildPhaseIdentityCollision, current))
        else Consequence.success(new ShapeReady(current))
      }
    }

  private def complete(r: Records): Boolean =
    r != null && r.source != null && r.source.reference != null &&
      name(r.source.reference.identity) && name(r.source.reference.revision) && phase(r.source.phase) &&
      authority(r.source.authority) && r.inventory != null && r.inventory.reference != null &&
      name(r.inventory.reference.identity) && name(r.inventory.reference.revision) && r.inventory.source != null &&
      name(r.inventory.source.identity) && name(r.inventory.source.revision) && phase(r.inventory.phase) &&
      r.inventory.orderedUnfinishedItems != null && r.inventory.orderedUnfinishedItems.nonEmpty &&
      r.inventory.orderedUnfinishedItems.forall(work) &&
      r.inventory.orderedUnfinishedItems.distinct == r.inventory.orderedUnfinishedItems &&
      r.inventory.occupiedPhases != null && r.inventory.occupiedPhases.forall(phase) &&
      r.proposal != null && r.proposal.reference != null && name(r.proposal.reference.identity) &&
      name(r.proposal.reference.revision) && r.proposal.source != null && name(r.proposal.source.identity) &&
      name(r.proposal.source.revision) && phase(r.proposal.phase) && authority(r.proposal.authority) &&
      r.proposal.children != null && r.proposal.children.forall(c => c != null && phase(c.phase) &&
        c.ownedUnfinishedItems != null && c.ownedUnfinishedItems.forall(work))
  private def authority(value: AuthorityReference): Boolean = value != null && name(value.identity) && name(value.revision)
  private def phase(value: PhaseIdentity): Boolean = value != null && name(value.value)
  private def work(value: WorkItemIdentity): Boolean = value != null && name(value.value)
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
