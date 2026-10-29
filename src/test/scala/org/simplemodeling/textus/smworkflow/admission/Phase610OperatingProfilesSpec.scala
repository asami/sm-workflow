package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration => HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{ComponentCreate, ComponentDescriptor, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.{EventPersistencePolicy, EventStorePersistenceDeclaration, EventTypeId}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.{Subsystem, SubsystemEventStoreBootstrap}
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.spec as spec
import org.goldenport.schema.Multiplicity
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms
import org.simplemodeling.textus.smworkflow.value.GoalPhaseStartInput
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take

/** Specification-owned configurations and Operation construction only. No live
  * model, execution observation, workflow storage or accepted Phase is inferred.
  * @since   Oct. 29, 2026
  * @version Oct. 29, 2026
  * @author  ASAMI, Tomoharu
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class Phase610OperatingProfilesSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _key = GoalPhaseOperatingProfiles.declaration.name
  private val _input = GoalPhaseStartInput("6.10", "sm-workflow")
  private val _store = WorkflowStoreIdentity("spec-profiles")
  private def _collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "profiles", name))
  private val _declaration = GoalPhaseNativeRun.Declaration(Configuration(NamedWorkflowStore("workflow", _store),
    MigrationPolicy("v1"), RetentionPolicy("v1"), LeasePolicy("v1"), DeliveryConfiguration.SameStore(_store)),
    _collection("instances"), _collection("continuations"), _input,
    RepositoryGitRuntimePrograms.Target(Path.of("/spec/repository"), "main", "origin", "main", "/spec/upstream.git"))
  private val _profile = WorkflowConcreteExecutionProfile("spec-baseline", "spec-provider", "spec-model", Map("effort" -> "spec-effort"))
  private val _mapping = take(WorkflowExecutionProfileMapping.createC("spec-baseline", "1", Vector(_profile),
    Vector(WorkflowExecutionProfileMapping.ClassBinding("Routine", _profile.identity))))
  private val _origin = GoalPhaseNativeExecution.Origin(WorkflowExecutorDescriptor("spec-context", _profile),
    WorkflowExecutionPreference())
  private def _request(value: Option[Any]): Request = Request.of(component = SmWorkflowComponent.name,
    service = "GoalPhaseService", operation = "startGoalPhase",
    properties = value.map(v => Property("profile", v, None)).toList)
  private def _host(values: Map[String, ConfigurationValue]): SubsystemEventStoreBootstrap = {
    val context = ExecutionContext.create()
    val policy = take(EventPersistencePolicy.createC("spec-profiles-v1", Vector(
      EventPersistencePolicy.Rule(EventTypeId("profiles-transient"), EventPersistencePolicy.Mode.Transient))))
    val storage = take(EventStorePersistenceDeclaration.createC("events", _collection("cursor"),
      _collection("events"), _collection("indexes"), Vector(EventStorePersistenceDeclaration.WorkflowGrant(
        SmWorkflowComponent.componentId, _declaration.configuration.workflowStore.name,
        _declaration.instances, _declaration.continuations))))
    val path = Files.createTempDirectory("smwf-profiles-host-").resolve("host.sqlite")
    val config = ResolvedConfiguration(HostConfiguration(Map(
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)) ++ values), ConfigurationTrace.empty)
    // Built-in host components need managed application storage even when no Workflow action runs.
    take(SubsystemEventStoreBootstrap.createC(policy, storage,
      ScopeContext(ScopeKind.Subsystem, "spec-profiles", None, context.observability),
      config,
      Some(RunMode.Command), AliasResolver.empty))
  }
  private def _create(owner: Subsystem): ComponentCreate = {
    val id = SmWorkflowComponent.componentId
    ComponentCreate(RuntimeBindingAdmissionFixture.admit(owner), ComponentOrigin.Builtin,
      Vector(ComponentDescriptor(name = Some(id.name), componentName = Some(id.name), version = Some("0.1.0"),
        schemaVersion = Some(3), componentId = Some(id))))
  }
  private val _plans = new GoalPhaseNativeRun.PlanPort {
    def loadForCallC(identity: String, core: org.goldenport.cncf.action.ActionCall.Core):
      Consequence[Option[GoalPhaseNativeRun.SelectedPlan]] =
      Consequence.stateConflict("Specification constructs Operations only; no selected Plan is loaded")
  }
  private def _service(values: Map[String, ConfigurationValue], d: GoalPhaseNativeRun.Declaration = _declaration):
    Consequence[spec.ServiceDefinition] = {
    var service = Option.empty[spec.ServiceDefinition]
    val factory = new SmWorkflowConfiguredComponentFactory(Vector(GoalPhaseOperatingProfiles.declaration), Vector.empty,
      (creation, parameters, _) => for {
        configured <- GoalPhaseOperatingProfiles.configuredC(parameters)
        bound <- GoalPhaseNativeServiceAssembly.bindC(creation, d.copy(operatingProfile = configured), _plans)
      } yield {
        service = Some(bound)
        Vector(bound)
      })
    val host = _host(values)
    try factory.primaryFactory.createPrimaryC(_create(host.subsystem)).map(_ => service.get)
    finally take(host.shutdownC())
  }
  private def _receiver(service: spec.ServiceDefinition): NativeWorkflowStartInput.Receiver =
    service.operations.operations.toVector.find(_.name == "startGoalPhase").get
      .asInstanceOf[NativeWorkflowStartInput.Receiver]
  private val _binding = take(GoalPhaseRuntimeCatalog.loadC()).definition
  private val _selected = ProfileStartAdmission.Selection("spec-selection", GoalPhaseNativeRun.startOperation,
    _binding.workflowIdentity, _binding.workflowRevision, "spec-participant", ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
    "spec-key", None, Some(TypedValue("GoalPhaseStartInput", Json.obj(
      "phaseIdentity" -> Json.fromString(_input.phaseIdentity), "repositoryIdentity" -> Json.fromString(_input.repositoryIdentity)))))

  "Standard operating profile" should afterWord("in spec:ai-operating-profiles, example:M1-E1, rules:P610-01,P610-02, phase:6.10") {
    "preserve both unconfigured and concrete baseline execution inputs under omission and explicit standard" in {
      Given("legacy two-argument Start and specification-owned unmapped or mapped baseline settings")
      val baselines = Vector(_declaration, _declaration.copy(executionProfiles = Some(_mapping), executionOrigin = Some(_origin)))
      When("the Start resolver receives omission or explicit standard with absent or standard config")
      val cases = for {
        d <- baselines
        config <- Vector(None, Some("standard"))
        explicit <- Vector(None, Some("standard"))
      } yield (d, take(GoalPhaseOperatingProfiles.forStartC(_request(explicit), d.copy(operatingProfile = config))))
      Then("standard retains the exact concrete mapping/origin and leaves parent policy and acceptance untouched")
      cases.foreach { (d, resolved) =>
        resolved.selection.identity shouldBe "standard"
        resolved.selection.revision shouldBe "1"
        resolved.profiles shouldBe d.executionProfiles
        resolved.origin shouldBe d.executionOrigin
        d.copy(executionProfiles = resolved.profiles, executionOrigin = resolved.origin) shouldBe d
      }
      _declaration.input shouldBe GoalPhaseStartInput("6.10", "sm-workflow")
      take(GoalPhaseOperatingProfiles.resolveC(None, None)).source shouldBe "default"
      take(GoalPhaseOperatingProfiles.resolveC(None, Some("standard"))).source shouldBe "config"
      take(GoalPhaseOperatingProfiles.resolveC(Some("standard"), Some("standard"))).source shouldBe "explicit"
    }
    "expose and consume the generated optional public parameter through canonical configuration and the actual Start receiver" in {
      Given("a normal Component parameter owner and actual generated GoalPhase Operation metadata")
      ThreeWorkflowRuntimeParameters.declarations.map(_.name) should contain(_key)
      val services = Vector(Map.empty[String, ConfigurationValue], Map(_key -> ConfigurationValue.StringValue("standard")))
        .map(values => take(_service(values)))
      When("the owning Start receiver constructs actions for omitted and explicit standard")
      services.foreach { service =>
        val receiver = _receiver(service)
        val parameter = receiver.specification.request.parameters.find(_.name == "profile").get
        parameter.domain.multiplicity shouldBe Multiplicity.ZeroOne
        receiver.specification.request shouldBe SmWorkflowComponent.GoalPhaseServiceService.StartGoalPhaseOperation.specification.request
        Vector(None, Some("standard")).foreach { value =>
          receiver.createStartOperationRequestC(_request(value), NativeWorkflowStartInput.Goal(_input), _selected).isSuccess shouldBe true
        }
      }
      Then("business admission still compares the original exact Phase/repository and no profile grants broader scope")
      NativeWorkflowStartInput.admitSelectedC(_selected, NativeWorkflowStartInput.Goal(_input), "spec-participant", _binding).isSuccess shouldBe true
      NativeWorkflowStartInput.admitSelectedC(_selected, NativeWorkflowStartInput.Goal(_input.copy(phaseIdentity = "other")),
        "spec-participant", _binding).isSuccess shouldBe false
    }
    "reject invalid names and parameter types at the real Start boundary without fallback or work effects" in {
      Given("an actual bound Start receiver and unsupported or malformed logical profiles")
      val receiver = _receiver(take(_service(Map.empty)))
      When("the caller supplies blank, padded, case-changed, unavailable or non-string values")
      val values: Vector[Any] = Vector("", " standard", "standard ", "Standard", "unknown", "cost", "quality", 1, true, Json.Null)
      val rejected = values.map(value => receiver.createStartOperationRequestC(_request(Some(value)),
        NativeWorkflowStartInput.Goal(_input), _selected))
      Then("every supplied invalid value fails before an Action can execute instead of using standard")
      rejected.foreach(_.isSuccess shouldBe false)
      GoalPhaseOperatingProfiles.resolveC(null, None).isSuccess shouldBe false
      GoalPhaseOperatingProfiles.resolveC(None, null).isSuccess shouldBe false
      GoalPhaseOperatingProfiles.resolveC(Some(null), None).isSuccess shouldBe false
    }
    "reject malformed canonical configuration and preserve the old declaration defaults" in {
      Given("canonical string configuration, invalid consumed types and a legacy declaration constructor")
      val values = Vector(ConfigurationValue.StringValue(""), ConfigurationValue.StringValue(" standard"),
        ConfigurationValue.StringValue("unknown"), ConfigurationValue.StringValue("cost"), ConfigurationValue.StringValue("quality"),
        ConfigurationValue.BooleanValue(true), ConfigurationValue.NullValue)
      When("the normal parameter owner decodes each profile and the host validates its declaration")
      val rejected = values.map(value => _service(Map(_key -> value)))
      Then("invalid configuration is refused rather than selecting a default or changing live settings")
      rejected.foreach(_.isSuccess shouldBe false)
      GoalPhaseNativeRun.validateC(_declaration).isSuccess shouldBe true
      GoalPhaseNativeRun.validateC(_declaration.copy(operatingProfile = Some("standard"))).isSuccess shouldBe true
      GoalPhaseNativeRun.validateC(_declaration.copy(operatingProfile = Some("unknown"))).isSuccess shouldBe false
      GoalPhaseNativeRun.validateC(_declaration.copy(operatingProfile = null)).isSuccess shouldBe false
      GoalPhaseOperatingProfiles.configuredC(null).isSuccess shouldBe false
    }
  }
}
