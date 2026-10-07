package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.SplitPhaseEntryLifecycle

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateGateDerivation.Evidence
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.{Outcome as PartitionOutcome, Policy}
import org.simplemodeling.textus.smworkflow.value.{SplitPhasePreviewCommand, SplitPhaseStartCommand}

/**
 * A host-owned catalog decision, never accepted from the preview command.
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhasePreviewExecution {
  final case class Catalog(
    repositoryRoot: Path,
    snapshotIdentity: String,
    enumerated: PartitionOutcome.Enumerated,
    policy: Policy,
    evidences: Vector[Evidence],
    admitted: SplitPhaseProposalAdmission.Outcome.Admitted
  )

  trait CatalogPort {
    def loadC(startCommand: SplitPhaseStartCommand): Consequence[Catalog]
  }

  trait CallScopedCatalogPort extends CatalogPort {
    final def loadC(startCommand: SplitPhaseStartCommand): Consequence[Catalog] =
      Consequence.stateConflict("SplitPhase catalog requires its current Operation call")
    def loadForCallC(startCommand: SplitPhaseStartCommand,
      core: org.goldenport.cncf.action.ActionCall.Core): Consequence[Catalog]
  }

  def forCall(port: CatalogPort, core: org.goldenport.cncf.action.ActionCall.Core): CatalogPort = port match {
    case managed: CallScopedCatalogPort => new CatalogPort {
      def loadC(start: SplitPhaseStartCommand) = managed.loadForCallC(start, core)
    }
    case existing => existing
  }

  def previewC(
    command: SplitPhasePreviewCommand,
    selectionPort: ProfileStartAdmission.SelectionPort,
    entryPort: SplitPhaseEntryAssessment.Port,
    proposalPort: SplitPhaseProposalAssessment.Port,
    catalogPort: CatalogPort,
    definition: DefinitionBinding,
    configuration: Configuration,
    instances: WorkflowInstancePersistence,
    admittedSemanticRecord: Option[InstanceRecord] = None
  ): Consequence[WorkflowInteraction[Nothing, String]] =
    if (command == null || command.startCommand == null ||
        command.startCommand.input == null ||
        command.startCommand.input.invocationMode != "PREVIEW" ||
        admittedSemanticRecord == null ||
        (command.expectedRevision != "2" &&
          !(command.expectedRevision == "3" && admittedSemanticRecord.nonEmpty)) ||
        command.idempotencyKey == null ||
        command.idempotencyKey.trim.isEmpty || proposalPort == null ||
        catalogPort == null || instances == null)
      Consequence.stateConflict("SplitPhase preview command is incomplete or not PREVIEW")
    else for {
      prepared <- SplitPhaseStartExecution.prepareC(command.startCommand,
        selectionPort, entryPort, definition, configuration)
      loaded <- instances.load(configuration, prepared.initial.identity)
      current <- loaded match {
        case Some(record) => admittedSemanticRecord match {
          case None => readyC(record, prepared.initial)
          case Some(admitted) => semanticReadyC(record, prepared.initial, admitted)
        }
        case None => Consequence.stateConflict("SplitPhase preview Start instance is missing")
      }
      _ <- if (SplitPhaseEntryLifecycle.permits("ProposalAssessment", "previewed", "Previewed"))
        Consequence.unit else Consequence.stateConflict("SplitPhase PREVIEW transition is not declared")
      catalog <- catalogPort.loadC(command.startCommand)
      _ <- if (catalog != null && catalog.repositoryRoot != null &&
          catalog.snapshotIdentity != null && catalog.snapshotIdentity.trim.nonEmpty &&
          catalog.enumerated != null && catalog.policy != null &&
          catalog.evidences != null && catalog.admitted != null) Consequence.unit
        else Consequence.stateConflict("SplitPhase preview catalog is incomplete")
      preview <- SplitPhasePreviewProjection.projectCatalogC(
        catalog.repositoryRoot, catalog.snapshotIdentity, prepared.assessment,
        catalog.enumerated, catalog.policy, catalog.evidences, proposalPort,
        catalog.admitted)
      payload = SplitPhasePreviewJson.encode(preview)
      entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1L), current.revision.next,
        Lifecycle.Completed, None, current.correlation,
        CausationReference(s"preview:${_sha256(_field(command.idempotencyKey) + _field(payload))}"),
        None, None, None)
      completed <- instances.append(configuration, current.identity,
        current.revision, entry)
      _ <- if (completed.history == current.history :+ entry &&
          completed.lifecycle == Lifecycle.Completed)
        Consequence.unit
        else Consequence.stateConflict("SplitPhase preview append returned a different history")
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, completed)
      terminal <- projectTerminalC(handle, completed,
        TypedValue("SplitPhasePreviewResult", payload),
        MinimalPresentation("Split Previewed", "Read-only Split Phase preview completed",
          nextAction = Some("Review preview; APPLY requires separate authorization")))
    } yield terminal

  /** Host-only handoff from the common completion's exact saved closing
    * record. Public preview commands cannot provide this record or authority.
    */
  private def semanticReadyC(record: InstanceRecord, initial: InstanceRecord,
    admitted: InstanceRecord): Consequence[InstanceRecord] =
    if (record == null || admitted == null || !SplitPhaseDefinitionBinding.sameRecord(record, admitted) ||
        record.identity != initial.identity || !SplitPhaseDefinitionBinding.sameDefinition(record.definition, initial.definition) ||
        record.correlation != initial.correlation || record.lifecycle != Lifecycle.Active ||
        record.revision != InstanceRevision(3L) || record.history == null || record.history.size != 3 ||
        record.currentProgression != Some(ProgressionReference("entry.ProposalAssessment")) ||
        record.suspension.nonEmpty || record.derivedCompositeOccurrence.nonEmpty ||
        record.committedPredecessor.nonEmpty ||
        record.history.head != HistoryEntry(HistorySequence(1L), InstanceRevision(1L),
          Lifecycle.Active, Some(ProgressionReference("entry.EntryAssessment")),
          initial.correlation, initial.causation, None, None, None) ||
        record.history(1).currentProgression != Some(ProgressionReference("entry.SemanticAssessment")) ||
        record.history(1).suspension.isEmpty ||
        !record.history.last.causation.value.startsWith("split-semantic-result:"))
      Consequence.stateConflict("SplitPhase semantic PREVIEW requires the exact saved admitted Result")
    else record.validateC

  private[admission] def readyC(record: InstanceRecord,
                      initial: InstanceRecord): Consequence[InstanceRecord] = {
    val first = HistoryEntry(HistorySequence(1L), InstanceRevision(1L),
      Lifecycle.Active, Some(ProgressionReference("entry.EntryAssessment")),
      initial.correlation, initial.causation, None, None, None)
    if (record == null || record.identity != initial.identity ||
        !SplitPhaseDefinitionBinding.sameDefinition(record.definition, initial.definition) ||
        record.correlation != initial.correlation ||
        record.derivedCompositeOccurrence.nonEmpty ||
        record.committedPredecessor.nonEmpty || record.suspension.nonEmpty ||
        record.lifecycle != Lifecycle.Active ||
        record.revision != InstanceRevision(2L) ||
        record.currentProgression != Some(ProgressionReference("entry.ProposalAssessment")) ||
        record.history == null || record.history.size != 2 ||
        record.history.head != first ||
        record.history(1).currentProgression !=
          Some(ProgressionReference("entry.ProposalAssessment")))
      Consequence.stateConflict("SplitPhase terminal requires the matching proposal-assessed instance")
    else record.validateC
  }

  private def _field(value: String): String = s"${value.length}:$value"
  private def _sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString
}

