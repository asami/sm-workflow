package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.{WorkflowMechanicalTransition as M, WorkflowMechanicalTransitionJsonV1,
  WorkflowCompletionRecordJsonV1, WorkflowInstanceRecordJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.configuration.ResolvedConfiguration
import org.goldenport.record.Record as StorageRecord
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningCheckpointJsonV1 as J, SplitPhasePlanningSnapshot as R,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPartitionSemanticJson as SJ,
  SplitPhaseExplicitPlanningInputJsonV1 as F}

/** Complete Create data and codec preflight only. No store/lease/program is
  * retained or executed; native execution must separately admit its public call.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
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
    val targets: Targets, val rows: Vector[Row], val anticipatedNativeBytes: String)

  def bindTargetsC(host: ResolvedConfiguration, declaration: SplitPhaseManagedStartAssembly.Declaration): Consequence[Targets] =
    SplitPhasePartitionEnrichmentStorage.bindC(host, declaration).map(p => new Targets(p.storeName, p.collection,
      p.planningCollection, p.proposalCollection, p.ownershipCollection, p.handoffsCollection, p.estimatesCollection,
      declaration.instances, declaration.continuations))

  def indexKeyC(instance: InstanceIdentity, context: S.ContextReference): Consequence[String] = guarded {
    if (instance == null || context == null || !canonical(instance.value) || !canonical(context.identity))
      invalid("context locator is incomplete")
    else M.keyC(M.Reference(Json.arr(Json.fromString(instance.value), Json.fromString(context.identity)).noSpaces,
      context.revision)).flatMap(key => finalKeyC("planning-context:" + key))
  }
  def mappingKeyC(reference: K.Reference): Consequence[String] = guarded {
    if (reference == null) invalid("mapping locator is absent")
    else M.keyC(M.Reference(reference.identity, reference.revision)).flatMap(key => finalKeyC("planning-snapshot:" + key))
  }

  def prepareC(captured: K.Captured, targets: Targets, closingEffects: Vector[Row] = Vector.empty): Consequence[Prepared] = guarded {
    if (captured == null || targets == null || closingEffects == null || captured.record.originalStart.configuration.workflowStore.name != targets.storeName)
      invalid("publication preparation is incomplete or crosses its store")
    else for {
      _ <- K.inspectC(captured.record)
      _ <- if (captured.index == K.index(captured.record) && captured.next.revision == captured.record.committedRevision &&
          captured.before.identity == captured.record.originalStart.instanceIdentity && captured.next.identity == captured.before.identity)
        Consequence.unit else invalid("captured publication lineage differs")
      before <- WorkflowInstanceRecordJsonV1.encodeC(captured.before)
      next <- WorkflowInstanceRecordJsonV1.encodeC(captured.next)
      native <- captured.record.origin match {
        case K.Origin.Mechanical(anchor) => captured.mechanicalIntent match {
          case Some(intent) if captured.completion.isEmpty && intent.reference == anchor &&
              intent.current == captured.before && intent.payload.value == captured.record =>
            WorkflowMechanicalTransitionJsonV1.encodeC(M.Receipt(anchor, SmWorkflowComponent.componentId,
              K.advanceOperation, captured.before, captured.next, intent.payload), J.payloadCodec)
          case _ => invalid("mechanical intent differs from its captured record")
        }
        case _: K.Origin.PartitionCompletion => captured.completion match {
          case Some(value) if captured.mechanicalIntent.isEmpty => WorkflowCompletionRecordJsonV1.encodeC(value, SJ.resultCodec)
          case _ => invalid("actual completion envelope is absent")
        }
      }
      mapping <- J.encodeC(captured.record)
      index <- J.encodeIndexC(captured.index)
      mappingKey <- mappingKeyC(captured.record.reference)
      indexKey <- indexKeyC(captured.index.instance, captured.index.context)
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
        row(targets.publication, mappingKey, J.storageField, mapping), row(targets.publication, indexKey, J.indexStorageField, index))
      _ <- validateRowsC(rows, targets)
    } yield new Prepared(captured, targets, rows, native)
  }

  /** Passive descriptor capacity, not provider acknowledgement or a grant. */
  def validateRowsC(rows: Vector[Row], targets: Targets): Consequence[Unit] = guarded {
    if (rows == null || targets == null || rows.size > 64 || rows.exists(_ == null) ||
        rows.map(r => (r.collection, r.identity)).distinct.size != rows.size)
      invalid("Create set is incomplete, duplicated or exceeds 64 effects")
    else rows.foldLeft(Consequence.unit) { (previous, r) => previous.flatMap { _ =>
      if (!targets.localCollections.contains(r.collection) || r.collection == targets.instances ||
          r.collection == targets.continuations || !canonical(r.identity) || r.identity.getBytes(UTF_8).length > 1024 ||
          r.payload == null || r.payload.fields.size != 1 || r.payload.fields.exists(_.key == "id"))
        invalid("Create target or payload differs from declared local purposes")
      else r.payload.getString(r.payload.fields.head.key) match {
        case Some(wire) if wire.getBytes(UTF_8).length <= J.maximumPayloadBytes => Consequence.unit
        case _ => invalid("Create payload is not a bounded complete string frame")
      }
    }}
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
