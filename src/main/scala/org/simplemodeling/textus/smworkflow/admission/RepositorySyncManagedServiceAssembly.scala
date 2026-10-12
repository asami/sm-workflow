package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.DefinitionBinding
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import RepositorySyncManagedFetch as Fetch

/** Current trusted-host entry/owning-advance pair with session-resolved inputs.
  * Captures declarations/selection reader only, never a provider or call lease.
  * @since   Oct.  6, 2026
  * @version Oct. 12, 2026
  */
private[smworkflow] object RepositorySyncManagedServiceAssembly {
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
    else bindC(params, declaration, acknowledgement, nativeProgression,
      nativeAcknowledgement, conflictPolicy, validationPolicy, nativeReview, convergence, humanConflict)

  def bindC(params: ComponentCreate, declaration: Fetch.Declaration,
    acknowledgement: () => Consequence[Unit] = () => Consequence.unit,
    nativeProgression: Boolean = false,
    nativeAcknowledgement: String => Consequence[Unit] = _ => Consequence.unit,
    conflictPolicy: RepositorySyncNativeConflictInput.Policy = RepositorySyncNativeConflictInput.noSemanticPaths,
    validationPolicy: Option[RepositorySyncNativeMergeValidation.Policy] = None,
    nativeReview: Option[RepositorySyncNativeMergeReview.Installation] = None,
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort] = None,
    humanConflict: Option[RepositorySyncHumanConflict.Installation] = None): Consequence[spec.ServiceDefinition] = for {
    _ <- Fetch.validateDeclarationC(declaration)
    _ <- if (params != null && acknowledgement != null && nativeAcknowledgement != null && conflictPolicy != null) Consequence.unit
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
  } yield NativeWorkflowDescriptionMetadata.service(SmWorkflowComponent.RepositorySyncServiceService,
    spec.OperationDefinitionGroup(NonEmptyVector.of(
    new Operation(true, declaration, definition, acknowledgement, nativeProgression, nativeAcknowledgement, conflicts, validationPolicy, review, convergence),
    new Operation(false, declaration, definition, acknowledgement, nativeProgression, nativeAcknowledgement, conflicts, validationPolicy, review, convergence))
    .concat(completion.toVector ++ reviewCompletion.toVector ++ humanCompletion.toVector)))

  private final class Operation(start: Boolean, declaration: Fetch.Declaration, definition: DefinitionBinding,
    acknowledgement: () => Consequence[Unit],
    nativeProgression: Boolean, nativeAcknowledgement: String => Consequence[Unit],
    conflicts: Option[RepositorySyncNativeConflictContinuation.Port],
    validationPolicy: Option[RepositorySyncNativeMergeValidation.Policy],
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port],
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort]) extends NativeWorkflowStartInput.Receiver
      with NativeWorkflowAdvanceInput.Receiver {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      if (start) SmWorkflowComponent.RepositorySyncServiceService.StartRepositorySyncOperation
      else SmWorkflowComponent.RepositorySyncServiceService.AdvanceRepositorySyncOperation)
    def createStartOperationRequestC(request: Request, input: NativeWorkflowStartInput.BusinessInput,
      selection: ProfileStartAdmission.Selection): Consequence[OperationRequest] = input match {
      case value: NativeWorkflowStartInput.Repository =>
        Consequence.success(Action(request, Some(value -> selection), None,
          declaration, definition, acknowledgement, nativeProgression,
          nativeAcknowledgement, conflicts, validationPolicy, review, convergence))
      case _ => Consequence.stateConflict("RepositorySync Start requires its own business input")
    }
    def createAdvanceOperationRequestC(request: Request,
      input: NativeWorkflowAdvanceInput.Resolved): Consequence[OperationRequest] = input match {
      case NativeWorkflowAdvanceInput.Repository(identity) => Consequence.success(Action(request, None, Some(identity),
        declaration, definition, acknowledgement, nativeProgression,
        nativeAcknowledgement, conflicts, validationPolicy, review, convergence))
      case _ => Consequence.stateConflict("RepositorySync advance requires its own selected target")
    }
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      Consequence.stateConflict("RepositorySync requires its owning session-resolved Operation input")
  }
  private final case class Action(request: Request, start: Option[(NativeWorkflowStartInput.Repository, ProfileStartAdmission.Selection)], identity: Option[String],
    declaration: Fetch.Declaration, definition: DefinitionBinding,
    acknowledgement: () => Consequence[Unit], nativeProgression: Boolean,
    nativeAcknowledgement: String => Consequence[Unit],
    conflicts: Option[RepositorySyncNativeConflictContinuation.Port],
    validationPolicy: Option[RepositorySyncNativeMergeValidation.Policy],
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port],
    convergence: Option[RepositorySyncNativeConvergence.AuthorityPort]) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = start match {
        case Some((input, selected)) => Fetch.startC(input, selected, declaration, definition, core)
          .map(NativeWorkflowOperationOutput.response(_))
        case None =>
          val result = if (nativeProgression) RepositorySyncNativeProgression.advanceC(identity.get, declaration, definition,
            core, acknowledgement, nativeAcknowledgement, conflicts, validationPolicy, review, convergence)
          else Fetch.advanceC(identity.get, declaration, definition, core, acknowledgement)
          result.map(NativeWorkflowOperationOutput.response(_))
      }
    }
  }
}
