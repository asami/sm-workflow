package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningSnapshot as R, SplitPhaseExplicitBoundaryValidation as B,
  SplitPhaseExplicitProposalPrecheck as P, SplitPhaseExplicitPartitionSemantic as S}

/** Application scope data only. Constructing/decoding it establishes no native
  * issue, current checkpoint, planner capability or authority.
  *
  * @since Oct.  4, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object SplitPhaseScopeSemantic {
  final case class Gap(original: B.Handoff, producingItems: Vector[P.WorkItemIdentity],
    receivingItems: Vector[P.WorkItemIdentity])
  final case class Request(context: S.ContextReference, checkpoint: K.Reference, source: P.SourceReference,
    gaps: Vector[Gap], items: Vector[B.Item], result: S.ResultReference)
  final case class Scope(handoff: B.HandoffIdentity, producingItems: Vector[P.WorkItemIdentity],
    receivingItems: Vector[P.WorkItemIdentity], rationale: String)
  enum Decision { case Enriched, DecisionNeeded }
  final case class Judgment(decision: Decision,
    rationale: String, scopes: Vector[Scope], evidence: Vector[S.EvidenceReference])
  final class Admitted private[SplitPhaseScopeSemantic] (val request: Request, val judgment: Judgment)

  /** Workflow-owned purpose selection from the complete retained frontier. No
    * enumeration, allocation, candidate selection or executable restoration.
    */
  def requestC(checkpoint: K.Record): Consequence[Request] = for {
    disposition <- R.dispositionC(checkpoint.snapshot)
    gaps <- checkpoint.snapshot.projection match {
      case Some(value: R.ProjectionView.Requirements) if disposition == R.Disposition.ProjectionRequired =>
        Consequence.success(value.candidates.collect {
          case semantic: R.CandidateProjectionView.SemanticView => semantic.gaps.map(gap =>
            Gap(gap.original, gap.producingItems, gap.receivingItems))
        }.flatten.distinct)
      case _ => _invalid("selected checkpoint has no scope projection frontier")
    }
    _ <- if (gaps.nonEmpty && gaps.map(_.original.identity).distinct.size == gaps.size) Consequence.unit
      else _invalid("scope gaps are absent or disagree about an original transfer")
    needed = gaps.flatMap(gap => gap.producingItems ++ gap.receivingItems).toSet
    items = checkpoint.snapshot.selection.boundary.boundary.items.filter(item => needed.contains(item.identity))
    request = Request(checkpoint.originalContext.reference, checkpoint.reference,
      checkpoint.snapshot.selection.proposal.source.reference, gaps, items,
      S.ResultReference(checkpoint.reference.identity + ":handoff-scope-result", checkpoint.reference.revision))
    _ <- _request_shape_c(request)
  } yield request

  /** Admit only the exact returned semantic task. Ranges are semantic transfer
    * scopes, not changes to candidate ownership, original transfer or closure.
    */
  def admitC(request: Request, result: ContextReference, judgment: Judgment): Consequence[Admitted] =
    inspectC(request, result, judgment).map(_ => new Admitted(request, judgment))

  /** Shared interpretation bounds only. Human admission can use these rules
    * without impersonating the issued AI task's result reference. Success
    * creates neither Admitted, Human provenance nor native execution authority.
    */
  def inspectScopesC(request: Request, scopes: Vector[Scope]): Consequence[Unit] =
    _request_shape_c(request).flatMap(_ => _inspect_scopes_c(request, scopes))

  /** Passive shape inspection uses the same result contract but creates no
    * admitted semantic value or native completion authority.
    */
  def inspectC(request: Request, result: ContextReference, judgment: Judgment): Consequence[Unit] = for {
    _ <- _request_shape_c(request)
    _ <- if (judgment != null &&
        result == ContextReference(request.result.identity, request.result.revision) && judgment.decision != null &&
        _name(judgment.rationale) && judgment.scopes != null && judgment.evidence != null &&
        judgment.evidence.nonEmpty && judgment.evidence.distinct == judgment.evidence &&
        judgment.evidence.forall(e => e != null && _name(e.identity) && _name(e.revision))) Consequence.unit
      else _invalid("host reference, rationale or evidence is incomplete or crossed")
    _ <- judgment.decision match {
      case Decision.DecisionNeeded if judgment.scopes.isEmpty => Consequence.unit
      case Decision.Enriched => _inspect_scopes_c(request, judgment.scopes)
      case _ => _invalid("reply changes gap coverage or contains partial, empty, foreign or reordered scopes")
    }
  } yield ()

  private def _inspect_scopes_c(request: Request, scopes: Vector[Scope]): Consequence[Unit] =
    if (scopes != null && scopes.forall(_ != null) &&
        scopes.map(_.handoff) == request.gaps.map(_.original.identity) &&
        scopes.zip(request.gaps).forall { (scope, gap) =>
          _name(scope.rationale) && _ordered_subset(scope.producingItems, gap.producingItems) &&
            _ordered_subset(scope.receivingItems, gap.receivingItems)
        }) Consequence.unit
    else _invalid("reply changes gap coverage or contains partial, empty, foreign or reordered scopes")

  private def _request_shape_c(request: Request): Consequence[Unit] =
    if (request != null && request.context != null && request.checkpoint != null && request.source != null &&
        request.result != null && Vector(request.context.identity, request.context.revision, request.checkpoint.identity,
          request.checkpoint.revision, request.source.identity, request.source.revision, request.result.identity,
          request.result.revision).forall(_name) && request.gaps != null && request.gaps.nonEmpty &&
        request.items != null && request.items.nonEmpty && request.items.forall(item => item != null && item.identity != null &&
          item.closureCriteria != null && item.prerequisiteItems != null && item.authority != null) &&
        request.items.map(_.identity).distinct.size == request.items.size &&
        request.gaps.forall(gap => gap != null && gap.original != null && gap.original.identity != null &&
          _ordered_subset(gap.producingItems, request.items.map(_.identity)) &&
          _ordered_subset(gap.receivingItems, request.items.map(_.identity))) &&
        request.gaps.map(_.original.identity).distinct.size == request.gaps.size) Consequence.unit
    else _invalid("scope request is incomplete")
  private def _ordered_subset(items: Vector[P.WorkItemIdentity], original: Vector[P.WorkItemIdentity]): Boolean =
    items != null && items.nonEmpty && items.distinct == items && original.filter(items.contains) == items
  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty && value.trim == value &&
    value.length <= 4096 && !value.exists(Character.isISOControl)
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase scope semantic " + reason)
}
