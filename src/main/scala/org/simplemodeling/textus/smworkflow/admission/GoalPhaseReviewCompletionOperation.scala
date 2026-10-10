package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant

import domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1
import io.circe.{Json, JsonObject}
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
import org.simplemodeling.textus.smworkflow.value.{ReviewStepResult, StepCloseRequest, StepReviewEvidence}

/** Application binding for the generated submitStepReview Provided Operation.
  * A fixture may connect accepted review completion to the deterministic
  * Step-close boundary without a second public close request.
  * @since   Sep. 26, 2026
  * @version Oct.  8, 2026
  */
object GoalPhaseReviewCompletionOperation {
  final case class AutoClosePorts(
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
    commit: GoalPhaseStepClosureBoundary.CommitPort,
    reviewedAuthority: Option[GoalPhaseReviewedClosure.Ports] = None
  ) {
    def validateC: Consequence[Unit] =
      if (snapshot == null || commit == null || reviewedAuthority == null ||
          reviewedAuthority.exists(value => value == null || value.validateC.toOption.isEmpty))
        Consequence.configurationInvalid("GoalPhase automatic Step closure ports are incomplete")
      else Consequence.unit
  }

  val payloadCodec: WorkflowResultJsonV1.PayloadCodec[ReviewStepResult] =
    new WorkflowResultJsonV1.PayloadCodec[ReviewStepResult] {
      val typeIdentity = "ReviewStepResult"

      def encode(value: ReviewStepResult): Json = Json.obj(
        "decision" -> Json.fromString(value.decision),
        "rationale" -> Json.fromString(value.rationale),
        "reviewEvidence" -> Json.arr(value.reviewEvidence.map(GoalPhaseStepCloseJson.encodeEvidence)*)
      )

      def decode(value: Json): Either[String, ReviewStepResult] = for {
        fields <- _fields(value, Set("decision", "rationale", "reviewEvidence"))
        decision <- _string(fields, "decision")
        rationale <- _string(fields, "rationale")
        items <- fields("reviewEvidence").flatMap(_.asArray)
          .toRight("reviewEvidence must be an array")
        evidence <- items.foldLeft[Either[String, Vector[StepReviewEvidence]]](Right(Vector.empty)) {
          (prior, item) => for {
            accumulated <- prior
            decoded <- GoalPhaseStepCloseJson.decodeEvidence(item)
          } yield accumulated :+ decoded
        }
      } yield ReviewStepResult(decision, rationale, evidence)
    }

  def bindC(
    artifact: CandidateAdmissionProducerAbi.Artifact,
    asOf: () => Instant,
    runtime: ContinuationRuntime,
    issuedPersistence: IssuedWorkOrderPersistence[StepCloseRequest],
    instancePersistence: WorkflowInstancePersistence,
    configuration: Configuration,
    freshUnitOfWork: () => UnitOfWork
  ): Consequence[spec.OperationDefinition] =
    _bindC(artifact, asOf, runtime, issuedPersistence, instancePersistence,
      configuration, freshUnitOfWork, None, None)

  /** Host-owned shared-storage path. The opaque framework dependency owns
    * claim admission and joint completion; no legacy resume runtime is used.
    * The application retains its typed judgment and deterministic follow-up.
    */
  def bindManagedC(
    artifact: CandidateAdmissionProducerAbi.Artifact,
    asOf: () => Instant,
    issuedPersistence: IssuedWorkOrderPersistence[StepCloseRequest],
    instancePersistence: WorkflowInstancePersistence,
    configuration: Configuration,
    freshUnitOfWork: () => UnitOfWork,
    managedCompletion: WorkflowManagedCompletion[ReviewStepResult],
    autoClose: Option[AutoClosePorts] = None,
    repairLoop: Option[GoalPhaseRepairLoop] = None
  ): Consequence[spec.OperationDefinition] =
    if (managedCompletion == null || autoClose == null ||
        autoClose.exists(value => value == null || value.validateC.toOption.isEmpty) ||
        repairLoop == null || repairLoop.exists(_ == null) ||
        (repairLoop.nonEmpty && autoClose.isEmpty))
      Consequence.configurationInvalid("GoalPhase managed review completion ports are incomplete")
    else _bindC(artifact, asOf, ContinuationRuntime.empty, issuedPersistence,
      instancePersistence, configuration, freshUnitOfWork, autoClose, repairLoop,
      Some(managedCompletion))

