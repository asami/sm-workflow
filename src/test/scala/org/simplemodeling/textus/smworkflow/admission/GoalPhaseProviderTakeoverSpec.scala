/**
 * @since   Nov. 21, 2026
 * @version Nov. 21, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.processexecution.{SbtScenarioSelection, SbtValidationMode}
import org.goldenport.cncf.validation.ValidationExecutionModeV1
import org.goldenport.cncf.workflow.{ContextReference, ContextSnapshot, ContinuationIdentity, StateMachineRevision, StateMachineRunIdentity}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceIdentity, WorkflowDefinitionIdentity, WorkflowDefinitionRevision}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ContinuationResult, ExecutionEvidence, ExecutionObservation, ExecutionPlacement, TypedValue, WorkflowHandle}
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
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

    "apply the existing trend window only to complete comparable scoped diagnostics" in {
      def observed(n: Int, findings: String*): T.Failure = _failure(n).copy(
        candidate = _candidate.copy(tree = n.toString * 40),
        diagnosticPolicy = Some(GoalPhaseRuntimeRepairControl.version -> 10),
        diagnostics = Some(findings.toVector))
      val improving = Vector(observed(1, "a", "b", "c", "d"), observed(2, "a", "b", "c"),
        observed(3, "a", "b"), observed(4, "a"))
      T.select(_dispatch, improving) shouldBe (_dispatch -> None)
      val regression = Vector(observed(1, "a"), observed(2, "a", "b"),
        observed(3, "a", "b", "c"), observed(4, "a", "b", "c", "d"))
      val (next, evidence) = T.select(_dispatch, regression)
      next.candidates shouldBe Vector("difficult", "hardest")
      evidence.get.previousValidation shouldBe "validation-3"
      evidence.get.trend.get.reason should include ("sustained regression")
      evidence.get.trend.get.validations shouldBe regression.map(_.validation)
      val oscillation = Vector(observed(1, "a"), observed(2, "b"), observed(3, "a"), observed(4, "b"))
      T.select(_dispatch, oscillation)._2.nonEmpty shouldBe true
      T.select(_dispatch, regression.updated(1, regression(1).copy(diagnostics = None))) shouldBe (_dispatch -> None)
      T.select(_dispatch, regression.updated(1, regression(1).copy(check = "other"))) shouldBe (_dispatch -> None)
      T.select(_dispatch, regression.updated(1, regression(1).copy(diagnosticPolicy = None))) shouldBe (_dispatch -> None)
      T.select(_dispatch, regression.updated(1, regression(1).copy(continuation = regression.head.continuation))) shouldBe (_dispatch -> None)
      val after = observed(5, "x").copy(dispatch = next, profile = "difficult", previousTakeover = evidence)
      T.select(_dispatch, regression :+ after) shouldBe (next -> evidence)
      val last = next.copy(role = "hardest", candidates = Vector("hardest"))
      val exhausted = (1 to 4).map(n => observed(n, "same").copy(dispatch = last, profile = "hardest",
        previousTakeover = evidence)).toVector
      T.select(_dispatch, exhausted) shouldBe (last -> evidence)
    }

    "keep the editor for a first CAR lint failure and use the next admitted editor for recurring blockers" in {
      val first = _failure(1).copy(check = "car-conformance", kind = "car-lint",
        diagnostics = Some(Vector("abi:missing-operation:src/main/car/abi-manifest.json")))
      val nextCandidate = _candidate.copy(tree = "c" * 40)
      val repeated = _failure(2).copy(check = first.check, kind = first.kind, candidate = nextCandidate,
        diagnostics = first.diagnostics)
      T.select(_dispatch, Vector(first)) shouldBe (_dispatch -> None)
      val (dispatch, evidence) = T.select(_dispatch, Vector(first, repeated))
      dispatch.candidates shouldBe Vector("difficult", "hardest")
      evidence.map(_.candidateTree) shouldBe Some(nextCandidate.tree)
      evidence.map(_.previousValidation) shouldBe Some(first.validation)
      val different = repeated.copy(diagnostics = Some(Vector("cml:syntax:src/main/cozy/main.cml")))
      T.select(_dispatch, Vector(first, different)) shouldBe (_dispatch -> None)
      T.select(_dispatch, Vector(first, repeated.copy(diagnostics = None))) shouldBe (_dispatch -> None)
    }

    "use only remaining captured roles for an attributable decline and none for a block" in {
      import GoalPhaseNativeWork as W
      val capture = P.Capture(P.Selection(P.standard, P.revision, "default"), None, Some("caller-standard"),
        Some(D.revision), None, None, dispatch = Some(_dispatch))
      val request = NativeHumanEvidenceFixture.work.copy(
        instanceIdentity = _scope.session, kind = "ImplementStep", stepIdentity = Some(_scope.step),
        operatingProfile = Some(capture))
      val handle = WorkflowHandle(SmWorkflowComponent.componentId, WorkflowDefinitionIdentity("GoalPhaseWorkflow"),
        WorkflowDefinitionRevision("1"), InstanceIdentity(_scope.session))
      def returned(disposition: String, role: String) = ContinuationResult(handle,
        StateMachineRunIdentity(_scope.session), ContinuationIdentity("editor-work"), StateMachineRevision("3"),
        ContextSnapshot("1"), TypedValue(W.resultCodec.typeIdentity, W.Result(disposition, "Fixture disposition", Vector.empty)),
        ContextReference("actual-result", "1"), Vector.empty, ExecutionEvidence(Vector.empty, Some("editor"),
          execution = Some(ExecutionObservation(ExecutionPlacement.Delegated, "caller", "editor", role))))
      val decline = returned("declined", "ordinary")
      W.admitProviderStopC(request, decline.result.value).isSuccess shouldBe true
      val next = T.declineC(request, decline).toOption.get.get
      next.successor.candidates shouldBe Vector("difficult", "hardest")
      next.continuation shouldBe "editor-work"
      next.resultIdentity shouldBe "actual-result"
      val laterFailure = _failure(1).copy(dispatch = next.successor, profile = next.successor.role)
      T.select(_dispatch, Vector(laterFailure)) shouldBe (next.successor -> None)
      val laterAgain = _failure(2).copy(dispatch = next.successor, profile = next.successor.role,
        candidate = _candidate.copy(tree = "c" * 40))
      T.select(_dispatch, Vector(laterFailure, laterAgain)) shouldBe (next.successor -> None)
      T.declineC(request, returned("declined", "hardest")).toOption shouldBe Some(None)
      T.declineC(request, returned("uncertain", "ordinary")).isSuccess shouldBe false
      T.declineC(request, returned("declined", "not-admitted")).isSuccess shouldBe false
      W.admitProviderStopC(request, decline.result.value.copy(findings = Vector("changed"))).isSuccess shouldBe false
      val blocked = returned("blocked", "ordinary")
      W.admitProviderStopC(request, blocked.result.value).isSuccess shouldBe true
      T.declineC(request, blocked).isSuccess shouldBe false
    }

    "reject incomplete or unattributed extracted diagnostic sets" in {
      import IncrementalSbtFailureDiagnosis as I
      val source = I.Source("stdout", "complete", 1, "retained-check-capture", "none-observed")
      val occurrence = I.Occurrence("stdout", 1, 1, 1, 1, "", 0, None, None, None, None,
        Some(I.Location("src/A.scala", 1, Some(2))), None, Vector.empty, Vector.empty)
      val group = I.Group("compiler-error", "src/A.scala:1:2: error", Vector(occurrence), 1, 0)
      val view = I.View("compile", "compile", "1", _candidate, "exited", Some(1),
        "retained-console-capture", "recognized-patterns-only", Vector(source), Vector(group), 0,
        I.Execution("tests-not-selected", "reported-summary-only", None, None, None, Vector.empty))
      T._diagnostics(view).get.size shouldBe 1
      T._diagnostics(view.copy(sources = Vector(source.copy(sourceLoss = "truncated")))) shouldBe None
      T._diagnostics(view.copy(sources = Vector(source.copy(textStatus = "legacy-completeness-unknown")))) shouldBe None
      T._diagnostics(view.copy(omittedGroups = 1)) shouldBe None
      T._diagnostics(view.copy(groups = Vector(group.copy(omittedOccurrences = 1)))) shouldBe None
      T._diagnostics(view.copy(groups = Vector(group.copy(kind = "test-failure")))) shouldBe None
      val rendered = group.copy(kind = "test-failure", occurrences = Vector(
        occurrence.copy(precedingSuiteHeader = Some("ExampleSpec"), test = Some("fails"))))
      val testView = view.copy(groups = Vector(rendered))
      T._diagnostics(testView) shouldBe None
      val selected = SbtValidationMode.Case(SbtScenarioSelection("fixture.ExampleSpec", "fails"), "develop")
      val observed = ValidationExecutionModeV1.Observation("fixture.ExampleSpec", "fails", "develop", "fixture")
      val failure = SbtValidationMode.CaseResult(selected, "failed", Some(observed))
      T._diagnostics(testView, Some(Vector(failure))) shouldBe Some(Vector("test:fixture.ExampleSpec:fails"))
      T._diagnostics(testView.copy(sources = Vector(source.copy(sourceLoss = "truncated"))),
        Some(Vector(failure))) shouldBe None
      T._diagnostics(testView, Some(Vector(failure.copy(status = "unverified")))) shouldBe None
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
