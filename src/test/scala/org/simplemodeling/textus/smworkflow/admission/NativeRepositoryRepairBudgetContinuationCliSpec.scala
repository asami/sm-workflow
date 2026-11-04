package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowWorkOrderJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import RepositorySyncPurpose as P
import RepositorySyncNativeMergeValidation as C
import RepositorySyncRepairBudgetContinuation as B
import RepositorySyncPurposePreparationContinuation as A
import RepositorySyncRepairScopeCorrection as N
import RepositorySyncNativeMergeRepair as M
import RepositorySyncRepairInventory.given
import SplitPhaseExplicitPartitionFixture.take

/** Explicit before/after qualification against installed product images.
  * The tiny build, semantic answers and independent reviews belong to this
  * fixture; they make no claim about real Human or consumer approval.
  * Native rows are observed read only. Runtime/UoW owns every workflow effect.
  * @since Nov. 4, 2026
  * @version Nov. 4, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryRepairBudgetContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeRepositorySelectionCorrectionFixture {
  private val _phase = sys.props.getOrElse("smworkflow.spec.repairBudgetPhase",
    fail("Select -Dsmworkflow.spec.repairBudgetPhase=before|after|scope-before|scope-after"))
  require(Set("before", "after", "scope-before", "scope-after")(_phase), "Select exactly one installed repair-budget phase")
  private val _scope_mode = _phase.startsWith("scope-")
  private lazy val _scope_episode = Path.of(sys.props.getOrElse("smworkflow.spec.repairBudgetScopeEpisode",
    fail("Select -Dsmworkflow.spec.repairBudgetScopeEpisode=<fresh absolute scope checkpoint JSON>")))
  private val _episode = if (_scope_mode) _scope_episode.resolveSibling(_scope_episode.getFileName.toString + ".limit.json")
    else Path.of(sys.props.getOrElse("smworkflow.spec.repairBudgetEpisode",
      fail("Select -Dsmworkflow.spec.repairBudgetEpisode=<absolute saved JSON>")))
  require(_episode.isAbsolute && _episode.normalize == _episode && Files.isDirectory(_episode.getParent))
  private val _test_path = "src/test/scala/fixture/SelectedInputSpec.scala"
  private val _document_path = "docs/budget.md"
  private val _baseline_path = "src/main/scala/fixture/BudgetBaseline.scala"
  private val _checks = originalChecks :+ appendedCheck
  // The frozen corrected recipe uses StandardWorkflowRuntime's unchanged
  // default three repairs; there is no fixture limit or later host override.
  private val _original_limit = 3
  private val _slot_count = 6

  private def _test(expected: String): String = """package fixture
    |class SelectedInputSpec extends org.scalatest.wordspec.AnyWordSpec
    |    with org.scalatest.GivenWhenThen with org.scalatest.matchers.should.Matchers {
    |  "the tiny product input" should {
    |    "retain its complete intended value" in {
    |      Given("the actual materialized Purpose workspace")
    |      When("the original selected test reads the product input")
    |      val actual = java.nio.file.Files.readString(java.nio.file.Paths.get("value.txt"))
    |      Then("the actual input satisfies the intended expectation")
    |      actual shouldBe __EXPECTED__
    |    }
    |  }
    |}
    |""".stripMargin.replace("      actual shouldBe __EXPECTED__",
      "      actual shouldBe __EXPECTED__" + (if (_scope_mode)
        "\n      BudgetBaseline.expectedValue shouldBe __EXPECTED__" else ""))
      .replace("__EXPECTED__", expected.asJson.noSpaces)

  private def _baseline(expected: String): String =
    "package fixture\nobject BudgetBaseline {\n  def expectedValue: String = " + expected.asJson.noSpaces + "\n}\n"

  private def _array(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
  private def _repository(scenario: F.Scenario): Path = Path.of(scenario.host.target.root)
  private def _parameters: Map[String, String] = selectionParameters(corrected = true) +
    ("sm-workflow.repository-sync.dirty-purposes" -> P.Policy((1 to _slot_count).toVector.map { ordinal =>
      P.Slot("purpose-slot-" + ordinal, _checks, GoalPhaseStepAssessment.Policy("budget-purpose-" + ordinal,
        if (ordinal == 1) "documentation" else "code", "required", "off", "off", Vector.empty))
    }, Vector.empty).asJson.noSpaces)

  private def _plan(scenario: F.Scenario): Json = {
    val base = selectionPlan(scenario, broken = false, corrected = true)
    def cloneProgram(value: Json, ordinal: Int): Json = value.mapObject(fields => fields
      .add("target", ("purpose-slot-" + ordinal).asJson)
      .add("identity", (fields("identity").get.asString.get + "-budget-slot-" + ordinal).asJson)
      .add("capability", (fields("capability").get.asString.get + "-budget-slot-" + ordinal).asJson))
    val addedgit = (2 to _slot_count).toVector.flatMap(ordinal => _array(base, "gitPrograms")
      .filter(_.hcursor.get[String]("target").toOption.contains("purpose-slot-1")).map(cloneProgram(_, ordinal)))
    val addedsbt = (2 to _slot_count).toVector.flatMap(ordinal => _array(base, "sbtPrograms").map(cloneProgram(_, ordinal)))
    val workspaces = (2 to _slot_count).toVector.map { ordinal =>
      val identity = "purpose-slot-" + ordinal
      Files.createDirectories(scenario.root.resolve(identity))
      Json.obj("identity" -> identity.asJson, "root" -> scenario.root.resolve(identity).toString.asJson)
    }
    val grants = (2 to _slot_count).toVector.map(ordinal => _array(base, "workspaceGrants")
      .find(_.hcursor.get[String]("workspaceId").toOption.contains("purpose-slot-1")).get
      .mapObject(_.add("workspaceId", ("purpose-slot-" + ordinal).asJson)))
    base.mapObject(fields => fields.add("gitPrograms", (_array(base, "gitPrograms") ++ addedgit).asJson)
      .add("sbtPrograms", (_array(base, "sbtPrograms") ++ addedsbt).asJson)
      .add("workspaces", (_array(base, "workspaces") ++ workspaces).asJson)
      .add("workspaceGrants", (_array(base, "workspaceGrants") ++ grants).asJson)
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(existing => (existing ++ (addedgit ++ addedsbt)
          .map(_.hcursor.get[Json]("capability").toOption.get)).distinct)))))))
  }

  private def _invocations(scenario: F.Scenario): Vector[String] = {
    val path = scenario.root.resolve("budget-sbt-invocations.txt")
    if (Files.exists(path)) Files.readString(path).linesIterator.toVector else Vector.empty
  }
  private def _used(scenario: F.Scenario, session: String): Vector[String] =
    current(scenario, session).history.flatMap(_.suspension).map(_.continuationIdentity.value)
      .filter(_.contains(":native-repair:")).map(RepositorySyncRepairScopeCorrection.budgetIdentity).distinct
  private def _native(scenario: F.Scenario, session: String): Vector[(String, Json)] =
    receipts(scenario, session).map(receipt => receipt.reference.identity -> receipt.payload.value.asJson).sortBy(_._1)
  private def _effects(scenario: F.Scenario, session: String): Json = {
    val repository = _repository(scenario)
    Json.obj("head" -> git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD").asJson,
      "indexEntries" -> git(scenario.root, "-C", repository.toString, "ls-files", "--stage").asJson,
      "indexTree" -> git(scenario.root, "-C", repository.toString, "write-tree").asJson,
      "status" -> git(scenario.root, "-C", repository.toString, "status", "--porcelain").asJson,
      "diff" -> git(scenario.root, "-C", repository.toString, "diff", "HEAD", "--").asJson,
      "source" -> Json.obj((Vector("value.txt" -> Files.readString(repository.resolve("value.txt")).asJson,
        _test_path -> Files.readString(repository.resolve(_test_path)).asJson,
        _document_path -> Files.readString(repository.resolve(_document_path)).asJson) ++
        Option.when(_scope_mode)(_baseline_path -> Files.readString(repository.resolve(_baseline_path)).asJson))*),
      "checks" -> formalReceipts(scenario, session).asJson, "invocations" -> _invocations(scenario).asJson)
  }
  private def _record(scenario: F.Scenario, session: String): String =
    take(WorkflowInstanceRecordJsonV1.encodeC(current(scenario, session)))
  private def _failed(scenario: F.Scenario, session: String): P.Progress = receipts(scenario, session)
    .filter(_.payload.value.stage == "PurposeRepairRequired").maxBy(_.next.revision.value).payload.value.purposes.get
  private def _assert_failure(scenario: F.Scenario, session: String): Unit = {
    val progress = _failed(scenario, session)
    progress.cursor shouldBe 1
    progress.plan.get.groups(1).content should contain(P.Content(_test_path, Some(_test("expected\n"))))
    val failed = progress.receipts.find(_.identity == originalChecks(1).identity).get
    failed.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
    failed.exitCode.exists(_ != 0) shouldBe true
    failed.stdout should include("SelectedInputSpec")
    failed.stdout should include("FAILED")
    (failed.stdout + failed.stderr) should not include "SmWorkflowInvalidFixtureOption"
  }
  private def _submit(scenario: F.Scenario, session: String, files: Vector[V.RepositorySyncNativeMergeRepairFile],
    value: String, ordinal: Int): F.Result = {
    val reference = V.WorkflowArtifactReference("fixture-budget-repair-" + ordinal, "1")
    files.map(_.path).toSet shouldBe Set("value.txt", _test_path)
    files.find(_.path == _test_path).get.text shouldBe _test("expected\n")
    val replacements = files.map(file => if (file.path == "value.txt") file.copy(text = value) else file)
    val result = V.RepositorySyncNativeMergeRepairResult("repaired",
      "Fixture-authored repair proposal " + ordinal + "; retain the complete intended test source",
      replacements, None)
    val submitted = successful(scenario.repositoryCall("submitNativeMergeRepair", Vector("--sessionId", session,
      "--result", json(result), "--resultReference", json(reference), "--executionEvidence",
      json(V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-repair-author"), None, None)))))
    submitted.business[V.RepositorySyncNativeMergeRepairSubmissionResult].accepted shouldBe true
    submitted
  }
  private def _review(scenario: F.Scenario, session: String, described: F.Result, suffix: String): Unit = {
    val reference = V.WorkflowArtifactReference("fixture-budget-independent-review-" + suffix, "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-independent-reviewer"), None, None)
    val verdict = V.RepositorySyncNativeMergeReviewResult("accept",
      "Independent fixture-authored review of this actual candidate and original checks; no real Human acceptance is claimed", Vector.empty)
    val preparation = V.WorkflowEvidencePreparationInput("RepositorySync", "review", described.invocation.stdout.toUri.toASCIIString,
      save(scenario.root, "budget-review-" + suffix + ".json", json(verdict)),
      save(scenario.root, "budget-review-reference-" + suffix + ".json", json(reference)),
      save(scenario.root, "budget-review-execution-" + suffix + ".json", json(execution)),
      "normal-development-host", java.time.Instant.now().toString, "entity:smworkflow.cli.host_evidence", None)
    val prepared = successful(scenario.prepareHostInput("prepareEvidence",
      take(new org.goldenport.record.io.RecordDecoder().json(json(preparation)))))
    scenario.selectPreparedImport(prepared)
    successful(scenario.repositoryCall("submitNativeMergeReview", Vector("--sessionId", session, "--result", json(verdict),
      "--resultReference", json(reference), "--executionEvidence", json(execution))))
      .business[V.RepositorySyncNativeMergeReviewSubmissionResult].accepted shouldBe true
  }

  private def _before(): Unit = {
    installed()
    if (Files.exists(_episode)) {
      _recollect_before()
      return
    }
    Given("the old normal installed runtime and a tiny fixture with six original fixed slots and the unchanged limit of three")
    if (Files.exists(_inprogress)) {
      _resume_before()
      return
    }
    val scenario = fresh("repair-budget-before", checked = true)
    val repository = _repository(scenario)
    val descriptor = scenario.root.resolve(".textus/assembly.yaml")
    Files.writeString(descriptor, Files.readString(descriptor).replace("      id: participant\n", "      id: sm-workflow-local\n"), UTF_8)
    Files.writeString(repository.resolve("build.sbt"), """scalaVersion := "2.12.18"
      |libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.19" % Test
      |Global / onLoad := {
      |  val prior = (Global / onLoad).value
      |  state => {
      |    val loaded = prior(state)
      |    IO.append(file(__INVOCATIONS__), java.lang.ProcessHandle.current().pid().toString + "\n")
      |    loaded
      |  }
      |}
      |""".stripMargin.replace("__INVOCATIONS__", scenario.root.resolve("budget-sbt-invocations.txt").toString.asJson.noSpaces))
    Files.createDirectories(repository.resolve(_test_path).getParent)
    Files.writeString(repository.resolve(_test_path), _test("initial\n"))
    Files.writeString(repository.resolve(_document_path), "Original independent document\n")
    if (_scope_mode) {
      Files.createDirectories(repository.resolve(_baseline_path).getParent)
      Files.writeString(repository.resolve(_baseline_path), _baseline("initial\n"))
      git(scenario.root, "-C", repository.toString, "add", "--", _baseline_path)
    }
    git(scenario.root, "-C", repository.toString, "add", "--", "build.sbt", _test_path, _document_path)
    git(scenario.root, "-C", repository.toString, "commit", "-m", "Tiny actual repair-budget product fixture")
    git(scenario.root, "-C", repository.toString, "push", scenario.host.target.endpoint, "HEAD:refs/heads/main")
    configure(scenario, _parameters + ("textus.subsystem.process-execution.plan" -> _plan(scenario).noSpaces))
    val configuration = Files.readString(scenario.root.resolve(".textus/config.json"))
    parse(configuration).toOption.get.hcursor.downField("sm-workflow.runtime.definition").focus shouldBe None
    Files.writeString(repository.resolve(_document_path), "Completed independent document\n")
    Files.writeString(repository.resolve(_test_path), _test("expected\n"))
    Files.writeString(repository.resolve("value.txt"), "wrong-initial\n")
    val session = start(scenario)
    successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitRepositoryPurposeResult")
    val request = purpose_inputs(scenario).head
    request.files.map(_.path).toSet shouldBe Set(_document_path, _test_path, "value.txt")
    val groups = Vector(P.Group("first-document", "Complete independent documentation", "docs: complete independent budget guide",
      Vector.empty, Vector(P.Content(_document_path, Some("Completed independent document\n"))), Vector.empty, "Independent non-consumed document"),
      P.Group("second-product", "Retain the intended product expectation", "fix: retain intended product input and test",
        Vector("first-document"), Vector(P.Content(_test_path, Some(_test("expected\n"))),
          P.Content("value.txt", Some("wrong-initial\n"))), Vector.empty, "Actual input and its complete intended specification"))
    successful(submit_purpose(scenario, session, P.Result("resolved", "Fixture-authored ordered purposes", groups, Vector.empty)))
      .business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
    When("the first independent Purpose receives actual runtime review and commits before the second Purpose fails Test")
    val firstreview = successful(advance(scenario, session))
    firstreview.business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitNativeMergeReview")
    _review(scenario, session, firstreview, "first")
    successful(advance(scenario, session))
    _assert_failure(scenario, session)
    val firstcommit = git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD")
    git(scenario.root, "-C", repository.toString, "show", "HEAD:" + _document_path) shouldBe "Completed independent document"
    git(scenario.root, "-C", repository.toString, "show", "HEAD:value.txt") shouldBe "initial"
    git(scenario.root, "-C", repository.toString, "show", "HEAD:" + _test_path) shouldBe _test("initial\n").trim
    _retain_inprogress(scenario, session, firstcommit, configuration, "first-failed")
    _finish_before(scenario, session, firstcommit, configuration)
  }

  private def _recollect_before(): Unit = {
    Given("the completed retained before checkpoint whose actual workflow effects were already executed")
    val saved = parse(Files.readString(_episode)).toOption.get
    def field[A: io.circe.Decoder](name: String): A = saved.hcursor.get[A](name).toOption.get
    val scenario = _reopen(saved)
    val session = field[String]("session")
    if (current(scenario, session).lifecycle == Lifecycle.Completed) {
      _recollect_completed(scenario, saved, after = false)
      return
    }
    When("the fixture reopens that exact physical repository host and Session using read-only observations")
    _record(scenario, session) shouldBe field[String]("record")
    _effects(scenario, session) shouldBe field[Json]("effects")
    Files.readString(scenario.root.resolve(".textus/config.json")) shouldBe field[String]("configuration")
    parse(field[String]("configuration")).toOption.get.hcursor.downField("sm-workflow.runtime.definition").focus shouldBe None
    ThreeWorkflowRuntimeParameters.encode(scenario.host) shouldBe field[Json]("host")
    scenario.plan.asJson shouldBe field[Json]("plan")
    field[Int]("originalLimit") shouldBe _original_limit
    _used(scenario, session) shouldBe field[Vector[String]]("usedIdentities")
    _used(scenario, session).size shouldBe _original_limit
    formalReceipts(scenario, session) shouldBe field[Vector[(String, C.Receipt)]]("formal")
    _native(scenario, session) shouldBe field[Vector[(String, Json)]]("nativeReceipts")
    P.repairProgressJson(_failed(scenario, session)) shouldBe field[Json]("failure")
    _assert_failure(scenario, session)
    _failed(scenario, session).commits shouldBe Vector(field[String]("firstCommit"))
    git(scenario.root, "-C", _repository(scenario).toString, "rev-parse", "HEAD") shouldBe field[String]("firstCommit")
    current(scenario, session).suspension shouldBe None
    current(scenario, session).lifecycle shouldBe Lifecycle.Active
    val accepted = field[Vector[String]]("acceptedSubmissions")
    accepted.size shouldBe _original_limit
    accepted.foreach { raw =>
      val path = Path.of(raw)
      require(path.isAbsolute && Files.isRegularFile(path), "Preserve each actual accepted repair stdout")
      parse(Files.readString(path)).toOption.get.findAllByKey("accepted") should contain(Json.True)
    }
    field[Vector[String]]("unknownRows").foreach(raw => stored(scenario) should contain(raw))
    Files.isRegularFile(Path.of(field[String]("waitStdout"))) shouldBe true
    Then("the literal old capture is recollected without replaying any workflow operation or changing its checkpoint")
    _record(scenario, session) shouldBe field[String]("record")
    _effects(scenario, session) shouldBe field[Json]("effects")
  }

  private def _assert_configuration(scenario: F.Scenario, original: String): Unit = {
    val key = "textus.import.data.file"
    val initial = parse(original).toOption.get
    val actual = parse(Files.readString(scenario.root.resolve(".textus/config.json"))).toOption.get
    require(initial.isObject && actual.isObject, "Both retained configurations must be objects")
    actual.mapObject(_.remove(key)) shouldBe initial.mapObject(_.remove(key))
    Vector(initial, actual).foreach { configuration =>
      val path = Path.of(configuration.hcursor.get[String](key).toOption.get)
      require(path.isAbsolute && path.normalize == path && Files.isRegularFile(path), "Retain the actual prepared import file")
      val payload = parse(Files.readString(path)).toOption.get
      payload.hcursor.get[Vector[Json]]("datastore").toOption.get should not be empty
      val evidence = payload.findAllByKey(NativeExternalEvidence.storageField)
      evidence should not be empty
      evidence.foreach(value => take(NativeExternalEvidence.decodeStorageC(value.asString.get)))
    }
  }

  private def _recollect_completed(scenario: F.Scenario, saved: Json, after: Boolean): Unit = {
    Given("the same Session already completed by its actual finite grant, fourth repair, fresh checks and second commit")
    def field[A: io.circe.Decoder](name: String): A = saved.hcursor.get[A](name).toOption.get
    val session = field[String]("session")
    val repository = _repository(scenario)
    When("the fixture observes the completed record, original history prefix and retained native receipts")
    val completed = current(scenario, session)
    val catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(RepositorySyncDefinitionBinding.bindC))))
    val originalrecord = take(WorkflowInstanceRecordJsonV1.decodeC(field[String]("record"), catalog))
    completed.lifecycle shouldBe Lifecycle.Completed
    completed.identity shouldBe originalrecord.identity
    completed.suspension shouldBe None
    completed.history.take(originalrecord.history.size) shouldBe originalrecord.history
    ThreeWorkflowRuntimeParameters.encode(scenario.host) shouldBe field[Json]("host")
    scenario.plan.asJson shouldBe field[Json]("plan")
    _assert_configuration(scenario, field[String]("configuration"))
    field[Int]("originalLimit") shouldBe _original_limit
    val used = _used(scenario, session)
    used.take(_original_limit) shouldBe field[Vector[String]]("usedIdentities")
    used.size shouldBe _original_limit + 1
    val original = field[Vector[(String, C.Receipt)]]("formal")
    original.foreach(value => formalReceipts(scenario, session) should contain(value))
    field[Vector[(String, Json)]]("nativeReceipts").foreach(value => _native(scenario, session) should contain(value))
    field[Vector[String]]("unknownRows").foreach(raw => stored(scenario) should contain(raw))
    P.repairProgressJson(_failed(scenario, session)) shouldBe field[Json]("failure")
    val firstcommit = field[String]("firstCommit")
    git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD^") shouldBe firstcommit
    val head = git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD")
    head should not be firstcommit
    git(scenario.root, "-C", repository.toString, "status", "--porcelain") shouldBe ""
    Files.readString(repository.resolve("value.txt")) shouldBe "expected\n"
    Files.readString(repository.resolve(_test_path)) shouldBe _test("expected\n")
    Files.readString(repository.resolve(_document_path)) shouldBe "Completed independent document\n"
    val purpose = receipts(scenario, session).filter(_.payload.value.purposes.nonEmpty)
      .maxBy(_.next.revision.value).payload.value.purposes.get
    purpose.cursor shouldBe 2
    purpose.commits shouldBe Vector(firstcommit, head)
    val fresh = formalReceipts(scenario, session).filterNot(original.contains)
    fresh.size shouldBe _checks.size
    fresh.map(_._2.identity).toSet shouldBe _checks.map(_.identity).toSet
    fresh.foreach { (_, receipt) =>
      receipt.termination shouldBe "exited"
      receipt.exitCode shouldBe Some(0)
      receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
    }
    val focused = fresh.find(_._2.identity == appendedCheck.identity).get._2.sbt.get
    focused.kind shouldBe WorkflowSbtValidation.Kind.TestOnly
    focused.selection.get.testClasses shouldBe Vector(selectedClass)
    focused.selection.get.testsRun should be > 0L
    _invocations(scenario).size shouldBe field[Json]("effects").hcursor.get[Vector[String]]("invocations").toOption.get.size + _checks.size
    val grants = receipts(scenario, session).filter(_.payload.value.stage == "RepairBudgetContinuationAdmitted")
    grants.size shouldBe 1
    val grant = grants.head
    val direction = grant.payload.value.repairBudget.get
    val target = direction.target
    target.sessionId shouldBe session
    target.root shouldBe repository.toString
    target.endpoint shouldBe scenario.host.target.endpoint
    target.context.repositoryIdentity shouldBe "continuation-fixture"
    target.context.localBranch shouldBe scenario.host.target.localBranch
    target.context.trackingRemote shouldBe scenario.host.target.remote
    target.context.trackingBranch shouldBe scenario.host.target.remoteBranch
    target.context.remoteEndpointIdentity shouldBe scenario.host.target.endpoint
    target.context.syncMode shouldBe "FULL_STATE"
    target.frontierRevision shouldBe originalrecord.revision.value.toString
    target.originalLimit shouldBe _original_limit
    target.effectiveLimit shouldBe _original_limit
    target.usedRepairs shouldBe _original_limit
    target.usedIdentities shouldBe field[Vector[String]]("usedIdentities")
    direction.choice shouldBe "ContinueRepairs"
    direction.additionalRepairs shouldBe 1
    take(B.effectiveLimitC(target, direction.additionalRepairs)) shouldBe _original_limit + 1
    grant.next.revision shouldBe originalrecord.revision.next
    grant.next.history.take(originalrecord.history.size) shouldBe originalrecord.history
    val accepted = field[Vector[String]]("acceptedSubmissions")
    accepted.size shouldBe _original_limit
    accepted.foreach { raw =>
      val path = Path.of(raw)
      require(path.isAbsolute && Files.isRegularFile(path), "Retain every original accepted submission")
      parse(Files.readString(path)).toOption.get.findAllByKey("accepted") should contain(Json.True)
    }
    val adoptionfile = scenario.root.resolve("budget-adoption.json")
    require(Files.isRegularFile(adoptionfile), "Recollect only the actual previously adopted input")
    val input = take(summon[org.goldenport.convert.ValueReader[V.RepositorySyncRepairBudgetContinuationAdoptionInput]]
      .readC(take(new org.goldenport.record.io.RecordDecoder().json(Files.readString(adoptionfile)))))
    input.sessionId shouldBe session
    take(B.targetC(input.target)) shouldBe target
    input.choice shouldBe direction.choice
    input.additionalRepairs shouldBe direction.additionalRepairs
    input.rationale shouldBe direction.rationale
    input.directionReference.identity shouldBe direction.reference.identity
    input.directionReference.revision shouldBe direction.reference.revision
    input.executionEvidence.workerIdentity shouldBe Some(direction.participant)
    val finalrecord = _record(scenario, session)
    val finaleffects = _effects(scenario, session)
    if (after) {
      When("the original adopted input is recollected and separate Advance recollects the actual retained Terminal")
      val terminalpath = Path.of(sys.props.getOrElse("smworkflow.spec.repairBudgetTerminalStdout",
        fail("Select -Dsmworkflow.spec.repairBudgetTerminalStdout=<actual retained Terminal stdout JSON>")))
      require(terminalpath.isAbsolute && terminalpath.normalize == terminalpath && terminalpath.startsWith(scenario.root.resolve("logs")) &&
        Files.isRegularFile(terminalpath), "Select only the actual same-fixture terminal stdout")
      val terminal = take(summon[org.goldenport.convert.ValueReader[V.RepositorySyncAdvanceResult]]
        .readC(take(new org.goldenport.record.io.RecordDecoder().json(Files.readString(terminalpath)))))
      terminal.sessionId shouldBe session
      terminal.kind shouldBe "Terminal"
      terminal.convergence.get.tip shouldBe head
      val repeated = successful(scenario.repositoryCall(B.adoptionOperation, Vector("--inputResource", adoptionfile.toUri.toASCIIString)))
        .business[V.RepositorySyncRepairBudgetContinuationAdoptionResult]
      repeated.outcome shouldBe "already-adopted"
      (repeated.originalLimit, repeated.effectiveLimit, repeated.usedRepairs) shouldBe ((_original_limit, _original_limit + 1, _original_limit + 1))
      _record(scenario, session) shouldBe finalrecord
      _effects(scenario, session) shouldBe finaleffects
      successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult] shouldBe terminal
      _record(scenario, session) shouldBe finalrecord
      _effects(scenario, session) shouldBe finaleffects
      Then("the retained successful product effects are qualified without rerunning them")
      val result = Json.obj(
        "root" -> scenario.root.toString.asJson, "session" -> session.asJson, "originalCheckpoint" -> saved,
        "target" -> parse(take(B.targetRecord(target).toJsonStringC)).toOption.get,
        "nativeGrant" -> grant.payload.value.asJson, "grantReference" -> grant.reference.asJson,
        "originalLimit" -> target.originalLimit.asJson, "effectiveLimit" -> take(B.effectiveLimitC(target, direction.additionalRepairs)).asJson,
        "usedAtGrant" -> target.usedRepairs.asJson, "usedIdentities" -> used.asJson,
        "freshReceipts" -> fresh.asJson, "purposeCommits" -> purpose.commits.asJson,
        "terminalStdout" -> terminalpath.toString.asJson, "terminal" -> parse(json(terminal)).toOption.get,
        "recollectedAdoption" -> parse(json(repeated)).toOption.get, "finalRecord" -> finalrecord.asJson,
        "finalEffects" -> finaleffects, "installedCommand" -> installed().toString.asJson)
      val resultpath = _episode.resolveSibling(_episode.getFileName.toString + ".result.json")
      if (Files.exists(resultpath)) parse(Files.readString(resultpath)).toOption.get shouldBe result
      else Files.writeString(resultpath, result.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
    } else {
      Then("the completed native receipts and original checkpoint are confirmed using only read-only observations")
      _record(scenario, session) shouldBe finalrecord
      _effects(scenario, session) shouldBe finaleffects
    }
  }

  private def _inprogress: Path = _episode.resolveSibling(_episode.getFileName.toString + ".inprogress.json")
  private def _reopen(saved: Json): F.Scenario = {
    val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
    val logs = Files.list(root.resolve("logs"))
    val ordinal = try math.max(1000, logs.filter(path => path.getFileName.toString.matches("[0-9]+\\.(stdout\\.json|stderr)"))
      .mapToInt(path => path.getFileName.toString.takeWhile(_.isDigit).toInt).max().orElse(999) + 1) finally logs.close()
    F.reopenInstalledRepository(root, take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(saved.hcursor.get[Json]("host").toOption.get)),
      saved.hcursor.get[GoalPhaseOrderedProgression.Plan]("plan").toOption.get, ordinal)
  }
  private def _retain_inprogress(scenario: F.Scenario, session: String, firstcommit: String,
    configuration: String, stage: String): Unit = {
    val checkpoint = Json.obj("root" -> scenario.root.toString.asJson, "session" -> session.asJson,
      "host" -> ThreeWorkflowRuntimeParameters.encode(scenario.host), "plan" -> scenario.plan.asJson,
      "firstCommit" -> firstcommit.asJson, "configuration" -> configuration.asJson,
      "record" -> _record(scenario, session).asJson, "stage" -> stage.asJson,
      "pendingIdentity" -> current(scenario, session).suspension.map(_.continuationIdentity.value).asJson)
    Files.writeString(_inprogress, checkpoint.noSpaces, UTF_8)
  }
  private def _resume_before(): Unit = {
    Given("the exact saved first-failure episode whose first Purpose is already committed")
    val saved = parse(Files.readString(_inprogress)).toOption.get
    def field[A: io.circe.Decoder](name: String): A = saved.hcursor.get[A](name).toOption.get
    val scenario = _reopen(saved)
    val session = field[String]("session")
    val configuration = field[String]("configuration")
    val firstcommit = field[String]("firstCommit")
    val stage = field[String]("stage")
    require(Set("first-failed", "pending-first-repair", "unfinished-second-repair")(stage),
      "Resume only the exact retained first failure, pending first repair or unfinished second preparation")
    _record(scenario, session) shouldBe field[String]("record")
    Files.readString(scenario.root.resolve(".textus/config.json")) shouldBe configuration
    git(scenario.root, "-C", _repository(scenario).toString, "rev-parse", "HEAD") shouldBe firstcommit
    if (stage == "unfinished-second-repair") {
      _resume_preparation(scenario, session, firstcommit, saved)
      return
    }
    _assert_failure(scenario, session)
    _failed(scenario, session).commits shouldBe Vector(firstcommit)
    _failed(scenario, session).attempt shouldBe 0
    _invocations(scenario).size shouldBe originalChecks.size
    if (stage == "pending-first-repair") {
      _used(scenario, session).size shouldBe 1
      current(scenario, session).suspension.get.continuationIdentity.value shouldBe field[String]("pendingIdentity")
    } else {
      _used(scenario, session) shouldBe Vector.empty
      current(scenario, session).suspension shouldBe None
    }
    _finish_before(scenario, session, firstcommit, configuration)
  }
  private def _resume_preparation(scenario: F.Scenario, session: String, firstcommit: String, saved: Json): Unit = {
    Given("the literal failed second preparation after two accepted repairs and before any new preparation direction")
    val old = current(scenario, session)
    old.currentProgression shouldBe Some(ProgressionReference("sync.PurposeUnfinished"))
    old.suspension shouldBe None
    saved.hcursor.downField("pendingIdentity").focus shouldBe Some(Json.Null)
    _used(scenario, session).size shouldBe 2
    val native = _native(scenario, session)
    val formal = formalReceipts(scenario, session)
    val effects = _effects(scenario, session)
    val oldwire = take(WorkflowInstanceRecordJsonV1.encodeC(old))
    val failed = receipts(scenario, session).find(receipt =>
      take(WorkflowInstanceRecordJsonV1.encodeC(receipt.next)) == oldwire).get
    failed.payload.value.stage shouldBe "PurposeUnfinished"
    failed.reference.identity should endWith("-prepare-result")
    val progress = failed.payload.value.purposes.get
    (progress.cursor, progress.attempt, progress.slotCursor) shouldBe ((1, 2, 3))
    progress.commits shouldBe Vector(firstcommit)
    progress.plan.get.groups(1).content should contain(P.Content("value.txt", Some("wrong-2\n")))
    progress.plan.get.groups(1).content should contain(P.Content(_test_path, Some(_test("expected\n"))))
    val effect = progress.effects.last
    effect.hcursor.get[String]("outcome").toOption shouldBe Some("Failed(1)")
    val intent = receipts(scenario, session).filter(receipt => receipt.payload.value.stage == "PurposePreparing" &&
      receipt.next.revision.value < old.revision.value).maxBy(_.next.revision.value)
    intent.payload.value.purposes.get.attempt shouldBe 2
    _assert_failure(scenario, session)
    formal.count(_._2.identity == originalChecks(1).identity) shouldBe 2
    _invocations(scenario).size shouldBe originalChecks.size * 2
    val accepted = saved.hcursor.get[Vector[String]]("acceptedSubmissions").toOption.get
    accepted.size shouldBe 2
    accepted.foreach { raw =>
      val path = Path.of(raw)
      require(path.isAbsolute && Files.isRegularFile(path), "Retain the actual earlier accepted submission stdout")
      parse(Files.readString(path)).toOption.get.findAllByKey("accepted") should contain(Json.True)
    }
    val failurecheckpoint = Json.obj("retainedInProgress" -> saved, "record" -> _record(scenario, session).asJson,
      "effects" -> effects, "formal" -> formal.asJson, "nativeReceipts" -> native.asJson,
      "failedProgress" -> P.repairProgressJson(progress), "failedEffect" -> effect,
      "failedResultReference" -> failed.reference.asJson,
      "failedResultRecord" -> take(WorkflowInstanceRecordJsonV1.encodeC(failed.next)).asJson,
      "failedIntentReference" -> intent.reference.asJson,
      "failedIntentRecord" -> take(WorkflowInstanceRecordJsonV1.encodeC(intent.next)).asJson,
      "acceptedRepairResubmitted" -> false.asJson, "realConsumerOperations" -> false.asJson)
    Files.writeString(_episode.resolveSibling(_episode.getFileName.toString + ".preparation-failure.json"),
      failurecheckpoint.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
    When("the existing public preparation continuation describes and adopts fixture-authored authorization")
    val described = successful(scenario.repositoryCall(A.describeOperation, Vector("--sessionId", session)))
    val description = described.business[V.RepositorySyncPurposePreparationContinuationDescriptionResult]
    val target = take(A.targetC(description.target))
    description.outcome shouldBe "adoption-required"
    target.eligibilityReason shouldBe ""
    A.progress(target).toOption.get shouldBe progress
    A.effect(target).toOption.get shouldBe effect
    current(scenario, session) shouldBe old
    _effects(scenario, session) shouldBe effects
    val directory = Files.createTempDirectory(scenario.root, "fixture-budget-preparation-direction-")
    val reference = V.WorkflowArtifactReference(directory.getFileName.toString, "1")
    val answer = V.RepositorySyncPurposePreparationContinuationAnswer("AuthorizeNewPreparation",
      "Fixture-authored authorization for the exact failed second preparation; preserve both accepted repairs and all old effects; no real Human consent is claimed")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(target.context.participantIdentity), None, None)
    val preparation = V.WorkflowEvidencePreparationInput("RepositorySync", "purpose-preparation-continuation",
      described.invocation.stdout.toUri.toASCIIString, save(directory, "answer.json", json(answer)),
      save(directory, "reference.json", json(reference)), save(directory, "execution.json", json(execution)),
      "normal-development-host", java.time.Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some(target.context.participantIdentity))
    val prepared = successful(scenario.prepareHostInput("prepareEvidence",
      take(new org.goldenport.record.io.RecordDecoder().json(json(preparation)))))
    scenario.selectPreparedImport(prepared)
    current(scenario, session) shouldBe old
    _effects(scenario, session) shouldBe effects
    val input = V.RepositorySyncPurposePreparationContinuationAdoptionInput(session, description.target,
      answer.choice, answer.rationale, reference, execution)
    val adoption = successful(scenario.repositoryCall(A.adoptionOperation, Vector("--input", json(input))))
    adoption.business[V.RepositorySyncPurposePreparationContinuationAdoptionResult].accepted shouldBe true
    adoption.business[V.RepositorySyncPurposePreparationContinuationAdoptionResult].outcome shouldBe "adopted"
    _effects(scenario, session) shouldBe effects
    _used(scenario, session).size shouldBe 2
    When("separate ordinary Advance executes only the authorized new preparation and its actual checks")
    successful(advance(scenario, session))
    _assert_failure(scenario, session)
    _failed(scenario, session).attempt shouldBe 2
    _failed(scenario, session).receipts.find(_.identity == originalChecks(1).identity).get.sbt.get.outcome shouldBe
      WorkflowSbtValidation.Outcome.Failed
    formalReceipts(scenario, session).filterNot(formal.contains).map(_._2.identity).toSet shouldBe originalChecks.map(_.identity).toSet
    _invocations(scenario).size shouldBe originalChecks.size * 3
    native.foreach(value => _native(scenario, session) should contain(value))
    current(scenario, session).history.take(old.history.size) shouldBe old.history
    git(scenario.root, "-C", _repository(scenario).toString, "rev-parse", "HEAD") shouldBe firstcommit
    val recovery = Json.obj("prepublicationFailure" -> failurecheckpoint,
      "publicTarget" -> parse(take(description.target.toJsonStringC)).toOption.get,
      "descriptionStdout" -> described.invocation.stdout.toString.asJson,
      "preparedEvidenceStdout" -> prepared.invocation.stdout.toString.asJson,
      "adoptionStdout" -> adoption.invocation.stdout.toString.asJson,
      "acceptedRepairResubmitted" -> false.asJson, "successfulPrefixReexecuted" -> false.asJson)
    _finish_before(scenario, session, firstcommit, Files.readString(scenario.root.resolve(".textus/config.json")), accepted, Some(recovery))
  }
  private def _finish_before(scenario: F.Scenario, session: String, firstcommit: String, configuration: String,
    acceptedPrefix: Vector[String] = Vector.empty, preparationRecovery: Option[Json] = None): Unit = {
    val repository = _repository(scenario)
    val retained = _native(scenario, session)
    val acceptedhistory = current(scenario, session).history
    val accepted = acceptedPrefix ++ ((acceptedPrefix.size + 1) to _original_limit).toVector.map { ordinal =>
      When("ordinary Advance issues repair " + ordinal + " and its accepted proposal still fails the unchanged actual Test")
      val recollecting = ordinal == 1 && current(scenario, session).suspension.nonEmpty
      val before = _effects(scenario, session)
      val record = _record(scenario, session)
      val issue = successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
      issue.submitOperation shouldBe Some("submitNativeMergeRepair")
      issue.repair.get.files.map(_.path).toSet shouldBe Set("value.txt", _test_path)
      _used(scenario, session).size shouldBe ordinal
      if (recollecting) {
        _record(scenario, session) shouldBe record
        _effects(scenario, session) shouldBe before
      }
      if (ordinal == 1) _retain_inprogress(scenario, session, firstcommit, configuration, "pending-first-repair")
      val submitted = _submit(scenario, session, issue.repair.get.files, "wrong-" + ordinal + "\n", ordinal)
      successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult].condition shouldBe Some("PurposeGrouped")
      successful(advance(scenario, session))
      _assert_failure(scenario, session)
      submitted.invocation.stdout.toString
    }
    When("ordinary Advance reaches the genuine existing native repair limit")
    val waiting = successful(advance(scenario, session))
    waiting.business[V.RepositorySyncAdvanceResult].kind shouldBe "Wait"
    waiting.business[V.RepositorySyncAdvanceResult].condition shouldBe Some("native-repair-limit")
    current(scenario, session).suspension shouldBe None
    _used(scenario, session).size shouldBe _original_limit
    _failed(scenario, session).commits shouldBe Vector(firstcommit)
    git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe firstcommit
    retained.foreach(value => _native(scenario, session) should contain(value))
    current(scenario, session).history.take(acceptedhistory.size) shouldBe acceptedhistory
    val effects = _effects(scenario, session)
    val record = _record(scenario, session)
    successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult] shouldBe waiting.business[V.RepositorySyncAdvanceResult]
    _effects(scenario, session) shouldBe effects
    _record(scenario, session) shouldBe record
    Then("the same Session and literal failure receipts are retained without any new continuation API call")
    val unknown = stored(scenario).filter(raw => raw.contains("UNKNOWN"))
    val checkpoint = Json.obj("root" -> scenario.root.toString.asJson, "session" -> session.asJson,
      "host" -> ThreeWorkflowRuntimeParameters.encode(scenario.host), "plan" -> scenario.plan.asJson,
      "configuration" -> Files.readString(scenario.root.resolve(".textus/config.json")).asJson,
      "record" -> record.asJson, "effects" -> effects,
      "nativeReceipts" -> _native(scenario, session).asJson, "formal" -> formalReceipts(scenario, session).asJson,
      "failure" -> P.repairProgressJson(_failed(scenario, session)), "usedIdentities" -> _used(scenario, session).asJson,
      "firstCommit" -> firstcommit.asJson, "acceptedSubmissions" -> accepted.asJson, "unknownRows" -> unknown.asJson,
      "waitStdout" -> waiting.invocation.stdout.toString.asJson, "waitResult" -> parse(json(waiting.business[V.RepositorySyncAdvanceResult])).toOption.get,
      "originalLimit" -> _original_limit.asJson, "installedCommand" -> installed().toString.asJson,
      "preparationRecovery" -> preparationRecovery.asJson)
    Files.writeString(_episode, checkpoint.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
  }

  private def _direction(scenario: F.Scenario, session: String, described: F.Result,
    choice: String, additional: Int): V.RepositorySyncRepairBudgetContinuationAdoptionInput = {
    val description = successful(described).business[V.RepositorySyncRepairBudgetContinuationDescriptionResult]
    val target = take(B.targetC(description.target))
    val participant = target.context.participantIdentity
    val directory = Files.createTempDirectory(scenario.root, "fixture-budget-direction-")
    val reference = V.WorkflowArtifactReference(directory.getFileName.toString, "1")
    val answer = V.RepositorySyncRepairBudgetContinuationAnswer(choice, additional,
      "Fixture-authored independent finite allowance for this actual failed target; no real Human approval is claimed")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(participant), None, None)
    val preparation = V.WorkflowEvidencePreparationInput("RepositorySync", "repair-budget-continuation",
      described.invocation.stdout.toUri.toASCIIString, save(directory, "answer.json", json(answer)),
      save(directory, "reference.json", json(reference)), save(directory, "execution.json", json(execution)),
      "normal-development-host", java.time.Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some(participant))
    val prepared = successful(scenario.prepareHostInput("prepareEvidence",
      take(new org.goldenport.record.io.RecordDecoder().json(json(preparation)))))
    scenario.selectPreparedImport(prepared)
    V.RepositorySyncRepairBudgetContinuationAdoptionInput(session, description.target, choice, additional,
      answer.rationale, reference, execution)
  }
  private def _adopt(scenario: F.Scenario, input: V.RepositorySyncRepairBudgetContinuationAdoptionInput): F.Result =
    scenario.repositoryCall(B.adoptionOperation, Vector("--input", json(input)))

  private def _after(): Unit = {
    Given("the updated installed runtime and the exact saved physical fixture, host and original Session")
    installed()
    val saved = parse(Files.readString(_episode)).toOption.get
    def field[A: io.circe.Decoder](name: String): A = saved.hcursor.get[A](name).toOption.get
    val root = Path.of(field[String]("root"))
    val scenario = _reopen(saved)
    val session = field[String]("session")
    val repository = _repository(scenario)
    val old = current(scenario, session)
    if (old.lifecycle == Lifecycle.Completed) {
      _recollect_completed(scenario, saved, after = true)
      return
    }
    _record(scenario, session) shouldBe field[String]("record")
    _effects(scenario, session) shouldBe field[Json]("effects")
    _assert_configuration(scenario, field[String]("configuration"))
    _used(scenario, session) shouldBe field[Vector[String]]("usedIdentities")
    val original = field[Vector[(String, C.Receipt)]]("formal")
    val oldnative = field[Vector[(String, Json)]]("nativeReceipts")
    val firstcommit = field[String]("firstCommit")
    val accepted = field[Vector[String]]("acceptedSubmissions")
    accepted.size shouldBe _original_limit
    accepted.foreach(path => Files.isRegularFile(Path.of(path)) shouldBe true)
    val failure = field[Json]("failure")
    P.repairProgressJson(_failed(scenario, session)) shouldBe failure
    val passive = _effects(scenario, session)
    When("public description exposes the typed target and independent fixture evidence prepares Defer")
    val described = successful(scenario.repositoryCall(B.describeOperation, Vector("--sessionId", session)))
    val description = described.business[V.RepositorySyncRepairBudgetContinuationDescriptionResult]
    val target = take(B.targetC(description.target))
    target.originalLimit shouldBe _original_limit
    target.effectiveLimit shouldBe _original_limit
    target.usedRepairs shouldBe _original_limit
    target.usedIdentities shouldBe _used(scenario, session)
    val deferred = _direction(scenario, session, described, "Defer", 0)
    successful(_adopt(scenario, deferred)).business[V.RepositorySyncRepairBudgetContinuationAdoptionResult].accepted shouldBe false
    current(scenario, session) shouldBe old
    _effects(scenario, session) shouldBe passive
    When("crossed stale contradictory and duplicate-field input is refused before the exact finite allowance is adopted")
    val input = _direction(scenario, session, described, "ContinueRepairs", 1)
    def refused(raw: String): Unit = {
      scenario.repositoryCall(B.adoptionOperation, Vector("--input", raw)).exitCode should not be 0
      current(scenario, session) shouldBe old
      _effects(scenario, session) shouldBe passive
      _native(scenario, session) shouldBe oldnative
    }
    refused(json(input.copy(sessionId = session + "-crossed")))
    refused(json(input.copy(additionalRepairs = 2)))
    val wire = parse(json(input)).toOption.get
    refused(wire.mapObject(fields => fields.add("target", fields("target").get.mapObject(
      _.add("frontierRevision", "0".asJson)))).noSpaces)
    refused(wire.mapObject(_.add("target", Json.Null)).noSpaces)
    refused(json(input).dropRight(1) + ",\"sessionId\":\"duplicate\"}")
    val uri = save(root, "budget-adoption.json", json(input))
    val adoption = successful(scenario.repositoryCall(B.adoptionOperation, Vector("--inputResource", uri)))
      .business[V.RepositorySyncRepairBudgetContinuationAdoptionResult]
    adoption.accepted shouldBe true
    adoption.outcome shouldBe "adopted"
    (adoption.originalLimit, adoption.effectiveLimit, adoption.usedRepairs) shouldBe ((_original_limit, _original_limit + 1, _original_limit))
    val admitted = current(scenario, session)
    admitted.revision shouldBe old.revision.next
    admitted.currentProgression shouldBe Some(ProgressionReference("sync.RepairBudgetContinuationAdmitted"))
    admitted.history.take(old.history.size) shouldBe old.history
    _effects(scenario, session) shouldBe passive
    _used(scenario, session) shouldBe field[Vector[String]]("usedIdentities")
    successful(_adopt(scenario, input)).business[V.RepositorySyncRepairBudgetContinuationAdoptionResult].outcome shouldBe "already-adopted"
    current(scenario, session) shouldBe admitted
    When("a separate ordinary Advance issues exactly one new repair and the actual product input is corrected")
    val issued = successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
    issued.submitOperation shouldBe Some("submitNativeMergeRepair")
    _used(scenario, session).size shouldBe _original_limit + 1
    val issuedrecord = current(scenario, session)
    successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult] shouldBe issued
    current(scenario, session) shouldBe issuedrecord
    _effects(scenario, session) shouldBe passive
    successful(_adopt(scenario, input)).business[V.RepositorySyncRepairBudgetContinuationAdoptionResult].outcome shouldBe "already-adopted"
    current(scenario, session) shouldBe issuedrecord
    _submit(scenario, session, issued.repair.get.files, "expected\n", _original_limit + 1)
    _effects(scenario, session) shouldBe passive
    successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult].condition shouldBe Some("PurposeGrouped")
    val reviewed = successful(advance(scenario, session))
    reviewed.business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitNativeMergeReview")
    val fresh = formalReceipts(scenario, session).filterNot(original.contains)
    fresh.map(_._2.identity).toSet shouldBe _checks.map(_.identity).toSet
    fresh.size shouldBe _checks.size
    fresh.foreach(_._2.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded)
    val focused = fresh.find(_._2.identity == appendedCheck.identity).get._2.sbt.get
    focused.kind shouldBe WorkflowSbtValidation.Kind.TestOnly
    focused.selection.get.testClasses shouldBe Vector(selectedClass)
    focused.selection.get.testsRun should be > 0L
    _invocations(scenario).size shouldBe passive.hcursor.get[Vector[String]]("invocations").toOption.get.size + _checks.size
    git(root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe firstcommit
    val reviewstate = _effects(scenario, session)
    successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult].issuedWorkContext shouldBe
      reviewed.business[V.RepositorySyncAdvanceResult].issuedWorkContext
    _effects(scenario, session) shouldBe reviewstate
    _review(scenario, session, reviewed, "second")
    val terminal = successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
    Then("both actual Purpose commits complete the same clean Session while every old failure and accepted repair remains history")
    terminal.kind shouldBe "Terminal"
    current(scenario, session).lifecycle shouldBe Lifecycle.Completed
    git(root, "-C", repository.toString, "status", "--porcelain") shouldBe ""
    git(root, "-C", repository.toString, "rev-parse", "HEAD^") shouldBe firstcommit
    val head = git(root, "-C", repository.toString, "rev-parse", "HEAD")
    head should not be firstcommit
    Files.readString(repository.resolve("value.txt")) shouldBe "expected\n"
    Files.readString(repository.resolve(_test_path)) shouldBe _test("expected\n")
    original.foreach(value => formalReceipts(scenario, session) should contain(value))
    oldnative.foreach(value => _native(scenario, session) should contain(value))
    _used(scenario, session).take(_original_limit) shouldBe field[Vector[String]]("usedIdentities")
    _used(scenario, session).size shouldBe _original_limit + 1
    current(scenario, session).history.take(old.history.size) shouldBe old.history
    P.repairProgressJson(_failed(scenario, session)) shouldBe failure
    field[Vector[String]]("unknownRows").foreach(raw => stored(scenario) should contain(raw))
    _assert_configuration(scenario, field[String]("configuration"))
    val finaleffects = _effects(scenario, session)
    val finalrecord = _record(scenario, session)
    successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult] shouldBe terminal
    _effects(scenario, session) shouldBe finaleffects
    _record(scenario, session) shouldBe finalrecord
    Files.writeString(_episode.resolveSibling(_episode.getFileName.toString + ".result.json"), Json.obj(
      "root" -> root.toString.asJson, "session" -> session.asJson, "target" -> parse(take(description.target.toJsonStringC)).toOption.get,
      "adoption" -> parse(json(adoption)).toOption.get, "freshReceipts" -> fresh.asJson,
      "firstCommit" -> firstcommit.asJson, "secondCommit" -> head.asJson, "usedIdentities" -> _used(scenario, session).asJson,
      "oldAcceptedSubmissions" -> accepted.asJson, "originalFailure" -> failure,
      "unknownRowsPreserved" -> field[Vector[String]]("unknownRows").size.asJson,
      "terminal" -> parse(json(terminal)).toOption.get, "finalRecord" -> finalrecord.asJson,
      "finalEffects" -> finaleffects, "installedCommand" -> installed().toString.asJson).noSpaces,
      UTF_8, StandardOpenOption.CREATE_NEW)
  }

  private def _scope_work_rows(scenario: F.Scenario, identity: String): Vector[String] =
    stored(scenario).filter(raw => WorkflowWorkOrderJsonV1.decodeC(raw, M.inputCodec).toOption.exists(_.current match {
      case order: WorkflowContinuation.WorkOrder[M.Request] => order.request.continuationId.value == identity
      case _ => false
    }))

  private def _scope_work(scenario: F.Scenario, identity: String): M.Request = {
    val rows = _scope_work_rows(scenario, identity)
    rows.size shouldBe 1
    take(WorkflowWorkOrderJsonV1.decodeC(rows.head, M.inputCodec)).current match {
      case order: WorkflowContinuation.WorkOrder[M.Request] => order.request.input.get.value
      case _ => fail("Retain the actual original native repair WorkOrder")
    }
  }

  private def _scope_invocation(result: F.Result): Json = Json.obj("exitCode" -> result.exitCode.asJson,
    "argv" -> result.invocation.argv.asJson, "pid" -> result.invocation.pid.asJson,
    "stdout" -> result.invocation.stdout.toString.asJson, "stderr" -> result.invocation.stderr.toString.asJson)

  private def _scope_direction(scenario: F.Scenario, session: String,
    described: F.Result): (V.RepositorySyncRepairScopeCorrectionAdoptionInput, F.Result) = {
    val description = successful(described).business[V.RepositorySyncRepairScopeCorrectionDescriptionResult]
    val target = take(N.targetC(description.target))
    val directory = Files.createTempDirectory(scenario.root, "fixture-budget-scope-lifecycle-")
    val reference = V.WorkflowArtifactReference(directory.getFileName.toString, "1")
    val answer = V.RepositorySyncRepairScopeCorrectionAnswer(N.lifecycleChoices.head, Vector.empty,
      "Independent fixture lifecycle direction: prior CLI returned and worker ended; accept retained UNKNOWN effects; no real Human approval is claimed",
      true, true)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(target.context.participantIdentity), None, None)
    val preparation = V.WorkflowEvidencePreparationInput("RepositorySyncRepairScopeCorrection", "adoption",
      described.invocation.stdout.toUri.toASCIIString, save(directory, "answer.json", json(answer)),
      save(directory, "reference.json", json(reference)), save(directory, "execution.json", json(execution)),
      "normal-development-host", java.time.Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some(target.context.participantIdentity))
    val prepared = successful(scenario.prepareHostInput("prepareEvidence",
      take(new org.goldenport.record.io.RecordDecoder().json(json(preparation)))))
    scenario.selectPreparedImport(prepared)
    (V.RepositorySyncRepairScopeCorrectionAdoptionInput(session, description.target, answer.choice, Vector.empty,
      answer.rationale, reference, true, true, execution), prepared)
  }

  private def _scope_snapshot(scenario: F.Scenario, session: String): Json = Json.obj(
    "record" -> _record(scenario, session).asJson, "effects" -> _effects(scenario, session),
    "nativeReceipts" -> _native(scenario, session).asJson, "formal" -> formalReceipts(scenario, session).asJson,
    "unknownRows" -> stored(scenario).filter(_.contains("UNKNOWN")).asJson,
    "configuration" -> Files.readString(scenario.root.resolve(".textus/config.json")).asJson,
    "usedIdentities" -> _used(scenario, session).asJson)

  private def _scope_assert_checkpoint(scenario: F.Scenario, saved: Json): Unit = {
    val session = saved.hcursor.get[String]("session").toOption.get
    _scope_snapshot(scenario, session) shouldBe saved.hcursor.get[Json]("postScope").toOption.get
    ThreeWorkflowRuntimeParameters.encode(scenario.host) shouldBe saved.hcursor.get[Json]("host").toOption.get
    scenario.plan.asJson shouldBe saved.hcursor.get[Json]("plan").toOption.get
    _scope_work_rows(scenario, saved.hcursor.get[String]("oldIdentity").toOption.get) shouldBe
      saved.hcursor.get[Vector[String]]("oldWorkRows").toOption.get
    val failed = saved.hcursor.get[Json]("oldAdvance").toOption.get
    failed.hcursor.get[Int]("exitCode").toOption.get should not be 0
    val logs = Vector("stdout", "stderr").map { name =>
      val path = Path.of(failed.hcursor.get[String](name).toOption.get)
      Files.isRegularFile(path) shouldBe true
      Files.readString(path)
    }.mkString("\n")
    logs should include("409")
    logs should include("repair budget stage has no native adoption reference")
    logs shouldBe saved.hcursor.get[String]("oldAdvanceLogs").toOption.get
  }

  private def _scope_before(): Unit = {
    installed()
    if (Files.exists(_scope_episode)) {
      Given("the exact already captured scope supersession and actual old installed 409")
      val saved = parse(Files.readString(_scope_episode)).toOption.get
      val scenario = _reopen(saved)
      When("the fixture recollects that same Session with read-only observations")
      if (current(scenario, saved.hcursor.get[String]("session").toOption.get).lifecycle == Lifecycle.Completed)
        _scope_recollect_completed(scenario, saved)
      else _scope_assert_checkpoint(scenario, saved)
      Then("the saved grant scope direction and actual failure remain untouched")
      return
    }
    Given("a fresh separate tiny scope episode with its tracked baseline helper and unchanged default repair limit")
    _before()
    val limited = parse(Files.readString(_episode)).toOption.get
    val scenario = _reopen(limited)
    val session = limited.hcursor.get[String]("session").toOption.get
    val repository = _repository(scenario)
    val beforegrant = _scope_snapshot(scenario, session)
    When("independent fixture evidence grants exactly one finite repair and ordinary Advance issues the fourth original assignment")
    val described = successful(scenario.repositoryCall(B.describeOperation, Vector("--sessionId", session)))
    val input = _direction(scenario, session, described, "ContinueRepairs", 1)
    val adoption = successful(_adopt(scenario, input))
    val grant = adoption.business[V.RepositorySyncRepairBudgetContinuationAdoptionResult]
    grant.accepted shouldBe true
    (grant.originalLimit, grant.effectiveLimit, grant.usedRepairs) shouldBe ((_original_limit, _original_limit + 1, _original_limit))
    _effects(scenario, session) shouldBe beforegrant.hcursor.get[Json]("effects").toOption.get
    val issued = successful(advance(scenario, session))
    val original = issued.business[V.RepositorySyncAdvanceResult]
    original.submitOperation shouldBe Some("submitNativeMergeRepair")
    original.repair.get.files.map(_.path).toSet shouldBe Set("value.txt", _test_path)
    val oldidentity = original.issuedWorkContext.get.continuationId
    val oldwork = _scope_work(scenario, oldidentity)
    val used = _used(scenario, session)
    used.size shouldBe _original_limit + 1
    N.budgetIdentity(oldidentity) shouldBe used.last
    val prescope = _scope_snapshot(scenario, session)
    When("native preparation qualifies the exact original assignment plus the necessary existing tracked baseline helper")
    val paths = (oldwork.files.map(_.path) :+ _baseline_path).distinct.sorted
    val preparedscope = successful(scenario.repositoryCall(N.prepareOperation, Vector("--input",
      N.Preparation(session, paths, "The intended selected specification also requires correcting the tracked baseline expectation helper").asJson.noSpaces)))
    val target = take(N.targetC(preparedscope.business[V.RepositorySyncRepairScopeCorrectionDescriptionResult].target))
    target.oldContinuationId shouldBe oldidentity
    target.oldAssignedPaths shouldBe oldwork.files.map(_.path)
    target.inventory.get.paths shouldBe paths
    target.purpose.get.progress shouldBe _failed(scenario, session)
    _scope_snapshot(scenario, session) shouldBe prescope
    val (direction, evidence) = _scope_direction(scenario, session, preparedscope)
    val scopeadoption = successful(scenario.repositoryCall(N.adoptionOperation, Vector("--input", json(direction))))
    scopeadoption.business[V.RepositorySyncRepairScopeCorrectionAdoptionResult].accepted shouldBe true
    scopeadoption.business[V.RepositorySyncRepairScopeCorrectionAdoptionResult].outcome shouldBe "adopted"
    _effects(scenario, session) shouldBe prescope.hcursor.get[Json]("effects").toOption.get
    _used(scenario, session) shouldBe used
    _scope_work(scenario, oldidentity) shouldBe oldwork
    val postscope = _scope_snapshot(scenario, session)
    postscope.hcursor.get[Vector[String]]("unknownRows").toOption.get should not be empty
    When("one ordinary Advance on the old installed image encounters the actual budget and scope composition defect")
    val failed = advance(scenario, session)
    failed.exitCode should not be 0
    val failurelogs = Vector(failed.invocation.stdout, failed.invocation.stderr).map(Files.readString(_)).mkString("\n")
    failurelogs should include("409")
    failurelogs should include("repair budget stage has no native adoption reference")
    Then("the same physical Session is checkpointed with the grant scope supersession original work UNKNOWN effects and literal old failure")
    _scope_snapshot(scenario, session) shouldBe postscope
    git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe limited.hcursor.get[String]("firstCommit").toOption.get
    val checkpoint = Json.obj("root" -> scenario.root.toString.asJson, "session" -> session.asJson,
      "host" -> ThreeWorkflowRuntimeParameters.encode(scenario.host), "plan" -> scenario.plan.asJson,
      "originalLimit" -> _original_limit.asJson, "effectiveLimit" -> (_original_limit + 1).asJson,
      "limitCheckpoint" -> limited, "beforeGrant" -> beforegrant, "preScope" -> prescope, "postScope" -> postscope,
      "firstCommit" -> limited.hcursor.get[Json]("firstCommit").toOption.get,
      "oldIdentity" -> oldidentity.asJson, "oldWork" -> M.inputCodec.encode(oldwork),
      "oldWorkRows" -> _scope_work_rows(scenario, oldidentity).asJson,
      "retainedScopeRows" -> stored(scenario).filter(_.contains(oldidentity)).asJson,
      "budgetInput" -> parse(json(input)).toOption.get, "budgetDescription" -> _scope_invocation(described),
      "budgetAdoption" -> _scope_invocation(adoption), "fourthIssue" -> _scope_invocation(issued),
      "scopeTarget" -> target.asJson, "scopeInput" -> parse(json(direction)).toOption.get,
      "scopePreparation" -> _scope_invocation(preparedscope), "scopeEvidence" -> _scope_invocation(evidence),
      "scopeAdoption" -> _scope_invocation(scopeadoption), "oldAdvance" -> _scope_invocation(failed),
      "oldAdvanceLogs" -> failurelogs.asJson, "installedCommand" -> installed().toString.asJson)
    Files.writeString(_scope_episode, checkpoint.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
  }

  private def _scope_result: Path = _scope_episode.resolveSibling(_scope_episode.getFileName.toString + ".result.json")

  private def _scope_recollect_completed(scenario: F.Scenario, saved: Json): Unit = {
    val result = parse(Files.readString(_scope_result)).toOption.get
    val session = saved.hcursor.get[String]("session").toOption.get
    Given("the exact scope episode already completed with retained fresh checks and its second Purpose commit")
    When("the fixture recollects only the retained result and current read-only native observations")
    result.hcursor.get[Json]("checkpoint").toOption.get shouldBe saved
    current(scenario, session).lifecycle shouldBe Lifecycle.Completed
    Then("the saved successful effects are unchanged without any workflow call or repeated evidence adoption")
    _scope_snapshot(scenario, session) shouldBe result.hcursor.get[Json]("finalState").toOption.get
    _scope_work_rows(scenario, saved.hcursor.get[String]("oldIdentity").toOption.get) shouldBe
      saved.hcursor.get[Vector[String]]("oldWorkRows").toOption.get
  }

  private def _scope_after(): Unit = {
    installed()
    Given("the new installed runtime and the exact old failed scope checkpoint on the same physical Session")
    val saved = parse(Files.readString(_scope_episode)).toOption.get
    def _field_[A: io.circe.Decoder](name: String): A = saved.hcursor.get[A](name).toOption.get
    val scenario = _reopen(saved)
    val session = _field_[String]("session")
    if (current(scenario, session).lifecycle == Lifecycle.Completed) {
      _scope_recollect_completed(scenario, saved)
      return
    }
    _scope_assert_checkpoint(scenario, saved)
    val repository = _repository(scenario)
    val old = current(scenario, session)
    val original = formalReceipts(scenario, session)
    val oldnative = _native(scenario, session)
    val passive = _effects(scenario, session)
    val used = _used(scenario, session)
    val firstcommit = _field_[String]("firstCommit")
    val oldidentity = _field_[String]("oldIdentity")
    val target = _field_[N.Target]("scopeTarget")
    used.size shouldBe _original_limit + 1
    val budgetgrants = receipts(scenario, session).filter(_.payload.value.stage == "RepairBudgetContinuationAdmitted")
    budgetgrants.size shouldBe 1
    val direction = budgetgrants.head.payload.value.repairBudget.get
    direction.target.originalLimit shouldBe _original_limit
    take(B.effectiveLimitC(direction.target, direction.additionalRepairs)) shouldBe _original_limit + 1
    When("separate ordinary Advance authenticates the existing scope supersession and issues its qualified replacement")
    val issuedcall = successful(advance(scenario, session))
    val issued = issuedcall.business[V.RepositorySyncAdvanceResult]
    issued.submitOperation shouldBe Some("submitNativeMergeRepair")
    val identity = issued.issuedWorkContext.get.continuationId
    identity should startWith(oldidentity + ":scope:")
    N.budgetIdentity(identity) shouldBe N.budgetIdentity(oldidentity)
    _used(scenario, session) shouldBe used
    issued.repair.get.files.map(_.path) shouldBe target.inventory.get.paths
    _scope_work(scenario, identity).admittedInventory shouldBe target.inventory
    _effects(scenario, session) shouldBe passive
    val issuedrecord = current(scenario, session)
    successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult] shouldBe issued
    current(scenario, session) shouldBe issuedrecord
    _effects(scenario, session) shouldBe passive
    When("the fixture corrects the actual issued input and necessary baseline helper while retaining the complete intended test")
    val files = issued.repair.get.files
    files.find(_.path == _test_path).get.text shouldBe _test("expected\n")
    files.find(_.path == _baseline_path).get.text shouldBe _baseline("initial\n")
    val replacements = files.map(file => file.path match {
      case "value.txt" => file.copy(text = "expected\n")
      case path if path == _baseline_path => file.copy(text = _baseline("expected\n"))
      case _ => file
    })
    val reference = V.WorkflowArtifactReference("fixture-budget-scope-fourth-repair", "1")
    val result = V.RepositorySyncNativeMergeRepairResult("repaired",
      "Fixture-authored fourth repair of the actual qualified input and stale baseline helper; preserve the original intended specification",
      replacements, None)
    val submission = successful(scenario.repositoryCall("submitNativeMergeRepair", Vector("--sessionId", session,
      "--result", json(result), "--resultReference", json(reference), "--executionEvidence",
      json(V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-budget-scope-repair-author"), None, None)))))
    submission.business[V.RepositorySyncNativeMergeRepairSubmissionResult].accepted shouldBe true
    _effects(scenario, session) shouldBe passive
    successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult].condition shouldBe Some("PurposeGrouped")
    val reviewed = successful(advance(scenario, session))
    reviewed.business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitNativeMergeReview")
    val fresh = formalReceipts(scenario, session).filterNot(original.contains)
    fresh.size shouldBe _checks.size
    fresh.map(_._2.identity).toSet shouldBe _checks.map(_.identity).toSet
    fresh.foreach { (_, receipt) =>
      receipt.termination shouldBe "exited"
      receipt.exitCode shouldBe Some(0)
      receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
    }
    val focused = fresh.find(_._2.identity == appendedCheck.identity).get._2.sbt.get
    focused.kind shouldBe WorkflowSbtValidation.Kind.TestOnly
    focused.selection.get.testClasses shouldBe Vector(selectedClass)
    focused.selection.get.testsRun should be > 0L
    _invocations(scenario).size shouldBe passive.hcursor.get[Vector[String]]("invocations").toOption.get.size + _checks.size
    git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe firstcommit
    When("independent fixture review accepts only the fresh actual candidate and ordinary Advance installs its second Purpose commit")
    _review(scenario, session, reviewed, "scope-second")
    val terminalcall = successful(advance(scenario, session))
    val terminal = terminalcall.business[V.RepositorySyncAdvanceResult]
    Then("the same clean Session completes with four cumulative repairs one original grant and every old native failure retained")
    terminal.kind shouldBe "Terminal"
    current(scenario, session).lifecycle shouldBe Lifecycle.Completed
    current(scenario, session).history.take(old.history.size) shouldBe old.history
    git(scenario.root, "-C", repository.toString, "status", "--porcelain") shouldBe ""
    git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD^") shouldBe firstcommit
    val head = git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD")
    head should not be firstcommit
    Files.readString(repository.resolve("value.txt")) shouldBe "expected\n"
    Files.readString(repository.resolve(_test_path)) shouldBe _test("expected\n")
    Files.readString(repository.resolve(_baseline_path)) shouldBe _baseline("expected\n")
    Files.readString(repository.resolve(_document_path)) shouldBe "Completed independent document\n"
    val purpose = receipts(scenario, session).flatMap(_.payload.value.purposes).maxBy(value => (value.cursor, value.attempt))
    purpose.commits shouldBe Vector(firstcommit, head)
    _used(scenario, session) shouldBe used
    original.foreach(value => formalReceipts(scenario, session) should contain(value))
    oldnative.foreach(value => _native(scenario, session) should contain(value))
    receipts(scenario, session).filter(_.payload.value.stage == "RepairBudgetContinuationAdmitted") shouldBe budgetgrants
    _field_[Json]("postScope").hcursor.get[Vector[String]]("unknownRows").toOption.get
      .foreach(raw => stored(scenario) should contain(raw))
    _scope_work_rows(scenario, oldidentity) shouldBe _field_[Vector[String]]("oldWorkRows")
    P.repairProgressJson(_failed(scenario, session)) shouldBe _field_[Json]("limitCheckpoint").hcursor.get[Json]("failure").toOption.get
    _assert_configuration(scenario, _field_[Json]("postScope").hcursor.get[String]("configuration").toOption.get)
    val finalstate = _scope_snapshot(scenario, session)
    successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult] shouldBe terminal
    _scope_snapshot(scenario, session) shouldBe finalstate
    Files.writeString(_scope_result, Json.obj("checkpoint" -> saved, "qualifiedIdentity" -> identity.asJson,
      "originalLimit" -> _original_limit.asJson, "effectiveLimit" -> (_original_limit + 1).asJson,
      "usedRepairs" -> used.size.asJson, "usedIdentities" -> used.asJson,
      "replacementIssue" -> _scope_invocation(issuedcall), "repairSubmission" -> _scope_invocation(submission),
      "reviewIssue" -> _scope_invocation(reviewed), "terminalInvocation" -> _scope_invocation(terminalcall),
      "freshReceipts" -> fresh.asJson, "purposeCommits" -> Vector(firstcommit, head).asJson,
      "terminal" -> parse(json(terminal)).toOption.get, "finalState" -> finalstate,
      "installedCommand" -> installed().toString.asJson).noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
  }

  "ID10 installed same-Session finite repair budget" should {
    if (_phase == "before") {
      "retain three accepted still-failing repairs at the actual original limit" in { _before() }
    }
    if (_phase == "after") {
      "adopt one finite allowance passively and complete through one new actual repair" in { _after() }
    }
    if (_phase == "scope-before") {
      "retain the actual old budget and scope 409 after one finite grant and fourth repair issuance" in { _scope_before() }
    }
    if (_phase == "scope-after") {
      "resume the exact scope checkpoint and complete its fourth repair without counting the administrative reissue" in { _scope_after() }
    }
  }
}