  private[smworkflow] def bindManagedRepairC(
    artifact: CandidateAdmissionProducerAbi.Artifact, asOf: () => Instant,
    issued: IssuedWorkOrderPersistence[StepCloseRequest], instances: WorkflowInstancePersistence,
    configuration: Configuration, freshUnitOfWork: () => UnitOfWork,
    managed: WorkflowManagedCompletion[ReviewStepResult], publication: GoalPhaseManagedRepairPublication,
    authorityProjection: Option[GoalPhaseManagedReviewAssembly.AuthorityProjection] = None
  )(using ExecutionContext): Consequence[spec.OperationDefinition] =
    if (managed == null || publication == null || authorityProjection == null ||
        authorityProjection.exists(value => value == null || value.snapshot == null || value.authority == null))
      Consequence.configurationInvalid("GoalPhase managed review follow-up is incomplete")
    else {
      val followup: (ContinuationResult[ReviewStepResult], InstanceRecord, ActionCall.Core) => Consequence[OperationResponse] =
        (submitted, record, core) => {
        given ExecutionContext = core.executionContext
        if (record.history.count(_.causation.value.startsWith("repair-result:")) < publication.maxRepairs)
          publication.advanceC(submitted.continuationId)
            .flatMap(work => WorkflowWorkOrderJsonV1.encodeC(work, GoalPhaseStepCloseJson.payloadCodec))
            .map(OperationResponse.Scalar(_))
        else WorkflowWaitJsonV1.encodeC(WorkflowInteraction(submitted.handle, WorkflowContinuation.Wait(
          StateMachineRevision(record.revision.value.toString), submitted.contextSnapshot,
          MinimalPresentation("Repair limit reached", "Awaiting explicit repair direction"))))
          .map(OperationResponse.Scalar(_))
        }
      authorityProjection match {
        case Some(value) => bindManagedAuthorityProjectionC(artifact, asOf, issued, instances,
          configuration, managed, value.snapshot, value.authority, Some(followup), value.preparation, value.nativeQuestion)
        case None => _bindC(artifact, asOf, ContinuationRuntime.empty, issued, instances, configuration,
          freshUnitOfWork, None, None, Some(managed), Some(followup))
      }
    }

  def bindAutoCloseC(
    artifact: CandidateAdmissionProducerAbi.Artifact,
    asOf: () => Instant,
    runtime: ContinuationRuntime,
    issuedPersistence: IssuedWorkOrderPersistence[StepCloseRequest],
    instancePersistence: WorkflowInstancePersistence,
    configuration: Configuration,
    freshUnitOfWork: () => UnitOfWork,
    ports: AutoClosePorts
  ): Consequence[spec.OperationDefinition] =
    if (ports == null || ports.validateC.toOption.isEmpty)
      Consequence.configurationInvalid("GoalPhase automatic Step closure ports are incomplete")
    else _bindC(artifact, asOf, runtime, issuedPersistence, instancePersistence,
      configuration, freshUnitOfWork, Some(ports), None)

  def bindRepairLoopC(
    artifact: CandidateAdmissionProducerAbi.Artifact,
    asOf: () => Instant,
    runtime: ContinuationRuntime,
    issuedPersistence: IssuedWorkOrderPersistence[StepCloseRequest],
    instancePersistence: WorkflowInstancePersistence,
    configuration: Configuration,
    freshUnitOfWork: () => UnitOfWork,
    ports: AutoClosePorts,
    loop: GoalPhaseRepairLoop
  ): Consequence[spec.OperationDefinition] =
    if (ports == null || ports.validateC.toOption.isEmpty || loop == null)
      Consequence.configurationInvalid("GoalPhase repair loop closure ports are incomplete")
    else _bindC(artifact, asOf, runtime, issuedPersistence, instancePersistence,
      configuration, freshUnitOfWork, Some(ports), Some(loop))

