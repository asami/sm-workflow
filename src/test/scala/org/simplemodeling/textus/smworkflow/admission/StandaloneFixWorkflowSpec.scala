package org.simplemodeling.textus.smworkflow.admission

import org.simplemodeling.textus.smworkflow.admission.StandaloneFixExecution.given

import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import StandaloneFixExecution as S
import WorkflowSbtValidation as T
import RepositorySyncNativeMergeValidation as V

/** Bounded admission specifications using explicitly fixture-owned receipts.
  * These do not assert installed delivery or execution of a real SBT process.
  * @since   Oct. 26, 2026
  *  version Oct. 26, 2026
  * @version Nov.  6, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class StandaloneFixWorkflowSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](c: Consequence[A]): A = c match {
    case Consequence.Success(value) => value
    case other => fail("Fixture admission failed: " + other)
  }
  private val checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, T.Kind.Compile),
    GoalPhaseNativeValidation.Check("lint", 2, T.Kind.FixCheck),
    GoalPhaseNativeValidation.Check("focused", 3, T.Kind.TestOnly))
  private val input = S.Input("repo", "fix-selected-behavior", "Correct selected boundary behavior", Vector("src"),
    checks.map(_.identity), Vector("behavior"), Vector(ContextReference("adopted-issue", "1")), false)
  private val candidate = S.Candidate("a" * 40, "b" * 40, Vector("src/main/scala/Example.scala"),
    "diff --git a/src/main/scala/Example.scala b/src/main/scala/Example.scala\n-old boundary\n+correct boundary")
  private def start = take(S.startC(input, "human", checks))
  private def selected = take(S.candidateC(start, candidate))
  private def receipt(check: GoalPhaseNativeValidation.Check, passed: Boolean = true, output: String = "") =
    V.Receipt(check.identity, "exited", Some(if (passed) 0 else 1), output, "", Some(T.Facts(check.kind,
      if (passed) T.Outcome.Succeeded else T.Outcome.Failed,
      if (passed && check.kind == T.Kind.TestOnly) Some(T.Selection(Vector("example.BehaviorSpec"), 1, 3L)) else None, None)))
  private def observe(s: S.State, r: V.Receipt) = {
    val observed = take(S.observedC(take(S.intentC(s)), r))
    if (observed.stage == "Assess") take(S.assessC(observed)) else observed
  }
  private def repaired = {
    val failed = observe(selected, receipt(checks.head, false, "[error] retained failure"))
    val work = S.Work("fixture-session", "12", "Repair", failed)
    val result = S.Result("completed", "Corrected only the selected behavior", Vector.empty,
      Vector(S.Condition("behavior", true, "Selected boundary condition addressed")), Vector.empty)
    take(S.completedC(work, result, ContextReference("actual-semantic-result", "1")))
  }
  private def passed(s: S.State) = checks.foldLeft(s)((state, check) => observe(state, receipt(check)))

  "Retained confirmed terminal validation" should {
    "resume only its affected check without consuming repair counts or replacing the candidate" in {
      Given("an existing selected candidate and a fixture-owned terminal incomplete capture")
      val captured = selected
      val waiting = take(S.observedC(take(S.intentC(captured)), TerminatedValidationFixture.receipt(checks.head)))
      When("a later ordinary continuation prepares the affected check")
      val resumed = take(S.continueTerminatedValidationC(waiting))
      val intent = take(S.intentC(resumed))
      Then("the original source/candidate and counts survive and an unresolved intent cannot be resumed again")
      waiting.stage shouldBe "Wait"
      waiting.receipts.last.processObservation should not be empty
      resumed.input shouldBe waiting.input
      resumed.candidate shouldBe waiting.candidate
      resumed.automaticCount shouldBe waiting.automaticCount
      resumed.ordinaryCount shouldBe waiting.ordinaryCount
      resumed.repairControl shouldBe waiting.repairControl
      intent.pendingCheck shouldBe Some(checks.head.identity)
      S.continueTerminatedValidationC(intent).isSuccess shouldBe false
    }
  }

  "Standalone repair admission" should {
    "retain the final successful observation before a missing-Compile code assessment can refuse" in {
      Given("a readable historical standalone code intent selecting only an actually nonempty focused test")
      val check = checks.last
      val legacyinput = input.copy(checks = Vector(check.identity), scalaLint = "off")
      val legacy = take(S.startC(legacyinput, "human", Vector(check)))
      val intent = take(S.intentC(take(S.candidateC(legacy, candidate))))
      val successful = receipt(check)
      When("the final actual receipt is observed without running the separate assessment")
      val observed = take(S.observedC(intent, successful))
      val restored = observed.asJson.as[S.State].fold(error => fail(error.message), identity)
      Then("the Assess checkpoint retains the receipt even though ordinary code acceptance still refuses")
      observed.stage shouldBe "Assess"
      restored.receipts shouldBe Vector(successful)
      restored.pendingCheck shouldBe None
      S.assessC(restored).isSuccess shouldBe false
      restored.input shouldBe legacyinput
      restored.automaticCount shouldBe 0
    }

    "reject a new incomplete standalone code selection while keeping its historical input readable" in {
      Given("the standalone code selection gate and an existing legacy TestOnly request")
      val incomplete = input.copy(checks = Vector(checks.last.identity), scalaLint = "off")
      When("the pre-effect gate checks incomplete code, complete code and validation-only selections")
      val results = Vector(WorkflowRepairAssessment.standaloneSelectionC(incomplete, Vector(checks.last)).isSuccess,
        WorkflowRepairAssessment.standaloneSelectionC(input, checks).isSuccess,
        WorkflowRepairAssessment.standaloneSelectionC(incomplete.copy(kind = "validation"), Vector(checks.last)).isSuccess)
      Then("only new incomplete code is rejected; historical decoding and ordinary code requirements remain")
      results shouldBe Vector(false, true, true)
      S.inputC(incomplete).isSuccess shouldBe true
      incomplete.asJson.as[S.Input] shouldBe Right(incomplete)
      WorkflowRepairAssessment.checksC("code", "off", Vector(checks.last), Vector(receipt(checks.last))).isSuccess shouldBe false
      WorkflowRepairAssessment.checksC("code", "off", Vector(checks.head), Vector(receipt(checks.head))).isSuccess shouldBe false
    }

    "finish a successful validation-only batch as NoChange and pause its failure without issuing repairs" in {
      Given("an explicitly selected standalone validation-only qualification")
      val check = checks.last
      val validation = input.copy(kind = "validation", checks = Vector(check.identity))
      val captured = take(S.candidateC(take(S.startC(validation, "human", Vector(check))), candidate))
      val intent = take(S.intentC(captured))
      When("the normal final observations record either the actual passing result or actual failed result")
      val observed = take(S.observedC(intent, receipt(check)))
      val passed = take(S.assessC(observed))
      val failed = take(S.observedC(intent, receipt(check, false, "[error] actual qualification failed")))
      Then("successful qualification accepts no repair or commit and failure requests Human direction")
      observed.stage shouldBe "Assess"
      passed.stage shouldBe "NoChange"
      passed.message should include("no code repair, commit or publication")
      passed.conditionReport shouldBe empty
      passed.semanticReference shouldBe None
      failed.stage shouldBe "Human"
      failed.receipts should have size 1
      failed.automaticCount shouldBe 0
      failed.ordinaryCount shouldBe 0
      S.inputC(validation.copy(reviewRequired = true)).isSuccess shouldBe false
      S.inputC(validation.copy(task = Some(S.Task("direct-instruction", Vector.empty)))).isSuccess shouldBe false
      StandaloneFixNativeRun.executionRequirementC(validation, "Repair").isSuccess shouldBe false
      val work = S.Work("fixture", "12", "Repair", failed.copy(stage = "Repair"))
      S.admitResultC(work, S.Result("completed", "Must not become repair work", Vector.empty,
        Vector(S.Condition("behavior", true, "Unsupported repair")), Vector.empty)).isSuccess shouldBe false
    }

    "connect retained failure, semantic repair and a fresh nonempty typed check batch without Phase completion or commit" in {
      Given("fixture-owned failed compilation and one admitted standalone semantic repair")
      val next = repaired
      next.automaticCount shouldBe 1
      next.ordinaryCount shouldBe 1
      next.stage shouldBe "Inspect"
      When("the new candidate receives compile, lint and an actually selected nonempty focused-test fixture batch")
      val accepted = passed(take(S.candidateC(next, candidate.copy(tree = "c" * 40))))
      Then("only standalone acceptance is eligible and its source candidate remains uncommitted")
      accepted.stage shouldBe "Accepted"
      accepted.message should include("uncommitted")
      accepted.receipts.map(_.identity) shouldBe input.checks
      accepted.semanticReference shouldBe Some(ContextReference("actual-semantic-result", "1"))
    }

    Vector("Compile", "Test").foreach { configuration =>
      s"preserve $configuration compiler diagnostics and truthfully withhold remaining test results" in {
        val output = "[error] -- [E007] Type Mismatch Error: /repo/src/Example.scala:8:3\n" +
          "[error] Found: String\n[error] Required: Int\n" +
          s"[error] (app / $configuration / compileIncremental) Compilation failed\n"
        val failed = observe(selected, receipt(checks.head, false, output))
        failed.stage shouldBe "Repair"
        failed.receipts.size shouldBe 1
        val diagnostic = S.diagnostics(failed, "actual-source-12").head
        diagnostic.testExecution.status shouldBe "not-executed-because-compilation-failed"
        diagnostic.testExecution.testsRun shouldBe None
        diagnostic.testExecution.failedTests shouldBe None
        diagnostic.groups.flatMap(_.occurrences).flatMap(_.compilerConfiguration) should contain(configuration)
      }
    }

    "keep raw Compile unselected while retaining actual mixed test execution in a selected standalone batch" in {
      val compiler = "[error] (other / Test / compileIncremental) Compilation failed\n"
      val raw = receipt(checks.head, false, compiler)
      IncrementalSbtFailureDiagnosis.extract(raw, T.Kind.Compile, "actual-source-12", S.snapshot(candidate))
        .testExecution.status shouldBe "tests-not-selected"
      val executed = "[info] Total number of tests run: 3\n" +
        "[info] Tests: succeeded 2, failed 1, canceled 0, ignored 0, pending 0\n"
      val failed = observe(selected, raw.copy(stdout = executed + compiler))
      val diagnostic = S.diagnostics(failed, "actual-source-12").head
      diagnostic.testExecution.status shouldBe "executed-tests-observed"
      diagnostic.testExecution.testsRun shouldBe Some(3)
      diagnostic.testExecution.failedTests shouldBe Some(1)
      diagnostic.testExecution.compilationBlocked.map(_.project) should contain(Some("other"))
      failed.receipts.size shouldBe 1
    }

    "retain uncertain output without using exit zero as successful behavioral execution or replay authority" in {
      val intent = take(S.intentC(selected))
      val unexecuted = receipt(checks.head).copy(sbt = Some(T.Facts(T.Kind.Compile, T.Outcome.NotExecuted, None, None)))
      val waiting = take(S.observedC(intent, unexecuted))
      waiting.stage shouldBe "Wait"
      waiting.receipts shouldBe Vector(unexecuted)
      S.intentC(waiting) shouldBe a[Consequence.Failure[?]]
      S.intentC(intent) shouldBe a[Consequence.Failure[?]]
    }

    "refuse an empty focused selection and a mismatched or repeated receipt" in {
      val malformed = receipt(checks.last).copy(sbt = Some(T.Facts(T.Kind.TestOnly, T.Outcome.Succeeded,
        Some(T.Selection(Vector("example.BehaviorSpec"), 0, 0)), None)))
      val beforeFocused = observe(observe(selected, receipt(checks.head)), receipt(checks(1)))
      S.observedC(take(S.intentC(beforeFocused)), malformed) shouldBe a[Consequence.Failure[?]]
      S.observedC(take(S.intentC(selected)), receipt(checks(1))) shouldBe a[Consequence.Failure[?]]
      S.observedC(selected, receipt(checks.head)) shouldBe a[Consequence.Failure[?]]
    }

    "require independent review for unresolved test meaning and refuse unresolved accepting results" in {
      val next = repaired.copy(unresolvedQuestions = Vector("Expected value is unsettled"))
      val candidateWithTests = candidate.copy(paths = Vector("src/test/scala/ExampleSpec.scala"), tree = "c" * 40)
      val review = passed(take(S.candidateC(next, candidateWithTests)))
      review.stage shouldBe "Review"
      review.reviewReference shouldBe None
      val work = S.Work("fixture-session", "20", "Review", review)
      val open = S.Result("accept", "Tests passed", Vector.empty,
        Vector(S.Condition("behavior", true, "Checks cover selected behavior")), Vector("Expected value is still unclear"))
      S.admitResultC(work, open) shouldBe a[Consequence.Failure[?]]
      val accepted = take(S.completedC(work, open.copy(unresolvedQuestions = Vector.empty), ContextReference("review-result", "1")))
      accepted.stage shouldBe "Accepted"
      accepted.reviewReference shouldBe Some(ContextReference("review-result", "1"))
    }

    "preserve the ten-batch ceiling even when the ordinary-count classification is exempt" in {
      val exhausted = selected.copy(stage = "Repair", automaticCount = 10, ordinaryCount = 0)
      val work = S.Work("fixture-session", "12", "Repair", exhausted)
      val result = S.Result("completed", "Local hygiene fix", Vector.empty,
        Vector(S.Condition("behavior", true, "Selected condition addressed")), Vector.empty,
        fixAssessment = Some(GoalPhaseFixAssessment.Report(
          GoalPhaseFixAssessment.Change("HYGIENE", Vector("one.unit"), false, false, "Local hygiene", false, false),
          GoalPhaseFixAssessment.Convergence("PROGRESSING", "HIGH", "Actual failure addressed"))))
      S.completedC(work, result, ContextReference("result", "1")) shouldBe a[Consequence.Failure[?]]
      S.startC(input.copy(repairLimit = 11), "human", checks) shouldBe a[Consequence.Failure[?]]
    }

    "refuse crossed ownership and keep initially passing checks separate from semantic repair acceptance" in {
      S.candidateC(start, candidate.copy(paths = Vector("unrelated/file.scala"))) shouldBe a[Consequence.Failure[?]]
      val unchanged = passed(selected)
      unchanged.stage shouldBe "NoChange"
      unchanged.semanticReference shouldBe None
      unchanged.automaticCount shouldBe 0
    }

    "reuse applicable successful validation for an unchanged reviewed candidate without a second check intent" in {
      Given("the unchanged candidate with its previously retained complete successful batch")
      val reviewed = passed(take(S.candidateC(repaired.copy(unresolvedQuestions = Vector("Unsettled meaning")), candidate)))
      val inspection = reviewed.copy(stage = "Inspect", unresolvedQuestions = Vector.empty)
      When("candidate reuse returns an assessment checkpoint before ordinary assessment")
      val retained = take(S.candidateC(inspection, candidate))
      val accepted = take(S.assessC(retained))
      Then("the existing receipt batch is retained and assessment issues no second check intent")
      retained.stage shouldBe "Assess"
      accepted.stage shouldBe "Accepted"
      retained.receipts shouldBe reviewed.receipts
      S.intentC(retained) shouldBe a[Consequence.Failure[?]]
    }

    "deliver the shared reasoned diagnostic selection and cache same-call owned source reads" in {
      val failed = observe(selected, receipt(checks.head, false, "[error] retained exact diagnostic"))
      val context = IncrementalSbtDiagnosisInformation.WorkContext(input.ownedPaths,
        Some(IncrementalSbtDiagnosisInformation.FailureContext("actual-source-12", S.snapshot(candidate), checks, failed.receipts)),
        Some(S.snapshot(candidate)))
      var reads = 0
      val reader = new IncrementalSbtDiagnosisInformation.SourceReader {
        def readC(path: String) = { reads += 1; Consequence.success(Some("actual selected source fixture")) }
      }
      val selections = Vector(
        IncrementalSbtDiagnosisInformation.Selection("retained", "full-output", "Read actual retained error", Some("compile"), Some("stdout"), None, None),
        IncrementalSbtDiagnosisInformation.Selection("source-one", "source-file", "Check retained source context", None, None, None, Some("src/main/scala/Example.scala")),
        IncrementalSbtDiagnosisInformation.Selection("source-two", "source-file", "Resolve another local question", None, None, None, Some("src/main/scala/Example.scala")),
        IncrementalSbtDiagnosisInformation.Selection("excluded", "source-file", "An excluded source request", None, None, None, Some("unrelated/Secret.scala")))
      val delivered = take(IncrementalSbtDiagnosisInformation.itemsC(context, selections, reader))
      delivered.head.text shouldBe Some("[error] retained exact diagnostic")
      delivered.last.status shouldBe "unavailable"
      reads shouldBe 1
      failed.automaticCount shouldBe 0
    }

    "round-trip retained state without a second executor observation or a new repair allowance" in {
      val state = repaired
      val restored = state.asJson.as[S.State].fold(e => fail(e.message), identity)
      restored shouldBe state
      restored.automaticCount shouldBe 1
      restored.repairControl.policy shouldBe GoalPhaseRuntimeRepairControl.version
    }

    "expose a separately derived standalone business definition and preserve the original public routes" in {
      val definition = take(StandaloneFixRuntimeCatalog.loadC())
      definition.workflowIdentity.value shouldBe "StandaloneFixWorkflow"
      definition.workflowRevision.value shouldBe "standalone-fix-business-operation-v1"
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should contain("StandaloneFixService.startStandaloneFix")
      ThreeWorkflowLauncherAssembly.publishedMcpOperations should contain("GoalPhaseService.startGoalPhase")
      val compiled = domain.statemachine.workflow.StandaloneFixWorkflowStateMachineWorkflow4.workflow
      compiled.actions.find(_.identity == "ReviewStandaloneFix").map(_.kind) shouldBe Some("JUDGMENT")
      compiled.actions.find(_.identity == "AdmitStandaloneFixReview").map(_.kind) shouldBe Some("ADMISSION")
      compiled.requiredOperations.map(_.identity.capability).toSet shouldBe Set("standalone-fix-repair-capability", "standalone-fix-review-capability")
      compiled.requiredOperations.exists(_.operation.operation.toLowerCase.contains("commit")) shouldBe false
    }
  }
}
