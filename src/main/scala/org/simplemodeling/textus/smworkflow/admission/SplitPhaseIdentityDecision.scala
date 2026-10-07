package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, JsonObject}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Stop-only application projection of an identity/numbering authority gap.
  * No Required SPI, Provider, claim, resume, approval, or document mutation is
  * created here. A host supplies its own human-boundary identities; they are
  * metadata, not generated Provided API registration or callable endpoints.
  * Human-result submission/execution remains a separate, unimplemented boundary.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseIdentityDecision {
  final case class Input(
    reason: String,
    sourceSnapshotIdentity: String,
    sourcePhaseIdentity: String,
    sourceAuthorityReference: String,
    sourceSnapshotDigest: String,
    proposalIdentity: String,
    proposalDigest: String
  )

  final case class Intent(
    handle: WorkflowHandle,
    expectedRevision: StateMachineRevision,
    snapshot: ContextSnapshot,
    input: Input,
    idempotencyKey: String
  )

  trait Port {
    /** Resolve only a projection for this exact stopped instance and input.
      * Implementations retain stable run/continuation and host-owned boundary
      * identities for retries. This is not authority to execute either identity.
      */
    def resolveC(intent: Intent): Consequence[ContinuationRequest[Input]]
  }

  val inputCodec: PayloadCodec[Input] = new PayloadCodec[Input] {
    val typeIdentity = "SplitPhaseIdentityDecisionInput"
    def encode(value: Input): Json = Json.obj(
      "reason" -> Json.fromString(value.reason),
      "sourceSnapshotIdentity" -> Json.fromString(value.sourceSnapshotIdentity),
      "sourcePhaseIdentity" -> Json.fromString(value.sourcePhaseIdentity),
      "sourceAuthorityReference" -> Json.fromString(value.sourceAuthorityReference),
      "sourceSnapshotDigest" -> Json.fromString(value.sourceSnapshotDigest),
      "proposalIdentity" -> Json.fromString(value.proposalIdentity),
      "proposalDigest" -> Json.fromString(value.proposalDigest)
    )
    def decode(value: Json): Either[String, Input] = for {
      fields <- value.asObject.filter(_.keys.toSet == Set("reason", "sourceSnapshotIdentity",
        "sourcePhaseIdentity", "sourceAuthorityReference", "sourceSnapshotDigest",
        "proposalIdentity", "proposalDigest")).toRight("invalid SplitPhase Decision input shape")
      reason <- _string(fields, "reason")
      source <- _string(fields, "sourceSnapshotIdentity")
      phase <- _string(fields, "sourcePhaseIdentity")
      authority <- _string(fields, "sourceAuthorityReference")
      sourceDigest <- _string(fields, "sourceSnapshotDigest")
      proposal <- _string(fields, "proposalIdentity")
      proposalDigest <- _string(fields, "proposalDigest")
      input = Input(reason, source, phase, authority, sourceDigest, proposal, proposalDigest)
      _ <- Either.cond(_valid(input), (), "invalid SplitPhase Decision identity evidence")
    } yield input
  }

  def projectC(
    stopped: WorkflowInteraction[Nothing, Nothing],
    record: InstanceRecord,
    assessment: SplitPhaseEntryAssessment.Assessment,
    reason: String,
    key: String,
    definition: DefinitionBinding,
    port: Port
  ): Consequence[String] =
    if (stopped == null || record == null || assessment == null ||
        assessment.source == null || assessment.proposalCandidate == null ||
        definition == null || port == null || !_name(key))
      Consequence.stateConflict("SplitPhase Decision projection is incomplete")
    else for {
      admitted <- record.validateC
      bound <- definition.validateC
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, admitted)
      wait <- stopped.current match {
        case value: WorkflowContinuation.Wait => Consequence.success(value)
        case _ => Consequence.stateConflict("SplitPhase Decision requires a stopped assessment")
      }
      candidate <- assessment.proposalCandidate match {
        case Some(value) if value != null => Consequence.success(value)
        case _ => Consequence.stateConflict("SplitPhase Decision proposal is missing")
      }
      input = Input(reason, assessment.source.identity, assessment.source.sourcePhaseIdentity,
        assessment.source.authorityReference, assessment.source.digest,
        candidate.identity, candidate.digest)
      _ <- if (_valid(input) && handle == stopped.handle &&
          SplitPhaseDefinitionBinding.sameDefinition(bound, admitted.definition) &&
          handle.workflowIdentity.value == "SplitPhaseWorkflow" &&
          admitted.revision == InstanceRevision(1L) &&
          admitted.lifecycle == Lifecycle.Active && admitted.suspension.isEmpty &&
          admitted.currentProgression == Some(ProgressionReference("entry.EntryAssessment")) &&
          wait.expectedRevision == StateMachineRevision("1") &&
          wait.contextSnapshot == ContextSnapshot(handle.workflowRevision.value,
            modelRevision = Some(assessment.source.revision),
            evidenceRevision = Some(assessment.source.digest))) Consequence.unit
        else Consequence.stateConflict("SplitPhase Decision differs from the stopped source boundary")
      intent = Intent(handle, wait.expectedRevision, wait.contextSnapshot, input, key)
      request <- port.resolveC(intent)
      semantic <- SplitPhaseDefinitionBinding.semanticOperationC
      _ <- _requestC(intent, request, semantic)
      wire <- WorkflowDecisionJsonV1.encodeC(
        WorkflowInteraction[Input, Nothing](handle, WorkflowContinuation.Decision(request,
          wait.presentation)), inputCodec)
    } yield wire

  private def _requestC(intent: Intent, request: ContinuationRequest[Input],
    semantic: StateMachineRequiredOperation): Consequence[Unit] = {
    val source = ContextReference(intent.input.sourceSnapshotIdentity,
      intent.snapshot.modelRevision.get)
    val proposal = ContextReference(intent.input.proposalIdentity, intent.input.proposalDigest)
    if (request == null || request.context == null || request.requiredOperation == null ||
        request.expectedRevision != intent.expectedRevision ||
        request.context.snapshot != intent.snapshot ||
        request.input != Some(TypedValue(inputCodec.typeIdentity, intent.input)) ||
        request.resultType == null || request.resultType.nonEmpty ||
        request.context.requiredFacts == null ||
        request.context.references == null || !request.context.requiredFacts.contains(source) ||
        !request.context.references.contains(proposal) ||
        request.operation == null || request.completionOperation == null ||
        request.operation.service == "SplitPhaseService" ||
        request.completionOperation.service == "SplitPhaseService" ||
        semantic.identity == request.requiredOperation ||
        semantic.operation == request.operation ||
        semantic.operation == request.completionOperation)
      Consequence.stateConflict("SplitPhase Decision host request differs from its frozen intent")
    else Consequence.unit // The common encoder validates the remaining wire fields.
  }

  private def _valid(input: Input): Boolean = input != null &&
    Set("nested-or-unknown-phase-numbering", "child-phase-identity-collision").contains(input.reason) &&
    Vector(input.sourceSnapshotIdentity, input.sourcePhaseIdentity,
      input.sourceAuthorityReference, input.proposalIdentity).forall(_name) &&
    Vector(input.sourceSnapshotDigest, input.proposalDigest).forall(value =>
      value != null && value.matches("[0-9a-f]{64}"))

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
  private def _string(fields: JsonObject, key: String): Either[String, String] =
    fields(key).flatMap(_.asString).filter(_name).toRight(s"missing SplitPhase Decision $key")
}
