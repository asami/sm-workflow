package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import org.simplemodeling.textus.smworkflow.preparation.{NativeGoalTaskStartInput, NativeStandaloneFixStartInput}
import SplitPhaseExplicitPartitionFixture.take

/** Public raw control and explicit native detail consumers, including a new
  * runtime over the same retained store. Fixture authorship is not execution.
  * @since   Nov.  8, 2026
  * @version Nov. 23, 2026
  * The suite identity follows the frozen reference-continuation check selection.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class WorkflowResponseArtifactReferencesSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  "Retained Workflow work" should {
    "return the saved completion projection on the first validation terminal response" in {
      Given("a fixture-owned validation checkpoint with one successful receipt and no CommonEvidence installation")
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/retained-work").toAbsolutePath), "terminal-").toRealPath()
      val checks = Vector(GoalPhaseNativeValidation.Check("compile", 1, WorkflowSbtValidation.Kind.Compile))
      val input = V.StandaloneFixInput("sm-workflow", "terminal-projection", "Qualify the selected candidate",
        NonEmptyVector.one("src"), NonEmptyVector.one("compile"), NonEmptyVector.one("behavior"),
        NonEmptyVector.one(V.WorkflowArtifactReference("fixture-request", "1")), false, "validation", "off", 10)
      val original = ThreeWorkflowNormalHostFixture.definition(root)
      val host = original.copy(goal = original.goal.copy(candidatePaths = input.ownedPaths.toVector,
        stepValidation = original.goal.stepValidation.copy(checks = checks)))
      val prepared = take(NativeStandaloneFixStartInput.prepareC(Map("input" -> "selected.json", "selection-id" -> "terminal-selection",
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "terminal-start",
        "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
      _with_runtime_(root, "Equal", Some(host), BoundedWorkExecutionAdmissionFixture.parameters) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(root, "Equal").size).flatMap { _ =>
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          call.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        })
        def _invoke_(operation: String, properties: List[Property]) = take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "StandaloneFixService", operation = operation, properties = properties), actor))
        val started = read_control_value[V.StandaloneFixView](_invoke_("startStandaloneFix", List(Property("input", input, None),
          Property("selectionId", "terminal-selection", None), Property("participant", "participant", None),
          Property("idempotencyKey", "terminal-start", None))))
        val receipt = RepositorySyncNativeMergeValidation.Receipt("compile", "exited", Some(0), "fixture success", "",
          Some(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Outcome.Succeeded, None, None)))
        var checkpoint = Option.empty[org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord]
        prepare { call => for {
          definition <- StandaloneFixRuntimeCatalog.loadC()
          h = BoundedWorkRuntime.Host(_configuration_, _collection_("instances"), _collection_("continuations"),
            "sm-workflow", root.resolve("repository").toString, "main", host.goal.candidatePaths, checks,
            None, None, None, None, None)
          store <- StandaloneFixNativeRun.storageC(h, definition, call.core)
          instances <- store.bindInstancesC()(using call.core.executionContext)
          current <- instances.loadC(InstanceIdentity(started.sessionId))(using call.core.executionContext)
            .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("Fixture Start is absent")))
          payload <- StandaloneFixNativeRun.loadPayloadC(store, started.sessionId, current.revision.value, call.core)
          observed <- call.observeC()
          tree <- RepositorySyncManagedGitObservation.indexTreeC(call.core)
          state = payload.state.copy(stage = "Assess", candidate = Some(StandaloneFixExecution.Candidate(observed.head, tree, Vector.empty, "")),
            receipts = Vector(receipt))
          saved <- StandaloneFixNativeRun.saveC(h, store, current, payload.copy(state = state), call.core)
          _ = { checkpoint = Some(saved) }
        } yield () }
        When("the first public Advance assesses and saves the terminal checkpoint")
        val terminal = read_control_value[V.StandaloneFixView](_invoke_("advanceStandaloneFix", List(Property("sessionId", started.sessionId, None))))
        val reference = terminal.retainedMaterials.find(_.fieldName == "state").get.reference
        val detail = read_control_value[V.WorkflowRetainedSnapshotReadResult](take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = WorkflowRetainedWork.serviceName,
            operation = WorkflowRetainedSnapshot.readOperation, properties = List(Property("input", reference, None))), actor)))
        import StandaloneFixExecution.given
        val state = take(GoalPhaseNativeMetadata.normalizePublicJson(DevelopmentOperationalView.json(detail.material))
          .flatMap(_.as[StandaloneFixExecution.State].left.map(_.message)).fold(Consequence.stateConflict(_), Consequence.success))
        Then("the first response references the exact saved warning and preserves the fixture receipt and repair counters")
        terminal.kind shouldBe "Terminal"
        terminal.sessionId shouldBe started.sessionId
        state.projectionWarning shouldBe Some("Common Evidence projection is not installed")
        state.receipts shouldBe Vector(receipt)
        state.automaticCount shouldBe 0
        state.ordinaryCount shouldBe 0
        var terminalrecord = checkpoint
        prepare { call => for {
          definition <- StandaloneFixRuntimeCatalog.loadC()
          current <- CallScopedWorkflowInstances.managed(_configuration_, definition, _collection_("instances"), _collection_("continuations"))
            .loadForCallC(_configuration_, InstanceIdentity(started.sessionId), call.core)
            .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("Fixture terminal is absent")))
          _ = { current.history.take(checkpoint.get.history.size) shouldBe checkpoint.get.history; terminalrecord = Some(current) }
        } yield () }
        When("the same Session is collected again")
        val recollected = read_control_value[V.StandaloneFixView](_invoke_("advanceStandaloneFix", List(Property("sessionId", started.sessionId, None))))
        Then("the same retained revision and history remain without SBT or publication replay")
        recollected shouldBe terminal
        prepare { call => for {
          definition <- StandaloneFixRuntimeCatalog.loadC()
          current <- CallScopedWorkflowInstances.managed(_configuration_, definition, _collection_("instances"), _collection_("continuations"))
            .loadForCallC(_configuration_, InstanceIdentity(started.sessionId), call.core)
            .flatMap(_.map(Consequence.success).getOrElse(Consequence.stateConflict("Fixture terminal is absent")))
          _ = { current.history shouldBe terminalrecord.get.history; current.revision shouldBe terminalrecord.get.revision }
        } yield () }
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        Files.exists(root.resolve("publication-count.txt")) shouldBe false
      }
    }

    "publish a bounded decision-needed result and retain Human state without inventing a completion route" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/retained-work").toAbsolutePath), "human-").toRealPath()
      val input = V.GoalTaskInput("sm-workflow", "retained-human", "SPECIFICATION-OWNED unresolved adopted objective",
        NonEmptyVector.one("docs/phase.md"), NonEmptyVector.one("test"), NonEmptyVector.one("objective"),
        NonEmptyVector.one(V.WorkflowArtifactReference("specification-original-request", "1")), false,
        "documentation", "off", 10, "direct-instruction", Vector.empty, None)
      val prepared = take(NativeGoalTaskStartInput.prepareC(Map("input" -> "selected.json", "selection-id" -> "human-selection",
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "human-start",
        "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
      var historical = Option.empty[V.WorkflowRetainedSnapshotReference]
      var human = Option.empty[V.WorkflowRetainedSnapshotReference]
      var session = ""
      var original = Option.empty[Record]
      _with_runtime_(root, "RetainedHuman", hostParameters = BoundedWorkExecutionAdmissionFixture.logicalParameters) {
        (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(root, "RetainedHuman").size).flatMap { _ =>
          val row = prepared.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          call.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        })
        def invoke(operation: String, properties: List[Property]) = take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "GoalTaskService", operation = operation, properties = properties), actor))
        val started = read_control_value[V.GoalTaskView](invoke("startGoalTask", List(Property("input", input, None),
          Property("selectionId", "human-selection", None), Property("participant", "participant", None),
          Property("idempotencyKey", "human-start", None))))
        session = started.sessionId
        val issued = read_value[V.GoalTaskView](invoke("advanceGoalTask", List(Property("sessionId", session, None))))
        original = issued.state
        val result = V.GoalTaskWorkResult("decision-needed", "SPECIFICATION-OWNED business meaning remains unresolved",
          Vector.empty, NonEmptyVector.one(V.GoalTaskCondition("objective", false, "No acceptance is claimed")),
          Vector("Resolve the adopted objective"), None, None)
        val reference = V.WorkflowArtifactReference("specification-decision-needed", "1")
        val evidence = V.WorkflowExecutionEvidence(Vector(reference), Some("specification-human-worker"), None,
          Some(V.WorkflowExecutionObservation("DELEGATED", "bounded-spec-caller", "specification-human-worker", "ordinary")))
        When("one genuine issued result is submitted through the public owner")
        val submitted = read_control_value[V.GoalTaskView](invoke("submitGoalTaskWorkResult", List(
          Property("sessionId", session, None), Property("issuedWorkContext", issued.issuedWorkContext.get, None),
          Property("result", result, None), Property("resultReference", reference, None), Property("executionEvidence", evidence, None))))
        Then("Submit publishes its actual original public state as an exact retained reference")
        submitted.kind shouldBe "Submitted"
        submitted.state shouldBe empty
        historical = Some(submitted.retainedMaterials.find(_.fieldName == "state").get.reference)
        val next = read_control_value[V.GoalTaskView](invoke("advanceGoalTask", List(Property("sessionId", session, None))))
        Then("the bounded Human checkpoint is compact and has no issued Decision or invented completion operation")
        next.kind shouldBe "Human"
        next.state shouldBe empty
        next.submitOperation shouldBe empty
        next.issuedWorkContext shouldBe empty
        next.retainedWork shouldBe empty
        human = Some(next.retainedMaterials.find(_.fieldName == "state").get.reference)
        read_control_value[V.GoalTaskView](invoke("advanceGoalTask", List(Property("sessionId", session, None)))) shouldBe next
      }
      When("a fresh runtime reads both exact native checkpoints without replaying Submit")
      _with_runtime_(root, "RetainedHuman", hostParameters = BoundedWorkExecutionAdmissionFixture.logicalParameters) {
        (owner, component, actor, _) =>
        def read(reference: V.WorkflowRetainedSnapshotReference) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = WorkflowRetainedWork.serviceName,
            operation = WorkflowRetainedSnapshot.readOperation, properties = List(Property("input", reference, None))), actor)
        val prior = read_control_value[V.WorkflowRetainedSnapshotReadResult](take(read(historical.get)))
        val next = read_control_value[V.WorkflowRetainedSnapshotReadResult](take(read(human.get)))
        prior.material shouldBe original.get
        next.material.getString("stage") shouldBe Some("Human")
        next.material.getString("message") shouldBe Some("SPECIFICATION-OWNED business meaning remains unresolved")
        import StandaloneFixExecution.given
        def state(record: Record) = take(GoalPhaseNativeMetadata.normalizePublicJson(DevelopmentOperationalView.json(record))
          .flatMap(_.as[StandaloneFixExecution.State].left.map(_.message)).fold(Consequence.stateConflict(_), Consequence.success))
        val before = state(prior.material)
        val after = state(next.material)
        after.input shouldBe before.input
        after.receipts shouldBe before.receipts
        after.automaticCount shouldBe before.automaticCount
        after.ordinaryCount shouldBe before.ordinaryCount
        after.commit shouldBe before.commit
        after.delivery shouldBe before.delivery
        val selected = take(WorkflowRetainedSnapshot.fromPublicC(human.get))
        read(WorkflowRetainedSnapshot.publicReference(selected.copy(instanceRevision = (selected.instanceRevision.toLong + 1).toString)))
          .isSuccess shouldBe false
        read(WorkflowRetainedSnapshot.publicReference(selected.copy(sessionId = "foreign-session"))).isSuccess shouldBe false
        read(WorkflowRetainedSnapshot.publicReference(selected.copy(owner = "DelegateTaskWorkflow"))).isSuccess shouldBe false
        read(WorkflowRetainedSnapshot.publicReference(selected.copy(componentInstance = "foreign-component"))).isSuccess shouldBe false
        read(WorkflowRetainedSnapshot.publicReference(selected.copy(workflowRevision = "foreign-revision"))).isSuccess shouldBe false
        val recollected = read_control_value[V.GoalTaskView](take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "GoalTaskService", operation = "advanceGoalTask",
            properties = List(Property("sessionId", session, None))), actor)))
        recollected.kind shouldBe "Human"
        recollected.retainedMaterials.find(_.fieldName == "state").get.reference shouldBe human.get
        Files.exists(root.resolve("sbt-executions.txt")) shouldBe false
        Files.exists(root.resolve("publication-count.txt")) shouldBe false
      }
    }

    "keep a growing plan out of ordinary control and read its exact issued revision after a fresh runtime" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/retained-work").toAbsolutePath), "fresh-")
      val host = ThreeWorkflowNormalHostFixture.definition(root)
      val selection = _goal_selection_(host.goal.input, "retained-plan", "retained-entry")
      val large = "Later retained explanation 日本語 \"escaped\". " * 30000
      val plan = GoalPhaseOrderedProgression.Plan(selection.selectionId, "1", host.goal.input.phaseIdentity,
        host.goal.input.repositoryIdentity, Vector(
          GoalPhaseOrderedProgression.Step("first", "Implement the selected first boundary", host.goal.candidatePaths, Vector.empty, Vector("test")),
          GoalPhaseOrderedProgression.Step("later", large, host.goal.candidatePaths, Vector("first"), Vector("test"))), Vector("test"), 0)
      var reference = Option.empty[V.WorkflowRetainedWorkReference]
      var issued = Option.empty[V.WorkflowIssuedWorkContext]
      var revision = 0L
      Given("a complete retained Plan with large unrelated later work and an ordinary first assignment")
      _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) =>
        prepare(call => call.setupC(_setup_commands_(root, "Goal").size).flatMap(_ =>
          call.createC("goal_selection", NativeWorkflowStartBusinessKey(NativeWorkflowStartInput.Goal(host.goal.input),
            selection.participantIdentity), Record.data(ProfileSelectionJsonV1.storageField ->
              take(ProfileSelectionJsonV1.encodeStorageC(selection)))).flatMap(_ =>
            call.createC("goal_plans", selection.selectionId, Record.data(GoalPhaseSelectedPlanJsonV1.storageField ->
              take(GoalPhaseSelectedPlanJsonV1.encodeStorageC(GoalPhaseNativeRun.SelectedPlan(selection.selectionId,
                selection.participantIdentity, plan))))))))
        def invoke(operation: String, properties: List[Property]) = take(owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = "GoalPhaseService", operation = operation, properties = properties), actor))
        val start = read_control_value[V.GoalPhaseStartResult](invoke("startGoalPhase", NativeWorkflowOperationInputFixture.goal(host.goal.input)))
        When("ordinary Advance returns its raw public control record")
        val raw = invoke("advanceGoalPhase", NativeWorkflowOperationInputFixture.advance(start.sessionId))
        val control = read_control_value[V.GoalPhaseAdvanceResult](raw)
        Then("the actual context and finite summary are inline while the complete Plan remains with its native issued owner")
        control.kind shouldBe "Request"
        control.work shouldBe empty
        control.retainedWork should not be empty
        control.controlSummary.get.getRecord("work").get.getString("kind") shouldBe Some("ImplementStep")
        take(control.toRecord().toJsonStringC).getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be < 32768
        reference = control.retainedWork
        issued = control.issuedWorkContext
        prepare(call => call.goalCurrentC(InstanceIdentity(start.sessionId)).map(current => { revision = current.revision.value; () }))
      }
      When("a separate runtime reads the exact retained reference without Start, Advance or accepted Submit replay")
      _with_runtime_(root, "Goal", Some(host)) { (owner, component, actor, prepare) =>
        val selected = reference.get
        def read(value: V.WorkflowRetainedWorkReference) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = WorkflowRetainedWork.serviceName,
            operation = WorkflowRetainedWork.readOperation, properties = List(Property("input", value, None))), actor)
        val detail = read_control_value[V.WorkflowRetainedWorkReadResult](take(read(selected)))
        val response = take(V.GoalPhaseAdvanceResult.createC(detail.response))
        Then("the full selected Plan and genuine issued context are unchanged, and reads add no native history")
        detail.availability shouldBe "available"
        response.work.get.selectedPlan.steps.last.purpose shouldBe large
        response.issuedWorkContext shouldBe issued
        prepare(call => call.goalCurrentC(InstanceIdentity(selected.sessionId)).map(current => {
          current.revision.value shouldBe revision; ()
        }))
        val internal = take(WorkflowRetainedWork.fromPublicC(selected))
        read(WorkflowRetainedWork.publicReference(internal.copy(identity = internal.identity + ":missing"))).isSuccess shouldBe false
        read(WorkflowRetainedWork.publicReference(internal.copy(revision = (internal.revision.toLong + 1).toString))).isSuccess shouldBe false
        read(WorkflowRetainedWork.publicReference(internal.copy(componentInstance = "foreign-instance"))).isSuccess shouldBe false
        read(WorkflowRetainedWork.publicReference(internal.copy(workflowRevision = "foreign-revision"))).isSuccess shouldBe false
      }
    }

    "resolve only exact selected snapshot descriptors and preserve serialized JSON representation" in {
      Given("one explicit immutable native snapshot reference and a multibyte escaped complete payload")
      val reference = WorkflowRetainedSnapshot.Reference(WorkflowRetainedSnapshot.schema, "component", "RepositorySyncWorkflow",
        "revision", "session", "session:native-receipt", "1", "2", "purpose-progress", "serialized-json")
      val selected = Json.obj("source" -> Json.fromString("日本語 \"escaped\""))
      val descriptor = Json.obj("target" -> Json.obj("progressSnapshot" -> Json.obj("retainedSnapshot" ->
        DevelopmentOperationalView.json(WorkflowRetainedSnapshot.publicReference(reference).toRecord()))))
      var reads = Vector.empty[String]
      When("the owning transport expands that selected descriptor before the original business decoder")
      val restored = take(WorkflowRetainedSnapshot.expandTextC(descriptor.noSpaces, resource => {
        reads = reads :+ resource.toString
        Consequence.success(selected.noSpaces)
      }))
      Then("the original complete serialized UTF-8 field is restored without truncation or latest substitution")
      io.circe.parser.parse(restored).toOption.get.hcursor.downField("target").get[String]("progressSnapshot").toOption shouldBe Some(selected.noSpaces)
      reads.size shouldBe 1
      take(WorkflowRetainedSnapshot.decodeResourceC(WorkflowRetainedSnapshot.resource(reference))) shouldBe reference
      WorkflowRetainedSnapshot.decodeResourceC(WorkflowRetainedSnapshot.resource(reference) + "x").isSuccess shouldBe false
      WorkflowRetainedSnapshot.expandTextC(descriptor.noSpaces, _ => Consequence.stateConflict("selected native material unavailable"))
        .isSuccess shouldBe false
    }

    "expand serialized snapshot descriptors returned in the installed CLI's JSON spelling" in {
      Given("the public RepositorySync preparation view with a selected snapshot descriptor")
      val reference = WorkflowRetainedSnapshot.Reference(WorkflowRetainedSnapshot.schema, "component", "RepositorySyncWorkflow",
        "revision", "session", "session:native-receipt", "1", "2", "purpose-progress", "serialized-json")
      val public = DevelopmentOperationalView.json(WorkflowRetainedSnapshot.publicReference(reference).toRecord())
      val snapshot = Json.obj("effects" -> Json.arr(Json.obj("retained_value" -> Json.Null)))
      val description = Json.obj("target" -> Json.obj("progress_snapshot" -> Json.obj("retained_snapshot" -> public)))
      When("normal Workflow Resource reads expand the exact returned reference")
      val restored = take(WorkflowRetainedSnapshot.expandTextC(description.noSpaces, _ => Consequence.success(snapshot.noSpaces)))
      Then("the surrounding CLI field names and the opaque snapshot contents remain exact")
      io.circe.parser.parse(restored).toOption.get.hcursor.downField("target").get[String]("progress_snapshot").toOption shouldBe
        Some(snapshot.noSpaces)
    }
  }
}
