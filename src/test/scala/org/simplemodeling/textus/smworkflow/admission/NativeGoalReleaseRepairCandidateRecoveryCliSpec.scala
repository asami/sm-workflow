package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import org.goldenport.convert.ValueReader
import org.goldenport.record.RecordPresentable
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import GoalPhaseWholePhaseCandidateRecovery as W
import GoalPhaseNativeCandidate as C
import GoalPhaseOrderedProgression as O
import IncrementalSbtDiagnosisRepair as D
import D.given
import SplitPhaseExplicitPartitionFixture.take

/** OLD setup and NEW resume operate on one actual test-owned public lifecycle.
  * SQLite access is read-only evidence collection. Every review and Human answer
  * is independent SPECIFICATION-OWNED input, never genuine Human consent.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalReleaseRepairCandidateRecoveryCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _directory_owners = sys.props.get("smworkflow.spec.releaseCandidateDirectoryOwners").exists { value =>
    require(Set("true", "false")(value), "Select an explicit true or false directory-owner fixture option")
    value == "true"
  }
  private val _source = if (_directory_owners) "src/test/scala/HistoryExample.scala" else "src/main/scala/HistoryExample.scala"
  private val _auxiliary = "src/test/scala/ReleaseAuxiliary.scala"
  private val _catalog = "src/main/catalog/car/org/simplemodeling/textus/textus-sm-workflow.yaml"
  private val _selectors = if (_directory_owners) Vector("src/test/scala", "build.sbt") else Vector(_source)
  private def _required(name: String): Path = {
    val path = Path.of(sys.props.getOrElse(name, throw new IllegalArgumentException("Missing " + name)))
    require(path.normalize == path && (0 until path.getNameCount).forall(index => path.getName(index).toString != ".."),
      name + " must be normalized without traversal")
    val selected = if (path.isAbsolute) path else {
      val relativeallowed = Set("smworkflow.spec.releaseCandidateNewInstallations", "smworkflow.spec.releaseCandidateLocator")
      val boundary = Path.of("target/p69rcr")
      require(relativeallowed(name) && path.startsWith(boundary) && path != boundary,
        name + " must be absolute or an explicit target/p69rcr descendant for the two fixture outputs")
      Path.of("").toAbsolutePath.normalize.toRealPath().resolve(path)
    }
    val parent = selected.getParent
    require(parent != null && Files.isDirectory(parent) && parent.toRealPath() == parent &&
      !Files.isSymbolicLink(parent) && !Files.isSymbolicLink(selected), name + " must retain a real nonsymlink parent and target")
    selected
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _read(path: Path): Json = parse(Files.readString(path)).fold(error => fail(error.message), identity)
  private def _save(scenario: F.Scenario, name: String, value: RecordPresentable): String = {
    val path = scenario.root.resolve(name + ".json")
    Files.writeString(path, _json(value), StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _checks(scenario: F.Scenario): Vector[String] = {
    val path = scenario.root.resolve("sbt-executions.txt")
    if (Files.exists(path)) Files.readString(path).linesIterator.toVector else Vector.empty
  }
  private def _native(scenario: F.Scenario): Vector[Json] = {
    val connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + scenario.root.resolve("workflow.sqlite").toUri.toASCIIString +
      "?mode=ro", new java.util.Properties())
    try {
      val statement = connection.createStatement()
      try {
        val tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
        val names = Vector.newBuilder[String]
        try { while (tables.next()) names += tables.getString(1) } finally tables.close()
        names.result().flatMap { name =>
          val rows = statement.executeQuery("SELECT * FROM \"" + name.replace("\"", "\"\"") + "\"")
          val values = Vector.newBuilder[Json]
          try { while (rows.next()) (1 to rows.getMetaData.getColumnCount).foreach { column =>
            Option(rows.getString(column)).filter(_.startsWith("cncf.workflow-json.v1:")).foreach(raw =>
              values += parse(raw.stripPrefix("cncf.workflow-json.v1:")).fold(error => fail(error.message), identity))
          } } finally rows.close()
          values.result()
        }
      } finally statement.close()
    } finally connection.close()
  }
  private def _instance(native: Vector[Json], session: String): Json = {
    val matches = native.filter(value => value.hcursor.get[String]("schemaVersion").toOption
      .contains("cncf.workflow-instance-record.v1") && value.hcursor.get[String]("identity").toOption.contains(session))
    matches.size shouldBe 1
    matches.head
  }
  private def _history(instance: Json): Vector[Json] = instance.hcursor.get[Vector[Json]]("history").toOption.get
  private def _causes(instance: Json): Vector[String] = _history(instance).map(_.hcursor.get[String]("causation").toOption.get)
  private def _revision(instance: Json): Long = instance.hcursor.downField("current").get[Long]("revision").toOption.get
  private def _receipts(native: Vector[Json], session: String): Vector[Json] = native.filter(value =>
    value.hcursor.get[String]("schemaVersion").toOption.contains("cncf.workflow-mechanical-transition.v1") &&
      value.hcursor.downField("current").get[String]("identity").toOption.contains(session))
  private def _receipt(native: Vector[Json], session: String, suffix: String): Json = {
    val matches = _receipts(native, session).filter(_.hcursor.downField("reference").get[String]("identity").toOption.exists(_.endsWith(suffix)))
    matches.size shouldBe 1
    matches.head
  }
  private def _payload(receipt: Json): C.Payload = C.codec.decode(receipt.hcursor.downField("payload").downField("value").focus.get)
    .fold(error => fail(error), identity)
  private def _counts(native: Vector[Json], session: String): Map[String, Int] = {
    val instance = _instance(native, session)
    val causes = _causes(instance)
    Map("historyResults" -> causes.count(_.startsWith("ordered-candidate:source-history-result-")),
      "stageResults" -> causes.count(_.startsWith("ordered-candidate:stage-result-")),
      "stepCommits" -> causes.count(_ == "ordered-step-closure:committed"),
      "observationIntents" -> causes.count(_ == "ordered-candidate:observation-intent"),
      "hygienePlans" -> causes.count(_ == "ordered-candidate:commit-hygiene-plan"),
      "submittedResults" -> native.count { value =>
        val result = value.hcursor.downField("semanticCompletion").downField("result")
        value.hcursor.get[String]("schemaVersion").toOption.contains("cncf.workflow-completion-record.v2") &&
          result.get[String]("kind").toOption.contains("CONTINUATION_RESULT") &&
          result.get[String]("runId").toOption.contains(session)
      })
  }
  private def _facts(scenario: F.Scenario): Json = {
    val repository = Path.of(scenario.host.target.root)
    val source = repository.resolve(_source)
    val common = Json.obj("source" -> Files.readString(source).asJson, "sourceModified" -> Files.getLastModifiedTime(source).toString.asJson,
      "head" -> scenario.candidateHead.asJson, "indexPaths" -> scenario.candidateIndex.asJson,
      "indexDiff" -> scenario.candidateIndexDiff.asJson, "worktreeDiff" -> scenario.candidateWorktreeDiff.asJson,
      "status" -> scenario.candidateStatus.asJson)
    if (!_directory_owners) common else common.mapObject(_.add("auxiliary", Files.readString(repository.resolve(_auxiliary)).asJson)
      .add("auxiliaryModified", Files.getLastModifiedTime(repository.resolve(_auxiliary)).toString.asJson)
      .add("build", Files.readString(repository.resolve("build.sbt")).asJson)
      .add("buildModified", Files.getLastModifiedTime(repository.resolve("build.sbt")).toString.asJson)
      .add("catalog", Files.readString(repository.resolve(_catalog)).asJson)
      .add("catalogModified", Files.getLastModifiedTime(repository.resolve(_catalog)).toString.asJson))
  }
  private final class Cli(val scenario: F.Scenario, executable: Path, installations: Path) {
    def call(service: String, operation: String, parameters: Vector[String]): F.Result =
      scenario.callInstalled(executable, service, operation, parameters, Some(installations),
        Some(scenario.root.resolve(".textus/assembly.yaml")))
    def goal(operation: String, parameters: Vector[String]): F.Result = call("GoalPhaseService", operation, parameters)
    def advance(session: String): F.Result = goal("advanceGoalPhase", Vector("--sessionId", session))
    def describe(session: String): F.Result = goal(W.describeOperation, Vector("--sessionId", session))
    def recover(name: String, input: V.GoalPhaseWholePhaseCandidateRecoveryInput): F.Result =
      goal(W.recoveryOperation, Vector("--inputResource", _save(scenario, name, input)))
    def prepare(operation: String, value: RecordPresentable): Unit = {
      val prepared = call("NativeWorkflowPreparationService", operation, Vector("--input", _json(value)))
      withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
      scenario.selectPreparedImport(prepared)
    }
    def submit(request: V.GoalPhaseAdvanceResult, result: V.GoalPhaseWorkResult, name: String,
      execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
      val submitted = goal(request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
        "--result", _json(result), "--resultReference", _json(V.WorkflowArtifactReference(name, "1"))) ++
        execution.toVector.flatMap(value => Vector("--executionEvidence", _json(value))))
      submitted.business[V.GoalPhaseWorkSubmissionResult].accepted shouldBe true
    }
    def acceptReview(actual: F.Result): Unit = {
      val reference = V.WorkflowArtifactReference("release-recovery-step-review", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
      val result = _result("accept")
      prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "review", actual.invocation.stdout.toUri.toASCIIString,
        _save(scenario, "step-review-result", result), _save(scenario, "step-review-reference", reference),
        _save(scenario, "step-review-execution", execution), "normal-development-host", Instant.now().toString,
        "entity:smworkflow.cli.host_evidence", None))
      submit(actual.business[V.GoalPhaseAdvanceResult], result, reference.identity, Some(execution))
    }
    def authority(actual: F.Result): Unit = prepare("prepareAuthority", V.WorkflowAuthorityPreparationInput(
      actual.invocation.stdout.toUri.toASCIIString,
      _save(scenario, "step-authority-reference", V.WorkflowArtifactReference("release-recovery-step-authority", "1")),
      "participant", "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence"))
    def authorize(actual: F.Result): V.GoalPhaseWholePhaseCandidateRecoveryInput = {
      val description = actual.business[V.GoalPhaseWholePhaseCandidateRecoveryDescriptionResult]
      val answer = V.GoalPhaseWholePhaseCandidateRecoveryAnswer("AuthorizeNewObservation",
        "SPECIFICATION-OWNED independent Human observed the OLD invocation terminate; permit one remaining clean observation", true)
      val reference = V.WorkflowArtifactReference("new-release-recovery-independent-human", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "whole-phase-candidate-recovery",
        actual.invocation.stdout.toUri.toASCIIString, _save(scenario, "new-human-answer", answer),
        _save(scenario, "new-human-reference", reference), _save(scenario, "new-human-execution", execution),
        "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some("participant")))
      V.GoalPhaseWholePhaseCandidateRecoveryInput(description.sessionId, description.target, answer.choice,
        answer.rationale, reference, answer.priorExecutionEnded)
    }
  }
  private def _result(outcome: String, issues: Option[V.GoalPhaseIssueReport] = None,
    manifest: Option[D.Manifest] = None): V.GoalPhaseWorkResult = V.GoalPhaseWorkResult(outcome,
    "SPECIFICATION-OWNED assigned source value and independent review", Vector.empty, None, None, issues,
    None, None, None, None, None, dependencyValidation = None,
    diagnosticRepair = manifest.map(value => GoalPhaseNativeMetadata.record(value.asJson)))

  "Installed release recovery with completed source history" should {
    "retain one OLD public repair frontier and resume it under NEW with fresh Human and clean-only hygiene" in {
      val executable = _required("smworkflow.spec.installedCommand").toRealPath()
      val oldimage = _required("smworkflow.spec.releaseCandidateOldInstallations").toRealPath()
      val newimage = _required("smworkflow.spec.releaseCandidateNewInstallations").toRealPath()
      val locator = _required("smworkflow.spec.releaseCandidateLocator")
      if (_directory_owners) {
        val checkout = Path.of("").toAbsolutePath.normalize.toRealPath()
        locator shouldBe checkout.resolve("target/p69rcr/directory-old-new-locator.json")
        newimage shouldBe checkout.resolve("target/p69rcr/installations").toRealPath()
      }
      val stage = sys.props.getOrElse("smworkflow.spec.releaseCandidateStage", throw new IllegalArgumentException("Missing releaseCandidateStage"))
      Set("setup", "resume", "verify") should contain(stage)
      oldimage should not be newimage
      if (stage == "setup") {
        Given("P69-RELEASE-UNKNOWN-01: OLD normal installed executable and actual Compile/Test with independent fixture review and authority")
        Files.exists(locator) shouldBe false
        val scenario = F.freshReleaseHistoryRecovery(if (_directory_owners) "p69-release-directory" else "p69-release-candidate",
          TypedSbtHostPlanFixture.normalExecutable, directoryowners = _directory_owners)
        val cli = new Cli(scenario, executable, oldimage)
        val start = cli.goal("startGoalPhase", Vector("--input", scenario.input.noSpaces)).business[V.GoalPhaseStartResult]
        val implementation = cli.advance(start.sessionId).business[V.GoalPhaseAdvanceResult]
        implementation.work.get.kind shouldBe "ImplementStep"
        val source = Path.of(scenario.host.target.root).resolve(_source)
        Files.readString(source) shouldBe "object HistoryExample { val value: Int = 0 }\n"
        Files.writeString(source, "object HistoryExample { val value: Int = 1 }\n")
        if (_directory_owners) {
          val repository = Path.of(scenario.host.target.root)
          Files.readString(repository.resolve(_auxiliary)) shouldBe "object ReleaseAuxiliary { val value: Int = 0 }\n"
          Files.writeString(repository.resolve(_auxiliary), "object ReleaseAuxiliary { val value: Int = 1 }\n")
          Files.writeString(repository.resolve("build.sbt"), "\n// Specification-owned Step directory candidate\n", StandardOpenOption.APPEND)
        }
        cli.submit(implementation, _result("completed"), "old-implementation")
        val reviewcall = cli.advance(start.sessionId)
        val review = reviewcall.business[V.GoalPhaseAdvanceResult]
        review.work.get.kind shouldBe "ReviewStep"
        review.work.get.review.get.checks.head.sbt.get.kind shouldBe "compile"
        review.work.get.review.get.checks.head.exitCode shouldBe Some(0)
        _checks(scenario) shouldBe empty
        cli.acceptReview(reviewcall)
        val closing = cli.advance(start.sessionId)
        closing.business[V.GoalPhaseAdvanceResult].authorityContext.get.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
        cli.authority(closing)
        val repaircall = cli.advance(start.sessionId)
        val repair = repaircall.business[V.GoalPhaseAdvanceResult]
        repair.work.get.kind shouldBe "RepairStep"
        repair.work.get.stepIdentity shouldBe None
        repair.work.get.completed.size shouldBe 1
        repair.work.get.review shouldBe None
        repair.work.get.checkFailure.get.initialPhaseRepair should not be empty
        repair.work.get.checkFailure.get.validation.outcome shouldBe Some("failed")
        repair.repairProgress.get.automaticCount shouldBe 1
        repair.repairProgress.get.ordinaryCount shouldBe 1
        repair.repairProgress.get.limit shouldBe 2
        _checks(scenario).size shouldBe 1
        val failure = repair.work.get.checkFailure.get.validation
        val failed = failure.checks.find(_.exitCode.exists(_ != 0)).get
        failed.sbt.get.kind shouldBe "test"
        val stream = if (failed.stdout.nonEmpty) "stdout" else "stderr"
        val manifest = D.Manifest(D.schema, "old-actual-phase-test-repair", failure.sourceRevision,
          O.Snapshot(failure.candidate.head, failure.candidate.tree), "Actual compiled value is 1; ProductCheck requires 42",
          Vector(D.Locator(failed.identity, stream, 1, 1, "Actual failed ProductCheck capture retained by the runtime")),
          Vector.empty, if (_directory_owners) Vector(_source, _auxiliary, "build.sbt") else Vector(_source),
          Vector(if (_directory_owners) "Set both assigned source values to 42 and append the harmless release build comment"
            else "Set only the assigned source value to 42"), Vector.empty,
          Vector("test"), Vector("Compile alone cannot establish ProductCheck"))
        val open = repair.work.get.issues.get.issues.filter(_.status != "RESOLVED")
        open should not be empty
        val issues = V.GoalPhaseIssueReport(open.map(issue => V.GoalPhaseIssueDisposition(issue.identity, "FIXED",
          "SPECIFICATION-OWNED assigned source now supplies the required actual value", None)))
        When("one accepted public repair Submit is followed by OLD Advance with no pending-index-check program")
        Files.writeString(source, "object HistoryExample { val value: Int = 42 }\n")
        if (_directory_owners) {
          val repository = Path.of(scenario.host.target.root)
          Files.writeString(repository.resolve(_auxiliary), "object ReleaseAuxiliary { val value: Int = 42 }\n")
          Files.writeString(repository.resolve("build.sbt"), "\n// Specification-owned release directory repair\n", StandardOpenOption.APPEND)
          Files.writeString(repository.resolve(_catalog), "name: retained-unstaged-publication-evidence\n")
        }
        cli.submit(repair, _result("completed", Some(issues), Some(manifest)), "old-accepted-release-repair")
        val failedadvance = cli.advance(start.sessionId)
        failedadvance.exitCode should not be 0
        val oldnative = _native(scenario)
        val oldinstance = _instance(oldnative, start.sessionId)
        val causes = _causes(oldinstance)
        causes.last shouldBe (if (_directory_owners) "ordered-candidate:stage-result-1" else "ordered-candidate:stage-result-0")
        val counts = _counts(oldnative, start.sessionId)
        counts("stepCommits") shouldBe 1
        counts("stageResults") shouldBe (if (_directory_owners) 4 else 2)
        counts("historyResults") shouldBe (if (_directory_owners) 4 else 2)
        if (_directory_owners) counts("submittedResults") shouldBe 3
        counts("hygienePlans") shouldBe 0
        counts("observationIntents") shouldBe 0
        val inspection = _receipts(oldnative, start.sessionId).filter(value => value.hcursor.downField("reference")
          .get[String]("identity").toOption.exists(_.endsWith(":inspection"))).map(_payload)
          .filter(_.step == "phase:" + scenario.plan.phase).maxBy(_.sourceRevision.toLong)
        val sourceprefix = start.sessionId + ":ordered-candidate:" + inspection.sourceRevision + ":"
        _receipt(oldnative, start.sessionId, sourceprefix + "source-history-result-0")
        _receipt(oldnative, start.sessionId, sourceprefix + "stage-result-0")
        if (_directory_owners) {
          _receipt(oldnative, start.sessionId, sourceprefix + "source-history-result-1")
          _receipt(oldnative, start.sessionId, sourceprefix + "stage-result-1")
        }
        Then("the actual complete history/stage prefix remains UNKNOWN and OLD cannot describe its noncontiguous release preparation")
        F.allowReleaseHistoryPendingIndex(scenario)
        val wait = cli.advance(start.sessionId)
        wait.business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
        val refused = cli.describe(start.sessionId)
        refused.exitCode should not be 0
        refused.invocation.logs should include(if (_directory_owners) "repository/index" else "noncontiguous")
        _instance(_native(scenario), start.sessionId) shouldBe oldinstance
        _counts(_native(scenario), start.sessionId) shouldBe counts
        _checks(scenario).size shouldBe 1
        val facts = _facts(scenario)
        Files.writeString(locator, Json.obj("schemaVersion" -> "release-candidate-recovery-fixture.v1".asJson,
          "root" -> scenario.root.toString.asJson, "sessionId" -> start.sessionId.asJson,
          "oldInstallations" -> oldimage.toString.asJson, "newInstallations" -> newimage.toString.asJson,
          "sourceRevision" -> inspection.sourceRevision.asJson, "frontierRevision" -> _revision(oldinstance).asJson,
          "instance" -> oldinstance, "nativePrefix" -> oldnative.asJson, "facts" -> facts, "counts" -> counts.asJson,
          "checks" -> _checks(scenario).asJson, "originalBase" -> take(implementation.work.get.originalBase.toRecord().toJsonStringC).asJson,
          "selectedPlan" -> take(implementation.work.get.selectedPlan.toRecord().toJsonStringC).asJson,
          "completed" -> repair.work.get.completed.map(value => take(value.toRecord().toJsonStringC)).asJson,
          "failedAdvanceExit" -> failedadvance.exitCode.asJson, "failedAdvanceStdout" -> failedadvance.invocation.stdout.toString.asJson,
          "failedAdvanceStderr" -> failedadvance.invocation.stderr.toString.asJson,
          "oldDescriptionExit" -> refused.exitCode.asJson, "oldDescriptionStderr" -> refused.invocation.stderr.toString.asJson,
          "invocations" -> scenario.invocations.map(value => Json.obj("argv" -> value.argv.asJson,
            "pid" -> value.pid.asJson, "stdout" -> value.stdout.toString.asJson, "stderr" -> value.stderr.toString.asJson)).asJson
        ).noSpaces, StandardOpenOption.CREATE_NEW)
      } else {
        Given("the immutable locator from OLD setup and the same test-owned normal host under the NEW installation")
        Files.isRegularFile(locator) shouldBe true
        val saved = _read(locator)
        saved.hcursor.get[String]("schemaVersion") shouldBe Right("release-candidate-recovery-fixture.v1")
        saved.hcursor.get[String]("oldInstallations") shouldBe Right(oldimage.toString)
        saved.hcursor.get[String]("newInstallations") shouldBe Right(newimage.toString)
        val session = saved.hcursor.get[String]("sessionId").toOption.get
        val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
        root.getFileName.toString.startsWith("p69-release-directory-") shouldBe _directory_owners
        Files.exists(root.resolve("new-release-candidate-result.json")) shouldBe false
        val scenario = F.reopenReleaseHistoryRecovery(root, TypedSbtHostPlanFixture.normalExecutable,
          if (stage == "verify") 2000 else 1000)
        val cli = new Cli(scenario, executable, newimage)
        val oldnative = saved.hcursor.get[Vector[Json]]("nativePrefix").toOption.get
        val oldinstance = saved.hcursor.downField("instance").focus.get
        val oldcounts = saved.hcursor.get[Map[String, Int]]("counts").toOption.get
        val oldchecks = saved.hcursor.get[Vector[String]]("checks").toOption.get
        val oldfacts = saved.hcursor.downField("facts").focus.get
        _facts(scenario) shouldBe oldfacts
        val (target, input, directioninstance, observedcall) = if (stage == "verify") {
          Given("NEW already retained its real direction, clean candidate and successful Phase Test before the prior assertion failed")
          val before = _native(scenario)
          val beforeinstance = _instance(before, session)
          val beforecounts = _counts(before, session)
          val beforechecks = _checks(scenario)
          _history(beforeinstance).take(_history(oldinstance).size) shouldBe _history(oldinstance)
          _receipts(oldnative, session).foreach(value => _receipts(before, session) should contain(value))
          beforechecks.size shouldBe oldchecks.size + 1
          beforechecks.take(oldchecks.size) shouldBe oldchecks
          beforecounts shouldBe oldcounts.updated("observationIntents", 1).updated("hygienePlans", 1)
          val reference = session + ":ordered-whole-phase-candidate-recovery:" + saved.hcursor.get[String]("sourceRevision").toOption.get
          val receipt = _receipt(before, session, reference)
          receipt.hcursor.downField("reference").get[String]("identity") shouldBe Right(reference)
          val direction = W.codec.decode(receipt.hcursor.downField("payload").downField("value").focus.get)
            .fold(error => fail(error), identity)
          val captured = direction.target
          val retaineddirection = receipt.hcursor.downField("next").focus.get
          val authorized = take(summon[ValueReader[V.GoalPhaseWholePhaseCandidateRecoveryInput]].readC(
            take(new RecordDecoder().json(Files.readString(root.resolve("new-authorize.json"))))))
          authorized.sessionId shouldBe session
          authorized.choice shouldBe "AuthorizeNewObservation"
          take(W.targetC(authorized.target)) shouldBe captured
          authorized.reference.identity shouldBe direction.reference.identity
          authorized.reference.revision shouldBe direction.reference.revision
          authorized.rationale shouldBe direction.rationale
          authorized.priorExecutionEnded shouldBe direction.priorExecutionEnded
          _require_target(saved, captured, oldfacts)
          _revision(retaineddirection) shouldBe _revision(oldinstance) + 1
          _history(retaineddirection).dropRight(1) shouldBe _history(oldinstance)
          _history(beforeinstance).take(_history(retaineddirection).size) shouldBe _history(retaineddirection)
          When("ordinary Advance reads the already pending ReviewPhase without a new Human, recovery or check")
          val readback = cli.advance(session)
          readback.business[V.GoalPhaseAdvanceResult].work.get.kind shouldBe "ReviewPhase"
          val afterread = _native(scenario)
          Then("the exact native instance, effect counts and actual check log remain unchanged")
          _instance(afterread, session) shouldBe beforeinstance
          _counts(afterread, session) shouldBe beforecounts
          _checks(scenario) shouldBe beforechecks
          (captured, authorized, retaineddirection, readback)
        } else {
          _instance(_native(scenario), session) shouldBe oldinstance
          _checks(scenario) shouldBe oldchecks
          val firstdescription = cli.describe(session)
          val described = if (!_directory_owners) firstdescription else {
            Given("the valid selected directory target with the fixed unrelated catalog retained only in the worktree")
            firstdescription.business[V.GoalPhaseWholePhaseCandidateRecoveryDescriptionResult].sessionId shouldBe session
            _facts(scenario) shouldBe oldfacts
            val before = _native(scenario)
            val beforechecks = _checks(scenario)
            When("only that catalog is staged in the isolated fixture before any Human authorization")
            scenario.stageReleaseHistoryCatalog()
            val refused = cli.describe(session)
            Then("description refuses repository/index ownership while native history and checks stay exact")
            refused.exitCode should not be 0
            refused.invocation.logs should include("repository/index")
            _instance(_native(scenario), session) shouldBe _instance(before, session)
            _counts(_native(scenario), session) shouldBe _counts(before, session)
            _native(scenario) should contain theSameElementsAs before
            _checks(scenario) shouldBe beforechecks
            scenario.restoreReleaseHistoryCatalogIndex()
            _facts(scenario) shouldBe oldfacts
            _instance(_native(scenario), session) shouldBe oldinstance
            _counts(_native(scenario), session) shouldBe oldcounts
            _native(scenario) should contain theSameElementsAs before
            _checks(scenario) shouldBe oldchecks
            val restored = cli.describe(session)
            restored.business[V.GoalPhaseWholePhaseCandidateRecoveryDescriptionResult] shouldBe
              firstdescription.business[V.GoalPhaseWholePhaseCandidateRecoveryDescriptionResult]
            restored
          }
          val description = described.business[V.GoalPhaseWholePhaseCandidateRecoveryDescriptionResult]
          val captured = take(W.targetC(description.target))
          _require_target(saved, captured, oldfacts)
          _instance(_native(scenario), session) shouldBe oldinstance
          _facts(scenario) shouldBe oldfacts
          When("fresh independent fixture Human evidence admits one direction and a separate ordinary Advance performs the remaining observation")
          val missing = V.GoalPhaseWholePhaseCandidateRecoveryInput(session, description.target, "AuthorizeNewObservation",
            "Input alone grants no authority", V.WorkflowArtifactReference("absent-release-human", "1"), true)
          cli.recover("new-missing-human", missing).exitCode should not be 0
          val authorized = cli.authorize(described)
          cli.recover("new-ended-false", authorized.copy(priorExecutionEnded = false)).exitCode should not be 0
          val directed = cli.recover("new-authorize", authorized).business[V.GoalPhaseWholePhaseCandidateRecoveryResult]
          directed.accepted shouldBe true
          val retaineddirection = _instance(_native(scenario), session)
          _revision(retaineddirection) shouldBe _revision(oldinstance) + 1
          _history(retaineddirection).dropRight(1) shouldBe _history(oldinstance)
          _facts(scenario) shouldBe oldfacts
          _checks(scenario) shouldBe oldchecks
          cli.recover("new-duplicate", authorized).exitCode should not be 0
          cli.describe(session).exitCode should not be 0
          (captured, authorized, retaineddirection, cli.advance(session))
        }
        val observed = observedcall.business[V.GoalPhaseAdvanceResult]
        Then("one clean hygiene plan precedes the new candidate and actual successful Phase Test while old effects and accepted Submit stay exact")
        observed.work.get.kind shouldBe "ReviewPhase"
        val priorreviews = observed.work.get.review.get.priorReviews
        priorreviews.exists(_.getString("kind").contains("ReviewPhase")) shouldBe false
        priorreviews.exists(value => value.getString("kind").contains("ReviewStep") &&
          value.getString("step").contains("step-1") && parse(take(value.toJsonStringC)).toOption.get
            .hcursor.downField("result").get[String]("outcome").toOption.contains("accept")) shouldBe true
        observed.work.get.review.get.checks.head.sbt.get.kind shouldBe "test"
        observed.work.get.review.get.checks.head.exitCode shouldBe Some(0)
        observed.validation.get.outcome shouldBe Some("passed")
        observed.work.get.completed.map(value => take(value.toRecord().toJsonStringC)) shouldBe saved.hcursor.get[Vector[String]]("completed").toOption.get
        _json(observed.work.get.selectedPlan) shouldBe saved.hcursor.get[String]("selectedPlan").toOption.get
        _json(observed.work.get.originalBase) shouldBe saved.hcursor.get[String]("originalBase").toOption.get
        observed.repairProgress.get.automaticCount shouldBe 1
        observed.repairProgress.get.ordinaryCount shouldBe 1
        observed.repairProgress.get.limit shouldBe 2
        _checks(scenario).size shouldBe oldchecks.size + 1
        _checks(scenario).take(oldchecks.size) shouldBe oldchecks
        val after = _native(scenario)
        val afterinstance = _instance(after, session)
        _history(afterinstance).take(_history(oldinstance).size) shouldBe _history(oldinstance)
        _receipts(oldnative, session).foreach(value => _receipts(after, session) should contain(value))
        val counts = _counts(after, session)
        counts shouldBe oldcounts.updated("observationIntents", 1).updated("hygienePlans", 1)
        val directionrevision = _revision(directioninstance).toString
        val prefix = session + ":ordered-candidate:" + directionrevision + ":"
        val intent = _receipt(after, session, prefix + "observation-intent")
        val hygiene = _receipt(after, session, prefix + "commit-hygiene-plan")
        val candidate = _receipt(after, session, prefix + "candidate")
        _revision(intent.hcursor.downField("next").focus.get) shouldBe _revision(directioninstance) + 1
        _revision(hygiene.hcursor.downField("next").focus.get) shouldBe _revision(directioninstance) + 2
        _revision(candidate.hcursor.downField("next").focus.get) shouldBe _revision(directioninstance) + 3
        _payload(hygiene).hygiene.get.outcome shouldBe "clean"
        _payload(hygiene).hygiene.get.mode shouldBe target.releasePreparation.get.hygieneMode
        _payload(hygiene).hygiene.get.findings shouldBe empty
        _payload(hygiene).hygiene.get.corrections shouldBe empty
        _payload(candidate).hygiene shouldBe _payload(hygiene).hygiene
        _payload(candidate).candidate.get.tree shouldBe target.repository.indexTree
        _facts(scenario) shouldBe oldfacts
        val checks = _checks(scenario)
        cli.advance(session).business[V.GoalPhaseAdvanceResult] shouldBe observed
        cli.recover("new-stale", input).exitCode should not be 0
        _instance(_native(scenario), session) shouldBe afterinstance
        _checks(scenario) shouldBe checks
        Files.writeString(root.resolve("new-release-candidate-result.json"), Json.obj("sessionId" -> session.asJson,
          "counts" -> counts.asJson, "checks" -> checks.asJson, "instance" -> afterinstance,
          "oldPrefix" -> oldinstance, "facts" -> _facts(scenario), "advanceExit" -> observedcall.exitCode.asJson,
          "advanceStdout" -> observedcall.invocation.stdout.toString.asJson,
          "advanceStderr" -> observedcall.invocation.stderr.toString.asJson).noSpaces, StandardOpenOption.CREATE_NEW)
      }
    }
  }

  private def _require_target(saved: Json, target: W.Target, oldfacts: Json): Unit = {
    target.sourceRevision shouldBe saved.hcursor.get[String]("sourceRevision").toOption.get
    target.frontierRevision.toLong shouldBe saved.hcursor.get[Long]("frontierRevision").toOption.get
    target.controller.stage shouldBe "PhaseInspection"
    target.controller.repairs shouldBe 1
    target.controller.ordinaryRepairs shouldBe 1
    target.controller.plan.repairLimit shouldBe 2
    target.controller.base shouldBe _implementation_base(saved)
    _json(F.publicPlan(target.controller.plan)) shouldBe saved.hcursor.get[String]("selectedPlan").toOption.get
    target.controller.completed.map(_.commit.identity) shouldBe Vector(oldfacts.hcursor.get[String]("head").toOption.get)
    target.oldObservationOutcome shouldBe "UNKNOWN"
    target.oldObservationTermination shouldBe "termination-unconfirmed"
    target.inspection.selectedPaths shouldBe _selectors
    target.releasePreparation.get.historyPrefix.size shouldBe (if (_directory_owners) 2 else 1)
    target.releasePreparation.get.historyPrefix.foreach(_.result.outcome shouldBe Some("updated"))
    target.stagingPrefix.size shouldBe (if (_directory_owners) 2 else 1)
    if (_directory_owners) {
      target.releasePreparation.get.historyPrefix.map(_.result.path).toSet shouldBe Set(_source, _auxiliary)
      target.candidatePaths shouldBe Vector("src/test/scala", "build.sbt")
      oldfacts.hcursor.get[String]("catalog") shouldBe Right("name: retained-unstaged-publication-evidence\n")
    }
  }

  private def _implementation_base(saved: Json): O.Snapshot = {
    val value = parse(saved.hcursor.get[String]("originalBase").toOption.get).toOption.get
    O.Snapshot(value.hcursor.get[String]("head").toOption.get, value.hcursor.get[String]("tree").toOption.get)
  }
}
