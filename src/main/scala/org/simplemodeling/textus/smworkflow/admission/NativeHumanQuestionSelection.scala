package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Resolve an issued Human slot, not an unchanged regenerated question body.
  * Stored meaning and presentation remain authoritative. Native source and
  * participant/answer admission belong to their respective owning boundaries.
  * @since   Oct.  8, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object NativeHumanQuestionSelection {
  def selectC[W](expected: WorkflowInteraction[W, Nothing], saved: WorkflowInteraction[W, Nothing]):
      Consequence[WorkflowInteraction[W, Nothing]] =
    if (expected == null || saved == null || saved.handle == null || saved.handle != expected.handle)
      invalid("question has another instance or executable definition reference")
    else (expected.current, saved.current) match {
      case (planned: WorkflowContinuation.Decision[W], selected: WorkflowContinuation.Decision[W])
          if planned.request != null && selected.request != null =>
        val e = planned.request
        val q = selected.request
        for {
          _ <- saved.handle.validateC
          _ <- if (q.runId == e.runId && q.continuationId == e.continuationId && q.expectedRevision == e.expectedRevision &&
              q.requiredOperation == e.requiredOperation && q.operation == e.operation && q.resultType == e.resultType &&
              q.completionOperation == e.completionOperation && q.input.exists(v => v != null && v.value != null &&
                e.input.exists(x => x != null && x.typeIdentity == v.typeIdentity)) &&
              q.context != null && e.context != null && GoalPhaseClosureAuthority.sameContextScope(q.context.snapshot, e.context.snapshot))
            Consequence.unit else invalid("question has an incompatible slot, executable route, type or model scope")
        } yield saved
      case _ => invalid("selected issue is not its declared Human Decision")
    }

  /** A repeated submission may observe its retained answer, never substitute
    * another choice/resolution using that answer's reference. Diagnostics are
    * not a copy certificate; callers consume the saved answer after selection.
    */
  def sameAnswerTarget[A](left: ContinuationResult[A], right: ContinuationResult[A]): Boolean =
    left != null && right != null && left.result != null && right.result != null &&
      left.handle == right.handle && left.runId == right.runId && left.continuationId == right.continuationId &&
      left.expectedRevision == right.expectedRevision && left.resultReference == right.resultReference &&
      left.result.typeIdentity == right.result.typeIdentity

  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Native Human " + reason)
}
