package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.{SplitPhaseScopeProjectionSnapshot as V,
  SplitPhaseExplicitCandidateProjection as Q, SplitPhaseExplicitPlanningAdvance as W,
  SplitPhaseExplicitPartitionComputation as E, SplitPhasePlanningCheckpointPreparation as K,
  SplitPhaseScopeSemantic as S, SplitPhaseExplicitPartitionSemantic as T,
  SplitPhaseExplicitBoundaryValidation as B}
import SplitPhaseExplicitPartitionFixture.*

/** Passive successor examples use actual constructed pure scope inputs only.
  *
  * @since Oct.  4, 2026
  * @version Oct.  4, 2026
  */
final class SplitPhaseScopeProjectionSnapshotSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private def _fixture(): (W.ProjectionRequired, K.Record, S.Request) = {
    val source = base(Vector.fill(4)(180))
    val candidate = enumerate(source).candidates.find(_.cutAfterIndices == Vector(1)).get
    val template = proof(source, candidate, "successor-template", handoffPairs = Vector(0 -> 1))
    val checkpoint = SplitPhasePlanningCheckpointFixture.forBoundary(source, template).record
    val facts = checkpoint.snapshot.selection
    val selection = take(W.selectC(take(E.loadC(template, facts.partition, facts.partition)), facts.planning, facts.planning))
    val need = take(W.advanceC(selection)).asInstanceOf[W.ProjectionRequired]
    (need, checkpoint, take(S.requestC(checkpoint)))
  }
  private def _reply(request: S.Request, broad: Boolean): S.Admitted = take(S.admitC(request,
    ContextReference(request.result.identity, request.result.revision), S.Judgment(request.context, request.checkpoint,
      S.Decision.Enriched, "original transfer analysis", request.gaps.map(gap => S.Scope(gap.original.identity,
        if (broad) gap.producingItems else gap.producingItems.takeRight(1),
        if (broad) gap.receivingItems else gap.receivingItems.take(1), "necessary items")),
      Vector(T.EvidenceReference("successor-evidence", "1")))))
  private def _narrow(): V.Record = {
    val (need, checkpoint, request) = _fixture()
    take(V.projectC(take(Q.applyScopeC(need, checkpoint, _reply(request, false)))))
  }

  "Scope projection successor data" should {
    "whole-frontier copying" which {
      "retain original root and every Built body with explicit effective local and cross-child transfers" in {
        Given("an actual narrow scope calculation over mixed Built and semantic candidates")
        val (need, checkpoint, request) = _fixture()
        val reply = _reply(request, false)
        val applied = take(Q.applyScopeC(need, checkpoint, reply)).asInstanceOf[Q.ScopeApplied]
        When("the complete calculation is copied to passive successor data")
        val record = take(V.projectC(applied))
        val candidates = record.frontier.asInstanceOf[V.Frontier.Applied].candidates
        Then("no original candidate document or root is replaced and local/cross owner facts remain explicit")
        record.first shouldBe checkpoint
        record.result shouldBe request.result
        record.judgment shouldBe reply.judgment
        candidates.map(_.key.cutAfterIndices) shouldBe applied.candidates.map(_.original.candidate.cutAfterIndices)
        candidates.map(_.transfers) shouldBe applied.candidates.map(_.transfers)
        candidates.foreach(_.output shouldBe a[V.CandidateOutput.Built])
        candidates.collect { case V.Candidate(_, _, V.CandidateOutput.Built(_, true)) => 1 }.size shouldBe
          applied.candidates.count(_.asInstanceOf[Q.ScopeBuilt].documentsAlreadyPublished)
        candidates.exists(_.transfers.exists(t => t.producingOwners == t.receivingOwners)) shouldBe true
        candidates.exists(_.transfers.exists(t => t.producingOwners != t.receivingOwners)) shouldBe true
        take(V.inspectC(record)) shouldBe ()
      }
      "retain known multi-owner needs distinctly from another semantic request" in {
        Given("the assigned enriched result contains necessary ranges spanning several cached candidate owners")
        val (need, checkpoint, request) = _fixture()
        val applied = take(Q.applyScopeC(need, checkpoint, _reply(request, true)))
        When("the complete held result is copied without selecting an easy Built candidate")
        val record = take(V.projectC(applied))
        val candidates = record.frontier.asInstanceOf[V.Frontier.Applied].candidates
        Then("the whole first frontier stays and exact known representability gaps require Human handling")
        record.first shouldBe checkpoint
        candidates.size shouldBe need.trace.projection.get.asInstanceOf[Q.Requirements].candidates.size
        candidates.exists(_.output.isInstanceOf[V.CandidateOutput.KnownUnrepresentable]) shouldBe true
        candidates.exists(_.output.isInstanceOf[V.CandidateOutput.Built]) shouldBe true
        take(V.inspectC(record)) shouldBe ()
      }
      "retain DecisionNeeded without fabricating candidate enrichment or Human approval" in {
        Given("the actual assigned semantic result requests Human direction without scopes")
        val (need, checkpoint, request) = _fixture()
        val reply = take(S.admitC(request, ContextReference(request.result.identity, request.result.revision),
          S.Judgment(request.context, request.checkpoint, S.Decision.DecisionNeeded, "human interpretation needed",
            Vector.empty, Vector(T.EvidenceReference("direction-evidence", "1")))))
        When("that genuine pure outcome is copied")
        val record = take(V.projectC(take(Q.applyScopeC(need, checkpoint, reply))))
        Then("the complete immutable first frontier and result remain with no invented enriched candidates")
        record.first shouldBe checkpoint
        record.frontier shouldBe V.Frontier.HumanDirectionRequired
        record.judgment shouldBe reply.judgment
        take(V.inspectC(record)) shouldBe ()
      }
      "preserve repository Decisions throughout the entire successor frontier" in {
        Given("original candidate requirements contain both semantic ranges and repository Decisions")
        val source = base(Vector.fill(4)(180))
        val candidate = enumerate(source).candidates.find(_.cutAfterIndices == Vector(1)).get
        val template = proof(source, candidate, "decision-successor", handoffPairs = Vector(0 -> 1))
        val boundaries = template.records.copy(ownership = template.records.ownership.copy(children =
          template.records.ownership.children.zipWithIndex.map((child, index) => child.copy(repository = B.RepositoryIdentity(s"repo-$index")))))
        val boundary = take(B.loadC(template.shape, boundaries, boundaries))
        val checkpoint = SplitPhasePlanningCheckpointFixture.forBoundary(source, boundary).record
        val facts = checkpoint.snapshot.selection
        val selection = take(W.selectC(take(E.loadC(boundary, facts.partition, facts.partition)), facts.planning, facts.planning))
        val need = take(W.advanceC(selection)).asInstanceOf[W.ProjectionRequired]
        val request = take(S.requestC(checkpoint))
        val outcome = take(Q.applyScopeC(need, checkpoint, _reply(request, false)))
        When("the held complete calculation is copied without eliminating competing Decisions")
        val record = take(V.projectC(outcome))
        val candidates = record.frontier.asInstanceOf[V.Frontier.Applied].candidates
        val decisions = checkpoint.snapshot.projection.get.candidates.collect {
          case value: SplitPhasePlanningSnapshot.CandidateProjectionView.DecisionView => value
        }
        Then("every original Decision body and candidate remains while shape inspection rejects its replacement")
        decisions.nonEmpty shouldBe true
        decisions.foreach(value => candidates.find(_.key == value.key).get.output shouldBe V.CandidateOutput.Decision(value))
        candidates.map(_.key) shouldBe checkpoint.snapshot.projection.get.candidates.map(_.key)
        take(V.inspectC(record)) shouldBe ()
        When("a caller replaces a retained repository Decision with an empty Human gap")
        val index = candidates.indexWhere(_.key == decisions.head.key)
        val replaced = candidates.updated(index, candidates(index).copy(output = V.CandidateOutput.KnownUnrepresentable(Vector.empty)))
        val inspection = V.inspectC(record.copy(frontier = V.Frontier.Applied(replaced)))
        Then("the replacement is rejected rather than normalized into a different decision")
        inspection shouldBe a[Consequence.Failure[?]]
      }
      "preserve newly exposed numbering Decisions without inventing another phase identity" in {
        Given("known scope ranges permit assembly but an original occupied suffix prevents some new documents")
        val source = base(Vector.fill(6)(120))
        val candidate = enumerate(source).candidates.find(_.cutAfterIndices == Vector(1, 3)).get
        val template = proof(source, candidate, "numbering-successor", handoffPairs = Vector(0 -> 1))
        val shapes = template.shape.records.copy(inventory = template.shape.records.inventory.copy(occupiedPhases =
          Set(SplitPhaseExplicitProposalPrecheck.PhaseIdentity("77"), SplitPhaseExplicitProposalPrecheck.PhaseIdentity("77.3"))))
        val shape = take(SplitPhaseExplicitProposalPrecheck.loadC(shapes, shapes))
          .asInstanceOf[SplitPhaseExplicitProposalPrecheck.ShapeReady]
        val boundary = take(B.loadC(shape, template.records, template.records))
        val checkpoint = SplitPhasePlanningCheckpointFixture.forBoundary(source, boundary).record
        val facts = checkpoint.snapshot.selection
        val selection = take(W.selectC(take(E.loadC(boundary, facts.partition, facts.partition)), facts.planning, facts.planning))
        val need = take(W.advanceC(selection)).asInstanceOf[W.ProjectionRequired]
        val request = take(S.requestC(checkpoint))
        When("the calculated held frontier is copied with its existing numbering records")
        val record = take(V.projectC(take(Q.applyScopeC(need, checkpoint, _reply(request, false)))))
        val candidates = record.frontier.asInstanceOf[V.Frontier.Applied].candidates
        Then("both retained and newly exposed numbering Decisions remain exact and no successor suffix is allocated")
        candidates.collect { case V.Candidate(_, _, V.CandidateOutput.Decision(value)) => value }.nonEmpty shouldBe true
        candidates.map(_.key) shouldBe checkpoint.snapshot.projection.get.candidates.map(_.key)
        candidates.collect { case V.Candidate(_, _, V.CandidateOutput.Decision(value)) => value }.foreach { value =>
          value.reason shouldBe Q.DecisionReason.PhaseNumberingRequired
          value.numbering.nonEmpty shouldBe true
        }
        take(V.inspectC(record)) shouldBe ()
      }
    }
    "passive relationship checks" which {
      "reject unrelated repositories and extra admissible dependencies in new Built ownership" in {
        Given("a genuine scope successor with a new Built candidate having at least three children")
        val source = base(Vector.fill(6)(120))
        val template = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(1, 3)).get,
          "ownership-successor", handoffPairs = Vector(0 -> 1))
        val checkpoint = SplitPhasePlanningCheckpointFixture.forBoundary(source, template).record
        val facts = checkpoint.snapshot.selection
        val need = take(W.advanceC(take(W.selectC(take(E.loadC(template, facts.partition, facts.partition)),
          facts.planning, facts.planning)))).asInstanceOf[W.ProjectionRequired]
        val record = take(V.projectC(take(Q.applyScopeC(need, checkpoint, _reply(take(S.requestC(checkpoint)), false)))))
        val candidates = record.frontier.asInstanceOf[V.Frontier.Applied].candidates
        val index = candidates.indexWhere(_.output match {
          case V.CandidateOutput.Built(documents, false) => documents.proposal.children.size >= 3
          case _ => false
        })
        val V.CandidateOutput.Built(documents, _) = candidates(index).output: @unchecked
        val owners = documents.ownership.children
        val changes = Vector(documents.ownership.copy(children = owners.map(_.copy(repository = B.RepositoryIdentity("unrelated")))),
          documents.ownership.copy(children = owners.updated(owners.size - 1,
            owners.last.copy(dependencies = Vector(owners.head.phase)))))
        val shapes = SplitPhaseExplicitProposalPrecheck.Records(facts.proposal.source, facts.proposal.inventory, documents.proposal)
        val shape = take(SplitPhaseExplicitProposalPrecheck.loadC(shapes, shapes))
          .asInstanceOf[SplitPhaseExplicitProposalPrecheck.ShapeReady]
        changes.foreach { ownership =>
          val boundary = B.Records(facts.boundary.boundary, ownership, documents.handoffs)
          take(B.loadC(shape, boundary, boundary))
        }
        When("those leaf-admissible ownership documents replace the calculated candidate")
        val inspected = changes.map(ownership => V.inspectC(record.copy(frontier = V.Frontier.Applied(
          candidates.updated(index, candidates(index).copy(output = V.CandidateOutput.Built(documents.copy(ownership = ownership), false)))))))
        Then("original repository assignments and exact inherited dependencies cannot be substituted")
        inspected.foreach(_ shouldBe a[Consequence.Failure[?]])
        take(V.inspectC(record)) shouldBe ()
      }
      "reject numbering substitution for known multi-owner scope gaps" in {
        Given("the same original candidate has occupied numbering and broad multi-owner ranges")
        val source = base(Vector.fill(6)(120))
        val template = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(1, 3)).get,
          "gap-precedence-successor", handoffPairs = Vector(0 -> 1))
        val shapes = template.shape.records.copy(inventory = template.shape.records.inventory.copy(occupiedPhases =
          Set(SplitPhaseExplicitProposalPrecheck.PhaseIdentity("77"), SplitPhaseExplicitProposalPrecheck.PhaseIdentity("77.3"))))
        val shape = take(SplitPhaseExplicitProposalPrecheck.loadC(shapes, shapes))
          .asInstanceOf[SplitPhaseExplicitProposalPrecheck.ShapeReady]
        val boundary = take(B.loadC(shape, template.records, template.records))
        val checkpoint = SplitPhasePlanningCheckpointFixture.forBoundary(source, boundary).record
        val facts = checkpoint.snapshot.selection
        val need = take(W.advanceC(take(W.selectC(take(E.loadC(boundary, facts.partition, facts.partition)),
          facts.planning, facts.planning)))).asInstanceOf[W.ProjectionRequired]
        val request = take(S.requestC(checkpoint))
        val broad = take(V.projectC(take(Q.applyScopeC(need, checkpoint, _reply(request, true)))))
        val narrow = take(V.projectC(take(Q.applyScopeC(need, checkpoint, _reply(request, false)))))
        val candidates = broad.frontier.asInstanceOf[V.Frontier.Applied].candidates
        val key = SplitPhasePlanningSnapshot.CandidateKey(Vector(0, 2, 3))
        val index = candidates.indexWhere(_.key == key)
        val decision = narrow.frontier.asInstanceOf[V.Frontier.Applied].candidates.find(_.key == key).get.output
        candidates(index).output shouldBe a[V.CandidateOutput.KnownUnrepresentable]
        decision shouldBe a[V.CandidateOutput.Decision]
        When("a numbering Decision valid only for narrow ranges replaces the known broad Human need")
        val inspected = V.inspectC(broad.copy(frontier = V.Frontier.Applied(
          candidates.updated(index, candidates(index).copy(output = decision)))))
        Then("the broad result retains Human precedence and refuses numbering as a substitute")
        inspected shouldBe a[Consequence.Failure[?]]
        take(V.inspectC(broad)) shouldBe ()
      }
      "reject matching malformed nested retained Built bodies in both first and successor" in {
        Given("a complete successor with one retained Built document")
        val record = _narrow()
        val candidates = record.frontier.asInstanceOf[V.Frontier.Applied].candidates
        val index = candidates.indexWhere(_.output match {
          case V.CandidateOutput.Built(_, true) => true
          case _ => false
        })
        val V.CandidateOutput.Built(documents, _) = candidates(index).output: @unchecked
        val projection = record.first.snapshot.projection.get
          .asInstanceOf[SplitPhasePlanningSnapshot.ProjectionView.Requirements]
        val changes = Vector(documents.copy(localHandoffs = Vector(null)),
          documents.copy(handoffs = documents.handoffs.copy(handoffs = null)),
          documents.copy(ownership = documents.ownership.copy(children = Vector(null))),
          documents.copy(handoffs = documents.handoffs.copy(incomingByChild = null)))
        When("the same malformed nested body is placed in the immutable-root copy and retained successor")
        val inspected = changes.map { changed =>
          val first = record.first.copy(snapshot = record.first.snapshot.copy(projection = Some(projection.copy(
            candidates = projection.candidates.map(c => if (c.key == documents.key) changed else c)))))
          Vector(V.inspectC(record.copy(first = first, frontier = V.Frontier.Applied(candidates.updated(index,
            candidates(index).copy(output = V.CandidateOutput.Built(changed, true)))))),
            V.inspectC(record.copy(first = first, judgment = record.judgment.copy(
              decision = S.Decision.DecisionNeeded, scopes = Vector.empty), frontier = V.Frontier.HumanDirectionRequired)))
        }
        Then("matching malformed copies reject in Applied and Human frontiers rather than admitted as complete data")
        inspected.flatten.foreach(_ shouldBe a[Consequence.Failure[?]])
        take(V.inspectC(record)) shouldBe ()
      }
      "reject malformed local originals even when the first comparison data matches them" in {
        Given("a genuine Human successor with a retained local Built and other semantic candidates")
        val source = base(Vector.fill(6)(120))
        val template = proof(source, enumerate(source).candidates.find(_.cutAfterIndices == Vector(1, 3)).get,
          "local-original-successor", handoffPairs = Vector(0 -> 1))
        val checkpoint = SplitPhasePlanningCheckpointFixture.forBoundary(source, template).record
        val facts = checkpoint.snapshot.selection
        val need = take(W.advanceC(take(W.selectC(take(E.loadC(template, facts.partition, facts.partition)),
          facts.planning, facts.planning)))).asInstanceOf[W.ProjectionRequired]
        val request = take(S.requestC(checkpoint))
        val reply = take(S.admitC(request, ContextReference(request.result.identity, request.result.revision),
          S.Judgment(request.context, request.checkpoint, S.Decision.DecisionNeeded, "human interpretation needed",
            Vector.empty, Vector(T.EvidenceReference("local-original-evidence", "1")))))
        val record = take(V.projectC(take(Q.applyScopeC(need, checkpoint, reply))))
        val projection = record.first.snapshot.projection.get
          .asInstanceOf[SplitPhasePlanningSnapshot.ProjectionView.Requirements]
        val original = facts.boundary.handoffs.handoffs.head
        When("an invalid original scalar is copied consistently into comparison facts and local and semantic bodies")
        val malformed = original.copy(kind = null)
        val boundary = facts.boundary.copy(handoffs = facts.boundary.handoffs.copy(handoffs =
          facts.boundary.handoffs.handoffs.map(h => if (h == original) malformed else h)))
        val candidates = projection.candidates.map {
          case built: SplitPhasePlanningSnapshot.CandidateProjectionView.BuiltView =>
            built.copy(localHandoffs = built.localHandoffs.map(h =>
              if (h.original == original) h.copy(original = malformed) else h))
          case semantic: SplitPhasePlanningSnapshot.CandidateProjectionView.SemanticView =>
            semantic.copy(gaps = semantic.gaps.map(g =>
              if (g.original == original) g.copy(original = malformed) else g))
          case other => other
        }
        val scopes = projection.scopes.map(s => if (s.original == original) s.copy(original = malformed) else s)
        val first = record.first.copy(originalContext = record.first.originalContext.copy(facts =
          record.first.originalContext.facts.copy(boundary = boundary)), snapshot = record.first.snapshot.copy(
            selection = facts.copy(boundary = boundary), projection = Some(projection.copy(candidates = candidates, scopes = scopes))))
        val partialfirst = K.inspectC(first)
        val partialjudgment = S.requestC(first).flatMap(r => S.inspectC(r,
          ContextReference(record.result.identity, record.result.revision), record.judgment))
        val inspected = V.inspectC(record.copy(first = first))
        val genuine = V.inspectC(record)
        Then("matching shallow facts cannot make the malformed local original complete while genuine Human data remains valid")
        take(partialfirst) shouldBe ()
        take(partialjudgment) shouldBe ()
        candidates.collectFirst {
          case built: SplitPhasePlanningSnapshot.CandidateProjectionView.BuiltView
              if built.key.cutAfterIndices == Vector(3) => built.localHandoffs.head.original.kind
        } shouldBe Some(null)
        record.frontier shouldBe V.Frontier.HumanDirectionRequired
        inspected shouldBe a[Consequence.Failure[?]]
        take(genuine) shouldBe ()
      }
      "reject missing reordered and duplicated candidates without reconstructing a planner" in {
        Given("a complete copied successor and its original ordered candidates")
        val record = _narrow()
        val candidates = record.frontier.asInstanceOf[V.Frontier.Applied].candidates
        When("passive caller data drops reorders or duplicates members")
        val results = Vector(candidates.tail, candidates.reverse, candidates :+ candidates.head).map(values =>
          V.inspectC(record.copy(frontier = V.Frontier.Applied(values))))
        Then("each changed full frontier is refused rather than normalized")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
      }
      "reject changed existing Built data and a forged already-published flag" in {
        Given("a successor containing previously published and newly constructed Built members")
        val record = _narrow()
        val candidates = record.frontier.asInstanceOf[V.Frontier.Applied].candidates
        val index = candidates.indexWhere(_.output match {
          case V.CandidateOutput.Built(_, true) => true
          case _ => false
        })
        val V.CandidateOutput.Built(documents, _) = candidates(index).output: @unchecked
        When("the original Built body or its publication status is changed")
        val changed = Vector(V.CandidateOutput.Built(documents.copy(localHandoffs = documents.localHandoffs :+
          Q.LocalHandoff(record.first.snapshot.selection.boundary.handoffs.handoffs.head,
            documents.proposal.children.head.phase, Vector.empty, Vector.empty)), true),
          V.CandidateOutput.Built(documents, false)).map(output => V.inspectC(record.copy(frontier =
            V.Frontier.Applied(candidates.updated(index, candidates(index).copy(output = output))))))
        Then("passive shape inspection preserves original documents and distinguishes reuse from new publication")
        changed.foreach(_ shouldBe a[Consequence.Failure[?]])
      }
      "reject crossed result references effective ranges owners and decision selectors" in {
        Given("a complete successor with the actual host result and effective transfer annotations")
        val record = _narrow()
        val candidates = record.frontier.asInstanceOf[V.Frontier.Applied].candidates
        val transfer = candidates.head.transfers.head
        val altered = Vector(transfer.copy(producingItems = transfer.producingItems.reverse :+ transfer.producingItems.head),
          transfer.copy(producingOwners = Vector.empty))
        When("caller data changes independent relationships instead of the actual semantic result")
        val results = Vector(V.inspectC(record.copy(result = record.result.copy(revision = "foreign"))),
          V.inspectC(record.copy(frontier = V.Frontier.HumanDirectionRequired))) ++ altered.map(value =>
          V.inspectC(record.copy(frontier = V.Frontier.Applied(candidates.updated(0,
            candidates.head.copy(transfers = candidates.head.transfers.updated(0, value)))))))
        Then("all contradictions are refused and the actual unchanged record still inspects")
        results.foreach(_ shouldBe a[Consequence.Failure[?]])
        take(V.inspectC(record)) shouldBe ()
      }
      "expose judgment inspection as Unit rather than semantic or native authority" in {
        Given("an independently readable request and the exact valid scoped judgment")
        val (_, _, request) = _fixture()
        val reply = _reply(request, false)
        When("only passive judgment shape is inspected")
        val actual: Consequence[Unit] = S.inspectC(request,
          ContextReference(request.result.identity, request.result.revision), reply.judgment)
        Then("inspection supplies no Admitted or executable successor")
        take(actual) shouldBe ()
        When("absent data is submitted to the passive project and inspect boundaries")
        val absent = Vector(V.projectC(null), V.inspectC(null))
        Then("both absent inputs are rejected without constructing authority")
        absent.foreach(_ shouldBe a[Consequence.Failure[?]])
      }
    }
  }
}
