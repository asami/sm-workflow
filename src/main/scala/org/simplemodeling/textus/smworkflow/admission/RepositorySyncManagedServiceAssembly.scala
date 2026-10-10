package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import domain.statemachine.providedapi.RepositorySyncWorkflowStateMachineProvidedApi3
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.RepositorySyncStartCommand
import RepositorySyncManagedFetch as Fetch

/** Additive trusted-host entry/owning-advance pair, not default installation.
  * Captures declarations/selection reader only, never a provider or call lease.
  * @since   Oct.  6, 2026
  * @version Oct. 10, 2026
  */
private[smworkflow] object RepositorySyncManagedServiceAssembly {
  val advanceSchema = "sm-workflow.repository-sync-advance.v1"
  def encodeAdvance(identity: String): String = Json.obj("schemaVersion" -> Json.fromString(advanceSchema),
    "instanceIdentity" -> Json.fromString(identity)).noSpaces
  def decodeAdvanceC(raw: String): Consequence[String] = io.circe.parser.parse(Option(raw).getOrElse("")).fold(
    _ => Consequence.stateConflict("RepositorySync advance JSON is invalid"), json =>
      if (json.hcursor.get[String]("schemaVersion").contains(advanceSchema))
        json.hcursor.get[String]("instanceIdentity").toOption
          .map(Consequence.success).getOrElse(Consequence.stateConflict("RepositorySync advance identity is invalid"))
      else Consequence.stateConflict("RepositorySync advance selects no stage, argument vector or receipt"))

  /** Normal host-record composition: no injected in-memory selection callback.
    * The independent effect/review/Human ports still belong to trusted assembly;
    * reading a selection does not install or synthesize any of those grants.
    */
  def bindHostC(params: ComponentCreate, declaration: Fetch.Declaration,
    acknowledgement: () => Consequence[Unit] = () => Consequence.unit,
    nativeProgression: Boolean = false,
    nativeAcknowledgement: String => Consequence[Unit] = _ => Consequence.unit,
    conflictPolicy: RepositorySyncNativeConflictInput.Policy = RepositorySyncNativeConflictInput.noSemanticPaths,
    validationPolicy: Option[RepositorySyncNativeMergeValidation.Policy] = None,
    nativeReview: Option[RepositorySyncNativeMergeReview.Installation] = None,
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort] = None,
    humanConflict: Option[RepositorySyncHumanConflict.Installation] = None): Consequence[spec.ServiceDefinition] =
    if (params == null)
      Consequence.configurationInvalid("RepositorySync host assembly requires its owning Subsystem")
    else for {
      selections <- RepositorySyncHostSelectionReadPolicy.bindC(params.configuration, declaration)
      service <- bindC(params, declaration, selections, acknowledgement, nativeProgression,
        nativeAcknowledgement, conflictPolicy, validationPolicy, nativeReview, convergence, humanConflict)
    } yield service

  def bindC(params: ComponentCreate, declaration: Fetch.Declaration,
    selections: ProfileStartAdmission.CallScopedSelectionPort,
    acknowledgement: () => Consequence[Unit] = () => Consequence.unit,
    nativeProgression: Boolean = false,
    nativeAcknowledgement: String => Consequence[Unit] = _ => Consequence.unit,
    conflictPolicy: RepositorySyncNativeConflictInput.Policy = RepositorySyncNativeConflictInput.noSemanticPaths,
    validationPolicy: Option[RepositorySyncNativeMergeValidation.Policy] = None,
    nativeReview: Option[RepositorySyncNativeMergeReview.Installation] = None,
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort] = None,
    humanConflict: Option[RepositorySyncHumanConflict.Installation] = None): Consequence[spec.ServiceDefinition] = for {
    _ <- Fetch.validateDeclarationC(declaration)
    _ <- if (params != null && selections != null && acknowledgement != null && nativeAcknowledgement != null && conflictPolicy != null) Consequence.unit
      else Consequence.configurationInvalid("RepositorySync managed host dependencies are incomplete")
    definition <- RepositorySyncDefinitionBinding.bindC
    _ <- conflictPolicy.validateC
    _ <- if (humanConflict != null && (humanConflict.isEmpty || (nativeProgression && conflictPolicy.semanticPaths.nonEmpty)))
      humanConflict.fold(Consequence.unit)(_.validateC)
      else Consequence.configurationInvalid("Human conflict needs paired native conflict progression and independent verifier")
    _ <- if (validationPolicy != null && (validationPolicy.isEmpty || nativeProgression)) Consequence.unit
      else Consequence.configurationInvalid("Merge validation requires its owning native progression")
    _ <- validationPolicy.fold(Consequence.unit)(_.validateC)
    _ <- if (nativeProgression || conflictPolicy.semanticPaths.isEmpty) Consequence.unit
      else Consequence.configurationInvalid("Conflict work cannot be installed without its owning native progression")
    _ <- if (nativeReview != null && (nativeReview.isEmpty || (nativeProgression && validationPolicy.nonEmpty))) Consequence.unit
      else Consequence.configurationInvalid("Native review requires its owning progression and selected validation policy")
    _ <- nativeReview.fold(Consequence.unit)(_.validateC)
    _ <- if (convergence != null && (convergence.isEmpty ||
        (nativeProgression && validationPolicy.nonEmpty && convergence.get != null))) Consequence.unit
      else Consequence.configurationInvalid("Native convergence requires native progression, validation and current host authority; merge convergence additionally requires paired review")
    review <- nativeReview match {
      case Some(installation) => RepositorySyncNativeMergeReviewContinuation.bindC(declaration, definition, installation, nativeAcknowledgement).map(Some(_))
      case None => Consequence.success(None)
    }
    reviewCompletion <- nativeReview match {
      case Some(installation) => RepositorySyncNativeMergeReviewContinuation.bindCompletionC(params, declaration, definition, installation).map(Some(_))
      case None => Consequence.success(None)
    }
    conflicts <- if (conflictPolicy.semanticPaths.nonEmpty)
      RepositorySyncNativeConflictContinuation.bindC(declaration, definition, conflictPolicy, nativeAcknowledgement, validationPolicy, review, humanConflict).map(Some(_))
      else Consequence.success(None)
    completion <- if (conflicts.nonEmpty)
      RepositorySyncNativeConflictContinuation.bindCompletionC(params, declaration, definition, conflictPolicy).map(Some(_))
      else Consequence.success(None)
    humanCompletion <- conflicts match {
      case Some(port) => port.bindHumanCompletionC(params)
      case None => Consequence.success(None)
    }
    generated = RepositorySyncWorkflowStateMachineProvidedApi3.workflow.providedOperations
    _ <- if (Vector((Fetch.startOperation.operation, "RepositorySyncStartCommand", "RepositorySyncStartResult"),
        (Fetch.advanceOperation.operation, "RepositorySyncAdvanceCommand", "RepositorySyncAdvanceResult")).forall { (name, input, output) =>
        generated.count(p => p.service == "RepositorySyncService" && p.name == name &&
          p.inputType.contains(input) && p.resultType.contains(output)) == 1
      }) Consequence.unit else Consequence.configurationInvalid("RepositorySync managed pair differs from the actual CML ABI")
  } yield NativeWorkflowDescriptionMetadata.service(SmWorkflowComponent.RepositorySyncServiceService,
    spec.OperationDefinitionGroup(NonEmptyVector.of(
    new Operation(true, declaration, definition, selections, acknowledgement, nativeProgression, nativeAcknowledgement, conflicts, validationPolicy, review, convergence),
    new Operation(false, declaration, definition, selections, acknowledgement, nativeProgression, nativeAcknowledgement, conflicts, validationPolicy, review, convergence))
    .concat(completion.toVector ++ reviewCompletion.toVector ++ humanCompletion.toVector)))

  private final class Operation(start: Boolean, declaration: Fetch.Declaration, definition: DefinitionBinding,
    selections: ProfileStartAdmission.CallScopedSelectionPort, acknowledgement: () => Consequence[Unit],
    nativeProgression: Boolean, nativeAcknowledgement: String => Consequence[Unit],
    conflicts: Option[RepositorySyncNativeConflictContinuation.Port],
    validationPolicy: Option[RepositorySyncNativeMergeValidation.Policy],
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port],
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort]) extends spec.OperationDefinition {
    private val name = if (start) Fetch.startOperation.operation else Fetch.advanceOperation.operation
    val specification = NativeWorkflowDescriptionMetadata.operation(
      if (start) SmWorkflowComponent.RepositorySyncServiceService.StartRepositorySyncOperation
      else SmWorkflowComponent.RepositorySyncServiceService.AdvanceRepositorySyncOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != name || !request.service.contains("RepositorySyncService"))
        Consequence.stateConflict("RepositorySync managed request differs from its exact Operation")
      else WorkflowJsonCommandRequest.commandArguments(request) match {
        case List(Argument("arg1", raw: String, _)) =>
          if (start) RepositorySyncStartServiceOperation.decodeCommandC(raw).map(command =>
            Action(request, Some(command), None, declaration, definition, selections, acknowledgement, nativeProgression, nativeAcknowledgement, conflicts, validationPolicy, review, convergence))
          else decodeAdvanceC(raw).map(identity =>
            Action(request, None, Some(identity), declaration, definition, selections, acknowledgement, nativeProgression, nativeAcknowledgement, conflicts, validationPolicy, review, convergence))
        case _ => Consequence.stateConflict("RepositorySync managed entry requires one complete JSON command")
      }
  }
  private final case class Action(request: Request, start: Option[RepositorySyncStartCommand], identity: Option[String],
    declaration: Fetch.Declaration, definition: DefinitionBinding, selections: ProfileStartAdmission.CallScopedSelectionPort,
    acknowledgement: () => Consequence[Unit], nativeProgression: Boolean,
    nativeAcknowledgement: String => Consequence[Unit],
    conflicts: Option[RepositorySyncNativeConflictContinuation.Port],
    validationPolicy: Option[RepositorySyncNativeMergeValidation.Policy],
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port],
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = start.fold(
        if (nativeProgression) RepositorySyncNativeProgression.advanceC(identity.get, declaration, definition,
          core, acknowledgement, nativeAcknowledgement, conflicts, validationPolicy, review, convergence)
        else Fetch.advanceC(identity.get, declaration, definition, core, acknowledgement))(
        command => Fetch.startC(command, declaration, definition, selections, core)).map(OperationResponse.Scalar(_))
    }
  }
}
