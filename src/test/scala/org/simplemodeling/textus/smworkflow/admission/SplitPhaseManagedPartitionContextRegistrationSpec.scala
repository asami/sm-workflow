package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.{ComponentRecordStorage, ComponentRecordStorageDeclaration, DataStore}
import org.goldenport.cncf.event.*
import org.goldenport.cncf.security.{IngressSecurityResolver, SecuritySubject}
import org.goldenport.cncf.subsystem.*
import org.goldenport.cncf.testutil.{RuntimeBindingAdmissionFixture, NativeHumanManagedHostObservation}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseStartCommand, SplitPhaseStartInput}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionContextJsonV1 as CJ, SplitPhaseExplicitPlanningInputJsonV1 as J,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPartitionComputation as E}
import SplitPhaseExplicitPartitionFixture.take
import SplitPhasePartitionContextFixture.*
import J.Purpose

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
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

    "retain through real authenticated Service CLI and recover committed uncertain reads after physical reopen" in {
      Given("only privately installed current inputs and canonical instance, no preinstalled context or human consent")
      val path = Files.createTempDirectory("smwf-partition-registry-").resolve("host.sqlite")
      val eventPolicy = take(EventPersistencePolicy.createC("partition-registry-v1", Vector(
        EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
      val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"), collection("indexes"),
        Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId, "workflow", workflow.instances, workflow.continuations))))
      def grants(c: DataStore.CollectionId) = take(ComponentRecordStorageDeclaration.createC(Vector(
        ComponentRecordStorageDeclaration.CreateGrant(SmWorkflowComponent.componentId, "workflow", c))))
      val command = SplitPhaseStartCommand(SplitPhaseStartInput("77", "PREVIEW", "authority", None, None, None),
        "selection", "participant", "start-key")
      val interrupted = for (fault <- Vector("missing", "failed", "divergent"); cli <- Vector(false, true)) yield
        (fault, cli, record.copy(reference = S.ContextReference(s"interrupted-$fault-$cli", "1")))
      var installed = false
      var interruptedCreates = 0
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
          val preparation = take(SplitPhaseManagedPartitionPreparation.bindC(host(values), workflow, catalog))
          val reader = take(SplitPhasePartitionContextReadPolicy.bindC(host(values), workflow))
          val instances = CallScopedWorkflowInstances.managed(configuration, catalog.definition, workflow.instances, workflow.continuations)
          var submitted = record
          var observed = Option.empty[Consequence[C.Record]]
          var captured = Option.empty[ActionCall.Core]
          var masked = Option.empty[String]
          var changeEstimate = Option.empty[String]
          val probe = new ProfileStartAdmission.CallScopedSelectionPort {
            def loadForCallC(identity: String, core: ActionCall.Core): Consequence[Option[ProfileStartAdmission.Selection]] = {
              captured = Some(core)
              final class Installer extends ProcedureActionCall {
                val core = captured.get
                def withStoreC[A](f: ExecutionContext => Consequence[A]): Consequence[A] = {
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
                }.flatMap(_ => instances.createForCallC(configuration, initial, core))
                  .flatMap(_ => instances.appendForCallC(configuration, instance.identity, initialRevision, instance.history.head, core))
                  .map(_ => ())
                def changeC(raw: String): Consequence[Unit] = withStoreC { ctx =>
                  given ExecutionContext = ctx
                  val c = target(Purpose.Estimates)
                  ctx.dataStoreSpace.dataStore(c).flatMap(_.update(c, DataStore.StringEntryId(facts.partition.estimates.reference.identity),
                    Record.data(J.storageField -> raw)))
                }
                def loadContextC(id: String): Consequence[Option[Record]] = withStoreC { ctx =>
                  given ExecutionContext = ctx
                  val c = collection("contexts")
                  ctx.dataStoreSpace.dataStore(c).flatMap(_.load(c, DataStore.StringEntryId(id)))
                }
                def execute() = Consequence.stateConflict("Private input/instance installer is not an Operation")
              }
              val installer = new Installer
              if (!installed) { take(installer.installC()); installed = true }
              changeEstimate.foreach(raw => take(installer.changeC(raw)))
              val prior = core.executionContext.dataStoreSpace.captureBinding()
              val before = take(instances.loadForCallC(configuration, instance.identity, core))
              before shouldBe Some(instance)
              val actualCore = if (mode != "untrusted") core else {
                val forged = take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.ControlledTest, core.executionContext,
                  Map("principal.id" -> command.participantIdentity, "privilege" -> "user", "cncf.security.authenticated" -> "true"))).executionContext
                val subject = SecuritySubject.from(forged.security)
                subject.isAuthenticated shouldBe true
                subject.isProviderAuthenticated shouldBe false
                subject.isTrustedLocalAuthenticated shouldBe false
                core.copy(executionContext = forged)
              }
              observed = Some(masked match {
                case None => registration.registerForCallC(submitted, actualCore)
                case Some(fault) => for {
                  capability <- ComponentRecordStorage.bindForCallC(core, "workflow", collection("contexts"),
                    DataStore.StringEntryId(submitted.reference.identity))
                  prepared <- preparation.prepareRecordForCallC(submitted, core)
                  result <- SplitPhasePartitionContextRetention.retainC(prepared, new SplitPhasePartitionContextRetention.Port {
                    private var created = false
                    def createC(row: Record) = capability.createC(row).map { _ => created = true; interruptedCreates += 1 }
                    def loadC() = if (!created) capability.loadC() else fault match {
                      case "missing" => Consequence.success(None)
                      case "failed" => Consequence.stateConflict("private committed readback interruption")
                      case "divergent" => CJ.encodeC(submitted.copy(owner = submitted.owner.copy(
                        presentation = submitted.owner.presentation.copy(currentSituation = "other-observation")))).map(raw =>
                        Some(Record.data(CJ.storageField -> raw)))
                    }
                  })
                } yield result
              })
              if (Set("ungranted", "foreign-grant", "untrusted").contains(mode))
                take(installer.loadContextC(record.reference.identity)) shouldBe None
              if (Set("initial", "reopen").contains(mode) && observed.get.isSuccess)
                take(reader.loadForCallC(submitted.reference, core)) shouldBe Some(submitted)
              if (masked.nonEmpty) take(reader.loadForCallC(submitted.reference, core)) shouldBe Some(submitted)
              if (submitted.reference.identity.startsWith("rejected-"))
                take(installer.loadContextC(submitted.reference.identity)) shouldBe None
              take(instances.loadForCallC(configuration, instance.identity, core)) shouldBe before
              core.executionContext.dataStoreSpace.captureBinding() shouldBe prior
              Consequence.success(None) // Context data never grants selection, Start or WorkOrder.
            }
          }
          val entry = new SplitPhaseEntryAssessment.Port {
            def loadSourceC(phase: String, authority: String) = Consequence.stateConflict("No old source fallback in typed registration")
            def loadProposalC(reference: String) = Consequence.stateConflict("No old proposal fallback in typed registration")
          }
          val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty, (creation, parameters, resolved) => {
            given ExecutionContext = resolved
            SplitPhaseManagedStartAssembly.bindC(creation, workflow, catalog, probe, entry).map(operation =>
              spec.ServiceDefinition("SplitPhaseService", spec.OperationDefinitionGroup(NonEmptyVector.of(operation))))
          }).primaryFactory
          val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
          owner.add(built)
          owner.withDescriptor(GenericSubsystemDescriptor(path = Path.of("<partition-context-registry-spec>"),
            subsystemName = "partition-registry", componentBindings = Vector(GenericSubsystemComponentBinding(built.name)),
            security = Some(GenericSubsystemSecurityBinding(authentication = Some(GenericSubsystemAuthenticationBinding(
              convention = Some("enabled"), fallbackPrivilege = Some("disabled"),
              localSubject = Some(GenericSubsystemLocalSubjectBinding(id = command.participantIdentity, roles = Vector("user"),
                capabilities = Vector("user"), attributes = Map.empty, securityLevel = Some("user")))))))))
          val actor = take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.Fixed, built.logic.executionContext(), Map.empty)).executionContext
          val wire = SplitPhaseStartServiceOperation.encodeCommand(command)
          val request = Request.of(component = built.name, service = "SplitPhaseService", operation = "startSplitPhase",
            arguments = List(Argument("arg1", wire)))
          def invoke(cli: Boolean): Consequence[C.Record] = {
            observed = None
            val response = if (cli) new CncfRuntime().executeCommandResponse(owner,
              Array(s"${built.name}/SplitPhaseService/startSplitPhase", "--json", wire))
              else owner.executeOperationResponseInContext(request, actor)
            response.isSuccess shouldBe false
            observed.getOrElse(fail(s"Registry probe not reached: mode=$mode cli=$cli response=$response"))
          }
          When(s"actual native actor, captured grant and typed current data meet in $mode Service/CLI calls")
          val allowed = Set("initial", "reopen").contains(mode)
          for (cli <- Vector(false, true)) {
            val result = invoke(cli)
            if (allowed) take(result) shouldBe record else result.isSuccess shouldBe false
          }
          if (allowed) {
            Then("full context and revision conflicts are refused without overwriting the original or history")
            Vector(record.copy(reference = record.reference.copy(revision = "2")),
              record.copy(owner = record.owner.copy(presentation = record.owner.presentation.copy(currentSituation = "other-situation"))))
              .foreach { conflict => submitted = conflict; invoke(false).isSuccess shouldBe false }
            submitted = record
            take(invoke(false)) shouldBe record
            submitted = record.copy(reference = S.ContextReference("rejected-instance", "1"),
              owner = record.owner.copy(handle = record.owner.handle.copy(instanceIdentity = InstanceIdentity("absent"))))
            invoke(false).isSuccess shouldBe false
            submitted = record.copy(reference = S.ContextReference("rejected-drift", "1"))
            val drift = facts.partition.estimates.copy(items = facts.partition.estimates.items.updated(0,
              facts.partition.estimates.items.head.copy(duration = E.Duration.Known(261))))
            changeEstimate = Some(take(J.encodeEstimatesC(drift)))
            invoke(false).isSuccess shouldBe false
            changeEstimate = Some(take(J.encodeEstimatesC(facts.partition.estimates)))
            submitted = record
            take(invoke(false)) shouldBe record
            changeEstimate = None
          }
          if (mode == "initial") {
            When("actual SQLite Create commits but its immediate observed read is masked before shutdown")
            for ((fault, cli, value) <- interrupted) {
              submitted = value
              masked = Some(fault)
              invoke(cli) match {
                case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets.collect {
                  case org.goldenport.observation.Descriptor.Facet.Reason(name) => name
                } should contain("transaction-indeterminate")
                case other => fail(s"Uncertain committed context published success: $other")
              }
            }
            masked = None
            interruptedCreates shouldBe interrupted.size
          }
          if (mode == "reopen") {
            When("a new physical host normally observes each committed uncertain context, without reinstalling it")
            for ((_, cli, value) <- interrupted) {
              submitted = value
              take(invoke(cli)) shouldBe value
              take(invoke(!cli)) shouldBe value
              interruptedCreates shouldBe interrupted.size
            }
          }
          submitted = record
          registration.registerForCallC(record, captured.get).isSuccess shouldBe false
          NativeHumanManagedHostObservation.counts(bootstrap) shouldBe (1, 0)
        } finally take(bootstrap.shutdownC())
      }
      installed shouldBe true
      interruptedCreates shouldBe interrupted.size
    }
  }
}
