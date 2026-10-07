package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Exact host read restriction, not a framework storage/write grant.
  * Separate artifact declaration does not make old host-record configuration
  * require a fifth purpose or turn its authorization collection into a catalog.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseArtifactReadPolicy {
  val prefix = "sm-workflow.goal-phase.artifacts."
  final case class Declaration(storeName: String, collection: DataStore.CollectionId)

  def decodeC(configuration: ResolvedConfiguration): Consequence[Declaration] =
    if (configuration == null) Consequence.configurationInvalid("Artifact declaration is absent")
    else {
      val values = configuration.configuration.values
      def exactC(name: String): Consequence[String] =
        GoalPhaseHostRecordReadPolicy.storedValue(values, prefix + name) match {
          case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
            Consequence.success(value)
          case _ => Consequence.configurationInvalid("Artifact declaration requires exact resolved host configuration")
        }
      for {
        store <- exactC("store-name")
        text <- exactC("reference-collection")
        identity <- EntityCollectionId.parse(text)
        collection = DataStore.CollectionId.EntityStore(identity)
        _ <- admitC(configuration, store, collection)
      } yield Declaration(store, collection)
    }
  def admitC(configuration: ResolvedConfiguration, storeName: String,
    collection: DataStore.CollectionId): Consequence[Unit] =
    if (configuration == null || storeName == null || collection == null)
      Consequence.configurationInvalid("Artifact read declaration is incomplete")
    else {
      val values = configuration.configuration.values
      def exactC(name: String): Consequence[String] =
        GoalPhaseHostRecordReadPolicy.storedValue(values, prefix + name) match {
          case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
            Consequence.success(value)
          case _ => Consequence.configurationInvalid("Artifact read requires exact resolved host configuration")
        }
      for {
        declaredStore <- exactC("store-name")
        declaredCollection <- exactC("reference-collection")
        identity <- EntityCollectionId.parse(declaredCollection)
        _ <- if (identity.print == declaredCollection && declaredStore == storeName &&
            ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, declaredStore).normalizedName == declaredStore &&
            collection == DataStore.CollectionId.EntityStore(identity)) Consequence.unit
          else Consequence.stateConflict("Artifact read destination differs from its host declaration")
        hasHostDeclaration = (Vector("store-name") ++ GoalPhaseHostRecordReadPolicy.Purpose.values.map(_.key)).exists(name =>
          GoalPhaseHostRecordReadPolicy.storedValue(values, GoalPhaseHostRecordReadPolicy.prefix + name).nonEmpty)
        _ <- if (!hasHostDeclaration) Consequence.unit
          else GoalPhaseHostRecordReadPolicy.decodeC(configuration).flatMap { host =>
            if (!host.collections.exists(_._2 == collection)) Consequence.unit
            else Consequence.stateConflict("Artifact collection overlaps another host-record purpose")
          }
      } yield ()
    }
}
