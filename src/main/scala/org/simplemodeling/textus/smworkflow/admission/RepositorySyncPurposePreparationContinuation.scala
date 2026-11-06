package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceFilePath, WorkspaceId}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import RepositorySyncManagedFetch as F
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P
import RepositorySyncPurpose.given

/** Read the actual failed Git receipt; a separate Human direction appends a
  * fresh preparation namespace without resubmitting the accepted repair.
  * @since   Nov.  4, 2026
  * @version Nov.  6, 2026
  */
private[smworkflow] object RepositorySyncPurposePreparationContinuation {
  val purpose = "repository-sync-purpose-preparation-continuation"
  val policy = "sm-workflow.repository-sync-purpose-preparation-continuation.v1"
  val describeOperation = "describeRepositorySyncPurposePreparationContinuation"
  val adoptionOperation = "adoptRepositorySyncPurposePreparationContinuation"
  val choices = Vector("AuthorizeNewPreparation", "Defer")
  val message = "The actual failed preparation and accepted repair remain unchanged. A Human direction only records one new preparation namespace; separate ordinary Advance owns execution. No prior stage, Submit or successful validation is replayed."

  final case class Target(componentInstance: String, sessionId: String, context: F.StartContext,
    root: String, endpoint: String, frontierRevision: String, frontierCause: String,
    failureKey: String, failureRevision: String, intentKey: String, intentRevision: String,
    progressSnapshot: String, failedEffect: String, actualSnapshot: String, indexTree: String,
    sourceHead: String, eligibilityReason: String, continuationPolicy: String)
  object Target {
    given Encoder[Target] = deriveEncoder
    private val raw: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(c => raw(c).flatMap(value =>
      if (validTarget(value) && value.asJson == c.value) Right(value)
      else Left(DecodingFailure("Purpose preparation requires the exact described target", c.history))))
  }
  final case class Answer(choice: String, rationale: String)
  final case class Direction(target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    private val raw: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = Decoder.instance(c => raw(c).flatMap(value =>
      if (value.asJson == c.value && value.choice == choices.head && bindingC(value.target.componentInstance,
          value.target, value.choice, value.rationale, value.participant, value.reference).isSuccess) Right(value)
      else Left(DecodingFailure("Purpose preparation direction is invalid", c.history))))
  }
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else N.invalid("purpose preparation requires independently provisioned Human evidence")
  }
  private final case class Observation(store: N.Store, source: N.Stage, target: Target)
  private def exact(value: String): Boolean = RepositorySyncPurposeContentContinuation.exact(value)
  private def ordinary(value: String): Boolean = RepositorySyncPurposeContentContinuation.ordinary(value) && value.trim.nonEmpty
  private def revision(value: String): Boolean = RepositorySyncPurposeContentContinuation.revision(value)
  private def oid(value: String): Boolean = value != null && value.matches("[0-9a-f]{40}|[0-9a-f]{64}")
  private def parsed[A: Decoder](text: String): Either[String, A] =
    Option(text).toRight("Opaque preparation observation is absent").flatMap(value =>
      io.circe.parser.parse(value).left.map(_.message).flatMap(_.as[A].left.map(_.message)))
  def progress(target: Target): Either[String, P.Progress] = parsed[P.Progress](target.progressSnapshot)
  def effect(target: Target): Either[String, Json] = parsed[Json](target.failedEffect)
  def namespace(target: Target): String = "prepare-continuation-" + target.frontierRevision
  def adoptionKey(target: Target): String = namespace(target) + "-adopted"
  def targetChoices(target: Target): Vector[String] = if (target.eligibilityReason.isEmpty) choices else Vector("Defer")
  def validTarget(t: Target): Boolean = t != null && exact(t.componentInstance) && exact(t.sessionId) &&
    t.context != null && t.context.syncMode == "FULL_STATE" && t.context.productIterator.forall(v => v.isInstanceOf[String] && exact(v.asInstanceOf[String])) &&
    exact(t.root) && scala.util.Try(java.nio.file.Path.of(t.root).isAbsolute).getOrElse(false) && exact(t.endpoint) &&
    Vector(t.frontierRevision, t.failureRevision, t.intentRevision).forall(revision) &&
    t.frontierRevision == t.failureRevision && t.intentRevision.toLong + 1 == t.failureRevision.toLong &&
    t.failureKey.endsWith("-result") && t.intentKey == t.failureKey.stripSuffix("-result") + "-intent" &&
    exact(t.failureKey) && exact(t.intentKey) && t.frontierCause == "native-repository:" + t.sessionId + ":" + t.failureKey &&
    oid(t.indexTree) && oid(t.sourceHead) && t.eligibilityReason != null &&
    RepositorySyncPurposeOwnership.ordinary(t.eligibilityReason) && t.continuationPolicy == policy &&
    progress(t).exists(p => p.request.root == t.root && p.request.repository == t.context.repositoryIdentity &&
      p.request.branch == t.context.localBranch && P.repairProgressJson(p).noSpaces == t.progressSnapshot) &&
    effect(t).exists(e => e.noSpaces == t.failedEffect && e.asObject.exists(_.keys.toSet == Set("outcome", "process")) &&
      e.hcursor.get[Json]("process").toOption.exists(WorkflowSbtCapture.decode(_).isRight)) &&
    parsed[G.Native](t.actualSnapshot).exists(a => a.observed.head == t.sourceHead &&
      RepositorySyncPurposeContentContinuation.canonical(a.asJson).noSpaces == t.actualSnapshot)
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Purpose preparation target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def decodePublicTarget(value: Json): Either[String, Target] = GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeTarget)
  def targetC(value: Record): Consequence[Target] = if (value == null) N.invalid("purpose preparation target is absent")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .flatMap(decodePublicTarget).fold(N.invalid, Consequence.success))
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def decodeAnswer(value: Json): Either[String, Answer] = for {
    answer <- value.as[Answer].left.map(_.message)
    _ <- Either.cond(answer.asJson == value && choices.contains(answer.choice) && ordinary(answer.rationale), (),
      "Purpose preparation answer requires exact choice and ordinary nonempty rationale")
  } yield answer
  def decodePublicAnswer(value: Json): Either[String, Answer] = GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeAnswer)
  def bindingC(component: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (!validTarget(target) || component != target.componentInstance || participant != target.context.participantIdentity ||
        !targetChoices(target).contains(choice) || !ordinary(rationale) || reference == null ||
        !exact(reference.identity) || !exact(reference.revision)) N.invalid("purpose preparation direction crosses its actual target or Human participant")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson,
      "repositoryPurposePreparationContinuation" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson))))
  def selectBinding(value: Json): Either[String, Json] = for {
    component <- value.hcursor.get[String]("componentInstance").left.map(_.message)
    rawtarget <- value.hcursor.downField("repositoryPurposePreparationContinuation").get[Json]("target").left.map(_.message)
    target <- decodeTarget(rawtarget)
    answer = value.hcursor.downField("repositoryPurposePreparationContinuation").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    selected <- bindingC(component, target, choice, rationale, participant, reference).toOption.toRight("Invalid preparation direction")
    _ <- Either.cond(selected == value, (), "Preparation binding fields differ")
  } yield selected

  /** Eligibility is separate from receipt readability: an unknown or different
    * effect is still described honestly but has no execution choice. */
  def eligibility(p: P.Progress, failedkey: String, intentstage: String, failure: Json,
    actual: G.Native, index: String, rootMatches: Boolean, expectedIndex: Option[String] = None): String = {
    val process = failure.hcursor.get[Json]("process").toOption.flatMap(WorkflowSbtCapture.decode(_).toOption)
    val known = process.exists(result => WorkflowSbtCapture.exitCode(result).exists(code => code != 0 &&
      failure.hcursor.get[String]("outcome").toOption.contains("Failed(" + code + ")")) &&
      !result.stdout.truncated && !result.stderr.truncated &&
      Vector(result.stdout, result.stderr).forall(c => WorkflowSbtCapture.text(c).textStatus == "complete"))
    if (!failedkey.endsWith("-prepare-result") || intentstage != "PurposePreparing") "The actual failed effect is not preparation"
    else if (!known) "The actual provider result is unknown, incomplete or not an exited nonzero preparation"
    else if (!p.plan.exists(plan => plan != null && plan.groups != null && p.cursor >= 0 && p.cursor < plan.groups.size) ||
        p.commits == null || p.commits.size != p.cursor || !p.commits.forall(oid) || p.attempt <= 0 ||
        p.baselineTree.isEmpty || p.candidate.nonEmpty || !RepositorySyncPurposeOwnership.validProgress(p))
      "Only an intact accepted repair of the current ordered Purpose with its exact completed commit prefix is eligible"
    else if (!RepositorySyncPurposeOwnership.worktreeProjectionC(p, p.plan.get.groups(p.cursor)).isSuccess)
      "Accepted later owners disagree on the integrated worktree text"
    else if (actual.mergeHead.nonEmpty || actual.observed.dirty.exists(_.unmerged) ||
        actual.observed.branch != p.request.branch ||
        actual.observed.head != (if (p.cursor > 0) p.commits.last else p.request.parent) ||
        index != expectedIndex.getOrElse(p.request.indexTree) || !rootMatches)
      "The actual source HEAD, index, branch or expected worktree has changed"
    else ""
  }
  /** The new permission changes only its exact attempt namespace. */
  def continuedProgressC(target: Target, current: P.Progress): Consequence[P.Progress] =
    if (validTarget(target) && target.eligibilityReason.isEmpty &&
        P.repairProgressJson(current).noSpaces == target.progressSnapshot)
      Consequence.success(current.copy(preparationNamespace = Some(namespace(target))))
    else N.invalid("preparation continuation crosses the exact failed progress or is ineligible")

  def matchingFailure(before: P.Progress, after: P.Progress, failure: Json): Boolean =
    after.effects == (before.effects :+ failure) && after.copy(effects = before.effects) == before

  private[admission] def completedPrefixIndexC(current: P.Progress, installed: P.Progress,
    prior: P.Progress): Consequence[String] = for {
    _ <- if (current.cursor > 0 && current.commits != null && current.commits.size == current.cursor &&
        current.commits.forall(oid) && installed.cursor == current.cursor && installed.commits == current.commits &&
        installed.request == current.request && prior.request == current.request && prior.cursor == current.cursor - 1)
      Consequence.unit else N.invalid("preparation completed Purpose prefix differs from the current cursor, request or commits")
    candidate <- prior.candidate.map(Consequence.success)
      .getOrElse(N.invalid("preparation completed Purpose has no actual preceding commit candidate"))
    _ <- if (candidate.commit == current.commits.last && installed.commits == (prior.commits :+ candidate.commit) &&
        oid(candidate.projectedIndex)) Consequence.unit
      else N.invalid("preparation completed Purpose candidate differs from the actual commit prefix or projected index")
  } yield candidate.projectedIndex

  /** Later-Purpose source facts come only from this Session's last completed
    * installation and its immediately preceding retained commit intent. */
  private[admission] def completedIndexC(store: N.Store, current: InstanceRecord, p: P.Progress): Consequence[String] =
    if (p.cursor == 0) Consequence.success(p.request.indexTree)
    else for {
      entry <- current.history.reverse.find(entry => entry.suspension.isEmpty &&
        entry.currentProgression.contains(ProgressionReference("sync.PurposeInstalled")))
        .map(Consequence.success).getOrElse(N.invalid("preparation has no actual earlier completed Purpose installation"))
      prefix = "native-repository:" + store.identity + ":"
      _ <- if (current.identity.value == store.identity && entry.causation.value.startsWith(prefix) &&
          entry.causation.value.endsWith("-installed")) Consequence.unit
        else N.invalid("preparation completed Purpose installation has no this-Session native reference")
      key = entry.causation.value.stripPrefix(prefix)
      installed <- store.loadC(key).flatMap(_.map(Consequence.success)
        .getOrElse(N.invalid("preparation completed Purpose installation receipt is absent")))
      installedprefix <- RepositorySyncPurposeContentContinuation.prefixC(current, entry.revision.value.toString)
      previous <- current.history.find(_.revision.value == entry.revision.value - 1).map(Consequence.success)
        .getOrElse(N.invalid("preparation completed Purpose installation has no immediately preceding intent"))
      intentkey = key.stripSuffix("-installed") + "-commit-intent"
      _ <- if (previous.currentProgression.contains(ProgressionReference("sync.PurposeInstalling")) &&
          previous.causation.value == prefix + intentkey) Consequence.unit
        else N.invalid("preparation completed Purpose predecessor is not its actual commit intent")
      intent <- store.loadC(intentkey).flatMap(_.map(Consequence.success)
        .getOrElse(N.invalid("preparation completed Purpose commit intent receipt is absent")))
      intentprefix <- RepositorySyncPurposeContentContinuation.prefixC(current, previous.revision.value.toString)
      _ <- if (installed.record == installedprefix && intent.record == intentprefix &&
          installed.payload.stage == "PurposeInstalled" && intent.payload.stage == "PurposeInstalling" &&
          installed.record.revision == intent.record.revision.next &&
          installed.record.history.dropRight(1) == intent.record.history) Consequence.unit
        else N.invalid("preparation completed Purpose pair crosses its adjacent immutable native history")
      completed <- installed.payload.purposes.map(Consequence.success)
        .getOrElse(N.invalid("preparation completed Purpose installation progress is absent"))
      before <- intent.payload.purposes.map(Consequence.success)
        .getOrElse(N.invalid("preparation completed Purpose commit intent progress is absent"))
      index <- completedPrefixIndexC(p, completed, before)
    } yield index

  private[admission] def rootMatchesC(p: P.Progress,
    read: String => Consequence[Option[String]]): Consequence[Boolean] = {
    val paths = (p.request.files.map(_.path) ++ p.projectedWorktree.toVector.flatten.map(_.path)).distinct
    paths.foldLeft(Consequence.success(true)) { (prior, path) => for {
      matched <- prior
      expected <- RepositorySyncPurposeOwnership.expected(p, path).map(Consequence.success)
        .getOrElse(N.invalid("purpose preparation expected source path is absent"))
      actual <- read(path)
    } yield matched && actual == expected.text }
  }
  private def _root_matches_c(store: N.Store, p: P.Progress): Consequence[Boolean] = for {
    workspace <- WorkspaceId.parseC("sm-workflow-repository")
    matches <- rootMatchesC(p, name => for {
      path <- WorkspaceFilePath.parseC(name)
      actual <- WorkflowFileExecution.readTextC(store.core, FileTarget(workspace), path)
    } yield actual.text)
  } yield matches
  private def _observe_c(identity: String, d: F.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, acknowledge: String => Consequence[Unit]): Consequence[Observation] = for {
    session <- F.sessionC(identity, d, definition, core)
    store <- N.bindC(session, d, core, acknowledge)
    current = session.started.current
    _ <- if (current.lifecycle == Lifecycle.Active && current.suspension.isEmpty &&
        current.currentProgression.contains(ProgressionReference("sync.PurposeUnfinished"))) Consequence.unit
      else N.invalid("purpose preparation description requires an actual unsuspended failed Purpose effect")
    prefix = "native-repository:" + identity + ":"
    key <- Option.when(current.causation.value.startsWith(prefix))(current.causation.value.stripPrefix(prefix))
      .map(Consequence.success).getOrElse(N.invalid("failed Purpose source has no native reference"))
    failed <- store.loadC(key).flatMap(_.map(Consequence.success).getOrElse(N.invalid("actual failed Purpose receipt is absent")))
    _ <- if (failed.record == current && failed.payload.stage == "PurposeUnfinished") Consequence.unit
      else N.invalid("failed Purpose receipt does not match the actual frontier")
    prior <- current.history.reverse.find(_.revision.value == current.revision.value - 1)
      .map(Consequence.success).getOrElse(N.invalid("failed Purpose receipt has no prior native intent"))
    intentkey <- Option.when(prior.causation.value.startsWith(prefix))(prior.causation.value.stripPrefix(prefix))
      .map(Consequence.success).getOrElse(N.invalid("failed Purpose predecessor has no native reference"))
    intent <- store.loadC(intentkey).flatMap(_.map(Consequence.success).getOrElse(N.invalid("actual failed Purpose intent is absent")))
    p <- failed.payload.purposes.map(Consequence.success).getOrElse(N.invalid("failed Purpose has no saved progress"))
    before <- intent.payload.purposes.map(Consequence.success).getOrElse(N.invalid("failed Purpose intent has no saved progress"))
    failure <- p.effects.lastOption.map(Consequence.success).getOrElse(N.invalid("failed Purpose has no actual provider observation"))
    intentprefix <- RepositorySyncPurposeContentContinuation.prefixC(current, prior.revision.value.toString)
    _ <- if (intent.record == intentprefix && intent.record.revision == prior.revision &&
        key.endsWith("-result") && intentkey == key.stripSuffix("-result") + "-intent" && matchingFailure(before, p, failure)) Consequence.unit
      else N.invalid("failed Purpose intent/result progress does not match")
    actual <- G.inspectC(core)
    root <- G.rootC(core)
    index <- G.indexTreeC(core)
    expectedindex <- completedIndexC(store, current, p)
    matches <- _root_matches_c(store, p)
    reason = if (root != failed.payload.root || d.target.root.toString != failed.payload.root ||
        d.input.repositoryIdentity != session.context.repositoryIdentity ||
        d.input.localBranch != session.context.localBranch || d.input.trackingRemote != session.context.trackingRemote ||
        d.input.trackingBranch != session.context.trackingBranch || d.input.remoteEndpointIdentity != session.context.remoteEndpointIdentity ||
        d.target.endpoint != session.context.remoteEndpointIdentity)
      "The actual repository root, endpoint or retained target mapping has changed"
      else eligibility(p, key, intent.payload.stage, failure, actual, index, matches, Some(expectedindex))
    target = Target(core.component.get.core.instanceId.canonicalKey, identity, session.context, failed.payload.root,
      d.target.endpoint, current.revision.value.toString, current.causation.value,
      key, failed.record.revision.value.toString, intentkey, intent.record.revision.value.toString,
      P.repairProgressJson(p).noSpaces, failure.noSpaces, RepositorySyncPurposeContentContinuation.canonical(actual.asJson).noSpaces,
      index, actual.observed.head, reason, policy)
    _ <- if (validTarget(target)) Consequence.unit else N.invalid("actual failed preparation cannot form its exact public target")
  } yield Observation(store, failed, target)

  private def _saved_c(store: N.Store, current: InstanceRecord, target: Target): Consequence[Option[N.Stage]] =
    store.loadC(adoptionKey(target)).flatMap {
      case Some(saved) if current.history.exists(entry => entry.revision == saved.record.revision &&
          entry.causation == saved.record.causation) && saved.payload.purposePreparation.exists(_.target == target) =>
        RepositorySyncPurposeContentContinuation.prefixC(current, saved.record.revision.value.toString).flatMap(prefix =>
          if (prefix == saved.record) Consequence.success(Some(saved)) else N.invalid("preparation adoption differs from immutable history"))
      case Some(_) => N.invalid("saved preparation direction is crossed or absent from current history")
      case None => Consequence.success(None)
    }

  /** Authenticate the actual adoption and unchanged source again immediately
    * before the normal runtime writes a fresh preparation intent. */
  def authenticateC(store: N.Store, source: N.Stage): Consequence[Unit] = {
    val p = source.payload.purposes.get
    p.preparationNamespace match {
      case None => if (source.payload.purposePreparation.isEmpty) Consequence.unit
        else N.invalid("a preparation direction cannot cross into another Purpose")
      case Some(name) => for {
        direction <- source.payload.purposePreparation.map(Consequence.success).getOrElse(N.invalid("preparation namespace has no saved Human direction"))
        _ <- if (name == namespace(direction.target) && direction.choice == choices.head) Consequence.unit
          else N.invalid("preparation namespace differs from its exact Human direction")
        saved <- _saved_c(store, source.record, direction.target).flatMap(_.map(Consequence.success)
          .getOrElse(N.invalid("preparation direction has no native adoption")))
        original <- progress(direction.target).fold(N.invalid, Consequence.success)
        failed <- store.loadC(direction.target.failureKey).flatMap(_.map(Consequence.success)
          .getOrElse(N.invalid("original failed preparation receipt is absent")))
        failedprefix <- RepositorySyncPurposeContentContinuation.prefixC(source.record, direction.target.failureRevision)
        intent <- store.loadC(direction.target.intentKey).flatMap(_.map(Consequence.success)
          .getOrElse(N.invalid("original preparation intent is absent")))
        intentprefix <- RepositorySyncPurposeContentContinuation.prefixC(source.record, direction.target.intentRevision)
        _ <- if (failed.record == failedprefix && intent.record == intentprefix && intent.payload.stage == "PurposePreparing" &&
            intent.payload.purposes.exists(before => matchingFailure(before, original, original.effects.last)) &&
            original.effects.last.noSpaces == direction.target.failedEffect &&
            failed.record.causation.value == direction.target.frontierCause &&
            failed.record.revision.value.toString == direction.target.failureRevision &&
            failed.payload.purposes.contains(original) && failed.payload.stage == "PurposeUnfinished" &&
            saved.payload.purposePreparation.contains(direction) && saved.payload.purposes.contains(original.copy(preparationNamespace = Some(name))) &&
            p == original.copy(preparationNamespace = Some(name))) Consequence.unit
          else N.invalid("preparation direction or accepted repair progress changed")
        actual <- G.inspectC(store.core)
        index <- G.indexTreeC(store.core)
        expectedindex <- completedIndexC(store, source.record, p)
        matches <- _root_matches_c(store, p)
        _ <- store.scopeC(actual.observed)
        _ <- if (store.declaration.target.endpoint == direction.target.endpoint && store.context == direction.target.context &&
            store.original.repositoryRoot == direction.target.root && store.context.remoteEndpointIdentity == direction.target.endpoint) Consequence.unit
          else N.invalid("preparation continuation host target or participant changed")
        _ <- if (eligibility(p, direction.target.failureKey, "PurposePreparing", original.effects.last, actual, index, matches,
            Some(expectedindex)).isEmpty &&
            RepositorySyncPurposeContentContinuation.canonical(actual.asJson).noSpaces == direction.target.actualSnapshot && index == direction.target.indexTree)
          Consequence.unit else N.invalid("source changed after preparation continuation adoption")
      } yield ()
    }
  }

  def operations(d: F.Declaration, definition: DefinitionBinding, installation: Installation,
    acknowledge: String => Consequence[Unit]): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, d, definition, installation, acknowledge), new Operation(false, d, definition, installation, acknowledge))
  def sessionC(suppliedRequest: Request, call: ActionCall.Core): Consequence[String] = {
    final class Reader extends ProcedureActionCall {
      val core: ActionCall.Core = call
      def execute(): Consequence[OperationResponse] = N.invalid("preparation session reader is not an Operation")
      def readC: Consequence[String] = NativeWorkflowResourceInput.selectC[Record](suppliedRequest).flatMap(source =>
        NativeWorkflowResourceInput.readC(source, ref => WorkflowArtifactBudget.readTextC(core, ref, Some(java.nio.charset.StandardCharsets.UTF_8))))
        .flatMap(_input_c).map(_.sessionId)
    }
    new Reader().readC
  }
  private def _input_c(record: Record): Consequence[V.RepositorySyncPurposePreparationContinuationAdoptionInput] = for {
    json <- record.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).fold(N.invalid, Consequence.success))
    _ <- if (json.asObject.exists(_.keys.toSet == Set("sessionId", "target", "choice", "rationale", "directionReference", "executionEvidence"))) Consequence.unit
      else N.invalid("preparation adoption requires the complete exact input")
    _ <- if (json.hcursor.downField("directionReference").focus.exists(_.asObject.exists(_.keys.toSet == Set("identity", "revision")))) Consequence.unit
      else N.invalid("preparation direction reference requires exact identity and revision")
    _ <- decodeAnswer(Json.obj("choice" -> json.hcursor.downField("choice").focus.getOrElse(Json.Null),
      "rationale" -> json.hcursor.downField("rationale").focus.getOrElse(Json.Null))).fold(N.invalid, _ => Consequence.unit)
    value <- V.RepositorySyncPurposePreparationContinuationAdoptionInput.createC(record)
    _ <- if (exact(value.sessionId) && value.directionReference != null && value.executionEvidence != null) Consequence.unit
      else N.invalid("preparation adoption lacks its Session, direction reference or execution")
    _ <- targetC(value.target)
  } yield value
  private final class Operation(describe: Boolean, d: F.Declaration, definition: DefinitionBinding,
    installation: Installation, acknowledge: String => Consequence[Unit]) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.RepositorySyncServiceService.DescribeRepositorySyncPurposePreparationContinuationOperation
      else SmWorkflowComponent.RepositorySyncServiceService.AdoptRepositorySyncPurposePreparationContinuationOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("RepositorySyncService") ||
          request.operation != (if (describe) describeOperation else adoptionOperation)) N.invalid("preparation Operation identity differs")
      else if (describe) NativeWorkflowOperationInput.textC(request, "sessionId").map(id => Action(request, Some(id), None))
      else NativeWorkflowResourceInput.selectC[Record](request).map(source => Action(request, None, Some(source)))
    private final case class Action(request: Request, identity: Option[String], input: Option[NativeWorkflowResourceInput.Source[Record]]) extends CommandAction {
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          value <- input.fold(Consequence.success(Option.empty[V.RepositorySyncPurposePreparationContinuationAdoptionInput]))(source =>
            NativeWorkflowResourceInput.readC(source, ref => WorkflowArtifactBudget.readTextC(core, ref, Some(java.nio.charset.StandardCharsets.UTF_8)))
              .flatMap(_input_c).map(Some(_)))
          sessionid = value.map(_.sessionId).orElse(identity).get
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(sessionid))
          selected = core.copy(executionContext = context)
          result <- value match {
            case None => _observe_c(sessionid, d, definition, selected, acknowledge).map(observed =>
              NativeWorkflowOperationOutput.response(V.RepositorySyncPurposePreparationContinuationDescriptionResult(sessionid,
                if (observed.target.eligibilityReason.isEmpty) "adoption-required" else "ineligible",
                if (observed.target.eligibilityReason.isEmpty) message else observed.target.eligibilityReason,
                observed.target.componentInstance, targetRecord(observed.target), targetChoices(observed.target), adoptionOperation)))
            case Some(supplied) => _adopt_c(supplied, d, definition, installation, selected, acknowledge)
          }
        } yield result
      }
    }
  }
  private def _adopt_c(input: V.RepositorySyncPurposePreparationContinuationAdoptionInput,
    d: F.Declaration, definition: DefinitionBinding, installation: Installation,
    core: ActionCall.Core, acknowledge: String => Consequence[Unit]): Consequence[OperationResponse] = for {
    target <- targetC(input.target)
    session <- F.sessionC(input.sessionId, d, definition, core)
    store <- N.bindC(session, d, core, acknowledge)
    reference = ContextReference(input.directionReference.identity, input.directionReference.revision)
    participant = session.context.participantIdentity
    _ <- if (target.sessionId == input.sessionId && session.context == target.context &&
        input.executionEvidence.workerIdentity.contains(participant)) Consequence.unit
      else N.invalid("preparation direction crosses its actual Session or execution participant")
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, target, input.choice, input.rationale, participant, reference)
    _ <- installation.evidence.admitC(core, reference, binding)
    direction = Direction(target, input.choice, input.rationale, participant, reference)
    saved <- _saved_c(store, session.started.current, target)
    outcome <- saved match {
      case Some(previous) => if (previous.payload.purposePreparation.contains(direction)) Consequence.success("already-adopted")
        else N.invalid("preparation direction contradicts the actual saved direction")
      case None => for {
        observed <- _observe_c(input.sessionId, d, definition, core, acknowledge)
        _ <- if (observed.target == target) Consequence.unit else N.invalid("preparation target is stale or source changed; describe again")
        result <- if (input.choice == "Defer") Consequence.success("deferred") else for {
          _ <- if (target.eligibilityReason.isEmpty) Consequence.unit else N.invalid("failed preparation is not eligible")
          actual <- _observe_c(input.sessionId, d, definition, core, acknowledge)
          _ <- if (actual.target == target) Consequence.unit else N.invalid("preparation source changed before adoption")
          p <- continuedProgressC(target, observed.source.payload.purposes.get)
          _ <- store.saveC(adoptionKey(target), "purpose-preparation-continuation", actual.source.record,
            actual.source.payload.copy(stage = "PurposeGrouped", purposePreparation = Some(direction),
              purposes = Some(p)))
        } yield "adopted"
      } yield result
    }
  } yield NativeWorkflowOperationOutput.response(V.RepositorySyncPurposePreparationContinuationAdoptionResult(input.sessionId,
    outcome != "deferred", outcome, if (outcome == "deferred") "Original failed preparation and accepted repair remain unchanged."
      else "Human preparation direction retained; run separate ordinary Advance. Original failure, UNKNOWN and repair counts remain unchanged."))
}
