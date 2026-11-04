package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.*
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
import SplitPhaseExplicitPartitionFixture.take
import RepositorySyncPurpose as P

/** Shared ordinary host configuration and real Git setup/observations only.
  * No Workflow record, process receipt or accepted result is seeded.
  * @since Nov. 3, 2026
  * @version Nov.  3, 2026
  */
private[admission] trait RepositorySyncMaintenanceFixture extends Phase69ConfiguredRepositoryFixture {
  protected val shared_path = "docs/shared.md"
  protected val purpose_path = "docs/purpose.md"
  protected val add_path = "docs/add-add.md"
  protected val add_local_text = "local addition\n" + ("界" * 30000) + "\n"
  protected val add_remote_text = "remote addition\n" + ("語" * 30000) + "\n"
  override protected def conflict_path(scenario: String): Option[String] =
    if (Set("Conflict", "ConflictDelete", "ConflictMode", "ConflictBinary")(scenario)) Some(shared_path) else None
  override protected def _setup_files_(root: Path, scenario: String, index: Int): Unit = {
    if (index == 2) Files.createDirectories(root.resolve("repository/docs"))
    super._setup_files_(root, scenario, index)
    if (index == 9) {
      val path = root.resolve("repository").resolve(shared_path)
      scenario match {
        case "ConflictDelete" => Files.delete(path)
        case "ConflictMode" =>
          Files.delete(path)
          Files.createSymbolicLink(path, Path.of("local-target"))
        case "ConflictBinary" => Files.write(path, Array[Byte](108, 111, 99, 97, 108, 0, 10))
        case _ => ()
      }
    }
  }
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val base = super.process_plan(root, scenario, goal)
    val slot = "purpose-slot-1"
    Files.createDirectories(root.resolve(slot))
    val programs = TypedGitHostPlanFixture.plan(Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString)).hcursor.get[Vector[Json]]("gitPrograms").toOption.get
      .filter(value => Set("head", "index-tree", "status", "check-pending-index", "pending-diff")(
        value.hcursor.get[String]("operation").toOption.get)).map(_.mapObject(fields => fields
        .add("target", Json.fromString(slot))
        .add("identity", Json.fromString(fields("identity").get.asString.get + "-maintenance"))
        .add("capability", Json.fromString(fields("capability").get.asString.get + "-maintenance"))))
    base.mapObject(fields => fields.add("gitPrograms", Json.fromValues(programs))
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(_ ++ programs.map(_.hcursor.get[Json]("capability").toOption.get)))))))
      .add("workspaces", fields("workspaces").get.mapArray(_ :+ Json.obj("identity" -> Json.fromString(slot),
        "root" -> Json.fromString(root.resolve(slot).toString))))
      .add("workspaceGrants", fields("workspaceGrants").get.mapArray(_ :+ Json.obj(
        "componentId" -> Json.fromString(SmWorkflowComponent.componentId.name), "workspaceId" -> Json.fromString(slot),
        "access" -> Json.fromString("manage")))))
      .mapObject(fields => fields.add("gitPrograms", fields("gitPrograms").get.mapArray(_.map(_.mapObject(program =>
        program.add("limits", program("limits").get.mapObject(_.remove("stdinBytes").remove("stdoutBytes")
          .add("artifactBytes", Json.fromLong(1048576L)))))))))
      .mapObject(fields => fields.add("gitRepositories", fields("gitRepositories").get.mapArray(_.map(_.mapObject(repository =>
        repository.add("limits", repository("limits").get.mapObject(_.remove("stdinBytes").remove("stdoutBytes"))))))))
  }
  protected def maintenance_parameters(review: Boolean = false): Map[String, String] = Map(
    "sm-workflow.repository-sync.document-inputs" -> Json.obj("documentPaths" -> Json.arr(Json.fromString("docs")),
      "consumedPaths" -> Json.arr(Json.fromString("src"), Json.fromString("build.sbt"), Json.fromString("project"), Json.fromString("value.txt")),
      "reason" -> Json.fromString("These controlled documents are independent of the declared product inputs")).noSpaces,
    "sm-workflow.repository-sync.dirty-purposes" -> Json.obj("sources" -> Json.arr(), "slots" -> Json.arr(Json.obj(
      "workspace" -> Json.fromString("purpose-slot-1"), "checks" -> Json.arr(), "actionPolicy" -> Json.obj(
        "identity" -> Json.fromString("maintenance-document"), "kind" -> Json.fromString("documentation"),
        "detailedReview" -> Json.fromString(if (review) "required" else "conditional"), "scalaLint" -> Json.fromString("off"),
        "carLint" -> Json.fromString("off"), "observations" -> Json.arr())))).noSpaces)
  protected def git(root: Path, arguments: String*): String = {
    val output = Files.createTempFile(root, "maintenance-git-", ".log")
    val builder = new ProcessBuilder((Vector("/usr/bin/git", "-c", "core.hooksPath=/dev/null",
      "-c", "credential.helper=", "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid") ++ arguments).asJava)
      .directory(root.toFile).redirectErrorStream(true).redirectOutput(output.toFile)
    builder.environment().put("GIT_CONFIG_NOSYSTEM", "1")
    builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null")
    builder.environment().put("GIT_TERMINAL_PROMPT", "0")
    val process = builder.start()
    try {
      process.getOutputStream.close()
      process.waitFor(30, TimeUnit.SECONDS) shouldBe true
      withClue(Files.readString(output)) { process.exitValue() shouldBe 0 }
      Files.readString(output).trim
    } finally {
      if (process.isAlive) { process.destroy(); if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly(); process.waitFor() }
    }
  }
  protected def grouping(text: String): P.Result = P.Result("resolved", "Specification-owned independent document purpose",
    Vector(P.Group("document-purpose", "Retain the independent document", "docs: retain independent purpose", Vector.empty,
      Vector(P.Content(purpose_path, Some(text))), Vector.empty, "This purpose does not resolve the shared conflict")), Vector.empty)
}

