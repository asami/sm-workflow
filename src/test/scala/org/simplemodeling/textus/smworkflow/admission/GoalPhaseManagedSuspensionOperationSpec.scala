package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import java.time.Instant
import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.{ComponentDataStore, DataStore}
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.SubsystemEventStoreBootstrap
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.spec as spec
import org.goldenport.value.BaseContent
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartCommand, GoalPhaseStartInput, RepairStepResult, ReviewStepResult, StepCloseRequest, StepReviewEvidence}
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/**
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
final class GoalPhaseManagedSuspensionOperationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private given context: ExecutionContext = ExecutionContext.create()
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"Managed GoalPhase fixture rejected: $failure")
  }
  /** Test fixture bytes are returned only after a real current-lease read.
    * This proves routing, not Blob acquisition or artifact materialization.
    */
  private def _snapshot_lease_c(core: ActionCall.Core): Consequence[Unit] =
    ManagedHostRecordRead.loadC(core, "workflow", collection("hostauthorizations"),
      "snapshot-lease-probe", GoalPhaseHostRecordReadPolicy.Purpose.Authorization).map(_ => ())
  /** Real ingress origin, supplied to the current dispatched call, not a
    * Factory-time context or an attribute claiming authentication.
    */
  private def _registration_context(): ExecutionContext = {
    import org.goldenport.cncf.subsystem.*
    import org.goldenport.cncf.security.IngressSecurityResolver
    val subsystem = Subsystem(name = "registration-actor", scopeContext = Some(
      ScopeContext(ScopeKind.Subsystem, "registration-actor", None, context.observability)),
      configuration = ResolvedConfiguration(HostConfiguration.empty, ConfigurationTrace.empty))
    val owner = subsystem
    val identity = org.goldenport.cncf.component.ComponentId("org.simplemodeling.textus.smworkflow.RegistrationActorSpec")
    val participant = new Component() {
      override val core = Component.Core.create(identity.name, identity, ComponentInstanceId.default(identity), Protocol.empty)
      override def subsystem = Some(owner)
    }.withArtifactMetadata(Component.ArtifactMetadata("spec", "RegistrationActorSpec", "0.0.0", component = Some("participant")))
    val configured = subsystem.withDescriptor(GenericSubsystemDescriptor(path = Path.of("<memory>"),
      subsystemName = "registration-actor", componentBindings = Vector(GenericSubsystemComponentBinding("participant")),
      security = Some(GenericSubsystemSecurityBinding(authentication = Some(GenericSubsystemAuthenticationBinding(
        convention = Some("enabled"), fallbackPrivilege = Some("disabled"),
        localSubject = Some(GenericSubsystemLocalSubjectBinding(id = "local-participant", roles = Vector("user"),
          capabilities = Vector("user"), attributes = Map.empty, securityLevel = Some("user")))))))))
    configured.add(Vector(participant))
    take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.Fixed,
      configured.components.head.logic.executionContext(), Map.empty)).executionContext
  }
  private def _configure_registration_cli(host: SubsystemEventStoreBootstrap): Unit = {
    import org.goldenport.cncf.subsystem.*
    host.subsystem.withDescriptor(GenericSubsystemDescriptor(path = Path.of("<registration-cli-spec>"),
      subsystemName = "managed-goal", componentBindings = Vector(GenericSubsystemComponentBinding(component.name)),
      security = Some(GenericSubsystemSecurityBinding(authentication = Some(GenericSubsystemAuthenticationBinding(
        convention = Some("enabled"), fallbackPrivilege = Some("disabled"),
        localSubject = Some(GenericSubsystemLocalSubjectBinding(id = "local-participant", roles = Vector("user"),
          capabilities = Vector("user"), attributes = Map.empty, securityLevel = Some("user")))))))))
    ()
  }
  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString.stripSuffix("\n") finally source.close()
  }
  private val artifact = take(CandidateAdmissionProducerAbi.parseC(
    resource("/workflow/goal-phase-candidate-admission-producer-abi.json")))
  private val definition = take(bindCandidateDefinitionC(take(CandidateWorkflowAbi.parseC(
    resource("/workflow/goal-phase-workflow-abi.json"), artifact, "src/main/cozy/sm-workflow.cml"))))
  private val spi = artifact.models.head.requiredSpi.head
  private val review = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.identity.value),
    spi.actionIdentity.value, StateMachineOperationIdentity(spi.operation.service.value, spi.operation.name.value),
    spi.operation.inputType.map(x => StateMachineInputTypeReference(x.value)),
    spi.operation.resultType.map(x => StateMachineResultTypeReference(x.value)),
    StateMachineRequiredOperationMetadata(ContextContract("managed-context", Vector.empty, Vector.empty),
      CompletionContract("managed-completion", Vector.empty), EvidenceContract("managed-evidence", Vector.empty), Vector.empty))
  private val config = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("managed-goal")),
    MigrationPolicy("v1"), RetentionPolicy("v1"), LeasePolicy("v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("managed-goal")))
  private val component = SmWorkflowComponent.componentId
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "managedgoal", name))
  private val instances = collection("instances")
  private val continuations = collection("continuations")
  private val declaration = take(EventStorePersistenceDeclaration.createC("events",
    collection("cursor"), collection("events"), collection("indexes"), Vector(
      EventStorePersistenceDeclaration.WorkflowGrant(component, "workflow", instances, continuations))))
  private val policy = take(EventPersistencePolicy.createC("managed-goal-v1", Vector(
    EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
  private val initial = InstanceRecord(InstanceIdentity("managed-goal-instance"), definition, initialRevision,
    Lifecycle.NotStarted, None, CorrelationReference("correlation"), CausationReference("start"),
    None, None, None, Vector.empty)
  private val active = take(initial.appendC(initialRevision, HistoryEntry(HistorySequence(1L),
    initialRevision.next, Lifecycle.Active, Some(ProgressionReference("step.Awaiting")),
    initial.correlation, initial.causation, None, None, None)))
  private val input = StepCloseRequest("close-managed", "step-managed", "a" * 64, "b" * 64, Vector.empty)
  private val requirement = ExecutionRequirement(Vector(CapabilityRequirement("review-step-capability")),
    RiskLevel("standard"), ReasoningLevel.Deep, true)
  private val presentation = MinimalPresentation("Review Step", "Awaiting full review")
  private val hostRecordConfiguration: Map[String, ConfigurationValue] = Map(
    GoalPhaseHostRecordReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    GoalPhaseHostRecordReadPolicy.prefix + "selection-collection" -> ConfigurationValue.StringValue(collection("hostselections").print),
    GoalPhaseHostRecordReadPolicy.prefix + "association-collection" -> ConfigurationValue.StringValue(collection("hostsubmissions").print),
    GoalPhaseHostRecordReadPolicy.prefix + "authorization-collection" -> ConfigurationValue.StringValue(collection("hostauthorizations").print),
    GoalPhaseHostRecordReadPolicy.prefix + "decision-collection" -> ConfigurationValue.StringValue(collection("hostdecisions").print))
  private def host(path: Path, recordConfiguration: Map[String, ConfigurationValue] = hostRecordConfiguration) =
    take(SubsystemEventStoreBootstrap.createC(policy, declaration,
    ScopeContext(ScopeKind.Subsystem, "managed-goal", None, context.observability),
    ResolvedConfiguration(HostConfiguration(Map(
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)) ++ recordConfiguration), ConfigurationTrace.empty)))
  private def storage(host: SubsystemEventStoreBootstrap) = take(WorkflowInstancePersistenceBinding.bindC(
    component, host.subsystem, ComponentDataStore.Environment(
      org.goldenport.cncf.config.ResolvedParameters.fromResolvedConfiguration(host.subsystem.configuration),
      Some(host.subsystem.configuration)), WorkflowInstancePersistenceBinding.Declaration(config, Vector(definition), instances)))
  private def issued(host: SubsystemEventStoreBootstrap) = take(WorkflowManagedIssuedWork.bindC(
    host.subsystem, component, config, StateMachineOperationIdentity("GoalPhaseService", "submitStepReview"),
    Vector(definition), instances, continuations, GoalPhaseStepCloseJson.payloadCodec))
  private def seed(host: SubsystemEventStoreBootstrap): Unit = {
    val binding = storage(host)
    take(binding.createC(initial))
    take(binding.appendC(initial.identity, initial.revision, active.history.head)) shouldBe active
  }
  private def instancePersistence(host: SubsystemEventStoreBootstrap): WorkflowInstancePersistence =
    instancePersistence(storage(host))

  private def instancePersistence(binding: WorkflowInstancePersistenceBinding): WorkflowInstancePersistence = {
    new WorkflowInstancePersistence {
      def create(configuration: Configuration, record: InstanceRecord) = binding.createC(record)
      def load(configuration: Configuration, identity: InstanceIdentity) = binding.loadC(identity)
      def append(configuration: Configuration, identity: InstanceIdentity, revision: InstanceRevision, entry: HistoryEntry) =
        Consequence.stateConflict("managed Operation must not use independent append")
    }
  }
  private def invoke(host: SubsystemEventStoreBootstrap, input: StepCloseRequest = this.input): Consequence[OperationResponse] = {
    val persistence = instancePersistence(host)
    val work = issued(host)
    val operation = take(GoalPhaseStepCloseServiceOperation.bindC(() => Instant.parse("2026-10-01T00:00:00Z"),
      new GoalPhaseStepCloseServiceOperation.InvocationPort {
        def resolveC(submission: String) =
          if (submission == input.submissionIdentity) WorkflowHandle.fromRecordC(component, active)
          else Consequence.stateConflict("foreign submission")
      }, review, persistence, config, ContinuationRuntime.empty, work.issuedPersistence,
      requirement, presentation,
      new GoalPhaseStepClosureBoundary.SnapshotPort {
        def loadC(step: String) = Consequence.stateConflict("review issue must not close Step")
      }, new GoalPhaseStepClosureBoundary.CommitPort {
        def commitC(request: GoalPhaseStepClosureBoundary.CommitRequest,
          snapshot: GoalPhaseStepClosureBoundary.ArtifactSnapshot) =
          Consequence.stateConflict("review issue must not commit Git")
      }, () => throw new IllegalStateException("managed route must not call legacy UoW"),
      managedIssued = Some(work)))
    val request = Request.of(component = component.name, service = "GoalPhaseService", operation = "requestStepClose",
      arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(input))))
    operation.createOperationRequest(request).flatMap {
      case action: CommandAction => action.createCall(ActionCall.Core(action, context, None, None)).execute()
      case _ => Consequence.stateConflict("expected generated command action")
    }
  }

  private def installReview(host: SubsystemEventStoreBootstrap,
    repairPorts: Option[GoalPhaseManagedRepairPublication.Ports] = None,
    authorityProjection: Option[GoalPhaseManagedReviewAssembly.AuthorityProjection] = None
  ): Option[GoalPhaseManagedRepairPublication] = {
    val subsystem = RuntimeBindingAdmissionFixture.admit(host.subsystem)
    var publisher: Option[GoalPhaseManagedRepairPublication] = None
    val factory = new Component.PrimaryComponentFactory {
      protected def create_Component(params: ComponentCreate): Component = new Component() {}
      protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = {
        comp.coreOption shouldBe None
        publisher = repairPorts.map(ports => take(GoalPhaseManagedRepairPublication.bindC(params,
          GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations), ports)))
        val completion = take(GoalPhaseManagedReviewAssembly.bindC(params,
          GoalPhaseManagedReviewAssembly.Declaration(config, definition, instances, continuations),
          GoalPhaseManagedReviewAssembly.Ports(artifact,
            repairPorts.map(_.asOf).getOrElse(() => Instant.parse("2026-10-01T00:00:00Z")),
            (_, _) => Consequence.success(Vector.empty), authorityProjection = authorityProjection)))
        val repairCompletion = repairPorts.map(ports => take(GoalPhaseManagedRepairCompletionAssembly.bindC(
          params, GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations),
          ports, (_, _) => Consequence.success(Vector.empty))))
        val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(
          spec.ServiceDefinition(name = "GoalPhaseService",
            operations = spec.OperationDefinitionGroup(NonEmptyVector(completion, repairCompletion.toVector))))))
        Component.Core.create(component.name, component, ComponentInstanceId.default(component), protocol)
      }
    }
    val appComponent = take(factory.createPrimaryC(ComponentCreate(subsystem, ComponentOrigin.Builtin)))
    subsystem.add(appComponent)
    publisher
  }

  private def reviewRequest(result: ContinuationResult[ReviewStepResult]): Request =
    Request.of(component = component.name, service = "GoalPhaseService", operation = "submitStepReview",
      arguments = List(Argument("arg1", take(WorkflowResultJsonV1.encodeC(result,
        GoalPhaseReviewCompletionOperation.payloadCodec)))))

  private def repairRequest(result: ContinuationResult[RepairStepResult]): Request =
    Request.of(component = component.name, service = "GoalPhaseService", operation = "submitStepRepair",
      arguments = List(Argument("arg1", take(WorkflowResultJsonV1.encodeC(result,
        GoalPhaseRepairCompletionOperation.payloadCodec)))))

  private def installStart(host: SubsystemEventStoreBootstrap, selection: ProfileStartAdmission.SelectionPort): Unit = {
    val subsystem = RuntimeBindingAdmissionFixture.admit(host.subsystem)
    val factory = new Component.PrimaryComponentFactory {
      protected def create_Component(params: ComponentCreate): Component = new Component() {}
      protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = {
        comp.coreOption shouldBe None
        val start = take(GoalPhaseManagedStartAssembly.bindC(params,
          GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations), selection))
        val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition(
          name = "GoalPhaseService", operations = spec.OperationDefinitionGroup(NonEmptyVector.of(start))))))
        Component.Core.create(component.name, component, ComponentInstanceId.default(component), protocol)
      }
    }
    subsystem.add(take(factory.createPrimaryC(ComponentCreate(subsystem, ComponentOrigin.Builtin))))
  }

  private def startRequest(command: GoalPhaseStartCommand): Request =
    Request.of(component = component.name, service = "GoalPhaseService", operation = "startGoalPhase",
      arguments = List(Argument("arg1", GoalPhaseStartServiceOperation.encodeCommand(command))))

  private def installPath(host: SubsystemEventStoreBootstrap, selection: ProfileStartAdmission.SelectionPort,
    invocation: GoalPhaseStepCloseServiceOperation.InvocationPort, unexpected: () => Unit,
    repairSnapshot: Option[GoalPhaseStepClosureBoundary.SnapshotPort] = None,
    authorityProjection: Option[GoalPhaseManagedReviewAssembly.AuthorityProjection] = None,
    hostRecordReaders: Boolean = false, initialArtifactMaximumBytes: Option[Int] = None,
    closureDecisionTemplate: Option[GoalPhaseClosureDecisionTemplate] = None,
    humanDecision: Option[GoalPhaseHumanDecisionServiceOperation.Host] = None,
    nativeHumanVerifier: Option[GoalPhaseHumanInteractionVerification.Verifier] = None,
    replaceExisting: Boolean = false,
    acceptedReviewFollowup: GoalPhaseManagedComponentFactory.AcceptedReviewFollowup =
      GoalPhaseManagedComponentFactory.AcceptedReviewFollowup.StopOnly): Unit = {
    val subsystem = RuntimeBindingAdmissionFixture.admit(host.subsystem)
    val descriptor = org.goldenport.cncf.component.ComponentDescriptor(name = Some(component.name),
      componentName = Some(component.name), version = Some("0.1.0"), schemaVersion = Some(3),
      componentId = Some(component))
    val params = ComponentCreate(subsystem, ComponentOrigin.Builtin, Vector(descriptor)).withInstanceMetadata(
      org.goldenport.cncf.component.ComponentInstanceMetadata(component.name, config = Map(
        GoalPhaseRuntimeParameters.storeName.name -> config.workflowStore.name,
        GoalPhaseRuntimeParameters.storeIdentity.name -> config.workflowStore.identity.value,
        GoalPhaseRuntimeParameters.migrationPolicy.name -> config.migrationPolicy.value,
        GoalPhaseRuntimeParameters.retentionPolicy.name -> config.retentionPolicy.value,
        GoalPhaseRuntimeParameters.leasePolicy.name -> config.leasePolicy.value,
        GoalPhaseRuntimeParameters.instances.name -> instances.print,
        GoalPhaseRuntimeParameters.continuations.name -> continuations.print), componentId = Some(component)))
    locally {
        val declared = GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations)
        val ports = GoalPhaseManagedIssueAssembly.Ports(() => Instant.parse("2026-10-01T00:00:00Z"),
          invocation, review, requirement, presentation,
          repairSnapshot.getOrElse(new GoalPhaseStepClosureBoundary.SnapshotPort {
            def loadC(step: String) = { unexpected(); Consequence.stateConflict("unexpected closure snapshot") }
          }), new GoalPhaseStepClosureBoundary.CommitPort {
            def commitC(request: GoalPhaseStepClosureBoundary.CommitRequest,
              snapshot: GoalPhaseStepClosureBoundary.ArtifactSnapshot) = {
              unexpected(); Consequence.stateConflict("unexpected Git commit")
            }
          }, GoalPhaseClosureAuthority.Ports(new GoalPhaseClosureAuthority.AuthorizationPort {
            def loadC(intent: GoalPhaseClosureAuthority.Intent) = {
              unexpected(); Consequence.stateConflict("unexpected closure authority")
            }
          }, new GoalPhaseClosureAuthority.DecisionPort {
            def resolveC(intent: GoalPhaseClosureAuthority.Intent) = {
              unexpected(); Consequence.stateConflict("unexpected closure Decision")
            }
          }))
        GoalPhaseManagedIssueAssembly.bindC(params, declared, ports.copy(authority = null)) shouldBe
          a[Consequence.Failure[?]]
        val servicePorts = GoalPhaseManagedServiceAssembly.Ports(selection, ports, artifact,
          (_, _) => Consequence.success(Vector.empty), authorityProjection)
        GoalPhaseManagedServiceAssembly.bindReviewSliceC(params, declared, servicePorts.copy(selection = null)) shouldBe
          a[Consequence.Failure[?]]
        GoalPhaseManagedServiceAssembly.bindReviewSliceC(params, declared, servicePorts.copy(events = null)) shouldBe
          a[Consequence.Failure[?]]
        GoalPhaseManagedServiceAssembly.bindReviewSliceC(params, declared,
          servicePorts.copy(issue = ports.copy(authority = null))) shouldBe a[Consequence.Failure[?]]
        val repairSpi = artifact.models.head.requiredSpi.find(_.actionIdentity.value == "RepairStep").get
        val repair = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(repairSpi.identity.value),
          repairSpi.actionIdentity.value, StateMachineOperationIdentity(repairSpi.operation.service.value,
            repairSpi.operation.name.value), repairSpi.operation.inputType.map(x => StateMachineInputTypeReference(x.value)),
          repairSpi.operation.resultType.map(x => StateMachineResultTypeReference(x.value)), review.metadata)
        val repairPorts = GoalPhaseManagedServiceAssembly.RepairPorts(
          GoalPhaseManagedRepairPublication.Ports(artifact, ports.asOf, repair,
            ExecutionRequirement(Vector(CapabilityRequirement("repair-step-capability")),
              RiskLevel("standard"), ReasoningLevel.Deep, false)), (_, _) => Consequence.success(Vector.empty))
        GoalPhaseManagedServiceAssembly.bindRepairSliceC(params, declared, servicePorts, null) shouldBe
          a[Consequence.Failure[?]]
        GoalPhaseManagedServiceAssembly.bindRepairSliceC(params, declared, servicePorts,
          repairPorts.copy(events = null)) shouldBe a[Consequence.Failure[?]]
        GoalPhaseManagedServiceAssembly.bindRepairSliceC(params, declared, servicePorts,
          repairPorts.copy(publication = repairPorts.publication.copy(artifact = null))) shouldBe
          a[Consequence.Failure[?]]
        val foreignReviews = Vector(
          review.copy(identity = StateMachineRequiredOperationIdentity("foreign-capability")),
          review.copy(actionIdentity = "ForeignAction"),
          review.copy(operation = StateMachineOperationIdentity("ForeignService", "reviewStep")),
          review.copy(inputType = Some(StateMachineInputTypeReference("ForeignInput"))),
          review.copy(resultType = Some(StateMachineResultTypeReference("ForeignResult"))),
          review.copy(inputType = null), review.copy(resultType = null),
          review.copy(metadata = null),
          review.copy(metadata = review.metadata.copy(completionContract = null)),
          review.copy(metadata = review.metadata.copy(evidenceContract = null)))
        foreignReviews.foreach { foreign =>
          GoalPhaseManagedServiceAssembly.bindRepairSliceC(params, declared,
            servicePorts.copy(issue = ports.copy(review = foreign)), repairPorts) shouldBe a[Consequence.Failure[?]]
          GoalPhaseManagedServiceAssembly.bindReviewSliceC(params, declared,
            servicePorts.copy(issue = ports.copy(review = foreign))) shouldBe a[Consequence.Failure[?]]
        }
        GoalPhaseManagedServiceAssembly.bindRepairSliceC(params, declared,
          servicePorts.copy(issue = ports.copy(requirement = requirement.copy(reviewRequired = false))),
          repairPorts) shouldBe a[Consequence.Failure[?]]
        GoalPhaseManagedServiceAssembly.bindRepairSliceC(params, declared,
          servicePorts.copy(issue = ports.copy(requirement = requirement.copy(capabilities = Vector.empty))),
          repairPorts) shouldBe a[Consequence.Failure[?]]
        val catalog = take(GoalPhaseRuntimeCatalog.parseC(resource("/workflow/goal-phase-workflow-abi.json"),
          resource("/workflow/goal-phase-candidate-admission-producer-abi.json")))
        val formattingOnlyCatalog = take(GoalPhaseRuntimeCatalog.parseC(resource("/workflow/goal-phase-workflow-abi.json") + "\n",
          resource("/workflow/goal-phase-candidate-admission-producer-abi.json")))
        new GoalPhaseManagedComponentFactory(null, declared, servicePorts, repairPorts).primaryFactory
          .createPrimaryC(params) shouldBe a[Consequence.Failure[?]]
        new GoalPhaseManagedComponentFactory(formattingOnlyCatalog, declared, servicePorts, repairPorts).primaryFactory
          .createPrimaryC(params) shouldBe a[Consequence.Success[?]]
        GoalPhaseManagedComponentFactory.configuredC(null, servicePorts, repairPorts) shouldBe
          a[Consequence.Failure[?]]
        GoalPhaseManagedComponentFactory.configuredC(catalog, null, repairPorts) shouldBe
          a[Consequence.Failure[?]]
        GoalPhaseManagedComponentFactory.configuredC(catalog, servicePorts, null) shouldBe
          a[Consequence.Failure[?]]
        GoalPhaseManagedComponentFactory.configuredC(catalog, servicePorts.copy(artifact = null), repairPorts) shouldBe
          a[Consequence.Failure[?]]
        GoalPhaseManagedComponentFactory.configuredC(catalog, servicePorts,
          repairPorts.copy(publication = repairPorts.publication.copy(artifact = null))) shouldBe
          a[Consequence.Failure[?]]
        val factory = if (hostRecordReaders) {
          val hostPorts = GoalPhaseManagedComponentFactory.HostPorts(ports.asOf, review, requirement,
            presentation, ports.snapshot, ports.commit, artifact, servicePorts.events, initialArtifactMaximumBytes,
            closureDecisionTemplate, humanDecision, nativeHumanVerifier, acceptedReviewFollowup)
          GoalPhaseManagedComponentFactory.configuredHostRecordsC(catalog, null, repairPorts) shouldBe
            a[Consequence.Failure[?]]
          GoalPhaseManagedComponentFactory.configuredHostRecordsC(catalog, hostPorts.copy(snapshot = null), repairPorts) shouldBe
            a[Consequence.Failure[?]]
          GoalPhaseManagedComponentFactory.configuredHostRecordsC(catalog, hostPorts.copy(nativeHumanVerifier = Some(null)), repairPorts)
            .isSuccess shouldBe false
          GoalPhaseManagedComponentFactory.configuredHostRecordsC(catalog, hostPorts.copy(acceptedReviewFollowup = null), repairPorts)
            .isSuccess shouldBe false
          val stopTemplate = GoalPhaseClosureDecisionTemplate(StateMachineRequiredOperationIdentity("host.step-close-authority"),
            StateMachineOperationIdentity("HumanBoundaryFixture", "decideStepClose"),
            StateMachineOperationIdentity("HumanBoundaryFixture", "recordStepCloseAuthority"))
          GoalPhaseManagedComponentFactory.configuredHostRecordsC(catalog, hostPorts.copy(
            acceptedReviewFollowup = GoalPhaseManagedComponentFactory.AcceptedReviewFollowup.NativeHuman,
            closureDecisionTemplate = Some(stopTemplate)), repairPorts).isSuccess shouldBe false
          take(GoalPhaseManagedComponentFactory.configuredHostRecordsC(catalog, hostPorts, repairPorts))
        } else take(GoalPhaseManagedComponentFactory.configuredC(catalog, servicePorts, repairPorts))
        val built = take(factory.primaryFactory.createPrimaryC(params))
        val configured = take(GoalPhaseRuntimeParameters.decodeC(built.initializationParameters, catalog))
        configured.configuration shouldBe declared.configuration
        configured.instances shouldBe declared.instances
        configured.continuations shouldBe declared.continuations
        GoalPhaseRuntimeCatalog.admitBindingC(catalog, configured, servicePorts.artifact) shouldBe
          a[Consequence.Success[?]]
        built shouldBe a[SmWorkflowComponent]
        built.mcpReadyServices shouldBe Set.empty
        if (nativeHumanVerifier.nonEmpty) {
          val services = built.protocol.services.services.filter(_.name == "GoalPhaseService")
          services.size shouldBe 1
          val operations = services.head.operations.operations.toVector.filter(
            _.name == GoalPhaseNativeHumanQuestion.completionOperation.operation)
          operations.size shouldBe 1
          val installed = operations.head
          val generated = SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseHumanDecisionOperation
          // Actual configured native binding, not fabricated producer metadata or Human consent.
          installed.specification.content.descriptiveAttributes shouldBe generated.specification.content.descriptiveAttributes
          installed.specification.content.nameAttributes shouldBe BaseContent.simple(generated.name).nameAttributes
          installed.specification.request should be theSameInstanceAs WorkflowJsonCommandRequest.definition
          installed.specification.response should be theSameInstanceAs spec.ResponseDefinition.void
        }
        if (nativeHumanVerifier.nonEmpty || replaceExisting) subsystem.upsert(Vector(built)) else subsystem.add(built)
    }
  }

  "GoalPhase managed suspension Operation" should {
    "publish a native human question only from the retained accepted review and recover its current-call successor" in {
      import org.goldenport.cncf.blob.*
      import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
      import org.goldenport.cncf.component.builtin.blob.BlobComponent
      import org.goldenport.bag.Bag
      import org.goldenport.datatype.ContentType
      import org.simplemodeling.model.datatype.EntityId
      for ((nativeAnswer, automatic, interrupted, realBlob) <- Vector((false, false, false, false), (true, false, false, false),
          (true, false, false, true), (true, true, false, false), (true, true, true, false),
          (true, true, false, true), (true, true, true, true))) {
      Given("actual Start, review issue/completion and a durable selection installed by an explicit test-only trusted installer")
      val path = Files.createTempDirectory("goal-native-reviewed-call-").resolve("host.sqlite")
      val workflow = GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations)
      val snapshotBytes = GoalPhaseStepClosureBoundary.ArtifactSnapshot(Vector[Byte](0, 1, -1, 10), Vector[Byte](3, 2, 1))
      var observedSnapshot = snapshotBytes
      def digest(bytes: Vector[Byte]) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes.toArray).map(byte => f"${byte & 0xff}%02x").mkString
      val input = this.input.copy(programDigest = digest(snapshotBytes.program), planningDigest = digest(snapshotBytes.planning))
      var snapshotReads = 0
      var failSnapshot = false
      var blobReads = 0
      val payloadRoot = Option.when(realBlob)(Files.createTempDirectory("goal-native-followup-blobs-").toRealPath())
      val pair = payloadRoot.map { root =>
        val store = new LocalBlobStore(root, name = "native-followup-artifacts")
        def put(name: String, bytes: Vector[Byte]) = {
          val identity = take(EntityId.bridgeFromParts("cncf", name, BlobRepository.CollectionId, Instant.EPOCH, name))
          val stored = take(store.put(BlobPutRequest(identity, BlobKind.Binary, Some(name + ".bin"),
            ContentType.APPLICATION_OCTET_STREAM), Bag.binary(bytes.toArray)))
          GoalPhaseBlobArtifactSnapshot.Reference(stored.id, stored.storageRef)
        }
        GoalPhaseBlobArtifactSnapshot.References("native-followup-artifact-pair", 0L,
          put("native_program", snapshotBytes.program), put("native_planning", snapshotBytes.planning))
      }
      val artifactConfiguration = hostRecordConfiguration.updated("textus.subsystem.user-mode", ConfigurationValue.StringValue("standalone")) ++
        Map(GoalPhaseArtifactReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
          GoalPhaseArtifactReadPolicy.prefix + "reference-collection" -> ConfigurationValue.StringValue(collection("artifactreferences").print))
      val recordGrant = take(org.goldenport.cncf.datastore.ComponentRecordStorageDeclaration.createC(
        Vector(collection("hostsubmissions"), collection("artifactreferences")).map(value =>
          org.goldenport.cncf.datastore.ComponentRecordStorageDeclaration.CreateGrant(component, "workflow", value))))
      def currentHost(): SubsystemEventStoreBootstrap = if (!realBlob) host(path,
        hostRecordConfiguration.updated("textus.subsystem.user-mode", ConfigurationValue.StringValue("standalone")))
      else {
        val current = take(SubsystemEventStoreBootstrap.createC(policy, declaration,
          ScopeContext(ScopeKind.Subsystem, "managed-goal", None, context.observability),
          ResolvedConfiguration(HostConfiguration(Map("textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
            "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)) ++ artifactConfiguration), ConfigurationTrace.empty),
          None, org.goldenport.cncf.path.AliasResolver.empty, recordGrant))
        val store = new LocalBlobStore(payloadRoot.get, name = "native-followup-artifacts")
        current.subsystem.findComponent(BuiltinComponentIdentity.BLOB).get.withPort(Component.Port.of(new BlobComponent.BlobService {
          def maxByteSize = 64L
          def blobStore = new BlobStore {
            def name = store.name
            def put(request: BlobPutRequest, payload: org.goldenport.bag.BinaryBag) = store.put(request, payload)
            def get(reference: BlobStorageRef) = {
              blobReads += 1
              if (failSnapshot) Consequence.stateConflict("test-owned Blob service temporarily unavailable")
              else store.get(reference)
            }
            def delete(reference: BlobStorageRef) = store.delete(reference)
            def accessUrl(reference: BlobStorageRef) = store.accessUrl(reference)
            def status() = store.status()
          }
        }))
        current
      }
      val assessedSnapshot = new GoalPhaseCallScopedSnapshot.Port {
        def loadForCallC(step: String, capsule: ActionCall.Core) = _snapshot_lease_c(capsule).flatMap { _ =>
          snapshotReads += 1
          if (failSnapshot) Consequence.stateConflict("fixture snapshot temporarily unavailable")
          else if (step == input.stepIdentity) Consequence.success(observedSnapshot)
          else Consequence.stateConflict("fixture snapshot Step differs")
        }
      }
      val followupPolicy = if (automatic) GoalPhaseManagedComponentFactory.AcceptedReviewFollowup.NativeHuman
        else GoalPhaseManagedComponentFactory.AcceptedReviewFollowup.StopOnly
      val command = GoalPhaseStartCommand(GoalPhaseStartInput("phase-2", "repository"),
        "native-selection", "local-participant", "native-start-key")
      val selected = ProfileStartAdmission.Selection(command.selectionIdentity,
        StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"), definition.workflowIdentity,
        definition.workflowRevision, command.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
        command.idempotencyKey, None, Some(GoalPhaseStartCommandAdapter.selectedInput(command.input)))
      val reader = new ProfileSelectionDataStorePort("workflow", collection("hostselections"))
      var installed = false
      val installSelection = new ProfileStartAdmission.CallScopedSelectionPort {
        def loadForCallC(identity: String, capsule: ActionCall.Core) = {
          final class Installer(val core: ActionCall.Core) extends org.goldenport.cncf.action.ProcedureActionCall {
            def installC() = {
              given ExecutionContext = executionContext
              val space = executionContext.dataStoreSpace
              val prior = space.captureBinding()
              try for {
                _ <- bind_component_application_datastore_c("workflow")
                store <- space.dataStore(collection("hostselections"))
                wire <- ProfileSelectionJsonV1.encodeStorageC(selected)
                _ <- store.create(collection("hostselections"), DataStore.StringEntryId(identity),
                  org.goldenport.record.Record.data(ProfileSelectionJsonV1.storageField -> wire))
              } yield ()
              finally space.restoreBinding(prior)
            }
            def execute() = Consequence.stateConflict("test-only selection installation has no public Operation")
          }
          (if (installed) Consequence.unit else new Installer(capsule).installC().map { _ => installed = true })
            .flatMap(_ => reader.loadForCallC(identity, capsule))
        }
      }
      val actor = _registration_context()
      var started = Option.empty[WorkflowHandle]
      var unexpected = 0
      val initialInvocation = new GoalPhaseStepCloseServiceOperation.InvocationPort {
        def resolveC(identity: String) = started.map(Consequence.success).getOrElse(Consequence.stateConflict("Start is missing"))
      }
      val first = currentHost()
      val work = try {
        installPath(first, installSelection, initialInvocation, () => unexpected += 1)
        started = Some(take(first.subsystem.executeOperationResponseInContext(startRequest(command), actor)) match {
          case OperationResponse.Scalar(wire: String) => take(WorkflowWaitJsonV1.decodeC(wire)).handle
          case other => fail(s"Expected actual Start WAIT: $other")
        })
        if (realBlob) {
          Given("real Blob bytes and a Start-created instance with explicit submission/catalog Create grants")
          installPath(first, reader, initialInvocation, () => unexpected += 1, hostRecordReaders = true,
            initialArtifactMaximumBytes = Some(64), replaceExisting = true, acceptedReviewFollowup = followupPolicy)
          val registration = GoalPhaseSubmissionRegistration.Request("native-followup-registration", input.submissionIdentity,
            "native-followup-registration-key", command)
          def commandRequest(operation: String, wire: String) = Request.of(component = component.name, service = "GoalPhaseService",
            operation = operation, arguments = List(Argument("arg1", wire)))
          When("public registration and artifact Operations retain the exact submission and Blob pair")
          take(first.subsystem.executeOperationResponseInContext(commandRequest(GoalPhaseRegistrationServiceOperation.operationName,
            GoalPhaseRegistrationServiceOperation.encodeCommand(registration)), actor))
          take(first.subsystem.executeOperationResponseInContext(commandRequest(GoalPhaseArtifactRegistrationServiceOperation.operationName,
            GoalPhaseArtifactRegistrationServiceOperation.encodeCommand(GoalPhaseArtifactRegistrationServiceOperation.Command(input, pair.get))), actor))
          Then("actual bytes are verified before the managed catalog is available, without Workflow progression")
          blobReads shouldBe 2
          take(storage(first).loadC(started.get.instanceIdentity)).get.revision shouldBe InstanceRevision(1L)
        }
        val issued = take(first.subsystem.executeOperationResponseInContext(Request.of(component = component.name,
          service = "GoalPhaseService", operation = "requestStepClose",
          arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(input)))), actor)) match {
          case OperationResponse.Scalar(wire: String) => take(WorkflowWorkOrderJsonV1.decodeC(wire, GoalPhaseStepCloseJson.payloadCodec))
          case other => fail(s"Expected actual review WorkOrder: $other")
        }
        issued
      } finally take(first.shutdownC())
      val request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
      val candidate = take(StepCloseRequestAdapter.submissionC(input)).candidate.reference
      val evidence = StepReviewEvidence("native-reviewed-result", candidate.revision,
        "2026-09-30T23:59:00Z", "full-step", "actual candidate reviewed", "receipt")
      val accepted = ContinuationResult(work.handle, request.runId, request.continuationId,
        request.expectedRevision, request.context.snapshot,
        TypedValue("ReviewStepResult", ReviewStepResult("accept", "review passed", Vector(evidence))),
        ContextReference(evidence.evidenceIdentity, candidate.revision), Vector.empty, ExecutionEvidence(Vector.empty))
      val reviewing = currentHost()
      var automaticResponse = Option.empty[OperationResponse]
      try {
        val interruptedauthority = Option.when(interrupted)(GoalPhaseManagedReviewAssembly.AuthorityProjection(
          assessedSnapshot, GoalPhaseClosureAuthority.Ports(new GoalPhaseClosureAuthority.CallScopedAuthorizationPort {
            def loadForCallC(intent: GoalPhaseClosureAuthority.Intent, capsule: ActionCall.Core) =
              _snapshot_lease_c(capsule).flatMap(_ => Consequence.stateConflict("test-owned current authority reader temporarily unavailable"))
          }, new GoalPhaseClosureAuthority.DecisionPort {
            def resolveC(intent: GoalPhaseClosureAuthority.Intent) =
              Consequence.stateConflict("native follow-up never publishes a stop-only Decision")
          }), nativeQuestion = Some(workflow)))
        if (automatic && interrupted) installReview(reviewing, authorityProjection = interruptedauthority)
        else if (automatic) installPath(reviewing, reader, initialInvocation, () => unexpected += 1,
          repairSnapshot = Some(assessedSnapshot), authorityProjection = interruptedauthority,
          hostRecordReaders = !interrupted, acceptedReviewFollowup = followupPolicy,
          initialArtifactMaximumBytes = Option.when(realBlob)(64))
        else installReview(reviewing)
        failSnapshot = interrupted
        When("the real managed review completion accepts the issued full-Step judgment after host reopen")
        val result = reviewing.subsystem.executeOperationResponseInContext(reviewRequest(accepted), actor)
        if (interrupted) result.isSuccess shouldBe false
        else {
          val response = take(result)
          if (automatic) automaticResponse = Some(response)
        }
        Then("accepted review remains retained, with native follow-up only after a successful actual authority observation")
        val reviewed = take(storage(reviewing).loadC(started.get.instanceIdentity)).get
        reviewed.revision shouldBe InstanceRevision(if (automatic && !interrupted) 4L else 3L)
        reviewed.suspension.nonEmpty shouldBe (automatic && !interrupted)
        snapshotReads shouldBe 0
        if (realBlob) {
          snapshotReads shouldBe 0
          blobReads shouldBe 2
        }
        reviewing.subsystem.executeOperationResponseInContext(reviewRequest(accepted), actor).isSuccess shouldBe false
      } finally take(reviewing.shutdownC())

      if (!nativeAnswer) {
        Given("Spec: docs/spec/goal-phase-closure-applicability.md; Rule: R5; Example: E19; an actual accepted review and an owning assessment, with the secondary selection collection unresolved")
        val assessedhost = host(path, hostRecordConfiguration
          .updated("textus.subsystem.user-mode", ConfigurationValue.StringValue("standalone"))
          .updated(GoalPhaseHostRecordReadPolicy.prefix + "selection-collection",
            ConfigurationValue.StringValue(collection("unobservedselections").print)))
        var independent = Option.empty[Consequence[GoalPhaseHumanDecisionContract.Question]]
        var published = Option.empty[GoalPhaseHumanDecisionContract.Question]
        var competing = Option.empty[Consequence[GoalPhaseHumanDecisionContract.Question]]
        val publisher = new GoalPhaseStepCloseServiceOperation.CallScopedInvocationPort {
          def resolveForCallC(identity: String, capsule: ActionCall.Core): Consequence[WorkflowHandle] = {
            given ExecutionContext = capsule.executionContext
            for {
              _ <- GoalPhaseParticipantAdmission.admitC(command.participantIdentity, capsule.executionContext)
              bindings <- WorkflowComponentStorage.bindC(ComponentCreate(capsule.component.get.subsystem.get, ComponentOrigin.Builtin),
                component, config, Vector(definition), instances, continuations)
              completion <- bindings.bindCompletionC(StateMachineOperationIdentity("GoalPhaseService", "submitStepReview"),
                GoalPhaseStepCloseJson.payloadCodec, GoalPhaseReviewCompletionOperation.payloadCodec,
                StateMachineResultTypeReference("ReviewStepResult"), (_, _) => Consequence.success(Vector.empty))
              retained <- completion.loadRetainedC(request.continuationId).map(_.get)
              records <- bindings.bindInstancesC()
              current <- records.loadC(started.get.instanceIdentity).map(_.get)
              assessment <- GoalPhaseClosureAuthority.assessReviewedC(artifact, work, retained.completion.result,
                Instant.parse("2026-10-01T00:00:00Z"), current, new GoalPhaseClosureAuthority.AuthorizationPort {
                  def loadC(intent: GoalPhaseClosureAuthority.Intent) = _snapshot_lease_c(capsule).map(_ => None)
                })
              _ = independent = Some(GoalPhaseManagedNativeHumanQuestion.advanceForCallC(request.continuationId,
                HumanDecisionContractFixture.choices, capsule, workflow))
              question <- GoalPhaseManagedNativeHumanQuestion.publishAssessedForCallC(retained.completion.result, current,
                assessment, capsule, workflow)
              _ = published = Some(question)
              _ = competing = Some(GoalPhaseManagedNativeHumanQuestion.publishAssessedForCallC(retained.completion.result,
                current, assessment, capsule, workflow))
              result <- Consequence.stateConflict("Assessed native fixture stops before another review or command")
            } yield result
          }
        }
        try {
          installPath(assessedhost, reader, publisher, () => unexpected += 1)
          When("the owning assessment publishes without resolving the participant selection again, then a stale publisher attempts the same transition")
          val publicationresult = assessedhost.subsystem.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalPhaseService",
            operation = "requestStepClose", arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(input)))), actor)
          Then("independent input resolution refuses, selected publication succeeds, and the transition boundary refuses the stale predecessor")
          publicationresult shouldBe a[Consequence.Failure[?]]
          independent.get shouldBe a[Consequence.Failure[?]]
          published.nonEmpty shouldBe true
          competing.get shouldBe a[Consequence.Failure[?]]
          take(storage(assessedhost).loadC(started.get.instanceIdentity)).get.revision shouldBe InstanceRevision(4L)
          take(storage(assessedhost).loadC(started.get.instanceIdentity)).get.history.size shouldBe 4
          unexpected shouldBe 0
        } finally take(assessedhost.shutdownC())
      }

      var observed = Option.empty[Consequence[GoalPhaseHumanDecisionContract.Question]]
      var escaped = Option.empty[ActionCall.Core]
      var selectedReview = request.continuationId
      var choices = HumanDecisionContractFixture.choices
      var selectedDeclaration = workflow
      val probe = new GoalPhaseStepCloseServiceOperation.CallScopedInvocationPort {
        def resolveForCallC(identity: String, capsule: ActionCall.Core) = {
          escaped = Some(capsule)
          observed = Some(GoalPhaseManagedNativeHumanQuestion.advanceForCallC(selectedReview, choices, capsule, selectedDeclaration))
          Consequence.stateConflict("test-only native publication probe stops before another review issue")
        }
      }
      def run(current: SubsystemEventStoreBootstrap, caller: ExecutionContext = actor) = {
        observed = None
        current.subsystem.executeOperationResponseInContext(Request.of(component = component.name,
          service = "GoalPhaseService", operation = "requestStepClose",
          arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(input)))), caller) shouldBe a[Consequence.Failure[?]]
        observed.getOrElse(Consequence.stateConflict("native probe was not reached by refused dispatch"))
      }
      val publishing = currentHost()
      val question = try {
        installPath(publishing, reader, probe, () => unexpected += 1)
        When("native advance receives missing review, copied authentication or an uncaptured collection")
        selectedReview = org.goldenport.cncf.workflow.ContinuationIdentity("missing-review")
        run(publishing).isSuccess shouldBe false
        selectedReview = request.continuationId
        run(publishing, ExecutionContext.withSecurityContext(actor, actor.security.copy())).isSuccess shouldBe false
        selectedDeclaration = workflow.copy(instances = collection("foreign_instances"))
        run(publishing).isSuccess shouldBe false
        selectedDeclaration = workflow
        Then("no question or extra history is published by those rejected current-call inputs")
        take(storage(publishing).loadC(started.get.instanceIdentity)).get.revision shouldBe
          InstanceRevision(if (!nativeAnswer || (automatic && !interrupted)) 4L else 3L)
        When("the admitted participant advances from the actual accepted review")
        def publicAdvance(wire: String = GoalPhaseNativeHumanAdvanceOperation.encodeCommand(request.continuationId)) =
          publishing.subsystem.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalPhaseService",
            operation = GoalPhaseNativeHumanAdvanceOperation.operationName, arguments = List(Argument("arg1", wire))), actor)
        def questionFromResponse(response: OperationResponse) = response match {
          case OperationResponse.Scalar(wire: String) =>
            val json = io.circe.parser.parse(wire).toOption.get
            json.hcursor.get[String]("schemaVersion").toOption shouldBe Some(GoalPhaseNativeHumanAdvanceOperation.resultSchema)
            take(GoalPhaseHumanQuestionRecord.decodeJsonC(json.hcursor.downField("question").focus.get.noSpaces))
          case other => fail(s"Expected native Workflow question: $other")
        }
        val actual = if (nativeAnswer) {
          installPath(publishing, reader, probe, () => unexpected += 1, hostRecordReaders = true, replaceExisting = true,
            repairSnapshot = Some(assessedSnapshot), acceptedReviewFollowup = followupPolicy,
            initialArtifactMaximumBytes = Option.when(realBlob)(64))
          _configure_registration_cli(publishing)
          val operationName = if (automatic) GoalPhaseReviewRecoveryServiceOperation.operationName
            else GoalPhaseNativeHumanAdvanceOperation.operationName
          val commandWire = if (automatic) GoalPhaseReviewRecoveryServiceOperation.encodeCommand(request.continuationId)
            else GoalPhaseNativeHumanAdvanceOperation.encodeCommand(request.continuationId)
          val selector = s"${component.name}/GoalPhaseService/$operationName";
          {
            publishing.subsystem.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalPhaseService",
              operation = operationName, arguments = List(Argument("arg1", commandWire))),
              ExecutionContext.withSecurityContext(actor, actor.security.copy())).isSuccess shouldBe false
            failSnapshot = false
            if (interrupted || !automatic) {
              Given("Spec: docs/spec/goal-phase-closure-applicability.md; Rule: R5; Example: E13; retained accepted review and different current bytes for a question-only transition")
              observedSnapshot = snapshotBytes.copy(program = Vector(42.toByte))
              if (realBlob) {
                val reference = pair.get.program.storageRef
                Files.write(payloadRoot.get.resolve(reference.container).resolve(reference.key),
                  snapshotBytes.program.map(_ => 42.toByte).toArray)
              }
              When("the owning advance or recovery publishes a native question without executing commit")
              take(publishing.subsystem.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalPhaseService",
                operation = operationName, arguments = List(Argument("arg1", commandWire))), actor))
              Then("the actual question transition uses managed state and CAS without an artifact-content certificate")
              take(storage(publishing).loadC(started.get.instanceIdentity)).get.revision shouldBe InstanceRevision(4L)
              take(storage(publishing).loadC(started.get.instanceIdentity)).get.suspension.nonEmpty shouldBe true
              snapshotReads shouldBe 0
              blobReads shouldBe (if (realBlob) 2 else 0)
              observedSnapshot = snapshotBytes
              if (realBlob) {
                val reference = pair.get.program.storageRef
                Files.write(payloadRoot.get.resolve(reference.container).resolve(reference.key), snapshotBytes.program.toArray)
              }
            }
          }
          val wire = take(new org.goldenport.cncf.cli.CncfRuntime().executeCommandResponse(publishing.subsystem,
            Array(selector, "--json", commandWire))).print;
          {
            if (automatic) automaticResponse.foreach(value => OperationResponse.Scalar(wire) shouldBe value)
            val expectedSnapshotReads = 0
            val expectedBlobReads = 2
            if (!realBlob) snapshotReads shouldBe expectedSnapshotReads
            else blobReads shouldBe expectedBlobReads
            take(publishing.subsystem.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalPhaseService",
              operation = operationName, arguments = List(Argument("arg1", commandWire))), actor)) shouldBe OperationResponse.Scalar(wire)
            if (!realBlob) snapshotReads shouldBe expectedSnapshotReads
            else blobReads shouldBe expectedBlobReads
          }
          questionFromResponse(OperationResponse.Scalar(wire))
        } else take(run(publishing))
        Then("native revision four retains the exact reviewed candidate and declared choices, without command authority")
        actual.intent.input.candidate shouldBe input.copy(reviewEvidence = Vector(evidence))
        actual.request.expectedRevision shouldBe StateMachineRevision("4")
        actual.choices shouldBe choices
        actual.request.completionOperation shouldBe GoalPhaseNativeHumanQuestion.completionOperation
        val pending = take(storage(publishing).loadC(started.get.instanceIdentity)).get
        pending.revision shouldBe InstanceRevision(4L)
        pending.currentProgression shouldBe Some(ProgressionReference("HumanPending"))
        pending.history.size shouldBe 4
        pending.suspension.get.continuationIdentity.value shouldBe actual.request.continuationId.value
        When("retry, later choice proposals and an escaped call attempt to reuse that question")
        if (nativeAnswer) {
          questionFromResponse(take(publicAdvance())) shouldBe actual
          publishing.subsystem.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalPhaseService",
            operation = GoalPhaseNativeHumanAdvanceOperation.operationName,
            arguments = List(Argument("arg1", GoalPhaseNativeHumanAdvanceOperation.encodeCommand(request.continuationId)))),
            ExecutionContext.withSecurityContext(actor, actor.security.copy())).isSuccess shouldBe false
          val extra = io.circe.parser.parse(GoalPhaseNativeHumanAdvanceOperation.encodeCommand(request.continuationId)).toOption.get
            .mapObject(_.add("choices", io.circe.Json.arr())).noSpaces
          publicAdvance(extra).isSuccess shouldBe false
        } else {
          take(run(publishing)) shouldBe actual
          choices = choices.updated(0, choices.head.copy(label = "Another visible question"))
          take(run(publishing)) shouldBe actual
        }
        choices = HumanDecisionContractFixture.choices
        GoalPhaseManagedNativeHumanQuestion.advanceForCallC(request.continuationId, choices, escaped.get, workflow).isSuccess shouldBe false
        Then("the saved pending declarations are used, not compared to the later proposal, and no effect callback runs")
        take(storage(publishing).loadC(started.get.instanceIdentity)).get shouldBe pending
        org.goldenport.cncf.testutil.NativeHumanManagedHostObservation.counts(publishing) shouldBe (1, 0)
        unexpected shouldBe 0
        actual
      } finally take(publishing.shutdownC())
      def queryRequest(identity: org.goldenport.cncf.workflow.ContinuationIdentity = question.request.continuationId) =
        Request.of(component = component.name, service = "GoalPhaseService", operation = GoalPhaseHumanCompletionQueryOperation.operationName,
          arguments = List(Argument("arg1", GoalPhaseHumanCompletionQueryOperation.encodeQuery(identity))))
      Given("Spec: docs/spec/goal-phase-closure-applicability.md; Rule: R5; Example: E20; the owning recovery entry has selected the actual accepted review and pending state, but cannot repeat participant selection")
      val selectedhost = host(path, hostRecordConfiguration
        .updated("textus.subsystem.user-mode", ConfigurationValue.StringValue("standalone"))
        .updated(GoalPhaseHostRecordReadPolicy.prefix + "selection-collection",
          ConfigurationValue.StringValue(collection("unobservedselections").print)))
      var independentrecovery = Option.empty[Consequence[GoalPhaseHumanDecisionContract.Question]]
      var selectedrecovery = Option.empty[GoalPhaseHumanDecisionContract.Question]
      val recoverer = new GoalPhaseStepCloseServiceOperation.CallScopedInvocationPort {
        def resolveForCallC(identity: String, capsule: ActionCall.Core): Consequence[WorkflowHandle] = {
          given ExecutionContext = capsule.executionContext
          for {
            _ <- GoalPhaseParticipantAdmission.admitC(command.participantIdentity, capsule.executionContext)
            bindings <- WorkflowComponentStorage.bindC(ComponentCreate(capsule.component.get.subsystem.get, ComponentOrigin.Builtin),
              component, config, Vector(definition), instances, continuations)
            completion <- bindings.bindCompletionC(StateMachineOperationIdentity("GoalPhaseService", "submitStepReview"),
              GoalPhaseStepCloseJson.payloadCodec, GoalPhaseReviewCompletionOperation.payloadCodec,
              StateMachineResultTypeReference("ReviewStepResult"), (_, _) => Consequence.success(Vector.empty))
            retained <- completion.loadRetainedC(request.continuationId).map(_.get)
            records <- bindings.bindInstancesC()
            current <- records.loadC(started.get.instanceIdentity).map(_.get)
            _ = independentrecovery = Some(GoalPhaseManagedNativeHumanQuestion.advanceForCallC(request.continuationId,
              choices, capsule, workflow))
            recovered <- GoalPhaseManagedNativeHumanQuestion.recoverSelectedForCallC(retained.completion.result, current,
              retained.completion.instanceRevision, capsule, workflow)
            _ = selectedrecovery = Some(recovered)
            result <- Consequence.stateConflict("Selected native recovery fixture stops before another review or command")
          } yield result
        }
      }
      try {
        installPath(selectedhost, reader, recoverer, () => unexpected += 1)
        When("the owning recovery selects the saved question without loading review, participant and predecessor again")
        val recoveryresult = selectedhost.subsystem.executeOperationResponseInContext(Request.of(component = component.name, service = "GoalPhaseService",
          operation = "requestStepClose", arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(input)))), actor)
        Then("independent resolution refuses while internal recovery returns the actual saved question, with no append or artifact access")
        recoveryresult shouldBe a[Consequence.Failure[?]]
        independentrecovery.get shouldBe a[Consequence.Failure[?]]
        selectedrecovery shouldBe Some(question)
        take(storage(selectedhost).loadC(started.get.instanceIdentity)).get.history.size shouldBe 4
        snapshotReads shouldBe 0
        blobReads shouldBe (if (realBlob) 2 else 0)
        unexpected shouldBe 0
      } finally take(selectedhost.shutdownC())
      When("a fresh host recovers the question using only the retained review, selection and native history")
      val reopened = currentHost()
      try {
        installPath(reopened, reader, probe, () => unexpected += 1)
        val recovered = take(run(reopened))
        Then("the same question returns without new review, replacement or history append")
        recovered shouldBe question
        val pending = take(storage(reopened).loadC(started.get.instanceIdentity)).get
        pending.history.size shouldBe 4
        if (nativeAnswer) {
          installPath(reopened, reader, probe, () => unexpected += 1, hostRecordReaders = true, replaceExisting = true,
            repairSnapshot = Some(assessedSnapshot), acceptedReviewFollowup = followupPolicy,
            initialArtifactMaximumBytes = Option.when(realBlob)(64))
          val priorReads = snapshotReads
          val priorBlobReads = blobReads
          failSnapshot = true
          val operationName = if (automatic) GoalPhaseReviewRecoveryServiceOperation.operationName
            else GoalPhaseNativeHumanAdvanceOperation.operationName
          val commandWire = if (automatic) GoalPhaseReviewRecoveryServiceOperation.encodeCommand(request.continuationId)
            else GoalPhaseNativeHumanAdvanceOperation.encodeCommand(request.continuationId)
          When("a new configured host recovers an already published native question without fresh assessment")
          val response = take(reopened.subsystem.executeOperationResponseInContext(Request.of(component = component.name,
            service = "GoalPhaseService", operation = operationName,
            arguments = List(Argument("arg1", commandWire))), actor))
          Then("the exact retained question returns without snapshot renewal or history change")
          response match {
            case OperationResponse.Scalar(wire: String) =>
              val json = io.circe.parser.parse(wire).toOption.get
              take(GoalPhaseHumanQuestionRecord.decodeJsonC(json.hcursor.downField("question").focus.get.noSpaces)) shouldBe question
            case other => fail(s"Expected recovered native question: $other")
          }
          snapshotReads shouldBe priorReads
          blobReads shouldBe priorBlobReads
          take(storage(reopened).loadC(started.get.instanceIdentity)).get shouldBe pending
        }
        if (nativeAnswer) {
          Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R5; Example: E17; independently enrolled test-only evidence for this actual native question and answer, with saved display diagnostics")
          val answer = GoalPhaseHumanDecisionContract.Answer(question.choices.head.identity)
          val evidencesnapshot = question.request.context.snapshot.copy(workspaceRevision = Some("old-evidence-workspace-diagnostic"))
          val evidenceQuestion = question.copy(choices = question.choices.map(value => value.copy(label = "Original evidence " + value.label)),
            intent = question.intent.copy(snapshot = evidencesnapshot),
            request = question.request.copy(context = question.request.context.copy(summary = "Original evidence context",
              snapshot = evidencesnapshot)))
          val receipt = GoalPhaseHumanInteractionVerification.Receipt("native-human-proof", "test-only-human-issuer",
            "actual-human", GoalPhaseHumanInteractionVerification.purpose,
            GoalPhaseHumanInteractionVerification.Binding(ComponentInstanceId.default(component), evidenceQuestion, answer, "local-participant"),
            Instant.parse("2026-09-30T23:59:00Z"))
          var nativeVerificationCalls = 0
          var nativeIssuerReads = 0
          val verifier = new GoalPhaseHumanInteractionVerification.Verifier {
            def issuerIdentity = { nativeIssuerReads += 1; receipt.issuer }
            def verifyForCallC(reference: String, expected: GoalPhaseHumanInteractionVerification.Binding, core: ActionCall.Core) =
              { nativeVerificationCalls += 1; Consequence.success(Option.when(reference == receipt.reference &&
                  expected.question.request.continuationId == question.request.continuationId &&
                  expected.answer == answer && expected.submittingParticipant == "local-participant")(receipt)) }
          }
          installPath(reopened, reader, probe, () => unexpected += 1,
            hostRecordReaders = true, nativeHumanVerifier = Some(verifier))
          Then("installing the generated native completion metadata supplies no Human evidence or state transition")
          nativeVerificationCalls shouldBe 0
          take(storage(reopened).loadC(started.get.instanceIdentity)).get shouldBe pending
          val result = ContinuationResult(question.intent.handle, question.request.runId, question.request.continuationId,
            question.request.expectedRevision, question.request.context.snapshot,
            TypedValue(GoalPhaseHumanDecisionContract.resultType, answer), ContextReference(receipt.reference, "1"),
            Vector.empty, ExecutionEvidence(Vector.empty))
          def answerRequest(value: ContinuationResult[GoalPhaseHumanDecisionContract.Answer]) = Request.of(
            component = component.name, service = "GoalPhaseService", operation = "submitGoalPhaseHumanDecision",
            arguments = List(Argument("arg1", take(WorkflowResultJsonV1.encodeC(value, GoalPhaseHumanDecisionContract.answerPayloadCodec)))))
          When("native completion receives an unissued human reference or copied authentication")
          reopened.subsystem.executeOperationResponseInContext(answerRequest(result.copy(resultReference = ContextReference("unissued-proof", "1"))), actor)
            .isSuccess shouldBe false
          reopened.subsystem.executeOperationResponseInContext(answerRequest(result), ExecutionContext.withSecurityContext(actor, actor.security.copy()))
            .isSuccess shouldBe false
          take(storage(reopened).loadC(started.get.instanceIdentity)).get shouldBe pending
          When("the native Provided Operation verifies the actual fixture receipt under the current call")
          take(reopened.subsystem.executeOperationResponseInContext(answerRequest(result), actor)) match {
            case OperationResponse.Scalar(wire: String) =>
              val json = io.circe.parser.parse(wire).toOption.get
              json.hcursor.get[String]("schemaVersion").toOption shouldBe Some(GoalPhaseManagedNativeHumanCompletion.resultSchema)
              take(GoalPhaseHumanAnswerRecord.decodeJsonC(json.hcursor.downField("receipt").focus.get.noSpaces)) shouldBe receipt
            case other => fail(s"Expected committed native answer receipt: $other")
          }
          Then("one atomic native completion retains the actual evidence and adjacent HumanAnswered history without Step closure or copied-question proof")
          nativeIssuerReads shouldBe nativeVerificationCalls
          val answered = take(storage(reopened).loadC(started.get.instanceIdentity)).get
          answered.revision shouldBe InstanceRevision(5L)
          answered.currentProgression shouldBe Some(ProgressionReference("HumanAnswered"))
          answered.lifecycle shouldBe Lifecycle.Active
          answered.suspension shouldBe None
          answered.history.dropRight(1) shouldBe pending.history
          val params = ComponentCreate(reopened.subsystem, ComponentOrigin.Builtin)
          val bindings = take(WorkflowComponentStorage.bindC(params, component, config, Vector(definition), instances, continuations))
          val completion = take(bindings.bindDecisionCompletionC(GoalPhaseNativeHumanQuestion.completionOperation,
            GoalPhaseClosureAuthority.inputCodec, GoalPhaseHumanDecisionContract.answerPayloadCodec,
            StateMachineResultTypeReference("GoalPhaseHumanAnswerResult"), (_, _) => Consequence.success(Vector.empty)))
          take(completion.loadRetainedC(result.continuationId)).get.completion.result shouldBe result
          When("the participant queries the retained completion without requesting renewed human evidence")
          val callsBeforeQuery = nativeVerificationCalls
          take(reopened.subsystem.executeOperationResponseInContext(queryRequest(), actor)) match {
            case OperationResponse.Scalar(wire: String) =>
              val json = io.circe.parser.parse(wire).toOption.get
              json.hcursor.get[String]("schemaVersion").toOption shouldBe Some(GoalPhaseNativeHumanCompletionObservation.schema)
              json.hcursor.get[String]("humanEvidenceReference").toOption shouldBe Some(receipt.reference)
              json.hcursor.get[Long]("completingRevision").toOption shouldBe Some(5L)
              take(WorkflowCompletionRecordJsonV1.decodeC(json.hcursor.get[String]("completion").toOption.get,
                GoalPhaseHumanDecisionContract.answerPayloadCodec)).result shouldBe result
            case other => fail(s"Expected retained native completion query: $other")
          }
          nativeVerificationCalls shouldBe callsBeforeQuery
          reopened.subsystem.executeOperationResponseInContext(queryRequest(), ExecutionContext.withSecurityContext(actor, actor.security.copy()))
            .isSuccess shouldBe false
          reopened.subsystem.executeOperationResponseInContext(queryRequest(org.goldenport.cncf.workflow.ContinuationIdentity("goal-phase-native-human:missing")), actor)
            .isSuccess shouldBe false
          reopened.subsystem.executeOperationResponseInContext(answerRequest(result), actor).isSuccess shouldBe false
          take(storage(reopened).loadC(started.get.instanceIdentity)).get shouldBe answered
        } else {
        When("another writer advances beyond the pending revision")
        take(storage(reopened).appendC(pending.identity, pending.revision,
          pending.history.last.copy(sequence = HistorySequence(5L), revision = InstanceRevision(5L),
            causation = CausationReference("test-concurrent-advance"), suspension = None)))
        val stale = run(reopened)
        Then("old accepted review cannot republish or downgrade the current instance")
        stale.isSuccess shouldBe false
        take(storage(reopened).loadC(started.get.instanceIdentity)).get.history.size shouldBe 5
        }
        org.goldenport.cncf.testutil.NativeHumanManagedHostObservation.counts(reopened) shouldBe (1, 0)
        unexpected shouldBe 0
      } finally take(reopened.shutdownC())
      if (nativeAnswer) {
        val retainedHost = currentHost()
        try {
          installPath(retainedHost, reader, probe, () => unexpected += 1, hostRecordReaders = true)
          val retained = take(storage(retainedHost).loadC(started.get.instanceIdentity)).get
          retained.revision shouldBe InstanceRevision(5L)
          retained.currentProgression shouldBe Some(ProgressionReference("HumanAnswered"))
          val bindings = take(WorkflowComponentStorage.bindC(ComponentCreate(retainedHost.subsystem, ComponentOrigin.Builtin),
            component, config, Vector(definition), instances, continuations))
          val completion = take(bindings.bindDecisionCompletionC(GoalPhaseNativeHumanQuestion.completionOperation,
            GoalPhaseClosureAuthority.inputCodec, GoalPhaseHumanDecisionContract.answerPayloadCodec,
            StateMachineResultTypeReference("GoalPhaseHumanAnswerResult"), (_, _) => Consequence.success(Vector.empty)))
          take(completion.loadRetainedC(question.request.continuationId)).get.completion.instanceRevision shouldBe InstanceRevision(5L)
          When("a fresh configured host has no human verifier but reads the already committed result")
          val recoveredResponse = take(retainedHost.subsystem.executeOperationResponseInContext(queryRequest(), actor))
          val recoveredWire = recoveredResponse match {
            case OperationResponse.Scalar(wire: String) =>
              val json = io.circe.parser.parse(wire).toOption.get
              json.hcursor.get[String]("choiceIdentity").toOption shouldBe Some(question.choices.head.identity)
              json.hcursor.get[Long]("currentRevision").toOption shouldBe Some(5L)
              wire
            case other => fail(s"Expected recovered read-only answer: $other")
          }
          When("normal CNCF CLI dispatch resolves local ingress and reads the same retained completion without a server")
          _configure_registration_cli(retainedHost)
          val cli = new org.goldenport.cncf.cli.CncfRuntime()
          val cliResult = take(cli.executeCommandResponse(retainedHost.subsystem,
            Array(s"${component.name}/GoalPhaseService/${GoalPhaseHumanCompletionQueryOperation.operationName}", "--json",
              GoalPhaseHumanCompletionQueryOperation.encodeQuery(question.request.continuationId)))).print
          cliResult shouldBe recoveredWire
          take(storage(retainedHost).loadC(started.get.instanceIdentity)).get shouldBe retained
          unexpected shouldBe 0
        } finally take(retainedHost.shutdownC())
      }
      }
    }

    "run repair callbacks in the dispatch context rather than the binding context" in {
      Given("SQLite issued review and completion bindings made without an execution context")
      val path = Files.createTempDirectory("goal-call-scoped-repair-").resolve("host.sqlite")
      def scalar(response: OperationResponse): String = response match {
        case OperationResponse.Scalar(value: String) => value
        case other => fail(s"Expected Workflow JSON: $other")
      }
      val first = host(path)
      val work = try {
        seed(first)
        take(WorkflowWorkOrderJsonV1.decodeC(scalar(take(invoke(first))), GoalPhaseStepCloseJson.payloadCodec))
      } finally take(first.shutdownC())
      val request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
      val candidate = take(StepCloseRequestAdapter.submissionC(input)).candidate.reference
      val submitted = ContinuationResult(work.handle, request.runId, request.continuationId,
        request.expectedRevision, request.context.snapshot,
        TypedValue("ReviewStepResult", ReviewStepResult("repair", "repair required", Vector(
          StepReviewEvidence("call-scoped-review", candidate.revision, "2026-09-30T23:59:00Z",
            "full-step", "repair required", "receipt")))),
        ContextReference("call-scoped-review", candidate.revision), Vector.empty, ExecutionEvidence(Vector.empty))
      val second = host(path)
      try {
        val subsystem = RuntimeBindingAdmissionFixture.admit(second.subsystem)
        val factory = new Component.PrimaryComponentFactory {
          protected def create_Component(params: ComponentCreate): Component = new Component() {}
          protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = {
            val declared = GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations)
            val repairSpi = artifact.models.head.requiredSpi.find(_.actionIdentity.value == "RepairStep").get
            val repair = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(repairSpi.identity.value),
              repairSpi.actionIdentity.value, StateMachineOperationIdentity(repairSpi.operation.service.value,
                repairSpi.operation.name.value), repairSpi.operation.inputType.map(x => StateMachineInputTypeReference(x.value)),
              repairSpi.operation.resultType.map(x => StateMachineResultTypeReference(x.value)), review.metadata)
            val publisher = take(GoalPhaseManagedRepairPublication.bindC(params, declared,
              GoalPhaseManagedRepairPublication.Ports(artifact, () => Instant.parse("2026-10-01T00:00:00Z"), repair,
                ExecutionRequirement(Vector(CapabilityRequirement("repair-step-capability")),
                  RiskLevel("standard"), ReasoningLevel.Deep, false))))
            val storage = take(WorkflowComponentStorage.bindC(params, component, config,
              Vector(definition), instances, continuations))
            val readOnly = instancePersistence(take(storage.bindInstancesC()))
            val reviewOperation = StateMachineOperationIdentity("GoalPhaseService", "submitStepReview")
            val reviewIssued = take(storage.bindIssuedC(reviewOperation, GoalPhaseStepCloseJson.payloadCodec))
            val reviewCompletion = take(storage.bindCompletionC(reviewOperation, GoalPhaseStepCloseJson.payloadCodec,
              GoalPhaseReviewCompletionOperation.payloadCodec, StateMachineResultTypeReference("ReviewStepResult"),
              (_, _) => Consequence.success(Vector.empty)))
            val reviewBound = take(GoalPhaseReviewCompletionOperation.bindManagedRepairC(artifact,
              () => Instant.parse("2026-10-01T00:00:00Z"), reviewIssued.issuedPersistence, readOnly, config,
              () => throw new IllegalStateException("No legacy UoW"), reviewCompletion, publisher)(using null))
            val repairOperation = StateMachineOperationIdentity("GoalPhaseService", "submitStepRepair")
            val repairIssued = take(storage.bindIssuedC(repairOperation, GoalPhaseStepCloseJson.payloadCodec))
            val repairCompletion = take(storage.bindCompletionC(repairOperation, GoalPhaseStepCloseJson.payloadCodec,
              GoalPhaseRepairCompletionOperation.payloadCodec, StateMachineResultTypeReference("RepairStepResult"),
              (_, _) => Consequence.success(Vector.empty)))
            val followup = GoalPhaseRepairCompletionOperation.ReReviewPorts(review, requirement, presentation,
              new GoalPhaseStepClosureBoundary.SnapshotPort {
                def loadC(step: String) = Consequence.success(
                  GoalPhaseStepClosureBoundary.ArtifactSnapshot(Vector[Byte](1, 2), Vector[Byte](3, 4)))
              })
            val repairBound = take(GoalPhaseRepairCompletionOperation.bindManagedC(publisher,
              repairIssued.issuedPersistence, readOnly, config, repairCompletion, Some(followup))(using null))
            Component.Core.create(component.name, component, ComponentInstanceId.default(component),
              Protocol(services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition(name = "GoalPhaseService",
                operations = spec.OperationDefinitionGroup(NonEmptyVector(reviewBound, Vector(repairBound))))))))
          }
        }
        subsystem.add(take(factory.createPrimaryC(ComponentCreate(subsystem, ComponentOrigin.Builtin))))
        When("review completion dispatch requests repair using its own live capsule")
        val repairWork = take(WorkflowWorkOrderJsonV1.decodeC(
          scalar(take(subsystem.executeOperationResponse(reviewRequest(submitted)))), GoalPhaseStepCloseJson.payloadCodec))
        val repairRequestBody = repairWork.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
        Then("repair is atomically issued despite the absent binding-time execution context")
        repairRequestBody.completionOperation shouldBe StateMachineOperationIdentity("GoalPhaseService", "submitStepRepair")
        val repaired = ContinuationResult(repairWork.handle, repairRequestBody.runId, repairRequestBody.continuationId,
          repairRequestBody.expectedRevision, repairRequestBody.context.snapshot,
          TypedValue("RepairStepResult", RepairStepResult("completed", "repaired", "call-scoped-repair")),
          ContextReference("call-scoped-repair", "1"), Vector.empty, ExecutionEvidence(Vector.empty))
        When("repair completion dispatch admits the result and publishes re-review")
        val reReview = take(WorkflowWorkOrderJsonV1.decodeC(
          scalar(take(subsystem.executeOperationResponse(repairRequest(repaired)))), GoalPhaseStepCloseJson.payloadCodec))
        Then("fresh review uses the current call and neither completion can be replayed")
        reReview.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request.completionOperation shouldBe
          StateMachineOperationIdentity("GoalPhaseService", "submitStepReview")
        subsystem.executeOperationResponse(reviewRequest(submitted)) shouldBe a[Consequence.Failure[?]]
        subsystem.executeOperationResponse(repairRequest(repaired)) shouldBe a[Consequence.Failure[?]]
        take(storage(second).loadC(active.identity)).get.revision shouldBe InstanceRevision(6L)
      } finally take(second.shutdownC())
    }

    "read current bounded Blob artifacts after reopen without an unchanged content or selection certificate" in {
      import org.goldenport.cncf.blob.*
      import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
      import org.goldenport.cncf.component.builtin.blob.BlobComponent
      import org.goldenport.bag.Bag
      import org.goldenport.datatype.ContentType
      import org.simplemodeling.model.datatype.EntityId
      import GoalPhaseBlobArtifactSnapshot.*
      Given("owned local Blob payloads and a host reference pair, independent of public request fields")
      val path = Files.createTempDirectory("goal-blob-host-").resolve("host.sqlite")
      val payloadRoot = Files.createTempDirectory("goal-blob-payloads-").toRealPath()
      val program = Vector[Byte](0, 1, -1, 10)
      val planning = Vector[Byte](3, 2, 1)
      def id(name: String) = take(EntityId.bridgeFromParts("cncf", name, BlobRepository.CollectionId, Instant.EPOCH, name))
      val initialStore = new LocalBlobStore(payloadRoot, name = "snapshot-local")
      def put(name: String, bytes: Vector[Byte]) = take(initialStore.put(BlobPutRequest(id(name), BlobKind.Binary,
        Some(name + ".bin"), ContentType.APPLICATION_OCTET_STREAM), Bag.binary(bytes.toArray)))
      def ref(value: BlobPutResult) = Reference(value.id, value.storageRef)
      val programRef = ref(put("program", program))
      val planningRef = ref(put("planning", planning))
      val pair = References("fixture-artifact-selection", 0L, programRef, planningRef)
      var currentPair = pair
      var changeSelectionOnRead = false
      var blobGets = 0
      var selections = 0
      var denyAfterSelection: Option[Int] = None
      val resolver = new ReferencePort {
        def resolveForCallC(scope: GoalPhaseCallScopedSnapshot.Scope, core: ActionCall.Core) = _snapshot_lease_c(core).flatMap { _ =>
          selections += 1
          if (denyAfterSelection.contains(selections)) Consequence.stateConflict("Test host denied actual reference resolution")
          else if (scope.request.stepIdentity == input.stepIdentity && scope.request.submissionIdentity == input.submissionIdentity &&
              scope.handle.instanceIdentity == active.identity && scope.purpose == GoalPhaseCallScopedSnapshot.Purpose.Candidate)
            Consequence.success(currentPair)
          else Consequence.stateConflict("Host has no artifact pair for this Step")
        }
      }
      val snapshot = take(GoalPhaseBlobArtifactSnapshot.bindC("workflow", resolver, 64))
      var observed: Option[Consequence[GoalPhaseStepClosureBoundary.ArtifactSnapshot]] = None
      var escaped: Option[ActionCall.Core] = None
      var unexpected = 0
      val artifactScope = take(GoalPhaseCallScopedSnapshot.scopeC(
        take(WorkflowHandle.fromRecordC(component, active)), input, GoalPhaseCallScopedSnapshot.Purpose.Candidate))
      val probe = new GoalPhaseStepCloseServiceOperation.CallScopedInvocationPort {
        def resolveForCallC(identity: String, core: ActionCall.Core): Consequence[WorkflowHandle] = {
          escaped = Some(core)
          observed = Some(snapshot.loadForScopeCallC(artifactScope, core))
          Consequence.stateConflict("Test-only artifact probe stops before Workflow progression")
        }
      }
      val selection = new ProfileStartAdmission.SelectionPort {
        def loadC(identity: String) = { unexpected += 1; Consequence.stateConflict("No Start in artifact probe") }
      }
      def install(current: SubsystemEventStoreBootstrap, store: BlobStore): Unit = {
        val blob = current.subsystem.findComponent(BuiltinComponentIdentity.BLOB).getOrElse(fail("Canonical Blob component missing"))
        blob.withPort(Component.Port.of(new BlobComponent.BlobService {
          def blobStore = new BlobStore {
            def name = store.name
            def put(request: BlobPutRequest, payload: org.goldenport.bag.BinaryBag) = store.put(request, payload)
            def get(ref: BlobStorageRef) = store.get(ref).map { result =>
              blobGets += 1
              if (changeSelectionOnRead) {
                changeSelectionOnRead = false
                currentPair = currentPair.copy(selectionRevision = currentPair.selectionRevision + 1L)
              }
              result
            }
            def delete(ref: BlobStorageRef) = store.delete(ref)
            def accessUrl(ref: BlobStorageRef) = store.accessUrl(ref)
            def status() = store.status()
          }
          def maxByteSize = 64L
        }))
        installPath(current, selection, probe, () => unexpected += 1)
      }
      def run(current: SubsystemEventStoreBootstrap) = {
        observed = None
        val close = Request.of(component = component.name, service = "GoalPhaseService", operation = "requestStepClose",
          arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(input))))
        current.subsystem.executeOperationResponse(close) shouldBe a[Consequence.Failure[?]]
        observed.get
      }
      val first = host(path)
      try {
        install(first, initialStore)
        When("the ordinary managed Operation reads from its Subsystem's real local Blob service")
        take(run(first)) shouldBe GoalPhaseStepClosureBoundary.ArtifactSnapshot(program, planning)
        Then("both actual payloads match the pinned pair and no selection or Workflow effect is fabricated")
        selections shouldBe 1
        snapshot.loadC(input.stepIdentity) shouldBe a[Consequence.Failure[?]]
        snapshot.loadForScopeCallC(artifactScope, escaped.get) shouldBe a[Consequence.Failure[?]]
        selections shouldBe 1
        unexpected shouldBe 0
      } finally take(first.shutdownC())
      val reopened = host(path)
      try {
        install(reopened, new LocalBlobStore(payloadRoot, name = "snapshot-local"))
        When("a fresh managed host and BlobStore reopen the owned payload files without in-memory metadata")
        take(run(reopened)) shouldBe GoalPhaseStepClosureBoundary.ArtifactSnapshot(program, planning)
        Then("the same real bytes and exact host-selected reference pair survive resource reopen")
        selections shouldBe 2

        When("the test host denies actual reference resolution for the next call")
        denyAfterSelection = Some(3)
        val revoked = run(reopened)
        Then("the reader returns no snapshot and does not access Blob payloads")
        revoked shouldBe a[Consequence.Failure[?]]
        selections shouldBe 3
        denyAfterSelection = None

        When("the host selection revision changes during real Blob get while both payload files stay valid")
        val getsBeforeRefresh = blobGets
        changeSelectionOnRead = true
        val changedSelection = run(reopened)
        Then("the selected inputs are read without a second whole-pair equality certificate")
        take(changedSelection) shouldBe GoalPhaseStepClosureBoundary.ArtifactSnapshot(program, planning)
        blobGets shouldBe getsBeforeRefresh + 2
        selections shouldBe 4
        currentPair.selectionRevision shouldBe 1L
        currentPair.program shouldBe pair.program
        currentPair.planning shouldBe pair.planning

        When("a fresh ordinary read selects the new stable revision")
        take(run(reopened)) shouldBe GoalPhaseStepClosureBoundary.ArtifactSnapshot(program, planning)
        Then("the new observation is verified without replaying or relabelling the stale result")
        selections shouldBe 5

        When("the owned planning payload changes at equal length while its metadata still reports the old digest")
        Files.write(payloadRoot.resolve(planningRef.storageRef.container).resolve(planningRef.storageRef.key),
          Vector[Byte](9, 9, 9).toArray)
        val changed = run(reopened)
        Then("actual current bytes are returned; the workflow adds no content-tamper gate to Blob storage")
        take(changed) shouldBe GoalPhaseStepClosureBoundary.ArtifactSnapshot(program, Vector[Byte](9, 9, 9))
        selections shouldBe 6
        snapshot.loadForScopeCallC(artifactScope, escaped.get) shouldBe a[Consequence.Failure[?]]
        selections shouldBe 6
        unexpected shouldBe 0
      } finally take(reopened.shutdownC())
    }

    "install an initial catalog through admitted storage and verify real bytes before returning its retained pair" in {
      import org.goldenport.cncf.blob.*
      import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
      import org.goldenport.cncf.component.builtin.blob.BlobComponent
      import org.goldenport.bag.Bag
      import org.goldenport.datatype.ContentType
      import org.simplemodeling.model.datatype.EntityId
      Given("an admitted Start, production submission registration, exact Create grants and real Blob bytes")
      val path = Files.createTempDirectory("goal-artifact-install-").resolve("host.sqlite")
      val payloadRoot = Files.createTempDirectory("goal-artifact-install-blobs-").toRealPath()
      val records = collection("artifactreferences")
      val associations = collection("hostsubmissions")
      val artifactConfig = hostRecordConfiguration ++ Map(
        GoalPhaseArtifactReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
        GoalPhaseArtifactReadPolicy.prefix + "reference-collection" -> ConfigurationValue.StringValue(records.print))
      val grant = take(org.goldenport.cncf.datastore.ComponentRecordStorageDeclaration.createC(
        Vector(associations, records).map(value =>
          org.goldenport.cncf.datastore.ComponentRecordStorageDeclaration.CreateGrant(component, "workflow", value))))
      def declaredHost() = take(SubsystemEventStoreBootstrap.createC(policy, declaration,
        ScopeContext(ScopeKind.Subsystem, "managed-goal", None, context.observability),
        ResolvedConfiguration(HostConfiguration(Map(
          "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
          "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)) ++ artifactConfig),
          ConfigurationTrace.empty), None, org.goldenport.cncf.path.AliasResolver.empty, grant))
      val command = GoalPhaseStartCommand(GoalPhaseStartInput("phase-2", "repository"),
        "artifact-selection", "local-participant", "artifact-start-key")
      val selected = ProfileStartAdmission.Selection(command.selectionIdentity,
        StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"), definition.workflowIdentity,
        definition.workflowRevision, command.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
        command.idempotencyKey, None, Some(GoalPhaseStartCommandAdapter.selectedInput(command.input)))
      // Explicit test selection source, not evidence for a production human entry.
      val selection = new ProfileStartAdmission.CallScopedSelectionPort {
        def loadForCallC(identity: String, core: ActionCall.Core) =
          Consequence.success(Option.when(identity == selected.selectionId)(selected))
      }
      val declared = GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations)
      val registration = GoalPhaseSubmissionRegistration.Request("artifact-registration", input.submissionIdentity,
        "artifact-registration-key", command)
      val blobStore = new LocalBlobStore(payloadRoot, name = "installation-blobs")
      def put(name: String, bytes: Vector[Byte]) = {
        val id = take(EntityId.bridgeFromParts("cncf", name, BlobRepository.CollectionId, Instant.EPOCH, name))
        val stored = take(blobStore.put(BlobPutRequest(id, BlobKind.Binary, Some(name + ".bin"),
          ContentType.APPLICATION_OCTET_STREAM), Bag.binary(bytes.toArray)))
        GoalPhaseBlobArtifactSnapshot.Reference(stored.id, stored.storageRef)
      }
      val pair = GoalPhaseBlobArtifactSnapshot.References("initial-artifact-pair", 0L,
        put("install_program", Vector[Byte](0, -1, 2)), put("install_planning", Vector[Byte](3, 4)))
      val candidate = input
      var scope: GoalPhaseCallScopedSnapshot.Scope = null
      var requestedPair = pair
      var registrationMode = true
      var installed: Option[Consequence[GoalPhaseBlobArtifactSnapshot.References]] = None
      var registered: Option[Consequence[GoalPhaseSubmissionRegistration.Record]] = None
      var captured: Option[ActionCall.Core] = None
      var blobReads = 0
      val probe = new GoalPhaseStepCloseServiceOperation.CallScopedInvocationPort {
        def resolveForCallC(identity: String, core: ActionCall.Core): Consequence[WorkflowHandle] = {
          captured = Some(core)
          if (registrationMode) registered = Some(GoalPhaseManagedRegistration.registerForCallC(
            registration, core, declared, selection, associations))
          else installed = Some(GoalPhaseManagedArtifactInstallation.installForCallC(scope, requestedPair,
            core, declared, selection, associations, records, 64))
          Consequence.stateConflict("Initial installation probe stops before Workflow progression")
        }
      }
      def install(current: SubsystemEventStoreBootstrap, store: BlobStore): Unit = {
        val blob = current.subsystem.findComponent(BuiltinComponentIdentity.BLOB).get
        blob.withPort(Component.Port.of(new BlobComponent.BlobService {
          def maxByteSize = 64L
          def blobStore = new BlobStore {
            def name = store.name
            def put(request: BlobPutRequest, payload: org.goldenport.bag.BinaryBag) = store.put(request, payload)
            def get(ref: BlobStorageRef) = { blobReads += 1; store.get(ref) }
            def delete(ref: BlobStorageRef) = store.delete(ref)
            def accessUrl(ref: BlobStorageRef) = store.accessUrl(ref)
            def status() = store.status()
          }
        }))
        installPath(current, selection, probe, () => fail("Initial catalog registration cannot commit Git"))
      }
      val actor = _registration_context()
      def run(current: SubsystemEventStoreBootstrap, caller: ExecutionContext = actor): Unit = {
        installed = None; registered = None
        val request = Request.of(component = component.name, service = "GoalPhaseService", operation = "requestStepClose",
          arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(candidate))))
        current.subsystem.executeOperationResponseInContext(request, caller) shouldBe a[Consequence.Failure[?]]
      }
      val first = declaredHost()
      val original = try {
        install(first, blobStore)
        val start = take(first.subsystem.executeOperationResponseInContext(startRequest(command), actor)) match {
          case OperationResponse.Scalar(wire: String) => take(WorkflowWaitJsonV1.decodeC(wire)).handle
          case other => fail(s"Expected initial Start: $other")
        }
        run(first)
        take(registered.get).originalStart.handle shouldBe start
        registrationMode = false
        val originalScope = take(GoalPhaseCallScopedSnapshot.scopeC(start, candidate,
          GoalPhaseCallScopedSnapshot.Purpose.Candidate))
        scope = originalScope
        When("registration is attempted by an anonymous caller, with missing submission or foreign instance")
        run(first, context)
        installed.get shouldBe a[Consequence.Failure[?]]
        for (bad <- Vector(
          take(GoalPhaseCallScopedSnapshot.scopeC(start, candidate.copy(submissionIdentity = "absent-submission"),
            GoalPhaseCallScopedSnapshot.Purpose.Candidate)),
          take(GoalPhaseCallScopedSnapshot.scopeC(start.copy(instanceIdentity = InstanceIdentity("foreign-instance")),
            candidate, GoalPhaseCallScopedSnapshot.Purpose.Candidate)),
          take(GoalPhaseCallScopedSnapshot.scopeC(start, candidate, GoalPhaseCallScopedSnapshot.Purpose.RepairSuccessor)))) {
          scope = bad
          run(first)
          installed.get shouldBe a[Consequence.Failure[?]]
        }
        Then("none can publish a catalog or read any Blob bytes")
        blobReads shouldBe 0
        scope = originalScope
        When("the current authenticated call installs the first candidate and retries exactly")
        run(first)
        take(installed.get) shouldBe pair
        run(first)
        take(installed.get) shouldBe pair
        Then("both results follow real byte checks and committed readback without changing Workflow")
        blobReads shouldBe 4
        take(storage(first).loadC(start.instanceIdentity)).get.history.size shouldBe 1
        When("a different selection revision attempts to reuse the same catalog identity")
        requestedPair = pair.copy(selectionRevision = 1L)
        run(first)
        take(installed.get) shouldBe pair
        blobReads shouldBe 6
        requestedPair = pair
        GoalPhaseManagedArtifactInstallation.installForCallC(originalScope, pair, captured.get,
          declared, selection, associations, records, 64) shouldBe a[Consequence.Failure[?]]
        blobReads shouldBe 6
        originalScope
      } finally take(first.shutdownC())
      val reopened = declaredHost()
      try {
        scope = original
        install(reopened, new LocalBlobStore(payloadRoot, name = "installation-blobs"))
        When("a fresh host reopens and retries without an artifact installer or reference fixture Map")
        run(reopened)
        take(installed.get) shouldBe pair
        blobReads shouldBe 8
        take(storage(reopened).loadC(original.handle.instanceIdentity)).get.history.size shouldBe 1
      } finally take(reopened.shutdownC())
      val denied = host(path, artifactConfig)
      try {
        install(denied, new LocalBlobStore(payloadRoot, name = "installation-blobs"))
        When("the same database is reopened with read declarations but no Create grant")
        run(denied)
        Then("existing equal rows do not confer write authority or cause Blob access")
        installed.get shouldBe a[Consequence.Failure[?]]
        blobReads shouldBe 8
      } finally take(denied.shutdownC())
    }

    "read declared catalog records and real Blob bytes after pool reopen without a fixture reference port" in {
      import org.goldenport.cncf.blob.*
      import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
      import org.goldenport.cncf.component.builtin.blob.BlobComponent
      import org.goldenport.bag.Bag
      import org.goldenport.datatype.ContentType
      import org.simplemodeling.model.datatype.EntityId
      Given("a dedicated artifact declaration, an owned SQLite database and actual local Blob payloads")
      val path = Files.createTempDirectory("goal-artifact-catalog-").resolve("host.sqlite")
      val payloadRoot = Files.createTempDirectory("goal-artifact-catalog-blobs-").toRealPath()
      val records = collection("artifactreferences")
      val artifactConfig = hostRecordConfiguration ++ Map(
        GoalPhaseArtifactReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
        GoalPhaseArtifactReadPolicy.prefix + "reference-collection" -> ConfigurationValue.StringValue(records.print))
      val program = Vector[Byte](0, -1, 2)
      val planning = Vector[Byte](3, 4)
      val blobStore = new LocalBlobStore(payloadRoot, name = "catalog-blobs")
      def put(name: String, bytes: Vector[Byte]) = {
        val id = take(EntityId.bridgeFromParts("cncf", name, BlobRepository.CollectionId, Instant.EPOCH, name))
        val stored = take(blobStore.put(BlobPutRequest(id, BlobKind.Binary, Some(name + ".bin"),
          ContentType.APPLICATION_OCTET_STREAM), Bag.binary(bytes.toArray)))
        GoalPhaseBlobArtifactSnapshot.Reference(stored.id, stored.storageRef)
      }
      val pair = GoalPhaseBlobArtifactSnapshot.References("installed-catalog-pair", 2L, put("program", program), put("planning", planning))
      def scope(step: String) = take(GoalPhaseCallScopedSnapshot.scopeC(take(WorkflowHandle.fromRecordC(component, active)),
        input.copy(stepIdentity = step), GoalPhaseCallScopedSnapshot.Purpose.Candidate))
      val original = scope(input.stepIdentity)
      val wrong = scope("wrong-catalog-scope")
      val malformed = scope("malformed-catalog")
      var requested = original
      var shouldInstall = false
      var installs = 0
      var blobReads = 0
      var escaped: Option[ActionCall.Core] = None
      var observed: Option[Consequence[GoalPhaseStepClosureBoundary.ArtifactSnapshot]] = None
      var currentReader: GoalPhaseArtifactReferenceCatalog.DataStorePort = null
      val snapshot = take(GoalPhaseBlobArtifactSnapshot.bindC("workflow", new GoalPhaseBlobArtifactSnapshot.ReferencePort {
        def resolveForCallC(scope: GoalPhaseCallScopedSnapshot.Scope, core: ActionCall.Core) = currentReader.resolveForCallC(scope, core)
      }, 64))
      val probe = new GoalPhaseStepCloseServiceOperation.CallScopedInvocationPort {
        def resolveForCallC(identity: String, capsule: ActionCall.Core): Consequence[WorkflowHandle] = {
          escaped = Some(capsule)
          final class FixtureInstaller(val core: ActionCall.Core) extends org.goldenport.cncf.action.ProcedureActionCall {
            def installC(): Consequence[Unit] = {
              given ExecutionContext = executionContext
              val space = executionContext.dataStoreSpace
              val prior = space.captureBinding()
              try for {
                _ <- bind_component_application_datastore_c("workflow")
                store <- space.dataStore(records)
                currentRaw <- GoalPhaseArtifactReferenceRecord.encodeStorageC(original, pair, 64)
                legacy = io.circe.parser.parse(currentRaw.substring(currentRaw.indexOf('{'))).toOption.get
                  .mapObject(_.add("schemaVersion", io.circe.Json.fromString("sm-workflow.goal-phase-artifact-references.v1"))
                    .add("scope", io.circe.Json.obj("sourceProgramDigest" -> io.circe.Json.fromString("ignored-legacy-proof"))))
                raw = "sm-workflow.goal-phase-artifact-references-json.v1:" + legacy.noSpaces
                key <- GoalPhaseArtifactReferenceRecord.keyC(original)
                wrongKey <- GoalPhaseArtifactReferenceRecord.keyC(wrong)
                malformedKey <- GoalPhaseArtifactReferenceRecord.keyC(malformed)
                _ <- store.create(records, DataStore.StringEntryId(key), org.goldenport.record.Record.data(
                  GoalPhaseArtifactReferenceRecord.storageField -> raw))
                _ <- store.create(records, DataStore.StringEntryId(wrongKey), org.goldenport.record.Record.data(
                  GoalPhaseArtifactReferenceRecord.storageField -> raw))
                _ <- store.create(records, DataStore.StringEntryId(malformedKey), org.goldenport.record.Record.data(
                  GoalPhaseArtifactReferenceRecord.storageField -> "invalid"))
              } yield ()
              finally space.restoreBinding(prior)
            }
            def execute() = Consequence.stateConflict("Test-only catalog installer is not an Operation")
          }
          val prepared = if (shouldInstall) new FixtureInstaller(capsule).installC().map { _ =>
            shouldInstall = false; installs += 1
          } else Consequence.unit
          observed = Some(prepared.flatMap(_ => snapshot.loadForScopeCallC(requested, capsule)))
          Consequence.stateConflict("Catalog test probe stops before Workflow progression")
        }
      }
      val noStart = new ProfileStartAdmission.SelectionPort {
        def loadC(identity: String) = Consequence.stateConflict("Catalog probe does not start Workflow")
      }
      def install(current: SubsystemEventStoreBootstrap, store: BlobStore): Unit = {
        currentReader = new GoalPhaseArtifactReferenceCatalog.DataStorePort("workflow", records, 64)
        val blob = current.subsystem.findComponent(BuiltinComponentIdentity.BLOB).get
        blob.withPort(Component.Port.of(new BlobComponent.BlobService {
          def maxByteSize = 64L
          def blobStore = new BlobStore {
            def name = store.name
            def put(request: BlobPutRequest, payload: org.goldenport.bag.BinaryBag) = store.put(request, payload)
            def get(ref: BlobStorageRef) = { blobReads += 1; store.get(ref) }
            def delete(ref: BlobStorageRef) = store.delete(ref)
            def accessUrl(ref: BlobStorageRef) = store.accessUrl(ref)
            def status() = store.status()
          }
        }))
        installPath(current, noStart, probe, () => fail("Catalog probe cannot commit"))
      }
      def run(current: SubsystemEventStoreBootstrap) = {
        observed = None
        val request = Request.of(component = component.name, service = "GoalPhaseService", operation = "requestStepClose",
          arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(input))))
        current.subsystem.executeOperationResponse(request) shouldBe a[Consequence.Failure[?]]
        observed.get
      }
      val first = host(path, artifactConfig)
      try {
        install(first, blobStore)
        When("the declared catalog is absent")
        run(first) shouldBe a[Consequence.Failure[?]]
        Then("missing catalog facts never become fallback references or Blob reads")
        blobReads shouldBe 0
        shouldInstall = true
        When("the explicit test-only installer creates owned records during a live managed call")
        take(run(first)) shouldBe GoalPhaseStepClosureBoundary.ArtifactSnapshot(program, planning)
        Then("the production reader obtains the actual scoped row and both Blob payloads from the host")
        installs shouldBe 1
        blobReads shouldBe 2
        currentReader.resolveForCallC(original, escaped.get) shouldBe a[Consequence.Failure[?]]
      } finally take(first.shutdownC())
      val reopened = host(path, artifactConfig)
      try {
        install(reopened, new LocalBlobStore(payloadRoot, name = "catalog-blobs"))
        When("a fresh host and fresh reader reopen the same database and payload root without any installer")
        take(run(reopened)) shouldBe GoalPhaseStepClosureBoundary.ArtifactSnapshot(program, planning)
        Then("catalog selection and real payloads survive common-pool shutdown and reopen")
        installs shouldBe 1
        blobReads shouldBe 4
        for (bad <- Vector(wrong, malformed, scope("absent-catalog"))) {
          requested = bad
          run(reopened) shouldBe a[Consequence.Failure[?]]
        }
        Then("wrong-scope, malformed and missing rows are denied before any further Blob access")
        blobReads shouldBe 4
        requested = original
        currentReader.resolveForCallC(original, escaped.get) shouldBe
          a[Consequence.Failure[?]]
      } finally take(reopened.shutdownC())
      val denied = host(path)
      try {
        install(denied, new LocalBlobStore(payloadRoot, name = "catalog-blobs"))
        When("an otherwise valid reopened host has no artifact read declaration")
        run(denied) shouldBe a[Consequence.Failure[?]]
        Then("existing SQLite bytes and Blob files cannot supply the missing read admission")
        installs shouldBe 1
        blobReads shouldBe 4
      } finally take(denied.shutdownC())
    }

    "project missing closure authority only after the exact managed review is committed" in {
      Given("a managed issued review with real candidate bytes and an empty host authority collection")
      val path = Files.createTempDirectory("goal-managed-authority-projection-").resolve("host.sqlite")
      val program = "managed closure candidate".getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
      val planning = "managed closure plan".getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector
      def digest(bytes: Vector[Byte]) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(bytes.toArray).map(byte => f"${byte & 0xff}%02x").mkString
      val candidateInput = input.copy(programDigest = digest(program), planningDigest = digest(planning))
      def scalar(response: OperationResponse): String = response match {
        case OperationResponse.Scalar(value: String) => value
        case other => fail(s"Expected Workflow JSON: $other")
      }
      val first = host(path)
      val work = try {
        seed(first)
        val response = scalar(take(invoke(first, candidateInput)))
        take(WorkflowWorkOrderJsonV1.decodeC(response, GoalPhaseStepCloseJson.payloadCodec))
      } finally take(first.shutdownC())
      val request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
      val candidate = take(StepCloseRequestAdapter.submissionC(candidateInput)).candidate.reference
      val evidence = StepReviewEvidence("authority-projection-review", candidate.revision,
        "2026-09-30T23:59:00Z", "full-step", "reviewed", "receipt")
      val submitted = ContinuationResult(work.handle, request.runId, request.continuationId,
        request.expectedRevision, request.context.snapshot,
        TypedValue("ReviewStepResult", ReviewStepResult("accept", "review passed", Vector(evidence))),
        ContextReference(evidence.evidenceIdentity, candidate.revision), Vector.empty, ExecutionEvidence(Vector.empty))
      var reads = 0
      var authoritycalls = Vector.empty[ActionCall.Core]
      val reader = new GoalPhaseClosureAuthorizationRecord.DataStorePort("workflow", collection("hostauthorizations"))
      val authority = new GoalPhaseClosureAuthority.CallScopedAuthorizationPort {
        def loadForCallC(intent: GoalPhaseClosureAuthority.Intent, core: ActionCall.Core) = {
          reads += 1
          authoritycalls :+= core
          reader.loadForCallC(intent, core)
        }
      }
      val decisions = new GoalPhaseClosureAuthority.CallScopedDecisionPort {
        def resolveForCallC(intent: GoalPhaseClosureAuthority.Intent, core: ActionCall.Core) =
          Consequence.success(ContinuationRequest(
            StateMachineRunIdentity(intent.handle.instanceIdentity.value),
            org.goldenport.cncf.workflow.ContinuationIdentity(s"authority-decision:${intent.idempotencyKey}"),
            intent.expectedRevision, StateMachineRequiredOperationIdentity("host.step-close-authority"),
            StateMachineOperationIdentity("HumanBoundaryFixture", "decideStepClose"),
            Some(TypedValue(GoalPhaseClosureAuthority.inputCodec.typeIdentity, intent.input)), None,
            ContextBundle("Resolve exact local commit authority",
              Vector(ContextReference(intent.input.candidate.stepIdentity, intent.input.candidateRevision)),
              Vector.empty, intent.snapshot),
            CompletionContract("human-stop-only", Vector.empty), EvidenceContract("human-evidence", Vector.empty),
            StateMachineOperationIdentity("HumanBoundaryFixture", "recordStepCloseAuthority")))
      }
      var snapshotCalls = Vector.empty[ActionCall.Core]
      var snapshotScopes = Vector.empty[GoalPhaseCallScopedSnapshot.Scope]
      val snapshot = new GoalPhaseCallScopedSnapshot.ScopedPort {
        def loadForScopeCallC(scope: GoalPhaseCallScopedSnapshot.Scope, core: ActionCall.Core) = _snapshot_lease_c(core).map { _ =>
          snapshotCalls = snapshotCalls :+ core
          snapshotScopes = snapshotScopes :+ scope
          GoalPhaseStepClosureBoundary.ArtifactSnapshot(program, planning)
        }
      }
      val projection = Some(GoalPhaseManagedReviewAssembly.AuthorityProjection(snapshot,
        GoalPhaseClosureAuthority.Ports(authority, decisions)))
      val second = host(path)
      val reviewed = try {
        installReview(second, authorityProjection = projection)
        When("accepted review completes through normal Subsystem dispatch after host reopen")
        val response = scalar(take(second.subsystem.executeOperationResponse(reviewRequest(submitted))))
        val decision = take(WorkflowDecisionJsonV1.decodeC(response, GoalPhaseClosureAuthority.inputCodec))
        Then("the exact committed judgment precedes missing-authority Decision with no Step closure")
        decision.handle shouldBe work.handle
        reads shouldBe 1
        snapshotCalls shouldBe empty
        snapshotScopes shouldBe empty
        When("snapshot access is attempted without the call or after its managed lease has ended")
        snapshot.loadC(candidateInput.stepIdentity) shouldBe a[Consequence.Failure[?]]
        GoalPhaseCallScopedSnapshot.forCall(snapshot, null).loadC(candidateInput.stepIdentity) shouldBe
          a[Consequence.Failure[?]]
        GoalPhaseCallScopedSnapshot.forCall(snapshot, authoritycalls.head).loadC(candidateInput.stepIdentity) shouldBe
          a[Consequence.Failure[?]]
        take(GoalPhaseCallScopedSnapshot.forScopeC(GoalPhaseCallScopedSnapshot.forCall(snapshot, authoritycalls.head),
          work.handle, candidateInput)).loadC(candidateInput.stepIdentity) shouldBe
          a[Consequence.Failure[?]]
        Then("no escaped view yields bytes or invokes the successful snapshot projection")
        snapshotCalls shouldBe empty
        val record = take(storage(second).loadC(active.identity)).get
        record.revision shouldBe InstanceRevision(3L)
        record.lifecycle shouldBe Lifecycle.Active
        record.suspension shouldBe None
        record.history.size shouldBe 3
        When("the semantic Result is replayed")
        second.subsystem.executeOperationResponse(reviewRequest(submitted)) shouldBe a[Consequence.Failure[?]]
        Then("replay causes no authority lookup or additional history")
        reads shouldBe 1
        snapshotCalls shouldBe empty
        take(storage(second).loadC(active.identity)) shouldBe Some(record)
        record
      } finally take(second.shutdownC())
      val third = host(path)
      try {
        installReview(third, authorityProjection = projection)
        When("another reconstructed host receives the same Result")
        third.subsystem.executeOperationResponse(reviewRequest(submitted)) shouldBe a[Consequence.Failure[?]]
        Then("the committed review remains exact without re-admission or authority effects")
        reads shouldBe 1
        snapshotCalls shouldBe empty
        take(storage(third).loadC(active.identity)) shouldBe Some(reviewed)
      } finally take(third.shutdownC())
      val fourth = host(path)
      try {
        val subsystem = RuntimeBindingAdmissionFixture.admit(fourth.subsystem)
        val factory = new Component.PrimaryComponentFactory {
          protected def create_Component(params: ComponentCreate): Component = new Component() {}
          protected def create_Core(params: ComponentCreate, comp: Component): Component.Core = {
            val invocation = new GoalPhaseStepCloseServiceOperation.InvocationPort {
              def resolveC(identity: String) = if (identity == candidateInput.submissionIdentity)
                Consequence.success(work.handle)
              else Consequence.stateConflict("Explicit test association differs")
            }
            val noCommit = new GoalPhaseStepClosureBoundary.CommitPort {
              def commitC(request: GoalPhaseStepClosureBoundary.CommitRequest,
                bytes: GoalPhaseStepClosureBoundary.ArtifactSnapshot) =
                Consequence.stateConflict("No commit is permitted by this test host")
            }
            val operation = take(GoalPhaseManagedIssueAssembly.bindC(params,
              GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations),
              GoalPhaseManagedIssueAssembly.Ports(() => Instant.parse("2026-10-01T00:00:00Z"),
                invocation, review, requirement, presentation, snapshot, noCommit,
                GoalPhaseClosureAuthority.Ports(authority, decisions))))
            Component.Core.create(component.name, component, ComponentInstanceId.default(component),
              Protocol(services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition("GoalPhaseService",
                spec.OperationDefinitionGroup(NonEmptyVector.of(operation)))))))
          }
        }
        subsystem.add(take(factory.createPrimaryC(ComponentCreate(subsystem, ComponentOrigin.Builtin))))
        When("a pre-reviewed requestStepClose is dispatched on another reconstructed managed host")
        val close = Request.of(component = component.name, service = "GoalPhaseService", operation = "requestStepClose",
          arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(candidateInput.copy(reviewEvidence = Vector(evidence))))))
        val response = scalar(take(subsystem.executeOperationResponse(close)))
        Then("Step-close observes authority once in its own active call and yields Decision without reading artifacts or closing history")
        take(WorkflowDecisionJsonV1.decodeC(response, GoalPhaseClosureAuthority.inputCodec)).handle shouldBe work.handle
        snapshotCalls shouldBe empty
        snapshotScopes shouldBe empty
        authoritycalls.size shouldBe 2
        (authoritycalls.head eq authoritycalls.last) shouldBe false
        reads shouldBe 2
        take(storage(fourth).loadC(active.identity)) shouldBe Some(reviewed)
        GoalPhaseCallScopedSnapshot.forCall(snapshot, authoritycalls.last).loadC(candidateInput.stepIdentity) shouldBe
          a[Consequence.Failure[?]]
        take(GoalPhaseCallScopedSnapshot.forScopeC(GoalPhaseCallScopedSnapshot.forCall(snapshot, authoritycalls.last),
          work.handle, candidateInput)).loadC(candidateInput.stepIdentity) shouldBe
          a[Consequence.Failure[?]]
        snapshotCalls shouldBe empty
      } finally take(fourth.shutdownC())
    }

    "read recorded closure authority and Decision after host reopen and deny escaped or malformed reads" in {
      Given("a managed SQLite host with a test-only installer for recorded closure facts and stop-only Decision")
      val path = Files.createTempDirectory("goal-managed-closure-record-").resolve("host.sqlite")
      val authorizations = collection("hostauthorizations")
      val decisions = collection("hostdecisions")
      val handle = take(WorkflowHandle.fromRecordC(component, active))
      val candidateRevision = take(StepClosureAdmission.candidateRefC(StepClosureAdmission.StepSnapshot(
        input.stepIdentity, input.programDigest, input.planningDigest))).revision
      val snapshot = ContextSnapshot(handle.workflowRevision.value, modelRevision = Some(candidateRevision))
      val intent = GoalPhaseClosureAuthority.Intent(handle, StateMachineRevision(active.revision.value.toString),
        snapshot, GoalPhaseClosureAuthority.Input(GoalPhaseClosureAuthority.reason, input, candidateRevision),
        "recorded-closure-intent")
      val authorization = GoalPhaseClosureAuthority.Authorization(handle, intent.expectedRevision, snapshot,
        "explicit-recorded-reference", "human", Instant.parse("2026-10-01T00:00:00Z"))
      val decision = ContinuationRequest(
        StateMachineRunIdentity(handle.instanceIdentity.value),
        org.goldenport.cncf.workflow.ContinuationIdentity(s"authority-decision:${intent.idempotencyKey}"),
        intent.expectedRevision, StateMachineRequiredOperationIdentity("host.step-close-authority"),
        StateMachineOperationIdentity("HumanBoundaryFixture", "decideStepClose"),
        Some(TypedValue(GoalPhaseClosureAuthority.inputCodec.typeIdentity, intent.input)), None,
        ContextBundle("Resolve exact local commit authority",
          Vector(ContextReference(intent.input.candidate.stepIdentity, intent.input.candidateRevision)),
          Vector.empty, intent.snapshot),
        CompletionContract("human-stop-only", Vector.empty), EvidenceContract("human-evidence", Vector.empty),
        StateMachineOperationIdentity("HumanBoundaryFixture", "recordStepCloseAuthority"))
      val decisionWire = take(WorkflowDecisionJsonV1.encodeC(WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing](
        handle, WorkflowContinuation.Decision(decision, MinimalPresentation("Recorded closure stop", "No approval"))),
        GoalPhaseClosureAuthority.inputCodec))
      val command = GoalPhaseStartCommand(GoalPhaseStartInput("phase-2", "repository"),
        "closure-selection", "participant", "closure-read-start")
      val selected = ProfileStartAdmission.Selection(command.selectionIdentity,
        StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"), definition.workflowIdentity,
        definition.workflowRevision, command.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
        command.idempotencyKey, None, Some(GoalPhaseStartCommandAdapter.selectedInput(command.input)))
      var requested = intent
      var observed: Option[Consequence[Option[GoalPhaseClosureAuthority.Authorization]]] = None
      var observedDecision: Option[Consequence[ContinuationRequest[GoalPhaseClosureAuthority.Input]]] = None
      var captured: Option[ActionCall.Core] = None
      var installed = false
      def selection(reader: GoalPhaseClosureAuthorizationRecord.DataStorePort,
        decisionReader: GoalPhaseClosureDecisionRecord.DataStorePort, install: Boolean) =
        new ProfileStartAdmission.CallScopedSelectionPort {
          def loadForCallC(identity: String, capsule: ActionCall.Core) = {
            captured = Some(capsule)
            final class Installer(val core: ActionCall.Core) extends org.goldenport.cncf.action.ProcedureActionCall {
              def installC(): Consequence[Unit] = {
                given ExecutionContext = executionContext
                val space = executionContext.dataStoreSpace
                val prior = space.captureBinding()
                try for {
                  _ <- bind_component_application_datastore_c("workflow")
                  store <- space.dataStore(authorizations)
                  raw <- GoalPhaseClosureAuthorizationRecord.encodeStorageC(intent.idempotencyKey, authorization)
                  _ <- store.create(authorizations, DataStore.StringEntryId(intent.idempotencyKey),
                    org.goldenport.record.Record.data(GoalPhaseClosureAuthorizationRecord.storageField -> raw))
                  _ <- store.create(authorizations, DataStore.StringEntryId("malformed"),
                    org.goldenport.record.Record.data(GoalPhaseClosureAuthorizationRecord.storageField -> "invalid"))
                  _ <- store.create(authorizations, DataStore.StringEntryId("foreign"),
                    org.goldenport.record.Record.data(GoalPhaseClosureAuthorizationRecord.storageField -> raw))
                  decisionStore <- space.dataStore(decisions)
                  decisionRaw <- GoalPhaseClosureDecisionRecord.encodeStorageC(intent, decisionWire)
                  _ <- decisionStore.create(decisions, DataStore.StringEntryId(intent.idempotencyKey),
                    org.goldenport.record.Record.data(GoalPhaseClosureDecisionRecord.storageField -> decisionRaw))
                  _ <- decisionStore.create(decisions, DataStore.StringEntryId("malformed"),
                    org.goldenport.record.Record.data(GoalPhaseClosureDecisionRecord.storageField -> "invalid"))
                  _ <- decisionStore.create(decisions, DataStore.StringEntryId("foreign"),
                    org.goldenport.record.Record.data(GoalPhaseClosureDecisionRecord.storageField -> decisionRaw))
                  _ <- Vector("v1", "v2").foldLeft(Consequence.unit) { (prior, version) => prior.flatMap { _ =>
                    val identity = s"recorded-closure-$version"
                    val json = io.circe.parser.parse(raw.substring(raw.indexOf('{'))).toOption.get.mapObject(
                      _.add("schemaVersion", io.circe.Json.fromString(s"sm-workflow.goal-phase-closure-authorization.$version"))
                        .add("intentIdentity", io.circe.Json.fromString(identity))
                        .add("requestDigest", io.circe.Json.True)
                        .add("validUntil", io.circe.Json.fromString("unusable-old-expiry")))
                    val legacy = s"sm-workflow.goal-phase-closure-authorization-json.$version:" + json.noSpaces
                    for {
                      _ <- store.create(authorizations, DataStore.StringEntryId(identity),
                        org.goldenport.record.Record.data(GoalPhaseClosureAuthorizationRecord.storageField -> legacy))
                      legacyDecision <- GoalPhaseClosureDecisionRecord.encodeStorageC(intent.copy(idempotencyKey = identity), decisionWire)
                      _ <- decisionStore.create(decisions, DataStore.StringEntryId(identity),
                        org.goldenport.record.Record.data(GoalPhaseClosureDecisionRecord.storageField -> legacyDecision))
                    } yield ()
                  }}
                } yield ()
                finally space.restoreBinding(prior)
              }
              def execute() = Consequence.stateConflict("test-only authority installer is not an Operation")
            }
            val prepared = if (install && !installed) new Installer(capsule).installC().map { _ => installed = true }
              else Consequence.unit
            prepared.flatMap { _ =>
              val result = reader.loadForCallC(requested, capsule)
              observed = Some(result)
              val decisionResult = decisionReader.resolveForCallC(requested, capsule)
              observedDecision = Some(decisionResult)
              if (install) {
                new GoalPhaseClosureAuthorizationRecord.DataStorePort("workflow", decisions)
                  .loadForCallC(requested, capsule) shouldBe a[Consequence.Failure[?]]
                new GoalPhaseClosureDecisionRecord.DataStorePort("workflow", authorizations)
                  .resolveForCallC(requested, capsule) shouldBe a[Consequence.Failure[?]]
                new GoalPhaseClosureAuthorizationRecord.DataStorePort("other", authorizations)
                  .loadForCallC(requested, capsule) shouldBe a[Consequence.Failure[?]]
              }
              decisionResult.flatMap(_ => result.flatMap {
                case Some(_) => Consequence.success(Some(selected))
                case None => Consequence.stateConflict("test probe stops on missing authorization")
              })
            }
          }
        }
      val firstReader = new GoalPhaseClosureAuthorizationRecord.DataStorePort("workflow", authorizations)
      val firstDecisionReader = new GoalPhaseClosureDecisionRecord.DataStorePort("workflow", decisions)
      val first = host(path)
      val response = try {
        seed(first)
        installStart(first, selection(firstReader, firstDecisionReader, true))
        When("normal dispatch installs and reads the record within the managed lease")
        val started = take(first.subsystem.executeOperationResponse(startRequest(command)))
        Then("the reader returns exact facts without changing the authorized instance")
        observed shouldBe Some(Consequence.success(Some(authorization)))
        observedDecision shouldBe Some(Consequence.success(decision))
        take(storage(first).loadC(active.identity)) shouldBe Some(active)
        firstReader.loadC(intent) shouldBe a[Consequence.Failure[?]]
        firstReader.loadForCallC(intent, null) shouldBe a[Consequence.Failure[?]]
        firstDecisionReader.resolveC(intent) shouldBe a[Consequence.Failure[?]]
        firstDecisionReader.resolveForCallC(intent, null) shouldBe a[Consequence.Failure[?]]
        When("the captured call core escapes its dispatch")
        val escaped = firstReader.loadForCallC(intent, captured.get)
        Then("the released lease cannot read authority or Decision")
        escaped shouldBe a[Consequence.Failure[?]]
        firstDecisionReader.resolveForCallC(intent, captured.get) shouldBe a[Consequence.Failure[?]]
        started
      } finally take(first.shutdownC())
      observed = None
      observedDecision = None
      captured = None
      val reopened = host(path)
      try {
        installStart(reopened, selection(new GoalPhaseClosureAuthorizationRecord.DataStorePort("workflow", authorizations),
          new GoalPhaseClosureDecisionRecord.DataStorePort("workflow", decisions), false))
        When("a reconstructed host reads using only the main adapter, without the installer")
        val restored = take(reopened.subsystem.executeOperationResponse(startRequest(command)))
        Then("the persisted facts and original Start response remain exact")
        restored shouldBe response
        observed shouldBe Some(Consequence.success(Some(authorization)))
        observedDecision shouldBe Some(Consequence.success(decision))
        take(storage(reopened).loadC(active.identity)) shouldBe Some(active)
        for (version <- Vector("v1", "v2")) {
          When(s"a reopened current call resolves the explicitly selected historical $version record identity")
          requested = intent.copy(idempotencyKey = s"recorded-closure-$version")
          take(reopened.subsystem.executeOperationResponse(startRequest(command))) shouldBe response
          Then("old digest and expiry values are ignored without renewal, rewriting or invented lookup mapping")
          observed shouldBe Some(Consequence.success(Some(authorization)))
          observedDecision shouldBe Some(Consequence.success(decision))
          take(storage(reopened).loadC(active.identity)) shouldBe Some(active)
        }
        When("the lookup is absent")
        requested = intent.copy(idempotencyKey = "missing")
        reopened.subsystem.executeOperationResponse(startRequest(command)) shouldBe a[Consequence.Failure[?]]
        Then("absence remains None rather than manufactured authority")
        observed shouldBe Some(Consequence.success(None))
        observedDecision.get shouldBe a[Consequence.Failure[?]]
        for (key <- Vector("malformed", "foreign")) {
          When(s"a present authorization record is $key")
          requested = intent.copy(idempotencyKey = key)
          reopened.subsystem.executeOperationResponse(startRequest(command)) shouldBe a[Consequence.Failure[?]]
          Then("the reader rejects it rather than treating it as missing or approved")
          observed.get shouldBe a[Consequence.Failure[?]]
          observedDecision.get shouldBe a[Consequence.Failure[?]]
        }
        When("the same scoped Decision is requested with different diagnostic workspace information")
        requested = intent.copy(snapshot = snapshot.copy(workspaceRevision = Some("foreign")))
        take(reopened.subsystem.executeOperationResponse(startRequest(command))) shouldBe response
        Then("diagnostic information is not an unchanged-content certificate")
        observedDecision shouldBe Some(Consequence.success(decision))
        When("the stored Decision is requested for an actually different candidate target")
        requested = intent.copy(snapshot = snapshot.copy(modelRevision = Some("foreign-target")))
        reopened.subsystem.executeOperationResponse(startRequest(command)) shouldBe a[Consequence.Failure[?]]
        Then("Decision scope mismatch fails without replacing its recorded request")
        observedDecision.get shouldBe a[Consequence.Failure[?]]
        take(storage(reopened).loadC(active.identity)) shouldBe Some(active)
      } finally take(reopened.shutdownC())
    }

    "query committed repair judgment after former expiry and host reopen without replaying admission or effects" in {
      Given("a managed issued review and a host-bound read capability without a separate pool")
      val path = Files.createTempDirectory("goal-managed-retained-query-").resolve("host.sqlite")
      var queryEvents = 0
      def reader(current: SubsystemEventStoreBootstrap,
        operation: StateMachineOperationIdentity = StateMachineOperationIdentity("GoalPhaseService", "submitStepReview"),
        closing: StateMachineResultTypeReference = StateMachineResultTypeReference("ReviewStepResult")) =
        take(WorkflowManagedCompletion.bindC(current.subsystem, component, config, operation,
          Vector(definition), instances, continuations, GoalPhaseStepCloseJson.payloadCodec,
          GoalPhaseReviewCompletionOperation.payloadCodec, closing,
          (_, _) => { queryEvents += 1; Consequence.success(Vector.empty) }))
      val first = host(path)
      val bound = reader(first)
      val work = try {
        seed(first)
        take(bound.loadRetainedC(org.goldenport.cncf.workflow.ContinuationIdentity("absent"))) shouldBe None
        bound.loadRetainedC(null) shouldBe a[Consequence.Failure[?]]
        val wire = take(invoke(first)) match {
          case OperationResponse.Scalar(value: String) => value
          case other => fail(s"expected managed WorkOrder: $other")
        }
        take(WorkflowWorkOrderJsonV1.decodeC(wire, GoalPhaseStepCloseJson.payloadCodec))
      } finally take(first.shutdownC())
      val request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
      bound.loadRetainedC(request.continuationId) shouldBe a[Consequence.Failure[?]]
      val candidate = take(StepCloseRequestAdapter.submissionC(input)).candidate.reference
      val submitted = ContinuationResult(work.handle, request.runId, request.continuationId,
        request.expectedRevision, request.context.snapshot,
        TypedValue("ReviewStepResult", ReviewStepResult("repair", "Repair is required", Vector(
          StepReviewEvidence("retained-repair", candidate.revision, "2026-09-30T23:59:00Z",
            "full-step", "repair reviewed", "receipt")))),
        ContextReference("retained-repair", candidate.revision), Vector.empty, ExecutionEvidence(Vector.empty))
      When("normal dispatch commits the applicable judgment")
      val second = host(path)
      val completed = try {
        installReview(second)
        val read = reader(second)
        take(read.loadRetainedC(request.continuationId)) shouldBe None
        take(second.subsystem.executeOperationResponse(reviewRequest(submitted)))
        take(read.loadRetainedC(request.continuationId)).get.completion.result shouldBe submitted
        take(storage(second).loadC(active.identity)).get
      } finally take(second.shutdownC())
      When("a reconstructed host reads that committed judgment after its former validity window")
      val repairSpi = artifact.models.head.requiredSpi.find(_.actionIdentity.value == "RepairStep").get
      val repair = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(repairSpi.identity.value),
        repairSpi.actionIdentity.value, StateMachineOperationIdentity(repairSpi.operation.service.value,
          repairSpi.operation.name.value), repairSpi.operation.inputType.map(x => StateMachineInputTypeReference(x.value)),
        repairSpi.operation.resultType.map(x => StateMachineResultTypeReference(x.value)), review.metadata)
      val repairPorts = GoalPhaseManagedRepairPublication.Ports(artifact,
        () => Instant.parse("2026-10-01T02:00:00Z"), repair,
        ExecutionRequirement(Vector(CapabilityRequirement("repair-step-capability")),
          RiskLevel("standard"), ReasoningLevel.Deep, false))
      var successor: Option[WorkflowInteraction[StepCloseRequest, Nothing]] = None
      val third = host(path)
      try {
        val publisher = installReview(third, Some(repairPorts)).get
        take(GoalPhaseReviewResultAdapter.assessC(artifact, work, submitted,
          Instant.parse("2026-10-01T02:00:00Z"))).repairRequested shouldBe true
        val retained = take(reader(third).loadRetainedC(request.continuationId)).get
        Then("the retained result remains exact without fresh admission, transitions or event effects")
        retained.completion.result shouldBe submitted
        retained.completion.instanceRevision shouldBe InstanceRevision(3L)
        retained.completion.closingResult shouldBe Some(StateMachineOperationResult(
          StateMachineResultTypeReference("ReviewStepResult"), submitted.resultReference))
        take(storage(third).loadC(active.identity)) shouldBe Some(completed)
        queryEvents shouldBe 0
        When("repair Intent is recovered from that exact committed judgment and history")
        val late = Instant.parse("2026-10-01T02:00:00Z")
        val intent = take(GoalPhaseRepairCandidateBoundary.recoverIntentC(artifact, work, retained, completed, late))
        Then("historical admission time is not fabricated and the original candidate remains immutable")
        intent.admittedAt shouldBe None
        intent.observedAt shouldBe late
        intent.retainedCompletion shouldBe Some(retained)
        intent.issuedReview shouldBe work
        intent.repairJudgment shouldBe submitted
        intent.originalRequest shouldBe input
        When("the Workflow reads current repaired artifact bytes")
        val snapshot = GoalPhaseStepClosureBoundary.ArtifactSnapshot(Vector[Byte](1, 2), Vector[Byte](3, 4))
        val prepared = take(GoalPhaseRepairCandidateBoundary.prepareCurrentC(intent,
          new GoalPhaseStepClosureBoundary.SnapshotPort {
            def loadC(step: String) =
              if (step == input.stepIdentity) Consequence.success(snapshot)
              else Consequence.stateConflict("foreign repair candidate")
          }, late))
        Then("old evidence is preserved but a new full-review judgment is required")
        prepared.requiresNewReview shouldBe true
        prepared.contentChanged shouldBe true
        prepared.currentRequest.reviewEvidence shouldBe submitted.result.value.reviewEvidence
        prepared.evaluation.acceptedEvidence shouldBe empty
        prepared.evaluation.gaps.nonEmpty shouldBe true
        intent.originalRequest shouldBe input
        Then("missing proof, unrelated history or altered issued facts cannot recover repair authority")
        GoalPhaseRepairCandidateBoundary.recoverIntentC(artifact, work, null, completed, late) shouldBe
          a[Consequence.Failure[?]]
        GoalPhaseRepairCandidateBoundary.recoverIntentC(artifact, work, retained, active, late) shouldBe
          a[Consequence.Failure[?]]
        val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]]
        val changedInput = work.copy(current = order.copy(request = order.request.copy(
          input = Some(TypedValue("StepCloseRequest", input.copy(programDigest = "c" * 64))))))
        GoalPhaseRepairCandidateBoundary.recoverIntentC(artifact, changedInput, retained, completed, late) shouldBe
          a[Consequence.Failure[?]]
        val changedOperation = work.copy(current = order.copy(request = order.request.copy(
          completionOperation = StateMachineOperationIdentity("GoalPhaseService", "submitStepRepair"))))
        GoalPhaseRepairCandidateBoundary.recoverIntentC(artifact, changedOperation, retained, completed, late) shouldBe
          a[Consequence.Failure[?]]
        take(storage(third).loadC(active.identity)) shouldBe Some(completed)
        queryEvents shouldBe 0
        Then("another completion Operation or closing type cannot claim the stored judgment")
        reader(third, operation = StateMachineOperationIdentity("GoalPhaseService", "submitStepRepair"))
          .loadRetainedC(request.continuationId) shouldBe a[Consequence.Failure[?]]
        reader(third, closing = StateMachineResultTypeReference("ForeignClosingResult"))
          .loadRetainedC(request.continuationId) shouldBe a[Consequence.Failure[?]]
        third.subsystem.executeOperationResponse(reviewRequest(submitted)) shouldBe a[Consequence.Failure[?]]
        take(storage(third).loadC(active.identity)) shouldBe Some(completed)
        When("advance runs after restart without the original completion callback or Intent store")
        GoalPhaseManagedRepairPublication.bindC(ComponentCreate(third.subsystem, ComponentOrigin.Builtin),
          GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations),
          repairPorts.copy(requirement = requirement)) shouldBe a[Consequence.Failure[?]]
        publisher.advanceC(org.goldenport.cncf.workflow.ContinuationIdentity("missing-review")) shouldBe
          a[Consequence.Failure[?]]
        take(storage(third).loadC(active.identity)) shouldBe Some(completed)
        val next = take(publisher.advanceC(request.continuationId))
        successor = Some(next)
        val repairRequest = next.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
        Then("one managed successor retains the original candidate and committed result reference")
        repairRequest.completionOperation shouldBe StateMachineOperationIdentity("GoalPhaseService", "submitStepRepair")
        repairRequest.requiredOperation shouldBe StateMachineRequiredOperationIdentity("repair-step-capability")
        repairRequest.input shouldBe Some(TypedValue("StepCloseRequest", input))
        repairRequest.context.requiredFacts shouldBe Vector(submitted.resultReference)
        val suspended = take(storage(third).loadC(active.identity)).get
        suspended.revision shouldBe InstanceRevision(4L)
        suspended.history.dropRight(1) shouldBe completed.history
        suspended.suspension.get.continuationIdentity.value shouldBe repairRequest.continuationId.value
        When("advance repeats for the same completed review")
        take(publisher.advanceC(request.continuationId)) shouldBe next
        Then("no duplicate successor or extra history is created")
        take(storage(third).loadC(active.identity)) shouldBe Some(suspended)
        queryEvents shouldBe 0
      } finally take(third.shutdownC())
      When("another host reconstructs managed repair publication")
      val repairOrder = successor.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
      val repaired = ContinuationResult(successor.get.handle, repairOrder.runId, repairOrder.continuationId,
        repairOrder.expectedRevision, repairOrder.context.snapshot,
        TypedValue("RepairStepResult", RepairStepResult("completed", "Repair applied", "repair-receipt")),
        ContextReference("repair-receipt", candidate.revision), Vector.empty, ExecutionEvidence(Vector.empty))
      val fourth = host(path)
      try {
        val publisher = installReview(fourth, Some(repairPorts)).get
        Then("advance returns the same durable successor, not new work")
        take(publisher.advanceC(request.continuationId)) shouldBe successor.get
        take(storage(fourth).loadC(active.identity)).get.revision shouldBe InstanceRevision(4L)
        When("a malformed repair result is submitted through normal Subsystem dispatch")
        fourth.subsystem.executeOperationResponse(repairRequest(repaired.copy(result =
          TypedValue("RepairStepResult", repaired.result.value.copy(outcome = "failed"))))) shouldBe
          a[Consequence.Failure[?]]
        fourth.subsystem.executeOperationResponse(repairRequest(repaired.copy(result =
          TypedValue("RepairStepResult", repaired.result.value.copy(evidenceIdentity = "foreign"))))) shouldBe
          a[Consequence.Failure[?]]
        Then("the repair suspension survives and no history entry is appended")
        take(storage(fourth).loadC(active.identity)).get.revision shouldBe InstanceRevision(4L)
        When("the exact issued ordinary repair completes")
        val response = take(fourth.subsystem.executeOperationResponse(repairRequest(repaired)))
        Then("managed completion clears only its suspension and waits for a fresh full review")
        val waitWire = response match {
          case OperationResponse.Scalar(value: String) => value
          case other => fail(s"expected managed repair Wait JSON: $other")
        }
        val waiting = take(WorkflowWaitJsonV1.decodeC(waitWire))
        waiting.handle shouldBe repaired.handle
        waiting.current shouldBe WorkflowContinuation.Wait(StateMachineRevision("5"), repaired.contextSnapshot,
          MinimalPresentation("Repair completed", "Awaiting fresh full-Step review"))
        val completedRepair = take(storage(fourth).loadC(active.identity)).get
        completedRepair.revision shouldBe InstanceRevision(5L)
        completedRepair.suspension shouldBe None
        completedRepair.currentProgression shouldBe Some(ProgressionReference("step.Awaiting"))
        completedRepair.causation shouldBe CausationReference(s"repair-result:repair-receipt@${candidate.revision}")
      } finally take(fourth.shutdownC())
      When("a fifth host reopens after repair completion without a callback or Intent map")
      var reReview: Option[WorkflowInteraction[StepCloseRequest, Nothing]] = None
      val unavailable = new GoalPhaseStepClosureBoundary.SnapshotPort {
        def loadC(step: String) = Consequence.stateConflict("artifact bytes unavailable")
      }
      val fifth = host(path)
      try {
        val publisher = installReview(fifth, Some(repairPorts)).get
        val repairReader = take(WorkflowManagedCompletion.bindC(fifth.subsystem, component, config,
          StateMachineOperationIdentity("GoalPhaseService", "submitStepRepair"), Vector(definition),
          instances, continuations, GoalPhaseStepCloseJson.payloadCodec,
          GoalPhaseRepairCompletionOperation.payloadCodec, StateMachineResultTypeReference("RepairStepResult"),
          (_, _) => Consequence.success(Vector.empty)))
        val proof = take(repairReader.loadRetainedC(repairOrder.continuationId)).get
        Then("the exact repair result and closing result remain retained and replay is refused")
        proof.completion.result shouldBe repaired
        proof.completion.instanceRevision shouldBe InstanceRevision(5L)
        proof.completion.closingResult shouldBe Some(StateMachineOperationResult(
          StateMachineResultTypeReference("RepairStepResult"), repaired.resultReference))
        fifth.subsystem.executeOperationResponse(repairRequest(repaired)) shouldBe a[Consequence.Failure[?]]
        take(storage(fifth).loadC(active.identity)).get.revision shouldBe InstanceRevision(5L)
        When("the Workflow recovers the re-review predecessor from retained repair completion")
        val current = take(storage(fifth).loadC(active.identity)).get
        val recovered = take(publisher.recoverCompletedRepairC(proof, current))
        Then("original review facts survive without fabricating admission time or replaying completion")
        recovered.originalRequest shouldBe input
        recovered.repairJudgment shouldBe submitted
        recovered.admittedAt shouldBe None
        recovered.retainedCompletion.get.completion.result shouldBe submitted
        When("the Workflow prepares current artifact bytes for the next full review")
        val prepared = take(GoalPhaseRepairCandidateBoundary.prepareCurrentC(recovered,
          new GoalPhaseStepClosureBoundary.SnapshotPort {
            def loadC(step: String) =
              if (step == input.stepIdentity) Consequence.success(
                GoalPhaseStepClosureBoundary.ArtifactSnapshot(Vector[Byte](7, 8), Vector[Byte](9)))
              else Consequence.stateConflict("foreign repaired Step")
          }, Instant.parse("2026-10-01T02:00:00Z")))
        Then("wrong-target evidence is preserved but not used to omit the required new full review")
        prepared.requiresNewReview shouldBe true
        prepared.contentChanged shouldBe true
        prepared.currentRequest.reviewEvidence shouldBe submitted.result.value.reviewEvidence
        prepared.evaluation.acceptedEvidence shouldBe empty
        prepared.evaluation.gaps.nonEmpty shouldBe true
        When("re-review recovery is supplied missing proof or unrelated history")
        publisher.recoverCompletedRepairC(null, current) shouldBe a[Consequence.Failure[?]]
        publisher.recoverCompletedRepairC(proof, completed) shouldBe a[Consequence.Failure[?]]
        Then("the committed repair remains unchanged and no successor is published by preparation")
        take(storage(fifth).loadC(active.identity)) shouldBe Some(current)
        When("the first re-review publication cannot read authoritative bytes")
        publisher.advanceReReviewC(repairOrder.continuationId, review, requirement, presentation,
          unavailable) shouldBe a[Consequence.Failure[?]]
        Then("the completed repair remains available for recovery without a partial successor")
        take(storage(fifth).loadC(active.identity)) shouldBe Some(current)
        When("advance reads repaired bytes and issues a new full-review WorkOrder")
        var reads = 0
        val next = take(publisher.advanceReReviewC(repairOrder.continuationId, review, requirement, presentation,
          new GoalPhaseStepClosureBoundary.SnapshotPort {
            def loadC(step: String) = {
              reads += 1
              Consequence.success(GoalPhaseStepClosureBoundary.ArtifactSnapshot(Vector[Byte](7, 8), Vector[Byte](9)))
            }
          }))
        reReview = Some(next)
        Then("one atomic successor retains the changed candidate and old evidence, not acceptance")
        val nextRequest = next.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
        nextRequest.input shouldBe Some(TypedValue("StepCloseRequest", prepared.currentRequest))
        nextRequest.context.requiredFacts shouldBe Vector(repaired.resultReference)
        nextRequest.completionOperation shouldBe StateMachineOperationIdentity("GoalPhaseService", "submitStepReview")
        reads shouldBe 1
        val suspended = take(storage(fifth).loadC(active.identity)).get
        suspended.revision shouldBe InstanceRevision(6L)
        suspended.history.dropRight(1) shouldBe current.history
        suspended.suspension.get.continuationIdentity.value shouldBe nextRequest.continuationId.value
        When("advance is retried with unavailable artifact bytes")
        take(publisher.advanceReReviewC(repairOrder.continuationId, review, requirement, presentation,
          unavailable)) shouldBe next
        Then("the already issued candidate and history remain exact without rereading artifacts")
        take(storage(fifth).loadC(active.identity)) shouldBe Some(suspended)
      } finally take(fifth.shutdownC())
      When("a sixth host reopens with no artifact bytes available")
      val sixth = host(path)
      try {
        val publisher = installReview(sixth, Some(repairPorts)).get
        Then("advance returns the retained re-review without constructing a different candidate")
        take(publisher.advanceReReviewC(repairOrder.continuationId, review, requirement, presentation,
          unavailable)) shouldBe reReview.get
        take(storage(sixth).loadC(active.identity)).get.revision shouldBe InstanceRevision(6L)
        When("fresh full-review evidence is returned for the issued repaired candidate")
        val next = reReview.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
        val nextCandidate = take(StepCloseRequestAdapter.submissionC(next.input.get.value)).candidate.reference
        val accepted = ContinuationResult(reReview.get.handle, next.runId, next.continuationId, next.expectedRevision,
          next.context.snapshot, TypedValue("ReviewStepResult", ReviewStepResult("accept", "Repaired Step reviewed",
            Vector(StepReviewEvidence("new-full-review", nextCandidate.revision, "2026-10-01T01:59:00Z",
              "full-step", "new candidate covered", "receipt")))),
          ContextReference("new-full-review", nextCandidate.revision), Vector.empty, ExecutionEvidence(Vector.empty))
        take(sixth.subsystem.executeOperationResponse(reviewRequest(accepted)))
        Then("normal managed review completion records the new judgment without closing or committing the Step")
        val reviewed = take(storage(sixth).loadC(active.identity)).get
        reviewed.revision shouldBe InstanceRevision(7L)
        reviewed.suspension shouldBe None
        reviewed.currentProgression shouldBe Some(ProgressionReference("step.Awaiting"))
        reviewed.causation shouldBe CausationReference(s"review-result:new-full-review@${nextCandidate.revision}")
        val read = take(reader(sixth).loadRetainedC(next.continuationId)).get
        read.completion.result shouldBe accepted
        sixth.subsystem.executeOperationResponse(reviewRequest(accepted)) shouldBe a[Consequence.Failure[?]]
        take(storage(sixth).loadC(active.identity)) shouldBe Some(reviewed)
      } finally take(sixth.shutdownC())
    }

    "issue review on the public Start instance and complete it after reopening the same Service" in {
      Given("a four-Operation managed Service assembled by the post-resolution Factory hook with explicit host authority ports")
      val path = Files.createTempDirectory("goal-managed-start-review-").resolve("host.sqlite")
      val command = GoalPhaseStartCommand(GoalPhaseStartInput("phase-2", "repository"),
        "start-review-selection", "participant", "start-review-key")
      val authorized = ProfileStartAdmission.Selection(command.selectionIdentity,
        StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"), definition.workflowIdentity,
        definition.workflowRevision, command.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
        command.idempotencyKey, None, Some(GoalPhaseStartCommandAdapter.selectedInput(command.input)))
      val selection = new ProfileStartAdmission.SelectionPort {
        def loadC(identity: String) = Consequence.success(Option.when(identity == authorized.selectionId)(authorized))
      }
      var associated: Option[WorkflowHandle] = None
      var unexpectedCalls = 0
      val invocation = new GoalPhaseStepCloseServiceOperation.InvocationPort {
        def resolveC(identity: String) = associated match {
          case Some(handle) if identity == input.submissionIdentity => Consequence.success(handle)
          case _ => Consequence.stateConflict("host has no associated Start")
        }
      }
      val closeRequest = Request.of(component = component.name, service = "GoalPhaseService", operation = "requestStepClose",
        arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(input))))
      def wire(response: OperationResponse): String = response match {
        case OperationResponse.Scalar(value: String) => value
        case other => fail(s"expected Workflow JSON: $other")
      }
      val first = host(path)
      val work = try {
        When("the declared Service is composed and Start associates the closure submission")
        installPath(first, selection, invocation, () => unexpectedCalls += 1)
        first.subsystem.executeOperationResponse(closeRequest) shouldBe a[Consequence.Failure[?]]
        associated = Some(take(WorkflowWaitJsonV1.decodeC(wire(take(
          first.subsystem.executeOperationResponse(startRequest(command)))))).handle)
        val value = take(WorkflowWorkOrderJsonV1.decodeC(wire(take(
          first.subsystem.executeOperationResponse(closeRequest))), GoalPhaseStepCloseJson.payloadCodec))
        Then("the review is issued only on the selected Start instance")
        value.handle.instanceIdentity shouldBe associated.get.instanceIdentity
        val saved = take(storage(first).loadC(value.handle.instanceIdentity)).get
        saved.history.size shouldBe 2
        saved.correlation shouldBe CorrelationReference(command.selectionIdentity)
        saved.suspension.nonEmpty shouldBe true
        first.subsystem.executeOperationResponse(closeRequest) shouldBe a[Consequence.Failure[?]]
        value
      } finally take(first.shutdownC())
      val request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
      val candidate = take(StepCloseRequestAdapter.submissionC(input)).candidate.reference
      val submitted = ContinuationResult(work.handle, request.runId, request.continuationId,
        request.expectedRevision, request.context.snapshot,
        TypedValue("ReviewStepResult", ReviewStepResult("accept", "review passed", Vector(
          StepReviewEvidence("start-review-result", candidate.revision, "2026-09-30T23:59:00Z",
            "full-step", "reviewed", "receipt")))),
        ContextReference("start-review-result", candidate.revision), Vector.empty, ExecutionEvidence(Vector.empty))
      val reopened = host(path)
      try {
        When("the same four-Operation Service reopens and receives the exact review result")
        installPath(reopened, selection, invocation, () => unexpectedCalls += 1)
        take(issued(reopened).loadC(request.continuationId)) shouldBe Some(work)
        take(WorkflowWaitJsonV1.decodeC(wire(take(reopened.subsystem.executeOperationResponse(reviewRequest(submitted))))))
          .current shouldBe a[WorkflowContinuation.Wait]
        val saved = take(storage(reopened).loadC(associated.get.instanceIdentity)).get
        Then("managed completion preserves the Start correlation and rejects replay without invoking closure effects")
        saved.history.size shouldBe 3
        saved.revision shouldBe InstanceRevision(3L)
        saved.suspension shouldBe None
        saved.correlation shouldBe CorrelationReference(command.selectionIdentity)
        reopened.subsystem.executeOperationResponse(reviewRequest(submitted)) shouldBe a[Consequence.Failure[?]]
        take(storage(reopened).loadC(saved.identity)) shouldBe Some(saved)
        unexpectedCalls shouldBe 0
      } finally take(reopened.shutdownC())
    }

    (for (interrupt <- Vector(false, true); projection <- Vector(false, true)) yield (interrupt, projection))
      .foreach { case (interruptFollowup, projectAuthority) =>
    s"return managed repair and fresh review work across reopen with interrupted follow-up = $interruptFollowup, authority projection = $projectAuthority" in {
      Given("a Start-selected four-Operation Service assembled by the post-resolution Factory hook and a host snapshot of repaired artifacts")
      val path = Files.createTempDirectory("goal-managed-auto-repair-").resolve("host.sqlite")
      val command = GoalPhaseStartCommand(GoalPhaseStartInput("phase-2", "repository"),
        "auto-repair-selection", "participant", "auto-repair-key")
      val authorized = ProfileStartAdmission.Selection(command.selectionIdentity,
        StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"), definition.workflowIdentity,
        definition.workflowRevision, command.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
        command.idempotencyKey, None, Some(GoalPhaseStartCommandAdapter.selectedInput(command.input)))
      val selection = new ProfileStartAdmission.SelectionPort {
        def loadC(identity: String) = Consequence.success(Option.when(identity == authorized.selectionId)(authorized))
      }
      var associated: Option[WorkflowHandle] = None
      var unexpectedCalls = 0
      var reads = 0
      var snapshotAvailable = true
      val invocation = new GoalPhaseStepCloseServiceOperation.InvocationPort {
        def resolveC(identity: String) = associated match {
          case Some(handle) if identity == input.submissionIdentity => Consequence.success(handle)
          case _ => Consequence.stateConflict("host has no associated Start")
        }
      }
      var snapshotCalls = Vector.empty[ActionCall.Core]
      var repairSnapshotScopes = Vector.empty[GoalPhaseCallScopedSnapshot.Scope]
      def fixtureSnapshotC(): Consequence[GoalPhaseStepClosureBoundary.ArtifactSnapshot] = {
          reads += 1
          if (snapshotAvailable)
            Consequence.success(GoalPhaseStepClosureBoundary.ArtifactSnapshot(Vector[Byte](7, 8), Vector[Byte](9)))
          else Consequence.stateConflict("authoritative repaired artifacts unavailable")
      }
      val snapshot = new GoalPhaseCallScopedSnapshot.ScopedPort {
        def loadForScopeCallC(scope: GoalPhaseCallScopedSnapshot.Scope, core: ActionCall.Core) = _snapshot_lease_c(core).flatMap { _ =>
          snapshotCalls = snapshotCalls :+ core
          repairSnapshotScopes = repairSnapshotScopes :+ scope
          fixtureSnapshotC()
        }
      }
      var authorityReads = 0
      val authorityReader = new GoalPhaseClosureAuthorizationRecord.DataStorePort("workflow", collection("hostauthorizations"))
      val closureAuthority = new GoalPhaseClosureAuthority.CallScopedAuthorizationPort {
        def loadForCallC(intent: GoalPhaseClosureAuthority.Intent, core: ActionCall.Core) = {
          authorityReads += 1
          authorityReader.loadForCallC(intent, core)
        }
      }
      val decisions = new GoalPhaseClosureAuthority.DecisionPort {
        def resolveC(intent: GoalPhaseClosureAuthority.Intent) = Consequence.success(ContinuationRequest(
          StateMachineRunIdentity(intent.handle.instanceIdentity.value),
          org.goldenport.cncf.workflow.ContinuationIdentity(s"authority-decision:${intent.idempotencyKey}"),
          intent.expectedRevision, StateMachineRequiredOperationIdentity("host.step-close-authority"),
          StateMachineOperationIdentity("HumanBoundaryFixture", "decideStepClose"),
          Some(TypedValue(GoalPhaseClosureAuthority.inputCodec.typeIdentity, intent.input)), None,
          ContextBundle("Resolve exact local commit authority",
            Vector(ContextReference(intent.input.candidate.stepIdentity, intent.input.candidateRevision)),
            Vector.empty, intent.snapshot),
          CompletionContract("human-stop-only", Vector.empty), EvidenceContract("human-evidence", Vector.empty),
          StateMachineOperationIdentity("HumanBoundaryFixture", "recordStepCloseAuthority")))
      }
      val projection = Option.when(projectAuthority)(GoalPhaseManagedReviewAssembly.AuthorityProjection(
        snapshot, GoalPhaseClosureAuthority.Ports(closureAuthority, decisions)))
      def wire(response: OperationResponse): String = response match {
        case OperationResponse.Scalar(value: String) => value
        case other => fail(s"expected Workflow JSON: $other")
      }
      def reopen[A](action: SubsystemEventStoreBootstrap => A): A = {
        val current = host(path)
        try {
          installPath(current, selection, invocation, () => unexpectedCalls += 1, Some(snapshot), projection)
          action(current)
        } finally take(current.shutdownC())
      }
      When("Start and requestStepClose issue the original review")
      val original = reopen { current =>
        associated = Some(take(WorkflowWaitJsonV1.decodeC(wire(take(
          current.subsystem.executeOperationResponse(startRequest(command)))))).handle)
        val close = Request.of(component = component.name, service = "GoalPhaseService", operation = "requestStepClose",
          arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(input))))
        take(WorkflowWorkOrderJsonV1.decodeC(wire(take(current.subsystem.executeOperationResponse(close))),
          GoalPhaseStepCloseJson.payloadCodec))
      }
      Then("review is suspended on the selected instance without reading repair artifacts")
      original.handle.instanceIdentity shouldBe associated.get.instanceIdentity
      reads shouldBe 0
      def judgment(work: WorkflowInteraction[StepCloseRequest, Nothing], decision: String, evidence: String) = {
        val request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
        val candidate = take(StepCloseRequestAdapter.submissionC(request.input.get.value)).candidate.reference
        ContinuationResult(work.handle, request.runId, request.continuationId, request.expectedRevision,
          request.context.snapshot, TypedValue("ReviewStepResult", ReviewStepResult(decision, "review judgment",
            Vector(StepReviewEvidence(evidence, candidate.revision, "2026-09-30T23:59:00Z",
              "full-step", "candidate covered", "receipt")))),
          ContextReference(evidence, candidate.revision), Vector.empty, ExecutionEvidence(Vector.empty))
      }
      When("submitStepReview receives a repair judgment after host reopen")
      val repair = reopen { current =>
        val result = take(WorkflowWorkOrderJsonV1.decodeC(wire(take(current.subsystem.executeOperationResponse(
          reviewRequest(judgment(original, "repair", "auto-review-repair"))))), GoalPhaseStepCloseJson.payloadCodec))
        Then("Workflow atomically issues repair without a skill selecting a route")
        take(storage(current).loadC(associated.get.instanceIdentity)).get.revision shouldBe InstanceRevision(4L)
        result
      }
      val repairOrder = repair.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
      repairOrder.completionOperation shouldBe StateMachineOperationIdentity("GoalPhaseService", "submitStepRepair")
      val repaired = ContinuationResult(repair.handle, repairOrder.runId, repairOrder.continuationId,
        repairOrder.expectedRevision, repairOrder.context.snapshot,
        TypedValue("RepairStepResult", RepairStepResult("completed", "repair applied", "auto-repair-receipt")),
        ContextReference("auto-repair-receipt",
          take(StepCloseRequestAdapter.submissionC(repairOrder.input.get.value)).candidate.reference.revision),
        Vector.empty, ExecutionEvidence(Vector.empty))
      if (interruptFollowup) {
        snapshotAvailable = false
        When("artifact lookup fails after submitStepRepair commits its ordinary action result")
        reopen { current =>
          current.subsystem.executeOperationResponse(repairRequest(repaired)) shouldBe a[Consequence.Failure[?]]
          Then("the exact completed repair remains retained without a partial re-review successor")
          val saved = take(storage(current).loadC(associated.get.instanceIdentity)).get
          saved.revision shouldBe InstanceRevision(5L)
          saved.suspension shouldBe None
          saved.causation shouldBe CausationReference(
            s"repair-result:${repaired.resultReference.identity}@${repaired.resultReference.revision}")
          take(issued(current).loadC(org.goldenport.cncf.workflow.ContinuationIdentity(
            s"${saved.identity.value}:6:${input.submissionIdentity}:review"))) shouldBe None
          current.subsystem.executeOperationResponse(repairRequest(repaired)) shouldBe a[Consequence.Failure[?]]
          take(storage(current).loadC(saved.identity)) shouldBe Some(saved)
          reads shouldBe 1
        }
        snapshotAvailable = true
      }
      When("another host issues re-review from normal completion or its retained failure gap")
      val fresh = reopen { current =>
        val result = if (!interruptFollowup)
          take(WorkflowWorkOrderJsonV1.decodeC(wire(take(current.subsystem.executeOperationResponse(
            repairRequest(repaired)))), GoalPhaseStepCloseJson.payloadCodec))
        else {
          val spi = artifact.models.head.requiredSpi.find(_.actionIdentity.value == "RepairStep").get
          val repairSpi = StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(spi.identity.value),
            spi.actionIdentity.value, StateMachineOperationIdentity(spi.operation.service.value, spi.operation.name.value),
            spi.operation.inputType.map(x => StateMachineInputTypeReference(x.value)),
            spi.operation.resultType.map(x => StateMachineResultTypeReference(x.value)), review.metadata)
          val publisher = take(GoalPhaseManagedRepairPublication.bindC(
            ComponentCreate(current.subsystem, ComponentOrigin.Builtin),
            GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations),
            GoalPhaseManagedRepairPublication.Ports(artifact, () => Instant.parse("2026-10-01T00:00:00Z"),
              repairSpi, ExecutionRequirement(Vector(CapabilityRequirement("repair-step-capability")),
                RiskLevel("standard"), ReasoningLevel.Deep, false))))
          // This pre-existing direct private recovery probe is outside dispatch.
          // Its explicit fixture bytes are not a production managed reader.
          val recoverySnapshot = new GoalPhaseStepClosureBoundary.SnapshotPort {
            def loadC(step: String) = fixtureSnapshotC()
          }
          val recovered = take(publisher.advanceReReviewC(repairOrder.continuationId, review, requirement,
            presentation, recoverySnapshot))
          snapshotAvailable = false
          take(publisher.advanceReReviewC(repairOrder.continuationId, review, requirement,
            presentation, recoverySnapshot)) shouldBe recovered
          recovered
        }
        Then("Workflow reads actual repaired bytes and atomically issues fresh full review")
        repairSnapshotScopes.head.handle.instanceIdentity shouldBe associated.get.instanceIdentity
        repairSnapshotScopes.head.request shouldBe repairOrder.input.get.value
        repairSnapshotScopes.head.purpose shouldBe GoalPhaseCallScopedSnapshot.Purpose.RepairSuccessor
        take(storage(current).loadC(associated.get.instanceIdentity)).get.revision shouldBe InstanceRevision(6L)
        reads shouldBe (if (interruptFollowup) 2 else 1)
        result
      }
      val freshOrder = fresh.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
      freshOrder.completionOperation shouldBe StateMachineOperationIdentity("GoalPhaseService", "submitStepReview")
      freshOrder.input.get.value should not be repairOrder.input.get.value
      freshOrder.context.requiredFacts shouldBe Vector(repaired.resultReference)
      When("the fresh candidate is accepted through submitStepReview after reopening")
      snapshotAvailable = true
      reopen { current =>
        val accepted = judgment(fresh, "accept", "auto-fresh-review")
        val response = wire(take(current.subsystem.executeOperationResponse(reviewRequest(accepted))))
        Then("the new judgment is retained once and configured authority absence yields Decision without closure")
        if (projectAuthority) {
          take(WorkflowDecisionJsonV1.decodeC(response, GoalPhaseClosureAuthority.inputCodec)).handle shouldBe associated.get
          authorityReads shouldBe 1
        } else take(WorkflowWaitJsonV1.decodeC(response)).current shouldBe a[WorkflowContinuation.Wait]
        val saved = take(storage(current).loadC(associated.get.instanceIdentity)).get
        saved.revision shouldBe InstanceRevision(7L)
        saved.suspension shouldBe None
        current.subsystem.executeOperationResponse(reviewRequest(accepted)) shouldBe a[Consequence.Failure[?]]
        take(storage(current).loadC(saved.identity)) shouldBe Some(saved)
        unexpectedCalls shouldBe 0
        reads shouldBe (if (interruptFollowup) 2 else 1)
        authorityReads shouldBe (if (projectAuthority) 1 else 0)
        When("the snapshot is invoked outside dispatch or through a captured completed call")
        val observedReads = reads
        snapshot.loadC(input.stepIdentity) shouldBe a[Consequence.Failure[?]]
        snapshotCalls.nonEmpty shouldBe true
        snapshotCalls.zip(repairSnapshotScopes).foreach { (call, scope) =>
          call.component.get.componentId shouldBe component
          GoalPhaseCallScopedSnapshot.forCall(snapshot, call).loadC(input.stepIdentity) shouldBe
            a[Consequence.Failure[?]]
          take(GoalPhaseCallScopedSnapshot.forScopeC(GoalPhaseCallScopedSnapshot.forCall(snapshot, call),
            scope.handle, scope.request, scope.purpose)).loadC(scope.request.stepIdentity) shouldBe
            a[Consequence.Failure[?]]
        }
        Then("all views refuse access after their own managed lease without another artifact read")
        reads shouldBe observedReads
      }
    }
    }

    "load host selection through the current managed Operation and reopen without a conversation store" in {
      Given("a managed host and a test-only trusted installer for an immutable selection record")
      val path = Files.createTempDirectory("goal-managed-selection-record-").resolve("host.sqlite")
      val selections = collection("hostselections")
      val reader = new ProfileSelectionDataStorePort("workflow", selections)
      val command = GoalPhaseStartCommand(GoalPhaseStartInput("phase-2", "repository"),
        "selection-durable", "participant", "durable-start-key")
      val selected = ProfileStartAdmission.Selection(command.selectionIdentity,
        StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"), definition.workflowIdentity,
        definition.workflowRevision, command.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
        command.idempotencyKey, None, Some(GoalPhaseStartCommandAdapter.selectedInput(command.input)))
      reader.loadC(command.selectionIdentity) shouldBe a[Consequence.Failure[?]]
      reader.loadForCallC(command.selectionIdentity, null) shouldBe a[Consequence.Failure[?]]
      var installed = false
      var capturedCall: Option[ActionCall.Core] = None
      val installThenRead = new ProfileStartAdmission.CallScopedSelectionPort {
        def loadForCallC(identity: String, capsule: ActionCall.Core) = {
          capturedCall = Some(capsule)
          final class Installer extends org.goldenport.cncf.action.ProcedureActionCall {
            val core = capsule
            def installC(): Consequence[Unit] = {
              given ExecutionContext = executionContext
              val space = executionContext.dataStoreSpace
              val prior = space.captureBinding()
              try for {
                _ <- bind_component_application_datastore_c("workflow")
                store <- space.dataStore(selections)
                wire <- ProfileSelectionJsonV1.encodeStorageC(selected)
                _ <- store.create(selections, DataStore.StringEntryId(identity),
                  org.goldenport.record.Record.data(ProfileSelectionJsonV1.storageField -> wire))
              } yield ()
              finally space.restoreBinding(prior)
            }
            def execute() = Consequence.stateConflict("test installer has no public Operation")
          }
          val installer = new Installer
          if (installed) reader.loadForCallC(identity, capsule)
          else installer.installC().flatMap { _ =>
            installed = true
            reader.loadForCallC(identity, capsule)
          }
        }
      }
      val first = host(path)
      val response = try {
        installStart(first, installThenRead)
        When("normal Subsystem dispatch runs Start inside its managed lease")
        val started = take(first.subsystem.executeOperationResponse(startRequest(command)))
        installed shouldBe true
        Then("the same recorded human choice cannot start another Phase or repository")
        first.subsystem.executeOperationResponse(startRequest(command.copy(
          input = command.input.copy(phaseIdentity = "foreign")))) shouldBe a[Consequence.Failure[?]]
        first.subsystem.executeOperationResponse(startRequest(command.copy(
          input = command.input.copy(repositoryIdentity = "foreign")))) shouldBe a[Consequence.Failure[?]]
        first.subsystem.executeOperationResponse(startRequest(command)) shouldBe Consequence.success(started)
        Then("an escaped call capsule cannot read records after the managed dispatch lease ends")
        reader.loadForCallC(command.selectionIdentity, capturedCall.get) shouldBe a[Consequence.Failure[?]]
        started
      } finally take(first.shutdownC())
      val reopened = host(path)
      val durableSelection = new ProfileSelectionDataStorePort("workflow", selections)
      val retainedDeclaration = GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations)
      var retainedStart: Option[GoalPhaseStartExecution.RetainedStart] = None
      var reopenedCall: Option[ActionCall.Core] = None
      try {
        // A private probe inside the existing dispatch; no new public Operation.
        installStart(reopened, new ProfileStartAdmission.CallScopedSelectionPort {
          def loadForCallC(identity: String, core: ActionCall.Core) = {
            reopenedCall = Some(core)
            GoalPhaseManagedStartAssembly.loadRetainedForCallC(command, core,
              retainedDeclaration.copy(configuration = config.copy(workflowStore = null)),
              durableSelection) shouldBe a[Consequence.Failure[?]]
            GoalPhaseManagedStartAssembly.loadRetainedForCallC(command, core,
              retainedDeclaration.copy(instances = collection("foreigninstances")),
              durableSelection) shouldBe a[Consequence.Failure[?]]
            GoalPhaseManagedStartAssembly.loadRetainedForCallC(command, core,
              retainedDeclaration, durableSelection).flatMap { retained =>
              retainedStart = Some(retained)
              durableSelection.loadForCallC(identity, core)
            }
          }
        })
        When("a new host with only the read adapter reopens the same physical database")
        take(reopened.subsystem.executeOperationResponse(startRequest(command))) shouldBe response
        Then("the persisted selection authorizes identical Start without an installer or in-memory selection map")
        val original = response match {
          case OperationResponse.Scalar(wire: String) => take(WorkflowWaitJsonV1.decodeC(wire))
          case other => fail(s"Expected original Start WAIT: $other")
        }
        retainedStart.get.interaction shouldBe original
        retainedStart.get.observedRevision shouldBe InstanceRevision(1L)
        GoalPhaseManagedStartAssembly.loadRetainedForCallC(command, reopenedCall.get,
          retainedDeclaration, durableSelection) shouldBe a[Consequence.Failure[?]]
        reopened.subsystem.executeOperationResponse(startRequest(command.copy(
          input = command.input.copy(phaseIdentity = "foreign")))) shouldBe a[Consequence.Failure[?]]
        reopened.subsystem.executeOperationResponse(startRequest(command.copy(
          input = command.input.copy(repositoryIdentity = "foreign")))) shouldBe a[Consequence.Failure[?]]
        reopened.subsystem.executeOperationResponse(startRequest(command.copy(selectionIdentity = "missing"))) shouldBe
          a[Consequence.Failure[?]]
        When("the same stored Workflow advances before a later registration read")
        val persistence = storage(reopened)
        val before = take(persistence.loadC(original.handle.instanceIdentity)).get
        val advanced = take(persistence.appendC(before.identity, before.revision, HistoryEntry(
          HistorySequence(2L), InstanceRevision(2L), Lifecycle.Active,
          Some(ProgressionReference("step.Awaiting")), before.correlation,
          CausationReference("later-retained-work"), None, None, None)))
        retainedStart = None
        // Start itself must not reset progression; its read probe still recovers origin.
        reopened.subsystem.executeOperationResponse(startRequest(command)) shouldBe a[Consequence.Failure[?]]
        Then("registration can read the original Start without treating it as current state")
        retainedStart.get.interaction shouldBe original
        retainedStart.get.observedRevision shouldBe InstanceRevision(2L)
        take(persistence.loadC(before.identity)).get shouldBe advanced
        GoalPhaseManagedStartAssembly.loadRetainedForCallC(command, null,
          retainedDeclaration, durableSelection) shouldBe a[Consequence.Failure[?]]
      } finally take(reopened.shutdownC())
      val undeclared = host(path, Map.empty)
      try {
        installStart(undeclared, new ProfileSelectionDataStorePort("workflow", selections))
        When("the same database is reopened by a host with no record-read declaration")
        undeclared.subsystem.executeOperationResponse(startRequest(command)) shouldBe a[Consequence.Failure[?]]
        Then("existing bytes do not grant collection access or change the Start instance")
        val wire = response match {
          case OperationResponse.Scalar(value: String) => value
          case other => fail(s"Expected recorded Start WAIT: $other")
        }
        val handle = take(WorkflowWaitJsonV1.decodeC(wire)).handle
        take(storage(undeclared).loadC(InstanceIdentity(handle.instanceIdentity.value))).get.revision shouldBe InstanceRevision(2L)
      } finally take(undeclared.shutdownC())
    }

    for (automatic <- Vector(false, true)) {
    s"dispatch the CML registration Operation with stored receipt retry and reopen, automatic question = $automatic" in {
      import org.goldenport.cncf.blob.*
      import org.goldenport.cncf.component.builtin.BuiltinComponentIdentity
      import org.goldenport.cncf.component.builtin.blob.BlobComponent
      import org.goldenport.bag.Bag
      import org.goldenport.datatype.ContentType
      import org.simplemodeling.model.datatype.EntityId
      Given("a test-installed persisted selection and Start, with an explicit host Create grant")
      val path = Files.createTempDirectory("goal-public-registration-").resolve("host.sqlite")
      val registrations = collection("hostsubmissions")
      val artifacts = collection("artifactreferences")
      val cliconfiguration = hostRecordConfiguration.updated("textus.subsystem.user-mode",
        ConfigurationValue.StringValue("standalone")) ++ Map(
        GoalPhaseArtifactReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
        GoalPhaseArtifactReadPolicy.prefix + "reference-collection" -> ConfigurationValue.StringValue(artifacts.print),
        GoalPhaseHumanQuestionStoragePolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
        GoalPhaseHumanQuestionStoragePolicy.prefix + "question-collection" -> ConfigurationValue.StringValue(collection("humanquestions").print),
        GoalPhaseHumanAnswerStoragePolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
        GoalPhaseHumanAnswerStoragePolicy.prefix + "answer-collection" -> ConfigurationValue.StringValue(collection("humananswers").print))
      val grant = take(org.goldenport.cncf.datastore.ComponentRecordStorageDeclaration.createC(
        Vector(registrations, artifacts).map(value =>
          org.goldenport.cncf.datastore.ComponentRecordStorageDeclaration.CreateGrant(component, "workflow", value))))
      val decisionGrant = take(org.goldenport.cncf.datastore.ComponentRecordStorageDeclaration.createC(
        Vector(registrations, artifacts, collection("hostdecisions"), collection("humanquestions")).map(value =>
          org.goldenport.cncf.datastore.ComponentRecordStorageDeclaration.CreateGrant(component, "workflow", value))))
      val answerGrant = take(org.goldenport.cncf.datastore.ComponentRecordStorageDeclaration.createC(
        Vector(registrations, artifacts, collection("hostdecisions"), collection("humanquestions"), collection("humananswers")).map(value =>
          org.goldenport.cncf.datastore.ComponentRecordStorageDeclaration.CreateGrant(component, "workflow", value))))
      val payloadRoot = Files.createTempDirectory("goal-public-artifact-blobs-").toRealPath()
      val payloadStore = new LocalBlobStore(payloadRoot, name = "public-artifacts")
      def put(name: String, bytes: Vector[Byte]) = {
        val id = take(EntityId.bridgeFromParts("cncf", name, BlobRepository.CollectionId, Instant.EPOCH, name))
        val stored = take(payloadStore.put(BlobPutRequest(id, BlobKind.Binary, Some(name + ".bin"),
          ContentType.APPLICATION_OCTET_STREAM), Bag.binary(bytes.toArray)))
        GoalPhaseBlobArtifactSnapshot.Reference(stored.id, stored.storageRef)
      }
      val pair = GoalPhaseBlobArtifactSnapshot.References("public-artifact-pair", 0L,
        put("public_program", Vector[Byte](0, -1, 2)), put("public_planning", Vector[Byte](3, 4)))
      val artifactCommand = GoalPhaseArtifactRegistrationServiceOperation.Command(
        input.copy(programDigest = take(payloadStore.get(pair.program.storageRef)).digest,
          planningDigest = take(payloadStore.get(pair.planning.storageRef)).digest), pair)
      val artifactWire = GoalPhaseArtifactRegistrationServiceOperation.encodeCommand(artifactCommand)
      val artifactSelector = s"${component.name}/GoalPhaseService/${GoalPhaseArtifactRegistrationServiceOperation.operationName}"
      def artifactRequest(value: String) = Request.of(component = component.name, service = "GoalPhaseService",
        operation = GoalPhaseArtifactRegistrationServiceOperation.operationName, arguments = List(Argument("arg1", value)))
      var artifactReceipt: Option[OperationResponse] = None
      var blobReads = 0
      def installBlobs(current: SubsystemEventStoreBootstrap): Unit = {
        val store = new LocalBlobStore(payloadRoot, name = "public-artifacts")
        val blob = current.subsystem.findComponent(BuiltinComponentIdentity.BLOB).get
        blob.withPort(Component.Port.of(new BlobComponent.BlobService {
          def maxByteSize = 64L
          def blobStore = new BlobStore {
            def name = store.name
            def put(request: BlobPutRequest, payload: org.goldenport.bag.BinaryBag) = store.put(request, payload)
            def get(ref: BlobStorageRef) = { blobReads += 1; store.get(ref) }
            def delete(ref: BlobStorageRef) = store.delete(ref)
            def accessUrl(ref: BlobStorageRef) = store.accessUrl(ref)
            def status() = store.status()
          }
        }))
      }
      def declaredHost(decisionWrites: Boolean = false, answerWrites: Boolean = false) = take(SubsystemEventStoreBootstrap.createC(policy, declaration,
        ScopeContext(ScopeKind.Subsystem, "managed-goal", None, context.observability),
        ResolvedConfiguration(HostConfiguration(Map(
          "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
          "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)) ++ cliconfiguration),
          ConfigurationTrace.empty), None, org.goldenport.cncf.path.AliasResolver.empty,
        if (answerWrites) answerGrant else if (decisionWrites) decisionGrant else grant))
      val command = GoalPhaseStartCommand(GoalPhaseStartInput("phase-2", "repository"),
        "public-registration-selection", "local-participant", "public-registration-start")
      val selected = ProfileStartAdmission.Selection(command.selectionIdentity,
        StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"), definition.workflowIdentity,
        definition.workflowRevision, command.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
        command.idempotencyKey, None, Some(GoalPhaseStartCommandAdapter.selectedInput(command.input)))
      val selection = new ProfileStartAdmission.SelectionPort {
        def loadC(identity: String) = Consequence.success(Option.when(identity == selected.selectionId)(selected))
      }
      var unexpected = 0
      val installer = new GoalPhaseStepCloseServiceOperation.CallScopedInvocationPort {
        def resolveForCallC(identity: String, capsule: ActionCall.Core) = {
          final class InstallSelection(val core: ActionCall.Core) extends org.goldenport.cncf.action.ProcedureActionCall {
            def installC(): Consequence[Unit] = {
              given ExecutionContext = executionContext
              val space = executionContext.dataStoreSpace
              val prior = space.captureBinding()
              try for {
                _ <- bind_component_application_datastore_c("workflow")
                store <- space.dataStore(collection("hostselections"))
                raw <- ProfileSelectionJsonV1.encodeStorageC(selected)
                _ <- store.create(collection("hostselections"), DataStore.StringEntryId(selected.selectionId),
                  org.goldenport.record.Record.data(ProfileSelectionJsonV1.storageField -> raw))
              } yield ()
              finally space.restoreBinding(prior)
            }
            def execute() = Consequence.stateConflict("test-only selection installer is not a public human entry")
          }
          new InstallSelection(capsule).installC().flatMap(_ => Consequence.stateConflict("selection fixture stops before review"))
        }
      }
      val actor = _registration_context()
      val first = declaredHost()
      val started = try {
        installPath(first, selection, installer, () => unexpected += 1)
        val response = take(first.subsystem.executeOperationResponseInContext(startRequest(command), actor))
        first.subsystem.executeOperationResponseInContext(Request.of(component = component.name,
          service = "GoalPhaseService", operation = "requestStepClose", arguments = List(Argument("arg1",
            GoalPhaseStepCloseJson.encode(input)))), actor) shouldBe a[Consequence.Failure[?]]
        response match {
          case OperationResponse.Scalar(wire: String) => take(WorkflowWaitJsonV1.decodeC(wire)).handle
          case other => fail(s"Expected Start WAIT: $other")
        }
      } finally take(first.shutdownC())
      val registration = GoalPhaseSubmissionRegistration.Request("public-registration-1", input.submissionIdentity,
        "public-registration-key", command)
      val wire = GoalPhaseRegistrationServiceOperation.encodeCommand(registration)
      var racewinner: Option[(String, String)] = None
      def request(value: String) = Request.of(component = component.name, service = "GoalPhaseService",
        operation = GoalPhaseRegistrationServiceOperation.operationName, arguments = List(Argument("arg1", value)))
      val questionTemplate = GoalPhaseClosureDecisionTemplate(StateMachineRequiredOperationIdentity("host.step-close-authority"),
        StateMachineOperationIdentity("HumanBoundaryFixture", "decideStepClose"),
        StateMachineOperationIdentity("HumanBoundaryFixture", "recordStepCloseAuthority"))
      def installConfigured(current: SubsystemEventStoreBootstrap, prepare: Boolean = automatic,
        human: Option[GoalPhaseHumanDecisionServiceOperation.Host] = None): Unit =
        installPath(current, new ProfileStartAdmission.SelectionPort {
          def loadC(identity: String) = { unexpected += 1; Consequence.stateConflict("fixture selection cannot run") }
        }, new GoalPhaseStepCloseServiceOperation.InvocationPort {
          def resolveC(identity: String) = { unexpected += 1; Consequence.stateConflict("fixture invocation cannot run") }
        }, () => unexpected += 1, hostRecordReaders = true, initialArtifactMaximumBytes = Some(64),
          closureDecisionTemplate = Option.when(prepare)(questionTemplate), humanDecision = human)
      val publichost = declaredHost()
      val receipt = try {
        installConfigured(publichost)
        installBlobs(publichost)
        When("the generated-CML command is dispatched through the configured host service")
        val accepted = take(publichost.subsystem.executeOperationResponseInContext(request(wire), actor))
        Then("the response contains the stored derived receipt and exact retry changes no Workflow history")
        val json = accepted match {
          case OperationResponse.Scalar(value: String) => io.circe.parser.parse(value).toOption.get
          case other => fail(s"Expected registration result JSON: $other")
        }
        json.asObject.get.keys.toSet shouldBe Set("schemaVersion", "receipt")
        json.hcursor.get[String]("schemaVersion").toOption.get shouldBe GoalPhaseRegistrationServiceOperation.resultSchema
        json.hcursor.downField("receipt").get[String]("instanceIdentity").toOption.get shouldBe started.instanceIdentity.value
        json.hcursor.downField("receipt").get[String]("participantIdentity").toOption.get shouldBe command.participantIdentity
        take(publichost.subsystem.executeOperationResponseInContext(request(wire), actor)) shouldBe accepted
        take(storage(publichost).loadC(started.instanceIdentity)).get.history.size shouldBe 1
        When("the new CML artifact Operation creates its initial catalog through the same configured service")
        val artifactsAccepted = take(publichost.subsystem.executeOperationResponseInContext(artifactRequest(artifactWire), actor))
        artifactReceipt = Some(artifactsAccepted)
        val artifactJson = artifactsAccepted match {
          case OperationResponse.Scalar(value: String) => io.circe.parser.parse(value).toOption.get
          case other => fail(s"Expected artifact receipt: $other")
        }
        Then("the receipt derives the registered handle and committed pair without changing Workflow")
        artifactJson.hcursor.get[String]("schemaVersion").toOption.get shouldBe GoalPhaseArtifactRegistrationServiceOperation.resultSchema
        artifactJson.hcursor.downField("receipt").get[String]("instanceIdentity").toOption.get shouldBe started.instanceIdentity.value
        artifactJson.hcursor.downField("receipt").downField("selection").focus.get shouldBe
          GoalPhaseArtifactReferenceRecord.referencesJson(pair)
        take(publichost.subsystem.executeOperationResponseInContext(artifactRequest(artifactWire), actor)) shouldBe artifactsAccepted
        blobReads shouldBe 4
        val badArtifact = io.circe.parser.parse(artifactWire).toOption.get.mapObject(_.add("receipt", io.circe.Json.obj())).noSpaces
        publichost.subsystem.executeOperationResponseInContext(artifactRequest(badArtifact), actor) shouldBe a[Consequence.Failure[?]]
        publichost.subsystem.executeOperationResponse(artifactRequest(artifactWire)) shouldBe a[Consequence.Failure[?]]
        take(publichost.subsystem.executeOperationResponseInContext(artifactRequest(
          GoalPhaseArtifactRegistrationServiceOperation.encodeCommand(artifactCommand.copy(selection =
            pair.copy(selectionRevision = 1L)))), actor)) shouldBe artifactsAccepted
        blobReads shouldBe 6
        When("a caller supplies extra authority/storage facts, noncanonical scope, or a foreign actor")
        val commandjson = io.circe.parser.parse(wire).toOption.get
        val extra = commandjson.mapObject(_.add("receipt", io.circe.Json.obj())).noSpaces
        val badkey = commandjson.mapObject(_.add("submissionIdentity", io.circe.Json.fromString(" bad"))).noSpaces
        val foreign = GoalPhaseRegistrationServiceOperation.encodeCommand(registration.copy(
          start = command.copy(participantIdentity = "foreign")))
        Then("the public Operation rejects them without changing the registered result")
        Vector(extra, badkey, foreign).foreach(value =>
          publichost.subsystem.executeOperationResponseInContext(request(value), actor) shouldBe a[Consequence.Failure[?]])
        publichost.subsystem.executeOperationResponse(request(wire)) shouldBe a[Consequence.Failure[?]]
        take(publichost.subsystem.executeOperationResponseInContext(request(wire), actor)) shouldBe accepted
        When("CNCF CLI resolves configured local ingress and dispatches its normal selector and JSON argument")
        _configure_registration_cli(publichost)
        val cli = new org.goldenport.cncf.cli.CncfRuntime()
        val selector = s"${component.name}/GoalPhaseService/${GoalPhaseRegistrationServiceOperation.operationName}"
        val cliresult = take(cli.executeCommandResponse(publichost.subsystem, Array(selector, "--json", wire))).print
        Then("CLI returns the same stored receipt without a server or actor-context injection")
        cliresult shouldBe json.noSpaces
        When("CLI invokes the artifact command using normal configured local ingress without a server")
        take(cli.executeCommandResponse(publichost.subsystem, Array(artifactSelector, "--json", artifactWire))).print shouldBe
          artifactJson.noSpaces
        blobReads shouldBe 8
        take(storage(publichost).loadC(started.instanceIdentity)).get.history.size shouldBe 1
        Vector(extra, badkey, foreign).foreach(value =>
          cli.executeCommandResponse(publichost.subsystem, Array(selector, "--json", value)) shouldBe a[Consequence.Failure[?]])
        def simultaneous(commands: Vector[String]): Vector[Consequence[org.goldenport.protocol.Response]] = {
          val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
          val ready = new java.util.concurrent.CountDownLatch(2)
          val start = new java.util.concurrent.CountDownLatch(1)
          try {
            val pending = commands.map { commandwire =>
              pool.submit(new java.util.concurrent.Callable[Consequence[org.goldenport.protocol.Response]] {
                def call(): Consequence[org.goldenport.protocol.Response] = {
                  ready.countDown()
                  if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    throw new IllegalStateException("registration start barrier timed out")
                  new org.goldenport.cncf.cli.CncfRuntime().executeCommandResponse(publichost.subsystem,
                    Array(selector, "--json", commandwire))
                }
              })
            }
            ready.await(10, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
            start.countDown()
            pending.map(_.get(20, java.util.concurrent.TimeUnit.SECONDS))
          } finally {
            start.countDown()
            pool.shutdownNow()
            pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS) shouldBe true
          }
        }
        When("two independent CLI calls register identical facts at one fresh submission identity")
        val equalrequest = registration.copy(registrationIdentity = "equal-race-registration",
          submissionIdentity = "equal-race-submission", idempotencyIdentity = "equal-race-key")
        val equalwire = GoalPhaseRegistrationServiceOperation.encodeCommand(equalrequest)
        val equalresults = simultaneous(Vector(equalwire, equalwire))
        Then("at least one retains the immutable row and both exact retries return the same receipt")
        equalresults.count(_.isSuccess) should be >= 1
        val equalreceipt = take(cli.executeCommandResponse(publichost.subsystem, Array(selector, "--json", equalwire))).print
        equalresults.collect { case Consequence.Success(response) => response.print }.foreach(_ shouldBe equalreceipt)
        take(cli.executeCommandResponse(publichost.subsystem, Array(selector, "--json", equalwire))).print shouldBe equalreceipt
        When("two independent CLI calls race different keys at another fresh submission identity")
        val leftrequest = registration.copy(registrationIdentity = "conflicting-race-registration",
          submissionIdentity = "conflicting-race-submission", idempotencyIdentity = "race-left")
        val rightrequest = leftrequest.copy(idempotencyIdentity = "race-right")
        val leftwire = GoalPhaseRegistrationServiceOperation.encodeCommand(leftrequest)
        val rightwire = GoalPhaseRegistrationServiceOperation.encodeCommand(rightrequest)
        val conflicting = simultaneous(Vector(leftwire, rightwire))
        Then("exactly one request succeeds and the losing key cannot overwrite its retained receipt")
        conflicting.count(_.isSuccess) shouldBe 1
        val winnerindex = conflicting.indexWhere(_.isSuccess)
        val winnerwire = Vector(leftwire, rightwire)(winnerindex)
        val loserwire = Vector(leftwire, rightwire)(1 - winnerindex)
        val winnerreceipt = take(conflicting(winnerindex)).print
        take(cli.executeCommandResponse(publichost.subsystem, Array(selector, "--json", winnerwire))).print shouldBe winnerreceipt
        cli.executeCommandResponse(publichost.subsystem, Array(selector, "--json", loserwire)) shouldBe a[Consequence.Failure[?]]
        racewinner = Some(winnerwire -> winnerreceipt)
        take(storage(publichost).loadC(started.instanceIdentity)).get.history.size shouldBe 1
        unexpected shouldBe 0
        accepted
      } finally take(publichost.shutdownC())
      val reopened = declaredHost()
      try {
        installConfigured(reopened)
        installBlobs(reopened)
        When("another configured host reopens and retries the same public registration")
        val repeated = take(reopened.subsystem.executeOperationResponseInContext(request(wire), actor))
        Then("it returns the identical committed receipt without an installer or conversation map")
        repeated shouldBe receipt
        When("the reopened host executes the same registration through the standard CLI")
        _configure_registration_cli(reopened)
        val cli = new org.goldenport.cncf.cli.CncfRuntime()
        When("a fresh host reopens the artifact catalog and returns equivalent Service and CLI receipts")
        take(reopened.subsystem.executeOperationResponseInContext(artifactRequest(artifactWire), actor)) shouldBe artifactReceipt.get
        val artifactCli = take(cli.executeCommandResponse(reopened.subsystem, Array(artifactSelector, "--json", artifactWire))).print
        artifactReceipt.get match {
          case OperationResponse.Scalar(value: String) => artifactCli shouldBe value
          case other => fail(s"Expected retained artifact receipt: $other")
        }
        blobReads shouldBe 12
        val cliresult = take(cli.executeCommandResponse(reopened.subsystem,
          Array(s"${component.name}/GoalPhaseService/${GoalPhaseRegistrationServiceOperation.operationName}", "--json", wire))).print
        Then("the CLI receipt remains identical across managed-pool shutdown and reopen")
        receipt match {
          case OperationResponse.Scalar(value: String) => cliresult shouldBe value
          case other => fail(s"Expected recorded receipt: $other")
        }
        When("the independently reopened host retries the winner of the conflicting registration race")
        val (winnerwire, winnerreceipt) = racewinner.get
        val winnerresult = take(cli.executeCommandResponse(reopened.subsystem,
          Array(s"${component.name}/GoalPhaseService/${GoalPhaseRegistrationServiceOperation.operationName}", "--json", winnerwire))).print
        Then("the same immutable winner remains committed after pool shutdown and reopen")
        winnerresult shouldBe winnerreceipt
        unexpected shouldBe 0
      } finally take(reopened.shutdownC())
      val undeclared = host(path, cliconfiguration)
      try {
        installConfigured(undeclared)
        installBlobs(undeclared)
        When("the same database is reopened with readers but without its Create grant")
        val result = undeclared.subsystem.executeOperationResponseInContext(request(wire), actor)
        Then("even an equal retry cannot infer write authority from stored bytes")
        result shouldBe a[Consequence.Failure[?]]
        When("the ungranted host has real local ingress but receives the same CLI command")
        _configure_registration_cli(undeclared)
        val cli = new org.goldenport.cncf.cli.CncfRuntime()
        val cliresult = cli.executeCommandResponse(undeclared.subsystem,
          Array(s"${component.name}/GoalPhaseService/${GoalPhaseRegistrationServiceOperation.operationName}", "--json", wire))
        Then("CLI syntax and authenticated ingress do not confer the missing Create grant")
        cliresult shouldBe a[Consequence.Failure[?]]
        undeclared.subsystem.executeOperationResponseInContext(artifactRequest(artifactWire), actor) shouldBe a[Consequence.Failure[?]]
        cli.executeCommandResponse(undeclared.subsystem, Array(artifactSelector, "--json", artifactWire)) shouldBe a[Consequence.Failure[?]]
        blobReads shouldBe 12
        take(storage(undeclared).loadC(started.instanceIdentity)).get.history.size shouldBe 1
      } finally take(undeclared.shutdownC())
      var recoveryIdentity = Option.empty[org.goldenport.cncf.workflow.ContinuationIdentity]
      var acceptedCandidate = Option.empty[StepCloseRequest]
      def recoveryRequest(identity: org.goldenport.cncf.workflow.ContinuationIdentity) = Request.of(component = component.name,
        service = "GoalPhaseService", operation = GoalPhaseReviewRecoveryServiceOperation.operationName,
        arguments = List(Argument("arg1", GoalPhaseReviewRecoveryServiceOperation.encodeCommand(identity))))
      // Observation and question registration do not read artifact bytes.
      // The later artifact command still reads its actual Blob pair.
      val acceptedReads = 12
      val recoveredReads = 12
      val artifactReopenReads = 14
      val beforePreparationReads = 14
      val reviewing = declaredHost(decisionWrites = automatic)
      val completed = try {
        installConfigured(reviewing)
        installBlobs(reviewing)
        _configure_registration_cli(reviewing)
        val cli = new org.goldenport.cncf.cli.CncfRuntime()
        When("a fresh configured host issues review for the actually registered candidate through CLI")
        val issued = take(cli.executeCommandResponse(reviewing.subsystem,
          Array(s"${component.name}/GoalPhaseService/requestStepClose", "--json",
            GoalPhaseStepCloseJson.encode(artifactCommand.subject)))).print
        val work = take(WorkflowWorkOrderJsonV1.decodeC(issued, GoalPhaseStepCloseJson.payloadCodec))
        work.handle shouldBe started
        val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
        recoveryIdentity = Some(order.continuationId)
        order.input.get.value shouldBe artifactCommand.subject
        take(storage(reviewing).loadC(started.instanceIdentity)).get.revision shouldBe InstanceRevision(2L)
        blobReads shouldBe 12
        val candidate = take(StepCloseRequestAdapter.submissionC(artifactCommand.subject)).candidate.reference
        val evidence = StepReviewEvidence("public-catalog-review", candidate.revision,
          "2026-09-30T23:59:00Z", "full-step", "actual candidate reviewed", "receipt")
        acceptedCandidate = Some(artifactCommand.subject.copy(reviewEvidence = Vector(evidence)))
        val submitted = ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision,
          order.context.snapshot, TypedValue("ReviewStepResult", ReviewStepResult("accept", "review passed", Vector(evidence))),
          ContextReference(evidence.evidenceIdentity, candidate.revision), Vector.empty, ExecutionEvidence(Vector.empty))
        When("accepted review completion observes authority for the registered candidate without an artifact preflight")
        val result = reviewing.subsystem.executeOperationResponseInContext(reviewRequest(submitted), actor)
        if (automatic) {
          Then("Workflow prepares the question automatically from the accepted result without a second command")
          val projected = take(result) match {
            case OperationResponse.Scalar(wire: String) => take(WorkflowDecisionJsonV1.decodeC(wire, GoalPhaseClosureAuthority.inputCodec))
            case other => fail(s"Expected automatic Decision JSON: $other")
          }
          val question = projected.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseClosureAuthority.Input]].request
          question.input.get.value.candidate shouldBe acceptedCandidate.get
          question.resultType shouldBe None
          question.completion shouldBe CompletionContract("human-stop-only", Vector.empty)
        } else result match {
          case Consequence.Failure(conclusion) =>
            conclusion.display should include("Host has no recorded closure Decision")
          case other => fail(s"Missing Decision unexpectedly allowed closure: $other")
        }
        blobReads shouldBe acceptedReads
        unexpected shouldBe 0
        val retained = take(storage(reviewing).loadC(started.instanceIdentity)).get
        retained.revision shouldBe InstanceRevision(3L)
        retained.history.size shouldBe 3
        retained.suspension shouldBe None
        retained.lifecycle shouldBe Lifecycle.Active
        reviewing.subsystem.executeOperationResponseInContext(reviewRequest(submitted), actor) shouldBe a[Consequence.Failure[?]]
        blobReads shouldBe acceptedReads
        When("recovery uses the committed judgment instead of resubmitting semantic review")
        reviewing.subsystem.executeOperationResponseInContext(recoveryRequest(order.continuationId),
          ExecutionContext.create()) shouldBe a[Consequence.Failure[?]]
        reviewing.subsystem.executeOperationResponseInContext(recoveryRequest(org.goldenport.cncf.workflow.ContinuationIdentity("missing-review")), actor) shouldBe
          a[Consequence.Failure[?]]
        blobReads shouldBe acceptedReads
        val recovered = reviewing.subsystem.executeOperationResponseInContext(recoveryRequest(order.continuationId), actor)
        if (automatic) take(recovered) shouldBe take(result)
        else recovered match {
          case Consequence.Failure(conclusion) => conclusion.display should include("Host has no recorded closure Decision")
          case other => fail(s"Recovery fabricated closure without Decision: $other")
        }
        blobReads shouldBe recoveredReads
        take(storage(reviewing).loadC(started.instanceIdentity)).get shouldBe retained
        retained
      } finally take(reviewing.shutdownC())
      val afterReview = declaredHost(decisionWrites = automatic)
      try {
        installConfigured(afterReview)
        installBlobs(afterReview)
        _configure_registration_cli(afterReview)
        When("another host reopens after the refused closure projection")
        take(storage(afterReview).loadC(started.instanceIdentity)).get shouldBe completed
        val recoveredArtifact = take(new org.goldenport.cncf.cli.CncfRuntime().executeCommandResponse(afterReview.subsystem,
          Array(artifactSelector, "--json", artifactWire))).print
        Then("the retained artifact remains readable and equal retry cannot reset accepted review history")
        artifactReceipt.get match {
          case OperationResponse.Scalar(value: String) => recoveredArtifact shouldBe value
          case other => fail(s"Expected original artifact receipt: $other")
        }
        blobReads shouldBe artifactReopenReads
        take(storage(afterReview).loadC(started.instanceIdentity)).get shouldBe completed
        When("CLI recovers only the deterministic projection after a fresh managed host reopens")
        val reopenedRecovery = new org.goldenport.cncf.cli.CncfRuntime().executeCommandResponse(afterReview.subsystem,
          Array(s"${component.name}/GoalPhaseService/${GoalPhaseReviewRecoveryServiceOperation.operationName}", "--json",
            GoalPhaseReviewRecoveryServiceOperation.encodeCommand(recoveryIdentity.get)))
        if (automatic) {
          val question = take(WorkflowDecisionJsonV1.decodeC(take(reopenedRecovery).print, GoalPhaseClosureAuthority.inputCodec))
          question.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseClosureAuthority.Input]].request.input.get.value.candidate shouldBe acceptedCandidate.get
        } else reopenedRecovery match {
          case Consequence.Failure(conclusion) => conclusion.display should include("Host has no recorded closure Decision")
          case other => fail(s"CLI recovery fabricated closure: $other")
        }
        blobReads shouldBe beforePreparationReads
        take(storage(afterReview).loadC(started.instanceIdentity)).get shouldBe completed
        unexpected shouldBe 0
      } finally take(afterReview.shutdownC())

      val acceptedInput = acceptedCandidate.get
      val intent = take(GoalPhaseClosureAuthority.intentC(acceptedInput, started, completed))
      val decision = take(questionTemplate.requestC(intent))
      val decisionWire = take(WorkflowDecisionJsonV1.encodeC(WorkflowInteraction[GoalPhaseClosureAuthority.Input, Nothing](
        started, WorkflowContinuation.Decision(decision, MinimalPresentation("Recorded closure stop", "No approval"))),
        GoalPhaseClosureAuthority.inputCodec))
      Given("the accepted closure intent is already assessed, and the metadata writer cannot resolve the selection again")
      val assessedhost = take(SubsystemEventStoreBootstrap.createC(policy, declaration,
        ScopeContext(ScopeKind.Subsystem, "managed-goal", None, context.observability),
        ResolvedConfiguration(HostConfiguration(Map(
          "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
          "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)) ++
          cliconfiguration.updated(GoalPhaseHostRecordReadPolicy.prefix + "selection-collection",
            ConfigurationValue.StringValue(collection("unobservedselections").print))), ConfigurationTrace.empty),
        None, org.goldenport.cncf.path.AliasResolver.empty, decisionGrant))
      var independentregistration = Option.empty[Consequence[ContinuationRequest[GoalPhaseClosureAuthority.Input]]]
      var assessedquestion = Option.empty[ContinuationRequest[GoalPhaseClosureAuthority.Input]]
      var assessedcore = Option.empty[ActionCall.Core]
      val assessedinstaller = new GoalPhaseStepCloseServiceOperation.CallScopedInvocationPort {
        def resolveForCallC(identity: String, capsule: ActionCall.Core): Consequence[WorkflowHandle] =
          GoalPhaseParticipantAdmission.admitC(command.participantIdentity, capsule.executionContext).flatMap { _ =>
            val workflow = GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations)
            independentregistration = Some(GoalPhaseManagedClosureDecisionRegistration.registerForCallC(
              recoveryIdentity.get, intent, decisionWire, capsule, workflow, collection("hostdecisions")))
            assessedcore = Some(capsule)
            GoalPhaseManagedClosureDecisionRegistration.retainAssessedForCallC(intent, decisionWire,
              capsule, workflow, collection("hostdecisions")).flatMap { question =>
              assessedquestion = Some(question)
              Consequence.stateConflict("Assessed metadata fixture stops before review or command execution")
            }
          }
      }
      try {
        installPath(assessedhost, selection, assessedinstaller, () => unexpected += 1)
        When("the owning managed call retains its assessed question at the declared Create destination")
        val registrationresult = assessedhost.subsystem.executeOperationResponseInContext(Request.of(
          component = component.name, service = "GoalPhaseService", operation = "requestStepClose",
          arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(artifactCommand.subject)))), actor)
        Then("independent registration refuses unresolved input, but assessed follow-up needs no second selection and grants no execution")
        independentregistration.get shouldBe a[Consequence.Failure[?]]
        assessedquestion shouldBe Some(decision)
        registrationresult shouldBe a[Consequence.Failure[?]]
        take(storage(assessedhost).loadC(started.instanceIdentity)).get shouldBe completed
        blobReads shouldBe beforePreparationReads
        unexpected shouldBe 0
        When("the writer is invoked without a current call or with its escaped lease")
        val workflow = GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations)
        val nocall = GoalPhaseManagedClosureDecisionRegistration.retainAssessedForCallC(intent, decisionWire,
          null, workflow, collection("hostdecisions"))
        val escaped = GoalPhaseManagedClosureDecisionRegistration.retainAssessedForCallC(intent, decisionWire,
          assessedcore.get, workflow, collection("hostdecisions"))
        Then("the actual storage boundary refuses both without changing native state")
        nocall shouldBe a[Consequence.Failure[?]]
        escaped shouldBe a[Consequence.Failure[?]]
        take(storage(assessedhost).loadC(started.instanceIdentity)).get shouldBe completed
      } finally take(assessedhost.shutdownC())
      var decisionInstalled = false
      var recorded = Option.empty[Consequence[ContinuationRequest[GoalPhaseClosureAuthority.Input]]]
      val decisionInstaller = new GoalPhaseStepCloseServiceOperation.CallScopedInvocationPort {
          def resolveForCallC(identity: String, capsule: ActionCall.Core) = {
            val workflow = GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations)
            GoalPhaseManagedClosureDecisionRegistration.registerForCallC(
              org.goldenport.cncf.workflow.ContinuationIdentity("missing-review"), intent, decisionWire, capsule,
              workflow, collection("hostdecisions")) shouldBe a[Consequence.Failure[?]]
            GoalPhaseManagedClosureDecisionRegistration.registerForCallC(recoveryIdentity.get,
              intent.copy(expectedRevision = StateMachineRevision("4")), decisionWire, capsule,
              workflow, collection("hostdecisions")) shouldBe a[Consequence.Failure[?]]
            val result = GoalPhaseManagedClosureDecisionRegistration.registerForCallC(recoveryIdentity.get,
              intent, decisionWire, capsule, workflow, collection("hostdecisions"))
            recorded = Some(result)
            result.flatMap { actual =>
              actual shouldBe decision
              take(GoalPhaseManagedClosureDecisionRegistration.registerForCallC(recoveryIdentity.get,
                intent, decisionWire, capsule, workflow, collection("hostdecisions"))) shouldBe decision
              decisionInstalled = true
              Consequence.stateConflict("Decision fixture stops before review")
            }
          }
        }
      def runDecisionRegistration(current: SubsystemEventStoreBootstrap) = {
        installPath(current, selection, decisionInstaller, () => unexpected += 1)
        current.subsystem.executeOperationResponseInContext(Request.of(component = component.name,
          service = "GoalPhaseService", operation = "requestStepClose",
          arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(artifactCommand.subject)))), actor)
      }
      val ungrantedRecording = declaredHost()
      try {
        When("the production Decision writer is called without its separate captured Create grant")
        runDecisionRegistration(ungrantedRecording) shouldBe a[Consequence.Failure[?]]
        recorded.get shouldBe a[Consequence.Failure[?]]
        decisionInstalled shouldBe false
        take(storage(ungrantedRecording).loadC(started.instanceIdentity)).get shouldBe completed
        blobReads shouldBe beforePreparationReads
      } finally take(ungrantedRecording.shutdownC())
      val ungrantedPublic = declaredHost()
      try {
        installConfigured(ungrantedPublic, prepare = true)
        installBlobs(ungrantedPublic)
        When("the public question command has no captured Decision Create grant")
        ungrantedPublic.subsystem.executeOperationResponseInContext(Request.of(component = component.name,
          service = "GoalPhaseService", operation = GoalPhaseReviewRecoveryServiceOperation.preparationOperationName,
          arguments = List(Argument("arg1", GoalPhaseReviewRecoveryServiceOperation.encodePreparationCommand(recoveryIdentity.get)))), actor) shouldBe
          a[Consequence.Failure[?]]
        take(storage(ungrantedPublic).loadC(started.instanceIdentity)).get shouldBe completed
        blobReads shouldBe beforePreparationReads
      } finally take(ungrantedPublic.shutdownC())
      val recording = declaredHost(decisionWrites = true)
      try {
        installConfigured(recording, prepare = true)
        installBlobs(recording)
        _configure_registration_cli(recording)
        val preparationWire = GoalPhaseReviewRecoveryServiceOperation.encodePreparationCommand(recoveryIdentity.get)
        def prepareRequest(wire: String) = Request.of(component = component.name, service = "GoalPhaseService",
          operation = GoalPhaseReviewRecoveryServiceOperation.preparationOperationName, arguments = List(Argument("arg1", wire)))
        When("the generated public question command is anonymous, missing its review, or carries caller-owned Decision metadata")
        recording.subsystem.executeOperationResponseInContext(prepareRequest(preparationWire), ExecutionContext.create()) shouldBe
          a[Consequence.Failure[?]]
        recording.subsystem.executeOperationResponseInContext(prepareRequest(
          GoalPhaseReviewRecoveryServiceOperation.encodePreparationCommand(org.goldenport.cncf.workflow.ContinuationIdentity("missing-review"))), actor) shouldBe
          a[Consequence.Failure[?]]
        val extraQuestion = io.circe.parser.parse(preparationWire).toOption.get.mapObject(_.add("decision", io.circe.Json.obj())).noSpaces
        recording.subsystem.executeOperationResponseInContext(prepareRequest(extraQuestion), actor) shouldBe a[Consequence.Failure[?]]
        blobReads shouldBe beforePreparationReads
        When("ordinary recovery automatically prepares a question without a separate preparation command")
        val result = take(recording.subsystem.executeOperationResponseInContext(recoveryRequest(recoveryIdentity.get), actor))
        val projected = result match {
          case OperationResponse.Scalar(wire: String) => take(WorkflowDecisionJsonV1.decodeC(wire, GoalPhaseClosureAuthority.inputCodec))
          case other => fail(s"Expected prepared question JSON: $other")
        }
        projected.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseClosureAuthority.Input]].request shouldBe decision
        blobReads shouldBe beforePreparationReads
        When("standard CLI retries the same preparation without replacing metadata or appending Workflow")
        val repeated = take(new org.goldenport.cncf.cli.CncfRuntime().executeCommandResponse(recording.subsystem,
          Array(s"${component.name}/GoalPhaseService/${GoalPhaseReviewRecoveryServiceOperation.preparationOperationName}",
            "--json", preparationWire))).print
        take(WorkflowDecisionJsonV1.decodeC(repeated, GoalPhaseClosureAuthority.inputCodec)) shouldBe projected
        blobReads shouldBe beforePreparationReads
        take(storage(recording).loadC(started.instanceIdentity)).get shouldBe completed
        unexpected shouldBe 0
      } finally take(recording.shutdownC())
      val humanQuestion = take(GoalPhaseHumanDecisionContract.createC("public-human-question", intent,
        HumanDecisionContractFixture.routing, HumanDecisionContractFixture.choices))
      var humanResult = Option.empty[Consequence[GoalPhaseHumanDecisionContract.Question]]
      var escapedHumanCore = Option.empty[ActionCall.Core]
      val humanDeclaration = GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations)
      val humanInstaller = new GoalPhaseStepCloseServiceOperation.CallScopedInvocationPort {
        def resolveForCallC(identity: String, capsule: ActionCall.Core) = {
          escapedHumanCore = Some(capsule)
          val result = GoalPhaseManagedHumanQuestionRegistration.registerForCallC(recoveryIdentity.get, humanQuestion,
            capsule, humanDeclaration)
          humanResult = Some(result)
          result.flatMap { actual =>
            actual shouldBe humanQuestion
            take(GoalPhaseManagedHumanQuestionRegistration.registerForCallC(recoveryIdentity.get, humanQuestion,
              capsule, humanDeclaration)) shouldBe humanQuestion
            val changed = humanQuestion.copy(choices = humanQuestion.choices.updated(0,
              humanQuestion.choices.head.copy(label = "Changed visible question")))
            GoalPhaseManagedHumanQuestionRegistration.registerForCallC(recoveryIdentity.get, changed, capsule,
              humanDeclaration) shouldBe a[Consequence.Failure[?]]
            val stale = take(GoalPhaseHumanDecisionContract.createC("stale-human-question",
              intent.copy(expectedRevision = StateMachineRevision("4")), HumanDecisionContractFixture.routing,
              HumanDecisionContractFixture.choices))
            GoalPhaseManagedHumanQuestionRegistration.registerForCallC(recoveryIdentity.get, stale, capsule,
              humanDeclaration) shouldBe a[Consequence.Failure[?]]
            Consequence.stateConflict("Human question fixture stops without answer or authority")
          }
        }
      }
      def runHumanRegistration(current: SubsystemEventStoreBootstrap) = {
        installPath(current, selection, humanInstaller, () => unexpected += 1)
        current.subsystem.executeOperationResponseInContext(Request.of(component = component.name,
          service = "GoalPhaseService", operation = "requestStepClose",
          arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(artifactCommand.subject)))), actor)
      }
      val ungrantedHuman = declaredHost()
      try {
        When("answerable question registration has its declaration but lacks a captured question Create grant")
        runHumanRegistration(ungrantedHuman) shouldBe a[Consequence.Failure[?]]
        humanResult.get shouldBe a[Consequence.Failure[?]]
        take(storage(ungrantedHuman).loadC(started.instanceIdentity)).get shouldBe completed
      } finally take(ungrantedHuman.shutdownC())
      for (reopen <- Vector(false, true)) {
        val humanHost = declaredHost(decisionWrites = true)
        try {
          When(s"the actual managed writer retains the answerable question, host reopen = $reopen")
          runHumanRegistration(humanHost) shouldBe a[Consequence.Failure[?]]
          humanResult.get shouldBe Consequence.success(humanQuestion)
          take(storage(humanHost).loadC(started.instanceIdentity)).get shouldBe completed
          GoalPhaseManagedHumanQuestionRegistration.registerForCallC(recoveryIdentity.get, humanQuestion,
            escapedHumanCore.get, humanDeclaration) shouldBe a[Consequence.Failure[?]]
          blobReads shouldBe beforePreparationReads
          unexpected shouldBe 0
        } finally take(humanHost.shutdownC())
      }
      var answerResult = Option.empty[Consequence[GoalPhaseHumanInteractionVerification.Receipt]]
      var verifiedCalls = 0
      val answer = GoalPhaseHumanDecisionContract.Answer("keep-open")
      val answerAsOf = Instant.parse("2026-10-01T00:00:00Z")
      val fixtureVerifier = new GoalPhaseHumanInteractionVerification.Verifier {
        val issuerIdentity = "managed-fixture-human-issuer"
        def verifyForCallC(reference: String, expected: GoalPhaseHumanInteractionVerification.Binding, capsule: ActionCall.Core) = {
          verifiedCalls += 1
          Consequence.success(Some(GoalPhaseHumanInteractionVerification.Receipt(reference, issuerIdentity,
            "fixture-independent-human", GoalPhaseHumanInteractionVerification.purpose, expected,
            answerAsOf.minusSeconds(86400))))
        }
      }
      val answerInstaller = new GoalPhaseStepCloseServiceOperation.CallScopedInvocationPort {
        def resolveForCallC(identity: String, capsule: ActionCall.Core) = {
          escapedHumanCore = Some(capsule)
          def register(value: GoalPhaseHumanDecisionContract.Answer, reference: String, verifier: GoalPhaseHumanInteractionVerification.Verifier,
            now: Instant = answerAsOf) = GoalPhaseManagedHumanAnswerRegistration.registerForCallC(recoveryIdentity.get,
              humanQuestion.request.continuationId, value, reference, now, capsule, humanDeclaration, verifier)
          if (verifiedCalls == 0) {
            register(answer, "managed-human-interaction", null) shouldBe a[Consequence.Failure[?]]
          }
          val result = register(answer, "managed-human-interaction", fixtureVerifier)
          answerResult = Some(result)
          result.flatMap { receipt =>
            receipt.binding.question shouldBe humanQuestion
            receipt.binding.answer shouldBe answer
            receipt.humanParticipant should not be receipt.binding.submittingParticipant
            // After expiry an equal retry observes, not renews, the saved receipt.
            take(register(answer, "managed-human-interaction", null, answerAsOf.plusSeconds(60))) shouldBe receipt
            register(GoalPhaseHumanDecisionContract.Answer("request-authorization"), "managed-human-interaction", fixtureVerifier) shouldBe a[Consequence.Failure[?]]
            register(answer, "different-interaction", fixtureVerifier) shouldBe a[Consequence.Failure[?]]
            Consequence.stateConflict("Answer fixture stops without advancing history or granting commands")
          }
        }
      }
      def runAnswerRegistration(current: SubsystemEventStoreBootstrap) = {
        installPath(current, selection, answerInstaller, () => unexpected += 1)
        current.subsystem.executeOperationResponseInContext(Request.of(component = component.name,
          service = "GoalPhaseService", operation = "requestStepClose",
          arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(artifactCommand.subject)))), actor)
      }
      val ungrantedAnswer = declaredHost(decisionWrites = true)
      try {
        When("the retained question has its grant but the independent answer Create grant is absent")
        runAnswerRegistration(ungrantedAnswer) shouldBe a[Consequence.Failure[?]]
        Then("no verifier is invoked and no accepted answer is returned")
        answerResult.get shouldBe a[Consequence.Failure[?]]
        verifiedCalls shouldBe 0
      } finally take(ungrantedAnswer.shutdownC())
      var firstAnswer = Option.empty[GoalPhaseHumanInteractionVerification.Receipt]
      for (reopen <- Vector(false, true)) {
        val answerHost = declaredHost(answerWrites = true)
        try {
          When(s"the actual managed answer writer creates or recovers its immutable fixture receipt, reopen = $reopen")
          runAnswerRegistration(answerHost) shouldBe a[Consequence.Failure[?]]
          val receipt = take(answerResult.get)
          Then("exact retry and physical reopen retain one receipt, never reverify or advance Workflow")
          firstAnswer.foreach(value => receipt shouldBe value)
          firstAnswer = Some(receipt)
          verifiedCalls shouldBe 1
          take(storage(answerHost).loadC(started.instanceIdentity)).get shouldBe completed
          GoalPhaseManagedHumanAnswerRegistration.registerForCallC(recoveryIdentity.get, humanQuestion.request.continuationId,
            answer, "managed-human-interaction", answerAsOf, escapedHumanCore.get, humanDeclaration, fixtureVerifier) shouldBe a[Consequence.Failure[?]]
          blobReads shouldBe beforePreparationReads
          unexpected shouldBe 0
        } finally take(answerHost.shutdownC())
      }
      import GoalPhaseHumanDecisionServiceOperation.{AnswerCommand, Host as HumanHost}
      def humanRequest(operation: String, wire: String) = Request.of(component = component.name, service = "GoalPhaseService",
        operation = operation, arguments = List(Argument("arg1", wire)))
      def scalar(response: OperationResponse) = response match {
        case OperationResponse.Scalar(value: String) => value
        case other => fail(s"Expected Human Decision JSON, got $other")
      }
      val prepareHumanWire = GoalPhaseHumanDecisionServiceOperation.encodePreparationCommand(recoveryIdentity.get)
      val noHumanBinding = declaredHost(answerWrites = true)
      try {
        installConfigured(noHumanBinding, prepare = false)
        When("the configured Factory has no human presentation/verifier dependency")
        noHumanBinding.subsystem.executeOperationResponseInContext(humanRequest(
          GoalPhaseHumanDecisionServiceOperation.prepareOperationName, prepareHumanWire), actor) shouldBe a[Consequence.Failure[?]]
        Then("the CML declaration alone exposes no working human entry")
        verifiedCalls shouldBe 1
      } finally take(noHumanBinding.shutdownC())
      val publicUngrant = declaredHost(decisionWrites = true)
      try {
        installConfigured(publicUngrant, prepare = false, human = Some(HumanHost(questionTemplate, fixtureVerifier)))
        When("the public answer entry has a configured verifier but no independent answer Create grant")
        val wire = GoalPhaseHumanDecisionServiceOperation.encodeAnswerCommand(AnswerCommand(recoveryIdentity.get,
          org.goldenport.cncf.workflow.ContinuationIdentity("goal-phase-human:" + recoveryIdentity.get.value), answer, "public-human-interaction"))
        publicUngrant.subsystem.executeOperationResponseInContext(humanRequest(GoalPhaseHumanDecisionServiceOperation.answerOperationName, wire), actor) shouldBe a[Consequence.Failure[?]]
        Then("actor authentication and CML cannot substitute answer storage authority")
        verifiedCalls shouldBe 1
      } finally take(publicUngrant.shutdownC())
      val publicHumanHost = declaredHost(answerWrites = true)
      var publicQuestion = Option.empty[GoalPhaseHumanDecisionContract.Question]
      var publicAnswerResponse = Option.empty[OperationResponse]
      try {
        installConfigured(publicHumanHost, prepare = false, human = Some(HumanHost(questionTemplate, fixtureVerifier)))
        _configure_registration_cli(publicHumanHost)
        When("the generated Human Decision Operation prepares its own question from the retained review")
        val response = take(publicHumanHost.subsystem.executeOperationResponseInContext(
          humanRequest(GoalPhaseHumanDecisionServiceOperation.prepareOperationName, prepareHumanWire), actor))
        val payload = io.circe.parser.parse(scalar(response)).toOption.get
        payload.hcursor.get[String]("schemaVersion").toOption.get shouldBe GoalPhaseHumanDecisionServiceOperation.questionResultSchema
        val prepared = take(GoalPhaseHumanQuestionRecord.decodeJsonC(payload.hcursor.downField("question").focus.get.noSpaces))
        publicQuestion = Some(prepared)
        Then("Workflow fixes the choices, question identity and declared human completion without advancing history")
        prepared.choices shouldBe GoalPhaseHumanDecisionServiceOperation.choices
        prepared.request.completionOperation shouldBe StateMachineOperationIdentity("GoalPhaseService", GoalPhaseHumanDecisionServiceOperation.answerOperationName)
        take(publicHumanHost.subsystem.executeOperationResponseInContext(
          humanRequest(GoalPhaseHumanDecisionServiceOperation.prepareOperationName, prepareHumanWire), actor)) shouldBe response
        val command = AnswerCommand(recoveryIdentity.get, prepared.request.continuationId, answer, "public-human-interaction")
        val wire = GoalPhaseHumanDecisionServiceOperation.encodeAnswerCommand(command)
        When("the public answer Operation receives only the issued question, choice and independent fixture reference")
        val accepted = take(publicHumanHost.subsystem.executeOperationResponseInContext(
          humanRequest(GoalPhaseHumanDecisionServiceOperation.answerOperationName, wire), actor))
        publicAnswerResponse = Some(accepted)
        val answerPayload = io.circe.parser.parse(scalar(accepted)).toOption.get
        val retained = take(GoalPhaseHumanAnswerRecord.decodeJsonC(answerPayload.hcursor.downField("receipt").focus.get.noSpaces))
        Then("Service and ordinary launcher CLI return the exact same retained fixture receipt, never Git authority")
        retained.binding.question shouldBe prepared
        retained.binding.answer shouldBe answer
        answerPayload.hcursor.get[String]("schemaVersion").toOption.get shouldBe GoalPhaseHumanDecisionServiceOperation.answerResultSchema
        val cli = take(new org.goldenport.cncf.cli.CncfRuntime().executeCommandResponse(publicHumanHost.subsystem,
          Array(s"${component.name}/GoalPhaseService/${GoalPhaseHumanDecisionServiceOperation.answerOperationName}", "--json", wire))).print
        cli shouldBe scalar(accepted)
        verifiedCalls shouldBe 2
        val extra = io.circe.parser.parse(wire).toOption.get.mapObject(_.add("receipt", io.circe.Json.obj())).noSpaces
        publicHumanHost.subsystem.executeOperationResponseInContext(humanRequest(GoalPhaseHumanDecisionServiceOperation.answerOperationName, extra), actor) shouldBe a[Consequence.Failure[?]]
        publicHumanHost.subsystem.executeOperationResponseInContext(humanRequest(
          GoalPhaseHumanDecisionServiceOperation.answerOperationName, wire), ExecutionContext.create()) shouldBe a[Consequence.Failure[?]]
        val conflictingWire = GoalPhaseHumanDecisionServiceOperation.encodeAnswerCommand(command.copy(
          answer = GoalPhaseHumanDecisionContract.Answer("request-authorization")))
        publicHumanHost.subsystem.executeOperationResponseInContext(humanRequest(
          GoalPhaseHumanDecisionServiceOperation.answerOperationName, conflictingWire), actor) shouldBe a[Consequence.Failure[?]]
        take(storage(publicHumanHost).loadC(started.instanceIdentity)).get shouldBe completed
        blobReads shouldBe beforePreparationReads
        unexpected shouldBe 0
      } finally take(publicHumanHost.shutdownC())
      val reopenedHumanHost = declaredHost(answerWrites = true)
      try {
        val refusingVerifier = new GoalPhaseHumanInteractionVerification.Verifier {
          val issuerIdentity = "unavailable-fixture-issuer"
          def verifyForCallC(reference: String, expected: GoalPhaseHumanInteractionVerification.Binding, capsule: ActionCall.Core) = {
            unexpected += 1
            Consequence.stateConflict("Equal retry must not call this verifier")
          }
        }
        installConfigured(reopenedHumanHost, prepare = false, human = Some(HumanHost(questionTemplate, refusingVerifier)))
        _configure_registration_cli(reopenedHumanHost)
        When("a separately bootstrapped public host recovers an answer whose original verifier is unavailable")
        val wire = GoalPhaseHumanDecisionServiceOperation.encodeAnswerCommand(AnswerCommand(recoveryIdentity.get,
          publicQuestion.get.request.continuationId, answer, "public-human-interaction"))
        val cli = take(new org.goldenport.cncf.cli.CncfRuntime().executeCommandResponse(reopenedHumanHost.subsystem,
          Array(s"${component.name}/GoalPhaseService/${GoalPhaseHumanDecisionServiceOperation.answerOperationName}", "--json", wire))).print
        Then("the same stored answer is observed, with no new verifier call, history or effect")
        cli shouldBe scalar(publicAnswerResponse.get)
        verifiedCalls shouldBe 2
        unexpected shouldBe 0
        take(storage(reopenedHumanHost).loadC(started.instanceIdentity)).get shouldBe completed
      } finally take(reopenedHumanHost.shutdownC())
      val resumed = declaredHost()
      val advanced = try {
        installConfigured(resumed, prepare = false)
        installBlobs(resumed)
        _configure_registration_cli(resumed)
        When("a production configured host recovers the recorded Decision without resubmitting review")
        val response = take(resumed.subsystem.executeOperationResponseInContext(recoveryRequest(recoveryIdentity.get), actor))
        val restored = response match {
          case OperationResponse.Scalar(value: String) =>
            take(WorkflowDecisionJsonV1.decodeC(value, GoalPhaseClosureAuthority.inputCodec))
          case other => fail(s"Expected recorded Decision JSON: $other")
        }
        restored.handle shouldBe started
        restored.current.asInstanceOf[WorkflowContinuation.Decision[GoalPhaseClosureAuthority.Input]].request shouldBe decision
        blobReads shouldBe beforePreparationReads
        take(storage(resumed).loadC(started.instanceIdentity)).get shouldBe completed
        val cliResult = take(new org.goldenport.cncf.cli.CncfRuntime().executeCommandResponse(resumed.subsystem,
          Array(s"${component.name}/GoalPhaseService/${GoalPhaseReviewRecoveryServiceOperation.operationName}", "--json",
            GoalPhaseReviewRecoveryServiceOperation.encodeCommand(recoveryIdentity.get)))).print
        take(WorkflowDecisionJsonV1.decodeC(cliResult, GoalPhaseClosureAuthority.inputCodec)) shouldBe restored
        blobReads shouldBe beforePreparationReads
        When("an independent canonical fixture writer advances the instance before a later recovery request")
        val retained = take(storage(resumed).appendC(completed.identity, completed.revision,
          completed.history.last.copy(sequence = HistorySequence(4L), revision = InstanceRevision(4L),
            causation = CausationReference("test-competing-writer"))))
        retained.history.take(3) shouldBe completed.history
        take(storage(resumed).loadC(started.instanceIdentity)).get shouldBe retained
        Then("the actual input state is no longer the reviewed revision, so recovery is refused without artifact reads")
        resumed.subsystem.executeOperationResponseInContext(recoveryRequest(recoveryIdentity.get), actor) match {
          case Consequence.Failure(conclusion) => conclusion.display should include("Recovery review is no longer current")
          case other => fail(s"Stale review recovered against revision 4: $other")
        }
        blobReads shouldBe beforePreparationReads
        unexpected shouldBe 0
        retained
      } finally take(resumed.shutdownC())
      val finalHost = declaredHost()
      try {
        installConfigured(finalHost, prepare = true)
        installBlobs(finalHost)
        take(storage(finalHost).loadC(started.instanceIdentity)).get shouldBe advanced
        finalHost.subsystem.executeOperationResponseInContext(recoveryRequest(recoveryIdentity.get), actor) shouldBe
          a[Consequence.Failure[?]]
        finalHost.subsystem.executeOperationResponseInContext(Request.of(component = component.name,
          service = "GoalPhaseService", operation = GoalPhaseReviewRecoveryServiceOperation.preparationOperationName,
          arguments = List(Argument("arg1", GoalPhaseReviewRecoveryServiceOperation.encodePreparationCommand(recoveryIdentity.get)))), actor) shouldBe
          a[Consequence.Failure[?]]
        blobReads shouldBe beforePreparationReads
        unexpected shouldBe 0
      } finally take(finalHost.shutdownC())
    }
    }

    "register through the current managed call and retain equal retry without replacing a row" in {
      Given("a host with one captured Create grant, admitted Start and real configured-local ingress")
      val path = Files.createTempDirectory("goal-managed-registration-write-").resolve("host.sqlite")
      val registrations = collection("hostsubmissions")
      val grant = take(org.goldenport.cncf.datastore.ComponentRecordStorageDeclaration.createC(Vector(
        org.goldenport.cncf.datastore.ComponentRecordStorageDeclaration.CreateGrant(component, "workflow", registrations))))
      def declaredHost() = take(SubsystemEventStoreBootstrap.createC(policy, declaration,
        ScopeContext(ScopeKind.Subsystem, "managed-goal", None, context.observability),
        ResolvedConfiguration(HostConfiguration(Map(
          "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
          "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)) ++ hostRecordConfiguration),
          ConfigurationTrace.empty), None, org.goldenport.cncf.path.AliasResolver.empty, grant))
      val command = GoalPhaseStartCommand(GoalPhaseStartInput("phase-2", "repository"),
        "registration-selection", "local-participant", "registration-start-key")
      val selected = ProfileStartAdmission.Selection(command.selectionIdentity,
        StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"), definition.workflowIdentity,
        definition.workflowRevision, command.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
        command.idempotencyKey, None, Some(GoalPhaseStartCommandAdapter.selectedInput(command.input)))
      val selection = new ProfileStartAdmission.CallScopedSelectionPort {
        def loadForCallC(identity: String, core: ActionCall.Core) =
          Consequence.success(Option.when(identity == selected.selectionId)(selected))
      }
      val declared = GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations)
      val original = GoalPhaseSubmissionRegistration.Request("registration-1", input.submissionIdentity,
        "registration-key", command)
      var submitted = original
      var maskedReadback: Option[String] = None
      var maskedCreates = 0
      var uncertainRows = Vector.empty[(String, WorkflowHandle)]
      var observed: Option[Consequence[GoalPhaseSubmissionRegistration.Record]] = None
      var escaped: Option[ActionCall.Core] = None
      var unexpected = 0
      val probe = new GoalPhaseStepCloseServiceOperation.CallScopedInvocationPort {
        def resolveForCallC(identity: String, core: ActionCall.Core): Consequence[WorkflowHandle] = {
          escaped = Some(core)
          observed = Some(maskedReadback match {
            case None => GoalPhaseManagedRegistration.registerForCallC(submitted, core, declared, selection, registrations)
            case Some(mode) => for {
              _ <- GoalPhaseParticipantAdmission.admitC(submitted.start.participantIdentity, core.executionContext)
              capability <- org.goldenport.cncf.datastore.ComponentRecordStorage.bindForCallC(
                core, "workflow", registrations, DataStore.StringEntryId(submitted.submissionIdentity))
              retained <- GoalPhaseManagedStartAssembly.loadRetainedForCallC(submitted.start, core, declared, selection)
              expected <- GoalPhaseSubmissionRegistration.prepareC(submitted, retained)
              result <- GoalPhaseRegistrationRetention.retainC(expected, new GoalPhaseRegistrationRetention.Port {
                private var created = false
                def createC(record: org.goldenport.record.Record) = capability.createC(record).map { _ =>
                  created = true
                  maskedCreates += 1
                }
                def loadC() = if (!created) capability.loadC() else mode match {
                  case "missing" => Consequence.success(None)
                  case "failed" => Consequence.stateConflict("test-only post-Create observation failure")
                  case "divergent" => GoalPhaseSubmissionRegistration.prepareC(
                    submitted.copy(idempotencyIdentity = "different-observed-key"), retained).flatMap { other =>
                    GoalPhaseSubmissionRegistration.encodeStorageC(other).map { raw => Some(
                      org.goldenport.record.Record.data(GoalPhaseSubmissionRegistration.storageField -> raw)) }
                  }
                }
              })
            } yield result
          })
          Consequence.stateConflict("test-only registration probe stops before review issuance")
        }
      }
      val actor = _registration_context()
      val first = declaredHost()
      val handle = try {
        installPath(first, selection, probe, () => unexpected += 1)
        val started = take(first.subsystem.executeOperationResponseInContext(startRequest(command), actor)) match {
          case OperationResponse.Scalar(wire: String) => take(WorkflowWaitJsonV1.decodeC(wire)).handle
          case other => fail(s"Expected Start WAIT: $other")
        }
        def run(): Consequence[GoalPhaseSubmissionRegistration.Record] = {
          observed = None
          val request = Request.of(component = component.name, service = "GoalPhaseService", operation = "requestStepClose",
            arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(input))))
          first.subsystem.executeOperationResponseInContext(request, actor) shouldBe a[Consequence.Failure[?]]
          observed.get
        }
        When("the current managed call creates the closed row and repeats the exact typed request")
        val registered = take(run())
        val repeated = take(run())
        Then("readback and retry retain the same original handle without advancing Workflow")
        registered.request shouldBe original
        repeated.request shouldBe registered.request
        repeated.originalStart shouldBe registered.originalStart
        registered.originalStart.handle shouldBe started
        take(storage(first).loadC(started.instanceIdentity)).get.history.size shouldBe 1
        When("real SQLite Create succeeds but its immediate observation is privately masked")
        Vector("missing", "failed", "divergent").foreach { mode =>
          submitted = original.copy(submissionIdentity = s"uncertain-$mode")
          maskedReadback = Some(mode)
          run() match {
            case Consequence.Failure(conclusion) =>
              conclusion.observation.cause.descriptor.facets.collect {
                case org.goldenport.observation.Descriptor.Facet.Reason(name) => name
              } should contain("transaction-indeterminate")
            case other => fail(s"Masked observation published a receipt: $other")
          }
          maskedReadback = None
          val recovered = take(run())
          recovered.request shouldBe submitted
          recovered.originalStart.handle shouldBe started
          uncertainRows = uncertainRows :+ (submitted.submissionIdentity -> started)
        }
        Then("no uncertain attempt publishes success, while ordinary retries recover the actual inserted rows")
        maskedCreates shouldBe 3
        take(storage(first).loadC(started.instanceIdentity)).get.history.size shouldBe 1
        submitted = original
        When("the same identity is reused with different input or a foreign submitting actor")
        submitted = original.copy(idempotencyIdentity = "foreign-key")
        val conflict = run()
        submitted = original.copy(submissionIdentity = "unauthorized-submission",
          start = command.copy(participantIdentity = "foreign"))
        val unauthorized = run()
        Then("both are denied and the admitted record remains unchanged")
        conflict shouldBe a[Consequence.Failure[?]]
        unauthorized shouldBe a[Consequence.Failure[?]]
        submitted = original
        take(run()).request shouldBe original
        GoalPhaseManagedRegistration.registerForCallC(original, escaped.get, declared, selection, registrations) shouldBe
          a[Consequence.Failure[?]]
        unexpected shouldBe 0
        started
      } finally take(first.shutdownC())
      val reopened = declaredHost()
      try {
        val reader = new GoalPhaseSubmissionRegistration.DataStorePort("workflow", registrations)
        var recovered: Option[WorkflowHandle] = None
        val readprobe = new GoalPhaseStepCloseServiceOperation.CallScopedInvocationPort {
          def resolveForCallC(identity: String, core: ActionCall.Core) =
            reader.resolveForCallC(identity, core).flatMap { value =>
              recovered = Some(value)
              Consequence.stateConflict("test-only readback stops before review issuance")
            }
        }
        installPath(reopened, selection, readprobe, () => unexpected += 1)
        When("another canonical host reopens the database without installing any registration")
        val request = Request.of(component = component.name, service = "GoalPhaseService", operation = "requestStepClose",
          arguments = List(Argument("arg1", GoalPhaseStepCloseJson.encode(input))))
        reopened.subsystem.executeOperationResponseInContext(request, actor) shouldBe a[Consequence.Failure[?]]
        Then("the committed registration resolves the original handle across pool shutdown and reopen")
        recovered shouldBe Some(handle)
        When("the reopened host loads each row whose immediate Create readback was masked")
        uncertainRows.foreach { case (identity, expected) =>
          recovered = None
          reopened.subsystem.executeOperationResponseInContext(Request.of(component = component.name,
            service = "GoalPhaseService", operation = "requestStepClose", arguments = List(Argument("arg1",
              GoalPhaseStepCloseJson.encode(input.copy(submissionIdentity = identity))))), actor) shouldBe
            a[Consequence.Failure[?]]
          recovered shouldBe Some(expected)
        }
        Then("the actual SQLite rows survive managed pool shutdown without replacement or compensation")
        recovered = None
        reopened.subsystem.executeOperationResponseInContext(Request.of(component = component.name,
          service = "GoalPhaseService", operation = "requestStepClose", arguments = List(Argument("arg1",
            GoalPhaseStepCloseJson.encode(input.copy(submissionIdentity = "unauthorized-submission"))))), actor) shouldBe
          a[Consequence.Failure[?]]
        recovered shouldBe None
        unexpected shouldBe 0
      } finally take(reopened.shutdownC())
    }

    "recover the closed registration before issuing review after host reopen" in {
      Given("a Start-created instance and a test-only trusted immutable submission registration")
      val path = Files.createTempDirectory("goal-managed-submission-record-").resolve("host.sqlite")
      val associations = collection("hostsubmissions")
      val reader = new GoalPhaseSubmissionRegistration.DataStorePort("workflow", associations)
      val command = GoalPhaseStartCommand(GoalPhaseStartInput("phase-2", "repository"),
        "association-selection", "participant", "association-key")
      val selected = ProfileStartAdmission.Selection(command.selectionIdentity,
        StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"), definition.workflowIdentity,
        definition.workflowRevision, command.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
        command.idempotencyKey, None, Some(GoalPhaseStartCommandAdapter.selectedInput(command.input)))
      val selection = new ProfileStartAdmission.SelectionPort {
        def loadC(identity: String) = Consequence.success(Option.when(identity == selected.selectionId)(selected))
      }
      val callSelection = new ProfileStartAdmission.CallScopedSelectionPort {
        def loadForCallC(identity: String, core: ActionCall.Core) = selection.loadC(identity)
      }
      val registrationRequest = GoalPhaseSubmissionRegistration.Request("registration-reopen",
        input.submissionIdentity, "registration-reopen-key", command)
      val retainedDeclaration = GoalPhaseManagedStartAssembly.Declaration(config, definition, instances, continuations)
      var startWire: Option[String] = None
      var installed = false
      var unexpected = 0
      val installer = new GoalPhaseStepCloseServiceOperation.CallScopedInvocationPort {
        def resolveForCallC(identity: String, capsule: ActionCall.Core) = {
          final class InstallCall(val core: ActionCall.Core) extends org.goldenport.cncf.action.ProcedureActionCall {
            def installC(): Consequence[Unit] = {
              given ExecutionContext = executionContext
              val space = executionContext.dataStoreSpace
              val prior = space.captureBinding()
              try for {
                _ <- bind_component_application_datastore_c("workflow")
                store <- space.dataStore(associations)
                retained <- GoalPhaseManagedStartAssembly.loadRetainedForCallC(command, core,
                  retainedDeclaration, callSelection)
                registration <- GoalPhaseSubmissionRegistration.prepareC(
                  registrationRequest.copy(submissionIdentity = identity), retained)
                raw <- GoalPhaseSubmissionRegistration.encodeStorageC(registration)
                _ <- store.create(associations, DataStore.StringEntryId(identity),
                  org.goldenport.record.Record.data(GoalPhaseSubmissionRegistration.storageField -> raw))
                selections <- space.dataStore(collection("hostselections"))
                selectionRaw <- ProfileSelectionJsonV1.encodeStorageC(selected)
                _ <- selections.create(collection("hostselections"), DataStore.StringEntryId(selected.selectionId),
                  org.goldenport.record.Record.data(ProfileSelectionJsonV1.storageField -> selectionRaw))
              } yield ()
              finally space.restoreBinding(prior)
            }
            def execute() = Consequence.stateConflict("test registration installer has no public Operation")
          }
          if (startWire.isEmpty) Consequence.stateConflict("test host has not recorded Start")
          else new InstallCall(capsule).installC().flatMap { _ =>
            installed = true
            Consequence.stateConflict("test installation stops before review issuance")
          }
        }
      }
      def scalar(response: OperationResponse): String = response match {
        case OperationResponse.Scalar(value: String) => value
        case other => fail(s"expected Workflow JSON: $other")
      }
      def close(identity: String) = Request.of(component = component.name, service = "GoalPhaseService",
        operation = "requestStepClose", arguments = List(Argument("arg1",
          GoalPhaseStepCloseJson.encode(input.copy(submissionIdentity = identity)))))
      val first = host(path)
      val handle = try {
        installPath(first, selection, installer, () => unexpected += 1)
        startWire = Some(scalar(take(first.subsystem.executeOperationResponse(startRequest(command)))))
        val started = take(WorkflowWaitJsonV1.decodeC(startWire.get)).handle
        When("the trusted test host records the closed registration but deliberately issues no review")
        first.subsystem.executeOperationResponse(close(input.submissionIdentity)) shouldBe a[Consequence.Failure[?]]
        installed shouldBe true
        Then("the original instance remains at Start until a later review request")
        take(storage(first).loadC(started.instanceIdentity)).get.history.size shouldBe 1
        started
      } finally take(first.shutdownC())
      val expectedStartWire = startWire.get
      startWire = None
      val reopened = host(path)
      try {
        installPath(reopened, new ProfileStartAdmission.SelectionPort {
          def loadC(identity: String) = { unexpected += 1; Consequence.stateConflict("No fixture selection may run") }
        }, new GoalPhaseStepCloseServiceOperation.InvocationPort {
          def resolveC(identity: String) = { unexpected += 1; Consequence.stateConflict("No fixture registration may run") }
        }, () => unexpected += 1, hostRecordReaders = true)
        When("the post-resolution Factory derives all record readers solely from the host declaration")
        scalar(take(reopened.subsystem.executeOperationResponse(startRequest(command)))) shouldBe expectedStartWire
        reader.resolveC(input.submissionIdentity) shouldBe a[Consequence.Failure[?]]
        reader.resolveForCallC(input.submissionIdentity, null) shouldBe a[Consequence.Failure[?]]
        reopened.subsystem.executeOperationResponse(close("missing")) shouldBe a[Consequence.Failure[?]]
        When("a new host resolves submission from only the complete managed stored registration")
        val work = take(WorkflowWorkOrderJsonV1.decodeC(scalar(take(
          reopened.subsystem.executeOperationResponse(close(input.submissionIdentity)))), GoalPhaseStepCloseJson.payloadCodec))
        Then("review is issued on the exact Start-created instance without a conversation association Map")
        work.handle shouldBe handle
        take(storage(reopened).loadC(handle.instanceIdentity)).get.history.size shouldBe 2
        unexpected shouldBe 0
      } finally take(reopened.shutdownC())
    }

    "start only from host human selection and recover identical Start without resetting history" in {
      val path = Files.createTempDirectory("goal-managed-start-").resolve("host.sqlite")
      val command = GoalPhaseStartCommand(GoalPhaseStartInput("phase-2", "repository"),
        "selection-start", "participant", "managed-start-key")
      val authorized = ProfileStartAdmission.Selection(command.selectionIdentity,
        StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"), definition.workflowIdentity,
        definition.workflowRevision, command.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman,
        command.idempotencyKey, None, Some(GoalPhaseStartCommandAdapter.selectedInput(command.input)))
      var recorded: Option[ProfileStartAdmission.Selection] = None
      val selection = new ProfileStartAdmission.SelectionPort {
        def loadC(identity: String) = Consequence.success(recorded.filter(_.selectionId == identity))
      }
      def start(current: SubsystemEventStoreBootstrap) = take(current.subsystem.executeOperationResponse(startRequest(command)))
      def wait(response: OperationResponse) = response match {
        case OperationResponse.Scalar(wire: String) => take(WorkflowWaitJsonV1.decodeC(wire))
        case other => fail(s"expected Start WAIT: $other")
      }
      val first = host(path)
      val started = try {
        installStart(first, selection)
        first.subsystem.executeOperationResponse(startRequest(command)) shouldBe a[Consequence.Failure[?]]
        recorded = Some(authorized.copy(origin = ProfileStartAdmission.SelectionOrigin.Skill))
        first.subsystem.executeOperationResponse(startRequest(command)) shouldBe a[Consequence.Failure[?]]
        recorded = Some(authorized)
        val response = start(first)
        start(first) shouldBe response
        val instance = take(storage(first).loadC(wait(response).handle.instanceIdentity)).get
        instance.lifecycle shouldBe Lifecycle.Active
        instance.revision shouldBe InstanceRevision(1L)
        instance.history.size shouldBe 1
        val changed = command.copy(input = command.input.copy(phaseIdentity = "foreign-phase"))
        first.subsystem.executeOperationResponse(startRequest(changed)) shouldBe a[Consequence.Failure[?]]
        take(storage(first).loadC(instance.identity)) shouldBe Some(instance)
        response -> instance
      } finally take(first.shutdownC())
      val reopened = host(path)
      try {
        installStart(reopened, selection)
        start(reopened) shouldBe started._1
        take(storage(reopened).loadC(started._2.identity)) shouldBe Some(started._2)
        recorded = None
        reopened.subsystem.executeOperationResponse(startRequest(command)) shouldBe a[Consequence.Failure[?]]
        take(storage(reopened).loadC(started._2.identity)) shouldBe Some(started._2)
      } finally take(reopened.shutdownC())
    }

    "reject incomplete review composition and a foreign host declaration" in {
      val current = host(Files.createTempDirectory("goal-managed-assembly-admission-").resolve("host.sqlite"))
      try {
        val params = ComponentCreate(current.subsystem, ComponentOrigin.Builtin)
        val declared = GoalPhaseManagedReviewAssembly.Declaration(config, definition, instances, continuations)
        var eventsCalled = false
        val ports = GoalPhaseManagedReviewAssembly.Ports(artifact, () => Instant.EPOCH,
          (_, _) => { eventsCalled = true; Consequence.success(Vector.empty) })
        GoalPhaseManagedReviewAssembly.bindC(params, declared, null) shouldBe a[Consequence.Failure[?]]
        GoalPhaseManagedReviewAssembly.bindC(params, declared, ports.copy(events = null)) shouldBe a[Consequence.Failure[?]]
        GoalPhaseManagedReviewAssembly.bindC(params, declared.copy(instances = collection("foreign")), ports) shouldBe
          a[Consequence.Failure[?]]
        take(GoalPhaseManagedReviewAssembly.bindC(params, declared, ports)).specification.name shouldBe "submitStepReview"
        eventsCalled shouldBe false
        take(storage(current).loadC(active.identity)) shouldBe None
      } finally take(current.shutdownC())
    }

    "complete recovered review through Subsystem dispatch and reject replay after another reopen" in {
      val path = Files.createTempDirectory("goal-managed-review-dispatch-").resolve("host.sqlite")
      val first = host(path)
      val work = try {
        seed(first)
        val wire = take(invoke(first)) match {
          case OperationResponse.Scalar(value: String) => value
          case other => fail(s"expected WorkOrder JSON: $other")
        }
        take(WorkflowWorkOrderJsonV1.decodeC(wire, GoalPhaseStepCloseJson.payloadCodec))
      } finally take(first.shutdownC())
      val request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
      val candidate = take(StepCloseRequestAdapter.submissionC(input)).candidate.reference
      val evidence = StepReviewEvidence("managed-review-result", candidate.revision,
        "2026-09-30T23:59:00Z", "full-step", "reviewed", "receipt")
      val submitted = ContinuationResult(work.handle, request.runId, request.continuationId,
        request.expectedRevision, request.context.snapshot,
        TypedValue("ReviewStepResult", ReviewStepResult("accept", "review passed", Vector(evidence))),
        ContextReference("managed-review-result", candidate.revision), Vector.empty, ExecutionEvidence(Vector.empty))
      val reopened = host(path)
      val reviewed = try {
        installReview(reopened)
        val suspended = take(storage(reopened).loadC(active.identity)).get
        val mismatched = submitted.copy(result = TypedValue("ReviewStepResult",
          submitted.result.value.copy(reviewEvidence = Vector(evidence.copy(reviewedRevision = "c" * 64)))))
        reopened.subsystem.executeOperationResponse(reviewRequest(mismatched)) shouldBe a[Consequence.Failure[?]]
        take(storage(reopened).loadC(active.identity)) shouldBe Some(suspended)
        val wire = take(reopened.subsystem.executeOperationResponse(reviewRequest(submitted))) match {
          case OperationResponse.Scalar(value: String) => value
          case other => fail(s"expected WAIT JSON: $other")
        }
        take(WorkflowWaitJsonV1.decodeC(wire)).current shouldBe a[WorkflowContinuation.Wait]
        val record = take(storage(reopened).loadC(active.identity)).get
        record.lifecycle shouldBe Lifecycle.Active
        record.suspension shouldBe None
        record.revision shouldBe InstanceRevision(3L)
        record.history.size shouldBe 3
        record.causation shouldBe CausationReference(s"review-result:managed-review-result@${candidate.revision}")
        reopened.subsystem.executeOperationResponse(reviewRequest(submitted)) shouldBe a[Consequence.Failure[?]]
        take(storage(reopened).loadC(active.identity)) shouldBe Some(record)
        record
      } finally take(reopened.shutdownC())
      val third = host(path)
      try {
        installReview(third)
        take(storage(third).loadC(active.identity)) shouldBe Some(reviewed)
        take(issued(third).loadC(request.continuationId)) shouldBe Some(work)
        third.subsystem.executeOperationResponse(reviewRequest(submitted)) shouldBe a[Consequence.Failure[?]]
        take(storage(third).loadC(active.identity)) shouldBe Some(reviewed)
      } finally take(third.shutdownC())
    }

    "return only atomically retained work and recover it after host reopen without legacy claims" in {
      val path = Files.createTempDirectory("goal-managed-operation-").resolve("host.sqlite")
      val first = host(path)
      val work = try {
        take(first.initializeCursorC())
        seed(first)
        val wire = take(invoke(first)) match {
          case OperationResponse.Scalar(value: String) => value
          case other => fail(s"expected WorkOrder JSON: $other")
        }
        val value = take(WorkflowWorkOrderJsonV1.decodeC(wire, GoalPhaseStepCloseJson.payloadCodec))
        val request = value.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
        take(issued(first).loadC(request.continuationId)) shouldBe Some(value)
        take(storage(first).loadC(active.identity)).get.suspension.get.continuationIdentity.value shouldBe
          request.continuationId.value
        invoke(first) shouldBe a[Consequence.Failure[?]]
        value
      } finally take(first.shutdownC())
      val reopened = host(path)
      try {
        val request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[StepCloseRequest]].request
        take(issued(reopened).loadC(request.continuationId)) shouldBe Some(work)
        request.completionOperation shouldBe StateMachineOperationIdentity("GoalPhaseService", "submitStepReview")
        request.input.map(_.value) shouldBe Some(input)
        take(storage(reopened).loadC(active.identity)).get.history.size shouldBe 2
      } finally take(reopened.shutdownC())
    }

    "persist an event-free suspension without requiring or initializing the cursor" in {
      val current = host(Files.createTempDirectory("goal-managed-missing-cursor-").resolve("host.sqlite"))
      try {
        seed(current)
        val wire = take(invoke(current)) match {
          case OperationResponse.Scalar(value: String) => value
          case other => fail(s"expected WorkOrder JSON: $other")
        }
        val work = take(WorkflowWorkOrderJsonV1.decodeC(wire, GoalPhaseStepCloseJson.payloadCodec))
        val identity = org.goldenport.cncf.workflow.ContinuationIdentity(
          s"${active.identity.value}:2:${input.submissionIdentity}:review")
        take(issued(current).loadC(identity)) shouldBe Some(work)
        take(storage(current).loadC(active.identity)).get.suspension.get.continuationIdentity.value shouldBe identity.value
        take(current.initializeCursorC()) // Empty-event selection did not create/reset the cursor.
      } finally take(current.shutdownC())
    }
  }
}
