package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.concurrent.atomic.AtomicReference
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalReceiptLimits, WorkflowMechanicalTransitionJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.{DoNotDiscover, GivenWhenThen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import RepositorySyncNativeProgressStore as N
import RepositorySyncPurpose as P
import RepositorySyncPurposePreparationContinuation as Previous
import RepositorySyncPurposePreparationIntentContinuation as I
import SplitPhaseExplicitPartitionFixture.take

/** Explicit before/after installed qualification in one tiny physical fixture.
  * No native row is seeded or edited. All directions, process observations and
  * independent reviewers belong to this fixture, never a real consumer Session.
  * @since Nov. 6, 2026
  * @version Nov. 6, 2026
  */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeRepositoryPurposePreparationIntentContinuationCliSpec extends AnyWordSpec with Matchers with GivenWhenThen
    with NativeRepositoryContinuationCliFixture {
  private val _first = "docs/preparation-first.md"
  private val _added = "docs/preparation-added.md"
  private val _initial_text = "First document baseline.\n"
  private val _first_text = _initial_text + "Independent first document purpose.\n"
  private val _staged_text = "New document staged before the later explanation.\n"
  private val _final_text = _staged_text + "Later worktree explanation retained after staging.\n"
  private val _stage = _select_stage()
  private lazy val _catalog = take(WorkflowInstanceRecordJsonV1.catalogC(Vector(take(RepositorySyncDefinitionBinding.bindC))))
  private lazy val _receipt_limits = take(WorkflowMechanicalReceiptLimits.createC(WorkflowMechanicalReceiptLimits.maximumBytes))

  private def _select_stage(): String = {
    Vector("Stage" -> "STAGE", "Directory" -> "DIRECTORY").foreach { (suffix, environment) =>
      val key = "smworkflow.spec.preparationIntent" + suffix
      sys.env.get("SM_WORKFLOW_SPEC_PREPARATION_INTENT_" + environment).foreach(value => System.setProperty(key, value))
    }
    Vector("installedCommand" -> "SM_WORKFLOW_SPEC_INSTALLED_COMMAND", "installations" -> "SM_WORKFLOW_SPEC_INSTALLATIONS")
      .foreach { (suffix, environment) => sys.env.get(environment).foreach(value => System.setProperty("smworkflow.spec." + suffix, value)) }
    val selected = sys.props.getOrElse("smworkflow.spec.preparationIntentStage", fail("Select explicit preparation-intent before|after stage"))
    require(Set("before", "after")(selected), "Select preparation-intent before|after")
    selected
  }
  private def _installed: Path = {
    val command = Path.of(sys.props.getOrElse("smworkflow.spec.installedCommand", fail("Select the actual absolute installed command")))
    require(command.isAbsolute && command.normalize == command && Files.isRegularFile(command) && Files.isExecutable(command),
      "Select the actual installed old/new CLI; source launcher fallback is forbidden")
    command
  }
  private def _episode_path(name: String = "preparation-intent"): Path = {
    val directory = Path.of(sys.props.getOrElse("smworkflow.spec.preparationIntentDirectory", fail("Select an existing absolute episode directory outside Git")))
    require(directory.isAbsolute && directory.normalize == directory && Files.isDirectory(directory))
    var ancestor = Option(directory)
    while (ancestor.nonEmpty) {
      require(!Files.exists(ancestor.get.resolve(".git")), "Retain the installed episode outside Git")
      ancestor = Option(ancestor.get.getParent)
    }
    directory.resolve(name + ".json")
  }
  private def _read(path: Path): Json = parse(Files.readString(path, UTF_8)).fold(error => fail(error.message), identity)
  private def _retain(path: Path, value: Json): Unit = {
    Files.writeString(path, value.noSpaces, UTF_8, StandardOpenOption.CREATE_NEW)
    ()
  }
  private def _success(result: F.Result): F.Result = {
    withClue(result.invocation.logs) { result.exitCode shouldBe 0 }
    result.invocation.argv.head shouldBe _installed.toString
    result
  }
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
      "firstText" -> Files.readString(repository.resolve(_first), UTF_8).asJson,
      "addedText" -> Files.readString(repository.resolve(_added), UTF_8).asJson,
      "remoteTip" -> git(scenario.root, "--git-dir=" + scenario.host.target.endpoint, "rev-parse", "refs/heads/main").asJson)
  }
  private def _host_plan(scenario: F.Scenario): Json = {
    val base = process_plan(scenario.root, "Equal", scenario.host.goal)
    val original = _array(base, "gitPrograms")
    val programs = original.filter(_.hcursor.get[String]("target").toOption.contains("purpose-slot-1")).map { program =>
      program.mapObject(fields => fields.add("target", "purpose-slot-2".asJson)
        .add("identity", (fields("identity").get.asString.get + "-p2").asJson)
        .add("capability", (fields("capability").get.asString.get + "-p2").asJson))
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
  private def _fresh(): F.Scenario = {
    _installed
    val scenario = fresh("preparation-intent-old-installed")
    val descriptor = scenario.root.resolve(".textus/assembly.yaml")
    val original = Files.readString(descriptor, UTF_8)
    original should include("      id: participant\n")
    Files.writeString(descriptor, original.replace("      id: participant\n", "      id: sm-workflow-local\n"), UTF_8)
    val slot = P.Slot("purpose-slot-1", Vector.empty,
      GoalPhaseStepAssessment.Policy("preparation-document", "documentation", "required", "off", "off", Vector.empty))
    configure(scenario, maintenance_parameters(review = true) ++ Map(
      "sm-workflow.repository-sync.dirty-purposes" -> P.Policy(Vector(slot, slot.copy(workspace = "purpose-slot-2")), Vector.empty).asJson.noSpaces,
      "textus.subsystem.process-execution.plan" -> _host_plan(scenario).noSpaces))
    val repository = _repository(scenario)
    Files.writeString(repository.resolve(_first), _initial_text, UTF_8)
    git(scenario.root, "-C", repository.toString, "add", "--", _first)
    git(scenario.root, "-C", repository.toString, "commit", "--no-verify", "--no-gpg-sign", "-m", "Preparation intent document baseline")
    git(scenario.root, "-C", repository.toString, "push", "--no-verify", "--", scenario.host.target.endpoint, "HEAD:refs/heads/main")
    Files.writeString(repository.resolve(_first), _first_text, UTF_8)
    Files.writeString(repository.resolve(_added), _staged_text, UTF_8)
    git(scenario.root, "-C", repository.toString, "add", "--", _added)
    Files.writeString(repository.resolve(_added), _final_text, UTF_8)
    scenario
  }
  private def _issue(scenario: F.Scenario): String = {
    val session = start(scenario)
    _success(advance(scenario, session)).business[V.RepositorySyncAdvanceResult].submitOperation shouldBe Some("submitRepositoryPurposeResult")
    val request = purpose_inputs(scenario).head
    request.files.map(_.path).toSet shouldBe Set(_first, _added)
    val added = request.files.find(_.path == _added).get
    (added.indexCode, added.worktreeCode, added.original, added.indexed, added.worktree) shouldBe
      (("A", "M", None, Some(_staged_text), Some(_final_text)))
    val result = P.Result("resolved", "Two independently intended document commits",
      Vector(P.Group("first-document", "Retain the first document", "docs: retain first document", Vector.empty,
        Vector(P.Content(_first, Some(_first_text))), Vector.empty, "Independent first explanation"),
        P.Group("added-document", "Retain the added document and appended explanation", "docs: retain added document", Vector("first-document"),
          Vector(P.Content(_added, Some(_final_text))), Vector.empty, "Preserve both the staged addition and its later worktree explanation")), Vector.empty)
    _success(submit_purpose(scenario, session, result)).business[V.RepositorySyncConflictSubmissionResult].accepted shouldBe true
    session
  }
  private def _review(scenario: F.Scenario, session: String, issued: F.Result, ordinal: Int): Unit = {
    val result = _success(issued).business[V.RepositorySyncAdvanceResult]
    result.submitOperation shouldBe Some("submitNativeMergeReview")
    result.review.get.selectedChecks shouldBe Vector.empty
    val directory = Files.createTempDirectory(scenario.root, "fixture-preparation-independent-review-")
    val reference = V.WorkflowArtifactReference("fixture-independent-preparation-review-" + ordinal, "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("fixture-independent-document-reviewer"), None, None)
    val verdict = V.RepositorySyncNativeMergeReviewResult("accept",
      "Independent fixture reviewer examined this exact ordinary document candidate and its ordered purpose", Vector.empty)
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

  /** Observe the owned invocation and its actual descendants while it runs.
    * This evidence remains separate from the fixture Human's explicit lifecycle
    * attestation and acceptance of UNKNOWN effects. A root exit alone is unused.
    */
  private def _observed_advance(scenario: F.Scenario, session: String): (F.Result, Json) = {
    val result = new AtomicReference[Either[Throwable, F.Result]]()
    val count = scenario.invocations.size
    val handles = scala.collection.mutable.LinkedHashMap.empty[Long, ProcessHandle]
    val worker = new Thread(() => {
      try result.set(Right(advance(scenario, session)))
      catch { case error: Throwable => result.set(Left(error)) }
    }, "fixture-owned-preparation-invocation")
    worker.start()
    while (worker.isAlive) {
      scenario.invocations.drop(count).foreach { invocation =>
        val root = ProcessHandle.of(invocation.pid)
        if (root.isPresent) {
          handles.update(root.get.pid(), root.get)
          val descendants = root.get.descendants()
          try descendants.forEach(handle => { handles.update(handle.pid(), handle); () }) finally descendants.close()
        }
      }
      Thread.sleep(10)
    }
    worker.join()
    val actual = result.get().fold(throw _, identity)
    handles.keySet should contain(actual.invocation.pid)
    handles.values.foreach(handle => handle.isAlive shouldBe false)
    val observed = handles.values.toVector.map(handle => Json.obj("pid" -> handle.pid().asJson,
      "startedAt" -> handle.info().startInstant().map[String](_.toString).orElse("").asJson,
      "aliveAfterInvocation" -> handle.isAlive.asJson))
    (actual, Json.obj("observedOwnedProcessTree" -> observed.asJson,
      "invocationRootPid" -> actual.invocation.pid.asJson,
      "independentFixtureHumanAttestation" -> "The fixture independently settles this controlled invocation and accepts its original UNKNOWN effects for the exact described target; observed process settlement does not classify the old effects as unexecuted.".asJson))
  }
  private def _reopen(episode: Json): F.Scenario = {
    val root = Path.of(episode.hcursor.get[String]("root").toOption.get)
    val logs = Files.list(root.resolve("logs"))
    val starting = try math.max(1000, logs.filter(path => path.getFileName.toString.matches("[0-9]+\\.(stdout\\.json|stderr)"))
      .mapToInt(path => path.getFileName.toString.takeWhile(_.isDigit).toInt).max().orElse(999) + 1) finally logs.close()
    F.reopenInstalledRepository(root, take(ThreeWorkflowRuntimeParameters.decodeDefinitionC(episode.hcursor.get[Json]("host").toOption.get)),
      episode.hcursor.get[GoalPhaseOrderedProgression.Plan]("plan").toOption.get, starting)
  }
  private def _description(scenario: F.Scenario, session: String): F.Result =
    _success(scenario.repositoryCall(Previous.describeOperation, Vector("--sessionId", session)))
  private def _adopt(scenario: F.Scenario, input: V.RepositorySyncPurposePreparationContinuationAdoptionInput): F.Result =
    scenario.repositoryCall(Previous.adoptionOperation, Vector("--input", json(input)))
  private def _direction(scenario: F.Scenario, session: String, described: F.Result, choice: String,
    lifecycle: Json): V.RepositorySyncPurposePreparationContinuationAdoptionInput = {
    val description = described.business[V.RepositorySyncPurposePreparationContinuationDescriptionResult]
    val target = take(I.targetC(description.target))
    val directory = Files.createTempDirectory(scenario.root, "fixture-preparation-independent-human-")
    val reference = V.WorkflowArtifactReference(directory.getFileName.toString, "1")
    val rationale = "Fixture-owned independent Human direction for " + choice +
      ". Independently examined the owned invocation settlement and unchanged native source. Accept original UNKNOWN effects for only this exact target. This is no real consumer consent."
    _retain(directory.resolve("independent-lifecycle-attestation.json"), lifecycle)
    val answer = V.RepositorySyncPurposePreparationContinuationAnswer(choice, rationale)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some(target.context.participantIdentity), None, None)
    val preparation = V.WorkflowEvidencePreparationInput("RepositorySync", "purpose-preparation-continuation",
      described.invocation.stdout.toUri.toASCIIString, save(directory, "answer.json", json(answer)),
      save(directory, "reference.json", json(reference)), save(directory, "execution.json", json(execution)),
      "normal-development-host", java.time.Instant.now().toString, "entity:smworkflow.cli.host_evidence", Some(target.context.participantIdentity))
    val prepared = _success(scenario.prepareHostInput("prepareEvidence", take(new org.goldenport.record.io.RecordDecoder().json(json(preparation)))))
    scenario.selectPreparedImport(prepared)
    V.RepositorySyncPurposePreparationContinuationAdoptionInput(session, description.target, choice, rationale, reference, execution)
  }

  "RS-PREP-02 installed resultless preparation" should {
    if (_stage == "before") {
      "retain the old installed second-purpose intent after a staged addition acquires newer worktree text" in {
        Given("the explicitly selected old installed CLI and a tiny document-only Git fixture")
        _installed
        val destination = _episode_path()
        Files.exists(destination) shouldBe false
        val scenario = _fresh()
        val originalhead = git(scenario.root, "-C", _repository(scenario).toString, "rev-parse", "HEAD")
        val session = _issue(scenario)
        val first = _success(advance(scenario, session))
        _review(scenario, session, first, 1)
        When("ordinary old installed Advance installs the first purpose then genuinely attempts the staged-added second document")
        val (failed, lifecycle) = _observed_advance(scenario, session)
        Then("the actual old invocation fails after one completed commit and retains an honest resultless preparation")
        failed.exitCode should not be 0
        failed.invocation.argv.head shouldBe _installed.toString
        current(scenario, session).currentProgression shouldBe Some(ProgressionReference("sync.PurposePreparing"))
        current(scenario, session).suspension shouldBe None
        _stages(scenario, session, "PurposePreparing").size shouldBe 2
        _stages(scenario, session, "PurposePrepared").size shouldBe 1
        _stages(scenario, session, "PurposeInstalled").size shouldBe 1
        _stages(scenario, session, "PurposeUnfinished") shouldBe empty
        val intent = _stages(scenario, session, "PurposePreparing").last
        val progress = intent.payload.value.purposes.get
        (progress.cursor, progress.attempt, progress.slotCursor, progress.baselineTree, progress.candidate) shouldBe ((1, 0, 1, None, None))
        progress.commits.size shouldBe 1
        val firstcommit = progress.commits.head
        git(scenario.root, "-C", _repository(scenario).toString, "rev-parse", "HEAD") shouldBe firstcommit
        git(scenario.root, "-C", _repository(scenario).toString, "show", ":" + _added) shouldBe _staged_text.trim
        Files.readString(_repository(scenario).resolve(_added), UTF_8) shouldBe _final_text
        receipts(scenario, session).exists(_.reference.identity.endsWith("purpose-2-prepare-result")) shouldBe false
        val beforestate = _state(scenario, session)
        When("the old runtime collects the actual resultless intent through ordinary public Advance")
        val waiting = _success(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
        Then("collection creates no retry result receipt or new commit and preserves original UNKNOWN")
        waiting.kind shouldBe "Wait"
        waiting.condition shouldBe Some("purpose-effect-unknown")
        _state(scenario, session) shouldBe beforestate
        _retain(destination, Json.obj("schemaVersion" -> "preparation-intent-installed-episode.v1".asJson,
          "root" -> scenario.root.toString.asJson, "host" -> ThreeWorkflowRuntimeParameters.encode(scenario.host),
          "plan" -> scenario.plan.asJson, "session" -> session.asJson, "oldInstalledCommand" -> _installed.toString.asJson,
          "originalHead" -> originalhead.asJson, "firstPurposeCommit" -> firstcommit.asJson,
          "beforeState" -> beforestate, "progressSnapshot" -> P.repairProgressJson(progress).noSpaces.asJson,
          "originalNativeReceipts" -> _native(scenario, session), "independentLifecycleEvidence" -> lifecycle,
          "oldFailure" -> Json.obj("exitCode" -> failed.exitCode.asJson, "argv" -> failed.invocation.argv.asJson,
            "pid" -> failed.invocation.pid.asJson, "stdout" -> failed.invocation.stdout.toString.asJson,
            "stderr" -> failed.invocation.stderr.toString.asJson), "oldFailureLogs" -> failed.invocation.logs.asJson,
          "originalResultStatus" -> "UNKNOWN".asJson))
      }
    }
    if (_stage == "after") {
      "adopt independently provisioned exact Human direction on the same Session and converge without replaying its completed prefix" in {
        Given("the retained old installed episode and the newly selected installed runtime on the exact same physical fixture")
        _installed
        val episode = _read(_episode_path())
        episode.hcursor.get[String]("schemaVersion").toOption shouldBe Some("preparation-intent-installed-episode.v1")
        val scenario = _reopen(episode)
        val session = episode.hcursor.get[String]("session").toOption.get
        val beforestate = episode.hcursor.get[Json]("beforeState").toOption.get
        val originalnative = episode.hcursor.get[Json]("originalNativeReceipts").toOption.get
        val firstcommit = episode.hcursor.get[String]("firstPurposeCommit").toOption.get
        val originalhead = episode.hcursor.get[String]("originalHead").toOption.get
        val lifecycle = episode.hcursor.get[Json]("independentLifecycleEvidence").toOption.get
        lifecycle.hcursor.get[String]("independentFixtureHumanAttestation").toOption.get should include("independently")
        _state(scenario, session) shouldBe beforestate
        val originalintent = _stages(scenario, session, "PurposePreparing").last
        val original = originalintent.payload.value.purposes.get
        P.repairProgressJson(original).noSpaces shouldBe episode.hcursor.get[String]("progressSnapshot").toOption.get
        When("ordinary new Advance first collects UNKNOWN and the public describe operation observes the unchanged intent")
        _success(advance(scenario, session)).business[V.RepositorySyncAdvanceResult].condition shouldBe Some("purpose-effect-unknown")
        val described = _description(scenario, session)
        val description = described.business[V.RepositorySyncPurposePreparationContinuationDescriptionResult]
        val target = take(I.targetC(description.target))
        Then("description binds original absence and initial later-group counters without creating a process result or mutation")
        description.outcome shouldBe "adoption-required"
        description.choices shouldBe I.choices
        target.originalResultStatus shouldBe "UNKNOWN"
        target.retainedRejection shouldBe None
        target.intentRevision shouldBe originalintent.next.revision.value.toString
        target.frontierRevision shouldBe target.intentRevision
        target.progressSnapshot shouldBe P.repairProgressJson(original).noSpaces
        (original.cursor, original.attempt, original.baselineTree, original.commits) shouldBe ((1, 0, None, Vector(firstcommit)))
        take(I.targetC(_description(scenario, session).business[V.RepositorySyncPurposePreparationContinuationDescriptionResult].target)) shouldBe target
        _state(scenario, session) shouldBe beforestate
        val noauthority = V.RepositorySyncPurposePreparationContinuationAdoptionInput(session, description.target, I.choices.head,
          "No independently installed Human evidence exists for this reference", V.WorkflowArtifactReference("unprovisioned-fixture-human", "1"),
          V.WorkflowExecutionEvidence(Vector.empty, Some(target.context.participantIdentity), None, None))
        When("adoption has no independently provisioned exact Human evidence or uses the old affirmative choice")
        _adopt(scenario, noauthority).exitCode should not be 0
        _adopt(scenario, noauthority.copy(choice = Previous.choices.head)).exitCode should not be 0
        Then("neither condition grants preparation authority and the root HEAD index and native history stay unchanged")
        _state(scenario, session) shouldBe beforestate
        val deferred = _direction(scenario, session, described, "Defer", lifecycle)
        When("the independently provisioned fixture Human defers this exact target")
        _success(_adopt(scenario, deferred)).business[V.RepositorySyncPurposePreparationContinuationAdoptionResult].outcome shouldBe "deferred"
        Then("Defer creates no event or effect")
        _state(scenario, session) shouldBe beforestate
        val authorized = _direction(scenario, session, described, I.choices.head, lifecycle)
        val foreign = I.targetRecord(target.copy(sessionId = session + "-foreign"))
        When("the authorized input crosses its Session target choice participant or evidence reference")
        Vector(authorized.copy(sessionId = session + "-foreign"), authorized.copy(target = foreign),
          authorized.copy(choice = "AuthorizeNewPreparation"), authorized.copy(rationale = authorized.rationale + " changed"),
          authorized.copy(directionReference = authorized.directionReference.copy(identity = "foreign-reference")),
          authorized.copy(executionEvidence = authorized.executionEvidence.copy(workerIdentity = Some("foreign-worker"))))
          .foreach { input => _adopt(scenario, input).exitCode should not be 0; _state(scenario, session) shouldBe beforestate }
        Then("every crossed input refuses and creates no namespace or Git mutation")
        _state(scenario, session) shouldBe beforestate
        When("the worktree changes after description but before the exact authorized adoption")
        Files.writeString(_repository(scenario).resolve(_added), _final_text + "Unaccepted source drift.\n", UTF_8)
        try {
          _adopt(scenario, authorized).exitCode should not be 0
          _native(scenario, session) shouldBe originalnative
        } finally Files.writeString(_repository(scenario).resolve(_added), _final_text, UTF_8)
        Then("stale source cannot be adopted and restoring the actual original bytes restores the original passive observation")
        _state(scenario, session) shouldBe beforestate
        val inputpath = Files.createTempFile(scenario.root, "exact-preparation-input-", ".json")
        Files.writeString(inputpath, json(authorized), UTF_8)
        When("the complete exact input Resource records one Human-authorized new preparation namespace")
        val adopted = _success(scenario.repositoryCall(Previous.adoptionOperation, Vector("--inputResource", inputpath.toUri.toASCIIString)))
          .business[V.RepositorySyncPurposePreparationContinuationAdoptionResult]
        val admitted = current(scenario, session)
        val saved = _stages(scenario, session, "PurposeGrouped").last.payload.value
        Then("adoption appends only the declared native transition and only the progress namespace changes")
        adopted.outcome shouldBe "adopted"
        admitted.currentProgression shouldBe Some(ProgressionReference("sync.PurposeGrouped"))
        admitted.revision shouldBe originalintent.next.revision.next
        admitted.history.dropRight(1) shouldBe originalintent.next.history
        saved.purposePreparation shouldBe None
        saved.purposePreparationIntent.map(_.target) shouldBe Some(target)
        saved.purposes.get shouldBe original.copy(preparationNamespace = Some(I.namespace(target)))
        val admittedstate = _state(scenario, session)
        Vector("head", "indexTree", "status", "firstText", "addedText", "remoteTip").foreach(key =>
          admittedstate.hcursor.get[Json](key).toOption shouldBe beforestate.hcursor.get[Json](key).toOption)
        _stages(scenario, session, "PurposePreparing").size shouldBe 2
        _stages(scenario, session, "PurposePrepared").size shouldBe 1
        When("the same exact authorization is collected through inline input")
        _success(_adopt(scenario, authorized)).business[V.RepositorySyncPurposePreparationContinuationAdoptionResult].outcome shouldBe "already-adopted"
        Then("inline and Resource inputs share one idempotent adoption and no preparation occurs passively")
        _state(scenario, session) shouldBe admittedstate
        When("a separate ordinary Advance performs new preparation and requests independent review of the original second purpose")
        val second = _success(advance(scenario, session))
        Then("the successful first purpose is never replayed and the original resultless intent remains literal")
        _stages(scenario, session, "PurposePreparing").size shouldBe 3
        _stages(scenario, session, "PurposePrepared").size shouldBe 2
        _stages(scenario, session, "PurposeInstalling").size shouldBe 1
        _stages(scenario, session, "PurposeInstalled").size shouldBe 1
        receipts(scenario, session) should contain(originalintent)
        _stages(scenario, session, "PurposePrepared").last.payload.value.purposes.get.commits shouldBe Vector(firstcommit)
        Files.readString(_repository(scenario).resolve(_added), UTF_8) shouldBe _final_text
        _review(scenario, session, second, 2)
        When("ordinary native continuation installs the reviewed second document and completes local bare-remote convergence")
        val terminal = _success(advance(scenario, session)).business[V.RepositorySyncAdvanceResult]
        val terminalstate = _state(scenario, session)
        val head = terminalstate.hcursor.get[String]("head").toOption.get
        Then("the same original Session has exactly two ordered commits and all original UNKNOWN observations remain preserved")
        terminal.kind shouldBe "Terminal"
        current(scenario, session).lifecycle shouldBe Lifecycle.Completed
        terminalstate.hcursor.get[String]("status").toOption shouldBe Some("")
        terminalstate.hcursor.get[String]("remoteTip").toOption shouldBe Some(head)
        git(scenario.root, "-C", _repository(scenario).toString, "rev-parse", "HEAD^") shouldBe firstcommit
        git(scenario.root, "-C", _repository(scenario).toString, "rev-list", "--count", originalhead + "..HEAD") shouldBe "2"
        git(scenario.root, "-C", _repository(scenario).toString, "show", "HEAD:" + _added) shouldBe _final_text.trim
        _stages(scenario, session, "PurposeInventory").size shouldBe 1
        purpose_inputs(scenario).size shouldBe 1
        _stages(scenario, session, "PurposeInstalled").size shouldBe 2
        _stages(scenario, session, "PurposeInstalled").last.payload.value.purposePreparationIntent shouldBe None
        _stages(scenario, session, "PurposeInstalled").last.payload.value.purposes.get.preparationNamespace shouldBe None
        val allnative = _native(scenario, session).asArray.get
        originalnative.asArray.get.foreach(value => allnative should contain(value))
        receipts(scenario, session).exists(_.reference.identity.endsWith("purpose-2-prepare-result")) shouldBe false
        Files.exists(scenario.root.resolve("sbt-invocations.txt")) shouldBe false
        When("terminal collection and the identical old adopted input repeat")
        _success(advance(scenario, session)).business[V.RepositorySyncAdvanceResult] shouldBe terminal
        _success(_adopt(scenario, authorized)).business[V.RepositorySyncPurposePreparationContinuationAdoptionResult].outcome shouldBe "already-adopted"
        Then("collection creates no additional commit receipt capture check or event")
        _state(scenario, session) shouldBe terminalstate
        _retain(_episode_path("preparation-intent.result"), Json.obj("originalEpisode" -> episode,
          "installedCommand" -> _installed.toString.asJson, "publicTarget" -> target.asJson,
          "admittedState" -> admittedstate, "adoptedInput" -> parse(json(authorized)).toOption.get,
          "terminalState" -> terminalstate, "terminal" -> parse(json(terminal)).toOption.get,
          "originalUnknownPreserved" -> true.asJson))
      }
    }
  }
}
