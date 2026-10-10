package org.simplemodeling.textus.smworkflow.admission

import scala.util.control.NonFatal

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Application-owned selection, not a scheduler or Workflow progression guard.
  * The Host supplies the policy; selection and returned diagnostics prove
  * neither execution nor permission. CNCF retains claim and resume authority.
  * @since   Sep. 28, 2026
  * @version Oct.  9, 2026
  */
private[smworkflow] object WorkflowWorkerMapping {
  final case class Profile(
    identity: String,
    modelIdentity: String,
    capabilities: Set[String],
    risks: Set[String],
    reviewCapable: Boolean
  )

  final case class Mapping(reasoning: ReasoningLevel, profile: Profile)

  final case class Policy(version: String, mappings: Vector[Mapping])

  /** Frozen application selection. No completion evidence is synthesized here. */
  final class Dispatch[W] private[WorkflowWorkerMapping] (
    val issued: WorkflowInteraction[W, Nothing],
    val policy: Policy,
    val profile: Profile
  )

  def selectC[W](
    issued: WorkflowInteraction[W, Nothing],
    policy: Policy,
    inputCodec: WorkflowResultJsonV1.PayloadCodec[W]
  ): Consequence[Dispatch[W]] = _safe {
    if (policy == null)
      Consequence.configurationInvalid("Worker mapping policy is missing")
    else issued.current match {
      case work: WorkflowContinuation.WorkOrder[?] =>
        val requirement = work.requirement
        val capabilities = requirement.capabilities.map(_.identity)
        // Existing ordered lookup, not a certificate for the entire policy.
        // Do not fall through an incompatible selected row to another profile.
        policy.mappings.find(x => x != null && x.reasoning != null &&
            x.reasoning == requirement.reasoning) match {
          case Some(mapping) if capabilities.forall(mapping.profile.capabilities.contains) &&
              mapping.profile.risks.contains(requirement.risk.value) &&
              (!requirement.reviewRequired || mapping.profile.reviewCapable) =>
            Consequence.success(new Dispatch(issued, policy, mapping.profile))
          case _ => Consequence.stateConflict("No compatible worker mapping for the issued requirement")
        }
      case _ => Consequence.stateConflict("Worker mapping requires WORK_ORDER")
    }
  }

  /** Convert returned completion data, never a planned dispatch or execution proof.
    * Native applicability and application judgment belong to the consuming Operation.
    */
  def normalizeC[W, R](
    dispatch: Dispatch[W],
    completion: SkillWorkResult[R]
  ): Consequence[ContinuationResult[R]] =
    if (dispatch == null || completion == null)
      Consequence.stateConflict("External completion conversion inputs are unavailable")
    else new SkillWorkResultNormalizerV1[W, R].admitC(dispatch.issued, completion)

  private def _safe[A](body: => Consequence[A]): Consequence[A] =
    try body catch {
      case NonFatal(_) => Consequence.stateConflict("Worker mapping selection input cannot be consumed")
    }
}
