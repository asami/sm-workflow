package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import cats.syntax.all.*
import io.circe.{Json, parser}
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.{ComponentRecordStorageDeclaration, DataStore}
import org.goldenport.cncf.event.*
import org.goldenport.cncf.security.IngressSecurityResolver
import org.goldenport.cncf.subsystem.*
import org.goldenport.cncf.testutil.{RuntimeBindingAdmissionFixture, NativeHumanManagedHostObservation}
import org.goldenport.cncf.workflow.{ContextReference, WorkflowMechanicalTransition as M}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as InstanceContinuationIdentity}
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseAdvanceCommand, SplitPhasePartitionContextReference}
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningCheckpointPublication as U, SplitPhasePlanningCheckpointJsonV1 as J,
  SplitPhasePlanningCheckpointObservation as O, SplitPhaseManagedPlanningCheckpoint as Managed,
  SplitPhaseExplicitPlanningInputJsonV1 as F, SplitPhaseTypedStartJsonV1 as TJ,
  SplitPhaseExplicitPartitionContextJsonV1 as CJ, SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseScopeProjectionCheckpointJsonV1 as NJ, SplitPhaseScopeProjectionCheckpointPreparation as NC,
  SplitPhaseScopeProjectionCheckpointPublication as NU, SplitPhaseScopeProjectionSnapshot as NV,
  SplitPhaseManagedScopeProjectionCheckpoint as NM, SplitPhasePlanningCheckpointDataStorePort as Reads}
import SplitPhasePlanningCheckpointFixture.{f, anchor}
import SplitPhaseObservedCheckpointFixture.*
import SplitPhaseExplicitPartitionFixture.take

/** Real SQLite/native calls with explicitly private setup probes. Selected
  * scenarios install the actual public continuation pair; seeded Start rows
  * still do not establish production human ingress or default installation.
  *
  * @since Oct.  2, 2026
 * @version Oct. 11, 2026
  */
final class SplitPhaseManagedPlanningCheckpointSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "managedcheckpoint", name))
  private def target(p: F.Purpose) = collection(p.toString.toLowerCase)
  private val declaration = SplitPhaseManagedStartAssembly.Declaration(f.configuration, collection("instances"), collection("continuations"))
  private val ip = SplitPhasePlanningInputReadPolicy.prefix
  private val cp = SplitPhasePartitionContextReadPolicy.prefix
  private val sp = SplitPhaseHostSelectionReadPolicy.prefix
  private val tp = SplitPhaseTypedStartReadPolicy.prefix
  private val ep = SplitPhasePartitionEnrichmentStorage.prefix
  private val values = Map[String, ConfigurationValue](
    ip + "store-name" -> ConfigurationValue.StringValue("workflow"),
    cp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    cp + "record-collection" -> ConfigurationValue.StringValue(collection("contexts").print),
    sp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    sp + "selection-collection" -> ConfigurationValue.StringValue(collection("selections").print),
    tp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    tp + "record-collection" -> ConfigurationValue.StringValue(collection("starts").print),
    ep + "store-name" -> ConfigurationValue.StringValue("workflow"),
    ep + "record-collection" -> ConfigurationValue.StringValue(collection("publications").print)) ++
    F.Purpose.values.map(p => (ip + p.key) -> ConfigurationValue.StringValue(target(p).print)) ++
    SplitPhaseCandidateAllocationFixture.values
  private def host(v: Map[String, ConfigurationValue]) = ResolvedConfiguration(HostConfiguration(v), ConfigurationTrace.empty)
  private def _earlier_host: ResolvedConfiguration = host(values.updated(ip + F.Purpose.Estimates.key,
    ConfigurationValue.StringValue(collection("unusedconstructionestimates").print)))
  private lazy val _relocated_catalog: SplitPhaseRuntimeCatalog.Catalog = {
    def _relocate_(value: Json): Json = value.asObject match {
      case Some(fields) => Json.fromFields(fields.toVector.map { (name, member) =>
        name -> (if (name == "line") member.asNumber.flatMap(_.toInt)
          .map(line => Json.fromInt(line + 17)).getOrElse(member) else _relocate_(member))
      })
      case None => value.asArray.map(items => Json.fromValues(items.map(_relocate_))).getOrElse(value)
    }
    val workflow = parser.parse(Files.readString(Path.of("target/cozy/statemachine-workflow-abi.json")))
      .fold(error => fail(error.toString), identity)
    val producer = parser.parse(Files.readString(Path.of("target/cozy/candidate-admission-producer-abi.json")))
      .fold(error => fail(error.toString), identity)
    take(SplitPhaseRuntimeCatalog.parseC(_relocate_(workflow).noSpaces, _relocate_(producer).noSpaces))
  }
  private def query(saved: K.Captured) = SplitPhaseAdvanceCommand(saved.before.identity.value,
    SplitPhasePartitionContextReference(saved.record.originalContext.reference.identity, saved.record.originalContext.reference.revision))
  private def _root_c(call: Probe, saved: K.Captured): Consequence[K.Record] = for {
    reads <- Reads.bindC(call.core.component.get.subsystem.get.configuration, declaration, f.catalog)
    selected <- reads.loadMappingForCallC(query(saved), call.core)
    actual <- selected match {
      case Some((_, value)) => Consequence.success(value)
      case None => Consequence.stateConflict("Private observation requires an actual selected native checkpoint")
    }
  } yield actual
  private def originalRows(saved: K.Captured): Vector[(DataStore.CollectionId, String, Record)] = {
    _original_rows(O.Original(saved.record.originalContext, saved.record.originalStart, saved.record.originalContext.facts), saved.record.originalStart)
  }
  private def _original_rows(original: O.Original, start: SplitPhaseTypedStartPreparation.Record): Vector[(DataStore.CollectionId, String, Record)] = {
    val context = original.context; val facts = original.facts
    val rows = Vector(
      (F.Purpose.Source, facts.proposal.source.reference.identity, take(F.encodeSourceC(facts.proposal.source))),
      (F.Purpose.Inventory, facts.proposal.inventory.reference.identity, take(F.encodeInventoryC(facts.proposal.inventory))),
      (F.Purpose.Proposal, facts.proposal.proposal.reference.identity, take(F.encodeProposalC(facts.proposal.proposal))),
      (F.Purpose.Boundary, facts.boundary.boundary.reference.identity, take(F.encodeBoundaryC(facts.boundary.boundary))),
      (F.Purpose.Ownership, facts.boundary.ownership.reference.identity, take(F.encodeOwnershipC(facts.boundary.ownership))),
      (F.Purpose.Handoffs, facts.boundary.handoffs.reference.identity, take(F.encodeHandoffsC(facts.boundary.handoffs))),
      (F.Purpose.Estimates, facts.partition.estimates.reference.identity, take(F.encodeEstimatesC(facts.partition.estimates))),
      (F.Purpose.PartitionPolicy, facts.partition.policy.reference.identity, take(F.encodePartitionPolicyC(facts.partition.policy))),
      (F.Purpose.Planning, facts.planning.reference.identity, take(F.encodePlanningC(facts.planning))))
    rows.map((p, id, raw) => (target(p), id, Record.data(F.storageField -> raw))) ++ Vector(
      (collection("starts"), start.reference.identity, Record.data(TJ.storageField -> take(TJ.encodeC(start)))),
      (collection("contexts"), context.reference.identity, Record.data(CJ.storageField -> take(CJ.encodeC(context)))),
      (collection("selections"), start.selection.selectionId, Record.data(ProfileSelectionJsonV1.storageField ->
        take(ProfileSelectionJsonV1.encodeStorageC(start.selection)))))
  }

  private final class Probe(val core: ActionCall.Core, task: Probe => Consequence[Unit]) extends ProcedureActionCall {
    private def withStoreC[A](c: DataStore.CollectionId)(run: DataStore => Consequence[A]): Consequence[A] = {
      given ExecutionContext = executionContext
      val space = executionContext.dataStoreSpace; val prior = space.captureBinding()
      try bind_component_application_datastore_c("workflow").flatMap(_ => space.dataStore(c).flatMap(run))
      finally space.restoreBinding(prior)
    }
    def writeC(c: DataStore.CollectionId, id: String, row: Record, update: Boolean = false): Consequence[Unit] = {
      given ExecutionContext = executionContext
      withStoreC(c)(store => if (update) store.update(c, DataStore.StringEntryId(id), row) else store.create(c, DataStore.StringEntryId(id), row))
    }
    def rowC(c: DataStore.CollectionId, id: String): Consequence[Option[Record]] = {
      given ExecutionContext = executionContext
      withStoreC(c)(_.load(c, DataStore.StringEntryId(id)))
    }
    /** Corruption setup for exact rows in newly allocated private test DBs only. */
    def deleteFixtureRowC(c: DataStore.CollectionId, id: String): Consequence[Unit] = {
      given ExecutionContext = executionContext
      withStoreC(c)(_.delete(c, DataStore.StringEntryId(id)))
    }
    def execute(): Consequence[OperationResponse] = task(this).map(_ => OperationResponse.Scalar("internal-checkpoint-probe"))
  }

  /** Actual declared-document reads through the existing owning load/execute.
    * Corruption and restoration affect only exact rows in the private test DB.
    */
  private def _specify_generated_documents[A](call: Probe, targets: U.Targets,
    document: SplitPhasePlanningSnapshot.CandidateProjectionView.BuiltView,
    load: () => Consequence[A], execute: () => Consequence[A], expected: A,
    native: InstanceRecord): Unit = {
    val proposal = document.proposal.copy(children = document.proposal.children.updated(0,
      document.proposal.children.head.copy(expectedMinutes = document.proposal.children.head.expectedMinutes + 1)))
    val ownership = document.ownership.copy(method = document.ownership.method + " updated documentation")
    val handoffs = document.handoffs.copy(handoffs = document.handoffs.handoffs.map(value =>
      value.copy(input = value.input + " updated documentation")))
    val rows = Vector(
      (targets.proposals, proposal.reference.identity, take(F.encodeProposalC(proposal)),
        take(F.encodeProposalC(proposal.copy(reference = proposal.reference.copy(identity = proposal.reference.identity + "-foreign"))))),
      (targets.ownerships, ownership.reference.identity, take(F.encodeOwnershipC(ownership)),
        take(F.encodeOwnershipC(ownership.copy(reference = ownership.reference.copy(identity = ownership.reference.identity + "-foreign"))))),
      (targets.handoffs, handoffs.reference.identity, take(F.encodeHandoffsC(handoffs)),
        take(F.encodeHandoffsC(handoffs.copy(reference = handoffs.reference.copy(identity = handoffs.reference.identity + "-foreign"))))))
    rows.foreach { case (collection, identity, changedwire, foreignwire) =>
      Given("a declared generated document with typed-readable, same-reference changed content")
      val original = take(call.rowC(collection, identity)).get
      val changed = Record.data(F.storageField -> changedwire)
      try {
        take(call.writeC(collection, identity, changed, update = true))
        When("the owning retained load and execution consume the declared document")
        val observed = load()
        val repeated = execute()
        Then("the native-derived result remains observable without saved-body equality or document rewriting")
        take(observed) shouldBe expected
        take(repeated) shouldBe expected
        take(call.rowC(collection, identity)).get.getString(F.storageField) shouldBe Some(changedwire)
        assertNative(call, native)

        Given("the actual selected document row has an unrelated non-null SQL column")
        take(call.writeC(collection, identity,
          Record.data(F.storageField -> changedwire, "unusedPhysicalColumn" -> "not a document certificate"), update = true))
        When("the owning load and execution read only the declared document field")
        val extendedload = load()
        val extendedexecute = execute()
        Then("extra physical columns do not reject readable selected content or cause rewriting")
        take(extendedload) shouldBe expected
        take(extendedexecute) shouldBe expected
        take(call.rowC(collection, identity)).get.getString("unusedPhysicalColumn") shouldBe Some("not a document certificate")
        assertNative(call, native)

        Given("that exact declared document is absent from the private fixture store")
        take(call.deleteFixtureRowC(collection, identity))
        When("the same owning load and execution try to consume the missing deliverable")
        val missingload = load()
        val missingexecute = execute()
        Then("absence fails at declared-document consumption without recreating native or document progress")
        missingload.isSuccess shouldBe false
        missingexecute.isSuccess shouldBe false
        take(call.rowC(collection, identity)) shouldBe None
        assertNative(call, native)

        Given("the declared row is restored with an unreadable document payload")
        take(call.writeC(collection, identity, Record.data(F.storageField -> "corrupt-declared-document")))
        When("the owning load and execution decode the selected malformed deliverable")
        val malformedload = load()
        val malformedexecute = execute()
        Then("neither consuming path accepts unreadable generated data or advances native state")
        malformedload.isSuccess shouldBe false
        malformedexecute.isSuccess shouldBe false
        assertNative(call, native)

        Given("the selected declared row contains a readable document addressed to another reference")
        take(call.writeC(collection, identity, Record.data(F.storageField -> foreignwire), update = true))
        When("the owning load and execution resolve that crossed document address")
        val foreignload = load()
        val foreignexecute = execute()
        Then("a different deliverable cannot stand in for the actual selected reference")
        foreignload.isSuccess shouldBe false
        foreignexecute.isSuccess shouldBe false
        assertNative(call, native)
      } finally {
        take(call.writeC(collection, identity, original, update = take(call.rowC(collection, identity)).nonEmpty))
      }
      Given("the private fixture restores the original declared row after observation")
      When("the owning load and execution read the original deliverable again")
      val restoredload = load()
      val restoredexecute = execute()
      Then("the retained result recovers without a new publication or native append")
      take(restoredload) shouldBe expected
      take(restoredexecute) shouldBe expected
      assertNative(call, native)
    }
  }

  /** Inert fixture annotations inside passive Human payloads only. */
  private def _with_unused_fields(value: Json): Json = value.asObject match {
    case Some(fields) => Json.obj(fields.toVector.map((key, child) => key -> _with_unused_fields(child))*)
      .mapObject(_.add("unused", Json.obj("command" -> Json.True, "grant" -> Json.True)))
    case None => value.asArray.map(items => Json.arr(items.map(_with_unused_fields)*)).getOrElse(value)
  }

  /** Private old-format payload setup; actual receipt envelope/instance stay untouched. */
  private def _store_passive_postimage(call: Probe, reference: M.Reference): Unit = {
    val key = take(M.keyC(reference))
    val row = take(call.rowC(declaration.continuations, key)).get
    val prefix = "cncf.workflow-json.v1:"
    val json = parser.parse(row.getString("mechanicalPayload").get.substring(prefix.length)).toOption.get
    val before = json.hcursor.downField("current").focus.get
    val changed = json.hcursor.downField("payload").downField("value").downField("payload")
      .withFocus(_.mapObject(_.add("before", before).add("next", before))).top.get
    take(call.writeC(declaration.continuations, key, Record.data("mechanicalPayload" -> (prefix + changed.noSpaces)), true))
  }

  /** Exercise unused legacy states through the owning readers, not a schema seal. */
  private def _specify_passive_checkpoint_states[A](call: Probe, reference: M.Reference,
    current: InstanceRecord, expected: A)(read: => Consequence[A], execute: => Consequence[A]): Unit = {
    val key = take(M.keyC(reference))
    val original = take(call.rowC(declaration.continuations, key)).get
    val prefix = "cncf.workflow-json.v1:"
    val json = parser.parse(original.getString("mechanicalPayload").get.substring(prefix.length)).toOption.get
    val variants = Vector(
      json.hcursor.downField("payload").downField("value").downField("payload")
        .withFocus(_.mapObject(_.remove("before").remove("next"))).top.get,
      json.hcursor.downField("payload").downField("value").downField("payload")
        .withFocus(_.mapObject(_.add("before", Json.False).add("next", Json.fromString("unreadable-unused-history")))).top.get)
    try {
      variants.foreach { value =>
        Given("Spec: docs/spec/split-phase-checkpoint-frame-applicability.md; the actual native calculation with absent or unreadable unused nested state copies")
        val wire = prefix + value.noSpaces
        take(call.writeC(declaration.continuations, key, Record.data("mechanicalPayload" -> wire), true))
        When("owning load and execution consume the result using its actual outer receipt states")
        val loaded = take(read)
        val repeated = take(execute)
        Then("the saved result and actual state return without a copy gate, rewrite or another append")
        loaded shouldBe expected
        repeated shouldBe expected
        take(call.rowC(declaration.continuations, key)).get.getString("mechanicalPayload") shouldBe Some(wire)
        assertNative(call, current)
      }
      for (field <- Vector("current", "next")) {
        Given("the actual outer native receipt state is malformed rather than an unused payload copy")
        take(call.writeC(declaration.continuations, key, Record.data("mechanicalPayload" ->
          (prefix + json.mapObject(_.add(field, Json.False)).noSpaces)), true))
        When("owning load and execution attempt to consume that actual native receipt")
        val refusedload = read
        val refusedexecution = execute
        Then("the native state consumer refuses without substitute state, publication or another append")
        refusedload.isSuccess shouldBe false
        refusedexecution.isSuccess shouldBe false
        assertNative(call, current)
      }
    } finally take(call.writeC(declaration.continuations, key, original, true))
  }

  private def _specify_native_checkpoint_selection[A](call: Probe, reference: M.Reference,
    current: InstanceRecord)(read: => Consequence[A]): Unit = {
    Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R16; Example: E35; a real receipt and an independently existing second native instance")
    val key = take(M.keyC(reference))
    val row = take(call.rowC(declaration.continuations, key)).get
    val prefix = "cncf.workflow-json.v1:"
    val json = parser.parse(row.getString("mechanicalPayload").get.substring(prefix.length)).toOption.get
    val identity = InstanceIdentity(current.identity.value + "-checkpoint-other")
    val other = take(instances.loadForCallC(f.configuration, identity, call.core)).getOrElse(
      take(instances.createForCallC(f.configuration, f.initial.copy(identity = identity), call.core)))
    val otherjson = parser.parse(take(org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1.encodeC(other))).toOption.get
    val changed = Vector(json.mapObject(_.add("component", Json.fromString("org.example.Foreign"))),
      json.hcursor.downField("operation").withFocus(_.mapObject(_.add("operation", Json.fromString("other")))).top.get,
      json.hcursor.downField("payload").withFocus(_.mapObject(_.add("typeIdentity", Json.fromString("other")))).top.get,
      json.hcursor.downField("reference").withFocus(_.mapObject(_.add("revision", Json.fromString("other")))).top.get,
      json.mapObject(_.add("next", otherjson)))
    changed.foreach { value =>
      When("the owner consumes one crossed native owner/Operation/type/reference or genuinely existing instance target")
      take(call.writeC(declaration.continuations, key, Record.data("mechanicalPayload" -> (prefix + value.noSpaces)), true))
      val refused = read
      Then("the actual receipt or selected-instance boundary refuses, with both instances unchanged")
      refused.isSuccess shouldBe false
      assertNative(call, current)
      take(instances.loadForCallC(f.configuration, identity, call.core)).get shouldBe other
      take(call.writeC(declaration.continuations, key, row, true))
    }
  }

  /** Private obsolete-row mutations exercise real owning consumers, not a codec oracle. */
  private def _specify_unused_successor_copy[A](call: Probe, target: DataStore.CollectionId,
    key: String, field: String, wire: String, expected: A, current: InstanceRecord)(
    read: => Consequence[A], execute: => Consequence[A]): Unit = {
    val original = take(call.rowC(target, key))
    val separator = wire.indexOf(':')
    val frame = parser.parse(wire.substring(separator + 1)).toOption.get
    val payload = frame.hcursor.downField("payload").focus.get
    val prefix = wire.substring(0, separator + 1)
    val legacy = frame.hcursor.downField("payload").withFocus(_.mapObject(fields =>
      fields.toVector.foldLeft(fields) { case (changed, (name, _)) =>
        if (name == "reference") changed else changed.add(name, Json.fromString("unused-malformed-copy"))
      })).top.get
    val variants = Vector(
      Record.data(field -> (prefix + legacy.spaces2)),
      Record.data(field -> "not-json"),
      Record.data(field -> (prefix + frame.mapObject(_.add("payload",
        payload.mapObject(_.remove("reference")))).noSpaces)),
      Record.data(field -> (prefix + frame.hcursor.downField("payload").downField("reference")
        .withFocus(_.mapObject(_.add("revision", Json.fromString("foreign")))).top.get.noSpaces)),
      Record.data("unusedPhysicalColumn" -> "no selected field"))
    try {
      Given("Spec: docs/spec/split-phase-checkpoint-frame-applicability.md; a real native successor and a missing unused application copy")
      original.foreach(_ => take(call.deleteFixtureRowC(target, key)))
      When("owning load and execution consume the genuine predecessor-derived native address")
      val withoutcopy = take(read)
      val repeatedwithoutcopy = take(execute)
      Then("the actual result returns without creating an application copy or appending again")
      withoutcopy shouldBe expected
      repeatedwithoutcopy shouldBe expected
      take(call.rowC(target, key)) shouldBe None
      assertNative(call, current)
      variants.foreach { row =>
        Given("an obsolete full, malformed, crossed-address or unrelated physical row at the old copy address")
        take(call.writeC(target, key, row))
        When("the owning consumers read and execute the real native successor")
        val loaded = take(read)
        val repeated = take(execute)
        Then("the unused row cannot select or reject the result and is not decoded, compared or rewritten")
        loaded shouldBe expected
        repeated shouldBe expected
        row.fields.foreach(member => take(call.rowC(target, key)).get.getString(member.key) shouldBe row.getString(member.key))
        assertNative(call, current)
        take(call.deleteFixtureRowC(target, key))
      }
    } finally {
      if (take(call.rowC(target, key)).nonEmpty) take(call.deleteFixtureRowC(target, key))
      original.foreach(row => take(call.writeC(target, key, row)))
    }
  }

  /** Real owning Scope reads select native results without application locators. */
  private def _specify_scope_native_result(call: Probe, port: NM.Port, command: SplitPhaseAdvanceCommand,
    targets: U.Targets, expected: NC.Record): Unit = {
    Given("an earlier Scope binding selecting another unused estimates collection, with the actual current owner and native result")
    val earlier = take(NM.bindC(_earlier_host, declaration, f.catalog))
    When("that binding reads and executes the retained result through the actual current owning call")
    val actual = take(earlier.loadForCallC(command, call.core)).get
    val repeated = take(earlier.executeForCallC(command, call.core))
    Then("actual current targets return the same Scope result without a construction-copy proof or append")
    actual.record shouldBe expected
    repeated.record shouldBe expected
    assertNative(call, expected.next)
    _specify_passive_checkpoint_states(call, M.Reference(expected.reference.identity, expected.reference.revision),
      expected.next, expected)(port.loadForCallC(command, call.core).map(_.get.record),
      port.executeForCallC(command, call.core).map(_.record))
    _specify_unused_successor_copy(call, targets.publication, take(NC.keyC(expected.reference)), NJ.storageField,
      take(NJ.encodeC(expected)), expected, expected.next)(
      port.loadForCallC(command, call.core).map(_.get.record), port.executeForCallC(command, call.core).map(_.record))

    val reference = M.Reference(expected.reference.identity, expected.reference.revision)
    val nativekey = take(M.keyC(reference))
    val nativerow = take(call.rowC(declaration.continuations, nativekey)).get
    val prefix = "cncf.workflow-json.v1:"
    val json = parser.parse(nativerow.getString("mechanicalPayload").get.substring(prefix.length)).toOption.get
    val extended = json.hcursor.downField("payload").downField("value").downField("payload")
      .withFocus(_.mapObject(_.add("scopeWork", Json.False).add("scopePredecessor", Json.Null).add("scopeIssued", Json.arr()))).top.get
    Given("an actual native receipt retaining only inert removed legacy certificate members")
    try {
      take(call.writeC(declaration.continuations, nativekey, Record.data("mechanicalPayload" -> (prefix + extended.noSpaces)), true))
      _store_passive_postimage(call, reference)
      When("the native payload reader and owning Scope consumer read the saved result")
      val loaded = take(port.loadForCallC(command, call.core)).get
      val repeated = take(port.executeForCallC(command, call.core))
      Then("removed certificates are ignored and actual receipt state supplies the native postimage")
      loaded.record shouldBe expected
      repeated.record shouldBe expected
      assertNative(call, expected.next)
    } finally take(call.writeC(declaration.continuations, nativekey, nativerow, true))
    _specify_native_checkpoint_selection(call, reference, expected.next)(port.loadForCallC(command, call.core))
    Given("the selected native receipt is actually missing after its native append")
    try {
      take(call.deleteFixtureRowC(declaration.continuations, nativekey))
      When("owning load and execution try to consume the missing result")
      val missingload = port.loadForCallC(command, call.core)
      val missingexecution = port.executeForCallC(command, call.core)
      Then("actual CAS prevents a stale replay without reissuing, appending or recreating the receipt")
      take(missingload) shouldBe None
      missingexecution.isSuccess shouldBe false
      take(call.rowC(declaration.continuations, nativekey)) shouldBe None
      assertNative(call, expected.next)
    } finally take(call.writeC(declaration.continuations, nativekey, nativerow,
      update = take(call.rowC(declaration.continuations, nativekey)).nonEmpty))
  }

  /** Owning Human observations consume native results, not full application copies. */
  private def _specify_human_native_result(call: Probe, port: SplitPhaseManagedHumanResolutionCheckpoint.Port,
    command: SplitPhaseAdvanceCommand,
    expected: SplitPhaseHumanResolutionCheckpointPreparation.Record): Unit = {
    Given("an earlier Human binding selecting another unused estimates collection, with the actual owning source and native result")
    val earlier = take(SplitPhaseManagedHumanResolutionCheckpoint.bindC(_earlier_host, declaration, f.catalog))
    When("that binding reads and executes the retained Human result through the actual current owning call")
    val actual = take(earlier.loadForCallC(command, call.core)).get
    val repeated = take(earlier.executeForCallC(command, call.core))
    Then("actual current targets return the same Human result without a construction-copy proof, consent or append")
    actual.record shouldBe expected
    repeated.record shouldBe expected
    assertNative(call, expected.next)
    _specify_passive_checkpoint_states(call, M.Reference(expected.reference.identity, expected.reference.revision),
      expected.next, expected)(port.loadForCallC(command, call.core).map(_.get.record),
      port.executeForCallC(command, call.core).map(_.record))
    _specify_unused_successor_copy(call, collection("humancheckpoints"),
      take(SplitPhaseHumanResolutionCheckpointPreparation.keyC(expected.reference)),
      SplitPhaseHumanResolutionCheckpointJsonV1.storageField,
      take(SplitPhaseHumanResolutionCheckpointJsonV1.encodeC(expected)), expected, expected.next)(
      port.loadForCallC(command, call.core).map(_.get.record), port.executeForCallC(command, call.core).map(_.record))

    val reference = M.Reference(expected.reference.identity, expected.reference.revision)
    val nativekey = take(M.keyC(reference))
    val nativerow = take(call.rowC(declaration.continuations, nativekey)).get
    val prefix = "cncf.workflow-json.v1:"
    val json = parser.parse(nativerow.getString("mechanicalPayload").get.substring(prefix.length)).toOption.get
    val extended = json.hcursor.downField("payload").downField("value").downField("payload")
      .withFocus(_.mapObject(_.add("priorScope", Json.False).add("humanQuestion", Json.Null)
        .add("humanCompletion", Json.arr()).add("humanIssued", Json.fromString("unused")))).top.get
    Given("the native Human result retains unreadable removed certificate fields and an old passive postimage")
    try {
      take(call.writeC(declaration.continuations, nativekey, Record.data("mechanicalPayload" -> (prefix + extended.noSpaces)), true))
      _store_passive_postimage(call, reference)
      When("the native payload reader and owning Human consumer read that retained result")
      val loaded = take(port.loadForCallC(command, call.core)).get
      val repeated = take(port.executeForCallC(command, call.core))
      Then("unused certificates are ignored and actual receipt states supply the native result postimage")
      loaded.record shouldBe expected
      repeated.record shouldBe expected
      assertNative(call, expected.next)
    } finally take(call.writeC(declaration.continuations, nativekey, nativerow, true))

    Given("the native Human receipt is actually missing after its native append")
    try {
      take(call.deleteFixtureRowC(declaration.continuations, nativekey))
      When("owning load and execution attempt to consume that missing result")
      val missingload = port.loadForCallC(command, call.core)
      val missingexecution = port.executeForCallC(command, call.core)
      Then("actual CAS prevents a stale replay without reinterpreting, appending or recreating the receipt")
      take(missingload) shouldBe None
      missingexecution.isSuccess shouldBe false
      take(call.rowC(declaration.continuations, nativekey)) shouldBe None
      assertNative(call, expected.next)
    } finally take(call.writeC(declaration.continuations, nativekey, nativerow,
      update = take(call.rowC(declaration.continuations, nativekey)).nonEmpty))
  }

  /** Owning ScopePlanning reads consume saved native results, not source copies. */
  private def _specify_scope_planning_native_result(call: Probe, port: SplitPhaseManagedScopePlanningCheckpoint.Port,
    command: SplitPhaseAdvanceCommand, targets: U.Targets,
    expected: SplitPhaseScopePlanningCheckpointPreparation.Record): Unit = {
    Given("an earlier ScopePlanning binding selecting another unused estimates collection, with the actual owning Scope and native result")
    val earlier = take(SplitPhaseManagedScopePlanningCheckpoint.bindC(_earlier_host, declaration, f.catalog))
    When("that binding reads and executes the retained planning result through the actual current owning call")
    val actual = take(earlier.loadForCallC(command, call.core)).get
    val repeated = take(earlier.executeForCallC(command, call.core))
    Then("actual current targets return the saved planning result without a construction-copy proof or append")
    actual.record shouldBe expected
    repeated.record shouldBe expected
    assertNative(call, expected.next)
    _specify_passive_checkpoint_states(call, M.Reference(expected.reference.identity, expected.reference.revision),
      expected.next, expected)(port.loadForCallC(command, call.core).map(_.get.record),
      port.executeForCallC(command, call.core).map(_.record))
    _specify_unused_successor_copy(call, targets.publication,
      take(SplitPhaseScopePlanningCheckpointPreparation.keyC(expected.reference)),
      SplitPhaseScopePlanningCheckpointJsonV1.storageField,
      take(SplitPhaseScopePlanningCheckpointJsonV1.encodeC(expected)), expected, expected.next)(
      port.loadForCallC(command, call.core).map(_.get.record), port.executeForCallC(command, call.core).map(_.record))

    val reference = M.Reference(expected.reference.identity, expected.reference.revision)
    val nativekey = take(M.keyC(reference))
    val nativerow = take(call.rowC(declaration.continuations, nativekey)).get
    val prefix = "cncf.workflow-json.v1:"
    val json = parser.parse(nativerow.getString("mechanicalPayload").get.substring(prefix.length)).toOption.get
    val extended = json.hcursor.downField("payload").downField("value").downField("payload")
      .withFocus(_.mapObject(_.add("priorScope", Json.False))).top.get
    Given("the actual native ScopePlanning result has an unreadable removed Scope copy and an old passive postimage")
    try {
      take(call.writeC(declaration.continuations, nativekey, Record.data("mechanicalPayload" -> (prefix + extended.noSpaces)), true))
      _store_passive_postimage(call, reference)
      When("the native reader and owning ScopePlanning load and execution consume the result")
      val loaded = take(port.loadForCallC(command, call.core)).get
      val repeated = take(port.executeForCallC(command, call.core))
      Then("the removed copy is ignored and actual outer receipt states supply the same saved calculation")
      loaded.record shouldBe expected
      repeated.record shouldBe expected
      assertNative(call, expected.next)
    } finally take(call.writeC(declaration.continuations, nativekey, nativerow, true))

    Given("the actual selected native calculation is missing after its native append")
    try {
      take(call.deleteFixtureRowC(declaration.continuations, nativekey))
      When("owning load and execution attempt to consume that missing calculation")
      val missingload = port.loadForCallC(command, call.core)
      val missingexecution = port.executeForCallC(command, call.core)
      Then("actual CAS prevents a stale replay without another calculation, append or recreated receipt")
      take(missingload) shouldBe None
      missingexecution.isSuccess shouldBe false
      take(call.rowC(declaration.continuations, nativekey)) shouldBe None
      assertNative(call, expected.next)
    } finally take(call.writeC(declaration.continuations, nativekey, nativerow,
      update = take(call.rowC(declaration.continuations, nativekey)).nonEmpty))
  }

  private def _specify_stale_checkpoint[A](call: Probe, intent: M.Intent[A], rows: Vector[U.Row], storename: String,
    codec: org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec[A], expected: InstanceRecord): Unit = {
    Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R15; Example: E32; passive historical descriptors and the real current native predecessor")
    val binding = call.executionContext.dataStoreSpace.captureBinding()
    val prior = rows.map(row => take(call.rowC(row.collection, row.identity)))
    val receiptkey = take(M.keyC(intent.reference))
    val priorreceipt = take(call.rowC(declaration.continuations, receiptkey))
    val programs = rows.map { row =>
      val storage = take(org.goldenport.cncf.datastore.ComponentRecordStorage.bindForCallC(call.core,
        storename, row.collection, DataStore.StringEntryId(row.identity)))
      take(storage.stageCreateC(row.payload))
    }
    val program = programs.foldLeft(org.goldenport.ConsequenceT.pure[
      [X] =>> org.goldenport.cncf.Program[org.goldenport.cncf.unitofwork.UnitOfWorkOp, X], Unit](()))(
      (before, next) => before.flatMap(_ => next))
    val native = take(take(org.goldenport.cncf.workflow.WorkflowComponentStorage.bindC(
      ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(f.catalog.definition),
      declaration.instances, declaration.continuations)).bindMechanicalC(K.advanceOperation, codec))
    When("the actual managed native executor consumes the historical expectedRevision and all staged Creates")
    val rejected = native.executeC(intent, program)(using call.executionContext)
    Then("actual stale applicability refuses with no native append, receipt, application or document change")
    rejected shouldBe a[Consequence.Failure[?]]
    rejected.toString should include ("CWF77-WORKFLOW-PERSISTENCE-007")
    assertNative(call, expected)
    rows.map(row => take(call.rowC(row.collection, row.identity))) shouldBe prior
    take(call.rowC(declaration.continuations, receiptkey)) shouldBe priorreceipt
    call.executionContext.dataStoreSpace.captureBinding() shouldBe binding
  }

  private def withHost(path: Path, declared: Map[String, ConfigurationValue] = values,
    denied: Option[DataStore.CollectionId] = None, operationName: String = K.advanceOperation.operation,
    participant: String = "participant", rowCheck: () => Consequence[Unit] = () => Consequence.unit,
    publicadvance: Boolean = false, retainedmechanical: Option[(K.Record, InstanceRecord)] = None,
    executionclock: Option[java.time.Clock] = None,
    humanverifier: Option[SplitPhaseHumanAnswerVerification.EvidencePort] = None,
    humanreadback: () => Consequence[Unit] = () => Consequence.unit,
    terminalreadback: () => Consequence[Unit] = () => Consequence.unit,
    reasoningcompletion: Boolean = false,
    processruntime: Option[(org.goldenport.cncf.processexecution.ProcessExecutionDriver,
      org.goldenport.cncf.processexecution.ProcessExecutionAdmission)] = None,
    advancecommand: Option[SplitPhaseAdvanceCommand] = None)
    (task: (Probe, Managed.Port) => Consequence[Unit]): Unit = {
    // A process-enabled host needs the same canonical WorkArea prerequisite as
    // the normal runtime. ExecutionContext.create() alone is intentionally inert.
    if (processruntime.nonEmpty) org.goldenport.cncf.context.GlobalContext.set(
      org.goldenport.cncf.context.GlobalContext(org.goldenport.cncf.workarea.WorkAreaSpace.create(
        org.goldenport.cncf.config.RuntimeConfig.default)))
    val selectedcommand = advancecommand.getOrElse(query(capture()))
    val ctx = ExecutionContext.create()
    val configured = host(declared ++ Map("textus.subsystem.user-mode" -> ConfigurationValue.StringValue("standalone"),
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"), "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)))
    val policy = take(EventPersistencePolicy.createC("managed-checkpoint-v1", Vector(
      EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
    val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"), collection("indexes"),
      Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId, "workflow", declaration.instances, declaration.continuations))))
    val targets = take(U.bindTargetsC(configured, declaration))
    // Only private obsolete-row probes use this grant; product binding has no Human copy target.
    val humanpublications = Vector(collection("humancheckpoints"))
    val grants = take(ComponentRecordStorageDeclaration.createC((targets.localCollections ++ humanpublications).filterNot(denied.contains)
      .map(c => ComponentRecordStorageDeclaration.CreateGrant(SmWorkflowComponent.componentId, "workflow", c))))
    val bootstrap = take(SubsystemEventStoreBootstrap.createC(policy, storage,
      ScopeContext(ScopeKind.Subsystem, "managed-checkpoint", None, ctx.observability,
        processexecutiondriveroption = processruntime.map(_._1), processexecutionadmissionoption = processruntime.map(_._2)), configured, None,
      org.goldenport.cncf.path.AliasResolver.empty, grants))
    try {
      val owner = RuntimeBindingAdmissionFixture.admit(bootstrap.subsystem)
      val port = take(Managed.bindWithRowObservationC(owner.configuration, declaration, f.catalog, rowCheck))
      var captured = Vector.empty[ActionCall.Core]
      val probeoperationname = if (publicadvance) "managedAdvanceFixture" else operationName
      val operation = new spec.OperationDefinition {
        val specification = spec.OperationDefinition.Specification(name = probeoperationname, request = WorkflowJsonCommandRequest.definition,
          response = spec.ResponseDefinition.void)
        def createOperationRequest(r: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
          val request = r
          override def commandExecutionMode = CommandExecutionMode.Sync
          def createCall(c: ActionCall.Core): ActionCall = {
            val actual = executionclock.fold(c)(clock => c.copy(executionContext =
              org.goldenport.cncf.testutil.ExecutionContextTestFixture.withClock(c.executionContext, clock)))
            captured :+= actual
            new Probe(actual, call => task(call, port).flatMap { _ =>
              retainedmechanical match {
                case None => Consequence.unit
                case Some((expected, current)) =>
                  When("the owning advance fixture observes its retained root within the actual managed dispatch")
                  port.loadMechanicalForCallC(selectedcommand, call.core).flatMap {
                    case Some(observed) =>
                      Then("the complete original root and later native current record remain exact")
                      observed.record shouldBe expected
                      observed.current shouldBe current
                      Consequence.unit
                    case None => Consequence.stateConflict("Owning advance fixture has no retained mechanical root")
                  }
              }
            })
          }
        })
      }
      val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty, (creation, parameters, resolved) =>
        if (publicadvance) humanverifier.fold(
          SplitPhaseManagedServiceAssembly.bindHostPlanningContinuationC(creation, declaration, f.catalog, terminalreadback))(
          verifier => SplitPhaseManagedServiceAssembly.bindHostHumanWithReadbackObservationC(
            creation, declaration, f.catalog, verifier, humanreadback, terminalreadback))
          .map(service => spec.ServiceDefinition("SplitPhaseService", spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(
            Vector(operation) ++ service.operations.operations.toVector))))
        else if (reasoningcompletion) SplitPhaseManagedReasoningCompletion.bindHostC(creation, declaration, f.catalog)
          .map(reasoning => spec.ServiceDefinition("SplitPhaseService", spec.OperationDefinitionGroup(NonEmptyVector.of(operation, reasoning))))
        else Consequence.success(spec.ServiceDefinition("SplitPhaseService", spec.OperationDefinitionGroup(NonEmptyVector.of(operation))))).primaryFactory
      val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin))); owner.add(built)
      owner.withDescriptor(GenericSubsystemDescriptor(path = Path.of("<managed-checkpoint-spec>"), subsystemName = "managed-checkpoint",
        componentBindings = Vector(GenericSubsystemComponentBinding(built.name)), security = Some(GenericSubsystemSecurityBinding(
          authentication = Some(GenericSubsystemAuthenticationBinding(convention = Some("enabled"), fallbackPrivilege = Some("disabled"),
            localSubject = Some(GenericSubsystemLocalSubjectBinding(id = participant, roles = Vector("user"), capabilities = Vector("user"),
              attributes = Map.empty, securityLevel = Some("user")))))))))
      val actor = take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.Fixed, built.logic.executionContext(), Map.empty)).executionContext
      val wire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(selectedcommand))
      take(owner.executeOperationResponseInContext(Request.of(component = built.name, service = "SplitPhaseService", operation = probeoperationname,
        arguments = List(Argument("arg1", wire))), actor))
      take(new CncfRuntime().executeCommandResponse(owner, Array(s"${built.name}/SplitPhaseService/$probeoperationname", "--json", wire)))
      captured.size shouldBe 2
      retainedmechanical match {
        case None => port.loadMechanicalForCallC(selectedcommand, captured.last).isSuccess shouldBe false
        case Some(_) =>
          When("the captured owning core is reused after both managed dispatches have ended")
          val rejected = port.loadMechanicalForCallC(selectedcommand, captured.last)
          Then("the expired call grants no mechanical storage access despite its correct Operation and participant")
          rejected.isSuccess shouldBe false
      }
      NativeHumanManagedHostObservation.counts(bootstrap) shouldBe (1, 0)
    } finally take(bootstrap.shutdownC())
  }
  private def instances = CallScopedWorkflowInstances.managed(f.configuration, f.catalog.definition, declaration.instances, declaration.continuations)
  private def installC(call: Probe, saved: K.Captured): Consequence[Unit] = originalRows(saved)
    .foldLeft(Consequence.unit) { case (prior, (c, id, row)) => prior.flatMap(_ => call.writeC(c, id, row)) }
    .flatMap(_ => instances.createForCallC(f.configuration, f.initial, call.core))
    .flatMap(_ => instances.appendForCallC(f.configuration, f.initial.identity, initialRevision, saved.before.history.head, call.core)).map(_ => ())
  private def assertNative(call: Probe, expected: InstanceRecord): Unit =
    take(instances.loadForCallC(f.configuration, expected.identity, call.core)).get shouldBe expected
  private def assertRows(call: Probe, saved: K.Captured): Unit = {
    val targets = take(U.bindTargetsC(host(values), declaration))
    take(U.prepareC(saved, targets)).rows.foreach { row =>
      val loaded = take(call.rowC(row.collection, row.identity)).get
      row.payload.fields.foreach(field => loaded.getString(field.key) shouldBe row.payload.getString(field.key))
    }
    take(call.rowC(declaration.continuations, take(M.keyC(anchor)))).nonEmpty shouldBe true
  }

  private def _assert_no_checkpoint(call: Probe, saved: K.Captured): Unit = {
    val targets = take(U.bindTargetsC(host(values), declaration))
    take(U.prepareC(saved, targets)).rows.foreach { row =>
      take(call.rowC(row.collection, row.identity)) shouldBe None
    }
    take(call.rowC(declaration.continuations, take(M.keyC(anchor)))) shouldBe None
  }

  private def _specify_scope_publication(call: Probe, captured: NC.Captured, targets: U.Targets): Unit = {
    Given("a genuine owning scope capture and its retained first mapping, index and already published documents")
    val first = captured.record.snapshot.first
    val rootkey = take(U.mappingKeyC(first.reference))
    val indexkey = take(U.indexKeyC(first.originalStart.instanceIdentity, first.originalContext.reference))
    val rootbefore = take(call.rowC(targets.publication, rootkey)).get
    val indexbefore = take(call.rowC(targets.publication, indexkey)).get
    val nativebefore = take(instances.loadForCallC(f.configuration, captured.record.before.identity, call.core)).get
    val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
    val retained = first.snapshot.projection.toVector.flatMap(_.candidates).collect {
      case value: SplitPhasePlanningSnapshot.CandidateProjectionView.BuiltView => value
    }.flatMap(document => Vector(targets.proposals -> document.proposal.reference.identity,
      targets.ownerships -> document.ownership.reference.identity, targets.handoffs -> document.handoffs.reference.identity))
    val retainedbefore = retained.map { (collection, identity) => take(call.rowC(collection, identity)).get }
    val newdocuments = captured.record.snapshot.frontier match {
      case NV.Frontier.HumanDirectionRequired => Vector.empty
      case NV.Frontier.Applied(candidates) => candidates.collect {
        case NV.Candidate(_, _, NV.CandidateOutput.Built(value, false)) => value
      }
    }
    val expectedrows = newdocuments.flatMap(document => Vector(
      U.Row(targets.proposals, document.proposal.reference.identity,
        Record.data(F.storageField -> take(F.encodeProposalC(document.proposal)))),
      U.Row(targets.ownerships, document.ownership.reference.identity,
        Record.data(F.storageField -> take(F.encodeOwnershipC(document.ownership)))),
      U.Row(targets.handoffs, document.handoffs.reference.identity,
        Record.data(F.storageField -> take(F.encodeHandoffsC(document.handoffs))))))
    When("the full empty-event publication descriptors are prepared twice without executing a program")
    val publication = take(NU.prepareC(captured, targets))
    val repeated = take(NU.prepareC(captured, targets))
    val actualnewrows = publication.rows.map(row => take(call.rowC(row.collection, row.identity)))
    val retainedafter = retained.map { (collection, identity) => take(call.rowC(collection, identity)).get }
    val rootafter = take(call.rowC(targets.publication, rootkey)).get
    val indexafter = take(call.rowC(targets.publication, indexkey)).get
    val nativeafter = take(instances.loadForCallC(f.configuration, captured.record.before.identity, call.core)).get
    Then("only new Built documents are proposed, with no application-copy producer, write or grant")
    publication.captured shouldBe captured
    publication.targets shouldBe targets
    publication.rows shouldBe expectedrows
    publication.rows.size shouldBe newdocuments.size * 3
    captured.record.snapshot.frontier match {
      case NV.Frontier.HumanDirectionRequired => publication.rows shouldBe Vector.empty
      case _: NV.Frontier.Applied =>
        newdocuments.nonEmpty shouldBe true
        retained.nonEmpty shouldBe true
    }
    repeated.rows shouldBe publication.rows
    actualnewrows.foreach(_ shouldBe None)
    retainedafter shouldBe retainedbefore
    rootafter shouldBe rootbefore
    indexafter shouldBe indexbefore
    nativeafter shouldBe nativebefore
    call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding

    Given("complete65-row descriptors and the genuine unexecuted Scope intent")
    val capacityrows = Vector.tabulate(65)(index => U.Row(targets.proposals, s"capacity-$index",
      Record.data(F.storageField -> "structural-document-descriptor")))
    When("preparation cannot derive documents because its original capture or declared targets are absent")
    val preparationfailures = Vector(NU.prepareC(null, targets), NU.prepareC(captured, null))
    val afterrefusals = take(instances.loadForCallC(f.configuration, captured.record.before.identity, call.core)).get
    Then("no publication is derived and the actual native state and binding remain unchanged")
    preparationfailures.foreach(_ shouldBe a[Consequence.Failure[?]])
    afterrefusals shouldBe nativebefore
    call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
    _specify_native_create_refusal(call, captured.intent, NJ.payloadCodec, capacityrows, targets.storeName, nativebefore)
    assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseScopeProjectionCheckpointPublication.Prepared(null, null, Vector.empty)")
  }

  /** Real already captured Scope/Human intents share the actual consuming owner.
    * This helper refuses effects; it does not construct native completion data.
    */
  private def _specify_native_create_refusal[A](call: Probe, intent: M.Intent[A],
    codec: org.goldenport.cncf.workflow.WorkflowResultJsonV1.PayloadCodec[A],
    rows: Vector[U.Row], storename: String, expected: InstanceRecord): Unit = {
    Given("a genuine owning Scope or Human intent, complete65-row descriptors and the actual native executor")
    rows.size shouldBe 65
    val binding = call.executionContext.dataStoreSpace.captureBinding()
    val native = take(take(org.goldenport.cncf.workflow.WorkflowComponentStorage.bindC(
      ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
      SmWorkflowComponent.componentId, declaration.configuration, Vector(f.catalog.definition),
      declaration.instances, declaration.continuations)).bindMechanicalC(K.advanceOperation, codec))
    val receiptkey = take(M.keyC(intent.reference))
    for ((row, reason) <- Vector(
      rows.head.copy(collection = declaration.instances) -> "Component record Create scope is not declared",
      rows.head.copy(payload = Record.data("id" -> "caller-selected", F.storageField -> "closed-data")) ->
        "Component record payload cannot select its physical identity")) {
      Given("the current owning call and data requesting protected storage or a payload-selected physical identity")
      When("the actual Subsystem issues a fixed-target capability and stageCreateC consumes its payload")
      val refused = org.goldenport.cncf.datastore.ComponentRecordStorage.bindForCallC(
        call.core, storename, row.collection, DataStore.StringEntryId(row.identity)).flatMap(_.stageCreateC(row.payload))
      Then("the responsible native storage boundary refuses before effects, without a passive row verifier")
      refused match {
        case Consequence.Failure(conclusion) => conclusion.display shouldBe reason
        case _ => fail("Invalid actual storage request incorrectly returned success")
      }
      assertNative(call, expected)
      take(call.rowC(declaration.continuations, receiptkey)) shouldBe None
      take(call.rowC(rows.head.collection, rows.head.identity)) shouldBe None
      call.executionContext.dataStoreSpace.captureBinding() shouldBe binding
    }
    for ((selected, reason) <- Vector(
      rows -> "Mechanical program exceeds its bounded Create plan",
      Vector(rows.head, rows.head) -> "Mechanical program duplicates a Create target")) {
      Given("every selected row staged through the current owning ComponentRecordStorage")
      val programs = selected.map { row =>
        val storage = take(org.goldenport.cncf.datastore.ComponentRecordStorage.bindForCallC(
          call.core, storename, row.collection, DataStore.StringEntryId(row.identity)))
        take(storage.stageCreateC(row.payload))
      }
      val program = programs.foldLeft(org.goldenport.ConsequenceT.pure[
        [X] =>> org.goldenport.cncf.Program[org.goldenport.cncf.unitofwork.UnitOfWorkOp, X], Unit](()))(
        (before, next) => before.flatMap(_ => next))
      When("the genuine Scope or Human native owner consumes an oversized or duplicate actual Create program")
      val refused = native.executeC(intent, program)(using call.executionContext)
      Then("the exact program-consumer cause refuses every effect without changing native state or current binding")
      refused match {
        case Consequence.Failure(conclusion) => conclusion.display shouldBe reason
        case _ => fail("Invalid Scope or Human actual Create program incorrectly returned success")
      }
      assertNative(call, expected)
      take(call.rowC(declaration.continuations, receiptkey)) shouldBe None
      selected.foreach(row => take(call.rowC(row.collection, row.identity)) shouldBe None)
      call.executionContext.dataStoreSpace.captureBinding() shouldBe binding
    }
  }

  private def _public_advance_c(call: Probe, wire: String, cli: Boolean): Consequence[OperationResponse] = {
    _public_operation_c(call, K.advanceOperation.operation, wire, cli)
  }
  private def _public_operation_c(call: Probe, operation: String, wire: String, cli: Boolean): Consequence[OperationResponse] = {
    val component = call.core.component.get
    val owner = component.subsystem.get
    if (cli) new CncfRuntime().executeCommandResponse(owner,
      Array(s"${component.name}/SplitPhaseService/$operation", "--json", wire))
      .map(response => OperationResponse.Scalar(response.print))
    else owner.executeOperationResponseInContext(Request.of(component = component.name,
      service = K.advanceOperation.service, operation = operation,
      arguments = List(Argument("arg1", wire))), call.executionContext)
  }
  private def _semantic_result(issued: WorkflowInteraction[SplitPhaseExplicitPartitionSemantic.Request, Nothing],
    decision: SplitPhaseExplicitPartitionSemantic.Decision,
    maximumminutes: Int = 180): ContinuationResult[SplitPhaseExplicitPartitionSemantic.Judgment] = {
    import SplitPhaseExplicitPartitionSemantic as S
    val work = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
    val input = work.input.get.value
    val enriched = decision == S.Decision.Enriched
    val judgment = S.Judgment(decision, "requested-semantics-only",
      if (enriched) input.items.map(i => S.NovelEstimate(i.identity, 120, maximumminutes, "bounded-work", "novel-closure")) else Vector.empty,
      if (enriched) input.boundaries.map(b => S.BoundaryJudgment(b.edge.afterItem,
        SplitPhasePartitionEnumeration.EdgeDisposition.Either, "independent-closure")) else Vector.empty,
      Vector(S.EvidenceReference("public-semantic-evidence", "1")))
    ContinuationResult(issued.handle, work.runId, work.continuationId, work.expectedRevision,
      work.context.snapshot, TypedValue("SplitPhasePartitionSemanticResult", judgment),
      ContextReference(input.reply.result.identity, input.reply.result.revision), Vector.empty,
      ExecutionEvidence(Vector(ContextReference("public-semantic-evidence", "1")), Some("participant")))
  }
  private def _scalar(response: OperationResponse): String = response match {
    case OperationResponse.Scalar(value: String) => value
    case _ => fail("Public advance did not return its complete scalar wire")
  }

  /** Genuine public issue/completion setup only; never installs a passive origin
    * as a receipt. The internal managed publication remains a separate call.
    */
  private def _complete_scope_fixture(call: Probe, saved: K.Captured,
    decision: SplitPhaseScopeSemantic.Decision, partitionroot: Boolean, broadscope: Boolean = false,
    broadreceiver: Boolean = false, partitionminutes: Int = 180): K.Record = {
    import org.goldenport.cncf.workflow.{WorkflowWorkOrderJsonV1, WorkflowResultJsonV1}
    import SplitPhaseScopeSemantic as SS
    import SplitPhaseScopeSemanticJson as SJ
    import SplitPhaseExplicitPartitionSemantic as PS
    import SplitPhaseExplicitPartitionSemanticJson as PJ
    val wire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved)))
    Given("the actual public continuation pair and the original typed facts for this root")
    if (partitionroot) {
      val original = saved.record.originalContext.facts
      val estimates = original.partition.estimates
      val facts = original.copy(partition = original.partition.copy(estimates = estimates.copy(items =
        estimates.items.updated(0, estimates.items.head.copy(duration =
          SplitPhaseExplicitPartitionComputation.Duration.Unresolved("original semantic estimate required"))))))
      val context = saved.record.originalContext.copy(facts = facts)
      _original_rows(O.Original(context, saved.record.originalStart, facts), saved.record.originalStart).foreach { case (c, id, row) =>
        take(call.writeC(c, id, row))
      }
      take(instances.createForCallC(f.configuration, f.initial, call.core))
      take(instances.appendForCallC(f.configuration, saved.before.identity, initialRevision, saved.before.history.head, call.core))
      When("public advance requests the unresolved estimate and its genuine assigned reply is completed")
      val issued = take(WorkflowWorkOrderJsonV1.decodeC(_scalar(take(_public_advance_c(call, wire, false))), PJ.requestCodec))
      val result = _semantic_result(issued, PS.Decision.Enriched, partitionminutes)
      take(_public_operation_c(call, SplitPhaseExplicitPartitionContext.completionOperation.operation,
        take(WorkflowResultJsonV1.encodeC(result, PJ.resultCodec)), true))
    } else take(installC(call, saved))
    When("public advance issues actual handoff scope work from the selected first root")
    val issued = take(WorkflowWorkOrderJsonV1.decodeC(_scalar(take(_public_advance_c(call, wire, true))), SJ.requestCodec))
    val order = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[SS.Request]].request
    val input = order.input.get.value
    val judgment = SS.Judgment(decision, "managed publication transfer analysis",
      if (decision == SS.Decision.Enriched) input.gaps.map(gap => SS.Scope(gap.original.identity,
        if (broadscope) gap.producingItems else gap.producingItems.takeRight(1),
        if (broadscope || broadreceiver) gap.receivingItems else gap.receivingItems.take(1), "actual producer and receiver")) else Vector.empty,
      Vector(PS.EvidenceReference("managed-publication-evidence", "1")))
    val reply = ContinuationResult(issued.handle, order.runId, order.continuationId, order.expectedRevision,
      order.context.snapshot, TypedValue(SJ.resultCodec.typeIdentity, judgment),
      ContextReference(input.result.identity, input.result.revision), Vector.empty,
      ExecutionEvidence(Vector(ContextReference("managed-publication-evidence", "1")), Some("participant")))
    When("the assigned scope result commits through its actual public completion Operation")
    take(_public_operation_c(call, SplitPhaseTypedScopeWorkIssue.completionOperation.operation,
      take(WorkflowResultJsonV1.encodeC(reply, SJ.resultCodec)), false))
    val targets = take(U.bindTargetsC(call.core.component.get.subsystem.get.configuration, declaration))
    val indexkey = take(U.indexKeyC(saved.before.identity, saved.record.originalContext.reference))
    val index = take(J.decodeIndexC(saved.before.identity, saved.record.originalContext.reference,
      take(call.rowC(targets.publication, indexkey)).get.getString(J.indexStorageField).get))
    val root = take(_root_c(call, saved))
    val current = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
    Then("the genuine root and actual scope completion are active and unsuspended, not application Terminal")
    root.origin.isInstanceOf[K.Origin.PartitionCompletion] shouldBe partitionroot
    root.originalStart shouldBe saved.record.originalStart
    root.snapshot.projection.get.candidates.exists(_.isInstanceOf[SplitPhasePlanningSnapshot.CandidateProjectionView.BuiltView]) shouldBe true
    current.suspension shouldBe None
    current.lifecycle shouldBe Lifecycle.Active
    root
  }

  private def _specify_human_resolution(call: Probe, saved: K.Captured,
    submitted: ContinuationResult[SplitPhaseHumanDirectionAnswerContract.Answer], correction: Boolean): Unit = {
    import SplitPhaseHumanScopeResolution as HR
    import SplitPhaseHumanScopeResolutionJsonV1 as HJ
    import SplitPhaseExplicitCandidateProjection as Q
    import SplitPhaseScopeSemantic as SS
    import SplitPhaseExplicitPartitionSemantic as T
    Given("the actual stored Human answer and a separately declared resolution-data purpose")
    val owner = call.core.component.get.subsystem.get.configuration
    val binding = call.executionContext.dataStoreSpace.captureBinding()
    When("the owning advance fixture observes the canonical Human completion after physical reopen")
    val observed = take(SplitPhaseManagedHumanCompletion.observeAnswerForAdvanceC(query(saved), declaration, f.catalog, call.core))
    val request = take(SS.requestC(observed.plan.source.record.snapshot.first))
    val record = HR.Record(HR.Reference("independent-resolution", "1"), request.context, request.checkpoint,
      observed.plan.source.record.reference, HR.DecisionReference(submitted.continuationId.value),
      request.gaps.map(gap => SS.Scope(gap.original.identity, gap.producingItems.takeRight(1),
        gap.receivingItems.take(1), "explicit Human producer and receiver interpretation")),
      "Human completes or corrects participant interpretation within original ranges",
      Vector(T.EvidenceReference("independent-human-consent", "1")))
    val port = take(SplitPhaseHumanResolutionReadPolicy.bindC(owner, declaration))
    val dataid = collection("humanresolutions")
    val targets = take(U.bindTargetsC(owner, declaration))
    val allocations = observed.plan.source.record.snapshot.first.snapshot.selection.planning.allocations.get.allocations
    val locations = allocations.flatMap(allocation => Vector(
      targets.proposals -> allocation.proposal.identity, targets.ownerships -> allocation.ownership.identity,
      targets.handoffs -> allocation.handoffs.identity))
    val documents = locations.map { (collection, identity) => (collection, identity, take(call.rowC(collection, identity))) }
    Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R17; Example: E36; complete interpretation with unused fields at every depth, not canonical consent")
    val storedwire = HJ.schema + ":" + _with_unused_fields(parser.parse(take(HJ.encodeC(record))).toOption.get).noSpaces
    if (take(call.rowC(dataid, record.reference.identity)).isEmpty)
      take(call.writeC(dataid, record.reference.identity, Record.data(HJ.storageField -> storedwire)))
    When("the real owning DataStore reads the retained interpretation field")
    val storedrow = take(call.rowC(dataid, record.reference.identity)).get
    Then("the value remains an opaque String with the complete exact storage representation")
    storedrow.fields.find(_.key == HJ.storageField).get.value.single.isInstanceOf[String] shouldBe true
    storedrow.getString(HJ.storageField) shouldBe Some(storedwire)
    When("the actual owning reader selects exactly the answer's data reference and Workflow interprets the full published frontier")
    val selected = take(port.loadForCallC(record.reference, call.core)).get
    val admitted = take(HR.admitC(observed, selected))
    val frontier = take(Q.applyHumanResolutionC(admitted))
    val next = take(W.continueHumanResolutionC(frontier))
    val previousbuilt = frontier.previous match {
      case applied: Q.ScopeApplied => applied.candidates.collect { case built: Q.ScopeBuilt => built.built }
      case _: Q.ScopeHumanDirectionRequired => frontier.previous.original.trace.projection.get
        .asInstanceOf[Q.Requirements].candidates.collect { case built: Q.Built => built }
    }
    Then("the complete ordered frontier is calculated without native append, document Create, renewed consent or allocation")
    selected shouldBe record
    admitted.observed.completion.result shouldBe submitted
    frontier.candidates.map(_.original.candidate.cutAfterIndices) shouldBe allocations.map(_.cutAfterIndices)
    frontier.projected.nonEmpty shouldBe true
    next.isInstanceOf[W.HumanResolutionContinued] shouldBe true
    previousbuilt.foreach { prior =>
      val retained = frontier.candidates.collectFirst { case built: Q.ScopeBuilt if built.built.candidate == prior.candidate => built }.get
      retained.documentsAlreadyPublished shouldBe true
      retained.built.boundary.shape.records shouldBe prior.boundary.shape.records
      retained.built.boundary.records shouldBe prior.boundary.records
      retained.built.localHandoffs shouldBe prior.localHandoffs
    }
    if (correction) {
      val firstbuilt = frontier.previous.original.trace.projection.get.asInstanceOf[Q.Requirements]
        .candidates.count(_.isInstanceOf[Q.Built])
      previousbuilt.size should be > firstbuilt
      frontier.previous.asInstanceOf[Q.ScopeApplied].candidates.exists(_.isInstanceOf[Q.ScopeUnrepresentable]) shouldBe true
    }
    assertNative(call, observed.answered)
    documents.foreach { (collection, identity, prior) => take(call.rowC(collection, identity)) shouldBe prior }
    call.executionContext.dataStoreSpace.captureBinding() shouldBe binding

    Given("the privately constructed ALL-Built calculation and its exact canonical HumanAnswered predecessor")
    When("Workflow captures the complete calculation and prepares, but does not execute, its mechanical successor")
    val captured = take(SplitPhaseHumanResolutionCheckpointPreparation.captureC(next))
    val capture = captured.record
    val downstream = take(SplitPhasePlanningSnapshot.projectC(next.asInstanceOf[W.HumanResolutionContinued].outcome))
    Then("the consumed interpretation, ordered frontier and downstream result remain distinct from an AI scope record")
    capture.reference shouldBe take(SplitPhaseHumanResolutionCheckpointPreparation.referenceC(
      observed.completion.result.continuationId, observed.completion.result.resultReference))
    take(SplitPhaseHumanResolutionCheckpointPreparation.keyC(capture.reference))
      .getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be <= 1024
    capture.resolution shouldBe record
    capture.frontier.map(_.key.cutAfterIndices) shouldBe allocations.map(_.cutAfterIndices)
    capture.frontier.map(_.transfers) shouldBe frontier.candidates.map(_.transfers)
    capture.frontier.zip(frontier.candidates).foreach { (savedcandidate, computed) =>
      val built = computed.asInstanceOf[Q.ScopeBuilt]
      savedcandidate.output shouldBe SplitPhaseScopeProjectionSnapshot.CandidateOutput.Built(
        SplitPhasePlanningSnapshot.CandidateProjectionView.BuiltView(savedcandidate.key,
          built.built.boundary.shape.records.proposal, built.built.boundary.records.ownership,
          built.built.boundary.records.handoffs, built.built.localHandoffs), built.documentsAlreadyPublished)
    }
    capture.downstream shouldBe downstream.record
    capture.before shouldBe observed.answered
    capture.next.history.init shouldBe capture.before.history
    capture.next.history.size shouldBe capture.before.history.size + 1
    capture.next.revision shouldBe capture.before.revision.next
    capture.next.lifecycle shouldBe Lifecycle.Active
    capture.next.currentProgression shouldBe capture.before.currentProgression
    capture.next.suspension shouldBe None
    capture.next.correlation shouldBe capture.before.correlation
    capture.next.derivedCompositeOccurrence shouldBe capture.before.derivedCompositeOccurrence
    capture.next.committedPredecessor shouldBe capture.before.committedPredecessor
    capture.next.causation shouldBe CausationReference(capture.reference.identity)
    captured.intent.configuration shouldBe observed.plan.source.record.snapshot.first.originalStart.configuration
    captured.intent shouldBe M.Intent(captured.intent.configuration,
      M.Reference(capture.reference.identity, capture.reference.revision), capture.before,
      capture.before.revision, capture.next.history.last,
      TypedValue(SplitPhaseHumanResolutionCheckpointPreparation.payloadType, capture.savedResult))
    assertNative(call, observed.answered)
    documents.foreach { (collection, identity, prior) => take(call.rowC(collection, identity)) shouldBe prior }
    call.executionContext.dataStoreSpace.captureBinding() shouldBe binding

    Given("the complete captured calculation and an independently selected generated definition, with no publication grant")
    val humanjson = SplitPhaseHumanResolutionCheckpointJsonV1
    val fixtureid = "human-checkpoint-codec-fixture"
    When("the owning fixture stores and reads only the opaque complete codec text through the real shared-pool DataStore")
    val wire = take(humanjson.encodeC(capture))
    if (take(call.rowC(dataid, fixtureid)).isEmpty)
      take(call.writeC(dataid, fixtureid, Record.data(humanjson.storageField -> wire)))
    val row = take(call.rowC(dataid, fixtureid)).get
    val restored = take(humanjson.decodeC(capture.reference, row.getString(humanjson.storageField).get))
    Then("the consumed saved-result data roundtrips without a synthetic native-envelope preflight")
    row.fields.find(_.key == humanjson.storageField).get.value.single.isInstanceOf[String] shouldBe true
    row.getString(humanjson.storageField) shouldBe Some(wire)
    humanjson.schema + ":" + humanjson.payloadCodec.encode(restored).noSpaces shouldBe wire
    restored.resolution shouldBe capture.resolution
    restored.frontier shouldBe capture.frontier
    restored.downstream shouldBe capture.downstream
    assertNative(call, observed.answered)
    documents.foreach { (collection, identity, prior) => take(call.rowC(collection, identity)) shouldBe prior }
    call.executionContext.dataStoreSpace.captureBinding() shouldBe binding

    Given("compatible unused metadata and removed legacy question/source certificates on the same Human result")
    val frame = parser.parse(wire.substring(humanjson.schema.length + 1)).toOption.get
    val compatible = Vector(frame.mapObject(_.add("extra", Json.Null)),
      frame.hcursor.downField("payload").withFocus(_.mapObject(_.add("extra", Json.Null)
        .add("priorScope", Json.False).add("humanQuestion", Json.fromString("unused"))
        .add("humanCompletion", Json.arr()).add("humanIssued", Json.Null))).top.get)
    When("typed decoding consumes the result without re-admitting removed historical copies")
    val accepted = compatible.map(value => take(humanjson.decodeC(capture.reference,
      humanjson.schema + ":" + value.noSpaces)))
    Then("the actual consumed Human facts survive; unused metadata is inert and grants no publication")
    frame.hcursor.downField("payload").focus.get.asObject.get.keys.toSet.intersect(Set("before", "next")) shouldBe Set.empty[String]
    accepted.foreach { value =>
      value.frontier shouldBe capture.frontier
      value.downstream shouldBe capture.downstream
      value.resolution shouldBe capture.resolution
    }
    assertNative(call, observed.answered)

    Given("Spec: docs/spec/split-phase-checkpoint-frame-applicability.md; an inert capture with a historical preparation postimage")
    val oldpostimage = capture.copy(next = capture.before)
    When("typed decoding consumes it without reconstructing an append certificate")
    val passivepostimage = take(humanjson.decodeC(capture.reference, take(humanjson.encodeC(oldpostimage))))
    Then("serialization omits the unused capture state and decoding returns result data without inventing native state")
    passivepostimage shouldBe capture.savedResult
    passivepostimage.downstream shouldBe capture.downstream
    assertNative(call, observed.answered)

    Given("crossed selected result addresses, incomplete frontier coverage and malformed consumed fields")
    val passive = Vector(capture.copy(reference = capture.reference.copy(revision = "foreign")),
      capture.copy(frontier = capture.frontier.tail))
    val corrupt = Vector(frame.mapObject(_.remove("payload")),
      frame.mapObject(_.add("schemaVersion", Json.fromString("incompatible"))),
      frame.hcursor.downField("payload").downField("frontier").withFocus(_ => Json.arr()).top.get,
      frame.hcursor.downField("payload").downField("resolution").withFocus(_ => Json.False).top.get)
    val malformed = Vector(wire.substring(humanjson.schema.length + 1), "foreign:" + frame.noSpaces,
      humanjson.schema + ":" + "x" * humanjson.maximumPayloadBytes,
      humanjson.schema + ":" + 0xd800.toChar)
    When("the existing typed result codec consumes those selected or malformed facts")
    val refuseddecoded = passive.map(value => humanjson.encodeC(value).flatMap(
      humanjson.decodeC(capture.reference, _)))
    val refusedframes = (corrupt.map(value => humanjson.schema + ":" + value.noSpaces) ++ malformed)
      .map(value => humanjson.decodeC(capture.reference, value))
    val foreignselected = humanjson.decodeC(capture.reference.copy(revision = "foreign"), wire)
    Then("missing consumed typed data or mismatched frontier coverage refuses without effects or substitute data")
    refuseddecoded.foreach(_.isSuccess shouldBe false)
    refusedframes.foreach(_.isSuccess shouldBe false)
    foreignselected.isSuccess shouldBe false
    assertNative(call, observed.answered)
    documents.foreach { (collection, identity, prior) => take(call.rowC(collection, identity)) shouldBe prior }
    take(call.rowC(dataid, fixtureid)).get.getString(humanjson.storageField) shouldBe Some(wire)
    call.executionContext.dataStoreSpace.captureBinding() shouldBe binding

    Given("an explicit complete interpretation that still spans more than one owner for an unresolved candidate")
    val broad = record.copy(scopes = request.gaps.map(gap => SS.Scope(gap.original.identity,
      gap.producingItems, gap.receivingItems, "explicit broad Human interpretation")))
    When("Workflow retains every held candidate instead of ranking an easier subset")
    val heldfrontier = take(Q.applyHumanResolutionC(take(HR.admitC(observed, broad))))
    val held = take(W.continueHumanResolutionC(heldfrontier))
    Then("the structured full frontier remains held and no subsequent planning stage or effect occurs")
    held.isInstanceOf[W.HumanResolutionHeld] shouldBe true
    held.mechanicalStages shouldBe Vector.empty
    heldfrontier.candidates.map(_.original.candidate.cutAfterIndices) shouldBe allocations.map(_.cutAfterIndices)
    heldfrontier.candidates.exists(_.isInstanceOf[Q.ScopeUnrepresentable]) shouldBe true
    assertNative(call, observed.answered)
    documents.foreach { (collection, identity, prior) => take(call.rowC(collection, identity)) shouldBe prior }

    Given("a held full frontier, a missing calculation and an oversized immutable source reference")
    When("fresh capture and purpose-key preparation receive inputs that cannot support successful publication")
    val heldcapture = SplitPhaseHumanResolutionCheckpointPreparation.captureC(held)
    val absentcapture = SplitPhaseHumanResolutionCheckpointPreparation.captureC(null)
    val oversizedkey = SplitPhaseHumanResolutionCheckpointPreparation.keyC(
      SplitPhaseHumanResolutionCheckpointPreparation.Reference("x" * 1024, "1"))
    Then("no successor, shortened key, document effect or native append is supplied")
    heldcapture.isSuccess shouldBe false
    absentcapture.isSuccess shouldBe false
    oversizedkey.isSuccess shouldBe false
    assertNative(call, observed.answered)
    documents.foreach { (collection, identity, prior) => take(call.rowC(collection, identity)) shouldBe prior }
    call.executionContext.dataStoreSpace.captureBinding() shouldBe binding

    Given("contradictory interpretation references, source ranges, answer evidence and Proposal/Start/host-selection purpose aliases")
    val invalid = Vector(record.copy(reference = record.reference.copy(revision = "foreign")),
      record.copy(context = record.context.copy(revision = "foreign")),
      record.copy(checkpoint = record.checkpoint.copy(revision = "foreign")),
      record.copy(scopeCheckpoint = record.scopeCheckpoint.copy(revision = "foreign")),
      record.copy(decision = HR.DecisionReference("foreign-question")),
      record.copy(evidence = Vector(T.EvidenceReference("unrecorded-evidence", "1"))),
      record.copy(scopes = Vector.empty), record.copy(scopes = record.scopes ++ record.scopes),
      record.copy(scopes = record.scopes.updated(0, record.scopes.head.copy(producingItems =
        if (request.gaps.head.producingItems.size > 1) request.gaps.head.producingItems.reverse
        else request.gaps.head.receivingItems))))
    When("the separate Human admission boundary and exact reader consume these contradictions")
    val rejected = invalid.map(value => HR.admitC(observed, value))
    val stale = port.loadForCallC(record.reference.copy(revision = "foreign"), call.core)
    val foreigncall = port.loadForCallC(record.reference, null)
    val aliases = Vector(target(F.Purpose.Proposal), collection("starts"), collection("selections")).map { target =>
      SplitPhaseHumanResolutionReadPolicy.bindC(host(values ++ SplitPhasePartitionReplyFixture.values ++ Map(
        SplitPhaseHumanResolutionReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
        SplitPhaseHumanResolutionReadPolicy.prefix + "record-collection" -> ConfigurationValue.StringValue(target.print))), declaration)
    }
    Then("no contradiction, stale revision, absent owning call or aliased data purpose is admitted")
    rejected.foreach(_.isSuccess shouldBe false)
    stale.isSuccess shouldBe false
    foreigncall.isSuccess shouldBe false
    aliases.foreach(_.isSuccess shouldBe false)
    assertNative(call, observed.answered)
    documents.foreach { (collection, identity, prior) => take(call.rowC(collection, identity)) shouldBe prior }
    call.executionContext.dataStoreSpace.captureBinding() shouldBe binding
  }

  "Actual managed scope successor publication" should {
    for (partitionroot <- Vector(false, true); decision <- SplitPhaseScopeSemantic.Decision.values;
        scenario <- Vector("publication", "interrupted-readback", "collision", "known-unrepresentable",
          "numbering-decision", "repository-decision")
        if Set("publication", "interrupted-readback", "collision").contains(scenario) ||
          decision == SplitPhaseScopeSemantic.Decision.Enriched) {
      s"preserve $decision with partition root $partitionroot through $scenario and physical reopen" in {
        import SplitPhaseExplicitPartitionFixture.{base, enumerate, proof}
        Given("a unique real SQLite fixture, public root setup and both original allocation policies")
        val originalsource = base(Vector.fill(4)(180))
        val source = if (scenario == "numbering-decision") originalsource.copy(inventory = originalsource.inventory.copy(
          occupiedPhases = originalsource.inventory.occupiedPhases + SplitPhaseExplicitProposalPrecheck.PhaseIdentity("77.3")))
          else originalsource
        val originaltemplate = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(1)).get,
          "managed-scope-template", handoffPairs = Vector(0 -> 1))
        val template = if (scenario == "repository-decision") {
          val records = originaltemplate.records.copy(ownership = originaltemplate.records.ownership.copy(
            children = originaltemplate.records.ownership.children.zipWithIndex.map { (child, index) =>
              child.copy(repository = SplitPhaseExplicitBoundaryValidation.RepositoryIdentity(s"repository-$index"))
            }))
          take(SplitPhaseExplicitBoundaryValidation.loadC(originaltemplate.shape, records))
        } else originaltemplate
        val saved = forBoundary(source, template)
        val workroot = Files.createDirectories(Path.of("target", "sm-workflow", "work", "scope-publication-spec"))
        // Retain diagnostic fixtures; project clean owns removal after pool closure.
        val path = Files.createTempDirectory(workroot, "managed-scope-").resolve("host.sqlite")
        val declared = values ++ SplitPhasePartitionReplyFixture.values
        var first = Option.empty[K.Record]
        var before = Option.empty[InstanceRecord]
        withHost(path, declared, publicadvance = true) { (call, _) =>
          if (first.isEmpty) {
            first = Some(_complete_scope_fixture(call, saved, decision, partitionroot,
              broadscope = scenario == "known-unrepresentable"))
            before = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core))
          }
          Consequence.unit
        }
        val retainedvalues = declared.filterNot((key, _) => key.startsWith(SplitPhaseCandidateAllocationPolicy.prefix) ||
          key.startsWith(SplitPhasePartitionReplyAllocationPolicy.prefix))
        var expected = Option.empty[NC.Record]
        var cores = Vector.empty[ActionCall.Core]
        var portoutside = Option.empty[NM.Port]
        var firstattempt = true
        var collisionrow = Option.empty[U.Row]
        Given("the original completion under a host that has not granted the handoff document collection")
        withHost(path, retainedvalues, denied = Some(target(F.Purpose.Handoffs))) { (call, _) =>
          val port = take(NM.bindC(call.core.component.get.subsystem.get.configuration, declaration, f.catalog))
          When("the live owning command tries to read or publish without that declared collection grant")
          val deniedload = port.loadForCallC(query(saved), call.core)
          val deniedexecute = port.executeForCallC(query(saved), call.core)
          Then("a genuine completion is not a storage grant and no native append is admitted")
          deniedload.isSuccess shouldBe false
          deniedexecute.isSuccess shouldBe false
          assertNative(call, before.get)
          Consequence.unit
        }
        for (mode <- Vector("first-owning-call", "physical-reopen")) {
          Given("the actual completed scope after physical pool shutdown, without allocation policies for " + mode)
          withHost(path, retainedvalues) { (call, _) =>
            val owner = call.core.component.get.subsystem.get
            var observations = 0
            val port = take(NM.bindWithReadbackObservationC(owner.configuration, declaration, f.catalog,
              () => { observations += 1; Consequence.unit }))
            val scope = take(SplitPhaseManagedScopeSemantic.bindC(ComponentCreate(owner, ComponentOrigin.Builtin), declaration, f.catalog))
            val targets = take(U.bindTargetsC(owner.configuration, declaration))
            val rootkey = take(U.mappingKeyC(first.get.reference))
            val indexkey = take(U.indexKeyC(saved.before.identity, saved.record.originalContext.reference))
            val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
            val rootrow = take(call.rowC(targets.publication, rootkey)).get
            val indexrow = take(call.rowC(targets.publication, indexkey)).get
            Given("the genuine completed scope and original whole frontier under this owning advance call")
            When("the scope port returns its constructed route twice instead of interpreting the next public handler")
            val routed = take(scope.routeForCallC(query(saved), call.core))
            val rerouted = take(scope.routeForCallC(query(saved), call.core))
            val routingnative = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            val routingroot = take(call.rowC(targets.publication, rootkey)).get
            val routingindex = take(call.rowC(targets.publication, indexkey)).get
            Then("completed provenance reaches the caller without dropping candidates, rendering success or changing native state")
            routed shouldBe a[SplitPhaseManagedScopeSemantic.Completed]
            rerouted shouldBe a[SplitPhaseManagedScopeSemantic.Completed]
            val completedroute = routed.asInstanceOf[SplitPhaseManagedScopeSemantic.Completed].source
            val repeatedroute = rerouted.asInstanceOf[SplitPhaseManagedScopeSemantic.Completed].source
            completedroute.source.record shouldBe first.get
            completedroute.source.current shouldBe routingnative
            completedroute.next shouldBe before.get
            completedroute.completion.result.result.value.decision shouldBe decision
            repeatedroute.source.record shouldBe completedroute.source.record
            repeatedroute.source.current shouldBe completedroute.source.current
            repeatedroute.completion shouldBe completedroute.completion
            routingnative shouldBe expected.fold(before.get)(record =>
              if (scenario == "collision" && decision == SplitPhaseScopeSemantic.Decision.Enriched) before.get else record.next)
            routingroot shouldBe rootrow
            routingindex shouldBe indexrow
            call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            if (expected.isEmpty) {
              When("the genuine owning completion is captured without publication")
              val captured = take(NC.captureC(take(scope.prepareProjectionForCallC(query(saved), call.core))))
              val descriptors = take(NU.prepareC(captured, targets))
              val absent = take(port.loadForCallC(query(saved), call.core))
              Then("exact first absence is not a grant and the full capture has the actual first root")
              absent shouldBe None
              captured.record.snapshot.first shouldBe first.get
              captured.record.before shouldBe before.get
              expected = Some(captured.record)
              if (scenario == "collision") {
                Given("an independently existing new-document row, or an unrelated obsolete Human-direction copy")
                val collided = if (decision == SplitPhaseScopeSemantic.Decision.Enriched) descriptors.rows.head
                  else U.Row(targets.publication, take(NC.keyC(captured.record.reference)),
                    Record.data(NJ.storageField -> "malformed"))
                take(call.writeC(collided.collection, collided.identity, collided.payload))
                collisionrow = Some(collided)
              }
            }
            val expectedrecord = expected.get
            val reference = M.Reference(expectedrecord.reference.identity, expectedrecord.reference.revision)
            val nativekey = take(M.keyC(reference))
            if (scenario == "collision" && decision == SplitPhaseScopeSemantic.Decision.Enriched) {
              When("managed publication encounters the independent collision on each owning call")
              val refused = port.executeForCallC(query(saved), call.core)
              val actualnative = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
              val nativereceipt = take(call.rowC(declaration.continuations, nativekey))
              val mapping = take(call.rowC(targets.publication, take(NC.keyC(expectedrecord.reference))))
              Then("there is no native append or partial publication and the independent row is retained")
              refused.isSuccess shouldBe false
              actualnative shouldBe before.get
              nativereceipt shouldBe None
              take(call.rowC(collisionrow.get.collection, collisionrow.get.identity)).get.getString(
                collisionrow.get.payload.fields.head.key) shouldBe collisionrow.get.payload.getString(collisionrow.get.payload.fields.head.key)
              if (decision == SplitPhaseScopeSemantic.Decision.Enriched) {
                mapping shouldBe None
                val documents = expectedrecord.snapshot.frontier.asInstanceOf[NV.Frontier.Applied].candidates.collect {
                  case NV.Candidate(_, _, NV.CandidateOutput.Built(value, false)) => value
                }
                documents.nonEmpty shouldBe true
                documents.foreach { document =>
                  take(call.rowC(targets.ownerships, document.ownership.reference.identity)) shouldBe None
                  take(call.rowC(targets.handoffs, document.handoffs.reference.identity)) shouldBe None
                }
              }
            } else {
              if (scenario == "interrupted-readback" && firstattempt) {
                Given("a test-only observation failure after actual native commit, not a replacement receipt")
                val interrupted = take(NM.bindWithReadbackObservationC(owner.configuration, declaration, f.catalog,
                  () => Consequence.stateConflict("test readback unavailable")))
                When("managed publication commits and its postcommit readback is interrupted")
                val uncertain = interrupted.executeForCallC(query(saved), call.core)
                val committed = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
                Then("the call reports failure despite the actual committed append, without retry")
                uncertain.isSuccess shouldBe false
                committed shouldBe expectedrecord.next
              }
              Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R13; Example: E30; actual owning successor observation, not a prepared-copy certificate")
              val priorobservations = observations
              When("the owning port publishes or independently recovers the saved successor twice and loads its actual observation")
              val observed = take(port.executeForCallC(query(saved), call.core))
              val repeated = take(port.executeForCallC(query(saved), call.core))
              val loaded = take(port.loadForCallC(query(saved), call.core)).get
              val native = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
              Then("three owning calls consume three observations, preserve the actual successor and append nothing on repeated or reopened calls")
              observations shouldBe priorobservations + 3
              observed.record shouldBe expectedrecord
              repeated.record shouldBe expectedrecord
              loaded.record shouldBe expectedrecord
              observed.current shouldBe expectedrecord.next
              native shouldBe expectedrecord.next
              native.history.dropRight(1) shouldBe before.get.history
              native.lifecycle shouldBe Lifecycle.Active
              native.suspension shouldBe None
              _specify_scope_native_result(call, port, query(saved), targets, expectedrecord)
              Given("the genuine committed whole frontier and cached allocations after " + mode)
              When("Workflow restores it and absorbs only still-needed deterministic planning")
              val continued = take(W.continuePublishedScopeC(loaded))
              val repeatedplanning = take(W.continuePublishedScopeC(repeated))
              val passive = take(NV.projectC(continued.scope))
              val repeatedpassive = take(NV.projectC(repeatedplanning.scope))
              Then("every saved result survives, with no enumeration, AI work, subset selection or native append")
              passive shouldBe expectedrecord.snapshot
              repeatedpassive shouldBe expectedrecord.snapshot
              continued.scope.original.selection.data shouldBe first.get.snapshot.selection.planning
              if (decision == SplitPhaseScopeSemantic.Decision.DecisionNeeded) {
                continued shouldBe a[W.PublishedHumanRequired]
                continued.scope shouldBe a[SplitPhaseExplicitCandidateProjection.ScopeHumanDirectionRequired]
                continued.scope.original.trace.projection.get.asInstanceOf[SplitPhaseExplicitCandidateProjection.Requirements]
                  .candidates.map(_.candidate.cutAfterIndices) shouldBe first.get.snapshot.projection.get.candidates.map(_.key.cutAfterIndices)
                continued.mechanicalStages shouldBe Vector.empty
              } else if (scenario == "known-unrepresentable") {
                continued shouldBe a[W.PublishedHumanRequired]
                val applied = continued.scope.asInstanceOf[SplitPhaseExplicitCandidateProjection.ScopeApplied]
                applied.projected shouldBe None
                applied.candidates.exists(_.isInstanceOf[SplitPhaseExplicitCandidateProjection.ScopeUnrepresentable]) shouldBe true
                applied.candidates.exists(_.isInstanceOf[SplitPhaseExplicitCandidateProjection.ScopeBuilt]) shouldBe true
                applied.candidates.map(_.original.candidate.cutAfterIndices) shouldBe
                  first.get.snapshot.projection.get.candidates.map(_.key.cutAfterIndices)
                continued.mechanicalStages shouldBe Vector.empty
              } else if (scenario == "numbering-decision" || scenario == "repository-decision") {
                continued shouldBe a[W.PublishedHumanRequired]
                val applied = continued.scope.asInstanceOf[SplitPhaseExplicitCandidateProjection.ScopeApplied]
                applied.projected shouldBe None
                val held = applied.candidates.collect { case value: SplitPhaseExplicitCandidateProjection.ScopeDecision => value }
                held.nonEmpty shouldBe true
                held.map(_.decision.reason).distinct shouldBe Vector(
                  if (scenario == "numbering-decision") SplitPhaseExplicitCandidateProjection.DecisionReason.PhaseNumberingRequired
                  else SplitPhaseExplicitCandidateProjection.DecisionReason.OneRepositoryPerChildRequired)
                applied.candidates.exists(_.isInstanceOf[SplitPhaseExplicitCandidateProjection.ScopeBuilt]) shouldBe true
                applied.candidates.map(_.original.candidate.cutAfterIndices) shouldBe
                  first.get.snapshot.projection.get.candidates.map(_.key.cutAfterIndices)
                if (scenario == "repository-decision") {
                  val originaldecisions = first.get.snapshot.projection.get.candidates.collect {
                    case value: SplitPhasePlanningSnapshot.CandidateProjectionView.DecisionView => value
                  }
                  originaldecisions.nonEmpty shouldBe true
                  passive.frontier.asInstanceOf[NV.Frontier.Applied].candidates.collect {
                    case NV.Candidate(_, _, NV.CandidateOutput.Decision(value)) => value
                  } should contain allElementsOf originaldecisions
                }
                continued.mechanicalStages shouldBe Vector.empty
              } else {
                val calculation = continued.asInstanceOf[W.PublishedContinued]
                val planned = calculation.outcome.asInstanceOf[W.Planned]
                planned.candidate.candidate.cutAfterIndices shouldBe Vector(1)
                planned.objective.stage shouldBe SplitPhaseExplicitObjectiveSelection.Stage.Duration
                calculation.scope.projected.get.candidates.map(_.candidate.cutAfterIndices) shouldBe
                  first.get.snapshot.projection.get.candidates.map(_.key.cutAfterIndices)
                calculation.mechanicalStages shouldBe Vector(W.Stage.CandidateAdmission, W.Stage.ObjectiveSelection)
                repeatedplanning.asInstanceOf[W.PublishedContinued].outcome.asInstanceOf[W.Planned]
                  .candidate.boundary.records shouldBe planned.candidate.boundary.records
              }
              assertNative(call, expectedrecord.next)
              Given("a different query than the command admitted for this actual advance call")
              When("that crossed query attempts constructed routing, retained observation and execution")
              val crossed = query(saved).copy(context = query(saved).context.copy(revision = "foreign"))
              val deniedroute = scope.routeForCallC(crossed, call.core)
              val deniedload = port.loadForCallC(crossed, call.core)
              val deniedexecute = port.executeForCallC(crossed, call.core)
              Then("neither passive stored data nor a live call grants the crossed command")
              deniedroute.isSuccess shouldBe false
              deniedload.isSuccess shouldBe false
              deniedexecute.isSuccess shouldBe false
              assertNative(call, expectedrecord.next)
              Given("the declared Built deliverables of the actually selected scope frontier")
              val document = expectedrecord.snapshot.frontier match {
                case NV.Frontier.Applied(candidates) => candidates.collectFirst {
                  case NV.Candidate(_, _, NV.CandidateOutput.Built(value, false)) => value
                }.orElse(candidates.collectFirst {
                  case NV.Candidate(_, _, NV.CandidateOutput.Built(value, true)) => value
                }).get
                case NV.Frontier.HumanDirectionRequired => first.get.snapshot.projection.get.candidates.collectFirst {
                  case value: SplitPhasePlanningSnapshot.CandidateProjectionView.BuiltView => value
                }.get
              }
              _specify_generated_documents(call, targets, document,
                () => port.loadForCallC(query(saved), call.core).map(_.get.record),
                () => port.executeForCallC(query(saved), call.core).map(_.record),
                expectedrecord, expectedrecord.next)
              When("the restored declared documents are independently observed again")
              val recovered = take(port.loadForCallC(query(saved), call.core)).get
              Then("the existing successor is recovered rather than recreated")
              recovered.record shouldBe expectedrecord
              recovered.current shouldBe expectedrecord.next
            }
            When("the original first mapping and context index are observed after this managed attempt")
            val retainedroot = take(call.rowC(targets.publication, rootkey)).get
            val retainedindex = take(call.rowC(targets.publication, indexkey)).get
            Then("no first mapping or mutable latest-result index was substituted, and the owning binding is restored")
            retainedroot shouldBe rootrow
            retainedindex shouldBe indexrow
            call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            firstattempt = false
            cores :+= call.core
            portoutside = Some(port)
            Consequence.unit
          }
        }
        Given("the same correct owning command and participant after all managed calls and pools have closed")
        When("an expired core attempts retained load and fresh execution")
        val expiredload = portoutside.get.loadForCallC(query(saved), cores.last)
        val expiredexecute = portoutside.get.executeForCallC(query(saved), cores.last)
        Then("no storage access is authorized by retaining a port or a former owning core")
        expiredload.isSuccess shouldBe false
        expiredexecute.isSuccess shouldBe false
        if (scenario != "collision") {
          Given("the actual retained successor reopened under a different authenticated participant")
          withHost(path, retainedvalues, participant = "other-participant") { (call, _) =>
            val port = take(NM.bindC(call.core.component.get.subsystem.get.configuration, declaration, f.catalog))
            When("the foreign participant attempts retained load and execution")
            val loaded = port.loadForCallC(query(saved), call.core)
            val executed = port.executeForCallC(query(saved), call.core)
            Then("the original participant's completed data grants no foreign execution or publication")
            loaded.isSuccess shouldBe false
            executed.isSuccess shouldBe false
            assertNative(call, expected.get.next)
            Consequence.unit
          }
        }
      }
    }
  }

  /** Pure postimages from a genuine current suspension; no native completion
    * is persisted and none of these values supplies Human consent.
    */
  private def _specify_human_answer_postimages(plan: SplitPhaseNativeHumanDirection.Plan,
    question: WorkflowInteraction[SplitPhaseNativeHumanDirection.Input, Nothing],
    native: InstanceRecord, call: Probe): Unit = {
    import org.goldenport.cncf.workflow.WorkflowResultJsonV1
    import SplitPhaseHumanDirectionAnswerContract as A
    import SplitPhaseNativeHumanAnswer as HA
    import SplitPhaseNativeHumanDirection as H
    val request = question.current.asInstanceOf[WorkflowContinuation.Decision[SplitPhaseNativeHumanDirection.Input]].request
    Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R4; Example: E7; a genuinely issued Scope Decision and owning native plan")
    val original = question.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]]
    val savedQuestion = question.copy(current = original.copy(
      presentation = MinimalPresentation("Saved Human title", "Saved Human notice"),
      request = request.copy(context = request.context.copy(summary = "Saved Human context",
        snapshot = request.context.snapshot.copy(workspaceRevision = Some("saved-workspace-diagnostic"),
          evidenceRevision = Some("saved-evidence-diagnostic"))))))
    When("presentation and diagnostic context differ from today's regenerated question")
    val selectedPlan = take(H.selectIssuedC(plan, savedQuestion))
    val inspected = H.inspectStoredC(plan.source.record, savedQuestion, native)
    Then("the saved question is consumed without another body certificate or native mutation")
    selectedPlan.question shouldBe savedQuestion
    take(org.goldenport.cncf.workflow.WorkflowDecisionJsonV1.decodeC(selectedPlan.wire, H.inputCodec)) shouldBe savedQuestion
    inspected.isSuccess shouldBe true
    take(instances.loadForCallC(f.configuration, native.identity, call.core)).get shouldBe native
    Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R4; Example: E8; the same genuine question with actual crossed targets")
    val input = request.input.get.value
    val foreignContext = input.copy(context = input.context.copy(identity = "foreign-context"))
    val foreignScope = input.copy(scopeCheckpoint = input.scopeCheckpoint.copy(identity = "foreign-scope-checkpoint"))
    val crossedQuestions = Vector(
      question.copy(current = original.copy(request = request.copy(input = Some(TypedValue(H.inputCodec.typeIdentity, foreignContext))))),
      question.copy(current = original.copy(request = request.copy(input = Some(TypedValue(H.inputCodec.typeIdentity, foreignScope))))),
      question.copy(current = original.copy(request = request.copy(continuationId = org.goldenport.cncf.workflow.ContinuationIdentity("foreign-slot")))),
      question.copy(current = original.copy(request = request.copy(operation = org.goldenport.cncf.workflow.StateMachineOperationIdentity("ForeignHuman", "answer")))),
      question.copy(current = original.copy(request = request.copy(context = request.context.copy(snapshot =
        request.context.snapshot.copy(modelRevision = Some("foreign-model")))))))
    When("the selected context checkpoint slot executable route or model is foreign")
    val selections = crossedQuestions.map(H.selectIssuedC(plan, _))
    Then("real scope conflicts are rejected without requiring full question equality")
    selections.foreach(_.isSuccess shouldBe false)
    Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R4; Example: E9; the genuine encoded Scope input")
    val payload = H.inputCodec.encode(input)
    val body = payload.hcursor.downField("payload").focus.get
    val annotations = Vector(body.mapObject(_.add("command", Json.True)),
      body.mapObject(fields => fields.add("context", fields("context").get.mapObject(_.add("grant", Json.True)))),
      body.mapObject(fields => fields.add("frontier", fields("frontier").get.mapObject(_.add("authority", Json.True)))))
    When("unused command grant and authority annotations are decoded beside the declared fields")
    val annotated = (annotations.map(value => payload.mapObject(_.add("payload", value))) :+ _with_unused_fields(payload))
      .map(H.inputCodec.decode)
    Then("the original passive Input survives without acquiring execution authority or changing native state")
    annotated.foreach(_ shouldBe Right(input))
    take(instances.loadForCallC(f.configuration, native.identity, call.core)).get shouldBe native
    Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R17; Example: E37; missing references or wrong consumed nested types")
    val malformed = Vector(body.mapObject(_.remove("context")), body.mapObject(_.add("checkpoint", Json.True)),
      body.mapObject(fields => fields.add("context", fields("context").get.mapObject(_.add("identity", Json.True)))),
      body.mapObject(fields => fields.add("frontier", fields("frontier").get.mapObject(_.add("value", Json.True)))))
    When("a public input lacks a usable original reference or complete frontier")
    val decodedInputs = malformed.map(value => H.inputCodec.decode(payload.mapObject(_.add("payload", value))))
    Then("declared typed input rejects them without a separate shape walk or serialized image")
    decodedInputs.foreach(_.isLeft shouldBe true)
    Given("the genuine whole-frontier Decision, exact native suspension and every declared passive answer")
    val answers = Vector[A.Answer](A.Answer.ProvideResolution(SplitPhaseExplicitPartitionSemantic.ContextReference("human-resolution", "1")),
      A.Answer.Defer, A.Answer.Cancel)
    answers.foreach { answer =>
      val submitted = ContinuationResult(question.handle, request.runId, request.continuationId, request.expectedRevision,
        request.context.snapshot, TypedValue(A.resultType, answer), ContextReference("human-evidence", "1"), Vector.empty,
        ExecutionEvidence(Vector(ContextReference("human-evidence", "1")), Some("participant")))
      When("the exact result is round-tripped and its pure adjacent HumanAnswered postimage is prepared")
      val decoded = take(WorkflowResultJsonV1.decodeC(take(WorkflowResultJsonV1.encodeC(submitted, A.payloadCodec)), A.payloadCodec))
      val next = take(HA.prepareC(plan, decoded, native))
      val stored = take(instances.loadForCallC(f.configuration, native.identity, call.core)).get
      Then("the entire native prefix is retained, the suspension is removed only in the calculated value and storage remains unchanged")
      decoded shouldBe submitted
      next.history.dropRight(1) shouldBe native.history
      next.revision shouldBe native.revision.next
      next.currentProgression shouldBe Some(ProgressionReference("HumanAnswered"))
      next.lifecycle shouldBe Lifecycle.Active
      next.suspension shouldBe None
      stored shouldBe native
      When("the Human result is offered to the semantic WorkOrder boundary or repeated against the answered postimage")
      val semantic = org.goldenport.cncf.workflow.WorkflowProtocolV1.admitResultC(question, decoded)
      val repeated = HA.prepareC(plan, decoded, next)
      val previous = HA.prepareC(plan, decoded, plan.intent.current)
      Then("neither another kind of work nor an unsuspended old/new state grants another answer append")
      semantic.isSuccess shouldBe false
      repeated.isSuccess shouldBe false
      previous.isSuccess shouldBe false
      Given("the same answer with crossed instance, continuation, revision or context correlation")
      val crossed = Vector(decoded.copy(handle = decoded.handle.copy(instanceIdentity = InstanceIdentity("foreign-instance"))),
        decoded.copy(continuationId = org.goldenport.cncf.workflow.ContinuationIdentity("foreign-question")),
        decoded.copy(expectedRevision = org.goldenport.cncf.workflow.StateMachineRevision("999")),
        decoded.copy(contextSnapshot = decoded.contextSnapshot.copy(workflowRevision = "foreign")))
      crossed.foreach { value =>
        When("that crossed answer requests a native postimage")
        val rejected = HA.prepareC(plan, value, native)
        Then("complete Decision correlation is required even without a persistence effect")
        rejected.isSuccess shouldBe false
      }
    }
  }

  "Public managed Human completion" should {
    import org.goldenport.cncf.workflow.{WorkflowDecisionJsonV1, WorkflowResultJsonV1,
      WorkflowCompletionRecordJsonV1, WorkflowComponentStorage, StateMachineOperationResult}
    import SplitPhaseNativeHumanDirection as H
    import SplitPhaseHumanDirectionAnswerContract as A
    import SplitPhaseHumanAnswerVerification as V
    import SplitPhaseManagedHumanCompletion as HC
    import SplitPhaseHumanTerminal as HT
    import SplitPhaseExplicitPartitionFixture.{base, enumerate, proof}
    val answers = Vector[A.Answer](A.Answer.ProvideResolution(
      SplitPhaseExplicitPartitionSemantic.ContextReference("independent-resolution", "1")), A.Answer.Defer, A.Answer.Cancel)
    for (partitionroot <- Vector(false, true); answer <- answers; interrupted <- Vector(false, true)
        if !interrupted || !answer.isInstanceOf[A.Answer.ProvideResolution];
        correction <- Vector(false, true) if !correction || answer.isInstanceOf[A.Answer.ProvideResolution];
        publication <- Vector("passive", "publication", "interrupted-publication", "publication-collision")
        if publication == "passive" || answer.isInstanceOf[A.Answer.ProvideResolution];
        objectivecase <- Vector("duration", "cost-record", "demand-record", "reasoning", "overhead-record", "human-objective", "cost", "overhead")
        if objectivecase == "duration" || (!partitionroot && !correction && !interrupted && publication == "publication")) {
      (s"retain $answer for partition root $partitionroot with interrupted acknowledgement $interrupted" +
        (if (correction) " and explicit correction of known scopes" else "") +
        (if (publication == "passive") "" else " with " + publication) +
        (if (objectivecase == "duration") "" else " and retained objective " + objectivecase)) in {
        Given("a unique real shared-pool host, genuine first root and an explicitly independent test verifier")
        val source = base(if (objectivecase == "duration") Vector.fill(4)(180) else Vector(260, 180, 260))
        val boundary = proof(source, enumerate(source).candidates.find(_.cutAfterIndices ==
          (if (objectivecase == "duration") Vector(1) else Vector(0))).get,
          "public-human-template", handoffPairs = Vector(0 -> 1))
        val objectives = SplitPhaseExplicitObjectiveSelection
        val cost = objectives.CostPolicy(objectives.CostPolicyReference("retained-cost", "1"), source.source.reference,
          source.source.authority, objectives.ProfileIdentity("expensive"), objectives.ProfileIdentity("lower"), 2, 1, 1)
        val demands = objectives.DemandRecord(objectives.DemandReference("retained-demands", "1"), source.source.reference,
          source.source.authority, source.inventory.orderedUnfinishedItems.zipWithIndex.map { (item, index) =>
            objectives.ItemDemand(item, if (objectivecase == "reasoning" && index == 0)
              objectives.Demand.Unresolved("unclassified reasoning")
            else if (objectivecase == "cost" && index == 0) objectives.Demand.ExpensiveReasoning
            else objectives.Demand.SettledExecution)
          })
        val overhead = objectives.OverheadPolicy(objectives.OverheadPolicyReference("retained-overhead", "1"),
          source.source.reference, source.source.authority, 1, if (objectivecase == "overhead") 1 else 0, 0, 0, 0)
        val objectivedata = objectives.Records(
          if (Set("duration", "cost-record").contains(objectivecase)) None else Some(cost),
          if (Set("duration", "cost-record", "demand-record").contains(objectivecase)) None else Some(demands),
          if (Set("human-objective", "cost", "overhead").contains(objectivecase)) Some(overhead) else None)
        val saved = forBoundary(source, boundary, objectivedata)
        val workroot = Files.createDirectories(Path.of("target", "sm-workflow", "work", "managed-human-completion-spec"))
        // Retained diagnostic fixtures; project clean owns removal after pool closure.
        val path = Files.createTempDirectory(workroot, "completion-").resolve("host.sqlite")
        val resolutionvalues = if (answer.isInstanceOf[A.Answer.ProvideResolution]) Map[String, ConfigurationValue](
          SplitPhaseHumanResolutionReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
          SplitPhaseHumanResolutionReadPolicy.prefix + "record-collection" -> ConfigurationValue.StringValue(collection("humanresolutions").print))
          else Map.empty[String, ConfigurationValue]
        val publicationvalues = if (publication != "passive") Map[String, ConfigurationValue](
          SplitPhaseHumanResolutionCheckpointPublication.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
          SplitPhaseHumanResolutionCheckpointPublication.prefix + "record-collection" ->
            ConfigurationValue.StringValue(collection("humancheckpoints").print)) else Map.empty[String, ConfigurationValue]
        val declared = values ++ SplitPhasePartitionReplyFixture.values ++ resolutionvalues ++ publicationvalues
        var initialized = false
        var allowed = false
        var checks = 0
        var interrupt = interrupted
        var observations = 0
        var terminalobservations = 0
        var terminalinterrupt = interrupted
        var terminal = Option.empty[InstanceRecord]
        var terminalwire = Option.empty[String]
        var question = Option.empty[WorkflowInteraction[H.Input, Nothing]]
        var pending = Option.empty[InstanceRecord]
        var answered = Option.empty[InstanceRecord]
        var submitted = Option.empty[ContinuationResult[A.Answer]]
        var ack = Option.empty[String]
        val evidence = new V.EvidencePort {
          def admitC(core: ActionCall.Core, reference: ContextReference, target: Json) = {
            checks += 1
            val enrolled = take(ThreeWorkflowHostEvidencePorts.splitAnswerBindingC(V.AnswerContext(
              core.component.get.core.instanceId, question.get, submitted.get, "participant")))
            target shouldBe enrolled
            reference shouldBe ContextReference("independent-human-consent", "1")
            val observed = if (!allowed) None else Some(NativeHostEvidence.Evidence(NativeHostEvidence.schema, V.purpose,
              ThreeWorkflowHostEvidencePorts.key(reference.identity, reference.revision), reference.identity, reference.revision,
              "independent-managed-human-spec", "independent-human", enrolled, core.executionContext.clock.instant().minusSeconds(86400).toString))
            NativeHostEvidenceFixtureAdmission.admitC(core, "independent-managed-human-spec", NativeHostEvidence.Purpose.SplitAnswer,
              ThreeWorkflowHostEvidencePorts.key(reference.identity, reference.revision), target, observed)
          }
        }
        val observation = () => {
          observations += 1
          if (interrupt) {
            interrupt = false
            org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate[Unit](
              "inert managed Human committed-readback observation interruption")
          } else Consequence.unit
        }
        val terminalobservation = () => {
          terminalobservations += 1
          if (terminalinterrupt) {
            terminalinterrupt = false
            org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate[Unit](
              "inert canonical terminal acknowledgement interruption")
          } else Consequence.unit
        }
        def _retained_c_(call: Probe): Consequence[Option[WorkflowCompletionRecordJsonV1.Record[A.Answer]]] = for {
          storage <- WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
            SmWorkflowComponent.componentId, declaration.configuration, Vector(f.catalog.definition),
            declaration.instances, declaration.continuations)
          native <- storage.bindDecisionCompletionC(H.completionOperation, H.inputCodec, A.payloadCodec, H.closingType,
            (_, _) => Consequence.success(Vector.empty))
          result <- native.loadRetainedC(submitted.get.continuationId)(using call.executionContext)
        } yield result.map(_.completion)
        withHost(path, declared, publicadvance = true, humanverifier = Some(evidence), humanreadback = observation,
          terminalreadback = terminalobservation) { (call, _) =>
          if (!initialized) {
            _complete_scope_fixture(call, saved, if (correction) SplitPhaseScopeSemantic.Decision.Enriched
              else SplitPhaseScopeSemantic.Decision.DecisionNeeded, partitionroot, broadreceiver = correction)
            When("the actual public advance publishes the complete frontier and issues its Human Decision")
            val wire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved)))
            val original = take(WorkflowDecisionJsonV1.decodeC(_scalar(take(_public_advance_c(call, wire, partitionroot))), H.inputCodec))
            val issued = if (!partitionroot && answer == A.Answer.Defer && !interrupted && objectivecase == "duration") {
              Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R4; Example: E10; a saved physical question with different presentation and diagnostic context")
              val decision = original.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]]
              val selected = original.copy(current = decision.copy(presentation = MinimalPresentation("Original retained display", "Retained notice"),
                request = decision.request.copy(context = decision.request.context.copy(summary = "Retained context",
                  snapshot = decision.request.context.snapshot.copy(workspaceRevision = Some("saved-diagnostic-workspace"))))))
              take(call.writeC(declaration.continuations, decision.request.continuationId.value,
                Record.data("issuedWorkPayload" -> ("cncf.workflow-json.v1:" + take(WorkflowDecisionJsonV1.encodeC(selected, H.inputCodec)))), update = true))
              selected
            } else original
            Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R17; Example: E36; compatible annotations in the actual retained Decision input")
            val issuedjson = parser.parse(take(WorkflowDecisionJsonV1.encodeC(issued, H.inputCodec))).toOption.get
            val annotatedissue = issuedjson.hcursor.downField("request").downField("input").downField("value")
              .withFocus(_with_unused_fields).top.get
            When("the private fixture adds inert annotations to the saved question without altering the native envelope or state")
            take(call.writeC(declaration.continuations, issued.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request.continuationId.value,
              Record.data("issuedWorkPayload" -> ("cncf.workflow-json.v1:" + annotatedissue.noSpaces)), update = true))
            val current = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            question = Some(issued)
            pending = Some(current)
            val request = issued.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request
            submitted = Some(ContinuationResult(issued.handle, request.runId, request.continuationId, request.expectedRevision,
              request.context.snapshot, TypedValue(A.resultType, answer), ContextReference("independent-human-consent", "1"),
              Vector.empty, ExecutionEvidence(Vector(ContextReference("independent-human-consent", "1")), Some("participant"))))
            Then("the complete issued question is retained and public Service and CLI recover it without another append")
            current.currentProgression shouldBe Some(ProgressionReference("HumanPending"))
            current.lifecycle shouldBe Lifecycle.Active
            current.suspension.nonEmpty shouldBe true
            Vector(false, true).foreach { cli =>
              take(WorkflowDecisionJsonV1.decodeC(_scalar(take(_public_advance_c(call, wire, cli))), H.inputCodec)) shouldBe issued
              assertNative(call, current)
            }
            val resultjson = parser.parse(take(WorkflowResultJsonV1.encodeC(submitted.get, A.payloadCodec))).toOption.get
            val resultwire = resultjson.hcursor.downField("result").downField("value").withFocus(_with_unused_fields).top.get.noSpaces
            When("the original authenticated relay submits without any independently admitted Human receipt")
            val refused = _public_operation_c(call, H.completionOperation.operation, resultwire, false)
            Then("authentication alone grants no answer append or successful acknowledgement")
            refused.isSuccess shouldBe false
            assertNative(call, current)
            take(_retained_c_(call)) shouldBe None
            checks shouldBe 1
            if (interrupted) observations shouldBe 0
            allowed = true
            When("the actual public completion consumes independently verified evidence and the canonical CNCF closing program")
            val completed = _public_operation_c(call, H.completionOperation.operation, resultwire, partitionroot)
            if (interrupted) {
              Then("the intended observation runs once, consumes its interruption and returns an indeterminate committed-readback acknowledgement")
              observations shouldBe 1
              interrupt shouldBe false
              completed match {
                case Consequence.Failure(conclusion) =>
                  conclusion.observation.taxonomy shouldBe org.goldenport.observation.Taxonomy.serviceUnavailable
                  conclusion.observation.cause.descriptor.facets should contain (
                    org.goldenport.observation.Descriptor.Facet.Reason("transaction-indeterminate"))
                  conclusion.display shouldBe "SplitPhase managed Human completion committed answer readback is not established; observe later without retry"
                case _ => fail("Interrupted committed Human observation incorrectly returned success")
              }
            } else ack = Some(_scalar(take(completed)))
            val next = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            answered = Some(next)
            Then("the entire adjacent native prefix is preserved and HumanAnswered is active, unsuspended and not Terminal")
            next.history.dropRight(1) shouldBe current.history
            next.revision shouldBe current.revision.next
            next.currentProgression shouldBe Some(ProgressionReference("HumanAnswered"))
            next.lifecycle shouldBe Lifecycle.Active
            next.suspension shouldBe None
            val committed = take(_retained_c_(call)).get
            committed.result shouldBe submitted.get
            committed.instanceRevision shouldBe next.revision
            committed.closingResult shouldBe Some(StateMachineOperationResult(H.closingType, submitted.get.resultReference))
            checks shouldBe 2
            allowed = false
            initialized = true
          }
          if (terminal.isEmpty) {
          When("the exact committed result is observed through both actual public profiles without renewed consent")
          val resultwire = take(WorkflowResultJsonV1.encodeC(submitted.get, A.payloadCodec))
          Vector(false, true).foreach { cli =>
            val observed = _scalar(take(_public_operation_c(call, H.completionOperation.operation, resultwire, cli)))
            ack.foreach(expected => observed shouldBe expected)
            ack = Some(observed)
            val json = take(parser.parse(observed).fold(_ => Consequence.stateConflict("Human response is not JSON"), Consequence.success))
            json.hcursor.get[String]("schemaVersion").toOption shouldBe Some(HC.responseSchema)
            val record = take(WorkflowCompletionRecordJsonV1.decodeC(json.hcursor.get[String]("completion").toOption.get, A.payloadCodec))
            record.result shouldBe submitted.get
            record.instanceRevision shouldBe answered.get.revision
            record.closingResult shouldBe Some(StateMachineOperationResult(H.closingType, submitted.get.resultReference))
            assertNative(call, answered.get)
            checks shouldBe 2
            if (interrupted) observations shouldBe 1
          }
          Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R2; Example: E11; the committed answer with changed diagnostic transport only")
          val diagnostic = submitted.get.copy(contextSnapshot = submitted.get.contextSnapshot.copy(
            workspaceRevision = Some("new-diagnostic-workspace"), evidenceRevision = Some("new-diagnostic-evidence")),
            evidence = submitted.get.evidence.copy(workerIdentity = Some("new-diagnostic-relay")))
          When("the same answer target and direction are resubmitted without renewed independent evidence")
          val diagnosticResponse = _scalar(take(_public_operation_c(call, H.completionOperation.operation,
            take(WorkflowResultJsonV1.encodeC(diagnostic, A.payloadCodec)), false)))
          Then("the response consumes the original saved answer and adds no native state or host admission")
          diagnosticResponse shouldBe ack.get
          assertNative(call, answered.get)
          checks shouldBe 2
          When("the same question is offered a changed answer or a different independent evidence reference")
          val alternative = if (answer == A.Answer.Cancel) A.Answer.Defer else A.Answer.Cancel
          val conflicting = Vector(submitted.get.copy(result = TypedValue(A.resultType, alternative)),
            submitted.get.copy(resultReference = ContextReference("different-consent", "1")),
            submitted.get.copy(contextSnapshot = submitted.get.contextSnapshot.copy(modelRevision = Some("foreign-model"))))
          conflicting.foreach { result =>
            val refused = _public_operation_c(call, H.completionOperation.operation,
              take(WorkflowResultJsonV1.encodeC(result, A.payloadCodec)), true)
            Then("a retained completion is not authority to substitute another complete answer or append history")
            refused.isSuccess shouldBe false
            assertNative(call, answered.get)
            checks shouldBe 2
            if (interrupted) observations shouldBe 1
          }
          Given("a recorded answer, but a privately corrupted canonical completion payload")
          val completionrow = take(call.rowC(declaration.continuations, submitted.get.continuationId.value)).get
          take(call.writeC(declaration.continuations, submitted.get.continuationId.value,
            Record.data("completionPayload" -> "malformed-private-fixture"), true))
          When("public advance cannot establish the recorded answer before any terminal append")
          val unavailable = _public_advance_c(call, take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved))), false)
          Then("no terminal, callback or native mutation occurs")
          unavailable.isSuccess shouldBe false
          assertNative(call, answered.get)
          terminalobservations shouldBe 0
          take(call.writeC(declaration.continuations, submitted.get.continuationId.value, completionrow, true))
          Given("the exact canonical answer restored without renewed consent")
          When("public advance reaches the saved HumanAnswered state")
          val later = _public_advance_c(call, take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved))), false)
          answer match {
            case _: A.Answer.ProvideResolution =>
              Then("missing resolution validation is not a terminal fallback or a new native transition")
              later.isSuccess shouldBe false
              assertNative(call, answered.get)
              terminalobservations shouldBe 0
            case _ =>
              Then("the recorded Defer or Cancel commits one real Completed successor, not another Human answer")
              if (interrupted) {
                later match {
                  case Consequence.Failure(conclusion) =>
                    conclusion.observation.taxonomy shouldBe org.goldenport.observation.Taxonomy.serviceUnavailable
                    conclusion.observation.cause.descriptor.facets should contain (
                      org.goldenport.observation.Descriptor.Facet.Reason("transaction-indeterminate"))
                    conclusion.display shouldBe "SplitPhase Human terminal committed terminal acknowledgement is not established; observe later without retry"
                  case _ => fail("Interrupted canonical terminal acknowledgement returned success")
                }
                terminalinterrupt shouldBe false
              } else terminalwire = Some(_scalar(take(later)))
              terminalobservations shouldBe 1
              val current = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
              current.lifecycle shouldBe Lifecycle.Completed
              current.currentProgression shouldBe None
              current.suspension shouldBe None
              current.revision shouldBe answered.get.revision.next
              current.history.dropRight(1) shouldBe answered.get.history
              current.correlation shouldBe answered.get.correlation
              current.causation shouldBe CausationReference("split-human-terminal:" + submitted.get.continuationId.value)
              terminal = Some(current)
              When("both public profiles observe the committed terminal without another append or callback")
              Vector(false, true).foreach { cli =>
                val wire = _scalar(take(_public_advance_c(call, take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved))), cli)))
                val decoded = take(org.goldenport.cncf.workflow.WorkflowTerminalJsonV1.decodeC(wire, HT.payloadCodec))
                val output = decoded.current.asInstanceOf[WorkflowContinuation.Terminal[HT.Result]].result.value
                Then("the canonical Terminal contains the exact deterministic outcome, Decision, answer and original context")
                output.outcome shouldBe (if (answer == A.Answer.Defer) HT.Outcome.Blocked else HT.Outcome.Cancelled)
                output.decision shouldBe submitted.get.continuationId
                output.answer shouldBe submitted.get.resultReference
                output.context shouldBe saved.record.originalContext.reference
                terminalwire.foreach(expected => wire shouldBe expected)
                terminalwire = Some(wire)
                decoded.handle shouldBe take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current))
                assertNative(call, current)
                terminalobservations shouldBe 1
                checks shouldBe 2
              }
              When("the original full Human submission is queried after termination")
              val wire = _scalar(take(_public_operation_c(call, H.completionOperation.operation, resultwire, false)))
              val json = take(parser.parse(wire).fold(_ => Consequence.stateConflict("Human observation is not JSON"), Consequence.success))
              val record = take(WorkflowCompletionRecordJsonV1.decodeC(json.hcursor.get[String]("completion").toOption.get, A.payloadCodec))
              Then("only currentRevision advances; the full retained answer and completingRevision remain historical facts")
              record.result shouldBe submitted.get
              record.instanceRevision shouldBe answered.get.revision
              json.hcursor.get[Long]("currentRevision").toOption shouldBe Some(current.revision.value)
              ack = Some(wire)
              assertNative(call, current)
              checks shouldBe 2
              terminalobservations shouldBe 1
          }
          } else {
            Given("the same host's subsequent dispatch after the terminal already committed")
            When("Service and CLI advance observe that terminal again")
            Vector(false, true).foreach { cli =>
              val observed = _scalar(take(_public_advance_c(call,
                take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved))), cli)))
              Then("the original terminal is returned with no append, verifier or callback")
              observed shouldBe terminalwire.get
              assertNative(call, terminal.get)
              checks shouldBe 2
              terminalobservations shouldBe 1
            }
          }
          terminal.foreach { current =>
            import org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1 as IJ
            val native = take(call.rowC(declaration.instances, current.identity.value)).get
            val completion = take(call.rowC(declaration.continuations, submitted.get.continuationId.value)).get
            val wrongcause = CausationReference("private-unrelated-terminal")
            val crossed = current.copy(causation = wrongcause,
              history = current.history.updated(current.history.size - 1, current.history.last.copy(causation = wrongcause)))
            val prefix = current.copy(history = current.history.updated(0,
              current.history.head.copy(causation = CausationReference("private-divergent-prefix"))))
            val incompatible = take(parser.parse(take(IJ.encodeC(current))).fold(
              _ => Consequence.stateConflict("Private native fixture is not JSON"), Consequence.success))
              .hcursor.downField("definition").downField("revision").withFocus(_ => Json.fromString("incompatible-definition")).top.get.noSpaces
            val corruptions = Vector(
              (declaration.instances, current.identity.value, Record.data("recordPayload" -> ("cncf.workflow-json.v1:" + take(IJ.encodeC(crossed)))), native),
              (declaration.instances, current.identity.value, Record.data("recordPayload" -> ("cncf.workflow-json.v1:" + take(IJ.encodeC(prefix)))), native),
              (declaration.instances, current.identity.value, Record.data("recordPayload" -> ("cncf.workflow-json.v1:" + incompatible)), native),
              (declaration.continuations, submitted.get.continuationId.value, Record.data("completionPayload" -> "malformed-private-fixture"), completion))
            corruptions.foreach { case (collection, identity, changed, original) =>
              Given("a privately corrupted terminal causation, prefix, definition or canonical answer")
              take(call.writeC(collection, identity, changed, true))
              val before = take(call.rowC(collection, identity)).get
              When("both public profiles attempt to observe the contradictory terminal")
              val refused = Vector(false, true).map(cli => _public_advance_c(call,
                take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved))), cli))
              Then("contradictions produce no Terminal, automatic repair, consent or callback")
              refused.foreach(_.isSuccess shouldBe false)
              val after = take(call.rowC(collection, identity)).get
              before.fields.foreach(field => after.getString(field.key) shouldBe before.getString(field.key))
              checks shouldBe 2
              terminalobservations shouldBe 1
              take(call.writeC(collection, identity, original, true))
              assertNative(call, current)
            }
            Given("the actual terminal queried with a different original context reference")
            val crossedcommand = query(saved).copy(context = SplitPhasePartitionContextReference("foreign-context", "1"))
            When("public advance receives that crossed context")
            val refused = _public_advance_c(call, take(SplitPhaseAdvanceCommandJsonV1.encodeC(crossedcommand)), true)
            Then("the stored terminal conveys no authority for that other original context")
            refused.isSuccess shouldBe false
            assertNative(call, current)
            checks shouldBe 2
            terminalobservations shouldBe 1
          }
          if (interrupted) observations shouldBe 1
          Consequence.unit
        }
        Given("the physical pool has closed and all fresh allocation policies and Human receipt availability are removed")
        val ap = SplitPhaseCandidateAllocationPolicy.prefix
        val rp = SplitPhasePartitionReplyAllocationPolicy.prefix
        val retainedvalues = declared.filterNot((key, _) => key.startsWith(ap) || key.startsWith(rp))
        withHost(path, retainedvalues, publicadvance = true, humanverifier = Some(evidence), humanreadback = observation,
          terminalreadback = terminalobservation) { (call, _) =>
          When("the original relay observes the exact complete result after physical reopen")
          val observed = _scalar(take(_public_operation_c(call, H.completionOperation.operation,
            take(WorkflowResultJsonV1.encodeC(submitted.get, A.payloadCodec)), true)))
          Then("the historical acknowledgement and native state survive without a verifier call or new allocation")
          observed shouldBe ack.get
          assertNative(call, terminal.getOrElse(answered.get))
          checks shouldBe 2
          if (interrupted) observations shouldBe 1
          terminalwire.foreach { expected =>
            When("the reopened public advance observes the same terminal under removed allocation and consent availability")
            val observed = _scalar(take(_public_advance_c(call, take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved))), true)))
            Then("the complete Terminal and canonical history survive with no new effects")
            observed shouldBe expected
            assertNative(call, terminal.get)
            terminalobservations shouldBe 1
            checks shouldBe 2
          }
          Consequence.unit
        }
        Given("the same stored answer and shared pool are reopened under a foreign authenticated relay")
        withHost(path, retainedvalues, participant = "foreign", publicadvance = true, humanverifier = Some(evidence), humanreadback = observation,
          terminalreadback = terminalobservation) { (call, _) =>
          When("that relay requests the saved completion rather than submitting new consent")
          val refused = _public_operation_c(call, H.completionOperation.operation,
            take(WorkflowResultJsonV1.encodeC(submitted.get, A.payloadCodec)), false)
          Then("even historical observation requires the original participant and cannot mutate native state")
          refused.isSuccess shouldBe false
          assertNative(call, terminal.getOrElse(answered.get))
          checks shouldBe 2
          if (interrupted) observations shouldBe 1
          terminal.foreach { expected =>
            When("the foreign relay requests the stored Terminal through public advance")
            val refused = _public_advance_c(call, take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved))), true)
            Then("historical termination is not authority for a foreign participant")
            refused.isSuccess shouldBe false
            assertNative(call, expected)
            terminalobservations shouldBe 1
          }
          Consequence.unit
        }
        if (answer.isInstanceOf[A.Answer.ProvideResolution]) {
          for (datapass <- Vector("first owning input read", "owning input read after physical pool reopen")) {
            var resolutioncalls = 0
            val datapassvalues = if (publication == "passive" && datapass == "owning input read after physical pool reopen")
              declared ++ Map(
                SplitPhaseHumanResolutionCheckpointPublication.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
                SplitPhaseHumanResolutionCheckpointPublication.prefix + "record-collection" ->
                  ConfigurationValue.StringValue(collection("humancheckpoints").print))
              else declared
            withHost(path, datapassvalues) { (call, _) =>
              Given(datapass + " with the same canonical Human completion and exact resolution reference")
              _specify_human_resolution(call, saved, submitted.get, correction)
              resolutioncalls += 1
              checks shouldBe 2
              if (publication == "passive" && datapass == "owning input read after physical pool reopen" && resolutioncalls == 2) {
                Given("an independent later native history entry after the same immutable Human completion")
                val current = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
                val later = take(current.appendC(current.revision, HistoryEntry(
                  HistorySequence(current.history.size.toLong + 1L), current.revision.next,
                  current.lifecycle, current.currentProgression, current.correlation,
                  CausationReference("fixture-later-history"), current.derivedCompositeOccurrence,
                  current.committedPredecessor, None)))
                take(instances.appendForCallC(f.configuration, current.identity, current.revision, later.history.last, call.core))
                When("the owning native observer restores the answer after later progress and passive capture receives its calculation")
                val observed = take(SplitPhaseManagedHumanCompletion.observeAnswerForAdvanceC(query(saved), declaration, f.catalog, call.core))
                val reader = take(SplitPhaseHumanResolutionReadPolicy.bindC(call.core.component.get.subsystem.get.configuration, declaration))
                val data = take(reader.loadForCallC(SplitPhaseHumanScopeResolution.Reference("independent-resolution", "1"), call.core)).get
                val admitted = take(SplitPhaseHumanScopeResolution.admitC(observed, data))
                val frontier = take(SplitPhaseExplicitCandidateProjection.applyHumanResolutionC(admitted))
                val calculation = take(W.continueHumanResolutionC(frontier))
                val captured = take(SplitPhaseHumanResolutionCheckpointPreparation.captureC(calculation))
                val targets = take(SplitPhaseHumanResolutionCheckpointPublication.bindTargetsC(
                  call.core.component.get.subsystem.get.configuration, declaration))
                val prepared = take(SplitPhaseHumanResolutionCheckpointPublication.prepareC(captured, targets))
                Then("historical capture and descriptors are passive, retaining the original expected revision without effects")
                observed.current shouldBe later
                observed.answered shouldBe current
                calculation shouldBe a[W.HumanResolutionContinued]
                captured.intent.current shouldBe current
                captured.intent.expectedRevision shouldBe current.revision
                assertNative(call, later)
                checks shouldBe 2
                _specify_stale_checkpoint(call, captured.intent, prepared.rows, targets.storeName,
                  SplitPhaseHumanResolutionCheckpointJsonV1.payloadCodec, later)
              }
              Consequence.unit
            }
          }
          if (publication != "passive") {
            import SplitPhaseHumanResolutionCheckpointPublication as HP
            import SplitPhaseHumanResolutionCheckpointPreparation as HK
            import SplitPhaseHumanResolutionCheckpointJsonV1 as HJ
            import SplitPhaseManagedHumanResolutionCheckpoint as HM
            var expected = Option.empty[HK.Record]
            var later = Option.empty[InstanceRecord]
            var publicationcalls = 0
            var publicationobservations = 0
            var publicationinterrupt = publication == "interrupted-publication"
            val observepublication = () => {
              publicationobservations += 1
              if (publicationinterrupt) {
                publicationinterrupt = false
                org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate[Unit](
                  "inert Human successor acknowledgement interruption")
              } else Consequence.unit
            }
            withHost(path, declared.filterNot((key, _) => key.startsWith(HP.prefix))) { (call, _) =>
              publicationcalls += 1
              val owner = call.core.component.get.subsystem.get.configuration
              val binding = call.executionContext.dataStoreSpace.captureBinding()
              val targets = take(HP.bindTargetsC(owner, declaration))
              val port = take(HM.bindWithReadbackObservationC(owner, declaration, f.catalog, observepublication))
              if (publicationcalls == 1) {
                Given("a genuine answered Human and complete interpretation, with no saved Human successor")
                val humananswer = take(HC.observeAnswerForAdvanceC(query(saved), declaration, f.catalog, call.core))
                val reader = take(SplitPhaseHumanResolutionReadPolicy.bindC(owner, declaration))
                val data = take(reader.loadForCallC(SplitPhaseHumanScopeResolution.Reference("independent-resolution", "1"), call.core)).get
                val admitted = take(SplitPhaseHumanScopeResolution.admitC(humananswer, data))
                val computed = take(SplitPhaseExplicitCandidateProjection.applyHumanResolutionC(admitted))
                val captured = take(HK.captureC(take(W.continueHumanResolutionC(computed))))
                When("the publisher prepares only new Built Creates without Human copy configuration")
                val prepared = take(HP.prepareC(captured, targets))
                expected = Some(captured.record)
                val newbuilt = captured.record.frontier.collect { case NV.Candidate(_, _, NV.CandidateOutput.Built(value, false)) => value }
                Then("previous Built documents are preserved without a speculative TOTAL-count certificate")
                prepared.rows.size shouldBe newbuilt.size * 3
                prepared.rows.map(_.identity).toSet shouldBe
                  newbuilt.flatMap(value => Vector(value.proposal.reference.identity, value.ownership.reference.identity,
                    value.handoffs.reference.identity)).toSet
                Given("sixty-five complete descriptors and the genuine unexecuted Human intent")
                val capacity = Vector.tabulate(65)(index => U.Row(targets.documents.proposals, s"capacity-$index",
                  Record.data(F.storageField -> "complete-descriptor-fixture")))
                _specify_native_create_refusal(call, captured.intent, HJ.payloadCodec, capacity, targets.storeName,
                  take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get)
                Given("the complete reserved native, input and document collection purposes")
                val reserved = targets.documents.localCollections ++ Vector(declaration.instances, declaration.continuations,
                  collection("contexts"), collection("starts"), collection("selections"), collection("humanresolutions")) ++
                  F.Purpose.values.toVector.map(target)
                reserved.distinct.foreach { alias =>
                  When(s"unused old Human copy metadata names ${alias.print}")
                  val aliased = HP.bindTargetsC(host(declared.updated(HP.prefix + "record-collection", ConfigurationValue.StringValue(alias.print))),
                    declaration)
                  Then("unused copy metadata selects no effect and does not reject actual document targets")
                  take(aliased).documents.localCollections shouldBe targets.documents.localCollections
                  take(aliased).storeName shouldBe targets.storeName
                }
                When("publication targets are interpreted without fresh Human input metadata")
                val retainedtargets = HP.bindTargetsC(host(declared.filterNot((key, _) => key.startsWith(SplitPhaseHumanResolutionReadPolicy.prefix))), declaration)
                Then("the retained observation configuration remains admissible")
                retainedtargets.isSuccess shouldBe true
                When("the publisher receives no captured successor")
                val missingcapture = HP.prepareC(null, targets)
                Then("missing capture is refused before publication")
                missingcapture.isSuccess shouldBe false
                When("the owning port observes the still-unpublished Human successor")
                val absent = take(port.loadForCallC(query(saved), call.core))
                Then("absence is preserved with unchanged canonical native state")
                absent shouldBe None
                assertNative(call, humananswer.answered)

                if (publication == "publication-collision") {
                  Given("an independently existing row at one newly required Built Create identity")
                  val collision = prepared.rows.head
                  take(call.writeC(collision.collection, collision.identity, Record.data(F.storageField -> "collision-fixture")))
                  val before = prepared.rows.map(row => take(call.rowC(row.collection, row.identity)))
                  When("the real managed native UoW attempts the complete Create set")
                  val refused = port.executeForCallC(query(saved), call.core)
                  Then("collision leaves the canonical instance, application and every other Create unchanged")
                  refused.isSuccess shouldBe false
                  prepared.rows.map(row => take(call.rowC(row.collection, row.identity))) shouldBe before
                  assertNative(call, humananswer.answered)
                  take(port.loadForCallC(query(saved), call.core)) shouldBe None
                  publicationobservations shouldBe 0
                } else {
                  Given("all new Create identities are absent and one existing managed provider owns native and document purposes")
                  When("the actual owning advance call publishes its complete Human successor once")
                  val result = port.executeForCallC(query(saved), call.core)
                  if (publication == "interrupted-publication") {
                    Then("the real postcommit observation runs once, consumes interruption and refuses successful acknowledgement")
                    publicationobservations shouldBe 1
                    publicationinterrupt shouldBe false
                    result match {
                      case Consequence.Failure(conclusion) =>
                        conclusion.observation.cause.descriptor.facets should contain (
                          org.goldenport.observation.Descriptor.Facet.Reason("transaction-indeterminate"))
                        conclusion.display shouldBe "SplitPhase managed Human successor postcommit observation interrupted; observe later without repeating publication"
                      case _ => fail("Interrupted Human successor publication incorrectly returned success")
                    }
                  } else {
                    Then("the owning publication returns its actual committed record and current instance")
                    val committed = take(result)
                    committed.record shouldBe captured.record
                    committed.current shouldBe captured.record.next
                    committed.source.answered shouldBe humananswer.answered
                  }
                  Given("a genuinely committed native Human successor without an application copy")
                  val publicationkey = take(HK.keyC(captured.record.reference))
                  When("the private probe checks the former application-copy address")
                  val unusedcopy = take(call.rowC(collection("humancheckpoints"), publicationkey))
                  Then("publication did not create a result-selection copy")
                  unusedcopy shouldBe None
                  Given("an old passive postimage in the native payload, not the actual native receipt envelope")
                  When("the fixture adds inert native metadata without changing a consumed Human fact")
                  _store_passive_postimage(call, M.Reference(captured.record.reference.identity, captured.record.reference.revision))
                  Then("the real native successor remains committed and no extra acknowledgement occurs")
                  assertNative(call, captured.record.next)
                  publicationobservations shouldBe 1
                  Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R14; Example: E31; a genuinely committed Human successor and one acknowledgement observation")
                  When("the owner independently reads back the committed native successor, application and Built documents")
                  val observed = take(port.loadForCallC(query(saved), call.core)).get
                  Then("the actual saved source and successor are consumed without another publication")
                  observed.record shouldBe captured.record
                  observed.current shouldBe captured.record.next
                  observed.source.answered shouldBe humananswer.answered
                  observed.record.before shouldBe humananswer.answered
                  observed.record.next shouldBe captured.record.next
                  publicationobservations shouldBe 1
                  _specify_native_checkpoint_selection(call,
                    M.Reference(captured.record.reference.identity, captured.record.reference.revision), observed.current)(
                    port.loadForCallC(query(saved), call.core))
                  _specify_human_native_result(call, port, query(saved), captured.record)
                  When("owning execute consumes that independently read publication again")
                  val acknowledged = take(port.executeForCallC(query(saved), call.core))
                  Then("the observed current source and record return without a second acknowledgement or append")
                  acknowledged.record shouldBe observed.record
                  acknowledged.current shouldBe observed.current
                  acknowledged.source.answered shouldBe observed.source.answered
                  publicationobservations shouldBe 1
                  assertNative(call, observed.current)
                  When("the planner restores the native-bound published Human downstream instead of recalculating it")
                  val resumed = take(W.restorePublishedHumanC(observed))
                  val resumedview = take(SplitPhasePlanningSnapshot.projectC(resumed.outcome))
                  val recapture = HK.captureC(resumed)
                  Then("the full saved calculation is restored, reports no new stages and cannot be a fresh capture")
                  resumedview.record shouldBe captured.record.downstream
                  resumed.mechanicalStages shouldBe Vector.empty
                  Given("the same genuinely published Human downstream and its complete saved disposition")
                  When("reasoning source selection consumes that native Human observation rather than passive data")
                  val reasoningsource = SplitPhaseRetainedReasoningSource.humanResolutionC(observed)
                  if (objectivecase == "reasoning") {
                    val native = take(reasoningsource)
                    val restored = take(W.restoreReasoningC(native))
                    val prepared = take(SplitPhaseExplicitReasoningSemantic.prepareC(
                      native.checkpoint.originalContext.reference, restored.pending,
                      SplitPhaseExplicitPartitionSemantic.ResultReference("human-reasoning-fixture-result", "1")))
                    Then("unknown-only semantic preparation retains every Human-calculated fact and zero new stages")
                    native.origin shouldBe a[SplitPhaseRetainedReasoningSource.HumanResolution]
                    native.publicationPostimage shouldBe captured.record.next
                    native.snapshot shouldBe captured.record.downstream
                    take(SplitPhasePlanningSnapshot.projectC(restored.pending)).record shouldBe captured.record.downstream
                    restored.mechanicalStages shouldBe Vector.empty
                    prepared.request.items.map(_.identity) shouldBe Vector(source.inventory.orderedUnfinishedItems.head)
                    prepared.request.authority shouldBe source.source.authority
                    prepared.request.items.head.closure shouldBe source.sourceBoundary.items.head
                  } else {
                    Then("a different native Human disposition is not relabeled as reasoning work")
                    reasoningsource.isSuccess shouldBe false
                  }
                  resumedview.disposition shouldBe (objectivecase match {
                    case "cost-record" | "demand-record" | "overhead-record" => SplitPhasePlanningSnapshot.Disposition.ObjectiveRecordRequired
                    case "reasoning" => SplitPhasePlanningSnapshot.Disposition.ReasoningRequired
                    case "human-objective" => SplitPhasePlanningSnapshot.Disposition.HumanDecisionRequired
                    case _ => SplitPhasePlanningSnapshot.Disposition.Planned
                  })
                  if (Set("duration", "cost", "overhead").contains(objectivecase)) {
                    val planned = resumed.outcome.asInstanceOf[W.Planned]
                    planned.objective.stage shouldBe (objectivecase match {
                      case "cost" => objectives.Stage.Cost
                      case "overhead" => objectives.Stage.Overhead
                      case _ => objectives.Stage.Duration
                    })
                  }
                  resumed.frontier.candidates.map(_.original.candidate.cutAfterIndices) shouldBe
                    captured.record.frontier.map(_.key.cutAfterIndices)
                  resumed.frontier.candidates.collect {
                    case value: SplitPhaseExplicitCandidateProjection.ScopeBuilt => value.documentsAlreadyPublished
                  } shouldBe captured.record.frontier.collect {
                    case NV.Candidate(_, _, NV.CandidateOutput.Built(_, published)) => published
                  }
                  recapture.isSuccess shouldBe false
                  When("the planner receives no owning Human publication observation")
                  val missingobservation = W.restorePublishedHumanC(null)
                  Then("passive data or absence cannot supply native continuation provenance")
                  missingobservation.isSuccess shouldBe false
                  When("the owner reads every newly published document through its actual declared target")
                  val saveddocuments = prepared.rows.map(row =>
                    row -> take(call.rowC(row.collection, row.identity)).get)
                  Then("actual generated document content is committed without an application result-copy dependency")
                  saveddocuments.foreach { (row, stored) =>
                    stored.getString(row.payload.fields.head.key) shouldBe row.payload.getString(row.payload.fields.head.key)
                  }
                  assertNative(call, captured.record.next)
                  publicationobservations shouldBe 1
                }
              } else {
                Given("a later owning call after the completed or rolled-back publication attempt")
                When("the owner observes rather than recaptures the same canonical Human step")
                val observed = take(port.loadForCallC(query(saved), call.core))
                if (publication == "publication-collision") {
                  Then("verified atomic absence remains absence, with the existing collision preserved")
                  observed shouldBe None
                  assertNative(call, expected.get.before)
                  publicationobservations shouldBe 0
                } else {
                  Then("the saved complete result is returned without a second append or acknowledgement callback")
                  observed.get.record shouldBe expected.get
                  When("the owning port executes the already published Human reference")
                  val repeated = take(port.executeForCallC(query(saved), call.core))
                  Then("execution returns that same saved result without another acknowledgement callback")
                  repeated.record shouldBe expected.get
                  publicationobservations shouldBe 1
                  Given("independent later native progress after the fully published Human successor")
                  val current = expected.get.next
                  val progressed = take(current.appendC(current.revision, HistoryEntry(
                    HistorySequence(current.history.size.toLong + 1L), current.revision.next, current.lifecycle,
                    current.currentProgression, current.correlation, CausationReference("fixture-after-human-publication"),
                    current.derivedCompositeOccurrence, current.committedPredecessor, None)))
                  take(instances.appendForCallC(f.configuration, current.identity, current.revision, progressed.history.last, call.core))
                  later = Some(progressed)
                  When("retained-first execution observes after that later native progress")
                  val retained = take(port.executeForCallC(query(saved), call.core))
                  Then("complete saved data joins the later current history without fresh calculation or capture")
                  retained.record shouldBe expected.get
                  retained.current shouldBe progressed
                  When("the planner restores that same saved Human frontier after independent later progress")
                  val resumed = take(W.restorePublishedHumanC(retained))
                  val resumedview = take(SplitPhasePlanningSnapshot.projectC(resumed.outcome))
                  Then("restoration keeps the original complete downstream without replaying its calculation")
                  resumedview.record shouldBe expected.get.downstream
                  resumed.mechanicalStages shouldBe Vector.empty
                  publicationobservations shouldBe 1
                  assertNative(call, progressed)
                }
              }
              checks shouldBe 2
              call.executionContext.dataStoreSpace.captureBinding() shouldBe binding
              Consequence.unit
            }
            if (publication != "publication-collision") {
              Given("physical pool closure, missing fresh Human input declaration and removed allocation policies")
              val retainedconfig = declared.filterNot((key, _) => key.startsWith(SplitPhaseHumanResolutionReadPolicy.prefix) ||
                key.startsWith(ap) || key.startsWith(rp))
              withHost(path, retainedconfig) { (call, _) =>
                val owner = call.core.component.get.subsystem.get.configuration
                val port = take(HM.bindC(owner, declaration, f.catalog))
                When("the reopened owner observes and executes the same published reference without fresh-input metadata")
                val observed = take(port.loadForCallC(query(saved), call.core)).get
                val repeated = take(port.executeForCallC(query(saved), call.core))
                Then("the full canonical result and later native prefix survive with no consent, allocation or append")
                observed.record shouldBe expected.get
                repeated.record shouldBe expected.get
                repeated.current shouldBe later.get
                When("the planner restores the reopened native-bound publication without any fresh-input declaration")
                val resumed = take(W.restorePublishedHumanC(repeated))
                val resumedview = take(SplitPhasePlanningSnapshot.projectC(resumed.outcome))
                val recapture = HK.captureC(resumed)
                Then("all saved candidates and downstream stages survive reopening, with no new calculation or capture")
                resumedview.record shouldBe expected.get.downstream
                resumed.mechanicalStages shouldBe Vector.empty
                recapture.isSuccess shouldBe false
                assertNative(call, later.get)
                publicationobservations shouldBe 1
                checks shouldBe 2
                Given("the declared Built deliverables of the physically reopened Human publication")
                val targets = take(HP.bindTargetsC(owner, declaration))
                val document = expected.get.frontier.collectFirst {
                  case NV.Candidate(_, _, NV.CandidateOutput.Built(value, false)) => value
                }.orElse(expected.get.frontier.collectFirst {
                  case NV.Candidate(_, _, NV.CandidateOutput.Built(value, true)) => value
                }).get
                _specify_generated_documents(call, targets.documents, document,
                  () => port.loadForCallC(query(saved), call.core).map(_.get.record),
                  () => port.executeForCallC(query(saved), call.core).map(_.record),
                  expected.get, later.get)
                Then("document consumption does not repeat publication observation or Human consent")
                publicationobservations shouldBe 1
                checks shouldBe 2
                Given("the physically reopened native result remains usable with absent or corrupt unused Human copies")
                _specify_unused_successor_copy(call, collection("humancheckpoints"),
                  take(HK.keyC(expected.get.reference)), HJ.storageField, take(HJ.encodeC(expected.get)), expected.get, later.get)(
                  port.loadForCallC(query(saved), call.core).map(_.get.record),
                  port.executeForCallC(query(saved), call.core).map(_.record))
                Then("unused application rows do not repeat publication observation or Human consent")
                publicationobservations shouldBe 1
                checks shouldBe 2
                Consequence.unit
              }
            }
          }
        }
      }
    }
  }

  "Independent native Human answer evidence" should {
    for (partitionroot <- Vector(false, true)) {
      s"admit only exact independent evidence on the current completion clock for partition root $partitionroot" in {
        import java.time.{Clock, Instant, ZoneOffset}
        import SplitPhaseNativeHumanDirection as H
        import SplitPhaseManagedHumanDirection as HM
        import SplitPhaseNativeHumanAnswer as HA
        import SplitPhaseHumanDirectionAnswerContract as A
        import SplitPhaseHumanAnswerVerification as HV
        import SplitPhaseExplicitPartitionFixture.{base, enumerate, proof}
        Given("a mixed original frontier and the actual public scope completion for this original root")
        val source = base(Vector.fill(4)(180))
        val boundary = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(1)).get,
          "human-answer-template", handoffPairs = Vector(0 -> 1))
        val saved = forBoundary(source, boundary)
        val workroot = Files.createDirectories(Path.of("target", "sm-workflow", "work", "native-human-answer-spec"))
        // Retained diagnostic fixtures; project clean owns removal after pool closure.
        val path = Files.createTempDirectory(workroot, "answer-").resolve("host.sqlite")
        val declared = values ++ SplitPhasePartitionReplyFixture.values
        var initialized = false
        withHost(path, declared, publicadvance = true) { (call, _) =>
          if (!initialized) {
            _complete_scope_fixture(call, saved, SplitPhaseScopeSemantic.Decision.DecisionNeeded, partitionroot)
            initialized = true
          }
          Consequence.unit
        }
        var plan = Option.empty[H.Plan]
        var question = Option.empty[WorkflowInteraction[H.Input, Nothing]]
        var pending = Option.empty[InstanceRecord]
        Given("that original completed scope and an explicitly internal current-call publication/issuance probe")
        withHost(path, declared) { (call, _) =>
          val owner = call.core.component.get.subsystem.get
          val publication = take(NM.bindC(owner.configuration, declaration, f.catalog))
          val issuer = take(HM.bindC(ComponentCreate(owner, ComponentOrigin.Builtin), declaration, f.catalog))
          When("the real managed publication and native Decision are issued or independently recovered")
          val observed = take(publication.executeForCallC(query(saved), call.core))
          val proposed = take(H.prepareC(observed)).get
          val actual = take(issuer.advanceForCallC(query(saved), call.core)).get
          val current = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
          Then("the actual question and current suspension match the genuine plan after each physical call")
          actual shouldBe proposed.question
          current shouldBe proposed.next
          plan = Some(proposed)
          question = Some(actual)
          pending = Some(current)
          Consequence.unit
        }
        val now = Instant.parse("2026-10-04T00:00:00Z")
        var previous = Option.empty[(HV.Admitted, ActionCall.Core)]
        Given("an internal declared-name completion probe with an explicitly fixed actual call clock, not a public completion handler")
        withHost(path, declared, operationName = H.completionOperation.operation,
          executionclock = Some(Clock.fixed(now, ZoneOffset.UTC))) { (call, _) =>
          val current = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
          val request = question.get.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request
          val answers = Vector[A.Answer](A.Answer.ProvideResolution(SplitPhaseExplicitPartitionSemantic.ContextReference("resolution", "1")),
            A.Answer.Defer, A.Answer.Cancel)
          var currentadmitted = Option.empty[HV.Admitted]
          Then("the current completion probe carries its selected clock and unchanged genuine native suspension")
          call.executionContext.clock.instant() shouldBe now
          current shouldBe pending.get
          answers.foreach { answer =>
            Given("independent host input attesting the actual question target, direction and original relay")
            val submitted = ContinuationResult(question.get.handle, request.runId, request.continuationId, request.expectedRevision,
              request.context.snapshot, TypedValue(A.resultType, answer), ContextReference("independent-human-answer", "3"),
              Vector.empty, ExecutionEvidence(Vector(ContextReference("independent-human-answer", "3")), Some("participant")))
            val context = HV.AnswerContext(call.core.component.get.core.instanceId, question.get, submitted, "participant")
            val attestation = take(ThreeWorkflowHostEvidencePorts.splitAnswerBindingC(context))
            val lookup = ThreeWorkflowHostEvidencePorts.key("independent-human-answer", "3")
            val issued = NativeHostEvidence.Evidence(NativeHostEvidence.schema, HV.purpose, lookup,
              "independent-human-answer", "3", "independent-provider", "human-user", attestation, now.minusSeconds(86400).toString)
            var evidencereads = 0
            def _evidence_(value: Option[NativeHostEvidence.Evidence]): HV.EvidencePort = new HV.EvidencePort {
              def admitC(core: ActionCall.Core, reference: ContextReference, target: Json) = {
                evidencereads += 1
                reference shouldBe submitted.resultReference
                target shouldBe attestation
                (core eq call.core) shouldBe true
                NativeHostEvidenceFixtureAdmission.admitC(core, "independent-provider", NativeHostEvidence.Purpose.SplitAnswer,
                  lookup, target, value)
              }
            }
            When("the current-call answer is admitted at its independent host input boundary")
            val verified = take(HA.admitForCallC(plan.get, submitted, current, call.core, _evidence_(Some(issued))))
            val observed = verified.evidence.forCallC(call.core)
            val expectednext = take(HA.prepareC(plan.get, submitted, current))
            Then("the host is admitted once and only the actual issued direction receives a call-bound admission and pure postimage")
            observed.isSuccess shouldBe true
            evidencereads shouldBe 1
            verified.next shouldBe expectednext
            previous.foreach { case (admitted, priorcore) =>
              When("evidence retained from a previous actual managed call is used in this distinct call")
              val crossed = admitted.forCallC(call.core)
              Then("the earlier call's admission does not transfer by equal question or data")
              (priorcore eq call.core) shouldBe false
              crossed.isSuccess shouldBe false
            }
            currentadmitted = Some(verified.evidence)
            When("a different authenticated relay identity is claimed for the same actual current call")
            val foreign = HV.admitForCallC(context.copy(submittingParticipant = "foreign"), call.core, _evidence_(Some(issued)))
            Then("authentication of the original relay does not admit another participant or call the host input boundary")
            foreign.isSuccess shouldBe false
            evidencereads shouldBe 1
            Given("absent or altered independent evidence, including a future event time")
            val foreignRelay = take(ThreeWorkflowHostEvidencePorts.splitAnswerBindingC(context.copy(submittingParticipant = "foreign")))
            val foreignReference = take(ThreeWorkflowHostEvidencePorts.splitAnswerBindingC(context.copy(
              result = submitted.copy(resultReference = ContextReference("foreign", "3")))))
            val variants = Vector(None, Some(issued.copy(revision = "foreign")),
              Some(issued.copy(issuer = "foreign")), Some(issued.copy(purpose = "split-phase-start")),
              Some(issued.copy(participant = "")),
              Some(issued.copy(binding = foreignRelay)), Some(issued.copy(binding = foreignReference)),
              Some(issued.copy(occurredAt = now.plusSeconds(1).toString)))
            variants.foreach { variant =>
              When("that absent, future-dated or crossed input is read at the host boundary")
              val rejected = HA.admitForCallC(plan.get, submitted, current, call.core, _evidence_(variant))
              Then("no verified Human answer is constructed")
              rejected.isSuccess shouldBe false
            }
            Given("an unavailable host input, not authentication-derived consent")
            val unavailable = new HV.EvidencePort {
              def admitC(core: ActionCall.Core, reference: ContextReference, target: Json) =
                throw new IllegalStateException("private host evidence unavailable")
            }
            When("independent evidence observation throws")
            val rejected = HA.admitForCallC(plan.get, submitted, current, call.core, unavailable)
            Then("indeterminate verification admits no answer and initiates no retry")
            rejected.isSuccess shouldBe false
          }
          previous = currentadmitted.map(_ -> call.core)
          When("the original native instance is independently observed after all read-only answer admissions")
          val unchanged = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
          Then("every admission remains a preparation prerequisite without a hidden completion or application effect")
          unchanged shouldBe current
          Consequence.unit
        }
      }
    }
  }

  "Native Human direction from the published whole frontier" should {
    for (partitionroot <- Vector(false, true);
        scenario <- Vector("semantic-direction", "known-unrepresentable", "numbering-decision", "repository-decision", "all-built")) {
      s"issue only genuine Human work for $scenario with partition root $partitionroot and recover it across pool closure" in {
        import org.goldenport.cncf.workflow.{WorkflowDecisionJsonV1, WorkflowWorkOrderJsonV1}
        import SplitPhaseNativeHumanDirection as H
        import SplitPhaseManagedHumanDirection as HM
        import SplitPhaseExplicitPartitionFixture.{base, enumerate, proof}
        Given("private original facts and the real public scope issue/completion pair for this first-root kind")
        val initial = base(Vector.fill(4)(180))
        val source = if (scenario == "numbering-decision") initial.copy(inventory = initial.inventory.copy(
          occupiedPhases = initial.inventory.occupiedPhases + SplitPhaseExplicitProposalPrecheck.PhaseIdentity("77.3")))
          else initial
        val template = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(1)).get,
          "native-human-template", handoffPairs = Vector(0 -> 1))
        val boundary = if (scenario == "repository-decision") {
          val rows = template.records.ownership.children
          val records = template.records.copy(ownership = template.records.ownership.copy(children = rows.updated(1,
            rows(1).copy(repository = SplitPhaseExplicitBoundaryValidation.RepositoryIdentity("second-repository")))))
          take(SplitPhaseExplicitBoundaryValidation.loadC(template.shape, records))
        } else template
        val saved = forBoundary(source, boundary)
        val decision = if (scenario == "semantic-direction") SplitPhaseScopeSemantic.Decision.DecisionNeeded
          else SplitPhaseScopeSemantic.Decision.Enriched
        val workroot = Files.createDirectories(Path.of("target", "sm-workflow", "work", "native-human-direction-spec"))
        // Diagnostic fixtures are retained; project clean owns removal after physical pool closure.
        val path = Files.createTempDirectory(workroot, "native-human-").resolve("host.sqlite")
        val declared = values ++ SplitPhasePartitionReplyFixture.values
        var first = Option.empty[K.Record]
        var completednative = Option.empty[InstanceRecord]
        withHost(path, declared, publicadvance = true) { (call, _) =>
          if (first.isEmpty) {
            When("the actual Workflow issues and completes its assigned scope work once")
            first = Some(_complete_scope_fixture(call, saved, decision, partitionroot, broadscope = scenario == "known-unrepresentable"))
            completednative = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core))
          }
          When("each Service or CLI dispatch independently reads the genuine original root and native completion")
          val targets = take(U.bindTargetsC(call.core.component.get.subsystem.get.configuration, declaration))
          val indexkey = take(U.indexKeyC(saved.before.identity, saved.record.originalContext.reference))
          val index = take(J.decodeIndexC(saved.before.identity, saved.record.originalContext.reference,
            take(call.rowC(targets.publication, indexkey)).get.getString(J.indexStorageField).get))
          val root = take(_root_c(call, saved))
          Then("the retained original first root has its actual Mechanical or PartitionCompletion provenance")
          root shouldBe first.get
          root.origin.isInstanceOf[K.Origin.PartitionCompletion] shouldBe partitionroot
          assertNative(call, completednative.get)
          Consequence.unit
        }
        val retainedvalues = declared.filterNot((key, _) => key.startsWith(SplitPhaseCandidateAllocationPolicy.prefix) ||
          key.startsWith(SplitPhasePartitionReplyAllocationPolicy.prefix))
        var issued = Option.empty[WorkflowInteraction[H.Input, Nothing]]
        var expectednative = Option.empty[InstanceRecord]
        var expiredport = Option.empty[HM.Port]
        var expiredcore = Option.empty[ActionCall.Core]
        for (mode <- Vector("first-owning-call", "physical-reopen")) {
          Given("the genuine completed scope and closed/reopened physical pool without allocation policies for " + mode)
          withHost(path, retainedvalues) { (call, _) =>
            val owner = call.core.component.get.subsystem.get
            val publication = take(NM.bindC(owner.configuration, declaration, f.catalog))
            val human = take(HM.bindC(ComponentCreate(owner, ComponentOrigin.Builtin), declaration, f.catalog))
            val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
            When("the owning Workflow publishes or recovers the complete scope frontier")
            val observed = take(publication.executeForCallC(query(saved), call.core))
            val planned = take(H.prepareC(observed))
            val published = observed.record
            Then("the Human selection is derived from all candidates, not a selected subset")
            published.snapshot.first shouldBe first.get
            planned.nonEmpty shouldBe (scenario != "all-built")
            Given("that native-bound whole-frontier observation and the original owning advance command")
            When("the canonical Decision issuer is called twice, including after physical reopen")
            val actual = take(human.advanceForCallC(query(saved), call.core))
            val repeated = take(human.advanceForCallC(query(saved), call.core))
            val native = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            val reobserved = take(publication.loadForCallC(query(saved), call.core)).get
            Then("only an actual Human need gets one adjacent native Decision and repeated calls append nothing")
            actual shouldBe repeated
            reobserved.record shouldBe published
            call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            if (scenario == "all-built") {
              actual shouldBe None
              native shouldBe published.next
            } else {
              val question = actual.get
              val request = question.current.asInstanceOf[WorkflowContinuation.Decision[H.Input]].request
              native shouldBe planned.get.next
              native.history.dropRight(1) shouldBe published.next.history
              native.suspension.get.continuationIdentity.value shouldBe request.continuationId.value
              native.currentProgression shouldBe Some(ProgressionReference("HumanPending"))
              request.requiredOperation.capability shouldBe "split-human-direction-capability"
              request.operation shouldBe org.goldenport.cncf.workflow.StateMachineOperationIdentity("SplitHumanService", "presentSplitHumanDirection")
              request.completionOperation shouldBe H.completionOperation
              request.evidence.identity shouldBe "independently-verified-split-human-direction-v1"
              take(H.frontierC(request.input.get.value)) shouldBe published.snapshot
              request.input.get.value.scopeCheckpoint shouldBe published.reference
              issued.foreach(question shouldBe _)
              issued = Some(question)
              Given("the actual native Decision wire and its complete application payload, not Human consent")
              val wire = take(WorkflowDecisionJsonV1.encodeC(question, H.inputCodec))
              val payload = H.inputCodec.encode(request.input.get.value).hcursor.downField("payload").focus.get
              When("the native Decision and actual generated raw input are independently decoded")
              val decoded = take(WorkflowDecisionJsonV1.decodeC(wire, H.inputCodec))
              val generated = payload.as[org.simplemodeling.textus.smworkflow.value.SplitPhaseHumanDirectionRequest].toOption.get
              val workorder = WorkflowWorkOrderJsonV1.decodeC(wire, H.inputCodec)
              val extended = H.inputCodec.encode(request.input.get.value).mapObject(_.add("unexpected", Json.fromBoolean(true)))
              val crossedinput = request.input.get.value.copy(scopeCheckpoint = request.input.get.value.scopeCheckpoint.copy(revision = "foreign"))
              val crossedpayload = H.inputCodec.encode(request.input.get.value).hcursor.downField("payload").downField("scopeCheckpoint")
                .downField("revision").withFocus(_ => Json.fromString("foreign")).top.get
              Then("the complete frontier survives unused annotations, while WorkOrder reinterpretation and crossed purpose data refuse")
              decoded shouldBe question
              generated.asJson shouldBe payload
              workorder.isSuccess shouldBe false
              H.inputCodec.decode(extended) shouldBe Right(request.input.get.value)
              H.inputCodec.decode(crossedpayload).isLeft shouldBe true
              H.frontierC(crossedinput).isSuccess shouldBe false
              assertNative(call, native)
              _specify_human_answer_postimages(planned.get, question, native, call)
            }
            expectednative.foreach(native shouldBe _)
            expectednative = Some(native)
            Given("a different command than the actual owning advance call")
            When("the Human issuer receives its crossed context")
            val crossed = human.advanceForCallC(query(saved).copy(context = query(saved).context.copy(revision = "foreign")), call.core)
            Then("the Decision grants neither crossed continuation nor another native append")
            crossed.isSuccess shouldBe false
            assertNative(call, native)
            call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            expiredport = Some(human)
            expiredcore = Some(call.core)
            Consequence.unit
          }
        }
        Given("a retained former issuer and core after the actual managed calls and pool closed")
        When("they are used to issue or recover Human work")
        val expired = expiredport.get.advanceForCallC(query(saved), expiredcore.get)
        Then("the closed core is not a captured Human or storage grant")
        expired.isSuccess shouldBe false
        Given("the retained actual question under a different current authenticated participant")
        withHost(path, retainedvalues, participant = "foreign") { (call, _) =>
          val human = take(HM.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin), declaration, f.catalog))
          When("that participant asks the genuine owning issuer to recover the original Decision")
          val refused = human.advanceForCallC(query(saved), call.core)
          Then("the original participant remains necessary and no native change occurs")
          refused.isSuccess shouldBe false
          assertNative(call, expectednative.get)
          Consequence.unit
        }
      }
    }
    "refuse free native plan construction and an absent owning observation" in {
      Given("passive Human payload data without native publication provenance")
      When("no owning observation is supplied to native preparation")
      val refused = SplitPhaseNativeHumanDirection.prepareC(null)
      Then("passive data cannot construct a native issuance plan")
      refused.isSuccess shouldBe false
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseNativeHumanDirection.Plan(null, null, null, null, null)")
    }
  }

  "Published planning construction" should {
    "refuse passive values as owning successor observations or constructed continuation outcomes" in {
      Given("raw records and current-state values without an independently observed owning publication")
      When("a caller tries free construction or provides no constructed observation")
      val missing = W.continuePublishedScopeC(null)
      Then("only the managed owner's genuine observation can restore the published frontier")
      missing.isSuccess shouldBe false
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseManagedScopeProjectionCheckpoint.Observed(null, null, null)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPlanningAdvance.PublishedHumanRequired(null)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPlanningAdvance.PublishedContinued(null, null, null)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseManagedScopeSemantic.Pending(null)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseManagedScopeSemantic.Completed(null)")
    }
  }

  "Actual public planning advance" should {
    for (decision <- SplitPhaseScopeSemantic.Decision.values) {
      s"issue and complete genuine $decision handoff scope work while retaining the whole checkpoint across reopen" in {
        import org.goldenport.cncf.workflow.{WorkflowWorkOrderJsonV1, WorkflowResultJsonV1, WorkflowWaitJsonV1, WorkflowComponentStorage}
        import SplitPhaseScopeSemantic as SS
        import SplitPhaseScopeSemanticJson as SJ
        import SplitPhaseTypedScopeWorkIssue as SI
        import SplitPhaseExplicitPartitionFixture.{base, enumerate, proof}
        Given("private original Start/context rows, known work, ambiguous semantic transfers and the actual public scope completion")
        val source = base(Vector.fill(4)(180))
        val template = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(1)).get,
          "public-scope-template", handoffPairs = Vector(0 -> 1))
        val saved = forBoundary(source, template)
        val workroot = Files.createDirectories(Path.of("target", "sm-workflow", "work", "scope-semantic-spec"))
        // Retain each unique SQLite fixture for diagnosing failed native/reopen checks;
        // project clean owns removal after the host has closed its pool.
        val path = Files.createTempDirectory(workroot, "public-scope-").resolve("host.sqlite")
        val wire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved)))
        val targets = take(U.bindTargetsC(host(values), declaration))
        val indexkey = take(U.indexKeyC(saved.before.identity, saved.record.originalContext.reference))
        var expectedwork = Option.empty[WorkflowInteraction[SS.Request, Nothing]]
        var expectedwire = Option.empty[String]
        var expectedroot = Option.empty[K.Record]
        var expectednative = Option.empty[InstanceRecord]
        var submitted = Option.empty[ContinuationResult[SS.Judgment]]
        var completionwire = Option.empty[String]
        var failuremessage = Option.empty[String]
        var completed = false
        for (mode <- Vector("fresh", "policy-removed")) {
          val declared = if (mode == "fresh") values else values.filterNot((key, _) => key.startsWith(SplitPhaseCandidateAllocationPolicy.prefix))
          withHost(path, declared, publicadvance = true) { (call, _) =>
            Given("the actual canonical SQLite host for " + mode)
            if (expectedwork.isEmpty) take(installC(call, saved))
            val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
            if (!completed) {
              When("public advance absorbs the mechanical prefix and selects only the scope requirement")
              val first = _scalar(take(_public_advance_c(call, wire, false)))
              val actual = take(WorkflowWorkOrderJsonV1.decodeC(first, SJ.requestCodec))
              val order = actual.current.asInstanceOf[WorkflowContinuation.WorkOrder[SS.Request]].request
              val input = order.input.get.value
              Then("the separate generated purpose and exact scope task are issued only once")
              order.operation.operation shouldBe "assessHandoffScopes"
              order.completionOperation shouldBe SI.completionOperation
              order.input.get.typeIdentity shouldBe SJ.requestCodec.typeIdentity
              actual.handle shouldBe saved.record.originalContext.owner.handle
              input.context shouldBe saved.record.originalContext.reference
              input.gaps.map(_.original) shouldBe Vector(template.records.handoffs.handoffs.head)
              val index = take(J.decodeIndexC(saved.before.identity, saved.record.originalContext.reference,
                take(call.rowC(targets.publication, indexkey)).get.getString(J.indexStorageField).get))
              val root = take(_root_c(call, saved))
              root.originalContext shouldBe saved.record.originalContext
              root.originalStart shouldBe saved.record.originalStart
              input.checkpoint shouldBe root.reference
              input.result.identity shouldBe root.reference.identity + ":handoff-scope-result"
              val frontier = root.snapshot.projection.get.asInstanceOf[SplitPhasePlanningSnapshot.ProjectionView.Requirements]
              frontier.candidates.exists(_.isInstanceOf[SplitPhasePlanningSnapshot.CandidateProjectionView.BuiltView]) shouldBe true
              expectedroot = Some(root)
              expectedwork = Some(actual)
              expectedwire = Some(first)
              expectednative = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core))
              expectednative.get.revision shouldBe saved.before.revision.next.next
              expectednative.get.suspension.get.continuationIdentity.value shouldBe order.continuationId.value
              _scalar(take(_public_advance_c(call, wire, true))) shouldBe first
              assertNative(call, expectednative.get)
              val judgment = SS.Judgment(decision, "original-transfer-closure-analysis",
                if (decision == SS.Decision.Enriched) input.gaps.map(gap => SS.Scope(gap.original.identity,
                  gap.producingItems.takeRight(1), gap.receivingItems.take(1), "actual evidence producer and receiver")) else Vector.empty,
                Vector(SplitPhaseExplicitPartitionSemantic.EvidenceReference("public-scope-evidence", "1")))
              val result = ContinuationResult(actual.handle, order.runId, order.continuationId, order.expectedRevision,
                order.context.snapshot, TypedValue(SJ.resultCodec.typeIdentity, judgment),
                ContextReference(input.result.identity, input.result.revision), Vector.empty,
                ExecutionEvidence(Vector(ContextReference("public-scope-evidence", "1")), Some("participant")))
              submitted = Some(result)
              completionwire = Some(take(WorkflowResultJsonV1.encodeC(result, SJ.resultCodec)))
              Given("a crossed host result reference and incomplete evidence for the original scope task")
              val crossed = result.copy(resultReference = result.resultReference.copy(identity = "participant-result"))
              val foreign = result.copy(result = result.result.copy(value = judgment.copy(evidence = Vector.empty)))
              for (bad <- Vector(crossed, foreign); cli <- Vector(false, true)) {
                When("the invalid reply is submitted by " + (if (cli) "CLI" else "Service"))
                val rejected = _public_operation_c(call, SI.completionOperation.operation,
                  take(WorkflowResultJsonV1.encodeC(bad, SJ.resultCodec)), cli)
                Then("it is rejected without changing native history or the exact issued work")
                rejected.isSuccess shouldBe false
                assertNative(call, expectednative.get)
                When("the same transport advances after the rejected reply")
                val retained = _scalar(take(_public_advance_c(call, wire, cli)))
                Then("the exact previously issued work is retained")
                retained shouldBe first
              }
              Given("the exact requested semantic reply with its actual execution evidence")
              When("the genuine completion is submitted by " + (if (decision == SS.Decision.Enriched) "CLI" else "Service"))
              val ack = _scalar(take(_public_operation_c(call, SI.completionOperation.operation, completionwire.get,
                decision == SS.Decision.Enriched)))
              val interaction = take(WorkflowWaitJsonV1.decodeC(ack))
              Then("only that semantic boundary commits and its acknowledgement is not application Terminal")
              interaction.current.isInstanceOf[WorkflowContinuation.Wait] shouldBe true
              expectednative = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core))
              expectednative.get.revision shouldBe saved.before.revision.next.next.next
              expectednative.get.lifecycle shouldBe Lifecycle.Active
              expectednative.get.suspension shouldBe None
              val storage = take(WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
                SmWorkflowComponent.componentId, f.configuration, Vector(f.catalog.definition), declaration.instances, declaration.continuations))
              val native = take(storage.bindCompletionC(SI.completionOperation, SJ.requestCodec, SJ.resultCodec, SI.closingType,
                (_, _) => Consequence.success(Vector.empty)))
              val retained = take(native.loadRetainedC(order.continuationId)(using call.executionContext)).get.completion
              retained.result shouldBe result
              retained.instanceRevision shouldBe expectednative.get.revision
              retained.closingResult shouldBe Some(org.goldenport.cncf.workflow.StateMachineOperationResult(SI.closingType, result.resultReference))
              failuremessage = Some("SplitPhase advance admitted " + decision.toString + " handoff scope result awaits its " +
                (if (decision == SS.Decision.Enriched) "whole-frontier mechanical projection" else "genuine human-direction") +
                " handler; no intermediate WAIT or Terminal was emitted")
              completed = true
            }
            Given("the genuine completed scope result and still uninstalled downstream handler for " + mode)
            When("both public advance transports observe the retained native lineage")
            for (cli <- Vector(false, true)) {
              val result = _public_advance_c(call, wire, cli)
              Then("actual unavailable taxonomy/message is returned without reissue or whole-application success")
              result.isSuccess shouldBe false
              result match {
                case Consequence.Failure(conclusion) =>
                  conclusion.observation.taxonomy shouldBe org.goldenport.observation.Taxonomy.serviceUnavailable
                  conclusion.display shouldBe failuremessage.get
                case _ => fail("Scope downstream handler incorrectly returned success")
              }
            }
            assertNative(call, expectednative.get)
            val index = take(J.decodeIndexC(saved.before.identity, saved.record.originalContext.reference,
              take(call.rowC(targets.publication, indexkey)).get.getString(J.indexStorageField).get))
            val root = take(_root_c(call, saved))
            root shouldBe expectedroot.get
            _original_rows(O.Original(saved.record.originalContext, saved.record.originalStart,
              saved.record.originalContext.facts), saved.record.originalStart).foreach { case (collection, identity, expected) =>
              val actual = take(call.rowC(collection, identity)).get
              expected.fields.foreach(field => actual.getString(field.key) shouldBe expected.getString(field.key))
            }
            call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            Given("the setup probe is not the owning advance Operation even though the completion is genuine")
            val scopeport = take(SplitPhaseManagedScopeSemantic.bindC(
              ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin), declaration, f.catalog))
            When("that non-owning probe requests executable scope restoration")
            val rejected = scopeport.prepareProjectionForCallC(query(saved), call.core)
            Then("passive setup authority cannot manufacture a completed native source")
            rejected.isSuccess shouldBe false
            Consequence.unit
          }
        }
        val retainedvalues = values.filterNot((key, _) => key.startsWith(SplitPhaseCandidateAllocationPolicy.prefix))
        for (mode <- Vector("native-owning-observation", "physical-reopen")) {
          Given("the real committed scope completion, all retained candidates and no allocation policy for " + mode)
          withHost(path, retainedvalues, retainedmechanical = Some(expectedroot.get -> expectednative.get)) { (call, _) =>
            val scopeport = take(SplitPhaseManagedScopeSemantic.bindC(
              ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin), declaration, f.catalog))
            val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
            val rootkey = take(U.mappingKeyC(expectedroot.get.reference))
            When("the explicit test-only owning advance call restores and applies its actual completed reply twice")
            val prepared = take(scopeport.prepareProjectionForCallC(query(saved), call.core))
            val restored = take(SplitPhasePlanningSnapshot.projectC(prepared.original))
            val repeated = take(scopeport.prepareProjectionForCallC(query(saved), call.core))
            val repeatedsnapshot = take(SplitPhasePlanningSnapshot.projectC(repeated.original))
            val native = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            val rootrow = take(call.rowC(targets.publication, rootkey)).get
            val root = take(_root_c(call, saved))
            val retainedwork = take(scopeport.loadForCallC(prepared.source.plan.work.current
              .asInstanceOf[WorkflowContinuation.WorkOrder[SS.Request]].request.continuationId, call.core)).get
            val originals = _original_rows(O.Original(saved.record.originalContext, saved.record.originalStart,
              saved.record.originalContext.facts), saved.record.originalStart).map { case (collection, identity, expected) =>
              expected -> take(call.rowC(collection, identity)).get
            }
            Then("native provenance and the complete cached frontier survive without reissue or publication")
            prepared.source.source.record shouldBe expectedroot.get
            prepared.source.completion.result shouldBe submitted.get
            prepared.source.next shouldBe expectednative.get
            restored.record shouldBe expectedroot.get.snapshot
            repeatedsnapshot.record shouldBe restored.record
            repeated.source.completion shouldBe prepared.source.completion
            root shouldBe expectedroot.get
            native shouldBe expectednative.get
            retainedwork shouldBe expectedwork.get
            originals.foreach { case (expected, actual) => expected.fields.foreach(field =>
              actual.getString(field.key) shouldBe expected.getString(field.key)) }
            call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            for (historicalcollection <- Vector(collection("starts"), collection("contexts"))) {
              Given("an unused historical Start or Context row after the actual mechanical computation")
              val identity = if (historicalcollection == collection("starts")) saved.record.originalStart.reference.identity
                else saved.record.originalContext.reference.identity
              val prior = take(call.rowC(historicalcollection, identity)).get
              take(call.writeC(historicalcollection, identity, Record.data(prior.fields.head.key -> "unused-historical-copy"), true))
              When("the owning scope operation restores the retained native calculation and genuine semantic reply")
              val actual = take(scopeport.prepareProjectionForCallC(query(saved), call.core))
              Then("no independent Start or Context body becomes a mechanical continuation gate")
              actual.source.source.record shouldBe expectedroot.get
              actual.source.completion.result shouldBe submitted.get
              actual.source.source.current shouldBe expectednative.get
              assertNative(call, expectednative.get)
              take(call.writeC(historicalcollection, identity, prior, true))
            }
            decision match {
              case SS.Decision.DecisionNeeded =>
                prepared.outcome.isInstanceOf[SplitPhaseExplicitCandidateProjection.ScopeHumanDirectionRequired] shouldBe true
                repeated.outcome.isInstanceOf[SplitPhaseExplicitCandidateProjection.ScopeHumanDirectionRequired] shouldBe true
              case SS.Decision.Enriched =>
                val applied = prepared.outcome.asInstanceOf[SplitPhaseExplicitCandidateProjection.ScopeApplied]
                val frontier = prepared.original.trace.projection.get.asInstanceOf[SplitPhaseExplicitCandidateProjection.Requirements]
                applied.candidates.map(_.original) shouldBe frontier.candidates
                applied.candidates.map(_.original.candidate.cutAfterIndices) shouldBe frontier.candidates.map(_.candidate.cutAfterIndices)
                applied.candidates.collect { case value: SplitPhaseExplicitCandidateProjection.ScopeBuilt
                    if value.documentsAlreadyPublished => value }.foreach { value =>
                  value.built shouldBe value.original
                }
                applied.reply.judgment shouldBe submitted.get.result.value
            }
            Given("the actual owning completion and already constructed full frontier, with no publication grant")
            When("the source-bound factory captures the same next mechanical intent twice")
            val capture = take(SplitPhaseScopeProjectionCheckpointPreparation.captureC(prepared))
            val recaptured = take(SplitPhaseScopeProjectionCheckpointPreparation.captureC(repeated))
            val fullsnapshot = take(SplitPhaseScopeProjectionSnapshot.projectC(prepared.outcome))
            val capturekey = take(SplitPhaseScopeProjectionCheckpointPreparation.keyC(capture.record.reference))
            val unchangednative = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            val unchangedroot = take(call.rowC(targets.publication, rootkey)).get
            Then("the consumed frontier and completion are retained and one inert append is prepared without committing it")
            capture.record shouldBe recaptured.record
            capture.intent shouldBe recaptured.intent
            capture.record.snapshot shouldBe fullsnapshot
            capture.record.scopeCompletion shouldBe prepared.source.completion
            capture.record.before shouldBe expectednative.get
            capture.record.next.history.dropRight(1) shouldBe expectednative.get.history
            capture.record.next.revision shouldBe expectednative.get.revision.next
            capture.record.next.suspension shouldBe None
            capture.record.next.lifecycle shouldBe Lifecycle.Active
            capture.record.next.currentProgression shouldBe expectednative.get.currentProgression
            capture.record.next.correlation shouldBe expectednative.get.correlation
            capture.intent.current shouldBe capture.record.before
            capture.intent.expectedRevision shouldBe capture.record.before.revision
            capture.intent.entry shouldBe capture.record.next.history.last
            capture.intent.reference shouldBe M.Reference(capture.record.reference.identity, capture.record.reference.revision)
            capture.intent.payload shouldBe TypedValue(SplitPhaseScopeProjectionCheckpointPreparation.payloadType, capture.record.savedResult)
            capturekey.startsWith("scope-successor:") shouldBe true
            capturekey should not be rootkey
            unchangednative shouldBe expectednative.get
            unchangedroot shouldBe rootrow
            call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            Given("the genuine captured full scope checkpoint and its existing native definitions")
            val fullrecord = capture.record
            When("the consumed passive payload wrapper is encoded and independently decoded")
            val fullwire = take(NJ.encodeC(fullrecord))
            val decodedfull = take(NJ.decodeC(fullrecord.reference, fullwire))
            val untouchednative = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            val untouchedroot = take(call.rowC(targets.publication, rootkey)).get
            Then("the consumed completion and native transition data survive without any commit or historical certificate")
            decodedfull.reference shouldBe fullrecord.reference
            decodedfull.snapshot shouldBe fullrecord.snapshot
            decodedfull.scopeCompletion shouldBe fullrecord.scopeCompletion
            decodedfull.scopeCompletion.closingResult.nonEmpty shouldBe true
            decodedfull shouldBe fullrecord.savedResult
            untouchednative shouldBe expectednative.get
            untouchedroot shouldBe rootrow
            call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            _specify_scope_publication(call, capture, take(U.bindTargetsC(host(values), declaration)))
            Given("the consumed passive result, foreign references and inert legacy source certificates")
            val invalidrecords = Vector(fullrecord.copy(scopeCompletion = fullrecord.scopeCompletion.copy(closingResult = None)),
              fullrecord.copy(before = fullrecord.next))
            val fulljson = parser.parse(fullwire.substring(NJ.schema.length + 1)).toOption.get
            val payload = fulljson.hcursor.downField("payload").focus.get
            val extra = payload.mapObject(_.add("scopeWork", Json.fromString("unused-malformed-legacy-work"))
              .add("scopePredecessor", Json.False).add("scopeIssued", Json.Null))
            val extendedwire = NJ.schema + ":" + fulljson.mapObject(_.add("payload", extra)).noSpaces
            When("typed payload readers consume actual fields and ignore removed legacy certificates")
            val passiverecords = invalidrecords.map(record => take(NJ.decodeC(record.reference,
              take(NJ.encodeC(record)))))
            val rejectedpurpose = NJ.decodeC(fullrecord.reference.copy(revision = "foreign"), fullwire)
            val legacy = take(NJ.decodeC(fullrecord.reference, extendedwire))
            Then("passive data grants no transition authority, old certificates do not gate reading and foreign selected data is refused")
            passiverecords.zip(invalidrecords).foreach { (actual, expected) =>
              actual.scopeCompletion shouldBe expected.scopeCompletion
              actual shouldBe expected.savedResult
            }
            rejectedpurpose shouldBe a[Consequence.Failure[?]]
            legacy shouldBe fullrecord.savedResult
            payload.asObject.get.keys.toSet.intersect(Set("scopeWork", "scopePredecessor", "scopeIssued")) shouldBe Set.empty[String]
            Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R10; Example: E24; actual checkpoint data with unused grant claims or missing consumed fields")
            val extensions = Vector(payload.mapObject(_.add("undeclaredGrant", Json.True)),
              payload.mapObject(o => o.add("reference", o("reference").get.mapObject(_.add("undeclaredGrant", Json.True)))))
            val badpayloads = Vector(payload.mapObject(_.remove("scopeCompletion")),
              payload.mapObject(o => o.add("reference", o("reference").get.mapObject(_.remove("revision")))))
            When("the typed reader consumes those frames without constructing a source certificate")
            val refusedshapes = badpayloads.map(value => NJ.decodeC(fullrecord.reference,
              NJ.schema + ":" + fulljson.mapObject(_.add("payload", value)).spaces2))
            val extended = extensions.map(value => take(NJ.decodeC(fullrecord.reference,
              NJ.schema + ":" + fulljson.mapObject(_.add("payload", value)).spaces2)))
            val compatible = take(NJ.decodeC(fullrecord.reference, NJ.schema + ":" + fulljson.spaces2))
            val nativeafter = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            Then("formatting and unused grant claims do not gate the checkpoint or grant effects; missing consumed data fails conversion")
            refusedshapes.foreach(_.isSuccess shouldBe false)
            extended.foreach(_ shouldBe fullrecord.savedResult)
            compatible.scopeCompletion shouldBe fullrecord.scopeCompletion
            nativeafter shouldBe expectednative.get
            call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            Given("an oversized external legacy certificate which is no longer emitted by the producer")
            val inflated = extra.mapObject(_.add("scopeWork", Json.fromString("語" * (NJ.maximumPayloadBytes / 3 + 1))))
            val inflatedwire = NJ.schema + ":" + fulljson.mapObject(_.add("payload", inflated)).noSpaces
            When("the existing raw byte-bound reader receives the oversized external frame")
            val refusedframe = NJ.decodeC(fullrecord.reference, inflatedwire)
            val stillnative = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            val stillroot = take(call.rowC(targets.publication, rootkey)).get
            Then("the existing resource boundary refuses the oversized raw input without retaining comparison-only bloat or granting a write")
            inflatedwire.getBytes(UTF_8).length should be > NJ.maximumPayloadBytes
            refusedframe shouldBe a[Consequence.Failure[?]]
            stillnative shouldBe expectednative.get
            stillroot shouldBe rootrow
            call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            Given("an advance query crossing the actual owning command's context")
            val crossed = query(saved).copy(context = query(saved).context.copy(revision = "foreign"))
            When("the owning call requests restoration for that crossed command")
            val refused = scopeport.prepareProjectionForCallC(crossed, call.core)
            val unchanged = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            Then("the crossed command receives no executable frontier and changes no native state")
            refused.isSuccess shouldBe false
            unchanged shouldBe expectednative.get
            Given("same-reference estimate and published Built rows whose complete data can drift independently")
            val estimates = saved.record.originalContext.facts.partition.estimates
            val changedestimates = estimates.copy(items = estimates.items.updated(0,
              estimates.items.head.copy(duration = SplitPhaseExplicitPartitionComputation.Duration.Known(181))))
            val builtin = expectedroot.get.snapshot.projection.get.candidates.collect {
              case value: SplitPhasePlanningSnapshot.CandidateProjectionView.BuiltView => value
            }.head
            val changedproposal = builtin.proposal.copy(children = builtin.proposal.children.updated(0,
              builtin.proposal.children.head.copy(expectedMinutes = builtin.proposal.children.head.expectedMinutes + 1)))
            val driftrows = Vector(
              (target(F.Purpose.Estimates), estimates.reference.identity,
                Record.data(F.storageField -> take(F.encodeEstimatesC(changedestimates)))),
              (targets.proposals, builtin.proposal.reference.identity,
                Record.data(F.storageField -> take(F.encodeProposalC(changedproposal)))))
            for ((collection, identity, changed) <- driftrows) {
              When("the owning call restores the native scope result with an unused input or a readable generated document changed")
              val priorrow = take(call.rowC(collection, identity)).get
              val observed = try {
                take(call.writeC(collection, identity, changed, update = true))
                scopeport.prepareProjectionForCallC(query(saved), call.core)
              } finally take(call.writeC(collection, identity, priorrow, update = true))
              val unchanged = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
              val restoredrow = take(call.rowC(collection, identity)).get
              Then("the actual native result is consumed without a copy certificate or another native transition")
              val restored = take(observed)
              restored.source.source.record shouldBe expectedroot.get
              restored.source.completion.result shouldBe submitted.get
              unchanged shouldBe expectednative.get
              restoredrow shouldBe priorrow
              call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            }
            Then("only native owners can construct completion sources and restored executable frontiers")
            assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseManagedScopeSemantic.CompletedSource(null, null, null, null, null)")
            assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPartitionComputation.RetainedScope(null, null)")
            Consequence.unit
          }
        }
        withHost(path, values, participant = "foreign", publicadvance = true) { (call, _) =>
          Given("a foreign actual authenticated participant and the retained original scope request/result")
          for (cli <- Vector(false, true)) {
            When("that participant invokes the real advance Operation by " + (if (cli) "CLI" else "Service"))
            val advance = _public_advance_c(call, wire, cli)
            Then("advance grants no continuation authority to that participant")
            advance.isSuccess shouldBe false
            When("that participant invokes the real completion Operation by " + (if (cli) "CLI" else "Service"))
            val completion = _public_operation_c(call, SI.completionOperation.operation, completionwire.get, cli)
            Then("completion grants no continuation authority to that participant")
            completion.isSuccess shouldBe false
          }
          Then("neither receives continuation authority and the native history remains intact")
          assertNative(call, expectednative.get)
          Consequence.unit
        }
        withHost(path, retainedvalues, participant = "foreign") { (call, _) =>
          Given("an owning advance Operation called by a different actual authenticated participant")
          val scopeport = take(SplitPhaseManagedScopeSemantic.bindC(
            ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin), declaration, f.catalog))
          When("that foreign participant requests the original completed scope frontier")
          val refused = scopeport.prepareProjectionForCallC(query(saved), call.core)
          val unchanged = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
          Then("the correct Operation name does not grant the original participant's continuation authority")
          refused.isSuccess shouldBe false
          unchanged shouldBe expectednative.get
          Consequence.unit
        }
        val completedpredecessor = expectednative.get
        val laterentry = completedpredecessor.history.last.copy(
          sequence = HistorySequence(completedpredecessor.history.last.sequence.value + 1L),
          revision = completedpredecessor.revision.next, causation = CausationReference("later-scope-capture-fixture"))
        val laterexpected = take(completedpredecessor.appendC(completedpredecessor.revision, laterentry))
        var laternative = completedpredecessor
        for (mode <- Vector("later-native", "later-physical-reopen")) {
          withHost(path, retainedvalues, retainedmechanical = Some(expectedroot.get -> laterexpected)) { (call, _) =>
            Given("the genuine completed scope prefix and its expected single later native entry for " + mode)
            val scopeport = take(SplitPhaseManagedScopeSemantic.bindC(
              ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin), declaration, f.catalog))
            val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
            When("the owning call appends that entry at most once and reads its native and first-row baseline")
            if (mode == "later-native" && laternative == completedpredecessor) {
              laternative = take(instances.appendForCallC(f.configuration, completedpredecessor.identity,
                completedpredecessor.revision, laterentry, call.core))
            }
            val rootkey = take(U.mappingKeyC(expectedroot.get.reference))
            val before = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            val rootbefore = take(call.rowC(targets.publication, rootkey)).get
            val indexbefore = take(call.rowC(targets.publication, indexkey)).get
            When("the owning call observes its retained frontier and prepares an inert historical successor intent")
            val retained = take(scopeport.prepareProjectionForCallC(query(saved), call.core))
            val snapshot = take(SplitPhaseScopeProjectionSnapshot.projectC(retained.outcome))
            val captured = take(NC.captureC(retained))
            val descriptors = take(NU.prepareC(captured, targets))
            val programs = descriptors.rows.map { row =>
              val storage = take(org.goldenport.cncf.datastore.ComponentRecordStorage.bindForCallC(call.core,
                targets.storeName, row.collection, DataStore.StringEntryId(row.identity)))
              take(storage.stageCreateC(row.payload))
            }
            val program = programs.foldLeft(org.goldenport.ConsequenceT.pure[
              [X] =>> org.goldenport.cncf.Program[org.goldenport.cncf.unitofwork.UnitOfWorkOp, X], Unit](()))(
              (before, next) => before.flatMap(_ => next))
            val native = take(take(WorkflowComponentStorage.bindC(
              ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
              SmWorkflowComponent.componentId, declaration.configuration, Vector(f.catalog.definition),
              declaration.instances, declaration.continuations))
              .bindMechanicalC(K.advanceOperation, NJ.payloadCodec))
            When("the actual managed native executor consumes the obsolete expected revision and all Creates, including none")
            val rejected = native.executeC(captured.intent, program)(using call.executionContext)
            val after = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            val rootafter = take(call.rowC(targets.publication, rootkey)).get
            val indexafter = take(call.rowC(targets.publication, indexkey)).get
            val receipt = take(call.rowC(declaration.continuations, take(M.keyC(captured.intent.reference))))
            val rows = descriptors.rows.map(value => take(call.rowC(value.collection, value.identity)))
            Then("passive preparation succeeds but actual stale execution grants no append, receipt or local row")
            before shouldBe laternative
            laternative.history.dropRight(1) shouldBe completedpredecessor.history
            laternative.revision shouldBe completedpredecessor.revision.next
            retained.source.source.current shouldBe laternative
            retained.source.next shouldBe completedpredecessor
            retained.source.source.current should not be retained.source.next
            retained.source.plan.work shouldBe expectedwork.get
            retained.source.completion.result shouldBe submitted.get
            retained.source.source.record shouldBe expectedroot.get
            snapshot.first shouldBe expectedroot.get
            snapshot.judgment shouldBe submitted.get.result.value
            captured.record.snapshot shouldBe snapshot
            captured.intent.current shouldBe completedpredecessor
            captured.intent.expectedRevision shouldBe completedpredecessor.revision
            rejected shouldBe a[Consequence.Failure[?]]
            rejected.toString should include ("CWF77-WORKFLOW-PERSISTENCE-007")
            receipt shouldBe None
            rows.foreach(_ shouldBe None)
            after shouldBe before
            rootafter shouldBe rootbefore
            indexafter shouldBe indexbefore
            call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            Consequence.unit
          }
        }
      }
    }
    "PartitionCompletion-root scope successors" which {
      for (decision <- SplitPhaseScopeSemantic.Decision.values) {
        s"preserve the genuine partition completion and $decision scope frontier across physical reopen" in {
          import org.goldenport.cncf.workflow.{WorkflowWorkOrderJsonV1, WorkflowResultJsonV1, WorkflowWaitJsonV1, WorkflowComponentStorage}
          import SplitPhaseScopeSemantic as SS
          import SplitPhaseScopeSemanticJson as SJ
          import SplitPhaseTypedScopeWorkIssue as SI
          import SplitPhaseExplicitPartitionSemantic as PS
          import SplitPhaseExplicitPartitionSemanticJson as PJ
          import SplitPhasePartitionCompletionCheckpointPreparation as AC
          import SplitPhasePartitionCompletionCheckpointJsonV1 as AJ
          import SplitPhaseExplicitPartitionComputation as E
          import SplitPhaseExplicitPartitionFixture.{base, enumerate, proof}
          Given("private original typed Start facts with an unresolved duration and ambiguous semantic transfers")
          val source = base(Vector.fill(4)(180))
          val template = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(1)).get,
            "partition-scope-template", handoffPairs = Vector(0 -> 1))
          val saved = forBoundary(source, template)
          val known = saved.record.originalContext.facts
          val estimates = known.partition.estimates
          val facts = known.copy(partition = known.partition.copy(estimates = estimates.copy(items =
            estimates.items.updated(0, estimates.items.head.copy(duration = E.Duration.Unresolved("original semantic estimate required"))))))
          val context = saved.record.originalContext.copy(facts = facts)
          val original = O.Original(context, saved.record.originalStart, facts)
          val workroot = Files.createDirectories(Path.of("target", "sm-workflow", "work", "scope-semantic-spec"))
          // Retain unique fixtures for diagnosis; project clean owns removal after pool closure.
          val path = Files.createTempDirectory(workroot, "partition-root-scope-").resolve("host.sqlite")
          val declared = values ++ SplitPhasePartitionReplyFixture.values
          val targets = take(U.bindTargetsC(host(declared), declaration))
          val wire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved)))
          var partition = Option.empty[AC.Record]
          var scopework = Option.empty[WorkflowInteraction[SS.Request, Nothing]]
          var scoperesult = Option.empty[ContinuationResult[SS.Judgment]]
          var expectednative = Option.empty[InstanceRecord]
          var expectedwrapper = Option.empty[NC.Record]
          withHost(path, declared, publicadvance = true) { (call, _) =>
            if (expectednative.isEmpty) {
              Given("the real public continuation pair and only the private original Start rows")
              When("public advance receives those original unresolved facts at the actual typed Start")
              _original_rows(original, saved.record.originalStart).foreach { case (c, id, row) => take(call.writeC(c, id, row)) }
              take(instances.createForCallC(f.configuration, f.initial, call.core))
              take(instances.appendForCallC(f.configuration, saved.before.identity, initialRevision, saved.before.history.head, call.core))
              val issued = take(WorkflowWorkOrderJsonV1.decodeC(_scalar(take(_public_advance_c(call, wire, false))), PJ.requestCodec))
              val pending = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[PS.Request]].request
              val submitted = _semantic_result(issued, PS.Decision.Enriched)
              val issuednative = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
              Then("the workflow requests the unresolved estimate before it can publish any planning root")
              pending.input.get.value.items.map(_.identity) shouldBe Vector(estimates.items.head.identity)
              issuednative.revision shouldBe saved.before.revision.next
              issuednative.suspension.get.continuationIdentity.value shouldBe submitted.continuationId.value
              Given("only the partition reply and result reference assigned by that actual WorkOrder")
              val resultwire = take(WorkflowResultJsonV1.encodeC(submitted, PJ.resultCodec))
              When("the real public partition completion admits that reply and its native post-image")
              val ackwire = _scalar(take(_public_operation_c(call, SplitPhaseExplicitPartitionContext.completionOperation.operation,
                resultwire, decision == SS.Decision.DecisionNeeded)))
              val ack = take(WorkflowWaitJsonV1.decodeC(ackwire))
              val row = take(call.rowC(collection("publications"), take(AC.lineageKeyC(submitted.continuationId)))).get
              val completed = take(AJ.decodeC(submitted.continuationId, row.getString(AJ.storageField).get, Vector(f.catalog.definition)))
              val root = completed.checkpoint.get
              Then("the first root is the actual partition completion, never a relabeled mechanical fixture")
              completed.context shouldBe context
              completed.start.routing shouldBe original.start
              completed.issued shouldBe issued
              completed.completion.result shouldBe submitted
              completed.before shouldBe issuednative
              completed.next.history.dropRight(1) shouldBe issuednative.history
              completed.next.revision shouldBe issuednative.revision.next
              root.origin shouldBe K.Origin.PartitionCompletion(submitted.continuationId,
                PS.ResultReference(submitted.resultReference.identity, submitted.resultReference.revision))
              root.originalContext shouldBe context
              root.originalStart.routing shouldBe original.start
              ack.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value shouldBe completed.next.revision.value.toString
              Given("the genuine partition-completed root with its whole mixed candidate frontier")
              When("public advance selects the next semantic scope requirement from that retained root")
              val issuedwire = _scalar(take(_public_advance_c(call, wire, true)))
              val actual = take(WorkflowWorkOrderJsonV1.decodeC(issuedwire, SJ.requestCodec))
              val order = actual.current.asInstanceOf[WorkflowContinuation.WorkOrder[SS.Request]].request
              val input = order.input.get.value
              val scopenative = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
              Then("the scope issue has the exact partition root and one adjacent real native suspension")
              input.context shouldBe context.reference
              input.checkpoint shouldBe root.reference
              input.gaps.map(_.original) shouldBe Vector(template.records.handoffs.handoffs.head)
              scopenative.history.dropRight(1) shouldBe completed.next.history
              scopenative.revision shouldBe completed.next.revision.next
              scopenative.suspension.get.continuationIdentity.value shouldBe order.continuationId.value
              root.snapshot.projection.get.asInstanceOf[SplitPhasePlanningSnapshot.ProjectionView.Requirements]
                .candidates.exists(_.isInstanceOf[SplitPhasePlanningSnapshot.CandidateProjectionView.BuiltView]) shouldBe true
              Given("only the scope reply requested by the issued scope WorkOrder")
              val judgment = SS.Judgment(decision, "partition-root transfer evidence",
                if (decision == SS.Decision.Enriched) input.gaps.map(gap => SS.Scope(gap.original.identity,
                  gap.producingItems.takeRight(1), gap.receivingItems.take(1), "actual producer and receiver")) else Vector.empty,
                Vector(PS.EvidenceReference("partition-root-scope-evidence", "1")))
              val reply = ContinuationResult(actual.handle, order.runId, order.continuationId, order.expectedRevision,
                order.context.snapshot, TypedValue(SJ.resultCodec.typeIdentity, judgment),
                ContextReference(input.result.identity, input.result.revision), Vector.empty,
                ExecutionEvidence(Vector(ContextReference("partition-root-scope-evidence", "1")), Some("participant")))
              val replywire = take(WorkflowResultJsonV1.encodeC(reply, SJ.resultCodec))
              When("the actual scope completion commits the assigned reply in the same host")
              val scopeack = take(WorkflowWaitJsonV1.decodeC(_scalar(take(_public_operation_c(call,
                SI.completionOperation.operation, replywire, decision == SS.Decision.Enriched)))))
              val next = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
              val storage = take(WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
                SmWorkflowComponent.componentId, f.configuration, Vector(f.catalog.definition), declaration.instances, declaration.continuations))
              val completion = take(storage.bindCompletionC(SI.completionOperation, SJ.requestCodec, SJ.resultCodec, SI.closingType,
                (_, _) => Consequence.success(Vector.empty)))
              val retained = take(completion.loadRetainedC(order.continuationId)(using call.executionContext)).get.completion
              Then("partition and scope completion histories remain contiguous and the real closing result is retained")
              next.history.dropRight(1) shouldBe scopenative.history
              next.revision shouldBe scopenative.revision.next
              next.suspension shouldBe None
              next.lifecycle shouldBe Lifecycle.Active
              retained.result shouldBe reply
              retained.instanceRevision shouldBe next.revision
              retained.closingResult shouldBe Some(org.goldenport.cncf.workflow.StateMachineOperationResult(SI.closingType, reply.resultReference))
              scopeack.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value shouldBe next.revision.value.toString
              partition = Some(completed)
              scopework = Some(actual)
              scoperesult = Some(reply)
              expectednative = Some(next)
            }
            Given("the actual completed partition and scope lineage, without an installed downstream success handler")
            val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
            When("both public transports advance and the original partition completion is independently observed")
            val later = Vector(false, true).map(cli => _public_advance_c(call, wire, cli))
            val observed = take(SplitPhaseManagedTypedPartitionCompletion.observeForCallC(partition.get.completion.result.continuationId,
              declaration, f.catalog, call.core)).get
            val current = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            Then("no reissue or invented Terminal occurs and the original native completion remains exact")
            later.foreach {
              case Consequence.Failure(conclusion) => conclusion.observation.taxonomy shouldBe org.goldenport.observation.Taxonomy.serviceUnavailable
              case Consequence.Success(value) => fail("Partition-root scope downstream invented success: " + value.toString)
            }
            observed.record shouldBe partition.get
            observed.current shouldBe expectednative.get
            current shouldBe expectednative.get
            call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            Consequence.unit
          }
          val retainedvalues = declared.filterNot((key, _) => key.startsWith(SplitPhaseCandidateAllocationPolicy.prefix) ||
            key.startsWith(SplitPhasePartitionReplyAllocationPolicy.prefix))
          for (mode <- Vector("owning-observation", "physical-reopen")) {
            Given("the genuine completed rows after host/pool closure, without allocation policies for " + mode)
            withHost(path, retainedvalues) { (call, _) =>
              val scopeport = take(SplitPhaseManagedScopeSemantic.bindC(
                ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin), declaration, f.catalog))
              val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
              When("the actual owning advance call restores the completed source and passively roundtrips its full wrapper")
              val prepared = take(scopeport.prepareProjectionForCallC(query(saved), call.core))
              val captured = take(NC.captureC(prepared))
              val repeated = take(NC.captureC(take(scopeport.prepareProjectionForCallC(query(saved), call.core))))
              val encoded = take(NJ.encodeC(captured.record))
              val decoded = take(NJ.decodeC(captured.record.reference, encoded))
              val rootrow = take(call.rowC(targets.publication, take(U.mappingKeyC(partition.get.checkpoint.get.reference)))).get
              val root = take(J.decodeC(partition.get.checkpoint.get.reference, rootrow.getString(J.storageField).get))
              val current = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
              val originals = _original_rows(original, saved.record.originalStart).map { case (c, id, row) => row -> take(call.rowC(c, id)).get }
              Then("all original partition provenance and the complete frontier survive with no native or application write")
              captured.record.snapshot.first shouldBe partition.get.checkpoint.get
              captured.record.snapshot.first.originalContext shouldBe context
              captured.record.snapshot.first.originalStart.routing shouldBe original.start
              captured.record.scopeCompletion.result shouldBe scoperesult.get
              captured.record.scopeCompletion.closingResult shouldBe Some(org.goldenport.cncf.workflow.StateMachineOperationResult(
                SI.closingType, scoperesult.get.resultReference))
              captured.record.before shouldBe expectednative.get
              captured.record.next.history.dropRight(1) shouldBe expectednative.get.history
              captured.record.next.revision shouldBe expectednative.get.revision.next
              decoded shouldBe captured.record.savedResult
              repeated.record shouldBe captured.record
              root shouldBe partition.get.checkpoint.get
              current shouldBe expectednative.get
              originals.foreach { case (expected, actual) => expected.fields.foreach(field =>
                actual.getString(field.key) shouldBe expected.getString(field.key)) }
              expectedwrapper.foreach(previous => captured.record shouldBe previous)
              expectedwrapper = Some(captured.record)
              call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
              _specify_scope_publication(call, captured, targets)
              Consequence.unit
            }
          }
          withHost(path, retainedvalues, participant = "foreign") { (call, _) =>
            Given("a different actual authenticated participant with all original partition-root rows still retained")
            val scopeport = take(SplitPhaseManagedScopeSemantic.bindC(
              ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin), declaration, f.catalog))
            When("that participant asks the owning advance Operation to restore the completed scope")
            val refused = scopeport.prepareProjectionForCallC(query(saved), call.core)
            val unchanged = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            Then("the real partition origin does not confer another participant's continuation authority")
            refused shouldBe a[Consequence.Failure[?]]
            unchanged shouldBe expectednative.get
            Consequence.unit
          }
        }
      }
    }
    for (decision <- SplitPhaseExplicitPartitionSemantic.Decision.values) {
      s"complete the genuine $decision reply through the public pair and preserve its later frontier on reopen" in {
        import org.goldenport.cncf.workflow.{WorkflowWorkOrderJsonV1, WorkflowResultJsonV1, WorkflowWaitJsonV1}
        import SplitPhaseExplicitPartitionSemanticJson as SJ
        import SplitPhasePartitionCompletionCheckpointPreparation as AC
        import SplitPhasePartitionCompletionCheckpointJsonV1 as AJ
        Given("private original typed Start rows, real SQLite, and the actual public continuation pair")
        val saved = capture()
        val original = O.Original(f.context.copy(owner = f.context.owner.copy(instanceRevision = saved.before.revision)), f.record, f.facts)
        val path = Files.createTempDirectory("smwf-public-roundtrip-").resolve("host.sqlite")
        val declared = values ++ SplitPhasePartitionReplyFixture.values
        val ap = SplitPhaseCandidateAllocationPolicy.prefix
        val rp = SplitPhasePartitionReplyAllocationPolicy.prefix
        val wire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved)))
        var expected = Option.empty[AC.Record]
        var retainedrows = Vector.empty[(DataStore.CollectionId, String, Record)]
        for (mode <- Vector("fresh", "reopened-without-policy")) {
          val currentvalues = if (mode == "fresh") declared
            else declared.filterNot((key, _) => key.startsWith(ap) || key.startsWith(rp))
          withHost(path, currentvalues, publicadvance = true) { (call, _) =>
            if (expected.isEmpty) {
              Given("only original rows and revision1, with no private WorkOrder issue or completion call")
              _original_rows(original, f.record).foreach { case (c, id, row) => take(call.writeC(c, id, row)) }
              take(instances.createForCallC(f.configuration, f.initial, call.core))
              take(instances.appendForCallC(f.configuration, saved.before.identity, initialRevision, saved.before.history.head, call.core))
              When("the real public advance issues the requested WorkOrder")
              val issuedwire = _scalar(take(_public_advance_c(call, wire, false)))
              val issued = take(WorkflowWorkOrderJsonV1.decodeC(issuedwire, SJ.requestCodec))
              val submitted = _semantic_result(issued, decision)
              val issuednative = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
              val issuedrow = take(call.rowC(declaration.continuations, original.context.owner.continuation.value)).get
              Then("the actual native instance has exactly one suspension at revision2")
              issuednative.revision shouldBe saved.before.revision.next
              issuednative.suspension.get.continuationIdentity.value shouldBe submitted.continuationId.value
              Given("a result with the same request but a participant-chosen result reference")
              val crossed = take(WorkflowResultJsonV1.encodeC(submitted.copy(
                resultReference = ContextReference("participant-chosen-result", "1")), SJ.resultCodec))
              val completionoperation = SplitPhaseExplicitPartitionContext.completionOperation.operation
              When("both actual public transports receive the crossed result")
              val refused = Vector(false, true).map(cli => _public_operation_c(call, completionoperation, crossed, cli))
              Then("the suspension and original issue remain unchanged and no completion lineage exists")
              refused.foreach(_.isSuccess shouldBe false)
              assertNative(call, issuednative)
              take(call.rowC(declaration.continuations, original.context.owner.continuation.value)) shouldBe Some(issuedrow)
              take(call.rowC(collection("publications"), take(AC.lineageKeyC(submitted.continuationId)))) shouldBe None
              _assert_no_checkpoint(call, saved)
              Given("the exact requested semantic reply, with no caller-selected route or candidate")
              val resultwire = take(WorkflowResultJsonV1.encodeC(submitted, SJ.resultCodec))
              When("the actual public completion runs in the owning managed call")
              val completionwire = _scalar(take(_public_operation_c(call, completionoperation, resultwire,
                decision == SplitPhaseExplicitPartitionSemantic.Decision.Enriched)))
              val raw = take(call.rowC(collection("publications"), take(AC.lineageKeyC(submitted.continuationId)))).get
              val actual = take(AJ.decodeC(submitted.continuationId, raw.getString(AJ.storageField).get, Vector(f.catalog.definition)))
              Then("the canonical original issue, exact reply and adjacent native completion are committed together")
              actual.context shouldBe original.context
              actual.start.routing shouldBe original.start
              actual.issued shouldBe issued
              actual.completion.result shouldBe submitted
              actual.before shouldBe issuednative
              actual.next.history.dropRight(1) shouldBe issuednative.history
              actual.next.revision shouldBe issuednative.revision.next
              actual.next.lifecycle shouldBe Lifecycle.Active
              actual.next.suspension shouldBe None
              actual.wire shouldBe completionwire
              val acknowledgement = take(WorkflowWaitJsonV1.decodeC(completionwire))
              acknowledgement.handle shouldBe issued.handle
              acknowledgement.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value shouldBe actual.next.revision.value.toString
              actual.checkpoint.nonEmpty shouldBe (decision == SplitPhaseExplicitPartitionSemantic.Decision.Enriched)
              actual.header.nonEmpty shouldBe actual.checkpoint.nonEmpty
              actual.checkpoint.foreach { checkpoint =>
                checkpoint.origin shouldBe K.Origin.PartitionCompletion(submitted.continuationId,
                  SplitPhaseExplicitPartitionSemantic.ResultReference(submitted.resultReference.identity, submitted.resultReference.revision))
                take(SplitPhasePlanningSnapshot.dispositionC(checkpoint.snapshot)) should not be SplitPhasePlanningSnapshot.Disposition.AllocationRequired
              }
              assertNative(call, actual.next)
              expected = Some(actual)
              val targets = take(U.bindTargetsC(host(declared), declaration))
              val captured = actual.checkpoint.toVector.flatMap(k => SplitPhasePlanningCheckpointObservation.builtDocuments(k.snapshot))
              val keys = Vector((collection("publications"), take(AC.lineageKeyC(submitted.continuationId))),
                (declaration.continuations, submitted.continuationId.value)) ++
                actual.header.toVector.map(h => (collection("publications"), h.result.identity)) ++
                actual.checkpoint.toVector.flatMap(k => Vector(
                  (targets.publication, take(U.mappingKeyC(k.reference))),
                  (targets.publication, take(U.indexKeyC(k.originalStart.instanceIdentity, k.originalContext.reference))),
                  (targets.estimates, k.snapshot.selection.partition.estimates.reference.identity),
                  (targets.handoffs, k.snapshot.selection.boundary.handoffs.reference.identity),
                  (targets.planning, k.snapshot.selection.planning.reference.identity))) ++
                captured.flatMap(d => Vector((targets.proposals, d.proposal.reference.identity),
                  (targets.ownerships, d.ownership.reference.identity), (targets.handoffs, d.handoffs.reference.identity)))
              retainedrows = keys.distinct.map { case (c, id) => (c, id, take(call.rowC(c, id)).get) }
            }
            Given("genuine completed lineage at the later frontier for " + mode)
            val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
            When("both public advance transports observe the now-unsuspended instance")
            val later = Vector(false, true).map(cli => _public_advance_c(call, wire, cli))
            Then("the unavailable later handler refuses instead of reissuing, replaying old WAIT or inventing Terminal")
            val expectedmessage = expected.get.checkpoint match {
              case Some(checkpoint) => "SplitPhase advance retained " + take(SplitPhasePlanningSnapshot.dispositionC(checkpoint.snapshot)).toString +
                " frontier awaits its installed continuation handler; no intermediate WAIT or Terminal was emitted"
              case None => "SplitPhase advance genuine human-direction completion awaits its typed Decision handler"
            }
            later.foreach {
              case Consequence.Failure(conclusion) =>
                conclusion.observation.taxonomy shouldBe org.goldenport.observation.Taxonomy.serviceUnavailable
                conclusion.display shouldBe expectedmessage
              case Consequence.Success(value) => fail("Later public advance invented success: " + value.toString)
            }
            assertNative(call, expected.get.next)
            retainedrows.foreach { case (c, id, row) => take(call.rowC(c, id)) shouldBe Some(row) }
            _original_rows(original, f.record).foreach { case (c, id, row) =>
              val current = take(call.rowC(c, id)).get
              row.fields.foreach(field => current.getString(field.key) shouldBe row.getString(field.key))
            }
            call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            Consequence.unit
          }
        }
      }
    }
    "issue the first genuine WorkOrder and recover it through Service, CLI and reopen without policy lookup" in {
      Given("private original unknown-duration inputs at typed Start, without private planning or issue execution")
      val saved = capture()
      val original = O.Original(f.context.copy(owner = f.context.owner.copy(instanceRevision = saved.before.revision)), f.record, f.facts)
      val path = Files.createTempDirectory("smwf-public-issue-").resolve("host.sqlite")
      val issuevalues = values ++ SplitPhasePartitionReplyFixture.values
      val ap = SplitPhaseCandidateAllocationPolicy.prefix
      val rp = SplitPhasePartitionReplyAllocationPolicy.prefix
      val wire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved)))
      var expected = Option.empty[String]
      var expectednative = Option.empty[InstanceRecord]
      var expectedrow = Option.empty[Record]
      for (mode <- Vector("fresh", "policy-removed", "policy-changed")) {
        val declared = if (mode == "policy-removed") issuevalues.filterNot((key, _) => key.startsWith(ap) || key.startsWith(rp))
          else if (mode == "policy-changed") issuevalues.updated(rp + "namespace", ConfigurationValue.StringValue("other-reply"))
            .updated(ap + "namespace", ConfigurationValue.StringValue("other-candidate")) else issuevalues
        withHost(path, declared, publicadvance = true) { (call, _) =>
          Given("an authenticated owning host for " + mode)
          if (expected.isEmpty) {
            _original_rows(original, f.record).foreach { case (c, id, row) => take(call.writeC(c, id, row)) }
            take(instances.createForCallC(f.configuration, f.initial, call.core))
            take(instances.appendForCallC(f.configuration, saved.before.identity, initialRevision, saved.before.history.head, call.core))
          }
          val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
          When("the real public advance Operation is invoked through both Service and CLI")
          val first = _scalar(take(_public_advance_c(call, wire, false)))
          val second = _scalar(take(_public_advance_c(call, wire, true)))
          Then("only one actual suspension exists and every subsequent call returns the exact issued work")
          val interaction = take(org.goldenport.cncf.workflow.WorkflowWorkOrderJsonV1.decodeC(first,
            SplitPhaseExplicitPartitionSemanticJson.requestCodec))
          interaction.current.isInstanceOf[WorkflowContinuation.WorkOrder[?]] shouldBe true
          if (expected.isEmpty) {
            expected = Some(first)
            expectednative = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core))
            expectedrow = take(call.rowC(declaration.continuations, original.context.owner.continuation.value))
          }
          first shouldBe expected.get
          second shouldBe first
          expectednative.get.revision shouldBe saved.before.revision.next
          expectednative.get.suspension.get.continuationIdentity shouldBe InstanceContinuationIdentity(original.context.owner.continuation.value)
          assertNative(call, expectednative.get)
          take(call.rowC(declaration.continuations, original.context.owner.continuation.value)) shouldBe expectedrow
          _assert_no_checkpoint(call, saved)
          call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
          Given("a malformed stored issued-work frame, followed by restoration of the original fixture row")
          take(call.writeC(declaration.continuations, original.context.owner.continuation.value,
            Record.data("issuedWorkPayload" -> "malformed"), true))
          When("both public transports encounter the malformed frame")
          val malformed = Vector(false, true).map(cli => _public_advance_c(call, wire, cli))
          Then("both fail without another native append, checkpoint or invented work")
          malformed.foreach(_.isSuccess shouldBe false)
          assertNative(call, expectednative.get)
          _assert_no_checkpoint(call, saved)
          take(call.writeC(declaration.continuations, original.context.owner.continuation.value, expectedrow.get, true))
          Given("a wrong public instance selector and malformed public JSON")
          val wrongwire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved).copy(instanceIdentity = "wrong-instance")))
          When("the public parser and original-run observer receive these invalid inputs")
          val invalid = Vector(false, true).flatMap(cli => Vector(_public_advance_c(call, wrongwire, cli),
            _public_advance_c(call, "{}", cli)))
          Then("no invalid request is treated as a fresh run or granted a new issue")
          invalid.foreach(_.isSuccess shouldBe false)
          assertNative(call, expectednative.get)
          take(call.rowC(declaration.continuations, original.context.owner.continuation.value)) shouldBe expectedrow
          Consequence.unit
        }
      }
      withHost(path, issuevalues, participant = "foreign", publicadvance = true) { (call, _) =>
        Given("a different actual authenticated local participant with the original issued work still stored")
        When("the foreign participant calls public advance through Service and CLI")
        val refused = Vector(false, true).map(cli => _public_advance_c(call, wire, cli))
        Then("the original participant boundary is retained and native state is unchanged")
        refused.foreach(_.isSuccess shouldBe false)
        assertNative(call, expectednative.get)
        take(call.rowC(declaration.continuations, original.context.owner.continuation.value)) shouldBe expectedrow
        Consequence.unit
      }
    }

    "automatically publish one complete mechanical checkpoint and refuse to invent a later successful frontier" in {
      Given("known-duration original inputs without a prior checkpoint or semantic issue")
      val saved = capture()
      val path = Files.createTempDirectory("smwf-public-mechanical-").resolve("host.sqlite")
      val ap = SplitPhaseCandidateAllocationPolicy.prefix
      val wire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved)))
      val targets = take(U.bindTargetsC(host(values), declaration))
      val indexkey = take(U.indexKeyC(saved.before.identity, saved.record.originalContext.reference))
      var expected = Option.empty[K.Record]
      var expectednative = Option.empty[InstanceRecord]
      for (mode <- Vector("fresh", "policy-removed", "policy-changed")) {
        val declared = if (mode == "policy-removed") values.filterNot((key, _) => key.startsWith(ap))
          else if (mode == "policy-changed") values.updated(ap + "namespace", ConfigurationValue.StringValue("different-candidate")) else values
        withHost(path, declared, publicadvance = true) { (call, _) =>
          Given("the canonical SQLite host for " + mode)
          if (expected.isEmpty) take(installC(call, saved))
          val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
          When("the real public advance runs through Service and CLI")
          val results = Vector(false, true).map(cli => _public_advance_c(call, wire, cli))
          Then("the known planning prefix commits once while the unavailable later handler is a failure, never successful WAIT or Terminal")
          results.foreach(_.isSuccess shouldBe false)
          val index = take(J.decodeIndexC(saved.before.identity, saved.record.originalContext.reference,
            take(call.rowC(targets.publication, indexkey)).get.getString(J.indexStorageField).get))
          val record = take(_root_c(call, saved))
          if (expected.isEmpty) {
            expected = Some(record)
            expectednative = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core))
          }
          record shouldBe expected.get
          record.originalContext shouldBe saved.record.originalContext
          record.originalStart shouldBe saved.record.originalStart
          record.committedRevision shouldBe saved.before.revision.next
          record.origin match {
            case K.Origin.Mechanical(actual) =>
              take(call.rowC(declaration.continuations, take(M.keyC(actual)))).nonEmpty shouldBe true
            case _ => fail("Public known-input advance did not retain its genuine mechanical origin")
          }
          take(SplitPhasePlanningSnapshot.dispositionC(record.snapshot)) should not be SplitPhasePlanningSnapshot.Disposition.AllocationRequired
          assertNative(call, expectednative.get)
          expectednative.get.suspension shouldBe None
          take(call.rowC(declaration.continuations, saved.record.originalContext.owner.continuation.value)) shouldBe None
          call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
          Consequence.unit
        }
      }
    }
  }

  "Public fresh mechanical current inputs" should {
    "compute from actual current estimates without using the stored context copy as an unchanged-data gate" in {
      Given("a historical context and a valid current estimates row whose known duration is now 261 minutes")
      val saved = capture()
      val estimates = saved.record.originalContext.facts.partition.estimates
      val changed = estimates.copy(items = estimates.items.updated(0, estimates.items.head.copy(
        duration = SplitPhaseExplicitPartitionComputation.Duration.Known(261))))
      val path = Files.createTempDirectory("smwf-current-estimates-").resolve("host.sqlite")
      val wire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved)))
      var installed = false
      var expected = Option.empty[K.Record]
      var expectednative = Option.empty[InstanceRecord]
      var expectedwire = Option.empty[String]
      for (mode <- Vector("fresh", "physical-reopen")) {
        withHost(path, publicadvance = true) { (call, _) =>
          if (!installed) {
            take(installC(call, saved))
            take(call.writeC(target(F.Purpose.Estimates), estimates.reference.identity,
              Record.data(F.storageField -> take(F.encodeEstimatesC(changed))), true))
            installed = true
          }
          When("the real public advance operation consumes current inputs in " + mode + " without invoking its CLI")
          val result = _public_advance_c(call, wire, false)
          val actual = take(_root_c(call, saved))
          val native = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
          val terminalwire = _scalar(take(result))
          val terminal = take(org.goldenport.cncf.workflow.WorkflowTerminalJsonV1.decodeC(terminalwire,
            SplitPhaseManagedSelectedPreview.payloadCodec))
          val payload = terminal.current.asInstanceOf[WorkflowContinuation.Terminal[Json]].result.value
          Then("the actual duration produces a unique selected Preview Terminal and reopening does not repeat its native history")
          payload.hcursor.get[String]("mode").toOption shouldBe Some("PREVIEW")
          take(SplitPhasePlanningSnapshot.dispositionC(actual.snapshot)) shouldBe SplitPhasePlanningSnapshot.Disposition.Planned
          actual.snapshot.selection.partition.estimates.items.head.duration shouldBe
            SplitPhaseExplicitPartitionComputation.Duration.Known(261)
          actual.originalContext.facts.partition.estimates.items.head.duration shouldBe
            SplitPhaseExplicitPartitionComputation.Duration.Known(261)
          actual.committedRevision shouldBe saved.before.revision.next
          native.lifecycle shouldBe Lifecycle.Completed
          native.suspension shouldBe None
          native.revision shouldBe actual.committedRevision.next.next
          if (expected.isEmpty) {
            expected = Some(actual); expectednative = Some(native); expectedwire = Some(terminalwire)
          } else {
            actual shouldBe expected.get; native shouldBe expectednative.get; terminalwire shouldBe expectedwire.get
          }
          Consequence.unit
        }
      }
    }
  }

  "E4 Public selected APPLY" should {
    import org.goldenport.cncf.processexecution.*
    import org.simplemodeling.textus.smworkflow.runtime.SelectedPhaseDocumentProgram as Program
    import SplitPhaseExplicitPartitionRawCodecs.given
    import scala.jdk.CollectionConverters.*

    for (episode <- Vector("normal", "effect-ack", "terminal-ack", "occupied-child", "missing-grant")) {
      s"E4 apply the actual selected plan through public advance and recover $episode across physical reopen" in {
        Given("a genuine known-work selected APPLY source, actual SQLite storage and a runtime-fixed document capability")
        val source = SplitPhaseExplicitPartitionFixture.base(Vector(360, 360))
        val command = f.command.copy(input = f.command.input.copy(invocationMode = "APPLY",
          proposalReference = Some(source.initial.shape.records.proposal.reference.identity)))
        val raw = f.record.copy(source = source.source, proposal = Some(source.initial.shape.records.proposal), command = command,
          selection = f.selectionFor(command))
        val start = take(SplitPhaseTypedStartPreparation.prepareC(raw, raw.source, raw.proposal, raw.selection, f.catalog, f.configuration))
        val facts = SplitPhaseExplicitPartitionContext.Facts(source.initial.shape.records, source.initial.records, source.records,
          SplitPhasePlanningCheckpointFixture.knownFacts.planning.copy(source = source.source.reference, authority = source.source.authority,
            objectives = SplitPhaseExplicitObjectiveSelection.Records(None, None, None)))
        val selected = take(SplitPhaseTypedStartPreparation.bindPlanningC(start, take(SplitPhaseExplicitPartitionContext.preparePlanningC(
          f.context.copy(facts = facts), facts, current, f.configuration, f.catalog))))
        val saved = capture(selected)
        take(SplitPhasePlanningSnapshot.dispositionC(saved.record.snapshot)) shouldBe SplitPhasePlanningSnapshot.Disposition.Planned
        val projection = take(SplitPhaseSelectedDocuments.projectC(saved.record.snapshot))
        var actualProjection = Option.empty[SplitPhaseSelectedDocuments.Projection]
        def observedProjection(call: Probe): SplitPhaseSelectedDocuments.Projection = {
          // Read the actual published selection. The private capture's fixed
          // checkpoint ID is not the runtime allocator's candidate identity.
          val targets = take(U.bindTargetsC(call.core.component.get.subsystem.get.configuration, declaration))
          val indexkey = take(U.indexKeyC(saved.before.identity, saved.record.originalContext.reference))
          val index = take(J.decodeIndexC(saved.before.identity, saved.record.originalContext.reference,
            take(call.rowC(targets.publication, indexkey)).get.getString(J.indexStorageField).get))
          val root = take(_root_c(call, saved))
          root.originalStart shouldBe saved.record.originalStart
          val selected = take(SplitPhaseSelectedDocuments.projectC(root.snapshot))
          selected.source shouldBe projection.source
          selected.cutAfterIndices shouldBe projection.cutAfterIndices
          selected.documents.map(d => (d.relativePath, d.phase, d.ownedItems)) shouldBe
            projection.documents.map(d => (d.relativePath, d.phase, d.ownedItems))
          selected
        }
        val work = Files.createDirectories(Path.of("target/sm-workflow/work/native-selected-apply").toAbsolutePath)
        val repository = Files.createTempDirectory(work, "repository-")
        Files.createDirectories(repository.resolve("docs/phase"))
        val phase = repository.resolve(projection.documents.head.relativePath)
        val original = (Vector("# Original Phase", "", "## Scope", "", "| ID | Outcome | Status |", "| --- | --- | --- |") ++
          source.inventory.orderedUnfinishedItems.map(i => s"| ${i.value} | original work | planned |") ++
          Vector("| old-completed | accepted work | completed |", "", "## History", "", "Retain actual user history.", "",
            "## Source boundary", "", "```" + Program.sourceSchema, source.sourceBoundary.asJson.noSpaces, "```", "")).mkString("\n")
        Files.writeString(phase, original)
        val child = repository.resolve(projection.documents.tail.head.relativePath)
        if (episode == "occupied-child") Files.writeString(child, "Independent existing child\n")
        val path = repository.resolve("host.sqlite")
        val wire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved)))
        val capability = take(ProcessCapabilityId.parseC("sm-workflow-selected-documents"))
        val classpath = Files.readAllLines(Path.of("target/cncf.d/runtime-classpath.txt")).asScala.map(_.trim)
          .filter(_.nonEmpty).mkString(java.io.File.pathSeparator)
        val fixed = Vector("-cp", classpath, "org.simplemodeling.textus.smworkflow.runtime.SelectedPhaseDocumentProgram", repository.toString, "repo")
        val definition = take(ProcessProgramDefinition.fromRuntimeC(capability, "sm-workflow-selected-documents",
          Path.of(System.getProperty("java.home"), "bin/java").toString, fixed,
          ProcessArgumentPolicy(fixed, Set.empty, Set(Vector("observe"), Vector("apply"))),
          ProcessExecutionLimits(Some(10000), Some(30000), Some(1000), Some(2097152), Some(131072), Some(16384),
            Some(1), Some(16), Some(1), Some(131072), Some(4194304)), Set.empty))
        val policy = take(ProcessExecutionPolicy.createC(Vector(definition)))
        def admission(grant: Boolean) = take(ProcessExecutionAdmission.createC(policy,
          if (grant) Vector(ProcessExecutionGrant(capability)) else Vector.empty))
        var installed = false
        var invoked = false
        var acknowledgements = 0
        var terminal = Option.empty[String]
        var native = Option.empty[InstanceRecord]
        val acknowledgement = () => {
          acknowledgements += 1
          if ((episode == "effect-ack" && acknowledgements == 1) || (episode == "terminal-ack" && acknowledgements == 2))
            Consequence.stateConflict("Test-only interrupted application acknowledgement") else Consequence.unit
        }
        val firstdriver = new LocalProcessExecutionDriver()
        try withHost(path, publicadvance = true, terminalreadback = acknowledgement,
          processruntime = Some(firstdriver -> admission(episode != "missing-grant"))) { (call, _) =>
          if (!installed) { take(installC(call, saved)); installed = true }
          if (!invoked) {
            invoked = true
            When("the owning public advance observes planning, saves intent and runs only its configured effect")
            val result = _public_advance_c(call, wire, false)
            Then("only actual applied readback may reach a Terminal; conflicts, missing grants and interrupted acknowledgements do not")
            if (episode == "normal") terminal = Some(_scalar(take(result))) else result.isSuccess shouldBe false
            if (episode == "occupied-child") result.toString should include("actual documents conflict with the saved selection")
            native = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core))
            actualProjection = Some(observedProjection(call))
          }
          assertNative(call, native.get)
          if (Set("normal", "effect-ack", "terminal-ack").contains(episode)) {
            Files.readString(phase) should include("Retain actual user history.")
            Files.readString(phase) should include("| old-completed | accepted work | completed |")
            actualProjection.get.documents.tail.foreach(d => Files.readString(repository.resolve(d.relativePath)) shouldBe d.childDocument.get)
            native.get.lifecycle shouldBe (if (episode == "effect-ack") Lifecycle.Active else Lifecycle.Completed)
          } else {
            Files.readString(phase) shouldBe original
            native.get.currentProgression.map(_.value) shouldBe Some(SplitPhaseSelectedDocumentEffect.applyingProgression)
            native.get.lifecycle shouldBe Lifecycle.Active
            if (episode == "occupied-child") Files.readString(child) shouldBe "Independent existing child\n"
            else Files.exists(child) shouldBe false
          }
          Consequence.unit
        } finally firstdriver.close()

        val sourceTime = Files.getLastModifiedTime(phase)
        val retainedvalues = values.filterNot((key, _) => key.startsWith(SplitPhaseCandidateAllocationPolicy.prefix) ||
          key.startsWith(SplitPhasePartitionReplyAllocationPolicy.prefix))
        Given("physical pool/runtime closure followed by a new host, retaining the saved intent/result and no allocation policy")
        val laterdriver = new LocalProcessExecutionDriver()
        try withHost(path, retainedvalues, publicadvance = true, processruntime = Some(laterdriver -> admission(true))) { (call, _) =>
          When("Service and CLI advance observe the saved application state")
          val results = Vector(false, true).map(cli => _public_advance_c(call, wire, cli))
          Then("completed and recoverable effects return the same native completion, while the actual occupied child remains an explicit conflict")
          observedProjection(call).json shouldBe actualProjection.get.json
          if (episode == "occupied-child") {
            results.foreach(_.isSuccess shouldBe false)
            results.foreach(_.toString should include("actual documents conflict with the saved selection"))
            assertNative(call, native.get)
            Files.readString(phase) shouldBe original
            Files.readString(child) shouldBe "Independent existing child\n"
          } else {
            val wires = results.map(r => _scalar(take(r)))
            terminal.foreach(expected => wires.foreach(_ shouldBe expected))
            terminal = Some(wires.head)
            wires.distinct.size shouldBe 1
            val output = take(org.goldenport.cncf.workflow.WorkflowTerminalJsonV1.decodeC(wires.head,
              SplitPhaseManagedSelectedApply.payloadCodec)).current.asInstanceOf[WorkflowContinuation.Terminal[Json]].result.value
            output.hcursor.get[String]("mode").toOption shouldBe Some("APPLY")
            output.hcursor.downField("effect").get[String]("status").toOption shouldBe Some("applied")
            output.hcursor.downField("documentProjection").focus shouldBe Some(actualProjection.get.json)
            val completed = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            completed.lifecycle shouldBe Lifecycle.Completed
            completed.revision shouldBe saved.before.revision.next.next.next.next
            if (episode != "missing-grant") Files.getLastModifiedTime(phase) shouldBe sourceTime
            native = Some(completed)
            actualProjection.get.documents.tail.foreach(d => Files.readString(repository.resolve(d.relativePath)) shouldBe d.childDocument.get)
          }
          Consequence.unit
        } finally laterdriver.close()
      }
    }
  }

  "Current-state observation before public advance" should {
    "consume a compatible real catalog with changed diagnostic locations across native execution and physical reopen" in {
      Given("official generated artifacts with only diagnostic line locations changed and privately admitted by the actual catalog owner")
      val catalog = _relocated_catalog
      catalog.workflow.workflow.rootLine should not be f.catalog.workflow.workflow.rootLine
      val saved = capture()
      val path = Files.createTempDirectory("smwf-relocated-catalog-").resolve("host.sqlite")
      var installed = false
      for (mode <- Vector("initial", "physical-reopen")) {
        withHost(path, values) { (call, _) =>
          Given("the real original rows and selected native predecessor for " + mode)
          if (!installed) { take(installC(call, saved)); installed = true }
          val port = take(Managed.bindC(_earlier_host, declaration, catalog))
          When("the owning call executes or observes the genuine prepared transition through that admitted catalog")
          val executed = take(port.executePreparedForCallC(saved, call.core))
          val loaded = take(port.loadMechanicalForCallC(query(saved), call.core)).get
          Then("the saved calculation and actual native progression survive without reproof of source locations or the whole declaration")
          executed.record shouldBe saved.record
          loaded.record shouldBe saved.record
          executed.current.revision shouldBe saved.next.revision
          executed.current.history shouldBe saved.next.history
          loaded.current.history shouldBe saved.next.history
          assertNative(call, saved.next)
          assertRows(call, saved)
          Consequence.unit
        }
      }
    }

    "read active, suspended and completed native history across SQLite reopen without allocating a checkpoint" in {
      Given("privately seeded original Start rows and native state, but no issued work or checkpoint")
      val saved = capture()
      val path = Files.createTempDirectory("smwf-current-state-").resolve("host.sqlite")
      val ap = SplitPhaseCandidateAllocationPolicy.prefix
      val declared = values.removed(ap + "policy-identity").removed(ap + "policy-revision").removed(ap + "namespace")
      var installed = false
      var expected = saved.before
      var observationchecks = 0
      for (mode <- Vector("active", "suspended", "completed", "reopened")) {
        var changed = false
        withHost(path, declared, rowCheck = () => { observationchecks += 1; Consequence.stateConflict("no checkpoint observation expected") }) { (call, port) =>
          Given("original rows and the selected privately seeded native history for " + mode)
          if (!installed) { take(installC(call, saved)); installed = true }
          if (!changed && (mode == "suspended" || mode == "completed")) {
            val revision = expected.revision.next
            val suspension = if (mode == "suspended") Some(SuspensionBoundary(
              ContinuationIdentity("fixture-current-suspension"), revision,
              ContextSnapshotReference("fixture-context"), CompletionReference("fixture-completion"),
              EvidenceReference("fixture-evidence"), expected.correlation)) else None
            val entry = expected.history.last.copy(sequence = HistorySequence(expected.history.last.sequence.value + 1),
              revision = revision, lifecycle = if (mode == "completed") Lifecycle.Completed else Lifecycle.Active,
              currentProgression = if (mode == "completed") None else expected.currentProgression,
              causation = CausationReference("fixture-" + mode), suspension = suspension)
            expected = take(instances.appendForCallC(f.configuration, expected.identity, expected.revision, entry, call.core))
            changed = true
          }
          val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
          When("the actual Service or CLI call observes the current native state")
          val observed = take(port.loadCurrentForCallC(query(saved), call.core))
          Then("the complete original data and latest native state are returned without writes or a grant to execute")
          observed.original shouldBe O.Original(saved.record.originalContext, saved.record.originalStart, saved.record.originalContext.facts)
          observed.current shouldBe expected
          assertNative(call, expected)
          _assert_no_checkpoint(call, saved)
          call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
          observationchecks shouldBe 0
          Consequence.unit
        }
      }
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseManagedPlanningCheckpoint.ObservedCurrent(null, null)")
    }

    "refuse crossed commands and unreadable routing metadata without certifying unused original input copies" in {
      Given("an actual native predecessor with independent original rows")
      val saved = capture()
      val path = Files.createTempDirectory("smwf-current-original-").resolve("host.sqlite")
      var installed = false
      withHost(path) { (call, port) =>
        Given("original rows and the canonical predecessor in the current owning call")
        if (!installed) { take(installC(call, saved)); installed = true }
        val command = query(saved)
        val changedqueries = Vector(command.copy(instanceIdentity = "different-instance"),
          command.copy(context = command.context.copy(identity = "different-context")),
          command.copy(context = command.context.copy(revision = "different-revision")))
        When("a caller requests each crossed instance or context under the original owning command")
        val refusedqueries = changedqueries.map(changed => port.loadCurrentForCallC(changed, call.core).isSuccess)
        Then("each crossed command is refused")
        refusedqueries shouldBe Vector.fill(changedqueries.size)(false)
        originalRows(saved).foreach { case (collection, identity, row) =>
          Given("one independently malformed original row")
          take(call.writeC(collection, identity, Record.data(row.fields.head.key -> "malformed-original"), true))
          When("current-state routing consumes only its raw Start and Context metadata")
          val result = port.loadCurrentForCallC(command, call.core)
          Then("unreadable selected routing metadata is refused while unused independent rows do not gate observation")
          if (collection == this.collection("starts") || collection == this.collection("contexts")) result.isSuccess shouldBe false
          else take(result).current shouldBe saved.before
          take(call.writeC(collection, identity, row, true))
        }
        Given("a changed source value that retains the original reference")
        val source = saved.record.originalStart.source
        val changedsource = source.copy(authority = source.authority.copy(identity = "different-source-authority"))
        take(call.writeC(target(F.Purpose.Source), source.reference.identity,
          Record.data(F.storageField -> take(F.encodeSourceC(changedsource))), true))
        When("current-state routing observes native state without preparing new source input")
        val changedresult = port.loadCurrentForCallC(command, call.core)
        Then("the unused independent source does not become a no-change gate or a grant to execute")
        take(changedresult).current shouldBe saved.before
        Given("the exact original source restored for subsequent observation")
        take(call.writeC(target(F.Purpose.Source), source.reference.identity,
          Record.data(F.storageField -> take(F.encodeSourceC(source))), true))
        When("the restored original state is observed again")
        val restored = take(port.loadCurrentForCallC(command, call.core))
        Then("restoration yields the original state and there is still no checkpoint or native append")
        restored.current shouldBe saved.before
        assertNative(call, saved.before)
        _assert_no_checkpoint(call, saved)
        Given("an unchanged original human selection and a canonical Start with only a different idempotency key")
        val start = saved.record.originalStart
        val changedstart = start.copy(command = start.command.copy(idempotencyKey = "different-retained-key"))
        take(call.writeC(collection("starts"), start.reference.identity,
          Record.data(TJ.storageField -> take(TJ.encodeC(changedstart))), true))
        When("retained-output observation runs before any mapping or work exists")
        val crossedretained = port.loadRetainedForCallC(command, call.core)
        Then("an unused stored idempotency key does not certify cached absence or create fresh execution authority")
        take(crossedretained) shouldBe None
        assertNative(call, saved.before)
        _assert_no_checkpoint(call, saved)
        Given("the exact original Start row restored")
        take(call.writeC(collection("starts"), start.reference.identity,
          Record.data(TJ.storageField -> take(TJ.encodeC(start))), true))
        When("retained-output observation sees the exact original unsuspended pre-issue state")
        val emptyretained = take(port.loadRetainedForCallC(command, call.core))
        Then("no retained output is returned and no plan or checkpoint is allocated")
        emptyretained shouldBe None
        assertNative(call, saved.before)
        _assert_no_checkpoint(call, saved)
        Consequence.unit
      }
    }

    "require the exact original authenticated advance actor and an existing native instance" in {
      Given("real native calls with wrong operation, different actor, forged authentication or absent instance")
      val saved = capture()
      for (mode <- Vector("wrong-operation", "different-actor", "forged-actor", "missing-instance")) {
        val path = Files.createTempDirectory("smwf-current-actor-").resolve("host.sqlite")
        var installed = false
        withHost(path, operationName = if (mode == "wrong-operation") "other" else K.advanceOperation.operation,
          participant = if (mode == "different-actor") "foreign" else "participant") { (call, port) =>
          Given("the actual owning call and privately seeded original data for " + mode)
          if (!installed) {
            if (mode == "missing-instance") originalRows(saved).foreach { case (c, id, row) => take(call.writeC(c, id, row)) }
            else take(installC(call, saved))
            installed = true
          }
          val core = if (mode == "forged-actor") call.core.copy(executionContext =
            take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.ControlledTest, call.executionContext,
              Map("principal.id" -> "participant", "privilege" -> "user", "cncf.security.authenticated" -> "true"))).executionContext)
            else call.core
          When("current-state observation is requested before any execution")
          val result = port.loadCurrentForCallC(query(saved), core)
          val pendingresult = port.loadPendingPartitionForCallC(query(saved), core)
          val retainedresult = port.loadRetainedForCallC(query(saved), core)
          Then("no observed current value or new native state is admitted")
          result.isSuccess shouldBe false
          pendingresult.isSuccess shouldBe false
          retainedresult.isSuccess shouldBe false
          take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)) shouldBe
            (if (mode == "missing-instance") None else Some(saved.before))
          _assert_no_checkpoint(call, saved)
          Consequence.unit
        }
      }
    }
  }

  private def _retained_reasoning_spec(example: String, rule: String) =
    afterWord(s"in spec:split-phase-retained-reasoning, example:$example, rules:$rule, phase:2")

  "Retained first-frontier reasoning" should {
    import SplitPhaseExplicitPartitionFixture.{base, enumerate, proof}
    import SplitPhaseExplicitObjectiveSelection as Objectives
    import SplitPhaseExplicitReasoningSemantic as Reasoning
    import SplitPhaseRetainedReasoningSource as Source
    import SplitPhasePlanningSnapshot as Snapshot
    import SplitPhaseExplicitPartitionComputation as Enumeration
    import SplitPhasePartitionCompletionCheckpointPreparation as Completion
    import SplitPhasePartitionCompletionCheckpointJsonV1 as CompletionJson
    import org.goldenport.cncf.workflow.{WorkflowWorkOrderJsonV1, WorkflowResultJsonV1, WorkflowComponentStorage,
      WorkflowCompletionRecordJsonV1, StateMachineOperationResult}
    import SplitPhaseExplicitPartitionSemantic as Semantic
    import SplitPhaseExplicitPartitionSemanticJson as SemanticJson

    "E1 Native cached reasoning frontier" should _retained_reasoning_spec("E1", "R1,R3,R4,R5,R6,R7") {
      for (partitionroot <- Vector(false, true);
          issuecase <- Vector("projection-only", "managed-issue", "interrupted-readback", "public-issue")) {
        val rules = if (issuecase == "projection-only") "R1,R3" else "R1,R3,R4,R5,R6,R7"
        s"E1/$rules preserve cached reasoning data for partition origin $partitionroot through $issuecase without allocation policies" in {
          Given(s"Spec: docs/spec/split-phase-retained-reasoning.md; Rules: $rules; Example: E1; a duration-tied full frontier with known expensive/settled demands and one unknown middle item")
          val source = base(Vector(260, 180, 260))
          val boundary = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(0)).get,
            "retained-reasoning-template")
          val objectives = Objectives.Records(
            Some(Objectives.CostPolicy(Objectives.CostPolicyReference("retained-reasoning-cost", "1"),
              source.source.reference, source.source.authority, Objectives.ProfileIdentity("expensive"),
              Objectives.ProfileIdentity("lower"), 2, 1, 1)),
            Some(Objectives.DemandRecord(Objectives.DemandReference("retained-reasoning-demand", "1"),
              source.source.reference, source.source.authority,
              source.inventory.orderedUnfinishedItems.zipWithIndex.map { (id, index) =>
                Objectives.ItemDemand(id, index match {
                  case 0 => Objectives.Demand.ExpensiveReasoning
                  case 1 => Objectives.Demand.Unresolved("unknown middle reasoning kernel")
                  case _ => Objectives.Demand.SettledExecution
                })
              })),
            Some(Objectives.OverheadPolicy(Objectives.OverheadPolicyReference("retained-reasoning-overhead", "1"),
              source.source.reference, source.source.authority, 1, 0, 0, 0, 0)))
          val saved = forBoundary(source, boundary, objectives)
          val workroot = Files.createDirectories(Path.of("target", "sm-workflow", "work", "retained-reasoning-spec"))
          // Retain diagnostic databases until project clean; withHost closes each pool before reopen.
          val path = Files.createTempDirectory(workroot, "smwf-retained-reasoning-").resolve("host.sqlite")
          val declared = values ++ SplitPhasePartitionReplyFixture.values
          val wire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved)))
          var expected = Option.empty[K.Record]
          var expectednative = Option.empty[InstanceRecord]

          if (partitionroot) {
            val facts = saved.record.originalContext.facts
            val estimates = facts.partition.estimates
            val unresolved = facts.copy(partition = facts.partition.copy(estimates = estimates.copy(items =
              estimates.items.updated(1, estimates.items(1).copy(duration = Enumeration.Duration.Unresolved("novel estimate"))))))
            val context = saved.record.originalContext.copy(facts = unresolved)
            withHost(path, declared, publicadvance = true) { (call, _) =>
              if (expected.isEmpty) {
                Given("only private original Start rows, with one estimate requiring genuine partition work")
                _original_rows(O.Original(context, saved.record.originalStart, unresolved), saved.record.originalStart).foreach { case (c, id, row) =>
                  take(call.writeC(c, id, row))
                }
                take(instances.createForCallC(f.configuration, f.initial, call.core))
                take(instances.appendForCallC(f.configuration, saved.before.identity, initialRevision, saved.before.history.head, call.core))
                When("real public advance issues the missing estimate and public completion receives its assigned reply")
                val issued = take(WorkflowWorkOrderJsonV1.decodeC(_scalar(take(_public_advance_c(call, wire, false))),
                  SemanticJson.requestCodec))
                val result = _semantic_result(issued, Semantic.Decision.Enriched)
                val resultwire = take(WorkflowResultJsonV1.encodeC(result, SemanticJson.resultCodec))
                take(_public_operation_c(call, SplitPhaseExplicitPartitionContext.completionOperation.operation, resultwire, true))
                val row = take(call.rowC(collection("publications"), take(Completion.lineageKeyC(result.continuationId)))).get
                val complete = take(CompletionJson.decodeC(result.continuationId, row.getString(CompletionJson.storageField).get,
                  Vector(f.catalog.definition)))
                expected = complete.checkpoint
                expectednative = Some(complete.next)
                Then("the saved reasoning frontier has the actual partition-result origin and enriched estimate")
                expected.get.origin shouldBe K.Origin.PartitionCompletion(result.continuationId,
                  Semantic.ResultReference(result.resultReference.identity, result.resultReference.revision))
                expected.get.snapshot.selection.partition.estimates.items(1).duration shouldBe Enumeration.Duration.Known(180)
                take(Snapshot.dispositionC(expected.get.snapshot)) shouldBe Snapshot.Disposition.ReasoningRequired
              }
              Consequence.unit
            }
          } else {
            withHost(path, declared) { (call, port) =>
              Given("original rows at the typed Start position and no checkpoint")
              if (expected.isEmpty) take(installC(call, saved))
              When("the genuine managed mechanical publication commits its complete saved calculation")
              val observed = take(port.executePreparedForCallC(saved, call.core))
              expected = Some(observed.record)
              expectednative = Some(observed.current)
              Then("the actual native receipt retains the exact reasoning-required frontier")
              observed.record shouldBe saved.record
              observed.publicationPostimage shouldBe saved.next
              take(Snapshot.dispositionC(observed.record.snapshot)) shouldBe Snapshot.Disposition.ReasoningRequired
              Consequence.unit
            }
          }

          val retainedvalues = declared.filterNot((key, _) => key.startsWith(SplitPhaseCandidateAllocationPolicy.prefix) ||
            key.startsWith(SplitPhasePartitionReplyAllocationPolicy.prefix))
          for (mode <- Vector("first physical reopen", "second physical reopen")) {
            Given("the closed host's real SQLite rows without either allocation policy for " + mode)
            withHost(path, retainedvalues) { (call, port) =>
              val binding = call.executionContext.dataStoreSpace.captureBinding()
              When("the owning advance observes the exact native origin, restores it twice and projects a pure reasoning issue twice")
              val native = if (partitionroot) take(Source.partitionCompletionC(
                take(port.loadPartitionCompletionForCallC(query(saved), call.core)).get))
                else take(Source.mechanicalC(take(port.loadMechanicalForCallC(query(saved), call.core)).get))
              val restored = Vector.fill(2)(take(W.restoreReasoningC(native)))
              val prepared = restored.map(value => take(Reasoning.prepareC(native.checkpoint.originalContext.reference,
                value.pending, SplitPhaseExplicitPartitionSemantic.ResultReference("first-reasoning-fixture-result", "1"))))
              val projected = Vector.fill(2)(take(SplitPhaseTypedReasoningWorkIssue.prepareC(native)))
              Then("all cached stages, original facts, candidate proofs and unknown-only closures survive without any new stage or append")
              native.checkpoint shouldBe expected.get
              native.current shouldBe expectednative.get
              native.publicationPostimage shouldBe expectednative.get
              restored.foreach { value =>
                value.source should be theSameInstanceAs native
                value.mechanicalStages shouldBe Vector.empty
                take(Snapshot.projectC(value.pending)).record shouldBe expected.get.snapshot
                val enumerated = value.pending.trace.partition.asInstanceOf[Enumeration.Enumerated]
                val cached = expected.get.snapshot.partition.asInstanceOf[Snapshot.PartitionView.Enumerated]
                enumerated.evaluatedCount shouldBe cached.evaluatedCount
                enumerated.rejectedByCeiling shouldBe cached.rejectedByCeiling
                enumerated.candidates.map(_.cutAfterIndices) shouldBe cached.candidates.map(_.key.cutAfterIndices)
                value.pending.selection.data.objectives shouldBe objectives
              }
              prepared.foreach { value =>
                value.request.source shouldBe source.source.reference
                value.request.authority shouldBe source.source.authority
                value.request.items.map(_.identity) shouldBe Vector(source.inventory.orderedUnfinishedItems(1))
                value.request.items.head.closure shouldBe source.sourceBoundary.items(1)
              }
              projected.map(_.work).distinct.size shouldBe 1
              projected.foreach { value =>
                val order = value.work.current.asInstanceOf[WorkflowContinuation.WorkOrder[Reasoning.Request]].request
                value.source should be theSameInstanceAs native
                value.intent.current shouldBe expectednative.get
                value.intent.expectedRevision shouldBe expectednative.get.revision
                value.next.history.dropRight(1) shouldBe expectednative.get.history
                value.next.revision shouldBe expectednative.get.revision.next
                value.next.suspension.get.continuationIdentity.value shouldBe order.continuationId.value
                value.mechanicalStages shouldBe Vector.empty
                value.work.handle shouldBe native.checkpoint.originalContext.owner.handle
                value.prepared.request.items shouldBe prepared.head.request.items
                take(Snapshot.projectC(value.prepared.pending)).record shouldBe native.snapshot
                value.prepared.request.source shouldBe source.source.reference
                value.prepared.request.authority shouldBe source.source.authority
                order.input.get.value shouldBe value.prepared.request
                order.operation shouldBe take(SplitPhaseDefinitionBinding.reasoningSemanticOperationC).operation
                order.completionOperation shouldBe SplitPhaseTypedReasoningWorkIssue.completionOperation
                value.prepared.request.result shouldBe Semantic.ResultReference(order.continuationId.value + ":result",
                  value.next.revision.value.toString)
              }
              assertNative(call, expectednative.get)
              call.executionContext.dataStoreSpace.captureBinding() shouldBe binding
              Consequence.unit
            }
          }
          if (issuecase == "projection-only") {
            Given("the same actual reasoning frontier followed by an independent later native append")
            var laternative = Option.empty[InstanceRecord]
            withHost(path, retainedvalues) { (call, port) =>
            val publication = expectednative.get
            val entry = HistoryEntry(HistorySequence(publication.history.last.sequence.value + 1L), publication.revision.next,
              publication.lifecycle, publication.currentProgression, publication.correlation,
              CausationReference("private-later-reasoning-fixture"), publication.derivedCompositeOccurrence,
              publication.committedPredecessor, None)
            val later = laternative match {
              case Some(actual) => actual
              case None =>
                val actual = take(instances.appendForCallC(f.configuration, publication.identity, publication.revision, entry, call.core))
                laternative = Some(actual)
                actual
            }
            When("owning observation projects the old saved reasoning need and the canonical native executor attempts that stale suspension")
            val source = if (partitionroot) take(Source.partitionCompletionC(
              take(port.loadPartitionCompletionForCallC(query(saved), call.core)).get))
              else take(Source.mechanicalC(take(port.loadMechanicalForCallC(query(saved), call.core)).get))
            val projected = take(SplitPhaseTypedReasoningWorkIssue.prepareC(source))
            val owner = call.core.component.get.subsystem.get
            val native = take(take(WorkflowComponentStorage.bindC(ComponentCreate(owner, ComponentOrigin.Builtin),
              SmWorkflowComponent.componentId, declaration.configuration, Vector(f.catalog.definition),
              declaration.instances, declaration.continuations)).bindIssuedC(
                SplitPhaseTypedReasoningWorkIssue.completionOperation, SplitPhaseExplicitReasoningSemanticJson.requestCodec))
            val refused = native.suspendC(projected.intent, projected.work)(using call.executionContext)
            val issued = take(native.loadC(take(SplitPhaseTypedReasoningWorkIssue.identityC(source)))(using call.executionContext))
            Then("the projection still refers to the original publication, but it grants neither a stale append nor a published WorkOrder")
            source.publicationPostimage shouldBe publication
            source.current shouldBe later
            projected.intent.current shouldBe publication
            projected.next.history.dropRight(1) shouldBe publication.history
            take(Snapshot.projectC(projected.prepared.pending)).record shouldBe expected.get.snapshot
            refused.isSuccess shouldBe false
            issued shouldBe None
            assertNative(call, later)
              Consequence.unit
            }
          } else {
            import SplitPhaseManagedReasoningIssue as Issue
            import SplitPhaseTypedReasoningWorkIssue as Projection
            import SplitPhaseExplicitReasoningSemanticJson as ReasoningJson
            var issuedwork = Option.empty[WorkflowInteraction[Reasoning.Request, Nothing]]
            var issuednext = Option.empty[InstanceRecord]
            var selected = Option.empty[Source.Source]
            var issuer = Option.empty[Issue.Port]
            var expiredcore = Option.empty[ActionCall.Core]
            var observations = 0
            val expectedobservations = if (issuecase == "public-issue") 0 else 1
            if (issuecase == "public-issue") {
              Given("the real first-frontier publication with neither allocator, before any reasoning suspension")
              withHost(path, retainedvalues, publicadvance = true) { (call, _) =>
                When("the generated public advance owns first reasoning issue and then reads that same saved task")
                val issued = take(WorkflowWorkOrderJsonV1.decodeC(
                  _scalar(take(_public_advance_c(call, wire, cli = true))), ReasoningJson.requestCodec))
                val repeated = take(WorkflowWorkOrderJsonV1.decodeC(
                  _scalar(take(_public_advance_c(call, wire, cli = false))), ReasoningJson.requestCodec))
                Then("both normal transports return the identical unknown-only task, with one native append")
                repeated shouldBe issued
                val current = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
                current.history.dropRight(1) shouldBe expectednative.get.history
                current.revision shouldBe expectednative.get.revision.next
                if (issuedwork.isEmpty) { issuedwork = Some(issued); issuednext = Some(current) }
                issued shouldBe issuedwork.get
                current shouldBe issuednext.get
                Consequence.unit
              }
            }
            for (mode <- Vector("first issue", "retained repeat", "physical reopen")) {
              Given("the same native frontier and actual managed host without either allocation policy for " + mode)
              withHost(path, retainedvalues) { (call, port) =>
                val binding = call.executionContext.dataStoreSpace.captureBinding()
                val nativesource = if (partitionroot) take(Source.partitionCompletionC(
                  take(port.loadPartitionCompletionForCallC(query(saved), call.core)).get))
                  else take(Source.mechanicalC(take(port.loadMechanicalForCallC(query(saved), call.core)).get))
                val projected = take(Projection.prepareC(nativesource))
                if (selected.isEmpty) selected = Some(nativesource)
                if (issuedwork.isEmpty) {
                  issuedwork = Some(projected.work)
                  issuednext = Some(projected.next)
                }
                if (issuer.isEmpty) {
                  issuer = Some(take(Issue.bindWithReadbackObservationC(
                    ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
                    declaration, f.catalog, () => {
                      observations += 1
                      if (issuecase == "interrupted-readback") Consequence.stateConflict("private postcommit readback interruption")
                      else Consequence.unit
                    })))
                }
                if (!partitionroot && issuecase != "public-issue" && mode == "first issue" && observations == 0) {
                  Given("the old genuine selected source and a changed unused Start idempotency key in the actual private receipt")
                  val reference = nativesource.checkpoint.origin.asInstanceOf[K.Origin.Mechanical].checkpoint
                  val key = take(M.keyC(reference))
                  val row = take(call.rowC(declaration.continuations, key)).get
                  val prefix = "cncf.workflow-json.v1:"
                  val json = parser.parse(row.getString("mechanicalPayload").get.substring(prefix.length)).toOption.get
                  val start = nativesource.checkpoint.originalStart
                  val changedstart = start.copy(command = start.command.copy(idempotencyKey = "unused-reasoning-start-key"))
                  val changed = json.hcursor.downField("payload").downField("value").downField("payload")
                    .downField("originalStart").withFocus(_ => Json.fromString(take(TJ.encodeC(changedstart)))).top.get
                  take(call.writeC(declaration.continuations, key,
                    Record.data("mechanicalPayload" -> (prefix + changed.noSpaces)), update = true))
                  When("the owning reader observes the actual native calculation rather than certifying the earlier copy")
                  val actualsource = take(Source.mechanicalC(take(port.loadMechanicalForCallC(query(saved), call.core)).get))
                  Then("only unused metadata differs; the actual frontier, postimage and assigned reasoning work are unchanged")
                  actualsource.checkpoint should not be selected.get.checkpoint
                  actualsource.checkpoint.originalStart.command.idempotencyKey shouldBe "unused-reasoning-start-key"
                  actualsource.snapshot shouldBe nativesource.snapshot
                  actualsource.publicationPostimage shouldBe nativesource.publicationPostimage
                  take(Projection.prepareC(actualsource)).work shouldBe projected.work
                  assertNative(call, expectednative.get)
                }
                val firstinterruption = issuecase == "interrupted-readback" && observations == 0
                When("the owning issuer consumes its reobserved native source without comparing the old copy, publishes only at exact absence, or reads the saved task")
                // The old witness has its old current. The port must reobserve
                // native current, not copy the witness or capture a physical lease.
                val response = issuer.get.issueForCallC(selected.get, call.core)
                val native = take(take(WorkflowComponentStorage.bindC(
                  ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
                  SmWorkflowComponent.componentId, declaration.configuration, Vector(f.catalog.definition),
                  declaration.instances, declaration.continuations)).bindIssuedC(Projection.completionOperation,
                    ReasoningJson.requestCodec))
                val actualwork = take(native.loadC(take(Projection.identityC(nativesource)))(using call.executionContext)).get
                val actualcurrent = take(instances.loadForCallC(f.configuration, nativesource.current.identity, call.core)).get
                Then("one genuine atomic issue and its complete task are retained, even when the first postcommit response was unavailable")
                if (firstinterruption) response.isSuccess shouldBe false
                else take(response) shouldBe actualwork
                observations shouldBe expectedobservations
                actualwork shouldBe issuedwork.get
                actualcurrent shouldBe issuednext.get
                actualcurrent.history.dropRight(1) shouldBe expectednative.get.history
                actualcurrent.revision shouldBe expectednative.get.revision.next
                nativesource.publicationPostimage shouldBe expectednative.get
                selected.get.current shouldBe (if (issuecase == "public-issue") issuednext.get else expectednative.get)
                projected.work shouldBe actualwork
                val order = actualwork.current.asInstanceOf[WorkflowContinuation.WorkOrder[Reasoning.Request]].request
                val request = order.input.get.value
                request shouldBe projected.prepared.request
                request.items.map(_.identity) shouldBe Vector(source.inventory.orderedUnfinishedItems(1))
                request.items.head.closure shouldBe source.sourceBoundary.items(1)
                request.source shouldBe source.source.reference
                request.authority shouldBe source.source.authority
                request.result shouldBe Semantic.ResultReference(order.continuationId.value + ":result", actualcurrent.revision.value.toString)
                take(Snapshot.projectC(projected.prepared.pending)).record shouldBe expected.get.snapshot
                projected.mechanicalStages shouldBe Vector.empty
                actualwork.handle shouldBe expected.get.originalContext.owner.handle
                order.completionOperation shouldBe Projection.completionOperation
                take(WorkflowWorkOrderJsonV1.decodeC(take(ReasoningJson.encodeWorkOrderC(actualwork)),
                  ReasoningJson.requestCodec)) shouldBe actualwork
                Given("R5: the actual native issued reasoning work and suspension, with both allocators removed")
                val pending = take(SplitPhaseTypedReasoningCompletion.pendingC(nativesource, actualcurrent, actualwork))
                val evidence = Semantic.EvidenceReference("native-reasoning-result-evidence", "1")
                def reasoningResult(decision: Reasoning.Decision): ContinuationResult[Reasoning.Judgment] = {
                  val judgment = Reasoning.Judgment(decision, "unknown-only-classification",
                    if (decision == Reasoning.Decision.Enriched) request.items.map(item =>
                      Reasoning.Assignment(item.identity, Reasoning.Classification.SettledExecution,
                        "this bounded implementation follows settled architecture")) else Vector.empty,
                    Vector(evidence))
                  ContinuationResult(actualwork.handle, order.runId, order.continuationId, order.expectedRevision,
                    order.context.snapshot, TypedValue(ReasoningJson.resultCodec.typeIdentity, judgment),
                    ContextReference(request.result.identity, request.result.revision), Vector.empty,
                    ExecutionEvidence(Vector(ContextReference(evidence.identity, evidence.revision)), Some("participant")))
                }
                When("Workflow admits the unknown-only result and prepares the successor from the cached whole frontier")
                val result = reasoningResult(Reasoning.Decision.Enriched)
                val plan = take(SplitPhaseTypedReasoningCompletion.admitC(pending, result))
                val admitted = plan.semantic.asInstanceOf[Reasoning.Admitted]
                val previousdemands = pending.prepared.pending.selection.data.objectives.demands.get
                val demands = admitted.resolved.objectives.demands.get
                Then("known classifications and original authority are retained without another allocation, projection or native append")
                demands.items(0) shouldBe previousdemands.items(0)
                demands.items(2) shouldBe previousdemands.items(2)
                demands.items(1).demand shouldBe Objectives.Demand.SettledExecution
                demands.source shouldBe previousdemands.source
                demands.authority shouldBe previousdemands.authority
                admitted.resolved.reference shouldBe W.DataReference(request.result.identity + ":planning", request.result.revision)
                demands.reference shouldBe Objectives.DemandReference(request.result.identity + ":demands", request.result.revision)
                plan.downstream shouldBe Some(take(Snapshot.projectC(plan.mechanical.get.outcome)).record)
                plan.downstream.get.partition shouldBe nativesource.snapshot.partition
                plan.downstream.get.projection shouldBe nativesource.snapshot.projection
                plan.downstream.get.admission shouldBe nativesource.snapshot.admission
                plan.next.history.dropRight(1) shouldBe actualcurrent.history
                plan.next.revision shouldBe actualcurrent.revision.next
                plan.next.suspension shouldBe None
                plan.closing shouldBe StateMachineOperationResult(Projection.closingType, result.resultReference)
                assertNative(call, actualcurrent)
                When("Workflow prepares a genuine DecisionNeeded result instead of partially classifying known work")
                val decision = take(SplitPhaseTypedReasoningCompletion.admitC(pending,
                  reasoningResult(Reasoning.Decision.DecisionNeeded)))
                Then("it keeps the full original need and prepares no partial planning successor or fake Terminal")
                decision.semantic shouldBe a[Reasoning.DecisionNeeded]
                decision.mechanical shouldBe None
                decision.downstream shouldBe None
                decision.next.suspension shouldBe None
                assertNative(call, actualcurrent)
                When("a reply crosses its assignment, evidence or known-item boundary")
                val crossed = Vector(
                  result.copy(resultReference = ContextReference("foreign-reasoning-result", "1")),
                  result.copy(result = result.result.copy(value = result.result.value.copy(assignments = Vector.empty))),
                  result.copy(result = result.result.copy(value = result.result.value.copy(assignments =
                    result.result.value.assignments :+ Reasoning.Assignment(source.inventory.orderedUnfinishedItems.head,
                      Reasoning.Classification.SettledExecution, "attempt to rewrite known work")))),
                  result.copy(evidence = result.evidence.copy(references = Vector(ContextReference("unrelated-evidence", "1"))))
                )
                Then("the semantic result consumer refuses a crossed reply or a rewrite of known work without a native append")
                crossed.foreach(reply => SplitPhaseTypedReasoningCompletion.admitC(pending, reply).isSuccess shouldBe false)
                assertNative(call, actualcurrent)
                When("the same source is requested again inside this actual owning call")
                val repeated = take(issuer.get.issueForCallC(selected.get, call.core))
                Then("it returns the identical saved work without repeating commit or interrupted observation")
                repeated shouldBe actualwork
                observations shouldBe expectedobservations
                assertNative(call, actualcurrent)
                call.executionContext.dataStoreSpace.captureBinding() shouldBe binding
                expiredcore = Some(call.core)
                Consequence.unit
              }
            }
            Given("the saved genuine task but a foreign participant or unrelated Operation")
            for ((operation, participant) <- Vector(K.advanceOperation.operation -> "foreign", "otherAdvance" -> "participant")) {
              withHost(path, retainedvalues, operationName = operation, participant = participant) { (call, _) =>
                val binding = call.executionContext.dataStoreSpace.captureBinding()
                When("that foreign actor or purpose invokes the metadata-only retained issuer")
                val rejected = issuer.get.issueForCallC(selected.get, call.core)
                Then("the owning-call admission refuses without changing the saved native suspension")
                rejected.isSuccess shouldBe false
                observations shouldBe expectedobservations
                assertNative(call, issuednext.get)
                call.executionContext.dataStoreSpace.captureBinding() shouldBe binding
                Consequence.unit
              }
            }
            Given("the formerly owning core after its managed dispatch and host have closed")
            When("that expired core requests the retained issue")
            val expired = issuer.get.issueForCallC(selected.get, expiredcore.get)
            Then("correct-looking metadata grants no expired lease or issue authority")
            expired.isSuccess shouldBe false
            observations shouldBe expectedobservations
            Given("R6: the pending native reasoning suspension after closing and reopening the public host")
            withHost(path, retainedvalues, publicadvance = true) { (call, _) =>
              val identity = issuedwork.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[Reasoning.Request]].request.continuationId
              val order = issuedwork.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[Reasoning.Request]]
              val saveddiagnostics = issuedwork.get.copy(current = order.copy(
                presentation = order.presentation.copy(summary = Some("saved reasoning explanation")),
                request = order.request.copy(context = order.request.context.copy(
                  summary = "saved reasoning context", references = Vector(ContextReference("saved-diagnostic", "old")),
                  snapshot = order.request.context.snapshot.copy(workspaceRevision = Some("old-workspace"),
                    evidenceRevision = Some("old-evidence"))))))
              val diagnosticwire = take(WorkflowWorkOrderJsonV1.encodeC(saveddiagnostics, ReasoningJson.requestCodec))
              Given("the actual native task with changed presentation and non-scope Context diagnostics")
              take(call.writeC(declaration.continuations, identity.value,
                Record.data("issuedWorkPayload" -> ("cncf.workflow-json.v1:" + diagnosticwire)), update = true))
              When("real public advance reads that saved task without reconstructing its original reasoning frontier")
              val diagnosticpending = take(WorkflowWorkOrderJsonV1.decodeC(
                _scalar(take(_public_advance_c(call, wire, cli = false))), ReasoningJson.requestCodec))
              Then("the saved diagnostics are returned without an issue-copy certificate or a native append")
              diagnosticpending shouldBe saveddiagnostics
              assertNative(call, issuednext.get)
              Given("a malformed payload in that same test-owned native issue row")
              take(call.writeC(declaration.continuations, identity.value,
                Record.data("issuedWorkPayload" -> "malformed-reasoning-task"), update = true))
              When("public advance reaches its owning native issued reader")
              val malformed = _public_advance_c(call, wire, cli = false)
              Then("that reader refuses the unusable task without another issue or history change")
              malformed.isSuccess shouldBe false
              assertNative(call, issuednext.get)
              // Restore the supported saved diagnostics, not an earlier task
              // copy: the following real fresh completion must consume them.
              take(call.writeC(declaration.continuations, identity.value,
                Record.data("issuedWorkPayload" -> ("cncf.workflow-json.v1:" + diagnosticwire)), update = true))
              When("public advance selects the actual reasoning completion contract before scope or partition routing")
              val pending = take(WorkflowWorkOrderJsonV1.decodeC(
                _scalar(take(_public_advance_c(call, wire, cli = true))), ReasoningJson.requestCodec))
              Then("it observes the supported saved task that remains in place for fresh completion without any native append")
              pending shouldBe saveddiagnostics
              assertNative(call, issuednext.get)
              Consequence.unit
            }
            val actualorder = issuedwork.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[Reasoning.Request]].request
            val actualrequest = actualorder.input.get.value
            val answerdecision = if (issuecase == "interrupted-readback") Reasoning.Decision.DecisionNeeded else Reasoning.Decision.Enriched
            val resultevidence = Semantic.EvidenceReference("public-reasoning-completion-evidence", "1")
            val judgment = Reasoning.Judgment(answerdecision, "unknown-only-public-result",
              if (answerdecision == Reasoning.Decision.Enriched) actualrequest.items.map(item =>
                Reasoning.Assignment(item.identity, Reasoning.Classification.SettledExecution, "settled bounded implementation"))
              else Vector.empty, Vector(resultevidence))
            val submitted = ContinuationResult(issuedwork.get.handle, actualorder.runId, actualorder.continuationId,
              actualorder.expectedRevision, actualorder.context.snapshot, TypedValue(ReasoningJson.resultCodec.typeIdentity, judgment),
              ContextReference(actualrequest.result.identity, actualrequest.result.revision), Vector.empty,
              ExecutionEvidence(Vector(ContextReference(resultevidence.identity, resultevidence.revision)), Some("participant")))
            val resultwire = take(WorkflowResultJsonV1.encodeC(submitted, ReasoningJson.resultCodec))
            var completed = Option.empty[InstanceRecord]
            var completedplanning = Option.empty[Snapshot.Record]
            var acknowledgement = Option.empty[String]
            var collisiontested = false
            for (reopen <- Vector("public result", "physical completion reopen")) {
              Given("R6: the real issued reasoning task and its generated public result Operation for " + reopen)
              withHost(path, retainedvalues, reasoningcompletion = true) { (call, _) =>
                val targets = take(U.bindTargetsC(call.core.component.get.subsystem.get.configuration, declaration))
                val successorfield = "splitPhaseReasoningSuccessorPayload"
                val successorkey = "reasoning-successor:" + take(M.keyC(M.Reference(actualorder.continuationId.value,
                  submitted.resultReference.revision)))
                if (!collisiontested) {
                  Given("a real pending assignment with crossed semantic input or native revision and scope")
                  val storage = take(WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get,
                    ComponentOrigin.Builtin), SmWorkflowComponent.componentId, declaration.configuration,
                    Vector(f.catalog.definition), declaration.instances, declaration.continuations))
                  val native = take(storage.bindCompletionC(Projection.completionOperation, ReasoningJson.requestCodec,
                    ReasoningJson.resultCodec, Projection.closingType, (_, _) => Consequence.success(Vector.empty)))
                  val semanticconflicts = Vector(
                    "reply assignment" -> submitted.copy(resultReference = ContextReference("foreign-reasoning-result", "1")),
                    "answer evidence" -> submitted.copy(evidence = submitted.evidence.copy(references = Vector.empty))) ++
                    (if (answerdecision == Reasoning.Decision.Enriched) Vector(
                      "missing unknown classification" -> submitted.copy(result = submitted.result.copy(value = judgment.copy(assignments = Vector.empty))),
                      "known-item rewrite" -> submitted.copy(result = submitted.result.copy(value = judgment.copy(assignments =
                        judgment.assignments :+ Reasoning.Assignment(source.inventory.orderedUnfinishedItems.head,
                          Reasoning.Classification.SettledExecution, "not part of the unknown assignment")))))
                    else Vector("partial DecisionNeeded" -> submitted.copy(result = submitted.result.copy(value = judgment.copy(assignments =
                      Vector(Reasoning.Assignment(actualrequest.items.head.identity, Reasoning.Classification.SettledExecution,
                        "DecisionNeeded cannot supply a partial classification")))))))
                  val nativeconflicts = Vector(
                    "stale native revision" -> submitted.copy(expectedRevision = org.goldenport.cncf.workflow.StateMachineRevision(actualorder.expectedRevision.value + ":stale")),
                    "crossed executable scope" -> submitted.copy(contextSnapshot = submitted.contextSnapshot.copy(workflowRevision = "foreign-workflow-scope")))
                  for ((reason, answer) <- semanticconflicts ++ nativeconflicts) {
                    When("the real result Operation consumes " + reason + " at its semantic or native completion boundary")
                    val rejectedwire = take(WorkflowResultJsonV1.encodeC(answer, ReasoningJson.resultCodec))
                    val rejected = _public_operation_c(call, Projection.completionOperation.operation, rejectedwire, cli = false)
                    Then("the harmful answer cannot complete or publish a successor while supported saved diagnostics remain usable")
                    rejected.isSuccess shouldBe false
                    take(native.loadRetainedC(actualorder.continuationId)(using call.executionContext)) shouldBe None
                    take(call.rowC(targets.publication, successorkey)) shouldBe None
                    val rejectedkey = "reasoning-successor:" + take(M.keyC(M.Reference(actualorder.continuationId.value,
                      answer.resultReference.revision)))
                    take(call.rowC(targets.publication, rejectedkey)) shouldBe None
                    assertNative(call, issuednext.get)
                  }
                  Given("an independently existing successor row in this private fixture before result submission")
                  take(call.writeC(targets.publication, successorkey, Record.data(successorfield -> "owned-fixture-collision")))
                  When("the real result Operation stages its native completion and successor against that collision")
                  val refused = _public_operation_c(call, Projection.completionOperation.operation, resultwire, cli = false)
                  Then("no native completion or revision is committed and the independent row is not overwritten")
                  refused.isSuccess shouldBe false
                  take(native.loadRetainedC(actualorder.continuationId)(using call.executionContext)) shouldBe None
                  assertNative(call, issuednext.get)
                  take(call.rowC(targets.publication, successorkey)).get.getString(successorfield) shouldBe Some("owned-fixture-collision")
                  // This exact row is a test-owned obstruction, not user data.
                  take(call.deleteFixtureRowC(targets.publication, successorkey))
                  collisiontested = true
                }
                When("the participant returns its assigned Judgment against the supported saved diagnostics without restoring an original issue copy")
                val response = take(_public_operation_c(call, Projection.completionOperation.operation, resultwire,
                  cli = reopen == "public result"))
                val wire = _scalar(response)
                val observed = take(SplitPhaseManagedReasoningCompletion.observeForCallC(actualorder.continuationId,
                  declaration, f.catalog, call.core)).get
                if (completed.isEmpty) { completed = Some(observed.current); acknowledgement = Some(wire) }
                Then("the native completion and complete successor are retained without another AI call, allocation or append")
                wire shouldBe acknowledgement.get
                val wait = take(org.goldenport.cncf.workflow.WorkflowWaitJsonV1.decodeC(wire))
                  .current.asInstanceOf[WorkflowContinuation.Wait]
                wait.expectedRevision.value shouldBe observed.current.revision.value.toString
                observed.current shouldBe completed.get
                observed.completion.result shouldBe submitted
                observed.next shouldBe completed.get
                observed.next.history.dropRight(1) shouldBe issuednext.get.history
                observed.next.revision shouldBe issuednext.get.revision.next
                observed.next.suspension shouldBe None
                observed.completion.closingResult shouldBe Some(StateMachineOperationResult(Projection.closingType, submitted.resultReference))
                if (answerdecision == Reasoning.Decision.Enriched) {
                  observed.downstream.nonEmpty shouldBe true
                  completedplanning = observed.downstream
                  observed.downstream.get.partition shouldBe expected.get.snapshot.partition
                  observed.downstream.get.projection shouldBe expected.get.snapshot.projection
                  observed.downstream.get.admission shouldBe expected.get.snapshot.admission
                } else observed.downstream shouldBe None
                assertNative(call, completed.get)
                When("a repeated result attempts to replace the already committed semantic answer")
                val crossed = submitted.copy(result = submitted.result.copy(value = judgment.copy(rationale = "different answer")))
                val crossedwire = take(WorkflowResultJsonV1.encodeC(crossed, ReasoningJson.resultCodec))
                val refused = _public_operation_c(call, Projection.completionOperation.operation, crossedwire, cli = false)
                Then("historical observation refuses the different result without changing native completion")
                refused.isSuccess shouldBe false
                assertNative(call, completed.get)
                Given("the saved successor calculation and unrelated physical metadata without a reconstructed Plan certificate")
                val stored = take(call.rowC(targets.publication, successorkey)).get.getString(successorfield).get
                val prefix = "sm-workflow.reasoning-successor.v1:"
                val storedjson = io.circe.parser.parse(stored.substring(prefix.length)).toOption.get
                val diagnostics = observed.downstream.map { snapshot =>
                  val partition = snapshot.partition.asInstanceOf[Snapshot.PartitionView.Enumerated]
                  partition.evaluatedCount should be < snapshot.selection.partition.policy.maximumCombinations
                  snapshot.copy(partition = partition.copy(evaluatedCount = partition.evaluatedCount + 1,
                    rejectedByCeiling = partition.rejectedByCeiling + 1))
                }
                val diagnosticjson = storedjson.mapObject(_.add("downstream", diagnostics
                  .map(snapshot => Json.fromString(take(SplitPhasePlanningSnapshotJsonV1.encodeC(snapshot))))
                  .getOrElse(Json.Null)))
                take(call.writeC(targets.publication, successorkey, Record.data(successorfield -> (prefix + diagnosticjson.noSpaces),
                  "unusedPhysicalColumn" -> "not a saved calculation certificate"), update = true))
                When("the real public repeated result and owning retained reader consume the saved calculation")
                val diagnosticresponse = _scalar(take(_public_operation_c(call, Projection.completionOperation.operation, resultwire, cli = false)))
                val diagnosticobservation = take(SplitPhaseManagedReasoningCompletion.observeForCallC(actualorder.continuationId,
                  declaration, f.catalog, call.core)).get
                Then("stored diagnostics survive without semantic readmission whole-body comparison or another native append")
                diagnosticresponse shouldBe acknowledgement.get
                diagnosticobservation.downstream shouldBe diagnostics
                diagnosticobservation.completion.result shouldBe submitted
                take(call.rowC(targets.publication, successorkey)).get.getString("unusedPhysicalColumn") shouldBe
                  Some("not a saved calculation certificate")
                assertNative(call, completed.get)
                Given("the actual issued task has different saved presentation after its native result completed")
                val issuedpayload = take(call.rowC(declaration.continuations, actualorder.continuationId.value))
                  .get.getString("issuedWorkPayload").get
                val originalorder = issuedwork.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[Reasoning.Request]]
                val diagnostictask = issuedwork.get.copy(current = originalorder.copy(
                  presentation = originalorder.presentation.copy(summary = Some("retained saved explanation"))))
                take(call.writeC(declaration.continuations, actualorder.continuationId.value,
                  Record.data("issuedWorkPayload" -> ("cncf.workflow-json.v1:" + take(WorkflowWorkOrderJsonV1.encodeC(
                    diagnostictask, ReasoningJson.requestCodec)))), update = true))
                When("the real repeated result and retained observation select that actual issued task")
                val retainedtaskresponse = _scalar(take(_public_operation_c(call, Projection.completionOperation.operation, resultwire, cli = false)))
                val retainedtask = take(SplitPhaseManagedReasoningCompletion.observeForCallC(actualorder.continuationId,
                  declaration, f.catalog, call.core)).get
                Then("source selection does not reconstruct the old WorkOrder or certify its unchanged publication prefix")
                retainedtaskresponse shouldBe acknowledgement.get
                retainedtask.work shouldBe diagnostictask
                retainedtask.downstream shouldBe diagnostics
                assertNative(call, completed.get)
                take(call.writeC(declaration.continuations, actualorder.continuationId.value,
                  Record.data("issuedWorkPayload" -> issuedpayload), update = true))
                Given("that selected successor row names a different retained result")
                val crossedjson = diagnosticjson.mapObject(_.add("result", Json.arr(Json.fromString("foreign-reasoning-result"),
                  Json.fromString(submitted.resultReference.revision))))
                take(call.writeC(targets.publication, successorkey, Record.data(successorfield -> (prefix + crossedjson.noSpaces)), update = true))
                When("the actual public result Operation resolves the successor belonging to this native completion")
                val crossedsuccessor = _public_operation_c(call, Projection.completionOperation.operation, resultwire, cli = false)
                Then("another result's calculation cannot substitute for the selected completion and native state stays unchanged")
                crossedsuccessor.isSuccess shouldBe false
                assertNative(call, completed.get)
                // Restore only this fixture-owned payload for the separate
                // physical reopen and Preview expectations below.
                Given("the original saved successor JSON restored with harmless presentation whitespace")
                val formatted = prefix + storedjson.spaces2
                take(call.writeC(targets.publication, successorkey, Record.data(successorfield -> formatted), update = true))
                When("retained observation reads the formatted record rather than a copied canonical-byte proof")
                val formattedobservation = take(SplitPhaseManagedReasoningCompletion.observeForCallC(actualorder.continuationId,
                  declaration, f.catalog, call.core)).get
                Then("full semantic successor data and the native completion remain unchanged")
                formattedobservation.downstream shouldBe observed.downstream
                formattedobservation.completion.result shouldBe submitted
                assertNative(call, completed.get)
                Consequence.unit
              }
            }
            Given("the retained result but an authenticated foreign participant")
            withHost(path, retainedvalues, reasoningcompletion = true, participant = "foreign") { (call, _) =>
              When("that actor submits the identical saved answer through the generated result Operation")
              val refused = _public_operation_c(call, Projection.completionOperation.operation, resultwire, cli = false)
              Then("the original-participant admission refuses without changing native completion")
              refused.isSuccess shouldBe false
              assertNative(call, completed.get)
              Consequence.unit
            }
            Given("R7: the saved reasoning successor reopened without either allocation policy")
            var preview = Option.empty[String]
            var previewnative = Option.empty[InstanceRecord]
            var previewobservations = 0
            var interruptpreview = issuecase == "public-issue"
            val previewobservation = () => {
              previewobservations += 1
              if (interruptpreview) {
                interruptpreview = false
                Consequence.serviceUnavailable("test-owned final preview acknowledgement interruption")
              } else Consequence.unit
            }
            for (reopen <- Vector("first public preview", "physical terminal reopen")) {
              withHost(path, retainedvalues, publicadvance = true, terminalreadback = previewobservation) { (call, _) =>
                When("public advance consumes the saved whole successor, rather than the original reasoning frontier")
                val response = _public_advance_c(call, wire, cli = true)
                if (answerdecision == Reasoning.Decision.Enriched) {
                  if (issuecase == "public-issue" && preview.isEmpty) {
                    Then("an interrupted final acknowledgement is indeterminate even though the actual terminal committed")
                    response.isSuccess shouldBe false
                  } else preview.foreach(prior => _scalar(take(response)) shouldBe prior)
                  When("both provided profiles observe the actual saved preview after any interrupted acknowledgement")
                  Vector(false, true).foreach { cli =>
                    val raw = _scalar(take(_public_advance_c(call, wire, cli)))
                    val decoded = take(org.goldenport.cncf.workflow.WorkflowTerminalJsonV1.decodeC(raw,
                      SplitPhaseManagedSelectedPreview.payloadCodec))
                    val output = decoded.current.asInstanceOf[WorkflowContinuation.Terminal[io.circe.Json]].result.value
                    val snapshot = completedplanning.get
                    val chosen = snapshot.objective.get.asInstanceOf[Snapshot.ObjectiveView.Selected]
                    val built = snapshot.projection.get.candidates.collectFirst {
                      case value: Snapshot.CandidateProjectionView.BuiltView if value.key == chosen.key => value
                    }.get
                    Then("the useful Terminal contains the selected partition, work items, ownership and validation, with no reallocation")
                    output.hcursor.get[String]("mode").toOption shouldBe Some("PREVIEW")
                    output.hcursor.get[String]("sourcePhaseIdentity").toOption shouldBe Some(source.source.phase.value)
                    output.hcursor.get[Vector[Int]]("cutAfterIndices").toOption shouldBe Some(chosen.key.cutAfterIndices)
                    val children = output.hcursor.get[Vector[io.circe.Json]]("children").toOption.get
                    children.map(_.hcursor.get[String]("identity").toOption.get) shouldBe
                      built.proposal.children.map(_.phase.value)
                    children.flatMap(_.hcursor.get[Vector[io.circe.Json]]("orderedWorkItems").toOption.get)
                      .map(_.hcursor.get[String]("identity").toOption.get) shouldBe
                      source.inventory.orderedUnfinishedItems.map(_.value)
                    children.map(_.hcursor.get[Int]("expectedMinutes").toOption.get) shouldBe
                      built.proposal.children.map(_.expectedMinutes)
                    output.hcursor.get[String]("validationDriver").toOption shouldBe Some(built.ownership.driver)
                    output.hcursor.downField("ownership").focus.nonEmpty shouldBe true
                    output.hcursor.downField("handoffs").focus.nonEmpty shouldBe true
                    output.hcursor.downField("documentProjection").focus shouldBe
                      Some(take(SplitPhaseSelectedDocuments.projectC(snapshot)).json)
                    val storage = take(WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get,
                      ComponentOrigin.Builtin), SmWorkflowComponent.componentId, declaration.configuration,
                      Vector(f.catalog.definition), declaration.instances, declaration.continuations))
                    val instances = take(storage.bindInstancesC()(using call.executionContext))
                    val actual = take(instances.loadC(saved.before.identity)(using call.executionContext)).get
                    actual.lifecycle shouldBe Lifecycle.Completed
                    actual.currentProgression shouldBe None
                    actual.suspension shouldBe None
                    actual.history.dropRight(2) shouldBe completed.get.history
                    actual.history.takeRight(2).head.currentProgression shouldBe Some(ProgressionReference("entry.Previewed"))
                    actual.revision shouldBe completed.get.revision.next.next
                    decoded.handle shouldBe take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, actual))
                    preview.foreach(prior => raw shouldBe prior)
                    previewnative.foreach(prior => actual shouldBe prior)
                    preview = Some(raw)
                    previewnative = Some(actual)
                    previewobservations shouldBe 1
                    assertNative(call, actual)
                  }
                } else {
                  Then("DecisionNeeded stays an explicit missing Human handler, not a partial preview, reissued AI task or Terminal")
                  response.isSuccess shouldBe false
                  assertNative(call, completed.get)
                  previewobservations shouldBe 0
                }
                Consequence.unit
              }
            }
            if (previewnative.nonEmpty) {
              Given("the saved preview but an authenticated foreign participant")
              withHost(path, retainedvalues, publicadvance = true, participant = "foreign") { (call, _) =>
                When("the foreign participant queries the original context")
                val refused = _public_advance_c(call, wire, cli = true)
                Then("no terminal is disclosed and no native history is appended")
                refused.isSuccess shouldBe false
                assertNative(call, previewnative.get)
                previewobservations shouldBe 1
                Consequence.unit
              }
            }
          }
        }
      }
    }

    "E2 Missing or different native reasoning provenance" should _retained_reasoning_spec("E2", "R2") {
      "E2/R2 refuse absent observations and another native disposition without creating a passive execution grant" in {
        Given("an actual managed checkpoint whose computed outcome is not a reasoning requirement")
        val saved = capture()
        val workroot = Files.createDirectories(Path.of("target", "sm-workflow", "work", "retained-reasoning-spec"))
        // Retain diagnostic databases until project clean, after withHost closes its pool.
        val path = Files.createTempDirectory(workroot, "smwf-retained-reasoning-refusal-").resolve("host.sqlite")
        var installed = false
        withHost(path) { (call, port) =>
          if (!installed) { take(installC(call, saved)); installed = true }
          val observed = take(port.executePreparedForCallC(saved, call.core))
          When("reasoning preparation receives a different genuine frontier or absent native data")
          val refused = Vector(Source.mechanicalC(observed), Source.mechanicalC(null), Source.partitionCompletionC(null),
            Source.scopePlanningC(null), Source.humanResolutionC(null),
            W.restoreReasoningC(null), SplitPhaseTypedReasoningWorkIssue.prepareC(null))
          val absentcompletion = SplitPhaseTypedReasoningCompletion.pendingC(null, observed.current, null)
          val issuer = take(SplitPhaseManagedReasoningIssue.bindC(
            ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin), declaration, f.catalog))
          val absentissue = issuer.issueForCallC(null, call.core)
          Then("all requests fail without changing the observed native record, and raw data has no public capability constructor")
          refused.foreach(_.isSuccess shouldBe false)
          absentissue.isSuccess shouldBe false
          absentcompletion.isSuccess shouldBe false
          assertNative(call, observed.current)
          assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseRetainedReasoningSource.Source(null, null, null)")
          assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseExplicitPlanningAdvance.ReasoningRestored(null, null)")
          assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseManagedPlanningCheckpoint.Observed(null, null, null)")
          assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseTypedReasoningWorkIssue.Plan(null, null, null, null, null)")
          assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseManagedReasoningIssue.Port(null, null, null)")
          assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseTypedReasoningCompletion.Pending(null, null)")
          Consequence.unit
        }
      }
    }
  }

  private def _scope_planning_spec(example: String, rules: String) =
    afterWord(s"in spec:split-phase-scope-downstream, example:$example, rules:$rules, phase:2")

  "E1 Native scope downstream calculation" should _scope_planning_spec("E1", "R1,R2,R3") {
    import SplitPhaseExplicitPartitionFixture.{base, enumerate, proof}
    import SplitPhaseExplicitObjectiveSelection as Objectives
    import SplitPhaseScopePlanningCheckpointPreparation as Planning
    import SplitPhaseScopePlanningCheckpointJsonV1 as PlanningJson
    import SplitPhaseManagedScopePlanningCheckpoint as PlanningPort
    import SplitPhasePlanningSnapshot as Snapshot
    import SplitPhaseRetainedReasoningSource as Source
    for (partitionroot <- Vector(false, true); objective <- Vector("reasoning", "overhead");
        mode <- Vector("publication", "interrupted-readback", "collision", "malformed-application",
          "missing-application", "missing-native", "missing-both")) {
      s"preserve $objective for original partition root $partitionroot through $mode and physical reopen" in {
        Given("a duration-tied frontier with a genuine public scope result and unique project-owned SQLite database")
        val source = base(Vector(260, 180, 260))
        val boundary = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(0)).get,
          "scope-planning-template", handoffPairs = Vector(0 -> 1))
        val objectives = Objectives.Records(
          Some(Objectives.CostPolicy(Objectives.CostPolicyReference("scope-planning-cost", "1"),
            source.source.reference, source.source.authority, Objectives.ProfileIdentity("expensive"),
            Objectives.ProfileIdentity("lower"), 2, 1, 1)),
          Some(Objectives.DemandRecord(Objectives.DemandReference("scope-planning-demand", "1"),
            source.source.reference, source.source.authority, source.inventory.orderedUnfinishedItems.zipWithIndex.map { (item, index) =>
              Objectives.ItemDemand(item, if (objective == "reasoning" && index == 0)
                Objectives.Demand.Unresolved("unknown reasoning classification") else Objectives.Demand.SettledExecution)
            })),
          Some(Objectives.OverheadPolicy(Objectives.OverheadPolicyReference("scope-planning-overhead", "1"),
            source.source.reference, source.source.authority, 1, 1, 0, 0, 0)))
        val saved = forBoundary(source, boundary, objectives)
        val workroot = Files.createDirectories(Path.of("target", "sm-workflow", "work", "scope-planning-spec"))
        // Retain diagnostic databases until project clean after every host has closed its pool.
        val path = Files.createTempDirectory(workroot, "scope-planning-").resolve("host.sqlite")
        val declared = values ++ SplitPhasePartitionReplyFixture.values
        var first = Option.empty[K.Record]
        withHost(path, declared, publicadvance = true) { (call, _) =>
          if (first.isEmpty) first = Some(_complete_scope_fixture(call, saved,
            SplitPhaseScopeSemantic.Decision.Enriched, partitionroot, partitionminutes = 260))
          Consequence.unit
        }
        val retainedvalues = declared.filterNot((key, _) => key.startsWith(SplitPhaseCandidateAllocationPolicy.prefix) ||
          key.startsWith(SplitPhasePartitionReplyAllocationPolicy.prefix))
        var prior = Option.empty[NC.Record]
        var expected = Option.empty[Planning.Record]
        var injected = false
        var interrupted = false
        var retainedcore = Option.empty[ActionCall.Core]
        var retainedport = Option.empty[PlanningPort.Port]
        for (reopen <- Vector("first calculation", "physical reopen")) {
          Given("the actual unsuspended scope publication without either allocation policy for " + reopen)
          withHost(path, retainedvalues) { (call, _) =>
            val owner = call.core.component.get.subsystem.get.configuration
            val scopeport = take(NM.bindC(owner, declaration, f.catalog))
            val scope = take(scopeport.executeForCallC(query(saved), call.core))
            if (prior.isEmpty) prior = Some(scope.record)
            val reference = take(Planning.referenceC(scope.record.scopeCompletion.result.continuationId,
              scope.record.scopeCompletion.result.resultReference))
            val key = take(Planning.keyC(reference))
            val nativekey = take(M.keyC(M.Reference(reference.identity, reference.revision)))
            val targets = take(U.bindTargetsC(owner, declaration))
            val binding = call.executionContext.dataStoreSpace.captureBinding()
            var observations = 0
            val port = take(PlanningPort.bindWithReadbackObservationC(owner, declaration, f.catalog,
              () => { observations += 1; Consequence.unit }))
            retainedcore = Some(call.core)
            retainedport = Some(port)
            if (mode == "collision" && !injected) {
              Given("an independently existing malformed application row without its native calculation")
              take(call.writeC(targets.publication, key, Record.data(PlanningJson.storageField -> "collision")))
              injected = true
            }
            if (injected && Set("missing-native", "missing-both").contains(mode)) {
              Given("the actual calculation receipt is missing after native progress, regardless of an old copy")
              When("retained load and execution try to consume or replay that native calculation")
              val absent = take(port.loadForCallC(query(saved), call.core))
              val rejected = port.executeForCallC(query(saved), call.core)
              Then("load reports actual absence and CAS prevents a stale calculation from appending again")
              absent shouldBe None
              rejected.isSuccess shouldBe false
              assertNative(call, expected.get.next)
              take(call.rowC(declaration.continuations, nativekey)) shouldBe None
              val application = take(call.rowC(targets.publication, key))
              if (mode == "missing-both") application shouldBe None
              else application.get.getString(PlanningJson.storageField) shouldBe Some(take(PlanningJson.encodeC(expected.get)))
            } else {
              if (mode == "interrupted-readback" && !interrupted) {
                Given("a one-time observation refusal after actual managed native commit")
                var refuse = true
                val interruptedport = take(PlanningPort.bindWithReadbackObservationC(owner, declaration, f.catalog,
                  () => if (refuse) { refuse = false; Consequence.stateConflict("scope planning observation unavailable") }
                    else Consequence.unit))
                When("the owning call commits its real calculation and readback is interrupted")
                val uncertain = interruptedport.executeForCallC(query(saved), call.core)
                Then("no successful acknowledgement is returned or automatic retry attempted")
                uncertain match {
                  case Consequence.Failure(conclusion) => conclusion.observation.cause.descriptor.facets should contain (
                    org.goldenport.observation.Descriptor.Facet.Reason("transaction-indeterminate"))
                  case _ => fail("Interrupted calculation readback incorrectly acknowledged success")
                }
                interrupted = true
              }
              Given("Spec: docs/spec/native-human-question-applicability.md; Rule: R14; Example: E31; actual scope planning with an inert owning-read counter")
              When("owning advance publishes or observes the already committed calculation twice and loads it")
              val observed = take(port.executeForCallC(query(saved), call.core))
              val repeated = take(port.executeForCallC(query(saved), call.core))
              val loaded = take(port.loadForCallC(query(saved), call.core)).get
              if (expected.isEmpty) expected = Some(observed.record)
              Then("the whole saved calculation and native append are exact, with no new retained processor stages")
              observations shouldBe 3
              observed.record shouldBe expected.get
              repeated.record shouldBe expected.get
              loaded.record shouldBe expected.get
              observed.scope.record shouldBe prior.get
              observed.record.before shouldBe prior.get.next
              observed.current shouldBe observed.record.next
              observed.current.history.dropRight(1) shouldBe prior.get.next.history
              observed.mechanicalStages shouldBe Vector.empty
              observed.record.downstream.selection shouldBe first.get.snapshot.selection
              observed.record.downstream.partition shouldBe first.get.snapshot.partition
              take(PlanningJson.decodeC(reference, take(PlanningJson.encodeC(observed.record)))) shouldBe observed.record.savedResult
              Given("that genuine native calculation and all its known demands, original authority and closures")
              When("reasoning selection asks only for its actual saved disposition")
              val reasoningsource = Source.scopePlanningC(observed)
              if (objective == "reasoning") {
                val native = take(reasoningsource)
                val restored = take(W.restoreReasoningC(native))
                val request = take(SplitPhaseExplicitReasoningSemantic.prepareC(
                  native.checkpoint.originalContext.reference, restored.pending,
                  SplitPhaseExplicitPartitionSemantic.ResultReference("scope-reasoning-fixture-result", "1"))).request
                Then("the complete scope-calculated frontier survives and only the unknown item is requested")
                native.origin shouldBe a[Source.ScopePlanning]
                native.publicationPostimage shouldBe observed.record.next
                native.checkpoint shouldBe first.get
                take(Snapshot.projectC(restored.pending)).record shouldBe observed.record.downstream
                restored.mechanicalStages shouldBe Vector.empty
                request.items.map(_.identity) shouldBe Vector(source.inventory.orderedUnfinishedItems.head)
                request.items.head.closure shouldBe source.sourceBoundary.items.head
                request.authority shouldBe source.source.authority
              } else {
                Then("the complete non-reasoning calculation is retained rather than relabeled as AI work")
                reasoningsource.isSuccess shouldBe false
                take(Snapshot.dispositionC(observed.record.downstream)) shouldBe Snapshot.Disposition.Planned
              }
              Given("the genuine scope observation now has independent later native progress")
              When("a caller passively captures the historical scope calculation")
              val recaptured = take(Planning.captureC(take(W.continuePublishedScopeC(observed.scope))))
              Then("historical data and its original expectedRevision are available without authorizing a fresh append")
              recaptured.intent.current shouldBe observed.scope.record.next
              recaptured.intent.expectedRevision shouldBe observed.scope.record.next.revision
              assertNative(call, observed.record.next)
              _specify_stale_checkpoint(call, recaptured.intent, Vector.empty, targets.storeName,
                PlanningJson.payloadCodec, observed.record.next)
              Given("a native calculation that has no application-copy producer")
              When("the owner checks the old copy address after first publication or independent reopen")
              val unusedcopy = take(call.rowC(targets.publication, key))
              Then("ordinary calculation writes no copy and pre-existing obsolete rows remain unmodified")
              if (!injected) unusedcopy shouldBe None
              else if (mode == "collision") unusedcopy.get.getString(PlanningJson.storageField) shouldBe Some("collision")
              else if (mode == "malformed-application") unusedcopy.get.getString(PlanningJson.storageField) shouldBe Some("malformed")
              else if (mode == "missing-application") unusedcopy shouldBe None
              Given("an old passive postimage in the actual native calculation payload")
              When("the owning reader consumes actual native receipt states rather than an inert copy")
              _store_passive_postimage(call, M.Reference(reference.identity, reference.revision))
              val diagnosticread = take(port.loadForCallC(query(saved), call.core)).get
              val diagnosticexecute = take(port.executeForCallC(query(saved), call.core))
              Then("actual owning consumption preserves the whole downstream and native history, without JSON copy proof or another append")
              diagnosticread.record.downstream shouldBe observed.record.downstream
              diagnosticexecute.record.downstream shouldBe observed.record.downstream
              diagnosticread.current shouldBe observed.current
              diagnosticexecute.current shouldBe observed.current
              diagnosticread.record.before shouldBe observed.record.before
              diagnosticread.record.next shouldBe observed.record.next
              diagnosticexecute.record.before shouldBe observed.record.before
              diagnosticexecute.record.next shouldBe observed.record.next
              observations shouldBe 5
              assertNative(call, observed.record.next)
              _specify_scope_planning_native_result(call, port, query(saved), targets, observed.record)
              _specify_native_checkpoint_selection(call, M.Reference(reference.identity, reference.revision), observed.current)(
                port.loadForCallC(query(saved), call.core))
              if (mode == "malformed-application" && !injected) {
                Given("the exact application row in this private diagnostic database")
                When("the test-only probe corrupts its complete frame")
                take(call.writeC(targets.publication, key, Record.data(PlanningJson.storageField -> "malformed")))
                Then("native progress remains committed while that application value is malformed")
                assertNative(call, observed.record.next)
                injected = true
              } else if (mode.startsWith("missing-") && !injected) {
                Given("the exact application/native rows in this private diagnostic database")
                // Deliberately add a private old copy to prove it supplies no native calculation.
                take(call.writeC(targets.publication, key, Record.data(PlanningJson.storageField -> take(PlanningJson.encodeC(observed.record)))))
                When("the test-only probe removes only the selected fixture records for " + mode)
                if (Set("missing-application", "missing-both").contains(mode))
                  take(call.deleteFixtureRowC(targets.publication, key))
                if (Set("missing-native", "missing-both").contains(mode))
                  take(call.deleteFixtureRowC(declaration.continuations, nativekey))
                Then("the actual instance still contains the committed calculation append")
                assertNative(call, observed.record.next)
                injected = true
              }
            }
            call.executionContext.dataStoreSpace.captureBinding() shouldBe binding
            Consequence.unit
          }
        }
        Given("the same stored calculation but a foreign participant or unrelated Operation")
        for ((operation, participant) <- Vector(K.advanceOperation.operation -> "foreign", "otherAdvance" -> "participant")) {
          withHost(path, retainedvalues, operationName = operation, participant = participant) { (call, _) =>
            val port = take(PlanningPort.bindC(call.core.component.get.subsystem.get.configuration, declaration, f.catalog))
            When("that other actor or purpose requests the retained calculation")
            val rejected = port.loadForCallC(query(saved), call.core)
            Then("the canonical native owning-call contract refuses it")
            rejected.isSuccess shouldBe false
            assertNative(call, expected.fold(prior.get.next)(_.next))
            Consequence.unit
          }
        }
        When("the expired previously captured owning core is used outside managed execution")
        val expired = retainedport.get.loadForCallC(query(saved), retainedcore.get)
        Then("correct-looking command metadata grants no expired storage access")
        expired.isSuccess shouldBe false
      }
    }
  }

  "E3 Public scope to selected Preview" should _scope_planning_spec("E3", "R1,R3,R4") {
    import SplitPhaseExplicitPartitionFixture.{base, enumerate, proof}
    import SplitPhaseExplicitObjectiveSelection as Objectives
    import SplitPhaseExplicitReasoningSemantic as Reasoning
    import SplitPhaseExplicitReasoningSemanticJson as ReasoningJson
    import SplitPhaseHumanAnswerVerification as Human
    import SplitPhaseNativeHumanDirection as HumanDirection
    import SplitPhaseHumanDirectionAnswerContract as HumanAnswer
    import SplitPhaseExplicitPartitionSemantic as Semantic
    import org.goldenport.cncf.workflow.{WorkflowWorkOrderJsonV1, WorkflowResultJsonV1,
      WorkflowTerminalJsonV1, WorkflowComponentStorage, WorkflowDecisionJsonV1}
    for (partitionroot <- Vector(false, true); needsreasoning <- Vector(false, true); humanresolution <- Vector(false, true)) {
      s"E3 connect ALL-Built scope to useful Preview for root $partitionroot reasoning $needsreasoning Human resolution $humanresolution and physical reopen" in {
        Given("a genuine public scope result with a complete cost/demand/overhead policy and one owned SQLite host")
        val source = base(Vector(260, 180, 260))
        val boundary = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(0)).get,
          "scope-public-preview", handoffPairs = Vector(0 -> 1))
        val objectives = Objectives.Records(
          Some(Objectives.CostPolicy(Objectives.CostPolicyReference("scope-preview-cost", "1"),
            source.source.reference, source.source.authority, Objectives.ProfileIdentity("expensive"),
            Objectives.ProfileIdentity("lower"), 2, 1, 1)),
          Some(Objectives.DemandRecord(Objectives.DemandReference("scope-preview-demand", "1"),
            source.source.reference, source.source.authority, source.inventory.orderedUnfinishedItems.zipWithIndex.map { (id, index) =>
              Objectives.ItemDemand(id, if (needsreasoning && index == 0)
                Objectives.Demand.Unresolved("one unknown scope-derived reasoning classification") else Objectives.Demand.SettledExecution)
            })),
          Some(Objectives.OverheadPolicy(Objectives.OverheadPolicyReference("scope-preview-overhead", "1"),
            source.source.reference, source.source.authority, 1, 1, 0, 0, 0)))
        val saved = forBoundary(source, boundary, objectives)
        val workroot = Files.createDirectories(Path.of("target", "sm-workflow", "work", "scope-public-preview-spec"))
        val path = Files.createTempDirectory(workroot, "scope-public-preview-").resolve("host.sqlite")
        val humanvalues = if (humanresolution) Map[String, ConfigurationValue](
          SplitPhaseHumanResolutionReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
          SplitPhaseHumanResolutionReadPolicy.prefix + "record-collection" -> ConfigurationValue.StringValue(collection("humanresolutions").print),
          SplitPhaseHumanResolutionCheckpointPublication.prefix + "store-name" -> ConfigurationValue.StringValue("workflow"),
          SplitPhaseHumanResolutionCheckpointPublication.prefix + "record-collection" -> ConfigurationValue.StringValue(collection("humancheckpoints").print))
          else Map.empty[String, ConfigurationValue]
        val declared = values ++ SplitPhasePartitionReplyFixture.values ++ humanvalues
        var initialized = false
        var scopecompleted = Option.empty[InstanceRecord]
        var humanchecks = 0
        var humanquestion = Option.empty[WorkflowInteraction[HumanDirection.Input, Nothing]]
        var humanresult = Option.empty[ContinuationResult[HumanAnswer.Answer]]
        val evidence = new Human.EvidencePort {
          def admitC(core: ActionCall.Core, reference: ContextReference, target: Json) = {
            humanchecks += 1
            if (!humanresolution) Consequence.securityPermissionDenied[Unit]("ALL-Built scope has no Human answer")
            else {
              val enrolled = take(ThreeWorkflowHostEvidencePorts.splitAnswerBindingC(Human.AnswerContext(
                core.component.get.core.instanceId, humanquestion.get, humanresult.get, "participant")))
              target shouldBe enrolled
              reference shouldBe ContextReference("independent-human-consent", "1")
              val now = core.executionContext.clock.instant()
              val lookup = ThreeWorkflowHostEvidencePorts.key(reference.identity, reference.revision)
              NativeHostEvidenceFixtureAdmission.admitC(core, "scope-preview-independent-test-human-source",
                NativeHostEvidence.Purpose.SplitAnswer, lookup, target, Some(NativeHostEvidence.Evidence(NativeHostEvidence.schema,
                  Human.purpose, lookup, reference.identity, reference.revision, "scope-preview-independent-test-human-source",
                  "independent-human", enrolled, now.minusSeconds(86400).toString)))
            }
          }
        }
        val wire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved)))
        withHost(path, declared, publicadvance = true, humanverifier = if (humanresolution) Some(evidence) else None) { (call, _) =>
          if (!initialized) {
            _complete_scope_fixture(call, saved, if (humanresolution) SplitPhaseScopeSemantic.Decision.DecisionNeeded
              else SplitPhaseScopeSemantic.Decision.Enriched, partitionroot, partitionminutes = 260)
            if (humanresolution) {
              When("Workflow issues the genuine whole-frontier Human question and an independent test host verifies its exact answer")
              val issued = take(WorkflowDecisionJsonV1.decodeC(_scalar(take(_public_advance_c(call, wire, true))), HumanDirection.inputCodec))
              humanquestion = Some(issued)
              val order = issued.current.asInstanceOf[WorkflowContinuation.Decision[HumanDirection.Input]].request
              humanresult = Some(ContinuationResult(issued.handle, order.runId, order.continuationId, order.expectedRevision,
                order.context.snapshot, TypedValue(HumanAnswer.resultType, HumanAnswer.Answer.ProvideResolution(
                  Semantic.ContextReference("independent-resolution", "1"))), ContextReference("independent-human-consent", "1"),
                Vector.empty, ExecutionEvidence(Vector(ContextReference("independent-human-consent", "1")), Some("participant"))))
              take(_public_operation_c(call, HumanDirection.completionOperation.operation,
                take(WorkflowResultJsonV1.encodeC(humanresult.get, HumanAnswer.payloadCodec)), false))
              Then("only the genuine Human answer is committed; no resolution data or selected partition is invented")
              humanchecks shouldBe 1
            }
            scopecompleted = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core))
            initialized = true
          }
          Consequence.unit
        }
        if (humanresolution) {
          Given("separately supplied complete resolution data after closing the answered host")
          withHost(path, declared) { (call, _) =>
            _specify_human_resolution(call, saved, humanresult.get, correction = false)
            humanchecks shouldBe 1
            assertNative(call, scopecompleted.get)
            Consequence.unit
          }
        }
        val retainedvalues = declared.filterNot((key, _) => key.startsWith(SplitPhaseCandidateAllocationPolicy.prefix) ||
          key.startsWith(SplitPhasePartitionReplyAllocationPolicy.prefix))
        var terminalwire = Option.empty[String]
        var terminalnative = Option.empty[InstanceRecord]
        for (reopen <- Vector("first public continuation", "physical terminal reopen")) {
          withHost(path, retainedvalues, publicadvance = true, humanverifier = Some(evidence)) { (call, _) =>
            When("owning public advance continues the complete ALL-Built scope under removed allocation policies for " + reopen)
            if (terminalwire.isEmpty && needsreasoning) {
              val issued = take(WorkflowWorkOrderJsonV1.decodeC(_scalar(take(_public_advance_c(call, wire, true))),
                ReasoningJson.requestCodec))
              val order = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[Reasoning.Request]].request
              val input = order.input.get.value
              Then("only the unknown classification is issued from the genuine scope-planning origin")
              input.items.map(_.identity) shouldBe Vector(source.inventory.orderedUnfinishedItems.head)
              input.items.head.closure shouldBe source.sourceBoundary.items.head
              input.authority shouldBe source.source.authority
              _scalar(take(_public_advance_c(call, wire, false))) shouldBe
                take(SplitPhaseExplicitReasoningSemanticJson.encodeWorkOrderC(issued))
              val evidence = Semantic.EvidenceReference("scope-preview-reasoning-evidence", "1")
              val judgment = Reasoning.Judgment(Reasoning.Decision.Enriched,
                "settled unknown scope work", input.items.map(item => Reasoning.Assignment(item.identity,
                  Reasoning.Classification.SettledExecution, "bounded settled implementation")), Vector(evidence))
              val result = ContinuationResult(issued.handle, order.runId, order.continuationId, order.expectedRevision,
                order.context.snapshot, TypedValue(ReasoningJson.resultCodec.typeIdentity, judgment),
                ContextReference(input.result.identity, input.result.revision), Vector.empty,
                ExecutionEvidence(Vector(ContextReference(evidence.identity, evidence.revision)), Some("participant")))
              When("the participant returns its assigned result through the real generated completion Operation")
              take(_public_operation_c(call, SplitPhaseTypedReasoningWorkIssue.completionOperation.operation,
                take(WorkflowResultJsonV1.encodeC(result, ReasoningJson.resultCodec)), true))
            }
            Vector(false, true).foreach { cli =>
              val raw = _scalar(take(_public_advance_c(call, wire, cli)))
              val decoded = take(WorkflowTerminalJsonV1.decodeC(raw, SplitPhaseManagedSelectedPreview.payloadCodec))
              val payload = decoded.current.asInstanceOf[WorkflowContinuation.Terminal[io.circe.Json]].result.value
              val storage = take(WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get,
                ComponentOrigin.Builtin), SmWorkflowComponent.componentId, declaration.configuration,
                Vector(f.catalog.definition), declaration.instances, declaration.continuations))
              val current = take(take(storage.bindInstancesC()(using call.executionContext))
                .loadC(saved.before.identity)(using call.executionContext)).get
              Then("the actual selected Preview contains all original work, with no Human question, renewed allocation or repeated append")
              payload.hcursor.get[String]("mode").toOption shouldBe Some("PREVIEW")
              payload.hcursor.get[String]("sourcePhaseIdentity").toOption shouldBe Some(source.source.phase.value)
              val documents = payload.hcursor.downField("documentProjection")
                .get[Vector[io.circe.Json]]("documents").toOption.get
              documents.head.hcursor.get[String]("mode").toOption shouldBe Some("updateSourceSections")
              documents.tail.foreach(_.hcursor.get[String]("mode").toOption shouldBe Some("createChild"))
              documents.flatMap(_.hcursor.get[Vector[String]]("ownedItems").toOption.get) shouldBe
                source.inventory.orderedUnfinishedItems.map(_.value)
              payload.hcursor.get[Vector[io.circe.Json]]("children").toOption.get
                .flatMap(_.hcursor.get[Vector[io.circe.Json]]("orderedWorkItems").toOption.get)
                .map(_.hcursor.get[String]("identity").toOption.get) shouldBe source.inventory.orderedUnfinishedItems.map(_.value)
              current.lifecycle shouldBe Lifecycle.Completed
              current.suspension shouldBe None
              val appendcount = (if (humanresolution) 3 else 4) + (if (needsreasoning) 2 else 0)
              current.history.dropRight(appendcount) shouldBe scopecompleted.get.history
              decoded.handle shouldBe take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current))
              terminalwire.foreach(prior => raw shouldBe prior)
              terminalnative.foreach(prior => current shouldBe prior)
              terminalwire = Some(raw)
              terminalnative = Some(current)
              humanchecks shouldBe (if (humanresolution) 1 else 0)
              assertNative(call, current)
            }
            Consequence.unit
          }
        }
      }
    }
  }

  "E2 Native scope calculation Create collision" should _scope_planning_spec("E2", "R2") {
    import SplitPhaseExplicitPartitionFixture.{base, enumerate, proof}
    import SplitPhaseScopePlanningCheckpointPreparation as Planning
    import SplitPhaseScopePlanningCheckpointJsonV1 as PlanningJson
    import org.goldenport.cncf.workflow.WorkflowComponentStorage
    import org.goldenport.cncf.datastore.ComponentRecordStorage
    for (partitionroot <- Vector(false, true)) {
      s"roll back the canonical transaction at original partition root $partitionroot and retain the independent row" in {
        Given("a genuine public completed scope in a unique project-owned SQLite database")
        val source = base(Vector(260, 180, 260))
        val boundary = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(0)).get,
          "scope-planning-collision", handoffPairs = Vector(0 -> 1))
        val saved = forBoundary(source, boundary)
        val workroot = Files.createDirectories(Path.of("target", "sm-workflow", "work", "scope-planning-spec"))
        // Retain diagnostic databases until project clean after all managed pools close.
        val path = Files.createTempDirectory(workroot, "scope-planning-rollback-").resolve("host.sqlite")
        val declared = values ++ SplitPhasePartitionReplyFixture.values
        var first = Option.empty[K.Record]
        withHost(path, declared, publicadvance = true) { (call, _) =>
          if (first.isEmpty) first = Some(_complete_scope_fixture(call, saved,
            SplitPhaseScopeSemantic.Decision.Enriched, partitionroot, partitionminutes = 260))
          Consequence.unit
        }
        val retainedvalues = declared.filterNot((key, _) => key.startsWith(SplitPhaseCandidateAllocationPolicy.prefix) ||
          key.startsWith(SplitPhasePartitionReplyAllocationPolicy.prefix))
        var attempted = Option.empty[Planning.Record]
        for (reopen <- Vector("collision", "physical reopen")) {
          Given("the actual owning advance without allocation policies for " + reopen)
          withHost(path, retainedvalues) { (call, _) =>
            val owner = call.core.component.get.subsystem.get
            val targets = take(U.bindTargetsC(owner.configuration, declaration))
            val binding = call.executionContext.dataStoreSpace.captureBinding()
            if (attempted.isEmpty) {
              val scope = take(take(NM.bindC(owner.configuration, declaration, f.catalog))
                .executeForCallC(query(saved), call.core))
              val calculated = take(W.continuePublishedScopeC(scope))
              val captured = take(Planning.captureC(calculated))
              // Deliberate private side effect tests canonical UoW rollback;
              // ordinary ScopePlanning supplies no application-copy Create.
              val row = U.Row(targets.publication, take(Planning.keyC(captured.record.reference)),
                Record.data(PlanningJson.storageField -> "private-collision-side-effect"))
              val storage = take(ComponentRecordStorage.bindForCallC(call.core, targets.storeName, row.collection,
                DataStore.StringEntryId(row.identity)))
              val program = take(storage.stageCreateC(row.payload))
              Given("the real fixed-target staged Create and a separately inserted conflicting row")
              take(call.writeC(row.collection, row.identity, Record.data(PlanningJson.storageField -> "independent-collision")))
              val native = take(take(WorkflowComponentStorage.bindC(ComponentCreate(owner, ComponentOrigin.Builtin),
                SmWorkflowComponent.componentId, declaration.configuration, Vector(f.catalog.definition),
                declaration.instances, declaration.continuations))
                .bindMechanicalC(K.advanceOperation, PlanningJson.payloadCodec))
              When("the same canonical native executor attempts the staged application and receipt transaction")
              val rejected = native.executeC(captured.intent, program)(using call.executionContext)
              Then("the conflicting Create fails without acknowledging a native calculation")
              rejected.isSuccess shouldBe false
              attempted = Some(captured.record)
            }
            val record = attempted.get
            val key = take(Planning.keyC(record.reference))
            val nativekey = take(M.keyC(M.Reference(record.reference.identity, record.reference.revision)))
            When("the actual instance, native receipt and independent row are read after failure or physical reopen")
            val current = take(instances.loadForCallC(f.configuration, record.before.identity, call.core)).get
            val receipt = take(call.rowC(declaration.continuations, nativekey))
            val application = take(call.rowC(targets.publication, key)).get
            Then("rollback retains the exact scope predecessor and independent row, with no partial native append or receipt")
            current shouldBe record.before
            receipt shouldBe None
            application.getString(PlanningJson.storageField) shouldBe Some("independent-collision")
            call.executionContext.dataStoreSpace.captureBinding() shouldBe binding
            Consequence.unit
          }
        }
      }
    }
  }

  "Genuine pending partition observation before public advance" should {
    "return the published work through Service, CLI and reopen without reply policy or another issue" in {
      Given("private original rows at the actual typed Start position and an admitted native issue host")
      val saved = capture()
      val original = O.Original(f.context.copy(owner = f.context.owner.copy(instanceRevision = saved.before.revision)), f.record, f.facts)
      val path = Files.createTempDirectory("smwf-pending-work-").resolve("host.sqlite")
      var expected = Option.empty[WorkflowInteraction[SplitPhaseExplicitPartitionSemantic.Request, Nothing]]
      var issuednative = Option.empty[InstanceRecord]
      var issuedrow = Option.empty[Record]
      val issuevalues = values ++ SplitPhasePartitionReplyFixture.values
      val ap = SplitPhaseCandidateAllocationPolicy.prefix
      val rp = SplitPhasePartitionReplyAllocationPolicy.prefix
      for (mode <- Vector("issue", "policy-removed", "policy-changed")) {
        val declared = if (mode == "policy-removed") issuevalues.filterNot((key, _) => key.startsWith(ap) || key.startsWith(rp))
          else if (mode == "policy-changed") issuevalues.updated(rp + "namespace", ConfigurationValue.StringValue("different-reply"))
            .updated(ap + "namespace", ConfigurationValue.StringValue("different-candidate")) else issuevalues
        withHost(path, declared) { (call, port) =>
          Given("the current owning call with physical SQLite state for " + mode)
          if (expected.isEmpty) {
            _original_rows(original, f.record).foreach { case (c, id, row) => take(call.writeC(c, id, row)) }
            take(instances.createForCallC(f.configuration, f.initial, call.core))
            take(instances.appendForCallC(f.configuration, saved.before.identity, initialRevision, saved.before.history.head, call.core))
            When("the unsuspended state is observed before the private issue-time fixture prepares work")
            val absent = take(port.loadPendingPartitionForCallC(query(saved), call.core))
            val retained = take(port.loadRetainedForCallC(query(saved), call.core))
            Then("no pending work is invented")
            absent shouldBe None
            retained shouldBe None
            Given("genuine native issue prepared once in the labeled fixture, not in recovery")
            val bound = take(SplitPhaseTypedStartPreparation.bindPartitionC(f.prepared,
              take(SplitPhaseExplicitPartitionContext.prepareC(original.context, original.facts, saved.before, f.configuration, f.catalog))))
            val issuing = take(SplitPhaseManagedTypedPartitionIssue.bindC(ComponentCreate(call.core.component.get.subsystem.get,
              ComponentOrigin.Builtin), declaration, f.catalog))
            expected = Some(take(org.goldenport.cncf.workflow.WorkflowWorkOrderJsonV1.decodeC(
              take(issuing.issueForCallC(bound, call.core)), SplitPhaseExplicitPartitionSemanticJson.requestCodec)))
            issuednative = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core))
            issuedrow = take(call.rowC(declaration.continuations, original.context.owner.continuation.value))
          }
          val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
          When("the owning advance call observes existing partition work")
          val observed = take(port.loadPendingPartitionForCallC(query(saved), call.core))
          Then("the exact published interaction, reply identities and native revision are preserved with no new documents or claim")
          observed shouldBe expected
          take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)) shouldBe issuednative
          take(call.rowC(declaration.continuations, original.context.owner.continuation.value)) shouldBe issuedrow
          _assert_no_checkpoint(call, saved)
          call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
          Given("a genuine current suspension, not an unsuspended fresh-planning boundary")
          When("the retained-output reader is called at that suspended boundary")
          val retainedresult = port.loadRetainedForCallC(query(saved), call.core)
          Then("the suspension is refused rather than reported as empty retained output")
          retainedresult.isSuccess shouldBe false
          take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)) shouldBe issuednative
          take(call.rowC(declaration.continuations, original.context.owner.continuation.value)) shouldBe issuedrow
          _assert_no_checkpoint(call, saved)
          call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
          Consequence.unit
        }
      }
      for (mode <- Vector("later-suspension", "completed")) {
        var changed = false
        withHost(path, issuevalues.filterNot((key, _) => key.startsWith(ap) || key.startsWith(rp))) { (call, port) =>
        Given("later native history for " + mode + ", retaining the original issued-work row")
        if (!changed) {
          val before = issuednative.get
          val completed = mode == "completed"
          val suspension = if (completed) None else Some(before.suspension.get.copy(
            continuationIdentity = ContinuationIdentity("private-later-unpublished-work"), expectedRevision = before.revision.next))
          val entry = before.history.last.copy(sequence = HistorySequence(before.history.last.sequence.value + 1L),
            revision = before.revision.next, lifecycle = if (completed) Lifecycle.Completed else Lifecycle.Active,
            currentProgression = if (completed) None else before.currentProgression,
            suspension = suspension, causation = CausationReference("private-later-fixture"))
          issuednative = Some(take(instances.appendForCallC(f.configuration, before.identity, before.revision, entry, call.core)))
          changed = true
        }
        When("advance observes the later native state")
        val observed = port.loadPendingPartitionForCallC(query(saved), call.core)
        val retained = port.loadRetainedForCallC(query(saved), call.core)
        Then("stale work is refused for another suspension, while completion alone is not promoted to a typed Terminal")
        if (mode == "later-suspension") observed.isSuccess shouldBe false else take(observed) shouldBe None
        retained.isSuccess shouldBe false
        take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)) shouldBe issuednative
        take(call.rowC(declaration.continuations, original.context.owner.continuation.value)) shouldBe issuedrow
        _assert_no_checkpoint(call, saved)
        Consequence.unit
        }
      }
    }

    "refuse a crossed suspension without actual work instead of reissuing" in {
      Given("privately seeded original Start rows and a native suspension with no issued Continuation")
      val saved = capture()
      val path = Files.createTempDirectory("smwf-pending-absent-").resolve("host.sqlite")
      var installed = false
      var expected = Option.empty[InstanceRecord]
      withHost(path) { (call, port) =>
        Given("the original native state and an unpublished suspension")
        if (!installed) {
          take(installC(call, saved))
          val revision = saved.before.revision.next
          val entry = saved.before.history.last.copy(sequence = HistorySequence(2), revision = revision,
            causation = CausationReference(saved.record.originalContext.reference.identity),
            suspension = Some(SuspensionBoundary(ContinuationIdentity("crossed-unpublished-work"), revision,
              ContextSnapshotReference(f.catalog.definition.workflowRevision.value), CompletionReference("fixture"),
              EvidenceReference("fixture"), saved.before.correlation)))
          expected = Some(take(instances.appendForCallC(f.configuration, saved.before.identity, saved.before.revision, entry, call.core)))
          installed = true
        }
        val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
        When("the advance call tries to observe pending work")
        val observed = port.loadPendingPartitionForCallC(query(saved), call.core)
        Then("missing publication is refused rather than converted to fresh planning or a fake Wait")
        observed.isSuccess shouldBe false
        take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)) shouldBe expected
        take(call.rowC(declaration.continuations, saved.record.originalContext.owner.continuation.value)) shouldBe None
        _assert_no_checkpoint(call, saved)
        call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
        Consequence.unit
      }
    }

    "consume saved WorkOrder diagnostics while refusing actual foreign issued bindings and ignoring unused Start-input changes" in {
      Given("one genuine native WorkOrder and independently stored original data")
      val saved = capture()
      val original = O.Original(f.context, f.record, f.facts)
      val path = Files.createTempDirectory("smwf-pending-drift-").resolve("host.sqlite")
      var installed = false
      var expected = Option.empty[WorkflowInteraction[SplitPhaseExplicitPartitionSemantic.Request, Nothing]]
      withHost(path, values ++ SplitPhasePartitionReplyFixture.values) { (call, port) =>
        Given("the actual issue-time fixture only on its first invocation")
        if (!installed) {
          _original_rows(original, f.record).foreach { case (c, id, row) => take(call.writeC(c, id, row)) }
          take(instances.createForCallC(f.configuration, f.initial, call.core))
          take(instances.appendForCallC(f.configuration, saved.before.identity, initialRevision, saved.before.history.head, call.core))
          val bound = take(SplitPhaseTypedStartPreparation.bindPartitionC(f.prepared,
            take(SplitPhaseExplicitPartitionContext.prepareC(original.context, original.facts, saved.before, f.configuration, f.catalog))))
          val issuing = take(SplitPhaseManagedTypedPartitionIssue.bindC(ComponentCreate(call.core.component.get.subsystem.get,
            ComponentOrigin.Builtin), declaration, f.catalog))
          expected = Some(take(org.goldenport.cncf.workflow.WorkflowWorkOrderJsonV1.decodeC(
            take(issuing.issueForCallC(bound, call.core)), SplitPhaseExplicitPartitionSemanticJson.requestCodec)))
          installed = true
        }
        val native = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
        val work = expected.get
        val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[SplitPhaseExplicitPartitionSemantic.Request]]
        val request = order.request
        val row = take(call.rowC(declaration.continuations, original.context.owner.continuation.value)).get
        val changedrequests = Vector(request.copy(expectedRevision = org.goldenport.cncf.workflow.StateMachineRevision("99")),
          request.copy(runId = org.goldenport.cncf.workflow.StateMachineRunIdentity("foreign")),
          request.copy(completionOperation = org.goldenport.cncf.workflow.StateMachineOperationIdentity("foreign", "operation")))
        for (changed <- changedrequests) {
          Given("the actual physical issued payload selecting a foreign run, revision or completion route")
          val selected = work.copy(current = order.copy(request = changed))
          take(call.writeC(declaration.continuations, original.context.owner.continuation.value,
            Record.data("issuedWorkPayload" -> ("cncf.workflow-json.v1:" + take(
              org.goldenport.cncf.workflow.WorkflowWorkOrderJsonV1.encodeC(selected,
                SplitPhaseExplicitPartitionSemanticJson.requestCodec)))), true))
          When("the actual native issued reader resolves that saved source for pending observation")
          val refused = port.loadPendingPartitionForCallC(query(saved), call.core)
          Then("foreign binding data fails at its native consumer without effects or a recreated issue")
          refused.isSuccess shouldBe false
          assertNative(call, native)
          _assert_no_checkpoint(call, saved)
          take(call.writeC(declaration.continuations, original.context.owner.continuation.value, row, true))
        }
        Given("the actual published payload made unreadable while its row identity remains")
        take(call.writeC(declaration.continuations, original.context.owner.continuation.value,
          Record.data("issuedWorkPayload" -> "malformed-issued-payload"), true))
        When("advance reads the actual malformed publication")
        val malformedresult = port.loadPendingPartitionForCallC(query(saved), call.core)
        Then("it refuses rather than recreating the original work")
        malformedresult.isSuccess shouldBe false
        take(call.writeC(declaration.continuations, original.context.owner.continuation.value, row, true))
        Given("the independent original source payload changed under the same reference")
        val source = f.record.source
        take(call.writeC(target(F.Purpose.Source), source.reference.identity, Record.data(F.storageField ->
          take(F.encodeSourceC(source.copy(authority = source.authority.copy(identity = "foreign"))))), true))
        When("advance observes the already assigned native work without preparing a new source input")
        val sourceresult = port.loadPendingPartitionForCallC(query(saved), call.core)
        Then("an unused independent source copy does not gate cached work or cause reissue")
        take(sourceresult) shouldBe expected
        take(call.writeC(target(F.Purpose.Source), source.reference.identity, Record.data(F.storageField -> take(F.encodeSourceC(source))), true))
        val start = f.record
        val changedcommands = Vector(start.command.copy(idempotencyKey = "different-valid-key"),
          start.command.copy(input = start.command.input.copy(sourceAuthorityReference = "different-authority")),
          start.command.copy(input = start.command.input.copy(sourcePhaseIdentity = "99")),
          start.command.copy(input = start.command.input.copy(sourceSnapshotReference = Some("different-source"))),
          start.command.copy(input = start.command.input.copy(proposalReference = None)),
          start.command.copy(input = start.command.input.copy(invocationMode = "APPLY")),
          start.command.copy(input = start.command.input.copy(originGoalPhaseTerminalResultReference = Some("different-terminal"))))
        val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
        val selectionrow = take(call.rowC(collection("selections"), start.selection.selectionId))
        val sourcerow = take(call.rowC(target(F.Purpose.Source), source.reference.identity))
        val proposalrow = take(call.rowC(target(F.Purpose.Proposal), start.proposal.get.reference.identity))
        for (command <- changedcommands) {
          Given("a canonical Start-command field changed under the same Start reference, with independent selection/source unchanged")
          val changed = start.copy(command = command)
          val wire = take(TJ.encodeC(changed))
          take(TJ.decodeC(start.reference, wire)) shouldBe changed
          take(call.writeC(collection("starts"), start.reference.identity, Record.data(TJ.storageField -> wire), true))
          When("advance observes pending work without consuming fresh Start input")
          val crossed = port.loadPendingPartitionForCallC(query(saved), call.core)
          Then("the same genuine WorkOrder is returned without selection/source/proposal changes or reissue")
          take(crossed) shouldBe expected
          When("fresh Start preparation separately consumes the changed command and unchanged actual selection")
          val freshreads = take(SplitPhaseTypedStartReadPolicy.bindC(call.core.component.get.subsystem.get.configuration,
            declaration, f.catalog))
          val fresh = freshreads.prepareForCallC(start.reference, call.core)
          Then("that incompatible fresh invocation is refused at its owning admission, not the pending-query boundary")
          fresh.isSuccess shouldBe false
          take(call.rowC(collection("selections"), start.selection.selectionId)) shouldBe selectionrow
          take(call.rowC(target(F.Purpose.Source), source.reference.identity)) shouldBe sourcerow
          take(call.rowC(target(F.Purpose.Proposal), start.proposal.get.reference.identity)) shouldBe proposalrow
          assertNative(call, native)
          take(call.rowC(declaration.continuations, original.context.owner.continuation.value)) shouldBe Some(row)
          _assert_no_checkpoint(call, saved)
          call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
          Given("the exact original Start row restored, without reissue")
          take(call.writeC(collection("starts"), start.reference.identity, Record.data(TJ.storageField -> take(TJ.encodeC(start))), true))
          When("advance recovers the original published interaction")
          val recovered = take(port.loadPendingPartitionForCallC(query(saved), call.core))
          Then("the same saved work and unchanged native data are returned")
          recovered shouldBe expected
          assertNative(call, native)
          take(call.rowC(declaration.continuations, original.context.owner.continuation.value)) shouldBe Some(row)
          _assert_no_checkpoint(call, saved)
          call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
        }
        Given("Start rows whose selected routing agrees but unused captured source or proposal differs")
        val changedstarts = Vector(start.copy(source = source.copy(authority = source.authority.copy(revision = "different"))),
          start.copy(proposal = start.proposal.map(proposal => proposal.copy(children = Vector.empty))))
        for (changed <- changedstarts) {
          Given("one changed captured Start body in the actual private store")
          take(call.writeC(collection("starts"), start.reference.identity,
            Record.data(TJ.storageField -> take(TJ.encodeC(changed))), true))
          When("pending observation consumes only actual saved routing")
          val observed = port.loadPendingPartitionForCallC(query(saved), call.core)
          Then("unused captured body echoes do not reject or recreate already assigned work")
          take(observed) shouldBe expected
          assertNative(call, native)
          take(call.rowC(declaration.continuations, original.context.owner.continuation.value)) shouldBe Some(row)
          _assert_no_checkpoint(call, saved)
        }
        Given("the exact original source and actual issued payload restored")
        take(call.writeC(collection("starts"), start.reference.identity,
          Record.data(TJ.storageField -> take(TJ.encodeC(start))), true))
        When("advance observes the stored work again")
        val restored = take(port.loadPendingPartitionForCallC(query(saved), call.core))
        Then("the original interaction and native history are preserved with no checkpoint publication")
        restored shouldBe expected
        assertNative(call, native)
        take(call.rowC(declaration.continuations, original.context.owner.continuation.value)) shouldBe Some(row)
        _assert_no_checkpoint(call, saved)
        Consequence.unit
      }
    }

    "return and complete saved diagnostic work through actual Operations and physical reopen without an issue-copy certificate" in {
      Given("a genuine native issue in one private SQLite host and compatible saved presentation and Context diagnostics")
      val saved = capture()
      val original = O.Original(f.context, f.record, f.facts)
      val path = Files.createTempDirectory("smwf-pending-diagnostics-").resolve("host.sqlite")
      val issuevalues = values ++ SplitPhasePartitionReplyFixture.values
      var installed = false
      var retainedwork = Option.empty[WorkflowInteraction[SplitPhaseExplicitPartitionSemantic.Request, Nothing]]
      var retainednative = Option.empty[InstanceRecord]
      var submitted = Option.empty[ContinuationResult[SplitPhaseExplicitPartitionSemantic.Judgment]]
      var acknowledgement = Option.empty[String]
      val querywire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved)))
      withHost(path, issuevalues, publicadvance = true) { (call, _) =>
        if (!installed) {
          _original_rows(original, f.record).foreach { case (c, id, row) => take(call.writeC(c, id, row)) }
          take(instances.createForCallC(f.configuration, f.initial, call.core))
          take(instances.appendForCallC(f.configuration, saved.before.identity, initialRevision, saved.before.history.head, call.core))
          val bound = take(SplitPhaseTypedStartPreparation.bindPartitionC(f.prepared,
            take(SplitPhaseExplicitPartitionContext.prepareC(original.context, original.facts, saved.before, f.configuration, f.catalog))))
          val issuing = take(SplitPhaseManagedTypedPartitionIssue.bindC(ComponentCreate(call.core.component.get.subsystem.get,
            ComponentOrigin.Builtin), declaration, f.catalog))
          val actual = take(org.goldenport.cncf.workflow.WorkflowWorkOrderJsonV1.decodeC(
            take(issuing.issueForCallC(bound, call.core)), SplitPhaseExplicitPartitionSemanticJson.requestCodec))
          val order = actual.current.asInstanceOf[WorkflowContinuation.WorkOrder[SplitPhaseExplicitPartitionSemantic.Request]]
          val selected = actual.copy(current = order.copy(presentation = order.presentation.copy(summary = Some("saved explanation")),
            request = order.request.copy(context = order.request.context.copy(summary = "original saved partition work",
              references = Vector(ContextReference("diagnostic-context", "old")),
              snapshot = order.request.context.snapshot.copy(workspaceRevision = Some("old-workspace"),
                evidenceRevision = Some("old-evidence"))))))
          val wire = take(org.goldenport.cncf.workflow.WorkflowWorkOrderJsonV1.encodeC(selected,
            SplitPhaseExplicitPartitionSemanticJson.requestCodec))
          take(call.writeC(declaration.continuations, original.context.owner.continuation.value,
            Record.data("issuedWorkPayload" -> ("cncf.workflow-json.v1:" + wire)), true))
          retainedwork = Some(selected)
          installed = true
        }
        val selected = retainedwork.get
        val native = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
        val row = take(call.rowC(declaration.continuations, original.context.owner.continuation.value))
        val binding = call.executionContext.dataStoreSpace.captureBinding()
        When("actual public advance consumes the saved native issue through its owning pending reader")
        val returned = take(org.goldenport.cncf.workflow.WorkflowWorkOrderJsonV1.decodeC(
          _scalar(take(_public_advance_c(call, querywire, false))), SplitPhaseExplicitPartitionSemanticJson.requestCodec))
        Then("the complete saved interaction survives without reissue, a diagnostic-copy gate or effects")
        returned shouldBe selected
        assertNative(call, native)
        take(call.rowC(declaration.continuations, original.context.owner.continuation.value)) shouldBe row
        _assert_no_checkpoint(call, saved)
        call.executionContext.dataStoreSpace.captureBinding() shouldBe binding
        retainedwork = Some(returned)
        retainednative = Some(native)
        Consequence.unit
      }
      Given("a physically reopened host without issue-time reply policy and the actual saved semantic work")
      val rp = SplitPhasePartitionReplyAllocationPolicy.prefix
      val issuednative = retainednative.get
      withHost(path, issuevalues.filterNot((key, _) => key.startsWith(rp)), publicadvance = true) { (call, _) =>
        val returned = retainedwork.get
        if (submitted.isEmpty) {
          When("actual advance observes the same pending assignment after reopening, before its first result")
          val observed = take(org.goldenport.cncf.workflow.WorkflowWorkOrderJsonV1.decodeC(
            _scalar(take(_public_advance_c(call, querywire, false))), SplitPhaseExplicitPartitionSemanticJson.requestCodec))
          Then("saved diagnostics and native state survive without a new allocation or issue")
          observed shouldBe returned
          assertNative(call, issuednative)
        }
        Given("only the answer to that actual saved assignment, not an echoed issue-time Context or presentation")
        val reply = _semantic_result(returned, SplitPhaseExplicitPartitionSemantic.Decision.Enriched)
        val binding = call.executionContext.dataStoreSpace.captureBinding()
        When("the owning public completion Operation consumes the semantic result and executes its actual managed closing")
        val response = _scalar(take(_public_operation_c(call, SplitPhaseExplicitPartitionContext.completionOperation.operation,
          take(org.goldenport.cncf.workflow.WorkflowResultJsonV1.encodeC(reply, SplitPhaseExplicitPartitionSemanticJson.resultCodec)), false)))
        val current = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
        val completion = take(SplitPhaseManagedTypedPartitionCompletion.bindHostC(
          ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin), declaration, f.catalog))
        val retained = take(completion.loadRetainedForCallC(original.context.owner.continuation, call.core)).get
        val reads = take(SplitPhasePartitionCompletionCheckpointDataStorePort.bindC(
          call.core.component.get.subsystem.get.configuration, declaration, f.catalog))
        val calculation = take(reads.loadForCallC(original.context.owner.continuation, call.core))
        Then("one actual native completion and whole calculated semantic output are saved without regenerated-WorkOrder equality")
        current.history.dropRight(1) shouldBe issuednative.history
        current.revision shouldBe issuednative.revision.next
        current.suspension shouldBe None
        retained.result shouldBe reply
        calculation.completion.result shouldBe reply
        calculation.issued shouldBe returned
        calculation.checkpoint.get.snapshot.selection.partition.estimates.items.head shouldBe f.facts.partition.estimates.items.head
        call.executionContext.dataStoreSpace.captureBinding() shouldBe binding
        submitted = Some(reply)
        acknowledgement = Some(response)
        retainednative = Some(current)
        Consequence.unit
      }
      Given("the actual persisted completion after another physical reopen")
      withHost(path, issuevalues.filterNot((key, _) => key.startsWith(rp)), publicadvance = true) { (call, _) =>
        When("the owning completion reader and repeated public result submission consume the saved result")
        val completion = take(SplitPhaseManagedTypedPartitionCompletion.bindHostC(
          ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin), declaration, f.catalog))
        val retained = take(completion.loadRetainedForCallC(original.context.owner.continuation, call.core)).get
        val response = _scalar(take(_public_operation_c(call, SplitPhaseExplicitPartitionContext.completionOperation.operation,
          take(org.goldenport.cncf.workflow.WorkflowResultJsonV1.encodeC(submitted.get,
            SplitPhaseExplicitPartitionSemanticJson.resultCodec)), false)))
        Then("the original completion and acknowledgement survive without another native append or reissue")
        retained.result shouldBe submitted.get
        response shouldBe acknowledgement.get
        assertNative(call, retainednative.get)
        Consequence.unit
      }
    }
  }

  "Managed mechanical checkpoint internal seam" should {
    "commit every document and recover after SQLite reopen without current allocation policy or repeated history" in {
      Given("complete privately seeded inputs in the one Subsystem-owned SQLite store")
      val path = Files.createTempDirectory("smwf-managed-checkpoint-").resolve("host.sqlite")
      val saved = capture(); var installed = false; var current = saved.next
      When("Service and CLI execute and subsequent physical hosts only observe stored data")
      for (mode <- Vector("initial", "policy-removed", "policy-changed")) {
        val ap = SplitPhaseCandidateAllocationPolicy.prefix
        val declared = if (mode == "policy-removed") values.removed(ap + "policy-identity").removed(ap + "policy-revision").removed(ap + "namespace")
          else if (mode == "policy-changed") values.updated(ap + "namespace", ConfigurationValue.StringValue("different-candidates")) else values
        withHost(path, declared) { (call, port) =>
          if (!installed) { take(installC(call, saved)); installed = true }
          val beforeBinding = call.executionContext.dataStoreSpace.captureBinding()
          val observed = if (mode == "initial") take(port.executePreparedForCallC(saved, call.core))
            else take(port.loadMechanicalForCallC(query(saved), call.core)).get
          Then("one native append and all exact candidate values survive, including later legitimate history")
          observed.record shouldBe saved.record; observed.current shouldBe current
          observed.publicationPostimage shouldBe saved.next
          assertNative(call, current); assertRows(call, saved)
          call.executionContext.dataStoreSpace.captureBinding() shouldBe beforeBinding
          Given("the actual committed mechanical mapping, independent original rows and current native receipt")
          When("Workflow selects the retained origin without a caller-selected mechanical or AI reader")
          val routed = take(port.loadRetainedForCallC(query(saved), call.core)).get
          Then("the verified mechanical data is returned without allocation, recomputation or another append")
          routed shouldBe a[Managed.RetainedMechanical]
          routed.asInstanceOf[Managed.RetainedMechanical].observed.record shouldBe saved.record
          routed.current shouldBe current
          assertNative(call, current)
          assertRows(call, saved)
          call.executionContext.dataStoreSpace.captureBinding() shouldBe beforeBinding
          if (mode == "initial" && current == saved.next) {
            val entry = current.history.last.copy(sequence = HistorySequence(3), revision = InstanceRevision(3), causation = CausationReference("later-native"))
            current = take(instances.appendForCallC(f.configuration, current.identity, current.revision, entry, call.core))
          }
          Consequence.unit
        }
      }
    }

    "leave every attempted row and native predecessor unchanged for missing grants and immutable row conflicts" in {
      Given("isolated physical hosts with a denied Create or already occupied immutable row")
      val saved = capture(); val t = take(U.bindTargetsC(host(values), declaration)); val rows = take(U.prepareC(saved, t)).rows
      val mapping = rows.find(_.identity == take(U.mappingKeyC(saved.record.reference))).get
      val index = rows.find(_.identity == take(U.indexKeyC(saved.before.identity, saved.record.originalContext.reference))).get
      val candidate = rows.find(_.collection == t.proposals).get
      val cases = Vector("missing-grant" -> Option.empty[U.Row], "mapping" -> Some(mapping), "index" -> Some(index), "candidate" -> Some(candidate))
      When("the native atomic path refuses the complete publication")
      cases.foreach { case (name, occupied) =>
        val path = Files.createTempDirectory("smwf-managed-rejected-").resolve("host.sqlite")
        var installed = false
        for (mode <- Vector("attempt", "reopen")) withHost(path, denied = if (name == "missing-grant" && mode == "attempt") Some(t.ownerships) else None) { (call, port) =>
          if (!installed) {
            take(installC(call, saved)); occupied.foreach(r => take(call.writeC(r.collection, r.identity, Record.data("fixture" -> "occupied"))))
            installed = true
          }
          if (mode == "attempt") port.executePreparedForCallC(saved, call.core).isSuccess shouldBe false
          Then("reopen proves no native append, receipt or partial new row and preserves the old collision")
          assertNative(call, saved.before)
          rows.foreach { row =>
            val actual = take(call.rowC(row.collection, row.identity))
            if (occupied.contains(row)) actual.get.getString("fixture") shouldBe Some("occupied") else actual shouldBe None
          }
          take(call.rowC(declaration.continuations, take(M.keyC(anchor)))) shouldBe None
          Consequence.unit
        }
      }
    }

    "refuse wrong operations, different participants and forged authentication before writes" in {
      Given("actual owning native calls whose operation or authenticated actor is not admitted")
      val saved = capture()
      When("execution and observation are requested under those calls")
      for ((operation, participant) <- Vector("other" -> "participant", K.advanceOperation.operation -> "foreign", K.advanceOperation.operation -> "participant")) {
        val path = Files.createTempDirectory("smwf-managed-actor-").resolve("host.sqlite")
        withHost(path, operationName = operation, participant = participant) { (call, port) =>
          val core = if (operation == K.advanceOperation.operation && participant == "participant") call.core.copy(executionContext =
            take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.ControlledTest, call.executionContext,
              Map("principal.id" -> "participant", "privilege" -> "user", "cncf.security.authenticated" -> "true"))).executionContext) else call.core
          port.executePreparedForCallC(saved, core).isSuccess shouldBe false
          // The foreign authenticated actor has no checkpoint to resolve; fresh
          // execution is refused before any reads or fixture/native writes.
          if (participant != "foreign") port.loadMechanicalForCallC(query(saved), core).isSuccess shouldBe false
          Then("no publication or canonical instance is created")
          take(call.rowC(declaration.instances, saved.before.identity.value)) shouldBe None
          val t = take(U.bindTargetsC(host(values), declaration))
          take(call.rowC(t.publication, take(U.indexKeyC(saved.before.identity, saved.record.originalContext.reference)))) shouldBe None
          Consequence.unit
        }
      }
    }

    "read only the selected locator native receipt and declared generated documents without unchanged-input proofs" in {
      Given("one actual committed checkpoint with selected outputs and unused historical input rows")
      val saved = capture(); val t = take(U.bindTargetsC(host(values), declaration)); val path = Files.createTempDirectory("smwf-managed-drift-").resolve("host.sqlite")
      var installed = false
      withHost(path) { (call, port) =>
        if (!installed) { take(installC(call, saved)); take(port.executePreparedForCallC(saved, call.core)); installed = true }
        val publication = take(U.prepareC(saved, t)).rows
        val documentkeys = O.builtDocuments(saved.record.snapshot).flatMap(d => Vector(
          t.proposals -> d.proposal.reference.identity, t.ownerships -> d.ownership.reference.identity,
          t.handoffs -> d.handoffs.reference.identity)).toSet
        val usedkeys = documentkeys ++ publication.filter(r => r.payload.getString(J.storageField).nonEmpty ||
          r.payload.getString(J.indexStorageField).nonEmpty).map(r => r.collection -> r.identity)
        val rows = (originalRows(saved) ++ publication.map(r => (r.collection, r.identity, r.payload)))
          .distinctBy((c, id, _) => c -> id)
        rows.foreach { case (c, id, original) =>
          Given("the independent row selected only if used by this cached mechanical observation")
          original.fields.size shouldBe 1
          take(call.writeC(c, id, Record.data(original.fields.head.key -> "malformed-complete-payload"), true))
          When("the owning call reads the cached result without preparing new inputs")
          val result = port.loadMechanicalForCallC(query(saved), call.core)
          Then("selected output readability is required, but unused input copies do not gate native results")
          if (usedkeys.contains(c -> id)) result.isSuccess shouldBe false
          else take(result).get.record shouldBe saved.record
          take(call.writeC(c, id, original, true))
          take(port.loadMechanicalForCallC(query(saved), call.core)).get.record shouldBe saved.record
        }
        Given("the selected physical native receipt is missing, while the locator and generated documents remain")
        val nativekey = take(M.keyC(anchor))
        val nativerow = take(call.rowC(declaration.continuations, nativekey)).get
        take(call.deleteFixtureRowC(declaration.continuations, nativekey))
        When("the cached mechanical reader resolves its actual native result")
        val absent = port.loadMechanicalForCallC(query(saved), call.core)
        Then("it refuses instead of trusting a stored application copy or recomputing a receipt")
        absent.isSuccess shouldBe false
        take(call.writeC(declaration.continuations, nativekey, nativerow))
        take(port.loadMechanicalForCallC(query(saved), call.core)).get.record shouldBe saved.record
        assertNative(call, saved.next); assertRows(call, saved)
        Consequence.unit
      }
    }

    "reopen compact and legacy mechanical locators without decoding unused payload copies" in {
      Given("an actually committed native receipt and its generated readable documents")
      val saved = capture()
      val path = Files.createTempDirectory("smwf-mechanical-locator-").resolve("host.sqlite")
      val targets = take(U.bindTargetsC(host(values), declaration))
      val key = take(U.mappingKeyC(saved.record.reference))
      var installed = false
      for (mode <- Vector("compact", "legacy", "legacy-unused-copies", "reopen")) {
        withHost(path) { (call, port) =>
          if (!installed) { take(installC(call, saved)); take(port.executePreparedForCallC(saved, call.core)); installed = true }
          Given("the selected stored locator in " + mode + " form")
          if (mode == "legacy" || mode == "legacy-unused-copies") {
            val encoded = take(J.encodeC(saved.record))
            val json = parser.parse(encoded.substring(J.schema.length + 1)).toOption.get
            val legacy = if (mode == "legacy") encoded else J.schema + ":" + json.mapObject(_.add("payload",
              json.hcursor.downField("payload").focus.get.mapObject(_.remove("snapshot").remove("originalContext")
                .add("originalStart", Json.fromString("unused non-frame copy"))))).noSpaces
            take(call.writeC(targets.publication, key, Record.data(J.storageField -> legacy), true))
          }
          val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
          When("the actual owning call resolves the selected native receipt twice")
          val first = take(port.loadMechanicalForCallC(query(saved), call.core)).get
          val second = take(port.loadMechanicalForCallC(query(saved), call.core)).get
          Then("the canonical computed result is returned without reconstructing or certifying legacy copies")
          first.record shouldBe saved.record
          second.record shouldBe saved.record
          assertNative(call, saved.next)
          call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
          Consequence.unit
        }
      }
    }

    "require declared generated document readability without recertifying its full stored body" in {
      Given("a real native checkpoint whose selected proposal row remains a valid declared document")
      val saved = capture()
      val path = Files.createTempDirectory("smwf-mechanical-document-").resolve("host.sqlite")
      val targets = take(U.bindTargetsC(host(values), declaration))
      var installed = false
      withHost(path) { (call, port) =>
        if (!installed) { take(installC(call, saved)); take(port.executePreparedForCallC(saved, call.core)); installed = true }
        val selected = O.builtDocuments(saved.record.snapshot).head.proposal
        val changed = selected.copy(children = selected.children.updated(0,
          selected.children.head.copy(expectedMinutes = selected.children.head.expectedMinutes + 1)))
        take(call.writeC(targets.proposals, selected.reference.identity,
          Record.data(F.storageField -> take(F.encodeProposalC(changed))), true))
        When("cached observation reads the current declared document, not a whole-body equality certificate")
        val observed = take(port.loadMechanicalForCallC(query(saved), call.core)).get
        Then("the actual native computation remains authoritative and observation appends nothing")
        observed.record shouldBe saved.record
        assertNative(call, saved.next)
        Given("that selected generated proposal is then absent")
        take(call.deleteFixtureRowC(targets.proposals, selected.reference.identity))
        When("the same observation resolves declared output availability")
        val absent = port.loadMechanicalForCallC(query(saved), call.core)
        Then("the missing declared document is refused, not replaced from an unused saved copy")
        absent.isSuccess shouldBe false
        assertNative(call, saved.next)
        take(call.writeC(targets.proposals, selected.reference.identity,
          Record.data(F.storageField -> take(F.encodeProposalC(selected)))))
        Consequence.unit
      }
    }

    "refuse crossed selected addresses and native operation inputs at their owning read boundary" in {
      Given("a real native commit and its selected physical checkpoint rows")
      val saved = capture()
      val path = Files.createTempDirectory("smwf-mechanical-owner-input-").resolve("host.sqlite")
      val targets = take(U.bindTargetsC(host(values), declaration))
      var installed = false
      withHost(path) { (call, port) =>
        if (!installed) {
          take(installC(call, saved))
          take(port.executePreparedForCallC(saved, call.core))
          installed = true
        }
        val nativekey = take(M.keyC(anchor))
        val nativerow = take(call.rowC(declaration.continuations, nativekey)).get
        val prefix = "cncf.workflow-json.v1:"
        val json = parser.parse(nativerow.getString("mechanicalPayload").get.substring(prefix.length)).toOption.get
        val changes = Vector(json.mapObject(_.add("component", Json.fromString("org.example.Foreign"))),
          json.mapObject(o => o.add("operation", o("operation").get.mapObject(_.add("operation", Json.fromString("other"))))),
          json.mapObject(o => o.add("payload", o("payload").get.mapObject(_.add("typeIdentity", Json.fromString("other"))))),
          json.mapObject(o => o.add("reference", o("reference").get.mapObject(_.add("revision", Json.fromString("other"))))))
        changes.foreach { changed =>
          Given("one actually stored incompatible native owner, operation, payload type or selected reference")
          take(call.writeC(declaration.continuations, nativekey,
            Record.data("mechanicalPayload" -> (prefix + changed.noSpaces)), true))
          When("the owning operation resolves the selected native computation")
          val refused = port.loadMechanicalForCallC(query(saved), call.core)
          Then("the CNCF input owner refuses without a downstream copy certificate or native append")
          refused.isSuccess shouldBe false
          assertNative(call, saved.next)
          take(call.writeC(declaration.continuations, nativekey, nativerow, true))
        }
        Given("the physical context index now declares a different selected context revision")
        val indexkey = take(U.indexKeyC(saved.index.instance, saved.index.context))
        val indexrow = take(call.rowC(targets.publication, indexkey)).get
        take(call.writeC(targets.publication, indexkey, Record.data(J.indexStorageField ->
          take(J.encodeIndexC(saved.index.copy(context = saved.index.context.copy(revision = "other"))))), true))
        When("the operation resolves that actually crossed application address")
        val crossed = port.loadMechanicalForCallC(query(saved), call.core)
        Then("selection refuses at the address owner and compatible restored inputs remain readable")
        crossed.isSuccess shouldBe false
        take(call.writeC(targets.publication, indexkey, indexrow, true))
        take(port.loadMechanicalForCallC(query(saved), call.core)).get.record shouldBe saved.record
        assertNative(call, saved.next)
        Consequence.unit
      }
    }

    "observe actual native computation after legacy historical-copy metadata changes and physical reopen" in {
      Given("one real committed mechanical result and an old full native payload with unused input copies")
      val saved = capture()
      val context = saved.record.originalContext
      val partition = context.facts.partition
      val legacy = saved.record.copy(originalContext = context.copy(facts = context.facts.copy(partition = partition.copy(
        estimates = partition.estimates.copy(reference = partition.estimates.reference.copy(revision = "old-copy"))))),
        predecessorRevision = InstanceRevision(41), committedRevision = InstanceRevision(42))
      val path = Files.createTempDirectory("smwf-mechanical-legacy-copy-").resolve("host.sqlite")
      var installed = false
      for (mode <- Vector("owning-read", "physical-reopen")) withHost(path) { (call, port) =>
        if (!installed) {
          take(installC(call, saved))
          take(port.executePreparedForCallC(saved, call.core))
          val key = take(M.keyC(anchor))
          val row = take(call.rowC(declaration.continuations, key)).get
          val prefix = "cncf.workflow-json.v1:"
          val json = parser.parse(row.getString("mechanicalPayload").get.substring(prefix.length)).toOption.get
          val changed = json.hcursor.downField("payload").downField("value")
            .withFocus(_ => J.payloadCodec.encode(legacy)).top.get
          take(call.writeC(declaration.continuations, key, Record.data("mechanicalPayload" -> (prefix + changed.noSpaces)), true))
          installed = true
        }
        When("the operation resolves its actual native receipt on " + mode)
        val observed = take(port.loadMechanicalForCallC(query(saved), call.core)).get
        Then("native transition state and computed output remain authoritative without a historical-copy certificate")
        observed.record shouldBe legacy
        observed.record.snapshot shouldBe saved.record.snapshot
        observed.publicationPostimage shouldBe saved.next
        observed.current shouldBe saved.next
        assertNative(call, saved.next)
        Consequence.unit
      }
    }

    "read passive native frontier data without admitting its crossed snapshot for selected use" in {
      Given("one real mechanical publication whose stored typed snapshot has no objective")
      val saved = capture()
      val passive = saved.record.copy(snapshot = saved.record.snapshot.copy(objective = None))
      val path = Files.createTempDirectory("smwf-mechanical-passive-frontier-").resolve("host.sqlite")
      var installed = false
      for (mode <- Vector("owning-read", "physical-reopen")) withHost(path) { (call, port) =>
        if (!installed) {
          take(installC(call, saved))
          take(port.executePreparedForCallC(saved, call.core))
          val key = take(M.keyC(anchor))
          val row = take(call.rowC(declaration.continuations, key)).get
          val prefix = "cncf.workflow-json.v1:"
          val json = parser.parse(row.getString("mechanicalPayload").get.substring(prefix.length)).toOption.get
          val changed = json.hcursor.downField("payload").downField("value")
            .withFocus(_ => J.payloadCodec.encode(passive)).top.get
          take(call.writeC(declaration.continuations, key,
            Record.data("mechanicalPayload" -> (prefix + changed.noSpaces)), true))
          installed = true
        }
        When("the owning frontier reader resolves passive data and a selected consumer tries use on " + mode)
        val observed = take(port.loadMechanicalForCallC(query(saved), call.core)).get
        val frontier = take(SplitPhaseManagedPlanningFrontier.loadForAdvanceC(query(saved), declaration, f.catalog, call.core))
        val selected = SplitPhaseSelectedDocuments.projectC(frontier.snapshot)
        Then("storage conveys no selected frontier capability and neither observation nor refused use appends")
        observed.record shouldBe passive
        frontier.snapshot shouldBe passive.snapshot
        frontier.original.original.start shouldBe saved.record.originalStart.routing
        frontier.publicationPostimage shouldBe saved.next
        frontier.current shouldBe saved.next
        selected.isSuccess shouldBe false
        observed.current shouldBe saved.next
        assertNative(call, saved.next)
        Consequence.unit
      }
    }

    "refuse an oversized actual native receipt after passive rows succeed without committing effects" in {
      Given("Spec: docs/spec/split-phase-checkpoint-frame-applicability.md; the truthful original Start history followed by an actual later native entry with large causation")
      val initial = SplitPhaseObservedCheckpointFixture.current
      val later = take(initial.appendC(initial.revision, initial.history.head.copy(
        sequence = HistorySequence(2), revision = InstanceRevision(2), causation = CausationReference("x" * 530000))))
      val saved = capture(SplitPhasePlanningCheckpointFixture.bound(current = later))
      val path = Files.createTempDirectory("smwf-mechanical-native-size-").resolve("host.sqlite")
      var installed = false
      for (mode <- Vector("owning-execution", "physical-reopen")) withHost(path, advancecommand = Some(query(saved))) { (call, port) =>
        if (!installed) {
          take(installC(call, saved))
          take(instances.appendForCallC(f.configuration, later.identity, initial.revision, later.history.last, call.core))
          installed = true
        }
        val binding = call.executionContext.dataStoreSpace.captureBinding()
        val targets = take(U.bindTargetsC(call.core.component.get.subsystem.get.configuration, declaration))
        When("the passive publisher prepares consumed rows without a discarded native receipt on " + mode)
        val prepared = U.prepareC(saved, targets)
        Then("actual row preparation succeeds and grants no mutation")
        prepared.isSuccess shouldBe true
        assertNative(call, later)
        _assert_no_checkpoint(call, saved)

        Given("the actual publication rows staged through the owning component storage and canonical native executor")
        val actual = take(prepared)
        val programs = actual.rows.map { row =>
          val storage = take(org.goldenport.cncf.datastore.ComponentRecordStorage.bindForCallC(call.core, targets.storeName, row.collection,
            DataStore.StringEntryId(row.identity)))
          take(storage.stageCreateC(row.payload))
        }
        val program = programs.foldLeft(org.goldenport.ConsequenceT.pure[
          [X] =>> org.goldenport.cncf.Program[org.goldenport.cncf.unitofwork.UnitOfWorkOp, X], Unit](()))(
          (before, next) => before.flatMap(_ => next))
        val native = take(take(org.goldenport.cncf.workflow.WorkflowComponentStorage.bindC(
          ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
          SmWorkflowComponent.componentId, declaration.configuration, Vector(f.catalog.definition),
          declaration.instances, declaration.continuations)).bindMechanicalC(K.advanceOperation, J.payloadCodec))
        When("the actual native executor encodes its complete receipt before consuming staged effects")
        val rejected = native.executeC(saved.mechanicalIntent.get, program)(using call.executionContext)
        Then("the real whole-receipt size boundary is the exact refusal cause and no effect or binding changes")
        rejected match {
          case Consequence.Failure(conclusion) =>
            conclusion.display shouldBe "Mechanical receipt encoding is absent or exceeds its bound"
          case _ => fail("Oversized actual native receipt incorrectly returned success")
        }
        assertNative(call, later)
        _assert_no_checkpoint(call, saved)
        call.executionContext.dataStoreSpace.captureBinding() shouldBe binding

        When("the owning managed checkpoint port independently attempts the same actual native transition")
        val refused = port.executePreparedForCallC(saved, call.core)
        Then("managed refusal also leaves the actual predecessor, receipt absence, all proposed rows and binding unchanged")
        refused.isSuccess shouldBe false
        assertNative(call, later)
        _assert_no_checkpoint(call, saved)
        call.executionContext.dataStoreSpace.captureBinding() shouldBe binding
        Consequence.unit
      }
    }

    "commit 64 actual local Creates with a separate native receipt and retain them after physical reopen" in {
      Given("Spec: docs/spec/split-phase-checkpoint-frame-applicability.md; truthful planning intent and actual SQLite owning dispatch")
      val saved = capture()
      val path = Files.createTempDirectory(Files.createDirectories(Path.of("target", "spec", "native-create-capacity")),
        "run-").resolve("host.sqlite")
      var installed = false
      var committed = false
      for (mode <- Vector("owning-execution", "physical-reopen")) withHost(path, advancecommand = Some(query(saved))) { (call, _) =>
        if (!installed) {
          take(installC(call, saved))
          installed = true
        }
        val binding = call.executionContext.dataStoreSpace.captureBinding()
        val owner = call.core.component.get.subsystem.get
        val targets = take(U.bindTargetsC(owner.configuration, declaration))
        val original = saved.record.originalContext.facts.proposal.proposal
        val rows = Vector.tabulate(64) { number =>
          val identity = s"native-capacity-$number"
          val document = original.copy(reference = original.reference.copy(identity = identity))
          U.Row(targets.proposals, identity, Record.data(F.storageField -> take(F.encodeProposalC(document))))
        }
        rows.size shouldBe 64

        val native = take(take(org.goldenport.cncf.workflow.WorkflowComponentStorage.bindC(
          ComponentCreate(owner, ComponentOrigin.Builtin), SmWorkflowComponent.componentId,
          declaration.configuration, Vector(f.catalog.definition), declaration.instances,
          declaration.continuations)).bindMechanicalC(K.advanceOperation, J.payloadCodec))
        if (!committed) {
          Given("all64 rows staged by the owning ComponentRecordStorage with the genuine unexecuted planning intent")
          val programs = rows.map { row =>
            val storage = take(org.goldenport.cncf.datastore.ComponentRecordStorage.bindForCallC(
              call.core, targets.storeName, row.collection, DataStore.StringEntryId(row.identity)))
            take(storage.stageCreateC(row.payload))
          }
          val program = programs.foldLeft(org.goldenport.ConsequenceT.pure[
            [X] =>> org.goldenport.cncf.Program[org.goldenport.cncf.unitofwork.UnitOfWorkOp, X], Unit](()))(
            (before, next) => before.flatMap(_ => next))
          When("the actual native owner commits64 local Creates and its additional receipt in one transition")
          val completed = take(native.executeC(saved.mechanicalIntent.get, program)(using call.executionContext))
          Then("the real committed native successor is returned without a63-local-row limit")
          completed.receipt.next shouldBe saved.next
          committed = true
        }
        When("the actual owning native reader and document rows are observed on " + mode)
        val retained = take(native.loadRetainedC(saved.mechanicalIntent.get.reference)(using call.executionContext)).get
        val documents = rows.map(row => take(call.rowC(row.collection, row.identity)).get.getString(F.storageField))
        Then("one native receipt and all64 committed document payloads survive repeated dispatch and physical reopen")
        retained.current shouldBe saved.next
        retained.receipt.next shouldBe saved.next
        documents shouldBe rows.map(_.payload.getString(F.storageField))
        take(call.rowC(declaration.continuations, take(M.keyC(saved.mechanicalIntent.get.reference)))).nonEmpty shouldBe true
        assertNative(call, saved.next)
        call.executionContext.dataStoreSpace.captureBinding() shouldBe binding
        Consequence.unit
      }
    }

    "refuse oversized or duplicated actual Create programs after passive preparation without committing effects" in {
      Given("Spec: docs/spec/split-phase-checkpoint-frame-applicability.md; truthful captured planning and an actual SQLite owning call")
      val saved = capture()
      val path = Files.createTempDirectory("smwf-mechanical-create-consumption-").resolve("host.sqlite")
      var installed = false
      for (mode <- Vector("owning-execution", "physical-reopen")) withHost(path) { (call, _) =>
        if (!installed) {
          take(installC(call, saved))
          installed = true
        }
        val binding = call.executionContext.dataStoreSpace.captureBinding()
        val targets = take(U.bindTargetsC(call.core.component.get.subsystem.get.configuration, declaration))
        val baseline = take(U.prepareC(saved, targets))
        val index = take(J.encodeIndexC(saved.index))
        val extra = Vector.tabulate(65 - baseline.rows.size)(number => U.Row(targets.publication,
          s"capacity-$number", Record.data(J.indexStorageField -> index)))
        When("the passive publisher prepares complete overflow and duplicate-target rows on " + mode)
        val overflow = take(U.prepareC(saved, targets, extra))
        val duplicated = take(U.prepareC(saved, targets, Vector(baseline.rows.head)))
        Then("neither actual row vector is truncated or rejected as a speculative execution plan")
        overflow.rows.size shouldBe 65
        duplicated.rows.count(row => row.collection == baseline.rows.head.collection &&
          row.identity == baseline.rows.head.identity) shouldBe 2
        assertNative(call, saved.before)
        _assert_no_checkpoint(call, saved)

        val native = take(take(org.goldenport.cncf.workflow.WorkflowComponentStorage.bindC(
          ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin),
          SmWorkflowComponent.componentId, declaration.configuration, Vector(f.catalog.definition),
          declaration.instances, declaration.continuations)).bindMechanicalC(K.advanceOperation, J.payloadCodec))
        for ((prepared, reason) <- Vector(
          overflow -> "Mechanical program exceeds its bounded Create plan",
          duplicated -> "Mechanical program duplicates a Create target")) {
          Given("actual rows staged through the current owning component storage, not a fabricated provider or receipt")
          val programs = prepared.rows.map { row =>
            val storage = take(org.goldenport.cncf.datastore.ComponentRecordStorage.bindForCallC(
              call.core, targets.storeName, row.collection, DataStore.StringEntryId(row.identity)))
            take(storage.stageCreateC(row.payload))
          }
          val program = programs.foldLeft(org.goldenport.ConsequenceT.pure[
            [X] =>> org.goldenport.cncf.Program[org.goldenport.cncf.unitofwork.UnitOfWorkOp, X], Unit](()))(
            (before, next) => before.flatMap(_ => next))
          When("the actual native mechanical owner consumes the staged program on " + mode)
          val rejected = native.executeC(saved.mechanicalIntent.get, program)(using call.executionContext)
          Then("the consuming-program refusal has its exact cause and commits no native, receipt or proposed-row effect")
          rejected match {
            case Consequence.Failure(conclusion) => conclusion.display shouldBe reason
            case _ => fail("Invalid actual Create program incorrectly returned success")
          }
          assertNative(call, saved.before)
          _assert_no_checkpoint(call, saved)
          prepared.rows.foreach(row => take(call.rowC(row.collection, row.identity)) shouldBe None)
          call.executionContext.dataStoreSpace.captureBinding() shouldBe binding
        }
        Consequence.unit
      }
    }

    "leave publication absent when the actual native predecessor advances after private capture" in {
      Given("a valid constructed capture whose real native instance subsequently advances")
      val saved = capture()
      val path = Files.createTempDirectory("smwf-mechanical-stale-cas-").resolve("host.sqlite")
      var installed = false
      var advanced = Option.empty[InstanceRecord]
      withHost(path) { (call, port) =>
        if (!installed) {
          take(installC(call, saved))
          val entry = saved.before.history.last.copy(sequence = HistorySequence(2), revision = InstanceRevision(2),
            causation = CausationReference("actual-other-transition"))
          advanced = Some(take(instances.appendForCallC(f.configuration, saved.before.identity,
            saved.before.revision, entry, call.core)))
          installed = true
        }
        When("the stale capture attempts its actual atomic native transition without a separate copy preflight")
        val rejected = port.executePreparedForCallC(saved, call.core)
        Then("the transition owner rejects the stale predecessor and commits no receipt or generated rows")
        rejected.isSuccess shouldBe false
        assertNative(call, advanced.get)
        _assert_no_checkpoint(call, saved)
        Consequence.unit
      }
    }

  }
}
