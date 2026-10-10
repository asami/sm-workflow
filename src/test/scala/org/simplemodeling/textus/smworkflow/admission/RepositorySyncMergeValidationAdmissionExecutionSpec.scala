package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.{Component, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventEngine
import org.goldenport.cncf.unitofwork.UnitOfWork
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.{Configuration as GenericConfiguration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncBoundaryAssessmentCommand, RepositorySyncFetchAdmissionCommand, RepositorySyncPreservationAdmissionCommand, RepositorySyncRelationAdmissionCommand, RepositorySyncMergeAdmissionCommand, RepositorySyncMergeValidationAdmissionCommand, RepositorySyncMergeReviewRequest, RepositorySyncMergeReviewResult, RepositorySyncMergeReviewFinding, RepositorySyncStartCommand, RepositorySyncStartInput, RepositorySyncMergedConvergenceCommand}

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncMergeValidationAdmissionExecutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import RepositorySyncMergeValidationAssessment as V
  import RepositorySyncMergeReviewAdmission as R
  import ProfileStartAdmission.*
  import RepositorySyncCheckpointAssessment.*

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"RepositorySync merge progression failed: $failure")
  }

  private val input = RepositorySyncStartInput(
    "repository-1", "main", "origin", "main", "remote-1", "FULL_STATE", "policy-v1")
  private val startCommand = RepositorySyncStartCommand(
    input, "sync-selection-1", "participant-1", "sync-start-1")
  private val boundaryCommand = RepositorySyncBoundaryAssessmentCommand(
    startCommand, "1", "sync-boundary-1")
  private val fetchCommand = RepositorySyncFetchAdmissionCommand(
    boundaryCommand, "2", "sync-fetch-1")
  private val preservationCommand = RepositorySyncPreservationAdmissionCommand(
    fetchCommand, "3", "sync-preservation-1")
  private val relationCommand = RepositorySyncRelationAdmissionCommand(
    preservationCommand, "4", "sync-relation-1")
  private val mergeCommand = RepositorySyncMergeAdmissionCommand(
    relationCommand, "5", "sync-merge-1")
  private val validationCommand = RepositorySyncMergeValidationAdmissionCommand(mergeCommand, "6", "sync-validation-1")
  private val human = Selection("sync-selection-1",
    StateMachineOperationIdentity("RepositorySyncService", "startRepositorySync"),
    WorkflowDefinitionIdentity("RepositorySyncWorkflow"),
    WorkflowDefinitionRevision("repository-sync-native-convergence-v10"),
    "participant-1", SelectionOrigin.ExplicitHuman, "sync-start-1", None)
  private val definition = take(RepositorySyncDefinitionBinding.bindC)
  private val configuration = Configuration(
    NamedWorkflowStore("workflow-primary", WorkflowStoreIdentity("workflow-store")),
    MigrationPolicy("fixture-v1"), RetentionPolicy("fixture-v1"), LeasePolicy("fixture-v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store")))

  private final class Fixture(dirty: Boolean = false, staged: Boolean = false, equal: Boolean = false, remoteAhead: Boolean = false, diverged: Boolean = true) {
    val paths = if (dirty) Vector("deleted.txt", "new.txt", "src/main.scala") else Vector.empty
    val snapshot = RepositorySyncBoundaryAssessment.Snapshot(
      "snapshot-1", "repository-1", "worktree-1", "main", "origin", "main", "remote-1",
      "policy-v1", "a" * 40, "b" * 64, "c" * 64, "d" * 64, paths,
      if (staged) "COMPLETE_FULLY_STAGED" else "EMPTY",
      detachedHead = false, pendingMerge = false)
    val selections = new SelectionPort {
      def loadC(selectionId: String) =
        Consequence.success(Option(human).filter(_.selectionId == selectionId))
    }
    val mappings = new RepositorySyncStartExecution.TrackingMappingPort {
      def loadC(repositoryIdentity: String) =
        Consequence.success(Option(input).filter(_.repositoryIdentity == repositoryIdentity))
    }
    val snapshots = new RepositorySyncBoundaryAssessment.Port {
      def captureC(repositoryIdentity: String) =
        Consequence.success(Option(snapshot).filter(_.repositoryIdentity == repositoryIdentity))
    }
    val admittedBoundary = take(RepositorySyncBoundaryAssessment.assessC(input, snapshots))
    val scopeEvidence = RepositorySyncDirtyScopeAssessment.Evidence(
      admittedBoundary.digest, "policy-v1", if (staged) paths else Vector.empty,
      paths, Vector.empty, "e" * 64)
    val scopes = new RepositorySyncDirtyScopeAssessment.Port {
      def loadC(repositoryIdentity: String, digest: String) =
        Consequence.success(Some(scopeEvidence))
    }
    val admittedScope = take(RepositorySyncDirtyScopeAssessment.assessC(admittedBoundary, scopes))
    val fetchedTip = if (equal) snapshot.headOid else "f" * 40
    val fetchReceipt = RepositorySyncFetchAssessment.Receipt(
      admittedBoundary.digest, admittedScope.digest, "repository-1", "worktree-1", "main",
      "origin", "main", "remote-1", fetchedTip, Some("9" * 40), fetchedTip,
      snapshot.headOid, snapshot.headOid, snapshot.treeDigest, snapshot.indexDigest,
      snapshot.worktreeDigest, "refs/remotes/origin/main", Vector("refs/remotes/origin/main"),
      endpointPolicyAccepted = true, fetchCompleted = true, executionDigest = "1" * 64)
    var fetchReceiptValue = fetchReceipt
    val fetches = new RepositorySyncFetchAssessment.Port {
      def fetchC(repositoryIdentity: String, boundaryDigest: String, dirtyScopeDigest: String) =
        Consequence.success(Some(fetchReceiptValue))
    }
    val admittedFetch = take(RepositorySyncFetchAssessment.assessC(admittedBoundary, admittedScope, fetches))
    val frozen = FrozenState(admittedBoundary.digest, admittedScope.digest, admittedFetch.digest,
      "repository-1", "worktree-1", "main", "policy-v1", snapshot.headOid,
      snapshot.treeDigest, snapshot.indexDigest, snapshot.worktreeDigest, paths,
      "3" * 40, "4" * 64, "5" * 64)
    var receiptTransform: Receipt => Receipt = identity
    var cleanTransform: CleanState => CleanState = identity
    var cleanMissing = false
    var preservationLoads = 0
    var checkpointLoads = 0
    val preservations = new Port {
      def loadFrozenC(repositoryIdentity: String, boundaryDigest: String,
        dirtyScopeDigest: String, fetchDigest: String) = {
        preservationLoads += 1
        Consequence.success(Some(frozen))
      }
      def loadCheckpointC(repositoryIdentity: String, frozenStateDigest: String) = {
        preservationLoads += 1
        checkpointLoads += 1
        Consequence.success(Some(receiptTransform(Receipt(frozenStateDigest, "2" * 40,
          Vector(snapshot.headOid), frozen.completeTreeOid, frozen.completeTreeDigest,
          checkpointPurpose, completed = true, executionDigest = "6" * 64))))
      }
      def loadCleanStateC(repositoryIdentity: String, preservationDigest: String) = {
        preservationLoads += 1
        Consequence.success(if (cleanMissing) None else Some(cleanTransform(CleanState(
          preservationDigest, "repository-1", "worktree-1", "main", "origin", "main",
          "remote-1", "policy-v1", if (dirty) "2" * 40 else snapshot.headOid,
          if (dirty) frozen.completeTreeOid else "7" * 40,
          if (dirty) frozen.completeTreeDigest else snapshot.treeDigest,
          fetchReceipt.fetchedTipOid, Vector.empty, "EMPTY", detachedHead = false,
          pendingMerge = false, inspectionDigest = "8" * 64))))
      }
    }
    var relationTransform: RepositorySyncRelationAssessment.Receipt => RepositorySyncRelationAssessment.Receipt = identity
    var relationMissing = false
    var relationFailure = false
    var relationLoads = 0
    val relations = new RepositorySyncRelationAssessment.Port {
      def loadC(repositoryIdentity: String, preservationDigest: String,
        localTipOid: String, fetchedTipOid: String): Consequence[Option[RepositorySyncRelationAssessment.Receipt]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        localTipOid shouldBe (if (dirty) "2" * 40 else snapshot.headOid)
        fetchedTipOid shouldBe fetchedTip
        relationLoads += 1
        if (relationFailure) Consequence.stateConflict("host graph receipt load failed")
        else Consequence.success(if (relationMissing) None else Some(relationTransform(
          RepositorySyncRelationAssessment.Receipt(preservationDigest,
            "repository-1", "worktree-1", "main", "origin", "main", "remote-1", "policy-v1",
            localTipOid, fetchedTipOid, Vector(if (equal || remoteAhead) localTipOid else if (diverged) "0" * 40 else fetchedTipOid),
            if (equal || remoteAhead) 0L else 3L, if (equal) 0L else if (remoteAhead || diverged) 2L else 0L,
            historyComplete = true, replacementHistory = false, inspectionDigest = "9" * 64))))
      }
    }
    var lastPending: RepositorySyncMergeAssessment.PendingState = null
    var frozenTransform: RepositorySyncMergeAssessment.FrozenState => RepositorySyncMergeAssessment.FrozenState = identity
    var mergeReceiptTransform: RepositorySyncMergeAssessment.Receipt => RepositorySyncMergeAssessment.Receipt = identity
    var pendingTransform: RepositorySyncMergeAssessment.PendingState => RepositorySyncMergeAssessment.PendingState = identity
    var structuralTransform: RepositorySyncMergeAssessment.StructuralEvidence => RepositorySyncMergeAssessment.StructuralEvidence = identity
    var missingStage = ""
    var failureStage = ""
    var mergeLoads = 0
    private def mergeState(binding: String) = RepositorySyncFastForwardAssessment.State(
      binding, "repository-1", "worktree-1", "main", "origin", "main", "remote-1", "policy-v1",
      if (dirty) "2" * 40 else snapshot.headOid, if (dirty) frozen.completeTreeOid else "7" * 40,
      if (dirty) frozen.completeTreeDigest else snapshot.treeDigest, fetchedTip, Vector.empty, "EMPTY",
      detachedHead = false, pendingOperations = Vector.empty, inspectionDigest = "b" * 64)
    private val mergePaths = Vector("README.md", "src/main.scala")
    private def mergeLoaded[A](stage: String, value: => A): Consequence[Option[A]] = {
      mergeLoads += 1
      if (failureStage == stage) Consequence.stateConflict("host merge evidence load failed")
      else Consequence.success(if (missingStage == stage) None else Some(value))
    }
    val merges = new RepositorySyncMergeAssessment.Port {
      def loadFrozenC(repositoryIdentity: String, relationDigest: String,
        localTipOid: String, fetchedTipOid: String): Consequence[Option[RepositorySyncMergeAssessment.FrozenState]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        localTipOid shouldBe (if (dirty) "2" * 40 else snapshot.headOid)
        fetchedTipOid shouldBe fetchedTip
        mergeLoaded("freeze", frozenTransform(RepositorySyncMergeAssessment.FrozenState(
          mergeState(relationDigest), Vector(localTipOid, fetchedTipOid), "0" * 40, "c" * 40, "d" * 64)))
      }
      def loadReceiptC(repositoryIdentity: String,
        frozenStateDigest: String): Consequence[Option[RepositorySyncMergeAssessment.Receipt]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        val localTip = if (dirty) "2" * 40 else snapshot.headOid
        mergeLoaded("receipt", mergeReceiptTransform(RepositorySyncMergeAssessment.Receipt(
          frozenStateDigest, "refs/heads/main", localTip, localTip, Vector(fetchedTip), "e" * 40,
          "f" * 64, mergePaths, Vector.empty, noCommit = true, noFastForward = true,
          completed = true, executionDigest = "1" * 64)))
      }
      def loadPendingStateC(repositoryIdentity: String,
        receiptDigest: String): Consequence[Option[RepositorySyncMergeAssessment.PendingState]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        mergeLoaded("pending", pendingTransform(RepositorySyncMergeAssessment.PendingState(
          mergeState(receiptDigest).copy(dirtyPaths = mergePaths,
            indexShape = RepositorySyncMergeAssessment.pendingIndexShape,
            pendingOperations = Vector("MERGE"), inspectionDigest = "2" * 64),
          Vector(fetchedTip), "e" * 40, "f" * 64, Vector.empty))).map { loaded =>
          loaded.foreach(value => lastPending = value)
          loaded
        }
      }
      def loadStructuralC(repositoryIdentity: String,
        pendingStateDigest: String): Consequence[Option[RepositorySyncMergeAssessment.StructuralEvidence]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        mergeLoaded("structural", structuralTransform(RepositorySyncMergeAssessment.StructuralEvidence(
          pendingStateDigest, Vector(if (dirty) "2" * 40 else snapshot.headOid, fetchedTip),
          "e" * 40, "f" * 64, Vector.empty, Vector.empty, Vector.empty, Vector.empty,
          scanCompleted = true, inspectionDigest = "3" * 64)))
      }
    }

    var kinds = Vector(V.ChangeKind.Documentation, V.ChangeKind.LocalSource)
    var expectedChecks = Vector("module-check")
    var deltaTransform: V.Delta => V.Delta = identity
    var policyTransform: V.Policy => V.Policy = identity
    var validationTransform: V.ValidationEvidence => V.ValidationEvidence = identity
    var validationMissing = ""
    var validationNull = ""
    var validationFailure = ""
    var validationLoads = 0
    private def validationLoaded[A](stage: String, value: => A): Consequence[Option[A]] = {
      validationLoads += 1
      if (validationFailure == stage) Consequence.stateConflict("fixture validation evidence unavailable")
      else Consequence.success(if (validationMissing == stage) None
        else if (validationNull == stage) Some(null.asInstanceOf[A]) else Some(value))
    }
    val validations = new V.Port {
      def loadDeltaC(repositoryIdentity: String, mergeDigest: String,
        parentTips: Vector[String], resultTreeOid: String) = validationLoaded("delta",
        deltaTransform(V.Delta(mergeDigest, parentTips, resultTreeOid, lastPending.resultTreeDigest,
          mergePaths.zip(kinds).map { (path, kind) => V.Change(path, kind, Vector("module-contract")) },
          "4" * 64)))
      def loadPolicyC(repositoryIdentity: String, repositoryPolicyVersion: String,
        deltaDigest: String) = validationLoaded("policy", policyTransform(V.Policy(deltaDigest,
        repositoryPolicyVersion, "validation-v1",
        Vector(V.LocalCheck("module-check", Vector("src/main.scala"), 0L)),
        Vector("full-check"), "merge-review-v1", "5" * 64)))
      def loadValidationC(repositoryIdentity: String, planDigest: String) = validationLoaded("validation",
        validationTransform(V.ValidationEvidence(planDigest, lastPending,
          expectedChecks.map(id => V.CheckReceipt(id, true, true, "6" * 64)), true, "7" * 64)))
    }
    var rejectValidationAppend = false
    var failAfterValidationAppend = false
    var stored: Option[InstanceRecord] = None
    var appends = 0
    var rejectMergeAppend = false
    var failAfterMergeAppend = false
    val instances = new WorkflowInstancePersistence {
      def create(config: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
        if (config != configuration || stored.nonEmpty)
          Consequence.stateConflict("RepositorySync fixture create conflict")
        else record.validateC.map { valid => stored = Some(valid); valid }
      def load(config: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
        if (config != configuration) Consequence.stateConflict("RepositorySync fixture store mismatch")
        else Consequence.success(stored.filter(_.identity == identity))
      def append(config: Configuration, identity: InstanceIdentity,
        expectedRevision: InstanceRevision, entry: HistoryEntry): Consequence[InstanceRecord] =
        if (rejectValidationAppend && expectedRevision == InstanceRevision(6L))
          Consequence.stateConflict("fixture validation append failure")
        else if (rejectMergeAppend && expectedRevision == InstanceRevision(5L))
          Consequence.stateConflict("RepositorySync fixture merge append failure")
        else stored match {
          case Some(current) if config == configuration && current.identity == identity =>
            current.appendC(expectedRevision, entry).flatMap { next =>
              appends += 1
              stored = Some(next)
              if (failAfterValidationAppend && expectedRevision == InstanceRevision(6L))
                Consequence.stateConflict("fixture validation append response uncertain")
              else if (failAfterMergeAppend && expectedRevision == InstanceRevision(5L))
                Consequence.stateConflict("fixture append response uncertain")
              else Consequence.success(next)
            }
          case _ => Consequence.stateConflict("RepositorySync fixture instance is missing")
        }
    }
    def start() = RepositorySyncStartExecution.startC(startCommand,
      selections, mappings, definition, configuration, instances)
    def boundary() = RepositorySyncBoundaryAssessmentExecution.assessC(boundaryCommand,
      selections, mappings, snapshots, scopes, definition, configuration, instances)
    def fetch() = RepositorySyncFetchAdmissionExecution.admitC(fetchCommand,
      selections, mappings, snapshots, scopes, fetches, definition, configuration, instances)
    def prepareFetched(): Unit = { take(start()); take(boundary()); take(fetch()); () }
    def preparePreserved(): Unit = { prepareFetched(); take(preserve()); () }
    def prepareMerged(): Unit = { prepare(); take(merge()); () }
    def validate(command: RepositorySyncMergeValidationAdmissionCommand = validationCommand) =
      RepositorySyncMergeValidationAdmissionExecution.admitC(command, selections, mappings,
        snapshots, scopes, fetches, preservations, relations, merges, validations, definition, configuration, instances)
    def prepare(): Unit = { preparePreserved(); take(classify()); () }
    def preserve(command: RepositorySyncPreservationAdmissionCommand = preservationCommand) =
      RepositorySyncPreservationAdmissionExecution.admitC(command, selections, mappings,
        snapshots, scopes, fetches, preservations, definition, configuration, instances)
    def classify(command: RepositorySyncRelationAdmissionCommand = relationCommand) =
      RepositorySyncRelationAdmissionExecution.admitC(command, selections, mappings,
        snapshots, scopes, fetches, preservations, relations, definition, configuration, instances)
    def merge(command: RepositorySyncMergeAdmissionCommand = mergeCommand) =
      RepositorySyncMergeAdmissionExecution.admitC(command, selections, mappings,
        snapshots, scopes, fetches, preservations, relations, merges, definition, configuration, instances)
    def retainedValidation() = for {
      preserved <- RepositorySyncCheckpointAssessment.assessC(admittedBoundary, admittedScope, admittedFetch, preservations)
      relation <- RepositorySyncRelationAssessment.assessC(admittedBoundary, admittedScope, admittedFetch, preserved, relations)
      merged <- RepositorySyncMergeAssessment.assessC(admittedBoundary, admittedScope, admittedFetch, preserved, relation, merges)
      validated <- V.assessC(V.Context(admittedBoundary, admittedScope, admittedFetch, preserved, relation, merged), validations)
    } yield validated
  }

  private val now = Instant.parse("2026-09-28T00:00:00Z")
  private val requirement = ExecutionRequirement(Vector(CapabilityRequirement("review-merge-capability")),
    RiskLevel("standard"), ReasoningLevel.Deep, true)
  private val presentation = MinimalPresentation("Review pending merge", "Awaiting full pending-merge review")
  private def unitOfWork() = new UnitOfWork(ExecutionContext.create(), EventEngine.noop(DataStore.noop()))
  private final class ReviewFixture(dirty: Boolean = false) {
    val f = new Fixture(dirty = dirty)
    f.prepareMerged()
    take(f.validate())
    val validated = take(f.retainedValidation())
    val submission = take(RepositorySyncMergeReviewAdmission.submissionC("review-1", validated))
    val active = f.stored.get
    val operation = take(RepositorySyncDefinitionBinding.reviewOperationC)
    val provided = StateMachineProvidedApiRequest("RepositorySyncWorkflow", "repository-sync-native-convergence-v10",
      StateMachineRunIdentity("sync-run-1"), StateMachineOperationIdentity("RepositorySyncService", "requestMergeReview"),
      Some(StateMachineOperationInput(StateMachineInputTypeReference("RepositorySyncMergeReviewRequest"),
        ContextReference(submission.identity, submission.candidate.reference.revision))),
      ContextBundle("Full pending-merge review", Vector.empty, Vector.empty,
        ContextSnapshot("repository-sync-native-convergence-v10", modelRevision = Some(validated.plan.policy.reviewPolicyVersion),
          evidenceRevision = Some(validated.digest))))
    def plan(command: RepositorySyncMergeValidationAdmissionCommand = validationCommand,
      submitted: RepositorySyncMergeReviewAdmission.Submission = submission,
      record: InstanceRecord = active, request: StateMachineProvidedApiRequest = provided,
      required: StateMachineRequiredOperation = operation, time: Instant = now) =
      RepositorySyncMergeReviewBoundary.planC(command, submitted, time, record, request, required,
        f.selections, f.mappings, configuration)
    def pending = take(plan()).pending.getOrElse(fail("full merge review is missing"))
  }

  private final class PublicReviewFixture(dirty: Boolean = false) {
    import RepositorySyncMergeReviewServiceOperation as S
    val review = new ReviewFixture(dirty)
    val input = take(RepositorySyncMergeReviewBoundary.requestC(review.submission))
    var retained: Option[S.Retained] = Some(S.Retained(validationCommand, review.submission, review.provided.runId))
    val continuationStore = new ContinuationRuntimePersistence.InMemory
    val runtime = new PersistentContinuationRuntime(continuationStore)
    val issued = new IssuedWorkOrderPersistence.InMemory[RepositorySyncMergeReviewRequest]
    val reviews = new RepositorySyncMergeReviewReceiptStore.InMemory
    var ports = S.Ports(new S.RetainedPort {
      def loadC(identity: String) = Consequence.success(retained.filter(_.submission.identity == identity))
    }, review.f.selections, review.f.mappings, review.f.instances, configuration,
      runtime, issued, requirement, presentation, () => now, () => unitOfWork(), reviews)
    def request(value: RepositorySyncMergeReviewRequest = input) = Request.of(
      component = SmWorkflowComponent.name, service = "RepositorySyncService", operation = "requestMergeReview",
      arguments = List(Argument("arg1", S.encodeRequest(value))))
    def result(work: WorkflowInteraction[RepositorySyncMergeReviewRequest, Nothing], decision: String = "accept") = {
      val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncMergeReviewRequest]].request
      val target = order.input.get.value.candidateRevision
      ContinuationResult(work.handle, order.runId, order.continuationId, order.expectedRevision, order.context.snapshot,
        TypedValue("RepositorySyncMergeReviewResult", RepositorySyncMergeReviewResult(target,
          now.minusSeconds(1).toString, decision, "Full pending merge reviewed",
          if (decision == "repair") Vector(RepositorySyncMergeReviewFinding(
            "finding-1", Vector("src/main.scala"), "Repair the changed source")) else Vector.empty)),
        ContextReference("merge-review-result-1", target), Vector.empty, ExecutionEvidence(Vector.empty))
    }
    def completion(value: ContinuationResult[RepositorySyncMergeReviewResult]) = Request.of(
      component = SmWorkflowComponent.name, service = "RepositorySyncService", operation = "submitMergeReview",
      arguments = List(Argument("arg1", take(WorkflowResultJsonV1.encodeC(value,
        RepositorySyncMergeReviewResultAdapter.payloadCodec)))))
    def withSubsystem[A](body: Subsystem => A): A = {
      val operations = NonEmptyVector.of(take(S.bindRequestC(ports)), take(S.bindCompletionC(ports)))
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition(
        "RepositorySyncService", spec.OperationDefinitionGroup(operations)))))
      val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(name = "sm-workflow-merge-review",
        version = None, configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
        aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
      try {
        val component = new Component() {}
        component.initialize(ComponentInit(subsystem, Component.Core.create(SmWorkflowComponent.name,
          SmWorkflowComponent.componentId, ComponentInstanceId.default(SmWorkflowComponent.componentId), protocol),
          ComponentOrigin.Builtin))
        subsystem.add(component)
        body(subsystem)
      } finally subsystem.shutdown()
    }
    def issue(subsystem: Subsystem) = take(WorkflowWorkOrderJsonV1.decodeC(
      scalar(take(subsystem.executeOperationResponse(request()))), RepositorySyncMergeReviewBoundary.inputCodec))
  }
  private def scalar(response: OperationResponse): String = response match {
    case OperationResponse.Scalar(value: String) => value
    case other => fail(s"Expected public Workflow JSON, found $other")
  }

  private final class MergeDecisionPort(
    transform: (ContinuationRequest[RepositorySyncMergeDecision.Input] =>
      ContinuationRequest[RepositorySyncMergeDecision.Input]) = (request => request)
  ) extends RepositorySyncMergeDecision.Port {
    var calls = 0
    def resolveC(intent: RepositorySyncMergeDecision.Intent) = {
      calls += 1
      Consequence.success(transform(ContinuationRequest[RepositorySyncMergeDecision.Input](
        intent.runId, org.goldenport.cncf.workflow.ContinuationIdentity(intent.idempotencyKey),
        intent.expectedRevision, StateMachineRequiredOperationIdentity("host.merge-decision"),
        StateMachineOperationIdentity("HumanBoundaryFixture", "decideMerge"),
        Some(TypedValue(RepositorySyncMergeDecision.inputCodec.typeIdentity, intent.input)), None,
        ContextBundle("Resolve the admitted full merge review",
          Vector(ContextReference(intent.input.candidate.submissionIdentity, intent.input.candidate.candidateRevision)),
          Vector(intent.input.reviewResultReference), intent.snapshot),
        CompletionContract("host-human-decision-stop-only-v1", Vector.empty),
        EvidenceContract("host-human-decision-evidence-v1", Vector.empty),
        StateMachineOperationIdentity("HumanBoundaryFixture", "recordMergeDecision"))))
    }
  }

  private final class CommitFixture(dirty: Boolean = false, decision: String = "accept") {
    import RepositorySyncMergeCommitBoundary as C
    val f = new PublicReviewFixture(dirty)
    val submitted = f.withSubsystem { subsystem =>
      val result = f.result(f.issue(subsystem), decision)
      take(subsystem.executeOperationResponse(f.completion(result)))
      result
    }
    val reviewed = f.review.f.stored.get
    val pending = f.review.submission.candidate.payload.value.plan.context.merge.pending
    var cached: Option[C.Receipt] = None
    var writes = 0
    var calls = 0
    var pendingLoads = 0
    var pendingTransform: RepositorySyncMergeAssessment.PendingState => RepositorySyncMergeAssessment.PendingState = identity
    var receiptTransform: C.Receipt => C.Receipt = identity
    var afterTransform: C.CommittedState => C.CommittedState = identity
    var failCommit = false
    var uncertainCommit = false
    var retainReceipt = true
    val commits = new C.CommitPort {
      def loadC(key: String) = Consequence.success(cached.filter(_.request.idempotencyKey == key))
      def commitC(request: C.Request, observed: RepositorySyncMergeAssessment.PendingState) = {
        calls += 1
        if (failCommit) Consequence.stateConflict("planned commit failure")
        else if (observed != pending) Consequence.stateConflict("fixture pending state differs")
        else cached match {
          case Some(value) if value.request == request => Consequence.success(value)
          case Some(_) => Consequence.stateConflict("fixture refuses replacement commit")
          case None =>
            writes += 1
            val value = receiptTransform(C.Receipt(request, "e" * 40, request.orderedParents,
              request.treeOid, request.treeDigest, request.localRef, Vector(request.localRef), true, "d" * 64))
            if (retainReceipt) cached = Some(value)
            if (uncertainCommit) { uncertainCommit = false; Consequence.stateConflict("uncertain commit response") }
            else Consequence.success(value)
        }
      }
    }
    val inspection = new C.InspectionPort {
      def pendingC(repository: String, validation: String) = {
        pendingLoads += 1
        Consequence.success(Some(pendingTransform(pending)))
      }
      def committedC(repository: String, receiptDigest: String) = Consequence.success(cached.map { receipt =>
        afterTransform(C.CommittedState(pending.state.copy(evidenceBinding = receiptDigest,
          localHeadOid = receipt.commitOid, localTreeOid = receipt.treeOid, localTreeDigest = receipt.treeDigest,
          dirtyPaths = Vector.empty, indexShape = "EMPTY", pendingOperations = Vector.empty,
          inspectionDigest = "c" * 64), receipt.orderedParents))
      })
    }
    def commit(reference: ContextReference = submitted.resultReference,
      ports: RepositorySyncMergeReviewServiceOperation.Ports = f.ports) = C.commitC(reference, ports, commits, inspection)
  }


  private final class MergedTailFixture(dirty: Boolean = false, trackingUpdated: Boolean = false) {
    import RepositorySyncPushAssessment as P
    import RepositorySyncConvergenceAssessment as K
    import RepositorySyncMergedConvergenceServiceOperation as O
    val c = new CommitFixture(dirty)
    val committed = take(c.commit())
    val before = committed.after.state
    val commitDigest = take(RepositorySyncMergeCommitBoundary.receiptDigestC(committed.receipt))
    val command = RepositorySyncMergedConvergenceCommand(c.submitted.resultReference.identity,
      c.submitted.resultReference.revision, "merged-push-1", "10", "merged-convergence-1")
    val frozen = P.FrozenState(before, before.trackingTipOid, true, true, false, "a" * 64,
      P.Authority(commitDigest, before.repositoryIdentity, before.worktreeIdentity, before.localBranch,
        before.trackingRemote, before.trackingBranch, before.remoteEndpointIdentity, before.repositoryPolicyVersion,
        before.localHeadOid, before.trackingTipOid, true, true, "b" * 64))
    var frozenTransform: P.FrozenState => P.FrozenState = identity
    var receiptTransform: P.Receipt => P.Receipt = identity
    var postTransform: RepositorySyncFastForwardAssessment.State => RepositorySyncFastForwardAssessment.State = identity
    var fetchTransform: K.FetchReceipt => K.FetchReceipt = identity
    var verificationTransform: K.Verification => K.Verification = identity
    var missing = Set.empty[String]
    var frozenLoads = 0
    var lastPushPost: Option[RepositorySyncFastForwardAssessment.State] = None
    var lastFetch: Option[K.FetchReceipt] = None
    var onVerification: () => Unit = () => ()
    val pushes = new P.Port {
      def loadFrozenC(repository: String, binding: String, source: String, destination: String) = {
        repository shouldBe before.repositoryIdentity
        binding shouldBe commitDigest
        binding should not be c.f.review.validated.plan.context.relation.digest
        source shouldBe committed.receipt.commitOid
        destination shouldBe before.trackingTipOid
        frozenLoads += 1
        Consequence.success(if (missing("freeze")) None else Some(frozenTransform(frozen)))
      }
      def loadReceiptC(repository: String, binding: String) = {
        val tracking = s"refs/remotes/${before.trackingRemote}/${before.trackingBranch}"
        Consequence.success(if (missing("push")) None else Some(receiptTransform(P.Receipt(binding,
          s"refs/heads/${before.localBranch}", s"refs/heads/${before.trackingBranch}", before.localHeadOid,
          before.trackingTipOid, before.localHeadOid,
          if (trackingUpdated) before.localHeadOid else before.trackingTipOid,
          Vector(s"refs/heads/${before.trackingBranch}"), if (trackingUpdated) Vector(tracking) else Vector.empty,
          false, false, false, true, true, "c" * 64))))
      }
      def loadPostStateC(repository: String, binding: String) = {
        val state = postTransform(before.copy(evidenceBinding = binding,
          trackingTipOid = if (trackingUpdated) before.localHeadOid else before.trackingTipOid,
          inspectionDigest = "d" * 64))
        lastPushPost = Some(state)
        Consequence.success(if (missing("post")) None else Some(state))
      }
    }
    val convergences = new K.Port {
      def loadFetchC(repository: String, binding: String, target: String) = {
        target shouldBe committed.receipt.commitOid
        val state = lastPushPost.get.copy(evidenceBinding = binding)
        val tracking = s"refs/remotes/${state.trackingRemote}/${state.trackingBranch}"
        val receipt = fetchTransform(K.FetchReceipt(state, state.localHeadOid, state.localHeadOid,
          state.localHeadOid, state.localTreeOid, state.localTreeDigest,
          s"refs/heads/${state.trackingBranch}", tracking,
          if (trackingUpdated) Vector.empty else Vector(tracking), true, true, "e" * 64))
        lastFetch = Some(receipt)
        Consequence.success(if (missing("fetch")) None else Some(receipt))
      }
      def loadVerificationC(repository: String, binding: String) = {
        onVerification()
        val state = lastFetch.get.before.copy(evidenceBinding = binding,
          trackingTipOid = before.localHeadOid, inspectionDigest = "f" * 64)
        Consequence.success(if (missing("verification")) None else Some(verificationTransform(
          K.Verification(state, 0L, 0L, true, false, "0" * 64))))
      }
    }
    var ports = RepositorySyncMergedConvergenceExecution.Ports(c.f.ports, c.commits, c.inspection, pushes, convergences)
    def complete(value: RepositorySyncMergedConvergenceCommand = command) =
      RepositorySyncMergedConvergenceExecution.admitC(value, ports)
    def request(value: RepositorySyncMergedConvergenceCommand = command) = Request.of(
      component = SmWorkflowComponent.name, service = "RepositorySyncService", operation = "admitMergedConvergence",
      arguments = List(Argument("arg1", O.encodeCommand(value))))
    def withSubsystem[A](body: Subsystem => A): A = {
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(spec.ServiceDefinition(
        "RepositorySyncService", spec.OperationDefinitionGroup(NonEmptyVector.of(take(O.bindC(ports))))))))
      val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(name = "sm-workflow-merged-convergence",
        version = None, configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
        aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
      try {
        val component = new Component() {}
        component.initialize(ComponentInit(subsystem, Component.Core.create(SmWorkflowComponent.name,
          SmWorkflowComponent.componentId, ComponentInstanceId.default(SmWorkflowComponent.componentId), protocol),
          ComponentOrigin.Builtin))
        subsystem.add(component)
        body(subsystem)
      } finally subsystem.shutdown()
    }
    def failingAppend(revision: Long, afterSave: Boolean): Unit = {
      val underlying = c.f.review.f.instances
      var failOnce = true
      val wrapped = new WorkflowInstancePersistence {
        def create(config: Configuration, record: InstanceRecord) = underlying.create(config, record)
        def load(config: Configuration, id: InstanceIdentity) = underlying.load(config, id)
        def append(config: Configuration, id: InstanceIdentity, expected: InstanceRevision, entry: HistoryEntry) =
          if (failOnce && expected == InstanceRevision(revision)) {
            failOnce = false
            if (afterSave) underlying.append(config, id, expected, entry).flatMap(_ =>
              Consequence.stateConflict("uncertain merged tail append response"))
            else Consequence.stateConflict("merged tail append failure")
          } else underlying.append(config, id, expected, entry)
      }
      ports = ports.copy(review = ports.review.copy(instances = wrapped))
    }
  }

  "RepositorySync merge validation progression" should {
    "require persisted MergeAdmitted history and never create or merge an instance implicitly" in {
      val f = new Fixture
      f.validate() shouldBe a[Consequence.Failure[?]]
      f.prepareFetched()
      f.validate() shouldBe a[Consequence.Failure[?]]
      take(f.preserve())
      take(f.classify())
      f.validate() shouldBe a[Consequence.Failure[?]]
      f.validationLoads shouldBe 0
      f.mergeLoads shouldBe 0
      take(f.merge())
      take(f.validate())
      f.stored.get.revision shouldBe InstanceRevision(7L)
      f.appends shouldBe 7
    }

    "append revision seven once with exact upstream history and return a nonterminal review WAIT on replay" in {
      val f = new Fixture
      f.prepareMerged()
      val prefix = f.stored.get.history
      val admitted = take(f.validate())
      val wait = admitted.current.asInstanceOf[WorkflowContinuation.Wait]
      wait.expectedRevision.value shouldBe "7"
      wait.presentation.nextAction shouldBe Some("Request full pending-merge review")
      wait.contextSnapshot.evidenceRevision should not be empty
      f.stored.get.lifecycle shouldBe Lifecycle.Active
      f.stored.get.currentProgression shouldBe Some(ProgressionReference("sync.MergeValidated"))
      f.stored.get.history.take(6) shouldBe prefix
      take(WorkflowWaitJsonV1.decodeC(take(WorkflowWaitJsonV1.encodeC(admitted)))) shouldBe admitted
      take(f.validate()) shouldBe admitted
      f.appends shouldBe 7
      f.stored.get.suspension shouldBe None
    }

    "preserve the checkpoint-based local tip and complete prior history through validation" in {
      val f = new Fixture(dirty = true)
      f.prepareMerged()
      val prefix = f.stored.get.history
      take(f.validate())
      take(f.validate())
      f.stored.get.history.take(6) shouldBe prefix
      f.lastPending.state.localHeadOid shouldBe "2" * 40
      f.checkpointLoads should be > 0
      f.appends shouldBe 7
    }

    "reject changed upstream keys and evidence before loading validation providers" in {
      val f = new Fixture
      f.prepareMerged()
      val altered = Vector(
        validationCommand.copy(mergeCommand = mergeCommand.copy(idempotencyKey = "other-merge")),
        validationCommand.copy(mergeCommand = mergeCommand.copy(relationCommand =
          relationCommand.copy(idempotencyKey = "other-relation"))),
        validationCommand.copy(mergeCommand = mergeCommand.copy(relationCommand =
          relationCommand.copy(preservationCommand = preservationCommand.copy(idempotencyKey = "other-preservation")))),
        validationCommand.copy(mergeCommand = mergeCommand.copy(relationCommand =
          relationCommand.copy(preservationCommand = preservationCommand.copy(fetchCommand =
            fetchCommand.copy(idempotencyKey = "other-fetch"))))),
        validationCommand.copy(mergeCommand = mergeCommand.copy(relationCommand =
          relationCommand.copy(preservationCommand = preservationCommand.copy(fetchCommand =
            fetchCommand.copy(boundaryCommand = boundaryCommand.copy(idempotencyKey = "other-boundary")))))))
      altered.foreach(f.validate(_) shouldBe a[Consequence.Failure[?]])
      f.fetchReceiptValue = f.fetchReceipt.copy(executionDigest = "0" * 64)
      f.validate() shouldBe a[Consequence.Failure[?]]
      f.fetchReceiptValue = f.fetchReceipt
      f.cleanTransform = _.copy(inspectionDigest = "0" * 64)
      f.validate() shouldBe a[Consequence.Failure[?]]
      f.cleanTransform = identity
      f.relationTransform = _.copy(remoteAhead = 4L)
      f.validate() shouldBe a[Consequence.Failure[?]]
      f.relationTransform = identity
      f.mergeReceiptTransform = _.copy(executionDigest = "0" * 64)
      f.validate() shouldBe a[Consequence.Failure[?]]
      f.mergeReceiptTransform = identity
      f.structuralTransform = _.copy(inspectionDigest = "0" * 64)
      f.validate() shouldBe a[Consequence.Failure[?]]
      f.structuralTransform = identity
      f.validationLoads shouldBe 0
      f.stored.get.revision shouldBe InstanceRevision(6L)
      take(f.validate())
    }

    "retain revision six on missing, null and failed delta, policy or validation evidence and admit an exact retry" in {
      Vector("delta", "policy", "validation").foreach { stage =>
        val f = new Fixture
        f.prepareMerged()
        f.validationMissing = stage
        f.validate() shouldBe a[Consequence.Failure[?]]
        f.validationMissing = ""
        f.validationNull = stage
        f.validate() shouldBe a[Consequence.Failure[?]]
        f.validationNull = ""
        f.validationFailure = stage
        f.validate() shouldBe a[Consequence.Failure[?]]
        f.validationFailure = ""
        f.stored.get.revision shouldBe InstanceRevision(6L)
        f.appends shouldBe 6
        take(f.validate())
        f.appends shouldBe 7
      }
    }

    "refuse weak policy, incomplete checks and changed staged merge instead of advancing" in {
      val f = new Fixture
      f.prepareMerged()
      f.policyTransform = _.copy(localizedChecks = Vector.empty)
      f.validate() shouldBe a[Consequence.Failure[?]]
      f.policyTransform = identity
      val invalid: Vector[V.ValidationEvidence => V.ValidationEvidence] = Vector(
        _.copy(completed = false), _.copy(checks = Vector.empty),
        x => x.copy(checks = x.checks.map(_.copy(successful = false))),
        x => x.copy(checks = x.checks.map(_.copy(completed = false))),
        x => x.copy(pending = x.pending.copy(resultTreeDigest = "0" * 64)),
        x => x.copy(pending = x.pending.copy(state = x.pending.state.copy(localHeadOid = "0" * 40))))
      invalid.foreach { transform =>
        f.validationTransform = transform
        f.validate() shouldBe a[Consequence.Failure[?]]
      }
      f.appends shouldBe 6
      f.validationTransform = identity
      take(f.validate())
    }

    "permit documentation-only product-test omission without skipping structure or claiming review acceptance" in {
      val f = new Fixture
      f.prepareMerged()
      f.kinds = Vector(V.ChangeKind.Documentation, V.ChangeKind.Metadata)
      f.expectedChecks = Vector.empty
      val admitted = take(f.validate())
      admitted.current.asInstanceOf[WorkflowContinuation.Wait].presentation.nextAction shouldBe
        Some("Request full pending-merge review")
      f.stored.get.lifecycle shouldBe Lifecycle.Active
      f.stored.get.currentProgression shouldBe Some(ProgressionReference("sync.MergeValidated"))
      take(f.validate()) shouldBe admitted
      f.appends shouldBe 7
      val blocked = new Fixture
      blocked.prepareMerged()
      blocked.structuralTransform = _.copy(scanCompleted = false)
      blocked.validate() shouldBe a[Consequence.Failure[?]]
      blocked.validationLoads shouldBe 0
      blocked.appends shouldBe 6
    }

    "require broad receipts for runtime and public-contract changes before reaching review WAIT" in {
      Vector(V.ChangeKind.Runtime, V.ChangeKind.PublicContract).foreach { kind =>
        val f = new Fixture
        f.prepareMerged()
        f.kinds = Vector(V.ChangeKind.Documentation, kind)
        f.validate() shouldBe a[Consequence.Failure[?]]
        f.appends shouldBe 6
        f.expectedChecks = Vector("full-check")
        take(f.validate())
        f.appends shouldBe 7
      }
    }

    "bind validation idempotency to delta, selected policy and successful receipt digests" in {
      val f = new Fixture
      f.prepareMerged()
      val admitted = take(f.validate())
      f.validate(validationCommand.copy(idempotencyKey = "other-validation")) shouldBe a[Consequence.Failure[?]]
      f.deltaTransform = _.copy(inspectionDigest = "0" * 64)
      f.validate() shouldBe a[Consequence.Failure[?]]
      f.deltaTransform = identity
      f.policyTransform = _.copy(validationPolicyVersion = "validation-v2")
      f.validate() shouldBe a[Consequence.Failure[?]]
      f.policyTransform = identity
      f.policyTransform = _.copy(reviewPolicyVersion = "review-v2")
      f.validate() shouldBe a[Consequence.Failure[?]]
      f.policyTransform = identity
      f.validationTransform = x => x.copy(checks = x.checks.map(_.copy(executionDigest = "0" * 64)))
      f.validate() shouldBe a[Consequence.Failure[?]]
      f.validationTransform = identity
      take(f.validate()) shouldBe admitted
      f.appends shouldBe 7
    }

    "retain revision six on pre-save failure and reconcile only the exact successful retry" in {
      val f = new Fixture
      f.prepareMerged()
      f.rejectValidationAppend = true
      f.validate() shouldBe a[Consequence.Failure[?]]
      f.stored.get.revision shouldBe InstanceRevision(6L)
      f.appends shouldBe 6
      f.rejectValidationAppend = false
      val admitted = take(f.validate())
      take(f.validate()) shouldBe admitted
      f.appends shouldBe 7
    }

    "reconcile an uncertain post-save response without another append or false rollback" in {
      val f = new Fixture
      f.prepareMerged()
      f.failAfterValidationAppend = true
      f.validate() shouldBe a[Consequence.Failure[?]]
      f.stored.get.revision shouldBe InstanceRevision(7L)
      f.appends shouldBe 7
      val admitted = take(f.validate())
      take(f.validate()) shouldBe admitted
      f.appends shouldBe 7
    }

    "reject persisted non-merge stages at either admitted revision before validation loads" in {
      val f = new Fixture
      f.prepareMerged()
      val original = f.stored
      val other = Some(ProgressionReference("sync.PushAdmitted"))
      f.stored = original.map(r => r.copy(currentProgression = other,
        history = r.history.init :+ r.history.last.copy(currentProgression = other)))
      take(f.stored.get.validateC)
      f.validate() shouldBe a[Consequence.Failure[?]]
      f.validationLoads shouldBe 0
      f.stored = original
      val admitted = take(f.validate())
      val validated = f.stored
      f.stored = validated.map(r => r.copy(currentProgression = other,
        history = r.history.init :+ r.history.last.copy(currentProgression = other)))
      val loads = f.validationLoads
      f.validate() shouldBe a[Consequence.Failure[?]]
      f.validationLoads shouldBe loads
      f.stored = validated
      take(f.validate()) shouldBe admitted
      f.appends shouldBe 7
    }

    "reject null and malformed command chains without consulting validation evidence" in {
      val f = new Fixture
      f.prepareMerged()
      val commands = Vector(null, validationCommand.copy(mergeCommand = null),
        validationCommand.copy(expectedRevision = "7"), validationCommand.copy(idempotencyKey = null),
        validationCommand.copy(idempotencyKey = ""),
        validationCommand.copy(mergeCommand = mergeCommand.copy(expectedRevision = "6")),
        validationCommand.copy(mergeCommand = mergeCommand.copy(idempotencyKey = null)),
        validationCommand.copy(mergeCommand = mergeCommand.copy(relationCommand = null)),
        validationCommand.copy(mergeCommand = mergeCommand.copy(relationCommand = relationCommand.copy(preservationCommand = null))),
        validationCommand.copy(mergeCommand = mergeCommand.copy(relationCommand =
          relationCommand.copy(preservationCommand = preservationCommand.copy(fetchCommand = null)))))
      commands.foreach(f.validate(_) shouldBe a[Consequence.Failure[?]])
      f.validationLoads shouldBe 0
      f.appends shouldBe 6
    }

    "refuse incomplete and incompatible generated public validation bindings" in {
      val f = new Fixture
      f.prepareMerged()
      RepositorySyncMergeValidationAdmissionServiceOperation.bindC(f.selections, f.mappings,
        f.snapshots, f.scopes, f.fetches, f.preservations, f.relations, f.merges, null,
        definition, configuration, f.instances) shouldBe a[Consequence.Failure[?]]
      RepositorySyncMergeValidationAdmissionServiceOperation.bindC(f.selections, f.mappings,
        f.snapshots, f.scopes, f.fetches, f.preservations, f.relations, f.merges, f.validations,
        take(SplitPhaseDefinitionBinding.bindC), configuration, f.instances) shouldBe a[Consequence.Failure[?]]
      f.validationLoads shouldBe 0
      f.appends shouldBe 6
    }

    "reject caller-selected outcomes, commands and nested schema drift at the strict wire boundary" in {
      val wire = RepositorySyncMergeValidationAdmissionServiceOperation.encodeCommand(validationCommand)
      take(RepositorySyncMergeValidationAdmissionServiceOperation.decodeCommandC(wire)) shouldBe validationCommand
      val bad = Vector(null, "{}", "not-json", wire.dropRight(1) + ",\"outcome\":\"SYNCHRONIZED\"}",
        wire.dropRight(1) + ",\"nextState\":\"Converged\"}",
        wire.dropRight(1) + ",\"validationAccepted\":true}",
        wire.dropRight(1) + ",\"command\":\"sbt test; git push\"}",
        wire.replace("\"expectedRevision\":\"6\"", "\"expectedRevision\":6"),
        wire.replace("\"idempotencyKey\":\"sync-validation-1\"", "\"idempotencyKey\":null"),
        wire.replace(RepositorySyncMergeValidationAdmissionServiceOperation.wireSchema, "unsupported"),
        wire.replace(RepositorySyncMergeAdmissionServiceOperation.wireSchema, "unsupported-nested"))
      bad.foreach(RepositorySyncMergeValidationAdmissionServiceOperation.decodeCommandC(_) shouldBe
        a[Consequence.Failure[?]])
    }

    "serve the generated public validation Operation and equivalent CNCF CLI JSON" in {
      val f = new Fixture
      f.prepareMerged()
      val operation = take(RepositorySyncMergeValidationAdmissionServiceOperation.bindC(
        f.selections, f.mappings, f.snapshots, f.scopes, f.fetches, f.preservations,
        f.relations, f.merges, f.validations, definition, configuration, f.instances))
      val wire = RepositorySyncMergeValidationAdmissionServiceOperation.encodeCommand(validationCommand)
      val request = Request.of(component = SmWorkflowComponent.name,
        service = "RepositorySyncService", operation = "admitMergeValidation",
        arguments = List(Argument("arg1", wire)))
      operation.createOperationRequest(request.copy(operation = "admitMerge")) shouldBe a[Consequence.Failure[?]]
      operation.createOperationRequest(request.copy(service = Some("OtherService"))) shouldBe a[Consequence.Failure[?]]
      operation.createOperationRequest(request.copy(arguments = Nil)) shouldBe a[Consequence.Failure[?]]
      val service = spec.ServiceDefinition("RepositorySyncService",
        spec.OperationDefinitionGroup(NonEmptyVector.of(operation)))
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
      val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
        name = "sm-workflow-repository-merge-validation", version = None,
        configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
        aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
      try {
        val component = new Component() {}
        component.initialize(ComponentInit(subsystem,
          Component.Core.create(SmWorkflowComponent.name, SmWorkflowComponent.componentId,
            ComponentInstanceId.default(SmWorkflowComponent.componentId), protocol), ComponentOrigin.Builtin))
        subsystem.add(component)
        val directWire = take(subsystem.executeOperationResponse(request)) match {
          case OperationResponse.Scalar(value: String) => value
          case other => fail(s"Expected public WAIT response, found $other")
        }
        val direct = take(WorkflowWaitJsonV1.decodeC(directWire))
        direct.handle.instanceIdentity shouldBe f.stored.get.identity
        direct.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value shouldBe "7"
        val selector = s"${SmWorkflowComponent.name}/RepositorySyncService/admitMergeValidation"
        take(new CncfRuntime().executeCommandResponse(subsystem, Array(selector, "--json", wire))).print shouldBe directWire
        f.appends shouldBe 7
      } finally subsystem.shutdown()
    }
  }

  "RepositorySync full-merge review Continuation" should {
    "admit the generated candidate binding and its actual Required SPI" in {
      val candidate = take(RepositorySyncDefinitionBinding.candidateC)
      definition.producerAbiIdentity.value shouldBe CandidateAdmissionProducerAbi.acceptedSchemaVersion
      definition.workflowIdentity.value shouldBe candidate.workflow.identity
      definition.workflowRevision.value shouldBe candidate.workflow.version
      candidate.workflow.actions.map(_.identity) shouldBe Vector("ReviewMerge", "AdmitMergeReview",
        "ResolveRepositoryMergeConflicts", "AdmitRepositoryConflicts", "ReviewNativePendingMerge", "AdmitNativeMergeReview", "PresentRepositoryConflictDecision")
      candidate.workflow.requiredSpi.map(_.capability) shouldBe
        Vector("review-merge-capability", "resolve-repository-conflict-capability", "native-merge-review-capability", "repository-conflict-human-capability")
      candidate.sidecar.models.head.judgments.head.alternatives.map(_.value) shouldBe
        Vector("accept", "repair", "decision-needed", "reject")
      take(CandidateWorkflowAbi.admitC(candidate)) shouldBe candidate
    }

    "accept rehydrated definition facts without relying on object identity" in {
      val r = new ReviewFixture
      val rehydrated = r.active.copy(definition = take(RepositorySyncDefinitionBinding.bindC))
      rehydrated.definition should not be r.active.definition
      val pending = take(r.plan(record = rehydrated)).pending.get
      val projected = take(RepositorySyncMergeReviewBoundary.suspendedRecordC(pending, r.active))
      val runtime = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory)
      val store = new IssuedWorkOrderPersistence.InMemory[RepositorySyncMergeReviewRequest]
      val uow = unitOfWork()
      take(runtime.stageSuspensionAfterC(uow, pending.continuation, () =>
        r.f.instances.append(configuration, r.active.identity, InstanceRevision(7L), projected.history.last).map(_ => ())))
      take(uow.commit())
      r.f.stored = r.f.stored.map(_.copy(definition = take(RepositorySyncDefinitionBinding.bindC)))
      take(runtime.claimC(pending.continuation.continuationId))
      val issued = take(RepositorySyncMergeReviewBoundary.issueRecoveredWorkOrderC(pending,
        projected, runtime, store, requirement, presentation, r.f.instances, configuration))
      issued.handle.workflowIdentity shouldBe projected.definition.workflowIdentity
      r.f.stored.get.history shouldBe projected.history
      r.f.appends shouldBe 8
    }

    "plan only the declared review and include the complete validated pending-merge identity" in {
      Vector(false, true).foreach { dirty =>
        val r = new ReviewFixture(dirty)
        val pending = r.pending
        val payload = pending.input.value
        payload.candidateRevision shouldBe r.submission.candidate.reference.revision
        payload.validationDigest shouldBe r.validated.digest
        payload.pendingHeadOid shouldBe (if (dirty) "2" * 40 else r.f.snapshot.headOid)
        payload.orderedParents shouldBe r.validated.plan.context.merge.frozen.state.parentTips
        payload.pendingTreeOid shouldBe r.validated.plan.context.merge.pending.resultTreeOid
        payload.pendingTreeDigest shouldBe r.validated.plan.context.merge.pending.resultTreeDigest
        payload.changedPaths shouldBe Vector("README.md", "src/main.scala")
        payload.selectedChecks shouldBe Vector("module-check")
        pending.continuation.requiredOperation shouldBe r.operation
        pending.continuation.expectedRevision shouldBe StateMachineRevision("8")
        pending.outcome shouldBe ActionExecution.Suspended(pending.continuation)
        r.pending.continuation shouldBe pending.continuation
        r.f.stored shouldBe Some(r.active)
        r.f.appends shouldBe 7
      }
    }

    "refuse changed upstream history, command keys or retained validation before planning work" in {
      val r = new ReviewFixture
      val other = Some(ProgressionReference("sync.MergeAdmitted"))
      val wrongStage = r.active.copy(currentProgression = other,
        history = r.active.history.init :+ r.active.history.last.copy(currentProgression = other))
      val changedPrefix = r.active.copy(history = r.active.history.updated(1,
        r.active.history(1).copy(causation = CausationReference("other-boundary"))))
      Vector(wrongStage, changedPrefix, r.active.copy(revision = InstanceRevision(6L)),
        r.active.copy(identity = InstanceIdentity("other-instance"))).foreach(record =>
        r.plan(record = record) shouldBe a[Consequence.Failure[?]])
      r.plan(command = validationCommand.copy(idempotencyKey = "other-validation")) shouldBe a[Consequence.Failure[?]]
      r.plan(command = validationCommand.copy(mergeCommand = mergeCommand.copy(idempotencyKey = "other-merge"))) shouldBe
        a[Consequence.Failure[?]]
      val changed = r.submission.copy(candidate = r.submission.candidate.copy(payload =
        r.submission.candidate.payload.copy(value = r.validated.copy(digest = "0" * 64))))
      r.plan(submitted = changed) shouldBe a[Consequence.Failure[?]]
      r.f.stored shouldBe Some(r.active)
    }

    "refuse caller-supplied undeclared operations and metadata even with a supplied full review" in {
      val r = new ReviewFixture
      val operations = Vector(null, r.operation.copy(identity = StateMachineRequiredOperationIdentity("other-capability")),
        r.operation.copy(actionIdentity = "ReviewStep"),
        r.operation.copy(operation = StateMachineOperationIdentity("OtherService", "reviewMerge")),
        r.operation.copy(inputType = Some(StateMachineInputTypeReference("OtherRequest"))),
        r.operation.copy(resultType = Some(StateMachineResultTypeReference("OtherResult"))),
        r.operation.copy(metadata = r.operation.metadata.copy(completionContract = CompletionContract("other", Vector.empty))))
      operations.foreach(operation => r.plan(required = operation) shouldBe a[Consequence.Failure[?]])
      r.plan(record = r.active.copy(definition = take(SplitPhaseDefinitionBinding.bindC))) shouldBe
        a[Consequence.Failure[?]]
      r.f.appends shouldBe 7
    }

    "bind the Provided request to actual candidate, policy and generated input type without diagnostic hash equality" in {
      val r = new ReviewFixture
      val p = r.provided
      val context = p.context
      val requests = Vector(null, p.copy(workflowIdentity = "SplitPhaseWorkflow"),
        p.copy(workflowRevision = "old"), p.copy(runId = null),
        p.copy(operation = StateMachineOperationIdentity("RepositorySyncService", "admitMergeValidation")),
        p.copy(input = None), p.copy(input = Some(p.input.get.copy(typeReference = StateMachineInputTypeReference("Other")))),
        p.copy(input = Some(p.input.get.copy(contextReference = ContextReference("review-1", "0" * 64)))),
        p.copy(context = null), p.copy(context = context.copy(summary = "")),
        p.copy(context = context.copy(summary = "   ")),
        p.copy(context = context.copy(snapshot = context.snapshot.copy(modelRevision = Some("other-policy")))),
        p.copy(context = context.copy(references = null)))
      requests.foreach(request => r.plan(request = request) shouldBe a[Consequence.Failure[?]])
      take(r.plan(request = p.copy(context = context.copy(snapshot = context.snapshot.copy(
        evidenceRevision = Some("obsolete-diagnostic-proof")))))).pending.nonEmpty shouldBe true
      r.f.stored shouldBe Some(r.active)
    }

    "persist suspension before publication and issue exactly the common review WorkOrder" in {
      val r = new ReviewFixture(dirty = true)
      val pending = r.pending
      val projected = take(RepositorySyncMergeReviewBoundary.suspendedRecordC(pending, r.active))
      val runtime = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory)
      val issuedStore = new IssuedWorkOrderPersistence.InMemory[RepositorySyncMergeReviewRequest]
      val uow = unitOfWork()
      take(runtime.stageSuspensionAfterC(uow, pending.continuation, () =>
        r.f.instances.append(configuration, r.active.identity, InstanceRevision(7L), projected.history.last).map(_ => ())))
      runtime.claimC(pending.continuation.continuationId) shouldBe a[Consequence.Failure[?]]
      RepositorySyncMergeReviewBoundary.issueRecoveredWorkOrderC(pending, projected, runtime,
        issuedStore, requirement, presentation, r.f.instances, configuration) shouldBe a[Consequence.Failure[?]]
      r.f.stored shouldBe Some(r.active)
      take(uow.commit())
      r.f.stored shouldBe Some(projected)
      projected.history.take(7) shouldBe r.active.history
      projected.currentProgression shouldBe Some(ProgressionReference("sync.MergeValidated"))
      projected.lifecycle shouldBe Lifecycle.Active
      take(runtime.claimC(pending.continuation.continuationId))
      val issued = take(RepositorySyncMergeReviewBoundary.issueRecoveredWorkOrderC(pending,
        r.f.stored.get, runtime, issuedStore, requirement, presentation, r.f.instances, configuration))
      val order = issued.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncMergeReviewRequest]]
      order.request.requiredOperation shouldBe StateMachineRequiredOperationIdentity("review-merge-capability")
      order.request.completionOperation shouldBe StateMachineOperationIdentity("RepositorySyncService", "submitMergeReview")
      order.request.resultType shouldBe Some(StateMachineResultTypeReference("RepositorySyncMergeReviewResult"))
      order.request.input shouldBe Some(pending.input)
      order.request.expectedRevision shouldBe StateMachineRevision("8")
      val wire = take(WorkflowWorkOrderJsonV1.encodeC(issued, RepositorySyncMergeReviewBoundary.inputCodec))
      take(WorkflowWorkOrderJsonV1.decodeC(wire, RepositorySyncMergeReviewBoundary.inputCodec)) shouldBe issued
      wire should not include "claimId"
      val inputJson = RepositorySyncMergeReviewBoundary.inputCodec.encode(pending.input.value)
      RepositorySyncMergeReviewBoundary.inputCodec.decode(inputJson.mapObject(_.add("nextState", io.circe.Json.fromString("Converged")))).isLeft shouldBe true
      take(RepositorySyncMergeReviewBoundary.issueRecoveredWorkOrderC(pending, projected, runtime,
        issuedStore, requirement, presentation, r.f.instances, configuration)) shouldBe issued
      take(issuedStore.loadC(pending.continuation.continuationId)) shouldBe Some(issued)
      r.f.appends shouldBe 8
    }

    "leave failed suspension persistence unclaimable and retain validated revision seven" in {
      val r = new ReviewFixture
      val pending = r.pending
      val runtime = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory)
      val uow = unitOfWork()
      take(runtime.stageSuspensionAfterC(uow, pending.continuation,
        () => Consequence.stateConflict("planned suspension write failure")))
      uow.commit() shouldBe a[Consequence.Failure[?]]
      runtime.claimC(pending.continuation.continuationId) shouldBe a[Consequence.Failure[?]]
      r.f.stored shouldBe Some(r.active)
      r.f.appends shouldBe 7
    }

    "refuse an already published and claimed continuation whose suspension was not saved" in {
      val r = new ReviewFixture
      val pending = r.pending
      val projected = take(RepositorySyncMergeReviewBoundary.suspendedRecordC(pending, r.active))
      val runtime = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory)
      val store = new IssuedWorkOrderPersistence.InMemory[RepositorySyncMergeReviewRequest]
      val uow = unitOfWork()
      take(runtime.stageSuspensionC(uow, pending.continuation))
      take(uow.commit())
      take(runtime.claimC(pending.continuation.continuationId))
      RepositorySyncMergeReviewBoundary.issueRecoveredWorkOrderC(pending, projected, runtime,
        store, requirement, presentation, r.f.instances, configuration) shouldBe a[Consequence.Failure[?]]
      take(store.loadC(pending.continuation.continuationId)) shouldBe None
      r.f.stored shouldBe Some(r.active)
      r.f.appends shouldBe 7
    }

    "recover the exact claimed WorkOrder after issued-store failure without another history append" in {
      val r = new ReviewFixture
      val pending = r.pending
      val projected = take(RepositorySyncMergeReviewBoundary.suspendedRecordC(pending, r.active))
      val continuationStore = new ContinuationRuntimePersistence.InMemory
      val runtime = new PersistentContinuationRuntime(continuationStore)
      val uow = unitOfWork()
      take(runtime.stageSuspensionAfterC(uow, pending.continuation, () =>
        r.f.instances.append(configuration, r.active.identity, InstanceRevision(7L), projected.history.last).map(_ => ())))
      take(uow.commit())
      take(runtime.claimC(pending.continuation.continuationId))
      val backing = new IssuedWorkOrderPersistence.InMemory[RepositorySyncMergeReviewRequest]
      var failOnce = true
      val issuedStore = new IssuedWorkOrderPersistence[RepositorySyncMergeReviewRequest] {
        def loadC(identity: org.goldenport.cncf.workflow.ContinuationIdentity) = backing.loadC(identity)
        def putIfAbsentC(value: WorkflowInteraction[RepositorySyncMergeReviewRequest, Nothing]) =
          if (failOnce) { failOnce = false; Consequence.stateConflict("planned issued-store failure") }
          else backing.putIfAbsentC(value)
      }
      RepositorySyncMergeReviewBoundary.issueRecoveredWorkOrderC(pending, projected, runtime,
        issuedStore, requirement, presentation, r.f.instances, configuration) shouldBe a[Consequence.Failure[?]]
      take(backing.loadC(pending.continuation.continuationId)) shouldBe None
      val recovered = new PersistentContinuationRuntime(continuationStore)
      val issued = take(RepositorySyncMergeReviewBoundary.issueRecoveredWorkOrderC(pending, projected,
        recovered, issuedStore, requirement, presentation, r.f.instances, configuration))
      take(RepositorySyncMergeReviewBoundary.issueRecoveredWorkOrderC(pending, projected, recovered,
        issuedStore, requirement, presentation, r.f.instances, configuration)) shouldBe issued
      r.f.stored shouldBe Some(projected)
      r.f.appends shouldBe 8
    }

    "refuse altered or duplicate suspension and incompatible WorkOrder requirements" in {
      val r = new ReviewFixture
      val pending = r.pending
      val projected = take(RepositorySyncMergeReviewBoundary.suspendedRecordC(pending, r.active))
      RepositorySyncMergeReviewBoundary.suspendedRecordC(pending, projected) shouldBe a[Consequence.Failure[?]]
      RepositorySyncMergeReviewBoundary.suspendedRecordC(null, r.active) shouldBe a[Consequence.Failure[?]]
      val runtime = new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory)
      val store = new IssuedWorkOrderPersistence.InMemory[RepositorySyncMergeReviewRequest]
      Vector(projected.copy(correlation = CorrelationReference("other")),
        projected.copy(suspension = None), r.active).foreach(record =>
        RepositorySyncMergeReviewBoundary.issueRecoveredWorkOrderC(pending, record, runtime, store,
          requirement, presentation, r.f.instances, configuration) shouldBe a[Consequence.Failure[?]])
      Vector(requirement.copy(capabilities = Vector(CapabilityRequirement("other"))),
        requirement.copy(reviewRequired = false), null).foreach(req =>
        RepositorySyncMergeReviewBoundary.issueRecoveredWorkOrderC(pending, projected, runtime, store,
          req, presentation, r.f.instances, configuration) shouldBe a[Consequence.Failure[?]])
      r.f.appends shouldBe 7
    }

    "reuse fresh full review evidence without publishing duplicate semantic work" in {
      val r = new ReviewFixture
      val target = r.submission.candidate.reference
      val review = R.Review(target.revision, now.minusSeconds(1),
        R.Disposition.Accept, Vector.empty, "full merge reviewed")
      val evidence = take(R.evidenceC("review-evidence", target, review, R.Scope.FullMerge, "retained-review-receipt"))
      val withEvidence = r.submission.copy(evidence = Vector(evidence))
      val planned = take(r.plan(submitted = withEvidence))
      planned.assessed.accepted shouldBe true
      planned.pending shouldBe None
      take(r.plan(submitted = withEvidence)).assessed shouldBe planned.assessed
      r.plan(submitted = withEvidence, required = r.operation.copy(actionIdentity = "Other")) shouldBe
        a[Consequence.Failure[?]]
      r.f.stored shouldBe Some(r.active)
      r.f.appends shouldBe 7
    }

    "reuse old applicable review while keeping focused coverage and nonaccepting judgments separate" in {
      val r = new ReviewFixture
      val target = r.submission.candidate.reference
      val review = R.Review(target.revision, now.minusSeconds(1),
        R.Disposition.Accept, Vector.empty, "full merge reviewed")
      val full = take(R.evidenceC("review-evidence", target, review, R.Scope.FullMerge, "retained-review-receipt"))
      val old = full.copy(payload = full.payload.copy(value = review.copy(reviewedAt = now.minusSeconds(7200))))
      take(r.plan(submitted = r.submission.copy(evidence = Vector(old)))).assessed.accepted shouldBe true
      val focused = full.copy(scope = full.scope.copy(value = R.Scope.Focused(Vector("src/main.scala"))))
      Vector(focused).foreach { evidence =>
        val planned = take(r.plan(submitted = r.submission.copy(evidence = Vector(evidence))))
        planned.assessed.accepted shouldBe false
        planned.pending.isDefined shouldBe true
      }
      Vector(R.Disposition.Repair, R.Disposition.DecisionNeeded, R.Disposition.Reject).foreach { disposition =>
        val findings = if (disposition == R.Disposition.Repair)
          Vector(R.Finding("finding-1", Vector("src/main.scala"), "repair the changed source")) else Vector.empty
        val evidence = full.copy(payload = full.payload.copy(value = review.copy(disposition = disposition, findings = findings)))
        val planned = take(r.plan(submitted = r.submission.copy(evidence = Vector(evidence))))
        planned.assessed.accepted shouldBe false
        planned.pending shouldBe None
      }
      r.f.appends shouldBe 7
    }

    "refuse malformed submissions and command chains without creating or progressing a Workflow" in {
      val r = new ReviewFixture
      Vector(null, validationCommand.copy(mergeCommand = null), validationCommand.copy(expectedRevision = "7"),
        validationCommand.copy(idempotencyKey = null),
        validationCommand.copy(mergeCommand = mergeCommand.copy(relationCommand = null))).foreach(command =>
        r.plan(command = command) shouldBe a[Consequence.Failure[?]])
      Vector[R.Submission](null, r.submission.copy(candidate = null),
        r.submission.copy(candidate = r.submission.candidate.copy(payload = null))).foreach(submission =>
        r.plan(submitted = submission) shouldBe a[Consequence.Failure[?]])
      r.plan(time = null) shouldBe a[Consequence.Failure[?]]
      r.f.stored shouldBe Some(r.active)
      r.f.appends shouldBe 7
    }
  }

  "RepositorySync reviewed merge push and convergence" should {
    import RepositorySyncPushAssessment as P
    import RepositorySyncConvergenceAssessment as K
    import RepositorySyncMergedConvergenceServiceOperation as O

    "return the typed terminal through public Operation and CLI for clean and dirty merges" in {
      for (dirty <- Vector(false, true); updated <- Vector(false, true)) {
        val f = new MergedTailFixture(dirty, updated)
        f.withSubsystem { subsystem =>
          val wire = scalar(take(subsystem.executeOperationResponse(f.request())))
          val terminal = take(WorkflowTerminalJsonV1.decodeC(wire, RepositorySyncConvergenceJson.payloadCodec))
          terminal.handle.instanceIdentity shouldBe f.committed.instance.identity
          terminal.current shouldBe a[WorkflowContinuation.Terminal[?]]
          val result = terminal.current.asInstanceOf[WorkflowContinuation.Terminal[org.simplemodeling.textus.smworkflow.value.RepositorySyncConvergenceResult]].result.value
          result.outcome shouldBe "SYNCHRONIZED"
          result.tipOid shouldBe f.committed.receipt.commitOid
          result.treeOid shouldBe f.committed.receipt.treeOid
          result.treeDigest shouldBe f.committed.receipt.treeDigest
          val selector = s"${SmWorkflowComponent.name}/RepositorySyncService/admitMergedConvergence"
          take(new CncfRuntime().executeCommandResponse(subsystem,
            Array(selector, "--json", O.encodeCommand(f.command)))).print shouldBe wire
          scalar(take(subsystem.executeOperationResponse(f.request()))) shouldBe wire
        }
        val record = f.c.f.review.f.stored.get
        record.revision shouldBe InstanceRevision(12L)
        record.lifecycle shouldBe Lifecycle.Completed
        record.currentProgression shouldBe None
        record.suspension shouldBe None
        record.history.take(10) shouldBe f.committed.instance.history
        record.history(10).currentProgression shouldBe Some(ProgressionReference("sync.PushAdmitted"))
        record.history(11).lifecycle shouldBe Lifecycle.Completed
        f.c.f.review.f.appends shouldBe 12
        f.c.calls shouldBe 1
        f.c.writes shouldBe 1
      }
    }

    "readmit a saved commit without creating another commit or changing its prefix" in {
      val f = new MergedTailFixture
      val before = f.c.f.review.f.stored.get
      take(RepositorySyncMergeCommitBoundary.readCommittedC(f.c.submitted.resultReference,
        f.ports.review, f.ports.commits, f.ports.committedInspection)).instance shouldBe before
      f.c.f.review.f.stored shouldBe Some(before)
      f.c.calls shouldBe 1
      f.c.pendingLoads shouldBe 1
    }

    "refuse an absent commit history even if its cached receipt exists" in {
      val f = new MergedTailFixture
      f.c.f.review.f.stored = Some(f.c.reviewed)
      f.complete() shouldBe a[Consequence.Failure[?]]
      f.frozenLoads shouldBe 0
      f.c.f.review.f.stored.get.revision shouldBe InstanceRevision(9L)
      f.c.calls shouldBe 1
    }

    "reject missing or changed retained review, commit and historical provenance before push admission" in {
      for (kind <- Vector("review", "commit", "parents", "history")) {
        val f = new MergedTailFixture
        kind match {
          case "review" => f.ports = f.ports.copy(review = f.ports.review.copy(reviews = new RepositorySyncMergeReviewReceiptStore.InMemory))
          case "commit" => f.c.cached = None
          case "parents" => f.c.cached = f.c.cached.map(r => r.copy(orderedParents = r.orderedParents.reverse))
          case _ =>
            val record = f.c.f.review.f.stored.get
            f.c.f.review.f.stored = Some(record.copy(history = record.history.updated(1,
              record.history(1).copy(causation = CausationReference("foreign-boundary")))))
        }
        f.complete() shouldBe a[Consequence.Failure[?]]
        f.frozenLoads shouldBe 0
        f.c.f.review.f.appends shouldBe 10
      }
    }

    "reject foreign references, missing keys and unsupported expected revisions before push evidence lookup" in {
      val f = new MergedTailFixture
      val invalid = Vector(f.command.copy(reviewResultIdentity = "unknown"),
        f.command.copy(reviewResultRevision = "foreign"), f.command.copy(pushIdempotencyKey = ""),
        f.command.copy(idempotencyKey = ""), f.command.copy(expectedRevision = "9"),
        f.command.copy(expectedRevision = "11"), f.command.copy(reviewResultIdentity = null))
      invalid.foreach(value => f.complete(value) shouldBe a[Consequence.Failure[?]])
      f.complete(null) shouldBe a[Consequence.Failure[?]]
      RepositorySyncMergedConvergenceExecution.admitC(f.command, null) shouldBe a[Consequence.Failure[?]]
      f.frozenLoads shouldBe 0
      f.c.f.review.f.appends shouldBe 10
    }

    "keep review freshness mandatory without consuming another Continuation or committing again" in {
      val f = new MergedTailFixture
      f.ports = f.ports.copy(review = f.ports.review.copy(asOf = () => now.plusSeconds(7200)))
      f.complete() shouldBe a[Consequence.Failure[?]]
      f.frozenLoads shouldBe 0
      f.c.calls shouldBe 1
      f.c.f.review.f.appends shouldBe 10
    }

    "reject advanced advertised tips, incomplete or replacement graph and absent independent remote authority" in {
      val transforms: Vector[P.FrozenState => P.FrozenState] = Vector(
        _.copy(advertisedRemoteTipOid = "9" * 40), _.copy(remoteAncestorOfLocal = false),
        _.copy(historyComplete = false), _.copy(replacementHistory = true), _.copy(graphInspectionDigest = "bad"),
        r => r.copy(authority = r.authority.copy(remoteWriteAuthorityAccepted = false)),
        r => r.copy(authority = r.authority.copy(nonForceOnlyAccepted = false)), _.copy(authority = null))
      transforms.foreach { transform =>
        val f = new MergedTailFixture
        f.frozenTransform = transform
        f.complete() shouldBe a[Consequence.Failure[?]]
        f.c.f.review.f.appends shouldBe 10
      }
    }

    "bind every remote authority fact to the exact committed mapping rather than the original relation" in {
      val transforms: Vector[P.Authority => P.Authority] = Vector(
        _.copy(relationDigest = "a" * 64), _.copy(repositoryIdentity = "other"), _.copy(worktreeIdentity = "other"),
        _.copy(localBranch = "other"), _.copy(trackingRemote = "other"), _.copy(trackingBranch = "other"),
        _.copy(remoteEndpointIdentity = "other"), _.copy(repositoryPolicyVersion = "other"),
        _.copy(sourceTipOid = "a" * 40), _.copy(destinationTipBeforeOid = "b" * 40), _.copy(evidenceDigest = "bad"))
      transforms.foreach { transform =>
        val f = new MergedTailFixture
        f.frozenTransform = r => r.copy(authority = transform(r.authority))
        f.complete() shouldBe a[Consequence.Failure[?]]
        f.c.f.review.f.appends shouldBe 10
      }
    }

    "reject a forged push pre-state even when its matching authority is also changed" in {
      val f = new MergedTailFixture
      f.frozenTransform = r => r.copy(before = r.before.copy(remoteEndpointIdentity = "other"),
        authority = r.authority.copy(remoteEndpointIdentity = "other"))
      f.complete() shouldBe a[Consequence.Failure[?]]
      f.c.f.review.f.appends shouldBe 10
      P.assessMergedC(f.committed.copy(after = f.committed.after.copy(
        orderedParents = f.committed.after.orderedParents.reverse)), f.pushes) shouldBe a[Consequence.Failure[?]]
    }

    "refuse force, tags, mirror, other refs and malformed push receipts before appending push history" in {
      val transforms: Vector[P.Receipt => P.Receipt] = Vector(
        _.copy(forceUsed = true), _.copy(tagsIncluded = true), _.copy(mirrorUsed = true),
        _.copy(changedRemoteRefs = Vector("refs/heads/other")), _.copy(changedLocalRefs = Vector("refs/heads/main")),
        _.copy(destinationRemoteRef = "refs/heads/other"), _.copy(sourceLocalRef = "refs/heads/other"),
        _.copy(sourceTipOid = "a" * 40), _.copy(destinationTipBeforeOid = "b" * 40),
        _.copy(destinationTipAfterOid = "a" * 40), _.copy(trackingTipAfterOid = "a" * 40),
        _.copy(frozenStateDigest = "f" * 64), _.copy(endpointPolicyAccepted = false),
        _.copy(completed = false), _.copy(executionDigest = "bad"))
      transforms.foreach { transform =>
        val f = new MergedTailFixture
        f.receiptTransform = transform
        f.complete() shouldBe a[Consequence.Failure[?]]
        f.c.f.review.f.appends shouldBe 10
      }
    }

    "require clean unchanged local head, tree and mapping after push" in {
      val transforms: Vector[RepositorySyncFastForwardAssessment.State => RepositorySyncFastForwardAssessment.State] = Vector(
        _.copy(localHeadOid = "a" * 40), _.copy(localTreeOid = "b" * 40), _.copy(localTreeDigest = "c" * 64),
        _.copy(dirtyPaths = Vector("new.txt")), _.copy(indexShape = "STAGED"), _.copy(detachedHead = true),
        _.copy(pendingOperations = Vector("merge")), _.copy(trackingTipOid = "a" * 40),
        _.copy(remoteEndpointIdentity = "other"), _.copy(evidenceBinding = "a" * 64), _.copy(inspectionDigest = "bad"))
      transforms.foreach { transform =>
        val f = new MergedTailFixture
        f.postTransform = transform
        f.complete() shouldBe a[Consequence.Failure[?]]
        f.c.f.review.f.appends shouldBe 10
      }
    }

    "preserve admitted push but never manufacture a terminal when verification evidence is missing" in {
      for (stage <- Vector("fetch", "verification")) {
        val f = new MergedTailFixture
        f.missing = Set(stage)
        f.complete() shouldBe a[Consequence.Failure[?]]
        f.complete() shouldBe a[Consequence.Failure[?]]
        f.c.f.review.f.stored.get.revision shouldBe InstanceRevision(11L)
        f.c.f.review.f.stored.get.lifecycle shouldBe Lifecycle.Active
        f.c.f.review.f.appends shouldBe 11
        f.missing = Set.empty
        take(f.complete()).current shouldBe a[WorkflowContinuation.Terminal[?]]
        f.c.f.review.f.appends shouldBe 12
        f.c.calls shouldBe 1
      }
    }

    "refuse missing push evidence without advancing history" in {
      for (stage <- Vector("freeze", "push", "post")) {
        val f = new MergedTailFixture
        f.missing = Set(stage)
        f.complete() shouldBe a[Consequence.Failure[?]]
        f.c.f.review.f.appends shouldBe 10
        f.missing = Set.empty
        take(f.complete())
        f.c.f.review.f.appends shouldBe 12
      }
    }

    "reject a verification fetch that changes local content, another ref or the selected remote target" in {
      val transforms: Vector[K.FetchReceipt => K.FetchReceipt] = Vector(
        _.copy(fetchedTipOid = "a" * 40), _.copy(trackingTipAfterOid = "a" * 40),
        _.copy(localTipAfterOid = "a" * 40), _.copy(localTreeAfterOid = "b" * 40),
        _.copy(localTreeAfterDigest = "c" * 64), _.copy(remoteRef = "refs/heads/other"),
        _.copy(targetedTrackingRef = "refs/remotes/other/main"), _.copy(changedRefs = Vector("refs/heads/main")),
        _.copy(endpointPolicyAccepted = false), _.copy(completed = false), _.copy(executionDigest = "bad"),
        r => r.copy(before = r.before.copy(evidenceBinding = "a" * 64)))
      transforms.foreach { transform =>
        val f = new MergedTailFixture
        f.fetchTransform = transform
        f.complete() shouldBe a[Consequence.Failure[?]]
        f.c.f.review.f.appends shouldBe 11
        f.c.f.review.f.stored.get.lifecycle shouldBe Lifecycle.Active
      }
    }

    "require independent 0/0 complete graph and clean equal tips rather than equal-looking tips alone" in {
      val transforms: Vector[K.Verification => K.Verification] = Vector(
        _.copy(localAhead = 1), _.copy(remoteAhead = 1), _.copy(historyComplete = false),
        _.copy(replacementHistory = true), _.copy(graphInspectionDigest = "bad"),
        r => r.copy(state = r.state.copy(trackingTipOid = "a" * 40)),
        r => r.copy(state = r.state.copy(localHeadOid = "a" * 40)),
        r => r.copy(state = r.state.copy(localTreeDigest = "a" * 64)),
        r => r.copy(state = r.state.copy(dirtyPaths = Vector("new.txt"))),
        r => r.copy(state = r.state.copy(indexShape = "STAGED")),
        r => r.copy(state = r.state.copy(pendingOperations = Vector("merge"))),
        r => r.copy(state = r.state.copy(remoteEndpointIdentity = "other")),
        r => r.copy(state = r.state.copy(evidenceBinding = "a" * 64)))
      transforms.foreach { transform =>
        val f = new MergedTailFixture
        f.verificationTransform = transform
        f.complete() shouldBe a[Consequence.Failure[?]]
        f.c.f.review.f.appends shouldBe 11
      }
    }

    "recover a push history append failure before or after save without another commit or duplicate history" in {
      for (after <- Vector(false, true)) {
        val f = new MergedTailFixture
        f.failingAppend(10L, after)
        f.complete() shouldBe a[Consequence.Failure[?]]
        f.c.f.review.f.stored.get.revision shouldBe InstanceRevision(if (after) 11L else 10L)
        take(f.complete()).current shouldBe a[WorkflowContinuation.Terminal[?]]
        f.c.f.review.f.appends shouldBe 12
        f.c.calls shouldBe 1
      }
    }

    "recover a terminal append failure before or after save without duplicating either tail entry" in {
      for (after <- Vector(false, true)) {
        val f = new MergedTailFixture
        f.failingAppend(11L, after)
        f.complete() shouldBe a[Consequence.Failure[?]]
        f.c.f.review.f.stored.get.revision shouldBe InstanceRevision(if (after) 12L else 11L)
        val terminal = take(f.complete())
        take(f.complete()) shouldBe terminal
        f.c.f.review.f.appends shouldBe 12
        f.c.calls shouldBe 1
      }
    }

    "bind completed history to the original push and convergence keys and all retained evidence" in {
      val f = new MergedTailFixture
      val terminal = take(f.complete())
      val record = f.c.f.review.f.stored.get
      f.complete(f.command.copy(pushIdempotencyKey = "foreign")) shouldBe a[Consequence.Failure[?]]
      f.complete(f.command.copy(idempotencyKey = "foreign")) shouldBe a[Consequence.Failure[?]]
      f.verificationTransform = _.copy(graphInspectionDigest = "9" * 64)
      f.complete() shouldBe a[Consequence.Failure[?]]
      f.verificationTransform = identity
      take(f.complete()) shouldBe terminal
      f.c.f.review.f.stored shouldBe Some(record)
      f.c.f.review.f.appends shouldBe 12
    }

    "refuse a WorkflowInstance change during verification and recover only the exact prior history" in {
      val f = new MergedTailFixture
      var original: Option[InstanceRecord] = None
      f.onVerification = () => {
        val record = f.c.f.review.f.stored.get
        original = Some(record)
        f.c.f.review.f.stored = Some(record.copy(causation = CausationReference("foreign"),
          history = record.history.init :+ record.history.last.copy(causation = CausationReference("foreign"))))
      }
      f.complete() shouldBe a[Consequence.Failure[?]]
      f.c.f.review.f.appends shouldBe 11
      f.c.f.review.f.stored = original
      f.onVerification = () => ()
      take(f.complete())
      f.c.f.review.f.appends shouldBe 12
    }

    "strictly decode the generated command without admitting next-state or remote authority fields" in {
      val f = new MergedTailFixture
      val wire = O.encodeCommand(f.command)
      take(O.decodeCommandC(wire)) shouldBe f.command
      val json = io.circe.parser.parse(wire).toOption.get
      for (field <- Vector("nextState", "force", "remoteWriteAuthorityAccepted", "claimToken"))
        O.decodeCommandC(json.mapObject(_.add(field, io.circe.Json.fromString("anything"))).noSpaces) shouldBe a[Consequence.Failure[?]]
      O.decodeCommandC(null) shouldBe a[Consequence.Failure[?]]
      O.decodeCommandC(wire.replace(O.wireSchema, "unknown")) shouldBe a[Consequence.Failure[?]]
      O.decodeCommandC(O.encodeCommand(f.command.copy(expectedRevision = "12"))) shouldBe a[Consequence.Failure[?]]
      O.bindC(null) shouldBe a[Consequence.Failure[?]]
      O.bindC(f.ports.copy(convergence = null)) shouldBe a[Consequence.Failure[?]]
      f.c.f.review.f.appends shouldBe 10
    }
  }

  "RepositorySync reviewed two-parent commit" should {
    import RepositorySyncMergeCommitBoundary as C

    "select retained typed review evidence without comparing or replacing the proposed body" in {
      Given("Spec: docs/spec/repository-sync-merge-review-applicability.md; Rule: R2; Example: E2; an occupied review-result reference")
      val f = new CommitFixture
      val record = take(f.f.reviews.loadC(f.submitted.resultReference)).get
      record.submitted shouldBe f.submitted
      record.retained shouldBe f.f.retained.get
      take(f.f.reviews.putIfAbsentC(record)) shouldBe record
      When("a later proposal changes only review rationale")
      val selected = take(f.f.reviews.putIfAbsentC(record.copy(submitted = f.submitted.copy(result = f.submitted.result.copy(
        value = f.submitted.result.value.copy(rationale = "Replacement"))))))
      Then("the saved input is selected without replacement or complete-body equality")
      selected shouldBe record
      f.f.review.f.appends shouldBe 9
      f.writes shouldBe 0
    }

    "commit the exact reviewed tree and ordered parents once for clean and dirty starts" in {
      Vector(false, true).foreach { dirty =>
        val f = new CommitFixture(dirty)
        val committed = take(f.commit())
        val request = committed.receipt.request
        request.pendingHeadOid shouldBe f.pending.state.localHeadOid
        request.orderedParents shouldBe f.f.input.orderedParents
        request.treeOid shouldBe f.pending.resultTreeOid
        request.treeDigest shouldBe f.pending.resultTreeDigest
        request.validationDigest shouldBe f.f.input.validationDigest
        request.reviewReference shouldBe f.submitted.resultReference
        committed.instance.history.take(9) shouldBe f.reviewed.history
        committed.instance.revision shouldBe InstanceRevision(10L)
        committed.instance.currentProgression shouldBe Some(ProgressionReference("sync.MergeValidated"))
        committed.instance.lifecycle shouldBe Lifecycle.Active
        committed.instance.suspension shouldBe None
        committed.after.state.trackingTipOid shouldBe f.pending.state.trackingTipOid
        committed.after.state.localHeadOid shouldBe committed.receipt.commitOid
        committed.interaction.current shouldBe a[WorkflowContinuation.Wait]
        take(f.commit()) shouldBe committed
        f.calls shouldBe 1
        f.writes shouldBe 1
        f.pendingLoads shouldBe 1
        f.f.review.f.appends shouldBe 10
      }
    }

    "recover a retained commit after its response was lost without committing again" in {
      val f = new CommitFixture
      f.uncertainCommit = true
      f.commit() shouldBe a[Consequence.Failure[?]]
      f.cached should not be empty
      f.f.review.f.stored shouldBe Some(f.reviewed)
      take(f.commit()).instance.revision shouldBe InstanceRevision(10L)
      f.calls shouldBe 1
      f.writes shouldBe 1
      f.f.review.f.appends shouldBe 10
    }

    "recover a failed history append from the retained commit receipt" in {
      val f = new CommitFixture
      var failOnce = true
      val backing = f.f.review.f.instances
      val instances = new WorkflowInstancePersistence {
        def create(c: Configuration, r: InstanceRecord) = backing.create(c, r)
        def load(c: Configuration, i: InstanceIdentity) = backing.load(c, i)
        def append(c: Configuration, i: InstanceIdentity, revision: InstanceRevision, entry: HistoryEntry) =
          if (failOnce) { failOnce = false; Consequence.stateConflict("planned commit history failure") }
          else backing.append(c, i, revision, entry)
      }
      val ports = f.f.ports.copy(instances = instances)
      f.commit(ports = ports) shouldBe a[Consequence.Failure[?]]
      f.f.review.f.stored shouldBe Some(f.reviewed)
      take(f.commit(ports = ports)).instance.revision shouldBe InstanceRevision(10L)
      f.writes shouldBe 1
      f.calls shouldBe 1
      f.f.review.f.appends shouldBe 10
    }

    "recover an uncertain history append without duplicating revision ten" in {
      val f = new CommitFixture
      var failOnce = true
      val backing = f.f.review.f.instances
      val instances = new WorkflowInstancePersistence {
        def create(c: Configuration, r: InstanceRecord) = backing.create(c, r)
        def load(c: Configuration, i: InstanceIdentity) = backing.load(c, i)
        def append(c: Configuration, i: InstanceIdentity, revision: InstanceRevision, entry: HistoryEntry) =
          backing.append(c, i, revision, entry).flatMap { value =>
            if (failOnce) { failOnce = false; Consequence.stateConflict("uncertain history append response") }
            else Consequence.success(value)
          }
      }
      val ports = f.f.ports.copy(instances = instances)
      f.commit(ports = ports) shouldBe a[Consequence.Failure[?]]
      f.f.review.f.stored.get.revision shouldBe InstanceRevision(10L)
      take(f.commit(ports = ports)).instance.revision shouldBe InstanceRevision(10L)
      f.writes shouldBe 1
      f.f.review.f.appends shouldBe 10
    }

    "refuse missing or nonaccepting reviews while allowing otherwise applicable old review" in {
      Given("a recorded full merge review and actual commit fixture with no product TTL")
      Vector("repair", "decision-needed", "reject").foreach { decision =>
        val f = new CommitFixture(decision = decision)
        f.commit() shouldBe a[Consequence.Failure[?]]
        f.writes shouldBe 0
        f.f.review.f.stored shouldBe Some(f.reviewed)
      }
      val f = new CommitFixture
      f.commit(ContextReference("missing", f.submitted.resultReference.revision)) shouldBe a[Consequence.Failure[?]]
      f.commit(ports = f.f.ports.copy(reviews = null)) shouldBe a[Consequence.Failure[?]]
      f.writes shouldBe 0
      f.calls shouldBe 0
      When("otherwise applicable review is consumed after its former expiry")
      take(f.commit(ports = f.f.ports.copy(asOf = () => now.plusSeconds(7200))))
      Then("time alone requires no new review and the actual authorized commit executes once")
      f.writes shouldBe 1
      f.calls shouldBe 1
    }

    "consume selected applicable review rationale without a complete-Result hash certificate" in {
      Given("a saved accepted review with changed diagnostic rationale but the actual target and Result reference")
      val f = new CommitFixture
      val original = take(f.f.reviews.loadC(f.submitted.resultReference)).get
      val forged = original.copy(submitted = original.submitted.copy(result = original.submitted.result.copy(
        value = original.submitted.result.value.copy(rationale = "Different accepted review"))))
      val store = new RepositorySyncMergeReviewReceiptStore {
        def loadC(reference: ContextReference) = Consequence.success(Some(forged))
        def putIfAbsentC(record: RepositorySyncMergeReviewReceiptStore.Record) = Consequence.success(record)
      }
      When("the commit boundary consumes that selected review")
      val committed = take(f.commit(ports = f.f.ports.copy(reviews = store)))
      Then("no complete-Result digest or body equality is required while real commit facts are checked")
      committed.receipt.request.productElementNames.toSet should not contain "reviewReceiptDigest"
      committed.receipt.request.idempotencyKey should startWith("repository-sync-two-parent-commit:")
      f.calls shouldBe 1
      f.writes shouldBe 1
    }

    "refuse candidate command and completed-history drift before committing" in {
      val f = new CommitFixture
      val seed = f.f.retained.get
      f.f.retained = Some(seed.copy(command = seed.command.copy(idempotencyKey = "other")))
      f.commit() shouldBe a[Consequence.Failure[?]]
      f.f.retained = Some(seed)
      f.f.review.f.stored = Some(f.reviewed.copy(history = f.reviewed.history.updated(1,
        f.reviewed.history(1).copy(causation = CausationReference("different-boundary")))))
      f.commit() shouldBe a[Consequence.Failure[?]]
      f.f.review.f.stored = Some(f.reviewed)
      take(f.commit())
      f.calls shouldBe 1
    }

    "refuse a changed live pending head index or tree before committing" in {
      val changes: Vector[RepositorySyncMergeAssessment.PendingState => RepositorySyncMergeAssessment.PendingState] = Vector(
        p => p.copy(state = p.state.copy(localHeadOid = "0" * 40)),
        p => p.copy(resultTreeOid = "0" * 40), p => p.copy(resultTreeDigest = "0" * 64),
        p => p.copy(state = p.state.copy(indexShape = "EMPTY")), p => p.copy(mergeHeads = Vector.empty))
      changes.foreach { change =>
        val f = new CommitFixture
        f.pendingTransform = change
        f.commit() shouldBe a[Consequence.Failure[?]]
        f.calls shouldBe 0
        f.f.review.f.stored shouldBe Some(f.reviewed)
      }
    }

    "refuse wrong commit receipts before saving commit history" in {
      val changes: Vector[C.Receipt => C.Receipt] = Vector(
        r => r.copy(orderedParents = r.orderedParents.reverse), r => r.copy(orderedParents = Vector(r.orderedParents.head)),
        r => r.copy(treeOid = "0" * 40), r => r.copy(treeDigest = "0" * 64),
        r => r.copy(commitOid = r.orderedParents.head), r => r.copy(changedRefs = Vector("refs/heads/other")),
        r => r.copy(completed = false), r => r.copy(executionDigest = "bad"),
        r => r.copy(request = r.request.copy(candidateRevision = "0" * 64)))
      changes.foreach { change =>
        val f = new CommitFixture
        f.receiptTransform = change
        f.commit() shouldBe a[Consequence.Failure[?]]
        f.f.review.f.stored shouldBe Some(f.reviewed)
        f.f.review.f.appends shouldBe 9
      }
      C.receiptDigestC(null) shouldBe a[Consequence.Failure[?]]
    }

    "refuse dirty mismatched or unrelated post-commit state before saving history" in {
      val changes: Vector[C.CommittedState => C.CommittedState] = Vector(
        s => s.copy(orderedParents = s.orderedParents.reverse),
        s => s.copy(state = s.state.copy(localHeadOid = "0" * 40)),
        s => s.copy(state = s.state.copy(localTreeDigest = "0" * 64)),
        s => s.copy(state = s.state.copy(dirtyPaths = Vector("unexpected.txt"))),
        s => s.copy(state = s.state.copy(pendingOperations = Vector("MERGE"))),
        s => s.copy(state = s.state.copy(trackingTipOid = "0" * 40)),
        s => s.copy(state = s.state.copy(remoteEndpointIdentity = "other")))
      changes.foreach { change =>
        val f = new CommitFixture
        f.afterTransform = change
        f.commit() shouldBe a[Consequence.Failure[?]]
        f.f.review.f.stored shouldBe Some(f.reviewed)
        f.writes shouldBe 1
      }
    }

    "require a durably retained receipt and leave history unchanged on provider failure" in {
      val f = new CommitFixture
      f.failCommit = true
      f.commit() shouldBe a[Consequence.Failure[?]]
      f.writes shouldBe 0
      f.f.review.f.stored shouldBe Some(f.reviewed)
      val missing = new CommitFixture
      missing.retainReceipt = false
      missing.commit() shouldBe a[Consequence.Failure[?]]
      missing.f.review.f.stored shouldBe Some(missing.reviewed)
      missing.writes shouldBe 1
    }

    "refuse committed history without its receipt and changed cached receipts on replay" in {
      val f = new CommitFixture
      val saved = take(f.commit())
      val original = f.cached
      f.cached = None
      f.commit() shouldBe a[Consequence.Failure[?]]
      f.cached = original.map(r => r.copy(orderedParents = r.orderedParents.reverse))
      f.commit() shouldBe a[Consequence.Failure[?]]
      f.cached = original
      take(f.commit()) shouldBe saved
      f.calls shouldBe 1
      f.f.review.f.appends shouldBe 10
    }

    "recover typed review retention after failure without repeating one-shot resume" in {
      val f = new PublicReviewFixture
      var failOnce = true
      val store = new RepositorySyncMergeReviewReceiptStore {
        def loadC(reference: ContextReference) = f.reviews.loadC(reference)
        def putIfAbsentC(record: RepositorySyncMergeReviewReceiptStore.Record) =
          if (failOnce) { failOnce = false; Consequence.stateConflict("planned retention failure") }
          else f.reviews.putIfAbsentC(record)
      }
      f.ports = f.ports.copy(reviews = store)
      val submitted = f.withSubsystem { subsystem =>
        val result = f.result(f.issue(subsystem))
        subsystem.executeOperationResponse(f.completion(result)) shouldBe a[Consequence.Failure[?]]
        result
      }
      val completed = f.review.f.stored.get
      completed.revision shouldBe InstanceRevision(9L)
      take(f.reviews.loadC(submitted.resultReference)) shouldBe None
      val retained = take(RepositorySyncMergeReviewServiceOperation.retainCompletedC(submitted, completed, f.ports))
      take(f.reviews.loadC(submitted.resultReference)) shouldBe Some(retained)
      take(RepositorySyncMergeReviewServiceOperation.retainCompletedC(submitted, completed, f.ports)) shouldBe retained
      f.review.f.appends shouldBe 9
    }
  }

  "RepositorySync merge Decision projection" should {
    import RepositorySyncMergeReviewServiceOperation as S
    import RepositorySyncMergeDecision as D

    "project a saved decision-needed review through public Service and CLI without granting commit authority" in {
      Vector(false, true).foreach { cli =>
        val f = new PublicReviewFixture
        val port = new MergeDecisionPort
        f.ports = f.ports.copy(decisions = Some(port))
        f.withSubsystem { subsystem =>
          val work = f.issue(subsystem)
          val submitted = f.result(work, "decision-needed")
          val resultWire = take(WorkflowResultJsonV1.encodeC(submitted, RepositorySyncMergeReviewResultAdapter.payloadCodec))
          val wire = if (cli) take(new CncfRuntime().executeCommandResponse(subsystem,
            Array(s"${SmWorkflowComponent.name}/RepositorySyncService/submitMergeReview", "--json", resultWire))).print
            else scalar(take(subsystem.executeOperationResponse(f.completion(submitted))))
          val decision = take(WorkflowDecisionJsonV1.decodeC(wire, D.inputCodec))
          val request = decision.current.asInstanceOf[WorkflowContinuation.Decision[D.Input]].request
          decision.handle shouldBe work.handle
          request.runId shouldBe submitted.runId
          request.expectedRevision shouldBe StateMachineRevision("9")
          request.continuationId should not be submitted.continuationId
          request.resultType shouldBe None
          request.input.get.value.candidate shouldBe f.input
          request.input.get.value.reviewResultReference shouldBe submitted.resultReference
          request.input.get.value.productElementNames.toSet shouldBe Set("candidate", "reviewResultReference")
          // Rehydrated admitted facts, rather than opaque binding object identity.
          val completed = f.review.f.stored.get
          val rebound = take(RepositorySyncDefinitionBinding.bindC)
          rebound should not be completed.definition
          f.review.f.stored = Some(completed.copy(definition = rebound))
          take(S.projectDecisionC(submitted.resultReference, f.ports)) shouldBe wire
          WorkflowWorkOrderJsonV1.decodeC(wire, D.inputCodec) shouldBe a[Consequence.Failure[?]]
          WorkflowProtocolV1.admitResultC(decision, submitted) shouldBe a[Consequence.Failure[?]]
          subsystem.executeOperationResponse(Request.of(component = SmWorkflowComponent.name,
            service = "HumanBoundaryFixture", operation = "recordMergeDecision")) shouldBe a[Consequence.Failure[?]]
          subsystem.executeOperationResponse(f.completion(submitted)) shouldBe a[Consequence.Failure[?]]
          f.review.f.stored.get.revision shouldBe InstanceRevision(9L)
          f.review.f.stored.get.suspension shouldBe None
          f.review.f.stored.get.history.take(7) shouldBe f.review.active.history
          f.review.f.appends shouldBe 9
          port.calls shouldBe 2
        }
      }
    }

    "keep accept repair reject and unconfigured legacy decision-needed as WAIT" in {
      Vector("accept", "repair", "reject", "decision-needed").foreach { disposition =>
        val f = new PublicReviewFixture
        val port = new MergeDecisionPort
        if (disposition != "decision-needed") f.ports = f.ports.copy(decisions = Some(port))
        val submitted = f.withSubsystem { subsystem =>
          val result = f.result(f.issue(subsystem), disposition)
          take(WorkflowWaitJsonV1.decodeC(scalar(take(subsystem.executeOperationResponse(f.completion(result))))))
          result
        }
        S.projectDecisionC(submitted.resultReference, f.ports) shouldBe a[Consequence.Failure[?]]
        port.calls shouldBe 0
        f.review.f.appends shouldBe 9
      }
      val f = new PublicReviewFixture
      S.bindCompletionC(f.ports.copy(decisions = null)) shouldBe a[Consequence.Failure[?]]
      S.bindCompletionC(f.ports.copy(decisions = Some(null))) shouldBe a[Consequence.Failure[?]]
    }

    "reject invalid semantic review evidence before projection or one-shot claim" in {
      val f = new PublicReviewFixture
      val port = new MergeDecisionPort
      f.ports = f.ports.copy(decisions = Some(port))
      f.withSubsystem { subsystem =>
        val submitted = f.result(f.issue(subsystem), "decision-needed")
        Vector(submitted.result.value.copy(candidateRevision = "0" * 64),
          submitted.result.value.copy(reviewedAt = "invalid-event-time"),
          submitted.result.value.copy(decision = "approve-commit")).foreach { value =>
          subsystem.executeOperationResponse(f.completion(submitted.copy(result = submitted.result.copy(value = value)))) shouldBe
            a[Consequence.Failure[?]]
        }
        f.review.f.stored.get.revision shouldBe InstanceRevision(8L)
        port.calls shouldBe 0
        take(subsystem.executeOperationResponse(f.completion(submitted)))
        port.calls shouldBe 1
        f.review.f.appends shouldBe 9
      }
    }

    "recover a failed post-resume projection from the retained review without repeating completion" in {
      val f = new PublicReviewFixture
      val unavailable = new D.Port {
        def resolveC(intent: D.Intent) = Consequence.configurationInvalid("Human boundary unavailable")
      }
      f.ports = f.ports.copy(decisions = Some(unavailable))
      f.withSubsystem { subsystem =>
        val submitted = f.result(f.issue(subsystem), "decision-needed")
        subsystem.executeOperationResponse(f.completion(submitted)) shouldBe a[Consequence.Failure[?]]
        f.review.f.stored.get.revision shouldBe InstanceRevision(9L)
        take(f.reviews.loadC(submitted.resultReference)).get.submitted shouldBe submitted
        val recovered = f.ports.copy(decisions = Some(new MergeDecisionPort))
        take(WorkflowDecisionJsonV1.decodeC(take(S.projectDecisionC(submitted.resultReference, recovered)), D.inputCodec))
        subsystem.executeOperationResponse(f.completion(submitted)) shouldBe a[Consequence.Failure[?]]
        f.review.f.appends shouldBe 9
      }
    }

    "refuse host intent drift semantic SPI reuse and malformed metadata without changing saved history" in {
      type Q = ContinuationRequest[D.Input]
      val semantic = take(RepositorySyncDefinitionBinding.reviewOperationC)
      val changes: Vector[Q => Q] = Vector(
        q => q.copy(runId = StateMachineRunIdentity("other")),
        q => q.copy(expectedRevision = StateMachineRevision("8")),
        q => q.copy(input = q.input.map(v => v.copy(value = v.value.copy(reviewResultReference = ContextReference("foreign-review", "1"))))),
        q => q.copy(context = q.context.copy(snapshot = q.context.snapshot.copy(modelRevision = Some("foreign-target")))),
        q => q.copy(context = q.context.copy(requiredFacts = Vector.empty)),
        q => q.copy(requiredOperation = semantic.identity), q => q.copy(operation = semantic.operation),
        q => q.copy(completionOperation = semantic.operation),
        q => q.copy(completionOperation = StateMachineOperationIdentity("RepositorySyncService", "submitMergeReview")),
        q => q.copy(resultType = Some(StateMachineResultTypeReference("RepositorySyncMergeReviewResult"))), q => q.copy(resultType = null),
        q => q.copy(continuationId = null), q => q.copy(context = null), q => null)
      changes.foreach { change =>
        val f = new CommitFixture(decision = "decision-needed")
        val port = new MergeDecisionPort(change)
        S.projectDecisionC(f.submitted.resultReference, f.f.ports.copy(decisions = Some(port))) shouldBe a[Consequence.Failure[?]]
        port.calls shouldBe 1
        f.f.review.f.stored shouldBe Some(f.reviewed)
        f.f.review.f.appends shouldBe 9
        f.commit() shouldBe a[Consequence.Failure[?]]
        f.calls shouldBe 0
        f.writes shouldBe 0
      }
      val f = new CommitFixture(decision = "decision-needed")
      val reused = new MergeDecisionPort(q => q.copy(continuationId = f.submitted.continuationId))
      S.projectDecisionC(f.submitted.resultReference, f.f.ports.copy(decisions = Some(reused))) shouldBe a[Consequence.Failure[?]]
      reused.calls shouldBe 1
      f.f.review.f.appends shouldBe 9
    }

    "consume old and diagnostic review metadata while refusing missing actual scope before host lookup" in {
      Given("a saved decision-needed review and current completed revision")
      val f = new CommitFixture(decision = "decision-needed")
      val port = new MergeDecisionPort
      val ports = f.f.ports.copy(decisions = Some(port))
      S.projectDecisionC(ContextReference("missing", f.submitted.resultReference.revision), ports) shouldBe a[Consequence.Failure[?]]
      When("the saved review is projected after its former expiry")
      take(S.projectDecisionC(f.submitted.resultReference, ports.copy(asOf = () => now.plusSeconds(7200))))
      val original = take(f.f.reviews.loadC(f.submitted.resultReference)).get
      val forged = original.copy(submitted = original.submitted.copy(result = original.submitted.result.copy(
        value = original.submitted.result.value.copy(rationale = "Substituted review"))))
      val store = new RepositorySyncMergeReviewReceiptStore {
        def loadC(ref: ContextReference) = Consequence.success(Some(forged))
        def putIfAbsentC(record: RepositorySyncMergeReviewReceiptStore.Record) = Consequence.success(record)
      }
      When("the selected review changes only diagnostic rationale")
      take(S.projectDecisionC(f.submitted.resultReference, ports.copy(reviews = store)))
      Then("neither time nor full-body equality is a restart gate")
      port.calls shouldBe 2
      val seed = f.f.retained.get
      f.f.retained = Some(seed.copy(command = seed.command.copy(idempotencyKey = "other")))
      S.projectDecisionC(f.submitted.resultReference, ports) shouldBe a[Consequence.Failure[?]]
      f.f.retained = Some(seed)
      f.f.review.f.stored = Some(f.reviewed.copy(history = f.reviewed.history.updated(1,
        f.reviewed.history(1).copy(causation = CausationReference("other-boundary")))))
      S.projectDecisionC(f.submitted.resultReference, ports) shouldBe a[Consequence.Failure[?]]
      port.calls shouldBe 2
      f.calls shouldBe 0
    }

    "check actual current revision but do not recertify selected receipt or retained metadata after projection" in {
      Vector("instance", "review", "retained").foreach { race =>
        Given(s"Spec: docs/spec/repository-sync-merge-review-applicability.md; Rule: R2; Example: E3; an admitted Decision projection and a later $race observation")
        val f = new CommitFixture(decision = "decision-needed")
        var replaced = false
        val original = take(f.f.reviews.loadC(f.submitted.resultReference)).get
        val store = new RepositorySyncMergeReviewReceiptStore {
          def loadC(ref: ContextReference) = Consequence.success(if (replaced) None else Some(original))
          def putIfAbsentC(record: RepositorySyncMergeReviewReceiptStore.Record) = Consequence.success(record)
        }
        val port = new MergeDecisionPort(q => {
          race match {
            case "instance" => f.f.review.f.stored = Some(take(f.reviewed.appendC(f.reviewed.revision,
              f.reviewed.history.last.copy(sequence = HistorySequence(10L), revision = InstanceRevision(10L),
                causation = CausationReference("concurrent-transition")))))
            case "review" => replaced = true
            case _ => f.f.retained = None
          }
          q
        })
        When("the host returns a scoped stop-only request")
        val result = S.projectDecisionC(f.submitted.resultReference, f.f.ports.copy(reviews = store, decisions = Some(port)))
        Then("a real revision conflict fails, but selected-input metadata needs no subsequent no-change certificate")
        if (race == "instance") result shouldBe a[Consequence.Failure[?]]
        else take(WorkflowDecisionJsonV1.decodeC(take(result), D.inputCodec))
        port.calls shouldBe 1
        f.f.review.f.appends shouldBe 9
        f.calls shouldBe 0
        f.writes shouldBe 0
      }
    }

    "strictly decode the Decision candidate and review reference without approval or selector fields" in {
      val f = new PublicReviewFixture
      val codec = D.inputCodec
      val input = D.Input(f.input, ContextReference("review-result", f.input.candidateRevision))
      val json = codec.encode(input)
      codec.decode(json) shouldBe Right(input)
      json.asObject.get.keys.toSet should not contain "reviewResultDigest"
      Vector(io.circe.Json.Null, io.circe.Json.True, io.circe.Json.fromString("unusable-proof")).foreach { value =>
        codec.decode(json.mapObject(_.add("reviewResultDigest", value))) shouldBe Right(input)
      }
      Vector(json.mapObject(_.add("approve", io.circe.Json.True)),
        json.mapObject(_.add("nextState", io.circe.Json.fromString("Converged"))),
        json.mapObject(_.add("candidate", json.hcursor.downField("candidate").focus.get
          .mapObject(_.add("commit", io.circe.Json.True)))),
        json.mapObject(_.add("reviewResultReference", io.circe.Json.obj(
          "identity" -> io.circe.Json.fromString("review-result"), "revision" -> io.circe.Json.fromString("r1"),
          "selector" -> io.circe.Json.fromString("commit"))))).foreach(value =>
          codec.decode(value).isLeft shouldBe true)
    }
  }

  "RepositorySync public merge review" should {
    "resolve selected legacy candidate and continuation addresses without reproducing content-derived keys" in {
      Given("Spec: docs/spec/repository-sync-merge-review-applicability.md; Rule: R2; Example: E7; an actually saved legacy candidate and suspension")
      val f = new PublicReviewFixture
      val oldSubmission = f.review.submission.copy(candidate = f.review.submission.candidate.copy(
        reference = f.review.submission.candidate.reference.copy(revision = "0" * 64)))
      f.retained = f.retained.map(_.copy(submission = oldSubmission))
      val oldInput = take(RepositorySyncMergeReviewBoundary.requestC(oldSubmission))
      val oldId = org.goldenport.cncf.workflow.ContinuationIdentity("saved-legacy-merge-review-address")
      val provided = f.review.provided.copy(input = Some(f.review.provided.input.get.copy(
        contextReference = ContextReference(oldSubmission.identity, oldInput.candidateRevision))))
      val pending = take(RepositorySyncMergeReviewBoundary.planC(validationCommand, oldSubmission, now,
        f.review.active, provided, f.review.operation, f.review.f.selections, f.review.f.mappings,
        configuration, Some(oldId))).pending.get
      val suspended = take(RepositorySyncMergeReviewBoundary.suspendedRecordC(pending, f.review.active))
      val uow = unitOfWork()
      take(f.runtime.stageSuspensionAfterC(uow, pending.continuation, () => f.review.f.instances.append(
        configuration, f.review.active.identity, InstanceRevision(7L), suspended.history.last).map(_ => ())))
      take(uow.commit())
      take(f.runtime.claimC(oldId))
      f.withSubsystem { subsystem =>
        When("the public operation resolves the saved suspension instead of regenerating its old hash")
        val work = take(WorkflowWorkOrderJsonV1.decodeC(scalar(take(subsystem.executeOperationResponse(
          f.request(oldInput)))), RepositorySyncMergeReviewBoundary.inputCodec))
        val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncMergeReviewRequest]].request
        Then("actual candidate and continuation references survive, without a new suspension or authority")
        order.continuationId shouldBe oldId
        order.input.get.value.candidateRevision shouldBe "0" * 64
        f.review.f.appends shouldBe 8
        take(subsystem.executeOperationResponse(f.completion(f.result(work))))
        f.review.f.stored.get.revision shouldBe InstanceRevision(9L)
        f.review.f.appends shouldBe 9
      }
    }

    "read legacy review fields without expiry or evidence-digest admission and complete once" in {
      Given("Spec: docs/spec/repository-sync-merge-review-applicability.md; Rule: R1; Example: E4; an issued review and legacy Result payload")
      val f = new PublicReviewFixture
      val codec = RepositorySyncMergeReviewResultAdapter.payloadCodec
      f.withSubsystem { subsystem =>
        val original = f.result(f.issue(subsystem))
        val value = original.result.value.copy(reviewedAt = now.plusSeconds(7200).toString)
        val submitted = original.copy(result = original.result.copy(value = value))
        val json = codec.encode(value)
        When("new output is produced and old payloads carry unusable or missing certificates")
        json.asObject.get.keys.toSet shouldBe Set("candidateRevision", "reviewedAt", "decision", "rationale", "findings")
        value.productElementNames.toSet should not contain "validUntil"
        val old = Vector(None, Some(io.circe.Json.Null), Some(io.circe.Json.True),
          Some(io.circe.Json.fromString("obsolete-proof-or-expiry")))
        for (expiry <- old; digest <- old) {
          val withExpiry = expiry.fold(json)(item => json.mapObject(_.add("validUntil", item)))
          val legacy = digest.fold(withExpiry)(item => withExpiry.mapObject(_.add("evidenceDigest", item)))
          codec.decode(legacy) shouldBe Right(value)
        }
        Given("the complete old Result envelope with those obsolete fields")
        val wire = io.circe.parser.parse(take(WorkflowResultJsonV1.encodeC(submitted, codec))).toOption.get
        val legacyWire = wire.mapObject(_.add("result", wire.hcursor.downField("result").focus.get.mapObject(
          _.add("value", json.mapObject(_.add("validUntil", io.circe.Json.fromString(now.minusSeconds(3600).toString))
            .add("evidenceDigest", io.circe.Json.True)))))).noSpaces
        When("the public completion operation consumes the legacy envelope before its recorded review event time")
        take(subsystem.executeOperationResponse(f.completion(submitted).copy(arguments = List(Argument("arg1", legacyWire)))))
        Then("no expiry, future-time or digest gate renews authority or repeats completion")
        f.review.f.stored.get.revision shouldBe InstanceRevision(9L)
        f.review.f.appends shouldBe 9
        subsystem.executeOperationResponse(f.completion(submitted)) shouldBe a[Consequence.Failure[?]]
        Vector(json.mapObject(_.add("approval", io.circe.Json.True)), json.mapObject(_.remove("candidateRevision")))
          .foreach(item => codec.decode(item).isLeft shouldBe true)
      }
    }

    "read a legacy completion hash suffix without recomputation or repeated receipt certification" in {
      Given("Spec: docs/spec/repository-sync-merge-review-applicability.md; Rule: R2; Example: E5; a selected review and old completion history")
      val f = new CommitFixture(decision = "decision-needed")
      val cause = CausationReference(f.reviewed.causation.value + ":unusable-old-content-proof")
      f.f.review.f.stored = Some(f.reviewed.copy(causation = cause,
        history = f.reviewed.history.dropRight(1) :+ f.reviewed.history.last.copy(causation = cause)))
      val port = new MergeDecisionPort(q => q.copy(context = q.context.copy(
        summary = "Another diagnostic summary", references = Vector.empty,
        snapshot = q.context.snapshot.copy(workspaceRevision = Some("old-workspace"), evidenceRevision = Some("obsolete-proof")))))
      When("the saved actual Result reference is projected after former expiry")
      val wire = take(RepositorySyncMergeReviewServiceOperation.projectDecisionC(f.submitted.resultReference, f.f.ports.copy(
        decisions = Some(port), asOf = () => now.plusSeconds(7200))))
      Then("the old suffix and diagnostic metadata require no new proof, while no commit or resume occurs")
      take(WorkflowDecisionJsonV1.decodeC(wire, RepositorySyncMergeDecision.inputCodec))
      f.f.review.f.stored.get.causation shouldBe cause
      f.f.review.f.appends shouldBe 9
      f.calls shouldBe 0
      f.writes shouldBe 0
    }

    "bind the declared typed operations and reject incompatible public request shapes" in {
      val f = new PublicReviewFixture
      val operation = take(RepositorySyncMergeReviewServiceOperation.bindRequestC(f.ports))
      val wire = RepositorySyncMergeReviewServiceOperation.encodeRequest(f.input)
      take(RepositorySyncMergeReviewServiceOperation.decodeRequestC(wire)) shouldBe f.input
      operation.createOperationRequest(f.request()).isSuccess shouldBe true
      operation.createOperationRequest(f.request().copy(operation = "admitMergeValidation")) shouldBe a[Consequence.Failure[?]]
      operation.createOperationRequest(f.request().copy(service = Some("GoalPhaseService"))) shouldBe a[Consequence.Failure[?]]
      Vector(wire.replace("\"request\":", "\"nextState\":\"Converged\",\"request\":"),
        wire.replace("\"submissionIdentity\":", "\"commit\":true,\"submissionIdentity\":"),
        wire.replace(RepositorySyncMergeReviewServiceOperation.wireSchema, "unsupported"), null).foreach(value =>
        RepositorySyncMergeReviewServiceOperation.decodeRequestC(value) shouldBe a[Consequence.Failure[?]])
      RepositorySyncMergeReviewServiceOperation.bindRequestC(f.ports.copy(runtime = ContinuationRuntime.empty)) shouldBe
        a[Consequence.Failure[?]]
      RepositorySyncMergeReviewServiceOperation.bindCompletionC(f.ports.copy(retained = null)) shouldBe a[Consequence.Failure[?]]
      f.review.f.appends shouldBe 7
    }

    "issue and replay the same typed WorkOrder through the public Operation without duplicate history" in {
      Vector(false, true).foreach { dirty =>
        val f = new PublicReviewFixture(dirty)
        f.withSubsystem { subsystem =>
          val work = f.issue(subsystem)
          val order = work.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncMergeReviewRequest]].request
          order.input.map(_.value) shouldBe Some(f.input)
          order.completionOperation shouldBe StateMachineOperationIdentity("RepositorySyncService", "submitMergeReview")
          order.expectedRevision shouldBe StateMachineRevision("8")
          f.review.f.stored.get.history.take(7) shouldBe f.review.active.history
          f.review.f.stored.get.currentProgression shouldBe Some(ProgressionReference("sync.MergeValidated"))
          f.issue(subsystem) shouldBe work
          take(f.issued.loadC(order.continuationId)) shouldBe Some(work)
          f.review.f.appends shouldBe 8
        }
      }
    }

    "expose the same review WorkOrder through the CLI JSON selector" in {
      val f = new PublicReviewFixture
      f.withSubsystem { subsystem =>
        val wire = scalar(take(subsystem.executeOperationResponse(f.request())))
        val selector = s"${SmWorkflowComponent.name}/RepositorySyncService/requestMergeReview"
        take(new CncfRuntime().executeCommandResponse(subsystem, Array(selector, "--json",
          RepositorySyncMergeReviewServiceOperation.encodeRequest(f.input)))).print shouldBe wire
        f.review.f.appends shouldBe 8
      }
    }

    "refuse actual public target conflicts and missing retained references before saving or publishing" in {
      val f = new PublicReviewFixture
      val i = f.input
      f.withSubsystem { subsystem =>
        Vector(i.copy(submissionIdentity = "other"), i.copy(repositoryIdentity = "other"),
          i.copy(candidateRevision = "0" * 64),
          i.copy(reviewPolicyVersion = "other"), i.copy(pendingHeadOid = "0" * 40),
          i.copy(pendingTreeOid = "0" * 40)).foreach(value =>
          subsystem.executeOperationResponse(f.request(value)) shouldBe a[Consequence.Failure[?]])
        f.retained = None
        subsystem.executeOperationResponse(f.request()) shouldBe a[Consequence.Failure[?]]
        f.review.f.stored shouldBe Some(f.review.active)
        f.review.f.appends shouldBe 7
      }
    }

    "use retained semantic input without certifying caller copies of diagnostic or derived data" in {
      Given("the actual target and a caller proposal with different copied validation and derived metadata")
      val f = new PublicReviewFixture
      f.withSubsystem { subsystem =>
        val proposal = f.input.copy(validationDigest = "not-a-proof", pendingTreeDigest = "not-a-proof",
          selectedChecks = Vector.empty, changedPaths = Vector.empty, orderedParents = f.input.orderedParents.reverse)
        When("the public review operation resolves that actual retained target")
        val wire = scalar(take(subsystem.executeOperationResponse(f.request(proposal))))
        val work = take(WorkflowWorkOrderJsonV1.decodeC(wire, RepositorySyncMergeReviewBoundary.inputCodec))
        Then("the semantic worker receives authoritative retained checks, paths and parents, not caller copies")
        work.current.asInstanceOf[WorkflowContinuation.WorkOrder[RepositorySyncMergeReviewRequest]].request.input.get.value shouldBe f.input
        f.review.f.appends shouldBe 8
      }
    }

    "recover an uncertain suspension append without appending revision eight again" in {
      val f = new PublicReviewFixture
      var failOnce = true
      f.ports = f.ports.copy(instances = new WorkflowInstancePersistence {
        def create(c: Configuration, r: InstanceRecord) = f.review.f.instances.create(c, r)
        def load(c: Configuration, i: InstanceIdentity) = f.review.f.instances.load(c, i)
        def append(c: Configuration, i: InstanceIdentity, revision: InstanceRevision, e: HistoryEntry) =
          f.review.f.instances.append(c, i, revision, e).flatMap { value =>
            if (failOnce) { failOnce = false; Consequence.stateConflict("uncertain suspension append response") }
            else Consequence.success(value)
          }
      })
      f.withSubsystem { subsystem =>
        subsystem.executeOperationResponse(f.request()) shouldBe a[Consequence.Failure[?]]
        f.review.f.stored.get.revision shouldBe InstanceRevision(8L)
        f.review.f.appends shouldBe 8
        f.issue(subsystem).current shouldBe a[WorkflowContinuation.WorkOrder[?]]
        f.review.f.appends shouldBe 8
      }
    }

    "recover claimed issuance failure through a recreated Continuation runtime" in {
      val f = new PublicReviewFixture
      var failOnce = true
      val backing = f.issued
      f.ports = f.ports.copy(issued = new IssuedWorkOrderPersistence[RepositorySyncMergeReviewRequest] {
        def loadC(id: org.goldenport.cncf.workflow.ContinuationIdentity) = backing.loadC(id)
        def putIfAbsentC(value: WorkflowInteraction[RepositorySyncMergeReviewRequest, Nothing]) =
          if (failOnce) { failOnce = false; Consequence.stateConflict("planned issue failure") }
          else backing.putIfAbsentC(value)
      })
      f.withSubsystem { subsystem => subsystem.executeOperationResponse(f.request()) shouldBe a[Consequence.Failure[?]] }
      f.review.f.appends shouldBe 8
      f.ports = f.ports.copy(runtime = new PersistentContinuationRuntime(f.continuationStore))
      f.withSubsystem { subsystem => f.issue(subsystem) shouldBe f.issue(subsystem) }
      f.review.f.appends shouldBe 8
    }

    "refuse a claimed Continuation that differs from retained public review provenance" in {
      Vector("run", "context", "operation").foreach { drift =>
        val f = new PublicReviewFixture
        val provided = f.review.provided.copy(context = f.review.provided.context.copy(
          summary = "Review the full validated pending merge"))
        val pending = take(f.review.plan(request = provided)).pending.get
        val projected = take(RepositorySyncMergeReviewBoundary.suspendedRecordC(pending, f.review.active))
        val valid = pending.continuation
        val foreign = drift match {
          case "run" => valid.copy(runId = StateMachineRunIdentity("foreign-run"))
          case "context" => valid.copy(context = valid.context.copy(snapshot = valid.context.snapshot.copy(modelRevision = Some("foreign-policy"))))
          case _ => valid.copy(requiredOperation = valid.requiredOperation.copy(actionIdentity = "OtherAction"))
        }
        val uow = unitOfWork()
        take(f.runtime.stageSuspensionAfterC(uow, foreign, () => f.review.f.instances.append(
          configuration, f.review.active.identity, InstanceRevision(7L), projected.history.last).map(_ => ())))
        take(uow.commit())
        take(f.runtime.claimC(foreign.continuationId))
        f.withSubsystem { subsystem =>
          subsystem.executeOperationResponse(f.request()) shouldBe a[Consequence.Failure[?]]
          take(f.issued.loadC(foreign.continuationId)) shouldBe None
          f.review.f.stored shouldBe Some(projected)
          f.review.f.appends shouldBe 8
        }
      }
    }

    "reuse a fresh full review without issuing work or granting commit authority" in {
      val f = new PublicReviewFixture
      val target = f.review.submission.candidate.reference
      val evidence = take(R.evidenceC("retained-review", target,
        R.Review(target.revision, now.minusSeconds(1), R.Disposition.Accept,
          Vector.empty, "Full merge reviewed"), R.Scope.FullMerge, "retained-receipt"))
      f.retained = f.retained.map(seed => seed.copy(submission = seed.submission.copy(evidence = Vector(evidence))))
      f.withSubsystem { subsystem =>
        val wait = take(WorkflowWaitJsonV1.decodeC(scalar(take(subsystem.executeOperationResponse(f.request())))))
        wait.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision shouldBe StateMachineRevision("7")
        f.review.f.stored shouldBe Some(f.review.active)
        f.review.f.appends shouldBe 7
      }
    }

    "admit typed review evidence and clear only the suspension through public completion" in {
      val f = new PublicReviewFixture
      f.withSubsystem { subsystem =>
        val issued = f.issue(subsystem)
        val submitted = f.result(issued)
        val assessment = take(RepositorySyncMergeReviewResultAdapter.assessC(
          take(RepositorySyncDefinitionBinding.candidateC), f.review.submission, issued, submitted, now))
        assessment.accepted shouldBe true
        assessment.judgment.payloadType.value shouldBe "RepositorySyncMergeReviewResult"
        val wire = take(WorkflowResultJsonV1.encodeC(submitted, RepositorySyncMergeReviewResultAdapter.payloadCodec))
        take(WorkflowResultJsonV1.decodeC(wire, RepositorySyncMergeReviewResultAdapter.payloadCodec)) shouldBe submitted
        val wait = take(WorkflowWaitJsonV1.decodeC(scalar(take(subsystem.executeOperationResponse(f.completion(submitted))))))
        wait.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision shouldBe StateMachineRevision("9")
        f.review.f.stored.get.lifecycle shouldBe Lifecycle.Active
        f.review.f.stored.get.currentProgression shouldBe Some(ProgressionReference("sync.MergeValidated"))
        f.review.f.stored.get.suspension shouldBe None
        f.review.f.stored.get.history.take(7) shouldBe f.review.active.history
        subsystem.executeOperationResponse(f.completion(submitted)) shouldBe a[Consequence.Failure[?]]
        f.review.f.appends shouldBe 9
      }
    }

    "complete through the CLI typed Result selector without a second review request" in {
      val f = new PublicReviewFixture
      f.withSubsystem { subsystem =>
        val submitted = f.result(f.issue(subsystem))
        val wire = take(WorkflowResultJsonV1.encodeC(submitted, RepositorySyncMergeReviewResultAdapter.payloadCodec))
        val selector = s"${SmWorkflowComponent.name}/RepositorySyncService/submitMergeReview"
        val resultWire = take(new CncfRuntime().executeCommandResponse(subsystem, Array(selector, "--json", wire))).print
        take(WorkflowWaitJsonV1.decodeC(resultWire)).current shouldBe a[WorkflowContinuation.Wait]
        f.review.f.stored.get.revision shouldBe InstanceRevision(9L)
        f.review.f.appends shouldBe 9
      }
    }

    "keep repair decision-needed and reject distinct from accepting a merge" in {
      Vector("repair", "decision-needed", "reject").foreach { decision =>
        val f = new PublicReviewFixture
        f.withSubsystem { subsystem =>
          val issued = f.issue(subsystem)
          val result = f.result(issued, decision)
          take(RepositorySyncMergeReviewResultAdapter.assessC(take(RepositorySyncDefinitionBinding.candidateC),
            f.review.submission, issued, result, now)).accepted shouldBe false
          val wire = scalar(take(subsystem.executeOperationResponse(f.completion(result))))
          wire should include ("commit remains blocked")
          f.review.f.stored.get.suspension shouldBe None
          f.review.f.stored.get.lifecycle shouldBe Lifecycle.Active
          f.review.f.appends shouldBe 9
        }
      }
    }

    "reject invalid semantic results without consuming the claim then accept the valid result" in {
      val f = new PublicReviewFixture
      f.withSubsystem { subsystem =>
        val valid = f.result(f.issue(subsystem))
        val v = valid.result.value
        Vector(v.copy(candidateRevision = "0" * 64), v.copy(decision = "commit"), v.copy(decision = "repair"),
          v.copy(reviewedAt = "invalid"), v.copy(rationale = ""),
          v.copy(findings = Vector(RepositorySyncMergeReviewFinding("finding", Vector("src/main.scala"), "not an accept"))),
          v.copy(decision = "repair", findings = Vector(RepositorySyncMergeReviewFinding("finding", Vector("other.scala"), "wrong path"))))
          .foreach(value => subsystem.executeOperationResponse(f.completion(valid.copy(result = valid.result.copy(value = value))))
            shouldBe a[Consequence.Failure[?]])
        f.review.f.stored.get.revision shouldBe InstanceRevision(8L)
        f.review.f.appends shouldBe 8
        take(subsystem.executeOperationResponse(f.completion(valid)))
        f.review.f.appends shouldBe 9
      }
    }

    "reject foreign continuation headers and context drift before one-shot resume" in {
      val f = new PublicReviewFixture
      f.withSubsystem { subsystem =>
        val valid = f.result(f.issue(subsystem))
        Vector(valid.copy(runId = StateMachineRunIdentity("other")),
          valid.copy(continuationId = org.goldenport.cncf.workflow.ContinuationIdentity("other")),
          valid.copy(expectedRevision = StateMachineRevision("7")),
          valid.copy(contextSnapshot = valid.contextSnapshot.copy(evidenceRevision = Some("0" * 64))),
          valid.copy(contextSnapshot = valid.contextSnapshot.copy(modelRevision = Some("other")))).foreach(value =>
          subsystem.executeOperationResponse(f.completion(value)) shouldBe a[Consequence.Failure[?]])
        WorkflowResultJsonV1.encodeC(valid.copy(result = valid.result.copy(typeIdentity = "OtherResult")),
          RepositorySyncMergeReviewResultAdapter.payloadCodec) shouldBe a[Consequence.Failure[?]]
        val wire = take(WorkflowResultJsonV1.encodeC(valid, RepositorySyncMergeReviewResultAdapter.payloadCodec))
        val foreignType = wire.replace("\"typeIdentity\":\"RepositorySyncMergeReviewResult\"",
          "\"typeIdentity\":\"OtherResult\"")
        foreignType should not be wire
        subsystem.executeOperationResponse(f.completion(valid).copy(arguments = List(Argument("arg1", foreignType)))) shouldBe
          a[Consequence.Failure[?]]
        f.review.f.stored.get.revision shouldBe InstanceRevision(8L)
        take(subsystem.executeOperationResponse(f.completion(valid)))
        f.review.f.appends shouldBe 9
      }
    }

    "reject retained provenance drift and changed suspension history before consuming the result" in {
      val f = new PublicReviewFixture
      f.withSubsystem { subsystem =>
        val valid = f.result(f.issue(subsystem))
        val saved = f.review.f.stored.get
        val seed = f.retained.get
        f.retained = Some(seed.copy(command = validationCommand.copy(idempotencyKey = "other-validation")))
        subsystem.executeOperationResponse(f.completion(valid)) shouldBe a[Consequence.Failure[?]]
        f.retained = Some(seed)
        f.review.f.stored = Some(saved.copy(history = saved.history.updated(1,
          saved.history(1).copy(causation = CausationReference("other-boundary")))))
        subsystem.executeOperationResponse(f.completion(valid)) shouldBe a[Consequence.Failure[?]]
        f.review.f.stored = Some(saved)
        take(subsystem.executeOperationResponse(f.completion(valid)))
        f.review.f.appends shouldBe 9
      }
    }

    "reject progress selectors and internal claim fields in Result payloads" in {
      val f = new PublicReviewFixture
      f.withSubsystem { subsystem =>
        val valid = f.result(f.issue(subsystem))
        val request = f.completion(valid)
        val wire = request.arguments.head match {
          case Argument("arg1", value: String, _) => value
          case other => fail(s"Expected Result JSON argument, found $other")
        }
        Vector(wire.replace("\"decision\":\"accept\"", "\"decision\":\"accept\",\"nextState\":\"Converged\""),
          wire.replace("\"decision\":\"accept\"", "\"decision\":\"accept\",\"claimId\":\"foreign\""))
          .foreach(value => subsystem.executeOperationResponse(request.copy(arguments = List(Argument("arg1", value))))
            shouldBe a[Consequence.Failure[?]])
        f.review.f.appends shouldBe 8
        take(subsystem.executeOperationResponse(request))
        f.review.f.appends shouldBe 9
      }
    }
  }
}
