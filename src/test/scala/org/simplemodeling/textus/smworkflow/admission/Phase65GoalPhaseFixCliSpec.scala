package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.goldenport.record.RecordPresentable
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Actual public CLI/SBT/Git on an isolated specification-owned repository.
  * Fixture reviews and authority are explicit test data, not genuine AI proof.
  * @since   Oct. 24, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class Phase65GoalPhaseFixCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def occurredAt: String = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString
  private def save(s: F.Scenario, name: String, value: RecordPresentable): String = {
    val path = s.root.resolve(name + ".json")
    Files.writeString(path, json(value))
    path.toUri.toASCIIString
  }
  private def result(outcome: String, findings: Vector[String] = Vector.empty,
    report: Option[V.GoalPhaseIssueReport] = None): V.GoalPhaseWorkResult =
    V.GoalPhaseWorkResult(outcome, "Specification-owned assigned work", findings,
      None, None, report, None, None, None, None)
  private def submit(s: F.Scenario, request: V.GoalPhaseAdvanceResult, value: V.GoalPhaseWorkResult,
    ref: V.WorkflowArtifactReference, execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
    val response = s.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--result", json(value), "--resultReference", json(ref)) ++ execution.toVector.flatMap(e =>
      Vector("--executionEvidence", json(e)))).business[V.GoalPhaseWorkSubmissionResult]
    response.accepted shouldBe true
    response.outcome shouldBe "accepted"
    response.sessionId shouldBe request.sessionId
  }
  private def review(s: F.Scenario, actual: F.Result, value: V.GoalPhaseWorkResult, name: String): Unit = {
    val request = actual.business[V.GoalPhaseAdvanceResult]
    val ref = V.WorkflowArtifactReference(name, "1")
    val execution = V.WorkflowExecutionEvidence(Vector(ref), Some("specification-release-reviewer"), None, None)
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", "review", actual.invocation.stdout.toUri.toASCIIString,
      save(s, name + "-result", value), save(s, name + "-reference", ref), save(s, name + "-execution", execution),
      "normal-development-host", occurredAt, "entity:smworkflow.cli.host_evidence", None)
    val prepared = s.prepareHostInput("prepareEvidence", input.toRecord())
    withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
    s.selectPreparedImport(prepared)
    submit(s, request, value, ref, Some(execution))
  }
  private def authority(s: F.Scenario, actual: F.Result, name: String): Unit = {
    val input = V.WorkflowAuthorityPreparationInput(actual.invocation.stdout.toUri.toASCIIString,
      save(s, name + "-authority-reference", V.WorkflowArtifactReference(name, "1")), "participant",
      "normal-development-host", occurredAt, "entity:smworkflow.cli.host_evidence")
    val prepared = s.prepareHostInput("prepareAuthority", input.toRecord())
    withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
    s.selectPreparedImport(prepared)
  }
  private def executions(s: F.Scenario): Vector[String] =
    Files.readString(s.root.resolve("sbt-executions.txt")).linesIterator.toVector

  "P65-Q2 Step and Release FIX" should {
    Vector(false, true).foreach { recording =>
      s"repair a failed Step through Release FIX and Final with explicit observation recording=$recording" in {
        Given("a selected product input and its usage contract in one original Step with a bounded repair window")
        val s = F.freshReleaseFix(if (recording) "p65-release-fix-recording" else "p65-release-fix",
          TypedSbtHostPlanFixture.normalExecutable, recording)
        val task = s.ownTask("release-fix-task")
        val started = s.start().business[V.GoalPhaseStartResult]
        s.retain(task, started)
        val implementation = s.advance(task).business[V.GoalPhaseAdvanceResult]
        val original = implementation.work.get
        val repo = Path.of(s.host.target.root)
        Files.writeString(repo.resolve("value.txt"), "incorrect\n")
        Files.createDirectories(repo.resolve("docs"))
        Files.writeString(repo.resolve("docs/usage.md"), "The input accepts arbitrary text.\n")
        submit(s, implementation, result("completed"), V.WorkflowArtifactReference("fixture-implementation", "1"))
        When("the actual selected Step check fails and ordinary Advance issues the existing FIX")
        val stepRepair = s.advance(task).business[V.GoalPhaseAdvanceResult]
        stepRepair.work.get.kind shouldBe "RepairStep"
        stepRepair.work.get.stepIdentity shouldBe Some("step-1")
        stepRepair.work.get.checkFailure.get.validation.outcome shouldBe Some("repair-required")
        stepRepair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 1))
        executions(s).size shouldBe 1
        Files.writeString(repo.resolve("value.txt"), "expected\n")
        val stepIssues = stepRepair.work.get.issues.get.issues.filter(_.status != "RESOLVED")
        val stepFixed = V.GoalPhaseIssueReport(stepIssues.map(i => V.GoalPhaseIssueDisposition(i.identity,
          "FIXED", "Corrected the selected actual failed product input", None)))
        submit(s, stepRepair, result("completed", report = Some(stepFixed)), V.WorkflowArtifactReference("fixture-step-fix", "1"))
        val stepReview = s.advance(task)
        stepReview.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewStep"
        stepReview.business[V.GoalPhaseAdvanceResult].work.get.issues.get.issues.forall(_.status == "RESOLVED") shouldBe true
        review(s, stepReview, result("accept"), "fixture-step-review")
        val stepClosing = s.advance(task)
        stepClosing.business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
        authority(s, stepClosing, "fixture-step-closure")
        val phaseReview = s.advance(task)
        val integrated = phaseReview.business[V.GoalPhaseAdvanceResult]
        integrated.kind shouldBe "Request"
        integrated.work.get.kind shouldBe "ReviewPhase"
        val acceptedPrefix = integrated.work.get.completed
        acceptedPrefix.size shouldBe 1
        acceptedPrefix.head.commit.identity shouldBe s.candidateHead
        integrated.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 1))
        executions(s).size shouldBe 3

        When("the integrated reviewer identifies the existing required usage-contract mismatch")
        def observations(identity: String, category: String) = Some(V.GoalPhaseReviewObservations(Vector(
          V.GoalPhaseReviewObservation(identity, category, "Retained nonblocking observation", "Record after accepted Phase review"))))
        val originalReview = result("repair", Vector("docs/usage.md must state that the accepted input is exactly expected followed by LF"))
        review(s, phaseReview, if (recording) originalReview.copy(reviewObservations = observations("original-phase-note", "HYGIENE")) else originalReview,
          "fixture-integrated-review")
        val repairCall = s.advance(task)
        val repair = repairCall.business[V.GoalPhaseAdvanceResult]
        Then("ordinary Advance issues Release repair without a Human question new Plan or reopened Step")
        repair.kind shouldBe "Request"
        repair.work.get.kind shouldBe "RepairStep"
        repair.work.get.stepIdentity shouldBe None
        repair.work.get.selectedPlan shouldBe original.selectedPlan
        repair.work.get.originalBase shouldBe original.originalBase
        repair.work.get.completed shouldBe acceptedPrefix
        repair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 1))
        if (recording) {
          repair.findingRecording.get.pending.map(_.observation.identity) shouldBe Vector("original-phase-note")
          repair.findingRecording.get.request shouldBe None
        }
        repair.work.get.review.get.priorReviews.exists(r => take(r.toJsonStringC).contains("ReviewPhase")) shouldBe true
        val open = repair.work.get.issues.get.issues.filter(_.status != "RESOLVED")
        open.size shouldBe 1
        open.head.source shouldBe "REVIEW"
        s.candidateHead shouldBe acceptedPrefix.head.commit.identity
        executions(s).size shouldBe 3
        val repeated = s.advance(task).business[V.GoalPhaseAdvanceResult]
        repeated shouldBe repair

        Given("only the assigned release finding is corrected within the original ownership")
        Files.writeString(repo.resolve("docs/usage.md"), "The accepted input is exactly expected followed by LF.\n")
        val fixed = V.GoalPhaseIssueReport(open.map(i => V.GoalPhaseIssueDisposition(i.identity,
          "FIXED", "Corrected the exact accepted-input description; validation and review remain pending", None)))
        submit(s, repair, result("completed", report = Some(fixed)), V.WorkflowArtifactReference("fixture-release-fix", "1"))
        val reReviewCall = s.advance(task)
        val reReview = reReviewCall.business[V.GoalPhaseAdvanceResult]
        Then("actual fresh selected checks precede the affected review and preserve the original report")
        reReview.kind shouldBe "Request"
        reReview.work.get.kind shouldBe "ReviewPhase"
        reReview.work.get.completed shouldBe acceptedPrefix
        reReview.work.get.selectedPlan shouldBe original.selectedPlan
        reReview.work.get.review.get.priorReviews.exists(r => take(r.toJsonStringC).contains("ReviewPhase")) shouldBe true
        reReview.work.get.review.get.checks.head.exitCode shouldBe Some(0)
        executions(s).size shouldBe 4
        if (recording) {
          reReview.findingRecording.get.pending.map(_.sourceReference.identity) shouldBe Vector("fixture-integrated-review")
          reReview.findingRecording.get.request shouldBe None
          Files.readString(repo.resolve("docs/journal.md")) shouldBe F.findingRecordingBaseline.toMap.apply("docs/journal.md")
        }
        val covered = V.GoalPhaseIssueReport(open.map(i => V.GoalPhaseIssueDisposition(i.identity,
          "RESOLVED", "Scoped review confirms the usage contract now matches the unchanged product input", None)))
        val acceptedReview = result("accept", report = Some(covered))
        review(s, reReviewCall, if (recording) acceptedReview.copy(reviewObservations = observations("accepted-phase-note", "DEVELOPMENT_CANDIDATE"))
          else acceptedReview, "fixture-scoped-release-review")
        if (recording) {
          val assignedCall = s.advance(task)
          val assigned = assignedCall.business[V.GoalPhaseAdvanceResult]
          assigned.submitOperation shouldBe Some("submitGoalPhaseFindingRecording")
          val request = assigned.findingRecording.get.request.get
          request.stepIdentity shouldBe None
          request.items.map(_.sourceReference.identity) shouldBe Vector("fixture-integrated-review", "fixture-scoped-release-review")
          request.items.map(_.observation.identity) shouldBe Vector("original-phase-note", "accepted-phase-note")
          request.items.map(_.sourceKind) shouldBe Vector("ReviewPhase", "ReviewPhase")
          request.items.map(_.destination) shouldBe Vector("docs/journal.md", "docs/candidates.md")
          request.completed shouldBe acceptedPrefix
          executions(s).size shouldBe 4
          s.advance(task).business[V.GoalPhaseAdvanceResult] shouldBe assigned
          val items = request.items.map { item =>
            val path = repo.resolve(item.destination)
            val section = "## " + item.observation.identity
            Files.writeString(path, Files.readString(path) + "\n" + section + "\n\n" +
              item.sourceReference.identity + "@" + item.sourceReference.revision + ": " + item.observation.summary + "\n")
            V.GoalPhaseFindingRecordingItemResult(item.sourceReference, item.observation.identity, "recorded",
              Vector(V.GoalPhaseFindingSavedSection(item.destination, section)), "Appended the exact source and observation")
          }
          val reply = V.GoalPhaseFindingRecordingResult("completed", "Recorded both actual Phase sources", items)
          val ack = s.call("command", assigned.submitOperation.get, Vector("--sessionId", assigned.submitSessionId.get,
            "--result", json(reply), "--resultReference", json(V.WorkflowArtifactReference("fixture-phase-recording", "1"))))
            .business[V.GoalPhaseFindingRecordingSubmissionResult]
          ack.accepted shouldBe true
          val reviewedUsage = Files.readString(repo.resolve("docs/usage.md"))
          When("an owned release file changes after its independent acceptance")
          Files.writeString(repo.resolve("docs/usage.md"), "Unreviewed release content.\n")
          s.advance(task).exitCode should not be 0
          s.stageReleaseRecordingCounterexample()
          s.advance(task).exitCode should not be 0
          Then("both unstaged and staged corruption reject before a recording intent or Final commit")
          s.candidateHead shouldBe acceptedPrefix.head.commit.identity
          executions(s).size shouldBe 4
          Files.writeString(repo.resolve("docs/usage.md"), reviewedUsage)
          s.stageReleaseRecordingCounterexample()
        }
        val finalWait = s.advance(task)
        finalWait.business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
        finalWait.business[V.GoalPhaseAdvanceResult].repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 1))
        authority(s, finalWait, "fixture-final-closure")
        val completed = s.advance(task).business[V.GoalPhaseAdvanceResult]
        Then("Final creates a real single-parent local commit and reaches Terminal with unchanged Step history and repair use")
        completed.kind shouldBe "Terminal"
        completed.sessionId shouldBe started.sessionId
        completed.completion.get.completed shouldBe acceptedPrefix
        s.candidateParent shouldBe acceptedPrefix.head.commit.identity
        s.candidateHead should not be acceptedPrefix.head.commit.identity
        s.candidateIndex shouldBe ""
        executions(s).size shouldBe 4
        if (recording) {
          completed.findingRecording.get.pending shouldBe empty
          completed.findingRecording.get.completed.last.result.items.map(_.outcome) shouldBe Vector("recorded", "recorded")
          Vector("docs/journal.md", "docs/candidates.md").foreach(path =>
            Files.readString(repo.resolve(path)) should startWith (F.findingRecordingBaseline.toMap.apply(path)))
        }
        s.retrieve(task) shouldBe started.sessionId
        val retained = s.advance(task).business[V.GoalPhaseAdvanceResult]
        retained.kind shouldBe "Terminal"
        s.candidateHead shouldBe completed.completion.get.reviewedCandidate.head
        executions(s).size shouldBe 4
      }
    }
  }
}