/** Public Operations against local repositories. Authored semantic responses and
  * independent reviewer evidence are specification data, never live acceptance.
  * @since Nov. 3, 2026
  * @version Nov.  3, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class RepositorySyncMaintenanceContinuationSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with RepositorySyncMaintenanceFixture {
  "RepositorySync maintenance continuation" should {
    for (scenario <- Vector("Equal", "Conflict", "ReviewedPurposeConflict", "MixedAddAdd",
        "ConflictDelete", "ConflictMode", "ConflictBinary")) {
      s"retain complete purpose text and continue the actual $scenario frontier in the same Session" in {
        Given("a real bare remote and local repository with one independent dirty document purpose")
        val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/repository-sync-maintenance").toAbsolutePath), "case-")
        val repository = root.resolve("repository")
        val reviewed = scenario == "ReviewedPurposeConflict"
        val mixed = scenario == "MixedAddAdd"
        val unsupported = Set("ConflictDelete", "ConflictMode", "ConflictBinary")(scenario)
        val runtimecase = if (reviewed || mixed) "Conflict" else scenario
        val text = if (scenario == "Equal" || mixed) "完全な本文を保持する\n" * 25000 else "independent purpose\n"
        _with_runtime_(root, runtimecase, hostParameters = maintenance_parameters(reviewed)) { (owner, component, actor, prepare) =>
          prepare(call => call.setupC(_setup_commands_(root, runtimecase).size))
          if (mixed) {
            val peer = root.resolve("peer")
            Files.writeString(repository.resolve(add_path), add_local_text)
            git(root, "-C", repository.toString, "add", "--", add_path)
            git(root, "-C", repository.toString, "commit", "-m", "local independent addition")
            Files.writeString(peer.resolve(add_path), add_remote_text)
            git(root, "-C", peer.toString, "add", "--", add_path)
            git(root, "-C", peer.toString, "commit", "-m", "remote independent addition")
            git(root, "-C", peer.toString, "push", root.resolve("upstream.git").toString, "HEAD:refs/heads/main")
          }
          def _invoke_[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = operation, properties = properties), actor)))
          val session = _invoke_[V.RepositorySyncStartResult]("startRepositorySync", List(Property("input", Record.data(
            "repositoryIdentity" -> "sm-workflow", "syncMode" -> "FULL_STATE"), None))).sessionId
          def _advance_() = _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(session))
          def _accept_review_(issued: V.RepositorySyncAdvanceResult, referenceidentity: String, rationale: String): Unit = {
            var judgment = Option.empty[WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]]
            prepare(_.repositoryWorkC(InstanceIdentity(session), RepositorySyncNativeMergeReviewContinuation.completionOperation,
              RepositorySyncNativeMergeReview.inputCodec).map(value => { judgment = Some(value); () }))
            val reviewrequest = judgment.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request
            if (mixed) {
              val actual = reviewrequest.input.get.value
              actual.localDiff.trim shouldBe git(root, "-C", repository.toString, "diff", "--cached", actual.localTip, "--")
              actual.remoteDiff.trim shouldBe git(root, "-C", repository.toString, "diff", "--cached", actual.remoteTip, "--")
              actual.localDiff should include("+" + ("語" * 30000))
              actual.remoteDiff should include("+" + ("界" * 30000))
              actual.localDiff.getBytes(java.nio.charset.StandardCharsets.UTF_8).length.toLong should be < 1048576L
              actual.remoteDiff.getBytes(java.nio.charset.StandardCharsets.UTF_8).length.toLong should be < 1048576L
              actual.uncertainties.exists(_.startsWith("Unexamined")) shouldBe false
            }
            val judged = ContextReference(referenceidentity, "1")
            val verdict = ContinuationResult(judgment.get.handle, reviewrequest.runId, reviewrequest.continuationId,
              reviewrequest.expectedRevision, reviewrequest.context.snapshot, TypedValue(reviewrequest.resultType.get.value,
                RepositorySyncNativeMergeReview.Result("accept", rationale, Vector.empty)),
              judged, Vector.empty, ExecutionEvidence(Vector(judged), Some("specification-independent-reviewer")))
            prepare(_.reviewEvidenceC(reviewrequest.input.get.value, verdict))
            _invoke_[V.RepositorySyncNativeMergeReviewSubmissionResult](issued.submitOperation.get,
              take(NativeWorkflowOperationInputFixture.submissionC(verdict))).accepted shouldBe true
          }
          Files.writeString(repository.resolve(purpose_path), text)
          When("normal Advance captures the complete worktree and issues its genuine purpose WorkOrder")
          val issued = _advance_()
          issued.submitOperation shouldBe Some("submitRepositoryPurposeResult")
          var purpose = Option.empty[WorkflowInteraction[P.Request, Nothing]]
          prepare(_.repositoryWorkC(InstanceIdentity(session), RepositorySyncPurposeContinuation.completionOperation, P.inputCodec)
            .map(value => { purpose = Some(value); () }))
          val request = purpose.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[P.Request]].request
          val input = request.input.get.value
          Then("capture and the result codec retain every byte of the ordinary document")
          input.files.find(_.path == purpose_path).get.worktree shouldBe Some(text)
          input.files.find(_.path == purpose_path).get.unexamined shouldBe None
          P.inputCodec.decode(P.inputCodec.encode(input)) shouldBe Right(input)
          P.resultCodec.decode(P.resultCodec.encode(grouping(text))) shouldBe Right(grouping(text))
          if (scenario == "Equal" || mixed) {
            val payloadbytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length.toLong
            payloadbytes shouldBe 775000L
            payloadbytes should be < 1048576L
            (payloadbytes * 2L) should be > 1048576L
          }
          val reference = ContextReference("spec-maintenance-purpose", "1")
          val reply = ContinuationResult(purpose.get.handle, request.runId, request.continuationId, request.expectedRevision,
            request.context.snapshot, TypedValue(request.resultType.get.value, grouping(text)), reference, Vector.empty,
            ExecutionEvidence(Vector(reference), Some("specification-purpose-author")))
          _invoke_[V.RepositorySyncConflictSubmissionResult]("submitRepositoryPurposeResult",
            take(NativeWorkflowOperationInputFixture.submissionC(reply))).accepted shouldBe true
          val first = _advance_()
          val purposereview = Option.when(reviewed)(first)
          if (reviewed) {
            When("the required purpose review is issued and independently completed before purpose installation")
            first.kind shouldBe "Request"
            first.review should not be empty
            first.review.get.changedPaths shouldBe Vector(purpose_path)
            first.review.get.selectedChecks shouldBe empty
            _advance_().copy(journalRecording = None) shouldBe first.copy(journalRecording = None)
            _accept_review_(first, "spec-maintenance-independent-purpose-review",
              "Specification-owned independent review of the actual document purpose before installation")
          }
          val progressed = if (reviewed) _advance_() else first
          if (scenario == "Equal") {
            progressed.kind shouldBe "Terminal"
            _advance_() shouldBe progressed
          } else {
            Then("the purpose commit and real unresolved merge remain retained before a separate Advance")
            progressed.kind shouldBe "Wait"
            val head = git(root, "-C", repository.toString, "rev-parse", "HEAD")
            val merge = git(root, "-C", repository.toString, "rev-parse", "MERGE_HEAD")
            val index = git(root, "-C", repository.toString, "ls-files", "--stage")
            index should include(shared_path)
            if (unsupported) {
              scenario match {
                case "ConflictDelete" =>
                  index.linesIterator.filter(_.split("\t", 2).last == shared_path)
                    .map(_.takeWhile(_ != '\t').split(" ").last.toInt).toVector shouldBe Vector(1, 3)
                case "ConflictMode" => index should include("120000 ")
                case "ConflictBinary" =>
                  Files.readAllBytes(repository.resolve(shared_path)).contains(0.toByte) shouldBe true
                case _ => ()
              }
            }
            git(root, "-C", repository.toString, "log", "-1", "--format=%s") shouldBe "docs: retain independent purpose"
            var history = Vector.empty[HistoryEntry]
            prepare(_.currentC(InstanceIdentity(session)).map { current =>
              current.currentProgression shouldBe Some(ProgressionReference("sync.MergeConflicted"))
              history = current.history
              current.history.count(_.currentProgression.contains(ProgressionReference("sync.PurposeInstalled"))) shouldBe 1
              ()
            })
            When("a separate public Advance selects actual current conflicts instead of a completed purpose")
            if (unsupported) {
              def _worktree_(): Either[Path, Option[Vector[Byte]]] = {
                val path = repository.resolve(shared_path)
                if (Files.isSymbolicLink(path)) Left(Files.readSymbolicLink(path))
                else Right(Option.when(Files.isRegularFile(path))(Files.readAllBytes(path).toVector))
              }
              val worktree = _worktree_()
              val refused = owner.executeOperationResponseInContext(Request.of(component = component.name,
                service = "RepositorySyncService", operation = "advanceRepositorySync",
                properties = NativeWorkflowOperationInputFixture.advance(session)), actor)
              Then("unsupported native content is refused before any conflict work or repository materialization")
              refused.isSuccess shouldBe false
              git(root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe head
              git(root, "-C", repository.toString, "rev-parse", "MERGE_HEAD") shouldBe merge
              git(root, "-C", repository.toString, "ls-files", "--stage") shouldBe index
              _worktree_() shouldBe worktree
              prepare(_.currentC(InstanceIdentity(session)).map { current =>
                current.history shouldBe history
                current.suspension shouldBe None
                ()
              })
            } else {
            val conflict = _advance_()
            conflict.sessionId shouldBe session
            conflict.conflict.get.conflicts.map(_.path) shouldBe (if (mixed) Vector(add_path, shared_path) else Vector(shared_path))
            conflict.conflict.get.conflicts.map(_.path) should not contain purpose_path
            purposereview.foreach(value => conflict.issuedWorkContext should not be value.issuedWorkContext)
            _advance_() shouldBe conflict
            git(root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe head
            git(root, "-C", repository.toString, "rev-parse", "MERGE_HEAD") shouldBe merge
            git(root, "-C", repository.toString, "ls-files", "--stage") shouldBe index
            prepare(_.currentC(InstanceIdentity(session)).map { current =>
              current.history.take(history.size) shouldBe history
              current.history.count(_.currentProgression.contains(ProgressionReference("sync.PurposeInstalled"))) shouldBe 1
              ()
            })
            var work = Option.empty[WorkflowInteraction[RepositorySyncNativeConflictInput.Request, Nothing]]
            prepare(_.repositoryWorkC(InstanceIdentity(session), RepositorySyncNativeConflictContinuation.completionOperation,
              RepositorySyncConflictSemantic.inputCodec).map(value => { work = Some(value); () }))
            val assigned = work.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeConflictInput.Request]].request
            val resolution = "local shared line\nremote shared line\n"
            val addedresolution = add_local_text + add_remote_text
            if (mixed) {
              Then("actual stage rows and the v2 request distinguish absent and present bases with all ordinary bytes intact")
              def _stages_(path: String): Vector[Int] = index.linesIterator.filter(_.split("\t", 2).last == path)
                .map(_.takeWhile(_ != '\t').split(" ").last.toInt).toVector
              _stages_(shared_path) shouldBe Vector(1, 2, 3)
              _stages_(add_path) shouldBe Vector(2, 3)
              val input = assigned.input.get.value
              val added = input.conflicts.find(_.path == add_path).get
              val shared = input.conflicts.find(_.path == shared_path).get
              shared.base.get.text shouldBe "original shared line\n"
              shared.local.text shouldBe "local shared line\n"
              shared.remote.text shouldBe "remote shared line\n"
              added.base shouldBe None
              added.local.text shouldBe add_local_text
              added.remote.text shouldBe add_remote_text
              input.conflicts.foreach(c => c.worktree shouldBe Files.readString(repository.resolve(c.path)))
              conflict.conflict.get.conflicts.find(_.path == add_path).get.base shouldBe None
              conflict.conflict.get.conflicts.find(_.path == shared_path).get.base.get.text shouldBe "original shared line\n"
              added.local.text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 90000
              added.remote.text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 90000
              addedresolution.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 131072
              val wire = RepositorySyncConflictSemantic.inputCodec.encode(input)
              wire.hcursor.get[String]("schemaVersion").toOption.get shouldBe "sm-workflow.repository-sync-conflict-request.v2"
              RepositorySyncConflictSemantic.inputCodec.decode(wire) shouldBe Right(input)
            }
            val result = RepositorySyncConflictSemantic.Result("resolved", assigned.input.get.value.conflicts.map(c =>
              RepositorySyncConflictSemantic.Resolution(c.identity, if (c.path == add_path) addedresolution else resolution)),
              "Authored specification resolution retaining both meanings")
            val resolved = ContextReference("spec-maintenance-resolution", "1")
            val response = ContinuationResult(work.get.handle, assigned.runId, assigned.continuationId, assigned.expectedRevision,
              assigned.context.snapshot, TypedValue(assigned.resultType.get.value, result), resolved, Vector.empty,
              ExecutionEvidence(Vector(resolved), Some("specification-conflict-author")))
            _invoke_[V.RepositorySyncConflictSubmissionResult](conflict.submitOperation.get,
              take(NativeWorkflowOperationInputFixture.submissionC(response))).accepted shouldBe true
            When("a later Advance consumes that genuine resolution and independently reviews the actual merge")
            val review = _advance_()
            review.review should not be empty
            review.review.get.changedPaths should contain(shared_path)
            if (mixed) review.review.get.changedPaths should contain(add_path)
            purposereview.foreach(value => review.issuedWorkContext should not be value.issuedWorkContext)
            _advance_().copy(journalRecording = None) shouldBe review.copy(journalRecording = None)
            _accept_review_(review, "spec-maintenance-independent-review",
              "Specification-owned independent review of both actual parent diffs")
            val terminal = _advance_()
            Then("native merge commit push and final fetch converge once and completed collection is passive")
            terminal.kind shouldBe "Terminal"
            val finalhead = git(root, "-C", repository.toString, "rev-parse", "HEAD")
            git(root, "-C", repository.toString, "show", "-s", "--format=%P", "HEAD").split(" ").toVector shouldBe Vector(head, merge)
            git(root, "--git-dir=" + root.resolve("upstream.git"), "rev-parse", "refs/heads/main") shouldBe finalhead
            git(root, "-C", repository.toString, "status", "--porcelain") shouldBe ""
            Files.exists(repository.resolve(".git/MERGE_HEAD")) shouldBe false
            val finalindex = git(root, "-C", repository.toString, "ls-files", "--stage")
            var completed = Option.empty[InstanceRecord]
            prepare(_.currentC(InstanceIdentity(session)).map(value => { completed = Some(value); () }))
            _advance_() shouldBe terminal
            git(root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe finalhead
            git(root, "--git-dir=" + root.resolve("upstream.git"), "rev-parse", "refs/heads/main") shouldBe finalhead
            git(root, "-C", repository.toString, "ls-files", "--stage") shouldBe finalindex
            Files.exists(repository.resolve(".git/MERGE_HEAD")) shouldBe false
            prepare(_.currentC(InstanceIdentity(session)).map { value =>
              take(WorkflowInstanceRecordJsonV1.encodeC(value)) shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(completed.get))
              ()
            })
            Files.readString(repository.resolve(shared_path)) shouldBe resolution
            if (mixed) Files.readString(repository.resolve(add_path)) shouldBe addedresolution
            }
          }
          Files.readString(repository.resolve(purpose_path)) shouldBe text
          Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        }
      }
    }
  }
}
