package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/**
 * Read restriction only: neither a framework storage grant nor human consent.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseHostSelectionReadPolicy {
  val prefix = "sm-workflow.split-phase.host-records."
  final case class Declaration(storeName: String, collection: DataStore.CollectionId)

  def decodeC(host: ResolvedConfiguration): Consequence[Declaration] =
    if (host == null) Consequence.configurationInvalid("SplitPhase host selection declaration is absent")
    else for {
      store <- exactStringC(host, "store-name")
      _ <- if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, store).normalizedName == store)
        Consequence.unit
        else Consequence.configurationInvalid("SplitPhase selection store is not canonical")
      raw <- exactStringC(host, "selection-collection")
      identity <- EntityCollectionId.parse(raw)
      _ <- if (identity.print == raw) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase selection collection is not canonical")
    } yield Declaration(store, DataStore.CollectionId.EntityStore(identity))

  def admitC(host: ResolvedConfiguration, store: String, collection: DataStore.CollectionId): Consequence[Unit] =
    decodeC(host).flatMap { declared =>
      if (declared.storeName == store && declared.collection == collection) Consequence.unit
      else Consequence.stateConflict("SplitPhase selection read differs from its host declaration")
    }

  def bindC(host: ResolvedConfiguration, workflow: SplitPhaseManagedStartAssembly.Declaration)
      : Consequence[ProfileSelectionDataStorePort] =
    if (workflow == null || workflow.configuration == null || workflow.instances == null || workflow.continuations == null)
      Consequence.configurationInvalid("SplitPhase selection reader requires its Workflow declaration")
    else for {
      _ <- workflow.configuration.validateC
      declared <- decodeC(host)
      _ <- if (declared.storeName == workflow.configuration.workflowStore.name &&
          declared.collection != workflow.instances && declared.collection != workflow.continuations) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase selection requires the same store and a separate collection")
    } yield new ProfileSelectionDataStorePort(declared.storeName, declared.collection,
      ProfileSelectionDataStorePort.Profile.SplitPhase)

  private def exactStringC(host: ResolvedConfiguration, key: String): Consequence[String] =
    GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
        Consequence.success(value)
      case _ => Consequence.configurationInvalid("SplitPhase selection requires explicit exact host configuration")
    }
}
