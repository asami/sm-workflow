package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import org.goldenport.cncf.processexecution.SbtOperation
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalTransitionJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.record.RecordPresentable
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import RepositorySyncNativeMergeValidation as C
import RepositorySyncNativeProgressStore as S
import RepositorySyncValidationContinuation as N
import SplitPhaseExplicitPartitionFixture.take

/** Two explicitly selected publication stages retain actual old successful
  * checks in four tiny repositories. Proposals, lifecycle answers and independent
  * review are specification-owned data, never live provider acceptance.
  * @since Nov. 3, 2026
  * @version Nov. 3, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryRequiredLintContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with RepositorySyncMaintenanceFixture {
  private val _policy = "sm-workflow.repository-sync-required-lint-continuation.v2"
  private val _variants = Vector("success", "failure", "timeout", "permission-denied")
  private val _resolved = "resolved product input\n"
  private val _old_checks = Vector(C.Check("compile1", 1, WorkflowSbtValidation.Kind.Compile),
    C.Check("test2", 2, WorkflowSbtValidation.Kind.Test))
  private val _checks = _old_checks :+ C.Check("lint3", 3, WorkflowSbtValidation.Kind.FixCheck)
  private lazy val _catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(RepositorySyncDefinitionBinding.bindC))))

  private final case class Episode(variant: String, root: String, normalJson: Json, plan: Json, session: String,
    originalRecord: String, head: String, indexTree: String, mergeHead: String,
    refusalStdout: String, refusalStderr: String, formalReceipts: Vector[(String, String, String)],
    invocations: Vector[String], commands: Vector[String])
  private final case class Live(scenario: F.Scenario, session: String) {
    def root: Path = scenario.root
    def repository: Path = Path.of(scenario.host.target.root)
    def call(service: String, operation: String, parameters: Vector[String]): F.Result = scenario.callInstalled(
      _installed, service, operation, parameters, None, Some(root.resolve(".textus/assembly.yaml")))
    def repositoryCall(operation: String, parameters: Vector[String]): F.Result = call("RepositorySyncService", operation, parameters)
    def advance(): F.Result = repositoryCall("advanceRepositorySync", Vector("--sessionId", session))
  }
  private def _installed: Path = {
    val path = Path.of(sys.props.getOrElse("smworkflow.spec.installedCommand", fail("Select the normal installed command explicitly")))
    path.isAbsolute shouldBe true
    path.normalize shouldBe path
    Files.isRegularFile(path) shouldBe true
    Files.isExecutable(path) shouldBe true
    path
  }
  private def _episode_path: Path = {
    val path = Path.of(sys.props.getOrElse("smworkflow.spec.requiredLintEpisode",
      fail("Select -Dsmworkflow.spec.requiredLintEpisode=<absolute private evidence file>")))
    path.isAbsolute shouldBe true
    path.normalize shouldBe path
    Files.isDirectory(path.getParent) shouldBe true
    path
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _save(root: Path, name: String, text: String): String = {
    val path = root.resolve(name)
    Files.writeString(path, text, UTF_8, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _lines(root: Path, name: String): Vector[String] = {
    val path = root.resolve(name)
    if (Files.exists(path)) Files.readString(path).linesIterator.filter(_.nonEmpty).toVector else Vector.empty
  }
  private def _episodes: Vector[Episode] = if (Files.exists(_episode_path))
    parse(Files.readString(_episode_path)).toOption.get.as[Vector[Episode]].toOption.get else Vector.empty
  private def _retain(episodes: Vector[Episode]): Unit = {
    val path = _episode_path
    val temporary = Files.createTempFile(path.getParent, "required-lint-", ".json")
    Files.setPosixFilePermissions(temporary, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
    Files.writeString(temporary, episodes.asJson.noSpaces, UTF_8)
    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
  }

  /** Read actual managed rows, never create or rewrite native evidence. */
  private def _stored(root: Path): Vector[String] = {
    val connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + root.resolve("workflow.sqlite").toUri.toASCIIString + "?mode=ro",
      new java.util.Properties())
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
            Option(rows.getString(column)).filter(_.startsWith("cncf.workflow-json.v1:"))
              .foreach(raw => values += raw.stripPrefix("cncf.workflow-json.v1:"))
          } } finally rows.close()
          values.result()
        }
      } finally statement.close()
    } finally connection.close()
  }
  private def _current(live: Live): InstanceRecord = {
    val selected = _stored(live.root).flatMap(WorkflowInstanceRecordJsonV1.decodeC(_, _catalog).toOption)
      .filter(_.identity.value == live.session)
    selected.size shouldBe 1
    selected.head
  }
  private def _receipts(live: Live) = _stored(live.root).flatMap(raw =>
    WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, S.codec).toOption).filter(_.next.identity.value == live.session)
  private def _formal(live: Live): Vector[(String, String, String)] = _receipts(live).filter(receipt =>
    receipt.reference.identity.contains("validation-result-") && receipt.payload.value.validation.exists(
      _.receipts.lastOption.exists(_.sbt.nonEmpty))).map(receipt => (receipt.reference.identity,
        receipt.reference.revision, receipt.payload.value.validation.get.receipts.last.asJson.noSpaces)).sortBy(_._1)
  private def _snapshot(live: Live) = (take(WorkflowInstanceRecordJsonV1.encodeC(_current(live))),
    git(live.root, "-C", live.repository.toString, "rev-parse", "HEAD"),
    git(live.root, "-C", live.repository.toString, "write-tree"),
    git(live.root, "-C", live.repository.toString, "rev-parse", "MERGE_HEAD"),
    git(live.root, "-C", live.repository.toString, "status", "--porcelain"), _formal(live),
    _lines(live.root, "sbt-invocations.txt"), _lines(live.root, "sbt-command-lines.txt"),
    _lines(live.root, "lint-entered.txt"))
  private def _reopen(episode: Episode): Live = {
    val root = Path.of(episode.root)
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(episode.normalJson))
    val plan = episode.plan.as[GoalPhaseOrderedProgression.Plan].toOption.get
    val logs = Files.list(root.resolve("logs"))
    val next = try { math.max(1000, logs.filter(path => Files.isRegularFile(path) &&
      path.getFileName.toString.matches("[0-9]+\\.(stdout\\.json|stderr)"))
      .mapToInt(path => path.getFileName.toString.takeWhile(_.isDigit).toInt).max().orElse(999) + 1)
    } finally logs.close()
    Live(F.reopenInstalledRepository(root, host, plan, next), episode.session)
  }

  private def _configure(scenario: F.Scenario, lint: Boolean, timeout: Boolean = false, denyLint: Boolean = false): Unit = {
    val root = scenario.root
    val selected = if (lint) _checks else _old_checks
    val paths = Vector("value.txt", "src")
    val impact = RepositorySyncProgramImpact.Policy(selected.map(check => RepositorySyncProgramImpact.Coverage(
      check, paths, paths, "Actual changed product inputs require this ordinary fixed check")), selected, paths,
      "Changed Scala behavior requires full selected product validation", Vector.empty)
    val base = process_plan(root, "Equal", scenario.host.goal)
    def _array_(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
    val operations = Vector(SbtOperation.Compile, SbtOperation.Test) ++ Option.when(lint)(SbtOperation.FixCheck)
    val plans = operations.zipWithIndex.map { (operation, index) => TypedSbtHostPlanFixture.plan(root,
      TypedSbtHostPlanFixture.normalExecutable, Vector(index + 1), operation = operation,
      executionTimeoutMillis = if (timeout && operation == SbtOperation.FixCheck) 5L else 180000L) }
    val programs = plans.flatMap(_array_(_, "sbtPrograms"))
    val lintPrograms = programs.filter(_.hcursor.get[String]("operation").toOption.contains("fix-check"))
    val deniedCapabilities = if (denyLint) lintPrograms.map(_.hcursor.get[Json]("capability").toOption.get).toSet
      else Set.empty[Json]
    val capabilities = programs.map(_.hcursor.get[Json]("capability").toOption.get).filterNot(deniedCapabilities)
    val plan = base.mapObject(fields => fields.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues((_array_(base, "workspaces") ++ plans.flatMap(_array_(_, "workspaces"))).distinct))
      .add("workspaceGrants", Json.fromValues((_array_(base, "workspaceGrants") ++ plans.flatMap(_array_(_, "workspaceGrants"))).distinct))
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(existing => (existing.filterNot(deniedCapabilities) ++ capabilities).distinct)))))))
    if (denyLint) {
      lintPrograms.size shouldBe 1
      _array_(plan, "sbtPrograms").filter(_.hcursor.get[String]("operation").toOption.contains("fix-check")) shouldBe lintPrograms
      val granted = _array_(plan, "grants").flatMap(_.hcursor.get[Vector[Json]]("capabilities").toOption.get)
      deniedCapabilities.foreach(capability => granted should not contain capability)
      capabilities.foreach(capability => granted should contain(capability))
    }
    val file = root.resolve(".textus/config.json")
    val config = parse(Files.readString(file)).toOption.get
    val parameters = maintenance_parameters() ++ Map("textus.subsystem.process-execution.plan" -> plan.noSpaces,
      "sm-workflow.repository-sync.program-inputs" -> impact.asJson.noSpaces,
      "textus.resource.url.file.roots" -> root.toString)
    Files.writeString(file, config.mapObject(fields => parameters.foldLeft(fields.remove("sm-workflow.runtime.definition")) {
      case (prior, (key, value)) => prior.add(key, Json.fromString(value))
    }).noSpaces)
  }
  private def _scenario(variant: String): F.Scenario = {
    val scenario = F.fresh("installed-required-lint-" + variant, sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable),
      bootstrapSelectedInput = false)
    val root = scenario.root
    val repository = root.resolve("repository")
    Files.createDirectories(repository.resolve("src/main/scala"))
    Files.writeString(repository.resolve("src/main/scala/LintInput.scala"), "object LintInput { val value = 0 }\n")
    Files.writeString(repository.resolve("src/test/scala/ProductCheck.scala"), """object ProductCheck {
      |  def main(args: Array[String]): Unit = {
      |    assert(java.nio.file.Files.readString(java.nio.file.Paths.get("value.txt")) == "resolved product input\n")
      |    assert(LintInput.value == 1)
      |    println("ACTUAL_REQUIRED_LINT_PRODUCT_TEST_PASSED")
      |  }
      |}
      |""".stripMargin)
    Files.writeString(repository.resolve("project/plugins.sbt"), "addSbtPlugin(\"ch.epfl.scala\" % \"sbt-scalafix\" % \"0.12.1\")\n")
    Files.writeString(repository.resolve(".scalafix.conf"), "rules = [DisableSyntax]\nDisableSyntax.noFinalize = true\n")
    Files.writeString(repository.resolve("build.sbt"), """
      |lazy val requiredLintMarker = taskKey[Unit]("Record entry to the ordinary selected lint alias")
      |requiredLintMarker := IO.append(file(__LINT__), java.lang.ProcessHandle.current().pid().toString + "\n")
      |addCommandAlias("fixCheck", ";requiredLintMarker;Compile / scalafix --check")
      |Global / onLoad := {
      |  val original = (Global / onLoad).value
      |  state => {
      |    val loaded = original(state)
      |    IO.append(file(__INVOCATIONS__), java.lang.ProcessHandle.current().pid().toString + "\n")
      |    IO.append(file(__COMMANDS__), state.remainingCommands.map(_.commandLine).mkString("", "\n", "\n"))
      |    loaded
      |  }
      |}
      |""".stripMargin.replace("__LINT__", root.resolve("lint-entered.txt").toString.asJson.noSpaces)
      .replace("__INVOCATIONS__", root.resolve("sbt-invocations.txt").toString.asJson.noSpaces)
      .replace("__COMMANDS__", root.resolve("sbt-command-lines.txt").toString.asJson.noSpaces), StandardOpenOption.APPEND)
    git(root, "-C", repository.toString, "add", "--", "src", "project/plugins.sbt", ".scalafix.conf", "build.sbt")
    git(root, "-C", repository.toString, "commit", "-m", "Tiny ordinary Compile Test and real scalafix inputs")
    git(root, "-C", repository.toString, "push", root.resolve("upstream.git").toString, "HEAD:refs/heads/main")
    val peer = root.resolve("peer")
    git(root, "clone", "--branch", "main", "--", root.resolve("upstream.git").toString, peer.toString)
    Files.writeString(peer.resolve("value.txt"), "remote product input\n")
    val violation = if (variant == "failure") "class FinalizerInput { override def finalize(): Unit = () }\n" else ""
    Files.writeString(peer.resolve("src/main/scala/LintInput.scala"), "object LintInput { val value = 1 }\n" + violation)
    git(root, "-C", peer.toString, "add", "--", "value.txt", "src/main/scala/LintInput.scala")
    git(root, "-C", peer.toString, "commit", "-m", "Remote actual Scala input")
    git(root, "-C", peer.toString, "push", root.resolve("upstream.git").toString, "HEAD:refs/heads/main")
    Files.writeString(repository.resolve("value.txt"), "local product input\n")
    git(root, "-C", repository.toString, "add", "--", "value.txt")
    git(root, "-C", repository.toString, "commit", "-m", "Local conflicting input")
    _configure(scenario, lint = false)
    scenario
  }
  private def _resolved_session(scenario: F.Scenario): Live = {
    val client = Live(scenario, "")
    val started = client.repositoryCall("startRepositorySync", Vector("--input",
      "{\"repositoryIdentity\":\"required-lint-fixture\",\"syncMode\":\"FULL_STATE\"}")).business[V.RepositorySyncStartResult]
    val live = Live(scenario, started.sessionId)
    live.advance().business[V.RepositorySyncAdvanceResult].condition shouldBe Some("MergeConflicted")
    val issued = live.advance().business[V.RepositorySyncAdvanceResult]
    issued.conflict.get.conflicts.map(_.path) shouldBe Vector("value.txt")
    val result = V.RepositorySyncConflictResult("resolved", issued.conflict.get.conflicts.map(value =>
      V.RepositorySyncConflictResolution(value.identity, _resolved)), "Specification-owned conflict resolution")
    val reference = V.WorkflowArtifactReference("fixture-required-lint-conflict", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-conflict-author"), None, None)
    live.repositoryCall("submitRepositorySyncWorkResult", Vector("--sessionId", live.session, "--result", _json(result),
      "--resultReference", _json(reference), "--executionEvidence", _json(execution)))
      .business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
    live
  }
  private def _retained_prefix(live: Live, episode: Episode): Unit = {
    _formal(live).filter(episode.formalReceipts.contains) shouldBe episode.formalReceipts
    _lines(live.root, "sbt-invocations.txt").take(episode.invocations.size) shouldBe episode.invocations
    val commands = _lines(live.root, "sbt-command-lines.txt")
    commands.take(episode.commands.size) shouldBe episode.commands
    commands.count(_ == "compile") shouldBe episode.commands.count(_ == "compile")
    commands.count(_ == "test") shouldBe episode.commands.count(_ == "test")
    _current(live).history.take(take(WorkflowInstanceRecordJsonV1.decodeC(episode.originalRecord, _catalog)).history.size) shouldBe
      take(WorkflowInstanceRecordJsonV1.decodeC(episode.originalRecord, _catalog)).history
  }
  private def _prepare(live: Live, described: F.Result, answer: V.RepositorySyncValidationContinuationAnswer,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence): Unit = {
    val directory = Files.createTempDirectory(live.root, "fixture-required-lint-direction-")
    val input = V.WorkflowEvidencePreparationInput("RepositorySync", "validation-continuation",
      described.invocation.stdout.toUri.toASCIIString, _save(directory, "answer.json", _json(answer)),
      _save(directory, "reference.json", _json(reference)), _save(directory, "execution.json", _json(execution)),
      "normal-development-host", java.time.Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some("participant"))
    val prepared = live.call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(input)))
    withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
    live.scenario.selectPreparedImport(prepared)
  }
  private def _review_and_converge(live: Live, output: F.Result, episode: Episode): Unit = {
    val issued = output.business[V.RepositorySyncAdvanceResult]
    issued.review should not be empty
    val result = V.RepositorySyncNativeMergeReviewResult("accept", "Specification-owned independent review of both actual parent diffs", Vector.empty)
    val reference = V.WorkflowArtifactReference("fixture-required-lint-independent-review", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-independent-reviewer"), None, None)
    val directory = Files.createTempDirectory(live.root, "fixture-required-lint-review-")
    val input = V.WorkflowEvidencePreparationInput("RepositorySync", "review", output.invocation.stdout.toUri.toASCIIString,
      _save(directory, "review.json", _json(result)), _save(directory, "reference.json", _json(reference)),
      _save(directory, "execution.json", _json(execution)), "normal-development-host", java.time.Instant.now().toString,
      "entity:smworkflow.cli.host_evidence", None)
    val prepared = live.call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(input)))
    prepared.exitCode shouldBe 0
    live.scenario.selectPreparedImport(prepared)
    live.repositoryCall("submitNativeMergeReview", Vector("--sessionId", live.session, "--result", _json(result),
      "--resultReference", _json(reference), "--executionEvidence", _json(execution)))
      .business[V.RepositorySyncNativeMergeReviewSubmissionResult].accepted shouldBe true
    val terminal = live.advance().business[V.RepositorySyncAdvanceResult]
    terminal.kind shouldBe "Terminal"
    _current(live).lifecycle shouldBe Lifecycle.Completed
    val tip = git(live.root, "-C", live.repository.toString, "rev-parse", "HEAD")
    git(live.root, "-C", live.repository.toString, "show", "-s", "--format=%P", "HEAD").split(" ").toVector shouldBe
      Vector(episode.head, episode.mergeHead)
    git(live.root, "--git-dir=" + live.root.resolve("upstream.git"), "rev-parse", "refs/heads/main") shouldBe tip
    git(live.root, "-C", live.repository.toString, "rev-parse", "refs/remotes/origin/main") shouldBe tip
    git(live.root, "-C", live.repository.toString, "status", "--porcelain") shouldBe ""
    Files.exists(live.repository.resolve(".git/MERGE_HEAD")) shouldBe false
    val completed = _current(live)
    live.advance().business[V.RepositorySyncAdvanceResult] shouldBe terminal
    _current(live) shouldBe completed
    _retained_prefix(live, episode)
    _lines(live.root, "sbt-invocations.txt").size shouldBe episode.invocations.size + 1
  }

  "Installed required-lint continuation" should {
    "capture REQUIRED-LINT actual successful checks and postcheck refusal" in {
      Given("the old normal published CAR and four tiny independent real Git SQLite and SBT fixture repositories")
      var retained = _episodes
      retained.map(_.variant).distinct shouldBe retained.map(_.variant)
      retained.foreach { episode =>
        val live = _reopen(episode)
        take(WorkflowInstanceRecordJsonV1.encodeC(_current(live))) shouldBe episode.originalRecord
        _formal(live) shouldBe episode.formalReceipts
        _lines(live.root, "sbt-invocations.txt") shouldBe episode.invocations
      }
      for (variant <- _variants if !retained.exists(_.variant == variant)) {
        When(s"normal Start Submit and Advance run actual Compile and Test once for the $variant episode")
        val live = _resolved_session(_scenario(variant))
        val refusal = live.advance()
        Then("the old actual Scala applicability guard refuses after genuine successful checks and the completed episode is immediately retained")
        refusal.exitCode should not be 0
        refusal.invocation.logs should include("actual Scala merge paths cannot claim lint non-applicability")
        val current = _current(live)
        current.currentProgression shouldBe Some(ProgressionReference("sync.MergeValidated"))
        current.suspension shouldBe None
        val formal = _formal(live)
        formal.map(value => parse(value._3).toOption.get.as[C.Receipt].toOption.get).map(receipt =>
          receipt.identity -> receipt.sbt.get.outcome).toSet shouldBe _old_checks.map(check =>
            check.identity -> WorkflowSbtValidation.Outcome.Succeeded).toSet
        _lines(live.root, "sbt-invocations.txt").size shouldBe 2
        _lines(live.root, "lint-entered.txt") shouldBe empty
        val episode = Episode(variant, live.root.toString, ThreeWorkflowRuntimeParameters.encode(live.scenario.host),
          live.scenario.plan.asJson, live.session, take(WorkflowInstanceRecordJsonV1.encodeC(current)),
          git(live.root, "-C", live.repository.toString, "rev-parse", "HEAD"),
          git(live.root, "-C", live.repository.toString, "write-tree"),
          git(live.root, "-C", live.repository.toString, "rev-parse", "MERGE_HEAD"),
          refusal.invocation.stdout.toString, refusal.invocation.stderr.toString, formal,
          _lines(live.root, "sbt-invocations.txt"), _lines(live.root, "sbt-command-lines.txt"))
        retained = retained :+ episode
        _retain(retained)
      }
      retained.map(_.variant) shouldBe _variants
    }

    for (variant <- _variants) {
      s"continue REQUIRED-LINT $variant in its exact retained Session" in {
        Given(s"the newly published normal CAR and the original $variant Session without another Start or accepted result Submit")
        val episode = _episodes.find(_.variant == variant).getOrElse(fail("Capture all four episodes before new publication"))
        val live = _reopen(episode)
        take(WorkflowInstanceRecordJsonV1.encodeC(_current(live))) shouldBe episode.originalRecord
        _formal(live) shouldBe episode.formalReceipts
        _lines(live.root, "sbt-invocations.txt") shouldBe episode.invocations
        git(live.root, "-C", live.repository.toString, "write-tree") shouldBe episode.indexTree
        _configure(live.scenario, lint = true, timeout = variant == "timeout", denyLint = variant == "permission-denied")
        val initial = _snapshot(live)
        When("public describe selects the actual v2 retained successful-check target under the corrected ordinary host")
        val described = live.repositoryCall(N.describeOperation, Vector("--sessionId", live.session))
        val description = described.business[V.RepositorySyncValidationContinuationDescriptionResult]
        val target = take(description.target.toJsonStringC)
        Then("the exact v2 target and its two choices are described without native or check effects")
        parse(target).toOption.get.hcursor.get[String]("continuation_policy") shouldBe Right(_policy)
        description.choices shouldBe Vector("AuthorizeMissingRequiredLint", "Defer")
        _snapshot(live) shouldBe initial
        val reference = V.WorkflowArtifactReference("fixture-required-lint-direction-" + variant, "1")
        val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
        val answer = V.RepositorySyncValidationContinuationAnswer("AuthorizeMissingRequiredLint",
          "Fixture-owned direction adds only missing lint; no old UNKNOWN execution is accepted", false, false)
        val input = V.RepositorySyncValidationContinuationAdoptionInput(live.session, description.target, answer.choice,
          answer.rationale, reference, false, false, execution)
        def _adopt_(value: V.RepositorySyncValidationContinuationAdoptionInput): F.Result =
          live.repositoryCall(N.adoptionOperation, Vector("--input", _json(value)))
        Vector(input, input.copy(sessionId = "foreign-session"), input.copy(priorExecutionEnded = true),
          input.copy(acceptOriginalUncertainty = true), input.copy(executionEvidence = execution.copy(workerIdentity = Some("other"))))
          .foreach { value =>
            When("missing evidence crossed Session participant or v1 lifecycle flags are presented")
            val refused = _adopt_(value)
            Then("adoption refuses and retains the original native record candidate and invocation counters")
            refused.exitCode should not be 0
            _snapshot(live) shouldBe initial
          }
        val stalewire = parse(_json(input)).toOption.get.mapObject(fields => fields.add("target",
          fields("target").get.mapObject(_.add("frontier_revision", "999999".asJson))))
        When("adoption receives a stale described frontier")
        val stalerefusal = live.repositoryCall(N.adoptionOperation, Vector("--input", stalewire.noSpaces))
        Then("the stale direction refuses without changing the retained candidate or evidence")
        stalerefusal.exitCode should not be 0
        _snapshot(live) shouldBe initial
        val deferred = answer.copy(choice = "Defer")
        val deferreference = reference.copy(identity = reference.identity + "-defer")
        When("the fixture separately prepares and adopts a false false Defer answer")
        _prepare(live, described, deferred, deferreference, execution.copy(references = Vector(deferreference)))
        val deferresult = _adopt_(input.copy(choice = "Defer", reference = deferreference,
          executionEvidence = execution.copy(references = Vector(deferreference))))
          .business[V.RepositorySyncValidationContinuationAdoptionResult]
        Then("Defer accepts no direction and leaves the original native and Git state unchanged")
        deferresult.accepted shouldBe false
        _snapshot(live) shouldBe initial
        When("normal passive evidence preparation binds the exact false false answer but adoption crosses its reference revision")
        _prepare(live, described, answer, reference, execution)
        val crossedreference = _adopt_(input.copy(reference = reference.copy(revision = "2")))
        Then("the changed evidence reference refuses without appending a direction")
        crossedreference.exitCode should not be 0
        _snapshot(live) shouldBe initial
        When("ordinary adoption receives the exact independently prepared false false direction")
        val adopted = _adopt_(input).business[V.RepositorySyncValidationContinuationAdoptionResult]
        Then("only its direction is stored while original successful checks and invocation counts remain retained")
        adopted.accepted shouldBe true
        _retained_prefix(live, episode)
        _lines(live.root, "sbt-invocations.txt") shouldBe episode.invocations
        When("one normal Advance requests only the newly selected fixed CNCF lint check under actual current effect permission")
        val output = live.advance()
        Then("the original successful receipts remain retained and Compile and Test are never replayed")
        _retained_prefix(live, episode)
        val added = _formal(live).filterNot(episode.formalReceipts.contains)
        if (variant == "permission-denied") {
          output.exitCode should not be 0
          output.invocation.logs should include("process.execution.capability-grant")
          added shouldBe empty
          _formal(live) shouldBe episode.formalReceipts
          _lines(live.root, "sbt-invocations.txt") shouldBe episode.invocations
          _lines(live.root, "sbt-command-lines.txt") shouldBe episode.commands
          _lines(live.root, "lint-entered.txt") shouldBe empty
          val intents = _receipts(live).filter(receipt => receipt.reference.identity.contains(":native-required-lint-") &&
            receipt.reference.identity.contains("validation-intent-"))
          intents.size shouldBe 1
          val retained = intents.head.payload.value.validation.get
          retained.selected shouldBe _checks
          retained.receipts.map(_.identity) shouldBe Vector("index-local", "index-remote") ++ _old_checks.map(_.identity)
          _current(live).currentProgression shouldBe Some(ProgressionReference("sync.ValidationChecking"))
          val denied = _snapshot(live)
          When("normal Advance collects the permission-denied retained lint intent without any restored grant")
          val repeated = live.advance()
          Then("the same actual intent and original result rows remain without another effect or result")
          repeated.business[V.RepositorySyncAdvanceResult].kind shouldBe "Wait"
          _snapshot(live) shouldBe denied
          _retained_prefix(live, episode)
        } else {
          added.size shouldBe 1
          val receipt = parse(added.head._3).toOption.get.as[C.Receipt].toOption.get
          receipt.identity shouldBe "lint3"
          receipt.sbt.get.kind shouldBe WorkflowSbtValidation.Kind.FixCheck
          receipt.processObservation should not be empty
          variant match {
          case "success" =>
            receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
            _lines(live.root, "lint-entered.txt").size shouldBe 1
            _review_and_converge(live, output, episode)
          case "failure" =>
            receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
            receipt.exitCode.exists(_ != 0) shouldBe true
            _lines(live.root, "lint-entered.txt").size shouldBe 1
            val issued = output.business[V.RepositorySyncAdvanceResult]
            issued.repair.get.validationFailure shouldBe Some("lint3")
            issued.repair.get.review.changedPaths should contain("src/main/scala/LintInput.scala")
            _lines(live.root, "sbt-invocations.txt").size shouldBe episode.invocations.size + 1
            val failed = _snapshot(live)
            live.advance().business[V.RepositorySyncAdvanceResult] shouldBe issued
            _snapshot(live) shouldBe failed
          case "timeout" =>
            receipt.termination shouldBe "timed-out"
            receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Incomplete
            _lines(live.root, "sbt-invocations.txt").size should be <= episode.invocations.size + 1
            val unknown = _snapshot(live)
            live.advance()
            _snapshot(live) shouldBe unknown
            live.advance()
            _snapshot(live) shouldBe unknown
          case _ => fail("Unknown fixture variant")
          }
        }
      }
    }

    "collect REQUIRED-LINT retained failed check without replay" in {
      Given("the original failure Session after its single genuine failed lint check and before ordinary repair collection")
      val episode = _episodes.find(_.variant == "failure").getOrElse(fail("Retain the actual failed lint episode first"))
      val live = _reopen(episode)
      _retained_prefix(live, episode)
      val formal = _formal(live)
      val added = formal.filterNot(episode.formalReceipts.contains)
      added.size shouldBe 1
      val receipt = parse(added.head._3).toOption.get.as[C.Receipt].toOption.get
      receipt.identity shouldBe "lint3"
      receipt.sbt.get.kind shouldBe WorkflowSbtValidation.Kind.FixCheck
      receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
      receipt.exitCode.exists(_ != 0) shouldBe true
      receipt.processObservation should not be empty
      val invocations = _lines(live.root, "sbt-invocations.txt")
      invocations.size shouldBe episode.invocations.size + 1
      val commands = _lines(live.root, "sbt-command-lines.txt")
      val markers = _lines(live.root, "lint-entered.txt")
      markers.size shouldBe 1
      _current(live).currentProgression shouldBe Some(ProgressionReference("sync.ValidationChecking"))
      _current(live).suspension shouldBe None

      When("one ordinary installed Advance collects the retained failed result")
      val output = live.advance()
      val issued = output.business[V.RepositorySyncAdvanceResult]
      val publicwire = parse(Files.readString(output.invocation.stdout)).toOption.get
      Then("the normal response issues the existing lint repair without another formal check or SBT invocation")
      issued.repair should not be empty
      issued.repair.get.validationFailure shouldBe Some("lint3")
      issued.repair.get.review.changedPaths should contain("src/main/scala/LintInput.scala")
      publicwire.hcursor.downField("repair").get[Option[String]]("validation_failure") shouldBe Right(Some("lint3"))
      publicwire.hcursor.downField("repair").downField("review").get[Vector[String]]("changed_paths")
        .toOption.get should contain("src/main/scala/LintInput.scala")
      _retained_prefix(live, episode)
      _formal(live) shouldBe formal
      _lines(live.root, "sbt-invocations.txt") shouldBe invocations
      _lines(live.root, "sbt-command-lines.txt") shouldBe commands
      _lines(live.root, "lint-entered.txt") shouldBe markers
      val collected = _snapshot(live)
      val issuedreceipts = _receipts(live)

      When("a subsequent ordinary installed Advance passively recollects that issued repair")
      val repeated = live.advance()
      Then("the same repair is returned with the native snapshot receipts and invocation counters unchanged")
      repeated.business[V.RepositorySyncAdvanceResult] shouldBe issued
      parse(Files.readString(repeated.invocation.stdout)).toOption.get.hcursor.downField("repair").focus shouldBe
        publicwire.hcursor.downField("repair").focus
      _snapshot(live) shouldBe collected
      _receipts(live) shouldBe issuedreceipts
      _retained_prefix(live, episode)
    }

    "continue REQUIRED-LINT fresh candidate preflight before any SBT invocation" in {
      Given("a fresh new tiny Scala merge under the newly published normal CAR with Compile Test but no selected lint")
      val live = _resolved_session(_scenario("fresh-preflight"))
      When("ordinary Advance would otherwise start product validation")
      val refused = live.advance()
      Then("required lint selection refuses before any SBT process and before a successful product result exists")
      refused.exitCode should not be 0
      refused.invocation.logs.toLowerCase should include("lint")
      _lines(live.root, "sbt-invocations.txt") shouldBe empty
      _lines(live.root, "lint-entered.txt") shouldBe empty
      _formal(live) shouldBe empty
    }
  }
}
