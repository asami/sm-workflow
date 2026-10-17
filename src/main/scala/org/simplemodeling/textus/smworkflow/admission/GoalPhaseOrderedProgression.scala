package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import GoalPhaseWorkClassification.given

/** Deterministic Phase controller, not a Service or a persistence provider.
  * Native adapters must admit selection, actual command receipts and independent
  * review before submitting events. These values are NOT permission tokens.
  * Semantic replies carry no stage, cursor, command or closure receipt.
  * @since   Oct.  6, 2026
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseOrderedProgression {
  final case class Step(identity: String, purpose: String, ownedPaths: Vector[String],
    dependencies: Vector[String], checks: Vector[String],
    workClassification: Option[GoalPhaseWorkClassification.Assignment] = None)
  object Step {
    given Encoder[Step] = deriveEncoder[Step].mapJson(_.mapObject(_.filter {
      case ("workClassification", value) => !value.isNull
      case _ => true
    }))
    given Decoder[Step] = deriveDecoder
  }
  final case class Plan(identity: String, revision: String, phase: String, repository: String,
    steps: Vector[Step], phaseChecks: Vector[String], repairLimit: Int = 0,
    phaseReviewClassification: Option[GoalPhaseWorkClassification.Classification] = None,
    acceptance: Option[GoalPhaseAcceptanceBoundary.Adoption] = None,
    effectiveAcceptance: Option[GoalPhaseAcceptanceBoundary.Effective] = None)
  object Plan {
    given Encoder[Plan] = deriveEncoder[Plan].mapJson(_.mapObject(_.filter {
      case ("phaseReviewClassification" | "acceptance" | "effectiveAcceptance", value) => !value.isNull
      case _ => true
    }))
    given Decoder[Plan] = deriveDecoder
  }
  final case class Snapshot(head: String, tree: String)
  final case class Commit(identity: String, parent: String, tree: String)
  final case class ClosedStep(step: String, commit: Commit, review: String)
  enum Stage {
    case Implementation, InspectCandidate, Validation, Review, Repair, Closing,
      PhaseInspection, PhaseValidation, PhaseReview, PhaseClosing, Decision, Closed
  }
  enum WorkKind { case ImplementStep, RepairStep, ReviewStep, ReviewPhase }
  final case class WorkKey(plan: String, revision: String, phase: String, repository: String, step: Option[String],
    kind: WorkKind, generation: Long)
  sealed trait Next
  final case class RepairDirective(reason: String, findings: Vector[String])
  final case class Semantic(key: WorkKey, purpose: String, completed: Vector[ClosedStep],
    selectedPlan: Plan, originalBase: Snapshot, candidate: Option[Snapshot],
    checksReceipt: Option[String], repairDirective: Option[RepairDirective]) extends Next
  final case class Inspect(phaseWide: Boolean, base: String) extends Next
  final case class Validate(snapshot: Snapshot, checks: Vector[String], phaseWide: Boolean) extends Next
  final case class BeginClosure(snapshot: Snapshot, phaseWide: Boolean) extends Next
  final case class ObserveClosure(intent: String, phaseWide: Boolean) extends Next
  final case class Decision(reason: String) extends Next
  final case class Terminal(steps: Vector[ClosedStep], repositoryReceipt: String) extends Next

  sealed trait Event
  /** Assigned AI work only. Review is deliberately a separate host-admitted event. */
  final case class SemanticCompleted(key: WorkKey, summary: String) extends Event
  final case class CandidateObserved(snapshot: Snapshot) extends Event
  final case class PhaseDocumentCandidateObserved(snapshot: Snapshot, paths: Vector[String]) extends Event
  final case class ChecksObserved(snapshot: Snapshot, selected: Vector[String],
    receipt: String, passed: Boolean) extends Event
  final case class RepairableChecksObserved(snapshot: Snapshot, selected: Vector[String],
    receipt: String, directive: RepairDirective) extends Event
  final case class ReviewAdmitted(key: WorkKey, snapshot: Snapshot, checksReceipt: String,
    evidence: String, outcome: ReviewOutcome, repairDirective: Option[RepairDirective] = None) extends Event
  enum ReviewOutcome { case Accept, Repair, Human }
  /** Durable intent must have committed before the native executor runs. */
  final case class ClosureIntentSaved(snapshot: Snapshot, identity: String) extends Event
  final case class ClosureUnknown(identity: String) extends Event
  final case class StepCommitObserved(intent: String, commit: Commit) extends Event
  /** tip is the reviewed ordered-Step tip; a native child may subsequently
    * integrate upstream history. The adapter proves this tip remains an ancestor
    * of the child's actual converged tip and retains that separate child result.
    */
  final case class PhaseRepositoryClosed(intent: String, base: String, tip: String,
    commits: Vector[Commit], receipt: String) extends Event
  final case class NativeFailure(reason: String) extends Event
  final case class DocumentConflictObserved(conflicts: Vector[String]) extends Event
  /** A verified Human may ask for the same semantic work again, not authorize
    * commands, accept a review, amend the Plan or refund a repair allowance.
    */
  final case class DecisionResumptionAdmitted(evidence: String) extends Event

  final class State private[GoalPhaseOrderedProgression] (
    val plan: Plan, val stage: Stage, val cursor: Int, val generation: Long, val repairs: Int,
    val base: Snapshot, val completed: Vector[ClosedStep], val candidate: Option[Snapshot],
    val checksReceipt: Option[String], val reviewEvidence: Option[String],
    val closureIntent: Option[String], val decision: Option[String],
    val repositoryReceipt: Option[String], val repairDirective: Option[RepairDirective],
    val decisionSource: Option[Stage], val documentDecision: Boolean = false) {
    private def phaseWide = cursor == plan.steps.size
    private def step = plan.steps(cursor)
    private def tip = completed.lastOption.map(_.commit.identity).getOrElse(base.head)
    def tipSnapshot: Snapshot = completed.lastOption.map(s => Snapshot(s.commit.identity, s.commit.tree)).getOrElse(base)
    private def checks = if (phaseWide) plan.phaseChecks else step.checks
    private def key(kind: WorkKind) = WorkKey(plan.identity, plan.revision, plan.phase, plan.repository,
      if (phaseWide) None else Some(step.identity), kind, generation)
    private def semantic(kind: WorkKind, purpose: String) = Semantic(key(kind), purpose,
      completed, plan, base, candidate, checksReceipt, repairDirective)

    /** Business context only: this projection grants no issue or execution authority. */
    def protectedStepCount(issued: Boolean): Int = cursor +
      (if (cursor < plan.steps.size && (stage != Stage.Implementation || issued)) 1 else 0)

    def phaseAcceptanceLocked(issued: Boolean): Boolean =
      stage == Stage.PhaseClosing || stage == Stage.Closed || (stage == Stage.PhaseReview && issued)

    /** Compare already captured effective contracts; the declaration owner captures them. */
    def refreshedC(proposed: Plan, issued: Boolean): Consequence[Either[Vector[String], State]] = for {
      _ <- validatePlanC(proposed)
      _ <- GoalPhaseAcceptanceBoundary.savedPlanC(proposed)
      _ <- if (stage == Stage.Decision || stage == Stage.Closed)
        invalid("refresh requires a live non-decision frontier") else Consequence.unit
    } yield {
      val protectedcount = protectedStepCount(issued)
      val phaselocked = phaseAcceptanceLocked(issued)
      val newtail = proposed.steps.size > cursor
      val restart = !issued && newtail && Set(Stage.PhaseInspection, Stage.PhaseValidation, Stage.PhaseReview)(stage)
      val finish = !issued && stage == Stage.Implementation && proposed.steps.size == cursor
      val stepacceptance = completed.nonEmpty || protectedcount > cursor
      val conflicts = Vector(
        Option.when(proposed.identity != plan.identity)("plan-identity"),
        Option.when(proposed.phase != plan.phase)("phase"),
        Option.when(proposed.repository != plan.repository)("repository"),
        Option.when(proposed.repairLimit != plan.repairLimit)("repair-limit"),
        Option.when(proposed.steps.take(cursor) != plan.steps.take(cursor))("completed-steps"),
        Option.when(protectedcount > cursor && proposed.steps.lift(cursor) != plan.steps.lift(cursor))("issued-step"),
        Option.when(phaselocked && proposed.steps != plan.steps)("phase-scope"),
        Option.when(stepacceptance && (_acceptance_usage(proposed) != _acceptance_usage(plan) ||
          _acceptance_conditions(proposed, Set("all", "step")) != _acceptance_conditions(plan, Set("all", "step"))))("step-acceptance"),
        Option.when(Set(Stage.PhaseReview, Stage.PhaseClosing)(stage) && !restart &&
          proposed.phaseChecks != plan.phaseChecks)("phase-checks"),
        Option.when(phaselocked && _acceptance_conditions(proposed, Set("phase")) !=
          _acceptance_conditions(plan, Set("phase")))("phase-acceptance"),
        Option.when(phaselocked && proposed.phaseReviewClassification != plan.phaseReviewClassification)("phase-review-classification")
      ).flatten
      if (conflicts.nonEmpty) Left(conflicts)
      else {
        val clear = restart || finish
        val nextstage = if (restart) Stage.Implementation else if (finish) Stage.PhaseInspection else stage
        Right(new State(proposed, nextstage, cursor, generation, repairs, base, completed,
          if (clear) None else candidate, if (clear) None else checksReceipt,
          if (clear) None else reviewEvidence, if (clear) None else closureIntent,
          if (clear) None else decision, repositoryReceipt, if (clear) None else repairDirective,
          if (clear) None else decisionSource))
      }
    }

    private def _acceptance_usage(value: Plan): Option[String] = value.effectiveAcceptance.flatMap(_.usage)
    private def _acceptance_conditions(value: Plan, scopes: Set[String]): Map[String, GoalPhaseAcceptanceBoundary.EffectiveCondition] =
      value.effectiveAcceptance.toVector.flatMap(_.conditions).filter(c => scopes(c.scope)).map(c => c.identity -> c).toMap

    def next: Next = stage match {
      case Stage.Implementation => semantic(WorkKind.ImplementStep, step.purpose)
      case Stage.Repair => semantic(WorkKind.RepairStep, step.purpose)
      case Stage.InspectCandidate => Inspect(false, tip)
      case Stage.PhaseInspection => Inspect(true, base.head)
      case Stage.Validation | Stage.PhaseValidation => Validate(candidate.get, checks, phaseWide)
      case Stage.Review => semantic(WorkKind.ReviewStep, step.purpose)
      case Stage.PhaseReview => semantic(WorkKind.ReviewPhase, "Review the whole selected Phase")
      case Stage.Closing | Stage.PhaseClosing => closureIntent match {
        case None => BeginClosure(candidate.get, phaseWide)
        case Some(value) => ObserveClosure(value, phaseWide)
      }
      case Stage.Decision => Decision(decision.get)
      case Stage.Closed => Terminal(completed, repositoryReceipt.get)
    }

    /** Pure transition: does not run commands, issue work, grant consent or write
      * storage. Owning advance will perform and persist these actions natively.
      */
    def acceptC(event: Event): Consequence[State] = event match {
      case SemanticCompleted(value, summary) if summary != null && summary.trim.nonEmpty &&
          ((stage == Stage.Implementation && value == key(WorkKind.ImplementStep)) ||
            (stage == Stage.Repair && value == key(WorkKind.RepairStep))) =>
        success(Stage.InspectCandidate, clear = true)
      case CandidateObserved(value) if validSnapshot(value) && value.head == tip &&
          (stage == Stage.InspectCandidate || stage == Stage.PhaseInspection) &&
          (!phaseWide || completed.lastOption.exists(_.commit.tree == value.tree)) =>
        success(if (phaseWide) Stage.PhaseValidation else Stage.Validation,
          newCandidate = Some(value))
      case PhaseDocumentCandidateObserved(value, paths) if stage == Stage.PhaseInspection && phaseWide &&
          validSnapshot(value) && value.head == tip && paths != null && paths.nonEmpty &&
          paths.distinct == paths && paths.forall(path) &&
          plan.steps.forall(step => paths.forall(step.ownedPaths.contains)) &&
          completed.lastOption.exists(_.commit.tree != value.tree) =>
        success(Stage.PhaseValidation, newCandidate = Some(value))
      case ChecksObserved(value, selected, receipt, passed) if candidate.contains(value) &&
          selected == checks && text(receipt) &&
          (stage == Stage.Validation || stage == Stage.PhaseValidation) =>
        if (passed) success(if (phaseWide) Stage.PhaseReview else Stage.Review,
          newChecks = Some(receipt))
        else success(Stage.Decision, reason = Some("Selected native checks failed: " + receipt))
      case RepairableChecksObserved(value, selected, receipt, directive) if
          stage == Stage.Validation && !phaseWide && candidate.contains(value) &&
          selected == checks && text(receipt) && _valid_directive(directive) &&
          generation < Long.MaxValue && repairs < plan.repairLimit =>
        success(Stage.Repair, newGeneration = generation + 1, newRepairs = repairs + 1,
          newDirective = Some(directive), clear = true)
      case ReviewAdmitted(value, snapshot, validation, evidence, outcome, directive) if
          candidate.contains(snapshot) && checksReceipt.contains(validation) && text(evidence) &&
          ((stage == Stage.Review && value == key(WorkKind.ReviewStep)) ||
            (stage == Stage.PhaseReview && value == key(WorkKind.ReviewPhase))) => outcome match {
        case ReviewOutcome.Accept => success(if (phaseWide) Stage.PhaseClosing else Stage.Closing,
          newReview = Some(evidence))
        // Phase repair needs a new explicitly selected amendment, not mutation of
        // already closed Step commits or a fabricated re-open/refunded budget.
        case ReviewOutcome.Repair if !phaseWide && repairs < plan.repairLimit =>
          if (directive.exists(d => d != null && d.reason != null && d.reason.trim.nonEmpty &&
              d.findings != null && d.findings.nonEmpty && d.findings.forall(f => f != null && f.trim.nonEmpty)))
            success(Stage.Repair, newGeneration = generation + 1, newRepairs = repairs + 1,
              newDirective = directive, clear = true)
          else invalid("repair requires the independent review's assigned reason and findings")
        case _ => success(Stage.Decision, reason = Some("Independent review requires a Human decision: " + evidence))
      }
      case ClosureIntentSaved(value, identity) if candidate.contains(value) && text(identity) &&
          closureIntent.isEmpty && (stage == Stage.Closing || stage == Stage.PhaseClosing) =>
        success(stage, newIntent = Some(identity))
      case ClosureUnknown(identity) if closureIntent.contains(identity) &&
          (stage == Stage.Closing || stage == Stage.PhaseClosing) => Consequence.success(this)
      case StepCommitObserved(intent, commit) if stage == Stage.Closing &&
          closureIntent.contains(intent) && validCommit(commit) && commit.parent == tip &&
          candidate.exists(_.tree == commit.tree) && commit.identity != tip =>
        val closed = completed :+ ClosedStep(step.identity, commit, reviewEvidence.get)
        val nextCursor = cursor + 1
        Consequence.success(new State(plan,
          if (nextCursor == plan.steps.size) Stage.PhaseInspection else Stage.Implementation,
          nextCursor, generation + 1, repairs, base, closed, None, None, None, None, None, None, None, None))
      case PhaseRepositoryClosed(intent, original, finalTip, commits, receipt) if
          stage == Stage.PhaseClosing && closureIntent.contains(intent) && original == base.head &&
          finalTip == tip && commits == completed.map(_.commit) && text(receipt) =>
        success(Stage.Closed, newRepository = Some(receipt))
      case NativeFailure(reason) if reason != null && reason.trim.nonEmpty && stage != Stage.Closed && stage != Stage.Decision =>
        success(Stage.Decision, reason = Some(reason))
      case DocumentConflictObserved(conflicts) if stage != Stage.Decision && stage != Stage.Closed &&
          conflicts != null && conflicts.nonEmpty && conflicts.size <= 128 &&
          conflicts.distinct == conflicts && conflicts.forall(value => text(value) &&
            value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 16384) =>
        Consequence.success(new State(plan, Stage.Decision, cursor, generation, repairs, base, completed,
          candidate, checksReceipt, reviewEvidence, closureIntent,
          Some("Latest document acceptance conflicts: " + conflicts.mkString(", ")), repositoryReceipt,
          repairDirective, Some(stage), true))
      case DecisionResumptionAdmitted(evidence) if stage == Stage.Decision && text(evidence) &&
          documentDecision && decisionSource.exists(value => value != Stage.Decision && value != Stage.Closed) =>
        success(decisionSource.get, reason = None)
      case DecisionResumptionAdmitted(evidence) if stage == Stage.Decision && text(evidence) &&
          !documentDecision && generation < Long.MaxValue && decisionSource.exists(Set(Stage.Implementation, Stage.Repair,
            Stage.Review, Stage.PhaseReview)) =>
        success(decisionSource.get, newGeneration = generation + 1, reason = None)
      case _ => invalid("event differs from the Workflow-assigned ordered Phase frontier")
    }

    private def success(nextStage: Stage, newCandidate: Option[Snapshot] = candidate,
      newChecks: Option[String] = checksReceipt, newReview: Option[String] = reviewEvidence,
      newIntent: Option[String] = closureIntent, reason: Option[String] = decision,
      newRepository: Option[String] = repositoryReceipt, newGeneration: Long = generation,
      newRepairs: Int = repairs,
      newDirective: Option[RepairDirective] = repairDirective,
      clear: Boolean = false): Consequence[State] = Consequence.success(new State(plan, nextStage,
      cursor, newGeneration, newRepairs, base, completed, if (clear) None else newCandidate,
      if (clear) None else newChecks, if (clear) None else newReview,
      if (clear) None else newIntent, reason, newRepository, newDirective,
      if (nextStage == Stage.Decision) decisionSource.orElse(Some(stage)) else None))
  }

  def startC(plan: Plan, initial: Snapshot): Consequence[State] = for {
    _ <- validatePlanC(plan)
    _ <- if (validSnapshot(initial)) Consequence.unit else invalid("native initial Git snapshot is missing")
  } yield new State(plan, Stage.Implementation, 0, 1, 0, initial, Vector.empty,
    None, None, None, None, None, None, None, None)

  def validatePlanC(plan: Plan): Consequence[Unit] = {
    if (plan == null || !Vector(plan.identity, plan.revision, plan.phase, plan.repository).forall(text) ||
        plan.steps == null || plan.steps.isEmpty || plan.steps.size > 64 ||
        !identities(plan.phaseChecks) || plan.phaseChecks.isEmpty || plan.repairLimit < 0 || plan.repairLimit > 64 ||
        plan.phaseReviewClassification == null || plan.phaseReviewClassification.exists(value =>
          value == null || value.kind != GoalPhaseWorkClassification.Kind.REVIEW || value.validateC.toOption.isEmpty))
      return invalid("selected complete Phase plan or bounded repair policy is missing")
    val ids = plan.steps.map(s => Option(s).map(_.identity).getOrElse(""))
    if (!identities(ids)) return invalid("selected Step identities are empty or duplicated")
    plan.steps.zipWithIndex.foldLeft(GoalPhaseAcceptanceBoundary.validatePlanC(plan)) { case (prior, (s, index)) => prior.flatMap { _ =>
      if (s == null || !text(s.purpose) || s.ownedPaths == null || s.ownedPaths.isEmpty ||
          s.ownedPaths.size > 256 || s.ownedPaths.distinct != s.ownedPaths || !s.ownedPaths.forall(path) ||
          !identities(s.dependencies) || !s.dependencies.forall(ids.take(index).contains) ||
          !identities(s.checks) || s.checks.isEmpty || s.workClassification == null ||
          s.workClassification.exists(value => value == null || value.validateC.toOption.isEmpty))
        invalid("Step coverage, earlier dependencies, selected checks or WorkClassification are invalid")
      else Consequence.unit
    }}
  }
  private def identities(values: Vector[String]) = values != null && values.size <= 64 &&
    values.forall(text) && values.distinct == values
  private def text(value: String) = value != null && value.trim.nonEmpty &&
    !value.exists(c => c == '\u0000' || c == '\r' || c == '\n')
  private def path(value: String) = text(value) && !value.startsWith("/") && !value.contains("\\") &&
    value.split("/", -1).forall(p => p.nonEmpty && p != "." && p != ".." && p != ".git")
  private def oid(value: String) = value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def validSnapshot(value: Snapshot) = value != null && oid(value.head) && oid(value.tree)
  private def _valid_directive(value: RepairDirective) = value != null && text(value.reason) &&
    value.findings != null && value.findings.nonEmpty && value.findings.forall(text)
  private def validCommit(value: Commit) = value != null && oid(value.identity) && oid(value.parent) && oid(value.tree)
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("GoalPhase ordered progression: " + reason)
}
