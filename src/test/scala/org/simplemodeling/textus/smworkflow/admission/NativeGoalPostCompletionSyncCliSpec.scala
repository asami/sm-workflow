package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.file.{Files, Path}
import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.convert.ValueReader
import org.goldenport.record.RecordPresentable
import org.goldenport.record.io.RecordDecoder
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.{GivenWhenThen}
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseCompletionPolicyAdoption as A
import SplitPhaseExplicitPartitionFixture.take

/** Installed public completion/adoption and ordinary evidence preparation on
  * newly created test-owned repositories. No persisted state is seeded/patched.
  * @since   Oct. 23, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalPostCompletionSyncCliSpec extends AnyWordSpec with GivenWhenThen with GoalPhasePostCompletionSyncFixture {
  private def _required(name: String): Path = {
    val path = Path.of(sys.props.getOrElse(name, throw new IllegalArgumentException("Missing " + name)))
    require(path.isAbsolute, name + " must be absolute")
    path.toRealPath()
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _read(path: Path): Json = parse(Files.readString(path)).fold(error => throw new AssertionError(error.message), identity)
  private final case class Invocation(exit: Int, stdout: Path, stderr: Path) {
    def business[A: ValueReader]: A = {
      withClue(Files.readString(stderr)) { exit shouldBe 0 }
      take(summon[ValueReader[A]].readC(take(new RecordDecoder().json(Files.readString(stdout)))))
    }
  }
  private final class Cli(root: Path, executable: Path, installations: Path, identity: String) {
    private var _ordinal = 0
    def call(service: String, operation: String, arguments: Vector[String]): Invocation = {
      val logs = Files.createDirectories(root.resolve("post-completion-cli-logs"))
      val index = _ordinal
      _ordinal += 1
      val stdout = logs.resolve(index + ".stdout.json")
      val stderr = logs.resolve(index + ".stderr.log")
      val builder = new ProcessBuilder((Vector(executable.toString, "--runtime-root", root.toString, "command",
        SmWorkflowComponent.componentId.name + "." + service + "." + operation) ++ arguments ++ Vector("--json")).asJava)
        .directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile)
      builder.environment().put("SM_WORKFLOW_INSTALLATIONS", installations.toString)
      val process = builder.start()
      try {
        process.getOutputStream.close()
        withClue(Files.readString(stderr)) { process.waitFor(180L, TimeUnit.SECONDS) shouldBe true }
        Invocation(process.exitValue(), stdout, stderr)
      } finally {
        if (process.isAlive) {
          val stream = process.toHandle.descendants()
          val children = try stream.iterator().asScala.toVector finally stream.close()
          process.destroy()
          children.filter(_.isAlive).foreach(_.destroy())
          if (!process.waitFor(10L, TimeUnit.SECONDS)) process.destroyForcibly()
          children.filter(_.isAlive).foreach(_.destroyForcibly())
          process.waitFor(10L, TimeUnit.SECONDS) shouldBe true
        }
        process.getInputStream.close()
        process.getErrorStream.close()
      }
    }
    def goal(operation: String, input: RecordPresentable): Invocation =
      call("GoalPhaseService", operation, Vector("--input", _json(input)))
    def advance(): Invocation = call("GoalPhaseService", "advanceGoalPhase", Vector("--sessionId", identity))
    def describeEvidence(reference: V.EvidenceReference): V.EvidenceDetailResult =
      call("EvidenceService", "describeEvidence", Vector("--input",
        _json(V.EvidenceDescribeInput(reference.identity, Some(reference.revision))))).business[V.EvidenceDetailResult]
    private def _file(name: String, value: RecordPresentable): String = {
      val path = Files.createTempDirectory(root, name + "-").resolve("input.json")
      Files.writeString(path, _json(value))
      path.toUri.toASCIIString
    }
    def prepareDirection(described: Invocation, choice: String, name: String): V.GoalPhaseCompletionPolicyAdoptionInput = {
      val description = described.business[V.GoalPhaseCompletionPolicyDescriptionResult]
      val reference = V.WorkflowArtifactReference(name, "1")
      val input = V.GoalPhaseCompletionPolicyAdoptionInput(identity, description.target, choice,
        "Fixture-owned independent Human selects local completion of already accepted Phase work", reference)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val evidence = V.WorkflowEvidencePreparationInput("GoalPhase", "completion-policy-adoption",
        described.stdout.toUri.toASCIIString, _file("direction", input), _file("reference", reference),
        _file("execution", execution), "normal-development-host", "2026-10-07T00:00:00Z",
        "entity:smworkflow.cli.host_evidence", Some("participant"))
      val prepared = call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(evidence)))
      withClue(Files.readString(prepared.stderr)) { prepared.exit shouldBe 0 }
      _read(prepared.stdout).hcursor.downField("datastore").focus.get.asArray.get should not be empty
      val config = root.resolve(".textus/config.json")
      Files.writeString(config, _read(config).mapObject(_.add("textus.import.data.file", Json.fromString(prepared.stdout.toString))).noSpaces)
      input
    }
  }

  "Installed GoalPhase completion and policy preparation" should {
    "retain installed Evidence lifetime on fresh completion and later already-Completed projection" in {
      object Evidence extends GoalPhasePostCompletionEvidenceFixture
      Given("GPSYNC-EVIDENCE-01: an installed runtime and a new test-owned Phase with real Test stdout in the managed Evidence archive")
      val executable = _required("smworkflow.spec.installedCommand")
      val installations = _required("smworkflow.spec.runtimeRoot")
      Files.isExecutable(executable) shouldBe true
      val root = Evidence.freshRoot("installed-evidence-lifetime")
      var identity = ""
      var reference = Option.empty[V.EvidenceReference]
      var original = Option.empty[V.EvidenceDetailResult]
      var completed = Option.empty[InstanceRecord]
      Evidence.episode(root, initialize = true, componentInstance = "default") { e =>
        identity = e.closeDevelopment()._1
        e.releasedEvidence should not be empty
        e.releasedEvidence.head.status shouldBe "stored"
        reference = e.releasedEvidence.head.evidence
        original = Some(e.describeEvidence(reference.get))
        original.get.status shouldBe "active"
        original.get.read.get.status shouldBe "found"
        original.get.read.get.text.get should not be empty
      }
      val checks = Evidence.phaseExecutions(root)
      Evidence.prepareInstalled(root)
      val cli = new Cli(root, executable, installations, identity)
      When("the normal installed Advance completes the Phase and a new installed EvidenceService call describes the real stdout")
      val terminal = cli.advance().business[V.GoalPhaseAdvanceResult]
      val detail = cli.describeEvidence(reference.get)
      Evidence.episode(root, componentInstance = "default") { e => completed = Some(e.current(identity)) }
      Then("the installed result keeps sync deferred while Evidence refers to the actual Completed revision and preserves its payload")
      terminal.kind shouldBe "Terminal"
      terminal.outcome shouldBe Some("completed")
      terminal.completion.get.synchronization.state shouldBe "deferred"
      completed.get.lifecycle shouldBe Lifecycle.Completed
      detail.status shouldBe "completed"
      detail.lifecycle.get.status shouldBe "completed"
      detail.lifecycle.get.completedAt should not be empty
      val instant = Instant.parse(detail.lifecycle.get.completedAt.get)
      detail.lifecycle.get.expiresAt shouldBe Some(instant.plus(Duration.ofDays(30)).toString)
      detail.lifecycle.get.completionReference shouldBe Some(V.WorkflowArtifactReference(identity,
        completed.get.revision.value.toString))
      detail.read shouldBe original.get.read
      detail.representation shouldBe original.get.representation
      Evidence.phaseExecutions(root) shouldBe checks
      Evidence.prepareInstalled(root)
      When("later installed commands project the already-Completed Session and freshly describe Evidence again")
      val reopened = cli.advance().business[V.GoalPhaseAdvanceResult]
      val retained = cli.describeEvidence(reference.get)
      Then("the first lifetime marker and actual native history survive without checks, work or synchronization replay")
      reopened shouldBe terminal
      retained shouldBe detail
      Evidence.episode(root, componentInstance = "default") { e => Evidence.sameInstance(e.current(identity), completed.get) }
      Evidence.phaseExecutions(root) shouldBe checks
    }

    "display completed Phase and deferred sync while preserving unrelated bytes and accepted checks" in {
      Given("GPSYNC-01,02,08: required absolute installed command and installations plus an explicitly local-policy fixture Start")
      val executable = _required("smworkflow.spec.installedCommand")
      val installations = _required("smworkflow.spec.runtimeRoot")
      Files.isExecutable(executable) shouldBe true
      val root = freshRoot("installed-local-completion")
      var identity = ""
      var candidate = ""
      episode(root, initialize = true, componentInstance = "default") { e =>
        val accepted = e.closeDevelopment()
        identity = accepted._1
        candidate = accepted._2.candidate.head
      }
      Files.writeString(root.resolve("repository/unrelated.txt"), "unrelated installed fixture bytes\n")
      val executions = phaseExecutions(root)
      val cli = new Cli(root, executable, installations, identity)
      When("the installed ordinary Advance completes and reopens the same accepted Phase")
      val completed = cli.advance().business[V.GoalPhaseAdvanceResult]
      val reopened = cli.advance().business[V.GoalPhaseAdvanceResult]
      Then("the actual public result independently displays completed development and deferred synchronization")
      completed.kind shouldBe "Terminal"
      completed.outcome shouldBe Some("completed")
      completed.completion.get.reviewedCandidate.head shouldBe candidate
      completed.completion.get.synchronization.state shouldBe "deferred"
      completed.completion.get.synchronization.reason should not be empty
      completed.completion.get.repository shouldBe None
      reopened shouldBe completed
      phaseExecutions(root) shouldBe executions
      Files.readString(root.resolve("repository/unrelated.txt")) shouldBe "unrelated installed fixture bytes\n"
      episode(root, componentInstance = "default")(_.current(identity).lifecycle shouldBe Lifecycle.Completed)
    }

    "adopt an explicitly strict retained frontier through installed public description preparation and adoption" in {
      object Strict extends GoalPhasePostCompletionSyncFixture {
        override protected def goal_completion_policy: String = GoalPhasePostCompletionSync.strictPolicy
      }
      Given("GPSYNC-06,08: a NEW explicitly strict normal Start with real accepted development and no child authority")
      val executable = _required("smworkflow.spec.installedCommand")
      val installations = _required("smworkflow.spec.runtimeRoot")
      val root = Strict.freshRoot("installed-policy-adoption")
      var identity = ""
      var history = Vector.empty[HistoryEntry]
      Strict.episode(root, initialize = true, componentInstance = "default") { e =>
        identity = e.closeDevelopment()._1
        history = e.current(identity).history
      }
      val cli = new Cli(root, executable, installations, identity)
      cli.advance().business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      val described = cli.goal(A.describeOperation, V.GoalPhaseCompletionPolicyDescriptionInput(identity))
      val description = described.business[V.GoalPhaseCompletionPolicyDescriptionResult]
      val target = take(A.targetC(description.target))
      val executions = Strict.phaseExecutions(root)
      When("normal installed preparation imports an independent Defer then exact AdoptLocalCompletion direction")
      val deferred = cli.prepareDirection(described, "Defer", "installed-policy-defer")
      cli.goal(A.adoptionOperation, deferred).business[V.GoalPhaseCompletionPolicyAdoptionResult].outcome shouldBe "deferred"
      Strict.episode(root, componentInstance = "default")(_.current(identity).history shouldBe history)
      val input = cli.prepareDirection(described, "AdoptLocalCompletion", "installed-policy-adopt")
      cli.goal(A.adoptionOperation, input).business[V.GoalPhaseCompletionPolicyAdoptionResult].outcome shouldBe "local-completion-adopted"
      val result = cli.advance().business[V.GoalPhaseAdvanceResult]
      Then("the original accepted work completes without a new check review child Start or saved Start mutation")
      result.kind shouldBe "Terminal"
      result.completion.get.reviewedCandidate.head shouldBe target.input.candidate.head
      result.completion.get.originalBase.head shouldBe target.input.base.head
      result.completion.get.completed.map(_.commit.identity) shouldBe target.input.completed.map(_.commit.identity)
      result.completion.get.synchronization.state shouldBe "deferred"
      cli.advance().business[V.GoalPhaseAdvanceResult] shouldBe result
      Strict.phaseExecutions(root) shouldBe executions
      Strict.episode(root, componentInstance = "default") { e =>
        e.current(identity).lifecycle shouldBe Lifecycle.Completed
        e.current(identity).history.take(history.size) shouldBe history
      }
    }
  }
}
