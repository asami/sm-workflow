package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Codec, Json}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.cncf.config.{ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.configuration.ConfigurationValue

/** Logical future-work policy only. Concrete assignments and availability belong
  * to the versioned caller adapter; this object never discovers or starts models.
  * @since   Nov.  2, 2026
  * @version Nov.  2, 2026
  */
private[smworkflow] object GoalPhaseDispatchPolicy {
  val revision = "standard-2026-11-01"
  final case class Capture(revision: String, role: String, candidates: Vector[String], placement: String,
    nature: Option[String], edges: Map[String, String]) derives Codec.AsObject
  val defaultEdges: Map[String, String] = Map("mechanical" -> "small", "small" -> "sol-medium",
    "sol-medium" -> "difficult", "ordinary" -> "difficult", "difficult" -> "hardest")
  val roles = Set("mechanical", "small", "ordinary", "difficult", "hardest", "sol-medium", "parent", "runtime")
  final case class Policy(revision: String, edges: Map[String, String]) derives Codec.AsObject
  val defaultPolicy = Policy(revision, defaultEdges)
  def policyValid(value: Policy): Boolean = value != null && value.revision == revision && value.edges != null &&
    value.edges.size <= 6 && value.edges.forall { case (a, b) =>
      roles(a) && roles(b) && defaultEdges.get(a).contains(b) && !Set("parent", "runtime")(a) && !Set("parent", "runtime")(b) && a != b &&
        _path(a, value.edges, Vector.empty).nonEmpty
    }
  private def _path(role: String, edges: Map[String, String], visited: Vector[String]): Option[Vector[String]] =
    if (visited.contains(role) || visited.size >= 16) None
    else edges.get(role).map(next => _path(next, edges, visited :+ role)).getOrElse(Some(visited :+ role))
  private def _decode(value: Json): Consequence[Policy] = if (!value.asObject.exists(_.keys.toSet == Set("revision", "edges")))
    Consequence.configurationInvalid("Logical fallback policy fields are missing or unknown")
    else value.as[Policy].fold(
    error => Consequence.configurationInvalid(error.message), p => if (policyValid(p)) Consequence.success(p)
      else Consequence.configurationInvalid("Logical fallback policy is cyclic, incomplete or references an invalid role"))
  val declaration: ComponentParameterKey[Policy] = ComponentParameterKey.optional(
    "sm-workflow.goal-phase.dispatch-policy", ComponentParameterDecoder {
      case ConfigurationValue.StringValue(value) if value == revision => Consequence.success(defaultPolicy)
      case ConfigurationValue.StringValue(value) => io.circe.parser.parse(value).fold(
        error => Consequence.configurationInvalid(error.message), _decode)
      case ConfigurationValue.ObjectValue(value) =>
        val rev = value.get("revision").collect { case ConfigurationValue.StringValue(v) => v }
        val edges = value.get("edges").collect { case ConfigurationValue.ObjectValue(v) => v }.map(_.toVector.collect {
          case (a, ConfigurationValue.StringValue(b)) => a -> b
        }.toMap)
        (rev, edges) match {
          case (Some(r), Some(e)) if value.keySet == Set("revision", "edges") &&
              value.get("edges").collect { case ConfigurationValue.ObjectValue(v) => v.size }.contains(e.size) && policyValid(Policy(r, e)) => Consequence.success(Policy(r, e))
          case _ => Consequence.configurationInvalid("Invalid logical fallback policy")
        }
      case _ => Consequence.configurationInvalid("Unknown logical dispatch-policy revision")
    })
  def chain(role: String, edges: Map[String, String] = defaultEdges): Vector[String] =
    _path(role, edges, Vector.empty).getOrElse(Vector.empty)
  def valid(value: Capture): Boolean = value != null && value.revision == revision && roles(value.role) &&
    policyValid(Policy(value.revision, value.edges)) &&
    value.candidates == (if (value.placement == "DELEGATED") chain(value.role, value.edges) else Vector(value.role)) &&
    Set("INLINE", "DELEGATED", "RUNTIME")(value.placement) && value.nature != null &&
    value.nature.forall(Set("mechanical", "parent-inline", "deterministic", "hardest")) &&
    (value.placement != "RUNTIME" || value.role == "runtime") && (value.placement != "INLINE" || value.role == "parent")

  def captureC(kind: String, classification: Option[GoalPhaseWorkClassification.Classification],
    requirement: ExecutionRequirement, isolated: Boolean, policy: Policy = defaultPolicy): Consequence[Capture] = {
    import GoalPhaseWorkClassification.*
    val nature = classification.flatMap(_.nature)
    val independent = requirement.reviewRequired || isolated ||
      requirement.disposition.exists(_.placement == ExecutionPlacement.Delegated)
    val role = if (nature.contains("deterministic")) "runtime"
      else if (nature.contains("parent-inline") && !independent) "parent"
      else if (nature.contains("hardest")) "hardest"
      else if (nature.contains("mechanical")) "mechanical"
      else if (classification.exists(c => c.kind == Kind.CODING && c.complexity == Complexity.SIMPLE)) "small"
      else if (Set(ReasoningLevel.Deep, ReasoningLevel.Critical)(requirement.reasoning) || kind == "ReviewPhase") "difficult"
      else "ordinary"
    val placement = if (role == "runtime") "RUNTIME" else if (role == "parent") "INLINE" else "DELEGATED"
    if (role == "runtime" && independent) Consequence.stateConflict("Deterministic work cannot replace an independent semantic assignment")
    else if (!policyValid(policy)) Consequence.configurationInvalid("Invalid configured logical fallback policy")
    else Consequence.success(Capture(policy.revision, role, if (placement == "DELEGATED") chain(role, policy.edges) else Vector(role), placement, nature, policy.edges))
  }
  def classification(request: GoalPhaseNativeWork.Request): Option[GoalPhaseWorkClassification.Classification] =
    if (request.kind == "ReviewPhase") request.selectedPlan.phaseReviewClassification
    else request.stepIdentity.flatMap(id => request.selectedPlan.steps.find(_.identity == id)).flatMap(_.workClassification).map { a =>
      if (request.kind == "ReviewStep") a.review else if (request.kind == "RepairStep") a.repair else a.implementation
    }
}
