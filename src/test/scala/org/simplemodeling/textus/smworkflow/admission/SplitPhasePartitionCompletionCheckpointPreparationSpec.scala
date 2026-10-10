package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePartitionCompletionCheckpointPreparation as A,
  SplitPhasePlanningCheckpointPublication as U, SplitPhasePartitionCompletionCheckpointPublication as AU,
  SplitPhaseExplicitPartitionSemantic as S, SplitPhaseTypedPartitionCompletion as P,
  SplitPhaseExplicitPartitionContext as C, SplitPhaseTypedStartPreparation as T,
  SplitPhaseExplicitPlanningAdvance as W, SplitPhaseExplicitPlanningInputJsonV1 as F}
import SplitPhaseExplicitPartitionFixture.take

/** Privately constructed fresh typed Start/AI result, not public human ingress
  * and not a native commit witness. Shared only by inert executable examples.
  * @since   Oct.  2, 2026
  * @version Oct. 11, 2026
  */
private[admission] object SplitPhaseAICompletionCheckpointFixture {
  val f = SplitPhaseTypedStartFixture
  def plan(decision: S.Decision = S.Decision.Enriched, facts: C.Facts = f.facts): P.Plan = {
    val current = SplitPhaseObservedCheckpointFixture.current
    val context = f.context.copy(facts = facts)
    val partition = take(C.prepareC(context, facts, current, f.configuration, f.catalog))
    val issued = SplitPhasePartitionReplyFixture.issue(take(T.bindPartitionC(f.prepared, partition)))
    val work = issued.interaction.current.asInstanceOf[WorkflowContinuation.WorkOrder[S.Request]].request
    val input = work.input.get.value
    val judgment = S.Judgment(decision, "semantic-only",
      if (decision == S.Decision.Enriched) input.items.map(i => S.NovelEstimate(i.identity, 120, 180, "bounded-work", "novel-closure"))
        else Vector.empty,
      if (decision == S.Decision.Enriched) input.boundaries.map(b => S.BoundaryJudgment(b.edge.afterItem,
        SplitPhasePartitionEnumeration.EdgeDisposition.Either, "independent-closure")) else Vector.empty,
      Vector(S.EvidenceReference("actual-ai-result-evidence", "1")))
    val result = ContinuationResult(issued.interaction.handle, work.runId, work.continuationId, work.expectedRevision,
      work.context.snapshot, TypedValue(SplitPhaseExplicitPartitionSemanticJson.resultCodec.typeIdentity, judgment),
      ContextReference(input.reply.result.identity, input.reply.result.revision), Vector.empty,
      ExecutionEvidence(Vector(ContextReference("actual-ai-result-evidence", "1")), Some("participant")))
    val pending = take(P.pendingC(context, facts, f.prepared, issued.projected, issued.interaction, f.configuration, f.catalog))
    val initial = take(P.admitC(pending, result))
    initial.mechanical match {
      case Some(need: W.AllocationRequired) => take(P.continueAllocatedC(initial,
        take(SplitPhaseCandidateAllocationFixture.policy.allocateC(need, input.reply.result))))
      case _ => initial
    }
  }
  def capture(decision: S.Decision = S.Decision.Enriched): A.Captured = take(A.captureC(plan(decision)))
  def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "aicompletion", name))
  val declaration = SplitPhaseManagedStartAssembly.Declaration(f.configuration, collection("instances"), collection("continuations"))
  private val _ip = SplitPhasePlanningInputReadPolicy.prefix
  private val _cp = SplitPhasePartitionContextReadPolicy.prefix
  private val _sp = SplitPhaseHostSelectionReadPolicy.prefix
  private val _tp = SplitPhaseTypedStartReadPolicy.prefix
  private val _ep = SplitPhasePartitionEnrichmentStorage.prefix
  val host = ResolvedConfiguration(HostConfiguration(Map[String, ConfigurationValue](
    _ip + "store-name" -> ConfigurationValue.StringValue("workflow"), _cp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    _cp + "record-collection" -> ConfigurationValue.StringValue(collection("contexts").print),
    _sp + "store-name" -> ConfigurationValue.StringValue("workflow"), _sp + "selection-collection" -> ConfigurationValue.StringValue(collection("selections").print),
    _tp + "store-name" -> ConfigurationValue.StringValue("workflow"), _tp + "record-collection" -> ConfigurationValue.StringValue(collection("starts").print),
    _ep + "store-name" -> ConfigurationValue.StringValue("workflow"), _ep + "record-collection" -> ConfigurationValue.StringValue(collection("publications").print)) ++
    F.Purpose.values.map(p => (_ip + p.key) -> ConfigurationValue.StringValue(collection(p.toString.toLowerCase).print))), ConfigurationTrace.empty)
  def targets = take(U.bindTargetsC(host, declaration))
}

