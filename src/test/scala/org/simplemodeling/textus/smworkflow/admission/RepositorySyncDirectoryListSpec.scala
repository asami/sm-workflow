package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.WorkspaceId
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take

/** Executable directory-list intake and clean native-batch contract. These
  * authored fixtures are not installed qualification or an execution receipt.
  * @since   Nov.  5, 2026
  * @version Nov.  6, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class RepositorySyncDirectoryListSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with Phase69ConfiguredRepositoryFixture {
  private val _scenarios = Vector("Equal", "LocalAhead", "RemoteAhead")
  private def _root(root: Path, ordinal: Int): Path = root.resolve("checkout-" + ordinal)
  private def _directory(root: Path, ordinal: Int): String = _root(root, ordinal).resolve("repository").toString
  private def _workspace(ordinal: Int): String = "batch-repository-" + ordinal

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val template = super.process_plan(root, scenario, goal)
    val repository = template.hcursor.get[Vector[Json]]("gitRepositories").toOption.get.head
    val repositories = _scenarios.indices.map { ordinal => repository.mapObject(fields => fields
      .add("target", Json.fromString(_workspace(ordinal)))
      .add("identity", Json.fromString("batch-git-" + ordinal))
      .add("capability", Json.fromString("batch-git-" + ordinal))
      .add("localBranch", Json.fromString("work-batch"))
      .add("remoteBranch", Json.fromString("release"))
      .add("endpoint", Json.fromString(_root(root, ordinal).resolve("upstream.git").toString))) }.toVector
    val workspaces = _scenarios.indices.map(ordinal => Json.obj("identity" -> Json.fromString(_workspace(ordinal)),
      "root" -> Json.fromString(_directory(root, ordinal)))).toVector
    val workspacegrants = _scenarios.indices.map(ordinal => Json.obj(
      "componentId" -> Json.fromString("org.simplemodeling.textus.SmWorkflow"),
      "workspaceId" -> Json.fromString(_workspace(ordinal)), "access" -> Json.fromString("manage"))).toVector
    template.mapObject(fields => fields.add("gitRepositories", Json.fromValues(repositories))
      .add("workspaces", Json.fromValues(workspaces)).add("workspaceGrants", Json.fromValues(workspacegrants))
      .add("grants", Json.arr(Json.obj("componentId" -> Json.fromString("org.simplemodeling.textus.SmWorkflow"),
        "capabilities" -> Json.fromValues(_scenarios.indices.map(ordinal => Json.fromString("batch-git-" + ordinal)))))))
  }

  private def _plan: Json = Json.obj("workspaces" -> Json.arr(
    Json.obj("identity" -> Json.fromString("first"), "root" -> Json.fromString("/fixture/repository")),
    Json.obj("identity" -> Json.fromString("checks"), "root" -> Json.fromString("/fixture/repository")),
    Json.obj("identity" -> Json.fromString("worktree"), "root" -> Json.fromString("/fixture/worktree"))),
    "gitRepositories" -> Json.arr(_repository("first"), _repository("worktree")))
  private def _repository(workspace: String): Json = Json.obj("target" -> Json.fromString(workspace),
    "identity" -> Json.fromString("repository-" + workspace), "localBranch" -> Json.fromString("work/no-upstream"),
    "remote" -> Json.fromString("origin"), "remoteBranch" -> Json.fromString("release"),
    "endpoint" -> Json.fromString("configured-endpoint"))

  "Directory-list RepositorySync" should {
    "resolve duplicate paths once while retaining distinct worktrees and configured destinations" in {
      Given("two configured worktrees, a validation workspace alias and no caller tracking metadata")
      val paths = Vector("/fixture/repository", "/fixture/repository/../repository", "/fixture/worktree")
      When("runtime intake resolves the configured Git bindings before remote observation")
      val selected = take(RepositorySyncDirectoryList.resolveC(paths, _plan, "batch"))
      Then("checkout identity, source branch and non-main destination stay separate")
      selected.map(_.workspace) shouldBe Vector("first", "worktree")
      selected.map(_.sessionId) shouldBe Vector("batch:repository-1", "batch:repository-2")
      selected.map(_.localBranch).distinct shouldBe Vector("work/no-upstream")
      selected.map(_.remoteBranch).distinct shouldBe Vector("release")
      selected.map(_.endpoint).distinct shouldBe Vector("configured-endpoint")
    }

    "refuse absent or ambiguous destinations without guessing a remote branch" in {
      Given("missing and conflicting configured repository targets")
      val missing = _plan.mapObject(_.add("gitRepositories", Json.arr()))
      val ambiguous = _plan.mapObject(_.add("gitRepositories", Json.arr(_repository("first"), _repository("first"))))
      val absentbranch = _plan.mapObject(_.add("gitRepositories", Json.arr(_repository("first").mapObject(_.remove("remoteBranch")))))
      When("intake resolves the same selected checkout under each configuration")
      val results = Vector(missing, ambiguous, absentbranch).map(plan =>
        RepositorySyncDirectoryList.resolveC(Vector("/fixture/repository"), plan, "batch"))
      Then("each returns its real configuration gap before any execution")
      results.foreach(_.isSuccess shouldBe false)
      RepositorySyncDirectoryList.decodeDirectoriesC(Json.arr(Json.fromString("relative"))).isSuccess shouldBe false
      RepositorySyncDirectoryList.decodeDirectoriesC(Json.arr()).isSuccess shouldBe false
    }

    "honor explicit origin main and non-main destinations without adopting caller target guesses" in {
      Given("a no-upstream source branch with an explicit configured destination")
      Vector("main", "release/next").foreach { destination =>
        val configured = _repository("first").mapObject(_.add("remoteBranch", Json.fromString(destination)))
        val plan = _plan.mapObject(_.add("gitRepositories", Json.arr(configured)))
        val member = take(RepositorySyncDirectoryList.resolveC(Vector("/fixture/repository"), plan, "explicit-target")).head
        def _request_(extra: (String, String)*): Request = Request.of(component = "sm-workflow",
          service = "RepositorySyncService", operation = "startRepositorySync",
          properties = List(Property("input", Record.data((Vector(
            "repositoryIdentity" -> "repository-first", "syncMode" -> "FULL_STATE") ++ extra)*), None)))

        When("the ordinary Start consumes the resolved binding before any remote observation")
        val selected = take(StandardRepositorySyncIntent.inputC(_request_(), member.target))
        val explicit = take(StandardRepositorySyncIntent.inputC(_request_("trackingRemote" -> "origin",
          "trackingBranch" -> destination, "localBranch" -> "work/no-upstream"), member.target))

        Then("the source stays distinct and mismatched optional target fields fail rather than override configuration")
        selected shouldBe explicit
        selected.localBranch shouldBe "work/no-upstream"
        selected.trackingRemote shouldBe "origin"
        selected.trackingBranch shouldBe destination
        StandardRepositorySyncIntent.inputC(_request_("trackingBranch" -> "guessed"), member.target).isFaillure shouldBe true
        StandardRepositorySyncIntent.inputC(_request_("localBranch" -> destination), member.target).isFaillure shouldBe true
        StandardRepositorySyncIntent.inputC(_request_("remoteEndpointIdentity" -> "unconfigured"), member.target).isFaillure shouldBe true
      }
    }

    "distinguish an observed absent remote ref from failed or incomplete remote access" in {
      Given("complete no-match and command-failure receipts plus an incomplete no-match capture")
      val empty = ProcessExecutionCapture(Vector.empty, 0, false)
      val diagnostic = ProcessExecutionCapture(Vector(1.toByte), 1, false)
      def _result_(code: Int, stderr: ProcessExecutionCapture): GitExecutionResult[GitRemoteTip] =
        GitExecutionResult(GitTarget(take(WorkspaceId.parseC("remote-fixture"))), GitOperation.RemoteTip,
          GitExecutionOutcome.Failed(code), None, ProcessExecutionResult(ProcessExecutionTermination.Exited(code),
            empty, stderr, Vector.empty, 1, "fixture-remote-tip"))
      val absent = _result_(2, empty)
      val command = _result_(128, diagnostic)
      val incomplete = _result_(2, empty.copy(truncated = true))
      When("the consuming observation owner interprets the actual typed terminal facts")
      val missing = RepositorySyncManagedGitObservation.remoteTipResultC(absent)
      val failed = RepositorySyncManagedGitObservation.remoteTipResultC(command)
      val unproven = RepositorySyncManagedGitObservation.remoteTipResultC(incomplete)
      Then("only a complete empty no-match result says that the configured remote ref is absent")
      missing.isSuccess shouldBe false
      missing.toString should include("no matching advertised branch")
      failed.toString should include("remote-tip")
      failed.toString should not include "no matching advertised branch"
      unproven.toString should not include "no matching advertised branch"
    }

    "finish a mixed clean batch in one normal operation and recollect exact retained results without replay" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/directory-list-sync").toAbsolutePath), "mixed-")
      _scenarios.indices.foreach(ordinal => Files.createDirectories(Path.of(_directory(root, ordinal))))
      val directories = (_scenarios.indices.map(ordinal => _directory(root, ordinal)).toVector :+ _directory(root, 0))
      val parameters = Map(RepositorySyncDirectoryList.declaration.name -> Json.fromValues(directories.map(Json.fromString)).noSpaces)
      var result = Json.Null
      var session = ""
      var revision = ""
      Given("equal, push-only and fast-forward repositories on no-upstream work branches with release destinations")
      _with_runtime_(root, "Equal", hostParameters = parameters) { (owner, component, actor, prepare) =>
        prepare(_ => _scenarios.zipWithIndex.foldLeft(Consequence.unit) { case (prior, (scenario, ordinal)) =>
          val commands = _setup_commands_(_root(root, ordinal), scenario).map(command => command.map {
            case "--initial-branch=main" => if (command.contains("--bare")) "--initial-branch=release" else "--initial-branch=work-batch"
            case argument => argument.replace("refs/heads/main", "refs/heads/release")
          })
          commands.foldLeft(prior)((previous, command) => previous.flatMap(_ => _run_setup_command_(command)))
        })
        When("the authenticated caller requests FULL_STATE for the configured directory list")
        val response = take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "RepositorySyncService", operation = RepositorySyncDirectoryList.syncOperation,
          properties = List(Property("input", Record.data("syncMode" -> "FULL_STATE"), None))), actor))
        result = _json(response)
        session = result.hcursor.get[String]("sessionId").toOption.get
        revision = result.hcursor.downField("resultReference").get[String]("revision").toOption.get
        Then("all three actual native Sessions converge and the duplicate creates no fourth execution")
        result.hcursor.get[String]("outcome").toOption shouldBe Some("Completed")
        val repositories = result.hcursor.get[Vector[Json]]("repositories").toOption.get
        repositories.size shouldBe 3
        repositories.map(_.hcursor.get[String]("kind").toOption.get).distinct shouldBe Vector("Terminal")
        repositories.map(_.hcursor.get[String]("outcome").toOption.get).distinct shouldBe Vector("converged")
        repositories.map(_.hcursor.get[String]("sessionId").toOption.get).distinct.size shouldBe 3
        repositories.foreach(_.hcursor.get[String]("tip").toOption.get should fullyMatch regex "[0-9a-f]{40}")
        result.hcursor.get[Vector[String]]("notStarted").toOption shouldBe Some(Vector.empty)
        result.noSpaces should not include "stdout"
        result.noSpaces should not include "progressSnapshot"
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      }
      When("a fresh runtime reads the selected revision and recollects the same aggregate Session")
      _with_runtime_(root, "Equal", hostParameters = parameters) { (owner, component, actor, _) =>
        val read = take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "RepositorySyncService", operation = RepositorySyncDirectoryList.readOperation,
          properties = List(Property("sessionId", session, None), Property("revision", revision, None))), actor))
        val collected = take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "RepositorySyncService", operation = RepositorySyncDirectoryList.syncOperation,
          properties = List(Property("sessionId", session, None),
            Property("input", Record.data("syncMode" -> "FULL_STATE"), None))), actor))
        Then("both expose the same retained native result without another fetch, commit, push or check")
        _json(read) shouldBe result
        _json(collected) shouldBe result
        result.hcursor.get[Vector[Json]]("repositories").toOption.get.foreach { repository =>
          val child = repository.hcursor.get[String]("sessionId").toOption.get
          val completed = take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "RepositorySyncService", operation = "advanceRepositorySync",
            properties = List(Property("sessionId", child, None))), actor))
          _json(completed).hcursor.get[String]("kind").toOption shouldBe Some("Terminal")
          _json(completed).hcursor.get[String]("outcome").toOption shouldBe Some("converged")
        }
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "RepositorySyncService", operation = RepositorySyncDirectoryList.readOperation,
          properties = List(Property("sessionId", session, None), Property("revision", "999999", None))), actor)
          .isSuccess shouldBe false
      }
    }
  }

  private def _json(response: OperationResponse): Json = response match {
    case OperationResponse.RecordResponse(record) => DevelopmentOperationalView.json(record)
    case other => fail("Expected native directory-list business record: " + other)
  }
}
