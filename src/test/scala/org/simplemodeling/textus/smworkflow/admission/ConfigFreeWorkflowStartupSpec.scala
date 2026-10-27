package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentOrigin}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.subsystem.GenericSubsystemDescriptor
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.protocol.Request
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Bootstrap is independent of a run; actual Plan data enters at Start.
  * @since Oct. 27, 2026
  * @version Oct. 27, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class ConfigFreeWorkflowStartupSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"Configuration-free startup failed: $other")
  }
  "Configuration-free component startup" should {
    "load its packaged standard declarations without embedding a business run or machine-specific paths" in {
      Given("the component's actual CAR source descriptors")
      When("the ordinary public CAR loader reads their standard assembly declarations")
      val descriptor = take(GenericSubsystemDescriptor.loadComponentArchive(Path.of("src/main/car").toAbsolutePath))
      Then("storage and input destinations belong to the component while Phase and Plan remain absent")
      descriptor.config.contains("sm-workflow.runtime.definition") shouldBe false
      descriptor.config("sm-workflow.goal-phase.persistence.store-name") shouldBe "workflow"
      descriptor.config("textus.datastore.path") shouldBe "${runtime.root}/.codex-workflow/storage/workflow.sqlite"
      descriptor.config.values.exists(_.contains("/Users/asami")) shouldBe false
      descriptor.config.values.exists(_.contains("not-selected")) shouldBe false
    }

    "serve ordinary Help without a configuration file, selected Phase, Plan or Session" in {
      Given("an ordinary component creation capsule with empty user configuration")
      val host = RuntimeBindingAdmissionFixture.admit(Subsystem(name = "help-only", version = None,
        configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
        aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
      val id = SmWorkflowComponent.componentId
      val descriptor = ComponentDescriptor(name = Some(id.name), componentName = Some(id.name),
        version = Some("0.1.0"), schemaVersion = Some(3), componentId = Some(id))
      When("the public component factory constructs the component and ordinary meta.help executes")
      val component = take(ThreeWorkflowRuntimeParameters.factory.primaryFactory.createPrimaryC(
        ComponentCreate(host, ComponentOrigin.Builtin, Vector(descriptor))))
      val subsystem = host.add(component)
      val response = subsystem.execute(Request.of(component = component.name, service = "meta", operation = "help",
        arguments = Nil, switches = Nil, properties = Nil))
      Then("Help succeeds through the normal component protocol and describes real deferred handlers")
      response.isSuccess shouldBe true
      component.mcpReadyOperations shouldBe ThreeWorkflowLauncherAssembly.publishedMcpOperations
      take(component.initializationParameters.resolve(ThreeWorkflowRuntimeParameters.definition)).value shouldBe None
      component.protocol.services.services.map(_.name) should contain allOf (
        "GoalPhaseService", "SplitPhaseService", "RepositorySyncService", "NativeWorkflowPreparationService")
    }

    "compose actual Phase and Step policies from each selected Plan instead of startup business defaults" in {
      Given("two actual Plans for the same runtime root")
      val target = RepositoryGitRuntimePrograms.Target(Path.of("/tmp/actual-repository"), "feature", "origin", "main", "https://example.org/repository.git")
      val first = GoalPhaseOrderedProgression.Plan("first-plan", "1", "PHASE-7", "project", Vector(
        GoalPhaseOrderedProgression.Step("implement", "Implement the selected change", Vector("src/main/Actual.scala"),
          Vector.empty, Vector("compile")),
        GoalPhaseOrderedProgression.Step("spec", "Describe its behavior", Vector("src/test/ActualSpec.scala"),
          Vector("implement"), Vector("test"))), Vector("test"))
      val second = first.copy(identity = "second-plan", phase = "PHASE-8", steps = Vector(
        GoalPhaseOrderedProgression.Step("next", "Implement the next change", Vector("src/Next.scala"), Vector.empty, Vector("test"))))
      When("standard runtime settings are composed at Start for each Plan")
      val one = take(StandardWorkflowRuntime.goalSettingsC(first, target))
      val two = take(StandardWorkflowRuntime.goalSettingsC(second, target))
      Then("each composition uses its real Phase, owned paths, selected checks and ordered Step identifiers")
      one.goal.input.phaseIdentity shouldBe "PHASE-7"
      two.goal.input.phaseIdentity shouldBe "PHASE-8"
      one.goal.candidatePaths shouldBe Vector("src/main/Actual.scala", "src/test/ActualSpec.scala")
      two.goal.candidatePaths shouldBe Vector("src/Next.scala")
      one.goal.closure.programs.map(_.step) shouldBe Vector("implement", "spec", "phase-release")
      two.goal.closure.programs.map(_.step) shouldBe Vector("next", "phase-release")
      one.goal.stepValidation.checks.map(_.kind) shouldBe Vector(WorkflowSbtValidation.Kind.Compile, WorkflowSbtValidation.Kind.Test)
      val largest = first.copy(steps = Vector.tabulate(64)(index => first.steps.head.copy(identity = "S" + (index + 1))))
      val settings = take(StandardWorkflowRuntime.goalSettingsC(largest, target))
      settings.goal.closure.programs.last.ordinal shouldBe 65
      val snapshot = GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40)
      val input = GoalPhaseNativeRepositoryClosure.Input("session", "1", largest, snapshot, Vector.empty, snapshot, "review", "child")
      val receipt = GoalPhaseNativeReleaseClosure.Payload(input, snapshot, 65)
      GoalPhaseNativeReleaseClosure.codec.decode(GoalPhaseNativeReleaseClosure.codec.encode(receipt)) shouldBe Right(receipt)
    }

    "require an explicit binding for a custom check instead of guessing its command" in {
      Given("a Plan check name that is not one of the standard operations")
      When("the default runtime resolves its check binding")
      val result = StandardWorkflowRuntime.standardChecksC(Vector("custom-project-check"))
      Then("it does not convert an unknown name into a guessed test, command or grant")
      result.isSuccess shouldBe false
    }
  }
}
