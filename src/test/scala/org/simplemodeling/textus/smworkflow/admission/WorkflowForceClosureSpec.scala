/**
 * @since   Oct. 30, 2026
 * @version Oct. 30, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.preparation.{NativeGoalTaskStartInput, NativeWorkflowForceClosurePreparation}
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms as Programs
import org.simplemodeling.textus.smworkflow.value as V

/** Specification-owned Human evidence only; no actual user Session is closed. */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class WorkflowForceClosureSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  private def take[A](value: Consequence[A]): A = value.toOption.getOrElse(fail(value.toString))
  private def json(record: InstanceRecord): Json = parser.parse(take(WorkflowInstanceRecordJsonV1.encodeC(record))).toOption.get
  override protected def process_plan(root: Path, scenario: String, goal: ThreeWorkflowRuntimeParameters.Goal): Json =
    TypedGitHostPlanFixture.plan(Programs.Target(root.resolve("repository"), "main", "origin", "main",
      root.resolve("upstream.git").toString), kinds = TypedGitHostPlanFixture.observations)

  "Human forced closure" should {
    "append only an attributable terminal, retain pending work and refuse ordinary progress and delayed results" in {
      val root = Files.createTempDirectory(Files.createDirectories(Path.of("target/sm-workflow/work/force-closure").toAbsolutePath), "episode-").toRealPath()
      val input = V.GoalTaskInput("sm-workflow", "force-closure-spec", "Specification unfinished implementation",
        NonEmptyVector.one("docs/phase.md"), NonEmptyVector.one("test"), NonEmptyVector.one("objective"),
        NonEmptyVector.one(V.WorkflowArtifactReference("specification-direct-request", "1")), false,
        "documentation", "off", 10, "direct-instruction", Vector.empty)
      val selection = take(NativeGoalTaskStartInput.prepareC(Map("input" -> "input", "selection-id" -> "closure-selection",
        "participant" -> "participant", "origin" -> "ExplicitHuman", "idempotency-key" -> "closure-start",
        "selection-collection" -> "entity:smworkflow.cli.goal_selection"), _ => input.toRecord().toJsonStringC))
      val facts = _collection_("force_closures").print
      val plan = ThreeWorkflowNormalHostFixture.storagePlan.mapObject { obj =>
        val records = obj("records").get.asArray.get :+ Json.obj("componentId" -> Json.fromString(
          org.simplemodeling.textus.smworkflow.SmWorkflowComponent.componentId.name),
          "storeName" -> Json.fromString("workflow"), "collection" -> Json.fromString(facts))
        obj.add("records", Json.fromValues(records))
      }
      val parameters = BoundedWorkExecutionAdmissionFixture.parameters ++ Map(
        WorkflowForceClosureService.collectionKey -> facts,
        "textus.subsystem.storage.plan" -> plan.noSpaces)
      _with_runtime_(root, "ForcedClosure", hostParameters = parameters) { (owner, component, actor, project) =>
        project(call => call.setupC(_setup_commands_(root, "ForcedClosure").size).flatMap { _ =>
          val row = selection.getVector("datastore").get.head.asInstanceOf[Record].getVector("records").get.head.asInstanceOf[Record]
          call.createC("goal_selection", row.getString("id").get, row.removeKeys(Set("id")))
        })
        def call(service: String, operation: String, properties: List[Property]) = owner.executeOperationResponseInContext(
          Request.of(component = component.name, service = service, operation = operation, properties = properties), actor)
        def task[A: org.goldenport.convert.ValueReader](operation: String, properties: List[Property]): A =
          read_value[A](take(call("GoalTaskService", operation, properties)))
        def current(id: String): InstanceRecord = {
          var observed = Option.empty[InstanceRecord]
          project(call => for {
            storage <- call.taskStorageC()
            instances <- storage.bindInstancesC()(using call.executionContext)
            record <- instances.loadC(InstanceIdentity(id))(using call.executionContext)
            _ = observed = record
          } yield ())
          observed.get
        }
        val started = task[V.GoalTaskView]("startGoalTask", List(Property("input", input, None),
          Property("selectionId", "closure-selection", None), Property("participant", "participant", None),
          Property("idempotencyKey", "closure-start", None)))
        val pending = task[V.GoalTaskView]("advanceGoalTask", List(Property("sessionId", started.sessionId, None)))
        pending.kind shouldBe "Request"
        val before = current(started.sessionId)
        before.suspension.nonEmpty shouldBe true
        val descriptionInput = V.WorkflowForceClosureDescriptionInput("GoalTask", started.sessionId)
        def describe() = read_value[V.WorkflowForceClosureView](take(call("WorkflowForceClosureService", "describeWorkflowForceClosure",
          List(Property("input", descriptionInput, None)))))
        val described = describe()
        val reference = V.WorkflowArtifactReference("specification-human-forced-close", "1")
        val answer = V.WorkflowForceClosureAnswer(described.target, "ForceClose", "Stop this specification run without accepting unfinished implementation",
          Vector("Implementation not returned", "Validation, review, commit and publication not performed"), "NotAccepted", true, "participant", reference)
        def close(value: V.WorkflowForceClosureAnswer) = call("WorkflowForceClosureService", "forceCloseWorkflow", List(Property("input", value, None)))

        Given("a real native pending fixture WorkOrder, without any independently provisioned Human direction")
        close(answer).isSuccess shouldBe false
        json(current(started.sessionId)) shouldBe json(before)
        project(call => for {
          prepared <- NativeWorkflowForceClosurePreparation.prepareC(described, answer, reference,
            V.WorkflowExecutionEvidence(Vector(reference), Some("participant"), None, None), "participant", "normal-development-host",
            call.executionContext.clock.instant().minusSeconds(1))
          _ <- call.createC("host_evidence", prepared.recordIdentity, prepared.record)
        } yield ())
        When("the public operation consumes the independently retained specification-owned direction")
        val closed = read_value[V.WorkflowForceClosureView](take(close(answer)))
        val after = current(started.sessionId)
        Then("only a terminal is appended, while every original native entry and WorkOrder fact remains")
        closed.kind shouldBe "ForcedClosed"
        closed.normalCompletion shouldBe false
        closed.closure.get should include (answer.reason)
        closed.closure.get should include ("NotAccepted")
        after.history.dropRight(1) shouldBe before.history
        after.revision shouldBe before.revision.next
        after.suspension shouldBe None
        after.lifecycle shouldBe Lifecycle.Completed
        take(GoalPhaseNativeObservation.projectC(after)).workflowState shouldBe "forced-closed"
        take(GoalPhaseNativeObservation.projectC(after)).attention shouldBe "unfinished"
        after.correlation shouldBe before.correlation
        take(WorkflowForceClosure.referenceC(after)) shouldBe ContextReference(reference.identity, reference.revision)
        // The original independently admitted input may later be retired. The
        // accepted direction must remain a workflow-owned historical fact.
        project(call => call.withTaskClaimsC { _ =>
          given org.goldenport.cncf.context.ExecutionContext = call.executionContext
          val collection = _collection_("host_evidence")
          call.executionContext.dataStoreSpace.dataStore(collection).flatMap(_.delete(collection,
            DataStore.StringEntryId(NativeExternalEvidence.recordIdentity(NativeExternalEvidence.Purpose.WorkflowForceClosure,
              ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)))))
        })
        close(answer).isSuccess shouldBe true
        val collected = describe()
        collected.kind shouldBe "ForcedClosed"
        collected.closure shouldBe closed.closure
        json(current(started.sessionId)) shouldBe json(after)
        close(answer.copy(reason = "Different direction under the same reference")).isSuccess shouldBe false
        call("GoalTaskService", "advanceGoalTask", List(Property("sessionId", started.sessionId, None))).isSuccess shouldBe false
        val result = V.GoalTaskWorkResult("completed", "Late result must remain unaccepted", Vector.empty,
          NonEmptyVector.one(V.GoalTaskCondition("objective", true, "Specification-owned late result")), Vector.empty, None, None)
        val late = call("GoalTaskService", "submitGoalTaskWorkResult", List(Property("sessionId", started.sessionId, None),
          Property("result", result, None), Property("resultReference", reference, None),
          Property("executionEvidence", V.WorkflowExecutionEvidence(Vector(reference), Some("specification-implementer"), None, None), None),
          Property("issuedWorkContext", pending.issuedWorkContext.get, None)))
        late.isSuccess shouldBe false
        late.toString should include ("Workflow was force-closed by Human direction")
        json(current(started.sessionId)) shouldBe json(after)
        WorkflowForceClosure.requireOpenC(after).isSuccess shouldBe false
        // No source work, check result, semantic Submit, commit or publication
        // is fabricated to obtain the terminal above.
      }
    }

    "keep Human acceptance separate and bind exact reason, unfinished work, issuer, reference and event time" in {
      val target = WorkflowForceClosure.Target(WorkflowForceClosure.schema, "fixture-component", "GoalPhase", "fixture-session",
        "fixture-human", Json.obj("nativeHistory" -> Json.arr("failed".asJson, "UNKNOWN".asJson)))
      val reference = ContextReference("fixture-source", "1")
      val direction = WorkflowForceClosure.Direction(target, "ForceClose", "Accept retained work with known unfinished validation",
        Vector("Validation failed", "Prior process outcome UNKNOWN"), "AcceptedByHuman", true, "fixture-human", reference)
      WorkflowForceClosure.validateC(direction).isSuccess shouldBe true
      WorkflowForceClosure.validateC(direction.copy(choice = "Complete")).isSuccess shouldBe false
      WorkflowForceClosure.validateC(direction.copy(acknowledgeUncertainEffects = false)).isSuccess shouldBe false
      WorkflowForceClosure.validateC(direction.copy(acceptance = "ValidationSucceeded")).isSuccess shouldBe false
      WorkflowForceClosure.decodeTarget(target.asJson.deepMerge(Json.obj("extra" -> true.asJson))).isLeft shouldBe true
      val purpose = NativeExternalEvidence.Purpose.WorkflowForceClosure
      val lookup = ThreeWorkflowExternalEvidencePorts.key(reference.identity, reference.revision)
      val now = Instant.parse("2026-10-30T00:00:00Z")
      val binding = WorkflowForceClosure.binding(direction)
      val evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity, lookup, reference.identity,
        reference.revision, "fixture-host", "fixture-human", binding, now.minusSeconds(1).toString)
      val declaration = NativeExternalEvidence.Declaration("workflow", null, "fixture-host")
      def observe(value: NativeExternalEvidence.Evidence, selected: Json = binding) =
        NativeExternalEvidence.observeC(value, declaration, purpose, lookup, selected, now)
      observe(evidence).isSuccess shouldBe true
      observe(evidence.copy(issuer = "other-host")).isSuccess shouldBe false
      observe(evidence.copy(reference = "other-human-message")).isSuccess shouldBe false
      observe(evidence.copy(participant = "other-human")).isSuccess shouldBe false
      observe(evidence.copy(occurredAt = now.plusSeconds(1).toString)).isSuccess shouldBe false
      observe(evidence, WorkflowForceClosure.binding(direction.copy(unfinished = Vector("changed scope")))).isSuccess shouldBe false
      WorkflowForceClosure.selectBinding(binding.deepMerge(Json.obj("ordinarySuccess" -> true.asJson))).isLeft shouldBe true
    }
  }
}
