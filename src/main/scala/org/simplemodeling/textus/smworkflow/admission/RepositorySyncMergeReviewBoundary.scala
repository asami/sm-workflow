package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.CandidateAdmissionGapRouting.{Binding, Target}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncMergeReviewRequest, RepositorySyncMergeValidationAdmissionCommand}
import RepositorySyncMergeValidationAssessment.name

/** A declared full-merge review gap becomes a common Continuation only after
  * exact retained validation/history admission. The host persists the projected
  * suspension before CNCF publishes it; this boundary does not commit or push.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncMergeReviewBoundary {
  val inputCodec = new WorkflowResultJsonV1.PayloadCodec[RepositorySyncMergeReviewRequest] {
    val typeIdentity = "RepositorySyncMergeReviewRequest"
    def encode(value: RepositorySyncMergeReviewRequest): Json = value.asJson
    def decode(value: Json): Either[String, RepositorySyncMergeReviewRequest] =
      if (value != null && value.asObject.exists(_.keys.toSet == Set("submissionIdentity", "repositoryIdentity",
          "candidateRevision", "validationDigest", "reviewPolicyVersion", "pendingHeadOid",
          "pendingTreeOid", "pendingTreeDigest", "orderedParents", "changedPaths", "selectedChecks")))
        value.as[RepositorySyncMergeReviewRequest].left.map(_.message)
      else Left("RepositorySync merge review input has an incompatible shape")
  }
  final class PendingReview private[RepositorySyncMergeReviewBoundary] (
    val continuation: Continuation,
    val input: TypedValue[RepositorySyncMergeReviewRequest],
    private[RepositorySyncMergeReviewBoundary] val active: InstanceRecord,
    val candidateRevision: String
  ) {
    def outcome: ActionExecution = ActionExecution.Suspended(continuation)
  }
  final case class Planned(assessed: RepositorySyncMergeReviewAdmission.Assessed,
    pending: Option[PendingReview])

  def requestC(submission: RepositorySyncMergeReviewAdmission.Submission): Consequence[RepositorySyncMergeReviewRequest] =
    if (submission == null || submission.candidate == null || submission.candidate.payload == null)
      Consequence.stateConflict("RepositorySync retained review candidate is missing")
    else for {
      admitted <- submission.validateC
      validated <- RepositorySyncMergeValidationAssessment.readmitC(admitted.candidate.payload.value)
      _ <- if (admitted.candidate.payload.typeIdentity == RepositorySyncMergeReviewAdmission.candidateType &&
          admitted.candidate.reference.identity == s"${validated.plan.context.boundary.snapshot.repositoryIdentity}:pending-merge")
        Consequence.unit else Consequence.stateConflict("RepositorySync retained review candidate has incompatible scope or type")
    } yield _request(admitted)

  private def _request(submission: RepositorySyncMergeReviewAdmission.Submission): RepositorySyncMergeReviewRequest = {
    val validated = submission.candidate.payload.value
    val context = validated.plan.context
    RepositorySyncMergeReviewRequest(submission.identity, context.boundary.snapshot.repositoryIdentity,
      submission.candidate.reference.revision, validated.digest, validated.plan.policy.reviewPolicyVersion,
      context.merge.pending.state.localHeadOid, context.merge.pending.resultTreeOid,
      context.merge.pending.resultTreeDigest, context.merge.frozen.state.parentTips,
      context.merge.receipt.changedPaths, validated.plan.selectedChecks)
  }

  /** Read-only prefix for reconstructing an exact saved suspension on retry.
    * It never rolls back or writes the current WorkflowInstance.
    */
  private[admission] def validatedPrefixC(current: InstanceRecord): Consequence[InstanceRecord] =
    if (current == null) Consequence.stateConflict("RepositorySync review instance is missing")
    else current.validateC.flatMap { valid =>
      if (valid.revision == InstanceRevision(7L)) Consequence.success(valid)
      else if (valid.revision == InstanceRevision(8L) && valid.history.size == 8 && valid.suspension.nonEmpty) {
        val entry = valid.history(6)
        valid.copy(revision = entry.revision, lifecycle = entry.lifecycle,
          currentProgression = entry.currentProgression, correlation = entry.correlation,
          causation = entry.causation, derivedCompositeOccurrence = entry.derivedCompositeOccurrence,
          committedPredecessor = entry.committedPredecessor, suspension = entry.suspension,
          history = valid.history.take(7)).validateC
      } else Consequence.stateConflict("RepositorySync review requires validated or suspended history")
    }

  /** Exact read-only historical prefix; no rollback or implicit persistence. */
  private[admission] def prefixC(current: InstanceRecord, revision: Long): Consequence[InstanceRecord] =
    if (current == null || revision < 1L || revision > 12L)
      Consequence.stateConflict("RepositorySync historical prefix is missing")
    else current.validateC.flatMap { valid =>
      if (valid.history.size < revision || valid.revision.value < revision)
        Consequence.stateConflict("RepositorySync historical prefix is unavailable")
      else {
        val entry = valid.history(revision.toInt - 1)
        valid.copy(revision = entry.revision, lifecycle = entry.lifecycle,
          currentProgression = entry.currentProgression, correlation = entry.correlation,
          causation = entry.causation, derivedCompositeOccurrence = entry.derivedCompositeOccurrence,
          committedPredecessor = entry.committedPredecessor, suspension = entry.suspension,
          history = valid.history.take(revision.toInt)).validateC
      }
    }

  def planC(
    command: RepositorySyncMergeValidationAdmissionCommand,
    submission: RepositorySyncMergeReviewAdmission.Submission,
    asOf: Instant,
    instance: InstanceRecord,
    provided: StateMachineProvidedApiRequest,
    reviewOperation: StateMachineRequiredOperation,
    selections: ProfileStartAdmission.SelectionPort,
    mappings: RepositorySyncStartExecution.TrackingMappingPort,
    configuration: Configuration,
    savedContinuation: Option[org.goldenport.cncf.workflow.ContinuationIdentity] = None
  ): Consequence[Planned] =
    if (!_command(command) || instance == null || submission == null ||
        submission.candidate == null || submission.candidate.payload == null ||
        savedContinuation == null || savedContinuation.exists(value => value == null || !name(value.value)))
      Consequence.stateConflict("RepositorySync review command or instance is incomplete")
    else for {
      declared <- RepositorySyncDefinitionBinding.bindC
      operation <- RepositorySyncDefinitionBinding.reviewOperationC
      _ <- if (_sameDefinition(instance.definition, declared) && reviewOperation == operation) Consequence.unit
        else Consequence.stateConflict("RepositorySync review differs from generated candidate declarations")
      validated <- RepositorySyncMergeValidationAssessment.readmitC(submission.candidate.payload.value)
      start = command.mergeCommand.relationCommand.preservationCommand.fetchCommand.boundaryCommand.startCommand
      initial <- RepositorySyncStartExecution.prepareC(start, selections, mappings, declared, configuration)
      boundary <- RepositorySyncBoundaryAssessment.assessC(start.input, new RepositorySyncBoundaryAssessment.Port {
        def captureC(repositoryIdentity: String) = Consequence.success(Some(validated.plan.context.boundary.snapshot))
      })
      _ <- if (boundary == validated.plan.context.boundary) Consequence.unit
        else Consequence.stateConflict("RepositorySync review validation belongs to another boundary")
      history <- _validatedHistoryC(command, initial, validated)
      active <- instance.validateC
      _ <- if (active.identity == initial.identity && active.correlation == initial.correlation &&
          active.lifecycle == Lifecycle.Active && active.revision == InstanceRevision(7L) &&
          active.history == history && active.currentProgression == Some(ProgressionReference("sync.MergeValidated")) &&
          active.derivedCompositeOccurrence.isEmpty && active.committedPredecessor.isEmpty && active.suspension.isEmpty)
        Consequence.unit
      else Consequence.stateConflict("RepositorySync review requires exact persisted validation history")
      assessed <- RepositorySyncMergeReviewAdmission.assessC(submission, asOf)
      _ <- if (_provided(provided, active, submission.identity, assessed.evaluation.target.revision, validated))
        Consequence.unit
      else Consequence.stateConflict("RepositorySync review Provided request differs from the candidate")
      missing <- RepositorySyncMergeReviewAdmission.assessC(submission.copy(evidence = Vector.empty), asOf)
      bindings = Vector(Binding(RepositorySyncMergeReviewAdmission.requirement, Target.SemanticAction(operation)))
      declaredGap <- CandidateAdmissionGapRouting.resolveForInstanceC(missing.evaluation, bindings, active)
      _ <- if (declaredGap.size == 1) Consequence.unit
        else Consequence.stateConflict("RepositorySync review declaration is ambiguous")
      routed <- CandidateAdmissionGapRouting.resolveForInstanceC(assessed.evaluation, bindings, active)
      _ <- if (routed.size == assessed.evaluation.gaps.size && routed.size <= 1) Consequence.unit
        else Consequence.stateConflict("RepositorySync review routing is ambiguous")
    } yield Planned(assessed, routed.headOption.map { resolution =>
      val target = assessed.evaluation.target
      val input = _request(submission)
      new PendingReview(Continuation(provided.runId,
        savedContinuation.getOrElse(org.goldenport.cncf.workflow.ContinuationIdentity(s"${active.identity.value}:8:merge-review")),
        StateMachineRevision("8"), resolution.target.asInstanceOf[Target.SemanticAction].operation,
        provided.context), TypedValue("RepositorySyncMergeReviewRequest", input), active, target.revision)
    })

  /** Pure projection; caller must save this exact record before publication. */
  def suspendedRecordC(pending: PendingReview, current: InstanceRecord): Consequence[InstanceRecord] =
    if (pending == null || current == null || !_sameRecord(current, pending.active))
      Consequence.stateConflict("RepositorySync review suspension requires the exact validated instance")
    else current.appendC(InstanceRevision(7L), HistoryEntry(
      HistorySequence(8L), InstanceRevision(8L), Lifecycle.Active, current.currentProgression,
      current.correlation, CausationReference(s"merge-review:${pending.continuation.continuationId.value}"),
      None, None, Some(SuspensionBoundary(
        WorkflowInstancePersistence.ContinuationIdentity(pending.continuation.continuationId.value),
        InstanceRevision(8L),
        ContextSnapshotReference(s"${pending.input.value.repositoryIdentity}:pending-merge@${pending.candidateRevision}"),
        CompletionReference(pending.continuation.requiredOperation.metadata.completionContract.identity),
        EvidenceReference(pending.continuation.requiredOperation.metadata.evidenceContract.identity),
        CorrelationReference(s"review:${pending.continuation.continuationId.value}")))))

  /** Recovery uses CNCF's persisted/claimed Continuation and issued-work store.
    * Merely projecting a suspension or presenting WAIT cannot issue work.
    */
  def issueRecoveredWorkOrderC(
    pending: PendingReview,
    suspended: InstanceRecord,
    runtime: ContinuationRuntime,
    issued: IssuedWorkOrderPersistence[RepositorySyncMergeReviewRequest],
    requirement: ExecutionRequirement,
    presentation: MinimalPresentation,
    instances: WorkflowInstancePersistence,
    configuration: Configuration
  ): Consequence[WorkflowInteraction[RepositorySyncMergeReviewRequest, Nothing]] =
    if (pending == null || suspended == null || runtime == null || issued == null ||
        instances == null || configuration == null ||
        requirement == null || requirement.capabilities !=
          Vector(CapabilityRequirement("review-merge-capability")) || !requirement.reviewRequired)
      Consequence.stateConflict("RepositorySync review WorkOrder dependencies are incompatible")
    else for {
      expected <- suspendedRecordC(pending, pending.active)
      _ <- if (_sameRecord(suspended, expected)) Consequence.unit
        else Consequence.stateConflict("RepositorySync review WorkOrder requires the exact persisted suspension")
      retained <- instances.load(configuration, suspended.identity)
      saved <- retained match {
        case Some(record) if record != null => record.validateC
        case _ => Consequence.stateConflict("RepositorySync review suspension has not been durably saved")
      }
      _ <- if (_sameRecord(saved, expected)) Consequence.unit
        else Consequence.stateConflict("RepositorySync review persisted suspension differs")
      declared <- RepositorySyncDefinitionBinding.bindC
      _ <- if (_sameDefinition(suspended.definition, declared)) Consequence.unit
        else Consequence.stateConflict("RepositorySync review WorkOrder definition changed")
      claim <- runtime.recoverClaimC(pending.continuation.continuationId)
      _ <- if (claim != null && claim.continuation != null &&
          claim.continuation.runId == pending.continuation.runId &&
          claim.continuation.continuationId == pending.continuation.continuationId &&
          claim.continuation.expectedRevision == pending.continuation.expectedRevision &&
          claim.continuation.requiredOperation == pending.continuation.requiredOperation &&
          claim.continuation.context != null && GoalPhaseClosureAuthority.sameContextScope(
            claim.continuation.context.snapshot, pending.continuation.context.snapshot)) Consequence.unit
        else Consequence.stateConflict("RepositorySync claimed review differs from retained provenance")
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, suspended)
      work <- WorkflowProtocolV1.issueRecoveredWorkOrderC(handle, pending.continuation.continuationId,
        Some(pending.input), StateMachineOperationIdentity("RepositorySyncService", "submitMergeReview"),
        requirement, presentation, runtime, issued)
    } yield work

  // Rehydration resolves the execution definition; diagnostic metadata is not
  // a certificate of unchanged content.
  private[admission] def _sameDefinition(left: DefinitionBinding, right: DefinitionBinding): Boolean =
    SplitPhaseDefinitionBinding.sameDefinition(left, right)

  private[admission] def _sameRecord(left: InstanceRecord, right: InstanceRecord): Boolean =
    left != null && right != null && _sameDefinition(left.definition, right.definition) &&
      left.copy(definition = right.definition) == right

  private def _validatedHistoryC(command: RepositorySyncMergeValidationAdmissionCommand,
    initial: InstanceRecord, validated: RepositorySyncMergeValidationAssessment.Assessment): Consequence[Vector[HistoryEntry]] = {
    val relation = command.mergeCommand.relationCommand
    val preservation = relation.preservationCommand
    val fetch = preservation.fetchCommand
    val context = validated.plan.context
    for {
      classified <- RepositorySyncRelationAdmissionExecution.classifiedHistoryC(initial,
        fetch.boundaryCommand.idempotencyKey, context.scope.digest, fetch.idempotencyKey, context.fetch.digest,
        preservation.idempotencyKey, context.preservation.digest, relation.idempotencyKey, context.relation.digest)
      merged <- RepositorySyncMergeAdmissionExecution.mergedHistoryC(initial, classified,
        command.mergeCommand.idempotencyKey, context.merge.digest)
      history <- RepositorySyncMergeValidationAdmissionExecution.validatedHistoryC(initial, merged,
        command.idempotencyKey, validated.digest)
    } yield history
  }

  private def _command(command: RepositorySyncMergeValidationAdmissionCommand): Boolean =
    command != null && command.mergeCommand != null && command.mergeCommand.relationCommand != null &&
      command.mergeCommand.relationCommand.preservationCommand != null &&
      command.mergeCommand.relationCommand.preservationCommand.fetchCommand != null &&
      command.mergeCommand.relationCommand.preservationCommand.fetchCommand.boundaryCommand != null &&
      command.mergeCommand.relationCommand.preservationCommand.fetchCommand.boundaryCommand.startCommand != null &&
      command.expectedRevision == "6" && command.mergeCommand.expectedRevision == "5" &&
      command.mergeCommand.relationCommand.expectedRevision == "4" &&
      command.mergeCommand.relationCommand.preservationCommand.expectedRevision == "3" &&
      command.mergeCommand.relationCommand.preservationCommand.fetchCommand.expectedRevision == "2" &&
      command.mergeCommand.relationCommand.preservationCommand.fetchCommand.boundaryCommand.expectedRevision == "1" &&
      name(command.idempotencyKey) && name(command.mergeCommand.idempotencyKey) &&
      name(command.mergeCommand.relationCommand.idempotencyKey) &&
      name(command.mergeCommand.relationCommand.preservationCommand.idempotencyKey) &&
      name(command.mergeCommand.relationCommand.preservationCommand.fetchCommand.idempotencyKey) &&
      name(command.mergeCommand.relationCommand.preservationCommand.fetchCommand.boundaryCommand.idempotencyKey)

  private def _provided(provided: StateMachineProvidedApiRequest, active: InstanceRecord,
    submissionIdentity: String, candidateRevision: String,
    validated: RepositorySyncMergeValidationAssessment.Assessment): Boolean =
    provided != null && provided.workflowIdentity == active.definition.workflowIdentity.value &&
      provided.workflowRevision == active.definition.workflowRevision.value &&
      provided.operation == StateMachineOperationIdentity("RepositorySyncService", "requestMergeReview") &&
      provided.runId != null && name(provided.runId.value) && provided.input != null &&
      provided.input.exists(input => input != null &&
        input.typeReference == StateMachineInputTypeReference("RepositorySyncMergeReviewRequest") &&
        input.contextReference == ContextReference(submissionIdentity, candidateRevision)) &&
      provided.context != null && provided.context.summary != null &&
      provided.context.summary.trim.nonEmpty && provided.context.snapshot != null &&
      provided.context.snapshot.workflowRevision == provided.workflowRevision &&
      provided.context.snapshot.modelRevision == Some(validated.plan.policy.reviewPolicyVersion) &&
      provided.context.requiredFacts != null && provided.context.references != null &&
      (provided.context.requiredFacts ++ provided.context.references).forall(reference =>
        reference != null && name(reference.identity) && name(reference.revision))
}
