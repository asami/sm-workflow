package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.SubsystemEventStoreBootstrap
import org.goldenport.cncf.testutil.NativeHumanManagedHostObservation
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as InstanceContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseNativeHumanQuestionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GoalPhaseHumanDecisionContract.*
  private given context: ExecutionContext = ExecutionContext.create()
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Native human fixture rejected: $other")
  }
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString.trim finally source.close()
  }
  private val artifact = take(CandidateAdmissionProducerAbi.parseC(resource("/workflow/goal-phase-candidate-admission-producer-abi.json")))
  private val definition = take(bindCandidateDefinitionC(take(CandidateWorkflowAbi.parseC(
    resource("/workflow/goal-phase-workflow-abi.json"), artifact, "src/main/cozy/sm-workflow.cml"))))
  private val component = SmWorkflowComponent.componentId
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "nativehuman", name))
  private val instances = collection("instances")
  private val continuations = collection("continuations")
  private val config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("native-human-spec")),
    MigrationPolicy("v1"), RetentionPolicy("retain"), LeasePolicy("explicit"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("native-human-spec")))
  private val declaration = take(EventStorePersistenceDeclaration.createC("events",
    collection("cursor"), collection("events"), collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(component, "workflow", instances, continuations))))
  private val policy = take(EventPersistencePolicy.createC("native-human-spec-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
  private def host(path: Path) = take(SubsystemEventStoreBootstrap.createC(policy, declaration,
    ScopeContext(ScopeKind.Subsystem, "native-human-spec", None, context.observability),
    ResolvedConfiguration(HostConfiguration(Map(
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString))), ConfigurationTrace.empty)))
  private def root(host: SubsystemEventStoreBootstrap) = take(WorkflowInstancePersistenceBinding.bindC(component,
    host.subsystem, ComponentDataStore.Environment(
      org.goldenport.cncf.config.ResolvedParameters.fromResolvedConfiguration(host.subsystem.configuration),
      Some(host.subsystem.configuration)),
    WorkflowInstancePersistenceBinding.Declaration(config, Vector(definition), instances)))
  private def decisions(host: SubsystemEventStoreBootstrap) = take(WorkflowManagedIssuedDecision.bindC(host.subsystem,
    component, config, GoalPhaseNativeHumanQuestion.completionOperation, Vector(definition), instances, continuations,
    GoalPhaseClosureAuthority.inputCodec))
  private def work(host: SubsystemEventStoreBootstrap) = take(WorkflowManagedIssuedWork.bindC(host.subsystem,
    component, config, GoalPhaseNativeHumanQuestion.completionOperation, Vector(definition), instances, continuations,
    GoalPhaseClosureAuthority.inputCodec))
  private val initial = InstanceRecord(InstanceIdentity("native-human-instance"), definition, initialRevision,
    Lifecycle.NotStarted, None, CorrelationReference("selection-1"), CausationReference("start-1"), None, None, None, Vector.empty)
  // Pure fixture predecessor only: this test does not establish accepted-review
  // provenance. That must be derived by the managed current-call integration.
  private val reviewed = (1L to 3L).foldLeft(initial) { (base, revision) =>
    take(base.appendC(base.revision, HistoryEntry(HistorySequence(revision), InstanceRevision(revision), Lifecycle.Active,
      Some(ProgressionReference("step.Awaiting")), base.correlation, CausationReference(s"fixture-review-$revision"),
      None, None, None)))
  }
  private val handle = take(WorkflowHandle.fromRecordC(component, reviewed))
  private val prior = take(GoalPhaseClosureAuthority.intentC(HumanDecisionContractFixture.candidate, handle, reviewed))
  private val reviewId = ContinuationIdentity("review-1")
  private val choices = HumanDecisionContractFixture.choices
  private def prepared = take(GoalPhaseNativeHumanQuestion.prepareC(config, reviewed, reviewId, prior, choices))
  private def bytes(record: InstanceRecord) = take(WorkflowInstanceRecordJsonV1.encodeC(record))
  private def seed(host: SubsystemEventStoreBootstrap): Unit = {
    val binding = root(host)
    take(binding.createC(initial))
    reviewed.history.foreach(entry => take(binding.appendC(initial.identity, InstanceRevision(entry.revision.value - 1L), entry)))
  }

  "Native human question preparation" should {
    "observe only a retained native answer present in the exact historical successor" in {
      Given("a synthetic pending native question and retained answer, not a fresh human receipt")
      val pending = prepared
      val request = pending.question.request
      val result = ContinuationResult(pending.issued.handle, request.runId, request.continuationId,
        request.expectedRevision, request.context.snapshot, TypedValue(resultType, Answer(choices.head.identity)),
        ContextReference("original-human-evidence", "1"), Vector.empty, ExecutionEvidence(Vector.empty))
      val answered = take(GoalPhaseNativeHumanAnswer.prepareC(pending.issued, result, pending.next))
      val retained = WorkflowCompletionRecordJsonV1.Record(answered.revision, result,
        Some(StateMachineOperationResult(StateMachineResultTypeReference("GoalPhaseHumanAnswerResult"), result.resultReference)))
      When("the pure observation reads that exact completed prefix and a later history entry")
      val later = take(answered.appendC(answered.revision, answered.history.last.copy(
        sequence = HistorySequence(6L), revision = InstanceRevision(6L), causation = CausationReference("later-progress"))))
      val json = io.circe.parser.parse(take(GoalPhaseNativeHumanCompletionObservation.projectC(pending.issued, retained, later))).toOption.get
      Then("the original completion revision and evidence reference are distinct from current progression")
      json.hcursor.get[Long]("completingRevision").toOption shouldBe Some(5L)
      json.hcursor.get[Long]("currentRevision").toOption shouldBe Some(6L)
      json.hcursor.get[String]("humanEvidenceReference").toOption shouldBe Some("original-human-evidence")
      take(WorkflowCompletionRecordJsonV1.decodeC(json.hcursor.get[String]("completion").toOption.get, answerPayloadCodec)) shouldBe retained
      When("a completion lacks its closing result, changes its saved revision or differs from history")
      GoalPhaseNativeHumanCompletionObservation.projectC(pending.issued, retained.copy(closingResult = None), later).isSuccess shouldBe false
      GoalPhaseNativeHumanCompletionObservation.projectC(pending.issued, retained.copy(instanceRevision = InstanceRevision(6L)), later).isSuccess shouldBe false
      GoalPhaseNativeHumanCompletionObservation.projectC(pending.issued, retained, pending.next).isSuccess shouldBe false
      val changed = answered.copy(causation = CausationReference("fabricated-answer-history"),
        history = answered.history.dropRight(1) :+ answered.history.last.copy(causation = CausationReference("fabricated-answer-history")))
      GoalPhaseNativeHumanCompletionObservation.projectC(pending.issued, retained, changed).isSuccess shouldBe false
      val query = GoalPhaseHumanCompletionQueryOperation.encodeQuery(request.continuationId)
      take(GoalPhaseHumanCompletionQueryOperation.decodeQueryC(query)) shouldBe request.continuationId
      GoalPhaseHumanCompletionQueryOperation.decodeQueryC(query.dropRight(1) + ",\"approved\":true}").isSuccess shouldBe false
      GoalPhaseHumanCompletionQueryOperation.decodeQueryC(GoalPhaseHumanCompletionQueryOperation.encodeQuery(ContinuationIdentity("old-observation")))
        .isSuccess shouldBe false
    }

    "prepare an adjacent answered state without admitting human evidence or closing the Step" in {
      Given("a native pending question and an exact declared answer, with no human verifier")
      val pending = prepared
      val request = pending.question.request
      val result = ContinuationResult(pending.issued.handle, request.runId, request.continuationId,
        request.expectedRevision, request.context.snapshot, TypedValue(resultType, Answer(choices.head.identity)),
        ContextReference("human-answer-result", "1"), Vector.empty, ExecutionEvidence(Vector.empty))
      When("the pure boundary computes the successor")
      val next = take(GoalPhaseNativeHumanAnswer.prepareC(pending.issued, result, pending.next))
      Then("revision five is Active HumanAnswered, with unchanged predecessor history and no command or closure")
      next.revision shouldBe InstanceRevision(5L)
      next.lifecycle shouldBe Lifecycle.Active
      next.currentProgression shouldBe Some(ProgressionReference("HumanAnswered"))
      next.suspension shouldBe None
      next.history.dropRight(1) shouldBe pending.next.history
      next.committedPredecessor shouldBe pending.next.committedPredecessor
      GoalPhaseNativeHumanAnswer.admitForCallC(pending.issued, result, pending.next,
        "local-participant", "unverified", java.time.Instant.parse("2026-10-01T00:00:00Z"), null, null)
        .isSuccess shouldBe false
      When("a result changes its choice, type, revision, question or result reference")
      val changed = Vector(
        result.copy(result = TypedValue(resultType, Answer("invented-approval"))),
        result.copy(result = TypedValue("ReviewStepResult", result.result.value)),
        result.copy(expectedRevision = StateMachineRevision("3")),
        result.copy(continuationId = ContinuationIdentity("foreign-question")),
        result.copy(resultReference = ContextReference("", "1")))
      Then("none can prepare another successor, and replay against the answered record is refused")
      changed.foreach(value => GoalPhaseNativeHumanAnswer.prepareC(pending.issued, value, pending.next).isSuccess shouldBe false)
      GoalPhaseNativeHumanAnswer.prepareC(pending.issued, result, next).isSuccess shouldBe false
      GoalPhaseNativeHumanAnswer.prepareC(pending.issued, result, reviewed).isSuccess shouldBe false
    }

    "allocate a separate adjacent pending identity without rewriting the observation intent" in {
      Given("a fixture reviewed base at revision three and an immutable observation question")
      val original = HumanDecisionContractFixture.question
      val originalBytes = take(GoalPhaseHumanQuestionRecord.encodeStorageC(original))
      When("Workflow prepares the generated native human route")
      val pending = prepared
      Then("revision four is HumanPending with its own question identity, exact choices and candidate")
      pending.next.revision shouldBe InstanceRevision(4L)
      pending.next.currentProgression shouldBe Some(ProgressionReference("HumanPending"))
      pending.next.history.dropRight(1) shouldBe reviewed.history
      pending.next.suspension.get.continuationIdentity shouldBe InstanceContinuationIdentity(pending.question.request.continuationId.value)
      pending.question.request.continuationId.value shouldBe "goal-phase-native-human:native-human-instance:4:review-1"
      pending.question.request.expectedRevision shouldBe StateMachineRevision("4")
      pending.question.request.requiredOperation shouldBe StateMachineRequiredOperationIdentity("human-decision-capability")
      pending.question.request.operation shouldBe StateMachineOperationIdentity("HumanDecisionService", "presentGoalPhaseHumanDecision")
      pending.question.request.completionOperation shouldBe GoalPhaseNativeHumanQuestion.completionOperation
      pending.question.intent.input shouldBe prior.input
      pending.question.choices shouldBe choices
      prior.expectedRevision shouldBe StateMachineRevision("3")
      take(GoalPhaseHumanQuestionRecord.encodeStorageC(original)) shouldBe originalBytes
      take(GoalPhaseHumanQuestionRecord.decodeStorageC(originalBytes)) shouldBe original
    }

    "recover every choice and intent from one native Decision payload without a side record" in {
      Given("a native question with generated routing and exact bounded visible choices")
      val pending = prepared
      When("the actual CNCF Decision wire is encoded and decoded")
      val wire = take(WorkflowDecisionJsonV1.encodeC(pending.issued, GoalPhaseClosureAuthority.inputCodec))
      val decoded = take(WorkflowDecisionJsonV1.decodeC(wire, GoalPhaseClosureAuthority.inputCodec))
      Then("the complete question is recovered and no WorkOrder or implicit permission is admitted")
      take(GoalPhaseNativeHumanQuestion.recoverC(decoded)) shouldBe pending.question
      WorkflowWorkOrderJsonV1.decodeC(wire, GoalPhaseClosureAuthority.inputCodec).isSuccess shouldBe false
      pending.question.admitAnswerC(Answer("invented-approval")).isSuccess shouldBe false
      GoalPhaseNativeHumanQuestion.recoverC(HumanDecisionContractFixture.question match {
        case value => WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing](value.intent.handle,
          WorkflowContinuation.Decision(value.request, MinimalPresentation("Human Decision", "Not command authorization")))
      }).isSuccess shouldBe false
    }

    "recover selected presentation without a regenerated-body equality certificate" in {
      Given("a native Decision with valid declared choices and diagnostic presentation changes")
      val pending = prepared
      val decision = pending.issued.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseClosureAuthority.Input]]
      val body = io.circe.parser.parse(decision.presentation.currentSituation).toOption.get
      val selected = decision.copy(presentation = decision.presentation.copy(
        currentSituation = body.spaces2, summary = Some("Another diagnostic summary")))
      When("the stored Decision is recovered without regenerating its complete wire")
      val recovered = take(GoalPhaseNativeHumanQuestion.recoverC(pending.issued.copy(current = selected)))
      Then("the actual question, allocated intent and choices are used without a presentation equality proof")
      recovered shouldBe pending.question
    }

    "refuse unsupported schema, routes, identity and context" in {
      Given("a fully projected native Decision")
      val pending = prepared
      val decision = pending.issued.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseClosureAuthority.Input]]
      val body = io.circe.parser.parse(decision.presentation.currentSituation).toOption.get
      When("an undeclared field, alternate completion, reused identity or altered context is supplied")
      val altered = Vector(
        decision.copy(presentation = decision.presentation.copy(title = "Changed")),
        decision.copy(presentation = decision.presentation.copy(currentSituation = body.mapObject(_.add("approval", Json.True)).noSpaces)),
        decision.copy(presentation = decision.presentation.copy(currentSituation = body.mapObject(_.add("choices", Json.arr())).noSpaces)),
        decision.copy(request = decision.request.copy(operation = StateMachineOperationIdentity("StepRepairService", "repairStep"))),
        decision.copy(request = decision.request.copy(completionOperation = StateMachineOperationIdentity("GoalPhaseService", "submitStepReview"))),
        decision.copy(request = decision.request.copy(continuationId = ContinuationIdentity("human-question-1"))),
        decision.copy(request = decision.request.copy(context = decision.request.context.copy(snapshot = ContextSnapshot("foreign")))))
      Then("every altered Decision is refused rather than normalized or turned into work")
      altered.foreach(value => GoalPhaseNativeHumanQuestion.recoverC(pending.issued.copy(current = value)).isSuccess shouldBe false)
      GoalPhaseNativeHumanQuestion.recoverC(null).isSuccess shouldBe false
    }

    "refuse nonadjacent, already suspended and foreign-definition preparation" in {
      Given("the exact reviewed fixture base and its prepared native suspension")
      val pending = prepared
      When("preparation receives a stale revision, wrong snapshot, active suspension or unsupported definition")
      val bad = Vector(prior.copy(expectedRevision = StateMachineRevision("2")),
        prior.copy(snapshot = ContextSnapshot("foreign")),
        prior.copy(handle = handle.copy(workflowRevision = WorkflowDefinitionRevision("old-v2"))))
      Then("no native question can be prepared for those mismatches")
      bad.foreach(value => GoalPhaseNativeHumanQuestion.prepareC(config, reviewed, reviewId, value, choices).isSuccess shouldBe false)
      GoalPhaseNativeHumanQuestion.prepareC(config, pending.next, reviewId, prior, choices).isSuccess shouldBe false
      GoalPhaseNativeHumanQuestion.prepareC(config, reviewed, null, prior, choices).isSuccess shouldBe false
      GoalPhaseNativeHumanQuestion.prepareC(config, reviewed, reviewId, prior, Vector(choices.head)).isSuccess shouldBe false
    }
  }

  "Native question on the managed shared SQLite provider" should {
    "atomically retain the pending history and full question, then recover both after physical reopen" in {
      Given("a canonical shared-store host with a fixture predecessor but no question side-record collection")
      val path = Files.createTempDirectory("goal-phase-native-human-").resolve("host.sqlite")
      val first = host(path)
      val pending = prepared
      val id = pending.question.request.continuationId
      try {
        seed(first)
        val issued = decisions(first)
        When("the native managed capability publishes the adjacent suspension")
        work(first).suspendC(pending.intent, pending.issued).isSuccess shouldBe false
        issued.putIfAbsentC(pending.issued).isSuccess shouldBe false
        take(issued.loadC(id)) shouldBe None
        val saved = take(issued.suspendC(pending.intent, pending.issued))
        Then("one physical provider retains exact revision four and the whole native question together")
        bytes(saved.instance) shouldBe bytes(pending.next)
        take(GoalPhaseNativeHumanQuestion.recoverC(take(issued.loadC(id)).get)) shouldBe pending.question
        take(issued.putIfAbsentC(pending.issued)) shouldBe pending.issued
        issued.suspendC(pending.intent, pending.issued).isSuccess shouldBe false
        work(first).loadC(id).isSuccess shouldBe false
        val changed = take(GoalPhaseNativeHumanQuestion.projectC(pending.question.copy(
          choices = choices.updated(0, choices.head.copy(label = "Another visible question")))))
        When("a later proposal changes the visible choice label at the occupied question identity")
        val selected = take(issued.putIfAbsentC(changed))
        Then("the saved question is selected without a full-body comparison, replacement or another suspension")
        selected shouldBe pending.issued
        take(GoalPhaseNativeHumanQuestion.recoverC(selected)) shouldBe pending.question
        bytes(take(root(first).loadC(initial.identity)).get) shouldBe bytes(pending.next)
        NativeHumanManagedHostObservation.counts(first) shouldBe (1, 0)
      } finally take(first.shutdownC())
      When("another canonical host reopens that exact database")
      val reopened = host(path)
      try {
        val recovered = take(decisions(reopened).loadC(id)).get
        Then("the same choices, candidate, snapshot and pending history survive without rebuilding or appending")
        recovered shouldBe pending.issued
        take(GoalPhaseNativeHumanQuestion.recoverC(recovered)) shouldBe pending.question
        bytes(take(root(reopened).loadC(initial.identity)).get) shouldBe bytes(pending.next)
        take(decisions(reopened).putIfAbsentC(recovered)) shouldBe recovered
        NativeHumanManagedHostObservation.counts(reopened) shouldBe (1, 0)
      } finally take(reopened.shutdownC())
    }

    "refuse a stale predecessor and uncaptured owner before publishing a question" in {
      Given("a shared host whose fixture base advances after native preparation")
      val current = host(Files.createTempDirectory("native-human-stale-").resolve("host.sqlite"))
      try {
        seed(current)
        val pending = prepared
        val changedEntry = pending.intent.entry.copy(suspension = None, causation = CausationReference("concurrent-writer"))
        val advanced = take(root(current).appendC(initial.identity, reviewed.revision, changedEntry))
        When("publication reuses the old base or an uncaptured Component owner")
        decisions(current).suspendC(pending.intent, pending.issued).isSuccess shouldBe false
        WorkflowManagedIssuedDecision.bindC(current.subsystem, org.goldenport.cncf.component.ComponentId("org.example.Foreign"), config,
          GoalPhaseNativeHumanQuestion.completionOperation, Vector(definition), instances, continuations,
          GoalPhaseClosureAuthority.inputCodec).isSuccess shouldBe false
        Then("no question is issued and the concurrent instance is left unchanged")
        take(decisions(current).loadC(pending.question.request.continuationId)) shouldBe None
        bytes(take(root(current).loadC(initial.identity)).get) shouldBe bytes(advanced)
        NativeHumanManagedHostObservation.counts(current) shouldBe (1, 0)
      } finally take(current.shutdownC())
    }
  }
}
