package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/**
 * Standalone registration: no Workflow transition or human authority.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseRegistrationServiceOperation {
  val operationName = "registerGoalPhaseSubmission"
  val wireSchema = "sm-workflow.goal-phase-registration-command.v1"
  val resultSchema = "sm-workflow.goal-phase-registration-result.v1"

  def encodeCommand(request: GoalPhaseSubmissionRegistration.Request): String = Json.obj(
    "schemaVersion" -> Json.fromString(wireSchema),
    "registrationIdentity" -> Json.fromString(request.registrationIdentity),
    "submissionIdentity" -> Json.fromString(request.submissionIdentity),
    "idempotencyIdentity" -> Json.fromString(request.idempotencyIdentity),
    "start" -> parse(GoalPhaseStartServiceOperation.encodeCommand(request.start)).toOption.get).noSpaces

  def decodeCommandC(wire: String): Consequence[GoalPhaseSubmissionRegistration.Request] = {
    val decoded = for {
      json <- Option(wire).toRight("Registration command JSON is missing")
        .flatMap(value => parse(value).left.map(_ => "Registration command JSON is invalid"))
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "registrationIdentity",
        "submissionIdentity", "idempotencyIdentity", "start")).toRight("Registration command fields differ")
      schema <- root("schemaVersion").flatMap(_.asString).toRight("Registration command schema is missing")
      _ <- Either.cond(schema == wireSchema, (), "Registration command schema differs")
      registration <- root("registrationIdentity").flatMap(_.asString).filter(_canonical).toRight("Registration identity is missing")
      submission <- root("submissionIdentity").flatMap(_.asString).filter(_canonical).toRight("Submission identity is missing")
      idempotency <- root("idempotencyIdentity").flatMap(_.asString).filter(_canonical).toRight("Registration key is missing")
      start <- root("start").toRight("Registration Start command is missing")
    } yield (registration, submission, idempotency, start)
    decoded.fold(Consequence.stateConflict(_), { case (registration, submission, idempotency, start) =>
      GoalPhaseStartServiceOperation.decodeCommandC(start.noSpaces).flatMap { command =>
        if (Vector(command.selectionIdentity, command.participantIdentity, command.idempotencyKey,
            command.input.phaseIdentity, command.input.repositoryIdentity).forall(_canonical))
          Consequence.success(GoalPhaseSubmissionRegistration.Request(registration, submission, idempotency, command))
        else Consequence.stateConflict("Registration Start facts are noncanonical")
      }
    })
  }

  def bindC(declaration: GoalPhaseManagedStartAssembly.Declaration,
    selection: ProfileStartAdmission.CallScopedSelectionPort,
    collection: DataStore.CollectionId): Consequence[spec.OperationDefinition] = {
    val generated = SmWorkflowComponent.GoalPhaseServiceService.RegisterGoalPhaseSubmissionOperation.specification
    if (declaration == null || declaration.configuration == null || declaration.definition == null ||
        declaration.instances == null || declaration.continuations == null || selection == null || collection == null ||
        generated.name != operationName || generated.request.parameters.map(_.name).toSet !=
          Set("registrationIdentity", "submissionIdentity", "idempotencyIdentity", "start"))
      Consequence.configurationInvalid("GoalPhase registration binding differs from generated CML")
    else for {
      _ <- declaration.configuration.validateC
      _ <- declaration.definition.validateC
      _ <- if (collection != declaration.instances && collection != declaration.continuations) Consequence.unit
        else Consequence.configurationInvalid("Registration collection aliases Workflow infrastructure")
    } yield new BoundOperation(declaration, selection, collection, generated)
  }

  private final class BoundOperation(declaration: GoalPhaseManagedStartAssembly.Declaration,
    selection: ProfileStartAdmission.CallScopedSelectionPort, collection: DataStore.CollectionId,
    generated: spec.OperationDefinition.Specification) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(generated, generated.response)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != operationName || !request.service.contains("GoalPhaseService"))
        Consequence.stateConflict("Registration request differs from the CML Operation")
      else WorkflowJsonCommandRequest.commandArguments(request) match {
        case List(Argument("arg1", wire: String, _)) =>
          decodeCommandC(wire).map(value => BoundAction(request, value, declaration, selection, collection))
        case _ => Consequence.stateConflict("Registration command requires one JSON argument")
      }
  }

  private final case class BoundAction(request: Request, registration: GoalPhaseSubmissionRegistration.Request,
    declaration: GoalPhaseManagedStartAssembly.Declaration, selection: ProfileStartAdmission.CallScopedSelectionPort,
    collection: DataStore.CollectionId) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = BoundCall(core, registration, declaration, selection, collection)
  }

  private final case class BoundCall(core: ActionCall.Core, registration: GoalPhaseSubmissionRegistration.Request,
    declaration: GoalPhaseManagedStartAssembly.Declaration, selection: ProfileStartAdmission.CallScopedSelectionPort,
    collection: DataStore.CollectionId) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] =
      GoalPhaseManagedRegistration.registerForCallC(registration, core, declaration, selection, collection).map { stored =>
        OperationResponse.Scalar(Json.obj("schemaVersion" -> Json.fromString(resultSchema),
          "receipt" -> GoalPhaseSubmissionRegistration.receiptJson(stored)).noSpaces)
      }
  }

  private def _canonical(value: String): Boolean = value != null && value.nonEmpty && value.trim == value
}
