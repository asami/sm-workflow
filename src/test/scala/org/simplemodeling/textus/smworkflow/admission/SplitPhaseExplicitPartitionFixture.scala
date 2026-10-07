package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E}
import SplitPhasePartitionEnumeration.EdgeDisposition

/** Test-only complete data, constructed through the real typed admission APIs.
  * Fixture names are explicit test references, not production host allocation.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[admission] object SplitPhaseExplicitPartitionFixture {
  final case class Base(source: P.SourceRecord, inventory: P.InventoryRecord,
    sourceBoundary: B.Boundary, edges: Vector[B.Edge], records: E.Records, initial: B.Validated)
  def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => throw new AssertionError(s"Explicit candidate fixture failed: $other")
  }
  def base(minutes: Vector[Int], dispositions: Vector[EdgeDisposition] = Vector.empty,
    overhead: Int = 0, sourceIdentity: String = "source"): Base = {
    val phase = P.PhaseIdentity("77")
    val ids = minutes.indices.map(i => P.WorkItemIdentity(s"item-$i")).toVector
    val source = P.SourceRecord(P.SourceReference(sourceIdentity, "1"), phase, P.AuthorityReference("authority", "1"))
    val inventory = P.InventoryRecord(P.InventoryReference("inventory", "1"), source.reference, phase, ids, Set(phase))
    val boundary = B.Boundary(B.BoundaryReference("boundary", "1"), source.reference, phase, source.authority,
      ids.map(id => B.Item(id, Vector(B.ClosureCriterion(s"close-${id.value}")), Vector.empty, source.authority)), Set.empty)
    val selectedEdges = if (dispositions.isEmpty) Vector.fill(ids.size - 1)(EdgeDisposition.Either) else dispositions
    val edges = ids.init.zip(selectedEdges).map((id, d) => B.Edge(id, d, source.authority))
    val children = ids.zipWithIndex.map((id, i) => P.Child(childPhase(i), Vector(id), 360, true))
    val initial = build(source, inventory, boundary, edges, children, "enumeration", Vector.empty, false)
    val records = E.Records(E.Estimates(E.EstimateReference("estimates", "1"), source.reference,
      ids.zip(minutes).map((id, value) => E.Item(id, E.Duration.Known(value)))), E.Policy(E.PolicyReference("policy", "1"), overhead, 4096))
    Base(source, inventory, boundary, edges, records, initial)
  }
  def enumerate(base: Base): E.Enumerated =
    take(E.enumerateC(take(E.loadC(base.initial, base.records, base.records)))).asInstanceOf[E.Enumerated]
  def proof(base: Base, candidate: E.Candidate, proposalIdentity: String,
    handoffPairs: Vector[(Int, Int)] = Vector.empty, finalOnly: Boolean = false,
    durationOverride: Option[Vector[Int]] = None): B.Validated = {
    val children = candidate.children.zipWithIndex.map((range, i) => P.Child(childPhase(i), range.ownedItems,
      durationOverride.fold(range.expectedMinutes)(_(i)), true))
    build(base.source, base.inventory, base.sourceBoundary, base.edges, children, proposalIdentity, handoffPairs, finalOnly)
  }
  def proofs(base: Base, enumerated: E.Enumerated): Vector[Option[B.Validated]] =
    enumerated.candidates.zipWithIndex.map((candidate, i) => Some(proof(base, candidate, s"candidate-$i")))
  private def childPhase(index: Int): P.PhaseIdentity = P.PhaseIdentity(if (index == 0) "77" else s"77.$index")
  private def build(source: P.SourceRecord, inventory: P.InventoryRecord, boundary: B.Boundary,
    edges: Vector[B.Edge], children: Vector[P.Child], proposalIdentity: String,
    handoffPairs: Vector[(Int, Int)], finalOnly: Boolean): B.Validated = {
    val proposal = P.ProposalRecord(P.ProposalReference(proposalIdentity, "1"), source.reference, source.phase, source.authority, children)
    val selected = P.Records(source, inventory, proposal)
    val shape = take(P.loadC(selected, selected)).asInstanceOf[P.ShapeReady]
    val phases = children.map(_.phase)
    val byItem = boundary.items.map(item => item.identity -> item.closureCriteria).toMap
    val ownership = B.Ownership(B.OwnershipReference(s"ownership-$proposalIdentity", "1"), source.reference, proposal.reference,
      if (finalOnly) B.Timing.FinalOnly else B.Timing.EachPhase, "sbt-test", "repository-driver", "inherit", phases.last,
      children.zipWithIndex.map((child, i) => B.ValidationChild(child.phase, B.RepositoryIdentity("repo"),
        child.ownedUnfinishedItems.flatMap(byItem(_)), if (finalOnly) phases.last else child.phase,
        if (finalOnly && i > 0) Vector(phases(i - 1)) else Vector.empty, phases.lift(i - 1), phases.lift(i + 1))))
    val handoffs = handoffPairs.zipWithIndex.map { case ((producer, receiver), i) =>
      B.Handoff(B.HandoffIdentity(s"handoff-$i"), phases(producer), phases(receiver), "review-result", "candidate", "review", "evidence",
        phases(producer), "candidate-changed", source.authority)
    }
    val plan = B.HandoffPlan(B.HandoffPlanReference(s"handoffs-$proposalIdentity", "1"), source.reference, proposal.reference,
      handoffs, phases.map(p => p -> handoffs.filter(_.receivingChild == p).map(_.identity)).toMap, edges)
    val records = B.Records(boundary, ownership, plan)
    take(B.loadC(shape, records, records))
  }
}
