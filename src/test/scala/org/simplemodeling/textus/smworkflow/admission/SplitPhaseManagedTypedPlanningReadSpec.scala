package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.SubsystemEventStoreBootstrap
import org.goldenport.cncf.testutil.{RuntimeBindingAdmissionFixture, NativeHumanManagedHostObservation}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPlanningInputJsonV1 as J,
  SplitPhaseExplicitPartitionContextJsonV1 as CJ, SplitPhaseTypedStartJsonV1 as TJ,
  SplitPhaseTypedStartPreparation as T, SplitPhaseExplicitPartitionSemantic as S}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture.*
import J.Purpose

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseManagedTypedPlanningReadSpec extends AnyWordSpec with Matchers with org.scalatest.GivenWhenThen {
  private val facts = SplitPhaseTypedStartFixture.facts.copy(
    partition = SplitPhasePartitionContextFixture.baseData.records,
    boundary = SplitPhaseTypedStartFixture.facts.boundary.copy(handoffs =
      SplitPhaseTypedStartFixture.facts.boundary.handoffs.copy(edges =
        SplitPhaseTypedStartFixture.facts.boundary.handoffs.edges.map(_.copy(
          disposition = SplitPhasePartitionEnumeration.EdgeDisposition.Either)))))
  private val context = SplitPhaseTypedStartFixture.context.copy(facts = facts)
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "typedplanning", name))
  private def target(purpose: Purpose) = collection(purpose.toString.toLowerCase)
  private val workflow = SplitPhaseManagedStartAssembly.Declaration(configuration, collection("instances"), collection("continuations"))
  private val ip = SplitPhasePlanningInputReadPolicy.prefix
  private val cp = SplitPhasePartitionContextReadPolicy.prefix
  private val sp = SplitPhaseHostSelectionReadPolicy.prefix
  private val tp = SplitPhaseTypedStartReadPolicy.prefix
  private val values = Map[String, ConfigurationValue](
    ip + "store-name" -> ConfigurationValue.StringValue("workflow"),
    cp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    cp + "record-collection" -> ConfigurationValue.StringValue(collection("contexts").print),
    sp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    sp + "selection-collection" -> ConfigurationValue.StringValue(collection("selections").print),
    tp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    tp + "record-collection" -> ConfigurationValue.StringValue(collection("starts").print)) ++
    Purpose.values.map(p => (ip + p.key) -> ConfigurationValue.StringValue(target(p).print))
  private def host(data: Map[String, ConfigurationValue]) = ResolvedConfiguration(HostConfiguration(data), ConfigurationTrace.empty)
  private def rows = Vector(
    (Purpose.Source, source.reference.identity, take(J.encodeSourceC(source))),
    (Purpose.Inventory, facts.proposal.inventory.reference.identity, take(J.encodeInventoryC(facts.proposal.inventory))),
    (Purpose.Proposal, proposal.reference.identity, take(J.encodeProposalC(proposal))),
    (Purpose.Boundary, facts.boundary.boundary.reference.identity, take(J.encodeBoundaryC(facts.boundary.boundary))),
    (Purpose.Ownership, facts.boundary.ownership.reference.identity, take(J.encodeOwnershipC(facts.boundary.ownership))),
    (Purpose.Handoffs, facts.boundary.handoffs.reference.identity, take(J.encodeHandoffsC(facts.boundary.handoffs))),
    (Purpose.Estimates, facts.partition.estimates.reference.identity, take(J.encodeEstimatesC(facts.partition.estimates))),
    (Purpose.PartitionPolicy, facts.partition.policy.reference.identity, take(J.encodePartitionPolicyC(facts.partition.policy))),
    (Purpose.Planning, facts.planning.reference.identity, take(J.encodePlanningC(facts.planning))))

  "Managed typed planning reads" should {
    "require fourteen separate purposes without legacy Start entry or proposal configuration" in {
      Given("fourteen distinct declared purposes without a retained call or provider")
      val port = take(SplitPhaseTypedStartReadPolicy.bindC(host(values), workflow, catalog))
      When("assembly metadata is incomplete or purpose collections alias")
      Then("no current-call planning admission or undeclared authority is created")
      port.prepareForCallC(record.reference, null).isSuccess shouldBe false
      val aliases = Purpose.values.toVector.map(target) ++ Vector(collection("contexts"), collection("selections"),
        workflow.instances, workflow.continuations)
      val invalid = values.keys.toVector.map(values.removed) ++ aliases.map(c =>
        values.updated(tp + "record-collection", ConfigurationValue.StringValue(c.print))) ++ Vector(
        values.updated(tp + "store-name", ConfigurationValue.StringValue("other")),
        values.updated(tp + "record-collection", ConfigurationValue.NumberValue(BigDecimal(1))))
      invalid.foreach(v => SplitPhaseTypedStartReadPolicy.bindC(host(v), workflow, catalog).isSuccess shouldBe false)
      SplitPhaseTypedStartReadPolicy.admitC(host(values), "workflow", collection("contexts")).isSuccess shouldBe false
    }

    "read current source proposal and selection through Service CLI and physical reopen without retaining admission" in {
      Given("independently stored known inputs, original typed Start and one canonical native instance")
      val path = Files.createTempDirectory("smwf-typed-planning-").resolve("host.sqlite")
      val policy = take(EventPersistencePolicy.createC("typed-starts-v1", Vector(
        EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
      val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"), collection("indexes"),
        Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId, "workflow", workflow.instances, workflow.continuations))))
      var installed = false
      var boundCalls = 0
      var retained = Option.empty[SplitPhaseTypedStartDataStorePort]
      for (mode <- Vector("initial", "reopen", "undeclared")) {
        val ctx = ExecutionContext.create()
        val declared = if (mode == "undeclared") values.removed(tp + "record-collection") else values
        val configured = host(declared ++ Map("textus.subsystem.user-mode" -> ConfigurationValue.StringValue("standalone"),
          "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
          "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)))
        val bootstrap = take(SubsystemEventStoreBootstrap.createC(policy, storage,
          ScopeContext(ScopeKind.Subsystem, "typed-starts", None, ctx.observability), configured))
        try {
          val owner = RuntimeBindingAdmissionFixture.admit(bootstrap.subsystem)
          val port = if (mode == "undeclared") retained.get
            else take(SplitPhaseTypedStartReadPolicy.bindC(owner.configuration, workflow, catalog))
          retained = Some(port)
          val instances = CallScopedWorkflowInstances.managed(configuration, catalog.definition, workflow.instances, workflow.continuations)
          var captured = Vector.empty[ActionCall.Core]
          val probe = new ProfileStartAdmission.CallScopedSelectionPort {
            def loadForCallC(identity: String, capsule: ActionCall.Core): Consequence[Option[ProfileStartAdmission.Selection]] = {
              captured :+= capsule
              final class Installer extends ProcedureActionCall {
                val core = capsule
                def writeC(c: DataStore.CollectionId, id: String, row: Record, update: Boolean): Consequence[Unit] = {
                  given ExecutionContext = executionContext
                  val space = executionContext.dataStoreSpace
                  val prior = space.captureBinding()
                  try bind_component_application_datastore_c("workflow").flatMap { _ =>
                    space.dataStore(c).flatMap(store => if (update) store.update(c, DataStore.StringEntryId(id), row)
                      else store.create(c, DataStore.StringEntryId(id), row))
                  } finally space.restoreBinding(prior)
                }
                def installC(): Consequence[Unit] = {
                  val all = rows.map { case (p, id, raw) => (target(p), id, Record.data(J.storageField -> raw)) } ++ Vector(
                    (collection("starts"), record.reference.identity, Record.data(TJ.storageField -> take(TJ.encodeC(record)))),
                    (collection("starts"), "malformed", Record.data(TJ.storageField -> "not-json")),
                    (collection("starts"), "missing-payload", Record.data("unrelated" -> "data")),
                    (collection("selections"), selection.selectionId, Record.data(ProfileSelectionJsonV1.storageField ->
                      take(ProfileSelectionJsonV1.encodeStorageC(selection)))),
                    (collection("contexts"), context.reference.identity, Record.data(CJ.storageField -> take(CJ.encodeC(context)))),
                    (collection("contexts"), "missing-start", Record.data(CJ.storageField -> take(CJ.encodeC(context.copy(
                      reference = S.ContextReference("missing-start", "1"), owner = context.owner.copy(start = SplitPhaseExplicitStartPreparation.Reference("absent", "1"))))))))
                  all.foldLeft(Consequence.unit) { case (before, (c, id, row)) => before.flatMap(_ => writeC(c, id, row, false)) }
                    .flatMap(_ => instances.createForCallC(configuration, initial, capsule))
                    .flatMap(_ => instances.appendForCallC(configuration, initial.identity, initialRevision, instance.history.head, capsule))
                    .map(_ => ())
                }
                def execute() = Consequence.stateConflict("Private test installer is not an Operation")
              }
              val installer = new Installer()
              if (!installed) { take(installer.installC()); installed = true }
              val beforeBinding = capsule.executionContext.dataStoreSpace.captureBinding()
              val before = take(instances.loadForCallC(configuration, instance.identity, capsule))
              When(s"the current native $mode call reads its exact planning selection")
              Then("complete current records bind without issuing AI work, and unavailable or changed authority refuses")
              if (mode == "undeclared") {
                port.prepareForCallC(record.reference, capsule).isSuccess shouldBe false
                port.bindPlanningForCallC(context.reference, capsule).isSuccess shouldBe false
              } else {
                take(port.prepareForCallC(record.reference, capsule)).get.record shouldBe record
                val bound = take(port.bindPlanningForCallC(context.reference, capsule)).get
                bound.context.currentInstance shouldBe instance
                bound.context.record shouldBe context
                port.bindPartitionForCallC(context.reference, capsule).isSuccess shouldBe false
                boundCalls += 1
                take(port.prepareForCallC(SplitPhaseExplicitStartPreparation.Reference("absent", "1"), capsule)) shouldBe None
                Vector(SplitPhaseExplicitStartPreparation.Reference("malformed", "1"),
                  SplitPhaseExplicitStartPreparation.Reference("missing-payload", "1"), record.reference.copy(revision = "2"))
                  .foreach(r => port.prepareForCallC(r, capsule).isSuccess shouldBe false)
                take(port.bindPlanningForCallC(S.ContextReference("absent", "1"), capsule)) shouldBe None
                port.bindPlanningForCallC(S.ContextReference("missing-start", "1"), capsule).isSuccess shouldBe false
                val changes = Vector(
                  (target(Purpose.Source), source.reference.identity, J.storageField,
                    take(J.encodeSourceC(source.copy(authority = source.authority.copy(revision = "2")))), take(J.encodeSourceC(source))),
                  (target(Purpose.Proposal), proposal.reference.identity, J.storageField,
                    take(J.encodeProposalC(proposal.copy(children = proposal.children.updated(0, proposal.children.head.copy(expectedMinutes = 361))))),
                    take(J.encodeProposalC(proposal))),
                  (collection("selections"), selection.selectionId, ProfileSelectionJsonV1.storageField,
                    take(ProfileSelectionJsonV1.encodeStorageC(selection.copy(recommendationReference = Some("changed")))),
                    take(ProfileSelectionJsonV1.encodeStorageC(selection))))
                changes.foreach { case (c, id, field, changed, original) =>
                  take(installer.writeC(c, id, Record.data(field -> changed), true))
                  port.prepareForCallC(record.reference, capsule).isSuccess shouldBe false
                  port.bindPlanningForCallC(context.reference, capsule).isSuccess shouldBe false
                  take(installer.writeC(c, id, Record.data(field -> original), true))
                }
                take(port.bindPlanningForCallC(context.reference, capsule)).get.start.record shouldBe record
              }
              take(instances.loadForCallC(configuration, instance.identity, capsule)) shouldBe before
              capsule.executionContext.dataStoreSpace.captureBinding() shouldBe beforeBinding
              Consequence.success(None) // Data probes never admit or execute the legacy Start.
            }
          }
          val entry = new SplitPhaseEntryAssessment.Port {
            def loadSourceC(phase: String, authority: String) = Consequence.stateConflict("No legacy source in typed probe")
            def loadProposalC(reference: String) = Consequence.stateConflict("No legacy proposal in typed probe")
          }
          val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty, (creation, parameters, resolved) => {
            given ExecutionContext = resolved
            SplitPhaseManagedStartAssembly.bindC(creation, workflow, catalog, probe, entry).map(operation =>
              spec.ServiceDefinition("SplitPhaseService", spec.OperationDefinitionGroup(NonEmptyVector.of(operation))))
          }).primaryFactory
          val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
          owner.add(built)
          val wire = SplitPhaseStartServiceOperation.encodeCommand(command)
          owner.executeOperationResponse(Request.of(component = built.name, service = "SplitPhaseService", operation = "startSplitPhase",
            arguments = List(Argument("arg1", wire)))).isSuccess shouldBe false
          new CncfRuntime().executeCommandResponse(owner, Array(s"${built.name}/SplitPhaseService/startSplitPhase", "--json", wire))
            .isSuccess shouldBe false
          captured.size shouldBe 2
          port.prepareForCallC(record.reference, captured.last).isSuccess shouldBe false
          port.bindPlanningForCallC(context.reference, captured.last).isSuccess shouldBe false
          port.bindPartitionForCallC(context.reference, captured.last).isSuccess shouldBe false
          NativeHumanManagedHostObservation.counts(bootstrap) shouldBe (1, 0)
        } finally take(bootstrap.shutdownC())
      }
      boundCalls shouldBe 4
    }
  }
}
