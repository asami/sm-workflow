package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/**
 * Independent data restriction, not a Create grant or semantic admission.
 * @since   Oct.  2, 2026
 * @version Oct. 23, 2026
 */
private[smworkflow] object SplitPhasePartitionContextReadPolicy {
  val prefix = "sm-workflow.split-phase.partition-contexts."
  final class Declaration private[SplitPhasePartitionContextReadPolicy] (
    val storeName: String, val collection: DataStore.CollectionId)

  def decodeC(host: ResolvedConfiguration): Consequence[Declaration] =
    if (host == null) Consequence.configurationInvalid("SplitPhase partition context declaration is absent")
    else for {
      store <- stringC(host, "store-name")
      _ <- if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, store).normalizedName == store)
        Consequence.unit else Consequence.configurationInvalid("SplitPhase partition context store is not canonical")
      raw <- stringC(host, "record-collection")
      identity <- EntityCollectionId.parse(raw)
      _ <- if (identity.print == raw) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase partition context collection is not canonical")
    } yield new Declaration(store, DataStore.CollectionId.EntityStore(identity))

  def bindC(host: ResolvedConfiguration, workflow: SplitPhaseManagedStartAssembly.Declaration):
      Consequence[SplitPhasePartitionContextDataStorePort] =
    if (workflow == null || workflow.configuration == null || workflow.instances == null || workflow.continuations == null)
      Consequence.configurationInvalid("SplitPhase partition context requires its Workflow declaration")
    else for {
      _ <- workflow.configuration.validateC
      declared <- decodeC(host)
      _ <- if (declared.storeName == workflow.configuration.workflowStore.name &&
          Vector(declared.collection, workflow.instances, workflow.continuations).distinct.size == 3) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase partition context must use its Workflow store without instance aliases")
    } yield new SplitPhasePartitionContextDataStorePort(declared)

  def admitC(host: ResolvedConfiguration, store: String, collection: DataStore.CollectionId): Consequence[Unit] =
    decodeC(host).flatMap { declared =>
      if (declared.storeName == store && declared.collection == collection) Consequence.unit
      else Consequence.stateConflict("SplitPhase partition context read differs from its declared purpose")
    }
  private def stringC(host: ResolvedConfiguration, key: String): Consequence[String] =
    GoalPhaseInputRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
        Consequence.success(value)
      case _ => Consequence.configurationInvalid("SplitPhase partition context requires exact host configuration")
    }
}
