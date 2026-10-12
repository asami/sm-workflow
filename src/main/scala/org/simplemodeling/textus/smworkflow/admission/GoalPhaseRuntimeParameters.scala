package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/**
 * Logical persistence configuration only; physical storage and grants belong to the host.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseRuntimeParameters {
  private val prefix = "sm-workflow.goal-phase.persistence."
  private val exactString = ComponentParameterDecoder[String] {
    case ConfigurationValue.StringValue(value) if value != null && value.nonEmpty && value.trim == value =>
      Consequence.success(value)
    case _ => Consequence.configurationInvalid("GoalPhase persistence requires an exact nonblank string")
  }
  private def key[A](name: String)(decode: String => Consequence[A]): ComponentParameterKey[A] =
    ComponentParameterKey.required(prefix + name,
      ComponentParameterDecoder(value => exactString.decode(value).flatMap(decode)))

  val storeName = key("store-name") { value =>
    if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, value).normalizedName == value)
      Consequence.success(value)
    else Consequence.configurationInvalid("GoalPhase logical store name is not canonical")
  }
  val storeIdentity = key("store-identity")(value => Consequence.success(WorkflowStoreIdentity(value)))
  val migrationPolicy = key("migration-policy")(value => Consequence.success(MigrationPolicy(value)))
  val retentionPolicy = key("retention-policy")(value => Consequence.success(RetentionPolicy(value)))
  val leasePolicy = key("lease-policy")(value => Consequence.success(LeasePolicy(value)))
  private def collectionC(value: String): Consequence[DataStore.CollectionId] =
    EntityCollectionId.parse(value).flatMap { identity =>
      if (identity.print == value) Consequence.success(DataStore.CollectionId.EntityStore(identity))
      else Consequence.configurationInvalid("GoalPhase collection identity is not canonical")
    }
  val instances = key("instance-collection")(collectionC)
  val continuations = key("continuation-collection")(collectionC)
  val declarations: Vector[ComponentParameterKey[?]] =
    Vector(storeName, storeIdentity, migrationPolicy, retentionPolicy, leasePolicy, instances, continuations)

  final case class Persistence(configuration: Configuration, instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId)

  def decodeC(parameters: ComponentInitializationParameters): Consequence[Persistence] =
    if (parameters == null)
      Consequence.configurationInvalid("GoalPhase configured persistence is missing")
    else for {
      name <- requiredC(parameters, storeName)
      identity <- requiredC(parameters, storeIdentity)
      migration <- requiredC(parameters, migrationPolicy)
      retention <- requiredC(parameters, retentionPolicy)
      lease <- requiredC(parameters, leasePolicy)
      root <- requiredC(parameters, instances)
      delivery <- requiredC(parameters, continuations)
      _ <- if (root != delivery) Consequence.unit
        else Consequence.configurationInvalid("GoalPhase persistence collections must be distinct")
      configuration <- Configuration(NamedWorkflowStore(name, identity), migration, retention, lease,
        DeliveryConfiguration.SameStore(identity)).validateC
    } yield Persistence(configuration, root, delivery)

  private def requiredC[A](parameters: ComponentInitializationParameters, key: ComponentParameterKey[A]): Consequence[A] =
    parameters.resolve(key).flatMap { result =>
      result.value match {
        case Some(value) => Consequence.success(value)
        case None => Consequence.configurationInvalid("GoalPhase required persistence parameter is absent")
      }
    }
}
