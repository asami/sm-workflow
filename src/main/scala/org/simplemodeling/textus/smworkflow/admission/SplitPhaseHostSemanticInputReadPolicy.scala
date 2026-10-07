package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/**
 * Declared read restriction only; never a physical-store or issue grant.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseHostSemanticInputReadPolicy {
  val prefix = "sm-workflow.split-phase.semantic-input-records."
  final case class Declaration(storeName: String, collection: DataStore.CollectionId)
  def decodeC(host: ResolvedConfiguration): Consequence[Declaration] =
    if (host == null) Consequence.configurationInvalid("SplitPhase semantic input declaration is absent")
    else for {
      store <- exactStringC(host, "store-name")
      _ <- if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, store).normalizedName == store)
        Consequence.unit else Consequence.configurationInvalid("SplitPhase semantic input store is not canonical")
      raw <- exactStringC(host, "input-collection")
      identity <- EntityCollectionId.parse(raw)
      _ <- if (identity.print == raw) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase semantic input collection is not canonical")
    } yield Declaration(store, DataStore.CollectionId.EntityStore(identity))

  def admitC(host: ResolvedConfiguration, store: String, collection: DataStore.CollectionId): Consequence[Unit] =
    decodeC(host).flatMap { declared =>
      if (declared.storeName == store && declared.collection == collection) Consequence.unit
      else Consequence.stateConflict("SplitPhase semantic input read differs from its declaration")
    }

  def bindC(host: ResolvedConfiguration, workflow: SplitPhaseManagedStartAssembly.Declaration):
      Consequence[SplitPhaseSemanticInputDataStorePort] =
    if (workflow == null || workflow.configuration == null || workflow.instances == null || workflow.continuations == null)
      Consequence.configurationInvalid("SplitPhase semantic input reader requires its Workflow declaration")
    else for {
      _ <- workflow.configuration.validateC
      _ <- SplitPhaseHostProposalReadPolicy.bindC(host, workflow)
      selection <- SplitPhaseHostSelectionReadPolicy.decodeC(host)
      entry <- SplitPhaseHostEntryReadPolicy.decodeC(host)
      proposal <- SplitPhaseHostProposalReadPolicy.decodeC(host)
      input <- decodeC(host)
      collections = proposal.collections.map(_._2) ++ Vector(selection.collection,
        entry.source, entry.proposal, workflow.instances, workflow.continuations, input.collection)
      _ <- if (input.storeName == workflow.configuration.workflowStore.name && collections.distinct.size == 10)
        Consequence.unit else Consequence.configurationInvalid("SplitPhase semantic inputs require the same store and ten separate collections")
    } yield new SplitPhaseSemanticInputDataStorePort(input)

  private def exactStringC(host: ResolvedConfiguration, key: String): Consequence[String] =
    GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
        Consequence.success(value)
      case _ => Consequence.configurationInvalid("SplitPhase semantic input requires explicit exact host configuration")
    }
}
