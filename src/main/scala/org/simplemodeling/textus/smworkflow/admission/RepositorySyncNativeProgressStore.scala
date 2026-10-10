package org.simplemodeling.textus.smworkflow.admission

import cats.syntax.all.*
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import RepositorySyncManagedGitObservation as Git

/** Per-call metadata binding only. Every transition borrows canonical shared
  * storage; Git effects are not captured in the DB completion transaction.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncNativeProgressStore {
  val schema = "sm-workflow.repository-sync-native-progress.v3"
  final case class Payload(stage: String, root: String,
    before: Git.Observed, indexTree: Option[String] = None, checkpoint: Option[String] = None,
    after: Option[Git.Observed] = None, method: Option[String] = None, mergeHead: Option[String] = None,
    materialization: Option[String] = None, validation: Option[RepositorySyncNativeMergeValidation.State] = None,
    finalTip: Option[String] = None)
  final case class Stage(record: InstanceRecord, payload: Payload)
  private given Encoder[RepositorySyncNativeMergeValidation.Check] = deriveEncoder
  private given Decoder[RepositorySyncNativeMergeValidation.Check] = deriveDecoder
  private given Encoder[RepositorySyncNativeMergeValidation.Receipt] = deriveEncoder
  private given Decoder[RepositorySyncNativeMergeValidation.Receipt] = deriveDecoder
  private given Encoder[RepositorySyncNativeMergeValidation.State] = Encoder.instance(RepositorySyncNativeMergeValidation.stateCodec.encode)
  private given Decoder[RepositorySyncNativeMergeValidation.State] = Decoder.instance(cursor =>
    RepositorySyncNativeMergeValidation.stateCodec.decode(cursor.value).left.map(reason => DecodingFailure(reason, cursor.history)))
  private given Encoder[Payload] = deriveEncoder
  private given Decoder[Payload] = deriveDecoder
  private val stages = Set("Preserving", "IndexCaptured", "CheckpointCreating", "CheckpointCreated",
    "CheckpointInstalling", "LocalStatePreserved", "Integrating", "Integrated", "PendingMergePrepared", "MergeConflicted", "MaterializingConflict",
    "ValidationChecking", "MergeValidated", "IntegrationValidationChecking", "IntegrationValidated", "ReviewAccepted", "NativeCommitPreparing", "NativeCommitObserved",
    "NativePushPreparing", "NativePushObserved", "NativeFinalFetching", "Converged")
  val codec: WorkflowResultJsonV1.PayloadCodec[Payload] = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "RepositorySyncAdvanceResult"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      root <- Option(value).flatMap(_.asObject).filter(_.keys.toSet == Set("schemaVersion", "payload"))
        .toRight("Native repository progress envelope differs")
      version <- root("schemaVersion").flatMap(_.asString).toRight("Native progress schema is missing")
      _ <- Either.cond(Set(schema, "sm-workflow.repository-sync-native-progress.v1", "sm-workflow.repository-sync-native-progress.v2")(version), (), "Native progress schema differs")
      fields <- root("payload").flatMap(_.asObject).filter { fields =>
        val required = Set("stage", "root", "before", "indexTree", "checkpoint", "after", "method", "mergeHead")
        val legacy = if (version != schema) Set("originalCommand") else Set.empty[String]
        required.subsetOf(fields.keys.toSet) && fields.keys.toSet.subsetOf(required ++ Set("materialization", "validation", "finalTip") ++ legacy)
      }.toRight("Native progress payload differs")
      normalized <- if (version.endsWith(".v1")) fields("validation").filterNot(_.isNull) match {
        case Some(value) => RepositorySyncNativeMergeValidation.legacyStateJson(value).map(v => fields.add("validation", v))
        case None => Right(fields)
      } else Right(fields)
      payload <- Json.fromJsonObject(normalized.remove("originalCommand")).as[Payload].left.map(_ => "Native progress data is incomplete")
      _ <- Either.cond(stages(payload.stage) && payload.before != null &&
        payload.root != null && payload.root.nonEmpty &&
        Vector(payload.indexTree, payload.checkpoint, payload.mergeHead, payload.finalTip).flatten.forall(oid) &&
        (!Set("IndexCaptured", "CheckpointCreating", "CheckpointCreated", "CheckpointInstalling")(payload.stage) || payload.indexTree.nonEmpty) &&
        (!Set("CheckpointCreated", "CheckpointInstalling")(payload.stage) || payload.checkpoint.nonEmpty) &&
        (!Set("LocalStatePreserved", "Integrated", "PendingMergePrepared", "MergeConflicted")(payload.stage) || payload.after.nonEmpty) &&
        (payload.stage != "PendingMergePrepared" || (payload.indexTree.nonEmpty && payload.mergeHead.nonEmpty)) &&
        (payload.stage != "MaterializingConflict" || (payload.materialization.nonEmpty && payload.mergeHead.nonEmpty)) &&
        (!Set("ValidationChecking", "MergeValidated", "ReviewAccepted", "NativeCommitPreparing", "NativeCommitObserved")(payload.stage) || payload.mergeHead.nonEmpty) &&
        (!Set("IntegrationValidationChecking", "IntegrationValidated")(payload.stage) ||
          (payload.mergeHead.isEmpty && payload.method.exists(Set("equal", "local-ahead", "fast-forward")))) &&
        (!Set("ValidationChecking", "MergeValidated", "IntegrationValidationChecking", "IntegrationValidated", "ReviewAccepted", "NativeCommitPreparing", "NativeCommitObserved",
            "NativePushPreparing", "NativePushObserved", "NativeFinalFetching", "Converged")(payload.stage) ||
          (payload.validation.nonEmpty && payload.after.nonEmpty && payload.indexTree.nonEmpty &&
            (payload.mergeHead.nonEmpty || payload.method.exists(Set("equal", "local-ahead", "fast-forward"))))) &&
        (!Set("NativeCommitObserved", "NativePushPreparing", "NativePushObserved", "NativeFinalFetching", "Converged")(payload.stage) ||
          payload.finalTip.nonEmpty),
        (), "Native progress stage shape differs")
    } yield payload
  }

  final class Store private[RepositorySyncNativeProgressStore] (
    val declaration: RepositorySyncManagedFetch.Declaration, val core: ActionCall.Core,
    val original: RepositorySyncManagedFetch.Payload, val command: org.simplemodeling.textus.smworkflow.value.RepositorySyncStartCommand,
    val identity: String,
    adapter: WorkflowManagedMechanicalTransition[Payload], storage: WorkflowComponentStorage,
    acknowledgement: String => Consequence[Unit]) {
    def loadC(key: String): Consequence[Option[Stage]] =
      adapter.loadRetainedC(reference(key))(using core.executionContext).flatMap {
        case None => Consequence.success(None)
        case Some(value) =>
          val p = value.receipt.payload.value
          if (p.root == original.repositoryRoot &&
              value.receipt.next.identity.value == identity &&
              value.receipt.next.currentProgression.exists(_.value == "sync." + p.stage))
            Consequence.success(Some(Stage(value.receipt.next, p)))
          else invalid("saved stage crosses its original selection or native progression")
      }

    /** A fresh external call is allowed only after this actual conditional
      * append/Create succeeds; concurrent writers lose before the Git effect.
      */
    def saveC(key: String, event: String, from: InstanceRecord, payload: Payload): Consequence[Stage] = for {
      _ <- if (from.currentProgression.exists(p => domain.statemachine.RepositorySyncBoundaryLifecycle.permits(
        p.value.stripPrefix("sync."), event, payload.stage))) Consequence.unit
        else invalid("native transition is not declared by CML")
      entry = HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next, Lifecycle.Active,
        Some(ProgressionReference("sync." + payload.stage)), from.correlation,
        CausationReference("native-repository:" + identity + ":" + key),
        from.derivedCompositeOccurrence, from.committedPredecessor, None)
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(declaration.configuration, reference(key),
        from, from.revision, entry, TypedValue(codec.typeIdentity, payload)),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield Stage(saved.receipt.next, saved.receipt.payload.value)

    /** Mechanical preparation accepts Active entries only. Close its actual
      * saved Converged result through the authoritative native instance API.
      * Reopen observes either the preparation or its one completed successor.
      */
    def closeC(prepared: Stage): Consequence[InstanceRecord] = for {
      _ <- if (prepared.payload.stage == "Converged" &&
          prepared.record.currentProgression.contains(ProgressionReference("sync.Converged"))) Consequence.unit
        else invalid("native closure has no actual saved convergence")
      entry = HistoryEntry(HistorySequence(prepared.record.history.size.toLong + 1), prepared.record.revision.next,
        Lifecycle.Completed, None, prepared.record.correlation,
        CausationReference("native-repository-completed:" + identity),
        prepared.record.derivedCompositeOccurrence, prepared.record.committedPredecessor, None)
      next <- prepared.record.appendC(prepared.record.revision, entry)
      instances <- storage.bindInstancesC()(using core.executionContext)
      actual <- instances.loadC(prepared.record.identity)(using core.executionContext).flatMap {
        case Some(value) => Consequence.success(value)
        case None => invalid("actual convergence instance is missing")
      }
      completed <- if (SplitPhaseDefinitionBinding.sameRecord(actual, next)) Consequence.success(actual)
        else if (SplitPhaseDefinitionBinding.sameRecord(actual, prepared.record))
          instances.appendC(prepared.record.identity, prepared.record.revision, entry)(using core.executionContext)
        else invalid("native current differs from saved convergence or its completed successor")
    } yield completed

    def payload(stage: String, before: Git.Observed): Payload = Payload(stage, original.repositoryRoot, before)
    def scopeC(actual: Git.Observed): Consequence[Unit] = Git.rootC(core).flatMap(root =>
      if (root == original.repositoryRoot && actual.branch == declaration.input.localBranch) Consequence.unit
      else invalid("actual root/branch crosses the original host mapping"))
    def acknowledgeC(stage: String): Consequence[Unit] = acknowledgement(stage) match {
      case Consequence.Success(_) => Consequence.unit
      case _ => uncertain("native " + stage + " acknowledgement interrupted; observe on a later call")
    }
    private def reference(key: String) = WorkflowMechanicalTransition.Reference(identity + ":native-" + key, "1")
  }
  def bindC(session: RepositorySyncManagedFetch.Session, declaration: RepositorySyncManagedFetch.Declaration,
    core: ActionCall.Core, acknowledgement: String => Consequence[Unit]): Consequence[Store] =
    session.storage.bindMechanicalC(RepositorySyncManagedFetch.advanceOperation, codec).map(adapter =>
      new Store(declaration, core, session.started.receipt.payload.value, session.command,
        session.started.receipt.next.identity.value, adapter, session.storage, acknowledgement))
  def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync native progression " + reason)
  def uncertain[A](reason: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate("RepositorySync native progression " + reason)
  private def oid(value: String) = value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
}
