package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation, GitStatusEntry}
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take
import RepositorySyncPurpose as P

/** Public directory entry exercises the existing purpose candidate owner in a
  * non-default configured workspace. Fixture authorship is not qualification.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class RepositorySyncDirectoryListPurposeSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  override protected def standard_runtime = true
  private val original = (1 to 7).map(n => s"original line $n\n").mkString
  private val first = original.replace("line 1", "purpose A")
  private val last = original.replace("line 7", "purpose B")
  private val both = first.replace("line 7", "purpose B")
  private val paths = Vector("shared.md", "staged.md", "unstaged.md", "untracked.md")
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val descriptor = take(GenericSubsystemDescriptor.loadComponentArchive(Path.of("src/main/car").toAbsolutePath))
    val template = io.circe.parser.parse(descriptor.config("textus.subsystem.process-execution.plan")).toOption.get
    val values = Map("repository.root" -> root.resolve("repository").toString, "git.branch" -> "main",
      "git.remote" -> "origin", "git.remote-branch" -> "main", "git.endpoint" -> root.resolve("upstream.git").toString,
      "git.user-name" -> "Fixture", "git.user-email" -> "fixture@example.invalid",
      "environment.path" -> "/usr/bin:/bin", "environment.java-home" -> "", "environment.ssh-auth-sock" -> "")
    def expand(value: Json): Json = value.mapString(text => values.foldLeft(text) { case (prior, (key, value)) =>
      prior.replace("${" + key + "}", value) }).mapArray(_.map(expand)).mapObject(_.mapValues(expand))
    val removedcapabilities = template.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get
      .map(_.hcursor.get[Json]("capability").toOption.get).toSet
    val base = expand(template).mapObject(fields => fields.add("sbtPrograms", Json.arr()).add("grants",
      fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(_.filterNot(removedcapabilities))))))))
    val source = TypedGitHostPlanFixture.plan(Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString)).hcursor.get[Vector[Json]]("gitPrograms").toOption.get
    val extras = (1 to 3).toVector.flatMap(index => source.filter(value =>
      Set("head", "index-tree", "status", "check-pending-index", "pending-diff")(value.hcursor.get[String]("operation").toOption.get)).map(value =>
        value.mapObject(fields => fields.add("target", Json.fromString("purpose-slot-" + index))
          .add("identity", Json.fromString(fields("identity").get.asString.get + "-slot-" + index))
          .add("capability", Json.fromString(fields("capability").get.asString.get + "-slot-" + index)))))
    (1 to 3).foreach(index => Files.createDirectories(root.resolve("purpose-slot-" + index)))
    val scoped = base.mapObject(fields => fields
      .add("workspaces", fields("workspaces").get.mapArray(_.map(_.mapObject(w =>
        if (w("identity").flatMap(_.asString).contains("sm-workflow-repository"))
          w.add("identity", Json.fromString("batch-dirty")) else w))))
      .add("workspaceGrants", fields("workspaceGrants").get.mapArray(_.map(_.mapObject(g =>
        if (g("workspaceId").flatMap(_.asString).contains("sm-workflow-repository"))
          g.add("workspaceId", Json.fromString("batch-dirty")) else g))))
      .add("gitRepositories", fields("gitRepositories").get.mapArray(_.map(_.mapObject(g =>
        if (g("target").flatMap(_.asString).contains("sm-workflow-repository"))
          g.add("target", Json.fromString("batch-dirty")) else g)))))
    val planned = scoped.mapObject(fields => fields.add("gitPrograms", Json.fromValues(extras))
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(_ ++ extras.map(_.hcursor.get[Json]("capability").toOption.get)))))))
      .add("workspaces", fields("workspaces").get.mapArray(_ ++ (1 to 3).map(index => Json.obj(
        "identity" -> Json.fromString("purpose-slot-" + index), "root" -> Json.fromString(root.resolve("purpose-slot-" + index).toString)))))
      .add("workspaceGrants", fields("workspaceGrants").get.mapArray(_ ++ (1 to 3).map(index => Json.obj(
        "componentId" -> Json.fromString(SmWorkflowComponent.componentId.name), "workspaceId" -> Json.fromString("purpose-slot-" + index),
        "access" -> Json.fromString("manage"))))))
    if (scenario != "Questions") planned else {
      val other = planned.hcursor.get[Vector[Json]]("gitRepositories").toOption.get.head.mapObject(fields => fields
        .add("target", Json.fromString("batch-other")).add("identity", Json.fromString("batch-other-git"))
        .add("capability", Json.fromString("batch-other-git"))
        .add("endpoint", Json.fromString(root.resolve("second/upstream.git").toString)))
      planned.mapObject(fields => fields
        .add("gitRepositories", fields("gitRepositories").get.mapArray(_ :+ other))
        .add("workspaces", fields("workspaces").get.mapArray(_ :+ Json.obj("identity" -> Json.fromString("batch-other"),
          "root" -> Json.fromString(root.resolve("second/repository").toString))))
        .add("workspaceGrants", fields("workspaceGrants").get.mapArray(_ :+ Json.obj(
          "componentId" -> Json.fromString(SmWorkflowComponent.componentId.name),
          "workspaceId" -> Json.fromString("batch-other"), "access" -> Json.fromString("manage"))))
        .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
          grant("capabilities").get.mapArray(_ :+ Json.fromString("batch-other-git"))))))))
    }
  }
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit =
    TypedSbtHostPlanFixture.build(root, true)
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] =
    super._setup_commands_(root, "Equal").patch(2, Vector(Vector("-C", root.resolve("repository").toString,
      "add", "--", "shared.md", "staged.md", "unstaged.md")), 0)
  override protected def _setup_files_(root: Path, scenario: String, index: Int): Unit =
    if (index == 2) paths.take(3).foreach(path => Files.writeString(root.resolve("repository").resolve(path), original))

  private def parameters(root: Path): Map[String, String] = Map(
    RepositorySyncDirectoryList.declaration.name -> Vector(root.resolve("repository").toString).asJson.noSpaces,
    RepositorySyncDocumentInputs.declaration.name -> RepositorySyncDocumentInputs.Policy(paths,
      Vector("src", "build.sbt", "project", "value.txt"), "The selected documents are not consumed by the build").asJson.noSpaces,
    P.declaration.name -> Json.obj("sources" -> Json.arr(), "slots" -> Json.fromValues((1 to 3).map(index =>
      Json.obj("workspace" -> Json.fromString("purpose-slot-" + index), "checks" -> Json.arr(),
        "actionPolicy" -> Json.obj("identity" -> Json.fromString("document-purpose"), "kind" -> Json.fromString("documentation"),
          "detailedReview" -> Json.fromString("conditional"), "scalaLint" -> Json.fromString("off"),
          "carLint" -> Json.fromString("off"), "observations" -> Json.arr()))))).noSpaces)

  private def batchClue(value: V.RepositoryDirectoryBatchResult): String =
    ("Actual purpose batch: session=" + value.sessionId + " outcome=" + value.outcome +
      value.repositories.map(repository => "\nrepository=" + repository.repositoryIdentity +
        " workspace=" + repository.workspace + " session=" + repository.sessionId +
        " revision=" + repository.nativeRevision + " kind=" + repository.kind +
        " outcome=" + repository.outcome + " condition=" + repository.condition +
        " diagnostic=" + repository.diagnostic).mkString + "\n").take(8192)

  "Directory-list dirty purpose handling" should {
    "combine independent currently-known questions with their original child contexts" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/directory-list-purpose").toAbsolutePath), "questions-")
      Files.createDirectories(root.resolve("second/repository"))
      val configured = parameters(root) + (RepositorySyncDirectoryList.declaration.name ->
        Vector(root.resolve("repository").toString, root.resolve("second/repository").toString).asJson.noSpaces)
      Given("two selected repositories with independently unknown dirty meanings")
      _with_runtime_(root, "Questions", hostParameters = configured) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(root, "Equal").size))
        prepare(_ => _setup_commands_(root.resolve("second"), "Equal").zipWithIndex.foldLeft(Consequence.unit) {
          case (prior, (command, index)) => prior.flatMap { _ =>
            _setup_files_(root.resolve("second"), "Equal", index)
            _run_setup_command_(command)
          }
        })
        Files.writeString(root.resolve("repository/shared.md"), first)
        Files.writeString(root.resolve("second/repository/shared.md"), last)
        def batch(session: Option[String] = None): V.RepositoryDirectoryBatchResult =
          read_value(take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "RepositorySyncService", operation = RepositorySyncDirectoryList.syncOperation,
            properties = session.toList.map(Property("sessionId", _, None)) :+
              Property("input", Record.data("syncMode" -> "FULL_STATE"), None)), actor)))
        When("one ordinary invocation reaches the native per-repository semantic owners")
        val value = batch()
        Then("both actual requests are returned together without a caller-owned scheduling loop")
        value.outcome shouldBe "AttentionRequired"
        value.notStarted shouldBe empty
        value.repositories.map(_.workspace) shouldBe Vector("batch-dirty", "batch-other")
        value.repositories.map(_.kind).distinct shouldBe Vector("Request")
        value.repositories.map(_.sessionId).distinct.size shouldBe 2
        value.repositories.foreach { repository =>
          repository.work shouldBe empty
          val detail = read_control_value[V.WorkflowRetainedWorkReadResult](take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = WorkflowRetainedWork.serviceName,
              operation = WorkflowRetainedWork.readOperation, properties = List(Property("input", repository.retainedWork.get, None))), actor)))
          val work = take(V.RepositorySyncAdvanceResult.createC(detail.response))
          work.sessionId shouldBe repository.sessionId
          work.submitOperation shouldBe Some("submitRepositoryPurposeResult")
          work.issuedWorkContext.get.runId shouldBe repository.sessionId
        }
        batch(Some(value.sessionId)) shouldBe value
        Files.readString(root.resolve("repository/shared.md")) shouldBe first
        Files.readString(root.resolve("second/repository/shared.md")) shouldBe last
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      }
    }

    for (excluded <- Vector(false, true)) {
      s"resume only accepted outstanding shared-file work and preserve excluded staging=$excluded" in {
        val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/directory-list-purpose").toAbsolutePath), "shared-")
        val repository = root.resolve("repository")
        Given("an actual staged purpose A and independent unstaged purpose B in the selected batch workspace")
        _with_runtime_(root, "Equal", hostParameters = parameters(root)) { (owner, component, actor, prepare) =>
          prepare(call => call.setupC(_setup_commands_(root, "Equal").size))
          def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = operation, properties = properties), actor)))
          def batch(session: Option[String] = None): V.RepositoryDirectoryBatchResult =
            invoke(RepositorySyncDirectoryList.syncOperation, session.toList.map(Property("sessionId", _, None)) :+
              Property("input", Record.data("syncMode" -> "FULL_STATE"), None))
          def indexedState(): (GitObjectId, Vector[GitStatusEntry]) = {
            var observed = Option.empty[(GitObjectId, Vector[GitStatusEntry])]
            prepare(call => for {
              index <- WorkflowManagedProcessExecution.executeGitC(call.core, GitOperation.IndexTree, "batch-dirty").flatMap(_.valueC)
              status <- WorkflowManagedProcessExecution.executeGitC(call.core, GitOperation.Status, "batch-dirty").flatMap(_.valueC)
            } yield { observed = Some(index -> status); () })
            observed.get
          }
          def worktreeBytes(): Vector[Option[Vector[Byte]]] = paths.map { path =>
            val selected = repository.resolve(path)
            Option.when(Files.exists(selected))(Files.readAllBytes(selected).toVector)
          }
          Files.writeString(repository.resolve("shared.md"), first)
          take(_run_setup_command_(Vector("-C", repository.toString, "add", "--", "shared.md")))
          Files.writeString(repository.resolve("shared.md"), both)
          if (excluded) {
            Files.writeString(repository.resolve("staged.md"), "excluded staged purpose\n")
            take(_run_setup_command_(Vector("-C", repository.toString, "add", "--", "staged.md")))
            Files.writeString(repository.resolve("unstaged.md"), "excluded unstaged purpose\n")
            Files.writeString(repository.resolve("untracked.md"), "excluded untracked purpose\n")
          }
          // Git observation may refresh index stat/cache-tree metadata. Preserve
          // the exact staged tree and dirty partition, rather than cache bytes.
          val before = indexedState()
          val beforeWorktree = worktreeBytes()
          val beforeHead = Files.readString(repository.resolve(".git/refs/heads/main"))
          When("one batch invocation returns the actual scoped purpose request")
          val issued = batch()
          issued.outcome shouldBe "AttentionRequired"
          issued.repositories.size shouldBe 1
          val summary = issued.repositories.head
          summary.workspace shouldBe "batch-dirty"
          summary.kind shouldBe "Request"
          summary.work shouldBe empty
          val detail = read_control_value[V.WorkflowRetainedWorkReadResult](take(owner.executeOperationResponseInContext(
            Request.of(component = component.name, service = WorkflowRetainedWork.serviceName,
              operation = WorkflowRetainedWork.readOperation, properties = List(Property("input", summary.retainedWork.get, None))), actor)))
          val semantic = take(V.RepositorySyncAdvanceResult.createC(detail.response))
          semantic.sessionId shouldBe summary.sessionId
          semantic.submitOperation shouldBe Some("submitRepositoryPurposeResult")
          semantic.issuedWorkContext should not be empty
          batch(Some(issued.sessionId)) shouldBe issued
          indexedState() shouldBe before
          worktreeBytes() shouldBe beforeWorktree
          Files.readString(repository.resolve(".git/refs/heads/main")) shouldBe beforeHead
          var work = Option.empty[WorkflowInteraction[P.Request, Nothing]]
          prepare(_.repositoryWorkC(InstanceIdentity(summary.sessionId), RepositorySyncPurposeContinuation.completionOperation,
            P.inputCodec).map(value => { work = Some(value); () }))
          val request = work.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[P.Request]].request
          request.input.get.value.files.find(_.path == "shared.md").get.indexed shouldBe Some(first)
          request.input.get.value.files.find(_.path == "shared.md").get.worktree shouldBe Some(both)
          val groups = Vector(
            P.Group("purpose-b", "Independent B", "docs: purpose B", Vector.empty,
              Vector(P.Content("shared.md", Some(last))), Vector.empty, "B is independent of the staged A change"),
            P.Group("purpose-a", "A after B", "docs: purpose A", Vector("purpose-b"),
              Vector(P.Content("shared.md", Some(both))), Vector.empty, "Keep accepted B and commit only A"))
          val result = P.Result("resolved", "The actual two independent shared-file meanings", groups,
            if (excluded) Vector("staged.md", "unstaged.md", "untracked.md") else Vector.empty)
          val reference = ContextReference("batch-shared-purpose-author", "1")
          val reply = ContinuationResult(work.get.handle, request.runId, request.continuationId, request.expectedRevision,
            request.context.snapshot, TypedValue(request.resultType.get.value, result), reference, Vector.empty,
            ExecutionEvidence(Vector(reference), Some("batch-purpose-specification")))
          invoke[V.RepositorySyncConflictSubmissionResult]("submitRepositoryPurposeResult",
            take(NativeWorkflowOperationInputFixture.submissionC(reply))).accepted shouldBe true
          val completed = batch(Some(issued.sessionId))
          val head = Files.readString(repository.resolve(".git/refs/heads/main")).trim
          val revision = completed.repositories.head.nativeRevision
          Then("native ordered purpose commits precede synchronization and repeated collection does not recommit")
          withClue(batchClue(completed)) {
            if (excluded) {
              completed.outcome shouldBe "AttentionRequired"
              completed.repositories.head.kind shouldBe "Wait"
              Files.readString(repository.resolve("staged.md")) shouldBe "excluded staged purpose\n"
              Files.readString(repository.resolve("unstaged.md")) shouldBe "excluded unstaged purpose\n"
              Files.readString(repository.resolve("untracked.md")) shouldBe "excluded untracked purpose\n"
            } else {
              completed.outcome shouldBe "Completed"
              completed.repositories.head.kind shouldBe "Terminal"
              completed.repositories.head.outcome shouldBe "converged"
            }
          }
          var installed = Option.empty[RepositorySyncNativeProgressStore.Stage]
          prepare(_.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input,
            InstanceIdentity(summary.sessionId), root, "purpose-2-installed").map(value => { installed = value; () }))
          withClue(batchClue(completed) + "Expected native purpose-2-installed receipt\n") {
            installed should not be empty
          }
          val progress = installed.get.payload.purposes.get
          progress.cursor shouldBe 2
          progress.commits.size shouldBe 2
          progress.plan.get.groups.map(_.identity) shouldBe Vector("purpose-b", "purpose-a")
          progress.commits.last shouldBe head
          Files.readString(repository.resolve("shared.md")) shouldBe both
          batch(Some(issued.sessionId)) shouldBe completed
          Files.readString(repository.resolve(".git/refs/heads/main")).trim shouldBe head
          completed.repositories.head.nativeRevision shouldBe revision
          Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        }
      }
    }
  }
}
