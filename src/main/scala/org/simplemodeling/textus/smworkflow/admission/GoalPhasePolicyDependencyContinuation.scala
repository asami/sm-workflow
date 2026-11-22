package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
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
import GoalPhaseDevelopmentPermissions as P

/** One ordinary native admission receipt derived from the selected Phase policy
  * and exact proposal. It does not synthesize an ApproveDependencyScope answer.
  * Actual Human decisions and their original lineage remain unchanged.
  * @since   Nov. 12, 2026
  * @version Nov. 23, 2026
  */
private[smworkflow] object GoalPhasePolicyDependencyContinuation {
  val causation = "ordered-phase-policy-dependency-admitted"
  val choice = "AdmitPhaseDependencyPolicy"
  final case class Source(from: InstanceRecord, controller: O.State, input: W.Request,
    result: ContinuationResult[W.Result], authority: P.Authority) {
    def reference: WorkflowMechanicalTransition.Reference = WorkflowMechanicalTransition.Reference(
      from.identity.value + ":phase-policy-dependency:" + from.revision.value, "1")
  }
  final case class Payload(sourceRevision: Long, requestingResult: ContextReference,
    proposal: GoalPhaseDependencyScope.Proposal, authority: P.Authority)
  object Payload {
    private given Encoder[ContextReference] = deriveEncoder
    private given Decoder[ContextReference] = deriveDecoder
    given Encoder[Payload] = deriveEncoder
    given Decoder[Payload] = deriveDecoder
  }
  val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhasePolicyDependencyAdmission"
    def encode(value: Payload): Json = value.asJson
    def decode(value: Json): Either[String, Payload] = value.as[Payload].left.map(_.message)
  }
  final case class Saved(record: InstanceRecord, controller: O.State, source: Source, payload: Payload) {
    def decision: ContextReference = ContextReference(source.reference.identity, source.reference.revision)
    def approval: GoalPhaseDependencyValidation.Approval = GoalPhaseDependencyValidation.Approval(
      source.input.stepIdentity.get, payload.proposal, decision)
    def context: W.DecisionContext = W.DecisionContext(source.result.result.value, choice,
      "Necessary dependency work admitted from the retained Phase policy and concrete graph; this is not a Human answer", Some(decision))
  }

  private def sourceC(from: InstanceRecord, controller: O.State, input: W.Request,
    result: ContinuationResult[W.Result], authority: P.Authority): Consequence[Source] = for {
    _ <- if (from.lifecycle == Lifecycle.Active && from.suspension.isEmpty &&
      from.currentProgression.contains(ProgressionReference("phase.Decision")) && controller.stage == O.Stage.Decision &&
      controller.decisionSource.exists(Set(O.Stage.Implementation, O.Stage.Repair)) &&
      !controller.documentDecision && controller.pendingRepair.isEmpty && controller.cursor < controller.plan.steps.size &&
      Set("ImplementStep", "RepairStep")(input.kind) && input.instanceIdentity == from.identity.value &&
      input.selectedPlan == controller.plan && input.originalBase == controller.base && input.completed == controller.completed &&
      input.generation == controller.generation && input.stepIdentity.contains(controller.plan.steps(controller.cursor).identity) &&
      result.result.value.outcome == "decision-needed" && result.result.value.dependencyProposal.nonEmpty &&
      result.result.value.dependencyProposal.flatMap(_.scopeUpdate).isEmpty && input.decisionContext.forall(context =>
        !Set("RejectDependencyScope", "Defer", "Cancel")(context.choice))) Consequence.unit
      else F.invalid("Phase-policy admission requires its exact open implementation/repair dependency Decision")
    _ <- W.admitSemanticResultC(input, result.result.value)
    revision <- input.sourceRevision.toLongOption.filter(_ > 0).map(n => Consequence.success(InstanceRevision(n)))
      .getOrElse(F.invalid("Phase-policy source revision is invalid"))
    pending <- GoalPhaseNativeHistory.prefixC(from, revision.next)
    completed <- GoalPhaseNativeHistory.completionC(from, pending, result.resultReference, "Decision")
    _ <- if (completed == from && pending.suspension.exists(_.continuationIdentity.value == result.continuationId.value))
      Consequence.unit else F.invalid("Phase policy does not bind the adjacent actual semantic completion")
    assessed <- P.assessC(controller.plan, result.result.value.dependencyProposal.get, authority)
    _ <- if (assessed.status == "admitted") Consequence.unit else F.invalid(assessed.reason)
  } yield Source(from, controller, input, result, authority)

  def selectC(from: InstanceRecord, controller: O.State, last: Option[(W.Request, ContinuationResult[W.Result])],
    configured: Option[ManagedDevelopmentRuntime.Policy]): Consequence[Option[Source]] = last match {
    case Some((input, result)) if result.result.value.dependencyProposal.exists(_.scopeUpdate.isEmpty) &&
      controller.stage == O.Stage.Decision && controller.decisionSource.exists(Set(O.Stage.Implementation, O.Stage.Repair)) &&
      !DependencyDevelopmentDelivery.requiresDirection(input, result.result.value, configured) &&
      input.decisionContext.forall(context => !Set("RejectDependencyScope", "Defer", "Cancel")(context.choice)) =>
      val proposal = result.result.value.dependencyProposal.get
      val authority = P.capture(controller.plan, proposal, configured)
      P.assessC(controller.plan, proposal, authority).flatMap { assessment =>
        if (assessment.status == "admitted") sourceC(from, controller, input, result, authority).map(Some(_))
        else Consequence.success(None)
      }
    case _ => Consequence.success(None)
  }

  private def expected(source: Source): Payload = Payload(source.from.revision.value,
    source.result.resultReference, source.result.result.value.dependencyProposal.get, source.authority)
  private def savedC(source: Source, record: InstanceRecord, value: Payload): Consequence[Saved] = for {
    _ <- if (value == expected(source)) Consequence.unit else F.invalid("Phase-policy receipt changes its actual proposal/source")
    resumed <- source.controller.acceptC(O.DecisionResumptionAdmitted(source.reference.identity))
    _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits("Decision", "dependency-policy-admitted-" + resumed.stage, resumed.stage.toString))
      Consequence.unit else F.invalid("Phase-policy continuation edge is absent from CML")
    predicted <- source.from.appendC(source.from.revision, F.history(source.from, "phase." + resumed.stage, causation))
    _ <- if (predicted == record) Consequence.unit else F.invalid("Phase-policy receipt is not its full adjacent native successor")
  } yield Saved(record, resumed, source, value)

  /** Reconstruct only from this actual source's canonical mechanical receipt;
    * later configuration cannot erase its originally retained graph/policy.
    */
  def retainedC(session: F.Session, from: InstanceRecord, controller: O.State,
    last: Option[(W.Request, ContinuationResult[W.Result])], core: ActionCall.Core): Consequence[Saved] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    reference = WorkflowMechanicalTransition.Reference(from.identity.value + ":phase-policy-dependency:" + from.revision.value, "1")
    actual <- adapter.loadRetainedC(reference)(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(F.invalid("Phase-policy history has no actual native receipt")))
    pair <- last.map(Consequence.success).getOrElse(F.invalid("Phase-policy history has no original semantic result"))
    source <- sourceC(from, controller, pair._1, pair._2, actual.receipt.payload.value.authority)
    next <- GoalPhaseNativeHistory.prefixC(session.current, actual.receipt.next.revision)
    _ <- if (next == actual.receipt.next) Consequence.unit else F.invalid("Phase-policy receipt crosses actual native history")
    saved <- savedC(source, next, actual.receipt.payload.value)
  } yield saved

  def advanceC(session: F.Session, d: F.Declaration, source: Source, core: ActionCall.Core): Consequence[Saved] = for {
    _ <- if (session.current == source.from) Consequence.unit else F.invalid("Fresh Phase-policy admission omits later history")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    resumed <- source.controller.acceptC(O.DecisionResumptionAdmitted(source.reference.identity))
    actual <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, source.reference,
      source.from, source.from.revision, F.history(source.from, "phase." + resumed.stage, causation),
      TypedValue(codec.typeIdentity, expected(source))),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    saved <- savedC(source, actual.receipt.next, actual.receipt.payload.value)
  } yield saved
}
