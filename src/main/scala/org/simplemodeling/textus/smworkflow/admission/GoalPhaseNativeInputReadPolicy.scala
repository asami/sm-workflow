package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId

/** Additional native Plan input restriction; legacy host-purpose declarations
  * are unchanged. Reading an input supplies no execution or Human authority.
  * @since   Oct.  6, 2026
  * @version Oct. 23, 2026
  */
private[smworkflow] object GoalPhaseNativeInputReadPolicy {
  final case class Declaration(storeName: String, plans: DataStore.CollectionId)
  final case class Readers(selections: ProfileSelectionDataStorePort, plans: GoalPhaseSelectedPlanDataStorePort)
  val planKey = GoalPhaseInputRecordReadPolicy.prefix + "plan-collection"

  def decodeC(host: ResolvedConfiguration): Consequence[Declaration] = for {
    records <- GoalPhaseInputRecordReadPolicy.decodeC(host)
    raw <- GoalPhaseInputRecordReadPolicy.storedValue(host.configuration.values, planKey) match {
      case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
        Consequence.success(value)
      case _ => Consequence.configurationInvalid("Native GoalPhase requires its explicit selected Plan collection")
    }
    identity <- EntityCollectionId.parse(raw)
    _ <- if (identity.print == raw) Consequence.unit else Consequence.configurationInvalid("Selected Plan collection is not canonical")
    plans = DataStore.CollectionId.EntityStore(identity)
    _ <- if (!records.collections.exists(_._2 == plans)) Consequence.unit
      else Consequence.configurationInvalid("Selected Plan and host-record purposes require separate collections")
  } yield Declaration(records.storeName, plans)

  def admitC(host: ResolvedConfiguration, store: String, collection: DataStore.CollectionId): Consequence[Unit] =
    decodeC(host).flatMap { declared =>
      if (declared.storeName == store && declared.plans == collection) Consequence.unit
      else Consequence.stateConflict("Selected Plan read differs from its host declaration")
    }

  def bindC(host: ResolvedConfiguration, workflow: GoalPhaseNativeRun.Declaration): Consequence[Readers] = for {
    _ <- GoalPhaseNativeRun.validateC(workflow)
    records <- GoalPhaseInputRecordReadPolicy.decodeC(host)
    declared <- decodeC(host)
    _ <- if (declared.storeName == workflow.configuration.workflowStore.name &&
        (records.collections.map(_._2) :+ declared.plans).forall(c => c != workflow.instances && c != workflow.continuations))
      Consequence.unit else Consequence.configurationInvalid("Native GoalPhase inputs require the same store and separate collections")
    selection = records.collections.find(_._1 == GoalPhaseInputRecordReadPolicy.Purpose.Selection).get._2
  } yield Readers(new ProfileSelectionDataStorePort(declared.storeName, selection),
    new GoalPhaseSelectedPlanDataStorePort(declared.storeName, declared.plans))
}
