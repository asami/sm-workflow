package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
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
  SplitPhaseManagedScopeProjectionCheckpoint as NM}
import SplitPhasePlanningCheckpointFixture.{f, anchor}
import SplitPhaseObservedCheckpointFixture.*
import SplitPhaseExplicitPartitionFixture.take

/** Real SQLite/native calls with explicitly private setup probes. Selected
  * scenarios install the actual public continuation pair; seeded Start rows
  * still do not establish production human ingress or default installation.
  *
  * @since Oct.  2, 2026
  * @version Oct.  5, 2026
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
  private def query(saved: K.Captured) = SplitPhaseAdvanceCommand(saved.before.identity.value,
    SplitPhasePartitionContextReference(saved.record.originalContext.reference.identity, saved.record.originalContext.reference.revision))
  private def originalRows(saved: K.Captured): Vector[(DataStore.CollectionId, String, Record)] = {
    _original_rows(O.Original(saved.record.originalContext, saved.record.originalStart, saved.record.originalContext.facts))
  }
  private def _original_rows(original: O.Original): Vector[(DataStore.CollectionId, String, Record)] = {
    val start = original.start; val context = original.context; val facts = original.facts
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

  private def withHost(path: Path, declared: Map[String, ConfigurationValue] = values,
    denied: Option[DataStore.CollectionId] = None, operationName: String = K.advanceOperation.operation,
    participant: String = "participant", rowCheck: () => Consequence[Unit] = () => Consequence.unit,
    publicadvance: Boolean = false, retainedmechanical: Option[(K.Record, InstanceRecord)] = None,
    executionclock: Option[java.time.Clock] = None,
    humanverifier: Option[SplitPhaseHumanAnswerVerification.Verifier] = None,
    humanreadback: () => Consequence[Unit] = () => Consequence.unit,
    terminalreadback: () => Consequence[Unit] = () => Consequence.unit,
    reasoningcompletion: Boolean = false,
    processruntime: Option[(org.goldenport.cncf.processexecution.ProcessExecutionDriver,
      org.goldenport.cncf.processexecution.ProcessExecutionAdmission)] = None)
    (task: (Probe, Managed.Port) => Consequence[Unit]): Unit = {
    // A process-enabled host needs the same canonical WorkArea prerequisite as
    // the normal runtime. ExecutionContext.create() alone is intentionally inert.
    if (processruntime.nonEmpty) org.goldenport.cncf.context.GlobalContext.set(
      org.goldenport.cncf.context.GlobalContext(org.goldenport.cncf.workarea.WorkAreaSpace.create(
        org.goldenport.cncf.config.RuntimeConfig.default)))
    val ctx = ExecutionContext.create()
    val configured = host(declared ++ Map("textus.subsystem.user-mode" -> ConfigurationValue.StringValue("standalone"),
      "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"), "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)))
    val policy = take(EventPersistencePolicy.createC("managed-checkpoint-v1", Vector(
      EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
    val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"), collection("indexes"),
      Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId, "workflow", declaration.instances, declaration.continuations))))
    val targets = take(U.bindTargetsC(configured, declaration))
    val humanpublications = if (declared.contains(SplitPhaseHumanResolutionCheckpointPublication.prefix + "record-collection"))
      Vector(take(SplitPhaseHumanResolutionCheckpointPublication.bindTargetsC(configured, declaration)).publication)
      else Vector.empty[DataStore.CollectionId]
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
                  port.loadMechanicalForCallC(query(capture()), call.core).flatMap {
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
      val wire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(capture())))
      take(owner.executeOperationResponseInContext(Request.of(component = built.name, service = "SplitPhaseService", operation = probeoperationname,
        arguments = List(Argument("arg1", wire))), actor))
      take(new CncfRuntime().executeCommandResponse(owner, Array(s"${built.name}/SplitPhaseService/$probeoperationname", "--json", wire)))
      captured.size shouldBe 2
      retainedmechanical match {
        case None => port.loadMechanicalForCallC(query(capture()), captured.last).isSuccess shouldBe false
        case Some(_) =>
          When("the captured owning core is reused after both managed dispatches have ended")
          val rejected = port.loadMechanicalForCallC(query(capture()), captured.last)
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
    val publication = take(NU.prepareC(captured, targets, f.catalog.definition))
    val repeated = take(NU.prepareC(captured, targets, f.catalog.definition))
    val mapping = publication.rows.last
    val decoded = take(NJ.decodeC(captured.record.reference, mapping.payload.getString(NJ.storageField).get, f.catalog.definition))
    val catalog = take(org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1.catalogC(Vector(f.catalog.definition)))
    val native = take(org.goldenport.cncf.workflow.WorkflowMechanicalTransitionJsonV1.decodeC(
      publication.anticipatedNativeBytes, catalog, NJ.payloadCodec(f.catalog.definition)))
    val actualnewrows = publication.rows.map(row => take(call.rowC(row.collection, row.identity)))
    val retainedafter = retained.map { (collection, identity) => take(call.rowC(collection, identity)).get }
    val rootafter = take(call.rowC(targets.publication, rootkey)).get
    val indexafter = take(call.rowC(targets.publication, indexkey)).get
    val nativeafter = take(instances.loadForCallC(f.configuration, captured.record.before.identity, call.core)).get
    Then("only new Built documents and the complete immutable successor mapping are proposed, with no write or grant")
    publication.captured shouldBe captured
    publication.targets shouldBe targets
    publication.rows.dropRight(1) shouldBe expectedrows
    publication.rows.size shouldBe newdocuments.size * 3 + 1
    captured.record.snapshot.frontier match {
      case NV.Frontier.HumanDirectionRequired => publication.rows.size shouldBe 1
      case _: NV.Frontier.Applied =>
        newdocuments.nonEmpty shouldBe true
        retained.nonEmpty shouldBe true
    }
    publication.rows.size + NU.NATIVE_RECEIPT_CREATES should be <= NU.MAX_TOTAL_CREATES
    mapping.collection shouldBe targets.publication
    mapping.identity shouldBe take(NC.keyC(captured.record.reference))
    mapping.identity should not be rootkey
    mapping.identity should not be indexkey
    mapping.payload.fields.map(_.key) shouldBe Vector(NJ.storageField)
    decoded shouldBe captured.record
    native.payload.value shouldBe captured.record
    native.current shouldBe captured.record.before
    native.next shouldBe captured.record.next
    repeated.rows shouldBe publication.rows
    repeated.anticipatedNativeBytes shouldBe publication.anticipatedNativeBytes
    actualnewrows.foreach(_ shouldBe None)
    retainedafter shouldBe retainedbefore
    rootafter shouldBe rootbefore
    indexafter shouldBe indexbefore
    nativeafter shouldBe nativebefore
    call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding

    Given("fixed scope purposes and structural descriptors whose count excludes the additional native receipt")
    val capacityrows = Vector.tabulate(64)(index => U.Row(targets.publication, s"capacity-$index",
      Record.data(NJ.storageField -> "structural-descriptor-not-a-committed-frame")))
    val crossed = Vector(capacityrows.head.copy(collection = targets.instances),
      capacityrows.head.copy(collection = targets.estimates),
      capacityrows.head.copy(payload = Record.data(J.indexStorageField -> "not-a-successor-field")),
      capacityrows.head.copy(identity = "foreign\nidentity"),
      capacityrows.head.copy(payload = Record.data(NJ.storageField -> ("語" * (NJ.maximumPayloadBytes / 3 + 1)))))
    val compileddefinition = take(SplitPhaseDefinitionBinding.bindC)
    When("capacity, duplicate, crossed-purpose, payload and absent-source descriptors are admitted structurally")
    val capacityaccepted = NU.validateRowsC(capacityrows.take(63), targets)
    val capacityrefused = NU.validateRowsC(capacityrows, targets)
    val duplicated = NU.validateRowsC(Vector(capacityrows.head, capacityrows.head), targets)
    val purposefailures = crossed.map(row => NU.validateRowsC(Vector(row), targets))
    val missing = Vector(NU.validateRowsC(null, targets), NU.validateRowsC(capacityrows.take(1), null))
    val preparationfailures = Vector(NU.prepareC(null, targets, f.catalog.definition),
      NU.prepareC(captured, null, f.catalog.definition), NU.prepareC(captured, targets, null),
      NU.prepareC(captured, targets, compileddefinition))
    val afterrefusals = take(instances.loadForCallC(f.configuration, captured.record.before.identity, call.core)).get
    Then("63 local descriptors fit the64 TOTAL limit while all inconsistent data is refused without native mutation")
    capacityaccepted shouldBe Consequence.unit
    capacityrefused shouldBe a[Consequence.Failure[?]]
    duplicated shouldBe a[Consequence.Failure[?]]
    purposefailures.foreach(_ shouldBe a[Consequence.Failure[?]])
    missing.foreach(_ shouldBe a[Consequence.Failure[?]])
    preparationfailures.foreach(_ shouldBe a[Consequence.Failure[?]])
    afterrefusals shouldBe nativebefore
    call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
    assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseScopeProjectionCheckpointPublication.Prepared(null, null, Vector.empty, \"\")")
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
    val judgment = S.Judgment(input.context, decision, "requested-semantics-only",
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
      _original_rows(O.Original(context, saved.record.originalStart, facts)).foreach { case (c, id, row) =>
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
    val judgment = SS.Judgment(input.context, input.checkpoint, decision, "managed publication transfer analysis",
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
    val root = take(J.decodeC(index.mapping, take(call.rowC(targets.publication,
      take(U.mappingKeyC(index.mapping)))).get.getString(J.storageField).get))
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
    Given("complete interpretation data, separately seeded as fixture data rather than canonical consent")
    if (take(call.rowC(dataid, record.reference.identity)).isEmpty)
      take(call.writeC(dataid, record.reference.identity, Record.data(HJ.storageField -> take(HJ.encodeStorageC(record)))))
    When("the real owning DataStore reads the retained interpretation field")
    val storedrow = take(call.rowC(dataid, record.reference.identity)).get
    Then("the value remains an opaque String with the complete exact storage representation")
    storedrow.fields.find(_.key == HJ.storageField).get.value.single.isInstanceOf[String] shouldBe true
    storedrow.getString(HJ.storageField) shouldBe Some(take(HJ.encodeStorageC(record)))
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
    Then("the full historical inputs, ordered frontier and downstream result remain distinct from an AI scope record")
    capture.priorScope shouldBe observed.plan.source.record
    capture.humanQuestion shouldBe observed.plan.question
    capture.humanCompletion shouldBe observed.completion
    capture.reference shouldBe take(SplitPhaseHumanResolutionCheckpointPreparation.referenceC(
      observed.completion.result.continuationId, observed.completion.result.resultReference))
    take(SplitPhaseHumanResolutionCheckpointPreparation.keyC(capture.reference))
      .getBytes(java.nio.charset.StandardCharsets.UTF_8).length should be <= 1024
    capture.humanIssued shouldBe observed.plan.next
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
      TypedValue(SplitPhaseHumanResolutionCheckpointPreparation.payloadType, capture))
    assertNative(call, observed.answered)
    documents.foreach { (collection, identity, prior) => take(call.rowC(collection, identity)) shouldBe prior }
    call.executionContext.dataStoreSpace.captureBinding() shouldBe binding

    Given("the complete captured calculation and an independently selected generated definition, with no publication grant")
    val humanjson = SplitPhaseHumanResolutionCheckpointJsonV1
    val inspection = SplitPhaseHumanResolutionCheckpointInspection
    val definition = f.catalog.definition
    val fixtureid = "human-checkpoint-codec-fixture"
    When("the owning fixture stores and reads only the opaque complete codec text through the real shared-pool DataStore")
    val wire = take(humanjson.encodeC(capture, definition))
    if (take(call.rowC(dataid, fixtureid)).isEmpty)
      take(call.writeC(dataid, fixtureid, Record.data(humanjson.storageField -> wire)))
    val row = take(call.rowC(dataid, fixtureid)).get
    val restored = take(humanjson.decodeC(capture.reference, row.getString(humanjson.storageField).get, definition))
    val nativewire = take(humanjson.encodeNativeC(capture, definition))
    val nativecatalog = take(org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1.catalogC(Vector(definition)))
    val receipt = take(org.goldenport.cncf.workflow.WorkflowMechanicalTransitionJsonV1.decodeC(
      nativewire, nativecatalog, humanjson.payloadCodec(definition)))
    Then("the complete data and native-envelope projection roundtrip without a native append, application record or private proof")
    row.fields.find(_.key == humanjson.storageField).get.value.single.isInstanceOf[String] shouldBe true
    row.getString(humanjson.storageField) shouldBe Some(wire)
    take(humanjson.encodeC(restored, definition)) shouldBe wire
    take(humanjson.encodeC(receipt.payload.value, definition)) shouldBe wire
    take(NJ.encodeC(restored.priorScope, definition)) shouldBe take(NJ.encodeC(capture.priorScope, definition))
    restored.humanQuestion shouldBe capture.humanQuestion
    restored.humanCompletion shouldBe capture.humanCompletion
    restored.resolution shouldBe capture.resolution
    restored.frontier shouldBe capture.frontier
    restored.downstream shouldBe capture.downstream
    SplitPhaseDefinitionBinding.sameRecord(restored.humanIssued, capture.humanIssued) shouldBe true
    SplitPhaseDefinitionBinding.sameRecord(restored.before, capture.before) shouldBe true
    SplitPhaseDefinitionBinding.sameRecord(restored.next, capture.next) shouldBe true
    receipt.reference shouldBe M.Reference(capture.reference.identity, capture.reference.revision)
    receipt.component shouldBe SmWorkflowComponent.componentId
    receipt.operation shouldBe K.advanceOperation
    inspection.inspectC(restored, definition).isSuccess shouldBe true
    assertNative(call, observed.answered)
    documents.foreach { (collection, identity, prior) => take(call.rowC(collection, identity)) shouldBe prior }
    call.executionContext.dataStoreSpace.captureBinding() shouldBe binding

    Given("crossed canonical/native/data/frontier joins and closed-frame corruptions of the same complete checkpoint")
    val passive = Vector(capture.copy(reference = capture.reference.copy(revision = "foreign")),
      capture.copy(humanIssued = capture.before), capture.copy(before = capture.next), capture.copy(next = capture.before),
      capture.copy(humanCompletion = capture.humanCompletion.copy(instanceRevision = capture.next.revision)),
      capture.copy(resolution = capture.resolution.copy(decision = HR.DecisionReference("foreign-decision"))),
      capture.copy(resolution = capture.resolution.copy(reference = capture.resolution.reference.copy(revision = "foreign"))),
      capture.copy(frontier = capture.frontier.tail),
      capture.copy(frontier = capture.frontier.updated(0, capture.frontier.head.copy(output =
        capture.frontier.head.output match {
          case NV.CandidateOutput.Built(value, published) => NV.CandidateOutput.Built(value, !published)
          case other => other
        }))),
      capture.copy(frontier = capture.frontier.updated(0, capture.frontier.head.copy(transfers = Vector.empty))))
    val frame = parser.parse(wire.substring(humanjson.schema.length + 1)).toOption.get
    val corrupt = Vector(frame.mapObject(_.add("extra", Json.Null)),
      frame.hcursor.downField("payload").withFocus(_.mapObject(_.add("extra", Json.Null))).top.get,
      frame.hcursor.downField("payload").downField("humanQuestion").withFocus(_.mapObject(_.add("extra", Json.Null))).top.get)
    val malformed = Vector(wire.substring(humanjson.schema.length + 1), "foreign:" + frame.noSpaces,
      humanjson.schema + ":" + "x" * humanjson.maximumPayloadBytes,
      humanjson.schema + ":" + 0xd800.toChar)
    When("passive inspection and the complete-frame codec consume those crossed or malformed facts")
    val refuseddata = passive.map(value => inspection.inspectC(value, definition))
    val refusedencoding = passive.map(value => humanjson.encodeC(value, definition))
    val refusedframes = (corrupt.map(value => humanjson.schema + ":" + value.noSpaces) ++ malformed)
      .map(value => humanjson.decodeC(capture.reference, value, definition))
    val foreignselected = humanjson.decodeC(capture.reference.copy(revision = "foreign"), wire, definition)
    val absentdefinition = humanjson.decodeC(capture.reference, wire, null)
    val absentrecord = inspection.inspectC(null, definition)
    Then("all contradictions refuse without shortening data, effects, a substitute record or execution permission")
    refuseddata.foreach(_.isSuccess shouldBe false)
    refusedencoding.foreach(_.isSuccess shouldBe false)
    refusedframes.foreach(_.isSuccess shouldBe false)
    foreignselected.isSuccess shouldBe false
    absentdefinition.isSuccess shouldBe false
    absentrecord.isSuccess shouldBe false
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
          take(SplitPhaseExplicitBoundaryValidation.loadC(originaltemplate.shape, records, records))
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
            val port = take(NM.bindC(owner.configuration, declaration, f.catalog))
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
              if (scenario == "collision") before.get else record.next)
            routingroot shouldBe rootrow
            routingindex shouldBe indexrow
            call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            if (expected.isEmpty) {
              When("the genuine owning completion is captured without publication")
              val captured = take(NC.captureC(take(scope.prepareProjectionForCallC(query(saved), call.core))))
              val descriptors = take(NU.prepareC(captured, targets, f.catalog.definition))
              val absent = take(port.loadForCallC(query(saved), call.core))
              Then("exact first absence is not a grant and the full capture has the actual first root")
              absent shouldBe None
              captured.record.snapshot.first shouldBe first.get
              captured.record.before shouldBe before.get
              descriptors.rows.size + 1 should be <= NU.MAX_TOTAL_CREATES
              expected = Some(captured.record)
              if (scenario == "collision") {
                Given("an independently existing new-document row, or a malformed immutable Human mapping")
                val collided = if (decision == SplitPhaseScopeSemantic.Decision.Enriched) descriptors.rows.head
                  else descriptors.rows.last.copy(payload = Record.data(NJ.storageField -> "malformed"))
                take(call.writeC(collided.collection, collided.identity, collided.payload))
                collisionrow = Some(collided)
              }
            }
            val expectedrecord = expected.get
            val reference = M.Reference(expectedrecord.reference.identity, expectedrecord.reference.revision)
            val nativekey = take(M.keyC(reference))
            if (scenario == "collision") {
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
              When("the owning port publishes or independently recovers the immutable saved successor twice")
              val observed = take(port.executeForCallC(query(saved), call.core))
              val repeated = take(port.executeForCallC(query(saved), call.core))
              val loaded = take(port.loadForCallC(query(saved), call.core)).get
              val native = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
              Then("complete native/application/documents agree and repeated or reopened calls append nothing")
              observed.record shouldBe expectedrecord
              repeated.record shouldBe expectedrecord
              loaded.record shouldBe expectedrecord
              observed.current shouldBe expectedrecord.next
              native shouldBe expectedrecord.next
              native.history.dropRight(1) shouldBe before.get.history
              native.lifecycle shouldBe Lifecycle.Active
              native.suspension shouldBe None
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
              Given("a same-reference published proposal whose complete body changes independently")
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
              val drifted = document.proposal.copy(children = document.proposal.children.updated(0,
                document.proposal.children.head.copy(expectedMinutes = document.proposal.children.head.expectedMinutes + 1)))
              take(call.writeC(targets.proposals, document.proposal.reference.identity,
                Record.data(F.storageField -> take(F.encodeProposalC(drifted))), update = true))
              When("retained load and execution independently observe the altered document body")
              val driftload = port.loadForCallC(query(saved), call.core)
              val driftexecute = port.executeForCallC(query(saved), call.core)
              Then("matching references alone do not establish complete readback and no new native append occurs")
              driftload.isSuccess shouldBe false
              driftexecute.isSuccess shouldBe false
              assertNative(call, expectedrecord.next)
              Given("the private fixture restores its exact original proposal bytes after the refusal")
              take(call.writeC(targets.proposals, document.proposal.reference.identity,
                Record.data(F.storageField -> take(F.encodeProposalC(document.proposal))), update = true))
              When("the complete original committed data is independently observed again")
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
    val request = question.current.asInstanceOf[WorkflowContinuation.Decision[SplitPhaseNativeHumanDirection.Input]].request
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
        val verifier = new V.Verifier {
          val issuerIdentity = "independent-managed-human-spec"
          def verifyForCallC(reference: V.EvidenceReference, expected: V.Binding, core: ActionCall.Core) = {
            checks += 1
            expected.question shouldBe question.get
            expected.result shouldBe submitted.get
            expected.submittingParticipant shouldBe "participant"
            reference shouldBe V.EvidenceReference("independent-human-consent", "1")
            if (!allowed) Consequence.success(None)
            else {
              val now = core.executionContext.clock.instant()
              Consequence.success(Some(V.Receipt(reference, issuerIdentity, "independent-human",
                V.purpose, expected, now, now.plusSeconds(60))))
            }
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
        withHost(path, declared, publicadvance = true, humanverifier = Some(verifier), humanreadback = observation,
          terminalreadback = terminalobservation) { (call, _) =>
          if (!initialized) {
            _complete_scope_fixture(call, saved, if (correction) SplitPhaseScopeSemantic.Decision.Enriched
              else SplitPhaseScopeSemantic.Decision.DecisionNeeded, partitionroot, broadreceiver = correction)
            When("the actual public advance publishes the complete frontier and issues its Human Decision")
            val wire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved)))
            val issued = take(WorkflowDecisionJsonV1.decodeC(_scalar(take(_public_advance_c(call, wire, partitionroot))), H.inputCodec))
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
            val resultwire = take(WorkflowResultJsonV1.encodeC(submitted.get, A.payloadCodec))
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
          When("the same question is offered a changed answer or a different independent evidence reference")
          val alternative = if (answer == A.Answer.Cancel) A.Answer.Defer else A.Answer.Cancel
          val conflicting = Vector(submitted.get.copy(result = TypedValue(A.resultType, alternative)),
            submitted.get.copy(resultReference = ContextReference("different-consent", "1")))
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
        withHost(path, retainedvalues, publicadvance = true, humanverifier = Some(verifier), humanreadback = observation,
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
        withHost(path, retainedvalues, participant = "foreign", publicadvance = true, humanverifier = Some(verifier), humanreadback = observation,
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
            withHost(path, declared) { (call, _) =>
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
                When("the owning native observer restores the answer after later progress and fresh capture receives its calculation")
                val observed = take(SplitPhaseManagedHumanCompletion.observeAnswerForAdvanceC(query(saved), declaration, f.catalog, call.core))
                val reader = take(SplitPhaseHumanResolutionReadPolicy.bindC(call.core.component.get.subsystem.get.configuration, declaration))
                val data = take(reader.loadForCallC(SplitPhaseHumanScopeResolution.Reference("independent-resolution", "1"), call.core)).get
                val admitted = take(SplitPhaseHumanScopeResolution.admitC(observed, data))
                val frontier = take(SplitPhaseExplicitCandidateProjection.applyHumanResolutionC(admitted))
                val calculation = take(W.continueHumanResolutionC(frontier))
                val refused = SplitPhaseHumanResolutionCheckpointPreparation.captureC(calculation)
                Then("valid historical observation does not permit a second fresh successor or alter the later history")
                observed.current shouldBe later
                observed.answered shouldBe current
                calculation shouldBe a[W.HumanResolutionContinued]
                refused.isSuccess shouldBe false
                assertNative(call, later)
                checks shouldBe 2
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
            withHost(path, declared) { (call, _) =>
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
                When("the pure publisher prepares all and only new Built Creates, plus the complete application frame")
                val prepared = take(HP.prepareC(captured, targets, f.catalog.definition))
                expected = Some(captured.record)
                val newbuilt = captured.record.frontier.collect { case NV.Candidate(_, _, NV.CandidateOutput.Built(value, false)) => value }
                Then("previous Built documents are preserved and capacity includes the extra native receipt")
                prepared.rows.size shouldBe newbuilt.size * 3 + 1
                prepared.rows.filterNot(_.collection == targets.publication).map(_.identity).toSet shouldBe
                  newbuilt.flatMap(value => Vector(value.proposal.reference.identity, value.ownership.reference.identity,
                    value.handoffs.reference.identity)).toSet
                When("the complete successor is independently interpreted as a native frame")
                val nativebytes = take(HJ.encodeNativeC(captured.record, f.catalog.definition))
                Then("the publisher anticipates that complete native frame")
                prepared.anticipatedNativeBytes shouldBe nativebytes
                Given("sixty-three complete side-row descriptors and the extra native receipt")
                val capacity = Vector.tabulate(63)(index => U.Row(targets.documents.proposals, s"capacity-$index",
                  Record.data(F.storageField -> "complete-descriptor-fixture")))
                When("the complete side-row set is interpreted against the total Create limit")
                val acceptedcapacity = HP.validateRowsC(capacity, targets)
                Then("sixty-three side rows leave room for the native receipt")
                acceptedcapacity.isSuccess shouldBe true
                When("one further side row is interpreted against that same total limit")
                val exceededcapacity = HP.validateRowsC(capacity :+ U.Row(targets.documents.proposals, "capacity-63",
                  Record.data(F.storageField -> "complete-descriptor-fixture")), targets)
                Then("sixty-four side rows cannot also accommodate the native receipt")
                exceededcapacity.isSuccess shouldBe false
                When("a duplicate side-row identity is interpreted")
                val duplicatecapacity = HP.validateRowsC(capacity :+ capacity.head, targets)
                Then("duplicate Create identities are refused")
                duplicatecapacity.isSuccess shouldBe false
                When("a side row claims the protected native instance purpose")
                val nativealias = HP.validateRowsC(Vector(capacity.head.copy(collection = declaration.instances)), targets)
                Then("side-row publication cannot alias the native instance purpose")
                nativealias.isSuccess shouldBe false
                When("an otherwise complete row has an oversized identity")
                val oversizedidentity = HP.validateRowsC(Vector(capacity.head.copy(identity = "x" * 1025)), targets)
                Then("the oversized Create identity is refused")
                oversizedidentity.isSuccess shouldBe false
                When("a complete row payload contains a malformed UTF-8 surrogate")
                val malformedpayload = HP.validateRowsC(Vector(capacity.head.copy(payload = Record.data(F.storageField -> 0xd800.toChar.toString))), targets)
                Then("the malformed payload is refused")
                malformedpayload.isSuccess shouldBe false
                Given("the complete reserved native, input and document collection purposes")
                val reserved = targets.documents.localCollections ++ Vector(declaration.instances, declaration.continuations,
                  collection("contexts"), collection("starts"), collection("selections"), collection("humanresolutions")) ++
                  F.Purpose.values.toVector.map(target)
                reserved.distinct.foreach { alias =>
                  When(s"the Human publication collection is interpreted as reserved purpose ${alias.print}")
                  val aliased = HP.bindTargetsC(host(declared.updated(HP.prefix + "record-collection", ConfigurationValue.StringValue(alias.print))),
                    declaration)
                  Then("publication purpose aliasing is refused")
                  aliased.isSuccess shouldBe false
                }
                When("publication targets are interpreted without fresh Human input metadata")
                val retainedtargets = HP.bindTargetsC(host(declared.filterNot((key, _) => key.startsWith(SplitPhaseHumanResolutionReadPolicy.prefix))), declaration)
                Then("the retained observation configuration remains admissible")
                retainedtargets.isSuccess shouldBe true
                When("the publisher receives no captured successor")
                val missingcapture = HP.prepareC(null, targets, f.catalog.definition)
                Then("missing capture is refused before publication")
                missingcapture.isSuccess shouldBe false
                When("the owning port observes the still-unpublished Human successor")
                val absent = take(port.loadForCallC(query(saved), call.core))
                Then("absence is preserved with unchanged canonical native state")
                absent shouldBe None
                assertNative(call, humananswer.answered)

                if (publication == "publication-collision") {
                  Given("an independently existing row at one newly required Built Create identity")
                  val collision = prepared.rows.find(_.collection != targets.publication).get
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
                    Then("the owning publication returns the complete captured successor")
                    take(result).record shouldBe captured.record
                  }
                  When("the owner independently reads back the committed native successor, application and Built documents")
                  val observed = take(port.loadForCallC(query(saved), call.core)).get
                  Then("the complete committed result matches the captured successor")
                  observed.record shouldBe captured.record
                  observed.current shouldBe captured.record.next
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
                  Given("that same native Human publication and independently valid proofs owned by another repository")
                  val nativehuman = take(SplitPhaseExplicitCandidateProjection.restorePublishedHumanC(observed))
                  val originalduration = take(SplitPhaseExplicitCandidateAdmissibility.restoreHumanC(nativehuman))
                    .asInstanceOf[SplitPhaseExplicitCandidateAdmissibility.DurationRanked]
                  val foreignrepository = SplitPhaseExplicitBoundaryValidation.RepositoryIdentity("different-retained-human-repository")
                  val crossedproofs = originalduration.input.boundaries.map(_.map { proof =>
                    val changed = proof.records.copy(ownership = proof.records.ownership.copy(
                      children = proof.records.ownership.children.map(child => child.copy(repository = foreignrepository))))
                    take(SplitPhaseExplicitBoundaryValidation.loadC(proof.shape, changed, changed))
                  })
                  When("a duration admission with the same enumeration but those other ownership proofs is supplied to objective restoration")
                  val crossedinput = take(SplitPhaseExplicitCandidateAdmissibility.loadC(originalduration.input.enumerated, crossedproofs))
                  val crossedduration = take(SplitPhaseExplicitCandidateAdmissibility.assessC(crossedinput))
                    .asInstanceOf[SplitPhaseExplicitCandidateAdmissibility.DurationRanked]
                  val crossedrestoration = objectives.restoreHumanC(nativehuman, crossedduration)
                  Then("the complete native admission is required before any copied objective can be returned")
                  crossedduration.input.enumerated shouldBe originalduration.input.enumerated
                  crossedduration.input.boundaries.flatMap(_.toVector.flatMap(_.records.ownership.children.map(_.repository)))
                    .distinct shouldBe Vector(foreignrepository)
                  crossedduration.input.boundaries.map(_.map(_.records)) should not be (
                    originalduration.input.boundaries.map(_.map(_.records)))
                  crossedrestoration match {
                    case Consequence.Failure(conclusion) =>
                      conclusion.display shouldBe "SplitPhase objective restoration requires the same complete native Human admission"
                    case _ => fail("Objective restoration incorrectly accepted ownership proofs from another repository")
                  }
                  When("the planner receives no owning Human publication observation")
                  val missingobservation = W.restorePublishedHumanC(null)
                  Then("passive data or absence cannot supply native continuation provenance")
                  missingobservation.isSuccess shouldBe false
                  prepared.rows.foreach { row =>
                    val stored = take(call.rowC(row.collection, row.identity)).get
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
                Given("a corrupt retained application frame after successful production publication")
                val targets = take(HP.bindTargetsC(owner, declaration))
                val key = take(HK.keyC(expected.get.reference))
                val original = take(call.rowC(targets.publication, key)).get
                take(call.writeC(targets.publication, key, Record.data(HJ.storageField -> "corrupt-retained-frame"), update = true))
                When("both retained observation and execution encounter the contradictory publication")
                val corruptobservation = port.loadForCallC(query(saved), call.core)
                Then("retained observation refuses the corrupt frame")
                corruptobservation.isSuccess shouldBe false
                When("retained execution encounters the same contradictory publication")
                val corruptexecution = port.executeForCallC(query(saved), call.core)
                Then("neither path falls back to fresh input or mutates canonical native progress")
                corruptexecution.isSuccess shouldBe false
                assertNative(call, later.get)
                Given("the original valid retained application frame is restored")
                take(call.writeC(targets.publication, key, original, update = true))
                When("the owner observes the restored retained publication")
                val restored = take(port.loadForCallC(query(saved), call.core)).get
                Then("the original complete result remains observable without new effects")
                restored.record shouldBe expected.get
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
            Given("an independently supplied receipt bound to the entire issued question, result and original participant")
            val submitted = ContinuationResult(question.get.handle, request.runId, request.continuationId, request.expectedRevision,
              request.context.snapshot, TypedValue(A.resultType, answer), ContextReference("independent-human-answer", "3"),
              Vector.empty, ExecutionEvidence(Vector(ContextReference("independent-human-answer", "3")), Some("participant")))
            val expected = HV.Binding(call.core.component.get.core.instanceId, question.get, submitted, "participant")
            val receipt = HV.Receipt(HV.EvidenceReference("independent-human-answer", "3"), "independent-provider", "human-user",
              HV.purpose, expected, now, now.plusSeconds(60))
            def _verifier_(value: Option[HV.Receipt]): HV.Verifier = new HV.Verifier {
              val issuerIdentity = "independent-provider"
              def verifyForCallC(reference: HV.EvidenceReference, binding: HV.Binding, core: ActionCall.Core) = {
                reference shouldBe receipt.reference
                binding shouldBe expected
                (core eq call.core) shouldBe true
                Consequence.success(value)
              }
            }
            When("the current-call answer is admitted using the independent host verifier")
            val verified = take(HA.admitForCallC(plan.get, submitted, current, call.core, _verifier_(Some(receipt))))
            val observed = take(verified.evidence.receiptForCallC(call.core))
            val expectednext = take(HA.prepareC(plan.get, submitted, current))
            Then("only the exact question/result receives a call-bound receipt and pure postimage, not a storage effect")
            observed shouldBe receipt
            verified.next shouldBe expectednext
            previous.foreach { case (admitted, priorcore) =>
              When("evidence retained from a previous actual managed call is used in this distinct call")
              val crossed = admitted.receiptForCallC(call.core)
              Then("the earlier call's admission does not transfer by equal question or data")
              (priorcore eq call.core) shouldBe false
              crossed.isSuccess shouldBe false
            }
            currentadmitted = Some(verified.evidence)
            When("a different authenticated relay identity is claimed for the same actual current call")
            val foreign = HV.admitForCallC(expected.copy(submittingParticipant = "foreign"), call.core, _verifier_(Some(receipt)))
            Then("authentication of the original relay does not admit another participant or call the independent verifier")
            foreign.isSuccess shouldBe false
            Given("absent or altered independent evidence, including exact expiry at the current execution clock")
            val variants = Vector(None, Some(receipt.copy(reference = receipt.reference.copy(revision = "foreign"))),
              Some(receipt.copy(issuer = "foreign")), Some(receipt.copy(purposeIdentity = "split-phase-start")),
              Some(receipt.copy(humanParticipant = "")), Some(receipt.copy(binding = expected.copy(submittingParticipant = "foreign"))),
              Some(receipt.copy(binding = expected.copy(result = submitted.copy(resultReference = ContextReference("foreign", "3"))))),
              Some(receipt.copy(occurredAt = now.plusSeconds(1))), Some(receipt.copy(validUntil = now)),
              Some(receipt.copy(validUntil = now.minusSeconds(1))))
            variants.foreach { variant =>
              When("the independent verifier returns that absent stale or crossed receipt")
              val rejected = HA.admitForCallC(plan.get, submitted, current, call.core, _verifier_(variant))
              Then("no verified Human answer is constructed")
              rejected.isSuccess shouldBe false
            }
            Given("an unavailable verifier, not an authentication-derived receipt")
            val unavailable = new HV.Verifier {
              val issuerIdentity = "independent-provider"
              def verifyForCallC(reference: HV.EvidenceReference, expected: HV.Binding, core: ActionCall.Core) =
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
          take(SplitPhaseExplicitBoundaryValidation.loadC(template.shape, records, records))
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
          val root = take(J.decodeC(index.mapping, take(call.rowC(targets.publication,
            take(U.mappingKeyC(index.mapping)))).get.getString(J.storageField).get))
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
              Then("the complete frontier survives, while WorkOrder reinterpretation, extension and crossed purpose data refuse")
              decoded shouldBe question
              generated.asJson shouldBe payload
              workorder.isSuccess shouldBe false
              H.inputCodec.decode(extended).isLeft shouldBe true
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
              val root = take(J.decodeC(index.mapping, take(call.rowC(targets.publication,
                take(U.mappingKeyC(index.mapping)))).get.getString(J.storageField).get))
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
              val judgment = SS.Judgment(input.context, input.checkpoint, decision, "original-transfer-closure-analysis",
                if (decision == SS.Decision.Enriched) input.gaps.map(gap => SS.Scope(gap.original.identity,
                  gap.producingItems.takeRight(1), gap.receivingItems.take(1), "actual evidence producer and receiver")) else Vector.empty,
                Vector(SplitPhaseExplicitPartitionSemantic.EvidenceReference("public-scope-evidence", "1")))
              val result = ContinuationResult(actual.handle, order.runId, order.continuationId, order.expectedRevision,
                order.context.snapshot, TypedValue(SJ.resultCodec.typeIdentity, judgment),
                ContextReference(input.result.identity, input.result.revision), Vector.empty,
                ExecutionEvidence(Vector(ContextReference("public-scope-evidence", "1")), Some("participant")))
              submitted = Some(result)
              completionwire = Some(take(WorkflowResultJsonV1.encodeC(result, SJ.resultCodec)))
              Given("a crossed host result reference and a foreign original scope context")
              val crossed = result.copy(resultReference = result.resultReference.copy(identity = "participant-result"))
              val foreign = result.copy(result = result.result.copy(value = judgment.copy(context = judgment.context.copy(revision = "foreign"))))
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
            val root = take(J.decodeC(index.mapping, take(call.rowC(targets.publication,
              take(U.mappingKeyC(index.mapping)))).get.getString(J.storageField).get))
            root shouldBe expectedroot.get
            _original_rows(O.Original(saved.record.originalContext, saved.record.originalStart,
              saved.record.originalContext.facts)).foreach { case (collection, identity, expected) =>
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
            val root = take(J.decodeC(expectedroot.get.reference, rootrow.getString(J.storageField).get))
            val retainedwork = take(scopeport.loadForCallC(prepared.source.plan.work.current
              .asInstanceOf[WorkflowContinuation.WorkOrder[SS.Request]].request.continuationId, call.core)).get
            val originals = _original_rows(O.Original(saved.record.originalContext, saved.record.originalStart,
              saved.record.originalContext.facts)).map { case (collection, identity, expected) =>
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
            Then("all native source data is retained and only one inert append is prepared without committing it")
            capture.record shouldBe recaptured.record
            capture.intent shouldBe recaptured.intent
            capture.record.snapshot shouldBe fullsnapshot
            capture.record.scopeWork shouldBe expectedwork.get
            capture.record.scopeCompletion shouldBe prepared.source.completion
            capture.record.scopePredecessor shouldBe prepared.source.source.predecessor
            capture.record.scopeIssued shouldBe prepared.source.plan.next
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
            capture.intent.payload shouldBe TypedValue(SplitPhaseScopeProjectionCheckpointPreparation.payloadType, capture.record)
            capturekey.startsWith("scope-successor:") shouldBe true
            capturekey should not be rootkey
            unchangednative shouldBe expectednative.get
            unchangedroot shouldBe rootrow
            call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            Given("the genuine captured full scope checkpoint and its existing native definitions")
            val fullrecord = capture.record
            val definitioncatalog = take(org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1.catalogC(Vector(f.catalog.definition)))
            When("the full passive wrapper and complete native envelope are encoded and independently decoded")
            val fullwire = take(NJ.encodeC(fullrecord, f.catalog.definition))
            val decodedfull = take(NJ.decodeC(fullrecord.reference, fullwire, f.catalog.definition))
            val nativewire = take(NJ.encodeNativeC(fullrecord, f.catalog.definition))
            val decodednative = take(org.goldenport.cncf.workflow.WorkflowMechanicalTransitionJsonV1.decodeC(
              nativewire, definitioncatalog, NJ.payloadCodec(f.catalog.definition)))
            val untouchednative = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            val untouchedroot = take(call.rowC(targets.publication, rootkey)).get
            Then("the actual work completion closing result and all four native histories survive without any commit")
            decodedfull.reference shouldBe fullrecord.reference
            decodedfull.snapshot shouldBe fullrecord.snapshot
            decodedfull.scopeWork shouldBe fullrecord.scopeWork
            decodedfull.scopeCompletion shouldBe fullrecord.scopeCompletion
            decodedfull.scopeCompletion.closingResult.nonEmpty shouldBe true
            Vector(decodedfull.scopePredecessor -> fullrecord.scopePredecessor,
              decodedfull.scopeIssued -> fullrecord.scopeIssued, decodedfull.before -> fullrecord.before,
              decodedfull.next -> fullrecord.next).foreach { (actual, expected) =>
              SplitPhaseDefinitionBinding.sameRecord(actual, expected) shouldBe true
            }
            decodednative.reference shouldBe capture.intent.reference
            decodednative.component shouldBe SmWorkflowComponent.componentId
            decodednative.operation shouldBe K.advanceOperation
            SplitPhaseDefinitionBinding.sameRecord(decodednative.current, fullrecord.before) shouldBe true
            SplitPhaseDefinitionBinding.sameRecord(decodednative.next, fullrecord.next) shouldBe true
            decodednative.payload.typeIdentity shouldBe NC.payloadType
            decodednative.payload.value.snapshot shouldBe fullrecord.snapshot
            decodednative.payload.value.scopeCompletion shouldBe fullrecord.scopeCompletion
            untouchednative shouldBe expectednative.get
            untouchedroot shouldBe rootrow
            call.executionContext.dataStoreSpace.captureBinding() shouldBe priorbinding
            _specify_scope_publication(call, capture, take(U.bindTargetsC(host(values), declaration)))
            Given("the same full wrapper with crossed purpose definition native prefix missing closing data or extra nested fields")
            val compileddefinition = take(SplitPhaseDefinitionBinding.bindC)
            val order = fullrecord.scopeWork.current.asInstanceOf[WorkflowContinuation.WorkOrder[SS.Request]]
            val input = order.request.input.get
            val alteredwork = fullrecord.scopeWork.copy(current = order.copy(request = order.request.copy(
              input = Some(input.copy(value = input.value.copy(result = input.value.result.copy(revision = "foreign")))))))
            val invalidrecords = Vector(fullrecord.copy(scopeWork = alteredwork),
              fullrecord.copy(scopeCompletion = fullrecord.scopeCompletion.copy(closingResult = None)),
              fullrecord.copy(before = fullrecord.scopeIssued))
            val fulljson = parser.parse(fullwire.substring(NJ.schema.length + 1)).toOption.get
            val payload = fulljson.hcursor.downField("payload").focus.get
            val extra = payload.mapObject(_.add("scopeWork",
              payload.hcursor.downField("scopeWork").focus.get.mapObject(_.add("unexpected", Json.fromBoolean(true)))))
            val extendedwire = NJ.schema + ":" + fulljson.mapObject(_.add("payload", extra)).noSpaces
            When("those independent inconsistencies reach typed encoding or selected full-frame decoding")
            val rejectedrecords = invalidrecords.map(record => NJ.encodeC(record, f.catalog.definition))
            val rejectedpurpose = NJ.decodeC(fullrecord.reference.copy(revision = "foreign"), fullwire, f.catalog.definition)
            val rejectedextension = NJ.decodeC(fullrecord.reference, extendedwire, f.catalog.definition)
            val rejecteddefinition = NJ.decodeC(fullrecord.reference, fullwire, compileddefinition)
            val missingdefinition = NJ.decodeC(fullrecord.reference, fullwire, null)
            Then("none is normalized into a different valid source or native grant")
            rejectedrecords.foreach(_ shouldBe a[Consequence.Failure[?]])
            rejectedpurpose shouldBe a[Consequence.Failure[?]]
            rejectedextension shouldBe a[Consequence.Failure[?]]
            rejecteddefinition shouldBe a[Consequence.Failure[?]]
            missingdefinition shouldBe a[Consequence.Failure[?]]
            Given("a passive copy changing only human presentation near the full app frame byte bound")
            val originalbytes = order.presentation.currentSituation.getBytes(UTF_8).length
            val availablebytes = NJ.maximumPayloadBytes - fullwire.getBytes(UTF_8).length + originalbytes - 8
            val inflatedorder = order.copy(presentation = order.presentation.copy(currentSituation = "語" * (availablebytes / 3)))
            val inflated = fullrecord.copy(scopeWork = fullrecord.scopeWork.copy(current = inflatedorder))
            When("the app frame is encoded and decoded but the larger complete native envelope is preflighted")
            val inflatedwire = take(NJ.encodeC(inflated, f.catalog.definition))
            val inflateddecoded = take(NJ.decodeC(inflated.reference, inflatedwire, f.catalog.definition))
            val refusedenvelope = NJ.encodeNativeC(inflated, f.catalog.definition)
            val stillnative = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            val stillroot = take(call.rowC(targets.publication, rootkey)).get
            Then("the valid app payload fits while aggregate native capacity is refused without a write or permit")
            availablebytes should be > 0
            inflatedwire.getBytes(UTF_8).length should be <= NJ.maximumPayloadBytes
            inflatedwire.getBytes(UTF_8).length should be >= (NJ.maximumPayloadBytes - 11)
            inflateddecoded.scopeWork shouldBe inflated.scopeWork
            inflateddecoded.scopeCompletion shouldBe fullrecord.scopeCompletion
            inflateddecoded.snapshot shouldBe fullrecord.snapshot
            refusedenvelope shouldBe a[Consequence.Failure[?]]
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
              When("the owning call observes changed complete data under the same record identity")
              val priorrow = take(call.rowC(collection, identity)).get
              val refused = try {
                take(call.writeC(collection, identity, changed, update = true))
                scopeport.prepareProjectionForCallC(query(saved), call.core)
              } finally take(call.writeC(collection, identity, priorrow, update = true))
              val unchanged = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
              val restoredrow = take(call.rowC(collection, identity)).get
              Then("matching identity strings confer no restored capability and no native transition occurs")
              refused.isSuccess shouldBe false
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
            When("the owning call observes its retained completed frontier but requests a fresh successor capture")
            val retained = take(scopeport.prepareProjectionForCallC(query(saved), call.core))
            val snapshot = take(SplitPhaseScopeProjectionSnapshot.projectC(retained.outcome))
            val rejected = SplitPhaseScopeProjectionCheckpointPreparation.captureC(retained)
            val after = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
            val rootafter = take(call.rowC(targets.publication, rootkey)).get
            val indexafter = take(call.rowC(targets.publication, indexkey)).get
            Then("retained observation stays available while the obsolete predecessor grants no new capture or state change")
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
            rejected shouldBe a[Consequence.Failure[?]]
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
          val purposes = take(SplitPhasePartitionCompletionCheckpointDataStorePort.capturePurposesC(host(declared)))
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
              _original_rows(original).foreach { case (c, id, row) => take(call.writeC(c, id, row)) }
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
              completed.start shouldBe original.start
              completed.issued shouldBe issued
              completed.completion.result shouldBe submitted
              completed.before shouldBe issuednative
              completed.next.history.dropRight(1) shouldBe issuednative.history
              completed.next.revision shouldBe issuednative.revision.next
              root.origin shouldBe K.Origin.PartitionCompletion(submitted.continuationId,
                PS.ResultReference(submitted.resultReference.identity, submitted.resultReference.revision))
              root.originalContext shouldBe context
              root.originalStart shouldBe original.start
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
              val judgment = SS.Judgment(input.context, input.checkpoint, decision, "partition-root transfer evidence",
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
              declaration, f.catalog, targets, purposes, call.core)).get
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
            val definitioncatalog = take(org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1.catalogC(Vector(f.catalog.definition)))
            withHost(path, retainedvalues) { (call, _) =>
              val scopeport = take(SplitPhaseManagedScopeSemantic.bindC(
                ComponentCreate(call.core.component.get.subsystem.get, ComponentOrigin.Builtin), declaration, f.catalog))
              val priorbinding = call.executionContext.dataStoreSpace.captureBinding()
              When("the actual owning advance call restores the completed source and passively roundtrips its full wrapper")
              val prepared = take(scopeport.prepareProjectionForCallC(query(saved), call.core))
              val captured = take(NC.captureC(prepared))
              val repeated = take(NC.captureC(take(scopeport.prepareProjectionForCallC(query(saved), call.core))))
              val encoded = take(NJ.encodeC(captured.record, f.catalog.definition))
              val decoded = take(NJ.decodeC(captured.record.reference, encoded, f.catalog.definition))
              val nativewire = take(NJ.encodeNativeC(captured.record, f.catalog.definition))
              val envelope = take(org.goldenport.cncf.workflow.WorkflowMechanicalTransitionJsonV1.decodeC(
                nativewire, definitioncatalog, NJ.payloadCodec(f.catalog.definition)))
              val rootrow = take(call.rowC(targets.publication, take(U.mappingKeyC(partition.get.checkpoint.get.reference)))).get
              val root = take(J.decodeC(partition.get.checkpoint.get.reference, rootrow.getString(J.storageField).get))
              val current = take(instances.loadForCallC(f.configuration, saved.before.identity, call.core)).get
              val originals = _original_rows(original).map { case (c, id, row) => row -> take(call.rowC(c, id)).get }
              Then("all original partition provenance and the complete frontier survive with no native or application write")
              captured.record.snapshot.first shouldBe partition.get.checkpoint.get
              captured.record.snapshot.first.originalContext shouldBe context
              captured.record.snapshot.first.originalStart shouldBe original.start
              captured.record.scopePredecessor shouldBe partition.get.next
              captured.record.scopeWork shouldBe scopework.get
              captured.record.scopeCompletion.result shouldBe scoperesult.get
              captured.record.scopeCompletion.closingResult shouldBe Some(org.goldenport.cncf.workflow.StateMachineOperationResult(
                SI.closingType, scoperesult.get.resultReference))
              captured.record.before shouldBe expectednative.get
              captured.record.next.history.dropRight(1) shouldBe expectednative.get.history
              captured.record.next.revision shouldBe expectednative.get.revision.next
              decoded shouldBe captured.record
              envelope.payload.value shouldBe captured.record
              envelope.current shouldBe captured.record.before
              envelope.next shouldBe captured.record.next
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
              _original_rows(original).foreach { case (c, id, row) => take(call.writeC(c, id, row)) }
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
              actual.start shouldBe original.start
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
            _original_rows(original).foreach { case (c, id, row) =>
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
            _original_rows(original).foreach { case (c, id, row) => take(call.writeC(c, id, row)) }
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
          val record = take(J.decodeC(index.mapping, take(call.rowC(targets.publication,
            take(U.mappingKeyC(index.mapping)))).get.getString(J.storageField).get))
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
          val root = take(J.decodeC(index.mapping, take(call.rowC(targets.publication,
            take(U.mappingKeyC(index.mapping)))).get.getString(J.storageField).get))
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

    "refuse crossed commands and independently changed original rows without writing or reconstructing planning" in {
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
          When("current-state observation reads that complete independent data")
          val result = port.loadCurrentForCallC(command, call.core)
          Then("the malformed row is refused, not replaced by planning computation")
          result.isSuccess shouldBe false
          take(call.writeC(collection, identity, row, true))
        }
        Given("a changed source value that retains the original reference")
        val source = saved.record.originalStart.source
        val changedsource = source.copy(authority = source.authority.copy(identity = "different-source-authority"))
        take(call.writeC(target(F.Purpose.Source), source.reference.identity,
          Record.data(F.storageField -> take(F.encodeSourceC(changedsource))), true))
        When("current-state observation compares the full independent source with the original Start")
        val changedresult = port.loadCurrentForCallC(command, call.core)
        Then("equal references do not hide the changed original authority")
        changedresult.isSuccess shouldBe false
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
        Then("semantic original-command drift is refused rather than presented as empty fresh output")
        crossedretained.isSuccess shouldBe false
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
                _original_rows(O.Original(context, saved.record.originalStart, unresolved)).foreach { case (c, id, row) =>
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
              projected.map(_.wire).distinct.size shouldBe 1
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
                take(WorkflowWorkOrderJsonV1.decodeC(value.wire, SplitPhaseExplicitReasoningSemanticJson.requestCodec)) shouldBe value.work
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
                val firstinterruption = issuecase == "interrupted-readback" && observations == 0
                When("the owning issuer reobserves the original selected source, publishes only at exact absence, or reads the saved task")
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
                  val judgment = Reasoning.Judgment(request.context, decision, "unknown-only-classification",
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
                val previousdemands = pending.issue.prepared.pending.selection.data.objectives.demands.get
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
                When("a reply crosses its assignment, evidence, known-item boundary or actual native completed history")
                val crossed = Vector(
                  result.copy(resultReference = ContextReference("foreign-reasoning-result", "1")),
                  result.copy(result = result.result.copy(value = result.result.value.copy(assignments = Vector.empty))),
                  result.copy(result = result.result.copy(value = result.result.value.copy(assignments =
                    result.result.value.assignments :+ Reasoning.Assignment(source.inventory.orderedUnfinishedItems.head,
                      Reasoning.Classification.SettledExecution, "attempt to rewrite known work")))),
                  result.copy(evidence = result.evidence.copy(references = Vector(ContextReference("unrelated-evidence", "1"))))
                )
                val retained = WorkflowCompletionRecordJsonV1.Record(plan.next.revision, result, Some(plan.closing))
                Then("the natural result boundary refuses without accepting a projected completion as a native publication")
                crossed.foreach(reply => SplitPhaseTypedReasoningCompletion.admitC(pending, reply).isSuccess shouldBe false)
                SplitPhaseTypedReasoningCompletion.pendingC(nativesource, nativesource.publicationPostimage,
                  actualwork).isSuccess shouldBe false
                SplitPhaseTypedReasoningCompletion.recoverC(nativesource, actualcurrent, actualwork,
                  retained).isSuccess shouldBe false
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
              When("public advance selects the actual reasoning completion contract before scope or partition routing")
              val pending = take(WorkflowWorkOrderJsonV1.decodeC(
                _scalar(take(_public_advance_c(call, wire, cli = true))), ReasoningJson.requestCodec))
              Then("it observes precisely the assigned saved work without any native append or allocation")
              pending shouldBe issuedwork.get
              assertNative(call, issuednext.get)
              Consequence.unit
            }
            val actualorder = issuedwork.get.current.asInstanceOf[WorkflowContinuation.WorkOrder[Reasoning.Request]].request
            val actualrequest = actualorder.input.get.value
            val answerdecision = if (issuecase == "interrupted-readback") Reasoning.Decision.DecisionNeeded else Reasoning.Decision.Enriched
            val resultevidence = Semantic.EvidenceReference("public-reasoning-completion-evidence", "1")
            val judgment = Reasoning.Judgment(actualrequest.context, answerdecision, "unknown-only-public-result",
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
                  Given("an independently existing successor row in this private fixture before result submission")
                  take(call.writeC(targets.publication, successorkey, Record.data(successorfield -> "owned-fixture-collision")))
                  When("the real result Operation stages its native completion and successor against that collision")
                  val refused = _public_operation_c(call, Projection.completionOperation.operation, resultwire, cli = false)
                  val storage = take(WorkflowComponentStorage.bindC(ComponentCreate(call.core.component.get.subsystem.get,
                    ComponentOrigin.Builtin), SmWorkflowComponent.componentId, declaration.configuration,
                    Vector(f.catalog.definition), declaration.instances, declaration.continuations))
                  val native = take(storage.bindCompletionC(Projection.completionOperation, ReasoningJson.requestCodec,
                    ReasoningJson.resultCodec, Projection.closingType, (_, _) => Consequence.success(Vector.empty)))
                  Then("no native completion or revision is committed and the independent row is not overwritten")
                  refused.isSuccess shouldBe false
                  take(native.loadRetainedC(actualorder.continuationId)(using call.executionContext)) shouldBe None
                  assertNative(call, issuednext.get)
                  take(call.rowC(targets.publication, successorkey)).get.getString(successorfield) shouldBe Some("owned-fixture-collision")
                  // This exact row is a test-owned obstruction, not user data.
                  take(call.deleteFixtureRowC(targets.publication, successorkey))
                  collisiontested = true
                }
                When("the participant returns only its assigned Judgment through the launcher or owning Operation")
                val response = take(_public_operation_c(call, Projection.completionOperation.operation, resultwire,
                  cli = reopen == "public result"))
                val wire = _scalar(response)
                val observed = take(SplitPhaseManagedReasoningCompletion.observeForCallC(actualorder.continuationId,
                  declaration, f.catalog, call.core)).get
                if (completed.isEmpty) { completed = Some(observed.current); acknowledgement = Some(wire) }
                Then("the native completion and complete successor are retained without another AI call, allocation or append")
                wire shouldBe acknowledgement.get
                observed.current shouldBe completed.get
                observed.plan.submitted shouldBe submitted
                observed.plan.next shouldBe completed.get
                observed.plan.next.history.dropRight(1) shouldBe issuednext.get.history
                observed.plan.next.revision shouldBe issuednext.get.revision.next
                observed.plan.next.suspension shouldBe None
                observed.plan.closing shouldBe StateMachineOperationResult(Projection.closingType, submitted.resultReference)
                if (answerdecision == Reasoning.Decision.Enriched) {
                  observed.plan.downstream.nonEmpty shouldBe true
                  completedplanning = observed.plan.downstream
                  observed.plan.downstream.get.partition shouldBe expected.get.snapshot.partition
                  observed.plan.downstream.get.projection shouldBe expected.get.snapshot.projection
                  observed.plan.downstream.get.admission shouldBe expected.get.snapshot.admission
                } else observed.plan.downstream shouldBe None
                assertNative(call, completed.get)
                When("a repeated result attempts to replace the already committed semantic answer")
                val crossed = submitted.copy(result = submitted.result.copy(value = judgment.copy(rationale = "different answer")))
                val crossedwire = take(WorkflowResultJsonV1.encodeC(crossed, ReasoningJson.resultCodec))
                val refused = _public_operation_c(call, Projection.completionOperation.operation, crossedwire, cli = false)
                Then("historical observation refuses the different result without changing native completion")
                refused.isSuccess shouldBe false
                assertNative(call, completed.get)
                Given("the saved successor's identical semantic JSON with harmless presentation whitespace")
                val stored = take(call.rowC(targets.publication, successorkey)).get.getString(successorfield).get
                val prefix = "sm-workflow.reasoning-successor.v1:"
                val formatted = prefix + io.circe.parser.parse(stored.substring(prefix.length)).toOption.get.spaces2
                take(call.writeC(targets.publication, successorkey, Record.data(successorfield -> formatted), update = true))
                When("retained observation reads the formatted record rather than a copied canonical-byte proof")
                val formattedobservation = take(SplitPhaseManagedReasoningCompletion.observeForCallC(actualorder.continuationId,
                  declaration, f.catalog, call.core)).get
                Then("full semantic successor data and the native completion remain unchanged")
                formattedobservation.plan.downstream shouldBe observed.plan.downstream
                formattedobservation.plan.submitted shouldBe submitted
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
          assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseTypedReasoningWorkIssue.Plan(null, null, null, null, null, null)")
          assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseManagedReasoningIssue.Port(null, null, null)")
          assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseTypedReasoningCompletion.Pending(null, null, null)")
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
            val port = take(PlanningPort.bindC(owner, declaration, f.catalog))
            retainedcore = Some(call.core)
            retainedport = Some(port)
            if (mode == "collision" && !injected) {
              Given("an independently existing malformed application row without its native calculation")
              take(call.writeC(targets.publication, key, Record.data(PlanningJson.storageField -> "collision")))
              injected = true
            }
            if (mode == "collision") {
              When("owning advance sees that one-sided application state")
              val rejected = port.executeForCallC(query(saved), call.core)
              Then("it refuses without recalculation, native append or overwriting the independent row")
              rejected.isSuccess shouldBe false
              assertNative(call, scope.record.next)
              take(call.rowC(declaration.continuations, nativekey)) shouldBe None
              take(call.rowC(targets.publication, key)).get.getString(PlanningJson.storageField) shouldBe Some("collision")
            } else if (injected) {
              When("retained advance reads the damaged calculation after actual native progress for " + mode)
              val rejected = port.executeForCallC(query(saved), call.core)
              Then("one-sided, corrupt or later-prefix absence is an error, never a fresh calculation")
              rejected.isSuccess shouldBe false
              assertNative(call, expected.get.next)
              take(call.rowC(declaration.continuations, nativekey)).nonEmpty shouldBe
                !Set("missing-native", "missing-both").contains(mode)
              val application = take(call.rowC(targets.publication, key))
              if (Set("missing-application", "missing-both").contains(mode)) application shouldBe None
              else if (mode == "malformed-application")
                application.get.getString(PlanningJson.storageField) shouldBe Some("malformed")
              else application.get.getString(PlanningJson.storageField) shouldBe
                Some(take(PlanningJson.encodeC(expected.get, f.catalog.definition)))
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
              When("owning advance publishes or observes the already committed calculation twice")
              val observed = take(port.executeForCallC(query(saved), call.core))
              val repeated = take(port.executeForCallC(query(saved), call.core))
              val loaded = take(port.loadForCallC(query(saved), call.core)).get
              if (expected.isEmpty) expected = Some(observed.record)
              Then("the whole saved calculation and native append are exact, with no new retained processor stages")
              observed.record shouldBe expected.get
              repeated.record shouldBe expected.get
              loaded.record shouldBe expected.get
              observed.record.priorScope shouldBe prior.get
              observed.record.before shouldBe prior.get.next
              observed.current shouldBe observed.record.next
              observed.current.history.dropRight(1) shouldBe prior.get.next.history
              observed.mechanicalStages shouldBe Vector.empty
              observed.record.downstream.selection shouldBe first.get.snapshot.selection
              observed.record.downstream.partition shouldBe first.get.snapshot.partition
              take(PlanningJson.decodeC(reference, take(PlanningJson.encodeC(observed.record, f.catalog.definition)),
                f.catalog.definition)) shouldBe observed.record
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
              When("a caller tries to recapture it as a fresh calculation")
              val recaptured = Planning.captureC(take(W.continuePublishedScopeC(observed.scope)))
              Then("the retained prefix grants no fresh append at the older scope postimage")
              recaptured.isSuccess shouldBe false
              assertNative(call, observed.record.next)
              if (mode == "malformed-application" && !injected) {
                Given("the exact application row in this private diagnostic database")
                When("the test-only probe corrupts its complete frame")
                take(call.writeC(targets.publication, key, Record.data(PlanningJson.storageField -> "malformed"), update = true))
                Then("native progress remains committed while that application value is malformed")
                assertNative(call, observed.record.next)
                injected = true
              } else if (mode.startsWith("missing-") && !injected) {
                Given("the exact application/native rows in this private diagnostic database")
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
        val verifier = new Human.Verifier {
          def issuerIdentity = "scope-preview-independent-test-human-verifier"
          def verifyForCallC(reference: Human.EvidenceReference, expected: Human.Binding, core: ActionCall.Core) = {
            humanchecks += 1
            if (!humanresolution) Consequence.securityPermissionDenied[Option[Human.Receipt]]("ALL-Built scope has no Human answer")
            else {
              expected.question shouldBe humanquestion.get
              expected.result shouldBe humanresult.get
              reference shouldBe Human.EvidenceReference("independent-human-consent", "1")
              val now = core.executionContext.clock.instant()
              Consequence.success(Some(Human.Receipt(reference, issuerIdentity, "independent-human",
                Human.purpose, expected, now, now.plusSeconds(60))))
            }
          }
        }
        val wire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(query(saved)))
        withHost(path, declared, publicadvance = true, humanverifier = if (humanresolution) Some(verifier) else None) { (call, _) =>
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
          withHost(path, retainedvalues, publicadvance = true, humanverifier = Some(verifier)) { (call, _) =>
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
              val judgment = Reasoning.Judgment(input.context, Reasoning.Decision.Enriched,
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
              val prepared = take(Planning.prepareC(captured, targets, f.catalog.definition))
              val row = prepared.rows.head
              val storage = take(ComponentRecordStorage.bindForCallC(call.core, targets.storeName, row.collection,
                DataStore.StringEntryId(row.identity)))
              val program = take(storage.stageCreateC(row.payload))
              Given("the real fixed-target staged Create and a separately inserted conflicting row")
              take(call.writeC(row.collection, row.identity, Record.data(PlanningJson.storageField -> "independent-collision")))
              val native = take(take(WorkflowComponentStorage.bindC(ComponentCreate(owner, ComponentOrigin.Builtin),
                SmWorkflowComponent.componentId, declaration.configuration, Vector(f.catalog.definition),
                declaration.instances, declaration.continuations))
                .bindMechanicalC(K.advanceOperation, PlanningJson.payloadCodec(f.catalog.definition)))
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
            _original_rows(original).foreach { case (c, id, row) => take(call.writeC(c, id, row)) }
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

    "refuse complete issued-frame and independently stored data drift without mutation" in {
      Given("one genuine native WorkOrder and independently stored original data")
      val saved = capture()
      val original = O.Original(f.context, f.record, f.facts)
      val path = Files.createTempDirectory("smwf-pending-drift-").resolve("host.sqlite")
      var installed = false
      var expected = Option.empty[WorkflowInteraction[SplitPhaseExplicitPartitionSemantic.Request, Nothing]]
      withHost(path, values ++ SplitPhasePartitionReplyFixture.values) { (call, port) =>
        Given("the actual issue-time fixture only on its first invocation")
        if (!installed) {
          _original_rows(original).foreach { case (c, id, row) => take(call.writeC(c, id, row)) }
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
        val input = request.input.get.value
        val changedrequests = Vector(request.copy(expectedRevision = org.goldenport.cncf.workflow.StateMachineRevision("99")),
          request.copy(runId = org.goldenport.cncf.workflow.StateMachineRunIdentity("foreign")),
          request.copy(context = request.context.copy(references = Vector.empty)),
          request.copy(input = Some(request.input.get.copy(value = input.copy(items = Vector.empty)))),
          request.copy(input = Some(request.input.get.copy(value = input.copy(boundaries = Vector.empty)))),
          request.copy(input = Some(request.input.get.copy(value = input.copy(reply = input.reply.copy(result =
            input.reply.result.copy(identity = input.reply.resolutions.estimates.get.identity)))))),
          request.copy(completionOperation = org.goldenport.cncf.workflow.StateMachineOperationIdentity("foreign", "operation")))
        When("the inert correspondence checker receives crossed full frame data")
        val framedresults = changedrequests.map(changed => O.verifyPendingPartitionC(original, native,
          work.copy(current = order.copy(request = changed))).isSuccess)
        val presentationresult = O.verifyPendingPartitionC(original, native,
          work.copy(current = order.copy(presentation = order.presentation.copy(summary = Some("different")))))
        Then("each frame mismatch is refused without constructing a pending completion or performing an effect")
        framedresults shouldBe Vector.fill(changedrequests.size)(false)
        presentationresult.isSuccess shouldBe false
        val row = take(call.rowC(declaration.continuations, original.context.owner.continuation.value)).get
        Given("the actual published payload made unreadable while its row identity remains")
        take(call.writeC(declaration.continuations, original.context.owner.continuation.value,
          Record.data("issuedWorkPayload" -> "malformed-issued-payload"), true))
        When("advance reads the actual malformed publication")
        val malformedresult = port.loadPendingPartitionForCallC(query(saved), call.core)
        Then("it refuses rather than recreating the original work")
        malformedresult.isSuccess shouldBe false
        take(call.writeC(declaration.continuations, original.context.owner.continuation.value, row, true))
        Given("the independent original source payload changed under the same reference")
        val source = original.start.source
        take(call.writeC(target(F.Purpose.Source), source.reference.identity, Record.data(F.storageField ->
          take(F.encodeSourceC(source.copy(authority = source.authority.copy(identity = "foreign"))))), true))
        When("advance reads the changed source")
        val sourceresult = port.loadPendingPartitionForCallC(query(saved), call.core)
        Then("equal source references do not conceal complete data drift")
        sourceresult.isSuccess shouldBe false
        take(call.writeC(target(F.Purpose.Source), source.reference.identity, Record.data(F.storageField -> take(F.encodeSourceC(source))), true))
        val start = original.start
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
          When("advance observes pending work using that semantically crossed Start command")
          val crossed = port.loadPendingPartitionForCallC(query(saved), call.core)
          Then("it refuses without changing original selection/source/proposal, native history, actual work or checkpoint")
          crossed.isSuccess shouldBe false
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
        Given("inert Start/context records whose selected command agrees but complete source or proposal differs")
        val changedstarts = Vector(start.copy(source = source.copy(authority = source.authority.copy(revision = "different"))),
          start.copy(proposal = start.proposal.map(proposal => proposal.copy(children = Vector.empty))))
        When("pending correspondence compares those complete raw records")
        val relationresults = changedstarts.map(changed => O.verifyPendingPartitionC(original.copy(start = changed), native, work).isSuccess)
        Then("same references do not conceal a changed Start-to-context source or proposal")
        relationresults shouldBe Vector(false, false)
        Given("the exact original source and actual issued payload restored")
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

    "refuse independent malformed payloads without recomputation or native mutation" in {
      Given("one actual committed checkpoint with every independently stored row")
      val saved = capture(); val t = take(U.bindTargetsC(host(values), declaration)); val path = Files.createTempDirectory("smwf-managed-drift-").resolve("host.sqlite")
      var installed = false
      When("original, effective, candidate and index rows are each independently corrupted and restored")
      withHost(path) { (call, port) =>
        if (!installed) { take(installC(call, saved)); take(port.executePreparedForCallC(saved, call.core)); installed = true }
        val rows = originalRows(saved) ++ take(U.prepareC(saved, t)).rows.map(r => (r.collection, r.identity, r.payload))
        rows.foreach { case (c, id, original) =>
          original.fields.size shouldBe 1
          take(call.writeC(c, id, Record.data(original.fields.head.key -> "malformed-complete-payload"), true))
          port.loadMechanicalForCallC(query(saved), call.core).isSuccess shouldBe false
          take(call.writeC(c, id, original, true))
          take(port.loadMechanicalForCallC(query(saved), call.core)).get.record shouldBe saved.record
        }
        Then("restoration recovers the exact mapping and canonical revision never changes")
        assertNative(call, saved.next); assertRows(call, saved)
        Consequence.unit
      }
    }

    "return uncertainty after committed read interruption and recover later without retry" in {
      Given("an actual native transaction and an inert post-read refusal, never a fake receipt")
      val saved = capture(); val path = Files.createTempDirectory("smwf-managed-readback-").resolve("host.sqlite")
      var installed = false; var interrupt = true; var checks = 0
      When("the first complete post-commit row observation is unavailable")
      withHost(path, rowCheck = () => { checks += 1; if (interrupt) Consequence.serviceUnavailable("private inert row observation interruption") else Consequence.unit }) { (call, port) =>
        if (!installed) {
          take(installC(call, saved)); installed = true
          port.executePreparedForCallC(saved, call.core).isSuccess shouldBe false
          checks shouldBe 1
          assertNative(call, saved.next); assertRows(call, saved)
          interrupt = false
        }
        take(port.loadMechanicalForCallC(query(saved), call.core)).get.record shouldBe saved.record
        Then("later observation and reopen retain one append and the original complete publication")
        assertNative(call, saved.next)
        Consequence.unit
      }
      withHost(path) { (call, port) =>
        take(port.loadMechanicalForCallC(query(saved), call.core)).get.current shouldBe saved.next
        assertRows(call, saved); Consequence.unit
      }
    }
  }
}
