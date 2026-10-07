package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{TypedValue, WorkflowStartRequest}
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseStartCommand, SplitPhaseStartInput}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitPartitionContext as C}

/**
 * Host data and private construction only, not actual human ingress or Start.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseTypedStartPreparation {
  type Reference = SplitPhaseExplicitStartPreparation.Reference
  final case class Record(reference: Reference, instanceIdentity: InstanceIdentity,
    workflowIdentity: WorkflowDefinitionIdentity, workflowRevision: WorkflowDefinitionRevision,
    configuration: Configuration, command: SplitPhaseStartCommand, selection: ProfileStartAdmission.Selection,
    source: P.SourceRecord, proposal: Option[P.ProposalRecord])
  final class Prepared private[SplitPhaseTypedStartPreparation] (val record: Record,
    val request: WorkflowStartRequest[SplitPhaseStartInput], val initial: InstanceRecord)
  final class PartitionBound private[SplitPhaseTypedStartPreparation] (
    val start: Prepared, val context: C.Prepared)
  final class PlanningBound private[SplitPhaseTypedStartPreparation] (
    val start: Prepared, val context: C.PreparedPlanning)

  def prepareC(frozen: Record, source: P.SourceRecord, proposal: Option[P.ProposalRecord],
    selection: ProfileStartAdmission.Selection, catalog: SplitPhaseRuntimeCatalog.Catalog,
    configuration: Configuration): Consequence[Prepared] =
    if (frozen == null || frozen.reference == null || !name(frozen.reference.identity) ||
        !name(frozen.reference.revision) || frozen.instanceIdentity == null || !name(frozen.instanceIdentity.value) ||
        frozen.command == null || frozen.command.input == null || catalog == null || configuration == null ||
        frozen.configuration != configuration || source == null || proposal == null || selection == null ||
        frozen.source != source || frozen.proposal != proposal || frozen.selection != selection)
      Consequence.stateConflict("SplitPhase typed Start requires complete unchanged current records and explicit allocation data")
    else for {
      _ <- configuration.validateC
      _ <- if (frozen.workflowIdentity == catalog.definition.workflowIdentity &&
          frozen.workflowRevision == catalog.definition.workflowRevision) Consequence.unit
        else Consequence.stateConflict("SplitPhase typed Start differs from its admitted compiled Workflow")
      input <- selectedInputC(frozen.command)
      _ <- if (selection.selectedInput != null && selection.selectedInput.contains(TypedValue("SplitPhaseStartInput", input))) Consequence.unit
        else Consequence.stateConflict("SplitPhase typed Start input differs from the complete host-selected input")
      request <- SplitPhaseStartCommandAdapter.admitC(frozen.command, new ProfileStartAdmission.SelectionPort {
        def loadC(identity: String) = Consequence.success(Some(selection).filter(_.selectionId == identity))
      })
      _ <- sourceBindingC(frozen.command.input, source, proposal)
      initial = InstanceRecord(frozen.instanceIdentity, catalog.definition, initialRevision,
        Lifecycle.NotStarted, None, CorrelationReference(request.invocationReference),
        CausationReference(frozen.reference.identity), None, None, None, Vector.empty)
      _ <- initial.validateC
    } yield new Prepared(frozen, request, initial)

  /** Private constructed values already establish their own invariants.
    * Check only the new Start/context relationship, not those invariants again.
    */
  def bindPartitionC(start: Prepared, context: C.Prepared): Consequence[PartitionBound] =
    if (start == null || context == null) Consequence.stateConflict("SplitPhase typed partition binding is absent")
    else bindContextC(start, context.record, context.currentInstance,
      context.selection.partition.boundary.shape.records).map(_ => new PartitionBound(start, context))

  def bindPlanningC(start: Prepared, context: C.PreparedPlanning): Consequence[PlanningBound] =
    if (start == null || context == null) Consequence.stateConflict("SplitPhase typed planning binding is absent")
    else bindContextC(start, context.record, context.currentInstance,
      context.selection.partition.boundary.shape.records).map(_ => new PlanningBound(start, context))

  private def bindContextC(start: Prepared, context: C.Record, current: InstanceRecord,
    facts: P.Records): Consequence[Unit] = {
      val original = start.initial
      if (context.owner.start == start.record.reference && context.owner.configuration == start.record.configuration &&
          current.identity == original.identity && current.definition == original.definition &&
          current.correlation == original.correlation &&
          current.history.headOption.exists(_.causation == original.causation) &&
          facts.source == start.record.source && start.record.proposal.forall(_ == facts.proposal))
        Consequence.unit
      else Consequence.stateConflict("SplitPhase partition differs from its exact typed Start, original history or complete selected source")
    }

  def selectedInputC(command: SplitPhaseStartCommand): Consequence[Json] =
    if (command == null) Consequence.stateConflict("SplitPhase typed Start command is absent")
    else for {
      // The existing command codec is data-only and does not invoke old execution.
      wire <- SplitPhaseTypedStartJsonV1.commandJsonC(command)
      input <- wire.hcursor.downField("input").focus.map(Consequence.success)
        .getOrElse(Consequence.stateConflict("SplitPhase typed selected input is absent"))
    } yield input

  private def sourceBindingC(input: SplitPhaseStartInput, source: P.SourceRecord,
    proposal: Option[P.ProposalRecord]): Consequence[Unit] = {
    val valid = source.reference != null && name(source.reference.identity) && name(source.reference.revision) &&
      source.phase != null && name(source.phase.value) && source.authority != null &&
      name(source.authority.identity) && name(source.authority.revision) &&
      source.phase.value == input.sourcePhaseIdentity && source.authority.identity == input.sourceAuthorityReference &&
      input.sourceSnapshotReference.forall(_ == source.reference.identity)
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
