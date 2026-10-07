package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.Json
import java.nio.file.{Files, Path}
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction}
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.{ExecutionContext, ScopeContext, ScopeKind}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.{Subsystem, SubsystemEventStoreBootstrap}
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.{Configuration as GenericConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.goldenport.protocol.{Argument, Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.goldenport.value.BaseContent
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhasePreviewCommand, SplitPhaseProposalAssessmentCommand, SplitPhaseStartCommand, SplitPhaseStartInput}
import SplitPhaseProposalPrecheck.{Child, ProposalDraft, digestOf}
import SplitPhasePartitionEnumeration.EdgeDisposition
import SplitPhaseSemanticInputs.{Judgment, Request as SemanticRequest, NovelEstimate, BoundaryAssessment}

/** Actual source document/catalog, generated binding and common public JSON;
  * process-memory stores deliberately make no production durability claim.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
final class SplitPhaseSemanticWorkflowSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"semantic workflow rejected: $failure")
  }
  private val root = Path.of("src/test/resources/source-boundary-reader-fixture")
  private val definition = take(SplitPhaseDefinitionBinding.bindC)
  private val configuration = Configuration(
    NamedWorkflowStore("workflow-primary", WorkflowStoreIdentity("workflow-store")),
    MigrationPolicy("fixture-v1"), RetentionPolicy("fixture-v1"), LeasePolicy("fixture-v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store")))

  private final class Fixture(val viaCli: Boolean = false, novel: Boolean = true, edge: Boolean = true,
    withDecision: Boolean = false, rehydrate: Boolean = false, sourcePhase: String = "77", mode: String = "PREVIEW",
    callScoped: Boolean = false) {
    val component = SmWorkflowComponent.componentId
    def snapshot = {
      val original = take(SplitPhaseSourceSnapshotCatalog.loadC(root, "snapshot-1")).get
      original.copy(source = original.source.copy(sourcePhaseIdentity = sourcePhase),
        inventory = original.inventory.copy(sourcePhaseIdentity = sourcePhase),
        boundary = original.boundary.map(_.copy(sourcePhaseIdentity = sourcePhase)))
    }
    var draft = ProposalDraft("split-proposal-1", sourcePhase, "source-authority-1", snapshot.source.digest,
      Vector(Child(sourcePhase, Vector("work-1", "work-2"), 360, true),
        Child(s"$sourcePhase.1", Vector("work-3"), 360, true)))
    val known = SplitPhaseProposalValidationFixture.evidenceFor(draft)
    var validation = known.copy(calibrationRequests = known.calibrationRequests.map(request =>
      if (novel && request.workItem.identity == "work-2") request.copy(workItem =
        request.workItem.copy(workClassKey = "novel-class")) else request),
      semanticEdges = known.semanticEdges.map(value =>
        if (edge && value.afterItem == "work-2") value.copy(disposition = EdgeDisposition.Unresolved) else value))
    var sourceRevisionDrift = false
    var abiBindingDrift = false
    var occupiedDrift = false
    var authorityDrift = false
    var failIntent = false
    var failCatalog = false
    var failAppendRevision: Option[Long] = None
    var stored = Option.empty[InstanceRecord]
    var creates = 0
    var catalogCalls = 0
    var decisionCalls = 0
    var failDecision = false
    var onDecision: () => Unit = () => ()
    type DecisionRequest = ContinuationRequest[SplitPhaseIdentityDecision.Input]
    var transformDecision: DecisionRequest => DecisionRequest = value => value
    val start = SplitPhaseStartCommand(SplitPhaseStartInput(sourcePhase, mode, "source-authority-1",
      Some("snapshot-1"), None, Some("split-proposal-1")), "split-selection", "participant", "semantic-start")
    val assessment = SplitPhaseProposalAssessmentCommand(start, "1", "semantic-assess")
    val selection = ProfileStartAdmission.Selection("split-selection",
      StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase"),
      definition.workflowIdentity, definition.workflowRevision, "participant",
      ProfileStartAdmission.SelectionOrigin.ExplicitHuman, "semantic-start", None)
    val selections = new ProfileStartAdmission.SelectionPort {
      def loadC(identity: String) = Consequence.success(Option(selection).filter(_.selectionId == identity))
    }
    val entry = new SplitPhaseEntryAssessment.Port {
      def loadSourceC(phase: String, authority: String) = Consequence.success(Some(
        if (sourceRevisionDrift) snapshot.source.copy(revision = "changed") else snapshot.source))
      def loadProposalC(identity: String) = Consequence.success(Some(SplitPhaseEntryAssessment.ProposalCandidate(
        draft.identity, draft.sourcePhaseIdentity, draft.sourceAuthorityReference,
        draft.sourceSnapshotDigest, digestOf(draft))))
    }
    val proposals = new SplitPhaseProposalAssessment.Port {
      def loadInventoryC(identity: String) = Consequence.success(Some(
        if (occupiedDrift) snapshot.inventory.copy(occupiedPhaseIdentities = Set(sourcePhase, s"$sourcePhase.1"))
        else snapshot.inventory))
      def loadDraftC(identity: String) = Consequence.success(Option(draft).filter(_.identity == identity))
      def loadValidationEvidenceC(identity: String) = Consequence.success(Some(validation))
      def loadSourceBoundaryC(identity: String) = Consequence.success(snapshot.boundary.map(boundary =>
        if (authorityDrift) boundary.copy(authorizedHandoffReferences = Set.empty) else boundary))
    }
    val instances = new WorkflowInstancePersistence {
      def create(config: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
        if (stored.nonEmpty) Consequence.stateConflict("duplicate instance")
        else record.validateC.map { value => stored = Some(value); creates += 1; value }
      def load(config: Configuration, identity: InstanceIdentity) =
        Consequence.success(stored.filter(_.identity == identity).map(record =>
          if (abiBindingDrift) record.copy(definition = take(RepositorySyncDefinitionBinding.bindC))
          else if (rehydrate) record.copy(definition = take(SplitPhaseDefinitionBinding.bindC)) else record))
      def append(config: Configuration, identity: InstanceIdentity, expected: InstanceRevision,
        value: HistoryEntry): Consequence[InstanceRecord] =
        if (failAppendRevision.contains(value.revision.value)) Consequence.stateConflict("planned append failure")
        else stored match {
          case Some(current) if current.identity == identity =>
            current.appendC(expected, value).map { next => stored = Some(next); next }
          case _ => Consequence.stateConflict("missing instance")
        }
    }
    val runtime = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory)
    val issued = new IssuedWorkOrderPersistence.InMemory[SemanticRequest]
    var retained = Map.empty[org.goldenport.cncf.workflow.ContinuationIdentity, SplitPhaseSemanticWorkflow.Retained]
    val intents = new SplitPhaseSemanticWorkflow.IntentPort {
      def saveC(id: org.goldenport.cncf.workflow.ContinuationIdentity, value: SplitPhaseSemanticWorkflow.Retained) =
        if (failIntent) Consequence.stateConflict("planned intent failure")
        else retained.get(id) match {
          case Some(existing) => Consequence.success(existing)
          case None => retained = retained.updated(id, value); Consequence.success(value)
        }
      def loadC(id: org.goldenport.cncf.workflow.ContinuationIdentity) = Consequence.success(retained.get(id))
    }
    val catalogs = new SplitPhaseSemanticWorkflow.CatalogPort {
      def loadC(command: SplitPhaseStartCommand, overlay: SplitPhaseProposalAssessment.Port,
        admitted: SplitPhaseSemanticInputs.Admitted): Consequence[SplitPhasePreviewExecution.Catalog] = {
        catalogCalls += 1
        if (failCatalog) Consequence.stateConflict("planned catalog failure")
        else {
          val estimates = validation.calibrationRequests.map { request =>
            val minutes = admitted.novelMinutes.getOrElse(request.workItem.identity,
              take(SplitPhaseDurationCalibration.calibrateC(request)) match {
                case value: SplitPhaseDurationCalibration.Outcome.Known => value.estimateMinutes
                case other => fail(s"unresolved catalog estimate: $other")
              })
            request.workItem.identity -> minutes
          }.toMap
          for {
            enumeration <- SplitPhasePartitionEnumeration.enumerateC(snapshot.source.digest,
              snapshot.inventory.orderedUnfinishedItems.map(id => SplitPhasePartitionEnumeration.WorkItem(id, estimates(id))),
              admitted.resolvedEdges, validation.partitionPolicy)
            enumerated <- enumeration match {
              case value: SplitPhasePartitionEnumeration.Outcome.Enumerated => Consequence.success(value)
              case _ => Consequence.stateConflict("semantic catalog remains pending")
            }
            evidence = SplitPhaseCandidateGateDerivation.Evidence(enumerated.candidates.head.digest,
              draft, estimates, validation.semanticHandoffs, validation.validationOwnership, admitted.resolvedEdges)
            prepared <- SplitPhaseStartExecution.prepareC(command, selections, entry, definition, configuration)
            outcome <- SplitPhaseProposalAdmission.admitCatalogC(root, "snapshot-1", prepared.assessment,
              enumerated, validation.partitionPolicy, Vector(evidence), overlay)
            accepted <- outcome match {
              case value: SplitPhaseProposalAdmission.Outcome.Admitted => Consequence.success(value)
              case other => Consequence.stateConflict(s"semantic catalog not admitted: $other")
            }
          } yield SplitPhasePreviewExecution.Catalog(root, "snapshot-1", enumerated,
            validation.partitionPolicy, Vector(evidence), accepted)
        }
      }
    }
    val decisions = new SplitPhaseIdentityDecision.Port {
      def resolveC(intent: SplitPhaseIdentityDecision.Intent): Consequence[DecisionRequest] = {
        decisionCalls += 1
        onDecision()
        if (failDecision) Consequence.configurationInvalid("planned identity Decision display failure")
        else Consequence.success(transformDecision(ContinuationRequest(
          StateMachineRunIdentity(s"human-boundary:${intent.handle.instanceIdentity.value}"),
          org.goldenport.cncf.workflow.ContinuationIdentity(s"identity-decision:${intent.idempotencyKey}"),
          intent.expectedRevision, StateMachineRequiredOperationIdentity("host.split-identity-decision"),
          StateMachineOperationIdentity("HumanBoundaryFixture", "decideSplitIdentity"),
          Some(TypedValue(SplitPhaseIdentityDecision.inputCodec.typeIdentity, intent.input)), None,
          ContextBundle("Resolve only the stopped split identity", Vector(
            ContextReference(intent.input.sourceSnapshotIdentity, intent.snapshot.modelRevision.get)),
            Vector(ContextReference(intent.input.proposalIdentity, intent.input.proposalDigest)), intent.snapshot),
          CompletionContract("host-human-decision-stop-only-v1", Vector.empty),
          EvidenceContract("host-human-decision-evidence-v1", Vector.empty),
          StateMachineOperationIdentity("HumanBoundaryFixture", "recordSplitIdentityDecision"))))
      }
    }
    var currentReads = Vector.empty[(String, ActionCall.Core)]
    private def observe(core: ActionCall.Core, role: String): Unit = currentReads :+= role -> core
    val currentInstances = if (!callScoped) instances else new CallScopedWorkflowInstances.Port {
      def createForCallC(config: Configuration, record: InstanceRecord, core: ActionCall.Core) = {
        observe(core, "instance-create"); instances.create(config, record)
      }
      def loadForCallC(config: Configuration, identity: InstanceIdentity, core: ActionCall.Core) = {
        observe(core, "instance-load"); instances.load(config, identity)
      }
      def appendForCallC(config: Configuration, identity: InstanceIdentity, revision: InstanceRevision,
        entry: HistoryEntry, core: ActionCall.Core) = {
        observe(core, "instance-append"); instances.append(config, identity, revision, entry)
      }
    }
    val currentSelections = if (!callScoped) selections else new ProfileStartAdmission.CallScopedSelectionPort {
      def loadForCallC(id: String, core: ActionCall.Core) = { observe(core, "selection"); selections.loadC(id) }
    }
    val currentEntry = if (!callScoped) entry else new SplitPhaseEntryAssessment.CallScopedPort {
      def loadSourceForCallC(phase: String, authority: String, core: ActionCall.Core) = {
        observe(core, "source"); entry.loadSourceC(phase, authority)
      }
      def loadProposalForCallC(id: String, core: ActionCall.Core) = { observe(core, "candidate"); entry.loadProposalC(id) }
    }
    val currentProposals = if (!callScoped) proposals else new SplitPhaseProposalAssessment.CallScopedPort {
      def loadInventoryForCallC(id: String, core: ActionCall.Core) = { observe(core, "inventory"); proposals.loadInventoryC(id) }
      def loadDraftForCallC(id: String, core: ActionCall.Core) = { observe(core, "draft"); proposals.loadDraftC(id) }
      def loadValidationForCallC(id: String, core: ActionCall.Core) = { observe(core, "validation"); proposals.loadValidationEvidenceC(id) }
      def loadBoundaryForCallC(id: String, core: ActionCall.Core) = { observe(core, "boundary"); proposals.loadSourceBoundaryC(id) }
    }
    val currentIntents = if (!callScoped) intents else new SplitPhaseSemanticWorkflow.CallScopedIntentPort {
      def saveForCallC(id: org.goldenport.cncf.workflow.ContinuationIdentity, value: SplitPhaseSemanticWorkflow.Retained,
        core: ActionCall.Core) = { observe(core, "intent-save"); intents.saveC(id, value) }
      def loadForCallC(id: org.goldenport.cncf.workflow.ContinuationIdentity, core: ActionCall.Core) = {
        observe(core, "intent-load"); intents.loadC(id)
      }
    }
    val currentCatalogs = if (!callScoped) catalogs else new SplitPhaseSemanticWorkflow.CallScopedCatalogPort {
      def loadForCallC(start: SplitPhaseStartCommand, overlay: SplitPhaseProposalAssessment.Port,
        inputs: SplitPhaseSemanticInputs.Admitted, core: ActionCall.Core) = {
        observe(core, "catalog"); catalogs.loadC(start, overlay, inputs)
      }
    }
    val semanticPorts = SplitPhaseSemanticWorkflow.Ports(
      currentSelections, currentEntry, currentProposals, definition, configuration, currentInstances, runtime, issued, currentIntents, currentCatalogs,
      ExecutionRequirement(Vector(CapabilityRequirement("assess-novel-split-capability")),
        RiskLevel("standard"), ReasoningLevel.Deep, true),
      MinimalPresentation("Assess split inputs", "Only unresolved estimates and boundaries"),
      () => new UnitOfWork(ExecutionContext.create(), EventEngine.noop(DataStore.noop())))
    val workflow = take(SplitPhaseSemanticWorkflow.bindC(semanticPorts))
    val service = take(SplitPhaseFixtureServiceAssembly.bindC(root, currentSelections, currentEntry, currentProposals,
      new SplitPhasePreviewExecution.CatalogPort {
        def loadC(command: SplitPhaseStartCommand) = Consequence.stateConflict("separate public preview not used")
      }, definition, configuration, currentInstances, Some(workflow), if (withDecision) Some(decisions) else None))
    val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(name = "split-semantic", version = None,
      configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
      aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
    val app = new Component() {}
    app.initialize(ComponentInit(subsystem, Component.Core.create(component.name, component,
      ComponentInstanceId.default(component), Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))), ComponentOrigin.Builtin))
    subsystem.add(app)
    val cli = new CncfRuntime()
    def invoke(operation: String, wire: String): Consequence[String] =
      if (viaCli) cli.executeCommandResponse(subsystem,
        Array(s"${component.name}/SplitPhaseService/$operation", "--json", wire)).map(_.print)
      else subsystem.executeOperationResponse(Request.of(component = component.name, service = "SplitPhaseService",
        operation = operation, arguments = List(Argument("arg1", wire)))).flatMap {
          case OperationResponse.Scalar(value: String) => Consequence.success(value)
          case _ => Consequence.stateConflict("common JSON response missing")
        }
    def current = stored.get
    def startWire(): String = take(invoke("startSplitPhase", SplitPhaseStartServiceOperation.encodeCommand(start)))
    def assessWire(): Consequence[String] = invoke("assessSplitProposal",
      SplitPhaseProposalAssessmentServiceOperation.encodeCommand(assessment))
    def begin(): WorkflowInteraction[SemanticRequest, Nothing] = {
      take(invoke("startSplitPhase", SplitPhaseStartServiceOperation.encodeCommand(start)))
      take(WorkflowWorkOrderJsonV1.decodeC(take(invoke("assessSplitProposal",
        SplitPhaseProposalAssessmentServiceOperation.encodeCommand(assessment))), SplitPhaseSemanticJson.requestCodec))
    }
    def order(work: WorkflowInteraction[SemanticRequest, Nothing]): ContinuationRequest[? <: SemanticRequest] =
      work.current match {
        case WorkflowContinuation.WorkOrder(request, _, _) => request
        case other => fail(s"not semantic work: $other")
      }
    def result(work: WorkflowInteraction[SemanticRequest, Nothing]): ContinuationResult[Judgment] = {
      val request = order(work)
      val input = request.input.get.value
      val value = Judgment(input.sourceSnapshotDigest, input.proposalDigest, input.contextDigest,
        "enriched", "Frozen gaps only", input.unresolvedWorkItems.map(item => NovelEstimate(item.identity,
          150, 180, "Architecture unchanged", "No comparable work-class evidence")),
        input.unresolvedEdges.map(value => BoundaryAssessment(value.afterItem, EdgeDisposition.CutOnly,
          value.authorityReference, "Independent closure")), Vector("semantic-evidence-1"))
      ContinuationResult(work.handle, request.runId, request.continuationId, request.expectedRevision,
        request.context.snapshot, TypedValue("SplitPhaseSemanticResult", value),
        ContextReference("semantic-result-1", input.contextDigest), Vector.empty, ExecutionEvidence(Vector.empty))
    }
    def wire(value: ContinuationResult[Judgment]) = take(WorkflowResultJsonV1.encodeC(value, SplitPhaseSemanticJson.resultCodec))
    def submit(value: ContinuationResult[Judgment]) = invoke("submitSplitSemanticResult", wire(value))
  }

  private def withFixture[A](viaCli: Boolean = false, novel: Boolean = true, edge: Boolean = true,
    withDecision: Boolean = false, rehydrate: Boolean = false, sourcePhase: String = "77", mode: String = "PREVIEW",
    callScoped: Boolean = false)(run: Fixture => A): A = {
    val f = new Fixture(viaCli, novel, edge, withDecision, rehydrate, sourcePhase, mode, callScoped)
    try run(f) finally f.subsystem.shutdown()
  }

  "SplitPhase combined semantic and identity binding" should {
    "retain actual generated completion descriptions without changing native JSON ingress or saved work" in {
      withFixture() { f =>
        Given("an issued semantic WorkOrder and its real generated completion declaration")
        f.begin()
        val before = f.current
        val declared = SmWorkflowComponent.SplitPhaseServiceService.SubmitSplitSemanticResultOperation.specification
        When("the native public completion operation is bound")
        val actual = take(f.workflow.completionC).specification
        Then("only generated descriptions are connected and binding does not progress the instance")
        actual.name shouldBe declared.name
        actual.content.nameAttributes shouldBe BaseContent.simple(declared.name).nameAttributes
        actual.content.descriptiveAttributes shouldBe declared.content.descriptiveAttributes
        actual.request should be theSameInstanceAs WorkflowJsonCommandRequest.definition
        actual.response should be theSameInstanceAs spec.ResponseDefinition.void
        f.current shouldBe before
      }
    }

    "stop identity collisions before semantic work and retain the same Start through Service and CLI retries" in {
      for (cli <- Vector(false, true); rehydrate <- Vector(false, true))
        withFixture(cli, withDecision = true, rehydrate = rehydrate) { f =>
          val bytes = Files.readAllBytes(root.resolve("docs/phase/phase-77.md")).toVector
          f.occupiedDrift = true
          val started = take(WorkflowWaitJsonV1.decodeC(f.startWire()))
          val before = f.current
          val wire = take(f.assessWire())
          val decision = take(WorkflowDecisionJsonV1.decodeC(wire, SplitPhaseIdentityDecision.inputCodec))
          decision.handle shouldBe started.handle
          val request = decision.current.asInstanceOf[WorkflowContinuation.Decision[SplitPhaseIdentityDecision.Input]].request
          request.expectedRevision shouldBe StateMachineRevision("1")
          request.input.get.value.reason shouldBe "child-phase-identity-collision"
          request.input.get.value.proposalDigest shouldBe digestOf(f.draft)
          take(f.assessWire()) shouldBe wire
          f.current shouldBe before
          f.current.history.size shouldBe 1
          f.creates shouldBe 1
          f.retained shouldBe empty
          f.catalogCalls shouldBe 0
          WorkflowWorkOrderJsonV1.decodeC(wire, SplitPhaseIdentityDecision.inputCodec) shouldBe a[Consequence.Failure[?]]
          f.invoke("submitSplitSemanticResult", wire) shouldBe a[Consequence.Failure[?]]
          f.invoke("recordSplitIdentityDecision", wire) shouldBe a[Consequence.Failure[?]]
          f.current shouldBe before
          Files.readAllBytes(root.resolve("docs/phase/phase-77.md")).toVector shouldBe bytes
        }
    }

    "route nested numbering to the identity stop even when semantic gaps are also present" in {
      for (cli <- Vector(false, true)) withFixture(cli, withDecision = true, sourcePhase = "77.1") { f =>
        f.startWire()
        val before = f.current
        val decision = take(WorkflowDecisionJsonV1.decodeC(take(f.assessWire()), SplitPhaseIdentityDecision.inputCodec))
        decision.current.asInstanceOf[WorkflowContinuation.Decision[SplitPhaseIdentityDecision.Input]]
          .request.input.get.value.reason shouldBe "nested-or-unknown-phase-numbering"
        f.current shouldBe before
        f.retained shouldBe empty
        f.catalogCalls shouldBe 0
      }
    }

    "complete collision-free semantic PREVIEW on the same instance with both ports and rehydrated ABI bindings" in {
      for (cli <- Vector(false, true); rehydrate <- Vector(false, true);
          (novel, edge) <- Vector(true -> true, true -> false, false -> true))
        withFixture(cli, novel, edge, withDecision = true, rehydrate = rehydrate) { f =>
          val bytes = Files.readAllBytes(root.resolve("docs/phase/phase-77.md")).toVector
          val work = f.begin()
          take(f.assessWire()) shouldBe take(WorkflowWorkOrderJsonV1.encodeC(work, SplitPhaseSemanticJson.requestCodec))
          val result = f.result(work)
          val terminal = take(WorkflowTerminalJsonV1.decodeC(take(f.submit(result)), SplitPhasePreviewServiceOperation.terminalCodec))
          terminal.handle.instanceIdentity shouldBe work.handle.instanceIdentity
          f.current.history.map(_.revision.value) shouldBe Vector(1L, 2L, 3L, 4L)
          f.current.lifecycle shouldBe Lifecycle.Completed
          f.decisionCalls shouldBe 0
          f.catalogCalls shouldBe 1
          f.creates shouldBe 1
          f.submit(result) shouldBe a[Consequence.Failure[?]]
          Files.readAllBytes(root.resolve("docs/phase/phase-77.md")).toVector shouldBe bytes
        }
    }

    "recover failed identity display by read-only reassessment without publishing work or appending history" in {
      for (cli <- Vector(false, true)) withFixture(cli, withDecision = true) { f =>
        f.occupiedDrift = true
        f.startWire()
        val before = f.current
        f.failDecision = true
        f.assessWire() shouldBe a[Consequence.Failure[?]]
        f.current shouldBe before
        f.retained shouldBe empty
        f.failDecision = false
        val wire = take(f.assessWire())
        take(WorkflowDecisionJsonV1.decodeC(wire, SplitPhaseIdentityDecision.inputCodec))
        take(f.assessWire()) shouldBe wire
        f.current shouldBe before
        f.retained shouldBe empty
        f.catalogCalls shouldBe 0
      }
    }

    "reject altered Decision metadata and semantic operation reuse before issuing AI work" in {
      type R = ContinuationRequest[SplitPhaseIdentityDecision.Input]
      val changes = Vector[R => R](
        r => r.copy(expectedRevision = StateMachineRevision("2")),
        r => r.copy(input = r.input.map(v => v.copy(value = v.value.copy(proposalDigest = "b" * 64)))),
        r => r.copy(context = r.context.copy(snapshot = r.context.snapshot.copy(modelRevision = Some("changed")))),
        r => r.copy(requiredOperation = StateMachineRequiredOperationIdentity("assess-novel-split-capability")),
        r => r.copy(operation = StateMachineOperationIdentity("SplitSemanticService", "assessNovelSplitWork")),
        r => r.copy(completionOperation = StateMachineOperationIdentity("SplitPhaseService", "submitSplitSemanticResult")),
        r => r.copy(resultType = Some(StateMachineResultTypeReference("SplitPhaseSemanticResult"))),
        r => r.copy(context = r.context.copy(references = Vector.empty)))
      for (change <- changes) withFixture(withDecision = true) { f =>
        f.occupiedDrift = true
        f.startWire()
        val before = f.current
        f.transformDecision = change
        f.assessWire() shouldBe a[Consequence.Failure[?]]
        f.current shouldBe before
        f.retained shouldBe empty
        f.catalogCalls shouldBe 0
      }
    }

    "reject source, proposal, inventory and instance drift during Decision lookup without advancing readiness" in {
      for (change <- Vector("source", "proposal", "inventory", "instance")) withFixture(withDecision = true) { f =>
        f.occupiedDrift = true
        f.startWire()
        val before = f.current
        f.onDecision = () => change match {
          case "source" => f.sourceRevisionDrift = true
          case "proposal" => f.draft = f.draft.copy(children = f.draft.children.map(_.copy(expectedMinutes = 361)))
          case "inventory" => f.occupiedDrift = false
          case _ => f.stored = Some(f.current.copy(correlation = CorrelationReference("foreign")))
        }
        f.assessWire() shouldBe a[Consequence.Failure[?]]
        f.current.revision shouldBe InstanceRevision(1L)
        f.current.history shouldBe before.history
        f.current.lifecycle shouldBe Lifecycle.Active
        f.retained shouldBe empty
        f.catalogCalls shouldBe 0
      }
    }

    "reject incomplete Decision options and crossed host stores or evidence ports before Start" in {
      withFixture(withDecision = true) { f =>
        val emptyCatalog = new SplitPhasePreviewExecution.CatalogPort {
          def loadC(command: SplitPhaseStartCommand) = Consequence.stateConflict("not used")
        }
        Vector[Option[SplitPhaseIdentityDecision.Port]](null, Some(null)).foreach { bad =>
          SplitPhaseFixtureServiceAssembly.bindC(root, f.selections, f.entry, f.proposals,
            emptyCatalog, definition, configuration, f.instances, Some(f.workflow), bad) shouldBe a[Consequence.Failure[?]]
        }
        val alternateEntry = new SplitPhaseEntryAssessment.Port {
          def loadSourceC(phase: String, authority: String) = f.entry.loadSourceC(phase, authority)
          def loadProposalC(id: String) = f.entry.loadProposalC(id)
        }
        SplitPhaseProposalAssessmentServiceOperation.bindC(f.selections, alternateEntry, f.proposals,
          definition, configuration, f.instances, Some(f.workflow), Some(f.decisions)) shouldBe a[Consequence.Failure[?]]
        val alternateStore = new WorkflowInstancePersistence {
          def create(c: Configuration, r: InstanceRecord): Consequence[InstanceRecord] = Consequence.stateConflict("not used")
          def load(c: Configuration, id: InstanceIdentity): Consequence[Option[InstanceRecord]] = Consequence.stateConflict("not used")
          def append(c: Configuration, id: InstanceIdentity, rev: InstanceRevision, h: HistoryEntry):
            Consequence[InstanceRecord] = Consequence.stateConflict("not used")
        }
        SplitPhaseProposalAssessmentServiceOperation.bindC(f.selections, f.entry, f.proposals,
          definition, configuration, alternateStore,
          Some(f.workflow), Some(f.decisions)) shouldBe a[Consequence.Failure[?]]
        f.stored shouldBe None
        f.retained shouldBe empty
        f.decisionCalls shouldBe 0
      }
    }

    "reject ABI drift and a new identity collision during semantic work without consuming its claim or converting it to Decision" in {
      for (cli <- Vector(false, true)) withFixture(cli, withDecision = true) { f =>
        val valid = f.result(f.begin())
        val before = f.current
        val claim = take(f.runtime.recoverClaimC(valid.continuationId))
        f.abiBindingDrift = true
        f.submit(valid) shouldBe a[Consequence.Failure[?]]
        f.current shouldBe before
        take(f.runtime.recoverClaimC(valid.continuationId)) shouldBe claim
        f.abiBindingDrift = false
        f.occupiedDrift = true
        f.submit(valid) shouldBe a[Consequence.Failure[?]]
        f.current shouldBe before
        take(f.runtime.recoverClaimC(valid.continuationId)) shouldBe claim
        f.decisionCalls shouldBe 0
        f.catalogCalls shouldBe 0
        f.occupiedDrift = false
        take(f.submit(valid))
        f.current.lifecycle shouldBe Lifecycle.Completed
      }
    }

    "keep semantic decision-needed as WAIT rather than inventing an identity Decision or authority" in {
      for (cli <- Vector(false, true)) withFixture(cli, withDecision = true) { f =>
        val valid = f.result(f.begin())
        val result = valid.copy(result = valid.result.copy(value = valid.result.value.copy(
          decision = "decision-needed", novelEstimates = Vector.empty, boundaries = Vector.empty)))
        take(WorkflowWaitJsonV1.decodeC(take(f.submit(result))))
        f.current.lifecycle shouldBe Lifecycle.Active
        f.current.revision shouldBe InstanceRevision(3L)
        f.current.suspension shouldBe None
        f.decisionCalls shouldBe 0
        f.catalogCalls shouldBe 0
        f.submit(result) shouldBe a[Consequence.Failure[?]]
      }
    }

    "preserve APPLY and deterministic readiness without semantic publication or a fabricated Decision" in {
      for (cli <- Vector(false, true); collision <- Vector(false, true))
        withFixture(cli, withDecision = true, mode = "APPLY") { f =>
          f.occupiedDrift = collision
          f.startWire()
          val before = f.current
          val wire = take(f.assessWire())
          if (collision) take(WorkflowDecisionJsonV1.decodeC(wire, SplitPhaseIdentityDecision.inputCodec))
          else take(WorkflowWaitJsonV1.decodeC(wire))
          f.current shouldBe before
          f.retained shouldBe empty
          f.catalogCalls shouldBe 0
          f.decisionCalls shouldBe (if (collision) 1 else 0)
        }
      withFixture(novel = false, edge = false, withDecision = true) { f =>
        f.startWire()
        take(WorkflowWaitJsonV1.decodeC(take(f.assessWire())))
        f.current.revision shouldBe InstanceRevision(2L)
        f.retained shouldBe empty
        f.decisionCalls shouldBe 0
        f.catalogCalls shouldBe 0
      }
    }
  }

  "SplitPhase semantic public completion" should {
    "retain only a closed original command projection without a new invocation or authority" in {
      withFixture() { f =>
        Given("the original fixture assessment command, not a new human authorization")
        val codec = SplitPhaseSemanticIntentCommandJsonV1
        val raw = take(codec.encodeC("continuation-1", f.assessment))
        When("the stored command is decoded with its exact original reference")
        take(codec.decodeC("continuation-1", raw)) shouldBe f.assessment
        val json = io.circe.parser.parse(raw.drop(codec.schema.length + 1)).toOption.get
        Then("crossed references, extra authority, malformed schema and nested command fields are refused")
        val invalid = Vector(json.mapObject(_.add("authority", io.circe.Json.fromBoolean(true))),
          json.mapObject(_.add("schemaVersion", io.circe.Json.fromString("v2"))),
          json.mapObject(root => root.add("command", root("command").get.mapObject(_.add("selectedOperation", io.circe.Json.fromString("other"))))))
        invalid.foreach(value => codec.decodeC("continuation-1", codec.schema + ":" + value.noSpaces) shouldBe a[Consequence.Failure[?]])
        codec.decodeC("foreign", raw) shouldBe a[Consequence.Failure[?]]
        codec.decodeC("continuation-1", null) shouldBe a[Consequence.Failure[?]]
        codec.encodeC(null, f.assessment) shouldBe a[Consequence.Failure[?]]
        f.retained shouldBe empty
        f.stored shouldBe None
      }
    }

    "recover original intent from managed records before Service CLI completion and refuse crossed provenance or replay after reopen" in {
      for (cli <- Vector(false, true); crossed <- Vector(false, true); deferred <- Vector(false, true);
        managedSources <- Vector(false, true)) withFixture() { f =>
        Given(s"a canonical SQLite suspension with crossed provenance = $crossed and first completion after reopen = $deferred, managed sources = $managedSources, not production Start")
        val work = f.begin()
        val original = f.retained.values.head
        val catalog = take(SplitPhaseRuntimeCatalog.loadC())
        val seed = original.copy(active = original.active.copy(definition = catalog.definition),
          prepared = original.prepared.copy(initial = original.prepared.initial.copy(definition = catalog.definition)))
        f.retained = Map.empty
        val suspended = f.current.copy(definition = catalog.definition)
        val valid = f.result(work)
        val submitted = valid.copy(result = valid.result.copy(value = valid.result.value.copy(
          decision = "decision-needed", novelEstimates = Vector.empty, boundaries = Vector.empty)))
        val fixtureClaim = take(f.runtime.recoverClaimC(submitted.continuationId))
        val path = Files.createTempDirectory("smwf-split-completion-reopen-").resolve("host.sqlite")
        def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "splitclosed", name))
        val declaration = SplitPhaseManagedStartAssembly.Declaration(configuration, collection("instances"), collection("continuations"))
        val inputCollection = collection("semanticInputs")
        val facts = SplitPhaseSemanticInputRecordJsonV1.Facts(seed.prepared.assessment, f.snapshot.inventory,
          f.draft, SplitPhaseProposalRecordJsonV1.BaselineValidation(f.validation.calibrationRequests,
            f.validation.semanticHandoffs, f.validation.validationOwnership, f.validation.partitionPolicy, f.validation.semanticEdges),
          f.snapshot.boundary.get)
        val input = SplitPhaseSemanticInputRecordJsonV1.InputRecord(seed.continuation.continuationId.value, "1",
          if (crossed) "foreign-continuation" else seed.continuation.continuationId.value, facts, seed.intent.request)
        val inputRaw = take(SplitPhaseSemanticInputRecordJsonV1.encodeC(input))
        val commandRaw = take(SplitPhaseSemanticIntentCommandJsonV1.encodeC(input.reference, seed.command))
        val source = facts.assessment.source
        val proposal = facts.assessment.proposalCandidate.get
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
        val baselineRows = Vector(
          (collection("selections"), f.selection.selectionId, ProfileSelectionJsonV1.storageField,
            take(ProfileSelectionJsonV1.encodeStorageC(f.selection))),
          (collection("sources"), source.sourcePhaseIdentity, SplitPhaseEntryRecordJsonV1.sourceField, sourceRaw),
          (collection("proposals"), proposal.identity, SplitPhaseEntryRecordJsonV1.proposalField, proposalRaw),
          (collection(Purpose.Inventory.toString), source.identity, SplitPhaseProposalRecordJsonV1.storageField(Purpose.Inventory),
            take(SplitPhaseProposalRecordJsonV1.encodeInventoryC(source.identity, facts.inventory))),
          (collection(Purpose.Draft.toString), facts.draft.identity, SplitPhaseProposalRecordJsonV1.storageField(Purpose.Draft),
            take(SplitPhaseProposalRecordJsonV1.encodeDraftC(facts.draft.identity, facts.draft))),
          (collection(Purpose.Validation.toString), facts.draft.identity, SplitPhaseProposalRecordJsonV1.storageField(Purpose.Validation),
            take(SplitPhaseProposalRecordJsonV1.encodeValidationC(facts.draft.identity, facts.validation))),
          (collection(Purpose.Boundary.toString), source.identity, SplitPhaseProposalRecordJsonV1.storageField(Purpose.Boundary),
            take(SplitPhaseProposalRecordJsonV1.encodeBoundaryC(source.identity, facts.boundary))))
        val policy = take(EventPersistencePolicy.createC("split-completion-v1", Vector(
          EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
        val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"),
          collection("events"), collection("indexes"), Vector(EventStorePersistenceDeclaration.WorkflowGrant(
            SmWorkflowComponent.componentId, configuration.workflowStore.name, declaration.instances, declaration.continuations))))
        var observations = Vector.empty[ActionCall.Core]
        def observe(core: ActionCall.Core): Unit = observations :+= core
        var installations = 0
        var installerCalls = Vector.empty[ActionCall.Core]
        var currentRecovery = Option.empty[SplitPhaseSemanticIntentDataStorePort]
        val ports = SplitPhaseManagedSemanticStorageAssembly.RecordCompletionPorts(
          new ProfileStartAdmission.CallScopedSelectionPort {
            def loadForCallC(id: String, core: ActionCall.Core) = { observe(core); f.selections.loadC(id) }
          },
          new SplitPhaseEntryAssessment.CallScopedPort {
            def loadSourceForCallC(phase: String, authority: String, core: ActionCall.Core) = {
              observe(core); f.entry.loadSourceC(phase, authority)
            }
            def loadProposalForCallC(id: String, core: ActionCall.Core) = { observe(core); f.entry.loadProposalC(id) }
          },
          new SplitPhaseProposalAssessment.CallScopedPort {
            def loadInventoryForCallC(id: String, core: ActionCall.Core) = { observe(core); f.proposals.loadInventoryC(id) }
            def loadDraftForCallC(id: String, core: ActionCall.Core) = { observe(core); f.proposals.loadDraftC(id) }
            def loadValidationForCallC(id: String, core: ActionCall.Core) = { observe(core); f.proposals.loadValidationEvidenceC(id) }
            def loadBoundaryForCallC(id: String, core: ActionCall.Core) = { observe(core); f.proposals.loadSourceBoundaryC(id) }
          },
          new SplitPhaseSemanticWorkflow.CallScopedCatalogPort {
            def loadForCallC(start: SplitPhaseStartCommand, proposals: SplitPhaseProposalAssessment.Port,
              inputs: SplitPhaseSemanticInputs.Admitted, core: ActionCall.Core) =
              Consequence.stateConflict("decision-needed must not build a catalog")
          }, f.semanticPorts.requirement, f.semanticPorts.presentation)
        val installOnly = new ProfileStartAdmission.CallScopedSelectionPort {
          def loadForCallC(id: String, capsule: ActionCall.Core): Consequence[Option[ProfileStartAdmission.Selection]] = {
            installerCalls :+= capsule
            final class Installer extends org.goldenport.cncf.action.ProcedureActionCall {
              val core = capsule
              def installC(): Consequence[Unit] = {
                given ExecutionContext = executionContext
                val space = executionContext.dataStoreSpace
                val prior = space.captureBinding()
                try for {
                  _ <- bind_component_application_datastore_c(configuration.workflowStore.name)
                  store <- space.dataStore(inputCollection)
                  _ <- store.create(inputCollection, DataStore.StringEntryId(input.reference), org.goldenport.record.Record.data(
                    SplitPhaseSemanticInputRecordJsonV1.storageField -> inputRaw,
                    SplitPhaseSemanticIntentCommandJsonV1.storageField -> commandRaw))
                  _ <- store.create(inputCollection, DataStore.StringEntryId("unissued-intent"), org.goldenport.record.Record.data(
                    SplitPhaseSemanticInputRecordJsonV1.storageField -> take(SplitPhaseSemanticInputRecordJsonV1.encodeC(
                      input.copy(reference = "unissued-intent", continuationReference = "unissued-intent"))),
                    SplitPhaseSemanticIntentCommandJsonV1.storageField -> take(SplitPhaseSemanticIntentCommandJsonV1.encodeC(
                      "unissued-intent", seed.command))))
                  _ <- if (!managedSources) Consequence.unit else baselineRows.foldLeft(Consequence.unit) {
                    case (previous, (target, identity, field, value)) => previous.flatMap { _ =>
                      space.dataStore(target).flatMap(_.create(target, DataStore.StringEntryId(identity),
                        org.goldenport.record.Record.data(field -> value))).map(_ => ())
                    }
                  }
                } yield () finally space.restoreBinding(prior)
              }
              def execute() = Consequence.stateConflict("private intent record installer has no public Operation")
            }
            if (installations == 0) { take(new Installer().installC()); installations += 1 }
            val recovery = currentRecovery.get
            val unissued = org.goldenport.cncf.workflow.ContinuationIdentity("unissued-intent")
            take(recovery.loadForCallC(unissued, capsule)) shouldBe None
            recovery.loadForCallC(unissued, capsule.copy(component = None)) shouldBe a[Consequence.Failure[?]]
            recovery.loadForCallC(unissued, capsule.copy(executionContext = null)) shouldBe a[Consequence.Failure[?]]
            recovery.saveForCallC(seed.continuation.continuationId, seed, capsule) shouldBe a[Consequence.Failure[?]]
            Consequence.success(None)
          }
        }
        def withHost(body: (SubsystemEventStoreBootstrap, ExecutionContext) => Unit): Unit = {
          val context = ExecutionContext.create()
          val hostValues: Map[String, ConfigurationValue] = Map(
            "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
            "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString),
            SplitPhaseHostSemanticInputReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue(configuration.workflowStore.name),
            SplitPhaseHostSemanticInputReadPolicy.prefix + "input-collection" -> ConfigurationValue.StringValue(inputCollection.print),
            SplitPhaseHostSelectionReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue(configuration.workflowStore.name),
            SplitPhaseHostSelectionReadPolicy.prefix + "selection-collection" -> ConfigurationValue.StringValue(collection("selections").print),
            SplitPhaseHostEntryReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue(configuration.workflowStore.name),
            SplitPhaseHostEntryReadPolicy.prefix + "source-collection" -> ConfigurationValue.StringValue(collection("sources").print),
            SplitPhaseHostEntryReadPolicy.prefix + "proposal-collection" -> ConfigurationValue.StringValue(collection("proposals").print),
            SplitPhaseHostProposalReadPolicy.prefix + "store-name" -> ConfigurationValue.StringValue(configuration.workflowStore.name)) ++
            SplitPhaseHostProposalReadPolicy.Purpose.values.toVector.map(purpose =>
              SplitPhaseHostProposalReadPolicy.prefix + purpose.key -> ConfigurationValue.StringValue(collection(purpose.toString).print))
          val hostConfig = ResolvedConfiguration(GenericConfiguration(hostValues), ConfigurationTrace.empty)
          val host = take(SubsystemEventStoreBootstrap.createC(policy, storage,
            ScopeContext(ScopeKind.Subsystem, "split-managed-completion", None, context.observability), hostConfig))
          try body(host, context) finally take(host.shutdownC())
        }
        var completed = Option.empty[InstanceRecord]
        var completionCount = 0
        var previousOwner = Option.empty[Subsystem]
        var previousComponent = Option.empty[Component]
        for (hostIndex <- (if (deferred) Vector(0, 1, 2) else Vector(0, 1))) {
          val reopen = hostIndex > 0
          val completeNow = !crossed && hostIndex == (if (deferred) 1 else 0)
          if (reopen) When("a new canonical host reopens the same database without resubmitting or republishing semantic work")
          withHost { (host, context) =>
            given ExecutionContext = context
            val owner = RuntimeBindingAdmissionFixture.admit(host.subsystem)
            previousOwner.foreach(previous => owner should not be theSameInstanceAs(previous))
            previousOwner = Some(owner)
            val binding = take(SplitPhaseManagedSemanticStorageAssembly.bindC(
              ComponentCreate(owner, ComponentOrigin.Builtin), declaration, catalog))
            currentRecovery = Some(new SplitPhaseSemanticIntentDataStorePort(
              take(SplitPhaseHostSemanticInputReadPolicy.bindC(owner.configuration, declaration)), binding,
              ports.selections, ports.entry, ports.proposals, ports.requirement, ports.presentation))
            val fixtureInstances = take(binding.persistence.storage.bindInstancesC())
            if (!reopen) {
              take(fixtureInstances.createC(seed.prepared.initial))
              take(fixtureInstances.appendC(seed.active.identity, initialRevision, seed.active.history.last))
              val intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(
                WorkflowInstanceAtomicTransitionV1.schemaVersion, configuration, seed.active, seed.active.revision,
                suspended.history.last, ContinuationRuntimePersistence.Record(seed.continuation, ContinuationRuntimePersistence.Status.Available))
              take(binding.issued.suspendC(intent, work))
            }
            val factory = new GoalPhaseConfiguredComponentFactory(Vector.empty, Vector.empty,
              (creation, parameters, resolved) => {
                given ExecutionContext = resolved
                for {
                  operation <- if (managedSources) SplitPhaseManagedSemanticStorageAssembly.bindHostRecordCompletionC(
                    creation, declaration, catalog, SplitPhaseManagedSemanticStorageAssembly.HostRecordCompletionPorts(
                      ports.catalog, ports.requirement, ports.presentation))
                    else SplitPhaseManagedSemanticStorageAssembly.bindRecordCompletionC(creation, declaration, catalog, ports)
                  operations <- if (deferred && reopen) Consequence.success(NonEmptyVector.of(operation))
                    else SplitPhaseStartServiceOperation.bindC(installOnly, ports.entry,
                      binding.persistence.definition, binding.persistence.configuration, binding.persistence.instances)
                      .map(installer => NonEmptyVector.of(operation, installer))
                } yield spec.ServiceDefinition("SplitPhaseService", spec.OperationDefinitionGroup(operations))
              }).primaryFactory
            val built = take(factory.createPrimaryC(ComponentCreate(owner, ComponentOrigin.Builtin)))
            previousComponent.foreach(previous => built should not be theSameInstanceAs(previous))
            previousComponent = Some(built)
            owner.add(built)
            if (deferred && reopen) built.protocol.services.services.find(_.name == "SplitPhaseService").get
              .operations.operations.toVector.map(_.specification.name) shouldBe Vector("submitSplitSemanticResult")
            def invoke(result: ContinuationResult[Judgment]): Consequence[String] = {
              val wire = f.wire(result)
              if (cli) new CncfRuntime().executeCommandResponse(owner,
                Array(s"${built.name}/SplitPhaseService/submitSplitSemanticResult", "--json", wire)).map(_.print)
              else owner.executeOperationResponse(Request.of(component = built.name, service = "SplitPhaseService",
                operation = "submitSplitSemanticResult", arguments = List(Argument("arg1", wire)))).flatMap {
                case OperationResponse.Scalar(value: String) => Consequence.success(value)
                case _ => Consequence.stateConflict("managed semantic response is missing")
              }
            }
            def probeRecordCall(): Unit = {
              owner.executeOperationResponse(Request.of(component = built.name, service = "SplitPhaseService", operation = "startSplitPhase",
                arguments = List(Argument("arg1", SplitPhaseStartServiceOperation.encodeCommand(seed.command.startCommand)))))
                .shouldBe(a[Consequence.Failure[?]])
              val expired = installerCalls.last
              currentRecovery.get.loadForCallC(seed.continuation.continuationId, expired) shouldBe a[Consequence.Failure[?]]
              currentRecovery.get.loadForCallC(org.goldenport.cncf.workflow.ContinuationIdentity("absent-issue"), expired)
                .shouldBe(a[Consequence.Failure[?]])
              currentRecovery.get.loadC(seed.continuation.continuationId) shouldBe a[Consequence.Failure[?]]
            }
            if (!reopen) {
              When("issued work has no preparatory input/command record")
              invoke(submitted) shouldBe a[Consequence.Failure[?]]
              take(binding.completion.loadRetainedC(submitted.continuationId)) shouldBe None
              SplitPhaseDefinitionBinding.sameRecord(take(fixtureInstances.loadC(seed.active.identity)).get, suspended) shouldBe true
              When("a private test-only managed record installer creates data without granting Start")
              probeRecordCall()
              installations shouldBe 1
              When("stale headers and invalid semantic payload reach the managed completion Operation")
              Vector(submitted.copy(expectedRevision = StateMachineRevision("999")),
                submitted.copy(result = submitted.result.copy(value = submitted.result.value.copy(rationale = ""))))
                .foreach(value => invoke(value) shouldBe a[Consequence.Failure[?]])
              Then("semantic rejection leaves the canonical suspension and completion unchanged")
              SplitPhaseDefinitionBinding.sameRecord(take(fixtureInstances.loadC(seed.active.identity)).get, suspended) shouldBe true
              take(binding.completion.loadRetainedC(submitted.continuationId)) shouldBe None
              observations = Vector.empty
              if (crossed) {
                When("the original input record crosses the actual issued Continuation")
                invoke(submitted) shouldBe a[Consequence.Failure[?]]
                take(binding.completion.loadRetainedC(submitted.continuationId)) shouldBe None
                SplitPhaseDefinitionBinding.sameRecord(take(fixtureInstances.loadC(seed.active.identity)).get, suspended) shouldBe true
              }
            }
            if (deferred && hostIndex == 1) {
              Then("publication and original input survived pool shutdown without any installer or successful Result")
              take(binding.issued.loadC(submitted.continuationId)) shouldBe Some(work)
              take(binding.completion.loadRetainedC(submitted.continuationId)) shouldBe None
              SplitPhaseDefinitionBinding.sameRecord(take(fixtureInstances.loadC(seed.active.identity)).get, suspended) shouldBe true
              When("stale and invalid results reach the new host before first completion")
              Vector(submitted.copy(expectedRevision = StateMachineRevision("999")),
                submitted.copy(result = submitted.result.copy(value = submitted.result.value.copy(rationale = ""))))
                .foreach(value => invoke(value) shouldBe a[Consequence.Failure[?]])
              take(binding.completion.loadRetainedC(submitted.continuationId)) shouldBe None
              SplitPhaseDefinitionBinding.sameRecord(take(fixtureInstances.loadC(seed.active.identity)).get, suspended) shouldBe true
            }
            if (completeNow) {
              observations = Vector.empty
              When(s"the first valid decision-needed Result recovers original intent through Service or CLI at host $hostIndex")
              val wait = take(WorkflowWaitJsonV1.decodeC(take(invoke(submitted))))
              Then("one managed closing stores revision three and returns WAIT without the legacy runtime or catalog")
              wait.handle.instanceIdentity shouldBe work.handle.instanceIdentity
              wait.current shouldBe a[WorkflowContinuation.Wait]
              if (managedSources) observations shouldBe empty
              else {
                val capsule = observations.head
                observations.foreach(_ should be theSameInstanceAs capsule)
                capsule.component.get should be theSameInstanceAs built
                currentRecovery.get.loadForCallC(seed.continuation.continuationId, capsule) shouldBe a[Consequence.Failure[?]]
              }
              completed = take(fixtureInstances.loadC(seed.active.identity))
              completed.get.history.map(_.revision.value) shouldBe Vector(1L, 2L, 3L)
              completed.get.lifecycle shouldBe Lifecycle.Active
              completed.get.suspension shouldBe None
              f.current.revision shouldBe InstanceRevision(2L)
              take(f.runtime.recoverClaimC(submitted.continuationId)) shouldBe fixtureClaim
              f.catalogCalls shouldBe 0
              completionCount += 1
            }
            if (reopen && !deferred) probeRecordCall()
            installations shouldBe 1
            f.retained shouldBe empty
            Then(if (completed.isEmpty) "publication remains suspended with no Result before completion or under crossed provenance"
              else "exact Result and typed closing evidence remain readable and replay cannot add history")
            if (completed.isEmpty) {
              take(binding.completion.loadRetainedC(submitted.continuationId)) shouldBe None
              take(binding.issued.loadC(submitted.continuationId)) shouldBe Some(work)
            }
            else {
              val retained = take(binding.completion.loadRetainedC(submitted.continuationId)).get.completion
              retained.instanceRevision shouldBe InstanceRevision(3L)
              retained.result shouldBe submitted
              retained.closingResult.get.typeReference shouldBe StateMachineResultTypeReference("SplitPhaseSemanticResult")
            }
            if (crossed || completed.nonEmpty) invoke(submitted) shouldBe a[Consequence.Failure[?]]
            SplitPhaseDefinitionBinding.sameRecord(take(fixtureInstances.loadC(seed.active.identity)).get,
              completed.getOrElse(suspended)) shouldBe true
          }
        }
        completionCount shouldBe (if (crossed) 0 else 1)
        installations shouldBe 1
      }
    }

    "retain atomically issued semantic work across actual SQLite host shutdown and reopen" in {
      withFixture() { f =>
        Given("existing semantic fixture work and a separate canonical SQLite storage grant, not a public Start issuer")
        val work = f.begin()
        val seed = f.retained.values.head
        val suspended = f.current
        val path = Files.createTempDirectory("smwf-split-semantic-reopen-").resolve("host.sqlite")
        def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "splitsaved", name))
        val declaration = SplitPhaseManagedStartAssembly.Declaration(configuration, collection("instances"), collection("continuations"))
        val catalog = take(SplitPhaseRuntimeCatalog.loadC())
        // Private fixture construction under the official catalog, not a
        // production alias or acceptance of the partial fixture definition.
        val active = seed.active.copy(definition = catalog.definition)
        val initial = seed.prepared.initial.copy(definition = catalog.definition)
        val expected = suspended.copy(definition = catalog.definition)
        val policy = take(EventPersistencePolicy.createC("split-semantic-v1", Vector(
          EventPersistencePolicy.Rule(EventTypeId("transient"), EventPersistencePolicy.Mode.Transient))))
        val storage = take(EventStorePersistenceDeclaration.createC("events", collection("cursor"),
          collection("events"), collection("indexes"), Vector(EventStorePersistenceDeclaration.WorkflowGrant(
            SmWorkflowComponent.componentId, configuration.workflowStore.name, declaration.instances, declaration.continuations))))
        def withHost(body: (SubsystemEventStoreBootstrap, ExecutionContext) => Unit): Unit = {
          val context = ExecutionContext.create()
          val hostConfig = ResolvedConfiguration(GenericConfiguration(Map(
            "textus.datastore.kind" -> ConfigurationValue.StringValue("sqlite"),
            "textus.datastore.path" -> ConfigurationValue.StringValue(path.toString))), ConfigurationTrace.empty)
          val host = take(SubsystemEventStoreBootstrap.createC(policy, storage,
            ScopeContext(ScopeKind.Subsystem, "split-semantic-storage", None, context.observability), hostConfig))
          try body(host, context) finally take(host.shutdownC())
        }
        val intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(
          WorkflowInstanceAtomicTransitionV1.schemaVersion, configuration, active, active.revision,
          expected.history.last, ContinuationRuntimePersistence.Record(seed.continuation, ContinuationRuntimePersistence.Status.Available))
        withHost { (host, context) =>
          given ExecutionContext = context
          val binding = take(SplitPhaseManagedSemanticStorageAssembly.bindC(
            ComponentCreate(host.subsystem, ComponentOrigin.Builtin), declaration, catalog))
          val fixtureInstances = take(binding.persistence.storage.bindInstancesC())
          fixtureInstances.createC(seed.active) shouldBe a[Consequence.Failure[?]]
          take(fixtureInstances.createC(initial))
          val started = take(fixtureInstances.appendC(initial.identity, initial.revision, active.history.last))
          SplitPhaseDefinitionBinding.sameRecord(started, active) shouldBe true
          binding.issued.putIfAbsentC(work) shouldBe a[Consequence.Failure[?]]
          When("the common managed binding atomically publishes the fixture suspension and exact WorkOrder")
          val saved = take(binding.issued.suspendC(intent, work))
          Then("the retained instance and WorkOrder match the admitted full-catalog fixture")
          SplitPhaseDefinitionBinding.sameRecord(saved.instance, expected) shouldBe true
          take(binding.issued.loadC(seed.continuation.continuationId)) shouldBe Some(work)
          take(binding.completion.loadRetainedC(seed.continuation.continuationId)) shouldBe None
          When("the consumed suspension is repeated and the issued WorkOrder is retried immutably")
          binding.issued.suspendC(intent, work) shouldBe a[Consequence.Failure[?]]
          take(binding.issued.putIfAbsentC(work)) shouldBe work
          Then("the stale transition is refused and immutable retry adds no history entry")
          val retried = take(fixtureInstances.loadC(active.identity)).get
          SplitPhaseDefinitionBinding.sameRecord(retried, expected) shouldBe true
          binding.issued.loadC(seed.continuation.continuationId)(using null) shouldBe a[Consequence.Failure[?]]
        }
        When("another canonical host reopens only the same configured SQLite database")
        withHost { (host, context) =>
          given ExecutionContext = context
          val binding = take(SplitPhaseManagedSemanticStorageAssembly.bindC(
            ComponentCreate(host.subsystem, ComponentOrigin.Builtin), declaration, catalog))
          val fixtureInstances = take(binding.persistence.storage.bindInstancesC())
          val loaded = take(fixtureInstances.loadC(active.identity)).get
          Then("instance history and typed semantic WorkOrder survive pool shutdown with no republishing")
          SplitPhaseDefinitionBinding.sameRecord(loaded, expected) shouldBe true
          take(binding.issued.loadC(seed.continuation.continuationId)) shouldBe Some(work)
          take(binding.completion.loadRetainedC(seed.continuation.continuationId)) shouldBe None
          SplitPhaseManagedSemanticStorageAssembly.bindC(ComponentCreate(host.subsystem, ComponentOrigin.Builtin),
            declaration.copy(continuations = collection("foreign")), catalog) shouldBe a[Consequence.Failure[?]]
        }
      }
    }

    "retain the completion capsule across selection, evidence, intent and automatic Preview" in {
      for (cli <- Vector(false, true)) withFixture(cli, callScoped = true) { f =>
        Given("the existing memory semantic fixture with only call-scoped application adapters")
        val work = f.begin()
        val result = f.result(work)
        f.currentReads = Vector.empty
        When("the issued semantic Result is returned through canonical Service or CLI dispatch")
        val terminal = take(WorkflowTerminalJsonV1.decodeC(take(f.submit(result)), SplitPhasePreviewServiceOperation.terminalCodec))
        Then("revalidation, intent and automatic Preview all use one incoming completion capsule")
        terminal.handle.instanceIdentity shouldBe work.handle.instanceIdentity
        f.currentReads.map(_._1).toSet shouldBe Set("selection", "source", "candidate", "inventory", "draft",
          "validation", "boundary", "intent-load", "catalog", "instance-load", "instance-append")
        val capsule = f.currentReads.head._2
        f.currentReads.foreach { case (_, current) => current should be theSameInstanceAs capsule }
        capsule.component.get should be theSameInstanceAs f.app
        f.current.history.map(_.revision.value) shouldBe Vector(1L, 2L, 3L, 4L)
        f.creates shouldBe 1
        f.catalogCalls shouldBe 1
        When("the same Result is replayed")
        Then("common claim admission refuses replay without another catalog call")
        f.submit(result) shouldBe a[Consequence.Failure[?]]
        f.catalogCalls shouldBe 1
      }
    }

    "refuse incomplete completion capsules before callbacks or claim consumption" in {
      withFixture(callScoped = true) { f =>
        Given("one issued semantic Result with an unconsumed claim")
        val result = f.result(f.begin())
        val before = f.current
        val claim = take(f.runtime.recoverClaimC(result.continuationId))
        val operation = take(f.workflow.completionC)
        val action = take(operation.createOperationRequest(Request.of(component = "sm-workflow", service = "SplitPhaseService",
          operation = "submitSplitSemanticResult", arguments = List(Argument("arg1", f.wire(result)))))).asInstanceOf[CommandAction]
        f.currentReads = Vector.empty
        When("execution receives a null capsule or null execution context")
        val results = Vector(action.createCall(null).execute(), action.createCall(ActionCall.Core(action, null, None, None)).execute())
        Then("both attempts refuse without read callbacks, history changes, or consuming the live claim")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        f.currentReads shouldBe empty
        f.current shouldBe before
        take(f.runtime.recoverClaimC(result.continuationId)) shouldBe claim
        f.catalogCalls shouldBe 0
        When("the original Result is dispatched with a valid current call")
        Then("the retained instance can still complete through the canonical engine")
        take(f.submit(result))
        f.current.lifecycle shouldBe Lifecycle.Completed
      }
    }

    "automatically PREVIEW the same instance through Service and CLI without document writes" in {
      for (cli <- Vector(false, true)) withFixture(cli) { f =>
        val bytes = Files.readAllBytes(root.resolve("docs/phase/phase-77.md")).toVector
        val work = f.begin()
        val request = f.order(work)
        request.operation shouldBe StateMachineOperationIdentity("SplitSemanticService", "assessNovelSplitWork")
        request.completionOperation shouldBe StateMachineOperationIdentity("SplitPhaseService", "submitSplitSemanticResult")
        request.input.get.value.unresolvedWorkItems.map(_.identity) shouldBe Vector("work-2")
        request.input.get.value.unresolvedEdges.map(_.afterItem) shouldBe Vector("work-2")
        request.input.get.value.knownEstimates.map(v => v.workItemIdentity -> v.minutes) shouldBe
          Vector("work-1" -> 180, "work-3" -> 360)
        val again = take(f.invoke("assessSplitProposal", SplitPhaseProposalAssessmentServiceOperation.encodeCommand(f.assessment)))
        take(WorkflowWorkOrderJsonV1.decodeC(again, SplitPhaseSemanticJson.requestCodec)) shouldBe work
        f.current.history.size shouldBe 2
        val result = f.result(work)
        val terminal = take(WorkflowTerminalJsonV1.decodeC(take(f.submit(result)), SplitPhasePreviewServiceOperation.terminalCodec))
        terminal.handle.instanceIdentity shouldBe work.handle.instanceIdentity
        f.current.lifecycle shouldBe Lifecycle.Completed
        f.current.revision shouldBe InstanceRevision(4L)
        f.current.history.map(_.revision.value) shouldBe Vector(1L, 2L, 3L, 4L)
        f.creates shouldBe 1
        f.catalogCalls shouldBe 1
        f.submit(result) shouldBe a[Consequence.Failure[?]]
        f.catalogCalls shouldBe 1
        Files.readAllBytes(root.resolve("docs/phase/phase-77.md")).toVector shouldBe bytes
      }
    }

    "connect edge-only and novel-only gaps without reassessing known inputs" in {
      for ((novel, edge) <- Vector(false -> true, true -> false)) withFixture(novel = novel, edge = edge) { f =>
        val work = f.begin()
        f.order(work).input.get.value.unresolvedWorkItems.nonEmpty shouldBe novel
        f.order(work).input.get.value.unresolvedEdges.nonEmpty shouldBe edge
        take(f.submit(f.result(work)))
        f.current.lifecycle shouldBe Lifecycle.Completed
      }
    }

    "reject malformed semantic coverage, ranges, new authority and foreign context before consuming the claim" in {
      withFixture() { f =>
        val valid = f.result(f.begin())
        val before = f.current
        val claim = take(f.runtime.recoverClaimC(valid.continuationId))
        val value = valid.result.value
        val estimate = value.novelEstimates.head
        val boundary = value.boundaries.head
        val invalid = Vector(value.copy(novelEstimates = Vector.empty),
          value.copy(novelEstimates = Vector(estimate.copy(workItemIdentity = "work-1"))),
          value.copy(novelEstimates = Vector(estimate.copy(minimumMinutes = 0))),
          value.copy(novelEstimates = Vector(estimate.copy(maximumMinutes = 400))),
          value.copy(boundaries = Vector.empty),
          value.copy(boundaries = Vector(boundary.copy(authorityReference = "worker-created"))),
          value.copy(contextDigest = "b" * 64), value.copy(decision = "Closed"),
          value.copy(decision = "decision-needed"))
        invalid.foreach { payload =>
          f.submit(valid.copy(result = valid.result.copy(value = payload))) shouldBe a[Consequence.Failure[?]]
          f.current shouldBe before
          take(f.runtime.recoverClaimC(valid.continuationId)) shouldBe claim
        }
        take(f.submit(valid))
        f.current.lifecycle shouldBe Lifecycle.Completed
      }
    }

    "reject stale common headers and worker-selected state in strict JSON before claim consumption" in {
      withFixture() { f =>
        val valid = f.result(f.begin())
        val before = f.current
        val claim = take(f.runtime.recoverClaimC(valid.continuationId))
        val invalid = Vector(valid.copy(runId = StateMachineRunIdentity("foreign")),
          valid.copy(expectedRevision = StateMachineRevision("999")),
          valid.copy(contextSnapshot = valid.contextSnapshot.copy(modelRevision = Some("stale"))))
        invalid.foreach(value => f.submit(value) shouldBe a[Consequence.Failure[?]])
        val wire = f.wire(valid)
        Vector(wire.replace("\"SplitPhaseSemanticResult\"", "\"ForeignResult\""),
          wire.replace("\"decision\":", "\"nextState\":\"Closed\",\"decision\":"),
          wire.replace("\"minimumMinutes\":", "\"nextState\":\"Closed\",\"minimumMinutes\":"))
          .foreach(value => f.invoke("submitSplitSemanticResult", value) shouldBe a[Consequence.Failure[?]])
        f.current shouldBe before
        take(f.runtime.recoverClaimC(valid.continuationId)) shouldBe claim
        take(f.submit(valid))
      }
    }

    "revalidate current host policy, source, proposal, occupied IDs and authority before the claim" in {
      withFixture() { f =>
        val valid = f.result(f.begin())
        val before = f.current
        val claim = take(f.runtime.recoverClaimC(valid.continuationId))
        val old = f.validation
        val draft = f.draft
        val changes = Vector[() => Unit](
          () => f.validation = old.copy(partitionPolicy = old.partitionPolicy.copy(version = "changed")),
          () => f.sourceRevisionDrift = true,
          () => f.draft = draft.copy(children = draft.children.map(_.copy(expectedMinutes = 361))),
          () => f.occupiedDrift = true,
          () => f.authorityDrift = true)
        changes.foreach { change =>
          change()
          f.submit(valid) shouldBe a[Consequence.Failure[?]]
          f.current shouldBe before
          take(f.runtime.recoverClaimC(valid.continuationId)) shouldBe claim
          f.validation = old; f.draft = draft; f.sourceRevisionDrift = false
          f.occupiedDrift = false; f.authorityDrift = false
        }
        take(f.submit(valid))
      }
    }

    "retain decision-needed as nonterminal WAIT without granting authority or previewing" in {
      withFixture() { f =>
        val valid = f.result(f.begin())
        val value = valid.result.value.copy(decision = "decision-needed", novelEstimates = Vector.empty, boundaries = Vector.empty)
        val wait = take(WorkflowWaitJsonV1.decodeC(take(f.submit(valid.copy(result = valid.result.copy(value = value))))))
        wait.current shouldBe a[WorkflowContinuation.Wait]
        f.current.lifecycle shouldBe Lifecycle.Active
        f.current.revision shouldBe InstanceRevision(3L)
        f.current.currentProgression shouldBe Some(ProgressionReference("entry.SemanticAssessment"))
        f.current.suspension shouldBe None
        f.catalogCalls shouldBe 0
        f.runtime.recoverClaimC(valid.continuationId) shouldBe a[Consequence.Failure[?]]
      }
    }

    "publish no WorkOrder when intent or suspension persistence fails" in {
      for (intent <- Vector(false, true)) withFixture() { f =>
        take(f.invoke("startSplitPhase", SplitPhaseStartServiceOperation.encodeCommand(f.start)))
        if (intent) f.failIntent = true else f.failAppendRevision = Some(2L)
        f.invoke("assessSplitProposal", SplitPhaseProposalAssessmentServiceOperation.encodeCommand(f.assessment)) shouldBe
          a[Consequence.Failure[?]]
        f.current.revision shouldBe InstanceRevision(1L)
        f.current.lifecycle shouldBe Lifecycle.Active
        f.current.suspension shouldBe None
      }
    }

    "report loose completion and preview persistence failures without claiming terminal success" in {
      for (failure <- Vector("resume-append", "catalog", "terminal-append")) withFixture() { f =>
        val bytes = Files.readAllBytes(root.resolve("docs/phase/phase-77.md")).toVector
        val valid = f.result(f.begin())
        failure match {
          case "resume-append" => f.failAppendRevision = Some(3L)
          case "catalog" => f.failCatalog = true
          case _ => f.failAppendRevision = Some(4L)
        }
        f.submit(valid) shouldBe a[Consequence.Failure[?]]
        f.current.lifecycle shouldBe Lifecycle.Active
        f.current.revision shouldBe InstanceRevision(if (failure == "resume-append") 2L else 3L)
        f.current.history.exists(_.lifecycle == Lifecycle.Completed) shouldBe false
        Files.readAllBytes(root.resolve("docs/phase/phase-77.md")).toVector shouldBe bytes
      }
    }

    "keep public preview revision three unavailable without the host-only admitted record" in {
      withFixture() { f =>
        val work = f.begin()
        f.invoke("previewSplitPhase", SplitPhasePreviewServiceOperation.encodeCommand(
          SplitPhasePreviewCommand(f.start, "3", "worker-chosen"))) shouldBe a[Consequence.Failure[?]]
        f.current.revision shouldBe InstanceRevision(2L)
        take(f.runtime.recoverClaimC(f.order(work).continuationId))
      }
    }
  }
}
