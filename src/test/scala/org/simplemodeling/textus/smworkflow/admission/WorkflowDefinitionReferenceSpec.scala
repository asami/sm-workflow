package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.file.{Files, Path}
import org.goldenport.cncf.workflow.{CandidateAdmissionProducerAbi, CandidateWorkflowAbi, WorkflowInstancePersistence}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import SplitPhaseExplicitPartitionFixture.take

/** Phase 5 declared-definition reference consumer examples.
  * @since Oct.  8, 2026
  * @version Oct.  8, 2026
  */
final class WorkflowDefinitionReferenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def json(path: String): Json =
    parse(Files.readString(Path.of(path))).fold(error => fail(error.toString), identity)

  private def bind(workflow: String, producer: String, revision: String,
    resource: String = "src/main/cozy/sm-workflow.cml"): DefinitionBinding =
    take(WorkflowInstancePersistence.bindCandidateDefinitionC(take(CandidateWorkflowAbi.parseSelectedC(
      workflow, take(CandidateAdmissionProducerAbi.parseC(producer)), resource,
      "SplitPhaseWorkflow", revision))))

  "E1 Declared reference readback" should {
    "accept relocated formatted definitions but reject changed record state" in {
      Given("Spec: docs/spec/workflow-definition-reference.md; Rule: R1/R2; Example: E1; actual generated ABI resources")
      val workflow = json("target/cozy/statemachine-workflow-abi.json")
      val producer = json("target/cozy/candidate-admission-producer-abi.json")
      val revision = "split-phase-selected-application-v12"
      val first = bind(workflow.noSpaces, producer.noSpaces, revision)
      val later = bind(workflow.noSpaces.replaceAll("\"line\":[0-9]+", "\"line\":999"),
        producer.spaces2, revision, "relocated/workflow.cml")
      val original = InstanceRecord(InstanceIdentity("reference-instance"), first, initialRevision, Lifecycle.NotStarted,
        None, CorrelationReference("reference-correlation"), CausationReference("reference-start"),
        None, None, None, Vector.empty)
      val loaded = original.copy(definition = later)

      When("native readback compares a rehydrated binding and its actual current state")
      val definitionMatches = SplitPhaseDefinitionBinding.sameDefinition(first, later)
      val recordMatches = SplitPhaseDefinitionBinding.sameRecord(original, loaded)
      val contradictionMatches = SplitPhaseDefinitionBinding.sameRecord(original,
        loaded.copy(causation = CausationReference("different-start")))

      Then("presentation is not authority and actual state contradictions remain rejected")
      (first eq later) shouldBe false
      first.sourceCorrelation should not be later.sourceCorrelation
      definitionMatches shouldBe true
      recordMatches shouldBe true
      contradictionMatches shouldBe false
      SplitPhaseDefinitionBinding.sameDefinition(null, later) shouldBe false
      SplitPhaseDefinitionBinding.sameRecord(original, null) shouldBe false
    }
  }

  "E2 Declared revision boundary" should {
    "retain an old admitted revision rather than migrating it to the current definition" in {
      Given("Spec: docs/spec/workflow-definition-reference.md; Rule: R2; Example: E2; actual resources with consistent old revision declarations")
      val workflow = json("target/cozy/statemachine-workflow-abi.json")
      val producer = json("target/cozy/candidate-admission-producer-abi.json")
      val wi = workflow.hcursor.downField("workflows").focus.get.asArray.get.indexWhere(
        _.hcursor.get[String]("identity").toOption.contains("SplitPhaseWorkflow"))
      val pi = producer.hcursor.downField("models").focus.get.asArray.get.indexWhere(
        _.hcursor.downField("model").downField("workflow").get[String]("identity").toOption.contains("SplitPhaseWorkflow"))
      wi should be >= 0
      pi should be >= 0
      val oldRevision = "split-phase-human-terminal-v8"
      val oldWorkflow = workflow.hcursor.downField("workflows").downN(wi).downField("version")
        .withFocus(_ => Json.fromString(oldRevision)).top.get.noSpaces
      val oldProducer = producer.hcursor.downField("models").downN(pi).downField("model").downField("workflow")
        .downField("version").withFocus(_ => Json.fromString(oldRevision)).top.get.noSpaces
      val old = bind(oldWorkflow, oldProducer, oldRevision)
      val current = take(SplitPhaseDefinitionBinding.bindC)

      When("the current native catalog encounters that separately admitted old reference")
      val accepted = SplitPhaseDefinitionBinding.sameDefinition(old, current)

      Then("a genuine revision difference is rejected without changing the saved version")
      old.workflowRevision.value shouldBe oldRevision
      current.workflowRevision.value shouldBe "split-phase-selected-application-v12"
      accepted shouldBe false
    }
  }

  "E4 Current and selected historical state" should {
    "resolve actual frontiers without requiring the complete preceding history to match a saved copy" in {
      Given("Spec: docs/spec/workflow-definition-reference.md; Rule: R4; Example: E4; a valid stored Workflow with three actual contiguous transitions")
      val definition = take(SplitPhaseDefinitionBinding.bindC)
      val initial = InstanceRecord(InstanceIdentity("state-reference"), definition, initialRevision, Lifecycle.NotStarted,
        None, CorrelationReference("owned-correlation"), CausationReference("owned-start"), None, None, None, Vector.empty)
      val first = take(initial.appendC(initial.revision, HistoryEntry(HistorySequence(1), initial.revision.next, Lifecycle.Active,
        Some(ProgressionReference("first")), initial.correlation, CausationReference("first-cause"), None, None, None)))
      val selected = take(first.appendC(first.revision, HistoryEntry(HistorySequence(2), first.revision.next, Lifecycle.Active,
        Some(ProgressionReference("selected")), initial.correlation, CausationReference("selected-cause"), None, None, None)))
      val current = take(selected.appendC(selected.revision, HistoryEntry(HistorySequence(3), selected.revision.next, Lifecycle.Active,
        Some(ProgressionReference("current")), initial.correlation, CausationReference("current-cause"), None, None, None)))
      val loaded = take(current.copy(history = current.history.updated(0,
        current.history.head.copy(causation = CausationReference("other-earlier-history-fact")))).validateC)

      When("readback resolves the current state or the actual selected historical revision")
      val currentmatches = SplitPhaseDefinitionBinding.sameRecord(current, loaded)
      val selectedmatches = SplitPhaseDefinitionBinding.includesState(loaded, selected)
      val crossed = loaded.copy(history = loaded.history.updated(1,
        loaded.history(1).copy(currentProgression = Some(ProgressionReference("another-selected-frontier")))))

      Then("an earlier-history body difference is no unchanged-content gate, while actual state/target contradictions still reject")
      loaded.history should not be current.history
      currentmatches shouldBe true
      selectedmatches shouldBe true
      SplitPhaseDefinitionBinding.includesState(crossed, selected) shouldBe false
      SplitPhaseDefinitionBinding.sameRecord(loaded, loaded.copy(revision = loaded.revision.next)) shouldBe false
      SplitPhaseDefinitionBinding.includesState(loaded, selected.copy(identity = InstanceIdentity("foreign-instance"))) shouldBe false
      SplitPhaseDefinitionBinding.includesState(selected, current) shouldBe false
      SplitPhaseDefinitionBinding.includesState(null, selected) shouldBe false
    }
  }
}
