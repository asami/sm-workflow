package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import org.goldenport.Consequence
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.{Component, ComponentInit, ComponentInstanceId, ComponentOrigin}
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem
import org.goldenport.cncf.testutil.RuntimeBindingAdmissionFixture
import org.goldenport.cncf.workflow.{StateMachineOperationIdentity, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.WorkflowContinuation
import org.goldenport.cncf.workflow.WorkflowWaitJsonV1
import org.goldenport.configuration.{Configuration as GenericConfiguration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncBoundaryAssessmentCommand, RepositorySyncStartCommand, RepositorySyncStartInput}

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncStartExecutionSpec extends AnyWordSpec with Matchers {
  import ProfileStartAdmission.*

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"RepositorySync Start fixture failed: $failure")
  }

  private val input = RepositorySyncStartInput(
    "repository-1", "main", "origin", "main", "remote-1", "FULL_STATE", "policy-v1")
  private val command = RepositorySyncStartCommand(
    input, "sync-selection-1", "participant-1", "sync-start-1")
  private val boundaryCommand = RepositorySyncBoundaryAssessmentCommand(
    command, "1", "sync-boundary-1")
  private val hash = "a" * 64
  private val snapshot = RepositorySyncBoundaryAssessment.Snapshot(
    "snapshot-1", "repository-1", "worktree-1", "main", "origin", "main",
    "remote-1", "policy-v1", "b" * 40, hash, "c" * 64, "d" * 64,
    Vector.empty, "EMPTY", detachedHead = false, pendingMerge = false)
  private val snapshotDigest = take(RepositorySyncBoundaryAssessment.assessC(input,
    new RepositorySyncBoundaryAssessment.Port {
      def captureC(repositoryIdentity: String) = Consequence.success(Some(snapshot))
    })).digest
  private val scope = RepositorySyncDirtyScopeAssessment.Evidence(
    snapshotDigest, "policy-v1", Vector.empty, Vector.empty, Vector.empty, "e" * 64)
  private val human = Selection(
    "sync-selection-1",
    StateMachineOperationIdentity("RepositorySyncService", "startRepositorySync"),
    WorkflowDefinitionIdentity("RepositorySyncWorkflow"),
    WorkflowDefinitionRevision("repository-sync-native-convergence-v10"),
    "participant-1", SelectionOrigin.ExplicitHuman, "sync-start-1", None)
  private val definition = take(RepositorySyncDefinitionBinding.bindC)
  private val configuration = Configuration(
    NamedWorkflowStore("workflow-primary", WorkflowStoreIdentity("workflow-store")),
    MigrationPolicy("fixture-v1"), RetentionPolicy("fixture-v1"), LeasePolicy("fixture-v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store"))
  )

  private final class Fixture {
    var selected: Option[Selection] = Some(human)
    var mapping: Option[RepositorySyncStartInput] = Some(input)
    var captured: Option[RepositorySyncBoundaryAssessment.Snapshot] = Some(snapshot)
    var dirtyScope: Option[RepositorySyncDirtyScopeAssessment.Evidence] = Some(scope)
    var stored: Option[InstanceRecord] = None
    var creates = 0
    var appends = 0
    var failAppendOnce = false

    val selections = new SelectionPort {
      def loadC(selectionId: String): Consequence[Option[Selection]] =
        Consequence.success(selected.filter(_.selectionId == selectionId))
    }
    val mappings = new RepositorySyncStartExecution.TrackingMappingPort {
      def loadC(repositoryIdentity: String): Consequence[Option[RepositorySyncStartInput]] =
        Consequence.success(mapping.filter(_.repositoryIdentity == repositoryIdentity))
    }
    val snapshots = new RepositorySyncBoundaryAssessment.Port {
      def captureC(repositoryIdentity: String): Consequence[Option[RepositorySyncBoundaryAssessment.Snapshot]] =
        Consequence.success(captured.filter(_.repositoryIdentity == repositoryIdentity))
    }
    val scopes = new RepositorySyncDirtyScopeAssessment.Port {
      def loadC(repositoryIdentity: String, digest: String):
        Consequence[Option[RepositorySyncDirtyScopeAssessment.Evidence]] =
        Consequence.success(dirtyScope)
    }
    val instances = new WorkflowInstancePersistence {
      def create(config: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
        if (config != configuration || stored.nonEmpty)
          Consequence.stateConflict("RepositorySync fixture create conflict")
        else record.validateC.map { valid =>
          creates += 1
          stored = Some(valid)
          valid
        }

      def load(config: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
        if (config != configuration) Consequence.stateConflict("RepositorySync fixture store mismatch")
        else Consequence.success(stored.filter(_.identity == identity))

      def append(config: Configuration, identity: InstanceIdentity,
        expectedRevision: InstanceRevision, entry: HistoryEntry): Consequence[InstanceRecord] =
        if (config != configuration || failAppendOnce) {
          failAppendOnce = false
          Consequence.stateConflict("RepositorySync fixture planned append failure")
        } else stored match {
          case Some(current) if current.identity == identity =>
            current.appendC(expectedRevision, entry).map { next =>
              appends += 1
              stored = Some(next)
              next
            }
          case _ => Consequence.stateConflict("RepositorySync fixture instance is missing")
        }
    }

    def start(value: RepositorySyncStartCommand = command) =
      RepositorySyncStartExecution.startC(
        value, selections, mappings, definition, configuration, instances)

    def assess(value: RepositorySyncBoundaryAssessmentCommand = boundaryCommand) =
      RepositorySyncBoundaryAssessmentExecution.assessC(value, selections,
        mappings, snapshots, scopes, definition, configuration, instances)
  }

  "RepositorySync Start execution" should {
    "create one durable Active instance without invoking Git and replay the same Start" in {
      val fixture = new Fixture
      val started = take(fixture.start())
      started.handle.workflowIdentity shouldBe definition.workflowIdentity
      started.handle.workflowRevision shouldBe definition.workflowRevision
      started.handle.instanceIdentity shouldBe fixture.stored.get.identity
      started.current shouldBe a[WorkflowContinuation.Wait]
      take(WorkflowWaitJsonV1.decodeC(take(WorkflowWaitJsonV1.encodeC(started)))) shouldBe started
      fixture.stored.get.lifecycle shouldBe Lifecycle.Active
      fixture.stored.get.currentProgression shouldBe
        Some(ProgressionReference("sync.BoundaryAssessment"))
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1
      take(fixture.start()) shouldBe started
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1
    }

    "reject unselected and mismapped repository Starts before instance creation" in {
      val fixture = new Fixture
      fixture.selected = None
      fixture.start() shouldBe a[Consequence.Failure[?]]
      fixture.selected = Some(human.copy(origin = SelectionOrigin.Recommendation))
      fixture.start() shouldBe a[Consequence.Failure[?]]
      fixture.selected = Some(human)
      fixture.mapping = None
      fixture.start() shouldBe a[Consequence.Failure[?]]
      fixture.mapping = Some(input.copy(trackingBranch = "release"))
      fixture.start() shouldBe a[Consequence.Failure[?]]
      fixture.creates shouldBe 0
      fixture.appends shouldBe 0
    }

    "reject a changed command under an already-used idempotency key" in {
      val fixture = new Fixture
      take(fixture.start())
      val changed = command.copy(input = input.copy(trackingBranch = "release"))
      fixture.mapping = Some(changed.input)
      fixture.start(changed) shouldBe a[Consequence.Failure[?]]
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1
    }

    "resume the same Start after the initial record was created but Active append failed" in {
      val fixture = new Fixture
      fixture.failAppendOnce = true
      fixture.start() shouldBe a[Consequence.Failure[?]]
      fixture.stored.map(_.lifecycle) shouldBe Some(Lifecycle.NotStarted)
      fixture.creates shouldBe 1
      take(fixture.start()).current shouldBe a[WorkflowContinuation.Wait]
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1
    }

    "capture a matching local boundary and replay the same assessment" in {
      val fixture = new Fixture
      take(fixture.start())
      val assessed = take(fixture.assess())
      assessed.handle.instanceIdentity shouldBe fixture.stored.get.identity
      assessed.current shouldBe a[WorkflowContinuation.Wait]
      fixture.stored.get.currentProgression shouldBe
        Some(ProgressionReference("sync.BoundaryCaptured"))
      fixture.stored.get.revision shouldBe InstanceRevision(2L)
      take(WorkflowWaitJsonV1.decodeC(take(WorkflowWaitJsonV1.encodeC(assessed)))) shouldBe
        assessed
      take(fixture.assess()) shouldBe assessed
      fixture.creates shouldBe 1
      fixture.appends shouldBe 2
    }

    "reject missing, changed, detached, pending-merge, and partial-index boundaries" in {
      val fixture = new Fixture
      fixture.assess() shouldBe a[Consequence.Failure[?]]
      take(fixture.start())
      for (invalid <- Vector(
          None,
          Some(snapshot.copy(localBranch = "release")),
          Some(snapshot.copy(remoteEndpointIdentity = "other-remote")),
          Some(snapshot.copy(detachedHead = true)),
          Some(snapshot.copy(pendingMerge = true)),
          Some(snapshot.copy(indexShape = "PARTIAL")),
          Some(snapshot.copy(dirtyPaths = Vector("../outside"))))) {
        fixture.captured = invalid
        fixture.assess() shouldBe a[Consequence.Failure[?]]
      }
      fixture.appends shouldBe 1
      fixture.captured = Some(snapshot)
      take(fixture.assess())
      fixture.captured = Some(snapshot.copy(treeDigest = "e" * 64))
      fixture.assess() shouldBe a[Consequence.Failure[?]]
      fixture.appends shouldBe 2
    }

    "reject a stale expected revision before reading or advancing the boundary" in {
      val fixture = new Fixture
      take(fixture.start())
      fixture.assess(boundaryCommand.copy(expectedRevision = "2")) shouldBe
        a[Consequence.Failure[?]]
      fixture.appends shouldBe 1
    }

    "admit only complete host-scanned dirty scope matching the snapshot and index" in {
      val dirty = snapshot.copy(dirtyPaths = Vector("docs/change.md", "src/Change.scala"),
        indexShape = "COMPLETE_FULLY_STAGED")
      val dirtyDigest = take(RepositorySyncBoundaryAssessment.assessC(input,
        new RepositorySyncBoundaryAssessment.Port {
          def captureC(repositoryIdentity: String) = Consequence.success(Some(dirty))
        })).digest
      val evidence = scope.copy(snapshotDigest = dirtyDigest,
        stagedPaths = dirty.dirtyPaths, scannedPaths = dirty.dirtyPaths)
      val fixture = new Fixture
      take(fixture.start())
      fixture.captured = Some(dirty)
      for (invalid <- Vector(
          None,
          Some(evidence.copy(snapshotDigest = snapshotDigest)),
          Some(evidence.copy(repositoryPolicyVersion = "policy-v2")),
          Some(evidence.copy(stagedPaths = Vector("docs/change.md"))),
          Some(evidence.copy(scannedPaths = Vector("docs/change.md"))),
          Some(evidence.copy(scannedPaths = dirty.dirtyPaths.reverse)),
          Some(evidence.copy(unsafeFindings = Vector("symlink escape"))),
          Some(evidence.copy(scanDigest = "bad")))) {
        fixture.dirtyScope = invalid
        fixture.assess() shouldBe a[Consequence.Failure[?]]
      }
      fixture.appends shouldBe 1
      fixture.dirtyScope = Some(evidence)
      take(fixture.assess()).current shouldBe a[WorkflowContinuation.Wait]
      fixture.appends shouldBe 2
      fixture.dirtyScope = Some(evidence.copy(scanDigest = "f" * 64))
      fixture.assess() shouldBe a[Consequence.Failure[?]]
      fixture.appends shouldBe 2
    }

    "admit unstaged dirty paths only with an empty index and a complete scan" in {
      val dirty = snapshot.copy(dirtyPaths = Vector("notes.txt"))
      val dirtyDigest = take(RepositorySyncBoundaryAssessment.assessC(input,
        new RepositorySyncBoundaryAssessment.Port {
          def captureC(repositoryIdentity: String) = Consequence.success(Some(dirty))
        })).digest
      val fixture = new Fixture
      take(fixture.start())
      fixture.captured = Some(dirty)
      fixture.dirtyScope = Some(scope.copy(snapshotDigest = dirtyDigest,
        scannedPaths = dirty.dirtyPaths, stagedPaths = dirty.dirtyPaths))
      fixture.assess() shouldBe a[Consequence.Failure[?]]
      fixture.dirtyScope = fixture.dirtyScope.map(_.copy(stagedPaths = Vector.empty))
      take(fixture.assess()).current shouldBe a[WorkflowContinuation.Wait]
    }

    "serve the generated Start through the same public Operation and CLI JSON contract" in {
      val fixture = new Fixture
      val operation = take(RepositorySyncStartServiceOperation.bindC(
        fixture.selections, fixture.mappings, definition, configuration, fixture.instances))
      val wire = RepositorySyncStartServiceOperation.encodeCommand(command)
      take(RepositorySyncStartServiceOperation.decodeCommandC(wire)) shouldBe command
      val request = Request.of(
        component = SmWorkflowComponent.name,
        service = "RepositorySyncService",
        operation = "startRepositorySync",
        arguments = List(Argument("arg1", wire)))
      operation.createOperationRequest(request).isSuccess shouldBe true
      operation.createOperationRequest(request.copy(operation = "startSplitPhase")) shouldBe
        a[Consequence.Failure[?]]
      operation.createOperationRequest(request.copy(arguments = List(Argument(
        "arg1", wire.replace("\"localBranch\":\"main\"",
          "\"localBranch\":\"main\",\"extra\":1")
      )))) shouldBe a[Consequence.Failure[?]]

      val service = spec.ServiceDefinition(
        name = "RepositorySyncService",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation)))
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
      val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
        name = "sm-workflow-repository-sync-start",
        version = None,
        configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
        aliasResolver = AliasResolver.empty,
        runMode = RunMode.Command))
      try {
        val component = new Component() {}
        component.initialize(ComponentInit(
          subsystem,
          Component.Core.create(SmWorkflowComponent.name, SmWorkflowComponent.componentId,
            ComponentInstanceId.default(SmWorkflowComponent.componentId), protocol),
          ComponentOrigin.Builtin))
        subsystem.add(component)
        val cli = new CncfRuntime()
        val selector = s"${SmWorkflowComponent.name}/RepositorySyncService/startRepositorySync"
        for (origin <- Vector(SelectionOrigin.Recommendation, SelectionOrigin.Skill,
            SelectionOrigin.Workflow, SelectionOrigin.TerminalResult)) {
          fixture.selected = Some(human.copy(origin = origin))
          cli.executeCommandResponse(subsystem,
            Array(selector, "--json", wire)) shouldBe a[Consequence.Failure[?]]
        }
        fixture.selected = Some(human.copy(
          startOperation = StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase")))
        cli.executeCommandResponse(subsystem,
          Array(selector, "--json", wire)) shouldBe a[Consequence.Failure[?]]
        fixture.selected = Some(human)
        fixture.mapping = Some(input.copy(trackingBranch = "release"))
        cli.executeCommandResponse(subsystem,
          Array(selector, "--json", wire)) shouldBe a[Consequence.Failure[?]]
        fixture.creates shouldBe 0
        fixture.appends shouldBe 0
        fixture.mapping = Some(input)
        val response = take(subsystem.executeOperationResponse(request))
        val wait = response match {
          case OperationResponse.Scalar(value: String) => take(WorkflowWaitJsonV1.decodeC(value))
          case other => fail(s"Expected public WAIT response, found $other")
        }
        wait.handle.instanceIdentity shouldBe fixture.stored.get.identity
        wait.current shouldBe a[WorkflowContinuation.Wait]
        val directWire = response match {
          case OperationResponse.Scalar(value: String) => value
          case other => fail(s"Expected public WAIT response, found $other")
        }
        take(cli.executeCommandResponse(subsystem,
          Array(selector, "--json", wire))).print shouldBe directWire
        cli.executeCommandResponse(subsystem,
          Array(selector, "--json", "{")) shouldBe a[Consequence.Failure[?]]
        fixture.creates shouldBe 1
        fixture.appends shouldBe 1
      } finally subsystem.shutdown()
    }

    "serve boundary assessment through the generated Operation and CLI JSON contract" in {
      val fixture = new Fixture
      take(fixture.start())
      val operation = take(RepositorySyncBoundaryAssessmentServiceOperation.bindC(
        fixture.selections, fixture.mappings, fixture.snapshots, fixture.scopes,
        definition, configuration, fixture.instances))
      val wire = RepositorySyncBoundaryAssessmentServiceOperation.encodeCommand(boundaryCommand)
      take(RepositorySyncBoundaryAssessmentServiceOperation.decodeCommandC(wire)) shouldBe
        boundaryCommand
      val request = Request.of(
        component = SmWorkflowComponent.name,
        service = "RepositorySyncService",
        operation = "assessRepositoryBoundary",
        arguments = List(Argument("arg1", wire)))
      operation.createOperationRequest(request).isSuccess shouldBe true
      operation.createOperationRequest(request.copy(arguments = List(Argument(
        "arg1", wire.replace("\"expectedRevision\":\"1\"",
          "\"expectedRevision\":\"1\",\"extra\":1")
      )))) shouldBe a[Consequence.Failure[?]]

      val service = spec.ServiceDefinition(
        name = "RepositorySyncService",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation)))
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
      val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
        name = "sm-workflow-repository-boundary",
        version = None,
        configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
        aliasResolver = AliasResolver.empty,
        runMode = RunMode.Command))
      try {
        val component = new Component() {}
        component.initialize(ComponentInit(
          subsystem,
          Component.Core.create(SmWorkflowComponent.name, SmWorkflowComponent.componentId,
            ComponentInstanceId.default(SmWorkflowComponent.componentId), protocol),
          ComponentOrigin.Builtin))
        subsystem.add(component)
        val selector = s"${SmWorkflowComponent.name}/RepositorySyncService/assessRepositoryBoundary"
        val cli = new CncfRuntime()
        fixture.captured = Some(snapshot.copy(detachedHead = true))
        cli.executeCommandResponse(subsystem,
          Array(selector, "--json", wire)) shouldBe a[Consequence.Failure[?]]
        fixture.appends shouldBe 1
        fixture.captured = Some(snapshot)
        val direct = take(subsystem.executeOperationResponse(request))
        val directWire = direct match {
          case OperationResponse.Scalar(value: String) => value
          case other => fail(s"Expected public WAIT response, found $other")
        }
        take(WorkflowWaitJsonV1.decodeC(directWire)).handle.instanceIdentity shouldBe
          fixture.stored.get.identity
        take(cli.executeCommandResponse(subsystem,
          Array(selector, "--json", wire))).print shouldBe directWire
        fixture.appends shouldBe 2
      } finally subsystem.shutdown()
    }
  }
}
