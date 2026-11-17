package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.security.SecuritySubject
import io.circe.Json
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V

/** The public caller selects a declared target and returns bounded semantics.
  * Process selection, effect admission, completion and recollection are runtime work.
  * @since   Oct. 26, 2026
  *  version Oct. 27, 2026
  *  version Nov.  2, 2026
  * @version Nov. 18, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object LocalPublicationService {
  private val _declared = SmWorkflowComponent.LocalPublicationServiceService
  def bindC(creation: ComponentCreate, d: GoalPhaseNativeRun.Declaration, definition: DefinitionBinding): Consequence[spec.ServiceDefinition] =
    NativeExternalEvidence.bindBoundedC(creation.configuration, BoundedWorkRuntime.host(d)).map(reader =>
      bind(d.localPublication, (id, core) => GoalPhaseNativeRun.retainedSessionC(id, d, definition, core)
        .flatMap(session => WorkflowForceClosure.requireOpenC(session.current)), Some(reader)))
  def bind(d: GoalPhaseNativeRun.Declaration, definition: DefinitionBinding): spec.ServiceDefinition =
    bind(d.localPublication, (id, core) => GoalPhaseNativeRun.retainedSessionC(id, d, definition, core)
      .flatMap(session => WorkflowForceClosure.requireOpenC(session.current)))

  def bind(policy: Option[LocalPublicationRuntime.Policy],
    owner: (String, ActionCall.Core) => Consequence[Unit], reader: Option[NativeExternalEvidence.Reader] = None): spec.ServiceDefinition =
    NativeWorkflowDescriptionMetadata.service(_declared, spec.OperationDefinitionGroup(NonEmptyVector.of(
      new Operation(false, policy, owner), new Operation(true, policy, owner),
      new LatestCollectionOperation(policy, owner), new RecoveryOperation("describeLocalPublicationRecovery", policy, reader),
      new RecoveryOperation("admitLocalPublicationRecovery", policy, reader),
      new RecoveryOperation("advanceLocalPublicationRecovery", policy, reader))))

  private final case class RecoveryInput(requestIdentity: String, targetIdentity: String,
    successorIdentity: String, explicit: Boolean, answer: Option[Json] = None)
  private final class RecoveryOperation(operation: String, policy: Option[LocalPublicationRuntime.Policy],
    reader: Option[NativeExternalEvidence.Reader]) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(_declared.operations.operations.find(_.name == operation).get)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains(_declared.name) || request.operation != name)
        Consequence.stateConflict("Standalone recovery requires its declared operation")
      else NativeWorkflowOperationInput.recordC(request, "input").flatMap { record =>
        val json = DevelopmentOperationalView.json(record)
        if (!DevelopmentOperationalView.uniqueFields(record) || !json.asObject.exists(_.keys.forall(
          Set("requestIdentity", "targetIdentity", "successorIdentity", "explicit", "answer"))))
          Consequence.valueInvalid("Invalid standalone recovery input")
        else json.as[RecoveryInput].fold(e => Consequence.valueInvalid(e.message), Consequence.success)
      }.map(input => RecoveryCall(request, input, operation, policy, reader))
  }
  private final case class RecoveryCall(request: Request, input: RecoveryInput, operation: String,
    policy: Option[LocalPublicationRuntime.Policy], reader: Option[NativeExternalEvidence.Reader]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        selected <- policy.map(Consequence.success).getOrElse(Consequence.configurationInvalid("Publication policy is absent"))
        _ <- LocalPublicationRuntime.validateC(selected)
        target <- selected.targets.find(_.identity == input.targetIdentity).map(Consequence.success)
          .getOrElse(Consequence.configurationInvalid("Publication target is not declared"))
        participant = SecuritySubject.from(core.executionContext.security).subjectId
        component = core.component.get.core.instanceId.canonicalKey
        port = LocalPublicationRuntime._port(core, selected)
        original = LocalPublication.Input(input.requestIdentity, input.targetIdentity, explicit = input.explicit)
        result <- operation match {
          case "describeLocalPublicationRecovery" if input.answer.isEmpty =>
            LocalPublicationRecovery.describeC(component, original, input.successorIdentity, target, participant, port)
              .map(t => V.LocalPublicationResult("direction-required", "Original publication remains UNKNOWN; writer lifecycle is unproven", Some(GoalPhaseNativeMetadata.record(t.asJson)), None))
          case "admitLocalPublicationRecovery" => for {
            raw <- input.answer.map(Consequence.success).getOrElse(Consequence.valueInvalid("Human answer is absent"))
            normalized <- GoalPhaseNativeMetadata.normalizePublicJson(raw).fold(Consequence.valueInvalid(_), Consequence.success)
            answer <- normalized.as[LocalPublicationRecovery.Answer].fold(e => Consequence.valueInvalid(e.message), Consequence.success)
            _ <- if (answer.target.unknown.request.input == original && answer.target.successorIdentity == input.successorIdentity) Consequence.unit
              else Consequence.stateConflict("Answer crosses the selected original or successor")
            evidence <- reader.map(Consequence.success).getOrElse(Consequence.securityPermissionDenied("Independent evidence reader is absent"))
            direction <- LocalPublicationRecovery.admitC(answer, target, participant, component, port, binding =>
              evidence.loadC(core, NativeExternalEvidence.Purpose.StandalonePublicationRecovery,
                ThreeWorkflowExternalEvidencePorts.key(answer.reference.identity, answer.reference.revision), binding).flatMap {
                  case Some(_) => Consequence.unit
                  case None => Consequence.securityPermissionDenied("Independent Human direction is absent")
                })
          } yield V.LocalPublicationResult("admitted", "One successor direction was retained; no publication executed", Some(GoalPhaseNativeMetadata.record(direction.asJson)), None)
          case "advanceLocalPublicationRecovery" if input.answer.isEmpty =>
            LocalPublicationRecovery.advanceC(input.successorIdentity, original, target, participant, component, port).flatMap(_result_c(core, selected, _, false))
          case _ => Consequence.valueInvalid("Invalid standalone recovery operation or answer")
        }
      } yield NativeWorkflowOperationOutput.response(result)
    }
  }

  private final class LatestCollectionOperation(policy: Option[LocalPublicationRuntime.Policy],
    owner: (String, ActionCall.Core) => Consequence[Unit]) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(_declared.CollectLatestLocalPublicationOperation)
    private val _parser = new io.circe.jawn.JawnParser(None, false)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains(_declared.name) || request.operation != name)
        Consequence.stateConflict("Local publication requires its declared service and Operation")
      else (request.toRecord.getAny("input") match {
        case Some(raw: String) => _parser.parse(raw).fold(
          _ => Consequence.valueInvalid("Invalid or duplicate latest publication input fields"), _ => Consequence.unit)
        case _ => Consequence.unit
      }).flatMap(_ => NativeWorkflowOperationInput.recordC(request, "input")).flatMap { record =>
        if (!DevelopmentOperationalView.uniqueFields(record)) Consequence.valueInvalid("Duplicate publication input fields")
        else LocalPublication.decodeLatestCollectionInputC(DevelopmentOperationalView.json(record))
      }.map(input => LatestCollectionCallAction(request, input, policy, owner))
  }

  private final case class LatestCollectionCallAction(request: Request, input: LocalPublication.LatestCollectionInput,
    policy: Option[LocalPublicationRuntime.Policy], owner: (String, ActionCall.Core) => Consequence[Unit]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        _ <- owner(input.ownerSessionId, core)
        result <- policy match {
          case None => Consequence.success(V.LocalPublicationResult("not-configured", "No publication target is configured", None, None))
          case Some(policy) => LocalPublicationRuntime.collectLatestC(core, policy, input).flatMap {
            case Some(r) => _result_c(core, policy, r, true)
            case None => Consequence.success(V.LocalPublicationResult("not-found", "No publication attempt is retained for this target; no execution", None, None))
          }
        }
      } yield NativeWorkflowOperationOutput.response(result)
    }
  }

  private final class Operation(collect: Boolean, policy: Option[LocalPublicationRuntime.Policy],
    owner: (String, ActionCall.Core) => Consequence[Unit]) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (collect)
      _declared.CollectLocalPublicationOperation else _declared.EnsureLocalPublicationOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains(_declared.name) || request.operation != name)
        Consequence.stateConflict("Local publication requires its declared service and Operation")
      else NativeWorkflowOperationInput.recordC(request, "input").flatMap { record =>
        if (!DevelopmentOperationalView.uniqueFields(record)) Consequence.valueInvalid("Duplicate publication input fields")
        else LocalPublication.decodeInputC(DevelopmentOperationalView.json(record)).flatMap(input =>
          if (collect && input.answer.nonEmpty) Consequence.valueInvalid("Collection accepts no semantic answer")
          else Consequence.success(input))
      }.map(input => CallAction(request, input, collect, policy, owner))
  }
  private final case class CallAction(request: Request, input: LocalPublication.Input, collect: Boolean,
    policy: Option[LocalPublicationRuntime.Policy], owner: (String, ActionCall.Core) => Consequence[Unit]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        _ <- input.ownerSessionId.fold(Consequence.unit)(id => owner(id, core))
        result <- policy match {
          case None => Consequence.success(V.LocalPublicationResult("not-configured", "No publication target is configured", None, None))
          case Some(policy) =>
            val value = if (collect) LocalPublicationRuntime.collectC(core, policy, input)
              else LocalPublicationRuntime.ensureC(core, policy, input).map(Some(_))
            value.flatMap {
              case Some(r) => _result_c(core, policy, r, collect)
              case None => Consequence.success(V.LocalPublicationResult("not-found", "No publication request is retained; no execution", None, None))
            }
        }
      } yield NativeWorkflowOperationOutput.response(result)
    }
  }
  private def _result_c(core: ActionCall.Core, policy: LocalPublicationRuntime.Policy,
    result: LocalPublication.Result, collect: Boolean): Consequence[V.LocalPublicationResult] =
    (if (collect) LocalPublicationRuntime.collectApplicabilityC(core, policy, result)
      else Consequence.success(result.applicability)).map(fact => V.LocalPublicationResult(result.status, result.reason,
      Some(GoalPhaseNativeMetadata.record(result.asJson)), fact.map(f => GoalPhaseNativeMetadata.record(f.asJson))))
}
