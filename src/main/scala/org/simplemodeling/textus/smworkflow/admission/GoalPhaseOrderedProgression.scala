package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.Consequence
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.cncf.workflow.ContextReference
import GoalPhaseWorkClassification.given
import GoalPhaseRuntimeRepairControl as R

/** Deterministic Phase controller, not a Service or a persistence provider.
  * Native adapters must admit selection, actual command receipts and independent
  * review before submitting events. These values are NOT permission tokens.
  * Semantic replies carry no stage, cursor, command or closure receipt.
  * @since   Oct.  6, 2026
  * @version Oct. 28, 2026
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
    effectiveAcceptance: Option[GoalPhaseAcceptanceBoundary.Effective] = None,
    phaseRepairInventory: Vector[String] = Vector.empty) {
    def phaseOwnedPaths: Vector[String] = (steps.flatMap(_.ownedPaths) ++ phaseRepairInventory).distinct
    def phasePathsOwned(paths: Vector[String]): Boolean =
      paths != null && paths.distinct == paths && paths.forall(path =>
        steps.exists(_.ownedPaths.exists(owner => path == owner || path.startsWith(owner + "/"))) ||
          phaseRepairInventory.contains(path))
  }
  object Plan {
    given Encoder[Plan] = deriveEncoder[Plan].mapJson(_.mapObject(_.filter {
      case ("phaseReviewClassification" | "acceptance" | "effectiveAcceptance", value) => !value.isNull
      case ("phaseRepairInventory", value) => !value.asArray.exists(_.isEmpty)
      case _ => true
    }))
    private val _decoder: Decoder[Plan] = deriveDecoder
    given Decoder[Plan] = Decoder.instance { cursor =>
      val selected = if (cursor.downField("phaseRepairInventory").succeeded) cursor.value
        else cursor.value.mapObject(_.add("phaseRepairInventory", Json.arr()))
      _decoder.decodeJson(selected).flatMap(value =>
        if (_valid_phase_inventory(value.phaseRepairInventory)) Right(value)
        else Left(io.circe.DecodingFailure("Phase repair inventory requires finite unique literal relative paths", cursor.history)))
    }
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
  final case class StepCommitChain(originalParent: Snapshot, partial: Commit, completion: Commit)
  object StepCommitChain {
    given Encoder[StepCommitChain] = deriveEncoder
    given Decoder[StepCommitChain] = deriveDecoder
  }
  def validStepCommitChain(value: StepCommitChain): Boolean = value != null &&
    validSnapshot(value.originalParent) && validCommit(value.partial) && validCommit(value.completion) &&
    value.partial.parent == value.originalParent.head && value.completion.parent == value.partial.identity &&
    Vector(value.originalParent.head, value.partial.identity, value.completion.identity).distinct.size == 3 &&
    value.partial.tree != value.completion.tree
  final case class ClosedStep(step: String, commit: Commit, review: Option[String],
    admission: Option[GoalPhaseStepAssessment.Admission] = None,
    partialCompletion: Option[StepCommitChain] = None)
  object ClosedStep {
    def apply(step: String, commit: Commit, review: String): ClosedStep = new ClosedStep(step, commit, Some(review))
    given Encoder[ClosedStep] = deriveEncoder[ClosedStep].mapJson(_.mapObject(_.filter {
      case ("admission" | "partialCompletion", value) => !value.isNull
      case _ => true
    }))
    private val _decoder: Decoder[ClosedStep] = deriveDecoder
    given Decoder[ClosedStep] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (!cursor.downField("partialCompletion").focus.exists(_.isNull) && value.partialCompletion.forall(c =>
          validStepCommitChain(c) && c.completion == value.commit)) Right(value)
      else Left(io.circe.DecodingFailure("Closed Step partial chain is malformed or crossed", cursor.history))))
  }
  def validStepAcceptance(step: ClosedStep): Boolean = step != null && step.review != null && step.admission != null &&
    step.partialCompletion != null && step.partialCompletion.forall(chain => validStepCommitChain(chain) &&
      chain.completion == step.commit) &&
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
  final case class IncompleteChecksObserved(snapshot: Snapshot, selected: Vector[String], receipt: String) extends Event
  final case class TriageAdmitted(key: WorkKey, assessment: GoalPhaseStepAssessment.Result,
    judgment: GoalPhaseStepTriage.Judgment) extends Event
  final case class StepAssessed(assessment: GoalPhaseStepAssessment.Result) extends Event
  final case class RepairableChecksObserved(snapshot: Snapshot, selected: Vector[String],
    receipt: String, directive: RepairDirective, comparableFindings: Boolean = true) extends Event
  final case class ReviewAdmitted(key: WorkKey, snapshot: Snapshot, checksReceipt: String,
    evidence: String, outcome: ReviewOutcome, repairDirective: Option[RepairDirective] = None,
    allowPhaseRepair: Boolean = false) extends Event
  /** Mechanical admission by the native completion owner; this event grants no effect authority. */
  final case class CoveredReviewCompletionObserved(snapshot: Snapshot, checksReceipt: String,
    originalGeneration: Long, originalCandidate: Snapshot, originalReviewReference: String,
    completionReference: String) extends Event
  enum ReviewOutcome { case Accept, Repair, Human }
  /** Durable intent must have committed before the native executor runs. */
  final case class ClosureIntentSaved(snapshot: Snapshot, identity: String) extends Event
  final case class ClosureUnknown(identity: String) extends Event
  final case class StepPartialCompletionObserved(intent: String, chain: StepCommitChain,
    expectedTree: String, eofCorrections: Vector[String]) extends Event
  final case class StepCommitObserved(intent: String, commit: Commit) extends Event
  final case class StepPreparedCommitObserved(intent: String, commit: Commit, paths: Vector[String]) extends Event
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
  /** Eligibility only; the native owner proves the distinct actual terminal
    * Failed or confirmed timeout source and independently verifies Human direction.
    * The historical receipt remains unchanged; no corrective Step is required.
    */
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
    val releaseCommit: Option[Commit] = None,
    val repairControl: Option[R.Context] = None,
    val policyRepairPending: Boolean = false,
    val repairObservations: Vector[R.Observation] = Vector.empty) {
    def selectedRepairLimit: Int = repairControl.map(_.selectedLimit).getOrElse(plan.repairLimit)
    def effectiveRepairLimit: Int = repairControl.map(_.effectiveLimit).getOrElse(plan.repairLimit)
    private def _repair_scope: String = Json.arr(Json.fromString(plan.identity), Json.fromString(plan.revision),
      Json.fromString(if (cursor == plan.steps.size) "Release" else "Step"),
      Json.fromString(if (cursor == plan.steps.size) plan.phase else plan.steps(cursor).identity)).noSpaces
    private def _observed_repair(directive: RepairDirective, reference: String, comparable: Boolean): Vector[R.Observation] =
      if (comparable) R.Context(R.version, selectedRepairLimit, repairObservations)
        .observe(_repair_scope, reference, directive.findings).observations
      else repairObservations.filter(_.scope == _repair_scope)
    private def _reserve_repair_c(directive: RepairDirective, reference: String,
      legacyreason: String, comparable: Boolean = true): Consequence[State] = {
      val observations = _observed_repair(directive, reference, comparable)
      val observed = repairControl.map(_.copy(observations = observations))
      val trend = observed.flatMap(_.stop)
      if (generation < Long.MaxValue && repairs < effectiveRepairLimit && trend.isEmpty)
        R.admitBatchC(repairs, ordinaryRepairs, effectiveRepairLimit).flatMap(counts =>
          success(Stage.Repair, newGeneration = generation + 1, newRepairs = counts.automatic,
            newOrdinaryRepairs = counts.ordinary, newDirective = Some(directive),
            newPendingRepair = None, newcontrol = observed, newpolicypending = false,
            newobservations = observations, reason = None, clear = true))
      else success(Stage.Decision, newDirective = Some(directive),
        newReview = if (Set(Stage.Review, Stage.PhaseReview)(stage)) Some(reference) else reviewEvidence,
        newPendingRepair = Option.when(effectiveRepairLimit > 0 && generation < Long.MaxValue)(directive),
        newcontrol = observed, newpolicypending = false, newobservations = observations,
        reason = Some(trend.getOrElse(repairControl.map(c => c.policy + ": runtime repair budget exhausted; " +
          "selected=" + c.selectedLimit + "; effective=" + c.effectiveLimit + "; evidence=" + reference).getOrElse(legacyreason))))
    }

    /** Policy adoption neither reserves a batch nor opens a continuation window. */
    private[admission] def adoptRepairPolicyC(choice: String, evidence: String,
      restoration: Option[R.RestorationLink] = None): Consequence[State] = for {
      _ <- if (text(evidence) && restoration != null && restoration.forall(R.validLink) &&
          Set("AdoptRuntimePolicy", "RetainSelectedLimit")(choice) && stage != Stage.Closed &&
          closureIntent.isEmpty && !documentDecision &&
          (stage != Stage.Decision || pendingRepair.nonEmpty || decisionSource.contains(Stage.Repair))) Consequence.unit
        else invalid("repair policy adoption requires the unchanged eligible repair frontier")
      selected = if (choice == "AdoptRuntimePolicy") R.ceiling else selectedRepairLimit
      _ <- if (selected >= 0 && selected <= R.ceiling && (choice != "RetainSelectedLimit" || selectedRepairLimit <= R.ceiling))
        Consequence.unit else invalid("retained selected repair allowance exceeds the runtime ceiling")
      context = R.Context(R.version, selected, repairObservations, restoration)
      _ <- if (repairControl.forall(c => c.policy != context.policy || c.selectedLimit != context.selectedLimit ||
          c.restoration != context.restoration)) Consequence.unit else invalid("repair policy is already adopted")
      adopted = new State(plan, stage, cursor, generation, repairs, base, completed, candidate, checksReceipt,
        reviewEvidence, closureIntent, decision, repositoryReceipt, repairDirective, decisionSource,
        documentDecision, ordinaryRepairs, pendingRepair, phaseReviewedCandidate, phaseRecordingPaths,
        candidateParent, stepAdmission, releaseCommit, Some(context),
        pendingRepair.nonEmpty && repairs < context.effectiveLimit && context.stop.isEmpty,
        repairObservations)
      resumed <- if (stage == Stage.Decision && pendingRepair.isEmpty && decisionSource.contains(Stage.Repair))
        adopted.acceptC(DecisionResumptionAdmitted(evidence)) else Consequence.success(adopted)
    } yield resumed

    /** Called only by ordinary issue dispatch (and reconstruction of that issue). */
    private[admission] def dispatchPolicyRepairC: Consequence[State] =
      if (policyRepairPending && stage == Stage.Decision && pendingRepair.exists(_valid_directive) &&
          repairControl.nonEmpty && repairs < effectiveRepairLimit && generation < Long.MaxValue)
        R.admitBatchC(repairs, ordinaryRepairs, effectiveRepairLimit).flatMap(counts =>
          success(Stage.Repair, newGeneration = generation + 1, newRepairs = counts.automatic,
            newOrdinaryRepairs = counts.ordinary, newDirective = pendingRepair, newPendingRepair = None,
            newpolicypending = false, reason = None, clear = true))
      else invalid("repair policy has no eligible unreserved pending batch")
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

    /** Eligibility only. The native owner admits a separate actual Human
      * directive and supersedes the retained unanswered legacy question. */
    private[admission] def continueLegacyReleaseRepairC(evidence: String,
      directive: RepairDirective): Consequence[State] = for {
      _ <- if (text(evidence) && !evidence.exists(_.isControl) && _valid_directive(directive) &&
          directive.findings.size <= 128 && directive.findings.forall(GoalPhasePurposeRepair.text)) Consequence.unit
        else invalid("legacy Release continuation requires its independently admitted exact blocker batch")
      _ <- if (stage == Stage.Decision && decisionSource.contains(Stage.PhaseReview) && !documentDecision &&
          phaseWide && completed.map(_.step) == plan.steps.map(_.identity) && pendingRepair.isEmpty &&
          closureIntent.isEmpty && phaseReviewedCandidate.isEmpty && phaseRecordingPaths.isEmpty && releaseCommit.isEmpty &&
          generation < Long.MaxValue && repairs < effectiveRepairLimit && candidate.exists(validSnapshot) &&
          checksReceipt.exists(text)) Consequence.unit
        else invalid("legacy Release continuation requires the original completed-Plan Phase review Decision with remaining allowance")
      next <- _reserve_repair_c(directive, evidence, "Legacy Release repair allowance exhausted")
    } yield next

    /** Eligibility only. The native owner proves the exact retained terminal
      * failed check and appends a distinct admission; old failed events stay Decision.
      */
    private[admission] def continueInitialPhaseCheckRepairC(failedReceipt: String,
      directive: RepairDirective): Consequence[State] = for {
      _ <- if (text(failedReceipt) && !failedReceipt.exists(_.isControl) && _valid_directive(directive) &&
          directive.findings.size <= 128 && directive.findings.forall(GoalPhasePurposeRepair.text)) Consequence.unit
        else invalid("initial Phase check repair requires its exact failed receipt and bounded directive")
      _ <- if (stage == Stage.Decision && decisionSource.contains(Stage.PhaseValidation) &&
          decision.contains("Selected native checks failed: " + failedReceipt) && !documentDecision &&
          phaseWide && completed.map(_.step) == plan.steps.map(_.identity) &&
          completed.forall(validStepAcceptance) && repairs == 0 && ordinaryRepairs == 0 &&
          repairDirective.isEmpty && pendingRepair.isEmpty && closureIntent.isEmpty &&
          reviewEvidence.isEmpty && phaseReviewedCandidate.isEmpty && phaseRecordingPaths.isEmpty &&
          releaseCommit.isEmpty && repositoryReceipt.isEmpty && candidate.exists(validSnapshot) &&
          repairControl.exists(_.policy == R.version) && effectiveRepairLimit > repairs &&
          generation < Long.MaxValue) Consequence.unit
        else invalid("initial Phase check repair requires its completed-Plan failed Decision and adopted remaining runtime allowance")
      next <- _reserve_repair_c(directive, failedReceipt, "Initial Phase check repair allowance exhausted", comparable = false)
      _ <- if (next.stage == Stage.Repair) Consequence.unit
        else invalid("initial Phase check repair has no reservable ordinary batch")
    } yield next

    /** The native owner separately proves a complete genuinely terminated
      * InvalidReport receipt and appends its own immutable admission. Historical
      * ChecksObserved(false) remains Decision; this reserves one NEW batch.
      */
    private[admission] def continueStepTestReportRepairC(failedReceipt: String,
      directive: RepairDirective): Consequence[State] = for {
      _ <- if (text(failedReceipt) && !failedReceipt.exists(_.isControl) && _valid_directive(directive) &&
          stage == Stage.Decision && decisionSource.contains(Stage.Validation) &&
          decision.contains("Selected native checks failed: " + failedReceipt) && !phaseWide && !documentDecision &&
          pendingRepair.isEmpty && closureIntent.isEmpty && reviewEvidence.isEmpty &&
          candidate.exists(validSnapshot) && completed.map(_.step) == plan.steps.take(cursor).map(_.identity) &&
          completed.forall(validStepAcceptance) && releaseCommit.isEmpty && repositoryReceipt.isEmpty &&
          repairControl.exists(_.policy == R.version) && repairs < effectiveRepairLimit && generation < Long.MaxValue)
        Consequence.unit else invalid("Step test report repair requires its exact failed Step Decision and remaining runtime allowance")
      next <- _reserve_repair_c(directive, failedReceipt, "Step test report repair allowance exhausted", comparable = false)
      _ <- if (next.stage == Stage.Repair) Consequence.unit else invalid("Step test report repair has no reservable ordinary batch")
    } yield next

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
          proposed.phaseRepairInventory == plan.phaseRepairInventory &&
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
      documentDecision = false, ordinaryRepairs = ordinaryRepairs, pendingRepair = None,
      stepAdmission = None, releaseCommit = releaseCommit, repairControl = repairControl, repairObservations = repairObservations)

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
          proposed.phaseRepairInventory == plan.phaseRepairInventory &&
          proposed.phaseReviewClassification == plan.phaseReviewClassification &&
          proposed.steps.size == plan.steps.size && proposed.steps.take(cursor) == plan.steps.take(cursor) &&
          proposed.steps.drop(cursor).zip(plan.steps.drop(cursor)).forall { (next, prior) =>
            next.copy(purpose = prior.purpose, checks = prior.checks) == prior
          }) Consequence.unit
        else invalid("Remaining Plan adoption must preserve the accepted prefix, every Step scope and closure policy")
    } yield new State(proposed, Stage.Implementation, cursor, generation + 1, repairs, base, completed,
      None, None, None, None, None, repositoryReceipt, None, None,
      documentDecision = false, ordinaryRepairs = ordinaryRepairs, pendingRepair = None,
      stepAdmission = None, releaseCommit = releaseCommit, repairControl = repairControl, repairObservations = repairObservations)

    /** Direction only: the native owner separately binds the actual Component,
      * finite host registration and independently admitted original-purpose
      * authority or Human directive.
      */
    private[admission] def adoptCurrentInventoryRevisionC(proposed: Plan, evidence: String): Consequence[State] = for {
      _ <- if (text(evidence) && !evidence.exists(_.isControl) && stage == Stage.Decision &&
          !documentDecision && pendingRepair.isEmpty && closureIntent.isEmpty && phaseReviewedCandidate.isEmpty &&
          cursor == completed.size && cursor < plan.steps.size && generation < Long.MaxValue &&
          decisionSource.exists(Set(Stage.Implementation, Stage.Repair))) Consequence.unit
        else invalid("Current inventory adoption requires admitted authority and its unanswered current implementation or repair Decision")
      _ <- currentInventoryPathsC(plan, proposed, step.identity)
    } yield new State(proposed, Stage.Implementation, cursor, generation + 1, repairs, base, completed,
      None, None, None, None, None, repositoryReceipt, None, None,
      documentDecision = false, ordinaryRepairs = ordinaryRepairs, pendingRepair = None,
      stepAdmission = None, releaseCommit = releaseCommit, repairControl = repairControl, repairObservations = repairObservations)

    /** Explicit direction changes only the outstanding adopted Phase boundary.
      * Successful checks and the immutable accepted prefix remain actual evidence.
      */
    private[admission] def adoptPhaseAcceptanceDeferralC(proposed: Plan, evidence: String): Consequence[State] = for {
      _ <- if (text(evidence) && !evidence.exists(_.isControl) && stage == Stage.Decision &&
          decisionSource.contains(Stage.PhaseReview) && cursor == plan.steps.size && cursor == completed.size &&
          completed.map(_.step) == plan.steps.map(_.identity) && completed.forall(s =>
            validStepAcceptance(s) && validCommit(s.commit)) &&
          completed.zipWithIndex.forall { (s, i) => s.partialCompletion.map(_.originalParent.head).getOrElse(s.commit.parent) ==
            (if (i == 0) base.head else completed(i - 1).commit.identity) } &&
          validSnapshot(base) && candidate.exists(validSnapshot) && checksReceipt.exists(text) &&
          !documentDecision && pendingRepair.isEmpty && closureIntent.isEmpty && phaseReviewedCandidate.isEmpty &&
          phaseRecordingPaths.isEmpty && releaseCommit.isEmpty && !policyRepairPending &&
          repairControl.flatMap(_.stop).isEmpty && generation < Long.MaxValue) Consequence.unit
        else invalid("Phase acceptance deferral requires actual Human direction at the exact checked all-closed Phase review Decision")
      _ <- phaseAcceptanceDeferralConditionsC(plan, proposed)
    } yield new State(proposed, Stage.PhaseReview, cursor, generation + 1, repairs, base, completed,
      candidate, checksReceipt, None, closureIntent, None, repositoryReceipt, repairDirective, None,
      documentDecision, ordinaryRepairs, pendingRepair, phaseReviewedCandidate, phaseRecordingPaths,
      candidateParent, stepAdmission, releaseCommit, repairControl, policyRepairPending, repairObservations)

    /** Continue the already reserved phase repair with an exact inventory append.
      * No additional repair batch is consumed and no past acceptance is changed.
      */
    private[admission] def adoptPhaseRepairInventoryC(proposed: Plan, evidence: String): Consequence[State] = for {
      _ <- if (text(evidence) && !evidence.exists(_.isControl) &&
          (stage == Stage.Repair || (stage == Stage.Decision && decisionSource.contains(Stage.Repair))) &&
          cursor == plan.steps.size && cursor == completed.size &&
          completed.map(_.step) == plan.steps.map(_.identity) && completed.forall(validStepAcceptance) &&
          repairs > 0 && repairs <= effectiveRepairLimit && repairDirective.exists(_valid_directive) &&
          pendingRepair.isEmpty && !policyRepairPending && repairControl.flatMap(_.stop).isEmpty &&
          !documentDecision && closureIntent.isEmpty && phaseReviewedCandidate.isEmpty &&
          phaseRecordingPaths.isEmpty && releaseCommit.isEmpty && generation < Long.MaxValue) Consequence.unit
        else invalid("Phase repair inventory requires the all-closed active reserved phase repair and no stop or closure frontier")
      _ <- phaseRepairInventoryPathsC(plan, proposed)
    } yield new State(proposed, Stage.Repair, cursor, generation + 1, repairs, base, completed,
      None, None, None, None, None, repositoryReceipt, repairDirective, None,
      documentDecision = false, ordinaryRepairs = ordinaryRepairs, pendingRepair = None,
      repairControl = repairControl, repairObservations = repairObservations)

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
          generation < Long.MaxValue && repairs < effectiveRepairLimit &&
          candidate.exists(validSnapshot) && checksReceipt.exists(text)) Consequence.unit
        else invalid("Review remaining Plan adoption requires an unanswered current-Step Review Decision with remaining repair allowance")
      condition <- reviewRepairConditionC(plan, proposed.copy(effectiveAcceptance = None))
      expected = plan.effectiveAcceptance.map(boundary => boundary.copy(conditions = boundary.conditions :+
        GoalPhaseAcceptanceBoundary.EffectiveCondition(condition.identity, condition.scope, condition.required,
          condition.description, "adopted")))
      _ <- if (proposed.effectiveAcceptance == expected) Consequence.unit
        else invalid("Review remaining Plan adoption must preserve every saved effective condition and its source")
      selected = new State(proposed, stage, cursor, generation, repairs, base, completed,
        candidate, checksReceipt, reviewEvidence, closureIntent, decision, repositoryReceipt, repairDirective,
        decisionSource, documentDecision, ordinaryRepairs, pendingRepair, phaseReviewedCandidate,
        phaseRecordingPaths, candidateParent, stepAdmission, releaseCommit, repairControl,
        repairObservations = repairObservations)
      next <- selected._reserve_repair_c(directive, evidence, "Review repair allowance exhausted")
    } yield next

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
          cursor < plan.steps.size && cursor == completed.size && generation < Long.MaxValue && repairs < effectiveRepairLimit &&
          candidate.exists(validSnapshot) && checksReceipt.exists(text)) Consequence.unit
        else invalid("legacy purpose continuation requires its unanswered Step Review and remaining ordinary repair allowance")
      next <- _reserve_repair_c(directive, evidence, "Legacy purpose repair allowance exhausted")
    } yield next

    /** Eligibility only: the native owner must prove the original terminal
      * Failed or confirmed TimedOut/incomplete Phase validation through its
      * distinct source contract and independently admit the Human direction.
      * Accepted Steps remain immutable; only one new corrective Step is added.
      */
    def adoptFailedPhaseRepairRevisionC(proposed: Plan, evidence: String): Consequence[State] =
      _adopt_validation_repair_revision_c(proposed, evidence, false)

    def adoptValidationRepairRevisionC(proposed: Plan, evidence: String): Consequence[State] =
      _adopt_validation_repair_revision_c(proposed, evidence, true)

    private def _adopt_validation_repair_revision_c(proposed: Plan, evidence: String, historical: Boolean): Consequence[State] = for {
      _ <- validatePlanC(proposed)
      _ <- GoalPhaseAcceptanceBoundary.savedPlanC(proposed)
      _ <- if (text(evidence) && !evidence.exists(c => Character.isISOControl(c))) Consequence.unit
        else invalid("Failed Phase repair adoption requires its independent Human direction")
      _ <- if (((stage == Stage.Decision && decisionSource.contains(Stage.PhaseValidation)) ||
          (historical && stage == Stage.PhaseValidation)) &&
          !documentDecision && pendingRepair.isEmpty && closureIntent.isEmpty &&
          phaseReviewedCandidate.isEmpty && phaseRecordingPaths.isEmpty && reviewEvidence.isEmpty &&
          cursor == plan.steps.size && completed.map(_.step) == plan.steps.map(_.identity) &&
          plan.steps.size < 64 && plan.phaseChecks.size == 1 && candidate.exists(validSnapshot) &&
          generation < Long.MaxValue) Consequence.unit
        else invalid("Failed Phase repair adoption requires the original completed-Plan Phase validation Decision")
      _ <- if (proposed.identity == plan.identity && proposed.revision != plan.revision &&
          proposed.phase == plan.phase && proposed.repository == plan.repository &&
          proposed.repairLimit == plan.repairLimit && proposed.phaseChecks == plan.phaseChecks &&
          proposed.phaseRepairInventory == plan.phaseRepairInventory &&
          proposed.phaseReviewClassification == plan.phaseReviewClassification &&
          proposed.acceptance == plan.acceptance && proposed.effectiveAcceptance == plan.effectiveAcceptance &&
          proposed.steps.size == plan.steps.size + 1 && proposed.steps.take(plan.steps.size) == plan.steps &&
          proposed.steps.last.dependencies.toSet == plan.steps.map(_.identity).toSet) Consequence.unit
        else invalid("Failed Phase repair adoption must retain the complete accepted Plan and append exactly one dependent corrective Step")
    } yield new State(proposed, Stage.Implementation, cursor, generation + 1, repairs, base, completed,
      None, None, None, None, None, repositoryReceipt, None, None,
      documentDecision = false, ordinaryRepairs = ordinaryRepairs, pendingRepair = None,
      stepAdmission = None, releaseCommit = releaseCommit, repairControl = repairControl, repairObservations = repairObservations)

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
        Option.when(proposed.phaseRepairInventory != plan.phaseRepairInventory)("phase-repair-inventory"),
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
          releaseCommit = releaseCommit, repairControl = repairControl, policyRepairPending = policyRepairPending,
          repairObservations = repairObservations))
      }
    }

    private def _acceptance_usage(value: Plan): Option[String] = value.effectiveAcceptance.flatMap(_.usage)
    private def _acceptance_conditions(value: Plan, scopes: Set[String]): Map[String, GoalPhaseAcceptanceBoundary.EffectiveCondition] =
      value.effectiveAcceptance.toVector.flatMap(_.conditions).filter(c => scopes(c.scope)).map(c => c.identity -> c).toMap

    def next: Next = stage match {
      case Stage.Implementation => semantic(WorkKind.ImplementStep, step.purpose)
      case Stage.Repair => semantic(WorkKind.RepairStep, if (phaseWide)
        "Repair only the assigned blocking Release findings within the unchanged Plan scope; retain closed Steps and applicable prior judgments"
        else step.purpose)
      case Stage.InspectCandidate => Inspect(false, tip)
      case Stage.PhaseInspection => Inspect(true, base.head)
      case Stage.Validation | Stage.PhaseValidation => Validate(candidate.get, checks, phaseWide)
      case Stage.Triage => semantic(WorkKind.TriageStep, step.purpose)
      case Stage.Review => semantic(WorkKind.ReviewStep, step.purpose)
      case Stage.PhaseReview => semantic(WorkKind.ReviewPhase, if (releaseRepairActive)
        "Review the unresolved Release findings, repair delta and affected meaning; retain the prior integrated Phase review"
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
          plan.steps.forall(step => paths.forall(p => StandaloneFixExecution.owned(p, step.ownedPaths))) =>
        success(Stage.PhaseClosing, newCandidate = Some(value), newPhaseReviewed = Some(reviewed), newPhasePaths = paths)
      case IncompleteChecksObserved(value, selected, receipt) if candidate.contains(value) &&
          selected == checks && text(receipt) && (stage == Stage.Validation || stage == Stage.PhaseValidation) =>
        success(Stage.Decision, reason = Some("Selected native checks incomplete: " + receipt))
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
              newadmission = Some(GoalPhaseStepAssessment.admission(assessment)))
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
          newadmission = Some(GoalPhaseStepAssessment.admission(resolved)))
        else success(Stage.Review)
      case RepairableChecksObserved(value, selected, receipt, directive, comparable) if
          (stage == Stage.Validation || (stage == Stage.PhaseValidation && releaseRepairActive)) && candidate.contains(value) &&
          selected == checks && text(receipt) && _valid_directive(directive) =>
        _reserve_repair_c(directive, receipt, "Selected repair window requires a Human decision: " + receipt, comparable)
          .map(state => if (state.stage == Stage.Decision) new State(state.plan, state.stage, state.cursor,
            state.generation, state.repairs, state.base, state.completed, state.candidate, Some(receipt),
            state.reviewEvidence, state.closureIntent, state.decision, state.repositoryReceipt, state.repairDirective,
            state.decisionSource, state.documentDecision, state.ordinaryRepairs, state.pendingRepair,
            state.phaseReviewedCandidate, state.phaseRecordingPaths, state.candidateParent, state.stepAdmission,
            state.releaseCommit, state.repairControl, state.policyRepairPending, state.repairObservations) else state)
      case CoveredReviewCompletionObserved(snapshot, validation, originalgeneration, originalcandidate,
          originalreview, completion) if stage == Stage.Review && !phaseWide &&
          candidate.contains(snapshot) && checksReceipt.contains(validation) && text(validation) &&
          generation > 1 && originalgeneration == generation - 1 && repairs > 0 &&
          validSnapshot(snapshot) && validSnapshot(originalcandidate) &&
          originalcandidate.head == snapshot.head && snapshot.head == candidateBase.head &&
          originalcandidate.tree != snapshot.tree && text(originalreview) && text(completion) &&
          closureIntent.isEmpty => success(Stage.Closing, newReview = Some(originalreview))
      case ReviewAdmitted(value, snapshot, validation, evidence, outcome, directive, allowphaserepair) if
          candidate.contains(snapshot) && checksReceipt.contains(validation) && text(evidence) &&
          ((stage == Stage.Review && value == key(WorkKind.ReviewStep)) ||
            (stage == Stage.PhaseReview && value == key(WorkKind.ReviewPhase))) => outcome match {
        case ReviewOutcome.Accept => success(if (phaseWide) Stage.PhaseClosing else Stage.Closing,
          newReview = Some(evidence))
        case ReviewOutcome.Repair if (!phaseWide || allowphaserepair) && generation < Long.MaxValue &&
            repairs < effectiveRepairLimit && !directive.exists(_valid_directive) =>
          invalid("repair requires the independent review's assigned reason and findings")
        case ReviewOutcome.Repair if (!phaseWide || allowphaserepair) && generation < Long.MaxValue &&
            (effectiveRepairLimit > 0 || repairControl.nonEmpty) && directive.exists(_valid_directive) =>
          _reserve_repair_c(directive.get, evidence, "Independent review exhausted the selected repair window: " + evidence)
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
      case StepPartialCompletionObserved(intent, chain, expected, paths) if stage == Stage.Closing && !phaseWide &&
          closureIntent.contains(intent) && checksReceipt.nonEmpty && (reviewEvidence.nonEmpty || stepAdmission.nonEmpty) &&
          validStepCommitChain(chain) && chain.originalParent == candidateBase &&
          chain.completion.tree == expected && oid(expected) && candidate.exists(c =>
            c.tree == expected || (paths != null && paths.nonEmpty)) &&
          paths != null && paths.size <= 256 && paths.distinct == paths && paths.forall(value => path(value) &&
            step.ownedPaths.exists(owner => value == owner || value.startsWith(owner + "/"))) && generation < Long.MaxValue =>
        _close_step_c(chain.completion, Some(chain))
      case StepEofCorrectionCommitObserved(intent, commit, paths) if stage == Stage.Closing && !phaseWide &&
          closureIntent.contains(intent) && checksReceipt.nonEmpty && (reviewEvidence.nonEmpty || stepAdmission.nonEmpty) &&
          validCommit(commit) && commit.parent == candidateBase.head && commit.identity != candidateBase.head && generation < Long.MaxValue &&
          candidate.exists(_.tree != commit.tree) && paths != null && paths.nonEmpty && paths.size <= 256 &&
          paths.distinct == paths && paths.forall(value => path(value) &&
            step.ownedPaths.exists(owner => value == owner || value.startsWith(owner + "/"))) =>
        _close_step_c(commit)
      case StepPreparedCommitObserved(intent, commit, paths) if stage == Stage.Closing && !phaseWide &&
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
      case ReleaseCommitObserved(commit) if stage == Stage.PhaseClosing && (releaseRepairActive || (phaseReviewedCandidate.nonEmpty && phaseRecordingPaths.nonEmpty)) && releaseCommit.isEmpty &&
          closureIntent.isEmpty && checksReceipt.nonEmpty && reviewEvidence.nonEmpty && validCommit(commit) &&
          commit.parent == tipSnapshot.head && commit.identity != tipSnapshot.head &&
          candidate.exists(c => c.head == tipSnapshot.head && c.tree == commit.tree && c.tree != tipSnapshot.tree) =>
        success(Stage.PhaseClosing, newCandidate = Some(Snapshot(commit.identity, commit.tree)),
          newPhaseReviewed = None, newPhasePaths = Vector.empty, newreleasecommit = Some(commit))
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
          stepAdmission = stepAdmission, releaseCommit = releaseCommit, repairControl = repairControl,
          repairObservations = repairObservations))
      case PhaseValidationRetryAdmitted(snapshot, selected, failed, direction) if stage == Stage.Decision &&
          decisionSource.contains(Stage.PhaseValidation) && !documentDecision && pendingRepair.isEmpty &&
          closureIntent.isEmpty && phaseWide && completed.map(_.step) == plan.steps.map(_.identity) &&
          candidate.contains(snapshot) && validSnapshot(snapshot) && selected == plan.phaseChecks && selected.size == 1 &&
          text(failed) && text(direction) && generation < Long.MaxValue =>
        success(Stage.PhaseValidation, newGeneration = generation + 1, newChecks = None, newReview = None,
          newIntent = None, reason = None, newPhaseReviewed = None, newPhasePaths = Vector.empty)
      case RepairContinuationAdmitted(evidence) if stage == Stage.Decision && text(evidence) &&
          !documentDecision && effectiveRepairLimit > 0 &&
          (repairs >= effectiveRepairLimit || repairControl.flatMap(_.stop).nonEmpty) &&
          generation < Long.MaxValue && pendingRepair.exists(_valid_directive) &&
          decisionSource.exists(Set(Stage.Validation, Stage.Review, Stage.PhaseValidation, Stage.PhaseReview)) =>
        success(Stage.Repair, newGeneration = generation + 1, newRepairs = 1,
          newOrdinaryRepairs = 1, newDirective = pendingRepair, newPendingRepair = None,
          reason = None, newcontrol = repairControl.map(_.copy(observations = Vector.empty)),
          newobservations = Vector.empty, clear = true)
      case DecisionResumptionAdmitted(evidence) if stage == Stage.Decision && text(evidence) &&
          documentDecision && decisionSource.exists(value => value != Stage.Decision && value != Stage.Closed) =>
        success(decisionSource.get, reason = None)
      case DecisionResumptionAdmitted(evidence) if stage == Stage.Decision && text(evidence) &&
          !documentDecision && pendingRepair.isEmpty && generation < Long.MaxValue && decisionSource.exists(Set(Stage.Implementation, Stage.Repair,
            Stage.Review, Stage.PhaseReview)) =>
        success(decisionSource.get, newGeneration = generation + 1, reason = None)
      case _ => invalid("event differs from the Workflow-assigned ordered Phase frontier")
    }

    private def _close_step_c(commit: Commit, chain: Option[StepCommitChain] = None): Consequence[State] = {
      val closed = completed :+ ClosedStep(step.identity, commit, reviewEvidence, stepAdmission, chain)
      val nextcursor = cursor + 1
      Consequence.success(new State(plan,
        if (nextcursor == plan.steps.size) Stage.PhaseInspection else Stage.Implementation,
        nextcursor, generation + 1, 0, base, closed, None, None, None, None, None, None, None, None,
        stepAdmission = None, releaseCommit = releaseCommit,
        repairControl = repairControl.map(_.copy(observations = Vector.empty))))
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
      newadmission: Option[GoalPhaseStepAssessment.Admission] = stepAdmission,
      newreleasecommit: Option[Commit] = releaseCommit,
      newcontrol: Option[R.Context] = repairControl,
      newpolicypending: Boolean = policyRepairPending,
      newobservations: Vector[R.Observation] = repairObservations,
      clear: Boolean = false): Consequence[State] = Consequence.success(new State(plan, nextStage,
      cursor, newGeneration, newRepairs, base, completed, if (clear) None else newCandidate,
      if (clear) None else newChecks, if (clear) None else newReview,
      if (clear) None else newIntent, reason, newRepository, newDirective,
      if (nextStage == Stage.Decision) decisionSource.orElse(Some(stage)) else None,
      ordinaryRepairs = newOrdinaryRepairs, pendingRepair = newPendingRepair,
      phaseReviewedCandidate = if (clear) None else newPhaseReviewed,
      phaseRecordingPaths = if (clear) Vector.empty else newPhasePaths,
      candidateParent = if (clear) None else newcandidateparent,
      stepAdmission = if (clear) None else newadmission, releaseCommit = newreleasecommit,
      repairControl = newcontrol, policyRepairPending = newpolicypending, repairObservations = newobservations))
  }

  def startC(plan: Plan, initial: Snapshot, repairPolicy: Option[String] = None): Consequence[State] = for {
    _ <- validatePlanC(plan)
    _ <- if (plan.phaseRepairInventory.isEmpty) Consequence.unit
      else invalid("A new Plan cannot start with unadmitted Phase repair inventory")
    _ <- if (validSnapshot(initial)) Consequence.unit else invalid("native initial Git snapshot is missing")
    _ <- if (repairPolicy != null && repairPolicy.forall(_ == R.version)) Consequence.unit
      else invalid("runtime repair policy is unsupported")
  } yield new State(plan, Stage.Implementation, 0, 1, 0, initial, Vector.empty,
    None, None, None, None, None, None, None, None,
    repairControl = repairPolicy.map(value => R.Context(value, plan.repairLimit)))

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

  /** Literal append only; no directory expansion or overlapping additions. */
  private[admission] def currentInventoryPathsC(current: Plan, proposed: Plan, stepIdentity: String):
      Consequence[Vector[String]] = for {
    _ <- validatePlanC(current)
    _ <- GoalPhaseAcceptanceBoundary.savedPlanC(current)
    _ <- validatePlanC(proposed)
    _ <- GoalPhaseAcceptanceBoundary.savedPlanC(proposed)
    index = current.steps.indexWhere(_.identity == stepIdentity)
    _ <- if (index >= 0 && proposed.steps.size == current.steps.size && proposed.revision != current.revision)
      Consequence.unit else invalid("Current inventory requires the same Step set and a fresh Plan revision")
    prior = current.steps(index)
    next = proposed.steps(index)
    added = next.ownedPaths.drop(prior.ownedPaths.size)
    _ <- if (added.nonEmpty && next.ownedPaths.take(prior.ownedPaths.size) == prior.ownedPaths &&
        proposed.copy(revision = current.revision, steps = proposed.steps.updated(index, prior)) == current &&
        next.copy(ownedPaths = prior.ownedPaths) == prior &&
        added.forall(value => RepositorySyncNativeConflictInput.relative(value) &&
          !value.exists(c => c.isControl || "*?[]{}".contains(c)) &&
          !prior.ownedPaths.exists(owner => _inventory_overlap(owner, value))) &&
        added.indices.forall(i => !added.take(i).exists(_inventory_overlap(_, added(i))))) Consequence.unit
      else invalid("Current inventory may only append finite unique uncovered literal paths; every other Plan value stays exact")
  } yield added

  private def _inventory_overlap(left: String, right: String): Boolean =
    left == right || left.startsWith(right + "/") || right.startsWith(left + "/")

  /** Return the exact ordered adopted IDs whose required flag is deferred.
    * Both inputs are saved Plans; later project configuration is never selected.
    */
  private[admission] def phaseAcceptanceDeferralConditionsC(previous: Plan, proposed: Plan): Consequence[Vector[String]] = for {
    _ <- validatePlanC(previous)
    _ <- GoalPhaseAcceptanceBoundary.savedPlanC(previous)
    _ <- validatePlanC(proposed)
    _ <- GoalPhaseAcceptanceBoundary.savedPlanC(proposed)
    _ <- if (previous.acceptance.nonEmpty && proposed.acceptance.nonEmpty &&
        previous.effectiveAcceptance.nonEmpty && proposed.effectiveAcceptance.nonEmpty &&
        previous.revision != proposed.revision && proposed.copy(revision = previous.revision,
          acceptance = previous.acceptance, effectiveAcceptance = previous.effectiveAcceptance) == previous)
      Consequence.unit else invalid("Phase acceptance deferral requires saved adopted boundaries and an otherwise unchanged fresh Plan")
    prior = previous.acceptance.get
    next = proposed.acceptance.get
    _ <- if (next.usage == prior.usage && next.conditions.size == prior.conditions.size &&
        next.conditions.zip(prior.conditions).forall { (n, p) =>
          n == p || (p.scope == "phase" && p.required && !n.required && n.copy(required = true) == p)
        }) Consequence.unit else invalid("Phase acceptance deferral may only change adopted phase required true to false")
    changed = prior.conditions.zip(next.conditions).collect { case (p, n) if p != n => p.identity }
    effective = previous.effectiveAcceptance.get
    _ <- if (changed.nonEmpty && changed.forall(id => prior.conditions.find(_.identity == id).exists(raw =>
        effective.conditions.exists(c => c.identity == id && c.scope == raw.scope && c.required &&
          c.description == raw.description && c.source == "adopted"))) &&
        proposed.effectiveAcceptance.contains(effective.copy(conditions = effective.conditions.map(c =>
          if (changed.contains(c.identity)) c.copy(required = false) else c)))) Consequence.unit
      else invalid("Phase acceptance deferral must retain exact selected defaults and defer only matching required adopted conditions")
  } yield changed

  private[admission] def phaseRepairInventoryPathsC(current: Plan, proposed: Plan): Consequence[Vector[String]] = for {
    _ <- validatePlanC(current)
    _ <- GoalPhaseAcceptanceBoundary.savedPlanC(current)
    _ <- validatePlanC(proposed)
    _ <- GoalPhaseAcceptanceBoundary.savedPlanC(proposed)
    added = proposed.phaseRepairInventory.drop(current.phaseRepairInventory.size)
    _ <- if (proposed.revision != current.revision && added.nonEmpty &&
        proposed.phaseRepairInventory.take(current.phaseRepairInventory.size) == current.phaseRepairInventory &&
        proposed.copy(revision = current.revision, phaseRepairInventory = current.phaseRepairInventory) == current &&
        added.forall(value => !current.phaseOwnedPaths.exists(_inventory_overlap(_, value))) &&
        added.indices.forall(i => !added.take(i).exists(_inventory_overlap(_, added(i))))) Consequence.unit
      else invalid("Phase repair inventory may only append uncovered exact literal paths at a fresh revision; every other Plan value stays exact")
  } yield added

  private def _valid_phase_inventory(values: Vector[String]): Boolean = values != null && values.size <= 256 &&
    values.distinct == values && values.forall(value => path(value) && value.trim == value &&
      !value.exists(c => c.isControl || "*?[]{}".contains(c)))

  def validatePlanC(plan: Plan): Consequence[Unit] = {
    if (plan == null || !Vector(plan.identity, plan.revision, plan.phase, plan.repository).forall(text) ||
        plan.steps == null || plan.steps.isEmpty || plan.steps.size > 64 || !_valid_phase_inventory(plan.phaseRepairInventory) ||
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
