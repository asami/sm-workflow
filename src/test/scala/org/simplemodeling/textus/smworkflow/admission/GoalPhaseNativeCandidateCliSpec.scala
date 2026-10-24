package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F

/** Normal isolated CLI regression with explicitly specification-owned semantics,
  * review attestations and local effect grants. No live Session or external AI.
  * @since   Oct. 21, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class GoalPhaseNativeCandidateCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeCallerExecutionFixture.json
  private final case class Episode(scenario: F.Scenario, task: Path,
    first: V.GoalPhaseAdvanceResult, second: V.GoalPhaseAdvanceResult, accepted: String)
  private def _executions(scenario: F.Scenario): Vector[String] = {
    val marker = scenario.root.resolve("sbt-executions.txt")
    if (Files.exists(marker)) Files.readString(marker).linesIterator.toVector else Vector.empty
  }
  private def _reference(name: String) = V.WorkflowArtifactReference("candidate-continuation-spec-" + name, "1")
  private def _result(outcome: String) = V.GoalPhaseWorkResult(outcome,
    "SPECIFICATION-OWNED selected candidate disposition", Vector.empty,
    None, None, None, None, None, None, None, None)
  private def _submit_call(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult, name: String,
    outcome: String, execution: Option[V.WorkflowExecutionEvidence]): F.Result =
    scenario.call("command", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--result", json(_result(outcome).toRecord()).noSpaces,
      "--resultReference", json(_reference(name).toRecord()).noSpaces) ++
      execution.toVector.flatMap(value => Vector("--executionEvidence", json(value.toRecord()).noSpaces)))
  private def _submit(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult, name: String,
    outcome: String = "completed", execution: Option[V.WorkflowExecutionEvidence] = None): V.GoalPhaseWorkSubmissionResult =
    _submit_call(scenario, request, name, outcome, execution).business[V.GoalPhaseWorkSubmissionResult]
  private def _save(scenario: F.Scenario, name: String, text: String): String = {
    val file = scenario.root.resolve(name + ".json")
    Files.writeString(file, text, UTF_8)
    file.toUri.toASCIIString
  }
  private def _prepare_review(scenario: F.Scenario, observed: F.Result, name: String): V.WorkflowExecutionEvidence = {
    val reference = _reference(name)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer-" + name), None, None)
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", "review", observed.invocation.stdout.toUri.toASCIIString,
      _save(scenario, name + "-result", json(_result("accept").toRecord()).noSpaces),
      _save(scenario, name + "-reference", json(reference.toRecord()).noSpaces),
      _save(scenario, name + "-execution", json(execution.toRecord()).noSpaces),
      "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", None)
    val prepared = scenario.prepareHostInput("prepareEvidence", input.toRecord())
    prepared.exitCode shouldBe 0
    scenario.selectPreparedImport(prepared)
    execution
  }
  private def _accept_review(scenario: F.Scenario, observed: F.Result, name: String): V.GoalPhaseAdvanceResult = {
    val request = observed.business[V.GoalPhaseAdvanceResult]
    val execution = _prepare_review(scenario, observed, name)
    _submit(scenario, request, name, "accept", Some(execution)).accepted shouldBe true
    request
  }
  private def _grant_step(scenario: F.Scenario, waiting: F.Result, name: String): Unit = {
    val result = waiting.business[V.GoalPhaseAdvanceResult]
    result.kind shouldBe "Wait"
    result.authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
    val input = V.WorkflowAuthorityPreparationInput(waiting.invocation.stdout.toUri.toASCIIString,
      _save(scenario, name + "-authority", json(_reference(name + "-authority").toRecord()).noSpaces),
      "participant", "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence")
    val prepared = scenario.prepareHostInput("prepareAuthority", input.toRecord())
    prepared.exitCode shouldBe 0
    scenario.selectPreparedImport(prepared)
  }
  private def _episode(name: String): Episode = {
    val scenario = F.fresh(name, sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), repairContinuation = true)
    scenario.prepareCandidateBaseline()
    val task = scenario.ownTask("candidate-continuation-task")
    val started = scenario.start().business[V.GoalPhaseStartResult]
    scenario.retain(task, started)
    val first = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    Files.writeString(Path.of(scenario.host.target.root).resolve("value.txt"), "expected\n")
    _submit(scenario, first, "first-implementation").accepted shouldBe true
    val reviewcall = scenario.advance(task)
    _accept_review(scenario, reviewcall, "first-review").work.get.kind shouldBe "ReviewStep"
    val waiting = scenario.advance(task)
    _grant_step(scenario, waiting, "first")
    val second = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    second.work.get.kind shouldBe "ImplementStep"
    second.work.get.stepIdentity shouldBe Some("step-2")
    second.work.get.completed.size shouldBe 1
    _executions(scenario).size shouldBe 1
    Episode(scenario, task, first, second, scenario.candidateHead)
  }
  private def _edit_second(episode: Episode): Unit = {
    val repository = Path.of(episode.scenario.host.target.root)
    Files.writeString(repository.resolve("docs/phase.md"), "# Owned native qualification candidate\n\nSecond selected contribution.\n")
    _submit(episode.scenario, episode.second, "second-implementation").accepted shouldBe true
  }

  "Normal native candidate continuation" should {
    "close an owned Step against actual integrated HEAD while retaining unrelated edits and scoped whole-Phase evidence" in {
      Given("an isolated ordinary Session with its first accepted Step and second implementation already issued")
      val episode = _episode("candidate-integrated")
      val scenario = episode.scenario
      val repository = Path.of(scenario.host.target.root)
      val original = episode.first.work.get.originalBase
      val accepted = episode.second.work.get.completed
      val integrated = scenario.integrateCandidateDocument(original.head)
      val user = repository.resolve("docs/user.md")
      val untracked = repository.resolve("docs/local-draft.md")
      Files.writeString(user, "Unrelated user edits remain byte-for-byte.\n")
      Files.writeString(untracked, "Untracked local draft remains untracked.\n")
      val userbytes = Files.readAllBytes(user).toVector
      val draftbytes = Files.readAllBytes(untracked).toVector
      _edit_second(episode)
      val beforechecks = _executions(scenario)
      When("ordinary Advance observes ancestry, stages fixed owned selections and runs the selected checks")
      val reviewcall = scenario.advance(episode.task)
      val review = reviewcall.business[V.GoalPhaseAdvanceResult]
      Then("independent ReviewStep sees only the selected Step delta with the actual integrated HEAD")
      review.work.get.kind shouldBe "ReviewStep"
      review.work.get.candidate.get.head shouldBe integrated
      review.work.get.originalBase shouldBe original
      review.work.get.completed shouldBe accepted
      review.work.get.review.get.changedPaths shouldBe Vector("docs/phase.md")
      review.work.get.review.get.diff should include("Second selected contribution")
      review.work.get.review.get.diff should not include "Imported main contribution"
      review.work.get.review.get.diff should not include "Unrelated user edits"
      scenario.candidateIndex shouldBe "docs/phase.md"
      _executions(scenario).size shouldBe beforechecks.size + 1
      review.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2))

      Given("the retained exact candidate/check receipts and a pending independent review request")
      When("a separate ordinary Advance reconstructs the accepted native prefix without resubmitting completed work")
      scenario.advance(episode.task).business[V.GoalPhaseAdvanceResult] shouldBe review
      Then("reconstruction starts no extra checks and leaves unrelated bytes and index ownership unchanged")
      _executions(scenario).size shouldBe beforechecks.size + 1
      Files.readAllBytes(user).toVector shouldBe userbytes
      Files.readAllBytes(untracked).toVector shouldBe draftbytes
      scenario.candidateIndex shouldBe "docs/phase.md"

      Given("specification-owned independent acceptance and an explicit local Step grant via normal host preparation")
      _accept_review(scenario, reviewcall, "second-review")
      val waiting = scenario.advance(episode.task)
      _grant_step(scenario, waiting, "second")
      When("ordinary Advance commits the selected Step and requests whole-Phase independent review")
      val phase = scenario.advance(episode.task).business[V.GoalPhaseAdvanceResult]
      Then("actual Git parent is the integrated HEAD and Phase context retains accepted Step evidence without imported work")
      scenario.candidateParent shouldBe integrated
      phase.work.get.kind shouldBe "ReviewPhase"
      phase.work.get.originalBase shouldBe original
      phase.work.get.completed.take(1) shouldBe accepted
      phase.work.get.completed.size shouldBe 2
      phase.work.get.review.get.changedPaths.toSet shouldBe Set("value.txt", "docs/phase.md")
      phase.work.get.review.get.diff should include("+expected")
      phase.work.get.review.get.diff should include("Second selected contribution")
      phase.work.get.review.get.diff should not include "Imported main contribution"
      phase.work.get.review.get.diff should not include "Unrelated user edits"
      phase.work.get.review.get.diff should not include "Untracked local draft"
      Files.readAllBytes(user).toVector shouldBe userbytes
      Files.readAllBytes(untracked).toVector shouldBe draftbytes
      scenario.candidateIndex shouldBe ""
      scenario.candidateStatus should include("M docs/user.md")
      scenario.candidateStatus should include("?? docs/local-draft.md")
      _executions(scenario).size shouldBe 3
      scenario.advance(episode.task).business[V.GoalPhaseAdvanceResult] shouldBe phase
      _executions(scenario).size shouldBe 3
    }

    "reject a foreign staged document before any selected staging or checks" in {
      Given("an accepted first Step and a selected second candidate alongside an unrelated staged document")
      val episode = _episode("candidate-foreign-index")
      val scenario = episode.scenario
      Files.writeString(Path.of(scenario.host.target.root).resolve("docs/user.md"), "Foreign staged user edit\n")
      scenario.stageForeignCandidateDocument()
      _edit_second(episode)
      val index = scenario.candidateIndex
      val checks = _executions(scenario)
      When("ordinary Advance attempts candidate inspection")
      val failed = scenario.advance(episode.task)
      Then("the exact foreign index remains untouched and no owned path or check is executed")
      failed.exitCode should not be 0
      failed.invocation.logs should include("selected Step ownership")
      scenario.candidateIndex shouldBe index
      index shouldBe "docs/user.md"
      _executions(scenario) shouldBe checks
      scenario.candidateHead shouldBe episode.accepted
    }

    "reject unrelated replacement history before staging or checks without rewriting accepted Steps" in {
      Given("an accepted first Step and an unrelated root history arranged only inside the isolated fixture")
      val episode = _episode("candidate-unrelated-history")
      val scenario = episode.scenario
      val replacement = scenario.replaceCandidateHistory()
      _edit_second(episode)
      val checks = _executions(scenario)
      When("ordinary Advance tries to prove a descendant path to the original accepted tip")
      val failed = scenario.advance(episode.task)
      Then("the bounded ancestry refusal occurs before selected staging or validation")
      failed.exitCode should not be 0
      failed.invocation.logs should (include("no common ancestor") or include("no bounded path"))
      scenario.candidateIndex shouldBe ""
      scenario.candidateHead shouldBe replacement
      _executions(scenario) shouldBe checks
    }

    "refuse review completion after actual HEAD or the global index drifts from the retained candidate" in {
      Vector("head", "index").foreach { drift =>
        Given("a retained second-Step review request with its exact candidate and successful check receipts: " + drift)
        val episode = _episode("candidate-drift-" + drift)
        val scenario = episode.scenario
        _edit_second(episode)
        val observed = scenario.advance(episode.task)
        val request = observed.business[V.GoalPhaseAdvanceResult]
        request.work.get.kind shouldBe "ReviewStep"
        val execution = _prepare_review(scenario, observed, "drift-review")
        val checks = _executions(scenario)
        if (drift == "head") scenario.moveCandidateHead(episode.first.work.get.originalBase.head)
        else {
          Files.writeString(Path.of(scenario.host.target.root).resolve("docs/user.md"), "Later staged foreign change\n")
          scenario.stageForeignCandidateDocument()
        }
        val index = scenario.candidateIndex
        val head = scenario.candidateHead
        When("normal Submit attempts to admit a supplied independent review against the drifted live frontier")
        val failed = _submit_call(scenario, request, "drift-review", "accept", Some(execution))
        Then("the retained proof remains historical while current preconditions refuse completion and preserve all actual data")
        failed.exitCode should not be 0
        failed.invocation.logs should include("selected check preconditions")
        scenario.candidateIndex shouldBe index
        scenario.candidateHead shouldBe head
        _executions(scenario) shouldBe checks
      }
    }
  }
}
