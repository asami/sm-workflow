package org.simplemodeling.textus.smworkflow.admission

import io.circe.syntax.*
import java.nio.file.{Files, Path}
import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.record.RecordPresentable
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseOrderedProgression as O
import GoalPhaseStepAssessment as A
import GoalPhaseNativeValidation as N
import WorkflowSbtValidation as T
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Actual disposable CLI/SBT/Git plus closed typed routing regressions.
  * Human/reviewer/triage inputs below are specification-owned, not real AI proof.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class Phase65ConditionalLintSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val sourcePath = "src/main/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriter.scala"
  private val base = O.Snapshot("a" * 40, "b" * 40)
  private val candidate = base.copy(tree = "c" * 40)
  private val checks = Vector(N.Check("compile", 1, T.Kind.Compile), N.Check("focused", 2, T.Kind.TestOnly),
    N.Check("scala-lint", 3, T.Kind.FixCheck))
  private val uncertainty = A.Observation("boundary-meaning", ContextReference("declared-contract", "1"),
    "Does the changed boundary retain its next consumer's guarantee?", "immediate", "The guarantee is needed before continuing", None)
  private def policy(immediate: Boolean = false, mandatory: Boolean = false) =
    A.Policy("managed-lifecycle-syntax-v1", "code", if (mandatory) "required" else "conditional", "required", "off",
      if (immediate) Vector(uncertainty) else Vector.empty, "conditional")
  private def plan(p: A.Policy) = O.Plan("conditional-plan", "1", "6.5", "specification-owned",
    Vector(O.Step("S1", "Retain the selected behavior", Vector(sourcePath), Vector.empty,
      checks.map(_.identity), actionPolicy = Some(p))), Vector("focused"))
  private def validation(p: A.Policy) = {
    val start = take(O.startC(plan(p), base))
    val implemented = take(start.acceptC(O.SemanticCompleted(start.next.asInstanceOf[O.Semantic].key, "implemented")))
    take(implemented.acceptC(O.CandidateObserved(candidate)))
  }
  private def receipts = checks.map(c => RepositorySyncNativeMergeValidation.Receipt(c.identity, "exited", Some(0), "actual-shaped", "",
    Some(T.Facts(c.kind, T.Outcome.Succeeded, Option.when(c.kind == T.Kind.TestOnly)(T.Selection(Vector("example.BehaviorSpec"), 1, 2)), None))))
  private def payload = N.Payload("8", "S1", candidate, checks, receipts, Some("passed"))
  private def assessed(p: A.Policy) = {
    val state = validation(p)
    val assessment = take(A.assessC(state, payload, "native-validation"))
    take(N.observedStateC(state, payload.copy(assessment = Some(assessment)), "native-validation")) -> assessment
  }
  private def json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def executions(s: F.Scenario): Vector[String] = Files.readAllLines(s.root.resolve("sbt-executions.txt")).asScala.toVector
  private def file(s: F.Scenario, name: String, value: RecordPresentable): String = {
    val path = s.root.resolve(name + ".json"); Files.writeString(path, json(value)); path.toUri.toASCIIString
  }
  private def author(s: F.Scenario, finalizers: String = ""): Unit = {
    val root = Path.of(s.host.target.root)
    Files.writeString(root.resolve(sourcePath), "package org.simplemodeling.textus.smworkflow.p62authored\n" +
      "object LineWriter { def render(value: String): String = value.reverse + \"\\n\" }\n" + finalizers)
    val test = root.resolve("src/test/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriterSpec.scala")
    Files.createDirectories(test.getParent)
    Files.writeString(test, """package org.simplemodeling.textus.smworkflow.p62authored
      |import org.scalatest.wordspec.AnyWordSpec
      |import org.scalatest.matchers.should.Matchers
      |final class TestScopeOnlyFinalizer { override protected def finalize(): Unit = () }
      |final class LineWriterSpec extends AnyWordSpec with Matchers {
      |  "LineWriter" should {
      |    "reverse nonempty text and retain LF" in { LineWriter.render("abc") shouldBe "cba\n" }
      |    "retain an empty line" in { LineWriter.render("") shouldBe "\n" }
      |  }
      |}
      |""".stripMargin)
  }
  private def result(request: V.GoalPhaseAdvanceResult, outcome: String, summary: String,
    observations: Vector[String] = Vector.empty): V.GoalPhaseWorkResult = {
    val issues = if (request.work.get.kind == "RepairStep") request.work.get.issues.map(value =>
      V.GoalPhaseIssueReport(value.issues.filter(_.status != "RESOLVED").map(issue =>
        V.GoalPhaseIssueDisposition(issue.identity, "FIXED", summary, None)))) else None
    V.GoalPhaseWorkResult(outcome, summary, observations, None, None, issues, None, None, None, None, None)
  }
  private def submit(s: F.Scenario, request: V.GoalPhaseAdvanceResult, value: V.GoalPhaseWorkResult, name: String) =
    s.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--result", json(value), "--resultReference", json(V.WorkflowArtifactReference(name, "1"))))
  private def close(s: F.Scenario, task: Path, wait: F.Result, name: String): V.GoalPhaseAdvanceResult = {
    val input = V.WorkflowAuthorityPreparationInput(wait.invocation.stdout.toUri.toASCIIString,
      file(s, name + "-authority", V.WorkflowArtifactReference(name + "-closure", "1")), "participant",
      "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence")
    val prepared = s.prepareHostInput("prepareAuthority", input.toRecord())
    prepared.exitCode shouldBe 0; s.selectPreparedImport(prepared)
    s.advance(task).business[V.GoalPhaseAdvanceResult]
  }

  private def review(s: F.Scenario, actual: F.Result, name: String): Unit = {
    val request = actual.business[V.GoalPhaseAdvanceResult]
    val value = result(request, "accept", "SPECIFICATION-OWNED independent review: the selected observation is harmless")
    val reference = V.WorkflowArtifactReference(name, "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-q3-reviewer"), None, None)
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", "review", actual.invocation.stdout.toUri.toASCIIString,
      file(s, name + "-result", value), file(s, name + "-reference", reference), file(s, name + "-execution", execution),
      "normal-development-host", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString, "entity:smworkflow.cli.host_evidence", None)
    val prepared = s.prepareHostInput("prepareEvidence", input.toRecord())
    withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
    s.selectPreparedImport(prepared)
    s.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--result", json(value), "--resultReference", json(reference), "--executionEvidence", json(execution)))
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
  }

  "Q3 typed conditional routing" should {
    "retain old policy bytes and required-review behavior rather than silently enabling triage" in {
      val old = policy(immediate = true).copy(semanticTriage = "off", scalaLint = "off")
      old.asJson.hcursor.downField("semanticTriage").focus shouldBe None
      take(A.assessC(validation(old), payload, "native-validation")).branch shouldBe "detailed-review"
      policy(mandatory = true, immediate = true).semanticTriage shouldBe "conditional"
      assessed(policy(mandatory = true, immediate = true))._1.next.asInstanceOf[O.Semantic].key.kind shouldBe O.WorkKind.ReviewStep
    }
    "require actual lint and nonempty behavioral receipts before any semantic route" in {
      val state = validation(policy())
      A.assessC(state, payload.copy(selected = checks.take(2), receipts = receipts.take(2)), "native-validation").isSuccess shouldBe false
      val lint = receipts.last
      Vector(lint.copy(exitCode = Some(1)), lint.copy(termination = "timed-out", exitCode = None),
        lint.copy(sbt = Some(T.Facts(T.Kind.FixCheck, T.Outcome.NotExecuted, None, None)))).foreach { invalid =>
        A.assessC(state, payload.copy(receipts = receipts.updated(2, invalid)), "native-validation").isSuccess shouldBe false
      }
      T.Facts(T.Kind.FixCheck, T.Outcome.Succeeded, None, None).valid shouldBe true
      T.Facts(T.Kind.FixCheck, T.Outcome.Succeeded, Some(T.Selection(Vector("example.BehaviorSpec"), 1, 2)), None).valid shouldBe false
    }
    "admit CLEAN without a fake review and escalate semantic SUSPICIOUS or DECLINE once" in {
      val (state, assessment) = assessed(policy(immediate = true))
      state.stage shouldBe O.Stage.Triage
      val key = state.next.asInstanceOf[O.Semantic].key
      val clean = GoalPhaseStepTriage.Judgment(ContextReference("actual-triage", "1"), "clean", "The selected guarantee is retained", Vector.empty)
      val admitted = take(state.acceptC(O.TriageAdmitted(key, assessment, clean)))
      admitted.stage shouldBe O.Stage.Closing
      admitted.reviewEvidence shouldBe None
      admitted.stepAdmission.get.assessment.triageJudgment shouldBe Some(clean)
      admitted.repairs shouldBe state.repairs
      admitted.acceptC(O.TriageAdmitted(key, assessment, clean)).isSuccess shouldBe false
      Vector(clean.copy(outcome = "suspicious", observations = Vector("Check the changed public result alternative")),
        clean.copy(outcome = "decline", summary = "Available scope does not settle the guarantee")).foreach { judgment =>
        val escalated = take(state.acceptC(O.TriageAdmitted(key, assessment, judgment)))
        escalated.next.asInstanceOf[O.Semantic].key.kind shouldBe O.WorkKind.ReviewStep
        escalated.stepAdmission shouldBe None
        escalated.repairs shouldBe state.repairs
      }
      GoalPhaseStepTriage.valid(clean.copy(outcome = "failed")) shouldBe false
      GoalPhaseStepTriage.valid(clean.copy(observations = Vector("unresolved"))) shouldBe false
    }
    "consume only complete explicit Operation/path/scenario joins without inventing coverage from partial metadata" in {
      val mapped = GoalPhaseStepTriage.Inventory(ContextReference("declared-operation-map", "1"), true,
        Vector(GoalPhaseStepTriage.ScenarioMapping("read", Vector(sourcePath), Vector("existing-read-scenario")),
          GoalPhaseStepTriage.ScenarioMapping("write", Vector("other.scala"), Vector.empty)))
      GoalPhaseStepTriage.signals(Vector(sourcePath), mapped).missing shouldBe Vector.empty
      GoalPhaseStepTriage.signals(Vector("other.scala"), mapped).missing shouldBe Vector("write")
      val partial = GoalPhaseStepTriage.signals(Vector("other.scala"), mapped.copy(complete = false))
      partial.status shouldBe "partial"
      partial.missing shouldBe Vector.empty
      val selected = policy().copy(scenarioInventory = Some(mapped))
      val actual = take(A.assessC(validation(selected), payload, "native-validation", Vector("other.scala")))
      actual.branch shouldBe "triage"
      actual.scenarioSignals.get.missing shouldBe Vector("write")
      A.validResult(actual) shouldBe true
      take(A.assessC(validation(selected), payload, "native-validation", Vector(sourcePath))).branch shouldBe "admit"
      val unavailable = policy().copy(scenarioInventory = None)
      assessed(unavailable)._2.scenarioSignals shouldBe None
    }
  }

  "Q3 actual Scala 3.3.8 lint and native progression" should {
    "retain a failed lint, scoped intentional exception, unrelated violation and ordinary FIX through commit" in {
      Given("an isolated normal SBT project and a specification-owned intentional-finalizer decision for One only")
      // Two actual failing candidates precede the final repaired candidate. Admit
      // that finite window in this fresh fixture Plan, never by resetting counters.
      val s = F.freshStepAssessment("p65-lint-fix", TypedSbtHostPlanFixture.normalExecutable, Some(policy()),
        lint = true, repairLimit = Some(3))
      val task = s.ownTask("lint-task"); val started = s.start().business[V.GoalPhaseStartResult]; s.retain(task, started)
      val initial = s.advance(task).business[V.GoalPhaseAdvanceResult]
      val one = "final class One { override protected def finalize(): Unit = () }\n"
      val two = "final class Two { override protected def finalize(): Unit = () }\n"
      author(s, one + two)
      val beforeLint = Files.readString(Path.of(s.host.target.root).resolve(sourcePath))
      submit(s, initial, result(initial, "completed", "SPECIFICATION-OWNED deliberate selected violations"), "lint-implementation")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val first = s.advance(task).business[V.GoalPhaseAdvanceResult]
      first.work.get.kind shouldBe "RepairStep"
      first.work.get.checkFailure.get.validation.checks.last.sbt.get.kind shouldBe "fix-check"
      first.work.get.checkFailure.get.validation.checks.last.exitCode.exists(_ != 0) shouldBe true
      // Source history is prepared before candidate capture and checks. Compare
      // with that actual checked tree, retaining the complete file bytes/text.
      val checkedSource = s.candidateSource(first.work.get.checkFailure.get.validation.candidate.tree, sourcePath)
      checkedSource should endWith(beforeLint)
      Files.readString(Path.of(s.host.target.root).resolve(sourcePath)) shouldBe checkedSource
      val diagnostic = first.work.get.checkFailure.get.validation.checks.last
      (diagnostic.stdout + diagnostic.stderr) should include("LineWriter.scala")
      val firstHistory = first.work.get.issues.get.history
      When("the fixture applies only the existing test-owned decision with standard rule-scoped suppression")
      val suppressed = "// scalafix:off DisableSyntax.noFinalize; SPECIFICATION-OWNED decision: preserve One's deliberate interop fixture only\n" +
        one + "// scalafix:on DisableSyntax.noFinalize\n"
      author(s, suppressed + two)
      submit(s, first, result(first, "completed", "SPECIFICATION-OWNED intentional exception for One; Two remains independently checked"), "lint-exception")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val second = s.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the unrelated unsuppressed finalizer still fails and old failure/repair history remains")
      second.work.get.kind shouldBe "RepairStep"
      second.repairProgress.get.automaticCount shouldBe 2
      second.repairProgress.get.limit shouldBe 3
      val secondLint = second.work.get.checkFailure.get.validation.checks.last
      (secondLint.stdout + secondLint.stderr) should include("final class Two")
      (secondLint.stdout + secondLint.stderr) should not include "final class One"
      (secondLint.stdout + secondLint.stderr) should not include "UnusedScalafixSuppression"
      second.work.get.issues.get.history.take(firstHistory.size) shouldBe firstHistory
      author(s, suppressed)
      submit(s, second, result(second, "completed", "Remove Two's unintended finalizer; retain original One exception and source reason"), "lint-correction")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val waiting = s.advance(task); val view = waiting.business[V.GoalPhaseAdvanceResult]
      view.kind shouldBe "Wait"; view.work shouldBe None
      view.validation.get.outcome shouldBe Some("passed")
      view.validation.get.checks.last.sbt.get.selection shouldBe None
      val count = executions(s).size
      val next = close(s, task, waiting, "lint-fix")
      next.work.get.completed.size shouldBe 1
      next.work.get.completed.head.admission.nonEmpty shouldBe true
      next.work.get.completed.head.review shouldBe None
      executions(s).size shouldBe count
    }
    "issue bounded triage from actual clean lint and retain CLEAN admission and Journal without check replay" in {
      val s = F.freshStepAssessment("p65-triage-clean", TypedSbtHostPlanFixture.normalExecutable,
        Some(policy(immediate = true)), lint = true)
      val task = s.ownTask("triage-task"); s.retain(task, s.start().business[V.GoalPhaseStartResult])
      val initial = s.advance(task).business[V.GoalPhaseAdvanceResult]; author(s)
      submit(s, initial, result(initial, "completed", "SPECIFICATION-OWNED changed boundary"), "triage-implementation")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val issued = s.advance(task).business[V.GoalPhaseAdvanceResult]
      issued.work.get.kind shouldBe "TriageStep"
      issued.issuedWorkContext.get.thinkingMode shouldBe "analysis"
      issued.work.get.triage.nonEmpty shouldBe true
      issued.work.get.review shouldBe None
      val count = executions(s).size
      val rejected = submit(s, issued, result(issued, "failed", "Transport failure is not semantic decline"), "triage-invalid")
      rejected.exitCode should not be 0
      val reopened = s.advance(task).business[V.GoalPhaseAdvanceResult]
      reopened.issuedWorkContext shouldBe issued.issuedWorkContext
      submit(s, issued, result(issued, "clean", "SPECIFICATION-OWNED triage: changed guarantee is retained"), "triage-clean")
        .business[V.GoalPhaseWorkSubmissionResult].journalRecording.get.status shouldBe "recorded"
      val waiting = s.advance(task)
      waiting.business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      val next = close(s, task, waiting, "triage-clean")
      next.work.get.completed.head.review shouldBe None
      take(next.work.get.completed.head.admission.get.toJsonStringC) should include("triage-clean")
      executions(s).size shouldBe count
      val recorded = s.findJournalEntries(V.JournalFindInput(s.plan.repository, Some(s.plan.phase),
        Some(next.sessionId), Some("StepAssessment"), None, None, None, Some(20))).business[V.JournalFindResult]
      recorded.entries.size shouldBe 2
    }
    Vector("suspicious", "decline", "mandatory").foreach { route =>
      s"connect $route to actual detailed review without another triage or successful-check replay" in {
        Given("an isolated lint-enabled project with either declared semantic uncertainty or mandatory review")
        val structural = GoalPhaseStepTriage.Inventory(ContextReference("declared-path-operation-map", "1"), true,
          Vector(GoalPhaseStepTriage.ScenarioMapping("render", Vector(sourcePath), Vector.empty)))
        val selected = if (route == "decline") policy().copy(scenarioInventory = Some(structural))
          else policy(immediate = true, mandatory = route == "mandatory")
        val s = F.freshStepAssessment("p65-triage-" + route, TypedSbtHostPlanFixture.normalExecutable, Some(selected), lint = true)
        val task = s.ownTask("triage-task"); s.retain(task, s.start().business[V.GoalPhaseStartResult])
        val initial = s.advance(task).business[V.GoalPhaseAdvanceResult]; author(s)
        submit(s, initial, result(initial, "completed", "SPECIFICATION-OWNED changed boundary"), "implementation-" + route)
          .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
        val first = s.advance(task)
        val issued = first.business[V.GoalPhaseAdvanceResult]
        val count = executions(s).size
        val reviewCall = if (route == "mandatory") {
          issued.work.get.kind shouldBe "ReviewStep"
          issued.work.get.triage shouldBe None
          issued.work.get.review.get.triageJudgment shouldBe None
          first
        } else {
          issued.work.get.kind shouldBe "TriageStep"
          if (route == "decline") take(issued.work.get.triage.get.toJsonStringC) should include("render")
          val observations = if (route == "suspicious") Vector("Examine the changed render guarantee") else Vector.empty
          submit(s, issued, result(issued, route, "SPECIFICATION-OWNED " + route + ": inspect the changed guarantee", observations), "triage-" + route)
            .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
          val escalated = s.advance(task)
          val view = escalated.business[V.GoalPhaseAdvanceResult]
          view.work.get.kind shouldBe "ReviewStep"
          view.work.get.review.get.triageJudgment.nonEmpty shouldBe true
          take(view.work.get.review.get.triageJudgment.get.toJsonStringC) should include("triage-" + route)
          view.work.get.issues.toVector.flatMap(_.issues) shouldBe Vector.empty
          view.repairProgress shouldBe issued.repairProgress
          s.advance(task).business[V.GoalPhaseAdvanceResult].issuedWorkContext shouldBe view.issuedWorkContext
          escalated
        }
        review(s, reviewCall, "independent-" + route)
        val waiting = s.advance(task)
        waiting.business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
        val next = close(s, task, waiting, "reviewed-" + route)
        next.work.get.completed.head.review.nonEmpty shouldBe true
        next.work.get.completed.head.admission shouldBe None
        executions(s).size shouldBe count
      }
    }

  }
}
