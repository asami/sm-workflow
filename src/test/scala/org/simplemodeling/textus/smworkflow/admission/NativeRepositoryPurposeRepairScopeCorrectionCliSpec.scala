package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowWorkOrderJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation
import org.goldenport.convert.ValueReader
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import RepositorySyncPurpose as P
import RepositorySyncRepairScopeCorrection as N
import RepositorySyncNativeMergeRepair as M
import SplitPhaseExplicitPartitionFixture.take
import RepositorySyncRepairInventory.given

/** Explicitly selected installed-product phases, one test per invocation.
  * This tiny repository is authored here; no producer checkout is copied.
  * Lifecycle answers, proposals and independent review are fixture inputs and
  * never claim real Human acceptance. Native datastore access is read only.
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryPurposeRepairScopeCorrectionCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeRepositorySelectionCorrectionFixture {
  private val _phase = sys.props.getOrElse("smworkflow.spec.purposeRepairPhase",
    fail("Select -Dsmworkflow.spec.purposeRepairPhase=capture|continue|finish|completion|ownership-capture|ownership-continue|prepare-capture|prepare-continue"))
  require(Set("capture", "continue", "finish", "completion", "ownership-capture", "ownership-continue", "prepare-capture", "prepare-continue")(_phase),
    "Select exactly one Purpose repair phase")
  private val _baseline = "src/test/scala/fixture/BaselineSpec.scala"
  private val _later = "src/test/scala/fixture/FutureSpec.scala"
  private val _future = "docs/future.md"
  private val _baseline_old = _test("BaselineSpec", "initial\n")
  private val _baseline_new = _test("BaselineSpec", "expected\n")
  private val _later_old = _test("FutureSpec", "expected\n")
  private val _later_new = _later_old + "\n// Independently intended later Purpose test explanation.\n"
  private val _future_new = "Independent future document text\n"
  private val _ownership_comment = "\n// Independently intended later Purpose baseline explanation.\n"
  private val _ownership_old = if (_phase.startsWith("prepare-"))
    _baseline_old.replace("actual shouldBe \"initial\\n\"", "actual.trim shouldBe \"initial\"")
    else _baseline_old + _ownership_comment
  private val _ownership_new = if (_phase.startsWith("prepare-"))
    _baseline_new.replace("actual shouldBe \"expected\\n\"", "actual.trim shouldBe \"expected\"")
    else _baseline_new + _ownership_comment
  private val _ownership_conflict = _test("BaselineSpec", "future-only\n")
  private def _episode_path: Path = {
    val property = if (_phase.startsWith("prepare-")) "smworkflow.spec.purposePreparationEpisode"
      else if (_phase.startsWith("ownership-")) "smworkflow.spec.purposeOwnershipEpisode" else "smworkflow.spec.purposeRepairEpisode"
    val value = Path.of(sys.props.getOrElse(property, fail("Select -D" + property + "=<absolute episodeJSON>")))
    require(value.isAbsolute && value.normalize == value && Files.isDirectory(value.getParent))
    value
  }
  private final case class Episode(root: String, host: Json, plan: Json, session: String,
    originalRecord: String, originalWorkIdentity: String, originalRequest: M.Request, progress: P.Progress,
    snapshot: Json, formal: Vector[(String, RepositorySyncNativeMergeValidation.Receipt)],
    oldDescriptionExit: Int, oldDescriptionLogs: String, oldDescriptionStdout: String, installedCommand: String)
  private given Encoder[Episode] = io.circe.generic.semiauto.deriveEncoder[Episode]
  private given Decoder[Episode] = io.circe.generic.semiauto.deriveDecoder[Episode]
  private final case class OwnershipEpisode(resolved: Episode, decision: Episode)
  private final case class OwnershipResume(decisionSession: String, target: Json, issued: Json, snapshot: Json,
    acceptedDecision: Option[Json] = None)
  private given Decoder[OwnershipResume] = io.circe.generic.semiauto.deriveDecoder[OwnershipResume]
  private def _ownership_resume: Option[OwnershipResume] = sys.props.get("smworkflow.spec.purposeOwnershipResume").map { raw =>
    val path = Path.of(raw)
    require(path.isAbsolute && path.normalize == path && Files.isRegularFile(path))
    parse(Files.readString(path)).toOption.get.as[OwnershipResume].toOption.get
  }

  private def _test(name: String, expected: String): String = """package fixture
    |class __NAME__ extends org.scalatest.wordspec.AnyWordSpec
    |    with org.scalatest.GivenWhenThen with org.scalatest.matchers.should.Matchers {
    |  "the tracked baseline product expectation" should {
    |    "match the actual materialized input" in {
    |      Given("the genuine Purpose candidate workspace")
    |      val root = java.nio.file.Paths.get("").toAbsolutePath.normalize
    |      When("the real SBT Test reads the complete input")
    |      val actual = java.nio.file.Files.readString(root.resolve("value.txt"))
    |      Then("the baseline expectation matches the proposed input")
    |      actual shouldBe __EXPECTED__
    |    }
    |  }
    |}
    |""".stripMargin.replace("__NAME__", name).replace("__EXPECTED__", Json.fromString(expected).noSpaces)

  private def _array(value: Json, key: String): Vector[Json] = value.hcursor.get[Vector[Json]](key).toOption.get
  private def _parameters(focused: Boolean = false): Map[String, String] = selectionParameters(corrected = focused) +
    ("sm-workflow.repository-sync.dirty-purposes" -> P.Policy((1 to 3).toVector.map(ordinal =>
      P.Slot("purpose-slot-" + ordinal, originalChecks ++ Option.when(focused)(appendedCheck),
        GoalPhaseStepAssessment.Policy("purpose-repair-" + ordinal, "code", "required", "off", "off", Vector.empty))), Vector.empty).asJson.noSpaces)
  private def _plan(scenario: F.Scenario, focused: Boolean = false): Json = {
    val selected = selectionPlan(scenario, broken = false, corrected = focused)
    val base = if (!focused) selected else selected.mapObject(fields => fields.add("sbtPrograms",
      fields("sbtPrograms").get.mapArray(_.map(program =>
        if (program.hcursor.get[String]("operation").toOption.contains("test-only"))
          program.mapObject(_.add("testClasses", Vector("fixture.BaselineSpec", "fixture.FutureSpec").asJson))
        else program))))
    val slot = "purpose-slot-1"
    def _clone_program_(value: Json, ordinal: Int): Json = value.mapObject(fields => fields
      .add("target", ("purpose-slot-" + ordinal).asJson)
      .add("identity", (fields("identity").get.asString.get + "-slot-" + ordinal).asJson)
      .add("capability", (fields("capability").get.asString.get + "-slot-" + ordinal).asJson))
    val addedgit = (2 to 3).toVector.flatMap(ordinal => _array(base, "gitPrograms")
      .filter(_.hcursor.get[String]("target").toOption.contains(slot)).map(_clone_program_(_, ordinal)))
    val addedsbt = (2 to 3).toVector.flatMap(ordinal => _array(base, "sbtPrograms").map(_clone_program_(_, ordinal)))
    val addedworkspaces = (2 to 3).toVector.map { ordinal =>
      val name = "purpose-slot-" + ordinal
      Files.createDirectories(scenario.root.resolve(name))
      Json.obj("identity" -> name.asJson, "root" -> scenario.root.resolve(name).toString.asJson)
    }
    val addedgrants = (2 to 3).toVector.map(ordinal => _array(base, "workspaceGrants").find(
      _.hcursor.get[String]("workspaceId").toOption.contains(slot)).get.mapObject(_.add("workspaceId", ("purpose-slot-" + ordinal).asJson)))
    base.mapObject(fields => fields.add("gitPrograms", (_array(base, "gitPrograms") ++ addedgit).asJson)
      .add("sbtPrograms", (_array(base, "sbtPrograms") ++ addedsbt).asJson)
      .add("workspaces", (_array(base, "workspaces") ++ addedworkspaces).asJson)
      .add("workspaceGrants", (_array(base, "workspaceGrants") ++ addedgrants).asJson)
      .add("grants", fields("grants").get.mapArray(_.map(_.mapObject(grant => grant.add("capabilities",
        grant("capabilities").get.mapArray(existing => (existing ++ (addedgit ++ addedsbt)
          .map(_.hcursor.get[Json]("capability").toOption.get)).distinct)))))))
  }
  private def _repository(scenario: F.Scenario): Path = Path.of(scenario.host.target.root)
  private def _invocations(scenario: F.Scenario): Vector[String] = {
    val path = scenario.root.resolve("sbt-invocations.txt")
    if (Files.exists(path)) Files.readString(path).linesIterator.toVector else Vector.empty
  }
  private def _state(scenario: F.Scenario, session: String): Json = Json.obj(
    "record" -> take(WorkflowInstanceRecordJsonV1.encodeC(current(scenario, session))).asJson,
    "head" -> git(scenario.root, "-C", _repository(scenario).toString, "rev-parse", "HEAD").asJson,
    "indexTree" -> git(scenario.root, "-C", _repository(scenario).toString, "write-tree").asJson,
    "status" -> git(scenario.root, "-C", _repository(scenario).toString, "status", "--porcelain").asJson,
    "diff" -> git(scenario.root, "-C", _repository(scenario).toString, "diff", "HEAD", "--").asJson,
    "checks" -> formalReceipts(scenario, session).asJson,
    "invocations" -> _invocations(scenario).asJson,
    "nativeReceipts" -> receipts(scenario, session).map { receipt =>
      val payload = receipt.payload.value
      val observed = payload.asJson.mapObject(fields =>
        if (payload.purposePreparation.isEmpty) fields.remove("purposePreparation") else fields)
      receipt.reference.identity -> observed.noSpaces
    }.sortBy(_._1).asJson)
  private def _work(scenario: F.Scenario, identity: String): M.Request = {
    val values = stored(scenario).flatMap(raw => WorkflowWorkOrderJsonV1.decodeC(raw, M.inputCodec).toOption)
      .flatMap(_.current match {
        case order: WorkflowContinuation.WorkOrder[M.Request] if order.request.continuationId.value == identity => order.request.input.map(_.value)
        case _ => None
      })
    values.size shouldBe 1
    values.head
  }
  private def _failed_progress(scenario: F.Scenario, session: String): P.Progress = receipts(scenario, session)
    .filter(_.payload.value.stage == "PurposeRepairRequired").maxBy(_.next.revision.value).payload.value.purposes.get
  private def _prepare(scenario: F.Scenario, session: String, paths: Vector[String]): F.Result = scenario.repositoryCall(N.prepareOperation,
    Vector("--input", N.Preparation(session, paths, "The actual Test assertion requires correcting this existing baseline test").asJson.noSpaces))
  private def _target(result: F.Result): N.Target = take(N.targetC(successful(result)
    .business[V.RepositorySyncRepairScopeCorrectionDescriptionResult].target))
  private def _adopt(scenario: F.Scenario, input: V.RepositorySyncRepairScopeCorrectionAdoptionInput): F.Result =
    scenario.repositoryCall(N.adoptionOperation, Vector("--input", json(input)))
  private def _direction(scenario: F.Scenario, session: String, described: F.Result): V.RepositorySyncRepairScopeCorrectionAdoptionInput = {
    val description = successful(described).business[V.RepositorySyncRepairScopeCorrectionDescriptionResult]
    val target = take(N.targetC(description.target))
    val directory = Files.createTempDirectory(scenario.root, "fixture-purpose-lifecycle-")
    val reference = V.WorkflowArtifactReference(directory.getFileName.toString, "1")
    val answer = V.RepositorySyncRepairScopeCorrectionAnswer(N.lifecycleChoices.head, Vector.empty,
      "Fixture-authored independent lifecycle direction: prior CLI returned, worker ended, accept original UNKNOWN effects; no real Human assertion", true, true)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(target.context.participantIdentity), None, None)
    val preparation = V.WorkflowEvidencePreparationInput("RepositorySyncRepairScopeCorrection", "adoption",
      described.invocation.stdout.toUri.toASCIIString, save(directory, "answer.json", json(answer)),
      save(directory, "reference.json", json(reference)), save(directory, "execution.json", json(execution)),
      "normal-development-host", java.time.Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some(target.context.participantIdentity))
    val prepared = scenario.prepareHostInput("prepareEvidence", take(new org.goldenport.record.io.RecordDecoder().json(json(preparation))))
    successful(prepared)
    scenario.selectPreparedImport(prepared)
    V.RepositorySyncRepairScopeCorrectionAdoptionInput(session, description.target, answer.choice, Vector.empty,
      answer.rationale, reference, true, true, execution)
  }
  private def _review(scenario: F.Scenario, session: String, described: F.Result, suffix: String): Unit = {
    val reference = V.WorkflowArtifactReference("fixture-independent-purpose-review-" + suffix, "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-independent-reviewer"), None, None)
    val verdict = V.RepositorySyncNativeMergeReviewResult("accept",
      "Independent fixture reviewer examined this actual fresh candidate and future ownership; no live reviewer acceptance is claimed", Vector.empty)
    val preparation = V.WorkflowEvidencePreparationInput("RepositorySync", "review", described.invocation.stdout.toUri.toASCIIString,
      save(scenario.root, "review-" + suffix + ".json", json(verdict)), save(scenario.root, "review-reference-" + suffix + ".json", json(reference)),
      save(scenario.root, "review-execution-" + suffix + ".json", json(execution)), "normal-development-host", java.time.Instant.now().toString,
      "entity:smworkflow.cli.host_evidence", None)
    val prepared = scenario.prepareHostInput("prepareEvidence", take(new org.goldenport.record.io.RecordDecoder().json(json(preparation))))
    successful(prepared)
    scenario.selectPreparedImport(prepared)
    successful(scenario.repositoryCall("submitNativeMergeReview", Vector("--sessionId", session, "--result", json(verdict),
      "--resultReference", json(reference), "--executionEvidence", json(execution))))
      .business[V.RepositorySyncNativeMergeReviewSubmissionResult].accepted shouldBe true
  }
  private def _reopen(episode: Episode): F.Scenario = {
    val root = Path.of(episode.root)
    val stream = Files.list(root.resolve("logs"))
    val ordinal = try math.max(1000, stream.filter(path => path.getFileName.toString.matches("[0-9]+\\.(stdout\\.json|stderr)"))
      .mapToInt(path => path.getFileName.toString.takeWhile(_.isDigit).toInt).max().orElse(999) + 1) finally stream.close()
    F.reopenInstalledRepository(root, take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(episode.host)),
      episode.plan.as[GoalPhaseOrderedProgression.Plan].toOption.get, ordinal)
  }

  "ID06 installed same-Session Purpose repair scope" should {
    if (_phase == "capture") {
      "capture legacy failed purpose repair" in {
        _capture()
      }
    }
    if (_phase == "continue") {
      "continue retained failed purpose repair" in {
        val episode = parse(Files.readString(_episode_path)).toOption.get.as[Episode].toOption.get
        _continue(episode)
      }
    }
    if (_phase == "finish") {
      "finish retained accepted purpose repair" in {
        Given("the same fixture episode after accepted repair submission and the retained installed grouped Advance failure")
        installed()
        val episode = parse(Files.readString(_episode_path)).toOption.get.as[Episode].toOption.get
        val scenario = _reopen(episode)
        When("the fixture reads the retained accepted repair frontier and original failure evidence")
        val accepted = current(scenario, episode.session)
        val qualification = _state(scenario, episode.session)
        Then("only the exact accepted repair frontier with unchanged original work and failed checks can resume")
        accepted.currentProgression shouldBe Some(ProgressionReference("sync.RepairProposalAdmitted"))
        _work(scenario, episode.originalWorkIdentity) shouldBe episode.originalRequest
        _failed_progress(scenario, episode.session) shouldBe episode.progress
        formalReceipts(scenario, episode.session) shouldBe episode.formal
        _invocations(scenario).size shouldBe 2
        episode.oldDescriptionExit should not be 0
        episode.oldDescriptionLogs should include("409")
        Files.isRegularFile(Path.of(episode.oldDescriptionStdout)) shouldBe true
        _finish(scenario, episode, Json.obj(
          "qualificationPhase" -> "finish".asJson,
          "resumedFrontier" -> "sync.RepairProposalAdmitted".asJson,
          "retainedPriorInstalledFailure" -> "actual pending merge history source is absent".asJson,
          "successfulPrefixReexecuted" -> false.asJson,
          "acceptedRepairState" -> qualification))
      }
    }
    if (_phase == "completion") {
      "complete one isolated current-installed purpose repair control" in {
        Given("a separately selected retained legacy episode and a new completion-control episode destination")
        val original = Path.of(sys.props.getOrElse("smworkflow.spec.purposeRepairOriginalEpisode",
          fail("Select -Dsmworkflow.spec.purposeRepairOriginalEpisode=<retained original episodeJSON>")))
        require(original.isAbsolute && original.normalize == original && Files.isRegularFile(original))
        When("the fixture checks the new episode destination before creating any repository or Session")
        val destination = _episode_path
        Then("the completion control has distinct absent episode and result paths")
        destination should not be original
        Files.exists(destination) shouldBe false
        Files.exists(destination.resolveSibling(destination.getFileName.toString + ".result.json")) shouldBe false
        val episode = _capture(focused = true, legacy = false)
        _continue(episode, legacy = false)
      }
    }
  }
  "ID07 installed same-Session Purpose ownership continuation" should {
    if (_phase == "ownership-capture") {
      "capture old ownership refusal for resolvable and conflicting later intents" in {
        Given("a unique absent ID07 episode and two authored tiny repositories under the actual old installed runtime")
        val destination = _episode_path
        val resolvedpath = destination.resolveSibling(destination.getFileName.toString + ".resolved.json")
        val decisionpath = destination.resolveSibling(destination.getFileName.toString + ".decision.json")
        Vector(destination, resolvedpath, decisionpath, destination.resolveSibling(destination.getFileName.toString + ".result.json"),
          destination.resolveSibling(destination.getFileName.toString + ".decision.result.json"),
          destination.resolveSibling(destination.getFileName.toString + ".resolved.result.json"))
          .foreach(path => Files.exists(path) shouldBe false)
        When("each real failed baseline candidate is also owned by its later planned Purpose")
        val resolved = _capture(focused = true, ownership = true, destination = resolvedpath)
        val decision = _capture(focused = true, ownership = true, conflict = true, destination = decisionpath)
        Then("the original Sessions and actual old prepare ownership refusals are retained once for new-product continuation")
        resolved.session should not be decision.session
        Files.writeString(destination, OwnershipEpisode(resolved, decision).asJson.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
      }
    }
    if (_phase == "ownership-continue") {
      "continue exact old ownership episodes with explicit integration or truthful decision wait" in {
        Given("the same retained ID07 repositories and Sessions after normal producer publication")
        val episode = parse(Files.readString(_episode_path)).toOption.get.as[OwnershipEpisode].toOption.get
        val resume = _ownership_resume
        resume.foreach(_.decisionSession shouldBe episode.decision.session)
        Files.exists(_episode_path.resolveSibling(_episode_path.getFileName.toString + ".result.json")) shouldBe false
        When("the conflicting later assertion cannot express both intended expectations")
        val decision = _ownership_result(episode.decision, "decision", resume.nonEmpty,
          _ownership_decision(episode.decision, resume))
        When("the separate current correction and later comment are explicitly integrated through the ordinary public repair result")
        val resolved = _ownership_result(episode.resolved, "resolved", resume.nonEmpty,
          _ownership_continue(episode.resolved))
        Then("actual terminal and unresolved-wait evidence are retained without replaying any successful case")
        Files.writeString(_episode_path.resolveSibling(_episode_path.getFileName.toString + ".result.json"),
          Json.obj("resolved" -> resolved, "decision" -> decision).noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
      }
    }
  }
  "ID08 installed same-Session Purpose preparation continuation" should {
    if (_phase == "prepare-capture") {
      "retain the actual old same-hunk preparation failure after accepting the repair once" in {
        Given("one new or unchanged pre-adoption episode and an absent failure checkpoint under the old normally installed product")
        val destination = _episode_path
        val checkpoint = destination.resolveSibling(destination.getFileName.toString + ".prepare-failure.json")
        Files.exists(checkpoint) shouldBe false
        sys.props.get("smworkflow.spec.purposePreparationRetainedCapture") match {
          case Some(metadata) => _recover_preparation_capture(destination, checkpoint, Path.of(metadata))
          case None => {
        val episode = if (Files.exists(destination)) {
          Files.isRegularFile(destination) shouldBe true
          val retained = parse(Files.readString(destination)).toOption.get.as[Episode].toOption.get
          retained.installedCommand shouldBe installed().toString
          retained.progress.selection.get.checks shouldBe (originalChecks :+ appendedCheck)
          retained.progress.request.files.find(_.path == _baseline).get.worktree shouldBe Some(_ownership_old)
          retained
        } else _capture(focused = true, legacy = false, ownership = true,
          preparationCapture = true, destination = destination)
        val (scenario, target, assignment) = _ownership_qualification(episode)
        val session = episode.session
        val continuation = P.OwnershipContinuation(Vector(P.OwnershipGroup("future-purpose",
          Vector(P.Content(_baseline, Some(_ownership_new))))),
          "Fixture-authored integration keeps the current corrected expectation and the later same-line trim assertion")
        val record = take(new org.goldenport.record.io.RecordDecoder().json(continuation.asJson.noSpaces))
        val result = V.RepositorySyncNativeMergeRepairResult("repaired",
          "Correct the stale current assertion while explicitly retaining the independently intended same-hunk trim assertion",
          Vector(V.RepositorySyncNativeMergeRepairFile(_baseline, _baseline_new)), Some(record))
        val beforesubmit = _state(scenario, session)
        When("the current correction and complete later integration are submitted exactly once")
        val submission = _ownership_submit(scenario, session, result)
        val acknowledgement = successful(submission).business[V.RepositorySyncNativeMergeRepairSubmissionResult]
        Then("the actual repair is accepted without executing further validation")
        acknowledgement.accepted shouldBe true
        _invocations(scenario).size shouldBe 2
        formalReceipts(scenario, session) shouldBe episode.formal
        val accepted = _state(scenario, session)
        When("separate ordinary Advance installs the accepted repair progress without resubmission")
        val groupedresponse = successful(advance(scenario, session))
        groupedresponse.business[V.RepositorySyncAdvanceResult].condition shouldBe Some("PurposeGrouped")
        val grouped = receipts(scenario, session).filter(_.payload.value.stage == "PurposeGrouped")
          .maxBy(_.next.revision.value)
        val progress = grouped.payload.value.purposes.get
        progress.request shouldBe episode.progress.request
        progress.plan.get.groups.tail shouldBe episode.progress.plan.get.groups.tail
        progress.plan.get.groups.head.content should contain(P.Content(_baseline, Some(_baseline_new)))
        progress.ownership shouldBe Some(continuation)
        (progress.cursor, progress.attempt, progress.slotCursor, progress.baselineTree, progress.effects, progress.commits) shouldBe
          ((episode.progress.cursor, episode.progress.attempt + 1, episode.progress.slotCursor + 1,
            Some(episode.progress.candidate.get.tree), episode.progress.effects, episode.progress.commits))
        val beforeprepare = _state(scenario, session)
        When("old ordinary typed Git preparation attempts the actual overlapping assertion hunk")
        val prepared = successful(advance(scenario, session))
        val wait = prepared.business[V.RepositorySyncAdvanceResult]
        Then("the real failed provider result is retained instead of silently dropping the separately integrated later text")
        wait.kind shouldBe "Wait"
        wait.condition shouldBe Some("purpose-effect-unfinished")
        wait.message should include("Purpose candidate preparation did not succeed")
        wait.message should include("Failed(1)")
        val failed = receipts(scenario, session).filter(_.payload.value.stage == "PurposeUnfinished")
          .maxBy(_.next.revision.value)
        val intent = receipts(scenario, session).find(receipt => receipt.payload.value.stage == "PurposePreparing" &&
          receipt.next.revision.next == failed.next.revision).get
        failed.reference.identity should endWith("-prepare-result")
        intent.reference.identity should endWith("-prepare-intent")
        val failedprogress = failed.payload.value.purposes.get
        failedprogress.effects.dropRight(1) shouldBe progress.effects
        val effect = failedprogress.effects.last
        effect.hcursor.get[String]("outcome").toOption shouldBe Some("Failed(1)")
        val process = WorkflowSbtCapture.decode(effect.hcursor.downField("process").focus.get).toOption.get
        WorkflowSbtCapture.termination(process) shouldBe "exited"
        WorkflowSbtCapture.exitCode(process) shouldBe Some(1)
        _invocations(scenario).size shouldBe 2
        formalReceipts(scenario, session) shouldBe episode.formal
        val failurestate = _state(scenario, session)
        Vector("head", "indexTree", "status", "diff", "checks", "invocations").foreach { name =>
          failurestate.hcursor.downField(name).focus shouldBe beforeprepare.hcursor.downField(name).focus
        }
        _work(scenario, episode.originalWorkIdentity) shouldBe episode.originalRequest
        stored(scenario).exists(raw => parse(raw).toOption.exists(_.findAllByKey("externalEffects")
          .contains(Json.fromString("UNKNOWN")))) shouldBe true
        When("ordinary Advance observes the retained preparation failure again")
        val repeated = successful(advance(scenario, session))
        Then("the retained unfinished Wait returns without another Git preparation or SBT execution")
        val repeatedwait = repeated.business[V.RepositorySyncAdvanceResult]
        repeatedwait.kind shouldBe "Wait"
        repeatedwait.condition shouldBe Some("purpose-unfinished")
        _state(scenario, session) shouldBe failurestate
        def invocation(value: F.Result): Json = Json.obj("exitCode" -> value.exitCode.asJson,
          "argv" -> value.invocation.argv.asJson, "pid" -> value.invocation.pid.asJson,
          "stdout" -> value.invocation.stdout.toString.asJson, "stderr" -> value.invocation.stderr.toString.asJson)
        val saved = Json.obj("qualificationPhase" -> "prepare-capture".asJson,
          "qualificationProvenance" -> "actual old installed same-hunk preparation failure; fixture-owned integration and lifecycle inputs".asJson,
          "episode" -> episode.asJson, "session" -> session.asJson,
          "target" -> target.asJson, "issuedOwnership" -> assignment, "ownershipContinuation" -> continuation.asJson,
          "repairResult" -> json(result).asJson, "acceptedAcknowledgement" -> json(acknowledgement).asJson,
          "beforeSubmission" -> beforesubmit, "acceptedState" -> accepted,
          "groupedProgress" -> P.repairProgressJson(progress), "groupedResponse" -> invocation(groupedresponse),
          "beforePreparation" -> beforeprepare, "failedProgress" -> P.repairProgressJson(failedprogress),
          "failedState" -> failurestate, "failedIntentReference" -> intent.reference.asJson,
          "failedIntentRecord" -> take(WorkflowInstanceRecordJsonV1.encodeC(intent.next)).asJson,
          "failedResultReference" -> failed.reference.asJson,
          "failedResultRecord" -> take(WorkflowInstanceRecordJsonV1.encodeC(failed.next)).asJson,
          "failedEffect" -> effect, "prepareResponse" -> invocation(prepared), "repeatResponse" -> invocation(repeated),
          "sbtInvocations" -> _invocations(scenario).asJson, "formalReceipts" -> formalReceipts(scenario, session).asJson,
          "acceptedRepairResubmitted" -> false.asJson, "realConsumerOperations" -> false.asJson)
        Files.writeString(checkpoint, saved.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
          }
        }
      }
    }
    if (_phase == "prepare-continue") {
      "continue the retained actual preparation failure in the same Session through ordinary completion" in {
        import RepositorySyncPurposePreparationContinuation as A
        Given("the exact failed old-product episode after its repair was accepted once and the updated normal installed product")
        val destination = _episode_path
        val checkpoint = destination.resolveSibling(destination.getFileName.toString + ".prepare-failure.json")
        val completedpath = destination.resolveSibling(destination.getFileName.toString + ".prepare-completion.json")
        Files.isRegularFile(destination) shouldBe true
        Files.isRegularFile(checkpoint) shouldBe true
        Files.exists(completedpath) shouldBe false
        val episode = parse(Files.readString(destination)).toOption.get.as[Episode].toOption.get
        val captured = parse(Files.readString(checkpoint)).toOption.get
        captured.hcursor.get[Json]("episode").toOption shouldBe Some(episode.asJson)
        captured.hcursor.get[String]("session").toOption shouldBe Some(episode.session)
        val acknowledgement = take(new org.goldenport.record.io.RecordDecoder().json(
          captured.hcursor.get[String]("acceptedAcknowledgement").toOption.get)
          .flatMap(summon[ValueReader[V.RepositorySyncNativeMergeRepairSubmissionResult]].readC))
        acknowledgement.accepted shouldBe true
        captured.hcursor.get[Boolean]("acceptedRepairResubmitted").toOption shouldBe Some(false)
        installed()
        val scenario = _reopen(episode)
        val session = episode.session
        val retainedstate = captured.hcursor.downField("failedState").focus.get
        _state(scenario, session) shouldBe retainedstate
        _work(scenario, episode.originalWorkIdentity) shouldBe episode.originalRequest
        formalReceipts(scenario, session) shouldBe episode.formal
        _invocations(scenario).size shouldBe 2
        val originalfailure = receipts(scenario, session).find(receipt =>
          receipt.reference.asJson == captured.hcursor.downField("failedResultReference").focus.get).get
        def verifyOriginalFailure(): Unit = {
          val retained = receipts(scenario, session).filter(_.reference == originalfailure.reference)
          retained.size shouldBe 1
          take(WorkflowInstanceRecordJsonV1.encodeC(retained.head.next)) shouldBe
            take(WorkflowInstanceRecordJsonV1.encodeC(originalfailure.next))
          RepositorySyncNativeProgressStore.codec.encode(retained.head.payload.value) shouldBe
            RepositorySyncNativeProgressStore.codec.encode(originalfailure.payload.value)
        }
        val failedprogress = originalfailure.payload.value.purposes.get
        P.repairProgressJson(failedprogress) shouldBe captured.hcursor.downField("failedProgress").focus.get
        val continuation = failedprogress.ownership.get
        val observedcalls = Vector.newBuilder[Json]
        def invocation(value: F.Result): Json = Json.obj("exitCode" -> value.exitCode.asJson,
          "argv" -> value.invocation.argv.asJson, "pid" -> value.invocation.pid.asJson,
          "stdout" -> value.invocation.stdout.toString.asJson, "stderr" -> value.invocation.stderr.toString.asJson)
        def describe(): F.Result = {
          val result = scenario.repositoryCall(A.describeOperation, Vector("--sessionId", session))
          observedcalls += invocation(result)
          result
        }
        def adopt(input: V.RepositorySyncPurposePreparationContinuationAdoptionInput): F.Result = {
          val result = scenario.repositoryCall(A.adoptionOperation, Vector("--input", json(input)))
          observedcalls += invocation(result)
          result
        }
        def direction(described: F.Result, choice: String, suffix: String): V.RepositorySyncPurposePreparationContinuationAdoptionInput = {
          val description = successful(described).business[V.RepositorySyncPurposePreparationContinuationDescriptionResult]
          val target = take(A.targetC(description.target))
          val directory = Files.createTempDirectory(scenario.root, "fixture-preparation-direction-" + suffix + "-")
          val reference = V.WorkflowArtifactReference(directory.getFileName.toString, "1")
          val answer = V.RepositorySyncPurposePreparationContinuationAnswer(choice,
            "Fixture-authored " + choice + " for the exact known failed preparation; no real Human consent is claimed")
          val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(target.context.participantIdentity), None, None)
          val input = V.WorkflowEvidencePreparationInput("RepositorySync", "purpose-preparation-continuation",
            described.invocation.stdout.toUri.toASCIIString, save(directory, "answer.json", json(answer)),
            save(directory, "reference.json", json(reference)), save(directory, "execution.json", json(execution)),
            "normal-development-host", java.time.Instant.now().toString, "entity:smworkflow.cli.host_evidence",
            Some(target.context.participantIdentity))
          val prepared = scenario.prepareHostInput("prepareEvidence", take(new org.goldenport.record.io.RecordDecoder().json(json(input))))
          observedcalls += invocation(prepared)
          successful(prepared)
          scenario.selectPreparedImport(prepared)
          V.RepositorySyncPurposePreparationContinuationAdoptionInput(session, description.target, answer.choice,
            answer.rationale, reference, execution)
        }
        When("the new public description reads the actual old failed provider receipt without repeating preparation")
        val described = describe()
        val description = successful(described).business[V.RepositorySyncPurposePreparationContinuationDescriptionResult]
        description.outcome shouldBe "adoption-required"
        description.choices shouldBe A.choices
        description.adoptionOperation shouldBe A.adoptionOperation
        val target = take(A.targetC(description.target))
        A.effect(target).toOption.get shouldBe captured.hcursor.downField("failedEffect").focus.get
        A.progress(target).toOption.get shouldBe failedprogress
        target.intentKey should endWith("-prepare-intent")
        target.failureKey should endWith("-prepare-result")
        val effect = A.effect(target).toOption.get
        val process = WorkflowSbtCapture.decode(effect.hcursor.downField("process").focus.get).toOption.get
        effect.hcursor.get[String]("outcome").toOption shouldBe Some("Failed(1)")
        WorkflowSbtCapture.termination(process) shouldBe "exited"
        WorkflowSbtCapture.exitCode(process) shouldBe Some(1)
        Then("the complete retained stdout stderr failed progress and repeated description remain passive")
        take(A.targetC(successful(describe()).business[V.RepositorySyncPurposePreparationContinuationDescriptionResult].target)) shouldBe target
        _state(scenario, session) shouldBe retainedstate
        val adoptionresume = sys.props.get("smworkflow.spec.purposePreparationAdoptionResume").map { value =>
          val path = Path.of(value)
          require(path.isAbsolute && path.normalize == path && Files.isRegularFile(path))
          path
        }
        if (adoptionresume.isEmpty) {
        val driftinput = direction(described, A.choices.head, "drift")
        val baselinepath = _repository(scenario).resolve(_baseline)
        val roottext = Files.readString(baselinepath)
        When("the selected root text drifts after an exact direction was prepared")
        try {
          Files.writeString(baselinepath, roottext + "\n// fixture root drift\n")
          val driftstate = _state(scenario, session)
          val ineligible = successful(describe()).business[V.RepositorySyncPurposePreparationContinuationDescriptionResult]
          ineligible.outcome shouldBe "ineligible"
          ineligible.choices shouldBe Vector("Defer")
          take(A.targetC(ineligible.target)).eligibilityReason should not be empty
          adopt(driftinput).exitCode should not be 0
          _state(scenario, session) shouldBe driftstate
        } finally Files.writeString(baselinepath, roottext)
        _state(scenario, session) shouldBe retainedstate
        When("crossed Session changed target and missing rationale are supplied")
        Vector(driftinput.copy(sessionId = session + "-foreign"),
          driftinput.copy(target = A.targetRecord(target.copy(indexTree = "f" * 40))),
          driftinput.copy(rationale = "")).foreach { input =>
          adopt(input).exitCode should not be 0
          _state(scenario, session) shouldBe retainedstate
        }
        When("the fixture supplies an explicit Defer through the dedicated passive evidence route")
        val deferinput = direction(describe(), "Defer", "defer")
        val deferred = successful(adopt(deferinput)).business[V.RepositorySyncPurposePreparationContinuationAdoptionResult]
        deferred.accepted shouldBe false
        deferred.outcome shouldBe "deferred"
        Then("Defer preserves the original failure accepted repair counters and all effect history")
        _state(scenario, session) shouldBe retainedstate
        _invocations(scenario).size shouldBe 2
        }
        When("the fixture explicitly authorizes one new preparation against a fresh exact public target")
        val authorizedescription = describe()
        val authorizationtarget = take(A.targetC(successful(authorizedescription)
          .business[V.RepositorySyncPurposePreparationContinuationDescriptionResult].target))
        authorizationtarget shouldBe target
        val input = adoptionresume match {
          case Some(path) =>
            val retained = take(new org.goldenport.record.io.RecordDecoder().json(Files.readString(path))
              .flatMap(summon[ValueReader[V.RepositorySyncPurposePreparationContinuationAdoptionInput]].readC))
            retained.sessionId shouldBe session
            retained.choice shouldBe A.choices.head
            retained.rationale.trim should not be empty
            take(A.targetC(retained.target)) shouldBe authorizationtarget
            retained.executionEvidence.workerIdentity shouldBe Some(authorizationtarget.context.participantIdentity)
            retained.executionEvidence.references should contain(retained.directionReference)
            A.bindingC(authorizationtarget.componentInstance, authorizationtarget, retained.choice, retained.rationale,
              authorizationtarget.context.participantIdentity, org.goldenport.cncf.workflow.ContextReference(
                retained.directionReference.identity, retained.directionReference.revision)).isSuccess shouldBe true
            retained
          case None => direction(authorizedescription, A.choices.head, "authorize")
        }
        val adoptedresult = adopt(input)
        val adopted = successful(adoptedresult).business[V.RepositorySyncPurposePreparationContinuationAdoptionResult]
        adopted.accepted shouldBe true
        adopted.outcome shouldBe "adopted"
        val adoptedstate = _state(scenario, session)
        Then("adoption records only a distinct namespace and duplicates have no preparation or validation effects")
        _invocations(scenario).size shouldBe 2
        formalReceipts(scenario, session) shouldBe episode.formal
        Vector("head", "indexTree", "status", "diff", "checks", "invocations").foreach(name =>
          adoptedstate.hcursor.downField(name).focus shouldBe retainedstate.hcursor.downField(name).focus)
        val grouped = receipts(scenario, session).filter(_.payload.value.stage == "PurposeGrouped")
          .maxBy(_.next.revision.value).payload.value.purposes.get
        grouped.preparationNamespace shouldBe Some(A.namespace(target))
        grouped.copy(preparationNamespace = failedprogress.preparationNamespace) shouldBe failedprogress
        RepositorySyncPurposeRuntime.attemptPrefix(grouped) should not be RepositorySyncPurposeRuntime.attemptPrefix(failedprogress)
        successful(adopt(input)).business[V.RepositorySyncPurposePreparationContinuationAdoptionResult].outcome shouldBe "already-adopted"
        _state(scenario, session) shouldBe adoptedstate
        verifyOriginalFailure()
        val qualification = Json.obj("qualificationPhase" -> "prepare-continue".asJson,
          "qualificationProvenance" -> "same actual old failed preparation Session; fixture-authored Human directions and reviews".asJson,
          "oldPreparationCheckpoint" -> checkpoint.toString.asJson, "oldPreparationFailure" -> captured,
          "publicTarget" -> target.asJson, "publicInvocations" -> observedcalls.result().asJson,
          "retainedFailureState" -> retainedstate, "adoptedState" -> adoptedstate,
          "newPreparationNamespace" -> grouped.preparationNamespace.asJson,
          "adoptionResumeInput" -> adoptionresume.map(_.toString).asJson,
          "negativeQualification" -> (if (adoptionresume.nonEmpty)
            "Previously successful drift, crossed-input, rationale and Defer checks retained in installed advance01; not rerun"
            else "Drift, crossed-input, rationale and Defer checks completed in this invocation").asJson,
          "acceptedRepairResubmitted" -> false.asJson, "realConsumerOperations" -> false.asJson)
        When("separate ordinary Advance performs new preparation and the remaining normal checks reviews and Purpose commits")
        val completed = _finish(scenario, episode, qualification, Some(continuation), Some(grouped))
        Then("the exact old failure stays retained and the one accepted repair has not been resubmitted")
        verifyOriginalFailure()
        _work(scenario, episode.originalWorkIdentity) shouldBe episode.originalRequest
        val preserved = current(scenario, session).history.count(_.currentProgression
          .contains(ProgressionReference("sync.RepairProposalAdmitted")))
        preserved shouldBe _current_from(captured.hcursor.get[String]("failedResultRecord").toOption.get).history
          .count(_.currentProgression.contains(ProgressionReference("sync.RepairProposalAdmitted")))
        Files.writeString(completedpath, completed.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
      }
    }
  }
  private def _recover_preparation_capture(destination: Path, checkpoint: Path, metadata: Path): Unit = {
    Given("the retained actual CLI responses after a fixture-only assertion failed, with no permission to repeat their effects")
    require(metadata.isAbsolute && metadata.normalize == metadata && Files.isRegularFile(metadata))
    Files.isRegularFile(destination) shouldBe true
    Files.exists(checkpoint) shouldBe false
    val episode = parse(Files.readString(destination)).toOption.get.as[Episode].toOption.get
    val scenario = _reopen(episode)
    val session = episode.session
    val locations = parse(Files.readString(metadata)).toOption.get.asObject.get
    locations.keys.toSet shouldBe Set("acknowledgement", "grouped", "prepare", "repeat")
    val sources = locations.toMap.map { case (name, value) =>
      val path = Path.of(value.asString.get)
      require(path.isAbsolute && path.normalize == path && Files.isRegularFile(path))
      path.getParent shouldBe Path.of(episode.root).resolve("logs")
      name -> path
    }
    def raw(name: String): String = Files.readString(sources(name))
    def business[A: ValueReader](name: String): A = take(new org.goldenport.record.io.RecordDecoder().json(raw(name))
      .flatMap(summon[ValueReader[A]].readC))
    When("the collector reads only the four saved ordinary operation responses")
    val acknowledgement = business[V.RepositorySyncNativeMergeRepairSubmissionResult]("acknowledgement")
    acknowledgement.sessionId shouldBe session
    acknowledgement.accepted shouldBe true
    acknowledgement.outcome shouldBe "accepted"
    val groupedresponse = business[V.RepositorySyncAdvanceResult]("grouped")
    groupedresponse.sessionId shouldBe session
    groupedresponse.condition shouldBe Some("PurposeGrouped")
    val prepared = business[V.RepositorySyncAdvanceResult]("prepare")
    prepared.sessionId shouldBe session
    prepared.kind shouldBe "Wait"
    prepared.condition shouldBe Some("purpose-effect-unfinished")
    prepared.message should include("Purpose candidate preparation did not succeed")
    prepared.message should include("Failed(1)")
    val repeated = business[V.RepositorySyncAdvanceResult]("repeat")
    repeated.sessionId shouldBe session
    repeated.kind shouldBe "Wait"
    repeated.condition shouldBe Some("purpose-unfinished")
    Then("actual immutable native progress proves the already completed preparation and leaves all old effects alone")
    val native = receipts(scenario, session)
    val failed = native.filter(_.payload.value.stage == "PurposeUnfinished").maxBy(_.next.revision.value)
    take(WorkflowInstanceRecordJsonV1.encodeC(failed.next)) shouldBe
      take(WorkflowInstanceRecordJsonV1.encodeC(current(scenario, session)))
    val intent = native.find(receipt => receipt.payload.value.stage == "PurposePreparing" &&
      receipt.next.revision.next == failed.next.revision).get
    failed.reference.identity should endWith("-prepare-result")
    intent.reference.identity should endWith("-prepare-intent")
    val grouped = native.filter(receipt => receipt.payload.value.stage == "PurposeGrouped" &&
      receipt.next.revision.value < intent.next.revision.value).maxBy(_.next.revision.value)
    val progress = grouped.payload.value.purposes.get
    val failedprogress = failed.payload.value.purposes.get
    intent.payload.value.purposes shouldBe Some(progress)
    failedprogress.effects.dropRight(1) shouldBe progress.effects
    failedprogress.copy(effects = progress.effects) shouldBe progress
    progress.request shouldBe episode.progress.request
    progress.plan.get.groups.tail shouldBe episode.progress.plan.get.groups.tail
    progress.plan.get.groups.head.content should contain(P.Content(_baseline, Some(_baseline_new)))
    progress.ownership.get.groups shouldBe Vector(P.OwnershipGroup("future-purpose",
      Vector(P.Content(_baseline, Some(_ownership_new)))))
    (progress.cursor, progress.attempt, progress.slotCursor, progress.baselineTree, progress.effects, progress.commits) shouldBe
      ((episode.progress.cursor, episode.progress.attempt + 1, episode.progress.slotCursor + 1,
        Some(episode.progress.candidate.get.tree), episode.progress.effects, episode.progress.commits))
    val effect = failedprogress.effects.last
    effect.hcursor.get[String]("outcome").toOption shouldBe Some("Failed(1)")
    val process = WorkflowSbtCapture.decode(effect.hcursor.downField("process").focus.get).toOption.get
    WorkflowSbtCapture.termination(process) shouldBe "exited"
    WorkflowSbtCapture.exitCode(process) shouldBe Some(1)
    val failurestate = _state(scenario, session)
    Vector("head", "indexTree", "status", "diff", "checks", "invocations").foreach(name =>
      failurestate.hcursor.downField(name).focus shouldBe episode.snapshot.hcursor.downField(name).focus)
    _work(scenario, episode.originalWorkIdentity) shouldBe episode.originalRequest
    formalReceipts(scenario, session) shouldBe episode.formal
    _invocations(scenario).size shouldBe 2
    Files.readString(_repository(scenario).resolve(_baseline)) shouldBe _ownership_old
    Files.readString(_repository(scenario).resolve(_later)) shouldBe _later_new
    Files.readString(_repository(scenario).resolve(_future)) shouldBe _future_new
    failed.next.history.count(_.currentProgression.contains(ProgressionReference("sync.RepairProposalAdmitted"))) shouldBe 1
    stored(scenario).exists(value => parse(value).toOption.exists(_.findAllByKey("externalEffects")
      .contains(Json.fromString("UNKNOWN")))) shouldBe true
    val saved = Json.obj("qualificationPhase" -> "prepare-capture".asJson,
      "qualificationProvenance" -> "read-only recovered retained actualCLIresults after fixtureassertion failure".asJson,
      "episode" -> episode.asJson, "session" -> session.asJson,
      "acceptedAcknowledgement" -> raw("acknowledgement").asJson,
      "acceptedRepairResubmitted" -> false.asJson, "realConsumerOperations" -> false.asJson,
      "failedState" -> failurestate, "failedResultReference" -> failed.reference.asJson,
      "failedResultRecord" -> take(WorkflowInstanceRecordJsonV1.encodeC(failed.next)).asJson,
      "failedIntentReference" -> intent.reference.asJson,
      "failedIntentRecord" -> take(WorkflowInstanceRecordJsonV1.encodeC(intent.next)).asJson,
      "failedProgress" -> P.repairProgressJson(failedprogress), "failedEffect" -> effect,
      "groupedProgress" -> P.repairProgressJson(progress),
      "retainedResponseFiles" -> Json.obj(sources.toVector.map { case (name, path) => name -> path.toString.asJson }*),
      "retainedResponses" -> Json.obj(sources.keys.toVector.map(name => name -> parse(raw(name)).toOption.get)*),
      "sbtInvocations" -> _invocations(scenario).asJson, "formalReceipts" -> formalReceipts(scenario, session).asJson)
    _state(scenario, session) shouldBe failurestate
    Files.writeString(checkpoint, saved.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
  }
  private def _capture(focused: Boolean = false, legacy: Boolean = true, ownership: Boolean = false,
    conflict: Boolean = false, preparationCapture: Boolean = false, destination: Path = _episode_path): Episode = {
    Given(if (legacy) "the old installed CLI, normal SBT Java host and a tiny repository with a stale tracked baseline test"
      else "the current installed CLI and one new tiny completion control with all fixed checks declared initially")
    installed()
    val scenario = fresh(if (preparationCapture) "purpose-preparation-old" else if (legacy)
      "purpose-repair-scope-old" else "purpose-repair-scope-completion", checked = true)
    val repository = _repository(scenario)
    val descriptor = scenario.root.resolve(".textus/assembly.yaml")
    Files.writeString(descriptor, Files.readString(descriptor).replace("      id: participant\n", "      id: sm-workflow-local\n"), UTF_8)
    val build = repository.resolve("build.sbt")
    val originalbuild = Files.readString(build)
    originalbuild should include("Test / test := (Test / runMain).toTask(\" ProductCheck\").value\n")
    val invocationpath = Json.fromString(scenario.root.resolve("sbt-invocations.txt").toString).noSpaces
    Files.writeString(build, originalbuild.replace("Test / test := (Test / runMain).toTask(\" ProductCheck\").value\n", "") +
      "\nlibraryDependencies += \"org.scalatest\" %% \"scalatest\" % \"3.2.19\" % Test\n" +
      "Global / onLoad := { val prior = (Global / onLoad).value; state => { val loaded = prior(state); IO.append(file(" +
      invocationpath + "), java.lang.ProcessHandle.current().pid().toString + \"\\n\"); loaded } }\n")
    Files.createDirectories(repository.resolve(_baseline).getParent)
    Files.writeString(repository.resolve(_baseline), _baseline_old)
    Files.writeString(repository.resolve(_later), _later_old)
    Files.writeString(repository.resolve(_future), "Original future document\n")
    Files.write(repository.resolve("binary.fixture"), Array[Byte](0, 1, 2))
    Files.createSymbolicLink(repository.resolve("symlink.fixture"), Path.of("value.txt"))
    git(scenario.root, "-C", repository.toString, "add", "--", "build.sbt", _baseline, _later, _future, "binary.fixture", "symlink.fixture")
    git(scenario.root, "-C", repository.toString, "commit", "-m", "Tiny actual baseline and future-purpose fixture")
    git(scenario.root, "-C", repository.toString, "push", scenario.host.target.endpoint, "HEAD:refs/heads/main")
    configure(scenario, _parameters(focused) + ("textus.subsystem.process-execution.plan" -> _plan(scenario, focused).noSpaces))
    Files.writeString(repository.resolve("value.txt"), "expected\n")
    Files.writeString(repository.resolve(_later), _later_new)
    val laterbaseline = if (conflict) _ownership_conflict else _ownership_old
    if (ownership) Files.writeString(repository.resolve(_baseline), laterbaseline)
    Files.writeString(repository.resolve(_future), _future_new)
    val session = start(scenario)
    successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitRepositoryPurposeResult")
    val request = purpose_inputs(scenario).head
    request.files.map(_.path).toSet shouldBe (Set("value.txt", _later, _future) ++ Option.when(ownership)(_baseline))
    val groups = Vector(P.Group("current-purpose", "Correct actual product input", "fix: correct actual product input", Vector.empty,
      Vector(P.Content("value.txt", Some("expected\n"))), Vector.empty, "Current input change"),
      P.Group("future-purpose", "Preserve independently intended future edits", "test: retain future purpose", Vector("current-purpose"),
        Vector(P.Content(_later, Some(_later_new)), P.Content(_future, Some(_future_new))) ++
          Option.when(ownership)(P.Content(_baseline, Some(laterbaseline))), Vector.empty, "Future test explanation and unrelated document"))
    successful(submit_purpose(scenario, session, P.Result("resolved", "Fixture-authored ordered independent purposes", groups, Vector.empty)))
      .business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
    When("ordinary runtime validation compiles successfully and runs a genuine Test assertion failure")
    successful(advance(scenario, session))
    val formal = formalReceipts(scenario, session)
    Then("Compile succeeds and the actual baseline Test fails before any appended focused check runs")
    formal.size shouldBe 2
    formal.find(_._2.identity == originalChecks.head.identity).get._2.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded
    val failed = formal.find(_._2.identity == originalChecks(1).identity).get._2
    failed.exitCode.exists(_ != 0) shouldBe true
    failed.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
    failed.stdout should include("BaselineSpec")
    failed.stdout should include("FAILED")
    (failed.stdout + failed.stderr) should not include "SmWorkflowInvalidFixtureOption"
    val repair = successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
    repair.submitOperation shouldBe Some("submitNativeMergeRepair")
    repair.repair.get.files.map(_.path) shouldBe Vector("value.txt")
    val old = current(scenario, session)
    val identity = old.suspension.get.continuationIdentity.value
    val snapshot = _state(scenario, session)
    When("the selected installed product describes the actual failed Purpose repair scope")
    val refused = if (ownership) _prepare(scenario, session, Vector(_baseline))
      else scenario.repositoryCall(N.describeOperation, Vector("--sessionId", session))
    if (legacy) {
      Then("the old product returns its actual 409 while the failed Session native work and original logs remain unchanged")
      refused.exitCode should not be 0
      refused.invocation.logs should include("409")
      if (ownership) {
        refused.invocation.logs should include(_baseline)
        refused.invocation.logs should include("future-purpose")
      }
    } else if (preparationCapture) {
      Then("the current v5 product qualifies exact later ownership while the original failed Session remains unchanged")
      refused.exitCode shouldBe 0
      successful(refused).business[V.RepositorySyncRepairScopeCorrectionDescriptionResult].outcome shouldBe "adoption-required"
      _target(refused).correctionPolicy shouldBe N.ownershipPolicy
    } else {
      Then("the current product requires preparation while the failed Session and original logs remain unchanged")
      refused.exitCode shouldBe 0
      successful(refused).business[V.RepositorySyncRepairScopeCorrectionDescriptionResult].outcome shouldBe "preparation-required"
    }
    _state(scenario, session) shouldBe snapshot
    val episode = Episode(scenario.root.toString, ThreeWorkflowRuntimeParameters.encode(scenario.host), scenario.plan.asJson,
      session, take(WorkflowInstanceRecordJsonV1.encodeC(old)), identity, _work(scenario, identity), _failed_progress(scenario, session),
      snapshot, formal, refused.exitCode, refused.invocation.logs, refused.invocation.stdout.toString, installed().toString)
    Files.writeString(destination, episode.asJson.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
    episode
  }
  private def _continue(episode: Episode, legacy: Boolean = true): Unit = {
    Given(if (legacy) "the new installed product and the same physical failed repository datastore host and Session"
      else "the current installed product and the same newly captured completion-control Session")
    installed()
    val scenario = _reopen(episode)
    val repository = _repository(scenario)
    val session = episode.session
    if (legacy) {
      episode.oldDescriptionExit should not be 0
      episode.oldDescriptionLogs should include("409")
    } else {
      episode.oldDescriptionExit shouldBe 0
      parse(Files.readString(Path.of(episode.oldDescriptionStdout))).toOption.get.hcursor
        .get[String]("outcome").toOption shouldBe Some("preparation-required")
    }
    Files.isRegularFile(Path.of(episode.oldDescriptionStdout)) shouldBe true
    When("the fixture reopens the retained failed control before scope qualification")
    val retained = _state(scenario, session)
    Then("the captured Session original work and failed progress remain exact")
    retained shouldBe episode.snapshot
    _work(scenario, episode.originalWorkIdentity) shouldBe episode.originalRequest
    _failed_progress(scenario, session) shouldBe episode.progress
    When("qualification requests a newly added test owned by the later purpose")
    val overlap = _prepare(scenario, session, Vector(_later))
    Then("a concrete path and later group conflict precedes supersession effects or check execution")
    overlap.exitCode should not be 0
    overlap.invocation.logs should include(_later)
    overlap.invocation.logs should include("future-purpose")
    _state(scenario, session) shouldBe episode.snapshot
    Vector(Vector("../outside"), Vector("/absolute"), Vector("missing.fixture"), Vector("binary.fixture"),
      Vector("symlink.fixture"), Vector("src"), Vector("value.txt", _baseline)).foreach { paths =>
      _prepare(scenario, session, paths).exitCode should not be 0
      _state(scenario, session) shouldBe episode.snapshot
    }
    val slotfile = scenario.root.resolve("purpose-slot-1").resolve(_baseline)
    val slottext = Files.readString(slotfile)
    try {
      Files.writeString(slotfile, slottext + "\n// candidate drift\n")
      _prepare(scenario, session, Vector(_baseline)).exitCode should not be 0
      Files.readString(slotfile) shouldBe slottext + "\n// candidate drift\n"
      _state(scenario, session) shouldBe episode.snapshot
    } finally Files.writeString(slotfile, slottext)
    try {
      Files.writeString(repository.resolve(_baseline), _baseline_old + "\n// root baseline drift\n")
      val before = _state(scenario, session)
      _prepare(scenario, session, Vector(_baseline)).exitCode should not be 0
      _state(scenario, session) shouldBe before
    } finally Files.writeString(repository.resolve(_baseline), _baseline_old)
    When("the existing prepare operation qualifies the necessary nonoverlapping unchanged baseline correction")
    val described = _prepare(scenario, session, Vector(_baseline))
    val target = _target(described)
    Then("the qualified target retains the failed progress and exact candidate and index ownership without effects")
    target.correctionPolicy shouldBe N.purposePolicy
    target.purpose.get.progress shouldBe episode.progress
    target.inventory.get.paths shouldBe Vector(_baseline)
    target.candidateTree shouldBe episode.progress.candidate.get.tree
    target.indexTree shouldBe episode.progress.candidate.get.index
    target.candidateTree should not be target.indexTree
    _target(_prepare(scenario, session, Vector(_baseline))) shouldBe target
    _state(scenario, session) shouldBe episode.snapshot
    val input = _direction(scenario, session, described)
    val invalid = Vector(input.copy(sessionId = session + "-foreign"), input.copy(priorExecutionEnded = false),
      input.copy(acceptOriginalUncertainty = false), input.copy(paths = Vector(_baseline)),
      input.copy(target = N.targetRecord(target.copy(indexTree = "f" * 40))),
      input.copy(target = N.targetRecord(target.copy(frontierRevision = (target.frontierRevision.toLong + 1).toString))))
    When("adoption receives foreign or incomplete lifecycle evidence or a changed target")
    invalid.foreach { value =>
      val refusal = _adopt(scenario, value)
      Then("each invalid adoption is refused without changing the retained Session")
      refusal.exitCode should not be 0
      _state(scenario, session) shouldBe episode.snapshot
    }
    When("the qualified correction is adopted with independent fixture lifecycle evidence")
    val adoption = successful(_adopt(scenario, input)).business[V.RepositorySyncRepairScopeCorrectionAdoptionResult]
    Then("independent fixture lifecycle evidence admits only one native supersession and duplicates have no effects")
    adoption.outcome shouldBe "adopted"
    val adopted = current(scenario, session)
    adopted.revision shouldBe (_current_from(episode.originalRecord).revision.next)
    adopted.history.take(_current_from(episode.originalRecord).history.size) shouldBe _current_from(episode.originalRecord).history
    val admittedstate = _state(scenario, session)
    successful(_adopt(scenario, input)).business[V.RepositorySyncRepairScopeCorrectionAdoptionResult].outcome shouldBe "already-adopted"
    _state(scenario, session) shouldBe admittedstate
    _invocations(scenario).size shouldBe 2
    _work(scenario, episode.originalWorkIdentity) shouldBe episode.originalRequest
    val oldresult = V.RepositorySyncNativeMergeRepairResult("repaired", "Superseded original worker proposal",
      Vector(V.RepositorySyncNativeMergeRepairFile("value.txt", "obsolete\n")), None)
    val reference = V.WorkflowArtifactReference("fixture-purpose-baseline-repair", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-repair-author"), None, None)
    def _submit_(result: V.RepositorySyncNativeMergeRepairResult): F.Result = scenario.repositoryCall("submitNativeMergeRepair",
      Vector("--sessionId", session, "--result", json(result), "--resultReference", json(reference), "--executionEvidence", json(execution)))
    When("the superseded original worker submits its obsolete proposal")
    val obsolete = _submit_(oldresult)
    Then("the submission is refused and the admitted correction remains unchanged")
    obsolete.exitCode should not be 0
    _state(scenario, session) shouldBe admittedstate
    When("separate ordinary Advance captures only the qualified actual candidate text")
    val issued = successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
    Then("the issued repair contains only the qualified baseline and repeated Advance preserves its inventory and original work")
    issued.repair.get.files.map(_.path) shouldBe Vector(_baseline)
    issued.repair.get.files.head.text shouldBe _baseline_old
    _work(scenario, current(scenario, session).suspension.get.continuationIdentity.value).admittedInventory shouldBe target.inventory
    successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult] shouldBe issued
    _invocations(scenario).size shouldBe 2
    _work(scenario, episode.originalWorkIdentity) shouldBe episode.originalRequest
    val result = V.RepositorySyncNativeMergeRepairResult("repaired", "Fixture-authored real correction of the actual stale assertion; original source provenance is this tiny specification",
      Vector(V.RepositorySyncNativeMergeRepairFile(_baseline, _baseline_new)), None)
    When("the issued repair submits the fixture-authored baseline correction")
    val submission = successful(_submit_(result)).business[V.RepositorySyncNativeMergeRepairSubmissionResult]
    Then("the native repair is accepted without executing additional checks")
    submission.accepted shouldBe true
    _invocations(scenario).size shouldBe 2
    val provenance = if (legacy) Json.obj() else Json.obj(
      "qualificationPhase" -> "completion".asJson,
      "qualificationProvenance" -> "current-installed normal-completion control".asJson,
      "retainedLegacyEpisode" -> sys.props("smworkflow.spec.purposeRepairOriginalEpisode").asJson,
      "retainedLegacyEpisodeReexecuted" -> false.asJson)
    _finish(scenario, episode, Json.obj(
      "overlapConflictLogs" -> overlap.invocation.logs.asJson, "target" -> target.asJson).deepMerge(provenance))
  }
  private def _ownership_submit(scenario: F.Scenario, session: String,
    result: V.RepositorySyncNativeMergeRepairResult): F.Result = {
    val reference = V.WorkflowArtifactReference("fixture-purpose-ownership-repair", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-ownership-author"), None, None)
    scenario.repositoryCall("submitNativeMergeRepair", Vector("--sessionId", session, "--result", json(result),
      "--resultReference", json(reference), "--executionEvidence", json(execution)))
  }
  private def _ownership_result(episode: Episode, kind: String, resume: Boolean, run: => Json): Json = {
    val path = _episode_path.resolveSibling(_episode_path.getFileName.toString + "." + kind + ".result.json")
    if (Files.exists(path)) {
      require(resume, "Select an explicit purposeOwnershipResume checkpoint to reuse a completed episode")
      val saved = parse(Files.readString(path)).toOption.get
      saved.hcursor.get[String]("session").toOption shouldBe Some(episode.session)
      saved.hcursor.get[String]("root").toOption shouldBe Some(episode.root)
      val scenario = _reopen(episode)
      val statekey = if (kind == "decision") "after" else "finalState"
      _state(scenario, episode.session) shouldBe saved.hcursor.downField(statekey).focus.get
      saved
    } else {
      val result = run
      Files.writeString(path, result.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
      result
    }
  }
  private def _ownership_assignment(scenario: F.Scenario, episode: Episode, target: N.Target,
    issued: V.RepositorySyncAdvanceResult, repeatrequest: Boolean = true): Json = {
    val session = episode.session
    val roottext = episode.progress.request.files.find(_.path == _baseline).get.worktree.get
    Then("candidate text, later intent and required owner path pairs are exposed separately and repetition is passive")
    issued.submitOperation shouldBe Some("submitNativeMergeRepair")
    issued.repair.get.files.map(_.path) shouldBe Vector(_baseline)
    issued.repair.get.files.head.text shouldBe _baseline_old
    val issuedjson = parse(json(issued)).toOption.get
    val assignment = GoalPhaseNativeMetadata.normalizePublicJson(
      parse(take(issued.purposeWork.get.toJsonStringC)).toOption.get).toOption.get
    assignment.hcursor.get[Vector[M.File]]("candidateBaseline").toOption.get shouldBe Vector(M.File(_baseline, _baseline_old))
    assignment.hcursor.get[Vector[Json]]("requiredResultPairs").toOption.get shouldBe
      Vector(Json.obj("identity" -> "future-purpose".asJson, "path" -> _baseline.asJson))
    val owners = assignment.hcursor.get[Vector[Json]]("owners").toOption.get
    owners.size shouldBe 1
    owners.head.hcursor.get[String]("identity").toOption shouldBe Some("future-purpose")
    owners.head.hcursor.get[P.Group]("originalGroup").toOption.get shouldBe episode.progress.plan.get.groups(1)
    owners.head.hcursor.get[Vector[P.Content]]("capturedWorktree").toOption.get shouldBe Vector(P.Content(_baseline, Some(roottext)))
    _work(scenario, issued.issuedWorkContext.get.continuationId).admittedInventory shouldBe target.inventory
    val pending = _state(scenario, session)
    if (repeatrequest) successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult] shouldBe issued
    _state(scenario, session) shouldBe pending
    _invocations(scenario).size shouldBe 2
    _work(scenario, episode.originalWorkIdentity) shouldBe episode.originalRequest
    issuedjson
  }
  private def _ownership_resumed_qualification(episode: Episode, checkpoint: OwnershipResume): (F.Scenario, N.Target, Json) = {
    Given("an explicitly selected checkpoint from the actual already issued decision Session Request")
    installed()
    val target = N.decodePublicTarget(checkpoint.target).toOption.get
    checkpoint.decisionSession shouldBe episode.session
    target.sessionId shouldBe episode.session
    target.correctionPolicy shouldBe N.ownershipPolicy
    target.inventory.get.inventoryPolicy shouldBe RepositorySyncRepairInventory.ownershipPolicy
    target.inventory.get.paths shouldBe Vector(_baseline)
    target.inventory.get.purpose.get.snapshot.progress shouldBe episode.progress
    target.purpose.get.progress shouldBe episode.progress
    val scenario = _reopen(episode)
    _failed_progress(scenario, episode.session) shouldBe episode.progress
    formalReceipts(scenario, episode.session) shouldBe episode.formal
    _work(scenario, episode.originalWorkIdentity) shouldBe episode.originalRequest
    val original = _current_from(episode.originalRecord)
    current(scenario, episode.session).history.take(original.history.size) shouldBe original.history
    val before = _state(scenario, episode.session)
    if (!checkpoint.snapshot.isNull) before shouldBe checkpoint.snapshot
    Vector("head", "indexTree", "status", "diff", "checks", "invocations").foreach { name =>
      before.hcursor.downField(name).focus shouldBe episode.snapshot.hcursor.downField(name).focus
    }
    val savedissued = take(summon[ValueReader[V.RepositorySyncAdvanceResult]].readC(
      take(new org.goldenport.record.io.RecordDecoder().json(checkpoint.issued.noSpaces))))
    val identity = savedissued.issuedWorkContext.get.continuationId
    identity should not be episode.originalWorkIdentity
    _work(scenario, identity).admittedInventory shouldBe target.inventory
    val issued = checkpoint.acceptedDecision match {
      case Some(value) =>
        When("the explicit checkpoint retains the actual already accepted decision acknowledgement")
        val acknowledgement = take(summon[ValueReader[V.RepositorySyncNativeMergeRepairSubmissionResult]].readC(
          take(new org.goldenport.record.io.RecordDecoder().json(value.noSpaces))))
        Then("the acknowledged decision and actual current frontier identify the same retained completion without resubmission")
        acknowledgement.sessionId shouldBe episode.session
        acknowledgement.accepted shouldBe true
        acknowledgement.outcome shouldBe "accepted"
        acknowledgement.message shouldBe "Accepted merge repair decision-needed: " + _ownership_decision_rationale
        val retained = current(scenario, episode.session)
        retained.currentProgression shouldBe Some(ProgressionReference("sync.RepairDecisionRequired"))
        retained.causation.value shouldBe "fixture-purpose-ownership-repair"
        savedissued
      case None =>
        current(scenario, episode.session).suspension.get.continuationIdentity.value shouldBe identity
        When("passive ordinary Advance collects the already issued Request without repeating qualification or adoption")
        val actual = successful(advance(scenario, episode.session)).business[V.RepositorySyncAdvanceResult]
        Then("the actual public Request is the exact checkpoint and has caused no new effects")
        GoalPhaseNativeMetadata.normalizePublicJson(parse(json(actual)).toOption.get).toOption.get shouldBe
          GoalPhaseNativeMetadata.normalizePublicJson(checkpoint.issued).toOption.get
        actual
    }
    _state(scenario, episode.session) shouldBe before
    val assignment = _ownership_assignment(scenario, episode, target, issued, repeatrequest = checkpoint.acceptedDecision.isEmpty)
    (scenario, target, assignment)
  }
  private def _ownership_qualification(episode: Episode): (F.Scenario, N.Target, Json) = {
    Given("the unchanged old ownership failure, original UNKNOWN work, complete progress and failed formal receipts")
    installed()
    val scenario = _reopen(episode)
    val session = episode.session
    val repository = _repository(scenario)
    val roottext = episode.progress.request.files.find(_.path == _baseline).get.worktree.get
    if (_phase == "prepare-capture") {
      episode.oldDescriptionExit shouldBe 0
      val captured = parse(Files.readString(Path.of(episode.oldDescriptionStdout))).toOption.get
      captured.hcursor.get[String]("outcome").toOption shouldBe Some("adoption-required")
      captured.hcursor.get[String]("adoption_operation").toOption shouldBe Some(N.adoptionOperation)
    } else {
      episode.oldDescriptionExit should not be 0
      episode.oldDescriptionLogs should include("409")
      episode.oldDescriptionLogs should include(_baseline)
      episode.oldDescriptionLogs should include("future-purpose")
    }
    Files.isRegularFile(Path.of(episode.oldDescriptionStdout)) shouldBe true
    _state(scenario, session) shouldBe episode.snapshot
    _work(scenario, episode.originalWorkIdentity) shouldBe episode.originalRequest
    _failed_progress(scenario, session) shouldBe episode.progress
    When("invalid paths and drift are presented to the new ownership qualification")
    Vector(Vector("../outside"), Vector("/absolute"), Vector("missing.fixture"), Vector("binary.fixture"),
      Vector("symlink.fixture"), Vector("src"), Vector("value.txt", _baseline)).foreach { paths =>
      _prepare(scenario, session, paths).exitCode should not be 0
      _state(scenario, session) shouldBe episode.snapshot
    }
    val slotfile = scenario.root.resolve("purpose-slot-1").resolve(_baseline)
    val slottext = Files.readString(slotfile)
    try {
      Files.writeString(slotfile, slottext + "\n// candidate drift\n")
      _prepare(scenario, session, Vector(_baseline)).exitCode should not be 0
      Files.readString(slotfile) shouldBe slottext + "\n// candidate drift\n"
      _state(scenario, session) shouldBe episode.snapshot
    } finally Files.writeString(slotfile, slottext)
    try {
      Files.writeString(repository.resolve(_baseline), roottext + "\n// root drift\n")
      val before = _state(scenario, session)
      _prepare(scenario, session, Vector(_baseline)).exitCode should not be 0
      _state(scenario, session) shouldBe before
    } finally Files.writeString(repository.resolve(_baseline), roottext)
    When("the existing prepare operation binds the candidate baseline and exact original later owner")
    val described = _prepare(scenario, session, Vector(_baseline))
    val target = _target(described)
    Then("v5 and inventory v2 qualify exact ownership without replacing any original intent or executing checks")
    target.correctionPolicy shouldBe N.ownershipPolicy
    target.inventory.get.inventoryPolicy shouldBe RepositorySyncRepairInventory.ownershipPolicy
    target.inventory.get.paths shouldBe Vector(_baseline)
    target.inventory.get.purpose.get.snapshot.progress shouldBe episode.progress
    target.inventory.get.purpose.get.baselines shouldBe empty
    target.purpose.get.progress shouldBe episode.progress
    target.candidateTree shouldBe episode.progress.candidate.get.tree
    target.indexTree shouldBe episode.progress.candidate.get.index
    target.candidateTree should not be target.indexTree
    _target(_prepare(scenario, session, Vector(_baseline))) shouldBe target
    val passive = successful(scenario.repositoryCall(N.describeOperation, Vector("--sessionId", session)))
    passive.business[V.RepositorySyncRepairScopeCorrectionDescriptionResult].outcome shouldBe "preparation-required"
    _state(scenario, session) shouldBe episode.snapshot
    val direction = _direction(scenario, session, described)
    When("adoption receives incomplete worker lifecycle evidence or crossed target facts")
    Vector(direction.copy(sessionId = session + "-foreign"), direction.copy(priorExecutionEnded = false),
      direction.copy(acceptOriginalUncertainty = false), direction.copy(paths = Vector(_baseline)),
      direction.copy(target = N.targetRecord(target.copy(indexTree = "f" * 40))),
      direction.copy(target = N.targetRecord(target.copy(frontierRevision = (target.frontierRevision.toLong + 1).toString))))
      .foreach { value =>
        _adopt(scenario, value).exitCode should not be 0
        _state(scenario, session) shouldBe episode.snapshot
      }
    When("fixture-authored lifecycle evidence adopts the exact correction once")
    successful(_adopt(scenario, direction)).business[V.RepositorySyncRepairScopeCorrectionAdoptionResult].outcome shouldBe "adopted"
    val adopted = current(scenario, session)
    Then("one supersession retains the original failed history and the duplicate adoption is passive")
    val original = _current_from(episode.originalRecord)
    adopted.revision shouldBe original.revision.next
    adopted.history.take(original.history.size) shouldBe original.history
    val admitted = _state(scenario, session)
    successful(_adopt(scenario, direction)).business[V.RepositorySyncRepairScopeCorrectionAdoptionResult].outcome shouldBe "already-adopted"
    _state(scenario, session) shouldBe admitted
    val oldresult = V.RepositorySyncNativeMergeRepairResult("repaired", "Superseded old fixture worker",
      Vector(V.RepositorySyncNativeMergeRepairFile("value.txt", "obsolete\n")), None)
    When("the obsolete original worker submits before ordinary successor issuance")
    _ownership_submit(scenario, session, oldresult).exitCode should not be 0
    Then("the old worker is refused without effects")
    _state(scenario, session) shouldBe admitted
    When("separate ordinary Advance issues the qualified nativeRepair ownership assignment")
    val issued = successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
    val issuedjson = _ownership_assignment(scenario, episode, target, issued)
    (scenario, target, issuedjson)
  }
  private def _ownership_decision_rationale: String =
    "Conflicting expectations at " + _baseline + " owned by future-purpose: current input must remain expected while later intent requires future-only; actual semantic/Human direction is required"
  private def _ownership_decision(episode: Episode, checkpoint: Option[OwnershipResume] = None): Json = {
    val (scenario, target, assignment) = checkpoint match {
      case Some(value) => _ownership_resumed_qualification(episode, value)
      case None => _ownership_qualification(episode)
    }
    val rationale = _ownership_decision_rationale
    val decision = V.RepositorySyncNativeMergeRepairResult("decision-needed", rationale, Vector.empty, None)
    val before = _state(scenario, episode.session)
    if (!checkpoint.exists(_.acceptedDecision.nonEmpty)) {
      When("the assigned worker returns the actual unresolved assertion conflict without replacements or continuation")
      successful(_ownership_submit(scenario, episode.session, decision))
        .business[V.RepositorySyncNativeMergeRepairSubmissionResult].accepted shouldBe true
    }
    When("ordinary Advance collects the actual retained unresolved decision")
    val waiting = successful(advance(scenario, episode.session)).business[V.RepositorySyncAdvanceResult]
    Then("the existing public route returns RepairDecisionRequired Wait with the actual owner and path and no candidate effects")
    waiting.kind shouldBe "Wait"
    waiting.condition shouldBe Some("RepairDecisionRequired")
    waiting.message should include(_baseline)
    waiting.message should include("future-purpose")
    waiting.message should include(rationale)
    current(scenario, episode.session).currentProgression shouldBe Some(ProgressionReference("sync.RepairDecisionRequired"))
    val after = _state(scenario, episode.session)
    Vector("head", "indexTree", "status", "diff", "checks", "invocations").foreach { name =>
      after.hcursor.downField(name).focus shouldBe before.hcursor.downField(name).focus
    }
    successful(advance(scenario, episode.session)).business[V.RepositorySyncAdvanceResult].condition shouldBe Some("RepairDecisionRequired")
    _state(scenario, episode.session) shouldBe after
    _failed_progress(scenario, episode.session) shouldBe episode.progress
    _work(scenario, episode.originalWorkIdentity) shouldBe episode.originalRequest
    Json.obj("session" -> episode.session.asJson, "root" -> episode.root.asJson, "target" -> target.asJson,
      "issued" -> assignment, "rationale" -> rationale.asJson, "wait" -> parse(json(waiting)).toOption.get,
      "before" -> before, "after" -> after, "resolved" -> false.asJson)
  }
  private def _ownership_continue(episode: Episode): Json = {
    val (scenario, target, assignment) = _ownership_qualification(episode)
    val continuation = P.OwnershipContinuation(Vector(P.OwnershipGroup("future-purpose",
      Vector(P.Content(_baseline, Some(_ownership_new))))),
      "Explicit fixture integration preserves the corrected current assertion and the separately intended later baseline explanation")
    val record = take(new org.goldenport.record.io.RecordDecoder().json(continuation.asJson.noSpaces))
    val result = V.RepositorySyncNativeMergeRepairResult("repaired", "Correct current stale baseline; separately preserve later Purpose meaning",
      Vector(V.RepositorySyncNativeMergeRepairFile(_baseline, _baseline_new)), Some(record))
    val before = _state(scenario, episode.session)
    When("the worker omits the required later-owner continuation")
    _ownership_submit(scenario, episode.session, result.copy(purposeContinuation = None)).exitCode should not be 0
    Then("the incomplete current-only repair is refused without swallowing later intent")
    _state(scenario, episode.session) shouldBe before
    When("the worker submits distinct current correction and exact integrated later ownership text")
    successful(_ownership_submit(scenario, episode.session, result))
      .business[V.RepositorySyncNativeMergeRepairSubmissionResult].accepted shouldBe true
    Then("proposal admission executes no new checks")
    _invocations(scenario).size shouldBe 2
    _finish(scenario, episode, Json.obj("qualificationPhase" -> "ownership-continue".asJson,
      "target" -> target.asJson, "issuedOwnership" -> assignment,
      "ownershipContinuation" -> continuation.asJson), Some(continuation))
  }
  private def _finish(scenario: F.Scenario, episode: Episode, qualification: Json,
    ownership: Option[P.OwnershipContinuation] = None, retainedGrouped: Option[P.Progress] = None): Json = {
    val repository = _repository(scenario)
    val session = episode.session
    val repaired = retainedGrouped.getOrElse {
      When("ordinary Advance transitions the accepted repair back to grouped Purpose work")
      val grouped = successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
      Then("the expanded group retains the original provenance and later groups while advancing only the repair attempt and slot")
      grouped.condition shouldBe Some("PurposeGrouped")
      receipts(scenario, session).filter(_.payload.value.stage == "PurposeGrouped")
        .maxBy(_.next.revision.value).payload.value.purposes.get
    }
    repaired.request.files.take(episode.progress.request.files.size) shouldBe episode.progress.request.files
    repaired.request.records shouldBe episode.progress.request.records
    if (ownership.isEmpty) {
      repaired.request.files.last.path shouldBe _baseline
      repaired.request.files.last.worktree shouldBe Some(_baseline_old)
    } else {
      repaired.request shouldBe episode.progress.request
      repaired.ownership shouldBe ownership
      repaired.plan.get.groups(1).content should contain(P.Content(_baseline, Some(_ownership_old)))
      repaired.plan.get.groups(1).content should contain(P.Content(_future, Some(_future_new)))
    }
    repaired.plan.get.groups.tail shouldBe episode.progress.plan.get.groups.tail
    repaired.plan.get.groups.head.content should contain(P.Content(_baseline, Some(_baseline_new)))
    (repaired.cursor, repaired.attempt, repaired.slotCursor, repaired.baselineTree, repaired.commits, repaired.effects) shouldBe
      ((episode.progress.cursor, episode.progress.attempt + 1, episode.progress.slotCursor + 1,
        Some(episode.progress.candidate.get.tree), episode.progress.commits, retainedGrouped.fold(episode.progress.effects)(_.effects)))
    When("ordinary runtime prepares the expanded candidate and obtains fresh affected checks and independent review")
    val reviewed = successful(advance(scenario, session))
    Then("fresh successful checks cover the corrected slot and native review waits while the root and later work remain preserved")
    reviewed.business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitNativeMergeReview")
    val fresh = formalReceipts(scenario, session).filterNot(episode.formal.contains)
    fresh.size shouldBe episode.progress.selection.get.checks.size
    fresh.foreach(_._2.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded)
    if (episode.progress.selection.get.checks.exists(_.kind == WorkflowSbtValidation.Kind.TestOnly)) {
      val selected = fresh.find(_._2.identity == appendedCheck.identity).get._2.sbt.get
      selected.kind shouldBe WorkflowSbtValidation.Kind.TestOnly
      selected.selection.get.testClasses shouldBe Vector("fixture.BaselineSpec", "fixture.FutureSpec")
      selected.selection.get.completedSuites shouldBe 2
      selected.selection.get.testsRun should be > 0L
    }
    Files.readString(scenario.root.resolve("purpose-slot-2").resolve(_baseline)) shouldBe _baseline_new
    Files.readString(repository.resolve(_baseline)) shouldBe (if (ownership.nonEmpty) _ownership_old else _baseline_old)
    Files.readString(repository.resolve(_later)) shouldBe _later_new
    Files.readString(repository.resolve(_future)) shouldBe _future_new
    val waiting = _state(scenario, session)
    When("ordinary Advance repeats while the current Purpose awaits independent review")
    val repeatedreview = successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
    Then("the same review context and retained Session state are returned")
    repeatedreview.issuedWorkContext shouldBe
      reviewed.business[V.RepositorySyncAdvanceResult].issuedWorkContext
    _state(scenario, session) shouldBe waiting
    When("the current Purpose receives independent review and ordinary Advance installs its commit")
    _review(scenario, session, reviewed, "current")
    val futurereview = successful(advance(scenario, session))
    Then("one current Purpose commit is installed and later work remains available for its own review")
    futurereview.business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitNativeMergeReview")
    val installedcurrent = receipts(scenario, session).filter(receipt => receipt.payload.value.stage == "PurposeInstalled" &&
      receipt.payload.value.purposes.exists(_.cursor == 1)).maxBy(_.next.revision.value).payload.value.purposes.get
    installedcurrent.commits.size shouldBe episode.progress.commits.size + 1
    git(scenario.root, "-C", repository.toString, "show", "HEAD:" + _baseline) shouldBe _baseline_new.trim
    git(scenario.root, "-C", repository.toString, "show", "HEAD:" + _later) shouldBe _later_old.trim
    Files.readString(repository.resolve(_later)) shouldBe _later_new
    Files.readString(repository.resolve(_future)) shouldBe _future_new
    if (ownership.nonEmpty) {
      Files.readString(repository.resolve(_baseline)) shouldBe _ownership_new
      installedcurrent.projectedWorktree.toVector.flatten should contain(P.Content(_baseline, Some(_ownership_new)))
      installedcurrent.request shouldBe episode.progress.request
      installedcurrent.plan.get.groups.tail shouldBe episode.progress.plan.get.groups.tail
      installedcurrent.ownership shouldBe ownership
      Files.readString(scenario.root.resolve("purpose-slot-3").resolve(_baseline)) shouldBe _ownership_new
      val laterchecks = formalReceipts(scenario, session).filterNot(episode.formal.contains).filterNot(fresh.contains)
      laterchecks.size shouldBe episode.progress.selection.get.checks.size
      laterchecks.foreach(_._2.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Succeeded)
      val selected = laterchecks.find(_._2.identity == appendedCheck.identity).get._2.sbt.get
      selected.selection.get.testClasses shouldBe Vector("fixture.BaselineSpec", "fixture.FutureSpec")
      selected.selection.get.completedSuites shouldBe 2
      selected.selection.get.testsRun should be > 0L
    }
    When("the future Purpose receives independent review and ordinary Advance completes synchronization")
    _review(scenario, session, futurereview, "future")
    val terminal = successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
    Then("both actual local Purpose commits finish the original Session and its fixture-local bare synchronization")
    terminal.kind shouldBe "Terminal"
    current(scenario, session).lifecycle shouldBe Lifecycle.Completed
    git(scenario.root, "-C", repository.toString, "status", "--porcelain") shouldBe ""
    Files.readString(repository.resolve(_baseline)) shouldBe (if (ownership.nonEmpty) _ownership_new else _baseline_new)
    Files.readString(repository.resolve(_later)) shouldBe _later_new
    Files.readString(repository.resolve(_future)) shouldBe _future_new
    val head = git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD")
    git(scenario.root, "--git-dir=" + scenario.root.resolve("upstream.git"), "rev-parse", "refs/heads/main") shouldBe head
    if (ownership.nonEmpty) {
      git(scenario.root, "-C", repository.toString, "rev-parse", "HEAD^") shouldBe installedcurrent.commits.last
      git(scenario.root, "-C", repository.toString, "show", "HEAD:" + _baseline) shouldBe _ownership_new.trim
      git(scenario.root, "-C", repository.toString, "show", "HEAD:" + _future) shouldBe _future_new.trim
      installedcurrent.commits.last should not be head
    }
    episode.formal.foreach(value => formalReceipts(scenario, session) should contain(value))
    _work(scenario, episode.originalWorkIdentity) shouldBe episode.originalRequest
    stored(scenario).exists(raw => parse(raw).toOption.exists(_.findAllByKey("externalEffects").contains(Json.fromString("UNKNOWN")))) shouldBe true
    val completed = _state(scenario, session)
    When("ordinary Advance repeats after the original Session has completed")
    val repeatedterminal = successful(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
    Then("the same terminal result and completed Session state are returned")
    repeatedterminal shouldBe terminal
    _state(scenario, session) shouldBe completed
    val provenance = if (qualification.hcursor.get[String]("qualificationPhase").toOption.contains("completion"))
      Json.obj("initialInstalledCommand" -> episode.installedCommand.asJson,
        "initialDescriptionExit" -> episode.oldDescriptionExit.asJson, "newInstalledCommand" -> installed().toString.asJson)
      else Json.obj("oldInstalledCommand" -> episode.installedCommand.asJson,
        "oldDescriptionExit" -> episode.oldDescriptionExit.asJson, "newInstalledCommand" -> installed().toString.asJson)
    val evidence = Json.obj("session" -> session.asJson, "root" -> scenario.root.toString.asJson,
      "originalRequest" -> M.inputCodec.encode(episode.originalRequest),
      "freshReceipts" -> formalReceipts(scenario, session).filterNot(episode.formal.contains).asJson,
      "repairedProgress" -> P.repairProgressJson(repaired), "head" -> head.asJson, "terminal" -> json(terminal).asJson,
      "installedCurrentProgress" -> P.repairProgressJson(installedcurrent),
      "localPurposeCommits" -> (installedcurrent.commits :+ head).asJson,
      "finalTree" -> Json.obj(_baseline -> Files.readString(repository.resolve(_baseline)).asJson,
        _later -> Files.readString(repository.resolve(_later)).asJson, _future -> Files.readString(repository.resolve(_future)).asJson,
        "value.txt" -> Files.readString(repository.resolve("value.txt")).asJson),
      "originalFailureProgress" -> P.repairProgressJson(episode.progress),
      "originalFormalReceipts" -> episode.formal.asJson,
      "finalState" -> completed).deepMerge(provenance).deepMerge(qualification)
    if (ownership.isEmpty) Files.writeString(_episode_path.resolveSibling(_episode_path.getFileName.toString + ".result.json"), evidence.noSpaces,
      UTF_8, StandardOpenOption.CREATE_NEW)
    evidence
  }
  private def _current_from(raw: String): InstanceRecord = {
    val catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(RepositorySyncDefinitionBinding.bindC))))
    take(WorkflowInstanceRecordJsonV1.decodeC(raw, catalog))
  }
}
