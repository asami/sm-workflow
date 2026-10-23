package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import SplitPhaseExplicitPlanningInputJsonV1.Purpose

/**
 * Independent read purpose. Not a storage grant or human ingress.
 * @since   Oct.  2, 2026
 * @version Oct. 23, 2026
 */
private[smworkflow] object SplitPhaseTypedStartReadPolicy {
  val prefix = "sm-workflow.split-phase.typed-starts."
  final class Declaration private[SplitPhaseTypedStartReadPolicy] (
    val storeName: String, val collection: DataStore.CollectionId)
  def decodeC(host: ResolvedConfiguration): Consequence[Declaration] =
    if (host == null) Consequence.configurationInvalid("SplitPhase typed Start declaration is absent")
    else for {
      store <- stringC(host, "store-name")
      _ <- if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, store).normalizedName == store)
        Consequence.unit else Consequence.configurationInvalid("SplitPhase typed Start store is not canonical")
      raw <- stringC(host, "record-collection")
      id <- EntityCollectionId.parse(raw)
      _ <- if (id.print == raw) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase typed Start collection is not canonical")
    } yield new Declaration(store, DataStore.CollectionId.EntityStore(id))

  def bindC(host: ResolvedConfiguration, workflow: SplitPhaseManagedStartAssembly.Declaration,
    catalog: SplitPhaseRuntimeCatalog.Catalog): Consequence[SplitPhaseTypedStartDataStorePort] = for {
    inputs <- SplitPhasePlanningInputReadPolicy.bindC(host, workflow)
    contexts <- SplitPhaseManagedPartitionPreparation.bindC(host, workflow, catalog)
    declared <- decodeC(host)
    inputDeclaration <- SplitPhasePlanningInputReadPolicy.decodeC(host)
    selectionDeclaration <- SplitPhaseInputSelectionReadPolicy.decodeC(host)
    contextDeclaration <- SplitPhasePartitionContextReadPolicy.decodeC(host)
    targets = Purpose.values.toVector.map(inputDeclaration.collection) ++ Vector(selectionDeclaration.collection,
      contextDeclaration.collection, declared.collection, workflow.instances, workflow.continuations)
    _ <- if (declared.storeName == workflow.configuration.workflowStore.name && targets.distinct.size == 14)
      Consequence.unit else Consequence.configurationInvalid("SplitPhase typed Start requires fourteen distinct same-store purposes")
  } yield new SplitPhaseTypedStartDataStorePort(declared, workflow, catalog, inputs, contexts)

  def admitC(host: ResolvedConfiguration, store: String, collection: DataStore.CollectionId): Consequence[Unit] =
    decodeC(host).flatMap { declared =>
      if (declared.storeName == store && declared.collection == collection) Consequence.unit
      else Consequence.stateConflict("SplitPhase typed Start read differs from its declared purpose")
    }
  private def stringC(host: ResolvedConfiguration, key: String): Consequence[String] =
    GoalPhaseInputRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
        Consequence.success(value)
      case _ => Consequence.configurationInvalid("SplitPhase typed Start requires exact host configuration")
    }
}
