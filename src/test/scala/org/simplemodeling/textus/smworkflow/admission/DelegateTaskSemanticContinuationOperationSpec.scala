package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.wordspec.AnyWordSpec
import BoundedWorkRuntime.given
import DelegateTaskSemanticContinuation as R

@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DelegateTaskSemanticContinuationOperationSpec extends AnyWordSpec with org.scalatest.matchers.should.Matchers
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
  private def take[A](value: Consequence[A]): A = value.toOption.getOrElse(fail(s"Operation refused: $value"))
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
    val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/delegate-semantic-managed").toAbsolutePath), "SPEC-").toRealPath()
    val contract = V.DelegateTaskContract("SPEC-requester", "SPEC-destination", "SPEC-requester", "SPEC-component", "destination",
      "documentation", NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"), Vector.empty,
      NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-authority", "1")), None, Vector.empty, "SPEC-owned direction")
    val input = V.DelegateTaskInput("sm-workflow", "SPEC-delegate-semantic", "SPEC-managed continuation",
      NonEmptyVector.one("docs/phase.md"), NonEmptyVector.one("test"), NonEmptyVector.of("objective", "delivery"),
      NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-original", "1")), false, "documentation", "off", 10,
      "direct-instruction", Vector.empty, contract, Vector.empty)
    var session = ""
    var description = Option.empty[V.DelegateTaskSemanticContinuationDescriptionResult]
    var emittedWork = Option.empty[V.WorkflowRetainedWorkReference]
    val parameters = BoundedWorkExecutionAdmissionFixture.logicalParameters ++ Map(
      "textus.resource.url.file.roots" -> root.toString, "textus.resource.read.max-bytes" -> "16777216")
    def installedRecord(operation: String, argument: Vector[String],
      service: String = "DelegateTaskService"): Record = {
      val (_, archive) = installed.get
      val logs = Files.createTempDirectory(root, "SPEC-installed-")
      val stdout = logs.resolve("stdout.json")
      val stderr = logs.resolve("stderr")
      val argv = NativeWorkflowCliFixture.candidateCommand(archive) ++ Vector(org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name +
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
    _with_runtime_(root, "TaskSemantic", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      val selected = take(NativeDelegateTaskStartInput.prepareC(Map("input" -> "input", "selection-id" -> "SPEC-selection",
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "SPEC-start",
        "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
      prepare { setup => for {
        _ <- setup.setupC(_setup_commands_(root, "TaskSemantic").size)
        row = selected.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        _ <- setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
      } yield () }
      val started = read_value[V.DelegateTaskView](take(call("startDelegateTask", List(Property("input", input, None),
        Property("selectionId", "SPEC-selection", None), Property("participant", "participant", None), Property("idempotencyKey", "SPEC-start", None)))))
      session = started.sessionId
      call("describeDelegateTaskSemanticContinuation", List(Property("input",
        V.DelegateTaskSemanticContinuationDescriptionInput(session), None))).isSuccess shouldBe false
      val issued = read_control_value[V.DelegateTaskView](take(call("advanceDelegateTask", List(Property("sessionId", session, None)))))
      call("describeDelegateTaskSemanticContinuation", List(Property("input",
        V.DelegateTaskSemanticContinuationDescriptionInput(session), None))).isSuccess shouldBe false
      val detail = read_value[V.WorkflowRetainedWorkReadResult](take(owner.executeOperationResponseInContext(Request.of(
        component = component.name, service = WorkflowRetainedWork.serviceName, operation = WorkflowRetainedWork.readOperation,
        properties = List(Property("input", issued.retainedWork.get, None))), actor)))
      val implement = take(V.DelegateTaskView.createC(detail.response))
      val ref = V.WorkflowArtifactReference("SPEC-original-implementation", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(ref), Some("SPEC-implementer"), None,
        Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "SPEC-implementer", "ordinary")))
      val result = V.DelegateTaskWorkResult("decision-needed", "SPEC dependency unresolved", Vector.empty,
        NonEmptyVector.of(V.DelegateTaskCondition("objective", false, "later delivery"),
          V.DelegateTaskCondition("delivery", false, "later delivery")),
        Vector("SPEC dependency needs an actual result"), None, None)
      take(call("submitDelegateTaskWorkResult", List(Property("sessionId", session, None),
        Property("issuedWorkContext", implement.issuedWorkContext.get, None), Property("result", result, None),
        Property("resultReference", ref, None), Property("executionEvidence", execution, None))))
      call("describeDelegateTaskSemanticContinuation", List(Property("input",
        V.DelegateTaskSemanticContinuationDescriptionInput(session), None))).isSuccess shouldBe false
      val human = read_control_value[V.DelegateTaskView](take(call("advanceDelegateTask", List(Property("sessionId", session, None)))))
      human.kind shouldBe "Human"
      if (installed.isEmpty) {
        val query = V.DelegateTaskSemanticContinuationDescriptionInput(session)
        description = Some(read_value[V.DelegateTaskSemanticContinuationDescriptionResult](take(call("describeDelegateTaskSemanticContinuation", List(Property("input", query, None))))))
      }
    }
    if (installed.nonEmpty) description = Some(installedCall[V.DelegateTaskSemanticContinuationDescriptionResult]("describeDelegateTaskSemanticContinuation",
      Vector("--input", encoded(V.DelegateTaskSemanticContinuationDescriptionInput(session)))))
    val described = description.get
    described.outcome shouldBe "decision-required"
    val target = take(R.targetC(described.target))
    target.original.result.unresolvedQuestions shouldBe Vector("SPEC dependency needs an actual result")
    target.original.work.kind shouldBe "Implement"
    target.original.resultReference shouldBe ContextReference("SPEC-original-implementation", "1")
    val stopped = target.business
    R.eligible(stopped) shouldBe true
    val invalidStates = Vector(
      "review" -> stopped.state.copy(stage = "Review"),
      "validation" -> stopped.state.copy(stage = "Validate"),
      "unknown effect" -> stopped.state.copy(stage = "CommitUnknown"),
      "terminal" -> stopped.state.copy(stage = "Completed"),
      "noninitial" -> stopped.state.copy(generation = 2),
      "pending check" -> stopped.state.copy(pendingCheck = Some("test")),
      "ordinary count" -> stopped.state.copy(ordinaryCount = 1),
      "automatic count" -> stopped.state.copy(automaticCount = 1),
      "candidate" -> stopped.state.copy(candidate = Some(S.Candidate(stopped.baseHead, "a" * 40, Vector.empty, ""))),
      "unknown receipt" -> stopped.state.copy(receipts = Vector(RepositorySyncNativeMergeValidation.Receipt("test", "unknown", None, "", ""))),
      "exhausted allowance" -> stopped.state.copy(repairControl = stopped.state.repairControl.copy(selectedLimit = 0)),
      "trend stop" -> stopped.state.copy(repairControl = stopped.state.repairControl.copy(observations =
        Vector.fill(4)(GoalPhaseRuntimeRepairControl.Observation("SPEC", "SPEC-failure", Set("failure"))))),
      "review result" -> stopped.state.copy(reviewReference = Some(ContextReference("SPEC-review", "1"))))
    invalidStates.foreach { case (label, other) =>
      withClue(label) { R.eligible(stopped.copy(state = other)) shouldBe false }
    }
    val reference = V.WorkflowArtifactReference("SPEC-human-direction", "1")
    val direction = V.DelegateTaskSemanticContinuationInput(session, described.target, "ContinueAfterDependencyResolution", "SPEC-owned explicit direction", "participant", reference, Vector(V.WorkflowArtifactReference("SPEC-upstream", "1")))
    _with_runtime_(root, "TaskSemantic", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      def unchanged(): Unit = {
        val again = read_value[V.DelegateTaskSemanticContinuationDescriptionResult](take(call("describeDelegateTaskSemanticContinuation",
          List(Property("input", V.DelegateTaskSemanticContinuationDescriptionInput(session), None)))))
        again.target shouldBe described.target
      }
      val alteredHistory = target.frontier.mapObject(fields => fields.add("history", Json.arr()))
      val mutated = Vector(
        "unadmitted" -> direction,
        "session" -> direction.copy(sessionId = "SPEC-foreign-session"),
        "participant" -> direction.copy(participant = "foreign"),
        "malformed target" -> direction.copy(target = "{}"),
        "revision" -> direction.copy(target = target.copy(frontierRevision = target.frontierRevision + 1).asJson.noSpaces),
        "owner" -> direction.copy(target = target.copy(componentInstance = "foreign").asJson.noSpaces),
        "root" -> direction.copy(target = target.copy(business = stopped.copy(root = "/foreign")).asJson.noSpaces),
        "branch" -> direction.copy(target = target.copy(business = stopped.copy(branch = "foreign")).asJson.noSpaces),
        "base" -> direction.copy(target = target.copy(business = stopped.copy(baseHead = "b" * 40)).asJson.noSpaces),
        "input" -> direction.copy(target = target.copy(business = stopped.copy(state = stopped.state.copy(
          input = stopped.state.input.copy(objective = "foreign objective")))).asJson.noSpaces),
        "history" -> direction.copy(target = target.copy(frontier = alteredHistory).asJson.noSpaces),
        "original issue" -> direction.copy(target = target.copy(original = target.original.copy(
          continuationId = "foreign-issue")).asJson.noSpaces),
        "completion" -> direction.copy(target = target.copy(original = target.original.copy(
          result = target.original.result.copy(summary = "foreign completion"))).asJson.noSpaces),
        "result reference" -> direction.copy(target = target.copy(original = target.original.copy(
          resultReference = ContextReference("foreign", "1"))).asJson.noSpaces),
        "choice" -> direction.copy(choice = "unrecognized"),
        "empty upstream" -> direction.copy(upstream = Vector.empty),
        "malformed upstream" -> direction.copy(upstream = Vector(V.WorkflowArtifactReference("", "1"))))
      mutated.foreach { case (label, invalid) =>
        withClue(label) { call("resumeDelegateTaskSemanticContinuation", List(Property("input", invalid, None))).isSuccess shouldBe false }
        unchanged()
      }
      val deferReference = V.WorkflowArtifactReference("SPEC-human-defer", "1")
      val deferred = direction.copy(choice = "Defer", reference = deferReference)
      def provision(selectedDirection: V.DelegateTaskSemanticContinuationInput): Unit = {
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
        val preparation = V.WorkflowEvidencePreparationInput("DelegateTask", "semantic-continuation", requestPath, resultPath,
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
      read_value[V.DelegateTaskSemanticContinuationDescriptionResult](take(call("resumeDelegateTaskSemanticContinuation", List(Property("input", deferred, None))))).outcome shouldBe "deferred"
      val afterDefer = read_value[V.DelegateTaskSemanticContinuationDescriptionResult](take(call("describeDelegateTaskSemanticContinuation",
        List(Property("input", V.DelegateTaskSemanticContinuationDescriptionInput(session), None)))))
      afterDefer.target shouldBe described.target
      provision(direction)
      Vector(direction.copy(rationale = "different"),
        direction.copy(upstream = Vector(V.WorkflowArtifactReference("SPEC-other-upstream", "1"))),
        direction.copy(reference = V.WorkflowArtifactReference("SPEC-other-human", "1"))).foreach { invalid =>
        call("resumeDelegateTaskSemanticContinuation", List(Property("input", invalid, None))).isSuccess shouldBe false
        unchanged()
      }
      if (installed.isEmpty) {
        val result = read_value[V.DelegateTaskSemanticContinuationDescriptionResult](take(call("resumeDelegateTaskSemanticContinuation", List(Property("input", direction, None)))))
        result.outcome shouldBe "resumed-awaiting-advance"
      }
    }
    if (installed.nonEmpty) installedCall[V.DelegateTaskSemanticContinuationDescriptionResult]("resumeDelegateTaskSemanticContinuation", Vector("--input", encoded(direction))).outcome shouldBe "resumed-awaiting-advance"
    def verifyCheckpoint(suspended: Boolean): Unit =
      _with_runtime_(root, "TaskSemantic", hostParameters = parameters, componentInstance = "default") { (_, _, _, prepare) =>
        prepare { setup => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", Vector("docs/phase.md"), Vector.empty,
            None, None, None, None, None)
          context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
          selected = setup.core.copy(executionContext = context)
          store <- DelegateTaskNativeRun.storageC(host, definition, selected)
          instances <- store.bindInstancesC()(using context)
          current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
            _.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC reopened Session absent")))
          payload <- DelegateTaskNativeRun.loadPayloadC(store, session,
            current.revision.value - (if (suspended) 1L else 0L), selected)
          currentJson <- WorkflowInstanceRecordJsonV1.encodeC(current).flatMap(s =>
            io.circe.parser.parse(s).fold(e => Consequence.stateConflict(e.message), Consequence.success))
          _ = {
            current.suspension.nonEmpty shouldBe suspended
            current.identity.value shouldBe target.sessionId
            val oldHistory = target.frontier.hcursor.get[Vector[Json]]("history").toOption.get
            val newHistory = currentJson.hcursor.get[Vector[Json]]("history").toOption.get
            newHistory.take(oldHistory.size) shouldBe oldHistory
            newHistory.size should be > oldHistory.size
            payload.root shouldBe stopped.root
            payload.branch shouldBe stopped.branch
            payload.baseHead shouldBe stopped.baseHead
            payload.state.input shouldBe stopped.state.input
            payload.state.input.conditions shouldBe Vector("objective", "delivery")
            payload.state.selected shouldBe stopped.state.selected
            payload.state.generation shouldBe stopped.state.generation
            payload.state.automaticCount shouldBe stopped.state.automaticCount
            payload.state.ordinaryCount shouldBe stopped.state.ordinaryCount
            payload.state.repairControl shouldBe stopped.state.repairControl
            payload.state.semanticReference shouldBe Some(target.original.resultReference)
            val accepted = payload.delegateSemanticContinuation.get
            accepted.target shouldBe target.asJson
            accepted.choice shouldBe direction.choice
            accepted.reference shouldBe ContextReference(reference.identity, reference.revision)
            accepted.upstream shouldBe Vector(ContextReference("SPEC-upstream", "1"))
          }
        } yield () }
      }
    def verifySuccessor(complete: V.DelegateTaskView): Unit = {
      complete.issuedWorkContext.get.requiredOperation shouldBe "delegate-task-implementation-capability"
      val json = io.circe.parser.parse(take(complete.work.get.toJsonStringC)).toOption.get
      val work = GoalPhaseNativeMetadata.normalizePublicJson(json).toOption.get.as[S.Work].toOption.get
      work.session shouldBe session
      work.kind shouldBe "Implement"
      work.state.input shouldBe stopped.state.input
      work.state.selected shouldBe stopped.state.selected
      work.state.generation shouldBe stopped.state.generation
      work.state.automaticCount shouldBe stopped.state.automaticCount
      work.state.ordinaryCount shouldBe stopped.state.ordinaryCount
      work.state.repairControl shouldBe stopped.state.repairControl
    }
    verifyCheckpoint(false)
    _with_runtime_(root, "TaskSemantic", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      val repeat = read_value[V.DelegateTaskSemanticContinuationDescriptionResult](take(call("resumeDelegateTaskSemanticContinuation", List(Property("input", direction, None)))))
      repeat.outcome shouldBe "already-resumed"
      call("resumeDelegateTaskSemanticContinuation", List(Property("input", direction.copy(rationale = "different"), None))).isSuccess shouldBe false
      if (installed.isEmpty) {
        val issued = read_control_value[V.DelegateTaskView](take(call("advanceDelegateTask", List(Property("sessionId", session, None)))))
        issued.kind shouldBe "Request"
        emittedWork = issued.retainedWork
        read_value[V.DelegateTaskSemanticContinuationDescriptionResult](take(call("resumeDelegateTaskSemanticContinuation",
          List(Property("input", direction, None))))).outcome shouldBe "already-resumed"
        val duplicate = read_control_value[V.DelegateTaskView](take(call("advanceDelegateTask", List(Property("sessionId", session, None)))))
        duplicate.retainedWork shouldBe issued.retainedWork
        val reference = issued.retainedWork.get
        val response = take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = WorkflowRetainedWork.serviceName, operation = WorkflowRetainedWork.readOperation,
          properties = List(Property("input", reference, None))), actor))
        val detail = read_value[V.WorkflowRetainedWorkReadResult](response)
        val complete = take(V.DelegateTaskView.createC(detail.response))
        verifySuccessor(complete)
      }
    }
    if (installed.nonEmpty) {
      val issued = installedCall[V.DelegateTaskView]("advanceDelegateTask", Vector("--sessionId", session))
      issued.kind shouldBe "Request"
      emittedWork = issued.retainedWork
      val reference = issued.retainedWork.get
      val detail = take(summon[org.goldenport.convert.ValueReader[V.WorkflowRetainedWorkReadResult]].readC(
        installedRecord(WorkflowRetainedWork.readOperation, Vector("--input", encoded(reference)), WorkflowRetainedWork.serviceName)))
      val complete = take(V.DelegateTaskView.createC(detail.response))
      verifySuccessor(complete)
      installedCall[V.DelegateTaskSemanticContinuationDescriptionResult]("resumeDelegateTaskSemanticContinuation",
        Vector("--input", encoded(direction))).outcome shouldBe "already-resumed"
      val duplicate = installedCall[V.DelegateTaskView]("advanceDelegateTask", Vector("--sessionId", session))
      duplicate.retainedWork shouldBe issued.retainedWork
    }
    verifyCheckpoint(true)
    _with_runtime_(root, "TaskSemantic", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, _) =>
      def call(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      read_value[V.DelegateTaskSemanticContinuationDescriptionResult](take(call("resumeDelegateTaskSemanticContinuation",
        List(Property("input", direction, None))))).outcome shouldBe "already-resumed"
      call("resumeDelegateTaskSemanticContinuation", List(Property("input", direction.copy(rationale = "changed"), None))).isSuccess shouldBe false
      val reopened = read_control_value[V.DelegateTaskView](take(call("advanceDelegateTask", List(Property("sessionId", session, None)))))
      reopened.retainedWork shouldBe emittedWork
    }
    verifyCheckpoint(true)
    Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
    Files.readString(root.resolve("repository/docs/phase.md")) shouldBe "Specification-owned original source\n"
  }
  "Managed DelegateTask semantic continuation" should {
    "preserve the original semantic stop across admission, separate Advance and reopen" in { qualify(None) }
  }
}
