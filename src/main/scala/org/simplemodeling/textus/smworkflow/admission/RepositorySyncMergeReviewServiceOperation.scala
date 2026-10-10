package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import domain.statemachine.providedapi.RepositorySyncWorkflowStateMachineProvidedApi3
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncMergeReviewRequest, RepositorySyncMergeReviewResult, RepositorySyncMergeValidationAdmissionCommand}
import RepositorySyncMergeReviewAdmission as R
import RepositorySyncMergeReviewBoundary as B

/** Explicit fixture/public binding. Host-retained provenance is mandatory;
  * neither public request nor semantic Result chooses a Git write or transition.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncMergeReviewServiceOperation {
  final case class Retained(command: RepositorySyncMergeValidationAdmissionCommand,
    submission: R.Submission, runId: StateMachineRunIdentity)
  trait RetainedPort {
    def loadC(submissionIdentity: String): Consequence[Option[Retained]]
  }
  final case class Ports(retained: RetainedPort, selections: ProfileStartAdmission.SelectionPort,
    mappings: RepositorySyncStartExecution.TrackingMappingPort, instances: WorkflowInstancePersistence,
    configuration: Configuration, runtime: ContinuationRuntime,
    issued: IssuedWorkOrderPersistence[RepositorySyncMergeReviewRequest], requirement: ExecutionRequirement,
    presentation: MinimalPresentation, asOf: () => Instant, freshUnitOfWork: () => UnitOfWork,
    reviews: RepositorySyncMergeReviewReceiptStore,
    decisions: Option[RepositorySyncMergeDecision.Port] = None)
  private final case class Prepared(seed: Retained, current: InstanceRecord, prefix: InstanceRecord,
    planned: B.Planned, candidate: CandidateWorkflowAbi.Definition)
  val wireSchema = "sm-workflow.repository-sync-merge-review.v1"

  def encodeRequest(value: RepositorySyncMergeReviewRequest): String = Json.obj(
    "schemaVersion" -> Json.fromString(wireSchema), "request" -> B.inputCodec.encode(value)).noSpaces
  def decodeRequestC(wire: String): Consequence[RepositorySyncMergeReviewRequest] = {
    val result = for {
      json <- if (wire == null) Left("RepositorySync review JSON is missing")
        else parse(wire).left.map(_ => "RepositorySync review JSON is invalid")
      fields <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "request"))
        .toRight("RepositorySync review JSON has an incompatible shape")
      _ <- Either.cond(fields("schemaVersion").flatMap(_.asString).contains(wireSchema), (),
        "RepositorySync review JSON schema is unsupported")
      value <- B.inputCodec.decode(fields("request").get)
    } yield value
    result.fold(Consequence.stateConflict(_), Consequence.success)
  }

  def bindRequestC(ports: Ports): Consequence[spec.OperationDefinition] =
    _portsC(ports).map(_ => new RequestOperation(ports))

  def bindCompletionC(ports: Ports): Consequence[spec.OperationDefinition] = _portsC(ports).flatMap { _ =>
    val definition = _provided
    val operation = definition.providedOperations.find(_.name == "submitMergeReview").get
    val codec = new WorkflowCompletionServiceOperation.Codec[RepositorySyncMergeReviewResult] {
      val resultTypeIdentity = RepositorySyncMergeReviewResultAdapter.payloadCodec.typeIdentity
      def decodeC(request: Request): Consequence[ContinuationResult[RepositorySyncMergeReviewResult]] =
        request.arguments match {
          case List(Argument("arg1", wire: String, _)) =>
            WorkflowResultJsonV1.decodeC(wire, RepositorySyncMergeReviewResultAdapter.payloadCodec)
          case _ => Consequence.stateConflict("RepositorySync review Result JSON is missing")
        }
      def encodeC(resume: ContinuationRuntime.Resume): Consequence[OperationResponse] =
        Consequence.stateConflict("RepositorySync review completion requires saved history")
      override def encodeAfterCommitC(resume: ContinuationRuntime.Resume,
        submitted: ContinuationResult[RepositorySyncMergeReviewResult], completed: Option[InstanceRecord]) =
        completed match {
          case Some(record) if record.revision == InstanceRevision(9L) && record.suspension.isEmpty =>
            retainCompletedC(submitted, record, ports).flatMap { _ =>
              if (submitted.result.value.decision == "decision-needed" && ports.decisions.nonEmpty)
                projectDecisionC(submitted.resultReference, ports).map(OperationResponse.Scalar(_))
              else _waitC(record, submitted.contextSnapshot, submitted.result.value.decision match {
              case "accept" => "Full merge review accepted; awaiting deterministic merge commit"
              case "repair" => "Merge repair required; commit remains blocked"
              case "decision-needed" => "Merge decision required; commit remains blocked"
              case _ => "Merge rejected; commit remains blocked"
              })
            }
          case _ => Consequence.stateConflict("RepositorySync review completion did not save history")
        }
    }
    WorkflowCompletionServiceOperation.bindC[RepositorySyncMergeReviewRequest, RepositorySyncMergeReviewResult](
      definition, operation, WorkflowJsonCommandRequest.definition, spec.ResponseDefinition.void,
      codec, ports.runtime, ports.issued, ports.instances, ports.configuration,
      ports.freshUnitOfWork, Some(_closing(ports)),
      descriptiveAttributes = SmWorkflowComponent.RepositorySyncServiceService.SubmitMergeReviewOperation.specification.content.descriptiveAttributes)
  }

  private def _closing(ports: Ports) = new WorkflowCompletionServiceOperation.ClosingProgram[RepositorySyncMergeReviewResult] {
      def selectC(submitted: ContinuationResult[RepositorySyncMergeReviewResult], current: InstanceRecord) =
        for {
          loaded <- ports.issued.loadC(submitted.continuationId)
          issued <- loaded match {
            case Some(value) if value != null => Consequence.success(value)
            case _ => Consequence.stateConflict("RepositorySync review Result has no issued WorkOrder")
          }
          work <- issued.current match {
            case value: WorkflowContinuation.WorkOrder[RepositorySyncMergeReviewRequest] if value.request != null =>
              Consequence.success(value)
            case _ => Consequence.stateConflict("RepositorySync review Result requires a WorkOrder")
          }
          input <- Option(work.request.input).flatten match {
            case Some(value) if value != null && value.value != null => Consequence.success(value.value)
            case _ => Consequence.stateConflict("RepositorySync review WorkOrder input is missing")
          }
          prepared <- _prepareC(input, ports, Some(current), forcePending = true)
          pending <- prepared.planned.pending match {
            case Some(value) => Consequence.success(value)
            case _ => Consequence.stateConflict("RepositorySync review declaration has no semantic work")
          }
          expected <- B.suspendedRecordC(pending, prepared.prefix)
          handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current)
          _ <- if (B._sameRecord(current, expected) && issued.handle == handle &&
              work.request.runId == pending.continuation.runId &&
              work.request.continuationId == pending.continuation.continuationId &&
              work.request.expectedRevision == pending.continuation.expectedRevision &&
              work.request.requiredOperation == pending.continuation.requiredOperation.identity &&
              work.request.operation == pending.continuation.requiredOperation.operation &&
              work.request.resultType == pending.continuation.requiredOperation.resultType &&
              work.request.input.exists(value => value != null && value.typeIdentity == pending.input.typeIdentity &&
                RepositorySyncMergeDecision.sameCandidateScope(value.value, pending.input.value)) &&
              work.request.context != null && GoalPhaseClosureAuthority.sameContextScope(
                work.request.context.snapshot, pending.continuation.context.snapshot) &&
              work.request.completion == pending.continuation.requiredOperation.metadata.completionContract &&
              work.request.evidence == pending.continuation.requiredOperation.metadata.evidenceContract &&
              work.request.completionOperation == StateMachineOperationIdentity("RepositorySyncService", "submitMergeReview") &&
              work.requirement == ports.requirement) Consequence.unit
            else Consequence.stateConflict("RepositorySync review WorkOrder differs from retained provenance")
          _ <- RepositorySyncMergeReviewResultAdapter.assessC(prepared.candidate, prepared.seed.submission,
            issued, submitted, ports.asOf())
          next <- current.appendC(InstanceRevision(8L), HistoryEntry(HistorySequence(9L), InstanceRevision(9L),
            Lifecycle.Active, current.currentProgression, current.correlation,
            CausationReference(s"merge-review-result:${submitted.resultReference.identity}@${submitted.resultReference.revision}"),
            None, None, None))
          program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Completed(StateMachineOperationResult(
              StateMachineResultTypeReference("RepositorySyncMergeReviewResult"), submitted.resultReference)))
        } yield WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next))
    }

  /** Re-admits the completed record without claiming/resuming again. This also
    * provides the host an explicit recovery after a post-resume retention failure.
    */
  def completedReceiptC(submitted: ContinuationResult[RepositorySyncMergeReviewResult],
    current: InstanceRecord, ports: Ports): Consequence[RepositorySyncMergeReviewReceiptStore.Record] =
    if (submitted == null || current == null || current.revision != InstanceRevision(9L))
      Consequence.stateConflict("RepositorySync review retention requires completed revision nine")
    else for {
      _ <- _portsC(ports)
      prefix <- B.prefixC(current, 8L)
      selected <- _closing(ports).selectC(submitted, prefix)
      cause = s"merge-review-result:${submitted.resultReference.identity}@${submitted.resultReference.revision}"
      _ <- if (selected.nextInstance.exists(_sameCurrentState(_, current)) &&
          (current.causation.value == cause || current.causation.value.startsWith(cause + ":"))) Consequence.unit
        else Consequence.stateConflict("RepositorySync completed review history differs")
      issued <- ports.issued.loadC(submitted.continuationId).flatMap {
        case Some(value) if value != null => Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync completed review has no issued WorkOrder")
      }
      input <- issued.current match {
        case work: WorkflowContinuation.WorkOrder[RepositorySyncMergeReviewRequest] => work.request.input match {
          case Some(value) => Consequence.success(value.value)
          case _ => Consequence.stateConflict("RepositorySync completed review has no input")
        }
        case _ => Consequence.stateConflict("RepositorySync completed review has no WorkOrder")
      }
      seed <- ports.retained.loadC(input.submissionIdentity).flatMap {
        case Some(value) if value != null => Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync completed review has no retained candidate")
      }
    } yield RepositorySyncMergeReviewReceiptStore.Record(seed, issued, submitted)

  def retainCompletedC(submitted: ContinuationResult[RepositorySyncMergeReviewResult],
    current: InstanceRecord, ports: Ports): Consequence[RepositorySyncMergeReviewReceiptStore.Record] = for {
    record <- completedReceiptC(submitted, current, ports)
    _ <- ports.instances.load(ports.configuration, current.identity).flatMap {
      case Some(value) if _sameCurrentState(value, current) => Consequence.unit
      case _ => Consequence.stateConflict("RepositorySync review retention requires durably saved completion")
    }
    saved <- ports.reviews.putIfAbsentC(record)
    _ <- if (saved != null && saved.submitted != null && saved.issued != null && saved.retained != null &&
        saved.reference == record.reference && saved.submitted.handle == record.submitted.handle &&
        saved.submitted.continuationId == record.submitted.continuationId && saved.issued.handle == record.issued.handle)
      Consequence.unit else Consequence.stateConflict("RepositorySync review retention returned another slot")
  } yield saved

  /** Host-only read projection/recovery. It is deliberately not a new callable
    * Provided API: a failed post-resume projection must not repeat the review.
    */
  def projectDecisionC(reference: ContextReference, ports: Ports): Consequence[String] = for {
    _ <- _portsC(ports)
    _ <- if (reference != null && RepositorySyncMergeValidationAssessment.name(reference.identity) &&
        RepositorySyncMergeValidationAssessment.name(reference.revision)) Consequence.unit
      else Consequence.stateConflict("RepositorySync Decision review reference is missing")
    port <- ports.decisions match {
      case Some(value) => Consequence.success(value)
      case _ => Consequence.configurationInvalid("RepositorySync human Decision projection is not bound")
    }
    saved <- ports.reviews.loadC(reference).flatMap {
      case Some(value) if value != null && value.submitted != null && value.retained != null && value.retained.submission != null && value.reference == reference &&
          value.issued != null && value.issued.handle != null => Consequence.success(value)
      case _ => Consequence.stateConflict("RepositorySync Decision has no retained review")
    }
    handle <- saved.issued.handle.validateC
    current <- ports.instances.load(ports.configuration, handle.instanceIdentity).flatMap {
      case Some(value) if value != null && value.revision == InstanceRevision(9L) => value.validateC
      case _ => Consequence.stateConflict("RepositorySync Decision requires saved revision nine")
    }
    admitted <- completedReceiptC(saved.submitted, current, ports)
    _ <- if (admitted.retained.runId == saved.retained.runId &&
        admitted.retained.submission.candidate.reference == saved.retained.submission.candidate.reference &&
        saved.submitted.result.value.decision == "decision-needed") Consequence.unit
      else Consequence.stateConflict("RepositorySync Decision differs from its admitted review")
    wire <- RepositorySyncMergeDecision.projectC(current, saved, port)
    _ <- ports.instances.load(ports.configuration, current.identity).flatMap {
      case Some(value) if _sameCurrentState(value, current) => Consequence.unit
      case _ => Consequence.stateConflict("RepositorySync Decision instance changed during projection")
    }
  } yield wire

  /** Current execution reference/state/CAS scope, not a full-history no-change certificate. */
  private def _sameCurrentState(left: InstanceRecord, right: InstanceRecord): Boolean =
    left != null && right != null && B._sameDefinition(left.definition, right.definition) &&
      left.identity == right.identity && left.revision == right.revision && left.lifecycle == right.lifecycle &&
      left.currentProgression == right.currentProgression && left.correlation == right.correlation && left.suspension == right.suspension

  private def _portsC(p: Ports): Consequence[Unit] =
    if (p == null || p.retained == null || p.selections == null || p.mappings == null ||
        p.instances == null || p.configuration == null || p.runtime == null ||
        p.runtime == ContinuationRuntime.empty || p.issued == null ||
        p.requirement == null || p.presentation == null || p.asOf == null || p.freshUnitOfWork == null || p.reviews == null ||
        p.decisions == null || p.decisions.exists(_ == null) ||
        p.requirement.capabilities != Vector(CapabilityRequirement("review-merge-capability")) ||
        !p.requirement.reviewRequired)
      Consequence.configurationInvalid("RepositorySync public review binding is incomplete")
    else for {
      bound <- RepositorySyncDefinitionBinding.bindC
      generated = _provided
      _ <- if (generated.identity == bound.workflowIdentity.value &&
          generated.version == bound.workflowRevision.value &&
          generated.providedOperations.count(op => op.service == "RepositorySyncService" &&
            op.name == "requestMergeReview" && op.inputType.contains("RepositorySyncMergeReviewRequest") &&
            op.resultType.contains("RepositorySyncMergeReviewSubmissionResult")) == 1 &&
          generated.providedOperations.count(op => op.service == "RepositorySyncService" &&
            op.name == "submitMergeReview" && op.inputType.contains("RepositorySyncMergeReviewResult") &&
            op.resultType.contains("RepositorySyncMergeReviewSubmissionResult")) == 1) Consequence.unit
        else Consequence.configurationInvalid("RepositorySync public review differs from generated Operations")
      _ <- p.configuration.validateC
      _ <- p.requirement.validateC
      _ <- p.presentation.validateC
    } yield ()

  private def _provided: GeneratedProvidedApiAbi.Definition = {
    val generated = RepositorySyncWorkflowStateMachineProvidedApi3.workflow
    GeneratedProvidedApiAbi.Definition(generated.schemaVersion, generated.generator,
      generated.identity, generated.version, GeneratedProvidedApiAbi.WorkflowSourceCorrelation(
        GeneratedProvidedApiAbi.SourceIdentity(generated.source.root.line),
        GeneratedProvidedApiAbi.SourceIdentity(generated.source.definition.line)),
      generated.providedOperations.map(value => GeneratedProvidedApiAbi.Operation(value.service,
        value.name, value.inputType, value.resultType, GeneratedProvidedApiAbi.SourceIdentity(value.source.line))))
  }

  private def _prepareC(input: RepositorySyncMergeReviewRequest, p: Ports,
    supplied: Option[InstanceRecord] = None, forcePending: Boolean = false): Consequence[Prepared] =
    if (input == null || !RepositorySyncMergeValidationAssessment.name(input.submissionIdentity))
      Consequence.stateConflict("RepositorySync review input has no retained submission")
    else for {
      loaded <- p.retained.loadC(input.submissionIdentity)
      seed <- loaded match {
        case Some(value) if value != null && value.command != null && value.submission != null && value.runId != null =>
          Consequence.success(value)
        case _ => Consequence.stateConflict("RepositorySync review retained provenance is unavailable")
      }
      expected <- B.requestC(seed.submission)
      _ <- if (RepositorySyncMergeDecision.sameCandidateScope(input, expected)) Consequence.unit
        else Consequence.stateConflict("RepositorySync review request differs from retained validation")
      candidate <- RepositorySyncDefinitionBinding.candidateC
      definition <- WorkflowInstancePersistence.bindCandidateDefinitionC(candidate)
      command = seed.command
      _ <- if (command.mergeCommand != null && command.mergeCommand.relationCommand != null &&
          command.mergeCommand.relationCommand.preservationCommand != null &&
          command.mergeCommand.relationCommand.preservationCommand.fetchCommand != null &&
          command.mergeCommand.relationCommand.preservationCommand.fetchCommand.boundaryCommand != null)
        Consequence.unit else Consequence.stateConflict("RepositorySync retained command chain is incomplete")
      start = command.mergeCommand.relationCommand.preservationCommand.fetchCommand.boundaryCommand.startCommand
      initial <- RepositorySyncStartExecution.prepareC(start, p.selections, p.mappings, definition, p.configuration)
      stored <- supplied match {
        case Some(value) => Consequence.success(Some(value))
        case None => p.instances.load(p.configuration, initial.identity)
      }
      current <- stored match {
        case Some(value) if value != null && value.identity == initial.identity => value.validateC
        case _ => Consequence.stateConflict("RepositorySync review WorkflowInstance is unavailable")
      }
      prefix <- B.validatedPrefixC(current)
      context = ContextBundle("Review the full validated pending merge", Vector.empty, Vector.empty,
        ContextSnapshot(definition.workflowRevision.value, modelRevision = Some(expected.reviewPolicyVersion),
          evidenceRevision = Some(expected.validationDigest)))
      provided = StateMachineProvidedApiRequest(definition.workflowIdentity.value, definition.workflowRevision.value,
        seed.runId, StateMachineOperationIdentity("RepositorySyncService", "requestMergeReview"),
        Some(StateMachineOperationInput(StateMachineInputTypeReference("RepositorySyncMergeReviewRequest"),
          ContextReference(input.submissionIdentity, input.candidateRevision))), context)
      operation <- RepositorySyncDefinitionBinding.reviewOperationC
      submission = if (forcePending || current.revision == InstanceRevision(8L))
        seed.submission.copy(evidence = Vector.empty) else seed.submission
      planned <- B.planC(command, submission, p.asOf(), prefix, provided, operation,
        p.selections, p.mappings, p.configuration, current.suspension.map(boundary =>
          org.goldenport.cncf.workflow.ContinuationIdentity(boundary.continuationIdentity.value)))
    } yield Prepared(seed, current, prefix, planned, candidate)

  private def _waitC(record: InstanceRecord, snapshot: ContextSnapshot, situation: String): Consequence[OperationResponse] =
    for {
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, record)
      wire <- WorkflowWaitJsonV1.encodeC(WorkflowInteraction(handle, WorkflowContinuation.Wait(
        StateMachineRevision(record.revision.value.toString), snapshot, MinimalPresentation("Repository Sync", situation))))
    } yield OperationResponse.Scalar(wire)

  private final class RequestOperation(p: Ports) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      SmWorkflowComponent.RepositorySyncServiceService.RequestMergeReviewOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != "requestMergeReview" || !request.service.contains("RepositorySyncService"))
        Consequence.stateConflict("RepositorySync public review request differs from the CML Operation")
      else request.arguments match {
        case List(Argument("arg1", wire: String, _)) => decodeRequestC(wire).map(input => RequestAction(request, input, p))
        case _ => Consequence.stateConflict("RepositorySync review request JSON is missing")
      }
  }
  private final case class RequestAction(request: Request, input: RepositorySyncMergeReviewRequest, p: Ports) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(core: ActionCall.Core): ActionCall = RequestCall(core, input, p)
  }
  private final case class RequestCall(core: ActionCall.Core, input: RepositorySyncMergeReviewRequest, p: Ports)
      extends ProcedureActionCall {
    def execute(): Consequence[OperationResponse] = _prepareC(input, p).flatMap { prepared =>
      prepared.planned.pending match {
        case None => _waitC(prepared.current, ContextSnapshot(prepared.current.definition.workflowRevision.value,
          modelRevision = Some(input.reviewPolicyVersion), evidenceRevision = Some(input.validationDigest)),
          if (prepared.planned.assessed.accepted) "Full merge review already admitted; awaiting deterministic merge commit"
          else "Existing merge review requires repair or a decision; commit remains blocked")
        case Some(pending) => for {
          projected <- B.suspendedRecordC(pending, prepared.prefix)
          _ <- if (prepared.current.revision == InstanceRevision(7L) || B._sameRecord(prepared.current, projected))
            Consequence.unit else Consequence.stateConflict("RepositorySync saved review suspension differs")
          _ <- if (prepared.current.revision == InstanceRevision(7L)) _publishC(pending, prepared, projected, p)
            else _claimOrPublishC(pending, prepared, projected, p)
          _ <- p.runtime.recoverClaimC(pending.continuation.continuationId) match {
            case Consequence.Success(_) => Consequence.unit
            case _ => p.runtime.claimC(pending.continuation.continuationId).map(_ => ())
          }
          work <- B.issueRecoveredWorkOrderC(pending, projected, p.runtime, p.issued,
            p.requirement, p.presentation, p.instances, p.configuration)
          wire <- WorkflowWorkOrderJsonV1.encodeC(work, B.inputCodec)
        } yield OperationResponse.Scalar(wire)
      }
    }
  }

  private def _claimOrPublishC(pending: B.PendingReview, prepared: Prepared, projected: InstanceRecord, p: Ports) =
    p.runtime.recoverClaimC(pending.continuation.continuationId) match {
      case Consequence.Success(_) => Consequence.unit
      case _ => p.runtime.claimC(pending.continuation.continuationId) match {
        case Consequence.Success(_) => Consequence.unit
        case _ => _publishC(pending, prepared, projected, p)
      }
    }
  private def _publishC(pending: B.PendingReview, prepared: Prepared, projected: InstanceRecord, p: Ports): Consequence[Unit] =
    for {
      uow <- Option(p.freshUnitOfWork()) match {
        case Some(value) => Consequence.success(value)
        case None => Consequence.stateConflict("RepositorySync review UnitOfWork is unavailable")
      }
      _ <- p.runtime.stageSuspensionAfterC(uow, pending.continuation, () => {
        val saved = if (prepared.current.revision == InstanceRevision(7L))
          p.instances.append(p.configuration, prepared.current.identity, InstanceRevision(7L), projected.history.last)
        else p.instances.load(p.configuration, prepared.current.identity).flatMap {
          case Some(value) => Consequence.success(value)
          case _ => Consequence.stateConflict("RepositorySync saved suspension is unavailable")
        }
        saved.flatMap(value => if (B._sameRecord(value, projected)) Consequence.unit
          else Consequence.stateConflict("RepositorySync review suspension append diverged"))
      })
      _ <- uow.commit()
    } yield ()
}
