package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.{WorkflowMechanicalReceiptLimits, WorkflowMechanicalReceiptParameters}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/**
 * Logical persistence configuration only; physical storage and grants belong to the host.
 * @since   Oct.  1, 2026
 * @version Oct. 21, 2026
 */
private[smworkflow] object GoalPhaseRuntimeParameters {
  private val _prefix = "sm-workflow.goal-phase.persistence."
  private val _default_receipt_limits = WorkflowMechanicalReceiptLimits.createC(16 * 1024 * 1024)
  private val _exact_string = ComponentParameterDecoder[String] {
    case ConfigurationValue.StringValue(value) if value != null && value.nonEmpty && value.trim == value =>
      Consequence.success(value)
    case _ => Consequence.configurationInvalid("GoalPhase persistence requires an exact nonblank string")
  }
  private def _key[A](name: String)(decode: String => Consequence[A]): ComponentParameterKey[A] =
    ComponentParameterKey.required(_prefix + name,
      ComponentParameterDecoder(value => _exact_string.decode(value).flatMap(decode)))

  val storeName = _key("store-name") { value =>
    if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, value).normalizedName == value)
      Consequence.success(value)
    else Consequence.configurationInvalid("GoalPhase logical store name is not canonical")
  }
  val storeIdentity = _key("store-identity")(value => Consequence.success(WorkflowStoreIdentity(value)))
  val migrationPolicy = _key("migration-policy")(value => Consequence.success(MigrationPolicy(value)))
  val retentionPolicy = _key("retention-policy")(value => Consequence.success(RetentionPolicy(value)))
  val leasePolicy = _key("lease-policy")(value => Consequence.success(LeasePolicy(value)))
  private def _collection_c(value: String): Consequence[DataStore.CollectionId] =
    EntityCollectionId.parse(value).flatMap { identity =>
      if (identity.print == value) Consequence.success(DataStore.CollectionId.EntityStore(identity))
      else Consequence.configurationInvalid("GoalPhase collection identity is not canonical")
    }
  val instances = _key("instance-collection")(_collection_c)
  val continuations = _key("continuation-collection")(_collection_c)
  val declarations: Vector[ComponentParameterKey[?]] =
    Vector(storeName, storeIdentity, migrationPolicy, retentionPolicy, leasePolicy, instances, continuations,
      WorkflowMechanicalReceiptParameters.maxBytes)

  final case class Persistence(configuration: Configuration, instances: DataStore.CollectionId,
    continuations: DataStore.CollectionId)

  def decodeC(parameters: ComponentInitializationParameters): Consequence[Persistence] =
    if (parameters == null)
      Consequence.configurationInvalid("GoalPhase configured persistence is missing")
    else for {
      name <- _required_c(parameters, storeName)
      identity <- _required_c(parameters, storeIdentity)
      migration <- _required_c(parameters, migrationPolicy)
      retention <- _required_c(parameters, retentionPolicy)
      lease <- _required_c(parameters, leasePolicy)
      root <- _required_c(parameters, instances)
      delivery <- _required_c(parameters, continuations)
      configuredlimits <- WorkflowMechanicalReceiptParameters.resolveC(parameters)
      limits <- configuredlimits.fold(_default_receipt_limits)(Consequence.success)
      _ <- if (root != delivery) Consequence.unit
        else Consequence.configurationInvalid("GoalPhase persistence collections must be distinct")
      configuration <- Configuration(NamedWorkflowStore(name, identity), migration, retention, lease,
        DeliveryConfiguration.SameStore(identity), Some(limits.maxBytes)).validateC
    } yield Persistence(configuration, root, delivery)

  private def _required_c[A](parameters: ComponentInitializationParameters, key: ComponentParameterKey[A]): Consequence[A] =
    parameters.resolve(key).flatMap { result =>
      result.value match {
        case Some(value) => Consequence.success(value)
        case None => Consequence.configurationInvalid("GoalPhase required persistence parameter is absent")
      }
    }
}
