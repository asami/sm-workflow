package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import GoalPhaseNativeRun as F
import GoalPhaseNativeWork as W
import GoalPhaseOrderedProgression as O
import GoalPhaseDependencyScope as D
import GoalPhaseDependencyValidation as V
import GoalPhaseDependencyValidation.given

/** A native inventory receipt inherits one actual Human boundary. It never
  * creates another answer, effect grant or dependency validation receipt.
  * @since   Oct. 26, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseDependencyInventoryContinuation {
  val schema = "sm-workflow.dependency-inventory-continuation.v1"
  val causation = "ordered-dependency-inventory:continued"
  private val _namespace = ":ordered-dependency-inventory:"

  sealed trait Assessment
  final case class WithinBoundary(approval: V.Approval) extends Assessment
  final case class NeedsHuman(reason: String) extends Assessment

  /** The supplied vector is the native owner's current Step projection, never
    * a submitted semantic approval claim. A missing reference is invalid input.
    */
  def assessC(step: String, proposed: D.Proposal, approvals: Vector[V.Approval]): Consequence[Assessment] = for {
    _ <- D.validateProposalC(proposed)
    _ <- _check(_text(step) && approvals != null && approvals.forall(V.approvalValid), "approval projection is invalid")
    update <- proposed.scopeUpdate.map(Consequence.success).getOrElse(F.invalid("inventory update has no approval link"))
    approved <- approvals.filter(_.decision == update.approval) match {
      case Vector(value) if value.step == step => Consequence.success(value)
      case _ => F.invalid("inventory approval reference is missing, foreign, stale or ambiguous")
    }
    mismatches = Vector(
      Option.when(approved.proposal.requester != proposed.requester)("requester"),
      Option.when(approved.proposal.dependency != proposed.dependency)("dependency"),
      Option.when(approved.proposal.usageContract != proposed.usageContract)("usageContract"),
      Option.when(approved.proposal.acceptance != proposed.acceptance)("acceptance"),
      Option.when(approved.proposal.dependentWork != proposed.dependentWork)("dependentWork"),
      Option.when(approved.proposal.authorizationBoundary.isEmpty)("legacyAuthorizationBoundary"),
      Option.when(approved.proposal.authorizationBoundary.map(_.purpose) != proposed.authorizationBoundary.map(_.purpose))("purpose"),
      Option.when(approved.proposal.authorizationBoundary.map(_.permittedOperations) != proposed.authorizationBoundary.map(_.permittedOperations))("permittedOperations"),
      Option.when(approved.proposal.authorizationBoundary.map(_.excludedPaths) != proposed.authorizationBoundary.map(_.excludedPaths))("excludedPaths"),
      Option.when(approved.proposal.authorizationBoundary.map(_.exactPaths) != proposed.authorizationBoundary.map(_.exactPaths))("exactPaths")
    ).flatten
  } yield if (mismatches.isEmpty) WithinBoundary(approved) else NeedsHuman(
    "Inventory boundary mismatch: " + mismatches.mkString(", ") +
      ". Complete supplied reason and necessity rationale remain in requestingResult.dependencyProposal.reason and requestingResult.dependencyProposal.scopeUpdate.rationale.")

  private[admission] def assessAssignmentC(input: W.Request, proposed: D.Proposal,
    approvals: Vector[V.Approval], decisions: Vector[W.DecisionContext]): Consequence[Assessment] = for {
    _ <- D.validateProposalC(proposed)
    original <- Option(input).flatMap(_.decisionContext).filter(context =>
      context.choice == "ApproveDependencyScope" && context.decisionReference == proposed.scopeUpdate.map(_.approval))
      .map(Consequence.success).getOrElse(F.invalid("inventory approval link is stale for this actual issued assignment"))
    _ <- _check(decisions != null && decisions.count(_ == original) == 1,
      "inventory assignment direction differs from its actual reconstructed Human or mechanical receipt")
    assessment <- assessC(input.stepIdentity.getOrElse(""), proposed, approvals)
    effective = original.inventoryContinuation.map(_.proposal).orElse(original.requestingResult.dependencyProposal)
    _ <- _check(approvals.filter(_.decision == proposed.scopeUpdate.get.approval) match {
      case Vector(approved) => effective.contains(approved.proposal)
      case _ => false
    }, "inventory assignment changes its exact effective approval projection")
  } yield assessment

  /** The owner passes an actual issued Request. Inventory changes may select
    * newly registered literal paths/revision, never a different fixed source.
    */
  private[admission] def bindingC(input: W.Request, configured: Option[V.Binding],
    standing: Option[V.Admission]): Consequence[Option[V.Binding]] =
    if (input == null || input.decisionContext == null || configured == null || standing == null ||
        input.decisionContext.exists(context => context == null || context.inventoryContinuation == null))
      F.invalid("inventory registration contains null native material")
    else if (input.decisionContext.flatMap(_.inventoryContinuation).isEmpty)
      Consequence.success(standing.map(_.binding).orElse(configured))
    else for {
      inventory <- input.decisionContext.flatMap(_.inventoryContinuation).map(Consequence.success)
        .getOrElse(F.invalid("inventory registration has no actual issued context"))
      selected <- configured.filter(V.bindingValid).map(Consequence.success)
        .getOrElse(F.invalid("inventory registration conflict: current concrete dependency Binding is absent or invalid"))
      _ <- _check(input.stepIdentity.contains(selected.step) && selected.requester == inventory.proposal.requester &&
        selected.dependency == inventory.proposal.dependency && standing.forall(previous =>
          V.admissionValid(previous) && selected.copy(revision = previous.binding.revision, paths = previous.binding.paths) == previous.binding),
        "inventory source conflict: fixed Binding identity, Step, Components, root, branch, base, adoption or checks changed; use the separate dependency source revision route")
    } yield Some(selected)

  final case class InventoryContext(receipt: ContextReference, approval: ContextReference, proposal: D.Proposal)
  object InventoryContext {
    given Encoder[InventoryContext] = deriveEncoder
    private val _decoder: Decoder[InventoryContext] = deriveDecoder
    given Decoder[InventoryContext] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (contextValid(value) && cursor.value == value.asJson) Right(value)
      else Left(DecodingFailure("Inventory continuation requires exact non-null fields", cursor.history))))
  }
  def contextValid(value: InventoryContext): Boolean = value != null && _reference(value.receipt) &&
    _reference(value.approval) && D.validateOptionC(Some(value.proposal)).isSuccess &&
    value.proposal.scopeUpdate.exists(_.approval == value.approval)

  private[admission] final class Source private[GoalPhaseDependencyInventoryContinuation](
    val from: InstanceRecord, val controller: O.State, val input: W.Request,
    val result: ContinuationResult[W.Result], val approved: V.Approval,
    val original: W.DecisionContext, val proposed: D.Proposal) {
    def reference: WorkflowMechanicalTransition.Reference =
      WorkflowMechanicalTransition.Reference(from.identity.value + _namespace + from.revision.value, "1")
  }

  /** Only the native Cursor calls this selector after reconstructing genuine
    * semantic completions and Human approvals. Ordinary proposals select none.
    */
  private[admission] def selectC(from: InstanceRecord, controller: O.State,
    lastresult: Option[(W.Request, ContinuationResult[W.Result])], approvals: Vector[V.Approval],
    decisions: Vector[W.DecisionContext]): Consequence[Option[Source]] = {
    if (from == null || controller == null || lastresult == null || approvals == null || decisions == null ||
        lastresult.exists(pair => pair == null || pair._1 == null || pair._2 == null || pair._2.result == null ||
          pair._2.result.value == null || pair._2.result.value.dependencyProposal == null))
      return F.invalid("inventory source contains null native material")
    val proposal = lastresult.flatMap(_._2.result.value.dependencyProposal)
    if (proposal.flatMap(_.scopeUpdate).isEmpty) Consequence.success(None)
    else for {
      _ <- _check(controller != null && controller.stage == O.Stage.Decision &&
        controller.decisionSource.exists(Set(O.Stage.Implementation, O.Stage.Repair)) &&
        controller.pendingRepair.isEmpty && !controller.documentDecision && !controller.policyRepairPending &&
        controller.closureIntent.isEmpty && controller.cursor < controller.plan.steps.size &&
        controller.generation < Long.MaxValue, "inventory update is not an open implementation or repair Step Decision")
      _ <- from.validateC
      _ <- _check(from.lifecycle == Lifecycle.Active && from.suspension.isEmpty &&
        from.currentProgression.contains(ProgressionReference("phase.Decision")), "inventory source is suspended or inactive")
      (input, result) = lastresult.get
      proposed = proposal.get
      _ <- W.admitSemanticResultC(input, result.result.value)
      _ <- _check(W.inputCodec.decode(W.inputCodec.encode(input)).contains(input) &&
        W.resultCodec.decode(W.resultCodec.encode(result.result.value)).contains(result.result.value) &&
        result.result.typeIdentity == W.resultCodec.typeIdentity && result.result.value.outcome == "decision-needed" &&
        input.instanceIdentity == from.identity.value && input.selectedPlan == controller.plan &&
        input.originalBase == controller.base && input.completed == controller.completed &&
        input.generation == controller.generation && input.stepIdentity.contains(controller.plan.steps(controller.cursor).identity) &&
        ((input.kind == "ImplementStep" && controller.decisionSource.contains(O.Stage.Implementation)) ||
          (input.kind == "RepairStep" && controller.decisionSource.contains(O.Stage.Repair))),
        "inventory update crosses its actual semantic kind, Session, Plan, Step, base or generation")
      revision <- input.sourceRevision.toLongOption.filter(n => n > 0 && n.toString == input.sourceRevision)
        .map(n => Consequence.success(InstanceRevision(n))).getOrElse(F.invalid("inventory work source revision is invalid"))
      worksource <- GoalPhaseNativeHistory.prefixC(from, revision)
      pending <- GoalPhaseNativeHistory.prefixC(from, revision.next)
      completed <- GoalPhaseNativeHistory.completionC(from, pending, result.resultReference, "Decision")
      _ <- _check(completed == from && worksource.suspension.isEmpty &&
        worksource.currentProgression.contains(ProgressionReference("phase." + controller.decisionSource.get.toString)) &&
        pending.suspension.exists(_.continuationIdentity.value == result.continuationId.value),
        "inventory source is not the actual complete adjacent semantic result prefix")
      assessment <- assessAssignmentC(input, proposed, approvals, decisions)
      selected <- assessment match {
        case NeedsHuman(_) => Consequence.success(Option.empty[Source])
        case WithinBoundary(approved) => for {
          original <- input.decisionContext.map(Consequence.success)
            .getOrElse(F.invalid("inventory approval has no actual source Request DecisionContext"))
        } yield Some(new Source(from, controller, input, result, approved, original, proposed))
      }
    } yield selected
  }

  final case class Payload(sourceRevision: String, plan: ContextReference, step: String, generation: Long,
    repairProgress: GoalPhaseRepairProgress.Snapshot, base: O.Snapshot, completed: Vector[O.ClosedStep],
    requestingResult: ContextReference, approval: V.Approval, originalResult: W.Result,
    choice: String, rationale: String, proposed: D.Proposal)
  object Payload {
    given Encoder[Payload] = deriveEncoder
    private val _decoder: Decoder[Payload] = deriveDecoder
    given Decoder[Payload] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (_payload_valid(value) && cursor.value == value.asJson) Right(value)
      else Left(DecodingFailure("Inventory receipt requires exact closed non-null payload fields", cursor.history))))
  }
  val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseDependencyInventoryContinuationReceipt"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> schema.asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("inventory receipt is absent")
      _ <- Either.cond(wire.asObject.exists(_.keys.toSet == Set("schemaVersion", "payload")) &&
        wire.hcursor.get[String]("schemaVersion").contains(schema), (), "inventory receipt schema or envelope differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
    } yield payload
  }
  private[admission] final case class Saved(record: InstanceRecord, payload: Payload, controller: O.State,
    context: W.DecisionContext) {
    def approval: V.Approval = payload.approval.copy(proposal = payload.proposed)
  }
  private[admission] def payload(source: Source): Payload = Payload(source.from.revision.value.toString,
    ContextReference(source.controller.plan.identity, source.controller.plan.revision), source.input.stepIdentity.get,
    source.controller.generation, GoalPhaseRepairProgress.snapshot(source.controller), source.controller.base,
    source.controller.completed, source.result.resultReference,
    source.approved.copy(proposal = source.original.requestingResult.dependencyProposal.get), source.original.requestingResult,
    source.original.choice, source.original.rationale, source.proposed)

  private[admission] def admittedStateC(source: Source, value: Payload): Consequence[O.State] = for {
    _ <- _check(_payload_valid(value) && value == payload(source), "inventory receipt changes its native source or original approval")
    state <- source.controller.acceptC(O.DecisionResumptionAdmitted(source.reference.identity))
  } yield state
  private def _saved(source: Source, record: InstanceRecord, value: Payload, state: O.State): Saved =
    Saved(record, value, state, source.original.copy(inventoryContinuation = Some(InventoryContext(
      ContextReference(source.reference.identity, "1"), source.approved.decision, source.proposed))))

  private[admission] def successorC(from: InstanceRecord, saved: InstanceRecord, stage: O.Stage): Consequence[Unit] = for {
    _ <- _check(from != null && saved != null && from.lifecycle == Lifecycle.Active && from.suspension.isEmpty &&
      from.currentProgression.contains(ProgressionReference("phase.Decision")) &&
      Set(O.Stage.Implementation, O.Stage.Repair)(stage), "inventory receipt has no active Decision predecessor")
    _ <- from.validateC
    _ <- saved.validateC
    _ <- _relation_c(stage)
    expected <- from.appendC(from.revision, F.history(from, "phase." + stage.toString, causation))
    _ <- _check(expected == saved, "inventory receipt is not the canonical adjacent full native successor")
  } yield ()
  private[admission] def retainedC(session: F.Session, core: ActionCall.Core, source: Source): Consequence[Option[Saved]] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    saved <- _retained_c(session, core, source, adapter)
  } yield saved
  private def _retained_c(session: F.Session, core: ActionCall.Core, source: Source,
    adapter: WorkflowManagedMechanicalTransition[Payload]): Consequence[Option[Saved]] = for {
    retained <- adapter.loadRetainedC(source.reference)(using core.executionContext)
    saved <- retained match {
      case None => Consequence.success(Option.empty[Saved])
      case Some(value) => for {
        actualsource <- GoalPhaseNativeHistory.prefixC(session.current, source.from.revision)
        actualnext <- GoalPhaseNativeHistory.prefixC(session.current, value.receipt.next.revision)
        _ <- _check(actualsource == source.from && actualnext == value.receipt.next,
          "inventory receipt crosses its complete actual native prefixes")
        state <- admittedStateC(source, value.receipt.payload.value)
        _ <- successorC(source.from, actualnext, state.stage)
      } yield Some(_saved(source, actualnext, value.receipt.payload.value, state))
    }
  } yield saved
  private[admission] def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    source: Source): Consequence[Saved] = for {
    _ <- _check(session.current == source.from, "fresh inventory continuation omits later native history")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- _retained_c(session, core, source, adapter)
    saved <- retained match {
      case Some(value) => Consequence.success(value)
      case None => for {
        value <- Consequence.success(payload(source))
        state <- admittedStateC(source, value)
        _ <- _relation_c(state.stage)
        actual <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, source.reference,
          source.from, source.from.revision, F.history(source.from, "phase." + state.stage.toString, causation),
          TypedValue(codec.typeIdentity, value)),
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
        _ <- successorC(source.from, actual.receipt.next, state.stage)
        _ <- _check(actual.receipt.payload.value == value, "fresh inventory receipt changes its exact generated payload")
      } yield _saved(source, actual.receipt.next, value, state)
    }
  } yield saved

  private def _relation_c(stage: O.Stage): Consequence[Unit] = _check(
    domain.statemachine.GoalPhaseRunLifecycle.permits("Decision", "dependency-inventory-continued-" + stage.toString, stage.toString),
    "inventory continuation edge is absent from CML")
  private def _payload_valid(value: Payload): Boolean = value != null && _revision(value.sourceRevision) &&
    _reference(value.plan) && _text(value.step) && value.generation > 0 && value.generation < Long.MaxValue &&
    GoalPhaseRepairProgress.validateOptionC(Some(value.repairProgress)).isSuccess && value.base != null &&
    Vector(value.base.head, value.base.tree).forall(v => v != null && v.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) &&
    value.completed != null && value.completed.size <= 64 && value.completed.forall(step =>
      O.validStepAcceptance(step) && _text(step.step) && step.commit != null &&
        Vector(step.commit.identity, step.commit.parent, step.commit.tree).forall(value =>
          value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})"))) &&
    _reference(value.requestingResult) && V.approvalValid(value.approval) && value.approval.step == value.step &&
    value.originalResult != null && W.resultCodec.decode(W.resultCodec.encode(value.originalResult)).contains(value.originalResult) &&
    value.originalResult.dependencyProposal.nonEmpty && value.originalResult.outcome == "decision-needed" &&
    value.choice == "ApproveDependencyScope" && GoalPhasePurposeRepair.text(value.rationale) &&
    D.validateOptionC(Some(value.proposed)).isSuccess && value.proposed.scopeUpdate.exists(_.approval == value.approval.decision) &&
    assessC(value.step, value.proposed, Vector(value.approval)).map {
      case WithinBoundary(_) => true
      case _ => false
    }.fold(_ => false, identity)
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _reference(value: ContextReference): Boolean = value != null && _text(value.identity) && _text(value.revision)
  private def _text(value: String): Boolean = GoalPhasePurposeRepair.text(value) && !value.exists(_.isControl)
  private def _check(valid: Boolean, reason: String): Consequence[Unit] =
    if (valid) Consequence.unit else F.invalid("dependency inventory continuation: " + reason)
}
