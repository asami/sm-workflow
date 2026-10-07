package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import SplitPhaseExplicitProposalPrecheck.{ShapeReady, SourceReference, ProposalReference, AuthorityReference, PhaseIdentity, WorkItemIdentity}
import SplitPhasePartitionEnumeration.EdgeDisposition

/** Typed closure/ownership/handoff construction following shape readiness.
  * No hash witnesses, duplicated shape checks, locks or claim protocols.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseExplicitBoundaryValidation {
  final case class BoundaryReference(identity: String, revision: String)
  final case class OwnershipReference(identity: String, revision: String)
  final case class HandoffPlanReference(identity: String, revision: String)
  final case class HandoffIdentity(value: String)
  final case class RepositoryIdentity(value: String)
  final case class ClosureCriterion(value: String)
  final case class Item(identity: WorkItemIdentity, closureCriteria: Vector[ClosureCriterion],
    prerequisiteItems: Vector[WorkItemIdentity], authority: AuthorityReference)
  final case class Boundary(reference: BoundaryReference, source: SourceReference, phase: PhaseIdentity,
    authority: AuthorityReference, items: Vector[Item], authorizedHandoffAuthorities: Set[AuthorityReference])
  enum Timing { case FinalOnly, EachPhase }
  final case class ValidationChild(phase: PhaseIdentity, repository: RepositoryIdentity,
    closureCriteria: Vector[ClosureCriterion], validationOwner: PhaseIdentity, dependencies: Vector[PhaseIdentity],
    predecessor: Option[PhaseIdentity], successor: Option[PhaseIdentity])
  final case class Ownership(reference: OwnershipReference, source: SourceReference, proposal: ProposalReference,
    timing: Timing, method: String, driver: String, bootstrapPolicy: String,
    finalValidationOwner: PhaseIdentity, children: Vector[ValidationChild])
  final case class Handoff(identity: HandoffIdentity, producingChild: PhaseIdentity, receivingChild: PhaseIdentity,
    kind: String, input: String, action: String, output: String, owner: PhaseIdentity,
    invalidationReason: String, authority: AuthorityReference)
  final case class Edge(afterItem: WorkItemIdentity, disposition: EdgeDisposition, authority: AuthorityReference)
  final case class HandoffPlan(reference: HandoffPlanReference, source: SourceReference, proposal: ProposalReference,
    handoffs: Vector[Handoff], incomingByChild: Map[PhaseIdentity, Vector[HandoffIdentity]], edges: Vector[Edge])
  final case class Records(boundary: Boundary, ownership: Ownership, handoffs: HandoffPlan)
  final class Validated private[SplitPhaseExplicitBoundaryValidation] (
    val shape: ShapeReady, val records: Records
  )

  /** Only a privately admitted semantic result may replace unknown dispositions.
    * All shape/closure/ownership facts and edge authorities are preserved; their
    * construction invariants are not checked a second time here.
    */
  def resolvePartitionSemantic(inputs: SplitPhaseExplicitPartitionSemantic.Admitted): Validated = {
    val original = inputs.current.partition.boundary
    val plan = original.records.handoffs.copy(
      reference = inputs.references.handoffs.getOrElse(original.records.handoffs.reference), edges = inputs.resolvedEdges)
    new Validated(original.shape, original.records.copy(handoffs = plan))
  }

  /** Natural loading boundary. Caller selects current complete records through
    * authorized readers. A constructed result proves these application rules,
    * not human consent, a framework grant or complete proposal admission.
    */
  def loadC(shape: ShapeReady, frozen: Records, current: Records): Consequence[Validated] =
    if (shape == null || !complete(current)) Consequence.stateConflict("SplitPhase explicit boundary data is incomplete")
    else if (frozen != current) Consequence.stateConflict("SplitPhase explicit boundary complete current records differ")
    else {
      val selected = shape.records
      val boundary = current.boundary
      val ownership = current.ownership
      val handoffs = current.handoffs
      val identities = selected.proposal.children.map(_.phase)
      val sourceItems = boundary.items.map(_.identity)
      val criteria = boundary.items.flatMap(_.closureCriteria)
      val expectedIncoming = handoffs.handoffs.groupBy(_.receivingChild).view.mapValues(_.map(_.identity)).toMap
      val declaredIncoming = handoffs.incomingByChild.filter { case (_, ids) => ids.nonEmpty }
      val permitted = boundary.authorizedHandoffAuthorities + boundary.authority
      if (boundary.source != selected.source.reference || boundary.phase != selected.source.phase ||
          boundary.authority != selected.source.authority || ownership.source != selected.source.reference ||
          ownership.proposal != selected.proposal.reference || handoffs.source != selected.source.reference ||
          handoffs.proposal != selected.proposal.reference)
        Consequence.stateConflict("SplitPhase explicit boundary crosses source/proposal/authority references")
      else if (ownership.children.map(_.phase) != identities || ownership.finalValidationOwner != identities.last ||
          ownership.children.exists(c => c.dependencies.distinct != c.dependencies) ||
          ownership.children.zipWithIndex.exists { case (child, index) =>
            child.predecessor != identities.lift(index - 1) || child.successor != identities.lift(index + 1) ||
              child.dependencies.exists(d => !identities.take(index).contains(d))
          }) Consequence.stateConflict("SplitPhase explicit validation dependency chain is inconsistent")
      else if (!timingMatches(ownership, identities))
        Consequence.stateConflict("SplitPhase explicit validation timing has no admissible owner/repository chain")
      else if (handoffs.handoffs.map(_.identity).distinct.size != handoffs.handoffs.size ||
          handoffs.incomingByChild.keySet != identities.toSet || expectedIncoming != declaredIncoming ||
          handoffs.handoffs.exists(h => !identities.contains(h.producingChild) || !identities.contains(h.receivingChild) ||
            identities.indexOf(h.producingChild) >= identities.indexOf(h.receivingChild) || h.owner != h.producingChild))
        Consequence.stateConflict("SplitPhase explicit handoff ownership or receipt is inconsistent")
      else if (sourceItems != selected.inventory.orderedUnfinishedItems || criteria.distinct != criteria)
        Consequence.stateConflict("SplitPhase explicit source closure coverage is inconsistent")
      else {
        val itemByIdentity = boundary.items.map(i => i.identity -> i).toMap
        val closureMatches = selected.proposal.children.zip(ownership.children).forall { case (child, declared) =>
          declared.closureCriteria == child.ownedUnfinishedItems.flatMap(itemByIdentity(_).closureCriteria)
        }
        // ShapeReady already guarantees contiguous exact ownership. Strictly
        // earlier source prerequisites therefore close within this or a prior child.
        val prerequisitesMatch = boundary.items.forall(item => item.prerequisiteItems.forall(p =>
          sourceItems.contains(p) && sourceItems.indexOf(p) < sourceItems.indexOf(item.identity)))
        val authorityMatches = boundary.items.forall(i => permitted.contains(i.authority)) &&
          handoffs.handoffs.forall(h => permitted.contains(h.authority)) &&
          handoffs.edges.map(_.afterItem) == sourceItems.init && handoffs.edges.forall(e => permitted.contains(e.authority))
        if (!closureMatches || !prerequisitesMatch || !authorityMatches)
          Consequence.stateConflict("SplitPhase explicit closure or authority differs from source declarations")
        else Consequence.success(new Validated(shape, current))
      }
    }

  private def timingMatches(plan: Ownership, identities: Vector[PhaseIdentity]): Boolean = plan.timing match {
    case Timing.FinalOnly => plan.children.map(_.repository).distinct.size == 1 &&
      plan.children.forall(_.validationOwner == identities.last) &&
      plan.children.zipWithIndex.forall { case (child, index) => index == 0 || child.dependencies.contains(identities(index - 1)) }
    case Timing.EachPhase => plan.children.forall(c => c.validationOwner == c.phase)
  }
  private def complete(r: Records): Boolean = r != null && r.boundary != null && r.ownership != null && r.handoffs != null && {
    val b = r.boundary
    val o = r.ownership
    val h = r.handoffs
    b.reference != null && name(b.reference.identity) && name(b.reference.revision) && source(b.source) && phase(b.phase) &&
      authority(b.authority) && b.items != null && b.items.nonEmpty && b.items.forall(i => i != null && work(i.identity) &&
        i.closureCriteria != null && i.closureCriteria.nonEmpty && i.closureCriteria.forall(criterion) &&
        i.prerequisiteItems != null && i.prerequisiteItems.forall(work) &&
        i.prerequisiteItems.distinct == i.prerequisiteItems && authority(i.authority)) &&
      b.authorizedHandoffAuthorities != null && b.authorizedHandoffAuthorities.forall(authority) &&
      o.reference != null && name(o.reference.identity) && name(o.reference.revision) && source(o.source) && proposal(o.proposal) &&
      o.timing != null && name(o.method) && name(o.driver) && name(o.bootstrapPolicy) && phase(o.finalValidationOwner) &&
      o.children != null && o.children.forall(c => c != null && phase(c.phase) && c.repository != null && name(c.repository.value) &&
        c.closureCriteria != null && c.closureCriteria.forall(criterion) && phase(c.validationOwner) && c.dependencies != null &&
        c.dependencies.forall(phase) && c.predecessor != null && c.predecessor.forall(phase) && c.successor != null && c.successor.forall(phase)) &&
      h.reference != null && name(h.reference.identity) && name(h.reference.revision) && source(h.source) && proposal(h.proposal) &&
      h.handoffs != null && h.handoffs.forall(v => v != null && handoff(v.identity) && phase(v.producingChild) &&
        phase(v.receivingChild) && name(v.kind) && name(v.input) && name(v.action) && name(v.output) && phase(v.owner) &&
        name(v.invalidationReason) && authority(v.authority)) && h.incomingByChild != null &&
      h.incomingByChild.forall((key, values) => phase(key) && values != null && values.forall(handoff)) &&
      h.edges != null && h.edges.forall(e => e != null && work(e.afterItem) && e.disposition != null && authority(e.authority))
  }
  private def source(v: SourceReference): Boolean = v != null && name(v.identity) && name(v.revision)
  private def proposal(v: ProposalReference): Boolean = v != null && name(v.identity) && name(v.revision)
  private def authority(v: AuthorityReference): Boolean = v != null && name(v.identity) && name(v.revision)
  private def phase(v: PhaseIdentity): Boolean = v != null && name(v.value)
  private def work(v: WorkItemIdentity): Boolean = v != null && name(v.value)
  private def handoff(v: HandoffIdentity): Boolean = v != null && name(v.value)
  private def criterion(v: ClosureCriterion): Boolean = v != null && name(v.value)
  private def name(v: String): Boolean = v != null && v.nonEmpty && v.trim == v
}
