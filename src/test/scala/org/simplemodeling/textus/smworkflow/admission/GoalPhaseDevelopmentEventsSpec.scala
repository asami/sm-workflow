package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import java.time.Instant
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentCreate, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.event.{EventCursorRecordV1, EventId, EventLane, EventPersistencePolicy, EventRecord,
  EventRecordJsonV2, EventStorePersistenceDeclaration, EventTypeId, ManagedEventHistory}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.SubsystemEventStoreBootstrap
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.Lifecycle
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.cncf.workflow.{ContextReference, WorkflowComponentStorage, WorkflowMechanicalTransition}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as Generated
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take
import DevelopmentOperationalView as V

/** Pure envelope checks and actual managed native producer/retained-read fixtures.
  * @since   Oct. 25, 2026
  * @version Oct. 25, 2026
  * @author ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class GoalPhaseDevelopmentEventsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _subject = V.Subject(V.Car("g", "a", "v"), "component", "instance", "GoalPhase", "session", "repository", "phase")
  private val _at = Instant.parse("2026-10-25T00:00:00Z")
  private def _event = take(GoalPhaseDevelopmentEvents.createC(_subject, 7, "ApprovalRequested", "actual-question", _at, Some("trace")))
  "Significant GoalPhase events" should {
    "freeze one exact event identity and preserve independent trace correlation" in {
      Given("the actual immutable subject, revision, causation and transition clock")
      val first = _event
      val second = _event
      first.id shouldBe second.id
      first.payload shouldBe second.payload
      first.lane shouldBe EventLane.Transactional
      first.attributes("correlationId") shouldBe "trace"
      first.attributes("history.key") shouldBe take(DevelopmentEventCorrelation.keyC(_subject))
      first.sequence shouldBe 0L
      When("the owning EventStore assigns a large numeric sequence")
      val decoded = take(GoalPhaseDevelopmentEvents.decodeC(_subject, Vector(first.copy(sequence = 9007199254740993L))))
      Then("the public event retains the complete subject and decimal sequence without losing integer precision")
      decoded.head.hcursor.get[String]("sequence").toOption shouldBe Some("9007199254740993")
      decoded.head.hcursor.downField("subject").focus shouldBe Some(_subject.asJson)
    }
    "invalidate crossed subjects and forced correlation collisions rather than filtering after limit" in {
      Given("a committed event for one exact subject and another subject with a different Session")
      val committed = _event.copy(sequence = 1)
      val other = _subject.copy(sessionId = "another-session")
      val collision = committed.copy(attributes = take(DevelopmentEventCorrelation.attributesC(other)))
      val malformed = committed.copy(payload = Map("development" -> "{}"))
      val unknown = committed.copy(payload = Map("development" -> V.Event("unknown", _subject, committed.name,
        "7", _at.toString, "actual-question").asJson.noSpaces))
      When("the full-envelope decoder sees a crossed subject, forced lookup collision, malformed row or unknown schema")
      val observations = Vector(GoalPhaseDevelopmentEvents.decodeC(other, Vector(committed)),
        GoalPhaseDevelopmentEvents.decodeC(other, Vector(collision)),
        GoalPhaseDevelopmentEvents.decodeC(_subject, Vector(committed, malformed)),
        GoalPhaseDevelopmentEvents.decodeC(_subject, Vector(unknown)))
      Then("each whole observation fails rather than omitting its invalid rows after the history limit")
      observations.foreach(_.isSuccess shouldBe false)
    }
    "refuse invented lifecycle vocabulary and invalid native revisions" in {
      Given("an otherwise valid bound subject with zero revision, invented failure vocabulary or an unsupported Workflow family")
      When("the native event constructor attempts to attribute each requested fact")
      val events = Vector(GoalPhaseDevelopmentEvents.createC(_subject, 0, "ApprovalAccepted", "cause", _at),
        GoalPhaseDevelopmentEvents.createC(_subject, 1, "TestFailedMeansPhaseFailed", "cause", _at),
        GoalPhaseDevelopmentEvents.createC(_subject.copy(workflow = "SplitPhase"), 1, "GoalPhaseStarted", "cause", _at))
      Then("none can manufacture a genuine GoalPhase event")
      events.foreach(_.isSuccess shouldBe false)
    }
    "retain native completion and committed history after a post-commit response failure and normal host reopen" in {
      object Native extends GoalPhasePostCompletionSyncFixture {
        val sessionId = "development-terminal-spec"
        var persistentPolicy = true
        override protected def local_subject_capabilities: Vector[String] = Vector("user", "event_view")
        override protected def _with_runtime_(root: Path, scenario: String,
          host: Option[ThreeWorkflowRuntimeParameters.Definition], hostParameters: Map[String, String],
          nativeAcknowledgement: Option[String => Consequence[Unit]],
          goalAcknowledgement: Option[String => Consequence[Unit]], componentInstance: String)(
          task: (org.goldenport.cncf.subsystem.Subsystem, org.goldenport.cncf.component.Component,
            ExecutionContext, (Setup => Consequence[Unit]) => Unit) => Unit): Unit = {
          val database = root.resolve("operation.sqlite")
          if (!Files.exists(database)) DevelopmentEventHostFixture.initialize(database)
          super._with_runtime_(root, scenario, host, hostParameters, nativeAcknowledgement, goalAcknowledgement, componentInstance) {
            (owner, component, actor, setup) =>
              task(owner, component, actor, setup)
          }
        }
        def subjectFor(root: Path): V.Subject = {
          val input = recoveryHost(root).goal.input
          V.Subject(V.Car("org.example", "native-car", "1-SNAPSHOT"), SmWorkflowComponent.componentId.name,
            ComponentInstanceId(SmWorkflowComponent.componentId, "repository-spec").canonicalKey, "GoalPhase", sessionId,
            input.repositoryIdentity, input.phaseIdentity)
        }
        override protected def post_completion_parameters(root: Path): Map[String, String] = {
          val policy = if (!persistentPolicy) ThreeWorkflowNormalHostFixture.storagePlan
            else ThreeWorkflowNormalHostFixture.storagePlan.mapObject(fields => fields.add("policy", Json.obj(
            "revision" -> Json.fromString("development-native-spec-v1"), "rules" -> Json.arr(Json.obj(
              "eventType" -> Json.fromString("development"), "mode" -> Json.fromString("persist"))))))
          Map("sm-workflow.development-observation" -> V.Configuration(V.schema, subjectFor(root)).asJson.noSpaces,
            "textus.subsystem.storage.plan" -> policy.noSpaces)
        }
        def assertNoStart(episode: Episode, identity: String): Unit = episode.native.prepare(call => for {
          catalog <- GoalPhaseRuntimeCatalog.loadC()
          storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
            SmWorkflowComponent.componentId, _configuration_, Vector(catalog.definition), _collection_("instances"), _collection_("continuations"))
          instances <- storage.bindInstancesC()(using call.executionContext)
          current <- instances.loadC(InstanceIdentity(identity))(using call.executionContext)
          starts <- storage.bindMechanicalC(GoalPhaseNativeRun.startOperation, GoalPhaseNativeRun.codec)
          receipt <- starts.loadRetainedC(WorkflowMechanicalTransition.Reference(identity + ":ordered-start", "1"))(using call.executionContext)
          reader <- ManagedEventHistory.bindForCallC(call.core.copy(executionContext = episode.native.actor))
          key <- DevelopmentEventCorrelation.keyC(subjectFor(episode.root).copy(sessionId = identity))
          history <- reader.readC(ManagedEventHistory.Query(DevelopmentEventCorrelation.namespace, key, GoalPhaseDevelopmentEvents.names, 20))
          _ = {
            current shouldBe None
            receipt shouldBe None
            history.events shouldBe empty
          }
        } yield ())
        def observe(episode: Episode, history: Boolean): Json = {
          val actor = episode.native.actor
          val input = Json.obj("schemaVersion" -> Json.fromString(V.schema), "subject" -> subjectFor(episode.root).asJson)
          take(episode.native.owner.executeOperationResponseInContext(Request.of(component = episode.native.component.name,
            service = "DevelopmentObservationService", operation = if (history) "readDevelopmentHistory" else "readDevelopmentState",
            properties = List(Property("input", GoalPhaseNativeMetadata.record(input), None))), actor)) match {
            case OperationResponse.RecordResponse(record) => V.json(record)
            case _ => throw new AssertionError("Expected ordinary observation RecordResponse")
          }
        }
      }
      Given("an actual isolated normal host whose event policy does not persist development events")
      val unavailable = Native.freshRoot("development-start-policy-refusal")
      Native.persistentPolicy = false
      try Native.episode(unavailable, initialize = true) { e =>
        When("Start refuses the missing persistent policy before creating a native instance")
        e.native.call("startGoalPhase", NativeWorkflowOperationInputFixture.goal(
          Generated.GoalPhaseStartInput("phase-2", "sm-workflow"), Some(Native.sessionId))).isSuccess shouldBe false
        Then("no initial/active instance, Start receipt or committed development event remains")
        Native.assertNoStart(e, Native.sessionId)
      } finally Native.persistentPolicy = true
      Given("an actual normal managed Start, selected Compile/Test, independent fixture-owned reviews and native local closure")
      val root = Native.freshRoot("development-terminal")
      var before = Json.Null
      var committed = Json.Null
      var failed = false
      Native.episode(root, initialize = true, acknowledgement = Some(point =>
        if (point == "goal-local-completed" && !failed) { failed = true; Consequence.stateConflict("SPECIFICATION-OWNED post-commit response failure") }
        else Consequence.unit)) { e =>
        val input = Generated.GoalPhaseStartInput("phase-2", "sm-workflow")
        When("Start is attempted for a Session outside the one exact configured subject")
        val crossed = Native.sessionId + "-crossed"
        e.native.call("startGoalPhase", NativeWorkflowOperationInputFixture.goal(input, Some(crossed))).isSuccess shouldBe false
        Then("association refusal leaves no instance, Start receipt or event for that Session")
        Native.assertNoStart(e, crossed)
        When("the actual configured Session starts through the same normal provided Operation")
        val started = e.native.invoke[Generated.GoalPhaseStartResult]("startGoalPhase",
          NativeWorkflowOperationInputFixture.goal(input, Some(Native.sessionId)))
        started.sessionId shouldBe Native.sessionId
        val initial = e.current(Native.sessionId)
        before = Native.observe(e, true)
        val state = Native.observe(e, false)
        Native.observe(e, false).hcursor.downField("state").focus shouldBe state.hcursor.downField("state").focus
        Native.sameInstance(e.current(Native.sessionId), initial)
        var assigned = e.advance(Native.sessionId)
        Vector("RejectDependencyScope", "ApproveDependencyScope").foreach { choice =>
          val proposal = GoalPhaseDependencyScope.Proposal("fixture-dependency-" + choice, "1", "IMPLEMENTATION",
            GoalPhaseDependencyScope.Component("org.example.Product", "sm-workflow", "primary"),
            GoalPhaseDependencyScope.Component("org.example.Library", "library", "dependency"),
            ContextReference("fixture-usage", "1"), "SPECIFICATION-OWNED exact dependency scope decision",
            Vector("src/Owned.scala"), Vector("Provide the exact fixture capability"), Vector(ContextReference("step-1", "1")), Vector.empty)
          val result = Generated.GoalPhaseWorkResult(outcome = "decision-needed",
            summary = "SPECIFICATION-OWNED dependency scope request", findings = Vector.empty,
            acceptance = None, fixAssessment = None, issueReport = None, planRevision = None, reviewObservations = None,
            dependencyProposal = GoalPhaseDependencyScope.publicProposal(Some(proposal)), completionConditions = None,
            repairFindingSources = None, dependencyValidation = None, diagnosticRepair = None)
          val proposed = e.native.invoke[Generated.GoalPhaseWorkSubmissionResult]("submitGoalPhaseWorkResult", List(
            Property("sessionId", Native.sessionId, None), Property("result", result.toRecord(), None),
            Property("resultReference", Generated.WorkflowArtifactReference("fixture-proposal-" + choice, "1").toRecord(), None)))
          proposed.accepted shouldBe true
          val displayed = e.advance(Native.sessionId)
          displayed.kind shouldBe "Human"
          Native.observe(e, false).hcursor.downField("state").get[String]("attention").toOption shouldBe Some("human-required")
          var question = Option.empty[WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing]]
          e.native.prepare(call => call.goalQuestionC(InstanceIdentity(Native.sessionId)).map(value => { question = Some(value); () }))
          val actual = question.get
          val order = actual.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input]].request
          val reference = ContextReference("fixture-human-" + choice, order.expectedRevision.value)
          val answer = ContinuationResult(actual.handle, order.runId, order.continuationId, order.expectedRevision,
            order.context.snapshot, TypedValue(GoalPhaseOrderedHumanDecision.answerCodec.typeIdentity,
              GoalPhaseOrderedHumanDecision.Answer(choice, "SPECIFICATION-OWNED Human direction")), reference,
            Vector.empty, ExecutionEvidence(Vector(reference), Some("specification-human")))
          e.native.prepare(call => for {
            prepared <- org.simplemodeling.textus.smworkflow.preparation.NativeHumanDecisionPreparation.goalC(
              displayed.human.get.humanDecisionContext.get, Generated.GoalPhaseOrderedDecisionAnswer(choice, answer.result.value.rationale),
              Generated.WorkflowArtifactReference(reference.identity, reference.revision),
              Generated.WorkflowExecutionEvidence(Vector.empty, answer.evidence.workerIdentity, None, None),
              "participant", "normal-development-host", call.executionContext.clock.instant().minusSeconds(60))
            _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
          } yield ())
          e.native.invoke[Generated.GoalPhaseOrderedDecisionSubmissionResult]("submitGoalPhaseOrderedDecision",
            take(NativeWorkflowOperationInputFixture.submissionC(answer))).accepted shouldBe true
          assigned = e.advance(Native.sessionId)
          assigned.work.get.kind shouldBe "ImplementStep"
        }
        e.native.submitImplementation(assigned).accepted shouldBe true
        e.native.acceptStep(e.advance(Native.sessionId))
        e.acceptPhase(e.advance(Native.sessionId))
        When("native completion commits and its subsequent response hook fails")
        val result = e.native.call("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(Native.sessionId))
        result.isSuccess shouldBe false
        failed shouldBe true
        e.current(Native.sessionId).lifecycle shouldBe Lifecycle.Completed
        committed = Native.observe(e, true)
        Then("the committed event and genuine native terminal state survive the failed response")
        committed.hcursor.get[String]("status").toOption shouldBe Some("observed")
        committed.hcursor.get[Vector[Json]]("events").toOption.get.map(_.hcursor.get[String]("eventType").toOption.get) shouldBe
          Vector("GoalPhaseCompleted", "ApprovalAccepted", "ApprovalRequested", "ApprovalRejected", "ApprovalRequested", "GoalPhaseStarted")
        before.hcursor.get[Vector[Json]]("events").toOption.get.map(_.hcursor.get[String]("eventType").toOption.get) shouldBe Vector("GoalPhaseStarted")
      }
      Native.episode(root) { e =>
        When("the owning host reopens and advances its already-completed Session normally")
        e.advance(Native.sessionId).kind shouldBe "Terminal"
        Then("the retained event identity and sequence are reused without duplicate completion or backfill")
        Native.observe(e, true).hcursor.downField("events").focus shouldBe committed.hcursor.downField("events").focus
        Native.observe(e, false).hcursor.downField("state").get[String]("workflowState").toOption shouldBe Some("completed")
      }
    }
  }
}

/** Test-owned first installation; never replayed when the normal host reopens.
  * Introduced: Oct. 25, 2026
  * Updated: Oct. 25, 2026
  * Author: ASAMI, Tomoharu
  */
