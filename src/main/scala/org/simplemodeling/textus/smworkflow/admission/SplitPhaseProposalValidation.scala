package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseDurationCalibration.{Outcome as DurationOutcome, Request as DurationRequest}
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.{Edge as PartitionEdge, Outcome as PartitionOutcome, Policy as PartitionPolicy, WorkItem as PartitionWorkItem, enumerateC, policyDigestOf}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.{ProposalDraft, digestOf}

/** Combines the currently implemented deterministic proposal checks. A ready
  * result authorizes only the post-entry assessment state, not proposal
  * admission, planning-file writes, PREVIEW, or APPLY.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseProposalValidation {
  final case class Evidence(
    calibrationRequests: Vector[DurationRequest],
    semanticHandoffs: SplitPhaseSemanticHandoff.Plan,
    validationOwnership: SplitPhaseValidationOwnership.Plan,
    partitionPolicy: PartitionPolicy,
    semanticEdges: Vector[PartitionEdge],
    semanticInputs: Option[SplitPhaseSemanticInputs.Admitted] = None
  )

  sealed trait Outcome
  object Outcome {
    final case class Ready(evidenceDigest: String) extends Outcome
    final case class NovelWorkRequired(workItemIdentities: Vector[String]) extends Outcome
    final case class SemanticBoundaryRequired(afterItems: Vector[String]) extends Outcome
  }

  def validateC(draft: ProposalDraft, evidence: Evidence): Consequence[Outcome] =
    if (draft == null || draft.children == null ||
        draft.children.exists(child => child == null || child.ownedUnfinishedItems == null) ||
        evidence == null ||
        evidence.calibrationRequests == null || evidence.semanticHandoffs == null ||
        evidence.validationOwnership == null || !_validPolicy(evidence.partitionPolicy) ||
        evidence.semanticEdges == null || evidence.semanticInputs == null)
      Consequence.stateConflict("SplitPhase proposal validation evidence is incomplete")
    else {
      val ownedItems = draft.children.flatMap(_.ownedUnfinishedItems)
      val requests = evidence.calibrationRequests
      if (ownedItems.isEmpty || ownedItems.distinct != ownedItems ||
          requests.exists(r => r == null || r.workItem == null) ||
          requests.map(_.workItem.identity) != ownedItems ||
          requests.map(_.policyVersion).distinct.size != 1 ||
          requests.map(_.evidenceSnapshotDigest).distinct.size != 1 ||
          requests.map(_.policy).distinct.size != 1)
        Consequence.stateConflict("SplitPhase calibration requests do not cover exact owned work")
      else for {
        calibrated <- requests.foldLeft(Consequence.success(Vector.empty[DurationOutcome])) {
          (acc, request) => for {
            values <- acc
            value <- SplitPhaseDurationCalibration.calibrateC(request)
          } yield values :+ value
        }
        handoffs <- SplitPhaseSemanticHandoff.validateC(draft, evidence.semanticHandoffs)
        ownership <- SplitPhaseValidationOwnership.validateC(draft, evidence.validationOwnership)
        resolved <- evidence.semanticInputs match {
          case None => Consequence.success((calibrated, evidence.semanticEdges))
          case Some(inputs) => SplitPhaseSemanticInputs.resolveC(inputs, draft, evidence, calibrated)
        }
        result <- _resultC(draft, requests, resolved._1, evidence.partitionPolicy,
          resolved._2,
          evidence.semanticHandoffs, handoffs.planDigest, ownership.planDigest)
      } yield result match {
        case Outcome.Ready(value) if evidence.semanticInputs.nonEmpty =>
          Outcome.Ready(_digest(Vector(value, evidence.semanticInputs.get.receiptDigest)))
        case value => value
      }
    }

  private def _resultC(
    draft: ProposalDraft,
    requests: Vector[DurationRequest],
    calibrated: Vector[DurationOutcome],
    partitionPolicy: PartitionPolicy,
    semanticEdges: Vector[PartitionEdge],
    handoffPlan: SplitPhaseSemanticHandoff.Plan,
    handoffDigest: String,
    ownershipDigest: String
  ): Consequence[Outcome] = {
    val unresolved = requests.zip(calibrated).collect {
      case (request, _: DurationOutcome.UnresolvedNovel) => request.workItem.identity
    }
    if (unresolved.nonEmpty)
      Consequence.success(Outcome.NovelWorkRequired(unresolved))
    else {
      val known = calibrated.collect { case value: DurationOutcome.Known => value }
      val estimates = requests.map(_.workItem.identity).zip(known).toMap
      if (known.size != calibrated.size || draft.children.exists(child =>
          child.ownedUnfinishedItems.map(item => estimates(item).estimateMinutes.toLong).sum +
            partitionPolicy.phaseOverheadMinutes.toLong !=
            child.expectedMinutes.toLong))
        Consequence.stateConflict("SplitPhase child duration differs from calibrated work")
      else enumerateC(draft.sourceSnapshotDigest,
        requests.zip(known).map { case (request, result) =>
          PartitionWorkItem(request.workItem.identity, result.estimateMinutes)
        }, semanticEdges, partitionPolicy).flatMap {
        case PartitionOutcome.SemanticAssessmentRequired(afterItems) =>
          Consequence.success(Outcome.SemanticBoundaryRequired(afterItems))
        case _: PartitionOutcome.NoAdmissible =>
          Consequence.stateConflict("SplitPhase proposal has no admissible partition")
        case partition: PartitionOutcome.Enumerated =>
          partition.candidates.find(candidate =>
            candidate.children.size == draft.children.size &&
              candidate.children.zip(draft.children).forall { case (range, child) =>
                range.ownedItems == child.ownedUnfinishedItems &&
                  range.expectedMinutes == child.expectedMinutes
              }) match {
            case None => Consequence.stateConflict(
              "SplitPhase proposal is absent from the considered partition set")
            case Some(candidate) => SplitPhaseShortChildReview.reviewC(draft,
              estimates.view.mapValues(_.estimateMinutes).toMap, handoffPlan,
              partitionPolicy.phaseOverheadMinutes).map { reviewed =>
              val fields = Vector(draft.sourceSnapshotDigest, digestOf(draft),
                SplitPhaseDurationCalibration.policyDigestOf(requests.head.policy),
                requests.head.evidenceSnapshotDigest, policyDigestOf(partitionPolicy),
                partition.consideredDigest, candidate.digest,
                handoffDigest, ownershipDigest, reviewed.traceDigest) ++
                requests.zip(known).flatMap { case (request, result) =>
                  Vector(request.workItem.identity, request.workItem.workClassKey,
                    request.workItem.repositoryClass, request.workItem.normalizedUnits.toString,
                    request.workItem.explicitBaselineMinutes.fold("")(_.toString),
                    result.method, result.estimateMinutes.toString,
                    result.acceptedObservationIds.mkString(",")) ++
                    result.exclusions.flatMap(e => Vector(e.observationId, e.reason))
                }
              Outcome.Ready(_digest(fields))
            }
          }
        }
      }
    }

  private def _digest(fields: Vector[String]): String = {
    val bytes = fields.map(s => s"${s.length}:$s").mkString.getBytes(StandardCharsets.UTF_8)
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
  }

  private def _validPolicy(policy: PartitionPolicy): Boolean =
    policy != null && policy.version != null && policy.version.trim.nonEmpty &&
      policy.phaseOverheadMinutes >= 0 && policy.phaseOverheadMinutes < 480 &&
      policy.maximumCombinations >= 1 && policy.maximumCombinations <= 4096
}
