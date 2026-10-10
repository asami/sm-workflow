package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import java.nio.file.{Files, Path}
import java.time.Instant
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
import org.goldenport.cncf.workflow.{ContextReference, WorkflowWaitJsonV1, WorkflowProtocolV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.GivenWhenThen
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.SplitPhaseStartCommand
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseTypedStartPreparation as T,
  SplitPhaseTypedStartHumanVerification as H, SplitPhaseTypedStartJsonV1 as TJ,
  SplitPhaseExplicitPlanningInputJsonV1 as J, SplitPhaseExplicitPartitionContext as C,
  SplitPhaseExplicitPartitionContextJsonV1 as CC,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseExplicitPartitionSemanticJson as SJ,
  SplitPhaseTypedPartitionCompletion as PC, SplitPhaseExplicitPartitionComputation as E,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhasePartitionEnrichmentPublication as U,
  SplitPhasePartitionEnrichmentJsonV1 as EJ, SplitPhasePartitionCompletionCheckpointPreparation as AC,
  SplitPhasePartitionCompletionCheckpointJsonV1 as AJ, SplitPhasePartitionCompletionCheckpointPublication as AU,
  SplitPhasePlanningCheckpointPreparation as CK,
  SplitPhasePlanningCheckpointPublication as CU, SplitPhasePlanningCheckpointJsonV1 as CJ,
  SplitPhasePlanningCheckpointObservation as CO}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import SplitPhaseExplicitPartitionFixture.take
import SplitPhaseTypedStartFixture.{configuration, catalog, facts, source, proposal, selectionFor}
import J.Purpose

/**
 * @since   Oct.  2, 2026
 * @version Oct. 10, 2026
 */
final class SplitPhaseManagedTypedStartOperationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private final case class ObservedRows(record: U.Record,
    planning: Option[SplitPhaseExplicitPlanningAdvance.Data], estimates: Option[E.Estimates],
    handoffs: Option[B.HandoffPlan], candidates: Vector[CO.Documents])
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "publictypedstart", name))
  private def target(p: Purpose) = collection(p.toString.toLowerCase)
  private val workflow = SplitPhaseManagedStartAssembly.Declaration(configuration, collection("instances"), collection("continuations"))
  private val now = Instant.parse("2026-10-02T03:00:00Z")
  private val ip = SplitPhasePlanningInputReadPolicy.prefix
  private val cp = SplitPhasePartitionContextReadPolicy.prefix
  private val sp = SplitPhaseHostSelectionReadPolicy.prefix
  private val tp = SplitPhaseTypedStartReadPolicy.prefix
  private val _ep = SplitPhasePartitionEnrichmentStorage.prefix
  private val _ap = SplitPhaseCandidateAllocationPolicy.prefix
  private val _rp = SplitPhasePartitionReplyAllocationPolicy.prefix
  private val values = Map[String, ConfigurationValue](
    ip + "store-name" -> ConfigurationValue.StringValue("workflow"),
    cp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    cp + "record-collection" -> ConfigurationValue.StringValue(collection("contexts").print),
    sp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    sp + "selection-collection" -> ConfigurationValue.StringValue(collection("selections").print),
    tp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    tp + "record-collection" -> ConfigurationValue.StringValue(collection("starts").print),
    _ep + "store-name" -> ConfigurationValue.StringValue("workflow"),
    _ep + "record-collection" -> ConfigurationValue.StringValue(collection("enrichments").print),
    _ap + "policy-identity" -> ConfigurationValue.StringValue("native-candidate-policy"),
    _ap + "policy-revision" -> ConfigurationValue.StringValue("1"),
    _ap + "namespace" -> ConfigurationValue.StringValue("native-candidates"),
    _rp + "policy-identity" -> ConfigurationValue.StringValue("native-reply-policy"),
    _rp + "policy-revision" -> ConfigurationValue.StringValue("1"),
    _rp + "namespace" -> ConfigurationValue.StringValue("native-replies"),
    SplitPhaseManagedTypedStartAssembly.revisionKey -> ConfigurationValue.StringValue("1")) ++
    Purpose.values.map(p => (ip + p.key) -> ConfigurationValue.StringValue(target(p).print))
  private def host(values: Map[String, ConfigurationValue]) = ResolvedConfiguration(HostConfiguration(values), ConfigurationTrace.empty)
  private def _purpose_hosts: Vector[(String, ResolvedConfiguration)] =
    Vector(tp + "record-collection", cp + "record-collection", sp + "selection-collection",
      ip + Purpose.Source.key, ip + Purpose.Inventory.key, ip + Purpose.Boundary.key,
      ip + Purpose.PartitionPolicy.key).zipWithIndex.map { (key, index) =>
      key -> host(values.updated(key, ConfigurationValue.StringValue(collection("alternate" + index).print)))
    }
  private def allocation(id: String, participant: String = "participant"): T.Record = {
    val command = SplitPhaseTypedStartFixture.command.copy(selectionIdentity = id, participantIdentity = participant,
      idempotencyKey = "key-" + id)
    SplitPhaseTypedStartFixture.record.copy(reference = SplitPhaseExplicitStartPreparation.Reference(id, "1"),
      instanceIdentity = InstanceIdentity("instance-" + id), command = command, selection = selectionFor(command))
  }
  private def scalar(response: OperationResponse): String = response match {
    case OperationResponse.Scalar(value: String) => value
    case other => fail(s"Expected typed Start JSON: $other")
  }
  private def inputRows = Vector(
    (target(Purpose.Source), source.reference.identity, take(J.encodeSourceC(source))),
    (target(Purpose.Inventory), facts.proposal.inventory.reference.identity, take(J.encodeInventoryC(facts.proposal.inventory))),
    (target(Purpose.Proposal), proposal.reference.identity, take(J.encodeProposalC(proposal))),
    (target(Purpose.Boundary), facts.boundary.boundary.reference.identity, take(J.encodeBoundaryC(facts.boundary.boundary))),
    (target(Purpose.Ownership), facts.boundary.ownership.reference.identity, take(J.encodeOwnershipC(facts.boundary.ownership))),
    (target(Purpose.Handoffs), facts.boundary.handoffs.reference.identity, take(J.encodeHandoffsC(facts.boundary.handoffs))),
    (target(Purpose.Estimates), facts.partition.estimates.reference.identity, take(J.encodeEstimatesC(facts.partition.estimates))),
    (target(Purpose.PartitionPolicy), facts.partition.policy.reference.identity, take(J.encodePartitionPolicyC(facts.partition.policy))),
    (target(Purpose.Planning), facts.planning.reference.identity, take(J.encodePlanningC(facts.planning))))
  private val completionIds = Vector("partition-completion-service", "partition-completion-cli", "partition-completion-decision")
  private val _collision_ids = Vector("partition-completion-collision", "partition-completion-lineage-collision",
    "partition-completion-map-collision", "partition-completion-index-collision", "partition-completion-candidate-collision")
  private val _negative_ids = Vector("partition-completion-missing-grant") ++ _collision_ids
  private def semanticResult(work: WorkflowInteraction[S.Request, Nothing],
    decision: Boolean): ContinuationResult[S.Judgment] = {
    val request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
    val input = request.input.get.value
    val judgment = S.Judgment(if (decision) S.Decision.DecisionNeeded else S.Decision.Enriched,
      "private-semantic-analysis", if (decision) Vector.empty else input.items.map(i =>
        S.NovelEstimate(i.identity, 120, 180, "bounded-work", "novel-closure")),
      if (decision) Vector.empty else input.boundaries.map(b => S.BoundaryJudgment(b.edge.afterItem,
        SplitPhasePartitionEnumeration.EdgeDisposition.Either, "independent-closure")),
      Vector(S.EvidenceReference("private-semantic-evidence", "1")))
    ContinuationResult(work.handle, request.runId, request.continuationId, request.expectedRevision, request.context.snapshot,
      TypedValue(SJ.resultCodec.typeIdentity, judgment), ContextReference(input.reply.result.identity, input.reply.result.revision),
      Vector.empty, ExecutionEvidence(Vector(ContextReference("private-semantic-evidence", "1")), Some("participant")))
  }

  "Public typed SplitPhase Start" should {
    "require an explicitly host-selected allocation version and human verifier" in {
      Given("the explicit host metadata for all existing purposes and enrichment")
      SplitPhaseManagedTypedStartAssembly.revisionC(host(values.removed(SplitPhaseManagedTypedStartAssembly.revisionKey)))
        .isSuccess shouldBe false
      SplitPhaseManagedTypedStartAssembly.revisionC(host(values.updated(SplitPhaseManagedTypedStartAssembly.revisionKey,
        ConfigurationValue.StringValue(" 1")))).isSuccess shouldBe false
      take(SplitPhaseManagedTypedStartAssembly.revisionC(host(values))).value shouldBe "1"
      SplitPhaseManagedTypedStartAssembly.bindHostC(null, workflow, catalog, null)(using ExecutionContext.create()).isSuccess shouldBe false
      H.admitForCallC(null, null, null).isSuccess shouldBe false
      When("the new enrichment storage metadata is bound without a native resource")
      take(SplitPhasePartitionEnrichmentStorage.bindC(host(values), workflow)).collection shouldBe collection("enrichments")
      Then("all fifteen purposes must remain distinct in the Workflow store")
      val aliases = Purpose.values.toVector.map(target) ++ Vector(workflow.instances, workflow.continuations,
        collection("starts"), collection("contexts"), collection("selections"))
      aliases.foreach { c =>
        SplitPhasePartitionEnrichmentStorage.bindC(host(values.updated(_ep + "record-collection",
          ConfigurationValue.StringValue(c.print))), workflow).isSuccess shouldBe false
      }
      Vector("store-name", "record-collection").foreach { key =>
        SplitPhasePartitionEnrichmentStorage.bindC(host(values.removed(_ep + key)), workflow).isSuccess shouldBe false
      }
      SplitPhasePartitionEnrichmentStorage.bindC(host(values.updated(_ep + "store-name",
        ConfigurationValue.StringValue("foreign"))), workflow).isSuccess shouldBe false
      SplitPhasePartitionEnrichmentStorage.bindC(host(values.updated(_ep + "record-collection",
        ConfigurationValue.StringValue(" " + collection("enrichments").print))), workflow).isSuccess shouldBe false
    }

    "start on native managed calls recover original receipts and bind registered context without resetting later history" in {
      Given("a native SQLite host with a fixture-only human verifier and explicit Workflow reply policy")
      val path = Files.createTempDirectory("smwf-public-typed-start-").resolve("host.sqlite")
      val policy = take(EventPersistencePolicy.createC("public-typed-start-v1", Vector(
        EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
      def storage(granted: Boolean) = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"), collection("events"), collection("indexes"),
        if (granted) Vector(EventStorePersistenceDeclaration.WorkflowGrant(SmWorkflowComponent.componentId, "workflow", workflow.instances, workflow.continuations))
        else Vector(EventStorePersistenceDeclaration.WorkflowGrant(org.goldenport.cncf.component.ComponentId("org.example.Foreign"),
          "workflow", workflow.instances, workflow.continuations))))
      val contextGrant = take(ComponentRecordStorageDeclaration.createC(Vector(
        collection("contexts"), target(Purpose.Estimates), target(Purpose.Handoffs), collection("enrichments"),
        target(Purpose.Planning), target(Purpose.Proposal), target(Purpose.Ownership))
        .map(c => ComponentRecordStorageDeclaration.CreateGrant(SmWorkflowComponent.componentId, "workflow", c))))
      val rejected = Vector("absent", "future", "foreign-binding", "foreign-issuer", "throwing", "null-outcome")
      val redundantreadbacks = for (stage <- Vector("create", "append"); failure <- Vector("missing", "failed", "divergent")) yield s"$stage-$failure"
      val allocations = (Vector("accepted", "foreign-history", "untrusted", "partition-issue") ++
        completionIds.sorted ++ _negative_ids.sorted ++ rejected ++ redundantreadbacks).map(allocation(_)) :+
        allocation("foreign-actor", "foreign")
      var installed = false
      var originalWire = Option.empty[String]
      var partitionWire = Option.empty[String]
      var completionFrames = Map.empty[String, ContinuationResult[S.Judgment]]
      var completionResponses = Map.empty[String, String]
      // Observations copied from issued WorkOrders, never a completion allocation input.
      var issuedReplies = Map.empty[String, S.Reply]
      var occupiedRows = Map.empty[String, CU.Row]
      var masked = Option.empty[String]
      var fault = Option.empty[String]
      var verifierCalls = 0
      var lastCall = Option.empty[ActionCall.Core]
      var lastAdmission = Option.empty[H.Admitted]
      var retainedOperation = Option.empty[spec.OperationDefinition]
      for (mode <- Vector("missing-enrichment-grant", "initial", "reopen", "reopen-reply-changed", "undeclared", "ungranted")) {
        val ctx = ExecutionContext.create()
        val declared = if (mode == "undeclared") values.removed(tp + "record-collection")
          else if (mode == "reopen") values.removed(_rp + "policy-identity").removed(_rp + "policy-revision")
            .removed(_rp + "namespace").removed(_ap + "policy-identity").removed(_ap + "policy-revision").removed(_ap + "namespace")
          else if (mode == "reopen-reply-changed") values.updated(_rp + "namespace",
            ConfigurationValue.StringValue("changed-native-replies")).updated(_ap + "namespace",
            ConfigurationValue.StringValue("changed-native-candidates")) else values
        val configured = host(declared ++ Map("textus.subsystem.user-mode" -> ConfigurationValue.StringValue("standalone"),
          "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
          "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString)))
        val grants = if (mode == "missing-enrichment-grant") take(ComponentRecordStorageDeclaration.createC(Vector(
          collection("contexts"), target(Purpose.Handoffs), collection("enrichments"),
          target(Purpose.Planning), target(Purpose.Proposal), target(Purpose.Ownership))
          .map(c => ComponentRecordStorageDeclaration.CreateGrant(SmWorkflowComponent.componentId, "workflow", c)))) else contextGrant
        val bootstrap = take(SubsystemEventStoreBootstrap.createC(policy, storage(mode != "ungranted"),
          ScopeContext(ScopeKind.Subsystem, "public-typed-start", None, ctx.observability), configured, None,
          org.goldenport.cncf.path.AliasResolver.empty, grants))
        try {
          val owner = RuntimeBindingAdmissionFixture.admit(bootstrap.subsystem)
          val managed = CallScopedWorkflowInstances.managed(configuration, catalog.definition, workflow.instances, workflow.continuations)
          val observing = new CallScopedWorkflowInstances.Port {
            def createForCallC(c: Configuration, record: InstanceRecord, core: ActionCall.Core) =
              managed.createForCallC(c, record, core).map { r =>
                if (fault.exists(_.startsWith("create-"))) { masked = fault; fault = None }; r
              }
            def appendForCallC(c: Configuration, id: InstanceIdentity, revision: InstanceRevision, entry: HistoryEntry, core: ActionCall.Core) =
              managed.appendForCallC(c, id, revision, entry, core).map { r =>
                if (fault.exists(_.startsWith("append-"))) { masked = fault; fault = None }; r
              }
            def loadForCallC(c: Configuration, id: InstanceIdentity, core: ActionCall.Core): Consequence[Option[InstanceRecord]] = {
              val result = managed.loadForCallC(c, id, core)
              val failure = masked
              masked = None
              failure match {
                case Some(f) if f.endsWith("missing") => result.map(_ => None)
                case Some(f) if f.endsWith("failed") => Consequence.serviceUnavailable("Private committed readback observation interruption")
                case Some(_) => result.map(_.map(_.copy(identity = InstanceIdentity("private-divergent-observation"))))
                case None => result
              }
            }
          }
          // This explicit fixture verifies no real human evidence. Native actor
          // authentication and actual storage/dispatch are exercised separately.
          val evidence = new H.EvidencePort {
            def admitC(core: ActionCall.Core, lookupIdentity: String, target: Json): Consequence[Unit] = {
              verifierCalls += 1
              lastCall = Some(core)
              val id = io.circe.parser.parse(lookupIdentity).toOption.get.asArray.get.head.asString.get
              val issued = NativeHostEvidence.Evidence(NativeHostEvidence.schema, H.purpose, lookupIdentity,
                "private-human-" + id, "1", "private-start-source", "private-human", target, now.minusSeconds(86400).toString)
              val observed = id match {
                case "absent" => None
                case "future" => Some(issued.copy(occurredAt = core.executionContext.clock.instant().plusSeconds(1).toString))
                case "foreign-binding" =>
                  val crossed = target.mapObject(o => o.add("startAttestation",
                    o("startAttestation").get.mapObject(_.add("instanceIdentity", Json.fromString("foreign")))))
                  Some(issued.copy(binding = crossed))
                case "foreign-issuer" => Some(issued.copy(issuer = "foreign"))
                case "throwing" => throw new IllegalStateException("Private verifier observation failure")
                case "null-outcome" => return null
                case _ => Some(issued)
              }
              NativeHostEvidenceFixtureAdmission.admitC(core, "private-start-source", NativeHostEvidence.Purpose.SplitStart,
                lookupIdentity, target, observed)
            }
          }
          val nativeHost = SplitPhaseManagedTypedStartAssembly.Host(evidence)
          if (mode == "ungranted") {
            SplitPhaseManagedTypedStartAssembly.bindHostC(ComponentCreate(owner, ComponentOrigin.Builtin), workflow, catalog, nativeHost)
              (using ctx).isSuccess shouldBe false
            SplitPhaseManagedTypedPartitionIssue.bindC(ComponentCreate(owner, ComponentOrigin.Builtin), workflow, catalog)
              .isSuccess shouldBe false
            SplitPhaseManagedTypedPartitionCompletion.bindHostC(ComponentCreate(owner, ComponentOrigin.Builtin), workflow, catalog).isSuccess shouldBe false
          } else {
            val inputs = take(SplitPhaseTypedStartReadPolicy.bindC(host(values), workflow, catalog))
            val revision = take(SplitPhaseManagedTypedStartAssembly.revisionC(host(values)))
            var fixtureTask: FixtureCall => Consequence[Unit] = _ => Consequence.unit
            final class FixtureCall(val core: ActionCall.Core) extends ProcedureActionCall {
              def writeC(c: DataStore.CollectionId, id: String, row: Record, update: Boolean): Consequence[Unit] = {
                given ExecutionContext = executionContext
                val space = executionContext.dataStoreSpace
                val prior = space.captureBinding()
                try bind_component_application_datastore_c("workflow").flatMap(_ => space.dataStore(c).flatMap(store =>
                  if (update) store.update(c, DataStore.StringEntryId(id), row) else store.create(c, DataStore.StringEntryId(id), row)))
                finally space.restoreBinding(prior)
              }
              def loadC(id: String) = managed.loadForCallC(configuration, InstanceIdentity("instance-" + id), core)
              def readRowC(c: DataStore.CollectionId, id: String): Consequence[Option[Record]] = {
                given ExecutionContext = executionContext
                val space = executionContext.dataStoreSpace
                val prior = space.captureBinding()
                try bind_component_application_datastore_c("workflow").flatMap(_ => space.dataStore(c)
                  .flatMap(_.load(c, DataStore.StringEntryId(id))))
                finally space.restoreBinding(prior)
              }
              def installC(): Consequence[Unit] = {
                val rows = inputRows.map { case (c, id, raw) => (c, id, Record.data(J.storageField -> raw)) } ++ allocations.flatMap { a => Vector(
                  (collection("starts"), a.reference.identity, Record.data(TJ.storageField -> take(TJ.encodeAllocationC(a)))),
                  (collection("selections"), a.command.selectionIdentity, Record.data(ProfileSelectionJsonV1.storageField ->
                    take(ProfileSelectionJsonV1.encodeStorageC(a.selection))))) }
                val foreign = allocations.find(_.reference.identity == "foreign-history").get
                val initial = take(T.prepareC(foreign, source, Some(proposal), foreign.selection, catalog, configuration)).initial
                  .copy(causation = CausationReference("foreign-original-start"))
                rows.foldLeft(Consequence.unit) { case (before, (c, id, row)) => before.flatMap(_ => writeC(c, id, row, false)) }
                  .flatMap(_ => managed.createForCallC(configuration, initial, core)).map(_ => ())
              }
              def execute(): Consequence[OperationResponse] = fixtureTask(this).map(_ => OperationResponse.Scalar("private-fixture"))
            }
            val fixtureOperation = new spec.OperationDefinition {
              val specification = spec.OperationDefinition.Specification(name = "privateFixture", request = WorkflowJsonCommandRequest.definition,
                response = spec.ResponseDefinition.void)
              def createOperationRequest(r: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
                val request = r
                override def commandExecutionMode = CommandExecutionMode.Sync
                def createCall(c: ActionCall.Core): ActionCall = new FixtureCall(c)
              })
            }
            // Test-only exact-advance read probe, not default public installation.
            val advanceProbe = new spec.OperationDefinition {
              val specification = spec.OperationDefinition.Specification(name = "advanceSplitPhase",
                request = WorkflowJsonCommandRequest.definition, response = spec.ResponseDefinition.void)
              def createOperationRequest(r: Request): Consequence[OperationRequest] = fixtureOperation.createOperationRequest(r)
            }
            var completionBinding = Option.empty[SplitPhaseManagedTypedPartitionCompletion.Binding]
            val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty, (creation, parameters, resolved) => {
              given ExecutionContext = resolved
              val start = if (mode == "undeclared") Consequence.success(retainedOperation.get)
                else SplitPhaseManagedTypedStartAssembly.bindHostC(creation, workflow, catalog, nativeHost).flatMap { _ =>
                  // This test decorates only redundant post-write loads, never
                  // the native mutation's authoritative result or real CAS.
                  SplitPhaseTypedStartServiceOperation.bindC(inputs, revision, nativeHost, observing)
                }
              start.flatMap { op =>
                retainedOperation = Some(op)
                val completion = if (mode == "undeclared") Consequence.success(None)
                  else SplitPhaseManagedTypedPartitionCompletion.bindHostC(creation, workflow, catalog).map(Some(_))
                completion.map { bound =>
                  completionBinding = bound
                  val operations = Vector(op, fixtureOperation, advanceProbe) ++ bound.map(_.operation)
                  spec.ServiceDefinition("SplitPhaseService", spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(operations)))
                }
              }
            }).primaryFactory
            val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
            owner.add(built)
            owner.withDescriptor(GenericSubsystemDescriptor(path = Path.of("<public-typed-start-spec>"), subsystemName = "public-typed-start",
              componentBindings = Vector(GenericSubsystemComponentBinding(built.name)), security = Some(GenericSubsystemSecurityBinding(
                authentication = Some(GenericSubsystemAuthenticationBinding(convention = Some("enabled"), fallbackPrivilege = Some("disabled"),
                  localSubject = Some(GenericSubsystemLocalSubjectBinding(id = "participant", roles = Vector("user"), capabilities = Vector("user"),
                    attributes = Map.empty, securityLevel = Some("user")))))))))
            val actor = take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.Fixed, built.logic.executionContext(), Map.empty)).executionContext
            def fixture(f: FixtureCall => Consequence[Unit]): Unit = {
              fixtureTask = f
              take(owner.executeOperationResponseInContext(Request.of(component = built.name, service = "SplitPhaseService", operation = "privateFixture",
                arguments = List(Argument("arg1", "private-test-fixture"))), actor))
            }
            def invoke(a: T.Record, cli: Boolean, command: SplitPhaseStartCommand = null): Consequence[OperationResponse] = {
              val wire = SplitPhaseStartServiceOperation.encodeCommand(if (command == null) a.command else command)
              if (cli) new CncfRuntime().executeCommandResponse(owner, Array(s"${built.name}/SplitPhaseService/startSplitPhase", "--json", wire))
                .map(response => OperationResponse.Scalar(response.print))
              else owner.executeOperationResponseInContext(Request.of(component = built.name, service = "SplitPhaseService", operation = "startSplitPhase",
                arguments = List(Argument("arg1", wire))), actor)
            }
            val accepted = allocations.head
            if (!installed) { fixture(_.installC()); installed = true }
            if (mode == "undeclared") {
              val calls = verifierCalls
              invoke(allocation("not-installed"), false).isSuccess shouldBe false
              invoke(accepted, false).isSuccess shouldBe false
              invoke(accepted, true).isSuccess shouldBe false
              verifierCalls shouldBe calls
            } else {
              val issuing = take(SplitPhaseManagedTypedPartitionIssue.bindC(ComponentCreate(owner, ComponentOrigin.Builtin), workflow, catalog))
              def complete(frame: ContinuationResult[S.Judgment], cli: Boolean): Consequence[OperationResponse] = {
                val wire = take(SJ.encodeResultC(frame))
                if (cli) new CncfRuntime().executeCommandResponse(owner, Array(
                  s"${built.name}/SplitPhaseService/submitPartitionSemanticResult", "--json", wire))
                  .map(response => OperationResponse.Scalar(response.print))
                else owner.executeOperationResponseInContext(Request.of(component = built.name, service = "SplitPhaseService",
                  operation = "submitPartitionSemanticResult", arguments = List(Argument("arg1", wire))), actor)
              }
              val attempted = if (mode == "missing-enrichment-grant") Vector("partition-completion-missing-grant")
                else if (mode == "initial") _collision_ids else Vector.empty[String]
              attempted.foreach { id =>
                val a = allocations.find(_.reference.identity == id).get
                take(invoke(a, false))
                var frame = Option.empty[ContinuationResult[S.Judgment]]
                fixture { call =>
                  val current = take(call.loadC(id)).get
                  val raw = SplitPhasePartitionContextFixture.record.copy(reference = S.ContextReference(id + "-context", "1"),
                    owner = SplitPhasePartitionContextFixture.record.owner.copy(start = a.reference,
                      handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current)),
                      instanceRevision = current.revision, continuation = org.goldenport.cncf.workflow.ContinuationIdentity(id + "-work")))
                  val registry = take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, workflow, catalog))
                  take(registry.registerForCallC(raw, call.core)) shouldBe raw
                  val bound = take(inputs.bindPartitionForCallC(raw.reference, call.core)).get
                  val work = take(org.goldenport.cncf.workflow.WorkflowWorkOrderJsonV1.decodeC(
                    take(issuing.issueForCallC(bound, call.core)), SJ.requestCodec))
                  val issuedreply = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value.reply
                  issuedReplies += id -> issuedreply
                  frame = Some(semanticResult(work, false))
                  if (_collision_ids.contains(id)) {
                    // Fixture-only projection chooses the actual row to occupy.
                    // It does not supply a witness or replace public completion.
                    val pending = take(PC.pendingC(raw, facts, bound.start, take(call.loadC(id)).get,
                      work, configuration, catalog))
                    val baseline = take(PC.admitC(pending, frame.get))
                    val need = baseline.mechanical.get.asInstanceOf[SplitPhaseExplicitPlanningAdvance.AllocationRequired]
                    val policy = take(SplitPhaseCandidateAllocationPolicy.decodeC(host(values)))
                    val captured = take(AC.captureC(take(PC.continueAllocatedC(baseline,
                      take(policy.allocateC(need, issuedreply.result))))))
                    val rows = take(AU.prepareRowsC(captured, take(CU.bindTargetsC(host(values), workflow))))
                    val saved = captured.record.checkpoint.get
                    val selected = id match {
                      case "partition-completion-collision" => rows.find(_.identity == issuedreply.result.identity).get
                      case "partition-completion-lineage-collision" => rows.find(_.identity == take(AC.lineageKeyC(frame.get.continuationId))).get
                      case "partition-completion-map-collision" => rows.find(_.identity == take(CU.mappingKeyC(saved.reference))).get
                      case "partition-completion-index-collision" => rows.find(_.identity == take(CU.indexKeyC(saved.originalStart.instanceIdentity,
                        saved.originalContext.reference))).get
                      case "partition-completion-candidate-collision" => rows.find(_.collection == target(Purpose.Proposal)).get
                    }
                    val occupied = selected.copy(payload = Record.data(selected.payload.fields.head.key -> "preexisting-collision"))
                    occupiedRows += id -> occupied
                    take(call.writeC(occupied.collection, occupied.identity, occupied.payload, false))
                  }
                  Consequence.unit
                }
                When("completion encounters an absent captured Create grant or an existing immutable publication identity")
                complete(frame.get, id.endsWith("collision")).isSuccess shouldBe false
              }
              val observednegatives = if (mode == "missing-enrichment-grant") Vector("partition-completion-missing-grant")
                else _negative_ids.sorted
              observednegatives.foreach { id => fixture { call =>
                Then("raw completion, closing history and all new rows remain absent, including after physical reopen")
                val allocated = issuedReplies(id)
                val continuation = org.goldenport.cncf.workflow.ContinuationIdentity(id + "-work")
                val current = take(call.loadC(id)).get
                current.revision shouldBe InstanceRevision(2)
                current.history.size shouldBe 2
                current.suspension.get.continuationIdentity.value shouldBe continuation.value
                take(completionBinding.get.loadRetainedForCallC(continuation, call.core)) shouldBe None
                take(completionBinding.get.recoverForCallC(continuation, call.core)) shouldBe None
                take(call.readRowC(target(Purpose.Estimates), allocated.resolutions.estimates.get.identity)) shouldBe None
                take(call.readRowC(target(Purpose.Handoffs), allocated.resolutions.handoffs.get.identity)) shouldBe None
                // Only expected-row projection occurs in this labeled fixture;
                // production recovery above still returns None without allocating any references.
                val work = take(issuing.loadForCallC(continuation, call.core)).get
                val request = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value
                val contexts = take(SplitPhasePartitionContextReadPolicy.bindC(owner.configuration, workflow))
                val raw = take(contexts.loadForCallC(request.context, call.core)).get
                val start = take(inputs.prepareForCallC(raw.owner.start, call.core)).get
                val pending = take(PC.pendingC(raw, facts, start, current, work, configuration, catalog))
                val baseline = take(PC.admitC(pending, semanticResult(work, false)))
                val need = baseline.mechanical.get.asInstanceOf[SplitPhaseExplicitPlanningAdvance.AllocationRequired]
                val policy = take(SplitPhaseCandidateAllocationPolicy.decodeC(host(values)))
                val plan = take(PC.continueAllocatedC(baseline, take(policy.allocateC(need, allocated.result))))
                val projection = take(U.projectC(plan)).get
                take(call.readRowC(target(Purpose.Planning), projection.record.planning.get.identity)) shouldBe None
                projection.candidates should not be empty
                projection.candidates.foreach { candidate =>
                  if (id != "partition-completion-candidate-collision")
                    take(call.readRowC(target(Purpose.Proposal), candidate.proposal.reference.identity)) shouldBe None
                  take(call.readRowC(target(Purpose.Ownership), candidate.ownership.reference.identity)) shouldBe None
                  take(call.readRowC(target(Purpose.Handoffs), candidate.handoffs.reference.identity)) shouldBe None
                }
                val prepared = take(AU.prepareRowsC(take(AC.captureC(plan)), take(CU.bindTargetsC(host(values), workflow))))
                prepared.foreach { row =>
                  val observed = take(call.readRowC(row.collection, row.identity))
                  occupiedRows.get(id).filter(r => r.collection == row.collection && r.identity == row.identity) match {
                    case Some(occupied) => observed.get.getString(occupied.payload.fields.head.key) shouldBe Some("preexisting-collision")
                    case None => observed shouldBe None
                  }
                }
                val mapping = take(call.readRowC(collection("enrichments"), allocated.result.identity))
                if (id == "partition-completion-collision") mapping.get.getString(EJ.storageField) shouldBe Some("preexisting-collision")
                else mapping shouldBe None
                Consequence.unit
              } }
              if (mode != "missing-enrichment-grant") {
              val partitionAllocation = allocations.find(_.reference.identity == "partition-issue").get
              take(invoke(partitionAllocation, false))
              var escapedPartition = Option.empty[T.PartitionBound]
              var escapedPartitionCall = Option.empty[ActionCall.Core]
              fixture { call =>
                val continuation = org.goldenport.cncf.workflow.ContinuationIdentity("typed-partition-issued")
                val prior = call.executionContext.dataStoreSpace.captureBinding()
                if (mode == "initial") {
                  val current = take(call.loadC("partition-issue")).get
                  val context = SplitPhasePartitionContextFixture.record.copy(reference = S.ContextReference("typed-issued-context", "1"),
                    owner = SplitPhasePartitionContextFixture.record.owner.copy(start = partitionAllocation.reference,
                      handle = take(WorkflowProtocolV1.WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current)),
                      instanceRevision = current.revision, continuation = continuation))
                  val registry = take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, workflow, catalog))
                  take(registry.registerForCallC(context, call.core)) shouldBe context
                  val bound = take(inputs.bindPartitionForCallC(context.reference, call.core)).get
                  val foreignCommand = partitionAllocation.command.copy(participantIdentity = "foreign")
                  val foreign = partitionAllocation.copy(command = foreignCommand, selection = selectionFor(foreignCommand))
                  val foreignBound = take(T.bindPartitionC(take(T.prepareC(foreign, source, Some(proposal), foreign.selection,
                    catalog, configuration)), bound.context))
                  issuing.issueForCallC(foreignBound, call.core).isSuccess shouldBe false
                  val forged = take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.ControlledTest, call.executionContext,
                    Map("principal.id" -> "participant", "privilege" -> "user", "cncf.security.authenticated" -> "true"))).executionContext
                  issuing.issueForCallC(bound, call.core.copy(executionContext = forged)).isSuccess shouldBe false
                  take(call.loadC("partition-issue")).get shouldBe current
                  val wire = take(issuing.issueForCallC(bound, call.core))
                  val decoded = take(org.goldenport.cncf.workflow.WorkflowWorkOrderJsonV1.decodeC(wire,
                    SplitPhaseExplicitPartitionSemanticJson.requestCodec))
                  decoded.handle shouldBe context.owner.handle
                  val request = decoded.current.asInstanceOf[WorkflowProtocolV1.WorkflowContinuation.WorkOrder[S.Request]].request
                  val input = request.input.get.value
                  val draft = bound.context.request
                  S.DraftRequest(input.context, input.source, input.items, input.boundaries) shouldBe draft
                  input.reply.result.identity should startWith ("sm-split-reply:native-replies:")
                  input.reply.resolutions.estimates should not be empty
                  input.reply.resolutions.handoffs should not be empty
                  request.input.get.value.items.map(_.identity.value) shouldBe Vector("item-1")
                  request.input.get.value.boundaries.map(_.edge.afterItem.value) shouldBe Vector("item-0")
                  request.completionOperation shouldBe C.completionOperation
                  partitionWire = Some(wire)
                  escapedPartition = Some(bound)
                  escapedPartitionCall = Some(call.core)
                  // A repeated mutation is not an immutable-work recovery API.
                  issuing.issueForCallC(bound, call.core).isSuccess shouldBe false
                }
                val observed = take(issuing.loadForCallC(continuation, call.core)).get
                take(SplitPhaseExplicitPartitionSemanticJson.encodeWorkOrderC(observed)) shouldBe partitionWire.get
                val retained = take(call.loadC("partition-issue")).get
                retained.history.size shouldBe 2
                retained.suspension.get.continuationIdentity.value shouldBe continuation.value
                retained.currentProgression shouldBe Some(ProgressionReference("entry.EntryAssessment"))
                call.executionContext.dataStoreSpace.captureBinding() shouldBe prior
                Consequence.unit
              }
              escapedPartition.foreach { bound =>
                issuing.issueForCallC(bound, escapedPartitionCall.get).isSuccess shouldBe false
                issuing.loadForCallC(bound.context.record.owner.continuation, escapedPartitionCall.get).isSuccess shouldBe false
              }
              completionIds.sorted.foreach { id =>
                val a = allocations.find(_.reference.identity == id).get
                take(invoke(a, false))
                if (mode == "initial") {
                  fixture { call =>
                    val current = take(call.loadC(id)).get
                    val context = SplitPhasePartitionContextFixture.record.copy(reference = S.ContextReference(id + "-context", "1"),
                      owner = SplitPhasePartitionContextFixture.record.owner.copy(start = a.reference,
                        handle = take(WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current)),
                        instanceRevision = current.revision, continuation = org.goldenport.cncf.workflow.ContinuationIdentity(id + "-work")))
                    val registry = take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, workflow, catalog))
                    take(registry.registerForCallC(context, call.core)) shouldBe context
                    val bound = take(inputs.bindPartitionForCallC(context.reference, call.core)).get
                    val issued = take(org.goldenport.cncf.workflow.WorkflowWorkOrderJsonV1.decodeC(
                      take(issuing.issueForCallC(bound, call.core)), SJ.requestCodec))
                    issuedReplies += id -> issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request.input.get.value.reply
                    completionFrames += id -> semanticResult(issued, id.endsWith("decision"))
                    Consequence.unit
                  }
                  val frame = completionFrames(id)
                  complete(frame.copy(resultReference = ContextReference("worker-selected-result", "1")), false).isSuccess shouldBe false
                  complete(frame.copy(expectedRevision = org.goldenport.cncf.workflow.StateMachineRevision("99")), true)
                    .isSuccess shouldBe false
                  val foreignCommand = a.command.copy(participantIdentity = "foreign")
                  val foreignStart = a.copy(command = foreignCommand, selection = selectionFor(foreignCommand))
                  fixture(_.writeC(collection("starts"), a.reference.identity, Record.data(TJ.storageField -> take(TJ.encodeAllocationC(foreignStart))), true))
                  complete(frame, false).isSuccess shouldBe false
                  fixture(_.writeC(collection("starts"), a.reference.identity, Record.data(TJ.storageField -> take(TJ.encodeAllocationC(a))), true))
                  fixture(_.writeC(target(Purpose.Source), source.reference.identity, Record.data(J.storageField ->
                    take(J.encodeSourceC(source.copy(authority = source.authority.copy(revision = "2"))))), true))
                  complete(frame, false).isSuccess shouldBe false
                  fixture(_.writeC(target(Purpose.Source), source.reference.identity, Record.data(J.storageField -> take(J.encodeSourceC(source))), true))
                  fixture { call => take(call.loadC(id)).get.history.size shouldBe 2; Consequence.unit }
                  val wire = scalar(take(complete(frame, id.endsWith("cli"))))
                  val waiting = take(WorkflowWaitJsonV1.decodeC(wire)).current.asInstanceOf[WorkflowContinuation.Wait]
                  waiting.expectedRevision.value shouldBe "3"
                  if (id.endsWith("decision")) waiting.presentation.title shouldBe "HumanDirectionRequired"
                  else waiting.presentation.title should not be "HostAllocationRequired"
                  completionResponses += id -> wire
                }
                val frame = completionFrames(id)
                val allocated = issuedReplies(id)
                When("the exact completed partition result is submitted again through its Operation and adapter")
                val servicewire = scalar(take(complete(frame, false)))
                val cliwire = scalar(take(complete(frame, true)))
                Then("both return the original saved acknowledgement without another completion")
                servicewire shouldBe completionResponses(id)
                cliwire shouldBe completionResponses(id)
                var expired = Option.empty[ActionCall.Core]
                fixture { call =>
                  When("canonical completion is recovered on the current native call, including physical reopen")
                  val human = verifierCalls
                  if (mode == "initial" && id.endsWith("service")) {
                    val prior = take(call.loadC(id)).get
                    take(managed.appendForCallC(configuration, prior.identity, prior.revision, prior.history.last.copy(
                      sequence = HistorySequence(4), revision = InstanceRevision(4),
                      causation = CausationReference("later-native-operation")), call.core))
                  }
                  val current = take(call.loadC(id)).get
                  current.history.size shouldBe (if (id.endsWith("service")) 4 else 3)
                  current.suspension shouldBe None
                  current.currentProgression shouldBe Some(ProgressionReference("entry.EntryAssessment"))
                  current.history(2).causation.value shouldBe allocated.result.identity
                  val retained = take(completionBinding.get.loadRetainedForCallC(frame.continuationId, call.core)).get
                  retained.result shouldBe frame
                  retained.instanceRevision shouldBe InstanceRevision(3)
                  retained.closingResult.get.typeReference shouldBe PC.closingType
                  retained.closingResult.get.contextReference shouldBe frame.resultReference
                  val recovered = take(completionBinding.get.recoverForCallC(frame.continuationId, call.core)).get
                  Then("the complete atomic publication retains issued references despite missing or changed reply policy, without human verification")
                  recovered.current shouldBe current
                  recovered.record.completion.result shouldBe frame
                  recovered.record.wire shouldBe completionResponses(id)
                  verifierCalls shouldBe human
                  val lineageKey = take(AC.lineageKeyC(frame.continuationId))
                  val lineage = take(call.readRowC(collection("enrichments"), lineageKey)).get
                  take(AJ.decodeC(frame.continuationId, lineage.getString(AJ.storageField).get, Vector(catalog.definition))) shouldBe recovered.record
                  val projected = recovered.record.header.map { header =>
                    val k = recovered.record.checkpoint.get
                    ObservedRows(header, header.planning.map(_ => k.snapshot.selection.planning),
                      header.estimates.map(_ => k.snapshot.selection.partition.estimates),
                      header.handoffs.map(_ => k.snapshot.selection.boundary.handoffs), CO.builtDocuments(k.snapshot))
                  }
                  val mapping = take(call.readRowC(collection("enrichments"), allocated.result.identity))
                  projected match {
                    case None => mapping shouldBe None
                    case Some(expected) =>
                      take(EJ.decodeC(allocated.result, mapping.get.getString(EJ.storageField).get)) shouldBe expected.record
                      expected.record.planning shouldBe expected.planning.map(_.reference)
                      expected.record.planning.get.identity should include ("sm-split:native-candidates:")
                      val saved = recovered.record.checkpoint.get
                      saved.origin shouldBe CK.Origin.PartitionCompletion(frame.continuationId, allocated.result)
                      take(CJ.decodeC(saved.reference, take(call.readRowC(collection("enrichments"),
                        take(CU.mappingKeyC(saved.reference)))).get.getString(CJ.storageField).get)) shouldBe saved
                      val indexKey = take(CU.indexKeyC(current.identity, saved.originalContext.reference))
                      val index = take(call.readRowC(collection("enrichments"), indexKey)).get
                      take(CJ.decodeIndexC(current.identity, saved.originalContext.reference, index.getString(CJ.indexStorageField).get)) shouldBe CK.index(saved)
                      val planning = take(call.readRowC(target(Purpose.Planning), expected.record.planning.get.identity)).get
                      take(J.decodePlanningC(expected.record.planning.get, planning.getString(J.storageField).get)) shouldBe expected.planning.get
                      expected.candidates should not be empty
                      expected.candidates.foreach { candidate =>
                        val proposalrow = take(call.readRowC(target(Purpose.Proposal), candidate.proposal.reference.identity)).get
                        take(J.decodeProposalC(candidate.proposal.reference, proposalrow.getString(J.storageField).get)) shouldBe candidate.proposal
                        val ownershiprow = take(call.readRowC(target(Purpose.Ownership), candidate.ownership.reference.identity)).get
                        take(J.decodeOwnershipC(candidate.ownership.reference, ownershiprow.getString(J.storageField).get)) shouldBe candidate.ownership
                        val handoffrow = take(call.readRowC(target(Purpose.Handoffs), candidate.handoffs.reference.identity)).get
                        take(J.decodeHandoffsC(candidate.handoffs.reference, handoffrow.getString(J.storageField).get)) shouldBe candidate.handoffs
                      }
                      Given("a completed native snapshot whose separate effective Planning row is no longer consumed")
                      val divergentplanning = expected.planning.get.copy(authority = expected.planning.get.authority.copy(revision = "foreign"))
                      take(call.writeC(target(Purpose.Planning), expected.record.planning.get.identity,
                        Record.data(J.storageField -> take(J.encodePlanningC(divergentplanning))), true))
                      When("retained completion is observed after only that unused row changes")
                      val planningrecovery = take(completionBinding.get.recoverForCallC(frame.continuationId, call.core)).get
                      Then("the actual native capture and history remain observable without a current-copy certificate")
                      planningrecovery.record shouldBe recovered.record
                      planningrecovery.current shouldBe current
                      take(call.writeC(target(Purpose.Planning), expected.record.planning.get.identity,
                        Record.data(J.storageField -> planning.getString(J.storageField).get), true))
                      val candidate = expected.candidates.head
                      val candidatepayload = take(J.encodeOwnershipC(candidate.ownership))
                      Given("a native completion whose generated Ownership document is not an acknowledgement input")
                      When("that separate declared document is unreadable during completion recovery")
                      take(call.writeC(target(Purpose.Ownership), candidate.ownership.reference.identity,
                        Record.data(J.storageField -> "malformed-candidate-payload"), true))
                      val ownershiprecovery = take(completionBinding.get.recoverForCallC(frame.continuationId, call.core)).get
                      Then("canonical completion remains observable without another native transition")
                      ownershiprecovery.record shouldBe recovered.record
                      ownershiprecovery.current shouldBe current
                      take(call.writeC(target(Purpose.Ownership), candidate.ownership.reference.identity,
                        Record.data(J.storageField -> candidatepayload), true))
                      val estimates = take(call.readRowC(target(Purpose.Estimates), expected.record.estimates.get.identity)).get
                      take(J.decodeEstimatesC(expected.record.estimates.get, estimates.getString(J.storageField).get)) shouldBe expected.estimates.get
                      val handoffs = take(call.readRowC(target(Purpose.Handoffs), expected.record.handoffs.get.identity)).get
                      take(J.decodeHandoffsC(expected.record.handoffs.get, handoffs.getString(J.storageField).get)) shouldBe expected.handoffs.get
                      Given("the native completion retains estimates while the separate effective Estimates row changes")
                      val divergent = expected.estimates.get.copy(items = expected.estimates.get.items.updated(0,
                        expected.estimates.get.items.head.copy(duration = E.Duration.Known(1))))
                      take(call.writeC(target(Purpose.Estimates), expected.record.estimates.get.identity,
                        Record.data(J.storageField -> take(J.encodeEstimatesC(divergent))), true))
                      When("retained completion consumes its captured snapshot rather than that unused Estimates row")
                      val estimaterecovery = take(completionBinding.get.recoverForCallC(frame.continuationId, call.core)).get
                      Then("the original completion and current history are recovered without recomputation")
                      estimaterecovery.record shouldBe recovered.record
                      estimaterecovery.current shouldBe current
                      take(call.writeC(target(Purpose.Estimates), expected.record.estimates.get.identity,
                        Record.data(J.storageField -> estimates.getString(J.storageField).get), true))
                      Given("the canonical native completion and a separate unused diagnostic header")
                      When("the header's captured continuation identity changes")
                      take(call.writeC(collection("enrichments"), allocated.result.identity, Record.data(EJ.storageField ->
                        take(EJ.encodeC(expected.record.copy(continuationIdentity = "foreign-continuation")))), true))
                      val headerrecovery = take(completionBinding.get.recoverForCallC(frame.continuationId, call.core)).get
                      Then("header copy equality does not gate the actual retained acknowledgement")
                      headerrecovery.record shouldBe recovered.record
                      headerrecovery.current shouldBe current
                      take(call.writeC(collection("enrichments"), allocated.result.identity,
                        Record.data(EJ.storageField -> mapping.get.getString(EJ.storageField).get), true))
                      Given("the same native completion with a malformed unused diagnostic header")
                      When("recovery reads its actual completion rather than the separate header")
                      take(call.writeC(collection("enrichments"), allocated.result.identity,
                        Record.data(EJ.storageField -> "malformed-publication"), true))
                      val malformedheaderrecovery = take(completionBinding.get.recoverForCallC(frame.continuationId, call.core)).get
                      Then("the original completion and current history remain observable")
                      malformedheaderrecovery.record shouldBe recovered.record
                      malformedheaderrecovery.current shouldBe current
                      take(call.writeC(collection("enrichments"), allocated.result.identity,
                        Record.data(EJ.storageField -> mapping.get.getString(EJ.storageField).get), true))
                  }
                  Given("the actual required calculation and response lineage")
                  When("its payload becomes unreadable")
                  take(call.writeC(collection("enrichments"), lineageKey, Record.data(AJ.storageField -> "malformed-lineage"), true))
                  val brokenlineage = completionBinding.get.recoverForCallC(frame.continuationId, call.core)
                  Then("recovery cannot return an unreadable response and calculation capture")
                  brokenlineage.isSuccess shouldBe false
                  take(call.writeC(collection("enrichments"), lineageKey,
                    Record.data(AJ.storageField -> lineage.getString(AJ.storageField).get), true))
                  Given("independent Context, Start, input, mapping, index and generated rows unused by acknowledgement")
                  val extraRows = recovered.record.checkpoint.toVector.flatMap { saved =>
                    Vector((collection("enrichments"), take(CU.mappingKeyC(saved.reference)), CJ.storageField),
                      (collection("enrichments"), take(CU.indexKeyC(current.identity, saved.originalContext.reference)), CJ.indexStorageField)) ++
                    CO.builtDocuments(saved.snapshot).flatMap(d => Vector(
                      (target(Purpose.Proposal), d.proposal.reference.identity, J.storageField),
                      (target(Purpose.Ownership), d.ownership.reference.identity, J.storageField),
                      (target(Purpose.Handoffs), d.handoffs.reference.identity, J.storageField)))
                  }
                  val observedrows = (inputRows.map((c, identity, _) => (c, identity, J.storageField)) ++ extraRows ++
                    Vector((collection("contexts"), recovered.record.context.reference.identity, CC.storageField),
                      (collection("starts"), a.reference.identity, TJ.storageField))).distinct
                  observedrows.foreach { (c, identity, field) =>
                    val before = take(call.readRowC(c, identity)).get.getString(field).get
                    When(s"only the payload of $identity is independently damaged")
                    take(call.writeC(c, identity, Record.data(field -> "malformed-independent-required-row"), true))
                    val observed = completionBinding.get.recoverForCallC(frame.continuationId, call.core)
                    Then("an unused independent row does not prevent recovery of the actual native completion and history")
                    val retained = take(observed).get
                    retained.record shouldBe recovered.record
                    retained.current shouldBe current
                    take(call.writeC(c, identity, Record.data(field -> before), true))
                    When("the independent row is restored and the same completion is observed")
                    val restored = take(completionBinding.get.recoverForCallC(frame.continuationId, call.core)).get
                    Then("restoration permits recovery of exactly the original native capture")
                    restored.record shouldBe recovered.record
                  }
                  Given("the original authenticated participant and completed native capture, with changed allocation participant metadata")
                  val foreigncommand = a.command.copy(participantIdentity = "foreign")
                  val foreignstart = a.copy(command = foreigncommand, selection = selectionFor(foreigncommand))
                  take(call.writeC(collection("starts"), a.reference.identity,
                    Record.data(TJ.storageField -> take(TJ.encodeAllocationC(foreignstart))), true))
                  When("the original participant observes its actual retained completion")
                  val participantrecovery = take(completionBinding.get.recoverForCallC(frame.continuationId, call.core)).get
                  Then("canonical ownership is retained without treating the changed allocation as authority or a copy certificate")
                  participantrecovery.record shouldBe recovered.record
                  participantrecovery.record.start.command.participantIdentity shouldBe "participant"
                  participantrecovery.current shouldBe current
                  take(call.writeC(collection("starts"), a.reference.identity,
                    Record.data(TJ.storageField -> take(TJ.encodeAllocationC(a))), true))
                  take(completionBinding.get.recoverForCallC(frame.continuationId, call.core)).get.current shouldBe current
                  verifierCalls shouldBe human
                  expired = Some(call.core)
                  Consequence.unit
                }
                locally {
                  val command = org.simplemodeling.textus.smworkflow.value.SplitPhaseAdvanceCommand(a.instanceIdentity.value,
                    org.simplemodeling.textus.smworkflow.value.SplitPhasePartitionContextReference(id + "-context", "1"))
                  val wire = take(SplitPhaseAdvanceCommandJsonV1.encodeC(command))
                  fixtureTask = call => {
                    Given("an exact owning advance call observing an original AI completion")
                    val port = take(SplitPhaseManagedPlanningCheckpoint.bindC(owner.configuration, workflow, catalog))
                    val beforeverifiers = verifierCalls
                    val beforenative = take(call.loadC(id)).get
                    val beforework = take(call.readRowC(workflow.continuations, frame.continuationId.value))
                    val beforebinding = call.executionContext.dataStoreSpace.captureBinding()
                    When("Workflow selects the actual retained origin, including a human-direction completion without a checkpoint")
                    val routed = take(port.loadRetainedForCallC(command, call.core)).get
                    Then("the original AI completion is recovered without a caller-selected route, new planning or human verification")
                    routed.isInstanceOf[SplitPhaseManagedPlanningCheckpoint.RetainedPartitionCompletion] shouldBe true
                    val actual = routed.asInstanceOf[SplitPhaseManagedPlanningCheckpoint.RetainedPartitionCompletion].observed
                    actual.record.completion.result shouldBe frame
                    actual.record.wire shouldBe completionResponses(id)
                    actual.current shouldBe beforenative
                    actual.record.checkpoint.isEmpty shouldBe id.endsWith("decision")
                    take(call.loadC(id)).get shouldBe beforenative
                    take(call.readRowC(workflow.continuations, frame.continuationId.value)) shouldBe beforework
                    verifierCalls shouldBe beforeverifiers
                    call.executionContext.dataStoreSpace.captureBinding() shouldBe beforebinding
                    if (!id.endsWith("decision")) {
                      Given("an enriched completion with its actual immutable checkpoint")
                      When("the original explicit AI reader observes that same checkpoint")
                      val explicit = take(port.loadPartitionCompletionForCallC(command, call.core)).get
                      Then("both readers preserve identical original completion while mechanical-only reading refuses")
                      explicit.record shouldBe actual.record
                      explicit.current shouldBe actual.current
                      port.loadMechanicalForCallC(command, call.core).isSuccess shouldBe false
                      val documents = CO.builtDocuments(actual.record.checkpoint.get.snapshot).flatMap(d => Vector(
                        (target(Purpose.Proposal), d.proposal.reference.identity),
                        (target(Purpose.Ownership), d.ownership.reference.identity),
                        (target(Purpose.Handoffs), d.handoffs.reference.identity)))
                      documents.foreach { (collection, identity) =>
                        Given(s"a selected planning frontier declaring the generated document $identity")
                        val original = take(call.readRowC(collection, identity)).get
                        When("that actual declared input is unreadable at the planning consumer")
                        take(call.writeC(collection, identity, Record.data(J.storageField -> "unreadable-declared-input"), true))
                        val unreadablerouted = port.loadRetainedForCallC(command, call.core)
                        val unreadableexplicit = port.loadPartitionCompletionForCallC(command, call.core)
                        val unaffectedacknowledgement = take(completionBinding.get.recoverForCallC(frame.continuationId, call.core)).get
                        Then("both consuming readers refuse the unavailable input while native acknowledgement is unaffected")
                        unreadablerouted.isSuccess shouldBe false
                        unreadableexplicit.isSuccess shouldBe false
                        unaffectedacknowledgement.record shouldBe actual.record
                        unaffectedacknowledgement.current shouldBe beforenative
                        take(call.loadC(id)).get shouldBe beforenative
                        take(call.readRowC(workflow.continuations, frame.continuationId.value)) shouldBe beforework
                        verifierCalls shouldBe beforeverifiers
                        When("the declared input is restored for the same selected frontier")
                        take(call.writeC(collection, identity, original, true))
                        val restoredinput = take(port.loadPartitionCompletionForCallC(command, call.core)).get
                        Then("the same frontier is readable without a new transition or copy comparison")
                        restoredinput.record shouldBe actual.record
                        restoredinput.current shouldBe beforenative
                      }
                    }
                    Given("the actual required completion lineage made unreadable without changing native or issued work")
                    val lineagekey = take(AC.lineageKeyC(frame.continuationId))
                    val lineagerow = take(call.readRowC(collection("enrichments"), lineagekey)).get
                    take(call.writeC(collection("enrichments"), lineagekey, Record.data(AJ.storageField -> "malformed-router-lineage"), true))
                    When("Workflow observes retained output with that broken actual lineage")
                    val broken = port.loadRetainedForCallC(command, call.core)
                    Then("the error is propagated rather than falling back to another origin or fresh planning")
                    broken.isSuccess shouldBe false
                    take(call.loadC(id)).get shouldBe beforenative
                    take(call.readRowC(workflow.continuations, frame.continuationId.value)) shouldBe beforework
                    verifierCalls shouldBe beforeverifiers
                    call.executionContext.dataStoreSpace.captureBinding() shouldBe beforebinding
                    Given("the exact completion lineage restored")
                    take(call.writeC(collection("enrichments"), lineagekey, lineagerow, true))
                    When("Workflow observes the restored retained origin")
                    val restored = take(port.loadRetainedForCallC(command, call.core)).get
                    Then("the same completion is recovered with unchanged native state and no verifier invocation")
                    restored.asInstanceOf[SplitPhaseManagedPlanningCheckpoint.RetainedPartitionCompletion].observed.record shouldBe actual.record
                    restored.current shouldBe beforenative
                    verifierCalls shouldBe beforeverifiers
                    call.executionContext.dataStoreSpace.captureBinding() shouldBe beforebinding
                    if (mode == "initial" && id.endsWith("service")) {
                      val beforeverifiers = verifierCalls
                      _purpose_hosts.foreach { (purpose, priorhost) =>
                        Given(s"the same completed mapping with an earlier advance binding selecting another $purpose")
                        val priorport = take(SplitPhaseManagedPlanningCheckpoint.bindC(priorhost, workflow, catalog))
                        When("that binding observes AI completion through the actual current advance call")
                        val observed = take(priorport.loadPartitionCompletionForCallC(command, call.core)).get
                        val routed = take(priorport.loadRetainedForCallC(command, call.core)).get
                        Then("the actual owning declarations recover the selected completion without a prior-host-copy gate or new effect")
                        observed.record shouldBe actual.record
                        observed.current shouldBe actual.current
                        routed.asInstanceOf[SplitPhaseManagedPlanningCheckpoint.RetainedPartitionCompletion].observed.record shouldBe actual.record
                        routed.current shouldBe actual.current
                        take(call.loadC(id)).get shouldBe beforenative
                        take(call.readRowC(workflow.continuations, frame.continuationId.value)) shouldBe beforework
                        call.executionContext.dataStoreSpace.captureBinding() shouldBe beforebinding
                        verifierCalls shouldBe beforeverifiers
                      }
                    }
                    Consequence.unit
                  }
                  take(owner.executeOperationResponseInContext(Request.of(component = built.name, service = "SplitPhaseService",
                    operation = "advanceSplitPhase", arguments = List(Argument("arg1", wire))), actor))
                  take(new CncfRuntime().executeCommandResponse(owner, Array(
                    s"${built.name}/SplitPhaseService/advanceSplitPhase", "--json", wire)))
                }
                completionBinding.get.loadRetainedForCallC(frame.continuationId, expired.get).isSuccess shouldBe false
                completionBinding.get.recoverForCallC(frame.continuationId, expired.get).isSuccess shouldBe false
              }
              val serviceWire = scalar(take(invoke(accepted, false)))
              scalar(take(invoke(accepted, true))) shouldBe serviceWire
              originalWire.foreach(_ shouldBe serviceWire)
              originalWire = Some(serviceWire)
              val result = take(WorkflowWaitJsonV1.decodeC(serviceWire))
              result.handle.instanceIdentity shouldBe accepted.instanceIdentity
              fixture { call =>
                val current = take(call.loadC("accepted")).get
                current.history.head.causation shouldBe CausationReference("accepted")
                if (mode == "initial") {
                  current.revision shouldBe InstanceRevision(1)
                  current.history.size shouldBe 1
                  managed.appendForCallC(configuration, current.identity, current.revision, HistoryEntry(HistorySequence(2), InstanceRevision(2),
                    Lifecycle.Active, Some(ProgressionReference("proposal.Awaiting")), current.correlation, CausationReference("later-progress"), None, None, None),
                    call.core).map(_ => ())
                } else { current.history.size shouldBe 2; Consequence.unit }
              }
              scalar(take(invoke(accepted, true))) shouldBe serviceWire
              fixture { call =>
                val current = take(call.loadC("accepted")).get
                current.history.size shouldBe 2
                val context = SplitPhasePartitionContextFixture.record.copy(reference = S.ContextReference("public-start-partition", "1"),
                  owner = SplitPhasePartitionContextFixture.record.owner.copy(start = accepted.reference,
                    handle = take(WorkflowProtocolV1.WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, current)), instanceRevision = current.revision))
                val registry = take(SplitPhaseManagedPartitionContextRegistration.bindC(owner.configuration, workflow, catalog))
                take(registry.registerForCallC(context, call.core)) shouldBe context
                take(inputs.bindPartitionForCallC(context.reference, call.core)).get.start.record shouldBe accepted
                val admitted = take(H.admitForCallC(take(inputs.prepareInvocationForCallC(accepted.command, revision, call.core)), call.core, evidence))
                take(admitted.preparedForCallC(call.core)).record shouldBe accepted
                lastAdmission.foreach { prior =>
                  prior.preparedForCallC(call.core).isSuccess shouldBe false
                }
                lastAdmission = Some(admitted)
                val forged = take(IngressSecurityResolver.resolve(SubsystemExecutionProfile.ControlledTest, call.executionContext,
                  Map("principal.id" -> "participant", "privilege" -> "user", "cncf.security.authenticated" -> "true"))).executionContext
                H.admitForCallC(take(inputs.prepareInvocationForCallC(accepted.command, revision, call.core)),
                  call.core.copy(executionContext = forged), evidence).isSuccess shouldBe false
                Consequence.unit
              }
              allocations.filter(a => rejected.contains(a.reference.identity) || a.reference.identity == "foreign-actor").foreach { a =>
                invoke(a, false).isSuccess shouldBe false
                invoke(a, true).isSuccess shouldBe false
                fixture(call => { take(call.loadC(a.reference.identity)) shouldBe None; Consequence.unit })
              }
              val calls = verifierCalls
              invoke(accepted, false, accepted.command.copy(input = accepted.command.input.copy(invocationMode = "APPLY"))).isSuccess shouldBe false
              verifierCalls shouldBe calls
              fixture(_.writeC(target(Purpose.Source), source.reference.identity, Record.data(J.storageField ->
                take(J.encodeSourceC(source.copy(authority = source.authority.copy(revision = "2"))))), true))
              invoke(accepted, true).isSuccess shouldBe false
              verifierCalls shouldBe calls
              fixture(_.writeC(target(Purpose.Source), source.reference.identity, Record.data(J.storageField -> take(J.encodeSourceC(source))), true))
              val foreign = allocations.find(_.reference.identity == "foreign-history").get
              invoke(foreign, false).isSuccess shouldBe false
              fixture(call => { take(call.loadC("foreign-history")).get.history shouldBe Vector.empty; Consequence.unit })
              if (mode == "initial") redundantreadbacks.foreach { id =>
                val a = allocations.find(_.reference.identity == id).get
                Given("a native Start writer with an authoritative committed result and an armed redundant post-write load fault")
                fault = Some(id)
                When("the public Start consumes the actual create and append results")
                val acknowledged = scalar(take(invoke(a, id.endsWith("missing"))))
                Then("no redundant post-write load is attempted and the original Start is acknowledged")
                masked shouldBe Some(id)
                fault shouldBe None
                // Clear the deliberately unused fault BEFORE independently
                // observing the real managed state; do not mask that observation.
                masked = None
                fixture { call =>
                  val current = take(call.loadC(id)).get
                  current.lifecycle shouldBe Lifecycle.Active
                  current.history.size shouldBe 1
                  current.history.head.causation shouldBe CausationReference(id)
                  Consequence.unit
                }
                When("the same actual Start is invoked again")
                val repeated = scalar(take(invoke(a, false)))
                Then("the original acknowledgement and single activated history are retained")
                repeated shouldBe acknowledged
                fixture(call => { take(call.loadC(id)).get.history.size shouldBe 1; Consequence.unit })
              } else redundantreadbacks.foreach { id =>
                val a = allocations.find(_.reference.identity == id).get
                When("the reopened runtime repeats the original successful Start through CLI and service in that order")
                val reopenedcli = scalar(take(invoke(a, true)))
                val reopenedservice = scalar(take(invoke(a, false)))
                Then("both original acknowledgements agree and the physically reopened native history remains single")
                reopenedcli shouldBe reopenedservice
                fixture(call => { take(call.loadC(id)).get.history.size shouldBe 1; Consequence.unit })
              }
              fixture { call =>
                val p = take(inputs.prepareInvocationForCallC(accepted.command, revision, call.core))
                lastAdmission = Some(take(H.admitForCallC(p, call.core, evidence)))
                Consequence.unit
              }
              // Value equality/call identity alone cannot extend the managed
              // lease. The canonical native storage denies this expired call.
              SplitPhaseTypedStartExecution.startForCallC(lastAdmission.get, lastCall.get, managed)
                .isSuccess shouldBe false
              }
            }
            NativeHumanManagedHostObservation.counts(bootstrap) shouldBe (1, 0)
          }
        } finally take(bootstrap.shutdownC())
      }
    }
  }
}
