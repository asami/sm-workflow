package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import GoalPhaseNativeValidation.Check
import RepositorySyncNativeMergeValidation.Receipt
import GoalPhaseRuntimeRepairControl as R

/** One bounded business request over the existing execution and repair policy.
  * It is neither a Phase Plan nor a second mechanical command runner.
  * @since   Oct. 26, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object StandaloneFixExecution {
  final case class Input(repositoryIdentity: String, requestIdentity: String, objective: String,
    ownedPaths: Vector[String], checks: Vector[String], conditions: Vector[String],
    sourceReferences: Vector[ContextReference], reviewRequired: Boolean,
    kind: String = "code", scalaLint: String = "required", repairLimit: Int = R.ceiling, task: Option[Task] = None, reviewOrigin: Option[StandaloneReviewFix.Origin] = None, delegation: Option[DelegateTask.Contract] = None)
  final case class Task(sourceKind: String, dependencies: Vector[String])
  final case class CommitExecution(outcome: String, selectedPaths: Vector[String], termination: String,
    exitCode: Option[Int], stdout: String, stderr: String)
  final case class Commit(identity: String, parent: String, tree: String)
  final case class Candidate(head: String, tree: String, paths: Vector[String], diff: String)
  final case class Condition(identity: String, satisfied: Boolean, detail: String)
  final case class Result(outcome: String, summary: String, findings: Vector[String],
    conditions: Vector[Condition], unresolvedQuestions: Vector[String],
    diagnosticRepair: Option[IncrementalSbtDiagnosisRepair.Manifest] = None,
    fixAssessment: Option[GoalPhaseFixAssessment.Report] = None,
    reviewDispositions: Option[Vector[StandaloneReviewFix.Disposition]] = None)
  final case class State(input: Input, participant: String, stage: String, generation: Long,
    selected: Vector[Check], candidate: Option[Candidate], receipts: Vector[Receipt],
    automaticCount: Int, ordinaryCount: Int, repairControl: R.Context,
    pendingCheck: Option[String] = None, findings: Vector[String] = Vector.empty,
    unresolvedQuestions: Vector[String] = Vector.empty,
    conditionReport: Vector[Condition] = Vector.empty,
    evidenceReferences: Vector[ContextReference] = Vector.empty,
    semanticReference: Option[ContextReference] = None,
    reviewReference: Option[ContextReference] = None,
    priorReview: Option[Result] = None,
    priorReviewCandidate: Option[Candidate] = None,
    message: String = "Selected validation has not executed", commit: Option[Commit] = None, commitExecution: Option[CommitExecution] = None, projectionWarning: Option[String] = None,
    reviewDispositions: Option[Vector[StandaloneReviewFix.Disposition]] = None,
    delivery: Option[DelegateTask.Delivery] = None)
  final case class Work(session: String, sourceRevision: String, kind: String, state: State)

  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  given Encoder[Task] = deriveEncoder
  given Decoder[Task] = deriveDecoder
  given Encoder[CommitExecution] = deriveEncoder
  given Decoder[CommitExecution] = deriveDecoder
  given Encoder[Commit] = deriveEncoder
  given Decoder[Commit] = deriveDecoder
  given Encoder[Input] = deriveEncoder[Input].mapJson(_.mapObject(_.filter {
    case ("task" | "reviewOrigin" | "delegation", value) => !value.isNull
    case _ => true
  }))
  given Decoder[Input] = deriveDecoder
  given Encoder[Candidate] = deriveEncoder
  given Decoder[Candidate] = deriveDecoder
  given Encoder[Condition] = deriveEncoder
  given Decoder[Condition] = deriveDecoder
  given Encoder[Result] = deriveEncoder[Result].mapJson(_.mapObject(_.filter {
    case ("diagnosticRepair" | "fixAssessment" | "reviewDispositions", value) => !value.isNull
    case _ => true
  }))
  given Decoder[Result] = deriveDecoder
  private given Encoder[R.Context] = deriveEncoder
  private given Decoder[R.Context] = deriveDecoder
  given Encoder[State] = deriveEncoder[State].mapJson(_.mapObject(o => o.filter { case ("commit" | "commitExecution" | "projectionWarning" | "reviewDispositions" | "delivery", value) => !value.isNull; case _ => true }))
  given Decoder[State] = deriveDecoder
  given Encoder[Work] = deriveEncoder
  given Decoder[Work] = deriveDecoder

  def inputC(input: Input): Consequence[Unit] = {
    val valid = try input != null && Vector(input.repositoryIdentity, input.requestIdentity, input.objective).forall(text) &&
      paths(input.ownedPaths) && strings(input.checks, true) && input.checks.size <= 16 &&
      strings(input.conditions, true) && input.conditions.size <= 64 && input.sourceReferences != null &&
      input.sourceReferences.nonEmpty && input.sourceReferences.size <= 128 && input.sourceReferences.forall(r => r != null && text(r.identity) && text(r.revision)) &&
      Set("code", "documentation")(input.kind) && Set("off", "required")(input.scalaLint) &&
      input.repairLimit > 0 && input.repairLimit <= R.ceiling && input.task != null &&
      input.delegation != null && input.delegation.forall(c => DelegateTask.valid(c) && input.task.nonEmpty && input.reviewOrigin.isEmpty) &&
      input.reviewOrigin != null && input.task.forall(t => t != null && Set("journal", "direct-instruction")(t.sourceKind) && strings(t.dependencies))
      catch { case scala.util.control.NonFatal(_) => false }
    if (valid) StandaloneReviewFix.inputC(input) else invalid("adopted objective, ownership, checks, conditions or repair policy is invalid")
  }

  def startC(input: Input, participant: String, selected: Vector[Check]): Consequence[State] = for {
    _ <- inputC(input)
    _ <- input.delegation.fold(Consequence.unit)(_ => DelegateTask.checksC(input, selected))
    _ <- if (text(participant) && selected != null && selected.forall(_ != null) && selected.map(_.identity) == input.checks &&
      selected.forall(c => c != null && c.ordinal > 0 && c.ordinal <= 16 && c.dependency.isEmpty)) Consequence.unit
      else invalid("checks must resolve exactly to the admitted host bindings")
  } yield State(input, participant, if (input.task.nonEmpty) "Implement" else "Inspect", 1, selected, None, Vector.empty, 0, 0,
    R.Context(R.version, input.repairLimit))

  def candidateC(state: State, candidate: Candidate): Consequence[State] =
    if (state == null || state.stage != "Inspect" || !validCandidate(candidate) ||
        !candidate.paths.forall(permitted(_, state.input))) invalid("candidate crosses its actual selected source scope")
    else if (state.input.reviewOrigin.nonEmpty && state.semanticReference.isEmpty) {
      val origin = state.input.reviewOrigin.get
      val findings = origin.findings.map(f => f.identity + ": " + f.summary)
      val control = state.repairControl.observe(state.input.requestIdentity,
        origin.originalReview.identity + "@" + origin.originalReview.revision, findings)
      Consequence.success(state.copy(stage = if (control.stop.nonEmpty) "Human" else "Repair", candidate = Some(candidate),
        findings = findings, repairControl = control, message = control.stop.getOrElse(
          "Repair only the identified original review findings; retain original sources and attribute changes/checks")))
    }
    else if (!StandaloneReviewFix.attributionValid(state, candidate)) Consequence.success(state.copy(stage = "Human", candidate = Some(candidate),
      message = "Observed candidate differs from the exact original-finding change attribution; no successful disposition is recorded"))
    else if (state.candidate.contains(candidate) && state.receipts.size == state.selected.size &&
        WorkflowRepairAssessment.checksC(state.input.kind, state.input.scalaLint, state.selected, state.receipts).isSuccess)
      assessC(state.copy(stage = "Assess", pendingCheck = None,
        message = "Unchanged candidate reuses its applicable complete successful check batch"))
    else Consequence.success(state.copy(stage = "Validate", candidate = Some(candidate), receipts = Vector.empty,
      pendingCheck = None, message = "Runtime observed the selected uncommitted candidate"))

  /** An intent is retained before its typed SBT effect. Redisplay of an intent
    * is uncertainty/collection work, never permission to run it again. */
  def intentC(state: State): Consequence[State] =
    if (state == null || state.stage != "Validate" || state.pendingCheck.nonEmpty ||
        state.receipts.size >= state.selected.size || state.candidate.isEmpty)
      invalid("validation has no new unexecuted selected check")
    else Consequence.success(state.copy(stage = "ValidationIntent",
      pendingCheck = Some(state.selected(state.receipts.size).identity),
      message = "Selected SBT intent retained; missing result must not be replayed"))

  def observedC(state: State, receipt: Receipt): Consequence[State] = for {
    _ <- if (state != null && state.stage == "ValidationIntent" && receipt != null && receipt.sbt != null && state.pendingCheck.contains(receipt.identity) &&
      state.selected.lift(state.receipts.size).exists(_.identity == receipt.identity) && receipt.sbt.exists(f => f != null && f.valid) &&
      RepositorySyncNativeMergeValidation.storedProductOutputValid(receipt))
      Consequence.unit else invalid("SBT observation differs from its retained selected intent")
    check = state.selected(state.receipts.size)
    all = state.receipts :+ receipt
    passed = RepositorySyncNativeMergeValidation.productSuccessful(receipt, check.kind)
    terminalFailure = receipt.termination == "exited" && receipt.exitCode.exists(_ != 0) &&
      receipt.sbt.exists(_.outcome == WorkflowSbtValidation.Outcome.Failed)
    next <- if (!passed && !terminalFailure) Consequence.success(state.copy(stage = "Wait", receipts = all,
      pendingCheck = None, message = "Incomplete or unverified validation retained; observe before any new attempt"))
    else if (!passed) {
      val diagnostic = diagnosticFor(state, receipt, check,
        state.generation.toString, state.candidate.get)
      val finding = diagnostic.groups.map(g => "check:" + check.identity + ":" + g.kind + ":" + g.message).distinct match {
        case Vector() => Vector("check:" + check.identity + ":unclassified-failure")
        case observed => observed
      }
      val control = state.repairControl.observe(state.input.requestIdentity,
        state.input.requestIdentity + ":generation:" + state.generation + ":" + check.identity, finding)
      val reason = control.stop.orElse(Option.when(state.automaticCount >= control.effectiveLimit)(
        "The existing automatic repair limit is exhausted"))
      Consequence.success(state.copy(stage = if (reason.nonEmpty) "Human" else "Repair", receipts = all,
        pendingCheck = None, findings = finding, repairControl = control,
        message = reason.getOrElse(if (diagnostic.testExecution.compilationBlocked.nonEmpty)
          "Compilation failed; affected remaining tests were not executed. Repair uses this retained failure."
          else "An actual failed selected check requires diagnosis and bounded repair")))
    } else if (all.size < state.selected.size) Consequence.success(state.copy(stage = "Validate", receipts = all,
      pendingCheck = None, message = "Actual selected check succeeded"))
    else assessC(state.copy(stage = "Assess", receipts = all, pendingCheck = None))
  } yield next

  def assessC(state: State): Consequence[State] = for {
    _ <- if (state != null && state.stage == "Assess" && state.candidate.nonEmpty) Consequence.unit
      else invalid("assessment has no current candidate")
    _ <- WorkflowRepairAssessment.checksC(state.input.kind, state.input.scalaLint, state.selected, state.receipts)
    _ <- StandaloneReviewFix.verifiedC(state)
    required = StandaloneReviewFix.needsReview(state) || (state.input.task.nonEmpty && state.input.delegation.isEmpty) || state.input.reviewRequired || state.unresolvedQuestions.nonEmpty || state.priorReview.exists(_.outcome == "repair")
    // An initially passing validation is a truthful no-change outcome. A repair
    // completion must evaluate every adopted condition before it can be accepted.
    _ <- if (state.semanticReference.isEmpty || (state.conditionReport.map(_.identity) == state.input.conditions &&
      state.conditionReport.forall(_.satisfied))) Consequence.unit
      else invalid("successful checks cannot replace the adopted semantic conditions")
  } yield state.copy(stage = if (required) "Review" else if (state.input.delegation.nonEmpty) {
      if (state.candidate.exists(_.paths.isEmpty)) "NoChange" else "CommitReady"
    } else if (state.semanticReference.isEmpty) "NoChange" else "Accepted",
    findings = if (state.input.reviewOrigin.nonEmpty && !required) Vector.empty else state.findings,
    message = if (required) "Independent scoped review is required by selected policy or unresolved meaning"
      else if (state.semanticReference.isEmpty) "Selected checks already passed; no repair was requested or implicitly committed"
      else "Selected checks and adopted conditions satisfied; independent review not required; changes remain uncommitted")

  def admitResultC(work: Work, result: Result): Consequence[Unit] = for {
    _ <- if (work != null && work.state != null && work.kind == work.state.stage && result != null && text(result.summary) &&
      strings(result.findings) && strings(result.unresolvedQuestions) && result.conditions != null &&
      result.conditions.forall(c => c != null && text(c.identity) && text(c.detail)) &&
      result.conditions.map(_.identity) == work.state.input.conditions &&
      Set("completed", "accept", "repair", "decision-needed")(result.outcome) &&
      (if (Set("Repair", "Implement")(work.kind)) Set("completed", "decision-needed")(result.outcome)
       else if (work.kind == "Review") Set("accept", "repair", "decision-needed")(result.outcome) else false) &&
      (!Set("completed", "accept")(result.outcome) || (result.findings.isEmpty && result.conditions.forall(_.satisfied))) &&
      (result.outcome != "repair" || result.findings.nonEmpty) &&
      (result.outcome != "accept" || result.unresolvedQuestions.isEmpty)) Consequence.unit
      else invalid("semantic result does not cover the actual work and adopted conditions")
    _ <- if (work.kind != "Implement" || (work.state.input.task.nonEmpty && work.state.stage == "Implement" &&
      result.fixAssessment.isEmpty && result.diagnosticRepair.isEmpty)) Consequence.unit
      else invalid("initial Task implementation is not a failure repair")
    _ <- StandaloneReviewFix.resultC(work, result)
    _ <- GoalPhaseFixAssessment.validateOptionC(result.fixAssessment)
    _ <- if (result.fixAssessment.exists(_.convergence.status == "BLOCKED") && result.outcome != "decision-needed")
      invalid("BLOCKED assessment requires an explicit decision") else Consequence.unit
    _ <- IncrementalSbtDiagnosisRepair.validateOptionC(result.diagnosticRepair)
    _ <- if (work.kind == "Review" && (result.fixAssessment.nonEmpty || result.diagnosticRepair.nonEmpty))
      invalid("independent review cannot supply implementation or a diagnostic repair manifest") else Consequence.unit
    _ <- result.diagnosticRepair.fold(Consequence.unit) { m =>
      val s = work.state
      val actual = s.candidate.map(snapshot)
      if (m.candidate == actual.orNull && m.sourceRevision == work.sourceRevision &&
        m.ownedPaths.forall(owned(_, s.input.ownedPaths)) && m.checks.forall(s.input.checks.contains) &&
        s.selected.filter(_.kind == WorkflowSbtValidation.Kind.Compile).forall(c => m.checks.contains(c.identity)) &&
        s.selected.exists(c => m.checks.contains(c.identity) && Set(WorkflowSbtValidation.Kind.Test, WorkflowSbtValidation.Kind.TestOnly)(c.kind)) &&
        m.evidence.forall(e => s.receipts.find(_.identity == e.check).exists { r =>
          val failed = s.selected.find(_.identity == r.identity).exists(c =>
            !RepositorySyncNativeMergeValidation.productSuccessful(r, c.kind))
          val raw = if (e.stream == "stdout") r.stdout else r.stderr
          failed && raw.nonEmpty && e.lastLine <= raw.split("\\r?\\n", -1).length
        })) Consequence.unit else invalid("diagnostic manifest crosses retained failure evidence, checks or ownership")
    }
  } yield ()

  /** Called only by the native completion owner after actual independent
    * provenance admission for Review. There is no caller repair loop/counter. */
  def completedC(work: Work, result: Result, reference: ContextReference): Consequence[State] = for {
    _ <- admitResultC(work, result)
    _ <- if (reference != null && text(reference.identity) && text(reference.revision)) Consequence.unit
      else invalid("semantic completion lacks its actual result reference")
    s = work.state.copy(reviewDispositions = result.reviewDispositions.orElse(work.state.reviewDispositions))
    next <- if (result.outcome == "decision-needed") Consequence.success(s.copy(stage = "Human",
      findings = result.findings, message = result.summary,
      semanticReference = if (Set("Repair", "Implement")(work.kind)) Some(reference) else s.semanticReference,
      reviewReference = if (work.kind == "Review") Some(reference) else s.reviewReference))
    else if (work.kind == "Review" && result.outcome == "accept") Consequence.success(s.copy(stage = if (s.input.task.isEmpty) "Accepted" else if (s.candidate.exists(_.paths.isEmpty)) "NoChange" else "CommitReady",
      reviewReference = Some(reference), priorReview = Some(result), priorReviewCandidate = s.candidate, conditionReport = result.conditions, findings = Vector.empty, unresolvedQuestions = Vector.empty,
      message = if (s.input.task.isEmpty) "Independent scoped review accepted; standalone changes remain uncommitted"
        else "Integrated Task review accepted; actual selected commit is still required unless there is no change"))
    else if (work.kind == "Review") {
      val control = s.repairControl.observe(s.input.requestIdentity, reference.identity + "@" + reference.revision, result.findings)
      Consequence.success(s.copy(stage = if (control.stop.nonEmpty || s.automaticCount >= control.effectiveLimit) "Human" else "Repair",
        findings = result.findings, priorReview = Some(result), priorReviewCandidate = s.candidate, reviewReference = Some(reference), repairControl = control,
        unresolvedQuestions = result.unresolvedQuestions, message = control.stop.getOrElse(result.summary)))
    } else if (work.kind == "Implement") {
      Consequence.success(s.copy(stage = "Inspect", semanticReference = Some(reference),
        conditionReport = result.conditions, unresolvedQuestions = result.unresolvedQuestions, message = result.summary))
    } else {
      val exempt = result.fixAssessment.exists(GoalPhaseFixAssessment.ordinaryCountExempt)
      if (s.generation == Long.MaxValue) invalid("repair generation is exhausted")
      else R.admitBatchC(s.automaticCount, s.ordinaryCount, s.repairControl.effectiveLimit, exempt).map(counts =>
        s.copy(stage = "Inspect", generation = s.generation + 1,
          automaticCount = counts.automatic, ordinaryCount = counts.ordinary,
          semanticReference = Some(reference), conditionReport = result.conditions,
          unresolvedQuestions = result.unresolvedQuestions, message = result.summary))
    }
  } yield next

  def committedC(state: State, actual: Commit): Consequence[State] =
    if (state == null || state.input.task.isEmpty || !Set("CommitIntent", "CommitUnknown")(state.stage) ||
      (state.reviewReference.isEmpty && (state.input.delegation.isEmpty || state.input.reviewRequired || state.priorReview.nonEmpty || state.unresolvedQuestions.nonEmpty)) || actual == null || !Vector(actual.identity, actual.parent, actual.tree).forall(v =>
        v != null && v.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) ||
      !state.candidate.exists(c => c.paths.nonEmpty && c.head == actual.parent && c.tree == actual.tree && actual.identity != c.head))
      invalid("Task completion requires the actual reviewed single-parent selected commit")
    else Consequence.success(state.copy(stage = "Completed", commit = Some(actual),
      message = "Integrated Task review, selected validation and actual local commit are complete"))

  def diagnostics(state: State, revision: String): Vector[IncrementalSbtFailureDiagnosis.View] =
    state.candidate.toVector.flatMap(c => state.receipts.flatMap(r => state.selected.find(_.identity == r.identity)
      .filter(check => !RepositorySyncNativeMergeValidation.productSuccessful(r, check.kind))
      .map(check => diagnosticFor(state, r, check, revision, c))))
  private def diagnosticFor(state: State, receipt: Receipt, check: GoalPhaseNativeValidation.Check,
    revision: String, candidate: Candidate): IncrementalSbtFailureDiagnosis.View = {
    val remainingTests = state.selected.dropWhile(_.identity != check.identity).drop(1).exists(c =>
      c.kind == WorkflowSbtValidation.Kind.Test || c.kind == WorkflowSbtValidation.Kind.TestOnly)
    IncrementalSbtFailureDiagnosis.extract(receipt, check.kind, revision, snapshot(candidate), remainingTests)
  }
  def snapshot(candidate: Candidate): GoalPhaseOrderedProgression.Snapshot = GoalPhaseOrderedProgression.Snapshot(candidate.head, candidate.tree)
  def validCandidate(c: Candidate): Boolean = c != null && Vector(c.head, c.tree).forall(v => v != null &&
    v.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) && c.paths != null && c.paths.distinct == c.paths &&
    c.paths.forall(GoalPhasePurposeRepair.path) && c.diff != null
  def permitted(path: String, input: Input): Boolean = owned(path, input.ownedPaths) &&
    input.delegation.forall(c => !c.exclusions.filter(GoalPhasePurposeRepair.path).exists(excluded => owned(path, Vector(excluded))))
  def owned(path: String, owners: Vector[String]): Boolean = owners.exists(p => path == p || path.startsWith(p + "/"))
  private def paths(values: Vector[String]): Boolean = strings(values, true) && values.forall(GoalPhasePurposeRepair.path)
  private def strings(values: Vector[String], nonempty: Boolean = false): Boolean = values != null &&
    (!nonempty || values.nonEmpty) && values.size <= 256 && values.distinct == values && values.forall(text)
  private def text(value: String): Boolean = value != null && value.trim.nonEmpty && value.length <= 16384 && !value.exists(_.isControl)
  private def invalid[A](message: String): Consequence[A] = Consequence.stateConflict("Standalone repair: " + message)
}
