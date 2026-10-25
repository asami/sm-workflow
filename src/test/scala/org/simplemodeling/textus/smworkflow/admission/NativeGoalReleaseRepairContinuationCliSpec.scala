package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import org.goldenport.convert.ValueReader
import org.goldenport.record.RecordPresentable
import org.goldenport.record.io.RecordDecoder
import org.goldenport.cncf.processexecution.GitOperationKind
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseReleaseRepairContinuation as P
import GoalPhaseAcceptanceBoundary as B
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Two explicitly selected immutable installed images and one retained fixture.
  * All reviews and Human directions here are SPECIFICATION-OWNED test data.
  * No real Session, genuine approval, or manually seeded native receipt is used.
  * @since   Oct. 25, 2026
  * @version Oct. 25, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalReleaseRepairContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _required(name: String): Path = {
    val value = Path.of(sys.props.getOrElse(name, throw new IllegalArgumentException("Missing " + name)))
    require(value.isAbsolute && value.normalize == value, name + " must be absolute and normalized")
    value
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _read(path: Path): Json = parse(Files.readString(path)).fold(error => fail(error.message), identity)
  private def _save(s: F.Scenario, name: String, value: RecordPresentable, suffix: String = ""): String = {
    val path = s.root.resolve(name + suffix + ".json")
    Files.writeString(path, _json(value), StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _checks(s: F.Scenario): Vector[String] =
    Files.readString(s.root.resolve("sbt-executions.txt")).linesIterator.toVector
  /** Read-only evidence of retained native envelopes; never used to patch them. */
  private def _stored(s: F.Scenario): Vector[String] = {
    // Explicit fixture-local driver use avoids global registration visibility dependencies when reopening under a separate SBT test loader.
    val connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + s.root.resolve("workflow.sqlite").toUri.toASCIIString + "?mode=ro", new java.util.Properties())
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
  private final class Cli(val scenario: F.Scenario, executable: Path, installations: Path, suffix: String = "") {
    def call(operation: String, parameters: Vector[String]): F.Result =
      scenario.callInstalled(executable, "GoalPhaseService", operation, parameters, Some(installations))
    def input(operation: String, value: RecordPresentable): F.Result = call(operation, Vector("--input", _json(value)))
    def resource(operation: String, name: String, value: RecordPresentable): F.Result =
      call(operation, Vector("--inputResource", _save(scenario, name, value, suffix)))
    def advance(session: String): F.Result = call("advanceGoalPhase", Vector("--sessionId", session))
    def prepare(operation: String, value: RecordPresentable): Unit = {
      val prepared = scenario.callInstalled(executable, "NativeWorkflowPreparationService", operation,
        Vector("--input", _json(value)), Some(installations))
      withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
      scenario.selectPreparedImport(prepared)
    }
    def submit(request: V.GoalPhaseAdvanceResult, result: V.GoalPhaseWorkResult,
      reference: V.WorkflowArtifactReference, execution: Option[V.WorkflowExecutionEvidence] = None): Unit = {
      val response = call(request.submitOperation.get, Vector("--sessionId", request.submitSessionId.get,
        "--result", _json(result), "--resultReference", _json(reference)) ++ execution.toVector.flatMap(e =>
        Vector("--executionEvidence", _json(e)))).business[V.GoalPhaseWorkSubmissionResult]
      response.accepted shouldBe true
    }
    def review(actual: F.Result, result: V.GoalPhaseWorkResult, name: String): Unit = {
      val reference = V.WorkflowArtifactReference(name, "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-release-reviewer"), None, None)
      prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "review", actual.invocation.stdout.toUri.toASCIIString,
        _save(scenario, name + "-result", result, suffix), _save(scenario, name + "-reference", reference, suffix),
        _save(scenario, name + "-execution", execution, suffix), "normal-development-host", Instant.now().toString,
        "entity:smworkflow.cli.host_evidence", None))
      submit(actual.business[V.GoalPhaseAdvanceResult], result, reference, Some(execution))
    }
    def authority(actual: F.Result, name: String): Unit = prepare("prepareAuthority",
      V.WorkflowAuthorityPreparationInput(actual.invocation.stdout.toUri.toASCIIString,
        _save(scenario, name + "-reference", V.WorkflowArtifactReference(name, "1"), suffix), "participant",
        "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence"))
  }
  private def _result(s: F.Scenario, request: V.GoalPhaseAdvanceResult, outcome: String,
    findings: Vector[String] = Vector.empty, issues: Option[V.GoalPhaseIssueReport] = None): V.GoalPhaseWorkResult = {
    val effective = take(B.captureC(None, s.plan)).effectiveAcceptance.get
    val acceptance = B.applicable(effective, request.work.get.kind).map(condition =>
      V.GoalPhaseAcceptanceObservation(condition.identity, if (outcome == "repair") "unsatisfied" else "satisfied",
        if (outcome == "repair") findings.head else "SPECIFICATION-OWNED verified accepted input contract"))
    V.GoalPhaseWorkResult(outcome, "SPECIFICATION-OWNED assigned semantic result", findings,
      Some(V.GoalPhaseAcceptanceReport(acceptance, Vector.empty)), None, issues, None, None, None, None,
      repairFindingSources = None, dependencyValidation = None)
  }

  private def _envelopes(s: F.Scenario): Vector[Json] =
    _stored(s).map(value => parse(value.stripPrefix("cncf.workflow-json.v1:")).toOption.get)
  private def _instance(s: F.Scenario, session: String): Json = {
    val matches = _envelopes(s).filter(value =>
      value.hcursor.get[String]("schemaVersion").toOption.contains("cncf.workflow-instance-record.v1") &&
        value.hcursor.get[String]("identity").toOption.contains(session))
    matches.size shouldBe 1
    matches.head
  }
  private def _receipts(s: F.Scenario, session: String): Vector[Json] = _envelopes(s).filter(value =>
    value.hcursor.get[String]("schemaVersion").toOption.contains("cncf.workflow-mechanical-transition.v1") &&
      value.hcursor.downField("current").get[String]("identity").toOption.contains(session))
  private def _receipt(s: F.Scenario, session: String, suffix: String): Json = {
    val matches = _receipts(s, session).filter(_.hcursor.downField("reference").get[String]("identity").toOption
      .exists(_.endsWith(suffix))).filter(value => !Set(":intent", ":execution", ":committed")(suffix) ||
        value.hcursor.downField("payload").get[String]("typeIdentity").toOption.contains("GoalPhaseNativeReleaseCommitReceipt"))
    matches.size shouldBe 1
    matches.head
  }
  private def _submitted_results(envelopes: Vector[Json], session: String): Int = envelopes.count { value =>
    val result = value.hcursor.downField("semanticCompletion").downField("result")
    value.hcursor.get[String]("schemaVersion").toOption.contains("cncf.workflow-completion-record.v2") &&
      result.get[String]("kind").toOption.contains("CONTINUATION_RESULT") &&
      result.get[String]("runId").toOption.contains(session)
  }
  /** Counters consume actual native history and retained result envelopes, plus
    * the normal SBT invocation log. No output-silence inference is used.
    */
  private def _effect_counts(s: F.Scenario, session: String): Map[String, Int] = {
    val history = _instance(s, session).hcursor.get[Vector[Json]]("history").toOption.get
    val causes = history.map(_.hcursor.get[String]("causation").toOption.get)
    Map("sbt" -> _checks(s).size,
      "reviewIssued" -> causes.count(_.startsWith("ordered-review-issued:")),
      "submittedResults" -> _submitted_results(_envelopes(s), session),
      "stageExecuted" -> causes.count(_.startsWith("ordered-candidate:stage-result-")),
      "historyReflected" -> causes.count(_.startsWith("ordered-candidate:source-history-result-")),
      "finalExecuted" -> causes.count(_ == "ordered-release-closure:execution"),
      "terminalAppended" -> history.count(_.hcursor.get[String]("lifecycle").toOption.contains("completed")))
  }
  private def _retain_snapshot(s: F.Scenario, session: String, name: String): Unit = {
    Files.writeString(s.root.resolve(name + ".json"), Json.obj("sessionId" -> session.asJson,
      "native" -> _envelopes(s).asJson, "counts" -> _effect_counts(s, session).asJson,
      "head" -> s.candidateHead.asJson, "index" -> s.candidateIndex.asJson,
      "invocations" -> s.invocations.map(value => Json.obj("argv" -> value.argv.asJson,
        "pid" -> value.pid.asJson, "stdout" -> value.stdout.toString.asJson,
        "stderr" -> value.stderr.toString.asJson)).asJson).noSpaces, StandardOpenOption.CREATE_NEW)
  }
  private def _sql_identifier(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
  private def _sql_literal(value: String): String = "'" + value.replace("'", "''") + "'"
  /** Discover the unique actual instance cell, not a historical lifecycle token
    * in a receipt. Only fixture-local trigger DDL is written through JDBC.
    */
  private def _refuse_terminal(s: F.Scenario, session: String): Unit = {
    val connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + s.root.resolve("workflow.sqlite"), new java.util.Properties())
    try {
      val statement = connection.createStatement()
      try {
        val tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
        val names = Vector.newBuilder[String]
        try { while (tables.next()) names += tables.getString(1) } finally tables.close()
        val targets = names.result().flatMap { name =>
          val rows = statement.executeQuery("SELECT * FROM " + _sql_identifier(name))
          val found = Vector.newBuilder[(String, String, String)]
          try { while (rows.next()) (1 to rows.getMetaData.getColumnCount).foreach { column =>
            Option(rows.getString(column)).filter(_.startsWith("cncf.workflow-json.v1:")).foreach { raw =>
              val json = parse(raw.stripPrefix("cncf.workflow-json.v1:")).toOption.get
              if (json.hcursor.get[String]("schemaVersion").toOption.contains("cncf.workflow-instance-record.v1") &&
                  json.hcursor.get[String]("identity").toOption.contains(session)) {
                json.hcursor.downField("current").get[String]("lifecycle").toOption.get shouldBe "active"
                found += ((name, rows.getMetaData.getColumnName(column), rows.getString("id")))
              }
            }
          } } finally rows.close()
          found.result()
        }
        targets.size shouldBe 1
        val (table, column, rowid) = targets.head
        Option(rowid).exists(_.nonEmpty) shouldBe true
        val cell = "NEW." + _sql_identifier(column)
        val json = "substr(" + cell + "," + ("cncf.workflow-json.v1:".length + 1) + ")"
        val condition = "NEW." + _sql_identifier("id") + "=" + _sql_literal(rowid) +
          " AND substr(" + cell + ",1," + "cncf.workflow-json.v1:".length + ")=" + _sql_literal("cncf.workflow-json.v1:") +
          " AND json_extract(" + json + ",'$.schemaVersion')='cncf.workflow-instance-record.v1'" +
          " AND json_extract(" + json + ",'$.identity')=" + _sql_literal(session) +
          " AND json_extract(" + json + ",'$.current.lifecycle')='completed'"
        val statements = Vector("INSERT", "UPDATE").map { operation =>
          "CREATE TRIGGER " + _sql_identifier("spec_terminal_refusal_" + operation.toLowerCase(java.util.Locale.ROOT)) +
            " BEFORE " + operation + " ON " + _sql_identifier(table) + " WHEN " + condition +
            " BEGIN SELECT RAISE(ABORT,'SPECIFICATION terminal lifecycle write refused'); END"
        }
        Files.writeString(s.root.resolve("terminal-refusal.sql"), statements.mkString(";\n") + ";\n", StandardOpenOption.CREATE_NEW)
        statements.foreach(statement.executeUpdate)
      } finally statement.close()
    } finally connection.close()
  }
  private def _allow_terminal(s: F.Scenario): Unit = {
    val connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + s.root.resolve("workflow.sqlite"), new java.util.Properties())
    try {
      val statement = connection.createStatement()
      try Vector("insert", "update").foreach(operation =>
        statement.executeUpdate("DROP TRIGGER " + _sql_identifier("spec_terminal_refusal_" + operation)))
      finally statement.close()
    } finally connection.close()
  }
  private def _deny_final(s: F.Scenario): String = {
    val path = s.root.resolve(".textus/config.json")
    val original = Files.readString(path)
    val config = parse(original).toOption.get
    val process = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val capability = TypedGitHostPlanFixture.capability(GitOperationKind.CommitSelected, Some("goal-step-2"))
    val grants = process.hcursor.get[Vector[Json]]("grants").toOption.get.map { grant =>
      val capabilities = grant.hcursor.get[Vector[String]]("capabilities").toOption.get
      capabilities should contain(capability)
      grant.mapObject(_.add("capabilities", capabilities.filterNot(_ == capability).asJson))
    }
    Files.writeString(path, config.mapObject(_.add("textus.subsystem.process-execution.plan",
      Json.fromString(process.mapObject(_.add("grants", grants.asJson)).noSpaces))).noSpaces)
    original
  }
  /** New setup through Final authority under exactly one installed image.
    * Reuses ordinary preparation, accepted Submit and actual typed Git/SBT.
    */
  private def _release_ready(name: String, executable: Path, image: Path): (F.Scenario, Cli, String, V.GoalPhaseAdvanceResult) = {
    val s = F.freshReleaseFix(name, TypedSbtHostPlanFixture.normalExecutable, requiredCondition = true)
    val cli = new Cli(s, executable, image)
    val started = cli.call("startGoalPhase", Vector("--input", s.input.noSpaces)).business[V.GoalPhaseStartResult]
    val implementation = cli.advance(started.sessionId).business[V.GoalPhaseAdvanceResult]
    val repo = Path.of(s.host.target.root)
    Files.writeString(repo.resolve("value.txt"), "expected\n")
    Files.createDirectories(repo.resolve("docs"))
    Files.writeString(repo.resolve("docs/usage.md"), "The accepted input is arbitrary text.\n")
    cli.submit(implementation, _result(s, implementation, "completed"), V.WorkflowArtifactReference("terminal-implementation", "1"))
    val step = cli.advance(started.sessionId)
    cli.review(step, _result(s, step.business[V.GoalPhaseAdvanceResult], "accept"), "terminal-step-review")
    val closing = cli.advance(started.sessionId)
    cli.authority(closing, "terminal-step-authority")
    val phase = cli.advance(started.sessionId)
    val integrated = phase.business[V.GoalPhaseAdvanceResult]
    integrated.work.get.kind shouldBe "ReviewPhase"
    integrated.work.get.releaseRepairContract shouldBe Some("sm-workflow.phase-release-repair.v1")
    cli.review(phase, _result(s, integrated, "repair", Vector("docs/usage.md must describe exactly expected followed by LF")),
      "terminal-integrated-review")
    val repair = cli.advance(started.sessionId).business[V.GoalPhaseAdvanceResult]
    repair.work.get.kind shouldBe "RepairStep"
    val open = repair.work.get.issues.get.issues.filter(_.status != "RESOLVED")
    open should not be empty
    Files.writeString(repo.resolve("docs/usage.md"), "The accepted input is exactly expected followed by LF.\n")
    val fixed = V.GoalPhaseIssueReport(open.map(issue => V.GoalPhaseIssueDisposition(issue.identity, "FIXED",
      "Corrected the actual usage contract", None)))
    cli.submit(repair, _result(s, repair, "completed", issues = Some(fixed)), V.WorkflowArtifactReference("terminal-release-fix", "1"))
    val rereviewcall = cli.advance(started.sessionId)
    val rereview = rereviewcall.business[V.GoalPhaseAdvanceResult]
    rereview.work.get.kind shouldBe "ReviewPhase"
    rereview.work.get.completed shouldBe integrated.work.get.completed
    val resolved = V.GoalPhaseIssueReport(open.map(issue => V.GoalPhaseIssueDisposition(issue.identity, "RESOLVED",
      "Independent fixture review confirms the unchanged accepted input", None)))
    cli.review(rereviewcall, _result(s, rereview, "accept", issues = Some(resolved)), "terminal-release-rereview")
    val finalwait = cli.advance(started.sessionId)
    finalwait.business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
    cli.authority(finalwait, "terminal-final-authority")
    _checks(s).size shouldBe 3
    (s, cli, started.sessionId, rereview)
  }

  sys.props.get("smworkflow.spec.terminalReopenInstallations").foreach { _ =>
    "Installed finite terminal completion on one new immutable image" should {
      "retain one actual Final after terminal write refusal and append only missing completion on ordinary reopen" in {
        Given("P65-FINAL126-TERMINAL-01: one explicitly selected new installation for fresh setup Final failure and all reopen calls")
        val executable = _required("smworkflow.spec.installedCommand")
        val image = _required("smworkflow.spec.terminalReopenInstallations").toRealPath()
        val (s, cli, session, accepted) = _release_ready("terminal-write-refusal", executable, image)
        val parent = s.candidateHead
        parent shouldBe accepted.work.get.completed.last.commit.identity
        val before = _instance(s, session)
        before.hcursor.downField("current").get[String]("lifecycle") shouldBe Right("active")
        val beforecounts = _effect_counts(s, session)
        beforecounts("finalExecuted") shouldBe 0
        beforecounts("terminalAppended") shouldBe 0
        Files.writeString(s.root.resolve("terminal-installation.json"), Json.obj("installation" -> image.toString.asJson,
          "command" -> executable.toString.asJson).noSpaces, StandardOpenOption.CREATE_NEW)
        _retain_snapshot(s, session, "terminal-before-final")
        _refuse_terminal(s, session)
        When("ordinary Final executes once while the fixture SQLite trigger rejects only the native Completed write")
        val failed = cli.advance(session)
        Then("the actual storage error remains visible without a claimed Terminal and the genuine Final chain and Closed payload survive")
        failed.exitCode should not be 0
        failed.invocation.logs should include("SPECIFICATION terminal lifecycle write refused")
        val failednative = _instance(s, session)
        failednative.hcursor.downField("current").get[String]("lifecycle") shouldBe Right("active")
        failednative.hcursor.downField("current").get[String]("progression") shouldBe Right("phase.Closed")
        val intent = _receipt(s, session, ":intent")
        val execution = _receipt(s, session, ":execution")
        val committed = _receipt(s, session, ":committed")
        intent.hcursor.downField("payload").get[String]("typeIdentity") shouldBe Right("GoalPhaseNativeReleaseCommitReceipt")
        execution.hcursor.downField("current").focus shouldBe intent.hcursor.downField("next").focus
        committed.hcursor.downField("current").focus shouldBe execution.hcursor.downField("next").focus
        val intentpayload = intent.hcursor.downField("payload").downField("value").downField("payload").focus.get
        val executionpayload = execution.hcursor.downField("payload").downField("value").downField("payload").focus.get
        val committedpayload = committed.hcursor.downField("payload").downField("value").downField("payload").focus.get
        executionpayload.mapObject(_.add("execution", Json.Null)) shouldBe intentpayload
        committedpayload.mapObject(_.add("commit", Json.Null)) shouldBe executionpayload
        committedpayload.hcursor.downField("execution").get[String]("outcome") shouldBe Right("succeeded")
        committedpayload.hcursor.downField("execution").get[Int]("exitCode") shouldBe Right(0)
        committedpayload.hcursor.downField("execution").get[Vector[String]]("selectedPaths") shouldBe Right(Vector("docs/usage.md"))
        val finalhead = s.candidateHead
        finalhead should not be parent
        s.candidateParent shouldBe parent
        s.candidateIndex shouldBe ""
        committedpayload.hcursor.downField("commit").get[String]("identity") shouldBe Right(finalhead)
        committedpayload.hcursor.downField("commit").get[String]("parent") shouldBe Right(parent)
        committedpayload.hcursor.downField("commit").get[String]("tree") shouldBe Right(accepted.work.get.candidate.get.tree)
        val local = _receipt(s, session, ":ordered-local-completion")
        local.hcursor.downField("next").focus shouldBe Some(failednative)
        _receipts(s, session).exists(_.hcursor.downField("reference").get[String]("identity").toOption
          .contains(session + ":ordered-local-terminal")) shouldBe false
        val failedcounts = _effect_counts(s, session)
        failedcounts shouldBe beforecounts.updated("finalExecuted", 1)
        failedcounts("finalExecuted") shouldBe 1
        failedcounts("terminalAppended") shouldBe 0
        failedcounts("reviewIssued") should be > 0
        failedcounts("submittedResults") should be > 0
        failedcounts("stageExecuted") should be > 0
        val failedreceipts = _receipts(s, session)
        val failedchecks = _checks(s)
        _retain_snapshot(s, session, "terminal-after-refusal")
        _allow_terminal(s)
        When("the same installed command explicitly performs ordinary Advance after removing only the fixture trigger")
        val reopened = cli.advance(session).business[V.GoalPhaseAdvanceResult]
        Then("one missing Completed append returns actual Terminal without any SBT review Submit stage history or Final replay")
        reopened.kind shouldBe "Terminal"
        reopened.completion.get.completed shouldBe accepted.work.get.completed
        val completed = _instance(s, session)
        completed.hcursor.downField("current").get[String]("lifecycle") shouldBe Right("completed")
        completed.hcursor.downField("current").downField("progression").focus shouldBe Some(Json.Null)
        completed.hcursor.get[Vector[Json]]("history").toOption.get.dropRight(1) shouldBe
          failednative.hcursor.get[Vector[Json]]("history").toOption.get
        completed.hcursor.downField("current").get[Long]("revision").toOption.get shouldBe
          failednative.hcursor.downField("current").get[Long]("revision").toOption.get + 1L
        _effect_counts(s, session) shouldBe failedcounts.updated("terminalAppended", 1)
        _checks(s) shouldBe failedchecks
        val terminalreceipt = _receipt(s, session, ":ordered-local-terminal")
        _receipts(s, session) should contain theSameElementsAs (failedreceipts :+ terminalreceipt)
        s.candidateHead shouldBe finalhead
        s.candidateParent shouldBe parent
        s.candidateIndex shouldBe ""
        _retain_snapshot(s, session, "terminal-after-reopen")
        When("a further passive Advance opens that same durable Completed state")
        val passive = cli.advance(session).business[V.GoalPhaseAdvanceResult]
        Then("the identical Terminal has zero native revision history receipt and effect increments")
        passive shouldBe reopened
        _instance(s, session) shouldBe completed
        _effect_counts(s, session) shouldBe failedcounts.updated("terminalAppended", 1)
        _receipts(s, session) should contain theSameElementsAs (failedreceipts :+ terminalreceipt)
        s.candidateHead shouldBe finalhead
        s.candidateIndex shouldBe ""
        val latercalls = s.invocations.dropWhile(_ != failed.invocation).drop(1)
          .filter(_.argv.exists(_.startsWith("org.simplemodeling.textus.SmWorkflow.")))
        latercalls.size shouldBe 2
        latercalls.foreach(value => value.argv should contain("org.simplemodeling.textus.SmWorkflow.GoalPhaseService.advanceGoalPhase"))
        _retain_snapshot(s, session, "terminal-after-passive")
      }

      "retain an actually incomplete Final intent and still Wait after capability restoration without replay" in {
        Given("P65-FINAL126-TERMINAL-01: a separate isolated new setup using the same explicit immutable installation")
        val executable = _required("smworkflow.spec.installedCommand")
        val image = _required("smworkflow.spec.terminalReopenInstallations").toRealPath()
        val (s, cli, session, accepted) = _release_ready("terminal-incomplete-final", executable, image)
        val head = s.candidateHead
        val index = s.candidateIndex
        head shouldBe accepted.work.get.completed.last.commit.identity
        val config = _deny_final(s)
        When("Final saves its genuine intent but its selected commit capability is withheld")
        val failed = cli.advance(session)
        Then("the actual failed call retains an intent without execution committed or local-completion receipts")
        failed.exitCode should not be 0
        val intent = _receipt(s, session, ":intent")
        intent.hcursor.downField("payload").get[String]("typeIdentity") shouldBe Right("GoalPhaseNativeReleaseCommitReceipt")
        Vector(":execution", ":committed", ":ordered-local-completion", ":ordered-local-terminal").foreach { suffix =>
          _receipts(s, session).exists(value => value.hcursor.downField("reference").get[String]("identity").toOption.exists(_.endsWith(suffix)) &&
            (!Set(":execution", ":committed")(suffix) || value.hcursor.downField("payload").get[String]("typeIdentity").toOption
              .contains("GoalPhaseNativeReleaseCommitReceipt"))) shouldBe false
        }
        val retained = _instance(s, session)
        val counts = _effect_counts(s, session)
        counts("finalExecuted") shouldBe 0
        counts("terminalAppended") shouldBe 0
        _retain_snapshot(s, session, "incomplete-final-failure")
        Files.writeString(s.root.resolve(".textus/config.json"), config)
        When("availability is restored and only ordinary Advance reopens the actual retained intent twice")
        val first = cli.advance(session).business[V.GoalPhaseAdvanceResult]
        val second = cli.advance(session).business[V.GoalPhaseAdvanceResult]
        Then("both responses honestly Wait and no candidate receipt or successful effect is fabricated or replayed")
        first.kind shouldBe "Wait"
        second shouldBe first
        _instance(s, session) shouldBe retained
        _effect_counts(s, session) shouldBe counts
        s.candidateHead shouldBe head
        s.candidateIndex shouldBe index
        _receipt(s, session, ":intent") shouldBe intent
        _retain_snapshot(s, session, "incomplete-final-reopened")
      }
    }
  }

  sys.props.get("smworkflow.spec.terminalReopenRetainedFixture").foreach { _ =>
    "Installed finite terminal completion retained tail" should {
      "continue retained terminal refusal through ordinary completion and one passive reopen on the same image" in {
        Given("P65-FINAL126-TERMINAL-01: the explicitly selected isolated fixture whose actual Final succeeded and terminal write failed")
        val executable = _required("smworkflow.spec.installedCommand")
        val image = _required("smworkflow.spec.terminalReopenInstallations").toRealPath()
        val root = _required("smworkflow.spec.terminalReopenRetainedFixture").toRealPath()
        val installation = _read(root.resolve("terminal-installation.json"))
        installation.hcursor.get[String]("installation") shouldBe Right(image.toString)
        installation.hcursor.get[String]("command") shouldBe Right(executable.toString)
        val beforepath = root.resolve("terminal-before-final.json")
        val beforetext = Files.readString(beforepath)
        val before = parse(beforetext).toOption.get
        val session = before.hcursor.get[String]("sessionId").toOption.get
        val originalerrorpath = root.resolve("logs/121.stderr")
        val originaloutputpath = root.resolve("logs/121.stdout.json")
        val originalerror = Files.readString(originalerrorpath)
        val originaloutput = Files.readString(originaloutputpath)
        originalerror should include("SPECIFICATION terminal lifecycle write refused")
        val s = F.reopenReleaseFix(root, TypedSbtHostPlanFixture.normalExecutable, 1000)
        val cli = new Cli(s, executable, image)
        val failednative = _instance(s, session)
        failednative.hcursor.downField("current").get[String]("lifecycle") shouldBe Right("active")
        failednative.hcursor.downField("current").get[String]("progression") shouldBe Right("phase.Closed")
        val intent = _receipt(s, session, ":intent")
        val execution = _receipt(s, session, ":execution")
        val committed = _receipt(s, session, ":committed")
        intent.hcursor.downField("payload").get[String]("typeIdentity") shouldBe Right("GoalPhaseNativeReleaseCommitReceipt")
        execution.hcursor.downField("current").focus shouldBe intent.hcursor.downField("next").focus
        committed.hcursor.downField("current").focus shouldBe execution.hcursor.downField("next").focus
        val intentpayload = intent.hcursor.downField("payload").downField("value").downField("payload").focus.get
        val executionpayload = execution.hcursor.downField("payload").downField("value").downField("payload").focus.get
        val committedpayload = committed.hcursor.downField("payload").downField("value").downField("payload").focus.get
        executionpayload.mapObject(_.add("execution", Json.Null)) shouldBe intentpayload
        committedpayload.mapObject(_.add("commit", Json.Null)) shouldBe executionpayload
        committedpayload.hcursor.downField("execution").get[String]("outcome") shouldBe Right("succeeded")
        committedpayload.hcursor.downField("execution").get[Int]("exitCode") shouldBe Right(0)
        committedpayload.hcursor.downField("execution").get[Vector[String]]("selectedPaths") shouldBe Right(Vector("docs/usage.md"))
        val local = _receipt(s, session, ":ordered-local-completion")
        local.hcursor.downField("payload").get[String]("typeIdentity") shouldBe Right("GoalPhaseLocalCompletionRecord")
        val localpayload = GoalPhasePostCompletionSync.codec.decode(local.hcursor.downField("payload").downField("value").focus.get)
          .fold(error => fail(error), identity)
        localpayload.input.instance shouldBe session
        localpayload.input.plan shouldBe take(B.captureC(None, s.plan))
        intentpayload.hcursor.downField("input").downField("base").focus shouldBe Some(Json.obj(
          "head" -> localpayload.input.base.head.asJson, "tree" -> localpayload.input.base.tree.asJson))
        local.hcursor.downField("next").focus shouldBe Some(failednative)
        val expectedcompleted = localpayload.input.completed.map(value => V.GoalPhaseClosedStep(value.step,
          V.GoalPhaseNativeCommit(value.commit.identity, value.commit.parent, value.commit.tree), value.review,
          admission = value.admission.map(a => GoalPhaseNativeMetadata.record(a.asJson))))
        val parent = localpayload.input.completed.last.commit.identity
        val finalhead = s.candidateHead
        finalhead should not be parent
        s.candidateParent shouldBe parent
        val index = s.candidateIndex
        index shouldBe ""
        committedpayload.hcursor.downField("commit").get[String]("identity") shouldBe Right(finalhead)
        committedpayload.hcursor.downField("commit").get[String]("parent") shouldBe Right(parent)
        committedpayload.hcursor.downField("commit").get[String]("tree") shouldBe Right(localpayload.input.candidate.tree)
        _receipts(s, session).exists(_.hcursor.downField("reference").get[String]("identity").toOption
          .contains(session + ":ordered-local-terminal")) shouldBe false
        val failedcounts = _effect_counts(s, session)
        val originalcounts = before.hcursor.get[Map[String, Int]]("counts").toOption.get
        val originalnative = before.hcursor.get[Vector[Json]]("native").toOption.get
        val originalsubmitted = _submitted_results(originalnative, session)
        originalsubmitted should be > 0
        failedcounts shouldBe originalcounts.updated("submittedResults", originalsubmitted).updated("finalExecuted", 1)
        failedcounts("finalExecuted") shouldBe 1
        failedcounts("terminalAppended") shouldBe 0
        failedcounts("submittedResults") should be > 0
        failedcounts("reviewIssued") should be > 0
        failedcounts("stageExecuted") should be > 0
        val failedreceipts = _receipts(s, session)
        val failedchecks = _checks(s)
        _retain_snapshot(s, session, "terminal-retained-before-reopen")
        When("only the fixture refusal triggers are removed and the same installed image performs one ordinary Advance")
        _allow_terminal(s)
        val reopened = cli.advance(session).business[V.GoalPhaseAdvanceResult]
        Then("the missing Completed append returns Terminal while accepted work and the one actual Final remain unchanged")
        reopened.kind shouldBe "Terminal"
        reopened.completion.get.completed shouldBe expectedcompleted
        val completed = _instance(s, session)
        completed.hcursor.downField("current").get[String]("lifecycle") shouldBe Right("completed")
        completed.hcursor.downField("current").downField("progression").focus shouldBe Some(Json.Null)
        completed.hcursor.downField("current").downField("suspension").focus shouldBe Some(Json.Null)
        val failedhistory = failednative.hcursor.get[Vector[Json]]("history").toOption.get
        val completedhistory = completed.hcursor.get[Vector[Json]]("history").toOption.get
        completedhistory.size shouldBe failedhistory.size + 1
        completedhistory.dropRight(1) shouldBe failedhistory
        completed.hcursor.downField("current").get[Long]("revision").toOption.get shouldBe
          failednative.hcursor.downField("current").get[Long]("revision").toOption.get + 1L
        val terminalreceipt = _receipt(s, session, ":ordered-local-terminal")
        terminalreceipt.hcursor.downField("current").focus shouldBe Some(failednative)
        terminalreceipt.hcursor.downField("next").focus shouldBe Some(completed)
        _receipts(s, session) should contain theSameElementsAs (failedreceipts :+ terminalreceipt)
        _effect_counts(s, session) shouldBe failedcounts.updated("terminalAppended", 1)
        _checks(s) shouldBe failedchecks
        s.candidateHead shouldBe finalhead
        s.candidateParent shouldBe parent
        s.candidateIndex shouldBe index
        _retain_snapshot(s, session, "terminal-retained-after-reopen")
        When("the same image performs one further passive ordinary Advance")
        val passive = cli.advance(session).business[V.GoalPhaseAdvanceResult]
        Then("identical Terminal native history revision receipts and effects survive without setup or Final replay")
        passive shouldBe reopened
        _instance(s, session) shouldBe completed
        _receipts(s, session) should contain theSameElementsAs (failedreceipts :+ terminalreceipt)
        _effect_counts(s, session) shouldBe failedcounts.updated("terminalAppended", 1)
        _checks(s) shouldBe failedchecks
        s.candidateHead shouldBe finalhead
        s.candidateParent shouldBe parent
        s.candidateIndex shouldBe index
        _retain_snapshot(s, session, "terminal-retained-after-passive")
        val applications = s.invocations.filter(_.argv.exists(_.startsWith("org.simplemodeling.textus.SmWorkflow.")))
        applications.size shouldBe 2
        applications.foreach(value => value.argv should contain("org.simplemodeling.textus.SmWorkflow.GoalPhaseService.advanceGoalPhase"))
        Files.readString(beforepath) shouldBe beforetext
        Files.readString(originalerrorpath) shouldBe originalerror
        Files.readString(originaloutputpath) shouldBe originaloutput
      }
    }
  }

  "Installed legacy Release frontier" should {
    "retain one old setup and continue that same Session through the separately selected new image" in {
      val executable = _required("smworkflow.spec.installedCommand")
      val oldimage = _required("smworkflow.spec.releaseRepairOldInstallations").toRealPath()
      val newimage = _required("smworkflow.spec.releaseRepairNewInstallations").toRealPath()
      val locator = _required("smworkflow.spec.releaseRepairLocator")
      val stage = sys.props.getOrElse("smworkflow.spec.releaseRepairStage", throw new IllegalArgumentException("Missing releaseRepairStage"))
      oldimage should not be newimage
      Set("setup", "continue") should contain(stage)
      if (stage == "setup") {
        Given("the explicitly selected old installed runtime and a fresh ordinary repository with a required Phase condition")
        Files.exists(locator) shouldBe false
        val s = F.freshReleaseFix("legacy-release-frontier", TypedSbtHostPlanFixture.normalExecutable, requiredCondition = true)
        val cli = new Cli(s, executable, oldimage)
        val started = cli.call("startGoalPhase", Vector("--input", s.input.noSpaces)).business[V.GoalPhaseStartResult]
        val implementation = cli.advance(started.sessionId).business[V.GoalPhaseAdvanceResult]
        val repo = Path.of(s.host.target.root)
        Files.writeString(repo.resolve("value.txt"), "expected\n")
        Files.createDirectories(repo.resolve("docs"))
        Files.writeString(repo.resolve("docs/usage.md"), "The accepted input is arbitrary text.\n")
        cli.submit(implementation, _result(s, implementation, "completed"), V.WorkflowArtifactReference("fixture-implementation", "1"))
        val review = cli.advance(started.sessionId)
        cli.review(review, _result(s, review.business[V.GoalPhaseAdvanceResult], "accept"), "fixture-step-review")
        val closing = cli.advance(started.sessionId)
        cli.authority(closing, "fixture-step-authority")
        val phase = cli.advance(started.sessionId)
        val integrated = phase.business[V.GoalPhaseAdvanceResult]
        integrated.work.get.kind shouldBe "ReviewPhase"
        integrated.work.get.releaseRepairContract shouldBe None
        _checks(s).size shouldBe 2
        When("an independently admitted fixture reviewer reports the actual required Phase usage-contract blocker")
        val finding = "docs/usage.md must describe exactly expected followed by LF"
        cli.review(phase, _result(s, integrated, "repair", Vector(finding)), "fixture-integrated-review")
        val human = cli.advance(started.sessionId)
        val pending = human.business[V.GoalPhaseAdvanceResult]
        Then("the old runtime stops at its actual unanswered Human and the immutable locator retains its complete accepted prefix")
        pending.kind shouldBe "Human"
        s.candidateHead shouldBe integrated.work.get.completed.last.commit.identity
        val native = _stored(s)
        native.nonEmpty shouldBe true
        Files.writeString(locator, Json.obj("schemaVersion" -> "release-repair-fixture.v1".asJson,
          "root" -> s.root.toString.asJson, "sessionId" -> started.sessionId.asJson,
          "oldInstallations" -> oldimage.toString.asJson, "newInstallations" -> newimage.toString.asJson,
          "phaseOutput" -> phase.invocation.stdout.toString.asJson, "humanOutput" -> human.invocation.stdout.toString.asJson,
          "nativePrefix" -> native.asJson, "checks" -> _checks(s).asJson,
          "head" -> s.candidateHead.asJson, "index" -> s.candidateIndex.asJson).noSpaces, StandardOpenOption.CREATE_NEW)
      } else {
        Given("the immutable locator from the completed old-image setup, with no setup or successful check replay")
        val saved = _read(locator)
        saved.hcursor.get[String]("schemaVersion") shouldBe Right("release-repair-fixture.v1")
        saved.hcursor.get[String]("oldInstallations") shouldBe Right(oldimage.toString)
        saved.hcursor.get[String]("newInstallations") shouldBe Right(newimage.toString)
        val root = Path.of(saved.hcursor.get[String]("root").toOption.get)
        val session = saved.hcursor.get[String]("sessionId").toOption.get
        val previous = sys.props.get("smworkflow.spec.releaseRepairPreviousDescription").map(_ =>
          _required("smworkflow.spec.releaseRepairPreviousDescription"))
        val logstart = sys.props.get("smworkflow.spec.releaseRepairLogStart").map(_.toInt).getOrElse(1000)
        require(logstart > 0 && logstart < Int.MaxValue, "Select a positive finite unused log start")
        val suffix = sys.props.getOrElse("smworkflow.spec.releaseRepairResourceSuffix", "")
        require(suffix.isEmpty || suffix.matches("-[a-z0-9][a-z0-9-]{0,31}"),
          "Select a restricted literal Resource suffix")
        val s = F.reopenReleaseFix(root, TypedSbtHostPlanFixture.normalExecutable, logstart)
        val cli = new Cli(s, executable, newimage, suffix)
        val oldchecks = saved.hcursor.get[Vector[String]]("checks").toOption.get
        val oldnative = saved.hcursor.get[Vector[String]]("nativePrefix").toOption.get
        val oldphase = take(summon[ValueReader[V.GoalPhaseAdvanceResult]].readC(take(new RecordDecoder().json(
          Files.readString(Path.of(saved.hcursor.get[String]("phaseOutput").toOption.get))))))
        val head = s.candidateHead
        val index = s.candidateIndex
        head shouldBe saved.hcursor.get[String]("head").toOption.get
        index shouldBe saved.hcursor.get[String]("index").toOption.get
        _checks(s) shouldBe oldchecks
        val nativeprefix = _stored(s)
        oldnative.foreach(value => nativeprefix should contain(value))
        val (descriptionpath, description) = previous match {
          case Some(path) =>
            require(path.toRealPath() == path && path.startsWith(root.resolve("logs")) && Files.isRegularFile(path),
              "Select the actual retained description stdout under this fixture's logs")
            (path, take(summon[ValueReader[V.GoalPhaseReleaseRepairContinuationDescriptionResult]].readC(
              take(new RecordDecoder().json(Files.readString(path))))))
          case None =>
            val described = cli.resource(P.describeOperation, "continue-description-input", V.GoalPhaseReleaseRepairContinuationDescriptionInput(session))
            (described.invocation.stdout, described.business[V.GoalPhaseReleaseRepairContinuationDescriptionResult])
        }
        val target = take(P.targetC(description.target))
        description.sessionId shouldBe session
        target.sessionId shouldBe session
        target.originalRequest.hcursor.downField("payload").downField("releaseRepairContract").focus shouldBe None
        target.originalResult.outcome shouldBe "repair"
        F.publicPlan(target.plan) shouldBe oldphase.work.get.selectedPlan
        target.completed.map(_.commit.identity) shouldBe oldphase.work.get.completed.map(_.commit.identity)
        target.base.head shouldBe oldphase.work.get.originalBase.head
        val reference = V.WorkflowArtifactReference("specification-new-release-human", "1")
        val input = V.GoalPhaseReleaseRepairContinuationRecoveryInput(session, description.target, "ContinueReleaseRepair",
          "SPECIFICATION-OWNED actual Human directs the complete saved blocker batch within the original Plan", reference)
        val unchanged = _stored(s)
        When("Defer missing evidence and altered targets are submitted without repeating successful setup")
        cli.resource(P.recoveryOperation, "continue-defer-input", input.copy(choice = "Defer"))
          .business[V.GoalPhaseReleaseRepairContinuationRecoveryResult].accepted shouldBe false
        cli.resource(P.recoveryOperation, "continue-no-evidence-input", input).exitCode should not be 0
        cli.resource(P.recoveryOperation, "continue-foreign-input", input.copy(sessionId = "foreign-session")).exitCode should not be 0
        cli.resource(P.recoveryOperation, "continue-stale-input", input.copy(target = P.targetRecord(target.copy(pendingRevision = "99999"))))
          .exitCode should not be 0
        val configpath = root.resolve(".textus/config.json")
        val originalconfig = Files.readString(configpath)
        val driftedhost = s.host.copy(goal = s.host.goal.copy(closure = s.host.goal.closure.copy(programs =
          Vector(GoalPhaseNativeClosure.StepProgram("step-1", 1), GoalPhaseNativeClosure.StepProgram("phase-release", 3)))))
        Files.writeString(configpath, parse(originalconfig).toOption.get.mapObject(_.add("sm-workflow.runtime.definition",
          Json.fromString(ThreeWorkflowRuntimeParameters.encode(driftedhost).noSpaces))).noSpaces)
        cli.resource(P.recoveryOperation, "continue-host-drift-input", input).exitCode should not be 0
        Files.writeString(configpath, originalconfig)
        _stored(s) should contain theSameElementsAs unchanged
        s.candidateHead shouldBe head
        s.candidateIndex shouldBe index
        _checks(s) shouldBe oldchecks
        val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
        cli.prepare("prepareEvidence", V.WorkflowEvidencePreparationInput("GoalPhase", "release-repair-continuation",
          descriptionpath.toUri.toASCIIString, _save(s, "continue-actual-direction", input, suffix),
          _save(s, "continue-direction-reference", reference, suffix), _save(s, "continue-direction-execution", execution, suffix),
          "normal-development-host", Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some("participant")))
        val admitted = cli.resource(P.recoveryOperation, "continue-admission-input", input)
          .business[V.GoalPhaseReleaseRepairContinuationRecoveryResult]
        Then("recovery records one distinct direction and performs no work Git checks or commit")
        admitted.accepted shouldBe true
        s.candidateHead shouldBe head
        s.candidateIndex shouldBe index
        _checks(s) shouldBe oldchecks
        cli.resource(P.recoveryOperation, "continue-duplicate-input", input).exitCode should not be 0
        Given("managed candidate content changes after admission but before the first separate Advance")
        val selected = Path.of(s.host.target.root).resolve("docs/usage.md")
        val originalcontent = Files.readString(selected)
        Files.writeString(selected, "Specification-owned unreviewed candidate drift\n")
        cli.advance(session).exitCode should not be 0
        Files.writeString(selected, originalcontent)
        _checks(s) shouldBe oldchecks
        val repair = cli.advance(session).business[V.GoalPhaseAdvanceResult]
        repair.kind shouldBe "Request"
        repair.work.get.kind shouldBe "RepairStep"
        repair.work.get.stepIdentity shouldBe None
        repair.work.get.completed shouldBe oldphase.work.get.completed
        repair.repairProgress shouldBe Some(V.GoalPhaseRepairProgress(1, 1, target.repairProgress.limit, None, None, None))
        cli.advance(session).business[V.GoalPhaseAdvanceResult] shouldBe repair
        _checks(s) shouldBe oldchecks
        val open = repair.work.get.issues.get.issues.filter(_.status != "RESOLVED")
        open.map(_.identity) shouldBe target.findings.map(_.issueIdentity)
        val repo = Path.of(s.host.target.root)
        Files.writeString(repo.resolve("docs/usage.md"), "The accepted input is exactly expected followed by LF.\n")
        val fixed = V.GoalPhaseIssueReport(open.map(issue => V.GoalPhaseIssueDisposition(issue.identity, "FIXED",
          "Corrected the exact original usage-contract blocker", None)))
        cli.submit(repair, _result(s, repair, "completed", issues = Some(fixed)), V.WorkflowArtifactReference("fixture-release-fix", "1"))
        val rereviewcall = cli.advance(session)
        val rereview = rereviewcall.business[V.GoalPhaseAdvanceResult]
        Then("only a fresh affected Phase check precedes scoped review with the original integrated report")
        rereview.work.get.kind shouldBe "ReviewPhase"
        rereview.work.get.completed shouldBe oldphase.work.get.completed
        rereview.work.get.review.get.priorReviews.exists(value => take(value.toJsonStringC).contains("fixture-integrated-review")) shouldBe true
        _checks(s).size shouldBe oldchecks.size + 1
        val resolved = V.GoalPhaseIssueReport(open.map(issue => V.GoalPhaseIssueDisposition(issue.identity, "RESOLVED",
          "Scoped independent fixture review confirms the required condition", None)))
        cli.review(rereviewcall, _result(s, rereview, "accept", issues = Some(resolved)), "fixture-release-rereview")
        val finalwait = cli.advance(session)
        finalwait.business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
        cli.authority(finalwait, "fixture-final-phase-authority")
        val terminal = cli.advance(session).business[V.GoalPhaseAdvanceResult]
        Then("Final records one actual Release commit and retains every original accepted result and unanswered question")
        terminal.kind shouldBe "Terminal"
        terminal.completion.get.completed shouldBe oldphase.work.get.completed
        s.candidateParent shouldBe head
        s.candidateHead should not be head
        s.candidateIndex shouldBe ""
        _checks(s).size shouldBe oldchecks.size + 1
        val retained = _stored(s)
        val originaldocuments = oldnative.filter(value => parse(value.stripPrefix("cncf.workflow-json.v1:")).toOption.get
          .hcursor.get[String]("kind").toOption.exists(kind => Set("RESULT", "DECISION")(kind)))
        originaldocuments should not be empty
        originaldocuments.foreach(value => retained should contain(value))
        cli.advance(session).business[V.GoalPhaseAdvanceResult].kind shouldBe "Terminal"
        _checks(s).size shouldBe oldchecks.size + 1
      }
    }
  }
}
