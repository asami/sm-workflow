package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1
import io.circe.Json
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{RepairStepResult, StepCloseRequest}

/** Typed ordinary Operation completion, not a CAM judgment. Its completion
  * clears only the matching repair suspension and then issues a new review.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
object GoalPhaseRepairCompletionOperation {
  private[smworkflow] final case class ReReviewPorts(
    review: StateMachineRequiredOperation, requirement: ExecutionRequirement,
    presentation: MinimalPresentation, snapshot: GoalPhaseStepClosureBoundary.SnapshotPort
  ) {
    def validateC: Consequence[Unit] =
      if (review == null || review.identity == null || review.operation == null || review.metadata == null ||
          review.metadata.completionContract == null || review.metadata.evidenceContract == null ||
          requirement == null || presentation == null || snapshot == null)
        Consequence.configurationInvalid("Managed re-review follow-up ports are incomplete")
      else for {
        _ <- requirement.validateC
        _ <- presentation.validateC
        _ <- if (requirement.reviewRequired &&
            requirement.capabilities.contains(CapabilityRequirement("review-step-capability"))) Consequence.unit
          else Consequence.configurationInvalid("Managed re-review requires full review capability")
      } yield ()
  }
  val payloadCodec: WorkflowResultJsonV1.PayloadCodec[RepairStepResult] =
    new WorkflowResultJsonV1.PayloadCodec[RepairStepResult] {
      val typeIdentity = "RepairStepResult"
      def encode(value: RepairStepResult): Json = Json.obj(
        "outcome" -> Json.fromString(value.outcome), "summary" -> Json.fromString(value.summary),
        "evidenceIdentity" -> Json.fromString(value.evidenceIdentity))
      def decode(value: Json): Either[String, RepairStepResult] = for {
        fields <- value.asObject.toRight("repair result must be an object")
        _ <- Either.cond(fields.keys.toSet == Set("outcome", "summary", "evidenceIdentity"), (),
          "repair result fields must match the generated type")
        outcome <- fields("outcome").flatMap(_.asString).toRight("outcome must be a string")
        summary <- fields("summary").flatMap(_.asString).toRight("summary must be a string")
        evidence <- fields("evidenceIdentity").flatMap(_.asString).filter(_.trim.nonEmpty)
          .toRight("evidenceIdentity must be nonblank")
      } yield RepairStepResult(outcome, summary, evidence)
    }

  def bindC(loop: GoalPhaseRepairLoop, runtime: ContinuationRuntime,
    issued: IssuedWorkOrderPersistence[StepCloseRequest], instances: WorkflowInstancePersistence,
    configuration: Configuration, freshUnitOfWork: () => UnitOfWork): Consequence[spec.OperationDefinition] =
    if (loop == null) Consequence.configurationInvalid("GoalPhase repair loop is unavailable")
    else _bindC((submitted, _) => loop.admitRepairC(submitted).map(_ => ()),
      loop.afterRepairC, runtime, issued, instances, configuration, freshUnitOfWork, None)

  /** Managed completion stops at retained repair facts. Re-review is recoverable
    * successor work, not a mandatory post-commit callback.
    */
  private[smworkflow] def bindManagedC(publication: GoalPhaseManagedRepairPublication,
    issued: IssuedWorkOrderPersistence[StepCloseRequest], instances: WorkflowInstancePersistence,
    configuration: Configuration, managed: WorkflowManagedCompletion[RepairStepResult],
    reReview: Option[ReReviewPorts] = None
  )(using ExecutionContext): Consequence[spec.OperationDefinition] =
    if (publication == null || managed == null || reReview == null ||
        reReview.exists(value => value == null || value.validateC.toOption.isEmpty))
      Consequence.configurationInvalid("GoalPhase managed repair completion is incomplete")
    else {
      // The declaration is decoder-only. Its callbacks fail closed until the
      // wrapper rebuilds them with the current admitted Operation capsule.
      def boundC(core: ActionCall.Core): Consequence[spec.OperationDefinition] =
        _bindC((submitted, record) =>
          if (core == null || core.executionContext == null)
            Consequence.stateConflict("Managed repair admission has no current Operation call")
          else publication.admitRepairC(submitted, record)(using core.executionContext),
          (submitted, record) =>
          if (core == null || core.executionContext == null)
            Consequence.stateConflict("Managed re-review follow-up has no current Operation call")
          else {
          given ExecutionContext = core.executionContext
          reReview match {
      case Some(ports) => publication.advanceReReviewC(submitted.continuationId,
        ports.review, ports.requirement, ports.presentation, GoalPhaseCallScopedSnapshot.forCall(ports.snapshot, core))
        .flatMap(work => WorkflowWorkOrderJsonV1.encodeC(work, GoalPhaseStepCloseJson.payloadCodec))
        .map(OperationResponse.Scalar(_))
      case None =>
      WorkflowWaitJsonV1.encodeC(WorkflowInteraction(submitted.handle, WorkflowContinuation.Wait(
        StateMachineRevision(record.revision.value.toString), submitted.contextSnapshot,
        MinimalPresentation("Repair completed", "Awaiting fresh full-Step review"))))
        .map(OperationResponse.Scalar(_))
          }
          }, ContinuationRuntime.empty, issued, instances, configuration,
      () => throw new IllegalStateException("Managed repair must not execute a legacy UnitOfWork"), Some(managed))
      boundC(null).map(operation => CallScopedOperationBinding.wrap(operation, boundC))
    }

  private def _bindC(
    admit: (ContinuationResult[RepairStepResult], InstanceRecord) => Consequence[Unit],
    after: (ContinuationResult[RepairStepResult], InstanceRecord) => Consequence[OperationResponse],
    runtime: ContinuationRuntime, issued: IssuedWorkOrderPersistence[StepCloseRequest],
    instances: WorkflowInstancePersistence, configuration: Configuration,
    freshUnitOfWork: () => UnitOfWork, managed: Option[WorkflowManagedCompletion[RepairStepResult]]
  ): Consequence[spec.OperationDefinition] = {
    val generated = GoalPhaseWorkflowStateMachineProvidedApi1.workflow
    val definition = GeneratedProvidedApiAbi.Definition(generated.schemaVersion, generated.generator,
      generated.identity, generated.version,
      GeneratedProvidedApiAbi.WorkflowSourceCorrelation(
        GeneratedProvidedApiAbi.SourceIdentity(generated.source.root.line),
        GeneratedProvidedApiAbi.SourceIdentity(generated.source.definition.line)),
      generated.providedOperations.map(op => GeneratedProvidedApiAbi.Operation(op.service, op.name,
        op.inputType, op.resultType, GeneratedProvidedApiAbi.SourceIdentity(op.source.line))))
    val operations = definition.providedOperations.filter(op =>
      op.service == "GoalPhaseService" && op.name == "submitStepRepair")
    if (admit == null || after == null || runtime == null || issued == null || instances == null ||
        configuration == null || freshUnitOfWork == null || operations.size != 1 || operations.head.inputType != Some("RepairStepResult") ||
        operations.head.resultType != Some("StepRepairSubmissionResult"))
      Consequence.configurationInvalid("GoalPhase repair completion is not declared by the generated Provided API")
    else {
      val codec = new WorkflowCompletionServiceOperation.Codec[RepairStepResult] {
        val resultTypeIdentity = payloadCodec.typeIdentity
        def decodeC(request: Request): Consequence[ContinuationResult[RepairStepResult]] =
          WorkflowJsonCommandRequest.commandArguments(request) match {
            case List(Argument("arg1", wire: String, _)) => WorkflowResultJsonV1.decodeC(wire, payloadCodec)
            case _ => Consequence.stateConflict("GoalPhase repair Result JSON is missing")
          }
        def encodeC(resume: ContinuationRuntime.Resume): Consequence[OperationResponse] =
          Consequence.stateConflict("GoalPhase repair completion requires an appended instance")
        override def encodeAfterCommitC(resume: ContinuationRuntime.Resume,
          submitted: ContinuationResult[RepairStepResult], completed: Option[InstanceRecord]):
          Consequence[OperationResponse] = completed match {
            case Some(record) if record.lifecycle == Lifecycle.Active && record.suspension.isEmpty =>
              after(submitted, record)
            case _ => Consequence.stateConflict("GoalPhase repair did not append an Active instance")
          }
      }
      val closing = new WorkflowCompletionServiceOperation.ClosingProgram[RepairStepResult] {
        def selectC(submitted: ContinuationResult[RepairStepResult], current: InstanceRecord):
          Consequence[WorkflowCompletionServiceOperation.SelectedClosing] = for {
          _ <- admit(submitted, current)
          _ <- if (current.lifecycle == Lifecycle.Active && current.suspension.exists(
              _.continuationIdentity.value == submitted.continuationId.value) &&
              current.currentProgression.contains(ProgressionReference("step.Awaiting"))) Consequence.unit
            else Consequence.stateConflict("GoalPhase repair is not the current suspension")
          next <- current.appendC(current.revision, HistoryEntry(
            HistorySequence(current.history.size.toLong + 1L), current.revision.next, Lifecycle.Active,
            current.currentProgression, current.correlation,
            CausationReference(s"repair-result:${submitted.resultReference.identity}@${submitted.resultReference.revision}"),
            current.derivedCompositeOccurrence, current.committedPredecessor, None))
          program = ConsequenceT.pure[
            [X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution
          ](ActionExecution.Completed(StateMachineOperationResult(
            StateMachineResultTypeReference("RepairStepResult"), submitted.resultReference)))
        } yield WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next))
      }
      WorkflowCompletionServiceOperation.bindC[StepCloseRequest, RepairStepResult](definition,
        operations.head, WorkflowJsonCommandRequest.definition, spec.ResponseDefinition.void,
        codec, runtime, issued, instances, configuration, freshUnitOfWork, Some(closing), managed,
        descriptiveAttributes = SmWorkflowComponent.GoalPhaseServiceService.SubmitStepRepairOperation.specification.content.descriptiveAttributes)
    }
  }
}
