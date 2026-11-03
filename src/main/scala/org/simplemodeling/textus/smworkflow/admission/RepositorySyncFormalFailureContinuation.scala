package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as _, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{TypedValue, WorkflowContinuation}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedFetch as F
import RepositorySyncManagedGitObservation as G
import RepositorySyncNativeProgressStore as N
import RepositorySyncNativeMergeValidation as C
import RepositorySyncPurpose as P
import RepositorySyncPurpose.given
import RepositorySyncPurposeContentContinuation.{canonical, exact, ordinary, revision, prefixC}

/** A genuine new validation attempt after explicit host-correction direction.
  * Formal failure and unknown old-worker effects retain separate provenance.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
private[smworkflow] object RepositorySyncFormalFailureContinuation {
  val policy = "sm-workflow.repository-sync-formal-failure-continuation.v1"
  val selectionPolicy = "sm-workflow.repository-sync-formal-failure-continuation.v2"
  val choices = Vector("AuthorizeNewValidationAfterHostCorrection", "Defer")
  val selectionChoices = Vector("AuthorizeNewValidationAfterSelectionCorrection", "Defer")
  def availableChoices(target: Target): Vector[String] =
    if (target.continuationPolicy == selectionPolicy) selectionChoices else choices
  val cause = "native-formal-failure-adopted:"
  val message = "The original product check formally failed. Authorize a genuine new attempt on the same candidate " +
    "under the described current resolved host configuration. No historical host configuration difference is asserted. " +
    "Successful checks are retained; the failed receipt remains immutable. Any old semantic worker must independently " +
    "have ended and its UNKNOWN effects must be accepted. Adoption stores direction; separate ordinary Advance executes."
  def targetMessage(target: Target): String = message + (if (target.continuationPolicy == selectionPolicy)
    " The described selection correction appends the exact registered TestOnly checks; separate Advance must obtain actual nonempty selected-test evidence and normal independent review."
    else "")
  final case class Pending(identity: String, expectedRevision: String, entityRevision: String,
    status: String, claim: Option[String], input: Json)
  final case class Target(componentInstance: String, sessionId: String, context: F.StartContext,
    root: String, endpoint: String, frontierRevision: String, frontierCause: String,
    sourceNamespace: String, candidateKey: String, candidateRevision: String,
    failureKey: String, failureRevision: String, intentKey: String, intentRevision: String,
    candidateReference: ContextReference, failureReference: ContextReference, intentReference: ContextReference,
    checkIndex: Int, check: C.Check, selectedChecks: Vector[C.Check], successfulPrefix: Vector[C.Receipt],
    failed: C.Receipt, validationPolicy: C.Policy, mergeState: Option[C.State],
    purpose: Option[P.Progress], purposeSlot: Option[P.Slot], actual: G.Native, indexTree: String,
    processPlan: Json, pending: Option[Pending], continuationPolicy: String,
    proposedChecks: Option[Vector[C.Check]] = None)
  object Target {
    // Progress can contain a prior host direction. Defer encoder resolution
    // until use rather than eagerly initializing that recursive codec graph.
    given Encoder[Target] = Encoder.instance(value => {
      val wire = canonical(Json.obj(
        "componentInstance" -> value.componentInstance.asJson,
        "sessionId" -> value.sessionId.asJson,
        "context" -> value.context.asJson,
        "root" -> value.root.asJson,
        "endpoint" -> value.endpoint.asJson,
        "frontierRevision" -> value.frontierRevision.asJson,
        "frontierCause" -> value.frontierCause.asJson,
        "sourceNamespace" -> value.sourceNamespace.asJson,
        "candidateKey" -> value.candidateKey.asJson,
        "candidateRevision" -> value.candidateRevision.asJson,
        "failureKey" -> value.failureKey.asJson,
        "failureRevision" -> value.failureRevision.asJson,
        "intentKey" -> value.intentKey.asJson,
        "intentRevision" -> value.intentRevision.asJson,
        "candidateReference" -> value.candidateReference.asJson,
        "failureReference" -> value.failureReference.asJson,
        "intentReference" -> value.intentReference.asJson,
        "checkIndex" -> value.checkIndex.asJson,
        "check" -> value.check.asJson,
        "selectedChecks" -> value.selectedChecks.asJson,
        "successfulPrefix" -> value.successfulPrefix.asJson,
        "failed" -> value.failed.asJson,
        "validationPolicy" -> value.validationPolicy.asJson,
        "mergeState" -> value.mergeState.asJson,
        "purpose" -> value.purpose.asJson,
        "purposeSlot" -> value.purposeSlot.asJson,
        "actual" -> value.actual.asJson,
        "indexTree" -> value.indexTree.asJson,
        "processPlan" -> value.processPlan,
        "pending" -> value.pending.asJson,
        "continuationPolicy" -> value.continuationPolicy.asJson,
        "proposedChecks" -> value.proposedChecks.asJson
      )).mapObject(_.add("processPlan", value.processPlan))
      value.purpose.fold(wire)(progress => wire.mapObject(_.add("purpose", _progress_json(progress))))
    })
    private def _raw: Decoder[Target] = Decoder.instance(c => for {
      componentinstance <- c.get[String]("componentInstance")
      sessionid <- c.get[String]("sessionId")
      context <- c.get[F.StartContext]("context")
      root <- c.get[String]("root")
      endpoint <- c.get[String]("endpoint")
      frontierrevision <- c.get[String]("frontierRevision")
      frontiercause <- c.get[String]("frontierCause")
      sourcenamespace <- c.get[String]("sourceNamespace")
      candidatekey <- c.get[String]("candidateKey")
      candidaterevision <- c.get[String]("candidateRevision")
      failurekey <- c.get[String]("failureKey")
      failurerevision <- c.get[String]("failureRevision")
      intentkey <- c.get[String]("intentKey")
      intentrevision <- c.get[String]("intentRevision")
      candidatereference <- c.get[ContextReference]("candidateReference")
      failurereference <- c.get[ContextReference]("failureReference")
      intentreference <- c.get[ContextReference]("intentReference")
      checkindex <- c.get[Int]("checkIndex")
      check <- c.get[C.Check]("check")
      selectedchecks <- c.get[Vector[C.Check]]("selectedChecks")
      successfulprefix <- c.get[Vector[C.Receipt]]("successfulPrefix")
      failed <- c.get[C.Receipt]("failed")
      validationpolicy <- c.get[C.Policy]("validationPolicy")
      mergestate <- c.get[Option[C.State]]("mergeState")
      purpose <- c.get[Option[P.Progress]]("purpose")
      purposeslot <- c.get[Option[P.Slot]]("purposeSlot")
      actual <- c.get[G.Native]("actual")
      indextree <- c.get[String]("indexTree")
      processplan <- c.get[Json]("processPlan")
      pending <- c.get[Option[Pending]]("pending")
      continuationpolicy <- c.get[String]("continuationPolicy")
      proposedchecks <- c.get[Option[Vector[C.Check]]]("proposedChecks")
    } yield Target(componentinstance, sessionid, context, root, endpoint, frontierrevision, frontiercause,
      sourcenamespace, candidatekey, candidaterevision, failurekey, failurerevision, intentkey, intentrevision,
      candidatereference, failurereference, intentreference, checkindex, check, selectedchecks, successfulprefix,
      failed, validationpolicy, mergestate, purpose, purposeslot, actual, indextree, processplan, pending, continuationpolicy, proposedchecks))
    given Decoder[Target] = Decoder.instance(c => for {
      restored <- _restore_public_json(c.value).left.map(reason => DecodingFailure(reason, c.history))
      value <- _raw.decodeJson(restored)
      result <- if (validTarget(value) && value.asJson == restored) Right(value)
        else Left(DecodingFailure("Formal failure target requires its exact canonical contract", c.history))
    } yield result)
  }
  /** Keep opaque host-map keys outside the public Record naming projection. */
  private[admission] def publicRecord(value: Target): Record =
    GoalPhaseNativeMetadata.record(_public_json(value.asJson))
  private def _public_json(value: Json): Json = value.arrayOrObject(value,
    values => Json.fromValues(values.map(_public_json)),
    fields => Json.fromFields(fields.toVector.map { (key, nested) =>
      key -> (if (key == "processPlan" && nested.isObject) Json.fromString(nested.noSpaces) else _public_json(nested))
    }))
  private def _restore_public_json(value: Json): Either[String, Json] = value.arrayOrObject[Either[String, Json]](Right(value),
    values => values.foldLeft[Either[String, Vector[Json]]](Right(Vector.empty)) { (prior, nested) => for {
      accumulated <- prior
      restored <- _restore_public_json(nested)
    } yield accumulated :+ restored }.map(Json.fromValues),
    fields => fields.toVector.foldLeft[Either[String, Vector[(String, Json)]]](Right(Vector.empty)) {
      case (prior, (key, nested)) => for {
        accumulated <- prior
        restored <- if (key == "processPlan") _restore_process_plan(nested) else _restore_public_json(nested)
      } yield accumulated :+ (key -> restored)
    }.map(Json.fromFields))
  private def _restore_process_plan(value: Json): Either[String, Json] =
    if (value.isObject) Right(value) else for {
      text <- value.asString.toRight("Formal failure processPlan requires an opaque JSON object or its complete JSON text")
      parsed <- io.circe.parser.parse(text).left.map(_.message)
      plan <- Either.cond(parsed.isObject, parsed, "Formal failure processPlan text must restore an exact JSON object")
    } yield plan
  private def _progress_json(value: P.Progress): Json = {
    val wire = canonical(value.asJson).mapObject(_.add("request", RepositorySyncPurposeContentContinuation.requestJson(value.request))
      .add("effects", value.effects.asJson))
    value.hostContinuation.fold(wire)(direction => wire.mapObject(_.add("hostContinuation", direction.asJson)))
  }
  final case class Direction(target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, priorExecutionEnded: Boolean, acceptOriginalUncertainty: Boolean)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    private val _raw: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = Decoder.instance(c => _raw(c).flatMap(value =>
      if (value.asJson == c.value && value.choice == availableChoices(value.target).head && bindingC(value.target.componentInstance,
          value.target, value.choice, value.rationale, value.participant, value.reference,
          value.priorExecutionEnded, value.acceptOriginalUncertainty).isSuccess) Right(value)
      else Left(DecodingFailure("Formal failure direction is invalid", c.history))))
  }
  final case class Checkpoint(direction: Direction, originalWorkerEffects: String)
  private val _checkpoint = new WorkflowResultJsonV1.PayloadCodec[Checkpoint] {
    val typeIdentity = "RepositorySyncFormalFailureContinuationCheckpoint"
    def encode(value: Checkpoint): Json = value.asJson
    def decode(value: Json): Either[String, Checkpoint] = value.as[Checkpoint].left.map(_.message).flatMap(v =>
      Either.cond(v.originalWorkerEffects == "UNKNOWN" && v.asJson == value, v, "Old worker effects remain UNKNOWN"))
  }
  final case class Saved(direction: Direction, revision: InstanceRevision)
  private final case class Source(store: N.Store, candidate: N.Stage, failed: N.Stage, intent: N.Stage,
    checks: Vector[C.Check], check: C.Check, index: Int, prefix: Vector[C.Receipt], receipt: C.Receipt,
    namespace: String, slot: Option[P.Slot])
  private final case class Observation(session: F.Session, source: Source, target: Target,
    entityRevision: Option[org.simplemodeling.model.datatype.EntityRevision])
  def namespace(target: Target): String = "formal-failure-" + target.frontierRevision
  def adoptionKey(target: Target): String = namespace(target) + "-adopted"
  private def _key(stage: N.Stage, identity: String): String = stage.record.causation.value
    .stripPrefix("native-repository:" + identity + ":")
  private def _reference(identity: String, key: String): ContextReference = ContextReference(identity + ":native-" + key, "1")
  def completeFailure(receipt: C.Receipt, check: C.Check): Boolean = receipt != null && check != null &&
    receipt.identity == check.identity && receipt.termination == "exited" && receipt.exitCode.exists(_ != 0) &&
    C.storedProductOutputValid(receipt) && receipt.sbt.forall(f => f.valid && f.kind == check.kind) &&
    (receipt.sbt.nonEmpty || check.kind == WorkflowSbtValidation.Kind.Test) &&
    receipt.processObservation.forall(p => !p.stdout.truncated && !p.stderr.truncated &&
      WorkflowSbtCapture.text(p.stdout).textStatus == "complete" && WorkflowSbtCapture.text(p.stderr).textStatus == "complete")
  def validPrefix(receipts: Vector[C.Receipt], checks: Vector[C.Check], merge: Boolean): Boolean = {
    val structural = if (merge) Vector("index-local", "index-remote") else Vector("index-local")
    receipts != null && checks != null && receipts.size >= structural.size && receipts.size <= checks.size + structural.size &&
      receipts.map(_.identity) == (structural ++ checks.map(_.identity)).take(receipts.size) &&
      C.successful(receipts, merge, checks)
  }
  def validTarget(t: Target): Boolean = t != null && exact(t.componentInstance) && exact(t.sessionId) &&
    t.context != null && t.context.productIterator.forall(v => v.isInstanceOf[String] && exact(v.asInstanceOf[String])) &&
    exact(t.root) && scala.util.Try(java.nio.file.Path.of(t.root).isAbsolute).getOrElse(false) &&
    exact(t.endpoint) && exact(t.frontierCause) &&
    Vector(t.frontierRevision, t.candidateRevision, t.failureRevision, t.intentRevision).forall(revision) &&
    t.failureRevision.toLong == t.intentRevision.toLong + 1 && t.candidateRevision.toLong < t.intentRevision.toLong &&
    t.failureRevision.toLong <= t.frontierRevision.toLong && t.sourceNamespace != null &&
    (t.sourceNamespace.isEmpty || t.sourceNamespace.matches("(?:repair-|required-lint-|formal-failure-)[1-9][0-9]*")) &&
    Vector(t.candidateKey, t.failureKey, t.intentKey).forall(exact) &&
    t.candidateReference == _reference(t.sessionId, t.candidateKey) && t.failureReference == _reference(t.sessionId, t.failureKey) &&
    t.intentReference == _reference(t.sessionId, t.intentKey) && t.selectedChecks != null && t.checkIndex >= 0 &&
    t.selectedChecks.lift(t.checkIndex).contains(t.check) && completeFailure(t.failed, t.check) &&
    t.successfulPrefix != null && t.successfulPrefix.size == t.checkIndex + (if (t.purpose.nonEmpty) 1 else 2) &&
    validPrefix(t.successfulPrefix, t.selectedChecks, t.purpose.isEmpty) && t.validationPolicy != null &&
    t.validationPolicy.validateC.isSuccess && t.mergeState != null && t.purpose != null && t.purposeSlot != null &&
    (if (t.purpose.nonEmpty) t.mergeState.isEmpty && t.purposeSlot.nonEmpty && t.purpose.exists(p =>
      p.candidate.nonEmpty && p.selection.exists(_.checks == t.selectedChecks) && p.receipts == t.successfulPrefix :+ t.failed)
      else t.purposeSlot.isEmpty && t.mergeState.exists(state => state.receipts == t.successfulPrefix :+ t.failed &&
        state.selected == t.selectedChecks && state.sourceRevision == t.candidateRevision && C.applies(state, t.validationPolicy))) &&
    t.actual != null && t.actual.observed != null && t.actual.observed.branch == t.context.localBranch &&
    t.indexTree != null && t.indexTree.matches("[0-9a-f]{40}|[0-9a-f]{64}") &&
    (t.purpose.nonEmpty || t.actual.mergeHead.nonEmpty) && t.processPlan != null && t.processPlan.asObject.exists(_.nonEmpty) &&
    _valid_source_keys(t) && t.pending != null && t.pending.forall(p => p != null && exact(p.identity) && p.expectedRevision == t.frontierRevision &&
      p.entityRevision == "1" && p.status == "Available" && p.claim != null && p.claim.isEmpty && p.input != null &&
      RepositorySyncNativeMergeRepair.inputCodec.decode(p.input).isRight) && _valid_selection(t)
  /** The current fixed policy and slot are the sole source of additions. */
  private[admission] def proposedChecks(checks: Vector[C.Check], validation: C.Policy,
    slot: Option[P.Slot]): Option[Vector[C.Check]] = {
    val additions = slot.toVector.flatMap(s => validation.checks.filter(check =>
      check.kind == WorkflowSbtValidation.Kind.TestOnly && s.checks.contains(check) &&
        !checks.exists(_.identity == check.identity)))
    Option.when(slot.nonEmpty && !checks.exists(_.kind == WorkflowSbtValidation.Kind.TestOnly) && additions.nonEmpty &&
      checks.forall(validation.checks.contains) && validation.checks.filter(checks.contains) == checks &&
      checks.forall(slot.get.checks.contains) && additions.map(_.identity).distinct.size == additions.size)(checks ++ additions)
  }
  private def _valid_selection(t: Target): Boolean = t.proposedChecks != null && (t.continuationPolicy match {
    case `policy` => t.proposedChecks.isEmpty
    case `selectionPolicy` => t.purpose.nonEmpty && t.proposedChecks.nonEmpty &&
      t.proposedChecks == proposedChecks(t.selectedChecks, t.validationPolicy, t.purposeSlot)
    case _ => false
  })
  def attemptChecks(target: Target): Vector[C.Check] = target.proposedChecks.getOrElse(target.selectedChecks)
  def attemptSelection(target: Target): Option[RepositorySyncProgramImpact.Selection] =
    target.purpose.flatMap(_.selection).map(_.copy(checks = attemptChecks(target)))
  /** Only this exact direction may transform the historical selection. */
  private[admission] def validPurposeAttempt(progress: P.Progress, direction: Direction): Boolean =
    direction.target.purpose.exists(original => progress == original.copy(
      receipts = progress.receipts, assessment = progress.assessment, review = progress.review,
      selection = attemptSelection(direction.target), hostContinuation = Some(direction)) &&
      progress.receipts.take(direction.target.successfulPrefix.size) == direction.target.successfulPrefix)
  private def _valid_source_keys(t: Target): Boolean = t.purpose match {
    case None => t.candidateKey == N.candidateKey(t.sourceNamespace, "pending-merge") &&
      t.failureKey == N.candidateKey(t.sourceNamespace, "validation-result-" + (t.checkIndex + 2)) &&
      t.intentKey == N.candidateKey(t.sourceNamespace, "validation-intent-" + (t.checkIndex + 2))
    case Some(progress) => progress.cursor >= 0 && progress.attempt >= 0 && progress.slotCursor >= 0 && t.sourceNamespace.isEmpty &&
      t.candidateKey == "purpose-" + (progress.cursor + 1) +
        (if (progress.attempt == 0) "" else "-repair-" + progress.attempt) + "-prepared" &&
      t.failureKey == RepositorySyncPurposeRuntime.attemptPrefix(progress) + "-check-" + t.checkIndex + "-result" &&
      t.intentKey == RepositorySyncPurposeRuntime.attemptPrefix(progress) + "-check-" + t.checkIndex + "-intent"
  }
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Formal failure target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def decodeAnswer(value: Json): Either[String, RepositorySyncValidationContinuation.Answer] =
    _decode_answer(value, choices)
  def decodeAnswer(target: Target, value: Json): Either[String, RepositorySyncValidationContinuation.Answer] =
    _decode_answer(value, availableChoices(target))
  private def _decode_answer(value: Json, allowed: Vector[String]): Either[String, RepositorySyncValidationContinuation.Answer] = for {
    answer <- value.as[RepositorySyncValidationContinuation.Answer].left.map(_.message)
    _ <- Either.cond(answer.asJson == value && allowed.contains(answer.choice) && ordinary(answer.rationale), (),
      "Formal failure answer requires exact fields and meaningful ordinary rationale")
  } yield answer
  def validAssertions(target: Target, choice: String, ended: Boolean, accepted: Boolean): Boolean =
    if (target.pending.isEmpty) !ended && !accepted else choice == "Defer" || ended && accepted
  def bindingC(component: String, target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, ended: Boolean, accepted: Boolean): Consequence[Json] =
    if (!validTarget(target) || component != target.componentInstance || participant != target.context.participantIdentity ||
        !availableChoices(target).contains(choice) || !ordinary(rationale) || reference == null || !exact(reference.identity) ||
        !exact(reference.revision) || !validAssertions(target, choice, ended, accepted))
      N.invalid("formal failure direction requires the exact failed candidate, host context and applicable old-worker assertions")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson,
      "repositoryValidationContinuation" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson,
          "priorExecutionEnded" -> ended.asJson, "acceptOriginalUncertainty" -> accepted.asJson))))
  private def _host_c(core: ActionCall.Core): Consequence[Json] = for {
    raw <- Option(core.executionContext.resolvedParameters).flatMap(_.get(GoalPhaseWholePhaseCandidateRecovery.processPlanKey))
      .map(_.value).map(Consequence.success).getOrElse(N.invalid("ordinary resolved process-execution plan is absent"))
    plan <- (raw match {
      case ConfigurationValue.StringValue(text) if text != null => io.circe.parser.parse(text).left.map(_.message)
      case value => GoalPhaseWholePhaseCandidateRecovery.configurationJson(value)
    }).fold(N.invalid, Consequence.success)
    _ <- if (plan.asObject.exists(_.nonEmpty)) Consequence.unit else N.invalid("ordinary resolved process plan is malformed")
  } yield plan
  private def _load_c(store: N.Store, key: String): Consequence[N.Stage] = store.loadC(key)
    .flatMap(_.map(Consequence.success).getOrElse(N.invalid("formal failure native source is absent: " + key)))
  private def _history_c(current: InstanceRecord, stage: N.Stage): Consequence[Unit] =
    prefixC(current, stage.record.revision.value.toString).flatMap(actual =>
      if (actual == stage.record) Consequence.unit else N.invalid("formal failure source crosses its native history"))

  private def _discover_c(store: N.Store, current: InstanceRecord, validation: C.Policy): Consequence[Source] = for {
    purpose <- store.currentPurposeC(current)
    source <- purpose match {
      case Some(failed) if failed.payload.stage == "PurposeRepairRequired" => for {
        progress <- failed.payload.purposes.map(Consequence.success).getOrElse(N.invalid("formal purpose failure has no progress"))
        checks <- progress.selection.map(s => Consequence.success(s.checks)).getOrElse(N.invalid("formal purpose failure has no selection"))
        index = progress.receipts.size - 2
        check <- checks.lift(index).map(Consequence.success).getOrElse(N.invalid("structural failure is not eligible"))
        slot <- store.declaration.purposePolicy.toVector.flatMap(_.slots).lift(progress.slotCursor)
          .map(Consequence.success).getOrElse(N.invalid("failed purpose slot is absent"))
        prefix = RepositorySyncPurposeRuntime.attemptPrefix(progress)
        intent <- _load_c(store, prefix + "-check-" + index + "-intent")
        candidate <- _load_c(store, "purpose-" + (progress.cursor + 1) +
          (if (progress.attempt == 0) "" else "-repair-" + progress.attempt) + "-prepared")
      } yield Source(store, candidate, failed, intent, checks, check, index, progress.receipts.dropRight(1),
        progress.receipts.last, "", Some(slot))
      case _ => for {
        candidate <- store.currentMergeCandidateC(current)
        failed <- C.retainedFailureC(store, candidate.pending, validation, candidate.namespace)
          .flatMap(_.map(Consequence.success).getOrElse(N.invalid("no genuine retained product-check failure")))
        state = failed.payload.validation.get
        index = state.receipts.size - 3
        check <- state.selected.lift(index).map(Consequence.success).getOrElse(N.invalid("structural failure is not eligible"))
        intent <- _load_c(store, N.candidateKey(candidate.namespace, "validation-intent-" + (index + 2)))
      } yield Source(store, candidate.pending, failed, intent, state.selected, check, index,
        state.receipts.dropRight(1), state.receipts.last, candidate.namespace, None)
    }
    _ <- _authenticate_receipts_c(source, current)
  } yield source

  private def _authenticate_receipts_c(source: Source, current: InstanceRecord): Consequence[Unit] = {
    val store = source.store
    val purpose = source.failed.payload.purposes
    val structural = if (purpose.nonEmpty) 1 else 2
    val receipts = source.prefix :+ source.receipt
    val inherited = purpose.flatMap(p => p.hostContinuation.filter(_.target.purpose.exists(previous =>
      previous.cursor == p.cursor && previous.attempt == p.attempt && previous.slotCursor == p.slotCursor)))
      .map(_.target.successfulPrefix)
      .orElse(source.candidate.payload.formalFailure.map(_.target.successfulPrefix))
    for {
      seed <- inherited.map(Consequence.success).getOrElse(source.candidate.payload.requiredLint match {
        case Some(direction) => RepositorySyncRequiredLintContinuation.seedC(store, source.candidate,
          source.namespace, direction.target.validationPolicy, source.checks)
        case None => Consequence.success(Vector.empty[C.Receipt])
      })
      _ <- if (completeFailure(source.receipt, source.check) && validPrefix(source.prefix, source.checks, purpose.isEmpty) &&
          source.prefix.size == source.index + structural && receipts.take(seed.size) == seed) Consequence.unit
        else N.invalid("formal failure has UNKNOWN, incomplete, structural or changed selected receipts")
      _ <- _history_c(current, source.candidate)
      _ <- _history_c(current, source.failed)
      _ <- _history_c(current, source.intent)
      _ <- if (purpose.forall(progress => source.candidate.payload.purposes.exists(candidate =>
          candidate.candidate == progress.candidate && candidate.request == progress.request && candidate.plan == progress.plan &&
          candidate.cursor == progress.cursor && candidate.attempt == progress.attempt && candidate.slotCursor == progress.slotCursor)))
        Consequence.unit else N.invalid("formal purpose result changed its original prepared candidate")
      _ <- purpose.flatMap(_.hostContinuation).filter(d => d.target.purpose.exists(p => purpose.exists(current =>
          p.cursor == current.cursor && p.attempt == current.attempt && p.slotCursor == current.slotCursor))) match {
        case Some(direction) if !purpose.exists(validPurposeAttempt(_, direction)) =>
          N.invalid("formal purpose selection differs from its exact retained direction")
        case _ => Consequence.unit
      }
      _ <- if (source.failed.record.revision == source.intent.record.revision.next &&
          source.failed.record.history.dropRight(1) == source.intent.record.history) Consequence.unit
        else N.invalid("formal failed check is not adjacent to its actual intent")
      _ <- (seed.size until receipts.size).foldLeft(Consequence.unit) { (prior, index) => prior.flatMap { _ =>
        val progress = purpose
        val prefix = progress.map(RepositorySyncPurposeRuntime.attemptPrefix)
        val resultkey = prefix.map(p => if (index == 0) p + "-structural" else p + "-check-" + (index - 1) + "-result")
          .getOrElse(N.candidateKey(source.namespace, "validation-result-" + index))
        val intentkey = prefix.map(p => if (index == 0) p + "-structural-intent" else p + "-check-" + (index - 1) + "-intent")
          .getOrElse(N.candidateKey(source.namespace, "validation-intent-" + index))
        for {
          intent <- _load_c(store, intentkey)
          result <- _load_c(store, resultkey)
          _ <- _history_c(current, intent)
          _ <- _history_c(current, result)
          before = intent.payload.purposes.map(_.receipts).orElse(intent.payload.validation.map(_.receipts))
          after = result.payload.purposes.map(_.receipts).orElse(result.payload.validation.map(_.receipts))
          _ <- if (result.record.revision == intent.record.revision.next && result.record.history.dropRight(1) == intent.record.history &&
              before.contains(receipts.take(index)) && after.contains(receipts.take(index + 1)) &&
              (if (purpose.nonEmpty) intent.payload.purposes.exists(p => p.candidate == purpose.get.candidate &&
                p.selection == purpose.get.selection && p.cursor == purpose.get.cursor && p.attempt == purpose.get.attempt &&
                p.slotCursor == purpose.get.slotCursor)
                else intent.payload.validation.exists(s => s.sourceRevision == source.candidate.record.revision.value.toString &&
                  s.selected == source.checks))) Consequence.unit
            else N.invalid("formal check prefix crosses contiguous original intent/result facts")
        } yield ()
      }}
    } yield ()
  }

  private def _observe_c(identity: String, d: F.Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    validation: C.Policy, acknowledge: String => Consequence[Unit]): Consequence[Observation] = for {
    session <- F.sessionC(identity, d, definition, core)
    current = session.started.current
    _ <- if (current.lifecycle == Lifecycle.Active && current.currentProgression.exists(p =>
        Set("sync.PurposeRepairRequired", "sync.ValidationChecking", "sync.ValidationRepairRequired")(p.value))) Consequence.unit
      else N.invalid("formal failure continuation requires its current failed Purpose or pending-merge frontier")
    store <- N.bindC(session, d, core, acknowledge)
    source <- _discover_c(store, current, validation)
    _ <- if (source.checks.forall(validation.checks.contains) &&
        validation.checks.filter(source.checks.contains) == source.checks &&
        source.slot.forall(slot => source.checks.forall(slot.checks.contains))) Consequence.unit
      else N.invalid("formal failure selected checks differ from the current fixed product and workspace policy")
    inspected <- current.suspension match {
      case None => Consequence.success(None)
      case Some(boundary) => for {
        _ <- if (boundary.completion.value == "repository-native-repair-completion-v1") Consequence.unit
          else N.invalid("another pending semantic issue cannot be superseded as a formal failure")
        adapter <- session.storage.bindWorkOrderSupersessionC(RepositorySyncNativeMergeRepairContinuation.completionOperation,
          RepositorySyncNativeMergeRepair.inputCodec, _checkpoint)
        actual <- adapter.inspectC(ContinuationIdentity(boundary.continuationIdentity.value), current.identity)(using core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(N.invalid("pending failed-check repair is absent")))
        input <- NativeWorkflowOperationOutput.workInputC(actual.issued)
        _ <- if (actual.current == current && actual.entityRevision.value == 1L &&
            actual.continuation.status == ContinuationRuntimePersistence.Status.Available && actual.continuation.claimId.isEmpty &&
            input.validationFailure.contains(source.receipt.identity) && input.review.validatedRevision == source.failed.record.revision.value.toString &&
            input.review.checks.lastOption.contains(source.receipt)) Consequence.unit
          else N.invalid("claimed, completed or unrelated repair cannot be superseded")
        order <- actual.issued.current match {
          case value: WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeRepair.Request] => Consequence.success(value)
          case _ => N.invalid("formal failure requires its actual repair WorkOrder")
        }
      } yield Some(Pending(boundary.continuationIdentity.value, order.request.expectedRevision.value,
        actual.entityRevision.value.toString, "Available", None, canonical(RepositorySyncNativeMergeRepair.inputCodec.encode(input))) -> actual.entityRevision)
    }
    actual <- G.inspectC(core)
    _ <- store.scopeC(actual.observed)
    tree <- G.indexTreeC(core)
    _ <- _live_source_c(source)
    plan <- _host_c(core)
    proposed = Option.when(source.failed.payload.purposes.nonEmpty)(proposedChecks(source.checks, validation, source.slot)).flatten
    target = Target(core.component.get.core.instanceId.canonicalKey, identity, session.context,
      store.original.repositoryRoot, d.target.endpoint, current.revision.value.toString, current.causation.value,
      source.namespace, _key(source.candidate, identity), source.candidate.record.revision.value.toString,
      _key(source.failed, identity), source.failed.record.revision.value.toString,
      _key(source.intent, identity), source.intent.record.revision.value.toString,
      _reference(identity, _key(source.candidate, identity)), _reference(identity, _key(source.failed, identity)),
      _reference(identity, _key(source.intent, identity)), source.index, source.check, source.checks, source.prefix,
      source.receipt, validation, source.failed.payload.validation, source.failed.payload.purposes, source.slot,
      actual, tree, plan, inspected.map(_._1), if (proposed.nonEmpty) selectionPolicy else policy, proposed)
    _ <- if (validTarget(target)) Consequence.unit else N.invalid("formal failure target is incomplete or crossed")
  } yield Observation(session, source, target, inspected.map(_._2))
  private def _live_source_c(source: Source): Consequence[Unit] = source.failed.payload.purposes match {
    case Some(progress) => for {
      _ <- RepositorySyncPurposeRuntime.candidateWorkspaceC(source.store, source.slot.get, progress.candidate.get)
      _ <- source.checks.foldLeft(Consequence.unit)((prior, check) => prior.flatMap(_ =>
        source.slot.get.checkWorkspaces.get(check.identity).fold(Consequence.unit)(workspace => for {
          actual <- WorkflowManagedProcessExecution.executeGitC(source.store.core, org.goldenport.cncf.processexecution.GitOperation.Root, workspace).flatMap(_.valueC)
          expected <- WorkflowManagedProcessExecution.executeGitC(source.store.core, org.goldenport.cncf.processexecution.GitOperation.Root, source.slot.get.workspace).flatMap(_.valueC)
          _ <- if (actual == expected) Consequence.unit else N.invalid("formal check workspace no longer consumes its candidate")
        } yield ())))
    } yield ()
    case None => C.preconditionsC(source.store, source.candidate)
  }

  private def _authenticate_target_c(store: N.Store, current: InstanceRecord, target: Target): Consequence[Source] = for {
    before <- prefixC(current, target.frontierRevision)
    candidate <- _load_c(store, target.candidateKey)
    failed <- _load_c(store, target.failureKey)
    intent <- _load_c(store, target.intentKey)
    source = Source(store, candidate, failed, intent, target.selectedChecks, target.check, target.checkIndex,
      target.successfulPrefix, target.failed, target.sourceNamespace, target.purposeSlot)
    _ <- if (validTarget(target) && before.causation.value == target.frontierCause && target.sessionId == store.identity &&
        target.context == store.context && target.root == store.original.repositoryRoot && target.endpoint == store.declaration.target.endpoint &&
        candidate.record.revision.value.toString == target.candidateRevision && failed.record.revision.value.toString == target.failureRevision &&
        intent.record.revision.value.toString == target.intentRevision && failed.payload.purposes == target.purpose &&
        failed.payload.validation == target.mergeState) Consequence.unit
      else N.invalid("formal failure direction crosses its original retained source")
    _ <- _authenticate_receipts_c(source, before)
    _ <- target.purpose.flatMap(_.hostContinuation) match {
      case Some(previous) => selectedC(store, before).flatMap {
        case Some(saved) if saved.direction == previous => Consequence.unit
        case _ => N.invalid("purpose attempt lost its previous host direction")
      }
      case None => Consequence.unit
    }
    _ <- if (target.purpose.isEmpty) store.currentMergeCandidateC(before).flatMap(value =>
      if (value.pending == candidate && value.namespace == target.sourceNamespace) Consequence.unit
      else N.invalid("formal failure candidate is no longer the actual original lineage")) else Consequence.unit
  } yield source
  def selectedC(store: N.Store, current: InstanceRecord): Consequence[Option[Saved]] =
    current.history.reverse.find(_.currentProgression.contains(ProgressionReference("sync.FormalFailureContinuationAdmitted"))) match {
      case None => Consequence.success(None)
      case Some(entry) => for {
        direction <- if (entry.causation.value.startsWith(cause)) for {
          adapter <- store.storage.bindWorkOrderSupersessionC(RepositorySyncNativeMergeRepairContinuation.completionOperation,
            RepositorySyncNativeMergeRepair.inputCodec, _checkpoint)
          retained <- adapter.loadRetainedC(ContinuationIdentity(entry.causation.value.stripPrefix(cause)), current.identity)(using store.core.executionContext)
            .flatMap(_.map(Consequence.success).getOrElse(N.invalid("formal direction has no supersession receipt")))
          _ <- _checkpoint.decode(_checkpoint.encode(retained.receipt.payload)).fold(N.invalid, _ => Consequence.unit)
          _ <- if (entry.revision == retained.receipt.nextRevision && retained.receipt.payload.direction.target.pending.exists(
              p => cause + p.identity == entry.causation.value)) Consequence.unit else N.invalid("formal supersession source differs")
        } yield retained.receipt.payload.direction else for {
          key <- Option(entry.causation.value).filter(_.startsWith("native-repository:" + store.identity + ":formal-failure-"))
            .map(value => Consequence.success(value.stripPrefix("native-repository:" + store.identity + ":")))
            .getOrElse(N.invalid("formal direction causation is malformed"))
          saved <- _load_c(store, key)
          _ <- _history_c(current, saved)
          direction <- saved.payload.formalFailure.map(Consequence.success).getOrElse(N.invalid("formal direction payload is absent"))
          _ <- if (direction.target.pending.isEmpty && key == adoptionKey(direction.target)) Consequence.unit
            else N.invalid("formal mechanical admission is not a result-only direction")
        } yield direction
        _ <- direction.asJson.as[Direction].fold(e => N.invalid(e.message), _ => Consequence.unit)
        _ <- _authenticate_target_c(store, current, direction.target)
        before <- prefixC(current, direction.target.frontierRevision)
        _ <- if (entry.revision == before.revision.next && entry.suspension.isEmpty && entry.lifecycle == Lifecycle.Active &&
            (direction.target.pending match {
              case None => before.suspension.isEmpty
              case Some(pending) => before.suspension.exists(_.continuationIdentity.value == pending.identity)
            })) Consequence.unit else N.invalid("formal direction is not adjacent to its original failed frontier")
        _ <- direction.target.pending match {
          case None => Consequence.unit
          case Some(pending) => for {
            issued <- store.storage.bindIssuedC(RepositorySyncNativeMergeRepairContinuation.completionOperation, RepositorySyncNativeMergeRepair.inputCodec)
            work <- issued.loadC(ContinuationIdentity(pending.identity))(using store.core.executionContext)
              .flatMap(_.map(Consequence.success).getOrElse(N.invalid("superseded original repair is absent")))
            input <- NativeWorkflowOperationOutput.workInputC(work)
            _ <- if (canonical(RepositorySyncNativeMergeRepair.inputCodec.encode(input)) == pending.input) Consequence.unit
              else N.invalid("superseded repair no longer binds its full original input")
          } yield ()
        }
      } yield Some(Saved(direction, entry.revision))
    }

  def describeC(identity: String, d: F.Declaration, definition: DefinitionBinding, core: ActionCall.Core,
    validation: C.Policy, acknowledge: String => Consequence[Unit]): Consequence[V.RepositorySyncValidationContinuationDescriptionResult] =
    _observe_c(identity, d, definition, core, validation, acknowledge).map(observed =>
      V.RepositorySyncValidationContinuationDescriptionResult(identity, "adoption-required", targetMessage(observed.target),
        observed.target.componentInstance, publicRecord(observed.target), availableChoices(observed.target),
        RepositorySyncValidationContinuation.adoptionOperation))
  def adoptC(input: V.RepositorySyncValidationContinuationAdoptionInput, d: F.Declaration, definition: DefinitionBinding,
    core: ActionCall.Core, validation: C.Policy, installation: RepositorySyncValidationContinuation.Installation,
    acknowledge: String => Consequence[Unit]): Consequence[V.RepositorySyncValidationContinuationAdoptionResult] = for {
    target <- input.target.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(decodeTarget).fold(N.invalid, Consequence.success))
    _ <- if (input.sessionId == target.sessionId && input.executionEvidence.workerIdentity.contains(target.context.participantIdentity))
      Consequence.unit else N.invalid("formal failure evidence has a foreign participant or Session")
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, target, input.choice, input.rationale,
      target.context.participantIdentity, reference, input.priorExecutionEnded, input.acceptOriginalUncertainty)
    _ <- installation.evidence.admitC(core, reference, binding)
    session <- F.sessionC(input.sessionId, d, definition, core)
    store <- N.bindC(session, d, core, acknowledge)
    previous <- selectedC(store, session.started.current)
    direction = Direction(target, input.choice, input.rationale, target.context.participantIdentity, reference,
      input.priorExecutionEnded, input.acceptOriginalUncertainty)
    outcome <- previous.filter(_.direction.target.frontierRevision == target.frontierRevision) match {
      case Some(saved) if saved.direction == direction && session.started.current.revision == saved.revision =>
        for { _ <- requireCurrentContextC(store, target, validation) } yield "already-adopted"
      case Some(_) => N.invalid("repeated formal failure direction is stale or conflicting")
      case None => for {
        actual <- _observe_c(input.sessionId, d, definition, core, validation, acknowledge)
        _ <- if (actual.target == target) Consequence.unit else N.invalid("formal target drifted; describe again")
        result <- if (input.choice == "Defer") Consequence.success("deferred") else target.pending match {
          case None => store.saveC(adoptionKey(target), "native-formal-failure-continuation-admitted", actual.session.started.current,
            actual.source.failed.payload.copy(stage = "FormalFailureContinuationAdmitted", formalFailure = Some(direction))).map(_ => "adopted")
          case Some(pending) => for {
            adapter <- actual.session.storage.bindWorkOrderSupersessionC(RepositorySyncNativeMergeRepairContinuation.completionOperation,
              RepositorySyncNativeMergeRepair.inputCodec, _checkpoint)
            current = actual.session.started.current
            _ <- if (current.currentProgression.exists(p => domain.statemachine.RepositorySyncBoundaryLifecycle.permits(
                p.value.stripPrefix("sync."), "native-formal-failure-continuation-admitted", "FormalFailureContinuationAdmitted")))
              Consequence.unit else N.invalid("formal supersession transition is not declared by CML")
            entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1), current.revision.next, Lifecycle.Active,
              Some(ProgressionReference("sync.FormalFailureContinuationAdmitted")), current.correlation,
              CausationReference(cause + pending.identity), current.derivedCompositeOccurrence, current.committedPredecessor, None)
            _ <- adapter.executeC(ContinuationIdentity(pending.identity), current.revision, actual.entityRevision.get,
              current, entry, TypedValue(_checkpoint.typeIdentity, Checkpoint(direction, "UNKNOWN")))(using core.executionContext)
          } yield "adopted"
        }
      } yield result
    }
  } yield V.RepositorySyncValidationContinuationAdoptionResult(input.sessionId, outcome != "deferred", outcome, message)

  def requireCurrentContextC(store: N.Store, target: Target, validation: C.Policy): Consequence[Unit] = for {
    host <- _host_c(store.core)
    actual <- G.inspectC(store.core)
    tree <- G.indexTreeC(store.core)
    _ <- store.scopeC(actual.observed)
    _ <- if (host == target.processPlan && validation == target.validationPolicy && actual == target.actual && tree == target.indexTree &&
        store.core.component.get.core.instanceId.canonicalKey == target.componentInstance &&
        target.purposeSlot == target.purpose.flatMap(p => store.declaration.purposePolicy.toVector.flatMap(_.slots).lift(p.slotCursor)))
      Consequence.unit else N.invalid("formal host, candidate, index or selected policy changed after description")
  } yield ()
  def authenticateCandidateC(store: N.Store, current: InstanceRecord, candidate: N.Stage, selected: String): Consequence[Unit] = for {
    direction <- candidate.payload.formalFailure.map(Consequence.success).getOrElse(N.invalid("formal candidate has no direction"))
    saved <- selectedC(store, current).flatMap(_.filter(_.direction == direction).map(Consequence.success)
      .getOrElse(N.invalid("formal candidate direction is not in its native history")))
    source <- _authenticate_target_c(store, current, direction.target)
    _ <- if (direction.target.purpose.isEmpty && selected == namespace(direction.target) &&
        candidate.record.revision == saved.revision.next && candidate.payload == source.candidate.payload.copy(
          stage = "PendingMergePrepared", validation = None, formalFailure = Some(direction), requiredLint = None, reviewInputs = None))
      Consequence.unit else N.invalid("formal candidate crosses its exact original pending tree and successful receipts")
  } yield ()
  def seedC(store: N.Store, candidate: N.Stage, selected: String, validation: C.Policy,
    checks: Vector[C.Check]): Consequence[Vector[C.Receipt]] = for {
    seed <- retainedSeedC(store, candidate, selected, validation, checks)
    _ <- candidate.payload.formalFailure.fold(Consequence.unit)(direction => requireCurrentContextC(store, direction.target, validation))
  } yield seed
  /** Historical failure discovery authenticates the original attempt without
    * demanding that the host still have its old, now corrected configuration. */
  def retainedSeedC(store: N.Store, candidate: N.Stage, selected: String, validation: C.Policy,
    checks: Vector[C.Check]): Consequence[Vector[C.Receipt]] = candidate.payload.formalFailure match {
    case None => Consequence.success(Vector.empty)
    case Some(direction) => for {
      instances <- store.storage.bindInstancesC()(using store.core.executionContext)
      current <- instances.loadC(candidate.record.identity)(using store.core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(N.invalid("formal candidate current instance is absent")))
      _ <- authenticateCandidateC(store, current, candidate, selected)
      _ <- if (checks == direction.target.selectedChecks && validation == direction.target.validationPolicy) Consequence.unit
        else N.invalid("formal attempt changed its selected checks or policy")
    } yield direction.target.successfulPrefix
  }
  def authenticatePurposeAttemptC(store: N.Store, stage: N.Stage, validation: Option[C.Policy]): Consequence[Unit] =
    stage.payload.purposes.flatMap(_.hostContinuation) match {
      case None => Consequence.unit
      case Some(direction) if !direction.target.purpose.exists(p => stage.payload.purposes.exists(current =>
          p.cursor == current.cursor && p.attempt == current.attempt && p.slotCursor == current.slotCursor)) => Consequence.unit
      case Some(direction) => for {
        instances <- store.storage.bindInstancesC()(using store.core.executionContext)
        current <- instances.loadC(stage.record.identity)(using store.core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(N.invalid("purpose host attempt current instance is absent")))
        saved <- selectedC(store, current).flatMap(_.filter(_.direction == direction).map(Consequence.success)
          .getOrElse(N.invalid("purpose host attempt has no authenticated direction")))
        _ <- validation.map(p => requireCurrentContextC(store, direction.target, p))
          .getOrElse(N.invalid("purpose host attempt has no current fixed policy"))
        progress = stage.payload.purposes.get
        _ <- if (validPurposeAttempt(progress, direction))
          Consequence.unit else N.invalid("purpose host attempt changed its candidate, grouping or successful receipts")
      } yield ()
    }
  def advanceC(store: N.Store, current: InstanceRecord, validation: C.Policy): Consequence[N.Stage] = for {
    saved <- selectedC(store, current).flatMap(_.map(Consequence.success).getOrElse(N.invalid("formal failure direction is absent")))
    direction = saved.direction
    target = direction.target
    _ <- if (current.revision == saved.revision && current.suspension.isEmpty) Consequence.unit
      else N.invalid("formal failure admission has already advanced or changed")
    source <- _authenticate_target_c(store, current, target)
    _ <- requireCurrentContextC(store, target, validation)
    _ <- _live_source_c(source)
    next <- target.purpose match {
      case Some(progress) => store.saveC(namespace(target) + "-purpose", "native-formal-failure-purpose-prepared", current,
        source.failed.payload.copy(stage = "PurposeMaterialized", formalFailure = Some(direction),
          purposes = Some(progress.copy(receipts = target.successfulPrefix, assessment = None, review = None,
            selection = attemptSelection(target), hostContinuation = Some(direction)))))
      case None => store.saveC(N.candidateKey(namespace(target), "pending-merge"), "native-formal-failure-merge-prepared", current,
        source.candidate.payload.copy(stage = "PendingMergePrepared", validation = None, formalFailure = Some(direction),
          requiredLint = None, reviewInputs = None))
    }
  } yield next
}
