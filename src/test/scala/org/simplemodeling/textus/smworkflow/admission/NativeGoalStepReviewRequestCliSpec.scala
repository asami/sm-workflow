package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.sql.DriverManager
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.convert.ValueReader
import org.goldenport.cncf.workflow.WorkflowWorkOrderJsonV1
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation
import org.goldenport.record.RecordPresentable
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseAcceptanceBoundary as B
import GoalPhaseOrderedProgression as O
import GoalPhaseStepReviewRequest as R
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Actual OLD to NEW installed-image qualification in newly test-owned roots.
  * No image or real Session is modified. Reviewer/authority evidence is fixture-owned.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalStepReviewRequestCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _required(name: String): Path = {
    val selected = Path.of(sys.props.getOrElse(name, throw new IllegalArgumentException("Missing " + name)))
    require(selected.isAbsolute, name + " must be absolute")
    selected.toRealPath()
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _read(path: Path): Json = parse(Files.readString(path)).fold(error => throw new AssertionError(error.message), identity)
  private final case class Invocation(exit: Int, stdout: Path, stderr: Path) {
    def business[A: ValueReader]: A = {
      withClue(Files.readString(stderr)) { exit shouldBe 0 }
      take(summon[ValueReader[A]].readC(take(new RecordDecoder().json(Files.readString(stdout)))))
    }
  }
  private final class Cli(root: Path, executable: Path, installations: Path, logname: String) {
    private var _ordinal = 0
    private var _operations = Vector.empty[String]
    def operations: Vector[String] = _operations
    def call(service: String, operation: String, arguments: Vector[String]): Invocation = {
      val logs = Files.createDirectories(root.resolve(logname))
      val index = _ordinal
      _ordinal += 1
      _operations = _operations :+ operation
      val stdout = logs.resolve(index + ".stdout.json")
      val stderr = logs.resolve(index + ".stderr.log")
      require(!Files.exists(stdout) && !Files.exists(stderr), "Retain every actual installed invocation")
      val descriptor = root.resolve(".textus/assembly.yaml")
      require(descriptor.isAbsolute && Files.isRegularFile(descriptor), "Select this scenario's existing absolute assembly descriptor")
      val builder = new ProcessBuilder((Vector(executable.toString, "--runtime-root", root.toString,
        "--textus.assembly.descriptor=" + descriptor.toString, "command",
        SmWorkflowComponent.componentId.name + "." + service + "." + operation) ++ arguments ++ Vector("--json")).asJava)
        .directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile)
      builder.environment().put("SM_WORKFLOW_INSTALLATIONS", installations.toString)
      val process = builder.start()
      try {
        process.getOutputStream.close()
        withClue(Files.readString(stderr)) { process.waitFor(240L, TimeUnit.SECONDS) shouldBe true }
        Invocation(process.exitValue(), stdout, stderr)
      } finally {
        if (process.isAlive) {
          val descendants = process.toHandle.descendants()
          val children = try descendants.iterator().asScala.toVector finally descendants.close()
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
    def advance(session: String): Invocation = call("GoalPhaseService", "advanceGoalPhase", Vector("--sessionId", session))
    def prepare(operation: String, input: RecordPresentable): Invocation =
      call("NativeWorkflowPreparationService", operation, Vector("--input", _json(input)))
    def file(name: String, value: RecordPresentable): String = {
      val path = Files.createTempDirectory(root, name + "-").resolve("input.json")
      Files.writeString(path, _json(value), StandardOpenOption.CREATE_NEW)
      path.toUri.toASCIIString
    }
    def importPrepared(value: Invocation): Unit = {
      withClue(Files.readString(value.stderr)) { value.exit shouldBe 0 }
      _read(value.stdout).hcursor.downField("datastore").focus.get.asArray.get should not be empty
      val config = root.resolve(".textus/config.json")
      Files.writeString(config, _read(config).mapObject(_.add("textus.import.data.file", Json.fromString(value.stdout.toString))).noSpaces)
    }
    def complete(plan: O.Plan, request: V.GoalPhaseAdvanceResult, name: String): Unit =
      submit(request, _result(plan, request, "completed"), name).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    def submit(request: V.GoalPhaseAdvanceResult, result: V.GoalPhaseWorkResult, name: String,
      execution: Option[V.WorkflowExecutionEvidence] = None): Invocation =
      call("GoalPhaseService", request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
        "--result", _json(result), "--resultReference", _json(V.WorkflowArtifactReference(name, "1"))) ++
        execution.toVector.flatMap(value => Vector("--executionEvidence", _json(value))))
    def review(plan: O.Plan, issued: Invocation, outcome: String, name: String): Unit = {
      val request = issued.business[V.GoalPhaseAdvanceResult]
      val result = _result(plan, request, outcome)
      val reference = V.WorkflowArtifactReference(name, "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
      importPrepared(prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "review",
        issued.stdout.toUri.toASCIIString, file(name + "-result", result), file(name + "-reference", reference),
        file(name + "-execution", execution), "normal-development-host", "2026-10-07T00:00:00Z",
        "entity:smworkflow.cli.host_evidence", None)))
      submit(request, result, name, Some(execution)).business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    }
    def authority(issued: Invocation, name: String): Unit = importPrepared(prepare("prepareAuthority",
      V.WorkflowAuthorityPreparationInput(issued.stdout.toUri.toASCIIString,
        file(name + "-reference", V.WorkflowArtifactReference(name, "1")), "participant", "normal-development-host",
        "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence")))
  }
  private def _result(plan: O.Plan, request: V.GoalPhaseAdvanceResult, outcome: String): V.GoalPhaseWorkResult = {
    val work = request.work.get
    val acceptance = take(B.captureC(None, plan)).effectiveAcceptance.map(boundary => V.GoalPhaseAcceptanceReport(
      B.applicable(boundary, work.kind).map(condition => V.GoalPhaseAcceptanceObservation(condition.identity,
        if (condition.required) "satisfied" else "unsatisfied", "Specification-owned observation of " + condition.description)), Vector.empty))
    val relevant = work.issues.toVector.flatMap(_.issues).filter(issue => (work.kind != "RepairStep" || issue.status != "RESOLVED") &&
      (work.kind == "ReviewPhase" || work.stepIdentity.contains(issue.step)))
    val dispositions = relevant.map(issue => V.GoalPhaseIssueDisposition(issue.identity,
      if (work.kind == "RepairStep") "FIXED" else if (outcome == "accept") "RESOLVED" else "NOT_REVIEWED",
      "Specification-owned exact issue disposition", None))
    V.GoalPhaseWorkResult(outcome, "Specification-owned " + work.kind + " " + outcome,
      if (outcome == "repair") Vector("Correct the selected documentation cleanup defect") else Vector.empty, acceptance,
      None, Option.when(work.kind != "ImplementStep" && work.issues.nonEmpty)(V.GoalPhaseIssueReport(dispositions)),
      None, None, None, None, None, dependencyValidation = None, diagnosticRepair = None)
  }
  private def _checks(scenario: F.Scenario): Vector[String] =
    Files.readString(scenario.root.resolve("sbt-executions.txt")).linesIterator.toVector
  /** Observation only: no fixture SQL writes or reconstructed Workflow rows. */
  private def _stored(scenario: F.Scenario): Vector[String] = {
    val connection = DriverManager.getConnection("jdbc:sqlite:" + scenario.root.resolve("workflow.sqlite").toUri.toASCIIString + "?mode=ro")
    try {
      val statement = connection.createStatement()
      try {
        val tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
        val names = Vector.newBuilder[String]
        try { while (tables.next()) names += tables.getString(1) } finally tables.close()
        names.result().flatMap { name =>
          val rows = statement.executeQuery("SELECT * FROM \"" + name.replace("\"", "\"\"") + "\"")
          val values = Vector.newBuilder[String]
          try { while (rows.next()) (1 to rows.getMetaData.getColumnCount).foreach { column =>
            Option(rows.getString(column)).filter(_.startsWith("cncf.workflow-json.v1:")).foreach(values += _)
          } } finally rows.close()
          values.result()
        }.sorted
      } finally statement.close()
    } finally connection.close()
  }
  private def _instance(scenario: F.Scenario, session: String): Json = {
    val instances = _stored(scenario).map(value => parse(value.stripPrefix("cncf.workflow-json.v1:")).toOption.get)
      .filter(value => value.hcursor.get[String]("identity").contains(session) && value.hcursor.downField("history").succeeded)
    instances.size shouldBe 1
    instances.head
  }
  private def _issued_generation(scenario: F.Scenario, request: V.GoalPhaseAdvanceResult): Long = {
    val orders = _stored(scenario).flatMap(raw => WorkflowWorkOrderJsonV1.decodeC(
      raw.stripPrefix("cncf.workflow-json.v1:"), GoalPhaseNativeWork.inputCodec).toOption)
      .map(_.current.asInstanceOf[WorkflowContinuation.WorkOrder[GoalPhaseNativeWork.Request]].request)
      .filter(_.continuationId.value == request.issuedWorkContext.get.continuationId)
    orders.size shouldBe 1
    val input = orders.head.input.get.value
    input.instanceIdentity shouldBe request.sessionId
    input.generation
  }

  "Installed OLD to NEW precommit Step review" should {
    "admit the explicit legacy host in the current installed image and refuse unissued purpose completion" in {
      Given("the selected current installed image and a fresh explicit legacy host")
      val executable = _required("smworkflow.spec.installedCommand")
      val newimage = _required("smworkflow.spec.runtimeRoot")
      val scenario = F.freshStepAssessment("installed-purpose-publication", TypedSbtHostPlanFixture.normalExecutable)
      val current = new Cli(scenario.root, executable, newimage, "installed-purpose-publication-logs")
      current.importPrepared(current.prepare("prepareGoalStart", scenario.selectedGoalPreparationInput))
      When("ordinary installed preparation, Start and Advance use the real configured factory")
      val session = current.goal("startGoalPhase", scenario.host.goal.input).business[V.GoalPhaseStartResult].sessionId
      val assigned = current.advance(session).business[V.GoalPhaseAdvanceResult]
      Then("host admission succeeds and genuine native implementation work is assigned")
      assigned.kind shouldBe "Request"
      assigned.work.get.kind shouldBe "ImplementStep"
      val before = _stored(scenario)
      When("the published purpose completion is called for a repository Session that was never started")
      val refused = current.call("RepositorySyncService", "submitRepositoryPurposeResult", Vector(
        "--sessionId", "specification-unissued-repository",
        "--result", _json(V.RepositorySyncPurposeResult("decision-needed", "No issued purpose work", Vector.empty, Vector.empty)),
        "--resultReference", _json(V.WorkflowArtifactReference("specification-unissued-purpose", "1"))))
      Then("native issued-work admission refuses it without changing retained Workflow facts")
      refused.exit should not be 0
      (Files.readString(refused.stdout) + Files.readString(refused.stderr)) should include("result session has no native execution")
      _stored(scenario) shouldBe before
    }

    "reopen genuine conditional Closing and retain its prefix checks and counters through native review repair and local closure" in {
      Given("P69-PRECOMMIT-REVIEW-01: explicit OLD/NEW images and a new test-owned two-Step native host")
      val executable = _required("smworkflow.spec.installedCommand")
      val oldimage = _required("smworkflow.spec.legacyRuntimeRoot")
      val newimage = _required("smworkflow.spec.runtimeRoot")
      val scenario = F.freshStepAssessment("installed-precommit-review", TypedSbtHostPlanFixture.normalExecutable, repairLimit = Some(10))
      val policy = GoalPhaseStepAssessment.Policy("conditional-documentation", "documentation", "conditional", "off", "off", Vector.empty)
      val selected = scenario.plan.copy(steps = scenario.plan.steps.updated(1,
        scenario.plan.steps(1).copy(actionPolicy = Some(policy))))
      Files.writeString(scenario.root.resolve("goal-plan.json"), selected.asJson.noSpaces)
      val repository = Path.of(scenario.host.target.root)
      val old = new Cli(scenario.root, executable, oldimage, "old-installed-precommit-logs")
      old.importPrepared(old.prepare("prepareGoalStart", scenario.selectedGoalPreparationInput))
      val session = old.goal("startGoalPhase", scenario.host.goal.input).business[V.GoalPhaseStartResult].sessionId
      val first = old.advance(session).business[V.GoalPhaseAdvanceResult]
      Files.writeString(repository.resolve("src/main/scala/org/simplemodeling/textus/smworkflow/p62authored/LineWriter.scala"),
        "package org.simplemodeling.textus.smworkflow.p62authored\nobject LineWriter { def render(value: String): String = value.reverse + \"\\n\" }\n")
      val test = repository.resolve(F.authoringPaths.head)
      Files.createDirectories(test.getParent)
      Files.writeString(test, """package org.simplemodeling.textus.smworkflow.p62authored
        |import org.scalatest.wordspec.AnyWordSpec
        |import org.scalatest.matchers.should.Matchers
        |import org.scalatest.GivenWhenThen
        |final class LineWriterSpec extends AnyWordSpec with Matchers with GivenWhenThen {
        |  "LineWriter" should {
        |    "reverse nonempty text and retain one terminal newline" in {
        |      Given("a selected nonempty line")
        |      val input = "abc"
        |      When("the ordinary renderer writes it")
        |      val actual = LineWriter.render(input)
        |      Then("the reversed text ends in one newline")
        |      actual shouldBe "cba\n"
        |    }
        |    "preserve an empty line" in {
        |      Given("an empty input line")
        |      val input = ""
        |      When("the ordinary renderer writes it")
        |      val actual = LineWriter.render(input)
        |      Then("one newline represents the empty line")
        |      actual shouldBe "\n"
        |    }
        |  }
        |}
        |""".stripMargin)
      When("the OLD installed implementation completes the first selected Step and its checks")
      old.complete(selected, first, "old-first-implementation")
      val firstclosing = old.advance(session)
      Then("the selected OLD and NEW images are distinct and first closure waits for authority")
      oldimage should not be newimage
      firstclosing.business[V.GoalPhaseAdvanceResult].work shouldBe None
      When("ordinary local authority closes the first Step and assigns the second")
      old.authority(firstclosing, "old-first-local-authority")
      val second = old.advance(session).business[V.GoalPhaseAdvanceResult]
      val secondgeneration = _issued_generation(scenario, second)
      Then("the actual second assignment preserves the accepted first Step")
      second.work.get.completed.size shouldBe 1
      When("the OLD installed image establishes genuine precommit conditional Closing")
      Files.writeString(repository.resolve("docs/validation.md"), "# Selected validation\n\nOriginal checked narrative before cleanup.\n")
      old.complete(selected, second, "old-second-implementation")
      val closingcall = old.advance(session)
      val closing = closingcall.business[V.GoalPhaseAdvanceResult]
      val before = _instance(scenario, session)
      val beforechecks = _checks(scenario)
      val oldreceipts = _stored(scenario).filter(_.contains("sm-workflow.goal-phase-native-validation.v2"))
      Then("the actual accepted prefix and complete successful receipts exist without a review or closure intent")
      closing.kind shouldBe "Wait"
      closing.work shouldBe None
      closing.validation.get.outcome shouldBe Some("passed")
      closing.validation.get.assessment.get.getString("review") shouldBe Some("not-executed")
      oldreceipts should not be empty
      val originalhistory = before.hcursor.get[Vector[Json]]("history").toOption.get
      originalhistory.last.hcursor.get[String]("progression").toOption shouldBe Some("phase.Closing")
      Given("a newly launched NEW installed process reopens the same native Session")
      val current = new Cli(scenario.root, executable, newimage, "new-installed-precommit-logs")
      When("passive description derives the exact target")
      val description = current.goal(R.describeOperation, V.GoalPhaseStepReviewDescriptionInput(session)).business[V.GoalPhaseStepReviewDescriptionResult]
      val target = take(R.targetC(description.target))
      val input = V.GoalPhaseStepReviewRequestInput(session, description.target,
        "An external dependency-delivery report motivates actual independent cleanup review")
      Then("description preserves the actual native Closing record")
      _instance(scenario, session) shouldBe before
      When("the exact request appends only native Review")
      val requestresult = current.goal(R.requestOperation, input).business[V.GoalPhaseStepReviewRequestResult]
      val requested = _instance(scenario, session)
      Then("one source-bound transition retains the old prefix counters and checks without issuing work")
      requestresult.accepted shouldBe true
      target.completed.map(_.commit.identity) shouldBe second.work.get.completed.map(_.commit.identity)
      target.generation shouldBe secondgeneration
      target.repairProgress.automaticCount shouldBe 0
      target.plan.repairLimit shouldBe 10
      requested.hcursor.get[Vector[Json]]("history").toOption.get.dropRight(1) shouldBe originalhistory
      requested.hcursor.downField("current").downField("suspension").focus shouldBe Some(Json.Null)
      _checks(scenario) shouldBe beforechecks
      When("the committed request is attempted again")
      val duplicate = current.goal(R.requestOperation, input)
      Then("the repeated request is refused")
      duplicate.exit should not be 0
      val reopened = new Cli(scenario.root, executable, newimage, "reopened-installed-precommit-logs")
      When("a further NEW process advances after the committed request without any issued work yet")
      val reviewcall = reopened.advance(session)
      val review = reviewcall.business[V.GoalPhaseAdvanceResult]
      val reviewgeneration = _issued_generation(scenario, review)
      val repeatedreview = reopened.advance(session).business[V.GoalPhaseAdvanceResult]
      Then("one independent same-generation ReviewStep uses the untouched checked candidate and repeated Advance returns it")
      review.work.get.kind shouldBe "ReviewStep"
      reviewgeneration shouldBe target.generation
      review.work.get.candidate.get.tree shouldBe target.candidate.tree
      review.work.get.purpose shouldBe target.plan.steps.find(_.identity == target.step).get.purpose
      review.message should include(input.reason)
      review.work.get.review.get.checks should not be empty
      repeatedreview shouldBe review
      _checks(scenario) shouldBe beforechecks
      When("the real assigned independent reviewer requires repair")
      reopened.review(selected, reviewcall, "repair", "new-independent-cleanup-review")
      val repair = reopened.advance(session).business[V.GoalPhaseAdvanceResult]
      val repairgeneration = _issued_generation(scenario, repair)
      val repeatedrepair = reopened.advance(session).business[V.GoalPhaseAdvanceResult]
      Then("the actual RepairStep stays on the selected Step and charges one repair batch")
      repair.work.get.kind shouldBe "RepairStep"
      repair.work.get.stepIdentity shouldBe Some(target.step)
      repair.repairProgress.get.automaticCount shouldBe 1
      repair.repairProgress.get.ordinaryCount shouldBe 1
      repair.message should not include input.reason
      repeatedrepair shouldBe repair
      When("the actual RepairStep changes its selected candidate and normal checks qualify it")
      Files.writeString(repository.resolve("docs/validation.md"), "# Selected validation\n\nCleanup corrected after the independent review.\n")
      reopened.complete(selected, repair, "new-cleanup-repair")
      val correctedcall = reopened.advance(session)
      val corrected = correctedcall.business[V.GoalPhaseAdvanceResult]
      val correctedgeneration = _issued_generation(scenario, corrected)
      Then("fresh actual checks precede independent review despite the immutable conditional Plan")
      corrected.work.get.kind shouldBe "ReviewStep"
      correctedgeneration shouldBe repairgeneration
      corrected.work.get.purpose shouldBe target.plan.steps.find(_.identity == target.step).get.purpose
      corrected.message should include(input.reason)
      corrected.work.get.candidate.get.tree should not be target.candidate.tree
      corrected.repairProgress.get.automaticCount shouldBe 1
      corrected.validation.get.outcome shouldBe Some("passed")
      _checks(scenario).size should be > beforechecks.size
      oldreceipts.foreach(receipt => _stored(scenario) should contain(receipt))
      When("fresh independent acceptance requests separate ordinary local closure authority")
      reopened.review(selected, correctedcall, "accept", "new-independent-corrected-review")
      val authority = reopened.advance(session)
      Then("the actual closure request requires Step closure authority")
      authority.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
      When("separate local authority performs ordinary closure")
      reopened.authority(authority, "new-second-local-authority")
      val phasecall = reopened.advance(session)
      val phase = phasecall.business[V.GoalPhaseAdvanceResult]
      Then("the repaired Step commits locally with the original accepted prefix and no duplicate request or native review issue")
      phase.work.get.kind shouldBe "ReviewPhase"
      phase.work.get.completed.size shouldBe 2
      phase.work.get.completed.head.commit.identity shouldBe target.completed.head.commit.identity
      phase.work.get.completed(1).review shouldBe Some("new-independent-corrected-review")
      val actualhistory = _instance(scenario, session).hcursor.get[Vector[Json]]("history").toOption.get
      actualhistory.take(originalhistory.size) shouldBe originalhistory
      actualhistory.count(_.hcursor.get[String]("causation").toOption.exists(_.startsWith("ordered-step-review-request:"))) shouldBe 1
      actualhistory.count(_.hcursor.get[String]("causation").toOption.exists(_.startsWith("ordered-review-issued:"))) shouldBe 3
      When("the original target is attempted after the repaired Step has committed")
      val stale = current.goal(R.requestOperation, input)
      Then("the stale request is refused without restarting or answering a Human question")
      stale.exit should not be 0
      old.operations.count(_ == "startGoalPhase") shouldBe 1
      (current.operations ++ reopened.operations) should not contain "startGoalPhase"
      (old.operations ++ current.operations ++ reopened.operations) should not contain "submitGoalPhaseOrderedDecision"
    }
  }
}
