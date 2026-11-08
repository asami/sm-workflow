package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceIdentity
import org.goldenport.protocol.{Property, Request}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import SplitPhaseExplicitPartitionFixture.take

/** Public raw control and explicit native detail consumers, including a new
  * runtime over the same retained store. Fixture authorship is not execution.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  * The suite identity follows the frozen reference-continuation check selection.
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class WorkflowResponseArtifactReferencesSpec extends AnyWordSpec with GivenWhenThen with ThreeWorkflowManagedRepositoryFixture {
  "Retained Workflow work" should {
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
  }
}
