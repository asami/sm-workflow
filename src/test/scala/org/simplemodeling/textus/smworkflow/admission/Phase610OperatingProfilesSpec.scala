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
    val factory = new SmWorkflowConfiguredComponentFactory(GoalPhaseOperatingProfiles.declarations, Vector.empty,
      (creation, parameters, _) => for {
        configured <- GoalPhaseOperatingProfiles.configuredC(parameters)
        mappings <- GoalPhaseOperatingProfiles.mappingsC(parameters)
        bound <- GoalPhaseNativeServiceAssembly.bindC(creation, d.copy(operatingProfile = configured, operatingMappings = d.operatingMappings ++ mappings), _plans)
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
    "reject invalid or unavailable public profiles at the real Start boundary without fallback or work effects" in {
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
        ConfigurationValue.StringValue("unknown"),
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

  private val _plan = GoalPhaseOrderedProgression.Plan("spec-profiles", "1", "6.10", "sm-workflow",
    Vector(GoalPhaseOrderedProgression.Step("S1", "Specification implementation", Vector("src/example.scala"), Vector.empty,
      Vector("focused"))), Vector("phase-check"), repairLimit = 10)
  private val _business = GoalPhaseNativeWork.Request("spec-instance", "2", "ImplementStep", Some("S1"), 1,
    _plan.steps.head.purpose, _plan, GoalPhaseOrderedProgression.Snapshot("a" * 40, "b" * 40), Vector.empty, None, None, None)
  private val _handle = WorkflowHandle(SmWorkflowComponent.componentId, _binding.workflowIdentity,
    _binding.workflowRevision, InstanceIdentity("spec-instance"))
  private def _work(review: Boolean = false): WorkflowInteraction[GoalPhaseNativeWork.Request, Nothing] = {
    val request = ContinuationRequest(StateMachineRunIdentity("spec-instance"),
      org.goldenport.cncf.workflow.ContinuationIdentity("spec-work"),
      StateMachineRevision("3"), StateMachineRequiredOperationIdentity("spec-work"),
      StateMachineOperationIdentity("SpecService", "work"), Some(TypedValue(GoalPhaseNativeWork.inputCodec.typeIdentity, _business)),
      Some(StateMachineResultTypeReference("GoalPhaseWorkResult")), ContextBundle("Specification work", Vector.empty,
        Vector.empty, ContextSnapshot(_binding.workflowRevision.value)), CompletionContract("spec-completion", Vector.empty),
      EvidenceContract("spec-evidence", Vector.empty), GoalPhaseNativeRun.completionOperation)
    WorkflowInteraction(_handle, WorkflowContinuation.WorkOrder(request,
      ExecutionRequirement(Vector(CapabilityRequirement("spec-work")), RiskLevel("SOURCE"), ReasoningLevel.Standard,
        review, reasoningClass = Some(ReasoningClassReference("Routine"))), MinimalPresentation("Assigned", "Waiting")))
  }
  private def _order(value: WorkflowInteraction[GoalPhaseNativeWork.Request, Nothing]) =
    value.current.asInstanceOf[WorkflowContinuation.WorkOrder[GoalPhaseNativeWork.Request]]
  private def _capture(value: WorkflowInteraction[GoalPhaseNativeWork.Request, Nothing]) =
    _order(value).request.input.get.value.operatingProfile.get
  private val _alternative = _profile.copy(identity = "spec-opt-in", model = "spec-opt-in-model", controls = Map("effort" -> "high"))
  private val _optIn = take(WorkflowExecutionProfileMapping.createC("spec-opt-in-mapping", "2", Vector(_alternative),
    Vector(WorkflowExecutionProfileMapping.ClassBinding("Routine", _alternative.identity))))
  private val _mapped = _declaration.copy(executionProfiles = Some(_mapping), executionOrigin = Some(_origin),
    operatingMappings = Map("cost" -> _optIn, "quality" -> _optIn))

  "Named profile capture at fresh work issuance" should afterWord(
    "in spec:ai-operating-profiles, example:M2-E1, rules:P610-02,P610-03,P610-10, phase:6.10") {
    "resolve explicit over config over standard and let a resume parameter supersede saved Start intent only for new work" in {
      Given("registered logical profiles, saved explicit Start intent and current configuration")
      GoalPhaseOperatingProfiles.registered shouldBe Set("standard", "cost", "quality")
      val start = Some(GoalPhaseOperatingProfiles.Selection("cost", "1", "explicit"))
      val configured = _mapped.copy(operatingProfile = Some("quality"))
      When("the fresh issuance resolver selects each actual input boundary")
      val defaults = take(GoalPhaseOperatingProfiles.forIssueC(_mapped, None))
      val config = take(GoalPhaseOperatingProfiles.forIssueC(configured, None))
      val explicit = take(GoalPhaseOperatingProfiles.forIssueC(configured, start))
      val resumed = take(GoalPhaseOperatingProfiles.forIssueC(configured.copy(invocationOperatingProfile =
        Some(GoalPhaseOperatingProfiles.Selection("standard", "1", "explicit"))), start))
      Then("new work has one attributable selection without amending saved Start intent or Plan")
      defaults.selection shouldBe GoalPhaseOperatingProfiles.Selection("standard", "1", "default")
      config.selection shouldBe GoalPhaseOperatingProfiles.Selection("quality", "1", "config")
      explicit.selection shouldBe start.get
      resumed.selection.identity shouldBe "standard"
      take(GoalPhaseOperatingProfiles.forIssueC(configured,
        Some(GoalPhaseOperatingProfiles.Selection("cost", "1", "config")))).selection shouldBe config.selection
      start.get.identity shouldBe "cost"
    }
    "capture configured and selected concrete profiles through the existing resolver while preserving requirement and acceptance" in {
      Given("a fresh WorkOrder and specification-owned supported host mappings for all three names")
      val original = _work()
      When("the same fresh boundary used by native issuance captures each selected logical route")
      val results = Vector("standard", "cost", "quality").map { name =>
        take(GoalPhaseOperatingProfiles.bindFreshC(original, _mapped.copy(operatingProfile = Some(name)), None))
      }
      Then("only execution disposition and the new capture differ; configured facts do not become observed model claims")
      results.foreach { issued =>
        val capture = _capture(issued)
        GoalPhaseOperatingProfiles.captureValid(capture) shouldBe true
        capture.mappingIdentity should not be empty
        capture.mappingRevision should not be empty
        capture.configured.map(_.model) shouldBe capture.selected.map(_.model)
        _order(issued).requirement.copy(disposition = None) shouldBe _order(original).requirement
        _order(issued).request.input.get.value.copy(operatingProfile = None) shouldBe _business
        _order(issued).request.context shouldBe _order(original).request.context
        _order(issued).request.completionOperation shouldBe _order(original).request.completionOperation
        NativeWorkflowOperationOutput.goalWork(_order(issued).request.input.get.value).operatingProfile should not be empty
      }
      _order(results.head).requirement.disposition.get.placement shouldBe ExecutionPlacement.Inline
      _order(results(1)).requirement.disposition.get.placement shouldBe ExecutionPlacement.Delegated
      _capture(results(1)).configured.get.controls shouldBe Map("effort" -> "high")
      val independent = take(GoalPhaseOperatingProfiles.bindFreshC(_work(review = true), _mapped, None))
      _order(independent).requirement.disposition.get.independence shouldBe ExecutionIndependence.Required
      _order(independent).requirement.disposition.get.placement shouldBe ExecutionPlacement.Delegated
      val isolated = take(GoalPhaseOperatingProfiles.bindFreshC(_work(), _mapped, None, isolateContext = true))
      _order(isolated).requirement.disposition.get.placement shouldBe ExecutionPlacement.Delegated
    }
    "round-trip retained work without rerouting after profile and mapping changes and keep historical absence unknown" in {
      Given("one newly captured opt-in WorkOrder and a legacy unprofiled WorkOrder")
      val captured = take(GoalPhaseOperatingProfiles.bindFreshC(_work(), _mapped.copy(operatingProfile = Some("cost")), None))
      val wire = take(WorkflowWorkOrderJsonV1.encodeC(captured, GoalPhaseNativeWork.inputCodec))
      When("the normal issued-work codec recollects the saved order independently of current config")
      val retained = take(WorkflowWorkOrderJsonV1.decodeC(wire, GoalPhaseNativeWork.inputCodec))
      val legacy = take(WorkflowWorkOrderJsonV1.decodeC(take(WorkflowWorkOrderJsonV1.encodeC(_work(),
        GoalPhaseNativeWork.inputCodec)), GoalPhaseNativeWork.inputCodec))
      val changed = _mapped.copy(operatingProfile = Some("quality"), operatingMappings = Map.empty)
      Then("captured values and wire bytes remain stable; any attempt to bind issued work again refuses")
      retained shouldBe captured
      take(WorkflowWorkOrderJsonV1.encodeC(retained, GoalPhaseNativeWork.inputCodec)) shouldBe wire
      GoalPhaseOperatingProfiles.bindFreshC(retained, changed, None).isSuccess shouldBe false
      _order(legacy).request.input.get.value.operatingProfile shouldBe None
      NativeWorkflowOperationOutput.goalWork(_order(legacy).request.input.get.value).operatingProfile shouldBe None
      val oldStart = GoalPhaseNativeRun.Payload("participant", "/spec/repository", "main", _plan, _business.originalBase)
      GoalPhaseNativeRun.codec.decode(GoalPhaseNativeRun.codec.encode(oldStart)) shouldBe Right(oldStart)
      val newStart = oldStart.copy(operatingProfile = Some(GoalPhaseOperatingProfiles.Selection("cost", "1", "explicit")))
      GoalPhaseNativeRun.codec.decode(GoalPhaseNativeRun.codec.encode(newStart)) shouldBe Right(newStart)
      val malformed = GoalPhaseNativeWork.inputCodec.encode(_order(captured).request.input.get.value)
        .mapObject(_.add("schemaVersion", Json.fromString("sm-workflow.goal-phase-native-work-request.v2")))
      GoalPhaseNativeWork.inputCodec.decode(malformed).isLeft shouldBe true
    }
    "make the real caller consume the saved concrete selection instead of a later mapping and refuse opt-in fallback" in {
      Given("a captured opt-in issue and a caller supplying a different later mapping")
      val withFallback = take(WorkflowExecutionProfileMapping.createC("spec-with-fallback", "3", Vector(_alternative, _profile),
        Vector(WorkflowExecutionProfileMapping.ClassBinding("Routine", _alternative.identity)),
        Vector(WorkflowExecutionProfileMapping.ClassBinding("Routine", _profile.identity))))
      val issued = take(GoalPhaseOperatingProfiles.bindFreshC(_work(), _mapped.copy(operatingProfile = Some("cost"),
        operatingMappings = Map("cost" -> withFallback)), None))
      val context = take(NativeWorkflowIssuedWorkContext.fromIssuedC(issued, NativeWorkflowIssuedWorkContext.Mode.Implementation,
        GoalPhaseNativeWork.inputCodec))
      When("the existing caller bridge prepares execution from the public issued context")
      val prepared = take(org.simplemodeling.textus.smworkflow.caller.NativeWorkflowCallerExecution.prepareC(context, _mapping, _origin.current))
      Then("the captured opt-in model and controls drive the actual next selection; unavailable has no alternate substitution")
      prepared.session.primary shouldBe _alternative
      prepared.session.mappingIdentity shouldBe "spec-with-fallback"
      prepared.session.alternate shouldBe None
      val selection = prepared.session.nextSelection.get
      val unavailable = take(prepared.recordC(selection, WorkflowExecutionHarness.Outcome(WorkflowExecutionHarness.Status.Unavailable,
        WorkflowExecutionHarness.Reason.ProviderUnavailable, "Specification-only unavailable model")))
      unavailable.session.nextSelection shouldBe None
      take(org.simplemodeling.textus.smworkflow.caller.NativeWorkflowCallerExecution.restoreC(context,
        unavailable.session)).session.primary shouldBe _alternative
      val standardMapping = _mapped.copy(executionProfiles = Some(withFallback))
      val standard = take(GoalPhaseOperatingProfiles.bindFreshC(_work(review = true), standardMapping, None))
      val standardContext = take(NativeWorkflowIssuedWorkContext.fromIssuedC(standard,
        NativeWorkflowIssuedWorkContext.Mode.Review, GoalPhaseNativeWork.inputCodec))
      val standardCaller = take(org.simplemodeling.textus.smworkflow.caller.NativeWorkflowCallerExecution.prepareC(
        standardContext, _mapping, _origin.current))
      standardCaller.session.alternate shouldBe Some(_profile)
    }
    "consume optional resume input on the actual Advance receiver while retaining the public context constructor" in {
      Given("the actual generated Advance operation and a registered but currently unavailable logical mapping")
      val service = take(_service(Map.empty))
      val receiver = service.operations.operations.toVector.find(_.name == "advanceGoalPhase").get
        .asInstanceOf[NativeWorkflowAdvanceInput.Receiver]
      def request(value: Any) = Request.of(component = SmWorkflowComponent.name, service = "GoalPhaseService",
        operation = "advanceGoalPhase", properties = List(Property("profile", value, None)))
      When("Advance binds a logical invocation without resolving any already pending work")
      val registered = receiver.createAdvanceOperationRequestC(request("cost"), NativeWorkflowAdvanceInput.Goal("spec-instance"))
      val invalid = receiver.createAdvanceOperationRequestC(request("unknown"), NativeWorkflowAdvanceInput.Goal("spec-instance"))
      Then("the current invocation can reach the native retained frontier, while an invalid name refuses")
      registered.isSuccess shouldBe true
      invalid.isSuccess shouldBe false
      receiver.specification.request.parameters.find(_.name == "profile").get.domain.multiplicity shouldBe Multiplicity.ZeroOne
      val unconfigured = take(GoalPhaseOperatingProfiles.bindFreshC(_work(), _declaration, None))
      val context = take(NativeWorkflowIssuedWorkContext.fromIssuedC(unconfigured,
        NativeWorkflowIssuedWorkContext.Mode.Implementation, GoalPhaseNativeWork.inputCodec))
      context.context.getRecord("operatingProfile").get.getAny("selected") shouldBe None
      context.context.getRecord("operatingProfile").get.getAny("configured") shouldBe None
    }
    "report unavailable opt-in mapping, missing classes and forced substitution without silent fallback" in {
      Given("registered names with absent host mappings or mappings that cannot honor requested work")
      When("fresh issuance tries these unavailable selections")
      val unavailable = GoalPhaseOperatingProfiles.bindFreshC(_work(), _declaration.copy(operatingProfile = Some("cost")), None)
      val wrongClass = take(WorkflowExecutionProfileMapping.createC("wrong-class", "1", Vector(_alternative),
        Vector(WorkflowExecutionProfileMapping.ClassBinding("Other", _alternative.identity))))
      val missing = GoalPhaseOperatingProfiles.bindFreshC(_work(), _mapped.copy(operatingProfile = Some("cost"),
        operatingMappings = Map("cost" -> wrongClass)), None)
      val forced = GoalPhaseOperatingProfiles.bindFreshC(_work(), _mapped.copy(operatingProfile = Some("cost"),
        executionOrigin = Some(_origin.copy(preference = WorkflowExecutionPreference(forceInline = true)))), None)
      Then("each refuses rather than selecting standard, another model or a fallback")
      unavailable.isSuccess shouldBe false
      unavailable.toString should include("unavailable")
      missing.isSuccess shouldBe false
      forced.isSuccess shouldBe false
      val captured = take(GoalPhaseOperatingProfiles.bindFreshC(_work(), _mapped.copy(operatingProfile = Some("cost")), None))
      val observation = ExecutionObservation(ExecutionPlacement.Delegated, "spec-context", "spec-worker", _alternative.identity)
      val evidence = ExecutionEvidence(Vector.empty, execution = Some(observation))
      GoalPhaseOperatingProfiles.admitExecutionC(Some(_capture(captured)), evidence).isSuccess shouldBe true
      GoalPhaseOperatingProfiles.admitExecutionC(Some(_capture(captured)), evidence.copy(modelIdentity = Some("wrong"))).isSuccess shouldBe false
      GoalPhaseOperatingProfiles.admitExecutionC(Some(_capture(captured)), evidence.copy(execution =
        Some(observation.copy(profileIdentity = _profile.identity)))).isSuccess shouldBe false
      GoalPhaseOperatingProfiles.admitExecutionC(Some(_capture(captured)), ExecutionEvidence(Vector.empty)).isSuccess shouldBe false
      GoalPhaseOperatingProfiles.admitExecutionC(None, ExecutionEvidence(Vector.empty)).isSuccess shouldBe true
    }
    "consume typed opt-in host mappings and logical public input through the actual Start receiver" in {
      Given("the standard CNCF mapping schema under the two declared opt-in configuration keys")
      val mapping = Json.obj("schemaVersion" -> Json.fromString(WorkflowExecutionProfileParameters.schemaVersion),
        "identity" -> Json.fromString("spec-configured"), "revision" -> Json.fromString("1"),
        "profiles" -> Json.arr(Json.obj("identity" -> Json.fromString(_alternative.identity),
          "provider" -> Json.fromString(_alternative.provider), "model" -> Json.fromString(_alternative.model),
          "controls" -> Json.obj("effort" -> Json.fromString("high")))),
        "classes" -> Json.arr(Json.obj("reasoningClass" -> Json.fromString("Routine"), "profile" -> Json.fromString(_alternative.identity))))
      val config = Map(GoalPhaseOperatingProfiles.cost.name -> ConfigurationValue.StringValue(mapping.noSpaces),
        GoalPhaseOperatingProfiles.quality.name -> ConfigurationValue.StringValue(mapping.noSpaces),
        _key -> ConfigurationValue.StringValue("quality"))
      When("normal initialization and the actual public receiver consume config and explicit logical names")
      val receiver = _receiver(take(_service(config, _mapped.copy(operatingMappings = Map.empty))))
      Then("all registered routes bind, while malformed mappings fail canonical configuration decoding")
      Vector(None, Some("standard"), Some("cost"), Some("quality")).foreach { name =>
        receiver.createStartOperationRequestC(_request(name), NativeWorkflowStartInput.Goal(_input), _selected).isSuccess shouldBe true
      }
      _service(Map(GoalPhaseOperatingProfiles.cost.name -> ConfigurationValue.StringValue("{}"))).isSuccess shouldBe false
      GoalPhaseOperatingProfiles.mappingsC(null).isSuccess shouldBe false
    }
  }

}
