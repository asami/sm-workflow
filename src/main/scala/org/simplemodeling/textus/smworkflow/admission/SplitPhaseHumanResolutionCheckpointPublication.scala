package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition as M
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.configuration.{ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record as StorageRecord
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseHumanResolutionCheckpointPreparation as C,
  SplitPhaseHumanResolutionCheckpointJsonV1 as J, SplitPhaseHumanResolutionCheckpointInspection as I,
  SplitPhaseScopeProjectionSnapshot as V, SplitPhasePlanningCheckpointPublication as U,
  SplitPhaseExplicitPlanningInputJsonV1 as F}

/** Human successor purpose metadata and complete passive Create descriptors.
  * No provider, pool, execution context or commit authority is retained here.
  *
  * @since Oct.  5, 2026
  * @version Oct.  5, 2026
  */
private[smworkflow] object SplitPhaseHumanResolutionCheckpointPublication {
  val prefix = "sm-workflow.split-phase.human-checkpoints."
  val MAX_TOTAL_CREATES = 64
  val NATIVE_RECEIPT_CREATES = 1

  final class Targets private[SplitPhaseHumanResolutionCheckpointPublication] (
    val documents: U.Targets, val publication: DataStore.CollectionId) {
    def storeName: String = documents.storeName
  }
  final class Prepared private[SplitPhaseHumanResolutionCheckpointPublication] (
    val captured: C.Captured, val targets: Targets, val rows: Vector[U.Row],
    val anticipatedNativeBytes: String)

  /** The new purpose is required only when this boundary is selected. Fresh
    * resolution input is optional for historical observation; if declared it
    * must not alias the saved result or any other protected purpose.
    */
  def bindTargetsC(host: ResolvedConfiguration, workflow: SplitPhaseManagedStartAssembly.Declaration):
      Consequence[Targets] = _guarded {
    if (host == null || workflow == null || workflow.configuration == null)
      _invalid("host or Workflow declaration is absent")
    else for {
      documents <- U.bindTargetsC(host, workflow)
      store <- _string_c(host, "store-name")
      _ <- if (ComponentDataStore.Request.forComponent(SmWorkflowComponent.componentId, store).normalizedName == store &&
          store == documents.storeName) Consequence.unit else _invalid("store differs from the canonical Workflow store")
      raw <- _string_c(host, "record-collection")
      identity <- EntityCollectionId.parse(raw)
      _ <- if (identity.print == raw) Consequence.unit else _invalid("collection is not canonical")
      input <- SplitPhasePlanningInputReadPolicy.decodeC(host)
      context <- SplitPhasePartitionContextReadPolicy.decodeC(host)
      start <- SplitPhaseTypedStartReadPolicy.decodeC(host)
      selection <- SplitPhaseHostSelectionReadPolicy.decodeC(host)
      collection = DataStore.CollectionId.EntityStore(identity)
      reserved = documents.localCollections ++ Vector(workflow.instances, workflow.continuations,
        context.collection, start.collection, selection.collection) ++ F.Purpose.values.toVector.map(input.collection)
      _ <- if (!reserved.contains(collection)) Consequence.unit else _invalid("publication aliases a protected purpose")
      _ <- _optional_input_c(host, store, collection)
    } yield new Targets(documents, collection)
  }

  def prepareC(captured: C.Captured, targets: Targets, definition: DefinitionBinding): Consequence[Prepared] = _guarded {
    if (captured == null || targets == null || definition == null)
      _invalid("private capture, targets or selected definition is absent")
    else for {
      _ <- I.inspectC(captured.record, definition)
      record = captured.record
      expected = M.Intent(record.priorScope.snapshot.first.originalStart.configuration,
        M.Reference(record.reference.identity, record.reference.revision), record.before,
        record.before.revision, record.next.history.last, TypedValue(C.payloadType, record))
      _ <- if (captured.intent == expected && expected.configuration.workflowStore.name == targets.storeName)
        Consequence.unit else _invalid("captured intent or declared store differs")
      application <- J.encodeC(record, definition)
      native <- J.encodeNativeC(record, definition)
      key <- C.keyC(record.reference)
      documents <- record.frontier.collect {
        case V.Candidate(_, _, V.CandidateOutput.Built(value, false)) => value
      }.foldLeft(Consequence.success(Vector.empty[U.Row])) { (prior, value) => for {
        accumulated <- prior
        proposal <- F.encodeProposalC(value.proposal)
        ownership <- F.encodeOwnershipC(value.ownership)
        handoffs <- F.encodeHandoffsC(value.handoffs)
      } yield accumulated ++ Vector(
        U.Row(targets.documents.proposals, value.proposal.reference.identity, StorageRecord.data(F.storageField -> proposal)),
        U.Row(targets.documents.ownerships, value.ownership.reference.identity, StorageRecord.data(F.storageField -> ownership)),
        U.Row(targets.documents.handoffs, value.handoffs.reference.identity, StorageRecord.data(F.storageField -> handoffs))) }
      rows = documents :+ U.Row(targets.publication, key, StorageRecord.data(J.storageField -> application))
      _ <- validateRowsC(rows, targets)
    } yield new Prepared(captured, targets, rows, native)
  }

  /** TOTAL capacity includes the additional native receipt. Closed purposes,
    * complete UTF-8 keys/frames and unique Creates are checked before staging.
    */
  def validateRowsC(rows: Vector[U.Row], targets: Targets): Consequence[Unit] = _guarded {
    if (rows == null || targets == null || rows.size > MAX_TOTAL_CREATES - NATIVE_RECEIPT_CREATES ||
        rows.exists(_ == null) || rows.map(row => (row.collection, row.identity)).distinct.size != rows.size)
      _invalid("Create set is absent, duplicated or exceeds64 TOTAL Creates")
    else rows.foldLeft(Consequence.unit) { (prior, row) => prior.flatMap { _ =>
      val document = Vector(targets.documents.proposals, targets.documents.ownerships,
        targets.documents.handoffs).contains(row.collection)
      val field = if (row.collection == targets.publication) J.storageField else F.storageField
      if (!(row.collection == targets.publication || document) || !_canonical(row.identity) ||
          row.identity.getBytes(UTF_8).length > 1024 || row.payload == null || row.payload.fields.size != 1 ||
          row.payload.fields.head.key != field)
        _invalid("Create identity, purpose or complete payload field differs")
      else row.payload.getString(field) match {
        case Some(wire) if UTF_8.newEncoder().canEncode(wire) && wire.getBytes(UTF_8).length <= J.maximumPayloadBytes =>
          Consequence.unit
        case _ => _invalid("Create payload is not a complete bounded UTF-8 String")
      }
    }}
  }

  private def _optional_input_c(host: ResolvedConfiguration, store: String,
    publication: DataStore.CollectionId): Consequence[Unit] = {
    val inputprefix = SplitPhaseHumanResolutionReadPolicy.prefix
    val declared = Vector("store-name", "record-collection").exists(key =>
      GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, inputprefix + key).nonEmpty)
    if (!declared) Consequence.unit
    else SplitPhaseHumanResolutionReadPolicy.decodeC(host).flatMap { input =>
      if (input.storeName == store && input.collection != publication) Consequence.unit
      else _invalid("fresh Human input aliases publication or crosses the Workflow store")
    }
  }
  private def _string_c(host: ResolvedConfiguration, key: String): Consequence[String] =
    GoalPhaseHostRecordReadPolicy.storedValue(host.configuration.values, prefix + key) match {
      case Some(ConfigurationValue.StringValue(value)) if _canonical(value) => Consequence.success(value)
      case _ => _invalid("exact declared publication metadata is absent")
    }
  private def _canonical(value: String): Boolean = value != null && value.nonEmpty && value.trim == value &&
    !value.exists(Character.isISOControl) && UTF_8.newEncoder().canEncode(value)
  private def _invalid[A](reason: String): Consequence[A] =
    Consequence.stateConflict("SplitPhase Human successor publication " + reason)
  private def _guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => _invalid("complete source, purpose metadata or descriptors are malformed")
  }
}
