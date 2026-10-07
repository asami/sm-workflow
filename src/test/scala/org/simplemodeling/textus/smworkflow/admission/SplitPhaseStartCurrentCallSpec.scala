package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workflow.{StateMachineOperationIdentity, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.protocol.{Argument, Request}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseStartCommand, SplitPhaseStartInput}

/**
  * @since Oct.  2, 2026
  * @version Oct.  5, 2026
  */
final class SplitPhaseStartCurrentCallSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"current-call binding failed: $other")
  }
  private val command = SplitPhaseStartCommand(SplitPhaseStartInput("2", "PREVIEW", "source-authority",
    None, None, None), "selection", "participant", "start-key")

  private final class Probe(callScopedEntry: Boolean = false) {
    var observed = Vector.empty[(String, ActionCall.Core)]
    var record: Option[ProfileStartAdmission.Selection] = None
    var effects = 0
    var observedEntry = Vector.empty[ActionCall.Core]
    private def forbidden[A]: Consequence[A] = {
      effects += 1
      Consequence.stateConflict("selection refusal must precede source and persistence")
    }
    val selection = new ProfileStartAdmission.CallScopedSelectionPort {
      def loadForCallC(identity: String, core: ActionCall.Core) = {
        observed :+= identity -> core
        Consequence.success(record)
      }
    }
    private val entry = if (callScopedEntry) new SplitPhaseEntryAssessment.CallScopedPort {
      def loadSourceForCallC(phase: String, authority: String, core: ActionCall.Core) = {
        observedEntry :+= core
        Consequence.success(Option.empty[SplitPhaseEntryAssessment.SourceSnapshot])
      }
      def loadProposalForCallC(identity: String, core: ActionCall.Core) = {
        observedEntry :+= core
        forbidden[Option[SplitPhaseEntryAssessment.ProposalCandidate]]
      }
    } else new SplitPhaseEntryAssessment.Port {
      def loadSourceC(phase: String, authority: String) = forbidden[Option[SplitPhaseEntryAssessment.SourceSnapshot]]
      def loadProposalC(identity: String) = forbidden[Option[SplitPhaseEntryAssessment.ProposalCandidate]]
    }
    private val instances = new WorkflowInstancePersistence {
      def create(configuration: Configuration, record: InstanceRecord) = forbidden[InstanceRecord]
      def load(configuration: Configuration, identity: InstanceIdentity) = forbidden[Option[InstanceRecord]]
      def append(configuration: Configuration, identity: InstanceIdentity,
        revision: InstanceRevision, entry: HistoryEntry) = forbidden[InstanceRecord]
    }
    private val configuration = Configuration(
      NamedWorkflowStore("current-call-probe", WorkflowStoreIdentity("current-call-store")),
      MigrationPolicy("probe-v1"), RetentionPolicy("probe-v1"), LeasePolicy("probe-v1"),
      DeliveryConfiguration.SameStore(WorkflowStoreIdentity("current-call-store")))
    private val operation = take(SplitPhaseStartServiceOperation.bindC(selection, entry,
      take(SplitPhaseRuntimeCatalog.loadC()).definition, configuration, instances))
    val action: CommandAction = take(operation.createOperationRequest(Request.of(
      component = "sm-workflow", service = "SplitPhaseService", operation = "startSplitPhase",
      arguments = List(Argument("arg1", SplitPhaseStartServiceOperation.encodeCommand(command)))))) match {
      case value: CommandAction => value
      case other => fail(s"expected Start command action: $other")
    }
    def core(context: ExecutionContext): ActionCall.Core = ActionCall.Core(action, context, None, None)
  }

  "SplitPhase Start current-call selection" should {
    "forward the same current call to entry only after selection admission and stop for absent source" in {
      Given("a call-scoped entry probe and a test-only admitted selection, without any source snapshot")
      val probe = new Probe(callScopedEntry = true)
      probe.record = Some(ProfileStartAdmission.Selection(command.selectionIdentity,
        StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase"),
        WorkflowDefinitionIdentity("SplitPhaseWorkflow"), WorkflowDefinitionRevision("split-phase-selected-application-v12"),
        command.participantIdentity, ProfileStartAdmission.SelectionOrigin.ExplicitHuman, command.idempotencyKey, None))
      val cores = Vector(probe.core(ExecutionContext.create()), probe.core(ExecutionContext.create()))
      When("the bound Start action executes with two separate incoming capsules")
      val results = cores.map(core => probe.action.createCall(core).execute())
      Then("entry receives each exact capsule and missing source prevents instance or proposal effects")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      probe.observedEntry.size shouldBe 2
      probe.observedEntry.zip(cores).foreach { (observed, expected) =>
        observed should be theSameInstanceAs expected
      }
      probe.effects shouldBe 0
    }

    "forward each actual call capsule rather than retaining a binding-time context" in {
      Given("one bound Start action and a call-scoped read-only selection probe with no record")
      val probe = new Probe
      probe.observed shouldBe empty
      probe.selection.loadC(command.selectionIdentity) shouldBe a[Consequence.Failure[?]]
      probe.observed shouldBe empty
      val first = probe.core(ExecutionContext.create())
      val second = probe.core(ExecutionContext.create())
      first.executionContext should not be theSameInstanceAs(second.executionContext)
      When("the same action executes under two independently created call capsules")
      val results = Vector(probe.action.createCall(first).execute(), probe.action.createCall(second).execute())
      Then("each managed lookup receives its exact current capsule, while absent consent prevents all effects")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      probe.observed.map(_._1) shouldBe Vector(command.selectionIdentity, command.selectionIdentity)
      probe.observed(0)._2 should be theSameInstanceAs first
      probe.observed(1)._2 should be theSameInstanceAs second
      probe.effects shouldBe 0
    }

    "refuse missing call capsules before invoking any host port" in {
      Given("one bound Start action but no complete Operation execution context")
      val probe = new Probe
      When("execution is attempted with an absent core or absent context")
      val results = Vector(probe.action.createCall(null).execute(),
        probe.action.createCall(probe.core(null)).execute())
      Then("neither selection lookup nor source or persistence work occurs")
      results.foreach(_ shouldBe a[Consequence.Failure[?]])
      probe.observed shouldBe empty
      probe.effects shouldBe 0
    }

    "preserve rejection of non-human selection origins after current-call lookup" in {
      Given("exactly correlated records whose origins are recommendations or automated actions")
      val probe = new Probe
      val origins = Vector(ProfileStartAdmission.SelectionOrigin.Recommendation,
        ProfileStartAdmission.SelectionOrigin.Skill, ProfileStartAdmission.SelectionOrigin.Workflow,
        ProfileStartAdmission.SelectionOrigin.TerminalResult)
      When("each non-human record is returned by the current-call port")
      val cores = origins.map { origin =>
        probe.record = Some(ProfileStartAdmission.Selection(command.selectionIdentity,
          StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase"),
          WorkflowDefinitionIdentity("SplitPhaseWorkflow"), WorkflowDefinitionRevision("split-phase-selected-application-v12"),
          command.participantIdentity, origin, command.idempotencyKey, None))
        val core = probe.core(ExecutionContext.create())
        probe.action.createCall(core).execute() shouldBe a[Consequence.Failure[?]]
        core
      }
      Then("current-call access does not manufacture human authorization or start a Workflow")
      probe.observed.size shouldBe origins.size
      probe.observed.zip(cores).foreach { case ((identity, observed), expected) =>
        identity shouldBe command.selectionIdentity
        observed should be theSameInstanceAs expected
      }
      probe.effects shouldBe 0
    }
  }
}
