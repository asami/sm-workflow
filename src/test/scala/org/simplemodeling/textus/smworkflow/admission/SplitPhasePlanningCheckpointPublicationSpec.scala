package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.workflow.WorkflowInstanceRecordJsonV1
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.configuration.{Configuration as HostConfiguration, ConfigurationTrace, ConfigurationValue, ResolvedConfiguration}
import org.goldenport.record.Record as StorageRecord
import org.simplemodeling.model.datatype.EntityCollectionId
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhasePlanningCheckpointPreparation as K,
  SplitPhasePlanningCheckpointPublication as U, SplitPhasePlanningCheckpointJsonV1 as J,
  SplitPhasePlanningSnapshot as R, SplitPhasePlanningSnapshotJsonV1 as RJ,
  SplitPhaseExplicitPlanningInputJsonV1 as F, SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseExplicitBoundaryValidation as B, SplitPhaseExplicitPartitionSemantic as S}
import SplitPhaseExplicitPartitionFixture.*
import SplitPhasePlanningCheckpointFixture.*

/**
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhasePlanningCheckpointPublicationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def collection(name: String) = DataStore.CollectionId.EntityStore(EntityCollectionId("test", "checkpoint", name))
  private val declaration = SplitPhaseManagedStartAssembly.Declaration(f.configuration, collection("instances"), collection("continuations"))
  private val ip = SplitPhasePlanningInputReadPolicy.prefix
  private val cp = SplitPhasePartitionContextReadPolicy.prefix
  private val sp = SplitPhaseHostSelectionReadPolicy.prefix
  private val tp = SplitPhaseTypedStartReadPolicy.prefix
  private val ep = SplitPhasePartitionEnrichmentStorage.prefix
  private val values = Map[String, ConfigurationValue](
    ip + "store-name" -> ConfigurationValue.StringValue("workflow"),
    cp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    cp + "record-collection" -> ConfigurationValue.StringValue(collection("contexts").print),
    sp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    sp + "selection-collection" -> ConfigurationValue.StringValue(collection("selections").print),
    tp + "store-name" -> ConfigurationValue.StringValue("workflow"),
    tp + "record-collection" -> ConfigurationValue.StringValue(collection("starts").print),
    ep + "store-name" -> ConfigurationValue.StringValue("workflow"),
    ep + "record-collection" -> ConfigurationValue.StringValue(collection("publications").print)) ++
    F.Purpose.values.map(p => (ip + p.key) -> ConfigurationValue.StringValue(collection(p.toString.toLowerCase).print))
  private def host(v: Map[String, ConfigurationValue]) = ResolvedConfiguration(HostConfiguration(v), ConfigurationTrace.empty)
  private def targets = take(U.bindTargetsC(host(values), declaration))
  private def built(saved: K.Captured) = saved.record.snapshot.projection.toVector.flatMap(_.candidates)
    .collect { case b: R.CandidateProjectionView.BuiltView => b }
  private def verifyDocuments(saved: K.Captured): Unit = {
    val prepared = take(U.prepareC(saved, targets)); val documents = built(saved)
    documents.nonEmpty shouldBe true
    documents.foreach { document =>
      def bytes(target: DataStore.CollectionId, identity: String) = prepared.rows.find(r => r.collection == target && r.identity == identity)
        .get.payload.getString(F.storageField).get
      take(F.decodeProposalC(document.proposal.reference, bytes(targets.proposals, document.proposal.reference.identity))) shouldBe document.proposal
      take(F.decodeOwnershipC(document.ownership.reference, bytes(targets.ownerships, document.ownership.reference.identity))) shouldBe document.ownership
      take(F.decodeHandoffsC(document.handoffs.reference, bytes(targets.handoffs, document.handoffs.reference.identity))) shouldBe document.handoffs
    }
    prepared.rows.count(_.collection == targets.proposals) shouldBe documents.size
    prepared.rows.count(_.collection == targets.ownerships) shouldBe documents.size
  }

  "Passive complete publication preflight" should {
    "prepare all Built documents including mixed scope and repository requirement frontiers" in {
      Given("real all-known and mixed frontiers rather than a fabricated winner")
      val s = base(Vector.fill(4)(180)); val e = enumerate(s)
      val grouped = proof(s, e.candidates.find(_.cutAfterIndices == Vector(1)).get, "grouped", handoffPairs = Vector(0 -> 1))
      val local = proof(s, e.candidates.find(_.cutAfterIndices == Vector(0, 1, 2)).get, "local", handoffPairs = Vector(0 -> 1))
      val mixed = s.initial.records.copy(ownership = s.initial.records.ownership.copy(children =
        s.initial.records.ownership.children.zipWithIndex.map((c, i) => c.copy(repository = B.RepositoryIdentity(if (i < 2) "repo-A" else "repo-B")))))
      When("every actual Built member is converted to three declared document rows")
      val captures = Vector(capture(), forBoundary(s, grouped), forBoundary(s, take(B.loadC(s.initial.shape, mixed, mixed))),
        forBoundary(s, local))
      Then("requirements do not discard usable candidate documents or local handoffs")
      captures.foreach(verifyDocuments)
      captures.tail.take(2).foreach(v => v.record.snapshot.projection.get shouldBe a[R.ProjectionView.Requirements])
      captures.foreach(v => take(J.decodeC(v.record.reference, take(J.encodeC(v.record)))).snapshot shouldBe v.record.snapshot)
      captures.last.record.snapshot.projection.get.candidates.collect { case b: R.CandidateProjectionView.BuiltView => b }
        .flatMap(_.localHandoffs).nonEmpty shouldBe true
    }

    "reuse unchanged planning data and distinguish actual AI completion publication effects" in {
      Given("one allocation saved in the original planning context and an actual semantic completion")
      val selected = bound(); val (allocation, _) = allocated(selected)
      val retained = bound(knownFacts.copy(planning = allocation.data))
      val unchanged = take(K.captureUnchangedC(retained, take(W.advanceC(retained.context.selection)), reference, anchor))
      val semantic = take(K.captureCompletionC(completion(), reference)).get
      When("the full descriptors and anticipated native envelopes are prepared")
      val mechanical = take(U.prepareC(unchanged, targets)); val ai = take(U.prepareC(semantic, targets))
      Then("retained planning is not recreated and AI has no fake mechanical receipt")
      mechanical.rows.exists(_.collection == targets.planning) shouldBe false
      ai.rows.count(_.collection == targets.estimates) shouldBe 1
      ai.rows.count(_.collection == targets.planning) shouldBe 1
      ai.rows.count(_.collection == targets.handoffs) shouldBe built(semantic).size + 1
      mechanical.rows.count(_.collection == targets.publication) shouldBe 2
      ai.rows.count(_.collection == targets.publication) shouldBe 2
      ai.captured.mechanicalIntent shouldBe None
      ai.anticipatedNativeBytes should not be mechanical.anticipatedNativeBytes
    }

    "bind distinct declared same-store purposes and reject missing or aliased metadata" in {
      Given("all fifteen explicit collection purposes and one store declaration")
      When("publication metadata is incomplete aliased or crosses the declared store")
      val aliases = F.Purpose.values.toVector.map(p => collection(p.toString.toLowerCase)) ++
        Vector(collection("contexts"), collection("selections"), collection("starts"), declaration.instances, declaration.continuations)
      Then("no passive target set supplies a storage capability or fallback")
      targets.storeName shouldBe "workflow"
      values.keys.foreach(key => U.bindTargetsC(host(values.removed(key)), declaration).isSuccess shouldBe false)
      aliases.foreach(c => U.bindTargetsC(host(values.updated(ep + "record-collection", ConfigurationValue.StringValue(c.print))), declaration)
        .isSuccess shouldBe false)
      U.bindTargetsC(host(values.updated(ep + "store-name", ConfigurationValue.StringValue("other"))), declaration).isSuccess shouldBe false
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningCheckpointPublication.Targets(null, null, null, null, null, null, null, null, null)")
      assertDoesNotCompile("new org.simplemodeling.textus.smworkflow.admission.SplitPhasePlanningCheckpointPublication.Prepared(null, null, Vector.empty, null)")
    }

    "derive injective Unicode delimiter locators and bound the final prefixed physical key" in {
      Given("query tuples containing delimiters and non-ASCII identities")
      val first = take(U.indexKeyC(InstanceIdentity("a/@語"), S.ContextReference("b", "1")))
      val second = take(U.indexKeyC(InstanceIdentity("a"), S.ContextReference("/@語b", "1")))
      When("canonical tuples and invalid names are encoded")
      Then("distinct tuples cannot alias and final UTF-8 bytes must fit")
      first should not be second
      first should startWith ("planning-context:")
      take(U.mappingKeyC(reference)) should startWith ("planning-snapshot:")
      Vector(" padded ", "\n", "語" * 600, "\ud800").foreach(id => U.indexKeyC(InstanceIdentity(id), S.ContextReference("b", "1")).isSuccess shouldBe false)
      U.mappingKeyC(reference.copy(identity = "語" * 600)).isSuccess shouldBe false
      U.indexKeyC(null, null).isSuccess shouldBe false
    }

    "check exactly 64 versus 65 complete passive descriptors including closing effects and byte limits" in {
      Given("duplicate-free descriptor vectors for capacity shape only, not private native completions")
      val t = targets; val saved = capture(); val frame = take(J.encodeIndexC(saved.index))
      def rows(count: Int) = Vector.tabulate(count)(i => U.Row(t.publication, s"capacity-$i", StorageRecord.data(J.indexStorageField -> frame)))
      val baseline = take(U.prepareC(saved, t))
      When("count duplication reserved targets and UTF-8 row bounds are preflighted")
      Then("overflow refuses the whole set without staging or truncation")
      U.validateRowsC(rows(64), t).isSuccess shouldBe true
      U.validateRowsC(rows(65), t).isSuccess shouldBe false
      U.prepareC(saved, t, rows(64 - baseline.rows.size)).isSuccess shouldBe true
      U.prepareC(saved, t, rows(65 - baseline.rows.size)).isSuccess shouldBe false
      U.validateRowsC(Vector(rows(1).head, rows(1).head), t).isSuccess shouldBe false
      U.validateRowsC(Vector(rows(1).head.copy(collection = t.instances)), t).isSuccess shouldBe false
      U.validateRowsC(Vector(rows(1).head.copy(payload = StorageRecord.data(J.indexStorageField -> ("語" * (J.maximumPayloadBytes / 2))))), t).isSuccess shouldBe false
      U.prepareC(null, t).isSuccess shouldBe false
    }

    "refuse an oversized whole native receipt even when snapshot and individual histories fit" in {
      Given("actual prepared history with large causation, never persisted or acknowledged")
      val later = take(f.instance.appendC(f.instance.revision, f.instance.history.last.copy(
        sequence = HistorySequence(2), revision = InstanceRevision(2), causation = CausationReference("x" * 530000))))
      val saved = capture(bound(current = later))
      When("the anticipated native receipt includes both histories plus complete mapping")
      Then("whole-envelope capacity refuses before any store execution")
      RJ.encodeC(saved.record.snapshot).isSuccess shouldBe true
      WorkflowInstanceRecordJsonV1.encodeC(saved.before).isSuccess shouldBe true
      WorkflowInstanceRecordJsonV1.encodeC(saved.next).isSuccess shouldBe true
      U.prepareC(saved, targets).isSuccess shouldBe false
    }
  }
}
