package org.simplemodeling.textus.smworkflow.admission

import scala.util.control.NonFatal

import org.goldenport.Consequence
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*

/** Application-owned selection, not a scheduler or Workflow progression guard.
  * The Host supplies the policy and attests completion; selection alone proves
  * neither execution nor permission. CNCF retains claim and resume authority.
  * @since   Sep. 28, 2026
  * @version Oct.  8, 2026
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

  final case class Policy(version: String, mappings: Vector[Mapping]) {
    def validateC: Consequence[Policy] =
      if (!_name(version) || mappings == null || mappings.isEmpty ||
          mappings.exists(x => x == null || x.reasoning == null || !_profile(x.profile)) ||
          mappings.map(_.reasoning).distinct.size != mappings.size)
        Consequence.configurationInvalid("Worker mapping policy is incomplete or ambiguous")
      else Consequence.success(this)
  }

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
    else for {
      admitted <- policy.validateC
      wire <- WorkflowWorkOrderJsonV1.encodeC(issued, inputCodec)
      decoded <- WorkflowWorkOrderJsonV1.decodeC(wire, inputCodec)
      _ <- if (decoded == issued) Consequence.unit
        else Consequence.stateConflict("Worker mapping requires a lossless WorkOrder projection")
      selection <- issued.current match {
        case work: WorkflowContinuation.WorkOrder[?] =>
          val requirement = work.requirement
          val capabilities = requirement.capabilities.map(_.identity)
          admitted.mappings.find(_.reasoning == requirement.reasoning) match {
            case Some(mapping) if capabilities.distinct.size == capabilities.size &&
                capabilities.forall(mapping.profile.capabilities.contains) &&
                mapping.profile.risks.contains(requirement.risk.value) &&
                (!requirement.reviewRequired || mapping.profile.reviewCapable) =>
              Consequence.success(new Dispatch(issued, admitted, mapping.profile))
            case _ => Consequence.stateConflict("No compatible worker mapping for the issued requirement")
          }
        case _ => Consequence.stateConflict("Worker mapping requires WORK_ORDER")
      }
    } yield selection
  }

  /** Normalize a Host-attested external completion, never a planned dispatch.
    * The existing application Result adapter still decides semantic admission.
    */
  def normalizeC[W, R](
    dispatch: Dispatch[W],
    completion: SkillWorkResult[R]
  ): Consequence[ContinuationResult[R]] =
    if (dispatch == null || completion == null ||
        completion.selectedWorkerProfile != dispatch.profile.identity ||
        completion.mappingPolicyVersion != dispatch.policy.version ||
        completion.workerIdentity == null || !completion.workerIdentity.exists(_name) ||
        completion.modelIdentity != Some(dispatch.profile.modelIdentity))
      Consequence.stateConflict("External completion differs from the frozen worker selection")
    else new SkillWorkResultNormalizerV1[W, R].admitC(dispatch.issued, completion)

  private def _profile(value: Profile): Boolean =
    value != null && _name(value.identity) && _name(value.modelIdentity) &&
      value.capabilities != null && value.capabilities.forall(_name) &&
      value.risks != null && value.risks.nonEmpty && value.risks.forall(_name)

  private def _name(value: String): Boolean = value != null && value.trim.nonEmpty

  private def _safe[A](body: => Consequence[A]): Consequence[A] =
    try body catch {
      case NonFatal(_) => Consequence.stateConflict("Worker mapping WorkOrder projection is invalid")
    }
}
