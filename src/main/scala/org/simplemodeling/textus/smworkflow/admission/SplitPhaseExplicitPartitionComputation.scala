package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import SplitPhaseExplicitProposalPrecheck.{SourceReference, WorkItemIdentity}
import SplitPhaseExplicitBoundaryValidation.Validated
import SplitPhasePartitionEnumeration.EdgeDisposition

/** Pure computation after one typed load boundary. Results are computed state,
  * not digest witnesses, caller assertions or permission to adopt a partition.
  *
  * @since Oct.  2, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseExplicitPartitionComputation {
  final case class EstimateReference(identity: String, revision: String)
  final case class PolicyReference(identity: String, revision: String)
  enum Duration {
    case Known(minutes: Int)
    case Unresolved(reason: String)
  }
  final case class Item(identity: WorkItemIdentity, duration: Duration)
  final case class Estimates(reference: EstimateReference, source: SourceReference, items: Vector[Item])
  final case class Policy(reference: PolicyReference, phaseOverheadMinutes: Int, maximumCombinations: Int)
  final case class Records(estimates: Estimates, policy: Policy)
  final class Input private[SplitPhaseExplicitPartitionComputation] (
    val boundary: Validated, val records: Records
  )

  /** Trusted projection from validated semantic coverage, preserving known
    * durations and the already constructed policy without redundant load checks.
    */
  def resolvePartitionSemantic(inputs: SplitPhaseExplicitPartitionSemantic.Admitted): Input = {
    val original = inputs.current.partition
    val estimates = original.records.estimates.copy(
      reference = inputs.references.estimates.getOrElse(original.records.estimates.reference), items = inputs.resolvedItems)
    new Input(SplitPhaseExplicitBoundaryValidation.resolvePartitionSemantic(inputs), original.records.copy(estimates = estimates))
  }
  final case class ChildRange(firstIndex: Int, endExclusive: Int,
    ownedItems: Vector[WorkItemIdentity], expectedMinutes: Int)
  final class Candidate private[SplitPhaseExplicitPartitionComputation] (
    val cutAfterIndices: Vector[Int], val children: Vector[ChildRange]
  )
  final case class UnresolvedItem(identity: WorkItemIdentity, reason: String)
  enum NoCandidateReason { case NoAuthorizedSplitBoundary, EverySplitExceedsEightHours }
  sealed trait Outcome { def input: Input }
  final class Enumerated private[SplitPhaseExplicitPartitionComputation] (
    val input: Input, val evaluatedCount: Int, val rejectedByCeiling: Int, val candidates: Vector[Candidate]
  ) extends Outcome
  /** Retained executable data remains inseparable from actual native provenance. */
  final class RetainedScope private[SplitPhaseExplicitPartitionComputation] (
    val source: SplitPhaseManagedScopeSemantic.CompletedSource, val enumerated: Enumerated)
  final class RetainedReasoning private[SplitPhaseExplicitPartitionComputation] (
    val source: SplitPhaseRetainedReasoningSource.Source, val enumerated: Enumerated)
  final class SemanticRequired private[SplitPhaseExplicitPartitionComputation] (
    val input: Input, val unresolvedItems: Vector[UnresolvedItem], val unresolvedAfterItems: Vector[WorkItemIdentity]
  ) extends Outcome
  final class NoAdmissible private[SplitPhaseExplicitPartitionComputation] (
    val input: Input, val reason: NoCandidateReason, val evaluatedCount: Int, val rejectedByCeiling: Int
  ) extends Outcome
  final case class Score(distanceFromSixHoursMinutes: Long, subFiveHourChildren: Int)
  final case class Ranked(candidate: Candidate, score: Score)
  final class Priority private[SplitPhaseExplicitPartitionComputation] (
    val enumerated: Enumerated, val ranked: Vector[Ranked], val durationLeaders: Vector[Candidate]
  )
  private final case class NumericItem(identity: WorkItemIdentity, minutes: Int)

  /** The caller supplies independently selected current data through authorized
    * readers. This is data admission, not authority, consent or calibration proof.
    */
  def loadC(boundary: Validated, frozen: Records, current: Records): Consequence[Input] =
    if (boundary == null || !complete(current))
      Consequence.stateConflict("SplitPhase explicit partition data is incomplete")
    else if (frozen != current)
      Consequence.stateConflict("SplitPhase explicit partition complete current records differ")
    else if (current.estimates.source != boundary.records.boundary.source ||
        current.estimates.items.map(_.identity) != boundary.records.boundary.items.map(_.identity))
      Consequence.stateConflict("SplitPhase explicit partition source or estimate coverage differs")
    else Consequence.success(new Input(boundary, current))

  /** Restore cached cuts/ranges/counts, never enumerate or allocate again.
    * Effective first-root facts include any earlier partition-AI enrichment.
    */
  def restoreScopeC(source: SplitPhaseManagedScopeSemantic.CompletedSource): Consequence[RetainedScope] = {
    import SplitPhaseExplicitProposalPrecheck as P
    import SplitPhaseExplicitBoundaryValidation as B
    import SplitPhasePlanningSnapshot as R
    if (source == null) Consequence.stateConflict("SplitPhase retained scope requires actual completed native source")
    else {
      val snapshot = source.source.record.snapshot
      for {
        disposition <- R.dispositionC(snapshot)
        _ <- if (disposition == R.Disposition.ProjectionRequired) Consequence.unit
          else Consequence.stateConflict("SplitPhase retained scope first frontier is not a projection requirement")
        enumerated <- _restore_enumerated_c(snapshot)
      } yield new RetainedScope(source, enumerated)
    }
  }

  /** The source can only come from the actual owning native observation. */
  def restoreReasoningC(source: SplitPhaseRetainedReasoningSource.Source): Consequence[RetainedReasoning] =
    if (source == null) Consequence.stateConflict("SplitPhase retained reasoning requires actual native source")
    else _restore_enumerated_c(source.snapshot).map(new RetainedReasoning(source, _))

  private def _restore_enumerated_c(snapshot: SplitPhasePlanningSnapshot.Record): Consequence[Enumerated] = {
    import SplitPhaseExplicitProposalPrecheck as P
    import SplitPhaseExplicitBoundaryValidation as B
    import SplitPhasePlanningSnapshot as R
    val facts = snapshot.selection
    for {
        shape <- P.loadC(facts.proposal, facts.proposal).flatMap {
          case ready: P.ShapeReady => Consequence.success(ready)
          case _ => Consequence.stateConflict("SplitPhase retained scope original shape no longer loads")
        }
        boundary <- B.loadC(shape, facts.boundary, facts.boundary)
        input <- loadC(boundary, facts.partition, facts.partition)
        cached <- snapshot.partition match {
          case value: R.PartitionView.Enumerated => Consequence.success(value)
          case _ => Consequence.stateConflict("SplitPhase retained scope has no cached enumeration")
        }
        numeric = input.records.estimates.items.collect {
          case Item(identity, Duration.Known(minutes)) => NumericItem(identity, minutes)
        }
        edges = input.boundary.records.handoffs.edges
        _ <- if (numeric.size == input.records.estimates.items.size && cached.candidates.forall { value =>
            val cuts = value.key.cutAfterIndices
            val legal = edges.zipWithIndex.forall { case (edge, index) => edge.disposition match {
              case EdgeDisposition.CutOnly => cuts.contains(index)
              case EdgeDisposition.CrossOnly => !cuts.contains(index)
              case EdgeDisposition.Either => true
              case EdgeDisposition.Unresolved => false
            }}
            legal && children(numeric, cuts, input.records.policy.phaseOverheadMinutes).contains(value.children)
          }) Consequence.unit
          else Consequence.stateConflict("SplitPhase retained scope cached ranges differ from effective known facts")
        enumerated = new Enumerated(input, cached.evaluatedCount, cached.rejectedByCeiling,
          cached.candidates.map(value => new Candidate(value.key.cutAfterIndices, value.children)))
    } yield enumerated
  }

  /** No shape polling/revalidation: Input and boundary are already constructed.
    * Unknown evidence is never converted into an estimate or authorized cut.
    */
  def enumerateC(input: Input): Consequence[Outcome] = {
    val items = input.records.estimates.items
    val edges = input.boundary.records.handoffs.edges
    val policy = input.records.policy
    val unresolvedItems = items.collect { case Item(identity, Duration.Unresolved(reason)) => UnresolvedItem(identity, reason) }
    val unresolvedEdges = edges.collect { case edge if edge.disposition == EdgeDisposition.Unresolved => edge.afterItem }
    if (unresolvedItems.nonEmpty || unresolvedEdges.nonEmpty)
      Consequence.success(new SemanticRequired(input, unresolvedItems, unresolvedEdges))
    else {
      val numericItems = items.collect { case Item(identity, Duration.Known(minutes)) => NumericItem(identity, minutes) }
      val combinations = edges.foldLeft(BigInt(1))((count, edge) =>
        count * (if (edge.disposition == EdgeDisposition.Either) 2 else 1))
      if (combinations > policy.maximumCombinations)
        Consequence.stateConflict("SplitPhase explicit partition enumeration exceeds policy limit")
      else {
        val cutSets = edges.zipWithIndex.foldLeft(Vector(Vector.empty[Int])) { case (current, (edge, index)) =>
          edge.disposition match {
            case EdgeDisposition.CutOnly => current.map(_ :+ index)
            case EdgeDisposition.CrossOnly => current
            case EdgeDisposition.Either => current.flatMap(cuts => Vector(cuts, cuts :+ index))
            case EdgeDisposition.Unresolved => current // excluded by the semantic outcome above
          }
        }.filter(_.nonEmpty).sortWith((left, right) =>
          if (left.size != right.size) left.size < right.size else lexLess(left, right))
        if (cutSets.isEmpty)
          Consequence.success(new NoAdmissible(input, NoCandidateReason.NoAuthorizedSplitBoundary, 0, 0))
        else {
          val evaluated = cutSets.map(cuts => cuts -> children(numericItems, cuts, policy.phaseOverheadMinutes))
          val feasible = evaluated.collect { case (cuts, Some(ranges)) => new Candidate(cuts, ranges) }
          val rejected = evaluated.count(_._2.isEmpty)
          if (feasible.isEmpty)
            Consequence.success(new NoAdmissible(input, NoCandidateReason.EverySplitExceedsEightHours, evaluated.size, rejected))
          else Consequence.success(new Enumerated(input, evaluated.size, rejected, feasible))
        }
      }
    }
  }

  /** Only constructed enumeration can be ranked. This duration-visible prefix
    * cannot assert any candidate's semantic, closure or short-child admissibility.
    */
  def rankC(enumerated: Enumerated): Consequence[Priority] = {
    val ranked = enumerated.candidates.map(candidate => Ranked(candidate,
      Score(candidate.children.map(c => math.abs(c.expectedMinutes.toLong - 360L)).sum,
        candidate.children.count(_.expectedMinutes < 300)))).sortWith { (left, right) =>
      if (left.score.distanceFromSixHoursMinutes != right.score.distanceFromSixHoursMinutes)
        left.score.distanceFromSixHoursMinutes < right.score.distanceFromSixHoursMinutes
      else if (left.score.subFiveHourChildren != right.score.subFiveHourChildren)
        left.score.subFiveHourChildren < right.score.subFiveHourChildren
      else lexLess(left.candidate.cutAfterIndices, right.candidate.cutAfterIndices)
    }
    val best = ranked.head.score
    Consequence.success(new Priority(enumerated, ranked, ranked.takeWhile(_.score == best).map(_.candidate)))
  }

  private def children(items: Vector[NumericItem], cuts: Vector[Int], overhead: Int): Option[Vector[ChildRange]] = {
    val ends = cuts.map(_ + 1) :+ items.size
    val starts = Vector(0) ++ ends.init
    val ranges = starts.zip(ends).map { case (start, end) =>
      val owned = items.slice(start, end)
      val minutes = owned.map(_.minutes.toLong).sum + overhead.toLong
      (start, end, owned.map(_.identity), minutes)
    }
    if (ranges.exists(_._4 > 480L)) None
    else Some(ranges.map { case (start, end, owned, minutes) => ChildRange(start, end, owned, minutes.toInt) })
  }
  private def complete(r: Records): Boolean = r != null && r.estimates != null && r.policy != null && {
    val e = r.estimates
    val p = r.policy
    e.reference != null && name(e.reference.identity) && name(e.reference.revision) &&
      e.source != null && name(e.source.identity) && name(e.source.revision) &&
      e.items != null && e.items.size >= 2 && e.items.size <= 128 &&
      e.items.forall(item => item != null && item.identity != null && name(item.identity.value) && (item.duration match {
        case Duration.Known(minutes) => minutes > 0
        case Duration.Unresolved(reason) => name(reason)
        case null => false
      })) && p.reference != null && name(p.reference.identity) && name(p.reference.revision) &&
      p.phaseOverheadMinutes >= 0 && p.phaseOverheadMinutes < 480 &&
      p.maximumCombinations >= 1 && p.maximumCombinations <= 4096
  }
  private def lexLess(left: Vector[Int], right: Vector[Int]): Boolean =
    left.zip(right).find((a, b) => a != b) match {
      case Some((a, b)) => a < b
      case None => left.size < right.size
    }
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
