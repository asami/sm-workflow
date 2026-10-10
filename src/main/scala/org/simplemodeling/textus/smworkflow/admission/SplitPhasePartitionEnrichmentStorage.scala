package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPlanningInputJsonV1 as F}

/** Purpose metadata only. Actual checkpoint owners bind storage inside their
  * native calls; this metadata neither stages effects nor certifies readback.
  * @since   Oct.  2, 2026
  * @version Oct. 11, 2026
  */
private[smworkflow] object SplitPhasePartitionEnrichmentStorage {
  val prefix = "sm-workflow.split-phase.partition-enrichment."
  final class Port private[SplitPhasePartitionEnrichmentStorage] (
    val storeName: String, val collection: DataStore.CollectionId,
    val estimatesCollection: DataStore.CollectionId, val handoffsCollection: DataStore.CollectionId,
    val planningCollection: DataStore.CollectionId, val proposalCollection: DataStore.CollectionId,
    val ownershipCollection: DataStore.CollectionId)

  def bindC(host: ResolvedConfiguration, workflow: SplitPhaseManagedStartAssembly.Declaration): Consequence[Port] =
    if (host == null || workflow == null || workflow.configuration == null || workflow.instances == null || workflow.continuations == null)
      Consequence.configurationInvalid("SplitPhase enrichment requires explicit host and Workflow metadata")
    else for {
      _ <- workflow.configuration.validateC
      store <- _string_c(host, "store-name")
      _ <- if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, store).normalizedName == store)
        Consequence.unit else Consequence.configurationInvalid("SplitPhase enrichment store is not canonical")
      raw <- _string_c(host, "record-collection")
      identity <- EntityCollectionId.parse(raw)
      _ <- if (identity.print == raw) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase enrichment collection is not canonical")
      inputs <- SplitPhasePlanningInputReadPolicy.decodeC(host)
      start <- SplitPhaseTypedStartReadPolicy.decodeC(host)
      context <- SplitPhasePartitionContextReadPolicy.decodeC(host)
      selection <- SplitPhaseHostSelectionReadPolicy.decodeC(host)
      target = DataStore.CollectionId.EntityStore(identity)
      targets = F.Purpose.values.toVector.map(inputs.collection) ++ Vector(start.collection, context.collection,
        selection.collection, workflow.instances, workflow.continuations, target)
      _ <- if (Vector(store, inputs.storeName, start.storeName, context.storeName, selection.storeName)
          .forall(_ == workflow.configuration.workflowStore.name) && targets.distinct.size == 15) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase enrichment requires fifteen distinct same-store purposes")
    } yield new Port(store, target, inputs.collection(F.Purpose.Estimates), inputs.collection(F.Purpose.Handoffs),
      inputs.collection(F.Purpose.Planning), inputs.collection(F.Purpose.Proposal), inputs.collection(F.Purpose.Ownership))

  private def _string_c(host: ResolvedConfiguration, key: String): Consequence[String] =
    GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if _name(value) => Consequence.success(value)
      case _ => Consequence.configurationInvalid("SplitPhase enrichment requires exact host configuration")
    }
  private def _exact(identity: String, revision: String): Boolean = _name(identity) && _name(revision)
  private def _name(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
