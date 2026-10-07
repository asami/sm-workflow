package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseEntryAssessment.Assessment

/** Deterministic shape precheck for a referenced split proposal. Success is not
  * proposal admission: calibration, handoffs, validation ownership, and the
  * document projection must still be checked before PREVIEW or APPLY.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseProposalPrecheck {
  final case class SourceInventory(
    sourcePhaseIdentity: String,
    sourceSnapshotDigest: String,
    orderedUnfinishedItems: Vector[String],
    occupiedPhaseIdentities: Set[String]
  )

  final case class Child(
    identity: String,
    ownedUnfinishedItems: Vector[String],
    expectedMinutes: Int,
    independentlyClosable: Boolean
  )

  final case class ProposalDraft(
    identity: String,
    sourcePhaseIdentity: String,
    sourceAuthorityReference: String,
    sourceSnapshotDigest: String,
    children: Vector[Child]
  )

  sealed trait Outcome
  object Outcome {
    final case class ShapeReady(proposalDigest: String) extends Outcome
    final case class DecisionRequired(reason: String) extends Outcome
  }

  def precheckC(
    assessment: Assessment,
    inventory: SourceInventory,
    draft: ProposalDraft
  ): Consequence[Outcome] =
    if (!_complete(assessment, inventory, draft))
      Consequence.stateConflict("SplitPhase proposal precheck input is incomplete")
    else {
      val source = assessment.source
      val candidate = assessment.proposalCandidate.get
      if (inventory.sourcePhaseIdentity != source.sourcePhaseIdentity ||
          inventory.sourceSnapshotDigest != source.digest ||
          draft.identity != candidate.identity ||
          draft.sourcePhaseIdentity != source.sourcePhaseIdentity ||
          draft.sourceAuthorityReference != source.authorityReference ||
          draft.sourceSnapshotDigest != source.digest ||
          candidate.digest != digestOf(draft))
        Consequence.stateConflict("SplitPhase proposal bytes or source inventory differ from Start evidence")
      else if (draft.children.size < 2 ||
          draft.children.exists(child => child.ownedUnfinishedItems.isEmpty ||
            child.expectedMinutes <= 0 || child.expectedMinutes > 480 ||
            !child.independentlyClosable) ||
          draft.children.flatMap(_.ownedUnfinishedItems) != inventory.orderedUnfinishedItems)
        Consequence.stateConflict("SplitPhase proposal is not a contiguous, exact, closable partition")
      else if (!source.sourcePhaseIdentity.matches("[0-9]+"))
        Consequence.success(Outcome.DecisionRequired("nested-or-unknown-phase-numbering"))
      else {
        val expected = Vector(source.sourcePhaseIdentity) ++
          (1 until draft.children.size).map(index => s"${source.sourcePhaseIdentity}.$index")
        if (draft.children.map(_.identity) != expected)
          Consequence.stateConflict("SplitPhase child identities do not follow source suffix order")
        else if (draft.children.tail.exists(child =>
          inventory.occupiedPhaseIdentities.contains(child.identity)))
          Consequence.success(Outcome.DecisionRequired("child-phase-identity-collision"))
        else Consequence.success(Outcome.ShapeReady(candidate.digest))
      }
    }

  /** The digest binds the ordered draft fields, not a caller-supplied claim. */
  def digestOf(draft: ProposalDraft): String = {
    val fields = Vector(
      draft.identity, draft.sourcePhaseIdentity, draft.sourceAuthorityReference,
      draft.sourceSnapshotDigest, draft.children.size.toString
    ) ++ draft.children.flatMap(child =>
      Vector(child.identity, child.expectedMinutes.toString,
        child.independentlyClosable.toString, child.ownedUnfinishedItems.size.toString) ++
        child.ownedUnfinishedItems)
    val bytes = fields.map(_framed).mkString.getBytes(StandardCharsets.UTF_8)
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
  }

  private def _complete(
    assessment: Assessment,
    inventory: SourceInventory,
    draft: ProposalDraft
  ): Boolean =
    assessment != null && assessment.source != null &&
      _name(assessment.source.sourcePhaseIdentity) &&
      _name(assessment.source.authorityReference) &&
      _digest(assessment.source.digest) &&
      assessment.proposalCandidate != null &&
      assessment.proposalCandidate.exists(candidate =>
        candidate != null && _name(candidate.identity) && _digest(candidate.digest)) &&
      inventory != null && draft != null &&
      _name(inventory.sourcePhaseIdentity) && _digest(inventory.sourceSnapshotDigest) &&
      inventory.orderedUnfinishedItems != null &&
      inventory.orderedUnfinishedItems.nonEmpty &&
      inventory.orderedUnfinishedItems.forall(_name) &&
      inventory.orderedUnfinishedItems.distinct.size == inventory.orderedUnfinishedItems.size &&
      inventory.occupiedPhaseIdentities != null &&
      inventory.occupiedPhaseIdentities.forall(_name) &&
      _name(draft.identity) && _name(draft.sourcePhaseIdentity) &&
      _name(draft.sourceAuthorityReference) && _digest(draft.sourceSnapshotDigest) &&
      draft.children != null && draft.children.forall(child =>
        child != null && _name(child.identity) && child.ownedUnfinishedItems != null &&
          child.ownedUnfinishedItems.forall(_name))

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
  private def _digest(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")
  private def _framed(value: String): String = s"${value.length}:$value"
}
