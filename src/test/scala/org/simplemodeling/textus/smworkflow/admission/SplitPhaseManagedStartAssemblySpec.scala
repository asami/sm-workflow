package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.SubsystemEventStoreBootstrap
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/**
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
final class SplitPhaseManagedStartAssemblySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"managed Start composition failed: $other")
  }
  private val componentId = SmWorkflowComponent.componentId
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "splitmanaged", name))
  private val configuration = Configuration(
    NamedWorkflowStore("workflow", WorkflowStoreIdentity("split-managed-store")),
    MigrationPolicy("split-v1"), RetentionPolicy("split-v1"), LeasePolicy("split-v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("split-managed-store")))
  private val declaration = SplitPhaseManagedStartAssembly.Declaration(configuration,
    collection("instances"), collection("continuations"))
  private val catalog = take(SplitPhaseRuntimeCatalog.loadC())

  private def withHost(body: (SubsystemEventStoreBootstrap, ExecutionContext) => Unit,
    extra: Map[String, ConfigurationValue] = Map.empty,
    retainedPath: Option[java.nio.file.Path] = None): Unit = {
    val context = ExecutionContext.create()
    val policy = take(EventPersistencePolicy.createC("split-managed-policy-v1", Vector(
      EventPersistencePolicy.Rule(EventTypeId("split-transient"), EventPersistencePolicy.Mode.Transient))))
    val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"),
      collection("events"), collection("indexes"), Vector(EventStorePersistenceDeclaration.WorkflowGrant(
        componentId, configuration.workflowStore.name, declaration.instances, declaration.continuations))))
    val path = retainedPath.getOrElse(Files.createTempDirectory("smwf-split-managed-start-").resolve("host.sqlite"))
    val config = ResolvedConfiguration(HostConfiguration(Map(
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
      "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)) ++ extra), ConfigurationTrace.empty)
    val host = take(SubsystemEventStoreBootstrap.createC(policy, storage,
      ScopeContext(ScopeKind.Subsystem, "split-managed-start", None, context.observability), config))
    try body(host, context) finally take(host.shutdownC())
  }

  "SplitPhase canonical managed persistence composition" should {
    "use current dispatch contexts for canonical instance CRUD and reject escaped or context-free access" in {
      Given("a real SQLite host and an official definition, with private instance fixture work")
      withHost { (host, context) =>
        val owner = RuntimeBindingAdmissionFixture.admit(host.subsystem)
        val initial = InstanceRecord(InstanceIdentity("current-call-private-fixture"), catalog.definition,
          initialRevision, Lifecycle.NotStarted, None, CorrelationReference("private-correlation"),
          CausationReference("private-causation"), None, None, None, Vector.empty)
        val entry = HistoryEntry(HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
          Some(ProgressionReference("entry.EntryAssessment")), initial.correlation, initial.causation,
          None, None, None)
        val expected = take(initial.appendC(initialRevision, entry))
        var binding = Option.empty[SplitPhaseManagedStartAssembly.PersistenceBinding]
        var composition = Option.empty[ExecutionContext]
        var calls = Vector.empty[ActionCall.Core]
        var readbacks = Vector.empty[InstanceRecord]
        val observe: ActionCall.Core => Consequence[Unit] = core => {
          calls :+= core
          val port = binding.get.instances
          val current = CallScopedWorkflowInstances.forCall(port, core)
          port.create(configuration, initial) shouldBe a[Consequence.Failure[?]]
          port.load(configuration, initial.identity) shouldBe a[Consequence.Failure[?]]
          port.append(configuration, initial.identity, initialRevision, entry) shouldBe a[Consequence.Failure[?]]
          current.load(configuration.copy(workflowStore =
            NamedWorkflowStore("foreign", WorkflowStoreIdentity("foreign"))), initial.identity) shouldBe
            a[Consequence.Failure[?]]
          CallScopedWorkflowInstances.forCall(port, core.copy(component = None))
            .load(configuration, initial.identity) shouldBe a[Consequence.Failure[?]]
          CallScopedWorkflowInstances.forCall(port, null).load(configuration, initial.identity) shouldBe
            a[Consequence.Failure[?]]
          CallScopedWorkflowInstances.forCall(port, core.copy(executionContext = null))
            .load(configuration, initial.identity) shouldBe a[Consequence.Failure[?]]
          val before = core.executionContext.dataStoreSpace.captureBinding()
          if (calls.size == 1) {
            take(current.load(configuration, initial.identity)) shouldBe None
            take(current.create(configuration, initial))
            take(current.append(configuration, initial.identity, initialRevision, entry)) shouldBe expected
          }
          readbacks :+= take(current.load(configuration, initial.identity)).get
          current.append(configuration, initial.identity, initialRevision, entry) shouldBe a[Consequence.Failure[?]]
          core.executionContext.dataStoreSpace.captureBinding() shouldBe before
          Consequence.unit
        }
        val factory = new SmWorkflowConfiguredComponentFactory(Vector.empty, Vector.empty,
          (creation, parameters, resolved) => {
            given ExecutionContext = resolved
            composition = Some(resolved)
            SplitPhaseManagedStartAssembly.bindPersistenceC(creation, declaration, catalog).map { value =>
              binding = Some(value)
              Vector(ManagedCallProbeFixture.service(observe))
            }
          }).primaryFactory
        val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
        owner.add(built)
        val request = ManagedCallProbeFixture.request(built.name)
        When("two direct Operations exercise canonical CRUD using their current call capsules")
        take(owner.executeOperationResponse(request))
        take(owner.executeOperationResponse(request))
        Then("each incoming context reads the persisted instance without reusing the Factory context")
        calls.size shouldBe 2
        calls.foreach(_.executionContext should not be theSameInstanceAs(composition.get))
        calls.head.executionContext should not be theSameInstanceAs(calls.last.executionContext)
        readbacks shouldBe Vector(expected, expected)
        When("the completed capsule is reused after dispatch")
        val escaped = CallScopedWorkflowInstances.forCall(binding.get.instances, calls.last)
        Then("all CRUD refuses the ended dispatch lease even while the host pool is alive")
        Vector(escaped.create(configuration, initial), escaped.load(configuration, initial.identity),
          escaped.append(configuration, initial.identity, initialRevision, entry))
          .foreach(_ shouldBe a[Consequence.Failure[?]])
      }
    }

    "read the actual managed selection collection during dispatch and refuse the escaped call afterwards" in {
      Given("a real SQLite host with only SplitPhase's independent selection read declaration")
      val selections = collection("selections")
      val records: Map[String, ConfigurationValue] = Map(
        SplitPhaseHostSelectionReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
        SplitPhaseHostSelectionReadPolicy.prefix + "selection-collection" -> ConfigurationValue.StringValue(selections.print))
      withHost({ (host, context) =>
        val owner = RuntimeBindingAdmissionFixture.admit(host.subsystem)
        val reader = take(SplitPhaseHostSelectionReadPolicy.bindC(owner.configuration, declaration))
        var calls = Vector.empty[ActionCall.Core]
        var readbacks = Vector.empty[Option[ProfileStartAdmission.Selection]]
        val observe: ActionCall.Core => Consequence[Unit] = core => {
          calls :+= core
          reader.loadForCallC("absent-selection", core).map { result =>
            readbacks :+= result
            ()
          }
        }
        val factory = ManagedCallProbeFixture.factory(observe)
        val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
        owner.add(built)
        val request = ManagedCallProbeFixture.request(built.name)
        When("two direct Operations read the declared empty collection")
        take(owner.executeOperationResponse(request))
        take(owner.executeOperationResponse(request))
        Then("successful datastore reads return absence without issuing execution authority")
        readbacks shouldBe Vector(None, None)
        calls.size shouldBe 2
        calls.foreach(_.component.get should be theSameInstanceAs built)
        When("the completed call capsule is used for another selection read")
        val escaped = reader.loadForCallC("absent-selection", calls.last)
        Then("the expired dispatch lease rejects it even though the host declaration still exists")
        escaped shouldBe a[Consequence.Failure[?]]
        reader.loadC("absent-selection") shouldBe a[Consequence.Failure[?]]
      }, records)
    }

    "bind inside canonical Factory initialization and dispatch with the owning component's current call" in {
      Given("a real SQLite Subsystem host grant and an official SplitPhase runtime catalog")
      withHost { (host, context) =>
        val owner = RuntimeBindingAdmissionFixture.admit(host.subsystem)
        var composition = Option.empty[ExecutionContext]
        var calls = Vector.empty[ActionCall.Core]
        val observe: ActionCall.Core => Consequence[Unit] = core => {
          calls :+= core
          Consequence.unit
        }
        val factory = new SmWorkflowConfiguredComponentFactory(Vector.empty, Vector.empty,
          (creation, parameters, resolved) => {
            composition = Some(resolved)
            given ExecutionContext = resolved
            SplitPhaseManagedStartAssembly.bindPersistenceC(creation, declaration, catalog)
              .map(_ => Vector(ManagedCallProbeFixture.service(observe)))
          }).primaryFactory
        When("canonical component construction binds persistence on the existing Subsystem")
        val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
        owner.add(built)
        Then("the component shares the existing datastore space without executing the probe")
        built.subsystem.get should be theSameInstanceAs owner
        built.scopeContext.dataStoreSpace should be theSameInstanceAs composition.get.dataStoreSpace
        built.mcpReadyOperations shouldBe Set.empty
        calls shouldBe empty
        When("the owning Subsystem dispatches two direct Operations")
        val request = ManagedCallProbeFixture.request(built.name)
        take(owner.executeOperationResponse(request))
        take(owner.executeOperationResponse(request))
        Then("each real call capsule belongs to the component and uses its incoming context")
        calls.size shouldBe 2
        calls.foreach { call =>
          call.component.get should be theSameInstanceAs built
          call.component.get.subsystem.get should be theSameInstanceAs owner
          call.executionContext should not be theSameInstanceAs(composition.get)
        }
      }
    }

    "reject ungranted collections and incomplete host dependencies before returning persistence" in {
      Given("a real host whose Workflow grant names only the admitted instance and continuation collections")
      withHost { (host, context) =>
        given ExecutionContext = context
        val create = ComponentCreate(host.subsystem, ComponentOrigin.Builtin)
        When("composition requests foreign collections or missing host dependencies")
        val results = Vector(
          SplitPhaseManagedStartAssembly.bindPersistenceC(create, declaration.copy(instances = collection("foreign")), catalog),
          SplitPhaseManagedStartAssembly.bindPersistenceC(create, declaration.copy(continuations = collection("foreign")), catalog),
          SplitPhaseManagedStartAssembly.bindPersistenceC(null, declaration, catalog),
          SplitPhaseManagedStartAssembly.bindPersistenceC(create, null, catalog),
          SplitPhaseManagedStartAssembly.bindPersistenceC(create, declaration, null))
        Then("canonical grants and actual dependencies refuse those bindings without an alternate storage route")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
      }
    }
  }
}
