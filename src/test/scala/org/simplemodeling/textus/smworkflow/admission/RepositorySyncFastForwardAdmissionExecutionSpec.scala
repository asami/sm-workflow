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
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncBoundaryAssessmentCommand, RepositorySyncFetchAdmissionCommand, RepositorySyncPreservationAdmissionCommand, RepositorySyncRelationAdmissionCommand, RepositorySyncFastForwardAdmissionCommand, RepositorySyncStartCommand, RepositorySyncStartInput}

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncFastForwardAdmissionExecutionSpec extends AnyWordSpec with Matchers {
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

  private final class Fixture(dirty: Boolean = false, staged: Boolean = false, equal: Boolean = false) {
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
            localTipOid, fetchedTipOid, Vector(snapshot.headOid),
            if (equal) 0L else if (dirty) 1L else 0L, if (equal) 0L else 3L,
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
        if (rejectForwardAppend && expectedRevision == InstanceRevision(5L))
          Consequence.stateConflict("RepositorySync fixture fast-forward append failure")
        else stored match {
          case Some(current) if config == configuration && current.identity == identity =>
            current.appendC(expectedRevision, entry).flatMap { next =>
              appends += 1
              stored = Some(next)
              if (failAfterForwardAppend && expectedRevision == InstanceRevision(5L))
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
    def forward(command: RepositorySyncFastForwardAdmissionCommand = fastForwardCommand) =
      RepositorySyncFastForwardAdmissionExecution.admitC(command, selections, mappings,
        snapshots, scopes, fetches, preservations, relations, fastForwards, definition, configuration, instances)
  }

  "RepositorySync fast-forward progression" should {
    "require persisted relation history before loading any fast-forward evidence" in {
      val fixture = new Fixture
      fixture.forward() shouldBe a[Consequence.Failure[?]]
      fixture.prepareFetched()
      fixture.forward() shouldBe a[Consequence.Failure[?]]
      take(fixture.preserve())
      fixture.forward() shouldBe a[Consequence.Failure[?]]
      fixture.fastForwardLoads shouldBe 0
      take(fixture.classify())
      take(fixture.forward())
      fixture.stored.get.revision shouldBe InstanceRevision(6L)
      fixture.appends shouldBe 6
    }

    "append revision six once, preserve prior history and return a nonterminal WAIT on replay" in {
      val fixture = new Fixture
      fixture.prepare()
      val prefix = fixture.stored.get.history
      val admitted = take(fixture.forward())
      val wait = admitted.current.asInstanceOf[WorkflowContinuation.Wait]
      wait.expectedRevision.value shouldBe "6"
      wait.presentation.nextAction shouldBe Some("Verify convergence")
      fixture.stored.get.currentProgression shouldBe Some(ProgressionReference("sync.FastForwardAdmitted"))
      fixture.stored.get.lifecycle shouldBe Lifecycle.Active
      fixture.stored.get.history.take(5) shouldBe prefix
      fixture.stored.get.correlation shouldBe prefix.head.correlation
      take(WorkflowWaitJsonV1.decodeC(take(WorkflowWaitJsonV1.encodeC(admitted)))) shouldBe admitted
      take(fixture.forward()) shouldBe admitted
      fixture.appends shouldBe 6
    }

    "reject every non-REMOTE_AHEAD route without reading fast-forward evidence" in {
      val equal = new Fixture(equal = true)
      val localAhead = new Fixture
      localAhead.relationTransform = _.copy(mergeBases = Vector(localAhead.fetchedTip), localAhead = 2L, remoteAhead = 0L)
      val diverged = new Fixture(dirty = true)
      Vector(equal, localAhead, diverged).foreach { fixture =>
        fixture.prepare()
        val prefix = fixture.stored.get.history
        fixture.forward() shouldBe a[Consequence.Failure[?]]
        fixture.fastForwardLoads shouldBe 0
        fixture.stored.get.history shouldBe prefix
        fixture.stored.get.revision shouldBe InstanceRevision(5L)
      }
    }

    "retain revision five on missing, failed or inconsistent pre/receipt/post evidence" in {
      Vector("freeze", "receipt", "post").foreach { stage =>
        val fixture = new Fixture
        fixture.prepare()
        fixture.missingStage = stage
        fixture.forward() shouldBe a[Consequence.Failure[?]]
        fixture.missingStage = ""
        fixture.failureStage = stage
        fixture.forward() shouldBe a[Consequence.Failure[?]]
        fixture.failureStage = ""
        fixture.stored.get.revision shouldBe InstanceRevision(5L)
        fixture.appends shouldBe 5
        take(fixture.forward())
        fixture.appends shouldBe 6
      }
      val invalid = new Fixture
      invalid.prepare()
      invalid.frozenTransform = value => value.copy(before = value.before.copy(pendingOperations = Vector("REBASE")))
      invalid.forward() shouldBe a[Consequence.Failure[?]]
      invalid.frozenTransform = identity
      invalid.forwardReceiptTransform = _.copy(resultTipOid = "0" * 40)
      invalid.forward() shouldBe a[Consequence.Failure[?]]
      invalid.forwardReceiptTransform = identity
      invalid.postStateTransform = _.copy(dirtyPaths = Vector("new.txt"))
      invalid.forward() shouldBe a[Consequence.Failure[?]]
      invalid.stored.get.revision shouldBe InstanceRevision(5L)
      invalid.postStateTransform = identity
      take(invalid.forward())
      invalid.appends shouldBe 6
    }

    "reject altered commands and upstream evidence before fast-forward loads" in {
      val fixture = new Fixture
      fixture.prepare()
      val commands = Vector(
        fastForwardCommand.copy(expectedRevision = "6"),
        fastForwardCommand.copy(idempotencyKey = ""),
        fastForwardCommand.copy(relationCommand = null),
        fastForwardCommand.copy(relationCommand = relationCommand.copy(expectedRevision = "5")),
        fastForwardCommand.copy(relationCommand = relationCommand.copy(idempotencyKey = "other-relation")),
        fastForwardCommand.copy(relationCommand = relationCommand.copy(preservationCommand =
          preservationCommand.copy(idempotencyKey = "other-preservation"))),
        fastForwardCommand.copy(relationCommand = relationCommand.copy(preservationCommand =
          preservationCommand.copy(fetchCommand = fetchCommand.copy(idempotencyKey = "other-fetch")))),
        fastForwardCommand.copy(relationCommand = relationCommand.copy(preservationCommand =
          preservationCommand.copy(fetchCommand = fetchCommand.copy(boundaryCommand =
            boundaryCommand.copy(idempotencyKey = "other-boundary"))))))
      commands.foreach(fixture.forward(_) shouldBe a[Consequence.Failure[?]])
      fixture.fetchReceiptValue = fixture.fetchReceipt.copy(executionDigest = "2" * 64)
      fixture.forward() shouldBe a[Consequence.Failure[?]]
      fixture.fetchReceiptValue = fixture.fetchReceipt
      fixture.cleanTransform = _.copy(inspectionDigest = "0" * 64)
      fixture.forward() shouldBe a[Consequence.Failure[?]]
      fixture.cleanTransform = identity
      fixture.relationTransform = _.copy(remoteAhead = 4L)
      fixture.forward() shouldBe a[Consequence.Failure[?]]
      fixture.fastForwardLoads shouldBe 0
      fixture.relationTransform = identity
      take(fixture.forward())
      fixture.appends shouldBe 6
    }

    "reject conflicting fast-forward keys and evidence after revision six without another append" in {
      val fixture = new Fixture
      fixture.prepare()
      val admitted = take(fixture.forward())
      fixture.forward(fastForwardCommand.copy(idempotencyKey = "other-forward")) shouldBe a[Consequence.Failure[?]]
      fixture.forwardReceiptTransform = _.copy(executionDigest = "0" * 64)
      fixture.forward() shouldBe a[Consequence.Failure[?]]
      fixture.forwardReceiptTransform = identity
      fixture.postStateTransform = _.copy(inspectionDigest = "0" * 64)
      fixture.forward() shouldBe a[Consequence.Failure[?]]
      fixture.postStateTransform = identity
      fixture.frozenTransform = value => value.copy(before = value.before.copy(inspectionDigest = "0" * 64))
      fixture.forward() shouldBe a[Consequence.Failure[?]]
      fixture.frozenTransform = identity
      take(fixture.forward()) shouldBe admitted
      fixture.appends shouldBe 6
    }

    "reject a changed progression in the persisted relation instance before evidence loads" in {
      val fixture = new Fixture
      fixture.prepare()
      val original = fixture.stored
      fixture.stored = original.map(_.copy(currentProgression = Some(ProgressionReference("sync.FastForwardAdmitted"))))
      fixture.forward() shouldBe a[Consequence.Failure[?]]
      fixture.fastForwardLoads shouldBe 0
      fixture.stored = original
      take(fixture.forward())
      fixture.appends shouldBe 6
    }

    "retain the classified instance on append failure and allow an exact retry" in {
      val fixture = new Fixture
      fixture.prepare()
      fixture.rejectForwardAppend = true
      fixture.forward() shouldBe a[Consequence.Failure[?]]
      fixture.stored.get.revision shouldBe InstanceRevision(5L)
      fixture.appends shouldBe 5
      fixture.rejectForwardAppend = false
      val admitted = take(fixture.forward())
      take(fixture.forward()) shouldBe admitted
      fixture.appends shouldBe 6
    }

    "reconcile a persisted append with a failed response without falsely rolling back or duplicating it" in {
      val fixture = new Fixture
      fixture.prepare()
      fixture.failAfterForwardAppend = true
      fixture.forward() shouldBe a[Consequence.Failure[?]]
      fixture.stored.get.revision shouldBe InstanceRevision(6L)
      fixture.appends shouldBe 6
      val reconciled = take(fixture.forward())
      take(fixture.forward()) shouldBe reconciled
      fixture.appends shouldBe 6
    }

    "reject incomplete and incompatible public bindings without progressing" in {
      val fixture = new Fixture
      fixture.prepare()
      RepositorySyncFastForwardAdmissionServiceOperation.bindC(
        fixture.selections, fixture.mappings, fixture.snapshots, fixture.scopes,
        fixture.fetches, fixture.preservations, fixture.relations, null,
        definition, configuration, fixture.instances) shouldBe a[Consequence.Failure[?]]
      RepositorySyncFastForwardAdmissionServiceOperation.bindC(
        fixture.selections, fixture.mappings, fixture.snapshots, fixture.scopes,
        fixture.fetches, fixture.preservations, fixture.relations, fixture.fastForwards,
        take(SplitPhaseDefinitionBinding.bindC), configuration, fixture.instances) shouldBe a[Consequence.Failure[?]]
      fixture.fastForwardLoads shouldBe 0
      fixture.stored.get.revision shouldBe InstanceRevision(5L)
    }

    "serve the generated fast-forward Operation and equivalent strict CLI JSON" in {
      val fixture = new Fixture
      fixture.prepare()
      val operation = take(RepositorySyncFastForwardAdmissionServiceOperation.bindC(
        fixture.selections, fixture.mappings, fixture.snapshots, fixture.scopes,
        fixture.fetches, fixture.preservations, fixture.relations, fixture.fastForwards,
        definition, configuration, fixture.instances))
      val wire = RepositorySyncFastForwardAdmissionServiceOperation.encodeCommand(fastForwardCommand)
      take(RepositorySyncFastForwardAdmissionServiceOperation.decodeCommandC(wire)) shouldBe fastForwardCommand
      RepositorySyncFastForwardAdmissionServiceOperation.decodeCommandC(null) shouldBe a[Consequence.Failure[?]]
      val request = Request.of(component = SmWorkflowComponent.name,
        service = "RepositorySyncService", operation = "admitFastForward",
        arguments = List(Argument("arg1", wire)))
      operation.createOperationRequest(request).isSuccess shouldBe true
      val malformed = Vector("not-json", "{}", wire.dropRight(1) + ",\"extra\":1}",
        wire.replace(RepositorySyncFastForwardAdmissionServiceOperation.wireSchema, "unsupported"),
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
        name = "sm-workflow-repository-fast-forward", version = None,
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
        direct.current.asInstanceOf[WorkflowContinuation.Wait].presentation.nextAction shouldBe Some("Verify convergence")
        val selector = s"${SmWorkflowComponent.name}/RepositorySyncService/admitFastForward"
        take(new CncfRuntime().executeCommandResponse(subsystem, Array(selector, "--json", wire))).print shouldBe directWire
        fixture.stored.get.revision shouldBe InstanceRevision(6L)
        fixture.appends shouldBe 6
      } finally subsystem.shutdown()
    }
  }
}
