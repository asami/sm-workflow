package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{TypedValue, WorkflowContinuation}
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
import RepositorySyncManagedGitObservation as G

/** A new independently admitted direction supersedes one unanswered legacy
  * Phase-review question without answering it or reinterpreting its review.
  * Recover records only the native disposition. Advance owns all later effects.
  * @since   Oct. 25, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseReleaseRepairContinuation {
  val purpose = "goal-phase-release-repair-continuation"
  val describeOperation = "describeGoalPhaseReleaseRepairContinuation"
  val recoveryOperation = "recoverGoalPhaseReleaseRepairContinuation"
  val schema = "sm-workflow.goal-phase-release-repair-continuation.v1"
  val origin = "legacy-phase-review-human-release-repair"
  val choices = Vector("ContinueReleaseRepair", "Defer")
  val message = "A separate actual Human may continue this exact saved Phase blocker batch within the unchanged Plan. The original accepted review, unanswered question, successful checks, completed Steps, base and repair history remain. A separate Advance issues Release RepairStep once; recovery executes no work, Git, validation or commit."

  final case class Program(step: String, ordinal: Int)
  object Program {
    given Encoder[Program] = deriveEncoder
    given Decoder[Program] = _strict(deriveDecoder[Program], Set("step", "ordinal"))(p =>
      p != null && _exact(p.step) && p.ordinal > 0 && p.ordinal <= 64)
  }
  final case class Host(root: String, branch: String, candidatePaths: Vector[String],
    validationIdentity: String, validationRevision: Int, checks: Vector[GoalPhaseNativeValidation.Check],
    closureIdentity: String, closureRevision: Int, programs: Vector[Program])
  object Host {
    given Encoder[Host] = deriveEncoder
    given Decoder[Host] = _strict(deriveDecoder[Host], Set("root", "branch", "candidatePaths",
      "validationIdentity", "validationRevision", "checks", "closureIdentity", "closureRevision", "programs"))(_host_valid)
  }
  final case class Finding(findingOrdinal: Int, conditionIdentity: String, text: String, issueIdentity: String)
  object Finding {
    given Encoder[Finding] = deriveEncoder
    given Decoder[Finding] = _strict(deriveDecoder[Finding], Set("findingOrdinal", "conditionIdentity", "text", "issueIdentity"))(_finding_valid)
  }
  final case class Target(sessionId: String, questionIdentity: String, sourceRevision: String, pendingRevision: String,
    assignmentIdentity: String, assignmentSourceRevision: String, requestingResult: ContextReference,
    originalRequest: Json, originalResult: W.Result, plan: O.Plan, base: O.Snapshot,
    completed: Vector[O.ClosedStep], generation: Long, candidate: O.Snapshot, checksReceipt: String,
    repairProgress: GoalPhaseRepairProgress.Snapshot, issues: GoalPhaseFixIssues.State,
    findings: Vector[Finding], host: Host)
  object Target {
    private given Encoder[ContextReference] = deriveEncoder
    private given Decoder[ContextReference] = _strict(deriveDecoder[ContextReference], Set("identity", "revision"))(_reference)
    private given Encoder[O.Snapshot] = deriveEncoder
    private given Decoder[O.Snapshot] = _strict(deriveDecoder[O.Snapshot], Set("head", "tree"))(_snapshot)
    private given Encoder[O.Commit] = deriveEncoder
    private given Decoder[O.Commit] = _strict(deriveDecoder[O.Commit], Set("identity", "parent", "tree"))(value =>
      value != null && _snapshot(O.Snapshot(value.identity, value.tree)) && _snapshot(O.Snapshot(value.parent, value.tree)))
    private given Decoder[O.StepCommitChain] = _strict(deriveDecoder[O.StepCommitChain],
      Set("originalParent", "partial", "completion"))(O.validStepCommitChain)
    private given Encoder[O.ClosedStep] = deriveEncoder[O.ClosedStep].mapJson(_.mapObject(_.filter {
      case ("admission" | "partialCompletion", value) => !value.isNull
      case _ => true
    }))
    private given Decoder[O.ClosedStep] = Decoder.instance(cursor => deriveDecoder[O.ClosedStep].apply(cursor).flatMap(value =>
      if (cursor.value.asObject.exists(fields => Set("step", "commit").subsetOf(fields.keys.toSet) &&
          fields.keys.toSet.subsetOf(Set("step", "commit", "review", "admission", "partialCompletion")) &&
          fields("admission").forall(!_.isNull) && fields("partialCompletion").forall(!_.isNull)) &&
          value != null && _exact(value.step) && value.commit != null && O.validStepAcceptance(value) && value.review.forall(_exact)) Right(value)
      else Left(DecodingFailure("Release continuation has invalid, unknown or contradictory fields", cursor.history))))
    given Encoder[Target] = deriveEncoder
    given Decoder[Target] = _strict(deriveDecoder[Target], Set("sessionId", "questionIdentity", "sourceRevision",
      "pendingRevision", "assignmentIdentity", "assignmentSourceRevision", "requestingResult", "originalRequest", "originalResult",
      "plan", "base", "completed", "generation", "candidate", "checksReceipt", "repairProgress", "issues", "findings", "host"))(
      value => validateTargetC(value).isSuccess)
  }
  final case class Payload(target: Target, choice: String, directive: ContextReference, participant: String, rationale: String)
  object Payload {
    private given Encoder[ContextReference] = deriveEncoder
    private given Decoder[ContextReference] = _strict(deriveDecoder[ContextReference], Set("identity", "revision"))(_reference)
    given Encoder[Payload] = deriveEncoder
    given Decoder[Payload] = _strict(deriveDecoder[Payload], Set("target", "choice", "directive", "participant", "rationale"))(
      value => value != null && validateTargetC(value.target).isSuccess && value.choice == choices.head &&
        _reference(value.directive) && _exact(value.participant) && GoalPhasePurposeRepair.text(value.rationale))
  }
  trait EvidencePort { def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit] }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = _check(evidence != null, "independent actual Human evidence provider is absent")
  }

  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def targetC(value: Record): Consequence[Target] = if (value == null) F.invalid("Release continuation target is absent")
    else for {
      target <- value.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
        .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(_.as[Target].left.map(_.message))
        .fold(F.invalid(_), Consequence.success))
      original <- W.inputCodec.decode(target.originalRequest).fold(F.invalid(_), Consequence.success)
      canonical = W.inputCodec.encode(original)
      projected <- GoalPhaseNativeMetadata.record(canonical).toJsonStringC.flatMap(raw =>
        io.circe.parser.parse(raw).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
          .fold(F.invalid(_), Consequence.success))
      _ <- _check(target.originalRequest == projected,
        "Release continuation original Request has contradictory or unknown public fields")
    } yield target.copy(originalRequest = canonical)

  def bindingC(component: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] = for {
    _ <- validateTargetC(target)
    _ <- _check(_exact(component) && choice == choices.head && GoalPhasePurposeRepair.text(rationale) &&
      _exact(participant) && _reference(reference), "Release continuation requires its exact independent Human directive")
  } yield Json.obj("componentInstance" -> Json.fromString(component), "releaseRepairContinuation" -> Json.obj(
    "target" -> target.asJson, "directive" -> Json.obj("choice" -> Json.fromString(choice), "rationale" -> Json.fromString(rationale),
      "participant" -> Json.fromString(participant), "reference" -> Json.obj(
        "identity" -> Json.fromString(reference.identity), "revision" -> Json.fromString(reference.revision)))))

  def selectBinding(value: Json): Either[String, Json] = for {
    supplied <- Option(value).toRight("Release continuation binding is absent")
    component <- supplied.hcursor.get[String]("componentInstance").left.map(_.message)
    target <- supplied.hcursor.downField("releaseRepairContinuation").get[Target]("target").left.map(_.message)
    directive = supplied.hcursor.downField("releaseRepairContinuation").downField("directive")
    choice <- directive.get[String]("choice").left.map(_.message)
    rationale <- directive.get[String]("rationale").left.map(_.message)
    participant <- directive.get[String]("participant").left.map(_.message)
    identity <- directive.downField("reference").get[String]("identity").left.map(_.message)
    revision <- directive.downField("reference").get[String]("revision").left.map(_.message)
    binding <- bindingC(component, target, choice, rationale, participant, ContextReference(identity, revision))
      .map[Either[String, Json]](Right(_)).fold(error => Left(error.toString), result => result)
    _ <- Either.cond(supplied == binding, (), "Release continuation binding has contradictory or unknown fields")
  } yield binding

  /** Exact required-condition provenance, never an observation promoted to a blocker. */
  private[admission] def findingsC(input: W.Request, result: W.Result, issues: GoalPhaseFixIssues.State,
    reference: ContextReference): Consequence[Vector[Finding]] = for {
    _ <- _check(input != null && input.kind == "ReviewPhase" && input.stepIdentity.isEmpty &&
      W.releaseRepairContractValid(input) && input.releaseRepairContract.isEmpty && result != null && result.outcome == "repair" &&
      result.planRevision.isEmpty && result.dependencyProposal.isEmpty && result.completionConditions.isEmpty &&
      result.repairFindingSources.isEmpty && _reference(reference), "legacy source must be the actual marker-absent accepted repair review")
    _ <- GoalPhaseAcceptanceBoundary.admitRequestC(input, result)
    _ <- GoalPhaseFixIssues.validateC(issues)
    required = input.selectedPlan.effectiveAcceptance.toVector.flatMap(boundary =>
      GoalPhaseAcceptanceBoundary.applicable(boundary, "ReviewPhase")).filter(_.required)
    observations = result.acceptance.toVector.flatMap(_.conditions)
    _ <- _check(required.nonEmpty && required.forall(condition => observations.exists(o =>
      o.identity == condition.identity && o.status != "not-evaluated")), "every actual required Phase condition must have been evaluated")
    failed = observations.filter(o => required.exists(_.identity == o.identity) && o.status == "unsatisfied")
    blockers = issues.issues.filter(issue => issue.repository == input.selectedPlan.repository &&
      issue.source == "REVIEW" && issue.evidenceReference == reference.identity && GoalPhaseFixIssues.unresolved(issue))
    _ <- _check(failed.nonEmpty && failed.map(_.detail) == result.findings && blockers.size == failed.size &&
      blockers.map(_.summary) == result.findings, "Release blockers must match every original failed required condition and exact native REVIEW issue")
  } yield failed.zip(blockers).zipWithIndex.map { case ((condition, issue), index) =>
    Finding(index + 1, condition.identity, condition.detail, issue.identity)
  }

  def validateTargetC(target: Target): Consequence[Unit] = for {
    _ <- _check(target != null && Vector(target.sessionId, target.questionIdentity, target.assignmentIdentity, target.checksReceipt).forall(_exact) &&
      Vector(target.sourceRevision, target.pendingRevision, target.assignmentSourceRevision).forall(_revision) &&
      target.pendingRevision.toLong == target.sourceRevision.toLong + 1 && _reference(target.requestingResult) &&
      target.plan != null && O.validatePlanC(target.plan).isSuccess && GoalPhaseAcceptanceBoundary.savedPlanC(target.plan).isSuccess &&
      _snapshot(target.base) && _snapshot(target.candidate) && target.completed != null &&
      target.completed.forall(value => O.validStepAcceptance(value) && _exact(value.step) &&
        value.commit != null && value.review.forall(_exact)) &&
      target.completed.map(_.step) == target.plan.steps.map(_.identity) && target.generation > 0 && target.generation < Long.MaxValue &&
      GoalPhaseRepairProgress.validateOptionC(Some(target.repairProgress)).isSuccess &&
      target.repairProgress.automaticCount < target.repairProgress.effectiveLimit.getOrElse(target.repairProgress.limit) && target.repairProgress.limit == target.plan.repairLimit &&
      _host_valid(target.host), "Release continuation target or saved host is incomplete")
    original <- W.inputCodec.decode(target.originalRequest).fold(F.invalid(_), Consequence.success)
    _ <- _check(original.instanceIdentity == target.sessionId && original.sourceRevision == target.assignmentSourceRevision &&
      original.selectedPlan == target.plan && original.originalBase == target.base && original.completed == target.completed &&
      original.generation == target.generation && original.candidate.contains(target.candidate) &&
      original.checksReceipt.contains(target.checksReceipt) && target.assignmentSourceRevision.toLong < target.sourceRevision.toLong &&
      original.review.exists(context => context != null && context.checks.nonEmpty && context.checks.forall(receipt =>
        receipt != null && receipt.exitCode.contains(0) && receipt.sbt.forall(_.valid))),
      "Release target differs from its original assignment, candidate or successful check context")
    findings <- findingsC(original, target.originalResult, target.issues, target.requestingResult)
    _ <- _check(target.findings == findings, "Release target changes required-condition provenance or the complete blocker batch")
  } yield ()

  private def _host_c(d: F.Declaration): Consequence[Host] = for {
    validation <- d.phaseValidationPolicy.map(Consequence.success).getOrElse(F.invalid("Release continuation requires the actual Phase validation policy"))
    closure <- d.closureInstallation.map(_.policy).map(Consequence.success).getOrElse(F.invalid("Release continuation requires fixed closure programs"))
    _ <- validation.validateC
    _ <- closure.validateC
    host = Host(d.target.root.toString, d.target.localBranch, d.candidatePaths, validation.identity, validation.revision,
      validation.checks, closure.identity, closure.revision, closure.programs.map(p => Program(p.step, p.ordinal)))
    _ <- _check(_host_valid(host), "Release host must declare its fixed phase-release program")
  } yield host

  private def _historical_target_c(source: H.Source, pending: InstanceRecord, host: Host,
    core: ActionCall.Core): Consequence[Target] = for {
    _ <- _check(source.sourceHistoryRecovery.isEmpty && source.result.result.typeIdentity == W.resultCodec.typeIdentity &&
      source.input.instanceIdentity == source.from.identity.value && source.controller.stage == O.Stage.Decision &&
      source.controller.decisionSource.contains(O.Stage.PhaseReview) && !source.controller.documentDecision &&
      source.controller.pendingRepair.isEmpty && source.controller.closureIntent.isEmpty &&
      source.candidate.exists(saved => saved.payload.candidate == source.controller.candidate) &&
      pending.revision == source.from.revision.next && pending.suspension.exists(boundary =>
        boundary.continuationIdentity.value == H.identity(source).value && boundary.completion.value == "goal-phase-ordered-human-completion-v1"),
      "Release continuation crosses its actual accepted native source or original unanswered question")
    issued <- source.session.storage.bindIssuedC(F.completionOperation, W.inputCodec)
    work <- issued.loadC(source.result.continuationId)(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(F.invalid("original accepted Phase review assignment is absent")))
    original <- work.current match {
      case order: WorkflowContinuation.WorkOrder[W.Request] => order.request.input.map(value => Consequence.success(value.value))
        .getOrElse(F.invalid("original Phase review has no captured Request"))
      case _ => F.invalid("original Phase review assignment is not a WorkOrder")
    }
    issues <- source.input.issues.map(Consequence.success).getOrElse(F.invalid("actual accepted Phase review issue state is absent"))
    findings <- findingsC(original, source.result.result.value, issues, source.result.resultReference)
    _ <- source.controller.continueLegacyReleaseRepairC("description-eligibility",
      O.RepairDirective(source.result.result.value.summary, findings.map(_.text)))
    target = Target(source.from.identity.value, H.identity(source).value, source.from.revision.value.toString,
      pending.revision.value.toString, source.result.continuationId.value, original.sourceRevision, source.result.resultReference,
      W.inputCodec.encode(original), source.result.result.value, source.controller.plan, source.controller.base,
      source.controller.completed, source.controller.generation, source.controller.candidate.get, source.controller.checksReceipt.get,
      GoalPhaseRepairProgress.snapshot(source.controller), issues, findings, host)
    _ <- validateTargetC(target)
  } yield target

  private def _target_c(source: H.Source, d: F.Declaration, core: ActionCall.Core): Consequence[Target] = for {
    pending <- _pending_c(source)
    host <- _host_c(d)
    target <- _historical_target_c(source, pending, host, core)
    reader <- source.session.storage.bindDecisionC(H.completionOperation, H.inputCodec)
    issued <- reader.loadC(H.identity(source))(using core.executionContext)
    _ <- _check(issued.nonEmpty, "Release continuation requires the actual issued unanswered Human question")
  } yield target

  def describeC(source: H.Source, d: F.Declaration, core: ActionCall.Core): Consequence[V.GoalPhaseReleaseRepairContinuationDescriptionResult] = for {
    target <- _target_c(source, d, core)
  } yield V.GoalPhaseReleaseRepairContinuationDescriptionResult(target.sessionId, "direction-required", message,
    targetRecord(target), choices, recoveryOperation, core.component.get.core.instanceId.canonicalKey)

  def recoverC(source: H.Source, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhaseReleaseRepairContinuationRecoveryInput): Consequence[V.GoalPhaseReleaseRepairContinuationRecoveryResult] = for {
    supplied <- targetC(input.target)
    actual <- _target_c(source, d, core)
    _ <- _check(input.sessionId == actual.sessionId && supplied == actual && choices.contains(input.choice) &&
      GoalPhasePurposeRepair.text(input.rationale) && input.reference != null &&
      _reference(ContextReference(input.reference.identity, input.reference.revision)), "Release recovery differs from the exact current target or closed choice")
    accepted <- if (input.choice == "Defer") Consequence.success(false) else for {
      installation <- d.releaseRepairContinuation.map(Consequence.success).getOrElse(
        Consequence.securityPermissionDenied("Release continuation has no independent actual Human evidence provider"))
      _ <- installation.validateC
      reference = ContextReference(input.reference.identity, input.reference.revision)
      binding <- bindingC(core.component.get.core.instanceId.canonicalKey, actual, input.choice, input.rationale,
        source.session.participantIdentity, reference)
      _ <- installation.evidence.admitC(core, reference, binding)
      payload = Payload(actual, input.choice, reference, source.session.participantIdentity, input.rationale)
      state <- source.controller.continueLegacyReleaseRepairC(reference.identity, repairDirective(payload))
      pending <- _pending_c(source)
      adapter <- source.session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec,
        GoalPhaseLegacyPurposeRepairContinuation.storageCodec)
      _ <- adapter.executeC(H.identity(source), pending.revision, pending,
        F.history(pending, "phase." + state.stage.toString, reference.identity),
        TypedValue(GoalPhaseLegacyPurposeRepairContinuation.storageCodec.typeIdentity,
          GoalPhaseLegacyPurposeRepairContinuation.ReleaseRepair(payload): GoalPhaseLegacyPurposeRepairContinuation.StoredDisposition))(using core.executionContext)
    } yield true
  } yield V.GoalPhaseReleaseRepairContinuationRecoveryResult(input.sessionId, accepted,
    if (accepted) "release-repair-continuation-admitted" else "deferred", message)

  private[admission] def reconstructC(source: H.Source, d: F.Declaration, core: ActionCall.Core, payload: Payload,
    sourceRevision: InstanceRevision, nextRevision: InstanceRevision): Consequence[A.Selected] = for {
    _ <- _check(payload != null && summon[Decoder[Payload]].decodeJson(payload.asJson).isRight && payload.participant == source.session.participantIdentity,
      "retained Release direction is invalid or belongs to another participant")
    host <- _host_c(d)
    _ <- _check(host == payload.target.host, "saved Release continuation host changed before new work")
    pending <- GoalPhaseNativeHistory.prefixC(source.session.current, sourceRevision)
    expected <- _historical_target_c(source, pending, host, core)
    _ <- _check(expected == payload.target && payload.target.pendingRevision == sourceRevision.value.toString &&
      nextRevision == sourceRevision.next, "retained Release disposition crosses its exact original native prefix")
    state <- source.controller.continueLegacyReleaseRepairC(payload.directive.identity, repairDirective(payload))
    next <- GoalPhaseNativeHistory.completionC(source.session.current, pending, payload.directive, state.stage.toString)
    _ <- _check(next.revision == nextRevision, "retained Release disposition is not its adjacent native successor")
  } yield A.Selected(state, next, source.documents, Some(W.DecisionContext(payload.target.originalResult, origin,
    payload.rationale, Some(payload.directive))))

  private[admission] def beforeWorkC(d: F.Declaration, core: ActionCall.Core, input: W.Request): Consequence[Unit] =
    if (!input.decisionContext.exists(_.choice == origin)) Consequence.unit else for {
      original <- input.review.toVector.flatMap(_.priorReviews).findLast(p => p.kind == "ReviewPhase" &&
        p.result == input.decisionContext.get.requestingResult).map(Consequence.success).getOrElse(F.invalid("Release continuation lost its original integrated report"))
      actual <- G.inspectC(core)
      root <- G.rootC(core)
      index <- G.indexTreeC(core)
      parent <- GitObjectId.parseC(original.candidate.head)
      pending <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.PendingPaths(parent)).flatMap(_.valueC)
      owned = input.selectedPlan.steps.flatMap(_.ownedPaths).distinct
      _ <- _check(root == d.target.root.toString && actual.mergeHead.isEmpty && actual.observed.branch == d.target.localBranch &&
        actual.observed.head == original.candidate.head && input.completed.lastOption.exists(_.commit.tree == actual.observed.tree) &&
        index == original.candidate.tree &&
        GoalPhaseNativeCandidateBoundary.pathsOwned(pending.map(_.value), owned) &&
        GoalPhaseNativeCandidateBoundary.scopedDirty(actual.observed.dirty, owned, true),
        "Release continuation candidate, head, index or managed dirty scope drifted before its first new work")
    } yield ()

  private[admission] def repairDirective(payload: Payload): O.RepairDirective =
    O.RepairDirective(payload.target.originalResult.summary, payload.target.findings.map(_.text))
  private def _pending_c(source: H.Source): Consequence[InstanceRecord] = for {
    following <- GoalPhaseNativeHistory.nextC(source.session.current, source.from)
    entry <- following match {
      case Some(value) if value.revision == source.session.current.revision && value.suspension.exists(boundary =>
          boundary.continuationIdentity.value == H.identity(source).value && boundary.completion.value == "goal-phase-ordered-human-completion-v1") => Consequence.success(value)
      case _ => F.invalid("Release continuation requires the exact current unanswered Human frontier")
    }
    pending <- GoalPhaseNativeHistory.prefixC(source.session.current, entry.revision)
  } yield pending

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseReleaseRepairContinuationOperation
      else SmWorkflowComponent.GoalPhaseServiceService.RecoverGoalPhaseReleaseRepairContinuationOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") ||
          request.operation != (if (describe) describeOperation else recoveryOperation)) F.invalid("Release continuation operation identity differs")
      else if (describe) NativeWorkflowResourceInput.selectC[V.GoalPhaseReleaseRepairContinuationDescriptionInput](request)
        .map(value => Action(request, Some(value), None, port))
      else NativeWorkflowResourceInput.selectC[V.GoalPhaseReleaseRepairContinuationRecoveryInput](request)
        .map(value => Action(request, None, Some(value), port))
  }
  private final case class Action(request: Request,
    description: Option[NativeWorkflowResourceInput.Source[V.GoalPhaseReleaseRepairContinuationDescriptionInput]],
    recovery: Option[NativeWorkflowResourceInput.Source[V.GoalPhaseReleaseRepairContinuationRecoveryInput]],
    port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        described <- description.fold(Consequence.success(Option.empty[V.GoalPhaseReleaseRepairContinuationDescriptionInput]))(
          source => NativeWorkflowResourceInput.readC(source, reference => read_resource_text(reference, Some(java.nio.charset.StandardCharsets.UTF_8))).map(Some(_)))
        recovered <- recovery.fold(Consequence.success(Option.empty[V.GoalPhaseReleaseRepairContinuationRecoveryInput]))(
          source => NativeWorkflowResourceInput.readC(source, reference => read_resource_text(reference, Some(java.nio.charset.StandardCharsets.UTF_8))).map(Some(_)))
        session <- described.map(_.sessionId).orElse(recovered.map(_.sessionId)).filter(_exact)
          .map(Consequence.success).getOrElse(F.invalid("Release continuation requires an exact Session"))
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
        selected = core.copy(executionContext = context)
        result <- recovered match {
          case Some(value) => port.recoverReleaseRepairContinuationC(value, selected).map(NativeWorkflowOperationOutput.response(_))
          case None => port.describeReleaseRepairContinuationC(session, selected).map(NativeWorkflowOperationOutput.response(_))
        }
      } yield result
    }
  }

  private def _strict[A](decoder: Decoder[A], keys: Set[String])(valid: A => Boolean): Decoder[A] =
    Decoder.instance(cursor => decoder(cursor).flatMap(value =>
      if (cursor.value.asObject.exists(_.keys.toSet == keys) && valid(value)) Right(value)
      else Left(DecodingFailure("Release continuation has invalid, unknown or contradictory fields", cursor.history))))
  private def _host_valid(value: Host): Boolean = value != null && _exact(value.root) && value.root.startsWith("/") &&
    _exact(value.branch) && GoalPhasePurposeRepair.paths(value.candidatePaths) && _exact(value.validationIdentity) &&
    value.validationRevision > 0 && value.checks != null &&
    GoalPhaseNativeValidation.Policy(value.validationIdentity, value.validationRevision, value.checks).validateC.isSuccess &&
    _exact(value.closureIdentity) && value.closureRevision > 0 && value.programs != null && value.programs.forall(_ != null) &&
    GoalPhaseNativeClosure.Policy(value.closureIdentity, value.closureRevision,
      value.programs.map(p => GoalPhaseNativeClosure.StepProgram(p.step, p.ordinal))).validateC.isSuccess &&
    value.programs.exists(_.step == GoalPhaseNativeReleaseClosure.programIdentity)
  private def _finding_valid(value: Finding): Boolean = value != null && value.findingOrdinal > 0 && value.findingOrdinal <= 128 &&
    _exact(value.conditionIdentity) && GoalPhasePurposeRepair.text(value.text) && value.issueIdentity != null &&
    value.issueIdentity.matches("issue-[1-9][0-9]{0,4}") && value.issueIdentity.stripPrefix("issue-").toInt <= 16384
  private def _reference(value: ContextReference): Boolean = value != null && _exact(value.identity) && _exact(value.revision)
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n < Long.MaxValue && n.toString == value)
  private def _snapshot(value: O.Snapshot): Boolean = value != null && Vector(value.head, value.tree).forall(oid =>
    oid != null && oid.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})"))
  private def _exact(value: String): Boolean = GoalPhasePurposeRepair.text(value) && value.trim == value && !value.exists(_.isControl)
  private def _check(valid: Boolean, reason: String): Consequence[Unit] = if (valid) Consequence.unit else F.invalid(reason)
}
