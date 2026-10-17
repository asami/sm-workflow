package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.{ContextReference}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms
import NativeHostEvidence.Purpose

/** Adapters for the existing independent ports. Actual independently issued
  * effect scope is distinct from review/Human semantic attestation. No factory
  * actor, dummy verifier, success default or reply-decoded authority is used.
  * @since   Oct.  7, 2026
  *  version Oct. 15, 2026
  * @version Oct. 16, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] final class ThreeWorkflowHostEvidencePorts(reader: NativeHostEvidence.Reader) {
  import ThreeWorkflowHostEvidencePorts.*
  val splitStart: SplitPhaseTypedStartHumanVerification.EvidencePort = new SplitPhaseTypedStartHumanVerification.EvidencePort {
    def admitC(core: ActionCall.Core, lookupIdentity: String, target: Json) =
      _admit_evidence_c(core, Purpose.SplitStart, lookupIdentity, target)
  }

  val splitAnswer: SplitPhaseHumanAnswerVerification.EvidencePort = new SplitPhaseHumanAnswerVerification.EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, target: Json) =
      _admit_evidence_c(core, Purpose.SplitAnswer, key(reference.identity, reference.revision), target)
  }

  val repositoryHuman: RepositorySyncHumanConflict.EvidencePort = new RepositorySyncHumanConflict.EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, target: Json) =
      _admit_evidence_c(core, Purpose.RepositoryHuman, key(reference.identity, reference.revision), target)
  }

  // Reader owns the external evidence/permission boundary. Consumers receive
  // its admission, not another receipt to compare against their request.
  private def _admit_evidence_c(core: ActionCall.Core, purpose: Purpose, lookup: String, target: Json): Consequence[Unit] = for {
    actual <- reader.loadC(core, purpose, lookup, target)
    _ <- if (actual.nonEmpty) Consequence.unit
      else Consequence.securityPermissionDenied("Action has no independently issued host evidence for its actual target")
  } yield ()

  val goalReview: GoalPhaseNativeReview.EvidencePort = new GoalPhaseNativeReview.EvidencePort {
    def admitC(core: ActionCall.Core, input: GoalPhaseNativeWork.Request,
      submitted: ContinuationResult[GoalPhaseNativeWork.Result]) = for {
      binding <- goalReviewBindingC(core, input, submitted)
      _ <- _admit_evidence_c(core, Purpose.GoalReview, key(submitted.resultReference.identity, submitted.resultReference.revision), binding)
    } yield ()
  }
  val goalHuman: GoalPhaseOrderedHumanDecision.EvidencePort = new GoalPhaseOrderedHumanDecision.EvidencePort {
    def admitC(core: ActionCall.Core, question: WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing],
      submitted: ContinuationResult[GoalPhaseOrderedHumanDecision.Answer], participant: String) = for {
      binding <- goalHumanBindingC(core.component.get.core.instanceId.canonicalKey, question, submitted, participant)
      _ <- _admit_evidence_c(core, Purpose.GoalHuman,
        key(submitted.resultReference.identity, submitted.resultReference.revision), binding)
    } yield ()
  }
  val repositoryReview: RepositorySyncNativeMergeReview.EvidencePort = new RepositorySyncNativeMergeReview.EvidencePort {
    def admitC(core: ActionCall.Core, input: RepositorySyncNativeMergeReview.Request,
      submitted: ContinuationResult[RepositorySyncNativeMergeReview.Result]) = for {
      binding <- repositoryReviewBindingC(core, input, submitted)
      _ <- _admit_evidence_c(core, Purpose.RepositoryReview, key(submitted.resultReference.identity, submitted.resultReference.revision), binding)
    } yield ()
  }
  val stepClosure: GoalPhaseNativeClosure.AuthorityPort = new GoalPhaseNativeClosure.AuthorityPort {
    def isGrantedC(core: ActionCall.Core, input: GoalPhaseNativeClosure.Input) = for {
      binding <- stepClosureBindingC(core, input)
      actual <- reader.loadC(core, Purpose.StepClosure, key(input.instance, input.sourceRevision),
        binding)
    } yield actual.nonEmpty
  }
  val phaseClosure: GoalPhaseNativeRepositoryClosure.AuthorityPort = new GoalPhaseNativeRepositoryClosure.AuthorityPort {
    def isGrantedC(core: ActionCall.Core, input: GoalPhaseNativeRepositoryClosure.Input) = for {
      binding <- phaseClosureBindingC(core, input)
      actual <- reader.loadC(core, Purpose.PhaseClosure, key(input.instance, input.sourceRevision),
        binding)
    } yield actual.nonEmpty
  }
  val convergence: RepositorySyncNativeConvergence.AuthorityPort = new RepositorySyncNativeConvergence.AuthorityPort {
    def admitC(core: ActionCall.Core, context: RepositorySyncManagedFetch.StartContext, target: RepositoryGitRuntimePrograms.Target,
      effect: RepositorySyncNativeConvergence.Effect) = for {
      binding <- repositoryEffectBindingC(core, context, target, effect)
      actual <- reader.loadC(core, Purpose.RepositoryEffect, key(context.selectionIdentity, context.invocationIdentity, effect.toString),
        binding)
      _ <- if (actual.nonEmpty) Consequence.unit
        else Consequence.securityPermissionDenied("Repository effect has no independently issued host authorization")
    } yield ()
  }

}

/** Shared wire bindings for the development host/client. Encoding is passive:
  * it does not install a record, attest independent review or authorize a call.
  */
