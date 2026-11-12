package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.wordspec.AnyWordSpec
import BoundedWorkRuntime.given
import DelegateTaskRepairContinuation as R

/** The facts here belong to this specification, not any real delegation. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
abstract class DelegateTaskRepairContinuationCodecSpec extends AnyWordSpec with org.scalatest.matchers.should.Matchers {
  private val contract = DelegateTask.Contract("requester", "destination", "requester", "component", "destination",
    "localized", Vector("dispatch", "implement", "validate", "commit", "return", "adopt", "review"), Vector.empty, Vector(ContextReference("authorization", "1")), None, Vector.empty, "fixture")
  private val goal = GoalTaskRepairContinuationFixture.payload
  private val payload = goal.copy(state = goal.state.copy(input = goal.state.input.copy(delegation = Some(contract))))
  private val target = R.Target("component", "session", payload.state.participant, 8L, Json.obj("fixture" -> true.asJson), payload)

  "DelegateTask repair continuation" should {
    "retain exact eligible targets while refusing other ownership and unknown failures" in {
      R.eligible(payload) shouldBe true
      R.decodeTarget(target.asJson) shouldBe Right(target)
      GoalTaskRepairContinuation.decodeTarget(target.asJson).isLeft shouldBe true
      R.decodeTarget(GoalTaskRepairContinuationFixture.target.asJson).isLeft shouldBe true
      R.eligible(goal) shouldBe false
      R.eligible(payload.copy(state = payload.state.copy(input = payload.state.input.copy(
        delegation = Some(contract.copy(role = "requester")))))) shouldBe false
      R.eligible(payload.copy(state = payload.state.copy(automaticCount = 10))) shouldBe false
      R.eligible(payload.copy(state = payload.state.copy(pendingCheck = Some("compile")))) shouldBe false
      R.eligible(payload.copy(state = payload.state.copy(receipts = payload.state.receipts.map(_.copy(termination = "unknown"))))) shouldBe false
    }
    "retain counts and history in a separately encoded Repair checkpoint" in {
      val direction = R.Direction(target.asJson, "ContinueRepair", "Specification direction", target.participant,
        ContextReference("spec-human", "1"))
      val next = payload.copy(state = payload.state.copy(stage = "Repair"), delegateRepairContinuation = Some(direction))
      val restored = next.asJson.as[BoundedWorkRuntime.Payload].toOption.get
      restored shouldBe next
      restored.state.automaticCount shouldBe payload.state.automaticCount
      restored.state.ordinaryCount shouldBe payload.state.ordinaryCount
      restored.state.receipts shouldBe payload.state.receipts
      restored.state.repairControl shouldBe payload.state.repairControl
      restored.state.generation shouldBe payload.state.generation
    }
  }
}

/** Public operations over one specification-owned managed destination Session. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DelegateTaskRepairContinuationOperationSpec extends DelegateTaskRepairContinuationCodecSpec
    with ThreeWorkflowManagedRepositoryFixture {
  import cats.data.NonEmptyVector
  import java.nio.file.{Files, Path}
  import org.goldenport.Consequence
  import org.goldenport.cncf.processexecution.GitOperationKind
  import org.goldenport.cncf.workflow.*
  import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as _, *}
  import org.goldenport.protocol.{Property, Request}
  import org.goldenport.record.Record
  import org.simplemodeling.textus.smworkflow.preparation.NativeDelegateTaskStartInput
  import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
  import org.simplemodeling.textus.smworkflow.value as V
  import StandaloneFixExecution as S
  import S.given
  private def take[A](value: Consequence[A]): A = value.toOption.get
  private def stoppedFixture(initial: S.State, candidate: S.Candidate): S.State = {
    val required = initial.input.submissionConditions.getOrElse(initial.input.conditions).toSet
    val completed = S.Result("completed", "Specification repair source ready", Vector.empty,
      initial.input.conditions.map(identity => S.Condition(identity, required(identity), "Specification source readiness")), Vector.empty)
    val implemented = take(S.completedC(S.Work("fixture", "1", "Implement", initial), completed,
      ContextReference("fixture-implementation", "1")))
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
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations ++ Vector(
        GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff), goalPaths = Vector("docs/phase.md"))
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    val file = root.resolve("repository/docs/phase.md")
    Files.createDirectories(file.getParent)
    Files.writeString(file, "Specification-owned original source\n")
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] =
    super._setup_commands_(root, scenario).patch(2, Vector(Vector("-C", root.resolve("repository").toString,
      "--literal-pathspecs", "add", "--", "docs/phase.md")), 0)

  private[admission] def qualify(installed: Option[(Path, Path)]): Unit = {
    import scala.jdk.CollectionConverters.*
    import java.util.concurrent.TimeUnit
    installed.foreach { case (executable, archive) =>
      require(executable.isAbsolute && Files.isExecutable(executable), "Installed command must exist")
      require(archive.isAbsolute && Files.isRegularFile(archive), "Candidate CAR must exist")
    }
    val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/delegate-repair-managed").toAbsolutePath), "SPEC-").toRealPath()
    val contract = V.DelegateTaskContract("SPEC-requester", "SPEC-destination", "SPEC-requester", "SPEC-component", "destination",
      "documentation", NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"), Vector.empty,
      NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-authority", "1")), None, Vector.empty, "SPEC-owned direction")
    val input = V.DelegateTaskInput("sm-workflow", "SPEC-delegate-repair", "SPEC-managed continuation",
      NonEmptyVector.one("docs/phase.md"), NonEmptyVector.one("test"), NonEmptyVector.one("objective"),
      NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-original", "1")), false, "documentation", "off", 10,
      "direct-instruction", Vector.empty, contract, Vector.empty)
    var session = ""
    var description = Option.empty[V.GoalTaskRepairContinuationDescriptionResult]
    var answer = Option.empty[V.GoalTaskRepairContinuationInput]
    var retained = Option.empty[BoundedWorkRuntime.Payload]
    var savedHistory = Vector.empty[HistoryEntry]
    val parameters = BoundedWorkExecutionAdmissionFixture.parameters ++ Map(
      "textus.resource.url.file.roots" -> root.toString, "textus.resource.read.max-bytes" -> "16777216")
    def installedRecord(operation: String, argument: Vector[String],
      service: String = "DelegateTaskService"): Record = {
      val (executable, archive) = installed.get
      val config = root.resolve(".textus/config.json")
      val json = io.circe.parser.parse(Files.readString(config)).toOption.get
      Files.writeString(config, json.mapObject(_.add("textus.component.file", archive.toString.asJson)).noSpaces)
      val logs = Files.createTempDirectory(root, "SPEC-installed-")
      val stdout = logs.resolve("stdout.json")
      val stderr = logs.resolve("stderr")
      val argv = Vector(executable.toString, org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name +
        "." + service + "." + operation, "--textus.assembly.descriptor=" + root.resolve(".textus/assembly.yaml"),
        "--textus.test.descriptor=" + root.resolve(".textus/test.yaml")) ++ argument ++ Vector("--json")
      val process = new ProcessBuilder(argv.asJava).directory(root.toFile).redirectOutput(stdout.toFile).redirectError(stderr.toFile).start()
      try {
        process.getOutputStream.close()
        withClue("installed operation timeout: " + stderr) { process.waitFor(300L, TimeUnit.SECONDS) shouldBe true }
        withClue("installed operation error: " + Files.readString(stderr)) { process.exitValue() shouldBe 0 }
        take(new org.goldenport.record.io.RecordDecoder().json(Files.readString(stdout)))
      } finally {
        if (process.isAlive) { process.destroy(); if (!process.waitFor(5L, TimeUnit.SECONDS)) process.destroyForcibly() }
        process.getInputStream.close(); process.getErrorStream.close()
      }
    }
    def installedCall[A: org.goldenport.convert.ValueReader](operation: String, argument: Vector[String]): A =
      take(summon[org.goldenport.convert.ValueReader[A]].readC(installedRecord(operation, argument)))
    def encoded(value: org.goldenport.record.RecordPresentable): String = take(value.toRecord().toJsonStringC)
    _with_runtime_(root, "TaskTrend", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      val selected = take(NativeDelegateTaskStartInput.prepareC(Map("input" -> "input", "selection-id" -> "SPEC-selection",
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "SPEC-start",
        "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
      prepare { setup => for {
        _ <- setup.setupC(_setup_commands_(root, "TaskTrend").size)
        row = selected.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        _ <- setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
      } yield () }
      val started = read_value[V.DelegateTaskView](take(call("startDelegateTask", List(Property("input", input, None),
        Property("selectionId", "SPEC-selection", None), Property("participant", "participant", None), Property("idempotencyKey", "SPEC-start", None)))))
      session = started.sessionId
      call("describeDelegateTaskRepairContinuation", List(Property("input",
        V.GoalTaskRepairContinuationDescriptionInput(session), None))).isSuccess shouldBe false
      prepare { setup => for {
        definition <- DelegateTaskRuntimeCatalog.loadC()
        host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
          "sm-workflow", root.resolve("repository").toString, "main", Vector("docs/phase.md"), Vector.empty, None, None, None, None, None)
        context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
        selected = setup.core.copy(executionContext = context)
        store <- DelegateTaskNativeRun.storageC(host, definition, selected)
        instances <- store.bindInstancesC()(using context)
        current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("Specification Start is absent")))
        original <- DelegateTaskNativeRun.loadPayloadC(store, session, current.revision.value, selected)
        observed <- RepositorySyncManagedGitObservation.observeC(setup.core)
        tree <- RepositorySyncManagedGitObservation.indexTreeC(setup.core)
        candidate = S.Candidate(observed.head, tree, Vector.empty, "")
        stopped = stoppedFixture(original.state, candidate)
        saved <- DelegateTaskNativeRun.saveC(host, store, current, original.copy(state = stopped,
          implementationWorker = Some("SPEC-worker"), implementationWorkers = Some(Vector("SPEC-worker"))), selected)
        _ = { retained = Some(original.copy(state = stopped, implementationWorker = Some("SPEC-worker"),
          implementationWorkers = Some(Vector("SPEC-worker")))); savedHistory = saved.history }
      } yield () }
      // These two invalid frontiers are SPEC-owned checkpoint variants. Neither
      // description executes checks; restore the exact stopped business afterward.
      def replaceStop(state: S.State): Unit = prepare { setup => for {
        definition <- DelegateTaskRuntimeCatalog.loadC()
        host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
          "sm-workflow", root.resolve("repository").toString, "main", Vector("docs/phase.md"), Vector.empty, None, None, None, None, None)
        context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
        selected = setup.core.copy(executionContext = context)
        store <- DelegateTaskNativeRun.storageC(host, definition, selected)
        instances <- store.bindInstancesC()(using context)
        current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("Specification Session is absent")))
        payload <- DelegateTaskNativeRun.loadPayloadC(store, session, current.revision.value, selected)
        saved <- DelegateTaskNativeRun.saveC(host, store, current, payload.copy(state = state), selected)
        _ = { savedHistory = saved.history }
      } yield () }
      val originalStop = retained.get.state
      Vector(originalStop.copy(automaticCount = originalStop.repairControl.effectiveLimit),
        originalStop.copy(receipts = originalStop.receipts.map(_.copy(termination = "unknown"))),
        originalStop.copy(pendingCheck = Some(originalStop.selected.head.identity))).foreach { invalid =>
        replaceStop(invalid)
        call("describeDelegateTaskRepairContinuation", List(Property("input",
          V.GoalTaskRepairContinuationDescriptionInput(session), None))).isSuccess shouldBe false
      }
      replaceStop(originalStop)
      if (installed.isEmpty) {
        val query = V.GoalTaskRepairContinuationDescriptionInput(session)
        description = Some(read_value[V.GoalTaskRepairContinuationDescriptionResult](take(call("describeDelegateTaskRepairContinuation", List(Property("input", query, None))))))
      }
    }
    if (installed.nonEmpty) description = Some(installedCall[V.GoalTaskRepairContinuationDescriptionResult]("describeDelegateTaskRepairContinuation",
      Vector("--input", encoded(V.GoalTaskRepairContinuationDescriptionInput(session)))))
    val described = description.get
    described.outcome shouldBe "decision-required"
    val target = take(R.targetC(described.target))
    target.business shouldBe retained.get
    val reference = V.WorkflowArtifactReference("SPEC-human-direction", "1")
    val direction = V.GoalTaskRepairContinuationInput(session, described.target, "ContinueRepair", "SPEC-owned explicit direction", "participant", reference)
    answer = Some(direction)
    _with_runtime_(root, "TaskTrend", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      call("resumeDelegateTaskRepair", List(Property("input", direction, None))).isSuccess shouldBe false
      Vector(direction.copy(sessionId = "SPEC-foreign-session"), direction.copy(participant = "foreign"),
        direction.copy(target = "{}"), direction.copy(target = target.copy(frontierRevision = target.frontierRevision + 1).asJson.noSpaces),
        direction.copy(target = target.copy(componentInstance = "foreign").asJson.noSpaces)).foreach { invalid =>
        call("resumeDelegateTaskRepair", List(Property("input", invalid, None))).isSuccess shouldBe false
      }
      val deferReference = V.WorkflowArtifactReference("SPEC-human-defer", "1")
      val deferred = direction.copy(choice = "Defer", reference = deferReference)
      def provision(selectedDirection: V.GoalTaskRepairContinuationInput): Unit = {
        val selectedReference = selectedDirection.reference
        val execution = V.WorkflowExecutionEvidence(Vector(selectedReference), Some("participant"), None, None)
        def save(name: String, data: org.goldenport.record.RecordPresentable): String = {
          val path = root.resolve("SPEC-" + selectedReference.identity + "-" + name + ".json")
          Files.writeString(path, encoded(data))
          path.toUri.toASCIIString
        }
        val requestPath = save("request", described)
        val resultPath = save("result", selectedDirection)
        val referencePath = save("reference", selectedReference)
        val executionPath = save("execution", execution)
        val preparation = V.WorkflowEvidencePreparationInput("DelegateTask", "repair-continuation", requestPath, resultPath,
          referencePath, executionPath, "normal-development-host", actor.clock.instant().minusSeconds(1).toString,
          "entity:smworkflow.cli.host_evidence", Some("participant"))
        val row = if (installed.nonEmpty) {
          val record = installedRecord("prepareEvidence", Vector("--input", encoded(preparation),
            "--preparationFormat", "legacy-full"), "NativeWorkflowPreparationService")
          record.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        } else {
          val response = take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "NativeWorkflowPreparationService", operation = "prepareEvidence", properties = List(
              Property("input", preparation, None), Property("preparationFormat", "legacy-full", None))), actor))
          val record = response match {
            case org.goldenport.protocol.operation.OperationResponse.RecordResponse(value) => value
            case org.goldenport.protocol.operation.OperationResponse.Json(value) =>
              take(new org.goldenport.record.io.RecordDecoder().json(value.noSpaces))
            case other => fail("Expected complete public preparation: " + other)
          }
          record.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        }
        prepare(setup => setup.createC("host_evidence", row.getString("id").get, row.removeKeys(Set("id"))))
      }
      provision(deferred)
      read_value[V.GoalTaskRepairContinuationDescriptionResult](take(call("resumeDelegateTaskRepair", List(Property("input", deferred, None))))).outcome shouldBe "deferred"
      val afterDefer = read_value[V.GoalTaskRepairContinuationDescriptionResult](take(call("describeDelegateTaskRepairContinuation",
        List(Property("input", V.GoalTaskRepairContinuationDescriptionInput(session), None)))))
      afterDefer.target shouldBe described.target
      provision(direction)
      if (installed.isEmpty) {
        val result = read_value[V.GoalTaskRepairContinuationDescriptionResult](take(call("resumeDelegateTaskRepair", List(Property("input", direction, None)))))
        result.outcome shouldBe "resumed-awaiting-advance"
      }
    }
    if (installed.nonEmpty) installedCall[V.GoalTaskRepairContinuationDescriptionResult]("resumeDelegateTaskRepair", Vector("--input", encoded(direction))).outcome shouldBe "resumed-awaiting-advance"
    _with_runtime_(root, "TaskTrend", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      val repeat = read_value[V.GoalTaskRepairContinuationDescriptionResult](take(call("resumeDelegateTaskRepair", List(Property("input", direction, None)))))
      repeat.outcome shouldBe "already-resumed"
      call("resumeDelegateTaskRepair", List(Property("input", direction.copy(rationale = "different"), None))).isSuccess shouldBe false
      prepare { setup => for {
        definition <- DelegateTaskRuntimeCatalog.loadC()
        host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
          "sm-workflow", root.resolve("repository").toString, "main", Vector("docs/phase.md"), Vector.empty, None, None, None, None, None)
        context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
        selected = setup.core.copy(executionContext = context)
        store <- DelegateTaskNativeRun.storageC(host, definition, selected)
        instances <- store.bindInstancesC()(using context)
        current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("Specification Session is absent")))
        payload <- DelegateTaskNativeRun.loadPayloadC(store, session, current.revision.value, selected)
        _ = { current.history.dropRight(1) shouldBe savedHistory
          payload.copy(state = payload.state.copy(stage = "Human"), delegateRepairContinuation = None) shouldBe retained.get }
      } yield () }
      if (installed.isEmpty) {
        val issued = read_control_value[V.DelegateTaskView](take(call("advanceDelegateTask", List(Property("sessionId", session, None)))))
        issued.kind shouldBe "Request"
        val reference = issued.retainedWork.get
        val response = take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = WorkflowRetainedWork.serviceName, operation = WorkflowRetainedWork.readOperation,
          properties = List(Property("input", reference, None))), actor))
        val detail = read_value[V.WorkflowRetainedWorkReadResult](response)
        val complete = take(V.DelegateTaskView.createC(detail.response))
        complete.issuedWorkContext.get.requiredOperation shouldBe "delegate-task-repair-capability"
      }
    }
    if (installed.nonEmpty) {
      val issued = installedCall[V.DelegateTaskView]("advanceDelegateTask", Vector("--sessionId", session))
      issued.kind shouldBe "Request"
      val reference = issued.retainedWork.get
      val detail = take(summon[org.goldenport.convert.ValueReader[V.WorkflowRetainedWorkReadResult]].readC(
        installedRecord(WorkflowRetainedWork.readOperation, Vector("--input", encoded(reference)), WorkflowRetainedWork.serviceName)))
      val complete = take(V.DelegateTaskView.createC(detail.response))
      complete.issuedWorkContext.get.requiredOperation shouldBe "delegate-task-repair-capability"
    }
    Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
    Files.readString(root.resolve("repository/docs/phase.md")) shouldBe "Specification-owned original source\n"
  }
  "Managed DelegateTask repair" should {
    "preserve the exact stopped Session across admission, separate Advance and reopen" in { qualify(None) }
  }
}
