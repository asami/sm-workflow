package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.Component
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.processexecution.GitOperationKind
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as _, *}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.{NativeGoalTaskStartInput, NativeReviewPreparation}
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V
import BoundedWorkRuntime.given
import StandaloneFixExecution as S
import StandaloneFixExecution.given
import GoalTaskRepairContinuation as R

/** Test-owned terminal check facts exercise the real domain stop rule. They are
  * never represented as installed qualification or genuine user consent. */
private[smworkflow] object GoalTaskRepairContinuationFixture {
  def take[A](result: Consequence[A]): A = result.toOption.get
  def stopped(initial: S.State, candidate: S.Candidate): S.State = {
    val completed = S.Result("completed", "Specification repair source ready", Vector.empty,
      initial.input.conditions.map(identity => S.Condition(identity, true, "Specification source readiness")), Vector.empty)
    val implemented = take(S.completedC(S.Work("fixture", "1", "Implement", initial), completed, ContextReference("fixture-implementation", "1")))
      .copy(automaticCount = 2, ordinaryCount = 2, generation = 3)
    (1 to 4).foldLeft(implemented) { (state, ordinal) =>
      val captured = take(S.candidateC(state, candidate.copy(diff = "fixture-generation-" + state.generation)))
      val check = captured.selected.head
      val receipt = RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(1),
        "[error] retained specification check failure", "", Some(WorkflowSbtValidation.Facts(check.kind,
          WorkflowSbtValidation.Outcome.Failed, None, None)))
      val observed = take(S.observedC(take(S.intentC(captured)), receipt))
      if (ordinal == 4) observed else take(S.completedC(S.Work("fixture", ordinal.toString, "Repair", observed),
        completed, ContextReference("fixture-repair-" + ordinal, "1")))
    }
  }
  val input = S.Input("fixture-repository", "fixture-trend", "Preserve this Task", Vector("src"), Vector("compile"),
    Vector("objective"), Vector(ContextReference("fixture-source", "1")), false, scalaLint = "off",
    task = Some(S.Task("direct-instruction", Vector.empty)))
  val state = stopped(take(S.startC(input, "fixture-human", Vector(GoalPhaseNativeValidation.Check("compile", 1,
    WorkflowSbtValidation.Kind.Compile)))), S.Candidate("a" * 40, "b" * 40, Vector("src/Example.scala"), "fixture"))
  val payload = BoundedWorkRuntime.Payload(state, "/fixture", "main", "a" * 40,
    implementationWorker = Some("fixture-worker"), unrelated = Some(Vector.empty), implementationWorkers = Some(Vector("fixture-worker")))
  val target = R.Target("fixture-component", "fixture-session", state.participant, 8L, Json.obj("nativeFixture" -> true.asJson), payload)
}

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalTaskRepairContinuationSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  import GoalTaskRepairContinuationFixture.take
  private val _native = GoalTaskNativeRun
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations :+ GitOperationKind.IndexTree)

  private final class Episode(root: Path, owner: Subsystem, component: Component, actor: ExecutionContext,
    val project: (Setup => Consequence[Unit]) => Unit) {
    def call(operation: String, properties: List[Property]): Consequence[OperationResponse] =
      owner.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalTaskService",
        operation = operation, properties = properties), actor)
    def value[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
      read_value[A](take(call(operation, properties)))
    def describe(session: String): V.GoalTaskRepairContinuationDescriptionResult = value("describeGoalTaskRepairContinuation",
      List(Property("input", V.GoalTaskRepairContinuationDescriptionInput(session), None)))
    def resume(input: V.GoalTaskRepairContinuationInput): V.GoalTaskRepairContinuationDescriptionResult =
      value(R.resumeOperation, List(Property("input", input, None)))
    def advance(session: String): V.GoalTaskView = value("advanceGoalTask", List(Property("sessionId", session, None)))
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
    def payload(session: String, revision: Long): BoundedWorkRuntime.Payload = {
      var result = Option.empty[BoundedWorkRuntime.Payload]
      project(call => for {
        storage <- call.taskStorageC()
        payload <- _native.loadPayloadC(storage, session, revision, call.core)
        _ = result = Some(payload)
      } yield ())
      result.get
    }
    def source(): (String, RepositorySyncManagedGitObservation.Observed, String) = {
      var result = Option.empty[(String, RepositorySyncManagedGitObservation.Observed, String)]
      project(call => for {
        root <- RepositorySyncManagedGitObservation.rootC(call.core)
        observed <- RepositorySyncManagedGitObservation.observeC(call.core)
        tree <- RepositorySyncManagedGitObservation.indexTreeC(call.core)
        _ = result = Some((root, observed, tree))
      } yield ())
      result.get
    }
    def save(session: String, transform: BoundedWorkRuntime.Payload => BoundedWorkRuntime.Payload): Unit = project(call => for {
      storage <- call.taskStorageC()
      instances <- storage.bindInstancesC()(using call.executionContext)
      current <- instances.loadC(InstanceIdentity(session))(using call.executionContext)
      payload <- _native.loadPayloadC(storage, session, current.get.revision.value, call.core)
      host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
        "sm-workflow", payload.root, payload.branch, payload.state.input.ownedPaths, Vector.empty, None, None, None, None, None)
      _ <- _native.saveC(host, storage, current.get, transform(payload), call.core)
    } yield ())
    def provision(description: V.GoalTaskRepairContinuationDescriptionResult, choice: String, name: String): V.GoalTaskRepairContinuationInput = {
      val reference = V.WorkflowArtifactReference("specification-human-" + name, "1")
      val answer = V.GoalTaskRepairContinuationInput(description.sessionId, description.target, choice,
        "Specification exact Human direction; no live consent claimed", "participant", reference)
      project(call => for {
        prepared <- NativeReviewPreparation.taskRepairContinuationC(description, answer, reference,
          V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None), "participant", "normal-development-host",
          call.executionContext.clock.instant().minusSeconds(1))
        _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
      } yield ())
      answer
    }
    def start(stop: Boolean = true): String = {
      val input = V.GoalTaskInput("sm-workflow", "task-trend-spec", "Continue the same Task after actual trend assessment",
        NonEmptyVector.one("docs/phase.md"), NonEmptyVector.one("test"), NonEmptyVector.one("objective"),
        NonEmptyVector.one(V.WorkflowArtifactReference("specification-direct-request", "1")), false,
        "documentation", "off", 10, "direct-instruction", Vector.empty)
      val prepared = take(NativeGoalTaskStartInput.prepareC(Map("input" -> "input", "selection-id" -> "trend-selection",
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "trend-start",
        "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
      project(call => call.setupC(_setup_commands_(root, "TaskTrend").size).flatMap { _ =>
        val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        call.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
      })
      val started = value[V.GoalTaskView]("startGoalTask", List(Property("input", input, None),
        Property("selectionId", "trend-selection", None), Property("participant", "participant", None), Property("idempotencyKey", "trend-start", None)))
      if (stop) {
        var candidate = Option.empty[S.Candidate]
        project(call => for {
          observed <- RepositorySyncManagedGitObservation.observeC(call.core)
          tree <- RepositorySyncManagedGitObservation.indexTreeC(call.core)
          _ = candidate = Some(S.Candidate(observed.head, tree, Vector.empty, ""))
        } yield ())
        save(started.sessionId, payload => payload.copy(state = GoalTaskRepairContinuationFixture.stopped(payload.state, candidate.get),
          implementationWorker = Some("spec-implementer"), implementationWorkers = Some(Vector("spec-implementer", "spec-repairer"))))
      }
      started.sessionId
    }
  }
  private def _episode(root: Path, hostparameters: Map[String, String] = Map.empty)(body: Episode => Unit): Unit =
    _with_runtime_(root, "TaskTrend", hostParameters = BoundedWorkExecutionAdmissionFixture.parameters ++ hostparameters) {
      (owner, component, actor, project) => body(new Episode(root, owner, component, actor, project))
    }
  private def _root(): Path = Files.createTempDirectory(Files.createDirectories(
    Path.of("target/sm-workflow/work/task-trend-managed").toAbsolutePath), "episode-").toRealPath()
  private def _json(record: InstanceRecord): Json = io.circe.parser.parse(take(WorkflowInstanceRecordJsonV1.encodeC(record))).toOption.get

  "Same Task repair-trend continuation" should {
    "append only Repair, preserve the stop and collect the same direction after ordinary Advance and physical reopen" in {
      val directory = _root()
      var session = ""
      var direction = Option.empty[V.GoalTaskRepairContinuationInput]
      var original = Option.empty[R.Target]
      var issued = Option.empty[V.GoalTaskView]
      Given("a managed Task whose four test-owned failed observations reached the normal trend Human stop")
      _episode(directory) { episode =>
        session = episode.start()
        val before = episode.current(session)
        val description = episode.describe(session)
        val target = take(R.targetC(description.target))
        original = Some(target)
        target.business.state.stage shouldBe "Human"
        target.business.state.automaticCount shouldBe 5
        target.business.state.ordinaryCount shouldBe 5
        R.eligible(target.business) shouldBe true
        _json(episode.current(session)) shouldBe _json(before)
        val missing = V.GoalTaskRepairContinuationInput(session, description.target, "ContinueRepair", "Locator is not consent",
          "participant", V.WorkflowArtifactReference("unadmitted-source", "1"))
        episode.call(R.resumeOperation, List(Property("input", missing, None))).isSuccess shouldBe false
        val defer = episode.provision(description, "Defer", "defer")
        episode.resume(defer).outcome shouldBe "deferred"
        _json(episode.current(session)) shouldBe _json(before)
        direction = Some(episode.provision(description, "ContinueRepair", "continue"))
        When("the public resume consumes separately provisioned exact Human evidence")
        episode.resume(direction.get).outcome shouldBe "resumed-awaiting-advance"
        val after = episode.current(session)
        val payload = episode.payload(session, after.revision.value)
        Then("one native checkpoint retains every original fact, and no work is issued")
        after.history.dropRight(1) shouldBe before.history
        after.revision shouldBe before.revision.next
        after.suspension shouldBe None
        payload.copy(state = payload.state.copy(stage = "Human"), repairContinuation = None) shouldBe target.business
        payload.repairContinuation.get.target shouldBe target.asJson
        payload.state.message shouldBe target.business.state.message
        payload.state.input.reviewRequired shouldBe target.business.state.input.reviewRequired
        // Once admitted, the native direction owns collection even if the host
        // subsequently retires its original external observation row.
        episode.project(call => call.withTaskClaimsC { _ =>
          given org.goldenport.cncf.context.ExecutionContext = call.executionContext
          val collection = _collection_("host_evidence")
          call.executionContext.dataStoreSpace.dataStore(collection).flatMap(_.delete(collection,
            org.goldenport.cncf.datastore.DataStore.StringEntryId(NativeExternalEvidence.recordIdentity(
              NativeExternalEvidence.Purpose.GoalTaskRepairContinuation,
              ThreeWorkflowExternalEvidencePorts.key(direction.get.reference.identity, direction.get.reference.revision)))))
        })
        episode.resume(direction.get).outcome shouldBe "already-resumed"
        episode.describe(session).target shouldBe description.target
        _json(episode.current(session)) shouldBe _json(after)
        episode.call(R.resumeOperation, List(Property("input", direction.get.copy(rationale = "Different direction"), None))).isSuccess shouldBe false
        When("a separate ordinary Advance issues the actual Repair")
        issued = Some(episode.advance(session))
        Then("the issued work preserves checks, allowance, worker exclusions and the same Task")
        issued.get.kind shouldBe "Request"
        issued.get.issuedWorkContext.get.requiredOperation shouldBe "goal-task-repair-capability"
        val pending = episode.current(session)
        val retained = episode.payload(session, pending.revision.value - 1)
        retained shouldBe payload
        retained.implementationWorkers shouldBe Some(Vector("spec-implementer", "spec-repairer"))
        episode.resume(direction.get).outcome shouldBe "already-resumed"
        _json(episode.current(session)) shouldBe _json(pending)
      }
      When("a fresh runtime reopens the same native database")
      _episode(directory) { episode =>
        val before = episode.current(session)
        episode.resume(direction.get).outcome shouldBe "already-resumed"
        episode.describe(session).target shouldBe original.get.asJson.noSpaces
        episode.advance(session).issuedWorkContext shouldBe issued.get.issuedWorkContext
        _json(episode.current(session)) shouldBe _json(before)
        When("the actual Repair returns a decision-needed result through public Submit and ordinary Advance")
        val reference = V.WorkflowArtifactReference("specification-repair-decision", "1")
        val result = V.GoalTaskWorkResult("decision-needed", "Fixture business judgment remains open", Vector.empty,
          NonEmptyVector.one(V.GoalTaskCondition("objective", false, "Fixture does not claim Task acceptance")),
          Vector("Specification Human judgment remains required"), None, None)
        val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("spec-repair-worker"), None,
          Some(V.WorkflowExecutionObservation("INLINE", BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
            BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity, BoundedWorkExecutionAdmissionFixture.profile.identity)))
        episode.value[V.GoalTaskView]("submitGoalTaskWorkResult", List(Property("sessionId", session, None),
          Property("result", result, None), Property("resultReference", reference, None), Property("executionEvidence", execution, None),
          Property("issuedWorkContext", issued.get.issuedWorkContext.get, None))).kind shouldBe "Submitted"
        episode.resume(direction.get).outcome shouldBe "already-resumed"
        episode.advance(session).kind shouldBe "Human"
        val progressed = episode.current(session)
        episode.resume(direction.get).outcome shouldBe "already-resumed"
        episode.describe(session).target shouldBe original.get.asJson.noSpaces
        _json(episode.current(session)) shouldBe _json(progressed)
        Then("normal Repair completion alone charges the existing batch allowance")
        val state = original.get.business.state.copy(stage = "Repair")
        val repairresult = S.Result("completed", "Specification bounded source readiness", Vector.empty,
          state.input.conditions.map(identity => S.Condition(identity, true, "Source ready")), Vector.empty)
        val next = take(S.completedC(S.Work(session, "fixture", "Repair", state), repairresult, ContextReference("fixture-result", "1")))
        next.automaticCount shouldBe 6
        next.ordinaryCount shouldBe 6
        next.repairControl shouldBe state.repairControl
      }
      _episode(directory) { episode =>
        val before = episode.current(session)
        episode.resume(direction.get).outcome shouldBe "already-resumed"
        episode.describe(session).target shouldBe original.get.asJson.noSpaces
        episode.advance(session).kind shouldBe "Human"
        _json(episode.current(session)) shouldBe _json(before)
      }
    }

    "refuse foreign or stale targets, pending work, exhausted allowance and forced closure without changing native history" in {
      Given("the exact managed stopped Task and separately provisioned direction")
      _episode(_root()) { episode =>
        val session = episode.start()
        val description = episode.describe(session)
        val target = take(R.targetC(description.target))
        val answer = episode.provision(description, "ContinueRepair", "negative")
        val before = episode.current(session)
        When("the direction crosses owner, Session, participant or candidate")
        Vector(answer.copy(sessionId = "unknown-session"), answer.copy(participant = "foreign-human"),
          answer.copy(target = "{malformed"), answer.copy(target = target.copy(frontier = Json.arr()).asJson.noSpaces),
          answer.copy(target = target.copy(componentInstance = "foreign-component").asJson.noSpaces),
          answer.copy(target = target.copy(frontierRevision = target.frontierRevision + 1).asJson.noSpaces),
          answer.copy(target = target.copy(business = target.business.copy(state = target.business.state.copy(
            candidate = target.business.state.candidate.map(_.copy(tree = "c" * 40))))).asJson.noSpaces)).foreach { input =>
          episode.call(R.resumeOperation, List(Property("input", input, None))).isSuccess shouldBe false
        }
        Then("all crossed targets leave the frontier untouched")
        _json(episode.current(session)) shouldBe _json(before)
        episode.save(session, payload => payload.copy(state = payload.state.copy(automaticCount = 10)))
        val exhausted = episode.current(session)
        episode.call("describeGoalTaskRepairContinuation", List(Property("input", V.GoalTaskRepairContinuationDescriptionInput(session), None))).isSuccess shouldBe false
        episode.call(R.resumeOperation, List(Property("input", answer, None))).isSuccess shouldBe false
        _json(episode.current(session)) shouldBe _json(exhausted)
        episode.project(call => for {
          storage <- call.taskStorageC()
          instances <- storage.bindInstancesC()(using call.executionContext)
          current <- instances.loadC(InstanceIdentity(session))(using call.executionContext)
          _ <- instances.appendC(current.get.identity, current.get.revision, _native.history(current.get,
            WorkflowForceClosure.cause(ContextReference("fixture-forced-close", "1")), accepted = true))(using call.executionContext)
        } yield ())
        val closed = episode.current(session)
        episode.call(R.resumeOperation, List(Property("input", answer, None))).isSuccess shouldBe false
        _json(episode.current(session)) shouldBe _json(closed)
      }
      Given("another Task with a genuine pending native WorkOrder and no trend stop")
      _episode(_root()) { episode =>
        val session = episode.start(false)
        episode.advance(session).kind shouldBe "Request"
        val before = episode.current(session)
        episode.call("describeGoalTaskRepairContinuation", List(Property("input", V.GoalTaskRepairContinuationDescriptionInput(session), None))).isSuccess shouldBe false
        _json(episode.current(session)) shouldBe _json(before)
      }
    }

    "refuse a physically changed scoped candidate without altering the source, checkpoint, receipts or allowance" in {
      Given("an actual managed source candidate and independently admitted continuation for its stopped Task")
      val directory = _root()
      _episode(directory) { episode =>
        val session = episode.start()
        val description = episode.describe(session)
        val answer = episode.provision(description, "ContinueRepair", "physical-source-change")
        val before = episode.current(session)
        val payload = episode.payload(session, before.revision.value)
        val path = directory.resolve("repository/docs/phase.md")
        Files.createDirectories(path.getParent)
        Files.writeString(path, "fixture-owned changed source after exact Human admission\n")
        val source = episode.source()
        When("public resume observes the changed physical working tree before its mechanical save")
        val refused = episode.call(R.resumeOperation, List(Property("input", answer, None)))
        Then("the same Task remains stopped and the read-only observation mutates no Git or source state")
        refused.isSuccess shouldBe false
        _json(episode.current(session)) shouldBe _json(before)
        episode.payload(session, before.revision.value) shouldBe payload
        payload.state.automaticCount shouldBe 5
        payload.state.ordinaryCount shouldBe 5
        episode.source() shouldBe source
        Files.readString(path) shouldBe "fixture-owned changed source after exact Human admission\n"
      }
    }

    "refuse the same admitted target through a changed host root without appending or changing native source facts" in {
      Given("the original active stopped Task and a genuine specification-owned exact Human direction")
      val directory = _root()
      var session = ""
      var answer = Option.empty[V.GoalTaskRepairContinuationInput]
      var before = Option.empty[Json]
      var payload = Option.empty[BoundedWorkRuntime.Payload]
      var source = Option.empty[(String, RepositorySyncManagedGitObservation.Observed, String)]
      _episode(directory) { episode =>
        session = episode.start()
        answer = Some(episode.provision(episode.describe(session), "ContinueRepair", "changed-host-root"))
        val current = episode.current(session)
        before = Some(_json(current))
        payload = Some(episode.payload(session, current.revision.value))
        source = Some(episode.source())
      }
      val originalhost = ThreeWorkflowNormalHostFixture.definition(directory)
      val changedroot = Files.createDirectories(directory.resolve("foreign-repository")).toString
      val changedhost = originalhost.copy(target = originalhost.target.copy(root = changedroot))
      When("the public operation is rebound to a host whose selected root differs from the saved Task source")
      _episode(directory, Map(ThreeWorkflowRuntimeParameters.definition.name -> ThreeWorkflowRuntimeParameters.encode(changedhost).noSpaces)) { episode =>
        val refused = episode.call(R.resumeOperation, List(Property("input", answer.get, None)))
        Then("host crossing refuses before a mechanical append and preserves all counters, history and receipts")
        refused.isSuccess shouldBe false
        val current = episode.current(session)
        _json(current) shouldBe before.get
        episode.payload(session, current.revision.value) shouldBe payload.get
        payload.get.state.automaticCount shouldBe 5
        payload.get.state.ordinaryCount shouldBe 5
        episode.source() shouldBe source.get
      }
    }

    "retain every remaining allowance through opaque continuation encoding without refunding a repair batch" in {
      Given("the full range of remaining native allowances and exact test-owned trend stop")
      val property = org.scalacheck.Prop.forAll(org.scalacheck.Gen.choose(0, 9)) { count =>
        val original = GoalTaskRepairContinuationFixture.payload
        val payload = original.copy(state = original.state.copy(automaticCount = count, ordinaryCount = count))
        val target = GoalTaskRepairContinuationFixture.target.copy(business = payload)
        val direction = R.Direction(target.asJson, "ContinueRepair", "Fixture bounded direction", target.participant,
          ContextReference("fixture-human", "1"))
        val continued = payload.copy(state = payload.state.copy(stage = "Repair"), repairContinuation = Some(direction))
        val restored = continued.asJson.as[BoundedWorkRuntime.Payload].toOption.get
        val result = S.Result("completed", "Source ready", Vector.empty,
          payload.state.input.conditions.map(identity => S.Condition(identity, true, "Fixture source readiness")), Vector.empty)
        val next = take(S.completedC(S.Work(target.sessionId, "fixture", "Repair", restored.state), result,
          ContextReference("fixture-completion", "1")))
        R.eligible(payload) && restored == continued && next.automaticCount == count + 1 &&
          next.ordinaryCount == count + 1 && next.repairControl == original.state.repairControl
      }
      When("each remaining allowance is retained, decoded and completed by the ordinary domain batch owner")
      val checked = org.scalacheck.Test.check(org.scalacheck.Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("continuation preserves counts while one actual semantic completion consumes exactly one existing batch")
      checked.passed shouldBe true
    }

    "decode historical payloads and reject other Human reasons and incomplete check effects" in {
      Given("a test-owned trend stop and an old payload without a retained direction field")
      val payload = GoalTaskRepairContinuationFixture.payload
      When("the compatible payload decoder and continuation eligibility boundary load it")
      payload.asJson.hcursor.downField("repairContinuation").focus shouldBe None
      payload.asJson.as[BoundedWorkRuntime.Payload] shouldBe Right(payload)
      Then("only the selected terminal failure with remaining unchanged allowance is eligible")
      R.eligible(payload) shouldBe true
      Vector(payload.copy(state = payload.state.copy(message = "Different Human reason")),
        payload.copy(state = payload.state.copy(pendingCheck = Some("compile"))),
        payload.copy(state = payload.state.copy(automaticCount = 10)),
        payload.copy(state = payload.state.copy(receipts = Vector.empty)),
        payload.copy(state = payload.state.copy(input = payload.state.input.copy(task = None))),
        payload.copy(state = payload.state.copy(receipts = payload.state.receipts.map(_.copy(termination = "unknown")))))
        .foreach(value => R.eligible(value) shouldBe false)
    }
  }
}
