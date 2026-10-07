package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseStartInput, SplitPhaseStartCommand, SplitPhaseProposalAssessmentCommand}

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseProposalCurrentCallSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"current-call fixture failed: $other")
  }
  private val definition = take(SplitPhaseDefinitionBinding.bindC)
  private val configuration = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("proposal-probe")),
    MigrationPolicy("v1"), RetentionPolicy("v1"), LeasePolicy("v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("proposal-probe")))
  private val start = SplitPhaseStartCommand(SplitPhaseStartInput("2", "PREVIEW", "source-authority", None, None, None),
    "selection", "participant", "start-key")
  private val command = SplitPhaseProposalAssessmentCommand(start, "1", "assessment-key")

  private final class Fixture(semanticBranch: Boolean) {
    var selected = Option.empty[ProfileStartAdmission.Selection]
    var selectionCalls = Vector.empty[ActionCall.Core]
    var entryCalls = Vector.empty[ActionCall.Core]
    var proposalCalls = Vector.empty[(String, ActionCall.Core)]
    var effects = 0
    private def forbidden[A]: Consequence[A] = { effects += 1; Consequence.stateConflict("no effects before source admission") }
    val selection = new ProfileStartAdmission.CallScopedSelectionPort {
      def loadForCallC(identity: String, core: ActionCall.Core) = { selectionCalls :+= core; Consequence.success(selected) }
    }
    val entry = new SplitPhaseEntryAssessment.CallScopedPort {
      def loadSourceForCallC(phase: String, authority: String, core: ActionCall.Core) = {
        entryCalls :+= core
        Consequence.success(Option.empty[SplitPhaseEntryAssessment.SourceSnapshot])
      }
      def loadProposalForCallC(identity: String, core: ActionCall.Core) = forbidden[Option[SplitPhaseEntryAssessment.ProposalCandidate]]
    }
    val proposal = new SplitPhaseProposalAssessment.CallScopedPort {
      private def observe[A](role: String, core: ActionCall.Core): Consequence[Option[A]] = {
        proposalCalls :+= role -> core
        Consequence.success(None)
      }
      def loadInventoryForCallC(identity: String, core: ActionCall.Core) = observe[SplitPhaseProposalPrecheck.SourceInventory]("inventory", core)
      def loadDraftForCallC(identity: String, core: ActionCall.Core) = observe[SplitPhaseProposalPrecheck.ProposalDraft]("draft", core)
      def loadValidationForCallC(identity: String, core: ActionCall.Core) = observe[SplitPhaseProposalValidation.Evidence]("validation", core)
      def loadBoundaryForCallC(identity: String, core: ActionCall.Core) = observe[SplitPhaseSourceBoundary.Contract]("boundary", core)
    }
    val instances = new WorkflowInstancePersistence {
      def create(configuration: Configuration, record: InstanceRecord) = forbidden[InstanceRecord]
      def load(configuration: Configuration, identity: InstanceIdentity) = forbidden[Option[InstanceRecord]]
      def append(configuration: Configuration, identity: InstanceIdentity, revision: InstanceRevision, entry: HistoryEntry) = forbidden[InstanceRecord]
    }
    val semantic = if (!semanticBranch) None else Some(take(SplitPhaseSemanticWorkflow.bindC(SplitPhaseSemanticWorkflow.Ports(
      selection, entry, proposal, definition, configuration, instances,
      new PersistentContinuationRuntime(new ContinuationRuntimePersistence.InMemory),
      new IssuedWorkOrderPersistence.InMemory[SplitPhaseSemanticInputs.Request],
      new SplitPhaseSemanticWorkflow.IntentPort {
        def saveC(id: org.goldenport.cncf.workflow.ContinuationIdentity, value: SplitPhaseSemanticWorkflow.Retained) = forbidden[SplitPhaseSemanticWorkflow.Retained]
        def loadC(id: org.goldenport.cncf.workflow.ContinuationIdentity) = forbidden[Option[SplitPhaseSemanticWorkflow.Retained]]
      }, new SplitPhaseSemanticWorkflow.CatalogPort {
        def loadC(start: SplitPhaseStartCommand, proposals: SplitPhaseProposalAssessment.Port, inputs: SplitPhaseSemanticInputs.Admitted) =
          forbidden[SplitPhasePreviewExecution.Catalog]
      }, ExecutionRequirement(Vector(CapabilityRequirement("assess-novel-split-capability")), RiskLevel("standard"), ReasoningLevel.Deep, true),
      MinimalPresentation("Split proposal", "Probe admission"), () => throw new AssertionError("no UoW before admission")))))
    val operation = take(SplitPhaseProposalAssessmentServiceOperation.bindC(selection, entry, proposal,
      definition, configuration, instances, semantic))
    val action = take(operation.createOperationRequest(Request.of(component = "sm-workflow", service = "SplitPhaseService",
      operation = "assessSplitProposal", arguments = List(Argument("arg1", SplitPhaseProposalAssessmentServiceOperation.encodeCommand(command))))))
      .asInstanceOf[CommandAction]
    def core(context: ExecutionContext) = ActionCall.Core(action, context, None, None)
    def admitTestSelection(): Unit = selected = Some(ProfileStartAdmission.Selection(start.selectionIdentity,
      StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase"), definition.workflowIdentity, definition.workflowRevision,
      start.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman, start.idempotencyKey, None))
  }

  "SplitPhase proposal current-call binding" should {
    for (semantic <- Vector(false, true)) {
      s"forward each current call to selection and entry without effects, semantic branch = $semantic" in {
        Given("one bound assessment Operation, test-only admitted selection and an absent source")
        val fixture = new Fixture(semantic)
        fixture.admitTestSelection()
        val cores = Vector(fixture.core(ExecutionContext.create()), fixture.core(ExecutionContext.create()))
        When("the Operation runs with two separate incoming call capsules")
        val results = cores.map(core => fixture.action.createCall(core).execute())
        Then("both read boundaries receive the exact current capsule and absent source prevents every later effect")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        fixture.selectionCalls.size shouldBe 2
        fixture.entryCalls.size shouldBe 2
        fixture.selectionCalls.zip(cores).foreach((actual, expected) => actual should be theSameInstanceAs expected)
        fixture.entryCalls.zip(cores).foreach((actual, expected) => actual should be theSameInstanceAs expected)
        fixture.proposalCalls shouldBe empty
        fixture.effects shouldBe 0
      }

      s"refuse absent consent and incomplete calls before proposal or runtime access, semantic branch = $semantic" in {
        Given("no host selection record and no downstream authority")
        val fixture = new Fixture(semantic)
        When("assessment executes once with a valid capsule and twice with incomplete capsules")
        val results = Vector(fixture.action.createCall(fixture.core(ExecutionContext.create())).execute(),
          fixture.action.createCall(null).execute(), fixture.action.createCall(fixture.core(null)).execute())
        Then("only the valid call reaches selection and no entry, proposal, persistence or semantic runtime runs")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        fixture.selectionCalls.size shouldBe 1
        fixture.entryCalls shouldBe empty
        fixture.proposalCalls shouldBe empty
        fixture.effects shouldBe 0
      }
    }

    "create exact call views for all four proposal purposes and retain context-free refusal" in {
      Given("one call-scoped proposal port and two independent incoming capsules")
      val fixture = new Fixture(false)
      val cores = Vector(fixture.core(ExecutionContext.create()), fixture.core(ExecutionContext.create()))
      When("each call view reads the four evidence purposes")
      cores.foreach { core =>
        val view = SplitPhaseProposalAssessment.forCall(fixture.proposal, core)
        Vector(view.loadInventoryC("snapshot"), view.loadDraftC("proposal"),
          view.loadValidationEvidenceC("proposal"), view.loadSourceBoundaryC("snapshot"))
          .foreach(_.toOption.get shouldBe None)
      }
      Then("every read retains its purpose and exact capsule while the original port remains unusable without dispatch")
      fixture.proposalCalls.map(_._1) shouldBe Vector.fill(2)(Vector("inventory", "draft", "validation", "boundary")).flatten
      fixture.proposalCalls.zip(cores.flatMap(core => Vector.fill(4)(core))).foreach { case ((_, actual), expected) =>
        actual should be theSameInstanceAs expected
      }
      Vector(fixture.proposal.loadInventoryC("snapshot"), fixture.proposal.loadDraftC("proposal"),
        fixture.proposal.loadValidationEvidenceC("proposal"), fixture.proposal.loadSourceBoundaryC("snapshot"))
        .foreach(_ shouldBe a[Consequence.Failure[?]])
      fixture.proposalCalls.size shouldBe 8
      fixture.effects shouldBe 0
    }
  }
}
