package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Json, parser}
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.{ComponentRecordStorageDeclaration, DataStore}
import org.goldenport.cncf.event.*
import org.goldenport.cncf.security.IngressSecurityResolver
import org.goldenport.cncf.subsystem.*
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.WorkflowMechanicalTransition as M
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhasePartitionContextReference
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningCheckpointPublication as U, SplitPhasePlanningCheckpointJsonV1 as J,
  SplitPhasePlanningCheckpointObservation as O, SplitPhaseManagedPlanningCheckpoint as Managed,
  SplitPhaseExplicitPlanningInputJsonV1 as F, SplitPhaseTypedStartJsonV1 as TJ,
  SplitPhaseExplicitPartitionContextJsonV1 as CJ, SplitPhasePlanningCheckpointDataStorePort as Reads}
import SplitPhasePlanningCheckpointFixture.{f, anchor}
import SplitPhaseObservedCheckpointFixture.*
import SplitPhaseExplicitPartitionFixture.take

/** Partial native checkpoint assembly, not the normal public Workflow factory.
  * The test-only Receiver supplies an already resolved target and preserves
  * actual managed dispatch. It neither decodes an old Command nor proves public
  * transport; that belongs to the three normal Operation suites.
  * Shared transaction, replay and interruption guarantees are not re-specified.
  *
  * @since   Oct.  2, 2026
  * @version Oct. 24, 2026
  */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SplitPhaseManagedPlanningCheckpointSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "managedcheckpoint", name))
  private def target(p: F.Purpose) = collection(p.toString.toLowerCase)
  private val declaration = SplitPhaseManagedStartAssembly.Declaration(f.configuration, collection("instances"), collection("continuations"))
  private val ip = SplitPhasePlanningInputReadPolicy.prefix
  private val cp = SplitPhasePartitionContextReadPolicy.prefix
  private val sp = SplitPhaseInputSelectionReadPolicy.prefix
  private val tp = SplitPhaseTypedStartReadPolicy.prefix
  private val ep = SplitPhasePartitionEnrichmentStorage.prefix
  private val values = Map[String, ConfigurationValue](
    ip + "store-name" -> ConfigurationValue.StringValue("workflow"),
    cp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    cp + "record-collection" -> ConfigurationValue.StringValue(collection("contexts").print),
    sp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    sp + "selection-collection" -> ConfigurationValue.StringValue(collection("selections").print),
    tp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    tp + "record-collection" -> ConfigurationValue.StringValue(collection("starts").print),
    ep + "store-name" -> ConfigurationValue.StringValue("workflow"),
    ep + "record-collection" -> ConfigurationValue.StringValue(collection("publications").print)) ++
    F.Purpose.values.map(p => (ip + p.key) -> ConfigurationValue.StringValue(target(p).print)) ++
    SplitPhaseCandidateAllocationFixture.values
  private def host(v: Map[String, ConfigurationValue]) = ResolvedConfiguration(HostConfiguration(v), ConfigurationTrace.empty)
  private def _earlier_host: ResolvedConfiguration = host(values.updated(ip + F.Purpose.Estimates.key,
    ConfigurationValue.StringValue(collection("unusedconstructionestimates").print)))
  private lazy val _relocated_catalog: SplitPhaseRuntimeCatalog.Catalog = {
    def _relocate_(value: Json): Json = value.asObject match {
      case Some(fields) => Json.fromFields(fields.toVector.map { (name, member) =>
        name -> (if (name == "line") member.asNumber.flatMap(_.toInt)
          .map(line => Json.fromInt(line + 17)).getOrElse(member) else _relocate_(member))
      })
      case None => value.asArray.map(items => Json.fromValues(items.map(_relocate_))).getOrElse(value)
    }
    val workflow = parser.parse(Files.readString(Path.of("target/cozy/statemachine-workflow-abi.json")))
      .fold(error => fail(error.toString), identity)
    val producer = parser.parse(Files.readString(Path.of("target/cozy/candidate-admission-producer-abi.json")))
      .fold(error => fail(error.toString), identity)
    take(SplitPhaseRuntimeCatalog.parseC(_relocate_(workflow).noSpaces, _relocate_(producer).noSpaces))
  }
  private def query(saved: K.Captured) = SplitPhaseAdvanceTarget(saved.before.identity.value,
    SplitPhasePartitionContextReference(saved.record.originalContext.reference.identity, saved.record.originalContext.reference.revision))
  private def _root_c(call: Probe, saved: K.Captured): Consequence[K.Record] = for {
    reads <- Reads.bindC(call.core.component.get.subsystem.get.configuration, declaration, f.catalog)
    selected <- reads.loadMappingForCallC(query(saved), call.core)
    actual <- selected match {
      case Some(value) => Consequence.success(value)
      case None => Consequence.stateConflict("Private observation requires an actual selected native checkpoint")
    }
  } yield actual
  private def originalRows(saved: K.Captured): Vector[(DataStore.CollectionId, String, Record)] = {
    _original_rows(O.Original(saved.record.originalContext, saved.record.originalStart, saved.record.originalContext.facts), saved.record.originalStart)
  }
  private def _original_rows(original: O.Original, start: SplitPhaseTypedStartPreparation.Record): Vector[(DataStore.CollectionId, String, Record)] = {
    val context = original.context; val facts = original.facts
    val rows = Vector(
      (F.Purpose.Source, facts.proposal.source.reference.identity, take(F.encodeSourceC(facts.proposal.source))),
      (F.Purpose.Inventory, facts.proposal.inventory.reference.identity, take(F.encodeInventoryC(facts.proposal.inventory))),
      (F.Purpose.Proposal, facts.proposal.proposal.reference.identity, take(F.encodeProposalC(facts.proposal.proposal))),
      (F.Purpose.Boundary, facts.boundary.boundary.reference.identity, take(F.encodeBoundaryC(facts.boundary.boundary))),
      (F.Purpose.Ownership, facts.boundary.ownership.reference.identity, take(F.encodeOwnershipC(facts.boundary.ownership))),
      (F.Purpose.Handoffs, facts.boundary.handoffs.reference.identity, take(F.encodeHandoffsC(facts.boundary.handoffs))),
      (F.Purpose.Estimates, facts.partition.estimates.reference.identity, take(F.encodeEstimatesC(facts.partition.estimates))),
      (F.Purpose.PartitionPolicy, facts.partition.policy.reference.identity, take(F.encodePartitionPolicyC(facts.partition.policy))),
      (F.Purpose.Planning, facts.planning.reference.identity, take(F.encodePlanningC(facts.planning))))
    rows.map((p, id, raw) => (target(p), id, Record.data(F.storageField -> raw))) ++ Vector(
      (collection("starts"), start.reference.identity, Record.data(TJ.storageField -> take(TJ.encodeC(start)))),
      (collection("contexts"), context.reference.identity, Record.data(CJ.storageField -> take(CJ.encodeC(context)))))
  }

  private final class Probe(val core: ActionCall.Core, task: Probe => Consequence[Unit]) extends ProcedureActionCall {
    private def withStoreC[A](c: DataStore.CollectionId)(run: DataStore => Consequence[A]): Consequence[A] = {
      given ExecutionContext = executionContext
      val space = executionContext.dataStoreSpace; val prior = space.captureBinding()
      try bind_component_application_datastore_c("workflow").flatMap(_ => space.dataStore(c).flatMap(run))
      finally space.restoreBinding(prior)
    }
    def writeC(c: DataStore.CollectionId, id: String, row: Record, update: Boolean = false): Consequence[Unit] = {
      given ExecutionContext = executionContext
      withStoreC(c)(store => if (update) store.update(c, DataStore.StringEntryId(id), row) else store.create(c, DataStore.StringEntryId(id), row))
    }
    def rowC(c: DataStore.CollectionId, id: String): Consequence[Option[Record]] = {
      given ExecutionContext = executionContext
      withStoreC(c)(_.load(c, DataStore.StringEntryId(id)))
    }
    /** Exact test-owned document in a fresh private database, never user data. */
    def deleteFixtureRowC(c: DataStore.CollectionId, id: String): Consequence[Unit] = {
      given ExecutionContext = executionContext
      withStoreC(c)(_.delete(c, DataStore.StringEntryId(id)))
    }
    def execute(): Consequence[OperationResponse] = task(this).map(_ => OperationResponse.Scalar("internal-checkpoint-probe"))
  }

  private def withHost(path: Path, resolvedTarget: Option[SplitPhaseAdvanceTarget] = None)
    (task: (Probe, Managed.Port) => Consequence[Unit]): Unit = {
    val configured = host(values ++ Map("textus.subsystem.user-mode" -> ConfigurationValue.StringValue("standalone"),
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"), "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)))
    val policy = take(EventPersistencePolicy.createC("managed-checkpoint-v1", Vector(
      EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
    val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"), collection("indexes"),
      Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId, "workflow", declaration.instances, declaration.continuations))))
    val targets = take(U.bindTargetsC(configured, declaration))
    val grants = take(ComponentRecordStorageDeclaration.createC(targets.localCollections
      .map(c => ComponentRecordStorageDeclaration.CreateGrant(SmWorkflowComponent.componentId, "workflow", c))))
    val bootstrap = take(SubsystemEventStoreBootstrap.createC(policy, storage,
      ScopeContext(ScopeKind.Subsystem, "managed-checkpoint", None, ExecutionContext.create().observability), configured, None,
      org.goldenport.cncf.path.AliasResolver.empty, grants))
    try {
      val owner = RuntimeBindingAdmissionFixture.admit(bootstrap.subsystem)
      val port = take(Managed.bindC(owner.configuration, declaration, f.catalog))
      val probeName = resolvedTarget.fold(K.advanceOperation.operation)(_ => "managedAdvanceFixture")
      val probe = new spec.OperationDefinition {
        val specification = spec.OperationDefinition.Specification(name = probeName, request = spec.RequestDefinition.empty,
          response = spec.ResponseDefinition.void)
        def createOperationRequest(input: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
          val request = input
          override def commandExecutionMode = CommandExecutionMode.Sync
          def createCall(core: ActionCall.Core): ActionCall = new Probe(core, call => task(call, port))
        })
      }
      val factory = new SmWorkflowConfiguredComponentFactory(Vector.empty, Vector.empty, (creation, parameters, resolved) => {
        val additional = resolvedTarget match {
          case None => Consequence.success(Vector.empty[spec.OperationDefinition])
          case Some(target) => SplitPhaseManagedAdvanceServiceOperation.bindRuntimeC(creation, declaration, f.catalog).flatMap {
            case receiver: NativeWorkflowAdvanceInput.Receiver => Consequence.success(Vector(new spec.OperationDefinition {
              val specification = receiver.specification
              def createOperationRequest(incoming: Request): Consequence[OperationRequest] =
                Consequence.success(new CommandAction {
                  val request = incoming
                  override def commandExecutionMode = CommandExecutionMode.Sync
                  def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
                    val core = c
                    private var _delegate = Option.empty[ActionCall]
                    def execute(): Consequence[OperationResponse] = for {
                      current <- CallScopedWorkflowInstances.managed(declaration.configuration, f.catalog.definition,
                        declaration.instances, declaration.continuations).loadForCallC(declaration.configuration,
                        InstanceIdentity(target.instanceIdentity), core).flatMap(_.map(Consequence.success)
                        .getOrElse(Consequence.stateConflict("Private fixture native current is absent")))
                      operation <- receiver.createAdvanceOperationRequestC(incoming, NativeWorkflowAdvanceInput.Split(target, current))
                      response <- operation match {
                        case action: CommandAction =>
                          val call = action.createCall(core.copy(action = action))
                          _delegate = Some(call)
                          call.execute()
                        case _ => Consequence.stateConflict("Private fixture receiver is not a native command")
                      }
                    } yield response
                    override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
                      val selected = _delegate; _delegate = None
                      selected.map(_.afterCommitResponseC(response)).getOrElse(
                        Consequence.stateConflict("Private fixture receiver was not executed"))
                    }
                  }
                })
            }))
            case _ => Consequence.configurationInvalid("Private fixture requires the actual resolved-target Receiver")
          }
        }
        additional.map(operations => Vector(spec.ServiceDefinition("SplitPhaseService",
          spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(Vector(probe) ++ operations)))))
      }).primaryFactory
      val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
      owner.add(built)
      owner.withDescriptor(GenericSubsystemDescriptor(path = Path.of("<managed-checkpoint-spec>"), subsystemName = "managed-checkpoint",
        componentBindings = Vector(GenericSubsystemComponentBinding(built.name)), security = Some(GenericSubsystemSecurityBinding(
          authentication = Some(GenericSubsystemAuthenticationBinding(convention = Some("enabled"), fallbackPrivilege = Some("disabled"),
            localSubject = Some(GenericSubsystemLocalSubjectBinding(id = "participant", roles = Vector("user"), capabilities = Vector("user"),
              attributes = Map.empty, securityLevel = Some("user")))))))))
      val actor = take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.Fixed, built.logic.executionContext(), Map.empty)).executionContext
      take(owner.executeOperationResponseInContext(Request.of(component = built.name,
        service = "SplitPhaseService", operation = probeName), actor))
    } finally take(bootstrap.shutdownC())
  }
  private def instances = CallScopedWorkflowInstances.managed(f.configuration, f.catalog.definition, declaration.instances, declaration.continuations)
  private def installC(call: Probe, saved: K.Captured): Consequence[Unit] = originalRows(saved)
    .foldLeft(Consequence.unit) { case (prior, (c, id, row)) => prior.flatMap(_ => call.writeC(c, id, row)) }
    .flatMap(_ => instances.createForCallC(f.configuration, f.initial, call.core))
    .flatMap(_ => instances.appendForCallC(f.configuration, f.initial.identity, initialRevision, saved.before.history.head, call.core)).map(_ => ())
  private def assertNative(call: Probe, expected: InstanceRecord): Unit =
    take(instances.loadForCallC(f.configuration, expected.identity, call.core)).get shouldBe expected
  private def assertRows(call: Probe, saved: K.Captured): Unit = {
    val targets = take(U.bindTargetsC(host(values), declaration))
    take(U.prepareC(saved, targets)).rows.foreach { row =>
      val loaded = take(call.rowC(row.collection, row.identity)).get
      row.payload.fields.foreach(field => loaded.getString(field.key) shouldBe row.payload.getString(field.key))
    }
    take(call.rowC(declaration.continuations, take(M.keyC(anchor)))).nonEmpty shouldBe true
  }
  "Managed checkpoint business data" should {
    "publish the complete deterministic calculation and its declared generated documents" in {
      Given("genuine known-work inputs and a fresh Subsystem-owned SQLite database")
      val saved = capture()
      val path = Files.createTempDirectory("smwf-checkpoint-normal-").resolve("host.sqlite")
      withHost(path) { (call, port) =>
        take(installC(call, saved))
        When("the owning managed checkpoint executes its prepared calculation")
        val observed = take(port.executePreparedForCallC(saved, call.core))
        Then("the actual calculation and every declared generated document are available")
        observed.record.snapshot shouldBe saved.record.snapshot
        observed.current.suspension shouldBe None
        O.builtDocuments(observed.record.snapshot).nonEmpty shouldBe true
        assertRows(call, saved)
        Consequence.unit
      }
    }

    "consume a compatible catalog without source-location or whole-declaration proofs" in {
      Given("official generated artifacts with only diagnostic line locations changed and privately admitted by the actual catalog owner")
      val catalog = _relocated_catalog
      catalog.workflow.workflow.rootLine should not be f.catalog.workflow.workflow.rootLine
      val saved = capture()
      val path = Files.createTempDirectory("smwf-relocated-catalog-").resolve("host.sqlite")
      withHost(path) { (call, _) =>
        take(installC(call, saved))
        val port = take(Managed.bindC(_earlier_host, declaration, catalog))
        When("the owning call consumes the prepared calculation through the compatible catalog")
        val executed = take(port.executePreparedForCallC(saved, call.core))
        Then("the genuine calculation is accepted without certifying diagnostic locations or construction configuration")
        executed.record.snapshot shouldBe saved.record.snapshot
        assertRows(call, saved)
        Consequence.unit
      }
    }

    "consume selected outputs but not unused historical input copies" in {
      Given("an actual published checkpoint and estimates input no longer consumed by cached observation")
      val saved = capture()
      val path = Files.createTempDirectory("smwf-checkpoint-unused-input-").resolve("host.sqlite")
      withHost(path) { (call, port) =>
        take(installC(call, saved))
        take(port.executePreparedForCallC(saved, call.core))
        take(call.writeC(target(F.Purpose.Estimates), saved.record.originalContext.facts.partition.estimates.reference.identity,
          Record.data(F.storageField -> "unused historical estimates"), update = true))
        When("the owning reader consumes the actual cached calculation")
        val observed = take(port.loadMechanicalForCallC(query(saved), call.core)).get
        Then("unused input does not refuse computed output or require restoration of a copy")
        observed.record.snapshot shouldBe saved.record.snapshot
        Consequence.unit
      }
    }

    "require declared generated document readability without recertifying its full stored body" in {
      Given("a real native checkpoint whose selected proposal row remains a valid declared document")
      val saved = capture()
      val path = Files.createTempDirectory("smwf-mechanical-document-").resolve("host.sqlite")
      val targets = take(U.bindTargetsC(host(values), declaration))
      withHost(path) { (call, port) =>
        take(installC(call, saved))
        take(port.executePreparedForCallC(saved, call.core))
        val selected = O.builtDocuments(saved.record.snapshot).head.proposal
        val changed = selected.copy(children = selected.children.updated(0,
          selected.children.head.copy(expectedMinutes = selected.children.head.expectedMinutes + 1)))
        take(call.writeC(targets.proposals, selected.reference.identity,
          Record.data(F.storageField -> take(F.encodeProposalC(changed))), true))
        When("cached observation reads the current declared document, not a whole-body equality certificate")
        val observed = take(port.loadMechanicalForCallC(query(saved), call.core)).get
        Then("the actual native computation remains authoritative and observation appends nothing")
        observed.record shouldBe saved.record
        assertNative(call, saved.next)
        Given("that selected generated proposal is then absent")
        take(call.deleteFixtureRowC(targets.proposals, selected.reference.identity))
        When("the same observation resolves declared output availability")
        val absent = port.loadMechanicalForCallC(query(saved), call.core)
        Then("the missing declared document is refused, not replaced from an unused saved copy")
        absent.isSuccess shouldBe false
        assertNative(call, saved.next)
        Consequence.unit
      }
    }

    "leave selected-data admission to the actual document consumer" in {
      Given("one real mechanical publication whose stored typed snapshot has no objective")
      val saved = capture()
      val passive = saved.record.copy(snapshot = saved.record.snapshot.copy(objective = None))
      val path = Files.createTempDirectory("smwf-mechanical-passive-frontier-").resolve("host.sqlite")
      withHost(path) { (call, port) =>
        take(installC(call, saved))
        take(port.executePreparedForCallC(saved, call.core))
        val key = take(M.keyC(anchor))
        val row = take(call.rowC(declaration.continuations, key)).get
        val prefix = "cncf.workflow-json.v1:"
        val json = parser.parse(row.getString("mechanicalPayload").get.substring(prefix.length)).toOption.get
        val changed = json.hcursor.downField("payload").downField("value")
          .withFocus(_ => J.payloadCodec.encode(passive)).top.get
        take(call.writeC(declaration.continuations, key,
          Record.data("mechanicalPayload" -> (prefix + changed.noSpaces)), true))
        When("the owning frontier reader resolves passive data and a selected consumer actually uses it")
        val observed = take(port.loadMechanicalForCallC(query(saved), call.core)).get
        val frontier = SplitPhaseManagedPlanningFrontier.observed(observed.record.snapshot,
          observed.publicationPostimage, observed.current, saved.record.originalStart.input.invocationMode)
        val selected = SplitPhaseSelectedDocuments.projectC(frontier.snapshot)
        Then("storage conveys no selected frontier capability and neither observation nor refused use appends")
        observed.record shouldBe passive
        frontier.snapshot shouldBe passive.snapshot
        frontier.invocationMode shouldBe saved.record.originalStart.input.invocationMode
        frontier.publicationPostimage shouldBe saved.next
        frontier.current shouldBe saved.next
        selected.isSuccess shouldBe false
        observed.current shouldBe saved.next
        assertNative(call, saved.next)
        Consequence.unit
      }
    }

  }

  "Resolved-target native advance fixture" should {
    "compute Preview from current estimates without recertifying the old context copy" in {
      Given("a historical context and genuine current estimates with a known duration of 261 minutes")
      val saved = capture()
      val estimates = saved.record.originalContext.facts.partition.estimates
      val changed = estimates.copy(items = estimates.items.updated(0, estimates.items.head.copy(
        duration = SplitPhaseExplicitPartitionComputation.Duration.Known(261))))
      val path = Files.createTempDirectory("smwf-checkpoint-resolved-advance-").resolve("host.sqlite")
      withHost(path, resolvedTarget = Some(query(saved))) { (call, _) =>
        take(installC(call, saved))
        take(call.writeC(target(F.Purpose.Estimates), estimates.reference.identity,
          Record.data(F.storageField -> take(F.encodeEstimatesC(changed))), update = true))
        // This private resolved-target fixture supplies the execution-specific
        // Start and actual root reference required by the current receiver.
        // Public Start publication is exercised by the normal Operation suite.
        val executionstart = saved.record.originalStart.copy(reference = SplitPhaseTypedStartPreparation.Reference(
          "split-start:" + saved.before.identity.value, "1"))
        take(call.writeC(collection("starts"), executionstart.reference.identity,
          Record.data(TJ.storageField -> take(TJ.encodeC(executionstart)))))
        val executioncontext = saved.record.originalContext.copy(owner = saved.record.originalContext.owner.copy(
          start = executionstart.reference))
        take(call.writeC(collection("contexts"), executioncontext.reference.identity,
          Record.data(CJ.storageField -> take(CJ.encodeC(executioncontext))), update = true))
        val frontier = take(SplitPhaseExecutionFrontierDataStorePort.bindForCallC(call.core, declaration))
        take(frontier.createForCallC(saved.before.identity, saved.before.revision,
          SplitPhaseExecutionFrontier.Value.Initial(saved.record.originalContext.reference), call.core))
        When("the test-only Receiver supplies the resolved target to the actual managed advance backend")
        val component = call.core.component.get
        val response = take(component.subsystem.get.executeOperationResponseInContext(
          Request.of(component = component.name, service = K.advanceOperation.service,
            operation = K.advanceOperation.operation), call.executionContext))
        val advanced = response match {
          case OperationResponse.RecordResponse(record) =>
            take(org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceResult.createC(record))
          case other => fail("Expected declared advance business value, got " + other.toString)
        }
        val root = take(_root_c(call, saved))
        Then("current business inputs produce a selected Preview without a legacy transport or input-copy gate")
        advanced.kind shouldBe "Terminal"
        advanced.selectedPlan.get.mode shouldBe "PREVIEW"
        take(SplitPhasePlanningSnapshot.dispositionC(root.snapshot)) shouldBe SplitPhasePlanningSnapshot.Disposition.Planned
        root.snapshot.selection.partition.estimates.items.head.duration shouldBe
          SplitPhaseExplicitPartitionComputation.Duration.Known(261)
        root.originalContext.facts.partition.estimates.items.head.duration shouldBe
          SplitPhaseExplicitPartitionComputation.Duration.Known(261)
        Consequence.unit
      }
    }
  }
}
