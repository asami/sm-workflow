package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentDescriptor, ComponentInstanceMetadata, ComponentOrigin}
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterKey, ComponentParameterProvenance}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.protocol.spec.ServiceDefinition
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Canonical factory mechanics only. Generated declarations are wiring probes,
  * not native workflow implementations or independent host-evidence fixtures.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
final class SmWorkflowConfiguredComponentFactorySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"Service bundle factory rejected: $failure")
  }
  private val limitKey = ComponentParameterKey.requiredInt("factory.bundle.limit")
  private val owner = SmWorkflowComponent.componentId
  private val services: Vector[ServiceDefinition] = Vector(SmWorkflowComponent.GoalPhaseServiceService,
    SmWorkflowComponent.SplitPhaseServiceService, SmWorkflowComponent.RepositorySyncServiceService)
  private val publication = Set("GoalPhaseService.startGoalPhase", "SplitPhaseService.startSplitPhase",
    "RepositorySyncService.startRepositorySync")
  private def subsystem(name: String) = RuntimeBindingAdmissionFixture.admit(Subsystem(name = name, version = None,
    configuration = ResolvedConfiguration(Configuration.empty, ConfigurationTrace.empty),
    aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
  private def create(host: Subsystem, instance: String, limit: String) =
    ComponentCreate(host, ComponentOrigin.Builtin, Vector(ComponentDescriptor(name = Some(owner.name),
      componentName = Some(owner.name), version = Some("0.1.0"), schemaVersion = Some(3), componentId = Some(owner),
      config = Map(limitKey.name -> "11"))))
      .withInstanceMetadata(ComponentInstanceMetadata(owner.name, instance, Map(limitKey.name -> limit), componentId = Some(owner)))

  "Canonical sm-workflow service bundle factory" should {
    "bind all three declarations once after resolution and preserve each actual component owner" in {
      Given("one reusable primary factory and two owners with distinct named-instance settings")
      val firstOwner = subsystem("bundle-owner-one")
      val secondOwner = subsystem("bundle-owner-two")
      var observed = Vector.empty[(ComponentCreate, ComponentInitializationParameters, ExecutionContext)]
      val factory = new SmWorkflowConfiguredComponentFactory(Vector(limitKey), Vector.empty, (creation, parameters, context) => {
        observed :+= ((creation, parameters, context))
        Consequence.success(services)
      }, publication).primaryFactory
      val first = create(firstOwner, "one", "23")
      val second = create(secondOwner, "two", "41")
      When("both components are constructed through the canonical public factory")
      val one = take(factory.createPrimaryC(first))
      val two = take(factory.createPrimaryC(second))
      Then("each bundle sees a single immutable resolved snapshot and its own unauthenticated composition scope")
      observed.map(_._1) shouldBe Vector(first, second)
      observed.map(v => take(v._2.resolve(limitKey)).value) shouldBe Vector(Some(23), Some(41))
      observed.foreach(v => take(v._2.resolve(limitKey)).provenance shouldBe ComponentParameterProvenance.SubsystemInstance)
      observed.zip(Vector(one -> firstOwner, two -> secondOwner)).foreach { case ((_, parameters, context), (built, host)) =>
        built.initializationParameters should be theSameInstanceAs parameters
        built.subsystem.get should be theSameInstanceAs host
        built.core.jobEngine should be theSameInstanceAs host.jobEngine
        context.scope.parent.get should be theSameInstanceAs built.scopeContext
        context.dataStoreSpace should be theSameInstanceAs built.scopeContext.dataStoreSpace
        SecuritySubject.current(using context).isTrustedLocalAuthenticated shouldBe false
        SecuritySubject.current(using context).isProviderAuthenticated shouldBe false
        built.protocol.services.services.map(_.name) should contain allElementsOf services.map(_.name)
        built.mcpReadyOperations shouldBe publication
        built.mcpReadyServices shouldBe Set.empty
      }
      one.instanceId should not be two.instanceId
      observed.head._3 should not be theSameInstanceAs(observed.last._3)
    }

    "never call the bundle binder when canonical required parameters are missing or malformed" in {
      Given("a required integer key and a counting bundle binder")
      val host = subsystem("bundle-invalid-parameters")
      var calls = 0
      val factory = new SmWorkflowConfiguredComponentFactory(Vector(limitKey), Vector.empty, (_, _, _) => {
        calls += 1
        Consequence.success(services)
      }).primaryFactory
      When("canonical construction receives missing and malformed settings")
      val missing = factory.createPrimaryC(ComponentCreate(host, ComponentOrigin.Builtin))
      val malformed = factory.createPrimaryC(create(host, "malformed", "not-an-integer"))
      Then("both fail before any service bundle is bound")
      missing.isSuccess shouldBe false
      malformed.isSuccess shouldBe false
      calls shouldBe 0
    }

    "leave no partial service bundle installed when trusted host composition fails" in {
      Given("resolved settings and a host binder that fails after calculating one service declaration")
      val host = subsystem("bundle-composition-failure")
      var captured: Option[Component] = None
      var calculated = Vector.empty[ServiceDefinition]
      val factory = new SmWorkflowConfiguredComponentFactory(Vector(limitKey), Vector.empty, (_, _, context) => {
        captured = Some(context.scope.parent.get.asInstanceOf[Component.Context].component)
        calculated = services.take(1)
        Consequence.configurationInvalid("test-only later native host dependency is unavailable")
      }, publication).primaryFactory
      When("canonical construction invokes the bundle binder")
      val result = factory.createPrimaryC(create(host, "failed", "23"))
      Then("the error propagates without publishing even the already calculated declaration")
      result.isSuccess shouldBe false
      calculated shouldBe services.take(1)
      captured.isDefined shouldBe true
      val installed = captured.get.protocol.services.services.map(_.name).toSet
      installed shouldBe Set("meta", "system")
      installed.intersect(services.map(_.name).toSet) shouldBe Set.empty
      captured.get.mcpReadyOperations shouldBe Set.empty
    }

    "refuse absent, incomplete-publication and conflicting bundles before installing a protocol" in {
      Given("valid required settings and malformed bundle shapes")
      val host = subsystem("bundle-invalid-services")
      val shapes = Vector(null, Vector.empty[ServiceDefinition], Vector(null),
        Vector(services.head, services.head), services.take(1))
      When("canonical construction checks each bundle against the declared publication")
      val results = shapes.zipWithIndex.map { case (shape, index) =>
        new SmWorkflowConfiguredComponentFactory(Vector(limitKey), Vector.empty,
          (_, _, _) => Consequence.success(shape), publication).primaryFactory
          .createPrimaryC(create(host, s"invalid-$index", "23"))
      }
      Then("none is advertised as a working component")
      results.forall(_.isFaillure) shouldBe true
    }
  }
}
