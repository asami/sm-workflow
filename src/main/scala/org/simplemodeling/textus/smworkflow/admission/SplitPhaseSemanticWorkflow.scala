package org.simplemodeling.textus.smworkflow.admission

import domain.statemachine.SplitPhaseEntryLifecycle

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request as ProtocolRequest}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhasePreviewCommand, SplitPhaseProposalAssessmentCommand, SplitPhaseStartCommand}
import SplitPhaseSemanticInputs.{Intent, Admitted, Judgment, Request as SemanticRequest, Outcome as SemanticOutcome}

/** Bounded PREVIEW connection. Common persistence / claims / public completion
  * are CNCF-owned. Ordinary fixture construction retains its loose transaction;
  * the explicit completion-only construction uses canonical managed closing.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] final class SplitPhaseSemanticWorkflow private (
  p: SplitPhaseSemanticWorkflow.Ports, operation: StateMachineRequiredOperation,
  managedCompletion: Option[WorkflowManagedCompletion[Judgment]] = None,
  managedIssued: Option[WorkflowManagedIssuedWork[SemanticRequest]] = None
) {
  import SplitPhaseSemanticWorkflow.*

  private def prepareC(command: SplitPhaseProposalAssessmentCommand): Consequence[Retained] =
    prepareRetainedC(command, p.selections, p.entry, p.proposals, p.definition, p.configuration, operation)

  private def suspensionC(seed: Retained): Consequence[InstanceRecord] =
    (if (SplitPhaseEntryLifecycle.permits("EntryAssessment", "semantic-needed", "SemanticAssessment"))
      Consequence.unit else Consequence.stateConflict("SplitPhase semantic suspension transition is not declared"))
      .flatMap(_ => seed.active.appendC(InstanceRevision(1L), HistoryEntry(HistorySequence(2L),
      InstanceRevision(2L), Lifecycle.Active, Some(ProgressionReference("entry.SemanticAssessment")),
      seed.active.correlation, CausationReference(s"split-semantic:${seed.continuation.continuationId.value}"),
      None, None, Some(SuspensionBoundary(
        WorkflowInstancePersistence.ContinuationIdentity(seed.continuation.continuationId.value),
        InstanceRevision(2L), ContextSnapshotReference(seed.continuation.context.snapshot.workflowRevision),
        CompletionReference(operation.metadata.completionContract.identity),
        EvidenceReference(operation.metadata.evidenceContract.identity),
        CorrelationReference(s"semantic:${seed.continuation.continuationId.value}"))))))

  /** Retained input plus fresh host evidence, never a worker-selected source. */
  private def revalidateC(seed: Retained): Consequence[Unit] = for {
    fresh <- prepareC(seed.command)
    _ <- if (fresh.intent.request == seed.intent.request &&
        sameRecord(fresh.active, seed.active) && fresh.continuation == seed.continuation)
      Consequence.unit else Consequence.stateConflict("SplitPhase semantic source / policy / authority context changed")
  } yield ()

  /** Both paths must use the same explicit host boundary; a semantic binding
    * cannot silently switch the assembly's stores, selection or source ports.
    */
  def validateHostC(selections: ProfileStartAdmission.SelectionPort,
    entry: SplitPhaseEntryAssessment.Port, proposals: SplitPhaseProposalAssessment.Port,
    definition: DefinitionBinding, configuration: Configuration,
    instances: WorkflowInstancePersistence): Consequence[Unit] =
    if ((p.selections eq selections) && (p.entry eq entry) && (p.proposals eq proposals) &&
        (p.instances eq instances) && sameDefinition(p.definition, definition) &&
        p.configuration == configuration) Consequence.unit
    else Consequence.configurationInvalid("SplitPhase semantic and assessment host bindings differ")

  def assessC(command: SplitPhaseProposalAssessmentCommand,
    decisions: Option[SplitPhaseIdentityDecision.Port] = None): Consequence[String] =
    if (command == null || command.startCommand == null || command.expectedRevision != "1" ||
        command.idempotencyKey == null || command.idempotencyKey.trim.isEmpty ||
        decisions == null || decisions.exists(_ == null))
      Consequence.stateConflict("SplitPhase semantic assessment command is incomplete")
    else for {
      prepared <- SplitPhaseStartExecution.prepareC(command.startCommand, p.selections,
        p.entry, p.definition, p.configuration)
      outcome <- SplitPhaseProposalAssessment.assessC(prepared.assessment, p.proposals)
      wire <- outcome match {
        case _: SplitPhaseProposalAssessment.Outcome.NovelWorkRequired |
             _: SplitPhaseProposalAssessment.Outcome.SemanticBoundaryRequired
             if command.startCommand.input.invocationMode == "PREVIEW" => publishC(command)
        // Identity precheck precedes novel-work/boundary assessment. Never
        // dispatch AI to resolve a numbering/ownership authority conflict.
        case _ => SplitPhaseProposalAssessmentExecution.assessWireC(command, p.selections,
          p.entry, p.proposals, p.definition, p.configuration, p.instances, decisions)
      }
    } yield wire

  /** Ephemeral current-call read view; all durable runtime/authority ports remain shared. */
  def assessForCallC(command: SplitPhaseProposalAssessmentCommand,
    decisions: Option[SplitPhaseIdentityDecision.Port], core: org.goldenport.cncf.action.ActionCall.Core): Consequence[String] =
    if (core == null || core.executionContext == null)
      Consequence.stateConflict("SplitPhase semantic assessment requires its current Operation call")
    else forCall(core).assessC(command, decisions)

  private def forCall(core: org.goldenport.cncf.action.ActionCall.Core): SplitPhaseSemanticWorkflow =
    new SplitPhaseSemanticWorkflow(p.copy(
      selections = ProfileStartAdmission.forCall(p.selections, core),
      entry = SplitPhaseEntryAssessment.forCall(p.entry, core),
      proposals = SplitPhaseProposalAssessment.forCall(p.proposals, core),
      instances = CallScopedWorkflowInstances.forCall(p.instances, core),
      issued = managedIssued.map(_.issuedPersistence(using core.executionContext)).getOrElse(p.issued),
      intents = intentsForCall(p.intents, core),
      catalog = catalogForCall(p.catalog, core)), operation, managedCompletion, managedIssued)

  private def publishC(command: SplitPhaseProposalAssessmentCommand): Consequence[String] = for {
    fresh <- prepareC(command)
    retained <- p.intents.saveC(fresh.continuation.continuationId, fresh)
    _ <- if (retained != null && retained.command == fresh.command &&
        retained.intent.request == fresh.intent.request &&
        sameRecord(retained.active, fresh.active) && retained.continuation == fresh.continuation)
      Consequence.unit else Consequence.stateConflict("SplitPhase retained semantic intent differs")
    _ <- revalidateC(retained)
    expected <- suspensionC(retained)
    current <- requiredC(p.instances.load(p.configuration, retained.active.identity))
    _ <- if (sameRecord(current, retained.active) || sameRecord(current, expected)) Consequence.unit
      else Consequence.stateConflict("SplitPhase semantic request differs from saved Start / suspension")
    _ <- p.runtime.recoverClaimC(retained.continuation.continuationId) match {
      case Consequence.Success(claim) if claim.continuation == retained.continuation &&
          sameRecord(current, expected) => Consequence.unit
      case _ => for {
        uow <- Option(p.freshUnitOfWork()).map(Consequence.success).getOrElse(
          Consequence.stateConflict("SplitPhase semantic UnitOfWork is missing"))
        _ <- p.runtime.stageSuspensionAfterC(uow, retained.continuation, () =>
          (if (sameRecord(current, retained.active))
            p.instances.append(p.configuration, current.identity, current.revision, expected.history.last)
          else p.instances.load(p.configuration, current.identity).flatMap(requiredValueC))
          .flatMap(saved => if (sameRecord(saved, expected)) Consequence.unit
            else Consequence.stateConflict("SplitPhase semantic suspension append diverged")))
        _ <- uow.commit()
        _ <- p.runtime.claimC(retained.continuation.continuationId)
      } yield ()
    }
    saved <- requiredC(p.instances.load(p.configuration, expected.identity))
    _ <- if (sameRecord(saved, expected)) Consequence.unit
      else Consequence.stateConflict("SplitPhase semantic suspension is not saved")
    claim <- p.runtime.recoverClaimC(retained.continuation.continuationId)
    _ <- if (claim.continuation == retained.continuation) Consequence.unit
      else Consequence.stateConflict("SplitPhase semantic claim differs from retained intent")
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, saved)
    work <- WorkflowProtocolV1.issueRecoveredWorkOrderC(handle, retained.continuation.continuationId,
      Some(TypedValue(SplitPhaseSemanticJson.requestCodec.typeIdentity, retained.intent.request)),
      StateMachineOperationIdentity("SplitPhaseService", "submitSplitSemanticResult"),
      p.requirement, p.presentation, p.runtime, p.issued)
    wire <- WorkflowWorkOrderJsonV1.encodeC(work, SplitPhaseSemanticJson.requestCodec)
  } yield wire

  private final case class Selected(seed: Retained, outcome: SemanticOutcome,
    next: InstanceRecord, overlay: SplitPhaseProposalAssessment.Port)

  private def selectC(submitted: ContinuationResult[Judgment],
    current: InstanceRecord): Consequence[Selected] = for {
    issued <- requiredC(p.issued.loadC(submitted.continuationId))
    seed <- requiredC(p.intents.loadC(submitted.continuationId))
    _ <- revalidateC(seed)
    expected <- suspensionC(seed)
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current)
    _ <- if (sameRecord(current, expected) && issued.handle == handle && submitted.handle == handle)
      Consequence.unit else Consequence.stateConflict("SplitPhase semantic completion differs from saved suspension")
    _ <- issued.current match {
      case work: WorkflowContinuation.WorkOrder[SemanticRequest] if
          work.request.runId == seed.continuation.runId &&
          work.request.continuationId == seed.continuation.continuationId &&
          work.request.expectedRevision == seed.continuation.expectedRevision &&
          work.request.requiredOperation == operation.identity &&
          work.request.operation == operation.operation &&
          work.request.resultType == operation.resultType &&
          work.request.context == seed.continuation.context &&
          work.request.completion == operation.metadata.completionContract &&
          work.request.evidence == operation.metadata.evidenceContract &&
          work.request.input.contains(TypedValue(SplitPhaseSemanticJson.requestCodec.typeIdentity, seed.intent.request)) &&
          work.request.completionOperation == StateMachineOperationIdentity("SplitPhaseService", "submitSplitSemanticResult") &&
          work.requirement == p.requirement => Consequence.unit
      case _ => Consequence.stateConflict("SplitPhase issued semantic work differs from retained provenance")
    }
    // The common resume guard also checks these; admission must precede claims.
    _ <- if (submitted.runId == seed.continuation.runId &&
        submitted.expectedRevision == seed.continuation.expectedRevision &&
        submitted.contextSnapshot == seed.continuation.context.snapshot)
      Consequence.unit else Consequence.stateConflict("SplitPhase semantic Result headers / context differ")
    candidate <- SplitPhaseDefinitionBinding.candidateC
    outcome <- SplitPhaseSemanticInputs.admitC(seed.intent, submitted.result.value)
    _ <- {
      import CandidateAdmissionProducerAbi.*
      admitTypedJudgmentResultC(candidate.sidecar, TypedJudgmentResultV1(
        acceptedTypedJudgmentResultSchemaVersion,
        JudgmentResult(JudgmentActionIdentity("AssessNovelSplitWork"),
          AlternativeIdentity(submitted.result.value.decision), Rationale(submitted.result.value.rationale),
          Evidence(submitted.result.value.evidenceReferences.mkString(",")),
          EvidenceScope("frozen-unresolved-inputs"), EvidenceFreshness("exact-context"),
          EvidenceProvenance("semantic-result-receipt")),
        TypeIdentity("SplitPhaseSemanticResult"), submitted.resultReference))
    }
    overlay = overlayPort(seed, outcome)
    _ <- outcome match {
      case _: SemanticOutcome.Enriched =>
        (if (SplitPhaseEntryLifecycle.permits("SemanticAssessment", "inputs-admitted", "ProposalAssessment"))
          Consequence.unit else Consequence.stateConflict("SplitPhase semantic admission transition is not declared"))
          .flatMap(_ => SplitPhaseProposalAssessment.assessC(seed.prepared.assessment, overlay)).flatMap {
          case _: SplitPhaseProposalAssessment.Outcome.EvidenceReady => Consequence.unit
          case _ => Consequence.stateConflict("SplitPhase enriched Result failed deterministic reassessment")
        }
      case _: SemanticOutcome.DecisionNeeded => Consequence.unit
    }
    wire <- WorkflowResultJsonV1.encodeC(submitted, SplitPhaseSemanticJson.resultCodec)
    progression = outcome match {
      case _: SemanticOutcome.Enriched => "entry.ProposalAssessment"
      case _: SemanticOutcome.DecisionNeeded => "entry.SemanticAssessment"
    }
    next <- current.appendC(InstanceRevision(2L), HistoryEntry(HistorySequence(3L),
      InstanceRevision(3L), Lifecycle.Active, Some(ProgressionReference(progression)),
      current.correlation, CausationReference(s"split-semantic-result:${digest(Vector(wire))}"),
      None, None, None))
  } yield Selected(seed, outcome, next, overlay)

  private def overlayPort(seed: Retained, outcome: SemanticOutcome): SplitPhaseProposalAssessment.Port =
    new SplitPhaseProposalAssessment.Port {
      def loadInventoryC(id: String) = p.proposals.loadInventoryC(id)
      def loadDraftC(id: String) = p.proposals.loadDraftC(id)
      def loadSourceBoundaryC(id: String) = p.proposals.loadSourceBoundaryC(id)
      def loadValidationEvidenceC(id: String) = p.proposals.loadValidationEvidenceC(id).map(_.map { evidence =>
        outcome match {
          case SemanticOutcome.Enriched(inputs) if
              seed.prepared.assessment.proposalCandidate.exists(_.identity == id) =>
            evidence.copy(semanticInputs = Some(inputs))
          case _ => evidence
        }
      })
    }

  /** Decoder-only declaration; execution rebuilds application callbacks with
    * the current capsule while retaining the canonical completion engine.
    */
  def completionC: Consequence[spec.OperationDefinition] =
    completionBindingC.map(declared => CallScopedOperationBinding.wrap(declared,
      core => forCall(core).completionBindingC))

  private def completionBindingC: Consequence[spec.OperationDefinition] = {
    // The common completion compares selected records structurally. Rehydrate
    // only an exactly equivalent opaque ABI binding to this admitted binding;
    // preserve every other record fact and reject foreign bindings. This is a
    // local fixture adapter, not a generic persistence or protocol replacement.
    def canonicalC(record: InstanceRecord): Consequence[InstanceRecord] =
      if (record != null && sameDefinition(record.definition, p.definition))
        record.validateC.map(_.copy(definition = p.definition))
      else Consequence.stateConflict("SplitPhase completion persistence ABI binding differs")
    val completionInstances = new WorkflowInstancePersistence {
      def create(configuration: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
        canonicalC(record).flatMap(p.instances.create(configuration, _)).flatMap(canonicalC)
      def load(configuration: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
        p.instances.load(configuration, identity).flatMap {
          case Some(record) => canonicalC(record).map(Some(_))
          case None => Consequence.success(None)
        }
      def append(configuration: Configuration, identity: InstanceIdentity,
        expected: InstanceRevision, entry: HistoryEntry): Consequence[InstanceRecord] =
        p.instances.append(configuration, identity, expected, entry).flatMap(canonicalC)
    }
    val definition = SplitPhaseDefinitionBinding.providedDefinition
    val completion = definition.providedOperations.find(_.name == "submitSplitSemanticResult").get
    val codec = new WorkflowCompletionServiceOperation.Codec[Judgment] {
      val resultTypeIdentity = SplitPhaseSemanticJson.resultCodec.typeIdentity
      def decodeC(request: ProtocolRequest): Consequence[ContinuationResult[Judgment]] =
        WorkflowJsonCommandRequest.commandArguments(request) match {
          case List(Argument("arg1", wire: String, _)) =>
            WorkflowResultJsonV1.decodeC(wire, SplitPhaseSemanticJson.resultCodec)
          case _ => Consequence.stateConflict("SplitPhase semantic Result JSON is missing")
        }
      def encodeC(resume: ContinuationRuntime.Resume): Consequence[OperationResponse] =
        Consequence.stateConflict("SplitPhase semantic completion requires saved history")
      override def encodeAfterCommitC(resume: ContinuationRuntime.Resume,
        submitted: ContinuationResult[Judgment], completed: Option[InstanceRecord]) =
        requiredValueC(completed).flatMap { record => for {
          seed <- requiredC(p.intents.loadC(submitted.continuationId))
          suspended <- suspensionC(seed)
          selected <- selectC(submitted, suspended)
          _ <- if (sameRecord(record, selected.next)) Consequence.unit
            else Consequence.stateConflict("SplitPhase semantic completion append diverged")
          response <- selected.outcome match {
            case SemanticOutcome.DecisionNeeded(reason, _) => for {
              handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, record)
              wire <- WorkflowWaitJsonV1.encodeC(WorkflowInteraction(handle,
                WorkflowContinuation.Wait(StateMachineRevision("3"), submitted.contextSnapshot,
                  MinimalPresentation("Split decision needed", reason))))
            } yield OperationResponse.Scalar(wire)
            case SemanticOutcome.Enriched(inputs) => for {
              _ <- revalidateC(seed)
              catalog <- p.catalog.loadC(seed.command.startCommand, selected.overlay, inputs)
              terminal <- SplitPhasePreviewExecution.previewC(
                SplitPhasePreviewCommand(seed.command.startCommand, "3", s"semantic-preview:${inputs.receiptDigest}"),
                p.selections, p.entry, selected.overlay,
                new SplitPhasePreviewExecution.CatalogPort {
                  def loadC(start: SplitPhaseStartCommand) = Consequence.success(catalog)
                }, p.definition, p.configuration, p.instances, Some(record))
              wire <- WorkflowTerminalJsonV1.encodeC(terminal,
                SplitPhasePreviewServiceOperation.terminalCodec)
            } yield OperationResponse.Scalar(wire)
          }
        } yield response }
    }
    val closing = new WorkflowCompletionServiceOperation.ClosingProgram[Judgment] {
      def selectC(submitted: ContinuationResult[Judgment], current: InstanceRecord) =
        SplitPhaseSemanticWorkflow.this.selectC(submitted, current).map { selected =>
          val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
            ActionExecution.Completed(StateMachineOperationResult(
              StateMachineResultTypeReference(resultTypeIdentity), submitted.resultReference)))
          WorkflowCompletionServiceOperation.SelectedClosing(program, Some(selected.next))
        }
      private val resultTypeIdentity = "SplitPhaseSemanticResult"
    }
    WorkflowCompletionServiceOperation.bindC[SemanticRequest, Judgment](definition, completion,
      WorkflowJsonCommandRequest.definition, spec.ResponseDefinition.void, codec, p.runtime,
      p.issued, completionInstances, p.configuration, p.freshUnitOfWork, Some(closing), managedCompletion,
      descriptiveAttributes = SmWorkflowComponent.SplitPhaseServiceService.SubmitSplitSemanticResultOperation.specification.content.descriptiveAttributes)
  }
}

