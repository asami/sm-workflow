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
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedFetch as F
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeMergeValidation as C
import RepositorySyncNativeProgressStore as N

/** An independent Human direction for one new remote integration episode.
  * Historical intents and their UNKNOWN outcomes are never amended or replayed.
  * @since Nov. 4, 2026
  * @version Nov. 4, 2026
  */
private[smworkflow] object RepositorySyncPushContinuation {
  val purpose = "repository-sync-push-continuation"
  val policy = "sm-workflow.repository-sync-push-continuation.v1"
  val describeOperation = "describeRepositorySyncPushContinuation"
  val adoptionOperation = "adoptRepositorySyncPushContinuation"
  val choices = Vector("RefreshRemoteAndContinue", "Defer")
  val finitePlan = Vector("one-fresh-fetch", "ordinary-necessary-integration-checks-review",
    "at-most-one-new-nonforce-push", "final-convergence-observation")
  val message = "The remote moved or a native push remains failed or UNKNOWN. An independent Human must " +
    "bind this exact target, attest priorExecutionEnded=true and acceptOriginalUncertainty=true. " +
    "RefreshRemoteAndContinue permits one fresh fetch, ordinary necessary integration/checks/review, " +
    "at most one new non-force push and final convergence observation under the original authority. " +
    "It preserves every original intent, accepted Purpose commit and receipt. New failure or movement " +
    "requires a new exact direction; it never retries automatically. Adoption stores direction only. " +
    "Run separate ordinary advanceRepositorySync. Defer writes nothing."

  /** Actual typed Git data, using the shared process capture codec without SBT facts. */
  final case class GitObservation(operation: GitOperation[Unit], outcome: GitExecutionOutcome,
    process: ProcessExecutionResult, valueObserved: Boolean)
  object GitObservation {
    given Encoder[GitObservation] = Encoder.instance(value => Json.obj(
      "operation" -> _operation_json(value.operation), "outcome" -> _outcome_json(value.outcome),
      "process" -> _process_json(value.process), "valueObserved" -> value.valueObserved.asJson))
    given Decoder[GitObservation] = Decoder.instance(cursor => (for {
      operation <- cursor.get[Json]("operation").left.map(_.message).flatMap(_operation)
      process <- cursor.get[Json]("process").left.map(_.message).flatMap(_process)
      outcome <- cursor.get[Json]("outcome").left.map(_.message).flatMap(value => _outcome(value, process))
      observed <- cursor.get[Boolean]("valueObserved").left.map(_.message)
      result = GitObservation(operation, outcome, process, observed)
      _ <- Either.cond(validObservation(result) && result.asJson == cursor.value, (),
        "Git observation requires exact typed operation/outcome/process facts")
    } yield result).left.map(DecodingFailure(_, cursor.history)))
  }
  private def _process_json(process: ProcessExecutionResult): Json = WorkflowSbtCapture.encode(process)
    .mapObject(_.add("artifacts", Json.fromValues(process.artifacts.map(artifact => Json.obj(
      "name" -> artifact.name.value.asJson, "kind" -> artifact.kind.toString.asJson,
      "byteCount" -> artifact.byteCount.asJson)))))
  private def _process(value: Json): Either[String, ProcessExecutionResult] = for {
    artifacts <- value.hcursor.get[Vector[Json]]("artifacts").left.map(_.message)
    decoded <- artifacts.foldLeft[Either[String, Vector[ProcessExecutionArtifact]]](Right(Vector.empty)) { (prior, wire) => for {
      all <- prior
      name <- wire.hcursor.get[String]("name").left.map(_.message)
      parsed <- ProcessArtifactName.parseC(name).toOption.toRight("Invalid process artifact name")
      kind <- wire.hcursor.get[String]("kind").left.map(_.message)
      count <- wire.hcursor.downField("byteCount").focus.flatMap(_.asNumber).flatMap(_.toLong)
        .filter(_ >= 0).toRight("Invalid process artifact byte count")
      _ <- Either.cond(kind == "File" && parsed.value == name && wire.asObject.exists(_.keys.toSet == Set("name", "kind", "byteCount")),
        (), "Invalid typed process artifact metadata")
    } yield all :+ ProcessExecutionArtifact(parsed, ProcessArtifactKind.File, count) }
    // Reuse the existing exact termination/capture codec; artifacts keep native metadata.
    base <- WorkflowSbtCapture.decode(value.mapObject(_.add("artifacts", Json.arr())))
    result = base.copy(artifacts = decoded)
    _ <- Either.cond(_process_json(result) == value, (), "Noncanonical Git process observation")
  } yield result
  private def _operation_json(operation: GitOperation[Unit]): Json = operation match {
    case GitOperation.Push(tip, branch) => Json.obj("kind" -> "push".asJson,
      "tip" -> tip.value.asJson, "branch" -> branch.value.asJson)
    case GitOperation.Fetch => Json.obj("kind" -> "fetch".asJson)
    case _ => Json.Null
  }
  private def _operation(value: Json): Either[String, GitOperation[Unit]] = for {
    kind <- value.hcursor.get[String]("kind").left.map(_.message)
    operation <- kind match {
      case "fetch" => Right(GitOperation.Fetch): Either[String, GitOperation[Unit]]
      case "push" => for {
        tip <- value.hcursor.get[String]("tip").left.map(_.message)
        branch <- value.hcursor.get[String]("branch").left.map(_.message)
        oid <- GitObjectId.parseC(tip).toOption.toRight("Invalid push object")
        name <- GitBranchName.parseC(branch).toOption.toRight("Invalid push branch")
      } yield GitOperation.Push(oid, name)
      case _ => Left("Unknown continuation Git operation")
    }
    _ <- Either.cond(_operation_json(operation) == value, (), "Noncanonical Git operation")
  } yield operation
  private def _outcome_json(outcome: GitExecutionOutcome): Json = outcome match {
    case GitExecutionOutcome.Succeeded => Json.obj("kind" -> "succeeded".asJson)
    case GitExecutionOutcome.Failed(code) => Json.obj("kind" -> "failed".asJson, "exitCode" -> code.asJson)
    case GitExecutionOutcome.Incomplete(_) => Json.obj("kind" -> "incomplete".asJson)
    case GitExecutionOutcome.InvalidOutput(reason) => Json.obj("kind" -> "invalid-output".asJson, "reason" -> reason.asJson)
    case GitExecutionOutcome.ConflictObservationRequired => Json.obj("kind" -> "conflict-observation-required".asJson)
  }
  private def _outcome(value: Json, process: ProcessExecutionResult): Either[String, GitExecutionOutcome] = for {
    kind <- value.hcursor.get[String]("kind").left.map(_.message)
    outcome <- kind match {
      case "succeeded" => Right(GitExecutionOutcome.Succeeded)
      case "failed" => value.hcursor.get[Int]("exitCode").left.map(_.message).map(GitExecutionOutcome.Failed(_))
      case "incomplete" => Right(GitExecutionOutcome.Incomplete(process.termination))
      case "invalid-output" => value.hcursor.get[String]("reason").left.map(_.message).map(GitExecutionOutcome.InvalidOutput(_))
      case _ => Left("Unknown push/fetch outcome")
    }
    _ <- Either.cond(_outcome_json(outcome) == value, (), "Noncanonical Git outcome")
  } yield outcome
  def validObservation(value: GitObservation): Boolean = value != null && value.operation != null &&
    value.outcome != null && value.process != null && value.process.termination != null &&
    value.process.stdout != null && value.process.stderr != null &&
    Vector(value.process.stdout, value.process.stderr).forall(c => c.content != null && c.byteCount >= 0 && c.byteCount == c.content.size.toLong) &&
    value.process.artifacts != null &&
    value.process.artifacts.forall(a => a != null && a.name != null && a.kind != null && a.byteCount >= 0) && value.process.elapsedMillis >= 0 &&
    _exact(value.process.safeProgramIdentity) && !_operation_json(value.operation).isNull &&
    _process(_process_json(value.process)).isRight && (value.outcome match {
      case GitExecutionOutcome.Succeeded => value.process.termination == ProcessExecutionTermination.Exited(0)
      case GitExecutionOutcome.Failed(code) => code != 0 && !value.valueObserved &&
        value.process.termination == ProcessExecutionTermination.Exited(code)
      case GitExecutionOutcome.Incomplete(termination) => !value.valueObserved && termination == value.process.termination &&
        (termination match { case ProcessExecutionTermination.Exited(_) => false; case _ => true })
      case GitExecutionOutcome.InvalidOutput(reason) => !value.valueObserved && reason != null && reason.nonEmpty
      case _ => false
    })
  def observationC(result: GitExecutionResult[Unit]): Consequence[GitObservation] = {
    val value = Option(result).map(r => GitObservation(r.operation, r.outcome, r.processResult, r.value.nonEmpty))
    value.filter(validObservation).map(Consequence.success)
      .getOrElse(N.invalid("push/fetch returned invalid actual typed execution facts"))
  }
  def successful(value: GitObservation): Boolean = validObservation(value) &&
    value.outcome == GitExecutionOutcome.Succeeded && value.valueObserved
  def failed(value: GitObservation): Boolean = validObservation(value) && (value.outcome match {
    case GitExecutionOutcome.Failed(_) => true
    case _ => false
  })

  final case class Intent(reference: ContextReference, revision: String, kind: String)
  final case class Target(componentInstance: String, sessionId: String, context: F.StartContext,
    root: String, endpoint: String, frontierRevision: String, frontierCause: String, frontierStage: String,
    sourceNamespace: String, retainedIntent: Option[Intent], committedReference: ContextReference,
    committedRevision: String, finalTip: String, tree: String, validationPolicy: C.Policy,
    selectedChecks: Vector[C.Check], actual: G.Native, advertisedRemoteTip: String,
    nativeObservation: Option[GitObservation], continuationPolicy: String, finitePlan: Vector[String])
  object Target {
    private val _encoder: Encoder[Target] = Encoder.instance(value => Json.obj(
      "componentInstance" -> value.componentInstance.asJson,
      "sessionId" -> value.sessionId.asJson,
      "context" -> value.context.asJson,
      "root" -> value.root.asJson,
      "endpoint" -> value.endpoint.asJson,
      "frontierRevision" -> value.frontierRevision.asJson,
      "frontierCause" -> value.frontierCause.asJson,
      "frontierStage" -> value.frontierStage.asJson,
      "sourceNamespace" -> value.sourceNamespace.asJson,
      "retainedIntent" -> value.retainedIntent.asJson,
      "committedReference" -> value.committedReference.asJson,
      "committedRevision" -> value.committedRevision.asJson,
      "finalTip" -> value.finalTip.asJson,
      "tree" -> value.tree.asJson,
      "validationPolicy" -> value.validationPolicy.asJson,
      "selectedChecks" -> value.selectedChecks.asJson,
      "actual" -> value.actual.asJson,
      "advertisedRemoteTip" -> value.advertisedRemoteTip.asJson,
      "nativeObservation" -> value.nativeObservation.asJson,
      "continuationPolicy" -> value.continuationPolicy.asJson,
      "finitePlan" -> value.finitePlan.asJson))
    private val _decoder: Decoder[Target] = Decoder.instance(cursor => for {
      componentinstance <- cursor.get[String]("componentInstance")
      sessionid <- cursor.get[String]("sessionId")
      context <- cursor.get[F.StartContext]("context")
      root <- cursor.get[String]("root")
      endpoint <- cursor.get[String]("endpoint")
      frontierrevision <- cursor.get[String]("frontierRevision")
      frontiercause <- cursor.get[String]("frontierCause")
      frontierstage <- cursor.get[String]("frontierStage")
      sourcenamespace <- cursor.get[String]("sourceNamespace")
      retainedintent <- cursor.get[Option[Intent]]("retainedIntent")
      committedreference <- cursor.get[ContextReference]("committedReference")
      committedrevision <- cursor.get[String]("committedRevision")
      finaltip <- cursor.get[String]("finalTip")
      tree <- cursor.get[String]("tree")
      validationpolicy <- cursor.get[C.Policy]("validationPolicy")
      selectedchecks <- cursor.get[Vector[C.Check]]("selectedChecks")
      actual <- cursor.get[G.Native]("actual")
      advertisedremotetip <- cursor.get[String]("advertisedRemoteTip")
      nativeobservation <- cursor.get[Option[GitObservation]]("nativeObservation")
      continuationpolicy <- cursor.get[String]("continuationPolicy")
      finiteplan <- cursor.get[Vector[String]]("finitePlan")
    } yield Target(componentinstance, sessionid, context, root, endpoint, frontierrevision,
      frontiercause, frontierstage, sourcenamespace, retainedintent, committedreference,
      committedrevision, finaltip, tree, validationpolicy, selectedchecks, actual,
      advertisedremotetip, nativeobservation, continuationpolicy, finiteplan))
    given Encoder[Target] = Encoder.instance(value => _canonical(_encoder(value)))
    given Decoder[Target] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (validTarget(value) && value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Push target requires its exact complete canonical contract", cursor.history))))
  }
  final case class Answer(choice: String, rationale: String, priorExecutionEnded: Boolean,
    acceptOriginalUncertainty: Boolean)
  final case class Direction(target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, priorExecutionEnded: Boolean, acceptOriginalUncertainty: Boolean)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    private val _decoder: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (value.choice == "RefreshRemoteAndContinue" && value.asJson == cursor.value &&
          bindingC(value.target.componentInstance, value.target, value.choice, value.rationale,
            value.participant, value.reference, value.priorExecutionEnded, value.acceptOriginalUncertainty).isSuccess) Right(value)
      else Left(DecodingFailure("Push direction requires its exact actual target and Human answer", cursor.history))))
  }
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else N.invalid("push continuation requires independent Human evidence admission")
  }
  final case class Saved(stage: N.Stage, direction: Direction) {
    def namespace: String = RepositorySyncPushContinuation.namespace(direction.target)
  }
  private final case class Source(store: N.Store, frontier: N.Stage, target: Target)
  private val _frontiers = Set("NativePushPreparing", "NativePushRefreshRequired", "NativePushFailed",
    "NativePushUncertain", "NativePushObserved", "NativeFinalFetching", "PushRefreshFetched",
    "PushRefreshFetchFailed", "PushRefreshFetchUncertain")
  private def _frontier_keys(stage: String): Set[String] = stage match {
    case "NativePushPreparing" => Set("push-intent")
    case "NativePushRefreshRequired" => Set("push-refresh-required", "refresh-required")
    case "NativePushFailed" | "NativePushUncertain" => Set("push-result")
    case "NativePushObserved" => Set("push-observed")
    case "NativeFinalFetching" => Set("final-fetch-intent")
    case "PushRefreshFetched" | "PushRefreshFetchFailed" | "PushRefreshFetchUncertain" =>
      Set("refresh-fetch-result", "refresh-fetch-observed")
    case _ => Set.empty
  }
  private def _canonical(value: Json): Json = value.mapObject(_.filter { case (_, v) => !v.isNull }
    .mapValues(_canonical)).mapArray(_.map(_canonical))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value &&
    !value.exists(_.isControl)
  private def _rationale(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value)
  private def _revision(value: String): Boolean = value != null && value.matches("[1-9][0-9]*") &&
    BigInt(value) < BigInt(Long.MaxValue) - 16
  private def _oid(value: String): Boolean = value != null && value.matches("[0-9a-f]{40}|[0-9a-f]{64}")
  private def _reference(value: ContextReference): Boolean = value != null && _exact(value.identity) && _exact(value.revision)
  private def _namespace(value: String): Boolean = value != null && (value.isEmpty ||
    value.matches("(?:push-continuation-|validation-continuation-|repair-|required-lint-|formal-failure-)[1-9][0-9]*"))
  private def _observed(value: G.Observed): Boolean = value != null && _exact(value.branch) &&
    GitBranchName.parseC(value.branch).isSuccess && _oid(value.head) && _oid(value.tree) &&
    value.dirty != null && value.dirty.isEmpty && value.trackingTip != null && value.trackingTip.exists(_oid) &&
    value.mergeBase != null && value.mergeBase.exists(_oid) && value.counts != null &&
    value.counts.exists(c => c != null && c.localAhead >= 0 && c.remoteAhead >= 0)
  private def _identity(session: String, namespace: String, key: String): String =
    session + ":native-" + N.candidateKey(namespace, key)
  def namespace(target: Target): String = "push-continuation-" + target.frontierRevision
  def adoptionKey(target: Target): String = namespace(target) + "-adopted"
  def validTarget(t: Target): Boolean = t != null && _exact(t.componentInstance) && _exact(t.sessionId) &&
    t.context != null && t.context.productIterator.forall(v => v.isInstanceOf[String] && _exact(v.asInstanceOf[String])) &&
    _exact(t.root) && scala.util.Try(java.nio.file.Path.of(t.root).isAbsolute).getOrElse(false) && _exact(t.endpoint) &&
    _revision(t.frontierRevision) && _exact(t.frontierCause) && _frontiers(t.frontierStage) && _namespace(t.sourceNamespace) &&
    _reference(t.committedReference) && t.committedReference.revision == "1" && _revision(t.committedRevision) &&
    BigInt(t.committedRevision) <= BigInt(t.frontierRevision) &&
    t.frontierCause.startsWith("native-repository:" + t.sessionId + ":") &&
    _frontier_keys(t.frontierStage).exists(key => t.frontierCause ==
      "native-repository:" + t.sessionId + ":" + N.candidateKey(t.sourceNamespace, key)) &&
    t.committedReference.identity.startsWith(t.sessionId + ":native-") &&
    _oid(t.finalTip) && _oid(t.tree) && t.validationPolicy != null && t.validationPolicy.validateC.isSuccess &&
    t.selectedChecks != null && t.selectedChecks.forall(t.validationPolicy.checks.contains) &&
    t.selectedChecks.distinct == t.selectedChecks && t.actual != null && _observed(t.actual.observed) &&
    t.actual.mergeHead != null && t.actual.mergeHead.isEmpty && t.actual.observed.branch == t.context.localBranch &&
    t.actual.observed.head == t.finalTip && t.actual.observed.tree == t.tree && _oid(t.advertisedRemoteTip) &&
    t.retainedIntent != null && t.retainedIntent.forall(i => i != null && _reference(i.reference) && i.reference.revision == "1" &&
      _revision(i.revision) && BigInt(i.revision) <= BigInt(t.frontierRevision) &&
      Set("push", "refresh-fetch", "final-fetch")(i.kind) &&
      i.reference.identity == _identity(t.sessionId, t.sourceNamespace, i.kind + "-intent")) &&
    t.nativeObservation != null && t.nativeObservation.forall(validObservation) &&
    t.nativeObservation.forall(o => o.operation match {
      case GitOperation.Push(tip, branch) => tip.value == t.finalTip && branch.value == t.context.trackingBranch
      case GitOperation.Fetch => true
      case _ => false
    }) && t.continuationPolicy == policy && t.finitePlan == finitePlan
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Push target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def decodePublicTarget(value: Json): Either[String, Target] =
    GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeTarget)
  def targetC(value: Record): Consequence[Target] = if (value == null) N.invalid("push target is absent")
    else value.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(decodePublicTarget).fold(N.invalid, Consequence.success))
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def decodeAnswer(value: Json): Either[String, Answer] = for {
    wire <- Option(value).toRight("Push answer is absent")
    answer <- wire.as[Answer].left.map(_.message)
    _ <- Either.cond(answer.asJson == wire && choices.contains(answer.choice) && _rationale(answer.rationale) &&
      (answer.choice == "Defer" || answer.priorExecutionEnded && answer.acceptOriginalUncertainty), (),
      "Push answer requires exact fields and explicit terminal/uncertainty assertions")
  } yield answer
  def decodePublicAnswer(value: Json): Either[String, Answer] =
    GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeAnswer)
  private[admission] def currentTargetC(supplied: Target, actual: Target): Consequence[Unit] =
    if (validTarget(supplied) && validTarget(actual) && supplied == actual) Consequence.unit
      else N.invalid("push target is stale or crosses its exact Component, Session, principal or native frontier")
  def bindingC(component: String, target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, priorExecutionEnded: Boolean, acceptOriginalUncertainty: Boolean): Consequence[Json] =
    if (!validTarget(target) || component != target.componentInstance || !choices.contains(choice) || !_rationale(rationale) ||
        participant != target.context.participantIdentity || !_reference(reference) ||
        (choice != "Defer" && (!priorExecutionEnded || !acceptOriginalUncertainty)))
      N.invalid("push continuation requires its exact actual target, original principal and independent ended/UNKNOWN acceptance")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson,
      "repositoryPushContinuation" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson,
          "priorExecutionEnded" -> priorExecutionEnded.asJson, "acceptOriginalUncertainty" -> acceptOriginalUncertainty.asJson))))
  def selectBinding(value: Json): Either[String, Json] = for {
    wire <- Option(value).toRight("Push binding is absent")
    component <- wire.hcursor.get[String]("componentInstance").left.map(_.message)
    raw <- wire.hcursor.downField("repositoryPushContinuation").get[Json]("target").left.map(_.message)
    target <- decodeTarget(raw)
    answer = wire.hcursor.downField("repositoryPushContinuation").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    ended <- answer.get[Boolean]("priorExecutionEnded").left.map(_.message)
    accepted <- answer.get[Boolean]("acceptOriginalUncertainty").left.map(_.message)
    binding <- bindingC(component, target, choice, rationale, participant, reference, ended, accepted)
      .map[Either[String, Json]](Right(_)).fold(e => Left(e.toString), identity)
    _ <- Either.cond(binding == wire, (), "Push binding has missing, extra or crossed fields")
  } yield binding

  /** Reconstruct the exact historical prefix, never compare old proof to a later head. */
  def prefixC(current: InstanceRecord, revision: String): Consequence[InstanceRecord] =
    if (!_revision(revision)) N.invalid("push continuation revision is malformed")
    else current.history.find(_.revision.value.toString == revision).map(e => current.copy(revision = e.revision,
      lifecycle = e.lifecycle, currentProgression = e.currentProgression, correlation = e.correlation,
      causation = e.causation, derivedCompositeOccurrence = e.derivedCompositeOccurrence,
      committedPredecessor = e.committedPredecessor, suspension = e.suspension,
      history = current.history.takeWhile(_.revision.value <= e.revision.value))).map(Consequence.success)
      .getOrElse(N.invalid("push continuation references absent native history"))
  private def _load_c(base: N.Store, key: String): Consequence[N.Stage] =
    base.scoped("").loadC(key).flatMap(_.map(Consequence.success).getOrElse(N.invalid("push native source is absent: " + key)))
  private def _frontier_c(base: N.Store, current: InstanceRecord): Consequence[N.Stage] = for {
    _ <- if (current.identity.value == base.identity && current.lifecycle == Lifecycle.Active && current.suspension.isEmpty &&
        current.currentProgression.exists(p => _frontiers(p.value.stripPrefix("sync.")))) Consequence.unit
      else N.invalid("push description requires an active unsuspended retained push/refresh frontier")
    prefix = "native-repository:" + base.identity + ":"
    _ <- if (current.causation.value.startsWith(prefix)) Consequence.unit else N.invalid("push frontier crosses its Session")
    value <- _load_c(base, current.causation.value.stripPrefix(prefix))
    _ <- if (value.record == current && value.payload.finalTip.nonEmpty && value.payload.indexTree.nonEmpty)
      Consequence.unit else N.invalid("push frontier has no exact saved committed native source")
  } yield value
  private def _committed_c(base: N.Store, current: InstanceRecord, frontier: N.Stage): Consequence[N.Stage] = {
    val prefix = "native-repository:" + base.identity + ":"
    val stages = Set("IntegrationValidated", "NativeCommitObserved", "PublicationObserved", "PublicationCommitObserved")
    current.history.reverse.filter(e => e.currentProgression.exists(p => stages(p.value.stripPrefix("sync."))) &&
      e.causation.value.startsWith(prefix)).foldLeft(Consequence.success(Option.empty[N.Stage])) { (prior, entry) => for {
      selected <- prior
      result <- selected match {
        case Some(_) => Consequence.success(selected)
        case None => for {
          source <- _load_c(base, entry.causation.value.stripPrefix(prefix))
          actual <- prefixC(current, source.record.revision.value.toString)
          _ <- if (source.record == actual && source.record.revision == entry.revision) Consequence.unit
            else N.invalid("committed push source crosses its exact native historical prefix")
        } yield Option.when(source.payload.indexTree == frontier.payload.indexTree &&
          source.payload.finalTip.orElse(source.payload.after.map(_.head)) == frontier.payload.finalTip)(source)
      }
    } yield result }.flatMap(_.map(Consequence.success).getOrElse(N.invalid("push frontier has no actual committed native source")))
  }
  private def _stage_reference(base: N.Store, stage: N.Stage): ContextReference =
    ContextReference(base.identity + ":native-" + stage.record.causation.value.stripPrefix("native-repository:" + base.identity + ":"), "1")
  private def _scope_c(base: N.Store): Consequence[Unit] = {
    val d = base.declaration
    val c = base.context
    if (d.input.repositoryIdentity == c.repositoryIdentity && d.input.localBranch == c.localBranch &&
        d.input.trackingRemote == c.trackingRemote && d.input.trackingBranch == c.trackingBranch &&
        d.input.remoteEndpointIdentity == c.remoteEndpointIdentity && d.input.syncMode == c.syncMode &&
        d.target.root.toString == base.original.repositoryRoot && d.target.localBranch == c.localBranch &&
        d.target.remote == c.trackingRemote && d.target.remoteBranch == c.trackingBranch) Consequence.unit
      else N.invalid("push continuation crosses the exact original Start scope")
  }
  private def _intent_c(base: N.Store, current: InstanceRecord, namespace: String): Consequence[Option[Intent]] = {
    val keys = Vector("push", "refresh-fetch", "final-fetch")
    keys.foldLeft(Consequence.success(Vector.empty[Intent])) { (prior, kind) => for {
      values <- prior
      saved <- base.scoped("").loadC(N.candidateKey(namespace, kind + "-intent"))
      selected <- saved match {
        case None => Consequence.success(None)
        case Some(stage) if stage.record.revision.value <= current.revision.value => for {
          prefix <- prefixC(current, stage.record.revision.value.toString)
          _ <- if (prefix == stage.record) Consequence.unit else N.invalid("push intent crosses actual historical prefix")
        } yield Some(Intent(ContextReference(_identity(base.identity, namespace, kind + "-intent"), "1"),
          stage.record.revision.value.toString, kind))
        case _ => Consequence.success(None)
      }
    } yield values ++ selected.toVector }.map(_.sortBy(i => BigInt(i.revision)).lastOption)
  }
  private def _source_namespace_c(base: N.Store, current: InstanceRecord,
    selected: Option[Saved]): Consequence[String] = selected match {
    case Some(value) => Consequence.success(value.namespace)
    case None => RepositorySyncValidationContinuation.selectedC(base.scoped(""), current)
      .map(_.map(_.namespace).getOrElse(""))
  }
  private def _source_c(identity: String, declaration: F.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, validation: C.Policy, acknowledgement: String => Consequence[Unit]): Consequence[Source] = for {
    session <- F.sessionC(identity, declaration, definition, core)
    base <- N.bindC(session, declaration, core, acknowledgement)
    _ <- _scope_c(base)
    _ <- validation.validateC
    selected <- selectedC(base, session.started.current)
    frontier <- _frontier_c(base, session.started.current)
    committed <- _committed_c(base, frontier.record, frontier)
    // Global loaders keep exact keys; only effect execution is scoped.
    sourcens <- _source_namespace_c(base, session.started.current, selected)
    _ <- if (frontier.payload.pushContinuation == selected.map(_.direction)) Consequence.unit
      else N.invalid("push source has crossed active direction provenance")
    intent <- _intent_c(base, frontier.record, sourcens)
    native <- G.inspectC(core)
    _ <- base.scopeC(native.observed)
    remote <- G.remoteTipC(core, declaration.target.remoteBranch)
    commit <- G.commitC(core, frontier.payload.finalTip.get)
    target = Target(core.component.get.core.instanceId.canonicalKey, identity, session.context,
      base.original.repositoryRoot, declaration.target.endpoint, frontier.record.revision.value.toString,
      frontier.record.causation.value, frontier.payload.stage, sourcens, intent,
      _stage_reference(base, committed), committed.record.revision.value.toString, frontier.payload.finalTip.get, frontier.payload.indexTree.get,
      validation, frontier.payload.validation.map(_.selected).getOrElse(validation.checks), native, remote,
      frontier.payload.pushObservation, policy, finitePlan)
    _ <- if (validTarget(target) && commit.identity == target.finalTip && commit.tree == target.tree) Consequence.unit
      else N.invalid("push target requires the actual clean committed source and fixed original check policy")
  } yield Source(base, frontier, target)

  /** Authenticate the adjacent saved adoption, old source and exact historical intent. */
  def selectedC(base: N.Store, current: InstanceRecord): Consequence[Option[Saved]] =
    current.history.reverse.find(_.currentProgression.contains(ProgressionReference("sync.PushContinuationAdmitted"))) match {
      case None => Consequence.success(None)
      case Some(entry) => for {
        _ <- _scope_c(base)
        prefix = "native-repository:" + base.identity + ":"
        _ <- if (entry.causation.value.startsWith(prefix)) Consequence.unit else N.invalid("push adoption crosses its Session")
        stage <- _load_c(base, entry.causation.value.stripPrefix(prefix))
        direction <- stage.payload.pushContinuation.map(Consequence.success).getOrElse(N.invalid("push adoption direction is absent"))
        _ <- direction.asJson.as[Direction].fold(e => N.invalid(e.message), _ => Consequence.unit)
        target = direction.target
        before <- prefixC(current, target.frontierRevision)
        adoption <- prefixC(current, entry.revision.value.toString)
        source <- _frontier_c(base, before)
        committed <- _committed_c(base, before, source)
        previous <- selectedC(base.scoped(""), before)
        originalnamespace <- _source_namespace_c(base, before, previous)
        originalintent <- _intent_c(base, before, target.sourceNamespace)
        _ <- if (target.componentInstance == base.core.component.get.core.instanceId.canonicalKey &&
            target.sessionId == base.identity && target.context == base.context && target.root == base.original.repositoryRoot &&
            target.endpoint == base.declaration.target.endpoint && target.sourceNamespace == originalnamespace &&
            source.payload.pushContinuation == previous.map(_.direction) && source.record.causation.value == target.frontierCause &&
            source.payload.stage == target.frontierStage && source.payload.finalTip.contains(target.finalTip) &&
            source.payload.indexTree.contains(target.tree) && source.payload.pushObservation == target.nativeObservation &&
            _stage_reference(base, committed) == target.committedReference && committed.record.revision.value.toString == target.committedRevision &&
            originalintent == target.retainedIntent && source.payload.validation.map(_.selected).getOrElse(target.validationPolicy.checks) == target.selectedChecks &&
            stage.record == adoption && adoption.revision == before.revision.next && adoption.history.dropRight(1) == before.history &&
            adoption.lifecycle == Lifecycle.Active && adoption.suspension.isEmpty &&
            entry.causation.value == prefix + adoptionKey(target) &&
            stage.payload == source.payload.copy(stage = "PushContinuationAdmitted", pushContinuation = Some(direction)))
          Consequence.unit else N.invalid("push adoption crosses its exact adjacent source, intent or actual Session lineage")
      } yield Some(Saved(stage, direction))
    }
  def activeStoreC(base: N.Store, current: InstanceRecord): Consequence[N.Store] =
    selectedC(base.scoped(""), current).map(_.fold(base)(s => base.scoped(s.namespace, pushDirection = Some(s.direction))))
  def convergenceStoreC(base: N.Store, accepted: N.Stage): Consequence[N.Store] =
    selectedC(base.scoped(""), accepted.record).flatMap {
      case None => Consequence.success(base)
      case Some(saved) if accepted.payload.pushContinuation.contains(saved.direction) => for {
        _ <- _fetch_source_c(base.scoped(""), accepted.record, saved)
      } yield base.scoped(saved.namespace, pushDirection = Some(saved.direction))
      case _ => N.invalid("convergence source has no authenticated active push direction")
    }
  private def _fetch_source_c(base: N.Store, current: InstanceRecord, saved: Saved): Consequence[N.Stage] = for {
    intent <- _load_c(base, N.candidateKey(saved.namespace, "refresh-fetch-intent"))
    returned <- _load_c(base, N.candidateKey(saved.namespace, "refresh-fetch-result"))
    observed <- _load_c(base, N.candidateKey(saved.namespace, "refresh-fetch-observed"))
    actualintent <- prefixC(current, intent.record.revision.value.toString)
    actualreturned <- prefixC(current, returned.record.revision.value.toString)
    actualobserved <- prefixC(current, observed.record.revision.value.toString)
    _ <- if (intent.record == actualintent && returned.record == actualreturned && observed.record == actualobserved &&
        intent.record.revision == saved.stage.record.revision.next && intent.record.history.dropRight(1) == saved.stage.record.history &&
        returned.record.revision == intent.record.revision.next && returned.record.history.dropRight(1) == intent.record.history &&
        observed.record.revision == returned.record.revision.next && observed.record.history.dropRight(1) == returned.record.history &&
        intent.payload.stage == "PushRefreshFetchPreparing" && intent.payload.before == saved.direction.target.actual.observed &&
        intent.payload.pushContinuation.contains(saved.direction) && intent.payload.validation.isEmpty && intent.payload.purposes.isEmpty &&
        returned.payload.pushObservation.exists(o => successful(o) && o.operation == GitOperation.Fetch) &&
        returned.payload == intent.payload.copy(stage = "PushRefreshFetched", pushObservation = returned.payload.pushObservation) &&
        observed.payload == returned.payload.copy(after = observed.payload.after) && observed.payload.stage == "PushRefreshFetched" &&
        observed.payload.after.exists(a => _observed(a) && a.head == saved.direction.target.finalTip &&
          a.tree == saved.direction.target.tree && a.trackingTip.contains(saved.direction.target.advertisedRemoteTip)))
      Consequence.unit else N.invalid("push candidate lacks the exact actual adjacent fresh Fetch result and observed relation")
  } yield observed
  def authenticateCandidateC(base: N.Store, current: InstanceRecord, pending: N.Stage,
    namespace: String): Consequence[Unit] = for {
    selected <- selectedC(base.scoped(""), current).flatMap(_.map(Consequence.success)
      .getOrElse(N.invalid("push candidate has no actual admitted direction")))
    fetched <- _fetch_source_c(base.scoped(""), current, selected)
    _ <- _candidate_before_c(base.scoped(""), current, pending, selected, fetched)
    actual <- prefixC(current, pending.record.revision.value.toString)
    _ <- if (pending.record == actual && pending.payload.pushContinuation.contains(selected.direction) &&
        pending.record.revision.value > selected.stage.record.revision.value &&
        (namespace == selected.namespace || namespace.matches("repair-[1-9][0-9]*")) &&
        pending.record.causation.value == "native-repository:" + base.identity + ":" + N.candidateKey(namespace, "pending-merge") &&
        pending.payload.stage == "PendingMergePrepared" && pending.payload.purposes.isEmpty &&
        pending.payload.mergeHead == fetched.payload.after.get.trackingTip && pending.payload.validation.isEmpty &&
        pending.payload.after.exists(a => a.head == fetched.payload.after.get.head && a.trackingTip == fetched.payload.after.get.trackingTip) &&
        pending.payload.continuation.isEmpty && pending.payload.requiredLint.isEmpty &&
        pending.payload.reviewInputs.isEmpty && pending.payload.formalFailure.isEmpty &&
        pending.payload.mergeCheckContinuation.isEmpty && pending.payload.validationPreparation.isEmpty)
      Consequence.unit else N.invalid("push candidate crosses actual history, active direction or new candidate receipt boundary")
  } yield ()
  private def _candidate_before_c(base: N.Store, current: InstanceRecord, pending: N.Stage,
    selected: Saved, fetched: N.Stage): Consequence[Unit] =
    if (pending.payload.before == fetched.payload.after.get) Consequence.unit else for {
      conflict <- _load_c(base, N.candidateKey(selected.namespace, "merge-conflicted"))
      actual <- prefixC(current, conflict.record.revision.value.toString)
      _ <- if (conflict.record == actual && conflict.payload.stage == "MergeConflicted" &&
          conflict.payload.pushContinuation.contains(selected.direction) && conflict.payload.before == fetched.payload.after.get &&
          conflict.payload.after.contains(pending.payload.before) && conflict.payload.mergeHead == pending.payload.mergeHead)
        Consequence.unit else N.invalid("push conflict candidate lost its exact real fetched conflict source")
    } yield ()

  def describeC(identity: String, declaration: F.Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    validation: C.Policy, acknowledgement: String => Consequence[Unit]): Consequence[V.RepositorySyncPushContinuationDescriptionResult] =
    _source_c(identity, declaration, definition, core, validation, acknowledgement).map(source =>
      V.RepositorySyncPushContinuationDescriptionResult(identity, "adoption-required", message,
        source.target.componentInstance, targetRecord(source.target), choices, adoptionOperation))
  private def _execution_evidence_c(input: V.RepositorySyncPushContinuationAdoptionInput,
    target: Target): Consequence[Unit] =
    if (input != null && target != null && input.sessionId == target.sessionId && input.directionReference != null &&
        _exact(input.directionReference.identity) && _exact(input.directionReference.revision) &&
        input.executionEvidence != null && input.executionEvidence.workerIdentity != null &&
        input.executionEvidence.workerIdentity.contains(target.context.participantIdentity) &&
        input.executionEvidence.references != null && input.executionEvidence.references.forall(reference =>
          reference != null && _exact(reference.identity) && _exact(reference.revision)) &&
        input.executionEvidence.references.exists(reference => reference.identity == input.directionReference.identity &&
          reference.revision == input.directionReference.revision)) Consequence.unit
      else N.invalid("push adoption requires the original Session/principal and complete execution evidence references containing its exact directionReference")
  def adoptC(input: V.RepositorySyncPushContinuationAdoptionInput, declaration: F.Declaration,
    definition: DefinitionBinding, core: ActionCall.Core, validation: C.Policy, installation: Installation,
    acknowledgement: String => Consequence[Unit]): Consequence[V.RepositorySyncPushContinuationAdoptionResult] = for {
    supplied <- targetC(input.target)
    _ <- _execution_evidence_c(input, supplied)
    reference = ContextReference(input.directionReference.identity, input.directionReference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, supplied, input.choice, input.rationale,
      supplied.context.participantIdentity, reference, input.priorExecutionEnded, input.acceptOriginalUncertainty)
    session <- F.sessionC(input.sessionId, declaration, definition, core)
    base <- N.bindC(session, declaration, core, acknowledgement)
    previous <- selectedC(base, session.started.current)
    direction = Direction(supplied, input.choice, input.rationale, supplied.context.participantIdentity, reference,
      input.priorExecutionEnded, input.acceptOriginalUncertainty)
    result <- previous match {
      case Some(saved) if saved.direction.target == supplied => for {
        _ <- if (saved.direction == direction) Consequence.unit else N.invalid("push adoption contradicts its already saved exact answer")
        _ <- installation.evidence.admitC(core, reference, binding)
      } yield V.RepositorySyncPushContinuationAdoptionResult(input.sessionId, true, "already-adopted",
        "The exact direction is already retained; no transition or effect was repeated.")
      case _ => for {
        source <- _source_c(input.sessionId, declaration, definition, core, validation, acknowledgement)
        _ <- currentTargetC(supplied, source.target)
        result <- if (input.choice == "Defer") Consequence.success(V.RepositorySyncPushContinuationAdoptionResult(
          input.sessionId, false, "deferred", "No direction or effect was stored; original native facts remain unchanged."))
          else for {
            _ <- installation.evidence.admitC(core, reference, binding)
            actual <- _source_c(input.sessionId, declaration, definition, core, validation, acknowledgement)
            _ <- currentTargetC(supplied, actual.target)
            _ <- actual.store.saveC(adoptionKey(supplied), "native-push-continuation-admitted", actual.frontier.record,
              actual.frontier.payload.copy(stage = "PushContinuationAdmitted", pushContinuation = Some(direction)))
          } yield V.RepositorySyncPushContinuationAdoptionResult(input.sessionId, true, "adopted",
            "One finite push continuation direction stored. Run separate ordinary advanceRepositorySync.")
      } yield result
    }
  } yield result

  def waitC(store: N.Store, stage: String): Consequence[V.RepositorySyncAdvanceResult] = Consequence.success(
    NativeWorkflowOperationOutput.repositoryWait(store.identity, message + " Use " + describeOperation + " --sessionId " + store.identity + ".", stage))
  /** Dispatch before old Purpose and original merge/fetch caches. */
  def advanceC(base: N.Store, selected: Saved, current: InstanceRecord,
    conflicts: Option[RepositorySyncNativeConflictContinuation.Port], validation: Option[C.Policy],
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port],
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort],
    repair: Option[RepositorySyncNativeMergeRepairContinuation.Port]): Consequence[V.RepositorySyncAdvanceResult] = for {
    policy <- validation.filter(_ == selected.direction.target.validationPolicy).map(Consequence.success)
      .getOrElse(N.invalid("push continuation requires unchanged original fixed validation bindings"))
    store = base.scoped(selected.namespace, pushDirection = Some(selected.direction))
    stage = current.currentProgression.map(_.value.stripPrefix("sync.")).getOrElse("")
    result <- if (Set("PushContinuationAdmitted", "PushRefreshFetchPreparing", "PushRefreshFetched")(stage)) for {
      fetched <- _fetch_c(store, selected, current, convergence)
      result <- if (fetched.payload.stage != "PushRefreshFetched") waitC(store, fetched.payload.stage)
        else RepositorySyncNativeProgression.progressC(store, fetched.record, fetched.payload.after.get,
          conflicts, validation, review, convergence, repair)
    } yield result
      else if (Set("NativePushRefreshRequired", "NativePushFailed", "NativePushUncertain",
        "PushRefreshFetchFailed", "PushRefreshFetchUncertain")(stage)) waitC(store, stage)
      else if (Set("MergeConflicted", "MergeResolutionAdmitted", "ConflictDecisionRequired")(stage))
        conflicts.map(_.advanceC(store.identity, store.core)).getOrElse(waitC(store, stage))
      else if (Set("ReviewRepairRequired", "ValidationRepairRequired", "RepairProposalAdmitted", "MaterializingRepair",
        "RepairDecisionRequired", "RepairBudgetContinuationAdmitted")(stage))
        repair.map(_.advanceC(store.identity, store.core)).getOrElse(waitC(store, stage))
      else _resume_c(base, store, current, conflicts, validation, review, convergence, repair)
  } yield result
  private def _fetch_c(store: N.Store, selected: Saved, current: InstanceRecord,
    authority: Option[RepositorySyncNativeConvergence.AuthorityPort]): Consequence[N.Stage] = for {
    completed <- store.loadC("refresh-fetch-observed")
    retained <- store.loadC("refresh-fetch-intent")
    returned <- store.loadC("refresh-fetch-result")
    result <- completed match {
      case Some(value) => Consequence.success(value)
      case None => for {
        native <- G.inspectC(store.core)
        _ <- store.scopeC(native.observed)
        _ <- if (native.mergeHead.isEmpty && native.observed.dirty.isEmpty &&
            native.observed.head == selected.direction.target.finalTip && native.observed.tree == selected.direction.target.tree)
          Consequence.unit else N.invalid("fresh push fetch requires its exact adopted clean local committed source")
        captured <- returned match {
          case Some(value) => Consequence.success(value)
          case None if retained.nonEmpty => for {
            // Missing actual returned result remains UNKNOWN. Never execute Fetch again.
            value <- store.saveC("refresh-fetch-result", "native-push-refresh-fetch-uncertain", retained.get.record,
              retained.get.payload.copy(stage = "PushRefreshFetchUncertain", after = Some(native.observed)))
          } yield value
          case None => for {
            _ <- if (current == selected.stage.record && native == selected.direction.target.actual) Consequence.unit
              else N.invalid("push refresh source drifted since its exact admitted native direction")
            remote <- G.remoteTipC(store.core, store.context.trackingBranch)
            value <- if (remote != selected.direction.target.advertisedRemoteTip)
              store.saveC("refresh-required", "native-push-refresh-required", current,
                selected.stage.payload.copy(stage = "NativePushRefreshRequired", after = Some(native.observed)))
              else for {
                port <- authority.map(Consequence.success).getOrElse(N.invalid("push refresh requires original host Fetch authority"))
                _ <- port.admitC(store.core, store.context, store.declaration.target, RepositorySyncNativeConvergence.Effect.FinalFetch)
                intent <- store.saveC("refresh-fetch-intent", "native-push-refresh-fetch-prepared", current,
                  N.Payload("PushRefreshFetchPreparing", store.original.repositoryRoot, native.observed,
                    indexTree = Some(native.observed.tree), finalTip = Some(native.observed.head),
                    pushContinuation = Some(selected.direction)))
                actual <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.Fetch)
                observation <- observationC(actual)
                stage = if (successful(observation)) "PushRefreshFetched"
                  else if (failed(observation)) "PushRefreshFetchFailed" else "PushRefreshFetchUncertain"
                event = if (successful(observation)) "native-push-refresh-fetch-observed"
                  else if (failed(observation)) "native-push-refresh-fetch-failed" else "native-push-refresh-fetch-uncertain"
                // Retain returned typed data before any further Git observation can fail.
                result <- store.saveC("refresh-fetch-result", event, intent.record,
                  intent.payload.copy(stage = stage, pushObservation = Some(observation)))
              } yield result
          } yield value
        }
        saved <- if (captured.payload.stage != "PushRefreshFetched") Consequence.success(captured)
          else for {
            observation <- captured.payload.pushObservation.filter(successful).map(Consequence.success)
              .getOrElse(N.invalid("fresh fetched state has no actual successful returned Fetch result"))
            after <- G.inspectC(store.core)
            _ <- store.scopeC(after.observed)
            advertised <- G.remoteTipC(store.core, store.context.trackingBranch)
            stable = after.mergeHead.isEmpty && after.observed.dirty.isEmpty &&
              captured.payload.finalTip.contains(after.observed.head) && captured.payload.indexTree.contains(after.observed.tree) &&
              after.observed.trackingTip.contains(advertised) && advertised == selected.direction.target.advertisedRemoteTip
            stage = if (stable) "PushRefreshFetched" else "PushRefreshFetchUncertain"
            event = if (stable) "native-push-refresh-fetch-observed" else "native-push-refresh-fetch-uncertain"
            value <- store.saveC("refresh-fetch-observed", event, captured.record,
              captured.payload.copy(stage = stage, after = Some(after.observed), pushObservation = Some(observation)))
          } yield value
      } yield saved
    }
  } yield result
  private def _resume_c(base: N.Store, store: N.Store, current: InstanceRecord,
    conflicts: Option[RepositorySyncNativeConflictContinuation.Port], validation: Option[C.Policy],
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port],
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort],
    repair: Option[RepositorySyncNativeMergeRepairContinuation.Port]): Consequence[V.RepositorySyncAdvanceResult] = for {
    candidate <- if (current.history.exists(e => e.currentProgression.contains(ProgressionReference("sync.PendingMergePrepared")) &&
        e.revision.value > store.pushContinuation.get.target.frontierRevision.toLong))
      base.currentMergeCandidateC(current).map(Some(_)) else Consequence.success(None)
    accepted <- if (candidate.nonEmpty) review.fold(Consequence.success(Option.empty[N.Stage]))(_.acceptedC(store.identity, store.core))
      else Consequence.success(None)
    integrated <- store.loadC("integration-validated")
    result <- accepted.orElse(integrated) match {
      case Some(value) if convergence.nonEmpty => for {
        _ <- if (value.payload.pushContinuation == store.pushContinuation) Consequence.unit
          else N.invalid("push convergence cannot select an old accepted candidate")
        _ <- integrated.filter(_ == value).fold(Consequence.unit)(checked => for {
          original <- store.loadC("integrated").flatMap(_.map(Consequence.success).getOrElse(N.invalid("fresh integration source is absent")))
          source <- C.integrationSourceC(original)
          policy <- validation.map(Consequence.success).getOrElse(N.invalid("fresh integration requires selected policy"))
          _ <- C.completedC(checked, source, RepositorySyncNativeProgression.integrationPolicy(store.declaration, policy), merge = false)
        } yield ())
        result <- RepositorySyncNativeConvergence.advanceC(base, value, convergence.get, validation)
      } yield result
      case _ if candidate.nonEmpty => RepositorySyncNativeProgression.pendingMergeC(base, candidate.get.pending,
        validation, review, convergence, candidate.get.namespace, repair)
      case _ => for {
        fetched <- store.loadC("refresh-fetch-observed").flatMap(_.filter(_.payload.stage == "PushRefreshFetched")
          .map(Consequence.success).getOrElse(N.invalid("push continuation has no genuine fresh fetched relation")))
        result <- RepositorySyncNativeProgression.progressC(store, fetched.record, fetched.payload.after.get,
          conflicts, validation, review, convergence, repair)
      } yield result
    }
  } yield result

  def operations(declaration: F.Declaration, definition: DefinitionBinding, installation: Installation,
    validation: Option[C.Policy], acknowledgement: String => Consequence[Unit]): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, declaration, definition, installation, validation, acknowledgement),
      new Operation(false, declaration, definition, installation, validation, acknowledgement))
  private[admission] def adoptionInputC(record: Record): Consequence[V.RepositorySyncPushContinuationAdoptionInput] = for {
    _ <- if (record != null && DevelopmentOperationalView.uniqueFields(record)) Consequence.unit
      else N.invalid("push input is absent or has duplicate fields")
    raw <- record.toJsonStringC
    _ <- GoalPhasePrerequisitePublicationRecovery.validateBusinessTextC(raw)
    json <- io.circe.parser.parse(raw).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
      .fold(N.invalid, Consequence.success)
    _ <- if (json.asObject.exists(_.keys.toSet == Set("sessionId", "target", "choice", "rationale", "directionReference",
        "priorExecutionEnded", "acceptOriginalUncertainty", "executionEvidence"))) Consequence.unit
      else N.invalid("push adoption requires exact complete input fields")
    targetjson <- json.hcursor.get[Json]("target").fold(e => N.invalid(e.message), Consequence.success)
    target <- decodeTarget(targetjson).fold(N.invalid, Consequence.success)
    _ <- decodeAnswer(Json.obj(Vector("choice", "rationale", "priorExecutionEnded", "acceptOriginalUncertainty")
      .map(key => key -> json.hcursor.downField(key).focus.getOrElse(Json.Null))*))
      .fold(N.invalid, _ => Consequence.unit)
    _ <- if (json.hcursor.downField("directionReference").focus.flatMap(_.asObject).exists(_.keys.toSet == Set("identity", "revision")))
      Consequence.unit else N.invalid("push directionReference requires exact identity/revision fields")
    value <- NativeWorkflowOperationInput.decodeValueC[V.RepositorySyncPushContinuationAdoptionInput](record, "input")
    _ <- if (value != null && _exact(value.sessionId) && value.executionEvidence != null && value.directionReference != null)
      Consequence.unit else N.invalid("push adoption requires its full typed input")
    _ <- _execution_evidence_c(value, target)
    restored <- value.toRecord().toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).fold(N.invalid, Consequence.success))
    _ <- if (restored == json) Consequence.unit else N.invalid("push adoption discarded or coerced fields")
  } yield value
  private[admission] def adoptionSourceC(request: Request): Consequence[NativeWorkflowResourceInput.Source[Record]] = for {
    source <- GoalPhasePrerequisitePublicationRecovery.resourceInputC(request)
    _ <- source match {
      case NativeWorkflowResourceInput.Inline(record) => adoptionInputC(record).map(_ => ())
      case _: NativeWorkflowResourceInput.Resource => Consequence.unit
    }
  } yield source
  private[admission] def readAdoptionInputC(source: NativeWorkflowResourceInput.Source[Record],
    read: org.goldenport.cncf.resource.ResourceReference => Consequence[String]): Consequence[V.RepositorySyncPushContinuationAdoptionInput] =
    NativeWorkflowResourceInput.readC(source, reference => read(reference)
      .flatMap(GoalPhasePrerequisitePublicationRecovery.validateBusinessTextC)).flatMap(adoptionInputC)
  def sessionC(suppliedRequest: Request, call: ActionCall.Core): Consequence[String] = {
    final class Reader extends ProcedureActionCall {
      val core = call
      def readSessionC: Consequence[String] = adoptionSourceC(suppliedRequest).flatMap(source =>
        readAdoptionInputC(source, reference => read_resource_text(reference,
          Some(java.nio.charset.StandardCharsets.UTF_8))).map(_.sessionId))
      def execute(): Consequence[OperationResponse] = N.invalid("push continuation reader is not an Operation")
    }
    new Reader().readSessionC
  }
  private final class Operation(describe: Boolean, declaration: F.Declaration, definition: DefinitionBinding,
    installation: Installation, validation: Option[C.Policy], acknowledgement: String => Consequence[Unit]) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.RepositorySyncServiceService.DescribeRepositorySyncPushContinuationOperation
      else SmWorkflowComponent.RepositorySyncServiceService.AdoptRepositorySyncPushContinuationOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("RepositorySyncService") ||
          request.operation != (if (describe) describeOperation else adoptionOperation)) N.invalid("push Operation identity differs")
      else if (describe) NativeWorkflowOperationInput.textC(request, "sessionId").map(id => Action(request, Some(id), None))
      else adoptionSourceC(request).map(source => Action(request, None, Some(source)))
    private final case class Action(request: Request, identity: Option[String],
      input: Option[NativeWorkflowResourceInput.Source[Record]]) extends CommandAction {
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          value <- input match {
            case None => Consequence.success(Option.empty[V.RepositorySyncPushContinuationAdoptionInput])
            case Some(source) => readAdoptionInputC(source, reference => read_resource_text(reference,
              Some(java.nio.charset.StandardCharsets.UTF_8))).map(Some(_))
          }
          session = value.map(_.sessionId).orElse(identity).get
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
          selected = core.copy(executionContext = context)
          policy <- validation.map(Consequence.success).getOrElse(N.invalid("push continuation requires fixed original validation policy"))
          _ <- installation.validateC
          result <- value match {
            case None => describeC(session, declaration, definition, selected, policy, acknowledgement).map(NativeWorkflowOperationOutput.response(_))
            case Some(value) => adoptC(value, declaration, definition, selected, policy, installation, acknowledgement).map(NativeWorkflowOperationOutput.response(_))
          }
        } yield result
      }
    }
  }
}
