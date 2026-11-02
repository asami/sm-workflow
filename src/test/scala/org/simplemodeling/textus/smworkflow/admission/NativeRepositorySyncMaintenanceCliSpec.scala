package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.record.RecordPresentable
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Explicit post-publication qualification of one exact installed executable.
  * Every business call starts that command. Authored proposal/reviewer inputs
  * qualify the isolated fixture and never attest live Human/model acceptance.
  * @since Nov. 3, 2026
  * @version Nov.  3, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositorySyncMaintenanceCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with RepositorySyncMaintenanceFixture {
  private def _required_path(name: String, executable: Boolean = false): Path = {
    val path = Path.of(sys.props.getOrElse(name, fail("Explicit installed qualification requires -D" + name)))
    path.isAbsolute shouldBe true
    path.normalize shouldBe path
    (if (executable) Files.isRegularFile(path) && Files.isExecutable(path) else Files.isDirectory(path)) shouldBe true
    path
  }
  private def _json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def _save(root: Path, name: String, text: String): String = {
    val path = root.resolve(name)
    Files.writeString(path, text)
    path.toUri.toASCIIString
  }
  /** Read-only native history evidence from the actual installed producer. */
  private def _current(root: Path, session: String): InstanceRecord = {
    val config = parse(Files.readString(root.resolve(".textus/config.json"))).toOption.get
    val database = config.hcursor.get[String]("textus.datastore.path").toOption.get
    val connection = new org.sqlite.JDBC().connect("jdbc:sqlite:" + Path.of(database).toUri.toASCIIString + "?mode=ro",
      new java.util.Properties())
    val catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(RepositorySyncDefinitionBinding.bindC))))
    try {
      val statement = connection.createStatement()
      try {
        val tables = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
        val names = Vector.newBuilder[String]
        try { while (tables.next()) names += tables.getString(1) } finally tables.close()
        val values = names.result().flatMap { name =>
          val rows = statement.executeQuery("SELECT * FROM \"" + name.replace("\"", "\"\"") + "\"")
          val found = Vector.newBuilder[InstanceRecord]
          try { while (rows.next()) (1 to rows.getMetaData.getColumnCount).foreach { column =>
            Option(rows.getString(column)).filter(_.startsWith("cncf.workflow-json.v1:")).foreach { raw =>
              WorkflowInstanceRecordJsonV1.decodeC(raw.stripPrefix("cncf.workflow-json.v1:"), catalog).toOption
                .filter(_.identity.value == session).foreach(found += _)
            }
          } } finally rows.close()
          found.result()
        }
        values.size shouldBe 1
        values.head
      } finally statement.close()
    } finally connection.close()
  }

  private def _finish_large_purpose(scenario: F.Scenario, session: String, issued: V.RepositorySyncAdvanceResult,
    purposetext: String, head: String, commitcount: Long): Unit = {
      import io.circe.generic.auto.*
      val executable = _required_path("smworkflow.spec.installedCommand", executable = true)
      val root = scenario.root
      val repository = root.resolve("repository")
      val remote = root.resolve("upstream.git")
      def _repository_call_(operation: String, arguments: Vector[String]): F.Result =
        scenario.callInstalled(executable, "RepositorySyncService", operation, arguments, None,
          Some(root.resolve(".textus/assembly.yaml")))
      def _advance_() = _repository_call_("advanceRepositorySync", Vector("--sessionId", session))
      val purposebytes = purposetext.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      purposebytes.length shouldBe 2170000
      purposebytes.length should be > 1048576
      Then("the actual issued Purpose request contains the entire ordinary text beyond the argument budget")
      issued.sessionId shouldBe session
      issued.kind shouldBe "Request"
      issued.submitSessionId shouldBe Some(session)
      issued.submitOperation shouldBe Some("submitRepositoryPurposeResult")
      issued.purposeWork should not be empty
      val files = parse(take(issued.purposeWork.get.toJsonStringC)).toOption.get.hcursor.get[Vector[Json]]("files").toOption.get
      val file = files.find(_.hcursor.get[String]("path").toOption.contains(purpose_path)).get.hcursor
      file.get[String]("worktree").toOption.get shouldBe purposetext
      file.downField("unexamined").focus.forall(_.isNull) shouldBe true
      val proposal = grouping(purposetext)
      val purpose = V.RepositorySyncPurposeResult(proposal.decision, proposal.rationale,
        proposal.groups.map(value => GoalPhaseNativeMetadata.record(value.asJson)), proposal.excludedPaths)
      val text = _json(purpose)
      text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 2170000
      val uri = _save(root, "complete-purpose-result.json", text)
      val reference = V.WorkflowArtifactReference("spec-installed-large-purpose", "1")
      val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-purpose-author"), None, None)
      val metadata = Vector("--sessionId", session, "--resultReference", _json(reference), "--executionEvidence", _json(evidence))
      val malformed = _save(root, "malformed-purpose-result.json", "{}")
      val oversized = root.resolve("over-budget-purpose-result.json")
      Files.write(oversized, Array.fill[Byte](16777217)('x'.toByte))
      Files.size(oversized) shouldBe 16777217L
      val outside = Files.createTempFile(root.getParent, "outside-purpose-result-", ".json")
      val small = _json(V.RepositorySyncPurposeResult("resolved", "Ambiguous transport refusal", Vector.empty, Vector.empty))
      val refusals = Vector(
        "both sources" -> Vector("--result", small, "--resultResource", uri),
        "neither source" -> Vector.empty[String],
        "nonlocal URI" -> Vector("--resultResource", "file://foreign/tmp/purpose.json"),
        "nonexistent file" -> Vector("--resultResource", root.resolve("absent-purpose-result.json").toUri.toASCIIString),
        "unauthorized outside-root file" -> Vector("--resultResource", outside.toUri.toASCIIString),
        "malformed DTO" -> Vector("--resultResource", malformed),
        "finite read budget overflow" -> Vector("--resultResource", oversized.toUri.toASCIIString))
      try {
        Files.writeString(outside, text, java.nio.charset.StandardCharsets.UTF_8)
        refusals.foreach { (label, arguments) =>
          Given("the actual pending Purpose assignment before " + label)
          val saved = take(WorkflowInstanceRecordJsonV1.encodeC(_current(root, session)))
          val savedhead = git(root, "-C", repository.toString, "rev-parse", "HEAD")
          val savedindex = git(root, "-C", repository.toString, "ls-files", "--stage")
          val savedtext = Files.readAllBytes(repository.resolve(purpose_path))
          Files.exists(repository.resolve(".git/MERGE_HEAD")) shouldBe false
          When("the installed Purpose Submit receives " + label)
          val refused = _repository_call_(issued.submitOperation.get, metadata ++ arguments)
          Then("refusal preserves the full native revision/history HEAD index and complete worktree text without any merge")
          withClue(label + ": " + refused.invocation.logs) { refused.exitCode should not be 0 }
          take(WorkflowInstanceRecordJsonV1.encodeC(_current(root, session))) shouldBe saved
          git(root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe savedhead
          git(root, "-C", repository.toString, "ls-files", "--stage") shouldBe savedindex
          Files.readAllBytes(repository.resolve(purpose_path)) shouldBe savedtext
          Files.readAllBytes(repository.resolve(purpose_path)) shouldBe purposebytes
          Files.exists(repository.resolve(".git/MERGE_HEAD")) shouldBe false
        }
        When("one installed Purpose Submit reads the complete public DTO from its authorized local Resource URI")
        val submitted = _repository_call_(issued.submitOperation.get, metadata ++ Vector("--resultResource", uri))
          .business[V.RepositorySyncConflictSubmissionResult]
        Then("the genuine result is accepted once while HEAD still awaits the owning Advance")
        submitted.accepted shouldBe true
        submitted.sessionId shouldBe session
        git(root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe head
        git(root, "-C", repository.toString, "rev-list", "--count", "HEAD").toLong shouldBe commitcount
        _current(root, session).history.count(_.currentProgression.contains(ProgressionReference("sync.PurposeInstalled"))) shouldBe 0
      } finally Files.deleteIfExists(outside)
      When("normal installed Advance owns Purpose candidate acceptance installation local commit push and final fetch")
      val terminal = _advance_().business[V.RepositorySyncAdvanceResult]
      Then("the same Session completes with exactly one purpose installation and commit and byte-exact local and pushed contents")
      terminal.kind shouldBe "Terminal"
      terminal.sessionId shouldBe session
      val completed = _current(root, session)
      completed.lifecycle shouldBe Lifecycle.Completed
      completed.history.count(_.currentProgression.contains(ProgressionReference("sync.PurposeInstalled"))) shouldBe 1
      val tip = git(root, "-C", repository.toString, "rev-parse", "HEAD")
      tip should not be head
      git(root, "-C", repository.toString, "show", "-s", "--format=%P", "HEAD") shouldBe head
      git(root, "-C", repository.toString, "rev-list", "--count", "HEAD").toLong shouldBe (commitcount + 1L)
      git(root, "-C", repository.toString, "log", "-1", "--format=%s") shouldBe "docs: retain independent purpose"
      git(root, "--git-dir=" + remote, "rev-parse", "refs/heads/main") shouldBe tip
      Files.readAllBytes(repository.resolve(purpose_path)) shouldBe purposebytes
      val blob = git(root, "-C", repository.toString, "hash-object", "--", purpose_path)
      git(root, "-C", repository.toString, "rev-parse", "HEAD:" + purpose_path) shouldBe blob
      git(root, "--git-dir=" + remote, "rev-parse", "refs/heads/main:" + purpose_path) shouldBe blob
      git(root, "--git-dir=" + remote, "cat-file", "-s", blob).toLong shouldBe purposebytes.length.toLong
      git(root, "-C", repository.toString, "status", "--porcelain") shouldBe ""
      Files.exists(repository.resolve(".git/MERGE_HEAD")) shouldBe false
      val finalindex = git(root, "-C", repository.toString, "ls-files", "--stage")
      val finalrecord = take(WorkflowInstanceRecordJsonV1.encodeC(completed))
      When("a passive terminal installed Advance collects the same completed Session")
      val collected = _advance_().business[V.RepositorySyncAdvanceResult]
      Then("the terminal result full record repository and remote retain the single completed effect")
      collected shouldBe terminal
      take(WorkflowInstanceRecordJsonV1.encodeC(_current(root, session))) shouldBe finalrecord
      git(root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe tip
      git(root, "-C", repository.toString, "rev-list", "--count", "HEAD").toLong shouldBe (commitcount + 1L)
      git(root, "--git-dir=" + remote, "rev-parse", "refs/heads/main") shouldBe tip
      git(root, "-C", repository.toString, "ls-files", "--stage") shouldBe finalindex
      Files.readAllBytes(repository.resolve(purpose_path)) shouldBe purposebytes
      git(root, "-C", repository.toString, "status", "--porcelain") shouldBe ""
      Files.exists(repository.resolve(".git/MERGE_HEAD")) shouldBe false
      Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      scenario.invocations should not be empty
      scenario.invocations.foreach(_.argv.head shouldBe executable.toString)
  }

  "Installed RepositorySync maintenance continuation" should {
    "complete a large purpose Resource through one accepted Submit and one purpose commit before terminal convergence" in {
      Given("the normal installed command and a fresh equal local/bare fixture with finite authorized Resource and Git budgets")
      val executable = _required_path("smworkflow.spec.installedCommand", executable = true)
      val scenario = F.fresh("installed-repository-large-purpose-resource", bootstrapSelectedInput = false)
      val root = scenario.root
      val repository = root.resolve("repository")
      val configfile = root.resolve(".textus/config.json")
      val original = parse(Files.readString(configfile)).toOption.get
      val baseplan = process_plan(root, "Equal", scenario.host.goal)
      def _large_program_(program: Json): Json = program.mapObject { fields =>
        val limits = fields("limits").get.mapObject { values =>
          Vector("stdinBytes", "stdoutBytes", "artifactBytes").foldLeft(values) { (prior, key) =>
            prior.add(key, Json.fromLong(8388608L))
          }.add("workAreaBytes", Json.fromLong(33554432L))
        }
        fields.add("limits", limits)
      }
      val plan = Vector("gitPrograms", "gitRepositories").foldLeft(baseplan) { (prior, name) =>
        prior.mapObject(fields => fields.add(name, fields(name).get.mapArray(_.map(_large_program_))))
      }
      // The existing independent-document rule selects no product checks or
      // mandatory detailed review. This is applicability, never a fake pass.
      val settings = maintenance_parameters() ++ Map("textus.resource.url.file.roots" -> root.toString,
        "cncf.resource.read.max-bytes" -> "16777216", "cncf.resource.write.max-bytes" -> "8388608",
        "textus.subsystem.process-execution.plan" -> plan.noSpaces)
      val configured = original.mapObject(fields => settings.foldLeft(fields.remove("sm-workflow.runtime.definition")) {
        case (prior, (key, value)) => prior.add(key, Json.fromString(value))
      }).mapObject(_.add("cncf.git.diff.maximum-bytes", Json.fromLong(8388608L)))
      Files.writeString(configfile, configured.noSpaces)
      def _repository_call_(operation: String, arguments: Vector[String]): F.Result =
        scenario.callInstalled(executable, "RepositorySyncService", operation, arguments, None,
          Some(root.resolve(".textus/assembly.yaml")))
      val head = git(root, "-C", repository.toString, "rev-parse", "HEAD")
      val commitcount = git(root, "-C", repository.toString, "rev-list", "--count", "HEAD").toLong
      val purposetext = "完全な本文を保持する\n" * 70000
      val purposebytes = purposetext.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      purposebytes.length shouldBe 2170000
      purposebytes.length should be > 1048576
      Files.write(repository.resolve(purpose_path), purposebytes)
      When("normal installed Start and Advance issue the genuine dirty Purpose assignment")
      val started = _repository_call_("startRepositorySync", Vector("--input",
        "{\"repositoryIdentity\":\"sm-workflow\",\"syncMode\":\"FULL_STATE\"}")).business[V.RepositorySyncStartResult]
      val session = started.sessionId
      val issued = _repository_call_("advanceRepositorySync", Vector("--sessionId", session)).business[V.RepositorySyncAdvanceResult]
      _finish_large_purpose(scenario, session, issued, purposetext, head, commitcount)
    }

    "complete retained large purpose Resource without restarting" in {
      Given("the canonical retained fixture with its actual pending large Purpose issue and unchanged default host metadata")
      import io.circe.generic.auto.*
      val root = _required_path("smworkflow.spec.purposeResourceRetainedRoot")
      val repository = root.resolve("repository")
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val host = original.copy(target = original.target.copy(root = repository.toString),
        repository = original.repository.copy(input = original.repository.input.copy(
          repositoryIdentity = original.goal.input.repositoryIdentity)))
      val plan = parse(Files.readString(root.resolve("goal-plan.json"))).toOption.get
        .as[GoalPhaseOrderedProgression.Plan].toOption.get
      val existinglogs = Files.list(root.resolve("logs"))
      val startinglogindex = try {
        math.max(1000, existinglogs.filter(path => Files.isRegularFile(path) &&
          path.getFileName.toString.matches("[0-9]+\\.(stdout\\.json|stderr)"))
          .mapToInt(path => path.getFileName.toString.takeWhile(_.isDigit).toInt).max().orElse(999) + 1)
      } finally existinglogs.close()
      val scenario = F.reopenInstalledRepository(root, host, plan, startinglogindex)
      val record = take(new org.goldenport.record.io.RecordDecoder().json(Files.readString(root.resolve("logs/7.stdout.json"))))
      val issued = take(V.RepositorySyncAdvanceResult.createC(record))
      val session = issued.sessionId
      val current = _current(root, session)
      current.identity.value shouldBe session
      current.lifecycle should not be Lifecycle.Completed
      current.suspension should not be empty
      val request = parse(take(issued.purposeWork.get.toJsonStringC)).toOption.get.hcursor
      val head = git(root, "-C", repository.toString, "rev-parse", "HEAD")
      head shouldBe request.get[String]("parent").toOption.get
      git(root, "-C", repository.toString, "write-tree") shouldBe request.get[String]("index_tree").toOption.get
      val purposetext = "完全な本文を保持する\n" * 70000
      Files.readString(repository.resolve(purpose_path)) shouldBe purposetext
      val commitcount = git(root, "-C", repository.toString, "rev-list", "--count", "HEAD").toLong
      When("only the pending Purpose completion and its owning Advance continue the actual retained Session")
      _finish_large_purpose(scenario, session, issued, purposetext, head, commitcount)
    }

    for (transport <- Vector("Inline", "Resource")) {
    s"complete a dirty purpose then resolve mixed modify/modify and add/add conflicts in the same Session using $transport" in {
      Given("the exact normal installed command using the published catalog's newest selection after repair publication and a fresh real local/bare fixture")
      val resource = transport == "Resource"
      val executable = _required_path("smworkflow.spec.installedCommand", executable = true)
      val scenario = F.fresh("installed-repository-maintenance-" + transport, bootstrapSelectedInput = false)
      val root = scenario.root
      val repository = root.resolve("repository")
      val peer = root.resolve("peer")
      val remote = root.resolve("upstream.git")
      val configfile = root.resolve(".textus/config.json")
      val original = parse(Files.readString(configfile)).toOption.get
      val baseplan = process_plan(root, "Conflict", scenario.host.goal)
      def _large_program_(program: Json): Json = program.mapObject { fields =>
        val limits = fields("limits").get.mapObject { values =>
          Vector("stdinBytes", "stdoutBytes", "artifactBytes").foldLeft(values) { (prior, key) =>
            prior.add(key, Json.fromLong(4194304L))
          }.add("workAreaBytes", Json.fromLong(16777216L))
        }
        fields.add("limits", limits)
      }
      val plan = if (resource) Vector("gitPrograms", "gitRepositories").foldLeft(baseplan) { (prior, name) =>
        prior.mapObject(fields => fields.add(name, fields(name).get.mapArray(_.map(_large_program_))))
      } else baseplan
      val settings = maintenance_parameters() ++ Map("textus.resource.url.file.roots" -> root.toString,
        "cncf.resource.read.max-bytes" -> (if (resource) "16777216" else "2097152"),
        "textus.subsystem.process-execution.plan" -> plan.noSpaces) ++
        (if (resource) Map("cncf.resource.write.max-bytes" -> "4194304")
         else Map.empty[String, String])
      val configured = original.mapObject(fields => settings.foldLeft(fields.remove("sm-workflow.runtime.definition")) {
        case (prior, (key, value)) => prior.add(key, Json.fromString(value))
      })
      val completeconfig = if (resource) configured.mapObject(_.add("cncf.git.diff.maximum-bytes", Json.fromLong(4194304L))) else configured
      Files.writeString(configfile, completeconfig.noSpaces)
      def _call_(service: String, operation: String, arguments: Vector[String]): F.Result =
        scenario.callInstalled(executable, service, operation, arguments, None, Some(root.resolve(".textus/assembly.yaml")))
      def _repository_call_(operation: String, arguments: Vector[String]) = _call_("RepositorySyncService", operation, arguments)
      Files.writeString(repository.resolve(shared_path), "original shared line\n")
      git(root, "-C", repository.toString, "add", "--", shared_path)
      git(root, "-C", repository.toString, "commit", "-m", "initial shared documentation")
      git(root, "-C", repository.toString, "push", "origin", "HEAD:refs/heads/main")
      git(root, "clone", "--no-local", remote.toString, peer.toString)
      Files.writeString(peer.resolve(shared_path), "remote shared line\n")
      Files.writeString(peer.resolve(add_path), add_remote_text)
      git(root, "-C", peer.toString, "add", "--", shared_path, add_path)
      git(root, "-C", peer.toString, "commit", "-m", "remote shared documentation")
      git(root, "-C", peer.toString, "push", "origin", "HEAD:refs/heads/main")
      Files.writeString(repository.resolve(shared_path), "local shared line\n")
      Files.writeString(repository.resolve(add_path), add_local_text)
      git(root, "-C", repository.toString, "add", "--", shared_path, add_path)
      git(root, "-C", repository.toString, "commit", "-m", "local shared documentation")
      val started = _repository_call_("startRepositorySync", Vector("--input",
        "{\"repositoryIdentity\":\"sm-workflow\",\"syncMode\":\"FULL_STATE\"}")).business[V.RepositorySyncStartResult]
      val session = started.sessionId
      def _advance_() = _repository_call_("advanceRepositorySync", Vector("--sessionId", session))
      def _submit_(issued: V.RepositorySyncAdvanceResult, result: RecordPresentable, name: String,
        worker: String): F.Result = {
        val reference = V.WorkflowArtifactReference(name, "1")
        val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some(worker), None, None)
        _repository_call_(issued.submitOperation.get, Vector("--sessionId", session, "--result", _json(result),
          "--resultReference", _json(reference), "--executionEvidence", _json(evidence)))
      }
      val purposetext = "完全な本文を保持する\n" * 25000
      val payloadbytes = purposetext.getBytes(java.nio.charset.StandardCharsets.UTF_8).length.toLong
      payloadbytes shouldBe 775000L
      payloadbytes should be < 1048576L
      (payloadbytes * 2L) should be > 1048576L
      Files.writeString(repository.resolve(purpose_path), purposetext)
      When("separate installed calls capture and submit the actual dirty purpose")
      val issued = _advance_().business[V.RepositorySyncAdvanceResult]
      issued.submitOperation shouldBe Some("submitRepositoryPurposeResult")
      val proposal = grouping(purposetext)
      import io.circe.generic.auto.*
      val purpose = V.RepositorySyncPurposeResult(proposal.decision, proposal.rationale,
        proposal.groups.map(value => GoalPhaseNativeMetadata.record(value.asJson)), proposal.excludedPaths)
      _submit_(issued, purpose, "spec-installed-purpose", "specification-purpose-author")
        .business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
      val waiting = _advance_().business[V.RepositorySyncAdvanceResult]
      waiting.kind shouldBe "Wait"
      val before = _current(root, session)
      before.currentProgression shouldBe Some(ProgressionReference("sync.MergeConflicted"))
      val head = git(root, "-C", repository.toString, "rev-parse", "HEAD")
      val merge = git(root, "-C", repository.toString, "rev-parse", "MERGE_HEAD")
      val index = git(root, "-C", repository.toString, "ls-files", "--stage")
      When("a later installed Advance selects the current conflict after purpose completion")
      val conflict = _advance_().business[V.RepositorySyncAdvanceResult]
      Then("the genuine issued conflicts retain each actual base or its absence in the same Session outside the last purpose")
      conflict.sessionId shouldBe session
      conflict.conflict.get.conflicts.map(_.path) shouldBe Vector(add_path, shared_path)
      val added = conflict.conflict.get.conflicts.find(_.path == add_path).get
      val shared = conflict.conflict.get.conflicts.find(_.path == shared_path).get
      added.base shouldBe None
      added.local.text shouldBe add_local_text
      added.remote.text shouldBe add_remote_text
      shared.base.get.text shouldBe "original shared line\n"
      shared.local.text shouldBe "local shared line\n"
      shared.remote.text shouldBe "remote shared line\n"
      conflict.conflict.get.conflicts.foreach(c => c.worktree shouldBe Files.readString(repository.resolve(c.path)))
      def _stages_(path: String): Vector[Int] = index.linesIterator.filter(_.split("\t", 2).last == path)
        .map(_.takeWhile(_ != '\t').split(" ").last.toInt).toVector
      _stages_(add_path) shouldBe Vector(2, 3)
      _stages_(shared_path) shouldBe Vector(1, 2, 3)
      _advance_().business[V.RepositorySyncAdvanceResult] shouldBe conflict
      git(root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe head
      git(root, "-C", repository.toString, "rev-parse", "MERGE_HEAD") shouldBe merge
      git(root, "-C", repository.toString, "ls-files", "--stage") shouldBe index
      val pending = _current(root, session)
      pending.history.take(before.history.size) shouldBe before.history
      pending.history.count(_.currentProgression.contains(ProgressionReference("sync.PurposeInstalled"))) shouldBe 1
      val resolution = "local shared line\nremote shared line\n"
      val addedresolution = add_local_text + add_remote_text + (if (resource) ("解" * 800000) + "\n" else "")
      addedresolution.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 131072
      added.local.text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 90000
      added.remote.text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 90000
      val answer = V.RepositorySyncConflictResult("resolved", conflict.conflict.get.conflicts.map(c =>
        V.RepositorySyncConflictResolution(c.identity, if (c.path == add_path) addedresolution else resolution)),
        "Authored isolated specification proposal preserving both meanings")
      val submitted = if (resource) {
        Given("a complete conflict DTO above the host argument budget saved under the isolated authorized Resource root")
        val text = _json(answer)
        text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length.toLong should be > 2300000L
        ("解" * 800000).getBytes(java.nio.charset.StandardCharsets.UTF_8).length.toLong shouldBe 2400000L
        val uri = _save(root, "complete-conflict-result.json", text)
        val reference = V.WorkflowArtifactReference("spec-installed-resolution", "1")
        val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-conflict-author"), None, None)
        val metadata = Vector("--sessionId", session, "--resultReference", _json(reference), "--executionEvidence", _json(evidence))
        val malformed = _save(root, "malformed-conflict-result.json", "{}")
        val oversized = root.resolve("over-budget-conflict-result.json")
        Files.write(oversized, Array.fill[Byte](16777217)('x'.toByte))
        Files.size(oversized) shouldBe 16777217L
        val outside = Files.createTempFile(root.getParent, "outside-conflict-result-", ".json")
        Files.writeString(outside, text, java.nio.charset.StandardCharsets.UTF_8)
        val small = _json(V.RepositorySyncConflictResult("resolved", Vector.empty, "Ambiguous transport refusal"))
        val refusals = Vector(
          "both sources" -> Vector("--result", small, "--resultResource", uri),
          "neither source" -> Vector.empty[String],
          "nonlocal URI" -> Vector("--resultResource", "file://foreign/tmp/result.json"),
          "nonexistent file" -> Vector("--resultResource", root.resolve("absent-conflict-result.json").toUri.toASCIIString),
          "unauthorized outside-root file" -> Vector("--resultResource", outside.toUri.toASCIIString),
          "malformed DTO" -> Vector("--resultResource", malformed),
          "finite read budget overflow" -> Vector("--resultResource", oversized.toUri.toASCIIString))
        try {
          refusals.foreach { (label, arguments) =>
            Given("the actual pending conflict assignment before " + label)
            val saved = take(WorkflowInstanceRecordJsonV1.encodeC(_current(root, session)))
            val savedhead = git(root, "-C", repository.toString, "rev-parse", "HEAD")
            val savedmerge = git(root, "-C", repository.toString, "rev-parse", "MERGE_HEAD")
            val savedindex = git(root, "-C", repository.toString, "ls-files", "--stage")
            When("the installed conflict Submit receives " + label)
            val refused = _repository_call_(conflict.submitOperation.get, metadata ++ arguments)
            Then("the actual native record including revision/history and Git merge state remain unchanged")
            withClue(label + ": " + refused.invocation.logs) { refused.exitCode should not be 0 }
            take(WorkflowInstanceRecordJsonV1.encodeC(_current(root, session))) shouldBe saved
            git(root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe savedhead
            git(root, "-C", repository.toString, "rev-parse", "MERGE_HEAD") shouldBe savedmerge
            git(root, "-C", repository.toString, "ls-files", "--stage") shouldBe savedindex
          }
          When("one installed Submit reads the complete valid conflict DTO through its local Resource URI")
          _repository_call_(conflict.submitOperation.get, metadata ++ Vector("--resultResource", uri))
        } finally Files.deleteIfExists(outside)
      } else _submit_(conflict, answer, "spec-installed-resolution", "specification-conflict-author")
      Then("the exact supplied conflict result is accepted without advancing materialization")
      submitted
        .business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
      When("the installed owner materializes the actual resolution and requests independent semantic review")
      val reviewed = _advance_()
      val review = reviewed.business[V.RepositorySyncAdvanceResult]
      review.review should not be empty
      review.review.get.changedPaths should contain(shared_path)
      review.review.get.changedPaths should contain(add_path)
      val actual = review.review.get
      actual.localDiff.trim shouldBe git(root, "-C", repository.toString, "diff", "--cached", actual.localTip, "--")
      actual.remoteDiff.trim shouldBe git(root, "-C", repository.toString, "diff", "--cached", actual.remoteTip, "--")
      actual.localDiff should include("+" + ("語" * 30000))
      actual.remoteDiff should include("+" + ("界" * 30000))
      if (resource) {
        actual.localDiff should include("+" + ("解" * 800000))
        actual.remoteDiff should include("+" + ("解" * 800000))
        actual.localDiff.getBytes(java.nio.charset.StandardCharsets.UTF_8).length.toLong should be < 4194304L
        actual.remoteDiff.getBytes(java.nio.charset.StandardCharsets.UTF_8).length.toLong should be < 4194304L
        Files.readAllBytes(repository.resolve(add_path)) shouldBe addedresolution.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      } else {
        actual.localDiff.getBytes(java.nio.charset.StandardCharsets.UTF_8).length.toLong should be < 1048576L
        actual.remoteDiff.getBytes(java.nio.charset.StandardCharsets.UTF_8).length.toLong should be < 1048576L
      }
      _advance_().business[V.RepositorySyncAdvanceResult].copy(journalRecording = None) shouldBe review.copy(journalRecording = None)
      val verdict = V.RepositorySyncNativeMergeReviewResult("accept",
        "Specification-owned independent review of both complete actual parent diffs; no live acceptance claimed", Vector.empty)
      val reference = V.WorkflowArtifactReference("spec-installed-independent-review", "1")
      val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
      val preparation = V.WorkflowEvidencePreparationInput("RepositorySync", "review", reviewed.invocation.stdout.toUri.toASCIIString,
        _save(root, "review-result.json", _json(verdict)), _save(root, "review-reference.json", _json(reference)),
        _save(root, "review-execution.json", _json(evidence)), "normal-development-host", "2026-10-07T00:00:00Z",
        "entity:smworkflow.cli.host_evidence", None)
      Files.size(reviewed.invocation.stdout) should be > 1048576L
      Files.size(reviewed.invocation.stdout) should be < (if (resource) 16777216L else 2097152L)
      val prepared = _call_("NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(preparation)))
      withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
      scenario.selectPreparedImport(prepared)
      _repository_call_(review.submitOperation.get, Vector("--sessionId", session, "--result", _json(verdict),
        "--resultReference", _json(reference), "--executionEvidence", _json(evidence)))
        .business[V.RepositorySyncNativeMergeReviewSubmissionResult].accepted shouldBe true
      val terminal = _advance_().business[V.RepositorySyncAdvanceResult]
      Then("real merge commit push and final fetch converge and later completed collection has no effects")
      terminal.kind shouldBe "Terminal"
      val completed = _current(root, session)
      completed.lifecycle shouldBe Lifecycle.Completed
      val tip = git(root, "-C", repository.toString, "rev-parse", "HEAD")
      git(root, "-C", repository.toString, "show", "-s", "--format=%P", "HEAD").split(" ").toVector shouldBe Vector(head, merge)
      git(root, "--git-dir=" + remote, "rev-parse", "refs/heads/main") shouldBe tip
      git(root, "-C", repository.toString, "status", "--porcelain") shouldBe ""
      Files.exists(repository.resolve(".git/MERGE_HEAD")) shouldBe false
      Files.readString(repository.resolve(shared_path)) shouldBe resolution
      Files.readString(repository.resolve(add_path)) shouldBe addedresolution
      if (resource) Files.readAllBytes(repository.resolve(add_path)) shouldBe addedresolution.getBytes(java.nio.charset.StandardCharsets.UTF_8)
      Files.readString(repository.resolve(purpose_path)) shouldBe purposetext
      val finalindex = git(root, "-C", repository.toString, "ls-files", "--stage")
      _advance_().business[V.RepositorySyncAdvanceResult] shouldBe terminal
      git(root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe tip
      git(root, "--git-dir=" + remote, "rev-parse", "refs/heads/main") shouldBe tip
      git(root, "-C", repository.toString, "ls-files", "--stage") shouldBe finalindex
      Files.exists(repository.resolve(".git/MERGE_HEAD")) shouldBe false
      take(WorkflowInstanceRecordJsonV1.encodeC(_current(root, session))) shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(completed))
      Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      scenario.invocations should not be empty
      scenario.invocations.foreach(_.argv.head shouldBe executable.toString)
    }
    }
  }
}
