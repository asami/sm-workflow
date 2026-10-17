package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord
import org.goldenport.configuration.ResolvedConfiguration
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPlanningAdvance as W}

/**
 * Host-only composition, not registration, consent, WorkOrder issue or grant.
 * @since   Oct.  2, 2026
 * @version Oct. 16, 2026
 */
private[smworkflow] object SplitPhaseManagedPartitionPreparation {
  final class Port private[SplitPhaseManagedPartitionPreparation] (
    workflow: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog,
    contexts: SplitPhasePartitionContextDataStorePort,
    currentInputs: SplitPhasePlanningInputDataStorePort,
    instances: CallScopedWorkflowInstances.Port
  ) {
    /** Host template data is passive; Start replaces its execution owner. */
    def loadTemplateForCallC(reference: S.ContextReference, core: ActionCall.Core): Consequence[Option[C.Template]] =
      contexts.loadTemplateForCallC(reference, core)

    def prepareForCallC(reference: S.ContextReference, core: ActionCall.Core): Consequence[Option[C.Prepared]] =
      preparePlanningForCallC(reference, core).flatMap {
        case None => Consequence.success(None)
        case Some(planning) => semanticC(planning).map(Some(_))
      }

    def preparePlanningForCallC(reference: S.ContextReference, core: ActionCall.Core): Consequence[Option[C.PreparedPlanning]] =
      contexts.loadForCallC(reference, core).flatMap {
        case None => Consequence.success(None)
        case Some(frozen) => preparePlanningRecordForCallC(frozen, core).map(Some(_))
      }

    def preparePlanningForCurrentCallC(reference: S.ContextReference, instance: InstanceRecord,
      core: ActionCall.Core): Consequence[Option[C.PreparedPlanning]] =
      contexts.loadForCallC(reference, core).flatMap {
        case None => Consequence.success(None)
        case Some(frozen) => _prepare_current_c(frozen, instance, core).map(Some(_))
      }

    /** Registration and reads share the one current-input construction boundary.
      * Raw host data does not establish a grant, human Start or issued work.
      */
    def prepareRecordForCallC(frozen: C.Record, core: ActionCall.Core): Consequence[C.Prepared] =
      preparePlanningRecordForCallC(frozen, core).flatMap(semanticC)

    private def semanticC(planning: C.PreparedPlanning): Consequence[C.Prepared] =
      W.advanceC(planning.selection).flatMap {
        case need: W.PartitionSemanticRequired => C.prepareSemanticC(planning, need)
        case _ => Consequence.stateConflict("SplitPhase partition context has no partition semantic work")
      }

    def preparePlanningRecordForCallC(frozen: C.Record, core: ActionCall.Core): Consequence[C.PreparedPlanning] =
      if (frozen == null || frozen.owner == null || frozen.owner.handle == null)
        Consequence.stateConflict("SplitPhase partition preparation requires complete raw owner data")
      else for {
        instance <- instances.loadForCallC(workflow.configuration, frozen.owner.handle.instanceIdentity, core).flatMap {
          case Some(value) => Consequence.success(value)
          case None => Consequence.stateConflict("SplitPhase partition preparation requires its canonical current instance")
        }
        prepared <- _prepare_current_c(frozen, instance, core)
      } yield prepared

    private def _prepare_current_c(frozen: C.Record, instance: InstanceRecord,
      core: ActionCall.Core): Consequence[C.PreparedPlanning] = for {
      current <- currentInputs.loadCurrentForCallC(frozen.facts, core)
      prepared <- C.preparePlanningC(frozen, current, instance, workflow.configuration, catalog)
    } yield prepared
  }
  def bindC(host: ResolvedConfiguration, workflow: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[Port] =
    if (catalog == null) Consequence.configurationInvalid("SplitPhase preparation requires its admitted runtime catalog")
    else for {
      contexts <- SplitPhasePartitionContextReadPolicy.bindC(host, workflow)
      inputs <- SplitPhasePlanningInputReadPolicy.bindC(host, workflow)
    } yield new Port(workflow, catalog, contexts, inputs, CallScopedWorkflowInstances.managed(
      workflow.configuration, catalog.definition, workflow.instances, workflow.continuations))
}
