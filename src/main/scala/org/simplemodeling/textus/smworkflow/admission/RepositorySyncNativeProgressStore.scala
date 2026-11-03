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
  *  version Oct. 28, 2026
  * @version Nov.  3, 2026
  */
private[smworkflow] object RepositorySyncNativeProgressStore {
  val schema = "sm-workflow.repository-sync-native-progress.v3"
  final case class Payload(stage: String, root: String,
    before: Git.Observed, indexTree: Option[String] = None, checkpoint: Option[String] = None,
    after: Option[Git.Observed] = None, method: Option[String] = None, mergeHead: Option[String] = None,
    validation: Option[RepositorySyncNativeMergeValidation.State] = None,
    finalTip: Option[String] = None, assessment: Option[RepositorySyncMergeAssessment.Result] = None,
    repairContext: Option[RepositorySyncNativeMergeReview.RepairContext] = None,
    continuation: Option[RepositorySyncValidationContinuation.Direction] = None,
    hygiene: Option[CommitHygiene.Record] = None,
    records: Option[CompletionRecords.Plan] = None,
    purposes: Option[RepositorySyncPurpose.Progress] = None,
    requiredLint: Option[RepositorySyncRequiredLintContinuation.Direction] = None,
    reviewInputs: Option[RepositorySyncReviewInputContinuation.Direction] = None,
    formalFailure: Option[RepositorySyncFormalFailureContinuation.Direction] = None,
    purposeContent: Option[RepositorySyncPurposeContentContinuation.Direction] = None)
  final case class Stage(record: InstanceRecord, payload: Payload)
  final case class Candidate(namespace: String, pending: Stage)
  def candidateKey(namespace: String, key: String): String =
    if (namespace.isEmpty) key else namespace + "-" + key
  private given Encoder[RepositorySyncNativeMergeValidation.State] = Encoder.instance(RepositorySyncNativeMergeValidation.stateCodec.encode)
  private given Decoder[RepositorySyncNativeMergeValidation.State] = Decoder.instance(cursor =>
    RepositorySyncNativeMergeValidation.stateCodec.decode(cursor.value).left.map(reason => DecodingFailure(reason, cursor.history)))
  private given Encoder[Payload] = deriveEncoder[Payload].mapJson(_.mapObject(_.filter {
    case ("assessment" | "repairContext" | "continuation" | "hygiene" | "records" | "purposes" | "requiredLint" | "reviewInputs" | "formalFailure" | "purposeContent", value) => !value.isNull
    case _ => true
  }))
  private given Decoder[Payload] = deriveDecoder
  val codec: WorkflowResultJsonV1.PayloadCodec[Payload] = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "RepositorySyncNativeProgressReceipt"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("Native repository progress is missing")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(version == schema, (), "Native progress schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
      _ <- Either.cond(Vector("formalFailure", "purposeContent").forall(name =>
        !wire.hcursor.downField("payload").downField(name).focus.exists(_.isNull)), (), "Continuation provenance cannot be explicitly null")
      _ <- Either.cond(!wire.hcursor.downField("payload").downField("hygiene").focus.exists(_.isNull) &&
        payload.hygiene != null && payload.hygiene.forall(CommitHygiene.valid), (), "Invalid commit hygiene receipt")
    } yield payload
  }

  final class Store private[RepositorySyncNativeProgressStore] (
    val declaration: RepositorySyncManagedFetch.Declaration, val core: ActionCall.Core,
    val original: RepositorySyncManagedFetch.Payload, val context: RepositorySyncManagedFetch.StartContext,
    val identity: String,
    adapter: WorkflowManagedMechanicalTransition[Payload], val storage: WorkflowComponentStorage,
    acknowledgement: String => Consequence[Unit],
    val namespace: String = "",
    val continuation: Option[RepositorySyncValidationContinuation.Direction] = None) {
    def scoped(namespace: String, direction: Option[RepositorySyncValidationContinuation.Direction] = None): Store =
      new Store(declaration, core, original, context, identity, adapter, storage, acknowledgement, namespace, direction)
    def key(key: String): String = candidateKey(namespace, key)
    def reference(key: String): WorkflowMechanicalTransition.Reference =
      WorkflowMechanicalTransition.Reference(identity + ":native-" + this.key(key), "1")
    /** Select the latest actual immutable inventory, including an explicitly
      * superseded inventory. Issuance history alone cannot create a snapshot. */
    def currentPurposeInventoryC(current: InstanceRecord): Consequence[Option[Stage]] = {
      val prefix = "native-repository:" + identity + ":"
      if (current.identity.value != identity) return invalid("purpose inventory history crosses its instance")
      current.history.reverse.find(entry => entry.currentProgression.contains(ProgressionReference("sync.PurposeInventory")) &&
          entry.causation.value.startsWith(prefix)) match {
        case None => Consequence.success(None)
        case Some(entry) => for {
          value <- loadC(entry.causation.value.stripPrefix(prefix)).flatMap(_.map(Consequence.success)
            .getOrElse(invalid("selected immutable purpose inventory is absent")))
          actual <- RepositorySyncPurposeContentContinuation.prefixC(current, entry.revision.value.toString)
          _ <- if (actual == value.record && value.payload.stage == "PurposeInventory" && value.payload.purposes.exists(p =>
              p.plan.isEmpty && p.candidate.isEmpty && p.commits.isEmpty)) Consequence.unit
            else invalid("selected purpose inventory crosses its native history")
          _ <- value.payload.purposeContent match {
            case None => if (entry.causation.value == prefix + "purpose-inventory") Consequence.unit
              else invalid("a replacement purpose inventory has no admitted provenance")
            case Some(direction) => RepositorySyncPurposeContentContinuation.selectedC(this, value.record).flatMap {
              case Some(saved) if saved.direction == direction && value.record.revision == saved.revision.next &&
                  entry.causation.value == prefix + RepositorySyncPurposeContentContinuation.inventoryKey(direction.target) &&
                  value.payload.purposes.exists(_.request == direction.target.current) => Consequence.unit
              case _ => invalid("fresh purpose inventory has no exact native supersession")
            }
          }
        } yield Some(value)
      }
    }
    /** The current native history selects the actual candidate receipt. A
      * reference is an ordinary progression link, not an unchanged-data seal.
      */
    def currentPurposeC(current: InstanceRecord): Consequence[Option[Stage]] = {
      if (current.identity.value != identity) return invalid("current purpose history crosses its instance")
      if (!RepositorySyncPurposeRuntime.selected(current)) return Consequence.success(None)
      val prefix = "native-repository:" + identity + ":"
      current.history.reverse.find(entry => entry.currentProgression.exists(p => Set("sync.PurposeChecked", "sync.PurposeRepairRequired", "sync.PendingMergePrepared")(p.value)) &&
        entry.causation.value.startsWith(prefix)) match {
        case None => Consequence.success(None)
        case Some(entry) if entry.currentProgression.contains(ProgressionReference("sync.PendingMergePrepared")) => Consequence.success(None)
        case Some(entry) => loadC(entry.causation.value.substring(prefix.length))
      }
    }
    def currentMergeCandidateC(current: InstanceRecord): Consequence[Candidate] = {
      if (current.identity.value != identity) return invalid("current candidate history crosses its instance")
      val selected = current.history.reverse.find(entry =>
        entry.currentProgression.contains(ProgressionReference("sync.PendingMergePrepared")))
      selected match {
        case None => invalid("actual pending merge history source is absent")
        case Some(entry) =>
          val pattern = (java.util.regex.Pattern.quote("native-repository:" + identity + ":") +
            "((?:repair-|required-lint-|formal-failure-)[1-9][0-9]*)-pending-merge").r
          entry.causation.value match {
            case cause if cause == "native-repository:" + identity + ":pending-merge" => loadC("pending-merge").flatMap {
              case Some(value) if value.payload.stage == "PendingMergePrepared" && value.record.revision == entry.revision &&
                  value.payload.requiredLint.isEmpty => Consequence.success(Candidate("", value))
              case _ => invalid("original pending merge receipt is absent or crossed")
            }
            case pattern(namespace) => loadC(candidateKey(namespace, "pending-merge")).flatMap {
              case Some(value) if value.payload.stage == "PendingMergePrepared" && value.record.revision == entry.revision =>
                if (namespace.startsWith("formal-failure-"))
                  RepositorySyncFormalFailureContinuation.authenticateCandidateC(this, current, value, namespace)
                    .map(_ => Candidate(namespace, value))
                else if (namespace.startsWith("required-lint-"))
                  RepositorySyncRequiredLintContinuation.authenticateCandidateC(this, current, value, namespace)
                    .map(_ => Candidate(namespace, value))
                else if (value.payload.requiredLint.isEmpty) Consequence.success(Candidate(namespace, value))
                else invalid("a changed repair candidate cannot inherit required-lint receipts")
              case _ => invalid("selected repaired candidate receipt is absent or has the wrong stage")
            }
            case _ => invalid("selected repaired candidate reference crosses its instance or is malformed")
          }
      }
    }
    def loadC(key: String): Consequence[Option[Stage]] =
      adapter.loadRetainedC(reference(key))(using core.executionContext).flatMap {
        case None => Consequence.success(None)
        case Some(value) =>
          val p = value.receipt.payload.value
          if (p.root == original.repositoryRoot &&
              (namespace.isEmpty || p.continuation == continuation) &&
              value.receipt.next.identity.value == identity &&
              value.receipt.next.currentProgression.exists(_.value == "sync." + p.stage))
            Consequence.success(Some(Stage(value.receipt.next, p)))
          else invalid("saved stage crosses its original selection or native progression")
      }

    /** Record normal progression through the existing managed transition API. */
    def saveC(key: String, event: String, from: InstanceRecord, payload: Payload): Consequence[Stage] = for {
      _ <- if (from.currentProgression.exists(p => domain.statemachine.RepositorySyncBoundaryLifecycle.permits(
        p.value.stripPrefix("sync."), event, payload.stage))) Consequence.unit
        else invalid("native transition is not declared by CML")
      entry = HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next, Lifecycle.Active,
        Some(ProgressionReference("sync." + payload.stage)), from.correlation,
        CausationReference("native-repository:" + identity + ":" + this.key(key)),
        from.derivedCompositeOccurrence, from.committedPredecessor, None)
      // A real repair materializes new inputs. Supplement provenance remains
      // in its original rows and is never a seed for that changed candidate.
      selectedpayload = if (this.key(key).startsWith("repair-") &&
          Set("MaterializingRepair", "PendingMergePrepared")(payload.stage))
          payload.copy(requiredLint = None, reviewInputs = None, formalFailure = None) else payload
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(declaration.configuration, reference(key),
        from, from.revision, entry, TypedValue(codec.typeIdentity, selectedpayload.copy(continuation = continuation.orElse(selectedpayload.continuation)))),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield Stage(saved.receipt.next, saved.receipt.payload.value)

    /** Mechanical preparation accepts Active entries only. Close its actual
      * saved Converged result through the authoritative native instance API.
      */
    def closeC(prepared: Stage): Consequence[InstanceRecord] = for {
      _ <- if (prepared.payload.stage == "Converged" &&
          prepared.record.currentProgression.contains(ProgressionReference("sync.Converged"))) Consequence.unit
        else invalid("native closure has no actual saved convergence")
      entry = HistoryEntry(HistorySequence(prepared.record.history.size.toLong + 1), prepared.record.revision.next,
        Lifecycle.Completed, None, prepared.record.correlation,
        CausationReference("native-repository-completed:" + identity),
        prepared.record.derivedCompositeOccurrence, prepared.record.committedPredecessor, None)
      instances <- storage.bindInstancesC()(using core.executionContext)
      completed <- instances.appendC(prepared.record.identity, prepared.record.revision, entry)(using core.executionContext)
    } yield completed

    def payload(stage: String, before: Git.Observed): Payload =
      Payload(stage, original.repositoryRoot, before, continuation = continuation)
    def scopeC(actual: Git.Observed): Consequence[Unit] = Git.rootC(core).flatMap(root =>
      if (root == original.repositoryRoot && actual.branch == context.localBranch) Consequence.unit
      else invalid("actual root/branch crosses the original host mapping"))
    def acknowledgeC(stage: String): Consequence[Unit] = acknowledgement(stage) match {
      case Consequence.Success(_) => Consequence.unit
      case _ => uncertain("native " + stage + " acknowledgement interrupted; observe on a later call")
    }
  }
  def bindC(session: RepositorySyncManagedFetch.Session, declaration: RepositorySyncManagedFetch.Declaration,
    core: ActionCall.Core, acknowledgement: String => Consequence[Unit]): Consequence[Store] =
    session.storage.bindMechanicalC(RepositorySyncManagedFetch.advanceOperation, codec).map(adapter =>
      new Store(declaration, core, session.started.receipt.payload.value, session.context,
        session.started.receipt.next.identity.value, adapter, session.storage, acknowledgement))
  /** Consume the actual paired committed revision in the current native record;
    * never regenerate or append a historical completion.
    */
  def completionNextC(current: InstanceRecord, revision: InstanceRevision,
    reference: ContextReference, stage: String): Consequence[InstanceRecord] =
    current.history.find(_.revision == revision) match {
      case Some(entry) if entry.currentProgression.contains(ProgressionReference("sync." + stage)) &&
          entry.causation.value == reference.identity && entry.suspension.isEmpty =>
        Consequence.success(current.copy(revision = entry.revision, lifecycle = entry.lifecycle,
          currentProgression = entry.currentProgression, correlation = entry.correlation, causation = entry.causation,
          derivedCompositeOccurrence = entry.derivedCompositeOccurrence, committedPredecessor = entry.committedPredecessor,
          suspension = entry.suspension, history = current.history.takeWhile(_.revision.value <= entry.revision.value)))
      case _ => invalid("paired completion has no actual committed native entry")
    }
  def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("RepositorySync native progression " + reason)
  def uncertain[A](reason: String): Consequence[A] =
    DataStoreConditionalTransitionFailure.transactionIndeterminate("RepositorySync native progression " + reason)
}
