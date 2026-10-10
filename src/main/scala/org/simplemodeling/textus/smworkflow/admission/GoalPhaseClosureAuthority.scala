package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.time.Instant
import domain.statemachine.workflow.GoalPhaseWorkflowStateMachineWorkflow1
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.CandidateAdmissionModel.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.value.{ReviewStepResult, StepCloseRequest}

/** Fixture closure authority gate. Host evidence is read-only; missing authority
  * produces a stop-only Decision, never approval. This gate cannot publish
  * review/repair WorkOrders or resume human results.
  * @since   Sep. 29, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseClosureAuthority {
  val requirementIdentity = "goal-phase-step-close-authority"
  val reason = "step-close-authority-unavailable"
  final case class Input(reason: String, candidate: StepCloseRequest,
    candidateRevision: String)
  final case class Intent(handle: WorkflowHandle, expectedRevision: StateMachineRevision,
    snapshot: ContextSnapshot, input: Input, idempotencyKey: String)
  final case class Authorization(handle: WorkflowHandle, expectedRevision: StateMachineRevision,
    contextSnapshot: ContextSnapshot,
    authorityReference: String, participantIdentity: String, grantedAt: Instant)
  enum AuthorityStatus {
    case Recorded, Missing
  }
  /** Read-only observation, not an execution grant or a suspension capability. */
  final case class Assessment(intent: Intent, status: AuthorityStatus)
  trait AuthorizationPort {
    /** Load already recorded exact local-commit authority. Do not create it. */
    def loadC(intent: Intent): Consequence[Option[Authorization]]
  }
  trait DecisionPort {
    /** Retain stable host-owned identities for this exact stopped intent.
      * These identities are metadata, not callable Provided Operations.
      */
    def resolveC(intent: Intent): Consequence[ContinuationRequest[Input]]
  }
  trait CallScopedAuthorizationPort extends AuthorizationPort {
    final def loadC(intent: Intent): Consequence[Option[Authorization]] =
      Consequence.stateConflict("GoalPhase closure authority requires a current Operation call")
    def loadForCallC(intent: Intent, core: ActionCall.Core): Consequence[Option[Authorization]]
  }
  trait CallScopedDecisionPort extends DecisionPort {
    final def resolveC(intent: Intent): Consequence[ContinuationRequest[Input]] =
      Consequence.stateConflict("GoalPhase closure Decision requires a current Operation call")
    def resolveForCallC(intent: Intent, core: ActionCall.Core): Consequence[ContinuationRequest[Input]]
  }
  final case class Ports(authorizations: AuthorizationPort, decisions: DecisionPort) {
    def validateC: Consequence[Unit] =
      if (authorizations == null || decisions == null)
        Consequence.configurationInvalid("GoalPhase closure authority ports are incomplete")
      else Consequence.unit

    /** Ephemeral view for one Operation execution, never a factory-held context. */
    def forCall(core: ActionCall.Core): Ports = Ports(
      authorizations match {
        case managed: CallScopedAuthorizationPort => new AuthorizationPort {
          def loadC(intent: Intent): Consequence[Option[Authorization]] =
            managed.loadForCallC(intent, core)
        }
        case existing => existing
      },
      decisions match {
        case managed: CallScopedDecisionPort => new DecisionPort {
          def resolveC(intent: Intent): Consequence[ContinuationRequest[Input]] =
            managed.resolveForCallC(intent, core)
        }
        case existing => existing
      })
  }

  val inputCodec: WorkflowResultJsonV1.PayloadCodec[Input] =
    new WorkflowResultJsonV1.PayloadCodec[Input] {
      val typeIdentity = "GoalPhaseClosureAuthorityInput"
      def encode(value: Input): Json = Json.obj(
        "reason" -> Json.fromString(value.reason),
        "candidate" -> GoalPhaseStepCloseJson.payloadCodec.encode(value.candidate),
        "candidateRevision" -> Json.fromString(value.candidateRevision))
      def decode(value: Json): Either[String, Input] = for {
        fields <- value.asObject.filter { root =>
          val required = Set("reason", "candidate", "candidateRevision")
          root.keys.toSet == required || root.keys.toSet == required + "requestDigest"
        }
          .toRight("invalid GoalPhase authority Decision input")
        why <- fields("reason").flatMap(_.asString).toRight("missing authority reason")
        candidate <- GoalPhaseStepCloseJson.payloadCodec.decode(fields("candidate").get)
        revision <- fields("candidateRevision").flatMap(_.asString).toRight("missing authority candidate revision")
        input = Input(why, candidate, revision)
        _ <- admitInputC(input).toOption.toRight("authority Decision input differs from its candidate")
      } yield input
    }

  /** Only the exact pre-reviewed public request is supported. A missing/stale
    * review is rejected before authority lookup, not converted into approval or
    * silently mixed with the legacy automatic review/repair composition.
    */
  def closeOrDecisionC(input: StepCloseRequest, review: StateMachineRequiredOperation,
    asOf: Instant, handle: WorkflowHandle, current: InstanceRecord,
    instances: WorkflowInstancePersistence, configuration: Configuration,
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
    commit: GoalPhaseStepClosureBoundary.CommitPort, ports: Ports): Consequence[String] =
    if (ports == null || asOf == null || instances == null || snapshot == null || commit == null)
      Consequence.stateConflict("GoalPhase closure authority context is incomplete")
    else for {
      _ <- ports.validateC
      plan <- GoalPhaseStepClosureBoundary.planPreReviewedC(input, review, asOf, handle, current)
      response <- _closePlanC(input, review.identity, handle, plan,
        instances, configuration, snapshot, commit, ports, true)
    } yield response

  def closeReviewedOrDecisionC(artifact: CandidateAdmissionProducerAbi.Artifact,
    issued: WorkflowInteraction[StepCloseRequest, Nothing],
    submitted: ContinuationResult[ReviewStepResult], asOf: Instant, current: InstanceRecord,
    instances: WorkflowInstancePersistence, configuration: Configuration,
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
    commit: GoalPhaseStepClosureBoundary.CommitPort, ports: Ports, allowCommit: Boolean): Consequence[String] =
    if (ports == null || asOf == null || instances == null || configuration == null ||
        snapshot == null || (allowCommit && commit == null))
      Consequence.stateConflict("GoalPhase reviewed authority context is incomplete")
    else for {
      _ <- ports.validateC
      plan <- GoalPhaseStepClosureBoundary.planReviewedC(artifact, issued, submitted, asOf, current)
      work <- issued.current match {
        case value: WorkflowContinuation.WorkOrder[StepCloseRequest] => Consequence.success(value)
        case _ => Consequence.stateConflict("GoalPhase authority has no issued review WorkOrder")
      }
      original = work.request.input.get.value
      input = original.copy(reviewEvidence =
        (original.reviewEvidence ++ submitted.result.value.reviewEvidence).distinct)
      _ <- StepCloseRequestAdapter.submissionC(input)
      response <- _closePlanC(input, work.request.requiredOperation, issued.handle, plan,
        instances, configuration, snapshot, commit, ports, allowCommit)
    } yield response

  /** Native follow-up assesses a retained review without constructing a legacy
    * stop-only request. The caller must finish this read-only guard before any
    * managed native publication. Publication owns its actual state/CAS boundary;
    * this observation is neither permission nor an unchanged-content witness.
    */
  private[admission] def assessReviewedC(artifact: CandidateAdmissionProducerAbi.Artifact,
    issued: WorkflowInteraction[StepCloseRequest, Nothing], submitted: ContinuationResult[ReviewStepResult],
    asOf: Instant, current: InstanceRecord, authorization: AuthorizationPort): Consequence[Assessment] =
    for {
      plan <- GoalPhaseStepClosureBoundary.planReviewedC(artifact, issued, submitted, asOf, current)
      work <- issued.current match {
        case value: WorkflowContinuation.WorkOrder[StepCloseRequest] => Consequence.success(value)
        case _ => Consequence.stateConflict("GoalPhase authority assessment has no issued review")
      }
      original = work.request.input.get.value
      input = original.copy(reviewEvidence =
        (original.reviewEvidence ++ submitted.result.value.reviewEvidence).distinct)
      assessed <- assessPlanC(input, issued.handle, plan, authorization)
    } yield assessed

  /** Package-local seam for already admitted plans; never accepts caller wire. */
  private[admission] def assessPlanC(input: StepCloseRequest, handle: WorkflowHandle,
    plan: GoalPhaseStepClosureBoundary.Plan, authorization: AuthorizationPort): Consequence[Assessment] =
    _inspectPlanC(input, handle, plan, authorization, (intent, evaluated) =>
        if (evaluated.gaps.isEmpty) Consequence.success(Assessment(intent, AuthorityStatus.Recorded))
        else _routeGapC(evaluated, plan.current).map(_ => Assessment(intent, AuthorityStatus.Missing)))

  private def _routeGapC(evaluated: AdmissionEvaluation[String], current: InstanceRecord): Consequence[Unit] =
    CandidateAdmissionGapRouting.resolveForInstanceC(evaluated,
      Vector(CandidateAdmissionGapRouting.Binding(requirementIdentity,
        CandidateAdmissionGapRouting.Target.AuthorityDecision(requirementIdentity))), current).flatMap { routed =>
      if (routed.size == 1) Consequence.unit
      else Consequence.stateConflict("GoalPhase authority gap routing is ambiguous")
    }

  /** Consume one actual authority observation for an admitted plan. No saved
    * result callback, artifact preflight, post-lookup instance/grant reload or
    * unchanged-content proof. An Assessment does not authorize a later effect.
    */
  private def _inspectPlanC[A](input: StepCloseRequest, handle: WorkflowHandle,
    plan: GoalPhaseStepClosureBoundary.Plan, authorization: AuthorizationPort,
    select: (Intent, AdmissionEvaluation[String]) => Consequence[A]
  ): Consequence[A] =
    if (input == null || handle == null || plan == null || plan.current == null || plan.commitRequest == null ||
        authorization == null || select == null)
      Consequence.stateConflict("GoalPhase authority assessment context is incomplete")
    else {
      val current = plan.current
      for {
        intent <- intentC(input, handle, current)
        authority <- authorization.loadC(intent)
        evaluated <- _evaluateC(intent, authority)
        selected <- select(intent, evaluated)
      } yield selected
    }

  private def _closePlanC(input: StepCloseRequest, review: StateMachineRequiredOperationIdentity,
    handle: WorkflowHandle, plan: GoalPhaseStepClosureBoundary.Plan,
    instances: WorkflowInstancePersistence, configuration: Configuration,
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
    commit: GoalPhaseStepClosureBoundary.CommitPort, ports: Ports, allowCommit: Boolean): Consequence[String] = {
    for {
      decision <- _inspectPlanC(input, handle, plan, ports.authorizations, (intent, evaluated) =>
        if (evaluated.gaps.isEmpty) Consequence.success(Option.empty[String])
        else for {
          _ <- _routeGapC(evaluated, plan.current)
          request <- ports.decisions.resolveC(intent)
          _ <- _requestC(intent, request, plan.current, review)
          wire <- WorkflowDecisionJsonV1.encodeC(WorkflowInteraction[Input, Nothing](handle,
            WorkflowContinuation.Decision(request, MinimalPresentation("Step closure authority",
              "Exact local commit authority is unavailable; Step remains open"))), inputCodec)
        } yield Some(wire))
      response <- decision match {
        case Some(wire) => Consequence.success(wire)
        case None if !allowCommit =>
          Consequence.stateConflict("GoalPhase read-only retained projection cannot commit or resume authority")
        case None => for {
          // Actual command ingress, not a certificate after read-only observation.
          // The fixture command and subsequent append are still separate effects.
          executing <- instances.load(configuration, plan.current.identity).flatMap {
            case Some(value) if value.revision == plan.current.revision &&
                value.lifecycle == Lifecycle.Active && value.suspension.isEmpty &&
                value.currentProgression.contains(ProgressionReference("step.Awaiting")) => Consequence.success(value)
            case _ => Consequence.stateConflict("GoalPhase commit target is not at its expected Awaiting revision")
          }
          target <- WorkflowHandle.fromRecordC(handle.componentIdentity, executing)
          _ <- if (target == handle) Consequence.unit
            else Consequence.stateConflict("GoalPhase commit target has a different executable Workflow reference")
          scoped <- GoalPhaseCallScopedSnapshot.forCurrentC(snapshot, executing, input)
          closed <- GoalPhaseStepClosureBoundary.commitPlanC(plan.copy(current = executing), instances, configuration, scoped, commit)
          closedHandle <- WorkflowHandle.fromRecordC(handle.componentIdentity, closed.instance)
          terminal <- projectTerminalC(closedHandle, closed.instance,
            TypedValue("StepCloseResult", closed.receipt.commitIdentity),
            MinimalPresentation("Step Closed", "Committed reviewed and authorized Step"))
          wire <- WorkflowTerminalJsonV1.encodeC(terminal, GoalPhaseStepCloseServiceOperation.terminalCodec)
        } yield wire
      }
    } yield response
  }

  /** Domain address for the selected completion, not a content certificate.
    * Previously allocated historical identities remain opaque read references;
    * this producer neither rehashes old payloads nor invents their lookup mapping.
    */
  private[admission] def intentC(input: StepCloseRequest, handle: WorkflowHandle,
    current: InstanceRecord): Consequence[Intent] =
    if (handle == null || current == null) Consequence.stateConflict("Closure intent context is missing")
    else for {
      _ <- handle.validateC
      _ <- current.validateC
      actual <- WorkflowHandle.fromRecordC(handle.componentIdentity, current)
      _ <- if (actual == handle) Consequence.unit else Consequence.stateConflict("Closure intent Handle differs")
      submission <- StepCloseRequestAdapter.submissionC(input)
      revision = submission.candidate.reference.revision
      address = Vector(handle.componentIdentity.name, handle.workflowIdentity.value,
        handle.workflowRevision.value, current.identity.value, current.revision.value.toString,
        input.submissionIdentity, input.stepIdentity)
        .map(value => s"${value.getBytes(StandardCharsets.UTF_8).length}:$value").mkString
    } yield Intent(handle, StateMachineRevision(current.revision.value.toString),
      ContextSnapshot(handle.workflowRevision.value, modelRevision = Some(revision)),
      Input(reason, input, revision), "closure:" + address)

  private def _evaluateC(intent: Intent, supplied: Option[Authorization]): Consequence[AdmissionEvaluation[String]] =
    if (supplied == null || supplied.exists(value => value == null || value.handle != intent.handle ||
        value.expectedRevision != intent.expectedRevision || !sameContextScope(value.contextSnapshot, intent.snapshot) ||
        !_name(value.authorityReference) || !_name(value.participantIdentity) || value.grantedAt == null))
      Consequence.stateConflict("GoalPhase commit authority is malformed or outside the current scope")
    else for {
      reference <- StepClosureAdmission.candidateRefC(StepClosureAdmission.StepSnapshot(
        intent.input.candidate.stepIdentity, intent.input.candidate.programDigest, intent.input.candidate.planningDigest))
      scope = TypedValue("goal-phase-closure-authority-scope-v2", intent.input.candidate.submissionIdentity)
      evidence = supplied.toVector.map(value => AdmissionEvidence(value.authorityReference, requirementIdentity,
        reference, TypedValue("goal-phase-closure-authority-v1", value), scope, value.participantIdentity))
      submission <- AdmissionSubmission(intent.input.candidate.submissionIdentity,
        CandidateSnapshot(reference, TypedValue(StepClosureAdmission.candidateType,
          StepClosureAdmission.StepSnapshot(intent.input.candidate.stepIdentity,
            intent.input.candidate.programDigest, intent.input.candidate.planningDigest))), evidence).validateC
      evaluated <- CandidateAdmissionModel.evaluateC(submission,
        Vector(AdmissionRequirement(requirementIdentity, RequirementKind.Authority,
          "goal-phase-closure-authority-v1", scope)), new EvidencePolicy[Authorization, String] {
          def isFresh(evidence: AdmissionEvidence[Authorization, String], target: CandidateRef): Boolean =
            true
          def covers(evidence: AdmissionEvidence[Authorization, String], requirement: AdmissionRequirement[String]): Boolean =
            evidence.scope == requirement.scope
        })
    } yield evaluated

  private def _requestC(intent: Intent, request: ContinuationRequest[Input], current: InstanceRecord,
    review: StateMachineRequiredOperationIdentity): Consequence[Unit] = {
    val semanticCapabilities = Vector(review.capability) ++
      GoalPhaseWorkflowStateMachineWorkflow1.workflow.requiredOperations.map(_.identity.capability)
    if (request == null || request.context == null || request.requiredOperation == null ||
        semanticCapabilities.contains(request.requiredOperation.capability) ||
        request.runId != StateMachineRunIdentity(current.identity.value) ||
        request.expectedRevision != intent.expectedRevision || !sameContextScope(request.context.snapshot, intent.snapshot) ||
        !request.input.exists(value => value != null && value.typeIdentity == inputCodec.typeIdentity && sameInputScope(value.value, intent.input)) ||
        request.resultType == null || request.resultType.nonEmpty ||
        request.context.requiredFacts == null || !request.context.requiredFacts.contains(
          ContextReference(intent.input.candidate.stepIdentity, intent.input.candidateRevision)) ||
        request.operation == null || request.completionOperation == null ||
        request.operation.service == "GoalPhaseService" || request.completionOperation.service == "GoalPhaseService")
      Consequence.stateConflict("GoalPhase authority Decision host request differs from the frozen intent")
    else Consequence.unit
  }

  private[admission] def sameContextScope(left: ContextSnapshot, right: ContextSnapshot): Boolean =
    left != null && right != null && left.workflowRevision == right.workflowRevision && left.modelRevision == right.modelRevision

  private[admission] def sameInputScope(left: Input, right: Input): Boolean =
    left != null && right != null && left.reason == right.reason && left.candidate != null && right.candidate != null &&
      left.candidate.submissionIdentity == right.candidate.submissionIdentity &&
      left.candidate.stepIdentity == right.candidate.stepIdentity && left.candidateRevision == right.candidateRevision

  private[admission] def sameIntentScope(left: Intent, right: Intent): Boolean =
    left != null && right != null && left.handle == right.handle && left.expectedRevision == right.expectedRevision &&
      left.idempotencyKey == right.idempotencyKey && sameContextScope(left.snapshot, right.snapshot) &&
      sameInputScope(left.input, right.input)

  private[admission] def admitInputC(input: Input): Consequence[Unit] =
    if (input == null || input.reason != reason)
      Consequence.stateConflict("Closure authority input is absent or has an unsupported reason")
    else StepCloseRequestAdapter.submissionC(input.candidate).flatMap { value =>
      if (value.candidate.reference.revision == input.candidateRevision) Consequence.unit
      else Consequence.stateConflict("Closure authority input has a different candidate target")
    }
  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
}
