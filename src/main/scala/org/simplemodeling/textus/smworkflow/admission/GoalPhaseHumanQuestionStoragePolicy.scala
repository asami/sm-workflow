package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId

/**
 * Separate exact host restriction, not a new grant or an extension of stop metadata.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseHumanQuestionStoragePolicy {
  val prefix = "sm-workflow.goal-phase.human-questions."
  final case class Declaration(storeName: String, collection: DataStore.CollectionId)
  def decodeC(host: ResolvedConfiguration, workflow: GoalPhaseManagedStartAssembly.Declaration): Consequence[Declaration] =
    if (host == null || workflow == null || workflow.configuration == null)
      Consequence.configurationInvalid("Human question storage declaration is absent")
    else {
      def exactC(name: String) = GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, prefix + name) match {
        case Some(ConfigurationValue.StringValue(value)) if GoalPhaseHumanDecisionContract.canonical(value, 1024) => Consequence.success(value)
        case _ => Consequence.configurationInvalid("Human question storage requires exact resolved host configuration")
      }
      for {
        store <- exactC("store-name")
        text <- exactC("question-collection")
        id <- EntityCollectionId.parse(text)
        collection = DataStore.CollectionId.EntityStore(id)
        records <- GoalPhaseHostRecordReadPolicy.decodeC(host)
        _ <- if (id.print == text && store == workflow.configuration.workflowStore.name && records.storeName == store &&
            collection != workflow.instances && collection != workflow.continuations && !records.collections.exists(_._2 == collection))
          Consequence.unit else Consequence.stateConflict("Human question destination overlaps or differs from its Workflow declaration")
        hasArtifacts = Vector("store-name", "reference-collection").exists(name =>
          GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, GoalPhaseArtifactReadPolicy.prefix + name).nonEmpty)
        _ <- if (!hasArtifacts) Consequence.unit else GoalPhaseArtifactReadPolicy.decodeC(host).flatMap { artifacts =>
          if (artifacts.collection != collection) Consequence.unit
          else Consequence.stateConflict("Human question collection overlaps the artifact catalog")
        }
      } yield Declaration(store, collection)
    }
}
