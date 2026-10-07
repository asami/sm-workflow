package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Json, parser}
import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall}
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.goldenport.cncf.subsystem.SubsystemEventStoreBootstrap
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.spec as spec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseStartCommand, SplitPhaseStartInput}
import SplitPhaseExplicitStartPreparation.{Record, Reference}
import SplitPhaseExplicitStartRecordJsonV1.*

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseExplicitStartRecordSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"explicit Start record fixture rejected: $failure")
  }
  private val catalog = take(SplitPhaseRuntimeCatalog.loadC())
  private val configuration = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("explicit-start")),
    MigrationPolicy("v1"), RetentionPolicy("v1"), LeasePolicy("v1"), DeliveryConfiguration.SameStore(WorkflowStoreIdentity("explicit-start")))
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "explicitstart", name))
  private val workflow = SplitPhaseManagedStartAssembly.Declaration(configuration, collection("instances"), collection("continuations"))
  private val command = SplitPhaseStartCommand(SplitPhaseStartInput("77", "PREVIEW", "authority-1",
    Some("snapshot-1"), None, Some("proposal-1")), "selection-1", "participant-1", "start-1")
  private val selection = ProfileStartAdmission.Selection("selection-1",
    StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase"), catalog.definition.workflowIdentity,
    catalog.definition.workflowRevision, "participant-1", ProfileStartAdmission.SelectionOrigin.ExplicitHuman, "start-1", None)
  private val source = SplitPhaseEntryAssessment.SourceSnapshot("snapshot-1", "77", "authority-1", "source-revision-4", "a" * 64)
  private val proposal = SplitPhaseEntryAssessment.ProposalCandidate("proposal-1", "77", "authority-1", source.digest, "b" * 64)
  private val original = Record(Reference("start-record-1", "record-revision-7"), InstanceIdentity("instance-42"),
    catalog.definition.workflowIdentity, catalog.definition.workflowRevision, configuration, command, selection,
    SplitPhaseEntryAssessment.Assessment(source, Some(proposal)))
  private val prefix = SplitPhaseHostStartRecordReadPolicy.prefix
  private val values: Map[String, ConfigurationValue] = Map(
    prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    prefix + "record-collection" -> ConfigurationValue.StringValue(collection("starts").print),
    SplitPhaseHostSemanticInputReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    SplitPhaseHostSemanticInputReadPolicy.prefix + "input-collection" -> ConfigurationValue.StringValue(collection("inputs").print),
    SplitPhaseHostSelectionReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    SplitPhaseHostSelectionReadPolicy.prefix + "selection-collection" -> ConfigurationValue.StringValue(collection("selections").print),
    SplitPhaseHostEntryReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
    SplitPhaseHostEntryReadPolicy.prefix + "source-collection" -> ConfigurationValue.StringValue(collection("sources").print),
    SplitPhaseHostEntryReadPolicy.prefix + "proposal-collection" -> ConfigurationValue.StringValue(collection("proposals").print),
    SplitPhaseHostProposalReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow")) ++
    SplitPhaseHostProposalReadPolicy.Purpose.values.toVector.map(purpose =>
      SplitPhaseHostProposalReadPolicy.prefix + purpose.key -> ConfigurationValue.StringValue(collection(purpose.toString).print))
  private def host(data: Map[String, ConfigurationValue]) = ResolvedConfiguration(HostConfiguration(data), ConfigurationTrace.empty)
  private def alter(raw: String)(f: Json => Json): String = schema + ":" + f(parser.parse(raw.drop(schema.length + 1)).toOption.get).noSpaces
  private def nested(json: Json, path: List[String])(f: Json => Json): Json = path match {
    case Nil => f(json)
    case key :: tail => json.mapObject(fields => fields.add(key, nested(fields(key).get, tail)(f)))
  }

  "SplitPhase explicit Start record storage" should {
    "round-trip full typed data including exact identity and revision without preparing Start" in {
      Given("the complete explicit Start record and its exact identity and revision")
      val selected = original
      When("the inert record is encoded and decoded")
      val raw = take(encodeC(selected))
      val decoded = take(decodeC(selected.reference, raw))
      Then("the complete data record is retained without preparing Start")
      decoded shouldBe original
      Given("the same record with both optional proposal references absent")
      val absentOptionals = original.copy(command = command.copy(input = command.input.copy(proposalReference = None)),
        assessment = original.assessment.copy(proposalCandidate = None))
      When("the absent-optionals record is round-tripped")
      val absent = take(decodeC(absentOptionals.reference, take(encodeC(absentOptionals))))
      Then("the absence of the optional proposal data is retained")
      absent shouldBe absentOptionals
      Given("a structurally valid record whose selection origin is Skill rather than human")
      val nonHuman = original.copy(selection = selection.copy(origin = ProfileStartAdmission.SelectionOrigin.Skill))
      When("the nonhuman inert record is round-tripped")
      val skill = take(decodeC(nonHuman.reference, take(encodeC(nonHuman))))
      Then("data decoding preserves origin without admitting it or issuing work")
      skill shouldBe nonHuman
      // Data decoding deliberately does not admit human origin or issue work.
    }

    "refuse nested extras omissions unsupported types schemas and crossed exact references" in {
      Given("the exact explicit Start wire with altered nested fields, types and schema variants")
      val raw = take(encodeC(original))
      val invalid = Vector(
        alter(raw)(_.mapObject(_.add("authority", Json.True))),
        alter(raw)(_.mapObject(_.add("schemaVersion", Json.fromString("v2")))),
        alter(raw)(json => nested(json, List("payload", "reference"))(_.mapObject(_.remove("revision")))),
        alter(raw)(json => nested(json, List("payload"))(_.mapObject(_.add("prepared", Json.True)))),
        alter(raw)(json => nested(json, List("payload", "configuration"))(_.mapObject(_.add("grant", Json.True)))),
        alter(raw)(json => nested(json, List("payload", "command", "input"))(_.mapObject(_.add("authority", Json.True)))),
        alter(raw)(json => nested(json, List("payload", "selection"))(_.mapObject(_.remove("origin")))),
        alter(raw)(json => nested(json, List("payload", "assessment", "source"))(_.mapObject(_.remove("revision")))),
        alter(raw)(json => nested(json, List("payload"))(_.mapObject(_.add("instanceIdentity", Json.fromInt(42))))))
      invalid.foreach { value =>
        When("the altered root or nested field set is decoded")
        val result = decodeC(original.reference, value)
        Then("the unsupported shape, type or schema is refused")
        result shouldBe a[Consequence.Failure[?]]
      }
      When("the exact wire is selected through another revision")
      val revision = decodeC(Reference(original.reference.identity, "other"), raw)
      Then("the revision mismatch is refused")
      revision shouldBe a[Consequence.Failure[?]]
      When("the exact wire is selected through a foreign identity")
      val identity = decodeC(Reference("foreign", original.reference.revision), raw)
      Then("the identity mismatch is refused")
      identity shouldBe a[Consequence.Failure[?]]
      When("an oversized explicit Start payload is decoded")
      val oversized = decodeC(original.reference, schema + ":" + "x" * (1024 * 1024))
      Then("the unbounded payload is refused")
      oversized shouldBe a[Consequence.Failure[?]]
      When("a noncanonical reference is encoded")
      val padded = encodeC(original.copy(reference = Reference(" padded ", "1")))
      Then("the padded reference is refused")
      padded shouldBe a[Consequence.Failure[?]]
      When("a command without selection identity is encoded")
      val missingselection = encodeC(original.copy(command = command.copy(selectionIdentity = null)))
      Then("the incomplete command is refused")
      missingselection shouldBe a[Consequence.Failure[?]]
      When("no explicit record is provided to the encoder")
      val absent = encodeC(null)
      Then("an absent record is refused")
      absent shouldBe a[Consequence.Failure[?]]
    }

    "bind eleven distinct host purposes without creating a context-free capability" in {
      Given("the complete eleven-purpose declaration for the owning Workflow store")
      val configured = host(values)
      When("the reader is bound and a context-free load is attempted")
      val reader = take(SplitPhaseHostStartRecordReadPolicy.bindC(configured, workflow))
      val contextfree = reader.loadC(original.reference)
      Then("configuration alone creates no loading capability")
      contextfree shouldBe a[Consequence.Failure[?]]
      When("the current-call load receives no call capsule")
      val missingcall = reader.loadForCallC(original.reference, null)
      Then("the missing owning call is refused")
      missingcall shouldBe a[Consequence.Failure[?]]
      When("the exact separate Start record collection is admitted")
      val admitted = SplitPhaseHostStartRecordReadPolicy.admitC(host(values), "workflow", collection("starts"))
      Then("the exact logical declaration is valid without granting a context-free load")
      admitted shouldBe a[Consequence.Success[?]]
      Given("declaration variants overlapping another purpose, omitting a key or changing store/type")
      val protectedNames = Vector("instances", "continuations", "selections", "sources", "proposals", "inputs") ++
        SplitPhaseHostProposalReadPolicy.Purpose.values.toVector.map(_.toString)
      val invalid = protectedNames.map(name => values.updated(prefix + "record-collection", ConfigurationValue.StringValue(collection(name).print))) ++
        values.keys.toVector.map(values.removed) ++ Vector(values.updated(prefix + "store-name", ConfigurationValue.StringValue("other")),
          values.updated(prefix + "record-collection", ConfigurationValue.NumberValue(BigDecimal(1))))
      invalid.foreach { data =>
        When("the altered purpose declaration is bound")
        val result = SplitPhaseHostStartRecordReadPolicy.bindC(host(data), workflow)
        Then("the overlapping or incomplete declaration is refused")
        result shouldBe a[Consequence.Failure[?]]
      }
      When("the owning Workflow declaration is absent")
      val missingworkflow = SplitPhaseHostStartRecordReadPolicy.bindC(host(values), null)
      Then("binding refuses the missing owner")
      missingworkflow shouldBe a[Consequence.Failure[?]]
    }

    "read and re-admit explicit data through real Service CLI calls and pool reopen without starting an instance" in {
      Given("privately installed data records, not a human ingress or public allocation registry")
      val path = Files.createTempDirectory("smwf-explicit-start-read-").resolve("host.sqlite")
      val raw = take(encodeC(original))
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
      val rows = Vector(
        (collection("starts"), original.reference.identity, storageField, raw),
        (collection("starts"), "foreign", storageField, raw),
        (collection("starts"), "malformed", storageField, "not-json"),
        (collection("starts"), "missing-payload", "unrelated", raw),
        (collection("starts"), "drift", storageField, take(encodeC(original.copy(reference = Reference("drift", "1"),
          assessment = original.assessment.copy(source = source.copy(revision = "different")))))),
        (collection("selections"), selection.selectionId, ProfileSelectionJsonV1.storageField, take(ProfileSelectionJsonV1.encodeStorageC(selection))),
        (collection("sources"), source.sourcePhaseIdentity, SplitPhaseEntryRecordJsonV1.sourceField, sourceRaw),
        (collection("proposals"), proposal.identity, SplitPhaseEntryRecordJsonV1.proposalField, proposalRaw))
      val policy = take(EventPersistencePolicy.createC("explicit-start-read-v1", Vector(
        EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
      val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"),
        collection("indexes"), Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId,
          configuration.workflowStore.name, workflow.instances, workflow.continuations))))
      var installed = 0
      var savedReader = Option.empty[SplitPhaseExplicitStartDataStorePort]
      for (mode <- Vector("initial", "reopen", "undeclared")) {
        val context = ExecutionContext.create()
        val declared = if (mode == "undeclared") values.removed(prefix + "record-collection") else values
        val config = host(declared ++ Map("textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
          "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)))
        val bootstrap = take(SubsystemEventStoreBootstrap.createC(policy, storage,
          ScopeContext(ScopeKind.Subsystem, "explicit-start-read", None, context.observability), config))
        try {
          val owner = RuntimeBindingAdmissionFixture.admit(bootstrap.subsystem)
          val reader = if (mode == "undeclared") savedReader.get else take(SplitPhaseHostStartRecordReadPolicy.bindC(owner.configuration, workflow))
          savedReader = Some(reader)
          val selectionReader = take(SplitPhaseHostSelectionReadPolicy.bindC(owner.configuration, workflow))
          val entry = take(SplitPhaseHostEntryReadPolicy.bindC(owner.configuration, workflow))
          var captured = Vector.empty[ActionCall.Core]
          var prepared = 0
          val probe = new ProfileStartAdmission.CallScopedSelectionPort {
            def loadForCallC(identity: String, capsule: ActionCall.Core): Consequence[Option[ProfileStartAdmission.Selection]] = {
              captured :+= capsule
              final class Installer extends ProcedureActionCall {
                val core = capsule
                def installC(): Consequence[Unit] = {
                  given ExecutionContext = executionContext
                  val space = executionContext.dataStoreSpace
                  val prior = space.captureBinding()
                  try bind_component_application_datastore_c("workflow").flatMap { _ => rows.foldLeft(Consequence.unit) {
                    case (previous, (target, key, field, value)) => previous.flatMap(_ => space.dataStore(target)
                      .flatMap(_.create(target, DataStore.StringEntryId(key), org.goldenport.record.Record.data(field -> value))).map(_ => ()))
                  }} finally space.restoreBinding(prior)
                }
                def execute() = Consequence.stateConflict("private data installer is not an Operation")
              }
              if (mode == "initial" && installed == 0) { take(new Installer().installC()); installed += 1 }
              val prior = capsule.executionContext.dataStoreSpace.captureBinding()
              if (mode == "undeclared") reader.loadForCallC(original.reference, capsule) shouldBe a[Consequence.Failure[?]]
              else {
                take(reader.loadForCallC(original.reference, capsule)) shouldBe Some(original)
                take(reader.loadForCallC(Reference("absent", "1"), capsule)) shouldBe None
                Vector(Reference("foreign", original.reference.revision), Reference("malformed", "1"), Reference("missing-payload", "1"),
                  original.reference.copy(revision = "other")).foreach(ref => reader.loadForCallC(ref, capsule) shouldBe a[Consequence.Failure[?]])
                reader.loadForCallC(original.reference, capsule.copy(component = None)) shouldBe a[Consequence.Failure[?]]
                reader.loadForCallC(original.reference, capsule.copy(executionContext = null)) shouldBe a[Consequence.Failure[?]]
                val wrong = new SplitPhaseExplicitStartDataStorePort(SplitPhaseHostStartRecordReadPolicy.Declaration("workflow", collection("sources")))
                wrong.loadForCallC(original.reference, capsule) shouldBe a[Consequence.Failure[?]]
                reader.prepareForCallC(Reference("drift", "1"), command, capsule, selectionReader, entry,
                  catalog.definition, configuration) shouldBe a[Consequence.Failure[?]]
                val value = take(reader.prepareForCallC(original.reference, command, capsule, selectionReader, entry,
                  catalog.definition, configuration)).get
                value.record shouldBe original
                value.initial.identity shouldBe original.instanceIdentity
                value.initial.history shouldBe empty
                prepared += 1
              }
              capsule.executionContext.dataStoreSpace.captureBinding() shouldBe prior
              // Never authorize this fixture probe to execute Start.
              Consequence.success(None)
            }
          }
          val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty, (creation, parameters, resolved) => {
            given ExecutionContext = resolved
            SplitPhaseManagedStartAssembly.bindC(creation, workflow, catalog, probe, entry).map(operation =>
              spec.ServiceDefinition("SplitPhaseService", spec.OperationDefinitionGroup(NonEmptyVector.of(operation))))
          }).primaryFactory
          val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
          owner.add(built)
          When(s"Service and ordinary CLI dispatch read exact explicit references in $mode mode")
          val wire = SplitPhaseStartServiceOperation.encodeCommand(command)
          owner.executeOperationResponse(Request.of(component = built.name, service = "SplitPhaseService", operation = "startSplitPhase",
            arguments = List(Argument("arg1", wire)))) shouldBe a[Consequence.Failure[?]]
          new CncfRuntime().executeCommandResponse(owner, Array(s"${built.name}/SplitPhaseService/startSplitPhase", "--json", wire))
            .shouldBe(a[Consequence.Failure[?]])
          captured.size shouldBe 2
          captured.head.executionContext should not be theSameInstanceAs(captured.last.executionContext)
          prepared shouldBe (if (mode == "undeclared") 0 else 2)
          reader.loadForCallC(original.reference, captured.last) shouldBe a[Consequence.Failure[?]]
          reader.loadC(original.reference) shouldBe a[Consequence.Failure[?]]
          given ExecutionContext = context
          val instances = take(SplitPhaseManagedStartAssembly.bindPersistenceC(ComponentCreate(owner, ComponentOrigin.Builtin), workflow, catalog))
          take(instances.storage.bindInstancesC()).loadC(original.instanceIdentity).map(_ shouldBe None) shouldBe a[Consequence.Success[?]]
        } finally take(bootstrap.shutdownC())
      }
      installed shouldBe 1
    }

    "register only with a current authenticated actor and independent Create grant and recover exact retry after reopen" in {
      import org.goldenport.cncf.datastore.ComponentRecordStorageDeclaration
      import org.goldenport.cncf.subsystem.*
      import org.goldenport.cncf.security.IngressSecurityResolver
      import io.circe.generic.auto.*
      import io.circe.syntax.*
      Given("configured-local actor authentication and privately installed source/selection data, not actual human consent")
      val path = Files.createTempDirectory("smwf-explicit-start-register-").resolve("host.sqlite")
      val expected = original.copy(reference = Reference(command.selectionIdentity, "1"))
      val interrupted = for (fault <- Vector("missing", "failed", "divergent"); cli <- Vector(false, true)) yield {
        val identity = s"interrupted-$fault-$cli"
        val input = command.copy(selectionIdentity = identity, idempotencyKey = s"key-$identity")
        val selected = selection.copy(selectionId = identity, idempotencyKey = input.idempotencyKey)
        (fault, cli, expected.copy(reference = Reference(identity, "1"), command = input, selection = selected,
          instanceIdentity = InstanceIdentity(s"instance-$identity")))
      }
      val grant = take(ComponentRecordStorageDeclaration.createC(Vector(
        ComponentRecordStorageDeclaration.CreateGrant(SmWorkflowComponent.componentId, "workflow", collection("starts")))))
      val policy = take(EventPersistencePolicy.createC("explicit-register-v1", Vector(
        EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
      val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"), collection("indexes"),
        Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId, "workflow", workflow.instances, workflow.continuations))))
      val rows = Vector(
        (collection("selections"), selection.selectionId, ProfileSelectionJsonV1.storageField, take(ProfileSelectionJsonV1.encodeStorageC(selection))),
        (collection("sources"), source.sourcePhaseIdentity, SplitPhaseEntryRecordJsonV1.sourceField,
          SplitPhaseEntryRecordJsonV1.sourcePrefix + source.asJson.mapObject(_.add("schemaVersion", Json.fromString(SplitPhaseEntryRecordJsonV1.sourceSchema))).noSpaces),
        (collection("proposals"), proposal.identity, SplitPhaseEntryRecordJsonV1.proposalField,
          SplitPhaseEntryRecordJsonV1.proposalPrefix + proposal.asJson.mapObject(_.add("schemaVersion", Json.fromString(SplitPhaseEntryRecordJsonV1.proposalSchema))).noSpaces)) ++
        interrupted.map { case (_, _, record) => (collection("selections"), record.selection.selectionId,
          ProfileSelectionJsonV1.storageField, take(ProfileSelectionJsonV1.encodeStorageC(record.selection))) }
      var installed = 0
      var interruptedCreates = 0
      for (mode <- Vector("ungranted", "initial", "reopen")) {
        val context = ExecutionContext.create()
        val cfg = host(values ++ Map("textus.subsystem.user-mode" -> ConfigurationValue.StringValue("standalone"),
          "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
          "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)))
        val scope = ScopeContext(ScopeKind.Subsystem, "explicit-registration", None, context.observability)
        val bootstrap = take(if (mode == "ungranted") SubsystemEventStoreBootstrap.createC(policy, storage, scope, cfg)
          else SubsystemEventStoreBootstrap.createC(policy, storage, scope, cfg, None, org.goldenport.cncf.path.AliasResolver.empty, grant))
        try {
          val owner = RuntimeBindingAdmissionFixture.admit(bootstrap.subsystem)
          val selections = take(SplitPhaseHostSelectionReadPolicy.bindC(owner.configuration, workflow))
          val entry = take(SplitPhaseHostEntryReadPolicy.bindC(owner.configuration, workflow))
          val reader = take(SplitPhaseHostStartRecordReadPolicy.bindC(owner.configuration, workflow))
          var submitted = expected
          var observed = Option.empty[Consequence[Record]]
          var escaped = Option.empty[ActionCall.Core]
          var maskedReadback = Option.empty[String]
          val probe = new ProfileStartAdmission.CallScopedSelectionPort {
            def loadForCallC(identity: String, core: ActionCall.Core): Consequence[Option[ProfileStartAdmission.Selection]] = {
              escaped = Some(core)
              final class Installer extends ProcedureActionCall {
                val core = escaped.get
                def installC(): Consequence[Unit] = {
                  given ExecutionContext = executionContext
                  val space = executionContext.dataStoreSpace
                  val prior = space.captureBinding()
                  try bind_component_application_datastore_c("workflow").flatMap { _ => rows.foldLeft(Consequence.unit) {
                    case (previous, (target, key, field, value)) => previous.flatMap(_ => space.dataStore(target)
                      .flatMap(_.create(target, DataStore.StringEntryId(key), org.goldenport.record.Record.data(field -> value))).map(_ => ()))
                  }} finally space.restoreBinding(prior)
                }
                def execute() = Consequence.stateConflict("private baseline installer is not an Operation")
              }
              if (installed == 0) { take(new Installer().installC()); installed += 1 }
              observed = Some(maskedReadback match {
                case None => SplitPhaseManagedStartRecordRegistration.registerForCallC(submitted, core, workflow, selections, entry)
                case Some(fault) => for {
                  capability <- org.goldenport.cncf.datastore.ComponentRecordStorage.bindForCallC(core,
                    "workflow", collection("starts"), DataStore.StringEntryId(submitted.reference.identity))
                  prepared <- SplitPhaseExplicitStartPreparation.prepareC(submitted.command, submitted,
                    ProfileStartAdmission.forCall(selections, core), SplitPhaseEntryAssessment.forCall(entry, core),
                    catalog.definition, configuration)
                  result <- SplitPhaseStartRecordRetention.retainC(prepared.record, new SplitPhaseStartRecordRetention.Port {
                    private var created = false
                    def createC(row: org.goldenport.record.Record) = capability.createC(row).map { _ =>
                      created = true
                      interruptedCreates += 1
                    }
                    def loadC() = if (!created) capability.loadC() else fault match {
                      case "missing" => Consequence.success(None)
                      case "failed" => Consequence.stateConflict("private post-Create observation interruption")
                      case "divergent" => SplitPhaseExplicitStartRecordJsonV1.encodeC(submitted.copy(
                        instanceIdentity = InstanceIdentity("foreign-observed-instance"))).map(raw => Some(
                          org.goldenport.record.Record.data(SplitPhaseExplicitStartRecordJsonV1.storageField -> raw)))
                    }
                  })
                } yield result
              })
              if (mode == "ungranted") take(reader.loadForCallC(expected.reference, core)) shouldBe None
              else take(reader.loadForCallC(expected.reference, core)) shouldBe Some(expected)
              if (maskedReadback.nonEmpty) take(reader.loadForCallC(submitted.reference, core)) shouldBe Some(submitted)
              // Observed registration data never authorizes this transport to Start.
              Consequence.success(None)
            }
          }
          val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty, (creation, parameters, resolved) => {
            given ExecutionContext = resolved
            SplitPhaseManagedStartAssembly.bindC(creation, workflow, catalog, probe, entry).map(operation =>
              spec.ServiceDefinition("SplitPhaseService", spec.OperationDefinitionGroup(NonEmptyVector.of(operation))))
          }).primaryFactory
          val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
          owner.add(built)
          owner.withDescriptor(GenericSubsystemDescriptor(path = java.nio.file.Path.of("<explicit-registration-spec>"),
            subsystemName = "explicit-registration", componentBindings = Vector(GenericSubsystemComponentBinding(built.name)),
            security = Some(GenericSubsystemSecurityBinding(authentication = Some(GenericSubsystemAuthenticationBinding(
              convention = Some("enabled"), fallbackPrivilege = Some("disabled"),
              localSubject = Some(GenericSubsystemLocalSubjectBinding(id = command.participantIdentity, roles = Vector("user"),
                capabilities = Vector("user"), attributes = Map.empty, securityLevel = Some("user")))))))))
          val actor = take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.Fixed,
            built.logic.executionContext(), Map.empty)).executionContext
          val request = Request.of(component = built.name, service = "SplitPhaseService", operation = "startSplitPhase",
            arguments = List(Argument("arg1", SplitPhaseStartServiceOperation.encodeCommand(command))))
          def invoke(cli: Boolean): Consequence[Record] = {
            observed = None
            val transport = if (cli) new CncfRuntime().executeCommandResponse(owner, Array(s"${built.name}/SplitPhaseService/startSplitPhase",
              "--json", SplitPhaseStartServiceOperation.encodeCommand(command)))
              else owner.executeOperationResponseInContext(request, actor)
            transport shouldBe a[Consequence.Failure[?]]
            observed.getOrElse(fail(s"Registration probe was not reached: mode=$mode cli=$cli transport=$transport"))
          }
          When(s"actual Service/CLI registration and retry execute in $mode mode")
          for (cli <- Vector(false, true)) {
            val result = invoke(cli)
            if (mode == "ungranted") result shouldBe a[Consequence.Failure[?]]
            else take(result) shouldBe expected
          }
          if (mode != "ungranted") {
            Vector(expected.copy(instanceIdentity = InstanceIdentity("different-instance")),
              expected.copy(reference = expected.reference.copy(revision = "2")),
              expected.copy(reference = Reference("different-record", "1")),
              expected.copy(command = command.copy(participantIdentity = "different-actor")))
              .foreach { conflict => submitted = conflict; invoke(false) shouldBe a[Consequence.Failure[?]] }
            submitted = expected
            take(invoke(false)) shouldBe expected
          }
          if (mode == "initial") {
            When("actual SQLite Create succeeds but immediate observation is unavailable or divergent, before host shutdown")
            for ((fault, cli, record) <- interrupted) {
              submitted = record
              maskedReadback = Some(fault)
              invoke(cli) match {
                case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets.collect {
                  case org.goldenport.observation.Descriptor.Facet.Reason(name) => name
                } should contain("transaction-indeterminate")
                case other => fail(s"Uncertain committed registration published success: $other")
              }
              interruptedCreates shouldBe interrupted.indexWhere(_._3 == record) + 1
            }
            maskedReadback = None
            // No successful normal retry for these rows before pool shutdown.
          }
          if (mode == "reopen") {
            When("a fresh host normally recovers all six committed rows whose initial observations failed")
            for ((_, cli, record) <- interrupted) {
              submitted = record
              take(invoke(cli)) shouldBe record
              take(invoke(!cli)) shouldBe record
              interruptedCreates shouldBe interrupted.size
            }
          }
          submitted = expected
          SplitPhaseManagedStartRecordRegistration.registerForCallC(expected, escaped.get, workflow, selections, entry)
            .shouldBe(a[Consequence.Failure[?]])
          given ExecutionContext = context
          val instances = take(SplitPhaseManagedStartAssembly.bindPersistenceC(ComponentCreate(owner, ComponentOrigin.Builtin), workflow, catalog))
          take(take(instances.storage.bindInstancesC()).loadC(expected.instanceIdentity)) shouldBe None
          interrupted.foreach { case (_, _, record) =>
            take(take(instances.storage.bindInstancesC()).loadC(record.instanceIdentity)) shouldBe None
          }
        } finally take(bootstrap.shutdownC())
      }
      installed shouldBe 1
      interruptedCreates shouldBe interrupted.size
    }
  }
}
