package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.goldenport.record.io.RecordDecoder
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.preparation.{NativeAuthorityPreparation, NativeReviewPreparation, NativeRemainingPlanPreparation}
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.model.datatype.EntityCollectionId
import GoalPhaseFindingRecording as R
import GoalPhaseNativeFindingRecording as N
import GoalPhaseNativeWork as S
import GoalPhaseOrderedProgression as O
import SplitPhaseExplicitPartitionFixture.take

/** Real normal Operations, managed Git/SBT and SQLite own the connected episode.
  * Reviews, authority and journal edits are explicitly SPECIFICATION-OWNED inputs,
  * never genuine external reviewer or AI execution evidence.
  * @since   Oct. 18, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseNativeFindingRecordingSpec extends AnyWordSpec with GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  private val _hygiene = "docs/journal.md"
  private val _candidates = "docs/candidates.md"
  private val _paths = Vector("step-1.txt", "step-2.txt", _hygiene, _candidates)
  private val _policy = R.Policy(_hygiene, _candidates)
  private val _journal = DevelopmentJournal.Policy("workflow",
    DataStore.CollectionId.EntityStore(EntityCollectionId("smworkflow", "cli", "development_journal")))
  private val _input = V.GoalPhaseStartInput("verification-p62-recording", "sm-workflow")
  private def _plan(two: Boolean = false): O.Plan = O.Plan("verification-recording-plan", "1", _input.phaseIdentity,
    _input.repositoryIdentity, Vector("step-1", "step-2").take(if (two) 2 else 1).zipWithIndex.map { (id, index) =>
      O.Step(id, "Implement the assigned " + id, Vector(id + ".txt", _hygiene, _candidates),
        if (index == 0) Vector.empty else Vector("step-1"), Vector("test"))
    }, Vector("test"), repairLimit = 2)
  private val _observation = R.Observation("naming-1", "HYGIENE", "Clarify the accepted name",
    "The required behavior is accepted; naming can be clarified separately")
  private val _idea = R.Observation("idea-1", "DEVELOPMENT_CANDIDATE", "Consider a future extension",
    "The extension is outside the selected requirement")
  private def _root(): Path = Files.createTempDirectory(Files.createDirectories(
    Path.of("target/sm-workflow/work/finding-recording").toAbsolutePath), "episode-").toRealPath()
  private def _host(directory: Path): ThreeWorkflowRuntimeParameters.Definition = {
    val original = ThreeWorkflowNormalHostFixture.definition(directory)
    original.copy(goal = original.goal.copy(input = _input, candidatePaths = _paths,
      closure = original.goal.closure.copy(programs = Vector(
        GoalPhaseNativeClosure.StepProgram("step-1", 1), GoalPhaseNativeClosure.StepProgram("step-2", 2)))))
  }
  private def _configured(value: R.Policy): Map[String, String] = Map(R.declaration.name -> Json.obj(
    "schemaVersion" -> Json.fromString(R.schemaVersion), "hygiene" -> Json.fromString(value.hygiene),
    "developmentCandidate" -> Json.fromString(value.developmentCandidate)).noSpaces)
  override protected def _prepare_sbt_build_(directory: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(directory, scenario)
    Files.createDirectories(directory.resolve("repository/docs"))
    Vector(_hygiene, _candidates).foreach { path =>
      val file = directory.resolve("repository").resolve(path)
      if (!Files.exists(file)) Files.writeString(file, "# Existing selected records\n\nPreserve this original narrative.\n")
    }
  }
  override protected def _setup_commands_(directory: Path, scenario: String): Vector[Vector[String]] = {
    val commands = super._setup_commands_(directory, scenario)
    commands.patch(commands.indexWhere(_.contains("commit")), Vector(Vector("-C", directory.resolve("repository").toString,
      "--literal-pathspecs", "add", "--", _hygiene, _candidates)), 0)
  }
  override protected def process_plan(directory: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    _process_plan(directory, goal)
  private def _process_plan(directory: Path, goal: ThreeWorkflowRuntimeParameters.Goal, denygit: Boolean = false): Json = {
    val target = Programs.Target(directory.resolve("repository"), "main", "origin", "main", directory.resolve("upstream.git").toString)
    val messages = goal.closure.programs.map(p => "Goal Step " + p.step)
    val selections = goal.closure.programs.map(program => Vector(program.step + ".txt", _hygiene, _candidates))
    val normalgit = TypedGitHostPlanFixture.plan(target, goalPaths = goal.candidatePaths, goalMessages = messages,
      goalSelections = selections)
    val denied = if (denygit) normalgit.hcursor.get[Vector[Json]]("gitPrograms").toOption.get
      .map(_.hcursor.get[String]("capability").toOption.get).toSet else Set.empty[String]
    val git = if (!denygit) normalgit else TypedGitHostPlanFixture.plan(target,
      goalPaths = goal.candidatePaths, goalMessages = messages, denied = denied, goalSelections = selections)
    val combined = TypedSbtHostPlanFixture.plan(directory, TypedSbtHostPlanFixture.normalExecutable, Vector(1), Some(git))
    if (!denygit) combined else {
      val gitcapabilities = git.hcursor.get[Vector[Json]]("grants").toOption.get
        .flatMap(_.hcursor.get[Vector[Json]]("capabilities").toOption.get)
      val sbtcapabilities = combined.hcursor.get[Vector[Json]]("sbtPrograms").toOption.get
        .map(_.hcursor.downField("capability").focus.get)
      val grants = combined.hcursor.get[Vector[Json]]("grants").toOption.get.map(_.mapObject(
        _.add("capabilities", Json.fromValues(gitcapabilities ++ sbtcapabilities))))
      combined.mapObject(_.add("grants", Json.fromValues(grants)))
    }
  }
  private def _install(call: Setup, selected: O.Plan): Consequence[Unit] = {
    val selection = _goal_selection_(_input, "specification-recording-selection", "specification-recording-start")
    for {
      _ <- call.createC("goal_selection", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(_input),
        selection.participantIdentity), Record.data(ProfileSelectionJsonV1.storageField -> take(ProfileSelectionJsonV1.encodeStorageC(selection))))
      _ <- call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
        take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(selection.selectionId, selection.participantIdentity, selected)))))
    } yield ()
  }
  private def _episode(directory: Path, initialize: Boolean = false, selected: O.Plan = _plan(), denygit: Boolean = false,
    selectedPolicy: Option[R.Policy] = Some(_policy), interrupt: Boolean = false,
    journal: Option[NativeWorkflowCliFixture.JournalHost] = None,
    journalInterruption: Option[String] = None, evidence: Boolean = false)(task: Episode => Unit): Unit = {
    val acknowledge: String => Consequence[Unit] = point =>
      if (interrupt && point.startsWith("goal-recording-candidate-prepared:") || journalInterruption.contains(point))
        Consequence.stateConflict("SPECIFICATION-OWNED interruption before actual staging") else Consequence.unit
    val parameters = selectedPolicy.map(_configured).getOrElse(Map.empty) ++ journal.toVector.flatMap(_.values) ++ (if (!denygit) Map.empty else Map(
      "textus.subsystem.process-execution.plan" -> _process_plan(directory, _host(directory).goal, denygit = true).noSpaces)) ++
      (if (evidence) _evidence_values(directory) else Map.empty)
    _with_runtime_(directory, "FindingRecording", Some(_host(directory)), parameters,
      goalAcknowledgement = Some(acknowledge)) { (owner, component, actor, prepare) =>
      if (initialize) prepare(call => call.setupC(_setup_commands_(directory, "FindingRecording").size).flatMap(_ => _install(call, selected)))
      task(new Episode(directory, owner, component, actor, prepare))
    }
  }
  private def _evidence_values(directory: Path): Map[String, String] = {
    // Only this specification host prepares the physical archive parent.
    Files.createDirectories(directory.resolve("repository"))
    val root = Files.createDirectories(directory.resolve("archive/logs")).getParent.toRealPath()
    val collection = EntityCollectionId("smworkflow", "cli", "common_evidence").print
    val declared = Json.obj("schemaVersion" -> Json.fromString(CommonEvidence.schemaVersion),
      "storeName" -> Json.fromString("workflow"), "collection" -> Json.fromString(collection),
      "hostIdentity" -> Json.fromString("spec-host"), "workspaceIdentity" -> Json.fromString("evidence-archive"),
      "directory" -> Json.fromString("logs"))
    val journal = NativeWorkflowCliFixture.JournalHost(_journal).values
    val storage = io.circe.parser.parse(journal("textus.subsystem.storage.plan")).toOption.get.mapObject(fields =>
      fields.add("records", Json.fromValues(fields("records").get.asArray.get :+ Json.obj(
        "componentId" -> Json.fromString(SmWorkflowComponent.componentId.name),
        "storeName" -> Json.fromString("workflow"), "collection" -> Json.fromString(collection)))))
    val process = _process_plan(directory, _host(directory).goal).mapObject(fields => fields
      .add("workspaces", Json.fromValues(fields("workspaces").get.asArray.get :+ Json.obj(
        "identity" -> Json.fromString("evidence-archive"), "root" -> Json.fromString(root.toString))))
      .add("workspaceGrants", Json.fromValues(fields("workspaceGrants").get.asArray.get :+ Json.obj(
        "componentId" -> Json.fromString(SmWorkflowComponent.componentId.name),
        "workspaceId" -> Json.fromString("evidence-archive"), "access" -> Json.fromString("manage")))))
    journal ++ Map(CommonEvidence.declaration.name -> declared.noSpaces,
      "textus.subsystem.storage.plan" -> storage.noSpaces,
      "textus.subsystem.process-execution.plan" -> process.noSpaces)
  }

  private[admission] def produceEvidenceForCli(startSuffix: String = ""): (Path, O.Plan, V.EvidenceRecordingState) = {
    require(startSuffix.matches("[A-Za-z0-9-]*"), "Use only a literal specification-owned Start suffix")
    val directory = _root()
    var saved = Option.empty[V.EvidenceRecordingState]
    _episode(directory, initialize = true, evidence = true) { e =>
      val id = e.start(Option.when(startSuffix.nonEmpty)("specification-recording" + startSuffix))
      e.advance(id).work.get.kind shouldBe "ImplementStep"
      Files.writeString(directory.resolve("repository/step-1.txt"), "actual test input\n")
      e.submit(e.work(id))
      val next = e.advance(id)
      _checks(directory) shouldBe 1
      val observed = next.validation.get.evidenceRecording.head
      observed.status shouldBe "stored"
      observed.journal.get.status shouldBe "recorded"
      saved = Some(observed)
      val repeated = e.advance(id)
      _checks(directory) shouldBe 1
      repeated.work.get.kind shouldBe next.work.get.kind
    }
    (directory, _plan(), saved.get)
  }

  /** Keep the real completed semantic result, leaving Test and automatic
    * archival entirely to a later ordinary installed CLI Advance.
    */
  private[admission] def prepareEvidenceForInstalledCli(): (Path, String, InstanceRecord) = {
    val directory = _root()
    var captured = Option.empty[(String, InstanceRecord)]
    _episode(directory, initialize = true, evidence = true) { e =>
      val id = e.start()
      e.advance(id)
      Files.writeString(directory.resolve("repository/step-1.txt"), "Installed CLI must run this Test itself.\n")
      e.submit(e.work(id))
      captured = Some((id, e.current(id)))
    }
    NativeWorkflowCliFixture.journalReader(directory, _plan())
    (directory, captured.get._1, captured.get._2)
  }

  /** Real managed Operations qualify the separately admitted new Human directive.
    * The directive, review and dependency request are specification-owned inputs.
    * The first Step commit, pending question and native SQL history are actual.
    */
  private[admission] def qualifyRemainingPlanAdoption(narrator: GivenWhenThen): Unit = {
    narrator.Given("an actual two-Step Session with its first Step accepted and committed")
    val directory = _root()
    val initial = _plan(two = true)
    var session = ""
    var original = Option.empty[InstanceRecord]
    var question = Option.empty[WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing]]
    var target = Option.empty[GoalPhaseRemainingPlanAdoption.Target]
    _episode(directory, initialize = true, selected = initial) { e =>
      session = e.start()
      e.advance(session)
      Files.writeString(directory.resolve("repository/step-1.txt"), "Accepted original first Step.\n")
      e.submit(e.work(session))
      val firstreview = e.advance(session)
      e.submit(e.work(session), Some(firstreview), "accept")
      val waiting = e.advance(session)
      e.authority(session, waiting)
      e.advance(session).work.get.stepIdentity shouldBe Some("step-2")
      Files.writeString(directory.resolve("repository/step-2.txt"), "Second Step requiring a correction.\n")
      e.submit(e.work(session))
      val review = e.advance(session)
      e.submit(e.work(session), Some(review), "repair", findings = Vector("Correct the second Step before acceptance"))
      e.advance(session).work.get.kind shouldBe "RepairStep"
      val dependency = GoalPhaseDependencyScope.Proposal("spec-old-dependency", "1", "REPAIR",
        GoalPhaseDependencyScope.Component("SmWorkflow", "sm-workflow", "repository"),
        GoalPhaseDependencyScope.Component("OtherComponent", "other-library", "repository"),
        ContextReference("old-usage", "1"), "Old proposed dependency contract needs reconsideration",
        Vector("src/old.scala"), Vector("Old dependency acceptance"), Vector(ContextReference("step-2", "1")), Vector.empty)
      e.submit(e.work(session), outcome = "decision-needed", dependencyProposal = Some(dependency))
      e.advance(session)
      question = Some(e.question(session))
      val before = e.current(session)
      original = Some(before)
      val raw = initial.copy(revision = "remaining-2", steps = initial.steps.updated(1,
        initial.steps(1).copy(purpose = "Use operation-owned Evidence for the remaining Step")))
      val proposal = GoalPhasePlanRevision.publicProposal(Some(GoalPhasePlanRevision.Proposal(
        "spec-new-contract", "2", "Actual new direction replaces the unaccepted future contract", raw))).get
      narrator.When("public description captures the actual unanswered frontier and proposed remaining contract")
      val description = e.invoke[V.GoalPhaseRemainingPlanDescriptionResult](GoalPhaseRemainingPlanAdoption.describeOperation,
        List(Property("input", V.GoalPhaseRemainingPlanDescriptionInput(session, proposal).toRecord(), None)))
      val captured = take(GoalPhaseRemainingPlanAdoption.targetC(description.target))
      target = Some(captured)
      narrator.Then("the target includes the actual accepted commit, unchanged base and nonzero repair history")
      captured.completed.size shouldBe 1
      captured.completed.head.commit.identity shouldBe e.observe().head
      captured.repairProgress shouldBe GoalPhaseRepairProgress.Snapshot(1, 1, 2)
      _same_instance(e.current(session), before)
      val input = V.GoalPhaseRemainingPlanAdoptionInput(session, description.target, proposal,
        "SPECIFICATION-OWNED new Human direction; no answer to the old dependency question",
        V.WorkflowArtifactReference("spec-new-human-directive", "1"))
      def _request_(value: V.GoalPhaseRemainingPlanAdoptionInput) = e.call(GoalPhaseRemainingPlanAdoption.adoptionOperation,
        List(Property("input", value.toRecord(), None)))
      narrator.When("a caller supplies the proposal without independent Human provenance")
      val missing = _request_(input)
      narrator.Then("it refuses and leaves the old question and actual native history intact")
      missing.isSuccess shouldBe false
      _same_instance(e.current(session), before)
      e.question(session) shouldBe question.get
      narrator.When("the specification host records the matching separately observed directive and public adoption runs")
      e.prepareRemaining(description, input)
      val crossed = _request_(input.copy(rationale = "A different unobserved directive"))
      val result = e.invoke[V.GoalPhaseRemainingPlanAdoptionResult](GoalPhaseRemainingPlanAdoption.adoptionOperation,
        List(Property("input", input.toRecord(), None)))
      val after = e.current(session)
      narrator.Then("only that directive succeeds once and the native transaction appends one adjacent history entry")
      crossed.isSuccess shouldBe false
      result.accepted shouldBe true
      result.outcome shouldBe "remaining-plan-adopted"
      after.history.take(before.history.size) shouldBe before.history
      after.revision shouldBe before.revision.next
      after.suspension shouldBe None
      after.history.last.causation.value shouldBe input.reference.identity
      e.retainedQuestion(session, captured.questionIdentity) shouldBe question.get
      _request_(input).isSuccess shouldBe false
      _same_instance(e.current(session), after)
    }
    narrator.Given("the producer runtime has closed and the same managed SQL Session reopens")
    _episode(directory, selected = initial) { e =>
      narrator.When("ordinary Advance reconstructs the retained adoption and issues only the unaccepted second Step")
      val next = e.advance(session)
      val assigned = _business(e.work(session))
      narrator.Then("the new work preserves the accepted prefix, its commit and repairs without answering the old Human")
      next.sessionId shouldBe session
      assigned.kind shouldBe "ImplementStep"
      assigned.stepIdentity shouldBe Some("step-2")
      assigned.selectedPlan.revision shouldBe "remaining-2"
      assigned.selectedPlan.steps.head shouldBe initial.steps.head
      assigned.selectedPlan.steps(1).purpose should include("operation-owned Evidence")
      assigned.completed shouldBe target.get.completed
      assigned.originalBase shouldBe target.get.base
      assigned.repairProgress shouldBe Some(target.get.repairProgress)
      e.observe().head shouldBe target.get.completed.head.commit.identity
      e.current(session).history.take(original.get.history.size) shouldBe original.get.history
      e.retainedQuestion(session, target.get.questionIdentity) shouldBe question.get
      narrator.When("the old issued Human answer is submitted after the separate adoption")
      val old = question.get.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]].request
      val answer = ContinuationResult(question.get.handle, old.runId, old.continuationId, old.expectedRevision, old.context.snapshot,
        TypedValue(GoalPhaseOrderedHumanDecision.answerCodec.typeIdentity, GoalPhaseOrderedHumanDecision.Answer(
          "ApproveDependencyScope", "SPECIFICATION-OWNED stale old answer")), ContextReference("spec-stale-answer", "1"),
        Vector.empty, ExecutionEvidence(Vector(ContextReference("spec-stale-answer", "1")), Some("participant")))
      val before = e.current(session)
      val stale = e.call(GoalPhaseOrderedHumanDecision.completionOperation.operation, take(NativeWorkflowOperationInputFixture.submissionC(answer)))
      narrator.Then("the stale answer refuses without changing the resumed native history")
      stale.isSuccess shouldBe false
      _same_instance(e.current(session), before)
    }
  }

  /** Actual SQL/Git/check history with fixture-owned independent review and
    * Human direction. The historical Review remains decision-needed.
    */
  private[admission] def qualifyReviewRepairAdoption(narrator: GivenWhenThen): Unit = {
    val directory = _root()
    val conditions = (1 to 30).map(n => GoalPhaseAcceptanceBoundary.Condition("phase-" + n, "phase", true,
      "Original Phase condition " + n)).toVector
    val initial = _plan(two = true).copy(repairLimit = 10,
      acceptance = Some(GoalPhaseAcceptanceBoundary.Adoption("Original selected usage", conditions)))
    val added = GoalPhaseAcceptanceBoundary.Condition("preserve-step-admission", "step", true,
      "Preserve Step admission during compatible document continuation")
    val raw = initial.copy(revision = "review-repair-2", acceptance = initial.acceptance.map(a =>
      a.copy(conditions = a.conditions :+ added)))
    val proposal = GoalPhasePlanRevision.publicProposal(Some(GoalPhasePlanRevision.Proposal("review-human-condition", "1",
      "Adopt the actual independent blocker as a future Step condition", raw))).get
    var session = ""
    var before = Option.empty[InstanceRecord]
    var oldquestion = Option.empty[WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing]]
    var captured = Option.empty[GoalPhaseRemainingPlanAdoption.Target]
    var reviewresult = Option.empty[ContinuationResult[S.Result]]
    narrator.Given("an isolated actual two-Step Session with thirty phase-only conditions and an accepted first Step")
    _episode(directory, initialize = true, selected = initial) { e =>
      session = e.start()
      e.advance(session)
      Files.writeString(directory.resolve("repository/step-1.txt"), "Accepted first Step\n")
      e.submit(e.work(session))
      val firstreview = e.advance(session)
      e.submit(e.work(session), Some(firstreview), "accept")
      val closing = e.advance(session)
      e.authority(session, closing)
      e.advance(session).work.get.stepIdentity shouldBe Some("step-2")
      Files.writeString(directory.resolve("repository/step-2.txt"), "Current candidate with an actual blocker\n")
      e.submit(e.work(session))
      val review = e.advance(session)
      reviewresult = Some(e.submit(e.work(session), Some(review), "decision-needed",
        findings = Vector("The selected Step admission must survive compatible document continuation")))
      e.advance(session)
      oldquestion = Some(e.question(session))
      before = Some(e.current(session))
      val checks = _checks(directory)
      narrator.When("read-only description binds the actual saved Review, reconstructed OPEN issue, candidate and checks")
      val description = e.invoke[V.GoalPhaseRemainingPlanDescriptionResult](GoalPhaseRemainingPlanAdoption.describeOperation,
        List(Property("input", V.GoalPhaseRemainingPlanDescriptionInput(session, proposal).toRecord(), None)))
      val target = take(GoalPhaseRemainingPlanAdoption.targetC(description.target))
      captured = Some(target)
      narrator.Then("the old accepted prefix and thirty original raw/effective conditions remain exact")
      _same_instance(e.current(session), before.get)
      target.completed.size shouldBe 1
      target.captured.plan.steps shouldBe target.plan.steps
      target.captured.plan.effectiveAcceptance.get.conditions.init shouldBe target.plan.effectiveAcceptance.get.conditions
      target.captured.plan.acceptance.get.conditions.init shouldBe conditions
      target.reviewRepair.get.conditionIdentity shouldBe added.identity
      target.reviewRepair.get.candidate shouldBe oldquestion.get.current.asInstanceOf[
        WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]].request.input.get.value.work.candidate.get
      val input = V.GoalPhaseRemainingPlanAdoptionInput(session, description.target, proposal,
        "SPECIFICATION-OWNED independent Human adopts this condition for newly issued and future Step work",
        V.WorkflowArtifactReference("spec-review-repair-direction", "1"))
      def _adopt_(value: V.GoalPhaseRemainingPlanAdoptionInput) = e.call(GoalPhaseRemainingPlanAdoption.adoptionOperation,
        List(Property("input", value.toRecord(), None)))
      narrator.When("missing and then crossed independent direction attempts the exact target")
      val missing = _adopt_(input)
      e.prepareRemaining(description, input)
      val crossed = _adopt_(input.copy(rationale = "An unobserved direction"))
      val wrongcandidate = target.copy(reviewRepair = target.reviewRepair.map(r => r.copy(candidate = r.candidate.copy(tree = "f" * 40))))
      val wrong = _adopt_(input.copy(target = GoalPhaseRemainingPlanAdoption.targetRecord(wrongcandidate)))
      narrator.Then("all fail without changing the pending question or native history")
      Vector(missing, crossed, wrong).foreach(_.isSuccess shouldBe false)
      _same_instance(e.current(session), before.get)
      narrator.When("the separately prepared actual direction is adopted once")
      take(_adopt_(input))
      val after = e.current(session)
      narrator.Then("one adjacent supersession retains the unanswered old question and does not run any checks")
      after.revision shouldBe before.get.revision.next
      after.history.take(before.get.history.size) shouldBe before.get.history
      after.history.last.causation.value shouldBe input.reference.identity
      e.retainedQuestion(session, target.questionIdentity) shouldBe oldquestion.get
      _checks(directory) shouldBe checks
      _adopt_(input).isSuccess shouldBe false
      _same_instance(e.current(session), after)
    }
    narrator.Given("the same managed SQL Session reopens after its original runtime has closed")
    _episode(directory, selected = initial) { e =>
      narrator.When("ordinary Advance traverses the saved supersession")
      val next = e.advance(session)
      val assigned = _business(e.work(session))
      narrator.Then("exactly the new RepairStep retains Session base prefix review context OPEN issue and ordinary consumption")
      next.sessionId shouldBe session
      assigned.kind shouldBe "RepairStep"
      assigned.stepIdentity shouldBe Some("step-2")
      assigned.originalBase shouldBe captured.get.base
      assigned.completed shouldBe captured.get.completed
      assigned.generation shouldBe captured.get.generation + 1
      assigned.repairProgress shouldBe Some(GoalPhaseRepairProgress.Snapshot(1, 1, 10))
      assigned.selectedPlan shouldBe captured.get.captured.plan
      assigned.candidate shouldBe None
      assigned.checksReceipt shouldBe None
      assigned.issues.get.issues.find(_.identity == captured.get.reviewRepair.get.issueIdentity).get.status shouldBe "OPEN"
      assigned.repairDirective.get.findings shouldBe reviewresult.get.result.value.findings
      assigned.review.get.priorReviews.last.result shouldBe reviewresult.get.result.value
      assigned.review.get.priorReviews.last.checksReceipt shouldBe captured.get.reviewRepair.get.checksReceipt
      e.current(session).history.take(before.get.history.size) shouldBe before.get.history
      e.retainedQuestion(session, captured.get.questionIdentity) shouldBe oldquestion.get
      val question = oldquestion.get.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]].request
      val answer = ContinuationResult(oldquestion.get.handle, question.runId, question.continuationId, question.expectedRevision,
        question.context.snapshot, TypedValue(GoalPhaseOrderedHumanDecision.answerCodec.typeIdentity,
          GoalPhaseOrderedHumanDecision.Answer("ResumeAssignedWork", "SPECIFICATION-OWNED stale answer")),
        ContextReference("spec-review-stale-answer", "1"), Vector.empty,
        ExecutionEvidence(Vector(ContextReference("spec-review-stale-answer", "1")), Some("participant")))
      narrator.When("a delayed old answer attempts to resume the superseded Review")
      val stale = e.call(GoalPhaseOrderedHumanDecision.completionOperation.operation,
        take(NativeWorkflowOperationInputFixture.submissionC(answer)))
      narrator.Then("the answer refuses and repeated Advance retains the same new repair")
      stale.isSuccess shouldBe false
      e.advance(session) shouldBe next
      narrator.When("the assigned repair completes and ordinary Advance observes its candidate and real checks")
      Files.writeString(directory.resolve("repository/step-2.txt"), "Corrected Step admission continuation\n")
      e.submit(e.work(session))
      val checked = e.advance(session)
      narrator.Then("ordinary independent ReviewStep follows and the new condition is applicable without retroactively rewriting the old review")
      checked.work.get.kind shouldBe "ReviewStep"
      _business(e.work(session)).selectedPlan shouldBe captured.get.captured.plan
      e.retainedQuestion(session, captured.get.questionIdentity) shouldBe oldquestion.get
      reviewresult.get.result.value.outcome shouldBe "decision-needed"
      reviewresult.get.result.value.acceptance.get.conditions shouldBe empty
    }
  }
  private def _request[A](work: WorkflowInteraction[A, Nothing]): ContinuationRequest[A] =
    work.current.asInstanceOf[WorkflowContinuation.WorkOrder[A]].request
  private def _business[A](work: WorkflowInteraction[A, Nothing]): A = _request(work).input.get.value
  private def _same_instance(actual: InstanceRecord, expected: InstanceRecord): Unit = {
    actual.definition.workflowIdentity shouldBe expected.definition.workflowIdentity
    actual.definition.workflowRevision shouldBe expected.definition.workflowRevision
    actual.copy(definition = expected.definition) shouldBe expected
  }
  private def _checks(directory: Path): Int = {
    val file = directory.resolve("sbt-executions.txt")
    if (Files.exists(file)) Files.readString(file).linesIterator.size else 0
  }
  private final class Episode(val directory: Path, owner: Subsystem, component: Component, actor: ExecutionContext,
    prepare: (Setup => Consequence[Unit]) => Unit) {
    def call(operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
      Request.of(component = component.name, service = "GoalPhaseService", operation = operation, properties = properties), actor)
    def invoke[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A = read_value[A](take(call(operation, properties)))
    def start(proposal: Option[String] = None): String = invoke[V.GoalPhaseStartResult]("startGoalPhase", NativeWorkflowOperationInputFixture.goal(_input, proposal)).sessionId
    def advance(id: String): V.GoalPhaseAdvanceResult = invoke[V.GoalPhaseAdvanceResult]("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(id))
    def current(id: String): InstanceRecord = {
      var value = Option.empty[InstanceRecord]
      prepare(call => call.goalCurrentC(InstanceIdentity(id)).map(v => { value = Some(v); () }))
      value.get
    }
    def work(id: String): WorkflowInteraction[S.Request, Nothing] = {
      var value = Option.empty[WorkflowInteraction[S.Request, Nothing]]
      prepare(call => call.goalWorkC(InstanceIdentity(id)).map(v => { value = Some(v); () }))
      value.get
    }
    def recording(id: String): WorkflowInteraction[R.Request, Nothing] = {
      var value = Option.empty[WorkflowInteraction[R.Request, Nothing]]
      prepare(call => call.goalRecordingC(InstanceIdentity(id)).map(v => { value = Some(v); () }))
      value.get
    }
    def question(id: String): WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing] = {
      var value = Option.empty[WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing]]
      prepare(call => call.goalQuestionC(InstanceIdentity(id)).map(v => { value = Some(v); () }))
      value.get
    }
    def retainedQuestion(id: String, questionId: String): WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing] = {
      var value = Option.empty[WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing]]
      prepare(call => for {
        catalog <- GoalPhaseRuntimeCatalog.loadC()
        storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
          SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
        reader <- storage.bindDecisionC(GoalPhaseOrderedHumanDecision.completionOperation, GoalPhaseOrderedHumanDecision.inputCodec)
        retained <- reader.loadC(org.goldenport.cncf.workflow.ContinuationIdentity(questionId))(using call.executionContext)
      } yield { value = retained; () })
      value.get
    }
    def prepareRemaining(description: V.GoalPhaseRemainingPlanDescriptionResult, input: V.GoalPhaseRemainingPlanAdoptionInput): Unit =
      prepare(call => for {
        prepared <- NativeRemainingPlanPreparation.prepareC(description, input, input.reference,
          V.WorkflowExecutionEvidence(Vector(input.reference), Some("participant"), None, None), "participant",
          "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
        _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
      } yield ())
    def observe(): RepositorySyncManagedGitObservation.Observed = {
      var value = Option.empty[RepositorySyncManagedGitObservation.Observed]
      prepare(call => call.observeC().map(v => { value = Some(v); () }))
      value.get
    }
    def index(): String = {
      var value = Option.empty[String]
      prepare(call => RepositorySyncManagedGitObservation.indexTreeC(call.core).map(v => { value = Some(v); () }))
      value.get
    }
    def submit(work: WorkflowInteraction[S.Request, Nothing], display: Option[V.GoalPhaseAdvanceResult] = None,
      outcome: String = "completed", observations: Option[R.Observations] = None, findings: Vector[String] = Vector.empty,
      dependencyProposal: Option[GoalPhaseDependencyScope.Proposal] = None): ContinuationResult[S.Result] = {
      val order = _request(work)
      val assigned = _business(work)
      val reports = assigned.issues.filter(_ => assigned.kind != "ImplementStep").map { state =>
        val relevant = state.issues.filter(GoalPhaseFixIssues.relevant(assigned, _))
        GoalPhaseFixIssues.Report(if (assigned.kind == "RepairStep") relevant.filter(GoalPhaseFixIssues.unresolved).map(i =>
          GoalPhaseFixIssues.Disposition(i.identity, "FIXED", "SPECIFICATION-OWNED correction of this exact issue", None))
        else relevant.filter(_.source == "REVIEW").map(i => GoalPhaseFixIssues.Disposition(i.identity,
          if (outcome == "accept") "RESOLVED" else "NOT_REVIEWED", "SPECIFICATION-OWNED independent coverage", None)))
      }
      val acceptance = assigned.selectedPlan.effectiveAcceptance.map(boundary => GoalPhaseAcceptanceBoundary.Report(
        GoalPhaseAcceptanceBoundary.applicable(boundary, assigned.kind).map(condition => GoalPhaseAcceptanceBoundary.Observation(
          condition.identity, if (condition.required) "satisfied" else "unsatisfied", "SPECIFICATION-OWNED " + condition.description)), Vector.empty))
      val value = S.Result(outcome, "SPECIFICATION-OWNED assigned semantic response", findings, acceptance = acceptance, issueReport = reports,
        reviewObservations = observations, dependencyProposal = dependencyProposal)
      val reference = ContextReference("specification-recording-" + assigned.kind, order.expectedRevision.value)
      val evidence = ExecutionEvidence(Vector(reference), Some(if (assigned.kind.startsWith("Review")) "specification-reviewer" else "participant"))
      val result = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
        TypedValue(S.resultCodec.typeIdentity, value), reference, Vector.empty, evidence)
      if (assigned.kind.startsWith("Review")) prepare(call => for {
        prepared <- NativeReviewPreparation.goalC(display.get.work.get.review.get.reviewContext.get,
          V.GoalPhaseWorkResult(value.outcome, value.summary, value.findings, GoalPhaseAcceptanceBoundary.publicReport(value.acceptance), None, GoalPhaseFixIssues.publicReport(value.issueReport), None,
            R.publicObservations(value.reviewObservations), None,
            value.completionConditions.map(condition => GoalPhaseNativeMetadata.record(condition.asJson)), None), V.WorkflowArtifactReference(reference.identity, reference.revision),
          V.WorkflowExecutionEvidence(Vector(V.WorkflowArtifactReference(reference.identity, reference.revision)), evidence.workerIdentity, None, None),
          "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
        _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
      } yield ())
      invoke[V.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult", take(NativeWorkflowOperationInputFixture.submissionC(result))).accepted shouldBe true
      result
    }
    def response(work: WorkflowInteraction[R.Request, Nothing], outcomes: Vector[String]): ContinuationResult[R.Result] = {
      val order = _request(work)
      val assigned = _business(work)
      val items = assigned.items.zip(outcomes).map { (item, outcome) => R.ItemResult(item.sourceReference, item.observation.identity, outcome,
        if (outcome == "failed") Vector.empty else Vector(R.SavedSection(item.destination, "Recorded " + item.observation.identity)),
        "SPECIFICATION-OWNED honest " + outcome + " disposition") }
      val outcome = if (outcomes.forall(_ == "recorded")) "completed" else if (outcomes.forall(_ == "failed")) "failed" else "partial"
      val reference = ContextReference("specification-recording-result", order.expectedRevision.value)
      ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
        TypedValue(R.resultCodec.typeIdentity, R.Result(outcome, "SPECIFICATION-OWNED finite recording outcome", items)),
        reference, Vector.empty, ExecutionEvidence(Vector(reference), Some("participant")))
    }
    def submitRecording(value: ContinuationResult[R.Result]): V.GoalPhaseFindingRecordingSubmissionResult =
      invoke[V.GoalPhaseFindingRecordingSubmissionResult]("submitGoalPhaseFindingRecording", take(NativeWorkflowOperationInputFixture.submissionC(value)))
    def readJournal(input: V.JournalReadInput): V.JournalReadResult = read_value[V.JournalReadResult](take(
      owner.executeOperationResponseInContext(Request.of(component = component.name, service = "JournalService",
        operation = "readJournalEntry", properties = List(Property("input", input.toRecord(), None))), actor)))
    def writeAssigned(work: WorkflowInteraction[R.Request, Nothing], indices: Set[Int] = Set.empty): Unit =
      _business(work).items.zipWithIndex.filter((_, index) => indices.isEmpty || indices(index)).foreach { (item, _) =>
      val file = directory.resolve("repository").resolve(item.destination)
      Files.writeString(file, Files.readString(file) + "\n## Recorded " + item.observation.identity + "\n\n" +
        item.sourceReference.identity + "@" + item.sourceReference.revision + " / " + item.observation.identity + "\n\n" + item.observation.rationale + "\n")
    }
    def authority(id: String, waiting: V.GoalPhaseAdvanceResult, child: Boolean = false): Unit = {
      val from = current(id)
      val selection = _repository_selection_(_host(directory).repository.input, "specification-recording-child",
        id + ":repository-closure:" + from.revision.value)
      prepare(call => for {
        prepared <- NativeAuthorityPreparation.goalC(waiting.authorityContext.get,
          V.WorkflowArtifactReference("specification-recording-authority", from.revision.value.toString), "participant",
          "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
        _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
        _ <- if (child) _prepare_input_(call, _host(directory).repository.input, selection, directory, merged = true) else Consequence.unit
      } yield ())
    }
    def recordingCandidate(id: String): GoalPhaseNativeRecordingCandidate.Saved = {
      val record = current(id)
      val entry = record.history.zipWithIndex.find(_._1.causation.value == "ordered-recording-candidate:inspection").get
      val from = take(GoalPhaseNativeHistory.prefixC(record, record.history(entry._2 - 1).revision))
      var value = Option.empty[GoalPhaseNativeRecordingCandidate.Saved]
      prepare(call => for {
        catalog <- GoalPhaseRuntimeCatalog.loadC()
        storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
          SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
        reader <- storage.bindMechanicalC(GoalPhaseNativeRun.advanceOperation, GoalPhaseNativeRecordingCandidate.codec)
        retained <- reader.loadRetainedC(WorkflowMechanicalTransition.Reference(id + ":ordered-recording-candidate:" + from.revision.value + ":candidate", "1"))(using call.executionContext)
        _ <- retained.map(r => { value = Some(GoalPhaseNativeRecordingCandidate.Saved(r.receipt.next, r.receipt.payload.value)); Consequence.unit })
          .getOrElse(Consequence.stateConflict("Specification has no actual final recording candidate"))
      } yield ())
      value.get
    }
  }
  private def _phase_review(e: Episode): (String, V.GoalPhaseAdvanceResult, WorkflowInteraction[S.Request, Nothing]) = {
    val id = e.start()
    e.advance(id)
    Files.writeString(e.directory.resolve("repository/step-1.txt"), "Assigned required implementation.\n")
    e.submit(e.work(id))
    val display = e.advance(id)
    e.submit(e.work(id), Some(display), "accept")
    val wait = e.advance(id)
    e.authority(id, wait)
    val phase = e.advance(id)
    (id, phase, e.work(id))
  }

  /** Capture a real file-only contract before host adoption, then close its runtime. */
  private def _capture_legacy_recording(directory: Path, outcomes: Vector[String]):
      (String, WorkflowInteraction[R.Request, Nothing], ContinuationResult[R.Result], InstanceRecord) = {
    var captured = Option.empty[(String, WorkflowInteraction[R.Request, Nothing], ContinuationResult[R.Result], InstanceRecord)]
    _episode(directory, initialize = true, journal = None) { e =>
      val (id, phase, review) = _phase_review(e)
      e.submit(review, Some(phase), "accept", Some(R.Observations(Vector(_idea))))
      e.advance(id)
      val assigned = e.recording(id)
      val reply = e.response(assigned, outcomes)
      if (outcomes.head == "recorded") e.writeAssigned(assigned)
      captured = Some((id, assigned, reply, e.current(id)))
    }
    captured.get
  }

  /** Leave the actual issued recording unconsumed for the parent's installed CLI.
    * All semantic responses and assigned file edits are SPECIFICATION-OWNED.
    */
  private[admission] def prepareJournalForInstalledCli(): GoalPhaseNativeFindingRecordingSpec.InstalledHandoff = {
    val directory = _root()
    val (id, assigned, reply, pending) = _capture_legacy_recording(directory, Vector("recorded"))
    _episode(directory, initialize = false, journal = Some(NativeWorkflowCliFixture.JournalHost(_journal))) { e =>
      When("the same closed producer reopens with Journal configured without consuming its captured recording")
      val retained = e.current(id)
      val issued = e.recording(id)
      Then("the actual pending instance and exact issued file-only request remain unchanged")
      _same_instance(retained, pending)
      issued shouldBe assigned
    }
    NativeWorkflowCliFixture.journalReader(directory, _plan())
    val reference = V.WorkflowArtifactReference(reply.resultReference.identity, reply.resultReference.revision)
    val evidence = V.WorkflowExecutionEvidence(reply.evidence.references.map(r =>
      V.WorkflowArtifactReference(r.identity, r.revision)), reply.evidence.workerIdentity, None,
      reply.evidence.execution.map(e => V.WorkflowExecutionObservation(e.placement.value,
        e.originContextIdentity, e.executorContextIdentity, e.profileIdentity)))
    val publicrecord = Record.dataAuto(
      "sessionId" -> reply.handle.instanceIdentity.value,
      "result" -> NativeWorkflowOperationOutput.goalRecordingResult(reply.result.value).toRecord(),
      "completionFacts" -> reply.completionFacts.map(r => V.WorkflowArtifactReference(r.identity, r.revision).toRecord()),
      "executionEvidence" -> evidence.toRecord(),
      "resultReference" -> reference.toRecord())
    val submission = io.circe.parser.parse(take(publicrecord.toJsonStringC)).toOption.get
    GoalPhaseNativeFindingRecordingSpec.InstalledHandoff(directory, id, _business(assigned), reply, pending, submission)
  }

  /** Reusable real Operation episode for the fresh-process public read spec.
    * Closing _episode closes the producer runtime before this method returns.
    */
  private[admission] def produceJournalForCli(): (Path, O.Plan, V.JournalEntry) = {
    Given("a normal managed producer issuing an actual file-only recording with Journal disabled before host adoption")
    val directory = _root()
    val (id, assigned, reply, pending) = _capture_legacy_recording(directory, Vector("recorded"))
    var saved = Option.empty[V.JournalEntry]
    _episode(directory, initialize = false, journal = Some(NativeWorkflowCliFixture.JournalHost(_journal))) { e =>
      Given("the closed runtime reopened on the same managed SQL Session with Journal declared and its captured file request intact")
      _same_instance(e.current(id), pending)
      e.recording(id) shouldBe assigned
      val proposed = DevelopmentJournal.identity(reply.resultReference)
      When("the adopted host publicly reads the proposed result key before fulfilling the captured recording")
      val absent = e.readJournal(V.JournalReadInput(proposed, None))
      Then("the proposed Journal entry is absent before the recording completion")
      absent.status shouldBe "absent"
      When("the actual recording completion submits through the managed Operation")
      val ack = e.submitRecording(reply)
      Then("the committed business result is accepted and its Journal append reports recorded")
      ack.accepted shouldBe true
      ack.journalRecording.get.status shouldBe "recorded"
      val actual = e.current(id)
      val read = e.readJournal(V.JournalReadInput(ack.journalRecording.get.entry.get.identity, None))
      Then("a separate public read returns the actual committed revision source and admitted observation")
      read.status shouldBe "found"
      read.entry.get.recording.get.committedRevision shouldBe actual.revision.value.toString
      read.entry.get.sourceReference shouldBe V.WorkflowArtifactReference(reply.resultReference.identity, reply.resultReference.revision)
      read.entry.get.recording.get.items.head.observation.identity shouldBe _idea.identity
      saved = read.entry
    }
    (directory, _plan(), saved.get)
  }

  "Connected development Journal recording" should {
    "append only after actual managed completion and read the exact admitted observation and result" in {
      When("the shared producer episode commits the actual recording completion and closes its runtime")
      val (_, _, entry) = produceJournalForCli()
      Then("the separately read entry contains the original review meaning, requested destination, saved section and honest outcome")
      entry.kind shouldBe "ExecutionObservation"
      entry.recording.get.items.head.observation shouldBe V.GoalPhaseReviewObservation(_idea.identity, _idea.category, _idea.summary, _idea.rationale)
      entry.recording.get.items.head.requestedDestination shouldBe _candidates
      entry.recording.get.items.head.records shouldBe Vector(V.GoalPhaseFindingSavedSection(_candidates, "Recorded " + _idea.identity))
      entry.recording.get.items.head.outcome shouldBe "recorded"
      entry.finding shouldBe None
    }

    "retain actual business acceptance when the separate Journal Create scope is unavailable" in {
      Given("a captured real file-only recording issued before Journal adoption and now closed")
      val directory = _root()
      val (id, work, reply, pending) = _capture_legacy_recording(directory, Vector("failed"))
      _episode(directory, initialize = false, journal = Some(NativeWorkflowCliFixture.JournalHost(_journal, grantCreate = false))) { e =>
        Given("the same Session reopened with Journal declared and its separate Create grant withheld")
        _same_instance(e.current(id), pending)
        e.recording(id) shouldBe work
        val prior = e.current(id)
        When("actual managed completion commits and the after-commit Journal bind fails")
        val ack = e.submitRecording(reply)
        val committed = e.current(id)
        Then("the business result remains accepted with one actual completion and no stored Journal claim")
        ack.accepted shouldBe true
        ack.journalRecording.get.status shouldBe "unavailable"
        ack.journalRecording.get.entry shouldBe None
        committed.revision shouldBe prior.revision.next
        committed.suspension shouldBe None
        committed.history.count(_.causation.value == reply.resultReference.identity) shouldBe 1
      }
    }
  }

  "Automatic native Finding recording" should {
    "record an actual accepted Phase review through normal Advance and publicly read its full original context" in {
      Given("a normal managed Phase, a configured Journal and specification-owned accepted Phase review observations")
      val directory = _root()
      _episode(directory, initialize = true, journal = Some(NativeWorkflowCliFixture.JournalHost(_journal))) { e =>
        val (id, display, review) = _phase_review(e)
        val submitted = e.submit(review, Some(display), "accept", Some(R.Observations(Vector(_idea, _observation))))
        val accepted = e.current(id)
        val key = DevelopmentJournalFinding.identity(submitted.resultReference, _idea.identity)
        e.readJournal(V.JournalReadInput(key, None)).status shouldBe "absent"
        val files = Vector(_hygiene, _candidates).map(p => p -> Files.readString(directory.resolve("repository").resolve(p))).toMap
        val count = _checks(directory)
        When("normal Advance automatically records the eligible Findings without a clerical Submit")
        val next = e.advance(id)
        val actual = e.current(id)
        val batch = next.findingRecording.get.journaled.head
        val read = e.readJournal(V.JournalReadInput(key, Some("1")))
        Then("the actual immutable Finding, every honest per-item state and accepted Phase source are exposed")
        next.findingRecording.get.pending shouldBe empty
        next.findingRecording.get.completed shouldBe empty
        next.findingRecording.get.request shouldBe None
        batch.sourceRevision shouldBe accepted.revision.value.toString
        batch.committedRevision shouldBe accepted.revision.next.next.value.toString
        batch.sources.map(_.observation.identity) shouldBe Vector(_idea.identity, _observation.identity)
        batch.items.map(_.status) shouldBe Vector("recorded", "recorded")
        batch.items.head.entry shouldBe Some(V.JournalEntryReference(key, "1"))
        read.status shouldBe "found"
        val entry = read.entry.get
        entry.identity shouldBe key
        entry.kind shouldBe "Finding"
        entry.revision shouldBe "1"
        entry.recording shouldBe None
        entry.sessionIdentity shouldBe id
        entry.repositoryIdentity shouldBe _input.repositoryIdentity
        entry.phaseIdentity shouldBe _input.phaseIdentity
        entry.stepIdentity shouldBe None
        entry.sourceReference shouldBe V.WorkflowArtifactReference(submitted.resultReference.identity, submitted.resultReference.revision)
        entry.finding.get.observation shouldBe V.GoalPhaseReviewObservation(_idea.identity, _idea.category, _idea.summary, _idea.rationale)
        entry.finding.get.sourceKind shouldBe "ReviewPhase"
        entry.finding.get.eligibilityStage shouldBe "PhaseClosing"
        entry.finding.get.sourceRevision shouldBe accepted.revision.value.toString
        entry.finding.get.completed.map(_.commit.identity) shouldBe _business(review).completed.map(_.commit.identity)
        entry.finding.get.candidate shouldBe _business(review).candidate.map(c => V.GoalPhaseNativeSnapshot(c.head, c.tree))
        entry.finding.get.reviewEvidence shouldBe Some(submitted.resultReference.identity)
        read.view.get should include(_idea.rationale)
        actual.history.take(accepted.history.size) shouldBe accepted.history
        actual.history.count(_.causation.value == "ordered-journal-findings-intent") shouldBe 1
        actual.history.count(_.causation.value == "ordered-journal-findings-result") shouldBe 1
        actual.history.exists(_.causation.value.startsWith("ordered-recording-issued:")) shouldBe false
        files.foreach((path, text) => Files.readString(directory.resolve("repository").resolve(path)) shouldBe text)
        When("normal Advance reconstructs the known batch again")
        val repeated = e.advance(id)
        Then("it consumes the batch once and neither appends nor requests clerical recording again")
        repeated.findingRecording shouldBe next.findingRecording
        e.current(id).history.count(_.causation.value == "ordered-journal-findings-intent") shouldBe 1
        e.current(id).history.count(_.causation.value == "ordered-journal-findings-result") shouldBe 1
        e.readJournal(V.JournalReadInput(key, None)).entry shouldBe read.entry
        _checks(directory) shouldBe count
      }
    }

    "wait for the actual reviewed Step commit before automatically recording and issuing the next Step" in {
      Given("a real two-Step managed Phase with Journal declared and a specification-owned nonblocking Step review observation")
      val directory = _root()
      _episode(directory, initialize = true, selected = _plan(two = true), journal = Some(NativeWorkflowCliFixture.JournalHost(_journal))) { e =>
        val id = e.start()
        e.advance(id)
        Files.writeString(directory.resolve("repository/step-1.txt"), "Assigned required implementation.\n")
        e.submit(e.work(id))
        val display = e.advance(id)
        val review = e.submit(e.work(id), Some(display), "accept", Some(R.Observations(Vector(_observation))))
        val key = DevelopmentJournalFinding.identity(review.resultReference, _observation.identity)
        When("normal Advance reaches the separate Step commit authority frontier")
        val waiting = e.advance(id)
        Then("review acceptance alone has neither appended a Finding nor consumed the pending observation")
        waiting.findingRecording.get.pending.map(_.observation.identity) shouldBe Vector(_observation.identity)
        waiting.findingRecording.get.journaled shouldBe empty
        e.readJournal(V.JournalReadInput(key, None)).status shouldBe "absent"
        e.current(id).history.exists(_.causation.value == "ordered-journal-findings-intent") shouldBe false
        When("actual authority admits the reviewed Step commit and normal Advance reaches its eligible frontier")
        e.authority(id, waiting)
        val next = e.advance(id)
        val assigned = _business(e.work(id))
        val actualcommit = assigned.completed.last
        val entry = e.readJournal(V.JournalReadInput(key, None)).entry.get
        Then("the automatically recorded context contains that actual commit and the original workflow continues at Step two")
        assigned.kind shouldBe "ImplementStep"
        assigned.stepIdentity shouldBe Some("step-2")
        actualcommit.commit.identity shouldBe e.observe().head
        entry.stepIdentity shouldBe Some("step-1")
        entry.finding.get.completed.last.commit.identity shouldBe actualcommit.commit.identity
        entry.finding.get.completed.last.review shouldBe Some(review.resultReference.identity)
        entry.finding.get.eligibilityStage shouldBe "Implementation"
        next.findingRecording.get.pending shouldBe empty
        next.findingRecording.get.journaled.flatMap(_.items).map(_.status) shouldBe Vector("recorded")
        next.findingRecording.get.completed shouldBe empty
        e.current(id).history.exists(_.causation.value.startsWith("ordered-recording-issued:")) shouldBe false
      }
    }

    "surface unavailable automatic recording once while preserving the actual accepted Phase business state" in {
      Given("an actual accepted Phase review and a Journal whose independent Create grant is withheld")
      val directory = _root()
      _episode(directory, initialize = true, journal = Some(NativeWorkflowCliFixture.JournalHost(_journal, grantCreate = false))) { e =>
        val (id, display, review) = _phase_review(e)
        val submitted = e.submit(review, Some(display), "accept", Some(R.Observations(Vector(_idea))))
        val accepted = e.current(id)
        When("ordinary Advance attempts automatic recording through the current managed capability")
        val next = e.advance(id)
        val actual = e.current(id)
        val result = next.findingRecording.get.journaled.head.items.head
        Then("business acceptance survives with a truthful unavailable outcome, no Entry claim and no legacy work")
        result.status shouldBe "unavailable"
        result.entry shouldBe None
        next.findingRecording.get.pending shouldBe empty
        next.findingRecording.get.completed shouldBe empty
        next.findingRecording.get.request shouldBe None
        actual.history.take(accepted.history.size) shouldBe accepted.history
        actual.history.count(_.causation.value == submitted.resultReference.identity) shouldBe 1
        actual.currentProgression shouldBe Some(ProgressionReference("phase.PhaseClosing"))
        actual.history.exists(_.causation.value.startsWith("ordered-recording-issued:")) shouldBe false
        When("another ordinary Advance reconstructs the terminal non-success batch")
        val repeated = e.advance(id)
        Then("the known non-success outcome is consumed once without retry or invalidation of accepted review")
        repeated.findingRecording shouldBe next.findingRecording
        e.current(id).history.count(_.causation.value == "ordered-journal-findings-intent") shouldBe 1
        e.current(id).history.count(_.causation.value == "ordered-journal-findings-result") shouldBe 1
      }
    }

    "retain an interrupted durable intent as unknown after reopening without another append or review" in {
      Given("a real managed accepted Phase review and a specification acknowledgement hook interrupting after durable Journal intent")
      val directory = _root()
      var id = ""
      var key = ""
      var saved = Option.empty[InstanceRecord]
      var accepted = Option.empty[InstanceRecord]
      var count = 0
      _episode(directory, initialize = true, journal = Some(NativeWorkflowCliFixture.JournalHost(_journal)),
        journalInterruption = Some("goal-journal-findings-prepared")) { e =>
        val (identity, display, review) = _phase_review(e)
        id = identity
        val submitted = e.submit(review, Some(display), "accept", Some(R.Observations(Vector(_idea))))
        key = DevelopmentJournalFinding.identity(submitted.resultReference, _idea.identity)
        accepted = Some(e.current(id))
        When("normal Advance commits the exact intent and is interrupted before the Journal effect")
        val interrupted = e.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(id))
        saved = Some(e.current(id))
        count = _checks(directory)
        Then("the actual native intent survives with no terminal result or stored Finding")
        interrupted.isSuccess shouldBe false
        saved.get.history.last.causation.value shouldBe "ordered-journal-findings-intent"
        saved.get.history.init shouldBe accepted.get.history
        e.readJournal(V.JournalReadInput(key, None)).status shouldBe "absent"
      }
      _episode(directory, initialize = false, journal = Some(NativeWorkflowCliFixture.JournalHost(_journal))) { e =>
        Given("the same managed SQL Session reopened with its original Journal declaration and no interruption hook")
        When("two ordinary Advances reconstruct the actual intent without a terminal receipt")
        val first = e.advance(id)
        val second = e.advance(id)
        Then("both remain paused with all pending source keys and no second append or consumed review replay")
        first.kind shouldBe "Wait"
        first.message should include("unknown recording outcome")
        first.condition shouldBe Some("phase.PhaseClosing")
        first.findingRecording.get.pending.map(_.observation.identity) shouldBe Vector(_idea.identity)
        first.findingRecording.get.journaled shouldBe empty
        second.findingRecording shouldBe first.findingRecording
        _same_instance(e.current(id), saved.get)
        e.readJournal(V.JournalReadInput(key, None)).status shouldBe "absent"
        _checks(directory) shouldBe count
      }
      _episode(directory, initialize = false, journal = None) { e =>
        Given("the same retained intent reopened after the host removes its Journal declaration")
        When("ordinary Advance reconstructs the frozen policy")
        val conflict = e.advance(id)
        Then("policy mismatch pauses without switching to file recording or reinterpreting the original destination")
        conflict.kind shouldBe "Wait"
        conflict.message should include("policy conflicts")
        conflict.findingRecording.get.request shouldBe None
        conflict.findingRecording.get.pending.size shouldBe 1
        _same_instance(e.current(id), saved.get)
      }
    }
  }

  "Native recording through normal public Operations" should {
    "retain mixed repair observations until the real Step commit then issue one immutable finite batch" in {
      Given("a fresh isolated V7 two-Step Session, exact tracked destinations, and a required blocker with a separate nonblocking observation")
      val directory = _root()
      var id = ""
      var issued = Option.empty[WorkflowInteraction[R.Request, Nothing]]
      var before = Option.empty[InstanceRecord]
      var commit = Option.empty[O.ClosedStep]
      _episode(directory, initialize = true, selected = _plan(two = true)) { e =>
        id = e.start()
        val first = e.advance(id)
        val implementation = e.work(id)
        first.findingRecording shouldBe None
        Files.writeString(directory.resolve("repository/step-1.txt"), "Required source with the observed defect.\n")
        e.submit(implementation)
        val reviewdisplay = e.advance(id)
        val firstreview = e.submit(e.work(id), Some(reviewdisplay), "repair", Some(R.Observations(Vector(_observation))), Vector("Correct required behavior"))
        When("normal Advance selects the real repair and independent re-review without recording before commit")
        val repairdisplay = e.advance(id)
        val repair = e.work(id)
        _business(repair).kind shouldBe "RepairStep"
        repairdisplay.findingRecording.get.pending.map(_.observation.identity) shouldBe Vector(_observation.identity)
        repairdisplay.findingRecording.get.request shouldBe None
        Files.writeString(directory.resolve("repository/step-1.txt"), "Corrected required source.\n")
        e.submit(repair)
        val again = e.advance(id)
        val secondreview = e.submit(e.work(id), Some(again), "accept", Some(R.Observations(Vector(_observation))))
        val waiting = e.advance(id)
        Then("both actual review references wait at separate Step authority, with no clerical issue yet")
        waiting.kind shouldBe "Wait"
        waiting.findingRecording.get.request shouldBe None
        waiting.findingRecording.get.pending.map(_.sourceReference.revision) shouldBe Vector(firstreview.resultReference.revision, secondreview.resultReference.revision)
        e.authority(id, waiting)
        When("the genuine Step commit completes and the next ordinary Advance reaches its safe recording frontier")
        val display = e.advance(id)
        val work = e.recording(id)
        val assigned = _business(work)
        issued = Some(work)
        before = Some(e.current(id))
        commit = assigned.completed.lastOption
        Then("one typed recording assignment captures full actual context after the reviewed commit without a next-Step issue")
        display.submitOperation shouldBe Some("submitGoalPhaseFindingRecording")
        display.resultType shouldBe Some(R.resultCodec.typeIdentity)
        display.issuedWorkContext.get.thinkingMode shouldBe "implementation"
        assigned.items.map(_.sourceReference) shouldBe Vector(firstreview.resultReference, secondreview.resultReference)
        assigned.items.map(_.destination) shouldBe Vector(_hygiene, _hygiene)
        assigned.selectedPlan shouldBe _business(implementation).selectedPlan
        assigned.originalBase shouldBe _business(implementation).originalBase
        assigned.completed.size shouldBe 1
        assigned.completed.head.review shouldBe Some(secondreview.resultReference.identity)
        assigned.completed.head.commit.identity shouldBe e.observe().head
        assigned.requiredIssues.get.issues.map(_.status) shouldBe Vector("RESOLVED")
        assigned.repairProgress shouldBe Some(GoalPhaseRepairProgress.Snapshot(0, 0, 2))
        _request(work).requiredOperation.capability shouldBe "goal-phase-finding-recording-capability"
        _request(work).operation shouldBe StateMachineOperationIdentity("GoalPhaseFindingRecordingService", "recordGoalFindings")
        work.current.asInstanceOf[WorkflowContinuation.WorkOrder[R.Request]].requirement.reviewRequired shouldBe false
        val count = _checks(directory)
        val repeated = e.advance(id)
        _business(e.recording(id)) shouldBe assigned
        repeated.findingRecording shouldBe display.findingRecording
        e.current(id).history shouldBe before.get.history
        _checks(directory) shouldBe count
      }
      _episode(directory, selectedPolicy = Some(R.Policy(_candidates, _candidates))) { e =>
        Given("the same pending assignment under later changed host defaults")
        val display = e.advance(id)
        val work = e.recording(id)
        val good = e.response(work, Vector("recorded", "recorded"))
        val wrong = good.copy(result = good.result.copy(value = good.result.value.copy(items = good.result.value.items.updated(0,
          good.result.value.items.head.copy(records = Vector(R.SavedSection(_candidates, "Crossed destination")))))))
        val crossed = good.copy(result = good.result.copy(value = good.result.value.copy(items = good.result.value.items.reverse)))
        val index = e.index()
        val count = _checks(directory)
        val originaltext = Files.readString(directory.resolve("repository").resolve(_hygiene))
        When("ordinary named public Result intake receives an exact-destination violation or crossed source order")
        val rejected = Vector(wrong, crossed).map(value => e.call("submitGoalPhaseFindingRecording", take(NativeWorkflowOperationInputFixture.submissionC(value))))
        Then("both refuse and pending native identity history index commands and original file stay unchanged")
        rejected.foreach(_.isSuccess shouldBe false)
        _business(work) shouldBe _business(issued.get)
        display.findingRecording.get.request.get.items.map(_.destination) shouldBe Vector(_hygiene, _hygiene)
        e.current(id).history shouldBe before.get.history
        e.index() shouldBe index
        _checks(directory) shouldBe count
        Files.readString(directory.resolve("repository").resolve(_hygiene)) shouldBe originaltext
        When("the specification edits only assigned journals and normal typed Submit commits the finite result")
        e.writeAssigned(work)
        val edited = Files.readString(directory.resolve("repository").resolve(_hygiene))
        val publicvalue = NativeWorkflowOperationOutput.goalRecordingResult(good.result.value)
        val decoded = take(V.GoalPhaseFindingRecordingResult.createC(take(new RecordDecoder().json(take(publicvalue.toRecord().toJsonStringC)))))
        decoded shouldBe publicvalue
        val ack = e.submitRecording(good)
        val completed = e.current(id)
        Then("the distinct after-commit acknowledgement accepts with no Git SBT file rewrite or eager next issue")
        ack.sessionId shouldBe id
        ack.accepted shouldBe true
        ack.outcome shouldBe "accepted"
        ack.message should include("completed")
        completed.suspension shouldBe None
        completed.history.last.causation.value shouldBe good.resultReference.identity
        completed.history.last.currentProgression shouldBe before.get.currentProgression
        e.index() shouldBe index
        e.observe().head shouldBe commit.get.commit.identity
        _checks(directory) shouldBe count
        Files.readString(directory.resolve("repository").resolve(_hygiene)) shouldBe edited
        When("the following ordinary Advance reconstructs the result and issues the next original Step")
        val next = e.advance(id)
        val nextwork = _business(e.work(id))
        Then("actual recording remains visible once, the reviewed commit stays exact and no count or Plan changes")
        nextwork.kind shouldBe "ImplementStep"
        nextwork.stepIdentity shouldBe Some("step-2")
        nextwork.completed shouldBe Vector(commit.get)
        nextwork.selectedPlan shouldBe _business(work).selectedPlan
        nextwork.repairProgress shouldBe _business(work).repairProgress
        next.findingRecording.get.pending shouldBe empty
        next.findingRecording.get.completed.map(_.result.outcome) shouldBe Vector("completed")
        next.findingRecording.get.completed.head.resultReference shouldBe V.WorkflowArtifactReference(good.resultReference.identity, good.resultReference.revision)
        e.current(id).history.count(_.causation.value == "ordered-recording-issued:" + id) shouldBe 1
        e.observe().head shouldBe commit.get.commit.identity
        edited should include("Preserve this original narrative.")
      }
    }

    "skip empty reviews and retain Phase observations at stable Wait when policy or literal ownership is unavailable" in {
      Given("a fresh V7 Session whose actual Step review has no observations and whose current host has no recording policy")
      val directory = _root()
      var id = ""
      var waiting = Option.empty[InstanceRecord]
      _episode(directory, initialize = true, selectedPolicy = None) { e =>
        val (identity, phase, work) = _phase_review(e)
        id = identity
        Then("the empty Step advances normally to Phase review with no recording issue or public empty state")
        _business(work).kind shouldBe "ReviewPhase"
        phase.findingRecording shouldBe None
        e.current(id).history.exists(_.causation.value.startsWith("ordered-recording-issued:")) shouldBe false
        When("actual accepted Phase review supplies a nonblocking item with no configured recording destination")
        e.submit(work, Some(phase), "accept", Some(R.Observations(Vector(_idea))))
        val wait = e.advance(id)
        waiting = Some(e.current(id))
        val count = _checks(directory)
        val repeated = e.advance(id)
        Then("truthful stable Wait retains every source identity and issues neither recording nor new checks")
        wait.kind shouldBe "Wait"
        wait.message should include("policy")
        wait.condition shouldBe Some("phase.PhaseClosing")
        wait.findingRecording.get.pending.map(_.observation.identity) shouldBe Vector(_idea.identity)
        wait.findingRecording.get.request shouldBe None
        repeated.findingRecording shouldBe wait.findingRecording
        e.current(id).history shouldBe waiting.get.history
        _checks(directory) shouldBe count
      }
      _episode(directory, selectedPolicy = Some(R.Policy("docs/not-owned.md", _candidates))) { e =>
        Given("the retained actual Phase observation and a later declared destination outside literal host and Plan ownership")
        When("ordinary Advance selects recording under that invalid current policy")
        val refused = e.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(id))
        Then("input admission refuses before issuance and keeps all native suspension and history facts")
        refused.isSuccess shouldBe false
        e.current(id).history shouldBe waiting.get.history
        e.current(id).suspension shouldBe None
      }
      Vector(R.Policy("../outside.md", _candidates), R.Policy("docs/journal.MD", _candidates)).foreach { invalid =>
        Given("an invalid literal or case-sensitive non-Markdown native configuration")
        When("the ordinary Component host attempts initialization before any recording issue")
        val rejected = intercept[AssertionError] { _episode(directory, selectedPolicy = Some(invalid))(_ => fail("Invalid host was installed")) }
        Then("the actual declaration boundary rejects that policy")
        rejected.getMessage should include("fixture failed")
      }
    }

    Vector(false, true).foreach { automatic =>
    s"retain every item at truthful Wait when accumulated real reviews exceed the finite recording bound with automatic Journal $automatic" in {
      Given("a fresh isolated V7 Step with two allowed repair batches and 128 observations in each actual review result")
      val directory = _root()
      _episode(directory, initialize = true,
        journal = Option.when(automatic)(NativeWorkflowCliFixture.JournalHost(_journal))) { e =>
        val id = e.start()
        e.advance(id)
        Files.writeString(directory.resolve("repository/step-1.txt"), "Initial assigned source.\n")
        e.submit(e.work(id))
        var display = e.advance(id)
        val observations = R.Observations(Vector.tabulate(128)(i => _observation.copy(identity = "finite-" + i)))
        var references = Vector.empty[ContextReference]
        When("two genuine repair and re-review cycles accumulate three distinct accepted source references before the Step commit")
        (0 until 2).foreach { index =>
          val result = e.submit(e.work(id), Some(display), "repair", Some(observations), Vector("Required blocker " + index))
          references = references :+ result.resultReference
          val repairdisplay = e.advance(id)
          repairdisplay.findingRecording.get.request shouldBe None
          val repair = e.work(id)
          Files.writeString(directory.resolve("repository/step-1.txt"), "Actual assigned correction " + index + ".\n")
          e.submit(repair)
          display = e.advance(id)
        }
        val accepted = e.submit(e.work(id), Some(display), "accept", Some(observations))
        references = references :+ accepted.resultReference
        val authoritywait = e.advance(id)
        e.authority(id, authoritywait)
        val wait = e.advance(id)
        val retained = e.current(id)
        val count = _checks(directory)
        val again = e.advance(id)
        Then("all 384 pending items remain visible with no subset recording issue task split or repair reset beyond the actual Step")
        wait.kind shouldBe "Wait"
        wait.message should include("256-item")
        wait.condition shouldBe Some("phase.PhaseInspection")
        wait.findingRecording.get.pending.size shouldBe 384
        wait.findingRecording.get.pending.map(p => p.sourceReference.identity -> p.sourceReference.revision).distinct shouldBe
          references.map(r => r.identity -> r.revision)
        wait.findingRecording.get.request shouldBe None
        wait.findingRecording.get.completed shouldBe empty
        wait.findingRecording.get.journaled shouldBe empty
        again.findingRecording shouldBe wait.findingRecording
        e.current(id).history shouldBe retained.history
        e.current(id).suspension shouldBe None
        e.current(id).history.exists(_.causation.value.startsWith("ordered-recording-issued:")) shouldBe false
        e.current(id).history.exists(_.causation.value == "ordered-journal-findings-intent") shouldBe false
        _checks(directory) shouldBe count
      }
    }
    }

    Vector(Vector("recorded", "recorded"), Vector("partial", "failed"), Vector("failed", "failed")).foreach { outcomes =>
      s"accept ${outcomes.mkString("/")} recording Submit with all Git grants denied and reserve observation for separate Advance" in {
        Given("ordinary typed Git and SBT grants create a legitimate Step or Phase recording frontier with specification-owned review inputs")
        val directory = _root()
        val step = outcomes.forall(_ == "recorded")
        var id = ""
        var issued = Option.empty[WorkflowInteraction[R.Request, Nothing]]
        var response = Option.empty[ContinuationResult[R.Result]]
        var pending = Option.empty[InstanceRecord]
        var completed = Option.empty[InstanceRecord]
        var reviewed = Option.empty[O.Snapshot]
        var journals = Map.empty[String, String]
        var count = 0
        _episode(directory, initialize = true, selected = _plan(two = step)) { e =>
          if (step) {
            id = e.start()
            e.advance(id)
            Files.writeString(directory.resolve("repository/step-1.txt"), "Assigned required implementation.\n")
            e.submit(e.work(id))
            val display = e.advance(id)
            e.submit(e.work(id), Some(display), "accept", Some(R.Observations(Vector(_observation, _idea))))
            val waiting = e.advance(id)
            e.authority(id, waiting)
          } else {
            val (identity, display, review) = _phase_review(e)
            id = identity
            reviewed = _business(review).candidate
            e.submit(review, Some(display), "accept", Some(R.Observations(Vector(_observation, _idea))))
          }
          e.advance(id)
          val work = e.recording(id)
          issued = Some(work)
          response = Some(e.response(work, outcomes))
          if (step) e.writeAssigned(work)
          else if (outcomes.head == "partial") e.writeAssigned(work, Set(0))
          pending = Some(e.current(id))
          journals = Vector(_hygiene, _candidates).map(path => path -> Files.readString(directory.resolve("repository").resolve(path))).toMap
          count = _checks(directory)
        }

        Given("the closed runtime reopens the same canonical SQLite Session with every declared Git capability denied and ordinary SBT grants retained")
        _episode(directory, denygit = true) { e =>
          val work = issued.get
          val good = response.get
          val resumed = e.current(id)
          _same_instance(resumed, pending.get)
          e.recording(id) shouldBe work
          val wrongitems = good.result.value.items.updated(0, good.result.value.items.head.copy(outcome = "recorded",
            records = Vector(R.SavedSection(_candidates, "Crossed assigned destination"))))
          val wrongoutcome = if (wrongitems.forall(_.outcome == "recorded")) "completed" else "partial"
          val wrong = good.copy(result = good.result.copy(value = good.result.value.copy(outcome = wrongoutcome, items = wrongitems)))
          val crossed = good.copy(result = good.result.copy(value = good.result.value.copy(items = good.result.value.items.reverse)))
          When("the distinct named Submit receives validly shaped crossed-destination and crossed-source replies under the denied Git plan")
          Vector(wrong, crossed).foreach { value =>
            e.call("submitGoalPhaseFindingRecording", take(NativeWorkflowOperationInputFixture.submissionC(value))).isSuccess shouldBe false
          }
          Then("both malformed replies leave the actual pending issue and complete native history unchanged")
          _same_instance(e.current(id), resumed)
          e.recording(id) shouldBe work
          _checks(directory) shouldBe count
          journals.foreach { (path, text) => Files.readString(directory.resolve("repository").resolve(path)) shouldBe text }

          When("the same named Submit consumes the saved exact honest response while all Git grants remain denied")
          val ack = e.submitRecording(good)
          val actual = e.current(id)
          completed = Some(actual)
          Then("canonical completion accepts and appends only its exact result without a file rewrite SBT run next suspension or automatic Advance")
          ack.sessionId shouldBe id
          ack.accepted shouldBe true
          ack.outcome shouldBe "accepted"
          ack.message shouldBe ("Accepted recording " + good.result.value.outcome + ": " + good.result.value.summary)
          actual.revision shouldBe pending.get.revision.next
          actual.history.init shouldBe pending.get.history
          actual.history.last.causation.value shouldBe good.resultReference.identity
          actual.currentProgression shouldBe pending.get.currentProgression
          actual.suspension shouldBe None
          actual.history.count(_.causation.value == "ordered-recording-issued:" + id) shouldBe 1
          _checks(directory) shouldBe count
          journals.foreach { (path, text) => Files.readString(directory.resolve("repository").resolve(path)) shouldBe text }

          When("a separate ordinary Advance attempts its required live Git observation under the same denied grants")
          val denied = e.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(id))
          Then("Advance fails and preserves that exact completed state showing that Git denial is effective")
          denied.isSuccess shouldBe false
          _same_instance(e.current(id), actual)
          _checks(directory) shouldBe count
          journals.foreach { (path, text) => Files.readString(directory.resolve("repository").resolve(path)) shouldBe text }
        }

        Given("the same completed Session reopens again with its ordinary typed Git and SBT grants")
        _episode(directory) { e =>
          val work = issued.get
          val assigned = _business(work)
          val result = response.get
          val reopened = e.current(id)
          _same_instance(reopened, completed.get)
          When("a separate normal Advance observes the retained result and selects the existing outcome-specific frontier")
          val next = e.advance(id)
          val actual = e.current(id)
          Then("the exact request result reference completed prefix Plan base and counts survive once with empty pending keys and no SBT replay")
          next.findingRecording shouldBe NativeWorkflowOperationOutput.goalFindingRecording(N.State(completed = Vector(
            N.Completed(assigned, result.resultReference, result.result.value))))
          actual.history.take(completed.get.history.size) shouldBe completed.get.history
          actual.history.count(_.causation.value == "ordered-recording-issued:" + id) shouldBe 1
          actual.history.count(_.causation.value == result.resultReference.identity) shouldBe 1
          _checks(directory) shouldBe count
          journals.foreach { (path, text) => Files.readString(directory.resolve("repository").resolve(path)) shouldBe text }
          if (step) {
            val nextwork = _business(e.work(id))
            nextwork.kind shouldBe "ImplementStep"
            nextwork.stepIdentity shouldBe Some("step-2")
            nextwork.selectedPlan shouldBe assigned.selectedPlan
            nextwork.originalBase shouldBe assigned.originalBase
            nextwork.completed shouldBe assigned.completed
            nextwork.repairProgress shouldBe assigned.repairProgress
          } else {
            next.kind shouldBe "Wait"
            next.authorityContext should not be empty
            val candidate = e.recordingCandidate(id)
            candidate.payload.reviewedCandidate shouldBe reviewed.get
            if (outcomes.head == "partial") candidate.payload.changedPaths shouldBe Vector(_hygiene)
            else {
              candidate.payload.finalCandidate shouldBe reviewed
              candidate.payload.changedPaths shouldBe empty
            }
          }
        }
      }
    }

    Vector(Vector("partial", "failed"), Vector("failed", "failed")).foreach { outcomes =>
      s"retain honest ${outcomes.mkString("/")} outcomes without retry or implementation success" in {
        Given("a fresh V7 accepted Phase review with two nonblocking sources and a real issued finite recording task")
        val directory = _root()
        _episode(directory, initialize = true) { e =>
          val (id, display, work) = _phase_review(e)
          e.submit(work, Some(display), "accept", Some(R.Observations(Vector(_observation, _idea))))
          e.advance(id)
          val recording = e.recording(id)
          val result = e.response(recording, outcomes)
          val index = e.index()
          val head = e.observe().head
          val count = _checks(directory)
          if (outcomes.head == "partial") e.writeAssigned(recording, Set(0))
          When("normal Submit accepts the honest finite outcome and ordinary Advance observes the actual assigned files")
          val ack = e.submitRecording(result)
          val next = e.advance(id)
          val actual = e.recordingCandidate(id)
          Then("the terminal batch disposition is visible exactly once without retry counts fabricated edits or renewed review")
          ack.accepted shouldBe true
          next.kind shouldBe "Wait"
          next.findingRecording.get.pending shouldBe empty
          next.findingRecording.get.completed.map(_.result.outcome) shouldBe Vector(result.result.value.outcome)
          next.findingRecording.get.completed.head.result.items.map(_.outcome) shouldBe outcomes
          if (outcomes.head == "partial") {
            actual.payload.finalCandidate.get.tree should not be actual.payload.reviewedCandidate.tree
            actual.payload.changedPaths shouldBe Vector(_hygiene)
          } else {
            actual.payload.finalCandidate shouldBe Some(actual.payload.reviewedCandidate)
            actual.payload.changedPaths shouldBe empty
            io.circe.parser.parse(take(next.authorityContext.get.scope.toJsonStringC)).toOption.get
              .hcursor.downField("reviewedCandidate").focus shouldBe None
            e.index() shouldBe index
          }
          e.observe().head shouldBe head
          _checks(directory) shouldBe count
          e.current(id).history.count(_.causation.value == "ordered-recording-issued:" + id) shouldBe 1
        }
      }
    }

    "stage only assigned Phase records and close the actual final index while retaining the independent reviewed source" in {
      Given("a fresh isolated V7 Phase with actual Step commit checks and independently scoped specification Phase review")
      val directory = _root()
      _episode(directory, initialize = true) { e =>
        val (id, display, review) = _phase_review(e)
        val source = _business(review).candidate.get
        val closed = _business(review).completed
        val accepted = e.submit(review, Some(display), "accept", Some(R.Observations(Vector(_observation, _idea))))
        e.advance(id)
        val recording = e.recording(id)
        val count = _checks(directory)
        val result = e.response(recording, Vector("recorded", "recorded"))
        When("the specification writes only assigned Markdown files and normal Submit then Advance owns typed staging")
        e.writeAssigned(recording)
        e.submitRecording(result)
        val waiting = e.advance(id)
        val from = e.current(id)
        val candidate = e.recordingCandidate(id)
        val scope = io.circe.parser.parse(take(waiting.authorityContext.get.scope.toJsonStringC)).toOption.get
        Then("ordinary authority Wait exposes the original reviewed tree and the actual final index without checks or review rerun")
        waiting.kind shouldBe "Wait"
        candidate.payload.reviewedCandidate shouldBe source
        candidate.payload.finalCandidate.get.head shouldBe source.head
        candidate.payload.finalCandidate.get.tree shouldBe e.index()
        candidate.payload.finalCandidate.get.tree should not be source.tree
        candidate.payload.assignedPaths shouldBe Vector(_hygiene, _candidates)
        candidate.payload.selectedPaths shouldBe Vector(_hygiene, _candidates)
        candidate.payload.stagedPaths shouldBe Vector(_hygiene, _candidates)
        candidate.payload.changedPaths.toSet shouldBe Set(_hygiene, _candidates)
        scope.hcursor.downField("reviewedCandidate").get[String]("tree").toOption shouldBe Some(source.tree)
        scope.hcursor.downField("candidate").get[String]("tree").toOption shouldBe Some(e.index())
        scope.hcursor.get[String]("review").toOption shouldBe Some(accepted.resultReference.identity)
        e.observe().head shouldBe closed.last.commit.identity
        e.observe().dirty.forall(p => Set(_hygiene, _candidates)(p.path) && p.worktree == ' ') shouldBe true
        _checks(directory) shouldBe count
        e.current(id).history.count(_.causation.value.startsWith("ordered-review-issued:")) shouldBe 2
        When("separate specification authority admits the existing fixed local bare-origin child closure")
        e.authority(id, waiting, child = true)
        val terminal = e.advance(id)
        val completion = terminal.completion.get
        Then("the ordinary child consumes the final actual tree and all retained recording outcomes survive Terminal")
        terminal.kind shouldBe "Terminal"
        completion.completed.map(_.commit.identity) shouldBe closed.map(_.commit.identity)
        completion.reviewedCandidate.head shouldBe source.head
        completion.reviewedCandidate.tree shouldBe source.tree
        completion.repository.get.tree shouldBe candidate.payload.finalCandidate.get.tree
        e.observe().tree shouldBe completion.repository.get.tree
        e.observe().head shouldBe completion.repository.get.tip
        e.observe().trackingTip shouldBe Some(completion.repository.get.tip)
        e.observe().dirty shouldBe empty
        terminal.findingRecording.get.completed.map(_.result.outcome) shouldBe Vector("completed")
        e.current(id).lifecycle shouldBe Lifecycle.Completed
        e.current(id).history.take(from.history.size) shouldBe from.history
      }
    }

    Vector(false, true).foreach { interrupted =>
      s"retain ${if (interrupted) "an unknown staging intent" else "an outside-destination edit"} without overwrite or replay" in {
        Given("a real accepted Phase recording completion before final native candidate observation")
        val directory = _root()
        var id = ""
        var saved = Option.empty[InstanceRecord]
        var index = ""
        var count = 0
        val outside = "Outside the assigned Phase recording destination.\n"
        _episode(directory, initialize = true, interrupt = interrupted) { e =>
          val (identity, display, review) = _phase_review(e)
          id = identity
          e.submit(review, Some(display), "accept", Some(R.Observations(Vector(_observation))))
          e.advance(id)
          val recording = e.recording(id)
          e.writeAssigned(recording)
          e.submitRecording(e.response(recording, Vector("recorded")))
          if (!interrupted) Files.writeString(directory.resolve("repository/step-1.txt"), outside)
          val beforecount = _checks(directory)
          When("ordinary Advance retains the unrelated unstaged edit or the specification interrupts after durable stage intent")
          val advanced = e.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(id))
          saved = Some(e.current(id))
          index = e.index()
          count = _checks(directory)
          Then("interruption retains its failed staging intent while normal completion waits for authority with only assigned candidate paths")
          if (interrupted) {
            advanced.isSuccess shouldBe false
            saved.get.history.last.causation.value shouldBe "ordered-recording-candidate:stage-intent-0"
          } else {
            val waiting = read_value[V.GoalPhaseAdvanceResult](take(advanced))
            waiting.kind shouldBe "Wait"
            waiting.condition shouldBe Some("phase.PhaseClosing")
            waiting.authorityContext should not be empty
            Files.readString(directory.resolve("repository/step-1.txt")) shouldBe outside
            val dirty = e.observe().dirty.filter(_.path == "step-1.txt")
            dirty.size shouldBe 1
            dirty.head.index shouldBe ' '
            dirty.head.worktree shouldBe 'M'
            val candidate = e.recordingCandidate(id)
            Vector(candidate.payload.selectedPaths, candidate.payload.stagedPaths, candidate.payload.changedPaths).foreach { paths =>
              paths should not contain "step-1.txt"
              paths.forall(candidate.payload.assignedPaths.contains) shouldBe true
            }
            _checks(directory) shouldBe beforecount
          }
        }
        _episode(directory) { e =>
          Given("the same real native history after reopening with normal policies")
          val text = Files.readString(directory.resolve("repository").resolve(_hygiene))
          When("ordinary Advance reconstructs that retained source twice")
          val first = e.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(id))
          val second = e.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(id))
          Then("unknown intent and ordinary authority both stay stable Wait without new checks staging or overwrite")
          if (interrupted) {
            read_value[V.GoalPhaseAdvanceResult](take(first)).kind shouldBe "Wait"
            read_value[V.GoalPhaseAdvanceResult](take(second)).message should include("unknown intent")
            read_value[V.GoalPhaseAdvanceResult](take(second)).condition shouldBe Some("phase.PhaseClosing")
          } else {
            val firstwait = read_value[V.GoalPhaseAdvanceResult](take(first))
            val secondwait = read_value[V.GoalPhaseAdvanceResult](take(second))
            firstwait.kind shouldBe "Wait"
            firstwait.condition shouldBe Some("phase.PhaseClosing")
            secondwait.kind shouldBe "Wait"
            secondwait.condition shouldBe Some("phase.PhaseClosing")
            Files.readString(directory.resolve("repository/step-1.txt")) shouldBe outside
          }
          e.current(id).history shouldBe saved.get.history
          e.index() shouldBe index
          _checks(directory) shouldBe count
          Files.readString(directory.resolve("repository").resolve(_hygiene)) shouldBe text
        }
      }
    }
  }
}

private[admission] object GoalPhaseNativeFindingRecordingSpec {
  final case class InstalledHandoff(root: Path, sessionId: String, request: R.Request,
    reply: ContinuationResult[R.Result], pending: InstanceRecord, submission: Json)
}
