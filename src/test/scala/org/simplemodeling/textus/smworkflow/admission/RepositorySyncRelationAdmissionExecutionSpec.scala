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
import org.simplemodeling.textus.smworkflow.value.{RepositorySyncBoundaryAssessmentCommand, RepositorySyncFetchAdmissionCommand, RepositorySyncPreservationAdmissionCommand, RepositorySyncRelationAdmissionCommand, RepositorySyncStartCommand, RepositorySyncStartInput}

/**
 * @since   Oct.  6, 2026
 * @version Oct.  8, 2026
 */
final class RepositorySyncRelationAdmissionExecutionSpec extends AnyWordSpec with Matchers {
  import ProfileStartAdmission.*
  import RepositorySyncCheckpointAssessment.*

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"RepositorySync relation progression failed: $failure")
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
    var stored: Option[InstanceRecord] = None
    var appends = 0
    var rejectRelationAppend = false
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
        if (rejectRelationAppend && expectedRevision == InstanceRevision(4L))
          Consequence.stateConflict("RepositorySync fixture relation append failure")
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
    def prepareFetched(): Unit = { take(start()); take(boundary()); take(fetch()); () }
    def prepare(): Unit = { prepareFetched(); take(preserve()); () }
    def preserve(command: RepositorySyncPreservationAdmissionCommand = preservationCommand) =
      RepositorySyncPreservationAdmissionExecution.admitC(command, selections, mappings,
        snapshots, scopes, fetches, preservations, definition, configuration, instances)
    def classify(command: RepositorySyncRelationAdmissionCommand = relationCommand) =
      RepositorySyncRelationAdmissionExecution.admitC(command, selections, mappings,
        snapshots, scopes, fetches, preservations, relations, definition, configuration, instances)
  }

  "RepositorySync relation progression" should {
    "require persisted preservation before graph loading and append only once" in {
      val fixture = new Fixture
      fixture.classify() shouldBe a[Consequence.Failure[?]]
      fixture.prepareFetched()
      fixture.classify() shouldBe a[Consequence.Failure[?]]
      fixture.relationLoads shouldBe 0
      take(fixture.preserve())
      val prefix = fixture.stored.get.history
      val admitted = take(fixture.classify())
      fixture.stored.get.history.take(4) shouldBe prefix
      fixture.stored.get.currentProgression shouldBe Some(ProgressionReference("sync.RelationClassified"))
      fixture.stored.get.revision shouldBe InstanceRevision(5L)
      take(WorkflowWaitJsonV1.decodeC(take(WorkflowWaitJsonV1.encodeC(admitted)))) shouldBe admitted
      take(fixture.classify()) shouldBe admitted
      fixture.appends shouldBe 5
    }

    "record all four route projections and retain exact history on replay" in {
      import RepositorySyncRelationAssessment.Relation
      Vector(Relation.Equal, Relation.LocalAhead, Relation.RemoteAhead, Relation.Diverged).foreach { route =>
        val fixture = routeFixture(route)
        fixture.prepare()
        val prefix = fixture.stored.get.history
        val admitted = take(fixture.classify())
        val wait = admitted.current.asInstanceOf[WorkflowContinuation.Wait]
        wait.presentation.currentSituation shouldBe s"Tracking tip relation admitted: ${route.value}"
        wait.presentation.nextAction shouldBe Some(route.nextAction)
        wait.contextSnapshot.evidenceRevision.get should fullyMatch regex "[0-9a-f]{64}"
        fixture.stored.get.history.take(4) shouldBe prefix
        take(fixture.classify()) shouldBe admitted
        fixture.appends shouldBe 5
      }
    }

    "leave revision four on missing, inconsistent or failed graph receipt loads" in {
      val fixture = new Fixture
      fixture.prepare()
      fixture.relationMissing = true
      fixture.classify() shouldBe a[Consequence.Failure[?]]
      fixture.relationMissing = false
      fixture.relationFailure = true
      fixture.classify() shouldBe a[Consequence.Failure[?]]
      fixture.relationFailure = false
      fixture.relationTransform = _.copy(historyComplete = false)
      fixture.classify() shouldBe a[Consequence.Failure[?]]
      fixture.relationTransform = _.copy(localTipOid = "0" * 40)
      fixture.classify() shouldBe a[Consequence.Failure[?]]
      fixture.stored.get.revision shouldBe InstanceRevision(4L)
      fixture.appends shouldBe 4
      fixture.relationTransform = identity
      take(fixture.classify())
      fixture.appends shouldBe 5
    }

    "reject altered commands and upstream history before graph loading" in {
      val fixture = new Fixture
      fixture.prepare()
      val commands = Vector(relationCommand.copy(expectedRevision = "5"),
        relationCommand.copy(preservationCommand = preservationCommand.copy(expectedRevision = "4")),
        relationCommand.copy(preservationCommand = preservationCommand.copy(idempotencyKey = "other-preservation")),
        relationCommand.copy(preservationCommand = preservationCommand.copy(fetchCommand =
          fetchCommand.copy(idempotencyKey = "other-fetch"))),
        relationCommand.copy(preservationCommand = preservationCommand.copy(fetchCommand =
          fetchCommand.copy(boundaryCommand = boundaryCommand.copy(idempotencyKey = "other-boundary")))))
      commands.foreach(fixture.classify(_) shouldBe a[Consequence.Failure[?]])
      fixture.fetchReceiptValue = fixture.fetchReceipt.copy(executionDigest = "2" * 64)
      fixture.classify() shouldBe a[Consequence.Failure[?]]
      fixture.fetchReceiptValue = fixture.fetchReceipt
      fixture.cleanTransform = _.copy(inspectionDigest = "0" * 64)
      fixture.classify() shouldBe a[Consequence.Failure[?]]
      fixture.relationLoads shouldBe 0
      fixture.cleanTransform = identity
      val admitted = take(fixture.classify())
      fixture.classify(relationCommand.copy(idempotencyKey = "other-relation")) shouldBe a[Consequence.Failure[?]]
      fixture.relationTransform = _.copy(inspectionDigest = "0" * 64)
      fixture.classify() shouldBe a[Consequence.Failure[?]]
      fixture.relationTransform = _.copy(remoteAhead = 4L)
      fixture.classify() shouldBe a[Consequence.Failure[?]]
      fixture.relationTransform = identity
      take(fixture.classify()) shouldBe admitted
      fixture.appends shouldBe 5
    }

    "retain the preserved instance on append failure and allow an exact retry" in {
      val fixture = new Fixture(dirty = true)
      fixture.prepare()
      fixture.rejectRelationAppend = true
      fixture.classify() shouldBe a[Consequence.Failure[?]]
      fixture.stored.get.revision shouldBe InstanceRevision(4L)
      fixture.appends shouldBe 4
      fixture.rejectRelationAppend = false
      val admitted = take(fixture.classify())
      take(fixture.classify()) shouldBe admitted
      fixture.appends shouldBe 5
    }

    "reject incomplete and incompatible public bindings without progressing" in {
      val fixture = new Fixture
      fixture.prepare()
      RepositorySyncRelationAdmissionServiceOperation.bindC(
        fixture.selections, fixture.mappings, fixture.snapshots, fixture.scopes,
        fixture.fetches, fixture.preservations, null, definition, configuration, fixture.instances) shouldBe
        a[Consequence.Failure[?]]
      RepositorySyncRelationAdmissionServiceOperation.bindC(
        fixture.selections, fixture.mappings, fixture.snapshots, fixture.scopes,
        fixture.fetches, fixture.preservations, fixture.relations,
        take(SplitPhaseDefinitionBinding.bindC), configuration, fixture.instances) shouldBe
        a[Consequence.Failure[?]]
      fixture.relationLoads shouldBe 0
      fixture.stored.get.revision shouldBe InstanceRevision(4L)
    }

    "serve the generated relation Operation and equivalent strict CLI JSON for all four routes" in {
      import RepositorySyncRelationAssessment.Relation
      Vector(Relation.Equal, Relation.LocalAhead, Relation.RemoteAhead, Relation.Diverged).foreach { route =>
        val fixture = routeFixture(route)
        fixture.prepare()
        val operation = take(RepositorySyncRelationAdmissionServiceOperation.bindC(
          fixture.selections, fixture.mappings, fixture.snapshots, fixture.scopes,
          fixture.fetches, fixture.preservations, fixture.relations, definition, configuration, fixture.instances))
        val wire = RepositorySyncRelationAdmissionServiceOperation.encodeCommand(relationCommand)
        take(RepositorySyncRelationAdmissionServiceOperation.decodeCommandC(wire)) shouldBe relationCommand
        val request = Request.of(component = SmWorkflowComponent.name,
          service = "RepositorySyncService", operation = "admitTipRelation",
          arguments = List(Argument("arg1", wire)))
        operation.createOperationRequest(request).isSuccess shouldBe true
        val malformed = Vector("not-json", "{}", wire.dropRight(1) + ",\"extra\":1}",
          wire.replace(RepositorySyncRelationAdmissionServiceOperation.wireSchema, "unsupported"),
          wire.replace("\"preservationCommand\":{", "\"preservationCommand\":null,\"other\":{"))
        malformed.foreach { value =>
          operation.createOperationRequest(request.copy(arguments = List(Argument("arg1", value)))) shouldBe
            a[Consequence.Failure[?]]
        }
        operation.createOperationRequest(request.copy(operation = "admitLocalPreservation")) shouldBe
          a[Consequence.Failure[?]]
        operation.createOperationRequest(request.copy(service = Some("OtherService"))) shouldBe
          a[Consequence.Failure[?]]
        fixture.stored.get.revision shouldBe InstanceRevision(4L)
        val service = spec.ServiceDefinition("RepositorySyncService",
          spec.OperationDefinitionGroup(NonEmptyVector.of(operation)))
        val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
        val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
          name = "sm-workflow-repository-relation", version = None,
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
          val direct = take(WorkflowWaitJsonV1.decodeC(directWire))
          direct.handle.instanceIdentity shouldBe fixture.stored.get.identity
          direct.current.asInstanceOf[WorkflowContinuation.Wait].presentation.nextAction shouldBe Some(route.nextAction)
          val selector = s"${SmWorkflowComponent.name}/RepositorySyncService/admitTipRelation"
          take(new CncfRuntime().executeCommandResponse(subsystem,
            Array(selector, "--json", wire))).print shouldBe directWire
          fixture.stored.get.revision shouldBe InstanceRevision(5L)
          fixture.appends shouldBe 5
        } finally subsystem.shutdown()
      }
    }
  }

  private def routeFixture(route: RepositorySyncRelationAssessment.Relation): Fixture = {
    import RepositorySyncRelationAssessment.Relation
    val fixture = new Fixture(dirty = route == Relation.Diverged, equal = route == Relation.Equal)
    if (route == Relation.LocalAhead)
      fixture.relationTransform = _.copy(mergeBases = Vector(fixture.fetchedTip), localAhead = 2L, remoteAhead = 0L)
    fixture
  }
}
