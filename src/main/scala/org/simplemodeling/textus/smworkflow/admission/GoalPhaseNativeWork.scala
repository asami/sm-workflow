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
import GoalPhaseDependencyValidation.given
import IncrementalSbtDiagnosisRepair.given

/** Assigned semantic payload only. Native receipts and permissions are never
  * decoded from an AI reply. Other work kinds will be selected by owning advance
  * only after their actual native predecessor has been connected.
  * @since   Oct.  6, 2026
  *  version Oct. 29, 2026
  * @version Nov.  7, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeWork {
  final case class CheckFailureContext(changedPaths: Vector[String], diff: String,
    validation: GoalPhaseNativeValidation.Payload, initialPhaseRepair: Option[ContextReference] = None,
    stepTestReportRepair: Option[ContextReference] = None)
  final case class PublicationFailureContext(repair: ContextReference, changedPaths: Vector[String], diff: String,
    publication: LocalPublication.Result, testsExecuted: Boolean)
  object PublicationFailureContext {
    private def _public(value: Json): Json = value.arrayOrObject(value,
      xs => Json.fromValues(xs.map(_public)), fields => Json.fromFields(fields.toVector.collect {
        case (key, item) if !item.isNull => key -> _public(item)
      }))
    given Encoder[PublicationFailureContext] = deriveEncoder[PublicationFailureContext].mapJson(_public)
    private val _decoder: Decoder[PublicationFailureContext] = deriveDecoder
    given Decoder[PublicationFailureContext] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (publicationFailureValid(value) && value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Publication repair requires its actual retained failure; tests were not executed", cursor.history))))
  }
  final case class Request(instanceIdentity: String, sourceRevision: String, kind: String,
    stepIdentity: Option[String], generation: Long, purpose: String, selectedPlan: O.Plan,
    originalBase: O.Snapshot, completed: Vector[O.ClosedStep], candidate: Option[O.Snapshot],
    checksReceipt: Option[String], repairDirective: Option[O.RepairDirective],
    review: Option[GoalPhaseNativeReview.Context] = None,
    decisionContext: Option[DecisionContext] = None,
    documents: Option[GoalPhaseDocumentSource.Bound] = None,
    checkFailure: Option[CheckFailureContext] = None,
    issues: Option[GoalPhaseFixIssues.State] = None,
    repairProgress: Option[GoalPhaseRepairProgress.Snapshot] = None,
    purposeRepair: Option[GoalPhasePurposeRepair.Contract] = None,
    releaseRepairContract: Option[String] = None,
    triage: Option[GoalPhaseStepTriage.Context] = None,
    diagnosticHistory: Option[Vector[IncrementalSbtDiagnosisRepair.Prior]] = None,
    operatingProfile: Option[GoalPhaseOperatingProfiles.Capture] = None,
    publicationFailure: Option[PublicationFailureContext] = None)
  final case class Result(outcome: String, summary: String, findings: Vector[String],
    acceptance: Option[GoalPhaseAcceptanceBoundary.Report] = None,
    fixAssessment: Option[GoalPhaseFixAssessment.Report] = None,
    issueReport: Option[GoalPhaseFixIssues.Report] = None,
    planRevision: Option[GoalPhasePlanRevision.Proposal] = None,
    reviewObservations: Option[GoalPhaseFindingRecording.Observations] = None,
    dependencyProposal: Option[GoalPhaseDependencyScope.Proposal] = None,
    completionConditions: Option[GoalPhaseNativeReviewCompletion.Conditions] = None,
    repairFindingSources: Option[Vector[GoalPhasePurposeRepair.Source]] = None,
    dependencyValidation: Option[GoalPhaseDependencyValidation.Selection] = None,
    dependencyAdmission: Option[GoalPhaseDependencyValidation.Admission] = None,
    diagnosticRepair: Option[IncrementalSbtDiagnosisRepair.Manifest] = None)
  object Result {
    given Encoder[Result] = deriveEncoder[Result].mapJson(_.mapObject(_.filter {
      case ("acceptance" | "fixAssessment" | "issueReport" | "planRevision" | "reviewObservations" | "dependencyProposal" | "completionConditions" | "repairFindingSources" | "dependencyValidation" | "dependencyAdmission" | "diagnosticRepair", value) => !value.isNull
      case _ => true
    }))
    private val _decoder: Decoder[Result] = deriveDecoder
    given Decoder[Result] = Decoder.instance { cursor =>
      if (Vector("fixAssessment", "issueReport", "planRevision", "reviewObservations", "dependencyProposal", "completionConditions", "repairFindingSources", "dependencyValidation", "dependencyAdmission", "diagnosticRepair").exists(key => cursor.downField(key).focus.exists(_.isNull)))
        Left(DecodingFailure("Fix reports must be absent or valid objects", cursor.history))
      else _decoder.tryDecode(cursor).flatMap { result =>
        if (IncrementalSbtDiagnosisRepair.validateOptionC(result.diagnosticRepair).isSuccess &&
            GoalPhaseFixAssessment.validateOptionC(result.fixAssessment).isSuccess &&
            GoalPhaseFixIssues.validateReportOptionC(result.issueReport).isSuccess &&
            GoalPhasePlanRevision.validateOptionC(result.planRevision).isSuccess &&
            GoalPhaseFindingRecording.validateObservationsOptionC(result.reviewObservations).isSuccess &&
            GoalPhaseDependencyScope.validateOptionC(result.dependencyProposal).isSuccess &&
            GoalPhaseDependencyValidation.validateSelectionC(result.dependencyValidation).isSuccess &&
            result.dependencyAdmission != null && result.dependencyAdmission.forall(GoalPhaseDependencyValidation.admissionValid) &&
            _dependency_selection_valid(result) &&
            GoalPhaseNativeReviewCompletion.validateOptionC(result.completionConditions).isSuccess &&
            GoalPhasePurposeRepair.validateSourcesC(result.repairFindingSources).isSuccess &&
            (result.repairFindingSources.isEmpty || (result.outcome == "repair" && result.findings != null &&
              result.findings.size <= 128 && result.findings.forall(GoalPhasePurposeRepair.text) &&
              result.repairFindingSources.exists(_.size == result.findings.size))) &&
            (result.planRevision.isEmpty || result.dependencyProposal.isEmpty)) Right(result)
        else Left(DecodingFailure("Invalid Fix assessment", cursor.history))
      }
    }
  }
  final case class DecisionContext(requestingResult: Result, choice: String, rationale: String,
    decisionReference: Option[ContextReference] = None,
    inventoryContinuation: Option[GoalPhaseDependencyInventoryContinuation.InventoryContext] = None)

  // Review Context and validation Payload use their owning companion codecs,
  // including the optional retained metadata defaults. Derive request-only
  // wrappers bottom-up to bound expansion at the actual receipt vectors.
  private given Encoder[DecisionContext] = deriveEncoder[DecisionContext].mapJson(_.mapObject(_.filter {
    case ("decisionReference" | "inventoryContinuation", value) => !value.isNull
    case _ => true
  }))
  private val _decision_decoder: Decoder[DecisionContext] = deriveDecoder
  private given Decoder[DecisionContext] = Decoder.instance { cursor =>
    if (Vector("decisionReference", "inventoryContinuation").exists(key => cursor.downField(key).focus.exists(_.isNull)) ||
        !cursor.value.asObject.exists(fields => fields.keys.toSet ==
          (Set("requestingResult", "choice", "rationale") ++
            Vector("decisionReference", "inventoryContinuation").filter(key => cursor.downField(key).focus.nonEmpty))))
      Left(DecodingFailure("Decision reference must be absent or valid", cursor.history))
    else _decision_decoder(cursor).flatMap { value =>
      val validreference = value.decisionReference.forall(reference => reference != null &&
        Vector(reference.identity, reference.revision).forall(text => _bounded_text(text) && !text.exists(_.isControl)))
      val dependency = Set("ApproveDependencyScope", "RejectDependencyScope")(value.choice)
      val inventory = value.inventoryContinuation.forall(context =>
        GoalPhaseDependencyInventoryContinuation.contextValid(context) && value.choice == "ApproveDependencyScope" &&
          value.decisionReference.contains(context.approval) && value.requestingResult != null &&
          value.requestingResult.dependencyProposal.exists(original =>
            GoalPhaseDependencyInventoryContinuation.assessC("context", context.proposal,
              Vector(GoalPhaseDependencyValidation.Approval("context", original, context.approval))).map {
                case GoalPhaseDependencyInventoryContinuation.WithinBoundary(_) => true
                case _ => false
              }.fold(_ => false, identity)))
      if (validreference && inventory && (!dependency || (value.decisionReference.nonEmpty && value.requestingResult != null &&
          value.requestingResult.dependencyProposal.nonEmpty && value.requestingResult.planRevision.isEmpty &&
          value.requestingResult.outcome == "decision-needed" &&
          GoalPhaseDependencyScope.validateOptionC(value.requestingResult.dependencyProposal).isSuccess))) Right(value)
      else Left(DecodingFailure("Dependency direction requires its proposal and actual answer reference", cursor.history))
    }
  }
  private given Encoder[CheckFailureContext] = deriveEncoder[CheckFailureContext].mapJson(_.mapObject(fields =>
    Vector("initialPhaseRepair", "stepTestReportRepair").foldLeft(fields)((prior, key) =>
      if (prior(key).exists(_.isNull)) prior.remove(key) else prior)))
  private val _check_failure_decoder: Decoder[CheckFailureContext] = deriveDecoder
  private given Decoder[CheckFailureContext] = Decoder.instance { cursor =>
    if (Vector("initialPhaseRepair", "stepTestReportRepair").exists(key => cursor.downField(key).focus.exists(_.isNull)))
      Left(DecodingFailure("Native repair references must be absent or valid", cursor.history))
    else _check_failure_decoder.tryDecode(cursor).flatMap(value =>
      if (admitCheckFailureContextC(value).isSuccess) Right(value)
      else Left(DecodingFailure("Invalid actual check failure context", cursor.history)))
  }

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
  private given Encoder[Request] = deriveEncoder[Request].mapJson(_.mapObject(_.filter {
    case ("operatingProfile" | "publicationFailure", value) => !value.isNull
    case _ => true
  }))
  private val _request_decoder: Decoder[Request] = deriveDecoder
  private given Decoder[Request] = Decoder.instance { cursor =>
    if (Vector("checkFailure", "issues", "repairProgress", "purposeRepair", "releaseRepairContract", "triage", "diagnosticHistory", "operatingProfile", "publicationFailure").exists(key => cursor.downField(key).focus.exists(_.isNull)))
      Left(DecodingFailure("Check failure context must be absent or a valid object", cursor.history))
    else _request_decoder.tryDecode(cursor).flatMap { request =>
      def _invalid_(field: String): Decoder.Result[Request] =
        Left(DecodingFailure("Invalid retained check failure context [field=" + field + "]", cursor.history))
      if (!_publication_request_valid(request)) _invalid_("publicationFailure")
      else if (request.operatingProfile == null || !request.operatingProfile.forall(GoalPhaseOperatingProfiles.captureValid))
        _invalid_("operatingProfile")
      else if (!IncrementalSbtDiagnosisRepair.historyValid(request.diagnosticHistory)) _invalid_("diagnosticHistory")
      else if (!request.checkFailure.forall(value => admitCheckFailureContextC(value).isSuccess)) _invalid_("checkFailure")
      else if (!GoalPhaseFixIssues.validateOptionC(request.issues).isSuccess) _invalid_("issues")
      else if (!GoalPhaseRepairProgress.validateOptionC(request.repairProgress).isSuccess) _invalid_("repairProgress")
      else if (!GoalPhasePurposeRepair.validateRequestC(request).isSuccess) _invalid_("purposeRepair")
      else if (!releaseRepairContractValid(request)) _invalid_("releaseRepairContract")
      else Right(request)
    }
  }

  val RELEASE_REPAIR_CONTRACT = "sm-workflow.phase-release-repair.v1"
  def publicationFailureValid(value: PublicationFailureContext): Boolean = value != null &&
    GoalPhaseDependencySourceRevision.referenceValid(value.repair) && value.repair.revision.toLongOption.exists(_ > 0) &&
    value.changedPaths != null && value.changedPaths.nonEmpty && value.changedPaths.distinct == value.changedPaths &&
    value.changedPaths.forall(_bounded_text) && _context_text(value.diff) && !value.testsExecuted &&
    GoalPhasePrerequisitePublicationRecovery.confirmedFailure(value.publication) &&
    value.publication.request != null && value.publication.request.target.dependency.isEmpty &&
    value.publication.request.target.beforeChecks
  private def _publication_request_valid(request: Request): Boolean = request != null && request.publicationFailure != null &&
    request.publicationFailure.forall(value => publicationFailureValid(value) && request.kind == "RepairStep" &&
      request.review != null && request.review.isEmpty && request.checkFailure != null && request.checkFailure.isEmpty &&
      request.candidate != null && request.candidate.isEmpty && request.checksReceipt != null && request.checksReceipt.isEmpty &&
      request.stepIdentity != null && request.stepIdentity.nonEmpty && value.repair.identity == request.instanceIdentity)
  def releaseRepairContractValid(request: Request): Boolean = request != null &&
    request.releaseRepairContract != null && request.releaseRepairContract.forall(value =>
      value == RELEASE_REPAIR_CONTRACT && request.kind == "ReviewPhase" && request.stepIdentity != null && request.stepIdentity.isEmpty)

  def checkFailureContextC(changedPaths: Vector[String], diff: String,
    validation: GoalPhaseNativeValidation.Payload,
    initialPhaseRepair: Option[ContextReference] = None,
    stepTestReportRepair: Option[ContextReference] = None): Consequence[CheckFailureContext] = {
    val value = CheckFailureContext(changedPaths, diff, validation, initialPhaseRepair, stepTestReportRepair)
    admitCheckFailureContextC(value).map(_ => value)
  }

  def admitCheckFailureContextC(value: CheckFailureContext): Consequence[Unit] = {
    val structural = value != null && value.changedPaths != null && value.changedPaths.distinct == value.changedPaths &&
      value.changedPaths.forall(_bounded_text) && value.diff != null &&
      (if (value.changedPaths.isEmpty) value.diff.isEmpty else _context_text(value.diff)) &&
      value.validation != null && value.validation.outcome != null &&
      value.initialPhaseRepair != null && value.stepTestReportRepair != null
    val valid = structural && {
      val ordinary = value.initialPhaseRepair.isEmpty && value.stepTestReportRepair.isEmpty &&
        value.validation.outcome.exists(Set("repair-required", "repair-limit-reached")) &&
        GoalPhaseNativeValidation.repairable(value.validation)
      val phase = value.changedPaths.nonEmpty && value.stepTestReportRepair.isEmpty && value.initialPhaseRepair.exists(_initial_phase_reference) &&
        (GoalPhaseInitialPhaseCheckRepair.terminalFailureValid(value.validation) ||
          value.validation.outcome.exists(Set("repair-required", "repair-limit-reached")) &&
            GoalPhaseInitialPhaseCheckRepair.repairContextValid(value.validation))
      val report = value.changedPaths.nonEmpty && value.initialPhaseRepair.isEmpty && value.stepTestReportRepair.nonEmpty &&
        value.stepTestReportRepair.forall(GoalPhaseStepTestReportRepair.validReference) &&
        GoalPhaseStepTestReportRepair.reportRepairFailureValid(value.validation)
      ordinary || phase || report
    }
    if (valid) Consequence.unit else F.invalid("repair requires its retained actual selected check failure context")
  }

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
      case "TriageStep" => ("goal-phase-step-triage-capability", "step-triage")
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
    if (request == null || !GoalPhasePurposeRepair.validateRequestC(request).isSuccess || !GoalPhaseRepairProgress.validateOptionC(request.repairProgress).isSuccess || result == null ||
        result.repairFindingSources == null || result.repairFindingSources.nonEmpty || request.kind != "ImplementStep" || request.review == null || request.review.nonEmpty ||
        request.checkFailure == null || request.checkFailure.nonEmpty || request.publicationFailure == null || request.publicationFailure.nonEmpty ||
        result.completionConditions == null || result.completionConditions.nonEmpty ||
        !_bounded_text(result.summary) || result.findings == null || result.findings.size > 128 ||
        !result.findings.forall(_bounded_text) ||
        !Set("completed", "decision-needed")(result.outcome) ||
        (result.outcome == "completed" && result.findings.nonEmpty))
      F.invalid("initial assigned result must report completion or an explicit decision, never review/command/closure")
    else for {
      _ <- IncrementalSbtDiagnosisRepair.admitC(request, result)
      _ <- GoalPhaseFixIssues.admitC(request, result)
      _ <- GoalPhaseFindingRecording.admitObservationsC(request.kind, result.reviewObservations)
      _ <- _plan_revision_c(result)
      _ <- GoalPhaseDependencyScope.admitResultC(request.kind, result.outcome, result.dependencyProposal)
      _ <- GoalPhaseDependencyValidation.validateSelectionC(result.dependencyValidation)
      _ <- GoalPhaseFixAssessment.admitC(request.kind, result.outcome, result.fixAssessment)
      _ <- GoalPhaseAcceptanceBoundary.admitC(request.selectedPlan, request.kind, result)
    } yield ()

  def admitSemanticResultC(request: Request, result: Result): Consequence[Unit] =
    if (request == null || !_publication_request_valid(request) || !GoalPhasePurposeRepair.validateRequestC(request).isSuccess || !GoalPhaseRepairProgress.validateOptionC(request.repairProgress).isSuccess)
      F.invalid("assigned request or progress is invalid")
    else if (request.kind == "TriageStep") GoalPhaseStepTriage.admitC(request, result)
    else if (request.kind == "ImplementStep") admitInitialResultC(request, result)
    else if (request.kind != "RepairStep" || request.repairDirective == null ||
        !request.repairDirective.exists(value => value != null && _bounded_text(value.reason) &&
          value.findings != null && value.findings.nonEmpty && value.findings.forall(_bounded_text)) ||
        !_repair_source(request) ||
        request.candidate.nonEmpty || request.checksReceipt.nonEmpty || result == null ||
        result.repairFindingSources == null || result.repairFindingSources.nonEmpty ||
        result.completionConditions == null || result.completionConditions.nonEmpty ||
        !_bounded_text(result.summary) || result.findings == null || result.findings.size > 128 ||
        !result.findings.forall(_bounded_text) ||
        !Set("completed", "decision-needed")(result.outcome) ||
        (result.outcome == "completed" && result.findings.nonEmpty))
      F.invalid("assigned repair must return semantics or a decision, not review, checks, commands or closure")
    else for {
      _ <- IncrementalSbtDiagnosisRepair.admitC(request, result)
      _ <- GoalPhaseFixIssues.admitC(request, result)
      _ <- GoalPhaseFindingRecording.admitObservationsC(request.kind, result.reviewObservations)
      _ <- _plan_revision_c(result)
      _ <- GoalPhaseDependencyScope.admitResultC(request.kind, result.outcome, result.dependencyProposal)
      _ <- GoalPhaseDependencyValidation.validateSelectionC(result.dependencyValidation)
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
    _ <- if (_dependency_selection_valid(result)) Consequence.unit
      else F.invalid("Dependency validation selection requires completed work without a Plan or dependency proposal")
  } yield ()

  private def _dependency_selection_valid(result: Result): Boolean =
    result.dependencyValidation != null && (result.dependencyValidation.isEmpty || (result.outcome == "completed" &&
      result.dependencyProposal.isEmpty && result.planRevision.isEmpty))

  private def _repair_source(request: Request): Boolean = if (request.review == null || request.checkFailure == null || request.publicationFailure == null) false
    else {
      val validreview = request.review.exists(value => value != null && value.changedPaths != null &&
        value.changedPaths.forall(_bounded_text) && value.diff != null &&
        (if (value.changedPaths.isEmpty) request.stepIdentity != null && request.stepIdentity.nonEmpty && value.diff.isEmpty
         else _context_text(value.diff)) && value.checks != null &&
        value.checks.forall(_ != null))
      (request.review.isEmpty && request.checkFailure.isEmpty && request.publicationFailure.exists(value =>
        publicationFailureValid(value) && value.repair.identity == request.instanceIdentity &&
          value.publication.request.input.ownerSessionId.contains(request.instanceIdentity) &&
          request.stepIdentity.flatMap(step => request.selectedPlan.steps.find(_.identity == step)).exists(step =>
            GoalPhaseNativeCandidateBoundary.pathsOwned(value.changedPaths, step.ownedPaths)))) ||
      (validreview && request.checkFailure.isEmpty && request.publicationFailure.isEmpty) ||
      (request.review.isEmpty && request.checkFailure.exists(value => admitCheckFailureContextC(value).isSuccess &&
        value.stepTestReportRepair.forall(_.identity.startsWith(request.instanceIdentity + ":ordered-step-test-report-repair:")) &&
        request.stepIdentity != null && request.stepIdentity.contains(value.validation.step))) ||
      (request.review.isEmpty && request.stepIdentity != null && request.stepIdentity.isEmpty &&
        request.selectedPlan != null && request.completed != null &&
        request.completed.map(_.step) == request.selectedPlan.steps.map(_.identity) &&
        request.completed.forall(O.validStepAcceptance) && request.checkFailure.exists(value =>
          admitCheckFailureContextC(value).isSuccess && value.initialPhaseRepair.exists(reference =>
            reference.identity.startsWith(request.instanceIdentity + ":ordered-initial-phase-check-repair:")) &&
          value.validation.step == "phase:" + request.selectedPlan.phase && {
            val selected = GoalPhaseNativeMetadata.declaredIdentities(value.validation.selected)
            selected.nonEmpty && selected == request.selectedPlan.phaseChecks.take(selected.size)
          })) ||
      (validreview && request.stepIdentity != null && request.stepIdentity.isEmpty && request.selectedPlan != null &&
        _bounded_text(request.selectedPlan.phase) && request.checkFailure.exists(value =>
          admitCheckFailureContextC(value).isSuccess && value.validation.step == "phase:" + request.selectedPlan.phase) &&
        request.review.exists(value => GoalPhaseNativeReview.priorReviewsForC(value.priorReviews, "ReviewPhase", None).isSuccess &&
          value.priorReviews.findLast(_.kind == "ReviewPhase").exists(_.result.outcome == "repair")))
    }

  private def _initial_phase_reference(value: ContextReference): Boolean = value != null &&
    value.revision == "1" && _bounded_text(value.identity) && !value.identity.exists(_.isControl) && {
      val separator = ":ordered-initial-phase-check-repair:"
      val parts = value.identity.split(java.util.regex.Pattern.quote(separator), -1)
      parts.length == 2 && parts(0).nonEmpty && parts(1).toLongOption.exists(n => n > 0 && n.toString == parts(1))
    }

  private def _bounded_text(value: String): Boolean = value != null && value.trim.nonEmpty &&
    !value.contains('\u0000')
  private def _context_text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000')

  // Encoder-produced object fields are schema names except inside opaque maps
  // and retained JSON. Never copy an actual-only key into a diagnostic.
  private val _diagnostic_opaque_fields = Set("controls", "edges", "properties", "environment", "metadata", "process")
  private[smworkflow] def _structural_mismatch(expected: Json, actual: Json, path: String = ""): Option[(String, String)] = {
    def location = if (path.isEmpty) "/" else path
    def child(key: String) = path + "/" + key.replace("~", "~0").replace("/", "~1")
    if (expected == actual) None
    else (expected.asObject, actual.asObject, expected.asArray, actual.asArray) match {
      case (Some(left), Some(right), _, _) =>
        val keys = (left.keys.toSet ++ right.keys.toSet).toVector.sorted
        keys.iterator.flatMap { key =>
          (left(key), right(key)) match {
            case (None, Some(_)) => Some(location -> "unexpected-field")
            case (Some(_), None) => Some(child(key) -> "missing-field")
            case (Some(a), Some(b)) if a != b && _diagnostic_opaque_fields(key) =>
              Some(child(key) -> "field-mismatch")
            case (Some(a), Some(b)) if a != b => _structural_mismatch(a, b, child(key))
            case _ => None
          }
        }.take(1).toVector.headOption
      case (_, _, Some(left), Some(right)) =>
        val common = math.min(left.size, right.size)
        (0 until common).iterator.flatMap(index =>
          _structural_mismatch(left(index), right(index), path + "/" + index)).take(1).toVector.headOption
          .orElse(Option.when(left.size != right.size)((path + "/" + common) -> "array-length"))
      case _ => Some(location -> "value-or-type-mismatch")
    }
  }

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
          val progress = if (value.repairProgress.isEmpty) issues.remove("repairProgress") else issues
          val purpose = if (value.purposeRepair.isEmpty) progress.remove("purposeRepair") else progress
          val release = if (value.releaseRepairContract.isEmpty) purpose.remove("releaseRepairContract") else purpose
          val triage = if (value.triage.isEmpty) release.remove("triage") else release
          val diagnosis = if (value.diagnosticHistory.isEmpty) triage.remove("diagnosticHistory") else triage
          val profile = if (value.operatingProfile.isEmpty) diagnosis.remove("operatingProfile") else diagnosis
          if (value.publicationFailure.isEmpty) profile.remove("publicationFailure") else profile
        }
        Json.obj("schemaVersion" -> Json.fromString(if (value.publicationFailure.nonEmpty)
          "sm-workflow.goal-phase-native-work-request.v6" else if (value.operatingProfile.nonEmpty)
          "sm-workflow.goal-phase-native-work-request.v5" else if (value.diagnosticHistory.nonEmpty)
          "sm-workflow.goal-phase-native-work-request.v4" else if (value.purposeRepair.nonEmpty)
          "sm-workflow.goal-phase-native-work-request.v3" else "sm-workflow.goal-phase-native-work-request.v2"),
          "payload" -> payload)
      }
      override def decode(value: Json): Either[String, Request] = for {
        wire <- Option(value).toRight("native ordered work is missing")
        version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
        marked = wire.hcursor.downField("payload").downField("purposeRepair").focus.nonEmpty
        diagnosis = wire.hcursor.downField("payload").downField("diagnosticHistory").focus.nonEmpty
        profile = wire.hcursor.downField("payload").downField("operatingProfile").focus.nonEmpty
        publication = wire.hcursor.downField("payload").downField("publicationFailure").focus.nonEmpty
        _ <- Either.cond((!publication && ((version == "sm-workflow.goal-phase-native-work-request.v2" && !marked && !diagnosis && !profile) ||
          (version == "sm-workflow.goal-phase-native-work-request.v3" && marked && !diagnosis && !profile) ||
          (version == "sm-workflow.goal-phase-native-work-request.v4" && diagnosis && !profile) ||
          (version == "sm-workflow.goal-phase-native-work-request.v5" && profile))) ||
          (version == "sm-workflow.goal-phase-native-work-request.v6" && publication), (), "native ordered request version or purpose contract differs")
        parsed <- wire.hcursor.get[Request]("payload").left.map(_.message)
        _ <- if (!Set("sm-workflow.goal-phase-native-work-request.v5", "sm-workflow.goal-phase-native-work-request.v6")(version) || encode(parsed) == wire)
          Right(()) else Left("retained v5 work must preserve its exact canonical payload" +
            _structural_mismatch(encode(parsed), wire).fold("") { case (path, category) =>
              " [path=" + path + "; category=" + category + "]"
            })
      } yield parsed
    }
  val resultCodec: WorkflowResultJsonV1.PayloadCodec[Result] =
    new Codec[Result]("GoalPhaseWorkResult", "sm-workflow.goal-phase-native-work-result.v1") {
      override def encode(value: Result): Json = Json.obj("schemaVersion" -> Json.fromString(
        if (value.diagnosticRepair.nonEmpty) "sm-workflow.goal-phase-native-work-result.v3"
        else if (value.repairFindingSources.nonEmpty) "sm-workflow.goal-phase-native-work-result.v2"
        else "sm-workflow.goal-phase-native-work-result.v1"), "payload" -> value.asJson)
      override def decode(value: Json): Either[String, Result] = for {
        wire <- Option(value).toRight("native ordered result is missing")
        version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
        marked = wire.hcursor.downField("payload").downField("repairFindingSources").focus.nonEmpty
        diagnosis = wire.hcursor.downField("payload").downField("diagnosticRepair").focus.nonEmpty
        _ <- Either.cond((version == "sm-workflow.goal-phase-native-work-result.v1" && !marked && !diagnosis) ||
          (version == "sm-workflow.goal-phase-native-work-result.v2" && marked && !diagnosis) ||
          (version == "sm-workflow.goal-phase-native-work-result.v3" && diagnosis), (), "native ordered result version or repair sources differ")
        parsed <- wire.hcursor.get[Result]("payload").left.map(_.message)
      } yield parsed
    }

  def providedDefinition: GeneratedProvidedApiAbi.Definition = {
    val p = domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1.workflow
    GeneratedProvidedApiAbi.Definition(p.schemaVersion, p.generator, p.identity, p.version,
      GeneratedProvidedApiAbi.WorkflowSourceCorrelation(GeneratedProvidedApiAbi.SourceIdentity(p.source.root.line),
        GeneratedProvidedApiAbi.SourceIdentity(p.source.definition.line)),
      p.providedOperations.map(o => GeneratedProvidedApiAbi.Operation(o.service, o.name, o.inputType, o.resultType,
        GeneratedProvidedApiAbi.SourceIdentity(o.source.line))))
  }
}
