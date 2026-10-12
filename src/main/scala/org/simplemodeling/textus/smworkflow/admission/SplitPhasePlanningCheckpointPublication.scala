package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition as M
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.record.Record as StorageRecord
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningCheckpointJsonV1 as J, SplitPhasePlanningSnapshot as R,
  SplitPhaseExplicitPartitionSemantic as S,
  SplitPhaseExplicitPlanningInputJsonV1 as F}

/** Complete Create data only. No speculative native receipt is encoded.
  * No store/lease/program is
  * retained or executed; native execution must separately admit its public call.
  * @since   Oct.  2, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object SplitPhasePlanningCheckpointPublication {
  final case class Row(collection: DataStore.CollectionId, identity: String, payload: StorageRecord)
  final class Targets private[SplitPhasePlanningCheckpointPublication] (val storeName: String,
    val publication: DataStore.CollectionId, val planning: DataStore.CollectionId,
    val proposals: DataStore.CollectionId, val ownerships: DataStore.CollectionId,
    val handoffs: DataStore.CollectionId, val estimates: DataStore.CollectionId,
    val instances: DataStore.CollectionId, val continuations: DataStore.CollectionId) {
    def localCollections: Vector[DataStore.CollectionId] = Vector(publication, planning, proposals, ownerships, handoffs, estimates)
  }
  final class Prepared private[SplitPhasePlanningCheckpointPublication] (val captured: K.Captured,
    val targets: Targets, val rows: Vector[Row])

  def bindTargetsC(host: ResolvedConfiguration, declaration: SplitPhaseManagedStartAssembly.Declaration): Consequence[Targets] =
    SplitPhasePartitionEnrichmentStorage.bindC(host, declaration).map(p => new Targets(p.storeName, p.collection,
      p.planningCollection, p.proposalCollection, p.ownershipCollection, p.handoffsCollection, p.estimatesCollection,
      declaration.instances, declaration.continuations))

  def mappingKeyC(reference: K.Reference): Consequence[String] = guarded {
    if (reference == null) invalid("mapping locator is absent")
    else M.keyC(M.Reference(reference.identity, reference.revision)).flatMap(key => finalKeyC("planning-snapshot:" + key))
  }

  def prepareC(captured: K.Captured, targets: Targets, closingEffects: Vector[Row] = Vector.empty): Consequence[Prepared] = guarded {
    if (captured == null || targets == null || closingEffects == null || captured.record.originalStart.configuration.workflowStore.name != targets.storeName)
      invalid("publication preparation is incomplete or crosses its store")
    else for {
      mapping <- captured.record.origin match {
        case _: K.Origin.Mechanical => J.encodeLocatorC(K.Locator(captured.record.reference, captured.record.origin))
        case _: K.Origin.PartitionCompletion => J.encodeC(captured.record)
      }
      mappingKey <- mappingKeyC(captured.record.reference)
      original = captured.record.originalContext.facts
      effective = captured.record.snapshot.selection
      estimates <- if (effective.partition.estimates == original.partition.estimates) Consequence.success(Vector.empty[Row])
        else F.encodeEstimatesC(effective.partition.estimates).map(raw => Vector(row(targets.estimates,
          effective.partition.estimates.reference.identity, F.storageField, raw)))
      handoffs <- if (effective.boundary.handoffs == original.boundary.handoffs) Consequence.success(Vector.empty[Row])
        else F.encodeHandoffsC(effective.boundary.handoffs).map(raw => Vector(row(targets.handoffs,
          effective.boundary.handoffs.reference.identity, F.storageField, raw)))
      planning <- if (effective.planning == original.planning) Consequence.success(Vector.empty[Row])
        else F.encodePlanningC(effective.planning).map(raw => Vector(row(targets.planning,
          effective.planning.reference.identity, F.storageField, raw)))
      documents <- captured.record.snapshot.projection.toVector.flatMap(_.candidates).collect {
        case b: R.CandidateProjectionView.BuiltView => b
      }.foldLeft(Consequence.success(Vector.empty[Row])) { (previous, document) => for {
        accumulated <- previous
        proposal <- F.encodeProposalC(document.proposal)
        ownership <- F.encodeOwnershipC(document.ownership)
        handoff <- F.encodeHandoffsC(document.handoffs)
      } yield accumulated ++ Vector(row(targets.proposals, document.proposal.reference.identity, F.storageField, proposal),
        row(targets.ownerships, document.ownership.reference.identity, F.storageField, ownership),
        row(targets.handoffs, document.handoffs.reference.identity, F.storageField, handoff)) }
      rows = estimates ++ handoffs ++ planning ++ documents ++ closingEffects ++ Vector(
        row(targets.publication, mappingKey, J.storageField, mapping))
    } yield new Prepared(captured, targets, rows)
  }

  private def row(collection: DataStore.CollectionId, id: String, field: String, wire: String): Row =
    Row(collection, id, StorageRecord.data(field -> wire))
  private def finalKeyC(key: String): Consequence[String] =
    if (key.getBytes(UTF_8).length <= 1024) Consequence.success(key) else invalid("final physical locator exceeds 1024 bytes")
  private def canonical(value: String): Boolean = value != null && value.nonEmpty && value.trim == value &&
    !value.exists(ch => Character.isISOControl(ch)) && UTF_8.newEncoder().canEncode(value)
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase checkpoint publication " + reason)
  private def guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => invalid("data or encoding is invalid")
  }
}
