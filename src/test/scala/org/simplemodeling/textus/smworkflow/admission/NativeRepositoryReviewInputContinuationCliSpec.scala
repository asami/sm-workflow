package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
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
import RepositorySyncNativeMergeReview as M
import RepositorySyncValidationContinuation as N
import RepositorySyncReviewInputContinuation as R
import SplitPhaseExplicitPartitionFixture.take

/** A finite feature on the two current leaves, retaining the original runtime
  * classification and leaving historical Human episode registrations untouched.
  */
class Phase8RemoteClassificationDeclaration extends org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration {
  override def declaration: org.goldenport.cncf.validation.ValidationMetadataV1.Declaration =
    super.declaration.copy(features = super.declaration.features +
      org.goldenport.cncf.validation.ValidationMetadataV1.FeatureReference("component:sm-workflow", "phase8-remote-classification"))
}

/** Two separately selected publication stages share one genuine native Session.
  * Independent semantic answers are specification-owned evidence, never live
  * provider acceptance. SQLite is observed read-only, never seeded or repaired.
  * @since   Nov.  3, 2026
  * @version Nov. 20, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryReviewInputContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with RepositorySyncMaintenanceFixture with org.simplemodeling.textus.smworkflow.testing.DevelopFixtureValidation {
  private val _checks = Vector(C.Check("compile1", 1, WorkflowSbtValidation.Kind.Compile),
    C.Check("test2", 2, WorkflowSbtValidation.Kind.Test), C.Check("lint3", 3, WorkflowSbtValidation.Kind.FixCheck))
  private val _unknown = Vector("README.md", "scripts/probe.py", "skills/probe/SKILL.md")
  private val _supplemental_paths = Vector(".gitignore", ".scalafix.conf")
  private val _consumed = Vector("value.txt", "src", "build.sbt", "project", ".scalafix.conf", "scripts", "skills")
  private val _catalog_path = "src/main/catalog/probe.yaml"
  private def _supplemental: Boolean = sys.props.get("smworkflow.spec.reviewInputSupplemental").contains("true")
  private def _known_catalog: Boolean = sys.props.get("smworkflow.spec.reviewInputKnownCatalog").contains("true")
  private val _rationale = "Specification-owned independent review: the complete parent diffs show the resolved value and Scala value agree. " +
    "README, script and skill classification remains Unexamined; this decision-needed result accepts no path or partial semantic coverage."
  private lazy val _catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(RepositorySyncDefinitionBinding.bindC))))
  private final case class Episode(root: String, normalJson: Json, plan: Json, session: String, originalRecord: String,
    head: String, indexTree: String, mergeHead: String, reviewStdout: String, continuationId: String, result: String, reference: String,
    execution: String, nativeRows: Vector[String], formalReceipts: Vector[(String, String, String)],
    invocations: Vector[String], commands: Vector[String], lintEntries: Vector[String])
  private final case class Live(scenario: F.Scenario, session: String) {
    def root: Path = scenario.root
    def repository: Path = Path.of(scenario.host.target.root)
    def call(service: String, operation: String, parameters: Vector[String]): F.Result = scenario.callInstalled(
      _installed, service, operation, parameters, None, Some(root.resolve(".textus/assembly.yaml")))
    def repositoryCall(operation: String, parameters: Vector[String]): F.Result = call("RepositorySyncService", operation, parameters)
    def advance(): F.Result = repositoryCall("advanceRepositorySync", Vector("--sessionId", session))
  }
  private def _installed: Path = {
    val path = Path.of(sys.props.getOrElse("smworkflow.spec.installedCommand", fail("Select the normal absolute installed command")))
    path.isAbsolute shouldBe true
    path.normalize shouldBe path
    Files.isRegularFile(path) shouldBe true
    Files.isExecutable(path) shouldBe true
    path
  }
  private def _episode_path: Path = {
    val path = Path.of(sys.props.getOrElse("smworkflow.spec.reviewInputEpisode", fail("Select the private absolute reviewInputEpisode file")))
    path.isAbsolute shouldBe true
    path.normalize shouldBe path
    Files.isDirectory(path.getParent) shouldBe true
    path
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _save(root: Path, name: String, value: String): String = {
    val path = root.resolve(name)
    Files.writeString(path, value, UTF_8, StandardOpenOption.CREATE_NEW)
    path.toUri.toASCIIString
  }
  private def _lines(root: Path, name: String): Vector[String] = {
    val path = root.resolve(name)
    if (Files.exists(path)) Files.readString(path).linesIterator.filter(_.nonEmpty).toVector else Vector.empty
  }
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
  private def _requests(live: Live): Vector[M.Request] = {
    def _nested_(value: Json): Vector[M.Request] = M.inputCodec.decode(value).toOption.toVector ++
      value.asObject.toVector.flatMap(_.values.toVector.flatMap(_nested_)) ++
      value.asArray.toVector.flatMap(_.flatMap(_nested_))
    _stored(live.root).flatMap(raw => parse(raw).toOption.toVector.flatMap(_nested_))
      .filter(_.instanceIdentity == live.session).distinct
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
    _lines(live.root, "sbt-invocations.txt"), _lines(live.root, "sbt-command-lines.txt"), _lines(live.root, "lint-entered.txt"))
  private def _impact(checks: Vector[C.Check] = _checks, supplemental: Boolean = false): RepositorySyncProgramImpact.Policy = {
    val consumed = if (supplemental) _consumed.filterNot(_supplemental_paths.contains) else _consumed
    RepositorySyncProgramImpact.Policy(checks.map(check => RepositorySyncProgramImpact.Coverage(check,
      consumed, consumed, "The real selected product checks consume declared source script and skill inputs")),
      checks, Vector("value.txt", "src"), "Actual source delta requires the existing full validation", Vector.empty)
  }
  private def _configure(scenario: F.Scenario, corrected: Boolean, demote: Boolean = false,
    checks: Vector[C.Check] = _checks, impact: Option[RepositorySyncProgramImpact.Policy] = None,
    remotePhase: Boolean = false, supplemental: Boolean = false): Unit = {
    val root = scenario.root
    val base = process_plan(root, "Equal", scenario.host.goal)
    def _array_(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
    val plans = Vector(SbtOperation.Compile, SbtOperation.Test, SbtOperation.FixCheck).zipWithIndex.map {
      (operation, index) => TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable,
        Vector(index + 1), operation = operation, executionTimeoutMillis = 180000L)
    }
    val programs = plans.flatMap(_array_(_, "sbtPrograms"))
    val productPlan = base.mapObject(fields => fields.add("sbtPrograms", Json.fromValues(programs))
      .add("workspaces", Json.fromValues((_array_(base, "workspaces") ++ plans.flatMap(_array_(_, "workspaces"))).distinct))
      .add("workspaceGrants", Json.fromValues((_array_(base, "workspaceGrants") ++ plans.flatMap(_array_(_, "workspaceGrants"))).distinct))
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(existing => (existing ++ programs.map(_.hcursor.get[Json]("capability").toOption.get)).distinct)))))))
    // The existing source workspace selects a linked contribution checkout.
    // Product workspaces stay on canonical main, where the remote merge occurs.
    val plan = if (!remotePhase) productPlan else {
      def rows(key: String) = _array_(productPlan, key)
      val sourceWorkspace = "sm-workflow-repository"
      val mainWorkspace = "review-input-canonical-main"
      val source = rows("gitRepositories").find(_.hcursor.get[String]("target").contains(sourceWorkspace)).get
      val main = source.mapObject(_.add("target", mainWorkspace.asJson)
        .add("identity", "review-input-main-family".asJson).add("capability", "review-input-main-git".asJson))
      productPlan.mapObject(fields => fields
        .add("gitRepositories", (rows("gitRepositories").map(row =>
          if (row.hcursor.get[String]("target").contains(sourceWorkspace))
            row.mapObject(_.add("localBranch", "contribution".asJson)) else row) :+ main).asJson)
        .add("workspaces", (rows("workspaces").map(row =>
          if (row.hcursor.get[String]("identity").contains(sourceWorkspace))
            row.mapObject(_.add("root", root.resolve("contribution").toString.asJson)) else row) :+
          Json.obj("identity" -> mainWorkspace.asJson, "root" -> root.resolve("repository").toString.asJson)).asJson)
        .add("workspaceGrants", (rows("workspaceGrants") :+ Json.obj(
          "componentId" -> org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name.asJson,
          "workspaceId" -> mainWorkspace.asJson, "access" -> "manage".asJson)).asJson)
        .add("grants", rows("grants").map(grant =>
          if (grant.hcursor.get[String]("componentId").contains(org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name))
            grant.mapObject(row => row.add("capabilities", row("capabilities").get.mapArray(_ :+ "review-input-main-git".asJson)))
          else grant).asJson))
    }
    val knowncatalog = _known_catalog && !supplemental
    val documents = Vector("docs") ++ Option.when(knowncatalog)(_catalog_path) ++ Option.when(corrected)("README.md") ++
      (if (demote) Vector("scripts", "skills") else Vector.empty)
    val documentscopes = if (knowncatalog) _consumed.filterNot(_ == "src") ++ Vector("src/main/scala", "src/test") else _consumed
    val consumed = documentscopes.filterNot(path => path == "scripts" || path == "skills" ||
      (supplemental && _supplemental_paths.contains(path))) ++
      (if (corrected && !demote) Vector("scripts", "skills") else Vector.empty) ++
      (if (supplemental && corrected) _supplemental_paths else Vector.empty)
    val inputs = RepositorySyncDocumentInputs.Policy(documents, consumed, "Explicit proposed classification of the actual tiny fixture inputs")
    val file = root.resolve(".textus/config.json")
    val config = parse(Files.readString(file)).toOption.get
    val parameters = maintenance_parameters() ++ Map("textus.subsystem.process-execution.plan" -> plan.noSpaces,
      "sm-workflow.repository-sync.program-inputs" -> impact.getOrElse(_impact(checks, supplemental)).asJson.noSpaces,
      "sm-workflow.repository-sync.document-inputs" -> inputs.asJson.noSpaces,
      "textus.resource.url.file.roots" -> root.toString)
    Files.writeString(file, config.mapObject(fields => parameters.foldLeft(fields.remove("sm-workflow.runtime.definition")) {
      case (prior, (key, value)) => prior.add(key, Json.fromString(value))
    }).noSpaces)
  }
  private def _append_ignore_rule(repository: Path, rule: String): Unit = {
    val file = repository.resolve(".gitignore")
    val prior = if (Files.exists(file)) Files.readString(file, UTF_8) else ""
    val separator = if (prior.nonEmpty && !prior.endsWith("\n")) "\n" else ""
    Files.writeString(file, separator + rule + "\n", UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
  }
  private def _scenario(remotePhase: Boolean = false, checks: Vector[C.Check] = _checks,
    supplemental: Boolean = false): F.Scenario = {
    sys.props.get("smworkflow.spec.sbt") should not be empty
    val scenario = F.fresh("installed-review-input", sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable), bootstrapSelectedInput = false)
    val root = scenario.root
    val repository = root.resolve("repository")
    Files.createDirectories(repository.resolve("src/main/scala"))
    Files.createDirectories(repository.resolve("scripts"))
    Files.createDirectories(repository.resolve("skills/probe"))
    if (_known_catalog && !supplemental) {
      Files.createDirectories(repository.resolve("src/main/catalog"))
      Files.writeString(repository.resolve(_catalog_path), "name: probe\npublishedAt: 2026-11-02T00:00:00Z\n")
    }
    Files.writeString(repository.resolve("src/main/scala/ReviewInput.scala"), "object ReviewInput { val value = 0 }\n")
    Files.writeString(repository.resolve("README.md"), "# Probe\nInitial fixture documentation.\n")
    Files.writeString(repository.resolve("scripts/probe.py"), "VALUE = 0\n")
    Files.writeString(repository.resolve("skills/probe/SKILL.md"), "# Probe skill\nRead value 0.\n")
    Files.writeString(repository.resolve("src/test/scala/ProductCheck.scala"), """object ProductCheck {
      |  def main(args: Array[String]): Unit = {
      |    assert(java.nio.file.Files.readString(java.nio.file.Paths.get("value.txt")) == "resolved product input\n")
      |    assert(ReviewInput.value == 1)
      |    assert(java.nio.file.Files.readString(java.nio.file.Paths.get("scripts/probe.py")) == "VALUE = 1\n")
      |    assert(java.nio.file.Files.readString(java.nio.file.Paths.get("skills/probe/SKILL.md")) == "# Probe skill\nRead value 1.\n")
      |    println("ACTUAL_REVIEW_INPUT_PRODUCT_TEST_PASSED")
      |  }
      |}
      |""".stripMargin)
    Files.writeString(repository.resolve("project/plugins.sbt"), "addSbtPlugin(\"ch.epfl.scala\" % \"sbt-scalafix\" % \"0.12.1\")\n")
    Files.writeString(repository.resolve(".scalafix.conf"), s"rules = [DisableSyntax]\nDisableSyntax.noFinalize = ${!supplemental}\n")
    Files.writeString(repository.resolve("build.sbt"), """
      |lazy val reviewInputLintMarker = taskKey[Unit]("Observe ordinary selected lint entry")
      |reviewInputLintMarker := IO.append(file(__LINT__), java.lang.ProcessHandle.current().pid().toString + "\n")
      |addCommandAlias("fixCheck", ";reviewInputLintMarker;Compile / scalafix --check")
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
    val baseadd = Vector("-C", repository.toString, "add", "--", "src", "scripts", "skills", "README.md", "project/plugins.sbt")
    if (supplemental) {
      _append_ignore_rule(repository, "/.scalafix.conf")
      git(root, (baseadd ++ Vector(".gitignore", "build.sbt"))*)
      git(root, "-C", repository.toString, "add", "-f", "--", ".scalafix.conf")
    } else {
      git(root, (baseadd ++ Vector(".scalafix.conf", "build.sbt"))*)
    }
    git(root, "-C", repository.toString, "commit", "-m", "Tiny actual review input consumption and ordinary fixed checks")
    git(root, "-C", repository.toString, "push", root.resolve("upstream.git").toString, "HEAD:refs/heads/main")
    val peer = root.resolve("peer")
    git(root, "clone", "--branch", "main", "--", root.resolve("upstream.git").toString, peer.toString)
    Files.writeString(peer.resolve("value.txt"), "remote product input\n")
    Files.writeString(peer.resolve("src/main/scala/ReviewInput.scala"), "object ReviewInput { val value = 1 }\n")
    Files.writeString(peer.resolve("README.md"), "# Probe\nThe fixture reads value 1.\n")
    Files.writeString(peer.resolve("scripts/probe.py"), "VALUE = 1\n")
    Files.writeString(peer.resolve("skills/probe/SKILL.md"), "# Probe skill\nRead value 1.\n")
    if (_known_catalog && !supplemental) Files.writeString(peer.resolve(_catalog_path), "name: probe\npublishedAt: 2026-11-03T00:00:00Z\n")
    if (supplemental) {
      _append_ignore_rule(peer, "!/.scalafix.conf")
      val scalafix = peer.resolve(".scalafix.conf")
      val rules = Files.readString(scalafix, UTF_8)
      Files.writeString(scalafix, rules.replace("DisableSyntax.noFinalize = false", "DisableSyntax.noFinalize = true"), UTF_8)
    }
    val peeradd = Vector("-C", peer.toString, "add", "--", "value.txt", "src", "README.md", "scripts", "skills") ++
      (if (supplemental) _supplemental_paths else Vector.empty)
    git(root, peeradd*)
    git(root, "-C", peer.toString, "commit", "-m", "Remote actual product and unknown classification inputs")
    git(root, "-C", peer.toString, "push", root.resolve("upstream.git").toString, "HEAD:refs/heads/main")
    Files.writeString(repository.resolve("value.txt"), "local product input\n")
    git(root, "-C", repository.toString, "add", "--", "value.txt")
    git(root, "-C", repository.toString, "commit", "-m", "Local conflicting input")
    if (remotePhase) git(root, "-C", repository.toString, "worktree", "add", "-b", "contribution",
      root.resolve("contribution").toString, "HEAD")
    _configure(scenario, corrected = false, checks = checks, remotePhase = remotePhase, supplemental = supplemental)
    scenario
  }
  private def _prepare(live: Live, operation: String, issued: F.Result, result: String,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence): Unit =
    _prepare(live, operation, issued.invocation.stdout, result, reference, execution)

  private def _prepare(live: Live, operation: String, request: Path, result: String,
    reference: V.WorkflowArtifactReference, execution: V.WorkflowExecutionEvidence): Unit = {
    val directory = Files.createTempDirectory(live.root, "independent-review-input-")
    val input = V.WorkflowEvidencePreparationInput("RepositorySync", operation, request.toUri.toASCIIString,
      _save(directory, "result.json", result), _save(directory, "reference.json", _json(reference)),
      _save(directory, "execution.json", _json(execution)), "normal-development-host", java.time.Instant.now().toString,
      "entity:smworkflow.cli.host_evidence", Option.when(operation == "validation-continuation")("participant"))
    val prepared = live.call("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(input)))
    withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
    live.scenario.selectPreparedImport(prepared)
  }
  private def _reopen(episode: Episode): Live = {
    val root = Path.of(episode.root)
    val host = take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(episode.normalJson))
    val plan = episode.plan.as[GoalPhaseOrderedProgression.Plan].toOption.get
    val logs = Files.list(root.resolve("logs"))
    val next = try { math.max(1000, logs.filter(path => Files.isRegularFile(path) &&
      path.getFileName.toString.matches("[0-9]+\\.(stdout\\.json|stderr)"))
      .mapToInt(path => path.getFileName.toString.takeWhile(_.isDigit).toInt).max().orElse(999) + 1) } finally logs.close()
    Live(F.reopenInstalledRepository(root, host, plan, next), episode.session)
  }
  private def _retained(live: Live, episode: Episode): Unit = {
    _formal(live) shouldBe episode.formalReceipts
    _lines(live.root, "sbt-invocations.txt") shouldBe episode.invocations
    _lines(live.root, "sbt-command-lines.txt") shouldBe episode.commands
    _lines(live.root, "lint-entered.txt") shouldBe episode.lintEntries
    val original = take(WorkflowInstanceRecordJsonV1.decodeC(episode.originalRecord, _catalog))
    _current(live).history.take(original.history.size) shouldBe original.history
    val currentrows = _stored(live.root)
    episode.nativeRows.filterNot(raw => WorkflowInstanceRecordJsonV1.decodeC(raw, _catalog).toOption.nonEmpty)
      .foreach(raw => currentrows should contain(raw))
  }

  @org.goldenport.cncf.validation.annotation.ValidationScenario(
    name = "Installed P8-03 semantic remote-phase classification should P8-03 REMOTE technical classification continue with actual retained product checks",
    identity = "phase8-remote-continue", declaration = classOf[Phase8RemoteClassificationDeclaration])
  def phase8RemoteContinueDeclaration(): Unit = ()

  @org.goldenport.cncf.validation.annotation.ValidationScenario(
    name = "Installed P8-03 semantic remote-phase classification should P8-03 REMOTE technical classification consumed-demotion with actual retained product checks",
    identity = "phase8-remote-consumed-demotion", declaration = classOf[Phase8RemoteClassificationDeclaration])
  def phase8RemoteConsumedDemotionDeclaration(): Unit = ()

  "Installed P8-03 semantic remote-phase classification" should {
    for (mode <- Vector("continue", "consumed-demotion")) {
      s"P8-03 REMOTE technical classification $mode with actual retained product checks" in {
        assume(!_supplemental, "not selected in the supplemental before fixture")
        Given("an explicitly selected current installed runtime, real SBT, and a fresh linked contribution/canonical-main/bare fixture")
        val selectedCar = Path.of(sys.props.getOrElse("smworkflow.spec.componentFile",
          fail("Select the actual updated normal component CAR explicitly for P8-03 qualification")))
        selectedCar.isAbsolute shouldBe true
        selectedCar.normalize shouldBe selectedCar
        Files.isRegularFile(selectedCar) shouldBe true
        val scenario = _scenario(remotePhase = true)
        val started = Live(scenario, "").repositoryCall("startRepositorySync", Vector("--input",
          "{\"repositoryIdentity\":\"p8-03-review-input\",\"syncMode\":\"FULL_STATE\",\"mainIntegration\":\"local-first\"}"))
          .business[V.RepositorySyncStartResult]
        val live = Live(scenario, started.sessionId)
        var boundary = live.advance()
        val phases = scala.collection.mutable.ArrayBuffer.empty[String]
        var attempts = 0
        while (boundary.business[V.RepositorySyncAdvanceResult].condition != Some("MergeConflicted") && attempts < 8) {
          val output = boundary.business[V.RepositorySyncAdvanceResult]
          output.kind shouldBe "Progress"
          phases ++= output.condition
          boundary = live.advance()
          attempts += 1
        }
        boundary.business[V.RepositorySyncAdvanceResult].condition shouldBe Some("MergeConflicted")
        phases should contain("LocalMainSourcePrepared")
        phases should contain("LocalMainIntegrated")
        _current(live).history.exists(_.causation.value == "repository-local-main-phase:remote") shouldBe true
        val conflict = live.advance().business[V.RepositorySyncAdvanceResult]
        val resolution = V.RepositorySyncConflictResult("resolved", conflict.conflict.get.conflicts.map(value =>
          V.RepositorySyncConflictResolution(value.identity, "resolved product input\n")), "Specification-owned actual remote conflict resolution")
        val conflictRef = V.WorkflowArtifactReference("p8-03-remote-conflict-" + mode, "1")
        val conflictExecution = V.WorkflowExecutionEvidence(Vector(conflictRef), Some("fixture-conflict-author"), None, None)
        live.repositoryCall("submitRepositorySyncWorkResult", Vector("--sessionId", live.session,
          "--result", _json(resolution), "--resultReference", _json(conflictRef), "--executionEvidence", _json(conflictExecution)))
          .business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true

        When("ordinary remote Advance executes actual Compile Test and scalafix before issuing native independent review")
        val issued = live.advance()
        val review = issued.business[V.RepositorySyncAdvanceResult].review.get
        val originalRequest = _requests(live).head
        originalRequest.indexTree shouldBe review.indexTree
        originalRequest.selectedChecks shouldBe _checks.map(_.identity)
        _unknown.foreach(path => originalRequest.uncertainties should contain("Unexamined unknown input: " + path))
        val formal = _formal(live)
        formal.size shouldBe 3
        formal.foreach(value => value._1 should include("local-main-remote-validation-result-"))
        formal.map(value => parse(value._3).toOption.get.as[C.Receipt].toOption.get).foreach(receipt =>
          receipt.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded)
        _lines(live.root, "sbt-invocations.txt").size shouldBe 3
        _lines(live.root, "lint-entered.txt").size shouldBe 1
        _lines(live.root, "sbt-command-lines.txt").mkString("\n") should include("fixCheck")
        originalRequest.checks.find(_.identity == "test2").get.stdout should include("ACTUAL_REVIEW_INPUT_PRODUCT_TEST_PASSED")

        val documents = Vector("docs", "README.md") ++ Option.when(_known_catalog)(_catalog_path) ++
          (if (mode == "consumed-demotion") Vector("value.txt") else Vector.empty)
        val consumed = (if (_known_catalog) _consumed.filterNot(_ == "src") ++ Vector("src/main/scala", "src/test") else _consumed)
          .filterNot(path => mode == "consumed-demotion" && path == "value.txt")
        val mapping = RepositorySyncDocumentInputs.Policy(documents, consumed,
          "Independent fixture assessment of actual remote candidate input consumption")
        val result = V.RepositorySyncNativeMergeReviewResult("decision-needed", _rationale, Vector.empty,
          Some(GoalPhaseNativeMetadata.record(mapping.asJson)))
        val reference = V.WorkflowArtifactReference("p8-03-remote-assessment-" + mode, "1")
        val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-independent-input-assessor"), None, None)
        def submit(value: V.RepositorySyncNativeMergeReviewResult, ref: V.WorkflowArtifactReference = reference): F.Result =
          live.repositoryCall("submitNativeMergeReview", Vector("--sessionId", live.session, "--result", _json(value),
            "--resultReference", _json(ref), "--executionEvidence", _json(execution)))
        val pending = _snapshot(live)
        When("a technical mapping is submitted without independently admitted review evidence")
        submit(result).exitCode should not be 0
        _snapshot(live) shouldBe pending
        _prepare(live, "review", issued, _json(result), reference, execution)
        When("the prepared mapping is replaced by a different caller mapping under the same evidence reference")
        val substituted = result.copy(inputClassification = Some(GoalPhaseNativeMetadata.record(mapping.copy(
          consumedPaths = mapping.consumedPaths :+ "foreign-input").asJson)))
        submit(substituted).exitCode should not be 0
        _snapshot(live) shouldBe pending
        Then("only the exact independently evidenced semantic result is accepted at the original review boundary")
        submit(result).business[V.RepositorySyncNativeMergeReviewSubmissionResult].accepted shouldBe true
        val decision = _current(live)
        decision.currentProgression shouldBe Some(ProgressionReference("sync.ReviewDecisionRequired"))
        val saved = _snapshot(live)
        val rows = _stored(live.root)
        val described = live.repositoryCall(N.describeOperation, Vector("--sessionId", live.session))
          .business[V.RepositorySyncValidationContinuationDescriptionResult]
        val target = R.decodePublicTarget(parse(take(described.target.toJsonStringC)).toOption.get).toOption.get
        target.phase shouldBe Some("local-main-remote")
        target.candidate.pending.reference.identity should include("local-main-remote-pending-merge")
        target.selectedChecks shouldBe _checks
        target.review.result.identity shouldBe reference.identity
        if (mode == "consumed-demotion") {
          When("ordinary Advance encounters demotion of an actually consumed original value input")
          target.applicabilityIssues.exists(_.startsWith("Previously consumed input was demoted: value.txt")) shouldBe true
          live.advance().exitCode should not be 0
          _snapshot(live) shouldBe saved
          _stored(live.root).sorted shouldBe rows.sorted
          _requests(live).size shouldBe 1
        } else {
          target.applicabilityIssues shouldBe empty
          val crossedPhase = Some("local-main-local")
          val crossed = target.copy(phase = crossedPhase,
            candidate = target.candidate.copy(pending = target.candidate.pending.copy(reference =
              org.goldenport.cncf.workflow.ContextReference(live.session + ":native-" + S.phaseKey(
                crossedPhase, target.namespace, "pending-merge"), "1"))),
            validated = target.validated.copy(reference = org.goldenport.cncf.workflow.ContextReference(
              live.session + ":native-" + S.phaseKey(crossedPhase, target.namespace, "merge-validated"), "1")))
          val crossInput = V.RepositorySyncValidationContinuationAdoptionInput(live.session, R.targetRecord(crossed),
            R.choices.head, "Crossed fixture phase must refuse", reference, false, false,
            execution.copy(workerIdentity = Some(target.context.participantIdentity)))
          When("the public owning continuation receives a structurally valid target for the wrong local-main phase")
          live.repositoryCall(N.adoptionOperation, Vector("--input", _json(crossInput))).exitCode should not be 0
          _snapshot(live) shouldBe saved
          _stored(live.root).sorted shouldBe rows.sorted
          When("ordinary Advance consumes the actual technical assessment without any Human-only adoption operation")
          live.advance().business[V.RepositorySyncAdvanceResult].condition shouldBe Some("ReviewInputContinuationAdmitted")
          val admission = _receipts(live).find(_.payload.value.stage == "ReviewInputContinuationAdmitted").get
          admission.reference.identity should include("local-main-remote-review-inputs-")
          admission.payload.value.reviewInputs.get.reference.identity shouldBe reference.identity
          admission.payload.value.reviewInputs.get.target.phase shouldBe Some("local-main-remote")
          admission.payload.value.validation.get.receipts shouldBe originalRequest.checks
          _current(live).history.take(decision.history.size) shouldBe decision.history
          _formal(live) shouldBe formal
          _requests(live).size shouldBe 1
          val invocations = _lines(live.root, "sbt-invocations.txt")
          val commands = _lines(live.root, "sbt-command-lines.txt")
          val lint = _lines(live.root, "lint-entered.txt")
          val followup = live.advance().business[V.RepositorySyncAdvanceResult]
          followup.kind shouldBe "Request"
          val revised = _requests(live).find(_.validatedRevision != originalRequest.validatedRevision).get
          Then("fresh independent review retains the actual checks full candidate and original unanswered semantic history")
          followup.issuedWorkContext.get.continuationId should not be issued.business[V.RepositorySyncAdvanceResult].issuedWorkContext.get.continuationId
          revised.judgmentMode shouldBe "detailed-review"
          revised.localTip shouldBe originalRequest.localTip
          revised.remoteTip shouldBe originalRequest.remoteTip
          revised.indexTree shouldBe originalRequest.indexTree
          revised.localDiff shouldBe originalRequest.localDiff
          revised.remoteDiff shouldBe originalRequest.remoteDiff
          revised.checks shouldBe originalRequest.checks
          revised.selectedChecks shouldBe originalRequest.selectedChecks
          revised.uncertainties.mkString("\n") should include(reference.identity)
          revised.uncertainties.mkString("\n") should include(_rationale)
          revised.uncertainties.exists(_.startsWith("Unexamined")) shouldBe false
          _formal(live) shouldBe formal
          _lines(live.root, "sbt-invocations.txt") shouldBe invocations
          _lines(live.root, "sbt-command-lines.txt") shouldBe commands
          _lines(live.root, "lint-entered.txt") shouldBe lint
          _current(live).history.take(decision.history.size) shouldBe decision.history
          rows.filterNot(raw => WorkflowInstanceRecordJsonV1.decodeC(raw, _catalog).toOption.nonEmpty)
            .foreach(raw => _stored(live.root) should contain(raw))
          val fresh = _snapshot(live)
          live.advance().business[V.RepositorySyncAdvanceResult].issuedWorkContext shouldBe followup.issuedWorkContext
          _snapshot(live) shouldBe fresh
          _current(live).lifecycle shouldBe Lifecycle.Active
          _current(live).suspension should not be empty
        }
      }
    }
  }

  "Installed retained review input continuation" should {
    "capture REVIEW-INPUT actual saved decision-needed" in {
      Given("the old normal published CAR, a new tiny real local/bare Git fixture and one unused private episode path")
      Files.exists(_episode_path) shouldBe false
      val supplemental = _supplemental
      val checks = if (supplemental) _checks.take(2) else _checks
      val unknown = if (supplemental) (_unknown ++ _supplemental_paths).sorted else _unknown
      val scenario = _scenario(checks = checks, supplemental = supplemental)
      val started = Live(scenario, "").repositoryCall("startRepositorySync", Vector("--input",
        "{\"repositoryIdentity\":\"review-input-fixture\",\"syncMode\":\"FULL_STATE\"}")).business[V.RepositorySyncStartResult]
      val live = Live(scenario, started.sessionId)
      def _advance_detail_(issued: V.RepositorySyncAdvanceResult): (V.RepositorySyncAdvanceResult, Option[V.WorkflowRetainedWorkReadResult]) =
        issued.retainedWork match {
          case Some(reference) =>
            val detail = scenario.readRetainedWork(reference).businessBounded[V.WorkflowRetainedWorkReadResult]
            detail.availability shouldBe "available"
            val full = take(V.RepositorySyncAdvanceResult.createC(detail.response))
            full.issuedWorkContext shouldBe issued.issuedWorkContext
            (full, Some(detail))
          case None => (issued, None)
        }
      When("ordinary Advance issues the real value conflict and the fixture submits its explicit semantic resolution")
      live.advance().business[V.RepositorySyncAdvanceResult].condition shouldBe Some("MergeConflicted")
      val conflictoutput = live.advance()
      val (fullconflict, _) = _advance_detail_(conflictoutput.business[V.RepositorySyncAdvanceResult])
      val conflict = fullconflict.conflict.getOrElse(fail("The issued work contains no Conflict assignment"))
      val resolution = V.RepositorySyncConflictResult("resolved", conflict.conflicts.map(value =>
        V.RepositorySyncConflictResolution(value.identity, "resolved product input\n")), "Specification-owned actual conflict resolution")
      val conflictref = V.WorkflowArtifactReference("fixture-review-input-conflict", "1")
      val conflictexecution = V.WorkflowExecutionEvidence(Vector(conflictref), Some("fixture-conflict-author"), None, None)
      val submitted = live.repositoryCall("submitRepositorySyncWorkResult", Vector("--sessionId", live.session,
        "--result", _json(resolution), "--resultReference", _json(conflictref), "--executionEvidence", _json(conflictexecution)))
      Then("only the actual conflict result is accepted")
      submitted.business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
      When(if (supplemental) "ordinary Advance runs actual Compile and Test before issuing independent detailed review"
        else "ordinary Advance runs actual Compile Test and scalafix before issuing independent detailed review")
      val output = live.advance()
      val issued = output.business[V.RepositorySyncAdvanceResult]
      Then(if (supplemental) "the complete candidate and two successful check receipts accompany the Unexamined tooling inputs"
        else "the complete candidate and all three successful check receipts accompany actual Unexamined input")
      val (fullreview, retainedreview) = _advance_detail_(issued)
      val review = fullreview.review.getOrElse(fail("The issued work contains no MergeReview assignment"))
      review.changedPaths should contain allElementsOf unknown
      review.localDiff.trim shouldBe git(live.root, "-C", live.repository.toString, "diff", "--cached", review.localTip, "--")
      review.remoteDiff.trim shouldBe git(live.root, "-C", live.repository.toString, "diff", "--cached", review.remoteTip, "--")
      val originalinput = _requests(live).head
      originalinput.judgmentMode shouldBe "detailed-review"
      unknown.foreach(path => originalinput.uncertainties should contain("Unexamined unknown input: " + path))
      val formal = _formal(live)
      val receipts = formal.map(value => parse(value._3).toOption.get.as[C.Receipt].toOption.get)
      receipts.map(receipt => receipt.identity -> receipt.sbt.get.outcome).toSet shouldBe
        checks.map(check => check.identity -> WorkflowSbtValidation.Outcome.Succeeded).toSet
      _lines(live.root, "sbt-invocations.txt").size shouldBe checks.size
      _lines(live.root, "lint-entered.txt").size shouldBe (if (supplemental) 0 else 1)
      if (supplemental) {
        receipts.map(receipt => receipt.identity -> receipt.sbt.get.kind) shouldBe checks.map(check => check.identity -> check.kind)
        Files.exists(live.root.resolve("lint-entered.txt")) shouldBe false
        originalinput.changedPaths should contain allElementsOf _supplemental_paths
      }
      val rationale = if (supplemental) _rationale + " The root ignore file and tracked Scalafix rules also remain Unexamined." else _rationale
      val result = V.RepositorySyncNativeMergeReviewResult("decision-needed", rationale, Vector.empty, None)
      val reference = V.WorkflowArtifactReference("fixture-review-input-independent-original", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-independent-original-reviewer"), None, None)
      When("the independent fixture prepares imports and submits decision-needed once through normal public operations")
      val reviewrequest = retainedreview.fold(output.invocation.stdout) { detail =>
        val directory = Files.createTempDirectory(live.root, "retained-review-input-")
        Files.writeString(directory.resolve("request.json"), take(detail.response.toJsonStringC), UTF_8, StandardOpenOption.CREATE_NEW)
      }
      _prepare(live, "review", reviewrequest, _json(result), reference, execution)
      val accepted = live.repositoryCall("submitNativeMergeReview", Vector("--sessionId", live.session, "--result", _json(result),
        "--resultReference", _json(reference), "--executionEvidence", _json(execution)))
      Then("the actual decision-needed completion is accepted and ordinary Advance retains a nonterminal Wait")
      accepted.business[V.RepositorySyncNativeMergeReviewSubmissionResult].accepted shouldBe true
      live.advance().business[V.RepositorySyncAdvanceResult].kind shouldBe "Wait"
      val current = _current(live)
      current.currentProgression shouldBe Some(ProgressionReference("sync.ReviewDecisionRequired"))
      current.lifecycle shouldBe Lifecycle.Active
      current.suspension shouldBe None
      val saved = _snapshot(live)
      When("the old running CAR collects the already saved decision-needed frontier again")
      live.advance().business[V.RepositorySyncAdvanceResult].kind shouldBe "Wait"
      Then("the native instance candidate original result and check counters stay unchanged and one episode is retained")
      _snapshot(live) shouldBe saved
      if (supplemental) {
        Given("the saved old review has two tooling paths outside the original document and program coverage")
        val originalvalidation = _receipts(live).find(_.payload.value.stage == "MergeValidated").get.payload.value.validation.get
        val originalassessment = originalvalidation.inputs.get
        originalassessment.unknownPaths shouldBe unknown
        originalvalidation.selected shouldBe checks
        val rows = _stored(live.root).sorted
        val config = live.root.resolve(".textus/config.json")
        val oldconfig = Files.readAllBytes(config)
        try {
          When("the old normal CAR describes the corrected classification under its original Compile and Test policy")
          _configure(live.scenario, corrected = true, checks = checks, supplemental = true)
          val described = live.repositoryCall(N.describeOperation, Vector("--sessionId", live.session))
          val description = described.business[V.RepositorySyncValidationContinuationDescriptionResult]
          val target = R.decodePublicTarget(parse(take(description.target.toJsonStringC)).toOption.get).toOption.get
          Then("the same saved frontier permits only Defer because neither newly consumed tooling path has selected program coverage")
          description.choices shouldBe Vector("Defer")
          target.applicabilityIssues shouldBe _supplemental_paths.map("New consumed input has no actual selected program input coverage: " + _)
          target.sessionId shouldBe live.session
          target.candidate.before.head shouldBe review.localTip
          target.candidate.tree shouldBe review.indexTree
          target.candidate.mergeHead shouldBe review.remoteTip
          target.review.continuationId shouldBe issued.issuedWorkContext.get.continuationId
          target.review.result.identity shouldBe reference.identity
          target.review.decision shouldBe "decision-needed"
          target.originalInputs shouldBe originalassessment
          target.proposedInputs.unknownPaths shouldBe empty
          target.proposedInputs.consumedPaths should contain allElementsOf unknown.filterNot(_ == "README.md")
          target.proposedInputs.consumedPaths should not contain "README.md"
          target.selectedChecks shouldBe checks
          target.validationPolicy.impact.get.coverage.foreach { coverage =>
            _supplemental_paths.foreach { path =>
              RepositorySyncProgramImpact.covers(coverage.inputPaths, path) shouldBe false
              RepositorySyncProgramImpact.covers(coverage.affectedPaths, path) shouldBe false
            }
          }
          _snapshot(live) shouldBe saved
          _stored(live.root).sorted shouldBe rows
        } finally Files.write(config, oldconfig)
      } else if (_known_catalog) {
        Given("CATALOG-APPLICABILITY retains a changed known catalog document under the existing broad src program coverage")
        val originalvalidation = _receipts(live).find(_.payload.value.stage == "MergeValidated").get.payload.value.validation.get
        val originalassessment = originalvalidation.inputs.get
        originalassessment.changedPaths should contain(_catalog_path)
        originalassessment.consumedPaths should not contain _catalog_path
        originalassessment.unknownPaths should not contain _catalog_path
        originalvalidation.selected shouldBe _checks
        _impact().coverage.foreach(coverage => coverage.inputPaths should contain("src"))
        val rows = _stored(live.root).sorted
        When("the old normal CAR describes the exact corrected unknown-input proposal before publication")
        _configure(live.scenario, corrected = true)
        val described = live.repositoryCall(N.describeOperation, Vector("--sessionId", live.session))
        val description = described.business[V.RepositorySyncValidationContinuationDescriptionResult]
        val beforetarget = R.decodePublicTarget(parse(take(description.target.toJsonStringC)).toOption.get).toOption.get
        Then("the genuine old description permits only Defer solely because of the preserved known catalog overlap and leaves native effects unchanged")
        description.choices shouldBe Vector("Defer")
        beforetarget.applicabilityIssues shouldBe Vector("Proposed document is a selected consumed program input: " + _catalog_path)
        beforetarget.originalInputs shouldBe originalassessment
        beforetarget.proposedInputs.changedPaths should contain(_catalog_path)
        beforetarget.proposedInputs.consumedPaths should not contain _catalog_path
        beforetarget.proposedInputs.unknownPaths should not contain _catalog_path
        beforetarget.selectedChecks shouldBe _checks
        beforetarget.validationPolicy.impact.get.coverage.foreach(coverage => coverage.inputPaths should contain("src"))
        _snapshot(live) shouldBe saved
        _stored(live.root).sorted shouldBe rows
        _configure(live.scenario, corrected = false)
        _snapshot(live) shouldBe saved
      }
      val episode = Episode(live.root.toString, ThreeWorkflowRuntimeParameters.encode(scenario.host), scenario.plan.asJson, live.session,
        take(WorkflowInstanceRecordJsonV1.encodeC(current)), review.localTip, review.indexTree, review.remoteTip,
        output.invocation.stdout.toString, issued.issuedWorkContext.get.continuationId, _json(result), _json(reference), _json(execution), _stored(live.root), formal,
        _lines(live.root, "sbt-invocations.txt"), _lines(live.root, "sbt-command-lines.txt"), _lines(live.root, "lint-entered.txt"))
      Files.writeString(_episode_path, episode.asJson.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
      Files.setPosixFilePermissions(_episode_path, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
    }

    "continue REVIEW-INPUT retained review without check replay" in {
      assume(!_supplemental, "not selected in the supplemental before fixture")
      Given("the newly published normal CAR and the single saved old decision-needed Session with actual successful receipts")
      val episode = parse(Files.readString(_episode_path)).toOption.get.as[Episode].toOption.get
      val live = _reopen(episode)
      take(WorkflowInstanceRecordJsonV1.encodeC(_current(live))) shouldBe episode.originalRecord
      _stored(live.root).sorted shouldBe episode.nativeRows.sorted
      _retained(live, episode)
      git(live.root, "-C", live.repository.toString, "write-tree") shouldBe episode.indexTree
      val oldrequest = _requests(live).find(_.indexTree == episode.indexTree).get
      oldrequest.uncertainties.exists(_.startsWith("Unexamined")) shouldBe true
      val reference = V.WorkflowArtifactReference("fixture-review-input-independent-direction", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
      val answer = V.RepositorySyncValidationContinuationAnswer("AdoptInputClassificationAndContinueReview",
        "Independent fixture direction classifies only old unknown inputs and preserves all check applicability", false, false)
      def _describe_(): F.Result = live.repositoryCall(N.describeOperation, Vector("--sessionId", live.session))
      def _adopt_(value: V.RepositorySyncValidationContinuationAdoptionInput): F.Result =
        live.repositoryCall(N.adoptionOperation, Vector("--input", _json(value)))
      When("only the host mapping proposes README documentation but wrongly demotes selected consumed scripts and skills")
      _configure(live.scenario, corrected = true, demote = true)
      val initial = _snapshot(live)
      val badmapping = _describe_().business[V.RepositorySyncValidationContinuationDescriptionResult]
      Then("the passive description exposes issues and permits only Defer without changing old native facts")
      badmapping.choices shouldBe Vector("Defer")
      val badwire = take(badmapping.target.toJsonStringC)
      badwire should include("issues")
      _snapshot(live) shouldBe initial
      When("a proposed current fixed check selection drops lint coverage")
      _configure(live.scenario, corrected = true, checks = _checks.dropRight(1))
      val badchecks = _describe_().business[V.RepositorySyncValidationContinuationDescriptionResult]
      Then("changed applicability permits only Defer and never synthesizes another successful receipt")
      badchecks.choices shouldBe Vector("Defer")
      _snapshot(live) shouldBe initial
      When("the external host proposes the exact corrected README and consumed script skill classification")
      _configure(live.scenario, corrected = true)
      val described = _describe_()
      val description = described.business[V.RepositorySyncValidationContinuationDescriptionResult]
      val targetwire = parse(take(description.target.toJsonStringC)).toOption.get
      val target = R.decodePublicTarget(targetwire).toOption.get
      Then("the public compact v3 target binds the unchanged actual candidate and original decision-needed review")
      targetwire.hcursor.get[String]("continuation_policy") shouldBe Right("sm-workflow.repository-sync-review-input-continuation.v3")
      target.sessionId shouldBe episode.session
      target.candidate.tree shouldBe episode.indexTree
      target.candidate.before.head shouldBe episode.head
      target.candidate.mergeHead shouldBe episode.mergeHead
      target.review.continuationId shouldBe episode.continuationId
      target.review.result.identity shouldBe "fixture-review-input-independent-original"
      target.review.decision shouldBe "decision-needed"
      target.originalInputs.unknownPaths shouldBe _unknown
      target.proposedInputs.unknownPaths shouldBe empty
      target.proposedInputs.binaryPaths shouldBe empty
      target.proposedInputs.consumedPaths should contain allElementsOf Vector("scripts/probe.py", "skills/probe/SKILL.md")
      target.proposedInputs.consumedPaths should not contain "README.md"
      target.selectedChecks shouldBe _checks
      target.applicabilityIssues shouldBe empty
      if (_known_catalog) {
        target.originalInputs.changedPaths should contain(_catalog_path)
        target.originalInputs.consumedPaths should not contain _catalog_path
        target.originalInputs.unknownPaths should not contain _catalog_path
        target.proposedInputs.changedPaths should contain(_catalog_path)
        target.proposedInputs.consumedPaths should not contain _catalog_path
        target.proposedInputs.unknownPaths should not contain _catalog_path
        target.validationPolicy.impact.get.coverage.foreach(coverage => coverage.inputPaths should contain("src"))
        oldrequest.changedPaths should contain(_catalog_path)
        oldrequest.uncertainties should not contain ("Unexamined unknown input: " + _catalog_path)
      }
      description.choices shouldBe Vector(answer.choice, "Defer")
      _snapshot(live) shouldBe initial
      val input = V.RepositorySyncValidationContinuationAdoptionInput(live.session, description.target, answer.choice,
        answer.rationale, reference, false, false, execution)
      Vector(input, input.copy(sessionId = "foreign-session"), input.copy(choice = "AuthorizeMissingRequiredLint"),
        input.copy(priorExecutionEnded = true), input.copy(acceptOriginalUncertainty = true),
        input.copy(executionEvidence = execution.copy(workerIdentity = Some("foreign-participant")))).foreach { value =>
        When("an unsupported unprepared crossed or bogus UNKNOWN answer attempts adoption")
        val refused = _adopt_(value)
        Then("adoption refuses before effects and retains the exact old candidate and successful checks")
        refused.exitCode should not be 0
        _snapshot(live) shouldBe initial
      }
      val deferredreference = reference.copy(identity = reference.identity + "-defer")
      val deferredexecution = execution.copy(references = Vector(deferredreference))
      When("the independent fixture prepares imports and adopts an exact false false Defer answer")
      _prepare(live, "validation-continuation", described, _json(answer.copy(choice = "Defer")), deferredreference, deferredexecution)
      val deferred = _adopt_(input.copy(choice = "Defer", reference = deferredreference, executionEvidence = deferredexecution))
      Then("Defer leaves the actual saved original result history and check counters unchanged")
      deferred.business[V.RepositorySyncValidationContinuationAdoptionResult].accepted shouldBe false
      _snapshot(live) shouldBe initial
      When("the independent fixture prepares the exact adopting direction but a crossed reference revision is submitted")
      _prepare(live, "validation-continuation", described, _json(answer), reference, execution)
      val crossed = _adopt_(input.copy(reference = reference.copy(revision = "2")))
      Then("the foreign reference refuses without appending any direction")
      crossed.exitCode should not be 0
      _snapshot(live) shouldBe initial
      val stalewire = parse(_json(input)).toOption.get.mapObject(fields => fields.add("target",
        fields("target").get.mapObject(_.add("frontier_revision", "999999".asJson))))
      When("the direction carries a stale public frontier revision")
      val stale = live.repositoryCall(N.adoptionOperation, Vector("--input", stalewire.noSpaces))
      Then("the stale target refuses before any native or Git effect")
      stale.exitCode should not be 0
      _snapshot(live) shouldBe initial
      When("normal adoption receives the exact independently prepared and imported false false classification direction")
      val adopted = _adopt_(input).business[V.RepositorySyncValidationContinuationAdoptionResult]
      Then("only an append-only admitted direction is stored with no review issuance check execution or semantic acceptance")
      adopted.accepted shouldBe true
      _current(live).currentProgression shouldBe Some(ProgressionReference("sync.ReviewInputContinuationAdmitted"))
      _requests(live) should contain(oldrequest)
      _requests(live).size shouldBe 1
      _retained(live, episode)
      val admission = _snapshot(live)
      When("the old direction is adopted again after its frontier has moved")
      val duplicate = _adopt_(input)
      Then("duplicate adoption refuses without another history entry or any check")
      duplicate.exitCode should not be 0
      _snapshot(live) shouldBe admission
      When("a separate normal Advance issues a fresh independent review over the same full actual candidate")
      val output = live.advance()
      val issued = output.business[V.RepositorySyncAdvanceResult]
      val review = issued.review.get
      val revised = _requests(live).find(_.validatedRevision != oldrequest.validatedRevision).get
      Then("the new issue retains original rationale reference limitations full diffs and genuine successful receipts without replay")
      issued.issuedWorkContext.get.continuationId should not be episode.continuationId
      issued.issuedWorkContext.get.continuationId shouldBe live.session + ":native-review:" + revised.validatedRevision
      revised.indexTree shouldBe oldrequest.indexTree
      revised.localTip shouldBe oldrequest.localTip
      revised.remoteTip shouldBe oldrequest.remoteTip
      revised.changedPaths shouldBe oldrequest.changedPaths
      revised.localDiff shouldBe oldrequest.localDiff
      revised.remoteDiff shouldBe oldrequest.remoteDiff
      revised.checks shouldBe oldrequest.checks
      revised.selectedChecks shouldBe oldrequest.selectedChecks
      val context = revised.uncertainties.mkString("\n")
      context should include("fixture-review-input-independent-original")
      context should include(_rationale)
      context.toLowerCase should include("decision-needed")
      context should include("no typed accepted subcoverage")
      oldrequest.uncertainties.foreach(limit => context should include(limit))
      revised.uncertainties.exists(_.startsWith("Unexamined")) shouldBe false
      _requests(live).size shouldBe 2
      _retained(live, episode)
      val issuedstate = _snapshot(live)
      When("ordinary Advance recollects the pending fresh review without a Submit")
      val recollected = live.advance().business[V.RepositorySyncAdvanceResult]
      recollected.review shouldBe issued.review
      recollected.issuedWorkContext.get.continuationId shouldBe issued.issuedWorkContext.get.continuationId
      Then("the same fresh review is issued only once and the old completion remains retained")
      _requests(live).size shouldBe 2
      _snapshot(live) shouldBe issuedstate
      _retained(live, episode)
      val result = V.RepositorySyncNativeMergeReviewResult("accept",
        "Specification-owned independent review examined both complete actual parent diffs: resolved value, Scala, script and skill all use 1; README is documentation only. No old partial prose is accepted coverage and no live provider acceptance is claimed.", Vector.empty, None)
      val reviewreference = V.WorkflowArtifactReference("fixture-review-input-independent-follow-up", "1")
      val reviewexecution = V.WorkflowExecutionEvidence(Vector(reviewreference), Some("fixture-independent-follow-up-reviewer"), None, None)
      When("a distinct independent fixture result is prepared imported and submitted after reviewing the real tiny full candidate")
      review.localDiff.trim shouldBe git(live.root, "-C", live.repository.toString, "diff", "--cached", review.localTip, "--")
      review.remoteDiff.trim shouldBe git(live.root, "-C", live.repository.toString, "diff", "--cached", review.remoteTip, "--")
      _prepare(live, "review", output, _json(result), reviewreference, reviewexecution)
      val accepted = live.repositoryCall("submitNativeMergeReview", Vector("--sessionId", live.session, "--result", _json(result),
        "--resultReference", _json(reviewreference), "--executionEvidence", _json(reviewexecution)))
      Then("normal Submit accepts only the fresh independent review and retains original decision-needed evidence")
      accepted.business[V.RepositorySyncNativeMergeReviewSubmissionResult].accepted shouldBe true
      _retained(live, episode)
      val acceptedstate = _snapshot(live)
      val acceptedrows = _stored(live.root).sorted
      val acceptedrequests = _requests(live)
      // This exercises the durable revised source after independent Submit,
      // before acceptance is consumed by convergence. Interruption between
      // saving that source and issuing is not simulated; the parent's source
      // review qualifies that path through the shared applicability gate.
      val changedcoverage = _impact().copy(coverage = _impact().coverage.map(coverage =>
        coverage.copy(inputPaths = coverage.inputPaths :+ "another-consumed-input")))
      for ((checks, impact) <- Vector((_checks.dropRight(1), Option.empty[RepositorySyncProgramImpact.Policy]),
          (_checks, Some(changedcoverage)))) {
        When("CB-RRI-01 changes only external current check selection or consumed coverage after independent accept and before convergence")
        _configure(live.scenario, corrected = true, checks = checks, impact = impact)
        val refused = live.advance()
        Then("Advance refuses before effects with the same latest source issue receipts counters and native history")
        refused.exitCode should not be 0
        refused.invocation.logs should include("check applicability drifted")
        _snapshot(live) shouldBe acceptedstate
        _stored(live.root).sorted shouldBe acceptedrows
        _requests(live) shouldBe acceptedrequests
        _retained(live, episode)
      }
      _configure(live.scenario, corrected = true)
      When("ordinary Advance performs its existing local merge commit file-local bare push and final fetch")
      val terminal = live.advance().business[V.RepositorySyncAdvanceResult]
      Then("the same Session completes with unchanged checks and the expected genuine two-parent merge")
      terminal.kind shouldBe "Terminal"
      _current(live).identity.value shouldBe episode.session
      _current(live).lifecycle shouldBe Lifecycle.Completed
      _current(live).suspension shouldBe None
      val tip = git(live.root, "-C", live.repository.toString, "rev-parse", "HEAD")
      git(live.root, "-C", live.repository.toString, "show", "-s", "--format=%P", "HEAD").split(" ").toVector shouldBe
        Vector(episode.head, episode.mergeHead)
      git(live.root, "--git-dir=" + live.root.resolve("upstream.git"), "rev-parse", "refs/heads/main") shouldBe tip
      git(live.root, "-C", live.repository.toString, "rev-parse", "refs/remotes/origin/main") shouldBe tip
      git(live.root, "-C", live.repository.toString, "status", "--porcelain") shouldBe ""
      Files.exists(live.repository.resolve(".git/MERGE_HEAD")) shouldBe false
      _retained(live, episode)
      val completed = take(WorkflowInstanceRecordJsonV1.encodeC(_current(live)))
      val rows = _stored(live.root).sorted
      When("ordinary Advance recollects the terminal Session")
      val repeated = live.advance().business[V.RepositorySyncAdvanceResult]
      Then("terminal collection is passive and preserves the complete actual native records")
      repeated shouldBe terminal
      take(WorkflowInstanceRecordJsonV1.encodeC(_current(live))) shouldBe completed
      _stored(live.root).sorted shouldBe rows
      _retained(live, episode)
    }
  }
}
