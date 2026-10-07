package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/**
 * Current host read restriction, never a provider grant or semantic admission.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseHostProposalReadPolicy {
  enum Purpose(val key: String) {
    case Inventory extends Purpose("inventory-collection")
    case Draft extends Purpose("draft-collection")
    case Validation extends Purpose("validation-collection")
    case Boundary extends Purpose("boundary-collection")
  }
  val prefix = "sm-workflow.split-phase.proposal-records."
  final case class Declaration(storeName: String, collections: Vector[(Purpose, DataStore.CollectionId)])

  def decodeC(host: ResolvedConfiguration): Consequence[Declaration] =
    if (host == null) Consequence.configurationInvalid("SplitPhase proposal read declaration is absent")
    else for {
      store <- exactStringC(host, "store-name")
      _ <- if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, store).normalizedName == store)
        Consequence.unit else Consequence.configurationInvalid("SplitPhase proposal store is not canonical")
      collections <- Purpose.values.toVector.foldLeft(Consequence.success(Vector.empty[(Purpose, DataStore.CollectionId)])) {
        (prior, purpose) => for {
          accumulated <- prior
          raw <- exactStringC(host, purpose.key)
          identity <- EntityCollectionId.parse(raw)
          _ <- if (identity.print == raw) Consequence.unit
            else Consequence.configurationInvalid("SplitPhase proposal collection is not canonical")
        } yield accumulated :+ (purpose -> DataStore.CollectionId.EntityStore(identity))
      }
      _ <- if (collections.map(_._2).distinct.size == 4) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase proposal purposes require separate collections")
    } yield Declaration(store, collections)

  def admitC(host: ResolvedConfiguration, purpose: Purpose, store: String,
    collection: DataStore.CollectionId): Consequence[Unit] =
    if (purpose == null || store == null || collection == null)
      Consequence.configurationInvalid("SplitPhase proposal read is incomplete")
    else decodeC(host).flatMap { declared =>
      if (declared.storeName == store && declared.collections.contains(purpose -> collection)) Consequence.unit
      else Consequence.stateConflict("SplitPhase proposal read differs from its declared purpose")
    }

  def bindC(host: ResolvedConfiguration, workflow: SplitPhaseManagedStartAssembly.Declaration)
      : Consequence[SplitPhaseProposalDataStorePort] =
    if (workflow == null || workflow.configuration == null || workflow.instances == null || workflow.continuations == null)
      Consequence.configurationInvalid("SplitPhase proposal reader requires its Workflow declaration")
    else for {
      _ <- workflow.configuration.validateC
      selection <- SplitPhaseHostSelectionReadPolicy.decodeC(host)
      entry <- SplitPhaseHostEntryReadPolicy.decodeC(host)
      proposal <- decodeC(host)
      collections = proposal.collections.map(_._2) ++
        Vector(selection.collection, entry.source, entry.proposal, workflow.instances, workflow.continuations)
      _ <- if (Vector(proposal.storeName, entry.storeName, selection.storeName).forall(_ == workflow.configuration.workflowStore.name) &&
          collections.distinct.size == 9) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase proposal records require the same store and nine separate collections")
    } yield new SplitPhaseProposalDataStorePort(proposal)

  private def exactStringC(host: ResolvedConfiguration, key: String): Consequence[String] =
    GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
        Consequence.success(value)
      case _ => Consequence.configurationInvalid("SplitPhase proposal read requires explicit exact host configuration")
    }
}
