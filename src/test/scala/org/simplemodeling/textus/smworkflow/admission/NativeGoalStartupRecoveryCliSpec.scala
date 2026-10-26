package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.*
import org.goldenport.convert.ValueReader
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{DefinitionBinding, InstanceRecord, ProgressionReference}
import org.goldenport.record.io.RecordDecoder
import org.scalatest.{GivenWhenThen}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeValidation as N
import SplitPhaseExplicitPartitionFixture.take

/** Installed CLI observations of genuine prelaunch failure and an old running
  * Compile. All roots, executable copies, gates and child processes are owned
  * by these examples; no production Session or review authority is supplied.
  * @since   Oct. 21, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalStartupRecoveryCliSpec extends AnyWordSpec with GivenWhenThen
    with GoalPhaseValidationRecoveryOperationFixture {
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val ordinary = super.process_plan(root, scenario, goal)
    val owned = root.resolve("owned-sbt/bin/sbt")
    if (!Files.exists(owned)) ordinary
    else {
      val programs = ordinary.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get
      require(programs.size == 2, "The owned executable belongs only to the second Compile")
      ordinary.mapObject(_.add("sbtPrograms", Json.fromValues(programs.updated(1,
        programs(1).mapObject(_.add("sbtExecutable", Json.fromString(owned.toString)))))))
    }
  }

  private final case class Invocation(pid: Long, exitcode: Int, stdout: Path, stderr: Path) {
    def business: V.GoalPhaseAdvanceResult = {
      withClue(Files.readString(stderr)) { exitcode shouldBe 0 }
      val record = take(new RecordDecoder().json(Files.readString(stdout)))
      take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(record))
    }
    def events: Vector[Json] = Files.readString(stderr).linesIterator
      .filter(_.startsWith("[cncf-sbt-event] "))
      .map(line => parse(line.stripPrefix("[cncf-sbt-event] ")).toOption.get).toVector
  }

  /** A finite caller retains actual captures and tracks only descendants of
    * its own Process. Gate release always precedes waiting or termination.
    */
  private final class Running(root: Path, executable: Path, identity: String, ordinal: Int, releasegate: Boolean) extends AutoCloseable {
    private val _logs = Files.createDirectories(root.resolve("installed-cli-logs"))
    private val _stdout = _logs.resolve(ordinal + ".stdout.json")
    private val _stderr = _logs.resolve(ordinal + ".stderr.log")
    private val _descendants = new ConcurrentHashMap[Long, ProcessHandle]()
    private val _finished = new AtomicBoolean(false)
    private val _tracker = new Thread(() => {
      while (!_finished.get()) {
        _track()
        Thread.sleep(20L)
      }
    }, "test-owned-installed-cli-" + ordinal)
    _tracker.setDaemon(true)
    private val _process = new ProcessBuilder(Vector(executable.toString, "--runtime-root", root.toString, "command",
      SmWorkflowComponent.componentId.name + ".GoalPhaseService.advanceGoalPhase", "--sessionId", identity, "--json").asJava)
      .directory(root.toFile).redirectOutput(_stdout.toFile).redirectError(_stderr.toFile).start()
    try {
      _process.getOutputStream.close()
      Files.writeString(_logs.resolve(ordinal + ".pid"), _process.pid().toString + "\n")
      _tracker.start()
    } catch {
      case error: Throwable =>
        try close() catch { case cleanup: Throwable => error.addSuppressed(cleanup) }
        throw error
    }

    def isAlive: Boolean = _process.isAlive
    def owns(pid: Long): Boolean = {
      _track()
      _descendants.containsKey(pid)
    }
    def await(): Invocation = {
      _process.waitFor(240L, TimeUnit.SECONDS) shouldBe true
      Invocation(_process.pid(), _process.exitValue(), _stdout, _stderr)
    }
    private def _track(): Unit = {
      val children = _process.toHandle.descendants()
      try children.iterator().asScala.foreach(child => _descendants.put(child.pid(), child))
      finally children.close()
    }
    override def close(): Unit = {
      try {
        if (releasegate) Files.writeString(root.resolve("compile-release.txt"), "release\n")
      } finally {
        try {
          try _track() finally {
            if (_process.isAlive && !_process.waitFor(5L, TimeUnit.SECONDS)) {
              _process.destroy()
              if (!_process.waitFor(10L, TimeUnit.SECONDS)) _process.destroyForcibly()
            }
            _descendants.values().asScala.filter(_.isAlive).foreach(_.destroy())
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L)
            while (_descendants.values().asScala.exists(_.isAlive) && System.nanoTime() < deadline) Thread.sleep(20L)
            _descendants.values().asScala.filter(_.isAlive).foreach(_.destroyForcibly())
            _process.waitFor(10L, TimeUnit.SECONDS) shouldBe true
            val stopped = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L)
            while (_descendants.values().asScala.exists(_.isAlive) && System.nanoTime() < stopped) Thread.sleep(20L)
            _descendants.values().asScala.filter(_.isAlive).map(_.pid()).toVector shouldBe empty
          }
        } finally {
          _finished.set(true)
          try {
            _tracker.join(2000L)
            _tracker.isAlive shouldBe false
          } finally {
            try _process.getOutputStream.close() finally {
              try _process.getInputStream.close() finally _process.getErrorStream.close()
            }
          }
        }
      }
    }
  }

  private final class InstalledCli(root: Path, identity: String, executable: Path) {
    private var _sequence = 0
    def start(releaseGate: Boolean = false): Running = {
      val ordinal = _sequence
      _sequence += 1
      new Running(root, executable, identity, ordinal, releaseGate)
    }
    def advance(): Invocation = {
      val running = start()
      try running.await() finally running.close()
    }
  }

  private def _installed(): Path = {
    val supplied = Path.of(sys.props.getOrElse("smworkflow.spec.installedCommand",
      throw new IllegalArgumentException("Required smworkflow.spec.installedCommand must name the installed CLI")))
    require(supplied.isAbsolute, "The installed CLI must have an absolute path")
    val actual = supplied.toRealPath()
    require(Files.isRegularFile(actual) && Files.isExecutable(actual), "The installed CLI must be a regular executable")
    actual
  }

  private def _prepare(root: Path, action: String): Unit = {
    TypedSbtHostPlanFixture.build(root, true)
    TypedSbtHostPlanFixture.buildCompile(root, true)
    val build = root.resolve("repository/build.sbt")
    val original = Files.readString(build)
    require(original.contains("  Seq(generated)"), "The ordinary Compile source generator must be present")
    Files.writeString(build, original.replace("  Seq(generated)", action + "\n  Seq(generated)"))
  }

  private def _initialize(root: Path, sequential: Boolean): (String, InstanceRecord, V.GoalPhaseAdvanceResult) = {
    var observed = Option.empty[(String, InstanceRecord, V.GoalPhaseAdvanceResult)]
    withRecoveryEpisode(root, initialize = true, sequential = sequential) { episode =>
      val identity = episode.start().sessionId
      val request = episode.advance(identity)
      request.work.get.kind shouldBe "ImplementStep"
      episode.submitImplementation(request).accepted shouldBe true
      observed = Some((identity, episode.current(identity), request))
      compileExecutions(root) shouldBe empty
    }
    observed.get
  }

  private def _source(record: InstanceRecord): String = record.history
    .find(_.currentProgression.contains(ProgressionReference("phase.Validation"))).get.revision.value.toString

  private def _base(record: InstanceRecord): Vector[String] = record.history
    .find(_.causation.value.startsWith("ordered-goal-start:")).get.causation.value.split(':').takeRight(2).toVector

  private def _unchanged(before: InstanceRecord, after: InstanceRecord): Unit = {
    after.identity shouldBe before.identity
    _same_definition(before.definition, after.definition)
    after.history.take(before.history.size) shouldBe before.history
    _base(after) shouldBe _base(before)
  }

  private def _same_definition(before: DefinitionBinding, after: DefinitionBinding): Unit = {
    after.workflowIdentity shouldBe before.workflowIdentity
    after.workflowRevision shouldBe before.workflowRevision
    after.sourceCorrelation shouldBe before.sourceCorrelation
    after.producerAbiIdentity shouldBe before.producerAbiIdentity
    after.workflowAbiIdentity shouldBe before.workflowAbiIdentity
    after.bootstrapAbiIdentity shouldBe before.bootstrapAbiIdentity
    after.producerRevision shouldBe before.producerRevision
  }

  private def _same_record(before: InstanceRecord, after: InstanceRecord): Unit = {
    _same_definition(before.definition, after.definition)
    after.copy(definition = before.definition) shouldBe before
  }

  private def _same_saved(before: Option[N.Saved], after: Option[N.Saved]): Unit = {
    after.isDefined shouldBe before.isDefined
    before.zip(after).foreach { (original, retained) =>
      retained.payload shouldBe original.payload
      _same_record(original.record, retained.record)
    }
  }

  private def _review(result: V.GoalPhaseAdvanceResult, identity: String, request: V.GoalPhaseAdvanceResult): Unit = {
    result.sessionId shouldBe identity
    result.kind shouldBe "Request"
    result.work.get.kind shouldBe "ReviewStep"
    result.work.get.selectedPlan shouldBe request.work.get.selectedPlan
    result.work.get.originalBase shouldBe request.work.get.originalBase
    result.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2)))
  }

  private def _same_selection(first: N.Payload, later: N.Payload): Unit = {
    later.sourceRevision shouldBe first.sourceRevision
    later.step shouldBe first.step
    later.selected shouldBe first.selected
    later.candidate shouldBe first.candidate
    later.metadata shouldBe first.metadata
    later.admissionFailures shouldBe first.admissionFailures
    later.uncertainties shouldBe first.uncertainties
    later.newAttempts shouldBe first.newAttempts
  }

  private def _await_ready(root: Path, running: Running): Long = {
    val marker = root.resolve("compile-ready.txt")
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120L)
    while (!Files.exists(marker) && running.isAlive && System.nanoTime() < deadline) Thread.sleep(25L)
    Files.isRegularFile(marker) shouldBe true
    running.isAlive shouldBe true
    val pid = Files.readString(marker).trim.toLong
    running.owns(pid) shouldBe true
    pid
  }

  "Installed GoalPhase startup recovery (E8)" should {
    "retain two actual NotStarted attempts and resume the same second check after owned permission restoration" in {
      Given("SBT-EXECUTION-RECOVERY-001: two normal Compile targets with a regular private copy for the second executable")
      val executable = _installed()
      val root = freshRecoveryRoot("installed-startup-failure")
      val normal = TypedSbtHostPlanFixture.normalExecutable
      val bin = Files.createDirectories(root.resolve("owned-sbt/bin"))
      val owned = bin.resolve("sbt")
      Files.copy(normal, owned, StandardCopyOption.COPY_ATTRIBUTES)
      val launcher = normal.getParent.resolve("sbt-launch.jar")
      Files.isRegularFile(launcher) shouldBe true
      Files.copy(launcher, bin.resolve("sbt-launch.jar"), StandardCopyOption.COPY_ATTRIBUTES)
      Files.isSymbolicLink(owned) shouldBe false
      Files.isSameFile(normal, owned) shouldBe false
      Files.isExecutable(owned) shouldBe true
      val permissions = Files.getPosixFilePermissions(owned)
      _prepare(root, """  val once = root.getParentFile / "startup-permissions-removed.txt"
        |  if (!once.exists()) {
        |    val executable = root.getParentFile / "owned-sbt" / "bin" / "sbt"
        |    val permissions = java.nio.file.Files.getPosixFilePermissions(executable.toPath)
        |    permissions.remove(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)
        |    permissions.remove(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE)
        |    permissions.remove(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE)
        |    java.nio.file.Files.setPosixFilePermissions(executable.toPath, permissions)
        |    IO.write(once, java.lang.ProcessHandle.current().pid().toString)
        |  }""".stripMargin)
      val (identity, accepted, request) = _initialize(root, true)
      val cli = new InstalledCli(root, identity, executable)

      When("the installed Advance runs first Compile and the second bound executable becomes nonexecutable only inside its generator")
      val stoppedcall = try {
        val observed = cli.advance()
        Files.isExecutable(owned) shouldBe false
        observed
      } finally Files.setPosixFilePermissions(owned, permissions)
      val waiting = stoppedcall.business
      Then("one bounded extra dispatch also observes actual NotStarted, retaining success and both stopped attempts without repair")
      waiting.sessionId shouldBe identity
      waiting.kind shouldBe "Wait"
      waiting.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2)))
      val pids = compileExecutions(root)
      pids.size shouldBe 1
      pids.head should not be ProcessHandle.current().pid()
      Files.readString(root.resolve("startup-permissions-removed.txt")).trim.toLong shouldBe pids.head
      val terminal = stoppedcall.events.filter(event => event.hcursor.get[String]("state").contains("terminal"))
      val notstarted = terminal.filter(_.hcursor.get[String]("termination").contains("NotStarted"))
      notstarted.size shouldBe 2
      notstarted.map(_.hcursor.get[String]("programIdentity").toOption.get).distinct.size shouldBe 1
      notstarted.foreach { event =>
        event.hcursor.get[String]("operation").toOption shouldBe Some("compile")
        event.hcursor.get[Option[Long]]("pid").toOption.flatten shouldBe None
        event.hcursor.get[Option[Int]]("exitCode").toOption.flatten shouldBe None
      }
      terminal.count(_.hcursor.get[Int]("exitCode").contains(0)) shouldBe 1
      stoppedcall.events.count(_.hcursor.get[String]("state").contains("started")) shouldBe 1
      var stoppedstate = Option.empty[InstanceRecord]
      var savedfirst = Option.empty[N.Saved]
      var savedstops = Vector.empty[N.Saved]
      withRecoveryEpisode(root, sequential = true) { episode =>
        val current = episode.current(identity)
        _unchanged(accepted, current)
        stoppedstate = Some(current)
        val source = _source(current)
        savedfirst = episode.retained(identity, source, "check-result-0")
        val first = savedfirst.get
        first.payload.selected.map(_.identity) shouldBe Vector("compile", "compile-second")
        first.payload.receipts.map(_.identity) shouldBe Vector("compile")
        first.payload.receipts.head.exitCode shouldBe Some(0)
        first.payload.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
        val keys = Vector("check-intent-1", "check-result-1", "check-intent-1-retry-1", "check-result-1-retry-1")
        val chain = keys.map(key => episode.retained(identity, source, key).get)
        chain.map(_.record.revision.value).distinct.size shouldBe 4
        chain.map(_.record.causation.value) shouldBe keys.map("ordered-validation:" + _)
        chain.sliding(2).foreach { pair =>
          pair.last.record.revision.value shouldBe pair.head.record.revision.value + 1
          pair.last.record.history.dropRight(1) shouldBe pair.head.record.history
        }
        savedstops = Vector(chain(1), chain(3))
        savedstops.map(_.payload.stopped.size) shouldBe Vector(1, 2)
        chain.foreach { saved =>
          _same_selection(first.payload, saved.payload)
          saved.payload.receipts shouldBe first.payload.receipts
        }
        savedstops.last.payload.stopped.foreach { receipt =>
          receipt.identity shouldBe "compile-second"
          receipt.termination shouldBe "not-started"
          receipt.exitCode shouldBe None
          receipt.stdout shouldBe ""
          receipt.stderr shouldBe ""
          receipt.sbt.get.kind shouldBe WorkflowSbtValidation.Kind.Compile
          receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.NotExecuted
        }
        savedstops.last.payload.stopped.take(1) shouldBe savedstops.head.payload.stopped
        episode.retained(identity, source, "check-intent-1-retry-2") shouldBe None
      }

      When("only the owned permissions are restored and a fresh installed Advance resumes the same Session")
      Files.getPosixFilePermissions(owned) shouldBe permissions
      val reviewcall = cli.advance()
      val review = reviewcall.business
      Then("the second actual Compile appends its next ordinal while the first success and stopped history remain immutable")
      _review(review, identity, request)
      compileExecutions(root).size shouldBe 2
      compileExecutions(root).distinct.size shouldBe 2
      compileExecutions(root).head shouldBe pids.head
      var reviewed = Option.empty[InstanceRecord]
      var successful = Option.empty[N.Saved]
      withRecoveryEpisode(root, sequential = true) { episode =>
        val current = episode.current(identity)
        _unchanged(stoppedstate.get, current)
        reviewed = Some(current)
        val source = _source(current)
        _same_saved(savedfirst, episode.retained(identity, source, "check-result-0"))
        Vector("check-result-1", "check-result-1-retry-1").zip(savedstops).foreach { (key, saved) =>
          _same_saved(Some(saved), episode.retained(identity, source, key))
        }
        val intent = episode.retained(identity, source, "check-intent-1-retry-2").get
        successful = episode.retained(identity, source, "check-result-1-retry-2")
        intent.record.revision.value should be > savedstops.last.record.revision.value
        successful.get.record.revision.value shouldBe intent.record.revision.value + 1
        _same_selection(savedfirst.get.payload, successful.get.payload)
        successful.get.payload.receipts.take(1) shouldBe savedfirst.get.payload.receipts
        successful.get.payload.receipts.map(_.identity) shouldBe Vector("compile", "compile-second")
        successful.get.payload.receipts.last.exitCode shouldBe Some(0)
        successful.get.payload.receipts.last.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
        successful.get.payload.stopped shouldBe savedstops.last.payload.stopped
      }

      When("a further fresh installed Advance observes pending independent review")
      val repeated = cli.advance()
      Then("no third Compile, fresh intent or historical replacement appears")
      _review(repeated.business, identity, request)
      repeated.business.work shouldBe review.work
      compileExecutions(root).size shouldBe 2
      repeated.events shouldBe empty
      withRecoveryEpisode(root, sequential = true) { episode =>
        _same_record(reviewed.get, episode.current(identity))
        _same_saved(successful, episode.retained(identity, _source(reviewed.get), "check-result-1-retry-2"))
        episode.retained(identity, _source(reviewed.get), "check-intent-1-retry-3") shouldBe None
      }
      // Prelaunch NotStarted never submitted a launcher task. This example
      // establishes no distributed late-worker fencing contract.
    }
  }

  "Installed GoalPhase old execution ownership (E9)" should {
    "wait on the original running Compile without a successor and retain its eventual actual result" in {
      Given("SBT-EXECUTION-RECOVERY-001: one ordinary Compile whose owned source generator waits on a finite gate")
      val executable = _installed()
      val root = freshRecoveryRoot("installed-old-execution")
      _prepare(root, """  IO.write(root.getParentFile / "compile-ready.txt", java.lang.ProcessHandle.current().pid().toString)
        |  val gate = root.getParentFile / "compile-release.txt"
        |  val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120L)
        |  while (!gate.isFile && System.nanoTime() < deadline) Thread.sleep(25L)
        |  require(gate.isFile, "The test-owned Compile gate was not released within 120 seconds")""".stripMargin)
      val (identity, accepted, request) = _initialize(root, false)
      val cli = new InstalledCli(root, identity, executable)
      val old = cli.start(releaseGate = true)
      var runningstate = Option.empty[InstanceRecord]
      var originalintent = Option.empty[N.Saved]
      val originalcall = try {
        val pid = _await_ready(root, old)
        compileExecutions(root) shouldBe Vector(pid)
        withRecoveryEpisode(root) { episode =>
          val current = episode.current(identity)
          _unchanged(accepted, current)
          current.causation.value shouldBe "ordered-validation:check-intent-0"
          runningstate = Some(current)
          val source = _source(current)
          originalintent = episode.retained(identity, source, "check-intent-0")
          originalintent should not be empty
          originalintent.get.payload.receipts shouldBe empty
          episode.retained(identity, source, "check-result-0") shouldBe None
        }

        When("another installed Advance uses that same Session while the original actual SBT remains behind the gate")
        val concurrent = cli.advance()
        val waiting = concurrent.business
        Then("normal Wait preserves the unresolved original intent without another intent, result, process or repair")
        old.isAlive shouldBe true
        waiting.kind shouldBe "Wait"
        waiting.sessionId shouldBe identity
        waiting.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2)))
        compileExecutions(root) shouldBe Vector(pid)
        concurrent.events shouldBe empty
        withRecoveryEpisode(root) { episode =>
          _same_record(runningstate.get, episode.current(identity))
          val source = _source(runningstate.get)
          _same_saved(originalintent, episode.retained(identity, source, "check-intent-0"))
          episode.retained(identity, source, "check-result-0") shouldBe None
          episode.retained(identity, source, "check-intent-0-retry-1") shouldBe None
        }

        When("the test releases its gate and awaits the exact original installed process")
        Files.writeString(root.resolve("compile-release.txt"), "release\n")
        old.await()
      } finally old.close()
      val review = originalcall.business
      Then("that original execution reaches pending independent review and supplies the only actual Compile result")
      _review(review, identity, request)
      compileExecutions(root).size shouldBe 1
      var reviewed = Option.empty[InstanceRecord]
      var result = Option.empty[N.Saved]
      withRecoveryEpisode(root) { episode =>
        val current = episode.current(identity)
        _unchanged(runningstate.get, current)
        reviewed = Some(current)
        val source = _source(current)
        _same_saved(originalintent, episode.retained(identity, source, "check-intent-0"))
        result = episode.retained(identity, source, "check-result-0")
        _same_selection(originalintent.get.payload, result.get.payload)
        result.get.payload.receipts.size shouldBe 1
        result.get.payload.receipts.head.identity shouldBe "compile"
        result.get.payload.receipts.head.exitCode shouldBe Some(0)
        result.get.payload.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
        result.get.payload.stopped shouldBe empty
        episode.retained(identity, source, "check-intent-0-retry-1") shouldBe None
      }

      When("a later fresh installed Advance observes the same pending review")
      val repeated = cli.advance()
      Then("the accepted implementation, Plan, base, candidate, history and sole successful process stay retained")
      _review(repeated.business, identity, request)
      repeated.business.work shouldBe review.work
      repeated.events shouldBe empty
      compileExecutions(root).size shouldBe 1
      withRecoveryEpisode(root) { episode =>
        _same_record(reviewed.get, episode.current(identity))
        val source = _source(reviewed.get)
        _same_saved(originalintent, episode.retained(identity, source, "check-intent-0"))
        _same_saved(result, episode.retained(identity, source, "check-result-0"))
        episode.retained(identity, source, "check-intent-0-retry-1") shouldBe None
      }
    }
  }
}
