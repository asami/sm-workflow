package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation}
import RepositorySyncNativeProgressStore as N
import RepositorySyncNativeMergeValidation as V

/** Selected merge policy over actual managed validation and pending paths.
  * An admission is neither a semantic review verdict nor Git effect authority.
  * @since   Oct. 24, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncMergeAssessment {
  final case class Policy(detailedReview: String, scalaLint: String, carLint: String,
    lintReason: String, contractPaths: Vector[String], repairLimit: Int = 3)
  final case class Result(policy: Policy, sourceRevision: String, branch: String,
    reason: String, overlappingPaths: Vector[String], contractPaths: Vector[String],
    conflictResolution: Boolean, triageReference: Option[ContextReference] = None)
  given Encoder[ContextReference] = deriveEncoder
  given Decoder[ContextReference] = deriveDecoder
  given Encoder[Policy] = deriveEncoder
  private val policyDecoder: Decoder[Policy] = deriveDecoder
  given Decoder[Policy] = Decoder.instance(c => policyDecoder.decodeJson(
    if (c.downField("repairLimit").focus.isEmpty)
      c.value.mapObject(_.add("repairLimit", io.circe.Json.fromInt(3))) else c.value))
  given Encoder[Result] = deriveEncoder
  given Decoder[Result] = deriveDecoder

  def validateC(policy: Policy): Consequence[Unit] =
    if (policy != null && Set("required", "conditional")(policy.detailedReview) &&
        Set("required", "not-applicable")(policy.scalaLint) &&
        Set("off", "not-applicable")(policy.carLint) && policy.lintReason != null &&
        policy.lintReason.trim.nonEmpty && policy.lintReason.length <= 4096 &&
        policy.contractPaths != null && policy.contractPaths.size <= 128 &&
        policy.contractPaths.distinct == policy.contractPaths &&
        policy.contractPaths.forall(RepositorySyncNativeConflictInput.relative) &&
        policy.repairLimit >= 0 && policy.repairLimit <= 64) Consequence.unit
    else N.invalid("merge assessment policy is incomplete or contradictory")

  /** The pending-index paths relative to both parents identify actual overlap.
    * No baseline build or semantic-clean conclusion is inferred from them.
    */
  def captureC(store: N.Store, source: N.Stage, policy: Policy): Consequence[Result] = for {
    _ <- validateC(policy)
    state <- source.payload.validation.map(Consequence.success)
      .getOrElse(N.invalid("merge assessment requires actual validation"))
    _ <- if (source.payload.stage == "MergeValidated" && source.payload.mergeHead.nonEmpty &&
        source.payload.indexTree.nonEmpty && state.receipts.size == state.selected.size + 2 &&
        V.successful(state.receipts, merge = true, selected = state.selected)) Consequence.unit
      else N.invalid("merge assessment requires complete successful merge checks")
    _ <- if (policy.scalaLint != "required" || state.selected.exists(_.kind == WorkflowSbtValidation.Kind.FixCheck))
      Consequence.unit else N.invalid("required merge lint has no selected actual fix-check result")
    _ <- V.preconditionsC(store, source)
    local <- GitObjectId.parseC(source.payload.before.head)
    remote <- GitObjectId.parseC(source.payload.mergeHead.get)
    localPaths <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.PendingPaths(local)).flatMap(_.valueC)
    remotePaths <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.PendingPaths(remote)).flatMap(_.valueC)
    _ <- V.preconditionsC(store, source)
    overlap = (localPaths.map(_.value).toSet intersect remotePaths.map(_.value).toSet).toVector.sorted
    changed = (localPaths ++ remotePaths).map(_.value).distinct.sorted
    _ <- if (!changed.exists(_.endsWith(".scala")) || policy.scalaLint == "required") Consequence.unit
      else N.invalid("actual Scala merge paths cannot claim lint non-applicability")
    contracts = changed.filter(path => policy.contractPaths.exists(prefix => path == prefix || path.startsWith(prefix + "/")))
    resolved = source.record.history.exists(_.currentProgression.exists(p =>
      Set("sync.MergeConflicted", "sync.MaterializingConflict", "sync.MergeResolutionAdmitted")(p.value)))
    reviewRepair = source.payload.repairContext.nonEmpty
    branch = if (policy.detailedReview == "required" || resolved || reviewRepair) "detailed-review"
      else if (overlap.nonEmpty || contracts.nonEmpty) "semantic-uncertainty" else "admit"
    reason = if (policy.detailedReview == "required") "Selected policy requires independent detailed review"
      else if (resolved) "Conflict resolution retains independent detailed review"
      else if (reviewRepair) "Original review findings and the affected repair delta require independent detailed re-review"
      else if (branch == "semantic-uncertainty") "Actual parent overlap or selected contract paths require scoped semantic judgment"
      else "Actual selected checks passed without unresolved merge interaction; no semantic Action selected"
  } yield Result(policy, source.record.revision.value.toString, branch, reason, overlap, contracts, resolved)

  def admitC(store: N.Store, source: N.Stage, validation: V.Policy, namespace: String): Consequence[N.Stage] = for {
    policy <- validation.assessment.map(Consequence.success)
      .getOrElse(N.invalid("merge admission has no explicitly selected assessment policy"))
    retained <- store.loadC(N.candidateKey(namespace, "merge-admitted"))
    admitted <- retained match {
      case Some(saved) => for {
        assessment <- saved.payload.assessment.map(Consequence.success)
          .getOrElse(N.invalid("retained merge admission has no actual assessment"))
        _ <- if (saved.payload.stage == "MergeAdmitted" && assessment.branch == "admit" &&
            assessment.sourceRevision == source.record.revision.value.toString && assessment.policy == policy &&
            saved.payload.indexTree == source.payload.indexTree && saved.payload.mergeHead == source.payload.mergeHead &&
            saved.payload.before.head == source.payload.before.head && saved.payload.validation == source.payload.validation)
          Consequence.unit else N.invalid("retained merge admission crosses its selected source or policy")
        _ <- V.preconditionsC(store, saved)
      } yield saved
      case None => for {
        assessment <- captureC(store, source, policy)
        selected <- if (assessment.branch != "admit") Consequence.success(source.copy(payload =
          source.payload.copy(assessment = Some(assessment)))) else for {
          saved <- store.saveC(N.candidateKey(namespace, "merge-admitted"), "native-merge-admitted", source.record,
            source.payload.copy(stage = "MergeAdmitted", assessment = Some(assessment)))
          _ <- store.acknowledgeC("merge-admitted")
        } yield saved
      } yield selected
    }
  } yield admitted

  def admitTriagedC(store: N.Store, source: N.Stage, from: org.goldenport.cncf.workflow.WorkflowInstancePersistence.InstanceRecord,
    policy: Policy, namespace: String, reference: ContextReference): Consequence[N.Stage] = for {
    assessment <- captureC(store, source, policy)
    _ <- if (assessment.branch == "semantic-uncertainty" && reference != null && reference.identity.trim.nonEmpty && reference.revision.trim.nonEmpty &&
        from.currentProgression.exists(_.value == "sync.MergeTriageClean")) Consequence.unit
      else N.invalid("triage cannot waive required independent review or missing checks")
    retained <- store.loadC(N.candidateKey(namespace, "merge-admitted"))
    payload = source.payload.copy(stage = "MergeAdmitted", assessment = Some(assessment.copy(branch = "admit",
      reason = "Actual independent triage CLEAN resolved the selected merge uncertainty", triageReference = Some(reference))))
    saved <- retained match {
      case Some(value) if value.payload == payload => Consequence.success(value)
      case Some(_) => N.invalid("retained triaged admission crosses its source")
      case None => store.saveC(N.candidateKey(namespace, "merge-admitted"), "native-merge-admitted", from, payload)
    }
  } yield saved
}
