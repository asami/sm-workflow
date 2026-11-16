package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.parser
import io.circe.syntax.*
import java.nio.file.{Files, Path}
import org.goldenport.cncf.workflow.WorkflowExecutionProfileParameters
import org.goldenport.record.RecordPresentable
import org.scalatest.DoNotDiscover
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value as V
import NativeWorkflowCliFixture as F
import SplitPhaseExplicitPartitionFixture.take

/** Requires a separately selected installed executable and component. All
  * directions are SPECIFICATION-OWNED fixture data, not a Human event. */
@DoNotDiscover
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.RuntimeTestDeclaration])
final class NativeGoalTaskScopeCorrectionCliSpec extends AnyWordSpec with Matchers {
  private lazy val executable: Path = {
    val path = Path.of(sys.props.get("smworkflow.spec.installedCommand").orElse(sys.env.get("SM_WORKFLOW_SPEC_INSTALLED_COMMAND"))
      .getOrElse(fail("Select an installed executable containing this source change")))
    require(path.isAbsolute && path.normalize == path && Files.isRegularFile(path) && Files.isExecutable(path)); path
  }
  private def json(value: RecordPresentable): String = take(value.toRecord().toJsonStringC)
  private def call(s: F.Scenario, service: String, operation: String, args: Vector[String]): F.Result =
    s.callInstalled(executable, service, operation, args, installations = None,
      assemblyDescriptor = Some(s.root.resolve(".textus/assembly.yaml")))

  "Deferred GoalTask scope correction" should {
    "prepare and admit additive inventory while leaving repository effects unchanged" in {
      val parameters = BoundedWorkExecutionAdmissionFixture.parameters
      val mapping = parser.parse(parameters(WorkflowExecutionProfileParameters.declaration.name)).toOption.get
      val origin = parser.parse(parameters(GoalPhaseNativeExecution.declaration.name)).toOption.get
      val scenario = F.fresh("goal-task-scope-deferred", bootstrapSelectedInput = false, mappedExecution = Some(mapping -> origin))
      val configPath = scenario.root.resolve(".textus/config.json")
      val config = parser.parse(Files.readString(configPath)).toOption.get.mapObject(_.remove(ThreeWorkflowRuntimeParameters.definition.name))
      val withComponent = sys.props.get("smworkflow.spec.componentFile").fold(config)(path => config.mapObject(_.add("textus.component.file", path.asJson)))
      Files.writeString(configPath, withComponent.noSpaces)
      val request = V.GoalTaskInput(scenario.host.goal.input.repositoryIdentity, "specification-deferred-scope",
        "SPECIFICATION-OWNED additive same-purpose inventory", NonEmptyVector.one("docs/phase.md"), NonEmptyVector.one("test"),
        NonEmptyVector.one("behavior"), NonEmptyVector.one(V.WorkflowArtifactReference("specification-source", "1")),
        false, "documentation", "off", 10, "direct-instruction", Vector.empty)
      val repository = Path.of(scenario.host.target.root)
      val head = scenario.candidateHead
      val index = scenario.candidateIndexDiff
      val status = scenario.candidateStatus
      val input = scenario.root.resolve("goal-task-request.json")
      Files.writeString(input, json(request))
      val prepared = call(scenario, "NativeWorkflowPreparationService", "prepareGoalTaskStart", Vector("--input", json(
        V.WorkflowGoalTaskStartPreparationInput(input.toUri.toASCIIString, "start", "participant", "ExplicitHuman", "start-key", "entity:smworkflow.cli.goal_selection"))))
      scenario.selectPreparedImport(prepared)
      val started = call(scenario, "GoalTaskService", "startGoalTask", Vector("--input", json(request), "--selectionId", "start",
        "--participant", "participant", "--idempotencyKey", "start-key")).business[V.GoalTaskView]
      def advance() = call(scenario, "GoalTaskService", "advanceGoalTask", Vector("--sessionId", started.sessionId)).business[V.GoalTaskView]
      val old = advance()
      val description = call(scenario, "GoalTaskService", "describeGoalTaskScopeCorrection", Vector("--input", json(
        V.GoalTaskScopeCorrectionDescriptionInput(started.sessionId, old.issuedWorkContext.get.continuationId, Vector("docs/additional.md")))))
        .business[V.GoalTaskScopeCorrectionView]
      val selected = V.GoalTaskScopeCorrectionInput(started.sessionId, old.issuedWorkContext.get.continuationId, description.target,
        "scope", "participant", "scope-key", V.WorkflowArtifactReference("SPECIFICATION-OWNED-standing-direction", "1"))
      call(scenario, "GoalTaskService", "correctGoalTaskScope", Vector("--input", json(selected))).exitCode should not be 0
      val source = scenario.root.resolve("scope-input.json")
      Files.writeString(source, json(selected))
      val scopePreparation = V.WorkflowGoalTaskScopeCorrectionPreparationInput(source.toUri.toASCIIString, "scope", "participant",
        "ExplicitHuman", "scope-key", "entity:smworkflow.cli.goal_selection")
      scenario.selectPreparedImport(call(scenario, "NativeWorkflowPreparationService", "prepareGoalTaskScopeCorrection", Vector("--input", json(scopePreparation))))
      val corrected = call(scenario, "GoalTaskService", "correctGoalTaskScope", Vector("--inputResource", source.toUri.toASCIIString))
        .business[V.GoalTaskScopeCorrectionView]
      corrected.outcome shouldBe "corrected-awaiting-advance"
      val successor = advance()
      successor.issuedWorkContext.get.continuationId should not be old.issuedWorkContext.get.continuationId
      val work = parser.parse(take(successor.work.get.toJsonStringC)).toOption.get
      val normalized = GoalPhaseNativeMetadata.normalizePublicJson(work).toOption.get
      normalized.hcursor.downField("state").downField("input").get[Vector[String]]("ownedPaths").toOption.get shouldBe
        Vector("docs/phase.md", "docs/additional.md")
      scenario.candidateHead shouldBe head
      scenario.candidateIndexDiff shouldBe index
      scenario.candidateStatus shouldBe status
      Files.exists(repository.resolve("docs/additional.md")) shouldBe false
      Files.exists(scenario.root.resolve("sbt-executions.txt")) shouldBe false
      Files.exists(scenario.root.resolve("publication-count.txt")) shouldBe false
    }
  }
}
