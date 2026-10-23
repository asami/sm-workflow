package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.cncf.processexecution.GitOperationKind
import org.goldenport.convert.ValueReader
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F

/** Explicit consumer of a genuinely accepted, separately authorized C4 review.
  * The parent must establish native local effect authorization before execution.
  * Saved inputs attribute observations; they authenticate no operator or provider.
  * @since   Oct. 17, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
@DoNotDiscover
final class NativeGoalScopedQualityClosureCliSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import NativeCallerExecutionFixture.{record, take}

  private val _source = "src/main/scala/HistoryExample.scala"
  private val _message = "P61 isolated source-history Step"
  private val _process_key = "textus.subsystem.process-execution.plan"
  private def _property(name: String): String = sys.props.get(name).filter(_.trim.nonEmpty)
    .getOrElse(throw new AssertionError("Required genuine closure prerequisite is absent: " + name))
  private def _read(path: Path): Json = {
    require(Files.isRegularFile(path), "Required genuine closure input is absent: " + path)
    io.circe.parser.parse(Files.readString(path, UTF_8)).fold(
      error => throw new AssertionError(error.message), identity)
  }
  private def _decode[A: ValueReader](value: Json): A = take(summon[ValueReader[A]].readC(record(value)))
  private def _text(value: Json, key: String): String = value.hcursor.get[String](key).fold(
    _ => throw new AssertionError("Required input string is absent: " + key), identity)
  private def _array(value: Json, key: String): Vector[Json] = value.hcursor.downField(key).focus
    .flatMap(_.asArray).map(_.toVector).getOrElse(throw new AssertionError("Required input array is absent: " + key))
  private def _local_file(path: Path, root: Path): Path = {
    require(path.isAbsolute && path.normalize == path && path.startsWith(root) &&
      Files.isRegularFile(path) && path.toRealPath() == path,
      "Select an existing normalized scenario-owned file without a symlink alias: " + path)
    path
  }
  private def _save(directory: Path, name: String, text: String): Path = {
    val path = directory.resolve(name)
    Files.writeString(path, text, UTF_8, StandardOpenOption.CREATE_NEW)
    path
  }
  private def _retain(directory: Path, name: String, result: F.Result): Path = {
    val output = _save(directory, name + ".stdout.json", Files.readString(result.invocation.stdout, UTF_8))
    _save(directory, name + ".stderr", Files.readString(result.invocation.stderr, UTF_8))
    output
  }
  private def _executions(root: Path): Vector[Long] =
    Files.readString(root.resolve("sbt-executions.txt"), UTF_8).linesIterator.map(_.toLong).toVector

  // Finite native observations only; Step commit execution belongs to typed Git/UoW.
  private enum GitRead {
    case Head, Status
    case Commit(identity: String)
    case ChangedPaths(parent: String, commit: String)
  }
  private def _git(directory: Path, repository: Path, name: String, observation: GitRead): String = {
    def _oid_(value: String): String = {
      require(value.matches("[0-9a-f]{40}"), "Native Git observation requires an actual SHA-1 object identity")
      value
    }
    val arguments = observation match {
      case GitRead.Head => Vector("rev-parse", "--verify", "HEAD")
      case GitRead.Status => Vector("status", "--porcelain=v1", "--untracked-files=all")
      case GitRead.Commit(value) => Vector("show", "--no-patch", "--format=%H%n%P%n%T%n%an%n%ae%n%B", _oid_(value), "--")
      case GitRead.ChangedPaths(parent, commit) =>
        Vector("diff", "--no-ext-diff", "--no-textconv", "--name-only", _oid_(parent), _oid_(commit), "--")
    }
    val argv = Vector("/usr/bin/git", "--no-optional-locks", "--no-replace-objects",
      "-c", "core.hooksPath=/dev/null", "-c", "core.fsmonitor=false", "-c", "credential.helper=",
      "-c", "gc.auto=0", "-c", "maintenance.auto=false", "-C", repository.toString) ++ arguments
    val stdout = _save(directory, name + ".git.stdout", "")
    val stderr = _save(directory, name + ".git.stderr", "")
    val builder = new ProcessBuilder(argv.asJava).directory(repository.toFile)
      .redirectOutput(stdout.toFile).redirectError(stderr.toFile)
    builder.environment().clear()
    Map("PATH" -> "/usr/bin:/bin", "GIT_TERMINAL_PROMPT" -> "0", "GIT_CONFIG_NOSYSTEM" -> "1",
      "GIT_CONFIG_GLOBAL" -> "/dev/null", "LC_ALL" -> "C").foreach { (key, value) =>
      builder.environment().put(key, value)
    }
    val process = builder.start()
    try {
      process.getOutputStream.close()
      require(process.waitFor(30, TimeUnit.SECONDS), "Finite native Git observation timed out: " + stdout)
      require(process.exitValue() == 0, "Native Git observation failed: " + Files.readString(stderr, UTF_8))
      Files.readString(stdout, UTF_8)
    } finally {
      if (process.isAlive) {
        process.destroy()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
          process.destroyForcibly()
          require(process.waitFor(5, TimeUnit.SECONDS), "Native Git observation could not be reaped")
        }
      }
      process.getInputStream.close()
      process.getErrorStream.close()
      process.getOutputStream.close()
    }
  }

  "Ordinary C4 accepted review closure" should afterWord(
    "in spec:native-host-review-preparation, example:E11, rules:R3,R4,R5,R7,R8") {
    "consume the saved authority Wait then close exactly one local Step and stop at pending Phase review" in {
      Given("an explicit retained C4 root, actual accepted Submit output, actual saved StepClosure Wait and separately authorized operator reference")
      val root = Path.of(_property("smworkflow.spec.scopedQualityRoot"))
      val scenario = F.reopenSourceHistory(root, TypedSbtHostPlanFixture.normalExecutable, startingLogIndex = 2100)
      val task = root.resolve("target/sm-workflow/tasks/provider-source-history-task")
      val session = scenario.retrieve(task)
      val reviewed = _decode[V.GoalPhaseAdvanceResult](_read(root.resolve("provider-review.json")))
      val acceptedfile = _local_file(Path.of(_property("smworkflow.spec.scopedQualitySubmit")), root)
      val waitfile = _local_file(Path.of(_property("smworkflow.spec.scopedQualityWait")), root)
      val referenceuri = URI.create(_property("smworkflow.spec.scopedQualityAuthorityReference"))
      require(referenceuri.getScheme == "file" && referenceuri.normalize() == referenceuri,
        "Authority reference must be an explicit normalized local file URI")
      val referencefile = _local_file(Path.of(referenceuri), root)
      val configfile = root.resolve(".textus/config.json")
      val config = _read(configfile)
      val issuer = _text(config, NativeExternalEvidence.prefix + "issuer")
      val startinput = _read(root.resolve("selected-input.json"))
      val selections = _array(startinput, "datastore").filter(value =>
        _text(value, "collection") == "entity:smworkflow.cli.goal_selection")
      require(selections.size == 1, "Retained actual Start input must select exactly one Goal selection collection")
      val startrows = _array(selections.head, "records")
      require(startrows.size == 1, "Retained actual Start input must contain exactly one selection")
      val selected = take(ProfileSelectionJsonV1.decodeStorageC(_text(startrows.head, ProfileSelectionJsonV1.storageField)))
      val repository = Path.of(scenario.host.target.root)
      val source = repository.resolve(_source)
      val beforesource = Files.readAllBytes(source).toVector
      val beforechecks = _executions(root)

      When("the explicit accepted output and reference are decoded before any configuration write or command")
      val accepted = _decode[V.GoalPhaseWorkSubmissionResult](_read(acceptedfile))
      val reference = _decode[V.WorkflowArtifactReference](_read(referencefile))
      Then("only a genuinely accepted original session is eligible; missing or negative prerequisites fail closed")
      withClue("A separately obtained actual accepted Submit output is required; this spec never invokes Submit: ") {
        accepted.accepted shouldBe true
        accepted.outcome shouldBe "accepted"
        accepted.sessionId shouldBe reviewed.sessionId
        accepted.sessionId shouldBe session
      }
      accepted.message.trim should not be empty
      reference.identity.trim should not be empty
      reference.revision.trim should not be empty
      issuer.trim should not be empty
      selected.participantIdentity.trim should not be empty
      reviewed.kind shouldBe "Request"
      reviewed.work.get.kind shouldBe "ReviewStep"
      reviewed.work.get.stepIdentity shouldBe Some("step-1")
      reviewed.work.get.completed shouldBe empty
      reviewed.work.get.review.get.changedPaths shouldBe Vector(_source)
      reviewed.work.get.selectedPlan.identity shouldBe scenario.plan.identity
      reviewed.work.get.selectedPlan.revision shouldBe scenario.plan.revision
      reviewed.work.get.candidate.get.head shouldBe reviewed.work.get.originalBase.head
      val text = new String(beforesource.toArray, UTF_8)
      text should startWith("/**\n * @since ")
      text should endWith(" */\nobject HistoryExample { val value: Int = 42 }\n")
      text.linesIterator.count(_.contains("@since")) shouldBe 1
      text.linesIterator.count(_.contains("@version")) shouldBe 1
      beforechecks.size shouldBe 1

      Given("the current isolated process plan already containing the typed closure declarations and their normal owner grant")
      val processplan = io.circe.parser.parse(_text(config, _process_key)).fold(
        error => throw new AssertionError(error.message), identity)
      When("the existing capabilities and SmWorkflow grant are selected without appending or rewriting configuration")
      val gitprograms = _array(processplan, "gitPrograms")
      val capabilities = (_array(processplan, "programs") ++ _array(processplan, "sbtPrograms") ++ gitprograms)
        .map(value => _text(value, "capability"))
      val metadatacapability = TypedGitHostPlanFixture.capability(GitOperationKind.CommitMetadata)
      val commitcapability = TypedGitHostPlanFixture.capability(GitOperationKind.CommitSelected, Some("goal-step-1"))
      val owner = org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name
      val grants = _array(processplan, "grants")
      val ownergrants = grants.filter(value => _text(value, "componentId") == owner)
      Then("the unique existing declarations and the actual normal owner grant contain both fixed closure capabilities")
      capabilities.distinct shouldBe capabilities
      gitprograms.map(value => _text(value, "capability")) should contain allOf (metadatacapability, commitcapability)
      ownergrants.size shouldBe 1
      val granted = _array(ownergrants.head, "capabilities").map(_.asString.get)
      granted.distinct shouldBe granted
      granted should contain allOf (metadatacapability, commitcapability)

      Given("accepted ReviewStep, its saved missing-authority Wait and the original staged candidate without a Step commit")
      val directory = Files.createTempDirectory(root, "quality-closure-continuation-")
      val beforehead = _git(directory, repository, "before", GitRead.Head).trim
      beforehead shouldBe reviewed.work.get.originalBase.head
      When("the actual saved normal Wait is decoded without repeating its Advance")
      val waiting = _decode[V.GoalPhaseAdvanceResult](_read(waitfile))
      Then("one StepClosure context retains the exact session Plan Step, reviewed candidate and successful validation without another commit or Test")
      waiting.kind shouldBe "Wait"
      waiting.sessionId shouldBe session
      waiting.work shouldBe None
      waiting.completion shouldBe None
      waiting.validation.size shouldBe 1
      val validation = waiting.validation.get
      validation.outcome shouldBe Some("passed")
      validation.scopeIdentity shouldBe "step-1"
      validation.candidate shouldBe reviewed.work.get.candidate.get
      validation.selectedChecks shouldBe Vector(V.GoalPhaseNativeValidationCheck("test", "test", None, None))
      validation.checks shouldBe reviewed.work.get.review.get.checks
      waiting.authorityContext.size shouldBe 1
      val authority = waiting.authorityContext.get
      authority.purpose shouldBe NativeExternalEvidence.Purpose.StepClosure.identity
      authority.componentInstance.trim should not be empty
      val scope = NativeCallerExecutionFixture.json(authority.scope)
      _text(scope, "instance") shouldBe session
      _text(scope, "plan") shouldBe reviewed.work.get.selectedPlan.identity
      _text(scope, "plan_revision") shouldBe reviewed.work.get.selectedPlan.revision
      _text(scope, "step") shouldBe "step-1"
      _text(scope, "review_evidence").trim should not be empty
      scope.hcursor.downField("parent").focus.get shouldBe NativeCallerExecutionFixture.json(reviewed.work.get.originalBase.toRecord())
      scope.hcursor.downField("candidate").focus.get shouldBe NativeCallerExecutionFixture.json(reviewed.work.get.candidate.get.toRecord())
      _git(directory, repository, "waiting", GitRead.Head).trim shouldBe beforehead
      _executions(root) shouldBe beforechecks
      Files.readAllBytes(source).toVector shouldBe beforesource

      Given("the actual saved Wait and explicit parent-authorized reference, retained Start participant and current issuer")
      val preparationinput = V.WorkflowAuthorityPreparationInput(waitfile.toUri.toASCIIString,
        referenceuri.toASCIIString, selected.participantIdentity, issuer, Instant.now().toString,
        "entity:smworkflow.cli.host_evidence")
      When("normal prepareAuthority exports standard StartupImport data and selects only its actual output")
      val prepared = scenario.prepareHostInput("prepareAuthority", preparationinput.toRecord())
      Then("the preparation succeeds without moving Git HEAD or starting another ProductCheck")
      prepared.exitCode shouldBe 0
      _array(_read(prepared.invocation.stdout), "datastore").size shouldBe 1
      _retain(directory, "prepared-authority", prepared)
      _git(directory, repository, "prepared", GitRead.Head).trim shouldBe beforehead
      _executions(root) shouldBe beforechecks

      Given("the actual normal preparation output selected for StartupImport without a direct authority or database write")
      When("normal Advance imports the prepared row and performs typed local Step closure and the Phase ProductCheck")
      scenario.selectPreparedImport(prepared)
      val phaseresult = scenario.advance(task)
      val phase = phaseresult.business[V.GoalPhaseAdvanceResult]
      Then("exactly one step-1 commit establishes the reviewed parent and tree before requesting independent ReviewPhase")
      phase.kind shouldBe "Request"
      phase.sessionId shouldBe session
      phase.completion shouldBe None
      phase.authorityContext shouldBe None
      phase.submitSessionId shouldBe Some(session)
      phase.issuedWorkContext should not be empty
      val work = phase.work.get
      work.kind shouldBe "ReviewPhase"
      work.selectedPlan shouldBe reviewed.work.get.selectedPlan
      work.originalBase shouldBe reviewed.work.get.originalBase
      work.completed.map(_.step) shouldBe Vector("step-1")
      val closed = work.completed.head
      val commit = closed.commit
      closed.review.exists(_.trim.nonEmpty) shouldBe true
      commit.identity should not be beforehead
      commit.parent shouldBe beforehead
      commit.tree shouldBe reviewed.work.get.candidate.get.tree
      work.candidate.get.head shouldBe commit.identity
      work.candidate.get.tree shouldBe commit.tree
      work.checkFailure shouldBe None
      work.review.get.changedPaths shouldBe Vector(_source)
      work.review.get.checks.size shouldBe 1
      val check = work.review.get.checks.head
      check.identity shouldBe "test"
      check.termination shouldBe "exited"
      check.exitCode shouldBe Some(0)
      check.sbt.get.kind shouldBe "test"
      check.sbt.get.outcome shouldBe "succeeded"
      check.stdout should include("PRODUCT_CHECK_ENTERED " + repository)
      check.stdout should include("PRODUCT_CHECK_PASSED")
      val afterchecks = _executions(root)
      afterchecks.take(beforechecks.size) shouldBe beforechecks
      afterchecks.size shouldBe beforechecks.size + 1
      afterchecks.distinct.size shouldBe afterchecks.size
      afterchecks should not contain ProcessHandle.current().pid()
      afterchecks should not contain phaseresult.invocation.pid
      _git(directory, repository, "closed", GitRead.Head).trim shouldBe commit.identity
      val metadata = _git(directory, repository, "commit", GitRead.Commit(commit.identity)).split("\n", 6).toVector
      metadata.take(5) shouldBe Vector(commit.identity, beforehead, commit.tree, "Workflow Fixture", "workflow@example.invalid")
      metadata.last.trim shouldBe _message
      _git(directory, repository, "paths", GitRead.ChangedPaths(beforehead, commit.identity)).linesIterator.toVector shouldBe Vector(_source)
      _git(directory, repository, "clean", GitRead.Status) shouldBe ""
      Files.readAllBytes(source).toVector shouldBe beforesource
      _retain(directory, "phase-review", phaseresult)

      Given("the pending real Phase review with its actual local commit and completed Phase check")
      When("another normal Advance rereads the pending work without a Phase review submission")
      val repeatedresult = scenario.advance(task)
      val repeated = repeatedresult.business[V.GoalPhaseAdvanceResult]
      Then("identical pending Phase review retains the original session and no additional Test or commit")
      repeated shouldBe phase
      _git(directory, repository, "repeated", GitRead.Head).trim shouldBe commit.identity
      _git(directory, repository, "repeated-clean", GitRead.Status) shouldBe ""
      _executions(root) shouldBe afterchecks
      Files.readAllBytes(source).toVector shouldBe beforesource
      _retain(directory, "repeated-phase-review", repeatedresult)
      scenario.invocations.size shouldBe 3
      scenario.invocations.map(_.pid).distinct.size shouldBe scenario.invocations.size
      scenario.invocations.foreach { invocation =>
        invocation.argv should contain("org.goldenport.cncf.CncfMain")
        invocation.argv should contain("command")
        invocation.argv should not contain GoalPhaseNativeRun.completionOperation.operation
        invocation.pid should not be ProcessHandle.current().pid()
      }
    }
  }
}
