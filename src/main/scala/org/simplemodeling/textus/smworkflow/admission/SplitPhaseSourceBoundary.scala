package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhasePartitionEnumeration.Edge
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.ProposalDraft
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseValidationOwnership.Plan

/** A projection of the authoritative source snapshot, supplied by the host's
  * source-document reader rather than by the proposed split. A digest alone or
  * a draft's independentlyClosable flag cannot satisfy either gate.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseSourceBoundary {
  final case class Item(
    identity: String,
    closureCriteria: Vector[String],
    prerequisiteItems: Vector[String],
    authorityReference: String
  )

  final case class Contract(
    sourceSnapshotDigest: String,
    sourcePhaseIdentity: String,
    sourceAuthorityReference: String,
    items: Vector[Item],
    authorizedHandoffReferences: Set[String]
  )

  final case class Verified(closureDigest: String, authorityDigest: String)

  def verifyC(draft: ProposalDraft, validation: Plan,
              handoffs: SplitPhaseSemanticHandoff.Plan,
              semanticEdges: Vector[Edge],
              contract: Contract): Consequence[Verified] =
    if (!_complete(draft, validation, handoffs, semanticEdges, contract))
      Consequence.stateConflict("SplitPhase source boundary contract is incomplete")
    else {
      val sourceItems = contract.items.map(_.identity)
      val owned = draft.children.flatMap(_.ownedUnfinishedItems)
      val itemByIdentity = contract.items.map(item => item.identity -> item).toMap
      val ownerIndex = draft.children.zipWithIndex.flatMap { case (child, index) =>
        child.ownedUnfinishedItems.map(_ -> index)
      }.toMap
      val criteria = contract.items.flatMap(_.closureCriteria)
      val exactCoverage = sourceItems == owned && sourceItems.distinct == sourceItems &&
        criteria.distinct == criteria
      val closureMatches = exactCoverage && draft.children.zip(validation.children).forall {
        case (child, declaration) =>
          val expected = child.ownedUnfinishedItems.flatMap(itemByIdentity(_).closureCriteria)
          declaration.identity == child.identity && declaration.closureCriteria == expected
      }
      val dependenciesCloseBackward = exactCoverage && contract.items.forall(item =>
        item.prerequisiteItems.forall(prerequisite =>
          ownerIndex.get(prerequisite).exists(_ <= ownerIndex(item.identity)) &&
            sourceItems.indexOf(prerequisite) < sourceItems.indexOf(item.identity)))
      val permittedReferences = contract.authorizedHandoffReferences +
        contract.sourceAuthorityReference
      val authorityMatches = contract.items.forall(item =>
        permittedReferences.contains(item.authorityReference)) &&
        handoffs.handoffs.forall(handoff =>
          permittedReferences.contains(handoff.authorityReference)) &&
        semanticEdges.map(_.afterItem) == sourceItems.init &&
        semanticEdges.forall(edge =>
          permittedReferences.contains(edge.authorityReference))
      if (contract.sourceSnapshotDigest != draft.sourceSnapshotDigest ||
          contract.sourcePhaseIdentity != draft.sourcePhaseIdentity ||
          contract.sourceAuthorityReference != draft.sourceAuthorityReference ||
          !exactCoverage || !closureMatches ||
          !dependenciesCloseBackward || !authorityMatches)
        Consequence.stateConflict(
          "SplitPhase closure or authority differs from the authoritative source boundary")
      else {
        val common = Vector(contract.sourceSnapshotDigest,
          contract.sourcePhaseIdentity, contract.sourceAuthorityReference)
        val closure = common ++ contract.items.flatMap(item =>
          Vector(item.identity, item.closureCriteria.size.toString) ++
            item.closureCriteria ++ Vector(item.prerequisiteItems.size.toString) ++
            item.prerequisiteItems) ++ validation.children.flatMap(child =>
          Vector(child.identity, child.closureCriteria.size.toString) ++
            child.closureCriteria)
        val authority = common ++ contract.items.flatMap(item =>
          Vector(item.identity, item.authorityReference)) ++
          contract.authorizedHandoffReferences.toVector.sorted ++
          handoffs.handoffs.map(_.authorityReference) ++
          semanticEdges.flatMap(edge => Vector(edge.afterItem,
            edge.disposition.toString, edge.authorityReference))
        Consequence.success(Verified(_digest(closure), _digest(authority)))
      }
    }

  private def _complete(draft: ProposalDraft, validation: Plan,
                        handoffs: SplitPhaseSemanticHandoff.Plan,
                        semanticEdges: Vector[Edge],
                        contract: Contract): Boolean =
    draft != null && draft.children != null &&
      draft.children.forall(child => child != null &&
        child.ownedUnfinishedItems != null) && validation != null &&
      validation.children != null && validation.children.size == draft.children.size &&
      validation.children.forall(child => child != null &&
        child.closureCriteria != null) && handoffs != null &&
      handoffs.handoffs != null && handoffs.handoffs.forall(_ != null) &&
      semanticEdges != null && semanticEdges.forall(edge =>
        edge != null && _name(edge.afterItem) && edge.disposition != null &&
          _name(edge.authorityReference)) && contract != null &&
      _sha(contract.sourceSnapshotDigest) && _name(contract.sourcePhaseIdentity) &&
      _name(contract.sourceAuthorityReference) && contract.items != null &&
      contract.items.nonEmpty && contract.items.forall(item =>
        item != null && _name(item.identity) && item.closureCriteria != null &&
          item.closureCriteria.nonEmpty && item.closureCriteria.forall(_name) &&
          item.prerequisiteItems != null && item.prerequisiteItems.forall(_name) &&
          item.prerequisiteItems.distinct == item.prerequisiteItems &&
          _name(item.authorityReference)) &&
      contract.authorizedHandoffReferences != null &&
      contract.authorizedHandoffReferences.forall(_name)

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
  private def _sha(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")
  private def _digest(fields: Vector[String]): String = {
    val bytes = fields.map(value => s"${value.length}:$value")
      .mkString.getBytes(StandardCharsets.UTF_8)
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
  }
}
