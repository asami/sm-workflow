package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import java.sql.DriverManager
import java.time.Instant
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.goldenport.record.RecordPresentable
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhasePlanRevision as R
import GoalPhaseCurrentInventoryContinuation as C
import SplitPhaseExplicitPartitionFixture.take

/** Small isolated normal host, actual native Operations, ordinary Compile/Test
  * and Git closure. All semantic/review/closure inputs are specification-owned.
  * The same scenario uses the selected installed command without source fallback.
  * @since   Nov. 3, 2026
  * @version Nov. 3, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalCurrentInventoryCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _source = "src/main/scala/NeededInventory.scala"
  private val _added = Vector("build.sbt", _source)
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _read(path: Path): Json = parse(Files.readString(path, UTF_8)).toOption.get
  private def _normal(scenario: F.Scenario): Unit = {
    val path = scenario.root.resolve(".textus/config.json")
    Files.writeString(path, _read(path).mapObject(_.remove("sm-workflow.runtime.definition")
      .add("textus.assembly.descriptor", Json.fromString(scenario.root.resolve(".textus/assembly.yaml").toString))).noSpaces, UTF_8)
  }
  private def _fresh(name: String, normal: Boolean = true): F.Scenario = {
    val scenario = F.fresh(name, sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), repairContinuation = true)
    // The actual packaged normal repository profile supports runtime selections;
    // initial setup adds only the selected ordinary Test effect and its grants.
    val path = scenario.root.resolve(".textus/config.json")
    val config = _read(path)
    val descriptor = take(GenericSubsystemDescriptor.loadComponentArchive(Path.of("src/main/car").toAbsolutePath))
    val template = parse(descriptor.config("textus.subsystem.process-execution.plan")).toOption.get
    val values = Map("repository.root" -> scenario.root.resolve("repository").toString, "git.branch" -> "main",
      "git.remote" -> "origin", "git.remote-branch" -> "main", "git.endpoint" -> scenario.root.resolve("upstream.git").toString,
      "git.user-name" -> "Fixture", "git.user-email" -> "fixture@example.invalid",
      "environment.path" -> "/usr/bin:/bin", "environment.java-home" -> "", "environment.ssh-auth-sock" -> "")
    def _expand_(value: Json): Json = value.mapString(text => values.foldLeft(text) { case (prior, (key, value)) =>
      prior.replace("${" + key + "}", value) }).mapArray(_.map(_expand_)).mapObject(_.mapValues(_expand_))
    def _array_(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
    val selected = _expand_(template)
    val sbt = TypedSbtHostPlanFixture.plan(scenario.root, TypedSbtHostPlanFixture.normalExecutable, Vector(1),
      operation = SbtOperation.Test)
    val removedcapabilities = _array_(selected, "sbtPrograms").map(_.hcursor.get[Json]("capability").toOption.get).toSet
    val grants = _array_(selected, "grants").map { grant =>
      val owner = grant.hcursor.get[String]("componentId").toOption.get
      val capabilities = _array_(grant, "capabilities").filterNot(removedcapabilities)
      val added = _array_(sbt, "grants").filter(_.hcursor.get[String]("componentId").contains(owner))
        .flatMap(_array_(_, "capabilities"))
      grant.mapObject(_.add("capabilities", Json.fromValues((capabilities ++ added).distinct)))
    }
    val process = selected.mapObject(_.add("sbtPrograms", Json.fromValues(_array_(sbt, "sbtPrograms")))
      .add("workspaces", Json.fromValues((_array_(selected, "workspaces") ++ _array_(sbt, "workspaces")).distinct))
      .add("workspaceGrants", Json.fromValues((_array_(selected, "workspaceGrants") ++ _array_(sbt, "workspaceGrants")).distinct))
      .add("grants", Json.fromValues(grants)))
    Files.writeString(path, config.mapObject(_.add("textus.subsystem.process-execution.plan",
      Json.fromString(process.noSpaces)).add("textus.assembly.descriptor",
      Json.fromString(scenario.root.resolve(".textus/assembly.yaml").toString))).noSpaces, UTF_8)
    if (normal) _normal(scenario)
    scenario
  }
  private def _start(scenario: F.Scenario): Path = {
    val task = scenario.ownTask("current-inventory")
    scenario.retain(task, scenario.start().business[V.GoalPhaseStartResult])
    task
  }
  private def _proposal(scenario: F.Scenario): R.Proposal = R.Proposal("needed-inventory", "1",
    "SPECIFICATION-OWNED: the retained product requires its existing build definition and one concrete new compiled source",
    scenario.plan.copy(revision = "inventory-2", steps = scenario.plan.steps.updated(0,
      scenario.plan.steps.head.copy(ownedPaths = scenario.plan.steps.head.ownedPaths ++ _added))))
  private def _result(outcome: String, proposal: Option[R.Proposal] = None): V.GoalPhaseWorkResult =
    V.GoalPhaseWorkResult(outcome, "SPECIFICATION-OWNED " + outcome, Vector.empty,
      None, None, None, R.publicProposal(proposal), None, None, None, None,
      dependencyValidation = None, diagnosticRepair = None)
  private def _submit(scenario: F.Scenario, work: V.GoalPhaseAdvanceResult,
    result: V.GoalPhaseWorkResult, name: String): F.Result = scenario.call("command", work.submitOperation.get,
      Vector("--sessionId", work.submitSessionId.get, "--result", _json(result), "--resultReference",
        _json(V.WorkflowArtifactReference(name, "1"))))
  private def _file(scenario: F.Scenario, name: String, value: RecordPresentable): String = {
    val path = scenario.root.resolve(name + ".json")
    Files.writeString(path, _json(value), UTF_8, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  /** Read-only retained envelopes provide provenance evidence, never fixture input. */
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
        }
      } finally statement.close()
    } finally connection.close()
  }

  "AUTO-FILE-GP ordinary native and installed current inventory" should afterWord(
    "in spec:goal-phase-current-inventory-continuation, example:E1, rules:R1,R2,R3,R4, phase:AUTO-FILE-GP-01") {
    "append an existing unchanged file and new source without Human and retain them through checks review and actual Step closure" in {
      Given("docs/spec/goal-phase-current-inventory-continuation.md R1–R3 E1: one small isolated normal host and specification-owned semantic inputs")
      val scenario = _fresh("auto-file-gp-native")
      val task = _start(scenario)
      val original = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      val repository = Path.of(scenario.host.target.root)
      val build = Files.readString(repository.resolve("build.sbt"), UTF_8)
      val proposal = _proposal(scenario)
      Files.exists(repository.resolve(_source)) shouldBe false
      When("the actual issued implementation submits its concrete inventory-only proposal and ordinary Advance evaluates it")
      _submit(scenario, original, _result("decision-needed", Some(proposal)), "actual-inventory-source")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val old = _stored(scenario)
      val continued = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the same Session issues the current implementation under the appended inventory without a fresh Human grant")
      continued.kind shouldBe "Request"
      continued.human shouldBe None
      continued.work.get.kind shouldBe "ImplementStep"
      continued.sessionId shouldBe original.sessionId
      continued.work.get.stepIdentity shouldBe original.work.get.stepIdentity
      continued.work.get.originalBase shouldBe original.work.get.originalBase
      continued.work.get.completed shouldBe original.work.get.completed
      continued.repairProgress shouldBe original.repairProgress
      continued.work.get.selectedPlan shouldBe original.work.get.selectedPlan.copy(revision = "inventory-2",
        steps = original.work.get.selectedPlan.steps.updated(0, original.work.get.selectedPlan.steps.head.copy(
          ownedPaths = original.work.get.selectedPlan.steps.head.ownedPaths ++ _added)))
      Files.readString(repository.resolve("build.sbt"), UTF_8) shouldBe build
      scenario.advance(task).business[V.GoalPhaseAdvanceResult] shouldBe continued
      val retained = _stored(scenario)
      val semantic = old.filter(value => value.contains("actual-inventory-source") &&
        value.contains("sm-workflow.goal-phase-native-work-result.v1"))
      semantic should not be empty
      semantic.foreach(value => retained should contain(value))
      retained.exists(_.contains(C.decision)) shouldBe true
      retained.exists(_.contains(C.causation)) shouldBe true
      retained.exists(_.contains("actual-inventory-source")) shouldBe true
      retained.exists(_.contains(":ordered-human:")) shouldBe false

      Given("the actual appended source and product change, with no new host file registration")
      Files.createDirectories(repository.resolve(_source).getParent)
      Files.writeString(repository.resolve(_source), "object NeededInventory { val value = 42 }\n", UTF_8)
      Files.writeString(repository.resolve("value.txt"), "expected\n", UTF_8)
      When("ordinary Submit and Advance stage the actual candidate and execute the selected native Test")
      _submit(scenario, continued, _result("completed"), "inventory-implementation")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val checked = scenario.advance(task)
      val review = checked.business[V.GoalPhaseAdvanceResult]
      Then("native independent review receives the appended Plan, concrete source diff and actual successful checks")
      review.work.get.kind shouldBe "ReviewStep"
      review.work.get.selectedPlan shouldBe continued.work.get.selectedPlan
      review.work.get.review.get.toString should include(_source)
      review.work.get.review.get.checks.head.exitCode shouldBe Some(0)
      review.work.get.review.get.checks.head.sbt.get.outcome shouldBe "succeeded"
      scenario.advance(task).business[V.GoalPhaseAdvanceResult] shouldBe review

      Given("separate specification-owned independent review evidence for this exact actual candidate")
      val result = _result("accept")
      val reference = V.WorkflowArtifactReference("inventory-independent-review", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
      val prepared = scenario.prepareHostInput("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "review",
        checked.invocation.stdout.toUri.toASCIIString, _file(scenario, "review-result", result),
        _file(scenario, "review-reference", reference), _file(scenario, "review-execution", execution),
        "normal-development-host", Instant.now().minusSeconds(60).toString, "entity:smworkflow.cli.host_evidence", None).toRecord())
      scenario.selectPreparedImport(prepared)
      When("the ordinary review Submit and subsequent closure receive their separately prepared native evidence")
      scenario.call("command", review.submitOperation.get, Vector("--sessionId", review.submitSessionId.get,
        "--result", _json(result), "--resultReference", _json(reference), "--executionEvidence", _json(execution)))
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val waiting = scenario.advance(task)
      waiting.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
      val authority = scenario.prepareHostInput("prepareAuthority", V.WorkflowAuthorityPreparationInput(
        waiting.invocation.stdout.toUri.toASCIIString, _file(scenario, "step-authority", V.WorkflowArtifactReference("inventory-step-authority", "1")),
        "participant", "normal-development-host", Instant.now().minusSeconds(60).toString,
        "entity:smworkflow.cli.host_evidence").toRecord())
      scenario.selectPreparedImport(authority)
      val next = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("a real scoped Step commit contains the new source and the next Step retains original base and repair accounting")
      next.work.get.kind shouldBe "ImplementStep"
      next.work.get.stepIdentity shouldBe Some("step-2")
      next.work.get.originalBase shouldBe original.work.get.originalBase
      next.work.get.completed.size shouldBe 1
      next.work.get.completed.head.commit.identity shouldBe scenario.candidateHead
      next.work.get.completed.head.commit.parent shouldBe original.work.get.originalBase.head
      next.repairProgress shouldBe original.repairProgress
      Files.readString(repository.resolve(_source), UTF_8) should include("value = 42")
      val repeated = scenario.advance(task).business[V.GoalPhaseAdvanceResult]
      // The one-time commit-hygiene note is presentation; every business field stays exact.
      repeated.copy(message = next.message) shouldBe next
      scenario.candidateHead shouldBe next.work.get.completed.head.commit.identity
    }

    "retain a purpose concern as Human and supersede only a proven unanswered inventory question without an answer" in {
      Given("docs/spec/goal-phase-current-inventory-continuation.md R3,R4 E2: an explicit purpose revision in a small normal host")
      val concern = _fresh("auto-file-gp-concern")
      val task = _start(concern)
      val initial = concern.advance(task).business[V.GoalPhaseAdvanceResult]
      val purpose = R.Proposal("actual-purpose-concern", "1", "SPECIFICATION-OWNED unresolved product purpose",
        concern.plan.copy(revision = "purpose-2", steps = concern.plan.steps.updated(0,
          concern.plan.steps.head.copy(purpose = "Implement a different product requirement"))))
      When("the authentic semantic completion changes the product purpose")
      _submit(concern, initial, _result("decision-needed", Some(purpose)), "purpose-source")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val question = concern.advance(task).business[V.GoalPhaseAdvanceResult]
      Then("the real judgment remains an unanswered ordinary Human question")
      question.kind shouldBe "Human"
      concern.advance(task).business[V.GoalPhaseAdvanceResult] shouldBe question
      _stored(concern).exists(_.contains(C.decision)) shouldBe false

      Given("an actual current implementation reporting both a concrete inventory proposal and an unresolved finding")
      val mixed = _fresh("auto-file-gp-mixed-finding")
      val mixedtask = _start(mixed)
      val mixedwork = mixed.advance(mixedtask).business[V.GoalPhaseAdvanceResult]
      val mixedresult = _result("decision-needed", Some(_proposal(mixed))).copy(
        findings = Vector("SPECIFICATION-OWNED unresolved requirement judgment alongside the needed files"))
      When("ordinary semantic Submit retains the complete mixed result and Advance considers automatic inventory")
      _submit(mixed, mixedwork, mixedresult, "mixed-inventory-finding-source")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val mixedquestion = mixed.advance(mixedtask).business[V.GoalPhaseAdvanceResult]
      Then("the genuine finding remains Human and reopening never supersedes it with an automatic inventory receipt")
      mixedquestion.kind shouldBe "Human"
      mixed.advance(mixedtask).business[V.GoalPhaseAdvanceResult] shouldBe mixedquestion
      val mixedstored = _stored(mixed)
      mixedstored.exists(_.contains("mixed-inventory-finding-source")) shouldBe true
      mixedstored.exists(_.contains(mixedresult.findings.head)) shouldBe true
      mixedstored.exists(_.contains(C.decision)) shouldBe false
      mixedstored.exists(_.contains(C.humanCausation)) shouldBe false

      Given("an actual fixed host with a missing literal capability which has already issued a file-only question")
      val pending = _fresh("auto-file-gp-unanswered", normal = false)
      val pendingtask = _start(pending)
      val work = pending.advance(pendingtask).business[V.GoalPhaseAdvanceResult]
      _submit(pending, work, _result("decision-needed", Some(_proposal(pending))), "unanswered-inventory-source")
        .business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
      val unanswered = pending.advance(pendingtask).business[V.GoalPhaseAdvanceResult]
      unanswered.kind shouldBe "Human"
      val old = _stored(pending)
      When("normal standard composition admits invocation-local inventory under the original purpose")
      _normal(pending)
      val continued = pending.advance(pendingtask).business[V.GoalPhaseAdvanceResult]
      Then("native supersession retains the actual question and source result without manufacturing a Human answer")
      continued.work.get.kind shouldBe "ImplementStep"
      continued.human shouldBe None
      continued.work.get.selectedPlan.steps.head.ownedPaths shouldBe work.work.get.selectedPlan.steps.head.ownedPaths ++ _added
      continued.work.get.originalBase shouldBe work.work.get.originalBase
      continued.repairProgress shouldBe work.repairProgress
      pending.advance(pendingtask).business[V.GoalPhaseAdvanceResult] shouldBe continued
      val retained = _stored(pending)
      val semantic = old.filter(value => value.contains("unanswered-inventory-source") &&
        value.contains("sm-workflow.goal-phase-native-work-result.v1"))
      semantic should not be empty
      semantic.foreach(value => retained should contain(value))
      retained.exists(_.contains(C.humanCausation)) shouldBe true
      retained.exists(_.contains(C.decision)) shouldBe true
      retained.exists(_.contains("AdoptPlanRevision\",\"rationale")) shouldBe false
    }
  }
}
