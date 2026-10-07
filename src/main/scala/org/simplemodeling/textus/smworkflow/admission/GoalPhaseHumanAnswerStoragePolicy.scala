package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId

/**
 * Destination restriction only: neither a question grant nor a human approval.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseHumanAnswerStoragePolicy {
  val prefix = "sm-workflow.goal-phase.human-answers."
  final case class Declaration(storeName: String, collection: DataStore.CollectionId)
  def decodeC(host: ResolvedConfiguration, workflow: GoalPhaseManagedStartAssembly.Declaration): Consequence[Declaration] =
    if (host == null) Consequence.configurationInvalid("Human answer storage is absent")
    else {
      def exactC(name: String) = GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, prefix + name) match {
        case Some(ConfigurationValue.StringValue(value)) if GoalPhaseHumanDecisionContract.canonical(value, 1024) => Consequence.success(value)
        case _ => Consequence.configurationInvalid("Human answer storage requires exact resolved host configuration")
      }
      for {
        questions <- GoalPhaseHumanQuestionStoragePolicy.decodeC(host, workflow)
        store <- exactC("store-name")
        text <- exactC("answer-collection")
        id <- EntityCollectionId.parse(text)
        collection = DataStore.CollectionId.EntityStore(id)
        // Reuse every Workflow/host/artifact disjointness check without extending
        // any host purpose list or granting access to the answer collection.
        checkedHost = host.copy(configuration = host.configuration.copy(values = host.configuration.values.updated(
          GoalPhaseHumanQuestionStoragePolicy.prefix + "question-collection", ConfigurationValue.StringValue(text))))
        checked <- GoalPhaseHumanQuestionStoragePolicy.decodeC(checkedHost, workflow)
        _ <- if (id.print == text && store == questions.storeName && checked.collection == collection && collection != questions.collection)
          Consequence.unit else Consequence.stateConflict("Human answer destination overlaps or differs from its question store")
      } yield Declaration(store, collection)
    }
}
