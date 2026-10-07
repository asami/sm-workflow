package org.simplemodeling.textus.smworkflow.admission

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitCandidateProjection as Q,
  SplitPhasePlanningCheckpointPreparation as K, SplitPhasePlanningSnapshot as R,
  SplitPhaseScopeSemantic as S, SplitPhaseExplicitPartitionSemantic as T,
  SplitPhaseExplicitProposalPrecheck as P, SplitPhaseExplicitBoundaryValidation as B}

/** Passive successor data, never a native receipt or executable frontier.
  *
  * @since Oct.  4, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseScopeProjectionSnapshot {
  enum CandidateOutput {
    case Built(documents: R.CandidateProjectionView.BuiltView, alreadyPublished: Boolean)
    case Decision(value: R.CandidateProjectionView.DecisionView)
    case KnownUnrepresentable(gaps: Vector[Q.ScopeGap])
  }
  final case class Candidate(key: R.CandidateKey, transfers: Vector[Q.TransferScope], output: CandidateOutput)
  enum Frontier {
    case Applied(candidates: Vector[Candidate])
    case HumanDirectionRequired
  }
  final case class Record(first: K.Record, result: T.ResultReference, judgment: S.Judgment, frontier: Frontier)

  /** Copy only already computed outputs; preserve the complete first root. */
  def projectC(outcome: Q.ScopeOutcome): Consequence[Record] = _guarded {
    if (outcome == null) _invalid("constructed scope outcome is absent")
    else {
      val frontier = outcome match {
        case _: Q.ScopeHumanDirectionRequired => Frontier.HumanDirectionRequired
        case value: Q.ScopeApplied => Frontier.Applied(value.candidates.map { candidate =>
          val key = R.CandidateKey(candidate.original.candidate.cutAfterIndices)
          val output = candidate match {
            case built: Q.ScopeBuilt =>
              val boundary = built.built.boundary
              CandidateOutput.Built(R.CandidateProjectionView.BuiltView(key, boundary.shape.records.proposal,
                boundary.records.ownership, boundary.records.handoffs, built.built.localHandoffs), built.documentsAlreadyPublished)
            case held: Q.ScopeDecision =>
              val decision = held.decision
              CandidateOutput.Decision(R.CandidateProjectionView.DecisionView(key, decision.reason,
                decision.repositories, decision.numbering.map(n => R.NumberingView(n.reason, n.records))))
            case known: Q.ScopeUnrepresentable => CandidateOutput.KnownUnrepresentable(known.gaps)
          }
          Candidate(key, candidate.transfers, output)
        })
      }
      val record = Record(outcome.checkpoint, outcome.reply.request.result, outcome.reply.judgment, frontier)
      inspectC(record).map(_ => record)
    }
  }

  /** Complete structural checks only: no private planning/native construction. */
  def inspectC(record: Record): Consequence[Unit] = _guarded {
    if (record == null || record.first == null || record.result == null || record.frontier == null)
      _invalid("successor data is incomplete")
    else for {
      _ <- K.inspectC(record.first)
      facts = record.first.snapshot.selection
      shape <- P.loadC(facts.proposal, facts.proposal).flatMap {
        case ready: P.ShapeReady => Consequence.success(ready)
        case _ => _invalid("original selected proposal requires a numbering Decision")
      }
      _ <- B.loadC(shape, facts.boundary, facts.boundary)
      _ <- record.first.snapshot.projection.toVector.flatMap(_.candidates).collect {
        case built: R.CandidateProjectionView.BuiltView => built
      }.foldLeft(Consequence.unit) { (prior, built) =>
        prior.flatMap(_ => _inspect_retained_built_c(record, built))
      }
      request <- S.requestC(record.first)
      _ <- S.inspectC(request, ContextReference(record.result.identity, record.result.revision), record.judgment)
      _ <- record.frontier match {
        case Frontier.HumanDirectionRequired if record.judgment.decision == S.Decision.DecisionNeeded => Consequence.unit
        case Frontier.Applied(candidates) if record.judgment.decision == S.Decision.Enriched =>
          _inspect_candidates_c(record, candidates)
        case _ => _invalid("frontier and actual semantic decision differ")
      }
    } yield ()
  }

  /** Cross-joins after the prior publication and Human data have been inspected.
    * Only structural data checks: no private outcome, projection, enumeration,
    * allocation or ranking is reconstructed from this passive frontier.
    */
  def inspectHumanFrontierC(prior: Record, scopes: Vector[S.Scope],
    candidates: Vector[Candidate], downstream: R.Record): Consequence[Unit] = _guarded {
    val first = prior.first
    val original = first.snapshot.projection.get.asInstanceOf[R.ProjectionView.Requirements].candidates
    val enumerated = first.snapshot.partition.asInstanceOf[R.PartitionView.Enumerated].candidates
    val previous = prior.frontier match {
      case Frontier.Applied(values) => values.collect {
        case Candidate(key, _, CandidateOutput.Built(documents, _)) => key -> documents
      }.toMap
      case Frontier.HumanDirectionRequired => original.collect {
        case documents: R.CandidateProjectionView.BuiltView => documents.key -> documents
      }.toMap
    }
    if (candidates == null || candidates.exists(_ == null) || candidates.map(_.key) != original.map(_.key) ||
        downstream.selection != first.snapshot.selection || downstream.partition != first.snapshot.partition)
      _invalid("Human full frontier, known selected facts or original enumeration differs")
    else for {
      _ <- candidates.zip(original).zip(enumerated).foldLeft(Consequence.unit) {
        case (acc, ((candidate, saved), range)) => acc.flatMap { _ => for {
          _ <- _inspect_transfers_c(first, scopes, candidate, range)
          _ <- (previous.get(candidate.key), candidate.output) match {
            case (Some(retained), CandidateOutput.Built(documents, true)) if documents == retained => Consequence.unit
            case (None, CandidateOutput.Built(documents, false)) if saved.isInstanceOf[R.CandidateProjectionView.SemanticView] =>
              _inspect_new_built_c(first, candidate, range, documents)
            case _ => _invalid("Human output changes retained Built/Decision or publication flags")
          }
        } yield () }
      }
      _ <- downstream.projection match {
        case Some(R.ProjectionView.Projected(documents)) if candidates.map(_.output).zip(documents).forall {
            case (CandidateOutput.Built(value, _), expected) => value == expected
            case _ => false
          } && documents.map(_.key) == candidates.map(_.key) => Consequence.unit
        case _ => _invalid("Human downstream complete projected documents differ")
      }
    } yield ()
  }

  private def _inspect_candidates_c(record: Record, candidates: Vector[Candidate]): Consequence[Unit] = {
    val original = record.first.snapshot.projection.get.asInstanceOf[R.ProjectionView.Requirements].candidates
    val enumerated = record.first.snapshot.partition.asInstanceOf[R.PartitionView.Enumerated].candidates
    if (candidates == null || candidates.exists(_ == null) || candidates.map(_.key) != original.map(_.key))
      _invalid("complete original candidate order or coverage differs")
    else candidates.zip(original).zip(enumerated).foldLeft(Consequence.unit) {
      case (prior, ((candidate, saved), range)) => prior.flatMap { _ => for {
        _ <- _inspect_transfers_c(record.first, record.judgment.scopes, candidate, range)
        _ <- (saved, candidate.output) match {
          case (built: R.CandidateProjectionView.BuiltView, CandidateOutput.Built(documents, true)) if documents == built =>
            Consequence.unit
          case (decision: R.CandidateProjectionView.DecisionView, CandidateOutput.Decision(value)) if value == decision =>
            Consequence.unit
          case (_: R.CandidateProjectionView.SemanticView, CandidateOutput.Built(documents, false)) if documents != null &&
              documents.key == candidate.key && documents.proposal != null && documents.ownership != null &&
              documents.handoffs != null && documents.localHandoffs != null => _inspect_new_built_c(record.first, candidate, range, documents)
          case (_: R.CandidateProjectionView.SemanticView, CandidateOutput.Decision(value)) if value != null &&
              value.key == candidate.key && value.reason != null && value.repositories != null && value.numbering != null =>
            _inspect_new_decision_c(record, candidate, range, value)
          case (_: R.CandidateProjectionView.SemanticView, CandidateOutput.KnownUnrepresentable(gaps)) =>
            val expected = candidate.transfers.collect { case transfer if transfer.producingOwners.size != 1 ||
                transfer.receivingOwners.size != 1 => Q.ScopeGap(transfer.original, transfer.producingItems,
              transfer.receivingItems, transfer.producingOwners, transfer.receivingOwners) }
            if (expected.nonEmpty && gaps == expected) Consequence.unit else _invalid("known Human representability gaps differ")
          case _ => _invalid("candidate output changes retained Built/Decision or known scope meaning")
        }
      } yield () }
    }
  }

  private def _inspect_new_decision_c(record: Record, candidate: Candidate, range: R.CandidateView,
    decision: R.CandidateProjectionView.DecisionView): Consequence[Unit] = {
    val facts = record.first.snapshot.selection
    val proposal = _proposal(record.first, candidate, range)
    val repositories = facts.proposal.proposal.children.zip(facts.boundary.ownership.children).flatMap { (child, ownership) =>
      child.ownedUnfinishedItems.map(_ -> ownership.repository)
    }.toMap
    val issues = proposal.children.flatMap { child =>
      val values = child.ownedUnfinishedItems.map(repositories(_)).distinct
      if (values.size > 1) Vector(Q.RepositoryIssue(child.phase, child.ownedUnfinishedItems, values)) else Vector.empty
    }
    if (issues.nonEmpty) {
      if (decision.reason == Q.DecisionReason.OneRepositoryPerChildRequired &&
          decision.repositories == issues && decision.numbering.isEmpty) Consequence.unit
      else _invalid("new repository Decision differs from original owner data")
    } else if (candidate.transfers.exists(t => t.producingOwners.size != 1 || t.receivingOwners.size != 1)) {
      _invalid("known Human representability gaps precede numbering Decisions")
    } else {
      val records = P.Records(facts.proposal.source, facts.proposal.inventory, proposal)
      P.loadC(records, records).flatMap {
        case held: P.DecisionRequired if decision.reason == Q.DecisionReason.PhaseNumberingRequired &&
            decision.repositories.isEmpty && decision.numbering.contains(R.NumberingView(held.reason, held.records)) => Consequence.unit
        case _ => _invalid("new numbering Decision differs from original inventory and allocations")
      }
    }
  }

  private def _proposal(first: K.Record, candidate: Candidate, range: R.CandidateView): P.ProposalRecord = {
    val facts = first.snapshot.selection
    val allocation = facts.planning.allocations.get.allocations.find(_.cutAfterIndices == candidate.key.cutAfterIndices).get
    val children = range.children.zipWithIndex.map { (child, index) =>
      val phase = facts.proposal.source.phase
      P.Child(if (index == 0) phase else P.PhaseIdentity(s"${phase.value}.$index"), child.ownedItems, child.expectedMinutes, true)
    }
    P.ProposalRecord(allocation.proposal, facts.proposal.source.reference, facts.proposal.source.phase,
      facts.proposal.source.authority, children)
  }

  private def _inspect_new_built_c(first: K.Record, candidate: Candidate, range: R.CandidateView,
    documents: R.CandidateProjectionView.BuiltView): Consequence[Unit] = {
    val facts = first.snapshot.selection
    val allocation = facts.planning.allocations.get.allocations.find(_.cutAfterIndices == candidate.key.cutAfterIndices).get
    val proposal = _proposal(first, candidate, range)
    val transfers = candidate.transfers
    if (documents.proposal != proposal || documents.ownership.reference != allocation.ownership ||
        documents.handoffs.reference != allocation.handoffs ||
        transfers.exists(t => t.producingOwners.size != 1 || t.receivingOwners.size != 1))
      _invalid("new documents differ from original candidate allocations or known single owners")
    else {
      val local = transfers.collect { case transfer if transfer.producingOwners == transfer.receivingOwners =>
        Q.LocalHandoff(transfer.original, transfer.producingOwners.head, transfer.producingItems, transfer.receivingItems) }
      val handoffs = transfers.collect { case transfer if transfer.producingOwners != transfer.receivingOwners =>
        transfer.original.copy(producingChild = transfer.producingOwners.head,
          receivingChild = transfer.receivingOwners.head, owner = transfer.producingOwners.head) }
      val shaperecords = P.Records(facts.proposal.source, facts.proposal.inventory, documents.proposal)
      val boundaryrecords = B.Records(facts.boundary.boundary, documents.ownership, documents.handoffs)
      for {
        _ <- if (documents.localHandoffs == local && documents.handoffs.handoffs == handoffs &&
            documents.handoffs.edges == facts.boundary.handoffs.edges &&
            documents.ownership == _expected_ownership(first, proposal, allocation.ownership)) Consequence.unit
          else _invalid("new document transfers or original validation policy differ")
        shape <- P.loadC(shaperecords, shaperecords).flatMap {
          case ready: P.ShapeReady => Consequence.success(ready)
          case _ => _invalid("new Built proposal instead requires a numbering Decision")
        }
        _ <- B.loadC(shape, boundaryrecords, boundaryrecords).map(_ => ())
      } yield ()
    }
  }

  private def _expected_ownership(first: K.Record, proposal: P.ProposalRecord,
    reference: B.OwnershipReference): B.Ownership = {
    val facts = first.snapshot.selection
    val original = facts.boundary.ownership
    val templates = facts.proposal.proposal.children.map(c => c.phase -> c.ownedUnfinishedItems).toMap
    val repositories = facts.proposal.proposal.children.zip(original.children).flatMap { (child, owner) =>
      child.ownedUnfinishedItems.map(_ -> owner.repository)
    }.toMap
    val criteria = facts.boundary.boundary.items.map(i => i.identity -> i.closureCriteria).toMap
    val phases = proposal.children.map(_.phase)
    def _owners_(items: Vector[P.WorkItemIdentity]): Vector[P.PhaseIdentity] = proposal.children.collect {
      case child if child.ownedUnfinishedItems.exists(items.contains) => child.phase
    }
    val children = proposal.children.zipWithIndex.map { (child, index) =>
      val inherited = original.children.filter(v => templates(v.phase).exists(child.ownedUnfinishedItems.contains))
        .flatMap(_.dependencies).flatMap(p => _owners_(templates(p))).filterNot(_ == child.phase).toSet
      val dependencies = if (original.timing == B.Timing.FinalOnly) inherited ++ phases.lift(index - 1) else inherited
      B.ValidationChild(child.phase, child.ownedUnfinishedItems.map(repositories(_)).distinct match {
        case Vector(repository) => repository
        case _ => throw new IllegalArgumentException("candidate lacks one original repository")
      }, child.ownedUnfinishedItems.flatMap(criteria(_)),
        if (original.timing == B.Timing.FinalOnly) phases.last else child.phase,
        phases.filter(dependencies.contains), phases.lift(index - 1), phases.lift(index + 1))
    }
    B.Ownership(reference, facts.proposal.source.reference, proposal.reference, original.timing,
      original.method, original.driver, original.bootstrapPolicy, phases.last, children)
  }

  private def _inspect_retained_built_c(record: Record,
    documents: R.CandidateProjectionView.BuiltView): Consequence[Unit] = {
    val facts = record.first.snapshot.selection
    val records = P.Records(facts.proposal.source, facts.proposal.inventory, documents.proposal)
    val boundary = B.Records(facts.boundary.boundary, documents.ownership, documents.handoffs)
    val templates = facts.proposal.proposal.children.map(c => c.phase -> c.ownedUnfinishedItems).toMap
    def _owners_(items: Vector[P.WorkItemIdentity]): Vector[P.PhaseIdentity] = documents.proposal.children.collect {
      case child if child.ownedUnfinishedItems.exists(items.contains) => child.phase
    }
    val local = facts.boundary.handoffs.handoffs.flatMap { handoff =>
      val producing = templates(handoff.producingChild)
      val receiving = templates(handoff.receivingChild)
      val owners = _owners_(producing)
      if (owners.size == 1 && owners == _owners_(receiving))
        Vector(Q.LocalHandoff(handoff, owners.head, producing, receiving)) else Vector.empty
    }
    for {
      shape <- P.loadC(records, records).flatMap {
        case ready: P.ShapeReady => Consequence.success(ready)
        case _ => _invalid("retained Built proposal requires a numbering Decision")
      }
      _ <- B.loadC(shape, boundary, boundary)
      _ <- if (documents.localHandoffs == local) Consequence.unit
        else _invalid("retained local handoff bodies are incomplete or differ from original ranges")
    } yield ()
  }

  private def _inspect_transfers_c(first: K.Record, scopes: Vector[S.Scope],
    candidate: Candidate, range: R.CandidateView): Consequence[Unit] = {
    val snapshot = first.snapshot
    val original = snapshot.selection.boundary.handoffs.handoffs
    val interpretations = scopes.map(scope => scope.handoff -> scope).toMap
    val templates = snapshot.selection.proposal.proposal.children.map(child => child.phase -> child.ownedUnfinishedItems).toMap
    def _owners_(items: Vector[P.WorkItemIdentity]): Vector[P.PhaseIdentity] =
      range.children.zipWithIndex.collect { case (child, index) if child.ownedItems.exists(items.contains) =>
        val phase = snapshot.selection.proposal.source.phase
        if (index == 0) phase else P.PhaseIdentity(s"${phase.value}.$index") }
    if (candidate.transfers == null || candidate.transfers.exists(_ == null) ||
        candidate.transfers.map(_.original) != original) _invalid("complete original transfer order or bodies differ")
    else candidate.transfers.foldLeft(Consequence.unit) { (prior, transfer) => prior.flatMap { _ =>
      val producing = interpretations.get(transfer.original.identity).fold(templates(transfer.original.producingChild))(_.producingItems)
      val receiving = interpretations.get(transfer.original.identity).fold(templates(transfer.original.receivingChild))(_.receivingItems)
      if (transfer.producingItems == producing && transfer.receivingItems == receiving &&
          transfer.producingOwners == _owners_(producing) && transfer.receivingOwners == _owners_(receiving)) Consequence.unit
      else _invalid("effective ranges or original cached owner mappings differ")
    }}
  }

  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase scope successor " + reason)
  private def _guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => _invalid("passive data shape is invalid")
  }
}
