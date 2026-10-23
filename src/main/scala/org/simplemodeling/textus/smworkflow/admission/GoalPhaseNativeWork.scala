package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Encoder, Decoder, DecodingFailure, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeRun as F

/** Assigned semantic payload only. Native receipts and permissions are never
  * decoded from an AI reply. Other work kinds will be selected by owning advance
  * only after their actual native predecessor has been connected.
  * @since   Oct.  6, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeWork {
  final case class CheckFailureContext(changedPaths: Vector[String], diff: String,
    validation: GoalPhaseNativeValidation.Payload)
  final case class Request(instanceIdentity: String, sourceRevision: String, kind: String,
    stepIdentity: Option[String], generation: Long, purpose: String, selectedPlan: O.Plan,
    originalBase: O.Snapshot, completed: Vector[O.ClosedStep], candidate: Option[O.Snapshot],
    checksReceipt: Option[String], repairDirective: Option[O.RepairDirective],
    review: Option[GoalPhaseNativeReview.Context] = None,
    decisionContext: Option[DecisionContext] = None,
    documents: Option[GoalPhaseDocumentSource.Bound] = None,
    checkFailure: Option[CheckFailureContext] = None,
    issues: Option[GoalPhaseFixIssues.State] = None,
    repairProgress: Option[GoalPhaseRepairProgress.Snapshot] = None)
  final case class Result(outcome: String, summary: String, findings: Vector[String],
    acceptance: Option[GoalPhaseAcceptanceBoundary.Report] = None,
    fixAssessment: Option[GoalPhaseFixAssessment.Report] = None,
    issueReport: Option[GoalPhaseFixIssues.Report] = None,
    planRevision: Option[GoalPhasePlanRevision.Proposal] = None,
    reviewObservations: Option[GoalPhaseFindingRecording.Observations] = None,
    dependencyProposal: Option[GoalPhaseDependencyScope.Proposal] = None,
    completionConditions: Option[GoalPhaseNativeReviewCompletion.Conditions] = None)
  object Result {
    given Encoder[Result] = deriveEncoder[Result].mapJson(_.mapObject(_.filter {
      case ("acceptance" | "fixAssessment" | "issueReport" | "planRevision" | "reviewObservations" | "dependencyProposal" | "completionConditions", value) => !value.isNull
      case _ => true
    }))
    private val _decoder: Decoder[Result] = deriveDecoder
    given Decoder[Result] = Decoder.instance { cursor =>
      if (Vector("fixAssessment", "issueReport", "planRevision", "reviewObservations", "dependencyProposal", "completionConditions").exists(key => cursor.downField(key).focus.exists(_.isNull)))
        Left(DecodingFailure("Fix reports must be absent or valid objects", cursor.history))
      else _decoder.tryDecode(cursor).flatMap { result =>
        if (GoalPhaseFixAssessment.validateOptionC(result.fixAssessment).isSuccess &&
            GoalPhaseFixIssues.validateReportOptionC(result.issueReport).isSuccess &&
            GoalPhasePlanRevision.validateOptionC(result.planRevision).isSuccess &&
            GoalPhaseFindingRecording.validateObservationsOptionC(result.reviewObservations).isSuccess &&
            GoalPhaseDependencyScope.validateOptionC(result.dependencyProposal).isSuccess &&
            GoalPhaseNativeReviewCompletion.validateOptionC(result.completionConditions).isSuccess &&
            (result.planRevision.isEmpty || result.dependencyProposal.isEmpty)) Right(result)
        else Left(DecodingFailure("Invalid Fix assessment", cursor.history))
      }
    }
  }
  final case class DecisionContext(requestingResult: Result, choice: String, rationale: String,
    decisionReference: Option[ContextReference] = None)

  // Review Context and validation Payload use their owning companion codecs,
  // including the optional retained metadata defaults. Derive request-only
  // wrappers bottom-up to bound expansion at the actual receipt vectors.
  private given Encoder[DecisionContext] = deriveEncoder[DecisionContext].mapJson(_.mapObject(fields =>
    if (fields("decisionReference").exists(_.isNull)) fields.remove("decisionReference") else fields))
  private val _decision_decoder: Decoder[DecisionContext] = deriveDecoder
  private given Decoder[DecisionContext] = Decoder.instance { cursor =>
    if (cursor.downField("decisionReference").focus.exists(_.isNull))
      Left(DecodingFailure("Decision reference must be absent or valid", cursor.history))
    else _decision_decoder(cursor).flatMap { value =>
      val validreference = value.decisionReference.forall(reference => reference != null &&
        Vector(reference.identity, reference.revision).forall(text => _bounded_text(text) && !text.exists(_.isControl)))
      val dependency = Set("ApproveDependencyScope", "RejectDependencyScope")(value.choice)
      if (validreference && (!dependency || (value.decisionReference.nonEmpty && value.requestingResult != null &&
          value.requestingResult.dependencyProposal.nonEmpty && value.requestingResult.planRevision.isEmpty &&
          value.requestingResult.outcome == "decision-needed" &&
          GoalPhaseDependencyScope.validateOptionC(value.requestingResult.dependencyProposal).isSuccess))) Right(value)
      else Left(DecodingFailure("Dependency direction requires its proposal and actual answer reference", cursor.history))
    }
  }
  private given Encoder[CheckFailureContext] = deriveEncoder
  private given Decoder[CheckFailureContext] = deriveDecoder

  // Derive the nested document payload bottom-up to keep whole-Request
  // derivation within Scala's inline depth without changing the wire fields.
  private given Encoder[GoalPhaseDocumentSource.Source] = deriveEncoder
  private given Decoder[GoalPhaseDocumentSource.Source] = deriveDecoder
  private given Encoder[GoalPhaseDocumentPlan.Binding] = deriveEncoder
  private given Decoder[GoalPhaseDocumentPlan.Binding] = deriveDecoder
  private given Encoder[GoalPhaseDocumentPlan.Item] = deriveEncoder
  private given Decoder[GoalPhaseDocumentPlan.Item] = deriveDecoder
  private given Encoder[GoalPhaseDocumentSource.Bound] = deriveEncoder
  private given Decoder[GoalPhaseDocumentSource.Bound] = deriveDecoder
  private given Encoder[Request] = deriveEncoder
  private val _request_decoder: Decoder[Request] = deriveDecoder
  private given Decoder[Request] = Decoder.instance { cursor =>
    if (Vector("checkFailure", "issues", "repairProgress").exists(key => cursor.downField(key).focus.exists(_.isNull)))
      Left(DecodingFailure("Check failure context must be absent or a valid object", cursor.history))
    else _request_decoder.tryDecode(cursor).flatMap { request =>
      if (request.checkFailure.forall(value => admitCheckFailureContextC(value).isSuccess) &&
          GoalPhaseFixIssues.validateOptionC(request.issues).isSuccess &&
          GoalPhaseRepairProgress.validateOptionC(request.repairProgress).isSuccess) Right(request)
      else Left(DecodingFailure("Invalid retained check failure context", cursor.history))
    }
  }

  def checkFailureContextC(changedPaths: Vector[String], diff: String,
    validation: GoalPhaseNativeValidation.Payload): Consequence[CheckFailureContext] = {
    val value = CheckFailureContext(changedPaths, diff, validation)
    admitCheckFailureContextC(value).map(_ => value)
  }

  def admitCheckFailureContextC(value: CheckFailureContext): Consequence[Unit] =
    if (value != null && value.changedPaths != null && value.changedPaths.nonEmpty &&
        value.changedPaths.forall(_bounded_text) && _context_text(value.diff) &&
        value.validation != null && value.validation.outcome != null &&
        value.validation.outcome.exists(Set("repair-required", "repair-limit-reached")) &&
        GoalPhaseNativeValidation.repairable(value.validation))
      Consequence.unit else F.invalid("repair requires its retained actual selected check failure context")

  def initialC(source: F.Session): Consequence[Request] = for {
    state <- O.startC(source.started.receipt.payload.value.plan, source.started.receipt.payload.value.base)
    semantic <- state.next match {
      case value: O.Semantic => Consequence.success(value)
      case _ => F.invalid("original selected plan has no initial semantic work")
    }
  } yield Request(source.current.identity.value, source.started.receipt.next.revision.value.toString,
    semantic.key.kind.toString, semantic.key.step, semantic.key.generation, semantic.purpose,
    semantic.selectedPlan, semantic.originalBase, semantic.completed, semantic.candidate,
    semantic.checksReceipt, semantic.repairDirective,
    documents = source.started.receipt.payload.value.documents,
    repairProgress = Some(GoalPhaseRepairProgress.snapshot(state)))

  def requiredC: Consequence[StateMachineRequiredOperation] = requiredC("ImplementStep")
  def requiredC(kind: String): Consequence[StateMachineRequiredOperation] = {
    val (capability, contract) = kind match {
      case "ImplementStep" => ("goal-phase-implementation-capability", "implementation")
      case "ReviewStep" => ("goal-phase-step-review-capability", "step-review")
      case "RepairStep" => ("goal-phase-repair-capability", "repair")
      case "ReviewPhase" => ("goal-phase-review-capability", "phase-review")
      case _ => return F.invalid("semantic work kind has no connected Required SPI")
    }
    val found = domain.statemachine.workflow.GoalPhaseWorkflowStateMachineWorkflow1.workflow.requiredOperations
      .filter(_.identity.capability == capability)
    if (found.size == 1) {
      val spi = found.head
      Consequence.success(StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.identity.capability),
        spi.actionIdentity, StateMachineOperationIdentity(spi.operation.service, spi.operation.operation),
        spi.inputType.map(v => StateMachineInputTypeReference(v.value)),
        spi.resultType.map(v => StateMachineResultTypeReference(v.value)),
        StateMachineRequiredOperationMetadata(
          ContextContract("goal-phase-ordered-" + contract + "-context-v1", Vector.empty, Vector.empty),
          CompletionContract("goal-phase-ordered-" + contract + "-completion-v1", Vector.empty),
          EvidenceContract("goal-phase-ordered-" + contract + "-evidence-v1", Vector.empty), Vector.empty)))
    }
    else F.invalid("initial work differs from actual CML Required SPI")
  }
  def admitInitialResultC(request: Request, result: Result): Consequence[Unit] =
    if (request == null || !GoalPhaseRepairProgress.validateOptionC(request.repairProgress).isSuccess || result == null || request.kind != "ImplementStep" || request.review == null || request.review.nonEmpty ||
        request.checkFailure == null || request.checkFailure.nonEmpty ||
        result.completionConditions == null || result.completionConditions.nonEmpty ||
        !_bounded_text(result.summary) || result.findings == null || result.findings.size > 128 ||
        !result.findings.forall(_bounded_text) ||
        !Set("completed", "decision-needed")(result.outcome) ||
        (result.outcome == "completed" && result.findings.nonEmpty))
      F.invalid("initial assigned result must report completion or an explicit decision, never review/command/closure")
    else for {
      _ <- GoalPhaseFixIssues.admitC(request, result)
      _ <- GoalPhaseFindingRecording.admitObservationsC(request.kind, result.reviewObservations)
      _ <- _plan_revision_c(result)
      _ <- GoalPhaseDependencyScope.admitResultC(request.kind, result.outcome, result.dependencyProposal)
      _ <- GoalPhaseFixAssessment.admitC(request.kind, result.outcome, result.fixAssessment)
      _ <- GoalPhaseAcceptanceBoundary.admitC(request.selectedPlan, request.kind, result)
    } yield ()

  def admitSemanticResultC(request: Request, result: Result): Consequence[Unit] =
    if (request == null || !GoalPhaseRepairProgress.validateOptionC(request.repairProgress).isSuccess)
      F.invalid("assigned request or progress is invalid")
    else if (request.kind == "ImplementStep") admitInitialResultC(request, result)
    else if (request.kind != "RepairStep" || request.repairDirective == null ||
        !request.repairDirective.exists(value => value != null && _bounded_text(value.reason) &&
          value.findings != null && value.findings.nonEmpty && value.findings.forall(_bounded_text)) ||
        !_repair_source(request) ||
        request.candidate.nonEmpty || request.checksReceipt.nonEmpty || result == null ||
        result.completionConditions == null || result.completionConditions.nonEmpty ||
        !_bounded_text(result.summary) || result.findings == null || result.findings.size > 128 ||
        !result.findings.forall(_bounded_text) ||
        !Set("completed", "decision-needed")(result.outcome) ||
        (result.outcome == "completed" && result.findings.nonEmpty))
      F.invalid("assigned repair must return semantics or a decision, not review, checks, commands or closure")
    else for {
      _ <- GoalPhaseFixIssues.admitC(request, result)
      _ <- GoalPhaseFindingRecording.admitObservationsC(request.kind, result.reviewObservations)
      _ <- _plan_revision_c(result)
      _ <- GoalPhaseDependencyScope.admitResultC(request.kind, result.outcome, result.dependencyProposal)
      _ <- GoalPhaseFixAssessment.admitC(request.kind, result.outcome, result.fixAssessment)
      _ <- GoalPhaseAcceptanceBoundary.admitC(request.selectedPlan, request.kind, result)
    } yield ()

  private def _plan_revision_c(result: Result): Consequence[Unit] = for {
    _ <- GoalPhasePlanRevision.validateOptionC(result.planRevision)
    _ <- GoalPhaseDependencyScope.validateOptionC(result.dependencyProposal)
    _ <- if (result.planRevision.isEmpty || result.dependencyProposal.isEmpty) Consequence.unit
      else F.invalid("Only one proposal kind may request a decision")
    _ <- if (result.planRevision.isEmpty || result.outcome == "decision-needed") Consequence.unit
      else F.invalid("Plan revision requires an ordinary implementation or repair decision-needed result")
  } yield ()

  private def _repair_source(request: Request): Boolean = request.review != null && request.checkFailure != null &&
    ((request.review.exists(value => value != null && value.changedPaths != null && value.changedPaths.nonEmpty &&
        value.changedPaths.forall(_bounded_text) && _context_text(value.diff) && value.checks != null &&
        value.checks.forall(_ != null)) && request.checkFailure.isEmpty) ||
      (request.review.isEmpty && request.checkFailure.exists(value => admitCheckFailureContextC(value).isSuccess &&
        request.stepIdentity != null && request.stepIdentity.getOrElse("phase:" + request.selectedPlan.phase) == value.validation.step)))

  private def _bounded_text(value: String): Boolean = value != null && value.trim.nonEmpty &&
    !value.contains('\u0000') && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 16384
  private def _context_text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000')

  private class Codec[A](val typeIdentity: String, schema: String)(using Encoder[A], Decoder[A])
      extends WorkflowResultJsonV1.PayloadCodec[A] {
    def encode(value: A) = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, A] = for {
      wire <- Option(value).toRight("native ordered work is missing")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(version == schema, (), "native ordered work schema differs")
      parsed <- wire.hcursor.get[A]("payload").left.map(_.message)
    } yield parsed
  }
  val inputCodec: WorkflowResultJsonV1.PayloadCodec[Request] =
    new Codec[Request]("GoalPhaseWorkRequest", "sm-workflow.goal-phase-native-work-request.v2") {
      override def encode(value: Request): Json = {
        val payload = value.asJson.mapObject { fields =>
          val review = if (value.review.isEmpty) fields.remove("review") else fields
          val decision = if (value.decisionContext.isEmpty) review.remove("decisionContext") else review
          val documents = if (value.documents.isEmpty) decision.remove("documents") else decision
          val checks = if (value.checkFailure.isEmpty) documents.remove("checkFailure") else documents
          val issues = if (value.issues.isEmpty) checks.remove("issues") else checks
          if (value.repairProgress.isEmpty) issues.remove("repairProgress") else issues
        }
        Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-work-request.v2"),
          "payload" -> payload)
      }
    }
  val resultCodec: WorkflowResultJsonV1.PayloadCodec[Result] =
    new Codec[Result]("GoalPhaseWorkResult", "sm-workflow.goal-phase-native-work-result.v1")

  def providedDefinition: GeneratedProvidedApiAbi.Definition = {
    val p = domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1.workflow
    GeneratedProvidedApiAbi.Definition(p.schemaVersion, p.generator, p.identity, p.version,
      GeneratedProvidedApiAbi.WorkflowSourceCorrelation(GeneratedProvidedApiAbi.SourceIdentity(p.source.root.line),
        GeneratedProvidedApiAbi.SourceIdentity(p.source.definition.line)),
      p.providedOperations.map(o => GeneratedProvidedApiAbi.Operation(o.service, o.name, o.inputType, o.resultType,
        GeneratedProvidedApiAbi.SourceIdentity(o.source.line))))
  }
}
