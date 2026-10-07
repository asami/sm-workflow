package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.{Component, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.{StateMachineOperationIdentity, WorkflowInstancePersistence, WorkflowWaitJsonV1, WorkflowTerminalJsonV1}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation
import org.goldenport.configuration.{Configuration as GenericConfiguration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncPushAdmissionCommand, RepositorySyncConvergenceCommand, RepositorySyncBoundaryAssessmentCommand, RepositorySyncFetchAdmissionCommand, RepositorySyncPreservationAdmissionCommand, RepositorySyncRelationAdmissionCommand, RepositorySyncFastForwardAdmissionCommand, RepositorySyncStartCommand, RepositorySyncStartInput}

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncConvergenceExecutionSpec extends AnyWordSpec with Matchers {
  import ProfileStartAdmission.*
  import RepositorySyncCheckpointAssessment.*

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"RepositorySync fast-forward progression failed: $failure")
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
  private val fastForwardCommand = RepositorySyncFastForwardAdmissionCommand(
    relationCommand, "5", "sync-fast-forward-1")
  private val pushCommand = RepositorySyncPushAdmissionCommand(relationCommand, "5", "sync-push-1")
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

  private final class Fixture(dirty: Boolean = false, staged: Boolean = false, equal: Boolean = false,
    localAhead: Boolean = false, trackingUpdated: Boolean = false) {
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
    val fetchedTip = if (equal) { if (dirty) "2" * 40 else snapshot.headOid } else "f" * 40
    val localTip = if (dirty) "2" * 40 else snapshot.headOid
    val convergedTip = if (localAhead) localTip else fetchedTip
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
            localTipOid, fetchedTipOid, Vector(if (equal || localAhead) fetchedTip else snapshot.headOid),
            if (localAhead) 3L else if (equal) 0L else if (dirty) 1L else 0L,
            if (equal || localAhead) 0L else 3L,
            historyComplete = true, replacementHistory = false, inspectionDigest = "9" * 64))))
      }
    }
    var frozenTransform: RepositorySyncFastForwardAssessment.FrozenState => RepositorySyncFastForwardAssessment.FrozenState = identity
    var forwardReceiptTransform: RepositorySyncFastForwardAssessment.Receipt => RepositorySyncFastForwardAssessment.Receipt = identity
    var postStateTransform: RepositorySyncFastForwardAssessment.State => RepositorySyncFastForwardAssessment.State = identity
    var missingStage = ""
    var failureStage = ""
    var fastForwardLoads = 0
    val fastForwards = new RepositorySyncFastForwardAssessment.Port {
      def loadFrozenC(repositoryIdentity: String, relationDigest: String,
        sourceTipOid: String, targetTipOid: String): Consequence[Option[RepositorySyncFastForwardAssessment.FrozenState]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        sourceTipOid shouldBe snapshot.headOid
        targetTipOid shouldBe fetchedTip
        fastForwardLoads += 1
        if (failureStage == "freeze") Consequence.stateConflict("host pre-state load failed")
        else Consequence.success(if (missingStage == "freeze") None else Some(frozenTransform(
          RepositorySyncFastForwardAssessment.FrozenState(RepositorySyncFastForwardAssessment.State(
            relationDigest, "repository-1", "worktree-1", "main", "origin", "main", "remote-1", "policy-v1",
            sourceTipOid, "7" * 40, snapshot.treeDigest, targetTipOid, Vector.empty, "EMPTY",
            detachedHead = false, pendingOperations = Vector.empty, inspectionDigest = "b" * 64),
            targetTipOid, "c" * 40, "d" * 64))))
      }
      def loadReceiptC(repositoryIdentity: String,
        frozenStateDigest: String): Consequence[Option[RepositorySyncFastForwardAssessment.Receipt]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        fastForwardLoads += 1
        if (failureStage == "receipt") Consequence.stateConflict("host receipt load failed")
        else Consequence.success(if (missingStage == "receipt") None else Some(forwardReceiptTransform(
          RepositorySyncFastForwardAssessment.Receipt(frozenStateDigest, snapshot.headOid, fetchedTip,
            fetchedTip, "c" * 40, "d" * 64, "refs/heads/main", Vector("refs/heads/main"),
            fastForwardOnly = true, completed = true, executionDigest = "e" * 64))))
      }
      def loadPostStateC(repositoryIdentity: String,
        receiptDigest: String): Consequence[Option[RepositorySyncFastForwardAssessment.State]] = {
        repositoryIdentity shouldBe input.repositoryIdentity
        fastForwardLoads += 1
        if (failureStage == "post") Consequence.stateConflict("host post-state load failed")
        else Consequence.success(if (missingStage == "post") None else Some(postStateTransform(
          RepositorySyncFastForwardAssessment.State(receiptDigest, "repository-1", "worktree-1", "main",
            "origin", "main", "remote-1", "policy-v1", fetchedTip, "c" * 40, "d" * 64, fetchedTip,
            Vector.empty, "EMPTY", detachedHead = false, pendingOperations = Vector.empty,
            inspectionDigest = "f" * 64))))
      }
    }
    var pushLoads = 0
    var pushReceiptTransform: RepositorySyncPushAssessment.Receipt => RepositorySyncPushAssessment.Receipt = identity
    val pushes = new RepositorySyncPushAssessment.Port {
      def loadFrozenC(repositoryIdentity: String, relationDigest: String,
        sourceTipOid: String, destinationTipBeforeOid: String) = {
        pushLoads += 1
        sourceTipOid shouldBe localTip
        destinationTipBeforeOid shouldBe fetchedTip
        val before = convergenceState(relationDigest, beforeFetch = true).copy(trackingTipOid = fetchedTip)
        Consequence.success(Some(RepositorySyncPushAssessment.FrozenState(before, fetchedTip,
          remoteAncestorOfLocal = true, historyComplete = true, replacementHistory = false,
          graphInspectionDigest = "d" * 64,
          authority = RepositorySyncPushAssessment.Authority(relationDigest, "repository-1", "worktree-1",
            "main", "origin", "main", "remote-1", "policy-v1", localTip, fetchedTip,
            nonForceOnlyAccepted = true, remoteWriteAuthorityAccepted = true, evidenceDigest = "e" * 64))))
      }
      def loadReceiptC(repositoryIdentity: String, frozenStateDigest: String) = {
        pushLoads += 1
        Consequence.success(Some(pushReceiptTransform(RepositorySyncPushAssessment.Receipt(
          frozenStateDigest, "refs/heads/main", "refs/heads/main", localTip, fetchedTip, localTip,
          if (trackingUpdated) localTip else fetchedTip, Vector("refs/heads/main"),
          if (trackingUpdated) Vector("refs/remotes/origin/main") else Vector.empty,
          forceUsed = false, tagsIncluded = false, mirrorUsed = false,
          endpointPolicyAccepted = true, completed = true, executionDigest = "f" * 64))))
      }
      def loadPostStateC(repositoryIdentity: String, receiptDigest: String) = {
        pushLoads += 1
        Consequence.success(Some(convergenceState(receiptDigest, beforeFetch = true)))
      }
    }
    var convergenceLoads = 0
    var convergenceMissing = false
    var convergenceFailure = false
    var convergenceFetchTransform: RepositorySyncConvergenceAssessment.FetchReceipt => RepositorySyncConvergenceAssessment.FetchReceipt = identity
    var verificationTransform: RepositorySyncConvergenceAssessment.Verification => RepositorySyncConvergenceAssessment.Verification = identity
    private def convergenceState(binding: String, beforeFetch: Boolean = false) = RepositorySyncFastForwardAssessment.State(
      binding, "repository-1", "worktree-1", "main", "origin", "main", "remote-1", "policy-v1",
      convergedTip, if (equal || localAhead) { if (dirty) frozen.completeTreeOid else "7" * 40 } else "c" * 40,
      if (equal || localAhead) { if (dirty) frozen.completeTreeDigest else snapshot.treeDigest } else "d" * 64,
      if (beforeFetch && localAhead && !trackingUpdated) fetchedTip else convergedTip,
      Vector.empty, "EMPTY", detachedHead = false, pendingOperations = Vector.empty,
      inspectionDigest = "a" * 64)
    val convergences = new RepositorySyncConvergenceAssessment.Port {
      def loadFetchC(repositoryIdentity: String, predecessorDigest: String, targetTipOid: String) = {
        convergenceLoads += 1
        targetTipOid shouldBe convergedTip
        if (convergenceFailure) Consequence.stateConflict("verification fetch load failed")
        else Consequence.success(if (convergenceMissing) None else {
          val state = convergenceState(predecessorDigest, beforeFetch = true)
          Some(convergenceFetchTransform(RepositorySyncConvergenceAssessment.FetchReceipt(state,
            convergedTip, convergedTip, convergedTip, state.localTreeOid, state.localTreeDigest,
            "refs/heads/main", "refs/remotes/origin/main", Vector("refs/remotes/origin/main"),
            endpointPolicyAccepted = true, completed = true, executionDigest = "b" * 64)))
        })
      }
      def loadVerificationC(repositoryIdentity: String, verificationFetchDigest: String) = {
        convergenceLoads += 1
        Consequence.success(Some(verificationTransform(RepositorySyncConvergenceAssessment.Verification(
          convergenceState(verificationFetchDigest), 0L, 0L, historyComplete = true,
          replacementHistory = false, graphInspectionDigest = "c" * 64))))
      }
    }
    var rejectCompletionAppend = false
    var failAfterCompletionAppend = false
    var stored: Option[InstanceRecord] = None
    var appends = 0
    var rejectForwardAppend = false
    var failAfterForwardAppend = false
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
        if (rejectCompletionAppend && entry.lifecycle == Lifecycle.Completed ||
            rejectForwardAppend && expectedRevision == InstanceRevision(5L))
          Consequence.stateConflict("RepositorySync fixture fast-forward append failure")
        else stored match {
          case Some(current) if config == configuration && current.identity == identity =>
            current.appendC(expectedRevision, entry).flatMap { next =>
              appends += 1
              stored = Some(next)
              if (failAfterCompletionAppend && entry.lifecycle == Lifecycle.Completed ||
                  failAfterForwardAppend && expectedRevision == InstanceRevision(5L))
                Consequence.stateConflict("fixture append response uncertain")
              else Consequence.success(next)
            }
          case _ => Consequence.stateConflict("RepositorySync fixture instance is missing")
        }
    }
    def convergenceCommand = RepositorySyncConvergenceCommand(relationCommand,
      if (equal || localAhead) None else Some(fastForwardCommand.idempotencyKey),
      if (localAhead) Some(pushCommand.idempotencyKey) else None,
      if (equal) "5" else "6", "sync-convergence-1")
    def converge(command: RepositorySyncConvergenceCommand = convergenceCommand) =
      RepositorySyncConvergenceExecution.admitC(command, selections, mappings, snapshots, scopes,
        fetches, preservations, relations, fastForwards, pushes, convergences, definition, configuration, instances)
    def prepareConvergence(): Unit = {
      prepare(); if (localAhead) take(push()) else if (!equal) take(forward()); ()
    }
    def push() = RepositorySyncPushAdmissionExecution.admitC(pushCommand, selections, mappings,
      snapshots, scopes, fetches, preservations, relations, pushes, definition, configuration, instances)
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
    def forward(command: RepositorySyncFastForwardAdmissionCommand = fastForwardCommand) =
      RepositorySyncFastForwardAdmissionExecution.admitC(command, selections, mappings,
        snapshots, scopes, fetches, preservations, relations, fastForwards, definition, configuration, instances)
  }

  "RepositorySync convergence terminal" should {
    "complete LOCAL_AHEAD after push and verification once while retaining checkpoint state and replay" in {
      for (dirty <- Vector(false, true); updated <- Vector(false, true)) {
        val fixture = new Fixture(dirty = dirty, localAhead = true, trackingUpdated = updated)
        fixture.prepareConvergence()
        val prefix = fixture.stored.get.history
        val terminal = take(fixture.converge())
        val completed = fixture.stored.get
        completed.revision shouldBe InstanceRevision(7L)
        completed.lifecycle shouldBe Lifecycle.Completed
        completed.history.take(6) shouldBe prefix
        completed.currentProgression shouldBe None
        val wire = take(WorkflowTerminalJsonV1.encodeC(terminal, RepositorySyncConvergenceJson.payloadCodec))
        wire should include ("\"outcome\":\"SYNCHRONIZED\"")
        wire should include (fixture.localTip)
        if (dirty) wire should include (fixture.frozen.completeTreeOid)
        take(WorkflowTerminalJsonV1.decodeC(wire, RepositorySyncConvergenceJson.payloadCodec)) shouldBe terminal
        take(fixture.converge()) shouldBe terminal
        fixture.appends shouldBe 7
        fixture.fastForwardLoads shouldBe 0
      }
    }

    "require the exact PushAdmitted stage and push key before convergence loads" in {
      val fixture = new Fixture(localAhead = true)
      fixture.prepare()
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.pushLoads shouldBe 0
      take(fixture.push())
      val record = fixture.stored.get
      fixture.pushLoads = 0
      val stage = Some(ProgressionReference("sync.FastForwardAdmitted"))
      fixture.stored = Some(record.copy(currentProgression = stage,
        history = record.history.updated(5, record.history(5).copy(currentProgression = stage))))
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.pushLoads shouldBe 0
      fixture.stored = Some(record)
      fixture.converge(fixture.convergenceCommand.copy(pushIdempotencyKey = Some("wrong"))) shouldBe
        a[Consequence.Failure[?]]
      fixture.convergenceLoads shouldBe 0
      fixture.stored shouldBe Some(record)
      take(fixture.converge())
    }

    "keep the admitted push active on missing, failed, advanced or dirty verification" in {
      val fixture = new Fixture(localAhead = true)
      fixture.prepareConvergence()
      val predecessor = fixture.stored.get
      fixture.convergenceMissing = true
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.convergenceMissing = false
      fixture.convergenceFailure = true
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.convergenceFailure = false
      fixture.convergenceFetchTransform = _.copy(fetchedTipOid = "0" * 40)
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.convergenceFetchTransform = _.copy(changedRefs = Vector.empty)
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.convergenceFetchTransform = identity
      fixture.verificationTransform = v => v.copy(state = v.state.copy(dirtyPaths = Vector("new.txt")))
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.stored shouldBe Some(predecessor)
      fixture.verificationTransform = identity
      take(fixture.converge())
    }

    "reject changed push and terminal evidence on completed LOCAL_AHEAD replay without appending" in {
      val fixture = new Fixture(localAhead = true)
      fixture.prepareConvergence()
      val terminal = take(fixture.converge())
      val completed = fixture.stored.get
      fixture.converge(fixture.convergenceCommand.copy(idempotencyKey = "different")) shouldBe a[Consequence.Failure[?]]
      fixture.converge(fixture.convergenceCommand.copy(pushIdempotencyKey = Some("different"))) shouldBe a[Consequence.Failure[?]]
      fixture.pushReceiptTransform = _.copy(executionDigest = "0" * 64)
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.pushReceiptTransform = identity
      fixture.verificationTransform = _.copy(graphInspectionDigest = "0" * 64)
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.stored shouldBe Some(completed)
      fixture.appends shouldBe 7
      fixture.verificationTransform = identity
      take(fixture.converge()) shouldBe terminal
    }

    "reject malformed and multiple predecessor keys before push evidence loads" in {
      val fixture = new Fixture(localAhead = true)
      fixture.prepareConvergence()
      fixture.pushLoads = 0
      val command = fixture.convergenceCommand
      Vector(command.copy(pushIdempotencyKey = null), command.copy(pushIdempotencyKey = Some(" ")),
        command.copy(pushIdempotencyKey = None), command.copy(expectedRevision = "5"),
        command.copy(fastForwardIdempotencyKey = Some("sync-fast-forward-1"))).foreach { invalid =>
        fixture.converge(invalid) shouldBe a[Consequence.Failure[?]]
      }
      fixture.pushLoads shouldBe 0
      fixture.convergenceLoads shouldBe 0
      fixture.appends shouldBe 6
    }

    "reconcile a stored LOCAL_AHEAD completion after an uncertain append response" in {
      val fixture = new Fixture(localAhead = true)
      fixture.prepareConvergence()
      fixture.rejectCompletionAppend = true
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.stored.get.lifecycle shouldBe Lifecycle.Active
      fixture.rejectCompletionAppend = false
      fixture.failAfterCompletionAppend = true
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.stored.get.lifecycle shouldBe Lifecycle.Completed
      fixture.failAfterCompletionAppend = false
      take(fixture.converge())
      fixture.appends shouldBe 7
    }

    "complete both admitted routes once and retain original history on replay" in {
      Vector(true, false).foreach { equal =>
        val fixture = new Fixture(equal = equal)
        fixture.prepareConvergence()
        val prefix = fixture.stored.get.history
        val terminal = take(fixture.converge())
        val record = fixture.stored.get
        record.lifecycle shouldBe Lifecycle.Completed
        record.currentProgression shouldBe None
        record.revision shouldBe InstanceRevision(if (equal) 6L else 7L)
        record.history.take(prefix.size) shouldBe prefix
        record.correlation shouldBe prefix.head.correlation
        val wire = take(WorkflowTerminalJsonV1.encodeC(terminal, RepositorySyncConvergenceJson.payloadCodec))
        wire should include (if (equal) "ALREADY_SYNCHRONIZED" else "SYNCHRONIZED")
        take(WorkflowTerminalJsonV1.decodeC(wire, RepositorySyncConvergenceJson.payloadCodec)) shouldBe terminal
        take(fixture.converge()) shouldBe terminal
        fixture.appends shouldBe record.revision.value.toInt
        if (equal) fixture.fastForwardLoads shouldBe 0
      }
    }

    "label equal tips after a checkpoint as synchronized" in {
      val fixture = new Fixture(dirty = true, equal = true)
      fixture.prepareConvergence()
      val wire = take(WorkflowTerminalJsonV1.encodeC(take(fixture.converge()), RepositorySyncConvergenceJson.payloadCodec))
      wire should include ("\"outcome\":\"SYNCHRONIZED\"")
      fixture.checkpointLoads should be > 0
      fixture.fastForwardLoads shouldBe 0
    }

    "require exact persisted predecessor before reading convergence evidence" in {
      val fixture = new Fixture
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.prepare()
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.fastForwardLoads shouldBe 0
      take(fixture.forward())
      val record = fixture.stored.get
      fixture.stored = Some(record.copy(history = record.history.updated(5,
        record.history(5).copy(causation = CausationReference("tampered")))))
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.convergenceLoads shouldBe 0
      fixture.stored = Some(record)
      take(fixture.converge())
    }

    "reject local-ahead and diverged routes without reading downstream evidence" in {
      val local = new Fixture
      local.relationTransform = _.copy(mergeBases = Vector(local.fetchedTip), localAhead = 2L, remoteAhead = 0L)
      val diverged = new Fixture(dirty = true)
      Vector(local, diverged).foreach { fixture =>
        fixture.prepare()
        fixture.converge(fixture.convergenceCommand.copy(fastForwardIdempotencyKey = None, expectedRevision = "5")) shouldBe
          a[Consequence.Failure[?]]
        fixture.fastForwardLoads shouldBe 0
        fixture.convergenceLoads shouldBe 0
        fixture.stored.get.revision shouldBe InstanceRevision(5L)
      }
    }

    "reject malformed revisions, optional keys and route mismatches without appending" in {
      val fixture = new Fixture(equal = true)
      fixture.prepareConvergence()
      val command = fixture.convergenceCommand
      Vector(null, command.copy(expectedRevision = "6"), command.copy(idempotencyKey = " "),
        command.copy(relationCommand = null), command.copy(fastForwardIdempotencyKey = null),
        command.copy(fastForwardIdempotencyKey = Some(" "), expectedRevision = "6"),
        command.copy(fastForwardIdempotencyKey = Some("wrong"), expectedRevision = "6")).foreach { invalid =>
        fixture.converge(invalid) shouldBe a[Consequence.Failure[?]]
      }
      fixture.convergenceLoads shouldBe 0
      fixture.appends shouldBe 5
    }

    "retain the predecessor on missing, failed, advanced-tip and dirty verification evidence" in {
      val fixture = new Fixture
      fixture.prepareConvergence()
      val prefix = fixture.stored.get.history
      fixture.convergenceMissing = true
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.convergenceMissing = false
      fixture.convergenceFailure = true
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.convergenceFailure = false
      fixture.convergenceFetchTransform = _.copy(fetchedTipOid = "0" * 40)
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.convergenceFetchTransform = identity
      fixture.verificationTransform = v => v.copy(state = v.state.copy(dirtyPaths = Vector("new.txt")))
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.stored.get.history shouldBe prefix
      fixture.stored.get.lifecycle shouldBe Lifecycle.Active
      fixture.verificationTransform = identity
      take(fixture.converge())
    }

    "reject changed command or evidence on a completed replay without duplicating history" in {
      val fixture = new Fixture
      fixture.prepareConvergence()
      val terminal = take(fixture.converge())
      val completed = fixture.stored.get
      fixture.converge(fixture.convergenceCommand.copy(idempotencyKey = "different")) shouldBe a[Consequence.Failure[?]]
      fixture.converge(fixture.convergenceCommand.copy(fastForwardIdempotencyKey = Some("different"))) shouldBe a[Consequence.Failure[?]]
      fixture.verificationTransform = _.copy(graphInspectionDigest = "d" * 64)
      fixture.converge() shouldBe a[Consequence.Failure[?]]
      fixture.stored shouldBe Some(completed)
      fixture.appends shouldBe 7
      fixture.verificationTransform = identity
      take(fixture.converge()) shouldBe terminal
    }

    "leave active state on append rejection and reconcile a stored completion after response failure" in {
      Vector(true, false).foreach { equal =>
        val fixture = new Fixture(equal = equal)
        fixture.prepareConvergence()
        fixture.rejectCompletionAppend = true
        fixture.converge() shouldBe a[Consequence.Failure[?]]
        fixture.stored.get.lifecycle shouldBe Lifecycle.Active
        fixture.rejectCompletionAppend = false
        fixture.failAfterCompletionAppend = true
        fixture.converge() shouldBe a[Consequence.Failure[?]]
        fixture.stored.get.lifecycle shouldBe Lifecycle.Completed
        fixture.failAfterCompletionAppend = false
        take(fixture.converge())
        fixture.appends shouldBe (if (equal) 6 else 7)
      }
    }

    "reject incompatible typed terminal payloads" in {
      val fixture = new Fixture(equal = true)
      fixture.prepareConvergence()
      val wire = take(WorkflowTerminalJsonV1.encodeC(take(fixture.converge()), RepositorySyncConvergenceJson.payloadCodec))
      Vector(wire.replace("ALREADY_SYNCHRONIZED", "UNKNOWN"),
        wire.replace(RepositorySyncConvergenceJson.schema, "unsupported"),
        wire.replace("\"tipOid\":\"" + fixture.fetchedTip + "\"", "\"tipOid\":\"invalid\""),
        wire.replace("\"outcome\":", "\"extra\":1,\"outcome\":"))
        .foreach(value => WorkflowTerminalJsonV1.decodeC(value, RepositorySyncConvergenceJson.payloadCodec) shouldBe
          a[Consequence.Failure[?]])
    }

    "execute the generated public operation through direct and CLI transports as the same terminal" in {
      Vector(new Fixture, new Fixture(localAhead = true)).foreach { fixture =>
      fixture.prepareConvergence()
      val operation = take(RepositorySyncConvergenceServiceOperation.bindC(fixture.selections, fixture.mappings,
        fixture.snapshots, fixture.scopes, fixture.fetches, fixture.preservations, fixture.relations,
        fixture.fastForwards, fixture.pushes, fixture.convergences, definition, configuration, fixture.instances))
      val wire = RepositorySyncConvergenceServiceOperation.encodeCommand(fixture.convergenceCommand)
      take(RepositorySyncConvergenceServiceOperation.decodeCommandC(wire)) shouldBe fixture.convergenceCommand
      val request = Request.of(component = SmWorkflowComponent.name, service = "RepositorySyncService",
        operation = "admitConvergence", arguments = List(Argument("arg1", wire)))
      Vector("not-json", "{}", wire.dropRight(1) + ",\"extra\":1}",
        wire.replace(RepositorySyncConvergenceServiceOperation.wireSchema, "unsupported"),
        wire.replace(RepositorySyncConvergenceServiceOperation.wireSchema, "sm-workflow.repository-sync-convergence.v1"),
        io.circe.parser.parse(wire).toOption.get.mapObject(_.remove("pushIdempotencyKey")).noSpaces,
        io.circe.parser.parse(wire).toOption.get.mapObject(_.add(
          "fastForwardIdempotencyKey", io.circe.Json.fromInt(1))).noSpaces,
        wire.replace("\"pushIdempotencyKey\":", "\"outcome\":\"SYNCHRONIZED\",\"pushIdempotencyKey\":"),
        io.circe.parser.parse(wire).toOption.get.mapObject(_.add(
          "pushIdempotencyKey", io.circe.Json.fromInt(1))).noSpaces,
        io.circe.parser.parse(wire).toOption.get.mapObject(_.add(
          "fastForwardIdempotencyKey", io.circe.Json.fromString("forward")).add(
          "pushIdempotencyKey", io.circe.Json.fromString("push"))).noSpaces,
        wire.replace(RepositorySyncRelationAdmissionServiceOperation.wireSchema, "unsupported-nested"))
        .foreach(value => operation.createOperationRequest(request.copy(arguments = List(Argument("arg1", value)))) shouldBe
          a[Consequence.Failure[?]])
      operation.createOperationRequest(request.copy(operation = "admitFastForward")) shouldBe a[Consequence.Failure[?]]
      operation.createOperationRequest(request.copy(service = Some("OtherService"))) shouldBe a[Consequence.Failure[?]]
      val service = spec.ServiceDefinition("RepositorySyncService", spec.OperationDefinitionGroup(NonEmptyVector.of(operation)))
      val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(name = "sm-workflow-convergence", version = None,
        configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
        aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
      try {
        val component = new Component() {}
        component.initialize(ComponentInit(subsystem, Component.Core.create(SmWorkflowComponent.name,
          SmWorkflowComponent.componentId, ComponentInstanceId.default(SmWorkflowComponent.componentId),
          Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))), ComponentOrigin.Builtin))
        subsystem.add(component)
        val response = take(subsystem.executeOperationResponse(request)) match {
          case OperationResponse.Scalar(value: String) => value
          case other => fail(s"Expected typed terminal response, found $other")
        }
        val terminal = take(WorkflowTerminalJsonV1.decodeC(response, RepositorySyncConvergenceJson.payloadCodec))
        terminal.handle.instanceIdentity shouldBe fixture.stored.get.identity
        response should include ("SYNCHRONIZED")
        take(new CncfRuntime().executeCommandResponse(subsystem,
          Array(s"${SmWorkflowComponent.name}/RepositorySyncService/admitConvergence", "--json", wire))).print shouldBe response
        fixture.appends shouldBe 7
      } finally subsystem.shutdown()
      }
    }
  }
}
