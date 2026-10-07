package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateAdmissibility.{Gate, assessC}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseCandidateGateDerivation.{Evidence, deriveC}
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.{Edge, EdgeDisposition, Policy, WorkItem, enumerateC}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.{Child, ProposalDraft, digestOf}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseSourceBoundary.{Contract, Item}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseSemanticHandoff.{Handoff, Plan as HandoffPlan}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseValidationOwnership.{ChildDeclaration, Plan as ValidationPlan, Timing}

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
final class SplitPhaseCandidateGateDerivationSpec extends AnyWordSpec with Matchers {
  private val source = "a" * 64
  private val policy = Policy("partition-v1", 20, 4096)

  private def sourceBoundary: Contract = Contract(source, "77", "authority-77",
    Vector(Item("a", Vector("closure-77"), Vector.empty, "authority-77"),
      Item("b", Vector("closure-77.1"), Vector("a"), "authority-77")),
    Set("authority-edge"))

  private def fixture: (SplitPhasePartitionEnumeration.Outcome.Enumerated, Evidence) = {
    val items = Vector(WorkItem("a", 180), WorkItem("b", 260))
    val enumerated = enumerateC(source, items,
      Vector(Edge("a", EdgeDisposition.CutOnly, "authority-edge")), policy) match {
      case Consequence.Success(value: SplitPhasePartitionEnumeration.Outcome.Enumerated) => value
      case other => fail(s"Expected one enumerated candidate: $other")
    }
    val draft = ProposalDraft("proposal-77", "77", "authority-77", source,
      Vector(Child("77", Vector("a"), 200, true),
        Child("77.1", Vector("b"), 280, true)))
    val handoff = Handoff("handoff-1", "77", "77.1", "review-result",
      "candidate", "review", "evidence", "77", "candidate-changed", "authority-edge")
    val handoffs = HandoffPlan(source, digestOf(draft), Vector(handoff),
      Map("77" -> Vector.empty, "77.1" -> Vector("handoff-1")))
    val validation = ValidationPlan(source, digestOf(draft), Timing.EachPhase,
      "test", "sm-workflow", "existing", "77.1", Vector(
        ChildDeclaration("77", "repository", Vector("closure-77"), "77",
          Vector.empty, None, Some("77.1")),
        ChildDeclaration("77.1", "repository", Vector("closure-77.1"), "77.1",
          Vector("77"), Some("77"), None)))
    enumerated -> Evidence(enumerated.candidates.head.digest, draft,
      Map("a" -> 180, "b" -> 260), handoffs, validation,
      Vector(Edge("a", EdgeDisposition.CutOnly, "authority-edge")))
  }

  "SplitPhase candidate gate derivation" should {
    "verify overhead-aware validation and short-child evidence while retaining pending gates" in {
      val (enumerated, evidence) = fixture
      deriveC(enumerated, policy, evidence) match {
        case Consequence.Success(assessment) =>
          assessment.candidateDigest shouldBe enumerated.candidates.head.digest
          assessment.closure shouldBe Gate.Pending(
            "independent-child-closure-evidence-required")
          assessment.authority shouldBe Gate.Pending(
            "authority-equivalence-evidence-required")
          assessment.validation shouldBe a[Gate.Passed]
          assessment.shortChild shouldBe a[Gate.Passed]
          assessC(enumerated, Vector(assessment)) shouldBe Consequence.success(
            SplitPhaseCandidateAdmissibility.Outcome.EvidenceRequired(
              enumerated.consideredDigest,
              Vector(enumerated.candidates.head.digest), Vector.empty))
        case other => fail(s"Expected derived candidate gates: $other")
      }
    }

    "reject a draft whose child duration omits fixed Phase overhead" in {
      val (enumerated, evidence) = fixture
      val changed = evidence.draft.copy(children = evidence.draft.children.updated(0,
        evidence.draft.children.head.copy(expectedMinutes = 180)))
      deriveC(enumerated, policy, evidence.copy(draft = changed)) shouldBe
        a[Consequence.Failure[?]]
    }

    "derive closure and authority only from an exact source-bound contract" in {
      val (enumerated, evidence) = fixture
      deriveC(enumerated, policy,
        evidence.copy(sourceBoundary = Some(sourceBoundary))) match {
        case Consequence.Success(assessment) =>
          assessment.closure shouldBe a[Gate.Passed]
          assessment.authority shouldBe a[Gate.Passed]
          assessC(enumerated, Vector(assessment)) match {
            case Consequence.Success(
                _: SplitPhaseCandidateAdmissibility.Outcome.DurationRanked) => succeed
            case other => fail(s"Expected duration ranking: $other")
          }
        case other => fail(s"Expected source-bound gates: $other")
      }
    }

    "reject incomplete closure, forward dependencies, and ungranted authority" in {
      val (enumerated, evidence) = fixture
      val wrongCriteria = sourceBoundary.copy(items = sourceBoundary.items.updated(1,
        sourceBoundary.items(1).copy(closureCriteria = Vector("other"))))
      val forwardDependency = sourceBoundary.copy(items = sourceBoundary.items.updated(0,
        sourceBoundary.items.head.copy(prerequisiteItems = Vector("b"))))
      val ungrantedAuthority = sourceBoundary.copy(
        authorizedHandoffReferences = Set.empty)
      val ungrantedEdge = evidence.copy(semanticEdges = Vector(
        Edge("a", EdgeDisposition.CutOnly, "unknown-authority")))
      Vector(wrongCriteria, forwardDependency, ungrantedAuthority).foreach { boundary =>
        deriveC(enumerated, policy, evidence.copy(sourceBoundary = Some(boundary))) shouldBe
          a[Consequence.Failure[?]]
      }
      deriveC(enumerated, policy,
        ungrantedEdge.copy(sourceBoundary = Some(sourceBoundary))) shouldBe
          a[Consequence.Failure[?]]
    }

    "reject duplicate source closure criteria across child owners" in {
      val (enumerated, evidence) = fixture
      val duplicate = sourceBoundary.copy(items = sourceBoundary.items.updated(1,
        sourceBoundary.items(1).copy(closureCriteria = Vector("closure-77"))))
      deriveC(enumerated, policy, evidence.copy(sourceBoundary = Some(duplicate))) shouldBe
        a[Consequence.Failure[?]]
    }

    "reject a different partition policy or a candidate outside the considered set" in {
      val (enumerated, evidence) = fixture
      deriveC(enumerated, policy.copy(phaseOverheadMinutes = 0), evidence) shouldBe
        a[Consequence.Failure[?]]
      deriveC(enumerated, policy, evidence.copy(candidateDigest = "b" * 64)) shouldBe
        a[Consequence.Failure[?]]
    }

    "require the authority-explicit handoff for a sub-four-hour child" in {
      val (enumerated, evidence) = fixture
      val missing = evidence.handoffs.copy(handoffs = Vector.empty,
        incomingHandoffIdsByChild = Map("77" -> Vector.empty,
          "77.1" -> Vector.empty))
      deriveC(enumerated, policy, evidence.copy(handoffs = missing)) match {
        case Consequence.Success(assessment) =>
          assessment.shortChild shouldBe a[Gate.Rejected]
        case other => fail(s"Expected a candidate-local rejection: $other")
      }
    }
  }
}
