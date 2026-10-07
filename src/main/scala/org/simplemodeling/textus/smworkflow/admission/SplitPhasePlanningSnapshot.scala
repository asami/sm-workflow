package org.simplemodeling.textus.smworkflow.admission

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitCandidateProjection as Q, SplitPhaseExplicitCandidateAdmissibility as A,
  SplitPhaseExplicitObjectiveSelection as O, SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseExplicitPartitionContext as C}

/** Passive computation data. Neither decoding nor shape inspection admits an
  * executable selection, current state, receipt, grant or human invocation.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhasePlanningSnapshot {
  final case class CandidateKey(cutAfterIndices: Vector[Int])
  final case class CandidateView(key: CandidateKey, children: Vector[E.ChildRange])
  enum PartitionView {
    case Enumerated(evaluatedCount: Int, rejectedByCeiling: Int, candidates: Vector[CandidateView])
    case SemanticRequired(items: Vector[E.UnresolvedItem], afterItems: Vector[P.WorkItemIdentity])
    case NoAdmissible(reason: E.NoCandidateReason, evaluatedCount: Int, rejectedByCeiling: Int)
  }
  final case class NumberingView(reason: P.DecisionReason, records: P.Records)
  sealed trait CandidateProjectionView { def key: CandidateKey }
  object CandidateProjectionView {
    final case class BuiltView(key: CandidateKey, proposal: P.ProposalRecord, ownership: B.Ownership,
      handoffs: B.HandoffPlan, localHandoffs: Vector[Q.LocalHandoff]) extends CandidateProjectionView
    final case class SemanticView(key: CandidateKey, gaps: Vector[Q.ScopeGap]) extends CandidateProjectionView
    final case class DecisionView(key: CandidateKey, reason: Q.DecisionReason,
      repositories: Vector[Q.RepositoryIssue], numbering: Option[NumberingView]) extends CandidateProjectionView
  }
  sealed trait ProjectionView { def candidates: Vector[CandidateProjectionView] }
  object ProjectionView {
    final case class Projected(candidates: Vector[CandidateProjectionView.BuiltView]) extends ProjectionView
    final case class Requirements(candidates: Vector[CandidateProjectionView], scopes: Vector[W.ScopeWork],
      decisionKeys: Vector[CandidateKey]) extends ProjectionView
  }
  final case class ShortReviewView(rebalanceAttempts: Vector[A.RebalanceAttempt],
    mergeAttempts: Vector[A.MergeAttempt], rejection: Option[A.Rejection])
  final case class AssessmentView(key: CandidateKey, boundaryProof: Option[CandidateKey],
    shortReview: Option[ShortReviewView], status: A.Status)
  final case class RankedView(key: CandidateKey, score: E.Score)
  sealed trait AdmissionView { def assessments: Vector[AssessmentView]; def rejectedKeys: Vector[CandidateKey] }
  object AdmissionView {
    final case class EvidenceRequired(assessments: Vector[AssessmentView], pendingKeys: Vector[CandidateKey],
      rejectedKeys: Vector[CandidateKey]) extends AdmissionView
    final case class NoAdmissible(assessments: Vector[AssessmentView], rejectedKeys: Vector[CandidateKey]) extends AdmissionView
    final case class DurationRanked(assessments: Vector[AssessmentView], eligible: Vector[RankedView],
      durationLeaderKeys: Vector[CandidateKey], rejectedKeys: Vector[CandidateKey]) extends AdmissionView
  }
  final case class CostScoreView(key: CandidateKey, potentialSavingUnits: String, materialSavingUnits: String)
  final case class OverheadScoreView(key: CandidateKey, childCount: Int, handoffCount: Int,
    validationRunCount: Int, totalUnits: String)
  enum ObjectiveView {
    case RecordRequired(required: O.RequiredRecord, leaderKeys: Vector[CandidateKey], costScores: Vector[CostScoreView])
    case ReasoningDemandRequired(items: Vector[O.UnresolvedItem], leaderKeys: Vector[CandidateKey])
    case Selected(key: CandidateKey, stage: O.Stage, costScores: Vector[CostScoreView], overheadScores: Vector[OverheadScoreView])
    case HumanDecisionRequired(leaderKeys: Vector[CandidateKey], authorities: Vector[O.AuthorityProjection],
      costScores: Vector[CostScoreView], overheadScores: Vector[OverheadScoreView])
  }
  final case class Record(selection: C.Facts, partition: PartitionView, projection: Option[ProjectionView],
    admission: Option[AdmissionView], objective: Option[ObjectiveView])
  enum Disposition {
    case PartitionSemanticRequired, AllocationRequired, PartitionUnavailable, ProjectionRequired,
      CandidatesUnavailable, HostEvidenceRequired, ObjectiveRecordRequired, ReasoningRequired,
      HumanDecisionRequired, Planned
  }
  final class Projected private[SplitPhasePlanningSnapshot] (val record: Record, val disposition: Disposition)

  /** Copy the already computed whole frontier. No planning processor is invoked. */
  def projectC(outcome: W.Outcome): Consequence[Projected] = guarded {
    if (outcome == null) invalid("constructed outcome is absent")
    else {
      val selected = outcome.selection
      val boundary = selected.partition.boundary
      val facts = C.Facts(boundary.shape.records, boundary.records, selected.partition.records, selected.data)
      val trace = outcome.trace
      val partition = trace.partition match {
        case e: E.Enumerated => PartitionView.Enumerated(e.evaluatedCount, e.rejectedByCeiling,
          e.candidates.map(c => CandidateView(key(c), c.children)))
        case e: E.SemanticRequired => PartitionView.SemanticRequired(e.unresolvedItems, e.unresolvedAfterItems)
        case e: E.NoAdmissible => PartitionView.NoAdmissible(e.reason, e.evaluatedCount, e.rejectedByCeiling)
      }
      val projection = trace.projection.map {
        case q: Q.Projected => ProjectionView.Projected(q.candidates.map(built))
        case q: Q.Requirements =>
          val need = outcome.asInstanceOf[W.ProjectionRequired]
          ProjectionView.Requirements(q.candidates.map {
            case b: Q.Built => built(b)
            case s: Q.SemanticRequired => CandidateProjectionView.SemanticView(key(s.candidate), s.gaps)
            case d: Q.DecisionRequired => CandidateProjectionView.DecisionView(key(d.candidate), d.reason,
              d.repositories, d.numbering.map(n => NumberingView(n.reason, n.records)))
          }, need.scopes, need.decisions.map(d => key(d.candidate)))
      }
      val documents = projection.toVector.flatMap(_.candidates).collect {
        case b: CandidateProjectionView.BuiltView => b.key -> b
      }.toMap
      val admission = trace.admission.map { a =>
        val assessments = a.assessments.map { assessment =>
          val k = key(assessment.candidate)
          require(assessment.boundary.forall(b => matches(b, facts, documents(k))), "boundary proof differs")
          AssessmentView(k, assessment.boundary.map(_ => k), assessment.shortReview.map(s =>
            ShortReviewView(s.rebalanceAttempts, s.mergeAttempts, s.rejection)), assessment.status)
        }
        a match {
          case v: A.EvidenceRequired => AdmissionView.EvidenceRequired(assessments,
            v.pendingCandidates.map(key), v.rejectedCandidates.map(key))
          case v: A.NoAdmissible => AdmissionView.NoAdmissible(assessments, v.rejectedCandidates.map(key))
          case v: A.DurationRanked => AdmissionView.DurationRanked(assessments,
            v.eligible.map(r => RankedView(key(r.candidate), r.score)), v.durationLeaders.map(key), v.rejectedCandidates.map(key))
        }
      }
      val objective = trace.objective.map {
        case v: O.RecordRequired => ObjectiveView.RecordRequired(v.required, v.leaders.map(key), costs(v.costScores))
        case v: O.ReasoningDemandRequired => ObjectiveView.ReasoningDemandRequired(v.unresolvedItems, v.leaders.map(key))
        case v: O.Selected =>
          require(matches(v.boundary, facts, documents(key(v.candidate))), "selected boundary differs")
          ObjectiveView.Selected(key(v.candidate), v.stage, costs(v.costScores), overheads(v.overheadScores))
        case v: O.HumanDecisionRequired => ObjectiveView.HumanDecisionRequired(v.leaders.map(key), v.authorities,
          costs(v.costScores), overheads(v.overheadScores))
      }
      val record = Record(facts, partition, projection, admission, objective)
      dispositionC(record).map(d => new Projected(record, d))
    }
  }

  /** Structural inspection only; callers still need independent current/native admission. */
  def dispositionC(record: Record): Consequence[Disposition] = guarded {
    inspect(record).fold(invalid, Consequence.success)
  }

  private def inspect(r: Record): Either[String, Disposition] = {
    def check(test: Boolean, reason: String): Either[String, Unit] = Either.cond(test, (), reason)
    for {
      _ <- check(r != null && r.selection != null && r.partition != null && r.projection != null &&
        r.admission != null && r.objective != null, "record is incomplete")
      _ <- check(factsShape(r.selection), "selected facts differ or are incomplete")
      d <- r.partition match {
        case PartitionView.SemanticRequired(items, edges) =>
          val ids = r.selection.partition.estimates.items
          val actualItems = ids.collect { case E.Item(id, E.Duration.Unresolved(reason)) => E.UnresolvedItem(id, reason) }
          val actualEdges = r.selection.boundary.handoffs.edges.collect {
            case edge if edge.disposition == SplitPhasePartitionEnumeration.EdgeDisposition.Unresolved => edge.afterItem }
          check(items == actualItems && edges == actualEdges && (items.nonEmpty || edges.nonEmpty) &&
            noLater(r), "partition requirements differ").map(_ => Disposition.PartitionSemanticRequired)
        case PartitionView.NoAdmissible(reason, count, rejected) =>
          val valid = reason match {
            case E.NoCandidateReason.NoAuthorizedSplitBoundary => count == 0 && rejected == 0
            case E.NoCandidateReason.EverySplitExceedsEightHours => count > 0 && count == rejected
            case null => false
          }
          check(valid && count <= r.selection.partition.policy.maximumCombinations && noLater(r),
            "partition refusal differs").map(_ => Disposition.PartitionUnavailable)
        case e: PartitionView.Enumerated => for {
          _ <- check(enumeratedShape(e, r.selection), "enumerated frontier differs")
          d <- r.projection match {
            case None => check(r.selection.planning.allocations.isEmpty && r.admission.isEmpty && r.objective.isEmpty,
              "allocation need crosses later data").map(_ => Disposition.AllocationRequired)
            case Some(p) => for {
              _ <- check(projectionShape(p, e, r.selection), "projection frontier differs")
              d <- p match {
                case _: ProjectionView.Requirements => check(r.admission.isEmpty && r.objective.isEmpty,
                  "projection requirement crosses later stages").map(_ => Disposition.ProjectionRequired)
                case p: ProjectionView.Projected => r.admission match {
                  case None => Left("projected candidates lack admission")
                  case Some(a) => for {
                    _ <- check(admissionShape(a, p), "admission frontier differs")
                    d <- a match {
                      case _: AdmissionView.EvidenceRequired => check(r.objective.isEmpty, "evidence crosses objective")
                        .map(_ => Disposition.HostEvidenceRequired)
                      case _: AdmissionView.NoAdmissible => check(r.objective.isEmpty, "refusal crosses objective")
                        .map(_ => Disposition.CandidatesUnavailable)
                      case ranked: AdmissionView.DurationRanked => r.objective match {
                        case None => Left("ranked candidates lack objective")
                        case Some(o) => check(objectiveShape(o, ranked, p), "objective frontier differs").map { _ => o match {
                          case _: ObjectiveView.RecordRequired => Disposition.ObjectiveRecordRequired
                          case _: ObjectiveView.ReasoningDemandRequired => Disposition.ReasoningRequired
                          case _: ObjectiveView.HumanDecisionRequired => Disposition.HumanDecisionRequired
                          case _: ObjectiveView.Selected => Disposition.Planned
                        }}
                      }
                    }
                  } yield d
                }
              }
            } yield d
          }
        } yield d
      }
    } yield d
  }

  private def factsShape(f: C.Facts): Boolean = {
    val p = f.proposal; val b = f.boundary; val e = f.partition; val w = f.planning
    p != null && b != null && e != null && w != null && {
      val s = p.source; val ids = p.inventory.orderedUnfinishedItems
      s != null && name(s.reference.identity) && name(s.reference.revision) && name(s.phase.value) &&
        name(s.authority.identity) && name(s.authority.revision) && ids.size >= 2 && ids.size <= 128 &&
        ids.distinct == ids && ids.forall(i => name(i.value)) && p.inventory.source == s.reference &&
        p.inventory.phase == s.phase && p.proposal.source == s.reference && p.proposal.authority == s.authority &&
        p.proposal.phase == s.phase && b.boundary.source == s.reference && b.boundary.authority == s.authority &&
        b.boundary.items.map(_.identity) == ids && b.ownership.proposal == p.proposal.reference &&
        b.handoffs.proposal == p.proposal.reference && e.estimates.source == s.reference && e.estimates.items.map(_.identity) == ids &&
        e.policy.maximumCombinations >= 1 && e.policy.maximumCombinations <= 4096 && w.source == s.reference &&
        w.authority == s.authority && w.allocations != null && w.objectives != null
    }
  }
  private def noLater(r: Record): Boolean = r.projection.isEmpty && r.admission.isEmpty && r.objective.isEmpty
  private def enumeratedShape(e: PartitionView.Enumerated, f: C.Facts): Boolean = {
    val ids = f.proposal.inventory.orderedUnfinishedItems
    e.evaluatedCount > 0 && e.rejectedByCeiling >= 0 && e.evaluatedCount <= f.partition.policy.maximumCombinations &&
      e.candidates.nonEmpty && e.evaluatedCount == e.candidates.size + e.rejectedByCeiling &&
      e.candidates.map(_.key).distinct.size == e.candidates.size && e.candidates.forall { c =>
        val cuts = c.key.cutAfterIndices
        val ends = cuts.map(_ + 1) :+ ids.size
        cuts.nonEmpty && cuts == cuts.distinct.sorted && cuts.forall(i => i >= 0 && i < ids.size - 1) &&
          c.children.size == ends.size && c.children.zip((Vector(0) ++ ends.init).zip(ends)).forall { case (child, (start, end)) =>
            child.firstIndex == start && child.endExclusive == end && child.ownedItems == ids.slice(start, end) &&
              child.expectedMinutes > 0 && child.expectedMinutes <= 480
          }
      }
  }
  private def projectionShape(p: ProjectionView, e: PartitionView.Enumerated, f: C.Facts): Boolean = {
    import CandidateProjectionView.*
    val allocations = f.planning.allocations.get
    val keys = e.candidates.map(_.key)
    p.candidates.map(_.key) == keys && allocations.source == f.proposal.source.reference &&
      allocations.authority == f.proposal.source.authority && allocations.allocations.map(a => CandidateKey(a.cutAfterIndices)) == keys &&
      p.candidates.zip(allocations.allocations).zip(e.candidates).forall { case ((candidate, allocation), range) => candidate match {
        case b: BuiltView => b.proposal.reference == allocation.proposal && b.ownership.reference == allocation.ownership &&
          b.handoffs.reference == allocation.handoffs && b.proposal.source == f.proposal.source.reference &&
          b.proposal.authority == f.proposal.source.authority && b.ownership.source == b.proposal.source &&
          b.handoffs.source == b.proposal.source && b.ownership.proposal == b.proposal.reference &&
          b.handoffs.proposal == b.proposal.reference && b.proposal.children.map(c => (c.ownedUnfinishedItems, c.expectedMinutes)) ==
            range.children.map(c => (c.ownedItems, c.expectedMinutes)) && b.localHandoffs != null
        case s: SemanticView => s.gaps.nonEmpty && s.gaps.forall(g => g != null && g.original != null &&
          g.producingItems.nonEmpty && g.receivingItems.nonEmpty && g.producingOwners != null && g.receivingOwners != null)
        case d: DecisionView => d.reason match {
          case Q.DecisionReason.OneRepositoryPerChildRequired => d.repositories.nonEmpty && d.numbering.isEmpty
          case Q.DecisionReason.PhaseNumberingRequired => d.repositories.isEmpty && d.numbering.exists(n =>
            n.reason != null && n.records.source == f.proposal.source && n.records.inventory == f.proposal.inventory &&
              n.records.proposal.reference == allocation.proposal && n.records.proposal.children.map(c =>
                (c.ownedUnfinishedItems, c.expectedMinutes)) == range.children.map(c => (c.ownedItems, c.expectedMinutes)))
          case null => false
        }
      }} && (p match {
        case _: ProjectionView.Projected => true
        case r: ProjectionView.Requirements =>
          val scopes = r.candidates.collect { case s: SemanticView => s }.flatMap(_.gaps)
            .map(g => W.ScopeWork(g.original, g.producingItems, g.receivingItems)).distinct
          val decisions = r.candidates.collect { case d: DecisionView => d.key }
          (scopes.nonEmpty || decisions.nonEmpty) && r.scopes == scopes && r.decisionKeys == decisions
      })
  }
  private def admissionShape(a: AdmissionView, p: ProjectionView.Projected): Boolean = {
    val keys = p.candidates.map(_.key)
    val accepted = a.assessments.filter(_.status == A.Status.Accepted).map(_.key)
    val pending = a.assessments.filter(_.status == A.Status.EvidenceRequired).map(_.key)
    val rejected = a.assessments.collect { case v if v.status.isInstanceOf[A.Status.Rejected] => v.key }
    a.assessments.map(_.key) == keys && a.rejectedKeys == rejected && a.assessments.forall { v =>
      v.boundaryProof != null && v.shortReview != null && v.boundaryProof.forall(k => k == v.key && keys.contains(k)) &&
        v.shortReview.isDefined == v.boundaryProof.isDefined && v.shortReview.forall(s =>
          s.rebalanceAttempts != null && s.mergeAttempts != null && s.rejection != null) && (v.status match {
          case A.Status.Accepted => v.shortReview.exists(_.rejection.isEmpty)
          case A.Status.EvidenceRequired => v.boundaryProof.isEmpty
          case A.Status.Rejected(reason) => v.shortReview.fold(reason == A.Rejection.OrdinaryChildTooShort)(_.rejection.contains(reason))
          case null => false
        })
    } && (a match {
      case v: AdmissionView.EvidenceRequired => pending.nonEmpty && v.pendingKeys == pending
      case _: AdmissionView.NoAdmissible => pending.isEmpty && accepted.isEmpty
      case v: AdmissionView.DurationRanked => pending.isEmpty && accepted.nonEmpty &&
        v.eligible.map(_.key).distinct.size == v.eligible.size && v.eligible.map(_.key).toSet == accepted.toSet &&
        v.eligible.forall(r => r.score.distanceFromSixHoursMinutes >= 0 && r.score.subFiveHourChildren >= 0) &&
        subset(v.durationLeaderKeys, v.eligible.map(_.key))
    })
  }
  private def objectiveShape(o: ObjectiveView, a: AdmissionView.DurationRanked, p: ProjectionView.Projected): Boolean = {
    val leaders = a.durationLeaderKeys
    def scoreShape(cs: Vector[CostScoreView], os: Vector[OverheadScoreView]): Boolean =
      cs != null && os != null && cs.map(_.key).distinct.size == cs.size && cs.forall(c =>
        leaders.contains(c.key) && decimal(c.potentialSavingUnits) && decimal(c.materialSavingUnits)) &&
        os.map(_.key).distinct.size == os.size && os.forall { s =>
          leaders.contains(s.key) && cs.exists(_.key == s.key) && decimal(s.totalUnits) && {
            val b = p.candidates.find(_.key == s.key).get
            s.childCount == b.proposal.children.size && s.handoffCount == b.handoffs.handoffs.size &&
              s.validationRunCount == (if (b.ownership.timing == B.Timing.FinalOnly) 1 else s.childCount)
          }
        }
    o match {
      case ObjectiveView.RecordRequired(required, ks, cs) => required != null && subset(ks, leaders) && scoreShape(cs, Vector.empty)
      case ObjectiveView.ReasoningDemandRequired(items, ks) => items.nonEmpty &&
        items.map(_.identity).distinct.size == items.size && items.forall(i => name(i.identity.value) && name(i.reason)) && subset(ks, leaders)
      case ObjectiveView.Selected(k, stage, cs, os) => leaders.contains(k) && scoreShape(cs, os) && (stage match {
        case O.Stage.Duration => leaders.size == 1 && cs.isEmpty && os.isEmpty
        case O.Stage.Cost => cs.nonEmpty && os.isEmpty && cs.exists(_.key == k)
        case O.Stage.Overhead | O.Stage.StableTieBreak => cs.nonEmpty && os.nonEmpty && os.exists(_.key == k)
        case null => false
      })
      case ObjectiveView.HumanDecisionRequired(ks, authorities, cs, os) => subset(ks, leaders) && ks.size > 1 &&
        authorities.size == ks.size && authorities.forall(v => v != null && v.sourceAuthority != null && v.handoffs != null) &&
        authorities.distinct.size > 1 && scoreShape(cs, os) && ks.forall(k => os.exists(_.key == k))
    }
  }
  private def subset(keys: Vector[CandidateKey], all: Vector[CandidateKey]): Boolean =
    keys != null && keys.nonEmpty && keys.distinct == keys && keys.forall(all.contains)
  private def matches(b: B.Validated, f: C.Facts, doc: CandidateProjectionView.BuiltView): Boolean =
    b.shape.records == P.Records(f.proposal.source, f.proposal.inventory, doc.proposal) &&
      b.records == B.Records(f.boundary.boundary, doc.ownership, doc.handoffs)
  private def key(c: E.Candidate): CandidateKey = CandidateKey(c.cutAfterIndices)
  private def built(b: Q.Built): CandidateProjectionView.BuiltView = CandidateProjectionView.BuiltView(key(b.candidate),
    b.boundary.shape.records.proposal, b.boundary.records.ownership, b.boundary.records.handoffs, b.localHandoffs)
  private def costs(values: Vector[O.CostScore]): Vector[CostScoreView] = values.map(c =>
    CostScoreView(key(c.candidate), c.potentialSavingUnits.toString, c.materialSavingUnits.toString))
  private def overheads(values: Vector[O.OverheadScore]): Vector[OverheadScoreView] = values.map(c =>
    OverheadScoreView(key(c.candidate), c.childCount, c.handoffCount, c.validationRunCount, c.totalUnits.toString))
  private def decimal(value: String): Boolean = value != null && value.matches("0|[1-9][0-9]*")
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase planning snapshot " + reason)
  private def guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => invalid("data shape is invalid")
  }
}
