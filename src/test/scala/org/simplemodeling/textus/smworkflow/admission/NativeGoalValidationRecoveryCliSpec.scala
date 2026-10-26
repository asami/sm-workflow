package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.convert.ValueReader
import org.goldenport.record.{Record, RecordPresentable}
import org.goldenport.record.io.RecordDecoder
import org.goldenport.protocol.Property
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseValidationRecovery as R
import SplitPhaseExplicitPartitionFixture.take

/** Fresh processes use ordinary CncfMain and generated Operations. The sole
  * acknowledgement interruption is installed by the isolated test host before
  * any effect; no private native row or invented execution receipt is written.
  * @since   Oct. 20, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalValidationRecoveryCliSpec extends AnyWordSpec with GivenWhenThen
    with GoalPhaseValidationRecoveryOperationFixture {
  private val primary = Path.of("").toAbsolutePath.normalize
  private val fixtureDirectory = primary.resolve("target/sm-workflow/work/validation-recovery")
  private final case class Invocation(pid: Long, exitCode: Int, stdout: Path, stderr: Path) {
    def logs: String = Files.readString(stdout) + "\n" + Files.readString(stderr)
    def business[A: ValueReader]: A = {
      exitCode shouldBe 0
      val record = take(new RecordDecoder().json(Files.readString(stdout)))
      take(summon[ValueReader[A]].readC(record))
    }
  }
  private final class Cli(val root: Path, val sessionId: String) {
    private var sequence = Vector.empty[Invocation]
    def invocations: Vector[Invocation] = sequence
    val taskRoute: Path = root.resolve("caller/workflow-session.json")
    Files.createDirectories(taskRoute.getParent)
    Files.writeString(taskRoute, Json.obj("family" -> Json.fromString("GoalPhase"),
      "sessionId" -> Json.fromString(sessionId)).noSpaces)
    def call(service: String, operation: String, arguments: Vector[String]): Invocation = {
      val logs = Files.createDirectories(root.resolve("cli-logs"))
      val stdout = logs.resolve(sequence.size + ".stdout.json")
      val stderr = logs.resolve(sequence.size + ".stderr")
      val argv = Vector(primary.resolve("bin/launcher").toString, "--classpath-file",
        primary.resolve("target/cncf.d/runtime-classpath.txt").toString, "--main-class", "org.goldenport.cncf.CncfMain", "--",
        "--force-exit", "--component-dev-dir", primary.toString,
        "--textus.assembly.descriptor=" + root.resolve(".textus/assembly.yaml"),
        "command", SmWorkflowComponent.componentId.name + "." + service + "." + operation) ++ arguments ++ Vector("--json")
      val process = new ProcessBuilder(argv.asJava).directory(root.toFile)
        .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      try {
        process.getOutputStream.close()
        if (!process.waitFor(240, TimeUnit.SECONDS)) {
          process.destroyForcibly()
          process.waitFor()
          fail("Owned ordinary CncfMain process exceeded its finite deadline: " + Files.readString(stderr))
        }
        val observed = Invocation(process.pid(), process.exitValue(), stdout, stderr)
        sequence :+= observed
        observed
      } finally {
        if (process.isAlive) { process.destroyForcibly(); process.waitFor() }
        process.getInputStream.close()
        process.getErrorStream.close()
      }
    }
    def goal(operation: String, arguments: Vector[String]): Invocation = call("GoalPhaseService", operation, arguments)
    def describe(): (Invocation, V.GoalPhaseValidationRecoveryDescriptionResult) = {
      val observed = goal(R.describeOperation, Vector("--sessionId", sessionId))
      (observed, observed.business[V.GoalPhaseValidationRecoveryDescriptionResult])
    }
    def advance(): Invocation = goal("advanceGoalPhase", Vector("--sessionId", sessionId))
    def recover(input: V.GoalPhaseValidationRecoveryInput): Invocation =
      goal(R.recoveryOperation, Vector("--input", json(input)))
    def prepareDirection(description: V.GoalPhaseValidationRecoveryDescriptionResult, requestFile: Path,
      choice: String, reference: V.WorkflowArtifactReference): V.GoalPhaseValidationRecoveryInput = {
      val directory = Files.createDirectories(root.resolve("caller/human-" + choice))
      val answer = V.GoalPhaseValidationRecoveryAnswer(choice, "Explicit test-owned Human direction accepting possible repeated effects")
      val answerFile = directory.resolve("answer.json")
      val referenceFile = directory.resolve("reference.json")
      val executionFile = directory.resolve("execution-evidence.json")
      Files.writeString(answerFile, json(answer))
      Files.writeString(referenceFile, json(reference))
      Files.writeString(executionFile, json(V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)))
      val input = V.WorkflowEvidencePreparationInput("GoalPhase", "validation-recovery", requestFile.toUri.toASCIIString,
        answerFile.toUri.toASCIIString, referenceFile.toUri.toASCIIString, executionFile.toUri.toASCIIString,
        "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", Some("participant"))
      val prepared = call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", json(input)))
      prepared.exitCode shouldBe 0
      parse(Files.readString(prepared.stdout)).toOption.get.hcursor.downField("datastore").focus.get.asArray.get should not be empty
      val config = root.resolve(".textus/config.json")
      val current = parse(Files.readString(config)).toOption.get
      Files.writeString(config, current.mapObject(_.add("textus.import.data.file", Json.fromString(prepared.stdout.toString))).noSpaces)
      V.GoalPhaseValidationRecoveryInput(sessionId, description.target, choice, answer.rationale, reference)
    }
    def retainPointer(name: String, description: Invocation, authorization: Option[Invocation],
      recoveryInput: V.GoalPhaseValidationRecoveryInput, expectedChecks: Vector[String] = Vector("compile"),
      expectedOutcome: String = "ReviewStep"): Unit = {
      val inputFile = root.resolve("caller/recovery-input.json")
      Files.writeString(inputFile, json(recoveryInput))
      val fields = Vector("fixtureKind" -> Json.fromString("test-owned-validation-recovery"),
        "root" -> Json.fromString(root.toString), "sessionId" -> Json.fromString(sessionId),
        "taskRoute" -> Json.fromString(taskRoute.toString), "description" -> Json.fromString(description.stdout.toString),
        "recoveryInput" -> Json.fromString(inputFile.toString),
        "configuration" -> Json.fromString(root.resolve(".textus/config.json").toString),
        "assembly" -> Json.fromString(root.resolve(".textus/assembly.yaml").toString),
        "database" -> Json.fromString(root.resolve("operation.sqlite").toString),
        "productionHumanEvidence" -> Json.False, "registeredRunnerObserved" -> Json.False,
        "platformExecutionObserved" -> Json.False,
        "expectedChecks" -> Json.fromValues(expectedChecks.map(Json.fromString)),
        "expectedOutcome" -> Json.fromString(expectedOutcome),
        "pointerAuthority" -> Json.False) ++
        authorization.map(value => "authorizationResult" -> Json.fromString(value.stdout.toString)).toVector
      Files.createDirectories(fixtureDirectory)
      Files.writeString(fixtureDirectory.resolve(name), Json.obj(fields*).spaces2 + "\n")
    }
  }
  private def json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def unknown(root: Path, sequential: Boolean = false, successful: Boolean = true):
    (String, Vector[org.goldenport.cncf.workflow.WorkflowInstancePersistence.HistoryEntry]) = {
    if (!successful) {
      TypedSbtHostPlanFixture.build(root, true)
      TypedSbtHostPlanFixture.buildCompile(root, false)
    }
    var identity = ""
    var history = Vector.empty[org.goldenport.cncf.workflow.WorkflowInstancePersistence.HistoryEntry]
    withRecoveryEpisode(root, initialize = true, interrupt = true, sequential = sequential) { episode =>
      identity = episode.start().sessionId
      val request = episode.advance(identity)
      episode.submitImplementation(request).accepted shouldBe true
      episode.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(identity)).isSuccess shouldBe false
      val current = episode.current(identity)
      current.causation.value shouldBe "ordered-validation:check-intent-0"
      history = current.history
      compileExecutions(root) shouldBe empty
    }
    (identity, history)
  }

  "Fresh ordinary GoalPhase validation recovery commands" should {
    "retain the old unknown intent across processes and reach independent review through one genuinely authorized new Compile" in {
      Given("docs/spec/goal-phase-validation-recovery.md R3–R6: a normal isolated Session interrupted immediately after intent commit")
      val root = freshRecoveryRoot("cli-full")
      val (identity, originalHistory) = unknown(root)
      val cli = new Cli(root, identity)
      When("separate ordinary CncfMain processes repeatedly Advance and describe the retained unknown Session")
      val waiting = cli.advance().business[V.GoalPhaseAdvanceResult]
      val repeatedwaiting = cli.advance().business[V.GoalPhaseAdvanceResult]
      val (descriptionCall, description) = cli.describe()
      val target = take(R.targetC(description.target))
      Then("the returned exact original target remains unresolved without executing Compile")
      waiting.kind shouldBe "Wait"
      waiting.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2)))
      repeatedwaiting.kind shouldBe "Wait"
      repeatedwaiting.sessionId shouldBe identity
      repeatedwaiting.repairProgress shouldBe waiting.repairProgress
      description.sessionId shouldBe identity
      description.choices shouldBe Vector("AuthorizeNewAttempt", "Defer")
      target.frontierRevision shouldBe originalHistory.last.revision.value.toString
      target.intentRevision shouldBe target.frontierRevision
      target.sourceRevision.toLong should be < target.intentRevision.toLong
      compileExecutions(root) shouldBe empty

      Given("a supplied request without independent proof and a test-owned Defer direction")
      val missing = V.GoalPhaseValidationRecoveryInput(identity, description.target, "AuthorizeNewAttempt", "No independent evidence",
        V.WorkflowArtifactReference("cli-missing-proof", "1"))
      When("one command lacks evidence and another explicitly admits Defer through passive preparation and normal StartupImport")
      val rejected = cli.recover(missing)
      val deferredInput = cli.prepareDirection(description, descriptionCall.stdout, "Defer", V.WorkflowArtifactReference("cli-defer-proof", "1"))
      val deferred = cli.recover(deferredInput).business[V.GoalPhaseValidationRecoveryResult]
      val stillWaiting = cli.advance().business[V.GoalPhaseAdvanceResult]
      Then("missing evidence refuses, Defer preserves the old unknown intent and neither grants execution")
      rejected.exitCode should not be 0
      deferred.accepted shouldBe true
      deferred.outcome shouldBe "deferred"
      stillWaiting.kind shouldBe "Wait"
      compileExecutions(root) shouldBe empty
      val (againCall, again) = cli.describe()
      take(R.targetC(again.target)) shouldBe target

      Given("a separately supplied, independently provisioned test-owned HumanNewAttempt direction for that exact target")
      val authorizedInput = cli.prepareDirection(again, againCall.stdout, "AuthorizeNewAttempt",
        V.WorkflowArtifactReference("cli-authorize-proof", "1"))
      When("normal recovery accepts the direction in one process, before any later Advance")
      val authorizationCall = cli.recover(authorizedInput)
      val accepted = authorizationCall.business[V.GoalPhaseValidationRecoveryResult]
      Then("authorization appends direction only and stale repetition is refused")
      accepted.accepted shouldBe true
      accepted.outcome shouldBe "new-attempt-authorized"
      compileExecutions(root) shouldBe empty
      cli.recover(authorizedInput).exitCode should not be 0
      cli.retainPointer("latest-cli-episode.json", againCall, Some(authorizationCall), authorizedInput)

      When("one later ordinary Advance runs the new attempt in a fresh process")
      val review = cli.advance().business[V.GoalPhaseAdvanceResult]
      Then("the same Session reaches pending independent review after real normal Compile, retaining Plan/base/history/counters")
      review.sessionId shouldBe identity
      review.kind shouldBe "Request"
      review.work.get.kind shouldBe "ReviewStep"
      review.work.get.selectedPlan.identity shouldBe target.planIdentity
      review.work.get.selectedPlan.revision shouldBe target.planRevision
      review.work.get.originalBase.head shouldBe target.base.head
      review.work.get.originalBase.tree shouldBe target.base.tree
      review.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(0, 0, 2, Some("sm-workflow.repair-trend.v1"), Some(2), Some(2)))
      compileExecutions(root).size shouldBe 1
      compileExecutions(root).head should not be ProcessHandle.current().pid()

      When("another fresh ordinary process advances the retained successful validation")
      val repeatedreview = cli.advance().business[V.GoalPhaseAdvanceResult]
      Then("independent review remains pending and successful Compile is not dispatched again")
      repeatedreview.sessionId shouldBe identity
      repeatedreview.kind shouldBe "Request"
      repeatedreview.work.get.kind shouldBe "ReviewStep"
      repeatedreview.repairProgress shouldBe review.repairProgress
      compileExecutions(root).size shouldBe 1
      withRecoveryEpisode(root) { episode =>
        val current = episode.current(identity)
        current.history.take(originalHistory.size) shouldBe originalHistory
        episode.retained(identity, target.sourceRevision, "check-result-0") shouldBe None
        val result = episode.retained(identity, target.sourceRevision, "check-result-0-retry-1").get
        result.payload.newAttempts.size shouldBe 1
        result.payload.receipts.head.exitCode shouldBe Some(0)
        result.payload.receipts.head.sbt.get.kind shouldBe WorkflowSbtValidation.Kind.Compile
      }
      cli.invocations.map(_.pid).distinct.size shouldBe cli.invocations.size
    }

    "prepare a fresh authorized installed SIGTERM fixture without advancing or claiming cancellation" in {
      Given("SBTR-06 final policy: an explicit installed CLI identity and a new ordinary two-check recovery fixture")
      val configured = sys.props.getOrElse("smworkflow.spec.installed",
        cancel("Opt-in SIGTERM fixture preparation requires -Dsmworkflow.spec.installed=<absolute installed sm-workflow>"))
      val supplied = Path.of(configured)
      supplied.isAbsolute shouldBe true
      val installed = supplied.toRealPath()
      Files.isRegularFile(installed) shouldBe true
      Files.isExecutable(installed) shouldBe true
      val root = freshRecoveryRoot("platform-installed-sigterm")
      TypedSbtHostPlanFixture.build(root, true)
      TypedSbtHostPlanFixture.buildCompile(root, true)
      val buildfile = root.resolve("repository/build.sbt")
      val originalbuild = Files.readString(buildfile)
      val markerline = "  IO.append(root.getParentFile / \"compile-generations.txt\""
      originalbuild should include(markerline)
      val managed = """  val descendant = new java.lang.ProcessBuilder("/bin/sh", "-c",
        |    "trap '' TERM; echo $$ > \"$1\"; echo TERM_RESISTANT_DESCENDANT_READY; exec /bin/sleep 60",
        |    "test-owned-descendant", (root.getParentFile / "managed-descendant.pid").getCanonicalPath).inheritIO().start()
        |  val ready = root.getParentFile / "managed-descendant.pid"
        |  val until = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        |  while (!ready.isFile && System.nanoTime() < until) Thread.sleep(10L)
        |  require(ready.isFile, "Test-owned managed descendant did not record its PID")
        |  require(IO.read(ready).trim == descendant.pid().toString, "Managed descendant identity differs")
        |  System.out.println("COMPILE_BEFORE_CANCEL_STDOUT")
        |  System.err.println("COMPILE_BEFORE_CANCEL_STDERR")
        |""".stripMargin
      Files.writeString(buildfile, originalbuild.replace(markerline, managed + markerline)
        .replace("  Seq(generated)", "  Thread.sleep(60000L)\n  Seq(generated)"))
      val (identity, _) = unknown(root, sequential = true)
      val cli = new Cli(root, identity)
      val (descriptioncall, description) = cli.describe()
      val target = take(R.targetC(description.target))
      val input = cli.prepareDirection(description, descriptioncall.stdout, "AuthorizeNewAttempt",
        V.WorkflowArtifactReference("installed-sigterm-fixture-human-direction", "1"))
      When("normal recovery accepts only the new test-owned authorization and all runtime processes close")
      val authorizationcall = cli.recover(input)
      val authorized = authorizationcall.business[V.GoalPhaseValidationRecoveryResult]
      Then("the fixture retains actual baseline bytes and references, with neither Compile nor cancellation performed")
      authorized.accepted shouldBe true
      authorized.outcome shouldBe "new-attempt-authorized"
      compileExecutions(root) shouldBe empty
      Files.exists(root.resolve("managed-descendant.pid")) shouldBe false
      val baseline = root.resolve("caller/installed-sigterm-before.sqlite")
      Files.copy(root.resolve("operation.sqlite"), baseline)
      val output = Files.createDirectory(root.resolve("caller/installed-sigterm-output"))
      val metadata = root.resolve("caller/installed-sigterm-fixture.json")
      val value = Json.obj("schema" -> Json.fromString("sm-workflow.installed-sigterm-fixture.v1"),
        "root" -> Json.fromString(root.toString), "sessionId" -> Json.fromString(identity),
        "installed" -> Json.fromString(installed.toString), "baselineDatabase" -> Json.fromString(baseline.toString),
        "descriptionReference" -> Json.fromString(descriptioncall.stdout.toString),
        "authorizationReference" -> Json.fromString(authorizationcall.stdout.toString),
        "sourceRevision" -> Json.fromString(target.sourceRevision), "planIdentity" -> Json.fromString(target.planIdentity),
        "planRevision" -> Json.fromString(target.planRevision),
        "base" -> Json.obj("head" -> Json.fromString(target.base.head), "tree" -> Json.fromString(target.base.tree)),
        "responseReference" -> Json.fromString(output.resolve("response.json").toString),
        "stderrReference" -> Json.fromString(output.resolve("stderr.log").toString),
        "pointerAuthority" -> Json.False)
      Files.writeString(metadata, value.spaces2 + "\n")
      Files.writeString(fixtureDirectory.resolve("latest-installed-sigterm-fixture.json"), value.spaces2 + "\n")
    }

    for (successful <- Vector(true, false)) {
      val scenario = if (successful) "sequential Compile" else "nonzero Compile"
      s"prepare a fresh authorized normal-platform $scenario fixture without advancing or claiming execution" in {
      Given("R6: a new test-owned normal Session with ordinary v3 process grants and an interrupted original selected intent")
      val root = freshRecoveryRoot(if (successful) "platform-sequential" else "platform-nonzero")
      val (identity, originalhistory) = unknown(root, sequential = successful, successful = successful)
      val cli = new Cli(root, identity)
      When("fresh normal commands describe and authorize one explicit test-owned HumanNewAttempt direction")
      val (descriptioncall, description) = cli.describe()
      val input = cli.prepareDirection(description, descriptioncall.stdout, "AuthorizeNewAttempt",
        V.WorkflowArtifactReference("normal-platform-fixture-human-direction", "1"))
      val authorizationcall = cli.recover(input)
      val accepted = authorizationcall.business[V.GoalPhaseValidationRecoveryResult]
      val pointername = if (successful) "latest-platform-episode.json" else "latest-platform-failure-episode.json"
      val checks = if (successful) Vector("compile", "compile-second") else Vector("compile")
      cli.retainPointer(pointername, descriptioncall, Some(authorizationcall), input, checks,
        if (successful) "ReviewStep" else "failed-selected-check")
      Then("the original history and authorization are durable while the new installed-platform Advance remains unperformed")
      accepted.accepted shouldBe true
      accepted.outcome shouldBe "new-attempt-authorized"
      compileExecutions(root) shouldBe empty
      Files.isRegularFile(root.resolve(".textus/config.json")) shouldBe true
      Files.isRegularFile(root.resolve(".textus/assembly.yaml")) shouldBe true
      Files.isRegularFile(root.resolve("operation.sqlite")) shouldBe true
      val pointer = parse(Files.readString(fixtureDirectory.resolve(pointername))).toOption.get
      pointer.hcursor.get[String]("root").toOption shouldBe Some(root.toString)
      pointer.hcursor.get[String]("sessionId").toOption shouldBe Some(identity)
      pointer.hcursor.get[Boolean]("platformExecutionObserved").toOption shouldBe Some(false)
      pointer.hcursor.get[Boolean]("pointerAuthority").toOption shouldBe Some(false)
      pointer.hcursor.get[Vector[String]]("expectedChecks").toOption shouldBe Some(checks)
      val configuration = Files.readString(root.resolve(".textus/config.json"))
      val config = parse(configuration).toOption.get
      val plan = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
      plan.hcursor.get[String]("schema").toOption shouldBe Some("cncf.runtime-process-execution.v3")
      plan.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.size shouldBe checks.size
      withRecoveryEpisode(root, sequential = successful) { episode =>
        val retained = episode.current(identity)
        retained.history.dropRight(1) shouldBe originalhistory
        retained.causation.value shouldBe "ordered-validation:check-recovery-0"
      }
      // Reopening verifies native history only; preserve the independently
      // provisioned StartupImport selection for the later installed CLI call.
      Files.writeString(root.resolve(".textus/config.json"), configuration)
      }
    }
  }
}
