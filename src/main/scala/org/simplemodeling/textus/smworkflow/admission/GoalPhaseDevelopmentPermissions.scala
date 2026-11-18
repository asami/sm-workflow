package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import GoalPhaseDependencyScope as D
import GoalPhaseOrderedProgression as O

/** Phase permission is an authorization source for ordinary admission. It is
  * neither a fabricated Human answer nor a provider/effect execution grant.
  * @since   Nov. 12, 2026
  * @version Nov. 18, 2026
  */
private[smworkflow] object GoalPhaseDevelopmentPermissions {
  final case class Authority(plan: ContextReference, allowDependencyRepair: Boolean, allowSharedCliUpdate: Boolean,
    managedEdge: Option[ManagedDevelopment.Edge], mode: Option[DependencyDevelopmentDelivery.Binding])
  object Authority {
    import io.circe.generic.auto.*
    given Encoder[Authority] = io.circe.generic.semiauto.deriveEncoder
    given Decoder[Authority] = io.circe.generic.semiauto.deriveDecoder
  }
  final case class Assessment(status: String, reason: String, authority: Authority)
  private val localOperations = Set("edit-source", "validate", "independent-review", "publish-local", "install-runtime")
  def capture(plan: O.Plan, proposal: D.Proposal, configured: Option[ManagedDevelopmentRuntime.Policy]): Authority = {
    val edge = configured.toVector.flatMap(_.definition.edges).find(e => e.provider == proposal.dependency && e.consumer == proposal.requester)
    val mode = edge.flatMap(e => configured.toVector.flatMap(_.delivery.toVector.flatten).find(_.edge == e.identity))
    Authority(ContextReference(plan.identity, plan.revision), plan.allowDependencyRepair.contains(true),
      plan.allowSharedCliUpdate.contains(true), edge, mode)
  }
  def assessC(plan: O.Plan, proposal: D.Proposal, authority: Authority): Consequence[Assessment] = for {
    _ <- O.validatePlanC(plan)
    _ <- D.validateProposalC(proposal)
    _ <- if (authority != null && authority.plan == ContextReference(plan.identity, plan.revision) &&
      authority.allowDependencyRepair == plan.allowDependencyRepair.contains(true) &&
      authority.allowSharedCliUpdate == plan.allowSharedCliUpdate.contains(true) && authority.managedEdge != null &&
      authority.managedEdge.forall(e => e.provider == proposal.dependency && e.consumer == proposal.requester) &&
      authority.mode != null && authority.mode.forall(m => authority.managedEdge.exists(_.identity == m.edge) &&
        Set(DependencyDevelopmentDelivery.fixed, DependencyDevelopmentDelivery.assignedChat, DependencyDevelopmentDelivery.subtree)(m.mode)))
      Consequence.unit else Consequence.stateConflict("Phase permission source crosses its retained Plan or dependency graph")
    boundary = proposal.authorizationBoundary
    fixed = authority.mode.exists(_.mode == DependencyDevelopmentDelivery.fixed)
    operations = boundary.toVector.flatMap(_.permittedOperations).toSet
    status = if (fixed) "dependency-change-required"
      else if (boundary.isEmpty || !boundary.exists(_.purpose == authority.plan)) "decision-needed"
      else if (!operations.subsetOf(localOperations)) "decision-needed"
      else if (operations.contains("install-runtime") && !authority.allowSharedCliUpdate) "human-shared-cli-update"
      else if (authority.managedEdge.isEmpty && operations.contains("edit-source") && !authority.allowDependencyRepair) "human-dependency-repair"
      else "admitted"
    reason = status match {
      case "dependency-change-required" => "Explicit Fixed dependency retains its restriction; user direction is required"
      case "human-shared-cli-update" => "allowSharedCliUpdate is false; identify the actual shared CLI target and obtain genuine same-Phase Human direction"
      case "human-dependency-repair" => "allowDependencyRepair is false for this external dependency; obtain genuine same-Phase Human direction"
      case "decision-needed" => "Concrete same-Phase purpose, local operations or semantic direction remains unresolved; remote/push/configuration effects are separate"
      case _ => "Existing managed membership or admitted Phase policy covers this necessary local work; configured owner, concrete grants, checks, scoped publication and acceptance still apply"
    }
  } yield Assessment(status, reason, authority)

  def instructions(plan: O.Plan): String =
    " Phase development policy retained in this selected Plan: allowDependencyRepair=" + plan.allowDependencyRepair.contains(true) +
      ", allowSharedCliUpdate=" + plan.allowSharedCliUpdate.contains(true) +
      ". Omission is false independently. Incorporated dependency projects use their configured development owner without another external-repair decision. " +
      "External repair and shared CLI update use their respective enabled policy only within the same Phase purpose; neither enables the other, remote publication or push. " +
      "Explicit Fixed/exclusions remain restrictions. Supply a concrete dependencyProposal authorizationBoundary whose purpose names this selected Plan and whose operations identify necessary local effects. " +
      "Runtime can admit that unchanged purpose through a distinct Phase-policy receipt, never a fabricated Human answer. A disabled needed property uses genuine Human AdoptPlanRevision of the complete same-purpose Plan to enable only the selected properties; rejection, defer and an unanswered proposal leave them false. " +
      "Scope-local artifact/tool use is separate from shared installation; install-runtime in a dependency proposal means a shared host installation and always observes allowSharedCliUpdate."
}
