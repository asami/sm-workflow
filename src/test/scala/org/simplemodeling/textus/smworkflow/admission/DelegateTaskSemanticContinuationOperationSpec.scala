package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.syntax.*
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.wordspec.AnyWordSpec
import BoundedWorkRuntime.given
import DelegateTaskSemanticContinuation as R

/** Actual native semantic work, with separately identified fixture qualification facts.
  * @since Nov. 12, 2026
  * @version Nov. 23, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
class DelegateTaskSemanticContinuationOperationSpec extends AnyWordSpec with org.scalatest.matchers.should.Matchers
    with org.scalatest.GivenWhenThen
    with ThreeWorkflowManagedRepositoryFixture {
  protected def isolated_repeated_semantic: Boolean = false
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
  private def _take[A](value: Consequence[A]): A = value.toOption.getOrElse(fail(s"Operation refused: $value"))
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json = {
    val git = TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations ++ Vector(
        GitOperationKind.IndexTree, GitOperationKind.PendingPaths, GitOperationKind.PendingDiff), goalPaths = Vector("docs/phase.md"),
      goalMessages = Vector("SPEC semantic source movement"), goalSelections = Vector(Vector("docs/phase.md")))
    Files.createDirectories(root.resolve("capture-root/validation"))
    val workspace = Json.obj("identity" -> "sm-workflow-validation-artifacts".asJson,
      "root" -> root.resolve("capture-root").toString.asJson)
    val grant = Json.obj("componentId" -> org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name.asJson,
      "workspaceId" -> "sm-workflow-validation-artifacts".asJson, "access" -> "manage".asJson)
    git.mapObject(fields => fields.add("workspaces", fields("workspaces").get.mapArray(_ :+ workspace))
      .add("workspaceGrants", fields("workspaceGrants").get.mapArray(_ :+ grant)))
  }
  override protected def _prepare_sbt_build_(root: Path, scenario: String): Unit = {
    super._prepare_sbt_build_(root, scenario)
    val file = root.resolve("repository/docs/phase.md")
    Files.createDirectories(file.getParent)
    Files.writeString(file, "Specification-owned original source\n")
  }
  override protected def _setup_commands_(root: Path, scenario: String): Vector[Vector[String]] =
    super._setup_commands_(root, scenario).patch(2, Vector(Vector("-C", root.resolve("repository").toString,
      "--literal-pathspecs", "add", "--", "docs/phase.md")), 0)

  private[admission] def qualify(installed: Option[(Path, Path)],
    repeated: Option[(String, Path)] = None): Unit = {
    import scala.jdk.CollectionConverters.*
    import java.util.concurrent.TimeUnit
    installed.foreach { case (executable, archive) =>
      require(executable.isAbsolute && Files.isExecutable(executable), "Installed command must exist")
      require(archive.isAbsolute && Files.isRegularFile(archive), "Candidate CAR must exist")
    }
    repeated.foreach { (phase, path) =>
      require(Set("before", "after", "collect-after")(phase) && path.isAbsolute,
        "Select before|after|collect-after and an absolute dedicated fixture")
    }
    if (repeated.exists(pair => Set("after", "collect-after")(pair._1))) {
      val collectonly = repeated.get._1 == "collect-after"
      require(!collectonly || installed.nonEmpty, "Collection requires the explicitly selected installed runtime")
      _repeated_stop(repeated.get._2.toRealPath(), installed, None, collectonly = collectonly)
      return
    }
    Given("a small isolated managed destination with actual initial semantic work")
    val root = repeated match {
      case Some((_, path)) =>
        Files.exists(path) shouldBe false
        Files.createDirectories(path).toRealPath()
      case None => Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/delegate-semantic-managed").toAbsolutePath), "SPEC-").toRealPath()
    }
    val contract = V.DelegateTaskContract("SPEC-requester", "SPEC-destination", "SPEC-requester", "SPEC-component", "destination",
      "documentation", NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"), Vector.empty,
      NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-authority", "1")), None, Vector.empty, "SPEC-owned direction", None, None, None)
    val input = V.DelegateTaskInput("sm-workflow", "SPEC-delegate-semantic", "SPEC-managed continuation",
      NonEmptyVector.one("docs/phase.md"), NonEmptyVector.one("test"), NonEmptyVector.of("objective", "delivery"),
      NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-original", "1")), false, "documentation", "off", 10,
      "direct-instruction", Vector.empty, contract, Vector.empty, None)
    var session = ""
    var description = Option.empty[V.DelegateTaskSemanticContinuationDescriptionResult]
    var emittedwork = Option.empty[V.WorkflowRetainedWorkReference]
    val parameters = BoundedWorkExecutionAdmissionFixture.logicalParameters ++ Map(
      "textus.resource.url.file.roots" -> root.toString, "textus.resource.read.max-bytes" -> "16777216")
    val nativeinitial = installed.nonEmpty && repeated.nonEmpty
    def _installed_record_(operation: String, argument: Vector[String],
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
        _take(new org.goldenport.record.io.RecordDecoder().json(Files.readString(stdout)))
      } finally {
        if (process.isAlive) { process.destroy(); if (!process.waitFor(5L, TimeUnit.SECONDS)) process.destroyForcibly() }
        process.getInputStream.close(); process.getErrorStream.close()
      }
    }
    def _installed_call_[A: org.goldenport.convert.ValueReader](operation: String, argument: Vector[String]): A =
      _take(summon[org.goldenport.convert.ValueReader[A]].readC(_installed_record_(operation, argument)))
    def _encoded_(value: org.goldenport.record.RecordPresentable): String = _take(value.toRecord().toJsonStringC)
    _with_runtime_(root, "TaskSemantic", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def _call_(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      val selected = _take(NativeDelegateTaskStartInput.prepareC(Map("input" -> "input", "selection-id" -> "SPEC-selection",
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "SPEC-start",
        "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
      prepare { setup => for {
        _ <- setup.setupC(_setup_commands_(root, "TaskSemantic").size)
        row = selected.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        _ <- setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
      } yield () }
      val started = if (nativeinitial) _installed_call_[V.DelegateTaskView]("startDelegateTask", Vector("--input", _encoded_(input),
        "--selectionId", "SPEC-selection", "--participant", "participant", "--idempotencyKey", "SPEC-start"))
        else read_value[V.DelegateTaskView](_take(_call_("startDelegateTask", List(Property("input", input, None),
          Property("selectionId", "SPEC-selection", None), Property("participant", "participant", None), Property("idempotencyKey", "SPEC-start", None)))))
      session = started.sessionId
      _call_("describeDelegateTaskSemanticContinuation", List(Property("input",
        V.DelegateTaskSemanticContinuationDescriptionInput(session), None))).isSuccess shouldBe false
      When("ordinary Advance issues the initial Implement and its actual result needs direction")
      val issued = if (nativeinitial) _installed_call_[V.DelegateTaskView]("advanceDelegateTask", Vector("--sessionId", session))
        else read_control_value[V.DelegateTaskView](_take(_call_("advanceDelegateTask", List(Property("sessionId", session, None)))))
      _call_("describeDelegateTaskSemanticContinuation", List(Property("input",
        V.DelegateTaskSemanticContinuationDescriptionInput(session), None))).isSuccess shouldBe false
      val detail = read_value[V.WorkflowRetainedWorkReadResult](_take(owner.executeOperationResponseInContext(Request.of(
        component = component.name, service = WorkflowRetainedWork.serviceName, operation = WorkflowRetainedWork.readOperation,
        properties = List(Property("input", issued.retainedWork.get, None))), actor)))
      val implement = _take(V.DelegateTaskView.createC(detail.response))
      val ref = V.WorkflowArtifactReference("SPEC-original-implementation", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(ref), Some("SPEC-implementer"), None,
        Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "SPEC-implementer", "ordinary")))
      val result = V.DelegateTaskWorkResult("decision-needed", "SPEC dependency unresolved", Vector.empty,
        NonEmptyVector.of(V.DelegateTaskCondition("objective", false, "later delivery"),
          V.DelegateTaskCondition("delivery", false, "later delivery")),
        Vector("SPEC dependency needs an actual result"), None, None)
      if (nativeinitial) _installed_call_[V.DelegateTaskView]("submitDelegateTaskWorkResult", Vector("--sessionId", session,
        "--issuedWorkContext", _encoded_(implement.issuedWorkContext.get), "--result", _encoded_(result),
        "--resultReference", _encoded_(ref), "--executionEvidence", _encoded_(execution)))
      else _take(_call_("submitDelegateTaskWorkResult", List(Property("sessionId", session, None),
        Property("issuedWorkContext", implement.issuedWorkContext.get, None), Property("result", result, None),
        Property("resultReference", ref, None), Property("executionEvidence", execution, None))))
      _call_("describeDelegateTaskSemanticContinuation", List(Property("input",
        V.DelegateTaskSemanticContinuationDescriptionInput(session), None))).isSuccess shouldBe false
      val human = if (nativeinitial) _installed_call_[V.DelegateTaskView]("advanceDelegateTask", Vector("--sessionId", session))
        else read_control_value[V.DelegateTaskView](_take(_call_("advanceDelegateTask", List(Property("sessionId", session, None)))))
      Then("the original completion is materialized as Human before it can be described")
      human.kind shouldBe "Human"
      if (installed.isEmpty) {
        val query = V.DelegateTaskSemanticContinuationDescriptionInput(session)
        description = Some(read_value[V.DelegateTaskSemanticContinuationDescriptionResult](_take(_call_("describeDelegateTaskSemanticContinuation", List(Property("input", query, None))))))
      }
    }
    if (installed.nonEmpty) description = Some(_installed_call_[V.DelegateTaskSemanticContinuationDescriptionResult]("describeDelegateTaskSemanticContinuation",
      Vector("--input", _encoded_(V.DelegateTaskSemanticContinuationDescriptionInput(session)))))
    val described = description.get
    described.outcome shouldBe "decision-required"
    val target = _take(R.targetC(described.target))
    target.original.result.unresolvedQuestions shouldBe Vector("SPEC dependency needs an actual result")
    target.original.work.kind shouldBe "Implement"
    target.original.resultReference shouldBe ContextReference("SPEC-original-implementation", "1")
    val stopped = target.business
    R.eligible(stopped) shouldBe true
    val invalidstates = Vector(
      "review" -> stopped.state.copy(stage = "Review"),
      "validation" -> stopped.state.copy(stage = "Validate"),
      "unknown effect" -> stopped.state.copy(stage = "CommitUnknown"),
      "terminal" -> stopped.state.copy(stage = "Completed"),
      "nonpositive generation" -> stopped.state.copy(generation = 0),
      "pending check" -> stopped.state.copy(pendingCheck = Some("test")),
      "ordinary count" -> stopped.state.copy(ordinaryCount = 1),
      "negative automatic count" -> stopped.state.copy(automaticCount = -1),
      "unknown receipt" -> stopped.state.copy(receipts = Vector(RepositorySyncNativeMergeValidation.Receipt("test", "unknown", None, "", ""))),
      "exhausted allowance" -> stopped.state.copy(repairControl = stopped.state.repairControl.copy(selectedLimit = 0)),
      "trend stop" -> stopped.state.copy(repairControl = stopped.state.repairControl.copy(observations =
        Vector.fill(4)(GoalPhaseRuntimeRepairControl.Observation("SPEC", "SPEC-failure", Set("failure"))))),
      "exhausted count" -> stopped.state.copy(generation = 11, automaticCount = 10, ordinaryCount = 10))
    invalidstates.foreach { case (label, other) =>
      withClue(label) { R.eligible(stopped.copy(state = other)) shouldBe false }
    }
    Given("historical candidate, review and non-stopping trend facts belonging to the retained request")
    val later = stopped.copy(state = stopped.state.copy(generation = 7, automaticCount = 6, ordinaryCount = 6,
      candidate = Some(S.Candidate(stopped.baseHead, "a" * 40, Vector.empty, "")),
      reviewReference = Some(ContextReference("SPEC-review", "1")),
      repairControl = stopped.state.repairControl.copy(observations = Vector(GoalPhaseRuntimeRepairControl.Observation(
        stopped.state.input.requestIdentity, "SPEC-terminal-check", Set("earlier failure"))))))
    When("only metadata eligibility is interpreted without claiming a native association")
    val eligible = R.eligible(later)
    Then("noninitial history alone does not exclude the semantic boundary")
    eligible shouldBe true
    val reference = V.WorkflowArtifactReference("SPEC-human-direction", "1")
    val direction = V.DelegateTaskSemanticContinuationInput(session, described.target, "ContinueAfterDependencyResolution", "SPEC-owned explicit direction", "participant", reference, Vector(V.WorkflowArtifactReference("SPEC-upstream", "1")))
    _with_runtime_(root, "TaskSemantic", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def _call_(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      def _unchanged_(): Unit = {
        val again = read_value[V.DelegateTaskSemanticContinuationDescriptionResult](_take(_call_("describeDelegateTaskSemanticContinuation",
          List(Property("input", V.DelegateTaskSemanticContinuationDescriptionInput(session), None)))))
        again.target shouldBe described.target
      }
      val alteredhistory = target.frontier.mapObject(fields => fields.add("history", Json.arr()))
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
        "history" -> direction.copy(target = target.copy(frontier = alteredhistory).asJson.noSpaces),
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
        withClue(label) { _call_("resumeDelegateTaskSemanticContinuation", List(Property("input", invalid, None))).isSuccess shouldBe false }
        _unchanged_()
      }
      val deferreference = V.WorkflowArtifactReference("SPEC-human-defer", "1")
      val deferred = direction.copy(choice = "Defer", reference = deferreference)
      def _provision_(selecteddirection: V.DelegateTaskSemanticContinuationInput): Unit = {
        val selectedreference = selecteddirection.reference
        val execution = V.WorkflowExecutionEvidence(Vector(selectedreference), Some("participant"), None, None)
        def _save_(name: String, data: org.goldenport.record.RecordPresentable): String = {
          val path = root.resolve("SPEC-" + selectedreference.identity + "-" + name + ".json")
          Files.writeString(path, _encoded_(data))
          path.toUri.toASCIIString
        }
        val requestpath = _save_("request", described)
        val resultpath = _save_("result", selecteddirection)
        val referencepath = _save_("reference", selectedreference)
        val executionpath = _save_("execution", execution)
        val preparation = V.WorkflowEvidencePreparationInput("DelegateTask", "semantic-continuation", requestpath, resultpath,
          referencepath, executionpath, "normal-development-host", actor.clock.instant().minusSeconds(1).toString,
          "entity:smworkflow.cli.host_evidence", Some("participant"))
        val row = if (installed.nonEmpty) {
          val record = _installed_record_("prepareEvidence", Vector("--input", _encoded_(preparation),
            "--preparationFormat", "legacy-full"), "NativeWorkflowPreparationService")
          record.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        } else {
          val response = _take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = "NativeWorkflowPreparationService", operation = "prepareEvidence", properties = List(
              Property("input", preparation, None), Property("preparationFormat", "legacy-full", None))), actor))
          val record = response match {
            case org.goldenport.protocol.operation.OperationResponse.RecordResponse(value) => value
            case org.goldenport.protocol.operation.OperationResponse.Json(value) =>
              _take(new org.goldenport.record.io.RecordDecoder().json(value.noSpaces))
            case other => fail("Expected complete public preparation: " + other)
          }
          record.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        }
        prepare(setup => setup.createC("host_evidence", row.getString("id").get, row.removeKeys(Set("id"))))
      }
      _provision_(deferred)
      read_value[V.DelegateTaskSemanticContinuationDescriptionResult](_take(_call_("resumeDelegateTaskSemanticContinuation", List(Property("input", deferred, None))))).outcome shouldBe "deferred"
      val afterdefer = read_value[V.DelegateTaskSemanticContinuationDescriptionResult](_take(_call_("describeDelegateTaskSemanticContinuation",
        List(Property("input", V.DelegateTaskSemanticContinuationDescriptionInput(session), None)))))
      afterdefer.target shouldBe described.target
      Given("an independently prepared Human direction bound to this exact original stop")
      _provision_(direction)
      Vector(direction.copy(rationale = "different"),
        direction.copy(upstream = Vector(V.WorkflowArtifactReference("SPEC-other-upstream", "1"))),
        direction.copy(reference = V.WorkflowArtifactReference("SPEC-other-human", "1"))).foreach { invalid =>
        _call_("resumeDelegateTaskSemanticContinuation", List(Property("input", invalid, None))).isSuccess shouldBe false
        _unchanged_()
      }
      if (installed.isEmpty) {
        When("the exact direction is admitted")
        val result = read_value[V.DelegateTaskSemanticContinuationDescriptionResult](_take(_call_("resumeDelegateTaskSemanticContinuation", List(Property("input", direction, None)))))
        result.outcome shouldBe "resumed-awaiting-advance"
      }
    }
    if (installed.nonEmpty) _installed_call_[V.DelegateTaskSemanticContinuationDescriptionResult]("resumeDelegateTaskSemanticContinuation", Vector("--input", _encoded_(direction))).outcome shouldBe "resumed-awaiting-advance"
    def _verify_checkpoint_(suspended: Boolean): Unit =
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
            val oldhistory = target.frontier.hcursor.get[Vector[Json]]("history").toOption.get
            val newhistory = currentJson.hcursor.get[Vector[Json]]("history").toOption.get
            newhistory.take(oldhistory.size) shouldBe oldhistory
            newhistory.size should be > oldhistory.size
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
    def _verify_successor_(complete: V.DelegateTaskView): Unit = {
      complete.issuedWorkContext.get.requiredOperation shouldBe "delegate-task-implementation-capability"
      val json = io.circe.parser.parse(_take(complete.work.get.toJsonStringC)).toOption.get
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
    Then("admission only appends a checkpoint and retains the original work and allowance")
    _verify_checkpoint_(false)
    _with_runtime_(root, "TaskSemantic", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def _call_(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      val repeat = read_value[V.DelegateTaskSemanticContinuationDescriptionResult](_take(_call_("resumeDelegateTaskSemanticContinuation", List(Property("input", direction, None)))))
      repeat.outcome shouldBe "already-resumed"
      _call_("resumeDelegateTaskSemanticContinuation", List(Property("input", direction.copy(rationale = "different"), None))).isSuccess shouldBe false
      if (installed.isEmpty) {
        val issued = read_control_value[V.DelegateTaskView](_take(_call_("advanceDelegateTask", List(Property("sessionId", session, None)))))
        issued.kind shouldBe "Request"
        emittedwork = issued.retainedWork
        read_value[V.DelegateTaskSemanticContinuationDescriptionResult](_take(_call_("resumeDelegateTaskSemanticContinuation",
          List(Property("input", direction, None))))).outcome shouldBe "already-resumed"
        val duplicate = read_control_value[V.DelegateTaskView](_take(_call_("advanceDelegateTask", List(Property("sessionId", session, None)))))
        duplicate.retainedWork shouldBe issued.retainedWork
        val reference = issued.retainedWork.get
        val response = _take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = WorkflowRetainedWork.serviceName, operation = WorkflowRetainedWork.readOperation,
          properties = List(Property("input", reference, None))), actor))
        val detail = read_value[V.WorkflowRetainedWorkReadResult](response)
        val complete = _take(V.DelegateTaskView.createC(detail.response))
        _verify_successor_(complete)
      }
    }
    if (installed.nonEmpty) {
      val issued = _installed_call_[V.DelegateTaskView]("advanceDelegateTask", Vector("--sessionId", session))
      issued.kind shouldBe "Request"
      emittedwork = issued.retainedWork
      val reference = issued.retainedWork.get
      val detail = _take(summon[org.goldenport.convert.ValueReader[V.WorkflowRetainedWorkReadResult]].readC(
        _installed_record_(WorkflowRetainedWork.readOperation, Vector("--input", _encoded_(reference)), WorkflowRetainedWork.serviceName)))
      val complete = _take(V.DelegateTaskView.createC(detail.response))
      _verify_successor_(complete)
      _installed_call_[V.DelegateTaskSemanticContinuationDescriptionResult]("resumeDelegateTaskSemanticContinuation",
        Vector("--input", _encoded_(direction))).outcome shouldBe "already-resumed"
      val duplicate = _installed_call_[V.DelegateTaskView]("advanceDelegateTask", Vector("--sessionId", session))
      duplicate.retainedWork shouldBe issued.retainedWork
    }
    _verify_checkpoint_(true)
    _with_runtime_(root, "TaskSemantic", hostParameters = parameters, componentInstance = "default") { (owner, component, actor, _) =>
      def _call_(name: String, properties: List[Property]) = owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = name, properties = properties), actor)
      read_value[V.DelegateTaskSemanticContinuationDescriptionResult](_take(_call_("resumeDelegateTaskSemanticContinuation",
        List(Property("input", direction, None))))).outcome shouldBe "already-resumed"
      _call_("resumeDelegateTaskSemanticContinuation", List(Property("input", direction.copy(rationale = "changed"), None))).isSuccess shouldBe false
      val reopened = read_control_value[V.DelegateTaskView](_take(_call_("advanceDelegateTask", List(Property("sessionId", session, None)))))
      reopened.retainedWork shouldBe emittedwork
    }
    _verify_checkpoint_(true)
    Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
    Files.readString(root.resolve("repository/docs/phase.md")) shouldBe "Specification-owned original source\n"
    repeated.foreach { _ => _repeated_stop(root, installed, Some((session, direction, emittedwork.get))) }
  }
  /** The failed qualification below is a fixture-owned typed fact, not an
    * executed test. Both Implement completions and the later Repair completion
    * are actual issued/submitted native work on this single retained Session.
    */
  private def _repeated_stop(root: Path, installed: Option[(Path, Path)],
    initial: Option[(String, V.DelegateTaskSemanticContinuationInput, V.WorkflowRetainedWorkReference)],
    collectonly: Boolean = false): Unit = {
    import scala.jdk.CollectionConverters.*
    import java.util.concurrent.TimeUnit
    import org.goldenport.convert.ValueReader
    import org.goldenport.cncf.processexecution.{GitOperation, GitSelectionId, ProcessExecutionCapture,
      ProcessExecutionResult, ProcessExecutionTermination}
    import org.goldenport.protocol.operation.OperationResponse
    val parameters = BoundedWorkExecutionAdmissionFixture.logicalParameters ++ Map(
      "textus.resource.url.file.roots" -> root.toString, "textus.resource.read.max-bytes" -> "16777216")
    val manifest = root.resolve("SPEC-repeated-semantic-fixture.json")
    def _encoded_(value: org.goldenport.record.RecordPresentable): String = _take(value.toRecord().toJsonStringC)
    def _file_(name: String, value: org.goldenport.record.RecordPresentable): String = {
      val path = root.resolve("SPEC-" + name + ".json")
      Files.writeString(path, _encoded_(value))
      path.toUri.toASCIIString
    }
    def _installed_(service: String, operation: String, arguments: Vector[String], success: Boolean = true): Record = {
      val logs = Files.createTempDirectory(root, "SPEC-repeated-call-")
      val stdout = logs.resolve("stdout.json")
      val stderr = logs.resolve("stderr")
      val argv = NativeWorkflowCliFixture.candidateCommand(installed.get._2) ++ Vector(
        org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name + "." + service + "." + operation,
        "--textus.assembly.descriptor=" + root.resolve(".textus/assembly.yaml"),
        "--textus.test.descriptor=" + root.resolve(".textus/test.yaml")) ++ arguments ++ Vector("--json")
      val process = new ProcessBuilder(argv.asJava).directory(root.toFile).redirectOutput(stdout.toFile)
        .redirectError(stderr.toFile).start()
      try {
        process.getOutputStream.close()
        withClue("installed operation timeout: " + stderr) { process.waitFor(300L, TimeUnit.SECONDS) shouldBe true }
        if (success) {
          withClue(Files.readString(stderr)) { process.exitValue() shouldBe 0 }
          _take(new org.goldenport.record.io.RecordDecoder().json(Files.readString(stdout)))
        } else {
          process.exitValue() should not be 0
          (Files.readString(stdout) + Files.readString(stderr)) should include ("409")
          Record.empty
        }
      } finally {
        if (process.isAlive) { process.destroy(); if (!process.waitFor(5L, TimeUnit.SECONDS)) process.destroyForcibly() }
        process.getInputStream.close(); process.getErrorStream.close()
      }
    }
    var retainedsession = ""
    var firstdirection = Option.empty[V.DelegateTaskSemanticContinuationInput]
    var stoppedrevision = 0L
    var stoppedpayload = Option.empty[BoundedWorkRuntime.Payload]
    var repairreference = Option.empty[V.WorkflowArtifactReference]
    var pendingidentity = Option.empty[String]
    if (initial.isEmpty) {
      Given("the same small fixture retained by the old installed runtime before publication")
      Files.isRegularFile(manifest) shouldBe true
      val saved = io.circe.parser.parse(Files.readString(manifest)).toOption.get
      retainedsession = saved.hcursor.get[String]("session").toOption.get
      firstdirection = Some(_take(V.DelegateTaskSemanticContinuationInput.createC(_take(
        new org.goldenport.record.io.RecordDecoder().json(saved.hcursor.get[Json]("firstDirection").toOption.get.noSpaces)))))
      stoppedrevision = saved.hcursor.get[Long]("humanRevision").toOption.get
      repairreference = Some(_take(V.WorkflowArtifactReference.createC(_take(new org.goldenport.record.io.RecordDecoder()
        .json(saved.hcursor.get[Json]("repairResultReference").toOption.get.noSpaces)))))
    }
    val collecteddirection = if (collectonly) {
      Given("the actual complete direction file retained by the successful installed admission")
      val path = root.resolve("SPEC-later-resume-input.json")
      Files.isRegularFile(path) shouldBe true
      Some(_take(V.DelegateTaskSemanticContinuationInput.createC(_take(
        new org.goldenport.record.io.RecordDecoder().json(Files.readString(path))))))
    } else None
    val collectedtarget = collecteddirection.map(direction => _take(R.targetC(direction.target)))
    val trendHost = ThreeWorkflowNormalHostFixture.definition(root)
    val scopedTrendHost = trendHost.copy(goal = trendHost.goal.copy(
      candidatePaths = Vector("docs/phase.md", "docs/trend-extra.md")))
    _with_runtime_(root, "TaskSemantic", if (installed.isEmpty) Some(scopedTrendHost) else None,
      hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def _call_[A: ValueReader](operation: String, properties: List[Property], arguments: Vector[String],
        control: Boolean = false, service: String = "DelegateTaskService"): A =
        if (installed.nonEmpty) _take(summon[ValueReader[A]].readC(_installed_(service, operation, arguments)))
        else {
          val response = _take(owner.executeOperationResponseInContext(Request.of(component = component.name,
            service = service, operation = operation, properties = properties), actor))
          if (control) read_control_value[A](response) else read_value[A](response)
        }
      def _advance_(): V.DelegateTaskView = _call_[V.DelegateTaskView]("advanceDelegateTask",
        List(Property("sessionId", retainedsession, None)), Vector("--sessionId", retainedsession), control = true)
      def _detail_(reference: V.WorkflowRetainedWorkReference): V.DelegateTaskView = {
        val detail = _call_[V.WorkflowRetainedWorkReadResult](WorkflowRetainedWork.readOperation,
          List(Property("input", reference, None)), Vector("--input", _encoded_(reference)), service = WorkflowRetainedWork.serviceName)
        _take(V.DelegateTaskView.createC(detail.response))
      }
      def _describe_(): V.DelegateTaskSemanticContinuationDescriptionResult = {
        val query = V.DelegateTaskSemanticContinuationDescriptionInput(retainedsession)
        _call_[V.DelegateTaskSemanticContinuationDescriptionResult]("describeDelegateTaskSemanticContinuation",
          List(Property("input", query, None)), Vector("--inputResource", _file_("later-description-input", query)))
      }
      def _resume_(direction: V.DelegateTaskSemanticContinuationInput,
        name: String = "later-resume-input"): V.DelegateTaskSemanticContinuationDescriptionResult =
        _call_[V.DelegateTaskSemanticContinuationDescriptionResult]("resumeDelegateTaskSemanticContinuation",
          List(Property("input", direction, None)), Vector("--inputResource", _file_(name, direction)))
      def _verify_successor_(successor: V.DelegateTaskView, original: BoundedWorkRuntime.Payload,
        direction: V.DelegateTaskSemanticContinuationInput): Unit = {
        val raw = io.circe.parser.parse(_take(successor.work.get.toJsonStringC)).toOption.get
        val work = GoalPhaseNativeMetadata.normalizePublicJson(raw).toOption.get.as[S.Work].toOption.get
        work.session shouldBe retainedsession
        work.kind shouldBe "Repair"
        work.state.generation shouldBe 3
        work.state.automaticCount shouldBe 2
        work.state.ordinaryCount shouldBe 2
        work.state.candidate shouldBe original.state.candidate
        work.state.receipts.asJson shouldBe original.state.receipts.asJson
        work.state.asJson shouldBe original.state.copy(stage = "Repair").asJson
        val rawcontext = io.circe.parser.parse(_take(successor.issuedWorkContext.get.context.toJsonStringC)).toOption.get
        val context = GoalPhaseNativeMetadata.normalizePublicJson(rawcontext).toOption.get
        val references = context.hcursor.get[Vector[Json]]("requiredFacts").toOption.get.map { reference =>
          ContextReference(reference.hcursor.get[String]("identity").toOption.get,
            reference.hcursor.get[String]("revision").toOption.get)
        }
        references should contain (ContextReference(direction.reference.identity, direction.reference.revision))
        references should contain (ContextReference("SPEC-later-upstream", "1"))
      }
      def _submit_(view: V.DelegateTaskView, result: V.DelegateTaskWorkResult, reference: V.WorkflowArtifactReference): Unit = {
        val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("SPEC-repeated-implementer"), None,
          Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "SPEC-repeated-implementer", "ordinary")))
        _call_[V.DelegateTaskView]("submitDelegateTaskWorkResult", List(Property("sessionId", retainedsession, None),
          Property("issuedWorkContext", view.issuedWorkContext.get, None), Property("result", result, None),
          Property("resultReference", reference, None), Property("executionEvidence", execution, None)),
          Vector("--sessionId", retainedsession, "--issuedWorkContext", _encoded_(view.issuedWorkContext.get),
            "--result", _encoded_(result), "--resultReference", _encoded_(reference), "--executionEvidence", _encoded_(execution)))
          .kind shouldBe "Submitted"
      }
      def _provision_(description: org.goldenport.record.RecordPresentable,
        direction: org.goldenport.record.RecordPresentable, reference: V.WorkflowArtifactReference, kind: String): Unit = {
        val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
        val preparation = V.WorkflowEvidencePreparationInput("DelegateTask", kind,
          _file_(kind + "-description", description), _file_(kind + "-direction", direction),
          _file_(kind + "-human-reference", reference), _file_(kind + "-human-execution", execution),
          "normal-development-host", actor.clock.instant().minusSeconds(1).toString,
          "entity:smworkflow.cli.host_evidence", Some("participant"))
        val prepared = if (installed.nonEmpty) _installed_("NativeWorkflowPreparationService", "prepareEvidence",
          Vector("--input", _encoded_(preparation), "--preparationFormat", "legacy-full"))
        else _take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "NativeWorkflowPreparationService", operation = "prepareEvidence", properties = List(
            Property("input", preparation, None), Property("preparationFormat", "legacy-full", None))), actor)) match {
          case OperationResponse.RecordResponse(record) => record
          case OperationResponse.Json(json) => _take(new org.goldenport.record.io.RecordDecoder().json(json.noSpaces))
          case other => fail("Expected public evidence preparation: " + other)
        }
        val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        prepare(setup => setup.createC("host_evidence", row.getString("id").get, row.removeKeys(Set("id"))))
      }
      def _checkpoint_(replace: Option[BoundedWorkRuntime.Payload => BoundedWorkRuntime.Payload] = None): Unit =
        prepare { setup => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main",
            if (installed.isEmpty) scopedTrendHost.goal.candidatePaths else Vector("docs/phase.md"), Vector.empty,
            None, None, None, None, None)
          context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(retainedsession))
          selected = setup.core.copy(executionContext = context)
          store <- DelegateTaskNativeRun.storageC(host, definition, selected)
          instances <- store.bindInstancesC()(using context)
          current <- instances.loadC(InstanceIdentity(retainedsession))(using context).flatMap(
            _.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC retained Session is absent")))
          payload <- DelegateTaskNativeRun.retainedPayloadC(store, retainedsession, selected)
          saved <- replace match {
            case Some(update) => DelegateTaskNativeRun.saveC(host, store, current, update(payload), selected)
            case None => Consequence.success(current)
          }
          actual <- DelegateTaskNativeRun.retainedPayloadC(store, retainedsession, selected)
          _ = {
            stoppedrevision = saved.revision.value
            stoppedpayload = Some(actual)
            pendingidentity = saved.suspension.map(_.continuationIdentity.value)
          }
        } yield () }
      initial.foreach { (session, direction, reference) =>
        retainedsession = session
        firstdirection = Some(direction)
        Given("the first admitted direction and its actual pending successor Implement on the same Session")
        val implementation = _detail_(reference)
        val implemented = V.DelegateTaskWorkResult("completed", "SPEC actual semantic source delivery", Vector.empty,
          NonEmptyVector.of(V.DelegateTaskCondition("objective", false, "SPEC delivery evaluation remains pending after Implement"),
            V.DelegateTaskCondition("delivery", false, "SPEC delivery evaluation remains pending after Implement")), Vector.empty, None, None)
        val implementationreference = V.WorkflowArtifactReference("SPEC-repeated-implemented", "1")
        When("that actual Implement is submitted, before installing a specification-owned failed-check checkpoint")
        _submit_(implementation, implemented, implementationreference)
        val raw = io.circe.parser.parse(_take(implementation.work.get.toJsonStringC)).toOption.get
        val work = GoalPhaseNativeMetadata.normalizePublicJson(raw).toOption.get.as[S.Work].toOption.get
        val result = S.Result("completed", implemented.summary, Vector.empty,
          Vector(S.Condition("objective", false, "SPEC delivery evaluation remains pending after Implement"),
            S.Condition("delivery", false, "SPEC delivery evaluation remains pending after Implement")), Vector.empty)
        var actualtree = ""
        prepare { setup => RepositorySyncManagedGitObservation.indexTreeC(setup.core).map(tree => actualtree = tree) }
        // Fixture-owned checkpoint setup: the accepted native completion is
        // preserved, while this known failure and its diagnostic were not run.
        _checkpoint_(Some { payload =>
          val delivered = _take(S.completedC(work, result, ContextReference(implementationreference.identity, "1")))
            .copy(generation = 3, automaticCount = 2, ordinaryCount = 2)
          val candidate = S.Candidate(payload.baseHead, actualtree, Vector.empty, "")
          val stopped = (1 to 4).foldLeft(delivered) { (state, _) =>
            val captured = _take(S.candidateC(state.copy(stage = "Inspect"), candidate))
            val check = captured.selected.head
            val receipt = RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(1),
              "[error] SPEC fixture-owned failure diagnostic; no test executed\n" * 800, "",
              Some(WorkflowSbtValidation.Facts(check.kind, WorkflowSbtValidation.Outcome.Failed, None, None)))
            _take(S.observedC(_take(S.intentC(captured)), receipt))
          }
          stopped.stage shouldBe "Human"
          stopped.repairControl.stop should not be empty
          payload.copy(state = stopped)
        })
        if (installed.isEmpty) {
        Given("the archived known failed receipt is a fixture-owned native checkpoint fact, not an executed check")
        val qualification = stoppedpayload.get
        var archivedfailure = Option.empty[RepositorySyncNativeMergeValidation.Receipt]
        prepare { setup => for {
          context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(retainedsession))
          selected = setup.core.copy(executionContext = context)
          receipt = qualification.state.receipts.head
          bytes = receipt.stdout.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
          process = ProcessExecutionResult(ProcessExecutionTermination.Exited(1),
            ProcessExecutionCapture(bytes, bytes.size.toLong, false), ProcessExecutionCapture(Vector.empty, 0L, false),
            Vector.empty, 1L, "specification-owned-failure")
          check = qualification.state.selected.head
          candidate = qualification.state.candidate.get
          association = WorkflowSbtCapture.Association(selected.component.get.core.instanceId.canonicalKey, retainedsession,
            stoppedrevision, qualification.state.participant, check.identity, check.ordinal, qualification.root,
            qualification.branch, candidate.head, candidate.tree)
          reference <- WorkflowSbtCapture.archiveC(selected, "sm-workflow-validation-artifacts", "validation/SPEC-semantic-failure.json",
            association, receipt.sbt.get, process, org.goldenport.cncf.resource.ResourceReadLimits(WorkflowSbtCapture.maximumArtifactBytes))
          _ = archivedfailure = Some(receipt.copy(processObservation = Some(process), externalCapture = Some(reference)))
        } yield () }
        When("that exact external reference is retained before the real repair direction is described or admitted")
        _checkpoint_(Some(payload => payload.copy(state = payload.state.copy(receipts = Vector(archivedfailure.get)))))
        Then("native reopening hydrates the archived failure and preserves its existing trend observations")
        stoppedpayload.get.state.receipts.head.processObservation shouldBe archivedfailure.get.processObservation
        stoppedpayload.get.state.repairControl shouldBe qualification.state.repairControl
        }
        Given("a real covering Human ContinueRepair admission for that exact recurring fixture trend")
        val repairquery = V.GoalTaskRepairContinuationDescriptionInput(retainedsession)
        val repairdescription = _call_[V.GoalTaskRepairContinuationDescriptionResult]("describeDelegateTaskRepairContinuation",
          List(Property("input", repairquery, None)), Vector("--input", _encoded_(repairquery)))
        val repairdirection = V.GoalTaskRepairContinuationInput(retainedsession, repairdescription.target, "ContinueRepair",
          "SPEC independently admitted continuation of the exact historical trend", "participant",
          V.WorkflowArtifactReference("SPEC-repeated-repair-human", "1"))
        _provision_(repairdescription, repairdirection, repairdirection.reference, "repair-continuation")
        When("the exact historical trend direction is admitted through the ordinary repair operation")
        _call_[V.GoalTaskRepairContinuationDescriptionResult]("resumeDelegateTaskRepair",
          List(Property("input", repairdirection, None)), Vector("--input", _encoded_(repairdirection)))
          .outcome shouldBe "resumed-awaiting-advance"
        When("a separate ordinary Advance issues the next work from that retained admission")
        val issued = _advance_()
        var repair = _detail_(issued.retainedWork.get)
        val repairjson = io.circe.parser.parse(_take(repair.work.get.toJsonStringC)).toOption.get
        val repairwork = GoalPhaseNativeMetadata.normalizePublicJson(repairjson).toOption.get.as[S.Work].toOption.get
        Then("the observed work is Repair retaining the covered trend, failed receipt and earlier direction")
        repairwork.kind shouldBe "Repair"
        repairwork.state.automaticCount shouldBe 2
        if (installed.isEmpty) {
          repairwork.state.receipts.head.externalCapture should not be empty
          repairwork.state.receipts.head.processObservation shouldBe None
        } else repairwork.state.receipts.head.stdout should include ("SPEC fixture-owned")
        if (installed.isEmpty) {
          Given("a native path addition covered by the retained active repair-policy direction")
          val query = V.DelegateTaskScopeCorrectionDescriptionInput(retainedsession,
            repair.issuedWorkContext.get.continuationId, Vector("docs/trend-extra.md"), Vector.empty)
          val description = _call_[V.DelegateTaskScopeCorrectionView]("describeDelegateTaskScopeCorrection",
            List(Property("input", query, None)), Vector.empty)
          val correction = V.DelegateTaskScopeCorrectionInput(retainedsession, query.oldContinuationId,
            description.target, "SPEC-trend-scope-selection", "participant", "SPEC-trend-scope-key",
            V.WorkflowArtifactReference("SPEC-trend-scope-human", "1"))
          val options = Map("input" -> "input", "selection-id" -> correction.selectionId,
            "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> correction.idempotencyKey,
            "selection-collection" -> "entity:smworkflow.cli.goal_selection")
          val selection = _take(org.simplemodeling.textus.smworkflow.preparation.NativeDelegateTaskScopeCorrectionInput
            .prepareC(options, _ => correction.toRecord().toJsonStringC))
          val row = selection.getVector("datastore").get.head.asInstanceOf[Record]
            .getVector("records").get.head.asInstanceOf[Record]
          prepare(_.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id"))))
          val corrected = _call_[V.DelegateTaskScopeCorrectionView]("correctDelegateTaskScope",
            List(Property("inputResource", _file_("trend-scope-correction", correction), None)), Vector.empty)
          corrected.outcome shouldBe "corrected-awaiting-advance"
          repair = _detail_(_advance_().retainedWork.get)
          val scopedJson = io.circe.parser.parse(_take(repair.work.get.toJsonStringC)).toOption.get
          val scopedWork = GoalPhaseNativeMetadata.normalizePublicJson(scopedJson).toOption.get.as[S.Work].toOption.get
          scopedWork.state.input.ownedPaths shouldBe repairwork.state.input.ownedPaths :+ "docs/trend-extra.md"
          scopedWork.state.copy(input = repairwork.state.input) shouldBe repairwork.state
        }
        val unresolved = V.DelegateTaskWorkResult("decision-needed", "SPEC later supplier needs an actual result", Vector.empty,
          NonEmptyVector.of(V.DelegateTaskCondition("objective", false, "SPEC pending supplier"),
            V.DelegateTaskCondition("delivery", false, "SPEC pending supplier")), Vector("SPEC later supplier result is required"), None, None)
        val actualreference = V.WorkflowArtifactReference("SPEC-repeated-repair-decision", "1")
        When("the actual issued Repair completes with decision-needed and ordinary Advance materializes Human")
        _submit_(repair, unresolved, actualreference)
        _advance_().kind shouldBe "Human"
        repairreference = Some(actualreference)
        _checkpoint_()
        Then("the original and later native work remain in one Session with the original direction")
        stoppedpayload.get.delegateSemanticContinuation.get.target shouldBe _take(R.targetC(direction.target)).asJson
        stoppedpayload.get.state.semanticReference shouldBe Some(ContextReference(actualreference.identity, "1"))
        if (installed.nonEmpty) {
          // This selected phase intentionally runs the OLD installed CAR.
          _installed_("DelegateTaskService", "describeDelegateTaskSemanticContinuation", Vector("--input",
            _encoded_(V.DelegateTaskSemanticContinuationDescriptionInput(retainedsession))), success = false)
        }
        Files.writeString(manifest, Json.obj("session" -> retainedsession.asJson,
          "firstDirection" -> io.circe.parser.parse(_encoded_(direction)).toOption.get,
          "humanRevision" -> stoppedrevision.asJson,
          "repairResultReference" -> io.circe.parser.parse(_encoded_(actualreference)).toOption.get).spaces2)
      }
      if (collectonly) {
        Given("the original admitted target and exact first and later directions retained in this same fixture")
        val direction = collecteddirection.get
        val target = collectedtarget.get
        val original = target.business
        direction.sessionId shouldBe retainedsession
        target.sessionId shouldBe retainedsession
        target.frontierRevision shouldBe stoppedrevision
        val first = firstdirection.get
        val firsttarget = _take(R.targetC(first.target))
        val expectedfirst = R.Direction(firsttarget.asJson, first.choice, first.rationale, first.participant,
          ContextReference(first.reference.identity, first.reference.revision),
          first.upstream.map(reference => ContextReference(reference.identity, reference.revision)))
        val expectedlatest = R.Direction(target.asJson, direction.choice, direction.rationale, direction.participant,
          ContextReference(direction.reference.identity, direction.reference.revision),
          direction.upstream.map(reference => ContextReference(reference.identity, reference.revision)))
        original.delegateSemanticContinuation shouldBe Some(expectedfirst)
        original.delegateRepairContinuation should not be empty
        When("the existing native checkpoint and current Git HEAD are read without changing the frontier")
        _checkpoint_()
        val collectionrevision = stoppedrevision
        val current = stoppedpayload.get
        pendingidentity should not be empty
        val pending = pendingidentity.get
        var observedhead = ""
        prepare { setup => RepositorySyncManagedGitObservation.observeC(setup.core).map(observed => observedhead = observed.head) }
        Then("the actual pending Repair retains the original semantic facts, both directions and cumulative allowance")
        collectionrevision should be > target.frontierRevision
        current.state.stage shouldBe "Repair"
        current.state.generation shouldBe 3
        current.state.automaticCount shouldBe 2
        current.state.ordinaryCount shouldBe 2
        current.state.candidate shouldBe original.state.candidate
        current.state.receipts.asJson shouldBe original.state.receipts.asJson
        current.state.asJson shouldBe original.state.copy(stage = "Repair").asJson
        current.delegateSemanticContinuation shouldBe Some(expectedlatest)
        current.delegateSemanticContinuation.get.target.as[R.Target].toOption.get.business.delegateSemanticContinuation shouldBe Some(expectedfirst)
        current.delegateRepairContinuation shouldBe original.delegateRepairContinuation
        current.state.semanticReference shouldBe Some(target.original.resultReference)
        current.state.semanticReference shouldBe Some(ContextReference(repairreference.get.identity, repairreference.get.revision))
        current.root shouldBe original.root
        current.branch shouldBe original.branch
        current.baseHead shouldBe observedhead
        current.baseHead should not be original.baseHead
        When("ordinary installed Advance collects the already pending successor and its retained work")
        val issued = _advance_()
        val successor = _detail_(issued.retainedWork.get)
        Then("the same actual issued Repair exposes the complete required Human and upstream facts")
        issued.kind shouldBe "Request"
        successor.issuedWorkContext.get.continuationId shouldBe pending
        _verify_successor_(successor, original, direction)
        _checkpoint_()
        stoppedrevision shouldBe collectionrevision
        pendingidentity shouldBe Some(pending)
        When("both exact historical directions are recollected from their retained complete inputs")
        val firstcollection = _resume_(first, "collection-first-resume-input")
        val latercollection = _resume_(direction, "collection-later-resume-input")
        Then("both report already-resumed without appending a checkpoint")
        firstcollection.outcome shouldBe "already-resumed"
        latercollection.outcome shouldBe "already-resumed"
        _checkpoint_()
        stoppedrevision shouldBe collectionrevision
        stoppedpayload.get shouldBe current
        When("ordinary Advance is repeated against that still pending work")
        val repeated = _advance_()
        Then("it returns the identical retained work with unchanged native revision and no SBT execution")
        repeated.retainedWork shouldBe issued.retainedWork
        _checkpoint_()
        stoppedrevision shouldBe collectionrevision
        stoppedpayload.get shouldBe current
        pendingidentity shouldBe Some(pending)
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      }
      if (!collectonly && (initial.isEmpty || installed.isEmpty)) {
        Given("a later actual Repair semantic stop with remaining allowance and the first immutable direction")
        val retainedrevision = stoppedrevision
        _checkpoint_()
        stoppedrevision shouldBe retainedrevision
        val original = stoppedpayload.get
        original.state.repairControl.stop should not be empty
        original.delegateRepairContinuation.get.choice shouldBe "ContinueRepair"
        if (installed.isEmpty) {
          Given("specification-owned variants with UNKNOWN, exhausted allowance or a changed historical policy association")
          val invalid = Vector(
            original.copy(state = original.state.copy(receipts = original.state.receipts.map(_.copy(termination = "unknown",
              externalCapture = None, processObservation = None)))),
            original.copy(state = original.state.copy(automaticCount = 10, ordinaryCount = 10, generation = 11)),
            original.copy(state = original.state.copy(repairControl = original.state.repairControl.copy(observations =
              original.state.repairControl.observations.map(_.copy(reference = "SPEC-uncovered-policy"))))),
            original.copy(delegateRepairContinuation = original.delegateRepairContinuation.map(direction => direction.copy(
              target = direction.target.mapObject(fields => fields.add("frontierRevision", (retainedrevision - 1).asJson))))))
          When("each variant is described through the actual native operation")
          invalid.foreach { other =>
            _checkpoint_(Some(_ => other))
            owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskService",
              operation = "describeDelegateTaskSemanticContinuation", properties = List(Property("input",
                V.DelegateTaskSemanticContinuationDescriptionInput(retainedsession), None))), actor).isSuccess shouldBe false
          }
          Then("each refuses without deleting historical policy observations or granting another continuation")
          _checkpoint_(Some(_ => original))
        }
        val expectedrevision = stoppedrevision
        val described = _describe_()
        val target = _take(R.targetC(described.target))
        target.original.work.kind shouldBe "Repair"
        target.original.resultReference shouldBe ContextReference(repairreference.get.identity, repairreference.get.revision)
        target.asJson shouldBe io.circe.parser.parse(described.target).toOption.get
        target.business.asJson shouldBe original.asJson
        if (installed.isEmpty) {
          target.business.state.receipts.head.termination shouldBe "external-unhydrated"
          R.eligible(target.business) shouldBe false
          target.business.delegateRepairContinuation.get.target.as[DelegateTaskRepairContinuation.Target].toOption.get
            .business.state.receipts.head.externalCapture shouldBe original.state.receipts.head.externalCapture
        }
        When("the old exact answer is collected while this newer Human remains pending")
        _resume_(firstdirection.get).outcome shouldBe "already-resumed"
        Then("collection appends nothing and describes the identical newer stop")
        _checkpoint_()
        stoppedrevision shouldBe expectedrevision
        _describe_().target shouldBe described.target
        if (installed.isEmpty) {
          When("a changed historical answer, a crossed new target or an unadmitted new direction is presented")
          val invalid = Vector(firstdirection.get.copy(rationale = "SPEC changed historical direction"),
            firstdirection.get.copy(target = described.target),
            firstdirection.get.copy(target = target.copy(componentInstance = "SPEC-foreign").asJson.noSpaces))
          invalid.foreach { answer =>
            owner.executeOperationResponseInContext(Request.of(component = component.name,
              service = "DelegateTaskService", operation = "resumeDelegateTaskSemanticContinuation",
              properties = List(Property("input", answer, None))), actor).isSuccess shouldBe false
          }
          Then("none appends state or grants the old evidence authority over this new stop")
          _checkpoint_()
          stoppedrevision shouldBe expectedrevision
          _describe_().target shouldBe described.target
        }
        val rationale = "SPEC independently admitted later supplier resolution.\n" +
          ("SPEC fixture-owned explanation of this supplied dependency decision; no process execution or successful test claimed.\n" * 1500) +
          "SPEC end of independently admitted explanation."
        val direction = V.DelegateTaskSemanticContinuationInput(retainedsession, described.target,
          "ContinueAfterDependencyResolution", rationale, "participant",
          V.WorkflowArtifactReference("SPEC-repeated-human-direction", "1"), Vector(V.WorkflowArtifactReference("SPEC-later-upstream", "1")))
        _encoded_(direction).getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be > 131072
        _provision_(described, direction, direction.reference, "semantic-continuation")
        Given("authorized source movement within the same owned file after the retained failed qualification")
        Files.writeString(root.resolve("repository/docs/phase.md"), "SPEC current authorized source after supplier resolution\n")
        var currenthead = ""
        prepare { setup => for {
          _ <- WorkflowManagedProcessExecution.stageGoalCandidateC(setup.core, 1).flatMap(_.valueC)
          selection <- GitSelectionId.parseC("goal-step-1")
          _ <- WorkflowManagedProcessExecution.executeGitC(setup.core, GitOperation.CommitSelected(selection)).flatMap(_.valueC)
          observed <- RepositorySyncManagedGitObservation.observeC(setup.core)
          _ = { currenthead = observed.head; currenthead should not be original.baseHead }
        } yield () }
        if (installed.isEmpty) {
        Given("the actual retained external fixture failure after authorized active HEAD movement")
        prepare { setup => for {
          context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(retainedsession))
          selected = setup.core.copy(executionContext = context)
          receipt = original.state.receipts.head
          process = receipt.processObservation.get
          candidate = original.state.candidate.get
          association = receipt.externalCapture.get.association
          specimen = original.copy(baseHead = currenthead)
          wire = specimen.asJson.as[BoundedWorkRuntime.Payload].toOption.get
          _ = When("the capture reopens against the retained candidate after actual authorized HEAD movement")
          hydrated <- DelegateTaskNativeRun.hydrateStateC(wire.state, retainedsession, selected, Some(wire))
          _ = {
            Then("the failed capture rehydrates through its old candidate, while wrong candidate, root and branch still refuse")
            hydrated.receipts.head.processObservation shouldBe Some(process)
            hydrated.receipts.head.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
            wire.baseHead should not be association.baseHead
            Vector(wire.copy(root = "/SPEC-foreign-root"), wire.copy(branch = "SPEC-foreign-branch"),
              wire.copy(state = wire.state.copy(candidate = Some(candidate.copy(head = "b" * 40)))),
              wire.copy(state = wire.state.copy(candidate = Some(candidate.copy(tree = "c" * 40))))).foreach { crossed =>
              DelegateTaskNativeRun.hydrateStateC(crossed.state, retainedsession, selected, Some(crossed)).isSuccess shouldBe false
            }
          }
        } yield () }
        }
        When("the independently prepared new direction is admitted through ordinary Resource input")
        _resume_(direction).outcome shouldBe "resumed-awaiting-advance"
        Then("only one checkpoint is appended, preserving candidate, failure, base, counts and both directions")
        _checkpoint_()
        stoppedrevision shouldBe expectedrevision + 1
        val admitted = stoppedpayload.get
        admitted.state shouldBe original.state.copy(stage = "Repair")
        admitted.baseHead shouldBe original.baseHead
        admitted.delegateSemanticContinuation.get.reference shouldBe ContextReference(direction.reference.identity, "1")
        admitted.delegateSemanticContinuation.get.target.as[R.Target].toOption.get.business.delegateSemanticContinuation shouldBe original.delegateSemanticContinuation
        Vector(firstdirection.get, direction).foreach(d => _resume_(d).outcome shouldBe "already-resumed")
        _checkpoint_()
        stoppedrevision shouldBe expectedrevision + 1
        When("a separate ordinary Advance selects the next native work")
        val issued = _advance_()
        val successor = _detail_(issued.retainedWork.get)
        Then("it issues Repair from current source while all earlier qualification remains historical")
        _verify_successor_(successor, original, direction)
        _checkpoint_()
        stoppedpayload.get.baseHead shouldBe currenthead
        val afterissuerevision = { _checkpoint_(); stoppedrevision }
        Vector(firstdirection.get, direction).foreach(d => _resume_(d).outcome shouldBe "already-resumed")
        _advance_().retainedWork shouldBe issued.retainedWork
        _checkpoint_()
        stoppedrevision shouldBe afterissuerevision
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      }
    }
  }

  private def _qualify_recorded_incomplete(): Unit = {
    import org.goldenport.convert.ValueReader
    Given("one specification-owned destination with an archived ended Incomplete fixture and no repair trend stop")
    val root = Files.createTempDirectory(Files.createDirectories(
      Path.of("target/sm-workflow/work/delegate-semantic-incomplete").toAbsolutePath), "SPEC-").toRealPath()
    val parameters = BoundedWorkExecutionAdmissionFixture.logicalParameters ++ Map(
      "textus.resource.url.file.roots" -> root.toString, "textus.resource.read.max-bytes" -> "16777216")
    val contract = V.DelegateTaskContract("SPEC-requester", "SPEC-destination", "SPEC-requester", "SPEC-component", "destination",
      "documentation", NonEmptyVector.of("dispatch", "implement", "validate", "review", "commit", "return", "adopt"), Vector.empty,
      NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-authority", "1")), None, Vector.empty, "SPEC-owned direction", None, None, None)
    val input = V.DelegateTaskInput("sm-workflow", "SPEC-incomplete-semantic", "SPEC preserve ordinary Repair decisions",
      NonEmptyVector.one("docs/phase.md"), NonEmptyVector.one("test"), NonEmptyVector.one("objective"),
      NonEmptyVector.one(V.WorkflowArtifactReference("SPEC-original", "1")), false, "documentation", "off", 10,
      "direct-instruction", Vector.empty, contract, Vector.empty, None)
    val originalHost = ThreeWorkflowNormalHostFixture.definition(root)
    val expandedHost = originalHost.copy(goal = originalHost.goal.copy(
      candidatePaths = Vector("docs/phase.md", "docs/extra.md", "docs/other.md")))
    _with_runtime_(root, "TaskSemantic", Some(expandedHost), hostParameters = parameters, componentInstance = "default") { (owner, component, actor, prepare) =>
      def _encoded_(value: org.goldenport.record.RecordPresentable): String = _take(value.toRecord().toJsonStringC)
      def _call_[A: ValueReader](name: String, input: org.goldenport.record.RecordPresentable,
        service: String = "DelegateTaskService"): A = read_value[A](_take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = service, operation = name,
            properties = List(Property("input", input, None))), actor)))
      val selected = _take(NativeDelegateTaskStartInput.prepareC(Map("input" -> "input", "selection-id" -> "SPEC-selection",
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "SPEC-start",
        "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
      prepare { setup => for {
        _ <- setup.setupC(_setup_commands_(root, "TaskSemantic").size)
        row = selected.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        _ <- setup.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
      } yield () }
      val started = read_value[V.DelegateTaskView](_take(owner.executeOperationResponseInContext(Request.of(
        component = component.name, service = "DelegateTaskService", operation = "startDelegateTask", properties = List(
          Property("input", input, None), Property("selectionId", "SPEC-selection", None),
          Property("participant", "participant", None), Property("idempotencyKey", "SPEC-start", None))), actor)))
      val session = started.sessionId
      Given("a distinct SPEC Session started while the selected worktree is clean")
      val foreignInput = input.copy(requestIdentity = "SPEC-foreign-scope-session")
      val foreignSelection = _take(NativeDelegateTaskStartInput.prepareC(Map("input" -> "input",
        "selection-id" -> "SPEC-foreign-selection", "participant" -> "participant", "origin" -> "ExplicitHuman",
        "idempotency-key" -> "SPEC-foreign-start", "selection-collection" -> "entity:smworkflow.cli.goal_selection"),
        _ => foreignInput.toRecord().toJsonStringC))
      val foreignRow = foreignSelection.getVector("datastore").get.head.asInstanceOf[Record]
        .getVector("records").get.head.asInstanceOf[Record]
      prepare(_.createC("goal_selection", foreignRow.getString("id").get, foreignRow.removeKeys(Set("id"))))
      val foreignStarted = read_value[V.DelegateTaskView](_take(owner.executeOperationResponseInContext(Request.of(
        component = component.name, service = "DelegateTaskService", operation = "startDelegateTask", properties = List(
          Property("input", foreignInput, None), Property("selectionId", "SPEC-foreign-selection", None),
          Property("participant", "participant", None), Property("idempotencyKey", "SPEC-foreign-start", None))), actor)))
      foreignStarted.sessionId should not be session
      def _checkpoint_(change: Option[BoundedWorkRuntime.Payload => BoundedWorkRuntime.Payload] = None):
          (InstanceRecord, BoundedWorkRuntime.Payload) = {
        var result = Option.empty[(InstanceRecord, BoundedWorkRuntime.Payload)]
        prepare { setup => for {
          definition <- DelegateTaskRuntimeCatalog.loadC()
          host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", expandedHost.goal.candidatePaths, Vector.empty, None, None, None, None, None)
          context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
          selected = setup.core.copy(executionContext = context)
          store <- DelegateTaskNativeRun.storageC(host, definition, selected)
          instances <- store.bindInstancesC()(using context)
          current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
            _.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC Session absent")))
          revision = if (current.suspension.nonEmpty) current.revision.value - 1L
            else if (current.causation.value.startsWith("standalone-completed:"))
              current.causation.value.stripPrefix("standalone-completed:").takeWhile(_ != ':').toLongOption.get
            else current.revision.value
          payload <- DelegateTaskNativeRun.loadPayloadC(store, session, revision, selected)
          next = change.fold(payload)(_(payload))
          saved <- if (change.isEmpty) Consequence.success(current)
            else DelegateTaskNativeRun.saveC(host, store, current, next, selected)
          _ = result = Some(saved -> next)
        } yield () }
        result.get
      }
      // These process facts are explicitly fixture-owned, archived through File
      // UoW and subsequently reopened by the production native loader.
      prepare { setup => for {
        definition <- DelegateTaskRuntimeCatalog.loadC()
        host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
          "sm-workflow", root.resolve("repository").toString, "main", expandedHost.goal.candidatePaths, Vector.empty, None, None, None, None, None)
        context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
        selected = setup.core.copy(executionContext = context)
        store <- DelegateTaskNativeRun.storageC(host, definition, selected)
        instances <- store.bindInstancesC()(using context)
        current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC Start absent")))
        original <- DelegateTaskNativeRun.loadPayloadC(store, session, current.revision.value, selected)
        observed <- RepositorySyncManagedGitObservation.observeC(setup.core)
        tree <- RepositorySyncManagedGitObservation.indexTreeC(setup.core)
        candidate = S.Candidate(observed.head, tree, Vector.empty, "SPEC fixture source")
        check = original.state.selected.head
        receipt = TerminatedValidationFixture.receipt(check)
        association = WorkflowSbtCapture.Association(selected.component.get.core.instanceId.canonicalKey, session,
          current.revision.next.value, original.state.participant, check.identity, check.ordinal,
          original.root, original.branch, candidate.head, candidate.tree)
        reference <- WorkflowSbtCapture.archiveC(selected, "sm-workflow-validation-artifacts", "validation/SPEC-incomplete-semantic.json",
          association, receipt.sbt.get, receipt.processObservation.get,
          org.goldenport.cncf.resource.ResourceReadLimits(WorkflowSbtCapture.maximumArtifactBytes))
        state = original.state.copy(stage = "Wait", candidate = Some(candidate),
          receipts = Vector(receipt.copy(externalCapture = Some(reference))), automaticCount = 2, ordinaryCount = 2, generation = 3)
        _ <- DelegateTaskNativeRun.saveC(host, store, current, original.copy(state = state,
          validationCaptureReservation = Some(BoundedWorkRuntime.ValidationCaptureReservation(association,
            reference.workspace, reference.path, WorkflowSbtCapture.maximumArtifactBytes))), selected)
      } yield () }
      def _provision_(name: String, kind: String, request: org.goldenport.record.RecordPresentable,
        answer: org.goldenport.record.RecordPresentable, reference: V.WorkflowArtifactReference): Unit = {
        def _file_(suffix: String, value: org.goldenport.record.RecordPresentable): String = {
          val path = root.resolve(name + "-" + suffix + ".json")
          Files.writeString(path, _encoded_(value))
          path.toUri.toASCIIString
        }
        val execution = V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None)
        val input = V.WorkflowEvidencePreparationInput("DelegateTask", kind, _file_("request", request),
          _file_("answer", answer), _file_("reference", reference), _file_("execution", execution), "normal-development-host",
          actor.clock.instant().minusSeconds(1).toString, "entity:smworkflow.cli.host_evidence", Some("participant"))
        val response = _take(owner.executeOperationResponseInContext(Request.of(component = component.name,
          service = "NativeWorkflowPreparationService", operation = "prepareEvidence", properties = List(
            Property("input", input, None), Property("preparationFormat", "legacy-full", None))), actor))
        val prepared = response match {
          case org.goldenport.protocol.operation.OperationResponse.RecordResponse(record) => record
          case org.goldenport.protocol.operation.OperationResponse.Json(json) => _take(new org.goldenport.record.io.RecordDecoder().json(json.noSpaces))
          case other => fail("Expected public preparation: " + other)
        }
        val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
        prepare(_.createC("host_evidence", row.getString("id").get, row.removeKeys(Set("id"))))
      }
      def _advance_(): V.DelegateTaskView = read_control_value[V.DelegateTaskView](_take(owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = "advanceDelegateTask",
          properties = List(Property("sessionId", session, None))), actor)))
      def _detail_(issued: V.DelegateTaskView): V.DelegateTaskView = _take(V.DelegateTaskView.createC(
        _call_[V.WorkflowRetainedWorkReadResult](WorkflowRetainedWork.readOperation, issued.retainedWork.get,
          WorkflowRetainedWork.serviceName).response))
      def _work_(view: V.DelegateTaskView): S.Work = GoalPhaseNativeMetadata.normalizePublicJson(
        io.circe.parser.parse(_take(view.work.get.toJsonStringC)).toOption.get).toOption.get.as[S.Work].toOption.get
      val before = _checkpoint_()._2
      before.state.repairControl.stop shouldBe None
      val repairdescription = _call_[V.GoalTaskRepairContinuationDescriptionResult]("describeDelegateTaskRepairContinuation",
        V.GoalTaskRepairContinuationDescriptionInput(session))
      val repairanswer = V.GoalTaskRepairContinuationInput(session, repairdescription.target, "RepairBeforeRetry",
        "SPEC retain ended Incomplete while resolving the ordinary Repair dependency", "participant",
        V.WorkflowArtifactReference("SPEC-incomplete-repair-human", "1"))
      _provision_("repair", "repair-continuation", repairdescription, repairanswer, repairanswer.reference)
      When("the exact RepairBeforeRetry is admitted and separate ordinary Advance issues actual Repair")
      _call_[V.GoalTaskRepairContinuationDescriptionResult]("resumeDelegateTaskRepair", repairanswer).outcome shouldBe "resumed-awaiting-advance"
      val repair = _detail_(_advance_())
      _work_(repair).kind shouldBe "Repair"
      _checkpoint_()._2.validationCaptureReservation shouldBe None
      val decision = V.DelegateTaskWorkResult("decision-needed", "SPEC ordinary Repair needs an upstream result", Vector.empty,
        NonEmptyVector.one(V.DelegateTaskCondition("objective", false, "SPEC upstream unresolved")),
        Vector("SPEC supply the actual dependency result"), None, None)
      val resultreference = V.WorkflowArtifactReference("SPEC-incomplete-repair-decision", "1")
      val execution = V.WorkflowExecutionEvidence(Vector(resultreference), Some("SPEC-repairer"), None,
        Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "SPEC-repairer", "ordinary")))
      When("that actual Repair submits decision-needed and ordinary Advance materializes Human")
      _take(owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskService",
        operation = "submitDelegateTaskWorkResult", properties = List(Property("sessionId", session, None),
          Property("issuedWorkContext", repair.issuedWorkContext.get, None), Property("result", decision, None),
          Property("resultReference", resultreference, None), Property("executionEvidence", execution, None))), actor))
      _advance_().kind shouldBe "Human"
      val original = _checkpoint_()._2
      Then("the original Incomplete and unchanged counters survive the semantic stop")
      original.state.receipts.asJson shouldBe before.state.receipts.asJson
      original.state.generation shouldBe before.state.generation
      original.state.automaticCount shouldBe before.state.automaticCount
      original.state.ordinaryCount shouldBe before.state.ordinaryCount
      original.state.repairControl.stop shouldBe None
      Given("a fixture variant that changes the historical Human reference despite an empty trend stop")
      val forged = original.copy(delegateRepairContinuation = original.delegateRepairContinuation.map(direction =>
        direction.copy(reference = ContextReference("SPEC-unadmitted-history", "1"))))
      _checkpoint_(Some(_ => forged))
      When("semantic description verifies the actual native repair admission")
      val refused = owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskService",
        operation = "describeDelegateTaskSemanticContinuation", properties = List(Property("input",
          V.DelegateTaskSemanticContinuationDescriptionInput(session), None))), actor)
      Then("an unattested history cannot use the ended Incomplete exception")
      refused.isSuccess shouldBe false
      _checkpoint_(Some(_ => original))
      val description = _call_[V.DelegateTaskSemanticContinuationDescriptionResult]("describeDelegateTaskSemanticContinuation",
        V.DelegateTaskSemanticContinuationDescriptionInput(session))
      val target = _take(R.targetC(description.target))
      target.original.work.kind shouldBe "Repair"
      target.original.resultReference shouldBe ContextReference(resultreference.identity, resultreference.revision)
      target.business.state.receipts.last.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Incomplete
      val answer = V.DelegateTaskSemanticContinuationInput(session, description.target, "ContinueAfterDependencyResolution",
        "SPEC actual upstream dependency supplied", "participant", V.WorkflowArtifactReference("SPEC-semantic-human", "1"),
        Vector(V.WorkflowArtifactReference("SPEC-upstream", "1")))
      val deferred = answer.copy(choice = "Defer", reference = V.WorkflowArtifactReference("SPEC-semantic-defer", "1"))
      When("independent evidence first selects Defer for the existing semantic operation")
      _provision_("semantic-defer", "semantic-continuation", description, deferred, deferred.reference)
      val revision = _checkpoint_()._1.revision
      val deferredresult = _call_[V.DelegateTaskSemanticContinuationDescriptionResult]("resumeDelegateTaskSemanticContinuation", deferred)
      Then("Defer changes no checkpoint or accounting")
      deferredresult.outcome shouldBe "deferred"
      _checkpoint_()._1.revision shouldBe revision
      _checkpoint_()._2 shouldBe original
      When("independent evidence admits the existing semantic direction and separate Advance issues successor Repair")
      _provision_("semantic", "semantic-continuation", description, answer, answer.reference)
      _call_[V.DelegateTaskSemanticContinuationDescriptionResult]("resumeDelegateTaskSemanticContinuation", answer).outcome shouldBe "resumed-awaiting-advance"
      val admittedrevision = _checkpoint_()._1.revision
      _call_[V.DelegateTaskSemanticContinuationDescriptionResult]("resumeDelegateTaskSemanticContinuation", answer).outcome shouldBe "already-resumed"
      _checkpoint_()._1.revision shouldBe admittedrevision
      var issued = _advance_()
      var successor = _work_(_detail_(issued))
      Then("the same Task retains Incomplete diagnostics and unchanged counters without another check")
      successor.session shouldBe session
      successor.kind shouldBe "Repair"
      successor.state.asJson shouldBe original.state.copy(stage = "Repair").asJson
      _advance_().retainedWork shouldBe issued.retainedWork
      _checkpoint_()._2.delegateRepairContinuation shouldBe original.delegateRepairContinuation
      Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
      Given("an admitted path addition while the recorded Incomplete repair direction remains active")
      val incompleteIssue = _detail_(issued)
      val incompleteQuery = V.DelegateTaskScopeCorrectionDescriptionInput(session,
        incompleteIssue.issuedWorkContext.get.continuationId, Vector("docs/extra.md"), Vector.empty)
      val incompleteDescription = _call_[V.DelegateTaskScopeCorrectionView]("describeDelegateTaskScopeCorrection", incompleteQuery)
      val incompleteCorrection = V.DelegateTaskScopeCorrectionInput(session, incompleteQuery.oldContinuationId,
        incompleteDescription.target, "SPEC-incomplete-scope-selection", "participant", "SPEC-incomplete-scope-key",
        V.WorkflowArtifactReference("SPEC-incomplete-scope-human", "1"))
      val incompleteOptions = Map("input" -> "input", "selection-id" -> incompleteCorrection.selectionId,
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> incompleteCorrection.idempotencyKey,
        "selection-collection" -> "entity:smworkflow.cli.goal_selection")
      val incompleteSelection = _take(org.simplemodeling.textus.smworkflow.preparation.NativeDelegateTaskScopeCorrectionInput
        .prepareC(incompleteOptions, _ => incompleteCorrection.toRecord().toJsonStringC))
      val incompleteRow = incompleteSelection.getVector("datastore").get.head.asInstanceOf[Record]
        .getVector("records").get.head.asInstanceOf[Record]
      prepare(_.createC("goal_selection", incompleteRow.getString("id").get, incompleteRow.removeKeys(Set("id"))))
      val incompleteFile = root.resolve("SPEC-incomplete-scope-correction.json")
      Files.writeString(incompleteFile, _encoded_(incompleteCorrection))
      _take(owner.executeOperationResponseInContext(Request.of(component = component.name,
        service = "DelegateTaskService", operation = "correctDelegateTaskScope",
        properties = List(Property("inputResource", incompleteFile.toUri.toASCIIString, None))), actor))
      Files.writeString(root.resolve("repository/docs/extra.md"), "SPEC admitted incomplete repair scope\n")
      val correctedIncomplete = _detail_(_advance_())
      val correctedWork = _work_(correctedIncomplete)
      correctedWork.state.input.ownedPaths shouldBe successor.state.input.ownedPaths :+ "docs/extra.md"
      val secondReference = V.WorkflowArtifactReference("SPEC-incomplete-second-decision", "1")
      _take(owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskService",
        operation = "submitDelegateTaskWorkResult", properties = List(Property("sessionId", session, None),
          Property("issuedWorkContext", correctedIncomplete.issuedWorkContext.get, None), Property("result", decision, None),
          Property("resultReference", secondReference, None),
          Property("executionEvidence", execution.copy(references = Vector(secondReference)), None))), actor))
      _advance_().kind shouldBe "Human"
      val secondDescription = _call_[V.DelegateTaskSemanticContinuationDescriptionResult]("describeDelegateTaskSemanticContinuation",
        V.DelegateTaskSemanticContinuationDescriptionInput(session))
      val secondTarget = _take(R.targetC(secondDescription.target))
      secondTarget.business.delegateRepairContinuation shouldBe original.delegateRepairContinuation
      secondTarget.business.state.receipts.last.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Incomplete
      secondTarget.business.state.input shouldBe correctedWork.state.input
      val secondAnswer = answer.copy(target = secondDescription.target,
        reference = V.WorkflowArtifactReference("SPEC-incomplete-second-human", "1"))
      _provision_("incomplete-second", "semantic-continuation", secondDescription, secondAnswer, secondAnswer.reference)
      _call_[V.DelegateTaskSemanticContinuationDescriptionResult]("resumeDelegateTaskSemanticContinuation", secondAnswer).outcome shouldBe "resumed-awaiting-advance"
      issued = _advance_()
      successor = _work_(_detail_(issued))
      successor.state shouldBe secondTarget.business.state.copy(stage = "Repair")
      Given("a normal completed Repair followed by a new scoped candidate and a fixture-owned formal Failed check")
      val completionreference = V.WorkflowArtifactReference("SPEC-incomplete-repair-completed", "1")
      val required = successor.state.input.submissionConditions.getOrElse(successor.state.input.conditions).toSet
      val report = successor.state.input.conditions.map(identity => S.Condition(identity, required(identity),
        if (required(identity)) "SPEC supplied scoped source" else "SPEC later validation and delivery remain unresolved"))
      val completed = V.DelegateTaskWorkResult("completed", "SPEC completed ordinary Repair", Vector.empty,
        NonEmptyVector.fromVectorUnsafe(report.map(condition => V.DelegateTaskCondition(condition.identity,
          condition.satisfied, condition.detail))), Vector.empty, None, None)
      val completionevidence = execution.copy(references = Vector(completionreference))
      _take(owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskService",
        operation = "submitDelegateTaskWorkResult", properties = List(Property("sessionId", session, None),
          Property("issuedWorkContext", _detail_(issued).issuedWorkContext.get, None), Property("result", completed, None),
          Property("resultReference", completionreference, None), Property("executionEvidence", completionevidence, None))), actor))
      // Materialize this accepted completion and install only the following
      // qualification fixture; no selected process is claimed or executed.
      val completion = S.Result("completed", completed.summary, Vector.empty,
        report, Vector.empty)
      _checkpoint_(Some { payload =>
        val next = _take(S.completedC(successor.copy(state = payload.state), completion,
          ContextReference(completionreference.identity, completionreference.revision)))
        payload.copy(state = next)
      })
      Files.writeString(root.resolve("repository/docs/phase.md"), "SPEC later scoped source after completed Repair\n")
      var candidate = Option.empty[S.Candidate]
      prepare { setup => for {
        _ <- WorkflowManagedProcessExecution.stageGoalCandidateC(setup.core, 1).flatMap(_.valueC)
        observed <- RepositorySyncManagedGitObservation.observeC(setup.core)
        tree <- RepositorySyncManagedGitObservation.indexTreeC(setup.core)
        _ = candidate = Some(S.Candidate(observed.head, tree, Vector("docs/phase.md"), "SPEC later scoped candidate"))
      } yield () }
      _checkpoint_(Some { payload =>
        val captured = _take(S.candidateC(payload.state, candidate.get))
        val check = captured.selected.head
        val failed = RepositorySyncNativeMergeValidation.Receipt(check.identity, "exited", Some(1),
          "[error] SPEC fixture-owned later formal failure", "", Some(WorkflowSbtValidation.Facts(check.kind,
            WorkflowSbtValidation.Outcome.Failed, None, None)))
        payload.copy(state = _take(S.observedC(_take(S.intentC(captured)), failed)))
      })
      val oldIssue = _advance_()
      val oldRepair = _detail_(oldIssue)
      val oldWork = _work_(oldRepair)
      val correctionQuery = V.DelegateTaskScopeCorrectionDescriptionInput(session,
        oldRepair.issuedWorkContext.get.continuationId, Vector("docs/other.md"), Vector.empty)
      val correctionDescription = _call_[V.DelegateTaskScopeCorrectionView]("describeDelegateTaskScopeCorrection", correctionQuery)
      val correction = V.DelegateTaskScopeCorrectionInput(session, correctionQuery.oldContinuationId,
        correctionDescription.target, "SPEC-scope-selection", "participant", "SPEC-scope-key",
        V.WorkflowArtifactReference("SPEC-scope-human", "1"))
      val options = Map("input" -> "input", "selection-id" -> correction.selectionId,
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> correction.idempotencyKey,
        "selection-collection" -> "entity:smworkflow.cli.goal_selection")
      val selection = _take(org.simplemodeling.textus.smworkflow.preparation.NativeDelegateTaskScopeCorrectionInput
        .prepareC(options, _ => correction.toRecord().toJsonStringC))
      val row = selection.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
      prepare(_.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id"))))
      val correctionFile = root.resolve("SPEC-scope-correction.json")
      Files.writeString(correctionFile, _encoded_(correction))
      val corrected = read_value[V.DelegateTaskScopeCorrectionView](_take(owner.executeOperationResponseInContext(
        Request.of(component = component.name, service = "DelegateTaskService", operation = "correctDelegateTaskScope",
          properties = List(Property("inputResource", correctionFile.toUri.toASCIIString, None))), actor)))
      corrected.outcome shouldBe "corrected-awaiting-advance"
      Files.writeString(root.resolve("repository/docs/other.md"), "SPEC admitted repair inventory\n")
      val correctedIssue = _advance_()
      val laterrepair = _detail_(correctedIssue)
      val laterwork = _work_(laterrepair)
      laterrepair.issuedWorkContext.get.continuationId should not be oldRepair.issuedWorkContext.get.continuationId
      laterwork.state.input.ownedPaths shouldBe oldWork.state.input.ownedPaths :+ "docs/other.md"
      laterwork.state.input.sourceReferences shouldBe oldWork.state.input.sourceReferences :+
        ContextReference(correction.reference.identity, correction.reference.revision)
      laterwork.state.copy(input = oldWork.state.input) shouldBe oldWork.state
      Given("a distinct SPEC Session and the first Session's genuine retained supersession")
      prepare { setup => for {
        definition <- DelegateTaskRuntimeCatalog.loadC()
        host = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
          "sm-workflow", root.resolve("repository").toString, "main", expandedHost.goal.candidatePaths,
          Vector.empty, None, None, None, None, None)
        context <- NativeWorkflowSessionInput.contextC(setup.executionContext, Some(session))
        selected = setup.core.copy(executionContext = context)
        store <- DelegateTaskNativeRun.storageC(host, definition, selected)
        instances <- store.bindInstancesC()(using context)
        source <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC source Session absent")))
        foreign <- instances.loadC(InstanceIdentity(foreignStarted.sessionId))(using context).flatMap(
          _.map(Consequence.success).getOrElse(Consequence.stateConflict("SPEC foreign Session absent")))
        payload <- DelegateTaskNativeRun.retainedPayloadC(store, session, selected)
        genuine <- DelegateTaskDeliveryProjection.checkpointsC(store, source, source, payload, selected, 0L)
        _ = {
          genuine.map(_._1.target.oldContinuationId) should contain (oldRepair.issuedWorkContext.get.continuationId)
          DelegateTaskDeliveryProjection.checkpointsC(store, foreign, source, payload, selected, 0L).isSuccess shouldBe false
        }
      } yield () }
      laterwork.kind shouldBe "Repair"
      laterwork.state.generation shouldBe original.state.generation + 1
      laterwork.state.automaticCount shouldBe original.state.automaticCount + 1
      laterwork.state.ordinaryCount shouldBe original.state.ordinaryCount + 1
      laterwork.state.candidate should not be original.state.candidate
      laterwork.state.repairControl.stop shouldBe None
      val laterreference = V.WorkflowArtifactReference("SPEC-later-ordinary-repair-decision", "1")
      When("the later ordinary Repair returns decision-needed while the old recorded Incomplete pointer remains historical")
      _take(owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskService",
        operation = "submitDelegateTaskWorkResult", properties = List(Property("sessionId", session, None),
          Property("issuedWorkContext", laterrepair.issuedWorkContext.get, None), Property("result", decision, None),
          Property("resultReference", laterreference, None),
          Property("executionEvidence", execution.copy(references = Vector(laterreference)), None))), actor))
      _advance_().kind shouldBe "Human"
      val stoppedAfterCorrection = _checkpoint_()._2
      Given("unrecorded scope and a foreign historical direction")
      val forgedInputs = Vector(
        stoppedAfterCorrection.copy(state = stoppedAfterCorrection.state.copy(input =
          stoppedAfterCorrection.state.input.copy(ownedPaths = stoppedAfterCorrection.state.input.ownedPaths :+ "docs/unadmitted.md"))),
        stoppedAfterCorrection.copy(delegateSemanticContinuation = stoppedAfterCorrection.delegateSemanticContinuation.map { prior =>
          val target = _take(R.targetC(prior.target.noSpaces))
          prior.copy(target = target.copy(sessionId = "SPEC-foreign-session").asJson)
        }))
      forgedInputs.foreach { forged =>
        _checkpoint_(Some(_ => forged))
        owner.executeOperationResponseInContext(Request.of(component = component.name, service = "DelegateTaskService",
          operation = "describeDelegateTaskSemanticContinuation", properties = List(Property("input",
            V.DelegateTaskSemanticContinuationDescriptionInput(session), None))), actor).isSuccess shouldBe false
        _checkpoint_(Some(_ => stoppedAfterCorrection))
      }
      val laterdescription = _call_[V.DelegateTaskSemanticContinuationDescriptionResult]("describeDelegateTaskSemanticContinuation",
        V.DelegateTaskSemanticContinuationDescriptionInput(session))
      val latertarget = _take(R.targetC(laterdescription.target))
      Then("the normal Failed receipt permits continuation and the original Incomplete admission remains immutable ancestry")
      latertarget.original.resultReference shouldBe ContextReference(laterreference.identity, laterreference.revision)
      latertarget.business.delegateRepairContinuation shouldBe original.delegateRepairContinuation
      latertarget.business.state.receipts.last.sbt.get.outcome shouldBe WorkflowSbtValidation.Outcome.Failed
      latertarget.business.delegateSemanticContinuation shouldBe stoppedAfterCorrection.delegateSemanticContinuation
      latertarget.business.state.input shouldBe laterwork.state.input
      _call_[V.DelegateTaskSemanticContinuationDescriptionResult]("resumeDelegateTaskSemanticContinuation", answer).outcome shouldBe "already-resumed"
      latertarget.business.state.generation shouldBe original.state.generation + 1
      val lateranswer = answer.copy(target = laterdescription.target, reference = V.WorkflowArtifactReference("SPEC-later-semantic-human", "1"))
      _provision_("later-semantic", "semantic-continuation", laterdescription, lateranswer, lateranswer.reference)
      _call_[V.DelegateTaskSemanticContinuationDescriptionResult]("resumeDelegateTaskSemanticContinuation", lateranswer).outcome shouldBe "resumed-awaiting-advance"
      _checkpoint_()._2.delegateRepairContinuation shouldBe original.delegateRepairContinuation
      val finalIssue = _advance_()
      val finalWork = _work_(_detail_(finalIssue))
      finalWork.state shouldBe latertarget.business.state.copy(stage = "Repair")
      finalWork.state.generation shouldBe original.state.generation + 1
      _call_[V.DelegateTaskSemanticContinuationDescriptionResult]("resumeDelegateTaskSemanticContinuation", answer).outcome shouldBe "already-resumed"
      _call_[V.DelegateTaskSemanticContinuationDescriptionResult]("resumeDelegateTaskSemanticContinuation", lateranswer).outcome shouldBe "already-resumed"
      _advance_().retainedWork shouldBe finalIssue.retainedWork
      Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
    }
  }

  "Managed DelegateTask semantic continuation" should {
    if (!isolated_repeated_semantic) {
      "preserve the original semantic stop across admission, separate Advance and reopen" in { qualify(None) }
      "retain ended Incomplete through RepairBeforeRetry and an actual Repair decision-needed continuation" in {
        _qualify_recorded_incomplete()
      }
    }
    "retain both actual semantic stops and independent directions in the same Session" in {
      val container = Files.createTempDirectory(Files.createDirectories(
        Path.of("target/sm-workflow/work/delegate-semantic-repeated").toAbsolutePath), "SPEC-")
      qualify(None, Some("before" -> container.resolve("fixture")))
    }
  }
}

/** Explicit selection of the repeated scenario preserves earlier suite results.
  * @since Nov. 19, 2026
  * @version Nov. 19, 2026
  */
@org.scalatest.DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class DelegateTaskRepeatedSemanticContinuationOperationSpec extends DelegateTaskSemanticContinuationOperationSpec {
  override protected def isolated_repeated_semantic: Boolean = true
}
