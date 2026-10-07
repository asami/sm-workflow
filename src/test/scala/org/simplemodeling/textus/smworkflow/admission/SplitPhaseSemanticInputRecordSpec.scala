package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, parser}
import cats.data.NonEmptyVector
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
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.spec as spec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseStartCommand, SplitPhaseStartInput}
import SplitPhaseSemanticInputRecordJsonV1.*
import SplitPhaseProposalPrecheck.{Child, ProposalDraft, SourceInventory}
import SplitPhaseEntryAssessment.{Assessment, SourceSnapshot, ProposalCandidate}

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseSemanticInputRecordSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"semantic baseline fixture failed: $failure")
  }
  private val draft = ProposalDraft("proposal", "77", "source-authority", "a" * 64,
    Vector(Child("77", Vector("work-1", "work-2"), 360, true), Child("77.1", Vector("work-3"), 360, true)))
  private val inventory = SourceInventory("77", "a" * 64, Vector("work-1", "work-2", "work-3"), Set("77"))
  private val assessment = Assessment(SourceSnapshot("snapshot", "77", "source-authority", "revision", "a" * 64),
    Some(ProposalCandidate("proposal", "77", "source-authority", "a" * 64, SplitPhaseProposalPrecheck.digestOf(draft))))
  private val evidence = SplitPhaseProposalValidationFixture.evidenceFor(draft)
  private val pendingEvidence = evidence.copy(calibrationRequests = evidence.calibrationRequests.map(request =>
    if (request.workItem.identity == "work-2") request.copy(workItem = request.workItem.copy(workClassKey = "novel-class"))
    else request))
  private val boundary = SplitPhaseSourceBoundary.Contract("a" * 64, "77", "source-authority",
    inventory.orderedUnfinishedItems.zipWithIndex.map { case (item, index) =>
      SplitPhaseSourceBoundary.Item(item, Vector(s"close-$item"), inventory.orderedUnfinishedItems.lift(index - 1).toVector,
        "source-authority")
    }, Set("authority-edge-work-1", "authority-edge-work-2"))
  private val facts = Facts(assessment, inventory, draft,
    SplitPhaseProposalRecordJsonV1.BaselineValidation(pendingEvidence.calibrationRequests, pendingEvidence.semanticHandoffs,
      pendingEvidence.validationOwnership, pendingEvidence.partitionPolicy, pendingEvidence.semanticEdges), boundary)
  private def record = InputRecord("semantic-input-1", "1", "continuation-1", facts,
    take(SplitPhaseSemanticInputs.prepareC(assessment, inventory, draft, pendingEvidence, boundary)).request)
  private def alter(raw: String)(f: Json => Json): String = schema + ":" + f(parser.parse(raw.drop(schema.length + 1)).toOption.get).noSpaces
  private def nested(value: Json, path: List[String])(f: Json => Json): Json = path match {
    case Nil => f(value)
    case head :: tail => value.mapObject(fields => fields.add(head, nested(fields(head).get, tail)(f)))
  }
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "semanticinput", name))
  private val configuration = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("split-input")),
    MigrationPolicy("v1"), RetentionPolicy("v1"), LeasePolicy("v1"), DeliveryConfiguration.SameStore(WorkflowStoreIdentity("split-input")))
  private val workflow = SplitPhaseManagedStartAssembly.Declaration(configuration, collection("instances"), collection("continuations"))
  private val prefix = SplitPhaseHostSemanticInputReadPolicy.prefix
  private val values: Map[String, ConfigurationValue] = Map(
    prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    prefix + "input-collection" -> ConfigurationValue.StringValue(collection("inputs").print),
    SplitPhaseHostSelectionReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    SplitPhaseHostSelectionReadPolicy.prefix + "selection-collection" -> ConfigurationValue.StringValue(collection("selections").print),
    SplitPhaseHostEntryReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    SplitPhaseHostEntryReadPolicy.prefix + "source-collection" -> ConfigurationValue.StringValue(collection("sources").print),
    SplitPhaseHostEntryReadPolicy.prefix + "proposal-collection" -> ConfigurationValue.StringValue(collection("proposals").print),
    SplitPhaseHostProposalReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow")) ++
    SplitPhaseHostProposalReadPolicy.Purpose.values.toVector.map(purpose =>
      SplitPhaseHostProposalReadPolicy.prefix + purpose.key -> ConfigurationValue.StringValue(collection(purpose.toString).print))
  private def host(data: Map[String, ConfigurationValue]) = ResolvedConfiguration(HostConfiguration(data), ConfigurationTrace.empty)

  "SplitPhase semantic baseline projection" should {
    "round-trip complete facts and reconstruct admission rather than decode an Intent" in {
      Given("an existing semantic fixture with unresolved work, not an issued production WorkOrder")
      val original = record
      When("the data projection is decoded and independently equal current facts are supplied")
      val loaded = take(decodeC(original.reference, take(encodeC(original))))
      val rebuilt = take(reconstructC(loaded, facts))
      Then("baseline facts round-trip exactly and only ordinary admission constructs Intent")
      loaded shouldBe original
      loaded.facts.validation.evidence.semanticInputs shouldBe None
      rebuilt.request shouldBe original.request
    }

    "refuse complete baseline drift even when the stored request is unchanged" in {
      Given("one frozen request and complete typed facts")
      val stored = record
      When("current source, calibration, inventory or boundary facts differ without rewriting the request")
      val changed = Vector(facts.copy(assessment = assessment.copy(source = assessment.source.copy(revision = "other"))),
        facts.copy(inventory = inventory.copy(occupiedPhaseIdentities = Set("77", "88"))),
        facts.copy(validation = facts.validation.copy(partitionPolicy = facts.validation.partitionPolicy.copy(version = "other"))),
        facts.copy(boundary = boundary.copy(authorizedHandoffReferences = Set("foreign"))))
      Then("every changed fact is refused before a new admitted Intent is returned")
      changed.foreach(value => reconstructC(stored, value) shouldBe a[Consequence.Failure[?]])
      reconstructC(stored.copy(request = stored.request.copy(contextDigest = "foreign")), facts) shouldBe a[Consequence.Failure[?]]
    }

    "deny recursive extras, missing provenance, unsupported schema and serialized admission" in {
      Given("one valid closed baseline storage envelope")
      val stored = record
      val raw = take(encodeC(stored))
      When("untrusted storage adds private admission or removes required nested facts")
      val invalid = Vector(
        alter(raw)(_.mapObject(_.add("authority", Json.fromBoolean(true)))),
        alter(raw)(_.mapObject(_.add("schemaVersion", Json.fromString("v2")))),
        alter(raw)(json => nested(json, List("payload"))(_.mapObject(_.remove("revision")))),
        alter(raw)(json => nested(json, List("payload", "facts", "validation"))(
          _.mapObject(_.add("semanticInputs", Json.obj("admitted" -> Json.fromBoolean(true)))))),
        alter(raw)(json => nested(json, List("payload", "facts", "inventory"))(
          _.mapObject(_.remove("orderedUnfinishedItems")))))
      Then("no ignored field, defaulted provenance or private admitted witness is accepted")
      invalid.foreach(value => decodeC(stored.reference, value) shouldBe a[Consequence.Failure[?]])
      decodeC("foreign", raw) shouldBe a[Consequence.Failure[?]]
      encodeC(stored.copy(revision = " ")) shouldBe a[Consequence.Failure[?]]
      encodeC(stored.copy(continuationReference = "")) shouldBe a[Consequence.Failure[?]]
      decodeC(stored.reference, schema + ":" + "x" * (1024 * 1024)) shouldBe a[Consequence.Failure[?]]
    }
  }

  "SplitPhase managed semantic baseline read declaration" should {
    "reconstruct from real managed records across Service CLI and pool reopen without granting Start" in {
      Given("private immutable baseline records in a canonical SQLite host, not a human invocation or issued work")
      val path = Files.createTempDirectory("smwf-split-input-reopen-").resolve("host.sqlite")
      val original = record
      val raw = take(encodeC(original))
      val source = assessment.source
      val proposal = assessment.proposalCandidate.get
      val sourceRaw = SplitPhaseEntryRecordJsonV1.sourcePrefix + Json.obj(
        "schemaVersion" -> Json.fromString(SplitPhaseEntryRecordJsonV1.sourceSchema),
        "identity" -> Json.fromString(source.identity), "sourcePhaseIdentity" -> Json.fromString(source.sourcePhaseIdentity),
        "authorityReference" -> Json.fromString(source.authorityReference), "revision" -> Json.fromString(source.revision),
        "digest" -> Json.fromString(source.digest)).noSpaces
      val proposalRaw = SplitPhaseEntryRecordJsonV1.proposalPrefix + Json.obj(
        "schemaVersion" -> Json.fromString(SplitPhaseEntryRecordJsonV1.proposalSchema),
        "identity" -> Json.fromString(proposal.identity), "sourcePhaseIdentity" -> Json.fromString(proposal.sourcePhaseIdentity),
        "authorityReference" -> Json.fromString(proposal.authorityReference),
        "sourceSnapshotDigest" -> Json.fromString(proposal.sourceSnapshotDigest), "digest" -> Json.fromString(proposal.digest)).noSpaces
      import SplitPhaseHostProposalReadPolicy.Purpose
      val dataRows = Vector(
        (collection("inputs"), original.reference, storageField, raw),
        (collection("inputs"), "foreign", storageField, raw),
        (collection("inputs"), "malformed", storageField, "not-json"),
        (collection("inputs"), "drift", storageField, take(encodeC(original.copy(reference = "drift",
          facts = facts.copy(assessment = assessment.copy(source = source.copy(revision = "foreign"))))))),
        (collection("inputs"), "missing-draft", storageField, take(encodeC(original.copy(reference = "missing-draft",
          facts = facts.copy(draft = draft.copy(identity = "absent")))))),
        (collection("sources"), source.sourcePhaseIdentity, SplitPhaseEntryRecordJsonV1.sourceField, sourceRaw),
        (collection("proposals"), proposal.identity, SplitPhaseEntryRecordJsonV1.proposalField, proposalRaw),
        (collection(Purpose.Inventory.toString), source.identity, SplitPhaseProposalRecordJsonV1.storageField(Purpose.Inventory),
          take(SplitPhaseProposalRecordJsonV1.encodeInventoryC(source.identity, inventory))),
        (collection(Purpose.Draft.toString), draft.identity, SplitPhaseProposalRecordJsonV1.storageField(Purpose.Draft),
          take(SplitPhaseProposalRecordJsonV1.encodeDraftC(draft.identity, draft))),
        (collection(Purpose.Validation.toString), draft.identity, SplitPhaseProposalRecordJsonV1.storageField(Purpose.Validation),
          take(SplitPhaseProposalRecordJsonV1.encodeValidationC(draft.identity, facts.validation))),
        (collection(Purpose.Boundary.toString), source.identity, SplitPhaseProposalRecordJsonV1.storageField(Purpose.Boundary),
          take(SplitPhaseProposalRecordJsonV1.encodeBoundaryC(source.identity, boundary))))
      val catalog = take(SplitPhaseRuntimeCatalog.loadC())
      val policy = take(EventPersistencePolicy.createC("split-input-read-v1", Vector(
        EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
      val eventStore = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"),
        collection("indexes"), Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId,
          configuration.workflowStore.name, workflow.instances, workflow.continuations))))
      var installed = 0
      var retainedReader = Option.empty[SplitPhaseSemanticInputDataStorePort]
      for (mode <- Vector("initial", "reopen", "undeclared")) {
        if (mode != "initial") When(s"a new host reopens the same SQLite database in $mode mode without an installer")
        val context = ExecutionContext.create()
        val declarations = if (mode == "undeclared") values.removed(prefix + "input-collection") else values
        val configurationValues = declarations ++ Map(
          "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
          "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString))
        val bootstrap = take(SubsystemEventStoreBootstrap.createC(policy, eventStore,
          ScopeContext(ScopeKind.Subsystem, "split-input-read", None, context.observability), host(configurationValues)))
        try {
          val owner = RuntimeBindingAdmissionFixture.admit(bootstrap.subsystem)
          val reader = if (mode == "undeclared") retainedReader.get
            else take(SplitPhaseHostSemanticInputReadPolicy.bindC(owner.configuration, workflow))
          retainedReader = Some(reader)
          val entry = take(SplitPhaseHostEntryReadPolicy.bindC(owner.configuration, workflow))
          val proposals = take(SplitPhaseHostProposalReadPolicy.bindC(owner.configuration, workflow))
          val selection = take(SplitPhaseHostSelectionReadPolicy.bindC(owner.configuration, workflow))
          var captured = Vector.empty[ActionCall.Core]
          var reconstructed = 0
          val observed = new ProfileStartAdmission.CallScopedSelectionPort {
            def loadForCallC(identity: String, capsule: ActionCall.Core): Consequence[Option[ProfileStartAdmission.Selection]] = {
              captured :+= capsule
              final class Installer extends ProcedureActionCall {
                val core = capsule
                def installC(): Consequence[Unit] = {
                  given ExecutionContext = executionContext
                  val space = executionContext.dataStoreSpace
                  val previous = space.captureBinding()
                  try bind_component_application_datastore_c("workflow").flatMap { _ =>
                    dataRows.foldLeft(Consequence.unit) { (prior, row) => prior.flatMap { _ =>
                      space.dataStore(row._1).flatMap(_.create(row._1, DataStore.StringEntryId(row._2),
                        org.goldenport.record.Record.data(row._3 -> row._4))).map(_ => ())
                    }}
                  } finally space.restoreBinding(previous)
                }
                def execute() = Consequence.stateConflict("private baseline installer is not an Operation")
              }
              if (mode == "initial" && installed == 0) { take(new Installer().installC()); installed += 1 }
              val previous = capsule.executionContext.dataStoreSpace.captureBinding()
              if (mode == "undeclared") {
                reader.loadForCallC(original.reference, capsule) shouldBe a[Consequence.Failure[?]]
                reader.reconstructForCallC(original.reference, capsule, entry, proposals) shouldBe a[Consequence.Failure[?]]
              } else {
                take(reader.loadForCallC(original.reference, capsule)) shouldBe Some(original)
                reader.loadRecoveryForCallC(original.reference, capsule) shouldBe a[Consequence.Failure[?]]
                take(reader.loadForCallC("absent", capsule)) shouldBe None
                take(reader.reconstructForCallC("absent", capsule, entry, proposals)) shouldBe None
                Vector("foreign", "malformed").foreach(reference =>
                  reader.loadForCallC(reference, capsule) shouldBe a[Consequence.Failure[?]])
                Vector("drift", "missing-draft").foreach(reference =>
                  reader.reconstructForCallC(reference, capsule, entry, proposals) shouldBe a[Consequence.Failure[?]])
                val wrongCollection = new SplitPhaseSemanticInputDataStorePort(
                  SplitPhaseHostSemanticInputReadPolicy.Declaration("workflow", collection("Draft")))
                wrongCollection.loadForCallC(original.reference, capsule) shouldBe a[Consequence.Failure[?]]
                reader.loadForCallC(original.reference, capsule.copy(component = None)) shouldBe a[Consequence.Failure[?]]
                reader.loadForCallC(original.reference, capsule.copy(executionContext = null)) shouldBe a[Consequence.Failure[?]]
                val intent = take(reader.reconstructForCallC(original.reference, capsule, entry, proposals)).get
                intent.request shouldBe original.request
                reconstructed += 1
              }
              capsule.executionContext.dataStoreSpace.captureBinding() shouldBe previous
              selection.loadForCallC(identity, capsule)
            }
          }
          val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty,
            (creation, parameters, resolved) => {
              given ExecutionContext = resolved
              SplitPhaseManagedStartAssembly.bindC(creation, workflow, catalog, observed, entry)
                .map(operation => spec.ServiceDefinition("SplitPhaseService", spec.OperationDefinitionGroup(NonEmptyVector.of(operation))))
            }).primaryFactory
          val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
          owner.add(built)
          val command = SplitPhaseStartCommand(SplitPhaseStartInput("77", "PREVIEW", "source-authority",
            Some(source.identity), None, Some(proposal.identity)), "absent-selection", "participant", "baseline-probe-key")
          val wire = SplitPhaseStartServiceOperation.encodeCommand(command)
          When(s"real Service and CLI dispatch observe baseline records in $mode mode")
          val serviceResult = owner.executeOperationResponse(Request.of(component = built.name, service = "SplitPhaseService",
            operation = "startSplitPhase", arguments = List(Argument("arg1", wire))))
          val cliResult = new CncfRuntime().executeCommandResponse(owner,
            Array(s"${built.name}/SplitPhaseService/startSplitPhase", "--json", wire))
          Then("baseline reconstruction never grants Start, and each dispatch owns a distinct expiring capsule")
          serviceResult shouldBe a[Consequence.Failure[?]]
          cliResult shouldBe a[Consequence.Failure[?]]
          captured.size shouldBe 2
          captured.head.executionContext should not be theSameInstanceAs(captured.last.executionContext)
          reconstructed shouldBe (if (mode == "undeclared") 0 else 2)
          reader.loadC(original.reference) shouldBe a[Consequence.Failure[?]]
          reader.loadForCallC(original.reference, captured.last) shouldBe a[Consequence.Failure[?]]
          reader.reconstructForCallC(original.reference, captured.last, entry, proposals) shouldBe a[Consequence.Failure[?]]
        } finally take(bootstrap.shutdownC())
      }
      Then("the private installer ran once; reopened hosts reconstructed only from managed stored facts")
      installed shouldBe 1
    }

    "bind ten disjoint purposes without granting context-free reads" in {
      Given("exact host declarations without a physical provider or incoming call")
      When("pure composition binds the data-only baseline reader")
      val port = take(SplitPhaseHostSemanticInputReadPolicy.bindC(host(values), workflow))
      Then("declared collection admission succeeds but context-free or null-call reads refuse")
      SplitPhaseHostSemanticInputReadPolicy.admitC(host(values), "workflow", collection("inputs")) shouldBe a[Consequence.Success[?]]
      port.loadC("semantic-input-1") shouldBe a[Consequence.Failure[?]]
      port.loadForCallC("semantic-input-1", null) shouldBe a[Consequence.Failure[?]]
      port.reconstructForCallC("semantic-input-1", null, null, null) shouldBe a[Consequence.Failure[?]]
    }

    "refuse incomplete configuration, foreign store and every protected collection alias" in {
      Given("the ten distinct declared collections")
      val reserved = Vector("instances", "continuations", "selections", "sources", "proposals") ++
        SplitPhaseHostProposalReadPolicy.Purpose.values.toVector.map(_.toString)
      When("the input collection aliases another purpose or required configuration is absent")
      val invalid = reserved.map(name => values.updated(prefix + "input-collection", ConfigurationValue.StringValue(collection(name).print))) ++
        values.keys.toVector.map(key => values.removed(key)) ++ Vector(
          values.updated(prefix + "store-name", ConfigurationValue.StringValue("other")),
          values.updated(prefix + "input-collection", ConfigurationValue.NumberValue(BigDecimal(1))))
      Then("composition refuses every invalid host without manufacturing defaults or storage grants")
      invalid.foreach(data => SplitPhaseHostSemanticInputReadPolicy.bindC(host(data), workflow) shouldBe a[Consequence.Failure[?]])
    }
  }
}
