package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import java.nio.file.{Files, Path}
import java.time.{Clock, Instant, ZoneOffset}
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.{ComponentRecordStorageDeclaration, DataStore}
import org.goldenport.cncf.event.*
import org.goldenport.cncf.security.IngressSecurityResolver
import org.goldenport.cncf.subsystem.*
import org.goldenport.cncf.testutil.{ExecutionContextTestFixture, NativeHumanManagedHostObservation, RuntimeBindingAdmissionFixture}
import org.goldenport.cncf.workflow.{WorkflowWaitJsonV1, WorkflowWorkOrderJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseAdvanceCommand, SplitPhasePartitionContextReference}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture.{configuration, catalog, facts, source, proposal}
import SplitPhaseExplicitPlanningInputJsonV1 as J
import SplitPhaseTypedStartHumanVerification as H
import SplitPhaseExplicitPartitionSemantic as S

/** Real public Start -> context registration -> public advance, rather than a
  * seeded native Start or a probe masquerading as advance. Human receipts and
  * input installation are explicitly fixture-only; not production ingress proof.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
final class SplitPhaseManagedWorkflowAssemblySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "workflowassembly", name))
  private def target(p: J.Purpose) = collection(p.toString.toLowerCase)
  private val declaration = SplitPhaseManagedStartAssembly.Declaration(configuration, collection("instances"), collection("continuations"))
  private val ip = SplitPhasePlanningInputReadPolicy.prefix
  private val cp = SplitPhasePartitionContextReadPolicy.prefix
  private val sp = SplitPhaseHostSelectionReadPolicy.prefix
  private val tp = SplitPhaseTypedStartReadPolicy.prefix
  private val ep = SplitPhasePartitionEnrichmentStorage.prefix
  private val now = Instant.parse("2026-10-06T13:30:00Z")
  private val compositionClock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)
  private val invocationClock = Clock.fixed(now, ZoneOffset.UTC)
  private val command = SplitPhaseTypedStartFixture.command.copy(selectionIdentity = "connected-start", idempotencyKey = "connected-key")
  private val allocation = SplitPhaseTypedStartFixture.record.copy(
    reference = SplitPhaseExplicitStartPreparation.Reference(command.selectionIdentity, "1"),
    instanceIdentity = InstanceIdentity("connected-instance"), command = command,
    selection = SplitPhaseTypedStartFixture.selectionFor(command))
  private val values = Map[String, ConfigurationValue](
    ip + "store-name" -> ConfigurationValue.StringValue("workflow"),
    cp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    cp + "record-collection" -> ConfigurationValue.StringValue(collection("contexts").print),
    sp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    sp + "selection-collection" -> ConfigurationValue.StringValue(collection("selections").print),
    tp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    tp + "record-collection" -> ConfigurationValue.StringValue(collection("starts").print),
    ep + "store-name" -> ConfigurationValue.StringValue("workflow"),
    ep + "record-collection" -> ConfigurationValue.StringValue(collection("publications").print),
    SplitPhaseManagedTypedStartAssembly.revisionKey -> ConfigurationValue.StringValue("1")) ++
    J.Purpose.values.map(p => (ip + p.key) -> ConfigurationValue.StringValue(target(p).print)) ++
    SplitPhaseCandidateAllocationFixture.values ++ SplitPhasePartitionReplyFixture.values
  private val inputRows = Vector(
    (J.Purpose.Source, source.reference.identity, take(J.encodeSourceC(source))),
    (J.Purpose.Inventory, facts.proposal.inventory.reference.identity, take(J.encodeInventoryC(facts.proposal.inventory))),
    (J.Purpose.Proposal, proposal.reference.identity, take(J.encodeProposalC(proposal))),
    (J.Purpose.Boundary, facts.boundary.boundary.reference.identity, take(J.encodeBoundaryC(facts.boundary.boundary))),
    (J.Purpose.Ownership, facts.boundary.ownership.reference.identity, take(J.encodeOwnershipC(facts.boundary.ownership))),
    (J.Purpose.Handoffs, facts.boundary.handoffs.reference.identity, take(J.encodeHandoffsC(facts.boundary.handoffs))),
    (J.Purpose.Estimates, facts.partition.estimates.reference.identity, take(J.encodeEstimatesC(facts.partition.estimates))),
    (J.Purpose.PartitionPolicy, facts.partition.policy.reference.identity, take(J.encodePartitionPolicyC(facts.partition.policy))),
    (J.Purpose.Planning, facts.planning.reference.identity, take(J.encodePlanningC(facts.planning))))

  private final class Human(mode: String) extends H.Verifier {
    val issuerIdentity = "private-connected-start-verifier"
    var calls = 0
    def verifyForCallC(expected: H.Binding, core: ActionCall.Core): Consequence[Option[H.Receipt]] = {
      calls += 1
      val receipt = H.Receipt(H.EvidenceReference("private-connected-human", "1"), issuerIdentity,
        "private-human", H.purpose, expected, now.minusSeconds(1),
        if (mode == "expired") now else now.plusSeconds(60))
      Consequence.success(if (mode == "absent") None else Some(receipt))
    }
  }
  // Installing answer routing does not invent an answer or Start permission.
  private val answers = new SplitPhaseHumanAnswerVerification.Verifier {
    val issuerIdentity = "private-no-answer-verifier"
    def verifyForCallC(reference: SplitPhaseHumanAnswerVerification.EvidenceReference,
      expected: SplitPhaseHumanAnswerVerification.Binding, core: ActionCall.Core) = Consequence.success(None)
  }
  private final class Host(path: Path, human: Human) extends AutoCloseable {
    private val context = ExecutionContext.create()
    private val configured = ResolvedConfiguration(HostConfiguration(values ++ Map(
      "textus.subsystem.user-mode" -> ConfigurationValue.StringValue("standalone"),
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString))), ConfigurationTrace.empty)
    private val policy = take(EventPersistencePolicy.createC("connected-service-v1", Vector(
      EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
    private val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"), collection("indexes"),
      Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId, "workflow", declaration.instances, declaration.continuations))))
    private val grants = take(ComponentRecordStorageDeclaration.createC(Vector(
      collection("contexts"), target(J.Purpose.Estimates), target(J.Purpose.Handoffs), collection("publications"),
      target(J.Purpose.Planning), target(J.Purpose.Proposal), target(J.Purpose.Ownership))
      .map(c => ComponentRecordStorageDeclaration.CreateGrant(SmWorkflowComponent.componentId, "workflow", c))))
    private val bootstrap = take(SubsystemEventStoreBootstrap.createC(policy, storage,
      ScopeContext(ScopeKind.Subsystem, "connected-workflow", None, context.observability), configured, None,
      org.goldenport.cncf.path.AliasResolver.empty, grants))
    val owner = RuntimeBindingAdmissionFixture.admit(bootstrap.subsystem)
    private val instances = CallScopedWorkflowInstances.managed(configuration, catalog.definition, declaration.instances, declaration.continuations)
    private var fixtureTask: Fixture => Consequence[Unit] = _ => Consequence.unit
    private final class Fixture(val core: ActionCall.Core) extends ProcedureActionCall {
      def instanceC() = instances.loadForCallC(configuration, allocation.instanceIdentity, core)
      def installC(): Consequence[Unit] = {
        val rows = inputRows.map((p, id, raw) => (target(p), id, Record.data(J.storageField -> raw))) ++ Vector(
          (collection("starts"), allocation.reference.identity, Record.data(SplitPhaseTypedStartJsonV1.storageField ->
            take(SplitPhaseTypedStartJsonV1.encodeC(allocation)))),
          (collection("selections"), command.selectionIdentity, Record.data(ProfileSelectionJsonV1.storageField ->
            take(ProfileSelectionJsonV1.encodeStorageC(allocation.selection)))))
        rows.foldLeft(Consequence.unit) { case (prior, (c, id, row)) => prior.flatMap { _ =>
          given ExecutionContext = executionContext
          val space = executionContext.dataStoreSpace
          val binding = space.captureBinding()
          try bind_component_application_datastore_c("workflow").flatMap(_ => space.dataStore(c)
            .flatMap(_.create(c, DataStore.StringEntryId(id), row)))
          finally space.restoreBinding(binding)
        }}
      }
      def registerContextC(): Consequence[Unit] = instanceC().flatMap {
        case Some(current) =>
          val selected = SplitPhasePartitionContextFixture.record.copy(owner = SplitPhasePartitionContextFixture.record.owner.copy(
            start = allocation.reference, handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current)),
            instanceRevision = current.revision))
          SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, declaration, catalog)
            .flatMap(_.registerForCallC(selected, core)).map(_ => ())
        case None => Consequence.stateConflict("Private context setup requires the actual public Start")
      }
      def execute(): Consequence[OperationResponse] = fixtureTask(this).map(_ => OperationResponse.Scalar("private-fixture"))
    }
    private val fixtureOperation = new spec.OperationDefinition {
      val specification = spec.OperationDefinition.Specification("privateFixture", WorkflowJsonCommandRequest.definition, spec.ResponseDefinition.void)
      def createOperationRequest(r: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
        val request = r
        override def commandExecutionMode = CommandExecutionMode.Sync
        def createCall(c: ActionCall.Core): ActionCall = new Fixture(c)
      })
    }
    private val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty, (creation, _, resolved) => {
      given ExecutionContext = ExecutionContextTestFixture.withClock(resolved, compositionClock)
      SplitPhaseManagedServiceAssembly.bindHostWorkflowC(creation, declaration, catalog, human, answers)
        .map(service => spec.ServiceDefinition(service.name, spec.OperationDefinitionGroup(
          NonEmptyVector.fromVectorUnsafe(service.operations.operations.toVector :+ fixtureOperation))))
    })
    val component = take(factory.primaryFactory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
    owner.add(component)
    owner.withDescriptor(GenericSubsystemDescriptor(path = Path.of("<connected-workflow-spec>"), subsystemName = "connected-workflow",
      componentBindings = Vector(GenericSubsystemComponentBinding(component.name)), security = Some(GenericSubsystemSecurityBinding(
        authentication = Some(GenericSubsystemAuthenticationBinding(convention = Some("enabled"), fallbackPrivilege = Some("disabled"),
          localSubject = Some(GenericSubsystemLocalSubjectBinding(id = "participant", roles = Vector("user"), capabilities = Vector("user"),
            attributes = Map.empty, securityLevel = Some("user")))))))))
    private val actor = ExecutionContextTestFixture.withClock(take(IngressSecurityResolver.resolve(
      SubsystemExecutionProfile.Fixed, component.logic.executionContext(), Map.empty)).executionContext, invocationClock)
    def invoke(name: String, wire: String): Consequence[String] = owner.executeOperationResponseInContext(
      Request.of(component = component.name, service = "SplitPhaseService", operation = name,
        arguments = List(Argument("arg1", wire))), actor).flatMap {
      case OperationResponse.Scalar(value: String) => Consequence.success(value)
      case _ => Consequence.stateConflict("Expected an actual Workflow JSON response")
    }
    def startC() = invoke("startSplitPhase", SplitPhaseStartServiceOperation.encodeCommand(command))
    def advanceC(): Consequence[String] = invoke("advanceSplitPhase", take(SplitPhaseAdvanceCommandJsonV1.encodeC(
      SplitPhaseAdvanceCommand(allocation.instanceIdentity.value, SplitPhasePartitionContextReference("partition-context", "1")))))
    def cliAdvanceC() = new CncfRuntime().executeCommandResponse(owner, Array(s"${component.name}/SplitPhaseService/advanceSplitPhase",
      "--json", take(SplitPhaseAdvanceCommandJsonV1.encodeC(SplitPhaseAdvanceCommand(allocation.instanceIdentity.value,
        SplitPhasePartitionContextReference("partition-context", "1")))))).map(_.print)
    private def fixture(task: Fixture => Consequence[Unit]): Unit = {
      fixtureTask = task
      take(invoke("privateFixture", "private-test-inputs"))
    }
    def install(): Unit = fixture(_.installC())
    def registerContext(): Unit = fixture(_.registerContextC())
    def current(): Option[InstanceRecord] = {
      var result = Option.empty[InstanceRecord]
      fixture(_.instanceC().map(value => { result = value; () }))
      result
    }
    def poolCounts = NativeHumanManagedHostObservation.counts(bootstrap)
    def close(): Unit = take(bootstrap.shutdownC())
  }
  private def path(): Path = Files.createTempDirectory(Files.createDirectories(
    Path.of("target/sm-workflow/work/split-workflow-assembly").toAbsolutePath.normalize), "episode-").resolve("host.sqlite")

  "The complete managed SplitPhase service" should {
    "connect genuine public Start to owning advance and recover the assigned work after reopening" in {
      Given("one host-owned SQLite pool and independently supplied fixture Start evidence")
      val database = path()
      val human = new Human("accepted")
      var originalStart = ""
      var assigned = ""
      val first = new Host(database, human)
      try {
        first.install()
        first.current() shouldBe None
        When("public Start runs with a current clock different from its composition clock")
        originalStart = take(first.startC())
        Then("the genuine Start creates the original revision using current-call Human evidence")
        take(WorkflowWaitJsonV1.decodeC(originalStart)).handle.instanceIdentity.value shouldBe allocation.instanceIdentity.value
        first.current().get.revision shouldBe InstanceRevision(1)
        human.calls shouldBe 1
        first.poolCounts shouldBe (1, 0)
        Given("a context registered against that actual public Start")
        first.registerContext()
        When("the installed owning advance processes the selected complete facts")
        assigned = take(first.advanceC())
        Then("only the unknown duration and semantic boundary are assigned, not deterministic packing")
        val work = take(WorkflowWorkOrderJsonV1.decodeC(assigned, SplitPhaseExplicitPartitionSemanticJson.requestCodec))
        val input = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
        input.items.map(_.identity.value) shouldBe Vector("item-1")
        input.boundaries.map(_.edge.afterItem.value) shouldBe Vector("item-0")
        first.current().get.suspension.nonEmpty shouldBe true
      } finally first.close()
      Given("the same physical database reopened without reinstalling inputs or seeding a native Start")
      val reopened = new Host(database, human)
      try {
        val before = reopened.current().get
        When("CLI advance and repeated public Start observe the retained original execution")
        val retained = take(reopened.cliAdvanceC())
        val repeatedStart = take(reopened.startC())
        Then("the exact assigned task and original Start response survive without changing later history")
        retained shouldBe assigned
        repeatedStart shouldBe originalStart
        SplitPhaseDefinitionBinding.sameRecord(reopened.current().get, before) shouldBe true
        reopened.poolCounts shouldBe (1, 0)
      } finally reopened.close()
    }

    "reject absent and expired Human evidence without a native Start or advance fallback" in {
      Vector("absent", "expired").foreach { mode =>
        Given(s"a complete selected allocation and authenticated participant, but $mode independent evidence")
        val human = new Human(mode)
        val host = new Host(path(), human)
        try {
          host.install()
          When("the public Start and owning advance are invoked")
          val start = host.startC()
          val advance = host.advanceC()
          Then("neither selection nor login creates consent, a native Start, work or Terminal")
          start.isSuccess shouldBe false
          advance.isSuccess shouldBe false
          host.current() shouldBe None
          human.calls shouldBe 1
          host.poolCounts shouldBe (1, 0)
        } finally host.close()
      }
    }
  }
}