private[smworkflow] object SplitPhaseSemanticWorkflow {
  final case class Retained(command: SplitPhaseProposalAssessmentCommand,
    prepared: SplitPhaseStartExecution.Prepared, intent: Intent, active: InstanceRecord,
    continuation: Continuation)

  private def prepareRetainedC(command: SplitPhaseProposalAssessmentCommand,
    selections: ProfileStartAdmission.SelectionPort, entry: SplitPhaseEntryAssessment.Port,
    proposals: SplitPhaseProposalAssessment.Port, definition: DefinitionBinding,
    configuration: Configuration, operation: StateMachineRequiredOperation): Consequence[Retained] = for {
    prepared <- SplitPhaseStartExecution.prepareC(command.startCommand, selections, entry, definition, configuration)
    candidate <- prepared.assessment.proposalCandidate match {
      case Some(value) => Consequence.success(value)
      case _ => Consequence.stateConflict("SplitPhase semantic work has no selected proposal")
    }
    inventory <- requiredC(proposals.loadInventoryC(prepared.assessment.source.identity))
    draft <- requiredC(proposals.loadDraftC(candidate.identity))
    evidence <- requiredC(proposals.loadValidationEvidenceC(candidate.identity))
    boundary <- requiredC(proposals.loadSourceBoundaryC(prepared.assessment.source.identity))
    intent <- SplitPhaseSemanticInputs.prepareC(prepared.assessment, inventory, draft, evidence, boundary)
    retained <- assembleRetainedC(command, prepared, intent, definition, operation)
  } yield retained

  // Existing fixture preparation, extracted without an identity-algorithm change.
  private def assembleRetainedC(command: SplitPhaseProposalAssessmentCommand,
    prepared: SplitPhaseStartExecution.Prepared, intent: Intent, definition: DefinitionBinding,
    operation: StateMachineRequiredOperation): Consequence[Retained] = for {
    active <- prepared.initial.appendC(initialRevision, HistoryEntry(HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
      Some(ProgressionReference("entry.EntryAssessment")), prepared.initial.correlation,
      prepared.initial.causation, None, None, None))
    id = org.goldenport.cncf.workflow.ContinuationIdentity(
      s"${active.identity.value}:2:${digest(Vector(command.idempotencyKey, intent.request.contextDigest))}:split-semantic")
    context = ContextBundle("Assess only unresolved SplitPhase inputs", Vector.empty, Vector.empty,
      ContextSnapshot(definition.workflowRevision.value, modelRevision = Some(prepared.assessment.source.revision),
        evidenceRevision = Some(intent.request.contextDigest)))
    continuation = Continuation(StateMachineRunIdentity(active.identity.value), id, StateMachineRevision("2"), operation, context)
  } yield Retained(command, prepared, intent, active, continuation)

  /** Re-admits the original command under current host readers. Stored command
    * and baseline remain data; the caller must separately match issued work.
    */
  def recoverRetainedForCallC(command: SplitPhaseProposalAssessmentCommand,
    stored: SplitPhaseSemanticInputRecordJsonV1.InputRecord, inputs: SplitPhaseSemanticInputDataStorePort,
    selections: ProfileStartAdmission.CallScopedSelectionPort, entry: SplitPhaseEntryAssessment.CallScopedPort,
    proposals: SplitPhaseProposalAssessment.CallScopedPort, definition: DefinitionBinding,
    configuration: Configuration, core: org.goldenport.cncf.action.ActionCall.Core): Consequence[Retained] =
    if (core == null || core.executionContext == null || command == null || command.startCommand == null ||
        command.expectedRevision != "1" || command.startCommand.input == null ||
        command.startCommand.input.invocationMode != "PREVIEW" || stored == null || inputs == null ||
        selections == null || entry == null || proposals == null || definition == null || configuration == null)
      Consequence.stateConflict("SplitPhase original semantic provenance is incomplete")
    else for {
      prepared <- SplitPhaseStartExecution.prepareC(command.startCommand,
        ProfileStartAdmission.forCall(selections, core), SplitPhaseEntryAssessment.forCall(entry, core), definition, configuration)
      _ <- if (prepared.assessment == stored.facts.assessment) Consequence.unit
        else Consequence.stateConflict("SplitPhase original assessment differs from stored baseline")
      intent <- inputs.reconstructExpectedForCallC(stored, core, entry, proposals)
      operation <- SplitPhaseDefinitionBinding.semanticOperationC
      retained <- assembleRetainedC(command, prepared, intent, definition, operation)
      _ <- if (stored.reference == retained.continuation.continuationId.value &&
          stored.continuationReference == retained.continuation.continuationId.value) Consequence.unit
        else Consequence.stateConflict("SplitPhase input record crosses the original Continuation")
    } yield retained
  trait IntentPort {
    def saveC(id: org.goldenport.cncf.workflow.ContinuationIdentity, value: Retained): Consequence[Retained]
    def loadC(id: org.goldenport.cncf.workflow.ContinuationIdentity): Consequence[Option[Retained]]
  }
  trait CallScopedIntentPort extends IntentPort {
    final def saveC(id: org.goldenport.cncf.workflow.ContinuationIdentity, value: Retained): Consequence[Retained] =
      Consequence.stateConflict("SplitPhase semantic intent requires its current Operation call")
    final def loadC(id: org.goldenport.cncf.workflow.ContinuationIdentity): Consequence[Option[Retained]] =
      Consequence.stateConflict("SplitPhase semantic intent requires its current Operation call")
    def saveForCallC(id: org.goldenport.cncf.workflow.ContinuationIdentity, value: Retained,
      core: org.goldenport.cncf.action.ActionCall.Core): Consequence[Retained]
    def loadForCallC(id: org.goldenport.cncf.workflow.ContinuationIdentity,
      core: org.goldenport.cncf.action.ActionCall.Core): Consequence[Option[Retained]]
  }
  trait CatalogPort {
    def loadC(start: SplitPhaseStartCommand, proposals: SplitPhaseProposalAssessment.Port,
      inputs: Admitted): Consequence[SplitPhasePreviewExecution.Catalog]
  }
  trait CallScopedCatalogPort extends CatalogPort {
    final def loadC(start: SplitPhaseStartCommand, proposals: SplitPhaseProposalAssessment.Port,
      inputs: Admitted): Consequence[SplitPhasePreviewExecution.Catalog] =
      Consequence.stateConflict("SplitPhase semantic catalog requires its current Operation call")
    def loadForCallC(start: SplitPhaseStartCommand, proposals: SplitPhaseProposalAssessment.Port,
      inputs: Admitted, core: org.goldenport.cncf.action.ActionCall.Core): Consequence[SplitPhasePreviewExecution.Catalog]
  }

  private def intentsForCall(port: IntentPort, core: org.goldenport.cncf.action.ActionCall.Core): IntentPort = port match {
    case managed: CallScopedIntentPort => new IntentPort {
      def saveC(id: org.goldenport.cncf.workflow.ContinuationIdentity, value: Retained) = managed.saveForCallC(id, value, core)
      def loadC(id: org.goldenport.cncf.workflow.ContinuationIdentity) = managed.loadForCallC(id, core)
    }
    case existing => existing
  }
  private def catalogForCall(port: CatalogPort, core: org.goldenport.cncf.action.ActionCall.Core): CatalogPort = port match {
    case managed: CallScopedCatalogPort => new CatalogPort {
      def loadC(start: SplitPhaseStartCommand, proposals: SplitPhaseProposalAssessment.Port, inputs: Admitted) =
        managed.loadForCallC(start, proposals, inputs, core)
    }
    case existing => existing
  }
  final case class Ports(selections: ProfileStartAdmission.SelectionPort,
    entry: SplitPhaseEntryAssessment.Port, proposals: SplitPhaseProposalAssessment.Port,
    definition: DefinitionBinding, configuration: Configuration,
    instances: WorkflowInstancePersistence, runtime: ContinuationRuntime,
    issued: IssuedWorkOrderPersistence[SemanticRequest], intents: IntentPort, catalog: CatalogPort,
    requirement: ExecutionRequirement, presentation: MinimalPresentation,
    freshUnitOfWork: () => UnitOfWork)

  def bindC(p: Ports): Consequence[SplitPhaseSemanticWorkflow] =
    for {
      _ <- admitPortsC(p, false)
      declared <- SplitPhaseDefinitionBinding.bindC
      operation <- SplitPhaseDefinitionBinding.semanticOperationC
      _ <- if (sameDefinition(p.definition, declared)) Consequence.unit
        else Consequence.configurationInvalid("SplitPhase semantic binding differs from generated declarations")
    } yield new SplitPhaseSemanticWorkflow(p, operation)

  /** Returns only completion: this construction cannot publish through the
    * loose fixture runtime or manufacture a Start / claim.
    */
  def bindManagedCompletionC(p: Ports, catalog: SplitPhaseRuntimeCatalog.Catalog,
    storage: SplitPhaseManagedSemanticStorageAssembly.Binding): Consequence[spec.OperationDefinition] =
    for {
      _ <- admitPortsC(p, true)
      _ <- if (catalog != null && storage != null && storage.persistence != null &&
          storage.issued != null && storage.completion != null &&
          sameDefinition(p.definition, catalog.definition) &&
          sameDefinition(p.definition, storage.persistence.definition) &&
          p.configuration == storage.persistence.configuration && (p.instances eq storage.persistence.instances) &&
          p.runtime == ContinuationRuntime.empty &&
          p.selections.isInstanceOf[ProfileStartAdmission.CallScopedSelectionPort] &&
          p.entry.isInstanceOf[SplitPhaseEntryAssessment.CallScopedPort] &&
          p.proposals.isInstanceOf[SplitPhaseProposalAssessment.CallScopedPort] &&
          p.intents.isInstanceOf[CallScopedIntentPort] && p.catalog.isInstanceOf[CallScopedCatalogPort])
        Consequence.unit else Consequence.configurationInvalid("SplitPhase managed completion host ports differ")
      operation <- SplitPhaseDefinitionBinding.semanticOperationC
      bound <- new SplitPhaseSemanticWorkflow(p, operation, Some(storage.completion), Some(storage.issued)).completionC
    } yield bound

  private def admitPortsC(p: Ports, managed: Boolean): Consequence[Unit] =
    if (p == null || p.selections == null || p.entry == null || p.proposals == null ||
        p.definition == null || p.configuration == null || p.instances == null || p.runtime == null ||
        (!managed && p.runtime == ContinuationRuntime.empty) || p.issued == null || p.intents == null ||
        p.catalog == null || p.freshUnitOfWork == null || p.requirement == null || p.presentation == null ||
        p.requirement.capabilities != Vector(CapabilityRequirement("assess-novel-split-capability")) ||
        !p.requirement.reviewRequired)
      Consequence.configurationInvalid("SplitPhase semantic fixture ports are incomplete")
    else for {
      _ <- p.definition.validateC
      _ <- p.configuration.validateC
      _ <- p.requirement.validateC
      _ <- p.presentation.validateC
    } yield ()

  private def requiredValueC[A](value: Option[A]): Consequence[A] = value match {
    case Some(result) if result != null => Consequence.success(result)
    case _ => Consequence.stateConflict("SplitPhase required host evidence / persistence is missing")
  }
  private def requiredC[A](value: Consequence[Option[A]]): Consequence[A] = value.flatMap(requiredValueC)
  private def digest(values: Vector[String]): String = MessageDigest.getInstance("SHA-256")
    .digest(values.map(v => s"${v.length}:$v").mkString.getBytes(StandardCharsets.UTF_8))
    .map(byte => f"${byte & 0xff}%02x").mkString
  private def sameDefinition(left: DefinitionBinding, right: DefinitionBinding): Boolean =
    SplitPhaseDefinitionBinding.sameDefinition(left, right)
  private def sameRecord(left: InstanceRecord, right: InstanceRecord): Boolean =
    SplitPhaseDefinitionBinding.sameRecord(left, right)
}
