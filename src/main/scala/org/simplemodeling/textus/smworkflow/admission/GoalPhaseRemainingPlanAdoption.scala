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
import GoalPhaseOrderedProgression as O
import GoalPhaseOrderedHumanDecision as H
import GoalPhasePlanRevision as R
import GoalPhaseDocumentSource as D

/** New Human direction for future work, separately from the unanswered old
  * question. All effects belong to the owning native guarded transaction.
  * @since   Oct. 20, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseRemainingPlanAdoption {
  val purpose = "goal-phase-remaining-plan-adoption"
  val describeOperation = "describeGoalPhaseRemainingPlan"
  val adoptionOperation = "adoptGoalPhaseRemainingPlan"
  val message = "Adopt only the remaining unaccepted work using an independently admitted actual Human directive. " +
    "The original unanswered question is retained as superseded-by-adoption, without an answer. " +
    "Accepted Steps, commits, reviews, base and repair history remain. Review continuation adds one required Step condition " +
    "for newly issued and future unaccepted work and consumes one ordinary repair. No check, Submit, Advance or commit runs here."

  final case class ReviewRepairTarget(issueIdentity: String, conditionIdentity: String,
    candidate: O.Snapshot, checksReceipt: String)
  object ReviewRepairTarget {
    given Encoder[ReviewRepairTarget] = deriveEncoder
    private val _decoder: Decoder[ReviewRepairTarget] = deriveDecoder
    given Decoder[ReviewRepairTarget] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (_valid_review_target(value) && cursor.value.asObject.exists(_.keys.toSet ==
          Set("issueIdentity", "conditionIdentity", "candidate", "checksReceipt"))) Right(value)
      else Left(DecodingFailure("Review repair target is incomplete", cursor.history))))
  }

  final case class Target(sessionId: String, questionIdentity: String, sourceRevision: String, pendingRevision: String,
    plan: O.Plan, base: O.Snapshot, completed: Vector[O.ClosedStep], generation: Long,
    repairProgress: GoalPhaseRepairProgress.Snapshot, requestingResult: ContextReference, captured: R.Captured,
    reviewRepair: Option[ReviewRepairTarget] = None)
  object Target {
    given Encoder[Target] = deriveEncoder[Target].mapJson(_.mapObject(fields =>
      if (fields("reviewRepair").exists(_.isNull)) fields.remove("reviewRepair") else fields))
    private val _decoder: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance { cursor =>
      if (cursor.downField("reviewRepair").focus.exists(_.isNull))
        Left(DecodingFailure("Review repair must be omitted or a valid target", cursor.history))
      else _decoder(cursor).flatMap(value => if (_validate_target_c(value).isSuccess) Right(value)
        else Left(DecodingFailure("Remaining Plan target is invalid", cursor.history)))
    }
  }
  final case class Payload(target: Target, directive: ContextReference, participant: String, rationale: String)
  final case class Selected(controller: O.State, record: InstanceRecord, documents: Option[D.Bound])
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else F.invalid("remaining Plan adoption requires an independent Human directive provider")
  }

  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def targetC(value: Record): Consequence[Target] = if (value == null) F.invalid("remaining Plan target is absent")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(_.as[Target].left.map(_.message))
      .fold(F.invalid(_), Consequence.success))

  def bindingC(component: String, target: Target, rationale: String, participant: String,
    reference: ContextReference): Consequence[Json] = for {
    _ <- if (_exact(component) && target != null && _text(rationale) && _exact(participant) &&
        reference != null && _exact(reference.identity) && _exact(reference.revision)) Consequence.unit
      else F.invalid("remaining Plan directive requires its actual target, participant, reference and rationale")
    _ <- _validate_target_c(target)
  } yield Json.obj("componentInstance" -> Json.fromString(component), "remainingPlanAdoption" -> Json.obj(
    "target" -> target.asJson, "directive" -> Json.obj("rationale" -> Json.fromString(rationale),
      "participant" -> Json.fromString(participant), "reference" -> Json.obj(
        "identity" -> Json.fromString(reference.identity), "revision" -> Json.fromString(reference.revision)))))

  def selectBinding(value: Json): Either[String, Json] = for {
    supplied <- Option(value).toRight("remaining Plan directive binding is absent")
    component <- supplied.hcursor.get[String]("componentInstance").left.map(_.message)
    target <- supplied.hcursor.downField("remainingPlanAdoption").get[Target]("target").left.map(_.message)
    directive = supplied.hcursor.downField("remainingPlanAdoption").downField("directive")
    rationale <- directive.get[String]("rationale").left.map(_.message)
    participant <- directive.get[String]("participant").left.map(_.message)
    identity <- directive.downField("reference").get[String]("identity").left.map(_.message)
    revision <- directive.downField("reference").get[String]("revision").left.map(_.message)
    binding <- bindingC(component, target, rationale, participant, ContextReference(identity, revision))
      .map[Either[String, Json]](Right(_)).fold(error => Left(error.toString), result => result)
  } yield binding

  private[admission] val codec: WorkflowResultJsonV1.PayloadCodec[Payload] = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseRemainingPlanAdoption"
    def encode(value: Payload): Json = value.asJson
    def decode(value: Json): Either[String, Payload] = for {
      supplied <- Option(value).toRight("remaining Plan adoption is absent")
      payload <- supplied.as[Payload].left.map(_.message)
      _ <- Either.cond(payload != null && payload.target != null && payload.directive != null &&
        _exact(payload.directive.identity) && _exact(payload.directive.revision) && _exact(payload.participant) && _text(payload.rationale),
        (), "remaining Plan adoption is incomplete")
      _ <- _validate_target_c(payload.target).map[Either[String, Unit]](_ => Right(())).fold(error => Left(error.toString), result => result)
    } yield payload
  }

  def describeC(source: H.Source, d: F.Declaration, core: ActionCall.Core, proposal: Record):
      Consequence[V.GoalPhaseRemainingPlanDescriptionResult] = for {
    target <- _target_c(source, d, core, proposal)
  } yield V.GoalPhaseRemainingPlanDescriptionResult(source.from.identity.value, "adoption-required", message,
    core.component.get.core.instanceId.canonicalKey, targetRecord(target), adoptionOperation).copy(message =
      target.reviewRepair.fold(message) { review =>
        val condition = target.captured.plan.acceptance.get.conditions.find(_.identity == review.conditionIdentity).get
        message + " Saved independent review " + source.result.resultReference.identity + "@" + source.result.resultReference.revision +
          ": " + source.result.result.value.summary + ". Finding " + review.issueIdentity + ": " + source.result.result.value.findings.head +
          ". Adopt required condition " + condition.identity + ": " + condition.description +
          ". scope=step applies to newly issued and future unaccepted ImplementStep, RepairStep and ReviewStep work; " +
          "historically accepted work keeps its original contract."
      })

  def adoptC(source: H.Source, d: F.Declaration, core: ActionCall.Core, input: V.GoalPhaseRemainingPlanAdoptionInput):
      Consequence[V.GoalPhaseRemainingPlanAdoptionResult] = for {
    supplied <- targetC(input.target)
    actual <- _target_c(source, d, core, input.proposal)
    _ <- if (input.sessionId == actual.sessionId && supplied == actual) Consequence.unit
      else F.invalid("remaining Plan adoption crosses its exact described target or captured contract")
    installation <- d.remainingPlanAdoption.map(Consequence.success).getOrElse(
      Consequence.securityPermissionDenied("remaining Plan adoption has no independent Human directive provider"))
    _ <- installation.validateC
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, actual, input.rationale,
      source.session.participantIdentity, reference)
    _ <- installation.evidence.admitC(core, reference, binding)
    state <- _adopted_c(source, actual, reference.identity, input.rationale)
    pending <- _pending_c(source)
    adapter <- source.session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec, codec)
    _ <- adapter.executeC(H.identity(source), pending.revision, pending,
      F.history(pending, "phase." + state.stage.toString, reference.identity),
      TypedValue(codec.typeIdentity, Payload(actual, reference, source.session.participantIdentity, input.rationale)))(using core.executionContext)
  } yield V.GoalPhaseRemainingPlanAdoptionResult(input.sessionId, true, "remaining-plan-adopted", message)

  /** Only persisted business input resumes the history walker; no directive is
    * recreated, and a retained receipt confers no additional write authority.
    */
  def retainedC(source: H.Source, core: ActionCall.Core): Consequence[Option[Selected]] = for {
    adapter <- source.session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec, codec)
    retained <- adapter.loadRetainedC(H.identity(source), source.session.current.identity)(using core.executionContext)
    selected <- retained match {
      case None => Consequence.success(Option.empty[Selected])
      case Some(value) => for {
        payload <- Consequence.success(value.receipt.payload)
        target = payload.target
        pending <- GoalPhaseNativeHistory.prefixC(source.session.current, value.receipt.sourceRevision)
        _ <- if (_matches(source, target, pending) && payload.participant == source.session.participantIdentity)
          Consequence.unit else F.invalid("retained remaining Plan adoption crosses its original actual source")
        _ <- validateDocumentsC(source.controller, target.captured, source.documents)
        review <- _review_target_c(source, target.captured.plan)
        _ <- if (review == target.reviewRepair) Consequence.unit
          else F.invalid("retained remaining Plan adoption crosses its actual Review issue, condition, candidate or checks")
        state <- _adopted_c(source, target, payload.directive.identity, payload.rationale)
        next <- GoalPhaseNativeHistory.completionC(source.session.current, pending, payload.directive, state.stage.toString)
      } yield Some(Selected(state, next, target.captured.documents))
    }
  } yield selected

  private def _target_c(source: H.Source, d: F.Declaration, core: ActionCall.Core, raw: Record): Consequence[Target] = for {
    pending <- _pending_c(source)
    proposal <- R.fromPublicProposalC(Some(raw)).flatMap(_.map(Consequence.success).getOrElse(F.invalid("remaining Plan proposal is absent")))
    plan <- if (source.controller.decisionSource.contains(O.Stage.Review)) for {
      condition <- O.reviewRepairConditionC(source.controller.plan, proposal.plan)
      // This explicit saved capture deliberately does not recapture current host
      // defaults. Every old effective condition, including its source, survives.
    } yield proposal.plan.copy(effectiveAcceptance = source.controller.plan.effectiveAcceptance.map(boundary =>
      boundary.copy(conditions = boundary.conditions :+ GoalPhaseAcceptanceBoundary.EffectiveCondition(
        condition.identity, condition.scope, condition.required, condition.description, "adopted"))))
      else GoalPhaseAcceptanceBoundary.captureC(d.projectAcceptance, proposal.plan)
    documents <- source.documents match {
      case None => Consequence.success(Option.empty[D.Bound])
      case Some(previous) => D.loadForCallC(previous.source, core).flatMap { imported =>
        if (imported.plan == proposal.plan) Consequence.success(Some(D.Bound(previous.source, imported.bindings, imported.items)))
        else F.invalid("remaining Plan proposal differs from the actual selected document Plan")
      }
    }
    captured = R.Captured(proposal, plan, documents)
    _ <- validateDocumentsC(source.controller, captured, source.documents)
    review <- _review_target_c(source, plan)
    target = Target(source.from.identity.value, H.identity(source).value, source.from.revision.value.toString,
      pending.revision.value.toString, source.controller.plan, source.controller.base, source.controller.completed,
      source.controller.generation, GoalPhaseRepairProgress.snapshot(source.controller), source.result.resultReference, captured, review)
    _ <- _adopted_c(source, target, "description-eligibility", "Read-only description eligibility")
    _ <- plan.steps.drop(source.controller.cursor).foldLeft(Consequence.unit) { (prior, step) =>
      prior.flatMap(_ => GoalPhaseNativeValidation.selectedChecksC(d, plan, Some(step.identity)).map(_ => ()))
    }
    questions <- source.session.storage.bindDecisionC(H.completionOperation, H.inputCodec)
    issued <- questions.loadC(H.identity(source))(using core.executionContext)
    _ <- if (issued.nonEmpty) Consequence.unit else F.invalid("remaining Plan adoption requires its actual issued unanswered question")
  } yield target

  private def _adopted_c(source: H.Source, target: Target, evidence: String, rationale: String): Consequence[O.State] =
    target.reviewRepair match {
      case Some(_) if source.controller.decisionSource.contains(O.Stage.Review) =>
        val result = source.result.result.value
        source.controller.adoptRemainingReviewRevisionC(target.captured.plan, evidence,
          O.RepairDirective(_single_line(result.summary + " / Human remaining Plan direction: " + rationale), result.findings))
      case None if !source.controller.decisionSource.contains(O.Stage.Review) =>
        source.controller.adoptRemainingRevisionC(target.captured.plan, evidence)
      case _ => F.invalid("remaining Plan adoption requires Review repair metadata exactly on the Review branch")
    }

  /** Uses the current reconstructed ledger supplied by the walker, rather than
    * the issue set captured before this independent review produced its finding.
    */
  private[admission] def reviewRepairTargetC(controller: O.State, input: GoalPhaseNativeWork.Request,
    result: GoalPhaseNativeWork.Result, reference: ContextReference, proposed: O.Plan): Consequence[ReviewRepairTarget] = for {
    _ <- if (controller != null && input != null && result != null && reference != null &&
        _exact(reference.identity) && _exact(reference.revision) && input.kind == "ReviewStep" &&
        controller.stage == O.Stage.Decision && controller.decisionSource.contains(O.Stage.Review) &&
        controller.cursor < controller.plan.steps.size && input.stepIdentity.contains(controller.plan.steps(controller.cursor).identity) &&
        input.selectedPlan == controller.plan && input.originalBase == controller.base && input.completed == controller.completed &&
        input.generation == controller.generation && input.candidate == controller.candidate &&
        input.checksReceipt == controller.checksReceipt && input.candidate.nonEmpty && input.checksReceipt.exists(_exact) &&
        input.review.exists(_ != null) && input.checkFailure.isEmpty &&
        result.outcome == "decision-needed" && _text(result.summary) && result.findings != null &&
        result.findings.size == 1 && result.findings.forall(_text) && result.planRevision.isEmpty &&
        result.dependencyProposal.isEmpty) Consequence.unit
      else F.invalid("Review continuation requires its actual current issued ReviewStep and single decision-needed finding")
    _ <- GoalPhaseAcceptanceBoundary.admitC(controller.plan, input.kind, result)
    _ <- GoalPhaseFixIssues.validateOptionC(input.issues)
    condition <- O.reviewRepairConditionC(controller.plan, proposed.copy(effectiveAcceptance = None))
    matching = input.issues.toVector.flatMap(_.issues).filter(issue =>
      issue.source == "REVIEW" && issue.status == "OPEN" && issue.repository == controller.plan.repository &&
        input.stepIdentity.contains(issue.step) && issue.evidenceReference == reference.identity &&
        issue.summary == result.findings.head)
    _ <- if (matching.size == 1) Consequence.unit
      else F.invalid("Review continuation requires exactly one corresponding OPEN actual REVIEW issue")
    target = ReviewRepairTarget(matching.head.identity, condition.identity, input.candidate.get, input.checksReceipt.get)
    _ <- controller.adoptRemainingReviewRevisionC(proposed, "review-target-eligibility",
      O.RepairDirective(_single_line(result.summary), result.findings))
  } yield target

  private def _review_target_c(source: H.Source, proposed: O.Plan): Consequence[Option[ReviewRepairTarget]] =
    if (!source.controller.decisionSource.contains(O.Stage.Review)) Consequence.success(None)
    else for {
      _ <- if (source.sourceHistoryRecovery.isEmpty && source.input.instanceIdentity == source.from.identity.value &&
          source.result.result.typeIdentity == GoalPhaseNativeWork.resultCodec.typeIdentity &&
          source.candidate.exists(saved => saved.payload.candidate == source.controller.candidate &&
            source.input.stepIdentity.contains(saved.payload.step))) Consequence.unit
        else F.invalid("Review continuation crosses its native source, candidate or recovery boundary")
      target <- reviewRepairTargetC(source.controller, source.input, source.result.result.value, source.result.resultReference, proposed)
    } yield Some(target)

  private def _validate_target_c(target: Target): Consequence[Unit] = for {
    _ <- if (target != null && target.reviewRepair != null && target.reviewRepair.forall(_valid_review_target)) Consequence.unit
      else F.invalid("remaining Plan target is incomplete")
    _ <- R.validateCapturedC(target.captured)
    _ <- target.reviewRepair.fold(Consequence.unit) { review => for {
      condition <- O.reviewRepairConditionC(target.plan, target.captured.proposal.plan)
      _ <- if (condition.identity == review.conditionIdentity) Consequence.unit
        else F.invalid("Review repair target names a different added condition")
    } yield () }
  } yield ()

  private def _valid_review_target(value: ReviewRepairTarget): Boolean = value != null &&
    value.issueIdentity != null && value.issueIdentity.matches("issue-[1-9][0-9]{0,4}") &&
    value.issueIdentity.stripPrefix("issue-").toInt <= 16384 &&
    value.conditionIdentity != null && value.conditionIdentity.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}") &&
    value.candidate != null && Vector(value.candidate.head, value.candidate.tree).forall(oid =>
      oid != null && oid.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) && _exact(value.checksReceipt)

  private def _single_line(value: String): String = value.replace('\r', ' ').replace('\n', ' ')

  private def _pending_c(source: H.Source): Consequence[InstanceRecord] = for {
    following <- GoalPhaseNativeHistory.nextC(source.session.current, source.from)
    entry <- following match {
      case Some(value) if value.revision == source.session.current.revision && value.suspension.exists(boundary =>
          boundary.continuationIdentity.value == H.identity(source).value &&
          boundary.completion.value == "goal-phase-ordered-human-completion-v1") => Consequence.success(value)
      case _ => F.invalid("remaining Plan adoption requires the exact current unanswered Human frontier")
    }
    pending <- GoalPhaseNativeHistory.prefixC(source.session.current, entry.revision)
  } yield pending

  private def _matches(source: H.Source, target: Target, pending: InstanceRecord): Boolean = target != null &&
    target.sessionId == source.from.identity.value && target.questionIdentity == H.identity(source).value &&
    target.sourceRevision == source.from.revision.value.toString && target.pendingRevision == pending.revision.value.toString &&
    target.plan == source.controller.plan && target.base == source.controller.base && target.completed == source.controller.completed &&
    target.generation == source.controller.generation && target.repairProgress == GoalPhaseRepairProgress.snapshot(source.controller) &&
    target.requestingResult == source.result.resultReference

  private[admission] def validateDocumentsC(controller: O.State, captured: R.Captured, previous: Option[D.Bound]): Consequence[Unit] = for {
    _ <- R.validateCapturedC(captured)
    _ <- (previous, captured.documents) match {
      case (None, None) => Consequence.unit
      case (Some(prior), Some(selected)) =>
        val editable = prior.bindings.drop(controller.cursor).flatMap(_.items).toSet
        if (prior.source == selected.source && prior.bindings == selected.bindings &&
            prior.items.size == selected.items.size && prior.items.forall(item => selected.items.find(_.identity == item.identity).exists(next =>
              next.checked == item.checked && (if (editable(item.identity) && !item.checked) true else next == item)))) Consequence.unit
        else F.invalid("remaining Plan adoption changes document source, membership, checked flags or accepted item content")
      case _ => F.invalid("remaining Plan adoption changes selected document presence")
    }
  } yield ()

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseRemainingPlanOperation
      else SmWorkflowComponent.GoalPhaseServiceService.AdoptGoalPhaseRemainingPlanOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") ||
          request.operation != (if (describe) describeOperation else adoptionOperation)) F.invalid("remaining Plan operation identity differs")
      else if (describe) NativeWorkflowOperationInput.valueC[V.GoalPhaseRemainingPlanDescriptionInput](request, "input")
        .map(value => Action(request, value.sessionId, Some(value), None, port))
      else NativeWorkflowOperationInput.valueC[V.GoalPhaseRemainingPlanAdoptionInput](request, "input").flatMap { value =>
        if (value == null || !_exact(value.sessionId) || value.reference == null || !_exact(value.reference.identity) ||
            !_exact(value.reference.revision) || !_text(value.rationale)) F.invalid("remaining Plan adoption requires actual Human direction")
        else Consequence.success(Action(request, value.sessionId, None, Some(value), port))
      }
  }
  private final case class Action(request: Request, sessionId: String, description: Option[V.GoalPhaseRemainingPlanDescriptionInput],
    adoption: Option[V.GoalPhaseRemainingPlanAdoptionInput], port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(sessionId))
        selected = core.copy(executionContext = context)
        result <- description match {
          case Some(value) => port.describeRemainingPlanC(value, selected).map(NativeWorkflowOperationOutput.response(_))
          case None => port.adoptRemainingPlanC(adoption.get, selected).map(NativeWorkflowOperationOutput.response(_))
        }
      } yield result
    }
  }
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536
}
