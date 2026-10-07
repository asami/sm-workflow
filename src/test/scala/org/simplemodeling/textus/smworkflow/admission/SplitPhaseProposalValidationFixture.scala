package org.simplemodeling.textus.smworkflow.admission

import org.simplemodeling.textus.smworkflow.admission.SplitPhaseDurationCalibration.*
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.{ProposalDraft, digestOf}

/**
 * @since   Sep. 27, 2026
 * @version Oct.  8, 2026
 */
private[admission] object SplitPhaseProposalValidationFixture {
  def evidenceFor(draft: ProposalDraft, phaseOverheadMinutes: Int = 0):
      SplitPhaseProposalValidation.Evidence = {
    val policy = Policy("fixture-duration-v1", 2, 100, 900,
      BigDecimal(2), BigDecimal(480), BigDecimal(2), 1, Map("scala" -> 0))
    val partitionPolicy = SplitPhasePartitionEnumeration.Policy(
      "fixture-partition-v1", phaseOverheadMinutes, 4096)
    val itemMinutes = draft.children.flatMap { child =>
      val ownedMinutes = child.expectedMinutes - phaseOverheadMinutes
      require(ownedMinutes > 0 && ownedMinutes % child.ownedUnfinishedItems.size == 0)
      child.ownedUnfinishedItems.map(_ ->
        (ownedMinutes / child.ownedUnfinishedItems.size))
    }
    val observations = itemMinutes.flatMap { case (item, minutes) =>
      Vector("a", "b").map { suffix =>
        val unsigned = Observation(s"$item-$suffix", item, "scala", Some(minutes),
          minutes, 1, 500, s"authority-$item-$suffix", "")
        unsigned.copy(evidenceDigest = observationDigestOf(unsigned))
      }
    }
    val snapshot = snapshotDigestOf(observations)
    val calibrations = itemMinutes.map { case (item, minutes) =>
      Request(policy.version, snapshot, policy, observations,
        WorkItem(item, item, "scala", 1, Some(minutes)))
    }
    val identities = draft.children.map(_.identity)
    val handoffs = SplitPhaseSemanticHandoff.Plan(draft.sourceSnapshotDigest,
      digestOf(draft), Vector.empty,
      identities.map(_ -> Vector.empty[String]).toMap)
    val ownership = SplitPhaseValidationOwnership.Plan(draft.sourceSnapshotDigest,
      digestOf(draft), SplitPhaseValidationOwnership.Timing.FinalOnly,
      "fixture-test", "fixture-driver", "inherit", identities.last,
      draft.children.zipWithIndex.map { case (child, index) =>
        val identity = child.identity
        SplitPhaseValidationOwnership.ChildDeclaration(
          identity, "fixture-repository",
          child.ownedUnfinishedItems.map(item => s"close-$item"), identities.last,
          identities.lift(index - 1).toVector,
          identities.lift(index - 1), identities.lift(index + 1))
      })
    val boundaries = draft.children.init.map(_.ownedUnfinishedItems.last).toSet
    val semanticEdges = itemMinutes.map(_._1).init.map { item =>
      SplitPhasePartitionEnumeration.Edge(item,
        if (boundaries.contains(item)) SplitPhasePartitionEnumeration.EdgeDisposition.CutOnly
        else SplitPhasePartitionEnumeration.EdgeDisposition.CrossOnly,
        s"authority-edge-$item")
    }
    SplitPhaseProposalValidation.Evidence(calibrations, handoffs, ownership,
      partitionPolicy, semanticEdges)
  }
}
