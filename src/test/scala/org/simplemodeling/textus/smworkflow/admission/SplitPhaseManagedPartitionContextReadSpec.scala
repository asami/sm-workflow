package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.SubsystemEventStoreBootstrap
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionContextJsonV1 as J,
  SplitPhaseExplicitPartitionSemantic as S}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhasePartitionContextFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
final class SplitPhaseManagedPartitionContextReadSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "partitioncontext", name))
  private val workflow = SplitPhaseManagedStartAssembly.Declaration(configuration, collection("instances"), collection("continuations"))
  private val prefix = SplitPhasePartitionContextReadPolicy.prefix
  private val values = Map[String, ConfigurationValue](prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    prefix + "record-collection" -> ConfigurationValue.StringValue(collection("contexts").print))
  private def host(data: Map[String, ConfigurationValue]) = ResolvedConfiguration(HostConfiguration(data), ConfigurationTrace.empty)

  "Managed typed partition context reading" should {
    "bind only its independent exact purpose without legacy role prerequisites" in {
      Given("minimal host declaration and its canonical Workflow storage binding")
      val reader = take(SplitPhasePartitionContextReadPolicy.bindC(host(values), workflow))
      val invalid = values.keys.toVector.map(values.removed) ++ Vector(
        values.updated(prefix + "store-name", ConfigurationValue.StringValue("other")),
        values.updated(prefix + "record-collection", ConfigurationValue.StringValue(workflow.instances.print)),
        values.updated(prefix + "record-collection", ConfigurationValue.StringValue(workflow.continuations.print)),
        values.updated(prefix + "record-collection", ConfigurationValue.NumberValue(BigDecimal(1))))
      When("binding and external reads are attempted without an active Operation")
      Then("other stores, aliases, missing declarations and context-free reads are refused")
      invalid.foreach(v => SplitPhasePartitionContextReadPolicy.bindC(host(v), workflow).isSuccess shouldBe false)
      reader.loadC(record.reference).isSuccess shouldBe false
      reader.loadForCallC(record.reference, null).isSuccess shouldBe false
      SplitPhasePartitionContextReadPolicy.admitC(host(values), "workflow", collection("foreign")).isSuccess shouldBe false
    }

    "recover raw complete context through direct managed Operation calls and pool reopen" in {
      Given("a canonical managed host and private fixture data, not a Context registration or consent Operation")
      val path = Files.createTempDirectory("smwf-partition-context-").resolve("host.sqlite")
      val raw = take(J.encodeC(record))
      val rows = Vector(("partition-context", J.storageField, raw), ("foreign", J.storageField, raw),
        ("malformed", J.storageField, "not-json"), ("missing-payload", "unrelated", "data"))
      val persistencePolicy = take(EventPersistencePolicy.createC("partition-context-v1", Vector(
        EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
      val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"), collection("indexes"),
        Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId, "workflow", workflow.instances, workflow.continuations))))
      val catalog = take(SplitPhaseRuntimeCatalog.loadC())
      var installations = 0
      var retainedReader = Option.empty[SplitPhasePartitionContextDataStorePort]
      var totalReads = 0
      for (mode <- Vector("initial", "reopen", "undeclared")) {
        val context = ExecutionContext.create()
        val declared = if (mode == "undeclared") values.removed(prefix + "record-collection") else values
        val configured = host(declared ++ Map("textus.subsystem.user-mode" -> ConfigurationValue.StringValue("standalone"),
          "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
          "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)))
        val bootstrap = take(SubsystemEventStoreBootstrap.createC(persistencePolicy, storage,
          ScopeContext(ScopeKind.Subsystem, "partition-context", None, context.observability), configured))
        try {
          val owner = RuntimeBindingAdmissionFixture.admit(bootstrap.subsystem)
          val reader = if (mode == "undeclared") retainedReader.get
            else take(SplitPhasePartitionContextReadPolicy.bindC(owner.configuration, workflow))
          retainedReader = Some(reader)
          var captured = Vector.empty[ActionCall.Core]
          var reads = 0
          val probe: ActionCall.Core => Consequence[Unit] = capsule => {
              captured :+= capsule
              final class Installer extends ProcedureActionCall {
                val core = capsule
                def installC(): Consequence[Unit] = {
                  given ExecutionContext = executionContext
                  val space = executionContext.dataStoreSpace
                  val prior = space.captureBinding()
                  try bind_component_application_datastore_c("workflow").flatMap { _ =>
                    rows.foldLeft(Consequence.unit) { case (previous, (id, field, value)) =>
                      previous.flatMap(_ => space.dataStore(collection("contexts")).flatMap(_.create(collection("contexts"),
                        DataStore.StringEntryId(id), org.goldenport.record.Record.data(field -> value))))
                    }
                  } finally space.restoreBinding(prior)
                }
                def execute() = Consequence.stateConflict("Private context installer is not an Operation")
              }
              if (mode == "initial" && installations == 0) { take(new Installer().installC()); installations += 1 }
              val prior = capsule.executionContext.dataStoreSpace.captureBinding()
              if (mode == "undeclared") reader.loadForCallC(record.reference, capsule).isSuccess shouldBe false
              else {
                take(reader.loadForCallC(record.reference, capsule)) shouldBe Some(record)
                reads += 1
                totalReads += 1
                take(reader.loadForCallC(S.ContextReference("absent", "1"), capsule)) shouldBe None
                Vector("foreign", "malformed", "missing-payload").foreach(id =>
                  reader.loadForCallC(S.ContextReference(id, "1"), capsule).isSuccess shouldBe false)
                reader.loadForCallC(record.reference.copy(revision = "2"), capsule).isSuccess shouldBe false
                reader.loadForCallC(record.reference, capsule.copy(component = None)).isSuccess shouldBe false
              }
              capsule.executionContext.dataStoreSpace.captureBinding() shouldBe prior
              Consequence.unit
          }
          val factory = ManagedCallProbeFixture.factory(probe)
          val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
          owner.add(built)
          When(s"direct managed Operation dispatch reads context data in $mode mode")
          take(owner.executeOperationResponse(ManagedCallProbeFixture.request(built.name)))
          take(owner.executeOperationResponse(ManagedCallProbeFixture.request(built.name)))
          Then("only a current declared data read succeeds and it never creates an instance or retains a lease")
          captured.size shouldBe 2
          reads shouldBe (if (mode == "undeclared") 0 else 2)
          reader.loadForCallC(record.reference, captured.last).isSuccess shouldBe false
          org.goldenport.cncf.testutil.NativeHumanManagedHostObservation.counts(bootstrap) shouldBe (1, 0)
          given ExecutionContext = context
          val bound = take(SplitPhaseManagedStartAssembly.bindPersistenceC(ComponentCreate(owner, ComponentOrigin.Builtin), workflow, catalog))
          take(take(bound.storage.bindInstancesC()).loadC(record.owner.handle.instanceIdentity)) shouldBe None
        } finally take(bootstrap.shutdownC())
      }
      installations shouldBe 1
      totalReads shouldBe 4
    }
  }
}
