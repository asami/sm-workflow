package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/**
 * Explicit read restriction, not a Create grant or allocation authority.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseHostStartRecordReadPolicy {
  val prefix = "sm-workflow.split-phase.start-records."
  final case class Declaration(storeName: String, collection: DataStore.CollectionId)

  def decodeC(host: ResolvedConfiguration): Consequence[Declaration] =
    if (host == null) Consequence.configurationInvalid("SplitPhase explicit Start declaration is absent")
    else for {
      store <- stringC(host, "store-name")
      _ <- if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, store).normalizedName == store)
        Consequence.unit else Consequence.configurationInvalid("SplitPhase explicit Start store is not canonical")
      raw <- stringC(host, "record-collection")
      identity <- EntityCollectionId.parse(raw)
      _ <- if (identity.print == raw) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase explicit Start collection is not canonical")
    } yield Declaration(store, DataStore.CollectionId.EntityStore(identity))

  def admitC(host: ResolvedConfiguration, store: String, collection: DataStore.CollectionId): Consequence[Unit] =
    decodeC(host).flatMap { declared =>
      if (declared.storeName == store && declared.collection == collection) Consequence.unit
      else Consequence.stateConflict("SplitPhase explicit Start read differs from its declared purpose")
    }

  def bindC(host: ResolvedConfiguration, workflow: SplitPhaseManagedStartAssembly.Declaration):
      Consequence[SplitPhaseExplicitStartDataStorePort] = for {
    _ <- SplitPhaseHostSemanticInputReadPolicy.bindC(host, workflow)
    selection <- SplitPhaseHostSelectionReadPolicy.decodeC(host)
    entry <- SplitPhaseHostEntryReadPolicy.decodeC(host)
    proposal <- SplitPhaseHostProposalReadPolicy.decodeC(host)
    input <- SplitPhaseHostSemanticInputReadPolicy.decodeC(host)
    start <- decodeC(host)
    collections = proposal.collections.map(_._2) ++ Vector(selection.collection, entry.source, entry.proposal,
      workflow.instances, workflow.continuations, input.collection, start.collection)
    _ <- if (start.storeName == workflow.configuration.workflowStore.name && collections.distinct.size == 11)
      Consequence.unit else Consequence.configurationInvalid("SplitPhase explicit Start requires the same store and eleven separate collections")
  } yield new SplitPhaseExplicitStartDataStorePort(start)

  private def stringC(host: ResolvedConfiguration, key: String): Consequence[String] =
    GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
        Consequence.success(value)
      case _ => Consequence.configurationInvalid("SplitPhase explicit Start requires exact host configuration")
    }
}
