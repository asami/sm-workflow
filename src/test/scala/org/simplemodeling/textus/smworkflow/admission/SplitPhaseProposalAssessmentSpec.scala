package org.simplemodeling.textus.smworkflow.admission

import java.nio.file.Path

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseEntryAssessment.*
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalAssessment.*
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.{Child, ProposalDraft, SourceInventory}
import org.simplemodeling.textus.smworkflow.value.SplitPhaseStartInput

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseProposalAssessmentSpec extends AnyWordSpec with Matchers {
  private val snapshotDigest = "a" * 64
  private val draft = ProposalDraft(
    "proposal-1", "77", "source-authority-1", snapshotDigest,
    Vector(
      Child("77", Vector("work-1", "work-2"), 360, true),
      Child("77.1", Vector("work-3"), 360, true)
    )
  )
  private val source = SourceSnapshot("snapshot-1", "77", "source-authority-1",
    "revision-1", snapshotDigest)
  private val candidate = ProposalCandidate("proposal-1", "77", "source-authority-1",
    snapshotDigest, SplitPhaseProposalPrecheck.digestOf(draft))
  private val inventory = SourceInventory("77", snapshotDigest,
    Vector("work-1", "work-2", "work-3"), Set("77"))
  private val sourceBoundary = SplitPhaseSourceBoundary.Contract(snapshotDigest,
    "77", "source-authority-1", Vector(
      SplitPhaseSourceBoundary.Item("work-1", Vector("close-work-1"),
        Vector.empty, "source-authority-1"),
      SplitPhaseSourceBoundary.Item("work-2", Vector("close-work-2"),
        Vector("work-1"), "source-authority-1"),
      SplitPhaseSourceBoundary.Item("work-3", Vector("close-work-3"),
        Vector("work-2"), "source-authority-1")),
    Set("authority-edge-work-1", "authority-edge-work-2"))

  private def take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case failure => fail(s"SplitPhase proposal assessment fixture failed: $failure")
  }

  private final class Fixture {
    var loadedInventory: Option[SourceInventory] = Some(inventory)
    var loadedDraft: Option[ProposalDraft] = Some(draft)
    var loadedValidation: Option[SplitPhaseProposalValidation.Evidence] =
      Some(SplitPhaseProposalValidationFixture.evidenceFor(draft))
    var loadedBoundary: Option[SplitPhaseSourceBoundary.Contract] = Some(sourceBoundary)
    var inventoryReads = Vector.empty[String]
    var draftReads = Vector.empty[String]
    var validationReads = Vector.empty[String]
    var boundaryReads = Vector.empty[String]

    val entry = new SplitPhaseEntryAssessment.Port {
      def loadSourceC(phase: String, authority: String): Consequence[Option[SourceSnapshot]] =
        Consequence.success(Some(source))
      def loadProposalC(reference: String): Consequence[Option[ProposalCandidate]] =
        Consequence.success(Some(candidate))
    }
    val evidence = new SplitPhaseProposalAssessment.Port {
      def loadInventoryC(reference: String): Consequence[Option[SourceInventory]] = {
        inventoryReads :+= reference
        Consequence.success(loadedInventory)
      }
      def loadDraftC(reference: String): Consequence[Option[ProposalDraft]] = {
        draftReads :+= reference
        Consequence.success(loadedDraft)
      }
      def loadValidationEvidenceC(reference: String):
          Consequence[Option[SplitPhaseProposalValidation.Evidence]] = {
        validationReads :+= reference
        Consequence.success(loadedValidation)
      }
      def loadSourceBoundaryC(reference: String):
          Consequence[Option[SplitPhaseSourceBoundary.Contract]] = {
        boundaryReads :+= reference
        Consequence.success(loadedBoundary)
      }
    }

    def entryAssessment(proposalReference: Option[String] = Some("proposal-1")): Assessment =
      take(SplitPhaseEntryAssessment.assessC(
        SplitPhaseStartInput("77", "PREVIEW", "source-authority-1",
          Some("snapshot-1"), None, proposalReference), entry))
  }

  "SplitPhase proposal assessment" should {
    "resolve exact host-owned evidence after entry correlation" in {
      val fixture = new Fixture
      val validation = take(SplitPhaseProposalValidation.validateC(draft,
        fixture.loadedValidation.get)).asInstanceOf[SplitPhaseProposalValidation.Outcome.Ready]
      val ready = take(SplitPhaseProposalAssessment.assessC(fixture.entryAssessment(),
        fixture.evidence)).asInstanceOf[Outcome.EvidenceReady]
      ready.proposalDigest shouldBe candidate.digest
      ready.evidenceDigest should fullyMatch regex "[0-9a-f]{64}"
      ready.evidenceDigest should not be validation.evidenceDigest
      fixture.inventoryReads shouldBe Vector("snapshot-1")
      fixture.draftReads shouldBe Vector("proposal-1")
      fixture.validationReads shouldBe Vector("proposal-1")
      fixture.boundaryReads shouldBe Vector("snapshot-1")
    }

    "carry a nonzero versioned Phase overhead through the public assessment path" in {
      val fixture = new Fixture
      val withOverhead = SplitPhaseProposalValidationFixture.evidenceFor(draft,
        phaseOverheadMinutes = 20)
      fixture.loadedValidation = Some(withOverhead)
      val validated = take(SplitPhaseProposalValidation.validateC(draft,
        withOverhead)).asInstanceOf[SplitPhaseProposalValidation.Outcome.Ready]
      val ready = take(SplitPhaseProposalAssessment.assessC(fixture.entryAssessment(),
        fixture.evidence)).asInstanceOf[Outcome.EvidenceReady]
      ready.proposalDigest shouldBe candidate.digest
      ready.evidenceDigest should not be validated.evidenceDigest
    }

    "hold public assessment for an unresolved semantic edge" in {
      val fixture = new Fixture
      fixture.loadedValidation = fixture.loadedValidation.map { value =>
        val edge = value.semanticEdges.last
        value.copy(semanticEdges = value.semanticEdges.updated(
          value.semanticEdges.size - 1,
          edge.copy(disposition = SplitPhasePartitionEnumeration.EdgeDisposition.Unresolved)))
      }
      take(SplitPhaseProposalAssessment.assessC(fixture.entryAssessment(),
        fixture.evidence)) shouldBe Outcome.SemanticBoundaryRequired(Vector("work-2"))
    }

    "ask for a proposal without fetching unrelated evidence" in {
      val fixture = new Fixture
      take(SplitPhaseProposalAssessment.assessC(fixture.entryAssessment(None), fixture.evidence)) shouldBe
        Outcome.ProposalRequired
      fixture.inventoryReads shouldBe empty
      fixture.draftReads shouldBe empty
      fixture.validationReads shouldBe empty
      fixture.boundaryReads shouldBe empty
    }

    "fail closed when a referenced inventory or draft is missing or changed" in {
      val missingInventory = new Fixture
      missingInventory.loadedInventory = None
      SplitPhaseProposalAssessment.assessC(missingInventory.entryAssessment(), missingInventory.evidence) shouldBe
        a[Consequence.Failure[?]]
      missingInventory.draftReads shouldBe empty

      val missingDraft = new Fixture
      missingDraft.loadedDraft = None
      SplitPhaseProposalAssessment.assessC(missingDraft.entryAssessment(), missingDraft.evidence) shouldBe
        a[Consequence.Failure[?]]

      val changedDraft = new Fixture
      changedDraft.loadedDraft = Some(draft.copy(children = draft.children.updated(1,
        draft.children(1).copy(expectedMinutes = 200))))
      SplitPhaseProposalAssessment.assessC(changedDraft.entryAssessment(), changedDraft.evidence) shouldBe
        a[Consequence.Failure[?]]

      val missingValidation = new Fixture
      missingValidation.loadedValidation = None
      SplitPhaseProposalAssessment.assessC(missingValidation.entryAssessment(),
        missingValidation.evidence) shouldBe a[Consequence.Failure[?]]
      missingValidation.boundaryReads shouldBe empty
    }

    "return a typed decision when the child identity is already occupied" in {
      val fixture = new Fixture
      fixture.loadedInventory = Some(inventory.copy(occupiedPhaseIdentities = Set("77", "77.1")))
      take(SplitPhaseProposalAssessment.assessC(fixture.entryAssessment(), fixture.evidence)) shouldBe
        Outcome.DecisionRequired("child-phase-identity-collision")
      fixture.validationReads shouldBe empty
      fixture.boundaryReads shouldBe empty
    }

    "leave unresolved calibration at the entry boundary" in {
      val fixture = new Fixture
      fixture.loadedValidation = fixture.loadedValidation.map(evidence =>
        evidence.copy(calibrationRequests = evidence.calibrationRequests.map(request =>
          request.copy(policy = request.policy.copy(minimumSampleCount = 3)))))
      take(SplitPhaseProposalAssessment.assessC(fixture.entryAssessment(), fixture.evidence)) shouldBe
        Outcome.NovelWorkRequired(Vector("work-1", "work-2", "work-3"))
    }

    "wait for missing source boundary and reject changed authority" in {
      val missing = new Fixture
      missing.loadedBoundary = None
      take(SplitPhaseProposalAssessment.assessC(missing.entryAssessment(),
        missing.evidence)) shouldBe Outcome.SourceBoundaryRequired
      missing.boundaryReads shouldBe Vector("snapshot-1")

      val changed = new Fixture
      changed.loadedBoundary = Some(sourceBoundary.copy(
        authorizedHandoffReferences = Set.empty))
      SplitPhaseProposalAssessment.assessC(changed.entryAssessment(),
        changed.evidence) shouldBe a[Consequence.Failure[?]]
    }

    "load the source contract from the frozen Phase file through the public assessment port" in {
      val fixture = new Fixture
      fixture.loadedBoundary = None
      val reference = SplitPhaseSourceBoundaryDocumentReader.DocumentReference(
        "snapshot-1", snapshotDigest, "77", "source-authority-1",
        Path.of("src/test/resources/source-boundary-reader-fixture"),
        "c25b570819610acae6f27df9d4f456a0af164ab368242641b7845febdbfb5455")
      val port = new SplitPhaseSourceBoundaryDocumentReader.Port(fixture.evidence,
        Map("snapshot-1" -> reference))
      take(SplitPhaseProposalAssessment.assessC(fixture.entryAssessment(), port)) shouldBe
        a[Outcome.EvidenceReady]
      fixture.boundaryReads shouldBe empty

      val stale = new SplitPhaseSourceBoundaryDocumentReader.Port(fixture.evidence,
        Map("snapshot-1" -> reference.copy(phaseDocumentSha256 = "b" * 64)))
      SplitPhaseProposalAssessment.assessC(fixture.entryAssessment(), stale) shouldBe
        a[Consequence.Failure[?]]
    }
  }
}
