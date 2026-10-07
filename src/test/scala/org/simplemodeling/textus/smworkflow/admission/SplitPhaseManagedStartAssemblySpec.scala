package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
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
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.spec as spec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseProposalAssessmentCommand, SplitPhaseStartCommand, SplitPhaseStartInput}

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
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

  private final class ReadProbe {
    var calls = Vector.empty[ActionCall.Core]
    var entries = 0
    val selection = new ProfileStartAdmission.CallScopedSelectionPort {
      def loadForCallC(identity: String, core: ActionCall.Core) = {
        calls :+= core
        Consequence.success(Option.empty[ProfileStartAdmission.Selection])
      }
    }
    val entry = new SplitPhaseEntryAssessment.Port {
      def loadSourceC(phase: String, authority: String): Consequence[Option[SplitPhaseEntryAssessment.SourceSnapshot]] = {
        entries += 1
        Consequence.stateConflict("source must not be read without selection")
      }
      def loadProposalC(identity: String): Consequence[Option[SplitPhaseEntryAssessment.ProposalCandidate]] = {
        entries += 1
        Consequence.stateConflict("proposal must not be read without selection")
      }
    }
  }

  "SplitPhase canonical managed Start composition" should {
    "use current dispatch contexts for canonical instance CRUD and reject escaped or context-free access" in {
      Given("a real SQLite host and an official definition, with private instance fixture work but no human consent")
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
        val observed = new ProfileStartAdmission.CallScopedSelectionPort {
          def loadForCallC(identity: String, core: ActionCall.Core): Consequence[Option[ProfileStartAdmission.Selection]] = {
            calls :+= core
            val port = binding.get.instances
            val current = CallScopedWorkflowInstances.forCall(port, core)
            port.create(configuration, initial) shouldBe a[Consequence.Failure[?]]
            port.load(configuration, initial.identity) shouldBe a[Consequence.Failure[?]]
            port.append(configuration, initial.identity, initialRevision, entry) shouldBe a[Consequence.Failure[?]]
            current.load(configuration.copy(retentionPolicy = RetentionPolicy("foreign")), initial.identity) shouldBe
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
              val saved = take(current.append(configuration, initial.identity, initialRevision, entry))
              SplitPhaseDefinitionBinding.sameRecord(saved, expected) shouldBe true
            }
            val loaded = take(current.load(configuration, initial.identity)).get
            readbacks :+= loaded
            current.append(configuration, initial.identity, initialRevision, entry) shouldBe a[Consequence.Failure[?]]
            core.executionContext.dataStoreSpace.captureBinding() shouldBe before
            Consequence.success(None)
          }
        }
        val probe = new ReadProbe
        val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty,
          (creation, parameters, resolved) => {
            given ExecutionContext = resolved
            composition = Some(resolved)
            binding = Some(take(SplitPhaseManagedStartAssembly.bindPersistenceC(creation, declaration, catalog)))
            SplitPhaseStartServiceOperation.bindC(observed, probe.entry, binding.get.definition,
              binding.get.configuration, binding.get.instances).map(operation => spec.ServiceDefinition(
              "SplitPhaseService", spec.OperationDefinitionGroup(NonEmptyVector.of(operation))))
          }).primaryFactory
        val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
        owner.add(built)
        val start = SplitPhaseStartCommand(SplitPhaseStartInput("2", "PREVIEW", "source-authority", None, None, None),
          "absent-selection", "participant", "current-call-key")
        val request = Request.of(component = built.name, service = "SplitPhaseService", operation = "startSplitPhase",
          arguments = List(Argument("arg1", SplitPhaseStartServiceOperation.encodeCommand(start))))
        When("two canonical dispatches exercise the private instance probe before refusing Start")
        val results = Vector(owner.executeOperationResponse(request), owner.executeOperationResponse(request))
        Then("CRUD retains exact history through each incoming context without Factory-context or consent reuse")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        calls.size shouldBe 2
        calls.foreach(_.executionContext should not be theSameInstanceAs(composition.get))
        calls.head.executionContext should not be theSameInstanceAs(calls.last.executionContext)
        readbacks.foreach(record => SplitPhaseDefinitionBinding.sameRecord(record, expected) shouldBe true)
        readbacks.size shouldBe 2
        probe.entries shouldBe 0
        When("the completed capsule is reused after dispatch")
        val escaped = CallScopedWorkflowInstances.forCall(binding.get.instances, calls.last)
        Then("all CRUD refuses the ended dispatch lease even while the host pool is alive")
        Vector(escaped.create(configuration, initial), escaped.load(configuration, initial.identity),
          escaped.append(configuration, initial.identity, initialRevision, entry))
          .foreach(_ shouldBe a[Consequence.Failure[?]])
      }
    }

    "assemble exactly the two entry Operations and refuse both without human selection" in {
      Given("one real Subsystem grant and nine independently declared SplitPhase collections")
      withHost({ (host, context) =>
        val owner = RuntimeBindingAdmissionFixture.admit(host.subsystem)
        val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty,
          (creation, parameters, resolved) => {
            given ExecutionContext = resolved
            SplitPhaseManagedServiceAssembly.bindHostEntrySliceC(creation, declaration, catalog)
          }).primaryFactory
        When("the canonical Factory builds the host-only entry slice")
        val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
        owner.add(built)
        Then("only bound Start and assessment Operations are advertised and no MCP capability is implied")
        val service = built.protocol.services.services.find(_.name == "SplitPhaseService").get
        service.operations.operations.toVector.map(_.specification.name) shouldBe
          Vector("startSplitPhase", "assessSplitProposal")
        built.mcpReadyOperations shouldBe Set.empty
        built.subsystem.get should be theSameInstanceAs owner
        val start = SplitPhaseStartCommand(SplitPhaseStartInput("2", "PREVIEW", "source-authority", None, None, None),
          "absent-selection", "participant", "entry-service-key")
        val assessment = SplitPhaseProposalAssessmentCommand(start, "1", "assessment-key")
        val requests = Vector(
          "startSplitPhase" -> SplitPhaseStartServiceOperation.encodeCommand(start),
          "assessSplitProposal" -> SplitPhaseProposalAssessmentServiceOperation.encodeCommand(assessment))
          .map { (operation, wire) => Request.of(component = built.name, service = "SplitPhaseService",
            operation = operation, arguments = List(Argument("arg1", wire))) }
        When("both Operations are dispatched twice with no human selection record")
        val results = requests.flatMap(request => Vector(owner.executeOperationResponse(request), owner.executeOperationResponse(request)))
        Then("both real managed dispatches fail closed rather than fabricate consent or semantic work")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        When("a malformed assessment command is submitted")
        val invalid = Request.of(component = built.name, service = "SplitPhaseService", operation = "assessSplitProposal",
          arguments = List(Argument("arg1", "{}")))
        Then("the bound public decoder rejects it")
        owner.executeOperationResponse(invalid) shouldBe a[Consequence.Failure[?]]
      }, entryServiceRecords)
    }

    "reject missing or aliased read declarations and foreign storage grants before Service publication" in {
      Given("a real host whose Workflow grant fixes the instance and continuation collections")
      val missing = entryServiceRecords - (SplitPhaseHostProposalReadPolicy.prefix + "boundary-collection")
      val alias = entryServiceRecords.updated(SplitPhaseHostProposalReadPolicy.prefix + "boundary-collection",
        ConfigurationValue.StringValue(collection("instances").print))
      Vector(Map.empty[String, ConfigurationValue], missing, alias).foreach { records =>
        withHost({ (host, context) =>
          given ExecutionContext = context
          When("entry Service assembly consumes an incomplete or aliased host record declaration")
          val result = SplitPhaseManagedServiceAssembly.bindHostEntrySliceC(
            ComponentCreate(host.subsystem, ComponentOrigin.Builtin), declaration, catalog)
          Then("no Service is returned")
          result shouldBe a[Consequence.Failure[?]]
        }, records)
      }
      withHost({ (host, context) =>
        given ExecutionContext = context
        val create = ComponentCreate(host.subsystem, ComponentOrigin.Builtin)
        When("a fully declared Service requests an ungranted instance collection or missing dependencies")
        val results = Vector(
          SplitPhaseManagedServiceAssembly.bindHostEntrySliceC(create,
            declaration.copy(instances = collection("foreign")), catalog),
          SplitPhaseManagedServiceAssembly.bindHostEntrySliceC(null, declaration, catalog),
          SplitPhaseManagedServiceAssembly.bindHostEntrySliceC(create, null, catalog),
          SplitPhaseManagedServiceAssembly.bindHostEntrySliceC(create, declaration, null))
        Then("canonical grant and dependency checks deny every composition")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
      }, entryServiceRecords)
    }

    "read all four proposal projections through the real managed datastore without accepting baseline evidence" in {
      import SplitPhaseProposalRecordFixtures.*
      import SplitPhaseHostProposalReadPolicy.Purpose
      Given("a real host with nine disjoint collections and private unadmitted proposal fixtures")
      val bindings = Purpose.values.toVector.map(role => role -> collection("proposal" + role.toString.toLowerCase))
      val records: Map[String, ConfigurationValue] = Map(
        SplitPhaseHostSelectionReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
        SplitPhaseHostSelectionReadPolicy.prefix + "selection-collection" -> ConfigurationValue.StringValue(collection("selections").print),
        SplitPhaseHostEntryReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
        SplitPhaseHostEntryReadPolicy.prefix + "source-collection" -> ConfigurationValue.StringValue(collection("sources").print),
        SplitPhaseHostEntryReadPolicy.prefix + "proposal-collection" -> ConfigurationValue.StringValue(collection("proposals").print),
        SplitPhaseHostProposalReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow")) ++
        bindings.map((role, identity) => SplitPhaseHostProposalReadPolicy.prefix + role.key -> ConfigurationValue.StringValue(identity.print))
      withHost({ (host, context) =>
        val owner = RuntimeBindingAdmissionFixture.admit(host.subsystem)
        val reader = take(SplitPhaseHostProposalReadPolicy.bindC(owner.configuration, declaration))
        val selection = take(SplitPhaseHostSelectionReadPolicy.bindC(owner.configuration, declaration))
        val codec = SplitPhaseProposalRecordJsonV1
        val rows = Vector(
          (Purpose.Inventory, snapshot, take(codec.encodeInventoryC(snapshot, inventory))),
          (Purpose.Draft, proposal, take(codec.encodeDraftC(proposal, draft))),
          (Purpose.Validation, proposal, take(codec.encodeValidationC(proposal, baseline))),
          (Purpose.Boundary, snapshot, take(codec.encodeBoundaryC(snapshot, boundary))))
        var installed = false
        var calls = Vector.empty[ActionCall.Core]
        var readbacks = 0
        val observed = new ProfileStartAdmission.CallScopedSelectionPort {
          def loadForCallC(identity: String, capsule: ActionCall.Core): Consequence[Option[ProfileStartAdmission.Selection]] = {
            calls :+= capsule
            final class Installer extends org.goldenport.cncf.action.ProcedureActionCall {
              val core = capsule
              def installC(): Consequence[Unit] = {
                given ExecutionContext = executionContext
                val space = executionContext.dataStoreSpace
                val prior = space.captureBinding()
                try bind_component_application_datastore_c("workflow").flatMap { _ =>
                  rows.foldLeft(Consequence.unit) { (prior, row) => prior.flatMap { _ =>
                    val target = bindings.find(_._1 == row._1).get._2
                    space.dataStore(target).flatMap(_.create(target, DataStore.StringEntryId(row._2),
                      org.goldenport.record.Record.data(codec.storageField(row._1) -> row._3))).map(_ => ())
                  }}
                } finally space.restoreBinding(prior)
              }
              def execute() = Consequence.stateConflict("test projection installer has no public Operation")
            }
            if (!installed) { take(new Installer().installC()); installed = true }
            val view = SplitPhaseProposalAssessment.forCall(reader, capsule)
            take(view.loadInventoryC(snapshot)) shouldBe Some(inventory)
            take(view.loadDraftC(proposal)) shouldBe Some(draft)
            take(view.loadValidationEvidenceC(proposal)) shouldBe Some(baseline.evidence)
            take(view.loadSourceBoundaryC(snapshot)) shouldBe Some(boundary)
            Vector(view.loadInventoryC("absent"), view.loadDraftC("absent"),
              view.loadValidationEvidenceC("absent"), view.loadSourceBoundaryC("absent"))
              .foreach(_.toOption.get shouldBe None)
            readbacks += 4
            selection.loadForCallC(identity, capsule)
          }
        }
        val probe = new ReadProbe
        val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty,
          (creation, parameters, resolved) => {
            given ExecutionContext = resolved
            SplitPhaseManagedStartAssembly.bindC(creation, declaration, catalog, observed, probe.entry)
              .map(operation => spec.ServiceDefinition(name = "SplitPhaseService",
                operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))))
          }).primaryFactory
        val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
        owner.add(built)
        val command = SplitPhaseStartCommand(SplitPhaseStartInput("2", "PREVIEW", "source-authority", None, None, None),
          "absent-selection", "participant", "start-key")
        val request = Request.of(component = built.name, service = "SplitPhaseService", operation = "startSplitPhase",
          arguments = List(Argument("arg1", SplitPhaseStartServiceOperation.encodeCommand(command))))
        When("two canonical dispatches observe the four stored projections through the same current call")
        val results = Vector(owner.executeOperationResponse(request), owner.executeOperationResponse(request))
        Then("all projections read back exactly but neither stored baseline nor missing human selection grants execution")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        readbacks shouldBe 8
        probe.entries shouldBe 0
        baseline.evidence.semanticInputs shouldBe None
        When("all four readers are called again using a completed dispatch capsule")
        val escaped = Vector(reader.loadInventoryForCallC(snapshot, calls.last), reader.loadDraftForCallC(proposal, calls.last),
          reader.loadValidationForCallC(proposal, calls.last), reader.loadBoundaryForCallC(snapshot, calls.last))
        Then("every purpose is denied after the lease ends")
        escaped.foreach(_ shouldBe a[Consequence.Failure[?]])
      }, records)
    }

    "read retained source and proposal projections through the actual managed call without granting Start" in {
      import SplitPhaseEntryRecordFixtures.*
      Given("a real host and private source/proposal fixtures, with no human selection record")
      val path = Files.createTempDirectory("smwf-split-entry-reopen-").resolve("host.sqlite")
      val sources = collection("sources")
      val proposals = collection("proposals")
      val records: Map[String, ConfigurationValue] = Map(
        SplitPhaseHostSelectionReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
        SplitPhaseHostSelectionReadPolicy.prefix + "selection-collection" -> ConfigurationValue.StringValue(collection("selections").print),
        SplitPhaseHostEntryReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
        SplitPhaseHostEntryReadPolicy.prefix + "source-collection" -> ConfigurationValue.StringValue(sources.print),
        SplitPhaseHostEntryReadPolicy.prefix + "proposal-collection" -> ConfigurationValue.StringValue(proposals.print))
      withHost({ (host, context) =>
        val owner = RuntimeBindingAdmissionFixture.admit(host.subsystem)
        val reader = take(SplitPhaseHostEntryReadPolicy.bindC(owner.configuration, declaration))
        val selection = take(SplitPhaseHostSelectionReadPolicy.bindC(owner.configuration, declaration))
        var installed = false
        var calls = Vector.empty[ActionCall.Core]
        var assessments = Vector.empty[SplitPhaseEntryAssessment.Assessment]
        val input = SplitPhaseStartInput("2", "PREVIEW", "source-authority", Some(sourceValue.identity), None, Some(proposalValue.identity))
        val observed = new ProfileStartAdmission.CallScopedSelectionPort {
          def loadForCallC(identity: String, capsule: ActionCall.Core): Consequence[Option[ProfileStartAdmission.Selection]] = {
            calls :+= capsule
            // Test-only source/proposal fixture setup within an owned dispatch.
            // There is no human record installer and no public fixture Operation.
            final class Installer extends org.goldenport.cncf.action.ProcedureActionCall {
              val core = capsule
              def installC(): Consequence[Unit] = {
                given ExecutionContext = executionContext
                val space = executionContext.dataStoreSpace
                val prior = space.captureBinding()
                try for {
                  _ <- bind_component_application_datastore_c("workflow")
                  sourceStore <- space.dataStore(sources)
                  proposalStore <- space.dataStore(proposals)
                  _ <- sourceStore.create(sources, DataStore.StringEntryId("2"),
                    org.goldenport.record.Record.data(SplitPhaseEntryRecordJsonV1.sourceField -> sourceWire))
                  _ <- sourceStore.create(sources, DataStore.StringEntryId("foreign"),
                    org.goldenport.record.Record.data(SplitPhaseEntryRecordJsonV1.sourceField -> sourceWire))
                  _ <- proposalStore.create(proposals, DataStore.StringEntryId(proposalValue.identity),
                    org.goldenport.record.Record.data(SplitPhaseEntryRecordJsonV1.proposalField -> proposalWire))
                  _ <- proposalStore.create(proposals, DataStore.StringEntryId("foreign"),
                    org.goldenport.record.Record.data(SplitPhaseEntryRecordJsonV1.proposalField -> proposalWire))
                  _ <- proposalStore.create(proposals, DataStore.StringEntryId("malformed"),
                    org.goldenport.record.Record.data("foreignPayload" -> proposalWire))
                } yield ()
                finally space.restoreBinding(prior)
              }
              def execute() = Consequence.stateConflict("test entry installer is not a transport Operation")
            }
            if (!installed) {
              take(new Installer().installC())
              installed = true
            }
            val current = SplitPhaseEntryAssessment.forCall(reader, capsule)
            take(current.loadSourceC("2", "source-authority")) shouldBe Some(sourceValue)
            take(current.loadProposalC(proposalValue.identity)) shouldBe Some(proposalValue)
            take(current.loadSourceC("absent", "source-authority")) shouldBe None
            take(current.loadProposalC("absent")) shouldBe None
            current.loadSourceC("2", "other-authority") shouldBe a[Consequence.Failure[?]]
            current.loadSourceC("foreign", "source-authority") shouldBe a[Consequence.Failure[?]]
            current.loadProposalC("foreign") shouldBe a[Consequence.Failure[?]]
            current.loadProposalC("malformed") shouldBe a[Consequence.Failure[?]]
            assessments :+= take(SplitPhaseEntryAssessment.assessC(input, current))
            SplitPhaseEntryAssessment.assessC(input.copy(sourceSnapshotReference = Some("foreign")), current) shouldBe
              a[Consequence.Failure[?]]
            selection.loadForCallC(identity, capsule)
          }
        }
        var hostBound = false
        val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty,
          (creation, parameters, resolved) => {
            given ExecutionContext = resolved
            hostBound = SplitPhaseManagedStartAssembly.bindHostC(creation, declaration, catalog).isSuccess
            SplitPhaseManagedStartAssembly.bindC(creation, declaration, catalog, observed, reader)
              .map(operation => spec.ServiceDefinition(name = "SplitPhaseService",
                operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))))
          }).primaryFactory
        val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
        owner.add(built)
        hostBound shouldBe true
        val command = SplitPhaseStartCommand(input, "absent-selection", "participant", "start-key")
        val request = Request.of(component = built.name, service = "SplitPhaseService", operation = "startSplitPhase",
          arguments = List(Argument("arg1", SplitPhaseStartServiceOperation.encodeCommand(command))))
        When("two canonical dispatches read the retained projections through a private observation seam")
        val results = Vector(owner.executeOperationResponse(request), owner.executeOperationResponse(request))
        Then("exact source/proposal assessments are observed but absent human selection still refuses Start")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        assessments shouldBe Vector.fill(2)(SplitPhaseEntryAssessment.Assessment(sourceValue, Some(proposalValue)))
        calls.size shouldBe 2
        When("the same entry reader is used without a current lease or a current call")
        val escaped = Vector(reader.loadSourceForCallC("2", "source-authority", calls.last),
          reader.loadProposalForCallC(proposalValue.identity, calls.last),
          reader.loadSourceC("2", "source-authority"), reader.loadProposalC(proposalValue.identity))
        Then("all escaped reads are refused without substituting a conversation or file store")
        escaped.foreach(_ shouldBe a[Consequence.Failure[?]])
      }, records, Some(path))
      When("a newly bootstrapped host reopens the same database with only entry and selection readers")
      withHost({ (host, context) =>
        val owner = RuntimeBindingAdmissionFixture.admit(host.subsystem)
        val reader = take(SplitPhaseHostEntryReadPolicy.bindC(owner.configuration, declaration))
        val selection = take(SplitPhaseHostSelectionReadPolicy.bindC(owner.configuration, declaration))
        val input = SplitPhaseStartInput("2", "PREVIEW", "source-authority", Some(sourceValue.identity), None, Some(proposalValue.identity))
        var recovered = Option.empty[SplitPhaseEntryAssessment.Assessment]
        var captured = Option.empty[ActionCall.Core]
        val observeReadOnly = new ProfileStartAdmission.CallScopedSelectionPort {
          def loadForCallC(identity: String, core: ActionCall.Core) = {
            captured = Some(core)
            SplitPhaseEntryAssessment.assessC(input, SplitPhaseEntryAssessment.forCall(reader, core)).flatMap { assessment =>
              recovered = Some(assessment)
              selection.loadForCallC(identity, core)
            }
          }
        }
        val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty,
          (creation, parameters, resolved) => {
            given ExecutionContext = resolved
            SplitPhaseManagedStartAssembly.bindC(creation, declaration, catalog, observeReadOnly, reader)
              .map(operation => spec.ServiceDefinition(name = "SplitPhaseService",
                operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))))
          }).primaryFactory
        val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
        owner.add(built)
        val command = SplitPhaseStartCommand(input, "absent-selection", "participant", "start-key")
        val request = Request.of(component = built.name, service = "SplitPhaseService", operation = "startSplitPhase",
          arguments = List(Argument("arg1", SplitPhaseStartServiceOperation.encodeCommand(command))))
        val result = owner.executeOperationResponse(request)
        Then("both stored projections survive shutdown, while absent selection still cannot authorize Start")
        recovered shouldBe Some(SplitPhaseEntryAssessment.Assessment(sourceValue, Some(proposalValue)))
        result shouldBe a[Consequence.Failure[?]]
        reader.loadSourceForCallC("2", "source-authority", captured.get) shouldBe a[Consequence.Failure[?]]
        reader.loadProposalForCallC(proposalValue.identity, captured.get) shouldBe a[Consequence.Failure[?]]
      }, records, Some(path))
    }

    "read the actual managed selection collection during dispatch and refuse the escaped call afterwards" in {
      Given("a real SQLite host with only SplitPhase's independent selection read declaration")
      val selections = collection("selections")
      val records: Map[String, ConfigurationValue] = Map(
        SplitPhaseHostSelectionReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
        SplitPhaseHostSelectionReadPolicy.prefix + "selection-collection" -> ConfigurationValue.StringValue(selections.print))
      withHost({ (host, context) =>
        val owner = RuntimeBindingAdmissionFixture.admit(host.subsystem)
        val probe = new ReadProbe
        val reader = take(SplitPhaseHostSelectionReadPolicy.bindC(owner.configuration, declaration))
        var calls = Vector.empty[ActionCall.Core]
        var readbacks = Vector.empty[Option[ProfileStartAdmission.Selection]]
        val observed = new ProfileStartAdmission.CallScopedSelectionPort {
          def loadForCallC(identity: String, core: ActionCall.Core) = {
            calls :+= core
            reader.loadForCallC(identity, core).map { result =>
              readbacks :+= result
              result
            }
          }
        }
        val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty,
          (creation, parameters, resolved) => {
            given ExecutionContext = resolved
            SplitPhaseManagedStartAssembly.bindC(creation, declaration, catalog, observed, probe.entry)
              .map(operation => spec.ServiceDefinition(name = "SplitPhaseService",
                operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))))
          }).primaryFactory
        val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
        owner.add(built)
        val command = SplitPhaseStartCommand(SplitPhaseStartInput("2", "PREVIEW", "source-authority",
          None, None, None), "absent-selection", "participant", "start-key")
        val request = Request.of(component = built.name, service = "SplitPhaseService", operation = "startSplitPhase",
          arguments = List(Argument("arg1", SplitPhaseStartServiceOperation.encodeCommand(command))))
        When("two actual managed Start calls read the declared empty collection")
        val results = Vector(owner.executeOperationResponse(request), owner.executeOperationResponse(request))
        Then("successful datastore reads return absence, so no source work is authorized")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        readbacks shouldBe Vector(None, None)
        calls.size shouldBe 2
        calls.foreach(_.component.get should be theSameInstanceAs built)
        probe.entries shouldBe 0
        When("the completed call capsule is used for another selection read")
        val escaped = reader.loadForCallC(command.selectionIdentity, calls.last)
        Then("the expired dispatch lease rejects it even though the host declaration still exists")
        escaped shouldBe a[Consequence.Failure[?]]
        reader.loadC(command.selectionIdentity) shouldBe a[Consequence.Failure[?]]
      }, records)
    }

    "bind inside canonical Factory initialization and dispatch with the owning component's current call" in {
      Given("a real SQLite Subsystem host grant and an official SplitPhase runtime catalog")
      withHost { (host, context) =>
        val owner = RuntimeBindingAdmissionFixture.admit(host.subsystem)
        val probe = new ReadProbe
        var composition: Option[ExecutionContext] = None
        val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty,
          (creation, parameters, resolved) => {
            composition = Some(resolved)
            given ExecutionContext = resolved
            SplitPhaseManagedStartAssembly.bindC(creation, declaration, catalog, probe.selection, probe.entry)
              .map(operation => spec.ServiceDefinition(name = "SplitPhaseService",
                operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))))
          }).primaryFactory
        When("canonical component construction binds the storage-backed Start Operation")
        val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
        owner.add(built)
        Then("the bound service retains host ownership without advertising MCP or invoking source authority")
        built.subsystem.get should be theSameInstanceAs owner
        built.scopeContext.dataStoreSpace should be theSameInstanceAs composition.get.dataStoreSpace
        built.mcpReadyOperations shouldBe Set.empty
        probe.calls shouldBe empty
        probe.entries shouldBe 0

        val command = SplitPhaseStartCommand(SplitPhaseStartInput("2", "PREVIEW", "source-authority",
          None, None, None), "absent-selection", "participant", "start-key")
        val request = Request.of(component = built.name, service = "SplitPhaseService", operation = "startSplitPhase",
          arguments = List(Argument("arg1", SplitPhaseStartServiceOperation.encodeCommand(command))))
        When("the owning Subsystem dispatches Start twice without a human selection record")
        val results = Vector(owner.executeOperationResponse(request), owner.executeOperationResponse(request))
        Then("the real call capsule reaches selection lookup and neither dispatch reads source or starts work")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        probe.calls.size shouldBe 2
        probe.calls.foreach { call =>
          call.component.get should be theSameInstanceAs built
          call.component.get.subsystem.get should be theSameInstanceAs owner
          call.executionContext should not be theSameInstanceAs(composition.get)
        }
        probe.entries shouldBe 0
      }
    }

    "reject ungranted collections and incomplete host dependencies before returning an Operation" in {
      Given("a real host whose Workflow grant names only the admitted instance and continuation collections")
      withHost { (host, context) =>
        given ExecutionContext = context
        val create = ComponentCreate(host.subsystem, ComponentOrigin.Builtin)
        val probe = new ReadProbe
        When("composition requests foreign collections or absent selection, entry or catalog dependencies")
        val results = Vector(
          SplitPhaseManagedStartAssembly.bindC(create, declaration.copy(instances = collection("foreign")), catalog,
            probe.selection, probe.entry),
          SplitPhaseManagedStartAssembly.bindC(create, declaration.copy(continuations = collection("foreign")), catalog,
            probe.selection, probe.entry),
          SplitPhaseManagedStartAssembly.bindC(create, declaration, catalog, null, probe.entry),
          SplitPhaseManagedStartAssembly.bindC(create, declaration, catalog, probe.selection, null),
          SplitPhaseManagedStartAssembly.bindC(create, declaration, null, probe.selection, probe.entry))
        Then("all compositions fail closed without an execution lookup or alternate storage route")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        probe.calls shouldBe empty
        probe.entries shouldBe 0
      }
    }
  }

  private def entryServiceRecords: Map[String, ConfigurationValue] = {
    val selection = SplitPhaseHostSelectionReadPolicy.prefix
    val entry = SplitPhaseHostEntryReadPolicy.prefix
    val proposal = SplitPhaseHostProposalReadPolicy.prefix
    Map(
      selection + "store-name" -> ConfigurationValue.StringValue("workflow"),
      selection + "selection-collection" -> ConfigurationValue.StringValue(collection("selections").print),
      entry + "store-name" -> ConfigurationValue.StringValue("workflow"),
      entry + "source-collection" -> ConfigurationValue.StringValue(collection("sources").print),
      entry + "proposal-collection" -> ConfigurationValue.StringValue(collection("proposals").print),
      proposal + "store-name" -> ConfigurationValue.StringValue("workflow")) ++
      SplitPhaseHostProposalReadPolicy.Purpose.values.toVector.map { role =>
        proposal + role.key -> ConfigurationValue.StringValue(collection("proposal" + role.toString.toLowerCase).print)
      }
  }
}
