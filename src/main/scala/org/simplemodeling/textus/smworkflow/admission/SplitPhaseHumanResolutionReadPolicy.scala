package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Fresh-resolution-only purpose restriction; not a global Start prerequisite.
  *
  * @since Oct.  5, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseHumanResolutionReadPolicy {
  val prefix = "sm-workflow.split-phase.human-resolutions."
  final class Declaration private[SplitPhaseHumanResolutionReadPolicy] (
    val storeName: String, val collection: DataStore.CollectionId)

  def decodeC(host: ResolvedConfiguration): Consequence[Declaration] =
    if (host == null) Consequence.configurationInvalid("SplitPhase Human resolution declaration is absent")
    else for {
      store <- _string_c(host, "store-name")
      _ <- if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, store).normalizedName == store)
        Consequence.unit else Consequence.configurationInvalid("SplitPhase Human resolution store is not canonical")
      raw <- _string_c(host, "record-collection")
      identity <- EntityCollectionId.parse(raw)
      _ <- if (identity.print == raw) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase Human resolution collection is not canonical")
    } yield new Declaration(store, DataStore.CollectionId.EntityStore(identity))

  def bindC(host: ResolvedConfiguration, workflow: SplitPhaseManagedStartAssembly.Declaration):
      Consequence[SplitPhaseHumanResolutionDataStorePort] =
    if (workflow == null || workflow.configuration == null || workflow.instances == null || workflow.continuations == null)
      Consequence.configurationInvalid("SplitPhase Human resolution requires its Workflow declaration")
    else for {
      _ <- workflow.configuration.validateC
      declared <- decodeC(host)
      targets <- SplitPhasePlanningCheckpointPublication.bindTargetsC(host, workflow)
      inputs <- SplitPhasePlanningInputReadPolicy.decodeC(host)
      context <- SplitPhasePartitionContextReadPolicy.decodeC(host)
      start <- SplitPhaseTypedStartReadPolicy.decodeC(host)
      selection <- SplitPhaseHostSelectionReadPolicy.decodeC(host)
      reserved = targets.localCollections ++ Vector(workflow.instances, workflow.continuations, context.collection,
        start.collection, selection.collection) ++
        SplitPhaseExplicitPlanningInputJsonV1.Purpose.values.toVector.map(inputs.collection)
      _ <- if (declared.storeName == workflow.configuration.workflowStore.name &&
          targets.storeName == declared.storeName && inputs.storeName == declared.storeName &&
          context.storeName == declared.storeName && start.storeName == declared.storeName &&
          selection.storeName == declared.storeName && !reserved.contains(declared.collection)) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase Human resolution requires its Workflow store and a separate data purpose")
    } yield new SplitPhaseHumanResolutionDataStorePort(declared)

  def admitC(host: ResolvedConfiguration, storeName: String, collection: DataStore.CollectionId): Consequence[Unit] =
    decodeC(host).flatMap { declared =>
      if (declared.storeName == storeName && declared.collection == collection) Consequence.unit
      else Consequence.stateConflict("SplitPhase Human resolution read differs from its declared purpose")
    }

  private def _string_c(host: ResolvedConfiguration, key: String): Consequence[String] =
    GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
        Consequence.success(value)
      case _ => Consequence.configurationInvalid("SplitPhase Human resolution requires exact host configuration")
    }
}
