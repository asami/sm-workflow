package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalTransitionJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.record.{Record, RecordPresentable}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncValidationContinuation as N
import RepositorySyncNativeProgressStore as S
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Fresh local remote and clone; all business execution uses ordinary public
  * CLI processes. Explicit installed mode pins one immutable published image.
  * Termination/authority attestations belong only to this isolated specification.
  * No native row is edited and no receipt is supplied as a process result.
  * @since Oct. 26, 2026
  * @version Oct. 26, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryValidationContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private lazy val _catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(RepositorySyncDefinitionBinding.bindC))))
  private lazy val _installed = sys.props.get("smworkflow.spec.installedCommand").map { value =>
    val path = Path.of(value)
    require(path.isAbsolute && path.toString == value && Files.isExecutable(path), "installedCommand requires an absolute existing executable")
    path
  }
  private lazy val _image = _installed.map { _ =>
    val path = Path.of(sys.props.getOrElse("smworkflow.spec.runtimeRoot",
      throw new IllegalArgumentException("Installed Repository continuation qualification requires smworkflow.spec.runtimeRoot")))
    require(path.isAbsolute && Files.isDirectory(path), "runtimeRoot requires an absolute immutable publication directory")
    path.toRealPath()
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _read(path: Path): Json = parse(Files.readString(path)).toOption.get
  private def _save(root: Path, name: String, value: String): String = {
    val path = root.resolve(name)
    Files.writeString(path, value)
    path.toUri.toASCIIString
  }
  private def _configure(scenario: F.Scenario, values: Map[String, String]): Unit = {
    val file = scenario.root.resolve(".textus/config.json")
    val original = _read(file)
    Files.writeString(file, original.mapObject(fields => values.foldLeft(fields) { case (f, (key, value)) =>
      f.add(key, Json.fromString(value)) }).noSpaces)
  }
  private def _call(scenario: F.Scenario, service: String, operation: String, arguments: Vector[String]): F.Result =
    _installed match {
      case Some(command) => scenario.callInstalled(command, service, operation, arguments, _image)
      case None if service == "RepositorySyncService" => scenario.repositoryCall(operation, arguments)
      case None =>
        arguments.take(1) shouldBe Vector("--input")
        scenario.prepareHostInput(operation, take(new org.goldenport.record.io.RecordDecoder().json(arguments(1))))
    }
  private def _prepare(scenario: F.Scenario, operation: String, input: RecordPresentable): F.Result =
    _call(scenario, "NativeWorkflowPreparationService", operation, Vector("--input", _json(input)))
  private def _repository(scenario: F.Scenario, operation: String, arguments: Vector[String]): F.Result =
    _call(scenario, "RepositorySyncService", operation, arguments)
  private def _git(root: Path, arguments: Vector[String], expectedexit: Int = 0): String = {
    require(root.isAbsolute && root.startsWith(Path.of("").toAbsolutePath.resolve("target/sm-workflow/work/native-cli")),
      "Git setup/observations must remain in the fresh specification root")
    val output = Files.createTempFile(root, "git-observation-", ".log")
    val argv = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects", "-c", "core.hooksPath=/dev/null",
      "-c", "core.fsmonitor=false", "-c", "credential.helper=", "-c", "gc.auto=0", "-c", "maintenance.auto=false") ++ arguments
    val builder = new ProcessBuilder(argv.asJava).directory(root.toFile).redirectErrorStream(true).redirectOutput(output.toFile)
    builder.environment().put("GIT_CONFIG_NOSYSTEM", "1")
    builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null")
    builder.environment().put("GIT_TERMINAL_PROMPT", "0")
    val process = builder.start()
    try {
      process.getOutputStream.close()
      process.waitFor(30, TimeUnit.SECONDS) shouldBe true
      withClue(Files.readString(output)) { process.exitValue() shouldBe expectedexit }
      Files.readString(output).trim
    } finally {
      if (process.isAlive) { process.destroy(); if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly(); process.waitFor() }
      process.getInputStream.close(); process.getErrorStream.close()
    }
  }
  private final case class Episode(scenario: F.Scenario, host: ThreeWorkflowRuntimeParameters.Definition,
    sessionId: String, selection: Path, originalHead: String) {
    val root: Path = scenario.root
    val repository: Path = Path.of(host.target.root)
    def advance(): F.Result = NativeRepositoryValidationContinuationCliSpec.this._repository(scenario,
      "advanceRepositorySync", Vector("--sessionId", sessionId))
    def describe(): F.Result = NativeRepositoryValidationContinuationCliSpec.this._repository(scenario,
      N.describeOperation, Vector("--sessionId", sessionId))
    def adopt(input: V.RepositorySyncValidationContinuationAdoptionInput): F.Result =
      NativeRepositoryValidationContinuationCliSpec.this._repository(scenario, N.adoptionOperation, Vector("--input", _json(input)))
    def correctBinding(): Unit = _configure(scenario, ThreeWorkflowNormalHostFixture.repositoryValidationContinuationValues(
      root, host, TypedSbtHostPlanFixture.normalExecutable, missingBinding = false))
  }
  /** Read-only qualification evidence from normal actual managed persistence. */
  private def _stored(episode: Episode): Vector[String] = {
    val database = episode.root.resolve("workflow.sqlite")
    val connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + database.toUri.toASCIIString + "?mode=ro", new java.util.Properties())
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
  private def _current(episode: Episode): InstanceRecord = {
    val values = _stored(episode).flatMap(WorkflowInstanceRecordJsonV1.decodeC(_, _catalog).toOption)
      .filter(_.identity.value == episode.sessionId)
    values.size shouldBe 1
    values.head
  }
  private def _receipts(episode: Episode) = _stored(episode).flatMap(raw =>
    WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, S.codec).toOption).filter(_.next.identity.value == episode.sessionId)
  private def _checks(episode: Episode): Vector[String] = {
    val file = episode.root.resolve("sbt-executions.txt")
    if (Files.exists(file)) Files.readString(file).linesIterator.toVector else Vector.empty
  }
  private def _episode(name: String): Episode = {
    val scenario = F.fresh(name, sbtRepair = Some(TypedSbtHostPlanFixture.normalExecutable))
    val clone = scenario.root.resolve("validation-clone")
    _git(scenario.root, Vector("clone", "--branch", "main", "--", scenario.host.target.endpoint, clone.toString))
    val host = scenario.host.copy(target = scenario.host.target.copy(root = clone.toString))
    _configure(scenario, ThreeWorkflowNormalHostFixture.repositoryValidationContinuationValues(scenario.root, host,
      TypedSbtHostPlanFixture.normalExecutable, missingBinding = true))
    Files.writeString(clone.resolve("value.txt"), "expected\n")
    val input = _save(scenario.root, "repository-start.json", _json(host.repository.input))
    val selected = _prepare(scenario, "prepareRepositoryStart", V.WorkflowRepositoryStartPreparationInput(input,
      name + "-selection", "participant", "ExplicitHuman", name + "-start", "entity:smworkflow.cli.repository_selections"))
    withClue(selected.invocation.logs) { selected.exitCode shouldBe 0 }
    scenario.selectPreparedImport(selected)
    val selection = scenario.root.resolve("actual-selected-row.json")
    Files.writeString(selection, _read(selected.invocation.stdout).hcursor.downField("datastore").downArray
      .downField("records").downArray.focus.get.noSpaces)
    val started = _repository(scenario, "startRepositorySync", Vector("--input", _json(host.repository.input))).business[V.RepositorySyncStartResult]
    val initial = Episode(scenario, host, started.sessionId, selection, "")
    val failed = initial.advance()
    failed.exitCode should not be 0
    failed.invocation.logs should include("sbt.execution.program-definition")
    val checkpoint = _git(scenario.root, Vector("-C", clone.toString, "rev-parse", "HEAD"))
    val result = initial.copy(originalHead = checkpoint)
    _current(result).currentProgression shouldBe Some(ProgressionReference("sync.IntegrationValidationChecking"))
    _current(result).history.count(_.currentProgression.contains(ProgressionReference("sync.CheckpointCreated"))) shouldBe 1
    _checks(result) shouldBe empty
    result
  }
  private def _direction(episode: Episode, described: F.Result, choice: String,
    provision: Boolean = true, ended: Boolean = true, uncertainty: Boolean = true): V.RepositorySyncValidationContinuationAdoptionInput = {
    val description = described.business[V.RepositorySyncValidationContinuationDescriptionResult]
    val directory = Files.createTempDirectory(episode.root, "specification-owned-terminal-attestation-")
    val reference = V.WorkflowArtifactReference(directory.getFileName.toString, "1")
    val answer = V.RepositorySyncValidationContinuationAnswer(choice,
      "Fixture-owned explicit path-scope direction: the actual prior CLI has returned and its process is terminated; original effects remain unknown",
      ended, uncertainty)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
    if (provision) {
      val input = V.WorkflowEvidencePreparationInput("RepositorySync", "validation-continuation",
        described.invocation.stdout.toUri.toASCIIString, _save(directory, "answer.json", _json(answer)),
        _save(directory, "reference.json", _json(reference)), _save(directory, "execution.json", _json(execution)),
        "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", Some("participant"))
      episode.scenario.selectPreparedImport(_prepare(episode.scenario, "prepareEvidence", input))
    }
    V.RepositorySyncValidationContinuationAdoptionInput(episode.sessionId, description.target, choice, answer.rationale,
      reference, ended, uncertainty, execution)
  }
  private def _effects(episode: Episode, component: String): Unit = {
    val target = _save(episode.root, "effect-target.json", episode.host.target.asJson.noSpaces)
    Vector("Push", "FinalFetch").foreach { effect =>
      val reference = _save(episode.root, effect + "-reference.json", _json(V.WorkflowArtifactReference("fixture-" + effect, "1")))
      val input = V.WorkflowRepositoryEffectPreparationInput(component, episode.selection.toUri.toASCIIString, target,
        effect, reference, "participant", "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence")
      episode.scenario.selectPreparedImport(_prepare(episode.scenario, "prepareRepositoryEffect", input))
    }
  }
  private def _complete(episode: Episode): V.RepositorySyncAdvanceResult = {
    val result = episode.advance().business[V.RepositorySyncAdvanceResult]
    result.kind shouldBe "Terminal"
    result.sessionId shouldBe episode.sessionId
    _current(episode).lifecycle shouldBe Lifecycle.Completed
    result.convergence.get.tip shouldBe _git(episode.root, Vector("-C", episode.repository.toString, "rev-parse", "HEAD"))
    result
  }

  "Public same-Session Repository validation continuation" should {
    Vector(false, true).foreach { changed =>
      s"qualify ${if (changed) "changed mixed staged and unstaged FULL_STATE" else "unchanged clean candidate prefix"} through ordinary source or explicitly installed CLI" in {
        Given("a fresh local bare remote/clone and a real checkpoint followed by missing-binding admission failure")
        When("the isolated setup reaches its actual resultless validation frontier")
        val e = _episode("repository-validation-" + changed)
        val before = _current(e)
        val old = _receipts(e)
        val originalintent = old.find(_.reference.identity == e.sessionId + ":native-validation-intent-1").get
        Then("the isolated missing-binding setup retains the original successful structural prefix")
        originalintent.payload.value.validation.get.receipts.map(_.identity) shouldBe Vector("index-local")
        Given("the selected unchanged or mixed staged/unstaged candidate and a corrected normal binding")
        if (changed) {
          Files.writeString(e.repository.resolve("staged.txt"), "explicit staged full-state content\n")
          _git(e.root, Vector("-C", e.repository.toString, "add", "--", "staged.txt"))
          Files.writeString(e.repository.resolve("docs/phase.md"), "explicit unstaged full-state content\n")
        }
        e.correctBinding()
        When("public description observes that current candidate")
        val described = e.describe()
        val description = described.business[V.RepositorySyncValidationContinuationDescriptionResult]
        val target = take(N.targetC(description.target))
        Then("the description preserves the old successful prefix and reports the actual dirty scope")
        target.successfulPrefix.map(_.identity) shouldBe Vector("index-local")
        target.actual.dirty.nonEmpty shouldBe changed
        Given("independently provisioned direction and effect evidence for that exact description")
        _effects(e, description.componentInstance)
        val input = _direction(e, described, if (changed) "AdoptCurrentFullState" else "AuthorizeNewValidation")
        val head = _git(e.root, Vector("-C", e.repository.toString, "rev-parse", "HEAD"))
        val index = Files.readAllBytes(e.repository.resolve(".git/index")).toVector
        When("the independently provisioned public adoption stores one direction without candidate or check effects")
        val adoption = e.adopt(input).business[V.RepositorySyncValidationContinuationAdoptionResult]
        Then("adoption appends only its native direction while HEAD, index and check effects remain unchanged")
        adoption.outcome shouldBe "adopted"
        val adopted = _current(e)
        adopted.history.dropRight(1) shouldBe before.history
        adopted.currentProgression shouldBe Some(ProgressionReference("sync.ValidationContinuationAdmitted"))
        _git(e.root, Vector("-C", e.repository.toString, "rev-parse", "HEAD")) shouldBe head
        Files.readAllBytes(e.repository.resolve(".git/index")).toVector shouldBe index
        _checks(e) shouldBe empty
        When("the same adoption is submitted again")
        val duplicate = e.adopt(input)
        Then("the stale duplicate refuses without another native direction")
        duplicate.exitCode should not be 0
        _current(e) shouldBe adopted
        Given("current same-path content remains inside the explicitly adopted path selection")
        if (changed) Files.writeString(e.repository.resolve("docs/phase.md"), "explicit unstaged full-state content at staging\n")
        When("a separate ordinary Advance performs only unfinished work in the fresh namespace")
        val terminal = _complete(e)
        Then("the same Session retains all old history and the original unknown intent while checks and candidate effects have exact scope")
        _current(e).history.take(before.history.size) shouldBe before.history
        _receipts(e) should contain allElementsOf old
        _receipts(e).find(_.reference == originalintent.reference).get shouldBe originalintent
        _receipts(e).exists(_.reference.identity == e.sessionId + ":native-validation-result-1") shouldBe false
        val namespace = N.namespace(target)
        val resultkeys = _receipts(e).filter(_.reference.identity.startsWith(e.sessionId + ":native-" + namespace + "-validation-result-"))
        resultkeys.flatMap(_.payload.value.validation).flatMap(_.receipts.lastOption).map(_.identity).toSet shouldBe
          (if (changed) Set("index-local", "test") else Set("test"))
        _checks(e).size shouldBe 1
        val checkpointcount = _current(e).history.count(_.currentProgression.contains(ProgressionReference("sync.CheckpointCreated")))
        checkpointcount shouldBe (if (changed) 2 else 1)
        if (changed) {
          _git(e.root, Vector("-C", e.repository.toString, "show", "HEAD:staged.txt")) shouldBe "explicit staged full-state content"
          _git(e.root, Vector("-C", e.repository.toString, "show", "HEAD:docs/phase.md")) shouldBe "explicit unstaged full-state content at staging"
          _git(e.root, Vector("-C", e.repository.toString, "rev-parse", "HEAD^")) shouldBe e.originalHead
        } else _git(e.root, Vector("-C", e.repository.toString, "rev-parse", "HEAD")) shouldBe e.originalHead
        val completed = _current(e)
        When("ordinary Advance is repeated after completion")
        val replay = e.advance().business[V.RepositorySyncAdvanceResult]
        Then("the terminal result replays without another history entry or check")
        replay shouldBe terminal
        _current(e) shouldBe completed
        _checks(e).size shouldBe 1
        if (_installed.nonEmpty) e.scenario.invocations.filter(_.argv.contains("command")).foreach(_.argv.head shouldBe _installed.get.toString)
      }
    }

    "deny missing terminal or uncertainty evidence, stale scope and implicit mixed staging while Defer writes nothing" in {
      Given("an actual resultless validation invocation that has returned to this isolated test host")
      val e = _episode("repository-validation-denials")
      val described = e.describe()
      val before = _current(e)
      val old = _receipts(e)
      Given("answers lacking dedicated evidence or explicit terminal/uncertainty acceptance")
      Vector(_direction(e, described, "AuthorizeNewValidation", provision = false),
        _direction(e, described, "AuthorizeNewValidation", provision = false, ended = false),
        _direction(e, described, "AuthorizeNewValidation", provision = false, uncertainty = false))
        .foreach { input =>
          When("public adoption receives that unsupported answer")
          val denied = e.adopt(input)
          Then("the missing independent evidence or explicit assertion refuses")
          denied.exitCode should not be 0
        }
      Given("an explicit Defer answer without terminal or uncertainty acceptance")
      val deferred = _direction(e, described, "Defer", ended = false, uncertainty = false)
      When("public adoption receives Defer")
      val deferral = e.adopt(deferred).business[V.RepositorySyncValidationContinuationAdoptionResult]
      Then("Defer stores nothing and performs no checks")
      deferral.outcome shouldBe "deferred"
      _current(e) shouldBe before
      _receipts(e) shouldBe old
      _checks(e) shouldBe empty
      Given("independent evidence for the earlier unchanged candidate")
      val held = _direction(e, described, "AuthorizeNewValidation")
      When("a later path changes that scope before adoption")
      Files.writeString(e.repository.resolve("additional.txt"), "later path\n")
      val stale = e.adopt(held)
      Then("the stale target refuses without changing the frontier")
      stale.exitCode should not be 0
      _current(e) shouldBe before
      Given("a fresh description of the changed candidate")
      val changed = e.describe()
      When("an unchanged-candidate answer is supplied for dirty work")
      val implicitadoption = e.adopt(_direction(e, changed, "AuthorizeNewValidation", provision = false))
      Then("dirty work cannot inherit unchanged-candidate authority")
      implicitadoption.exitCode should not be 0
      Given("explicit FULL_STATE evidence for the described index and paths")
      val fullstate = _direction(e, changed, "AdoptCurrentFullState")
      When("the index changes after that description and before adoption")
      _git(e.root, Vector("-C", e.repository.toString, "add", "--", "additional.txt"))
      val indexdrift = e.adopt(fullstate)
      Then("no denial or Defer mutates native progression or executes SBT")
      indexdrift.exitCode should not be 0
      _current(e) shouldBe before
      _checks(e) shouldBe empty
      _receipts(e) shouldBe old
    }

    Vector(false, true).foreach { conflicted =>
      s"refuse an actual ${if (conflicted) "unmerged index" else "pending merge"} without altering the original native intent" in {
        Given("an isolated resultless native validation with its original clean checkpoint")
        val e = _episode("repository-validation-merge-" + conflicted)
        val before = _current(e)
        val old = _receipts(e)
        _git(e.root, Vector("-C", e.repository.toString, "checkout", "--detach", "HEAD^"))
        val path = if (conflicted) "value.txt" else "other-branch.txt"
        Files.writeString(e.repository.resolve(path), "independent competing branch input\n")
        _git(e.root, Vector("-C", e.repository.toString, "add", "--", path))
        _git(e.root, Vector("-C", e.repository.toString, "-c", "user.name=Continuation Specification",
          "-c", "user.email=continuation@example.invalid", "commit", "--no-verify", "--no-gpg-sign", "-m", "Competing isolated input"))
        val competing = _git(e.root, Vector("-C", e.repository.toString, "rev-parse", "HEAD"))
        _git(e.root, Vector("-C", e.repository.toString, "checkout", "main"))
        When("normal Git creates the isolated pending merge or unmerged index")
        _git(e.root, Vector("-C", e.repository.toString, "-c", "user.name=Continuation Specification",
          "-c", "user.email=continuation@example.invalid", "merge", "--no-commit", "--no-ff", "--no-verify", competing),
          expectedexit = if (conflicted) 1 else 0)
        Then("the isolated pending merge has retained the original checkpoint HEAD")
        _git(e.root, Vector("-C", e.repository.toString, "rev-parse", "HEAD")) shouldBe e.originalHead
        When("public description observes a genuine merge frontier outside bounded nonmerge continuation")
        val refused = e.describe()
        Then("the existing merge owner route is required and no validation direction or effect is appended")
        refused.exitCode should not be 0
        _current(e) shouldBe before
        _receipts(e) shouldBe old
        _checks(e) shouldBe empty
      }
    }

    "require a fresh bounded adoption for a subsequent resultless continuation namespace" in {
      Given("the original missing binding and an unchanged candidate under one explicit adoption")
      val e = _episode("repository-validation-second-unknown")
      val first = e.describe()
      val original = take(N.targetC(first.business[V.RepositorySyncValidationContinuationDescriptionResult].target))
      val input = _direction(e, first, "AuthorizeNewValidation")
      When("the first bounded direction is adopted")
      val adoption = e.adopt(input).business[V.RepositorySyncValidationContinuationAdoptionResult]
      Then("the unchanged candidate has an explicit retained direction")
      adoption.accepted shouldBe true
      When("ordinary Advance encounters the still-missing binding in that namespace")
      val failed = e.advance()
      Then("the new invocation refuses with its own resultless frontier")
      failed.exitCode should not be 0
      val stopped = _current(e)
      When("ordinary Advance observes the retained unknown invocation again")
      val waiting = e.advance().business[V.RepositorySyncAdvanceResult]
      Then("it waits without moving the frontier")
      waiting.kind shouldBe "Wait"
      _current(e) shouldBe stopped
      When("a new public description observes the current resultless intent")
      val second = e.describe()
      val target = take(N.targetC(second.business[V.RepositorySyncValidationContinuationDescriptionResult].target))
      Then("the described target names the continuation namespace and its actual stopped revision")
      target.namespace shouldBe N.namespace(original)
      target.intentRevision shouldBe stopped.revision.value.toString
      When("the old direction is submitted for that later intent")
      val stale = e.adopt(input)
      Then("the old target cannot authorize another invocation")
      stale.exitCode should not be 0
      When("the test host supplies fresh ended-invocation evidence for that exact new intent and corrects its normal binding")
      e.correctBinding()
      _effects(e, second.business[V.RepositorySyncValidationContinuationDescriptionResult].componentInstance)
      val readoption = e.adopt(_direction(e, second, "AuthorizeNewValidation")).business[V.RepositorySyncValidationContinuationAdoptionResult]
      Then("the exact fresh direction is accepted")
      readoption.accepted shouldBe true
      When("separate ordinary Advance completes the unfinished work")
      _complete(e)
      Then("the checkpoint and original structural prefix remain once and the missing SBT executes once")
      _current(e).history.count(_.currentProgression.contains(ProgressionReference("sync.ValidationContinuationAdmitted"))) shouldBe 2
      _current(e).history.count(_.currentProgression.contains(ProgressionReference("sync.CheckpointCreated"))) shouldBe 1
      _receipts(e).count(_.reference.identity.endsWith("validation-result-0")) shouldBe 1
      _checks(e).size shouldBe 1
    }
  }
}
