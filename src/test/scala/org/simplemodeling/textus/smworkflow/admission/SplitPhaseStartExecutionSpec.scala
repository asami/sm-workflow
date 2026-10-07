package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import scala.jdk.CollectionConverters.*

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
import org.goldenport.cncf.workflow.{WorkflowTerminalJsonV1, WorkflowWaitJsonV1}
import org.goldenport.cncf.workflow.{ContinuationIdentity as RuntimeContinuationIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.configuration.{Configuration as GenericConfiguration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.{Argument, Protocol, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseApplyCommand, SplitPhasePreviewCommand, SplitPhaseProposalAssessmentCommand, SplitPhaseStartCommand, SplitPhaseStartInput}

/**
  * @since Oct.  2, 2026
  * @version Oct.  5, 2026
  */
final class SplitPhaseStartExecutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import ProfileStartAdmission.*
  import SplitPhaseEntryAssessment.*
  import SplitPhaseProposalPrecheck.*

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"SplitPhase Start fixture failed: $failure")
  }

  private def sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x").mkString

  private val definition = take(SplitPhaseDefinitionBinding.bindC)
  private val configuration = Configuration(
    NamedWorkflowStore("workflow-primary", WorkflowStoreIdentity("workflow-store")),
    MigrationPolicy("fixture-v1"), RetentionPolicy("fixture-v1"), LeasePolicy("fixture-v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store"))
  )
  private val command = SplitPhaseStartCommand(
    SplitPhaseStartInput("77.1", "PREVIEW", "source-authority-1", None,
      Some("goal-terminal-result-1"), Some("split-proposal-1")),
    "split-selection-1", "participant-1", "split-start-1"
  )
  private val human = Selection(
    "split-selection-1", StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase"),
    WorkflowDefinitionIdentity("SplitPhaseWorkflow"),
    WorkflowDefinitionRevision("split-phase-selected-application-v12"),
    "participant-1", SelectionOrigin.ExplicitHuman, "split-start-1", None
  )

  private final class Fixture {
    var selected: Option[Selection] = Some(human)
    var source: Option[SourceSnapshot] = Some(SourceSnapshot(
      "source-snapshot-1", "77.1", "source-authority-1", "source-revision-1", "a" * 64))
    var proposal: Option[ProposalCandidate] = Some(ProposalCandidate(
      "split-proposal-1", "77.1", "source-authority-1", "a" * 64, "b" * 64))
    var inventory: Option[SourceInventory] = None
    var draft: Option[ProposalDraft] = None
    var validationEvidence: Option[SplitPhaseProposalValidation.Evidence] = None
    var sourceBoundary: Option[SplitPhaseSourceBoundary.Contract] = None
    var stored: Option[InstanceRecord] = None
    var creates = 0
    var appends = 0
    var failAppendOnce = false
    var failAppendAtRevisionOnce: Option[Long] = None

    val selections = new SelectionPort {
      def loadC(selectionId: String): Consequence[Option[Selection]] =
        Consequence.success(selected.filter(_.selectionId == selectionId))
    }
    val entry = new Port {
      def loadSourceC(phase: String, authority: String): Consequence[Option[SourceSnapshot]] =
        Consequence.success(source)
      def loadProposalC(reference: String): Consequence[Option[ProposalCandidate]] =
        Consequence.success(proposal)
    }
    val proposals = new SplitPhaseProposalAssessment.Port {
      def loadInventoryC(identity: String): Consequence[Option[SourceInventory]] =
        Consequence.success(inventory.filter(_ => identity == "source-snapshot-1"))
      def loadDraftC(identity: String): Consequence[Option[ProposalDraft]] =
        Consequence.success(draft.filter(_.identity == identity))
      def loadValidationEvidenceC(identity: String):
          Consequence[Option[SplitPhaseProposalValidation.Evidence]] =
        Consequence.success(validationEvidence.filter(_ => draft.exists(_.identity == identity)))
      def loadSourceBoundaryC(identity: String):
          Consequence[Option[SplitPhaseSourceBoundary.Contract]] =
        Consequence.success(sourceBoundary.filter(_ => identity == "source-snapshot-1"))
    }
    val instances = new WorkflowInstancePersistence {
      def create(config: Configuration, record: InstanceRecord): Consequence[InstanceRecord] =
        if (config != configuration || stored.nonEmpty)
          Consequence.stateConflict("SplitPhase fixture create conflict")
        else record.validateC.map { valid =>
          creates += 1
          stored = Some(valid)
          valid
        }

      def load(config: Configuration, identity: InstanceIdentity): Consequence[Option[InstanceRecord]] =
        if (config != configuration) Consequence.stateConflict("SplitPhase fixture store mismatch")
        else Consequence.success(stored.filter(_.identity == identity))

      def append(config: Configuration, identity: InstanceIdentity,
        expectedRevision: InstanceRevision, entry: HistoryEntry): Consequence[InstanceRecord] =
        if (config != configuration || failAppendOnce ||
            failAppendAtRevisionOnce.contains(expectedRevision.value)) {
          failAppendOnce = false
          failAppendAtRevisionOnce = None
          Consequence.stateConflict("SplitPhase fixture planned append failure")
        } else stored match {
          case Some(current) if current.identity == identity =>
            current.appendC(expectedRevision, entry).map { next =>
              appends += 1
              stored = Some(next)
              next
            }
          case _ => Consequence.stateConflict("SplitPhase fixture instance is missing")
        }
    }

    def start(input: SplitPhaseStartCommand = command) =
      SplitPhaseStartExecution.startC(input, selections, entry, definition, configuration, instances)

    def stage(input: SplitPhaseProposalAssessmentCommand) =
      SplitPhaseProposalAssessmentExecution.assessC(
        input, selections, entry, proposals, definition, configuration, instances)

    def shapeReadyStart: SplitPhaseStartCommand = {
      val selected = command.copy(input = command.input.copy(sourcePhaseIdentity = "77"))
      source = source.map(_.copy(sourcePhaseIdentity = "77"))
      val planned = ProposalDraft("split-proposal-1", "77", "source-authority-1",
        "a" * 64, Vector(
          Child("77", Vector("item-a"), 360, true),
          Child("77.1", Vector("item-b"), 360, true)))
      proposal = Some(ProposalCandidate("split-proposal-1", "77",
        "source-authority-1", "a" * 64, digestOf(planned)))
      draft = Some(planned)
      validationEvidence = Some(SplitPhaseProposalValidationFixture.evidenceFor(planned))
      sourceBoundary = Some(SplitPhaseSourceBoundary.Contract("a" * 64, "77",
        "source-authority-1", Vector(
          SplitPhaseSourceBoundary.Item("item-a", Vector("close-item-a"),
            Vector.empty, "source-authority-1"),
          SplitPhaseSourceBoundary.Item("item-b", Vector("close-item-b"),
            Vector("item-a"), "source-authority-1")),
        Set("authority-edge-item-a")))
      inventory = Some(SourceInventory("77", "a" * 64,
        Vector("item-a", "item-b"), Set.empty))
      selected
    }
  }

  private final class CatalogFixture(root: Path, mode: String = "PREVIEW") {
    val run = new Fixture
    val digest = "c53e30985ec679e39c14055cfe7c73c94e3e05be78dbd381b68c43d04f300760"
    val draft = ProposalDraft("split-proposal-1", "77", "source-authority-1",
      digest, Vector(
        Child("77", Vector("work-1", "work-2"), 360, true),
        Child("77.1", Vector("work-3"), 360, true)))
    val startCommand = command.copy(input = command.input.copy(
      sourcePhaseIdentity = "77", invocationMode = mode,
      sourceSnapshotReference = Some("snapshot-1")))
    val entry = new SplitPhaseSourceSnapshotCatalog.EntryPort(root,
      "snapshot-1", run.entry)
    val proposals = new SplitPhaseSourceSnapshotCatalog.ProposalPort(root,
      "snapshot-1", run.proposals)
    run.proposal = Some(ProposalCandidate("split-proposal-1", "77",
      "source-authority-1", digest, digestOf(draft)))
    run.draft = Some(draft)
    run.validationEvidence = Some(SplitPhaseProposalValidationFixture.evidenceFor(draft))

    def start() = SplitPhaseStartExecution.startC(startCommand, run.selections,
      entry, definition, configuration, run.instances)
    def assess() = SplitPhaseProposalAssessmentExecution.assessC(
      SplitPhaseProposalAssessmentCommand(startCommand, "1", "catalog-assessment-1"),
      run.selections, entry, proposals, definition, configuration, run.instances)

    def previewCatalog: SplitPhasePreviewExecution.Catalog = {
      val snapshot = take(SplitPhaseSourceSnapshotCatalog.loadC(root, "snapshot-1")).get
      val validation = SplitPhaseProposalValidationFixture.evidenceFor(draft)
      val estimates = Map("work-1" -> 180, "work-2" -> 180, "work-3" -> 360)
      val enumerated = take(SplitPhasePartitionEnumeration.enumerateC(digest,
        snapshot.inventory.orderedUnfinishedItems.map(item =>
          SplitPhasePartitionEnumeration.WorkItem(item, estimates(item))),
        validation.semanticEdges, validation.partitionPolicy))
        .asInstanceOf[SplitPhasePartitionEnumeration.Outcome.Enumerated]
      val evidence = SplitPhaseCandidateGateDerivation.Evidence(
        enumerated.candidates.head.digest, draft, estimates,
        validation.semanticHandoffs, validation.validationOwnership,
        validation.semanticEdges)
      val assessment = take(SplitPhaseStartExecution.prepareC(startCommand,
        run.selections, entry, definition, configuration)).assessment
      val admitted = take(SplitPhaseProposalAdmission.admitCatalogC(root,
        "snapshot-1", assessment, enumerated, validation.partitionPolicy,
        Vector(evidence), proposals))
        .asInstanceOf[SplitPhaseProposalAdmission.Outcome.Admitted]
      SplitPhasePreviewExecution.Catalog(root, "snapshot-1", enumerated,
        validation.partitionPolicy, Vector(evidence), admitted)
    }

    def preview(command: SplitPhasePreviewCommand,
                catalog: SplitPhasePreviewExecution.Catalog) =
      SplitPhasePreviewExecution.previewC(command, run.selections, entry,
        proposals, new SplitPhasePreviewExecution.CatalogPort {
          def loadC(start: SplitPhaseStartCommand) = Consequence.success(catalog)
        }, definition, configuration, run.instances)

    def documentPlan(catalog: SplitPhasePreviewExecution.Catalog) = {
      val assessment = take(SplitPhaseStartExecution.prepareC(startCommand,
        run.selections, entry, definition, configuration)).assessment
      SplitPhaseDocumentProjection.projectC(catalog, assessment, proposals)
    }
  }

  "SplitPhase Start execution" should {
    "carry one file-backed source catalog through Start and proposal assessment" in {
      val fixture = new CatalogFixture(Path.of(
        "src/test/resources/source-boundary-reader-fixture"))

      val started = take(fixture.start())
      started.current shouldBe a[WorkflowContinuation.Wait]
      started.current.asInstanceOf[WorkflowContinuation.Wait]
        .contextSnapshot.evidenceRevision shouldBe Some(fixture.digest)
      val assessed = take(fixture.assess())

      assessed.current shouldBe a[WorkflowContinuation.Wait]
      fixture.run.stored.map(_.revision) shouldBe Some(InstanceRevision(2L))
      fixture.run.appends shouldBe 2
    }

    "reject catalog byte drift after Start before appending proposal evidence" in {
      val root = Files.createTempDirectory("smwf-source-snapshot-")
      val phaseDirectory = root.resolve("docs/phase")
      val catalogDirectory = phaseDirectory.resolve("snapshots")
      val phaseFile = phaseDirectory.resolve("phase-77.md")
      val catalogFile = catalogDirectory.resolve("snapshot-1.json")
      Files.createDirectories(catalogDirectory)
      val source = Path.of("src/test/resources/source-boundary-reader-fixture/docs/phase")
      Files.copy(source.resolve("phase-77.md"), phaseFile)
      Files.copy(source.resolve("snapshots/snapshot-1.json"), catalogFile)
      try {
        val fixture = new CatalogFixture(root)
        take(fixture.start())
        val original = Files.readString(catalogFile, StandardCharsets.UTF_8)
        original should include("phase-77-r1")
        Files.writeString(catalogFile,
          original.replace("phase-77-r1", "phase-77-r2"), StandardCharsets.UTF_8)

        fixture.assess() shouldBe a[Consequence.Failure[?]]
        fixture.run.stored.map(_.revision) shouldBe Some(InstanceRevision(1L))
        fixture.run.appends shouldBe 1
      } finally {
        Files.deleteIfExists(catalogFile)
        Files.deleteIfExists(phaseFile)
        Files.deleteIfExists(catalogDirectory)
        Files.deleteIfExists(phaseDirectory)
        Files.deleteIfExists(root.resolve("docs"))
        Files.deleteIfExists(root)
      }
    }

    "create an independent durable instance and make identical retry idempotent" in {
      Given("a host-recorded human selection and generated SplitPhase definition")
      val fixture = new Fixture

      When("the selected SplitPhase Start executes twice")
      val started = take(fixture.start())
      val repeated = take(fixture.start())

      Then("one SplitPhase instance reaches its generated entry progression")
      started shouldBe repeated
      started.handle.workflowIdentity shouldBe WorkflowDefinitionIdentity("SplitPhaseWorkflow")
      started.handle.instanceIdentity.value should startWith("split-phase-")
      started.handle.instanceIdentity.value should not startWith("goal-phase-")
      started.current shouldBe a[WorkflowContinuation.Wait]
      fixture.stored.map(_.lifecycle) shouldBe Some(Lifecycle.Active)
      fixture.stored.flatMap(_.currentProgression) shouldBe
        Some(ProgressionReference("entry.EntryAssessment"))
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1
      started.current.asInstanceOf[WorkflowContinuation.Wait].contextSnapshot.modelRevision shouldBe
        Some("source-revision-1")
      started.current.asInstanceOf[WorkflowContinuation.Wait].contextSnapshot.evidenceRevision shouldBe
        Some("a" * 64)
      take(WorkflowWaitJsonV1.decodeC(take(WorkflowWaitJsonV1.encodeC(started)))) shouldBe started
    }

    "reject mismatched source and proposal evidence before creating an instance" in {
      Given("a Start command carrying an independently selected proposal reference")
      val wrongSource = new Fixture
      wrongSource.source = wrongSource.source.map(_.copy(sourcePhaseIdentity = "77.2"))
      val wrongSnapshot = new Fixture
      val wrongProposal = new Fixture
      wrongProposal.proposal = wrongProposal.proposal.map(_.copy(sourceSnapshotDigest = "c" * 64))

      When("source identity, snapshot reference, or proposal source digest differs")
      val outcomes = Vector(
        wrongSource.start(),
        wrongSnapshot.start(command.copy(input = command.input.copy(
          sourceSnapshotReference = Some("another-snapshot")))),
        wrongProposal.start()
      )

      Then("none can bind the Start to a source or inherit a prior proposal")
      outcomes.foreach(outcome => outcome shouldBe a[Consequence.Failure[?]])
      Vector(wrongSource, wrongSnapshot, wrongProposal).foreach(_.creates shouldBe 0)
    }

    "reject source evidence drift on a retry of the same Start key" in {
      Given("an admitted Start whose source snapshot later changes")
      val fixture = new Fixture
      take(fixture.start())
      fixture.source = fixture.source.map(_.copy(revision = "source-revision-2", digest = "c" * 64))
      fixture.proposal = fixture.proposal.map(_.copy(sourceSnapshotDigest = "c" * 64))

      When("the same command is retried against newer source evidence")
      val outcome = fixture.start()

      Then("the original durable invocation is not silently rebound")
      outcome shouldBe a[Consequence.Failure[?]]
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1
    }

    "start without a prior proposal when the exact source snapshot is selected" in {
      Given("a human Start selecting the current source but no prior proposal")
      val fixture = new Fixture
      fixture.proposal = None
      val input = command.copy(input = command.input.copy(
        sourceSnapshotReference = Some("source-snapshot-1"), proposalReference = None))

      When("entry assessment resolves the host-owned source snapshot")
      val started = take(fixture.start(input))

      Then("the independent run waits for subsequent proposal construction")
      started.current shouldBe a[WorkflowContinuation.Wait]
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1
    }

    "reject recommendation-only authority and changed input under the same key" in {
      Given("a SplitPhase command with a prior GoalPhase terminal reference")
      val fixture = new Fixture

      When("there is no independent human selection")
      fixture.selected = Some(human.copy(origin = SelectionOrigin.Recommendation))

      Then("the referenced result cannot start a SplitPhase instance")
      fixture.start() shouldBe a[Consequence.Failure[?]]
      fixture.creates shouldBe 0

      When("the human selection is supplied and its key is reused with different evidence")
      fixture.selected = Some(human)
      take(fixture.start())
      val changed = command.copy(input = command.input.copy(proposalReference = None))

      Then("the stored invocation cannot be rebound to another payload")
      fixture.start(changed) shouldBe a[Consequence.Failure[?]]
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1
    }

    "resume an initial record after a failed Active append" in {
      Given("a store whose first append fails after the initial record was created")
      val fixture = new Fixture
      fixture.failAppendOnce = true

      When("the identical selected Start is retried")
      fixture.start() shouldBe a[Consequence.Failure[?]]
      fixture.stored.map(_.lifecycle) shouldBe Some(Lifecycle.NotStarted)
      val resumed = take(fixture.start())

      Then("the existing record advances once without a second create")
      resumed.current shouldBe a[WorkflowContinuation.Wait]
      fixture.creates shouldBe 1
      fixture.appends shouldBe 1
    }

    "execute the generated Provided Operation through a public Service fixture" in {
      Given("a bound SplitPhaseService and host-owned stores")
      val fixture = new Fixture
      val operation = take(SplitPhaseStartServiceOperation.bindC(
        fixture.selections, fixture.entry, definition, configuration, fixture.instances))
      val wire = SplitPhaseStartServiceOperation.encodeCommand(command)
      val request = Request.of(
        component = SmWorkflowComponent.name, service = "SplitPhaseService",
        operation = "startSplitPhase", arguments = List(Argument("arg1", wire))
      )
      val service = spec.ServiceDefinition(
        name = "SplitPhaseService",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))
      )
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
      val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
        name = "sm-workflow-split-phase-start", version = None,
        configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
        aliasResolver = AliasResolver.empty, runMode = RunMode.Command
      ))
      try {
        val component = new Component() {}
        component.initialize(ComponentInit(
          subsystem,
          Component.Core.create(SmWorkflowComponent.name, SmWorkflowComponent.componentId,
            ComponentInstanceId.default(SmWorkflowComponent.componentId), protocol),
          ComponentOrigin.Builtin
        ))
        subsystem.add(component)

        val cli = new CncfRuntime()
        val selector = s"${SmWorkflowComponent.name}/SplitPhaseService/startSplitPhase"
        for (origin <- Vector(
            SelectionOrigin.Recommendation, SelectionOrigin.Skill,
            SelectionOrigin.Workflow, SelectionOrigin.TerminalResult)) {
          fixture.selected = Some(human.copy(origin = origin))
          cli.executeCommandResponse(subsystem,
            Array(selector, "--json", wire)) shouldBe a[Consequence.Failure[?]]
        }
        fixture.selected = Some(human.copy(
          startOperation = StateMachineOperationIdentity("GoalPhaseService", "startGoalPhase")))
        cli.executeCommandResponse(subsystem,
          Array(selector, "--json", wire)) shouldBe a[Consequence.Failure[?]]
        fixture.creates shouldBe 0
        fixture.appends shouldBe 0
        fixture.selected = Some(human)

        When("the public Operation receives the versioned command JSON")
        val response = take(subsystem.executeOperationResponse(request))
        val wait = response match {
          case OperationResponse.Scalar(value: String) => take(WorkflowWaitJsonV1.decodeC(value))
          case other => fail(s"Expected SplitPhase WAIT response, found $other")
        }

        Then("the Service returns the independently stored SplitPhase Handle")
        wait.handle.instanceIdentity shouldBe fixture.stored.get.identity
        wait.current shouldBe a[WorkflowContinuation.Wait]
        take(subsystem.executeOperationResponse(request)) shouldBe response
        fixture.creates shouldBe 1
        fixture.appends shouldBe 1
      } finally subsystem.shutdown()
    }
  }

  "SplitPhase proposal assessment execution" should {
    "advance only a partially validated proposal to the generated second state" in {
      val fixture = new Fixture
      val start = fixture.shapeReadyStart
      take(fixture.start(start))
      val command = SplitPhaseProposalAssessmentCommand(start, "1", "assessment-1")

      val assessed = take(fixture.stage(command))
      take(fixture.stage(command)) shouldBe assessed
      assessed.handle.instanceIdentity shouldBe fixture.stored.get.identity
      fixture.stored.map(_.revision) shouldBe Some(InstanceRevision(2L))
      fixture.stored.flatMap(_.currentProgression) shouldBe
        Some(ProgressionReference("entry.ProposalAssessment"))
      fixture.stored.map(_.history.size) shouldBe Some(2)
      fixture.appends shouldBe 2
      take(WorkflowWaitJsonV1.decodeC(take(WorkflowWaitJsonV1.encodeC(assessed)))) shouldBe assessed

      fixture.validationEvidence = fixture.validationEvidence.map(evidence =>
        evidence.copy(validationOwnership = evidence.validationOwnership.copy(
          driver = "changed-driver")))
      fixture.stage(command) shouldBe a[Consequence.Failure[?]]
      fixture.appends shouldBe 2

      fixture.stage(command.copy(idempotencyKey = "different-key")) shouldBe
        a[Consequence.Failure[?]]
      fixture.appends shouldBe 2
    }

    "keep the entry revision when a proposal or human decision is required" in {
      val missing = new Fixture
      val prepared = missing.shapeReadyStart
      val selected = prepared.copy(input = prepared.input.copy(proposalReference = None))
      missing.proposal = None
      take(missing.start(selected))
      take(missing.stage(SplitPhaseProposalAssessmentCommand(selected, "1", "assessment-1")))
      missing.stored.map(_.revision) shouldBe Some(InstanceRevision(1L))
      missing.appends shouldBe 1

      val decision = new Fixture
      val start = decision.shapeReadyStart
      decision.inventory = decision.inventory.map(_.copy(occupiedPhaseIdentities = Set("77.1")))
      take(decision.start(start))
      take(decision.stage(SplitPhaseProposalAssessmentCommand(start, "1", "assessment-2")))
      decision.stored.map(_.revision) shouldBe Some(InstanceRevision(1L))
      decision.appends shouldBe 1
    }

    "wait at revision one until the authoritative source boundary is available" in {
      val fixture = new Fixture
      val start = fixture.shapeReadyStart
      take(fixture.start(start))
      val originalBoundary = fixture.sourceBoundary
      fixture.sourceBoundary = None
      val command = SplitPhaseProposalAssessmentCommand(start, "1", "assessment-boundary")
      val waiting = take(fixture.stage(command))
      waiting.current shouldBe a[WorkflowContinuation.Wait]
      fixture.stored.map(_.revision) shouldBe Some(InstanceRevision(1L))
      fixture.appends shouldBe 1

      fixture.sourceBoundary = originalBoundary
      take(fixture.stage(command))
      fixture.stored.map(_.revision) shouldBe Some(InstanceRevision(2L))
      fixture.appends shouldBe 2
    }

    "reject missing validation evidence and wait on unresolved novel work" in {
      val missing = new Fixture
      val start = missing.shapeReadyStart
      take(missing.start(start))
      missing.validationEvidence = None
      missing.stage(SplitPhaseProposalAssessmentCommand(start, "1", "assessment-missing")) shouldBe
        a[Consequence.Failure[?]]
      missing.stored.map(_.revision) shouldBe Some(InstanceRevision(1L))

      val novel = new Fixture
      val novelStart = novel.shapeReadyStart
      take(novel.start(novelStart))
      novel.validationEvidence = novel.validationEvidence.map(evidence =>
        evidence.copy(calibrationRequests = evidence.calibrationRequests.map(request =>
          request.copy(policy = request.policy.copy(minimumSampleCount = 3)))))
      val waiting = take(novel.stage(SplitPhaseProposalAssessmentCommand(
        novelStart, "1", "assessment-novel")))
      waiting.current shouldBe a[WorkflowContinuation.Wait]
      novel.stored.map(_.revision) shouldBe Some(InstanceRevision(1L))
      novel.appends shouldBe 1
    }

    "keep the public assessment at revision one for an unresolved split edge" in {
      val fixture = new Fixture
      val start = fixture.shapeReadyStart
      take(fixture.start(start))
      fixture.validationEvidence = fixture.validationEvidence.map { evidence =>
        val last = evidence.semanticEdges.last
        evidence.copy(semanticEdges = evidence.semanticEdges.updated(
          evidence.semanticEdges.size - 1,
          last.copy(disposition = SplitPhasePartitionEnumeration.EdgeDisposition.Unresolved)))
      }
      val waiting = take(fixture.stage(SplitPhaseProposalAssessmentCommand(
        start, "1", "assessment-unresolved-edge")))
      waiting.current shouldBe a[WorkflowContinuation.Wait]
      fixture.stored.map(_.revision) shouldBe Some(InstanceRevision(1L))
      fixture.appends shouldBe 1
    }

    "retry a failed stage append without skipping or duplicating history" in {
      val fixture = new Fixture
      val start = fixture.shapeReadyStart
      take(fixture.start(start))
      val command = SplitPhaseProposalAssessmentCommand(start, "1", "assessment-retry-1")
      fixture.failAppendOnce = true

      fixture.stage(command) shouldBe a[Consequence.Failure[?]]
      fixture.stored.map(_.revision) shouldBe Some(InstanceRevision(1L))
      val assessed = take(fixture.stage(command))
      assessed.handle.instanceIdentity shouldBe fixture.stored.get.identity
      fixture.stored.map(_.history.size) shouldBe Some(2)
      fixture.appends shouldBe 2
      fixture.stage(command.copy(expectedRevision = "2")) shouldBe
        a[Consequence.Failure[?]]
    }

    "execute the generated stage Operation through a public Service fixture" in {
      val fixture = new Fixture
      val start = fixture.shapeReadyStart
      take(fixture.start(start))
      val operation = take(SplitPhaseProposalAssessmentServiceOperation.bindC(
        fixture.selections, fixture.entry, fixture.proposals,
        definition, configuration, fixture.instances))
      val command = SplitPhaseProposalAssessmentCommand(start, "1", "assessment-public-1")
      val wire = SplitPhaseProposalAssessmentServiceOperation.encodeCommand(command)
      take(SplitPhaseProposalAssessmentServiceOperation.decodeCommandC(wire)) shouldBe command
      val request = Request.of(
        component = SmWorkflowComponent.name, service = "SplitPhaseService",
        operation = "assessSplitProposal", arguments = List(Argument("arg1", wire))
      )
      val service = spec.ServiceDefinition(
        name = "SplitPhaseService",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))
      )
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
      val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
        name = "sm-workflow-split-phase-assessment", version = None,
        configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
        aliasResolver = AliasResolver.empty, runMode = RunMode.Command
      ))
      try {
        val component = new Component() {}
        component.initialize(ComponentInit(
          subsystem,
          Component.Core.create(SmWorkflowComponent.name, SmWorkflowComponent.componentId,
            ComponentInstanceId.default(SmWorkflowComponent.componentId), protocol),
          ComponentOrigin.Builtin
        ))
        subsystem.add(component)

        val boundary = fixture.sourceBoundary
        fixture.sourceBoundary = None
        val pendingResponse = take(subsystem.executeOperationResponse(request))
        val pendingWait = pendingResponse match {
          case OperationResponse.Scalar(value: String) => take(WorkflowWaitJsonV1.decodeC(value))
          case other => fail(s"Expected SplitPhase boundary WAIT response, found $other")
        }
        pendingWait.current shouldBe a[WorkflowContinuation.Wait]
        fixture.stored.map(_.revision) shouldBe Some(InstanceRevision(1L))
        fixture.sourceBoundary = boundary

        val response = take(subsystem.executeOperationResponse(request))
        val wait = response match {
          case OperationResponse.Scalar(value: String) => take(WorkflowWaitJsonV1.decodeC(value))
          case other => fail(s"Expected SplitPhase assessment WAIT response, found $other")
        }
        wait.handle.instanceIdentity shouldBe fixture.stored.get.identity
        fixture.stored.map(_.revision) shouldBe Some(InstanceRevision(2L))
        take(subsystem.executeOperationResponse(request)) shouldBe response
        fixture.appends shouldBe 2
      } finally subsystem.shutdown()
    }
  }

  private final class DecisionPort(
    transform: (ContinuationRequest[SplitPhaseIdentityDecision.Input] =>
      ContinuationRequest[SplitPhaseIdentityDecision.Input]) = (request => request)
  ) extends SplitPhaseIdentityDecision.Port {
    var calls = 0
    def resolveC(intent: SplitPhaseIdentityDecision.Intent) = {
      calls += 1
      val request = ContinuationRequest[SplitPhaseIdentityDecision.Input](
        StateMachineRunIdentity(s"human-boundary:${intent.handle.instanceIdentity.value}"),
        RuntimeContinuationIdentity(s"identity-decision:${sha256(intent.idempotencyKey)}"),
        intent.expectedRevision,
        StateMachineRequiredOperationIdentity("host.split-identity-decision"),
        StateMachineOperationIdentity("HumanBoundaryFixture", "decideSplitIdentity"),
        Some(TypedValue(SplitPhaseIdentityDecision.inputCodec.typeIdentity, intent.input)),
        None,
        ContextBundle("Resolve the stopped split identity conflict",
          Vector(ContextReference(intent.input.sourceSnapshotIdentity,
            intent.snapshot.modelRevision.get)),
          Vector(ContextReference(intent.input.proposalIdentity, intent.input.proposalDigest)),
          intent.snapshot),
        CompletionContract("host-human-decision-stop-only-v1", Vector.empty),
        EvidenceContract("host-human-decision-evidence-v1", Vector.empty),
        // A fixture-owned metadata identity, deliberately not a callable service.
        StateMachineOperationIdentity("HumanBoundaryFixture", "recordSplitIdentityDecision"))
      Consequence.success(transform(request))
    }
  }

  private def decisionWire(fixture: Fixture, start: SplitPhaseStartCommand,
    port: SplitPhaseIdentityDecision.Port,
    revision: String = "1"): Consequence[String] =
    SplitPhaseProposalAssessmentExecution.assessWireC(
      SplitPhaseProposalAssessmentCommand(start, revision, "identity-decision-1"),
      fixture.selections, fixture.entry, fixture.proposals,
      definition, configuration, fixture.instances, Some(port))

  "SplitPhase identity Decision projection" should {
    "return one typed stop through public Start, Service and CLI without granting authority" in {
      val fixture = new Fixture
      val start = fixture.shapeReadyStart
      fixture.inventory = fixture.inventory.map(_.copy(occupiedPhaseIdentities = Set("77.1")))
      val port = new DecisionPort
      val startOperation = take(SplitPhaseStartServiceOperation.bindC(
        fixture.selections, fixture.entry, definition, configuration, fixture.instances))
      val assessmentOperation = take(SplitPhaseProposalAssessmentServiceOperation.bindC(
        fixture.selections, fixture.entry, fixture.proposals, definition, configuration,
        fixture.instances, decisions = Some(port)))
      val service = spec.ServiceDefinition("SplitPhaseService",
        spec.OperationDefinitionGroup(NonEmptyVector.of(startOperation, assessmentOperation)))
      val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
        name = "sm-workflow-split-identity-decision", version = None,
        configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
        aliasResolver = AliasResolver.empty, runMode = RunMode.Command))
      try {
        val component = new Component() {}
        component.initialize(ComponentInit(subsystem,
          Component.Core.create(SmWorkflowComponent.name, SmWorkflowComponent.componentId,
            ComponentInstanceId.default(SmWorkflowComponent.componentId),
            Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))), ComponentOrigin.Builtin))
        subsystem.add(component)
        def execute(operation: String, wire: String): String =
          take(subsystem.executeOperationResponse(Request.of(component = SmWorkflowComponent.name,
            service = "SplitPhaseService", operation = operation,
            arguments = List(Argument("arg1", wire))))) match {
            case OperationResponse.Scalar(value: String) => value
            case other => fail(s"Expected SplitPhase Decision wire, found $other")
          }
        val started = take(WorkflowWaitJsonV1.decodeC(execute("startSplitPhase",
          SplitPhaseStartServiceOperation.encodeCommand(start))))
        val stopped = fixture.stored.get
        val command = SplitPhaseProposalAssessmentCommand(start, "1", "identity-decision-public")
        val commandWire = SplitPhaseProposalAssessmentServiceOperation.encodeCommand(command)
        val wire = execute("assessSplitProposal", commandWire)
        val decision = take(WorkflowDecisionJsonV1.decodeC(wire, SplitPhaseIdentityDecision.inputCodec))
        val current = decision.current.asInstanceOf[
          WorkflowContinuation.Decision[SplitPhaseIdentityDecision.Input]]
        decision.handle shouldBe started.handle
        current.request.input.get.value.reason shouldBe "child-phase-identity-collision"
        current.request.input.get.value.sourceAuthorityReference shouldBe "source-authority-1"
        current.request.resultType shouldBe None
        current.request.expectedRevision shouldBe StateMachineRevision("1")
        new CncfRuntime().executeCommandResponse(subsystem,
          Array(s"${SmWorkflowComponent.name}/SplitPhaseService/assessSplitProposal",
            "--json", commandWire)).map(_.print) shouldBe Consequence.success(wire)
        execute("assessSplitProposal", commandWire) shouldBe wire
        WorkflowWorkOrderJsonV1.decodeC(wire, SplitPhaseIdentityDecision.inputCodec) shouldBe
          a[Consequence.Failure[?]]
        WorkflowWaitJsonV1.decodeC(wire) shouldBe a[Consequence.Failure[?]]
        val fabricatedApproval = ContinuationResult(decision.handle, current.request.runId,
          current.request.continuationId, current.request.expectedRevision,
          current.request.context.snapshot, TypedValue("HumanApproval", "approved"),
          ContextReference("fabricated-approval", "1"), Vector.empty,
          ExecutionEvidence(Vector.empty))
        admitResultC(decision, fabricatedApproval) shouldBe a[Consequence.Failure[?]]
        subsystem.executeOperationResponse(Request.of(component = SmWorkflowComponent.name,
          service = "HumanBoundaryFixture", operation = "recordSplitIdentityDecision",
          arguments = List(Argument("arg1", "approved")))) shouldBe a[Consequence.Failure[?]]
        fixture.stored shouldBe Some(stopped)
        fixture.creates shouldBe 1
        fixture.appends shouldBe 1
        fixture.stored.get.lifecycle shouldBe Lifecycle.Active
        fixture.stored.get.suspension shouldBe None
      } finally subsystem.shutdown()
    }

    "keep nested phase numbering at the same typed identity Decision boundary" in {
      val fixture = new Fixture
      fixture.inventory = Some(SourceInventory("77.1", "a" * 64,
        Vector("item-a", "item-b"), Set.empty))
      val draft = ProposalDraft("split-proposal-1", "77.1", "source-authority-1",
        "a" * 64, Vector(Child("77.1", Vector("item-a"), 360, true),
          Child("77.1.1", Vector("item-b"), 360, true)))
      fixture.draft = Some(draft)
      fixture.proposal = fixture.proposal.map(_.copy(digest = digestOf(draft)))
      val started = take(fixture.start())
      val wire = take(decisionWire(fixture, command, new DecisionPort))
      val decoded = take(WorkflowDecisionJsonV1.decodeC(wire, SplitPhaseIdentityDecision.inputCodec))
      decoded.handle shouldBe started.handle
      decoded.current.asInstanceOf[WorkflowContinuation.Decision[SplitPhaseIdentityDecision.Input]]
        .request.input.get.value.reason shouldBe "nested-or-unknown-phase-numbering"
      fixture.appends shouldBe 1
    }

    "leave deterministic readiness and missing source or semantic evidence out of Decision" in {
      for (pending <- Vector("source", "novel", "ready")) {
        val fixture = new Fixture
        val start = fixture.shapeReadyStart
        take(fixture.start(start))
        pending match {
          case "source" => fixture.sourceBoundary = None
          case "novel" => fixture.validationEvidence = fixture.validationEvidence.map(evidence =>
            evidence.copy(calibrationRequests = evidence.calibrationRequests.map(request =>
              request.copy(policy = request.policy.copy(minimumSampleCount = 3)))))
          case _ => ()
        }
        val port = new DecisionPort
        take(WorkflowWaitJsonV1.decodeC(take(decisionWire(fixture, start, port))))
          .current shouldBe a[WorkflowContinuation.Wait]
        port.calls shouldBe 0
        fixture.appends shouldBe (if (pending == "ready") 2 else 1)
      }
    }

    "reject host request drift and attempts to reuse generated semantic or completion operations" in {
      type R = ContinuationRequest[SplitPhaseIdentityDecision.Input]
      val variants: Vector[R => R] = Vector(
        r => r.copy(expectedRevision = StateMachineRevision("2")),
        r => r.copy(context = r.context.copy(snapshot = r.context.snapshot.copy(
          evidenceRevision = Some("c" * 64)))),
        r => r.copy(input = r.input.map(v => v.copy(value = v.value.copy(
          sourceAuthorityReference = "caller-authority")))),
        r => r.copy(requiredOperation = StateMachineRequiredOperationIdentity("assess-novel-split-capability")),
        r => r.copy(operation = StateMachineOperationIdentity("SplitSemanticService", "assessNovelSplitWork")),
        r => r.copy(completionOperation = StateMachineOperationIdentity("SplitPhaseService", "submitSplitSemanticResult")),
        r => r.copy(context = r.context.copy(requiredFacts = Vector.empty)),
        r => r.copy(context = r.context.copy(references = Vector.empty)),
        r => r.copy(resultType = Some(StateMachineResultTypeReference("SplitPhaseSemanticResult"))),
        r => r.copy(continuationId = RuntimeContinuationIdentity("")),
        r => r.copy(resultType = null),
        r => r.copy(completionOperation = null))
      for (change <- variants) {
        val fixture = new Fixture
        val start = fixture.shapeReadyStart
        fixture.inventory = fixture.inventory.map(_.copy(occupiedPhaseIdentities = Set("77.1")))
        take(fixture.start(start))
        val before = fixture.stored
        decisionWire(fixture, start, new DecisionPort(change)) shouldBe a[Consequence.Failure[?]]
        fixture.stored shouldBe before
        fixture.appends shouldBe 1
      }
    }

    "fail closed when the opted-in host boundary is unavailable" in {
      val fixture = new Fixture
      val start = fixture.shapeReadyStart
      fixture.inventory = fixture.inventory.map(_.copy(occupiedPhaseIdentities = Set("77.1")))
      take(fixture.start(start))
      val before = fixture.stored
      val missing = new SplitPhaseIdentityDecision.Port {
        def resolveC(intent: SplitPhaseIdentityDecision.Intent) =
          Consequence.configurationInvalid("No host human Decision boundary is bound")
      }
      decisionWire(fixture, start, missing) shouldBe a[Consequence.Failure[?]]
      fixture.stored shouldBe before
      fixture.appends shouldBe 1
      // The old explicitly unconfigured fixture retains its WAIT compatibility.
      take(fixture.stage(SplitPhaseProposalAssessmentCommand(start, "1", "legacy-assessment")))
        .current shouldBe a[WorkflowContinuation.Wait]
      SplitPhaseProposalAssessmentServiceOperation.bindC(fixture.selections, fixture.entry,
        fixture.proposals, definition, configuration, fixture.instances,
        decisions = Some(null)) shouldBe a[Consequence.Failure[?]]
    }

    "reject stale selectors and source drift before consulting the human boundary" in {
      val fixture = new Fixture
      val start = fixture.shapeReadyStart
      fixture.inventory = fixture.inventory.map(_.copy(occupiedPhaseIdentities = Set("77.1")))
      take(fixture.start(start))
      val before = fixture.stored
      val port = new DecisionPort
      decisionWire(fixture, start, port, "2") shouldBe a[Consequence.Failure[?]]
      fixture.source = fixture.source.map(_.copy(digest = "c" * 64))
      decisionWire(fixture, start, port) shouldBe a[Consequence.Failure[?]]
      port.calls shouldBe 0
      fixture.stored shouldBe before
    }

    "reject a concurrent instance change after host projection without returning a stale Decision" in {
      val fixture = new Fixture
      val start = fixture.shapeReadyStart
      fixture.inventory = fixture.inventory.map(_.copy(occupiedPhaseIdentities = Set("77.1")))
      take(fixture.start(start))
      val changing = new DecisionPort(request => {
        fixture.inventory = fixture.inventory.map(_.copy(occupiedPhaseIdentities = Set.empty))
        take(fixture.stage(SplitPhaseProposalAssessmentCommand(start, "1", "concurrent-assessment")))
        request
      })
      decisionWire(fixture, start, changing) shouldBe a[Consequence.Failure[?]]
      fixture.stored.map(_.revision) shouldBe Some(InstanceRevision(2L))
      fixture.appends shouldBe 2 // The concurrent deterministic assessment, not Decision approval.
    }

    "strictly decode the identity Decision input without accepting approval or selector fields" in {
      val input = SplitPhaseIdentityDecision.Input("child-phase-identity-collision",
        "source-snapshot-1", "77", "source-authority-1", "a" * 64,
        "split-proposal-1", "b" * 64)
      val codec = SplitPhaseIdentityDecision.inputCodec
      val json = codec.encode(input)
      codec.decode(json) shouldBe Right(input)
      codec.decode(json.mapObject(_.add("approved", io.circe.Json.True))).isLeft shouldBe true
      codec.decode(json.mapObject(_.remove("proposalDigest"))).isLeft shouldBe true
      codec.decode(json.mapObject(_.add("reason", io.circe.Json.fromString("approved"))))
        .isLeft shouldBe true
      codec.decode(json.mapObject(_.add("proposalDigest", io.circe.Json.fromString("unknown"))))
        .isLeft shouldBe true
      codec.decode(io.circe.Json.Null).isLeft shouldBe true
    }
  }

  "SplitPhase preview execution" should {
    "complete only the admitted PREVIEW at revision two without writing documents" in {
      val root = Path.of("src/test/resources/source-boundary-reader-fixture")
      val fixture = new CatalogFixture(root)
      take(fixture.start())
      take(fixture.assess())
      val catalog = fixture.previewCatalog
      val command = SplitPhasePreviewCommand(fixture.startCommand, "2", "preview-1")
      val source = root.resolve("docs/phase/phase-77.md")
      val sourceBytes = Files.readAllBytes(source).toVector

      val completed = take(fixture.preview(command, catalog))
      completed.current shouldBe a[WorkflowContinuation.Terminal[?]]
      fixture.run.stored.map(_.revision) shouldBe Some(InstanceRevision(3L))
      fixture.run.stored.map(_.lifecycle) shouldBe Some(Lifecycle.Completed)
      fixture.run.stored.flatMap(_.currentProgression) shouldBe None
      fixture.run.appends shouldBe 3
      val wire = take(WorkflowTerminalJsonV1.encodeC(completed,
        SplitPhasePreviewServiceOperation.terminalCodec))
      val decoded = take(WorkflowTerminalJsonV1.decodeC(wire,
        SplitPhasePreviewServiceOperation.terminalCodec))
      decoded.current shouldBe a[WorkflowContinuation.Terminal[?]]
      wire should include("work-3")
      wire should include("SplitPhasePreviewResult")
      Files.readAllBytes(source).toVector shouldBe sourceBytes
      Files.exists(root.resolve("docs/phase/phase-77.1.md")) shouldBe false
      fixture.preview(command, catalog) shouldBe a[Consequence.Failure[?]]
      fixture.run.appends shouldBe 3
    }

    "leave the instance active for stale admission, APPLY mode, or failed append" in {
      val root = Path.of("src/test/resources/source-boundary-reader-fixture")
      val fixture = new CatalogFixture(root)
      take(fixture.start())
      take(fixture.assess())
      val catalog = fixture.previewCatalog
      val command = SplitPhasePreviewCommand(fixture.startCommand, "2", "preview-2")
      fixture.preview(command, catalog.copy(admitted =
        catalog.admitted.copy(proposalDigest = "0" * 64))) shouldBe
        a[Consequence.Failure[?]]
      fixture.preview(command.copy(startCommand = fixture.startCommand.copy(input =
        fixture.startCommand.input.copy(invocationMode = "APPLY"))), catalog) shouldBe
        a[Consequence.Failure[?]]
      fixture.preview(command.copy(expectedRevision = "1"), catalog) shouldBe
        a[Consequence.Failure[?]]
      fixture.run.failAppendOnce = true
      fixture.preview(command, catalog) shouldBe a[Consequence.Failure[?]]
      fixture.run.stored.map(_.revision) shouldBe Some(InstanceRevision(2L))
      fixture.run.appends shouldBe 2
      take(fixture.preview(command, catalog))
      fixture.run.appends shouldBe 3
    }

    "execute the generated preview Operation through a public Service fixture" in {
      val root = Path.of("src/test/resources/source-boundary-reader-fixture")
      val fixture = new CatalogFixture(root)
      take(fixture.start())
      take(fixture.assess())
      val catalog = fixture.previewCatalog
      val catalogPort = new SplitPhasePreviewExecution.CatalogPort {
        def loadC(start: SplitPhaseStartCommand) = Consequence.success(catalog)
      }
      val operation = take(SplitPhasePreviewServiceOperation.bindC(
        fixture.run.selections, fixture.entry, fixture.proposals, catalogPort,
        definition, configuration, fixture.run.instances))
      val command = SplitPhasePreviewCommand(fixture.startCommand, "2", "preview-public-1")
      val wire = SplitPhasePreviewServiceOperation.encodeCommand(command)
      take(SplitPhasePreviewServiceOperation.decodeCommandC(wire)) shouldBe command
      val request = Request.of(
        component = SmWorkflowComponent.name, service = "SplitPhaseService",
        operation = "previewSplitPhase", arguments = List(Argument("arg1", wire))
      )
      val service = spec.ServiceDefinition(
        name = "SplitPhaseService",
        operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))
      )
      val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
      val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
        name = "sm-workflow-split-phase-preview", version = None,
        configuration = ResolvedConfiguration(GenericConfiguration.empty, ConfigurationTrace.empty),
        aliasResolver = AliasResolver.empty, runMode = RunMode.Command
      ))
      try {
        val component = new Component() {}
        component.initialize(ComponentInit(
          subsystem,
          Component.Core.create(SmWorkflowComponent.name, SmWorkflowComponent.componentId,
            ComponentInstanceId.default(SmWorkflowComponent.componentId), protocol),
          ComponentOrigin.Builtin
        ))
        subsystem.add(component)

        val terminalWire = take(subsystem.executeOperationResponse(request)) match {
          case OperationResponse.Scalar(value: String) => value
          case other => fail(s"Expected SplitPhase TERMINAL response, found $other")
        }
        val terminal = take(WorkflowTerminalJsonV1.decodeC(terminalWire,
          SplitPhasePreviewServiceOperation.terminalCodec))
        terminal.current shouldBe a[WorkflowContinuation.Terminal[?]]
        terminalWire should include("work-3")
        fixture.run.stored.map(_.lifecycle) shouldBe Some(Lifecycle.Completed)
      } finally subsystem.shutdown()
    }
  }

  "SplitPhase document projection" should {
    "produce a deterministic bounded write set without touching Phase files" in {
      val root = Path.of("src/test/resources/source-boundary-reader-fixture")
      val fixture = new CatalogFixture(root)
      val catalog = fixture.previewCatalog
      val source = root.resolve("docs/phase/phase-77.md")
      val before = Files.readAllBytes(source).toVector

      val plan = take(fixture.documentPlan(catalog))
      take(fixture.documentPlan(catalog)) shouldBe plan
      plan.proposalDigest shouldBe catalog.admitted.proposalDigest
      plan.changes.map(_.relativePath) shouldBe
        Vector("docs/phase/phase-77.md", "docs/phase/phase-77.1.md")
      plan.changes.head.expectedSha256 shouldBe
        Some("c25b570819610acae6f27df9d4f456a0af164ab368242641b7845febdbfb5455")
      plan.changes(1).expectedSha256 shouldBe None
      plan.changes.head.desiredContent should include("work-1")
      plan.changes.head.desiredContent should include("work-2")
      plan.changes.head.desiredContent should not include ("| work-3 |")
      plan.changes(1).desiredContent should include("| work-3 |")
      plan.changes(1).desiredContent should include("split-phase-projection.v1")
      Files.readAllBytes(source).toVector shouldBe before
      Files.exists(root.resolve("docs/phase/phase-77.1.md")) shouldBe false
      fixture.documentPlan(catalog.copy(admitted =
        catalog.admitted.copy(proposalDigest = "0" * 64))) shouldBe
        a[Consequence.Failure[?]]
    }

    "reject a child Phase identity occupied after the source snapshot" in {
      val source = Path.of("src/test/resources/source-boundary-reader-fixture")
        .toAbsolutePath.normalize
      val copied = Files.createTempDirectory("smwf-document-projection-")
      val walked = Files.walk(source)
      try walked.iterator().asScala.foreach { path =>
        val target = copied.resolve(source.relativize(path).toString)
        if (Files.isDirectory(path)) Files.createDirectories(target)
        else Files.copy(path, target)
      } finally walked.close()
      try {
        val fixture = new CatalogFixture(copied)
        val catalog = fixture.previewCatalog
        Files.writeString(copied.resolve("docs/phase/phase-77.1.md"),
          "# Phase 77.1 - unrelated\n")
        fixture.documentPlan(catalog) shouldBe a[Consequence.Failure[?]]
      } finally {
        val created = Files.walk(copied)
        try created.iterator().asScala.toVector
          .sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
        finally created.close()
      }
    }
  }

  "SplitPhase document conflict classification" should {
    "separate exact base, already applied, and partial or unrelated drift" in {
      val root = Path.of("src/test/resources/source-boundary-reader-fixture")
      val fixture = new CatalogFixture(root)
      val plan = take(fixture.documentPlan(fixture.previewCatalog))
      val sourcePath = plan.changes.head.relativePath
      val childPath = plan.changes(1).relativePath
      var current = Map(sourcePath -> Files.readString(root.resolve(sourcePath)))
      val port = new SplitPhaseDocumentConflict.ReadPort {
        def readC(path: String): Consequence[Option[String]] =
          Consequence.success(current.get(path))
      }

      take(SplitPhaseDocumentConflict.classifyC(plan, port)) shouldBe
        SplitPhaseDocumentConflict.Outcome.Ready(Vector(
          sourcePath -> plan.changes.head.expectedSha256,
          childPath -> None))

      current = current.updated(childPath, "# Phase 77.1 - unrelated\n")
      take(SplitPhaseDocumentConflict.classifyC(plan, port)) shouldBe
        SplitPhaseDocumentConflict.Outcome.Conflict(Vector(childPath))

      current = Map(sourcePath -> plan.changes.head.desiredContent)
      take(SplitPhaseDocumentConflict.classifyC(plan, port)) shouldBe
        SplitPhaseDocumentConflict.Outcome.Conflict(Vector(sourcePath))

      current = current.updated(childPath, plan.changes(1).desiredContent)
      take(SplitPhaseDocumentConflict.classifyC(plan, port)) shouldBe
        SplitPhaseDocumentConflict.Outcome.AlreadyApplied

      SplitPhaseDocumentConflict.classifyC(plan.copy(changes =
        plan.changes.updated(1, plan.changes(1).copy(desiredSha256 = "0" * 64))),
        port) shouldBe a[Consequence.Failure[?]]

      SplitPhaseDocumentConflict.classifyC(plan.copy(changes =
        plan.changes.updated(1, plan.changes(1).copy(expectedSha256 = null))),
        port) shouldBe a[Consequence.Failure[?]]
    }
  }

  "SplitPhase projected document static validation" should {
    "preserve exact item ownership, boundary metadata, and proposal provenance" in {
      val root = Path.of("src/test/resources/source-boundary-reader-fixture")
      val fixture = new CatalogFixture(root)
      val plan = take(fixture.documentPlan(fixture.previewCatalog))
      take(SplitPhaseDocumentStaticValidation.validateC(plan)) shouldBe (())

      def changed(content: String) = plan.copy(changes =
        plan.changes.updated(1, plan.changes(1).copy(
          desiredContent = content, desiredSha256 = sha256(content))))
      val child = plan.changes(1).desiredContent
      SplitPhaseDocumentStaticValidation.validateC(changed(
        child.replace("| work-3 |", "| work-2 |"))) shouldBe
        a[Consequence.Failure[?]]
      SplitPhaseDocumentStaticValidation.validateC(changed(
        child.replace("close-work-3", "close-other"))) shouldBe
        a[Consequence.Failure[?]]
      SplitPhaseDocumentStaticValidation.validateC(changed(
        child.replace(plan.proposalDigest, "0" * 64))) shouldBe
        a[Consequence.Failure[?]]
    }
  }

  "SplitPhase document atomic CAS contract" should {
    "reject a malformed projected write set before touching its host port" in {
      val root = Path.of("src/test/resources/source-boundary-reader-fixture")
      val fixture = new CatalogFixture(root)
      val plan = take(fixture.documentPlan(fixture.previewCatalog))
      val child = plan.changes(1)
      val malformed = child.desiredContent.replace("close-work-3", "close-other")
      val invalid = plan.copy(changes = plan.changes.updated(1,
        child.copy(desiredContent = malformed, desiredSha256 = sha256(malformed))))
      var reads = 0
      var writes = 0
      val port = new SplitPhaseDocumentCas.Port {
        def readC(path: String): Consequence[Option[String]] = {
          reads += 1
          Consequence.success(None)
        }
        def compareAndSwapC(changes: Vector[SplitPhaseDocumentProjection.Change]):
            Consequence[SplitPhaseDocumentCas.Port.Result] = {
          writes += 1
          Consequence.success(SplitPhaseDocumentCas.Port.Rejected)
        }
      }

      SplitPhaseDocumentCas.executeC(invalid, port) shouldBe
        a[Consequence.Failure[?]]
      reads shouldBe 0
      writes shouldBe 0
    }

    "apply exactly once and treat an identical replay as success without another write" in {
      val root = Path.of("src/test/resources/source-boundary-reader-fixture")
      val fixture = new CatalogFixture(root)
      val plan = take(fixture.documentPlan(fixture.previewCatalog))
      val source = plan.changes.head.relativePath
      var current = Map(source -> Files.readString(root.resolve(source)))
      var writes = 0
      val port = new SplitPhaseDocumentCas.Port {
        def readC(path: String): Consequence[Option[String]] =
          Consequence.success(current.get(path))
        def compareAndSwapC(changes: Vector[SplitPhaseDocumentProjection.Change]):
            Consequence[SplitPhaseDocumentCas.Port.Result] = {
          writes += 1
          val matches = changes.forall(change =>
            current.get(change.relativePath).map(sha256) ==
              change.expectedSha256)
          if (matches) {
            current = current ++ changes.map(change =>
              change.relativePath -> change.desiredContent)
            Consequence.success(SplitPhaseDocumentCas.Port.Applied)
          } else Consequence.success(SplitPhaseDocumentCas.Port.Rejected)
        }
      }

      take(SplitPhaseDocumentCas.executeC(plan, port)) shouldBe
        SplitPhaseDocumentCas.Outcome.Applied
      writes shouldBe 1
      plan.changes.foreach(change =>
        current.get(change.relativePath) shouldBe Some(change.desiredContent))
      take(SplitPhaseDocumentCas.executeC(plan, port)) shouldBe
        SplitPhaseDocumentCas.Outcome.AlreadyApplied
      writes shouldBe 1
      Files.exists(root.resolve("docs/phase/phase-77.1.md")) shouldBe false
    }

    "reject concurrent drift without claiming a partial write succeeded" in {
      val root = Path.of("src/test/resources/source-boundary-reader-fixture")
      val fixture = new CatalogFixture(root)
      val plan = take(fixture.documentPlan(fixture.previewCatalog))
      val source = plan.changes.head.relativePath
      val child = plan.changes(1).relativePath
      val original = Files.readString(root.resolve(source))
      var current = Map(source -> original)
      var writes = 0
      val port = new SplitPhaseDocumentCas.Port {
        def readC(path: String): Consequence[Option[String]] =
          Consequence.success(current.get(path))
        def compareAndSwapC(changes: Vector[SplitPhaseDocumentProjection.Change]):
            Consequence[SplitPhaseDocumentCas.Port.Result] = {
          writes += 1
          current = current.updated(child, "# Phase 77.1 - concurrent owner\n")
          Consequence.success(SplitPhaseDocumentCas.Port.Rejected)
        }
      }

      take(SplitPhaseDocumentCas.executeC(plan, port)) shouldBe
        SplitPhaseDocumentCas.Outcome.Conflict(Vector(child))
      writes shouldBe 1
      current(source) shouldBe original
      current(child) shouldBe "# Phase 77.1 - concurrent owner\n"
    }

    "accept an identical concurrent installation after its CAS rejection" in {
      val root = Path.of("src/test/resources/source-boundary-reader-fixture")
      val fixture = new CatalogFixture(root)
      val plan = take(fixture.documentPlan(fixture.previewCatalog))
      val source = plan.changes.head.relativePath
      var current = Map(source -> Files.readString(root.resolve(source)))
      val port = new SplitPhaseDocumentCas.Port {
        def readC(path: String): Consequence[Option[String]] =
          Consequence.success(current.get(path))
        def compareAndSwapC(changes: Vector[SplitPhaseDocumentProjection.Change]):
            Consequence[SplitPhaseDocumentCas.Port.Result] = {
          current = current ++ changes.map(change =>
            change.relativePath -> change.desiredContent)
          Consequence.success(SplitPhaseDocumentCas.Port.Rejected)
        }
      }

      take(SplitPhaseDocumentCas.executeC(plan, port)) shouldBe
        SplitPhaseDocumentCas.Outcome.AlreadyApplied
    }

    "detect a port that reports success without installing every desired document" in {
      val root = Path.of("src/test/resources/source-boundary-reader-fixture")
      val fixture = new CatalogFixture(root)
      val plan = take(fixture.documentPlan(fixture.previewCatalog))
      val source = plan.changes.head.relativePath
      var current = Map(source -> Files.readString(root.resolve(source)))
      val port = new SplitPhaseDocumentCas.Port {
        def readC(path: String): Consequence[Option[String]] =
          Consequence.success(current.get(path))
        def compareAndSwapC(changes: Vector[SplitPhaseDocumentProjection.Change]):
            Consequence[SplitPhaseDocumentCas.Port.Result] = {
          current = current.updated(source, changes.head.desiredContent)
          Consequence.success(SplitPhaseDocumentCas.Port.Applied)
        }
      }

      SplitPhaseDocumentCas.executeC(plan, port) shouldBe a[Consequence.Failure[?]]
    }
  }

  "SplitPhase compensating file port" should {
    def withCopiedFixture[A](run: Path => A): A = {
      val source = Path.of("src/test/resources/source-boundary-reader-fixture")
        .toAbsolutePath.normalize
      val copied = Files.createTempDirectory("smwf-document-file-port-")
      val walked = Files.walk(source)
      try walked.iterator().asScala.foreach { path =>
        val target = copied.resolve(source.relativize(path).toString)
        if (Files.isDirectory(path)) Files.createDirectories(target)
        else Files.copy(path, target)
      } finally walked.close()
      try run(copied)
      finally {
        val created = Files.walk(copied)
        try created.iterator().asScala.toVector
          .sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
        finally created.close()
      }
    }

    "prepare an APPLY Start and apply only its copied documents" in
      withCopiedFixture { root =>
        val fixture = new CatalogFixture(root, "APPLY")
        take(fixture.start())
        take(fixture.assess())
        val catalog = fixture.previewCatalog
        val source = root.resolve("docs/phase/phase-77.md")
        val child = root.resolve("docs/phase/phase-77.1.md")
        val original = Files.readAllBytes(source).toVector
        val catalogPort = new SplitPhasePreviewExecution.CatalogPort {
          def loadC(start: SplitPhaseStartCommand) = Consequence.success(catalog)
        }
        val apply = SplitPhaseApplyDocuments.Command(fixture.startCommand, "2", "apply-1")

        val prepared = take(SplitPhaseApplyDocuments.prepareC(apply,
          fixture.run.selections, fixture.entry, fixture.proposals, catalogPort,
          definition, configuration, fixture.run.instances))
        Files.readAllBytes(source).toVector shouldBe original
        Files.exists(child) shouldBe false
        take(SplitPhaseApplyDocuments.applyDocumentsC(prepared)) shouldBe
          SplitPhaseDocumentCas.Outcome.Applied
        take(SplitPhaseApplyDocuments.applyDocumentsC(prepared)) shouldBe
          SplitPhaseDocumentCas.Outcome.AlreadyApplied
        take(SplitPhaseApplyDocuments.resumeDocumentsC(root,
          prepared.instanceIdentity, fixture.startCommand)) shouldBe
          SplitPhaseDocumentCas.Outcome.AlreadyApplied
        prepared.plan.changes.foreach(change =>
          Files.readString(root.resolve(change.relativePath)) shouldBe change.desiredContent)
        fixture.run.stored.map(_.revision) shouldBe Some(InstanceRevision(2L))
        fixture.run.stored.map(_.lifecycle) shouldBe Some(Lifecycle.Active)
      }

    "persist a host-owned APPLY plan and reject a changed replay" in
      withCopiedFixture { root =>
        val fixture = new CatalogFixture(root, "APPLY")
        take(fixture.start())
        take(fixture.assess())
        val plan = take(fixture.documentPlan(fixture.previewCatalog))
        val identity = fixture.run.stored.get.identity
        val source = root.resolve(plan.changes.head.relativePath)
        val original = Files.readAllBytes(source).toVector

        val stored = take(SplitPhaseApplyPlanStore.saveC(root, identity,
          fixture.startCommand, plan))
        take(SplitPhaseApplyPlanStore.saveC(root, identity,
          fixture.startCommand, plan)) shouldBe stored
        take(SplitPhaseApplyPlanStore.loadC(root, identity,
          fixture.startCommand)) shouldBe Some(stored)
        SplitPhaseApplyPlanStore.saveC(root, identity, fixture.startCommand,
          plan.copy(proposalIdentity = "other-proposal")) shouldBe
          a[Consequence.Failure[?]]
        SplitPhaseApplyPlanStore.loadC(root, identity,
          fixture.startCommand.copy(idempotencyKey = "different-start")) shouldBe
          a[Consequence.Failure[?]]
        Files.readAllBytes(source).toVector shouldBe original
        Files.exists(root.resolve(plan.changes(1).relativePath)) shouldBe false
      }

    "complete a host-only APPLY and replay without reloading the changed source" in
      withCopiedFixture { root =>
        val fixture = new CatalogFixture(root, "APPLY")
        take(fixture.start())
        take(fixture.assess())
        val catalog = fixture.previewCatalog
        var loads = 0
        val catalogPort = new SplitPhasePreviewExecution.CatalogPort {
          def loadC(start: SplitPhaseStartCommand) = {
            loads += 1
            Consequence.success(catalog)
          }
        }
        val apply = SplitPhaseApplyDocuments.Command(fixture.startCommand, "2", "apply-final-1")

        val completed = take(SplitPhaseApplyExecution.applyC(apply, root,
          fixture.run.selections, fixture.entry, fixture.proposals, catalogPort,
          definition, configuration, fixture.run.instances))
        completed shouldBe a[SplitPhaseApplyExecution.Outcome.Completed]
        fixture.run.stored.map(_.revision) shouldBe Some(InstanceRevision(4L))
        fixture.run.stored.map(_.lifecycle) shouldBe Some(Lifecycle.Completed)
        val beforeReplayLoads = loads
        take(SplitPhaseApplyExecution.applyC(apply, root,
          fixture.run.selections, fixture.entry, fixture.proposals, catalogPort,
          definition, configuration, fixture.run.instances)) shouldBe
          SplitPhaseApplyExecution.Outcome.AlreadyCompleted(
            completed.asInstanceOf[SplitPhaseApplyExecution.Outcome.Completed].planDigest)
        loads shouldBe beforeReplayLoads
      }

    "finish APPLY from the saved plan after completion append fails" in
      withCopiedFixture { root =>
        val fixture = new CatalogFixture(root, "APPLY")
        take(fixture.start())
        take(fixture.assess())
        val catalog = fixture.previewCatalog
        var loads = 0
        val catalogPort = new SplitPhasePreviewExecution.CatalogPort {
          def loadC(start: SplitPhaseStartCommand) = {
            loads += 1
            Consequence.success(catalog)
          }
        }
        val apply = SplitPhaseApplyDocuments.Command(fixture.startCommand, "2", "apply-final-2")
        fixture.run.failAppendAtRevisionOnce = Some(3L)

        SplitPhaseApplyExecution.applyC(apply, root,
          fixture.run.selections, fixture.entry, fixture.proposals, catalogPort,
          definition, configuration, fixture.run.instances) shouldBe
          a[Consequence.Failure[?]]
        fixture.run.stored.map(_.revision) shouldBe Some(InstanceRevision(3L))
        fixture.run.stored.map(_.lifecycle) shouldBe Some(Lifecycle.Active)
        Files.readString(root.resolve("docs/phase/phase-77.1.md")) should include("work-3")
        val beforeResumeLoads = loads

        take(SplitPhaseApplyExecution.applyC(apply, root,
          fixture.run.selections, fixture.entry, fixture.proposals, catalogPort,
          definition, configuration, fixture.run.instances)) shouldBe
          a[SplitPhaseApplyExecution.Outcome.Completed]
        loads shouldBe beforeResumeLoads
        fixture.run.stored.map(_.revision) shouldBe Some(InstanceRevision(4L))
        fixture.run.stored.map(_.lifecycle) shouldBe Some(Lifecycle.Completed)
      }

    "execute and replay the generated APPLY Operation through the public Service" in
      withCopiedFixture { root =>
        val fixture = new CatalogFixture(root, "APPLY")
        take(fixture.start())
        take(fixture.assess())
        val catalog = fixture.previewCatalog
        var loads = 0
        val catalogPort = new SplitPhasePreviewExecution.CatalogPort {
          def loadC(start: SplitPhaseStartCommand) = {
            loads += 1
            Consequence.success(catalog)
          }
        }
        val operation = take(SplitPhaseApplyServiceOperation.bindC(root,
          fixture.run.selections, fixture.entry, fixture.proposals, catalogPort,
          definition, configuration, fixture.run.instances))
        val command = SplitPhaseApplyCommand(fixture.startCommand, "2", "apply-public-1")
        val wire = SplitPhaseApplyServiceOperation.encodeCommand(command)
        take(SplitPhaseApplyServiceOperation.decodeCommandC(wire)) shouldBe command
        val request = Request.of(
          component = SmWorkflowComponent.name, service = "SplitPhaseService",
          operation = "applySplitPhase", arguments = List(Argument("arg1", wire))
        )
        val service = spec.ServiceDefinition(
          name = "SplitPhaseService",
          operations = spec.OperationDefinitionGroup(NonEmptyVector.of(operation))
        )
        val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
        val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
          name = "sm-workflow-split-phase-apply", version = None,
          configuration = ResolvedConfiguration(GenericConfiguration.empty,
            ConfigurationTrace.empty),
          aliasResolver = AliasResolver.empty, runMode = RunMode.Command
        ))
        try {
          val component = new Component() {}
          component.initialize(ComponentInit(
            subsystem,
            Component.Core.create(SmWorkflowComponent.name, SmWorkflowComponent.componentId,
              ComponentInstanceId.default(SmWorkflowComponent.componentId), protocol),
            ComponentOrigin.Builtin
          ))
          subsystem.add(component)

          def execute(request: Request): String =
            take(subsystem.executeOperationResponse(request)) match {
              case OperationResponse.Scalar(value: String) => value
              case other => fail(s"Expected SplitPhase APPLY TERMINAL response, found $other")
            }

          val terminalWire = execute(request)
          val terminal = take(WorkflowTerminalJsonV1.decodeC(terminalWire,
            SplitPhaseApplyServiceOperation.terminalCodec))
          terminal.current shouldBe a[WorkflowContinuation.Terminal[?]]
          terminalWire should include("planDigest")
          Files.exists(root.resolve("docs/phase/phase-77.1.md")) shouldBe true
          fixture.run.stored.map(_.revision) shouldBe Some(InstanceRevision(4L))
          val beforeReplayLoads = loads
          execute(request) shouldBe terminalWire
          loads shouldBe beforeReplayLoads
          val changed = command.copy(idempotencyKey = "apply-public-other")
          val changedRequest = Request.of(
            component = SmWorkflowComponent.name, service = "SplitPhaseService",
            operation = "applySplitPhase", arguments = List(Argument("arg1",
              SplitPhaseApplyServiceOperation.encodeCommand(changed)))
          )
          subsystem.executeOperationResponse(changedRequest) shouldBe
            a[Consequence.Failure[?]]
        } finally subsystem.shutdown()
      }

    "route Start, assessment, and APPLY through one host-bound fixture Service" in
      withCopiedFixture { root =>
        val fixture = new CatalogFixture(root, "APPLY")
        val catalogPort = new SplitPhasePreviewExecution.CatalogPort {
          def loadC(start: SplitPhaseStartCommand) =
            Consequence.success(fixture.previewCatalog)
        }
        val service = take(SplitPhaseFixtureServiceAssembly.bindC(root,
          fixture.run.selections, fixture.entry, fixture.proposals, catalogPort,
          definition, configuration, fixture.run.instances))
        service.operations.operations.toVector.map(_.specification.name) shouldBe
          Vector("startSplitPhase", "assessSplitProposal", "previewSplitPhase", "applySplitPhase")
        val protocol = Protocol(services = spec.ServiceDefinitionGroup(Vector(service)))
        val subsystem = RuntimeBindingAdmissionFixture.admit(Subsystem(
          name = "sm-workflow-split-phase-fixture-assembly", version = None,
          configuration = ResolvedConfiguration(GenericConfiguration.empty,
            ConfigurationTrace.empty),
          aliasResolver = AliasResolver.empty, runMode = RunMode.Command
        ))
        try {
          val component = new Component() {}
          component.initialize(ComponentInit(
            subsystem,
            Component.Core.create(SmWorkflowComponent.name, SmWorkflowComponent.componentId,
              ComponentInstanceId.default(SmWorkflowComponent.componentId), protocol),
            ComponentOrigin.Builtin
          ))
          subsystem.add(component)

          def execute(operation: String, wire: String): String = {
            val request = Request.of(
              component = SmWorkflowComponent.name, service = "SplitPhaseService",
              operation = operation, arguments = List(Argument("arg1", wire))
            )
            take(subsystem.executeOperationResponse(request)) match {
              case OperationResponse.Scalar(value: String) => value
              case other => fail(s"Expected SplitPhase JSON response, found $other")
            }
          }

          val cli = new CncfRuntime()
          def executeCli(operation: String, wire: String): String = {
            val selector = s"${SmWorkflowComponent.name}/SplitPhaseService/$operation"
            take(cli.executeCommandResponse(subsystem,
              Array(selector, "--json", wire))).print
          }

          val startWire = SplitPhaseStartServiceOperation.encodeCommand(fixture.startCommand)
          val startedWire = execute("startSplitPhase", startWire)
          executeCli("startSplitPhase", startWire) shouldBe startedWire
          val started = take(WorkflowWaitJsonV1.decodeC(startedWire))
          started.current shouldBe a[WorkflowContinuation.Wait]
          fixture.run.stored.map(_.revision) shouldBe Some(InstanceRevision(1L))

          val assessment = SplitPhaseProposalAssessmentCommand(
            fixture.startCommand, "1", "fixture-assembly-assessment-1")
          val assessmentWire =
            SplitPhaseProposalAssessmentServiceOperation.encodeCommand(assessment)
          val assessedWire = execute("assessSplitProposal", assessmentWire)
          executeCli("assessSplitProposal", assessmentWire) shouldBe assessedWire
          val assessed = take(WorkflowWaitJsonV1.decodeC(assessedWire))
          assessed.current shouldBe a[WorkflowContinuation.Wait]
          fixture.run.stored.map(_.revision) shouldBe Some(InstanceRevision(2L))

          val apply = SplitPhaseApplyCommand(fixture.startCommand, "2",
            "fixture-assembly-apply-1")
          val applyWire = SplitPhaseApplyServiceOperation.encodeCommand(apply)
          val terminalWire = execute("applySplitPhase", applyWire)
          val terminal = take(WorkflowTerminalJsonV1.decodeC(terminalWire,
            SplitPhaseApplyServiceOperation.terminalCodec))
          terminal.current shouldBe a[WorkflowContinuation.Terminal[?]]
          fixture.run.stored.map(_.revision) shouldBe Some(InstanceRevision(4L))
          execute("applySplitPhase", applyWire) shouldBe terminalWire

          val selector =
            s"${SmWorkflowComponent.name}/SplitPhaseService/applySplitPhase"
          val launcherWire = executeCli("applySplitPhase", applyWire)
          launcherWire shouldBe terminalWire
          take(WorkflowTerminalJsonV1.decodeC(launcherWire,
            SplitPhaseApplyServiceOperation.terminalCodec)).current shouldBe
            a[WorkflowContinuation.Terminal[?]]
          cli.executeCommandResponse(subsystem,
            Array(s"${SmWorkflowComponent.name}/SplitPhaseService/advanceWorkflow",
              "--json", applyWire)) shouldBe a[Consequence.Failure[?]]
          cli.executeCommandResponse(subsystem,
            Array(selector, "--json", "{")) shouldBe a[Consequence.Failure[?]]
        } finally subsystem.shutdown()
      }

    "leave documents unchanged if the APPLY intent append fails" in
      withCopiedFixture { root =>
        val fixture = new CatalogFixture(root, "APPLY")
        take(fixture.start())
        take(fixture.assess())
        val catalog = fixture.previewCatalog
        val catalogPort = new SplitPhasePreviewExecution.CatalogPort {
          def loadC(start: SplitPhaseStartCommand) = Consequence.success(catalog)
        }
        val apply = SplitPhaseApplyDocuments.Command(fixture.startCommand, "2", "apply-final-3")
        val source = root.resolve("docs/phase/phase-77.md")
        val original = Files.readAllBytes(source).toVector
        fixture.run.failAppendAtRevisionOnce = Some(2L)

        SplitPhaseApplyExecution.applyC(apply, root,
          fixture.run.selections, fixture.entry, fixture.proposals, catalogPort,
          definition, configuration, fixture.run.instances) shouldBe
          a[Consequence.Failure[?]]
        fixture.run.stored.map(_.revision) shouldBe Some(InstanceRevision(2L))
        Files.readAllBytes(source).toVector shouldBe original
        Files.exists(root.resolve("docs/phase/phase-77.1.md")) shouldBe false
        take(SplitPhaseApplyExecution.applyC(apply, root,
          fixture.run.selections, fixture.entry, fixture.proposals, catalogPort,
          definition, configuration, fixture.run.instances)) shouldBe
          a[SplitPhaseApplyExecution.Outcome.Completed]
      }

    "reject noncanonical or tampered persisted APPLY plan bytes" in
      withCopiedFixture { root =>
        val fixture = new CatalogFixture(root, "APPLY")
        take(fixture.start())
        take(fixture.assess())
        val plan = take(fixture.documentPlan(fixture.previewCatalog))
        val identity = fixture.run.stored.get.identity
        take(SplitPhaseApplyPlanStore.saveC(root, identity,
          fixture.startCommand, plan))
        val listed = Files.list(root.resolve(".codex-workflow/split-phase-apply-plans"))
        val files = try listed.iterator().asScala.toVector
          finally listed.close()
        files.size shouldBe 1
        val file = files.head
        val raw = Files.readString(file)
        Files.writeString(file, raw.replace(plan.proposalDigest, "0" * 64))

        SplitPhaseApplyPlanStore.loadC(root, identity, fixture.startCommand) shouldBe
          a[Consequence.Failure[?]]
      }

    "reject PREVIEW mode before loading an APPLY catalog or writing documents" in
      withCopiedFixture { root =>
        val fixture = new CatalogFixture(root)
        take(fixture.start())
        take(fixture.assess())
        val source = root.resolve("docs/phase/phase-77.md")
        val original = Files.readAllBytes(source).toVector
        var loads = 0
        val catalogPort = new SplitPhasePreviewExecution.CatalogPort {
          def loadC(start: SplitPhaseStartCommand) = {
            loads += 1
            Consequence.success(fixture.previewCatalog)
          }
        }

        SplitPhaseApplyDocuments.prepareC(
          SplitPhaseApplyDocuments.Command(fixture.startCommand, "2", "apply-2"),
          fixture.run.selections, fixture.entry, fixture.proposals, catalogPort,
          definition, configuration, fixture.run.instances) shouldBe
          a[Consequence.Failure[?]]
        loads shouldBe 0
        Files.readAllBytes(source).toVector shouldBe original
        Files.exists(root.resolve("docs/phase/phase-77.1.md")) shouldBe false
      }

    "install both copied Phase documents and make identical replay idempotent" in
      withCopiedFixture { root =>
        val fixture = new CatalogFixture(root)
        val plan = take(fixture.documentPlan(fixture.previewCatalog))
        val port = SplitPhaseDocumentFilePort(root)

        take(SplitPhaseDocumentCas.executeC(plan, port)) shouldBe
          SplitPhaseDocumentCas.Outcome.Applied
        plan.changes.foreach { change =>
          Files.readString(root.resolve(change.relativePath)) shouldBe change.desiredContent
        }
        take(SplitPhaseDocumentCas.executeC(plan, port)) shouldBe
          SplitPhaseDocumentCas.Outcome.AlreadyApplied
      }

    "reject an occupied child without replacing either document" in
      withCopiedFixture { root =>
        val fixture = new CatalogFixture(root)
        val plan = take(fixture.documentPlan(fixture.previewCatalog))
        val source = root.resolve(plan.changes.head.relativePath)
        val child = root.resolve(plan.changes(1).relativePath)
        val original = Files.readAllBytes(source).toVector
        Files.writeString(child, "# Other owner\n")

        take(SplitPhaseDocumentCas.executeC(plan, SplitPhaseDocumentFilePort(root))) shouldBe
          SplitPhaseDocumentCas.Outcome.Conflict(Vector(plan.changes(1).relativePath))
        Files.readAllBytes(source).toVector shouldBe original
        Files.readString(child) shouldBe "# Other owner\n"
      }

    "restore the source after a failure between the two installs" in
      withCopiedFixture { root =>
        val fixture = new CatalogFixture(root)
        val plan = take(fixture.documentPlan(fixture.previewCatalog))
        val source = root.resolve(plan.changes.head.relativePath)
        val child = root.resolve(plan.changes(1).relativePath)
        val original = Files.readAllBytes(source).toVector
        val port = SplitPhaseDocumentFilePort.withInstallProbe(root, index =>
          if (index == 1) throw new IllegalStateException("injected install failure"))

        SplitPhaseDocumentCas.executeC(plan, port) shouldBe a[Consequence.Failure[?]]
        Files.readAllBytes(source).toVector shouldBe original
        Files.exists(child) shouldBe false
        val listed = Files.list(root.resolve("docs/phase"))
        try listed.iterator().asScala.map(_.getFileName.toString).toVector
          .exists(_.startsWith(".split-phase-stage-")) shouldBe false
        finally listed.close()
      }

    "preserve a child claimed between comparison and installation" in
      withCopiedFixture { root =>
        val fixture = new CatalogFixture(root)
        val plan = take(fixture.documentPlan(fixture.previewCatalog))
        val source = root.resolve(plan.changes.head.relativePath)
        val child = root.resolve(plan.changes(1).relativePath)
        val original = Files.readAllBytes(source).toVector
        val port = SplitPhaseDocumentFilePort.withInstallProbe(root, index =>
          if (index == 1) Files.writeString(child, "# Concurrent child\n"))

        SplitPhaseDocumentCas.executeC(plan, port) shouldBe a[Consequence.Failure[?]]
        Files.readAllBytes(source).toVector shouldBe original
        Files.readString(child) shouldBe "# Concurrent child\n"
      }

    "reject a symlink child before any file install" in
      withCopiedFixture { root =>
        val fixture = new CatalogFixture(root)
        val plan = take(fixture.documentPlan(fixture.previewCatalog))
        val source = root.resolve(plan.changes.head.relativePath)
        val child = root.resolve(plan.changes(1).relativePath)
        val original = Files.readAllBytes(source).toVector
        Files.createSymbolicLink(child, source)

        SplitPhaseDocumentCas.executeC(plan, SplitPhaseDocumentFilePort(root)) shouldBe
          a[Consequence.Failure[?]]
        Files.readAllBytes(source).toVector shouldBe original
        Files.isSymbolicLink(child) shouldBe true
      }
  }
}
