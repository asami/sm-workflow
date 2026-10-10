package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

import domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1
import org.goldenport.Consequence
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
import org.goldenport.protocol.Protocol
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{ReviewStepResult, StepCloseRequest, StepReviewEvidence}

/**
 * @since   Oct.  6, 2026
 *  version Oct.  8, 2026
 * @version Oct.  9, 2026
 */
final class GoalPhaseReviewBoundarySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val now = Instant.parse("2026-09-26T00:00:00Z")
  private val programContent = "program fixture\n".getBytes(StandardCharsets.UTF_8).toVector
  private val planningContent = "planning fixture\n".getBytes(StandardCharsets.UTF_8).toVector
  private def digest(content: Vector[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(content.toArray)
      .map(byte => f"${byte & 0xff}%02x").mkString
  private val request = StepCloseRequest(
    "close-1", "step-1", digest(programContent), digest(planningContent), Vector.empty)

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"GoalPhase review boundary was rejected: $failure")
  }

  private def resource(path: String): String = {
    val source = scala.io.Source.fromInputStream(getClass.getResourceAsStream(path), "UTF-8")
    try source.mkString.stripSuffix("\n") finally source.close()
  }

  private val sidecar = take(CandidateAdmissionProducerAbi.parseC(
    resource("/workflow/goal-phase-candidate-admission-producer-abi.json")))
  private val definition = take(CandidateWorkflowAbi.parseC(
    resource("/workflow/goal-phase-workflow-abi.json"), sidecar, "src/main/cozy/sm-workflow.cml"))
  private val binding = take(WorkflowInstancePersistence.bindCandidateDefinitionC(definition))
  private val spi = sidecar.models.head.requiredSpi.head
  private val reviewOperation = StateMachineRequiredOperation(
    StateMachineRequiredOperationIdentity(spi.identity.value),
    spi.actionIdentity.value,
    StateMachineOperationIdentity(spi.operation.service.value, spi.operation.name.value),
    spi.operation.inputType.map(x => StateMachineInputTypeReference(x.value)),
    spi.operation.resultType.map(x => StateMachineResultTypeReference(x.value)),
    StateMachineRequiredOperationMetadata(
      ContextContract("goal-phase-review-context", Vector.empty, Vector.empty),
      CompletionContract("goal-phase-review-completion", Vector.empty),
      EvidenceContract("goal-phase-review-evidence", Vector.empty),
      Vector.empty
    )
  )
  private val initial = InstanceRecord(
    InstanceIdentity("goal-phase-instance-1"), binding, InstanceRevision(0L), Lifecycle.NotStarted,
    None, CorrelationReference("goal-phase-correlation"), CausationReference("goal-phase-causation"),
    Some(DerivedCompositeOccurrenceReference("goal-phase-occurrence")),
    Some(CommittedEntityTransitionReference("step-candidate-committed")), None, Vector.empty
  )
  private val active = take(initial.appendC(InstanceRevision(0L), HistoryEntry(
    HistorySequence(1L), InstanceRevision(1L), Lifecycle.Active,
    Some(ProgressionReference("step.Awaiting")),
    CorrelationReference("goal-phase-correlation"), CausationReference("goal-phase-causation"),
    Some(DerivedCompositeOccurrenceReference("goal-phase-occurrence")),
    Some(CommittedEntityTransitionReference("step-candidate-committed")), None
  )))
  private val candidateRevision = take(StepClosureAdmission.candidateRefC(
    StepClosureAdmission.StepSnapshot(request.stepIdentity, request.programDigest,
      request.planningDigest))).revision
  private val providedOperation = StateMachineOperationIdentity("GoalPhaseService", "requestStepClose")
  private val provided = StateMachineProvidedApiRequest(
    "GoalPhaseWorkflow", "goal-phase-ordered-run-v4", StateMachineRunIdentity("goal-phase-run-1"),
    providedOperation,
    Some(StateMachineOperationInput(StateMachineInputTypeReference("StepCloseRequest"),
      ContextReference("step-close-input", candidateRevision))),
    ContextBundle("Review Step candidate before closing", Vector.empty, Vector.empty,
      ContextSnapshot("goal-phase-ordered-run-v4"))
  )

  "GoalPhase review boundary" should {
    "prepare the declared review continuation and publish it only after CNCF UnitOfWork commit" in {
      Given("the generated GoalPhase declaration, exact admitted Step candidate and uncommitted owning UnitOfWork")
      When("the current generated provided-Operation declaration is inspected")
      val generated = GoalPhaseWorkflowStateMachineProvidedApi1.workflow
      Then("the complete eight-Operation ABI includes the distinct ordered-Human completion")
      generated.providedOperations.map(op => op.service -> op.name) shouldBe
        Vector("GoalPhaseService" -> "startGoalPhase", "GoalPhaseService" -> "requestStepClose",
          "GoalPhaseService" -> "submitStepReview", "GoalPhaseService" -> "submitStepRepair",
          "GoalPhaseService" -> "submitGoalPhaseHumanDecision", "GoalPhaseService" -> "advanceGoalPhase",
          "GoalPhaseService" -> "submitGoalPhaseWorkResult", "GoalPhaseService" -> "submitGoalPhaseOrderedDecision")
      generated.providedOperations.head.inputType shouldBe Some("GoalPhaseStartCommand")
      generated.providedOperations(1).inputType shouldBe Some("StepCloseRequest")
      generated.providedOperations(2).inputType shouldBe Some("ReviewStepResult")

      When("the declared review is planned and its publication is staged behind the actual commit")
      val pending = take(GoalPhaseReviewBoundary.planC(
        request, now, active, provided, reviewOperation)).getOrElse(fail("review is missing"))
      Then("the pending review retains its exact request and continuation contract")
      pending.input.value shouldBe request
      pending.continuation.requiredOperation shouldBe reviewOperation
      pending.continuation.runId shouldBe provided.runId
      pending.continuation.expectedRevision shouldBe StateMachineRevision("2")
      pending.outcome shouldBe ActionExecution.Suspended(pending.continuation)

      Given("a real uncommitted UnitOfWork and in-memory continuation and issued-work stores")
      val runtime = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory)
      val unitOfWork = new UnitOfWork(ExecutionContext.create(), EventEngine.noop(DataStore.noop()))
      var stored: Option[InstanceRecord] = None
      When("the suspension is staged for publication after the owning commit")
      runtime.stageSuspensionAfterC(unitOfWork, pending.continuation,
        () => GoalPhaseReviewBoundary.suspendedRecordC(pending, active).map { record =>
          stored = Some(record)
          ()
        }).isSuccess shouldBe true
      val issuedStore = new IssuedWorkOrderPersistence.InMemory[StepCloseRequest]
      val requirement = WorkflowProtocolV1.ExecutionRequirement(
        Vector(WorkflowProtocolV1.CapabilityRequirement("review-step-capability")),
        WorkflowProtocolV1.RiskLevel("standard"), WorkflowProtocolV1.ReasoningLevel.Deep, true)
      val presentation = WorkflowProtocolV1.MinimalPresentation(
        "Review Step closure", "Awaiting full-Step review")
      val component = SmWorkflowComponent.componentId
      val projected = take(GoalPhaseReviewBoundary.suspendedRecordC(pending, active))
      Then("the uncommitted review cannot yet be issued or claimed")
      GoalPhaseReviewBoundary.issueRecoveredWorkOrderC(
        pending, projected, component, runtime, issuedStore, requirement, presentation) shouldBe
        a[Consequence.Failure[?]]
      runtime.claimC(pending.continuation.continuationId) shouldBe a[Consequence.Failure[?]]
      stored shouldBe None
      When("the actual UnitOfWork commits")
      unitOfWork.commit().isSuccess shouldBe true
      Then("the original suspension and revision are retained and claimable")
      stored.flatMap(_.suspension).map(_.continuationIdentity.value) shouldBe
        Some(pending.continuation.continuationId.value)
      stored.map(_.revision) shouldBe Some(InstanceRevision(2L))
      runtime.claimC(pending.continuation.continuationId).isSuccess shouldBe true
      When("the retained committed review is issued through its actual completion route")
      val issued = take(GoalPhaseReviewBoundary.issueRecoveredWorkOrderC(
        pending, stored.getOrElse(fail("suspended instance was not saved")), component,
        runtime, issuedStore, requirement, presentation))
      val work = issued.current match {
        case value: WorkflowProtocolV1.WorkflowContinuation.WorkOrder[?] => value
        case other => fail(s"Expected a review WorkOrder, found $other")
      }
      Then("only committed suspension publishes the exact Step review completion route, not the ordered-Human route")
      work.request.completionOperation shouldBe
        StateMachineOperationIdentity("GoalPhaseService", "submitStepReview")
      work.request.resultType shouldBe Some(StateMachineResultTypeReference("ReviewStepResult"))
      work.request.input.map(_.typeIdentity) shouldBe Some("StepCloseRequest")
      take(issuedStore.loadC(pending.continuation.continuationId)) shouldBe Some(issued)

      val reviewEvidence = StepReviewEvidence("review-result-1", candidateRevision,
        "2026-09-25T23:59:00Z", "full-step", "reviewed", "receipt")
      val submitted = ContinuationResult(
        issued.handle, work.request.runId, work.request.continuationId,
        work.request.expectedRevision, work.request.context.snapshot,
        TypedValue("ReviewStepResult", ReviewStepResult("accept", "review passed", Vector(reviewEvidence))),
        ContextReference("review-result-1", candidateRevision), Vector.empty,
        ExecutionEvidence(Vector.empty)
      )
      val accepted = take(GoalPhaseReviewResultAdapter.assessC(sidecar, issued, submitted, now))
      accepted.closureReady shouldBe true
      accepted.repairRequested shouldBe false
      accepted.evaluation.gaps shouldBe empty
      accepted.typedJudgment.payloadType.value shouldBe "ReviewStepResult"

      // This is a Host-completion stand-in, not evidence of actual Codex execution.
      val hostProfile = WorkflowWorkerMapping.Profile("fixture-reviewer", "fixture-model",
        Set("review-step-capability"), Set("standard"), reviewCapable = true)
      val hostPolicy = WorkflowWorkerMapping.Policy("fixture-policy-v1",
        Vector(WorkflowWorkerMapping.Mapping(ReasoningLevel.Deep, hostProfile)))
      val dispatch = take(WorkflowWorkerMapping.selectC(
        issued, hostPolicy, GoalPhaseStepCloseJson.payloadCodec))
      val hostResult = take(WorkflowWorkerMapping.normalizeC(dispatch, SkillWorkResult(
        submitted.runId, submitted.continuationId, submitted.expectedRevision,
        submitted.contextSnapshot, submitted.result, submitted.resultReference,
        submitted.completionFacts, submitted.evidence.references,
        Some("fixture-host-worker"), Some(hostProfile.modelIdentity), hostProfile.identity, hostPolicy.version
      )))
      hostResult.copy(evidence = submitted.evidence) shouldBe submitted
      submitted.evidence.skillDispatch shouldBe None
      submitted.evidence.workerIdentity shouldBe None
      submitted.evidence.modelIdentity shouldBe None
      hostResult.evidence.skillDispatch shouldBe Some(SkillDispatchEvidence(
        requirement, hostProfile.identity, hostPolicy.version))
      take(GoalPhaseReviewResultAdapter.assessC(sidecar, issued, hostResult, now)) shouldBe accepted

      val repair = submitted.copy(result = submitted.result.copy(value =
        ReviewStepResult("repair", "focused repair needed", Vector(reviewEvidence.copy(scope = "focused:docs")))))
      val assessedRepair = take(GoalPhaseReviewResultAdapter.assessC(sidecar, issued, repair, now))
      assessedRepair.closureReady shouldBe false
      assessedRepair.repairRequested shouldBe true
      assessedRepair.evaluation.gaps should not be empty

      val missingFullReview = submitted.copy(result = submitted.result.copy(value =
        ReviewStepResult("accept", "premature accept", Vector(reviewEvidence.copy(scope = "focused:docs")))))
      GoalPhaseReviewResultAdapter.assessC(sidecar, issued, missingFullReview, now) shouldBe
        a[Consequence.Failure[?]]
      GoalPhaseReviewResultAdapter.assessC(sidecar, issued,
        submitted.copy(result = submitted.result.copy(typeIdentity = "OtherResult")), now) shouldBe
        a[Consequence.Failure[?]]
      GoalPhaseReviewResultAdapter.assessC(sidecar, issued,
        submitted.copy(result = submitted.result.copy(value =
          ReviewStepResult("accept", "stale review", Vector(reviewEvidence.copy(reviewedRevision = "c" * 64))))),
        now) shouldBe a[Consequence.Failure[?]]
      GoalPhaseReviewResultAdapter.assessC(sidecar, issued.copy(current = null), submitted, now) shouldBe
        a[Consequence.Failure[?]]

      val configuration = Configuration(
        NamedWorkflowStore("workflow-primary", WorkflowStoreIdentity("workflow-store")),
        MigrationPolicy("fixture-v1"), RetentionPolicy("fixture-v1"), LeasePolicy("fixture-v1"),
        DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store"))
      )
      var committed = Vector.empty[GoalPhaseStepClosureBoundary.CommitRequest]
      var committedSnapshots = Vector.empty[GoalPhaseStepClosureBoundary.ArtifactSnapshot]
      var currentSnapshot = GoalPhaseStepClosureBoundary.ArtifactSnapshot(programContent, planningContent)
      val snapshotPort = new GoalPhaseStepClosureBoundary.SnapshotPort {
        def loadC(stepIdentity: String): Consequence[GoalPhaseStepClosureBoundary.ArtifactSnapshot] =
          if (stepIdentity == request.stepIdentity) Consequence.success(currentSnapshot)
          else Consequence.stateConflict("unknown Step artifact snapshot")
      }
      var rejectClosedAppendOnce = false
      val instances = new WorkflowInstancePersistence {
        def create(configuration: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
          Consequence.stateConflict("GoalPhase fixture was already created")
        def load(configuration: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
          Consequence.success(stored.filter(_.identity == identity))
        def append(configuration: Configuration, identity: InstanceIdentity,
          expectedRevision: InstanceRevision, entry: HistoryEntry): Consequence[InstanceRecord] =
          stored match {
            case Some(_) if entry.lifecycle == Lifecycle.Completed && committed.isEmpty =>
              Consequence.stateConflict("Step Closed was attempted before the deterministic commit")
            case Some(_) if entry.lifecycle == Lifecycle.Completed && rejectClosedAppendOnce =>
              rejectClosedAppendOnce = false
              Consequence.stateConflict("planned Step Closed append failure")
            case Some(current) if current.identity == identity =>
              current.appendC(expectedRevision, entry).map { next =>
                stored = Some(next)
                next
              }
            case _ => Consequence.stateConflict("GoalPhase fixture WorkflowInstance is unavailable")
          }
      }
      val completion = take(GoalPhaseReviewCompletionOperation.bindC(
        sidecar, () => now, runtime, issuedStore, instances, configuration,
        () => new UnitOfWork(ExecutionContext.create(), EventEngine.noop(DataStore.noop()))
      ))
      val wire = take(WorkflowResultJsonV1.encodeC(
        hostResult, GoalPhaseReviewCompletionOperation.payloadCodec))
      take(WorkflowResultJsonV1.decodeC(
        wire, GoalPhaseReviewCompletionOperation.payloadCodec)) shouldBe hostResult
      val completionRequest = Request.of(
        component = component.name, service = "GoalPhaseService", operation = "submitStepReview",
        arguments = List(Argument("arg1", wire))
      )
      completion.createOperationRequest(completionRequest).isSuccess shouldBe true
      completion.createOperationRequest(completionRequest.copy(operation = "other")) shouldBe
        a[Consequence.Failure[?]]
      completion.createOperationRequest(completionRequest.copy(arguments = List(Argument(
        "arg1", wire.replace("\"decision\":\"accept\"", "\"decision\":\"accept\",\"extra\":1")
      )))) shouldBe a[Consequence.Failure[?]]
      val commitPort = new GoalPhaseStepClosureBoundary.CommitPort {
        def commitC(request: GoalPhaseStepClosureBoundary.CommitRequest,
          snapshot: GoalPhaseStepClosureBoundary.ArtifactSnapshot):
          Consequence[GoalPhaseStepClosureBoundary.CommitReceipt] =
          committed.indexWhere(_.idempotencyKey == request.idempotencyKey) match {
            case index if index >= 0 &&
                (committed(index) != request || committedSnapshots(index) != snapshot) =>
              Consequence.stateConflict("Step commit idempotency key was reused for different artifacts")
            case index if index >= 0 => Consequence.success(receiptFor(request))
            case _ =>
              committed :+= request
              committedSnapshots :+= snapshot
              Consequence.success(receiptFor(request))
          }

        private def receiptFor(request: GoalPhaseStepClosureBoundary.CommitRequest) =
          GoalPhaseStepClosureBoundary.CommitReceipt(
            "c" * 40, request.idempotencyKey, request.stepIdentity, request.candidateRevision,
            request.programDigest, request.planningDigest, request.reviewEvidenceIdentity,
            request.fromState, request.event, request.toState
          )
      }
      GoalPhaseStepClosureBoundary.closeReviewedC(
        sidecar, issued, submitted, now, instances, configuration, snapshotPort, commitPort
      ) shouldBe a[Consequence.Failure[?]]
      committed shouldBe empty
      val service = spec.ServiceDefinition(
        name = "GoalPhaseService",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(completion))
      )
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
      val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
        name = "sm-workflow-review-completion",
        version = None,
        configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
        aliasResolver = AliasResolver.empty,
        runMode = RunMode.Command
      ))
      try {
        val appComponent = new Component() {}
        appComponent.initialize(ComponentInit(
          subsystem,
          Component.Core.create(component.name, component, ComponentInstanceId.default(component), protocol),
          ComponentOrigin.Builtin
        ))
        subsystem.add(appComponent)
        val response = take(subsystem.executeOperationResponse(completionRequest))
        val waitWire = response match {
          case OperationResponse.Scalar(value: String) => value
          case other => fail(s"Expected public WAIT response, found $other")
        }
        val wait = take(WorkflowWaitJsonV1.decodeC(waitWire))
        wait.handle shouldBe submitted.handle
        wait.current shouldBe a[WorkflowContinuation.Wait]
        stored.map(_.lifecycle) shouldBe Some(Lifecycle.Active)
        stored.flatMap(_.suspension) shouldBe None
        stored.map(_.revision) shouldBe Some(InstanceRevision(3L))
        subsystem.executeOperationResponse(completionRequest) shouldBe a[Consequence.Failure[?]]
      } finally subsystem.shutdown()
      GoalPhaseStepClosureBoundary.closeReviewedC(
        sidecar, issued, repair, now, instances, configuration, snapshotPort, commitPort
      ) shouldBe a[Consequence.Failure[?]]
      committed shouldBe empty
      currentSnapshot = currentSnapshot.copy(program = "changed program\n".getBytes(StandardCharsets.UTF_8).toVector)
      GoalPhaseStepClosureBoundary.closeReviewedC(
        sidecar, issued, submitted, now, instances, configuration, snapshotPort, commitPort
      ) shouldBe a[Consequence.Failure[?]]
      committed shouldBe empty
      currentSnapshot = GoalPhaseStepClosureBoundary.ArtifactSnapshot(programContent, planningContent)
      currentSnapshot = currentSnapshot.copy(planning = "changed planning\n".getBytes(StandardCharsets.UTF_8).toVector)
      GoalPhaseStepClosureBoundary.closeReviewedC(
        sidecar, issued, submitted, now, instances, configuration, snapshotPort, commitPort
      ) shouldBe a[Consequence.Failure[?]]
      committed shouldBe empty
      currentSnapshot = GoalPhaseStepClosureBoundary.ArtifactSnapshot(programContent, planningContent)
      val mismatchedReceiptPort = new GoalPhaseStepClosureBoundary.CommitPort {
        def commitC(request: GoalPhaseStepClosureBoundary.CommitRequest,
          snapshot: GoalPhaseStepClosureBoundary.ArtifactSnapshot):
          Consequence[GoalPhaseStepClosureBoundary.CommitReceipt] =
          Consequence.success(GoalPhaseStepClosureBoundary.CommitReceipt(
            "d" * 40, request.idempotencyKey, request.stepIdentity, request.candidateRevision,
            request.programDigest, "d" * 64, request.reviewEvidenceIdentity,
            request.fromState, request.event, request.toState
          ))
      }
      GoalPhaseStepClosureBoundary.closeReviewedC(
        sidecar, issued, submitted, now, instances, configuration, snapshotPort, mismatchedReceiptPort
      ) shouldBe a[Consequence.Failure[?]]
      val mismatchedKeyPort = new GoalPhaseStepClosureBoundary.CommitPort {
        def commitC(request: GoalPhaseStepClosureBoundary.CommitRequest,
          snapshot: GoalPhaseStepClosureBoundary.ArtifactSnapshot):
          Consequence[GoalPhaseStepClosureBoundary.CommitReceipt] =
          Consequence.success(GoalPhaseStepClosureBoundary.CommitReceipt(
            "d" * 40, "f" * 64, request.stepIdentity, request.candidateRevision,
            request.programDigest, request.planningDigest, request.reviewEvidenceIdentity,
            request.fromState, request.event, request.toState
          ))
      }
      GoalPhaseStepClosureBoundary.closeReviewedC(
        sidecar, issued, submitted, now, instances, configuration, snapshotPort, mismatchedKeyPort
      ) shouldBe a[Consequence.Failure[?]]
      stored.map(_.revision) shouldBe Some(InstanceRevision(3L))
      rejectClosedAppendOnce = true
      GoalPhaseStepClosureBoundary.closeReviewedC(
        sidecar, issued, submitted, now, instances, configuration, snapshotPort, commitPort
      ) shouldBe a[Consequence.Failure[?]]
      committed should have size 1
      stored.map(_.revision) shouldBe Some(InstanceRevision(3L))
      When("the applicable full-Step review closes after clock passage and the interrupted append is retried")
      val closed = take(GoalPhaseStepClosureBoundary.closeReviewedC(
        sidecar, issued, submitted, Instant.parse("2026-09-26T02:00:00Z"),
        instances, configuration, snapshotPort, commitPort
      ))
      Then("clock passage does not withdraw the review and the same actual commit closes the Step once")
      committed should have size 1
      committedSnapshots shouldBe Vector(GoalPhaseStepClosureBoundary.ArtifactSnapshot(programContent, planningContent))
      committed.head.idempotencyKey should fullyMatch regex "[0-9a-f]{64}"
      closed.receipt.idempotencyKey shouldBe committed.head.idempotencyKey
      take(commitPort.commitC(committed.head, committedSnapshots.head)) shouldBe closed.receipt
      commitPort.commitC(committed.head.copy(programDigest = "d" * 64), committedSnapshots.head) shouldBe
        a[Consequence.Failure[?]]
      commitPort.commitC(committed.head, committedSnapshots.head.copy(program = Vector.empty)) shouldBe
        a[Consequence.Failure[?]]
      committed should have size 1
      committed.head.programDigest shouldBe request.programDigest
      committed.head.planningDigest shouldBe request.planningDigest
      committed.head.candidateRevision shouldBe candidateRevision
      (committed.head.fromState, committed.head.event, committed.head.toState) shouldBe
        (("Awaiting", "admitted", "Closed"))
      closed.receipt.reviewEvidenceIdentity shouldBe reviewEvidence.evidenceIdentity
      closed.instance.lifecycle shouldBe Lifecycle.Completed
      closed.instance.revision shouldBe InstanceRevision(4L)
      closed.instance.suspension shouldBe None
      stored shouldBe Some(closed.instance)
      val terminalHandle = take(WorkflowHandle.fromRecordC(component, closed.instance))
      take(projectTerminalC(terminalHandle, closed.instance,
        TypedValue("StepCloseResult", closed.receipt.commitIdentity),
        MinimalPresentation("Step Closed", "Committed reviewed Step"))).current shouldBe
        a[WorkflowContinuation.Terminal[?]]
      GoalPhaseStepClosureBoundary.closeReviewedC(
        sidecar, issued, submitted, now, instances, configuration, snapshotPort, commitPort
      ) shouldBe a[Consequence.Failure[?]]
      committed should have size 1
      GoalPhaseReviewResumeBoundary.resumeC(
        sidecar, submitted, now, component, issuedStore, runtime, instances, configuration,
        () => new UnitOfWork(ExecutionContext.create(), EventEngine.noop(DataStore.noop()))
      ) shouldBe a[Consequence.Failure[?]]
    }

    "prepare no review continuation when full-Step evidence is current" in {
      val evidence = StepReviewEvidence("review-1", candidateRevision,
        "2026-09-25T23:59:00Z", "full-step", "reviewed", "receipt")
      take(GoalPhaseReviewBoundary.planC(
        request.copy(reviewEvidence = Vector(evidence)), now, active, provided, reviewOperation)) shouldBe None
    }

    "close a pre-reviewed Step without issuing a review WorkOrder" in {
      val evidence = StepReviewEvidence("review-1", candidateRevision,
        "2026-09-25T23:59:00Z", "full-step", "reviewed", "receipt")
      val reviewedRequest = request.copy(reviewEvidence = Vector(evidence))
      val component = SmWorkflowComponent.componentId
      val handle = take(WorkflowHandle.fromRecordC(component, active))
      val configuration = Configuration(
        NamedWorkflowStore("workflow-primary", WorkflowStoreIdentity("workflow-store")),
        MigrationPolicy("fixture-v1"), RetentionPolicy("fixture-v1"), LeasePolicy("fixture-v1"),
        DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store"))
      )
      var stored = active
      var committed: Option[GoalPhaseStepClosureBoundary.CommitRequest] = None
      var commitCount = 0
      val instances = new WorkflowInstancePersistence {
        def create(config: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
          Consequence.stateConflict("pre-reviewed fixture already exists")
        def load(config: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
          Consequence.success(Option(stored).filter(_.identity == identity))
        def append(config: Configuration, identity: InstanceIdentity,
          expectedRevision: InstanceRevision, entry: HistoryEntry): Consequence[InstanceRecord] =
          if (stored.identity != identity ||
              (entry.lifecycle == Lifecycle.Completed && committed.isEmpty))
            Consequence.stateConflict("pre-reviewed fixture append preceded commit")
          else stored.appendC(expectedRevision, entry).map { next => stored = next; next }
      }
      val snapshot = GoalPhaseStepClosureBoundary.ArtifactSnapshot(programContent, planningContent)
      val snapshotPort = new GoalPhaseStepClosureBoundary.SnapshotPort {
        def loadC(stepIdentity: String): Consequence[GoalPhaseStepClosureBoundary.ArtifactSnapshot] =
          if (stepIdentity == request.stepIdentity) Consequence.success(snapshot)
          else Consequence.stateConflict("unknown pre-reviewed Step snapshot")
      }
      val commitPort = new GoalPhaseStepClosureBoundary.CommitPort {
        def commitC(selected: GoalPhaseStepClosureBoundary.CommitRequest,
          content: GoalPhaseStepClosureBoundary.ArtifactSnapshot):
          Consequence[GoalPhaseStepClosureBoundary.CommitReceipt] =
          if (content != snapshot || committed.exists(_ != selected))
            Consequence.stateConflict("pre-reviewed commit differs from the candidate")
          else {
            if (committed.isEmpty) { committed = Some(selected); commitCount += 1 }
            Consequence.success(GoalPhaseStepClosureBoundary.CommitReceipt(
              "a" * 40, selected.idempotencyKey, selected.stepIdentity,
              selected.candidateRevision, selected.programDigest, selected.planningDigest,
              selected.reviewEvidenceIdentity, selected.fromState, selected.event, selected.toState
            ))
          }
      }
      val plan = take(GoalPhaseStepClosureBoundary.planPreReviewedC(
        reviewedRequest, reviewOperation, now, handle, active))
      plan.commitRequest.reviewEvidenceIdentity shouldBe evidence.evidenceIdentity
      GoalPhaseStepClosureBoundary.planPreReviewedC(
        reviewedRequest.copy(reviewEvidence = Vector(evidence.copy(scope = "focused:docs"))),
        reviewOperation, now, handle, active) shouldBe a[Consequence.Failure[?]]
      GoalPhaseStepClosureBoundary.planPreReviewedC(
        reviewedRequest, reviewOperation, now,
        handle.copy(instanceIdentity = InstanceIdentity("another-instance")), active) shouldBe
        a[Consequence.Failure[?]]
      val closed = take(GoalPhaseStepClosureBoundary.closePreReviewedC(
        reviewedRequest, reviewOperation, now, handle, instances, configuration,
        snapshotPort, commitPort))
      commitCount shouldBe 1
      closed.instance.lifecycle shouldBe Lifecycle.Completed
      closed.instance.revision shouldBe InstanceRevision(2L)
      closed.instance.suspension shouldBe None
      closed.receipt.reviewEvidenceIdentity shouldBe evidence.evidenceIdentity
      GoalPhaseStepClosureBoundary.closePreReviewedC(
        reviewedRequest, reviewOperation, now, handle, instances, configuration,
        snapshotPort, commitPort) shouldBe a[Consequence.Failure[?]]
      commitCount shouldBe 1

      stored = active
      committed = None
      commitCount = 0
      val runtime = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory)
      val issuedStore = new IssuedWorkOrderPersistence.InMemory[StepCloseRequest]
      val requirement = ExecutionRequirement(
        Vector(CapabilityRequirement("review-step-capability")), RiskLevel("standard"),
        ReasoningLevel.Deep, true)
      val presentation = MinimalPresentation("Review Step closure", "Awaiting full-Step review")
      val invocationPort = new GoalPhaseStepCloseServiceOperation.InvocationPort {
        def resolveC(submissionIdentity: String): Consequence[WorkflowHandle] =
          if (submissionIdentity == request.submissionIdentity) Consequence.success(handle)
          else Consequence.stateConflict("unknown host Step-close invocation")
      }
      val operation = take(GoalPhaseStepCloseServiceOperation.bindC(
        () => now, invocationPort, reviewOperation, instances, configuration, runtime, issuedStore,
        requirement, presentation, snapshotPort, commitPort,
        () => new UnitOfWork(ExecutionContext.create(), EventEngine.noop(DataStore.noop()))))
      val wire = GoalPhaseStepCloseJson.encode(reviewedRequest)
      take(GoalPhaseStepCloseJson.decodeC(wire)) shouldBe reviewedRequest
      val publicRequest = Request.of(component = component.name, service = "GoalPhaseService",
        operation = "requestStepClose", arguments = List(Argument("arg1", wire)))
      operation.createOperationRequest(publicRequest).isSuccess shouldBe true
      operation.createOperationRequest(publicRequest.copy(arguments = List(Argument("arg1",
        wire.replace("\"stepIdentity\":\"step-1\"",
          "\"stepIdentity\":\"step-1\",\"extra\":1"))))) shouldBe a[Consequence.Failure[?]]
      val service = spec.ServiceDefinition("GoalPhaseService",
        spec.OperationDefinitionGroup(NonEmptyVector.of(operation)))
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
      val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
        name = "sm-workflow-step-close", version = None,
        configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
        aliasResolver = AliasResolver.empty, runMode = RunMode.Command
      ))
      try {
        val appComponent = new Component() {}
        appComponent.initialize(ComponentInit(subsystem,
          Component.Core.create(component.name, component, ComponentInstanceId.default(component), protocol),
          ComponentOrigin.Builtin))
        subsystem.add(appComponent)
        val terminalWire = take(subsystem.executeOperationResponse(publicRequest)) match {
          case OperationResponse.Scalar(value: String) => value
          case other => fail(s"Expected public TERMINAL response, found $other")
        }
        val terminal = take(WorkflowTerminalJsonV1.decodeC(
          terminalWire, GoalPhaseStepCloseServiceOperation.terminalCodec))
        terminal.current shouldBe a[WorkflowContinuation.Terminal[?]]
        stored.lifecycle shouldBe Lifecycle.Completed
        commitCount shouldBe 1

        stored = active
        committed = None
        commitCount = 0
        val reviewWire = GoalPhaseStepCloseJson.encode(request)
        val cli = new CncfRuntime()
        val selector = s"${component.name}/GoalPhaseService/requestStepClose"
        val workWire = take(cli.executeCommandResponse(subsystem,
          Array(selector, "--json", reviewWire))).print
        val work = take(WorkflowWorkOrderJsonV1.decodeC(
          workWire, GoalPhaseStepCloseJson.payloadCodec))
        work.current shouldBe a[WorkflowContinuation.WorkOrder[?]]
        stored.lifecycle shouldBe Lifecycle.Active
        stored.revision shouldBe InstanceRevision(2L)
        stored.suspension should not be empty
        commitCount shouldBe 0
      } finally subsystem.shutdown()
    }

    "close an accepted review through its public completion Operation without a second close request" in {
      var directResponses = Map.empty[String, String]
      for (decision <- Vector("accept", "repair"); viaCli <- Vector(false, true)) {
        val pending = take(GoalPhaseReviewBoundary.planC(
          request, now, active, provided, reviewOperation)).getOrElse(fail("review is missing"))
        val component = SmWorkflowComponent.componentId
        val configuration = Configuration(
          NamedWorkflowStore("workflow-primary", WorkflowStoreIdentity("workflow-store")),
          MigrationPolicy("fixture-v1"), RetentionPolicy("fixture-v1"), LeasePolicy("fixture-v1"),
          DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store"))
        )
        var stored = active
        var commits = 0
        val instances = new WorkflowInstancePersistence {
          def create(config: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
            Consequence.stateConflict("auto-close fixture already exists")
          def load(config: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
            Consequence.success(Option(stored).filter(_.identity == identity))
          def append(config: Configuration, identity: InstanceIdentity,
            expectedRevision: InstanceRevision, entry: HistoryEntry): Consequence[InstanceRecord] =
            if (stored.identity != identity ||
                (entry.lifecycle == Lifecycle.Completed && commits == 0))
              Consequence.stateConflict("automatic Step Closed append preceded commit")
            else stored.appendC(expectedRevision, entry).map { next => stored = next; next }
        }
        val runtime = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory)
        val unitOfWork = new UnitOfWork(ExecutionContext.create(), EventEngine.noop(DataStore.noop()))
        take(runtime.stageSuspensionAfterC(unitOfWork, pending.continuation, () =>
          instances.append(configuration, stored.identity, stored.revision,
            take(GoalPhaseReviewBoundary.suspendedRecordC(pending, stored)).history.last).map(_ => ())
        ))
        take(unitOfWork.commit())
        take(runtime.claimC(pending.continuation.continuationId))
        val issuedStore = new IssuedWorkOrderPersistence.InMemory[StepCloseRequest]
        val requirement = ExecutionRequirement(
          Vector(CapabilityRequirement("review-step-capability")), RiskLevel("standard"),
          ReasoningLevel.Deep, true)
        val issued = take(GoalPhaseReviewBoundary.issueRecoveredWorkOrderC(
          pending, stored, component, runtime, issuedStore, requirement,
          MinimalPresentation("Review Step closure", "Awaiting full-Step review")))
        val work = issued.current match {
          case value: WorkflowContinuation.WorkOrder[?] => value
          case other => fail(s"Expected review WorkOrder, found $other")
        }
        work.request.completionOperation shouldBe
          StateMachineOperationIdentity("GoalPhaseService", "submitStepReview")
        val evidence = StepReviewEvidence("review-auto-1", candidateRevision,
          "2026-09-25T23:59:00Z",
          if (decision == "accept") "full-step" else "focused:docs", "reviewed", "receipt")
        val submitted = ContinuationResult(
          issued.handle, work.request.runId, work.request.continuationId,
          work.request.expectedRevision, work.request.context.snapshot,
          TypedValue("ReviewStepResult", ReviewStepResult(decision, "review result", Vector(evidence))),
          ContextReference("review-auto-1", candidateRevision), Vector.empty,
          ExecutionEvidence(Vector.empty)
        )
        val snapshot = GoalPhaseStepClosureBoundary.ArtifactSnapshot(programContent, planningContent)
        val snapshotPort = new GoalPhaseStepClosureBoundary.SnapshotPort {
          def loadC(stepIdentity: String): Consequence[GoalPhaseStepClosureBoundary.ArtifactSnapshot] =
            if (stepIdentity == request.stepIdentity) Consequence.success(snapshot)
            else Consequence.stateConflict("unknown auto-close Step")
        }
        val commitPort = new GoalPhaseStepClosureBoundary.CommitPort {
          def commitC(selected: GoalPhaseStepClosureBoundary.CommitRequest,
            content: GoalPhaseStepClosureBoundary.ArtifactSnapshot):
            Consequence[GoalPhaseStepClosureBoundary.CommitReceipt] =
            if (content != snapshot || commits != 0)
              Consequence.stateConflict("automatic Step commit was duplicated or changed")
            else {
              commits += 1
              Consequence.success(GoalPhaseStepClosureBoundary.CommitReceipt(
                "e" * 40, selected.idempotencyKey, selected.stepIdentity,
                selected.candidateRevision, selected.programDigest, selected.planningDigest,
                selected.reviewEvidenceIdentity, selected.fromState, selected.event, selected.toState))
            }
        }
        val completion = take(GoalPhaseReviewCompletionOperation.bindAutoCloseC(
          sidecar, () => now, runtime, issuedStore, instances, configuration,
          () => new UnitOfWork(ExecutionContext.create(), EventEngine.noop(DataStore.noop())),
          GoalPhaseReviewCompletionOperation.AutoClosePorts(snapshotPort, commitPort)))
        val wire = take(WorkflowResultJsonV1.encodeC(
          submitted, GoalPhaseReviewCompletionOperation.payloadCodec))
        val completionRequest = Request.of(component = component.name, service = "GoalPhaseService",
          operation = "submitStepReview", arguments = List(Argument("arg1", wire)))
        val service = spec.ServiceDefinition("GoalPhaseService",
          spec.OperationDefinitionGroup(NonEmptyVector.of(completion)))
        val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
        val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
          name = "sm-workflow-auto-close", version = None,
          configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
          aliasResolver = AliasResolver.empty, runMode = RunMode.Command
        ))
        try {
          val appComponent = new Component() {}
          appComponent.initialize(ComponentInit(subsystem,
            Component.Core.create(component.name, component, ComponentInstanceId.default(component), protocol),
            ComponentOrigin.Builtin))
          subsystem.add(appComponent)
          val cli = new CncfRuntime()
          val selector = s"${component.name}/GoalPhaseService/submitStepReview"
          val response = if (viaCli) {
            take(cli.executeCommandResponse(subsystem,
              Array(selector, "--json", wire))).print
          } else take(subsystem.executeOperationResponse(completionRequest)) match {
            case OperationResponse.Scalar(value: String) => value
            case other => fail(s"Expected typed completion response, found $other")
          }
          if (viaCli) response shouldBe directResponses(decision)
          else directResponses = directResponses.updated(decision, response)
          if (decision == "accept") {
            take(WorkflowTerminalJsonV1.decodeC(
              response, GoalPhaseStepCloseServiceOperation.terminalCodec)).current shouldBe
              a[WorkflowContinuation.Terminal[?]]
            stored.lifecycle shouldBe Lifecycle.Completed
            stored.revision shouldBe InstanceRevision(4L)
            commits shouldBe 1
          } else {
            take(WorkflowWaitJsonV1.decodeC(response)).current shouldBe
              a[WorkflowContinuation.Wait]
            stored.lifecycle shouldBe Lifecycle.Active
            stored.revision shouldBe InstanceRevision(3L)
            commits shouldBe 0
          }
          if (viaCli)
            cli.executeCommandResponse(subsystem,
              Array(selector, "--json", wire)) shouldBe a[Consequence.Failure[?]]
          else subsystem.executeOperationResponse(completionRequest) shouldBe
            a[Consequence.Failure[?]]
        } finally subsystem.shutdown()
      }
    }

    "preserve a successful scoped review while publishing and completing a full-review WorkOrder" in {
      val focused = StepReviewEvidence("scoped-review-1", candidateRevision,
        "2026-09-25T23:59:00Z",
        "focused:src/main/scala/Step.scala", "scoped review passed", "scoped-receipt")
      val input = request.copy(reviewEvidence = Vector(focused))
      exerciseFocusedReview(input, viaCli = true) shouldBe
        exerciseFocusedReview(input, viaCli = false)
    }

    "retain wrong-target and focused evidence without using it to bypass full-review completion" in {
      val focused = StepReviewEvidence("scoped-review-1", candidateRevision,
        "2026-09-25T23:59:00Z",
        "focused:docs", "scoped review passed", "scoped-receipt")
      val previousFull = focused.copy(evidenceIdentity = "previous-full-review",
        reviewedRevision = "f" * 64, scope = "full-step")
      val inputs = Vector(request.copy(reviewEvidence = Vector(focused, previousFull)),
        request.copy(reviewEvidence = Vector(focused)))
      inputs.foreach { input =>
        exerciseFocusedReview(input, viaCli = true) shouldBe
          exerciseFocusedReview(input, viaCli = false)
      }
    }

    "reject mismatched Provided Operation and candidate context" in {
      GoalPhaseReviewBoundary.planC(request, now, active,
        provided.copy(operation = StateMachineOperationIdentity("OtherService", "requestStepClose")),
        reviewOperation) shouldBe a[Consequence.Failure[?]]
      GoalPhaseReviewBoundary.planC(request, now, active,
        provided.copy(input = Some(StateMachineOperationInput(
          StateMachineInputTypeReference("StepCloseRequest"), ContextReference("step-close-input", "stale")))),
        reviewOperation) shouldBe a[Consequence.Failure[?]]
    }

    "leave the Continuation unclaimable when post-commit instance persistence fails" in {
      val pending = take(GoalPhaseReviewBoundary.planC(
        request, now, active, provided, reviewOperation)).getOrElse(fail("review is missing"))
      val runtime = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory)
      val unitOfWork = new UnitOfWork(ExecutionContext.create(), EventEngine.noop(DataStore.noop()))
      runtime.stageSuspensionAfterC(unitOfWork, pending.continuation,
        () => Consequence.stateConflict("planned WorkflowInstance persistence failure")).isSuccess shouldBe true
      unitOfWork.commit() shouldBe a[Consequence.Failure[?]]
      runtime.claimC(pending.continuation.continuationId) shouldBe a[Consequence.Failure[?]]
    }
  }

  /** Application fixture only: both Operations use real issued-work admission.
    * The initial scoped evidence is never promoted to a full review or dropped.
    */
  private def exerciseFocusedReview(input: StepCloseRequest, viaCli: Boolean): String = {
    Given("a current Step candidate whose focused or crossed evidence does not cover its full review")
    val assessed = take(GoalPhaseStepCloseRouting.assessC(input, now, active, reviewOperation))
    assessed.admissionSatisfied shouldBe false
    assessed.submission.evidence.map(_.identity) shouldBe input.reviewEvidence.map(_.evidenceIdentity)
    assessed.evaluation.gaps.map(_.requirement.identity) shouldBe
      Vector(StepClosureAdmission.fullReviewRequirement)
    val component = SmWorkflowComponent.componentId
    val handle = take(WorkflowHandle.fromRecordC(component, active))
    val configuration = Configuration(
      NamedWorkflowStore("workflow-primary", WorkflowStoreIdentity("workflow-store")),
      MigrationPolicy("fixture-v1"), RetentionPolicy("fixture-v1"), LeasePolicy("fixture-v1"),
      DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store")))
    var stored = active
    var closeInvocations = 0
    var commits = Vector.empty[GoalPhaseStepClosureBoundary.CommitRequest]
    val instances = new WorkflowInstancePersistence {
      def create(config: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
        Consequence.stateConflict("scoped review fixture already exists")
      def load(config: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
        Consequence.success(Option(stored).filter(_.identity == identity))
      def append(config: Configuration, identity: InstanceIdentity,
        expectedRevision: InstanceRevision, entry: HistoryEntry): Consequence[InstanceRecord] =
        if (stored.identity != identity || (entry.lifecycle == Lifecycle.Completed && commits.isEmpty))
          Consequence.stateConflict("scoped review closure preceded its commit receipt")
        else stored.appendC(expectedRevision, entry).map { next => stored = next; next }
    }
    val snapshot = GoalPhaseStepClosureBoundary.ArtifactSnapshot(programContent, planningContent)
    val snapshotPort = new GoalPhaseStepClosureBoundary.SnapshotPort {
      def loadC(stepIdentity: String): Consequence[GoalPhaseStepClosureBoundary.ArtifactSnapshot] =
        if (stepIdentity == input.stepIdentity) Consequence.success(snapshot)
        else Consequence.stateConflict("unknown scoped-review candidate")
    }
    val commitPort = new GoalPhaseStepClosureBoundary.CommitPort {
      def commitC(selected: GoalPhaseStepClosureBoundary.CommitRequest,
        content: GoalPhaseStepClosureBoundary.ArtifactSnapshot):
        Consequence[GoalPhaseStepClosureBoundary.CommitReceipt] =
        if (content != snapshot || commits.nonEmpty)
          Consequence.stateConflict("scoped review commit changed or was duplicated")
        else {
          commits :+= selected
          Consequence.success(GoalPhaseStepClosureBoundary.CommitReceipt(
            "b" * 40, selected.idempotencyKey, selected.stepIdentity, selected.candidateRevision,
            selected.programDigest, selected.planningDigest, selected.reviewEvidenceIdentity,
            selected.fromState, selected.event, selected.toState))
        }
    }
    val runtime = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory)
    val issuedStore = new IssuedWorkOrderPersistence.InMemory[StepCloseRequest]
    val requirement = ExecutionRequirement(Vector(CapabilityRequirement("review-step-capability")),
      RiskLevel("standard"), ReasoningLevel.Deep, reviewRequired = true)
    val invocationPort = new GoalPhaseStepCloseServiceOperation.InvocationPort {
      def resolveC(submissionIdentity: String): Consequence[WorkflowHandle] =
        if (submissionIdentity == input.submissionIdentity) {
          closeInvocations += 1
          Consequence.success(handle)
        } else Consequence.stateConflict("unknown scoped-review close invocation")
    }
    def freshUnitOfWork(): UnitOfWork =
      new UnitOfWork(ExecutionContext.create(), EventEngine.noop(DataStore.noop()))
    val close = take(GoalPhaseStepCloseServiceOperation.bindC(
      () => now, invocationPort, reviewOperation, instances, configuration, runtime, issuedStore,
      requirement, MinimalPresentation("Full-Step review", "Scoped evidence is insufficient for closure"),
      snapshotPort, commitPort, () => freshUnitOfWork()))
    val completion = take(GoalPhaseReviewCompletionOperation.bindAutoCloseC(
      sidecar, () => now, runtime, issuedStore, instances, configuration, () => freshUnitOfWork(),
      GoalPhaseReviewCompletionOperation.AutoClosePorts(snapshotPort, commitPort)))
    val service = spec.ServiceDefinition("GoalPhaseService",
      spec.OperationDefinitionGroup(NonEmptyVector.of(close, completion)))
    val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
    val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
      name = "sm-workflow-scoped-review", version = None,
      configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
      aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
    try {
      val appComponent = new Component() {}
      appComponent.initialize(ComponentInit(subsystem,
        Component.Core.create(component.name, component, ComponentInstanceId.default(component), protocol),
        ComponentOrigin.Builtin))
      subsystem.add(appComponent)
      val cli = new CncfRuntime()
      def invoke(operation: String, wire: String): Consequence[String] =
        if (viaCli) cli.executeCommandResponse(subsystem,
          Array(s"${component.name}/GoalPhaseService/$operation", "--json", wire)).map(_.print)
        else subsystem.executeOperationResponse(Request.of(component = component.name,
          service = "GoalPhaseService", operation = operation, arguments = List(Argument("arg1", wire))))
          .flatMap {
            case OperationResponse.Scalar(value: String) => Consequence.success(value)
            case _ => Consequence.stateConflict("scoped review fixture returned no common JSON envelope")
          }

      When("the actual close Operation issues the distinct full-Step review")
      val workWire = take(invoke("requestStepClose", GoalPhaseStepCloseJson.encode(input)))
      val issued = take(WorkflowWorkOrderJsonV1.decodeC(workWire, GoalPhaseStepCloseJson.payloadCodec))
      val work = issued.current match {
        case value: WorkflowContinuation.WorkOrder[?] => value
        case other => fail(s"Expected a full-review WorkOrder, found $other")
      }
      Then("the original candidate and actual native review are retained without a commit")
      issued.handle shouldBe handle
      work.requirement shouldBe requirement
      work.request.requiredOperation shouldBe StateMachineRequiredOperationIdentity("review-step-capability")
      work.request.operation shouldBe StateMachineOperationIdentity("StepReviewService", "reviewStep")
      work.request.completionOperation shouldBe StateMachineOperationIdentity("GoalPhaseService", "submitStepReview")
      work.request.input shouldBe Some(TypedValue("StepCloseRequest", input))
      take(issuedStore.loadC(work.request.continuationId)) shouldBe Some(issued)
      closeInvocations shouldBe 1
      commits shouldBe empty
      stored.revision shouldBe InstanceRevision(2L)
      stored.suspension.map(_.continuationIdentity.value) shouldBe Some(work.request.continuationId.value)
      val suspended = stored
      val claim = take(runtime.recoverClaimC(work.request.continuationId))
      Given("full-Step review evidence for the actual candidate and two genuinely incompatible review results")
      val full = StepReviewEvidence("full-review-completion", candidateRevision,
        "2026-09-25T23:59:00Z", "full-step", "full review passed", "full-receipt")
      val submitted = ContinuationResult(issued.handle, work.request.runId, work.request.continuationId,
        work.request.expectedRevision, work.request.context.snapshot,
        TypedValue("ReviewStepResult", ReviewStepResult("accept", "full-Step review passed", Vector(full))),
        ContextReference(full.evidenceIdentity, candidateRevision), Vector.empty, ExecutionEvidence(Vector.empty))
      def resultWire(result: ContinuationResult[ReviewStepResult]): String =
        take(WorkflowResultJsonV1.encodeC(result, GoalPhaseReviewCompletionOperation.payloadCodec))
      val invalid = Vector(
        submitted.copy(result = submitted.result.copy(value = submitted.result.value.copy(
          reviewEvidence = Vector(full.copy(scope = "focused:docs"))))),
        submitted.copy(result = submitted.result.copy(value = submitted.result.value.copy(
          reviewEvidence = Vector(full.copy(reviewedRevision = "f" * 64))))))
      When("the public completion Operation receives focused or wrong-candidate review evidence")
      invalid.foreach { value =>
        val rejected = invoke("submitStepReview", resultWire(value))
        Then("the incompatible review fails without changing the suspension, commit or owning claim")
        rejected shouldBe a[Consequence.Failure[?]]
        stored shouldBe suspended
        commits shouldBe empty
        take(runtime.recoverClaimC(work.request.continuationId)) shouldBe claim
        take(issuedStore.loadC(work.request.continuationId)) shouldBe Some(issued)
      }
      When("the full review carries a workspace diagnostic that is not a native applicability condition")
      val diagnostic = submitted.copy(contextSnapshot = submitted.contextSnapshot.copy(workspaceRevision = Some("historical-workspace-note")))
      val admitted = take(GoalPhaseReviewResultAdapter.assessC(sidecar, issued, diagnostic, now))
      Then("full candidate evidence remains applicable without a diagnostic copy certificate")
      admitted.closureReady shouldBe true
      admitted.evaluation.acceptedEvidence shouldBe Vector(full.evidenceIdentity)
      When("the actual public completion consumes that applicable review")
      val terminalWire = take(invoke("submitStepReview", resultWire(diagnostic)))
      val terminal = take(WorkflowTerminalJsonV1.decodeC(
        terminalWire, GoalPhaseStepCloseServiceOperation.terminalCodec))
      Then("one real review completion and commit close the original Step")
      terminal.handle shouldBe issued.handle
      terminal.current shouldBe a[WorkflowContinuation.Terminal[?]]
      stored.lifecycle shouldBe Lifecycle.Completed
      stored.revision shouldBe InstanceRevision(4L)
      stored.suspension shouldBe None
      commits.size shouldBe 1
      commits.head.reviewEvidenceIdentity shouldBe full.evidenceIdentity
      commits.head.candidateRevision shouldBe candidateRevision
      commits.head.programDigest shouldBe input.programDigest
      commits.head.planningDigest shouldBe input.planningDigest
      closeInvocations shouldBe 1
      take(issuedStore.loadC(work.request.continuationId)) shouldBe Some(issued)
      When("the completed review is submitted again")
      val replay = invoke("submitStepReview", resultWire(diagnostic))
      Then("its consumed state prevents another commit")
      replay shouldBe a[Consequence.Failure[?]]
      commits.size shouldBe 1
      terminalWire
    } finally subsystem.shutdown()
  }
}
