package org.simplemodeling.textus.smworkflow.admission

import io.circe.syntax.*
import java.net.URLEncoder
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation
import BoundedWorkRuntime.Payload
import StandaloneFixExecution as S
import DelegateTaskNativeRun as N
import DelegateTaskScopeCorrection.{Checkpoint, given}

/** Read-only reconstruction of omitted derived references. The immutable
  * selected State and its actual successful/failed/unknown effects are never
  * rewritten. Only this distinct native projection material carries the view.
  * @since   Nov. 19, 2026
  * @version Nov. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object DelegateTaskDeliveryProjection {
  val materialKind = "bounded-derived-delivery"
  private def invalid[A](message: String): Consequence[A] =
    Consequence.stateConflict("DelegateTask delivery projection: " + message)

  /** Identifies the managed receipt, not a new Human authorization. The native
    * owner verifies its exact target and inventory before this is constructed. */
  private[admission] def receiptReference(checkpoint: Checkpoint, nextRevision: String): ContextReference = {
    val target = checkpoint.target
    val identity = Vector(target.sessionId, target.oldContinuationId)
      .map(URLEncoder.encode(_, "UTF-8")).mkString(":")
    ContextReference("sm-workflow-supersession:" + identity, nextRevision)
  }

  private[admission] def correctedC(input: S.Input, checkpoint: Checkpoint): Consequence[S.Input] =
    DelegateTaskScopeCorrection.correctedInputC(input, checkpoint.target.additionalOwnedPaths,
      checkpoint.target.submissionConditions).map { selected =>
      if (checkpoint.target.additionalOwnedPaths.isEmpty || selected.sourceReferences.contains(checkpoint.reference)) selected
      else selected.copy(sourceReferences = selected.sourceReferences :+ checkpoint.reference)
    }

  /** This pure reconciliation is called only after every checkpoint has been
    * read and authenticated through the native supersession owner below.
    */
  private[admission] def reconcileC(actual: S.Input, checkpoints: Vector[Checkpoint]): Consequence[S.Input] = {
    def restore(input: S.Input, references: Vector[ContextReference]): S.Input =
      input.copy(sourceReferences = references.foldLeft(input.sourceReferences) { (prior, reference) =>
        if (prior.contains(reference)) prior else prior :+ reference
      })
    checkpoints.headOption match {
      case None => S.inputC(actual).map(_ => actual)
      case Some(first) => for {
        accumulated <- checkpoints.foldLeft(Consequence.success(first.target.business.state.input -> Vector.empty[ContextReference])) {
          case (prior, checkpoint) => for {
            previous <- prior
            (expected, references) = previous
            _ <- if (restore(checkpoint.target.business.state.input, references) == expected)
              Consequence.unit else invalid("correction input does not continue the exact ordered admitted input")
            next <- correctedC(expected, checkpoint)
          } yield next -> (references ++ Option.when(checkpoint.target.additionalOwnedPaths.nonEmpty)(checkpoint.reference)).distinct
        }
        (expected, references) = accumulated
        _ <- if (restore(actual, references) == expected) Consequence.unit
          else invalid("delivery differs by more than missing genuine correction references")
        _ <- S.inputC(expected)
      } yield expected
    }
  }

  // REASON: the existing snapshot owner admits the actual bound Component
  // before invoking this internal projection; this is its established UoW core.
  @SuppressWarnings(Array("scalafix:NoOptionGet"))
  def stateC(storage: WorkflowComponentStorage, current: InstanceRecord, source: InstanceRecord,
    payload: Payload, core: ActionCall.Core): Consequence[S.State] = for {
    _ <- current.validateC
    _ <- source.validateC
    _ <- if (current.definition == source.definition && current.identity == source.identity &&
      source.revision.value <= current.revision.value && source.history == current.history.take(source.history.size) &&
      payload.state.input.delegation.exists(_.role == "destination") &&
      Set("ReturnReady", "Returned")(payload.state.stage)) Consequence.unit
      else invalid("requires an actual same-owner completed destination delivery source prefix")
    _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, core.executionContext)
    adapter <- storage.bindWorkOrderSupersessionC(N.completionOperation, N.inputCodec, DelegateTaskScopeCorrection.checkpointCodec)
    issued <- storage.bindIssuedC(N.completionOperation, N.inputCodec)
    catalog <- WorkflowInstanceRecordJsonV1.catalogC(Vector(current.definition))
    entries = source.history.filter(_.causation.value.startsWith(DelegateTaskScopeCorrection.resumeCause))
    retainedCheckpoints <- entries.foldLeft(Consequence.success(Vector.empty[(Checkpoint, String)])) { (prior, entry) => for {
      previous <- prior
      old = entry.causation.value.stripPrefix(DelegateTaskScopeCorrection.resumeCause)
      retained <- adapter.loadRetainedC(org.goldenport.cncf.workflow.ContinuationIdentity(old), current.identity)(using core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(invalid("native scope supersession is absent")))
      receipt = retained.receipt
      saved = receipt.payload
      target <- DelegateTaskScopeCorrection.targetC(saved.target.asJson.noSpaces)
      frontier <- WorkflowInstanceRecordJsonV1.decodeC(target.frontier.noSpaces, catalog)
      actual <- issued.loadC(org.goldenport.cncf.workflow.ContinuationIdentity(old))(using core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(invalid("superseded original WorkOrder is absent")))
      wire <- WorkflowWorkOrderJsonV1.encodeC(actual, N.inputCodec).flatMap(raw =>
        io.circe.parser.parse(raw).fold(e => invalid(e.message), Consequence.success))
      work <- actual.current match {
        case order: WorkflowContinuation.WorkOrder[S.Work] if order.request.completionOperation == N.completionOperation =>
          order.request.input.map(value => Consequence.success(value.value)).getOrElse(invalid("original Work input is absent"))
        case _ => invalid("scope source is not an original DelegateTask WorkOrder")
      }
      revision <- work.sourceRevision.toLongOption.map(Consequence.success).getOrElse(invalid("invalid source revision"))
      business <- N.loadPayloadC(storage, current.identity.value, revision, core)
      _ <- if (retained.current == current && receipt.identity.value == old &&
        receipt.nextRevision == entry.revision && receipt.sourceRevision == frontier.revision &&
        receipt.nextRevision == receipt.sourceRevision.next && frontier.revision.value == target.frontierRevision &&
        frontier.identity == current.identity && frontier.definition == current.definition &&
        frontier.history == source.history.take(frontier.history.size) &&
        target.componentInstance == core.component.get.core.instanceId.canonicalKey &&
        target.sessionId == current.identity.value && target.participant == payload.state.participant &&
        target.oldContinuationId == old && target.issued == wire && target.business.asJson == business.asJson &&
        work.session == target.sessionId && work.state.asJson == business.state.asJson &&
        Set("Implement", "Repair")(work.kind) && work.kind == business.state.stage &&
        business.root == payload.root && business.branch == payload.branch &&
        saved.reference.identity != null && saved.reference.identity.nonEmpty &&
        saved.reference.revision != null && saved.reference.revision.nonEmpty)
        Consequence.unit else invalid("scope receipt crosses its native owner, exact issue, source or adjacent history")
    } yield previous :+ (saved -> receipt.nextRevision.value.toString) }
    checkpoints = retainedCheckpoints.map(_._1)
    input <- reconcileC(payload.state.input, checkpoints)
    // A reused Human reference cannot establish an order-preserving extension
    // at the receiver. Add a distinct address of the confirmed native receipt.
    proofs = retainedCheckpoints.filter { case (c, _) => c.target.additionalOwnedPaths.nonEmpty &&
      c.target.business.state.input.sourceReferences.contains(c.reference) }.map { case (c, revision) =>
        receiptReference(c, revision)
      }
    _ <- if (proofs.distinct == proofs && !proofs.exists(input.sourceReferences.contains)) Consequence.unit
      else invalid("native receipt reference collides with retained source references")
    projected = input.copy(sourceReferences = proofs.foldLeft(input.sourceReferences) { (refs, proof) =>
      if (refs.contains(proof)) refs else refs :+ proof
    })
    _ <- S.inputC(projected)
  } yield payload.state.copy(input = projected)
}