private[admission] object DevelopmentEventHostFixture extends Matchers {
  def initialize(path: Path): Unit = {
    Files.exists(path) shouldBe false
    given context: ExecutionContext = ExecutionContext.create()
    def _collection_(name: String) =
      DataStore.CollectionId.EntityStore(EntityCollectionId("smworkflow", "cli", name))
    val declaration = take(EventStorePersistenceDeclaration.createC("events", _collection_("cursor"),
      _collection_("events"), _collection_("indexes"), Vector(EventStorePersistenceDeclaration.WorkflowGrant(
        SmWorkflowComponent.componentId, "workflow", _collection_("instances"), _collection_("continuations")))))
    val policy = take(EventPersistencePolicy.createC("development-fixture-v1", Vector(
      EventPersistencePolicy.Rule(EventTypeId("development"), EventPersistencePolicy.Mode.Persist))))
    val scope = ScopeContext(ScopeKind.Subsystem, "development-fixture", None, context.observability)
    val resolved = ResolvedConfiguration(HostConfiguration(Map(
      "textus.subsystem.user-mode" -> ConfigurationValue.StringValue("standalone"),
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString))), ConfigurationTrace.empty)
    val bootstrap = take(SubsystemEventStoreBootstrap.createC(policy, declaration, scope, resolved,
      None, AliasResolver.empty))
    try take(bootstrap.initializeCursorC()) finally take(bootstrap.shutdownC())

    val store = take(SqlDataStore.sqliteC(path.toString))
    try {
      // Temporarily provision canonical schema before the real producer runs.
      // This generic row is deleted, never dispatched or counted as a Workflow outcome.
      val at = Instant.parse("2026-10-25T00:00:00Z")
      val schema = EventRecord(EventId("fixture", "schema", Some(at), Some("schema")), "SchemaOnly",
        "development", Map.empty, Map("history.namespace" -> "fixture", "history.key" -> "schema"),
        at, persistent = true, EventRecord.Status.Stored, EventLane.Transactional, sequence = 1L)
      val effect = take(EventRecordJsonV2.createSideEffectC(_collection_("events"), schema))
      take(store.create(effect.collection, effect.entryId, effect.record))
      take(store.delete(effect.collection, effect.entryId))
      take(store.load(effect.collection, effect.entryId)) shouldBe None
      val row = take(store.load(_collection_("cursor"), DataStore.StringEntryId("global"))).getOrElse(
        throw new AssertionError("First installation must retain its initial event cursor"))
      val cursor = take(EventCursorRecordV1.decodeC(Record(row.fields.filterNot(_.key == "id"))))
      cursor.revision shouldBe 1L
      cursor.lastSequence shouldBe 0L
    } finally take(store.closeC())
  }
}
