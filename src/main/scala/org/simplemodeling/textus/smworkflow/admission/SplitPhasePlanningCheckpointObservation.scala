package org.simplemodeling.textus.smworkflow.admission

import scala.util.control.NonFatal
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.{WorkflowMechanicalTransition as M}
import org.goldenport.cncf.workflow.{ContextBundle, ContextReference, ContextSnapshot, StateMachineRunIdentity, StateMachineRevision, StateMachineOperationIdentity}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhaseTypedStartPreparation as T, SplitPhaseExplicitPartitionContext as C,
  SplitPhasePlanningSnapshot as R, SplitPhaseExplicitProposalPrecheck as P,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPartitionSemanticJson as J}

/** Relation checks on inert complete data. Unit is not a native witness or an
  * executable plan; native receipt/prefix admission stays with CNCF.
  * @since   Oct.  2, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhasePlanningCheckpointObservation {
  final case class Original(context: C.Record, start: T.Record, facts: C.Facts)
  final case class Documents(proposal: P.ProposalRecord, ownership: B.Ownership, handoffs: B.HandoffPlan)
  final case class Data(index: K.ContextIndex, mapping: K.Record, original: Original,
    effective: C.Facts, documents: Vector[Documents])

  def verifyOriginalC(mapping: K.Record, original: Original, predecessor: InstanceRecord): Consequence[Unit] = guarded {
    for {
      _ <- verifyStartLineageC(original, predecessor)
      _ <- if (mapping.originalContext == original.context && mapping.originalStart == original.start &&
          original.context.owner.instanceRevision == predecessor.revision && mapping.predecessorRevision == predecessor.revision)
        Consequence.unit else invalid("original mapping/native predecessor revision differ")
    } yield ()
  }

  /** Shared raw Start ancestry, also for the later suspended AI boundary. This
    * does not construct a prefix instance, prepare a Start or admit execution.
    */
  def verifyStartLineageC(original: Original, predecessor: InstanceRecord): Consequence[Unit] = guarded {
    val context = original.context
    val start = original.start
    val handle = context.owner.handle
    val first = predecessor.history.headOption
    if (original.facts == context.facts &&
        start.configuration == context.owner.configuration && start.instanceIdentity == predecessor.identity &&
        handle.instanceIdentity == predecessor.identity && handle.componentIdentity == SmWorkflowComponent.componentId &&
        handle.workflowIdentity == predecessor.definition.workflowIdentity &&
        handle.workflowRevision == predecessor.definition.workflowRevision &&
        start.workflowIdentity == predecessor.definition.workflowIdentity && start.workflowRevision == predecessor.definition.workflowRevision &&
        context.owner.instanceRevision.value <= predecessor.revision.value &&
        predecessor.correlation == CorrelationReference(start.command.selectionIdentity) && first.exists(h =>
          h.sequence == HistorySequence(1) && h.revision == InstanceRevision(1) && h.lifecycle == Lifecycle.Active &&
          h.currentProgression == Some(ProgressionReference("entry.EntryAssessment")) &&
          h.correlation == CorrelationReference(start.command.selectionIdentity) &&
          h.causation == CausationReference(start.reference.identity) && h.derivedCompositeOccurrence.isEmpty &&
          h.committedPredecessor.isEmpty && h.suspension.isEmpty)) Consequence.unit
    else invalid("original Start/context/native predecessor differ")
  }

  def verifyMechanicalC(data: Data, receipt: M.Receipt[K.Record]): Consequence[Unit] = guarded {
    val mapping = data.mapping
    for {
      _ <- verifyOriginalC(mapping, data.original, receipt.current)
      _ <- if (data.index == K.index(mapping) && mapping.origin == K.Origin.Mechanical(receipt.reference) &&
          receipt.component == SmWorkflowComponent.componentId && receipt.operation == K.advanceOperation &&
          receipt.payload.typeIdentity == "SplitPhasePlanningCheckpoint" && receipt.payload.value == mapping &&
          receipt.next.revision == mapping.committedRevision && data.effective == mapping.snapshot.selection &&
          data.documents == builtDocuments(mapping.snapshot)) Consequence.unit
        else invalid("index/native mapping/effective facts or documents differ")
    } yield ()
  }

  /** Inert observation of an actually published first partition WorkOrder.
    * It compares raw unknown-work facts, not a reconstructed planning outcome.
    * Unit cannot create Pending, an issue plan, claim or completion authority.
    */
  def verifyPendingPartitionC(original: Original, current: InstanceRecord,
    issued: WorkflowInteraction[S.Request, Nothing]): Consequence[Unit] = guarded {
    val context = original.context
    val owner = context.owner
    val facts = original.facts
    val items = facts.boundary.boundary.items
    val edges = facts.boundary.handoffs.edges
    val byidentity = items.map(item => item.identity -> item).toMap
    val unknownitems = facts.partition.estimates.items.collect {
      case E.Item(identity, E.Duration.Unresolved(reason)) => S.NovelWork(identity, reason, byidentity(identity))
    }
    val unknownedges = edges.zipWithIndex.collect {
      case (edge, index) if edge.disposition == SplitPhasePartitionEnumeration.EdgeDisposition.Unresolved =>
        S.UnknownBoundary(edge, items(index), items(index + 1))
    }
    val bundle = ContextBundle("Assess only unresolved partition work",
      Vector(ContextReference(context.reference.identity, context.reference.revision)),
      Vector(ContextReference(owner.start.identity, owner.start.revision),
        ContextReference(facts.proposal.source.reference.identity, facts.proposal.source.reference.revision)),
      ContextSnapshot(current.definition.workflowRevision.value,
        modelRevision = Some(facts.proposal.source.reference.revision), evidenceRevision = Some(context.reference.revision)))
    for {
      _ <- verifyStartLineageC(original, current)
      _ <- _verify_start_selection_c(original)
      required <- SplitPhaseDefinitionBinding.partitionSemanticOperationC
      order <- issued.current match {
        case value: WorkflowContinuation.WorkOrder[?] if value.request != null && value.request.input.exists(input =>
            input != null && input.typeIdentity == J.requestCodec.typeIdentity && input.value.isInstanceOf[S.Request]) =>
          Consequence.success(value.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]])
        case _ => invalid("pending partition has no actual typed WorkOrder")
      }
      request = order.request
      input = request.input.get.value
      reply = input.reply
      references = Vector(reply.result.identity) ++ reply.resolutions.estimates.map(_.identity) ++ reply.resolutions.handoffs.map(_.identity)
      _ <- if (issued.handle == owner.handle && owner.completion == C.completionOperation &&
          request.runId == StateMachineRunIdentity(current.identity.value) && request.continuationId == owner.continuation &&
          request.expectedRevision == StateMachineRevision(current.revision.value.toString) &&
          request.requiredOperation == required.identity && request.operation == required.operation &&
          request.resultType == required.resultType && request.completion == required.metadata.completionContract &&
          request.evidence == required.metadata.evidenceContract && request.completionOperation == owner.completion &&
          request.context == bundle && order.requirement == owner.requirement && order.presentation == owner.presentation &&
          input.context == context.reference && input.source == facts.proposal.source.reference &&
          input.items == unknownitems && input.boundaries == unknownedges &&
          (unknownitems.nonEmpty || unknownedges.nonEmpty) &&
          reply.resolutions.estimates.isDefined == unknownitems.nonEmpty &&
          reply.resolutions.handoffs.isDefined == unknownedges.nonEmpty &&
          _reference(reply.result.identity, reply.result.revision) &&
          reply.resolutions.estimates.forall(r => _reference(r.identity, r.revision) &&
            r.identity != facts.partition.estimates.reference.identity) &&
          reply.resolutions.handoffs.forall(r => _reference(r.identity, r.revision) &&
            r.identity != facts.boundary.handoffs.reference.identity) && references.distinct == references)
        Consequence.unit else invalid("actual pending request differs from original work or reply roles")
      last = current.history.last
      prior = current.history.dropRight(1).last
      _ <- if (current.lifecycle == Lifecycle.Active && owner.instanceRevision == prior.revision &&
          current.revision == owner.instanceRevision.next && prior.suspension.isEmpty &&
          last.sequence == HistorySequence(prior.sequence.value + 1L) &&
          last.currentProgression == prior.currentProgression && last.correlation == prior.correlation &&
          last.causation == CausationReference(context.reference.identity) &&
          last.derivedCompositeOccurrence == prior.derivedCompositeOccurrence && last.committedPredecessor == prior.committedPredecessor &&
          current.suspension.exists(boundary => boundary.continuationIdentity.value == request.continuationId.value &&
            boundary.expectedRevision == current.revision && boundary.contextSnapshot.value == bundle.snapshot.workflowRevision &&
            boundary.completion.value == request.completion.identity && boundary.evidence.value == request.evidence.identity &&
            boundary.resumeCorrelation == current.correlation)) Consequence.unit
        else invalid("actual pending work is not the current adjacent original suspension")
    } yield ()
  }

  /** Compare independently retained data only; do not readmit a human Start. */
  def verifyInvocationC(original: Original): Consequence[Unit] = guarded {
    _verify_start_selection_c(original)
  }

  private def _verify_start_selection_c(original: Original): Consequence[Unit] = {
    val start = original.start
    val command = start.command
    val input = command.input
    val selection = start.selection
    val source = start.source
    for {
      selected <- T.selectedInputC(command)
      _ <- if (selection.selectionId == command.selectionIdentity &&
          selection.startOperation == StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase") &&
          selection.workflowIdentity == start.workflowIdentity && selection.workflowRevision == start.workflowRevision &&
          selection.participantIdentity == command.participantIdentity &&
          selection.origin == ProfileStartAdmission.SelectionOrigin.ExplicitHuman &&
          selection.idempotencyKey == command.idempotencyKey && selection.recommendationReference != null &&
          selection.recommendationReference.forall(value => value != null && value.trim.nonEmpty) &&
          selection.selectedInput.contains(TypedValue("SplitPhaseStartInput", selected)) &&
          original.context.owner.start == start.reference && original.facts.proposal.source == source &&
          start.proposal.forall(_ == original.facts.proposal.proposal) &&
          source.phase.value == input.sourcePhaseIdentity && source.authority.identity == input.sourceAuthorityReference &&
          input.sourceSnapshotReference.forall(_ == source.reference.identity) &&
          (input.proposalReference match {
            case None => start.proposal.isEmpty
            case Some(identity) => start.proposal.exists(proposal => proposal.reference.identity == identity &&
              proposal.source == source.reference && proposal.phase == source.phase && proposal.authority == source.authority)
          })) Consequence.unit
        else invalid("original Start command, human selection or context source/proposal differ")
    } yield ()
  }

  def builtDocuments(snapshot: R.Record): Vector[Documents] =
    snapshot.projection.toVector.flatMap(_.candidates).collect {
      case value: R.CandidateProjectionView.BuiltView => Documents(value.proposal, value.ownership, value.handoffs)
    }

  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("SplitPhase checkpoint observation " + reason)
  private def _reference(identity: String, revision: String): Boolean =
    Vector(identity, revision).forall(value => value != null && value.nonEmpty && value.length <= 1024 &&
      value.trim == value && !value.exists(c => Character.isISOControl(c)))
  private def guarded[A](body: => Consequence[A]): Consequence[A] = try body catch {
    case NonFatal(_) => invalid("complete data is absent or malformed")
  }
}
