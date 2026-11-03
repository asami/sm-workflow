package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as _, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{TypedValue, WorkflowContinuation}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import RepositorySyncManagedFetch as F
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeMergeRepair as M
import RepositorySyncNativeMergeRepairContinuation as R
import RepositorySyncNativeProgressStore as N

/** Automatic file qualification and separate prior-worker lifecycle direction.
  * Native supersession retains the original issue and its UNKNOWN effects.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
private[smworkflow] object RepositorySyncRepairScopeCorrection {
  val purpose = "repository-sync-repair-scope-correction"
  val policy = "sm-workflow.repository-sync-repair-scope-correction.v1"
  val automaticPolicy = "sm-workflow.repository-sync-repair-scope-correction.v2"
  val uncappedPolicy = "sm-workflow.repository-sync-repair-scope-correction.v3"
  val prepareOperation = "prepareRepositorySyncRepairScopeCorrection"
  val describeOperation = "describeRepositorySyncRepairScopeCorrection"
  val adoptionOperation = "adoptRepositorySyncRepairScopeCorrection"
  val resumeCause = "native-repair-scope-adopted:"
  val choices = Vector("AdoptCurrentIntegrationRepairScope", "Defer")
  val lifecycleChoices = Vector("ContinueAfterPriorWorkerEndedAcceptingUnknown", "Defer")
  val automaticMessage = "The exact supplied file inventory was qualified automatically for the retained failed check. " +
    "File eligibility requires no Human permission. The prior available worker may nevertheless have executed: " +
    "only an actual independent lifecycle answer can attest that it ended and accept its UNKNOWN effects. " +
    "Paths are fixed by the prepared inventory; separate ordinary Advance captures and issues them. Defer writes nothing."
  val preparationMessage = "Prepare the exact existing file inventory and causal rationale for the retained failed check. " +
    "The runtime qualifies file eligibility before any prior-worker lifecycle direction; no Human file selection or adoption is available yet."
  private def _automatic(target: Target): Boolean = target != null &&
    (target.correctionPolicy == automaticPolicy || target.correctionPolicy == uncappedPolicy && target.inventory != null && target.inventory.nonEmpty)
  def targetChoices(target: Target): Vector[String] =
    if (_automatic(target)) lifecycleChoices
    else if (target != null && target.correctionPolicy == policy) choices else Vector.empty
  def targetMessage(target: Target): String =
    if (_automatic(target)) automaticMessage
    else if (target != null && target.correctionPolicy == policy) message else preparationMessage
  val message = "No native completion or claim was found for this available repair issue. This does not prove " +
    "nonexecution. The actual Human must attest that the original semantic worker stopped or ended and accept " +
    "its UNKNOWN external proposal effects. Select a nonempty sorted subset of the permitted current integration " +
    "paths within the existing assignment limit. Adoption only stores direction and supersedes that exact issue. " +
    "Separate ordinary Advance captures and issues the selected files. Old checks and receipts remain unchanged; " +
    "the new repaired candidate requires its own checks. Defer writes nothing."

  final case class Target(componentInstance: String, sessionId: String, context: F.StartContext,
    root: String, endpoint: String, frontierRevision: String, frontierCause: String,
    oldContinuationId: String, expectedRevision: String, entityRevision: String,
    sourceRevision: String, candidateReference: ContextReference, candidateRevision: String,
    failureReference: ContextReference, failureRevision: String, failedCheck: RepositorySyncNativeMergeValidation.Receipt,
    localTip: String, remoteTip: String, candidateTree: String, actual: G.Native,
    indexTree: String, changedPaths: Vector[String], oldAssignedPaths: Vector[String],
    maximumFiles: Option[Int], correctionPolicy: String, inventory: Option[RepositorySyncRepairInventory.Scope] = None)
  object Target {
    private def _canonical(value: Json): Json = value.mapObject(_.filter { case (_, field) => !field.isNull }
      .mapValues(_canonical)).mapArray(_.map(_canonical))
    given Encoder[Target] = Encoder.instance[Target](value => _canonical(Json.obj(
      "componentInstance" -> value.componentInstance.asJson,
      "sessionId" -> value.sessionId.asJson,
      "context" -> value.context.asJson,
      "root" -> value.root.asJson,
      "endpoint" -> value.endpoint.asJson,
      "frontierRevision" -> value.frontierRevision.asJson,
      "frontierCause" -> value.frontierCause.asJson,
      "oldContinuationId" -> value.oldContinuationId.asJson,
      "expectedRevision" -> value.expectedRevision.asJson,
      "entityRevision" -> value.entityRevision.asJson,
      "sourceRevision" -> value.sourceRevision.asJson,
      "candidateReference" -> value.candidateReference.asJson,
      "candidateRevision" -> value.candidateRevision.asJson,
      "failureReference" -> value.failureReference.asJson,
      "failureRevision" -> value.failureRevision.asJson,
      "failedCheck" -> value.failedCheck.asJson,
      "localTip" -> value.localTip.asJson,
      "remoteTip" -> value.remoteTip.asJson,
      "candidateTree" -> value.candidateTree.asJson,
      "actual" -> value.actual.asJson,
      "indexTree" -> value.indexTree.asJson,
      "changedPaths" -> value.changedPaths.asJson,
      "oldAssignedPaths" -> value.oldAssignedPaths.asJson,
      "maximumFiles" -> value.maximumFiles.asJson,
      "correctionPolicy" -> value.correctionPolicy.asJson,
      "inventory" -> value.inventory.asJson)))
    private val _raw: Decoder[Target] = Decoder.instance(cursor => for {
      componentinstance <- cursor.get[String]("componentInstance")
      sessionid <- cursor.get[String]("sessionId")
      context <- cursor.get[F.StartContext]("context")
      root <- cursor.get[String]("root")
      endpoint <- cursor.get[String]("endpoint")
      frontierrevision <- cursor.get[String]("frontierRevision")
      frontiercause <- cursor.get[String]("frontierCause")
      oldcontinuationid <- cursor.get[String]("oldContinuationId")
      expectedrevision <- cursor.get[String]("expectedRevision")
      entityrevision <- cursor.get[String]("entityRevision")
      sourcerevision <- cursor.get[String]("sourceRevision")
      candidatereference <- cursor.get[ContextReference]("candidateReference")
      candidaterevision <- cursor.get[String]("candidateRevision")
      failurereference <- cursor.get[ContextReference]("failureReference")
      failurerevision <- cursor.get[String]("failureRevision")
      failedcheck <- cursor.get[RepositorySyncNativeMergeValidation.Receipt]("failedCheck")
      localtip <- cursor.get[String]("localTip")
      remotetip <- cursor.get[String]("remoteTip")
      candidatetree <- cursor.get[String]("candidateTree")
      actual <- cursor.get[G.Native]("actual")
      indextree <- cursor.get[String]("indexTree")
      changedpaths <- cursor.get[Vector[String]]("changedPaths")
      oldassignedpaths <- cursor.get[Vector[String]]("oldAssignedPaths")
      maximumfiles <- cursor.get[Option[Int]]("maximumFiles")
      correctionpolicy <- cursor.get[String]("correctionPolicy")
      inventory <- cursor.get[Option[RepositorySyncRepairInventory.Scope]]("inventory")
    } yield Target(componentinstance, sessionid, context, root, endpoint, frontierrevision,
      frontiercause, oldcontinuationid, expectedrevision, entityrevision, sourcerevision,
      candidatereference, candidaterevision, failurereference, failurerevision, failedcheck,
      localtip, remotetip, candidatetree, actual, indextree, changedpaths, oldassignedpaths,
      maximumfiles, correctionpolicy, inventory))
    given Decoder[Target] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (validTarget(value) && value.asJson == cursor.value) Right(value)
      else Left(io.circe.DecodingFailure("Repair scope target requires the exact complete canonical contract", cursor.history))))
  }
  final case class Answer(choice: String, paths: Vector[String], rationale: String,
    priorExecutionEnded: Boolean, acceptOriginalUncertainty: Boolean)
  final case class Direction(target: Target, paths: Vector[String], choice: String, rationale: String,
    participant: String, reference: ContextReference, priorExecutionEnded: Boolean, acceptOriginalUncertainty: Boolean)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    private val _raw: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (validTarget(value.target) && targetChoices(value.target).headOption.contains(value.choice) && value.asJson == cursor.value &&
          value.target.inventory.forall(_.paths == value.paths) && bindingC(value.target.componentInstance,
          value.target, value.choice, if (_automatic(value.target)) Vector.empty else value.paths,
          value.rationale, value.participant, value.reference,
          value.priorExecutionEnded, value.acceptOriginalUncertainty).isSuccess) Right(value)
      else Left(io.circe.DecodingFailure("Repair scope direction is incomplete or invalid", cursor.history))))
  }
  final case class Checkpoint(direction: Direction, externalEffects: String)
  val checkpointCodec: WorkflowResultJsonV1.PayloadCodec[Checkpoint] = new WorkflowResultJsonV1.PayloadCodec[Checkpoint] {
    val typeIdentity = "RepositorySyncRepairScopeCorrectionCheckpoint"
    def encode(value: Checkpoint): Json = value.asJson
    def decode(value: Json): Either[String, Checkpoint] = for {
      wire <- Option(value).toRight("Repair scope checkpoint is absent")
      parsed <- wire.as[Checkpoint].left.map(_.message)
      _ <- Either.cond(parsed.externalEffects == "UNKNOWN" && parsed.asJson == wire, (), "Original effects must remain UNKNOWN")
    } yield parsed
  }
  final case class Saved(retained: WorkflowManagedWorkOrderSupersession.Retained[Checkpoint]) {
    def direction: Direction = retained.receipt.payload.direction
    def revision: InstanceRevision = retained.receipt.nextRevision
  }
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else N.invalid("repair scope correction requires independent Human evidence")
  }
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _revision(value: String): Boolean = value != null && value.matches("[1-9][0-9]*") &&
    value.toLongOption.exists(_ < Long.MaxValue - 8)
  private def _oid(value: String): Boolean = value != null && value.matches("[0-9a-f]{40}|[0-9a-f]{64}")
  private def _reference(value: ContextReference): Boolean = value != null && _exact(value.identity) && _exact(value.revision)
  private def _paths(value: Vector[String]): Boolean = value != null && value.nonEmpty &&
    value.forall(RepositorySyncNativeConflictInput.relative) && value == value.distinct.sorted
  def validTarget(t: Target): Boolean = t != null && _exact(t.componentInstance) && _exact(t.sessionId) &&
    t.context != null && t.context.productIterator.forall(v => v.isInstanceOf[String] && _exact(v.asInstanceOf[String])) &&
    t.context.syncMode == "FULL_STATE" && _exact(t.root) && scala.util.Try(java.nio.file.Path.of(t.root).isAbsolute).getOrElse(false) &&
    _exact(t.endpoint) && Vector(t.frontierRevision, t.expectedRevision, t.entityRevision, t.sourceRevision,
      t.candidateRevision, t.failureRevision).forall(_revision) && _exact(t.frontierCause) &&
    t.oldContinuationId == t.sessionId + ":native-repair:" + t.sourceRevision &&
    t.expectedRevision == t.frontierRevision && t.entityRevision == "1" &&
    t.candidateRevision.toLong < t.failureRevision.toLong && t.failureRevision.toLong < t.sourceRevision.toLong &&
    t.sourceRevision.toLong < t.frontierRevision.toLong &&
    _reference(t.candidateReference) && t.candidateReference.identity.startsWith(t.sessionId + ":native-") &&
    t.candidateReference.revision == "1" && _reference(t.failureReference) &&
    t.failureReference.identity.startsWith(t.sessionId + ":native-") && t.failureReference.revision == "1" &&
    t.failedCheck != null && _exact(t.failedCheck.identity) && t.failedCheck.termination == "exited" &&
    t.failedCheck.exitCode.exists(_ != 0) && Vector(t.localTip, t.remoteTip, t.candidateTree, t.indexTree).forall(_oid) &&
    t.candidateTree == t.indexTree && t.actual != null && t.actual.observed != null &&
    _oid(t.actual.observed.head) && t.actual.observed.head == t.localTip && t.actual.mergeHead.contains(t.remoteTip) &&
    _oid(t.actual.observed.tree) && t.actual.observed.branch == t.context.localBranch &&
    t.actual.observed.dirty != null && !t.actual.observed.dirty.exists(_.unmerged) &&
    t.actual.observed.dirty.forall(p => RepositorySyncNativeConflictInput.relative(p.path) &&
      p.renamedFrom != null && p.renamedFrom.forall(RepositorySyncNativeConflictInput.relative) &&
      " MADRCU?!T".contains(p.index) && " MADRCU?!T".contains(p.worktree)) &&
    t.actual.observed.trackingTip.contains(t.remoteTip) && t.actual.observed.counts != null &&
    t.actual.observed.counts.exists(c => c.localAhead > 0 && c.remoteAhead > 0) &&
    t.actual.observed.mergeBase != null && t.actual.observed.mergeBase.exists(_oid) &&
    _paths(t.changedPaths) && _paths(t.oldAssignedPaths) &&
    t.oldAssignedPaths.forall(t.changedPaths.contains) &&
    t.maximumFiles != null && t.inventory != null &&
    (if (t.correctionPolicy == uncappedPolicy) t.maximumFiles.isEmpty && t.inventory.forall(scope => _valid_inventory(t, scope))
      else t.maximumFiles.exists(limit => limit > 0 && limit <= 128 && t.oldAssignedPaths.size <= limit) &&
        (if (t.correctionPolicy == policy) t.inventory.isEmpty && t.changedPaths.exists(p => !t.oldAssignedPaths.contains(p))
          else t.correctionPolicy == automaticPolicy && t.inventory.exists(scope =>
            _valid_inventory(t, scope) && t.maximumFiles.exists(scope.paths.size <= _))))
  private def _valid_inventory(t: Target, scope: RepositorySyncRepairInventory.Scope): Boolean = scope != null &&
        scope.inventoryPolicy == RepositorySyncRepairInventory.policy && scope.instanceIdentity == t.sessionId &&
        scope.sourceRevision == t.sourceRevision && scope.failureReference == t.failureReference &&
        scope.failureIdentity == t.failedCheck.identity && scope.localTip == t.localTip && scope.remoteTip == t.remoteTip &&
        scope.candidateTree == t.candidateTree && _revision(scope.validatedRevision) &&
        scope.validatedRevision.toLong < t.sourceRevision.toLong && _paths(scope.paths) &&
        scope.rationale != null && scope.rationale.trim.nonEmpty && !scope.rationale.contains('\u0000') &&
        java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(scope.rationale) && scope.additions != null &&
        scope.additions.forall(entry => entry != null && Set("100644", "100755")(entry.mode) && _oid(entry.blob)) &&
        scope.additions.map(_.path) == scope.paths.filterNot(t.changedPaths.contains)
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Repair scope target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def decodePublicTarget(value: Json): Either[String, Target] = GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeTarget)
  def targetC(value: Record): Consequence[Target] = if (value == null) N.invalid("repair scope target is absent")
    else value.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(decodePublicTarget).fold(N.invalid, Consequence.success))
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def decodeAnswer(value: Json): Either[String, Answer] = for {
    wire <- Option(value).toRight("Repair scope answer is absent")
    answer <- wire.as[Answer].left.map(_.message)
    _ <- Either.cond(answer.asJson == wire && (choices ++ lifecycleChoices).contains(answer.choice) && _exact(answer.rationale) &&
      answer.paths != null && (if (answer.choice == "Defer") answer.paths.isEmpty
        else (if (answer.choice == lifecycleChoices.head) answer.paths.isEmpty else _paths(answer.paths)) &&
          answer.priorExecutionEnded && answer.acceptOriginalUncertainty), (),
      "Repair scope answer requires exact fields, explicit selected paths and terminal/uncertainty acceptance")
  } yield answer
  def decodePublicAnswer(value: Json): Either[String, Answer] = GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeAnswer)
  def bindingC(component: String, target: Target, choice: String, paths: Vector[String], rationale: String,
    participant: String, reference: ContextReference, ended: Boolean, accepted: Boolean): Consequence[Json] =
    if (!validTarget(target) || component != target.componentInstance || participant != target.context.participantIdentity ||
        !targetChoices(target).contains(choice) || !_exact(rationale) || !_reference(reference) || paths == null ||
        (if (choice == "Defer") paths.nonEmpty else
          (if (_automatic(target)) paths.nonEmpty
            else !_paths(paths) || target.maximumFiles.exists(paths.size > _) || !paths.forall(target.changedPaths.contains)) ||
          !ended || !accepted))
      N.invalid("repair scope requires the exact prepared target, matching paths, participant, reference and explicit terminal/uncertainty assertions")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson,
      "repositoryRepairScopeCorrection" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "paths" -> paths.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson,
          "priorExecutionEnded" -> ended.asJson, "acceptOriginalUncertainty" -> accepted.asJson))))
  def selectBinding(value: Json): Either[String, Json] = for {
    wire <- Option(value).toRight("Repair scope binding is absent")
    component <- wire.hcursor.get[String]("componentInstance").left.map(_.message)
    raw <- wire.hcursor.downField("repositoryRepairScopeCorrection").get[Json]("target").left.map(_.message)
    target <- decodeTarget(raw)
    answer = wire.hcursor.downField("repositoryRepairScopeCorrection").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    paths <- answer.get[Vector[String]]("paths").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    ended <- answer.get[Boolean]("priorExecutionEnded").left.map(_.message)
    accepted <- answer.get[Boolean]("acceptOriginalUncertainty").left.map(_.message)
    binding <- bindingC(component, target, choice, paths, rationale, participant, reference, ended, accepted).toOption
      .toRight("Invalid independent repair scope direction")
    _ <- Either.cond(binding == wire, (), "Repair scope binding fields differ")
  } yield binding

  /** Administrative suffixes identify the same original repair attempt. */
  def budgetIdentity(identity: String): String = identity.replaceFirst(":scope:[1-9][0-9]*$", "")
  def originalIdentity(source: R.Source): String = source.store.identity + ":native-repair:" + source.basis.next.revision.value
  def selectedC(source: R.Source): Consequence[Option[Saved]] = {
    val old = originalIdentity(source)
    val current = source.session.started.current
    current.history.find(_.causation.value == resumeCause + old) match {
      case None => Consequence.success(None)
      case Some(entry) => for {
        adapter <- source.session.storage.bindWorkOrderSupersessionC(R.completionOperation, M.inputCodec, checkpointCodec)
        retained <- adapter.loadRetainedC(ContinuationIdentity(old), current.identity)(using source.store.core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(N.invalid("repair scope history has no native supersession receipt")))
        saved = Saved(retained)
        target = saved.direction.target
        _ <- checkpointCodec.decode(checkpointCodec.encode(retained.receipt.payload)).fold(N.invalid, _ => Consequence.unit)
        _ <- if (saved.revision == entry.revision && target.sessionId == source.store.identity &&
            target.context == source.session.context && target.sourceRevision == source.basis.next.revision.value.toString &&
            target.failureRevision == source.basis.validated.record.revision.value.toString &&
            target.failedCheck == source.basis.input.checks.last && target.candidateTree == source.basis.input.indexTree &&
            target.localTip == source.basis.input.localTip && target.remoteTip == source.basis.input.remoteTip &&
            (if (_automatic(target)) target.changedPaths == source.basis.input.changedPaths
              else target.changedPaths.forall(source.basis.input.changedPaths.contains)) && target.inventory.forall(scope =>
              RepositorySyncRepairInventory.valid(scope, source.basis.input, source.basis.validationFailure))) Consequence.unit
          else N.invalid("repair scope correction crosses the original failed candidate or native history")
      } yield Some(saved)
    }
  }
  private final case class Observation(source: R.Source, target: Target, current: InstanceRecord,
    entityRevision: org.simplemodeling.model.datatype.EntityRevision)
  private def _reference(record: InstanceRecord, identity: String): Consequence[ContextReference] = {
    val prefix = "native-repository:" + identity + ":"
    if (record.causation.value.startsWith(prefix)) Consequence.success(ContextReference(identity + ":native-" +
      record.causation.value.stripPrefix(prefix), "1")) else N.invalid("repair scope source has no native retained reference")
  }
  private def _observe_c(identity: String, port: R.Port, core: ActionCall.Core): Consequence[Observation] = for {
    source <- port.sourceC(identity, core)
    _ <- if (source.session.context.syncMode == "FULL_STATE" && source.basis.validationFailure.nonEmpty &&
        source.basis.findings.isEmpty && source.basis.validated.payload.purposes.isEmpty &&
        source.session.started.current.lifecycle == Lifecycle.Active) Consequence.unit
      else N.invalid("scope correction requires an active FULL_STATE check-origin repair")
    adapter <- source.session.storage.bindWorkOrderSupersessionC(R.completionOperation, M.inputCodec, checkpointCodec)
    old = originalIdentity(source)
    pending <- adapter.inspectC(ContinuationIdentity(old), InstanceIdentity(identity))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(N.invalid("actual original pending repair issue is absent")))
    _ <- if (pending.current == source.session.started.current &&
        pending.continuation.status == ContinuationRuntimePersistence.Status.Available && pending.continuation.claimId.isEmpty &&
        pending.entityRevision.value == 1L) Consequence.unit
      else N.invalid("known claim, completion or changed issue cannot be corrected")
    order <- pending.issued.current match {
      case value: WorkflowContinuation.WorkOrder[M.Request] => Consequence.success(value)
      case _ => N.invalid("scope correction requires the actual repair WorkOrder")
    }
    input <- order.request.input.map(v => Consequence.success(v.value)).getOrElse(N.invalid("original repair input is absent"))
    _ <- if (input.validationFailure == source.basis.validationFailure && input.review.copy(
        localDiff = source.basis.input.localDiff, remoteDiff = source.basis.input.remoteDiff,
        priorReview = source.basis.input.priorReview) == source.basis.input &&
        order.request.completionOperation == R.completionOperation) Consequence.unit
      else N.invalid("original repair issue crosses the retained formal failure")
    _ <- RepositorySyncNativeMergeValidation.preconditionsC(source.store, source.basis.validated)
    candidate <- source.store.currentMergeCandidateC(pending.current)
    candidatereference <- _reference(candidate.pending.record, identity)
    failurereference <- _reference(source.basis.validated.record, identity)
    actual <- G.inspectC(core)
    tree <- G.indexTreeC(core)
    permitted = source.basis.input.changedPaths
    target = Target(core.component.get.core.instanceId.canonicalKey, identity, source.session.context,
      source.basis.validated.payload.root, source.store.declaration.target.endpoint,
      pending.current.revision.value.toString, pending.current.causation.value, old,
      order.request.expectedRevision.value, pending.entityRevision.value.toString,
      source.basis.next.revision.value.toString, candidatereference, candidate.pending.record.revision.value.toString,
      failurereference, source.basis.validated.record.revision.value.toString, source.basis.input.checks.last,
      source.basis.input.localTip, source.basis.input.remoteTip, source.basis.input.indexTree,
      actual, tree, permitted, input.files.map(_.path), None, uncappedPolicy)
    _ <- if (validTarget(target)) Consequence.unit else N.invalid("repair issue does not bind the current integration candidate")
    _ <- RepositorySyncNativeMergeValidation.preconditionsC(source.store, source.basis.validated)
  } yield Observation(source, target, pending.current, pending.entityRevision)

  def operations(port: R.Port, installation: Installation): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port, installation), new Operation(false, port, installation)) ++
      (if (port.automaticInventory) Vector(new PreparationOperation(port)) else Vector.empty)
  def sessionC(suppliedRequest: Request, call: ActionCall.Core): Consequence[String] = {
    final class Reader extends ProcedureActionCall {
      val core = call
      def readSessionC: Consequence[String] = NativeWorkflowResourceInput.selectC[Record](suppliedRequest).flatMap(source =>
        NativeWorkflowResourceInput.readC(source, reference => read_resource_text(reference,
          Some(java.nio.charset.StandardCharsets.UTF_8))).flatMap(record =>
            if (suppliedRequest.operation == prepareOperation) _prepare_input_c(record).map(_.sessionId)
            else _input_c(record).map(_.sessionId)))
      def execute(): Consequence[OperationResponse] = N.invalid("repair scope reader is not an Operation")
    }
    new Reader().readSessionC
  }
  final case class Preparation(sessionId: String, paths: Vector[String], rationale: String)
  private def _prepare_input_c(record: Record): Consequence[Preparation] = for {
    json <- record.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).fold(N.invalid, Consequence.success))
    value <- json.as[Preparation].left.map(_.message).fold(N.invalid, Consequence.success)
    _ <- if (value.asJson == json && _exact(value.sessionId) && _paths(value.paths) && value.rationale != null &&
        value.rationale.trim.nonEmpty && !value.rationale.contains('\u0000') &&
        java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value.rationale))
      Consequence.unit else N.invalid("automatic inventory preparation requires exact sessionId, sorted concrete paths and causal rationale")
  } yield value
  def requestedInventoryPathsC(suppliedRequest: Request, call: ActionCall.Core): Consequence[Vector[String]] = {
    final class Reader extends ProcedureActionCall {
      val core = call
      def readC: Consequence[Vector[String]] = NativeWorkflowResourceInput.selectC[Record](suppliedRequest).flatMap(source =>
        NativeWorkflowResourceInput.readC(source, reference => read_resource_text(reference,
          Some(java.nio.charset.StandardCharsets.UTF_8))).flatMap { record =>
          if (suppliedRequest.operation == prepareOperation) _prepare_input_c(record).map(_.paths)
          else _input_c(record).flatMap(input => targetC(input.target)).map(_.inventory.toVector.flatMap(_.paths))
        })
      def execute(): Consequence[OperationResponse] = N.invalid("inventory reader is not an Operation")
    }
    new Reader().readC
  }
  private def _prepare_c(identity: String, paths: Vector[String], rationale: String, port: R.Port,
    core: ActionCall.Core): Consequence[Observation] = for {
    _ <- if (port.automaticInventory) Consequence.unit else N.invalid("automatic inventory requires the installed runtime composition")
    observed <- _observe_c(identity, port, core)
    scope <- RepositorySyncRepairInventory.qualifyC(observed.source, paths, rationale,
      observed.target.failureReference)
    target = observed.target.copy(inventory = Some(scope))
    _ <- if (validTarget(target)) Consequence.unit else N.invalid("automatic inventory target is incomplete")
  } yield observed.copy(target = target)
  private def _matching_observation_c(target: Target, port: R.Port, core: ActionCall.Core): Consequence[Observation] =
    if (target.correctionPolicy == uncappedPolicy && target.inventory.nonEmpty)
      _prepare_c(target.sessionId, target.inventory.get.paths, target.inventory.get.rationale, port, core)
    else if (target.correctionPolicy == automaticPolicy) for {
      observed <- _prepare_c(target.sessionId, target.inventory.get.paths, target.inventory.get.rationale, port, core)
      historical = observed.target.copy(correctionPolicy = automaticPolicy, maximumFiles = target.maximumFiles)
      _ <- if (validTarget(historical)) Consequence.unit else N.invalid("historical automatic target exceeds its original contract")
    } yield observed.copy(target = historical)
    else if (target.correctionPolicy == policy) for {
      observed <- _observe_c(target.sessionId, port, core)
      permitted <- port.permittedPathsC(observed.source)
      historical = observed.target.copy(changedPaths = permitted, correctionPolicy = policy, maximumFiles = target.maximumFiles)
      _ <- if (validTarget(historical)) Consequence.unit else N.invalid("historical selected target exceeds its original contract")
    } yield observed.copy(target = historical)
    else N.invalid("repair scope inventory must be prepared before adoption")
  private final class PreparationOperation(port: R.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      SmWorkflowComponent.RepositorySyncServiceService.PrepareRepositorySyncRepairScopeCorrectionOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("RepositorySyncService") || request.operation != prepareOperation)
        N.invalid("automatic inventory Operation identity differs")
      else NativeWorkflowResourceInput.selectC[Record](request).map(source => Action(request, source))
    private final case class Action(request: Request, input: NativeWorkflowResourceInput.Source[Record]) extends CommandAction {
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          value <- NativeWorkflowResourceInput.readC(input, reference => read_resource_text(reference,
            Some(java.nio.charset.StandardCharsets.UTF_8))).flatMap(_prepare_input_c)
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(value.sessionId))
          observed <- _prepare_c(value.sessionId, value.paths, value.rationale, port, core.copy(executionContext = context))
        } yield NativeWorkflowOperationOutput.response(V.RepositorySyncRepairScopeCorrectionDescriptionResult(value.sessionId,
          "adoption-required", automaticMessage, observed.target.componentInstance, targetRecord(observed.target),
          lifecycleChoices, adoptionOperation))
      }
    }
  }
  private final class Operation(describe: Boolean, port: R.Port, installation: Installation) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.RepositorySyncServiceService.DescribeRepositorySyncRepairScopeCorrectionOperation
      else SmWorkflowComponent.RepositorySyncServiceService.AdoptRepositorySyncRepairScopeCorrectionOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("RepositorySyncService") ||
          request.operation != (if (describe) describeOperation else adoptionOperation)) N.invalid("repair scope Operation identity differs")
      else if (describe) NativeWorkflowOperationInput.textC(request, "sessionId").map(id => Action(request, Some(id), None))
      else NativeWorkflowResourceInput.selectC[Record](request).map(input => Action(request, None, Some(input)))
    private final case class Action(request: Request, identity: Option[String],
      input: Option[NativeWorkflowResourceInput.Source[Record]]) extends CommandAction {
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          value <- input match {
            case None => Consequence.success(Option.empty[V.RepositorySyncRepairScopeCorrectionAdoptionInput])
            case Some(source) => NativeWorkflowResourceInput.readC(source, reference => read_resource_text(reference,
              Some(java.nio.charset.StandardCharsets.UTF_8))).flatMap(_input_c).map(Some(_))
          }
          session = value.map(_.sessionId).orElse(identity).get
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
          selected = core.copy(executionContext = context)
          source <- port.sourceC(session, selected)
          saved <- selectedC(source)
          result <- (value, saved) match {
            case (None, Some(saved)) => Consequence.success(NativeWorkflowOperationOutput.response(
              V.RepositorySyncRepairScopeCorrectionDescriptionResult(session, "already-adopted", targetMessage(saved.direction.target),
                saved.direction.target.componentInstance, targetRecord(saved.direction.target), targetChoices(saved.direction.target), adoptionOperation)))
            case (None, None) => _observe_c(session, port, selected).map(observed => NativeWorkflowOperationOutput.response(
              V.RepositorySyncRepairScopeCorrectionDescriptionResult(session, "preparation-required", targetMessage(observed.target),
                observed.target.componentInstance, targetRecord(observed.target), targetChoices(observed.target), prepareOperation)))
            case (Some(input), previous) => _adopt_c(input, previous, port, installation, selected)
          }
        } yield result
      }
    }
  }
  private def _input_c(record: Record): Consequence[V.RepositorySyncRepairScopeCorrectionAdoptionInput] = for {
    json <- record.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).fold(N.invalid, Consequence.success))
    _ <- if (json.asObject.exists(_.keys.toSet == Set("sessionId", "target", "choice", "paths", "rationale", "reference",
        "priorExecutionEnded", "acceptOriginalUncertainty", "executionEvidence")) &&
        json.hcursor.downField("reference").focus.flatMap(_.asObject).exists(_.keys.toSet == Set("identity", "revision"))) Consequence.unit
      else N.invalid("repair scope adoption requires the complete exact input and reference fields")
    _ <- decodeAnswer(Json.obj(Vector("choice", "paths", "rationale", "priorExecutionEnded", "acceptOriginalUncertainty")
      .map(key => key -> json.hcursor.downField(key).focus.getOrElse(Json.Null))*))
      .fold(N.invalid, _ => Consequence.unit)
    value <- V.RepositorySyncRepairScopeCorrectionAdoptionInput.createC(record)
    _ <- if (value != null && _exact(value.sessionId) && value.reference != null && value.executionEvidence != null)
      Consequence.unit else N.invalid("repair scope adoption requires the typed input and existing execution evidence")
    _ <- targetC(value.target)
  } yield value
  private def _adopt_c(input: V.RepositorySyncRepairScopeCorrectionAdoptionInput, saved: Option[Saved],
    port: R.Port, installation: Installation, core: ActionCall.Core): Consequence[OperationResponse] = for {
    target <- targetC(input.target)
    reference = ContextReference(input.reference.identity, input.reference.revision)
    participant = target.context.participantIdentity
    _ <- if (target.sessionId == input.sessionId && input.executionEvidence.workerIdentity.contains(participant)) Consequence.unit
      else N.invalid("repair scope execution evidence has a foreign or absent participant")
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, target, input.choice, input.paths,
      input.rationale, participant, reference, input.priorExecutionEnded, input.acceptOriginalUncertainty)
    _ <- installation.evidence.admitC(core, reference, binding)
    selectedpaths = target.inventory.fold(input.paths)(_.paths)
    direction = Direction(target, selectedpaths, input.choice, input.rationale, participant, reference,
      input.priorExecutionEnded, input.acceptOriginalUncertainty)
    outcome <- saved match {
      case Some(previous) => if (previous.direction == direction) Consequence.success("already-adopted")
        else N.invalid("repeated repair scope correction contradicts the actual saved direction")
      case None => for {
        observed <- _matching_observation_c(target, port, core)
        _ <- if (observed.target == target) Consequence.unit else N.invalid("repair scope target is stale or crossed; describe again")
        result <- if (input.choice == "Defer") Consequence.success("deferred") else for {
          // Capture only for eligibility now; ordinary Advance owns the issued text.
          _ <- port.captureC(observed.source, Some(selectedpaths), target.inventory)
          actual <- _matching_observation_c(target, port, core)
          _ <- if (actual.target == target) Consequence.unit else N.invalid("repair scope drifted before native supersession")
          adapter <- observed.source.session.storage.bindWorkOrderSupersessionC(R.completionOperation, M.inputCodec, checkpointCodec)
          current = actual.current
          entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1), current.revision.next, Lifecycle.Active,
            current.currentProgression, current.correlation, CausationReference(resumeCause + target.oldContinuationId),
            current.derivedCompositeOccurrence, current.committedPredecessor, None)
          _ <- adapter.executeC(ContinuationIdentity(target.oldContinuationId), current.revision, actual.entityRevision,
            current, entry, TypedValue(checkpointCodec.typeIdentity, Checkpoint(direction, "UNKNOWN")))(using core.executionContext)
        } yield "adopted"
      } yield result
    }
  } yield NativeWorkflowOperationOutput.response(V.RepositorySyncRepairScopeCorrectionAdoptionResult(input.sessionId,
    outcome != "deferred", outcome, if (outcome == "deferred") "No mutation; original pending issue remains."
      else "Direction retained through native supersession; original UNKNOWN effects, failed checks and receipts remain. Run separate ordinary Advance."))
}
