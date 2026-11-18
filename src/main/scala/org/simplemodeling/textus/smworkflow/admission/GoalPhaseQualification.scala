/**
 * @since   Nov. 17, 2026
 * @version Nov. 18, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.config.{ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.convert.ValueReader
import java.nio.charset.StandardCharsets.UTF_8
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeValidation.Check

/** Finite host-owned qualification inventory. Selection records no authority or
  * successful check; ordinary Advance executes the exact combined selection.
  */
private[smworkflow] object GoalPhaseQualification {
  val describeOperation = "describeGoalPhaseQualification"
  val selectOperation = "selectGoalPhaseQualification"
  val parameter = "sm-workflow.goal-phase.qualification"
  final case class Scope(identity: String, checks: Vector[Check], executionOrder: Option[Vector[String]] = None)
  final case class Policy(identity: String, revision: String, scopes: Vector[Scope])
  final case class Target(sessionId: String, workflowIdentity: String, workflowRevision: String,
    participant: String, frontierRevision: Long, planIdentity: String, planRevision: String,
    scope: String, generation: Long, candidate: O.Snapshot, candidateRevision: Long,
    policyIdentity: String, policyRevision: String, hostPlan: String, required: Vector[Check], supplemental: Vector[Check],
    executionOrder: Option[Vector[String]] = None)
  final case class Selection(target: Target)
  final case class Source(session: F.Session, from: InstanceRecord, controller: O.State,
    candidate: GoalPhaseNativeCandidate.Saved)
  final case class Saved(record: InstanceRecord, selection: Selection)

  private def omitAbsentOrder(value: Json): Json = value.mapObject(fields =>
    if (fields("executionOrder").exists(_.isNull)) fields.remove("executionOrder") else fields)
  given Encoder[Scope] = deriveEncoder[Scope].mapJson(omitAbsentOrder)
  given Decoder[Scope] = deriveDecoder
  given Encoder[Policy] = deriveEncoder
  given Decoder[Policy] = deriveDecoder
  given Encoder[Target] = deriveEncoder[Target].mapJson(omitAbsentOrder)
  given Decoder[Target] = deriveDecoder
  given Encoder[Selection] = deriveEncoder
  given Decoder[Selection] = deriveDecoder

  private def exact(s: String): Boolean = s != null && s.nonEmpty && s.trim == s && !s.exists(_.isControl)
  def checksValid(checks: Vector[Check]): Boolean = checks != null && checks.nonEmpty && checks.size <= 16 &&
    GoalPhaseNativeValidation.Policy("qualification", 1, checks).validateC.isSuccess &&
    GoalPhaseNativeMetadata.buildReferencesValid(checks) &&
    checks.forall(c => c.kind == WorkflowSbtValidation.Kind.TestOnly ||
      (c.kind == WorkflowSbtValidation.Kind.TestCompile && c.metadata.isEmpty &&
        checks.exists(_.metadata.exists(_.buildCheckIdentity.contains(c.identity)))))
  private def orderShapeValid(order: Option[Vector[String]]): Boolean = order != null && order.forall(ids =>
    ids != null && ids.nonEmpty && ids.forall(exact) && ids.distinct == ids)
  private def scopeOrderValid(value: Scope): Boolean = orderShapeValid(value.executionOrder) &&
    value.executionOrder.forall(ids => value.checks.forall(check => ids.contains(check.identity)))
  def policyValid(p: Policy): Boolean = p != null && exact(p.identity) && exact(p.revision) &&
    p.scopes != null && p.scopes.nonEmpty && p.scopes.size <= 16 &&
    p.scopes.forall(s => s != null && exact(s.identity) && checksValid(s.checks) && scopeOrderValid(s)) &&
    p.scopes.map(_.identity).distinct.size == p.scopes.size
  def decodePolicyC(raw: String): Consequence[Policy] =
    io.circe.parser.parse(raw).left.map(_.message).flatMap { json =>
      json.as[Policy].left.map(_.message).flatMap(p => Either.cond(policyValid(p) && p.asJson == json, p,
        "Qualification requires its complete exact finite host inventory"))
    }.fold(F.invalid, Consequence.success)
  val declaration = ComponentParameterKey.optional(parameter, ComponentParameterDecoder[Policy] {
    case ConfigurationValue.StringValue(raw) => decodePolicyC(raw)
    case _ => Consequence.configurationInvalid("Qualification inventory requires an exact JSON string")
  })
  def scope(state: O.State): String = state.plan.steps.lift(state.cursor).map(_.identity)
    .getOrElse("phase:" + state.plan.phase)
  def configured(d: F.Declaration, state: O.State): Option[Scope] =
    d.qualificationPolicy.flatMap(_.scopes.find(_.identity == scope(state)))
  def validationStage(state: O.State): String = if (state.cursor == state.plan.steps.size) "PhaseValidation" else "Validation"
  def triageCompatible(state: O.State): Boolean = state.plan.steps.lift(state.cursor).flatMap(_.actionPolicy)
    .forall(_.semanticTriage == "off")
  def noConsumption(candidate: GoalPhaseNativeCandidate.Payload): Boolean =
    candidate.dependency.isEmpty && candidate.dependencyAdmission.isEmpty && candidate.dependencyOrigin.isEmpty &&
    candidate.dependencySourceDirection.isEmpty && candidate.dependencySourceGuard.isEmpty &&
    candidate.dependencySourceStages.isEmpty && candidate.dependencySourceCapture.isEmpty && candidate.dependencySourceScope.isEmpty
  def combinedC(required: Vector[Check], supplemental: Vector[Check],
    executionOrder: Option[Vector[String]] = None): Consequence[Vector[Check]] = {
    val combined = Option(required).getOrElse(Vector.empty) ++ Option(supplemental).getOrElse(Vector.empty)
    val valid = required != null && required.nonEmpty && required.forall(c => c != null && c.metadata.isEmpty) &&
      checksValid(supplemental) && GoalPhaseNativeValidation.Policy("combined-qualification", 1, combined).validateC.isSuccess &&
      orderShapeValid(executionOrder)
    if (!valid) F.invalid("Qualification order crosses original check identities, ordinals or typed TestOnly bindings")
    else {
      val identities = combined.map(_.identity)
      val requiredIds = required.map(_.identity)
      val order = executionOrder.getOrElse(identities)
      if (order.size != identities.size || order.toSet != identities.toSet ||
          order.filter(requiredIds.contains) != requiredIds)
        F.invalid("Qualification execution order must contain every captured check once and preserve required-check order")
      else {
        val ordered = order.map(id => combined.find(_.identity == id).get)
        if (GoalPhaseNativeMetadata.buildReferencesValid(ordered)) Consequence.success(ordered)
        else F.invalid("Qualification execution order requires every managed build in the successful ordinary preparation prefix")
      }
    }
  }
  // All callers validating a Target use this captured sequence, including
  // historical prefixes. Current configuration never rewrites receipt order.
  def orderedChecks(t: Target): Vector[Check] = {
    val combined = t.required ++ t.supplemental
    t.executionOrder.fold(combined)(ids => ids.flatMap(id => combined.find(_.identity == id)))
  }
  def targetValid(t: Target): Boolean = t != null &&
    Vector(t.sessionId, t.workflowIdentity, t.workflowRevision, t.participant, t.planIdentity, t.planRevision,
      t.scope, t.policyIdentity, t.policyRevision).forall(exact) && t.frontierRevision > 0 &&
    t.frontierRevision < Long.MaxValue && t.generation > 0 && t.candidateRevision > 0 &&
    t.candidateRevision <= t.frontierRevision && t.candidate != null &&
    Vector(t.candidate.head, t.candidate.tree).forall(s => s != null && s.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) &&
    t.hostPlan != null && io.circe.parser.parse(t.hostPlan).toOption.exists(_.asObject.exists(_.nonEmpty)) && t.required != null && combinedC(t.required, t.supplemental, t.executionOrder).isSuccess

  val codec: WorkflowResultJsonV1.PayloadCodec[Selection] = new WorkflowResultJsonV1.PayloadCodec[Selection] {
    val typeIdentity = "GoalPhaseQualificationSelection"
    def encode(value: Selection): Json = Json.obj("schemaVersion" -> "sm-workflow.goal-phase-qualification.v1".asJson,
      "payload" -> value.asJson)
    def decode(value: Json): Either[String, Selection] = for {
      selected <- Option(value).toRight("Qualification selection is absent")
        .flatMap(_.hcursor.get[Selection]("payload").left.map(_.message))
      _ <- Either.cond(targetValid(selected.target) && encode(selected) == value, (), "Qualification selection is incomplete or changed")
    } yield selected
  }
  def cause(from: InstanceRecord): String = "ordered-qualification-selected:" + from.revision.value
  private def reference(from: InstanceRecord) = WorkflowMechanicalTransition.Reference(from.identity.value + ":" + cause(from), "1")
  private def targetRecord(t: Target): Record = GoalPhaseNativeMetadata.record(t.asJson)
  private def targetC(raw: Record): Consequence[Target] =
    if (raw == null) F.invalid("Qualification target is absent") else raw.toJsonStringC.flatMap(text =>
      io.circe.parser.parse(text).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(json =>
        json.as[Target].left.map(_.message).flatMap(t => Either.cond(targetValid(t) && t.asJson == json, t,
          "Qualification target requires its exact complete non-null fields"))).fold(F.invalid, Consequence.success))

  /** Existing resolved host plan capture; CNCF alone interprets grants and programs. */
  private def hostC(core: ActionCall.Core): Consequence[Json] = for {
    raw <- Option(core.executionContext.resolvedParameters).flatMap(_.get(GoalPhaseWholePhaseCandidateRecovery.processPlanKey))
      .map(_.value).map(Consequence.success).getOrElse(F.invalid("Ordinary resolved process-execution plan is absent"))
    plan <- (raw match {
      case ConfigurationValue.StringValue(text) if text != null => io.circe.parser.parse(text).left.map(_.message)
      case value => GoalPhaseWholePhaseCandidateRecovery.configurationJson(value)
    }).fold(F.invalid, Consequence.success)
    _ <- if (plan.asObject.exists(_.nonEmpty)) Consequence.unit else F.invalid("Ordinary resolved process plan is malformed")
  } yield plan

  def targetC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Target] = for {
    _ <- if (source != null && source.from.lifecycle == Lifecycle.Active && source.from.suspension.isEmpty &&
        source.from.currentProgression.contains(ProgressionReference("phase." + validationStage(source.controller))) &&
        source.controller.stage.toString == validationStage(source.controller) &&
        noConsumption(source.candidate.payload)) Consequence.unit
      else F.invalid("Qualification requires unstarted native Validation; dependency consumption needs its separate source owner")
    policy <- d.qualificationPolicy.filter(policyValid).map(Consequence.success)
      .getOrElse(F.invalid("No independent finite qualification inventory is configured"))
    selected <- configured(d, source.controller).map(Consequence.success)
      .getOrElse(F.invalid("No qualification inventory covers this exact Step or Phase"))
    required <- GoalPhaseNativeValidation.selectedChecksC(d, source.controller.plan,
      source.controller.plan.steps.lift(source.controller.cursor).map(_.identity))
    _ <- combinedC(required, selected.checks, selected.executionOrder)
    candidate <- source.candidate.payload.candidate.map(Consequence.success).getOrElse(F.invalid("Qualification candidate is absent"))
    host <- hostC(core)
    t = Target(source.from.identity.value, source.from.definition.workflowIdentity.value,
      source.from.definition.workflowRevision.value, source.session.participantIdentity, source.from.revision.value,
      source.controller.plan.identity, source.controller.plan.revision, scope(source.controller), source.controller.generation,
      candidate, source.candidate.record.revision.value, policy.identity, policy.revision, host.noSpaces, required, selected.checks, selected.executionOrder)
    _ <- if (targetValid(t)) Consequence.unit else F.invalid("Qualification target is invalid")
  } yield t

  def describeC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[V.GoalPhaseQualificationDescriptionResult] = for {
    t <- targetC(source, d, core)
    _ <- GoalPhaseNativeValidation.qualificationUnstartedC(source.session, d, core, source.candidate, source.controller, source.from)
  } yield V.GoalPhaseQualificationDescriptionResult(t.sessionId, "selection-required",
    "Select the exact configured checks and captured execution order; ordinary Advance owns checks, independent review and closing.", targetRecord(t), selectOperation)

  def selectC(source: Source, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhaseQualificationSelectionInput): Consequence[V.GoalPhaseQualificationSelectionResult] = for {
    _ <- if (source.from == source.session.current) Consequence.unit else F.invalid("Qualification frontier is stale")
    actual <- targetC(source, d, core)
    supplied <- targetC(input.target)
    _ <- if (input.sessionId == actual.sessionId && supplied == actual &&
        input.checkIdentities.toVector == actual.supplemental.map(_.identity)) Consequence.unit
      else F.invalid("Qualification selection crosses its target, inventory, order or original required checks")
    _ <- GoalPhaseNativeValidation.qualificationUnstartedC(source.session, d, core, source.candidate, source.controller, source.from)
    _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits(validationStage(source.controller),
        "qualification-selected", validationStage(source.controller))) Consequence.unit else F.invalid("Qualification transition is absent from CML")
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(source.from), source.from,
      source.from.revision, F.history(source.from, "phase." + validationStage(source.controller), cause(source.from)),
      TypedValue(codec.typeIdentity, Selection(actual))),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield V.GoalPhaseQualificationSelectionResult(input.sessionId, true, "selected",
    "Exact qualification inventory and execution order retained; separate ordinary Advance owns execution. No process authority was granted.")

  private def _historical_target_c(source: Source, d: F.Declaration, captured: Target): Consequence[Target] = for {
    _ <- if (source != null && source.from.lifecycle == Lifecycle.Active && source.from.suspension.isEmpty &&
        source.from.currentProgression.contains(ProgressionReference("phase." + validationStage(source.controller))) &&
        source.controller.stage.toString == validationStage(source.controller) &&
        noConsumption(source.candidate.payload) && triageCompatible(source.controller) && targetValid(captured)) Consequence.unit
      else F.invalid("Historical qualification requires its active unsuspended exact Validation source")
    required <- GoalPhaseNativeValidation.selectedChecksC(d, source.controller.plan,
      source.controller.plan.steps.lift(source.controller.cursor).map(_.identity))
    _ <- combinedC(required, captured.supplemental, captured.executionOrder)
    candidate <- source.candidate.payload.candidate.map(Consequence.success).getOrElse(F.invalid("Historical qualification candidate is absent"))
  } yield Target(source.from.identity.value, source.from.definition.workflowIdentity.value,
    source.from.definition.workflowRevision.value, source.session.participantIdentity, source.from.revision.value,
    source.controller.plan.identity, source.controller.plan.revision, scope(source.controller), source.controller.generation,
    candidate, source.candidate.record.revision.value, captured.policyIdentity, captured.policyRevision,
    captured.hostPlan, required, captured.supplemental, captured.executionOrder)

  def retainedC(source: Source, d: F.Declaration, core: ActionCall.Core, historyOnly: Boolean = false): Consequence[Saved] = for {
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- adapter.loadRetainedC(reference(source.from))(using core.executionContext).flatMap(
      _.map(Consequence.success).getOrElse(F.invalid("Qualification history has no retained selection")))
    t <- if (historyOnly) _historical_target_c(source, d, retained.receipt.payload.value.target) else targetC(source, d, core)
    saved = retained.receipt.next
    actual <- GoalPhaseNativeHistory.prefixC(source.session.current, saved.revision)
    expected <- source.from.appendC(source.from.revision,
      F.history(source.from, "phase." + validationStage(source.controller), cause(source.from)))
    _ <- if (actual == saved && expected == saved && retained.receipt.payload.value == Selection(t)) Consequence.unit
      else F.invalid("Qualification selection crosses its exact native predecessor or host inventory")
  } yield Saved(saved, Selection(t))

  /** Validation acquires only the exact immediately preceding native selection.
    * No caller-provided payload or declaration can replace the stored proof.
    */
  def forValidationC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    candidate: GoalPhaseNativeCandidate.Saved, state: O.State, frontier: InstanceRecord,
    historyOnly: Boolean = false): Consequence[Option[Selection]] =
    if (!frontier.causation.value.startsWith("ordered-qualification-selected:")) Consequence.success(None)
    else for {
      predecessor <- GoalPhaseNativeHistory.prefixC(session.current, InstanceRevision(frontier.revision.value - 1))
      retained <- retainedC(Source(session, predecessor, state, candidate), d, core, historyOnly)
      _ <- if (retained.record == frontier) Consequence.unit else F.invalid("Qualification source frontier is crossed")
    } yield Some(retained.selection)

  def originalPrimary(payload: GoalPhaseNativeValidation.Payload): Vector[Check] = {
    val primary = payload.selected.filter(_.dependency.isEmpty)
    payload.qualification.fold(primary)(q => primary.filter(c => q.target.required.exists(_.identity == c.hostIdentity.getOrElse(c.identity))))
  }
  def payloadValid(payload: GoalPhaseNativeValidation.Payload): Boolean = payload.qualification != null &&
    payload.qualification.forall { q =>
      q != null && targetValid(q.target) && payload.selected != null && {
        val t = q.target
        val declared = GoalPhaseNativeMetadata.declaredIdentities(payload.selected)
        val requested = orderedChecks(t)
        val combined = requested.map(_.identity)
        val prefix = requested.takeWhile(_.metadata.isEmpty).map(_.identity)
        payload.assessment != null && !payload.assessment.exists(_.branch == "triage") &&
          payload.candidate == t.candidate && payload.step == t.scope &&
          payload.sourceRevision == (t.frontierRevision + 1).toString &&
          payload.dependency.isEmpty && payload.dependencySource.isEmpty && payload.sourceConsumption.isEmpty &&
          (declared == combined || (declared == prefix && !payload.outcome.contains("passed")))
      }
    }

  def satisfied(d: F.Declaration, state: O.State, payload: Option[GoalPhaseNativeValidation.Payload]): Boolean =
    configured(d, state).forall { selected => payload.exists(p => p.outcome.contains("passed") &&
      p.qualification.exists(q => q.target.supplemental == selected.checks && q.target.executionOrder == selected.executionOrder && q.target.scope == scope(state) &&
        q.target.generation == state.generation && state.candidate.contains(q.target.candidate) &&
        d.qualificationPolicy.exists(policy => policy.identity == q.target.policyIdentity && policy.revision == q.target.policyRevision)) &&
      GoalPhaseNativeValidation.successful(p.receipts, p.selected)) }

  /** Existing authorized business Resource reader; no target-sized argv is required. */
  private def inputForCallC[A: ValueReader](incoming: Request, call: ActionCall.Core,
    keys: Set[String]): Consequence[A] = {
    final class Reader extends ProcedureActionCall {
      val core = call
      def readInputC: Consequence[A] = for {
        source <- GoalPhasePrerequisitePublicationRecovery.resourceInputC(incoming)
        record <- NativeWorkflowResourceInput.readC(source, reference => read_resource_text(reference, Some(UTF_8))
          .flatMap(GoalPhasePrerequisitePublicationRecovery.validateBusinessTextC))
        value <- GoalPhasePrerequisitePublicationRecovery.businessRecordC[A](record, keys)
      } yield value
      def execute(): Consequence[OperationResponse] = F.invalid("Qualification input reader is not an Operation")
    }
    new Reader().readInputC
  }
  def descriptionForCallC(request: Request, core: ActionCall.Core): Consequence[V.GoalPhaseQualificationDescriptionInput] =
    inputForCallC[V.GoalPhaseQualificationDescriptionInput](request, core, Set("sessionId"))
  def selectionForCallC(request: Request, core: ActionCall.Core): Consequence[V.GoalPhaseQualificationSelectionInput] =
    inputForCallC[V.GoalPhaseQualificationSelectionInput](request, core, Set("sessionId", "target", "checkIdentities"))

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseQualificationOperation
      else SmWorkflowComponent.GoalPhaseServiceService.SelectGoalPhaseQualificationOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request != null && request.service.contains("GoalPhaseService") &&
          request.operation == (if (describe) describeOperation else selectOperation))
        Consequence.success(Action(request, describe, port))
      else F.invalid("Qualification Operation identity differs")
  }
  private final case class Action(request: Request, describe: Boolean,
    port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = if (describe) for {
        input <- descriptionForCallC(Action.this.request, core)
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(input.sessionId))
        result <- port.describeQualificationC(input.sessionId, core.copy(executionContext = context))
      } yield NativeWorkflowOperationOutput.response(result)
      else for {
        input <- selectionForCallC(Action.this.request, core)
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(input.sessionId))
        result <- port.selectQualificationC(input, core.copy(executionContext = context))
      } yield NativeWorkflowOperationOutput.response(result)
    }
  }
}
