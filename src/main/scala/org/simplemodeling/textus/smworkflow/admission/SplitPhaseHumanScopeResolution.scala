package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionSemantic as T,
  SplitPhasePlanningCheckpointPreparation as K, SplitPhaseScopeProjectionCheckpointPreparation as C,
  SplitPhaseScopeSemantic as S, SplitPhaseManagedHumanCompletion as H,
  SplitPhaseHumanDirectionAnswerContract as A}

/** Passive interpretation and its separate, canonical-Human admission boundary.
  * Neither decoded data nor its reference is an AI result or execution permit.
  *
  * @since Oct.  5, 2026
  * @version Oct. 11, 2026
  */
private[smworkflow] object SplitPhaseHumanScopeResolution {
  final case class Reference(identity: String, revision: String)
  final case class DecisionReference(identity: String)
  final case class Record(reference: Reference, context: T.ContextReference, checkpoint: K.Reference,
    scopeCheckpoint: C.Reference, decision: DecisionReference, scopes: Vector[S.Scope],
    rationale: String, evidence: Vector[T.EvidenceReference])
  final class Admitted private[SplitPhaseHumanScopeResolution] (val observed: H.ObservedAnswer,
    val record: Record, val request: S.Request)

  /** Shape only. Source ranges and actual Human provenance are admitted below. */
  def inspectC(record: Record): Consequence[Unit] =
    if (record != null && record.reference != null && record.context != null && record.checkpoint != null &&
        record.scopeCheckpoint != null && record.decision != null &&
        Vector(record.reference.identity, record.reference.revision, record.context.identity, record.context.revision,
          record.checkpoint.identity, record.checkpoint.revision, record.scopeCheckpoint.identity,
          record.scopeCheckpoint.revision, record.decision.identity).forall(_reference) &&
        _text(record.rationale) && record.scopes != null && record.scopes.nonEmpty &&
        record.scopes.forall(scope => scope != null && scope.handoff != null && _reference(scope.handoff.value) &&
          _text(scope.rationale) && _items(scope.producingItems) && _items(scope.receivingItems)) &&
        record.scopes.map(_.handoff).distinct.size == record.scopes.size && record.evidence != null &&
        record.evidence.nonEmpty && record.evidence.distinct == record.evidence &&
        record.evidence.forall(value => value != null && _reference(value.identity) && _reference(value.revision)))
      Consequence.unit
    else _invalid("interpretation data is incomplete or noncanonical")

  /** Only the existing private native observer can supply ObservedAnswer.
    * Consume the separate interpretation against the owning source's work items;
    * no copied Decision or outer evidence-reference echo is required.
    */
  def admitC(observed: H.ObservedAnswer, record: Record): Consequence[Admitted] = for {
    _ <- inspectC(record)
    _ <- if (observed != null) Consequence.unit else _invalid("canonical Human observation is absent")
    _ <- observed.completion.result.result.value match {
      case _: A.Answer.ProvideResolution => Consequence.unit
      case _ => _invalid("completed Human answer did not select resolution data")
    }
    first = observed.plan.source.record.snapshot.first
    request <- S.requestC(first)
    _ <- S.inspectScopesC(request, record.scopes)
  } yield new Admitted(observed, record, request)

  private def _items(items: Vector[SplitPhaseExplicitProposalPrecheck.WorkItemIdentity]): Boolean =
    items != null && items.nonEmpty && items.distinct == items &&
      items.forall(item => item != null && _reference(item.value))
  private def _reference(value: String): Boolean = _text(value) && value.length <= 1024
  private def _text(value: String): Boolean = value != null && value.nonEmpty && value.trim == value &&
    value.length <= 4096 && !value.exists(Character.isISOControl)
  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase Human scope resolution " + reason)
}
