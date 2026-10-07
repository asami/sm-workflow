package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall}
import org.goldenport.cncf.config.RuntimeConfig
import org.goldenport.cncf.context.{ExecutionContext, GlobalContext, RuntimeContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkInterpreter, UnitOfWorkOp}
import org.goldenport.cncf.workarea.WorkAreaSpace
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.OperationResponse
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import RepositorySyncManagedGitObservation as Observation
import WorkflowManagedProcessExecution as Effects
import SplitPhaseExplicitPartitionFixture.take

/** Test-owned local Git only, with actual canonical admission/driver/UoW.
  * Setup capabilities are explicitly fixture-only. This is effect evidence,
  * not installed Start/advance, durable fetch intent or public convergence.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
final class RepositorySyncManagedGitObservationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val limits = ProcessExecutionLimits(Some(10000), Some(30000), Some(1000), Some(2097152),
    Some(131072), Some(16384), Some(1), Some(64), Some(1), Some(131072), Some(4194304))
  private def result(text: String, exit: Int = 0, truncated: Boolean = false) = ProcessExecutionResult(
    ProcessExecutionTermination.Exited(exit), ProcessExecutionCapture(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector,
      text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length.toLong, truncated),
    ProcessExecutionCapture(Vector.empty, 0L, false), Vector.empty, 0L, "git-observation-fixture")
  private def target(): Programs.Target = {
    val work = Files.createDirectories(Path.of("target/sm-workflow/work/repository-git").toAbsolutePath.normalize)
    val root = Files.createTempDirectory(work, "episode-")
    Programs.Target(root.resolve("local"), "main", "origin", "main", root.resolve("remote.git").toString)
  }
  private def withRuntime[A](definitions: Vector[ProcessProgramDefinition], grant: Boolean = true)
    (run: ActionCall.Core => A): A = {
    val policy = take(ProcessExecutionPolicy.createC(definitions))
    val admission = take(ProcessExecutionAdmission.createC(policy,
      if (grant) definitions.map(d => ProcessExecutionGrant(d.capability)) else Vector.empty))
    val driver = new LocalProcessExecutionDriver()
    GlobalContext.set(GlobalContext(WorkAreaSpace.create(RuntimeConfig.default)))
    val base = ExecutionContext.create()
    val scope = ScopeContext(ScopeKind.Runtime, "repository-git", None, base.observability,
      processexecutiondriveroption = Some(driver), processexecutionadmissionoption = Some(admission))
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val uow: UnitOfWork = new UnitOfWork(context)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(ScopeKind.Runtime, "repository-git-host", Some(scope), base.observability, None),
      unitofworksupplier = () => uow,
      unitofworkinterpreterfn = new (cats.~>[UnitOfWorkOp, Consequence]) {
        def apply[B](operation: UnitOfWorkOp[B]): Consequence[B] = new UnitOfWorkInterpreter(uow).interpret(operation)
      }, commitaction = work => { take(work.commit()); () }, abortaction = work => { take(work.abort()); () },
      disposeaction = work => { take(work.dispose()); () }, token = "repository-git-host")
    val action = new CommandAction {
      val request = Request(None, Some("RepositorySyncService"), "gitEffectFixture", Nil, Nil, Nil)
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = Consequence.stateConflict("test context only")
      }
    }
    try run(ActionCall.Core(action, context, None, None)) finally {
      try runtime.dispose() finally driver.close()
    }
  }
  /** Completely fixed setup vectors touching only this episode's owned local
    * repositories. These are never registered by product host assembly.
    */
  private def setupDefinitions(t: Programs.Target): Vector[ProcessProgramDefinition] = {
    val peer = t.root.getParent.resolve("peer")
    val commands = Vector(
      Vector("init", "--bare", "--initial-branch=main", t.endpoint),
      Vector("init", "--initial-branch=main", t.root.toString),
      Vector("-C", t.root.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
        "commit", "--allow-empty", "-m", "fixture initial"),
      Vector("-C", t.root.toString, "push", t.endpoint, "HEAD:refs/heads/main"),
      Vector("clone", "--no-local", t.endpoint, peer.toString),
      Vector("-C", peer.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
        "commit", "--allow-empty", "-m", "fixture remote successor"),
      Vector("-C", peer.toString, "push", t.endpoint, "HEAD:refs/heads/main"),
      Vector("-C", t.root.toString, "add", "--", "staged name\nwith newline"),
      Vector("-C", t.root.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
        "commit", "--allow-empty", "-m", "fixture local successor"),
      Vector("init", "--initial-branch=main", peer.toString),
      Vector("-C", peer.toString, "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
        "commit", "--allow-empty", "-m", "unrelated fixture root"),
      Vector("-C", peer.toString, "push", t.endpoint, "HEAD:refs/heads/main"))
    commands.zipWithIndex.map { (command, index) =>
      val capability = take(ProcessCapabilityId.parseC(s"fixture-git-$index"))
      val fixed = Vector("-c", "core.hooksPath=/dev/null", "-c", "credential.helper=") ++ command
      take(ProcessProgramDefinition.fromRuntimeC(capability, capability.print, "/usr/bin/git", fixed,
        ProcessArgumentPolicy(fixed, permittedArgumentVectors = Set(Vector.empty)), limits, Set.empty,
        environment = Map("PATH" -> "/usr/bin:/bin", "GIT_CONFIG_GLOBAL" -> "/dev/null", "GIT_CONFIG_NOSYSTEM" -> "1",
          "GIT_TERMINAL_PROMPT" -> "0", "LC_ALL" -> "C")))
    }
  }
  private def setup(contextCore: ActionCall.Core, index: Int): Unit = {
    final class SetupCall(val core: ActionCall.Core) extends ProcedureActionCall {
      def runC: Consequence[ProcessExecutionResult] = executeProgram(process_exec(ProcessExecutionRequest(
        take(ProcessCapabilityId.parseC(s"fixture-git-$index")))))
      def execute(): Consequence[OperationResponse] = Consequence.stateConflict("fixture setup only")
    }
    take(new SetupCall(contextCore).runC).termination shouldBe ProcessExecutionTermination.Exited(0)
  }

  "RepositorySync actual Git observations" should {
    "fetch only the fixed tracking branch and classify actual history while preserving dirty files" in {
      Given("fresh fixture-owned local/peer/bare repositories and runtime-fixed Git purposes")
      val t = target()
      val definitions = take(Programs.definitionsC(t, "/usr/bin/git", limits, Map("PATH" -> "/usr/bin:/bin")))
      withRuntime(definitions ++ setupDefinitions(t)) { core =>
        (0 to 3).foreach(setup(core, _))
        val staged = t.root.resolve("staged name\nwith newline")
        val untracked = t.root.resolve("untracked name")
        Files.writeString(staged, "staged bytes\n")
        setup(core, 7)
        Files.writeString(staged, "unstaged successor bytes\n")
        Files.writeString(untracked, "untracked bytes\n")
        val before = take(Observation.observeC(core))
        before.trackingTip shouldBe None
        before.counts shouldBe None
        // Fixture invokes the fixed runtime effect directly: no production
        // owning intent/progression or convergence receipt is fabricated.
        When("the test host executes the fixed fetch and the component observes actual native results")
        take(Effects.executeC(core, Effects.Purpose.GitFetch, "")).termination shouldBe ProcessExecutionTermination.Exited(0)
        val equal = take(Observation.observeC(core))
        Then("one real tracking tip is equal, local HEAD/index/worktree stay unchanged, and FETCH_HEAD is absent")
        equal.trackingTip shouldBe Some(before.head)
        equal.counts.map(_.relation) shouldBe Some(Observation.Relation.Equal)
        equal.mergeBase shouldBe Some(before.head)
        equal.head shouldBe before.head
        equal.tree shouldBe before.tree
        equal.dirty shouldBe before.dirty
        Files.readString(staged) shouldBe "unstaged successor bytes\n"
        Files.readString(untracked) shouldBe "untracked bytes\n"
        Files.exists(t.root.resolve(".git/FETCH_HEAD")) shouldBe false
        When("the owned peer advances its main branch and the same runtime-fixed fetch runs")
        (4 to 6).foreach(setup(core, _))
        take(Effects.executeC(core, Effects.Purpose.GitFetch, "")).termination shouldBe ProcessExecutionTermination.Exited(0)
        val remote = take(Observation.observeC(core))
        Then("the real graph is remote-ahead without replacing dirty files or the local HEAD")
        remote.counts shouldBe Some(Observation.Counts(0, 1))
        remote.mergeBase shouldBe Some(before.head)
        remote.head shouldBe before.head
        remote.dirty shouldBe before.dirty
        // Clear the staged entry using a separately fixed fixture commit;
        // committing the owned staged bytes does not authorize user commits.
        When("the fixture local branch gets its own independent commit")
        setup(core, 8)
        val diverged = take(Observation.observeC(core))
        Then("the actual graph, rather than an AI estimate or supplied receipt, reports divergence")
        diverged.counts shouldBe Some(Observation.Counts(1, 1))
        diverged.mergeBase shouldBe Some(before.head)
        Files.readString(staged) shouldBe "unstaged successor bytes\n"
        Files.readString(untracked) shouldBe "untracked bytes\n"
      }
      Given("a physically new runtime after the original Git effect host is disposed")
      withRuntime(definitions) { core =>
        When("only native Git observation is requested after reopening")
        val reopened = take(Observation.observeC(core))
        Then("the real persisted Git graph and dirty content are observed without another fetch or fixture setup")
        reopened.counts shouldBe Some(Observation.Counts(1, 1))
        Files.readString(t.root.resolve("staged name\nwith newline")) shouldBe "unstaged successor bytes\n"
        Files.readString(t.root.resolve("untracked name")) shouldBe "untracked bytes\n"
      }
    }
    "observe actual local-ahead and divergence through their retained common ancestor" in {
      Given("owned local and bare repositories with the same actual initial commit")
      val t = target()
      val definitions = take(Programs.definitionsC(t, "/usr/bin/git", limits, Map("PATH" -> "/usr/bin:/bin")))
      withRuntime(definitions ++ setupDefinitions(t)) { core =>
        (0 to 3).foreach(setup(core, _))
        take(Effects.executeC(core, Effects.Purpose.GitFetch, "")).termination shouldBe ProcessExecutionTermination.Exited(0)
        val initial = take(Observation.observeC(core))
        When("the local fixture advances independently before the remote fixture advances")
        setup(core, 8)
        val ahead = take(Observation.observeC(core))
        Then("the real graph is local-ahead and its single common ancestor is the initial commit")
        ahead.counts shouldBe Some(Observation.Counts(1, 0))
        ahead.mergeBase shouldBe Some(initial.head)
        Given("that preserved local-ahead history and a peer cloned from the remote parent")
        When("the peer advances and fixed fetch observes that remote history")
        (4 to 6).foreach(setup(core, _))
        take(Effects.executeC(core, Effects.Purpose.GitFetch, "")).termination shouldBe ProcessExecutionTermination.Exited(0)
        val diverged = take(Observation.observeC(core))
        Then("both histories remain present and the actual common ancestor still identifies their base")
        diverged.counts shouldBe Some(Observation.Counts(1, 1))
        diverged.mergeBase shouldBe Some(initial.head)
        diverged.head shouldBe ahead.head
      }
    }
    "refuse unrelated histories rather than classifying them as mergeable divergence" in {
      Given("an owned local root and an independently initialized remote root, without a force push")
      val t = target()
      val definitions = take(Programs.definitionsC(t, "/usr/bin/git", limits, Map("PATH" -> "/usr/bin:/bin")))
      withRuntime(definitions ++ setupDefinitions(t)) { core =>
        (0 to 2).foreach(setup(core, _))
        (9 to 11).foreach(setup(core, _))
        val local = take(Observation.observeC(core))
        When("the fixed fetch obtains that unrelated tip and native graph observation checks the common ancestor")
        take(Effects.executeC(core, Effects.Purpose.GitFetch, "")).termination shouldBe ProcessExecutionTermination.Exited(0)
        val outcome = Observation.observeC(core)
        val head = take(Effects.executeC(core, Effects.Purpose.GitHead, "").flatMap(Observation.requiredTextC)
          .flatMap(Observation.oidC))
        Then("the route refuses automatic integration while the original local commit remains intact")
        outcome.isSuccess shouldBe false
        head shouldBe local.head
        Files.exists(t.root.resolve(".git/MERGE_HEAD")) shouldBe false
      }
    }
    "fast-forward only the observed remote commit and preserve its result after host reopen" in {
      Given("owned local and remote branches with an actual remote-ahead graph")
      val t = target()
      val definitions = take(Programs.definitionsC(t, "/usr/bin/git", limits))
      var selected = ""
      withRuntime(definitions ++ setupDefinitions(t)) { core =>
        (0 to 6).foreach(setup(core, _))
        take(Effects.executeC(core, Effects.Purpose.GitFetch, "")).termination shouldBe ProcessExecutionTermination.Exited(0)
        val before = take(Observation.observeC(core))
        before.counts shouldBe Some(Observation.Counts(0, 1))
        selected = before.trackingTip.get
        When("the fixture calls the native fast-forward effect with that complete observed commit")
        val effect = take(Effects.executeNativeGitC(core, Effects.NativeGitEffect.FastForward(selected)))
        val after = take(Observation.observeC(core))
        Then("Git advances exactly to that commit without a merge commit or pending merge")
        effect.termination shouldBe ProcessExecutionTermination.Exited(0)
        after.head shouldBe selected
        after.counts shouldBe Some(Observation.Counts(0, 0))
        after.dirty shouldBe Vector.empty
        Files.exists(t.root.resolve(".git/MERGE_HEAD")) shouldBe false
      }
      Given("a physically new host with the same fixed target, without another mutation")
      When("native state is observed after reopening")
      val reopened = withRuntime(definitions)(Observation.observeC)
      Then("the actual selected commit and equal graph remain")
      take(reopened).head shouldBe selected
      take(reopened).counts shouldBe Some(Observation.Counts(0, 0))
    }
    "merge a clean divergent graph only with the trusted host commit identity" in {
      Given("two fixture-owned divergent descendants and an explicitly configured commit identity")
      val t = target()
      val definitions = take(Programs.definitionsC(t, "/usr/bin/git", limits,
        commitIdentity = Some(Programs.CommitIdentity("Workflow Fixture", "workflow@example.invalid"))))
      withRuntime(definitions ++ setupDefinitions(t)) { core =>
        (0 to 6).foreach(setup(core, _))
        setup(core, 8)
        take(Effects.executeC(core, Effects.Purpose.GitFetch, "")).termination shouldBe ProcessExecutionTermination.Exited(0)
        val before = take(Observation.observeC(core))
        before.counts shouldBe Some(Observation.Counts(1, 1))
        When("the fixture invokes ordinary merge of the exact observed remote commit")
        val effect = take(Effects.executeNativeGitC(core, Effects.NativeGitEffect.Merge(before.trackingTip.get)))
        val after = take(Observation.observeC(core))
        Then("the native merged history contains both descendants and no unresolved merge")
        effect.termination shouldBe ProcessExecutionTermination.Exited(0)
        after.head should not be before.head
        after.head should not be before.trackingTip.get
        after.counts shouldBe Some(Observation.Counts(2, 0))
        after.mergeBase shouldBe before.trackingTip
        after.dirty shouldBe Vector.empty
        Files.exists(t.root.resolve(".git/MERGE_HEAD")) shouldBe false
      }
      Given("the same host mapping without a declared commit identity")
      When("runtime definitions are assembled without inventing an author")
      val unconfigured = take(Programs.definitionsC(t, "/usr/bin/git", limits))
      Then("ordinary merge is not an installed capability and unsafe identity declarations are refused")
      unconfigured.exists(_.capability.print == Effects.Purpose.GitMerge.capability) shouldBe false
      Programs.definitionsC(t, "/usr/bin/git", limits,
        commitIdentity = Some(Programs.CommitIdentity("name\noption", "workflow@example.invalid"))).isSuccess shouldBe false
    }
    "push only the selected commit to the fixed branch and refuse a changed divergent remote" in {
      Given("owned local-ahead history with one fixed non-force destination")
      val t = target()
      val definitions = take(Programs.definitionsC(t, "/usr/bin/git", limits))
      withRuntime(definitions ++ setupDefinitions(t)) { core =>
        (0 to 3).foreach(setup(core, _))
        take(Effects.executeC(core, Effects.Purpose.GitFetch, "")).termination shouldBe ProcessExecutionTermination.Exited(0)
        setup(core, 8)
        val local = take(Observation.observeC(core))
        local.counts shouldBe Some(Observation.Counts(1, 0))
        When("the fixture pushes exactly that observed commit to the configured destination")
        val effect = take(Effects.executeNativeGitC(core, Effects.NativeGitEffect.Push(local.head, t.remoteBranch)))
        val actual = take(Observation.remoteTipC(core, t.remoteBranch))
        Then("the real remote receives the selected commit without a force option")
        effect.termination shouldBe ProcessExecutionTermination.Exited(0)
        actual shouldBe local.head
        Given("a peer cloned from the now-current remote that independently advances it")
        (4 to 6).foreach(setup(core, _))
        val advanced = take(Observation.remoteTipC(core, t.remoteBranch))
        advanced should not be local.head
        setup(core, 8)
        take(Effects.executeC(core, Effects.Purpose.GitFetch, "")).termination shouldBe ProcessExecutionTermination.Exited(0)
        val retained = take(Observation.observeC(core))
        retained.counts shouldBe Some(Observation.Counts(1, 1))
        When("the same non-force effect attempts to publish the now-divergent retained local tip")
        val refused = take(Effects.executeNativeGitC(core, Effects.NativeGitEffect.Push(retained.head, t.remoteBranch)))
        val unchanged = take(Observation.remoteTipC(core, t.remoteBranch))
        Then("Git refuses the remote regression, preserving both remote and local tips")
        refused.termination shouldBe ProcessExecutionTermination.Exited(1)
        unchanged shouldBe advanced
        take(Observation.observeC(core)).head shouldBe retained.head
        Given("that unchanged local tip and a different destination supplied to the internal adapter")
        When("the canonical runtime checks its complete fixed refspec template")
        val crossed = Effects.executeNativeGitC(core, Effects.NativeGitEffect.Push(retained.head, "another"))
        Then("the crossed destination and nonnative option/ref values are refused before execution")
        crossed.isSuccess shouldBe false
        Effects.executeNativeGitC(core, Effects.NativeGitEffect.FastForward("HEAD")).isSuccess shouldBe false
        Effects.executeNativeGitC(core, Effects.NativeGitEffect.FastForward("--force")).isSuccess shouldBe false
        Effects.executeNativeGitC(core, Effects.NativeGitEffect.FastForward("A" * 40)).isSuccess shouldBe false
        val pushPolicy = definitions.find(_.capability.print == Effects.Purpose.GitPush.capability).get.argumentPolicy
        pushPolicy.validateC(Vector("+" + local.head + ":refs/heads/main"), limits).isSuccess shouldBe false
        pushPolicy.validateC(Vector(local.head + ":refs/heads/main", "--force"), limits).isSuccess shouldBe false
        take(Observation.remoteTipC(core, t.remoteBranch)) shouldBe advanced
      }
    }
    "decode NUL paths and deterministic native graph relations without management hashes" in {
      Given("native porcelain paths containing newlines, spaces and rename source/destination fields")
      val wire = " M ordinary name\u0000R  new\nname\u0000old name\u0000?? untracked\u0000UU conflict\u0000"
      When("the deterministic native parsers evaluate the captured values")
      val paths = take(Observation.statusC(wire))
      val counts = Vector("0\t0\n", "2\t0\n", "0\t3\n", "2\t3\n").map(v => take(Observation.countsC(v)).relation)
      Then("paths are not line-split or trimmed, and relations have the four exact dispositions")
      paths(1) shouldBe Observation.DirtyPath('R', ' ', "new\nname", Some("old name"))
      paths.last.unmerged shouldBe true
      counts shouldBe Observation.Relation.values.toVector
      Observation.statusC("R  destination\u0000").isSuccess shouldBe false
      Observation.statusC(" M unterminated").isSuccess shouldBe false
      Observation.countsC("-1\t0\n").isSuccess shouldBe false
      Observation.countsC("9999999999999999999999999\t0\n").isSuccess shouldBe false
    }
    "refuse incomplete capture and unknown termination instead of claiming missing references" in {
      Given("captured process outcomes, including a legitimate missing-reference exit")
      val missing = result("", 1)
      When("the observation boundary evaluates actual termination and full UTF-8 capture")
      val absent = take(Observation.optionalOidC(missing))
      Then("only exact empty exit1 is absence; timeout, launch failure and incomplete output are refused")
      absent shouldBe None
      Observation.optionalOidC(missing.copy(termination = ProcessExecutionTermination.TimedOut)).isSuccess shouldBe false
      Observation.optionalOidC(missing.copy(termination = ProcessExecutionTermination.LaunchFailed)).isSuccess shouldBe false
      Observation.requiredTextC(result("false\n", truncated = true)).isSuccess shouldBe false
      Observation.requiredTextC(result("value").copy(stdout = ProcessExecutionCapture(Vector(0xff.toByte), 1L, false)))
        .isSuccess shouldBe false
      Observation.oidC("a" * 40 + "\n").isSuccess shouldBe true
      Observation.oidC("a" * 64 + "\n").isSuccess shouldBe true
      Observation.optionalMergeBaseC(result("a" * 40 + "\n")).toOption shouldBe Some(Some("a" * 40))
      Observation.optionalMergeBaseC(missing).toOption shouldBe Some(None)
      Observation.optionalMergeBaseC(result("a" * 40 + "\n" + "b" * 40 + "\n")).isSuccess shouldBe false
      Observation.optionalMergeBaseC(result("", 0)).isSuccess shouldBe false
      Observation.optionalMergeBaseC(result("", 1, truncated = true)).isSuccess shouldBe false
      Observation.optionalMergeBaseC(result("unexpected", 1)).isSuccess shouldBe false
      Observation.optionalMergeBaseC(missing.copy(termination = ProcessExecutionTermination.TimedOut)).isSuccess shouldBe false
    }
    "deny caller-selected arguments and missing runtime grants before Git execution" in {
      Given("fixed host-owned Git programs and no grants")
      val t = target()
      val definitions = take(Programs.definitionsC(t, "/usr/bin/git", limits))
      When("a canonical component observation requests the ungranted fixed capability")
      val outcome = withRuntime(definitions, grant = false)(Observation.observeC)
      Then("observation is refused and no repository or remote was created")
      outcome.isSuccess shouldBe false
      Files.exists(t.root) shouldBe false
      Files.exists(Path.of(t.endpoint)) shouldBe false
      definitions.foreach(d => d.argumentPolicy.validateC(Vector("-C", "/other"), limits).isSuccess shouldBe false)
      Programs.definitionsC(t.copy(remoteBranch = "../wrong"), "/usr/bin/git", limits).isSuccess shouldBe false
      Programs.definitionsC(t, "/usr/bin/git", limits, Map("GIT_DIR" -> "/another/.git")).isSuccess shouldBe false
    }
  }
}
