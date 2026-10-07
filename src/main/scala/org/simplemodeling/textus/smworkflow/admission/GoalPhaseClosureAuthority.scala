package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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
    candidateRevision: String, requestDigest: String)
  final case class Intent(handle: WorkflowHandle, expectedRevision: StateMachineRevision,
    snapshot: ContextSnapshot, input: Input, idempotencyKey: String)
  final case class Authorization(handle: WorkflowHandle, expectedRevision: StateMachineRevision,
    contextSnapshot: ContextSnapshot, requestDigest: String,
    authorityReference: String, participantIdentity: String, grantedAt: Instant, validUntil: Instant)
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
        "candidateRevision" -> Json.fromString(value.candidateRevision),
        "requestDigest" -> Json.fromString(value.requestDigest))
      def decode(value: Json): Either[String, Input] = for {
        fields <- value.asObject.filter(_.keys.toSet == Set("reason", "candidate", "candidateRevision", "requestDigest"))
          .toRight("invalid GoalPhase authority Decision input")
        why <- fields("reason").flatMap(_.asString).toRight("missing authority reason")
        candidate <- GoalPhaseStepCloseJson.payloadCodec.decode(fields("candidate").get)
        revision <- fields("candidateRevision").flatMap(_.asString).toRight("missing authority candidate revision")
        digest <- fields("requestDigest").flatMap(_.asString).toRight("missing authority request digest")
        input = Input(why, candidate, revision, digest)
        _ <- Either.cond(_validInput(input), (), "authority Decision input differs from its candidate")
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
      response <- _closePlanC(input, review.identity, asOf, handle, plan,
        instances, configuration, snapshot, commit, ports, true, () => Consequence.unit)
    } yield response

  def closeReviewedOrDecisionC(artifact: CandidateAdmissionProducerAbi.Artifact,
    issued: WorkflowInteraction[StepCloseRequest, Nothing],
    submitted: ContinuationResult[ReviewStepResult], asOf: Instant, current: InstanceRecord,
    instances: WorkflowInstancePersistence, configuration: Configuration,
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
    commit: GoalPhaseStepClosureBoundary.CommitPort, ports: Ports, allowCommit: Boolean,
    validateRetained: () => Consequence[Unit]): Consequence[String] =
    if (ports == null || asOf == null || instances == null || configuration == null ||
        snapshot == null || (allowCommit && commit == null) || validateRetained == null)
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
      response <- _closePlanC(input, work.request.requiredOperation, asOf, issued.handle, plan,
        instances, configuration, snapshot, commit, ports, allowCommit, validateRetained)
    } yield response

  /** Native follow-up assesses a retained review without constructing a legacy
    * stop-only request. The caller must finish this read-only guard before any
    * managed native publication and re-admit its exact current predecessor.
    */
  private[admission] def assessReviewedC(artifact: CandidateAdmissionProducerAbi.Artifact,
    issued: WorkflowInteraction[StepCloseRequest, Nothing], submitted: ContinuationResult[ReviewStepResult],
    asOf: Instant, current: InstanceRecord, instances: WorkflowInstancePersistence,
    configuration: Configuration, snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
    authorization: AuthorizationPort, validateRetained: () => Consequence[Unit]): Consequence[Assessment] =
    for {
      plan <- GoalPhaseStepClosureBoundary.planReviewedC(artifact, issued, submitted, asOf, current)
      work <- issued.current match {
        case value: WorkflowContinuation.WorkOrder[StepCloseRequest] => Consequence.success(value)
        case _ => Consequence.stateConflict("GoalPhase authority assessment has no issued review")
      }
      original = work.request.input.get.value
      input = original.copy(reviewEvidence =
        (original.reviewEvidence ++ submitted.result.value.reviewEvidence).distinct)
      assessed <- assessPlanC(input, asOf, issued.handle, plan, instances, configuration,
        snapshot, authorization, validateRetained)
    } yield assessed

  /** Package-local seam for already admitted plans; never accepts caller wire. */
  private[admission] def assessPlanC(input: StepCloseRequest, asOf: Instant, handle: WorkflowHandle,
    plan: GoalPhaseStepClosureBoundary.Plan, instances: WorkflowInstancePersistence,
    configuration: Configuration, snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
    authorization: AuthorizationPort, validateRetained: () => Consequence[Unit]): Consequence[Assessment] =
    _inspectPlanC(input, asOf, handle, plan, instances, configuration, snapshot, authorization,
      validateRetained, (intent, evaluated) =>
        if (evaluated.gaps.isEmpty) Consequence.success(Assessment(intent, AuthorityStatus.Recorded))
        else _routeGapC(evaluated, plan.current).map(_ => Assessment(intent, AuthorityStatus.Missing)))
      .map(_._1)

  private def _routeGapC(evaluated: AdmissionEvaluation[String], current: InstanceRecord): Consequence[Unit] =
    CandidateAdmissionGapRouting.resolveForInstanceC(evaluated,
      Vector(CandidateAdmissionGapRouting.Binding(requirementIdentity,
        CandidateAdmissionGapRouting.Target.AuthorityDecision(requirementIdentity))), current).flatMap { routed =>
      if (routed.size == 1) Consequence.unit
      else Consequence.stateConflict("GoalPhase authority gap routing is ambiguous")
    }

  /** Selection stays between the first reads and their drift checks, preserving
    * the existing stop-only route's exact lookup order. Neither this guard nor
    * native assessment writes state or executes commands.
    */
  private def _inspectPlanC[A](input: StepCloseRequest, asOf: Instant, handle: WorkflowHandle,
    plan: GoalPhaseStepClosureBoundary.Plan, instances: WorkflowInstancePersistence,
    configuration: Configuration, snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
    authorization: AuthorizationPort, validateRetained: () => Consequence[Unit],
    select: (Intent, AdmissionEvaluation[String]) => Consequence[A]
  ): Consequence[(A, GoalPhaseStepClosureBoundary.SnapshotPort)] =
    if (input == null || asOf == null || handle == null || plan == null || plan.current == null || plan.commitRequest == null ||
        instances == null || configuration == null || snapshot == null || authorization == null ||
        validateRetained == null || select == null)
      Consequence.stateConflict("GoalPhase authority assessment context is incomplete")
    else {
      val current = plan.current
      for {
        _ <- validateRetained()
        scoped <- GoalPhaseCallScopedSnapshot.forCurrentC(snapshot, current, input)
        bytes <- scoped.loadC(input.stepIdentity)
        _ <- GoalPhaseStepClosureBoundary.admitSnapshotC(plan.commitRequest, bytes)
        intent <- intentC(input, handle, current)
        authority <- authorization.loadC(intent)
        evaluated <- _evaluateC(intent, asOf, authority)
        selected <- select(intent, evaluated)
        loaded <- instances.load(configuration, current.identity)
        _ <- if (loaded.exists(value => GoalPhaseStepClosureBoundary.sameRecord(current, value))) Consequence.unit
          else Consequence.stateConflict("GoalPhase authority lookup changed the Workflow instance")
        latestBytes <- scoped.loadC(input.stepIdentity)
        _ <- if (latestBytes == bytes) Consequence.unit
          else Consequence.stateConflict("GoalPhase authority lookup changed the candidate bytes")
        latestAuthority <- authorization.loadC(intent)
        _ <- if (latestAuthority == authority) Consequence.unit
          else Consequence.stateConflict("GoalPhase authority evidence changed during lookup")
        _ <- validateRetained()
      } yield (selected, scoped)
    }

  private def _closePlanC(input: StepCloseRequest, review: StateMachineRequiredOperationIdentity,
    asOf: Instant, handle: WorkflowHandle, plan: GoalPhaseStepClosureBoundary.Plan,
    instances: WorkflowInstancePersistence, configuration: Configuration,
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
    commit: GoalPhaseStepClosureBoundary.CommitPort, ports: Ports, allowCommit: Boolean,
    validateRetained: () => Consequence[Unit]): Consequence[String] = {
    for {
      inspected <- _inspectPlanC(input, asOf, handle, plan, instances, configuration, snapshot,
        ports.authorizations, validateRetained, (intent, evaluated) =>
        if (evaluated.gaps.isEmpty) Consequence.success(Option.empty[String])
        else for {
          _ <- _routeGapC(evaluated, plan.current)
          request <- ports.decisions.resolveC(intent)
          _ <- _requestC(intent, request, plan.current, review)
          wire <- WorkflowDecisionJsonV1.encodeC(WorkflowInteraction[Input, Nothing](handle,
            WorkflowContinuation.Decision(request, MinimalPresentation("Step closure authority",
              "Exact local commit authority is unavailable; Step remains open"))), inputCodec)
        } yield Some(wire))
      (decision, scoped) = inspected
      response <- decision match {
        case Some(wire) => Consequence.success(wire)
        case None if !allowCommit =>
          Consequence.stateConflict("GoalPhase read-only retained projection cannot commit or resume authority")
        case None => for {
          closed <- GoalPhaseStepClosureBoundary.commitPlanC(plan, instances, configuration, scoped, commit)
          closedHandle <- WorkflowHandle.fromRecordC(handle.componentIdentity, closed.instance)
          terminal <- projectTerminalC(closedHandle, closed.instance,
            TypedValue("StepCloseResult", closed.receipt.commitIdentity),
            MinimalPresentation("Step Closed", "Committed reviewed and authorized Step"))
          wire <- WorkflowTerminalJsonV1.encodeC(terminal, GoalPhaseStepCloseServiceOperation.terminalCodec)
        } yield wire
      }
    } yield response
  }

  /** Pure correlation shared by projection and immutable stop metadata retention. */
  private[admission] def intentC(input: StepCloseRequest, handle: WorkflowHandle,
    current: InstanceRecord): Consequence[Intent] =
    if (handle == null || current == null) Consequence.stateConflict("Closure intent context is missing")
    else for {
      _ <- handle.validateC
      _ <- current.validateC
      actual <- WorkflowHandle.fromRecordC(handle.componentIdentity, current)
      _ <- if (actual == handle) Consequence.unit else Consequence.stateConflict("Closure intent Handle differs")
      submission <- StepCloseRequestAdapter.submissionC(input)
      digest = _digest(GoalPhaseStepCloseJson.encode(input))
      revision = submission.candidate.reference.revision
    } yield Intent(handle, StateMachineRevision(current.revision.value.toString),
      ContextSnapshot(handle.workflowRevision.value, modelRevision = Some(revision), evidenceRevision = Some(digest)),
      Input(reason, input, revision, digest),
      _digest(s"${handle.instanceIdentity.value}:${current.revision.value}:$digest"))

  private def _evaluateC(intent: Intent, asOf: Instant,
    supplied: Option[Authorization]): Consequence[AdmissionEvaluation[String]] =
    if (supplied == null || supplied.exists(value => value == null || value.handle != intent.handle ||
        value.expectedRevision != intent.expectedRevision || value.contextSnapshot != intent.snapshot ||
        value.requestDigest != intent.input.requestDigest || !_name(value.authorityReference) ||
        !_name(value.participantIdentity) || value.grantedAt == null || value.validUntil == null ||
        value.grantedAt.isAfter(asOf) || value.validUntil.isBefore(asOf) ||
        value.validUntil.isBefore(value.grantedAt)))
      Consequence.stateConflict("GoalPhase commit authority is malformed, stale or outside the exact request")
    else for {
      reference <- StepClosureAdmission.candidateRefC(StepClosureAdmission.StepSnapshot(
        intent.input.candidate.stepIdentity, intent.input.candidate.programDigest, intent.input.candidate.planningDigest))
      scope = TypedValue("goal-phase-closure-authority-scope-v1", intent.input.requestDigest)
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
            !evidence.payload.value.grantedAt.isAfter(asOf) && !evidence.payload.value.validUntil.isBefore(asOf)
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
        request.expectedRevision != intent.expectedRevision || request.context.snapshot != intent.snapshot ||
        request.input != Some(TypedValue(inputCodec.typeIdentity, intent.input)) ||
        request.resultType == null || request.resultType.nonEmpty ||
        request.context.requiredFacts == null || !request.context.requiredFacts.contains(
          ContextReference(intent.input.candidate.stepIdentity, intent.input.candidateRevision)) ||
        request.context.references == null || !request.context.references.contains(
          ContextReference(intent.input.candidate.submissionIdentity, intent.input.requestDigest)) ||
        request.operation == null || request.completionOperation == null ||
        request.operation.service == "GoalPhaseService" || request.completionOperation.service == "GoalPhaseService")
      Consequence.stateConflict("GoalPhase authority Decision host request differs from the frozen intent")
    else Consequence.unit
  }

  private def _validInput(input: Input): Boolean = input != null && input.reason == reason &&
    StepCloseRequestAdapter.submissionC(input.candidate).toOption.exists(value =>
      value.candidate.reference.revision == input.candidateRevision) &&
    input.requestDigest == _digest(GoalPhaseStepCloseJson.encode(input.candidate))
  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
  private def _digest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.getBytes(StandardCharsets.UTF_8)).map(byte => f"${byte & 0xff}%02x").mkString
}
