package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path}
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import SplitPhaseExplicitPartitionFixture.take

/** Real controlled repositories and public standard Operations. Configured
  * document consumption is specification data; no review, consent or successful
  * check is fabricated. Product test bindings/grants are deliberately absent.
  * @since   Oct. 28, 2026
  * @version Oct. 27, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class Phase69MergeAssessmentSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  override protected def standard_runtime = true
  private val shared = "docs/shared.md"
  private def original(scenario: String): String = if (scenario == "Large")
    (1 to 15000).map(n => s"original documentation line $n\n").mkString else "original document\n"
  private def additions(who: String): String = (1 to 4000).map(n => s"$who additional documentation line $n 日本語📝\n").mkString

  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] = {
    val repository = root.resolve("repository")
    val peer = root.resolve("peer")
    val remote = root.resolve("upstream.git")
    def commit(directory: Path, message: String): Vector[String] = Vector("-C", directory.toString,
      "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "commit", "-m", message)
    val remotePath = if (Set("Large", "Conflict")(scenario)) shared else if (scenario == "Binary") "docs/remote.bin" else "docs/remote.md"
    val localPath = if (Set("Large", "Conflict")(scenario)) shared else "docs/local.md"
    Vector(Vector("init", "--bare", "--initial-branch=main", remote.toString),
      Vector("init", "--initial-branch=main", repository.toString),
      Vector("-C", repository.toString, "add", "--", shared), commit(repository, "initial program and documentation"),
      Vector("-C", repository.toString, "push", remote.toString, "HEAD:refs/heads/main"),
      Vector("clone", "--no-local", remote.toString, peer.toString),
      Vector("-C", peer.toString, "add", "--", remotePath), commit(peer, "remote documentation purpose"),
      Vector("-C", peer.toString, "push", remote.toString, "HEAD:refs/heads/main"),
      Vector("-C", repository.toString, "add", "--", localPath), commit(repository, "local documentation purpose"))
  }
  override protected def _setup_files_(root: Path, scenario: String, index: Int): Unit = {
    if (index == 2) {
      Files.createDirectories(root.resolve("repository/docs"))
      Files.writeString(root.resolve("repository").resolve(shared), original(scenario))
    }
    if (index == 6) {
      if (scenario == "Large") Files.writeString(root.resolve("peer").resolve(shared), original(scenario) + additions("remote"))
      else if (scenario == "Conflict") Files.writeString(root.resolve("peer").resolve(shared), "remote incompatible meaning\n")
      else if (scenario == "Binary") Files.write(root.resolve("peer/docs/remote.bin"), Array[Byte](1, 0, 2, 3))
      else Files.writeString(root.resolve("peer/docs/remote.md"), "remote documentation\n")
    }
    if (index == 9) {
      if (scenario == "Large") Files.writeString(root.resolve("repository").resolve(shared), additions("local") + original(scenario))
      else if (scenario == "Conflict") Files.writeString(root.resolve("repository").resolve(shared), "local incompatible meaning\n")
      else Files.writeString(root.resolve("repository/docs/local.md"), "local documentation\n")
    }
  }

  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    Files.createDirectories(root.resolve("evidence"))
    val descriptor = take(GenericSubsystemDescriptor.loadComponentArchive(Path.of("src/main/car").toAbsolutePath))
    val template = io.circe.parser.parse(descriptor.config("textus.subsystem.process-execution.plan")).toOption.get
    val values = Map("repository.root" -> root.resolve("repository").toString, "git.branch" -> "main", "git.remote" -> "origin",
      "git.remote-branch" -> "main", "git.endpoint" -> root.resolve("upstream.git").toString,
      "git.user-name" -> "Fixture", "git.user-email" -> "fixture@example.invalid",
      "environment.path" -> "/usr/bin:/bin", "environment.java-home" -> "", "environment.ssh-auth-sock" -> "")
    def expand(value: Json): Json = value.mapString(text => values.foldLeft(text) { case (prior, (key, value)) =>
      prior.replace("${" + key + "}", value) }).mapArray(_.map(expand)).mapObject(_.mapValues(expand))
    val removed = template.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get.map(_.hcursor.get[Json]("capability").toOption.get).toSet
    expand(template).mapObject(fields => fields.add("sbtPrograms", Json.arr()).add("grants",
      fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(_.filterNot(removed)))))))
      .add("workspaces", fields("workspaces").get.mapArray(_ :+ Json.obj("identity" -> Json.fromString("merge-evidence"),
        "root" -> Json.fromString(root.resolve("evidence").toString))))
      .add("workspaceGrants", fields("workspaceGrants").get.mapArray(_ :+ Json.obj(
        "componentId" -> Json.fromString(SmWorkflowComponent.componentId.name), "workspaceId" -> Json.fromString("merge-evidence"),
        "access" -> Json.fromString("manage")))))
  }

  private def parameters(root: Path, consumed: Boolean = false): Map[String, String] = {
    val storage = ThreeWorkflowNormalHostFixture.storagePlan.mapObject(fields => fields.add("records",
      fields("records").get.mapArray(_ :+ Json.obj("componentId" -> Json.fromString(SmWorkflowComponent.componentId.name),
        "storeName" -> Json.fromString("workflow"), "collection" -> Json.fromString(_collection_("evidence").print)))))
    Map("textus.subsystem.storage.plan" -> storage.noSpaces,
      "textus.resource.url.file.roots" -> root.toString,
      "sm-workflow.repository-sync.document-inputs" -> Json.obj(
        "documentPaths" -> Json.arr(Json.fromString("docs")),
        "consumedPaths" -> Json.fromValues((Vector("src", "build.sbt", "project", "scripts") ++
          (if (consumed) Vector("docs") else Vector.empty)).map(Json.fromString)),
        "reason" -> Json.fromString("This controlled build consumes only its declared Scala/build inputs; docs are text documents unless explicitly excluded")).noSpaces,
      "sm-workflow.evidence" -> Json.obj("schemaVersion" -> Json.fromString(CommonEvidence.schemaVersion),
        "storeName" -> Json.fromString("workflow"), "collection" -> Json.fromString(_collection_("evidence").print),
        "hostIdentity" -> Json.fromString("merge-spec-host"), "workspaceIdentity" -> Json.fromString("merge-evidence"),
        "directory" -> Json.fromString("logs")).noSpaces)
  }
  private def root(): Path = Files.createTempDirectory(Files.createDirectories(Path.of("target/phase69-merge-assessment").toAbsolutePath), "case-")
  private def startInput: List[Property] = List(Property("input", Record.data("repositoryIdentity" -> "sm-workflow", "syncMode" -> "FULL_STATE"), None))

  "Phase 6.9 merge assessment through normal Operations" should {
    "admit a document-only divergent merge with unchanged consumed inputs and no product test or AI request" in {
      val directory = root()
      var session = ""
      _with_runtime_(directory, "Documents", hostParameters = parameters(directory)) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(directory, "Documents").size, scenario = "Documents"))
        val source = Files.readString(directory.resolve("repository/src/test/scala/ProductCheck.scala"))
        def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "RepositorySyncService", operation = operation, properties = properties), actor)))
        session = invoke[V.RepositorySyncStartResult]("startRepositorySync", startInput).sessionId
        val result = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", List(Property("sessionId", session, None)))
        result.kind shouldBe "Terminal"
        result.issuedWorkContext shouldBe None
        Files.readString(directory.resolve("repository/docs/local.md")) shouldBe "local documentation\n"
        Files.readString(directory.resolve("repository/docs/remote.md")) shouldBe "remote documentation\n"
        Files.readString(directory.resolve("repository/src/test/scala/ProductCheck.scala")) shouldBe source
        prepare(call => for {
          validation <- call.validationC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
            InstanceIdentity(session), directory, "merge-validated")
          committed <- call.nativeStageC(ThreeWorkflowNormalHostFixture.definition(directory).repository.input,
            InstanceIdentity(session), directory, "commit-observed")
          observed <- call.observeC()
        } yield {
          validation.selected shouldBe empty
          validation.receipts.map(_.identity) shouldBe Vector("index-local", "index-remote")
          validation.inputs.get.changedPaths shouldBe Vector("docs/local.md", "docs/remote.md")
          validation.inputs.get.documentOnly shouldBe true
          committed.get.payload.assessment.get.branch shouldBe "admit"
          observed.head shouldBe result.convergence.get.tip
          observed.trackingTip shouldBe Some(observed.head)
          observed.dirty shouldBe empty
          ()
        })
        Files.exists(directory.resolve("sbt-executions.txt")) shouldBe false
      }
      _with_runtime_(directory, "Documents", hostParameters = parameters(directory)) { (owner, component, actor, _) =>
        val collected = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
          properties = List(Property("sessionId", session, None))), actor)))
        collected.kind shouldBe "Terminal"
        collected.sessionId shouldBe session
      }
    }

    "issue only actual conflict work without running a blanket product check" in {
      val directory = root()
      _with_runtime_(directory, "Conflict", hostParameters = parameters(directory)) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(directory, "Conflict").size, scenario = "Conflict"))
        def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "RepositorySyncService", operation = operation, properties = properties), actor)))
        val session = invoke[V.RepositorySyncStartResult]("startRepositorySync", startInput).sessionId
        val properties = List(Property("sessionId", session, None))
        invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", properties).kind shouldBe "Wait"
        val work = invoke[V.RepositorySyncAdvanceResult]("advanceRepositorySync", properties)
        work.kind shouldBe "Request"
        work.conflict.get.conflicts.map(_.path) shouldBe Vector(shared)
        work.review shouldBe None
        Files.exists(directory.resolve("sbt-executions.txt")) shouldBe false
      }
    }

    "retain a large textual uncertainty with exact public range access and stable pending work" in {
      val directory = root()
      _with_runtime_(directory, "Large", hostParameters = parameters(directory)) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(directory, "Large").size, scenario = "Large"))
        def invoke[A: org.goldenport.convert.ValueReader](service: String, operation: String, properties: List[Property]): A =
          read_value[A](take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = service, operation = operation, properties = properties), actor)))
        val session = invoke[V.RepositorySyncStartResult]("RepositorySyncService", "startRepositorySync", startInput).sessionId
        val properties = List(Property("sessionId", session, None))
        val work = invoke[V.RepositorySyncAdvanceResult]("RepositorySyncService", "advanceRepositorySync", properties)
        work.kind shouldBe "Request"
        work.review.get.judgmentMode shouldBe Some("triage")
        work.review.get.selectedChecks shouldBe empty
        work.review.get.localDiff should include("Preview only")
        val reference = "identity: \"([^\"]+)\"".r.findFirstMatchIn(work.review.get.localDiff).get.group(1)
        val full = invoke[V.EvidenceReadResult]("EvidenceService", "readEvidence", List(Property("input", V.EvidenceReadInput(reference, None), None)))
        full.status shouldBe "found"
        full.text.get.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 65536
        val points = full.text.get.codePointCount(0, full.text.get.length)
        val pieces = (0 until points by 32768).map { offset =>
          val range = invoke[V.EvidenceReadResult]("EvidenceService", "readEvidenceRange", List(Property("input",
            V.EvidenceRangeInput(reference, Some("1"), offset, 32768), None)))
          range.status shouldBe "found"
          range.metadata shouldBe full.metadata
          range.message should include("does not attest review coverage")
          range.text.get
        }
        pieces.mkString shouldBe full.text.get
        pieces.last should include("remote additional documentation line 4000")
        val pending = invoke[V.RepositorySyncAdvanceResult]("RepositorySyncService", "advanceRepositorySync", properties)
        pending.issuedWorkContext.get.continuationId shouldBe work.issuedWorkContext.get.continuationId
        pending.review shouldBe work.review
        Files.exists(directory.resolve("sbt-executions.txt")) shouldBe false
      }
    }

    "keep binary coverage explicitly unexamined rather than accepting a text-only judgment" in {
      val directory = root()
      _with_runtime_(directory, "Binary", hostParameters = parameters(directory)) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(directory, "Binary").size, scenario = "Binary"))
        val started = read_value[V.RepositorySyncStartResult](take(owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "RepositorySyncService", operation = "startRepositorySync", properties = startInput), actor)))
        val work = read_value[V.RepositorySyncAdvanceResult](take(owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "RepositorySyncService", operation = "advanceRepositorySync",
          properties = List(Property("sessionId", started.sessionId, None))), actor)))
        work.kind shouldBe "Request"
        work.review.get.uncertainties.exists(_.startsWith("Unexamined binary")) shouldBe true
        prepare(call => call.repositoryWorkC(InstanceIdentity(started.sessionId),
          RepositorySyncNativeMergeReviewContinuation.completionOperation, RepositorySyncNativeMergeReview.inputCodec)
          .flatMap(NativeWorkflowOperationOutput.workInputC).map { input =>
            RepositorySyncNativeMergeReview.admitC(input, RepositorySyncNativeMergeReview.Result("clean", "Text-only claim", Vector.empty),
              RepositorySyncNativeMergeReview.Policy("merge-review", 1)).isSuccess shouldBe false
            ()
          })
        Files.exists(directory.resolve("sbt-executions.txt")) shouldBe false
      }
    }

    "exclude consumed documents from the document-only shortcut and leave their program validation unresolved" in {
      val directory = root()
      _with_runtime_(directory, "Documents", hostParameters = parameters(directory, consumed = true)) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(directory, "Documents").size, scenario = "Documents"))
        val started = read_value[V.RepositorySyncStartResult](take(owner.executeOperationResponseInContext(Request.of(
          component = component.name, service = "RepositorySyncService", operation = "startRepositorySync", properties = startInput), actor)))
        val result = owner.executeOperationResponseInContext(Request.of(component = component.name, service = "RepositorySyncService",
          operation = "advanceRepositorySync", properties = List(Property("sessionId", started.sessionId, None))), actor)
        result.isSuccess shouldBe false
        result.toString should include("consumed=docs/local.md,docs/remote.md")
        Files.exists(directory.resolve("sbt-executions.txt")) shouldBe false
        Files.exists(directory.resolve("repository/.git/MERGE_HEAD")) shouldBe true
      }
    }
  }
}
