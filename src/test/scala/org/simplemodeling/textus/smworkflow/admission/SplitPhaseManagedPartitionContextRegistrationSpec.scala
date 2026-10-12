package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.{ComponentRecordStorageDeclaration, DataStore}
import org.goldenport.cncf.event.*
import org.goldenport.cncf.security.{IngressSecurityResolver, SecuritySubject}
import org.goldenport.cncf.subsystem.*
import org.goldenport.cncf.testutil.{RuntimeBindingAdmissionFixture, NativeHumanManagedHostObservation}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPlanningInputJsonV1 as J,
  SplitPhaseExplicitPartitionSemantic as S}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhasePartitionContextFixture.*
import J.Purpose

/**
 * @since   Oct.  2, 2026
 * @version Oct. 12, 2026
 */
final class SplitPhaseManagedPartitionContextRegistrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "partitionregistry", name))
  private def target(purpose: Purpose) = collection(purpose.toString.toLowerCase)
  private val workflow = SplitPhaseManagedStartAssembly.Declaration(configuration, collection("instances"), collection("continuations"))
  private val cp = SplitPhasePartitionContextReadPolicy.prefix
  private val ip = SplitPhasePlanningInputReadPolicy.prefix
  private val values = Map[String, ConfigurationValue](
    cp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    cp + "record-collection" -> ConfigurationValue.StringValue(collection("contexts").print),
    ip + "store-name" -> ConfigurationValue.StringValue("workflow")) ++ Purpose.values.map(p =>
      (ip + p.key) -> ConfigurationValue.StringValue(target(p).print))
  private def host(data: Map[String, ConfigurationValue]) = ResolvedConfiguration(HostConfiguration(data), ConfigurationTrace.empty)
  private def inputRows = Vector(
    (target(Purpose.Source), facts.proposal.source.reference.identity, take(J.encodeSourceC(facts.proposal.source))),
    (target(Purpose.Inventory), facts.proposal.inventory.reference.identity, take(J.encodeInventoryC(facts.proposal.inventory))),
    (target(Purpose.Proposal), facts.proposal.proposal.reference.identity, take(J.encodeProposalC(facts.proposal.proposal))),
    (target(Purpose.Boundary), facts.boundary.boundary.reference.identity, take(J.encodeBoundaryC(facts.boundary.boundary))),
    (target(Purpose.Ownership), facts.boundary.ownership.reference.identity, take(J.encodeOwnershipC(facts.boundary.ownership))),
    (target(Purpose.Handoffs), facts.boundary.handoffs.reference.identity, take(J.encodeHandoffsC(facts.boundary.handoffs))),
    (target(Purpose.Estimates), facts.partition.estimates.reference.identity, take(J.encodeEstimatesC(facts.partition.estimates))),
    (target(Purpose.PartitionPolicy), facts.partition.policy.reference.identity, take(J.encodePartitionPolicyC(facts.partition.policy))),
    (target(Purpose.Planning), facts.planning.reference.identity, take(J.encodePlanningC(facts.planning)))
  )

  "Managed typed partition context registration" should {
    "refuse absent or aliased host declarations and calls without retained authority" in {
      Given("pure composition metadata, not a current call or captured grant")
      val port = take(SplitPhaseManagedPartitionContextRegistration.bindC(host(values), workflow, catalog))
      When("registration lacks an Operation or composition aliases its purpose")
      Then("no context-free authority is created")
      port.registerForCallC(record, null).isSuccess shouldBe false
      port.registerForCallC(null, null).isSuccess shouldBe false
      Vector(workflow.instances, workflow.continuations, target(Purpose.Estimates)).foreach { c =>
        val bad = values.updated(cp + "record-collection", ConfigurationValue.StringValue(c.print))
        SplitPhaseManagedPartitionContextRegistration.bindC(host(bad), workflow, catalog).isSuccess shouldBe false
      }
      SplitPhaseManagedPartitionContextRegistration.bindC(host(values.removed(cp + "record-collection")), workflow, catalog)
        .isSuccess shouldBe false
    }

    "create once through an authenticated direct Operation and read the semantic context after physical reopen" in {
      Given("current inputs and a canonical instance, with a context created only on the initial host")
      val path = Files.createTempDirectory("smwf-partition-registry-").resolve("host.sqlite")
      val participant = "participant"
      val eventPolicy = take(EventPersistencePolicy.createC("partition-registry-v1", Vector(
        EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
      val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"), collection("indexes"),
        Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId, "workflow", workflow.instances, workflow.continuations))))
      def grants(c: DataStore.CollectionId) = take(ComponentRecordStorageDeclaration.createC(Vector(
        ComponentRecordStorageDeclaration.CreateGrant(SmWorkflowComponent.componentId, "workflow", c))))
      var installed = false
      var retained = Option.empty[SplitPhaseManagedPartitionContextRegistration.Port]
      for (mode <- Vector("ungranted", "foreign-grant", "untrusted", "initial", "reopen", "undeclared")) {
        val context = ExecutionContext.create()
        val declared = if (mode == "undeclared") values.removed(cp + "record-collection") else values
        val cfg = host(declared ++ Map("textus.subsystem.user-mode" -> ConfigurationValue.StringValue("standalone"),
          "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
          "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)))
        val scope = ScopeContext(ScopeKind.Subsystem, "partition-registry", None, context.observability)
        val bootstrap = take(if (mode == "ungranted") SubsystemEventStoreBootstrap.createC(eventPolicy, storage, scope, cfg)
          else SubsystemEventStoreBootstrap.createC(eventPolicy, storage, scope, cfg, None,
            org.goldenport.cncf.path.AliasResolver.empty, grants(if (mode == "foreign-grant") collection("foreign") else collection("contexts"))))
        try {
          val owner = RuntimeBindingAdmissionFixture.admit(bootstrap.subsystem)
          val registration = if (mode == "undeclared") retained.get
            else take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, workflow, catalog))
          retained = Some(registration)
          val reader = take(SplitPhasePartitionContextReadPolicy.bindC(host(values), workflow))
          val instances = CallScopedWorkflowInstances.managed(configuration, catalog.definition, workflow.instances, workflow.continuations)
          var observed = Option.empty[Consequence[C.Record]]
          var captured = Option.empty[ActionCall.Core]
          val probe: ActionCall.Core => Consequence[Unit] = capsule => {
            captured = Some(capsule)
            final class Installer extends ProcedureActionCall {
              val core = capsule
              private def withStoreC[A](f: ExecutionContext => Consequence[A]): Consequence[A] = {
                given ExecutionContext = executionContext
                val space = executionContext.dataStoreSpace
                val prior = space.captureBinding()
                try bind_component_application_datastore_c("workflow").flatMap(_ => f(executionContext))
                finally space.restoreBinding(prior)
              }
              def installC(): Consequence[Unit] = withStoreC { ctx =>
                given ExecutionContext = ctx
                inputRows.foldLeft(Consequence.unit) { case (prior, (c, id, raw)) =>
                  prior.flatMap(_ => ctx.dataStoreSpace.dataStore(c).flatMap(_.create(c, DataStore.StringEntryId(id),
                    Record.data(J.storageField -> raw))))
                }
              }.flatMap(_ => instances.createForCallC(configuration, initial, capsule))
                .flatMap(_ => instances.appendForCallC(configuration, instance.identity, initialRevision, instance.history.head, capsule))
                .map(_ => ())
              def loadContextC(id: String): Consequence[Option[Record]] = withStoreC { ctx =>
                given ExecutionContext = ctx
                val c = collection("contexts")
                ctx.dataStoreSpace.dataStore(c).flatMap(_.load(c, DataStore.StringEntryId(id)))
              }
              def execute() = Consequence.stateConflict("Private input/instance installer is not an Operation")
            }
            val installer = new Installer
            if (!installed) { take(installer.installC()); installed = true }
            val prior = capsule.executionContext.dataStoreSpace.captureBinding()
            val before = take(instances.loadForCallC(configuration, instance.identity, capsule))
            before shouldBe Some(instance)
            val actualCore = if (mode != "untrusted") capsule else {
              val forged = take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.ControlledTest, capsule.executionContext,
                Map("principal.id" -> participant, "privilege" -> "user", "cncf.security.authenticated" -> "true"))).executionContext
              val subject = SecuritySubject.from(forged.security)
              subject.isAuthenticated shouldBe true
              subject.isProviderAuthenticated shouldBe false
              subject.isTrustedLocalAuthenticated shouldBe false
              capsule.copy(executionContext = forged)
            }
            observed = Some(if (mode == "reopen")
              reader.loadForCallC(record.reference, capsule).flatMap(_.map(Consequence.success)
                .getOrElse(Consequence.stateConflict("Previously created semantic context is absent")))
            else registration.registerForCallC(record, actualCore))
            if (Set("ungranted", "foreign-grant", "untrusted").contains(mode))
              take(installer.loadContextC(record.reference.identity)) shouldBe None
            if (Set("initial", "reopen").contains(mode)) {
              take(reader.loadForCallC(record.reference, capsule)) shouldBe Some(record)

            }
            if (mode == "initial") {
              val foreign = record.copy(reference = S.ContextReference("rejected-instance", "1"),
                owner = record.owner.copy(handle = record.owner.handle.copy(instanceIdentity = InstanceIdentity("absent"))))
              registration.registerForCallC(foreign, capsule).isSuccess shouldBe false
              take(installer.loadContextC(foreign.reference.identity)) shouldBe None
            }
            take(instances.loadForCallC(configuration, instance.identity, capsule)) shouldBe before
            capsule.executionContext.dataStoreSpace.captureBinding() shouldBe prior
            Consequence.unit
          }
          val factory = ManagedCallProbeFixture.factory(probe)
          val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
          owner.add(built)
          owner.withDescriptor(GenericSubsystemDescriptor(path = Path.of("<partition-context-registry-spec>"),
            subsystemName = "partition-registry", componentBindings = Vector(GenericSubsystemComponentBinding(built.name)),
            security = Some(GenericSubsystemSecurityBinding(authentication = Some(GenericSubsystemAuthenticationBinding(
              convention = Some("enabled"), fallbackPrivilege = Some("disabled"),
              localSubject = Some(GenericSubsystemLocalSubjectBinding(id = participant, roles = Vector("user"),
                capabilities = Vector("user"), attributes = Map.empty, securityLevel = Some("user")))))))))
          val actor = take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.Fixed, built.logic.executionContext(), Map.empty)).executionContext
          When(s"the actual actor, grant and current inputs are consumed by one direct Operation in $mode mode")
          take(owner.executeOperationResponseInContext(ManagedCallProbeFixture.request(built.name), actor))
          val result = observed.get
          Then("initial Create and normal reopen succeed, while missing current authority is refused without creating data")
          if (Set("initial", "reopen").contains(mode)) take(result) shouldBe record
          else result.isSuccess shouldBe false
          registration.registerForCallC(record, captured.get).isSuccess shouldBe false
          NativeHumanManagedHostObservation.counts(bootstrap) shouldBe (1, 0)
        } finally take(bootstrap.shutdownC())
      }
      installed shouldBe true
    }
  }
}
