package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, StandardOpenOption}
import java.time.LocalDate
import org.goldenport.cncf.workflow.{WorkflowInstanceRecordJsonV1, WorkflowMechanicalTransition, WorkflowMechanicalTransitionJsonV1}
import org.goldenport.record.RecordPresentable
import org.scalatest.DoNotDiscover
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseSourceHistoryNormalization as N
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Explicit installed qualification through the scenario's normal assembly and
  * launcher. Policy, termination and independent review facts are test-owned;
  * these isolated episodes establish no live consumer consent.
  * @since   Nov.  5, 2026
  * @version Nov.  6, 2026
  */
private[admission] abstract class NativeGoalSourceHistoryNormalizationResourceCliSupport extends NativeGoalSourceHistoryRecoveryCliSupport {

  override protected def _call(fixture: Implemented, service: String, operation: String,
    arguments: Vector[String]): F.Result = sys.props.get("smworkflow.spec.installedCommand") match {
    case None => super._call(fixture, service, operation, arguments)
    case Some(command) => fixture.scenario.callInstalled(Path.of(command), service, operation, arguments,
      assemblyDescriptor = Some(fixture.scenario.root.resolve(".textus/assembly.yaml")))
  }

  private def _normalization_description(fixture: Implemented): F.Result =
    _goal(fixture, N.describeOperation, Vector("--sessionId", fixture.request.sessionId))

  private def _normalization_direction(fixture: Implemented, described: F.Result): V.GoalPhaseSourceHistoryNormalizationAdoptionInput = {
    val description = described.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult]
    val directory = Files.createTempDirectory(fixture.scenario.root, "resource-policy-direction-")
    val reference = V.WorkflowArtifactReference(directory.getFileName.toString + ":held-policy", "1")
    val answer = V.GoalPhaseSourceHistoryNormalizationAnswer(N.choices.head,
      "Specification-owned existing policy and completed old invocation; preserve the unanswered Human", true)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
    def _file_(name: String, value: RecordPresentable): String = {
      val path = directory.resolve(name + ".json")
      Files.writeString(path, _json(value), UTF_8)
      path.toUri.toASCIIString
    }
    val preparation = V.WorkflowEvidencePreparationInput("GoalPhase", "source-history-normalization",
      described.invocation.stdout.toUri.toASCIIString, _file_("answer", answer), _file_("reference", reference),
      _file_("execution", execution), "normal-development-host", "2026-10-07T00:00:00Z",
      "entity:smworkflow.cli.host_evidence", Some("participant"))
    _select_import(fixture, _call(fixture, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(preparation))))
    V.GoalPhaseSourceHistoryNormalizationAdoptionInput(description.sessionId, description.target, answer.choice,
      answer.rationale, reference, answer.priorExecutionEnded)
  }

  private def _old_result_absent(fixture: Implemented, target: N.Target): Unit = {
    val identity = target.recovery.sessionId + ":ordered-candidate:" + target.recovery.sourceRevision +
      ":source-history-result-" + target.recovery.intentIndex
    _stored_json(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog,
      GoalPhaseNativeSourceHistory.codec).toOption).exists(_.reference.identity == identity) shouldBe false
  }

  private def _prefix_reconstruct(fixture: Implemented, plan: GoalPhaseOrderedProgression.Plan): F.Result = {
    val input = V.GoalPhaseDevelopmentReconstructionInput(fixture.request.sessionId,
      "verification-inputs/phase.md", "verification-inputs/checklist.md", GoalPhaseNativeMetadata.record(
        GoalPhasePlanRevision.Proposal("prefix-current-source", "1", "Same actual tiny Phase and unchanged raw Plan",
          plan.copy(effectiveAcceptance = None)).asJson),
      "SPECIFICATION-OWNED same-Session common reconstruction using current source under the existing policy")
    val request = Files.createTempFile(fixture.scenario.root, "prefix-reconstruction-input-", ".json")
    Files.writeString(request, _json(input), UTF_8)
    _goal(fixture, GoalPhaseDevelopmentReconstruction.operation, Vector("--inputResource", request.toUri.toASCIIString))
  }

  private def _prefix_source_facts(fixture: Implemented): Vector[String] = _stored_json(fixture).filter(raw =>
    WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, GoalPhaseNativeSourceHistory.codec).isSuccess).sorted

  private def _prefix_question(fixture: Implemented, identity: String): (String, org.goldenport.cncf.workflow.ContinuationRuntimePersistence.Record) = {
    val rows = _stored_json(fixture).distinct
    val questions = rows.filter(raw => org.goldenport.cncf.workflow.WorkflowDecisionJsonV1.decodeC(
      raw, GoalPhaseOrderedHumanDecision.inputCodec).toOption.exists(_.current match {
        case decision: org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation.Decision[?] =>
          decision.request.continuationId.value == identity
        case _ => false
      }))
    val statuses = rows.flatMap(raw => org.goldenport.cncf.workflow.ContinuationRecordJsonV1.decodeC(raw).toOption)
      .filter(_.continuation.continuationId.value == identity)
    questions.size shouldBe 1
    statuses.size shouldBe 1
    questions.head -> statuses.head
  }

  /** This constant leaf deliberately crosses a parent-owned publication boundary.
    * The locator is only a test caller's retained observation, never authority.
    */
  protected final def prefix_reconstruction_episode(mode: String, locator: Path): Unit = {
    require(Set("capture-old", "continue-new")(mode) && locator.isAbsolute && locator.normalize == locator &&
      Files.isDirectory(locator.getParent), "Select capture-old/continue-new and an explicit absolute caller-owned locator")
    val installed = sys.props.get("smworkflow.spec.installedCommand").map(Path.of(_)).getOrElse(
      fail("This two-stage qualification requires the normal installed executable"))
    installed.isAbsolute shouldBe true
    Files.isExecutable(installed) shouldBe true
    if (mode == "capture-old") _capture_prefix_episode(locator) else _continue_prefix_episode(locator)
  }

  private def _capture_prefix_episode(locator: Path): Unit = {
    Given("SHR-CURRENT-SOURCE-PREFIX: the still-published normal runtime and a tiny two-Step Nov5 episode before any new publication")
    Files.exists(locator) shouldBe false
    val episode = _document_episode("source-prefix-current-reconstruction", later = true,
      virtualStartAt = Some("2026-11-05T00:00:00Z"), currentSourceReconstruction = true)
    val fixture = episode.fixture
    val issued = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    issued.kind shouldBe "Human"
    val recovery = take(GoalPhaseSourceHistoryRecovery.targetC(issued.human.get.sourceHistoryRecovery.get))
    val documentpaths = Vector("verification-inputs/phase.md", "verification-inputs/checklist.md")
    episode.plan.steps.foreach(step => documentpaths.foreach(path => step.ownedPaths should contain(path)))
    recovery.candidatePaths shouldBe (_prefix_paths :+ sourcePath) ++ documentpaths
    recovery.completedPrefix.size shouldBe 4
    recovery.completedPrefix.foreach(_.result.updatedOn shouldBe "2026-11-05")
    val repository = Path.of(fixture.scenario.host.target.root)
    val unchanged = _prefix_paths.drop(1).map(path => (path, fileBytes(repository.resolve(path)),
      Files.getLastModifiedTime(repository.resolve(path))))
    val first = repository.resolve(_prefix_paths.head)
    val currentrule = take(WorkflowSourceHistoryRule.decodeC(recovery.historyPolicy))
    val corrected = Files.readString(first).replace("= 42", "= 43")
    Files.writeString(first, take(WorkflowSourceHistoryRule.applyC(currentrule, WorkflowSourceHistoryRule.Input(
      _prefix_paths.head, corrected, LocalDate.of(2026, 11, 6), None, None, true))).text, UTF_8)
    Files.writeString(fixture.source, _later_corrected_text, UTF_8)
    val config = fixture.scenario.root.resolve(".textus/config.json")
    Files.writeString(config, parse(Files.readString(config)).toOption.get.mapObject(_.add(
      "textus.clock.virtual-start-at", Json.fromString("2026-11-06T00:00:00Z"))).noSpaces, UTF_8)

    Given("legitimate current Nov6 source and independent fixture-owned same-authority normalization preparation")
    val descriptioncall = _normalization_description(fixture)
    val description = descriptioncall.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult]
    val target = take(N.targetC(description.target))
    target.recovery shouldBe recovery
    val adoption = _normalization_direction(fixture, descriptioncall)
    val request = fixture.scenario.root.resolve("prefix-normalization-adoption.json")
    Files.writeString(request, _json(adoption), UTF_8, StandardOpenOption.CREATE_NEW)
    _goal(fixture, N.adoptionOperation, Vector("--inputResource", request.toUri.toASCIIString))
      .business[V.GoalPhaseSourceHistoryNormalizationAdoptionResult].outcome shouldBe "adopted"
    When("separate ordinary Advance formally records the old-date prefix mismatch before touching the affected source")
    _advance(fixture).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
    val reference = WorkflowMechanicalTransition.Reference(N.reference(target).identity + ":reconciliation-result", "1")
    val failed = _receipt(fixture, reference, N.observationCodec)
    failed.payload.value.outcome shouldBe Some("failed")
    failed.next shouldBe _current(fixture)
    Files.readString(fixture.source) shouldBe _later_corrected_text
    executions(fixture.scenario).size shouldBe 1
    val historyfacts = _prefix_source_facts(fixture)
    val completedfacts = _completed_facts(fixture)
    val unknown = _receipt(fixture, WorkflowMechanicalTransition.Reference(recovery.intentIdentity,
      recovery.intentRevision), GoalPhaseNativeSourceHistory.codec)
    unknown.payload.value.outcome shouldBe None
    _old_result_absent(fixture, target)

    When("the old existing reconstruct Operation receives the unchanged raw Plan and actual Phase and checklist")
    val questioncall = _prefix_reconstruct(fixture, episode.plan)
    val question = questioncall.business[V.GoalPhaseAdvanceResult]
    Then("the old runtime retains the exact technical Defer/Cancel Human with no new source effects")
    question.kind shouldBe "Human"
    question.human.get.choices shouldBe Vector("Defer", "Cancel")
    question.human.get.developmentReconstruction.nonEmpty shouldBe true
    question.repairProgress shouldBe issued.repairProgress
    val pending = _current(fixture)
    val questionidentity = pending.suspension.get.continuationIdentity.value
    val (body, status) = _prefix_question(fixture, questionidentity)
    status.status shouldBe org.goldenport.cncf.workflow.ContinuationRuntimePersistence.Status.Available
    _prefix_reconstruct(fixture, episode.plan).business[V.GoalPhaseAdvanceResult] shouldBe question
    _current(fixture) shouldBe pending
    _prefix_source_facts(fixture) shouldBe historyfacts
    _completed_facts(fixture) shouldBe completedfacts
    unchanged.foreach { (path, bytes, time) =>
      fileBytes(repository.resolve(path)) shouldBe bytes
      Files.getLastModifiedTime(repository.resolve(path)) shouldBe time
    }
    val sourcefacts = (_prefix_paths :+ sourcePath).map(path => Json.obj("path" -> path.asJson,
      "text" -> Files.readString(repository.resolve(path)).asJson,
      "modified" -> Files.getLastModifiedTime(repository.resolve(path)).toString.asJson))
    val record = Json.obj("schemaVersion" -> "sm-workflow.spec.source-prefix-reconstruction.v1".asJson,
      "root" -> fixture.scenario.root.toString.asJson, "task" -> fixture.task.toString.asJson,
      "session" -> fixture.request.sessionId.asJson, "plan" -> episode.plan.asJson,
      "completedRequest" -> _json(episode.completed).asJson, "question" -> _json(question).asJson,
      "questionIdentity" -> questionidentity.asJson, "pendingRevision" -> pending.revision.value.toString.asJson,
      "questionBody" -> body.asJson,
      "questionStatus" -> take(org.goldenport.cncf.workflow.ContinuationRecordJsonV1.encodeC(status)).asJson,
      "pendingRecord" -> take(WorkflowInstanceRecordJsonV1.encodeC(pending)).asJson,
      "sourceFacts" -> historyfacts.asJson, "completedFacts" -> completedfacts.asJson,
      "failedResult" -> take(WorkflowMechanicalTransitionJsonV1.encodeC(failed, N.observationCodec)).asJson,
      "unknown" -> take(WorkflowMechanicalTransitionJsonV1.encodeC(unknown, GoalPhaseNativeSourceHistory.codec)).asJson,
      "sources" -> Json.fromValues(sourcefacts), "executions" -> executions(fixture.scenario).size.asJson)
    Files.writeString(locator, record.spaces2 + "\n", UTF_8, StandardOpenOption.CREATE_NEW)
  }

  private def _continue_prefix_episode(locator: Path): Unit = {
    Given("the SAME physical tiny fixture with its retained technical Human and the newly published normal installed runtime")
    val retained = parse(Files.readString(locator)).toOption.get
    retained.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.spec.source-prefix-reconstruction.v1")
    def _string_(key: String): String = retained.hcursor.get[String](key).toOption.get
    def _business_[A: org.goldenport.convert.ValueReader](key: String): A = take(summon[org.goldenport.convert.ValueReader[A]]
      .readC(take(new org.goldenport.record.io.RecordDecoder().json(_string_(key)))))
    val scenario = F.reopenSourceHistoryPrefix(Path.of(_string_("root")))
    val completed = _business_[V.GoalPhaseAdvanceResult]("completedRequest")
    val fixture = Implemented(scenario, Path.of(_string_("task")), completed, Path.of(scenario.host.target.root).resolve(sourcePath))
    scenario.retrieve(fixture.task) shouldBe _string_("session")
    val plan = retained.hcursor.get[GoalPhaseOrderedProgression.Plan]("plan").toOption.get
    scenario.plan shouldBe plan
    val before = take(WorkflowInstanceRecordJsonV1.decodeC(_string_("pendingRecord"), _catalog))
    _current(fixture) shouldBe before
    val oldquestion = _business_[V.GoalPhaseAdvanceResult]("question")
    val identity = _string_("questionIdentity")
    val (questionbody, status) = _prefix_question(fixture, identity)
    questionbody shouldBe _string_("questionBody")
    take(org.goldenport.cncf.workflow.ContinuationRecordJsonV1.encodeC(status)) shouldBe _string_("questionStatus")
    val sources = retained.hcursor.get[Vector[Json]]("sources").toOption.get
    val repository = Path.of(scenario.host.target.root)
    def _source_facts_(): Unit = sources.foreach { value =>
      val path = repository.resolve(value.hcursor.get[String]("path").toOption.get)
      Files.readString(path) shouldBe value.hcursor.get[String]("text").toOption.get
      Files.getLastModifiedTime(path).toString shouldBe value.hcursor.get[String]("modified").toOption.get
    }
    _source_facts_()
    val oldfacts = retained.hcursor.get[Vector[String]]("sourceFacts").toOption.get
    val oldcompleted = retained.hcursor.get[Vector[String]]("completedFacts").toOption.get
    val oldfailed = take(WorkflowMechanicalTransitionJsonV1.decodeC(_string_("failedResult"), _catalog, N.observationCodec))
    val oldunknown = take(WorkflowMechanicalTransitionJsonV1.decodeC(_string_("unknown"), _catalog, GoalPhaseNativeSourceHistory.codec))
    val oldtarget = oldfailed.payload.value.target
    val executionsbefore = retained.hcursor.get[Int]("executions").toOption.get
    executions(scenario).size shouldBe executionsbefore
    When("the existing reconstruct Operation reassesses its exact stale technical question using current source and unchanged documents")
    val selected = _prefix_reconstruct(fixture, plan).business[V.GoalPhaseAdvanceResult]
    Then("native supersession leaves the genuine question unanswered and appends one v2 common current-source selection")
    selected.kind shouldBe "Wait"
    selected.repairProgress shouldBe oldquestion.repairProgress
    val current = _current(fixture)
    current.history.dropRight(1) shouldBe before.history
    current.causation.value shouldBe GoalPhaseDevelopmentReconstruction.causation
    current.currentProgression.get.value shouldBe "phase.InspectCandidate"
    current.suspension shouldBe None
    val (bodyafter, statusafter) = _prefix_question(fixture, identity)
    bodyafter shouldBe questionbody
    statusafter shouldBe status.copy(status = org.goldenport.cncf.workflow.ContinuationRuntimePersistence.Status.Superseded)
    val selections = _stored_json(fixture).flatMap(raw => parse(raw).toOption).filter(value =>
      value.hcursor.get[String]("schemaVersion").contains("cncf.workflow-decision-supersession.v1") &&
        value.hcursor.get[String]("identity").contains(identity))
    selections.size shouldBe 1
    val selection = GoalPhaseDevelopmentReconstruction.codec.decode(selections.head.hcursor.downField("payload").focus.get)
      .fold(reason => fail(reason), value => value)
    selection.humanAnswer shouldBe None
    val proof = selection.prefixSelection.get
    proof.reusable shouldBe _prefix_paths.drop(1)
    proof.fresh shouldBe _prefix_paths.take(1)
    proof.supersededQuestion shouldBe Some(N.Question(identity, _string_("pendingRevision")))
    proof.newGeneration shouldBe proof.oldGeneration + 1
    _source_facts_()
    _prefix_reconstruct(fixture, plan).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
    _current(fixture) shouldBe current
    executions(scenario).size shouldBe executionsbefore

    When("a separate ordinary Advance prepares current source runs its fresh native check and issues independent ReviewStep")
    val review = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    Then("the same Session Plan base completed Step and repair accounting survive with only current-source qualifications")
    review.sessionId shouldBe _string_("session")
    review.work.get.kind shouldBe "ReviewStep"
    review.work.get.stepIdentity shouldBe Some("step-2")
    review.work.get.selectedPlan shouldBe completed.work.get.selectedPlan
    review.work.get.originalBase shouldBe completed.work.get.originalBase
    review.work.get.completed shouldBe completed.work.get.completed
    review.repairProgress shouldBe oldquestion.repairProgress
    review.work.get.review.get.checks.head.sbt.get.outcome shouldBe "succeeded"
    executions(scenario).size shouldBe executionsbefore + 1
    _receipt(fixture, oldfailed.reference, N.observationCodec) shouldBe oldfailed
    _receipt(fixture, oldunknown.reference, GoalPhaseNativeSourceHistory.codec) shouldBe oldunknown
    oldunknown.payload.value.outcome shouldBe None
    _old_result_absent(fixture, oldtarget)
    _stored_json(fixture) should contain allElementsOf oldfacts
    _stored_json(fixture) should contain allElementsOf oldcompleted
    sources.filter(value => _prefix_paths.drop(1).contains(value.hcursor.get[String]("path").toOption.get)).foreach { value =>
      val path = repository.resolve(value.hcursor.get[String]("path").toOption.get)
      Files.readString(path) shouldBe value.hcursor.get[String]("text").toOption.get
      Files.getLastModifiedTime(path).toString shouldBe value.hcursor.get[String]("modified").toOption.get
    }
    val fresh = _prefix_source_facts(fixture).diff(oldfacts).map(raw => take(WorkflowMechanicalTransitionJsonV1.decodeC(
      raw, _catalog, GoalPhaseNativeSourceHistory.codec)).payload.value)
    fresh.map(_.path).distinct shouldBe Vector(_prefix_paths.head, sourcePath)
    fresh.groupBy(_.path).values.foreach(values => {
      values.size shouldBe 2
      values.map(_.updatedOn).distinct shouldBe Vector("2026-11-06")
      values.map(_.sourceRevision).distinct.size shouldBe 1
      values.head.sourceRevision should not be oldunknown.payload.value.sourceRevision
    })
    fresh.filter(_.outcome.nonEmpty).map(value => value.path -> value.outcome.get).toMap shouldBe Map(
      _prefix_paths.head -> "unchanged", sourcePath -> "updated")
    val pending = _current(fixture)
    val freshfacts = _prefix_source_facts(fixture)
    val times = (_prefix_paths :+ sourcePath).map(path => Files.getLastModifiedTime(repository.resolve(path)))
    When("the same pending independent Review is recollected")
    _advance(fixture).business[V.GoalPhaseAdvanceResult] shouldBe review
    Then("no successful source effect check generation or question is repeated")
    _current(fixture) shouldBe pending
    _prefix_source_facts(fixture) shouldBe freshfacts
    (_prefix_paths :+ sourcePath).map(path => Files.getLastModifiedTime(repository.resolve(path))) shouldBe times
    executions(scenario).size shouldBe executionsbefore + 1
    _prefix_question(fixture, identity) shouldBe (questionbody -> statusafter)
    Files.writeString(locator.resolveSibling(locator.getFileName.toString + ".continued.json"), Json.obj(
      "locator" -> locator.toString.asJson, "root" -> scenario.root.toString.asJson,
      "session" -> review.sessionId.asJson, "selection" -> GoalPhaseDevelopmentReconstruction.codec.encode(selection),
      "review" -> _json(review).asJson).spaces2 + "\n", UTF_8, StandardOpenOption.CREATE_NEW)
  }

  private def _stop_business[A: org.goldenport.convert.ValueReader](path: Path): A =
    take(summon[org.goldenport.convert.ValueReader[A]].readC(
      take(new org.goldenport.record.io.RecordDecoder().json(Files.readString(path, UTF_8)))))

  private def _stop_fixture(locator: Path): (Json, Implemented) = {
    require(locator.isAbsolute && locator.normalize == locator && Files.isRegularFile(locator) &&
      locator.toRealPath() == locator, "Select the explicit real caller-owned existing episode locator")
    val seed = parse(Files.readString(locator, UTF_8)).fold(error => fail(error.message), identity)
    seed.hcursor.get[String]("schemaVersion") shouldBe Right("sm-workflow.spec.current-candidate-history-stop.seed.v1")
    val root = Path.of(seed.hcursor.get[String]("root").toOption.get)
    val scenario = F.reopenSourceHistoryPrefix(root)
    val task = Path.of(seed.hcursor.get[String]("task").toOption.get)
    task shouldBe root.resolve("target/sm-workflow/tasks/source-history-document-task")
    val implementation = Path.of(seed.hcursor.get[String]("implementationOutput").toOption.get)
    val human = Path.of(seed.hcursor.get[String]("humanOutput").toOption.get)
    implementation shouldBe root.resolve("logs/15.stdout.json")
    human shouldBe root.resolve("logs/18.stdout.json")
    Vector(implementation, human).foreach(path => {
      Files.isRegularFile(path) shouldBe true
      path.toRealPath() shouldBe path
    })
    val request = _stop_business[V.GoalPhaseAdvanceResult](implementation)
    val original = _stop_business[V.GoalPhaseAdvanceResult](human)
    val session = seed.hcursor.get[String]("session").toOption.get
    scenario.retrieve(task) shouldBe session
    request.sessionId shouldBe session
    original.sessionId shouldBe session
    request.work.get.kind shouldBe "ImplementStep"
    request.work.get.stepIdentity shouldBe Some("step-2")
    request.work.get.completed.size shouldBe 1
    request.work.get.selectedPlan.copy(effectiveAcceptance = None) shouldBe F.publicPlan(scenario.plan)
    original.kind shouldBe "Human"
    seed -> Implemented(scenario, task, request, Path.of(scenario.host.target.root).resolve(sourcePath))
  }

  private def _stop_sources(fixture: Implemented): Json = {
    val repository = Path.of(fixture.scenario.host.target.root)
    Json.fromValues((_prefix_paths :+ sourcePath).map(path => Json.obj("path" -> path.asJson,
      "text" -> Files.readString(repository.resolve(path), UTF_8).asJson,
      "modified" -> Files.getLastModifiedTime(repository.resolve(path)).toString.asJson)))
  }

  private def _stop_checkpoint(locator: Path, seed: Json, facts: Json): Unit =
    Files.writeString(locator, seed.mapObject(_.add("currentCandidateStop", facts)).spaces2 + "\n", UTF_8)

  private def _stop_adopt(fixture: Implemented, described: F.Result): (N.Target, Path) = {
    val description = described.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult]
    val target = take(N.targetC(description.target))
    val input = _normalization_direction(fixture, described)
    val path = Files.createTempFile(fixture.scenario.root, "current-history-normalization-", ".json")
    Files.writeString(path, _json(input), UTF_8)
    _goal(fixture, N.adoptionOperation, Vector("--inputResource", path.toUri.toASCIIString))
      .business[V.GoalPhaseSourceHistoryNormalizationAdoptionResult].outcome shouldBe "adopted"
    target -> path
  }

  protected final def current_candidate_stop_episode(mode: String, locator: Path): Unit = {
    require(Set("capture-stop", "continue-stop")(mode), "Select only capture-stop or continue-stop")
    val command = Path.of(sys.props.getOrElse("smworkflow.spec.installedCommand",
      fail("Current-candidate stop qualification requires the normal installed executable")))
    command.isAbsolute shouldBe true
    Files.isExecutable(command) shouldBe true
    if (mode == "capture-stop") _capture_current_stop(locator) else _continue_current_stop(locator)
  }

  private def _capture_current_stop(locator: Path): Unit = {
    Given("the actual partial old03 episode with a completed first Step four dated prefixes and its original unanswered Human")
    val (seed, fixture) = _stop_fixture(locator)
    seed.hcursor.downField("currentCandidateStop").focus shouldBe None
    val original = _stop_business[V.GoalPhaseAdvanceResult](Path.of(seed.hcursor.get[String]("humanOutput").toOption.get))
    val recovery = take(GoalPhaseSourceHistoryRecovery.targetC(original.human.get.sourceHistoryRecovery.get))
    recovery.completedPrefix.size shouldBe 4
    recovery.completedPrefix.foreach(_.result.updatedOn shouldBe "2026-11-05")
    val initial = _current(fixture)
    val originalidentity = initial.suspension.get.continuationIdentity.value
    val (questionbody, _) = _prefix_question(fixture, originalidentity)
    val initialfacts = _prefix_source_facts(fixture)
    val completedfacts = _completed_facts(fixture)
    val oldunknown = _receipt(fixture, WorkflowMechanicalTransition.Reference(recovery.intentIdentity,
      recovery.intentRevision), GoalPhaseNativeSourceHistory.codec)
    val count = executions(fixture.scenario).size
    count shouldBe 1
    When("ordinary Advance only recollects that same original Human without successful setup replay")
    _advance(fixture).business[V.GoalPhaseAdvanceResult] shouldBe original
    Then("the retained current record prefix receipts check and commit are identical")
    _current(fixture) shouldBe initial
    _prefix_source_facts(fixture) shouldBe initialfacts
    _completed_facts(fixture) shouldBe completedfacts
    executions(fixture.scenario).size shouldBe count

    Given("a legitimate Nov6 current source correction under the unchanged rule and standard fixture-local clock")
    val repository = Path.of(fixture.scenario.host.target.root)
    val reusable = _prefix_paths.drop(1).map(path => (path, fileBytes(repository.resolve(path)),
      Files.getLastModifiedTime(repository.resolve(path))))
    val rule = take(WorkflowSourceHistoryRule.decodeC(recovery.historyPolicy))
    val first = repository.resolve(_prefix_paths.head)
    Files.writeString(first, take(WorkflowSourceHistoryRule.applyC(rule, WorkflowSourceHistoryRule.Input(
      _prefix_paths.head, Files.readString(first, UTF_8).replace("= 42", "= 43"), LocalDate.of(2026, 11, 6),
      None, None, true))).text, UTF_8)
    Files.writeString(fixture.source, _later_corrected_text, UTF_8)
    val config = fixture.scenario.root.resolve(".textus/config.json")
    Files.writeString(config, parse(Files.readString(config, UTF_8)).toOption.get.mapObject(_.add(
      "textus.clock.virtual-start-at", Json.fromString("2026-11-06T00:00:00Z"))).noSpaces, UTF_8)
    val (target, adoption) = _stop_adopt(fixture, _normalization_description(fixture))
    target.recovery shouldBe recovery
    When("separate ordinary Advance retains the formal old-date mismatch and common reconstruction selects the current subject")
    _advance(fixture).business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
    val failed = _receipt(fixture, WorkflowMechanicalTransition.Reference(N.reference(target).identity +
      ":reconciliation-result", "1"), N.observationCodec)
    failed.payload.value.outcome shouldBe Some("failed")
    val selectedcall = _prefix_reconstruct(fixture, fixture.scenario.plan)
    val selected = selectedcall.business[V.GoalPhaseAdvanceResult]
    selected.kind shouldBe "Wait"
    val selection = _receipt(fixture, GoalPhaseDevelopmentReconstruction.reference(failed.next), GoalPhaseDevelopmentReconstruction.codec)
    selection.next shouldBe _current(fixture)
    selection.next.currentProgression.get.value shouldBe "phase.InspectCandidate"
    val proof = selection.payload.value.prefixSelection.get
    proof.reusable shouldBe _prefix_paths.drop(1)
    proof.fresh shouldBe _prefix_paths.take(1)
    proof.supersededQuestion shouldBe None
    proof.newGeneration shouldBe proof.oldGeneration + 1
    Then("the actual successful selection and original facts are checkpointed before introducing the later stop")
    val selectedfacts = Json.obj("phase" -> "selected".asJson,
      "selection" -> take(WorkflowMechanicalTransitionJsonV1.encodeC(selection, GoalPhaseDevelopmentReconstruction.codec)).asJson,
      "selectionOutput" -> selectedcall.invocation.stdout.toString.asJson, "selectedResult" -> _json(selected).asJson,
      "generation" -> proof.newGeneration.asJson, "oldAdoption" -> adoption.toString.asJson,
      "oldUnknown" -> take(WorkflowMechanicalTransitionJsonV1.encodeC(oldunknown, GoalPhaseNativeSourceHistory.codec)).asJson,
      "formalFailure" -> take(WorkflowMechanicalTransitionJsonV1.encodeC(failed, N.observationCodec)).asJson,
      "initialSourceFacts" -> initialfacts.asJson, "completedFacts" -> completedfacts.asJson,
      "originalHuman" -> _json(original).asJson, "originalQuestionIdentity" -> originalidentity.asJson,
      "originalQuestionBody" -> questionbody.asJson, "executions" -> count.asJson, "sourcesAtSelection" -> _stop_sources(fixture))
    _stop_checkpoint(locator, seed, selectedfacts)

    Given("the selected current affected source with a second managed dated block and unchanged compiled semantics")
    val conformed = take(WorkflowSourceHistoryRule.applyC(rule, WorkflowSourceHistoryRule.Input(sourcePath,
      _later_corrected_text, LocalDate.of(2026, 11, 6), None, None, true))).text
    Files.writeString(fixture.source, conformed, UTF_8)
    Files.writeString(fixture.source, "\n/**\n * @since Nov.  6, 2026\n * @version Nov.  6, 2026\n */\n", UTF_8,
      StandardOpenOption.APPEND)
    When("ordinary Advance actually stops at its new native source-history intent after the successful current prefix")
    val stoppedcall = _advance(fixture)
    val stopped = _current(fixture)
    val stoppedfacts = selectedfacts.mapObject(_.add("phase", "stopped".asJson)
      .add("stoppedOutput", stoppedcall.invocation.stdout.toString.asJson)
      .add("stoppedError", stoppedcall.invocation.stderr.toString.asJson)
      .add("stoppedRecord", take(WorkflowInstanceRecordJsonV1.encodeC(stopped)).asJson)
      .add("sourceFactsAtStop", _prefix_source_facts(fixture).asJson).add("sourcesAtStop", _stop_sources(fixture)))
    _stop_checkpoint(locator, seed, stoppedfacts)
    Then("the successful selection is never replayed and the distinct current affected intent remains UNKNOWN")
    stoppedcall.exitCode should not be 0
    stoppedcall.invocation.logs should include("multiple managed block comments")
    stopped.causation.value shouldBe "ordered-candidate:source-history-intent-1"
    val unknowns = _stored_json(fixture).flatMap(raw => WorkflowMechanicalTransitionJsonV1.decodeC(
      raw, _catalog, GoalPhaseNativeSourceHistory.codec).toOption).filter(receipt =>
      receipt.next == stopped && receipt.payload.value.path == sourcePath && receipt.payload.value.outcome.isEmpty)
    unknowns.size shouldBe 1
    val newunknown = unknowns.head
    val source = newunknown.payload.value.sourceRevision
    val currentprefix = _receipt(fixture, WorkflowMechanicalTransition.Reference(fixture.request.sessionId +
      ":ordered-candidate:" + source + ":source-history-result-0", "1"), GoalPhaseNativeSourceHistory.codec)
    newunknown.next shouldBe stopped
    newunknown.payload.value.path shouldBe sourcePath
    newunknown.payload.value.outcome shouldBe None
    currentprefix.payload.value.path shouldBe _prefix_paths.head
    currentprefix.payload.value.updatedOn shouldBe "2026-11-06"
    currentprefix.payload.value.outcome shouldBe Some("unchanged")
    Files.writeString(fixture.source, conformed, UTF_8)
    val beforewait = _stop_sources(fixture)
    val retainedfacts = _prefix_source_facts(fixture)
    val waitcall = _advance(fixture)
    waitcall.business[V.GoalPhaseAdvanceResult].kind shouldBe "Wait"
    val refused = _normalization_description(fixture)
    val captured = stoppedfacts.mapObject(_.add("phase", "captured".asJson)
      .add("newUnknown", take(WorkflowMechanicalTransitionJsonV1.encodeC(newunknown, GoalPhaseNativeSourceHistory.codec)).asJson)
      .add("currentPrefix", take(WorkflowMechanicalTransitionJsonV1.encodeC(currentprefix, GoalPhaseNativeSourceHistory.codec)).asJson)
      .add("sources", beforewait).add("sourceFacts", retainedfacts.asJson)
      .add("waitOutput", waitcall.invocation.stdout.toString.asJson)
      .add("refusedOutput", refused.invocation.stdout.toString.asJson).add("refusedError", refused.invocation.stderr.toString.asJson))
    _stop_checkpoint(locator, seed, captured)
    refused.exitCode should not be 0
    refused.invocation.logs should include("409")
    _current(fixture) shouldBe stopped
    _stop_sources(fixture) shouldBe beforewait
    _prefix_source_facts(fixture) shouldBe retainedfacts
    _receipt(fixture, selection.reference, GoalPhaseDevelopmentReconstruction.codec) shouldBe selection
    _receipt(fixture, oldunknown.reference, GoalPhaseNativeSourceHistory.codec) shouldBe oldunknown
    _receipt(fixture, failed.reference, N.observationCodec) shouldBe failed
    executions(fixture.scenario).size shouldBe count
    reusable.foreach { (path, bytes, time) =>
      fileBytes(repository.resolve(path)) shouldBe bytes
      Files.getLastModifiedTime(repository.resolve(path)) shouldBe time
    }
  }

  private def _continue_current_stop(locator: Path): Unit = {
    Given("the exact same stopped physical current candidate and the newly published normal installed runtime")
    val (seed, fixture) = _stop_fixture(locator)
    val facts = seed.hcursor.downField("currentCandidateStop").focus.getOrElse(fail("Capture-stop checkpoint is required"))
    facts.hcursor.get[String]("phase") shouldBe Right("captured")
    def _string_(name: String): String = facts.hcursor.get[String](name).toOption.get
    val stopped = take(WorkflowInstanceRecordJsonV1.decodeC(_string_("stoppedRecord"), _catalog))
    val selection = take(WorkflowMechanicalTransitionJsonV1.decodeC(_string_("selection"), _catalog,
      GoalPhaseDevelopmentReconstruction.codec))
    val failed = take(WorkflowMechanicalTransitionJsonV1.decodeC(_string_("formalFailure"), _catalog, N.observationCodec))
    val oldunknown = take(WorkflowMechanicalTransitionJsonV1.decodeC(_string_("oldUnknown"), _catalog, GoalPhaseNativeSourceHistory.codec))
    val newunknown = take(WorkflowMechanicalTransitionJsonV1.decodeC(_string_("newUnknown"), _catalog, GoalPhaseNativeSourceHistory.codec))
    val currentprefix = take(WorkflowMechanicalTransitionJsonV1.decodeC(_string_("currentPrefix"), _catalog, GoalPhaseNativeSourceHistory.codec))
    val sourcefacts = facts.hcursor.get[Vector[String]]("sourceFacts").toOption.get
    val completedfacts = facts.hcursor.get[Vector[String]]("completedFacts").toOption.get
    val original = take(summon[org.goldenport.convert.ValueReader[V.GoalPhaseAdvanceResult]].readC(
      take(new org.goldenport.record.io.RecordDecoder().json(_string_("originalHuman")))))
    val count = facts.hcursor.get[Int]("executions").toOption.get
    val generation = facts.hcursor.get[Long]("generation").toOption.get
    _current(fixture) shouldBe stopped
    _stop_sources(fixture) shouldBe facts.hcursor.downField("sources").focus.get
    _prefix_source_facts(fixture) shouldBe sourcefacts
    _completed_facts(fixture) shouldBe completedfacts
    _receipt(fixture, selection.reference, GoalPhaseDevelopmentReconstruction.codec) shouldBe selection
    _receipt(fixture, failed.reference, N.observationCodec) shouldBe failed
    _receipt(fixture, oldunknown.reference, GoalPhaseNativeSourceHistory.codec) shouldBe oldunknown
    _receipt(fixture, newunknown.reference, GoalPhaseNativeSourceHistory.codec) shouldBe newunknown
    _receipt(fixture, currentprefix.reference, GoalPhaseNativeSourceHistory.codec) shouldBe currentprefix
    selection.payload.value.prefixSelection.get.newGeneration shouldBe generation
    executions(fixture.scenario).size shouldBe count
    When("ordinary Advance exposes the new native source-history Human and its existing exact public normalization description")
    val question = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    Then("the new target belongs to the actual later inspection while the old consumed target remains unavailable")
    question.kind shouldBe "Human"
    question.repairProgress shouldBe original.repairProgress
    val current = take(GoalPhaseSourceHistoryRecovery.targetC(question.human.get.sourceHistoryRecovery.get))
    val old = failed.payload.value.target.recovery
    current.sourceRevision shouldBe newunknown.payload.value.sourceRevision
    current.intentIdentity shouldBe newunknown.reference.identity
    current.sourceRevision.toLong should be > old.frontierRevision.toLong
    current.sourceRevision should not be old.sourceRevision
    current.planIdentity shouldBe old.planIdentity
    current.planRevision shouldBe old.planRevision
    current.step shouldBe old.step
    current.base shouldBe old.base
    current.controllerTip shouldBe old.controllerTip
    current.completedPrefix.size shouldBe 1
    current.completedPrefix.head.result shouldBe currentprefix.payload.value
    val pending = _current(fixture)
    val stale = _goal(fixture, N.adoptionOperation, Vector("--inputResource", Path.of(_string_("oldAdoption")).toUri.toASCIIString))
    stale.exitCode should not be 0
    _current(fixture) shouldBe pending
    val identity = pending.suspension.get.continuationIdentity.value
    val (body, status) = _prefix_question(fixture, identity)
    Given("fresh fixture-owned current-target preparation with an explicit new prior-execution-ended judgment")
    val (target, _) = _stop_adopt(fixture, _normalization_description(fixture))
    target.recovery shouldBe current
    When("separate ordinary Advance composes old reusable current successful and affected normalization qualifications")
    val review = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    Then("the same generation base accepted first Step and counters reach one fresh check and independent Review without repeated File effects")
    review.work.get.kind shouldBe "ReviewStep"
    review.work.get.stepIdentity shouldBe Some("step-2")
    review.work.get.selectedPlan shouldBe fixture.request.work.get.selectedPlan
    review.work.get.originalBase shouldBe fixture.request.work.get.originalBase
    review.work.get.completed shouldBe fixture.request.work.get.completed
    review.repairProgress shouldBe original.repairProgress
    review.work.get.review.get.checks.head.sbt.get.outcome shouldBe "succeeded"
    val observed = _receipt(fixture, WorkflowMechanicalTransition.Reference(N.reference(target).identity +
      ":reconciliation-result", "1"), N.observationCodec)
    observed.payload.value.outcome shouldBe Some("observed-current-postcondition")
    _stop_sources(fixture) shouldBe facts.hcursor.downField("sources").focus.get
    _prefix_source_facts(fixture) shouldBe sourcefacts
    _stored_json(fixture) should contain allElementsOf completedfacts
    _receipt(fixture, selection.reference, GoalPhaseDevelopmentReconstruction.codec) shouldBe selection
    _receipt(fixture, failed.reference, N.observationCodec) shouldBe failed
    _receipt(fixture, oldunknown.reference, GoalPhaseNativeSourceHistory.codec) shouldBe oldunknown
    _receipt(fixture, newunknown.reference, GoalPhaseNativeSourceHistory.codec) shouldBe newunknown
    _receipt(fixture, currentprefix.reference, GoalPhaseNativeSourceHistory.codec) shouldBe currentprefix
    _old_result_absent(fixture, failed.payload.value.target)
    _old_result_absent(fixture, target)
    executions(fixture.scenario).size shouldBe count + 1
    val inputs = _stored_json(fixture).flatMap(raw => org.goldenport.cncf.workflow.WorkflowWorkOrderJsonV1.decodeC(
      raw, GoalPhaseNativeWork.inputCodec).toOption).flatMap(value => value.current.asInstanceOf[
        org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation.WorkOrder[GoalPhaseNativeWork.Request]].request.input.map(_.value))
      .filter(input => input.instanceIdentity == fixture.request.sessionId && input.kind == "ReviewStep" && input.stepIdentity.contains("step-2"))
    inputs.size shouldBe 1
    inputs.head.generation shouldBe generation
    val (afterbody, afterstatus) = _prefix_question(fixture, identity)
    afterbody shouldBe body
    afterstatus shouldBe status.copy(status = org.goldenport.cncf.workflow.ContinuationRuntimePersistence.Status.Superseded)
    val (oldbody, oldstatus) = _prefix_question(fixture, _string_("originalQuestionIdentity"))
    oldbody shouldBe _string_("originalQuestionBody")
    oldstatus.status shouldBe org.goldenport.cncf.workflow.ContinuationRuntimePersistence.Status.Superseded
    val before = _current(fixture)
    When("the same pending independent Review is recollected without a new common selection")
    _advance(fixture).business[V.GoalPhaseAdvanceResult] shouldBe review
    Then("no successful source effect check generation or old authority is repeated")
    _current(fixture) shouldBe before
    _prefix_source_facts(fixture) shouldBe sourcefacts
    _stop_sources(fixture) shouldBe facts.hcursor.downField("sources").focus.get
    executions(fixture.scenario).size shouldBe count + 1
    Files.writeString(locator.resolveSibling(locator.getFileName.toString + ".qualified.json"), Json.obj(
      "locator" -> locator.toString.asJson, "root" -> fixture.scenario.root.toString.asJson,
      "session" -> fixture.request.sessionId.asJson, "generation" -> generation.asJson,
      "selection" -> _string_("selection").asJson, "review" -> _json(review).asJson,
      "normalization" -> take(WorkflowMechanicalTransitionJsonV1.encodeC(observed, N.observationCodec)).asJson).spaces2 + "\n",
      UTF_8, StandardOpenOption.CREATE_NEW)
  }

  private def _nested_public_preparation(fixture: Implemented,
    genuine: V.GoalPhaseSourceHistoryNormalizationDescriptionResult): Unit = {
    Given("specification-owned synthetic public data with 1024 completed prefix pairs and nested initial-v8 lineage; it is no native description or historical authority")
    val actual = take(N.targetC(genuine.target))
    val selected = SourceHistoryNormalizationSpecificationFixture.nestedTarget(sourcePath,
      actual.recovery.historyPolicy, actual.recovery.intent.providerIdentity, 1024)
    val description = genuine.copy(sessionId = selected.recovery.sessionId, target = N.targetRecord(selected),
      message = "SPECIFICATION-OWNED synthetic nested public preparation input; never import or adopt this artificial Session")
    val directory = Files.createTempDirectory(fixture.scenario.root, "specification-owned-synthetic-nested-public-")
    val reference = V.WorkflowArtifactReference(directory.getFileName.toString + ":passive-specimen-only", "1")
    val answer = V.GoalPhaseSourceHistoryNormalizationAnswer(N.choices.head,
      "Specification-owned passive public DTO specimen only; no production Human choice or historical authority", true)
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
    def _file_(name: String, value: RecordPresentable): Path = {
      val path = directory.resolve(name + ".json")
      Files.writeString(path, _json(value), UTF_8)
      path
    }
    val descriptionfile = _file_("synthetic-complete-description", description)
    val descriptionbytes = Files.readAllBytes(descriptionfile).toVector
    val publicwire = GoalPhaseNativeMetadata.normalizePublicJson(parse(Files.readString(descriptionfile)).toOption.get)
      .fold(message => fail(message), identity)
    val publictarget = publicwire.hcursor.downField("target")
    val publiccontroller = publictarget.downField("recovery").downField("inspection")
      .downField("dependencySourceDirection").downField("target").downField("initialOrigin")
      .downField("lineage").downField("recovery").downField("controller")
    description.sessionId should not be genuine.sessionId
    description.componentInstance shouldBe genuine.componentInstance
    publictarget.downField("recovery").get[Vector[Json]]("completedPrefix").toOption.get.size shouldBe 1024
    publiccontroller.focus.flatMap(_.asObject).get.size shouldBe 11
    publiccontroller.downField("candidate").focus shouldBe None
    descriptionbytes.size should be > (500 * 1024)

    val config = fixture.scenario.root.resolve(".textus/config.json")
    val originalconfig = parse(Files.readString(config)).toOption.get
    originalconfig.hcursor.get[String]("textus.resource.url.file.roots").toOption.get.split(',').toVector should contain(
      fixture.scenario.root.toString)
    val budget = 8388608L
    val configured = originalconfig.mapObject(_.add("textus.resource.read.max-bytes", Json.fromString(budget.toString))).noSpaces
    Files.writeString(config, configured, UTF_8)
    Files.size(descriptionfile) should be < budget
    val preparation = V.WorkflowEvidencePreparationInput("GoalPhase", "source-history-normalization",
      descriptionfile.toUri.toASCIIString, _file_("synthetic-answer", answer).toUri.toASCIIString,
      _file_("synthetic-reference", reference).toUri.toASCIIString,
      _file_("synthetic-execution", execution).toUri.toASCIIString,
      "normal-development-host", "2026-10-07T00:00:00Z", "entity:smworkflow.cli.host_evidence", Some("participant"))
    val before = _current(fixture)
    val native = _stored_json(fixture).sorted
    val facts = _completed_facts(fixture)
    val unknownreference = WorkflowMechanicalTransition.Reference(actual.recovery.intentIdentity, actual.recovery.intentRevision)
    val unknown = _receipt(fixture, unknownreference, GoalPhaseNativeSourceHistory.codec)
    val executioncount = executions(fixture.scenario).size
    val repository = Path.of(fixture.scenario.host.target.root)
    val prefix = _prefix_paths.map(path => (path, fileBytes(repository.resolve(path)), Files.getLastModifiedTime(repository.resolve(path))))

    When("the normal installed preparation service reads the complete specimen FILE and fixture-owned answer/reference/execution FILES under the configured finite Resource policy")
    val prepared = _call(fixture, "NativeWorkflowPreparationService", "prepareEvidence", Vector("--input", _json(preparation)))
    Then("one passive datastore import is returned and retained as output only; the genuine Session UNKNOWN and successful effects remain unchanged")
    withClue(prepared.invocation.logs) { prepared.exitCode shouldBe 0 }
    val output = parse(Files.readString(prepared.invocation.stdout)).toOption.get
    val imports = output.hcursor.get[Vector[Json]]("datastore").toOption.get
    imports.size shouldBe 1
    imports.head.hcursor.get[String]("collection") shouldBe Right("entity:smworkflow.cli.host_evidence")
    imports.head.hcursor.get[Vector[Json]]("records").toOption.get.size shouldBe 1
    Files.isRegularFile(prepared.invocation.stdout) shouldBe true
    Files.isRegularFile(prepared.invocation.stderr) shouldBe true
    Files.readAllBytes(descriptionfile).toVector shouldBe descriptionbytes
    Files.readString(config) shouldBe configured
    _current(fixture) shouldBe before
    _stored_json(fixture).sorted shouldBe native
    _completed_facts(fixture) shouldBe facts
    actual.recovery.completedPrefix.size shouldBe 4
    _receipt(fixture, unknownreference, GoalPhaseNativeSourceHistory.codec) shouldBe unknown
    unknown.payload.value.outcome shouldBe None
    _old_result_absent(fixture, actual)
    executions(fixture.scenario).size shouldBe executioncount
    prefix.foreach { (path, bytes, time) =>
      fileBytes(repository.resolve(path)) shouldBe bytes
      Files.getLastModifiedTime(repository.resolve(path)) shouldBe time
    }
    // Deliberately do not select/import this synthetic output or adopt its artificial Session.
  }

  protected def resource_episode(name: String, observed: Boolean, deferred: Boolean, nestedPublic: Boolean = false): Unit = {
    Given("a real two-Step episode with four successful source-history entries and an actual missing-result UNKNOWN")
    val episode = _document_episode(name, later = true, canonicalHost = deferred)
    val fixture = episode.fixture
    val issued = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    issued.kind shouldBe "Human"
    issued.human.get.choices shouldBe GoalPhaseSourceHistoryRecovery.choices
    val recovery = take(GoalPhaseSourceHistoryRecovery.targetC(issued.human.get.sourceHistoryRecovery.get))
    recovery.intentIndex shouldBe 4
    recovery.completedPrefix.size shouldBe 4
    val config = fixture.scenario.root.resolve(".textus/config.json")
    if (deferred) {
      Given("the normal deferred host used since initial Start retains the same Session and unanswered Human with identical standard policies")
      val processplan = NativeCommonDelegateTaskFixture.processPlan(fixture.scenario.root, focused = false)
      val target = fixture.scenario.host.target
      val repositories = processplan.hcursor.get[Vector[io.circe.Json]]("gitRepositories").toOption.get
      repositories.size shouldBe 1
      val repository = repositories.head
      repository.hcursor.get[String]("target") shouldBe Right("sm-workflow-repository")
      repository.hcursor.get[String]("localBranch") shouldBe Right(target.localBranch)
      repository.hcursor.get[String]("remote") shouldBe Right(target.remote)
      repository.hcursor.get[String]("remoteBranch") shouldBe Right(target.remoteBranch)
      repository.hcursor.get[String]("endpoint") shouldBe Right(target.endpoint)
      val workspaces = processplan.hcursor.get[Vector[io.circe.Json]]("workspaces").toOption.get
        .filter(_.hcursor.get[String]("identity").contains("sm-workflow-repository"))
      workspaces.size shouldBe 1
      workspaces.head.hcursor.get[String]("root") shouldBe Right(target.root)
      val original = parse(Files.readString(config)).toOption.get
      original.hcursor.downField("sm-workflow.runtime.definition").focus shouldBe None
      parse(original.hcursor.get[String]("textus.subsystem.process-execution.plan").toOption.get) shouldBe Right(processplan)
    }
    val before = _current(fixture)
    val facts = _completed_facts(fixture)
    val sourcefacts = _stored_json(fixture).filter(raw =>
      WorkflowMechanicalTransitionJsonV1.decodeC(raw, _catalog, GoalPhaseNativeSourceHistory.codec).isSuccess)
    val repository = Path.of(fixture.scenario.host.target.root)
    val prefix = _prefix_paths.map(path => (path, fileBytes(repository.resolve(path)), Files.getLastModifiedTime(repository.resolve(path))))
    val index = indexBytes(fixture.scenario)
    val described = _normalization_description(fixture)
    val description = described.business[V.GoalPhaseSourceHistoryNormalizationDescriptionResult]
    val target = take(N.targetC(description.target))
    target.recovery shouldBe recovery
    target.pendingQuestion.get.pendingRevision shouldBe before.revision.value.toString
    _current(fixture) shouldBe before
    val unknown = _receipt(fixture, WorkflowMechanicalTransition.Reference(recovery.intentIdentity, recovery.intentRevision),
      GoalPhaseNativeSourceHistory.codec)
    unknown.payload.value.outcome shouldBe None

    if (nestedPublic) _nested_public_preparation(fixture, description)

    Given("independent test-owned policy and actual-ended evidence prepared through the normal public service")
    val input = _normalization_direction(fixture, described)
    val body = "// Preserve this independent source comment.\n" + _later_corrected_text
    val normalized = take(WorkflowSourceHistoryRule.applyC(take(WorkflowSourceHistoryRule.decodeC(recovery.historyPolicy)),
      WorkflowSourceHistoryRule.Input(sourcePath, body, LocalDate.parse(recovery.intent.updatedOn), None, None,
        recovery.intent.dateUncertain))).text
    Files.writeString(fixture.source, if (observed) normalized else body, UTF_8)
    val sourcebytes = fileBytes(fixture.source)
    val modified = Files.getLastModifiedTime(fixture.source)
    val request = fixture.scenario.root.resolve("complete-normalization-adoption.json")
    // Legal JSON whitespace enlarges representation without inventing authority or changing the complete target.
    Files.writeString(request, _json(input) + (" " * (501 * 1024)), UTF_8)
    Files.size(request) should be > (500L * 1024)
    val serialized = parse(Files.readString(request)).toOption.get
    take(N.targetC(input.target)) shouldBe target
    val arguments = Vector("--inputResource", request.toUri.toASCIIString)

    When("the existing adoption Operation acquires only the complete local Resource under its owning call")
    _goal(fixture, N.adoptionOperation, arguments).business[V.GoalPhaseSourceHistoryNormalizationAdoptionResult].outcome shouldBe "adopted"
    Then("the unanswered Human is superseded once and adoption performs no source or candidate effect")
    val adopted = _current(fixture)
    adopted.history.dropRight(1) shouldBe before.history
    adopted.causation.value shouldBe N.cause(target)
    adopted.causation.value should include("superseded-by-normalization")
    adopted.suspension shouldBe None
    fileBytes(fixture.source) shouldBe sourcebytes
    Files.getLastModifiedTime(fixture.source) shouldBe modified
    indexBytes(fixture.scenario) shouldBe index
    _completed_facts(fixture) shouldBe facts
    executions(fixture.scenario).size shouldBe 1
    _goal(fixture, N.adoptionOperation, arguments).exitCode should not be 0
    _current(fixture) shouldBe adopted
    parse(Files.readString(request)).toOption.get shouldBe serialized

    When("a separate ordinary Advance reconciles the original rule and date then runs normal candidate validation")
    val review = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    val observation = _receipt(fixture, WorkflowMechanicalTransition.Reference(N.reference(target).identity +
      ":reconciliation-result", "1"), N.observationCodec).payload.value
    Then("the actual branch reaches a fresh independent ReviewStep with unchanged accepted work and original UNKNOWN")
    observation.outcome shouldBe Some(if (observed) "observed-current-postcondition" else "updated-current")
    Files.readString(fixture.source) shouldBe normalized
    if (observed) {
      fileBytes(fixture.source) shouldBe sourcebytes
      Files.getLastModifiedTime(fixture.source) shouldBe modified
    }
    review.work.get.kind shouldBe "ReviewStep"
    review.work.get.stepIdentity shouldBe Some("step-2")
    review.work.get.selectedPlan shouldBe episode.completed.work.get.selectedPlan
    review.work.get.originalBase shouldBe episode.completed.work.get.originalBase
    review.work.get.completed shouldBe episode.completed.work.get.completed
    review.repairProgress shouldBe issued.repairProgress
    review.work.get.review.get.checks.head.sbt.get.outcome shouldBe "succeeded"
    executions(fixture.scenario).size shouldBe 2
    prefix.foreach { (path, bytes, time) =>
      fileBytes(repository.resolve(path)) shouldBe bytes
      Files.getLastModifiedTime(repository.resolve(path)) shouldBe time
    }
    _receipt(fixture, WorkflowMechanicalTransition.Reference(recovery.intentIdentity, recovery.intentRevision),
      GoalPhaseNativeSourceHistory.codec) shouldBe unknown
    _old_result_absent(fixture, target)
    _stored_json(fixture) should contain allElementsOf sourcefacts
    _stored_json(fixture) should contain allElementsOf facts

    When("the same independent Review is recollected through ordinary Advance")
    val complete = _current(fixture)
    val finalmodified = Files.getLastModifiedTime(fixture.source)
    val recollected = _advance(fixture).business[V.GoalPhaseAdvanceResult]
    Then("no successful prefix source normalization or selected check is repeated")
    recollected shouldBe review
    _current(fixture) shouldBe complete
    Files.getLastModifiedTime(fixture.source) shouldBe finalmodified
    executions(fixture.scenario).size shouldBe 2
    complete.history.count(_.suspension.exists(_.completion.value == "goal-phase-ordered-human-completion-v1")) shouldBe 1
    _old_result_absent(fixture, target)
    if (deferred) parse(Files.readString(config)).toOption.get.hcursor.downField("sm-workflow.runtime.definition").focus shouldBe None
  }

}

@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalSourceHistoryNormalizationResourceCliSpec extends NativeGoalSourceHistoryNormalizationResourceCliSupport {
  "Public normalization adoption through a complete Resource request" should {
    "normalize the affected source under its retained authority after configured-runtime Resource adoption" in {
      resource_episode("normalization-resource-update", observed = false, deferred = false)
    }
  }
}

@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalSourceHistoryNormalizationResourceDeferredCliSpec extends NativeGoalSourceHistoryNormalizationResourceCliSupport {
  "Public normalization adoption through a complete Resource request" should {
    "observe the satisfied postcondition without replacement after standard deferred Resource session resolution" in {
      resource_episode("normalization-resource-observed-deferred", observed = true, deferred = true)
    }
  }
}

@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalSourceHistoryNormalizationNestedPublicCliSpec extends NativeGoalSourceHistoryNormalizationResourceCliSupport {
  "Installed nested public preparation and genuine isolated Resource adoption" should {
    "prepare the large synthetic public specimen passively then adopt only the real Session target and Advance to independent review" in {
      resource_episode("normalization-resource-nested-public-deferred", observed = true, deferred = true, nestedPublic = true)
    }
  }
}
