package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import SplitPhaseExplicitPlanningInputJsonV1.Purpose

/**
 * Purpose restrictions are not grants. No legacy entry-role prerequisites.
 * @since   Oct.  2, 2026
 * @version Oct. 23, 2026
 */
private[smworkflow] object SplitPhasePlanningInputReadPolicy {
  val prefix = "sm-workflow.split-phase.planning-inputs."
  final class Declaration private[SplitPhasePlanningInputReadPolicy] (
    val storeName: String, private val collections: Map[Purpose, DataStore.CollectionId]
  ) {
    def collection(purpose: Purpose): DataStore.CollectionId = collections(purpose)
    private[SplitPhasePlanningInputReadPolicy] def targets = collections.values.toVector
  }

  def decodeC(host: ResolvedConfiguration): Consequence[Declaration] =
    if (host == null) Consequence.configurationInvalid("SplitPhase planning input declaration is absent")
    else for {
      store <- stringC(host, "store-name")
      _ <- if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, store).normalizedName == store)
        Consequence.unit else Consequence.configurationInvalid("SplitPhase planning input store is not canonical")
      collections <- Purpose.values.toVector.foldLeft(Consequence.success(Map.empty[Purpose, DataStore.CollectionId])) {
        (previous, purpose) => for {
          values <- previous
          raw <- stringC(host, purpose.key)
          identity <- EntityCollectionId.parse(raw)
          _ <- if (identity.print == raw) Consequence.unit
            else Consequence.configurationInvalid("SplitPhase planning input collection is not canonical")
        } yield values.updated(purpose, DataStore.CollectionId.EntityStore(identity))
      }
      _ <- if (collections.values.toVector.distinct.size == Purpose.values.length) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase planning input purposes must be separate")
    } yield new Declaration(store, collections)

  def bindC(host: ResolvedConfiguration, workflow: SplitPhaseManagedStartAssembly.Declaration):
      Consequence[SplitPhasePlanningInputDataStorePort] =
    if (workflow == null || workflow.configuration == null || workflow.instances == null || workflow.continuations == null)
      Consequence.configurationInvalid("SplitPhase planning inputs require their Workflow declaration")
    else for {
      _ <- workflow.configuration.validateC
      declared <- decodeC(host)
      context <- SplitPhasePartitionContextReadPolicy.decodeC(host)
      targets = declared.targets ++ Vector(context.collection, workflow.instances, workflow.continuations)
      _ <- if (declared.storeName == workflow.configuration.workflowStore.name &&
          context.storeName == declared.storeName && targets.distinct.size == Purpose.values.length + 3) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase planning inputs must use their Workflow store without storage aliases")
    } yield new SplitPhasePlanningInputDataStorePort(declared)

  def admitC(host: ResolvedConfiguration, purpose: Purpose, store: String,
    collection: DataStore.CollectionId): Consequence[Unit] = decodeC(host).flatMap { declared =>
    if (purpose != null && declared.storeName == store && declared.collection(purpose) == collection) Consequence.unit
    else Consequence.stateConflict("SplitPhase planning input read differs from its declared purpose")
  }
  private def stringC(host: ResolvedConfiguration, key: String): Consequence[String] =
    GoalPhaseInputRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
        Consequence.success(value)
      case _ => Consequence.configurationInvalid("SplitPhase planning inputs require exact host configuration")
    }
}
