package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.{Component, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.{StateMachineOperationIdentity, WorkflowInstancePersistence, WorkflowWaitJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation
import org.goldenport.configuration.{Configuration as GenericConfiguration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncBoundaryAssessmentCommand, RepositorySyncFetchAdmissionCommand, RepositorySyncPreservationAdmissionCommand, RepositorySyncRelationAdmissionCommand, RepositorySyncMergeAdmissionCommand, RepositorySyncStartCommand, RepositorySyncStartInput}

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncMergeAdmissionExecutionSpec extends AnyWordSpec with Matchers {
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
          Vector(fetchedTip), "e" * 40, "f" * 64, Vector.empty)))
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
        if (rejectMergeAppend && expectedRevision == InstanceRevision(5L))
          Consequence.stateConflict("RepositorySync fixture merge append failure")
        else stored match {
          case Some(current) if config == configuration && current.identity == identity =>
            current.appendC(expectedRevision, entry).flatMap { next =>
              appends += 1
              stored = Some(next)
              if (failAfterMergeAppend && expectedRevision == InstanceRevision(5L))
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
  }

  "RepositorySync merge progression" should {
    "require persisted relation history before loading any merge evidence" in {
      val fixture = new Fixture
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.prepareFetched()
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      take(fixture.preserve())
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.mergeLoads shouldBe 0
      take(fixture.classify())
      take(fixture.merge())
      fixture.stored.get.revision shouldBe InstanceRevision(6L)
      fixture.appends shouldBe 6
    }

    "append revision six once, preserve prior history and return a nonterminal WAIT on replay" in {
      val fixture = new Fixture
      fixture.prepare()
      val prefix = fixture.stored.get.history
      val admitted = take(fixture.merge())
      val wait = admitted.current.asInstanceOf[WorkflowContinuation.Wait]
      wait.expectedRevision.value shouldBe "6"
      wait.presentation.nextAction shouldBe Some("Validate and review merge result")
      fixture.stored.get.currentProgression shouldBe Some(ProgressionReference("sync.MergeAdmitted"))
      fixture.stored.get.lifecycle shouldBe Lifecycle.Active
      fixture.stored.get.history.take(5) shouldBe prefix
      fixture.stored.get.correlation shouldBe prefix.head.correlation
      take(WorkflowWaitJsonV1.decodeC(take(WorkflowWaitJsonV1.encodeC(admitted)))) shouldBe admitted
      take(fixture.merge()) shouldBe admitted
      fixture.appends shouldBe 6
    }

    "reject every non-DIVERGED route without reading merge evidence" in {
      Vector(new Fixture(equal = true), new Fixture(remoteAhead = true), new Fixture(diverged = false))
        .foreach { fixture =>
          fixture.prepare()
          val prefix = fixture.stored.get.history
          fixture.merge() shouldBe a[Consequence.Failure[?]]
          fixture.mergeLoads shouldBe 0
          fixture.stored.get.history shouldBe prefix
          fixture.stored.get.revision shouldBe InstanceRevision(5L)
        }
    }

    "admit a checkpoint-based local tip and preserve its full-state history" in {
      val fixture = new Fixture(dirty = true)
      fixture.prepare()
      val prefix = fixture.stored.get.history
      val admitted = take(fixture.merge())
      admitted.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value shouldBe "6"
      fixture.stored.get.history.take(5) shouldBe prefix
      take(fixture.merge()) shouldBe admitted
      fixture.appends shouldBe 6
      fixture.checkpointLoads should be > 0
    }

    "retain revision five on missing, failed or inconsistent pre/receipt/pending/structural evidence" in {
      Vector("freeze", "receipt", "pending", "structural").foreach { stage =>
        val fixture = new Fixture
        fixture.prepare()
        fixture.missingStage = stage
        fixture.merge() shouldBe a[Consequence.Failure[?]]
        fixture.missingStage = ""
        fixture.failureStage = stage
        fixture.merge() shouldBe a[Consequence.Failure[?]]
        fixture.failureStage = ""
        fixture.stored.get.revision shouldBe InstanceRevision(5L)
        fixture.appends shouldBe 5
        take(fixture.merge())
        fixture.appends shouldBe 6
      }
      val invalid = new Fixture
      invalid.prepare()
      invalid.frozenTransform = value => value.copy(before = value.before.copy(pendingOperations = Vector("REBASE")))
      invalid.merge() shouldBe a[Consequence.Failure[?]]
      invalid.frozenTransform = identity
      invalid.mergeReceiptTransform = _.copy(localTipAfterOid = "0" * 40)
      invalid.merge() shouldBe a[Consequence.Failure[?]]
      invalid.mergeReceiptTransform = identity
      invalid.pendingTransform = value => value.copy(state = value.state.copy(dirtyPaths = Vector("new.txt")))
      invalid.merge() shouldBe a[Consequence.Failure[?]]
      invalid.stored.get.revision shouldBe InstanceRevision(5L)
      invalid.pendingTransform = identity
      take(invalid.merge())
      invalid.appends shouldBe 6
    }

    "reject altered commands and upstream evidence before merge loads" in {
      val fixture = new Fixture
      fixture.prepare()
      val commands = Vector(
        mergeCommand.copy(expectedRevision = "6"),
        mergeCommand.copy(idempotencyKey = ""),
        mergeCommand.copy(relationCommand = null),
        mergeCommand.copy(relationCommand = relationCommand.copy(expectedRevision = "5")),
        mergeCommand.copy(relationCommand = relationCommand.copy(idempotencyKey = "other-relation")),
        mergeCommand.copy(relationCommand = relationCommand.copy(preservationCommand =
          preservationCommand.copy(idempotencyKey = "other-preservation"))),
        mergeCommand.copy(relationCommand = relationCommand.copy(preservationCommand =
          preservationCommand.copy(fetchCommand = fetchCommand.copy(idempotencyKey = "other-fetch")))),
        mergeCommand.copy(relationCommand = relationCommand.copy(preservationCommand =
          preservationCommand.copy(fetchCommand = fetchCommand.copy(boundaryCommand =
            boundaryCommand.copy(idempotencyKey = "other-boundary"))))))
      commands.foreach(fixture.merge(_) shouldBe a[Consequence.Failure[?]])
      fixture.fetchReceiptValue = fixture.fetchReceipt.copy(executionDigest = "2" * 64)
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.fetchReceiptValue = fixture.fetchReceipt
      fixture.cleanTransform = _.copy(inspectionDigest = "0" * 64)
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.cleanTransform = identity
      fixture.relationTransform = _.copy(remoteAhead = 4L)
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.mergeLoads shouldBe 0
      fixture.relationTransform = identity
      take(fixture.merge())
      fixture.appends shouldBe 6
    }

    "reject conflicting merge keys and evidence after revision six without another append" in {
      val fixture = new Fixture
      fixture.prepare()
      val admitted = take(fixture.merge())
      fixture.merge(mergeCommand.copy(idempotencyKey = "other-merge")) shouldBe a[Consequence.Failure[?]]
      fixture.mergeReceiptTransform = _.copy(executionDigest = "0" * 64)
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.mergeReceiptTransform = identity
      fixture.pendingTransform = value => value.copy(state = value.state.copy(inspectionDigest = "0" * 64))
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.pendingTransform = identity
      fixture.frozenTransform = value => value.copy(before = value.before.copy(inspectionDigest = "0" * 64))
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.frozenTransform = identity
      fixture.structuralTransform = _.copy(inspectionDigest = "0" * 64)
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.structuralTransform = identity
      take(fixture.merge()) shouldBe admitted
      fixture.appends shouldBe 6
    }

    "reject null commands and retained records without advancing the classified instance" in {
      val fixture = new Fixture
      fixture.prepare()
      Vector(null, mergeCommand.copy(relationCommand = null),
        mergeCommand.copy(relationCommand = relationCommand.copy(preservationCommand = null)),
        mergeCommand.copy(relationCommand = relationCommand.copy(preservationCommand =
          preservationCommand.copy(fetchCommand = null))),
        mergeCommand.copy(idempotencyKey = null)).foreach { command =>
        fixture.merge(command) shouldBe a[Consequence.Failure[?]]
      }
      fixture.mergeLoads shouldBe 0
      fixture.frozenTransform = _ => null
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.frozenTransform = identity
      fixture.mergeReceiptTransform = _ => null
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.mergeReceiptTransform = identity
      fixture.pendingTransform = _ => null
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.pendingTransform = identity
      fixture.structuralTransform = _ => null
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.stored.get.revision shouldBe InstanceRevision(5L)
      fixture.appends shouldBe 5
      fixture.structuralTransform = identity
      take(fixture.merge())
      fixture.appends shouldBe 6
    }

    "bind inherited whitespace to history and block both-parent diagnostics without completion" in {
      val fixture = new Fixture
      fixture.prepare()
      val local = RepositorySyncMergeAssessment.WhitespaceDiagnostic("src/main.scala", 12L, "a" * 40)
      val remote = local.copy(parentTipOid = fixture.fetchedTip)
      fixture.structuralTransform = _.copy(whitespaceDiagnostics = Vector(local, remote))
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.appends shouldBe 5
      fixture.structuralTransform = _.copy(whitespaceDiagnostics = Vector(local))
      val admitted = take(fixture.merge())
      take(fixture.merge()) shouldBe admitted
      fixture.structuralTransform = identity
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.structuralTransform = _.copy(whitespaceDiagnostics = Vector(local))
      take(fixture.merge()) shouldBe admitted
      fixture.appends shouldBe 6
      fixture.stored.get.lifecycle shouldBe Lifecycle.Active
      fixture.stored.get.currentProgression shouldBe Some(ProgressionReference("sync.MergeAdmitted"))
    }

    "reject a changed progression in the persisted relation instance before evidence loads" in {
      val fixture = new Fixture
      fixture.prepare()
      val original = fixture.stored
      fixture.stored = original.map(_.copy(currentProgression = Some(ProgressionReference("sync.MergeAdmitted"))))
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.mergeLoads shouldBe 0
      fixture.stored = original
      take(fixture.merge())
      fixture.appends shouldBe 6
    }

    "retain the classified instance on append failure and allow an exact retry" in {
      val fixture = new Fixture
      fixture.prepare()
      fixture.rejectMergeAppend = true
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.stored.get.revision shouldBe InstanceRevision(5L)
      fixture.appends shouldBe 5
      fixture.rejectMergeAppend = false
      val admitted = take(fixture.merge())
      take(fixture.merge()) shouldBe admitted
      fixture.appends shouldBe 6
    }

    "reconcile a persisted append with a failed response without falsely rolling back or duplicating it" in {
      val fixture = new Fixture
      fixture.prepare()
      fixture.failAfterMergeAppend = true
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.stored.get.revision shouldBe InstanceRevision(6L)
      fixture.appends shouldBe 6
      val reconciled = take(fixture.merge())
      take(fixture.merge()) shouldBe reconciled
      fixture.appends shouldBe 6
    }

    "reject different parents, unresolved conflicts and an auto-commit receipt without appending" in {
      val fixture = new Fixture
      fixture.prepare()
      fixture.frozenTransform = value => value.copy(parentTips = value.parentTips.reverse)
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.frozenTransform = identity
      fixture.structuralTransform = _.copy(conflictMarkerPaths = Vector("src/main.scala"))
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.frozenTransform = identity
      fixture.structuralTransform = identity
      fixture.mergeReceiptTransform = _.copy(noCommit = false)
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.stored.get.revision shouldBe InstanceRevision(5L)
      fixture.appends shouldBe 5
      fixture.mergeReceiptTransform = identity
      take(fixture.merge())
      fixture.appends shouldBe 6
    }

    "reject a persisted different revision-six stage before loading merge evidence" in {
      val fixture = new Fixture
      fixture.prepare()
      val admitted = take(fixture.merge())
      val original = fixture.stored
      val other = Some(ProgressionReference("sync.FastForwardAdmitted"))
      fixture.stored = original.map(record => record.copy(currentProgression = other,
        history = record.history.init :+ record.history.last.copy(currentProgression = other)))
      take(fixture.stored.get.validateC)
      val loads = fixture.mergeLoads
      fixture.merge() shouldBe a[Consequence.Failure[?]]
      fixture.mergeLoads shouldBe loads
      fixture.stored = original
      take(fixture.merge()) shouldBe admitted
      fixture.appends shouldBe 6
    }

    "reject incomplete and incompatible public bindings without progressing" in {
      val fixture = new Fixture
      fixture.prepare()
      RepositorySyncMergeAdmissionServiceOperation.bindC(
        fixture.selections, fixture.mappings, fixture.snapshots, fixture.scopes,
        fixture.fetches, fixture.preservations, fixture.relations, null,
        definition, configuration, fixture.instances) shouldBe a[Consequence.Failure[?]]
      RepositorySyncMergeAdmissionServiceOperation.bindC(
        fixture.selections, fixture.mappings, fixture.snapshots, fixture.scopes,
        fixture.fetches, fixture.preservations, fixture.relations, fixture.merges,
        take(SplitPhaseDefinitionBinding.bindC), configuration, fixture.instances) shouldBe a[Consequence.Failure[?]]
      fixture.mergeLoads shouldBe 0
      fixture.stored.get.revision shouldBe InstanceRevision(5L)
    }

    "serve the generated merge Operation and equivalent strict CLI JSON" in {
      val fixture = new Fixture
      fixture.prepare()
      val operation = take(RepositorySyncMergeAdmissionServiceOperation.bindC(
        fixture.selections, fixture.mappings, fixture.snapshots, fixture.scopes,
        fixture.fetches, fixture.preservations, fixture.relations, fixture.merges,
        definition, configuration, fixture.instances))
      val wire = RepositorySyncMergeAdmissionServiceOperation.encodeCommand(mergeCommand)
      take(RepositorySyncMergeAdmissionServiceOperation.decodeCommandC(wire)) shouldBe mergeCommand
      RepositorySyncMergeAdmissionServiceOperation.decodeCommandC(null) shouldBe a[Consequence.Failure[?]]
      val request = Request.of(component = SmWorkflowComponent.name,
        service = "RepositorySyncService", operation = "admitMerge",
        arguments = List(Argument("arg1", wire)))
      operation.createOperationRequest(request).isSuccess shouldBe true
      val malformed = Vector("not-json", "{}", wire.dropRight(1) + ",\"extra\":1}",
        wire.dropRight(1) + ",\"outcome\":\"SYNCHRONIZED\"}",
        wire.replace("\"expectedRevision\":\"5\"", "\"expectedRevision\":5"),
        wire.replace("\"idempotencyKey\":\"sync-merge-1\"", "\"idempotencyKey\":null"),
        wire.replace(RepositorySyncMergeAdmissionServiceOperation.wireSchema, "unsupported"),
        wire.replace("\"relationCommand\":{", "\"relationCommand\":null,\"other\":{"),
        wire.replace(RepositorySyncRelationAdmissionServiceOperation.wireSchema, "unsupported-nested"))
      malformed.foreach { value =>
        operation.createOperationRequest(request.copy(arguments = List(Argument("arg1", value)))) shouldBe
          a[Consequence.Failure[?]]
      }
      operation.createOperationRequest(request.copy(operation = "admitTipRelation")) shouldBe a[Consequence.Failure[?]]
      operation.createOperationRequest(request.copy(service = Some("OtherService"))) shouldBe a[Consequence.Failure[?]]
      fixture.stored.get.revision shouldBe InstanceRevision(5L)
      val service = spec.ServiceDefinition("RepositorySyncService",
        spec.OperationDefinitionGroup(NonEmptyVector.of(operation)))
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
      val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
        name = "sm-workflow-repository-merge", version = None,
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
        direct.handle.instanceIdentity shouldBe fixture.stored.get.identity
        direct.current.asInstanceOf[WorkflowContinuation.Wait].presentation.nextAction shouldBe Some("Validate and review merge result")
        val selector = s"${SmWorkflowComponent.name}/RepositorySyncService/admitMerge"
        take(new CncfRuntime().executeCommandResponse(subsystem, Array(selector, "--json", wire))).print shouldBe directWire
        fixture.stored.get.revision shouldBe InstanceRevision(6L)
        fixture.appends shouldBe 6
      } finally subsystem.shutdown()
    }
  }
}
