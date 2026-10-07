package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.RepositorySyncMergeReviewRequest

/** Stop-only projection of an already admitted, durably retained merge review.
  * Host boundary identities are metadata, not callable Provided API endpoints.
  * No human Result, Required SPI, claim, resume, Git write, or approval is added.
  * @since   Sep. 29, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncMergeDecision {
  final case class Input(candidate: RepositorySyncMergeReviewRequest,
    reviewResultReference: ContextReference, reviewResultDigest: String)
  final case class Intent(handle: WorkflowHandle, runId: StateMachineRunIdentity,
    reviewContinuationId: org.goldenport.cncf.workflow.ContinuationIdentity,
    expectedRevision: StateMachineRevision, snapshot: ContextSnapshot,
    input: Input, idempotencyKey: String)
  trait Port {
    /** Retain stable host-owned identities for this exact stopped intent. */
    def resolveC(intent: Intent): Consequence[ContinuationRequest[Input]]
  }

  val inputCodec: PayloadCodec[Input] = new PayloadCodec[Input] {
    val typeIdentity = "RepositorySyncMergeDecisionInput"
    def encode(value: Input): Json = Json.obj(
      "candidate" -> RepositorySyncMergeReviewBoundary.inputCodec.encode(value.candidate),
      "reviewResultReference" -> Json.obj(
        "identity" -> Json.fromString(value.reviewResultReference.identity),
        "revision" -> Json.fromString(value.reviewResultReference.revision)),
      "reviewResultDigest" -> Json.fromString(value.reviewResultDigest))
    def decode(value: Json): Either[String, Input] = for {
      fields <- value.asObject.filter(_.keys.toSet == Set("candidate", "reviewResultReference", "reviewResultDigest"))
        .toRight("invalid RepositorySync Decision input shape")
      candidate <- RepositorySyncMergeReviewBoundary.inputCodec.decode(fields("candidate").get)
      ref <- fields("reviewResultReference").flatMap(_.asObject)
        .filter(_.keys.toSet == Set("identity", "revision")).toRight("invalid RepositorySync Decision review reference")
      identity <- _string(ref, "identity")
      revision <- _string(ref, "revision")
      digest <- _string(fields, "reviewResultDigest")
      _ <- Either.cond(digest.matches("[0-9a-f]{64}"), (), "invalid RepositorySync Decision review digest")
    } yield Input(candidate, ContextReference(identity, revision), digest)
  }

  /** The caller re-admits the complete saved review chain before and after this
    * projection. Revision nine stays active and unsuspended: this is not a new
    * resumable engine boundary or permission to commit the reviewed candidate.
    */
  def projectC(record: InstanceRecord, receipt: RepositorySyncMergeReviewReceiptStore.Record,
    port: Port): Consequence[String] =
    if (record == null || receipt == null || receipt.retained == null || receipt.submitted == null || port == null)
      Consequence.stateConflict("RepositorySync Decision projection is incomplete")
    else for {
      current <- record.validateC
      definition <- RepositorySyncDefinitionBinding.bindC
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current)
      _ <- if (current.revision == InstanceRevision(9L) && current.lifecycle == Lifecycle.Active &&
          current.suspension.isEmpty && current.currentProgression == Some(ProgressionReference("sync.MergeValidated")) &&
          RepositorySyncMergeReviewBoundary._sameDefinition(current.definition, definition) && receipt.issued.handle == handle &&
          receipt.submitted.handle == handle && receipt.submitted.result.value.decision == "decision-needed") Consequence.unit
        else Consequence.stateConflict("RepositorySync Decision requires the exact stopped merge review")
      candidate <- RepositorySyncMergeReviewBoundary.requestC(receipt.retained.submission)
      resultWire <- WorkflowResultJsonV1.encodeC(receipt.submitted, RepositorySyncMergeReviewResultAdapter.payloadCodec)
      digest = RepositorySyncMergeValidationAssessment.digest(Vector("repository-sync-retained-review-v1", resultWire))
      intent = Intent(handle, receipt.retained.runId, receipt.submitted.continuationId,
        StateMachineRevision("9"), receipt.submitted.contextSnapshot,
        Input(candidate, receipt.submitted.resultReference, digest),
        s"repository-sync-merge-decision:${handle.instanceIdentity.value}:$digest")
      request <- port.resolveC(intent)
      review <- RepositorySyncDefinitionBinding.reviewOperationC
      _ <- _requestC(intent, request, review)
      wire <- WorkflowDecisionJsonV1.encodeC(WorkflowInteraction[Input, Nothing](handle,
        WorkflowContinuation.Decision(request, MinimalPresentation("Repository Sync",
          "Merge decision required; commit remains blocked"))), inputCodec)
    } yield wire

  private def _requestC(intent: Intent, request: ContinuationRequest[Input],
    review: StateMachineRequiredOperation): Consequence[Unit] = {
    val candidate = ContextReference(intent.input.candidate.submissionIdentity, intent.input.candidate.candidateRevision)
    if (request == null || request.context == null || request.requiredOperation == null ||
        request.runId != intent.runId || request.continuationId == intent.reviewContinuationId ||
        request.expectedRevision != intent.expectedRevision || request.context.snapshot != intent.snapshot ||
        request.input != Some(TypedValue(inputCodec.typeIdentity, intent.input)) ||
        request.resultType == null || request.resultType.nonEmpty ||
        request.context.requiredFacts == null || !request.context.requiredFacts.contains(candidate) ||
        request.context.references == null || !request.context.references.contains(intent.input.reviewResultReference) ||
        request.operation == null || request.completionOperation == null ||
        request.operation.service == "RepositorySyncService" || request.completionOperation.service == "RepositorySyncService" ||
        request.requiredOperation == review.identity || request.operation == review.operation ||
        request.completionOperation == review.operation)
      Consequence.stateConflict("RepositorySync Decision host request differs from its frozen intent")
    else Consequence.unit // Common wire admission validates the remaining metadata.
  }

  private def _string(fields: JsonObject, key: String): Either[String, String] =
    fields(key).flatMap(_.asString).filter(_.trim.nonEmpty).toRight(s"missing RepositorySync Decision $key")
}
