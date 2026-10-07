package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseDurationCalibration.{Outcome as Duration, Request as Calibration}
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.{Edge, EdgeDisposition}
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.{ProposalDraft, SourceInventory}

/** Application payload / semantic admission only. This is not a generic
  * Continuation, publication mechanism or authority decision engine.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseSemanticInputs {
  final case class KnownEstimate(workItemIdentity: String, minutes: Int, method: String)
  final case class Request(sourceSnapshotDigest: String, proposalDigest: String,
    contextDigest: String, sourceAuthorityReference: String,
    unresolvedWorkItems: Vector[SplitPhaseDurationCalibration.WorkItem],
    unresolvedEdges: Vector[Edge], knownEstimates: Vector[KnownEstimate])
  final case class NovelEstimate(workItemIdentity: String, minimumMinutes: Int,
    maximumMinutes: Int, assumptions: String, noveltyBasis: String)
  final case class BoundaryAssessment(afterItem: String, disposition: EdgeDisposition,
    authorityReference: String, rationale: String)
  final case class Judgment(sourceSnapshotDigest: String, proposalDigest: String,
    contextDigest: String, decision: String, rationale: String,
    novelEstimates: Vector[NovelEstimate], boundaries: Vector[BoundaryAssessment],
    evidenceReferences: Vector[String])

  final class Intent private[SplitPhaseSemanticInputs] (
    val request: Request,
    private[SplitPhaseSemanticInputs] val draft: ProposalDraft,
    private[SplitPhaseSemanticInputs] val evidence: SplitPhaseProposalValidation.Evidence,
    private[SplitPhaseSemanticInputs] val calibrated: Vector[Duration],
    private[SplitPhaseSemanticInputs] val boundary: SplitPhaseSourceBoundary.Contract
  )
  final class Admitted private[SplitPhaseSemanticInputs] (
    private[SplitPhaseSemanticInputs] val intent: Intent,
    val judgment: Judgment, val receiptDigest: String,
    val resolvedEdges: Vector[Edge], val novelMinutes: Map[String, Int]
  )
  sealed trait Outcome
  object Outcome {
    final case class Enriched(inputs: Admitted) extends Outcome
    final case class DecisionNeeded(rationale: String, evidenceReferences: Vector[String]) extends Outcome
  }

  /** Freeze only after source, proposal shape, deterministic inputs and
    * host-owned closure / authority evidence agree. Gather both gap kinds in
    * one request, even when the older assessment exposes only the first gap.
    */
  def prepareC(assessment: SplitPhaseEntryAssessment.Assessment,
    inventory: SourceInventory, draft: ProposalDraft,
    evidence: SplitPhaseProposalValidation.Evidence,
    boundary: SplitPhaseSourceBoundary.Contract): Consequence[Intent] =
    if (evidence == null || evidence.semanticInputs == null || evidence.semanticInputs.nonEmpty)
      Consequence.stateConflict("SplitPhase semantic intent requires unmodified host evidence")
    else for {
      shape <- SplitPhaseProposalPrecheck.precheckC(assessment, inventory, draft)
      _ <- shape match {
        case _: SplitPhaseProposalPrecheck.Outcome.ShapeReady => Consequence.unit
        case _ => Consequence.stateConflict("SplitPhase semantic work cannot resolve invocation authority")
      }
      pending <- SplitPhaseProposalValidation.validateC(draft, evidence)
      _ <- pending match {
        case _: SplitPhaseProposalValidation.Outcome.NovelWorkRequired => Consequence.unit
        case _: SplitPhaseProposalValidation.Outcome.SemanticBoundaryRequired => Consequence.unit
        case _ => Consequence.stateConflict("SplitPhase has no unresolved semantic inputs")
      }
      verified <- SplitPhaseSourceBoundary.verifyC(draft, evidence.validationOwnership,
        evidence.semanticHandoffs, evidence.semanticEdges, boundary)
      calibrated <- calibrateC(evidence.calibrationRequests)
      handoffs <- SplitPhaseSemanticHandoff.validateC(draft, evidence.semanticHandoffs)
      ownership <- SplitPhaseValidationOwnership.validateC(draft, evidence.validationOwnership)
      unresolved = evidence.calibrationRequests.zip(calibrated).collect {
        case (request, _: Duration.UnresolvedNovel) => request.workItem
      }
      edges = evidence.semanticEdges.filter(_.disposition == EdgeDisposition.Unresolved)
      known = evidence.calibrationRequests.zip(calibrated).collect {
        case (request, value: Duration.Known) => KnownEstimate(request.workItem.identity,
          value.estimateMinutes, value.method)
      }
      context = digest(Vector(assessment.source.identity, assessment.source.revision,
        assessment.source.digest, SplitPhaseProposalPrecheck.digestOf(draft),
        verified.closureDigest, verified.authorityDigest, handoffs.planDigest, ownership.planDigest,
        SplitPhasePartitionEnumeration.policyDigestOf(evidence.partitionPolicy)) ++
        inventory.occupiedPhaseIdentities.toVector.sorted ++
        evidence.calibrationRequests.flatMap(request => Vector(
          request.policyVersion, request.evidenceSnapshotDigest,
          SplitPhaseDurationCalibration.policyDigestOf(request.policy),
          request.workItem.identity, request.workItem.workClassKey,
          request.workItem.repositoryClass, request.workItem.normalizedUnits.toString,
          request.workItem.explicitBaselineMinutes.fold("")(_.toString))) ++
        calibrated.flatMap(value => Vector(value.policyVersion, value.evidenceSnapshotDigest,
          value.acceptedObservationIds.size.toString) ++ value.acceptedObservationIds ++
          Vector(value.exclusions.size.toString) ++ value.exclusions.flatMap(e =>
            Vector(e.observationId, e.reason)) ++ (value match {
            case known: Duration.Known => Vector("known", known.estimateMinutes.toString, known.method)
            case novel: Duration.UnresolvedNovel => Vector("novel", novel.reason)
          })) ++ boundary.authorizedHandoffReferences.toVector.sorted)
      request = Request(assessment.source.digest, SplitPhaseProposalPrecheck.digestOf(draft),
        context, boundary.sourceAuthorityReference, unresolved, edges, known)
    } yield new Intent(request, draft, evidence, calibrated, boundary)

  /** Pure semantic gate, intended to run before common claim consumption.
    * An enriched result covers exactly the frozen gaps, not known inputs.
    */
  def admitC(intent: Intent, result: Judgment): Consequence[Outcome] =
    if (intent == null || result == null || result.novelEstimates == null ||
        result.boundaries == null || result.evidenceReferences == null ||
        !name(result.rationale) || result.evidenceReferences.isEmpty ||
        !result.evidenceReferences.forall(name) ||
        result.evidenceReferences.distinct != result.evidenceReferences ||
        result.sourceSnapshotDigest != intent.request.sourceSnapshotDigest ||
        result.proposalDigest != intent.request.proposalDigest ||
        result.contextDigest != intent.request.contextDigest)
      Consequence.stateConflict("SplitPhase judgment differs from its frozen semantic intent")
    else result.decision match {
      case "decision-needed" if result.novelEstimates.isEmpty && result.boundaries.isEmpty =>
        Consequence.success(Outcome.DecisionNeeded(result.rationale, result.evidenceReferences))
      case "enriched" =>
        val permitted = intent.boundary.authorizedHandoffReferences + intent.boundary.sourceAuthorityReference
        val estimates = result.novelEstimates
        val edges = result.boundaries
        if (estimates.exists(e => e == null || e.minimumMinutes <= 0 ||
            e.maximumMinutes < e.minimumMinutes || e.maximumMinutes > 480 ||
            !name(e.assumptions) || !name(e.noveltyBasis)) ||
            estimates.map(_.workItemIdentity) != intent.request.unresolvedWorkItems.map(_.identity) ||
            edges.exists(e => e == null || e.disposition == null ||
              e.disposition == EdgeDisposition.Unresolved || !name(e.rationale) ||
              !permitted.contains(e.authorityReference)) ||
            edges.map(_.afterItem) != intent.request.unresolvedEdges.map(_.afterItem))
          Consequence.stateConflict("SplitPhase semantic result changes known inputs, coverage or authority")
        else {
          val resolved = intent.evidence.semanticEdges.map(edge =>
            edges.find(_.afterItem == edge.afterItem).fold(edge)(value =>
              Edge(value.afterItem, value.disposition, value.authorityReference)))
          val receipt = digest(Vector(result.sourceSnapshotDigest, result.proposalDigest,
            result.contextDigest, result.decision, result.rationale) ++
            estimates.flatMap(e => Vector(e.workItemIdentity, e.minimumMinutes.toString,
              e.maximumMinutes.toString, e.assumptions, e.noveltyBasis)) ++
            edges.flatMap(e => Vector(e.afterItem, e.disposition.toString, e.authorityReference, e.rationale)) ++
            result.evidenceReferences)
          Consequence.success(Outcome.Enriched(new Admitted(intent, result, receipt,
            resolved, estimates.map(e => e.workItemIdentity -> e.maximumMinutes).toMap)))
        }
      case _ => Consequence.stateConflict("SplitPhase semantic decision is unsupported")
    }

  /** Reuse cannot move semantic evidence onto a changed draft, host policy,
    * calibration request or edge set. Known calibration remains untouched.
    * The conservative upper range endpoint is an input to the existing checks,
    * never proposal acceptance or a partition selected by the worker.
    */
  def resolveC(admitted: Admitted, draft: ProposalDraft,
    evidence: SplitPhaseProposalValidation.Evidence, calibrated: Vector[Duration]):
    Consequence[(Vector[Duration], Vector[Edge])] =
    if (admitted == null || evidence == null ||
        draft != admitted.intent.draft ||
        evidence.copy(semanticInputs = None) != admitted.intent.evidence ||
        calibrated != admitted.intent.calibrated)
      Consequence.stateConflict("SplitPhase admitted semantic inputs are stale")
    else Consequence.success((evidence.calibrationRequests.zip(calibrated).map {
      case (request, value: Duration.UnresolvedNovel) => Duration.Known(
        admitted.novelMinutes(request.workItem.identity), "admitted-novel-range-upper-bound",
        value.policyVersion, value.evidenceSnapshotDigest, Vector.empty, value.exclusions)
      case (_, value) => value
    }, admitted.resolvedEdges))

  private def calibrateC(requests: Vector[Calibration]): Consequence[Vector[Duration]] =
    requests.foldLeft(Consequence.success(Vector.empty[Duration])) { (acc, request) => for {
      values <- acc
      value <- SplitPhaseDurationCalibration.calibrateC(request)
    } yield values :+ value }
  private def name(value: String): Boolean = value != null && value.trim.nonEmpty
  private def digest(fields: Vector[String]): String = MessageDigest.getInstance("SHA-256")
    .digest(fields.map(s => s"${s.length}:$s").mkString.getBytes(StandardCharsets.UTF_8))
    .map(byte => f"${byte & 0xff}%02x").mkString
}
