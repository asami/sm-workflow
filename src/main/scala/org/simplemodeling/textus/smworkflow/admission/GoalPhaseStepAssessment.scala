package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeValidation as N
import WorkflowSbtValidation as T

/** Small PLAN-owned Step policy over actual native validation facts.
  * No provider selection, effect authority or replacement validation engine.
  * @since   Oct. 23, 2026
  * @version Oct. 23, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseStepAssessment {
  final case class Observation(identity: String, source: ContextReference, question: String,
    timing: String, reason: String, plannedStep: Option[String])
  final case class Policy(identity: String, kind: String, detailedReview: String,
    scalaLint: String, carLint: String, observations: Vector[Observation])
  final case class Result(policy: Policy, candidate: O.Snapshot, validationReference: String,
    checks: Vector[String], branch: String, reason: String, triage: String, review: String)
  final case class Admission(reference: String, assessment: Result)

  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  given Encoder[Observation] = deriveEncoder
  given Decoder[Observation] = deriveDecoder
  given Encoder[Policy] = deriveEncoder
  given Decoder[Policy] = deriveDecoder
  given Encoder[Result] = deriveEncoder
  given Decoder[Result] = deriveDecoder
  given Encoder[Admission] = deriveEncoder
  given Decoder[Admission] = deriveDecoder

  private def policyShape(policy: Policy): Boolean = try {
    policy != null && text(policy.identity) && Set("code", "documentation")(policy.kind) &&
      Set("required", "conditional")(policy.detailedReview) && policy.scalaLint == "off" && policy.carLint == "off" &&
      policy.observations != null && policy.observations.size <= 64 &&
      policy.observations.forall(o => o != null && text(o.identity) && o.source != null &&
        text(o.source.identity) && text(o.source.revision) && text(o.question) && text(o.reason) &&
        Set("immediate", "release")(o.timing) && o.plannedStep != null && o.plannedStep.forall(text) &&
        (o.timing != "release" || o.plannedStep.nonEmpty)) &&
      policy.observations.map(_.identity).distinct.size == policy.observations.size
  } catch { case scala.util.control.NonFatal(_) => false }

  def validateC(policy: Policy, step: O.Step, plan: O.Plan): Consequence[Unit] = {
    val valid = policyShape(policy) && step != null && plan != null && plan.steps != null &&
      policy.observations.forall(_.plannedStep.forall(id =>
        plan.steps.dropWhile(_.identity != step.identity).drop(1).exists(_.identity == id)))
    if (valid) Consequence.unit else invalid("PLAN Step policy or observation deferral is invalid")
  }

  /** Only a complete successful typed batch can reach semantic assessment.
    * Missing, uncertain and failed results keep their existing owning route.
    */
  def assessC(controller: O.State, payload: N.Payload, reference: String): Consequence[Result] = for {
    _ <- if (controller != null && payload != null && controller.cursor < controller.plan.steps.size &&
        controller.stage == O.Stage.Validation && controller.candidate.contains(payload.candidate) && text(reference))
      Consequence.unit else invalid("assessment has no exact current Step candidate")
    step = controller.plan.steps(controller.cursor)
    policy <- step.actionPolicy.map(Consequence.success).getOrElse(invalid("Step has no selected assessment policy"))
    _ <- validateC(policy, step, controller.plan)
    _ <- if (payload.outcome.contains("passed") && payload.selected.map(_.identity) == step.checks &&
        payload.receipts.size == payload.selected.size && payload.receipts.zip(payload.selected).forall((r, c) =>
          r != null && r.identity == c.identity && RepositorySyncNativeMergeValidation.productSuccessful(r, c.kind))) Consequence.unit
      else invalid("assessment requires the complete actual successful selected checks")
    _ <- if (policy.kind != "code" || (payload.selected.exists(_.kind == T.Kind.Compile) &&
        payload.selected.zip(payload.receipts).exists((c, r) => c.kind == T.Kind.TestOnly &&
          r.sbt.flatMap(_.selection).exists(s => s.completedSuites > 0 && s.testsRun > 0)))) Consequence.unit
      else invalid("code admission requires compilation and actual nonempty focused behavioral tests")
    branch = if (policy.detailedReview == "required" || policy.observations.exists(_.timing == "immediate")) "detailed-review"
      else "admit"
    reason = branch match {
      case "detailed-review" if policy.detailedReview == "required" => "PLAN requires independent detailed review; triage is not selected"
      case "detailed-review" => "An unresolved immediate observation retains the existing independent judgment route; conditional triage is a later Q3 connection"
      case _ => "All selected checks passed with no unresolved immediate obligation; eligible Release observations remain unresolved"
    }
  } yield Result(policy, payload.candidate, reference, step.checks, branch, reason,
    "not-selected", if (branch == "detailed-review") "required" else "not-executed")

  def admission(result: Result): Admission = Admission(result.validationReference + ":step-admission", result)
  /** Structural read boundary, not a substitute for native receipt recomputation. */
  def validResult(a: Result): Boolean = try {
    a != null && policyShape(a.policy) && a.candidate != null &&
      Vector(a.candidate.head, a.candidate.tree).forall(s => s != null && s.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) &&
      text(a.validationReference) && a.checks != null && a.checks.nonEmpty && a.checks.forall(text) &&
      a.checks.distinct == a.checks && text(a.reason) && a.triage == "not-selected" &&
      (if (a.policy.detailedReview == "required" || a.policy.observations.exists(_.timing == "immediate"))
        a.branch == "detailed-review" && a.review == "required"
      else a.branch == "admit" && a.review == "not-executed")
  } catch { case scala.util.control.NonFatal(_) => false }
  def validAdmission(value: Admission): Boolean = try {
    value != null && validResult(value.assessment) && value == admission(value.assessment) &&
      value.assessment.branch == "admit"
  } catch { case scala.util.control.NonFatal(_) => false }
  private def text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.exists(_.isControl)
  private def invalid[A](message: String): Consequence[A] = Consequence.stateConflict("Step assessment: " + message)
}
