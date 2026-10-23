package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.sql.DriverManager
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalTransition, WorkflowMechanicalTransitionJsonV1}
import org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec
import org.goldenport.record.RecordPresentable
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseSourceHistoryRecovery as R
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Every episode owns a fresh normal SQLite host. Reads below only observe
  * standard native codec bytes; no private row is ever seeded or changed.
  * Optional installed-command qualification uses the same isolated host.
  * @since   Oct. 21, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
final class NativeGoalSourceHistoryRecoveryCliSpec extends AnyWordSpec with NativeGoalSourceHistoryCliFixture {
  private lazy val _catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(GoalPhaseRuntimeCatalog.loadC()).definition)))
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _call(fixture: Implemented, service: String, operation: String,
    arguments: Vector[String]): F.Result = sys.props.get("smworkflow.spec.installedCommand") match {
    case None => if (service == "GoalPhaseService") fixture.scenario.call("command", operation, arguments)
      else fixture.scenario.prepareHostInput(operation,
        take(new org.goldenport.record.io.RecordDecoder().json(arguments(1))))
    case Some(value) =>
      val executable = Path.of(value)
      executable.isAbsolute shouldBe true
      Files.isRegularFile(executable) shouldBe true
      Files.isExecutable(executable) shouldBe true
      val directory = Files.createTempDirectory(fixture.scenario.root, "installed-recovery-call-")
      val stdout = directory.resolve("response.json")
      val stderr = directory.resolve("stderr.log")
      val argv = Vector(executable.toString, "--runtime-root", fixture.scenario.root.toString, "command",
        SmWorkflowComponent.componentId.name + "." + service + "." + operation) ++ arguments ++ Vector("--json")
      val process = new ProcessBuilder(argv.asJava).directory(fixture.scenario.root.toFile)
        .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      try {
        process.getOutputStream.close()
        if (!process.waitFor(240, TimeUnit.SECONDS)) {
          process.destroyForcibly()
          process.waitFor()
          fail("Owned installed recovery command exceeded its finite deadline: " + Files.readString(stderr))
        }
        F.Result(process.exitValue(), F.Invocation(argv, process.pid(), stdout, stderr))
      } finally {
        if (process.isAlive) { process.destroyForcibly(); process.waitFor() }
        process.getInputStream.close()
        process.getErrorStream.close()
      }
  }
  private def _goal(fixture: Implemented, operation: String, arguments: Vector[String]): F.Result =
    _call(fixture, "GoalPhaseService", operation, arguments)
  private def _advance(fixture: Implemented): F.Result =
    _goal(fixture, "advanceGoalPhase", Vector("--sessionId", fixture.request.sessionId))
  private def _describe(fixture: Implemented): F.Result =
    _goal(fixture, R.describeOperation, Vector("--sessionId", fixture.request.sessionId))
  private def _recover(fixture: Implemented, input: V.GoalPhaseSourceHistoryRecoveryInput): F.Result =
    _goal(fixture, R.recoveryOperation, Vector("--input", _json(input)))

  private final case class HumanAnswer(answer: V.GoalPhaseOrderedDecisionAnswer,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence)
  private def _human_answer(fixture: Implemented, request: Path, choice: String,
    identity: String, prepare: Boolean = true): HumanAnswer = {
    val directory = Files.createTempDirectory(fixture.scenario.root, "ordinary-human-source-history-")
    val answer = V.GoalPhaseOrderedDecisionAnswer(choice, "Test-owned independent Human direction accepting the unknown old effect")
    val reference = V.WorkflowArtifactReference(identity, "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("test-independent-human"), None, None)
    def _file_(name: String, value: RecordPresentable): String = {
      val file = directory.resolve(name + ".json")
      Files.writeString(file, _json(value))
      file.toUri.toASCIIString
    }
    if (prepare) {
      val input = V.WorkflowEvidencePreparationInput("GoalPhase", "human", request.toUri.toASCIIString,
        _file_("answer", answer), _file_("reference", reference), _file_("execution", execution),
        "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", Some("participant"))
      _select_import(fixture, _call(fixture, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(input))))
    }
    HumanAnswer(answer, reference, execution)
  }
  private def _human_submit(fixture: Implemented, question: V.GoalPhaseAdvanceResult, answer: HumanAnswer): F.Result =
    _goal(fixture, question.submitOperation.get, Vector("--sessionId", question.submitSessionId.get,
      "--answer", _json(answer.answer), "--resultReference", _json(answer.reference),
      "--executionEvidence", _json(answer.execution)))

  private def _select_import(fixture: Implemented, prepared: F.Result): Unit = {
    prepared.exitCode shouldBe 0
    val config = fixture.scenario.root.resolve(".textus/config.json")
    val current = parse(Files.readString(config)).toOption.get
    Files.writeString(config, current.mapObject(_.add("textus.import.data.file",
      Json.fromString(prepared.invocation.stdout.toString))).noSpaces)
  }

  private def _direction(fixture: Implemented, description: V.GoalPhaseSourceHistoryRecoveryDescriptionResult,
    request: Path, choice: String, referenceIdentity: String): V.GoalPhaseSourceHistoryRecoveryInput = {
    val root = fixture.scenario.root
    val directory = Files.createTempDirectory(root, "human-source-history-")
    val reference = V.WorkflowArtifactReference(referenceIdentity, "1")
    val answer = V.GoalPhaseSourceHistoryRecoveryAnswer(choice, "Explicit test-owned Human direction accepting possible repeated normalization")
    val answerfile = directory.resolve("answer.json")
    val referencefile = directory.resolve("reference.json")
    val executionfile = directory.resolve("execution.json")
    Files.writeString(answerfile, _json(answer))
    Files.writeString(referencefile, _json(reference))
    Files.writeString(executionfile, _json(V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)))
    val input = V.WorkflowEvidencePreparationInput("GoalPhase", "source-history-recovery", request.toUri.toASCIIString,
      answerfile.toUri.toASCIIString, referencefile.toUri.toASCIIString, executionfile.toUri.toASCIIString,
      "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", Some("participant"))
    val prepared = _call(fixture, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(input)))
    _select_import(fixture, prepared)
    V.GoalPhaseSourceHistoryRecoveryInput(description.sessionId, description.target, choice, answer.rationale, reference)
  }

  /** Isolated observation uses SQLite's read-only URI; only native codec bytes leave this reader. */
  private def _stored_json(fixture: Implemented): Vector[String] = {
    val database = fixture.scenario.root.resolve("workflow.sqlite")
    Files.isRegularFile(database) shouldBe true
    val connection = DriverManager.getConnection("jdbc:sqlite:" + database.toUri.toASCIIString + "?mode=ro")
    try {
      val statement = connection.createStatement()
      try {
        val tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
        val names = Vector.newBuilder[String]
        try { while (tables.next()) names += tables.getString(1) } finally tables.close()
        names.result().flatMap { name =>
          val query = statement.executeQuery("SELECT * FROM \"" + name.replace("\"", "\"\"") + "\"")
          val values = Vector.newBuilder[String]
          try {
            while (query.next()) {
              (1 to query.getMetaData.getColumnCount).foreach { column =>
                Option(query.getString(column)).filter(_.startsWith("cncf.workflow-json.v1:"))
                  .foreach(encoded => values += encoded.stripPrefix("cncf.workflow-json.v1:"))
              }
            }
          } finally query.close()
          values.result()
        }
      } finally statement.close()
    } finally connection.close()
  }
  private def _current(fixture: Implemented): InstanceRecord = {
    val instances = _stored_json(fixture).flatMap(raw => WorkflowInstanceRecordJsonV1.decodeC(raw, _catalog).toOption)
      .filter(_.identity.value == fixture.request.sessionId)
    instances.size shouldBe 1
    instances.head
  }
  private def _receipt[A](fixture: Implemented, reference: WorkflowMechanicalTransition.Reference,
    codec: PayloadCodec[A]): WorkflowMechanicalTransition.Receipt[A] = {
    val values = _stored_json(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, codec).toOption)
      .filter(_.reference == reference)
    values.size shouldBe 1
    values.head
  }

  private def _completed_facts(fixture: Implemented): Vector[String] = _stored_json(fixture).filter { raw =>
    parse(raw).toOption.exists(json =>
      json.hcursor.downField("reference").get[String]("identity").toOption.exists(_.startsWith(fixture.request.sessionId + ":")) &&
        json.hcursor.downField("payload").get[String]("typeIdentity").toOption.exists(kind =>
          Set(GoalPhaseNativeClosure.codec.typeIdentity, GoalPhaseNativeValidation.codec.typeIdentity)(kind)))
  }.sorted

  private final case class DocumentEpisode(fixture: Implemented, plan: GoalPhaseOrderedProgression.Plan,
    phase: Path, checklist: Path, completed: V.GoalPhaseAdvanceResult)
  private val _prefix_paths = (1 to 4).map(index => "src/main/scala/ARecovery" + index + ".scala").toVector
  private val _later_corrected_text = changedText + "// Preserve the compiled value in the second selected Step.\n"
  private def _prefix_text(index: Int, value: Int): String =
    "object ARecovery" + index + " { val value: Int = " + value + " }\n"
  private def _fixture_git(fixture: Implemented, arguments: Vector[String]): String = {
    val directory = Files.createTempDirectory(fixture.scenario.root, "test-owned-integration-")
    val stdout = directory.resolve("stdout")
    val stderr = directory.resolve("stderr")
    val argv = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects", "-C",
      fixture.scenario.host.target.root, "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false",
      "-c", "user.name=Native Workflow Fixture", "-c", "user.email=native-workflow@example.invalid",
      "-c", "user.useConfigOnly=true") ++ arguments
    val process = new ProcessBuilder(argv.asJava).redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
    try {
      process.getOutputStream.close()
      process.waitFor(20, TimeUnit.SECONDS) shouldBe true
      withClue(Files.readString(stderr)) { process.exitValue() shouldBe 0 }
      Files.readString(stdout).trim
    } finally {
      if (process.isAlive) { process.destroyForcibly(); process.waitFor() }
      process.getInputStream.close()
      process.getErrorStream.close()
    }
  }
  private def _write_documents(phase: Path, checklist: Path, plan: GoalPhaseOrderedProgression.Plan): Unit = {
    val bindings = plan.steps.zipWithIndex.map { (step, index) => Json.obj("step" -> Json.fromString(step.identity),
      "items" -> Json.arr(Json.fromString("RECOVERY-" + (index + 1)))) }
    Files.writeString(phase, "# Isolated recovery document input\n\n```sm-workflow-plan\n" +
      Json.obj("schemaVersion" -> Json.fromString("sm-workflow.phase-plan.v1"), "plan" -> plan.asJson,
        "bindings" -> Json.fromValues(bindings)).spaces2 + "\n```\n")
    Files.writeString(checklist, "# Isolated recovery acceptance\n\n" + plan.steps.zipWithIndex.map { (step, index) =>
      "- [ ] RECOVERY-" + (index + 1) + ": Preserve the selected semantics of " + step.identity
    }.mkString("\n") + "\n")
  }
  private def _submit(fixture: Implemented, request: V.GoalPhaseAdvanceResult, name: String,
    outcome: String = "completed", execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
    val result = V.GoalPhaseWorkResult(outcome, "Test-owned selected semantic disposition", Vector.empty,
      None, None, None, None, None, None, None)
    val reference = V.WorkflowArtifactReference(name, "1")
    _goal(fixture, request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
      "--result", _json(result), "--resultReference", _json(reference)) ++
      execution.toVector.flatMap(value => Vector("--executionEvidence", _json(value))))
      .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
  }
  private def _document_episode(name: String, later: Boolean = false): DocumentEpisode = {
    Given("CB1,CB2: a fresh normal source-history host with two document-selected Steps and fixed closure programs")
    val scenario = F.fresh(name, sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), sourceHistory = true)
    val repository = Path.of(scenario.host.target.root)
    val documents = Files.createDirectories(repository.resolve("verification-inputs"))
    Files.writeString(repository.resolve(".git/info/exclude"), "\n/verification-inputs/\n", StandardOpenOption.APPEND)
    val phase = documents.resolve("phase.md")
    val checklist = documents.resolve("checklist.md")
    val candidatepaths = if (later) _prefix_paths :+ sourcePath else scenario.host.goal.candidatePaths
    val plan = scenario.plan.copy(steps = scenario.plan.steps ++ Vector(scenario.plan.steps.head.copy(
      identity = "step-2", purpose = "Retain the selected compiled value and maintain its source", ownedPaths = candidatepaths)))
    _write_documents(phase, checklist, plan)
    val host = scenario.host.copy(goal = scenario.host.goal.copy(candidatePaths = candidatepaths, closure = scenario.host.goal.closure.copy(
      programs = Vector(GoalPhaseNativeClosure.StepProgram("step-1", 1), GoalPhaseNativeClosure.StepProgram("step-2", 2)))))
    val selected = host.target
    val target = org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target(
      Path.of(selected.root), selected.localBranch, selected.remote, selected.remoteBranch, selected.endpoint)
    val git = TypedGitHostPlanFixture.plan(target, goalPaths = host.goal.candidatePaths,
      goalMessages = Vector("Complete first recovery fixture Step", "Complete second recovery fixture Step"),
      goalSelections = Vector.fill(2)(host.goal.candidatePaths))
    val processplan = TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git))
    configure(scenario, enabledSettings ++ Map("sm-workflow.runtime.definition" ->
      Json.fromString(ThreeWorkflowRuntimeParameters.encode(host).noSpaces), "textus.subsystem.process-execution.plan" ->
      Json.fromString(processplan.noSpaces), "textus.resource.url.file.roots" -> Json.fromString(scenario.root.toString)))
    Files.writeString(scenario.root.resolve("goal-plan.json"), Json.obj(
      "schemaVersion" -> Json.fromString(GoalPhaseDocumentSource.declarationSchema),
      "phasePath" -> Json.fromString("verification-inputs/phase.md"),
      "checklistPath" -> Json.fromString("verification-inputs/checklist.md")).noSpaces)
    val prepared = scenario.prepareHostInput("prepareGoalStart", scenario.selectedGoalPreparationInput.toRecord())
    scenario.selectPreparedImport(prepared)
    val task = scenario.ownTask("source-history-document-task")
    if (later) {
      val setup = Implemented(scenario, task, null, repository.resolve(sourcePath))
      _prefix_paths.zipWithIndex.foreach { (path, index) => Files.writeString(repository.resolve(path), _prefix_text(index + 1, 0)) }
      _fixture_git(setup, Vector("--literal-pathspecs", "add", "--") ++ _prefix_paths)
      _fixture_git(setup, Vector("commit", "--no-verify", "--no-gpg-sign", "-m", "Prepare test-owned prefix sources"))
    }
    val started = scenario.start().business[V.GoalPhaseStartResult]
    scenario.retain(task, started)
    val first = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
    val fixture = Implemented(scenario, task, first, repository.resolve(sourcePath))
    first.work.get.selectedPlan.steps.size shouldBe 2
    When("normal implementation, an independently prepared review and separately granted closure complete the first Step")
    Files.writeString(fixture.source, changedText)
    _submit(fixture, first, name + "-implementation-1")
    val reviewcall = _advance(fixture)
    val review = reviewcall.business[V.GoalPhaseAdvanceResult]
    review.work.get.kind shouldBe "ReviewStep"
    val reference = V.WorkflowArtifactReference(name + "-review-1", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("test-independent-reviewer"), None, None)
    val result = V.GoalPhaseWorkResult("accept", "Test-owned selected semantic disposition", Vector.empty,
      None, None, None, None, None, None, None)
    def _file_(name: String, value: RecordPresentable): String = {
      val file = scenario.root.resolve(name + ".json")
      Files.writeString(file, _json(value))
      file.toUri.toASCIIString
    }
    val evidence = V.WorkflowEvidencePreparationInput("GoalPhase", "review", reviewcall.invocation.stdout.toUri.toASCIIString,
      _file_("review-result", result), _file_("review-reference", reference), _file_("review-execution", execution),
      "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", None)
    _select_import(fixture, _call(fixture, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(evidence))))
    _submit(fixture, review, name + "-review-1", "accept", Some(execution))
    val closing = _advance(fixture)
    closing.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
    val authority = V.WorkflowAuthorityPreparationInput(closing.invocation.stdout.toUri.toASCIIString,
      _file_("closure-reference", V.WorkflowArtifactReference(name + "-closure", "1")), "participant",
      "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence")
    _select_import(fixture, _call(fixture, "NativeWorkflowPreparationService", "prepareAuthority", Vector("--input", _json(authority))))
    val second = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    Then("the second assigned implementation retains the first actual check and committed Step")
    second.work.get.kind shouldBe "ImplementStep"
    second.work.get.stepIdentity shouldBe Some("step-2")
    second.work.get.completed.size shouldBe 1
    executions(scenario).size shouldBe 1
    val next = fixture.copy(request = second)
    Given("the second accepted implementation contains a duplicate managed version header")
    if (later) _prefix_paths.zipWithIndex.foreach { (path, index) =>
      Files.writeString(repository.resolve(path), _prefix_text(index + 1, 42))
    }
    Files.writeString(next.source, "/**\n * @version Oct. 20, 2026\n * @version Oct. 21, 2026\n */\n" +
      (if (later) _later_corrected_text else changedText))
    _submit(next, second, name + "-implementation-2")
    When("a normal Advance reaches the second Step's first source-history interpretation failure")
    _advance(next).exitCode should not be 0
    Then("the actual unresolved history intent follows its completed native Step and source-history prefixes")
    _current(next).causation.value shouldBe "ordered-candidate:source-history-intent-" + (if (later) 4 else 0)
    executions(scenario).size shouldBe 1
    DocumentEpisode(next, plan, phase, checklist, second)
  }

  private def _failed(name: String): Implemented = {
    val duplicate = "/**\n * @version Oct. 20, 2026\n * @version Oct. 21, 2026\n */\n" + changedText
    val fixture = implemented(name, enabledSettings, duplicate)
    configure(fixture.scenario, enabledSettings.updated("textus.resource.url.file.roots", Json.fromString(fixture.scenario.root.toString)))
    Given("R1: accepted implementation and a duplicate latest-version header in an isolated normal host")
    val index = indexBytes(fixture.scenario)
    When("normal Advance commits inspection and the first history intent before interpretation rejects the header")
    val failed = _advance(fixture)
    Then("the failed source normalization leaves no staging or product check and retains the native first intent")
    failed.exitCode should not be 0
    failed.invocation.logs should include("duplicate since or latest version")
    Files.readString(fixture.source) shouldBe duplicate
    indexBytes(fixture.scenario) shouldBe index
    executions(fixture.scenario) shouldBe empty
    _current(fixture).causation.value shouldBe "ordered-candidate:source-history-intent-0"
    fixture
  }

  "Public source history recovery through normal generated Operations" should {
    "end prefix reuse after the recovered candidate so a later same-Step repair receives ordinary source normalization" in {
      Given("CB-P116-SOURCE-HISTORY-CONTINUATION-001: an isolated later-intent recovery after a completed first Step and four successful normalization results")
      val episode = _document_episode("source-history-later-ordinary-repair", later = true)
      val fixture = episode.fixture
      val repository = Path.of(fixture.scenario.host.target.root)
      val original = _current(fixture)
      val issued = _advance(fixture)
      val question = issued.business[V.GoalPhaseAdvanceResult]
      val target = take(R.targetC(question.human.get.sourceHistoryRecovery.get))
      val originalintent = _receipt(fixture, WorkflowMechanicalTransition.Reference(target.intentIdentity, target.intentRevision),
        GoalPhaseNativeSourceHistory.codec)
      val prefixreceipts = target.completedPrefix.map(entry => (
        _receipt(fixture, WorkflowMechanicalTransition.Reference(entry.intentIdentity, entry.intentRevision), GoalPhaseNativeSourceHistory.codec),
        _receipt(fixture, WorkflowMechanicalTransition.Reference(entry.resultIdentity, entry.resultRevision), GoalPhaseNativeSourceHistory.codec)))
      val answer = _human_answer(fixture, issued.invocation.stdout, "AuthorizeNewAttempt", "later-ordinary-repair-human")
      When("ordinary Human completion authorizes the new attempt and a corrected fifth source reaches independent Review")
      _human_submit(fixture, question, answer).business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
      Files.writeString(fixture.source, _later_corrected_text)
      val reviewcall = _advance(fixture)
      val review = reviewcall.business[V.GoalPhaseAdvanceResult]
      Then("the recovered candidate completes with its one new actual check and unchanged repair counters")
      target.intentIndex shouldBe 4
      review.work.get.kind shouldBe "ReviewStep"
      review.work.get.stepIdentity shouldBe Some("step-2")
      review.repairProgress shouldBe episode.completed.repairProgress
      executions(fixture.scenario).size shouldBe 2
      val recovered = _current(fixture)

      Given("a separately prepared ordinary independent Review result requesting one same-Step source-header repair")
      val finding = "Maintain the former prefix source during this ordinary same-Step repair"
      val reviewresult = V.GoalPhaseWorkResult("repair", "Test-owned independent review requests the selected source maintenance",
        Vector(finding), None, None, None, None, None, None, None)
      val reviewreference = V.WorkflowArtifactReference("later-ordinary-repair-review", "1")
      val reviewexecution = V.WorkflowExecutionEvidence(Vector(reviewreference), Some("test-independent-reviewer"), None, None)
      def _file_(name: String, value: RecordPresentable): String = {
        val file = fixture.scenario.root.resolve("later-ordinary-repair-" + name + ".json")
        Files.writeString(file, _json(value))
        file.toUri.toASCIIString
      }
      val evidence = V.WorkflowEvidencePreparationInput("GoalPhase", "review", reviewcall.invocation.stdout.toUri.toASCIIString,
        _file_("result", reviewresult), _file_("reference", reviewreference), _file_("execution", reviewexecution),
        "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", None)
      When("normal evidence preparation and ordinary Review Submit request repair of that actual candidate")
      _select_import(fixture, _call(fixture, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(evidence))))
      _goal(fixture, review.submitOperation.get, Vector("--sessionId", review.submitSessionId.get,
        "--result", _json(reviewresult), "--resultReference", _json(reviewreference),
        "--executionEvidence", _json(reviewexecution))).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val repair = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("ordinary RepairStep retains the original accepted Step facts and accounts for its assigned review issue")
      repair.work.get.kind shouldBe "RepairStep"
      repair.work.get.stepIdentity shouldBe Some("step-2")
      repair.work.get.repairDirective.get.findings shouldBe Vector(finding)
      repair.work.get.selectedPlan shouldBe episode.completed.work.get.selectedPlan
      repair.work.get.originalBase shouldBe episode.completed.work.get.originalBase
      repair.work.get.completed shouldBe episode.completed.work.get.completed
      repair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, 1))
      executions(fixture.scenario).size shouldBe 2

      Given("the assigned repair removes a former prefix file's managed header while preserving valid compiled source")
      val path = _prefix_paths.head
      val source = repository.resolve(path)
      val bare = _prefix_text(1, 42) + "// Ordinary same-Step repair after recovery candidate completion.\n"
      Files.writeString(source, bare)
      val dispositions = repair.work.get.issues.toVector.flatMap(_.issues).filter(issue =>
        issue.repository == repair.work.get.selectedPlan.repository && repair.work.get.stepIdentity.contains(issue.step) &&
          issue.status != "RESOLVED").map(issue =>
        V.GoalPhaseIssueDisposition(issue.identity, "FIXED", "Test-owned observation of the assigned source maintenance", None))
      val repairresult = V.GoalPhaseWorkResult("completed", "Test-owned ordinary repair of the assigned former prefix source",
        Vector.empty, None, None, Some(V.GoalPhaseIssueReport(dispositions)), None, None, None, None)
      val repairreference = V.WorkflowArtifactReference("later-ordinary-repair-completed", "1")
      When("ordinary RepairStep completion is followed by a fresh normal Advance")
      _goal(fixture, repair.submitOperation.get, Vector("--sessionId", repair.submitSessionId.get,
        "--result", _json(repairresult), "--resultReference", _json(repairreference)))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val repaired = _current(fixture)
      val freshcall = _advance(fixture)
      val freshreview = freshcall.business[V.GoalPhaseAdvanceResult]
      val completed = _current(fixture)
      val freshhistory = completed.history.drop(repaired.history.size)
      val inspection = freshhistory.find(_.causation.value == "ordered-candidate:inspection").get
      val sourcerevision = (inspection.revision.value - 1).toString
      val intentreference = WorkflowMechanicalTransition.Reference(target.sessionId + ":ordered-candidate:" +
        sourcerevision + ":source-history-intent-0", "1")
      val resultreference = WorkflowMechanicalTransition.Reference(target.sessionId + ":ordered-candidate:" +
        sourcerevision + ":source-history-result-0", "1")
      val intent = _receipt(fixture, intentreference, GoalPhaseNativeSourceHistory.codec)
      val result = _receipt(fixture, resultreference, GoalPhaseNativeSourceHistory.codec)
      val rule = take(WorkflowSourceHistoryRule.decodeC(enabledSettings(ruleKey)))
      val expected = take(WorkflowSourceHistoryRule.applyC(rule, WorkflowSourceHistoryRule.Input(path, bare,
        java.time.LocalDate.parse(intent.payload.value.updatedOn), None, None, true)))
      Then("fresh current rule/date normalization records the former prefix source and actual check before independent Review")
      intent.payload.value.path shouldBe path
      intent.payload.value.sourceRevision shouldBe sourcerevision
      intent.payload.value.outcome shouldBe None
      result.payload.value shouldBe intent.payload.value.copy(outcome = Some("updated"))
      expected.outcome shouldBe "updated"
      Files.readString(source) shouldBe expected.text
      Files.readString(source) should startWith("/**\n * @since   ")
      Files.readString(source) should endWith(bare)
      freshreview.work.get.kind shouldBe "ReviewStep"
      freshreview.work.get.stepIdentity shouldBe Some("step-2")
      freshreview.work.get.review.get.changedPaths should contain(path)
      freshreview.work.get.review.get.checks.head.sbt.get.outcome shouldBe "succeeded"
      freshreview.work.get.selectedPlan shouldBe episode.completed.work.get.selectedPlan
      freshreview.work.get.originalBase shouldBe episode.completed.work.get.originalBase
      freshreview.work.get.completed shouldBe episode.completed.work.get.completed
      freshreview.repairProgress shouldBe repair.repairProgress
      executions(fixture.scenario).size shouldBe 3
      completed.history.take(original.history.size) shouldBe original.history
      completed.history.take(recovered.history.size) shouldBe recovered.history
      completed.history.count(_.suspension.exists(_.completion.value == "goal-phase-ordered-human-completion-v1")) shouldBe 1
      val historyreceipts = _stored_json(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(
        raw, _catalog, GoalPhaseNativeSourceHistory.codec).toOption)
      historyreceipts.count(_.reference == originalintent.reference) shouldBe 1
      historyreceipts.count(_.reference == WorkflowMechanicalTransition.Reference(target.sessionId +
        ":ordered-candidate:" + target.sourceRevision + ":source-history-result-" + target.intentIndex, "1")) shouldBe 0
      _receipt(fixture, WorkflowMechanicalTransition.Reference(target.intentIdentity, target.intentRevision),
        GoalPhaseNativeSourceHistory.codec) shouldBe originalintent
      target.completedPrefix.zip(prefixreceipts).foreach { (entry, saved) =>
        _receipt(fixture, WorkflowMechanicalTransition.Reference(entry.intentIdentity, entry.intentRevision), GoalPhaseNativeSourceHistory.codec) shouldBe saved._1
        _receipt(fixture, WorkflowMechanicalTransition.Reference(entry.resultIdentity, entry.resultRevision), GoalPhaseNativeSourceHistory.codec) shouldBe saved._2
      }
      When("ordinary Advance reopens the new independent Review and another recovery description is requested")
      val repeated = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      _describe(fixture).exitCode should not be 0
      Then("the exact new Review remains pending with one consumed recovery Human and no replay")
      repeated shouldBe freshreview
      _current(fixture) shouldBe completed
      executions(fixture.scenario).size shouldBe 3
    }

    "reuse four successful normalizations after ordinary Human completion against an independently integrated descendant" in {
      Given("P116-SOURCE-HISTORY-RECOVERY-002: a completed first Step and four actual updated results before the fifth unknown intent")
      val episode = _document_episode("source-history-later-descendant", later = true)
      val fixture = episode.fixture
      val repository = Path.of(fixture.scenario.host.target.root)
      val original = _current(fixture)
      val prefix = _prefix_paths.map(path => (path, fileBytes(repository.resolve(path)), Files.getLastModifiedTime(repository.resolve(path))))
      When("the fixture independently integrates only the exact normalized prefix bytes before the initial recovery Human question")
      _fixture_git(fixture, Vector("--literal-pathspecs", "add", "--") ++ _prefix_paths)
      _fixture_git(fixture, Vector("commit", "--no-verify", "--no-gpg-sign", "-m", "Independently integrate normalized fixture prefix"))
      val parent = _fixture_git(fixture, Vector("rev-parse", "HEAD"))
      val integratedindex = indexBytes(fixture.scenario)
      val issued = _advance(fixture)
      val question = issued.business[V.GoalPhaseAdvanceResult]
      val target = take(R.targetC(question.human.get.sourceHistoryRecovery.get))
      val receipts = target.completedPrefix.map(entry => (
        _receipt(fixture, WorkflowMechanicalTransition.Reference(entry.intentIdentity, entry.intentRevision), GoalPhaseNativeSourceHistory.codec),
        _receipt(fixture, WorkflowMechanicalTransition.Reference(entry.resultIdentity, entry.resultRevision), GoalPhaseNativeSourceHistory.codec)))
      Then("the exact later-intent target binds the real prefix without changing accepted work or its unknown successor")
      target.intentIndex shouldBe 4
      target.completedPrefix.map(_.index) shouldBe Vector(0, 1, 2, 3)
      target.completedPrefix.map(_.result.outcome) shouldBe Vector.fill(4)(Some("updated"))
      target.completedPrefix.map(_.intent.path) shouldBe _prefix_paths
      target.intent.path shouldBe sourcePath
      parent should not be target.controllerTip.head
      question.repairProgress shouldBe episode.completed.repairProgress
      indexBytes(fixture.scenario) shouldBe integratedindex
      _advance(fixture).business[V.GoalPhaseAdvanceResult] shouldBe question

      Given("a genuine test-owned ordinary Human answer for the question issued after independent prefix integration")
      val answer = _human_answer(fixture, issued.invocation.stdout, "AuthorizeNewAttempt", "later-descendant-human")
      When("ordinary Submit accepts that answer without executing File Git or SBT")
      _human_submit(fixture, question, answer).business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
      val directed = _current(fixture)
      Then("completion retains the exact original history and the single prior Step check")
      directed.history.take(original.history.size) shouldBe original.history
      indexBytes(fixture.scenario) shouldBe integratedindex
      executions(fixture.scenario).size shouldBe 1
      prefix.foreach { (path, bytes, modified) =>
        fileBytes(repository.resolve(path)) shouldBe bytes
        Files.getLastModifiedTime(repository.resolve(path)) shouldBe modified
      }

      When("the fixture corrects only its own malformed fifth source and calls fresh normal Advance without another commit")
      Files.writeString(fixture.source, _later_corrected_text)
      val observed = _advance(fixture)
      val review = observed.business[V.GoalPhaseAdvanceResult]
      val completed = _current(fixture)
      val freshhistory = completed.history.drop(directed.history.size)
      Then("actual descendant ancestry pins the fresh candidate while the old base accepted tip inspection and unknown intent remain exact")
      review.work.get.kind shouldBe "ReviewStep"
      review.work.get.stepIdentity shouldBe Some("step-2")
      review.work.get.candidate.get.head shouldBe parent
      parent should not be target.controllerTip.head
      review.work.get.originalBase shouldBe episode.completed.work.get.originalBase
      review.work.get.completed shouldBe episode.completed.work.get.completed
      review.work.get.selectedPlan shouldBe episode.completed.work.get.selectedPlan
      review.repairProgress shouldBe episode.completed.repairProgress
      review.work.get.review.get.changedPaths shouldBe Vector(sourcePath)
      _prefix_paths.foreach(path => review.work.get.review.get.diff should not include path)
      review.work.get.review.get.checks.head.sbt.get.outcome shouldBe "succeeded"
      executions(fixture.scenario).size shouldBe 2
      completed.history.take(directed.history.size) shouldBe directed.history
      freshhistory.count(_.causation.value.startsWith("ordered-candidate:source-history-intent-")) shouldBe 1
      freshhistory.count(_.causation.value.startsWith("ordered-candidate:source-history-result-")) shouldBe 1
      completed.history.count(_.causation.value == "ordered-candidate:source-history-intent-4") shouldBe 1
      completed.history.exists(_.causation.value == "ordered-candidate:source-history-result-4") shouldBe false
      target.completedPrefix.zip(receipts).foreach { (entry, saved) =>
        _receipt(fixture, WorkflowMechanicalTransition.Reference(entry.intentIdentity, entry.intentRevision), GoalPhaseNativeSourceHistory.codec) shouldBe saved._1
        _receipt(fixture, WorkflowMechanicalTransition.Reference(entry.resultIdentity, entry.resultRevision), GoalPhaseNativeSourceHistory.codec) shouldBe saved._2
      }
      prefix.foreach { (path, bytes, modified) =>
        fileBytes(repository.resolve(path)) shouldBe bytes
        Files.getLastModifiedTime(repository.resolve(path)) shouldBe modified
      }
      When("ordinary Advance reopens the pending independent review")
      val repeated = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the exact saved descendant candidate and native check remain without replay")
      repeated shouldBe review
      _current(fixture) shouldBe completed
      executions(fixture.scenario).size shouldBe 2

      Given("P116-CONT-01-RETAINED: the completed recovery Human and pending review retain their original ordered candidate policy")
      val config = parse(Files.readString(fixture.scenario.root.resolve(".textus/config.json"))).toOption.get
      val key = "sm-workflow.runtime.definition"
      val definition = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(parse(config.hcursor.get[String](key).toOption.get).toOption.get))
      val candidatepaths = definition.goal.candidatePaths
      val extendedpaths = candidatepaths :+ "src/main/scala/RetainedRecoveryUnused.scala"
      val payloads = _stored_json(fixture).sorted
      val source = fileBytes(fixture.source)
      val modified = Files.getLastModifiedTime(fixture.source)
      val index = indexBytes(fixture.scenario)
      target.candidatePaths shouldBe candidatepaths
      When("the test appends one safe unrelated declaration path and calls ordinary Advance")
      configure(fixture.scenario, enabledSettings.updated(key, Json.fromString(ThreeWorkflowRuntimeParameters.encode(
        definition.copy(goal = definition.goal.copy(candidatePaths = extendedpaths))).noSpaces)))
      val extended = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the identical pending review reopens with the complete record saved Human target prefix sources and checks unchanged")
      extended shouldBe review
      _current(fixture) shouldBe completed
      _stored_json(fixture).sorted shouldBe payloads
      target.completedPrefix.zip(receipts).foreach { (entry, saved) =>
        _receipt(fixture, WorkflowMechanicalTransition.Reference(entry.intentIdentity, entry.intentRevision), GoalPhaseNativeSourceHistory.codec) shouldBe saved._1
        _receipt(fixture, WorkflowMechanicalTransition.Reference(entry.resultIdentity, entry.resultRevision), GoalPhaseNativeSourceHistory.codec) shouldBe saved._2
      }
      prefix.foreach { (path, bytes, timestamp) =>
        fileBytes(repository.resolve(path)) shouldBe bytes
        Files.getLastModifiedTime(repository.resolve(path)) shouldBe timestamp
      }
      fileBytes(fixture.source) shouldBe source
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario).size shouldBe 2

      Given("the same retained Human target with two original candidate entries reordered instead of an append-only extension")
      val reorderedpaths = extendedpaths.updated(0, candidatepaths(1)).updated(1, candidatepaths(0))
      When("ordinary Advance reads the reordered declaration")
      configure(fixture.scenario, enabledSettings.updated(key, Json.fromString(ThreeWorkflowRuntimeParameters.encode(
        definition.copy(goal = definition.goal.copy(candidatePaths = reorderedpaths))).noSpaces)))
      val refused = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the historical mismatch waits without new native history normalization staging or checks")
      refused.kind shouldBe "Wait"
      _current(fixture) shouldBe completed
      _stored_json(fixture).sorted shouldBe payloads
      prefix.foreach { (path, bytes, timestamp) =>
        fileBytes(repository.resolve(path)) shouldBe bytes
        Files.getLastModifiedTime(repository.resolve(path)) shouldBe timestamp
      }
      fileBytes(fixture.source) shouldBe source
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario).size shouldBe 2
    }

    "refuse an altered completed prefix before persisting any fresh inspection or normalization" in {
      Given("P116-SOURCE-HISTORY-RECOVERY-002: four successful original results and an authorized later unknown source")
      val episode = _document_episode("source-history-later-altered", later = true)
      val fixture = episode.fixture
      val issued = _advance(fixture)
      val question = issued.business[V.GoalPhaseAdvanceResult]
      val answer = _human_answer(fixture, issued.invocation.stdout, "AuthorizeNewAttempt", "later-altered-human")
      _human_submit(fixture, question, answer).business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
      val before = _current(fixture)
      val prefix = Path.of(fixture.scenario.host.target.root).resolve(_prefix_paths.head)
      Files.writeString(prefix, _prefix_text(1, 42))
      Files.writeString(fixture.source, _later_corrected_text)
      val index = indexBytes(fixture.scenario)
      When("ordinary Advance finds that a saved successful source no longer satisfies its original normalization")
      val refused = _advance(fixture)
      Then("the call fails closed before fresh inspection persistence staging source writes or another check")
      refused.exitCode should not be 0
      _current(fixture) shouldBe before
      Files.readString(prefix) shouldBe _prefix_text(1, 42)
      Files.readString(fixture.source) shouldBe _later_corrected_text
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario).size shouldBe 1
    }

    "retain the four-result prefix when the new remaining source is unknown again without another Human allowance" in {
      Given("P116-SOURCE-HISTORY-RECOVERY-002: a later-intent Human authorizes one attempt while the fifth source remains malformed")
      val episode = _document_episode("source-history-later-second-unknown", later = true)
      val fixture = episode.fixture
      val issued = _advance(fixture)
      val question = issued.business[V.GoalPhaseAdvanceResult]
      val answer = _human_answer(fixture, issued.invocation.stdout, "AuthorizeNewAttempt", "later-second-unknown-human")
      _human_submit(fixture, question, answer).business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
      val before = _current(fixture)
      When("the new candidate skips the completed prefix and reaches the still malformed remaining source")
      _advance(fixture).exitCode should not be 0
      val failed = _current(fixture)
      val fresh = failed.history.drop(before.history.size)
      Then("only the remaining source has a new unknown intent and repeated Advance cannot issue another recovery Human")
      fresh.map(_.causation.value) shouldBe Vector("ordered-candidate:inspection", "ordered-candidate:source-history-intent-0")
      _advance(fixture).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      _describe(fixture).exitCode should not be 0
      _current(fixture) shouldBe failed
      failed.history.take(before.history.size) shouldBe before.history
      failed.history.count(_.suspension.exists(_.completion.value == "goal-phase-ordered-human-completion-v1")) shouldBe 1
      executions(fixture.scenario).size shouldBe 1
    }

    "complete the ordinary recovery Human through the deployed host program and installed CLI without manual import selection" in {
      Given("HOST-CONTINUATION-003: explicitly selected installed CLI and canonical shared host program, otherwise qualification is cancelled")
      val executable = sys.props.get("smworkflow.spec.installedCommand").map(Path.of(_))
        .getOrElse(cancel("Set smworkflow.spec.installedCommand to an absolute existing installed CLI for this opt-in qualification"))
      val hostscript = sys.props.get("smworkflow.spec.hostCommandScript").map(Path.of(_))
        .getOrElse(cancel("Set smworkflow.spec.hostCommandScript to the absolute existing canonical shared native-goal-command.py"))
      executable.isAbsolute shouldBe true
      Files.isRegularFile(executable) shouldBe true
      Files.isExecutable(executable) shouldBe true
      hostscript.isAbsolute shouldBe true
      Files.isRegularFile(hostscript) shouldBe true
      hostscript.toRealPath() shouldBe hostscript
      hostscript.endsWith(Path.of("common/codex/scripts/native-goal-command.py")) shouldBe true
      val episode = _document_episode("source-history-installed-host-complete", later = true)
      val fixture = episode.fixture
      val repository = Path.of(fixture.scenario.host.target.root)
      val prefix = _prefix_paths.map(path => (path, fileBytes(repository.resolve(path)), Files.getLastModifiedTime(repository.resolve(path))))
      val original = _current(fixture)
      val originalfacts = _completed_facts(fixture)
      When("the fixture independently integrates the exact normalized prefix before ordinary installed Advance issues the recovery Human")
      _fixture_git(fixture, Vector("--literal-pathspecs", "add", "--") ++ _prefix_paths)
      _fixture_git(fixture, Vector("commit", "--no-verify", "--no-gpg-sign", "-m", "Independently integrate installed fixture prefix before Human"))
      val parent = _fixture_git(fixture, Vector("rev-parse", "HEAD"))
      val originalindex = indexBytes(fixture.scenario)
      val issued = _advance(fixture)
      val question = issued.business[V.GoalPhaseAdvanceResult]
      val pending = _current(fixture)
      Then("the original accepted prefix, Plan, base, counts and index remain while the independent question is pending")
      question.kind shouldBe "Human"
      question.submitOperation shouldBe Some("submitGoalPhaseOrderedDecision")
      question.resultType shouldBe Some("GoalPhaseOrderedDecisionAnswer")
      val target = take(R.targetC(question.human.get.sourceHistoryRecovery.get))
      target.intentIndex shouldBe 4
      parent should not be target.controllerTip.head
      target.completedPrefix.map(_.result.outcome) shouldBe Vector.fill(4)(Some("updated"))
      pending.history.dropRight(1) shouldBe original.history
      question.human.get.work shouldBe fixture.request.work.get
      question.repairProgress shouldBe fixture.request.repairProgress
      indexBytes(fixture.scenario) shouldBe originalindex
      executions(fixture.scenario).size shouldBe 1

      Given("test-owned channel records explicitly supply answer, reference, worker, participant, issuer and time; these fixtures are not live Human consent")
      val task = Files.createDirectories(fixture.scenario.root.resolve("target/sm-workflow/tasks/installed-human-complete"))
      Files.writeString(task.resolve("workflow-session.json"), Json.obj(
        "family" -> Json.fromString("GoalPhase"), "sessionId" -> Json.fromString(question.sessionId)).noSpaces)
      val output = Files.createDirectory(task.resolve("complete"))
      val answer = V.GoalPhaseOrderedDecisionAnswer("AuthorizeNewAttempt",
        "Test-owned independent Human direction accepting the unknown old effect")
      val reference = V.WorkflowArtifactReference("installed-host-complete-human", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("test-independent-human"), None, None)
      val submission = task.resolve("submission.json")
      Files.writeString(submission, Json.obj("answer" -> parse(_json(answer)).toOption.get,
        "resultReference" -> parse(_json(reference)).toOption.get,
        "executionEvidence" -> parse(_json(execution)).toOption.get).noSpaces)
      val provenance = task.resolve("human-provenance.json")
      Files.writeString(provenance, Json.obj("participant" -> Json.fromString("participant"),
        "issuer" -> Json.fromString("normal-development-host"),
        "occurredAt" -> Json.fromString("2026-10-07T00:00:00Z"),
        "collection" -> Json.fromString("entity:smworkflow.cli.host_evidence")).noSpaces)
      val config = fixture.scenario.root.resolve(".textus/config.json")
      val configbytes = Files.readAllBytes(config).toVector
      val routebytes = Files.readAllBytes(task.resolve("workflow-session.json")).toVector
      Files.writeString(fixture.source, _later_corrected_text)
      val stdout = task.resolve("host-response.jsonl")
      val stderr = task.resolve("host-stderr.log")
      val argv = Vector("uv", "run", "--no-cache", "--managed-python", "--no-python-downloads", "--script",
        hostscript.toString, "--executable", executable.toString, "--runtime-root", fixture.scenario.root.toString,
        "--task", task.toString, "--action", "complete", "--request", issued.invocation.stdout.toString,
        "--submission", submission.toString, "--human-provenance", provenance.toString, "--output", output.toString)
      When("one finite deployed program invocation owns normal preparation, fixed import selection, accepted Submit and ordinary Advance without an intervening commit")
      val process = new ProcessBuilder(argv.asJava).directory(fixture.scenario.root.toFile)
        .redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      try {
        process.getOutputStream.close()
        if (!process.waitFor(240, TimeUnit.SECONDS)) {
          process.destroyForcibly()
          process.waitFor()
          fail("Owned installed host completion exceeded its finite deadline: " + Files.readString(stderr))
        }
        Then("actual preparation and accepted native completion reach independent ReviewStep with one real typed SBT check")
        withClue(Files.readString(stderr)) { process.exitValue() shouldBe 0 }
        val prepared = parse(Files.readString(output.resolve("preparation/response.json"))).toOption.get
        prepared.hcursor.downField("datastore").focus.flatMap(_.asArray).isDefined shouldBe true
        val submitted = F.Result(0, F.Invocation(argv, process.pid(), output.resolve("response.json"), output.resolve("stderr.log")))
          .business[V.GoalPhaseOrderedDecisionSubmissionResult]
        submitted.accepted shouldBe true
        submitted.sessionId shouldBe question.sessionId
        val advanced = F.Result(0, F.Invocation(argv, process.pid(), output.resolve("advance/response.json"), output.resolve("advance/stderr.log")))
        val review = advanced.business[V.GoalPhaseAdvanceResult]
        review.work.get.kind shouldBe "ReviewStep"
        review.work.get.stepIdentity shouldBe Some("step-2")
        review.work.get.completed shouldBe episode.completed.work.get.completed
        review.work.get.candidate.get.head shouldBe parent
        review.work.get.review.get.changedPaths shouldBe Vector(sourcePath)
        _prefix_paths.foreach(path => review.work.get.review.get.diff should not include path)
        review.work.get.review.get.checks.head.sbt.get.outcome shouldBe "succeeded"
        review.work.get.selectedPlan shouldBe fixture.request.work.get.selectedPlan
        review.work.get.originalBase shouldBe fixture.request.work.get.originalBase
        review.repairProgress shouldBe question.repairProgress
        val retained = _current(fixture)
        retained.history.take(original.history.size) shouldBe original.history
        retained.history.take(pending.history.size) shouldBe pending.history
        retained.history.count(_.causation.value == "ordered-candidate:source-history-intent-4") shouldBe 1
        retained.history.exists(_.causation.value == "ordered-candidate:source-history-result-4") shouldBe false
        val freshhistory = retained.history.drop(pending.history.size)
        freshhistory.count(_.causation.value.startsWith("ordered-candidate:source-history-intent-")) shouldBe 1
        freshhistory.count(_.causation.value.startsWith("ordered-candidate:source-history-result-")) shouldBe 1
        retained.history.count(_.causation.value == reference.identity) shouldBe 1
        retained.history.count(_.causation.value.startsWith("ordered-source-history-recovery:")) shouldBe 0
        _completed_facts(fixture).take(originalfacts.size) shouldBe originalfacts
        executions(fixture.scenario).size shouldBe 2
        prefix.foreach { (path, bytes, modified) =>
          fileBytes(repository.resolve(path)) shouldBe bytes
          Files.getLastModifiedTime(repository.resolve(path)) shouldBe modified
        }
        Files.readAllBytes(config).toVector shouldBe configbytes
        Files.readAllBytes(task.resolve("workflow-session.json")).toVector shouldBe routebytes
        val observations = Files.readString(stdout).linesIterator.map(line => parse(line).toOption.get)
          .filterNot(_.hcursor.get[String]("recordType").toOption.contains("native-command-progress")).toVector
        observations.size shouldBe 3
        observations.head shouldBe prepared
        observations(1).hcursor.get[Boolean]("accepted").toOption shouldBe Some(true)
        observations(2).hcursor.get[String]("kind").toOption shouldBe Some("Request")
      } finally {
        if (process.isAlive) { process.destroyForcibly(); process.waitFor() }
        process.getInputStream.close()
        process.getErrorStream.close()
      }
    }

    "issue one ordinary recovery Human and reach independent ReviewStep only after its independently admitted typed answer" in {
      val fixture = _failed("source-history-ordinary-human")
      Given("SOURCE-HISTORY-CONTINUATION-002: accepted implementation and the exact first unknown normalization intent")
      val original = _current(fixture)
      val index = indexBytes(fixture.scenario)
      val source = fileBytes(fixture.source)
      When("ordinary Advance reaches that retained first-intent stop")
      val issued = _advance(fixture)
      val question = issued.business[V.GoalPhaseAdvanceResult]
      val pending = _current(fixture)
      val target = take(R.targetC(question.human.get.sourceHistoryRecovery.get))
      Then("the normal durable Decision binds the actual original intent and accepted work without any effect")
      question.kind shouldBe "Human"
      question.submitOperation shouldBe Some("submitGoalPhaseOrderedDecision")
      question.resultType shouldBe Some("GoalPhaseOrderedDecisionAnswer")
      question.human.get.choices shouldBe R.choices
      question.human.get.rationale should include("UNKNOWN")
      question.human.get.work shouldBe fixture.request.work.get
      question.human.get.requestingResult.outcome shouldBe "completed"
      question.repairProgress shouldBe fixture.request.repairProgress
      target.frontierRevision shouldBe original.revision.value.toString
      target.sourceRevision.toLong + 2 shouldBe original.revision.value
      target.base.head shouldBe fixture.request.work.get.originalBase.head
      pending.history.dropRight(1) shouldBe original.history
      pending.suspension.get.continuationIdentity.value shouldBe fixture.request.sessionId + ":ordered-human:" + original.revision.value
      fileBytes(fixture.source) shouldBe source
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe empty

      When("another ordinary Advance reopens the unanswered Decision")
      val repeated = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the exact same saved question and history are returned without another intent or Decision")
      repeated shouldBe question
      _current(fixture) shouldBe pending

      Given("missing independent evidence and a separately prepared answer bound to a crossed captured target")
      val missing = _human_answer(fixture, issued.invocation.stdout, "AuthorizeNewAttempt", "ordinary-missing", prepare = false)
      val human = question.human.get
      val context = human.humanDecisionContext.get
      val raw = parse(take(context.question.toJsonStringC)).toOption.get
      val crossedwire = raw.mapObject(fields => fields.add("source", fields("source").get.mapObject { source =>
        val retained = org.goldenport.cncf.context.RuntimeContext.PropertyNameContext.default
          .aliases("sourceHistoryRecovery").foldLeft(source)((selected, alias) => selected.remove(alias))
        retained.add("sourceHistoryRecovery", target.copy(planRevision = "crossed-plan").asJson)
      }))
      val crossedcontext = context.copy(question = GoalPhaseNativeMetadata.record(crossedwire))
      val crossedquestion = question.copy(human = Some(human.copy(humanDecisionContext = Some(crossedcontext))))
      val crossedfile = fixture.scenario.root.resolve("test-owned-crossed-recovery-question.json")
      Files.writeString(crossedfile, _json(crossedquestion))
      When("ordinary Submit receives missing and crossed evidence before the actual independently prepared answer")
      _human_submit(fixture, question, missing).exitCode should not be 0
      val crossed = _human_answer(fixture, crossedfile, "AuthorizeNewAttempt", "ordinary-crossed")
      _human_submit(fixture, question, crossed).exitCode should not be 0
      val answer = _human_answer(fixture, issued.invocation.stdout, "AuthorizeNewAttempt", "ordinary-authorized")
      _human_submit(fixture, question, answer.copy(reference = answer.reference.copy(revision = "2"))).exitCode should not be 0
      Then("missing crossed and stale references cannot consume the pending original question")
      _current(fixture) shouldBe pending
      fileBytes(fixture.source) shouldBe source
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe empty

      Given("the exact independent ordinary GoalHuman evidence and fixture-owned correction of the malformed source")
      Files.writeString(fixture.source, changedText)
      When("ordinary typed Human completion accepts AuthorizeNewAttempt")
      val accepted = _human_submit(fixture, question, answer).business[V.GoalPhaseOrderedDecisionSubmissionResult]
      val completed = _current(fixture)
      Then("managed completion appends only its actual successor with the controller and counters retained")
      accepted.accepted shouldBe true
      completed.history.dropRight(1) shouldBe pending.history
      completed.causation.value shouldBe answer.reference.identity
      completed.currentProgression shouldBe original.currentProgression
      completed.suspension shouldBe None
      Files.readString(fixture.source) shouldBe changedText
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe empty

      When("the host explicitly calls the next ordinary Advance")
      val review = assertReview(fixture, _advance(fixture))
      val reviewed = _current(fixture)
      Then("existing native candidate and real SBT checks reach the next independent ReviewStep with the original Plan base and counts")
      review.repairProgress shouldBe question.repairProgress
      review.work.get.selectedPlan shouldBe fixture.request.work.get.selectedPlan
      review.work.get.originalBase shouldBe fixture.request.work.get.originalBase
      reviewed.history.take(completed.history.size) shouldBe completed.history
      reviewed.history.count(_.causation.value == "ordered-candidate:source-history-intent-0") shouldBe 2
      reviewed.history.count(_.causation.value == "ordered-candidate:source-history-result-0") shouldBe 1
      reviewed.history.count(_.causation.value.startsWith("ordered-source-history-recovery:")) shouldBe 0
      executions(fixture.scenario).size shouldBe 1
      When("ordinary Advance reopens that independent review")
      val reopened = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("saved review and native effects are reused without resubmitting the accepted implementation")
      reopened shouldBe review
      _current(fixture) shouldBe reviewed
      executions(fixture.scenario).size shouldBe 1
    }

    "retain an ordinary Defer completion without another recovery question or source attempt" in {
      val fixture = _failed("source-history-ordinary-defer")
      Given("SOURCE-HISTORY-CONTINUATION-002: one issued recovery Decision and an independently prepared Defer answer")
      val issued = _advance(fixture)
      val question = issued.business[V.GoalPhaseAdvanceResult]
      val pending = _current(fixture)
      val source = fileBytes(fixture.source)
      val index = indexBytes(fixture.scenario)
      val answer = _human_answer(fixture, issued.invocation.stdout, "Defer", "ordinary-source-defer")
      When("normal typed Human Submit completes Defer and ordinary Advance is called twice")
      _human_submit(fixture, question, answer).business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
      val completed = _current(fixture)
      val waiting = Vector.fill(2)(_advance(fixture).business[V.GoalPhaseAdvanceResult])
      Then("the actual deferred successor remains stopped and grants neither a fresh Human nor a retry")
      completed.history.dropRight(1) shouldBe pending.history
      waiting.foreach { value =>
        value.kind shouldBe "Wait"
        value.human shouldBe None
        value.repairProgress shouldBe question.repairProgress
      }
      _current(fixture) shouldBe completed
      fileBytes(fixture.source) shouldBe source
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe empty
    }

    "retain unknown history and accepted work until independent Human direction authorizes a separate fresh candidate attempt" in {
      val fixture = _failed("source-history-public-recovery")
      Given("R1,R2: the first unresolved source-history intent, with no source result or candidate")
      val before = _current(fixture)
      val index = indexBytes(fixture.scenario)
      When("separate normal description calls inspect the unresolved source frontier")
      val described = _describe(fixture)
      val description = described.business[V.GoalPhaseSourceHistoryRecoveryDescriptionResult]
      val target = take(R.targetC(description.target))
      val again = _describe(fixture).business[V.GoalPhaseSourceHistoryRecoveryDescriptionResult]
      Then("description is read-only and binds actual retained source, first intent, original Plan/base and participant")
      again shouldBe description
      _current(fixture) shouldBe before
      target.frontierRevision shouldBe before.revision.value.toString
      target.intentRecordRevision shouldBe target.frontierRevision
      target.sourceRevision.toLong + 2 shouldBe target.frontierRevision.toLong
      target.planIdentity shouldBe fixture.request.work.get.selectedPlan.identity
      target.planRevision shouldBe fixture.request.work.get.selectedPlan.revision
      target.base.head shouldBe fixture.request.work.get.originalBase.head
      target.base.tree shouldBe fixture.request.work.get.originalBase.tree
      target.inspection.selectedPaths shouldBe Vector(sourcePath)
      description.message should include("UNKNOWN")
      executions(fixture.scenario) shouldBe empty

      Given("R2: fixture-owned correction of the duplicate header without a new Submit or authorization")
      When("the fixture corrects only source bytes and repeats the read-only diagnostic description")
      Files.writeString(fixture.source, changedText)
      _describe(fixture).business[V.GoalPhaseSourceHistoryRecoveryDescriptionResult] shouldBe description
      Then("correction and diagnostic reads alone do not authorize normalization or change the retained frontier")
      _current(fixture) shouldBe before
      Files.readString(fixture.source) shouldBe changedText
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe empty

      Given("R3: missing evidence, crossed current-target inputs and independently prepared Defer")
      val missing = V.GoalPhaseSourceHistoryRecoveryInput(fixture.request.sessionId, description.target,
        "AuthorizeNewAttempt", "No independent consent", V.WorkflowArtifactReference("missing-source-consent", "1"))
      When("normal recovery receives an unbound request and then independently prepared Defer")
      _recover(fixture, missing).exitCode should not be 0
      _recover(fixture, missing.copy(sessionId = "other-session")).exitCode should not be 0
      _recover(fixture, missing.copy(target = R.targetRecord(target.copy(planRevision = "foreign-plan")))).exitCode should not be 0
      val deferredinput = _direction(fixture, description, described.invocation.stdout, "Defer", "source-defer")
      val deferred = _recover(fixture, deferredinput).business[V.GoalPhaseSourceHistoryRecoveryResult]
      Then("Defer and refused inputs retain the exact unknown prefix with no effects or retry")
      deferred.outcome shouldBe "deferred"
      deferred.accepted shouldBe true
      _current(fixture) shouldBe before
      Files.readString(fixture.source) shouldBe changedText
      executions(fixture.scenario) shouldBe empty

      Given("R3,R4: a separately prepared and normally imported independent Human authorization for the complete current target")
      val authorization = _direction(fixture, description, described.invocation.stdout, "AuthorizeNewAttempt", "source-authorize")
      When("public recovery appends direction before any later ordinary Advance")
      _recover(fixture, authorization.copy(reference = V.WorkflowArtifactReference("crossed-source-answer", "1")))
        .exitCode should not be 0
      _recover(fixture, authorization.copy(target = R.targetRecord(target.copy(planRevision = "stale-plan"))))
        .exitCode should not be 0
      val accepted = _recover(fixture, authorization).business[V.GoalPhaseSourceHistoryRecoveryResult]
      val directed = _current(fixture)
      Then("only one direction record is appended and stale or duplicated authorization refuses without File/Git/SBT")
      accepted.outcome shouldBe "new-attempt-authorized"
      directed.history.dropRight(1) shouldBe before.history
      directed.causation.value shouldBe "ordered-source-history-recovery:" + target.sourceRevision
      directed.currentProgression shouldBe before.currentProgression
      _recover(fixture, authorization).exitCode should not be 0
      _describe(fixture).exitCode should not be 0
      _current(fixture) shouldBe directed
      Files.readString(fixture.source) shouldBe changedText
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe empty

      When("a later separate normal Advance performs the authorized fresh inspection")
      val observed = _advance(fixture)
      val review = assertReview(fixture, observed)
      val completed = _current(fixture)
      val normalized = fileBytes(fixture.source)
      val staged = indexBytes(fixture.scenario)
      val checks = executions(fixture.scenario)
      Then("the same accepted implementation reaches the next independent review, retaining history Plan base and counters")
      review.repairProgress shouldBe fixture.request.repairProgress
      review.work.get.selectedPlan shouldBe fixture.request.work.get.selectedPlan
      review.work.get.originalBase shouldBe fixture.request.work.get.originalBase
      completed.history.take(directed.history.size) shouldBe directed.history
      completed.history.count(_.causation.value == "ordered-candidate:source-history-intent-0") shouldBe 2
      completed.history.count(_.causation.value == "ordered-candidate:source-history-result-0") shouldBe 1
      completed.history.count(_.causation.value == "ordered-candidate:stage-intent-0") shouldBe 1
      Files.readString(fixture.source).linesIterator.count(_.contains("@version")) shouldBe 1
      checks.size shouldBe 1

      Given("R4: a completed new candidate and its pending independent review in a reopened normal process")
      When("ordinary Advance repeats and the old authorization is submitted again")
      val reopened = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      _recover(fixture, authorization).exitCode should not be 0
      Then("saved work remains identical without another normalization staging or check")
      reopened shouldBe review
      _current(fixture) shouldBe completed
      fileBytes(fixture.source) shouldBe normalized
      indexBytes(fixture.scenario) shouldBe staged
      executions(fixture.scenario) shouldBe checks

      Given("P116-CONT-01-RETAINED: a canonical persisted mechanical direction and its pending review under the original candidate declaration")
      val config = parse(Files.readString(fixture.scenario.root.resolve(".textus/config.json"))).toOption.get
      val key = "sm-workflow.runtime.definition"
      val definition = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(parse(config.hcursor.get[String](key).toOption.get).toOption.get))
      val extendedpaths = definition.goal.candidatePaths :+ "src/main/scala/RetainedRecoveryUnused.scala"
      val payloads = _stored_json(fixture).sorted
      val modified = Files.getLastModifiedTime(fixture.source)
      target.candidatePaths shouldBe definition.goal.candidatePaths
      When("the test appends one safe unrelated declaration path and calls ordinary Advance")
      configure(fixture.scenario, enabledSettings.updated(key, Json.fromString(ThreeWorkflowRuntimeParameters.encode(
        definition.copy(goal = definition.goal.copy(candidatePaths = extendedpaths))).noSpaces)))
      val extended = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      Then("the exact review record and all retained payloads reopen without source index normalization staging or check changes")
      extended shouldBe review
      _current(fixture) shouldBe completed
      _stored_json(fixture).sorted shouldBe payloads
      fileBytes(fixture.source) shouldBe normalized
      Files.getLastModifiedTime(fixture.source) shouldBe modified
      indexBytes(fixture.scenario) shouldBe staged
      executions(fixture.scenario) shouldBe checks
      checks.size shouldBe 1
    }

    "stop again when a separately authorized new attempt fails without granting implicit retry" in {
      val fixture = _failed("source-history-second-failure")
      Given("R2,R4: the duplicate header remains and a Human explicitly accepts possible repetition")
      val issued = _advance(fixture)
      val question = issued.business[V.GoalPhaseAdvanceResult]
      val authorization = _human_answer(fixture, issued.invocation.stdout, "AuthorizeNewAttempt", "source-repeat-failure")
      val original = _current(fixture)
      When("ordinary Human completion appends direction and a separate Advance reaches another actual interpretation failure")
      _human_submit(fixture, question, authorization).business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
      _advance(fixture).exitCode should not be 0
      val failed = _current(fixture)
      Then("another Advance waits and the first-incident recovery API refuses the newly failed attempt")
      failed.history.take(original.history.size) shouldBe original.history
      _advance(fixture).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      _describe(fixture).exitCode should not be 0
      _current(fixture) shouldBe failed
      failed.history.count(_.suspension.exists(_.completion.value == "goal-phase-ordered-human-completion-v1")) shouldBe 1
      executions(fixture.scenario) shouldBe empty
    }

    "describe and authorize from retained history after a completed Step when Git observation is unavailable" in {
      Given("CB1: a normal completed first Step and a second Step stopped at its first unknown history intent")
      val episode = _document_episode("source-history-storage-only")
      val fixture = episode.fixture
      val before = _current(fixture)
      val facts = _completed_facts(fixture)
      facts should not be empty
      val checks = executions(fixture.scenario)
      val source = fileBytes(fixture.source)
      val index = indexBytes(fixture.scenario)
      val completed = episode.completed.work.get.completed
      val config = fixture.scenario.root.resolve(".textus/config.json")
      val current = parse(Files.readString(config)).toOption.get
      val key = "textus.subsystem.process-execution.plan"
      val plan = parse(current.hcursor.get[String](key).toOption.get).toOption.get
      val grants = plan.hcursor.downField("grants").focus.get.asArray.get.map { grant =>
        val capabilities = grant.hcursor.downField("capabilities").focus.get.asArray.get
          .filterNot(_.asString.exists(_.startsWith("sm-workflow-git-")))
        grant.mapObject(_.add("capabilities", Json.fromValues(capabilities)))
      }
      Files.writeString(config, current.mapObject(_.add(key,
        Json.fromString(plan.mapObject(_.add("grants", Json.fromValues(grants))).noSpaces))).noSpaces)
      When("ordinary Advance attempts its normal actual-commit verification with every Git grant removed")
      _advance(fixture).exitCode should not be 0
      Then("normal continuation still requires actual Git verification and leaves the exact retained state unchanged")
      _current(fixture) shouldBe before
      When("the public storage-only description reconstructs the prior completed native Step")
      val described = _describe(fixture)
      val description = described.business[V.GoalPhaseSourceHistoryRecoveryDescriptionResult]
      val target = take(R.targetC(description.target))
      Then("the exact second-Step target retains its original base and the actual completed first-Step tip")
      target.step shouldBe "step-2"
      target.base.head shouldBe episode.completed.work.get.originalBase.head
      target.controllerTip.head shouldBe completed.head.commit.identity
      target.controllerTip.tree shouldBe completed.head.commit.tree
      _current(fixture) shouldBe before
      Given("CB1: separately prepared independent Human direction for that target under the same unavailable Git grants")
      val authorization = _direction(fixture, description, described.invocation.stdout,
        "AuthorizeNewAttempt", "source-storage-only-authorize")
      When("public recovery appends its single mechanical direction using the retained-only traversal")
      _recover(fixture, authorization).business[V.GoalPhaseSourceHistoryRecoveryResult].accepted shouldBe true
      val directed = _current(fixture)
      Then("the completed commit and check facts remain exact and no File Git or SBT effect was needed")
      directed.history.dropRight(1) shouldBe before.history
      directed.causation.value shouldBe "ordered-source-history-recovery:" + target.sourceRevision
      _completed_facts(fixture) shouldBe facts
      executions(fixture.scenario) shouldBe checks
      fileBytes(fixture.source) shouldBe source
      indexBytes(fixture.scenario) shouldBe index
    }

    "submit the ordinary pending recovery Human from retained completed-Step history while Git grants are absent" in {
      Given("SOURCE-HISTORY-CONTINUATION-002: a real completed first Step and an issued second-Step recovery Human")
      val episode = _document_episode("source-history-ordinary-storage-only")
      val fixture = episode.fixture
      val issued = _advance(fixture)
      val question = issued.business[V.GoalPhaseAdvanceResult]
      val pending = _current(fixture)
      val facts = _completed_facts(fixture)
      val source = fileBytes(fixture.source)
      val index = indexBytes(fixture.scenario)
      val checks = executions(fixture.scenario)
      val config = fixture.scenario.root.resolve(".textus/config.json")
      val current = parse(Files.readString(config)).toOption.get
      val key = "textus.subsystem.process-execution.plan"
      val plan = parse(current.hcursor.get[String](key).toOption.get).toOption.get
      val grants = plan.hcursor.downField("grants").focus.get.asArray.get.map { grant =>
        val capabilities = grant.hcursor.downField("capabilities").focus.get.asArray.get
          .filterNot(_.asString.exists(_.startsWith("sm-workflow-git-")))
        grant.mapObject(_.add("capabilities", Json.fromValues(capabilities)))
      }
      Files.writeString(config, current.mapObject(_.add(key,
        Json.fromString(plan.mapObject(_.add("grants", Json.fromValues(grants))).noSpaces))).noSpaces)
      When("independent fixture-host preparation and ordinary Human Submit reconstruct the pending question without Git observations")
      val answer = _human_answer(fixture, issued.invocation.stdout, "AuthorizeNewAttempt", "ordinary-storage-only-answer")
      val accepted = _human_submit(fixture, question, answer).business[V.GoalPhaseOrderedDecisionSubmissionResult]
      val completed = _current(fixture)
      Then("managed Human completion retains every actual prior commit and check without any File Git or SBT effect")
      accepted.accepted shouldBe true
      completed.history.dropRight(1) shouldBe pending.history
      completed.causation.value shouldBe answer.reference.identity
      _completed_facts(fixture) shouldBe facts
      fileBytes(fixture.source) shouldBe source
      indexBytes(fixture.scenario) shouldBe index
      executions(fixture.scenario) shouldBe checks
      When("storage-only diagnostic traversal crosses the completed Human answer and normal Advance requires Git again")
      _describe(fixture).exitCode should not be 0
      _advance(fixture).exitCode should not be 0
      Then("the consumed recovery answer survives while ordinary command authority remains required")
      _current(fixture) shouldBe completed
      _completed_facts(fixture) shouldBe facts
      executions(fixture.scenario) shouldBe checks
    }

    "refuse another same-Step direction after a safe future-tail document update and a second normalization failure" in {
      Given("CB2: an actual completed first Step and a second Step with an unresolved first history intent")
      val episode = _document_episode("source-history-document-repeat")
      val fixture = episode.fixture
      val original = _current(fixture)
      val facts = _completed_facts(fixture)
      val checks = executions(fixture.scenario)
      val issued = _advance(fixture)
      val question = issued.business[V.GoalPhaseAdvanceResult]
      val target = take(R.targetC(question.human.get.sourceHistoryRecovery.get))
      val authorization = _human_answer(fixture, issued.invocation.stdout,
        "AuthorizeNewAttempt", "source-document-first-authorize")
      When("the Human authorizes one new attempt and the document owner independently extends only the future tail")
      _human_submit(fixture, question, authorization).business[V.GoalPhaseOrderedDecisionSubmissionResult].accepted shouldBe true
      val directed = _current(fixture)
      val revisedplan = episode.plan.copy(revision = "2", steps = episode.plan.steps :+ episode.plan.steps.head.copy(
        identity = "step-3", purpose = "Future unissued maintenance after the protected second Step"))
      _write_documents(episode.phase, episode.checklist, revisedplan)
      val failedcall = _advance(fixture)
      Then("ordinary Advance selects that safe document revision before a genuine new history interpretation failure")
      failedcall.exitCode should not be 0
      failedcall.invocation.logs should include("duplicate since or latest version")
      val failed = _current(fixture)
      failed.history.take(directed.history.size) shouldBe directed.history
      failed.history.take(original.history.size) shouldBe original.history
      val freshhistory = failed.history.drop(directed.history.size)
      freshhistory.map(_.causation.value) shouldBe Vector("ordered-document-plan-selected",
        "ordered-candidate:inspection", "ordered-candidate:source-history-intent-0")
      val sourcerevision = freshhistory.head.revision.value.toString
      val inspectionreference = WorkflowMechanicalTransition.Reference(fixture.request.sessionId +
        ":ordered-candidate:" + sourcerevision + ":inspection", "1")
      val intentreference = WorkflowMechanicalTransition.Reference(fixture.request.sessionId +
        ":ordered-candidate:" + sourcerevision + ":source-history-intent-0", "1")
      val inspection = _receipt(fixture, inspectionreference, GoalPhaseNativeCandidate.codec)
      val intent = _receipt(fixture, intentreference, GoalPhaseNativeSourceHistory.codec)
      Given("CB2: actual retained fresh inspection and intent facts and an independently prepared new exact-target direction")
      val newtarget = target.copy(planRevision = revisedplan.revision, sourceRevision = sourcerevision,
        frontierRevision = failed.revision.value.toString, intentIdentity = intentreference.identity,
        intentRecordRevision = intent.next.revision.value.toString, inspection = inspection.payload.value,
        intent = intent.payload.value)
      val newdescription = V.GoalPhaseSourceHistoryRecoveryDescriptionResult(fixture.request.sessionId,
        "decision-required", R.message, question.human.get.humanDecisionContext.get.componentInstance,
        R.targetRecord(newtarget), R.choices, R.recoveryOperation)
      val requestfile = fixture.scenario.root.resolve("test-owned-second-source-direction.json")
      Files.writeString(requestfile, _json(newdescription))
      val newauthorization = _direction(fixture, newdescription, requestfile,
        "AuthorizeNewAttempt", "source-document-second-authorize")
      When("ordinary Advance reopens and public description or recovery is requested for the later same-Step failure")
      val waiting = _advance(fixture).business[V.GoalPhaseAdvanceResult]
      _describe(fixture).exitCode should not be 0
      _recover(fixture, newauthorization).exitCode should not be 0
      Then("the consumed direction remains consumed across document selection with unchanged old prefix checks and counters")
      waiting.kind shouldBe "Wait"
      waiting.repairProgress shouldBe episode.completed.repairProgress
      waiting.work shouldBe None
      _current(fixture) shouldBe failed
      failed.history.count(_.suspension.exists(_.completion.value == "goal-phase-ordered-human-completion-v1")) shouldBe 1
      _completed_facts(fixture) shouldBe facts
      executions(fixture.scenario) shouldBe checks
    }

    "refuse a semantic implementation frontier before any native inspection exists" in {
      Given("R1: accepted semantic work without a saved native inspection or first history intent")
      val fixture = implemented("source-history-unsupported-frontier", enabledSettings)
      val before = _current(fixture)
      When("the public description is requested before ordinary candidate inspection")
      val refused = _describe(fixture)
      Then("the unsupported frontier is refused without creating inspection or effects")
      refused.exitCode should not be 0
      _current(fixture) shouldBe before
      Files.readString(fixture.source) shouldBe changedText
      executions(fixture.scenario) shouldBe empty
    }

    "refuse an unknown staging effect after successful history normalization" in {
      Given("R1: an isolated accepted implementation with normal history and a denied staging capability")
      val fixture = implemented("source-history-staging-refused", enabledSettings)
      val config = fixture.scenario.root.resolve(".textus/config.json")
      val current = parse(Files.readString(config)).toOption.get
      val key = "textus.subsystem.process-execution.plan"
      val plan = parse(current.hcursor.get[String](key).toOption.get).toOption.get
      val stage = TypedGitHostPlanFixture.capability(org.goldenport.cncf.processexecution.GitOperationKind.StageSelectedAll,
        Some("goal-path-1"))
      val grants = plan.hcursor.downField("grants").focus.get.asArray.get.map { grant =>
        val capabilities = grant.hcursor.downField("capabilities").focus.get.asArray.get
          .filterNot(_.asString.contains(stage))
        grant.mapObject(_.add("capabilities", Json.fromValues(capabilities)))
      }
      val denied = plan.mapObject(_.add("grants", Json.fromValues(grants)))
      Files.writeString(config, current.mapObject(_.add(key, Json.fromString(denied.noSpaces))).noSpaces)
      When("ordinary Advance records history success and then fails at the selected staging capability")
      _advance(fixture).exitCode should not be 0
      val before = _current(fixture)
      Then("the actual stage intent remains outside the source-history recovery boundary")
      before.causation.value shouldBe "ordered-candidate:stage-intent-0"
      before.history.exists(_.causation.value == "ordered-candidate:source-history-result-0") shouldBe true
      _describe(fixture).exitCode should not be 0
      _advance(fixture).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
      _current(fixture) shouldBe before
      executions(fixture.scenario) shouldBe empty
    }

    "retain ordinary ownership admission when a new authorized candidate encounters a foreign global index" in {
      val fixture = _failed("source-history-foreign-index")
      Given("R4: an explicitly authorized new attempt with corrected source")
      val described = _describe(fixture)
      val description = described.business[V.GoalPhaseSourceHistoryRecoveryDescriptionResult]
      val authorization = _direction(fixture, description, described.invocation.stdout, "AuthorizeNewAttempt", "source-index-authorize")
      _recover(fixture, authorization).business[V.GoalPhaseSourceHistoryRecoveryResult].accepted shouldBe true
      Files.writeString(fixture.source, changedText)
      val before = _current(fixture)
      val repository = Path.of(fixture.scenario.host.target.root)
      Files.writeString(repository.resolve("foreign.txt"), "Independent out-of-scope change\n")
      When("the fixture stages an unrelated file and a later ordinary Advance inspects the new attempt")
      val process = new ProcessBuilder("/usr/bin/git", "--no-optional-locks", "-C", repository.toString,
        "-c", "core.hooksPath=/dev/null", "--literal-pathspecs", "add", "--", "foreign.txt")
        .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
      try {
        process.getOutputStream.close()
        process.waitFor(10, TimeUnit.SECONDS) shouldBe true
        process.exitValue() shouldBe 0
      } finally {
        if (process.isAlive) { process.destroyForcibly(); process.waitFor() }
        process.getInputStream.close()
        process.getErrorStream.close()
      }
      val index = indexBytes(fixture.scenario)
      val refused = _advance(fixture)
      Then("authorization cannot bypass the normal foreign-index and selected ownership checks")
      refused.exitCode should not be 0
      _current(fixture) shouldBe before
      indexBytes(fixture.scenario) shouldBe index
      Files.readString(fixture.source) shouldBe changedText
      executions(fixture.scenario) shouldBe empty
    }
  }
}
