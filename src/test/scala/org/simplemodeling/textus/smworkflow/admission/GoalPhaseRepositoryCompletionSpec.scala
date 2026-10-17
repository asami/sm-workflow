package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import java.nio.file.{Files, Path}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import GoalPhaseNativeRepositoryClosure as Closure
import GoalPhaseNativeRun as F
import SplitPhaseExplicitPartitionFixture.take

/** Specification-owned native records exercise admission, not managed execution.
  * Public Operation/SQLite episodes own persistence and no-replay evidence.
  * @since   Oct. 15, 2026
  * @version Oct. 15, 2026
  * @author  ASAMI, Tomoharu
  */
final class GoalPhaseRepositoryCompletionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val child = "selected-repository-child"
  private def initial: InstanceRecord = take(InstanceRecord(InstanceIdentity("selected-goal"),
    take(GoalPhaseRuntimeCatalog.loadC()).definition, initialRevision, Lifecycle.NotStarted, None,
    CorrelationReference("selected-correlation"), CausationReference("selected-start"),
    Some(DerivedCompositeOccurrenceReference("selected-occurrence")),
    Some(CommittedEntityTransitionReference("selected-predecessor")), None, Vector.empty).validateC)
  private def closing: InstanceRecord = {
    val start = initial
    take(start.appendC(start.revision, F.history(start, "phase.PhaseClosing", "selected-phase-review")))
  }
  private def saved: InstanceRecord = {
    val prior = closing
    take(prior.appendC(prior.revision, F.history(prior, "phase.Closed", "ordered-repository-closed:" + child)))
  }
  private def completion(record: InstanceRecord): HistoryEntry =
    F.history(record, "phase.Closed", "ordered-phase-completed:" + child)
      .copy(lifecycle = Lifecycle.Completed, currentProgression = None)
  private def otherRevision: DefinitionBinding = {
    val workflow = parse(Files.readString(Path.of("target/cozy/statemachine-workflow-abi.json"))).toOption.get
    val producer = parse(Files.readString(Path.of("target/cozy/candidate-admission-producer-abi.json"))).toOption.get
    val wi = workflow.hcursor.downField("workflows").focus.get.asArray.get.indexWhere(
      _.hcursor.get[String]("identity").toOption.contains("GoalPhaseWorkflow"))
    val pi = producer.hcursor.downField("models").focus.get.asArray.get.indexWhere(
      _.hcursor.downField("model").downField("workflow").get[String]("identity").toOption.contains("GoalPhaseWorkflow"))
    wi should be >= 0
    pi should be >= 0
    val revision = "specification-other-goal-revision"
    val changedWorkflow = workflow.hcursor.downField("workflows").downN(wi).downField("version")
      .withFocus(_ => Json.fromString(revision)).top.get.noSpaces
    val changedProducer = producer.hcursor.downField("models").downN(pi).downField("model").downField("workflow")
      .downField("version").withFocus(_ => Json.fromString(revision)).top.get.noSpaces
    val admitted = take(CandidateWorkflowAbi.parseSelectedC(changedWorkflow,
      take(CandidateAdmissionProducerAbi.parseC(changedProducer)), "src/main/cozy/sm-workflow.cml", "GoalPhaseWorkflow", revision))
    take(WorkflowInstancePersistence.bindCandidateDefinitionC(admitted))
  }

  "Selected native repository completion" should afterWord(
    "in spec:goal-phase-native-retention-applicability, example:E16, rule:R6") {
    "produce one native completion entry for the actual Active closed frontier" in {
      Given("a specification-owned Active phase.Closed record validated through the admitted GoalPhase catalog")
      val original = saved
      When("completion admission sees that same selected native frontier")
      val entry = take(Closure.completionEntryC(original, original, child)).get
      val completed = take(original.appendC(original.revision, entry))
      Then("normal native append accepts exactly the next Completed entry with its original causal linkage")
      entry shouldBe completion(original)
      completed.revision shouldBe original.revision.next
      completed.lifecycle shouldBe Lifecycle.Completed
      completed.currentProgression shouldBe None
      completed.suspension shouldBe None
      completed.history.last shouldBe entry
    }

    "admit the corresponding committed completion with a separately loaded definition" in {
      Given("the selected closure and its actual native append result, rebound to an independently admitted current catalog")
      val original = saved
      val binding = take(GoalPhaseRuntimeCatalog.loadC()).definition
      (binding eq original.definition) shouldBe false
      val current = take(take(original.appendC(original.revision, completion(original))).copy(definition = binding).validateC)
      When("completion admission rereads that committed native transition")
      val entry = take(Closure.completionEntryC(original, current, child))
      Then("there is no second append entry and diagnostic binding object identity is irrelevant")
      entry shouldBe None
      current.history.last shouldBe completion(original)
      current.revision shouldBe original.revision.next
    }

    "refuse another instance, workflow identity or workflow revision" in {
      Given("native-valid current records with a different owning instance or independently admitted workflow binding")
      val original = saved
      val completed = take(original.appendC(original.revision, completion(original)))
      val foreign = Vector(completed.copy(identity = InstanceIdentity("another-goal")),
        completed.copy(definition = take(RepositorySyncDefinitionBinding.bindC)),
        completed.copy(definition = otherRevision)).map(record => take(record.validateC))
      When("those records are offered as this saved Goal closure's current completion")
      val admitted = foreign.map(record => Closure.completionEntryC(original, record, child))
      Then("each different native owner or workflow revision fails without proposing an append")
      admitted.foreach(_.isSuccess shouldBe false)
    }

    "refuse other native revisions, selected last entries and completion linkage" in {
      Given("native-valid frontiers before or beyond the selected completion and altered completion linkage")
      val original = saved
      val expected = completion(original)
      val extra = take(original.appendC(original.revision, F.history(original, "phase.Closed", "extra-closure")))
      val later = take(extra.appendC(extra.revision, completion(extra)))
      val altered = Vector(expected.copy(causation = CausationReference("another-completion")),
        expected.copy(correlation = CorrelationReference("another-correlation")),
        expected.copy(derivedCompositeOccurrence = Some(DerivedCompositeOccurrenceReference("another-occurrence"))),
        expected.copy(committedPredecessor = Some(CommittedEntityTransitionReference("another-predecessor"))))
        .map(entry => take(original.appendC(original.revision, entry)))
      val prior = closing
      val differentLast = take(prior.appendC(prior.revision, original.history.last.copy(causation = CausationReference("another-closure"))))
      val progression = take(prior.appendC(prior.revision, original.history.last.copy(currentProgression = Some(ProgressionReference("phase.PhaseClosing")))))
      val suspended = take(prior.appendC(prior.revision, original.history.last.copy(suspension = Some(SuspensionBoundary(
        WorkflowInstancePersistence.ContinuationIdentity("other-slot"), original.revision, ContextSnapshotReference("other-context"),
        CompletionReference("other-completion"), EvidenceReference("other-evidence"), CorrelationReference("other-resume"))))))
      When("admission compares the actual selected last native transition and completion frontier")
      val rejected = (Vector(prior, extra, later, differentLast, progression, suspended) ++ altered)
        .map(record => Closure.completionEntryC(original, take(record.validateC), child))
      Then("wrong revisions, progression, suspension, completion cause or predecessor facts cannot substitute for the owning transition")
      rejected.foreach(_.isSuccess shouldBe false)
    }

    "refuse missing or malformed consumed records and child sessions" in {
      Given("the selected closure alongside missing native records, malformed current facts and absent child sessions")
      val original = saved
      val completed = take(original.appendC(original.revision, completion(original)))
      When("completion admission consumes those invalid boundary inputs")
      val rejected = Vector(Closure.completionEntryC(null, completed, child),
        Closure.completionEntryC(original, null, child), Closure.completionEntryC(initial, completed, child),
        Closure.completionEntryC(original.copy(history = null), completed, child),
        Closure.completionEntryC(original, completed.copy(revision = InstanceRevision(99)), child),
        Closure.completionEntryC(original, completed.copy(currentProgression = Some(ProgressionReference("phase.Closed"))), child),
        Closure.completionEntryC(original, completed.copy(suspension = Some(SuspensionBoundary(
          WorkflowInstancePersistence.ContinuationIdentity("slot"), completed.revision, ContextSnapshotReference("context"),
          CompletionReference("completion"), EvidenceReference("evidence"), CorrelationReference("resume")))), child)) ++
        Vector(null, "", " \n").map(session => Closure.completionEntryC(original, completed, session))
      Then("native validation and consumed-session admission fail without any synthetic completion")
      rejected.foreach(_.isSuccess shouldBe false)
    }
  }
}
