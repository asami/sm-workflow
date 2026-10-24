package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeWork as W

/** Bounded semantic uncertainty, separate from independent detailed review.
  * Transport failure has no business disposition and leaves the issued work open.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseStepTriage {
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  final case class Context(changedPaths: Vector[String], diff: String,
    checks: Vector[RepositorySyncNativeMergeValidation.Receipt], assessment: GoalPhaseStepAssessment.Result)
  object Context {
    given Encoder[Context] = deriveEncoder
    given Decoder[Context] = deriveDecoder
  }
  final case class Judgment(reference: ContextReference, outcome: String, summary: String, observations: Vector[String])
  object Judgment {
    given Encoder[Judgment] = deriveEncoder
    given Decoder[Judgment] = deriveDecoder
  }
  def valid(value: Judgment): Boolean = try { value != null && value.reference != null &&
    Vector(value.reference.identity, value.reference.revision, value.summary).forall(text) &&
    Set("clean", "suspicious", "decline")(value.outcome) && value.observations != null &&
    value.observations.size <= 128 && value.observations.forall(text) &&
    (value.outcome != "clean" || value.observations.isEmpty) &&
    (value.outcome != "suspicious" || value.observations.nonEmpty)
  } catch { case scala.util.control.NonFatal(_) => false }

  def admitC(request: W.Request, result: W.Result): Consequence[Unit] = {
    val validcontext = try { request != null && request.kind == "TriageStep" && request.triage != null &&
      request.triage.exists(c => c != null && c.changedPaths != null && c.changedPaths.nonEmpty &&
        c.changedPaths.forall(text) && c.diff != null && c.diff.trim.nonEmpty &&
        c.diff.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 2097152 && c.checks != null && c.checks.nonEmpty &&
        GoalPhaseStepAssessment.validResult(c.assessment) && c.assessment.branch == "triage" &&
        request.candidate.contains(c.assessment.candidate) && request.checksReceipt.contains(c.assessment.validationReference) &&
        c.checks.map(_.identity) == c.assessment.checks) && request.review.isEmpty && request.checkFailure.isEmpty
    } catch { case scala.util.control.NonFatal(_) => false }
    val validresult = try { result != null && valid(Judgment(ContextReference("uncommitted", "1"),
        result.outcome, result.summary, result.findings)) && result.acceptance.isEmpty && result.fixAssessment.isEmpty &&
        result.issueReport.isEmpty && result.planRevision.isEmpty && result.reviewObservations.isEmpty &&
        result.dependencyProposal.isEmpty && result.completionConditions.isEmpty && result.repairFindingSources.isEmpty
    } catch { case scala.util.control.NonFatal(_) => false }
    if (validcontext && validresult) Consequence.unit else GoalPhaseNativeRun.invalid("triage requires its exact passed-check context and only a bounded semantic disposition")
  }

  def completedC(request: W.Request, result: W.Result, reference: ContextReference,
    controller: O.State): Consequence[O.State] = for {
    _ <- admitC(request, result)
    work <- controller.next match {
      case value: O.Semantic if value.key.kind == O.WorkKind.TriageStep &&
          request.stepIdentity == value.key.step && request.generation == value.key.generation &&
          request.selectedPlan == value.selectedPlan && request.originalBase == value.originalBase &&
          request.completed == value.completed && request.candidate == value.candidate &&
          request.checksReceipt == value.checksReceipt => Consequence.success(value)
      case _ => GoalPhaseNativeRun.invalid("triage completion crosses its actual assigned frontier")
    }
    judgment = Judgment(reference, result.outcome, result.summary, result.findings)
    next <- controller.acceptC(O.TriageAdmitted(work.key, request.triage.get.assessment, judgment))
  } yield next

  /** Explicit path/Operation/scenario joins only; partial inventories prove no absence. */
  final case class ScenarioMapping(operation: String, paths: Vector[String], scenarios: Vector[String])
  final case class Inventory(source: ContextReference, complete: Boolean, mappings: Vector[ScenarioMapping])
  final case class Signals(source: ContextReference, status: String, changedOperations: Vector[String], missing: Vector[String])
  object Inventory {
    given Encoder[ScenarioMapping] = deriveEncoder
    given Decoder[ScenarioMapping] = deriveDecoder
    given Encoder[Inventory] = deriveEncoder
    given Decoder[Inventory] = deriveDecoder
  }
  object Signals {
    given Encoder[Signals] = deriveEncoder
    given Decoder[Signals] = deriveDecoder
  }
  def validInventory(value: Inventory): Boolean = try {
    value != null && value.source != null && Vector(value.source.identity, value.source.revision).forall(text) &&
      value.mappings != null && value.mappings.size <= 128 && value.mappings.forall(m => m != null &&
        text(m.operation) && m.paths != null && m.paths.nonEmpty && m.paths.forall(path =>
          text(path) && !path.startsWith("/") && !path.split("/", -1).exists(p => p.isEmpty || p == "." || p == "..")) &&
        m.paths.distinct == m.paths && m.scenarios != null && m.scenarios.forall(text) && m.scenarios.distinct == m.scenarios) &&
      value.mappings.map(_.operation).distinct.size == value.mappings.size
  } catch { case scala.util.control.NonFatal(_) => false }
  def signals(changedPaths: Vector[String], inventory: Inventory): Signals = {
    require(validInventory(inventory) && changedPaths != null && changedPaths.forall(text))
    val changed = inventory.mappings.filter(_.paths.exists(changedPaths.contains)).map(_.operation)
    Signals(inventory.source, if (inventory.complete) "observed" else "partial", changed,
      if (inventory.complete) inventory.mappings.filter(m => changed.contains(m.operation) && m.scenarios.isEmpty).map(_.operation)
      else Vector.empty)
  }
  def validSignals(value: Signals): Boolean = try {
    value != null && value.source != null && Vector(value.source.identity, value.source.revision).forall(text) &&
      Set("observed", "partial")(value.status) && value.changedOperations != null && value.missing != null &&
      value.changedOperations.forall(text) && value.changedOperations.distinct == value.changedOperations &&
      value.missing.forall(value.changedOperations.contains) && value.missing.distinct == value.missing &&
      (value.status != "partial" || value.missing.isEmpty)
  } catch { case scala.util.control.NonFatal(_) => false }

  private def text(value: String): Boolean = value != null && value.trim.nonEmpty &&
    !value.contains('\u0000') && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 16384
}
