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
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncBoundaryAssessmentCommand, RepositorySyncFetchAdmissionCommand, RepositorySyncStartCommand, RepositorySyncStartInput}

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncFetchAdmissionExecutionSpec extends AnyWordSpec with Matchers {
  import ProfileStartAdmission.*

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"RepositorySync fetch progression failed: $failure")
  }

  private val input = RepositorySyncStartInput(
    "repository-1", "main", "origin", "main", "remote-1", "FULL_STATE", "policy-v1")
  private val startCommand = RepositorySyncStartCommand(
    input, "sync-selection-1", "participant-1", "sync-start-1")
  private val boundaryCommand = RepositorySyncBoundaryAssessmentCommand(
    startCommand, "1", "sync-boundary-1")
  private val fetchCommand = RepositorySyncFetchAdmissionCommand(
    boundaryCommand, "2", "sync-fetch-1")
  private val snapshot = RepositorySyncBoundaryAssessment.Snapshot(
    "snapshot-1", "repository-1", "worktree-1", "main", "origin", "main",
    "remote-1", "policy-v1", "a" * 40, "b" * 64, "c" * 64, "d" * 64,
    Vector.empty, "EMPTY", detachedHead = false, pendingMerge = false)
  private val boundary = take(RepositorySyncBoundaryAssessment.assessC(input,
    new RepositorySyncBoundaryAssessment.Port {
      def captureC(repositoryIdentity: String) = Consequence.success(Some(snapshot))
    }))
  private val scopeEvidence = RepositorySyncDirtyScopeAssessment.Evidence(
    boundary.digest, "policy-v1", Vector.empty, Vector.empty, Vector.empty, "e" * 64)
  private val scope = take(RepositorySyncDirtyScopeAssessment.assessC(boundary,
    new RepositorySyncDirtyScopeAssessment.Port {
      def loadC(repositoryIdentity: String, snapshotDigest: String) =
        Consequence.success(Some(scopeEvidence))
    }))
  private val fetched = RepositorySyncFetchAssessment.Receipt(
    boundary.digest, scope.digest, "repository-1", "worktree-1", "main", "origin",
    "main", "remote-1", "f" * 40, Some("9" * 40), "f" * 40,
    snapshot.headOid, snapshot.headOid, snapshot.treeDigest,
    snapshot.indexDigest, snapshot.worktreeDigest,
    "refs/remotes/origin/main", Vector("refs/remotes/origin/main"),
    endpointPolicyAccepted = true, fetchCompleted = true, executionDigest = "1" * 64)
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
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store")))

  private final class Fixture {
    var stored: Option[InstanceRecord] = None
    var scopeEvidenceValue: Option[RepositorySyncDirtyScopeAssessment.Evidence] =
      Some(scopeEvidence)
    var fetchedValue: Option[RepositorySyncFetchAssessment.Receipt] = Some(fetched)
    var appends = 0
    var rejectFetchAppend = false

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
    val scopes = new RepositorySyncDirtyScopeAssessment.Port {
      def loadC(repositoryIdentity: String, digest: String) =
        Consequence.success(scopeEvidenceValue)
    }
    val fetches = new RepositorySyncFetchAssessment.Port {
      def fetchC(repositoryIdentity: String, boundaryDigest: String,
        dirtyScopeDigest: String) = Consequence.success(fetchedValue)
    }
    val instances = new WorkflowInstancePersistence {
      def create(config: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
        if (config != configuration || stored.nonEmpty)
          Consequence.stateConflict("RepositorySync fixture create conflict")
        else record.validateC.map { valid =>
          stored = Some(valid)
          valid
        }

      def load(config: Configuration, identity: InstanceIdentity):
        Consequence[Option[InstanceRecord]] =
        if (config != configuration)
          Consequence.stateConflict("RepositorySync fixture store mismatch")
        else Consequence.success(stored.filter(_.identity == identity))

      def append(config: Configuration, identity: InstanceIdentity,
        expectedRevision: InstanceRevision, entry: HistoryEntry): Consequence[InstanceRecord] =
        if (rejectFetchAppend && expectedRevision == InstanceRevision(2L))
          Consequence.stateConflict("RepositorySync fixture fetch append failure")
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
    def fetch(value: RepositorySyncFetchAdmissionCommand = fetchCommand) =
      RepositorySyncFetchAdmissionExecution.admitC(value, selections,
        mappings, snapshots, scopes, fetches, definition, configuration, instances)
  }

  "RepositorySync fetch progression" should {
    "require the captured boundary, then append one idempotent FetchAdmitted progression" in {
      val fixture = new Fixture
      fixture.fetch() shouldBe a[Consequence.Failure[?]]
      take(fixture.start())
      fixture.fetch() shouldBe a[Consequence.Failure[?]]
      take(fixture.boundary())
      val admitted = take(fixture.fetch())
      admitted.current shouldBe a[WorkflowContinuation.Wait]
      admitted.current.asInstanceOf[WorkflowContinuation.Wait].presentation.nextAction shouldBe
        Some("Verify clean local state before relation classification")
      fixture.stored.get.currentProgression shouldBe
        Some(ProgressionReference("sync.FetchAdmitted"))
      fixture.stored.get.revision shouldBe InstanceRevision(3L)
      take(WorkflowWaitJsonV1.decodeC(take(WorkflowWaitJsonV1.encodeC(admitted)))) shouldBe
        admitted
      take(fixture.fetch()) shouldBe admitted
      fixture.appends shouldBe 3
    }

    "reject stale boundary and changed fetch evidence without another append" in {
      val fixture = new Fixture
      take(fixture.start())
      take(fixture.boundary())
      fixture.fetch(fetchCommand.copy(expectedRevision = "3")) shouldBe
        a[Consequence.Failure[?]]
      fixture.fetch(fetchCommand.copy(boundaryCommand = boundaryCommand.copy(
        idempotencyKey = "other-boundary"))) shouldBe a[Consequence.Failure[?]]
      fixture.fetchedValue = None
      fixture.fetch() shouldBe a[Consequence.Failure[?]]
      fixture.fetchedValue = Some(fetched)
      fixture.scopeEvidenceValue = None
      fixture.fetch() shouldBe a[Consequence.Failure[?]]
      fixture.scopeEvidenceValue = Some(scopeEvidence)
      fixture.fetchedValue = Some(fetched.copy(localHeadAfter = "2" * 40))
      fixture.fetch() shouldBe a[Consequence.Failure[?]]
      fixture.fetchedValue = Some(fetched)
      take(fixture.fetch())
      fixture.fetchedValue = Some(fetched.copy(executionDigest = "2" * 64))
      fixture.fetch() shouldBe a[Consequence.Failure[?]]
      fixture.fetchedValue = Some(fetched)
      fixture.fetch(fetchCommand.copy(idempotencyKey = "other-fetch")) shouldBe
        a[Consequence.Failure[?]]
      fixture.appends shouldBe 3
    }

    "retain the captured boundary on append failure and accept a matching no-op fetch on retry" in {
      val fixture = new Fixture
      take(fixture.start())
      take(fixture.boundary())
      fixture.fetchedValue = Some(fetched.copy(
        trackingTipBefore = Some(fetched.fetchedTipOid), changedRefs = Vector.empty))
      fixture.rejectFetchAppend = true
      fixture.fetch() shouldBe a[Consequence.Failure[?]]
      fixture.stored.get.revision shouldBe InstanceRevision(2L)
      fixture.appends shouldBe 2
      fixture.rejectFetchAppend = false
      val admitted = take(fixture.fetch())
      take(fixture.fetch()) shouldBe admitted
      fixture.stored.get.revision shouldBe InstanceRevision(3L)
      fixture.appends shouldBe 3
    }

    "serve the generated fetch Operation and CLI JSON contract" in {
      val fixture = new Fixture
      take(fixture.start())
      take(fixture.boundary())
      val operation = take(RepositorySyncFetchAdmissionServiceOperation.bindC(
        fixture.selections, fixture.mappings, fixture.snapshots, fixture.scopes,
        fixture.fetches, definition, configuration, fixture.instances))
      val wire = RepositorySyncFetchAdmissionServiceOperation.encodeCommand(fetchCommand)
      take(RepositorySyncFetchAdmissionServiceOperation.decodeCommandC(wire)) shouldBe
        fetchCommand
      val request = Request.of(
        component = SmWorkflowComponent.name,
        service = "RepositorySyncService",
        operation = "admitTrackingFetch",
        arguments = List(Argument("arg1", wire)))
      operation.createOperationRequest(request).isSuccess shouldBe true
      val extraFieldRequest = request.copy(arguments = List(Argument(
        "arg1", wire.replace("\"expectedRevision\":\"2\"",
          "\"expectedRevision\":\"2\",\"extra\":1"))))
      operation.createOperationRequest(extraFieldRequest) shouldBe
        a[Consequence.Failure[?]]

      val service = spec.ServiceDefinition(
        name = "RepositorySyncService",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation)))
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
      val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
        name = "sm-workflow-repository-fetch",
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
        val selector = s"${SmWorkflowComponent.name}/RepositorySyncService/admitTrackingFetch"
        val cli = new CncfRuntime()
        val direct = take(subsystem.executeOperationResponse(request))
        val directWire = direct match {
          case OperationResponse.Scalar(value: String) => value
          case other => fail(s"Expected public WAIT response, found $other")
        }
        take(WorkflowWaitJsonV1.decodeC(directWire)).handle.instanceIdentity shouldBe
          fixture.stored.get.identity
        take(cli.executeCommandResponse(subsystem,
          Array(selector, "--json", wire))).print shouldBe directWire
        fixture.appends shouldBe 3
      } finally subsystem.shutdown()
    }
  }
}
