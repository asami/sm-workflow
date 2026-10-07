package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import org.goldenport.Consequence
import org.simplemodeling.textus.smworkflow.admission.SplitPhaseProposalPrecheck.{ProposalDraft, digestOf}

/** Deterministic validation-owner and serial-chain check for a shape-ready
  * proposal. This is one input to proposal admission, not the whole admission
  * decision or authority to write planning documents.
  * @since   Sep. 27, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object SplitPhaseValidationOwnership {
  enum Timing {
    case FinalOnly, EachPhase
  }

  final case class ChildDeclaration(
    identity: String,
    repositoryIdentity: String,
    closureCriteria: Vector[String],
    validationOwner: String,
    dependencies: Vector[String],
    predecessor: Option[String],
    successor: Option[String]
  )

  final case class Plan(
    sourceSnapshotDigest: String,
    proposalDigest: String,
    timing: Timing,
    method: String,
    driver: String,
    bootstrapPolicy: String,
    finalValidationOwner: String,
    children: Vector[ChildDeclaration]
  )

  final case class Validated(planDigest: String)

  def validateC(draft: ProposalDraft, plan: Plan): Consequence[Validated] =
    if (!_complete(draft, plan))
      Consequence.stateConflict("SplitPhase validation ownership declaration is incomplete")
    else if (plan.sourceSnapshotDigest != draft.sourceSnapshotDigest ||
        plan.proposalDigest != digestOf(draft))
      Consequence.stateConflict("SplitPhase validation ownership differs from proposal evidence")
    else {
      val identities = draft.children.map(_.identity)
      val declarations = plan.children
      val finalOwner = identities.last
      if (identities.distinct != identities ||
          declarations.map(_.identity) != identities ||
          plan.finalValidationOwner != finalOwner ||
          declarations.exists(child => child.closureCriteria.distinct != child.closureCriteria ||
            child.dependencies.distinct != child.dependencies ||
            child.closureCriteria.isEmpty) ||
          declarations.zipWithIndex.exists { case (child, index) =>
            child.predecessor != identities.lift(index - 1) ||
              child.successor != identities.lift(index + 1) ||
              child.dependencies.exists(dependency =>
                !identities.take(index).contains(dependency))
          })
        Consequence.stateConflict("SplitPhase child validation and dependency chain is inconsistent")
      else plan.timing match {
        case Timing.FinalOnly if
            declarations.map(_.repositoryIdentity).distinct.size == 1 &&
            declarations.forall(_.validationOwner == finalOwner) &&
            declarations.zipWithIndex.forall { case (child, index) =>
              index == 0 || child.dependencies.contains(identities(index - 1))
            } => Consequence.success(Validated(_digest(plan)))
        case Timing.EachPhase if declarations.forall(child =>
            child.validationOwner == child.identity) =>
          Consequence.success(Validated(_digest(plan)))
        case _ => Consequence.stateConflict(
          "SplitPhase validation timing has no admissible owner or repository chain")
      }
    }

  private def _complete(draft: ProposalDraft, plan: Plan): Boolean =
    draft != null && _name(draft.identity) && _name(draft.sourcePhaseIdentity) &&
      _name(draft.sourceAuthorityReference) &&
      draft.children != null && draft.children.size >= 2 &&
      draft.children.forall(child => child != null && _name(child.identity) &&
        child.ownedUnfinishedItems != null && child.ownedUnfinishedItems.forall(_name)) &&
      _sha(draft.sourceSnapshotDigest) && plan != null &&
      _sha(plan.sourceSnapshotDigest) && _sha(plan.proposalDigest) &&
      plan.timing != null && _name(plan.method) && _name(plan.driver) &&
      _name(plan.bootstrapPolicy) && _name(plan.finalValidationOwner) &&
      plan.children != null && plan.children.size == draft.children.size &&
      plan.children.forall(child =>
        child != null && _name(child.identity) && _name(child.repositoryIdentity) &&
          child.closureCriteria != null && child.closureCriteria.forall(_name) &&
          _name(child.validationOwner) && child.dependencies != null &&
          child.dependencies.forall(_name) && child.predecessor != null &&
          child.predecessor.forall(_name) && child.successor != null &&
          child.successor.forall(_name))

  private def _digest(plan: Plan): String = {
    val fields = Vector(
      plan.sourceSnapshotDigest, plan.proposalDigest, plan.timing.toString,
      plan.method, plan.driver, plan.bootstrapPolicy, plan.finalValidationOwner,
      plan.children.size.toString
    ) ++ plan.children.flatMap(child =>
      Vector(child.identity, child.repositoryIdentity, child.validationOwner,
        child.predecessor.getOrElse(""), child.successor.getOrElse(""),
        child.closureCriteria.size.toString) ++ child.closureCriteria ++
        Vector(child.dependencies.size.toString) ++ child.dependencies)
    val bytes = fields.map(value => s"${value.length}:$value").mkString
      .getBytes(StandardCharsets.UTF_8)
    MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
  }

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty
  private def _sha(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")
}