private[smworkflow] object ThreeWorkflowHostEvidencePorts {
  def key(parts: String*): String = Json.fromValues(parts.map(Json.fromString)).noSpaces
  private def scopeBinding(core: ActionCall.Core, scope: Json): Json = Json.obj(
    "componentInstance" -> Json.fromString(core.component.get.core.instanceId.canonicalKey), "scope" -> scope)
  def stepClosureBindingC(core: ActionCall.Core, input: GoalPhaseNativeClosure.Input): Consequence[Json] =
    Consequence.success(scopeBinding(core, input.asJson.mapObject(_.add("kind", Json.fromString("step-closure")))))
  def phaseClosureBindingC(core: ActionCall.Core, input: GoalPhaseNativeRepositoryClosure.Input): Consequence[Json] =
    Consequence.success(scopeBinding(core, Json.obj("kind" -> Json.fromString("phase-closure"),
      "instance" -> Json.fromString(input.instance), "sourceRevision" -> Json.fromString(input.sourceRevision),
      "plan" -> Json.obj("identity" -> Json.fromString(input.plan.identity), "revision" -> Json.fromString(input.plan.revision),
        "phase" -> Json.fromString(input.plan.phase), "repository" -> Json.fromString(input.plan.repository)),
      "base" -> input.base.asJson, "candidate" -> input.candidate.asJson,
      "completed" -> Json.fromValues(input.completed.map(step => Json.obj("step" -> Json.fromString(step.step), "commit" -> step.commit.asJson))),
      "review" -> Json.fromString(input.review), "childInvocation" -> Json.fromString(input.childInvocation))))
  def repositoryEffectBindingC(core: ActionCall.Core, context: RepositorySyncManagedFetch.StartContext,
    target: RepositoryGitRuntimePrograms.Target, effect: RepositorySyncNativeConvergence.Effect): Consequence[Json] = {
    if (core == null || core.component == null || core.component.isEmpty || core.component.get == null ||
        core.component.get.core == null || core.component.get.core.instanceId == null) {
      Consequence.stateConflict("Repository effect owning component is absent")
    } else NativeHostRepositoryEffectScope.bindingFromInputsC(core.component.get.core.instanceId.canonicalKey,
      context, target, effect)
  }
  def splitStartBindingC(value: SplitPhaseTypedStartHumanVerification.StartContext): Consequence[Json] =
    NativeHostSplitStartScope.bindingC(value)
  def splitAnswerBindingC(value: SplitPhaseHumanAnswerVerification.AnswerContext): Consequence[Json] =
    NativeHostSplitAnswerScope.bindingC(value)
  def repositoryHumanSourceC(question: WorkflowInteraction[RepositorySyncHumanConflict.Input, Nothing]): Consequence[Json] = {
    if (question == null) return Consequence.stateConflict("Repository Human question is absent")
    question.current match {
      case decision: WorkflowContinuation.Decision[RepositorySyncHumanConflict.Input] if decision.request != null &&
          decision.request.input != null && decision.request.input.exists(v => v != null && v.value != null && v.value.conflict != null) =>
        val input = decision.request.input.get.value
        val c = input.conflict
        if (c.conflicts == null || c.conflicts.exists(e => e == null || e.base == null || e.local == null || e.remote == null ||
              Vector(e.identity, e.path, e.base.identity, e.local.identity, e.remote.identity, e.mode).exists(_ == null)) ||
            Vector(input.requestingResultIdentity, input.requestingResultRevision, c.instanceIdentity, c.conflictRevision, c.localTip, c.remoteTip).exists(_ == null)) {
          Consequence.stateConflict("Repository Human selected conflict is absent")
        } else Consequence.success(Json.obj("requestingResult" -> Json.obj("identity" -> Json.fromString(input.requestingResultIdentity),
          "revision" -> Json.fromString(input.requestingResultRevision)), "conflict" -> Json.obj(
          "instanceIdentity" -> Json.fromString(c.instanceIdentity), "conflictRevision" -> Json.fromString(c.conflictRevision),
          "localTip" -> Json.fromString(c.localTip), "remoteTip" -> Json.fromString(c.remoteTip), "conflicts" -> Json.fromValues(c.conflicts.map(e =>
            Json.obj("identity" -> Json.fromString(e.identity), "path" -> Json.fromString(e.path), "base" -> Json.fromString(e.base.identity),
              "local" -> Json.fromString(e.local.identity), "remote" -> Json.fromString(e.remote.identity), "mode" -> Json.fromString(e.mode)))))))
      case _ => Consequence.stateConflict("Repository Human evidence has no actual conflict question")
    }
  }

  def goalHumanSourceC(question: WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing]): Consequence[Json] = {
    if (question == null) return Consequence.stateConflict("Ordered Human question is absent")
    question.current match {
      case decision: WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input] if decision.request != null &&
          decision.request.input != null && decision.request.input.exists(v => v != null && v.value != null && v.value.work != null) =>
        val input = decision.request.input.get.value
        val work = input.work
        if (input.requestingResult == null || work.selectedPlan == null || work.originalBase == null ||
            work.stepIdentity == null || work.stepIdentity.exists(_ == null) || work.candidate == null ||
            work.candidate.exists(c => c == null || c.head == null || c.tree == null) || work.checksReceipt == null ||
            work.checksReceipt.exists(_ == null) || Vector(input.requestingResult.identity, input.requestingResult.revision,
              work.instanceIdentity, work.sourceRevision, work.kind, work.selectedPlan.identity, work.selectedPlan.revision,
              work.selectedPlan.phase, work.selectedPlan.repository, work.originalBase.head, work.originalBase.tree).exists(_ == null)) {
          Consequence.stateConflict("Ordered Human selected assignment is absent")
        } else Consequence.success(Json.obj("requestingResult" -> Json.obj("identity" -> Json.fromString(input.requestingResult.identity),
          "revision" -> Json.fromString(input.requestingResult.revision)), "assignment" -> Json.obj(
          "instanceIdentity" -> Json.fromString(work.instanceIdentity), "sourceRevision" -> Json.fromString(work.sourceRevision),
          "kind" -> Json.fromString(work.kind), "stepIdentity" -> work.stepIdentity.asJson, "generation" -> Json.fromLong(work.generation),
          "selectedPlan" -> Json.obj("identity" -> Json.fromString(work.selectedPlan.identity), "revision" -> Json.fromString(work.selectedPlan.revision),
            "phase" -> Json.fromString(work.selectedPlan.phase), "repository" -> Json.fromString(work.selectedPlan.repository)),
          "originalBase" -> work.originalBase.asJson, "candidate" -> work.candidate.asJson, "checksReceipt" -> work.checksReceipt.asJson)))
      case _ => Consequence.stateConflict("Ordered Human evidence has no actual assigned-work question")
    }
  }

  def repositoryHumanBindingC(value: RepositorySyncHumanConflict.AnswerContext): Consequence[Json] = {
    if (value == null || value.componentInstance == null || value.result == null || value.result.result == null ||
        value.result.result.value == null) return Consequence.stateConflict("Repository Human target is absent")
    val answer = value.result.result.value
    if (answer.choice == "ProvideResolution" && (answer.resolutions == null || answer.resolutions.exists(_ == null))) {
      Consequence.stateConflict("Repository Human resolution is absent")
    } else for {
      source <- repositoryHumanSourceC(value.question)
      binding <- NativeHostHumanScope.bindingC(value.componentInstance.canonicalKey, Purpose.RepositoryHuman, value.question, value.result,
        value.submittingParticipant, source, Json.obj("choice" -> Json.fromString(answer.choice), "resolutions" ->
          Json.fromValues(if (answer.choice == "ProvideResolution") answer.resolutions.map(r =>
            Json.obj("conflictIdentity" -> Json.fromString(r.conflictIdentity), "text" -> Json.fromString(r.text))) else Vector.empty)))
    } yield binding
  }

  def goalHumanBindingC(componentInstance: String, question: WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing],
    submitted: ContinuationResult[GoalPhaseOrderedHumanDecision.Answer], participant: String): Consequence[Json] = {
    if (submitted == null || submitted.result == null || submitted.result.value == null) {
      Consequence.stateConflict("Ordered Human target is absent")
    } else for {
      source <- goalHumanSourceC(question)
      binding <- NativeHostHumanScope.bindingC(componentInstance, Purpose.GoalHuman, question, submitted, participant,
        source, Json.obj("choice" -> Json.fromString(submitted.result.value.choice)))
    } yield binding
  }
  def goalReviewBindingC(core: ActionCall.Core, input: GoalPhaseNativeWork.Request,
    submitted: ContinuationResult[GoalPhaseNativeWork.Result]): Consequence[Json] =
    if (input == null || submitted == null || submitted.result == null || submitted.result.value == null)
      Consequence.stateConflict("Goal review target or result is absent")
    else for {
      _ <- GoalPhaseAcceptanceBoundary.savedPlanC(input.selectedPlan)
      report = submitted.result.value.acceptance
      _ <- if (report == null) Consequence.stateConflict("Goal review report option is absent")
        else report.fold(Consequence.unit)(GoalPhaseAcceptanceBoundary.validateReportC)
    } yield _review_target(core, "goal-review", goalReviewTarget(input), submitted,
      goalReviewVerdict(submitted.result.value))
  def goalReviewTarget(input: GoalPhaseNativeWork.Request): Json =
    Json.obj("instance" -> Json.fromString(input.instanceIdentity), "sourceRevision" -> Json.fromString(input.sourceRevision),
      "workKind" -> Json.fromString(input.kind), "step" -> input.stepIdentity.asJson, "generation" -> Json.fromLong(input.generation),
      "plan" -> Json.obj("identity" -> Json.fromString(input.selectedPlan.identity), "revision" -> Json.fromString(input.selectedPlan.revision),
        "phase" -> Json.fromString(input.selectedPlan.phase), "repository" -> Json.fromString(input.selectedPlan.repository)),
      "base" -> input.originalBase.asJson, "candidate" -> input.candidate.asJson, "checksReceipt" -> input.checksReceipt.asJson,
      "changedPaths" -> input.review.map(_.changedPaths).getOrElse(Vector.empty).asJson).mapObject { fields =>
        val checks = input.review.toVector.flatMap(_.checks).filter(_.sbt.nonEmpty)
        val target = input.selectedPlan.effectiveAcceptance.fold(fields)(value => fields.add("effectiveAcceptance", value.asJson))
        if (checks.isEmpty) target else target.add("testChecks", Json.fromValues(checks.map(_check_target)))
      }
  def goalReviewVerdict(result: GoalPhaseNativeWork.Result): Json =
    Json.obj("outcome" -> Json.fromString(result.outcome), "findings" -> result.findings.asJson,
      "repairSummary" -> (if (result.outcome == "repair") Json.fromString(result.summary) else Json.Null))
      .mapObject(fields => result.acceptance.fold(fields)(value => fields.add("acceptance", value.asJson)))
  def repositoryReviewBindingC(core: ActionCall.Core, input: RepositorySyncNativeMergeReview.Request,
    submitted: ContinuationResult[RepositorySyncNativeMergeReview.Result]): Consequence[Json] = {
    Consequence.success(_review_target(core, "repository-review", repositoryReviewTarget(input), submitted,
      repositoryReviewVerdict(submitted.result.value)))
  }
  def repositoryReviewTarget(input: RepositorySyncNativeMergeReview.Request): Json =
    Json.obj("instance" -> Json.fromString(input.instanceIdentity), "validatedRevision" -> Json.fromString(input.validatedRevision),
      "localTip" -> Json.fromString(input.localTip), "remoteTip" -> Json.fromString(input.remoteTip), "indexTree" -> Json.fromString(input.indexTree),
      "changedPaths" -> input.changedPaths.asJson, "validationMode" -> Json.fromString(input.validationMode), "selectedChecks" -> input.selectedChecks.asJson,
      "checks" -> Json.fromValues(input.checks.map(_check_target)))
  private def _check_target(receipt: RepositorySyncNativeMergeValidation.Receipt): Json = {
    val fields = Json.obj("identity" -> Json.fromString(receipt.identity),
      "termination" -> Json.fromString(receipt.termination), "exitCode" -> receipt.exitCode.asJson)
    receipt.sbt.fold(fields)(facts => fields.mapObject(_.add("sbt", facts.asJson)))
  }
  def repositoryReviewVerdict(result: RepositorySyncNativeMergeReview.Result): Json =
    Json.obj("decision" -> Json.fromString(result.decision), "findings" -> Json.fromValues(result.findings.map(f => Json.obj(
      "identity" -> Json.fromString(f.identity), "affectedPaths" -> f.affectedPaths.asJson,
      "repairRationale" -> (if (result.decision == "repair") Json.fromString(f.rationale) else Json.Null)))))
  private def _review_target[A](core: ActionCall.Core, kind: String, target: Json,
    value: ContinuationResult[A], verdict: Json): Json = reviewBinding(core.component.get.core.instanceId.canonicalKey,
      kind, target, Json.obj("handle" -> NativeHostHumanScope.handleJson(value.handle),
        "runId" -> Json.fromString(value.runId.value), "continuationId" -> Json.fromString(value.continuationId.value),
        "typeIdentity" -> Json.fromString(value.result.typeIdentity)),
      Json.obj("identity" -> Json.fromString(value.resultReference.identity), "revision" -> Json.fromString(value.resultReference.revision)),
      Json.fromString(value.evidence.workerIdentity.get), verdict)

  def reviewBinding(componentInstance: String, kind: String, target: Json, completion: Json,
    reference: Json, worker: Json, verdict: Json): Json = Json.obj(
    "componentInstance" -> Json.fromString(componentInstance), "attestation" -> Json.obj(
      "kind" -> Json.fromString(kind), "target" -> target, "result" -> Json.obj(
        "handle" -> completion.hcursor.downField("handle").focus.getOrElse(Json.Null),
        "runId" -> completion.hcursor.downField("runId").focus.getOrElse(Json.Null),
        "continuationId" -> completion.hcursor.downField("continuationId").focus.getOrElse(Json.Null),
        "reference" -> reference, "typeIdentity" -> completion.hcursor.downField("typeIdentity").focus.getOrElse(Json.Null), "worker" -> worker,
        "verdict" -> verdict)))
}
