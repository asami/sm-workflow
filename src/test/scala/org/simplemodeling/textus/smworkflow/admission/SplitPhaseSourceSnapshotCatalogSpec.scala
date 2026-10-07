package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseEntryAssessment.{ProposalCandidate, SourceSnapshot}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.{Child, ProposalDraft, SourceInventory, digestOf}
import org.simplemodeling.textus.smworkflow.value.SplitPhaseStartInput

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseSourceSnapshotCatalogSpec extends AnyWordSpec with Matchers {
  private val root = Path.of("src/test/resources/source-boundary-reader-fixture")
  private val catalogDigest =
    "c53e30985ec679e39c14055cfe7c73c94e3e05be78dbd381b68c43d04f300760"

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case other => fail(s"Expected source snapshot catalog success: $other")
  }

  "SplitPhase source snapshot catalog" should {
    "bind Start, inventory, and document boundary to one catalog digest" in {
      val loaded = take(SplitPhaseSourceSnapshotCatalog.loadC(root, "snapshot-1")).get
      loaded.source shouldBe SourceSnapshot("snapshot-1", "77",
        "source-authority-1", "phase-77-r1", catalogDigest)
      loaded.inventory shouldBe SourceInventory("77", catalogDigest,
        Vector("work-1", "work-2", "work-3"), Set("77"))
      loaded.documentReference.sourceSnapshotDigest shouldBe catalogDigest
      loaded.boundary.map(_.items.map(_.identity)) shouldBe
        Some(Vector("work-1", "work-2", "work-3"))

      val draft = ProposalDraft("proposal-1", "77", "source-authority-1",
        catalogDigest, Vector(
          Child("77", Vector("work-1", "work-2"), 360, true),
          Child("77.1", Vector("work-3"), 360, true)))
      val candidate = ProposalCandidate("proposal-1", "77", "source-authority-1",
        catalogDigest, digestOf(draft))
      val proposalDelegate = new SplitPhaseEntryAssessment.Port {
        def loadSourceC(phase: String, authority: String):
            Consequence[Option[SourceSnapshot]] = Consequence.success(None)
        def loadProposalC(identity: String): Consequence[Option[ProposalCandidate]] =
          Consequence.success(Option.when(identity == candidate.identity)(candidate))
      }
      val entry = new SplitPhaseSourceSnapshotCatalog.EntryPort(root,
        "snapshot-1", proposalDelegate)
      val assessment = take(SplitPhaseEntryAssessment.assessC(
        SplitPhaseStartInput("77", "PREVIEW", "source-authority-1",
          Some("snapshot-1"), None, Some("proposal-1")), entry))
      assessment.source.digest shouldBe catalogDigest

      val evidence = SplitPhaseProposalValidationFixture.evidenceFor(draft)
      val assessmentDelegate = new SplitPhaseProposalAssessment.Port {
        def loadInventoryC(identity: String): Consequence[Option[SourceInventory]] =
          Consequence.success(None)
        def loadSourceBoundaryC(identity: String):
            Consequence[Option[SplitPhaseSourceBoundary.Contract]] =
          Consequence.success(None)
        def loadDraftC(identity: String): Consequence[Option[ProposalDraft]] =
          Consequence.success(Option.when(identity == draft.identity)(draft))
        def loadValidationEvidenceC(identity: String):
            Consequence[Option[SplitPhaseProposalValidation.Evidence]] =
          Consequence.success(Option.when(identity == draft.identity)(evidence))
      }
      val proposal = new SplitPhaseSourceSnapshotCatalog.ProposalPort(root,
        "snapshot-1", assessmentDelegate)
      take(SplitPhaseProposalAssessment.assessC(assessment, proposal)) shouldBe
        a[SplitPhaseProposalAssessment.Outcome.EvidenceReady]
    }

    "reject stale catalog references and unsafe lookup identities" in {
      SplitPhaseSourceSnapshotCatalog.loadC(root, "snapshot-stale") shouldBe
        a[Consequence.Failure[?]]
      SplitPhaseSourceSnapshotCatalog.loadC(root, "../phase-77") shouldBe
        a[Consequence.Failure[?]]
      take(SplitPhaseSourceSnapshotCatalog.loadC(root, "missing")) shouldBe None
    }

    "keep a registered prose-only source boundary pending" in {
      val loaded = take(SplitPhaseSourceSnapshotCatalog.loadC(root,
        "snapshot-prose")).get
      loaded.source.sourcePhaseIdentity shouldBe "78"
      loaded.boundary shouldBe None
    }
  }
}
