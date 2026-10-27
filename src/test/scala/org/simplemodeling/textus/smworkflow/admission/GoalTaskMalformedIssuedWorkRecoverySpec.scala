/**
 * @since   Oct. 26, 2026
 * @version Oct. 27, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import cats.data.NonEmptyVector
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.{NativeGoalTaskStartInput, NativeRetainedWorkRecoveryPreparation}
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import GoalTaskMalformedIssuedWorkRecovery as R

/** Synthetic domain/codec specifications; no genuine Human or nonexecution observation. */
private[smworkflow] object GoalTaskMalformedIssuedWorkRecoveryFixture {
  def take[A](value: Consequence[A]): A = value.toOption.get
  val input = StandaloneFixExecution.Input("fixture-repository", "fixture-request", "Selected behavior",
    Vector("src"), Vector("compile", "focused"), Vector("behavior"), Vector(ContextReference("fixture-source", "1")),
    false, scalaLint = "off", task = Some(StandaloneFixExecution.Task("direct-instruction", Vector.empty)))
  val selected = Vector(GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile),
    GoalPhaseNativeValidation.Check("focused", 2, WorkflowSbtValidation.Kind.TestOnly))
  val state = take(StandaloneFixExecution.startC(input, "fixture-human", selected)).copy(automaticCount = 2, ordinaryCount = 1)
  val payload = BoundedWorkRuntime.Payload(state, "/fixture", "main", "a" * 40,
    unrelated = Some(Vector.empty), implementationWorkers = Some(Vector("fixture-implementer")))
  val target = R.Target("fixture-component", "fixture-session", "fixture-human", 3L, "fixture-old-issue",
    Json.obj("retainedNativeFrontier" -> true.asJson), Json.obj("immutableOriginalIssue" -> true.asJson),
    payload, Json.obj("reasoningClass" -> "CodingEngineeringStandard".asJson),
    Json.obj("mappingIdentity" -> "fixture-mapping".asJson), "1")
  val reference = ContextReference("fixture-human-message", "1")
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalTaskMalformedIssuedWorkRecoverySpec extends AnyWordSpec with Matchers with ThreeWorkflowManagedRepositoryFixture {
  import GoalTaskMalformedIssuedWorkRecoveryFixture.*
  private val native = GoalTaskNativeRun
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations)

  private final class Episode(val root: Path, owner: Subsystem, component: Component, actor: ExecutionContext,
    val project: (Setup => Consequence[Unit]) => Unit) {
    def call(operation: String, properties: List[Property]): Consequence[OperationResponse] =
      owner.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalTaskService",
        operation = operation, properties = properties), actor)
    def value[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
      read_value[A](take(call(operation, properties)))
    def advance(session: String): V.GoalTaskView = value("advanceGoalTask", List(Property("sessionId", session, None)))
    def describe(session: String, old: String): V.GoalTaskMalformedIssuedWorkRecoveryDescriptionResult =
      value("describeGoalTaskMalformedIssuedWorkRecovery", List(Property("input",
        V.GoalTaskMalformedIssuedWorkRecoveryDescriptionInput(session, old), None)))
    def recover(input: V.GoalTaskMalformedIssuedWorkRecoveryInput): V.GoalTaskMalformedIssuedWorkRecoveryDescriptionResult =
      value("recoverGoalTaskMalformedIssuedWork", List(Property("input", input, None)))
    def current(session: String): InstanceRecord = {
      var result = Option.empty[InstanceRecord]
      project(call => for {
        storage <- call.taskStorageC()
        instances <- storage.bindInstancesC()(using call.executionContext)
        current <- instances.loadC(InstanceIdentity(session))(using call.executionContext)
        _ = result = current
      } yield ())
      result.get
    }
    def issue(id: String): WorkflowInteraction[StandaloneFixExecution.Work, Nothing] = {
      var result = Option.empty[WorkflowInteraction[StandaloneFixExecution.Work, Nothing]]
      project(call => for {
        storage <- call.taskStorageC()
        issued <- storage.bindIssuedC(native.completionOperation, native.inputCodec)
        value <- issued.loadC(ContinuationIdentity(id))(using call.executionContext)
        _ = result = value
      } yield ())
      result.get
    }
    def retained(session: String, old: String): WorkflowManagedWorkOrderSupersession.Retained[R.Checkpoint] = {
      var result = Option.empty[WorkflowManagedWorkOrderSupersession.Retained[R.Checkpoint]]
      project(call => for {
        storage <- call.taskStorageC()
        supersession <- storage.bindWorkOrderSupersessionC(native.completionOperation, native.inputCodec, R.checkpointCodec)
        saved <- supersession.loadRetainedC(ContinuationIdentity(old), InstanceIdentity(session))(using call.executionContext)
        _ = result = saved
      } yield ())
      result.get
    }
    def provision(description: V.GoalTaskMalformedIssuedWorkRecoveryDescriptionResult, choice: String,
      name: String, rationale: String = "Specification-owned exact-target direction; no actual Human consent claimed"): V.GoalTaskMalformedIssuedWorkRecoveryInput = {
      val reference = V.WorkflowArtifactReference("specification-human-" + name, "1")
      val answer = V.GoalTaskMalformedIssuedWorkRecoveryInput(description.sessionId,
        take(R.targetC(description.target)).oldContinuationId, description.target, choice,
        rationale, "participant", reference)
      project(call => for {
        prepared <- NativeRetainedWorkRecoveryPreparation.prepareC(description, answer, reference,
          V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None), "participant",
          "normal-development-host", call.executionContext.clock.instant().minusSeconds(1))
        _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
      } yield ())
      answer
    }
    /** Initial historical defect is issued through CNCF's managed suspension.
      * Neither the old issue nor any raw native row is rewritten. */
    def startMalformed(): (String, String) = {
      val input = V.GoalTaskInput("sm-workflow", "malformed-task-spec", "Preserve the adopted request through recovery",
        NonEmptyVector.one("docs/phase.md"), NonEmptyVector.one("test"), NonEmptyVector.one("objective"),
        NonEmptyVector.one(V.WorkflowArtifactReference("specification-direct-request", "1")), false,
        "documentation", "off", 10, "direct-instruction", Vector.empty)
      val prepared = take(NativeGoalTaskStartInput.prepareC(Map("input" -> "input", "selection-id" -> "malformed-task-selection",
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "malformed-task-start",
        "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
      project(call => call.setupC(_setup_commands_(root, "TaskRecovery").size).flatMap { _ =>
        val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        call.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
      })
      val started = value[V.GoalTaskView]("startGoalTask", List(Property("input", input, None),
        Property("selectionId", "malformed-task-selection", None), Property("participant", "participant", None),
        Property("idempotencyKey", "malformed-task-start", None)))
      var old = ""
      project(call => for {
        storage <- call.taskStorageC()
        instances <- storage.bindInstancesC()(using call.executionContext)
        from <- instances.loadC(InstanceIdentity(started.sessionId))(using call.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("Started Task missing")))
        payload <- native.loadPayloadC(storage, started.sessionId, from.revision.value, call.core)
        capability = "goal-task-implementation-capability"
        metadata = StateMachineRequiredOperationMetadata(ContextContract(capability + ":context", Vector.empty, Vector.empty),
          CompletionContract(capability + ":completion", Vector.empty), EvidenceContract(capability + ":evidence", Vector.empty), Vector.empty)
        required = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(capability), "ImplementGoalTask",
          StateMachineOperationIdentity("GoalTaskImplementationService", "implementGoalTask"),
          Some(StateMachineInputTypeReference(native.inputCodec.typeIdentity)), Some(StateMachineResultTypeReference(native.resultCodec.typeIdentity)), metadata)
        work = StandaloneFixExecution.Work(started.sessionId, from.revision.value.toString, "Implement", payload.state)
        context = ContextBundle("Specification historical malformed issue", payload.state.input.sourceReferences, Vector.empty,
          ContextSnapshot(from.definition.workflowRevision.value, evidenceRevision = Some(work.sourceRevision)))
        continuation = Continuation(StateMachineRunIdentity(started.sessionId),
          ContinuationIdentity(started.sessionId + ":standalone:" + from.revision.next.value),
          StateMachineRevision(from.revision.next.value.toString), required, context)
        _ = old = continuation.continuationId.value
        handle <- WorkflowHandle.fromRecordC(org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId, from)
        interaction = WorkflowInteraction[StandaloneFixExecution.Work, Nothing](handle, WorkflowContinuation.WorkOrder(
          ContinuationRequest(continuation.runId, continuation.continuationId, continuation.expectedRevision, required.identity,
            required.operation, Some(TypedValue(native.inputCodec.typeIdentity, work)), required.resultType, context,
            metadata.completionContract, metadata.evidenceContract, native.completionOperation),
          ExecutionRequirement(Vector(CapabilityRequirement(capability)), RiskLevel("SOURCE_IMPLEMENTATION"), ReasoningLevel.Standard, false),
          MinimalPresentation("Specification old issue", input.objective)))
        entry = native.history(from, "standalone-issued:Implement").copy(suspension = Some(SuspensionBoundary(
          NativeIdentity(old), from.revision.next, ContextSnapshotReference(context.snapshot.workflowRevision),
          CompletionReference(metadata.completionContract.identity), EvidenceReference(metadata.evidenceContract.identity), from.correlation)))
        issued <- storage.bindIssuedC(native.completionOperation, native.inputCodec)
        _ <- issued.suspendC(WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
          _configuration_, from, from.revision, entry,
          ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available)), interaction)(using call.executionContext)
      } yield ())
      (started.sessionId, old)
    }
  }
  private def withEpisode(root: Path, componentinstance: String = "repository-spec")(body: Episode => Unit): Unit =
    _with_runtime_(root, "TaskRecovery", hostParameters = BoundedWorkExecutionAdmissionFixture.parameters,
      componentInstance = componentinstance) {
      (owner, component, actor, project) => body(new Episode(root, owner, component, actor, project))
    }
  private def root(): Path = Files.createTempDirectory(Files.createDirectories(
    Path.of("target/sm-workflow/work/task-malformed-managed").toAbsolutePath), "episode-").toRealPath()

  /** Each managed read resolves a fresh DefinitionBinding. Compare the validated
    * complete native representation, including every field and history entry,
    * rather than that binding's JVM object identity. */
  private def assertSameNativeRecord(actual: InstanceRecord, expected: InstanceRecord): Unit = {
    def canonical(record: InstanceRecord): Json =
      io.circe.parser.parse(take(WorkflowInstanceRecordJsonV1.encodeC(record))).toOption.get
    canonical(actual) shouldBe canonical(expected)
  }

  /** The managed view and public CLI use different Record naming projections.
    * Compare every issued-context member through the ordinary public codec. */
  private def assertSameIssuedContext(actual: Option[V.WorkflowIssuedWorkContext],
    expected: Option[V.WorkflowIssuedWorkContext]): Unit = {
    def canonical(value: Option[V.WorkflowIssuedWorkContext]): Option[Json] = value.map { context =>
      val json = io.circe.parser.parse(take(context.toRecord().toJsonStringC)).toOption.get
      GoalPhaseNativeMetadata.normalizePublicJson(json).toOption.get
    }
    canonical(actual) shouldBe canonical(expected)
  }

  /** Focused installed qualification reuses the existing managed historical
    * issuer and trusted specification host. The actual public describe/recover/
    * Advance/Submit calls below are independent installed CLI processes. No raw
    * workflow row is seeded or edited and no real user Task is selected.
    */
  private[admission] def qualifyInstalledRecovery(scenario: NativeWorkflowCliFixture.Scenario,
    executable: Path, installations: Path, covering: Boolean): Unit = {
    import StandaloneFixExecution.given
    val choice = if (covering) R.nonexecutionChoice else R.choices.head
    val effects = if (covering) "NOT_EXECUTED" else "UNKNOWN"
    var session = ""
    var old = ""
    var original = Option.empty[WorkflowInteraction[StandaloneFixExecution.Work, Nothing]]
    var before = Option.empty[InstanceRecord]
    var oldbinding = Option.empty[V.WorkflowIssuedWorkContext]
    def register(): Unit = {
      val assembly = scenario.root.resolve(".textus/assembly.yaml")
      val text = Files.readString(assembly)
      require(text.contains("components: []"), "Register only the retained specification-owned normal host")
      Files.writeString(assembly, text.replace("components: []",
        "components:\n  - namespace: org.simplemodeling.textus\n    id: SmWorkflow\n    version: 0.1.0-SNAPSHOT"))
    }
    def json(value: org.goldenport.record.RecordPresentable): String = take(value.toRecord().toJsonStringC)
    def call(operation: String, parameters: Vector[String]): NativeWorkflowCliFixture.Result =
      scenario.callInstalled(executable, "GoalTaskService", operation, parameters, Some(installations),
        Some(scenario.root.resolve(".textus/assembly.yaml")))
    def advance(): V.GoalTaskView = call("advanceGoalTask", Vector("--sessionId", session)).business[V.GoalTaskView]
    withEpisode(scenario.root, componentinstance = "default") { e =>
      val started = e.startMalformed()
      session = started._1
      old = started._2
      original = Some(e.issue(old))
      before = Some(e.current(session))
    }
    register()
    oldbinding = advance().issuedWorkContext
    oldbinding.get.continuationId shouldBe old
    val description = call("describeGoalTaskMalformedIssuedWorkRecovery", Vector("--input",
      json(V.GoalTaskMalformedIssuedWorkRecoveryDescriptionInput(session, old))))
      .business[V.GoalTaskMalformedIssuedWorkRecoveryDescriptionResult]
    description.sessionId shouldBe session
    description.outcome shouldBe "decision-required"
    description.externalEffects shouldBe "UNKNOWN"
    val target = take(R.targetC(description.target))
    val absent = V.GoalTaskMalformedIssuedWorkRecoveryInput(session, old, description.target, choice,
      "SPECIFICATION input alone is not independently admitted evidence", "participant",
      V.WorkflowArtifactReference("specification-no-admission", "1"))
    call("recoverGoalTaskMalformedIssuedWork", Vector("--input", json(absent))).exitCode should not be 0
    advance().issuedWorkContext shouldBe oldbinding
    var direction = Option.empty[V.GoalTaskMalformedIssuedWorkRecoveryInput]
    withEpisode(scenario.root, componentinstance = "default") { e =>
      // Only this independently trusted fixture host provisions its admitted
      // SPECIFICATION Human source, never an asserted real chat/user event.
      direction = Some(e.provision(description, choice, "installed-" + effects.toLowerCase,
        if (covering) "SPECIFICATION-OWNED independent host observation covers this exact fixture issue and caller sources; no external executor was invoked"
        else "SPECIFICATION-OWNED exact-target Human branch qualification; no actual user consent is claimed and external effects remain UNKNOWN"))
      e.issue(old) shouldBe original.get
      assertSameNativeRecord(e.current(session), before.get)
    }
    register()
    val recovered = call("recoverGoalTaskMalformedIssuedWork", Vector("--input", json(direction.get)))
      .business[V.GoalTaskMalformedIssuedWorkRecoveryDescriptionResult]
    recovered.outcome shouldBe "recovered-awaiting-advance"
    recovered.externalEffects shouldBe effects
    recovered.successorContinuationId shouldBe None
    val successor = advance()
    successor.kind shouldBe "Request"
    successor.sessionId shouldBe session
    val binding = successor.issuedWorkContext.get
    binding.continuationId should not be old
    binding.executionRequirement.getString("reasoning_class")
      .orElse(binding.executionRequirement.getString("reasoningClass")) shouldBe Some("DesignStandard")
    binding.executionRequirement.getRecord("disposition") should not be empty
    val workjson = io.circe.parser.parse(take(successor.work.get.toJsonStringC)).toOption.get
    val normalized = GoalPhaseNativeMetadata.normalizePublicJson(workjson).toOption.get
    normalized.hcursor.get[StandaloneFixExecution.State]("state").toOption.get shouldBe target.business.state
    val redisplayed = advance()
    redisplayed.issuedWorkContext shouldBe successor.issuedWorkContext
    redisplayed.work shouldBe successor.work
    val collected = call("recoverGoalTaskMalformedIssuedWork", Vector("--input", json(direction.get)))
      .business[V.GoalTaskMalformedIssuedWorkRecoveryDescriptionResult]
    collected.outcome shouldBe "already-recovered"
    collected.successorContinuationId shouldBe Some(binding.continuationId)
    collected.externalEffects shouldBe effects
    val result = V.GoalTaskWorkResult("completed", "SPECIFICATION delayed original result; must remain unaccepted",
      Vector.empty, NonEmptyVector.one(V.GoalTaskCondition("objective", true, "Specification-owned semantic result")),
      Vector.empty, None, None)
    val reference = V.WorkflowArtifactReference("specification-delayed-original-result", "1")
    val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-implementer"), None,
      Some(V.WorkflowExecutionObservation("INLINE", BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
        BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity, BoundedWorkExecutionAdmissionFixture.profile.identity)))
    val late = call("submitGoalTaskWorkResult", Vector("--sessionId", session, "--result", json(result),
      "--resultReference", json(reference), "--executionEvidence", json(execution), "--issuedWorkContext", json(oldbinding.get)))
    late.exitCode should not be 0
    advance().issuedWorkContext shouldBe successor.issuedWorkContext
    withEpisode(scenario.root, componentinstance = "default") { e =>
      val retained = e.retained(session, old)
      retained.receipt.payload.direction.target.business shouldBe target.business
      retained.receipt.payload.externalEffects shouldBe effects
      e.issue(old) shouldBe original.get
      val current = e.current(session)
      current.history.take(before.get.history.size) shouldBe before.get.history
      current.suspension should not be empty
      assertSameIssuedContext(e.advance(session).issuedWorkContext, successor.issuedWorkContext)
    }
    register()
    advance().issuedWorkContext shouldBe successor.issuedWorkContext
  }

  "Managed public Task recovery" should {
    "retain one disposition across physical reopen, then issue and redisplay one real successor and collect after its completion at a Human boundary" in {
      val directory = root()
      var session = ""
      var old = ""
      var original = Option.empty[WorkflowInteraction[StandaloneFixExecution.Work, Nothing]]
      var direction = Option.empty[V.GoalTaskMalformedIssuedWorkRecoveryInput]
      var successor = Option.empty[V.GoalTaskView]
      var target = Option.empty[R.Target]
      var oldBinding = Option.empty[V.WorkflowIssuedWorkContext]
      withEpisode(directory) { e =>
        val started = e.startMalformed()
        session = started._1; old = started._2
        original = Some(e.issue(old))
        oldBinding = e.advance(session).issuedWorkContext
        val description = e.describe(session, old)
        description.outcome shouldBe "decision-required"
        description.externalEffects shouldBe "UNKNOWN"
        target = Some(take(R.targetC(description.target)))
        direction = Some(e.provision(description, R.choices.head, "durable"))
        val recovered = e.recover(direction.get)
        recovered.outcome shouldBe "recovered-awaiting-advance"
        recovered.successorContinuationId shouldBe None
        e.current(session).suspension shouldBe None
        e.retained(session, old).receipt.payload.direction.target.business shouldBe target.get.business
        e.issue(old) shouldBe original.get
        e.project(call => call.withTaskClaimsC { claims =>
          claims.claimC(ContinuationIdentity(old)).isSuccess shouldBe false
          claims.completeC(ContinuationIdentity(old), "late-old-token").isSuccess shouldBe false
          Consequence.unit
        })
      }
      // Closing/reopening the owning runtime also closes/reopens physical SQLite.
      withEpisode(directory) { e =>
        e.recover(direction.get).outcome shouldBe "already-recovered"
        val issued = e.advance(session)
        issued.kind shouldBe "Request"
        val id = issued.issuedWorkContext.get.continuationId
        id should not be old
        val actual = e.issue(id).current.asInstanceOf[WorkflowContinuation.WorkOrder[StandaloneFixExecution.Work]]
        actual.request.input.get.value.state shouldBe target.get.business.state
        actual.request.context.references should contain(ContextReference(old, "1"))
        actual.requirement.reasoningClass should not be empty
        actual.requirement.disposition should not be empty
        e.advance(session).issuedWorkContext shouldBe issued.issuedWorkContext
        e.recover(direction.get).successorContinuationId shouldBe Some(id)
        e.issue(old) shouldBe original.get
        successor = Some(issued)
      }
      withEpisode(directory) { e =>
        val issued = e.advance(session)
        issued.issuedWorkContext shouldBe successor.get.issuedWorkContext
        val reference = V.WorkflowArtifactReference("specification-successor-result", "1")
        val result = V.GoalTaskWorkResult("decision-needed", "Specification successor completed with unresolved business meaning",
          Vector.empty, NonEmptyVector.one(V.GoalTaskCondition("objective", false, "Fixture does not claim Task acceptance")),
          Vector("Human business judgment remains required"), None, None)
        val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-worker"), None,
          Some(V.WorkflowExecutionObservation("INLINE", BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
            BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity, BoundedWorkExecutionAdmissionFixture.profile.identity)))
        val fields = List(Property("sessionId", session, None),
          Property("result", result, None), Property("resultReference", reference, None), Property("executionEvidence", execution, None))
        val before = e.current(session)
        e.call("submitGoalTaskWorkResult", fields).isSuccess shouldBe false
        e.call("submitGoalTaskWorkResult", fields :+ Property("issuedWorkContext", oldBinding.get, None)).isSuccess shouldBe false
        e.call("submitGoalTaskWorkResult", fields :+ Property("issuedWorkContext",
          issued.issuedWorkContext.get.copy(runId = "foreign-session"), None)).isSuccess shouldBe false
        Vector(
          issued.issuedWorkContext.get.copy(schemaVersion = "foreign-protocol"),
          issued.issuedWorkContext.get.copy(expectedRevision = "999"),
          issued.issuedWorkContext.get.copy(resultType = Some("StandaloneFixWorkResult")),
          issued.issuedWorkContext.get.copy(completion = Record.data("identity" -> "foreign-completion")),
          issued.issuedWorkContext.get.copy(context = Record.data("summary" -> "foreign-context")),
          issued.issuedWorkContext.get.copy(handle = Record.data("instanceIdentity" -> "foreign-instance"))
        ).foreach(binding => e.call("submitGoalTaskWorkResult", fields :+
          Property("issuedWorkContext", binding, None)).isSuccess shouldBe false)
        e.call("submitGoalTaskWorkResult", fields :+ Property("issuedWorkContext",
          issued.issuedWorkContext.get.toRecord().removeKeys(Set("context")), None)).isSuccess shouldBe false
        assertSameNativeRecord(e.current(session), before)
        val submitted = e.value[V.GoalTaskView]("submitGoalTaskWorkResult", fields :+
          Property("issuedWorkContext", issued.issuedWorkContext.get, None))
        submitted.kind shouldBe "Submitted"
        e.current(session).suspension shouldBe None
        e.recover(direction.get).successorContinuationId shouldBe Some(issued.issuedWorkContext.get.continuationId)
        e.retained(session, old).receipt.payload.direction.target.business shouldBe target.get.business
        e.issue(old) shouldBe original.get
        // The fixture deliberately submits decision-needed: continuation completion
        // retains a Human business boundary, rather than claiming Task acceptance.
        val human = e.advance(session)
        withClue(s"Successor completion must retain its explicit decision-needed boundary; actual ${human.kind}: ${human.message}. ") {
          human.kind shouldBe "Human"
          human.message shouldBe result.summary
        }
        human.issuedWorkContext shouldBe None
        human.work shouldBe None
        e.project(call => for {
          storage <- call.taskStorageC()
          current <- storage.bindInstancesC()(using call.executionContext).flatMap(_.loadC(InstanceIdentity(session))(using call.executionContext))
          payload <- native.loadPayloadC(storage, session, current.get.revision.value, call.core)
          _ = payload.supersededWork shouldBe None
          _ = payload.state.stage shouldBe "Human"
          _ = payload.state.message shouldBe result.summary
          _ = payload.state.semanticReference shouldBe Some(ContextReference(reference.identity, reference.revision))
          _ = payload.state.input shouldBe target.get.business.state.input
          _ = payload.state.automaticCount shouldBe target.get.business.state.automaticCount
          _ = payload.state.ordinaryCount shouldBe target.get.business.state.ordinaryCount
          _ = payload.state.repairControl shouldBe target.get.business.state.repairControl
          _ = current.get.suspension shouldBe None
        } yield ())
      }
      withEpisode(directory) { e =>
        // Historical collection after completion cannot authorize another issue.
        e.recover(direction.get).outcome shouldBe "already-recovered"
        e.recover(direction.get).successorContinuationId shouldBe successor.get.issuedWorkContext.map(_.continuationId)
        val before = e.current(session)
        e.recover(direction.get)
        assertSameNativeRecord(e.current(session), before)
        val human = e.advance(session)
        withClue(s"Reopened completed successor must remain at the same Human boundary; actual ${human.kind}: ${human.message}. ") {
          human.kind shouldBe "Human"
          human.message shouldBe "Specification successor completed with unresolved business meaning"
        }
        human.issuedWorkContext shouldBe None
        human.work shouldBe None
        assertSameNativeRecord(e.current(session), before)
        e.issue(old) shouldBe original.get
      }
    }

    "retire only independently covered nonexecution and collect its one ordinary successor after reopening" in { qualifyCoveringNonexecution() }

    "leave the exact native frontier and business checkpoint unchanged for absent evidence, Defer and stale claim-release targets" in { qualifyPriorClaimRefusal() }

    "refuse known native claimed and completed old work despite independently provisioned fixture direction" in {
      Vector(false, true).foreach { complete => withEpisode(root()) { e =>
        val (session, old) = e.startMalformed()
        val description = e.describe(session, old)
        val direction = e.provision(description, R.choices.head, if (complete) "completed" else "claimed")
        val before = e.current(session)
        val original = e.issue(old)
        e.project(call => call.withTaskClaimsC { claims => for {
          claimed <- claims.claimC(ContinuationIdentity(old))
          _ <- if (complete) claims.completeC(ContinuationIdentity(old), claimed.claimId.get).map(_ => ()) else Consequence.unit
        } yield () })
        e.call("recoverGoalTaskMalformedIssuedWork", List(Property("input", direction, None))).isSuccess shouldBe false
        e.call("describeGoalTaskMalformedIssuedWorkRecovery", List(Property("input",
          V.GoalTaskMalformedIssuedWorkRecoveryDescriptionInput(session, old), None))).isSuccess shouldBe false
        assertSameNativeRecord(e.current(session), before)
        e.issue(old) shouldBe original
      } }
    }

    "preserve legacy Task omission while rejecting any supplied mismatched issued binding" in {
      withEpisode(root()) { e =>
        val (session, old) = e.startMalformed()
        val issued = e.advance(session)
        val reference = V.WorkflowArtifactReference("specification-legacy-result", "1")
        val result = V.GoalTaskWorkResult("decision-needed", "Fixture legacy semantic completion",
          Vector.empty, NonEmptyVector.one(V.GoalTaskCondition("objective", false, "Business judgment remains open")),
          Vector("Specification-owned unresolved question"), None, None)
        val fields = List(Property("sessionId", session, None), Property("result", result, None),
          Property("resultReference", reference, None), Property("executionEvidence",
            V.WorkflowExecutionEvidence(Vector(reference), Some("specification-legacy-worker"), None, None), None))
        val before = e.current(session)
        e.call("submitGoalTaskWorkResult", fields :+ Property("issuedWorkContext",
          issued.issuedWorkContext.get.copy(expectedRevision = "999"), None)).isSuccess shouldBe false
        assertSameNativeRecord(e.current(session), before)
        e.value[V.GoalTaskView]("submitGoalTaskWorkResult", fields).kind shouldBe "Submitted"
        e.current(session).suspension shouldBe None
      }
    }

    "refuse the original entity snapshot after a competing native claim-release without a partial checkpoint" in {
      withEpisode(root()) { e =>
        val (session, old) = e.startMalformed()
        val description = e.describe(session, old)
        val target = take(R.targetC(description.target))
        val direction = e.provision(description, R.choices.head, "entity-race")
        val before = e.current(session)
        val original = e.issue(old)
        var refused = false
        e.project(call => for {
          storage <- call.taskStorageC()
          adapter <- storage.bindWorkOrderSupersessionC(native.completionOperation, native.inputCodec, R.checkpointCodec)
          observed <- adapter.inspectC(ContinuationIdentity(old), InstanceIdentity(session))(using call.executionContext)
          pending = observed.get
          _ <- call.withTaskClaimsC { claims => for {
            claimed <- claims.claimC(ContinuationIdentity(old))
            _ <- claims.releaseC(ContinuationIdentity(old), claimed.claimId.get)
          } yield () }
          checkpoint = R.Checkpoint(R.Direction(target, direction.choice, direction.rationale, direction.participant,
            ContextReference(direction.reference.identity, direction.reference.revision)), "UNKNOWN")
          attempted = adapter.executeC(ContinuationIdentity(old), pending.current.revision, pending.entityRevision,
            pending.current, native.history(pending.current, R.resumeCause + old),
            TypedValue(R.checkpointCodec.typeIdentity, checkpoint))(using call.executionContext)
          _ = refused = !attempted.isSuccess
          saved <- adapter.loadRetainedC(ContinuationIdentity(old), InstanceIdentity(session))(using call.executionContext)
          _ = saved shouldBe None
        } yield ())
        refused shouldBe true
        assertSameNativeRecord(e.current(session), before)
        e.issue(old) shouldBe original
        e.call("describeGoalTaskMalformedIssuedWorkRecovery", List(Property("input",
          V.GoalTaskMalformedIssuedWorkRecoveryDescriptionInput(session, old), None))).isSuccess shouldBe false
      }
    }
  }

  private[admission] def qualifyCoveringNonexecution(): Unit = {
      val directory = root()
      var direction = Option.empty[V.GoalTaskMalformedIssuedWorkRecoveryInput]
      var session = ""
      var old = ""
      var original = Option.empty[WorkflowInteraction[StandaloneFixExecution.Work, Nothing]]
      withEpisode(directory) { e =>
        val started = e.startMalformed()
        session = started._1
        old = started._2
        original = Some(e.issue(old))
        val description = e.describe(session, old)
        val before = e.current(session)
        val missing = V.GoalTaskMalformedIssuedWorkRecoveryInput(session, old, description.target,
          R.nonexecutionChoice, "Specification source locator is not covering evidence", "participant",
          V.WorkflowArtifactReference("absent-nonexecution", "1"))
        e.call("recoverGoalTaskMalformedIssuedWork", List(Property("input", missing, None))).isSuccess shouldBe false
        assertSameNativeRecord(e.current(session), before)
        val humanOnly = e.provision(description, R.choices.head, "human-is-not-nonexecution")
        e.call("recoverGoalTaskMalformedIssuedWork", List(Property("input",
          humanOnly.copy(choice = R.nonexecutionChoice), None))).isSuccess shouldBe false
        assertSameNativeRecord(e.current(session), before)
        direction = Some(e.provision(description, R.nonexecutionChoice, "covering-nonexecution"))
        val recovered = e.recover(direction.get)
        recovered.externalEffects shouldBe "NOT_EXECUTED"
        recovered.outcome shouldBe "recovered-awaiting-advance"
        val checkpoint = e.retained(session, old).receipt.payload
        checkpoint.externalEffects shouldBe "NOT_EXECUTED"
        R.checkpointAdmitted(checkpoint) shouldBe true
        checkpoint.direction.target.business shouldBe take(R.targetC(description.target)).business
        e.issue(old) shouldBe original.get
      }
      withEpisode(directory) { e =>
        val first = e.advance(session)
        val second = e.advance(session)
        first.kind shouldBe "Request"
        first.issuedWorkContext shouldBe second.issuedWorkContext
        val before = e.current(session)
        val collected = e.recover(direction.get)
        collected.outcome shouldBe "already-recovered"
        collected.externalEffects shouldBe "NOT_EXECUTED"
        collected.successorContinuationId shouldBe first.issuedWorkContext.map(_.continuationId)
        assertSameNativeRecord(e.current(session), before)
        e.issue(old) shouldBe original.get
      }

  }

  private[admission] def qualifyPriorClaimRefusal(): Unit = {
      withEpisode(root()) { e =>
        val (session, old) = e.startMalformed()
        val description = e.describe(session, old)
        val before = e.current(session)
        val original = e.issue(old)
        val target = take(R.targetC(description.target))
        val missing = V.GoalTaskMalformedIssuedWorkRecoveryInput(session, old, description.target, R.choices.head,
          "Source locator alone is not host evidence", "participant", V.WorkflowArtifactReference("source-locator-only", "1"))
        e.call("recoverGoalTaskMalformedIssuedWork", List(Property("input", missing, None))).isSuccess shouldBe false
        assertSameNativeRecord(e.current(session), before)
        val defer = e.provision(description, "Defer", "defer")
        e.recover(defer).outcome shouldBe "deferred"
        assertSameNativeRecord(e.current(session), before)
        val authorized = e.provision(description, R.choices.head, "stale")
        e.project(call => call.withTaskClaimsC { claims => for {
          claimed <- claims.claimC(ContinuationIdentity(old))
          _ <- claims.releaseC(ContinuationIdentity(old), claimed.claimId.get)
        } yield () })
        // Same native frontier and Available state, different exact entity revision.
        assertSameNativeRecord(e.current(session), before)
        e.call("describeGoalTaskMalformedIssuedWorkRecovery", List(Property("input",
          V.GoalTaskMalformedIssuedWorkRecoveryDescriptionInput(session, old), None))).isSuccess shouldBe false
        e.call("recoverGoalTaskMalformedIssuedWork", List(Property("input", authorized, None))).isSuccess shouldBe false
        assertSameNativeRecord(e.current(session), before)
        e.issue(old) shouldBe original
        // A released native claim is execution history, not fresh unclaimed work.
        e.call("recoverGoalTaskMalformedIssuedWork", List(Property("input", authorized, None))).isSuccess shouldBe false
      }

  }

  "Malformed Task recovery admission" should {
    "admit only missing-both requirements, rejecting both partial shapes and valid work" in {
      val legacy = ExecutionRequirement(Vector(CapabilityRequirement("fixture")), RiskLevel("SOURCE_IMPLEMENTATION"), ReasoningLevel.Standard, false)
      R.missingBoth(legacy) shouldBe true
      R.missingBoth(legacy.copy(reasoningClass = Some(ReasoningClassReference("CodingEngineeringStandard")))) shouldBe false
      val disposition = ExecutionDisposition(ExecutionPlacement.Inline, ExecutionIndependence.Optional, "fixture-context")
      R.missingBoth(legacy.copy(disposition = Some(disposition))) shouldBe false
      R.missingBoth(legacy.copy(disposition = Some(disposition), reasoningClass = Some(ReasoningClassReference("CodingEngineeringStandard")))) shouldBe false
    }
    "round-trip the entire original business checkpoint without inventing a successor or changing counts" in {
      val checkpoint = R.Checkpoint(R.Direction(target, R.choices.head, "Fixture explicit unresolved-effects direction",
        target.participant, reference), "UNKNOWN")
      val restored = R.checkpointCodec.decode(R.checkpointCodec.encode(checkpoint)).toOption.get
      restored shouldBe checkpoint
      restored.direction.target.business shouldBe payload
      restored.direction.target.business.state.automaticCount shouldBe 2
      restored.direction.target.business.state.ordinaryCount shouldBe 1
      restored.direction.target.business.supersededWork shouldBe None
      restored.externalEffects shouldBe "UNKNOWN"
      R.decodeTarget(target.asJson.mapObject(_.add("notExecuted", true.asJson))).isLeft shouldBe true
    }
    "bind exact owner, participant, target and genuine-source reference under a dedicated purpose" in {
      val binding = take(R.bindingC(target.componentInstance, target, R.choices.head, "Fixture Human direction", target.participant, reference))
      R.selectBinding(binding) shouldBe Right(binding)
      R.bindingC("foreign-owner", target, R.choices.head, "Fixture direction", target.participant, reference).isSuccess shouldBe false
      R.bindingC(target.componentInstance, target, "NotExecuted", "Fixture direction", target.participant, reference).isSuccess shouldBe false
      R.bindingC(target.componentInstance, target, R.choices.head, "Fixture direction", "foreign-human", reference).isSuccess shouldBe false
      val locatorOnly = Json.obj("componentInstance" -> target.componentInstance.asJson, "source" -> reference.identity.asJson)
      R.selectBinding(locatorOnly).isLeft shouldBe true
      val defer = take(R.bindingC(target.componentInstance, target, "Defer", "Fixture explicit defer", target.participant, reference))
      R.selectBinding(defer) shouldBe Right(defer)
    }
  }
}
