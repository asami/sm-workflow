package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.WorkflowProtocolV1.ContinuationResult
import GoalPhaseNativeRun as F
import GoalPhaseNativeWork as S
import GoalPhaseOrderedProgression as O

/** Actual selected semantic review context and independent host admission.
  * A worker's identity/accept string alone never establishes independence.
  * @since   Oct.  6, 2026
  *  version Oct. 11, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeReview {
  final case class Context(changedPaths: Vector[String],
    diff: String, checks: Vector[RepositorySyncNativeMergeValidation.Receipt])
  final case class Policy(identity: String, revision: Int) {
    def validateC: Consequence[Unit] = if (identity != null &&
        identity.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}") && revision > 0) Consequence.unit
      else F.invalid("native independent review policy is incomplete")
  }
  trait EvidencePort {
    def admitC(core: ActionCall.Core, input: S.Request, submitted: ContinuationResult[S.Result]): Consequence[Unit]
  }
  final case class Installation(policy: Policy, evidence: EvidencePort) {
    def validateC = if (policy == null || evidence == null) F.invalid("independent review verifier is missing")
      else policy.validateC
  }
  /** The owning adapter reconstructs this controller from actual retained
    * implementation/repair/review completions, never from a submitted stage.
    */
  def completedStateC(result: S.Result, evidence: String,
    controller: O.State): Consequence[O.State] = for {
    _ <- if (result != null && bounded(result.summary, 16384) && result.summary.trim.nonEmpty &&
        result.findings != null && result.findings.size <= 128 && result.findings.forall(f => bounded(f, 16384) && f.trim.nonEmpty) &&
        Set("accept", "repair", "decision-needed")(result.outcome) &&
        (result.outcome != "accept" || result.findings.isEmpty) &&
        (result.outcome != "repair" || result.findings.nonEmpty)) Consequence.unit
      else F.invalid("review result must contain an assigned semantic disposition, rationale and required findings")
    work <- controller.next match {
      case value: O.Semantic if value.key.kind == O.WorkKind.ReviewStep ||
          value.key.kind == O.WorkKind.ReviewPhase => Consequence.success(value)
      case _ => F.invalid("review result has no assigned semantic frontier")
    }
    _ <- GoalPhaseAcceptanceBoundary.admitC(controller.plan, work.key.kind.toString, result)
    outcome = result.outcome match {
      case "accept" => O.ReviewOutcome.Accept
      case "repair" => O.ReviewOutcome.Repair
      case _ => O.ReviewOutcome.Human
    }
    directive = if (result.outcome == "repair") Some(O.RepairDirective(result.summary, result.findings)) else None
    next <- controller.acceptC(O.ReviewAdmitted(work.key, work.candidate.get, work.checksReceipt.get,
      evidence, outcome, directive))
  } yield next
  private def bounded(value: String, maximum: Int) = value != null && !value.contains('\u0000') &&
    value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= maximum
}
