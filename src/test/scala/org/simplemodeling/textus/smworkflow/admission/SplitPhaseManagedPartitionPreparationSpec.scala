package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.SubsystemEventStoreBootstrap
import org.goldenport.cncf.testutil.{RuntimeBindingAdmissionFixture, NativeHumanManagedHostObservation}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPlanningInputJsonV1 as J,
  SplitPhaseExplicitPartitionContextJsonV1 as CJ, SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPartitionComputation as E}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhasePartitionContextFixture.*
import J.Purpose

/**
 * @since   Oct.  2, 2026
 * @version Oct. 24, 2026
 */
@org.goldenport.cncf.validation.annotation.ValidationSpecification(
  declaration = classOf[org.simplemodeling.textus.smworkflow.testing.LocalTestDeclaration])
final class SplitPhaseManagedPartitionPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "partitioninputs", name))
  private def target(purpose: Purpose) = collection(purpose.toString.toLowerCase)
  private val workflow = SplitPhaseManagedStartAssembly.Declaration(configuration, collection("instances"), collection("continuations"))
  private val prefix = SplitPhasePlanningInputReadPolicy.prefix
  private val cp = SplitPhasePartitionContextReadPolicy.prefix
  private val values = Map[String, ConfigurationValue](
    prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    cp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    cp + "record-collection" -> ConfigurationValue.StringValue(collection("contexts").print)) ++
    Purpose.values.map(p => (prefix + p.key) -> ConfigurationValue.StringValue(target(p).print))
  private def host(data: Map[String, ConfigurationValue]) = ResolvedConfiguration(HostConfiguration(data), ConfigurationTrace.empty)
  private final case class SourceRow(purpose: Purpose, identity: String, raw: String, drift: String)
  private def sourceRow[A](purpose: Purpose, identity: String, value: A, changed: A,
    encode: A => Consequence[String]) = SourceRow(purpose, identity, take(encode(value)), take(encode(changed)))
  private def sourceRows = Vector(
    sourceRow(Purpose.Source, facts.proposal.source.reference.identity, facts.proposal.source,
      facts.proposal.source.copy(phase = facts.proposal.source.phase.copy(value = "88")), J.encodeSourceC),
    sourceRow(Purpose.Inventory, facts.proposal.inventory.reference.identity, facts.proposal.inventory,
      facts.proposal.inventory.copy(orderedUnfinishedItems = facts.proposal.inventory.orderedUnfinishedItems.reverse), J.encodeInventoryC),
    sourceRow(Purpose.Proposal, facts.proposal.proposal.reference.identity, facts.proposal.proposal,
      facts.proposal.proposal.copy(children = facts.proposal.proposal.children.updated(0,
        facts.proposal.proposal.children.head.copy(expectedMinutes = 361))), J.encodeProposalC),
    sourceRow(Purpose.Boundary, facts.boundary.boundary.reference.identity, facts.boundary.boundary,
      facts.boundary.boundary.copy(items = facts.boundary.boundary.items.reverse), J.encodeBoundaryC),
    sourceRow(Purpose.Ownership, facts.boundary.ownership.reference.identity, facts.boundary.ownership,
      facts.boundary.ownership.copy(method = "other-method"), J.encodeOwnershipC),
    sourceRow(Purpose.Handoffs, facts.boundary.handoffs.reference.identity, facts.boundary.handoffs,
      facts.boundary.handoffs.copy(edges = facts.boundary.handoffs.edges.updated(0,
        facts.boundary.handoffs.edges.head.copy(disposition = SplitPhasePartitionEnumeration.EdgeDisposition.CutOnly))), J.encodeHandoffsC),
    sourceRow(Purpose.Estimates, facts.partition.estimates.reference.identity, facts.partition.estimates,
      facts.partition.estimates.copy(items = facts.partition.estimates.items.updated(0,
        facts.partition.estimates.items.head.copy(duration = E.Duration.Known(261)))), J.encodeEstimatesC),
    sourceRow(Purpose.PartitionPolicy, facts.partition.policy.reference.identity, facts.partition.policy,
      facts.partition.policy.copy(phaseOverheadMinutes = 15), J.encodePartitionPolicyC),
    sourceRow(Purpose.Planning, facts.planning.reference.identity, facts.planning,
      facts.planning.copy(authority = facts.planning.authority.copy(revision = "2")), J.encodePlanningC))

  "Managed typed partition preparation" should {
    "bind only nine distinct current purposes disjoint from all Workflow storage" in {
      Given("independent input and context declarations, with no legacy entry or proposal policy")
      take(SplitPhaseManagedPartitionPreparation.bindC(host(values), workflow, catalog))
      val invalid = values.keys.toVector.map(values.removed) ++ Vector(
        values.updated(prefix + "store-name", ConfigurationValue.StringValue("other")),
        values.updated(prefix + Purpose.Source.key, ConfigurationValue.NumberValue(BigDecimal(1)))) ++
        Vector(workflow.instances, workflow.continuations, collection("contexts"), target(Purpose.Inventory)).map(c =>
          values.updated(prefix + Purpose.Source.key, ConfigurationValue.StringValue(c.print)))
      When("missing roles, wrong stores, wrong types or aliases are bound")
      Then("composition refuses them before any data read")
      invalid.foreach(v => SplitPhaseManagedPartitionPreparation.bindC(host(v), workflow, catalog).isSuccess shouldBe false)
      SplitPhasePlanningInputReadPolicy.admitC(host(values), Purpose.Source, "workflow", target(Purpose.Inventory))
        .isSuccess shouldBe false
      val preparation = take(SplitPhaseManagedPartitionPreparation.bindC(host(values), workflow, catalog))
      preparation.prepareForCallC(record.reference, null).isSuccess shouldBe false
    }

    "prepare from independent current rows and canonical instance through direct Operation calls and physical reopen" in {
      Given("private fixture rows in one managed SQLite provider, not production registration or human consent")
      val path = Files.createTempDirectory("smwf-partition-inputs-").resolve("host.sqlite")
      val rows = sourceRows
      def withSource(identity: String) = facts.copy(proposal = facts.proposal.copy(source = facts.proposal.source.copy(
        reference = facts.proposal.source.reference.copy(identity = identity))))
      val contextRows = Vector(record,
        record.copy(reference = S.ContextReference("absent-source", "1"), facts = withSource("absent")),
        record.copy(reference = S.ContextReference("malformed-source", "1"), facts = withSource("malformed")),
        record.copy(reference = S.ContextReference("missing-payload-source", "1"), facts = withSource("missing-payload")),
        record.copy(reference = S.ContextReference("wrong-source-revision", "1"), facts = facts.copy(proposal =
          facts.proposal.copy(source = facts.proposal.source.copy(reference = facts.proposal.source.reference.copy(revision = "2"))))),
        record.copy(reference = S.ContextReference("absent-instance", "1"), owner = record.owner.copy(
          handle = record.owner.handle.copy(instanceIdentity = InstanceIdentity("absent")))))
      val eventPolicy = take(EventPersistencePolicy.createC("partition-inputs-v1", Vector(
        EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
      val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"), collection("indexes"),
        Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId, "workflow", workflow.instances, workflow.continuations))))
      var retained = Option.empty[SplitPhaseManagedPartitionPreparation.Port]
      var installed = false
      var preparedCalls = 0
      for (mode <- Vector("initial", "reopen", "undeclared")) {
        val context = ExecutionContext.create()
        val declared = if (mode == "undeclared") values.removed(prefix + Purpose.Estimates.key) else values
        val configured = host(declared ++ Map("textus.subsystem.user-mode" -> ConfigurationValue.StringValue("standalone"),
          "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
          "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)))
        val bootstrap = take(SubsystemEventStoreBootstrap.createC(eventPolicy, storage,
          ScopeContext(ScopeKind.Subsystem, "partition-inputs", None, context.observability), configured))
        try {
          val owner = RuntimeBindingAdmissionFixture.admit(bootstrap.subsystem)
          val preparation = if (mode == "undeclared") retained.get
            else take(SplitPhaseManagedPartitionPreparation.bindC(owner.configuration, workflow, catalog))
          retained = Some(preparation)
          val currentInputs = take(SplitPhasePlanningInputReadPolicy.bindC(host(values), workflow))
          val contexts = take(SplitPhasePartitionContextReadPolicy.bindC(host(values), workflow))
          val instances = CallScopedWorkflowInstances.managed(configuration, catalog.definition, workflow.instances, workflow.continuations)
          var captured = Vector.empty[ActionCall.Core]
          val probe: ActionCall.Core => Consequence[Unit] = capsule => {
              captured :+= capsule
              final class Installer extends ProcedureActionCall {
                val core = capsule
                private def withStoreC[A](f: ExecutionContext => Consequence[A]): Consequence[A] = {
                  given ExecutionContext = executionContext
                  val space = executionContext.dataStoreSpace
                  val prior = space.captureBinding()
                  try bind_component_application_datastore_c("workflow").flatMap(_ => f(executionContext))
                  finally space.restoreBinding(prior)
                }
                def writeC(c: DataStore.CollectionId, id: String, row: Record, update: Boolean): Consequence[Unit] =
                  withStoreC { ctx =>
                    given ExecutionContext = ctx
                    ctx.dataStoreSpace.dataStore(c).flatMap(store =>
                      if (update) store.update(c, DataStore.StringEntryId(id), row)
                      else store.create(c, DataStore.StringEntryId(id), row))
                  }
                def changeC(row: SourceRow, raw: String): Consequence[Unit] =
                  writeC(target(row.purpose), row.identity, Record.data(J.storageField -> raw), true)
                def installC(): Consequence[Unit] = {
                  val all = rows.map(r => (target(r.purpose), r.identity, Record.data(J.storageField -> r.raw))) ++
                    contextRows.map(r => (collection("contexts"), r.reference.identity, Record.data(CJ.storageField -> take(CJ.encodeC(r))))) ++
                    Vector((target(Purpose.Source), "malformed", Record.data(J.storageField -> "not-json")),
                      (target(Purpose.Source), "missing-payload", Record.data("unrelated" -> "data")))
                  all.foldLeft(Consequence.unit) { case (prior, (c, id, row)) =>
                    prior.flatMap(_ => writeC(c, id, row, false))
                  }.flatMap(_ => instances.createForCallC(configuration, initial, capsule))
                    .flatMap(_ => instances.appendForCallC(configuration, initial.identity, initialRevision, instance.history.head, capsule))
                    .map(_ => ())
                }
                def execute() = Consequence.stateConflict("Private fixture installer is not an Operation")
              }
              val installer = new Installer()
              if (!installed) { take(installer.installC()); installed = true }
              val prior = capsule.executionContext.dataStoreSpace.captureBinding()
              if (mode == "undeclared") {
                preparation.prepareForCallC(record.reference, capsule).isSuccess shouldBe false
                currentInputs.loadCurrentForCallC(facts, capsule).isSuccess shouldBe false
              } else {
                val before = take(instances.loadForCallC(configuration, instance.identity, capsule))
                before shouldBe Some(instance)
                take(currentInputs.loadCurrentForCallC(facts, capsule)) shouldBe facts
                val prepared = take(preparation.prepareForCallC(record.reference, capsule)).get
                prepared.record shouldBe record
                prepared.request.items.map(_.identity.value) shouldBe Vector("item-1")
                prepared.request.boundaries.map(_.edge.afterItem.value) shouldBe Vector("item-0")
                preparedCalls += 1
                take(preparation.prepareForCallC(S.ContextReference("absent", "1"), capsule)) shouldBe None
                contextRows.tail.foreach(r => preparation.prepareForCallC(r.reference, capsule).isSuccess shouldBe false)
                rows.foreach { row =>
                  Given(s"an independent current ${row.purpose} row and an unchanged historical context")
                  When("the actual row is updated and managed preparation reads its selected references")
                  take(installer.changeC(row, row.drift))
                  val current = take(currentInputs.loadCurrentForCallC(facts, capsule))
                  val stored = take(contexts.loadForCallC(record.reference, capsule))
                  val result = preparation.prepareForCallC(record.reference, capsule)
                  Then("compatible current values are consumed and only concrete source contradictions fail")
                  withClue(row.purpose.toString) {
                    current should not be facts
                    stored shouldBe Some(record)
                    row.purpose match {
                      case Purpose.Source | Purpose.Inventory | Purpose.Boundary | Purpose.Planning =>
                        result.isSuccess shouldBe false
                      case _ =>
                        val accepted = take(result).get
                        accepted.record.facts shouldBe current
                        row.purpose match {
                          case Purpose.Proposal => accepted.record.facts.proposal.proposal.children.head.expectedMinutes shouldBe 361
                          case Purpose.Ownership => accepted.selection.partition.boundary.records.ownership.method shouldBe "other-method"
                          case Purpose.Handoffs => accepted.request.boundaries shouldBe empty
                          case Purpose.Estimates => accepted.selection.partition.records.estimates.items.head.duration shouldBe E.Duration.Known(261)
                          case Purpose.PartitionPolicy => accepted.selection.partition.records.policy.phaseOverheadMinutes shouldBe 15
                          case _ => fail("Unexpected compatible purpose")
                        }
                    }
                  }
                  take(installer.changeC(row, row.raw))
                }
                take(preparation.prepareForCallC(record.reference, capsule)).get.request shouldBe prepared.request
                take(instances.loadForCallC(configuration, instance.identity, capsule)) shouldBe before
              }
              capsule.executionContext.dataStoreSpace.captureBinding() shouldBe prior
              Consequence.unit
          }
          val factory = ManagedCallProbeFixture.factory(probe)
          val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
          owner.add(built)
          When(s"direct managed Operation calls run the data probe in $mode mode")
          take(owner.executeOperationResponse(ManagedCallProbeFixture.request(built.name)))
          take(owner.executeOperationResponse(ManagedCallProbeFixture.request(built.name)))
          Then("only current declared data and canonical instance prepare; no lease or admission is retained")
          captured.size shouldBe 2
          preparation.prepareForCallC(record.reference, captured.last).isSuccess shouldBe false
          NativeHumanManagedHostObservation.counts(bootstrap) shouldBe (1, 0)
        } finally take(bootstrap.shutdownC())
      }
      preparedCalls shouldBe 4
    }
  }
}
