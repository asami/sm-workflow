package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.{Component, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.{Configuration as GenericConfiguration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{GoalPhaseStartCommand, GoalPhaseStartInput, RepairStepResult, ReviewStepResult, StepCloseRequest, StepReviewEvidence}

/**
 * Shared test-classpath Host fixture. It performs no real Git or external command.
 * @since   Sep. 29, 2026
 * @version Oct.  8, 2026
 */
private[admission] object GoalPhaseApplicationFixture {
  def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => throw new IllegalStateException(s"repair loop rejected: $failure")
  }
  def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString.stripSuffix("\n") finally source.close()
  }
  def digest(content: Vector[Byte]): String = MessageDigest.getInstance("SHA-256")
    .digest(content.toArray).map(byte => f"${byte & 0xff}%02x").mkString
  val now = Instant.parse("2026-09-28T00:00:00Z")
  val sidecar = take(CandidateAdmissionProducerAbi.parseC(
    resource("/workflow/goal-phase-candidate-admission-producer-abi.json")))
  val binding = take(WorkflowInstancePersistence.bindCandidateDefinitionC(take(
    CandidateWorkflowAbi.parseC(resource("/workflow/goal-phase-workflow-abi.json"), sidecar,
      "src/main/cozy/sm-workflow.cml"))))

}

