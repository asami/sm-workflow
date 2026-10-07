package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Application read restriction from the owning Subsystem's configuration.
  * Not a framework storage grant, write capability or human authorization.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseHostRecordReadPolicy {
  enum Purpose(val key: String) {
    case Selection extends Purpose("selection-collection")
    case Association extends Purpose("association-collection")
    case Authorization extends Purpose("authorization-collection")
    case Decision extends Purpose("decision-collection")
  }
  val prefix = "sm-workflow.goal-phase.host-records."
  final case class Declaration(storeName: String, collections: Vector[(Purpose, DataStore.CollectionId)])

  def admitC(configuration: ResolvedConfiguration, purpose: Purpose,
    storeName: String, collection: DataStore.CollectionId): Consequence[Unit] =
    if (configuration == null || purpose == null || collection == null || storeName == null)
      Consequence.configurationInvalid("GoalPhase host record read declaration is incomplete")
    else for {
      declared <- decodeC(configuration)
      _ <- if (declared.storeName == storeName && declared.collections.contains(purpose -> collection)) Consequence.unit
        else Consequence.stateConflict("GoalPhase host record store or collection is not declared for this purpose")
    } yield ()

  def decodeC(configuration: ResolvedConfiguration): Consequence[Declaration] =
    if (configuration == null)
      Consequence.configurationInvalid("GoalPhase host record read declaration is absent")
    else for {
      configuredStore <- exactStringC(configuration, "store-name")
      _ <- if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId,
          configuredStore).normalizedName == configuredStore) Consequence.unit
        else Consequence.stateConflict("GoalPhase host record read store is not declared")
      bindings <- Purpose.values.toVector.foldLeft(Consequence.success(Vector.empty[(Purpose, DataStore.CollectionId)])) {
        (prior, role) => for {
          accumulated <- prior
          raw <- exactStringC(configuration, role.key)
          identity <- EntityCollectionId.parse(raw)
          _ <- if (identity.print == raw) Consequence.unit
            else Consequence.configurationInvalid("GoalPhase host record collection is not canonical")
        } yield accumulated :+ (role -> DataStore.CollectionId.EntityStore(identity))
      }
      _ <- if (bindings.map(_._2).distinct.size == Purpose.values.length) Consequence.unit
        else Consequence.configurationInvalid("GoalPhase host record purposes require distinct collections")
    } yield Declaration(configuredStore, bindings)

  private def exactStringC(configuration: ResolvedConfiguration, name: String): Consequence[String] =
    storedValue(configuration.configuration.values, prefix + name) match {
      case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
        Consequence.success(value)
      case _ => Consequence.configurationInvalid("GoalPhase host record read requires explicit exact configuration")
    }

  // Only the resolved host snapshot: no live JVM-property/file fallback,
  // operation arguments, string coercion or implicit SQL provider selection.
  private[admission] def storedValue(values: Map[String, ConfigurationValue], key: String): Option[ConfigurationValue] = {
    def nested(current: Map[String, ConfigurationValue], path: List[String]): Option[ConfigurationValue] = path match {
      case head :: Nil => current.get(head)
      case head :: tail => current.get(head) match {
        case Some(ConfigurationValue.ObjectValue(children)) => nested(children, tail)
        case _ => None
      }
      case Nil => None
    }
    values.get(key).orElse(nested(values, key.split('.').toList))
  }
}
