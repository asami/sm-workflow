package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Resolved host read restriction, not selection creation, consent or a storage
  * grant. The current owning call borrows the Subsystem's normal DataStoreSpace.
  * @since   Oct.  6, 2026
  * @version Oct. 23, 2026
  */
private[smworkflow] object RepositorySyncInputSelectionReadPolicy {
  val prefix = "sm-workflow.repository-sync.input-records."
  final case class Declaration(storeName: String, collection: DataStore.CollectionId)

  def decodeC(host: ResolvedConfiguration): Consequence[Declaration] =
    if (host == null) Consequence.configurationInvalid("RepositorySync host selection declaration is absent")
    else for {
      store <- exactStringC(host, "store-name")
      _ <- if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, store).normalizedName == store)
        Consequence.unit else Consequence.configurationInvalid("RepositorySync selection store is not canonical")
      raw <- exactStringC(host, "selection-collection")
      identity <- EntityCollectionId.parse(raw)
      _ <- if (identity.print == raw) Consequence.unit
        else Consequence.configurationInvalid("RepositorySync selection collection is not canonical")
    } yield Declaration(store, DataStore.CollectionId.EntityStore(identity))

  def admitC(host: ResolvedConfiguration, store: String, collection: DataStore.CollectionId): Consequence[Unit] =
    decodeC(host).flatMap { declared =>
      if (declared.storeName == store && declared.collection == collection) Consequence.unit
      else Consequence.stateConflict("RepositorySync selection read differs from its host declaration")
    }

  def bindC(host: ResolvedConfiguration, workflow: RepositorySyncManagedFetch.Declaration)
      : Consequence[ProfileSelectionDataStorePort] = for {
    _ <- RepositorySyncManagedFetch.validateDeclarationC(workflow)
    declared <- decodeC(host)
    _ <- if (declared.storeName == workflow.configuration.workflowStore.name &&
        declared.collection != workflow.instances && declared.collection != workflow.continuations) Consequence.unit
      else Consequence.configurationInvalid("RepositorySync selection requires the same store and a separate collection")
  } yield new ProfileSelectionDataStorePort(declared.storeName, declared.collection,
    ProfileSelectionDataStorePort.Profile.RepositorySync)

  private def exactStringC(host: ResolvedConfiguration, key: String): Consequence[String] =
    GoalPhaseInputRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
        Consequence.success(value)
      case _ => Consequence.configurationInvalid("RepositorySync selection requires explicit exact host configuration")
    }
}
