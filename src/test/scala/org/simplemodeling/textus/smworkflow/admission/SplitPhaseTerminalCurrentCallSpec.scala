package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Files
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workflow.{StateMachineOperationIdentity, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.{Argument, Request}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseApplyCommand, SplitPhasePreviewCommand, SplitPhaseStartCommand, SplitPhaseStartInput}

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseTerminalCurrentCallSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case other => fail(s"terminal call fixture failed: $other")
  }
  private val definition = take(SplitPhaseDefinitionBinding.bindC)
  private val configuration = Configuration(NamedWorkflowStore("workflow", WorkflowStoreIdentity("terminal-probe")),
    MigrationPolicy("v1"), RetentionPolicy("v1"), LeasePolicy("v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("terminal-probe")))

  private final class Fixture(applyMode: Boolean) {
    val start = SplitPhaseStartCommand(SplitPhaseStartInput("2", if (applyMode) "APPLY" else "PREVIEW",
      "source-authority", None, None, None), "selection", "participant", "start-key")
    val root = Files.createTempDirectory("smwf-terminal-call-")
    var selected = Option.empty[ProfileStartAdmission.Selection]
    var selections = Vector.empty[ActionCall.Core]
    var sources = Vector.empty[ActionCall.Core]
    var catalogCalls = Vector.empty[(SplitPhaseStartCommand, ActionCall.Core)]
    var effects = 0
    private def forbidden[A]: Consequence[A] = {
      effects += 1
      Consequence.stateConflict("terminal effects must not run before admission")
    }
    val selection = new ProfileStartAdmission.CallScopedSelectionPort {
      def loadForCallC(identity: String, core: ActionCall.Core) = { selections :+= core; Consequence.success(selected) }
    }
    val entry = new SplitPhaseEntryAssessment.CallScopedPort {
      def loadSourceForCallC(phase: String, authority: String, core: ActionCall.Core) = {
        sources :+= core
        Consequence.success(Option.empty[SplitPhaseEntryAssessment.SourceSnapshot])
      }
      def loadProposalForCallC(identity: String, core: ActionCall.Core) = forbidden[Option[SplitPhaseEntryAssessment.ProposalCandidate]]
    }
    val proposal = new SplitPhaseProposalAssessment.CallScopedPort {
      def loadInventoryForCallC(identity: String, core: ActionCall.Core) = forbidden[Option[SplitPhaseProposalPrecheck.SourceInventory]]
      def loadDraftForCallC(identity: String, core: ActionCall.Core) = forbidden[Option[SplitPhaseProposalPrecheck.ProposalDraft]]
      def loadValidationForCallC(identity: String, core: ActionCall.Core) = forbidden[Option[SplitPhaseProposalValidation.Evidence]]
      def loadBoundaryForCallC(identity: String, core: ActionCall.Core) = forbidden[Option[SplitPhaseSourceBoundary.Contract]]
    }
    val catalog = new SplitPhasePreviewExecution.CallScopedCatalogPort {
      def loadForCallC(command: SplitPhaseStartCommand, core: ActionCall.Core) = {
        catalogCalls :+= command -> core
        Consequence.stateConflict("catalog probe has no admitted partition")
      }
    }
    val instances = new WorkflowInstancePersistence {
      def create(configuration: Configuration, record: InstanceRecord) = forbidden[InstanceRecord]
      def load(configuration: Configuration, identity: InstanceIdentity) = forbidden[Option[InstanceRecord]]
      def append(configuration: Configuration, identity: InstanceIdentity, revision: InstanceRevision, entry: HistoryEntry) = forbidden[InstanceRecord]
    }
    val operation = take(if (applyMode) SplitPhaseApplyServiceOperation.bindC(root, selection, entry, proposal,
      catalog, definition, configuration, instances)
      else SplitPhasePreviewServiceOperation.bindC(selection, entry, proposal, catalog, definition, configuration, instances))
    val name = if (applyMode) "applySplitPhase" else "previewSplitPhase"
    val wire = if (applyMode) SplitPhaseApplyServiceOperation.encodeCommand(SplitPhaseApplyCommand(start, "2", "terminal-key"))
      else SplitPhasePreviewServiceOperation.encodeCommand(SplitPhasePreviewCommand(start, "2", "terminal-key"))
    val action = take(operation.createOperationRequest(Request.of(component = "sm-workflow", service = "SplitPhaseService",
      operation = name, arguments = List(Argument("arg1", wire))))).asInstanceOf[CommandAction]
    def core(context: ExecutionContext) = ActionCall.Core(action, context, None, None)
    def assertEmptyRoot(): Unit = {
      val files = Files.list(root)
      try files.count() shouldBe 0L finally files.close()
    }
  }

  "SplitPhase terminal current-call binding" should {
    for (applyMode <- Vector(false, true)) {
      s"forward current selection calls and refuse missing consent without effects, APPLY = $applyMode" in {
        Given("a terminal Operation and call-scoped ports with no human selection")
        val fixture = new Fixture(applyMode)
        val cores = Vector(fixture.core(ExecutionContext.create()), fixture.core(ExecutionContext.create()))
        When("two distinct calls and two incomplete calls execute")
        val results = cores.map(core => fixture.action.createCall(core).execute()) ++
          Vector(fixture.action.createCall(null).execute(), fixture.action.createCall(fixture.core(null)).execute())
        Then("only the valid calls reach selection and no downstream work or document write occurs")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        fixture.selections.zip(cores).foreach((actual, expected) => actual should be theSameInstanceAs expected)
        fixture.selections.size shouldBe 2
        fixture.sources shouldBe empty
        fixture.catalogCalls shouldBe empty
        fixture.effects shouldBe 0
        fixture.assertEmptyRoot()
      }
    }

    "forward Preview's current source read after test-only selection without reaching persistence" in {
      Given("a test-only selection and a missing source, not a production consent issuer")
      val fixture = new Fixture(false)
      fixture.selected = Some(ProfileStartAdmission.Selection(fixture.start.selectionIdentity,
        StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase"), definition.workflowIdentity, definition.workflowRevision,
        fixture.start.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman, fixture.start.idempotencyKey, None))
      val cores = Vector(fixture.core(ExecutionContext.create()), fixture.core(ExecutionContext.create()))
      When("Preview receives both current capsules")
      val results = cores.map(core => fixture.action.createCall(core).execute())
      Then("source lookup receives each exact capsule and absence prevents all later effects")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      fixture.sources.size shouldBe 2
      fixture.sources.zip(cores).foreach((actual, expected) => actual should be theSameInstanceAs expected)
      fixture.catalogCalls shouldBe empty
      fixture.effects shouldBe 0
      fixture.assertEmptyRoot()
    }

    "retain exact catalog call views and refuse context-free managed reads" in {
      Given("a call-scoped catalog and separate incoming capsules")
      val fixture = new Fixture(false)
      val cores = Vector(fixture.core(ExecutionContext.create()), fixture.core(ExecutionContext.create()))
      When("each read-only catalog view is invoked at the original Start command")
      cores.foreach(core => SplitPhasePreviewExecution.forCall(fixture.catalog, core).loadC(fixture.start)
        shouldBe a[Consequence.Failure[?]])
      Then("the exact command and capsule reach the adapter but its context-free API remains denied")
      fixture.catalogCalls.size shouldBe 2
      fixture.catalogCalls.zip(cores).foreach { case ((command, actual), expected) =>
        command should be theSameInstanceAs fixture.start
        actual should be theSameInstanceAs expected
      }
      fixture.catalog.loadC(fixture.start) shouldBe a[Consequence.Failure[?]]
      fixture.catalogCalls.size shouldBe 2
      val plain = new SplitPhasePreviewExecution.CatalogPort {
        def loadC(command: SplitPhaseStartCommand) = Consequence.stateConflict("ordinary fixture")
      }
      SplitPhasePreviewExecution.forCall(plain, cores.head) should be theSameInstanceAs plain
      fixture.effects shouldBe 0
      fixture.assertEmptyRoot()
    }
  }
}
