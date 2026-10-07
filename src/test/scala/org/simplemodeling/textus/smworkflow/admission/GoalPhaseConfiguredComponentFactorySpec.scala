package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentInstanceMetadata, ComponentOrigin}
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterKey, ComponentParameterProvenance}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseConfiguredComponentFactorySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"configured factory failed: $other")
  }
  private val limitKey = ComponentParameterKey.requiredInt("factory.probe.limit")
  private val componentId = SmWorkflowComponent.componentId
  private def emptySubsystem(name: String): Subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
    name = name, version = None,
    configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
    aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
  private def descriptor(value: String): ComponentDescriptor = ComponentDescriptor(
    name = Some(componentId.name), componentName = Some(componentId.name), version = Some("0.1.0"),
    schemaVersion = Some(3), componentId = Some(componentId), config = Map(limitKey.name -> value))

  "Configured GoalPhase factory" should {
    "derive each service composition context from its actual component without caller authentication" in {
      Given("one reusable factory and two owners with independently named component instances")
      val firstOwner = emptySubsystem("composition-owner-one")
      val secondOwner = emptySubsystem("composition-owner-two")
      var observed = Vector.empty[(ComponentCreate, ComponentInitializationParameters, ExecutionContext)]
      val factory = new GoalPhaseConfiguredComponentFactory(Vector(limitKey), Vector.empty,
        (creation, parameters, composition) => {
          val subject = SecuritySubject.current(using composition)
          subject.isProviderAuthenticated shouldBe false
          subject.isTrustedLocalAuthenticated shouldBe false
          observed = observed :+ ((creation, parameters, composition))
          Consequence.success(SmWorkflowComponent.StepReviewServiceService)
        }).primaryFactory
      val first = ComponentCreate(firstOwner, ComponentOrigin.Builtin, Vector(descriptor("11")))
        .withInstanceMetadata(ComponentInstanceMetadata(componentId.name, "first", componentId = Some(componentId)))
      val second = ComponentCreate(secondOwner, ComponentOrigin.Builtin, Vector(descriptor("29")))
        .withInstanceMetadata(ComponentInstanceMetadata(componentId.name, "second", componentId = Some(componentId)))

      When("canonical construction invokes the post-resolution binder without an implicit caller context")
      val one = take(factory.createPrimaryC(first))
      val two = take(factory.createPrimaryC(second))

      Then("each binder receives its own component scope and immutable parameters, not another owner's context")
      observed.map(_._1) shouldBe Vector(first, second)
      observed.size shouldBe 2
      observed.zip(Vector(one -> firstOwner, two -> secondOwner)).foreach { case ((_, parameters, composition), (built, owner)) =>
        built.subsystem.get should be theSameInstanceAs owner
        built.initializationParameters should be theSameInstanceAs parameters
        composition.scope.parent.get should be theSameInstanceAs built.scopeContext
        composition.dataStoreSpace should be theSameInstanceAs built.scopeContext.dataStoreSpace
        built.core.jobEngine should be theSameInstanceAs owner.jobEngine
        built.services.services.map(_.serviceDefinition.name) should contain("StepReviewService")
      }
      observed.head._3 should not be theSameInstanceAs(observed.last._3)
      observed.head._3.scope.parent.get should not be theSameInstanceAs(observed.last._3.scope.parent.get)
      one.instanceId should not be two.instanceId
    }

    "assemble after canonical resolution while preserving named instance and host core ownership" in {
      Given("a packaged default overridden by a named instance and a host-owned service binder")
      val subsystem = emptySubsystem("configured-goal-factory")
      val metadata = ComponentInstanceMetadata(componentId.name, "selected", Map(limitKey.name -> "23"),
        componentId = Some(componentId))
      val create = ComponentCreate(subsystem, ComponentOrigin.Builtin, Vector(descriptor("11")))
        .withInstanceMetadata(metadata)
      var snapshot: Option[ComponentInitializationParameters] = None
      var calls = 0
      val factory = new GoalPhaseConfiguredComponentFactory(Vector(limitKey), Vector.empty, (captured, parameters, _) => {
        captured should be theSameInstanceAs create
        val resolution = take(parameters.resolve(limitKey))
        resolution.value shouldBe Some(23)
        resolution.provenance shouldBe ComponentParameterProvenance.SubsystemInstance
        snapshot = Some(parameters)
        calls += 1
        Consequence.success(SmWorkflowComponent.StepReviewServiceService)
      }).primaryFactory
      When("the canonical factory creates the named component")
      val built = take(factory.createPrimaryC(create))
      Then("normal initialization receives that same immutable snapshot and the bound service")
      calls shouldBe 1
      built.initializationParameters should be theSameInstanceAs snapshot.get
      built.instanceId shouldBe metadata.instanceId
      built.instanceMetadata shouldBe Some(metadata)
      built.core.jobEngine should be theSameInstanceAs subsystem.jobEngine
      built.core.factory shouldBe Some(factory)
      built.protocol.services.services should contain(SmWorkflowComponent.StepReviewServiceService)
      built.protocol.services.services.count(_.name == "StepReviewService") shouldBe 1
      built.mcpReadyServices shouldBe Set.empty
    }

    "retain independent creation authority when a primary factory is reused" in {
      Given("one reusable primary factory and two independently configured instances")
      val subsystem = emptySubsystem("configured-goal-reuse")
      var observed = Vector.empty[(ComponentCreate, Int)]
      val factory = new GoalPhaseConfiguredComponentFactory(Vector(limitKey), Vector.empty, (create, parameters, _) => {
        observed = observed :+ (create -> take(parameters.resolve(limitKey)).value.get)
        Consequence.success(SmWorkflowComponent.StepReviewServiceService)
      }).primaryFactory
      val first = ComponentCreate(subsystem, ComponentOrigin.Builtin, Vector(descriptor("11")))
        .withInstanceMetadata(ComponentInstanceMetadata(componentId.name, "first", componentId = Some(componentId)))
      val second = ComponentCreate(subsystem, ComponentOrigin.Builtin, Vector(descriptor("29")))
        .withInstanceMetadata(ComponentInstanceMetadata(componentId.name, "second", componentId = Some(componentId)))
      When("both components are constructed with their own creation capsules")
      val one = take(factory.createPrimaryC(first))
      val two = take(factory.createPrimaryC(second))
      Then("neither configuration nor identity leaks into the other component")
      observed shouldBe Vector(first -> 11, second -> 29)
      one.instanceId should not be two.instanceId
      take(one.initializationParameters.resolve(limitKey)).value shouldBe Some(11)
      take(two.initializationParameters.resolve(limitKey)).value shouldBe Some(29)
    }

    "reject missing or malformed required settings before calling the service binder" in {
      Given("a required integer declaration with missing or noninteger configuration")
      val subsystem = emptySubsystem("configured-goal-invalid")
      var calls = 0
      val factory = new GoalPhaseConfiguredComponentFactory(Vector(limitKey), Vector.empty, (_, _, _) => {
        calls += 1
        Consequence.success(SmWorkflowComponent.StepReviewServiceService)
      }).primaryFactory
      When("ordinary canonical creation attempts both invalid configurations")
      val missing = factory.createPrimaryC(ComponentCreate(subsystem, ComponentOrigin.Builtin))
      val malformed = factory.createPrimaryC(ComponentCreate(subsystem, ComponentOrigin.Builtin,
        Vector(descriptor("invalid"))))
      Then("no bound component or callback is advertised")
      missing shouldBe a[Consequence.Failure[?]]
      malformed shouldBe a[Consequence.Failure[?]]
      calls shouldBe 0
    }

    "fail closed when resolved configuration cannot assemble a service" in {
      Given("valid resolved settings but a rejected or absent host service")
      val subsystem = emptySubsystem("configured-goal-no-service")
      val create = ComponentCreate(subsystem, ComponentOrigin.Builtin, Vector(descriptor("11")))
      When("the trusted binder rejects composition or returns a null service")
      val rejected = new GoalPhaseConfiguredComponentFactory(Vector(limitKey), Vector.empty,
        (_, _, _) => Consequence.configurationInvalid("host authority unavailable")).primaryFactory.createPrimaryC(create)
      val absent = new GoalPhaseConfiguredComponentFactory(Vector(limitKey), Vector.empty,
        (_, _, _) => Consequence.success(null)).primaryFactory.createPrimaryC(create)
      Then("neither exposes placeholder services as a working runtime")
      rejected shouldBe a[Consequence.Failure[?]]
      absent shouldBe a[Consequence.Failure[?]]
    }
  }
}
