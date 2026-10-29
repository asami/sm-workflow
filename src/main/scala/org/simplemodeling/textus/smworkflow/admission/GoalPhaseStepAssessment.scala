package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeValidation as N

/** Small PLAN-owned Step policy over actual native validation facts.
  * No provider selection, effect authority or replacement validation engine.
  * @since   Oct. 23, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseStepAssessment {
  final case class Observation(identity: String, source: ContextReference, question: String,
    timing: String, reason: String, plannedStep: Option[String])
  final case class Policy(identity: String, kind: String, detailedReview: String,
    scalaLint: String, carLint: String, observations: Vector[Observation], semanticTriage: String = "off",
    scenarioInventory: Option[GoalPhaseStepTriage.Inventory] = None)
  final case class Result(policy: Policy, candidate: O.Snapshot, validationReference: String,
    checks: Vector[String], branch: String, reason: String, triage: String, review: String,
    triageJudgment: Option[GoalPhaseStepTriage.Judgment] = None,
    scenarioSignals: Option[GoalPhaseStepTriage.Signals] = None)
  final case class Admission(reference: String, assessment: Result)

  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  given Encoder[Observation] = deriveEncoder
  given Decoder[Observation] = deriveDecoder
  given Encoder[Policy] = deriveEncoder[Policy].mapJson(_.mapObject(fields =>
    (if (fields("semanticTriage").contains(io.circe.Json.fromString("off"))) fields.remove("semanticTriage") else fields)
      .filter { case ("scenarioInventory", value) => !value.isNull; case _ => true }))
  private val policyDecoder: Decoder[Policy] = deriveDecoder
  given Decoder[Policy] = Decoder.instance { cursor =>
    val normalized = if (cursor.downField("semanticTriage").focus.isEmpty)
      cursor.value.mapObject(_.add("semanticTriage", io.circe.Json.fromString("off"))) else cursor.value
    policyDecoder.decodeJson(normalized.mapObject(fields =>
      if (fields("scenarioInventory").isEmpty) fields.add("scenarioInventory", io.circe.Json.Null) else fields))
  }
  given Encoder[Result] = deriveEncoder[Result].mapJson(_.mapObject(_.filter {
    case ("triageJudgment" | "scenarioSignals", value) => !value.isNull
    case _ => true
  }))
  private val resultDecoder: Decoder[Result] = deriveDecoder
  given Decoder[Result] = Decoder.instance { cursor =>
    val normalized = if (cursor.downField("triageJudgment").focus.isEmpty)
      cursor.value.mapObject(_.add("triageJudgment", io.circe.Json.Null)) else cursor.value
    resultDecoder.decodeJson(normalized.mapObject(fields =>
      if (fields("scenarioSignals").isEmpty) fields.add("scenarioSignals", io.circe.Json.Null) else fields))
  }
  given Encoder[Admission] = deriveEncoder
  given Decoder[Admission] = deriveDecoder

  private def policyShape(policy: Policy): Boolean = try {
    policy != null && text(policy.identity) && Set("code", "documentation")(policy.kind) &&
      Set("required", "conditional")(policy.detailedReview) && Set("off", "required")(policy.scalaLint) && policy.carLint == "off" && Set("off", "conditional")(policy.semanticTriage) &&
      policy.scenarioInventory != null && policy.scenarioInventory.forall(GoalPhaseStepTriage.validInventory) &&
      policy.observations != null && policy.observations.size <= 64 &&
      policy.observations.forall(o => o != null && text(o.identity) && o.source != null &&
        text(o.source.identity) && text(o.source.revision) && text(o.question) && text(o.reason) &&
        Set("immediate", "release")(o.timing) && o.plannedStep != null && o.plannedStep.forall(text) &&
        (o.timing != "release" || o.plannedStep.nonEmpty)) &&
      policy.observations.map(_.identity).distinct.size == policy.observations.size
  } catch { case scala.util.control.NonFatal(_) => false }

  def validatePurposePolicyC(policy: Policy): Consequence[Unit] =
    if (policyShape(policy) && policy.observations.forall(_.plannedStep.isEmpty)) Consequence.unit
    else invalid("purpose policy must be complete and cannot defer to an unbound PLAN Step")

  def validateC(policy: Policy, step: O.Step, plan: O.Plan): Consequence[Unit] = {
    val valid = policyShape(policy) && step != null && plan != null && plan.steps != null &&
      policy.observations.forall(_.plannedStep.forall(id =>
        plan.steps.dropWhile(_.identity != step.identity).drop(1).exists(_.identity == id)))
    if (valid) Consequence.unit else invalid("PLAN Step policy or observation deferral is invalid")
  }

  /** Only a complete successful typed batch can reach semantic assessment.
    * Missing, uncertain and failed results keep their existing owning route.
    */
  def assessC(controller: O.State, payload: N.Payload, reference: String,
    changedPaths: Vector[String] = Vector.empty): Consequence[Result] = for {
    _ <- if (controller != null && payload != null && controller.cursor < controller.plan.steps.size &&
        controller.stage == O.Stage.Validation && controller.candidate.contains(payload.candidate) && text(reference))
      Consequence.unit else invalid("assessment has no exact current Step candidate")
    step = controller.plan.steps(controller.cursor)
    policy <- step.actionPolicy.map(Consequence.success).getOrElse(invalid("Step has no selected assessment policy"))
    _ <- validateC(policy, step, controller.plan)
    _ <- if (payload.outcome.contains("passed") && payload.selected != null && payload.receipts != null &&
        payload.receipts.size == payload.selected.size && N.successful(payload.receipts, payload.selected)) Consequence.unit
      else invalid("assessment requires the complete actual successful selected checks")
    selected = payload.selected.filter(_.dependency.isEmpty)
    receipts = payload.receipts.filter(r => selected.exists(_.identity == r.identity))
    _ <- if (GoalPhaseNativeMetadata.declaredIdentities(selected) == step.checks &&
        receipts.map(_.identity) == selected.map(_.identity)) Consequence.unit
      else invalid("assessment requires the exact primary Step checks and matching receipts")
    _ <- WorkflowRepairAssessment.checksC(policy.kind, policy.scalaLint, selected, receipts)
    signals = policy.scenarioInventory.map(GoalPhaseStepTriage.signals(changedPaths, _))
    immediate = policy.observations.exists(_.timing == "immediate") || signals.exists(_.missing.nonEmpty)
    branch = if (policy.detailedReview == "required" ||
        (policy.semanticTriage == "off" && immediate)) "detailed-review"
      else if (immediate) "triage" else "admit"
    reason = branch match {
      case "detailed-review" if policy.detailedReview == "required" => "PLAN requires independent detailed review; triage is not selected"
      case "detailed-review" => "An unresolved immediate observation retains the existing independent judgment route; conditional triage is a later Q3 connection"
      case "triage" => "Selected immediate semantic uncertainty requires bounded triage using the exact candidate and passed checks"
      case _ => "All selected checks passed with no unresolved immediate obligation; eligible Release observations remain unresolved"
    }
  } yield Result(policy, payload.candidate, reference, step.checks, branch, reason,
    if (branch == "triage") "required" else "not-selected",
    if (branch == "detailed-review") "required" else "not-executed", scenarioSignals = signals)

  /** Same acceptance policy for a genuine immutable purpose candidate. Structural
    * document admission consumes actual native Git checking; code retains the
    * common compile/nonempty behavioral/lint evidence gate. No result is review.
    */
  def assessPurposeC(policy: Policy, candidate: O.Snapshot, reference: String,
    structural: RepositorySyncNativeMergeValidation.Receipt,
    selected: Vector[GoalPhaseNativeValidation.Check],
    receipts: Vector[RepositorySyncNativeMergeValidation.Receipt]): Consequence[Result] = for {
    _ <- if (policyShape(policy) && text(reference) && structural != null &&
        structural.termination == "exited" && structural.exitCode.contains(0) &&
        org.goldenport.cncf.processexecution.GitIndexCheck.parseStoredC(0, structural.stdout, structural.stderr).isSuccess)
      Consequence.unit else invalid("purpose assessment requires its actual structural receipt and selected policy")
    _ <- if (policy.kind == "documentation" && selected.isEmpty && receipts.isEmpty) Consequence.unit
      else WorkflowRepairAssessment.checksC(policy.kind, policy.scalaLint, selected, receipts)
    immediate = policy.observations.exists(_.timing == "immediate")
    branch = if (policy.detailedReview == "required" || immediate) "detailed-review" else "admit"
  } yield Result(policy, candidate, reference, selected.map(_.identity), branch,
    if (branch == "admit") "Actual purpose candidate structural and selected product checks passed with no immediate obligation"
      else "Selected independent purpose review remains required; successful validation is not review acceptance",
    "not-selected", if (branch == "admit") "not-executed" else "required")

  def admission(result: Result): Admission = Admission(result.validationReference + ":step-admission", result)
  def triaged(result: Result, judgment: GoalPhaseStepTriage.Judgment): Result = result.copy(
    branch = if (judgment.outcome == "clean") "admit" else "detailed-review",
    reason = judgment.summary, triage = judgment.outcome,
    review = if (judgment.outcome == "clean") "not-executed" else "required", triageJudgment = Some(judgment))
  /** Structural read boundary, not a substitute for native receipt recomputation. */
  def validResult(a: Result): Boolean = try {
    a != null && policyShape(a.policy) && a.candidate != null &&
      Vector(a.candidate.head, a.candidate.tree).forall(s => s != null && s.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) &&
      text(a.validationReference) && a.checks != null && a.checks.nonEmpty && a.checks.forall(text) &&
      a.checks.distinct == a.checks && text(a.reason) && a.triageJudgment != null &&
      a.triageJudgment.forall(GoalPhaseStepTriage.valid) && a.scenarioSignals != null &&
      a.scenarioSignals.forall(GoalPhaseStepTriage.validSignals) &&
      a.policy.scenarioInventory.isDefined == a.scenarioSignals.isDefined &&
      a.policy.scenarioInventory.zip(a.scenarioSignals).forall { (inventory, signals) =>
        signals.source == inventory.source && signals.status == (if (inventory.complete) "observed" else "partial") &&
          signals.changedOperations.forall(op => inventory.mappings.exists(_.operation == op)) &&
          signals.missing == (if (inventory.complete) inventory.mappings.filter(m => signals.changedOperations.contains(m.operation) && m.scenarios.isEmpty).map(_.operation) else Vector.empty)
      } &&
      (if (a.policy.detailedReview == "required" ||
          (a.policy.semanticTriage == "off" && immediate(a))) a.branch == "detailed-review" &&
        a.review == "required" && a.triage == "not-selected" && a.triageJudgment.isEmpty
      else if (immediate(a)) a.triageJudgment match {
        case None => a.branch == "triage" && a.triage == "required" && a.review == "not-executed"
        case Some(j) if j.outcome == "clean" => a.branch == "admit" && a.triage == "clean" && a.review == "not-executed"
        case Some(j) => a.branch == "detailed-review" && a.triage == j.outcome && a.review == "required"
      }
      else a.branch == "admit" && a.review == "not-executed" && a.triage == "not-selected" && a.triageJudgment.isEmpty)
  } catch { case scala.util.control.NonFatal(_) => false }
  private def immediate(a: Result): Boolean = a.policy.observations.exists(_.timing == "immediate") || a.scenarioSignals.exists(_.missing.nonEmpty)
  def validAdmission(value: Admission): Boolean = try {
    value != null && validResult(value.assessment) && value == admission(value.assessment) &&
      value.assessment.branch == "admit"
  } catch { case scala.util.control.NonFatal(_) => false }
  private def text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.exists(_.isControl)
  private def invalid[A](message: String): Consequence[A] = Consequence.stateConflict("Step assessment: " + message)
}
