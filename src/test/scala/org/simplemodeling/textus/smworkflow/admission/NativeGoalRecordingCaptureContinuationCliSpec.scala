package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import org.goldenport.record.RecordPresentable
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Normal installed public operations on one fresh isolated repair lifecycle.
  * Reviews and authority are SPECIFICATION-OWNED fixture inputs. No production
  * Session or native row is edited and no source-classpath CLI is substituted.
  * @since   Oct. 30, 2026
  * @version Oct. 30, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalRecordingCaptureContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _required(name: String): Path = {
    val path = Path.of(sys.props.getOrElse(name, throw new IllegalArgumentException("Missing " + name)))
    require(path.isAbsolute && path.normalize == path, name + " must be absolute and normalized")
    path.toRealPath()
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _save(s: F.Scenario, name: String, value: RecordPresentable): String = {
    val path = s.root.resolve(name + ".json")
    Files.writeString(path, _json(value), StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _result(outcome: String, issues: Option[V.GoalPhaseIssueReport] = None,
    unused: Option[String] = None): V.GoalPhaseWorkResult = V.GoalPhaseWorkResult(outcome,
    "SPECIFICATION-OWNED assigned source correction and independent review", Vector.empty, None, None, issues,
    None, None, None, None, None, dependencyValidation = None,
    diagnosticRepair = None)
  private final class Cli(s: F.Scenario, executable: Path, image: Path) {
    def call(service: String, operation: String, parameters: Vector[String]): F.Result =
      s.callInstalled(executable, service, operation, parameters, Some(image), Some(s.root.resolve(".textus/assembly.yaml")))
    def goal(operation: String, parameters: Vector[String]): F.Result = call("GoalPhaseService", operation, parameters)
    def advance(session: String): F.Result = goal("advanceGoalPhase", Vector("--sessionId", session))
    def prepare(operation: String, value: RecordPresentable): Unit = {
      val response = call("NativeWorkflowPreparationService", operation, Vector("--input", _json(value)))
      withClue(response.invocation.logs) { response.exitCode shouldBe 0 }
      s.selectPreparedImport(response)
    }
    def submit(request: V.GoalPhaseAdvanceResult, result: V.GoalPhaseWorkResult, name: String,
      execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
      val response = goal(request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
        "--result", _json(result), "--resultReference", _json(V.WorkflowArtifactReference(name, "1"))) ++
        execution.toVector.flatMap(value => Vector("--executionEvidence", _json(value))))
      response.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    }
    def review(actual: F.Result, name: String): Unit = {
      val work = actual.business[V.GoalPhaseAdvanceResult]
      val unresolved = work.work.get.issues.toVector.flatMap(_.issues).filter(_.status != "RESOLVED")
      val issues = Option.when(unresolved.nonEmpty)(V.GoalPhaseIssueReport(unresolved.map(issue =>
        V.GoalPhaseIssueDisposition(issue.identity, "RESOLVED", "Independent fixture review confirms the assigned correction", None))))
      val result = _result("accept", issues)
      val reference = V.WorkflowArtifactReference(name, "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
      prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "review", actual.invocation.stdout.toUri.toASCIIString,
        _save(s, name + "-result", result), _save(s, name + "-reference", reference), _save(s, name + "-execution", execution),
        "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence", None))
      submit(work, result, name, Some(execution))
    }
    def authority(actual: F.Result, name: String): Unit = prepare("prepareAuthority", V.WorkflowAuthorityPreparationInput(
      actual.invocation.stdout.toUri.toASCIIString, _save(s, name, V.WorkflowArtifactReference(name, "1")),
      "participant", "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence"))
  }


  private def _config(s: F.Scenario): Json = parse(Files.readString(s.root.resolve(".textus/config.json"))).toOption.get
  private def _writeConfig(s: F.Scenario, value: Json): Unit =
    Files.writeString(s.root.resolve(".textus/config.json"), value.noSpaces)
  private def _process(s: F.Scenario): Json =
    parse(_config(s).hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
  private def _selectProcess(s: F.Scenario, value: Json): Unit =
    _writeConfig(s, _config(s).mapObject(_.add("textus.subsystem.process-execution.plan", Json.fromString(value.noSpaces))))
  private def _without(s: F.Scenario, operation: String, selection: String): Json = {
    val original = _process(s)
    val programs = original.hcursor.get[Vector[Json]]("gitPrograms").toOption.get
    val removed = programs.filter(p => p.hcursor.get[String]("operation").toOption.contains(operation) &&
      p.hcursor.get[String]("selectionId").toOption == Option.when(selection.nonEmpty)(selection))
    removed.size shouldBe 1
    val capabilities = removed.map(_.hcursor.get[String]("capability").toOption.get).toSet
    val grants = original.hcursor.get[Vector[Json]]("grants").toOption.get.map(_.mapObject(fields =>
      fields.add("capabilities", Json.fromValues(fields("capabilities").get.asArray.get.filterNot(j =>
        j.asString.exists(capabilities))))))
    _selectProcess(s, original.mapObject(_.add("gitPrograms", Json.fromValues(programs.filterNot(removed.contains)))
      .add("grants", Json.fromValues(grants))))
    original
  }
  private def _limitTreeRead(s: F.Scenario): Json = {
    val original = _process(s)
    val programs = original.hcursor.get[Vector[Json]]("gitPrograms").toOption.get
    val selected = programs.filter(_.hcursor.get[String]("operation").toOption.contains("tree"))
    selected.size shouldBe 1
    val limited = programs.map { p => if (!selected.contains(p)) p else p.mapObject(fields =>
      fields.add("limits", fields("limits").get.mapObject(_.add("stdoutBytes", Json.fromLong(64))))) }
    _selectProcess(s, original.mapObject(_.add("gitPrograms", Json.fromValues(limited))))
    original
  }
  private def _reviewed(s: F.Scenario, cli: Cli): (String, V.GoalPhaseAdvanceResult) = {
    val start = cli.goal("startGoalPhase", Vector("--input", s.input.noSpaces)).business[V.GoalPhaseStartResult]
    val implementation = cli.advance(start.sessionId).business[V.GoalPhaseAdvanceResult]
    Files.writeString(Path.of(s.host.target.root).resolve("value.txt"), "expected\n")
    cli.submit(implementation, _result("completed"), "record-capture-implementation")
    val step = cli.advance(start.sessionId)
    step.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewStep"
    cli.review(step, "record-capture-step-review")
    val stepwait = cli.advance(start.sessionId)
    cli.authority(stepwait, "record-capture-step-authority")
    val phase = cli.advance(start.sessionId)
    val request = phase.business[V.GoalPhaseAdvanceResult]
    request.work.get.kind shouldBe "ReviewPhase"
    cli.review(phase, "record-capture-phase-review")
    (start.sessionId, request)
  }

  "Installed Phase recording capture continuation" should {
    "resume only missing read preparation after actual successful staging and refuse a changed record policy" in {
      Given("an installed host whose unchanged release selection contains source directories and a tracked completion record")
      val s = F.freshPhaseRecordingCapture("p99-recording-read-capture", TypedSbtHostPlanFixture.normalExecutable)
      val cli = new Cli(s, _required("smworkflow.spec.installedCommand"), _required("smworkflow.spec.installations"))
      val (id, review) = _reviewed(s, cli)
      val beforehead = s.candidateHead
      val repository = Path.of(s.host.target.root)
      val beforetext = Files.readString(repository.resolve("docs/phase.md"))
      val checks = Files.readString(s.root.resolve("sbt-executions.txt"))
      val releasePrograms = _process(s).hcursor.get[Vector[Json]]("gitPrograms").toOption.get.filter(p =>
        p.hcursor.get[String]("selectionId").toOption.contains("goal-step-2"))
      releasePrograms.size shouldBe 4
      releasePrograms.foreach(_.hcursor.get[Vector[String]]("selectedPaths").toOption.get shouldBe
        Vector("value.txt", "src/main", "src/test", "docs/phase.md"))
      val process = _limitTreeRead(s)
      When("Advance stages the record then its narrowed TreeEntries read exceeds the deliberately small read-output bound")
      val failed = cli.advance(id)
      failed.exitCode should not be 0
      failed.invocation.logs should include ("output-limit-exceeded(stream=stdout)")
      s.candidateHead shouldBe beforehead
      s.candidateIndex shouldBe ""
      Files.readString(repository.resolve("docs/phase.md")) shouldBe beforetext
      Files.readString(s.root.resolve("sbt-executions.txt")) shouldBe checks
      _selectProcess(s, process)

      s.selectRecordingIndexCounterexample(changed = true)
      val crossed = cli.advance(id)
      crossed.exitCode should not be 0
      s.candidateHead shouldBe beforehead
      Files.readString(repository.resolve("docs/phase.md")) shouldBe beforetext
      s.selectRecordingIndexCounterexample(changed = false)

      val policy = _config(s).hcursor.get[String](CompletionRecords.declaration.name).toOption.get
      _writeConfig(s, _config(s).mapObject(_.add(CompletionRecords.declaration.name,
        Json.fromString("""{"targets":[]}"""))))
      val refused = cli.advance(id)
      refused.exitCode should not be 0
      s.candidateHead shouldBe beforehead
      Files.readString(repository.resolve("docs/phase.md")) shouldBe beforetext
      _writeConfig(s, _config(s).mapObject(_.add(CompletionRecords.declaration.name, Json.fromString(policy))))

      Then("restoring only the original bindings permits normal capture Final and Terminal without repeating accepted work")
      val wait = cli.advance(id)
      val current = wait.business[V.GoalPhaseAdvanceResult]
      current.kind shouldBe "Wait"
      current.repairProgress shouldBe review.repairProgress
      cli.authority(wait, "record-capture-final-authority")
      var finalresult = cli.advance(id)
      for (n <- 1 to 3 if finalresult.business[V.GoalPhaseAdvanceResult].kind != "Terminal") {
        val value = finalresult.business[V.GoalPhaseAdvanceResult]
        value.kind shouldBe "Wait"
        cli.authority(finalresult, "record-capture-final-authority-" + n)
        finalresult = cli.advance(id)
      }
      val terminal = finalresult.business[V.GoalPhaseAdvanceResult]
      terminal.kind shouldBe "Terminal"
      terminal.outcome shouldBe Some("completed")
      terminal.completion.get.originalBase shouldBe review.work.get.originalBase
      terminal.completion.get.completed shouldBe review.work.get.completed
      s.candidateParent shouldBe beforehead
      val after = Files.readString(repository.resolve("docs/phase.md"))
      after should startWith (beforetext)
      val marker = "<!-- sm-workflow completion " + id + " phase " + s.plan.phase + " -->"
      after.sliding(marker.length).count(_ == marker) shouldBe 1
      s.candidateIndex shouldBe ""
      Files.readString(s.root.resolve("sbt-executions.txt")) shouldBe checks
      val release = s.candidateHead
      cli.advance(id).business[V.GoalPhaseAdvanceResult].kind shouldBe "Terminal"
      s.candidateHead shouldBe release
      Files.readString(repository.resolve("docs/phase.md")) shouldBe after
      Files.readString(s.root.resolve("sbt-executions.txt")) shouldBe checks
      Files.writeString(s.root.resolve("recording-capture-result.json"), Json.obj(
        "sessionId" -> id.asJson, "acceptedSource" -> beforehead.asJson, "releaseCommit" -> release.asJson,
        "failedCapture" -> failed.invocation.stdout.toString.asJson, "refusedPolicy" -> refused.invocation.stdout.toString.asJson,
        "refusedIndex" -> crossed.invocation.stdout.toString.asJson,
        "terminal" -> parse(_json(terminal)).toOption.get).noSpaces, StandardOpenOption.CREATE_NEW)
    }

    "retain an actual staging intent without result as unknown even after its missing program is restored" in {
      Given("another isolated installed Session with accepted Phase review")
      val s = F.freshPhaseRecordingCapture("p99-recording-unknown-stage", TypedSbtHostPlanFixture.normalExecutable)
      val cli = new Cli(s, _required("smworkflow.spec.installedCommand"), _required("smworkflow.spec.installations"))
      val (id, _) = _reviewed(s, cli)
      val head = s.candidateHead
      val file = Path.of(s.host.target.root).resolve("docs/phase.md")
      val before = Files.readString(file)
      val checks = Files.readString(s.root.resolve("sbt-executions.txt"))
      val process = _without(s, org.goldenport.cncf.processexecution.GitOperationKind.StageSelectedAll.wireName, "goal-path-4")
      val failed = cli.advance(id)
      failed.exitCode should not be 0
      _selectProcess(s, process)
      When("a new normal Advance sees the actual retained intent with no actual stage success")
      val wait = cli.advance(id).business[V.GoalPhaseAdvanceResult]
      Then("it remains a bounded Wait and never stages writes commits or repeats checks")
      wait.kind shouldBe "Wait"
      s.candidateHead shouldBe head
      s.candidateIndex shouldBe ""
      Files.readString(file) shouldBe before
      Files.readString(s.root.resolve("sbt-executions.txt")) shouldBe checks
      cli.advance(id).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      s.candidateHead shouldBe head
      Files.readString(file) shouldBe before
    }

    "refuse a symlink completion record through the unchanged regular-only typed Git decoder" in {
      Given("a broad source selection and a tracked Markdown record that is actually a symlink")
      val s = F.freshPhaseRecordingCapture("p99-recording-symlink", TypedSbtHostPlanFixture.normalExecutable,
        symlinkRecord = true)
      val cli = new Cli(s, _required("smworkflow.spec.installedCommand"), _required("smworkflow.spec.installations"))
      val (id, _) = _reviewed(s, cli)
      val head = s.candidateHead
      val repository = Path.of(s.host.target.root)
      val before = Files.readString(repository.resolve("docs/phase-target.md"))
      val checks = Files.readString(s.root.resolve("sbt-executions.txt"))
      When("normal Phase closure observes only the exact record path in the accepted tree")
      val refused = cli.advance(id)
      Then("the existing decoder rejects the unsupported mode before any projection or commit")
      refused.exitCode should not be 0
      refused.invocation.logs should include ("invalid-output")
      s.candidateHead shouldBe head
      Files.isSymbolicLink(repository.resolve("docs/phase.md")) shouldBe true
      Files.readString(repository.resolve("docs/phase-target.md")) shouldBe before
      Files.readString(s.root.resolve("sbt-executions.txt")) shouldBe checks
    }
  }
}