final class SplitPhasePartitionCompletionCheckpointPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import SplitPhaseAICompletionCheckpointFixture.*
  "Original AI-completion capture" should {
    "copy the actual computed outcome and whole native lineage without another append" in {
      Given("a constructed fresh unknown-only AI completion continued through its actual allocation")
      val selected = plan()
      When("the original closing selection is captured and one complete Create set is prepared")
      val captured = take(A.captureC(selected))
      val rows = take(AU.prepareRowsC(captured, targets))
      Then("original result/evidence and before/next survive and every prepared physical target appears once")
      captured.record.completion.result shouldBe selected.submitted
      captured.record.before shouldBe selected.pending.current
      captured.record.next shouldBe selected.next
      captured.record.next.history.dropRight(1) shouldBe captured.record.before.history
      captured.checkpoint.get.mechanicalIntent shouldBe None
      captured.checkpoint.get.record.snapshot.selection.planning shouldBe selected.mechanical.get.selection.data
      rows.map(r => (r.collection, r.identity)).distinct.size shouldBe rows.size
      rows.count(_.collection == targets.publication) shouldBe 4
      val documents = SplitPhasePlanningCheckpointObservation.builtDocuments(captured.record.checkpoint.get.snapshot)
      documents should not be empty
      rows.count(_.collection == targets.proposals) shouldBe documents.size
      rows.count(_.collection == targets.ownerships) shouldBe documents.size
      rows.count(_.collection == targets.handoffs) shouldBe documents.size + 1
    }
    "capture the entire mixed candidate frontier after the original unknown-only AI result" in {
      Given("original ownership separating the last item into another repository while partition work is still unknown")
      val original = f.facts
      val ownership = original.boundary.ownership.copy(children = original.boundary.ownership.children.zipWithIndex.map {
        (child, i) => child.copy(repository = SplitPhaseExplicitBoundaryValidation.RepositoryIdentity(
          if (i < 2) "repo-A" else "repo-B"))
      })
      val facts = original.copy(boundary = original.boundary.copy(ownership = ownership))
      val selected = plan(facts = facts)
      val reached = selected.mechanical.get.asInstanceOf[W.ProjectionRequired]
      val actual = reached.trace.projection.get.asInstanceOf[SplitPhaseExplicitCandidateProjection.Requirements]
      When("that computed completion is captured, encoded and prepared without filtering candidates")
      val captured = take(A.captureC(selected))
      val saved = captured.record.checkpoint.get.snapshot
      val frontier = saved.projection.get.asInstanceOf[SplitPhasePlanningSnapshot.ProjectionView.Requirements]
      val rows = take(AU.prepareRowsC(captured, targets))
      val encoded = take(SplitPhasePartitionCompletionCheckpointJsonV1.encodeC(captured.record))
      val decoded = take(SplitPhasePartitionCompletionCheckpointJsonV1.decodeC(selected.submitted.continuationId,
        encoded, Vector(f.catalog.definition)))
      Then("Built documents and every repository Decision survive in their original order with the original AI evidence")
      frontier.candidates.exists(_.isInstanceOf[SplitPhasePlanningSnapshot.CandidateProjectionView.BuiltView]) shouldBe true
      frontier.candidates.exists(_.isInstanceOf[SplitPhasePlanningSnapshot.CandidateProjectionView.DecisionView]) shouldBe true
      frontier.candidates.map(_.key.cutAfterIndices) shouldBe actual.candidates.map(_.candidate.cutAfterIndices)
      frontier.scopes shouldBe reached.scopes
      frontier.decisionKeys.map(_.cutAfterIndices) shouldBe reached.decisions.map(_.candidate.cutAfterIndices)
      val documents = SplitPhasePlanningCheckpointObservation.builtDocuments(saved)
      val built = actual.candidates.collect { case b: SplitPhaseExplicitCandidateProjection.Built => b }
      documents.map(_.proposal) shouldBe built.map(_.boundary.shape.records.proposal)
      documents.map(_.ownership) shouldBe built.map(_.boundary.records.ownership)
      documents.map(_.handoffs) shouldBe built.map(_.boundary.records.handoffs)
      frontier.candidates.collect { case d: SplitPhasePlanningSnapshot.CandidateProjectionView.DecisionView => d.repositories } shouldBe
        actual.candidates.collect { case d: SplitPhaseExplicitCandidateProjection.DecisionRequired => d.repositories }
      rows.count(_.collection == targets.proposals) shouldBe documents.size
      rows.count(_.collection == targets.ownerships) shouldBe documents.size
      decoded shouldBe captured.record
      decoded.completion.result shouldBe selected.submitted
      decoded.checkpoint.get.snapshot.projection shouldBe saved.projection
    }
    "retain DecisionNeeded completion without inventing planning or human ingress" in {
      Given("an actual admitted AI result requiring human direction")
      val captured = capture(S.Decision.DecisionNeeded)
      When("the original closing lineage is prepared")
      val rows = take(AU.prepareRowsC(captured, targets))
      Then("only one lineage row exists and raw data cannot construct execution/observation capabilities")
      captured.record.checkpoint shouldBe None
      captured.record.header shouldBe None
      captured.checkpoint shouldBe None
      rows.size shouldBe 1
      rows.head.identity shouldBe take(A.lineageKeyC(captured.record.completion.result.continuationId))
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionCompletionCheckpointPreparation.Captured(null, None)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhaseManagedTypedPartitionCompletion.ObservedCompletion(null, null)")
    }
    "serialize inert facts without readmitting a completion and bound physical lineage keys" in {
      Given("stored data variants that cannot construct a selected Plan or a Captured publication")
      val saved = capture().record
      val identity = ContinuationIdentity("実例:[]\"\\")
      val variants = Vector(saved.copy(checkpoint = None), saved.copy(header = None),
        saved.copy(before = saved.next), saved.copy(wire = "unused stored response diagnostic"),
        saved.copy(start = saved.start.copy(instanceIdentity = WorkflowInstancePersistence.InstanceIdentity("foreign"))),
        saved.copy(checkpoint = saved.checkpoint.map(k => k.copy(reference = k.reference.copy(identity = "caller-selected")))))
      When("the existing frame codec preserves typed data without repeating native admission")
      val decoded = variants.map(v => take(SplitPhasePartitionCompletionCheckpointJsonV1.decodeC(
        v.completion.result.continuationId, take(SplitPhasePartitionCompletionCheckpointJsonV1.encodeC(v)),
        Vector(f.catalog.definition))))
      val keys = Vector(A.lineageKeyC(identity), A.lineageKeyC(identity))
      val invalidkeys = Vector(A.lineageKeyC(ContinuationIdentity("x" * 1024)), A.lineageKeyC(null))
      val absentplan = A.captureC(null)
      Then("the values remain inert records while physical address bounds and the actual Plan input still apply")
      decoded shouldBe variants
      keys.map(take(_)).distinct.size shouldBe 1
      invalidkeys.foreach(_.isSuccess shouldBe false)
      absentplan.isSuccess shouldBe false
    }
  }
}
