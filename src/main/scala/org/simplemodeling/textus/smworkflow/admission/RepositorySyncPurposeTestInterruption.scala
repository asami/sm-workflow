package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation}
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedFetch as F
import RepositorySyncNativeMergeValidation as C
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P
import RepositorySyncPurpose.given
import RepositorySyncPurposeContentContinuation.{exact, ordinary, prefixC, revision}
import RepositorySyncValidationContinuation as A

/** A host-observed termination permits repair of one resultless direct Purpose Test.
  * @since   Nov. 20, 2026
  * @version Nov. 20, 2026
  */
private[smworkflow] object RepositorySyncPurposeTestInterruption {
  val policy = "sm-workflow.repository-sync-purpose-test-interruption.v1"
  val legacyPolicy = "sm-workflow.repository-sync-purpose-test-interruption-legacy.v1"
  val legacySelector = "legacy-missing-plan"
  val observationKind = "purpose-test-termination"
  val observationPurpose = "repository-sync-purpose-test-termination"
  val observationSchema = "sm-workflow.repository-sync-purpose-test-termination.v1"
  val legacyObservationKind = "purpose-test-legacy-termination"
  val legacyObservationPurpose = "repository-sync-purpose-test-legacy-termination"
  val legacyObservationSchema = "sm-workflow.repository-sync-purpose-test-legacy-termination.v1"
  val choice = "ContinuePurposeRepairAcceptingUnknown"
  val legacyChoice = "ContinueLegacyPurposeRepairAcceptingUnknown"
  val choices = Vector(choice, "Defer")
  val originalPlanNotRetained = "not-retained"
  private val _event = "native-purpose-test-interruption-admitted"
  private val _message = "The retained direct Purpose Test has no product result. ContinuePurposeRepairAcceptingUnknown requires " +
    "the exact independent Human direction and separately imported CNCF Started and confirmed Terminal observations " +
    "for the original Test process. The old effects remain UNKNOWN. Separate ordinary advanceRepositorySync issues the " +
    "owning Purpose Repair. Defer records no direction."
  private val _legacy_message = "The retained direct Purpose Test has no product result. " +
    "ContinueLegacyPurposeRepairAcceptingUnknown requires the exact independent Human direction and separately imported " +
    "termination evidence with the archived invocation binding and trace. The original plan was not retained and the old " +
    "effects remain UNKNOWN. Separate ordinary advanceRepositorySync issues the owning Purpose Repair. Defer records no direction."

  final case class Candidate(groupIdentity: String, paths: Vector[String], parent: String, index: String,
    commit: String, tree: String, projectedIndex: String, worktreeBefore: Option[String], worktreeAfter: Option[String])
  final case class ReceiptLink(identity: String, reference: ContextReference, revision: String)
  object ReceiptLink {
    given Encoder[ReceiptLink] = deriveEncoder
    given Decoder[ReceiptLink] = deriveDecoder
  }
  final case class Target(continuationPolicy: String, componentInstance: String, sessionId: String,
    context: F.StartContext, root: String, endpoint: String, frontierRevision: String, frontierCause: String,
    phase: Option[String], cursor: Int, attempt: Int, slotCursor: Int, candidateReference: ContextReference,
    candidateRevision: String, candidate: Candidate, intentReference: ContextReference, intentRevision: String,
    selectedChecks: Vector[C.Check], checkIndex: Int, check: C.Check, successfulPrefix: Vector[ReceiptLink],
    slot: String, checkWorkspace: String, programIdentity: String, processPlan: String)
  object Target {
    given Encoder[Target] = deriveEncoder[Target].mapJson(RepositorySyncPurposeContentContinuation.canonical)
    private val _raw: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (validTarget(value) && value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Purpose Test interruption requires the exact canonical target", cursor.history))))
  }

  // These types are the metadata-only Started and Terminal wire projections of
  // CNCF ProcessExecutionProgress. Exact decoding excludes captured output.
  final case class Started(operation: String, workspaceId: String, programIdentity: String, state: String, pid: Long)
  final case class Terminal(operation: String, workspaceId: String, programIdentity: String, state: String, pid: Long,
    termination: String, exitCode: Int, elapsedMillis: Long)
  final case class Observation(schemaVersion: String, target: Target, reference: ContextReference,
    observer: String, invocationReference: ContextReference, traceReference: ContextReference,
    hostPlanReference: Option[ContextReference], started: Started, terminal: Terminal,
    legacyBindingReference: Option[ContextReference] = None) {
    def references: Vector[ContextReference] =
      Vector(reference, invocationReference, traceReference) ++ hostPlanReference ++ legacyBindingReference
  }
  object Observation {
    def apply(schemaVersion: String, target: Target, reference: ContextReference, observer: String,
      invocationReference: ContextReference, traceReference: ContextReference, hostPlanReference: ContextReference,
      started: Started, terminal: Terminal): Observation =
      Observation(schemaVersion, target, reference, observer, invocationReference, traceReference,
        Some(hostPlanReference), started, terminal)

    given Encoder[Observation] = deriveEncoder[Observation].mapJson(_.mapObject(_.filter {
      case ("hostPlanReference" | "legacyBindingReference", value) => !value.isNull
      case _ => true
    }))
    private val _raw: Decoder[Observation] = deriveDecoder
    given Decoder[Observation] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (validObservation(value) && value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Purpose Test requires matching CNCF Started and confirmed output-limit Terminal metadata", cursor.history))))
  }
  final case class AsRunPlan(invocationReference: ContextReference, traceReference: ContextReference, pid: Long, plan: Json)
  object AsRunPlan {
    given Encoder[AsRunPlan] = deriveEncoder
    private val _raw: Decoder[AsRunPlan] = deriveDecoder
    given Decoder[AsRunPlan] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (value.asJson == cursor.value && value.pid > 0 && value.plan.isObject) Right(value)
      else Left(DecodingFailure("Purpose Test as-run plan requires the exact invocation, trace, PID and plan", cursor.history))))
  }
  final case class LegacyRun(sessionId: String, frontierRevision: String, intentReference: ContextReference,
    candidateReference: ContextReference, invocationReference: ContextReference, traceReference: ContextReference,
    operation: String, cliExitCode: Int, pid: Long, originalPlanStatus: String)
  object LegacyRun {
    given Encoder[LegacyRun] = deriveEncoder
    private val _raw: Decoder[LegacyRun] = deriveDecoder
    given Decoder[LegacyRun] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (value.asJson == cursor.value && exact(value.sessionId) && revision(value.frontierRevision) &&
          Vector(value.intentReference, value.candidateReference, value.invocationReference, value.traceReference).forall(_ref) &&
          value.operation == "advanceRepositorySync" && value.cliExitCode != 0 && value.pid > 0 &&
          value.originalPlanStatus == originalPlanNotRetained) Right(value)
      else Left(DecodingFailure("Legacy Purpose Test requires the exact archived invocation binding", cursor.history))))
  }
  final case class Direction(observation: Observation, choice: String, rationale: String, humanReference: ContextReference,
    originalEffects: String) {
    def target: Target = observation.target
  }
  object Direction {
    given Encoder[Direction] = deriveEncoder
    private val _raw: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (value.asJson == cursor.value && _valid_direction(value)) Right(value)
      else Left(DecodingFailure("Purpose Test repair direction requires separate exact evidence and UNKNOWN effects", cursor.history))))
  }

  final case class Proof(intent: ContextReference, frontierRevision: String, observation: ContextReference,
    candidateCommit: String, parent: String, candidateTree: String, check: String,
    selectedChecks: Vector[String], successfulPrefix: Vector[String], originalEffects: String,
    originalPlanStatus: Option[String] = None)
  object Proof {
    given Encoder[Proof] = deriveEncoder[Proof].mapJson(_.mapObject(_.filter {
      case ("originalPlanStatus", value) => !value.isNull
      case _ => true
    }))
    private val _raw: Decoder[Proof] = deriveDecoder
    given Decoder[Proof] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (value.asJson == cursor.value && _valid_proof(value)) Right(value)
      else Left(DecodingFailure("Repair interruption proof has incomplete or crossed candidate facts", cursor.history))))
  }

  private final case class Source(stage: N.Stage, target: Target)
  private def _oid(value: String): Boolean = value.matches("[0-9a-f]{40}|[0-9a-f]{64}")
  private def _ref(value: ContextReference): Boolean = exact(value.identity) && exact(value.revision)
  private def _distinct(first: ContextReference, second: ContextReference): Boolean = first.identity != second.identity
  private def _active(current: InstanceRecord, stage: String): Boolean = current.lifecycle == Lifecycle.Active &&
    current.suspension.isEmpty && current.currentProgression.contains(ProgressionReference("sync." + stage))
  def descriptionFrontier(current: InstanceRecord): Boolean =
    current.currentProgression.contains(ProgressionReference("sync.PurposeChecking")) ||
      (current.currentProgression.contains(ProgressionReference("sync.PurposeRepairRequired")) && {
        val prefix = _cause(current.identity.value, "")
        current.history.reverseIterator.find(entry => entry.currentProgression.contains(ProgressionReference("sync.PurposeRepairRequired")) &&
          entry.causation.value.startsWith(prefix)).exists(_.causation.value.stripPrefix(prefix)
            .matches("(?:[A-Za-z0-9][A-Za-z0-9_.-]*-)?purpose-test-interruption-[1-9][0-9]*-adopted"))
      })
  private def _native_reference(store: N.Store, key: String): ContextReference = {
    val reference = store.reference(key)
    ContextReference(reference.identity, reference.revision)
  }
  private def _cause(session: String, key: String): String = "native-repository:" + session + ":" + key
  private def _load_c(store: N.Store, key: String): Consequence[N.Stage] = store.loadC(key)
    .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Purpose Test interruption native source is absent: " + key)))
  private def _history_c(current: InstanceRecord, stage: N.Stage): Consequence[Unit] =
    prefixC(current, stage.record.revision.value.toString).flatMap(actual =>
      if (actual == stage.record) Consequence.unit else N.invalid("Purpose Test interruption crosses native history"))
  private def _key_c(store: N.Store, current: InstanceRecord): Consequence[String] = {
    val prefix = _cause(store.identity, "")
    val cause = current.causation.value
    if (cause.startsWith(prefix)) Consequence.success(cause.stripPrefix(prefix))
    else N.invalid("Purpose Test interruption has no actual native cause")
  }
  private[admission] def direct(check: C.Check): Boolean = check.kind == WorkflowSbtValidation.Kind.Test && !check.ordinaryDiscovery &&
    check.setupRequired.isEmpty && check.invocation.isEmpty && check.metadata.isEmpty
  private def _check_workspace(slot: P.Slot, check: C.Check): String = slot.checkWorkspaces.getOrElse(check.identity, slot.workspace)
  private def _program(plan: Json, workspace: String): Option[String] = for {
    programs <- plan.hcursor.get[Vector[Json]]("sbtPrograms").toOption
    workspaces <- plan.hcursor.get[Vector[Json]]("workspaces").toOption
    if workspaces.count(_.hcursor.get[String]("identity").contains(workspace)) == 1
    matches = programs.filter(row => row.hcursor.get[String]("target").contains(workspace) &&
      row.hcursor.get[String]("operation").contains("test"))
    if matches.size == 1
    identity <- matches.head.hcursor.get[String]("identity").toOption.filter(_.matches("[a-z][a-z0-9-]{0,63}"))
  } yield identity

  def validTarget(target: Target): Boolean = {
    val candidate = target.candidate
    val prefix = target.successfulPrefix
    val selected = target.selectedChecks
    val slot = io.circe.parser.decode[P.Slot](target.slot).toOption
    val plan = io.circe.parser.parse(target.processPlan).toOption
    val phase = target.phase.fold("")(_ + "-")
    val attempt = "purpose-" + (target.cursor + 1) + (if (target.attempt == 0) "" else "-repair-" + target.attempt)
    val key = target.frontierCause.stripPrefix(_cause(target.sessionId, ""))
    Set(policy, legacyPolicy)(target.continuationPolicy) && Vector(target.componentInstance, target.sessionId, target.root, target.endpoint,
      target.frontierCause, candidate.groupIdentity).forall(exact) && target.context.productIterator.forall {
      case value: String => exact(value)
      case _ => false
    } && scala.util.Try(java.nio.file.Path.of(target.root).isAbsolute).getOrElse(false) &&
      target.context.syncMode == "FULL_STATE" && N.validPhase(target.phase) &&
      Vector(target.frontierRevision, target.intentRevision, target.candidateRevision).forall(revision) &&
      target.frontierRevision == target.intentRevision && target.candidateRevision.toLong < target.intentRevision.toLong &&
      target.cursor >= 0 && target.cursor < Int.MaxValue && target.attempt >= 0 && target.slotCursor >= 0 &&
      key.startsWith(phase + attempt + "-") && key.endsWith("-check-" + target.checkIndex + "-intent") &&
      target.frontierCause == _cause(target.sessionId, key) &&
      target.intentReference == ContextReference(target.sessionId + ":native-" + key, "1") &&
      target.candidateReference.identity.startsWith(target.sessionId + ":native-" + phase + attempt + "-") &&
      target.candidateReference.identity.endsWith("-prepared") && target.candidateReference.revision == "1" &&
      (Vector(candidate.parent, candidate.index, candidate.commit, candidate.tree, candidate.projectedIndex) ++
        candidate.worktreeBefore ++ candidate.worktreeAfter).forall(_oid) && candidate.paths.nonEmpty &&
      candidate.paths.distinct == candidate.paths && candidate.paths.forall(RepositorySyncNativeConflictInput.relative) &&
      selected.forall(check => exact(check.identity) && check.ordinal > 0) && selected.map(_.identity).distinct.size == selected.size &&
      selected.lift(target.checkIndex).contains(target.check) && direct(target.check) &&
      selected.take(target.checkIndex).exists(_.kind == WorkflowSbtValidation.Kind.Compile) &&
      prefix.map(_.identity) == (Vector("index-local") ++ selected.take(target.checkIndex).map(_.identity)) &&
      prefix.forall(link => revision(link.revision) && link.revision.toLong > target.candidateRevision.toLong &&
        link.revision.toLong < target.intentRevision.toLong && link.reference.revision == "1" &&
        link.reference.identity.startsWith(target.sessionId + ":native-")) &&
      prefix.map(_.revision.toLong).sliding(2).forall(pair => pair.size == 1 || pair.head < pair.last) &&
      slot.exists(value => _slot_matches(value, selected) && _check_workspace(value, target.check) == target.checkWorkspace &&
        value.asJson.noSpaces == target.slot) && WorkspaceId.parseC(target.checkWorkspace).isSuccess &&
      plan.exists(value => value.noSpaces == target.processPlan && _program(value, target.checkWorkspace).contains(target.programIdentity))
  }

  def isLegacy(target: Target): Boolean = target.continuationPolicy == legacyPolicy
  def availableChoices(target: Target): Vector[String] =
    if (isLegacy(target)) Vector(legacyChoice, "Defer") else choices
  private def _message_for(target: Target): String = if (isLegacy(target)) _legacy_message else _message

  def decodeTarget(value: Json): Either[String, Target] = value.as[Target].left.map(_.message)
  def decodeAnswer(target: Target, value: Json): Either[String, A.Answer] = for {
    answer <- value.as[A.Answer].left.map(_.message)
    _ <- Either.cond(answer.asJson == value && availableChoices(target).contains(answer.choice) && ordinary(answer.rationale) &&
      (answer.choice == "Defer" || (answer.priorExecutionEnded && answer.acceptOriginalUncertainty)), (),
      "Purpose Test interruption requires the exact direction and terminal/UNKNOWN assertions")
  } yield answer
  def bindingC(component: String, target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, ended: Boolean, accepted: Boolean): Consequence[Json] =
    if (component == target.componentInstance && validTarget(target) && participant == target.context.participantIdentity &&
        _ref(reference) && decodeAnswer(target, A.Answer(choice, rationale, ended, accepted).asJson).isRight)
      Consequence.success(Json.obj("componentInstance" -> component.asJson,
        "repositoryValidationContinuation" -> Json.obj("target" -> target.asJson, "answer" -> Json.obj(
          "choice" -> choice.asJson, "rationale" -> rationale.asJson, "participant" -> participant.asJson,
          "reference" -> reference.asJson, "priorExecutionEnded" -> ended.asJson, "acceptOriginalUncertainty" -> accepted.asJson))))
    else N.invalid("Purpose Test Human direction crosses its actual target or authority")

  def validObservation(value: Observation): Boolean = {
    val target = value.target
    val started = value.started
    val terminal = value.terminal
    val references = value.references
    val matchingpolicy = if (isLegacy(target))
      value.schemaVersion == legacyObservationSchema && value.legacyBindingReference.exists(_.revision == "1") && value.hostPlanReference.isEmpty
    else value.schemaVersion == observationSchema && value.hostPlanReference.exists(_.revision == "1") && value.legacyBindingReference.isEmpty
    matchingpolicy && validTarget(target) && exact(value.observer) &&
      value.observer != target.context.participantIdentity && references.forall(_ref) &&
      references.map(_.identity).distinct.size == references.size && started.state == "started" &&
      terminal.state == "terminal" && started.pid > 0 && started.pid == terminal.pid &&
      started.operation == "test" && terminal.operation == started.operation &&
      started.workspaceId == target.checkWorkspace && terminal.workspaceId == started.workspaceId &&
      started.programIdentity == target.programIdentity && terminal.programIdentity == started.programIdentity &&
      Set("OutputLimitExceeded(Stdout)", "OutputLimitExceeded(Stderr)")(terminal.termination) && terminal.elapsedMillis >= 0
  }
  def matchesAsRunPlan(value: Observation, recorded: AsRunPlan): Boolean =
    !isLegacy(value.target) && recorded.invocationReference == value.invocationReference && recorded.traceReference == value.traceReference &&
      recorded.pid == value.started.pid && io.circe.parser.parse(value.target.processPlan).contains(recorded.plan)
  def matchesLegacyRun(value: Observation, recorded: LegacyRun): Boolean = {
    val target = value.target
    isLegacy(target) && recorded.sessionId == target.sessionId && recorded.frontierRevision == target.frontierRevision &&
      recorded.intentReference == target.intentReference && recorded.candidateReference == target.candidateReference &&
      recorded.invocationReference == value.invocationReference && recorded.traceReference == value.traceReference &&
      recorded.operation == "advanceRepositorySync" && recorded.cliExitCode != 0 && recorded.pid == value.started.pid &&
      recorded.originalPlanStatus == originalPlanNotRetained
  }

  // The prefix is part of the CNCF SBT progress reporter's JSON-line format.
  private val _trace_frame = "[cncf-sbt-event] "
  private val _trace_parser = io.circe.jawn.JawnParser(allowDuplicateKeys = false)
  def matchesLegacyTrace(value: Observation, trace: String): Boolean = {
    val events = trace.linesIterator.filter(_.startsWith(_trace_frame)).foldLeft(
      Right(Vector.empty[(String, Json)]): Either[io.circe.Error, Vector[(String, Json)]]) { (prior, line) => for {
        selected <- prior
        event <- _trace_parser.parse(line.stripPrefix(_trace_frame))
        cursor = event.hcursor
        operation <- cursor.get[String]("operation")
        workspace <- cursor.get[String]("workspaceId")
        program <- cursor.get[String]("programIdentity")
        state <- cursor.get[String]("state")
      } yield if (operation == value.started.operation && workspace == value.started.workspaceId &&
          program == value.started.programIdentity)
        selected :+ (state -> event) else selected }
    isLegacy(value.target) && value.started.pid > 0 && events.exists { selected =>
      val lifecycle = selected.headOption match {
        case Some(("launching", event)) if !event.hcursor.downField("pid").succeeded => selected.tail
        case _ => selected
      }
      val pid = Json.fromLong(value.started.pid)
      lifecycle.headOption.contains("started" -> value.started.asJson) &&
        lifecycle.lastOption.contains("terminal" -> value.terminal.asJson) &&
        lifecycle.slice(1, lifecycle.size - 1).forall { case (state, event) =>
          state == "waiting" && event.hcursor.downField("pid").focus.contains(pid)
        }
    }
  }
  def observationBinding(value: Observation): Json = Json.obj("componentInstance" -> value.target.componentInstance.asJson,
    "purposeTestTermination" -> value.asJson)
  def selectObservation(value: Json): Either[String, Observation] = for {
    observation <- value.hcursor.get[Observation]("purposeTestTermination").left.map(_.message)
    _ <- Either.cond(observationBinding(observation) == value, (), "Purpose Test termination binding fields differ")
  } yield observation
  private def _valid_direction(value: Direction): Boolean = validObservation(value.observation) &&
    value.choice == (if (isLegacy(value.target)) legacyChoice else choice) && ordinary(value.rationale) && _ref(value.humanReference) &&
    value.observation.references.forall(_distinct(value.humanReference, _)) && value.originalEffects == "UNKNOWN"

  private def _slot_matches(slot: P.Slot, checks: Vector[C.Check]): Boolean =
    P.validatePolicyC(P.Policy(Vector(slot), Vector.empty)).isSuccess && checks.forall(check =>
      slot.checks.exists(fixed => fixed.identity == check.identity && fixed.ordinal == check.ordinal && fixed.kind == check.kind))
  private def _component(core: ActionCall.Core): String = core.component.get.core.instanceId.canonicalKey
  private def _candidate(progress: P.Progress): Option[Candidate] = for {
    candidate <- progress.candidate
    group <- progress.plan.flatMap(_.groups.lift(progress.cursor))
    effective = RepositorySyncPurposeOwnership.effectiveGroup(progress, group)
    if candidate.changes == effective.content
  } yield Candidate(effective.identity, effective.content.map(_.path), candidate.parent, candidate.index,
    candidate.commit, candidate.tree, candidate.projectedIndex, candidate.worktreeBefore, candidate.worktreeAfter)

  private def _same_attempt(retained: P.Progress, selected: P.Progress): Boolean =
    retained.candidate == selected.candidate && retained.request == selected.request && retained.plan == selected.plan &&
      retained.cursor == selected.cursor && retained.attempt == selected.attempt && retained.slotCursor == selected.slotCursor &&
      retained.ownership == selected.ownership && retained.preparationNamespace == selected.preparationNamespace

  private def _prefix_links_c(store: N.Store, current: InstanceRecord, progress: P.Progress,
    through: Int): Consequence[Vector[ReceiptLink]] = {
    val selected = progress.selection.get.checks
    val receipts = progress.receipts.take(through)
    val attempt = RepositorySyncPurposeRuntime.attemptPrefix(progress)
    val inherited = progress.hostContinuation.filter(_.target.purpose.exists(_same_attempt(_, progress)))
    for {
      links <- inherited.fold(Consequence.success(Vector.empty[ReceiptLink])) { direction => for {
        retained <- RepositorySyncFormalFailureContinuation.selectedC(store, current)
        _ <- if (retained.exists(_.direction == direction) && RepositorySyncFormalFailureContinuation.validPurposeAttempt(progress, direction) &&
            direction.target.successfulPrefix.size <= through) Consequence.unit
          else N.invalid("Purpose Test prefix crosses its authenticated former attempt")
        previous <- prefixC(current, direction.target.intentRevision)
        values <- _prefix_links_c(store, previous, direction.target.purpose.get, direction.target.successfulPrefix.size)
      } yield values }
      result <- (links.size until through).foldLeft(Consequence.success(links)) { (prior, position) => for {
        values <- prior
        receipt = receipts(position)
        resultkey = if (position == 0) attempt + "-structural" else if (receipt.group.nonEmpty)
          attempt + "-ordinary-" + (position - 1) + "-complete" else attempt + "-check-" + (position - 1) + "-result"
        saved <- _load_c(store, resultkey)
        _ <- _history_c(current, saved)
        _ <- if (saved.payload.stage == "PurposeMaterialized" && saved.payload.purposes.exists(p =>
            _same_attempt(p, progress) && p.selection == progress.selection && p.receipts == receipts.take(position + 1)))
          Consequence.unit else N.invalid("Purpose Test prefix crosses an actual successful result")
        intentkey = if (position == 0) attempt + "-structural-intent" else attempt + "-check-" + (position - 1) + "-intent"
        _ <- if (receipt.group.nonEmpty) Consequence.unit else for {
          intent <- _load_c(store, intentkey)
          _ <- _history_c(current, intent)
          _ <- if (intent.payload.stage == "PurposeChecking" && saved.record.revision == intent.record.revision.next &&
              saved.record.history.dropRight(1) == intent.record.history && intent.payload.purposes.exists(p =>
                _same_attempt(p, progress) && p.selection == progress.selection && p.receipts == receipts.take(position)))
            Consequence.unit else N.invalid("Purpose Test prefix result is not adjacent to its selected check intent")
        } yield ()
      } yield values :+ ReceiptLink(receipt.identity, _native_reference(store, resultkey), saved.record.revision.value.toString) }
      _ <- if (RepositorySyncFormalFailureContinuation.validPrefix(receipts, selected, merge = false) &&
          receipts.headOption.exists(_.exitCode.contains(0)) && receipts.drop(1).forall(r => r.sbt.nonEmpty || r.group.nonEmpty))
        Consequence.unit else N.invalid("Purpose Test requires the original successful index-local and typed product prefix")
    } yield result
  }

  private def _source_c(store: N.Store, current: InstanceRecord, selectedpolicy: String): Consequence[Source] = for {
    _ <- if (_active(current, "PurposeChecking")) Consequence.unit
      else N.invalid("Purpose Test interruption requires its active unsuspended retained check intent")
    key <- _key_c(store, current)
    intent <- _load_c(store, key)
    progress <- intent.payload.purposes.map(Consequence.success).getOrElse(N.invalid("Purpose Test intent has no retained progress"))
    selection <- progress.selection.map(Consequence.success).getOrElse(N.invalid("Purpose Test intent has no ordered selection"))
    position = progress.receipts.size - 1
    base = RepositorySyncPurposeRuntime.attemptPrefix(progress) + "-check-" + position
    selected <- selection.checks.lift(position).filter(direct).map(Consequence.success)
      .getOrElse(N.invalid("Only the exact resultless direct Purpose Test check is eligible"))
    _ <- if (intent.record == current && intent.payload.stage == "PurposeChecking" && key == store.key(base + "-intent") &&
        intent.payload.mergeHead.isEmpty && intent.payload.validation.isEmpty && progress.interruption.isEmpty &&
        progress.assessment.isEmpty && progress.review.isEmpty && !progress.reusable.exists(_.identity == selected.identity) &&
        !selection.reused.exists(_.check == selected.identity) && !progress.ordinaryGroups.contains(selected.identity)) Consequence.unit
      else N.invalid("Purpose Test intent is crossed, already settled or outside its retained direct selection")
    _ <- if (selectedpolicy != legacyPolicy || intent.payload.purposeTestEvidenceRequirement.isEmpty) Consequence.unit
      else N.invalid("This Purpose Test intent requires strict as-run plan evidence")
    result <- store.loadC(base + "-result")
    _ <- if (result.isEmpty) Consequence.unit else N.invalid("Purpose Test already has an actual result")
    preparedkey = RepositorySyncPurposeRuntime.allocationIdentity(progress) + "-prepared"
    prepared <- _load_c(store, preparedkey)
    _ <- _history_c(current, prepared)
    candidate <- _candidate(progress).map(Consequence.success).getOrElse(N.invalid("Purpose Test has no exact candidate and group"))
    _ <- if (prepared.payload.stage == "PurposePrepared" && prepared.payload.mergeHead.isEmpty &&
        prepared.payload.purposes.exists(_same_attempt(_, progress)) &&
        prepared.payload.before == intent.payload.before && store.original.repositoryRoot == store.declaration.target.root.toString &&
        store.original.repositoryRoot == progress.request.root && store.context.repositoryIdentity == progress.request.repository &&
        store.context.localBranch == progress.request.branch && store.context.localBranch == store.declaration.target.localBranch)
      Consequence.unit else N.invalid("Purpose Test crosses its original non-pending source or prepared candidate")
    links <- _prefix_links_c(store, current, progress, progress.receipts.size)
    before <- prefixC(current, (current.revision.value - 1).toString)
    beforekey <- _key_c(store, before)
    prior <- _load_c(store, beforekey)
    _ <- if (_active(before, "PurposeMaterialized") && prior.record == before &&
        current.history.dropRight(1) == before.history && prior.payload == intent.payload.copy(
          stage = "PurposeMaterialized", purposeTestEvidenceRequirement = None))
      Consequence.unit else N.invalid("Purpose Test intent is not adjacent to its authentic materialized successful prefix")
    slot <- P.slot(store.declaration.purposePolicy, store.context.repositoryIdentity, progress)
      .filter(_slot_matches(_, selection.checks)).map(Consequence.success).getOrElse(N.invalid("Purpose Test has no exact slot"))
    workspace = _check_workspace(slot, selected)
    plan <- RepositorySyncPurposeMaterializationContinuation.hostPlanC(store.core)
    program <- _program(plan, workspace).map(Consequence.success).getOrElse(N.invalid("Purpose Test has no unique resolved safe Test program"))
    target = Target(selectedpolicy, _component(store.core), store.identity, store.context, store.original.repositoryRoot,
      store.declaration.target.endpoint, current.revision.value.toString, current.causation.value, store.phase, progress.cursor,
      progress.attempt, progress.slotCursor, _native_reference(store, preparedkey), prepared.record.revision.value.toString,
      candidate, _native_reference(store, base + "-intent"), current.revision.value.toString, selection.checks,
      position, selected, links, slot.asJson.noSpaces, workspace, program, plan.noSpaces)
    _ <- if (validTarget(target)) Consequence.unit else N.invalid("Purpose Test target cannot be represented by the exact contract")
  } yield Source(intent, target)

  private def _adoption_key(target: Target): String = "purpose-test-interruption-" + target.frontierRevision + "-adopted"
  def repairReference(target: Target): ContextReference =
    ContextReference(target.sessionId + ":native-" + N.phaseKey(target.phase, "", _adoption_key(target)), "1")
  private def _adopted_c(store: N.Store, current: InstanceRecord, saved: N.Stage): Consequence[Direction] = for {
    direction <- saved.payload.purposes.flatMap(_.interruption).map(Consequence.success)
      .getOrElse(N.invalid("Purpose Test repair has no retained interruption direction"))
    target = direction.target
    before <- prefixC(current, target.frontierRevision)
    _ <- _history_c(current, saved)
    original <- _source_c(store, before, target.continuationPolicy)
    _ <- if (_valid_direction(direction) && target == original.target && _active(saved.record, "PurposeRepairRequired") &&
        saved.record.causation.value == _cause(store.identity, store.key(_adoption_key(target))) &&
        saved.record.revision == before.revision.next && saved.record.history.dropRight(1) == before.history &&
        saved.payload == _adopted_payload(original.stage, direction)) Consequence.unit
      else N.invalid("Purpose Test repair crosses its adjacent native direction, host or unchanged candidate")
  } yield direction

  private def _adopted_payload(intent: N.Stage, direction: Direction): N.Payload =
    intent.payload.copy(stage = "PurposeRepairRequired", purposeTestEvidenceRequirement = None,
      purposes = intent.payload.purposes.map(_.copy(interruption = Some(direction))))

  def describeC(session: F.Session, declaration: F.Declaration, core: ActionCall.Core,
    acknowledge: String => Consequence[Unit], selectedPolicy: String = policy): Consequence[V.RepositorySyncValidationContinuationDescriptionResult] = for {
    store <- N.bindC(session, declaration, core, acknowledge)
    source <- _source_c(store, session.started.current, selectedPolicy)
  } yield V.RepositorySyncValidationContinuationDescriptionResult(store.identity, "adoption-required", _message_for(source.target),
    source.target.componentInstance, GoalPhaseNativeMetadata.record(source.target.asJson), availableChoices(source.target), A.adoptionOperation)

  def describeAdoptedC(store: N.Store, current: InstanceRecord, source: N.Stage,
    selectedPolicy: Option[String] = None): Consequence[V.RepositorySyncValidationContinuationDescriptionResult] = for {
    direction <- _adopted_c(store, current, source)
    target = direction.target
    _ <- if (selectedPolicy.forall(_ == target.continuationPolicy)) Consequence.unit
      else N.invalid("Purpose Test evidence policy differs from the retained adopted direction")
  } yield V.RepositorySyncValidationContinuationDescriptionResult(store.identity, "already-adopted", _message_for(target),
    target.componentInstance, GoalPhaseNativeMetadata.record(target.asJson), availableChoices(target), A.adoptionOperation)

  def adoptC(input: V.RepositorySyncValidationContinuationAdoptionInput, target: Target, declaration: F.Declaration,
    definition: DefinitionBinding, core: ActionCall.Core, installation: A.Installation,
    acknowledge: String => Consequence[Unit]): Consequence[V.RepositorySyncValidationContinuationAdoptionResult] = for {
    session <- F.sessionC(input.sessionId, declaration, definition, core)
    store <- N.bindC(session, declaration, core, acknowledge)
    current = session.started.current
    human = ContextReference(input.reference.identity, input.reference.revision)
    references = input.executionEvidence.references.map(reference => ContextReference(reference.identity, reference.revision))
    _ <- if (input.sessionId == target.sessionId && input.executionEvidence.workerIdentity.contains(store.context.participantIdentity) &&
        references.forall(_ref) && references.map(_.identity).distinct == references.map(_.identity) &&
        references.contains(human) && references.size == (if (input.choice == "Defer") 1 else 2)) Consequence.unit
      else N.invalid("Purpose Test requires the exact Human reference and a distinct observation reference for repair")
    binding <- bindingC(_component(core), target, input.choice, input.rationale, store.context.participantIdentity,
      human, input.priorExecutionEnded, input.acceptOriginalUncertainty)
    _ <- installation.evidence.admitC(core, human, binding)
    previous <- store.loadC(_adoption_key(target))
    outcome <- if (input.choice == "Defer" && previous.isEmpty) for {
      source <- _source_c(store, current, target.continuationPolicy)
      _ <- if (source.target == target) Consequence.unit else N.invalid("Purpose Test target drifted; describe again")
    } yield "deferred" else if (input.choice == "Defer") N.invalid("Purpose Test direction already adopted")
    else for {
      observationref <- references.find(_ != human).map(Consequence.success).getOrElse(N.invalid("Purpose Test observation is absent"))
      observed <- installation.evidence.purposeTestTerminationC(core, observationref, target)
      observation <- observed.filter(value => value.reference == observationref && value.target == target && validObservation(value))
        .map(Consequence.success).getOrElse(Consequence.securityPermissionDenied("Purpose Test has no independently confirmed termination for this exact intent"))
      direction = Direction(observation, input.choice, input.rationale, human, "UNKNOWN")
      _ <- if (_valid_direction(direction)) Consequence.unit else N.invalid("Purpose Test direction and observation references cross")
      selected <- previous match {
        case Some(saved) => for {
          retained <- _adopted_c(store, current, saved)
          _ <- if (retained == direction) Consequence.unit else N.invalid("Purpose Test repeated direction is conflicting")
        } yield "already-adopted"
        case None => for {
          source <- _source_c(store, current, target.continuationPolicy)
          _ <- if (source.target == target) Consequence.unit else N.invalid("Purpose Test target, host or native frontier drifted; describe again")
          _ <- store.saveC(_adoption_key(target), _event, current, _adopted_payload(source.stage, direction))
        } yield "adopted"
      }
    } yield selected
  } yield V.RepositorySyncValidationContinuationAdoptionResult(input.sessionId, outcome != "deferred", outcome, _message_for(target))

  def requireRepairC(store: N.Store, current: InstanceRecord, source: N.Stage, validation: C.Policy): Consequence[Direction] = for {
    direction <- _adopted_c(store, current, source)
    snapshot <- RepositorySyncPurposeRuntime.repairSnapshotC(store, source)
    progress = snapshot.progress
    target = direction.target
    _ <- if (matchesProgress(progress, direction) && snapshot.indexTree == target.candidate.index) Consequence.unit
      else N.invalid("Purpose Test repair source HEAD, index, inventory or candidate drifted")
    _ <- _require_current_selection_c(store, progress, validation)
    slot <- P.slot(store.declaration.purposePolicy, store.context.repositoryIdentity, progress).map(Consequence.success)
      .getOrElse(N.invalid("Purpose Test repair slot is absent"))
    _ <- if (target.checkWorkspace == slot.workspace) Consequence.unit else for {
      scoped <- RepositorySyncPurposeRuntime.candidateCoreC(store, progress)
      actual <- WorkflowManagedProcessExecution.executeGitC(scoped, GitOperation.Root, target.checkWorkspace).flatMap(_.valueC)
      expected <- WorkflowManagedProcessExecution.executeGitC(scoped, GitOperation.Root, slot.workspace).flatMap(_.valueC)
      _ <- if (actual == expected) Consequence.unit else N.invalid("Purpose Test workspace no longer consumes its exact candidate")
    } yield ()
  } yield direction

  private def _require_current_selection_c(store: N.Store, progress: P.Progress, validation: C.Policy): Consequence[Unit] = for {
    _ <- validation.validateC
    selected <- progress.selection.map(Consequence.success).getOrElse(N.invalid("Purpose Test retained selection is absent"))
    matches <- validation.impact match {
      case None => Consequence.success(validation.checks == selected.checks)
      case Some(impact) => for {
        candidate <- progress.candidate.map(Consequence.success).getOrElse(N.invalid("Purpose Test retained candidate is absent"))
        parent <- GitObjectId.parseC(candidate.parent)
        tree <- GitObjectId.parseC(candidate.tree)
        changes <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.TreeChanges(parent, tree)).flatMap(_.valueC)
        paths = changes.map(_.path.value)
        documents = store.declaration.documentInputs
        consumed = paths.filter(path => documents.exists(inputs => RepositorySyncProgramImpact.covers(inputs.consumedPaths, path)))
        unknown = paths.filterNot(path => documents.exists(inputs => RepositorySyncProgramImpact.covers(inputs.consumedPaths, path) ||
          RepositorySyncProgramImpact.covers(inputs.documentPaths, path)))
        inputs = RepositorySyncDocumentInputs.Assessment(paths, consumed, unknown, Vector.empty, "Actual Purpose repair candidate paths")
        current = RepositorySyncProgramImpact.select(impact, inputs)
      } yield current.depth == selected.depth && current.checks == selected.checks
    }
    _ <- if (matches) Consequence.unit else N.invalid("Purpose Test repair current impact branch or ordered selection differs")
  } yield ()

  def matchesProgress(progress: P.Progress, direction: Direction): Boolean = {
    val target = direction.target
    _valid_direction(direction) && _candidate(progress).contains(target.candidate) && progress.cursor == target.cursor &&
      progress.attempt == target.attempt && progress.slotCursor == target.slotCursor &&
      progress.selection.exists(_.checks == target.selectedChecks) && progress.receipts.map(_.identity) == target.successfulPrefix.map(_.identity) &&
      RepositorySyncFormalFailureContinuation.validPrefix(progress.receipts, target.selectedChecks, merge = false) &&
      progress.receipts.headOption.exists(_.exitCode.contains(0))
  }
  def proof(direction: Direction): Proof = {
    val target = direction.target
    Proof(target.intentReference, target.frontierRevision, direction.observation.reference, target.candidate.commit,
      target.candidate.parent, target.candidate.tree, target.check.identity, target.selectedChecks.map(_.identity),
      target.successfulPrefix.drop(1).map(_.identity), direction.originalEffects,
      Option.when(isLegacy(target))(originalPlanNotRetained))
  }
  private def _valid_proof(value: Proof): Boolean = _ref(value.intent) && _ref(value.observation) &&
    revision(value.frontierRevision) && _distinct(value.intent, value.observation) &&
    Vector(value.candidateCommit, value.parent, value.candidateTree).forall(_oid) && exact(value.check) &&
    value.selectedChecks.forall(exact) && value.selectedChecks.distinct == value.selectedChecks &&
    value.successfulPrefix.nonEmpty && value.selectedChecks.startsWith(value.successfulPrefix :+ value.check) &&
    value.originalEffects == "UNKNOWN" && value.originalPlanStatus.forall(_ == originalPlanNotRetained)
  def repairProofValid(review: RepositorySyncNativeMergeReview.Request, identity: String): Boolean =
    review.interruption.exists(value => _valid_proof(value) && value.check == identity &&
      value.intent.identity.startsWith(review.instanceIdentity + ":native-") && value.intent.revision == "1" &&
      review.validatedRevision.toLongOption.contains(value.frontierRevision.toLong + 1) &&
      review.localTip == value.parent && review.remoteTip == value.parent && review.indexTree == value.candidateTree &&
      review.selectedChecks == value.selectedChecks && review.checks.map(_.identity) == value.successfulPrefix &&
      review.checks.forall(receipt => C.productSuccessful(receipt, receipt.sbt.map(_.kind).getOrElse(WorkflowSbtValidation.Kind.TestOnly))))
}
