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
  * never represented as installed qualification or genuine user consent.
  * @since Nov.  5, 2026
  * @version Nov.  5, 2026
  */
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
  def exhausted(state: S.State): S.State = {
    val seeded = state.copy(stage = "Inspect", automaticCount = 10, ordinaryCount = 10,
      generation = 11, repairControl = state.repairControl.copy(observations = Vector.empty))
    val intent = take(S.intentC(take(S.candidateC(seeded, seeded.candidate.get))))
    val check = intent.selected.head
    val receipt = RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(1),
      "[error] retained exhausted specification check failure", "", Some(WorkflowSbtValidation.Facts(check.kind,
        WorkflowSbtValidation.Outcome.Failed, None, None)))
    take(S.observedC(intent, receipt))
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

/** Managed same-Task continuation behavior.
  * @since Nov.  5, 2026
  * @version Nov. 23, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class GoalTaskRepairContinuationSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  import GoalTaskRepairContinuationFixture.take
  private val _native = GoalTaskNativeRun
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations ++ Vector(
        GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff),
      goalPaths = Vector("docs/phase.md"))
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    val path = root.resolve("repository/docs/phase.md")
    Files.createDirectories(path.getParent)
    Files.writeString(path, "Specification-owned original source\n")
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] =
    super._setup_commands_(root, scenario).patch(2, Vector(Vector("-C", root.resolve("repository").toString,
      "--literal-pathspecs", "add", "--", "docs/phase.md")), 0)

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
    def completeRepair(session: String, issued: V.GoalTaskView, name: String): Unit = {
      val reference = V.WorkflowArtifactReference("specification-repair-" + name, "1")
      val result = V.GoalTaskWorkResult("completed", "Specification source readiness", Vector.empty,
        NonEmptyVector.one(V.GoalTaskCondition("objective", true, "Fixture source ready; no installed success")),
        Vector.empty, None, None)
      val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("spec-repair-worker"), None,
        Some(V.WorkflowExecutionObservation("INLINE", BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity,
          BoundedWorkExecutionAdmissionFixture.origin.current.contextIdentity, BoundedWorkExecutionAdmissionFixture.profile.identity)))
      value[V.GoalTaskView]("submitGoalTaskWorkResult", List(Property("sessionId", session, None),
        Property("result", result, None), Property("resultReference", reference, None), Property("executionEvidence", execution, None),
        Property("issuedWorkContext", issued.issuedWorkContext.get, None))).kind shouldBe "Submitted"
    }
    // This Git-only managed fixture admits no SBT execution. Ordinary Advance
    // consumes the real submitted Repair and captures its candidate, then stops
    // at confirmed validation nonexecution. The selected terminal failure below
    // is explicitly specification-owned evidence, using the normal domain rule.
    def failedQualification(session: String): Unit = {
      advance(session).kind shouldBe "Wait"
      save(session, payload => {
        payload.state.stage shouldBe "AdmissionBlocked"
        val intent = take(S.intentC(payload.state.copy(stage = "Validate")))
        val check = intent.selected(intent.receipts.size)
        val receipt = RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(1),
          "[error] retained specification check failure", "", Some(WorkflowSbtValidation.Facts(check.kind,
            WorkflowSbtValidation.Outcome.Failed, None, None)))
        payload.copy(state = take(S.observedC(intent, receipt)))
      })
      advance(session).kind shouldBe "Human"
    }
    // Seed the already exhausted budget as explicit specification-owned facts,
    // then obtain the exact exhaustion Human reason from the real observation
    // rule. Merely changing a trend stop's counter is deliberately insufficient.
    def exhaustedQualification(session: String): Unit = save(session, payload =>
      payload.copy(state = GoalTaskRepairContinuationFixture.exhausted(payload.state)))
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
        "documentation", "off", 10, "direct-instruction", Vector.empty, None)
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
    "admit one exact scoped exhaustion direction and charge only its native Repair completion across reopen" in {
      val directory = _root()
      var session = ""
      var answer = Option.empty[V.GoalTaskRepairContinuationInput]
      var ordinary = Option.empty[V.GoalTaskRepairContinuationInput]
      var original = Option.empty[R.Target]
      var issued = Option.empty[V.GoalTaskView]
      var sourceRevision = 0L
      Given("a fixture-managed exhausted terminal qualification with its prior ordinary direction retained")
      _episode(directory) { episode =>
        session = episode.start()
        ordinary = Some(episode.provision(episode.describe(session), "ContinueRepair", "before-exception"))
        episode.resume(ordinary.get).outcome shouldBe "resumed-awaiting-advance"
        episode.completeRepair(session, episode.advance(session), "before-exception")
        episode.failedQualification(session)
        episode.exhaustedQualification(session)
        val before = episode.current(session)
        val description = episode.describe(session)
        val target = take(R.targetC(description.target))
        val source = episode.source()
        description.choices shouldBe R.choices
        description.message should include ("one scoped additional repair batch")
        R.eligible(target.business) shouldBe false
        R.eligibleScoped(target.business) shouldBe true
        target.business.state.automaticCount shouldBe 10
        target.business.state.ordinaryCount shouldBe 10
        target.business.state.repairControl.stop shouldBe None
        target.business.repairContinuation.get.choice shouldBe "ContinueRepair"
        val missing = V.GoalTaskRepairContinuationInput(session, description.target, "ContinueRepair",
          "Missing exact separate Human evidence", "participant", V.WorkflowArtifactReference("unadmitted-scoped", "1"))
        When("the exhausted target lacks its own exact Human evidence or attempts to reuse the earlier trend direction")
        episode.call(R.resumeOperation, List(Property("input", missing, None))).isSuccess shouldBe false
        episode.call(R.resumeOperation, List(Property("input", ordinary.get.copy(target = description.target), None))).isSuccess shouldBe false
        val rejectedChoice = R.bindingC(target.componentInstance, target, "ContinueScopedRepair", "Unsupported public choice",
          target.participant, ContextReference("fixture-human", "1"))
        rejectedChoice.isSuccess shouldBe false
        val deferred = episode.provision(description, "Defer", "scoped-defer")
        episode.resume(deferred).outcome shouldBe "deferred"
        _json(episode.current(session)) shouldBe _json(before)
        val stale = episode.provision(description, "ContinueRepair", "stale-scoped")
        episode.save(session, identity)
        val moved = episode.current(session)
        episode.call(R.resumeOperation, List(Property("input", stale, None))).isSuccess shouldBe false
        _json(episode.current(session)) shouldBe _json(moved)
        val fresh = episode.describe(session)
        original = Some(take(R.targetC(fresh.target)))
        answer = Some(episode.provision(fresh, "ContinueRepair", "one-scoped"))
        NativeReviewPreparation.taskRepairContinuationC(fresh.copy(choices = Vector("ContinueScopedRepair", "Defer")), answer.get,
          answer.get.reference, V.WorkflowExecutionEvidence(Vector(answer.get.reference), Some("participant"), None, None),
          "participant", "normal-development-host", java.time.Instant.parse("2026-11-17T00:00:00Z")).isSuccess shouldBe false
        val crossed = Vector(
          original.get.copy(componentInstance = "foreign-owner"),
          original.get.copy(sessionId = "foreign-session"),
          original.get.copy(business = original.get.business.copy(root = "/foreign-source")),
          original.get.copy(business = original.get.business.copy(branch = "foreign-branch")),
          original.get.copy(business = original.get.business.copy(state = original.get.business.state.copy(
            input = original.get.business.state.input.copy(objective = "Foreign objective")))),
          original.get.copy(business = original.get.business.copy(state = original.get.business.state.copy(
            candidate = original.get.business.state.candidate.map(_.copy(tree = "c" * 40))))))
        crossed.foreach(target => episode.call(R.resumeOperation, List(Property("input",
          answer.get.copy(target = target.asJson.noSpaces), None))).isSuccess shouldBe false)
        Then("crossed and stale directions preserve all native and physical source facts")
        _json(episode.current(session)) shouldBe _json(moved)
        episode.source() shouldBe source
        When("the exact separate scoped direction is adopted")
        episode.resume(answer.get).outcome shouldBe "resumed-awaiting-advance"
        val resumed = episode.current(session)
        val payload = episode.payload(session, resumed.revision.value)
        resumed.history.dropRight(1) shouldBe moved.history
        resumed.revision shouldBe moved.revision.next
        resumed.suspension shouldBe None
        payload shouldBe original.get.business.copy(state = original.get.business.state.copy(stage = "Repair"),
          repairContinuation = Some(R.Direction(original.get.asJson, answer.get.choice, answer.get.rationale,
            answer.get.participant, ContextReference(answer.get.reference.identity, answer.get.reference.revision))))
        payload.state.automaticCount shouldBe 10
        payload.state.ordinaryCount shouldBe 10
        payload.state.repairControl.effectiveLimit shouldBe 10
        episode.resume(answer.get).outcome shouldBe "already-resumed"
        episode.resume(ordinary.get).outcome shouldBe "already-resumed"
        _json(episode.current(session)) shouldBe _json(resumed)
        issued = Some(episode.advance(session))
        issued.get.kind shouldBe "Request"
        sourceRevision = episode.current(session).revision.value - 1L
      }
      When("the same native Session is physically reopened and public Submit retains its actual Repair completion")
      _episode(directory) { episode =>
        val pending = episode.current(session)
        episode.resume(answer.get).outcome shouldBe "already-resumed"
        episode.advance(session).issuedWorkContext shouldBe issued.get.issuedWorkContext
        _json(episode.current(session)) shouldBe _json(pending)
        episode.completeRepair(session, issued.get, "one-scoped")
        val submitted = episode.current(session)
        episode.project(call => for {
          storage <- call.taskStorageC()
          instances <- storage.bindInstancesC()(using call.executionContext)
          canonical <- instances.loadC(InstanceIdentity(session))(using call.executionContext)
          _ = _json(canonical.get) shouldBe _json(submitted)
          payload <- _native.loadPayloadC(storage, session, sourceRevision, call.core)
          work = S.Work(session, sourceRevision.toString, "Repair", payload.state)
          changedCause = CausationReference("foreign-submitted-frontier")
          changedIndex = submitted.history.size - 2
          changedHistory = submitted.history.updated(changedIndex,
            submitted.history(changedIndex).copy(causation = changedCause))
          crossed = submitted.copy(history = changedHistory)
          _ = crossed.validateC.isSuccess shouldBe true
          _ = R.completionAllowanceC(storage, crossed, call.core, work).isSuccess shouldBe false
          allowance <- R.completionAllowanceC(storage, submitted, call.core, work)
          _ = allowance.nonEmpty shouldBe true
          result = S.Result("completed", "Fixture source readiness", Vector.empty,
            work.state.input.conditions.map(id => S.Condition(id, true, "Fixture ready")), Vector.empty)
          _ = S.completedC(work, result, ContextReference("fixture-result", "1")).isSuccess shouldBe false
          _ = Vector(work.copy(session = "foreign-session"), work.copy(sourceRevision = (sourceRevision + 1L).toString),
            work.copy(kind = "Review"), work.copy(state = work.state.copy(generation = work.state.generation + 1L)),
            work.copy(state = work.state.copy(automaticCount = 9)),
            work.copy(state = work.state.copy(input = work.state.input.copy(objective = "Foreign objective"))),
            work.copy(state = work.state.copy(candidate = work.state.candidate.map(_.copy(tree = "c" * 40)))))
            .foreach { changed =>
              allowance.get.limitForC(changed).isSuccess shouldBe false
              S.completedC(changed, result, ContextReference("fixture-result", "1"), allowance).isSuccess shouldBe false
            }
        } yield ())
        episode.resume(answer.get).outcome shouldBe "already-resumed"
        _json(episode.current(session)) shouldBe _json(submitted)
        Then("only ordinary native Advance consumes the one additional batch and preserves the frozen policy and ancestry")
        episode.advance(session).kind shouldBe "Wait"
        val after = episode.current(session)
        val payload = episode.payload(session, after.revision.value)
        payload.state.automaticCount shouldBe 11
        payload.state.ordinaryCount shouldBe 11
        payload.state.generation shouldBe original.get.business.state.generation + 1L
        payload.state.input shouldBe original.get.business.state.input
        payload.state.repairControl shouldBe original.get.business.state.repairControl
        payload.repairContinuation.get.target shouldBe original.get.asJson
        episode.project(call => for {
          storage <- call.taskStorageC()
          originalPayload <- _native.loadPayloadC(storage, session, sourceRevision, call.core)
          work = S.Work(session, sourceRevision.toString, "Repair", originalPayload.state)
          _ = R.completionAllowanceC(storage, submitted, call.core, work).isSuccess shouldBe false
        } yield ())
        episode.payload(session, original.get.frontierRevision) shouldBe original.get.business
        after.history.take(submitted.history.size) shouldBe submitted.history
        episode.advance(session).kind shouldBe "Wait"
        episode.payload(session, episode.current(session).revision.value).state.automaticCount shouldBe 11
        episode.failedQualification(session)
        val stopped = episode.current(session)
        val exhausted = episode.payload(session, stopped.revision.value)
        exhausted.state.automaticCount shouldBe 11
        R.eligible(exhausted) shouldBe false
        R.eligibleScoped(exhausted) shouldBe false
        episode.call("describeGoalTaskRepairContinuation", List(Property("input",
          V.GoalTaskRepairContinuationDescriptionInput(session), None))).isSuccess shouldBe false
        val second = exhausted.copy(state = exhausted.state.copy(automaticCount = 10))
        R.eligibleScoped(second) shouldBe false
        R.decodeTarget(original.get.copy(frontierRevision = stopped.revision.value, business = second).asJson).isLeft shouldBe true
        val repair = exhausted.state.copy(stage = "Repair")
        val result = S.Result("completed", "No second allowance", Vector.empty,
          repair.input.conditions.map(id => S.Condition(id, true, "Fixture ready")), Vector.empty)
        S.completedC(S.Work(session, stopped.revision.value.toString, "Repair", repair), result,
          ContextReference("second-result", "1")).isSuccess shouldBe false
        Vector(answer.get, ordinary.get).foreach(direction => episode.resume(direction).outcome shouldBe "already-resumed")
        episode.call(R.resumeOperation, List(Property("input", answer.get.copy(rationale = "Changed admitted exception"), None))).isSuccess shouldBe false
        _json(episode.current(session)) shouldBe _json(stopped)
      }
      When("the completed batch and exhausted frontier are reopened again")
      _episode(directory) { episode =>
        val before = episode.current(session)
        episode.resume(answer.get).outcome shouldBe "already-resumed"
        episode.resume(ordinary.get).outcome shouldBe "already-resumed"
        episode.advance(session).kind shouldBe "Human"
        _json(episode.current(session)) shouldBe _json(before)
        episode.payload(session, before.revision.value).state.automaticCount shouldBe 11
      }
    }

    "offer unchanged public choices with scoped eligibility only for the exact exhausted terminal failed Task qualification" in {
      Given("a specification-owned terminal failed qualification at the unchanged native ten-batch limit")
      val base = GoalTaskRepairContinuationFixture.payload
      val payload = base.copy(state = GoalTaskRepairContinuationFixture.exhausted(base.state))
      val target = GoalTaskRepairContinuationFixture.target.copy(business = payload)
      When("the target is described and all other Human reasons, budgets and incomplete effects are considered")
      R.decodeTarget(target.asJson) shouldBe Right(target)
      R.choicesFor(target) shouldBe R.choices
      R.eligible(payload) shouldBe false
      R.eligibleScoped(payload) shouldBe true
      Vector(
        payload.copy(state = payload.state.copy(message = "Different Human reason")),
        payload.copy(state = payload.state.copy(automaticCount = 9)),
        payload.copy(state = payload.state.copy(automaticCount = 11)),
        payload.copy(state = payload.state.copy(input = payload.state.input.copy(repairLimit = 9))),
        payload.copy(state = payload.state.copy(repairControl = payload.state.repairControl.copy(selectedLimit = 9))),
        payload.copy(state = payload.state.copy(generation = Long.MaxValue)),
        payload.copy(state = payload.state.copy(pendingCheck = Some("compile"))),
        payload.copy(state = payload.state.copy(receipts = Vector.empty)),
        payload.copy(state = payload.state.copy(receipts = payload.state.receipts.map(_.copy(termination = "unknown")))),
        payload.copy(state = payload.state.copy(receipts = payload.state.receipts.map(_.copy(exitCode = Some(0))))),
        payload.copy(state = payload.state.copy(input = payload.state.input.copy(task = None))),
        payload.copy(state = payload.state.copy(input = payload.state.input.copy(kind = "validation"))),
        payload.copy(state = payload.state.copy(commit = Some(S.Commit("c" * 40, "a" * 40, "b" * 40)))),
        payload.copy(supersededWork = Some("pending-work")))
        .foreach(value => R.eligibleScoped(value) shouldBe false)
      Then("counter-only changes to an ordinary trend stop and unsupported public choices still refuse")
      R.eligibleScoped(base.copy(state = base.state.copy(automaticCount = 10))) shouldBe false
      R.bindingC(GoalTaskRepairContinuationFixture.target.componentInstance, GoalTaskRepairContinuationFixture.target,
        "ContinueScopedRepair", "Unsupported public choice at ordinary trend stop", base.state.participant,
        ContextReference("fixture-human", "1")).isSuccess shouldBe false
      R.choicesFor(GoalTaskRepairContinuationFixture.target) shouldBe R.choices
    }

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
        episode.call("describeGoalTaskRepairContinuation", List(Property("input",
          V.GoalTaskRepairContinuationDescriptionInput(session), None))).isSuccess shouldBe false
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
        episode.call("describeGoalTaskRepairContinuation", List(Property("input",
          V.GoalTaskRepairContinuationDescriptionInput(session), None))).isSuccess shouldBe false
        episode.advance(session).kind shouldBe "Human"
        _json(episode.current(session)) shouldBe _json(before)
      }
    }

    "describe each later Human stop and retain both exact directions through normal Repair progress and reopen" in {
      val directory = _root()
      var session = ""
      var first = Option.empty[V.GoalTaskRepairContinuationInput]
      var second = Option.empty[V.GoalTaskRepairContinuationInput]
      Given("a managed first episode with independently provisioned specification Human evidence")
      _episode(directory) { episode =>
        session = episode.start()
        val firstdescription = episode.describe(session)
        first = Some(episode.provision(firstdescription, "ContinueRepair", "episode-one"))
        episode.resume(first.get).outcome shouldBe "resumed-awaiting-advance"
        val firstpayload = episode.payload(session, episode.current(session).revision.value)
        val issued = episode.advance(session)
        When("ordinary Repair is submitted and its next selected qualification fails with specification-owned terminal evidence")
        episode.completeRepair(session, issued, "episode-one")
        episode.resume(first.get).outcome shouldBe "already-resumed"
        episode.failedQualification(session)
        val before = episode.current(session)
        val description = episode.describe(session)
        val target = take(R.targetC(description.target))
        Then("description selects the current eligible stop rather than collecting the first episode")
        description.outcome shouldBe "decision-required"
        description.target should not be firstdescription.target
        target.frontierRevision shouldBe before.revision.value
        target.frontier shouldBe _json(before)
        target.business.state.automaticCount shouldBe 6
        target.business.state.ordinaryCount shouldBe 6
        target.business.state.generation shouldBe firstpayload.state.generation + 1
        target.business.repairContinuation shouldBe firstpayload.repairContinuation
        target.business.implementationWorkers.get should contain allElementsOf firstpayload.implementationWorkers.get
        target.business.state.input shouldBe firstpayload.state.input
        target.business.state.selected shouldBe firstpayload.state.selected
        target.business.state.candidate.get.head shouldBe firstpayload.state.candidate.get.head
        target.business.state.candidate.get.tree shouldBe firstpayload.state.candidate.get.tree
        target.business.state.candidate.get.paths shouldBe firstpayload.state.candidate.get.paths
        target.business.state.repairControl.observations.dropRight(1) shouldBe firstpayload.state.repairControl.observations.tail
        target.business.state.repairControl.selectedLimit shouldBe firstpayload.state.repairControl.selectedLimit
        target.business.state.repairControl.policy shouldBe firstpayload.state.repairControl.policy
        val absent = first.get.copy(target = description.target)
        episode.call(R.resumeOperation, List(Property("input", absent, None))).isSuccess shouldBe false
        val deferred = episode.provision(description, "Defer", "episode-two-defer")
        episode.resume(deferred).outcome shouldBe "deferred"
        _json(episode.current(session)) shouldBe _json(before)
        second = Some(episode.provision(description, "ContinueRepair", "episode-two"))
        When("a separate exact Human direction resumes the later stop")
        episode.resume(second.get).outcome shouldBe "resumed-awaiting-advance"
        val after = episode.current(session)
        val payload = episode.payload(session, after.revision.value)
        Then("exactly one append changes only stage and latest direction, embedding the earlier direction intact")
        after.revision shouldBe before.revision.next
        after.history.dropRight(1) shouldBe before.history
        payload.copy(state = payload.state.copy(stage = "Human"), repairContinuation = target.business.repairContinuation) shouldBe target.business
        payload.repairContinuation.get.target shouldBe target.asJson
        Vector(first.get, second.get).foreach { answer =>
          episode.resume(answer).outcome shouldBe "already-resumed"
          Vector(answer.copy(rationale = "Changed Human rationale"), answer.copy(choice = "Defer"),
            answer.copy(reference = V.WorkflowArtifactReference("changed-reference", "1"))).foreach { changed =>
            episode.call(R.resumeOperation, List(Property("input", changed, None))).isSuccess shouldBe false
          }
        }
        episode.describe(session).target shouldBe description.target
        _json(episode.current(session)) shouldBe _json(after)
        When("ordinary Advance issues another Repair and public Submit retains its completion")
        val next = episode.advance(session)
        Vector(first.get, second.get).foreach(answer => episode.resume(answer).outcome shouldBe "already-resumed")
        episode.completeRepair(session, next, "episode-two")
        val submitted = episode.current(session)
        Vector(first.get, second.get).foreach(answer => episode.resume(answer).outcome shouldBe "already-resumed")
        _json(episode.current(session)) shouldBe _json(submitted)
        episode.failedQualification(session)
        val later = episode.current(session)
        val unresumed = episode.provision(episode.describe(session), "ContinueRepair", "stale-unresumed")
        episode.save(session, identity)
        val moved = episode.current(session)
        episode.describe(session).outcome shouldBe "decision-required"
        episode.call(R.resumeOperation, List(Property("input", unresumed, None))).isSuccess shouldBe false
        _json(episode.current(session)) shouldBe _json(moved)
        episode.save(session, payload => payload.copy(state = payload.state.copy(automaticCount = 10)))
        val exhausted = episode.current(session)
        episode.call("describeGoalTaskRepairContinuation", List(Property("input", V.GoalTaskRepairContinuationDescriptionInput(session), None))).isSuccess shouldBe false
        episode.call(R.resumeOperation, List(Property("input", unresumed, None))).isSuccess shouldBe false
        Vector(first.get, second.get).foreach(answer => episode.resume(answer).outcome shouldBe "already-resumed")
        _json(episode.current(session)) shouldBe _json(exhausted)
        exhausted.history.take(later.history.size) shouldBe later.history
      }
      When("the same database is physically reopened after later progress")
      _episode(directory) { episode =>
        val before = episode.current(session)
        Vector(first.get, second.get).foreach(answer => episode.resume(answer).outcome shouldBe "already-resumed")
        _json(episode.current(session)) shouldBe _json(before)
        Then("a later ineligible Human cannot be hidden by either old direction")
        episode.save(session, payload => payload.copy(state = payload.state.copy(automaticCount = 7, message = "Different Human reason")))
        val ineligible = episode.current(session)
        episode.call("describeGoalTaskRepairContinuation", List(Property("input", V.GoalTaskRepairContinuationDescriptionInput(session), None))).isSuccess shouldBe false
        _json(episode.current(session)) shouldBe _json(ineligible)
      }
    }

    "refuse malformed retained ancestry and terminal collection without appending" in {
      Given("native retained directions whose original target is malformed, noncanonical or crossed")
      Vector("malformed", "noncanonical", "owner", "session", "participant", "input", "revision", "direction", "terminal", "forced").foreach { mode =>
        _episode(_root()) { episode =>
          val session = episode.start()
          val answer = episode.provision(episode.describe(session), "ContinueRepair", "invalid-" + mode)
          episode.resume(answer).outcome shouldBe "resumed-awaiting-advance"
          episode.save(session, payload => {
            val direction = payload.repairContinuation.get
            val target = take(R.targetC(answer.target))
            val changed = mode match {
              case "malformed" => direction.copy(target = Json.arr())
              case "noncanonical" => direction.copy(target = target.asJson.mapObject(_.add("unsupported", true.asJson)))
              case "owner" => direction.copy(target = target.copy(componentInstance = "foreign-component").asJson)
              case "session" => direction.copy(target = target.copy(sessionId = "foreign-session").asJson)
              case "participant" => direction.copy(participant = "foreign-human")
              case "input" => direction.copy(target = target.copy(business = target.business.copy(state = target.business.state.copy(
                input = target.business.state.input.copy(objective = "Foreign objective")))).asJson)
              case "revision" => direction.copy(target = target.copy(frontierRevision = Long.MaxValue).asJson)
              case "direction" => direction.copy(choice = "Defer")
              case _ => direction
            }
            payload.copy(state = if (mode == "terminal") payload.state.copy(stage = "NoChange") else payload.state,
              repairContinuation = Some(changed))
          })
          if (mode == "forced") episode.project(call => for {
            storage <- call.taskStorageC()
            instances <- storage.bindInstancesC()(using call.executionContext)
            current <- instances.loadC(InstanceIdentity(session))(using call.executionContext)
            _ <- instances.appendC(current.get.identity, current.get.revision, _native.history(current.get,
              WorkflowForceClosure.cause(ContextReference("fixture-retained-forced-close", "1")), accepted = true))(using call.executionContext)
          } yield ())
          val before = episode.current(session)
          When("public description or original-direction collection loads the invalid or terminal boundary: " + mode)
          episode.call("describeGoalTaskRepairContinuation", List(Property("input", V.GoalTaskRepairContinuationDescriptionInput(session), None))).isSuccess shouldBe false
          episode.call(R.resumeOperation, List(Property("input", answer, None))).isSuccess shouldBe false
          Then("neither call appends or restores any old checkpoint")
          _json(episode.current(session)) shouldBe _json(before)
        }
      }
    }

    "bound retained opaque ancestry by the existing native allowance and decreasing frontier revisions" in {
      Given("eligible specification stops linked only by their original opaque targets")
      val original = GoalTaskRepairContinuationFixture.target
      def _linked_(previous: R.Target, revision: Long): R.Target = original.copy(frontierRevision = revision,
        business = original.business.copy(repairContinuation = Some(R.Direction(previous.asJson, "ContinueRepair",
          "Specification earlier admitted direction", original.participant, ContextReference("fixture-chain-" + revision, "1")))))
      val limit = original.business.state.repairControl.effectiveLimit
      val property = org.scalacheck.Prop.forAll(org.scalacheck.Gen.choose(1, limit)) { size =>
        val target = (1 until size).foldLeft(original)((previous, ordinal) => _linked_(previous, original.frontierRevision + ordinal))
        R.decodeTarget(target.asJson) == Right(target)
      }
      When("the compatible decoder loads every permitted chain length")
      val checked = org.scalacheck.Test.check(org.scalacheck.Test.Parameters.default.withMinSuccessfulTests(50), property)
      Then("valid ancestry is retained exactly and nondecreasing or over-bound ancestry refuses")
      checked.passed shouldBe true
      R.decodeTarget(_linked_(original, original.frontierRevision).asJson).isLeft shouldBe true
      val excessive = (1 to limit).foldLeft(original)((previous, ordinal) => _linked_(previous, original.frontierRevision + ordinal))
      R.decodeTarget(excessive.asJson).isLeft shouldBe true
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