private[admission] class GoalPhaseApplicationFixture(val viaCli: Boolean = false, failIntent: Boolean = false,
    fromPublicStart: Boolean = false, withAuthority: Boolean = false,
    programText: String = "original program\n", planningText: String = "original planning\n",
    callScopedAuthority: Boolean = false) {
    import GoalPhaseApplicationFixture.*
    val component = SmWorkflowComponent.componentId
    var program = programText.getBytes(StandardCharsets.UTF_8).toVector
    val planning = planningText.getBytes(StandardCharsets.UTF_8).toVector
    var snapshotMissing = false
    val request = StepCloseRequest("close-loop", "step-loop", digest(program), digest(planning), Vector.empty)
    val originalCandidate = candidate(request)
    val initial = InstanceRecord(InstanceIdentity("goal-loop"), binding, InstanceRevision(0L),
      Lifecycle.NotStarted, None, CorrelationReference("loop-correlation"), CausationReference("loop-start"),
      Some(DerivedCompositeOccurrenceReference("loop-occurrence")),
      Some(CommittedEntityTransitionReference("candidate-committed")), None, Vector.empty)
    var stored: InstanceRecord = if (fromPublicStart) null else take(initial.appendC(InstanceRevision(0L), HistoryEntry(HistorySequence(1L),
      InstanceRevision(1L), Lifecycle.Active, Some(ProgressionReference("step.Awaiting")),
      initial.correlation, initial.causation, initial.derivedCompositeOccurrence, initial.committedPredecessor, None)))
    val configuration = Configuration(NamedWorkflowStore("workflow-primary", WorkflowStoreIdentity("workflow-store")),
      MigrationPolicy("fixture-v1"), RetentionPolicy("fixture-v1"), LeasePolicy("fixture-v1"),
      DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store")))
    var closeInvocations = 0
    var creates = 0
    var appends = 0
    var failAppendRevision: Option[InstanceRevision] = None
    var hostCloseInstance: Option[InstanceIdentity] = None
    var commits = Vector.empty[GoalPhaseStepClosureBoundary.CommitRequest]
    val instances = new WorkflowInstancePersistence {
      def create(config: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
        if (config != configuration || !fromPublicStart || stored != null)
          Consequence.stateConflict("fixture create conflict")
        else record.validateC.map { valid => creates += 1; stored = valid; valid }
      def load(config: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
        if (config != configuration) Consequence.stateConflict("fixture store mismatch")
        else Consequence.success(Option(stored).filter(_.identity == identity))
      def append(config: Configuration, identity: InstanceIdentity, expected: InstanceRevision,
        entry: HistoryEntry): Consequence[InstanceRecord] =
        if (failAppendRevision.contains(entry.revision))
          Consequence.stateConflict("planned instance append failure")
        else if (config != configuration || stored == null || identity != stored.identity ||
            (entry.lifecycle == Lifecycle.Completed && commits.isEmpty))
          Consequence.stateConflict("closure preceded deterministic commit")
        else stored.appendC(expected, entry).map { next => appends += 1; stored = next; next }
    }
    val snapshotPort = new GoalPhaseStepClosureBoundary.SnapshotPort {
      def loadC(step: String): Consequence[GoalPhaseStepClosureBoundary.ArtifactSnapshot] =
        if (snapshotMissing || step != request.stepIdentity) Consequence.stateConflict("snapshot unavailable")
        else Consequence.success(GoalPhaseStepClosureBoundary.ArtifactSnapshot(program, planning))
    }
    val commitPort = new GoalPhaseStepClosureBoundary.CommitPort {
      def commitC(selected: GoalPhaseStepClosureBoundary.CommitRequest,
        snapshot: GoalPhaseStepClosureBoundary.ArtifactSnapshot): Consequence[GoalPhaseStepClosureBoundary.CommitReceipt] =
        if (snapshot.program != program || snapshot.planning != planning || commits.nonEmpty)
          Consequence.stateConflict("duplicate or changed commit")
        else {
          commits :+= selected
          Consequence.success(GoalPhaseStepClosureBoundary.CommitReceipt("f" * 40,
            selected.idempotencyKey, selected.stepIdentity, selected.candidateRevision,
            selected.programDigest, selected.planningDigest, selected.reviewEvidenceIdentity,
            selected.fromState, selected.event, selected.toState))
        }
    }
    val runtime = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory)
    val issued = new IssuedWorkOrderPersistence.InMemory[StepCloseRequest]
    var retained = Map.empty[org.goldenport.cncf.workflow.ContinuationIdentity, GoalPhaseRepairCandidateBoundary.Intent]
    val intents = new GoalPhaseRepairLoop.IntentPort {
      def saveC(id: org.goldenport.cncf.workflow.ContinuationIdentity,
        intent: GoalPhaseRepairCandidateBoundary.Intent): Consequence[Unit] =
        if (failIntent || retained.contains(id)) Consequence.stateConflict("intent persistence failed")
        else { retained = retained.updated(id, intent); Consequence.unit }
      def loadC(id: org.goldenport.cncf.workflow.ContinuationIdentity):
        Consequence[Option[GoalPhaseRepairCandidateBoundary.Intent]] = Consequence.success(retained.get(id))
    }
    def operation(capability: String): StateMachineRequiredOperation = {
      val spi = sidecar.models.head.requiredSpi.find(_.identity.value == capability).get
      StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(capability), spi.actionIdentity.value,
        StateMachineOperationIdentity(spi.operation.service.value, spi.operation.name.value),
        spi.operation.inputType.map(t => StateMachineInputTypeReference(t.value)),
        spi.operation.resultType.map(t => StateMachineResultTypeReference(t.value)),
        StateMachineRequiredOperationMetadata(ContextContract(s"$capability-context", Vector.empty, Vector.empty),
          CompletionContract(s"$capability-completion", Vector.empty), EvidenceContract(s"$capability-evidence", Vector.empty), Vector.empty))
    }
    val review = operation("review-step-capability")
    val repair = operation("repair-step-capability")
    def requirement(capability: String, review: Boolean): ExecutionRequirement = ExecutionRequirement(
      Vector(CapabilityRequirement(capability)), RiskLevel("standard"), ReasoningLevel.Deep, review)
    val reviewRequirement = requirement("review-step-capability", true)
    def fresh(): UnitOfWork = new UnitOfWork(ExecutionContext.create(), EventEngine.noop(DataStore.noop()))
    val startCommand = GoalPhaseStartCommand(GoalPhaseStartInput("77.1", "cncf"),
      "goal-selection", "goal-participant", "goal-start")
    var selected: Option[ProfileStartAdmission.Selection] = Some(ProfileStartAdmission.Selection(
      startCommand.selectionIdentity, StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase"),
      binding.workflowIdentity, binding.workflowRevision, startCommand.participantIdentity,
      ProfileStartAdmission.SelectionOrigin.ExplicitHuman, startCommand.idempotencyKey, None,
      Some(GoalPhaseStartCommandAdapter.selectedInput(startCommand.input))))
    val selection = new ProfileStartAdmission.SelectionPort {
      def loadC(id: String): Consequence[Option[ProfileStartAdmission.Selection]] =
        Consequence.success(selected.filter(_.selectionId == id))
    }
    val invocation = new GoalPhaseStepCloseServiceOperation.InvocationPort {
      def resolveC(submission: String): Consequence[WorkflowHandle] = {
        closeInvocations += 1
        if (stored == null || (fromPublicStart &&
            (submission != request.submissionIdentity || !hostCloseInstance.contains(stored.identity))))
          Consequence.stateConflict("submission has no host-associated Start instance")
        else WorkflowHandle.fromRecordC(component, stored)
      }
    }
    var authorityCalls = 0
    var decisionCalls = 0
    var captured: Option[GoalPhaseClosureAuthority.Intent] = None
    var authorization: Option[GoalPhaseClosureAuthority.Authorization] = None
    var autoGrant = false
    var failDecision = false
    var failReceipt = false
    var wrongReceipt = false
    var receiptOverride: Option[GoalPhaseReviewedClosure.Record] = None
    var onAuthority: GoalPhaseClosureAuthority.Intent => Unit = _ => ()
    val receiptStore = new GoalPhaseReviewedClosure.InMemory
    val receipts = new GoalPhaseReviewedClosure.ReceiptPort {
      def putIfAbsentC(record: GoalPhaseReviewedClosure.Record): Consequence[GoalPhaseReviewedClosure.Record] =
        if (failReceipt) Consequence.stateConflict("planned accepted review persistence failure")
        else if (wrongReceipt) Consequence.success(record.copy(reviewed = initial))
        else receiptStore.putIfAbsentC(record)
      def loadC(id: InstanceIdentity, revision: InstanceRevision): Consequence[Option[GoalPhaseReviewedClosure.Record]] =
        receiptOverride match {
          case Some(value) => Consequence.success(Some(value))
          case None => receiptStore.loadC(id, revision)
        }
    }
    def grant(intent: GoalPhaseClosureAuthority.Intent): GoalPhaseClosureAuthority.Authorization =
      GoalPhaseClosureAuthority.Authorization(intent.handle, intent.expectedRevision, intent.snapshot,
        intent.input.requestDigest, "recorded-local-authority", "human", now.minusSeconds(60), now.plusSeconds(3600))
    val staticAuthorityPorts = GoalPhaseClosureAuthority.Ports(new GoalPhaseClosureAuthority.AuthorizationPort {
      def loadC(intent: GoalPhaseClosureAuthority.Intent): Consequence[Option[GoalPhaseClosureAuthority.Authorization]] = {
        authorityCalls += 1; captured = Some(intent); onAuthority(intent)
        Consequence.success(if (autoGrant) Some(grant(intent)) else authorization)
      }
    }, new GoalPhaseClosureAuthority.DecisionPort {
      def resolveC(intent: GoalPhaseClosureAuthority.Intent):
        Consequence[ContinuationRequest[GoalPhaseClosureAuthority.Input]] = {
        decisionCalls += 1
        if (failDecision) Consequence.stateConflict("planned authority display failure")
        else Consequence.success(ContinuationRequest(
          StateMachineRunIdentity(intent.handle.instanceIdentity.value),
          org.goldenport.cncf.workflow.ContinuationIdentity(s"authority-decision:${intent.idempotencyKey}"),
          intent.expectedRevision, StateMachineRequiredOperationIdentity("host.step-close-authority"),
          StateMachineOperationIdentity("HumanBoundaryFixture", "decideStepClose"),
          Some(TypedValue(GoalPhaseClosureAuthority.inputCodec.typeIdentity, intent.input)), None,
          ContextBundle("Resolve exact local commit authority",
            Vector(ContextReference(intent.input.candidate.stepIdentity, intent.input.candidateRevision)),
            Vector(ContextReference(intent.input.candidate.submissionIdentity, intent.input.requestDigest)), intent.snapshot),
          CompletionContract("human-stop-only", Vector.empty), EvidenceContract("human-evidence", Vector.empty),
          StateMachineOperationIdentity("HumanBoundaryFixture", "recordStepCloseAuthority")))
      }
    })
    var authorityCores = Vector.empty[ActionCall.Core]
    var decisionCores = Vector.empty[ActionCall.Core]
    val authorityPorts = if (!callScopedAuthority) staticAuthorityPorts else GoalPhaseClosureAuthority.Ports(
      new GoalPhaseClosureAuthority.CallScopedAuthorizationPort {
        def loadForCallC(intent: GoalPhaseClosureAuthority.Intent, core: ActionCall.Core) = {
          authorityCores :+= core
          staticAuthorityPorts.authorizations.loadC(intent)
        }
      }, new GoalPhaseClosureAuthority.CallScopedDecisionPort {
        def resolveForCallC(intent: GoalPhaseClosureAuthority.Intent, core: ActionCall.Core) = {
          decisionCores :+= core
          staticAuthorityPorts.decisions.resolveC(intent)
        }
      })
    val closurePorts = GoalPhaseReviewedClosure.Ports(authorityPorts, receipts)
    val ports = GoalPhaseFixtureServiceAssembly.Ports(selection, binding, configuration, instances,
      invocation, sidecar, () => now, runtime, issued, () => fresh(), snapshotPort, commitPort,
      intents, review, repair, reviewRequirement, requirement("repair-step-capability", false),
      if (withAuthority) Some(closurePorts) else None)
    val service = take(GoalPhaseFixtureServiceAssembly.bindC(ports))
    val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
    val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(name = "repair-loop", version = None,
      configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
      aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
    val app = new Component() {}
    app.initialize(ComponentInit(subsystem,
      Component.Core.create(component.name, component, ComponentInstanceId.default(component), protocol), ComponentOrigin.Builtin))
    subsystem.add(app)
    val cli = new CncfRuntime()
    def invoke(operation: String, wire: String): Consequence[String] =
      if (viaCli) cli.executeCommandResponse(subsystem,
        Array(s"${component.name}/GoalPhaseService/$operation", "--json", wire)).map(_.print)
      else subsystem.executeOperationResponse(Request.of(component = component.name, service = "GoalPhaseService",
        operation = operation, arguments = List(Argument("arg1", wire)))).flatMap {
          case OperationResponse.Scalar(value: String) => Consequence.success(value)
          case _ => Consequence.stateConflict("no common JSON envelope")
        }
    def candidate(input: StepCloseRequest): String = take(StepClosureAdmission.candidateRefC(
      StepClosureAdmission.StepSnapshot(input.stepIdentity, input.programDigest, input.planningDigest))).revision
    def order(interaction: WorkflowInteraction[StepCloseRequest, Nothing]): ContinuationRequest[? <: StepCloseRequest] =
      interaction.current match {
        case WorkflowContinuation.WorkOrder(request, _, _) => request
        case other => throw new IllegalStateException(s"not a WorkOrder: $other")
      }
    def work(wire: String): WorkflowInteraction[StepCloseRequest, Nothing] =
      take(WorkflowWorkOrderJsonV1.decodeC(wire, GoalPhaseStepCloseJson.payloadCodec))
    def result[R](interaction: WorkflowInteraction[StepCloseRequest, Nothing], value: R,
      typeIdentity: String, reference: ContextReference): ContinuationResult[R] = {
      val issued = order(interaction)
      ContinuationResult(interaction.handle, issued.runId, issued.continuationId, issued.expectedRevision,
        issued.context.snapshot, TypedValue(typeIdentity, value), reference, Vector.empty, ExecutionEvidence(Vector.empty))
    }
    def reviewResult(interaction: WorkflowInteraction[StepCloseRequest, Nothing], decision: String,
      target: Option[String] = None): ContinuationResult[ReviewStepResult] = {
      val revision = target.getOrElse(candidate(order(interaction).input.get.value))
      val evidence = StepReviewEvidence(s"review-${order(interaction).expectedRevision.value}", revision,
        "2026-09-27T23:59:00Z", "2026-09-28T01:00:00Z", "full-step", "review receipt", "fixture-reviewer")
      result(interaction, ReviewStepResult(decision, "review rationale", Vector(evidence)), "ReviewStepResult",
        ContextReference(evidence.evidenceIdentity, revision))
    }
    def reviewWire(result: ContinuationResult[ReviewStepResult]): String =
      take(WorkflowResultJsonV1.encodeC(result, GoalPhaseReviewCompletionOperation.payloadCodec))
    def repairWire(result: ContinuationResult[RepairStepResult]): String =
      take(WorkflowResultJsonV1.encodeC(result, GoalPhaseRepairCompletionOperation.payloadCodec))
    def firstReview(): WorkflowInteraction[StepCloseRequest, Nothing] =
      work(take(invoke("requestStepClose", GoalPhaseStepCloseJson.encode(request))))
    def start(input: GoalPhaseStartCommand = startCommand): Consequence[WorkflowInteraction[Nothing, Nothing]] =
      invoke("startGoalPhase", GoalPhaseStartServiceOperation.encodeCommand(input))
        .flatMap(WorkflowWaitJsonV1.decodeC)
    def associateClose(handle: WorkflowHandle): Unit = {
      require(take(WorkflowHandle.fromRecordC(component, stored)) == handle, "close handle differs from Start instance")
      hostCloseInstance = Some(handle.instanceIdentity)
    }
    def repairWork(): WorkflowInteraction[StepCloseRequest, Nothing] =
      work(take(invoke("submitStepReview", reviewWire(reviewResult(firstReview(), "repair")))))
    def repaired(work: WorkflowInteraction[StepCloseRequest, Nothing]): ContinuationResult[RepairStepResult] =
      result(work, RepairStepResult("completed", "repair applied", "repair-receipt"), "RepairStepResult",
        ContextReference("repair-receipt", originalCandidate))
    def project(handle: WorkflowHandle, asOf: Instant = now): Consequence[String] =
      GoalPhaseReviewedClosure.projectRetainedC(handle, sidecar, asOf, instances, configuration,
        snapshotPort, closurePorts)
    def saved: GoalPhaseReviewedClosure.Record =
      take(receiptStore.loadC(stored.identity, stored.revision)).get
  }