  private def _bindC(
    artifact: CandidateAdmissionProducerAbi.Artifact,
    asOf: () => Instant,
    runtime: ContinuationRuntime,
    issuedPersistence: IssuedWorkOrderPersistence[StepCloseRequest],
    instancePersistence: WorkflowInstancePersistence,
    configuration: Configuration,
    freshUnitOfWork: () => UnitOfWork,
    autoClose: Option[AutoClosePorts],
    repairLoop: Option[GoalPhaseRepairLoop],
    managedCompletion: Option[WorkflowManagedCompletion[ReviewStepResult]] = None,
    managedRepair: Option[(ContinuationResult[ReviewStepResult], InstanceRecord, ActionCall.Core) => Consequence[OperationResponse]] = None,
    currentCall: Option[ActionCall.Core] = None,
    acceptedProjection: Option[(ContinuationResult[ReviewStepResult], InstanceRecord, ActionCall.Core) =>
      Consequence[OperationResponse]] = None
  ): Consequence[spec.OperationDefinition] = {
    val generated = GoalPhaseWorkflowStateMachineProvidedApi1.workflow
    val completion = generated.providedOperations.filter(operation =>
      operation.service == "GoalPhaseService" && operation.name == "submitStepReview")
    if (artifact == null || asOf == null || runtime == null || issuedPersistence == null ||
        instancePersistence == null || configuration == null || freshUnitOfWork == null ||
        completion.size != 1 || completion.head.inputType != Some("ReviewStepResult") ||
        completion.head.resultType != Some("StepReviewSubmissionResult"))
      Consequence.configurationInvalid("GoalPhase review completion binding is incomplete")
    else CandidateAdmissionProducerAbi.admitC(artifact).flatMap { admitted =>
      val definition = GeneratedProvidedApiAbi.Definition(
        generated.schemaVersion, generated.generator, generated.identity, generated.version,
        GeneratedProvidedApiAbi.WorkflowSourceCorrelation(
          GeneratedProvidedApiAbi.SourceIdentity(generated.source.root.line),
          GeneratedProvidedApiAbi.SourceIdentity(generated.source.definition.line)
        ),
        generated.providedOperations.map { operation =>
          GeneratedProvidedApiAbi.Operation(
            operation.service, operation.name, operation.inputType, operation.resultType,
            GeneratedProvidedApiAbi.SourceIdentity(operation.source.line)
          )
        }
      )
      val completionOperation = definition.providedOperations.find(operation =>
        operation.service == "GoalPhaseService" && operation.name == "submitStepReview").get
      val codec = new WorkflowCompletionServiceOperation.Codec[ReviewStepResult] {
        val resultTypeIdentity = payloadCodec.typeIdentity

        def decodeC(request: Request): Consequence[ContinuationResult[ReviewStepResult]] =
          WorkflowJsonCommandRequest.commandArguments(request) match {
            case List(Argument("arg1", wire: String, _)) =>
              WorkflowResultJsonV1.decodeC(wire, payloadCodec)
            case _ => Consequence.stateConflict("GoalPhase review Result JSON is missing")
          }

        def encodeC(resume: ContinuationRuntime.Resume): Consequence[OperationResponse] =
          Consequence.stateConflict("GoalPhase review completion requires an appended WorkflowInstance")

        override def encodeAfterCommitC(
          resume: ContinuationRuntime.Resume,
          submitted: ContinuationResult[ReviewStepResult],
          completed: Option[InstanceRecord]
        ): Consequence[OperationResponse] = completed match {
          case Some(record) if record.lifecycle == Lifecycle.Active && record.suspension.isEmpty =>
            if (submitted.result.value.decision == "accept" && acceptedProjection.nonEmpty)
              currentCall match {
                case Some(core) => acceptedProjection.get(submitted, record, core)
                case None => Consequence.stateConflict("Managed accepted projection has no current call")
              }
            else if (submitted.result.value.decision == "repair" && managedRepair.nonEmpty)
              currentCall match {
                case Some(core) => managedRepair.get(submitted, record, core)
                case None => Consequence.stateConflict("Managed repair follow-up has no current call")
              }
            else if (submitted.result.value.decision == "repair" &&
                repairLoop.exists(_.canRepair(record)))
              repairLoop.get.afterReviewC(submitted, record)
            else autoClose match {
              case Some(ports) if submitted.result.value.decision == "accept" =>
                for {
                  loaded <- issuedPersistence.loadC(submitted.continuationId)
                  issued <- loaded match {
                    case Some(value) if value != null => Consequence.success(value)
                    case _ => Consequence.stateConflict("GoalPhase automatic Step closure has no issued WorkOrder")
                  }
                  wire <- ports.reviewedAuthority match {
                    case Some(authority) => currentCall match {
                      case Some(core) => GoalPhaseReviewedClosure.afterReviewC(
                        admitted, issued, submitted, record, asOf(), instancePersistence,
                        configuration, GoalPhaseCallScopedSnapshot.forCall(ports.snapshot, core), ports.commit,
                        authority.copy(authority = authority.authority.forCall(core)))
                      case None => Consequence.stateConflict("Reviewed closure has no current Operation call")
                    }
                    case None => for {
                      closed <- GoalPhaseStepClosureBoundary.closeReviewedC(
                        admitted, issued, submitted, asOf(), instancePersistence, configuration,
                        GoalPhaseCallScopedSnapshot.forCall(ports.snapshot, currentCall.orNull), ports.commit)
                      handle <- WorkflowHandle.fromRecordC(issued.handle.componentIdentity, closed.instance)
                      terminal <- projectTerminalC(handle, closed.instance,
                        TypedValue("StepCloseResult", closed.receipt.commitIdentity),
                        MinimalPresentation("Step Closed", "Committed reviewed Step"))
                      wire <- WorkflowTerminalJsonV1.encodeC(
                        terminal, GoalPhaseStepCloseServiceOperation.terminalCodec)
                    } yield wire
                  }
                } yield OperationResponse.Scalar(wire)
              case _ =>
                val situation = if (submitted.result.value.decision == "accept")
                  "Review accepted; awaiting deterministic Step close"
                else "Repair requested; Step remains open"
                WorkflowWaitJsonV1.encodeC(WorkflowInteraction(
                  submitted.handle,
                  WorkflowContinuation.Wait(
                    StateMachineRevision(record.revision.value.toString), submitted.contextSnapshot,
                    MinimalPresentation("Step closure pending", situation)
                  )
                )).map(OperationResponse.Scalar(_))
            }
          case _ => Consequence.stateConflict("GoalPhase review completion did not append an Active instance")
        }
      }
      val closing = new WorkflowCompletionServiceOperation.ClosingProgram[ReviewStepResult] {
        def selectC(
          submitted: ContinuationResult[ReviewStepResult],
          current: InstanceRecord
        ): Consequence[WorkflowCompletionServiceOperation.SelectedClosing] =
          if (submitted == null || submitted.continuationId == null || current == null)
            Consequence.stateConflict("GoalPhase review closing Action input is incomplete")
          else for {
            loaded <- issuedPersistence.loadC(submitted.continuationId)
            issued <- loaded match {
              case Some(value) if value != null => Consequence.success(value)
              case _ => Consequence.stateConflict("GoalPhase review has no issued WorkOrder")
            }
            expected <- WorkflowHandle.fromRecordC(issued.handle.componentIdentity, current)
            _ <- if (expected == issued.handle) Consequence.unit
                 else Consequence.stateConflict("GoalPhase review WorkOrder Handle differs from the instance")
            _ <- GoalPhaseReviewResultAdapter.assessC(admitted, issued, submitted, asOf())
            next <- GoalPhaseReviewResumeBoundary.reviewedRecordC(current, submitted)
            program = ConsequenceT.pure[
              [X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution
            ](ActionExecution.Completed(StateMachineOperationResult(
              StateMachineResultTypeReference("ReviewStepResult"), submitted.resultReference
            )))
          } yield WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next))
      }
      WorkflowCompletionServiceOperation.bindC[StepCloseRequest, ReviewStepResult](
        definition, completionOperation, WorkflowJsonCommandRequest.definition,
        spec.ResponseDefinition.void,
        codec, runtime, issuedPersistence, instancePersistence, configuration,
        freshUnitOfWork, Some(closing), managedCompletion,
        descriptiveAttributes = SmWorkflowComponent.GoalPhaseServiceService.SubmitStepReviewOperation.specification.content.descriptiveAttributes
      ).map { operation =>
        if (currentCall.isEmpty && (autoClose.exists(value => value.reviewedAuthority.nonEmpty ||
            value.snapshot.isInstanceOf[GoalPhaseCallScopedSnapshot.Port]) ||
            acceptedProjection.nonEmpty || managedRepair.nonEmpty))
          CallScopedOperationBinding.wrap(operation, core => _bindC(artifact, asOf, runtime,
            issuedPersistence, instancePersistence, configuration, freshUnitOfWork,
            autoClose, repairLoop, managedCompletion, managedRepair, Some(core), acceptedProjection))
        else operation
      }
    }
  }

  private[smworkflow] def bindManagedAuthorityProjectionC(
    artifact: CandidateAdmissionProducerAbi.Artifact, asOf: () => Instant,
    issued: IssuedWorkOrderPersistence[StepCloseRequest], instances: WorkflowInstancePersistence,
    configuration: Configuration, managed: WorkflowManagedCompletion[ReviewStepResult],
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort, authority: GoalPhaseClosureAuthority.Ports,
    repairFollowup: Option[(ContinuationResult[ReviewStepResult], InstanceRecord, ActionCall.Core) => Consequence[OperationResponse]] = None,
    preparation: Option[GoalPhaseReviewDecisionPreparation.Configuration] = None,
    nativeQuestion: Option[GoalPhaseManagedStartAssembly.Declaration] = None
  ): Consequence[spec.OperationDefinition] =
    if (managed == null || snapshot == null || authority == null || authority.validateC.toOption.isEmpty ||
        repairFollowup == null || repairFollowup.exists(_ == null) || preparation == null ||
        preparation.exists(value => value == null || value.validateC.toOption.isEmpty || value.declaration.configuration != configuration) ||
        nativeQuestion == null || nativeQuestion.exists(value => value == null || value.configuration != configuration) ||
        (preparation.nonEmpty && nativeQuestion.nonEmpty))
      Consequence.configurationInvalid("Managed authority projection ports are incomplete")
    else _bindC(artifact, asOf, ContinuationRuntime.empty, issued, instances, configuration,
      () => throw new IllegalStateException("Managed projection cannot use legacy UnitOfWork"),
      None, None, Some(managed), repairFollowup, acceptedProjection = Some((submitted, current, core) => {
        projectAcceptedForCallC(artifact, asOf, issued, instances, configuration,
          managed, snapshot, authority, submitted, current, core, preparation, nativeQuestion)
      }))

  /** Deterministic retained-review follow-up, never semantic result replay.
    * Stop-only projection is read-only; native mode may publish one managed
    * adjacent question after fresh authority assessment.
    */
  private[smworkflow] def projectAcceptedForCallC(
    artifact: CandidateAdmissionProducerAbi.Artifact, asOf: () => Instant,
    issued: IssuedWorkOrderPersistence[StepCloseRequest], instances: WorkflowInstancePersistence,
    configuration: Configuration, managed: WorkflowManagedCompletion[ReviewStepResult],
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort, authority: GoalPhaseClosureAuthority.Ports,
    submitted: ContinuationResult[ReviewStepResult], current: InstanceRecord, core: ActionCall.Core,
    preparation: Option[GoalPhaseReviewDecisionPreparation.Configuration] = None,
    nativeQuestion: Option[GoalPhaseManagedStartAssembly.Declaration] = None
  ): Consequence[OperationResponse] =
    if (core == null || core.executionContext == null || submitted == null ||
        submitted.result == null || submitted.result.value == null ||
        submitted.continuationId == null || current == null ||
        current.lifecycle != Lifecycle.Active || current.suspension.nonEmpty || preparation == null ||
        preparation.exists(value => value == null || value.validateC.toOption.isEmpty || value.declaration.configuration != configuration) ||
        nativeQuestion == null || nativeQuestion.exists(value => value == null || value.configuration != configuration) ||
        (preparation.nonEmpty && nativeQuestion.nonEmpty))
      Consequence.stateConflict("Managed accepted projection requires retained active accepted review")
    else {
      given ExecutionContext = core.executionContext
      for {
          retained <- managed.loadRetainedC(submitted.continuationId).flatMap {
            case Some(value) if value.completion.instanceRevision == current.revision &&
                value.completion.result.result.value.decision == "accept" => Consequence.success(value)
            case _ => Consequence.stateConflict("Managed accepted projection lacks a current committed accepted review")
          }
          actual = retained.completion.result
          response <- projectSelectedForCallC(artifact, asOf, issued, instances, configuration,
            snapshot, authority, actual, current, core, preparation, nativeQuestion)
      } yield response
    }

  /** Internal follow-up of the entry's selected canonical completion. It is
    * not another public input admission or a saved-copy equality check.
    */
  private[smworkflow] def projectSelectedForCallC(
    artifact: CandidateAdmissionProducerAbi.Artifact, asOf: () => Instant,
    issued: IssuedWorkOrderPersistence[StepCloseRequest], instances: WorkflowInstancePersistence,
    configuration: Configuration, snapshot: GoalPhaseStepClosureBoundary.SnapshotPort,
    authority: GoalPhaseClosureAuthority.Ports,
    actual: ContinuationResult[ReviewStepResult], current: InstanceRecord, core: ActionCall.Core,
    preparation: Option[GoalPhaseReviewDecisionPreparation.Configuration],
    nativeQuestion: Option[GoalPhaseManagedStartAssembly.Declaration]
  ): Consequence[OperationResponse] = {
      given ExecutionContext = core.executionContext
      for {
          work <- issued.loadC(actual.continuationId).flatMap {
            case Some(value) => Consequence.success(value)
            case _ => Consequence.stateConflict("Managed accepted projection lacks issued review")
          }
          response <- nativeQuestion match {
            case Some(declaration) => for {
              assessment <- GoalPhaseClosureAuthority.assessReviewedC(artifact, work, actual,
                asOf(), current, authority.forCall(core).authorizations)
              question <- GoalPhaseManagedNativeHumanQuestion.publishAssessedForCallC(actual, current,
                assessment, core, declaration)
              response <- GoalPhaseNativeHumanAdvanceOperation.responseC(question)
            } yield response
            case None =>
              val scopedauthority = authority.forCall(core)
              val selectedauthority = preparation.fold(scopedauthority)(value =>
                GoalPhaseClosureAuthority.Ports(scopedauthority.authorizations, new GoalPhaseClosureAuthority.DecisionPort {
                  def resolveC(intent: GoalPhaseClosureAuthority.Intent) =
                    GoalPhaseReviewDecisionPreparation.resolveForCallC(value, intent, core)
                }))
              GoalPhaseClosureAuthority.closeReviewedOrDecisionC(artifact, work, actual,
                asOf(), current, instances, configuration, GoalPhaseCallScopedSnapshot.forCall(snapshot, core),
                null, selectedauthority, false).map(OperationResponse.Scalar(_))
          }
      } yield response
    }

  private def _fields(value: Json, required: Set[String]): Either[String, JsonObject] =
    value.asObject match {
      case Some(fields) if fields.keys.toSet == required => Right(fields)
      case _ => Left("GoalPhase review payload has an incompatible shape")
    }

  private def _string(fields: JsonObject, name: String): Either[String, String] =
    fields(name).flatMap(_.asString).filter(_.trim.nonEmpty)
      .toRight(s"GoalPhase review payload is missing $name")
}
