package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.*
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E
import SplitPhaseExplicitPartitionFixture.take

/** Actual typed Git in the normal bootstrap/current Component and UoW.
  * Fixed ProcessBuilder setup creates inputs only, never Workflow history.
  * Producer decoder/security examples are reused rather than duplicated here.
  * @since   Oct.  6, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncManagedGitObservationSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private def _fresh(): Path = Files.createTempDirectory(Files.createDirectories(
    Path.of("target/sm-workflow/work/repository-git").toAbsolutePath.normalize), "episode-")
  private def target(root: Path) = Programs.Target(root.resolve("repository"), "main", "origin", "main",
    root.resolve("upstream.git").toString)
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    TypedGitHostPlanFixture.plan(target(root), denied = if (scenario == "NoGrant")
      Set(TypedGitHostPlanFixture.capability(GitOperationKind.Head)) else Set.empty,
      commitIdentity = scenario != "NoIdentity")
  override protected def _setup_files_(root: Path, scenario: String, index: Int): Unit = ()
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    val repository = root.resolve("repository")
    val endpoint = root.resolve("upstream.git").toString
    val peer = root.resolve("peer")
    val commands = Vector(
      Vector("init", "--bare", "--initial-branch=main", endpoint),
      Vector("init", "--initial-branch=main", repository.toString),
      Vector("-C", repository.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
        "commit", "--allow-empty", "-m", "fixture initial"),
      Vector("-C", repository.toString, "push", endpoint, "HEAD:refs/heads/main"),
      Vector("clone", "--no-local", endpoint, peer.toString),
      Vector("-C", peer.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
        "commit", "--allow-empty", "-m", "fixture remote successor"),
      Vector("-C", peer.toString, "push", endpoint, "HEAD:refs/heads/main"),
      Vector("-C", repository.toString, "add", "--", "staged name\nwith newline"),
      Vector("-C", repository.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
        "commit", "--allow-empty", "-m", "fixture local successor"),
      Vector("init", "--initial-branch=main", peer.toString),
      Vector("-C", peer.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
        "commit", "--allow-empty", "-m", "unrelated fixture root"),
      Vector("-C", peer.toString, "push", endpoint, "HEAD:refs/heads/main"))
    commands
  }
  private def _value_c[A](core: ActionCall.Core, operation: GitOperation[A]): Consequence[A] =
    E.executeGitC(core, operation).flatMap(_.valueC)
  private def oid(value: String): GitObjectId = take(GitObjectId.parseC(value))
  private def _main_branch: GitBranchName = take(GitBranchName.parseC("main"))

  "Typed managed Git observations and application history policy" should {
    "preserve literal dirty paths through fetch and a normal host reopen" in {
      Given("owned local/peer/bare inputs and a normal V3 host with actual component grants")
      val root = _fresh()
      var retained = Option.empty[G.Observed]
      _with_runtime_(root, "Observation") { (_, _, _, prepare) =>
        prepare(call => call.setupC(4))
        val staged = root.resolve("repository/staged name\nwith newline")
        val untracked = root.resolve("repository/untracked name")
        Files.writeString(staged, "staged bytes\n")
        prepare(call => call.setupC(8, startIndex = 7))
        Files.writeString(staged, "unstaged successor bytes\n")
        Files.writeString(untracked, "untracked bytes\n")
        When("the owning component obtains typed facts before and after fixed Fetch")
        prepare(call => for {
          before <- G.observeC(call.core)
          _ <- _value_c(call.core, GitOperation.Fetch)
          equal <- G.observeC(call.core)
          _ = {
            Then("tracking becomes equal while HEAD tree and exact dirty filenames remain")
            before.trackingTip shouldBe None
            before.counts shouldBe None
            equal.trackingTip shouldBe Some(before.head)
            equal.counts shouldBe Some(G.Counts(0, 0))
            equal.mergeBase shouldBe Some(before.head)
            equal.head shouldBe before.head
            equal.tree shouldBe before.tree
            equal.dirty shouldBe before.dirty
            equal.dirty.map(_.path) should contain("staged name\nwith newline")
          }
          _ <- call.setupC(7, startIndex = 4)
          _ <- _value_c(call.core, GitOperation.Fetch)
          remote <- G.observeC(call.core)
          _ = {
            Then("actual remote-ahead history preserves dirty local contents")
            remote.counts shouldBe Some(G.Counts(0, 1))
            remote.head shouldBe before.head
            remote.dirty shouldBe before.dirty
          }
          _ <- call.setupC(9, startIndex = 8)
          diverged <- G.observeC(call.core)
          _ = {
            Then("an independent local commit creates actual divergence at the retained ancestor")
            diverged.counts shouldBe Some(G.Counts(1, 1))
            diverged.mergeBase shouldBe Some(before.head)
            retained = Some(diverged)
          }
        } yield ())
        Files.readString(staged) shouldBe "unstaged successor bytes\n"
        Files.readString(untracked) shouldBe "untracked bytes\n"
        Files.exists(root.resolve("repository/.git/FETCH_HEAD")) shouldBe false
      }
      Given("the original runtime is closed and the Git state is retained")
      _with_runtime_(root, "Observation") { (_, _, _, prepare) =>
        When("a fresh normal runtime observes without Fetch or input setup")
        prepare(call => G.observeC(call.core).map { actual =>
          Then("the same graph and dirty contents survive without repeating an effect")
          actual shouldBe retained.get
          Files.readString(root.resolve("repository/staged name\nwith newline")) shouldBe "unstaged successor bytes\n"
          Files.readString(root.resolve("repository/untracked name")) shouldBe "untracked bytes\n"
          ()
        })
      }
    }

    "map real local-ahead and diverged history through their retained common ancestor" in {
      Given("owned repositories with the same actual initial commit")
      val root = _fresh()
      _with_runtime_(root, "Observation") { (_, _, _, prepare) =>
        prepare(call => call.setupC(4))
        When("local and remote fixture inputs advance independently and typed Fetch observes the remote")
        prepare(call => for {
          _ <- _value_c(call.core, GitOperation.Fetch)
          initial <- G.observeC(call.core)
          _ <- call.setupC(9, startIndex = 8)
          ahead <- G.observeC(call.core)
          _ = {
            Then("local-ahead is based on actual native counts and the initial ancestor")
            ahead.counts shouldBe Some(G.Counts(1, 0))
            ahead.mergeBase shouldBe Some(initial.head)
          }
          _ <- call.setupC(7, startIndex = 4)
          _ <- _value_c(call.core, GitOperation.Fetch)
          diverged <- G.observeC(call.core)
          _ = {
            Then("both descendants remain and application relation mapping reports divergence")
            diverged.counts shouldBe Some(G.Counts(1, 1))
            diverged.mergeBase shouldBe Some(initial.head)
            diverged.head shouldBe ahead.head
          }
        } yield ())
      }
    }

    "refuse actual unrelated histories without changing the local tip" in {
      Given("independently initialized owned roots and a fixed nonforce remote")
      val root = _fresh()
      _with_runtime_(root, "Observation") { (_, _, _, prepare) =>
        prepare(call => call.setupC(3).flatMap(_ => call.setupC(12, startIndex = 9)))
        When("typed Fetch obtains the unrelated root and application ancestry policy evaluates it")
        prepare(call => for {
          before <- G.observeC(call.core)
          _ <- _value_c(call.core, GitOperation.Fetch)
          head <- _value_c(call.core, GitOperation.Head)
          _ = {
            Then("automatic integration is refused and the original HEAD stays intact")
            G.observeC(call.core).isSuccess shouldBe false
            head.value shouldBe before.head
            Files.exists(root.resolve("repository/.git/MERGE_HEAD")) shouldBe false
          }
        } yield ())
      }
    }

    "fast-forward only the observed typed commit and preserve it after reopen" in {
      Given("an actual remote-ahead graph under a normal current-UoW host")
      val root = _fresh()
      var selected = ""
      _with_runtime_(root, "Observation") { (_, _, _, prepare) =>
        prepare(call => call.setupC(7))
        When("typed FastForward consumes the exact fetched commit")
        prepare(call => for {
          _ <- _value_c(call.core, GitOperation.Fetch)
          before <- G.observeC(call.core)
          _ = before.counts shouldBe Some(G.Counts(0, 1))
          tip <- GitObjectId.parseC(before.trackingTip.get)
          _ <- _value_c(call.core, GitOperation.FastForward(tip))
          after <- G.observeC(call.core)
          _ = {
            Then("HEAD is the selected commit with no pending merge or dirty state")
            selected = tip.value
            after.head shouldBe selected
            after.counts shouldBe Some(G.Counts(0, 0))
            after.dirty shouldBe empty
            Files.exists(root.resolve("repository/.git/MERGE_HEAD")) shouldBe false
          }
        } yield ())
      }
      Given("the completed effect host has closed")
      _with_runtime_(root, "Observation") { (_, _, _, prepare) =>
        When("ordinary typed observations run in a fresh runtime")
        prepare(call => G.observeC(call.core).map { actual =>
          Then("the same selected tip remains without another integration")
          actual.head shouldBe selected
          actual.counts shouldBe Some(G.Counts(0, 0))
          ()
        })
      }
    }

    "prepare then commit a clean pending merge using only the fixed host identity" in {
      Given("actual divergent descendants and a host-selected commit identity")
      val root = _fresh()
      _with_runtime_(root, "Observation") { (_, _, _, prepare) =>
        prepare(call => call.setupC(7).flatMap(_ => call.setupC(9, startIndex = 8)))
        When("typed PendingMerge and CommitPending execute in order")
        prepare(call => for {
          _ <- _value_c(call.core, GitOperation.Fetch)
          before <- G.observeC(call.core)
          _ <- _value_c(call.core, GitOperation.PendingMerge(oid(before.trackingTip.get)))
          pending <- G.inspectC(call.core)
          _ = pending.mergeHead shouldBe before.trackingTip
          _ <- _value_c(call.core, GitOperation.CommitPending)
          after <- G.observeC(call.core)
          commit <- G.commitC(call.core, after.head)
          _ = {
            Then("the observed merge has both actual parents and no unresolved index")
            commit.parents shouldBe Vector(before.head, before.trackingTip.get)
            after.counts shouldBe Some(G.Counts(2, 0))
            after.mergeBase shouldBe before.trackingTip
            after.dirty shouldBe empty
          }
        } yield ())
      }
      Given("a new normal runtime with identity-requiring declarations absent")
      _with_runtime_(root, "NoIdentity") { (_, _, _, prepare) =>
        When("the component requests a merge without installed host identity")
        prepare(call => for {
          before <- G.observeC(call.core)
          refused = E.executeGitC(call.core, GitOperation.PendingMerge(oid(before.trackingTip.get)))
          after <- G.observeC(call.core)
          _ = {
            Then("admission refuses before mutation and no identity is invented")
            refused.isSuccess shouldBe false
            after shouldBe before
          }
        } yield ())
      }
    }

    "push only the fixed destination and retain a real nonforce refusal" in {
      Given("owned local-ahead history with an independently granted fixed remote destination")
      val root = _fresh()
      _with_runtime_(root, "Observation") { (_, _, _, prepare) =>
        prepare(call => call.setupC(4))
        When("typed Push publishes the selected local descendant")
        prepare(call => for {
          _ <- _value_c(call.core, GitOperation.Fetch)
          _ <- call.setupC(9, startIndex = 8)
          local <- G.observeC(call.core)
          _ <- _value_c(call.core, GitOperation.Push(oid(local.head), _main_branch))
          remote <- G.remoteTipC(call.core, "main")
          _ = {
            Then("the actual fixed remote receives that exact commit")
            local.counts shouldBe Some(G.Counts(1, 0))
            remote shouldBe local.head
          }
          _ <- call.setupC(7, startIndex = 4)
          advanced <- G.remoteTipC(call.core, "main")
          _ <- call.setupC(9, startIndex = 8)
          _ <- _value_c(call.core, GitOperation.Fetch)
          retained <- G.observeC(call.core)
          _ = When("a nonforce push meets actual divergence or names a different destination")
          refused <- E.executeGitC(call.core, GitOperation.Push(oid(retained.head), _main_branch))
          crossed = E.executeGitC(call.core, GitOperation.Push(oid(retained.head), take(GitBranchName.parseC("another"))))
          unchanged <- G.remoteTipC(call.core, "main")
          after <- G.observeC(call.core)
          _ = {
            Then("negative typed outcomes cannot become facts and both repository tips remain")
            retained.counts shouldBe Some(G.Counts(1, 1))
            refused.outcome shouldBe GitExecutionOutcome.Failed(1)
            refused.valueC.isSuccess shouldBe false
            crossed.isSuccess shouldBe false
            unchanged shouldBe advanced
            after.head shouldBe retained.head
          }
        } yield ())
      }
    }

    "map typed literal status and history facts without an application Git parser" in {
      Given("validated typed status entries with literal filenames and the complete CNCF conflict classification")
      val path = take(GitPath.parseC("new\nname"))
      val original = take(GitPath.parseC("old name"))
      val renamed = GitStatusEntry('R', ' ', path, Some(original))
      When("business values retain those typed fields and map nonnegative history counts")
      val dirty = G.DirtyPath(renamed.indexCode, renamed.worktreeCode, renamed.path.value, renamed.renamedFrom.map(_.value))
      val relations = Vector(GitHistoryCounts(0, 0), GitHistoryCounts(2, 0), GitHistoryCounts(0, 3), GitHistoryCounts(2, 3))
        .map(value => G.Counts(value.localAhead, value.remoteAhead).relation)
      Then("literal names survive and all conflict pairs use CNCF's classification")
      dirty shouldBe G.DirtyPath('R', ' ', "new\nname", Some("old name"))
      relations shouldBe G.Relation.values.toVector
      Vector("DD", "AU", "UD", "UA", "DU", "AA", "UU").foreach(pair =>
        G.DirtyPath(pair(0), pair(1), "conflict", None).unmerged shouldBe true)
      dirty.unmerged shouldBe false
    }

    "refuse absent current component grants and invalid business selections before execution" in {
      Given("a normal V3 host with workspace authority but no current Head process grant")
      val root = _fresh()
      _with_runtime_(root, "NoGrant") { (_, _, _, prepare) =>
        When("the component requests typed Head and out-of-bound semantic ordinals")
        prepare(call => {
          val head = E.executeGitC(call.core, GitOperation.Head)
          val selections = Vector(E.stageConflictC(call.core, 0), E.stageConflictC(call.core, 129),
            E.stageGoalCandidateC(call.core, 257), E.commitGoalStepC(call.core, 65))
          Then("each is refused without creating a Git repository or remote")
          head.isSuccess shouldBe false
          selections.foreach(_.isSuccess shouldBe false)
          GitObjectId.parseC("HEAD").isSuccess shouldBe false
          GitObjectId.parseC("--force").isSuccess shouldBe false
          GitObjectId.parseC("A" * 40).isSuccess shouldBe false
          Files.exists(root.resolve("repository/.git")) shouldBe false
          Files.exists(root.resolve("upstream.git")) shouldBe false
          Consequence.unit
        })
      }
    }
  }
}
