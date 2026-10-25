package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseNativeWork as W
import GoalPhaseOrderedProgression as O
import GoalPhaseOrderedHumanDecision as H
import GoalPhaseRemainingPlanAdoption as A

/** A separately observed Human purpose directive preserves the original review
  * and unanswered question. The native disposition family retains its old outer
  * identity; the exact inner version and origin own the new business meaning.
  * @since   Oct. 24, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseLegacyPurposeRepairContinuation {
  val purpose = "goal-phase-review-purpose-repair"
  val describeOperation = "describeGoalPhaseReviewPurposeRepair"
  val admissionOperation = "admitGoalPhaseReviewPurposeRepair"
  val schema = "sm-workflow.goal-phase-legacy-purpose-repair.v1"
  val origin = "legacy-review-human-purpose"
  val message = "Bind one independently observed actual Human directive acknowledging every saved blocking finding as an original Step purpose defect inside its unchanged owned scope. The original review, unanswered question, Plan, base, accepted prefix and repair history remain. Next ordinary Advance issues the same-Step RepairStep; no checks, Submit, Advance or commit run here."
  final case class Finding(findingOrdinal: Int, text: String, issueIdentity: String)
  object Finding {
    given Encoder[Finding] = deriveEncoder
    private val _decoder: Decoder[Finding] = deriveDecoder
    given Decoder[Finding] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (_keys(cursor.value, Set("findingOrdinal", "text", "issueIdentity")) && _finding(value)) Right(value)
      else Left(DecodingFailure("Legacy purpose finding is invalid", cursor.history))))
  }
  final case class Target(sessionId: String, questionIdentity: String, sourceRevision: String, pendingRevision: String,
    assignmentIdentity: String, assignmentSourceRevision: String, requestingResult: ContextReference,
    originalResult: W.Result, plan: O.Plan, base: O.Snapshot, completed: Vector[O.ClosedStep], generation: Long,
    contract: GoalPhasePurposeRepair.Contract, candidate: O.Snapshot, checksReceipt: String,
    repairProgress: GoalPhaseRepairProgress.Snapshot, findings: Vector[Finding])
  object Target {
    given Encoder[Target] = deriveEncoder
    private val _decoder: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (_keys(cursor.value, Set("sessionId", "questionIdentity", "sourceRevision", "pendingRevision", "assignmentIdentity",
          "assignmentSourceRevision", "requestingResult", "originalResult", "plan", "base", "completed", "generation", "contract",
          "candidate", "checksReceipt", "repairProgress", "findings")) && validateTargetC(value).isSuccess) Right(value)
      else Left(DecodingFailure("Legacy purpose target is invalid", cursor.history))))
  }
  final case class Payload(target: Target, directive: ContextReference, participant: String, rationale: String,
    acknowledgeOriginalPurposeDefects: Boolean)
  sealed trait StoredDisposition
  final case class ExistingRemainingPlan(payload: A.Payload) extends StoredDisposition
  final case class LegacyPurposeRepair(payload: Payload) extends StoredDisposition
  final case class ReleaseRepair(payload: GoalPhaseReleaseRepairContinuation.Payload) extends StoredDisposition
  trait EvidencePort { def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit] }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = _check(evidence != null, "independent Human directive provider is absent")
  }

  /** One native read, selected only by the explicit inner discriminator. */
  val storageCodec: WorkflowResultJsonV1.PayloadCodec[StoredDisposition] = new WorkflowResultJsonV1.PayloadCodec[StoredDisposition] {
    val typeIdentity = A.codec.typeIdentity
    def encode(value: StoredDisposition): Json = value match {
      case ExistingRemainingPlan(payload) => A.codec.encode(payload)
      case LegacyPurposeRepair(payload) => Json.obj("schemaVersion" -> schema.asJson, "origin" -> origin.asJson, "payload" -> payload.asJson)
      case ReleaseRepair(payload) => Json.obj("schemaVersion" -> GoalPhaseReleaseRepairContinuation.schema.asJson,
        "origin" -> GoalPhaseReleaseRepairContinuation.origin.asJson, "payload" -> payload.asJson)
    }
    def decode(value: Json): Either[String, StoredDisposition] = Option(value).toRight("Decision disposition is absent").flatMap { supplied =>
      if (supplied.hcursor.downField("schemaVersion").focus.isEmpty) {
        if (_keys(supplied, Set("target", "directive", "participant", "rationale")))
          A.codec.decode(supplied).map(ExistingRemainingPlan(_))
        else Left("Old Decision disposition has contradictory or unknown fields")
      } else if (supplied.hcursor.get[String]("schemaVersion").contains(GoalPhaseReleaseRepairContinuation.schema)) for {
        _ <- Either.cond(_keys(supplied, Set("schemaVersion", "origin", "payload")) &&
          supplied.hcursor.get[String]("origin").contains(GoalPhaseReleaseRepairContinuation.origin), (),
          "Release disposition has contradictory or unknown fields/origin")
        payload <- supplied.hcursor.get[GoalPhaseReleaseRepairContinuation.Payload]("payload").left.map(_.message)
      } yield ReleaseRepair(payload)
      else for {
        version <- supplied.hcursor.get[String]("schemaVersion").left.map(_.message)
        selected <- supplied.hcursor.get[String]("origin").left.map(_.message)
        _ <- Either.cond(version == schema && selected == origin && _keys(supplied, Set("schemaVersion", "origin", "payload")),
          (), "Unknown or contradictory Decision disposition version/origin")
        raw <- supplied.hcursor.get[Json]("payload").left.map(_.message)
        _ <- Either.cond(_keys(raw, Set("target", "directive", "participant", "rationale", "acknowledgeOriginalPurposeDefects")),
          (), "Legacy purpose disposition fields differ")
        payload <- raw.as[Payload].left.map(_.message)
        _ <- Either.cond(_payload_valid(payload), (), "Legacy purpose disposition is incomplete")
      } yield LegacyPurposeRepair(payload)
    }
  }

  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def targetC(value: Record): Consequence[Target] = if (value == null) F.invalid("purpose repair target is absent")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(_.as[Target].left.map(_.message))
      .fold(F.invalid(_), Consequence.success))

  def bindingC(component: String, target: Target, rationale: String, participant: String,
    reference: ContextReference, acknowledgeOriginalPurposeDefects: Boolean): Consequence[Json] = for {
    _ <- validateTargetC(target)
    _ <- _check(_exact(component) && _exact(participant) && _reference(reference) &&
      GoalPhasePurposeRepair.text(rationale) && acknowledgeOriginalPurposeDefects,
      "purpose directive must explicitly acknowledge the whole described original-purpose batch")
  } yield Json.obj("componentInstance" -> component.asJson, "reviewPurposeRepair" -> Json.obj(
    "target" -> target.asJson, "directive" -> Json.obj("rationale" -> rationale.asJson,
      "participant" -> participant.asJson, "reference" -> reference.asJson,
      "acknowledgeOriginalPurposeDefects" -> acknowledgeOriginalPurposeDefects.asJson)))

  def selectBinding(value: Json): Either[String, Json] = for {
    supplied <- Option(value).toRight("Purpose directive binding is absent")
    component <- supplied.hcursor.get[String]("componentInstance").left.map(_.message)
    target <- supplied.hcursor.downField("reviewPurposeRepair").get[Target]("target").left.map(_.message)
    directive = supplied.hcursor.downField("reviewPurposeRepair").downField("directive")
    rationale <- directive.get[String]("rationale").left.map(_.message)
    participant <- directive.get[String]("participant").left.map(_.message)
    reference <- directive.get[ContextReference]("reference").left.map(_.message)
    acknowledged <- directive.get[Boolean]("acknowledgeOriginalPurposeDefects").left.map(_.message)
    binding <- bindingC(component, target, rationale, participant, reference, acknowledged)
      .map[Either[String, Json]](Right(_)).fold(error => Left(error.toString), result => result)
    _ <- Either.cond(supplied == binding, (), "Purpose directive binding has unknown or contradictory fields")
  } yield binding

  /** Actual saved untyped review and every OPEN issue, without a new condition. */
  private[admission] def reviewFindingsC(controller: O.State, input: W.Request, result: W.Result,
    reference: ContextReference): Consequence[Vector[Finding]] = for {
    _ <- _check(controller != null && input != null && result != null && _reference(reference) &&
      input.kind == "ReviewStep" && input.purposeRepair != null && input.purposeRepair.isEmpty &&
      controller.stage == O.Stage.Decision && controller.decisionSource.contains(O.Stage.Review) &&
      controller.cursor < controller.plan.steps.size && input.stepIdentity.contains(controller.plan.steps(controller.cursor).identity) &&
      input.instanceIdentity != null && input.selectedPlan == controller.plan && input.originalBase == controller.base &&
      input.completed == controller.completed && input.generation == controller.generation && input.purpose == controller.plan.steps(controller.cursor).purpose &&
      input.candidate == controller.candidate && input.checksReceipt == controller.checksReceipt && input.candidate.nonEmpty &&
      input.checksReceipt.exists(_exact) && input.review.exists(_ != null) && input.checkFailure.isEmpty &&
      result.outcome == "decision-needed" && result.repairFindingSources != null && result.repairFindingSources.isEmpty &&
      result.planRevision.isEmpty && result.dependencyProposal.isEmpty && result.completionConditions.isEmpty &&
      GoalPhasePurposeRepair.text(result.summary) && result.findings != null && result.findings.nonEmpty &&
      result.findings.size <= 128 && result.findings.forall(GoalPhasePurposeRepair.text),
      "purpose continuation requires the actual saved untyped Step review decision and unchanged frontier")
    _ <- GoalPhaseAcceptanceBoundary.admitC(controller.plan, input.kind, result)
    _ <- GoalPhasePurposeRepair.validateRequestC(input)
    _ <- GoalPhaseFixIssues.validateOptionC(input.issues)
    issues = input.issues.toVector.flatMap(_.issues).filter(issue => issue.source == "REVIEW" && issue.status == "OPEN" &&
      issue.repository == controller.plan.repository && input.stepIdentity.contains(issue.step) && issue.evidenceReference == reference.identity)
    _ <- _check(issues.size == result.findings.size && issues.map(_.summary) == result.findings &&
      issues.map(_.identity).distinct.size == issues.size, "purpose batch must cover every saved blocking finding and its exact OPEN REVIEW issue in order")
    findings = issues.zipWithIndex.map((issue, index) => Finding(index + 1, issue.summary, issue.identity))
    _ <- controller.continueLegacyPurposeRepairC("description-eligibility", O.RepairDirective(result.summary, result.findings))
  } yield findings

  def validateTargetC(target: Target): Consequence[Unit] = _check(target != null &&
    Vector(target.sessionId, target.questionIdentity, target.assignmentIdentity, target.checksReceipt).forall(_exact) &&
    Vector(target.sourceRevision, target.pendingRevision, target.assignmentSourceRevision).forall(_revision) &&
    target.pendingRevision.toLong == target.sourceRevision.toLong + 1 && _reference(target.requestingResult) &&
    target.plan != null && O.validatePlanC(target.plan).isSuccess && GoalPhaseAcceptanceBoundary.savedPlanC(target.plan).isSuccess &&
    target.base != null && _snapshot(target.base) && target.completed != null && target.completed.size < target.plan.steps.size &&
    target.generation > 0 && target.generation < Long.MaxValue && GoalPhasePurposeRepair.validContract(target.contract) &&
    target.plan.steps.lift(target.completed.size).exists(step => step.identity == target.contract.stepIdentity &&
      step.purpose == target.contract.purpose && step.ownedPaths == target.contract.ownedPaths) &&
    _snapshot(target.candidate) && GoalPhaseRepairProgress.validateOptionC(Some(target.repairProgress)).isSuccess &&
    target.repairProgress.automaticCount < target.repairProgress.limit && target.findings != null && target.findings.nonEmpty &&
    target.findings.size <= 128 && target.findings.forall(_finding) &&
    target.findings.map(_.findingOrdinal) == (1 to target.findings.size).toVector &&
    target.findings.map(_.issueIdentity).distinct.size == target.findings.size &&
    target.originalResult != null && target.originalResult.outcome == "decision-needed" &&
    target.originalResult.repairFindingSources.isEmpty && target.originalResult.planRevision.isEmpty &&
    target.originalResult.dependencyProposal.isEmpty && target.originalResult.findings == target.findings.map(_.text) &&
    W.resultCodec.decode(W.resultCodec.encode(target.originalResult)).contains(target.originalResult), "legacy purpose target is invalid")

  def describeC(source: H.Source, core: ActionCall.Core): Consequence[V.GoalPhaseReviewPurposeRepairDescriptionResult] = for {
    target <- _target_c(source, core)
  } yield V.GoalPhaseReviewPurposeRepairDescriptionResult(target.sessionId, "direction-required", message,
    core.component.get.core.instanceId.canonicalKey, targetRecord(target), admissionOperation)

  def admitC(source: H.Source, declaration: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhaseReviewPurposeRepairInput): Consequence[V.GoalPhaseReviewPurposeRepairResult] = for {
    supplied <- targetC(input.target)
    actual <- _target_c(source, core)
    _ <- _check(input.sessionId == actual.sessionId && supplied == actual && input.acknowledgeOriginalPurposeDefects,
      "purpose directive crosses its exact current described target or omits acknowledgment")
    installation <- declaration.reviewPurposeRepair.map(Consequence.success).getOrElse(
      Consequence.securityPermissionDenied("Purpose repair has no independent actual Human directive provider"))
    _ <- installation.validateC
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, actual, input.rationale,
      source.session.participantIdentity, reference, input.acknowledgeOriginalPurposeDefects)
    _ <- installation.evidence.admitC(core, reference, binding)
    payload = Payload(actual, reference, source.session.participantIdentity, input.rationale, true)
    state <- _continued_c(source, payload)
    pending <- _pending_c(source)
    adapter <- source.session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec, storageCodec)
    _ <- adapter.executeC(H.identity(source), pending.revision, pending,
      F.history(pending, "phase." + state.stage.toString, reference.identity),
      TypedValue(storageCodec.typeIdentity, LegacyPurposeRepair(payload): StoredDisposition))(using core.executionContext)
  } yield V.GoalPhaseReviewPurposeRepairResult(input.sessionId, true, "review-purpose-repair-admitted", message)

  /** Called by the single shared native disposition reader after restart. */
  private[admission] def reconstructC(source: H.Source, core: ActionCall.Core, payload: Payload,
    sourceRevision: InstanceRevision, nextRevision: InstanceRevision): Consequence[A.Selected] = for {
    _ <- _check(_payload_valid(payload) && payload.participant == source.session.participantIdentity,
      "retained purpose direction is malformed or belongs to another participant")
    pending <- GoalPhaseNativeHistory.prefixC(source.session.current, sourceRevision)
    expected <- _historical_target_c(source, pending)
    _ <- _check(payload.target == expected && sourceRevision.value.toString == payload.target.pendingRevision &&
      nextRevision == sourceRevision.next, "retained purpose direction crosses its exact original assignment, review, issue or native revisions")
    state <- _continued_c(source, payload)
    next <- GoalPhaseNativeHistory.completionC(source.session.current, pending, payload.directive, state.stage.toString)
    _ <- _check(next.revision == nextRevision, "retained purpose successor differs from its native receipt")
  } yield A.Selected(state, next, source.documents, Some(decisionContext(payload)))

  private def _target_c(source: H.Source, core: ActionCall.Core): Consequence[Target] = for {
    pending <- _pending_c(source)
    target <- _historical_target_c(source, pending)
    questions <- source.session.storage.bindDecisionC(H.completionOperation, H.inputCodec)
    issued <- questions.loadC(H.identity(source))(using core.executionContext)
    _ <- _check(issued.nonEmpty, "purpose continuation requires its actual issued unanswered question")
  } yield target

  private def _historical_target_c(source: H.Source, pending: InstanceRecord): Consequence[Target] = for {
    _ <- _check(source.sourceHistoryRecovery.isEmpty && source.input.instanceIdentity == source.from.identity.value &&
      source.result.result.typeIdentity == W.resultCodec.typeIdentity && source.candidate.exists(saved =>
        saved.payload.candidate == source.controller.candidate && source.input.stepIdentity.contains(saved.payload.step)) &&
      pending.revision == source.from.revision.next && pending.suspension.exists(boundary =>
        boundary.continuationIdentity.value == H.identity(source).value && boundary.completion.value == "goal-phase-ordered-human-completion-v1"),
      "purpose continuation crosses its native source, candidate or original Human question")
    findings <- reviewFindingsC(source.controller, source.input, source.result.result.value, source.result.resultReference)
    contract <- GoalPhasePurposeRepair.capture("ReviewStep", source.input.stepIdentity, source.controller.plan)
      .map(Consequence.success).getOrElse(F.invalid("legacy review has no actual selected Step"))
    target = Target(source.from.identity.value, H.identity(source).value, source.from.revision.value.toString,
      pending.revision.value.toString, source.result.continuationId.value, source.input.sourceRevision, source.result.resultReference,
      source.result.result.value, source.controller.plan, source.controller.base, source.controller.completed, source.controller.generation,
      contract, source.controller.candidate.get, source.controller.checksReceipt.get, GoalPhaseRepairProgress.snapshot(source.controller), findings)
    _ <- validateTargetC(target)
  } yield target

  private[admission] def repairDirective(payload: Payload): O.RepairDirective =
    O.RepairDirective(payload.target.originalResult.summary, payload.target.findings.map(_.text))

  private[admission] def decisionContext(payload: Payload): W.DecisionContext =
    W.DecisionContext(payload.target.originalResult, origin, payload.rationale, Some(payload.directive))

  private def _continued_c(source: H.Source, payload: Payload): Consequence[O.State] =
    source.controller.continueLegacyPurposeRepairC(payload.directive.identity, repairDirective(payload))
  private def _pending_c(source: H.Source): Consequence[InstanceRecord] = for {
    following <- GoalPhaseNativeHistory.nextC(source.session.current, source.from)
    entry <- following match {
      case Some(value) if value.revision == source.session.current.revision && value.suspension.exists(boundary =>
          boundary.continuationIdentity.value == H.identity(source).value && boundary.completion.value == "goal-phase-ordered-human-completion-v1") =>
        Consequence.success(value)
      case _ => F.invalid("purpose repair requires the exact current unanswered Human frontier")
    }
    pending <- GoalPhaseNativeHistory.prefixC(source.session.current, entry.revision)
  } yield pending

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseReviewPurposeRepairOperation
      else SmWorkflowComponent.GoalPhaseServiceService.AdmitGoalPhaseReviewPurposeRepairOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") ||
          request.operation != (if (describe) describeOperation else admissionOperation)) F.invalid("purpose repair operation identity differs")
      else if (describe) NativeWorkflowOperationInput.valueC[V.GoalPhaseReviewPurposeRepairDescriptionInput](request, "input")
        .map(value => Action(request, value.sessionId, None, port))
      else NativeWorkflowOperationInput.valueC[V.GoalPhaseReviewPurposeRepairInput](request, "input").flatMap { value =>
        if (value == null || !_exact(value.sessionId) || value.reference == null ||
            !_reference(ContextReference(value.reference.identity, value.reference.revision)) || !GoalPhasePurposeRepair.text(value.rationale) ||
            !value.acknowledgeOriginalPurposeDefects) F.invalid("purpose repair requires explicit actual Human direction")
        else Consequence.success(Action(request, value.sessionId, Some(value), port))
      }
  }
  private final case class Action(request: Request, sessionId: String, input: Option[V.GoalPhaseReviewPurposeRepairInput],
    port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(sessionId))
        selected = core.copy(executionContext = context)
        result <- input match {
          case None => port.describeReviewPurposeRepairC(sessionId, selected).map(NativeWorkflowOperationOutput.response(_))
          case Some(value) => port.admitReviewPurposeRepairC(value, selected).map(NativeWorkflowOperationOutput.response(_))
        }
      } yield result
    }
  }
  private def _payload_valid(value: Payload): Boolean = value != null && validateTargetC(value.target).isSuccess &&
    _reference(value.directive) && _exact(value.participant) && GoalPhasePurposeRepair.text(value.rationale) && value.acknowledgeOriginalPurposeDefects
  private def _keys(value: Json, keys: Set[String]): Boolean = value != null && value.asObject.exists(_.keys.toSet == keys)
  private def _finding(value: Finding): Boolean = value != null && value.findingOrdinal > 0 && value.findingOrdinal <= 128 &&
    GoalPhasePurposeRepair.text(value.text) && value.issueIdentity != null && value.issueIdentity.matches("issue-[1-9][0-9]{0,4}") &&
    value.issueIdentity.stripPrefix("issue-").toInt <= 16384
  private def _reference(value: ContextReference): Boolean = value != null && _exact(value.identity) && _exact(value.revision)
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n < Long.MaxValue && n.toString == value)
  private def _snapshot(value: O.Snapshot): Boolean = value != null && Vector(value.head, value.tree).forall(oid =>
    oid != null && oid.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})"))
  private def _exact(value: String): Boolean = GoalPhasePurposeRepair.text(value) && value.trim == value && !value.exists(_.isControl)
  private def _check(valid: Boolean, reason: String): Consequence[Unit] = if (valid) Consequence.unit else F.invalid(reason)
}
