package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.cncf.workflow.ContextReference
import GoalPhaseWorkClassification.given

/** Deterministic Phase controller, not a Service or a persistence provider.
  * Native adapters must admit selection, actual command receipts and independent
  * review before submitting events. These values are NOT permission tokens.
  * Semantic replies carry no stage, cursor, command or closure receipt.
  * @since   Oct.  6, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseOrderedProgression {
  final case class Step(identity: String, purpose: String, ownedPaths: Vector[String],
    dependencies: Vector[String], checks: Vector[String],
    workClassification: Option[GoalPhaseWorkClassification.Assignment] = None,
    actionPolicy: Option[GoalPhaseStepAssessment.Policy] = None)
  object Step {
    given Encoder[Step] = deriveEncoder[Step].mapJson(_.mapObject(_.filter {
      case ("workClassification" | "actionPolicy", value) => !value.isNull
      case _ => true
    }))
    given Decoder[Step] = deriveDecoder
  }
  final case class Plan(identity: String, revision: String, phase: String, repository: String,
    steps: Vector[Step], phaseChecks: Vector[String], repairLimit: Int = 10,
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
  object Snapshot {
    given Encoder[Snapshot] = deriveEncoder
    given Decoder[Snapshot] = deriveDecoder
  }
  final case class HistoryCommit(identity: String, tree: String, parents: Vector[String])
  final case class CandidateContinuation(parent: Snapshot, lineage: Vector[HistoryCommit])
  /** A retained proof is interpreted against the immutable accepted Step tip. */
  def continuationValid(anchor: Snapshot, value: CandidateContinuation): Boolean =
    validSnapshot(anchor) && value != null && validSnapshot(value.parent) && value.parent.head != anchor.head &&
      value.lineage != null && value.lineage.size >= 2 && value.lineage.size <= 256 &&
      value.lineage.forall(c => c != null && oid(c.identity) && oid(c.tree) && c.parents != null &&
        c.parents.size <= 256 && c.parents.distinct == c.parents && c.parents.forall(oid) && !c.parents.contains(c.identity)) &&
      value.lineage.map(_.identity).distinct.size == value.lineage.size &&
      value.lineage.head.identity == value.parent.head && value.lineage.head.tree == value.parent.tree &&
      value.lineage.last.identity == anchor.head && value.lineage.last.tree == anchor.tree &&
      value.lineage.sliding(2).forall(pair => pair.head.parents.contains(pair.last.identity)) &&
      value.lineage.zipWithIndex.forall { (commit, index) =>
        !commit.parents.exists(value.lineage.take(index).map(_.identity).contains)
      }
  final case class Commit(identity: String, parent: String, tree: String)
  object Commit {
    given Encoder[Commit] = deriveEncoder
    given Decoder[Commit] = deriveDecoder
  }
  final case class ClosedStep(step: String, commit: Commit, review: Option[String],
    admission: Option[GoalPhaseStepAssessment.Admission] = None)
  object ClosedStep {
    def apply(step: String, commit: Commit, review: String): ClosedStep = new ClosedStep(step, commit, Some(review))
    given Encoder[ClosedStep] = deriveEncoder[ClosedStep].mapJson(_.mapObject(_.filter {
      case ("admission", value) => !value.isNull
      case _ => true
    }))
    given Decoder[ClosedStep] = deriveDecoder
  }
  def validStepAcceptance(step: ClosedStep): Boolean = step != null && step.review != null && step.admission != null &&
    ((step.review.exists(text) && step.admission.isEmpty) || (step.review.isEmpty &&
      step.admission.exists(GoalPhaseStepAssessment.validAdmission)))
  enum Stage {
    case Implementation, InspectCandidate, Validation, Triage, Review, Repair, Closing,
      PhaseInspection, PhaseValidation, PhaseReview, PhaseClosing, Decision, Closed
  }
  enum WorkKind { case ImplementStep, RepairStep, TriageStep, ReviewStep, ReviewPhase }
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
  final case class Terminal(steps: Vector[ClosedStep], repositoryReceipt: Option[String]) extends Next

  sealed trait Event
  /** Assigned AI work only. Review is deliberately a separate host-admitted event. */
  final case class SemanticCompleted(key: WorkKey, summary: String,
    ordinaryCountExempt: Boolean = false) extends Event
  final case class CandidateObserved(snapshot: Snapshot) extends Event
  final case class CandidateContinuationObserved(snapshot: Snapshot, continuation: CandidateContinuation) extends Event
  final case class PhaseManagementCandidateObserved(snapshot: Snapshot, paths: Vector[String]) extends Event
  final case class PhaseFindingRecordsObserved(reviewed: Snapshot, finalCandidate: Snapshot, paths: Vector[String]) extends Event
  final case class ChecksObserved(snapshot: Snapshot, selected: Vector[String],
    receipt: String, passed: Boolean) extends Event
  final case class TriageAdmitted(key: WorkKey, assessment: GoalPhaseStepAssessment.Result,
    judgment: GoalPhaseStepTriage.Judgment) extends Event
  final case class StepAssessed(assessment: GoalPhaseStepAssessment.Result) extends Event
  final case class RepairableChecksObserved(snapshot: Snapshot, selected: Vector[String],
    receipt: String, directive: RepairDirective) extends Event
  final case class ReviewAdmitted(key: WorkKey, snapshot: Snapshot, checksReceipt: String,
    evidence: String, outcome: ReviewOutcome, repairDirective: Option[RepairDirective] = None) extends Event
  /** Mechanical admission by the native completion owner; this event grants no effect authority. */
  final case class CoveredReviewCompletionObserved(snapshot: Snapshot, checksReceipt: String,
    originalGeneration: Long, originalCandidate: Snapshot, originalReviewReference: String,
    completionReference: String) extends Event
  enum ReviewOutcome { case Accept, Repair, Human }
  /** Durable intent must have committed before the native executor runs. */
  final case class ClosureIntentSaved(snapshot: Snapshot, identity: String) extends Event
  final case class ClosureUnknown(identity: String) extends Event
  final case class StepCommitObserved(intent: String, commit: Commit) extends Event
  final case class StepEofCorrectionCommitObserved(intent: String, commit: Commit, paths: Vector[String]) extends Event
  /** tip is the reviewed ordered-Step tip; a native child may subsequently
    * integrate upstream history. The adapter proves this tip remains an ancestor
    * of the child's actual converged tip and retains that separate child result.
    */
  final case class PhaseRepositoryClosed(intent: String, base: String, tip: String,
    commits: Vector[Commit], receipt: String) extends Event
  /** Accepted committed development is complete independently of synchronization. */
  final case class PhaseLocallyCompleted(snapshot: Snapshot, commits: Vector[Commit], review: String) extends Event
  /** Actual Final commit of the accepted release delta; closed Steps stay immutable. */
  final case class ReleaseCommitObserved(commit: Commit) extends Event
  final case class NativeFailure(reason: String) extends Event
  final case class DocumentConflictObserved(conflicts: Vector[String]) extends Event
  /** A verified Human may ask for the same semantic work again, not authorize
    * commands, accept a review, amend the Plan or refund a repair allowance.
    */
  final case class DecisionResumptionAdmitted(evidence: String) extends Event
  /** Host-verified consent for one exhausted positive Step repair window only. */
  final case class RepairContinuationAdmitted(evidence: String) extends Event
  /** Eligibility only; the native owner independently verifies Human direction. */
  final case class PhaseValidationRetryAdmitted(snapshot: Snapshot, selected: Vector[String],
    failedReceipt: String, direction: String) extends Event
  /** Pure eligibility only; the native owner authenticates the actual Human direction. */
  final case class CandidateWorkRevisionAdmitted(direction: ContextReference, rationale: String) extends Event

  final class State private[GoalPhaseOrderedProgression] (
    val plan: Plan, val stage: Stage, val cursor: Int, val generation: Long, val repairs: Int,
    val base: Snapshot, val completed: Vector[ClosedStep], val candidate: Option[Snapshot],
    val checksReceipt: Option[String], val reviewEvidence: Option[String],
    val closureIntent: Option[String], val decision: Option[String],
    val repositoryReceipt: Option[String], val repairDirective: Option[RepairDirective],
    val decisionSource: Option[Stage], val documentDecision: Boolean = false,
    val ordinaryRepairs: Int = 0, val pendingRepair: Option[RepairDirective] = None,
    val phaseReviewedCandidate: Option[Snapshot] = None, val phaseRecordingPaths: Vector[String] = Vector.empty,
    val candidateParent: Option[Snapshot] = None,
    val stepAdmission: Option[GoalPhaseStepAssessment.Admission] = None,
    val releaseCommit: Option[Commit] = None) {
    private def phaseWide = cursor == plan.steps.size
    private def step = plan.steps(cursor)
    private def tip = tipSnapshot.head
    def tipSnapshot: Snapshot = releaseCommit.map(c => Snapshot(c.identity, c.tree))
      .orElse(completed.lastOption.map(s => Snapshot(s.commit.identity, s.commit.tree))).getOrElse(base)
    def releaseRepairActive: Boolean = phaseWide && repairs > 0 && repairDirective.nonEmpty
    def candidateBase: Snapshot = candidateParent.getOrElse(tipSnapshot)
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

    /** Explicit host-admitted adoption retains the consumed repair window and
      * accepted prefix. A Phase review may append one bounded corrective Step. */
    def adoptRevisionC(proposed: Plan, evidence: String): Consequence[State] = for {
      _ <- validatePlanC(proposed)
      _ <- GoalPhaseAcceptanceBoundary.savedPlanC(proposed)
      _ <- if (text(evidence) && !evidence.exists(c => Character.isISOControl(c))) Consequence.unit
        else invalid("Plan adoption requires an actual host-admitted answer reference")
      current = cursor < plan.steps.size &&
        decisionSource.exists(Set(Stage.Implementation, Stage.Repair, Stage.Review))
      corrective = cursor == plan.steps.size && cursor == completed.size && plan.steps.size < 64 &&
        decisionSource.contains(Stage.PhaseReview)
      _ <- if (stage == Stage.Decision && !documentDecision && pendingRepair.isEmpty &&
          closureIntent.isEmpty && generation < Long.MaxValue && (current || corrective)) Consequence.unit
        else invalid("Plan adoption requires an eligible current-Step or completed-Phase review decision")
      _ <- if (proposed.revision != plan.revision && proposed.identity == plan.identity &&
          proposed.phase == plan.phase && proposed.repository == plan.repository &&
          proposed.repairLimit == plan.repairLimit && proposed.phaseChecks == plan.phaseChecks &&
          proposed.phaseReviewClassification == plan.phaseReviewClassification) Consequence.unit
        else invalid("Plan adoption must retain Plan identity, Phase, repository and closure policy")
      _ <- if (current &&
          proposed.steps.size == plan.steps.size && proposed.steps.updated(cursor, step) == plan.steps &&
          proposed.steps(cursor).copy(purpose = step.purpose, checks = step.checks) == step) Consequence.unit
        else if (corrective && proposed.steps.size == plan.steps.size + 1 &&
          proposed.steps.take(plan.steps.size) == plan.steps &&
          proposed.steps.last.dependencies.toSet == plan.steps.map(_.identity).toSet &&
          proposed.steps.last.ownedPaths.forall(value => plan.steps.exists(_.ownedPaths.exists(owner =>
            value == owner || value.startsWith(owner + "/")))) &&
          proposed.acceptance == plan.acceptance && proposed.effectiveAcceptance == plan.effectiveAcceptance) Consequence.unit
        else invalid("Plan adoption may revise the current Step or append exactly one corrective Step within the unchanged completed Plan scope and acceptance")
      _ <- if (corrective || completed.isEmpty || (_acceptance_usage(proposed) == _acceptance_usage(plan) &&
          _acceptance_conditions(proposed, Set("all", "step")) ==
            _acceptance_conditions(plan, Set("all", "step")))) Consequence.unit
        else invalid("Plan adoption cannot redefine accepted Step conditions or Usage Contract")
    } yield new State(proposed, Stage.Implementation, cursor, generation + 1, repairs, base, completed,
      None, None, None, None, None, repositoryReceipt, None, None,
      documentDecision = false, ordinaryRepairs = ordinaryRepairs, pendingRepair = None)

    /** Separate explicit remaining-work adoption. Historical accepted Step
      * contracts stay in their original retained native requests and results;
      * this Plan governs only newly issued suffix work and future acceptance.
      * The owning native operation must supersede its actual pending Decision
      * atomically after independent admission of the new Human directive.
      */
    def adoptRemainingRevisionC(proposed: Plan, evidence: String): Consequence[State] = for {
      _ <- validatePlanC(proposed)
      _ <- GoalPhaseAcceptanceBoundary.savedPlanC(proposed)
      _ <- if (text(evidence) && !evidence.exists(c => Character.isISOControl(c))) Consequence.unit
        else invalid("Remaining Plan adoption requires its actual independent Human directive")
      _ <- if (stage == Stage.Decision && !documentDecision && pendingRepair.isEmpty &&
          closureIntent.isEmpty && phaseReviewedCandidate.isEmpty && cursor < plan.steps.size &&
          cursor == completed.size && generation < Long.MaxValue &&
          decisionSource.exists(Set(Stage.Implementation, Stage.Repair))) Consequence.unit
        else invalid("Remaining Plan adoption requires an unanswered implementation or repair Decision")
      _ <- if (proposed.revision != plan.revision && proposed.identity == plan.identity &&
          proposed.phase == plan.phase && proposed.repository == plan.repository &&
          proposed.repairLimit == plan.repairLimit && proposed.phaseChecks == plan.phaseChecks &&
          proposed.phaseReviewClassification == plan.phaseReviewClassification &&
          proposed.steps.size == plan.steps.size && proposed.steps.take(cursor) == plan.steps.take(cursor) &&
          proposed.steps.drop(cursor).zip(plan.steps.drop(cursor)).forall { (next, prior) =>
            next.copy(purpose = prior.purpose, checks = prior.checks) == prior
          }) Consequence.unit
        else invalid("Remaining Plan adoption must preserve the accepted prefix, every Step scope and closure policy")
    } yield new State(proposed, Stage.Implementation, cursor, generation + 1, repairs, base, completed,
      None, None, None, None, None, repositoryReceipt, None, None,
      documentDecision = false, ordinaryRepairs = ordinaryRepairs, pendingRepair = None)

    /** Eligibility only. The native owner binds the actual Review finding and
      * new condition to an independently admitted Human directive. This consumes
      * one ordinary repair; no past acceptance observation is manufactured.
      */
    private[admission] def adoptRemainingReviewRevisionC(proposed: Plan, evidence: String,
      directive: RepairDirective): Consequence[State] = for {
      _ <- validatePlanC(proposed)
      _ <- GoalPhaseAcceptanceBoundary.savedPlanC(proposed)
      _ <- if (text(evidence) && !evidence.exists(_.isControl) && directive != null && text(directive.reason) &&
          directive.findings != null && directive.findings.size == 1 &&
          directive.reason.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 16384 &&
          directive.findings.forall(finding => finding != null && finding.trim.nonEmpty && !finding.contains('\u0000') &&
            finding.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 16384)) Consequence.unit
        else invalid("Review remaining Plan adoption requires its independently admitted directive and exact finding")
      _ <- if (stage == Stage.Decision && decisionSource.contains(Stage.Review) && !documentDecision &&
          pendingRepair.isEmpty && closureIntent.isEmpty && phaseReviewedCandidate.isEmpty &&
          phaseRecordingPaths.isEmpty && cursor < plan.steps.size && cursor == completed.size &&
          generation < Long.MaxValue && repairs < plan.repairLimit &&
          candidate.exists(validSnapshot) && checksReceipt.exists(text)) Consequence.unit
        else invalid("Review remaining Plan adoption requires an unanswered current-Step Review Decision with remaining repair allowance")
      condition <- reviewRepairConditionC(plan, proposed.copy(effectiveAcceptance = None))
      expected = plan.effectiveAcceptance.map(boundary => boundary.copy(conditions = boundary.conditions :+
        GoalPhaseAcceptanceBoundary.EffectiveCondition(condition.identity, condition.scope, condition.required,
          condition.description, "adopted")))
      _ <- if (proposed.effectiveAcceptance == expected) Consequence.unit
        else invalid("Review remaining Plan adoption must preserve every saved effective condition and its source")
    } yield new State(proposed, Stage.Repair, cursor, generation + 1, repairs + 1, base, completed,
      None, None, None, None, None, repositoryReceipt, Some(directive), None,
      documentDecision = false, ordinaryRepairs = ordinaryRepairs + 1, pendingRepair = None)

    /** Separate unchanged-Plan continuation of an independently directed legacy
      * review batch. Native receipt reconstruction owns the actual Human origin.
      */
    private[admission] def continueLegacyPurposeRepairC(evidence: String,
      directive: RepairDirective): Consequence[State] = for {
      _ <- if (text(evidence) && !evidence.exists(_.isControl) && directive != null &&
          GoalPhasePurposeRepair.text(directive.reason) && directive.findings != null &&
          directive.findings.nonEmpty && directive.findings.size <= 128 && directive.findings.forall(GoalPhasePurposeRepair.text))
        Consequence.unit else invalid("legacy purpose continuation requires the independently admitted exact finding batch")
      _ <- if (stage == Stage.Decision && decisionSource.contains(Stage.Review) && !documentDecision &&
          pendingRepair.isEmpty && closureIntent.isEmpty && phaseReviewedCandidate.isEmpty && phaseRecordingPaths.isEmpty &&
          cursor < plan.steps.size && cursor == completed.size && generation < Long.MaxValue && repairs < plan.repairLimit &&
          candidate.exists(validSnapshot) && checksReceipt.exists(text)) Consequence.unit
        else invalid("legacy purpose continuation requires its unanswered Step Review and remaining ordinary repair allowance")
    } yield new State(plan, Stage.Repair, cursor, generation + 1, repairs + 1, base, completed,
      None, None, None, None, None, repositoryReceipt, Some(directive), None,
      documentDecision = false, ordinaryRepairs = ordinaryRepairs + 1, pendingRepair = None)

    /** Eligibility only: the native owner must prove the original terminated
      * failed Phase validation and independently admit the Human direction.
      * Accepted Steps remain immutable; only one new corrective Step is added.
      */
    def adoptFailedPhaseRepairRevisionC(proposed: Plan, evidence: String): Consequence[State] = for {
      _ <- validatePlanC(proposed)
      _ <- GoalPhaseAcceptanceBoundary.savedPlanC(proposed)
      _ <- if (text(evidence) && !evidence.exists(c => Character.isISOControl(c))) Consequence.unit
        else invalid("Failed Phase repair adoption requires its independent Human direction")
      _ <- if (stage == Stage.Decision && decisionSource.contains(Stage.PhaseValidation) &&
          !documentDecision && pendingRepair.isEmpty && closureIntent.isEmpty &&
          phaseReviewedCandidate.isEmpty && phaseRecordingPaths.isEmpty && reviewEvidence.isEmpty &&
          cursor == plan.steps.size && completed.map(_.step) == plan.steps.map(_.identity) &&
          plan.steps.size < 64 && plan.phaseChecks.size == 1 && candidate.exists(validSnapshot) &&
          generation < Long.MaxValue) Consequence.unit
        else invalid("Failed Phase repair adoption requires the original completed-Plan Phase validation Decision")
      _ <- if (proposed.identity == plan.identity && proposed.revision != plan.revision &&
          proposed.phase == plan.phase && proposed.repository == plan.repository &&
          proposed.repairLimit == plan.repairLimit && proposed.phaseChecks == plan.phaseChecks &&
          proposed.phaseReviewClassification == plan.phaseReviewClassification &&
          proposed.acceptance == plan.acceptance && proposed.effectiveAcceptance == plan.effectiveAcceptance &&
          proposed.steps.size == plan.steps.size + 1 && proposed.steps.take(plan.steps.size) == plan.steps &&
          proposed.steps.last.dependencies.toSet == plan.steps.map(_.identity).toSet) Consequence.unit
        else invalid("Failed Phase repair adoption must retain the complete accepted Plan and append exactly one dependent corrective Step")
    } yield new State(proposed, Stage.Implementation, cursor, generation + 1, repairs, base, completed,
      None, None, None, None, None, repositoryReceipt, None, None,
      documentDecision = false, ordinaryRepairs = ordinaryRepairs, pendingRepair = None)

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
          if (clear) None else decisionSource, documentDecision, ordinaryRepairs, pendingRepair,
          if (clear) None else phaseReviewedCandidate, if (clear) Vector.empty else phaseRecordingPaths,
          if (clear) None else candidateParent, stepAdmission = if (clear) None else stepAdmission,
          releaseCommit = releaseCommit))
      }
    }

    private def _acceptance_usage(value: Plan): Option[String] = value.effectiveAcceptance.flatMap(_.usage)
    private def _acceptance_conditions(value: Plan, scopes: Set[String]): Map[String, GoalPhaseAcceptanceBoundary.EffectiveCondition] =
      value.effectiveAcceptance.toVector.flatMap(_.conditions).filter(c => scopes(c.scope)).map(c => c.identity -> c).toMap

    def next: Next = stage match {
      case Stage.Implementation => semantic(WorkKind.ImplementStep, step.purpose)
      case Stage.Repair => semantic(WorkKind.RepairStep, if (phaseWide)
        "Repair only the assigned blocking release findings within the unchanged Plan scope; retain closed Steps and applicable prior judgments"
        else step.purpose)
      case Stage.InspectCandidate => Inspect(false, tip)
      case Stage.PhaseInspection => Inspect(true, base.head)
      case Stage.Validation | Stage.PhaseValidation => Validate(candidate.get, checks, phaseWide)
      case Stage.Triage => semantic(WorkKind.TriageStep, step.purpose)
      case Stage.Review => semantic(WorkKind.ReviewStep, step.purpose)
      case Stage.PhaseReview => semantic(WorkKind.ReviewPhase, if (releaseRepairActive)
        "Review the unresolved release findings, repair delta and affected meaning; reuse the prior integrated Phase review"
        else "Review the whole selected Phase")
      case Stage.Closing | Stage.PhaseClosing => closureIntent match {
        case None => BeginClosure(candidate.get, phaseWide)
        case Some(value) => ObserveClosure(value, phaseWide)
      }
      case Stage.Decision => Decision(decision.get)
      case Stage.Closed => Terminal(completed, repositoryReceipt)
    }

    /** Pure transition: does not run commands, issue work, grant consent or write
      * storage. Owning advance will perform and persist these actions natively.
      */
    def acceptC(event: Event): Consequence[State] = event match {
      case CandidateWorkRevisionAdmitted(direction, rationale) if stage == Stage.InspectCandidate &&
          !phaseWide && cursor == completed.size && generation < Long.MaxValue &&
          candidate.isEmpty && checksReceipt.isEmpty && reviewEvidence.isEmpty && closureIntent.isEmpty &&
          decision.isEmpty && decisionSource.isEmpty && !documentDecision && pendingRepair.isEmpty &&
          phaseReviewedCandidate.isEmpty && phaseRecordingPaths.isEmpty && candidateParent.isEmpty &&
          repositoryReceipt.isEmpty && direction != null && text(direction.identity) && text(direction.revision) &&
          rationale != null && rationale.trim.nonEmpty && !rationale.contains('\u0000') =>
        success(Stage.Implementation, newGeneration = generation + 1, clear = true,
          newDirective = Some(RepairDirective("Human-requested reconciliation: " + direction.identity + "@" + direction.revision,
            Vector(rationale))))
      case SemanticCompleted(value, summary, exempt) if summary != null && summary.trim.nonEmpty &&
          ((stage == Stage.Implementation && value == key(WorkKind.ImplementStep)) ||
            (stage == Stage.Repair && value == key(WorkKind.RepairStep))) &&
          (!exempt || (stage == Stage.Repair && ordinaryRepairs > 0 && repairs > 0)) =>
        success(if (phaseWide) Stage.PhaseInspection else Stage.InspectCandidate,
          newOrdinaryRepairs = ordinaryRepairs - (if (exempt) 1 else 0), clear = true)
      case CandidateObserved(value) if validSnapshot(value) && value.head == tip &&
          (stage == Stage.InspectCandidate || stage == Stage.PhaseInspection) &&
          (!phaseWide || tipSnapshot.tree == value.tree || releaseRepairActive) =>
        success(if (phaseWide) Stage.PhaseValidation else Stage.Validation,
          newCandidate = Some(value))
      case CandidateContinuationObserved(value, continuation) if stage == Stage.InspectCandidate && !phaseWide &&
          validSnapshot(value) && continuationValid(tipSnapshot, continuation) && value.head == continuation.parent.head =>
        success(Stage.Validation, newCandidate = Some(value), newcandidateparent = Some(continuation.parent))
      case PhaseManagementCandidateObserved(value, paths) if stage == Stage.PhaseInspection && phaseWide &&
          validSnapshot(value) && value.head == tip && paths != null && paths.nonEmpty &&
          paths.distinct == paths && paths.forall(path) &&
          plan.steps.forall(step => paths.forall(step.ownedPaths.contains)) &&
          completed.lastOption.exists(_.commit.tree != value.tree) =>
        success(Stage.PhaseValidation, newCandidate = Some(value))
      case PhaseFindingRecordsObserved(reviewed, value, paths) if stage == Stage.PhaseClosing && phaseWide &&
          closureIntent.isEmpty && phaseReviewedCandidate.isEmpty && candidate.contains(reviewed) &&
          checksReceipt.nonEmpty && reviewEvidence.nonEmpty && validSnapshot(reviewed) && validSnapshot(value) &&
          reviewed.head == tip && value.head == tip && reviewed.tree != value.tree && paths != null && paths.nonEmpty &&
          paths.size <= 256 && paths.distinct == paths && paths.forall(p => path(p) && p.endsWith(".md")) &&
          plan.steps.forall(step => paths.forall(step.ownedPaths.contains)) =>
        success(Stage.PhaseClosing, newCandidate = Some(value), newPhaseReviewed = Some(reviewed), newPhasePaths = paths)
      case ChecksObserved(value, selected, receipt, passed) if candidate.contains(value) &&
          selected == checks && text(receipt) &&
          (stage == Stage.PhaseValidation || (stage == Stage.Validation && (!passed || step.actionPolicy.isEmpty))) =>
        if (passed) success(if (phaseWide) Stage.PhaseReview else Stage.Review,
          newChecks = Some(receipt))
        else success(Stage.Decision, reason = Some("Selected native checks failed: " + receipt))
      case StepAssessed(assessment) if stage == Stage.Validation && !phaseWide && GoalPhaseStepAssessment.validResult(assessment) &&
          step.actionPolicy.contains(assessment.policy) && candidate.contains(assessment.candidate) &&
          assessment.checks == checks && text(assessment.validationReference) &&
          Set("admit", "detailed-review", "triage", "decision")(assessment.branch) =>
        assessment.branch match {
          case "admit" if GoalPhaseStepAssessment.validAdmission(GoalPhaseStepAssessment.admission(assessment)) =>
            success(Stage.Closing, newChecks = Some(assessment.validationReference),
              newAdmission = Some(GoalPhaseStepAssessment.admission(assessment)))
          case "triage" if assessment.triage == "required" && assessment.triageJudgment.isEmpty =>
            success(Stage.Triage, newChecks = Some(assessment.validationReference))
          case "detailed-review" if assessment.policy.detailedReview == "required" ||
              assessment.policy.observations.exists(_.timing == "immediate") || assessment.scenarioSignals.exists(_.missing.nonEmpty) =>
            success(Stage.Review, newChecks = Some(assessment.validationReference))
          case "decision" if assessment.policy.observations.exists(_.timing == "immediate") =>
            success(Stage.Decision, newChecks = Some(assessment.validationReference), reason = Some(assessment.reason))
          case _ => invalid("assessment branch contradicts its selected policy")
        }
      case TriageAdmitted(value, assessment, judgment) if stage == Stage.Triage && !phaseWide &&
          value == key(WorkKind.TriageStep) && GoalPhaseStepAssessment.validResult(assessment) &&
          assessment.branch == "triage" && step.actionPolicy.contains(assessment.policy) &&
          candidate.contains(assessment.candidate) && checksReceipt.contains(assessment.validationReference) &&
          assessment.checks == checks && GoalPhaseStepTriage.valid(judgment) =>
        val resolved = GoalPhaseStepAssessment.triaged(assessment, judgment)
        if (!GoalPhaseStepAssessment.validResult(resolved)) invalid("triage contradicts its selected policy")
        else if (judgment.outcome == "clean") success(Stage.Closing,
          newAdmission = Some(GoalPhaseStepAssessment.admission(resolved)))
        else success(Stage.Review)
      case RepairableChecksObserved(value, selected, receipt, directive) if
          (stage == Stage.Validation || stage == Stage.PhaseValidation) && candidate.contains(value) &&
          selected == checks && text(receipt) && _valid_directive(directive) =>
        if (generation < Long.MaxValue && repairs < plan.repairLimit)
          success(Stage.Repair, newGeneration = generation + 1, newRepairs = repairs + 1,
            newOrdinaryRepairs = ordinaryRepairs + 1, newDirective = Some(directive), clear = true)
        else success(Stage.Decision, newChecks = Some(receipt), newDirective = Some(directive),
          newPendingRepair = Option.when(plan.repairLimit > 0 && generation < Long.MaxValue)(directive),
          reason = Some("Selected repair window requires a Human decision: " + receipt))
      case CoveredReviewCompletionObserved(snapshot, validation, originalgeneration, originalcandidate,
          originalreview, completion) if stage == Stage.Review && !phaseWide &&
          candidate.contains(snapshot) && checksReceipt.contains(validation) && text(validation) &&
          generation > 1 && originalgeneration == generation - 1 && repairs > 0 &&
          validSnapshot(snapshot) && validSnapshot(originalcandidate) &&
          originalcandidate.head == snapshot.head && snapshot.head == candidateBase.head &&
          originalcandidate.tree != snapshot.tree && text(originalreview) && text(completion) &&
          closureIntent.isEmpty => success(Stage.Closing, newReview = Some(originalreview))
      case ReviewAdmitted(value, snapshot, validation, evidence, outcome, directive) if
          candidate.contains(snapshot) && checksReceipt.contains(validation) && text(evidence) &&
          ((stage == Stage.Review && value == key(WorkKind.ReviewStep)) ||
            (stage == Stage.PhaseReview && value == key(WorkKind.ReviewPhase))) => outcome match {
        case ReviewOutcome.Accept => success(if (phaseWide) Stage.PhaseClosing else Stage.Closing,
          newReview = Some(evidence))
        case ReviewOutcome.Repair if generation < Long.MaxValue && repairs < plan.repairLimit =>
          if (directive.exists(_valid_directive))
            success(Stage.Repair, newGeneration = generation + 1, newRepairs = repairs + 1,
              newOrdinaryRepairs = ordinaryRepairs + 1, newDirective = directive, clear = true)
          else invalid("repair requires the independent review's assigned reason and findings")
        case ReviewOutcome.Repair if plan.repairLimit > 0 &&
            repairs >= plan.repairLimit && generation < Long.MaxValue && directive.exists(_valid_directive) =>
          success(Stage.Decision, newReview = Some(evidence), newDirective = directive,
            newPendingRepair = directive,
            reason = Some("Independent review exhausted the selected repair window: " + evidence))
        case _ => success(Stage.Decision, reason = Some("Independent review requires a Human decision: " + evidence))
      }
      case ClosureIntentSaved(value, identity) if candidate.contains(value) && text(identity) &&
          closureIntent.isEmpty && (stage == Stage.Closing || stage == Stage.PhaseClosing) =>
        success(stage, newIntent = Some(identity))
      case ClosureUnknown(identity) if closureIntent.contains(identity) &&
          (stage == Stage.Closing || stage == Stage.PhaseClosing) => Consequence.success(this)
      case StepCommitObserved(intent, commit) if stage == Stage.Closing &&
          closureIntent.contains(intent) && validCommit(commit) && commit.parent == candidateBase.head &&
          candidate.exists(_.tree == commit.tree) && commit.identity != candidateBase.head && generation < Long.MaxValue =>
        _close_step_c(commit)
      case StepEofCorrectionCommitObserved(intent, commit, paths) if stage == Stage.Closing && !phaseWide &&
          closureIntent.contains(intent) && checksReceipt.nonEmpty && (reviewEvidence.nonEmpty || stepAdmission.nonEmpty) &&
          validCommit(commit) && commit.parent == candidateBase.head && commit.identity != candidateBase.head && generation < Long.MaxValue &&
          candidate.exists(_.tree != commit.tree) && paths != null && paths.nonEmpty && paths.size <= 256 &&
          paths.distinct == paths && paths.forall(value => path(value) &&
            step.ownedPaths.exists(owner => value == owner || value.startsWith(owner + "/"))) =>
        _close_step_c(commit)
      case PhaseLocallyCompleted(snapshot, commits, review) if stage == Stage.PhaseClosing &&
          cursor == plan.steps.size && completed.map(_.step) == plan.steps.map(_.identity) &&
          candidate.contains(snapshot) && snapshot == tipSnapshot && phaseReviewedCandidate.isEmpty &&
          phaseRecordingPaths.isEmpty && checksReceipt.nonEmpty && reviewEvidence.contains(review) &&
          commits == completed.map(_.commit) => success(Stage.Closed)
      case ReleaseCommitObserved(commit) if stage == Stage.PhaseClosing && releaseRepairActive && releaseCommit.isEmpty &&
          closureIntent.isEmpty && checksReceipt.nonEmpty && reviewEvidence.nonEmpty &&
          validCommit(commit) && commit.parent == tipSnapshot.head && commit.identity != tipSnapshot.head &&
          candidate.exists(c => c.head == tipSnapshot.head && c.tree == commit.tree && c.tree != tipSnapshot.tree) =>
        success(Stage.PhaseClosing, newCandidate = Some(Snapshot(commit.identity, commit.tree)),
          newPhaseReviewed = None, newPhasePaths = Vector.empty, newReleaseCommit = Some(commit))
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
          repairDirective, Some(stage), true, ordinaryRepairs, pendingRepair, phaseReviewedCandidate, phaseRecordingPaths, candidateParent,
          stepAdmission = stepAdmission, releaseCommit = releaseCommit))
      case PhaseValidationRetryAdmitted(snapshot, selected, failed, direction) if stage == Stage.Decision &&
          decisionSource.contains(Stage.PhaseValidation) && !documentDecision && pendingRepair.isEmpty &&
          closureIntent.isEmpty && phaseWide && completed.map(_.step) == plan.steps.map(_.identity) &&
          candidate.contains(snapshot) && validSnapshot(snapshot) && selected == plan.phaseChecks && selected.size == 1 &&
          text(failed) && text(direction) && generation < Long.MaxValue =>
        success(Stage.PhaseValidation, newGeneration = generation + 1, newChecks = None, newReview = None,
          newIntent = None, reason = None, newPhaseReviewed = None, newPhasePaths = Vector.empty)
      case RepairContinuationAdmitted(evidence) if stage == Stage.Decision && text(evidence) &&
          !documentDecision && plan.repairLimit > 0 && repairs >= plan.repairLimit &&
          generation < Long.MaxValue && pendingRepair.exists(_valid_directive) &&
          decisionSource.exists(Set(Stage.Validation, Stage.Review, Stage.PhaseValidation, Stage.PhaseReview)) =>
        success(Stage.Repair, newGeneration = generation + 1, newRepairs = 1,
          newOrdinaryRepairs = 1, newDirective = pendingRepair, newPendingRepair = None,
          reason = None, clear = true)
      case DecisionResumptionAdmitted(evidence) if stage == Stage.Decision && text(evidence) &&
          documentDecision && decisionSource.exists(value => value != Stage.Decision && value != Stage.Closed) =>
        success(decisionSource.get, reason = None)
      case DecisionResumptionAdmitted(evidence) if stage == Stage.Decision && text(evidence) &&
          !documentDecision && pendingRepair.isEmpty && generation < Long.MaxValue && decisionSource.exists(Set(Stage.Implementation, Stage.Repair,
            Stage.Review, Stage.PhaseReview)) =>
        success(decisionSource.get, newGeneration = generation + 1, reason = None)
      case _ => invalid("event differs from the Workflow-assigned ordered Phase frontier")
    }

    private def _close_step_c(commit: Commit): Consequence[State] = {
      val closed = completed :+ ClosedStep(step.identity, commit, reviewEvidence, stepAdmission)
      val nextcursor = cursor + 1
      Consequence.success(new State(plan,
        if (nextcursor == plan.steps.size) Stage.PhaseInspection else Stage.Implementation,
        nextcursor, generation + 1, 0, base, closed, None, None, None, None, None, None, None, None))
    }

    private def success(nextStage: Stage, newCandidate: Option[Snapshot] = candidate,
      newChecks: Option[String] = checksReceipt, newReview: Option[String] = reviewEvidence,
      newIntent: Option[String] = closureIntent, reason: Option[String] = decision,
      newRepository: Option[String] = repositoryReceipt, newGeneration: Long = generation,
      newRepairs: Int = repairs,
      newOrdinaryRepairs: Int = ordinaryRepairs,
      newDirective: Option[RepairDirective] = repairDirective,
      newPendingRepair: Option[RepairDirective] = pendingRepair,
      newPhaseReviewed: Option[Snapshot] = phaseReviewedCandidate,
      newPhasePaths: Vector[String] = phaseRecordingPaths,
      newcandidateparent: Option[Snapshot] = candidateParent,
      newAdmission: Option[GoalPhaseStepAssessment.Admission] = stepAdmission,
      newReleaseCommit: Option[Commit] = releaseCommit,
      clear: Boolean = false): Consequence[State] = Consequence.success(new State(plan, nextStage,
      cursor, newGeneration, newRepairs, base, completed, if (clear) None else newCandidate,
      if (clear) None else newChecks, if (clear) None else newReview,
      if (clear) None else newIntent, reason, newRepository, newDirective,
      if (nextStage == Stage.Decision) decisionSource.orElse(Some(stage)) else None,
      ordinaryRepairs = newOrdinaryRepairs, pendingRepair = newPendingRepair,
      phaseReviewedCandidate = if (clear) None else newPhaseReviewed,
      phaseRecordingPaths = if (clear) Vector.empty else newPhasePaths,
      candidateParent = if (clear) None else newcandidateparent,
      stepAdmission = if (clear) None else newAdmission, releaseCommit = newReleaseCommit))
  }

  def startC(plan: Plan, initial: Snapshot): Consequence[State] = for {
    _ <- validatePlanC(plan)
    _ <- if (validSnapshot(initial)) Consequence.unit else invalid("native initial Git snapshot is missing")
  } yield new State(plan, Stage.Implementation, 0, 1, 0, initial, Vector.empty,
    None, None, None, None, None, None, None, None)

  /** Exact raw delta for the narrow Review continuation. Existing exclusions,
    * defaults and Usage Contract retain their original meaning and ordering.
    */
  private[admission] def reviewRepairConditionC(current: Plan, proposed: Plan):
      Consequence[GoalPhaseAcceptanceBoundary.Condition] = for {
    _ <- validatePlanC(current)
    _ <- GoalPhaseAcceptanceBoundary.savedPlanC(current)
    _ <- validatePlanC(proposed)
    _ <- if (current.effectiveAcceptance.exists(boundary =>
        GoalPhaseAcceptanceBoundary.applicable(boundary, "ReviewStep").isEmpty) &&
        proposed.effectiveAcceptance.isEmpty && proposed.revision != current.revision &&
        proposed.copy(revision = current.revision, acceptance = current.acceptance) ==
          current.copy(effectiveAcceptance = None)) Consequence.unit
      else invalid("Review continuation requires a configured boundary without applicable Step conditions and an otherwise exact Plan")
    prior <- current.acceptance.map(Consequence.success).getOrElse(
      invalid("Review continuation requires the saved adopted Usage Contract"))
    adoption <- proposed.acceptance.map(Consequence.success).getOrElse(invalid("Review continuation requires its added condition"))
    _ <- if (adoption.usage == prior.usage && adoption.conditions.size == prior.conditions.size + 1 &&
        adoption.conditions.take(prior.conditions.size) == prior.conditions) Consequence.unit
      else invalid("Review continuation may only append one raw condition to the unchanged Usage Contract")
    condition = adoption.conditions.last
    _ <- if (condition.scope == "step" && condition.required &&
        !current.effectiveAcceptance.get.conditions.exists(_.identity == condition.identity) &&
        !prior.conditions.exists(_.identity == condition.identity)) Consequence.unit
      else invalid("Review continuation requires one fresh required Step condition; exclusions cannot be promoted")
  } yield condition

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
          !identities(s.checks) || s.checks.isEmpty || s.workClassification == null || s.actionPolicy == null ||
          s.workClassification.exists(value => value == null || value.validateC.toOption.isEmpty))
        invalid("Step coverage, earlier dependencies, selected checks or WorkClassification are invalid")
      else s.actionPolicy.fold(Consequence.unit)(GoalPhaseStepAssessment.validateC(_, s, plan))
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
