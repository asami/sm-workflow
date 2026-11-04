package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalReceiptLimits, WorkflowMechanicalTransitionJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P
import SplitPhaseExplicitPartitionFixture.take

/** Installed old/new product qualification over one retained physical Session.
  * Every repository, lifecycle direction and independent reviewer is owned by
  * this fixture. They make no assertion about a real consumer or Human consent.
  * Native datastore access is read only; all transitions use public Operations.
  * @since Nov. 4, 2026
  * @version Nov. 4, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryPurposeMaterializationContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeRepositoryContinuationCliFixture {
  private val _policy = "sm-workflow.repository-sync-purpose-materialization-continuation.v1"
  private val _shared = "docs/materialization-shared.md"
  private val _later = "docs/materialization-later.md"
  private val _initial_text = "Initial document.\n"
  private val _first_text = "Initial document.\nFirst independent explanation.\n"
  private val _final_text = "Initial document.\nFirst independent explanation.\nLater independent explanation.\n"
  private val _later_text = "Untracked text owned only by the later document purpose.\n"
  private val _stage = _select_stage()
  private lazy val _catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(RepositorySyncDefinitionBinding.bindC))))
  private lazy val _receipt_limits = take(WorkflowMechanicalReceiptLimits.createC(WorkflowMechanicalReceiptLimits.maximumBytes))

  private def _select_stage(): String = {
    Vector("EpisodeStage" -> "EPISODE_STAGE", "EpisodeDirectory" -> "EPISODE_DIRECTORY", "CollectOnly" -> "COLLECT_ONLY")
      .foreach { (suffix, environment) =>
        val key = "smworkflow.spec.purposeMaterialization" + suffix
        if (!sys.props.contains(key)) sys.env.get("SM_WORKFLOW_SPEC_PURPOSE_MATERIALIZATION_" + environment)
          .foreach(value => System.setProperty(key, value))
      }
    if (!sys.props.contains("smworkflow.spec.installedCommand")) sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND")
      .foreach(value => System.setProperty("smworkflow.spec.installedCommand", value))
    if (!sys.props.contains("smworkflow.spec.installations")) sys.env.get("SM_WORKFLOW_SPEC_INSTALLATIONS")
      .foreach(value => System.setProperty("smworkflow.spec.installations", value))
    val selected = sys.props.getOrElse("smworkflow.spec.purposeMaterializationEpisodeStage",
      fail("Select purposeMaterializationEpisodeStage=before|after|future explicitly"))
    require(Set("before", "after", "future")(selected), "Select one installed materialization episode stage")
    selected
  }
  private def _installed: Path = {
    val executable = Path.of(sys.props.getOrElse("smworkflow.spec.installedCommand",
      fail("Select the actual absolute installed command; no source fallback")))
    require(executable.isAbsolute && executable.normalize == executable && Files.isRegularFile(executable) && Files.isExecutable(executable))
    executable
  }
  private def _episode_path(name: String = "materialization"): Path = {
    val directory = Path.of(sys.props.getOrElse("smworkflow.spec.purposeMaterializationEpisodeDirectory",
      fail("Select an existing absolute episode directory outside Git")))
    require(directory.isAbsolute && directory.normalize == directory && Files.isDirectory(directory))
    var ancestor = Option(directory)
    while (ancestor.nonEmpty) {
      require(!Files.exists(ancestor.get.resolve(".git")), "Retained materialization episodes must be outside Git")
      ancestor = Option(ancestor.get.getParent)
    }
    directory.resolve(name + ".json")
  }
  private def _success(result: F.Result): F.Result = {
    withClue(result.invocation.logs) { result.exitCode shouldBe 0 }
    result.invocation.argv.head shouldBe _installed.toString
    result
  }
  private def _read(path: Path): Json = parse(Files.readString(path, UTF_8)).fold(error => fail(error.message), identity)
  private def _retain(path: Path, value: Json): Unit = {
    Files.writeString(path, value.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
    ()
  }
  private def _invocation(result: F.Result): Json = Json.obj("exitCode" -> result.exitCode.asJson,
    "argv" -> result.invocation.argv.asJson, "pid" -> result.invocation.pid.asJson,
    "stdout" -> result.invocation.stdout.toString.asJson, "stderr" -> result.invocation.stderr.toString.asJson)
  private def _repository(scenario: F.Scenario): Path = Path.of(scenario.host.target.root)
  private def _array(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
  override protected def receipts(scenario: F.Scenario, session: String) = stored(scenario).flatMap(raw =>
    WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, N.codec, _receipt_limits).toOption)
      .filter(_.next.identity.value == session)
  private def _stages(scenario: F.Scenario, session: String, stage: String) =
    receipts(scenario, session).filter(_.payload.value.stage == stage).sortBy(_.next.revision.value)
  private def _native(scenario: F.Scenario, session: String): Json = receipts(scenario, session).sortBy(_.next.revision.value)
    .map(receipt => Json.obj("reference" -> receipt.reference.asJson,
      "next" -> take(WorkflowInstanceRecordJsonV1.encodeC(receipt.next)).asJson,
      "payload" -> N.codec.encode(receipt.payload.value))).asJson
  private def _state(scenario: F.Scenario, session: String): Json = {
    val repository = _repository(scenario)
    Json.obj("record" -> take(WorkflowInstanceRecordJsonV1.encodeC(current(scenario, session))).asJson,
      "nativeReceipts" -> _native(scenario, session), "inventories" -> purpose_inputs(scenario).map(P.inputCodec.encode).asJson,
      "head" -> git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD").asJson,
      "indexTree" -> git(scenario.root, "-C", repository.toString, "write-tree").asJson,
      "status" -> git(scenario.root, "-C", repository.toString, "status", "--porcelain=v1", "--untracked-files=all").asJson,
      "worktree" -> Json.obj(_shared -> Files.readString(repository.resolve(_shared), UTF_8).asJson,
        _later -> Files.readString(repository.resolve(_later), UTF_8).asJson),
      "remoteTip" -> git(scenario.root, "--git-dir=" + scenario.host.target.endpoint, "rev-parse", "refs/heads/main").asJson)
  }
  private def _projection(scenario: F.Scenario): Unit = {
    Files.readString(_repository(scenario).resolve(_shared), UTF_8) shouldBe _final_text
    Files.readString(_repository(scenario).resolve(_later), UTF_8) shouldBe _later_text
  }
  private def _host_plan(scenario: F.Scenario): Json = {
    val base = process_plan(scenario.root, "Equal", scenario.host.goal)
    val original = _array(base, "gitPrograms")
    val programs = original.filter(_.hcursor.get[String]("target").toOption.contains("purpose-slot-1")).map { program =>
      program.mapObject(fields => fields.add("target", "purpose-slot-2".asJson)
        .add("identity", (fields("identity").get.asString.get + "-second-slot").asJson)
        .add("capability", (fields("capability").get.asString.get + "-second-slot").asJson))
    }
    Files.createDirectories(scenario.root.resolve("purpose-slot-2"))
    val grant = _array(base, "workspaceGrants").find(_.hcursor.get[String]("workspaceId").toOption.contains("purpose-slot-1")).get
      .mapObject(_.add("workspaceId", "purpose-slot-2".asJson))
    base.mapObject(fields => fields.add("gitPrograms", (original ++ programs).asJson)
      .add("workspaces", (_array(base, "workspaces") :+ Json.obj("identity" -> "purpose-slot-2".asJson,
        "root" -> scenario.root.resolve("purpose-slot-2").toString.asJson)).asJson)
      .add("workspaceGrants", (_array(base, "workspaceGrants") :+ grant).asJson)
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(granted => granted.add("capabilities",
        granted("capabilities").get.mapArray(existing => (existing ++ programs.map(_.hcursor.get[Json]("capability").toOption.get)).distinct)))))))
  }
  private def _map_workspace(scenario: F.Scenario, slot: String, root: Path): Unit = {
    val config = _read(scenario.root.resolve(".textus/config.json"))
    val plan = parse(config.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get).toOption.get
    val mapped = plan.mapObject(fields => fields.add("workspaces", fields("workspaces").get.mapArray(_.map { workspace =>
      if (workspace.hcursor.get[String]("identity").toOption.contains(slot)) workspace.mapObject(_.add("root", root.toString.asJson))
      else workspace
    })))
    configure(scenario, Map("textus.subsystem.process-execution.plan" -> mapped.noSpaces))
  }
  private def _fresh(name: String, directoryonly: Boolean): F.Scenario = {
    _installed
    val scenario = fresh(name)
    val descriptor = scenario.root.resolve(".textus/assembly.yaml")
    val assembly = Files.readString(descriptor, UTF_8)
    assembly should include("      id: participant\n")
    Files.writeString(descriptor, assembly.replace("      id: participant\n", "      id: sm-workflow-local\n"), UTF_8)
    val slot = P.Slot("purpose-slot-1", Vector.empty,
      GoalPhaseStepAssessment.Policy("materialization-document", "documentation", "required", "off", "off", Vector.empty))
    configure(scenario, maintenance_parameters(review = true) ++ Map(
      "sm-workflow.repository-sync.dirty-purposes" -> P.Policy(Vector(slot, slot.copy(workspace = "purpose-slot-2")), Vector.empty).asJson.noSpaces,
      "textus.subsystem.process-execution.plan" -> _host_plan(scenario).noSpaces))
    val repository = _repository(scenario)
    Files.writeString(repository.resolve(_shared), _initial_text, UTF_8)
    git(scenario.root, "-C", repository.toString, "add", "--", _shared)
    git(scenario.root, "-C", repository.toString, "commit", "--no-verify", "--no-gpg-sign", "-m", "Document materialization fixture baseline")
    git(scenario.root, "-C", repository.toString, "push", "--no-verify", "--", scenario.host.target.endpoint, "HEAD:refs/heads/main")
    Files.writeString(repository.resolve(_shared), _final_text, UTF_8)
    Files.writeString(repository.resolve(_later), _later_text, UTF_8)
    Files.createDirectories(scenario.root.resolve("purpose-slot-1/occupied-child"))
    if (!directoryonly) Files.writeString(scenario.root.resolve("purpose-slot-1/occupied-marker.txt"), "Preserve old occupied destination.\n", UTF_8)
    scenario
  }
  private def _issue(scenario: F.Scenario): String = {
    val session = start(scenario)
    val issued = _success(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
    issued.submitOperation shouldBe Some("submitRepositoryPurposeResult")
    val request = purpose_inputs(scenario).head
    request.files.map(_.path).toSet shouldBe Set(_shared, _later)
    val later = request.files.find(_.path == _later).get
    (later.indexCode, later.worktreeCode, later.original, later.indexed, later.worktree) shouldBe
      (("?", "?", None, None, Some(_later_text)))
    val grouping = P.Result("resolved", "Two independently intended ordered document commits sharing one path",
      Vector(P.Group("first-document", "Retain the first explanation", "docs: retain first explanation", Vector.empty,
        Vector(P.Content(_shared, Some(_first_text))), Vector.empty, "The first explanation is independently meaningful"),
        P.Group("later-document", "Retain the later explanation and new document", "docs: retain later explanation", Vector("first-document"),
          Vector(P.Content(_shared, Some(_final_text)), P.Content(_later, Some(_later_text))), Vector.empty,
          "Later ownership includes the overlapping path and the originally untracked document")), Vector.empty)
    _success(submit_purpose(scenario, session, grouping)).business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
    session
  }
  private def _reopen(episode: Json): F.Scenario = {
    val root = Path.of(episode.hcursor.get[String]("root").toOption.get)
    val logs = Files.list(root.resolve("logs"))
    val starting = try math.max(1000, logs.filter(path => path.getFileName.toString.matches("[0-9]+\\.(stdout\\.json|stderr)"))
      .mapToInt(path => path.getFileName.toString.takeWhile(_.isDigit).toInt).max().orElse(999) + 1) finally logs.close()
    F.reopenInstalledRepository(root, take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(episode.hcursor.get[Json]("host").toOption.get)),
      episode.hcursor.get[GoalPhaseOrderedProgression.Plan]("plan").toOption.get, starting)
  }
  private def _target_json(result: F.Result): Json = {
    val description = _success(result).business[V.RepositorySyncValidationContinuationDescriptionResult]
    take(RepositorySyncValidationContinuation.anyTargetC(description.target))
    val target = parse(take(description.target.toJsonStringC)).toOption.get
    GoalPhaseNativeMetadata.normalizePublicJson(target).fold(fail(_), identity)
  }
  private def _direction(scenario: F.Scenario, session: String, described: F.Result, choice: String,
    originalfailure: Json): V.RepositorySyncValidationContinuationAdoptionInput = {
    val description = _success(described).business[V.RepositorySyncValidationContinuationDescriptionResult]
    val target = _target_json(described)
    val participant = target.hcursor.downField("context").get[String]("participantIdentity").toOption.get
    val directory = Files.createTempDirectory(scenario.root, "fixture-owned-materialization-human-")
    val reference = V.WorkflowArtifactReference(directory.getFileName.toString, "1")
    val rationale = "Fixture-owned independent Human direction for " + choice +
      ": the actual prior CLI returned and terminated; retain its original UNKNOWN effects. No real consumer consent is claimed."
    val answer = V.RepositorySyncValidationContinuationAnswer(choice, rationale, true, true)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(participant), None, None)
    _retain(directory.resolve("actual-prior-cli.json"), originalfailure)
    val preparation = V.WorkflowEvidencePreparationInput("RepositorySync", "validation-continuation",
      described.invocation.stdout.toUri.toASCIIString, save(directory, "answer.json", json(answer)),
      save(directory, "reference.json", json(reference)), save(directory, "execution.json", json(execution)),
      "normal-development-host", java.time.Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some(participant))
    val prepared = _success(scenario.prepareHostInput("prepareEvidence", take(new org.goldenport.record.io.RecordDecoder().json(json(preparation)))))
    scenario.selectPreparedImport(prepared)
    V.RepositorySyncValidationContinuationAdoptionInput(session, description.target, choice, rationale, reference, true, true, execution)
  }
  private def _review(scenario: F.Scenario, session: String, issued: F.Result, ordinal: Int): Unit = {
    val result = _success(issued).business[V.RepositorySyncAdvanceResult]
    result.submitOperation shouldBe Some("submitNativeMergeReview")
    result.review should not be empty
    result.review.get.selectedChecks shouldBe Vector.empty
    val reference = V.WorkflowArtifactReference("fixture-independent-materialization-review-" + ordinal, "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-independent-document-reviewer"), None, None)
    val verdict = V.RepositorySyncNativeMergeReviewResult("accept",
      "Fixture-owned independent review examined this actual document candidate, overlap and later untracked ownership", Vector.empty)
    val directory = Files.createTempDirectory(scenario.root, "fixture-materialization-review-")
    val preparation = V.WorkflowEvidencePreparationInput("RepositorySync", "review", issued.invocation.stdout.toUri.toASCIIString,
      save(directory, "answer.json", json(verdict)), save(directory, "reference.json", json(reference)),
      save(directory, "execution.json", json(execution)), "normal-development-host", java.time.Instant.now().toString,
      "entity:smworkflow.cli.host_evidence", None)
    val prepared = _success(scenario.prepareHostInput("prepareEvidence", take(new org.goldenport.record.io.RecordDecoder().json(json(preparation)))))
    scenario.selectPreparedImport(prepared)
    _success(scenario.repositoryCall("submitNativeMergeReview", Vector("--sessionId", session, "--result", json(verdict),
      "--resultReference", json(reference), "--executionEvidence", json(execution))))
      .business[V.RepositorySyncNativeMergeReviewSubmissionResult].accepted shouldBe true
  }
  private def _finish(scenario: F.Scenario, session: String, prepared: P.Progress,
    originalnative: Json, originalhead: String, oldintent: Boolean): Json = {
    When("a separate ordinary Advance materializes the retained first candidate and requests the required independent review")
    val first = _success(advance(scenario, session))
    val firststate = _state(scenario, session)
    Then("the first prepared candidate is unchanged and source projection keeps the later document untracked")
    _stages(scenario, session, "PurposePrepared").head.payload.value.purposes.get shouldBe prepared
    _stages(scenario, session, "PurposePreparing").size shouldBe 1
    _stages(scenario, session, "PurposePrepared").size shouldBe (if (oldintent) 2 else 1)
    _projection(scenario)
    git(scenario.root, "-C", _repository(scenario).toString, "status", "--porcelain=v1", "--", _later) shouldBe ("?? " + _later)
    val repeatfirst = _success(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
    repeatfirst.issuedWorkContext shouldBe first.business[V.RepositorySyncAdvanceResult].issuedWorkContext
    _state(scenario, session) shouldBe firststate
    _review(scenario, session, first, 1)
    When("ordinary Advance installs the first purpose and materializes the separate later purpose")
    val second = _success(advance(scenario, session))
    val firstinstalled = _stages(scenario, session, "PurposeInstalled").head.payload.value.purposes.get
    val firstcommit = firstinstalled.commits.last
    Then("the installed first commit is exactly the original prepared commit with the original parent")
    firstcommit shouldBe prepared.candidate.get.commit
    git(scenario.root, "-C", _repository(scenario).toString, "rev-parse", "HEAD") shouldBe firstcommit
    git(scenario.root, "-C", _repository(scenario).toString, "rev-parse", "HEAD^") shouldBe originalhead
    git(scenario.root, "-C", _repository(scenario).toString, "show", "HEAD:" + _shared) shouldBe _first_text.trim
    git(scenario.root, "-C", _repository(scenario).toString, "ls-tree", "--name-only", "HEAD", "--", _later) shouldBe ""
    _projection(scenario)
    git(scenario.root, "-C", _repository(scenario).toString, "status", "--porcelain=v1", "--", _later) shouldBe ("?? " + _later)
    val secondstate = _state(scenario, session)
    val repeatsecond = _success(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
    repeatsecond.issuedWorkContext shouldBe second.business[V.RepositorySyncAdvanceResult].issuedWorkContext
    _state(scenario, session) shouldBe secondstate
    _review(scenario, session, second, 2)
    When("the later purpose receives independent review and ordinary Advance finishes local bare-remote convergence")
    val completed = _success(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
    val terminalstate = _state(scenario, session)
    val head = terminalstate.hcursor.get[String]("head").toOption.get
    Then("the original Session completes with exactly two ordered purpose commits and one original capture")
    completed.kind shouldBe "Terminal"
    current(scenario, session).lifecycle shouldBe Lifecycle.Completed
    terminalstate.hcursor.get[String]("status").toOption shouldBe Some("")
    terminalstate.hcursor.get[String]("remoteTip").toOption shouldBe Some(head)
    git(scenario.root, "-C", _repository(scenario).toString, "rev-parse", "HEAD^") shouldBe firstcommit
    git(scenario.root, "-C", _repository(scenario).toString, "rev-list", "--count", originalhead + "..HEAD") shouldBe "2"
    git(scenario.root, "-C", _repository(scenario).toString, "show", "HEAD:" + _shared) shouldBe _final_text.trim
    git(scenario.root, "-C", _repository(scenario).toString, "show", "HEAD:" + _later) shouldBe _later_text.trim
    _projection(scenario)
    _stages(scenario, session, "PurposeInventory").size shouldBe 1
    purpose_inputs(scenario).size shouldBe 1
    _stages(scenario, session, "PurposePreparing").size shouldBe 2
    _stages(scenario, session, "PurposePrepared").size shouldBe (if (oldintent) 3 else 2)
    _stages(scenario, session, "PurposeMaterializing").size shouldBe (if (oldintent) 3 else 2)
    // PurposeMaterialized includes the structural result as well as materialization.
    _stages(scenario, session, "PurposeMaterialized").count(_.payload.value.purposes.get.selection.isEmpty) shouldBe 2
    _stages(scenario, session, "PurposeInstalling").size shouldBe 2
    _stages(scenario, session, "PurposeInstalled").size shouldBe 2
    current(scenario, session).history.count(_.currentProgression.contains(ProgressionReference("sync.PurposeGrouped"))) shouldBe 1
    val allnative = _native(scenario, session).asArray.get
    originalnative.asArray.get.foreach(value => allnative should contain(value))
    Files.isDirectory(scenario.root.resolve("purpose-slot-1/occupied-child")) shouldBe true
    Files.exists(scenario.root.resolve("sbt-invocations.txt")) shouldBe false
    When("ordinary collection repeats after Terminal")
    _success(advance(scenario, session)).business[V.RepositorySyncAdvanceResult] shouldBe completed
    Then("collection has no additional capture materialization review or commit effect")
    _state(scenario, session) shouldBe terminalstate
    Json.obj("session" -> session.asJson, "root" -> scenario.root.toString.asJson,
      "installedCommand" -> _installed.toString.asJson, "firstPurposeCommit" -> firstcommit.asJson,
      "secondPurposeCommit" -> head.asJson, "terminal" -> parse(json(completed)).toOption.get,
      "journalRecording" -> parse(json(completed)).toOption.get.hcursor.downField("journalRecording").focus.getOrElse(Json.Null),
      "originalPreparedProgress" -> P.repairProgressJson(prepared), "finalState" -> terminalstate,
      "originalUnknownPreserved" -> oldintent.asJson)
  }

  "ID12 installed purpose materialization" should {
    "old installed capture" which {
      if (_stage == "before") {
        "retain the real occupied-destination UNKNOWN and prepared candidate without replay" in {
          Given("the explicitly selected old installed product and an outside-Git retained episode destination")
          _installed
          val destination = _episode_path()
          val collectonly = sys.props.get("smworkflow.spec.purposeMaterializationCollectOnly").contains("true")
          if (collectonly) {
            val episode = _read(destination)
            val scenario = _reopen(episode)
            val session = episode.hcursor.get[String]("session").toOption.get
            When("only the retained old failure logs and native state are collected")
            val snapshot = _state(scenario, session)
            val failure = episode.hcursor.get[Json]("oldFailure").toOption.get
            val logs = Files.readString(Path.of(failure.hcursor.get[String]("stdout").toOption.get)) +
              Files.readString(Path.of(failure.hcursor.get[String]("stderr").toOption.get))
            Then("the actual 409 and original resultless intent survive with no new invocation")
            logs should include("409")
            snapshot shouldBe episode.hcursor.get[Json]("beforeState").toOption.get
            scenario.invocations shouldBe Vector.empty
          } else {
            Files.exists(destination) shouldBe false
            val scenario = _fresh("purpose-materialization-old", directoryonly = false)
            val originalconfig = Files.readString(scenario.root.resolve(".textus/config.json"), UTF_8)
            When("one accepted grouping is advanced against the genuinely occupied fixed destination")
            val session = _issue(scenario)
            val failed = advance(scenario, session)
            Then("the actual old CLI returns 409 after retaining preparation and the materialize intent without a provider result")
            failed.exitCode should not be 0
            failed.invocation.argv.head shouldBe _installed.toString
            failed.invocation.logs should include("409")
            val process = ProcessHandle.of(failed.invocation.pid)
            (process.isPresent && process.get.isAlive) shouldBe false
            current(scenario, session).currentProgression shouldBe Some(ProgressionReference("sync.PurposeMaterializing"))
            current(scenario, session).suspension shouldBe None
            _stages(scenario, session, "PurposePrepared").size shouldBe 1
            _stages(scenario, session, "PurposeMaterializing").size shouldBe 1
            _stages(scenario, session, "PurposeMaterialized") shouldBe empty
            _stages(scenario, session, "PurposeUnfinished") shouldBe empty
            val prepared = _stages(scenario, session, "PurposePrepared").head.payload.value.purposes.get
            val intent = _stages(scenario, session, "PurposeMaterializing").head
            intent.payload.value.purposes.get shouldBe prepared
            intent.next.revision shouldBe _stages(scenario, session, "PurposePrepared").head.next.revision.next
            prepared.effects.size shouldBe 1
            prepared.candidate should not be empty
            val originalnative = _native(scenario, session)
            val initialstate = _state(scenario, session)
            When("ordinary Advance collects the resultless intent without retrying it")
            val collection = _success(advance(scenario, session))
            val waiting = collection.business[V.RepositorySyncAdvanceResult]
            Then("UNKNOWN remains a passive Wait without inventing a failed process receipt")
            waiting.kind shouldBe "Wait"
            waiting.condition shouldBe Some("purpose-effect-unknown")
            _state(scenario, session) shouldBe initialstate
            When("only normal workspace mappings are corrected to separate fresh empty directories")
            val freshfirst = Files.createDirectory(scenario.root.resolve("fresh-purpose-slot-1"))
            val freshsecond = Files.createDirectory(scenario.root.resolve("fresh-purpose-slot-2"))
            _map_workspace(scenario, "purpose-slot-1", freshfirst)
            _map_workspace(scenario, "purpose-slot-2", freshsecond)
            val beforestate = _state(scenario, session)
            Then("the original occupied workspace and exact old native history remain untouched")
            beforestate shouldBe initialstate
            Files.readString(scenario.root.resolve("purpose-slot-1/occupied-marker.txt"), UTF_8) shouldBe "Preserve old occupied destination.\n"
            _retain(destination, Json.obj("schemaVersion" -> "purpose-materialization-installed-episode.v1".asJson,
              "root" -> scenario.root.toString.asJson, "host" -> ThreeWorkflowRuntimeParameters.encode(scenario.host),
              "plan" -> scenario.plan.asJson, "session" -> session.asJson, "oldInstalledCommand" -> _installed.toString.asJson,
              "originalHostConfiguration" -> originalconfig.asJson,
              "correctedHostConfiguration" -> Files.readString(scenario.root.resolve(".textus/config.json"), UTF_8).asJson,
              "oldFailure" -> _invocation(failed), "oldFailureLogs" -> failed.invocation.logs.asJson,
              "originalNativeReceipts" -> originalnative, "preparedProgress" -> P.repairProgressJson(prepared),
              "beforeState" -> beforestate, "unknownCollection" -> _invocation(collection),
              "priorCliTerminated" -> true.asJson, "originalWorkerEffects" -> "UNKNOWN".asJson))
          }
        }
      }
    }
    "same Session after publication" which {
      if (_stage == "after") {
        "adopt exact fixture Human direction and finish both original purposes without repreparation" in {
          Given("the retained old real failure and the new explicitly selected installed product over the same physical Session")
          _installed
          val episode = _read(_episode_path())
          val scenario = _reopen(episode)
          val session = episode.hcursor.get[String]("session").toOption.get
          val originalnative = episode.hcursor.get[Json]("originalNativeReceipts").toOption.get
          val beforestate = episode.hcursor.get[Json]("beforeState").toOption.get
          _state(scenario, session) shouldBe beforestate
          Files.readString(scenario.root.resolve(".textus/config.json"), UTF_8) shouldBe episode.hcursor.get[String]("correctedHostConfiguration").toOption.get
          val prepared = _stages(scenario, session, "PurposePrepared").head.payload.value.purposes.get
          P.repairProgressJson(prepared) shouldBe episode.hcursor.get[Json]("preparedProgress").toOption.get
          episode.hcursor.get[Boolean]("priorCliTerminated").toOption shouldBe Some(true)
          val oldfailure = episode.hcursor.get[Json]("oldFailure").toOption.get
          val oldpid = oldfailure.hcursor.get[Long]("pid").toOption.get
          val process = ProcessHandle.of(oldpid)
          (process.isPresent && process.get.isAlive) shouldBe false
          When("the new public description observes the old adjacent preparation and materialization intent")
          val described = _success(describe(scenario, session))
          val description = described.business[V.RepositorySyncValidationContinuationDescriptionResult]
          val target = _target_json(described)
          Then("the generic route describes only the explicit new materialization policy and remains passive")
          description.outcome shouldBe "adoption-required"
          description.choices shouldBe Vector("AuthorizeNewMaterialization", "Defer")
          target.hcursor.get[String]("continuationPolicy").toOption shouldBe Some(_policy)
          target.hcursor.get[String]("sessionId").toOption shouldBe Some(session)
          _target_json(_success(describe(scenario, session))) shouldBe target
          _state(scenario, session) shouldBe beforestate
          When("an independent fixture Human defers the exact proposed continuation")
          val deferred = _direction(scenario, session, described, "Defer", oldfailure)
          val deferresult = _success(adopt(scenario, deferred)).business[V.RepositorySyncValidationContinuationAdoptionResult]
          Then("Defer appends no event or effect")
          deferresult.outcome shouldBe "deferred"
          _state(scenario, session) shouldBe beforestate
          val authorized = _direction(scenario, session, described, "AuthorizeNewMaterialization", oldfailure)
          val foreigntarget = GoalPhaseNativeMetadata.record(target.mapObject(_.add("sessionId", (session + "-foreign").asJson)))
          val unknownpolicy = GoalPhaseNativeMetadata.record(target.mapObject(_.add("continuationPolicy", (_policy + "-unknown").asJson)))
          When("authorization omits lifecycle acceptance or crosses its target participant reference or Session")
          Vector(authorized.copy(priorExecutionEnded = false), authorized.copy(acceptOriginalUncertainty = false),
            authorized.copy(sessionId = session + "-foreign"), authorized.copy(target = foreigntarget), authorized.copy(target = unknownpolicy),
            authorized.copy(reference = authorized.reference.copy(identity = "foreign-reference")),
            authorized.copy(executionEvidence = authorized.executionEvidence.copy(workerIdentity = Some("foreign-worker"))),
            authorized.copy(executionEvidence = authorized.executionEvidence.copy(references = Vector.empty))).foreach { input =>
            adopt(scenario, input).exitCode should not be 0
            _state(scenario, session) shouldBe beforestate
          }
          When("the mapped destination becomes occupied after description")
          val changed = scenario.root.resolve("fresh-purpose-slot-1/changed-after-description")
          Files.createDirectory(changed)
          try {
            adopt(scenario, authorized).exitCode should not be 0
            _state(scenario, session) shouldBe beforestate
          } finally Files.delete(changed)
          When("the exact independent direction authorizes one new materialization")
          val adoption = _success(adopt(scenario, authorized)).business[V.RepositorySyncValidationContinuationAdoptionResult]
          val admitted = current(scenario, session)
          Then("adoption appends one declared transition to PurposePrepared with the complete original progress")
          adoption.outcome shouldBe "adopted"
          admitted.currentProgression shouldBe Some(ProgressionReference("sync.PurposePrepared"))
          val old = _stages(scenario, session, "PurposeMaterializing").head.next
          admitted.revision shouldBe old.revision.next
          admitted.history.take(old.history.size) shouldBe old.history
          val admittedreceipts = _stages(scenario, session, "PurposePrepared")
          admittedreceipts.size shouldBe 2
          admittedreceipts.last.payload.value.purposes.get shouldBe prepared
          val admittedstate = _state(scenario, session)
          _success(adopt(scenario, authorized)).business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "already-adopted"
          _state(scenario, session) shouldBe admittedstate
          // Adoption is a PurposePrepared event; only one actual prepare-intent
          // and its original prepared receipt exist before ordinary continuation.
          val evidence = _finish(scenario, session, prepared, originalnative,
            beforestate.hcursor.get[String]("head").toOption.get, oldintent = true)
          val completedstate = _state(scenario, session)
          When("the identical adopted input is collected after Terminal")
          _success(adopt(scenario, authorized)).business[V.RepositorySyncValidationContinuationAdoptionResult].outcome shouldBe "already-adopted"
          Then("terminal idempotence preserves all original UNKNOWN history and the two completed purpose commits")
          _state(scenario, session) shouldBe completedstate
          Files.readString(scenario.root.resolve("purpose-slot-1/occupied-marker.txt"), UTF_8) shouldBe "Preserve old occupied destination.\n"
          _retain(_episode_path("materialization.result"), evidence.deepMerge(Json.obj("originalEpisode" -> episode,
            "publicTarget" -> target, "adoptedInput" -> parse(json(authorized)).toOption.get,
            "admittedState" -> admittedstate, "oldIntentReceipt" -> originalnative.asArray.get.last)))
        }
      }
    }
    "future ordinary preparation" which {
      if (_stage == "future") {
        "wait before intent for a nonempty directory or source overlap and resume with an empty registered target" in {
          Given("the new installed product and a separate fresh fixture whose selected destination contains only a directory")
          _installed
          val destination = _episode_path("future")
          Files.exists(destination) shouldBe false
          val scenario = _fresh("purpose-materialization-preflight", directoryonly = true)
          val session = _issue(scenario)
          When("ordinary Advance prepares the first candidate and preflights its nonempty destination")
          val first = _success(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
          val waitingstate = _state(scenario, session)
          Then("the runtime honestly waits in PurposePrepared before any materialization intent or provider receipt")
          first.kind shouldBe "Wait"
          first.condition shouldBe Some("purpose-materialization-preflight")
          first.message.trim should not be empty
          current(scenario, session).currentProgression shouldBe Some(ProgressionReference("sync.PurposePrepared"))
          _stages(scenario, session, "PurposeMaterializing") shouldBe empty
          _stages(scenario, session, "PurposeMaterialized") shouldBe empty
          val prepared = _stages(scenario, session, "PurposePrepared").head.payload.value.purposes.get
          val originalnative = _native(scenario, session)
          When("ordinary collection repeats with the same occupied directory")
          _success(advance(scenario, session)).business[V.RepositorySyncAdvanceResult].condition shouldBe Some("purpose-materialization-preflight")
          Then("preflight collection creates no new intent snapshot or result")
          _state(scenario, session) shouldBe waitingstate
          When("the normal host mapping selects the actual source repository instead")
          _map_workspace(scenario, "purpose-slot-1", _repository(scenario))
          val overlap = _success(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
          Then("source overlap is also a passive preflight refusal with the original preparation preserved")
          overlap.condition shouldBe Some("purpose-materialization-preflight")
          _state(scenario, session) shouldBe waitingstate
          When("normal host mappings select separate ordinary fresh empty targets")
          _map_workspace(scenario, "purpose-slot-1", Files.createDirectory(scenario.root.resolve("future-empty-slot-1")))
          _map_workspace(scenario, "purpose-slot-2", Files.createDirectory(scenario.root.resolve("future-empty-slot-2")))
          _retain(destination, Json.obj("root" -> scenario.root.toString.asJson, "session" -> session.asJson,
            "host" -> ThreeWorkflowRuntimeParameters.encode(scenario.host), "plan" -> scenario.plan.asJson,
            "preflightState" -> waitingstate, "directoryWait" -> parse(json(first)).toOption.get,
            "sourceOverlapWait" -> parse(json(overlap)).toOption.get))
          val evidence = _finish(scenario, session, prepared, originalnative,
            waitingstate.hcursor.get[String]("head").toOption.get, oldintent = false)
          Then("ordinary continuation finishes without requiring Human recovery or classifying any uncertain invocation as unstarted")
          _stages(scenario, session, "PurposeUnfinished") shouldBe empty
          _retain(_episode_path("future.result"), evidence)
        }
      }
    }
  }
}
