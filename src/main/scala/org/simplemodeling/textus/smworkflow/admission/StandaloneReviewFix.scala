package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.simplemodeling.textus.smworkflow.value as V
import StandaloneFixExecution as S

/** Original review input and exact disposition attribution within the existing
  * standalone request. These are business facts, not a second repair ledger.
  * @since   Oct. 27, 2026
  * @version Oct. 26, 2026
  */
private[smworkflow] object StandaloneReviewFix {
  final case class Finding(identity: String, summary: String, paths: Vector[String], sourceReferences: Vector[ContextReference])
  final case class Origin(originalReview: ContextReference, context: String, findings: Vector[Finding])
  final case class Disposition(identity: String, outcome: String, basis: String, rationale: String,
    changedPaths: Vector[String], checks: Vector[String])
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  given Encoder[Finding] = deriveEncoder
  given Decoder[Finding] = deriveDecoder
  given Encoder[Origin] = deriveEncoder
  given Decoder[Origin] = deriveDecoder
  given Encoder[Disposition] = deriveEncoder
  given Decoder[Disposition] = deriveDecoder

  def inputC(input: S.Input): Consequence[Unit] = input.reviewOrigin match {
    case None => Consequence.unit
    case Some(origin) =>
      val valid = try input.task.isEmpty && origin != null && reference(origin.originalReview) &&
        origin.context != null && origin.context.trim.nonEmpty && origin.context.length <= 65536 &&
        !origin.context.exists(c => c.isControl && c != '\n' && c != '\t' && c != '\r') &&
        origin.findings != null && origin.findings.nonEmpty && origin.findings.size <= 128 &&
        origin.findings.forall(f => f != null && text(f.identity) && text(f.summary) && paths(f.paths, input.ownedPaths, true) &&
          f.sourceReferences != null && f.sourceReferences.size <= 128 && f.sourceReferences.distinct == f.sourceReferences &&
          f.sourceReferences.forall(reference)) && origin.findings.map(_.identity).distinct.size == origin.findings.size
      catch { case scala.util.control.NonFatal(_) => false }
      if (valid) Consequence.unit else invalid("original review, finding identities, context or ownership is invalid")
  }

  def resultC(work: S.Work, result: S.Result): Consequence[Unit] = work.state.input.reviewOrigin match {
    case None => if (result.reviewDispositions.isEmpty) Consequence.unit else invalid("dispositions require actual review-origin input")
    case Some(origin) =>
      val valid = try result.reviewDispositions.exists { entries => entries != null &&
        entries.map(_.identity) == origin.findings.map(_.identity) && entries.forall(d => d != null &&
          Set("fixed", "not-applicable", "unresolved")(d.outcome) && Set("mechanical", "semantic")(d.basis) && text(d.rationale) &&
          paths(d.changedPaths, work.state.input.ownedPaths) && strings(d.checks) && d.checks.forall(work.state.input.checks.contains) &&
          (d.outcome != "fixed" || (d.changedPaths.nonEmpty && d.checks.nonEmpty &&
            (work.state.input.kind != "code" || work.state.selected.exists(c => d.checks.contains(c.identity) &&
              Set(WorkflowSbtValidation.Kind.Test, WorkflowSbtValidation.Kind.TestOnly)(c.kind))))) &&
          (d.outcome != "not-applicable" || d.changedPaths.isEmpty) &&
          (result.outcome != "accept" || d.outcome != "unresolved")) }
      catch { case scala.util.control.NonFatal(_) => false }
      if (valid && work.kind == "Review" && result.outcome == "accept")
        verifiedC(work.state.copy(reviewDispositions = result.reviewDispositions))
      else if (valid) Consequence.unit else invalid("result must cover every original finding exactly with scoped changes, selected checks and a reasoned disposition")
  }

  def needsReview(state: S.State): Boolean = state.reviewDispositions.exists(_.exists(d =>
    d.outcome == "unresolved" || d.basis == "semantic"))

  /** Candidate and receipts come from the native owner, never the semantic result. */
  def attributionValid(state: S.State, candidate: S.Candidate): Boolean = state.input.reviewOrigin.isEmpty ||
    state.reviewDispositions.exists(entries => entries.flatMap(_.changedPaths).toSet == candidate.paths.toSet)

  def verifiedC(state: S.State): Consequence[Unit] =
    if (state.input.reviewOrigin.isEmpty || (state.candidate.exists(attributionValid(state, _)) &&
      state.reviewDispositions.exists(_.forall(d => d.checks.forall(id => state.selected.find(_.identity == id).exists(check =>
        state.receipts.find(_.identity == id).exists(r => RepositorySyncNativeMergeValidation.productSuccessful(r, check.kind))))))))
      Consequence.unit else invalid("finding attribution has no matching observed candidate and successful selected check receipts")

  def references(origin: Origin): Vector[ContextReference] =
    (Vector(origin.originalReview) ++ origin.findings.flatMap(_.sourceReferences)).distinct

  def fromPublicC(value: V.StandaloneReviewFixInput): Consequence[S.Input] = {
    val origin = Origin(ContextReference(value.originalReview.identity, value.originalReview.revision), value.reviewContext,
      value.originalFindings.toVector.map(f => Finding(f.identity, f.summary, f.paths.toVector,
        f.sourceReferences.toVector.map(r => ContextReference(r.identity, r.revision)))))
    val input = S.Input(value.repositoryIdentity, value.requestIdentity, value.objective, value.ownedPaths.toVector,
      value.checks.toVector, value.conditions.toVector, value.sourceReferences.toVector.map(r => ContextReference(r.identity, r.revision)),
      value.reviewRequired, value.kind, value.scalaLint, value.repairLimit, reviewOrigin = Some(origin))
    S.inputC(input).map(_ => input)
  }
  def dispositions(values: Vector[V.StandaloneReviewFixDisposition]): Option[Vector[Disposition]] =
    Option.when(values.nonEmpty)(values.map(d => Disposition(d.identity, d.outcome, d.basis, d.rationale, d.changedPaths, d.checks)))

  private def paths(values: Vector[String], owners: Vector[String], nonempty: Boolean = false): Boolean =
    strings(values, nonempty) && values.forall(p => GoalPhasePurposeRepair.path(p) && S.owned(p, owners))
  private def strings(values: Vector[String], nonempty: Boolean = false): Boolean = values != null &&
    (!nonempty || values.nonEmpty) && values.size <= 256 && values.distinct == values && values.forall(text)
  private def reference(value: ContextReference): Boolean = value != null && text(value.identity) && text(value.revision)
  private def text(value: String): Boolean = value != null && value.trim.nonEmpty && value.length <= 16384 && !value.exists(_.isControl)
  private def invalid[A](message: String): Consequence[A] = Consequence.stateConflict("Standalone review fix: " + message)
}
