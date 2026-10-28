package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take
import RepositorySyncPurpose as P

/** Real repositories and public standard Operations consume genuine issued
  * purpose contexts. Authored fixtures are distinct from installed evidence.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class Phase69DirtyPurposeSpec extends AnyWordSpec with Matchers with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
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
    val planned = base.mapObject(fields => fields.add("gitPrograms", Json.fromValues(extras))
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(_ ++ extras.map(_.hcursor.get[Json]("capability").toOption.get)))))))
      .add("workspaces", fields("workspaces").get.mapArray(_ ++ (1 to 3).map(index => Json.obj(
        "identity" -> Json.fromString("purpose-slot-" + index), "root" -> Json.fromString(root.resolve("purpose-slot-" + index).toString)))))
      .add("workspaceGrants", fields("workspaceGrants").get.mapArray(_ ++ (1 to 3).map(index => Json.obj(
        "componentId" -> Json.fromString(SmWorkflowComponent.componentId.name), "workspaceId" -> Json.fromString("purpose-slot-" + index),
        "access" -> Json.fromString("manage"))))))
    if (!Set("check-failed", "check-unknown")(scenario)) planned else {
      val sbt = TypedSbtHostPlanFixture.plan(root, TypedSbtHostPlanFixture.normalExecutable, Vector(1),
        executionTimeoutMillis = if (scenario == "check-unknown") 1L else 180000L)
        .hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.map(_.mapObject(
          _.add("target", Json.fromString("purpose-slot-1"))))
      planned.mapObject(_.add("sbtPrograms", Json.fromValues(sbt))).mapObject(fields => fields.add("grants",
        fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
          grant("capabilities").get.mapArray(_ ++ sbt.map(_.hcursor.get[Json]("capability").toOption.get))))))))
    }
  }
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    TypedSbtHostPlanFixture.build(root, true)
    if (Set("check-failed", "check-unknown")(scenario)) {
      val source = root.resolve("repository/src/test/scala/ProductCheck.scala")
      val passed = "println(\"PRODUCT_CHECK_PASSED\")"
      Files.writeString(source, Files.readString(source).replace(passed,
        "assert(java.nio.file.Files.readString(root.resolve(\"shared.md\")) == " +
          Json.fromString(original).noSpaces + ", \"actual selected purpose input differs\")\n    " + passed))
    }
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] =
    super._setup_commands_(root, "Equal").patch(2, Vector(Vector("-C", root.resolve("repository").toString,
      "add", "--", "shared.md", "staged.md", "unstaged.md")), 0)
  override protected def _setup_files_(root: Path, scenario: String, index: Int): Unit =
    if (index == 2) paths.take(3).foreach(path => Files.writeString(root.resolve("repository").resolve(path), original))
  private def parameters(review: Boolean = false, checked: Boolean = false): Map[String, String] = {
    val check = RepositorySyncNativeMergeValidation.Check("purpose-product", 1, WorkflowSbtValidation.Kind.Test)
    val impact = RepositorySyncProgramImpact.Policy(Vector(RepositorySyncProgramImpact.Coverage(check,
      Vector("shared.md"), Vector("shared.md", "value.txt", "build.sbt", "project", "src"),
      "The selected product consumes the candidate and its committed build/value inputs")), Vector(check),
      Vector("build.sbt", "project", "src"), "Build inputs affect all product behavior", Vector.empty)
    val configured = Map(
    "sm-workflow.repository-sync.document-inputs" -> Json.obj("documentPaths" -> Json.fromValues(paths.filterNot(path => checked && path == "shared.md").map(Json.fromString)),
      "consumedPaths" -> Json.fromValues((Vector("src", "build.sbt", "project", "value.txt") ++
        (if (checked) Vector("shared.md") else Vector.empty)).map(Json.fromString)),
      "reason" -> Json.fromString("Controlled product consumes Scala/build inputs; these documents are not build inputs")).noSpaces,
    "sm-workflow.repository-sync.dirty-purposes" -> Json.obj("sources" -> Json.arr(), "slots" -> Json.fromValues((1 to 3).map(index =>
      Json.obj("workspace" -> Json.fromString("purpose-slot-" + index), "checks" -> (if (checked) Json.arr(Json.obj(
        "identity" -> Json.fromString(check.identity), "ordinal" -> Json.fromInt(check.ordinal))) else Json.arr()), "actionPolicy" -> Json.obj(
        "identity" -> Json.fromString("purpose-document-acceptance"), "kind" -> Json.fromString(if (checked) "code" else "documentation"),
        "detailedReview" -> Json.fromString(if (review) "required" else "conditional"), "scalaLint" -> Json.fromString("off"), "carLint" -> Json.fromString("off"),
        "observations" -> Json.arr()))))).noSpaces)
    if (checked) configured + (RepositorySyncProgramImpact.declaration.name -> impact.asJson.noSpaces) else configured
  }

  "Phase 6.9 dirty purpose public Operations" should {
    for (scenario <- Vector("independent-shared", "excluded-partial", "excluded-shared", "inseparable", "review-required", "invalid-dependency", "check-failed", "check-unknown")) {
      s"consume actual issued grouping for $scenario and preserve native content/staging facts" in {
        Given("a controlled equal repository with staged A and independently unstaged B in one shared file")
        val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase69-dirty-purpose").toAbsolutePath), "case-")
        val repository = root.resolve("repository")
        val checked = Set("check-failed", "check-unknown")(scenario)
        val runtimecase = if (checked) scenario else "Equal"
        _with_runtime_(root, runtimecase, hostParameters = parameters(scenario == "review-required", checked)) { (owner, component, actor, prepare) =>
          prepare(call => call.setupC(_setup_commands_(root, "Equal").size))
          def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
            read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "RepositorySyncService", operation = operation, properties = properties), actor)))
          val session = invoke[V.RepositorySyncStartResult]("startRepositorySync", List(Property("input", Record.data(
            "repositoryIdentity" -> "sm-workflow", "syncMode" -> "FULL_STATE"), None))).sessionId
          def write(path: String, content: String): Unit = { Files.writeString(repository.resolve(path), content); () }
          def stage(path: String): Unit = take(_run_setup_command_(Vector("-C", repository.toString, "add", "--", path)))
          write("shared.md", first); stage("shared.md"); write("shared.md", both)
          val excluded = if (scenario == "excluded-partial") Vector("staged.md", "unstaged.md", "untracked.md")
            else if (scenario == "excluded-shared") Vector("shared.md", "staged.md", "unstaged.md", "untracked.md") else Vector.empty
          if (excluded.nonEmpty) { write("staged.md", "excluded staged purpose\n"); stage("staged.md")
            write("unstaged.md", "excluded ongoing purpose\n"); write("untracked.md", "excluded untracked purpose\n") }
          val issued = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(session))
          issued.kind shouldBe "Request"
          issued.submitOperation shouldBe Some("submitRepositoryPurposeResult")
          issued.issuedWorkContext should not be empty
          var work = Option.empty[WorkflowInteraction[P.Request, Nothing]]
          prepare(_.repositoryWorkC(InstanceIdentity(session), RepositorySyncPurposeContinuation.completionOperation, P.inputCodec)
            .map(value => { work = Some(value); () }))
          val request = work.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[P.Request]].request
          val input = request.input.get.value
          input.files.find(_.path == "shared.md").get.indexed shouldBe Some(first)
          input.files.find(_.path == "shared.md").get.worktree shouldBe Some(both)
          val repeated = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(session))
          repeated.issuedWorkContext shouldBe issued.issuedWorkContext
          val groups = if (scenario == "excluded-shared") Vector(P.Group("purpose-b", "Only originally unstaged B", "docs: purpose B", Vector.empty,
            Vector(P.Content("shared.md", Some(last))), Vector.empty, "Retain staged A in this shared file as explicitly excluded ongoing work"))
            else if (Set("inseparable", "review-required", "check-failed", "check-unknown")(scenario)) Vector(P.Group("coalesced", "Coherent shared meaning", "docs: coherent shared purpose",
            Vector.empty, Vector(P.Content("shared.md", Some(both))), Vector("meaning-a", "meaning-b"), "The two edits express one inseparable meaning"))
            else Vector(P.Group("purpose-b", "Independent originally unstaged B", "docs: purpose B", Vector.empty,
              Vector(P.Content("shared.md", Some(last))), Vector.empty, "B is independent of the staged first-line edit"),
              P.Group("purpose-a", "Staged A after B", "docs: purpose A", Vector(if (scenario == "invalid-dependency") "missing" else "purpose-b"),
                Vector(P.Content("shared.md", Some(both))), Vector.empty, "Retain the already accepted B and commit only remaining A"))
          val result = P.Result("resolved", "Controlled actual dirty meaning and dependency partition", groups, excluded)
          val reference = ContextReference("spec-purpose-" + scenario, "1")
          val reply = ContinuationResult(work.get.handle, request.runId, request.continuationId, request.expectedRevision,
            request.context.snapshot, TypedValue(request.resultType.get.value, result), reference, Vector.empty,
            ExecutionEvidence(Vector(reference), Some("specification-purpose-author")))
          When("the genuine grouping result is submitted through the public paired completion")
          val completion = owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "RepositorySyncService", operation = "submitRepositoryPurposeResult",
            properties = take(NativeWorkflowOperationInputFixture.submissionC(reply))), actor)
          if (scenario == "invalid-dependency") {
            completion.isSuccess shouldBe false
            Files.readString(repository.resolve("shared.md")) shouldBe both
            Files.exists(root.resolve("purpose-slot-1/.git")) shouldBe false
          } else {
            read_value[V.RepositorySyncConflictSubmissionResult](take(completion)).accepted shouldBe true
            var output = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(session))
            if (checked) {
              Then("the actual selected check remains unfinished and never supplies review acceptance or a commit")
              output.kind shouldBe "Wait"
              output.review shouldBe None
              var failed = Option.empty[RepositorySyncNativeProgressStore.Stage]
              prepare(call => call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input,
                InstanceIdentity(session), root, "purpose-1-check-0-result").map(value => { failed = value; () }))
              val receipt = failed.get.payload.purposes.get.receipts.last
              val candidate = failed.get.payload.purposes.get.candidate.get
              val marker = root.resolve("sbt-executions.txt")
              val executions = if (Files.exists(marker)) Files.readString(marker) else ""
              output = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(session))
              if (scenario == "check-failed") {
                receipt.termination shouldBe "exited"
                receipt.exitCode.exists(_ != 0) shouldBe true
                failed.get.payload.stage shouldBe "PurposeRepairRequired"
                output.kind shouldBe "Request"
                output.repair should not be empty
                output.review shouldBe None
                var repair = Option.empty[WorkflowInteraction[RepositorySyncNativeMergeRepair.Request, Nothing]]
                prepare(_.repositoryWorkC(InstanceIdentity(session), RepositorySyncNativeMergeRepairContinuation.completionOperation,
                  RepositorySyncNativeMergeRepair.inputCodec).map(value => { repair = Some(value); () }))
                val assigned = repair.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeRepair.Request]].request
                val basis = assigned.input.get.value
                basis.validationFailure shouldBe Some(receipt.identity)
                basis.findings shouldBe empty
                basis.review.indexTree shouldBe candidate.tree
                basis.files.map(_.path) shouldBe Vector("shared.md")
                basis.files.head.text shouldBe both
                invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(session))
                  .issuedWorkContext shouldBe output.issuedWorkContext
              } else {
                receipt.termination should not be "exited"
                failed.get.payload.stage shouldBe "PurposeMaterialized"
                output.kind shouldBe "Wait"
                output.repair shouldBe None
                output.review shouldBe None
              }
              (if (Files.exists(marker)) Files.readString(marker) else "") shouldBe executions
              prepare(_.observeC().map { observed =>
                observed.head shouldBe candidate.parent
                ()
              })
            } else {
            if (scenario == "review-required") {
              output.kind shouldBe "Request"
              output.review should not be empty
              var judgment = Option.empty[WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]]
              prepare(_.repositoryWorkC(InstanceIdentity(session), RepositorySyncNativeMergeReviewContinuation.completionOperation,
                RepositorySyncNativeMergeReview.inputCodec).map(value => { judgment = Some(value); () }))
              val assigned = judgment.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncNativeMergeReview.Request]].request
              val reference = ContextReference("spec-purpose-independent-review", "1")
              val reply = ContinuationResult(judgment.get.handle, assigned.runId, assigned.continuationId, assigned.expectedRevision,
                assigned.context.snapshot, TypedValue(assigned.resultType.get.value, RepositorySyncNativeMergeReview.Result(
                  "accept", "Specification-owned independent examination of the complete actual candidate diff", Vector.empty)),
                reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-independent-merge-judge")))
              val properties = take(NativeWorkflowOperationInputFixture.submissionC(reply))
              val refused = owner.executeOperationResponseInContext(Request.of(component = component.name,
                service = "RepositorySyncService", operation = "submitNativeMergeReview", properties = properties), actor)
              refused.isSuccess shouldBe false
              prepare(_.reviewEvidenceC(assigned.input.get.value, reply))
              invoke[V.RepositorySyncNativeMergeReviewSubmissionResult]("submitNativeMergeReview", properties).accepted shouldBe true
              output = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(session))
            }
            Then("each actual candidate passed its own document structural admission before ordered installation")
            prepare(call => for {
              observed <- call.observeC()
              inventory <- call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, InstanceIdentity(session), root, "purpose-inventory")
              installed <- call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, InstanceIdentity(session), root,
                if (groups.size == 1) "purpose-1-installed" else "purpose-2-installed")
              firstCommit <- call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(root).repository.input, InstanceIdentity(session), root, "purpose-1-installed")
            } yield {
              inventory.get.payload.purposes.get.request shouldBe input
              installed.get.payload.purposes.get.commits.size shouldBe groups.size
              if (groups.size == 2) firstCommit.get.payload.purposes.get.commits.size shouldBe 1
              if (excluded.nonEmpty) {
                observed.dirty.map(_.path).toSet shouldBe excluded.toSet
                if (scenario == "excluded-shared") {
                  observed.dirty.find(_.path == "shared.md").get.index shouldBe 'M'
                  observed.dirty.find(_.path == "shared.md").get.worktree shouldBe ' '
                }
              } else observed.dirty shouldBe empty
              ()
            })
            if (excluded.nonEmpty) {
              output.kind shouldBe "Wait"
              Files.readString(repository.resolve("staged.md")) shouldBe "excluded staged purpose\n"
              Files.readString(repository.resolve("unstaged.md")) shouldBe "excluded ongoing purpose\n"
              Files.readString(repository.resolve("untracked.md")) shouldBe "excluded untracked purpose\n"
            } else {
              output.kind shouldBe "Terminal"
              invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", NativeWorkflowOperationInputFixture.advance(session)) shouldBe output
            }
            }
            Files.readString(repository.resolve("shared.md")) shouldBe both
          }
          if (!checked) Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        }
      }
    }
  }
}
