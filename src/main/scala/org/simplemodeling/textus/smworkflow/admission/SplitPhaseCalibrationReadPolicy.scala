package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Host restriction on three calibration data purposes, not a storage grant.
  * Composition checks the selected Workflow binding once; other workflow
  * application data roles are not prerequisites for this independent reader.
  * @since   Oct.  2, 2026
  * @version Oct. 23, 2026
  */
private[smworkflow] object SplitPhaseCalibrationReadPolicy {
  val prefix = "sm-workflow.split-phase.calibration."
  enum Purpose(val key: String) {
    case Request extends Purpose("request-collection")
    case Snapshot extends Purpose("snapshot-collection")
    case Policy extends Purpose("policy-collection")
  }
  final class Declaration private[SplitPhaseCalibrationReadPolicy] (
    val storeName: String, private val collections: Map[Purpose, DataStore.CollectionId]
  ) {
    def collection(purpose: Purpose): DataStore.CollectionId = collections(purpose)
    private[SplitPhaseCalibrationReadPolicy] def targets = collections.values.toVector
  }

  def decodeC(host: ResolvedConfiguration): Consequence[Declaration] =
    if (host == null) Consequence.configurationInvalid("SplitPhase calibration declaration is absent")
    else for {
      store <- stringC(host, "store-name")
      _ <- if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, store).normalizedName == store)
        Consequence.unit else Consequence.configurationInvalid("SplitPhase calibration store is not canonical")
      collections <- Purpose.values.toVector.foldLeft(Consequence.success(Map.empty[Purpose, DataStore.CollectionId])) {
        (previous, purpose) => for {
          values <- previous
          raw <- stringC(host, purpose.key)
          identity <- EntityCollectionId.parse(raw)
          _ <- if (identity.print == raw) Consequence.unit
            else Consequence.configurationInvalid("SplitPhase calibration collection is not canonical")
        } yield values.updated(purpose, DataStore.CollectionId.EntityStore(identity))
      }
      _ <- if (collections.values.toVector.distinct.size == 3) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase calibration data purposes must be separate")
    } yield new Declaration(store, collections)

  def bindC(host: ResolvedConfiguration, workflow: SplitPhaseManagedStartAssembly.Declaration):
    Consequence[SplitPhaseCalibrationDataStorePort] =
    if (workflow == null || workflow.configuration == null || workflow.instances == null || workflow.continuations == null)
      Consequence.configurationInvalid("SplitPhase calibration requires its Workflow declaration")
    else for {
      _ <- workflow.configuration.validateC
      declared <- decodeC(host)
      _ <- if (declared.storeName == workflow.configuration.workflowStore.name &&
          (declared.targets ++ Vector(workflow.instances, workflow.continuations)).distinct.size == 5) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase calibration must use its Workflow store without aliasing instance storage")
    } yield new SplitPhaseCalibrationDataStorePort(declared)

  def admitC(host: ResolvedConfiguration, purpose: Purpose, store: String,
    collection: DataStore.CollectionId): Consequence[Unit] = decodeC(host).flatMap { declared =>
    if (purpose != null && declared.storeName == store && declared.collection(purpose) == collection) Consequence.unit
    else Consequence.stateConflict("SplitPhase calibration read differs from its declared purpose")
  }

  private def stringC(host: ResolvedConfiguration, key: String): Consequence[String] =
    GoalPhaseInputRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if value != null && value.nonEmpty && value.trim == value =>
        Consequence.success(value)
      case _ => Consequence.configurationInvalid("SplitPhase calibration requires exact host configuration")
    }
}
