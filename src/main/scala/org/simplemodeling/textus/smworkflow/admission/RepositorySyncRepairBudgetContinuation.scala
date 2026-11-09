package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import RepositorySyncManagedFetch as F
import RepositorySyncNativeMergeRepair as M
import RepositorySyncNativeMergeRepairContinuation as R
import RepositorySyncNativeMergeReview as Review
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P

/** Explicit finite Human allowance over the original cumulative repair history.
  * Adoption is passive; ordinary Advance owns the next paired repair issue.
  * @since   Nov.  4, 2026
  * @version Nov.  5, 2026
  */
private[smworkflow] object RepositorySyncRepairBudgetContinuation {
  val purpose = "repository-sync-repair-budget-continuation"
  val policy = "sm-workflow.repository-sync-repair-budget-continuation.v1"
  val describeOperation = "describeRepositorySyncRepairBudgetContinuation"
  val adoptionOperation = "adoptRepositorySyncRepairBudgetContinuation"
  val choices = Vector("ContinueRepairs", "Defer")
  private val _stage = "RepairBudgetContinuationAdmitted"
  private val _message = "The actual finite repair allowance is exhausted. An independent Human may grant a positive finite additional allowance for this exact failed candidate. Original limits, used repairs, failed receipts and earlier commits remain history. Adoption stores direction only; run separate ordinary Advance. Defer writes nothing."

  final case class Budget(originalLimit: Int, effectiveLimit: Int, usedIdentities: Vector[String]) {
    def usedRepairs: Int = usedIdentities.size
    def exhausted: Boolean = usedRepairs >= effectiveLimit
    def remaining: Int = math.max(0, effectiveLimit - usedRepairs)
  }
  final case class Target(componentInstance: String, sessionId: String, context: F.StartContext,
    root: String, endpoint: String, frontierRevision: String, frontierCause: String,
    sourceRevision: String, sourceCause: String, failedRevision: String, failedCause: String,
    failureReference: ContextReference, failedCheck: Option[RepositorySyncNativeMergeValidation.Receipt],
    review: Review.Request, purpose: Option[P.RepairSnapshot], originalLimit: Int, effectiveLimit: Int,
    usedIdentities: Vector[String], usedRepairs: Int, continuationPolicy: String)
  private given Encoder[Review.Request] = Encoder.instance(value => Review.inputCodec.encode(value).hcursor.downField("payload").focus.get)
  private given Decoder[Review.Request] = Decoder.instance(cursor => Review.inputCodec.decode(Json.obj(
    "schemaVersion" -> Json.fromString("sm-workflow.repository-native-merge-review-request.v2"), "payload" -> cursor.value))
    .left.map(reason => io.circe.DecodingFailure(reason, cursor.history)))
  object Target {
    private def _outer_receipt(value: Json): Json = value.mapObject(fields => fields("sbt").fold(fields)(facts =>
      fields.add("sbt", facts.mapObject(_.filter {
        case ("selection" | "reason", field) => !field.isNull
        case _ => true
      }))))
    given Encoder[Target] = deriveEncoder[Target].mapJson(_.mapObject { fields =>
      val selected = fields.filter {
        case ("failedCheck" | "purpose", value) => !value.isNull
        case _ => true
      }
      val failed = selected("failedCheck").fold(selected)(value => selected.add("failedCheck", _outer_receipt(value)))
      failed("review").fold(failed)(review => failed.add("review", review.mapObject(values =>
        values("checks").fold(values)(checks => values.add("checks", checks.mapArray(_.map(_outer_receipt)))))))
    })
    private val _raw: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(cursor => for {
      restored <- P.restoreRepairJson(cursor.value).left.map(reason => io.circe.DecodingFailure(reason, cursor.history))
      target <- _raw.decodeJson(restored)
      _ <- Either.cond(validTarget(target), (),
        io.circe.DecodingFailure("Repair budget target has malformed finite candidate facts", cursor.history))
      _ <- Either.cond(target.asJson == restored, (),
        io.circe.DecodingFailure("Repair budget target has a noncanonical complete contract", cursor.history))
    } yield target)
  }
  final case class Answer(choice: String, additionalRepairs: Int, rationale: String)
  final case class Direction(target: Target, choice: String, additionalRepairs: Int, rationale: String,
    participant: String, reference: ContextReference)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    private val _raw: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (value.choice == "ContinueRepairs" && value.asJson == cursor.value && bindingC(value.target.componentInstance,
          value.target, value.choice, value.additionalRepairs, value.rationale, value.participant, value.reference).isSuccess)
        Right(value)
      else Left(io.circe.DecodingFailure("Repair budget direction is incomplete or invalid", cursor.history))))
  }
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else N.invalid("repair budget continuation requires independent Human evidence")
  }

  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value &&
    !value.exists(_.isControl) && java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value)
  private def _revision(value: String): Boolean = value != null && value.matches("[1-9][0-9]*") &&
    value.toLongOption.exists(_ < Long.MaxValue - 8)
  private def _reference(value: ContextReference): Boolean = value != null && _exact(value.identity) && _exact(value.revision)
  private def _oid(value: String): Boolean = value != null && value.matches("[0-9a-f]{40}|[0-9a-f]{64}")
  private def _failed(value: RepositorySyncNativeMergeValidation.Receipt): Boolean = value != null &&
    _exact(value.identity) && value.termination == "exited" && value.exitCode != null && value.exitCode.exists(_ != 0)
  private def _identity(session: String, identity: String): Boolean = identity != null &&
    identity.startsWith(session + ":native-repair:") && _revision(identity.stripPrefix(session + ":native-repair:"))
  def validTarget(t: Target): Boolean = t != null && _exact(t.componentInstance) && _exact(t.sessionId) &&
    t.context != null && t.context.productIterator.forall(value => value.isInstanceOf[String] && _exact(value.asInstanceOf[String])) &&
    _exact(t.root) && scala.util.Try(java.nio.file.Path.of(t.root).isAbsolute).getOrElse(false) && _exact(t.endpoint) &&
    Vector(t.frontierRevision, t.sourceRevision, t.failedRevision).forall(_revision) &&
    t.failedRevision.toLong <= t.sourceRevision.toLong && t.sourceRevision.toLong <= t.frontierRevision.toLong &&
    Vector(t.frontierCause, t.sourceCause, t.failedCause).forall(_exact) && _reference(t.failureReference) &&
    t.failedCause.startsWith("native-repository:" + t.sessionId + ":") &&
    t.failureReference == ContextReference(t.sessionId + ":native-" + t.failedCause.stripPrefix("native-repository:" + t.sessionId + ":"), "1") &&
    t.continuationPolicy == policy && t.originalLimit >= 0 && t.effectiveLimit >= t.originalLimit &&
    t.usedIdentities != null && t.usedIdentities.forall(_identity(t.sessionId, _)) &&
    t.usedIdentities == t.usedIdentities.distinct && t.usedRepairs == t.usedIdentities.size && t.usedRepairs >= t.effectiveLimit &&
    t.review != null && t.review.instanceIdentity == t.sessionId && t.review.validatedRevision == t.failedRevision &&
    Vector(t.review.localTip, t.review.remoteTip, t.review.indexTree).forall(_oid) &&
    t.review.changedPaths != null && t.review.changedPaths.nonEmpty && t.review.changedPaths.distinct == t.review.changedPaths &&
    t.review.changedPaths.forall(RepositorySyncNativeConflictInput.relative) &&
    t.review.selectedChecks != null && t.review.selectedChecks.distinct == t.review.selectedChecks &&
    t.review.selectedChecks.forall(_exact) && t.review.checks != null && t.review.checks.forall(_ != null) &&
    t.review.localDiff != null && t.review.remoteDiff != null && _exact(t.review.validationMode) &&
    _exact(t.review.validationReason) && t.review.uncertainties != null && t.review.uncertainties.forall(_exact) &&
    t.review.priorReview != null && t.review.judgmentMode == "detailed-review" &&
    t.failedCheck != null && t.failedCheck == t.review.checks.lastOption.filter(_failed) &&
    t.failedCheck.forall(check => _failed(check) && t.review.checks.contains(check) &&
      t.review.selectedChecks.contains(check.identity)) && t.purpose != null && t.purpose.forall(snapshot =>
      RepositorySyncPurposeRuntime.validBudgetSnapshot(snapshot) && snapshot.progress.request.root == t.root &&
      snapshot.progress.request.repository == t.context.repositoryIdentity && snapshot.progress.candidate.exists(candidate =>
        candidate.parent == t.review.localTip && candidate.parent == t.review.remoteTip && candidate.tree == t.review.indexTree) &&
      snapshot.progress.receipts.drop(1) == t.review.checks &&
      snapshot.progress.selection.exists(_.checks.map(_.identity) == t.review.selectedChecks) &&
      (if (snapshot.progress.receipts.last.exitCode.exists(_ != 0)) t.failedCheck.contains(snapshot.progress.receipts.last)
        else t.failedCheck.isEmpty))

  def targetChoices(target: Target): Vector[String] = if (validTarget(target)) choices else Vector.empty
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Repair budget target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def decodePublicTarget(value: Json): Either[String, Target] = GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeTarget)
  def targetC(value: Record): Consequence[Target] = if (value == null || !DevelopmentOperationalView.uniqueFields(value))
    N.invalid("repair budget target is absent or has duplicate fields")
    else value.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(decodePublicTarget).fold(N.invalid, Consequence.success))
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(P.repairPublicJson(value.asJson))
  def decodeAnswer(value: Json): Either[String, Answer] = for {
    wire <- Option(value).toRight("Repair budget answer is absent")
    answer <- wire.as[Answer].left.map(_.message)
    _ <- Either.cond(answer.asJson == wire && choices.contains(answer.choice) && _exact(answer.rationale) &&
      (if (answer.choice == "Defer") answer.additionalRepairs == 0 else answer.additionalRepairs > 0), (),
      "Repair budget answer requires exact fields, rationale and a positive finite Int allowance, or zero for Defer")
  } yield answer
  def decodePublicAnswer(value: Json): Either[String, Answer] = GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeAnswer)
  def effectiveLimitC(target: Target, additionalRepairs: Int): Consequence[Int] =
    if (!validTarget(target) || additionalRepairs <= 0) N.invalid("repair budget requires an exhausted exact target and positive allowance")
    else scala.util.Try(Math.addExact(target.usedRepairs, additionalRepairs)).toOption
      .map(Consequence.success).getOrElse(N.invalid("repair budget finite Int allowance overflows"))
  def bindingC(component: String, target: Target, choice: String, additionalRepairs: Int, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] = for {
    _ <- if (validTarget(target) && component == target.componentInstance && participant == target.context.participantIdentity &&
        _reference(reference)) Consequence.unit else N.invalid("repair budget direction crosses component, target, participant or reference")
    _ <- decodeAnswer(Answer(choice, additionalRepairs, rationale).asJson).fold(N.invalid, _ => Consequence.unit)
    _ <- if (choice == "ContinueRepairs") effectiveLimitC(target, additionalRepairs).map(_ => ()) else Consequence.unit
  } yield Json.obj("componentInstance" -> component.asJson, "repositoryRepairBudgetContinuation" -> Json.obj(
    "target" -> target.asJson, "answer" -> Json.obj("choice" -> choice.asJson, "additionalRepairs" -> additionalRepairs.asJson,
      "rationale" -> rationale.asJson, "participant" -> participant.asJson, "reference" -> reference.asJson)))
  def selectBinding(value: Json): Either[String, Json] = for {
    wire <- Option(value).toRight("Repair budget binding is absent")
    component <- wire.hcursor.get[String]("componentInstance").left.map(_.message)
    targetjson <- wire.hcursor.downField("repositoryRepairBudgetContinuation").get[Json]("target").left.map(_.message)
    target <- decodeTarget(targetjson)
    answer = wire.hcursor.downField("repositoryRepairBudgetContinuation").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    additional <- answer.get[Int]("additionalRepairs").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    selected <- bindingC(component, target, choice, additional, rationale, participant, reference).toOption
      .toRight("Invalid independent repair budget direction")
    _ <- Either.cond(selected == wire, (), "Repair budget binding fields differ")
  } yield selected

  def usedIdentities(current: InstanceRecord): Vector[String] = current.history.flatMap(_.suspension.map(_.continuationIdentity.value))
    .filter(_.startsWith(current.identity.value + ":native-repair:"))
    .map(RepositorySyncRepairScopeCorrection.budgetIdentity).distinct
  def adoptionKey(target: Target): String = "repair-budget-" + target.frontierRevision + "-adopted"
  private[admission] def grantC(previous: Budget, direction: Direction, actualUsed: Vector[String]): Consequence[Budget] = for {
    _ <- if (previous != null && previous.usedIdentities != null && previous.originalLimit >= 0 &&
        previous.effectiveLimit >= previous.originalLimit && direction != null && validTarget(direction.target) &&
        direction.choice == "ContinueRepairs" && direction.target.originalLimit == previous.originalLimit &&
        direction.target.effectiveLimit == previous.effectiveLimit && actualUsed == direction.target.usedIdentities &&
        actualUsed.take(previous.usedIdentities.size) == previous.usedIdentities &&
        !actualUsed.contains(direction.target.sessionId + ":native-repair:" + direction.target.sourceRevision)) Consequence.unit
      else N.invalid("repair budget grant changes original allowance, previous effective limit or cumulative used identities")
    _ <- bindingC(direction.target.componentInstance, direction.target, direction.choice, direction.additionalRepairs,
      direction.rationale, direction.participant, direction.reference)
    limit <- effectiveLimitC(direction.target, direction.additionalRepairs)
  } yield Budget(previous.originalLimit, limit, actualUsed)
  private def _prefix_c(current: InstanceRecord, revision: String): Consequence[InstanceRecord] =
    RepositorySyncPurposeContentContinuation.prefixC(current, revision)
  private def _component_c(core: ActionCall.Core): Consequence[String] =
    core.component.map(value => Consequence.success(value.core.instanceId.canonicalKey))
      .getOrElse(N.invalid("repair budget requires its actual Component instance"))

  /** The only source of allowance is an adjacent, immutable native transition.
    * Every prior grant is replayed as data validation, never as an effect. */
  def budgetC(source: R.Source, originalLimit: Int): Consequence[Budget] =
    _budget_c(source.store, source.session.started.current, source.session.context, originalLimit)
  private def _budget_c(store: N.Store, current: InstanceRecord, context: F.StartContext, original: Int): Consequence[Budget] = {
    val prefix = "native-repository:" + store.identity + ":repair-budget-"
    if (original < 0 || current.identity.value != store.identity) return N.invalid("repair budget original allowance or instance is invalid")
    current.history.filter(entry => entry.currentProgression.contains(ProgressionReference("sync." + _stage)) &&
      entry.suspension.isEmpty).foldLeft(Consequence.success(Budget(original, original, Vector.empty))) { (prior, entry) => for {
      budget <- prior
      next <- if (entry.causation.value.startsWith(RepositorySyncRepairScopeCorrection.resumeCause))
        RepositorySyncRepairScopeCorrection.authenticateHistoryC(store, current, entry).map(_ => budget)
      else for {
      _ <- if (entry.causation.value.startsWith(prefix)) Consequence.unit else N.invalid("repair budget stage has no native adoption reference")
      saved <- store.scoped("").loadC(entry.causation.value.stripPrefix("native-repository:" + store.identity + ":"))
        .flatMap(_.map(Consequence.success).getOrElse(N.invalid("repair budget adoption history has no retained receipt")))
      direction <- saved.payload.repairBudget.map(Consequence.success).getOrElse(N.invalid("repair budget receipt lacks its exact direction"))
      _ <- direction.asJson.as[Direction].fold(error => N.invalid(error.message), _ => Consequence.unit)
      target = direction.target
      component <- _component_c(store.core)
      before <- _prefix_c(current, target.frontierRevision)
      after <- _prefix_c(current, entry.revision.value.toString)
      failed <- _failed_source_c(store, before, target)
      _ <- if (component == target.componentInstance && target.sessionId == store.identity && target.context == context &&
          target.root == store.original.repositoryRoot && target.endpoint == store.declaration.target.endpoint &&
          target.originalLimit == original && target.effectiveLimit == budget.effectiveLimit &&
          target.usedIdentities == usedIdentities(before) && target.usedRepairs == target.usedIdentities.size &&
          !target.usedIdentities.contains(target.sessionId + ":native-repair:" + target.sourceRevision) &&
          target.usedIdentities.take(budget.usedIdentities.size) == budget.usedIdentities &&
          before.lifecycle == Lifecycle.Active && before.suspension.isEmpty && before.causation.value == target.frontierCause &&
          Set("sync.PurposeRepairRequired", "sync.ValidationRepairRequired", "sync.ReviewRepairRequired")
            .contains(before.currentProgression.map(_.value).getOrElse("")) &&
          after == saved.record && entry.revision == before.revision.next && saved.record.history.dropRight(1) == before.history &&
          entry.causation.value == "native-repository:" + store.identity + ":" + adoptionKey(target) &&
          saved.payload == failed.payload.copy(stage = _stage, repairBudget = Some(direction))) Consequence.unit
        else N.invalid("repair budget grant crosses original selection, cumulative budget, failed source or adjacent native history")
      granted <- grantC(budget, direction, usedIdentities(before))
      } yield granted
    } yield next }.flatMap { budget =>
      val used = usedIdentities(current)
      if (used.take(budget.usedIdentities.size) == budget.usedIdentities && used.forall(_identity(store.identity, _)))
        Consequence.success(budget.copy(usedIdentities = used))
      else N.invalid("repair budget cumulative identities were reset or malformed")
    }
  }

  private def _failed_source_c(store: N.Store, frontier: InstanceRecord, target: Target): Consequence[N.Stage] = for {
    _ <- if (validTarget(target)) Consequence.unit else N.invalid("repair budget target is malformed")
    source <- _prefix_c(frontier, target.sourceRevision)
    failed <- store.scoped("").loadC(target.failedCause.stripPrefix("native-repository:" + store.identity + ":"))
      .flatMap(_.map(Consequence.success).getOrElse(N.invalid("repair budget original failed receipt is absent")))
    actual <- _prefix_c(frontier, target.failedRevision)
    _ <- if (source.causation.value == target.sourceCause && failed.record == actual && actual.causation.value == target.failedCause &&
        failed.payload.root == target.root && failed.payload.purposes == target.purpose.map(_.progress)) Consequence.unit
      else N.invalid("repair budget source crosses its exact immutable failed candidate")
    _ <- if (target.failedCheck.nonEmpty && target.purpose.isEmpty) for {
      _ <- if (source.causation.value.startsWith("native-repository:" + store.identity + ":")) Consequence.unit
        else N.invalid("repair budget check source lacks its actual native repair-required receipt")
      admitted <- store.scoped("").loadC(source.causation.value.stripPrefix("native-repository:" + store.identity + ":"))
        .flatMap(_.map(Consequence.success).getOrElse(N.invalid("repair budget check source receipt is absent")))
      _ <- if (admitted.record == source && admitted.payload == failed.payload.copy(stage = "ValidationRepairRequired"))
        Consequence.unit else N.invalid("repair budget check source differs from its original admitted failure")
    } yield () else Consequence.unit
    _ <- target.failedCheck match {
      case Some(check) =>
        val receipts = failed.payload.purposes.map(_.receipts.drop(1)).orElse(failed.payload.validation.map(_.receipts))
        val selected = failed.payload.purposes.flatMap(_.selection.map(_.checks.map(_.identity)))
          .orElse(failed.payload.validation.map(_.selected.map(_.identity)))
        if (Set("PurposeRepairRequired", "ValidationChecking")(failed.payload.stage) &&
            receipts.contains(target.review.checks) && selected.contains(target.review.selectedChecks) &&
            receipts.exists(_.lastOption.contains(check)) && _failed(check) &&
            (if (failed.payload.purposes.nonEmpty) source == failed.record &&
              source.currentProgression.contains(ProgressionReference("sync.PurposeRepairRequired"))
            else source.currentProgression.contains(ProgressionReference("sync.ValidationRepairRequired")) &&
              source.revision == failed.record.revision.next && source.history.dropRight(1) == failed.record.history)) Consequence.unit
        else N.invalid("repair budget source is not its actual settled failed check and exact selection")
      case None => RepositorySyncNativeMergeReviewContinuation.savedOriginalC(store, frontier, failed).flatMap {
        case Some(saved) if saved.result.result.value.decision == "repair" && saved.input == target.review && saved.next == source => Consequence.unit
        case _ => N.invalid("repair budget source is not its actual paired formal repair review")
      }
    }
    _ <- if (target.purpose.nonEmpty || failed.payload.validation.exists(state =>
        failed.payload.before.head == target.review.localTip && failed.payload.mergeHead.contains(target.review.remoteTip) &&
        failed.payload.indexTree.contains(target.review.indexTree) && state.mode == target.review.validationMode &&
        state.reason == target.review.validationReason && state.inputs.forall(_.changedPaths == target.review.changedPaths))) Consequence.unit
      else N.invalid("repair budget candidate facts differ from the actual failed validation")
    _ <- if (frontier.revision == source.revision && frontier == source) Consequence.unit
      else N.invalid("repair budget grant is not at the actual failed source frontier")
  } yield failed

  /** The authenticated scope frontier or its one ordinary replacement issue;
    * later or unrelated descendants cannot stand in for this assignment. */
  private[admission] def validScopeFrontier(current: InstanceRecord, target: RepositorySyncRepairScopeCorrection.Target,
    revision: InstanceRevision, originalIdentity: String): Boolean = RepositorySyncRepairScopeCorrection.validTarget(target) &&
    target.oldContinuationId == originalIdentity && current.identity.value == target.sessionId &&
    current.lifecycle == Lifecycle.Active && current.currentProgression.contains(ProgressionReference("sync." + _stage)) &&
    revision.value.toString == (target.frontierRevision.toLong + 1L).toString &&
    current.history.find(_.revision == revision).exists(entry => entry.lifecycle == Lifecycle.Active && entry.suspension.isEmpty &&
      entry.currentProgression.contains(ProgressionReference("sync." + _stage)) &&
      entry.causation.value == RepositorySyncRepairScopeCorrection.resumeCause + originalIdentity &&
      (if (current.revision == revision) current.suspension.isEmpty && current.causation == entry.causation
       else current.revision == revision.next && current.history.dropRight(1).lastOption.contains(entry) &&
         current.lifecycle == Lifecycle.Active && current.currentProgression == entry.currentProgression &&
         current.correlation == entry.correlation && current.derivedCompositeOccurrence == entry.derivedCompositeOccurrence &&
         current.committedPredecessor == entry.committedPredecessor &&
         current.causation.value == "native-repair-issued:" + target.sessionId && current.suspension.exists(boundary =>
           boundary.continuationIdentity.value == originalIdentity + ":scope:" + revision.value &&
           boundary.expectedRevision == current.revision)))

  def admittedFrontierC(source: R.Source): Consequence[Boolean] =
    if (!source.session.started.current.currentProgression.contains(ProgressionReference("sync." + _stage))) Consequence.success(false)
    else for {
      entry <- source.session.started.current.history.reverse.find(entry => entry.suspension.isEmpty &&
        entry.currentProgression.contains(ProgressionReference("sync." + _stage)) &&
        entry.causation.value.startsWith("native-repository:" + source.store.identity + ":repair-budget-"))
        .map(Consequence.success).getOrElse(N.invalid("current budget marker has no native adoption history"))
      saved <- source.store.loadC(entry.causation.value.stripPrefix("native-repository:" + source.store.identity + ":"))
        .flatMap(_.map(Consequence.success).getOrElse(N.invalid("current repair budget marker has no receipt")))
      direction <- saved.payload.repairBudget.map(Consequence.success).getOrElse(N.invalid("current budget marker lacks its direction"))
      target = direction.target
      _ <- budgetC(source, target.originalLimit)
      correction <- RepositorySyncRepairScopeCorrection.selectedC(source)
      current = source.session.started.current
      _ <- if (target.sourceRevision == source.basis.next.revision.value.toString &&
          target.sourceCause == source.basis.next.causation.value && target.failedRevision == source.basis.validated.record.revision.value.toString &&
          target.review == source.basis.input.copy(localDiff = target.review.localDiff, remoteDiff = target.review.remoteDiff,
            priorReview = target.review.priorReview) && target.purpose.map(_.progress) == source.basis.validated.payload.purposes &&
          (saved.record == current || (current.revision == saved.record.revision.next &&
            current.history.dropRight(1) == saved.record.history && current.suspension.exists(boundary =>
              boundary.continuationIdentity.value == RepositorySyncRepairScopeCorrection.originalIdentity(source))) ||
            correction.exists(value => validScopeFrontier(current, value.direction.target, value.revision,
              RepositorySyncRepairScopeCorrection.originalIdentity(source)))))
        Consequence.unit else N.invalid("current budget marker crosses the actual repair assignment")
    } yield true

  private final case class Observation(source: R.Source, target: Target)
  private def _observe_c(identity: String, port: R.Port, core: ActionCall.Core): Consequence[Observation] = for {
    source <- port.sourceC(identity, core)
    current = source.session.started.current
    original <- port.originalRepairLimit.map(Consequence.success).getOrElse(N.invalid("repair budget requires its original selected finite allowance"))
    budget <- budgetC(source, original)
    _ <- if (current.lifecycle == Lifecycle.Active && current.suspension.isEmpty && budget.exhausted &&
        Set("sync.PurposeRepairRequired", "sync.ValidationRepairRequired", "sync.ReviewRepairRequired")
          .contains(current.currentProgression.map(_.value).getOrElse("")) && current == source.basis.next) Consequence.unit
      else N.invalid("repair budget description requires the actual unsuspended formally failed exhausted source")
    _ <- port.requireUnissuedC(source)
    _ <- port.captureC(source).map(_ => ())
    snapshot <- source.basis.validated.payload.purposes match {
      case Some(_) => RepositorySyncPurposeRuntime.budgetSnapshotC(source.store, source.basis.validated).map(Some(_))
      case None => RepositorySyncNativeMergeValidation.preconditionsC(source.store, source.basis.validated).map(_ => Option.empty[P.RepairSnapshot])
    }
    component <- _component_c(core)
    failed = source.basis.validated.record
    target = Target(component, identity, source.session.context, source.basis.validated.payload.root,
      source.store.declaration.target.endpoint, current.revision.value.toString, current.causation.value,
      source.basis.next.revision.value.toString, source.basis.next.causation.value,
      failed.revision.value.toString, failed.causation.value,
      ContextReference(identity + ":native-" + failed.causation.value.stripPrefix("native-repository:" + identity + ":"), "1"),
      source.basis.validationFailure.flatMap(id => source.basis.input.checks.find(_.identity == id)),
      source.basis.input, snapshot, original, budget.effectiveLimit, budget.usedIdentities, budget.usedRepairs, policy)
    _ <- _failed_source_c(source.store, current, target)
  } yield Observation(source, target)

  private def _adoption_input_c(record: Record): Consequence[V.RepositorySyncRepairBudgetContinuationAdoptionInput] = for {
    _ <- if (record != null && DevelopmentOperationalView.uniqueFields(record)) Consequence.unit
      else N.invalid("repair budget input is absent or has duplicate fields")
    raw <- record.toJsonStringC
    json <- io.circe.parser.parse(raw).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
      .fold(N.invalid, Consequence.success)
    _ <- if (json.asObject.exists(_.keys.toSet == Set("sessionId", "target", "choice", "additionalRepairs", "rationale",
        "directionReference", "executionEvidence")) && json.hcursor.downField("directionReference").focus.flatMap(_.asObject)
        .exists(_.keys.toSet == Set("identity", "revision"))) Consequence.unit else N.invalid("repair budget adoption requires exact complete typed fields")
    _ <- decodeAnswer(Json.obj(Vector("choice", "additionalRepairs", "rationale")
      .map(key => key -> json.hcursor.downField(key).focus.getOrElse(Json.Null))*))
      .fold(N.invalid, _ => Consequence.unit)
    value <- V.RepositorySyncRepairBudgetContinuationAdoptionInput.createC(record)
    _ <- if (value != null && _exact(value.sessionId) && value.directionReference != null && value.executionEvidence != null &&
        value.executionEvidence.workerIdentity != null) Consequence.unit else N.invalid("repair budget adoption has incomplete Session or evidence")
    _ <- targetC(value.target)
    restored <- value.toRecord().toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).fold(N.invalid, Consequence.success))
    _ <- if (restored == json) Consequence.unit else N.invalid("repair budget adoption contains discarded or coerced fields")
  } yield value
  private def _adoption_source_c(request: Request): Consequence[NativeWorkflowResourceInput.Source[Record]] =
    NativeWorkflowResourceInput.selectC[Record](request).flatMap {
      case source @ NativeWorkflowResourceInput.Inline(record) => _adoption_input_c(record).map(_ => source)
      case source: NativeWorkflowResourceInput.Resource => Consequence.success(source)
    }
  def sessionC(suppliedRequest: Request, call: ActionCall.Core): Consequence[String] = {
    final class Reader extends ProcedureActionCall {
      val core = call
      def readSessionC: Consequence[String] = _adoption_source_c(suppliedRequest).flatMap(source =>
        NativeWorkflowResourceInput.readC(source, reference => WorkflowArtifactBudget.readTextC(core, reference,
          Some(java.nio.charset.StandardCharsets.UTF_8))).flatMap(_adoption_input_c).map(_.sessionId))
      def execute(): Consequence[OperationResponse] = N.invalid("repair budget Session reader is not an Operation")
    }
    new Reader().readSessionC
  }
  def operations(port: R.Port, installation: Installation): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port, installation), new Operation(false, port, installation))
  private final class Operation(describe: Boolean, port: R.Port, installation: Installation) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.RepositorySyncServiceService.DescribeRepositorySyncRepairBudgetContinuationOperation
      else SmWorkflowComponent.RepositorySyncServiceService.AdoptRepositorySyncRepairBudgetContinuationOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("RepositorySyncService") ||
          request.operation != (if (describe) describeOperation else adoptionOperation)) N.invalid("repair budget Operation identity differs")
      else if (describe) NativeWorkflowOperationInput.textC(request, "sessionId").map(id => Action(request, Some(id), None))
      else _adoption_source_c(request).map(input => Action(request, None, Some(input)))
    private final case class Action(request: Request, identity: Option[String],
      input: Option[NativeWorkflowResourceInput.Source[Record]]) extends CommandAction {
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          _ <- installation.validateC
          value <- input match {
            case None => Consequence.success(Option.empty[V.RepositorySyncRepairBudgetContinuationAdoptionInput])
            case Some(source) => NativeWorkflowResourceInput.readC(source, reference => WorkflowArtifactBudget.readTextC(core, reference,
              Some(java.nio.charset.StandardCharsets.UTF_8))).flatMap(_adoption_input_c).map(Some(_))
          }
          session = value.map(_.sessionId).orElse(identity).get
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
          selected = core.copy(executionContext = context)
          result <- value match {
            case None => _observe_c(session, port, selected).map(observed => NativeWorkflowOperationOutput.response(
              V.RepositorySyncRepairBudgetContinuationDescriptionResult(session, "adoption-required", _message,
                observed.target.componentInstance, targetRecord(observed.target), choices, adoptionOperation)))
            case Some(value) => _adopt_c(value, port, installation, selected).map(NativeWorkflowOperationOutput.response(_))
          }
        } yield result
      }
    }
  }
  private def _adopt_c(input: V.RepositorySyncRepairBudgetContinuationAdoptionInput, port: R.Port,
    installation: Installation, core: ActionCall.Core): Consequence[V.RepositorySyncRepairBudgetContinuationAdoptionResult] = for {
    target <- targetC(input.target)
    component <- _component_c(core)
    _ <- if (target.sessionId == input.sessionId && input.executionEvidence.workerIdentity.contains(target.context.participantIdentity))
      Consequence.unit else N.invalid("repair budget execution evidence crosses Session or participant")
    reference = ContextReference(input.directionReference.identity, input.directionReference.revision)
    binding <- bindingC(component, target, input.choice, input.additionalRepairs, input.rationale,
      target.context.participantIdentity, reference)
    _ <- installation.evidence.admitC(core, reference, binding)
    passive <- port.passiveC(input.sessionId, core)
    (session, store) = passive
    original <- port.originalRepairLimit.map(Consequence.success).getOrElse(N.invalid("repair budget original finite allowance is absent"))
    budget <- _budget_c(store, session.started.current, session.context, original)
    direction = Direction(target, input.choice, input.additionalRepairs, input.rationale, target.context.participantIdentity, reference)
    saved <- session.started.current.history.find(_.causation.value == "native-repository:" + store.identity + ":" + adoptionKey(target)) match {
      case None => Consequence.success(Option.empty[N.Stage])
      case Some(_) => store.loadC(adoptionKey(target))
    }
    outcome <- saved match {
      case Some(previous) => if (previous.payload.repairBudget.contains(direction)) Consequence.success("already-adopted")
        else N.invalid("repair budget repeated adoption contradicts the actual saved direction")
      case None => for {
        observed <- _observe_c(input.sessionId, port, core)
        _ <- if (observed.target == target) Consequence.unit else N.invalid("repair budget target is stale or crossed; describe again")
        result <- if (input.choice == "Defer") Consequence.success("deferred") else for {
          actual <- _observe_c(input.sessionId, port, core)
          _ <- if (actual.target == target) Consequence.unit else N.invalid("repair budget target drifted before adoption")
          _ <- actual.source.store.saveC(adoptionKey(target), "repair-budget-continuation", actual.source.session.started.current,
            actual.source.basis.validated.payload.copy(stage = _stage, repairBudget = Some(direction)))
        } yield "adopted"
      } yield result
    }
    limit <- if (outcome == "deferred") Consequence.success(budget.effectiveLimit)
      else effectiveLimitC(target, input.additionalRepairs)
  } yield V.RepositorySyncRepairBudgetContinuationAdoptionResult(input.sessionId, outcome != "deferred", outcome,
    if (outcome == "deferred") "No direction or effect was stored."
    else "Finite allowance retained with original history; run separate ordinary Advance.", original, limit,
    if (outcome == "adopted") target.usedRepairs else budget.usedRepairs)
}
