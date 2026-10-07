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
import org.simplemodeling.textus.smworkflow.value.StepCloseRequest

/** One registered transport-independent initial registration. No handle,
  * destination, authority, receipt or refresh intent is supplied by a caller.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseArtifactRegistrationServiceOperation {
  val operationName = "registerGoalPhaseArtifacts"
  val wireSchema = "sm-workflow.goal-phase-artifact-registration-command.v1"
  val resultSchema = "sm-workflow.goal-phase-artifact-registration-result.v1"
  final case class Command(subject: StepCloseRequest, selection: GoalPhaseBlobArtifactSnapshot.References)

  def encodeCommand(command: Command): String = Json.obj(
    "schemaVersion" -> Json.fromString(wireSchema),
    "subject" -> parse(GoalPhaseStepCloseJson.encode(command.subject)).toOption.get,
    "selection" -> GoalPhaseArtifactReferenceRecord.referencesJson(command.selection)).noSpaces

  def decodeCommandC(wire: String, maximumBytes: Int): Consequence[Command] = {
    val decoded = for {
      json <- Option(wire).toRight("Artifact command JSON is missing")
        .flatMap(value => parse(value).left.map(_ => "Artifact command JSON is invalid"))
      root <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "subject", "selection"))
        .toRight("Artifact command fields differ")
      _ <- Either.cond(root("schemaVersion").flatMap(_.asString).contains(wireSchema), (), "Artifact command schema differs")
      subject <- root("subject").toRight("Artifact subject is missing")
      selection <- root("selection").toRight("Artifact selection is missing")
    } yield (subject, selection)
    decoded.fold(Consequence.stateConflict(_), { case (subject, selection) => for {
      request <- GoalPhaseStepCloseJson.decodeC(subject.noSpaces)
      _ <- StepCloseRequestAdapter.submissionC(request).map(_ => ())
      references <- GoalPhaseArtifactReferenceRecord.decodeReferencesC(selection, maximumBytes)
      _ <- if (references.program.digest == request.programDigest && references.planning.digest == request.planningDigest)
        Consequence.unit else Consequence.stateConflict("Artifact command content differs from its Step candidate")
    } yield Command(request, references) })
  }

  def bindC(declaration: GoalPhaseManagedStartAssembly.Declaration,
    selection: ProfileStartAdmission.CallScopedSelectionPort, associationCollection: DataStore.CollectionId,
    artifacts: GoalPhaseArtifactReadPolicy.Declaration, maximumBytes: Int): Consequence[spec.OperationDefinition] = {
    val generated = SmWorkflowComponent.GoalPhaseServiceService.RegisterGoalPhaseArtifactsOperation.specification
    if (declaration == null || declaration.configuration == null || declaration.definition == null ||
        declaration.instances == null || declaration.continuations == null || selection == null ||
        associationCollection == null || artifacts == null || artifacts.collection == null ||
        maximumBytes <= 0 || maximumBytes == Int.MaxValue ||
        generated.name != operationName || generated.request.parameters.map(_.name).toSet != Set("subject", "selection"))
      Consequence.configurationInvalid("Artifact Operation requires exact generated CML and explicit host bindings")
    else for {
      _ <- declaration.configuration.validateC
      _ <- declaration.definition.validateC
      _ <- if (artifacts.storeName == declaration.configuration.workflowStore.name &&
          associationCollection != artifacts.collection &&
          !Vector(declaration.instances, declaration.continuations).exists(value =>
            value == associationCollection || value == artifacts.collection)) Consequence.unit
        else Consequence.configurationInvalid("Artifact Operation destinations differ or alias Workflow infrastructure")
    } yield new BoundOperation(declaration, selection, associationCollection, artifacts, maximumBytes, generated)
  }

  private final class BoundOperation(declaration: GoalPhaseManagedStartAssembly.Declaration,
    selection: ProfileStartAdmission.CallScopedSelectionPort, association: DataStore.CollectionId,
    artifacts: GoalPhaseArtifactReadPolicy.Declaration, maximumBytes: Int,
    generated: spec.OperationDefinition.Specification) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(generated, generated.response)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != operationName || !request.service.contains("GoalPhaseService"))
        Consequence.stateConflict("Artifact request differs from its CML Operation")
      else WorkflowJsonCommandRequest.commandArguments(request) match {
        case List(Argument("arg1", wire: String, _)) => decodeCommandC(wire, maximumBytes).map(command =>
          BoundAction(request, command, declaration, selection, association, artifacts, maximumBytes))
        case _ => Consequence.stateConflict("Artifact Operation requires one JSON command")
      }
  }

  private final case class BoundAction(request: Request, command: Command,
    declaration: GoalPhaseManagedStartAssembly.Declaration,
    selection: ProfileStartAdmission.CallScopedSelectionPort, association: DataStore.CollectionId,
    artifacts: GoalPhaseArtifactReadPolicy.Declaration, maximumBytes: Int) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall =
      BoundCall(core, command, declaration, selection, association, artifacts, maximumBytes)
  }

  private final case class BoundCall(core: ActionCall.Core, command: Command,
    declaration: GoalPhaseManagedStartAssembly.Declaration,
    selection: ProfileStartAdmission.CallScopedSelectionPort, association: DataStore.CollectionId,
    artifacts: GoalPhaseArtifactReadPolicy.Declaration, maximumBytes: Int) extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = for {
      registration <- ManagedHostRecordRead.loadC(core, declaration.configuration.workflowStore.name,
        association, command.subject.submissionIdentity, GoalPhaseHostRecordReadPolicy.Purpose.Association)
      handle <- registration.map(record => GoalPhaseSubmissionRegistration.decodeHandleC(command.subject.submissionIdentity, record))
        .getOrElse(Consequence.stateConflict("Artifact Operation requires a registered submission"))
      scope <- GoalPhaseCallScopedSnapshot.scopeC(handle, command.subject, GoalPhaseCallScopedSnapshot.Purpose.Candidate)
      retained <- GoalPhaseManagedArtifactInstallation.installForCallC(scope, command.selection, core,
        declaration, selection, association, artifacts.collection, maximumBytes)
    } yield OperationResponse.Scalar(Json.obj("schemaVersion" -> Json.fromString(resultSchema),
      "receipt" -> Json.obj("typeIdentity" -> Json.fromString("GoalPhaseArtifactRegistrationReceipt"),
        "componentIdentity" -> Json.fromString(handle.componentIdentity.name),
        "workflowIdentity" -> Json.fromString(handle.workflowIdentity.value),
        "workflowRevision" -> Json.fromString(handle.workflowRevision.value),
        "instanceIdentity" -> Json.fromString(handle.instanceIdentity.value),
        "submissionIdentity" -> Json.fromString(scope.request.submissionIdentity),
        "stepIdentity" -> Json.fromString(scope.request.stepIdentity),
        "sourceCandidateRevision" -> Json.fromString(scope.candidateRevision),
        "selection" -> GoalPhaseArtifactReferenceRecord.referencesJson(retained))).noSpaces)
  }
}
