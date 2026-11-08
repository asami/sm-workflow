package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.config.{ComponentConfigurationKey}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.resource.{ResourceTreeQueryLimits, ResourceTreeSelection}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceFilePath, WorkspaceId}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedFetch as F
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P
import RepositorySyncPurpose.given
import RepositorySyncPurposeContentContinuation.{exact, ordinary, revision, prefixC}

/** Explicit Human direction for one resultless materialization. The original
  * UNKNOWN, prepared candidate and complete Purpose progress remain immutable.
  * @since   Nov.  4, 2026
  * @version Nov.  8, 2026
  */
private[smworkflow] object RepositorySyncPurposeMaterializationContinuation {
  val policy = "sm-workflow.repository-sync-purpose-materialization-continuation.v1"
  val choices = Vector("AuthorizeNewMaterialization", "Defer")
  val message = "The actual materialization intent has no provider result and remains UNKNOWN. An independent Human must attest priorExecutionEnded=true and acceptOriginalUncertainty=true for this exact target. Adoption records direction only; separate ordinary Advance materializes the same prepared candidate in the current empty fixed workspace, then follows normal checks, review and commit. Defer writes nothing."

  final case class Target(componentInstance: String, sessionId: String, context: F.StartContext,
    root: String, endpoint: String, frontierRevision: String, frontierCause: String,
    sourceNamespace: String, intentKey: String, intentRevision: String, preparedKey: String,
    preparedRevision: String, progressSnapshot: String, preparedPayload: Json, intentPayload: Json,
    actualSnapshot: String, indexTree: String, processPlan: Json, workspaceId: String,
    workspaceRoot: String, continuationPolicy: String)
  object Target {
    given Encoder[Target] = deriveEncoder
    private val _raw: Decoder[Target] = Decoder.instance(c => for {
      componentinstance <- c.get[String]("componentInstance")
      sessionid <- c.get[String]("sessionId")
      context <- c.get[F.StartContext]("context")
      root <- c.get[String]("root")
      endpoint <- c.get[String]("endpoint")
      frontierrevision <- c.get[String]("frontierRevision")
      frontiercause <- c.get[String]("frontierCause")
      sourcenamespace <- c.get[String]("sourceNamespace")
      intentkey <- c.get[String]("intentKey")
      intentrevision <- c.get[String]("intentRevision")
      preparedkey <- c.get[String]("preparedKey")
      preparedrevision <- c.get[String]("preparedRevision")
      progresssnapshot <- c.get[String]("progressSnapshot")
      preparedpayload <- c.get[Json]("preparedPayload")
      intentpayload <- c.get[Json]("intentPayload")
      actualsnapshot <- c.get[String]("actualSnapshot")
      indextree <- c.get[String]("indexTree")
      processplan <- c.get[Json]("processPlan")
      workspaceid <- c.get[String]("workspaceId")
      workspaceroot <- c.get[String]("workspaceRoot")
      continuationpolicy <- c.get[String]("continuationPolicy")
    } yield Target(componentinstance, sessionid, context, root, endpoint, frontierrevision, frontiercause,
      sourcenamespace, intentkey, intentrevision, preparedkey, preparedrevision, progresssnapshot,
      preparedpayload, intentpayload, actualsnapshot, indextree, processplan, workspaceid, workspaceroot, continuationpolicy))
    given Decoder[Target] = Decoder.instance(c => for {
      restored <- _restore(c.value).left.map(DecodingFailure(_, c.history))
      value <- _raw.decodeJson(restored)
      result <- if (validTarget(value) && value.asJson == restored) Right(value)
        else Left(DecodingFailure("Purpose materialization requires the exact complete described target", c.history))
    } yield result)
  }
  final case class Direction(target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, priorExecutionEnded: Boolean, acceptOriginalUncertainty: Boolean)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    private val _raw: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = Decoder.instance(c => _raw(c).flatMap(value =>
      if (value.asJson == c.value && value.choice == choices.head && bindingC(value.target.componentInstance,
          value.target, value.choice, value.rationale, value.participant, value.reference,
          value.priorExecutionEnded, value.acceptOriginalUncertainty).isSuccess) Right(value)
      else Left(DecodingFailure("Purpose materialization direction requires exact Human assertions and scope", c.history))))
  }
  final case class Host(processPlan: Json, workspaceId: String, workspaceRoot: String)
  final case class Source(session: F.Session, store: N.Store, prepared: N.Stage, intent: N.Stage, target: Target)

  private val _opaque_fields = Set("preparedPayload", "intentPayload", "processPlan")
  private def _public(value: Json): Json = value.mapObject(fields => io.circe.JsonObject.fromIterable(
    fields.toVector.map { (key, nested) => key -> (if (_opaque_fields(key)) Json.fromString(nested.noSpaces) else nested) }))
  private def _restore(value: Json): Either[String, Json] = value.asObject.toRight("Materialization target must be an object")
    .flatMap(fields => fields.toVector.foldLeft[Either[String, Vector[(String, Json)]]](Right(Vector.empty)) {
      case (prior, (key, nested)) => for {
        values <- prior
        restored <- if (_opaque_fields(key)) {
          if (nested.isObject) Right(nested) else nested.asString.toRight("Opaque materialization fact is absent")
            .flatMap(text => io.circe.parser.parse(text).left.map(_.message))
            .flatMap(json => Either.cond(json.isObject, json, "Opaque materialization fact requires a complete JSON object"))
        } else Right(nested)
      } yield values :+ (key -> restored)
    }.map(Json.fromFields))
  private def _parsed[A: Decoder](text: String): Either[String, A] = Option(text).toRight("Materialization observation is absent")
    .flatMap(value => io.circe.parser.parse(value).left.map(_.message).flatMap(_.as[A].left.map(_.message)))
  private def _oid(value: String): Boolean = value != null && value.matches("[0-9a-f]{40}|[0-9a-f]{64}")
  def progress(target: Target): Either[String, P.Progress] = _parsed[P.Progress](target.progressSnapshot)
  def actual(target: Target): Either[String, G.Native] = _parsed[G.Native](target.actualSnapshot)
  def namespace(target: Target): String = "materialization-continuation-" + target.frontierRevision
  def adoptionKey(target: Target): String = namespace(target) + "-adopted"
  def materializationPrefix(p: P.Progress, direction: Option[Direction]): String =
    RepositorySyncPurposeRuntime.attemptPrefix(p) + direction.map(d => "-" + namespace(d.target)).getOrElse("")

  private[admission] def validProgress(p: P.Progress): Boolean = p != null && p.request != null &&
    p.plan.exists(plan => plan != null && plan.decision == "resolved" && plan.groups != null &&
      p.cursor >= 0 && p.cursor < plan.groups.size) && p.attempt >= 0 && p.slotCursor >= 0 &&
    p.commits != null && p.commits.size == p.cursor && p.commits.forall(_oid) &&
    p.receipts != null && p.receipts.isEmpty && p.selection.isEmpty && p.assessment.isEmpty && p.review.isEmpty &&
    p.reusable != null && p.reusable.isEmpty && p.effects != null && p.effects.nonEmpty &&
    RepositorySyncPurposeOwnership.validProgress(p) && p.candidate.exists(candidate => candidate != null &&
      Vector(candidate.parent, candidate.index, candidate.commit, candidate.tree, candidate.projectedIndex).forall(_oid) &&
      candidate.parent == (if (p.cursor == 0) p.request.parent else p.commits.last) &&
      candidate.changes == RepositorySyncPurposeOwnership.effectiveGroup(p, p.plan.get.groups(p.cursor)).content &&
      candidate.expected != null && candidate.expected.map(_.path) == candidate.changes.map(_.path) &&
      candidate.expected.forall(content => RepositorySyncPurposeOwnership.expected(p, content.path).contains(content)) &&
      P.producerC(candidate).isSuccess)
  def validTarget(t: Target): Boolean = t != null && exact(t.componentInstance) && exact(t.sessionId) &&
    t.context != null && t.context.syncMode == "FULL_STATE" &&
    t.context.productIterator.forall(v => v.isInstanceOf[String] && exact(v.asInstanceOf[String])) &&
    exact(t.root) && exact(t.endpoint) && t.endpoint == t.context.remoteEndpointIdentity &&
    Vector(t.frontierRevision, t.intentRevision, t.preparedRevision).forall(revision) &&
    t.frontierRevision == t.intentRevision && t.preparedRevision.toLong + 1 == t.intentRevision.toLong &&
    t.sourceNamespace != null && (t.sourceNamespace.isEmpty || exact(t.sourceNamespace)) && exact(t.intentKey) && exact(t.preparedKey) &&
    t.frontierCause == "native-repository:" + t.sessionId + ":" + t.intentKey && _oid(t.indexTree) &&
    t.continuationPolicy == policy && (for {
      p <- progress(t)
      observed <- actual(t)
      prepared <- N.codec.decode(t.preparedPayload)
      intent <- N.codec.decode(t.intentPayload)
      host <- selectWorkspaceC(t.processPlan, t.root, t.workspaceId).toOption.toRight("Invalid fixed workspace")
    } yield validProgress(p) && p.asJson.noSpaces == t.progressSnapshot && p.request.root == t.root &&
      p.request.repository == t.context.repositoryIdentity && p.request.branch == t.context.localBranch &&
      observed.asJson.noSpaces == t.actualSnapshot && observed.mergeHead.isEmpty && !observed.observed.dirty.exists(_.unmerged) &&
      observed.observed.branch == t.context.localBranch && observed.observed.head == p.candidate.get.parent &&
      t.indexTree == p.candidate.get.index && host.workspaceRoot == t.workspaceRoot &&
      N.codec.encode(prepared) == t.preparedPayload && N.codec.encode(intent) == t.intentPayload &&
      prepared.root == t.root && prepared.stage == "PurposePrepared" && prepared.purposes.contains(p) &&
      intent == prepared.copy(stage = "PurposeMaterializing") &&
      t.intentKey == N.candidateKey(t.sourceNamespace, materializationPrefix(p, prepared.purposeMaterialization) + "-materialize-intent") &&
      t.preparedKey == prepared.purposeMaterialization.map(d => adoptionKey(d.target))
        .getOrElse(N.candidateKey(t.sourceNamespace, RepositorySyncPurposeRuntime.attemptPrefix(p) + "-prepared"))).getOrElse(false)
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Purpose materialization target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def targetC(value: Record): Consequence[Target] = if (value == null) N.invalid("purpose materialization target is absent")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(decodeTarget).fold(N.invalid, Consequence.success))
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(_public(value.asJson))
  def decodeAnswer(value: Json): Either[String, RepositorySyncValidationContinuation.Answer] = for {
    wire <- Option(value).toRight("Materialization answer is absent")
    answer <- wire.as[RepositorySyncValidationContinuation.Answer].left.map(_.message)
    _ <- Either.cond(answer.asJson == wire && choices.contains(answer.choice) && ordinary(answer.rationale) && answer.rationale.trim.nonEmpty &&
      (answer.choice == "Defer" || (answer.priorExecutionEnded && answer.acceptOriginalUncertainty)), (),
      "Materialization answer requires exact choice, rationale and both lifecycle assertions")
  } yield answer
  def bindingC(component: String, target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, ended: Boolean, accepted: Boolean): Consequence[Json] =
    if (!validTarget(target) || component != target.componentInstance || participant != target.context.participantIdentity ||
        !choices.contains(choice) || !ordinary(rationale) || rationale.trim.isEmpty || reference == null ||
        !exact(reference.identity) || !exact(reference.revision) || (choice != "Defer" && (!ended || !accepted)))
      N.invalid("materialization direction requires its exact original authority and explicit Human assertions")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson,
      "repositoryValidationContinuation" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson,
          "priorExecutionEnded" -> ended.asJson, "acceptOriginalUncertainty" -> accepted.asJson))))
  private[admission] def executionEvidenceValid(input: V.RepositorySyncValidationContinuationAdoptionInput, target: Target): Boolean =
    input != null && target != null && input.sessionId == target.sessionId && input.reference != null &&
      exact(input.reference.identity) && exact(input.reference.revision) && input.executionEvidence != null &&
      input.executionEvidence.workerIdentity != null && input.executionEvidence.workerIdentity.contains(target.context.participantIdentity) &&
      input.executionEvidence.references != null && input.executionEvidence.references.forall(r => r != null && exact(r.identity) && exact(r.revision)) &&
      input.executionEvidence.references.exists(r => r.identity == input.reference.identity && r.revision == input.reference.revision)

  /** A logical empty-tree query refuses any child before reading file bytes. */
  private[admission] val emptyLimits = ResourceTreeQueryLimits(maxDepth = 0, maxVisitedDirectories = 1,
    maxEntries = 0, maxEntryBytes = 0, maxTotalBytes = 0)
  private val _host_plan_key: ComponentConfigurationKey[Json] = ComponentConfigurationKey.required(
    GoalPhaseWholePhaseCandidateRecovery.processPlanKey, RepositorySyncMergeCheckContinuation.HostPlanDecoder)
  private final class Read(val core: ActionCall.Core) extends ProcedureActionCall {
    def hostC: Consequence[Json] = component_configuration(_host_plan_key).flatMap(_.value
      .map(Consequence.success).getOrElse(N.invalid("ordinary resolved materialization host plan is absent")))
    def emptyC(workspace: WorkspaceId): Consequence[Unit] =
      executeProgram(file_read_snapshot(FileTarget(workspace), ResourceTreeSelection(Vector(".")), emptyLimits)).flatMap(result =>
        if (result.entries.isEmpty) Consequence.unit else N.invalid("materialization destination is occupied"))
    def execute(): Consequence[OperationResponse] = N.invalid("materialization preflight reader is not an Operation")
  }
  private[admission] def selectWorkspaceC(plan: Json, root: String, selected: String): Consequence[Host] = for {
    _ <- if (plan != null && plan.asObject.exists(_.nonEmpty) && exact(root) && exact(selected)) Consequence.unit
      else N.invalid("materialization requires its complete resolved host plan and fixed slot")
    rows <- plan.hcursor.get[Vector[Json]]("workspaces").fold(e => N.invalid(e.message), Consequence.success)
    repositories <- plan.hcursor.get[Option[Vector[Json]]]("gitRepositories").fold(e => N.invalid(e.message), Consequence.success)
    // Validation workspaces may alias the checkout root. The host's existing
    // Git target selects its source; legacy plans without that field retain
    // the unique-root rule rather than acquiring a guessed target.
    source = rows.filter(row => row.hcursor.get[String]("root").contains(root) && repositories.forall(_.exists(repository =>
      repository.hcursor.get[String]("target").toOption == row.hcursor.get[String]("identity").toOption)))
    targets = rows.filter(_.hcursor.get[String]("identity").contains(selected))
    _ <- if (source.size == 1 && targets.size == 1 && source.head != targets.head) Consequence.unit
      else N.invalid("materialization workspace mapping is absent, duplicated or crosses the source")
    sourceidentity <- source.head.hcursor.get[String]("identity").fold(e => N.invalid(e.message), Consequence.success)
    _ <- WorkspaceId.parseC(sourceidentity)
    sourceroot <- source.head.hcursor.get[String]("root").fold(e => N.invalid(e.message), Consequence.success)
    targetroot <- targets.head.hcursor.get[String]("root").fold(e => N.invalid(e.message), Consequence.success)
    paths <- scala.util.Try((java.nio.file.Path.of(root), java.nio.file.Path.of(sourceroot), java.nio.file.Path.of(targetroot))).toOption
      .map(Consequence.success).getOrElse(N.invalid("materialization workspace root is malformed"))
    _ <- if (exact(sourceroot) && exact(targetroot) && paths._1.isAbsolute && paths._2.isAbsolute && paths._3.isAbsolute &&
        paths._1.normalize == paths._1 && paths._2.normalize == paths._2 && paths._3.normalize == paths._3 &&
        paths._1 == paths._2 && !paths._1.startsWith(paths._3) && !paths._3.startsWith(paths._1)) Consequence.unit
      else N.invalid("materialization requires an ordinary absolute fixed destination disjoint from the actual source root")
  } yield Host(plan, selected, targetroot)
  def preflightC(store: N.Store, p: P.Progress): Consequence[Host] = for {
    slot <- RepositorySyncPurpose.slots(store.declaration.purposePolicy, store.context.repositoryIdentity).lift(p.slotCursor).map(Consequence.success)
      .getOrElse(N.invalid("materialization fixed Purpose slot is absent"))
    root <- G.rootC(store.core)
    _ <- if (root == store.original.repositoryRoot && root == store.declaration.target.root.toString && root == p.request.root)
      Consequence.unit else N.invalid("materialization actual source root or original host mapping changed")
    reader = new Read(store.core)
    plan <- reader.hostC
    source <- WorkspaceId.parseC(RepositorySyncDirectoryList.workspace(store.core, "sm-workflow-repository"))
    rows <- plan.hcursor.get[Vector[Json]]("workspaces").fold(e => N.invalid(e.message), Consequence.success)
    registered = rows.filter(_.hcursor.get[String]("identity").contains(source.value))
    _ <- if (registered.size == 1 && registered.head.hcursor.get[String]("root").contains(root)) Consequence.unit
      else N.invalid("materialization source differs from the admitted checkout workspace")
    host <- selectWorkspaceC(plan, root, slot.workspace)
    workspace <- WorkspaceId.parseC(host.workspaceId)
    _ <- reader.emptyC(workspace)
    candidate <- p.candidate.map(P.producerC).getOrElse(N.invalid("materialization prepared candidate is absent"))
    group <- p.plan.flatMap(_.groups.lift(p.cursor)).map(value => Consequence.success(RepositorySyncPurposeOwnership.effectiveGroup(p, value)))
      .getOrElse(N.invalid("materialization current ordered Purpose is absent"))
    identity <- GitSelectionId.parseC(group.identity)
    paths <- group.content.foldLeft(Consequence.success(Vector.empty[GitPath])) { (prior, content) => for {
      values <- prior; path <- GitPath.parseC(content.path)
    } yield values :+ path }
    selection = GitSelectionParameters(identity, paths, Some(group.message))
    _ <- GitExecutionPreflight.validateC(store.core.executionContext, GitTarget(source, Some(selection)),
      GitOperation.MaterializePurposeCandidate(identity, candidate, workspace))
  } yield host

  private def _load_c(store: N.Store, key: String): Consequence[N.Stage] = store.scoped("").loadC(key)
    .flatMap(_.map(Consequence.success).getOrElse(N.invalid("materialization native receipt is absent: " + key)))
  private def _history_c(current: InstanceRecord, stage: N.Stage): Consequence[Unit] =
    prefixC(current, stage.record.revision.value.toString).flatMap(value =>
      if (value == stage.record) Consequence.unit else N.invalid("materialization receipt crosses immutable native history"))
  private def _preparation_c(store: N.Store, current: InstanceRecord, prepared: N.Stage): Consequence[Unit] =
    prepared.payload.purposeMaterialization match {
      case Some(direction) => _adoption_c(store, current, direction).flatMap(saved =>
        if (saved == prepared) Consequence.unit else N.invalid("materialization prepared source differs from prior native adoption"))
      case None => for {
        p <- prepared.payload.purposes.map(Consequence.success).getOrElse(N.invalid("prepared Purpose progress is absent"))
        prefix = "native-repository:" + store.identity + ":"
        key = prepared.record.causation.value.stripPrefix(prefix)
        intent <- _load_c(store, key.stripSuffix("-prepared") + "-prepare-intent")
        _ <- _history_c(current, intent)
        before <- intent.payload.purposes.map(Consequence.success).getOrElse(N.invalid("preparation intent progress is absent"))
        effect <- p.effects.lastOption.map(Consequence.success).getOrElse(N.invalid("preparation has no actual provider result"))
        process <- effect.hcursor.get[Json]("process").toOption.flatMap(WorkflowSbtCapture.decode(_).toOption)
          .map(Consequence.success).getOrElse(N.invalid("preparation provider result is absent"))
        _ <- if (key.endsWith("-prepared") && intent.payload.stage == "PurposePreparing" &&
            prepared.record.revision == intent.record.revision.next && prepared.record.history.dropRight(1) == intent.record.history &&
            prepared.payload == intent.payload.copy(stage = "PurposePrepared", purposes = Some(p)) &&
            before.candidate.isEmpty && p.copy(candidate = None, effects = before.effects) == before &&
            p.effects == (before.effects :+ effect) && effect.hcursor.get[String]("outcome").contains("Succeeded") &&
            WorkflowSbtCapture.exitCode(process).contains(0)) Consequence.unit
          else N.invalid("prepared candidate lacks its actual adjacent successful preparation receipt")
      } yield ()
    }
  private def _authenticate_c(store: N.Store, current: InstanceRecord, target: Target): Consequence[(N.Stage, N.Stage)] = for {
    _ <- if (validTarget(target) && target.componentInstance == store.core.component.get.core.instanceId.canonicalKey &&
        target.sessionId == store.identity && target.context == store.context && target.root == store.original.repositoryRoot &&
        target.endpoint == store.declaration.target.endpoint && target.endpoint == store.context.remoteEndpointIdentity &&
        store.declaration.target.root.toString == target.root && store.declaration.target.localBranch == target.context.localBranch &&
        store.declaration.target.remote == target.context.trackingRemote && store.declaration.target.remoteBranch == target.context.trackingBranch &&
        store.declaration.input.repositoryIdentity == target.context.repositoryIdentity &&
        store.declaration.input.localBranch == target.context.localBranch && store.declaration.input.trackingRemote == target.context.trackingRemote &&
        store.declaration.input.trackingBranch == target.context.trackingBranch && store.declaration.input.remoteEndpointIdentity == target.context.remoteEndpointIdentity &&
        store.declaration.input.syncMode == target.context.syncMode) Consequence.unit
      else N.invalid("materialization direction crosses its original Component, participant or Start target")
    frontier <- prefixC(current, target.frontierRevision)
    prepared <- _load_c(store, target.preparedKey)
    intent <- _load_c(store, target.intentKey)
    result <- store.scoped("").loadC(target.intentKey.stripSuffix("-intent") + "-result")
    success <- store.scoped("").loadC(target.intentKey.stripSuffix("-materialize-intent") + "-materialized")
    _ <- _history_c(frontier, prepared)
    _ <- if (intent.record == frontier && frontier.lifecycle == Lifecycle.Active && frontier.suspension.isEmpty &&
        frontier.currentProgression.contains(ProgressionReference("sync.PurposeMaterializing")) &&
        frontier.causation.value == target.frontierCause && prepared.record.revision.value.toString == target.preparedRevision &&
        prepared.record.revision.next == intent.record.revision && intent.record.history.dropRight(1) == prepared.record.history &&
        N.codec.encode(prepared.payload) == target.preparedPayload && N.codec.encode(intent.payload) == target.intentPayload &&
        result.isEmpty && success.isEmpty) Consequence.unit
      else N.invalid("materialization intent, prepared receipt or actual result absence is crossed")
    _ <- _preparation_c(store, frontier, prepared)
    p <- progress(target).fold(N.invalid, Consequence.success)
    expected <- RepositorySyncPurposePreparationContinuation.completedIndexC(store.scoped(""), frontier, p)
    _ <- if (expected == target.indexTree) Consequence.unit else N.invalid("materialization completed Purpose prefix index changed")
  } yield (prepared, intent)
  private def _current_c(store: N.Store, target: Target): Consequence[Unit] = for {
    p <- progress(target).fold(N.invalid, Consequence.success)
    observed <- G.inspectC(store.core)
    _ <- store.scopeC(observed.observed)
    index <- G.indexTreeC(store.core)
    workspace <- WorkspaceId.parseC(RepositorySyncDirectoryList.workspace(store.core, "sm-workflow-repository"))
    matches <- RepositorySyncPurposePreparationContinuation.rootMatchesC(p, name => for {
      path <- WorkspaceFilePath.parseC(name)
      value <- WorkflowFileExecution.readTextC(store.core, FileTarget(workspace), path)
    } yield value.text)
    host <- preflightC(store, p)
    after <- G.inspectC(store.core)
    afterindex <- G.indexTreeC(store.core)
    _ <- if (observed.asJson.noSpaces == target.actualSnapshot && index == target.indexTree && matches &&
        after == observed && afterindex == index &&
        RepositorySyncPurposeOwnership.worktreeProjectionC(p, RepositorySyncPurposeOwnership.effectiveGroup(p, p.plan.get.groups(p.cursor))).isSuccess &&
        host.processPlan == target.processPlan && host.workspaceId == target.workspaceId && host.workspaceRoot == target.workspaceRoot)
      Consequence.unit else N.invalid("materialization source content, candidate, current host plan or fixed destination changed")
  } yield ()
  def sourceC(identity: String, d: F.Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    acknowledge: String => Consequence[Unit]): Consequence[Source] = for {
    session <- F.sessionC(identity, d, definition, core)
    current = session.started.current
    _ <- if (current.lifecycle == Lifecycle.Active && current.suspension.isEmpty &&
        current.currentProgression.contains(ProgressionReference("sync.PurposeMaterializing"))) Consequence.unit
      else N.invalid("materialization continuation requires the actual active unsuspended resultless Purpose intent")
    store <- N.bindC(session, d, core, acknowledge)
    prefix = "native-repository:" + identity + ":"
    key <- Option.when(current.causation.value.startsWith(prefix))(current.causation.value.stripPrefix(prefix))
      .map(Consequence.success).getOrElse(N.invalid("materialization frontier has no actual native intent"))
    intent <- _load_c(store, key)
    prior <- current.history.find(_.revision.value == current.revision.value - 1).map(Consequence.success)
      .getOrElse(N.invalid("materialization intent has no adjacent prepared receipt"))
    preparedkey <- Option.when(prior.causation.value.startsWith(prefix))(prior.causation.value.stripPrefix(prefix))
      .map(Consequence.success).getOrElse(N.invalid("materialization predecessor has no native prepared reference"))
    prepared <- _load_c(store, preparedkey)
    p <- intent.payload.purposes.map(Consequence.success).getOrElse(N.invalid("materialization intent progress is absent"))
    observed <- G.inspectC(core)
    index <- G.indexTreeC(core)
    host <- preflightC(store, p)
    target = Target(core.component.get.core.instanceId.canonicalKey, identity, session.context, store.original.repositoryRoot,
      d.target.endpoint, current.revision.value.toString, current.causation.value, store.namespace, key,
      intent.record.revision.value.toString, preparedkey, prepared.record.revision.value.toString, p.asJson.noSpaces,
      N.codec.encode(prepared.payload), N.codec.encode(intent.payload), observed.asJson.noSpaces, index,
      host.processPlan, host.workspaceId, host.workspaceRoot, policy)
    authenticated <- _authenticate_c(store, current, target)
    _ <- _current_c(store, target)
  } yield Source(session, store, authenticated._1, authenticated._2, target)
  private def _adoption_c(store: N.Store, current: InstanceRecord, direction: Direction): Consequence[N.Stage] = for {
    _ <- direction.asJson.as[Direction].fold(e => N.invalid(e.message), _ => Consequence.unit)
    original <- _authenticate_c(store, current, direction.target)
    saved <- _load_c(store, adoptionKey(direction.target))
    _ <- _history_c(current, saved)
    _ <- if (saved.record.revision == original._2.record.revision.next &&
        saved.record.history.dropRight(1) == original._2.record.history &&
        saved.payload == original._2.payload.copy(stage = "PurposePrepared", purposeMaterialization = Some(direction))) Consequence.unit
      else N.invalid("materialization adoption is not its exact adjacent unchanged native direction")
  } yield saved
  /** Authenticate again immediately before the ordinary new materialize intent. */
  def authenticateC(store: N.Store, source: N.Stage): Consequence[Unit] = source.payload.purposeMaterialization match {
    case None => Consequence.unit
    case Some(direction) => for {
      saved <- _adoption_c(store, source.record, direction)
      p <- progress(direction.target).fold(N.invalid, Consequence.success)
      _ <- if (source == saved && source.payload.purposes.contains(p)) Consequence.unit
        else N.invalid("materialization direction cannot cross a later Purpose or changed progress")
      _ <- _current_c(store, direction.target)
    } yield ()
  }
  def describeC(identity: String, d: F.Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    acknowledge: String => Consequence[Unit]): Consequence[V.RepositorySyncValidationContinuationDescriptionResult] =
    sourceC(identity, d, definition, core, acknowledge).flatMap { source => for {
      target <- WorkflowRetainedSnapshot.compactC(targetRecord(source.target), Vector(
        "progressSnapshot" -> WorkflowRetainedSnapshot.reference(source.store, source.prepared, source.target.preparedKey, "purpose-progress"),
        "preparedPayload" -> WorkflowRetainedSnapshot.reference(source.store, source.prepared, source.target.preparedKey, "native-payload"),
        "intentPayload" -> WorkflowRetainedSnapshot.reference(source.store, source.intent, source.target.intentKey, "native-payload")), core)
    } yield V.RepositorySyncValidationContinuationDescriptionResult(identity, "adoption-required", message,
        source.target.componentInstance, target, choices, RepositorySyncValidationContinuation.adoptionOperation) }
  def adoptC(input: V.RepositorySyncValidationContinuationAdoptionInput, d: F.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, installation: RepositorySyncValidationContinuation.Installation,
    acknowledge: String => Consequence[Unit]): Consequence[V.RepositorySyncValidationContinuationAdoptionResult] = for {
    target <- targetC(input.target)
    _ <- if (executionEvidenceValid(input, target)) Consequence.unit else N.invalid("materialization evidence lacks its exact artifact reference or original worker")
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, target, input.choice, input.rationale,
      target.context.participantIdentity, reference, input.priorExecutionEnded, input.acceptOriginalUncertainty)
    _ <- installation.evidence.admitC(core, reference, binding)
    session <- F.sessionC(input.sessionId, d, definition, core)
    store <- N.bindC(session, d, core, acknowledge)
    retained <- store.scoped("").loadC(adoptionKey(target))
    direction = Direction(target, input.choice, input.rationale, target.context.participantIdentity, reference,
      input.priorExecutionEnded, input.acceptOriginalUncertainty)
    outcome <- retained match {
      case Some(saved) if saved.payload.purposeMaterialization.contains(direction) =>
        _adoption_c(store, session.started.current, direction).flatMap(actual =>
          if (actual == saved) Consequence.success("already-adopted") else N.invalid("materialization repeat has no actual native adoption"))
      case Some(_) => N.invalid("materialization direction conflicts with its existing native admission")
      case None => for {
        observed <- sourceC(input.sessionId, d, definition, core, acknowledge)
        _ <- if (observed.target == target) Consequence.unit else N.invalid("materialization target drifted; describe the current intent again")
        result <- if (input.choice == "Defer") Consequence.success("deferred") else
          store.scoped("").saveC(adoptionKey(target), "native-purpose-materialization-continuation-admitted", observed.intent.record,
            observed.intent.payload.copy(stage = "PurposePrepared", purposeMaterialization = Some(direction))).map(_ => "adopted")
      } yield result
    }
  } yield V.RepositorySyncValidationContinuationAdoptionResult(input.sessionId, outcome != "deferred", outcome, message)
}
