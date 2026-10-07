package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/**
 * Resolved host read restriction, not a physical storage or authorization grant.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseHostEntryReadPolicy {
  enum Purpose(val key: String) {
    case Source extends Purpose("source-collection")
    case Proposal extends Purpose("proposal-collection")
  }
  val prefix = "sm-workflow.split-phase.entry-records."
  final case class Declaration(storeName: String, source: DataStore.CollectionId, proposal: DataStore.CollectionId) {
    def collection(purpose: Purpose): DataStore.CollectionId = purpose match {
      case Purpose.Source => source
      case Purpose.Proposal => proposal
    }
  }

  def decodeC(host: ResolvedConfiguration): Consequence[Declaration] =
    if (host == null) Consequence.configurationInvalid("SplitPhase entry read declaration is absent")
    else for {
      store <- exactStringC(host, "store-name")
      _ <- if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, store).normalizedName == store)
        Consequence.unit else Consequence.configurationInvalid("SplitPhase entry store is not canonical")
      source <- collectionC(host, Purpose.Source)
      proposal <- collectionC(host, Purpose.Proposal)
      _ <- if (source != proposal) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase entry purposes require separate collections")
    } yield Declaration(store, source, proposal)

  def admitC(host: ResolvedConfiguration, purpose: Purpose, store: String,
    collection: DataStore.CollectionId): Consequence[Unit] =
    if (purpose == null || collection == null || store == null)
      Consequence.configurationInvalid("SplitPhase entry read is incomplete")
    else decodeC(host).flatMap { declared =>
      if (declared.storeName == store && declared.collection(purpose) == collection) Consequence.unit
      else Consequence.stateConflict("SplitPhase entry read differs from its declared purpose")
    }

  def bindC(host: ResolvedConfiguration, workflow: SplitPhaseManagedStartAssembly.Declaration)
      : Consequence[SplitPhaseEntryDataStorePort] =
    if (workflow == null || workflow.configuration == null || workflow.instances == null || workflow.continuations == null)
      Consequence.configurationInvalid("SplitPhase entry reader requires its Workflow declaration")
    else for {
      _ <- workflow.configuration.validateC
      selection <- SplitPhaseHostSelectionReadPolicy.decodeC(host)
      entry <- decodeC(host)
      _ <- if (entry.storeName == workflow.configuration.workflowStore.name && selection.storeName == entry.storeName &&
          Vector(entry.source, entry.proposal, selection.collection, workflow.instances, workflow.continuations).distinct.size == 5)
        Consequence.unit else Consequence.configurationInvalid("SplitPhase records require the same store and separate collections")
    } yield new SplitPhaseEntryDataStorePort(entry)

  private def collectionC(host: ResolvedConfiguration, purpose: Purpose): Consequence[DataStore.CollectionId] =
    for {
      raw <- exactStringC(host, purpose.key)
      identity <- EntityCollectionId.parse(raw)
      _ <- if (identity.print == raw) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase entry collection is not canonical")
    } yield DataStore.CollectionId.EntityStore(identity)

  private def exactStringC(host: ResolvedConfiguration, key: String): Consequence[String] =
    GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
        Consequence.success(value)
      case _ => Consequence.configurationInvalid("SplitPhase entry read requires explicit exact host configuration")
    }
}
