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
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncBoundaryAssessmentCommand, RepositorySyncFetchAdmissionCommand, RepositorySyncPreservationAdmissionCommand, RepositorySyncStartCommand, RepositorySyncStartInput}

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncPreservationAdmissionExecutionSpec extends AnyWordSpec with Matchers {
  import ProfileStartAdmission.*
  import RepositorySyncCheckpointAssessment.*

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"RepositorySync preservation progression failed: $failure")
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

  private final class Fixture(dirty: Boolean = false, staged: Boolean = false) {
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
    val fetchReceipt = RepositorySyncFetchAssessment.Receipt(
      admittedBoundary.digest, admittedScope.digest, "repository-1", "worktree-1", "main",
      "origin", "main", "remote-1", "f" * 40, Some("9" * 40), "f" * 40,
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
    var stored: Option[InstanceRecord] = None
    var appends = 0
    var rejectPreservationAppend = false
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
        if (rejectPreservationAppend && expectedRevision == InstanceRevision(3L))
          Consequence.stateConflict("RepositorySync fixture preservation append failure")
        else stored match {
          case Some(current) if config == configuration && current.identity == identity =>
            current.appendC(expectedRevision, entry).map { next =>
              appends += 1
              stored = Some(next)
              next
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
    def prepare(): Unit = { take(start()); take(boundary()); take(fetch()); () }
    def preserve(command: RepositorySyncPreservationAdmissionCommand = preservationCommand) =
      RepositorySyncPreservationAdmissionExecution.admitC(command, selections, mappings,
        snapshots, scopes, fetches, preservations, definition, configuration, instances)
  }

  "RepositorySync preservation progression" should {
    "require the admitted fetch before loading evidence and append one clean-route progression" in {
      val fixture = new Fixture
      fixture.preserve() shouldBe a[Consequence.Failure[?]]
      take(fixture.start())
      fixture.preserve() shouldBe a[Consequence.Failure[?]]
      take(fixture.boundary())
      fixture.preserve() shouldBe a[Consequence.Failure[?]]
      fixture.preservationLoads shouldBe 0
      take(fixture.fetch())
      val admitted = take(fixture.preserve())
      admitted.current shouldBe a[WorkflowContinuation.Wait]
      fixture.stored.get.currentProgression shouldBe Some(ProgressionReference("sync.LocalStatePreserved"))
      fixture.stored.get.revision shouldBe InstanceRevision(4L)
      fixture.checkpointLoads shouldBe 0
      take(WorkflowWaitJsonV1.decodeC(take(WorkflowWaitJsonV1.encodeC(admitted)))) shouldBe admitted
      take(fixture.preserve()) shouldBe admitted
      fixture.appends shouldBe 4
    }

    "admit dirty and complete-fully-staged checkpoint receipts without changing prior history" in {
      Vector(false, true).foreach { staged =>
        val fixture = new Fixture(dirty = true, staged = staged)
        fixture.prepare()
        val prefix = fixture.stored.get.history
        val admitted = take(fixture.preserve())
        fixture.stored.get.history.take(3) shouldBe prefix
        fixture.stored.get.revision shouldBe InstanceRevision(4L)
        fixture.checkpointLoads shouldBe 1
        take(fixture.preserve()) shouldBe admitted
        fixture.receiptTransform = _.copy(executionDigest = "9" * 64)
        fixture.preserve() shouldBe a[Consequence.Failure[?]]
        fixture.receiptTransform = identity
        take(fixture.preserve()) shouldBe admitted
        fixture.appends shouldBe 4
      }
    }

    "leave revision three on missing or invalid checkpoint and clean-gate evidence" in {
      val fixture = new Fixture(dirty = true)
      fixture.prepare()
      fixture.receiptTransform = _.copy(purpose = "ACCEPTANCE")
      fixture.preserve() shouldBe a[Consequence.Failure[?]]
      fixture.receiptTransform = identity
      fixture.cleanTransform = _.copy(localHeadOid = "0" * 40)
      fixture.preserve() shouldBe a[Consequence.Failure[?]]
      fixture.cleanTransform = identity
      fixture.cleanMissing = true
      fixture.preserve() shouldBe a[Consequence.Failure[?]]
      fixture.stored.get.revision shouldBe InstanceRevision(3L)
      fixture.appends shouldBe 3
      fixture.cleanMissing = false
      take(fixture.preserve())
      fixture.appends shouldBe 4
    }

    "reject changed command keys, fetch evidence, and preservation evidence on replay" in {
      val fixture = new Fixture
      fixture.prepare()
      val commands = Vector(preservationCommand.copy(expectedRevision = "4"),
        preservationCommand.copy(fetchCommand = fetchCommand.copy(idempotencyKey = "other-fetch")),
        preservationCommand.copy(fetchCommand = fetchCommand.copy(boundaryCommand =
          boundaryCommand.copy(idempotencyKey = "other-boundary"))))
      commands.foreach(fixture.preserve(_) shouldBe a[Consequence.Failure[?]])
      fixture.fetchReceiptValue = fixture.fetchReceipt.copy(executionDigest = "2" * 64)
      fixture.preserve() shouldBe a[Consequence.Failure[?]]
      fixture.preservationLoads shouldBe 0
      fixture.fetchReceiptValue = fixture.fetchReceipt
      take(fixture.preserve())
      fixture.preserve(preservationCommand.copy(idempotencyKey = "other-preservation")) shouldBe
        a[Consequence.Failure[?]]
      fixture.cleanTransform = _.copy(inspectionDigest = "9" * 64)
      fixture.preserve() shouldBe a[Consequence.Failure[?]]
      fixture.cleanTransform = identity
      take(fixture.preserve())
      fixture.appends shouldBe 4
    }

    "retain the fetched instance on append failure and allow an exact retry" in {
      val fixture = new Fixture(dirty = true)
      fixture.prepare()
      fixture.rejectPreservationAppend = true
      fixture.preserve() shouldBe a[Consequence.Failure[?]]
      fixture.stored.get.revision shouldBe InstanceRevision(3L)
      fixture.appends shouldBe 3
      fixture.rejectPreservationAppend = false
      val admitted = take(fixture.preserve())
      take(fixture.preserve()) shouldBe admitted
      fixture.appends shouldBe 4
    }

    "reject incomplete or incompatible public bindings without progressing the instance" in {
      val fixture = new Fixture
      fixture.prepare()
      RepositorySyncPreservationAdmissionServiceOperation.bindC(
        fixture.selections, fixture.mappings, fixture.snapshots, fixture.scopes,
        fixture.fetches, null, definition, configuration, fixture.instances) shouldBe
        a[Consequence.Failure[?]]
      RepositorySyncPreservationAdmissionServiceOperation.bindC(
        fixture.selections, fixture.mappings, fixture.snapshots, fixture.scopes,
        fixture.fetches, fixture.preservations,
        take(SplitPhaseDefinitionBinding.bindC),
        configuration, fixture.instances) shouldBe a[Consequence.Failure[?]]
      fixture.preservationLoads shouldBe 0
      fixture.stored.get.revision shouldBe InstanceRevision(3L)
    }

    "serve the generated preservation Operation and equivalent strict CLI JSON for both routes" in {
      Vector(false, true).foreach { dirty =>
        val fixture = new Fixture(dirty = dirty)
        fixture.prepare()
        val operation = take(RepositorySyncPreservationAdmissionServiceOperation.bindC(
          fixture.selections, fixture.mappings, fixture.snapshots, fixture.scopes,
          fixture.fetches, fixture.preservations, definition, configuration, fixture.instances))
        val wire = RepositorySyncPreservationAdmissionServiceOperation.encodeCommand(preservationCommand)
        take(RepositorySyncPreservationAdmissionServiceOperation.decodeCommandC(wire)) shouldBe preservationCommand
        val request = Request.of(component = SmWorkflowComponent.name,
          service = "RepositorySyncService", operation = "admitLocalPreservation",
          arguments = List(Argument("arg1", wire)))
        operation.createOperationRequest(request).isSuccess shouldBe true
        val malformed = Vector("not-json", "{}", wire.dropRight(1) + ",\"extra\":1}",
          wire.replace(RepositorySyncPreservationAdmissionServiceOperation.wireSchema, "unsupported"),
          wire.replace("\"fetchCommand\":{", "\"fetchCommand\":null,\"other\":{"))
        malformed.foreach { value =>
          operation.createOperationRequest(request.copy(arguments = List(Argument("arg1", value)))) shouldBe
            a[Consequence.Failure[?]]
        }
        operation.createOperationRequest(request.copy(operation = "admitTrackingFetch")) shouldBe
          a[Consequence.Failure[?]]
        operation.createOperationRequest(request.copy(service = Some("OtherService"))) shouldBe
          a[Consequence.Failure[?]]
        val service = spec.ServiceDefinition("RepositorySyncService",
          spec.OperationDefinitionGroup(NonEmptyVector.of(operation)))
        val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
        val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
          name = "sm-workflow-repository-preservation", version = None,
          configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
          aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
        try {
          val component = new Component() {}
          component.initialize(ComponentInit(subsystem,
            Component.Core.create(SmWorkflowComponent.name, SmWorkflowComponent.componentId,
              ComponentInstanceId.default(SmWorkflowComponent.componentId), protocol),
            ComponentOrigin.Builtin))
          subsystem.add(component)
          val directWire = take(subsystem.executeOperationResponse(request)) match {
            case OperationResponse.Scalar(value: String) => value
            case other => fail(s"Expected public WAIT response, found $other")
          }
          take(WorkflowWaitJsonV1.decodeC(directWire)).handle.instanceIdentity shouldBe fixture.stored.get.identity
          val selector = s"${SmWorkflowComponent.name}/RepositorySyncService/admitLocalPreservation"
          take(new CncfRuntime().executeCommandResponse(subsystem,
            Array(selector, "--json", wire))).print shouldBe directWire
          fixture.stored.get.revision shouldBe InstanceRevision(4L)
          fixture.appends shouldBe 4
        } finally subsystem.shutdown()
      }
    }
  }

}
