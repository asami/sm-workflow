package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import java.nio.file.Path
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V

/** Real retained completion, unrelated bytes and paired child behavior.
  * @since   Oct. 23, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhasePostCompletionSyncSpec extends AnyWordSpec with GivenWhenThen with GoalPhasePostCompletionSyncFixture {
  private val _observation = GoalPhaseFindingRecording.Observation("completion-observation", "HYGIENE",
    "Retain the accepted reviewer observation", "This nonblocking observation needs the ordinary finite recording disposition")
  private val _observations = GoalPhaseFindingRecording.Observations(Vector(_observation))
  "GoalPhase completion before best-effort sync" should {
    "wait for normal recording configuration when the accepted Phase contains a pending observation" in {
      Given("GPSYNC-01,05 / CB-01: accepted real Phase review with a nonblocking observation and no recording policy")
      val root = freshRoot("pending-phase-recording")
      episode(root, initialize = true) { e =>
        val identity = e.closeDevelopment(Some(_observations))._1
        val accepted = e.current(identity)
        val checks = phaseExecutions(root)
        When("ordinary local-policy Advance reaches the actual PhaseClosing frontier")
        val result = e.advance(identity)
        Then("the pending observation uses the normal recording Wait before any durable local completion")
        result.kind shouldBe "Wait"
        result.message should include("recording policy")
        result.findingRecording.get.pending.map(_.observation.identity) shouldBe Vector(_observation.identity)
        result.completion shouldBe None
        sameInstance(e.current(identity), accepted)
        accepted.lifecycle shouldBe Lifecycle.Active
        accepted.currentProgression shouldBe Some(ProgressionReference("phase.PhaseClosing"))
        phaseExecutions(root) shouldBe checks
        e.advance(identity) shouldBe result
      }
    }

    "finish normal automatic Phase Journal recording before completion and preserve its actual projection on reopen" in {
      object Journal extends GoalPhasePostCompletionSyncFixture {
        override protected def post_completion_parameters(root: Path): Map[String, String] =
          NativeWorkflowCliFixture.JournalHost(DevelopmentJournal.Policy("workflow",
            DataStore.CollectionId.EntityStore(EntityCollectionId("smworkflow", "cli", "development_journal")))).values
      }
      Given("GPSYNC-01,05 / CB-01: accepted Phase observations and an explicitly configured normal Journal create grant")
      val root = Journal.freshRoot("phase-journal-before-completion")
      var identity = ""
      var terminal = Option.empty[V.GoalPhaseAdvanceResult]
      var completed = Option.empty[InstanceRecord]
      Journal.episode(root, initialize = true) { e =>
        val accepted = e.closeDevelopment(Some(_observations))
        identity = accepted._1
        val checks = Journal.phaseExecutions(root)
        When("ordinary Advance reaches local completion through the normal finding and document frontier")
        val result = e.advance(identity)
        terminal = Some(result)
        completed = Some(e.current(identity))
        Then("one real Journal intent and outcome precede durable completion with no pending observation or accepted-work replay")
        result.kind shouldBe "Terminal"
        result.completion.get.reviewedCandidate.head shouldBe accepted._2.candidate.head
        result.findingRecording.get.pending shouldBe empty
        result.findingRecording.get.completed shouldBe empty
        result.findingRecording.get.journaled.flatMap(_.sources).map(_.observation.identity) shouldBe Vector(_observation.identity)
        result.findingRecording.get.journaled.flatMap(_.items).map(_.status) shouldBe Vector("recorded")
        val causes = completed.get.history.map(_.causation.value)
        causes.count(_ == "ordered-journal-findings-intent") shouldBe 1
        causes.count(_ == "ordered-journal-findings-result") shouldBe 1
        causes.indexOf("ordered-journal-findings-result") should be < causes.indexOf("ordered-local-completion:" + identity)
        completed.get.lifecycle shouldBe Lifecycle.Completed
        Journal.phaseExecutions(root) shouldBe checks
      }
      val checks = Journal.phaseExecutions(root)
      Journal.episode(root) { e =>
        When("the completed Session is reopened through ordinary Advance")
        val reopened = e.advance(identity)
        Then("the saved completion projects the same actual Journal batch without reconstructing semantic work or recording twice")
        reopened shouldBe terminal.get
        reopened.findingRecording shouldBe terminal.get.findingRecording
        sameInstance(e.current(identity), completed.get)
        Journal.phaseExecutions(root) shouldBe checks
      }
    }

    "retain an honest completed clerical disposition from an accepted Step through Phase completion and reopen" in {
      object Recording extends GoalPhasePostCompletionSyncFixture {
        private val _paths = Vector("value.txt", "docs/hygiene.md", "docs/candidates.md")
        override protected def _recovery_plan: GoalPhaseOrderedProgression.Plan = {
          val original = super._recovery_plan
          original.copy(steps = original.steps.map(_.copy(ownedPaths = _paths)))
        }
        override protected def recoveryHost(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
          val original = super.recoveryHost(root)
          original.copy(goal = original.goal.copy(candidatePaths = _paths))
        }
        override protected def post_completion_parameters(root: Path): Map[String, String] = Map(
          GoalPhaseFindingRecording.declaration.name -> io.circe.Json.obj(
            "schemaVersion" -> io.circe.Json.fromString(GoalPhaseFindingRecording.schemaVersion),
            "hygiene" -> io.circe.Json.fromString("docs/hygiene.md"),
            "developmentCandidate" -> io.circe.Json.fromString("docs/candidates.md")).noSpaces)
        override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
          super._prepare_sbt_build_(root, scenario)
          Files.createDirectories(root.resolve("repository/docs"))
          _paths.tail.foreach { path =>
            val file = root.resolve("repository").resolve(path)
            if (!Files.exists(file)) Files.writeString(file, "# Fixture recording destination\n")
          }
        }
        override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
          val commands = super._setup_commands_(root, scenario)
          commands.patch(commands.indexWhere(_.contains("commit")), Vector(Vector("-C", root.resolve("repository").toString,
            "--literal-pathspecs", "add", "--") ++ _paths.tail), 0)
        }
      }
      Given("GPSYNC-01,05 / CB-01: an accepted Step observation and a normal exact Markdown recording assignment")
      val root = Recording.freshRoot("completed-recording-projection")
      var identity = ""
      var terminal = Option.empty[V.GoalPhaseAdvanceResult]
      var completed = Option.empty[InstanceRecord]
      Recording.episode(root, initialize = true) { e =>
        identity = e.native.start().sessionId
        e.native.submitImplementation(e.advance(identity)).accepted shouldBe true
        val step = e.advance(identity)
        e.acceptStep(step, _observations)
        val assignment = e.advance(identity)
        assignment.submitOperation shouldBe Some("submitGoalPhaseFindingRecording")
        When("the real assigned worker submits an honest no-write failed disposition before Phase review")
        e.failRecording(identity).accepted shouldBe true
        val phase = e.advance(identity)
        phase.work.get.kind shouldBe "ReviewPhase"
        val accepted = e.acceptPhase(phase)
        val checks = Recording.phaseExecutions(root)
        val result = e.advance(identity)
        terminal = Some(result)
        completed = Some(e.current(identity))
        Then("the original accepted committed Phase completes and retains the actual clerical result without inventing a saved section")
        result.kind shouldBe "Terminal"
        result.completion.get.reviewedCandidate.head shouldBe accepted.candidate.head
        result.findingRecording.get.pending shouldBe empty
        result.findingRecording.get.completed.map(_.result.outcome) shouldBe Vector("failed")
        result.findingRecording.get.completed.head.result.items.flatMap(_.records) shouldBe empty
        Recording.phaseExecutions(root) shouldBe checks
      }
      val checks = Recording.phaseExecutions(root)
      Recording.episode(root) { e =>
        When("the completed parent is reopened")
        val reopened = e.advance(identity)
        Then("its saved actual completed recording projection survives unchanged without a clerical or Phase replay")
        reopened shouldBe terminal.get
        sameInstance(e.current(identity), completed.get)
        Recording.phaseExecutions(root) shouldBe checks
      }
    }

    "retain a completed parent while actual child conflict work requires a Human" in {
      object Conflict extends GoalPhasePostCompletionSyncFixture {
        override protected def recoveryHost(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
          val original = super.recoveryHost(root)
          original.copy(repository = original.repository.copy(conflicts = RepositorySyncNativeConflictInput.Policy(Set("value.txt"))))
        }
      }
      Given("GPSYNC-04,05: independently authorized exact local candidate and a divergent remote producing a real semantic conflict")
      val root = Conflict.freshRoot("child-human")
      var identity = ""
      var child = ""
      var pending = Option.empty[InstanceRecord]
      Conflict.episode(root, initialize = true) { e =>
        val accepted = e.closeDevelopment()
        identity = accepted._1
        e.authorize(accepted._2)
        e.divergeRemote()
        When("the completed Phase's one actual child reaches conflict work and its owner requests Human direction")
        val result = e.advance(identity)
        child = result.completion.get.synchronization.childSessionId.get
        result.kind shouldBe "Terminal"
        result.completion.get.synchronization.state shouldBe "request"
        val human = e.requestChildHuman(child)
        pending = Some(e.child(child))
        Then("the child owns the Human question and parent reopening only reports its actual separate state")
        human.kind shouldBe "Human"
        val reopened = e.advance(identity)
        reopened.kind shouldBe "Terminal"
        reopened.outcome shouldBe Some("completed")
        reopened.completion.get.synchronization.state shouldBe "human"
        reopened.completion.get.synchronization.childSessionId shouldBe Some(child)
        reopened.completion.get.synchronization.advanceOperation shouldBe Some("advanceRepositorySync")
        e.current(identity).lifecycle shouldBe Lifecycle.Completed
        sameInstance(e.child(child), pending.get)
      }
      Conflict.episode(root) { e =>
        When("the parent is opened again with the existing unanswered child Human question")
        val result = e.advance(identity)
        Then("no child question or Phase review is reissued and no child transition occurs")
        result.kind shouldBe "Terminal"
        result.completion.get.synchronization.state shouldBe "human"
        sameInstance(e.child(child), pending.get)
      }
    }

    "report a real failed child product check without reopening or replaying the accepted Phase" in {
      object Failed extends GoalPhasePostCompletionSyncFixture {
        override protected def recoveryHost(root: Path): ThreeWorkflowRuntimeParameters.Definition = {
          val original = super.recoveryHost(root)
          original.copy(repository = original.repository.copy(validation = RepositorySyncNativeMergeValidation.Policy(
            "post-completion-product-check", 1, "required", "Observe the independently changed external readiness input",
            Vector(RepositorySyncNativeMergeValidation.Check("phase-test", 2, WorkflowSbtValidation.Kind.Test)))))
        }
      }
      Given("GPSYNC-04: accepted real Phase Test then a changed external readiness input for the child's explicitly selected Test")
      val root = Failed.freshRoot("child-failed-test")
      var identity = ""
      var child = ""
      var failed = Option.empty[InstanceRecord]
      Failed.episode(root, initialize = true) { e =>
        val accepted = e.closeDevelopment()
        identity = accepted._1
        e.authorize(accepted._2)
        Files.delete(root.resolve("phase-ready"))
        When("the one paired child executes its real selected Test after durable Phase completion")
        val result = e.advance(identity)
        child = result.completion.get.synchronization.childSessionId.get
        failed = Some(e.child(child))
        Then("the actual failed receipt remains the child's outcome while the original Phase stays completed")
        result.kind shouldBe "Terminal"
        result.completion.get.synchronization.state shouldBe "failed"
        result.completion.get.repository shouldBe None
        result.completion.get.reviewedCandidate.head shouldBe accepted._2.candidate.head
        e.current(identity).lifecycle shouldBe Lifecycle.Completed
        Failed.phaseExecutions(root).size shouldBe 2
      }
      Failed.episode(root) { e =>
        When("ordinary parent Advance observes the retained known failed child")
        val result = e.advance(identity)
        Then("completion remains Terminal and neither Phase nor child Test is replayed")
        result.completion.get.synchronization.state shouldBe "failed"
        sameInstance(e.child(child), failed.get)
        Failed.phaseExecutions(root).size shouldBe 2
      }
    }
    "complete accepted development without authority and reopen without Phase checks" in {
      Given("GPSYNC-01,03,05: an isolated normal local-policy Start and accepted real Compile, Test, review and Step commit")
      val root = freshRoot("no-authority")
      var identity = ""
      var terminal = Option.empty[V.GoalPhaseAdvanceResult]
      var history = Vector.empty[HistoryEntry]
      episode(root, initialize = true) { e =>
        val (session, accepted) = e.closeDevelopment()
        identity = session
        When("ordinary Advance completes without any separately granted repository authority")
        val result = e.advance(identity)
        terminal = Some(result)
        Then("the original accepted candidate and complete Steps are Terminal while sync is explicitly deferred")
        result.kind shouldBe "Terminal"
        result.outcome shouldBe Some("completed")
        result.completion.get.repository shouldBe None
        result.completion.get.synchronization.state shouldBe "deferred"
        result.completion.get.synchronization.reason should include("authority")
        result.completion.get.reviewedCandidate.head shouldBe accepted.candidate.head
        result.completion.get.completed.map(_.commit.identity) shouldBe accepted.completed.map(_.commit.identity)
        e.current(identity).lifecycle shouldBe Lifecycle.Completed
        history = e.current(identity).history
      }
      val executions = phaseExecutions(root)
      episode(root) { e =>
        When("the completed Session is reopened through ordinary Advance")
        val reopened = e.advance(identity)
        Then("completion projects first with unchanged history and no accepted check replay")
        reopened shouldBe terminal.get
        e.current(identity).history shouldBe history
        phaseExecutions(root) shouldBe executions
      }
    }

    "preserve unrelated modified and untracked bytes while safely deferring FULL_STATE" in {
      Given("GPSYNC-02,07: accepted committed Phase development with independently prepared sync authority")
      val root = freshRoot("dirty-preservation")
      episode(root, initialize = true) { e =>
        val (identity, accepted) = e.closeDevelopment()
        e.authorize(accepted)
        val tracked = root.resolve("repository/value.txt")
        val untracked = root.resolve("repository/unrelated.bin")
        val trackedbytes = "unrelated later edit\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        val untrackedbytes = Array[Byte](0, 1, 2, -1, 10)
        Files.write(tracked, trackedbytes)
        Files.write(untracked, untrackedbytes)
        val before = e.observation()
        When("ordinary Advance completes the original accepted committed candidate")
        val result = e.advance(identity)
        val after = e.observation()
        Then("Phase completion is retained and no staging checkpoint stash or deletion changes unrelated work")
        result.kind shouldBe "Terminal"
        result.completion.get.synchronization.state shouldBe "deferred"
        result.completion.get.synchronization.reason should include("FULL_STATE")
        result.completion.get.synchronization.childSessionId shouldBe None
        result.completion.get.reviewedCandidate.head shouldBe accepted.candidate.head
        e.current(identity).lifecycle shouldBe Lifecycle.Completed
        after shouldBe before
        Files.readAllBytes(tracked).toVector shouldBe trackedbytes.toVector
        Files.readAllBytes(untracked).toVector shouldBe untrackedbytes.toVector
      }
    }

    "retain the original accepted candidate when a separate clean descendant is current" in {
      Given("GPSYNC-01,02: accepted Phase work and a separately accepted clean descendant commit")
      val root = freshRoot("accepted-descendant")
      episode(root, initialize = true) { e =>
        val (identity, accepted) = e.closeDevelopment()
        e.authorize(accepted)
        e.appendIndependentDescendant()
        val descendant = e.observation()
        descendant.head should not be accepted.candidate.head
        descendant.dirty shouldBe empty
        val executions = phaseExecutions(root)
        When("ordinary Advance completes the original accepted whole-Phase frontier")
        val result = e.advance(identity)
        Then("the reviewed candidate remains original and the different current HEAD safely defers synchronization")
        result.kind shouldBe "Terminal"
        result.completion.get.reviewedCandidate.head shouldBe accepted.candidate.head
        result.completion.get.synchronization.state shouldBe "deferred"
        result.completion.get.synchronization.reason should include("descendant")
        e.observation() shouldBe descendant
        phaseExecutions(root) shouldBe executions
      }
    }

    "surface a completion acknowledgement failure and recover only its exact missing Completed append" in {
      Given("GPSYNC-01,05: a durable Closed record whose completion acknowledgement fails before Completed")
      val root = freshRoot("completion-storage-boundary")
      var identity = ""
      var closed = Option.empty[InstanceRecord]
      val stop: String => Consequence[Unit] = point => if (point == "goal-local-completion-recorded")
        Consequence.stateConflict("fixture completion storage acknowledgement failed") else Consequence.unit
      episode(root, initialize = true, acknowledgement = Some(stop)) { e =>
        identity = e.closeDevelopment()._1
        When("ordinary Advance saves Closed but cannot confirm its completion preparation")
        val failed = e.native.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(identity))
        Then("the real error is returned and no successful parent or child completion is invented")
        failed.isSuccess shouldBe false
        failed.toString should include("completion storage acknowledgement failed")
        closed = Some(e.current(identity))
        closed.get.lifecycle shouldBe Lifecycle.Active
        closed.get.currentProgression shouldBe Some(ProgressionReference("phase.Closed"))
      }
      val executions = phaseExecutions(root)
      episode(root) { e =>
        When("ordinary Advance reopens the actual saved local completion")
        val result = e.advance(identity)
        val completed = e.current(identity)
        Then("only the exact missing native completion is appended and no accepted work is reissued")
        result.kind shouldBe "Terminal"
        completed.lifecycle shouldBe Lifecycle.Completed
        completed.history.dropRight(1) shouldBe closed.get.history
        completed.revision shouldBe closed.get.revision.next
        phaseExecutions(root) shouldBe executions
        e.advance(identity) shouldBe result
        sameInstance(e.current(identity), completed)
      }
    }

    "complete before the one authorized clean connected child and passively retain actual convergence" in {
      Given("GPSYNC-01,03: a clean exact accepted candidate, local bare remote and independently granted existing sync effects")
      val root = freshRoot("connected")
      var identity = ""
      var child = ""
      var completed = Option.empty[InstanceRecord]
      episode(root, initialize = true) { e =>
        val accepted = e.closeDevelopment()
        identity = accepted._1
        e.authorize(accepted._2)
        When("ordinary Advance durably completes Phase before invoking paired public RepositorySync Start and Advance")
        val result = e.advance(identity)
        child = result.completion.get.synchronization.childSessionId.get
        Then("both actual outcomes are completed and the original accepted reviewed candidate is preserved")
        result.kind shouldBe "Terminal"
        result.completion.get.synchronization.state shouldBe "succeeded"
        result.completion.get.repository.get.tip shouldBe accepted._2.candidate.head
        result.completion.get.reviewedCandidate.head shouldBe accepted._2.candidate.head
        e.child(child).lifecycle shouldBe Lifecycle.Completed
        completed = Some(e.current(identity))
        completed.get.lifecycle shouldBe Lifecycle.Completed
      }
      episode(root) { e =>
        val before = e.child(child)
        When("the completed parent is reopened")
        val result = e.advance(identity)
        Then("passive observation returns the actual existing convergence with no child progression")
        result.completion.get.synchronization.state shouldBe "succeeded"
        result.completion.get.synchronization.childSessionId shouldBe Some(child)
        sameInstance(e.child(child), before)
        sameInstance(e.current(identity), completed.get)
      }
    }

    "retain completion across before-Start and after-Start interruption without retry or child duplication" in {
      for (point <- Vector("goal-local-before-child-start", "goal-local-after-child-start")) {
        Given("GPSYNC-04,05: independently authorized clean follow-up interrupted at " + point)
        val root = freshRoot(point)
        var identity = ""
        var child = ""
        var childrecord = Option.empty[InstanceRecord]
        val stop: String => Consequence[Unit] = actual => if (actual == point)
          Consequence.stateConflict("fixture interruption " + point) else Consequence.unit
        episode(root, initialize = true, acknowledgement = Some(stop)) { e =>
          val accepted = e.closeDevelopment()
          identity = accepted._1
          e.authorize(accepted._2)
          When("the actual fresh call reaches the selected interruption boundary")
          val result = e.advance(identity)
          child = result.completion.get.synchronization.childSessionId.get
          childrecord = e.childOption(child)
          Then("the Phase is completed and the actual follow-up error remains unknown rather than success")
          result.kind shouldBe "Terminal"
          result.completion.get.synchronization.state shouldBe "unknown"
          result.completion.get.synchronization.reason should include(point)
          result.completion.get.repository shouldBe None
          e.current(identity).lifecycle shouldBe Lifecycle.Completed
          childrecord.nonEmpty shouldBe (point == "goal-local-after-child-start")
        }
        val executions = phaseExecutions(root)
        episode(root) { e =>
          val parent = e.current(identity)
          When("the same parent reopens twice through ordinary Advance")
          val first = e.advance(identity)
          val second = e.advance(identity)
          Then("the known child identity and its exact state remain unchanged with no blind Start or Advance")
          first.kind shouldBe "Terminal"
          second shouldBe first
          first.completion.get.synchronization.childSessionId shouldBe Some(child)
          first.completion.get.synchronization.state shouldBe
            (if (childrecord.isEmpty) "unknown" else "in-progress")
          val actualchild = e.childOption(child)
          actualchild.isDefined shouldBe childrecord.isDefined
          actualchild.foreach(actual => sameInstance(actual, childrecord.get))
          sameInstance(e.current(identity), parent)
          phaseExecutions(root) shouldBe executions
        }
      }
    }
  }
}