/** Deterministic, fully projected result; no proposed document is written. */
private[smworkflow] object SplitPhasePreviewJson {
  def encode(preview: SplitPhasePreviewProjection.Preview): String = {
    val admission = preview.admission
    Json.obj(
      "schemaVersion" -> Json.fromString("sm-workflow.split-phase-preview-result.v1"),
      "admission" -> Json.obj(
        "consideredDigest" -> Json.fromString(admission.consideredDigest),
        "candidateDigest" -> Json.fromString(admission.candidateDigest),
        "proposalDigest" -> Json.fromString(admission.proposalDigest),
        "proposalEvidenceDigest" -> Json.fromString(admission.proposalEvidenceDigest),
        "objectiveStage" -> Json.fromString(admission.objectiveStage),
        "sourceSnapshotDigest" -> Json.fromString(admission.sourceSnapshotDigest),
        "partitionPolicyDigest" -> Json.fromString(admission.partitionPolicyDigest),
        "costDocumentSha256" -> _optional(admission.costDocumentSha256),
        "overheadDocumentSha256" -> _optional(admission.overheadDocumentSha256)
      ),
      "sourcePhaseIdentity" -> Json.fromString(preview.sourcePhaseIdentity),
      "proposalIdentity" -> Json.fromString(preview.proposalIdentity),
      "validationTiming" -> Json.fromString(preview.validationTiming.toString),
      "validationMethod" -> Json.fromString(preview.validationMethod),
      "validationDriver" -> Json.fromString(preview.validationDriver),
      "finalValidationOwner" -> Json.fromString(preview.finalValidationOwner),
      "children" -> Json.fromValues(preview.children.map(child => Json.obj(
        "identity" -> Json.fromString(child.identity),
        "orderedWorkItems" -> Json.fromValues(child.orderedWorkItems.map(item => Json.obj(
          "identity" -> Json.fromString(item.identity),
          "closureCriteria" -> _strings(item.closureCriteria),
          "prerequisiteItems" -> _strings(item.prerequisiteItems),
          "authorityReference" -> Json.fromString(item.authorityReference)
        ))),
        "expectedMinutes" -> Json.fromInt(child.expectedMinutes),
        "closureCriteria" -> _strings(child.closureCriteria),
        "validationOwner" -> Json.fromString(child.validationOwner),
        "dependencies" -> _strings(child.dependencies),
        "incomingHandoffIds" -> _strings(child.incomingHandoffIds)
      ))),
      "handoffs" -> Json.fromValues(preview.handoffs.map(handoff => Json.obj(
        "identity" -> Json.fromString(handoff.identity),
        "producingChild" -> Json.fromString(handoff.producingChild),
        "receivingChild" -> Json.fromString(handoff.receivingChild),
        "kind" -> Json.fromString(handoff.kind),
        "input" -> Json.fromString(handoff.input),
        "action" -> Json.fromString(handoff.action),
        "output" -> Json.fromString(handoff.output),
        "owner" -> Json.fromString(handoff.owner),
        "invalidationReason" -> Json.fromString(handoff.invalidationReason),
        "authorityReference" -> Json.fromString(handoff.authorityReference)
      )))
    ).noSpaces
  }

  private def _optional(value: Option[String]): Json =
    value.fold(Json.Null)(Json.fromString)
  private def _strings(values: Vector[String]): Json =
    Json.fromValues(values.map(Json.fromString))
}
