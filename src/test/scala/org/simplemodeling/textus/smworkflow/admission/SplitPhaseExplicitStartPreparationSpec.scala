package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.StateMachineOperationIdentity
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import io.circe.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.value.{SplitPhaseStartCommand, SplitPhaseStartInput}

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseExplicitStartPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import SplitPhaseExplicitStartPreparation.*
  import ProfileStartAdmission.*
  import SplitPhaseEntryAssessment.*

  private def take[A](value: Consequence[A]): A = value match {
    case Consequence.Success(result) => result
    case failure => fail(s"explicit Start preparation rejected: $failure")
  }
  private val definition = take(SplitPhaseRuntimeCatalog.loadC()).definition
  private val configuration = Configuration(
    NamedWorkflowStore("workflow-primary", WorkflowStoreIdentity("workflow-store")),
    MigrationPolicy("fixture-v1"), RetentionPolicy("fixture-v1"), LeasePolicy("fixture-v1"),
    DeliveryConfiguration.SameStore(WorkflowStoreIdentity("workflow-store")))
  private val command = SplitPhaseStartCommand(SplitPhaseStartInput("77", "PREVIEW", "authority-1",
    Some("snapshot-1"), None, Some("proposal-1")), "selection-1", "participant-1", "start-1")
  private val selection = Selection("selection-1", StateMachineOperationIdentity("SplitPhaseService", "startSplitPhase"),
    definition.workflowIdentity, definition.workflowRevision, "participant-1", SelectionOrigin.ExplicitHuman,
    "start-1", None)
  private val source = SourceSnapshot("snapshot-1", "77", "authority-1", "source-revision-4", "a" * 64)
  private val proposal = ProposalCandidate("proposal-1", "77", "authority-1", source.digest, "b" * 64)
  private val record = Record(Reference("start-record-1", "record-revision-7"), InstanceIdentity("instance-42"),
    definition.workflowIdentity, definition.workflowRevision, configuration, command, selection,
    Assessment(source, Some(proposal)))

  private final class Readers {
    var currentSelection: Option[Selection] = Some(selection)
    var currentSource: Option[SourceSnapshot] = Some(source)
    var currentProposal: Option[ProposalCandidate] = Some(proposal)
    var selectionReads = 0
    val selections = new SelectionPort {
      def loadC(identity: String) = {
        selectionReads += 1
        Consequence.success(currentSelection.filter(_.selectionId == identity))
      }
    }
    val entry = new Port {
      def loadSourceC(phase: String, authority: String) = Consequence.success(currentSource)
      def loadProposalC(identity: String) = Consequence.success(currentProposal)
    }
    def prepare(frozen: Record = record, input: SplitPhaseStartCommand = command,
      config: Configuration = configuration, binding: DefinitionBinding = definition) =
      prepareC(input, frozen, selections, entry, binding, config)
  }

  "SplitPhase explicit Start preparation" should {
    "retain exact host references and produce no progression or implicit allocation" in {
      Given("complete immutable fixture data, not an actual human ingress or persisted allocation")
      val readers = new Readers
      When("the explicit record is checked against current selection and entry evidence")
      val prepared = take(readers.prepare())
      Then("identity and causation remain the explicit references, not transformations of the idempotency key")
      prepared.record shouldBe record
      prepared.record.reference.revision shouldBe "record-revision-7"
      prepared.initial.identity shouldBe InstanceIdentity("instance-42")
      prepared.initial.causation shouldBe CausationReference("start-record-1")
      prepared.initial.correlation shouldBe CorrelationReference("selection-1")
      prepared.initial.revision shouldBe initialRevision
      prepared.initial.lifecycle shouldBe Lifecycle.NotStarted
      prepared.initial.history shouldBe empty
      prepared.initial.suspension shouldBe None
      prepared.request.input.value shouldBe command.input
      readers.selectionReads shouldBe 1
    }

    "repeat exact preparation from independent readers without choosing a newer reference" in {
      Given("independent current-evidence readers for the same explicit Start record")
      val readers = new Readers
      When("the first reader prepares that exact record")
      val first = take(readers.prepare())
      When("an independent reader repeats the exact preparation")
      val second = take(new Readers().prepare())
      Then("the complete record, request and unstarted allocation are identical")
      second.record shouldBe first.record
      second.request shouldBe first.request
      second.initial shouldBe first.initial
      Given("a separately supplied record reference and instance allocation")
      val differentExplicit = record.copy(reference = Reference("other-record", "2"),
        instanceIdentity = InstanceIdentity("other-instance"))
      When("that different explicit allocation is prepared")
      val other = take(new Readers().prepare(differentExplicit))
      Then("its exact identity and causation are preserved without selecting a newer reference")
      other.initial.identity shouldBe differentExplicit.instanceIdentity
      other.initial.causation shouldBe CausationReference("other-record")
      // Pure preparation does not admit a second allocation for the same key;
      // a canonical registry must enforce that boundary before any Create.
    }

    "reject every changed original command field instead of digest-only matching" in {
      Given("command variants changing each original selected field")
      val inputs = Vector(command.copy(selectionIdentity = "other"), command.copy(participantIdentity = "other"),
        command.copy(idempotencyKey = "other"), command.copy(input = command.input.copy(invocationMode = "APPLY")),
        command.copy(input = command.input.copy(sourcePhaseIdentity = "other")),
        command.copy(input = command.input.copy(sourceAuthorityReference = "other")),
        command.copy(input = command.input.copy(sourceSnapshotReference = None)),
        command.copy(input = command.input.copy(originGoalPhaseTerminalResultReference = Some("other"))),
        command.copy(input = command.input.copy(proposalReference = None)))
      inputs.foreach { value =>
        When("a changed command is compared with the complete original record")
        val result = new Readers().prepare(input = value)
        Then("the command mismatch is refused rather than digest-matched")
        result shouldBe a[Consequence.Failure[?]]
      }
    }

    "compare the whole observed selection including recommendation and selected input" in {
      Vector(selection.copy(recommendationReference = Some("new-recommendation")),
        selection.copy(selectedInput = Some(TypedValue("SplitPhaseStartInput", Json.obj("changed" -> Json.True)))),
        selection.copy(participantIdentity = "other"), selection.copy(idempotencyKey = "other"))
        .foreach { changed =>
          Given("a current selection with changed recommendation, input, participant or key")
          val readers = new Readers
          readers.currentSelection = Some(changed)
          When("the whole observed selection is compared with the retained selection")
          val result = readers.prepare()
          Then("the full-record drift is refused")
          result shouldBe a[Consequence.Failure[?]]
        }
    }

    "re-admit origin and generated operation even when frozen and observed selection agree" in {
      Given("matching frozen/current selections with nonhuman origin or foreign operation or revision")
      val invalid = SelectionOrigin.values.toVector.filterNot(_ == SelectionOrigin.ExplicitHuman)
        .map(origin => selection.copy(origin = origin)) ++ Vector(
          selection.copy(startOperation = StateMachineOperationIdentity("SplitPhaseService", "other")),
          selection.copy(workflowRevision = WorkflowDefinitionRevision("other")))
      invalid.foreach { changed =>
        val readers = new Readers
        readers.currentSelection = Some(changed)
        When("preparation re-admits the otherwise equal selection contract")
        val result = readers.prepare(record.copy(selection = changed))
        Then("equality cannot authorize the invalid origin or generated operation")
        result shouldBe a[Consequence.Failure[?]]
      }
      Given("a reader with no current selection")
      val missing = new Readers
      missing.currentSelection = None
      When("the original record is prepared against that missing selection")
      val result = missing.prepare()
      Then("an absent selection is refused")
      result shouldBe a[Consequence.Failure[?]]
    }

    "reject source and proposal drift even when their recorded digests are unchanged" in {
      Given("the original reader with a same-digest source whose revision has changed")
      val readers = new Readers
      readers.currentSource = Some(source.copy(revision = "source-revision-5"))
      When("the complete source is re-admitted")
      val changedsource = readers.prepare()
      Then("source revision drift is refused")
      changedsource shouldBe a[Consequence.Failure[?]]
      Given("the original source restored but a proposal with a foreign authority")
      readers.currentSource = Some(source)
      readers.currentProposal = Some(proposal.copy(authorityReference = "other"))
      When("the complete proposal is re-admitted")
      val changedproposal = readers.prepare()
      Then("proposal authority drift is refused")
      changedproposal shouldBe a[Consequence.Failure[?]]
      Given("the selected proposal is absent from the current reader")
      readers.currentProposal = None
      When("preparation requires that originally selected proposal")
      val missingproposal = readers.prepare()
      Then("the absent selected proposal is refused")
      missingproposal shouldBe a[Consequence.Failure[?]]
      Given("the original proposal restored but no current source")
      readers.currentProposal = Some(proposal)
      readers.currentSource = None
      When("preparation re-admits the current source")
      val missingsource = readers.prepare()
      Then("the absent source is refused")
      missingsource shouldBe a[Consequence.Failure[?]]
    }

    "refuse foreign Workflow and storage declarations before current evidence is read" in {
      Vector(record.copy(workflowIdentity = WorkflowDefinitionIdentity("other")),
        record.copy(workflowRevision = WorkflowDefinitionRevision("other")),
        record.copy(configuration = configuration.copy(workflowStore =
          NamedWorkflowStore("other", WorkflowStoreIdentity("other")))))
        .foreach { frozen =>
          Given("an explicit record with foreign Workflow identity, revision or store configuration")
          val readers = new Readers
          When("preparation validates the record's owning declarations")
          val result = readers.prepare(frozen)
          Then("the foreign declaration is refused before current selection evidence is read")
          result shouldBe a[Consequence.Failure[?]]
          readers.selectionReads shouldBe 0
        }
    }

    "reject malformed explicit references and absent dependencies without legacy fallback" in {
      Vector(record.copy(reference = null), record.copy(reference = Reference("", "1")),
        record.copy(reference = Reference(" padded ", "1")), record.copy(reference = Reference("r", " ")),
        record.copy(instanceIdentity = null), record.copy(instanceIdentity = InstanceIdentity(" ")),
        record.copy(selection = null), record.copy(assessment = null)).foreach { frozen =>
          Given("a current reader and an explicit record with malformed or absent required data")
          val current = new Readers
          When("the malformed explicit record is prepared")
          val result = current.prepare(frozen)
          Then("the incomplete data is refused without implicit allocation")
          result shouldBe a[Consequence.Failure[?]]
        }
      Given("ordinary current-evidence ports for dependency-refusal checks")
      val readers = new Readers
      When("the original command is absent")
      val missingcommand = prepareC(null, record, readers.selections, readers.entry, definition, configuration)
      Then("preparation refuses the missing command")
      missingcommand shouldBe a[Consequence.Failure[?]]
      When("the original record is absent")
      val missingrecord = prepareC(command, null, readers.selections, readers.entry, definition, configuration)
      Then("preparation refuses the missing record")
      missingrecord shouldBe a[Consequence.Failure[?]]
      When("the current selection port is absent")
      val missingselection = prepareC(command, record, null, readers.entry, definition, configuration)
      Then("preparation refuses the missing selection dependency")
      missingselection shouldBe a[Consequence.Failure[?]]
      When("the current entry port is absent")
      val missingentry = prepareC(command, record, readers.selections, null, definition, configuration)
      Then("preparation refuses the missing entry dependency without legacy fallback")
      missingentry shouldBe a[Consequence.Failure[?]]
    }
  }
}
