package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
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
import SplitPhaseExplicitDurationCalibration.*
import SplitPhaseExplicitCalibrationRecordJsonV1.Record
import SplitPhaseCalibrationReadPolicy.Purpose

/**
 * @since   Oct.  2, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SplitPhaseManagedCalibrationReadSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Managed calibration fixture failed: $other")
  }
  private val catalog = take(SplitPhaseRuntimeCatalog.loadC())
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "calibration", name))
  private val configuration = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("calibration")),
    MigrationPolicy("v1"), RetentionPolicy("v1"), LeasePolicy("v1"), DeliveryConfiguration.SameStore(WorkflowStoreIdentity("calibration")))
  private val workflow = SplitPhaseManagedStartAssembly.Declaration(configuration, collection("instances"), collection("continuations"))
  private val policy = PolicyRecord(Reference("policy", "1"), SplitPhaseDurationCalibration.Policy("v1", 2, 100, 900,
    BigDecimal(3), BigDecimal(120), BigDecimal(2), 15, Map("scala" -> 10)))
  private val snapshot = Snapshot(Reference("history", "1"), Vector(
    Observation(Reference("a", "1"), "api", "scala", Some(100), 120, 2, 500, "authority-a"),
    Observation(Reference("b", "1"), "api", "scala", Some(100), 140, 2, 500, "authority-b")))
  private val item = SplitPhaseDurationCalibration.WorkItem("item", "api", "scala", 2, Some(100))
  private val record = Record(Reference("calibration", "1"), Request(snapshot, policy, item))
  private val prefix = SplitPhaseCalibrationReadPolicy.prefix
  private val values = Map[String, ConfigurationValue](prefix + "store-name" -> ConfigurationValue.StringValue("workflow")) ++
    Purpose.values.map(p => prefix + p.key -> ConfigurationValue.StringValue(collection(p.toString).print))
  private def host(data: Map[String, ConfigurationValue]) = ResolvedConfiguration(HostConfiguration(data), ConfigurationTrace.empty)

  "Managed calibration loading" should {
    "compose only exact independent calibration purposes without unrelated role prerequisites" in {
      Given("minimal calibration configuration and its selected Workflow binding")
      val declared = take(SplitPhaseCalibrationReadPolicy.decodeC(host(values)))
      When("composition and external-purpose restrictions are evaluated")
      val reader = SplitPhaseCalibrationReadPolicy.bindC(host(values), workflow)
      val invalid = values.keys.toVector.map(values.removed) ++ Vector(
        values.updated(prefix + "store-name", ConfigurationValue.StringValue("other")),
        values.updated(prefix + Purpose.Request.key, ConfigurationValue.StringValue(collection("instances").print)),
        values.updated(prefix + Purpose.Snapshot.key, values(prefix + Purpose.Policy.key)),
        values.updated(prefix + Purpose.Policy.key, ConfigurationValue.NumberValue(BigDecimal(1))))
      Then("only canonical same-store distinct collections are admitted without a live capability")
      reader shouldBe a[Consequence.Success[?]]
      Purpose.values.foreach(p => declared.collection(p) shouldBe collection(p.toString))
      invalid.foreach(v => SplitPhaseCalibrationReadPolicy.bindC(host(v), workflow) shouldBe a[Consequence.Failure[?]])
      SplitPhaseCalibrationReadPolicy.admitC(host(values), Purpose.Request, "workflow", collection("Snapshot"))
        .shouldBe(a[Consequence.Failure[?]])
      take(reader).loadForCallC(record.reference, null) shouldBe a[Consequence.Failure[?]]
    }

    "consume independently referenced source data and ignore unused diagnostics without constructing executable input" in {
      Given("separate current history and policy projections")
      val historyRaw = take(SplitPhaseCalibrationSourceJsonV1.encodeSnapshotC(snapshot))
      val policyRaw = take(SplitPhaseCalibrationSourceJsonV1.encodePolicyC(policy))
      val historyJson = parser.parse(historyRaw.drop(SplitPhaseCalibrationSourceJsonV1.snapshotSchema.length + 1)).toOption.get
      val changed = historyJson.mapObject(o => o.add("payload", o("payload").get.mapObject(_.add("admitted", Json.True))))
      val missing = historyJson.mapObject(o => o.add("payload", o("payload").get.mapObject(_.remove("reference"))))
      When("exact references and malformed projections are decoded")
      val history = take(SplitPhaseCalibrationSourceJsonV1.decodeSnapshotC(snapshot.reference, historyRaw))
      val observedPolicy = take(SplitPhaseCalibrationSourceJsonV1.decodePolicyC(policy.reference, policyRaw))
      val withDiagnostic = take(SplitPhaseCalibrationSourceJsonV1.decodeSnapshotC(snapshot.reference,
        SplitPhaseCalibrationSourceJsonV1.snapshotSchema + ":" + changed.noSpaces))
      Then("complete data survives unused fields while missing consumed fields and crossed references fail")
      history shouldBe snapshot
      observedPolicy shouldBe policy
      withDiagnostic shouldBe snapshot
      SplitPhaseCalibrationSourceJsonV1.decodeSnapshotC(snapshot.reference,
        SplitPhaseCalibrationSourceJsonV1.snapshotSchema + ":" + missing.noSpaces) shouldBe a[Consequence.Failure[?]]
      SplitPhaseCalibrationSourceJsonV1.decodeSnapshotC(snapshot.reference.copy(revision = "2"), historyRaw)
        .shouldBe(a[Consequence.Failure[?]])
      SplitPhaseCalibrationSourceJsonV1.decodePolicyC(policy.reference, historyRaw) shouldBe a[Consequence.Failure[?]]
      SplitPhaseCalibrationSourceJsonV1.encodeSnapshotC(null) shouldBe a[Consequence.Failure[?]]
      SplitPhaseCalibrationSourceJsonV1.encodePolicyC(policy.copy(policy = policy.policy.copy(maximumRatio = null)))
        .shouldBe(a[Consequence.Failure[?]])
    }

    "load and calculate through direct managed Operation calls and pool reopen" in {
      Given("canonical SQLite storage with a private data installer, not Start or human authority")
      val path = Files.createTempDirectory("smwf-calibration-read-").resolve("host.sqlite")
      val driftHistory = snapshot.copy(reference = Reference("drift-history", "1"))
      val driftPolicy = policy.copy(reference = Reference("drift-policy", "1"))
      def requestRow(value: Record) = (collection("Request"), value.reference.identity,
        SplitPhaseExplicitCalibrationRecordJsonV1.storageField, take(SplitPhaseExplicitCalibrationRecordJsonV1.encodeC(value)))
      val rows = Vector(requestRow(record),
        requestRow(record.copy(reference = Reference("history-drift-input", "1"), request = record.request.copy(snapshot = driftHistory))),
        requestRow(record.copy(reference = Reference("policy-drift-input", "1"), request = record.request.copy(policy = driftPolicy))),
        requestRow(record.copy(reference = Reference("missing-source-input", "1"), request = record.request.copy(
          snapshot = snapshot.copy(reference = Reference("missing-history", "1"))))),
        requestRow(record.copy(reference = Reference("crossed-source-revision", "1"), request = record.request.copy(
          snapshot = snapshot.copy(reference = snapshot.reference.copy(revision = "2"))))),
        (collection("Request"), "foreign", SplitPhaseExplicitCalibrationRecordJsonV1.storageField,
          take(SplitPhaseExplicitCalibrationRecordJsonV1.encodeC(record))),
        (collection("Request"), "malformed", SplitPhaseExplicitCalibrationRecordJsonV1.storageField, "not-json"),
        (collection("Request"), "missing-payload", "unrelated", "data"),
        (collection("Snapshot"), snapshot.reference.identity, SplitPhaseCalibrationSourceJsonV1.snapshotField,
          take(SplitPhaseCalibrationSourceJsonV1.encodeSnapshotC(snapshot))),
        (collection("Snapshot"), driftHistory.reference.identity, SplitPhaseCalibrationSourceJsonV1.snapshotField,
          take(SplitPhaseCalibrationSourceJsonV1.encodeSnapshotC(driftHistory.copy(observations = snapshot.observations.updated(0,
            snapshot.observations.head.copy(actualMinutes = 210)))))),
        (collection("Policy"), policy.reference.identity, SplitPhaseCalibrationSourceJsonV1.policyField,
          take(SplitPhaseCalibrationSourceJsonV1.encodePolicyC(policy))),
        (collection("Policy"), driftPolicy.reference.identity, SplitPhaseCalibrationSourceJsonV1.policyField,
          take(SplitPhaseCalibrationSourceJsonV1.encodePolicyC(driftPolicy.copy(policy = policy.policy.copy(roundingQuantumMinutes = 5))))))
      val persistencePolicy = take(EventPersistencePolicy.createC("calibration-read-v1", Vector(
        EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
      val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"), collection("indexes"),
        Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId, "workflow", workflow.instances, workflow.continuations))))
      var installations = 0
      var retainedReader = Option.empty[SplitPhaseCalibrationDataStorePort]
      var constructed = Vector.empty[Input]
      for (mode <- Vector("initial", "reopen", "undeclared")) {
        val context = ExecutionContext.create()
        val declared = if (mode == "undeclared") values.removed(prefix + Purpose.Policy.key) else values
        val config = host(declared ++ Map("textus.subsystem.user-mode" -> ConfigurationValue.StringValue("standalone"),
          "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
          "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)))
        val bootstrap = take(SubsystemEventStoreBootstrap.createC(persistencePolicy, storage,
          ScopeContext(ScopeKind.Subsystem, "calibration-read", None, context.observability), config))
        try {
          val owner = RuntimeBindingAdmissionFixture.admit(bootstrap.subsystem)
          val reader = if (mode == "undeclared") retainedReader.get
            else take(SplitPhaseCalibrationReadPolicy.bindC(owner.configuration, workflow))
          retainedReader = Some(reader)
          var captured = Vector.empty[ActionCall.Core]
          var loaded = 0
          val probe: ActionCall.Core => Consequence[Unit] = capsule => {
              captured :+= capsule
              final class Installer extends ProcedureActionCall {
                val core = capsule
                def installC(): Consequence[Unit] = {
                  given ExecutionContext = executionContext
                  val space = executionContext.dataStoreSpace
                  val prior = space.captureBinding()
                  try bind_component_application_datastore_c("workflow").flatMap { _ =>
                    rows.foldLeft(Consequence.unit) { case (previous, (target, key, field, value)) =>
                      previous.flatMap(_ => space.dataStore(target).flatMap(_.create(target, DataStore.StringEntryId(key),
                        org.goldenport.record.Record.data(field -> value))).map(_ => ()))
                    }
                  } finally space.restoreBinding(prior)
                }
                def execute() = Consequence.stateConflict("Private calibration data installer is not an Operation")
              }
              if (mode == "initial" && installations == 0) { take(new Installer().installC()); installations += 1 }
              val prior = capsule.executionContext.dataStoreSpace.captureBinding()
              if (mode == "undeclared") reader.loadForCallC(record.reference, capsule) shouldBe a[Consequence.Failure[?]]
              else {
                take(reader.loadForCallC(record.reference, capsule)) shouldBe Some(record)
                val input = take(reader.loadInputForCallC(record.reference, item, capsule)).get
                val result = take(calculateC(input))
                result shouldBe a[Outcome.Known]
                result.asInstanceOf[Outcome.Known].estimateMinutes shouldBe 145
                constructed :+= input
                loaded += 1
                take(reader.loadInputForCallC(Reference("absent", "1"), item, capsule)) shouldBe None
                Vector("foreign", "malformed", "missing-payload",
                  "missing-source-input", "crossed-source-revision").foreach(id =>
                  reader.loadInputForCallC(Reference(id, "1"), item, capsule) shouldBe a[Consequence.Failure[?]])
                Given("declared source rows whose actual values differ from the historical request copies")
                When("the current managed reader resolves those history and policy references")
                val changedhistory = take(reader.loadInputForCallC(Reference("history-drift-input", "1"), item, capsule)).get
                val changedpolicy = take(reader.loadInputForCallC(Reference("policy-drift-input", "1"), item, capsule)).get
                val historyestimate = take(calculateC(changedhistory)).asInstanceOf[Outcome.Known].estimateMinutes
                val policyestimate = take(calculateC(changedpolicy)).asInstanceOf[Outcome.Known].estimateMinutes
                Then("current deterministic inputs, not the copied values, determine the estimates")
                historyestimate shouldBe 190
                policyestimate shouldBe 140
                reader.loadForCallC(record.reference.copy(revision = "2"), capsule) shouldBe a[Consequence.Failure[?]]
                Given("a current work item with updated units and the same actual selected target")
                When("the reader constructs calculation input from that work item")
                val changeditem = take(reader.loadInputForCallC(record.reference,
                  item.copy(normalizedUnits = 3, explicitBaselineMinutes = None), capsule)).get
                val itemestimate = take(calculateC(changeditem)).asInstanceOf[Outcome.Known].estimateMinutes
                val foreigntarget = reader.loadInputForCallC(record.reference, item.copy(identity = "different-target"), capsule)
                Then("current units and baseline are admitted while a different target remains unresolved")
                itemestimate shouldBe 205
                foreigntarget shouldBe a[Consequence.Failure[?]]
                reader.loadForCallC(record.reference, capsule.copy(component = None)) shouldBe a[Consequence.Failure[?]]
              }
              capsule.executionContext.dataStoreSpace.captureBinding() shouldBe prior
              Consequence.unit
          }
          val factory = ManagedCallProbeFixture.factory(probe)
          val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
          owner.add(built)
          When(s"direct managed Operation dispatch loads calibration in $mode mode")
          take(owner.executeOperationResponse(ManagedCallProbeFixture.request(built.name)))
          take(owner.executeOperationResponse(ManagedCallProbeFixture.request(built.name)))
          Then("only the current declared data path loads inputs without creating Workflow history")
          captured.size shouldBe 2
          captured.head.executionContext should not be theSameInstanceAs(captured.last.executionContext)
          loaded shouldBe (if (mode == "undeclared") 0 else 2)
          reader.loadForCallC(record.reference, captured.last) shouldBe a[Consequence.Failure[?]]
          given ExecutionContext = context
          val bound = take(SplitPhaseManagedStartAssembly.bindPersistenceC(ComponentCreate(owner, ComponentOrigin.Builtin), workflow, catalog))
          take(bound.storage.bindInstancesC()).loadC(InstanceIdentity("not-created")).map(_ shouldBe None)
            .shouldBe(a[Consequence.Success[?]])
        } finally take(bootstrap.shutdownC())
      }
      installations shouldBe 1
      constructed.size shouldBe 4
      constructed.foreach(input => take(calculateC(input)).asInstanceOf[Outcome.Known].estimateMinutes shouldBe 145)
    }
  }
}
