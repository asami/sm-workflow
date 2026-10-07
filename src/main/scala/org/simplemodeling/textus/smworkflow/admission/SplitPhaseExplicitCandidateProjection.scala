package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitPlanningAdvance as W, SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningSnapshot as R, SplitPhaseScopeSemantic as S}

/** Candidate documents are mechanical projections, not AI-produced gate flags.
  * Host reference allocation and semantic scope refinement remain distinct.
  *
  * @since Oct.  2, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseExplicitCandidateProjection {
  final case class AllocationReference(identity: String, revision: String)
  final case class Allocation(cutAfterIndices: Vector[Int], proposal: P.ProposalReference,
    ownership: B.OwnershipReference, handoffs: B.HandoffPlanReference)
  final case class Records(reference: AllocationReference, source: P.SourceReference,
    authority: P.AuthorityReference, allocations: Vector[Allocation])
  final class Input private[SplitPhaseExplicitCandidateProjection] (val enumerated: E.Enumerated, val records: Records)
  final case class LocalHandoff(original: B.Handoff, phase: P.PhaseIdentity,
    producingItems: Vector[P.WorkItemIdentity], receivingItems: Vector[P.WorkItemIdentity])
  final case class ScopeGap(original: B.Handoff, producingItems: Vector[P.WorkItemIdentity],
    receivingItems: Vector[P.WorkItemIdentity], producingOwners: Vector[P.PhaseIdentity], receivingOwners: Vector[P.PhaseIdentity])
  final case class RepositoryIssue(phase: P.PhaseIdentity, ownedItems: Vector[P.WorkItemIdentity], repositories: Vector[B.RepositoryIdentity])
  enum DecisionReason { case OneRepositoryPerChildRequired, PhaseNumberingRequired }
  sealed trait CandidateOutcome { def candidate: E.Candidate }
  final class Built private[SplitPhaseExplicitCandidateProjection] (val candidate: E.Candidate,
    val boundary: B.Validated, val localHandoffs: Vector[LocalHandoff]) extends CandidateOutcome
  final class SemanticRequired private[SplitPhaseExplicitCandidateProjection] (val candidate: E.Candidate,
    val gaps: Vector[ScopeGap]) extends CandidateOutcome
  final class DecisionRequired private[SplitPhaseExplicitCandidateProjection] (val candidate: E.Candidate,
    val reason: DecisionReason, val repositories: Vector[RepositoryIssue], val numbering: Option[P.DecisionRequired]) extends CandidateOutcome
  sealed trait Outcome { def input: Input }
  final class Projected private[SplitPhaseExplicitCandidateProjection] (val input: Input,
    val candidates: Vector[Built]) extends Outcome
  final class Requirements private[SplitPhaseExplicitCandidateProjection] (val input: Input,
    val candidates: Vector[CandidateOutcome]) extends Outcome

  /** Explicit effective annotations, including for already published documents.
    * These are calculated application data, never authority or an AI route.
    */
  final case class TransferScope(original: B.Handoff, producingItems: Vector[P.WorkItemIdentity],
    receivingItems: Vector[P.WorkItemIdentity], producingOwners: Vector[P.PhaseIdentity],
    receivingOwners: Vector[P.PhaseIdentity])
  sealed trait ScopedCandidate {
    def original: CandidateOutcome
    def transfers: Vector[TransferScope]
  }
  final class ScopeBuilt private[SplitPhaseExplicitCandidateProjection] (val original: CandidateOutcome,
    val transfers: Vector[TransferScope], val built: Built, val documentsAlreadyPublished: Boolean) extends ScopedCandidate
  final class ScopeDecision private[SplitPhaseExplicitCandidateProjection] (val original: CandidateOutcome,
    val transfers: Vector[TransferScope], val decision: DecisionRequired) extends ScopedCandidate
  /** The ranges are known, but not representable with this candidate's owners.
    * Human direction is required; do not issue another identical AI scope task.
    */
  final class ScopeUnrepresentable private[SplitPhaseExplicitCandidateProjection] (val original: SemanticRequired,
    val transfers: Vector[TransferScope], val gaps: Vector[ScopeGap]) extends ScopedCandidate
  sealed trait ScopeOutcome {
    def original: W.ProjectionRequired
    def checkpoint: K.Record
    def reply: S.Admitted
  }
  final class ScopeHumanDirectionRequired private[SplitPhaseExplicitCandidateProjection] (
    val original: W.ProjectionRequired, val checkpoint: K.Record, val reply: S.Admitted) extends ScopeOutcome
  final class ScopeApplied private[SplitPhaseExplicitCandidateProjection] (
    val original: W.ProjectionRequired, val checkpoint: K.Record, val reply: S.Admitted,
    val candidates: Vector[ScopedCandidate], val projected: Option[Projected]) extends ScopeOutcome

  /** Human interpretation is not an AI ScopeOutcome. The entire previous
    * publication remains attached, including the previous judgment and rows.
    */
  final class HumanApplied private[SplitPhaseExplicitCandidateProjection] (
    val resolution: SplitPhaseHumanScopeResolution.Admitted, val previous: ScopeOutcome,
    val candidates: Vector[ScopedCandidate], val projected: Option[Projected])

  /** The owning observer, not a decoded Record, supplies native provenance.
    * This reconstruction carries saved calculation data, not mutation authority.
    */
  final class RetainedHuman private[SplitPhaseExplicitCandidateProjection] (
    val observed: SplitPhaseManagedHumanResolutionCheckpoint.Observed, val frontier: HumanApplied)
  final class RetainedReasoning private[SplitPhaseExplicitCandidateProjection] (
    val source: SplitPhaseRetainedReasoningSource.Source, val projected: Projected)

  /** Allocations are exact current host data, not identities assigned by AI.
    * Admitted template and enumeration are consumed without re-enumeration.
    */
  def loadC(enumerated: E.Enumerated, frozen: Records, current: Records): Consequence[Input] =
    if (enumerated == null || !complete(current))
      Consequence.stateConflict("SplitPhase explicit candidate allocations are incomplete")
    else if (frozen != current)
      Consequence.stateConflict("SplitPhase explicit candidate complete allocations differ")
    else {
      val original = enumerated.input.boundary
      val allocations = current.allocations
      if (current.source != original.shape.records.source.reference || current.authority != original.shape.records.source.authority ||
          allocations.map(_.cutAfterIndices) != enumerated.candidates.map(_.cutAfterIndices) ||
          allocations.map(_.proposal.identity).distinct.size != allocations.size ||
          allocations.map(_.ownership.identity).distinct.size != allocations.size ||
          allocations.map(_.handoffs.identity).distinct.size != allocations.size ||
          allocations.exists(a => a.proposal.identity == original.shape.records.proposal.reference.identity ||
            a.ownership.identity == original.records.ownership.reference.identity ||
            a.handoffs.identity == original.records.handoffs.reference.identity))
        Consequence.stateConflict("SplitPhase explicit allocation source, coverage or distinct record identities differ")
      else Consequence.success(new Input(enumerated, current))
    }

  /** Absorb document assembly. Any unresolved projection holds the whole set;
    * a skill cannot drop a candidate or choose a replacement execution path.
    */
  def projectC(input: Input): Consequence[Outcome] = {
    val results = input.enumerated.candidates.zip(input.records.allocations).foldLeft(
      Consequence.success(Vector.empty[CandidateOutcome])) { case (acc, (candidate, allocation)) =>
      acc.flatMap(values => _project_one_c(input, candidate, allocation, Map.empty).map(values :+ _))
    }
    results.map(values => if (values.forall(_.isInstanceOf[Built]))
      new Projected(input, values.collect { case built: Built => built })
      else new Requirements(input, values))
  }

  /** Copy saved views only through a privately native-bound restored frontier.
    * Existing documents, gaps and Decisions are not projected or allocated again.
    */
  def restoreScopeC(retained: E.RetainedScope): Consequence[Requirements] =
    if (retained == null) _scope_invalid("restoration requires actual completed native frontier")
    else {
      val snapshot = retained.source.source.record.snapshot
      for {
        raw <- snapshot.selection.planning.allocations.map(Consequence.success)
          .getOrElse(_scope_invalid("saved allocation data is absent"))
        input <- loadC(retained.enumerated, raw, raw)
        views <- snapshot.projection match {
          case Some(value: R.ProjectionView.Requirements) => Consequence.success(value.candidates)
          case _ => _scope_invalid("saved whole requirement is absent")
        }
        _ <- if (views.map(_.key.cutAfterIndices) == input.enumerated.candidates.map(_.cutAfterIndices)) Consequence.unit
          else _scope_invalid("saved candidate order or coverage differs")
        restored <- views.zip(input.enumerated.candidates).foldLeft(Consequence.success(Vector.empty[CandidateOutcome])) {
          case (acc, (view, candidate)) => acc.flatMap(values => _restore_view_c(snapshot.selection.proposal,
            snapshot.selection.boundary, candidate, view).map(values :+ _))
        }
      } yield new Requirements(input, restored)
    }

  private def _restore_view_c(proposal: P.Records, boundary: B.Records, candidate: E.Candidate,
    view: R.CandidateProjectionView): Consequence[CandidateOutcome] = view match {
    case value: R.CandidateProjectionView.BuiltView =>
      val records = P.Records(proposal.source, proposal.inventory, value.proposal)
      for {
        shape <- P.loadC(records, records).flatMap {
          case ready: P.ShapeReady => Consequence.success(ready)
          case _ => _scope_invalid("saved Built shape changed to a Decision")
        }
        raw = B.Records(boundary.boundary, value.ownership, value.handoffs)
        loaded <- B.loadC(shape, raw, raw)
      } yield new Built(candidate, loaded, value.localHandoffs)
    case value: R.CandidateProjectionView.SemanticView =>
      Consequence.success(new SemanticRequired(candidate, value.gaps))
    case value: R.CandidateProjectionView.DecisionView => value.reason match {
      case DecisionReason.OneRepositoryPerChildRequired =>
        Consequence.success(new DecisionRequired(candidate, value.reason, value.repositories, None))
      case DecisionReason.PhaseNumberingRequired => value.numbering match {
        case Some(saved) => P.loadC(saved.records, saved.records).flatMap {
          case loaded: P.DecisionRequired if loaded.reason == saved.reason =>
            Consequence.success(new DecisionRequired(candidate, value.reason, value.repositories, Some(loaded)))
          case _ => _scope_invalid("saved numbering Decision changed")
        }
        case None => _scope_invalid("saved numbering Decision data is absent")
      }
    }
  }

  /** Restore every saved Built candidate in its original order. No mechanical
    * projection or allocation is run, and passive data cannot construct this.
    */
  def restoreReasoningC(retained: E.RetainedReasoning): Consequence[RetainedReasoning] =
    if (retained == null) _scope_invalid("reasoning restoration requires actual native frontier")
    else {
      val snapshot = retained.source.snapshot
      for {
        raw <- snapshot.selection.planning.allocations.map(Consequence.success)
          .getOrElse(_scope_invalid("saved reasoning allocation is absent"))
        input <- loadC(retained.enumerated, raw, raw)
        views <- snapshot.projection match {
          case Some(value: R.ProjectionView.Projected) => Consequence.success(value.candidates)
          case _ => _scope_invalid("saved reasoning whole Built frontier is absent")
        }
        restored <- views.zip(input.enumerated.candidates).foldLeft(Consequence.success(Vector.empty[Built])) {
          case (prior, (view, candidate)) => prior.flatMap { accumulated =>
            _restore_view_c(snapshot.selection.proposal, snapshot.selection.boundary, candidate, view).flatMap {
              case built: Built => Consequence.success(accumulated :+ built)
              case _ => _scope_invalid("saved reasoning Built changed kind")
            }
          }
        }
      } yield new RetainedReasoning(retained.source, new Projected(input, restored))
    }

  /** Restore only the independently observed committed successor. Saved results
    * are copied, not projected or semantically applied again. The observation
    * is native-bound data, never a fresh storage/execution capability.
    */
  def restorePublishedScopeC(observed: SplitPhaseManagedScopeProjectionCheckpoint.Observed): Consequence[ScopeOutcome] =
    if (observed == null) _scope_invalid("published restoration requires an actual owning observation")
    else for {
      original <- W.restoreCompletedScopeC(observed.source)
      required = original.trace.projection.get.asInstanceOf[Requirements]
      snapshot = observed.record.snapshot
      restored <- snapshot.frontier match {
        case SplitPhaseScopeProjectionSnapshot.Frontier.HumanDirectionRequired =>
          Consequence.success[ScopeOutcome](new ScopeHumanDirectionRequired(original, snapshot.first, observed.source.admitted))
        case SplitPhaseScopeProjectionSnapshot.Frontier.Applied(candidates) =>
          candidates.zip(required.candidates).foldLeft(Consequence.success(Vector.empty[ScopedCandidate])) {
            case (prior, (saved, before)) => prior.flatMap { accumulated =>
              _restore_published_candidate_c(snapshot.first.snapshot.selection.proposal,
                snapshot.first.snapshot.selection.boundary, before, saved).map(accumulated :+ _)
            }
          }.map { values =>
            val projected = if (values.forall(_.isInstanceOf[ScopeBuilt])) Some(new Projected(required.input,
              values.collect { case built: ScopeBuilt => built.built })) else None
            new ScopeApplied(original, snapshot.first, observed.source.admitted, values, projected): ScopeOutcome
          }
      }
      passive <- SplitPhaseScopeProjectionSnapshot.projectC(restored)
      _ <- if (passive == snapshot) Consequence.unit
        else _scope_invalid("restored whole published frontier differs from actual committed data")
    } yield restored

  private def _restore_published_candidate_c(proposal: P.Records, boundary: B.Records,
    before: CandidateOutcome, saved: SplitPhaseScopeProjectionSnapshot.Candidate): Consequence[ScopedCandidate] = {
    import SplitPhaseScopeProjectionSnapshot.CandidateOutput
    saved.output match {
      case CandidateOutput.Built(documents, published) =>
        _restore_view_c(proposal, boundary, before.candidate, documents).flatMap {
          case built: Built => Consequence.success(new ScopeBuilt(before, saved.transfers, built, published))
          case _ => _scope_invalid("published Built restored as a different candidate kind")
        }
      case CandidateOutput.Decision(value) =>
        _restore_view_c(proposal, boundary, before.candidate, value).flatMap {
          case held: DecisionRequired => Consequence.success(new ScopeDecision(before, saved.transfers, held))
          case _ => _scope_invalid("published Decision restored as a different candidate kind")
        }
      case CandidateOutput.KnownUnrepresentable(gaps) => before match {
        case semantic: SemanticRequired => Consequence.success(new ScopeUnrepresentable(semantic, saved.transfers, gaps))
        case _ => _scope_invalid("known unrepresentable result lacks its original semantic candidate")
      }
    }
  }

  /** Interpret the current published frontier, never replay first-root AI
    * application. Every stored Built row is already published, even when the
    * earlier publication recorded it as newly created. Structural Decisions
    * remain Decisions; only unresolved scopes are mechanically projected.
    */
  def applyHumanResolutionC(resolution: SplitPhaseHumanScopeResolution.Admitted): Consequence[HumanApplied] =
    if (resolution == null) _scope_invalid("canonical admitted Human interpretation is absent")
    else for {
      previous <- restorePublishedScopeC(resolution.observed.plan.source)
      required = previous.original.trace.projection.get.asInstanceOf[Requirements]
      scopes = resolution.record.scopes.map(scope => scope.handoff -> scope).toMap
      retained = previous match {
        case applied: ScopeApplied => applied.candidates.map(Some(_))
        case _: ScopeHumanDirectionRequired => Vector.fill(required.candidates.size)(None)
      }
      candidates <- required.candidates.zip(required.input.records.allocations).zip(retained).foldLeft(
        Consequence.success(Vector.empty[ScopedCandidate])) { case (prior, ((original, allocation), saved)) =>
        prior.flatMap { values =>
          val transfers = _transfer_scopes(required.input, original.candidate, scopes)
          val interpreted: Consequence[ScopedCandidate] = saved match {
            case Some(built: ScopeBuilt) =>
              Consequence.success(new ScopeBuilt(original, transfers, built.built, true))
            case Some(held: ScopeDecision) =>
              Consequence.success(new ScopeDecision(original, transfers, held.decision))
            case _ => original match {
              case built: Built => Consequence.success(new ScopeBuilt(original, transfers, built, true))
              case held: DecisionRequired => Consequence.success(new ScopeDecision(original, transfers, held))
              case semantic: SemanticRequired =>
                _project_one_c(required.input, original.candidate, allocation, scopes).map {
                  case built: Built => new ScopeBuilt(original, transfers, built, false)
                  case held: DecisionRequired => new ScopeDecision(original, transfers, held)
                  case known: SemanticRequired => new ScopeUnrepresentable(semantic, transfers, known.gaps)
                }
            }
          }
          interpreted.map(values :+ _)
        }
      }
      projected = if (candidates.forall(_.isInstanceOf[ScopeBuilt])) Some(new Projected(required.input,
        candidates.collect { case built: ScopeBuilt => built.built })) else None
    } yield new HumanApplied(resolution, previous, candidates, projected)

  /** Reconstruct the already published Human frontier, without applying the
    * interpretation, projecting candidates, enumerating or allocating again.
    * Its complete native/application/document join was observed by the owner.
    */
  def restorePublishedHumanC(observed: SplitPhaseManagedHumanResolutionCheckpoint.Observed): Consequence[RetainedHuman] =
    if (observed == null) _scope_invalid("Human restoration requires an actual owning observation")
    else for {
      resolution <- SplitPhaseHumanScopeResolution.admitC(observed.source, observed.record.resolution)
      previous <- restorePublishedScopeC(observed.source.plan.source)
      required = previous.original.trace.projection.get.asInstanceOf[Requirements]
      saved = observed.record.frontier
      _ <- if (saved.map(_.key.cutAfterIndices) == required.candidates.map(_.candidate.cutAfterIndices)) Consequence.unit
        else _scope_invalid("retained Human candidate order or complete coverage differs")
      candidates <- saved.zip(required.candidates).foldLeft(Consequence.success(Vector.empty[ScopedCandidate])) {
        case (prior, (value, before)) => prior.flatMap { accumulated =>
          _restore_published_candidate_c(previous.checkpoint.snapshot.selection.proposal,
            previous.checkpoint.snapshot.selection.boundary, before, value).flatMap {
            case built: ScopeBuilt => Consequence.success(accumulated :+ built)
            case _ => _scope_invalid("retained Human publication is not ALL-Built")
          }
        }
      }
      projected = new Projected(required.input, candidates.collect { case built: ScopeBuilt => built.built })
      frontier = new HumanApplied(resolution, previous, candidates, Some(projected))
    } yield new RetainedHuman(observed, frontier)

  /** Pure calculation from constructed inputs and the complete exact first
    * snapshot/request. No raw snapshot-to-planner adapter, enumeration, identity
    * allocation, native completion admission, storage or Terminal is provided.
    */
  def applyScopeC(original: W.ProjectionRequired, checkpoint: K.Record, reply: S.Admitted): Consequence[ScopeOutcome] =
    if (original == null || checkpoint == null || reply == null)
      _scope_invalid("constructed requirement, checkpoint or admitted reply is absent")
    else for {
      snapshot <- R.projectC(original)
      _ <- if (checkpoint.snapshot == snapshot.record) Consequence.unit
        else _scope_invalid("complete first checkpoint differs from the constructed frontier")
      request <- S.requestC(checkpoint)
      _ <- if (reply.request == request) Consequence.unit
        else _scope_invalid("assigned request differs in context, source, transfer, closure or host result")
      outcome <- reply.judgment.decision match {
        case S.Decision.DecisionNeeded => Consequence.success[ScopeOutcome](
          new ScopeHumanDirectionRequired(original, checkpoint, reply))
        case S.Decision.Enriched =>
          val required = original.trace.projection.get.asInstanceOf[Requirements]
          val scopes = reply.judgment.scopes.map(scope => scope.handoff -> scope).toMap
          required.candidates.zip(required.input.records.allocations).foldLeft(
            Consequence.success(Vector.empty[ScopedCandidate])) { case (acc, (candidate, allocation)) =>
            acc.flatMap { values =>
              val transfers = _transfer_scopes(required.input, candidate.candidate, scopes)
              val result: Consequence[ScopedCandidate] = candidate match {
                case built: Built => Consequence.success(new ScopeBuilt(built, transfers, built, true))
                case decision: DecisionRequired => Consequence.success(new ScopeDecision(decision, transfers, decision))
                case semantic: SemanticRequired =>
                  _project_one_c(required.input, semantic.candidate, allocation, scopes).map {
                    case built: Built => new ScopeBuilt(semantic, transfers, built, false)
                    case decision: DecisionRequired => new ScopeDecision(semantic, transfers, decision)
                    case known: SemanticRequired => new ScopeUnrepresentable(semantic, transfers, known.gaps)
                  }
              }
              result.map(values :+ _)
            }
          }.map { values =>
            val projected = if (values.forall(_.isInstanceOf[ScopeBuilt])) Some(new Projected(required.input,
              values.collect { case value: ScopeBuilt => value.built })) else None
            new ScopeApplied(original, checkpoint, reply, values, projected): ScopeOutcome
          }
      }
    } yield outcome

  private def _transfer_scopes(input: Input, candidate: E.Candidate,
    scopes: Map[B.HandoffIdentity, S.Scope]): Vector[TransferScope] = {
    val original = input.enumerated.input.boundary
    val sourcephase = original.shape.records.source.phase
    val templates = original.shape.records.proposal.children.map(child => child.phase -> child.ownedUnfinishedItems).toMap
    def _owners_(items: Vector[P.WorkItemIdentity]): Vector[P.PhaseIdentity] =
      candidate.children.zipWithIndex.collect { case (child, index) if child.ownedItems.exists(items.contains) =>
        if (index == 0) sourcephase else P.PhaseIdentity(s"${sourcephase.value}.$index") }
    original.records.handoffs.handoffs.map { handoff =>
      val producing = scopes.get(handoff.identity).fold(templates(handoff.producingChild))(_.producingItems)
      val receiving = scopes.get(handoff.identity).fold(templates(handoff.receivingChild))(_.receivingItems)
      TransferScope(handoff, producing, receiving, _owners_(producing), _owners_(receiving))
    }
  }

  private def _scope_invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase scope projection " + reason)

  private def _project_one_c(input: Input, candidate: E.Candidate, allocation: Allocation,
    scopes: Map[B.HandoffIdentity, S.Scope]): Consequence[CandidateOutcome] = {
    val original = input.enumerated.input.boundary
    val selected = original.shape.records
    val templatechildren = selected.proposal.children
    val templateownership = original.records.ownership
    val templatescopes = templatechildren.map(c => c.phase -> c.ownedUnfinishedItems).toMap
    val repositorybyitem = templatechildren.zip(templateownership.children).flatMap((c, v) =>
      c.ownedUnfinishedItems.map(_ -> v.repository)).toMap
    val children = candidate.children.zipWithIndex.map((range, i) => P.Child(
      if (i == 0) selected.source.phase else P.PhaseIdentity(s"${selected.source.phase.value}.$i"),
      range.ownedItems, range.expectedMinutes, true))
    val phases = children.map(_.phase)
    val repositories = children.map(c => c.ownedUnfinishedItems.map(repositorybyitem(_)).distinct)
    val issues = children.zip(repositories).collect { case (child, values) if values.size != 1 =>
      RepositoryIssue(child.phase, child.ownedUnfinishedItems, values) }
    if (issues.nonEmpty) Consequence.success(new DecisionRequired(candidate, DecisionReason.OneRepositoryPerChildRequired, issues, None))
    else {
      def _owners_(items: Vector[P.WorkItemIdentity]): Vector[P.PhaseIdentity] = children.collect {
        case child if child.ownedUnfinishedItems.exists(items.contains) => child.phase
      }
      val transfers = _transfer_scopes(input, candidate, scopes)
      val gaps = transfers.collect { case value if value.producingOwners.size != 1 || value.receivingOwners.size != 1 =>
        ScopeGap(value.original, value.producingItems, value.receivingItems, value.producingOwners, value.receivingOwners) }
      if (gaps.nonEmpty) Consequence.success(new SemanticRequired(candidate, gaps))
      else {
        val local = transfers.collect { case value if value.producingOwners.head == value.receivingOwners.head =>
          LocalHandoff(value.original, value.producingOwners.head, value.producingItems, value.receivingItems) }
        val handoffs = transfers.collect { case value if value.producingOwners.head != value.receivingOwners.head =>
          val producer = value.producingOwners.head
          value.original.copy(producingChild = producer, receivingChild = value.receivingOwners.head, owner = producer) }
        val byitem = original.records.boundary.items.map(i => i.identity -> i.closureCriteria).toMap
        val validation = children.zipWithIndex.map { case (child, index) =>
          val inherited = templateownership.children.filter(v => templatescopes(v.phase).exists(child.ownedUnfinishedItems.contains))
            .flatMap(_.dependencies).flatMap(p => _owners_(templatescopes(p))).filterNot(_ == child.phase).toSet
          val dependencies = if (templateownership.timing == B.Timing.FinalOnly)
            inherited ++ phases.lift(index - 1) else inherited
          B.ValidationChild(child.phase, repositories(index).head, child.ownedUnfinishedItems.flatMap(byitem(_)),
            if (templateownership.timing == B.Timing.FinalOnly) phases.last else child.phase,
            phases.filter(dependencies.contains), phases.lift(index - 1), phases.lift(index + 1))
        }
        val proposal = P.ProposalRecord(allocation.proposal, selected.source.reference, selected.source.phase,
          selected.source.authority, children)
        val shaperecords = P.Records(selected.source, selected.inventory, proposal)
        P.loadC(shaperecords, shaperecords).flatMap {
          case decision: P.DecisionRequired =>
            Consequence.success(new DecisionRequired(candidate, DecisionReason.PhaseNumberingRequired, Vector.empty, Some(decision)))
          case shape: P.ShapeReady =>
            val ownership = B.Ownership(allocation.ownership, selected.source.reference, allocation.proposal,
              templateownership.timing, templateownership.method, templateownership.driver, templateownership.bootstrapPolicy,
              phases.last, validation)
            val plan = B.HandoffPlan(allocation.handoffs, selected.source.reference, allocation.proposal, handoffs,
              phases.map(p => p -> handoffs.filter(_.receivingChild == p).map(_.identity)).toMap, original.records.handoffs.edges)
            val records = B.Records(original.records.boundary, ownership, plan)
            B.loadC(shape, records, records).map(boundary => new Built(candidate, boundary, local))
        }
      }
    }
  }

  private def complete(r: Records): Boolean = r != null && r.reference != null &&
    name(r.reference.identity) && name(r.reference.revision) && r.source != null &&
    name(r.source.identity) && name(r.source.revision) && r.authority != null &&
    name(r.authority.identity) && name(r.authority.revision) && r.allocations != null &&
    r.allocations.forall(a => a != null && a.cutAfterIndices != null && a.proposal != null &&
      name(a.proposal.identity) && name(a.proposal.revision) && a.ownership != null &&
      name(a.ownership.identity) && name(a.ownership.revision) && a.handoffs != null &&
      name(a.handoffs.identity) && name(a.handoffs.revision))
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
