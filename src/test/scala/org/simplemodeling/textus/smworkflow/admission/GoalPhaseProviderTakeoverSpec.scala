/**
 * @since   Nov. 21, 2026
 * @version Nov. 21, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ExecutionEvidence, ExecutionObservation, ExecutionPlacement}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseDispatchPolicy as D
import GoalPhaseOperatingProfiles as P
import GoalPhaseOrderedProgression as O
import GoalPhaseProviderTakeover as T

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalPhaseProviderTakeoverSpec extends AnyWordSpec with Matchers {
  private val _dispatch = D.Capture(D.revision, "ordinary", D.chain("ordinary"), "DELEGATED", None, D.defaultEdges)
  private val _candidate = O.Snapshot("a" * 40, "b" * 40)
  private val _scope = T.Scope("session", "plan", "1", "step")
  private def _failure(ordinal: Int): T.Failure =
    T.Failure(_scope, _dispatch, "ordinary", s"editor-$ordinal", s"continuation-$ordinal",
      s"result-$ordinal", "1", s"validation-$ordinal", _candidate, "compile", "compile", 1, None)

  "The selected Step's editor takeover" should {
    "use the next captured role once two separate failed validations establish the same blocker" in {
      val failures = Vector(_failure(1), _failure(2))
      val (dispatch, takeover) = T.select(_dispatch, failures)
      dispatch.candidates shouldBe Vector("difficult", "hardest")
      D.valid(dispatch) shouldBe true
      val saved = takeover.get
      saved.departedContinuation shouldBe "continuation-2"
      saved.previousValidation shouldBe "validation-1"
      saved.validation shouldBe "validation-2"
      saved.candidateTree shouldBe _candidate.tree
      val capture = P.Capture(P.Selection(P.standard, P.revision, "default"), None, Some("caller-standard"),
        Some(D.revision), None, None, dispatch = Some(dispatch), providerTakeover = takeover)
      P.captureValid(capture) shouldBe true
      T.retained(capture, failures) shouldBe true
      T.retained(capture, failures.takeRight(1)) shouldBe false

      def execution(profile: String, executor: String, result: String) =
        ExecutionEvidence(Vector(ContextReference(result, "1")), execution = Some(
          ExecutionObservation(ExecutionPlacement.Delegated, "caller", executor, profile)))
      P.admitExecutionC(Some(capture), execution("ordinary", "editor-2", "result-2")).isSuccess shouldBe false
      P.admitExecutionC(Some(capture), execution("difficult", "editor-2", "result-3")).isSuccess shouldBe false
      P.admitExecutionC(Some(capture), execution("difficult", "editor-3", "result-2")).isSuccess shouldBe false
      P.admitExecutionC(Some(capture), execution("difficult", "editor-3", "result-3")).isSuccess shouldBe true

      val next = _failure(3).copy(dispatch = dispatch, profile = "difficult", previousTakeover = takeover)
      T.select(_dispatch, Vector(_failure(2), next)) shouldBe (dispatch -> takeover)
      val later = next.copy(continuation = "continuation-4", validation = "validation-4",
        executor = "editor-4", result = "result-4")
      val (last, furtherTakeover) = T.select(_dispatch, Vector(next, later))
      last.candidates shouldBe Vector("hardest")
      furtherTakeover.map(_.departedContinuation) shouldBe Some("continuation-4")
    }

    "preserve the current assignment when comparable evidence or an admitted alternate is absent" in {
      val first = _failure(1)
      val second = _failure(2)
      val changed = Vector(
        second.copy(scope = _scope.copy(step = "other-step")),
        second.copy(scope = _scope.copy(session = "other-session")),
        second.copy(scope = _scope.copy(revision = "2")),
        second.copy(candidate = _candidate.copy(tree = "c" * 40)),
        second.copy(check = "test"),
        second.copy(exit = 2),
        second.copy(profile = "difficult"),
        second.copy(continuation = first.continuation),
        second.copy(validation = first.validation))
      changed.foreach(current => T.select(_dispatch, Vector(first, current)) shouldBe (_dispatch -> None))
      T.select(_dispatch, Vector(second)) shouldBe (_dispatch -> None)
      val closed = _dispatch.copy(candidates = Vector("ordinary"), edges = Map.empty)
      T.select(closed, Vector(first.copy(dispatch = closed), second.copy(dispatch = closed))) shouldBe (closed -> None)
    }
  }
}
