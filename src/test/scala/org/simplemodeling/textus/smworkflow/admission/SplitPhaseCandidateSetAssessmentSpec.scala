package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateAdmissibility.Outcome
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateGateDerivation.Evidence
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.{Edge, EdgeDisposition, Outcome as PartitionOutcome, WorkItem, enumerateC}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.{Child, ProposalDraft}

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseCandidateSetAssessmentSpec extends AnyWordSpec with Matchers {
  private val root = Path.of("src/test/resources/source-boundary-reader-fixture")
  private val snapshotIdentity = "snapshot-1"
  private val sourceDigest =
    "c53e30985ec679e39c14055cfe7c73c94e3e05be78dbd381b68c43d04f300760"

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Expected candidate-set success: $other")
  }

  private def withCopiedSource[A](run: Path => A): A = {
    val copied = Files.createTempDirectory("smwf-proposal-admission-")
    val source = root.toAbsolutePath.normalize
    val entries = Files.walk(source)
    try entries.iterator().asScala.foreach { path =>
      val target = copied.resolve(source.relativize(path).toString)
      if (Files.isDirectory(path)) Files.createDirectories(target)
      else Files.copy(path, target)
    } finally entries.close()
    try run(copied)
    finally {
      val created = Files.walk(copied)
      try created.iterator().asScala.toVector
        .sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      finally created.close()
    }
  }

  private def fixture: (SplitPhaseSourceSnapshotCatalog.Snapshot,
                        PartitionOutcome.Enumerated,
                        SplitPhasePartitionEnumeration.Policy, Evidence) = {
    val snapshot = take(SplitPhaseSourceSnapshotCatalog.loadC(root,
      snapshotIdentity)).get
    val draft = ProposalDraft("proposal-1", "77", "source-authority-1",
      sourceDigest, Vector(
        Child("77", Vector("work-1", "work-2"), 360, true),
        Child("77.1", Vector("work-3"), 360, true)))
    val validation = SplitPhaseProposalValidationFixture.evidenceFor(draft)
    val estimates = Map("work-1" -> 180, "work-2" -> 180, "work-3" -> 360)
    val enumerated = take(enumerateC(sourceDigest,
      snapshot.inventory.orderedUnfinishedItems.map(item =>
        WorkItem(item, estimates(item))), validation.semanticEdges,
      validation.partitionPolicy)).asInstanceOf[PartitionOutcome.Enumerated]
    enumerated.candidates.size shouldBe 1
    val evidence = Evidence(enumerated.candidates.head.digest, draft, estimates,
      validation.semanticHandoffs, validation.validationOwnership,
      validation.semanticEdges)
    (snapshot, enumerated, validation.partitionPolicy, evidence)
  }

  "SplitPhase candidate-set assessment" should {
    "derive all gates from one file-backed source catalog before duration ranking" in {
      val (_, enumerated, policy, evidence) = fixture

      val outcome = take(SplitPhaseCandidateSetAssessment.assessCatalogC(root,
        snapshotIdentity, enumerated, policy, Vector(evidence)))

      outcome match {
        case ready: Outcome.DurationRanked =>
          ready.consideredDigest shouldBe enumerated.consideredDigest
          ready.durationLeaders shouldBe Vector(evidence.candidateDigest)
          ready.eligible.map(_.candidateDigest) shouldBe
            Vector(evidence.candidateDigest)
        case other => fail(s"Expected duration frontier, found $other")
      }
      take(SplitPhaseObjectiveDecision.decideCatalogC(root, snapshotIdentity,
        enumerated, policy, Vector(evidence))) shouldBe
        SplitPhaseObjectiveDecision.Outcome.DurationDecisive(
          enumerated.consideredDigest, evidence.candidateDigest, Vector.empty)
      take(SplitPhaseObjectiveDecision.decideCatalogWithFinalC(root,
        snapshotIdentity, enumerated, policy, Vector(evidence))) shouldBe
        SplitPhaseObjectiveDecision.Outcome.ObjectiveSelected(
          enumerated.consideredDigest, evidence.candidateDigest,
          "duration", Vector.empty)
    }

    "keep closure and authority pending without a source boundary" in {
      val (snapshot, enumerated, policy, evidence) = fixture

      take(SplitPhaseCandidateSetAssessment.assessC(snapshot.copy(boundary = None),
        enumerated, policy, Vector(evidence))) shouldBe
        Outcome.EvidenceRequired(enumerated.consideredDigest,
          Vector(evidence.candidateDigest), Vector.empty)
    }

    "admit only the referenced proposal matching the objective-selected candidate" in {
      val (snapshot, enumerated, policy, evidence) = fixture
      val proposal = SplitPhaseEntryAssessment.ProposalCandidate(
        evidence.draft.identity, snapshot.source.sourcePhaseIdentity,
        snapshot.source.authorityReference, snapshot.source.digest,
        SplitPhaseProposalPrecheck.digestOf(evidence.draft))
      val assessment = SplitPhaseEntryAssessment.Assessment(snapshot.source,
        Some(proposal))
      val validation = SplitPhaseProposalValidationFixture.evidenceFor(evidence.draft)
      def port(draft: ProposalDraft) = new SplitPhaseProposalAssessment.Port {
        def loadInventoryC(identity: String) = Consequence.success(
          Option.empty[SplitPhaseProposalPrecheck.SourceInventory])
        def loadDraftC(identity: String) = Consequence.success(Some(draft))
        def loadValidationEvidenceC(identity: String) =
          Consequence.success(Some(validation))
        def loadSourceBoundaryC(identity: String) = Consequence.success(
          Option.empty[SplitPhaseSourceBoundary.Contract])
      }

      val admitted = take(SplitPhaseProposalAdmission.admitCatalogC(root, snapshotIdentity,
        assessment, enumerated, policy, Vector(evidence),
        port(evidence.draft))) match {
        case value: SplitPhaseProposalAdmission.Outcome.Admitted =>
          value.candidateDigest shouldBe evidence.candidateDigest
          value.proposalDigest shouldBe proposal.digest
          value.objectiveStage shouldBe "duration"
          value.proposalEvidenceDigest.matches("[0-9a-f]{64}") shouldBe true
          value.sourceSnapshotDigest shouldBe sourceDigest
          value.partitionPolicyDigest shouldBe
            SplitPhasePartitionEnumeration.policyDigestOf(policy)
          value.costDocumentSha256.get.matches("[0-9a-f]{64}") shouldBe true
          value.overheadDocumentSha256.get.matches("[0-9a-f]{64}") shouldBe true
          value
        case other => fail(s"Expected proposal admission, found $other")
      }
      take(SplitPhaseProposalAdmission.revalidateCatalogC(root, snapshotIdentity,
        assessment, enumerated, policy, Vector(evidence), port(evidence.draft),
        admitted)) shouldBe admitted
      SplitPhaseProposalAdmission.revalidateCatalogC(root, snapshotIdentity,
        assessment, enumerated, policy, Vector(evidence), port(evidence.draft),
        admitted.copy(costDocumentSha256 = None)) shouldBe
          a[Consequence.Failure[?]]
      SplitPhaseProposalAdmission.revalidateCatalogC(root, snapshotIdentity,
        assessment, enumerated, policy, Vector(evidence), port(evidence.draft),
        admitted.copy(sourceSnapshotDigest = "0" * 64)) shouldBe
          a[Consequence.Failure[?]]
      withCopiedSource { copied =>
        take(SplitPhaseProposalAdmission.revalidateCatalogC(copied,
          snapshotIdentity, assessment, enumerated, policy, Vector(evidence),
          port(evidence.draft), admitted)) shouldBe admitted
        val cost = copied.resolve("docs/phase/cost-policies/snapshot-1.json")
        Files.writeString(cost, Files.readString(cost).replace(
          "cost-fixture-v1", "cost-fixture-v2"))
        SplitPhaseProposalAdmission.revalidateCatalogC(copied,
          snapshotIdentity, assessment, enumerated, policy, Vector(evidence),
          port(evidence.draft), admitted) shouldBe a[Consequence.Failure[?]]
      }
      take(SplitPhaseProposalAdmission.admitCatalogC(root, snapshotIdentity,
        assessment.copy(proposalCandidate = None), enumerated, policy,
        Vector(evidence), port(evidence.draft))) shouldBe
        SplitPhaseProposalAdmission.Outcome.ProposalPending(
          SplitPhaseProposalAssessment.Outcome.ProposalRequired)
      SplitPhaseProposalAdmission.admitCatalogC(root, snapshotIdentity,
        assessment.copy(proposalCandidate = Some(proposal.copy(digest = "0" * 64))),
        enumerated, policy, Vector(evidence), port(evidence.draft)) shouldBe
          a[Consequence.Failure[?]]
      SplitPhaseProposalAdmission.admitCatalogC(root, snapshotIdentity,
        assessment, enumerated, policy, Vector(evidence),
        port(evidence.draft.copy(children = evidence.draft.children.updated(1,
          evidence.draft.children(1).copy(expectedMinutes = 350))))) shouldBe
          a[Consequence.Failure[?]]
    }

    "project an admitted proposal without changing planning documents" in {
      val (snapshot, enumerated, policy, evidence) = fixture
      val proposal = SplitPhaseEntryAssessment.ProposalCandidate(
        evidence.draft.identity, snapshot.source.sourcePhaseIdentity,
        snapshot.source.authorityReference, snapshot.source.digest,
        SplitPhaseProposalPrecheck.digestOf(evidence.draft))
      val assessment = SplitPhaseEntryAssessment.Assessment(snapshot.source,
        Some(proposal))
      val validation = SplitPhaseProposalValidationFixture.evidenceFor(evidence.draft)
      val port = new SplitPhaseProposalAssessment.Port {
        def loadInventoryC(identity: String) = Consequence.success(
          Option.empty[SplitPhaseProposalPrecheck.SourceInventory])
        def loadDraftC(identity: String) = Consequence.success(Some(evidence.draft))
        def loadValidationEvidenceC(identity: String) =
          Consequence.success(Some(validation))
        def loadSourceBoundaryC(identity: String) = Consequence.success(
          Option.empty[SplitPhaseSourceBoundary.Contract])
      }
      val admitted = take(SplitPhaseProposalAdmission.admitCatalogC(root,
        snapshotIdentity, assessment, enumerated, policy, Vector(evidence), port))
        .asInstanceOf[SplitPhaseProposalAdmission.Outcome.Admitted]
      val source = root.resolve("docs/phase/phase-77.md")
      val sourceBytes = Files.readAllBytes(source).toVector
      val preview = take(SplitPhasePreviewProjection.projectCatalogC(root,
        snapshotIdentity, assessment, enumerated, policy, Vector(evidence),
        port, admitted))

      preview.admission shouldBe admitted
      preview.sourcePhaseIdentity shouldBe "77"
      preview.children.map(_.identity) shouldBe Vector("77", "77.1")
      preview.children.map(_.orderedWorkItems.map(_.identity)) shouldBe
        Vector(Vector("work-1", "work-2"), Vector("work-3"))
      preview.children.map(_.closureCriteria) shouldBe
        Vector(Vector("close-work-1", "close-work-2"), Vector("close-work-3"))
      preview.children.map(_.validationOwner) shouldBe Vector("77.1", "77.1")
      preview.handoffs shouldBe Vector.empty
      Files.readAllBytes(source).toVector shouldBe sourceBytes
      Files.exists(root.resolve("docs/phase/phase-77.1.md")) shouldBe false
      SplitPhasePreviewProjection.projectCatalogC(root, snapshotIdentity,
        assessment, enumerated, policy, Vector(evidence), port,
        admitted.copy(proposalDigest = "0" * 64)) shouldBe
          a[Consequence.Failure[?]]
    }

    "reject one short-child candidate without losing tied admissible candidates" in {
      val estimates = Map("work-1" -> 260, "work-2" -> 180, "work-3" -> 260)
      val edges = Vector("work-1", "work-2").map(item =>
        Edge(item, EdgeDisposition.Either, s"authority-edge-$item"))
      val policy = SplitPhasePartitionEnumeration.Policy("tie-partition-v1", 0, 4096)
      val enumerated = take(enumerateC(sourceDigest,
        Vector("work-1", "work-2", "work-3").map(item =>
          WorkItem(item, estimates(item))), edges, policy))
        .asInstanceOf[PartitionOutcome.Enumerated]
      enumerated.candidates.size shouldBe 3
      val evidence = enumerated.candidates.map { candidate =>
        val children = candidate.children.zipWithIndex.map { case (range, index) =>
          Child(if (index == 0) "77" else s"77.$index",
            range.ownedItems, range.expectedMinutes, true)
        }
        val draft = ProposalDraft(s"proposal-${candidate.cutAfterIndices.mkString("-")}",
          "77", "source-authority-1", sourceDigest, children)
        val validation = SplitPhaseProposalValidationFixture.evidenceFor(draft)
        Evidence(candidate.digest, draft, estimates, validation.semanticHandoffs,
          validation.validationOwnership, edges)
      }

      take(SplitPhaseCandidateSetAssessment.assessCatalogC(root,
        snapshotIdentity, enumerated, policy, evidence)) match {
        case ready: Outcome.DurationRanked =>
          ready.rejectedCandidateDigests.size shouldBe 1
          ready.eligible.size shouldBe 2
          ready.durationLeaders.size shouldBe 2
          ready.durationLeaders.toSet shouldBe
            ready.eligible.map(_.candidateDigest).toSet
          val costPolicy = SplitPhaseCostIsolation.Policy("cost-fixture-v1",
            "expensive", "lower-cost", 2, 1, 300)
          val costEvidence = SplitPhaseCostIsolation.Evidence(sourceDigest,
            enumerated.consideredDigest, costPolicy, Map(
              "work-1" -> SplitPhaseCostIsolation.Demand.ExpensiveReasoning,
              "work-2" -> SplitPhaseCostIsolation.Demand.SettledExecution,
              "work-3" -> SplitPhaseCostIsolation.Demand.SettledExecution))
          val cost = take(SplitPhaseCostIsolation.rankC(enumerated, ready,
            costEvidence))
          cost.scores.map(_.materialSavingUnits) shouldBe Vector(BigInt(440), BigInt(0))
          cost.costLeaders shouldBe Vector(enumerated.candidates.find(
            _.cutAfterIndices == Vector(0)).get.digest)
          cost.evidenceDigest.matches("[0-9a-f]{64}") shouldBe true
          take(SplitPhaseCostIsolation.rankC(enumerated, ready,
            costEvidence.copy(policy = costPolicy.copy(
              materialSavingThresholdUnits = 500)))).costLeaders.size shouldBe 2
          val costTie = take(SplitPhaseCostIsolation.rankC(enumerated, ready,
            costEvidence.copy(policy = costPolicy.copy(
              materialSavingThresholdUnits = 500))))
          val allOverheadEvidence = evidence.map { e =>
            val candidate = enumerated.candidates.find(_.digest == e.candidateDigest).get
            if (candidate.cutAfterIndices == Vector(1))
              e.copy(validationOwnership = e.validationOwnership.copy(
                timing = SplitPhaseValidationOwnership.Timing.EachPhase,
                children = e.validationOwnership.children.map(child =>
                  child.copy(validationOwner = child.identity))))
            else e
          }
          val overheadEvidence = allOverheadEvidence.filter(e =>
            costTie.costLeaders.contains(e.candidateDigest))
          val overheadPolicy = SplitPhaseOverheadPriority.Policy(
            "overhead-fixture-v1", 1, 0, 10, 1, 1)
          val overhead = take(SplitPhaseOverheadPriority.rankC(enumerated,
            costTie, overheadEvidence, overheadPolicy))
          overhead.scores.map(_.validationRunCount) shouldBe Vector(1, 2)
          overhead.scores.map(_.totalUnits) shouldBe Vector(BigInt(16), BigInt(26))
          overhead.overheadLeaders shouldBe Vector(enumerated.candidates.find(
            _.cutAfterIndices == Vector(0)).get.digest)
          take(SplitPhaseOverheadPriority.rankC(enumerated, costTie,
            overheadEvidence, overheadPolicy.copy(perValidationRunUnits = 0)))
            .overheadLeaders.size shouldBe 2
          SplitPhaseOverheadPriority.rankC(enumerated, costTie,
            overheadEvidence.tail, overheadPolicy) shouldBe a[Consequence.Failure[?]]
          take(SplitPhaseObjectiveDecision.decideCatalogWithOverheadC(root,
            snapshotIdentity, enumerated, policy, allOverheadEvidence)) match {
            case ranked: SplitPhaseObjectiveDecision.Outcome.OverheadRanked =>
              ranked.overheadLeaders shouldBe overhead.overheadLeaders
              ranked.overheadEvidenceDigest.matches("[0-9a-f]{64}") shouldBe true
              ranked.overheadDocumentSha256.matches("[0-9a-f]{64}") shouldBe true
            case other => fail(s"Expected source-bound overhead frontier, found $other")
          }
          take(SplitPhaseObjectiveDecision.decideCatalogWithFinalC(root,
            snapshotIdentity, enumerated, policy, allOverheadEvidence)) shouldBe
            SplitPhaseObjectiveDecision.Outcome.ObjectiveSelected(
              enumerated.consideredDigest, overhead.overheadLeaders.head,
              "overhead", Vector(enumerated.candidates.find(
                _.cutAfterIndices == Vector(0, 1)).get.digest))
          take(SplitPhaseObjectiveDecision.decideCatalogWithFinalC(root,
            snapshotIdentity, enumerated, policy, evidence)) shouldBe
            SplitPhaseObjectiveDecision.Outcome.ObjectiveSelected(
              enumerated.consideredDigest,
              enumerated.candidates.find(_.cutAfterIndices == Vector(0)).get.digest,
              "stable-tie-break", Vector(enumerated.candidates.find(
                _.cutAfterIndices == Vector(0, 1)).get.digest))
          val differingAuthority = evidence.map { e =>
            val candidate = enumerated.candidates.find(_.digest == e.candidateDigest).get
            if (candidate.cutAfterIndices == Vector(1)) {
              val from = e.draft.children.head.identity
              val to = e.draft.children.last.identity
              val handoff = SplitPhaseSemanticHandoff.Handoff("handoff-1", from, to,
                "decision", "input", "transfer", "output", from,
                "source-changed", "authority-edge-work-1")
              e.copy(handoffs = e.handoffs.copy(
                handoffs = Vector(handoff),
                incomingHandoffIdsByChild = Map(from -> Vector.empty[String],
                  to -> Vector(handoff.identity))))
            } else e
          }
          take(SplitPhaseObjectiveDecision.decideCatalogWithFinalC(root,
            snapshotIdentity, enumerated, policy, differingAuthority)) match {
            case required: SplitPhaseObjectiveDecision.Outcome.AuthorityDecisionRequired =>
              required.tiedCandidateDigests.size shouldBe 2
              required.authorityDigests.distinct.size shouldBe 2
            case other => fail(s"Expected human authority Decision, found $other")
          }
          SplitPhaseCostIsolation.rankC(enumerated, ready,
            costEvidence.copy(sourceSnapshotDigest = "0" * 64)) shouldBe
              a[Consequence.Failure[?]]
          SplitPhaseCostIsolation.rankC(enumerated, ready,
            costEvidence.copy(demandByItem = costEvidence.demandByItem - "work-3")) shouldBe
              a[Consequence.Failure[?]]
        case other => fail(s"Expected two tied admissible candidates, found $other")
      }
      take(SplitPhaseObjectiveDecision.decideCatalogC(root, snapshotIdentity,
        enumerated, policy, evidence)) match {
        case required: SplitPhaseObjectiveDecision.Outcome.CostPolicyRequired =>
          required.tiedCandidateDigests.size shouldBe 2
          required.rejectedCandidateDigests.size shouldBe 1
        case other => fail(s"Expected cost-policy boundary, found $other")
      }
      take(SplitPhaseObjectiveDecision.decideCatalogWithCostC(root,
        snapshotIdentity, enumerated, policy, evidence)) match {
        case ranked: SplitPhaseObjectiveDecision.Outcome.CostRanked =>
          ranked.costLeaders.size shouldBe 2
          ranked.costEvidenceDigest.matches("[0-9a-f]{64}") shouldBe true
          ranked.costDocumentSha256.matches("[0-9a-f]{64}") shouldBe true
          ranked.rejectedCandidateDigests.size shouldBe 1
        case other => fail(s"Expected source-bound cost frontier, found $other")
      }
    }

    "reject missing candidate coverage and caller-supplied boundary authority" in {
      val (snapshot, enumerated, policy, evidence) = fixture

      SplitPhaseCandidateSetAssessment.assessC(snapshot, enumerated, policy,
        Vector.empty) shouldBe a[Consequence.Failure[?]]
      SplitPhaseCandidateSetAssessment.assessC(snapshot, enumerated, policy,
        Vector(evidence.copy(sourceBoundary = snapshot.boundary))) shouldBe
        a[Consequence.Failure[?]]
      SplitPhaseCandidateSetAssessment.assessC(snapshot, enumerated, policy,
        Vector(evidence.copy(draft = evidence.draft.copy(
          sourceAuthorityReference = "other-authority")))) shouldBe
        a[Consequence.Failure[?]]
      SplitPhaseCandidateSetAssessment.assessCatalogC(root, "snapshot-stale",
        enumerated, policy, Vector(evidence)) shouldBe a[Consequence.Failure[?]]
    }
  }
}
