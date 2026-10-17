package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitPartitionContext as C}

/**
 * Host data and private construction only, not actual human ingress or Start.
 * @since   Oct.  2, 2026
 * @version Oct. 16, 2026
 */
private[smworkflow] object SplitPhaseTypedStartPreparation {
  final case class Reference(identity: String, revision: String)
  /** Passive routing data, never a fresh Start admission or effect grant. */
  final case class Routing(reference: Reference, instanceIdentity: InstanceIdentity, participantIdentity: String,
    invocationMode: String)
  final case class Allocation(reference: Reference, input: NativeWorkflowStartInput.Split,
    participantIdentity: String, source: P.SourceReference, proposal: Option[P.ProposalReference],
    planningContext: SplitPhaseExplicitPartitionSemantic.ContextReference)
  final case class Invocation(prepared: Prepared, allocation: Reference, template: C.Template)
  final case class Record(reference: Reference, instanceIdentity: InstanceIdentity,
    configuration: Configuration, input: NativeWorkflowStartInput.Split, participantIdentity: String,
    source: P.SourceRecord, proposal: Option[P.ProposalRecord]) {
    def routing: Routing = Routing(reference, instanceIdentity, participantIdentity, input.invocationMode)
  }
  final class Prepared private[SplitPhaseTypedStartPreparation] (val record: Record,
    val initial: InstanceRecord)
  final class PartitionBound private[SplitPhaseTypedStartPreparation] (
    val start: Prepared, val context: C.Prepared)
  final class PlanningBound private[SplitPhaseTypedStartPreparation] (
    val start: Prepared, val context: C.PreparedPlanning)

  def prepareC(frozen: Record, source: P.SourceRecord, proposal: Option[P.ProposalRecord],
    catalog: SplitPhaseRuntimeCatalog.Catalog,
    configuration: Configuration): Consequence[Prepared] =
    if (frozen == null || frozen.reference == null || !name(frozen.reference.identity) ||
        !name(frozen.reference.revision) || frozen.instanceIdentity == null || !name(frozen.instanceIdentity.value) ||
        frozen.input == null || catalog == null || configuration == null || source == null || proposal == null)
      Consequence.stateConflict("SplitPhase typed Start requires current inputs and explicit allocation data")
    else for {
      _ <- if (Set("APPLY", "PREVIEW").contains(frozen.input.invocationMode)) Consequence.unit
        else Consequence.stateConflict("SplitPhase Start requires APPLY or PREVIEW")
      _ <- sourceBindingC(frozen.input, source, proposal)
      initial = InstanceRecord(frozen.instanceIdentity, catalog.definition, initialRevision,
        Lifecycle.NotStarted, None, CorrelationReference(frozen.reference.identity),
        CausationReference(frozen.reference.identity), None, None, None, Vector.empty)
      actual = frozen.copy(configuration = configuration, source = source, proposal = proposal)
    } yield new Prepared(actual, initial)

  /** Private constructed values already establish their own invariants.
    * Check only the new Start/context relationship, not those invariants again.
    */
  def bindPartitionC(start: Prepared, context: C.Prepared): Consequence[PartitionBound] =
    if (start == null || context == null) Consequence.stateConflict("SplitPhase typed partition binding is absent")
    else bindContextC(start, context.record, context.currentInstance).map(_ => new PartitionBound(start, context))

  def bindPlanningC(start: Prepared, context: C.PreparedPlanning): Consequence[PlanningBound] =
    if (start == null || context == null) Consequence.stateConflict("SplitPhase typed planning binding is absent")
    else bindContextC(start, context.record, context.currentInstance).map(_ => new PlanningBound(start, context))

  private def bindContextC(start: Prepared, context: C.Record, current: InstanceRecord): Consequence[Unit] = {
      if (context.owner.start == start.record.reference && current.identity == start.record.instanceIdentity)
        Consequence.unit
      else Consequence.stateConflict("SplitPhase partition belongs to another Start or instance")
    }

  private def sourceBindingC(input: NativeWorkflowStartInput.Split, source: P.SourceRecord,
    proposal: Option[P.ProposalRecord]): Consequence[Unit] = {
    val valid = source.reference != null && name(source.reference.identity) && name(source.reference.revision) &&
      source.phase != null && name(source.phase.value) && source.authority != null &&
      name(source.authority.identity) && name(source.authority.revision) &&
      source.phase.value == input.sourcePhaseIdentity
    val selected = input.proposalReference match {
      case None => proposal.isEmpty
      case Some(identity) => proposal.exists(p => p != null && p.reference != null && p.reference.identity == identity &&
        name(p.reference.revision) && p.source == source.reference && p.phase == source.phase && p.authority == source.authority)
    }
    if (valid && selected) Consequence.unit
    else Consequence.stateConflict("SplitPhase typed Start crosses its selected source, proposal or authority")
  }
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
