package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as InstanceContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitPlanningAdvance as W, SplitPhaseExplicitPartitionSemantic as S}

/**
 * Complete storage data, not an issued WorkOrder, grant or human invocation.
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
private[smworkflow] object SplitPhaseExplicitPartitionContext {
  final case class Owner(handle: WorkflowHandle, instanceRevision: InstanceRevision,
    configuration: Configuration, start: SplitPhaseTypedStartPreparation.Reference,
    continuation: ContinuationIdentity, completion: StateMachineOperationIdentity,
    requirement: ExecutionRequirement, presentation: MinimalPresentation)
  final case class Facts(proposal: P.Records, boundary: B.Records, partition: E.Records, planning: W.Data)
  final case class Record(reference: S.ContextReference, owner: Owner, facts: Facts)
  /** Admitted current inputs, not an assertion that semantic work is needed. */
  final class PreparedPlanning private[SplitPhaseExplicitPartitionContext] (val record: Record,
    val selection: W.Selection, val currentInstance: InstanceRecord)
  final class Prepared private[SplitPhaseExplicitPartitionContext] (val record: Record,
    val selection: W.Selection, val semantic: S.Prepared, val currentInstance: InstanceRecord) {
    def request: S.DraftRequest = semantic.request
  }
  val completionOperation = StateMachineOperationIdentity("SplitPhaseService", "submitPartitionSemanticResult")

  /** The host supplies independently loaded current facts and the canonical
    * instance/configuration at this boundary. A decoded snapshot is not current
    * evidence merely because its own reference or fields compare to themselves.
    */
  def prepareC(frozen: Record, current: Facts, instance: InstanceRecord,
    configuration: Configuration, catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[Prepared] = for {
    planning <- preparePlanningC(frozen, current, instance, configuration, catalog)
    pending <- W.advanceC(planning.selection).flatMap {
      case need: W.PartitionSemanticRequired => Consequence.success(need)
      case _ => Consequence.stateConflict("SplitPhase partition context has no partition semantic work")
    }
    prepared <- prepareSemanticC(planning, pending)
  } yield prepared

  /** Conversion consumes the constructed outcome without reloading
    * inputs or enumerating a second time. */
  def prepareSemanticC(planning: PreparedPlanning, pending: W.PartitionSemanticRequired): Consequence[Prepared] =
    if (planning == null || pending == null)
      Consequence.stateConflict("SplitPhase semantic conversion requires planning and pending work")
    else S.prepareC(planning.record.reference, pending).map(semantic =>
      new Prepared(planning.record, planning.selection, semantic, planning.currentInstance))

  def preparePlanningC(frozen: Record, current: Facts, instance: InstanceRecord,
    configuration: Configuration, catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[PreparedPlanning] =
    if (frozen == null || frozen.reference == null || !name(frozen.reference.identity) ||
        !name(frozen.reference.revision) || frozen.owner == null || frozen.facts == null ||
        current == null || instance == null || configuration == null || catalog == null)
      Consequence.stateConflict("SplitPhase partition context requires complete host data and current facts")
    else {
      val owner = frozen.owner
      if (owner.handle == null || owner.instanceRevision == null ||
          owner.start == null || !name(owner.start.identity) || !name(owner.start.revision) ||
          owner.continuation == null || !name(owner.continuation.value) || owner.completion != completionOperation ||
          owner.requirement == null || owner.presentation == null)
        Consequence.stateConflict("SplitPhase partition context owner or host bindings are incomplete")
      else for {
        _ <- configuration.validateC
        handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, instance)
        _ <- if (instance.definition == catalog.definition && owner.handle == handle && owner.instanceRevision == instance.revision &&
            instance.lifecycle == Lifecycle.Active) Consequence.unit
          else Consequence.stateConflict("SplitPhase partition context differs from its exact active instance")
        _ <- owner.requirement.validateC
        _ <- owner.presentation.validateC
        // Each selected current input is admitted once by its owning constructor.
        // Historical copies and configuration are not no-change certificates.
        shape <- P.loadC(current.proposal).flatMap {
          case ready: P.ShapeReady => Consequence.success(ready)
          case _: P.DecisionRequired => Consequence.stateConflict("SplitPhase partition context requires resolved host numbering")
        }
        boundary <- B.loadC(shape, current.boundary)
        partition <- E.loadC(boundary, current.partition)
        selection <- W.selectC(partition, current.planning)
      } yield new PreparedPlanning(frozen.copy(owner = owner.copy(configuration = configuration),
        facts = current), selection, instance)
    }
  private def name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
