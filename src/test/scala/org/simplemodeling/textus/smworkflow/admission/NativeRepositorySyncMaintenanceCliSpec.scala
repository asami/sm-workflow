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

  "Installed RepositorySync maintenance continuation" should {
    "complete a dirty purpose then resolve a different actual merge conflict in the same Session" in {
      Given("the exact normal installed command using the published catalog's newest selection after repair publication and a fresh real local/bare fixture")
      val executable = _required_path("smworkflow.spec.installedCommand", executable = true)
      val scenario = F.fresh("installed-repository-maintenance", bootstrapSelectedInput = false)
      val root = scenario.root
      val repository = root.resolve("repository")
      val peer = root.resolve("peer")
      val remote = root.resolve("upstream.git")
      val configfile = root.resolve(".textus/config.json")
      val original = parse(Files.readString(configfile)).toOption.get
      val settings = maintenance_parameters() ++ Map("textus.resource.url.file.roots" -> root.toString,
        "textus.subsystem.process-execution.plan" -> process_plan(root, "Conflict", scenario.host.goal).noSpaces)
      Files.writeString(configfile, original.mapObject(fields => settings.foldLeft(fields.remove("sm-workflow.runtime.definition")) {
        case (prior, (key, value)) => prior.add(key, Json.fromString(value))
      }).noSpaces)
      def _call_(service: String, operation: String, arguments: Vector[String]): F.Result =
        scenario.callInstalled(executable, service, operation, arguments, None, Some(root.resolve(".textus/assembly.yaml")))
      def _repository_call_(operation: String, arguments: Vector[String]) = _call_("RepositorySyncService", operation, arguments)
      Files.writeString(repository.resolve(shared_path), "original shared line\n")
      git(root, "-C", repository.toString, "add", "--", shared_path)
      git(root, "-C", repository.toString, "commit", "-m", "initial shared documentation")
      git(root, "-C", repository.toString, "push", "origin", "HEAD:refs/heads/main")
      git(root, "clone", "--no-local", remote.toString, peer.toString)
      Files.writeString(peer.resolve(shared_path), "remote shared line\n")
      git(root, "-C", peer.toString, "add", "--", shared_path)
      git(root, "-C", peer.toString, "commit", "-m", "remote shared documentation")
      git(root, "-C", peer.toString, "push", "origin", "HEAD:refs/heads/main")
      Files.writeString(repository.resolve(shared_path), "local shared line\n")
      git(root, "-C", repository.toString, "add", "--", shared_path)
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
      Then("the genuine issued conflict uses the same Session and a path outside the last purpose")
      conflict.sessionId shouldBe session
      conflict.conflict.get.conflicts.map(_.path) shouldBe Vector(shared_path)
      _advance_().business[V.RepositorySyncAdvanceResult] shouldBe conflict
      git(root, "-C", repository.toString, "rev-parse", "HEAD") shouldBe head
      git(root, "-C", repository.toString, "rev-parse", "MERGE_HEAD") shouldBe merge
      git(root, "-C", repository.toString, "ls-files", "--stage") shouldBe index
      val pending = _current(root, session)
      pending.history.take(before.history.size) shouldBe before.history
      pending.history.count(_.currentProgression.contains(ProgressionReference("sync.PurposeInstalled"))) shouldBe 1
      val resolution = "local shared line\nremote shared line\n"
      val answer = V.RepositorySyncConflictResult("resolved", conflict.conflict.get.conflicts.map(c =>
        V.RepositorySyncConflictResolution(c.identity, resolution)), "Authored isolated specification proposal preserving both meanings")
      _submit_(conflict, answer, "spec-installed-resolution", "specification-conflict-author")
        .business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
      When("the installed owner materializes the actual resolution and requests independent semantic review")
      val reviewed = _advance_()
      val review = reviewed.business[V.RepositorySyncAdvanceResult]
      review.review should not be empty
      review.review.get.changedPaths should contain(shared_path)
      _advance_().business[V.RepositorySyncAdvanceResult].copy(journalRecording = None) shouldBe review.copy(journalRecording = None)
      val verdict = V.RepositorySyncNativeMergeReviewResult("accept",
        "Specification-owned independent review of both complete actual parent diffs; no live acceptance claimed", Vector.empty)
      val reference = V.WorkflowArtifactReference("spec-installed-independent-review", "1")
      val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-independent-reviewer"), None, None)
      val preparation = V.WorkflowEvidencePreparationInput("RepositorySync", "review", reviewed.invocation.stdout.toUri.toASCIIString,
        _save(root, "review-result.json", _json(verdict)), _save(root, "review-reference.json", _json(reference)),
        _save(root, "review-execution.json", _json(evidence)), "normal-development-host", "2026-10-07T00:00:00Z",
        "entity:smworkflow.cli.host_evidence", None)
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
      Files.readString(repository.resolve(purpose_path)) shouldBe purposetext
      _advance_().business[V.RepositorySyncAdvanceResult] shouldBe terminal
      take(WorkflowInstanceRecordJsonV1.encodeC(_current(root, session))) shouldBe take(WorkflowInstanceRecordJsonV1.encodeC(completed))
      Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      scenario.invocations should not be empty
      scenario.invocations.foreach(_.argv.head shouldBe executable.toString)
    }
  }
}
