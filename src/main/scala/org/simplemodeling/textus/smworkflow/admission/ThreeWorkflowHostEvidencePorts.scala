package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.{ContextReference, WorkflowResultJsonV1}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms
import NativeHostEvidence.{Purpose, Observed}

/** Adapters for the existing independent ports. Actual independently issued
  * effect scope is distinct from review/Human semantic attestation. No factory
  * actor, dummy verifier, success default or reply-decoded authority is used.
  * @since   Oct.  7, 2026
  * @version Oct.  9, 2026
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
    def loadForCallC(core: ActionCall.Core, input: GoalPhaseNativeClosure.Input) = for {
      binding <- stepClosureBindingC(core, input)
      actual <- reader.loadC(core, Purpose.StepClosure, key(input.instance, input.sourceRevision),
        binding)
      revision <- integerRevisionC(actual)
    } yield actual.zip(revision).map { (o, revision) => GoalPhaseNativeClosure.Authorization(o.evidence.reference, revision, input) }
  }
  val phaseClosure: GoalPhaseNativeRepositoryClosure.AuthorityPort = new GoalPhaseNativeRepositoryClosure.AuthorityPort {
    def loadForCallC(core: ActionCall.Core, input: GoalPhaseNativeRepositoryClosure.Input) = for {
      binding <- phaseClosureBindingC(core, input)
      actual <- reader.loadC(core, Purpose.PhaseClosure, key(input.instance, input.sourceRevision),
        binding)
      revision <- integerRevisionC(actual)
    } yield actual.zip(revision).map { (o, revision) => GoalPhaseNativeRepositoryClosure.Authorization(o.evidence.reference, revision, input) }
  }
  val convergence: RepositorySyncNativeConvergence.AuthorityPort = new RepositorySyncNativeConvergence.AuthorityPort {
    def admitC(core: ActionCall.Core, originalCommand: Json, target: RepositoryGitRuntimePrograms.Target,
      effect: RepositorySyncNativeConvergence.Effect) = for {
      selection <- originalCommand.hcursor.get[String]("selectionIdentity").fold(
        _ => Consequence.stateConflict("Repository effect has no original selected command"), Consequence.success)
      invocation <- originalCommand.hcursor.get[String]("idempotencyKey").fold(
        _ => Consequence.stateConflict("Repository effect has no original invocation"), Consequence.success)
      binding <- repositoryEffectBindingC(core, originalCommand, target, effect)
      actual <- reader.loadC(core, Purpose.RepositoryEffect, key(selection, invocation, effect.toString),
        binding)
      _ <- if (actual.nonEmpty) Consequence.unit
        else Consequence.securityPermissionDenied("Repository effect has no independently issued host authorization")
    } yield ()
  }

  private def integerRevisionC(actual: Option[Observed]): Consequence[Option[Int]] = actual match {
    case None => Consequence.success(None)
    case Some(value) => value.evidence.revision.toIntOption.filter(_ > 0).map(v => Consequence.success(Some(v)))
      .getOrElse(Consequence.stateConflict("Native closure authority requires its positive integer revision"))
  }
}

/** Shared wire bindings for the development host/client. Encoding is passive:
  * it does not install a record, attest independent review or authorize a call.
  */
private[smworkflow] object ThreeWorkflowHostEvidencePorts {
  def key(parts: String*): String = Json.fromValues(parts.map(Json.fromString)).noSpaces
  def ownedBinding(core: ActionCall.Core, value: Json): Json = Json.obj(
    "componentInstance" -> Json.fromString(core.component.get.core.instanceId.canonicalKey), "value" -> value)
  private def authorityBindingC(core: ActionCall.Core, purpose: Purpose, value: Json): Consequence[Json] =
    NativeHostAuthorityScope.select(purpose, ownedBinding(core, value), legacy = true)
      .fold(Consequence.stateConflict(_), Consequence.success)
  def stepClosureBindingC(core: ActionCall.Core, input: GoalPhaseNativeClosure.Input): Consequence[Json] =
    authorityBindingC(core, Purpose.StepClosure, input.asJson)
  def phaseClosureBindingC(core: ActionCall.Core, input: GoalPhaseNativeRepositoryClosure.Input): Consequence[Json] =
    authorityBindingC(core, Purpose.PhaseClosure, Json.obj(
      "instance" -> Json.fromString(input.instance), "sourceRevision" -> Json.fromString(input.sourceRevision),
      "plan" -> Json.obj("identity" -> Json.fromString(input.plan.identity), "revision" -> Json.fromString(input.plan.revision),
        "phase" -> Json.fromString(input.plan.phase), "repository" -> Json.fromString(input.plan.repository)),
      "base" -> input.base.asJson, "candidate" -> input.candidate.asJson,
      "completed" -> Json.fromValues(input.completed.map(step => Json.obj("step" -> Json.fromString(step.step), "commit" -> step.commit.asJson))),
      "review" -> Json.fromString(input.review), "childInvocation" -> Json.fromString(input.childInvocation)))
  def repositoryEffectBindingC(core: ActionCall.Core, originalCommand: Json, target: RepositoryGitRuntimePrograms.Target,
    effect: RepositorySyncNativeConvergence.Effect): Consequence[Json] =
    authorityBindingC(core, Purpose.RepositoryEffect, Json.obj("originalCommand" -> originalCommand,
      "target" -> Json.obj("root" -> Json.fromString(target.root.toString), "localBranch" -> Json.fromString(target.localBranch),
        "remote" -> Json.fromString(target.remote), "remoteBranch" -> Json.fromString(target.remoteBranch),
        "endpoint" -> Json.fromString(target.endpoint)), "effect" -> Json.fromString(effect.toString)))
  def splitStartBindingC(value: SplitPhaseTypedStartHumanVerification.StartContext): Consequence[Json] =
    NativeHostSplitStartScope.bindingC(value)
  def splitAnswerBindingC(value: SplitPhaseHumanAnswerVerification.AnswerContext): Consequence[Json] =
    NativeHostSplitAnswerScope.bindingC(value)
  def repositoryHumanBindingC(value: RepositorySyncHumanConflict.AnswerContext): Consequence[Json] =
    if (value == null || value.componentInstance == null || value.question == null || value.result == null ||
        value.result.result == null || value.result.result.value == null) Consequence.stateConflict("Repository Human target is absent")
    else value.question.current match {
      case decision: WorkflowContinuation.Decision[RepositorySyncHumanConflict.Input] if decision.request != null &&
          decision.request.input.exists(v => v != null && v.value != null && v.value.conflict != null) =>
        val input = decision.request.input.get.value
        val c = input.conflict
        val answer = value.result.result.value
        NativeHostHumanScope.bindingC(value.componentInstance.canonicalKey, Purpose.RepositoryHuman, value.question, value.result,
          value.submittingParticipant, Json.obj("requestingResult" -> Json.obj("identity" -> Json.fromString(input.requestingResultIdentity),
            "revision" -> Json.fromString(input.requestingResultRevision)), "conflict" -> Json.obj(
            "instanceIdentity" -> Json.fromString(c.instanceIdentity), "conflictRevision" -> Json.fromString(c.conflictRevision),
            "localTip" -> Json.fromString(c.localTip), "remoteTip" -> Json.fromString(c.remoteTip), "conflicts" -> Json.fromValues(c.conflicts.map(e =>
              Json.obj("identity" -> Json.fromString(e.identity), "path" -> Json.fromString(e.path), "base" -> Json.fromString(e.base.identity),
                "local" -> Json.fromString(e.local.identity), "remote" -> Json.fromString(e.remote.identity), "mode" -> Json.fromString(e.mode)))))),
          Json.obj("choice" -> Json.fromString(answer.choice), "resolutions" -> Json.fromValues(answer.resolutions.map(r =>
            Json.obj("conflictIdentity" -> Json.fromString(r.conflictIdentity), "text" -> Json.fromString(r.text))))))
      case _ => Consequence.stateConflict("Repository Human evidence has no actual conflict question")
    }
  def goalHumanBindingC(componentInstance: String,
    question: WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing],
    submitted: ContinuationResult[GoalPhaseOrderedHumanDecision.Answer], participant: String): Consequence[Json] =
    if (question == null || submitted == null || submitted.result == null || submitted.result.value == null)
      Consequence.stateConflict("Ordered Human target is absent")
    else question.current match {
      case decision: WorkflowContinuation.Decision[GoalPhaseOrderedHumanDecision.Input] if decision.request != null &&
          decision.request.input.exists(v => v != null && v.value != null && v.value.work != null) =>
        val input = decision.request.input.get.value
        val work = input.work
        NativeHostHumanScope.bindingC(componentInstance, Purpose.GoalHuman, question, submitted, participant,
          Json.obj("requestingResult" -> Json.obj("identity" -> Json.fromString(input.requestingResult.identity),
            "revision" -> Json.fromString(input.requestingResult.revision)), "assignment" -> Json.obj(
            "instanceIdentity" -> Json.fromString(work.instanceIdentity), "sourceRevision" -> Json.fromString(work.sourceRevision),
            "kind" -> Json.fromString(work.kind), "stepIdentity" -> work.stepIdentity.asJson, "generation" -> Json.fromLong(work.generation),
            "selectedPlan" -> Json.obj("identity" -> Json.fromString(work.selectedPlan.identity), "revision" -> Json.fromString(work.selectedPlan.revision),
              "phase" -> Json.fromString(work.selectedPlan.phase), "repository" -> Json.fromString(work.selectedPlan.repository)),
            "originalBase" -> work.originalBase.asJson, "candidate" -> work.candidate.asJson, "checksReceipt" -> work.checksReceipt.asJson)),
          Json.obj("choice" -> Json.fromString(submitted.result.value.choice)))
      case _ => Consequence.stateConflict("Ordered Human evidence has no actual assigned-work question")
    }
  def reviewBindingC[A](core: ActionCall.Core, input: Json, submitted: ContinuationResult[A],
    codec: WorkflowResultJsonV1.PayloadCodec[A]): Consequence[Json] = {
    if (core == null || codec == null || submitted == null || submitted.handle == null || submitted.runId == null ||
        submitted.continuationId == null ||
        submitted.resultReference == null || submitted.result == null || submitted.result.value == null || submitted.evidence == null)
      return Consequence.stateConflict("Independent review has no actual result target")
    val purpose = if (codec.typeIdentity == GoalPhaseNativeWork.resultCodec.typeIdentity) Some(Purpose.GoalReview)
      else if (codec.typeIdentity == RepositorySyncNativeMergeReview.resultCodec.typeIdentity) Some(Purpose.RepositoryReview) else None
    purpose.map { purpose =>
      NativeHostReviewScope.select(purpose, ownedBinding(core, Json.obj("input" -> input,
        "result" -> reviewResultJson(submitted, codec))), legacy = true).fold(Consequence.stateConflict(_), Consequence.success)
    }.getOrElse(Consequence.stateConflict("Independent review has no declared semantic result type"))
  }
  def goalReviewBindingC(core: ActionCall.Core, input: GoalPhaseNativeWork.Request,
    submitted: ContinuationResult[GoalPhaseNativeWork.Result]): Consequence[Json] =
    reviewBindingC(core, Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-work-request.v2"),
      "payload" -> Json.obj("instanceIdentity" -> Json.fromString(input.instanceIdentity), "sourceRevision" -> Json.fromString(input.sourceRevision),
        "kind" -> Json.fromString(input.kind), "stepIdentity" -> input.stepIdentity.map(Json.fromString).getOrElse(Json.Null),
        "generation" -> Json.fromLong(input.generation), "selectedPlan" -> Json.obj(
          "identity" -> Json.fromString(input.selectedPlan.identity), "revision" -> Json.fromString(input.selectedPlan.revision),
          "phase" -> Json.fromString(input.selectedPlan.phase), "repository" -> Json.fromString(input.selectedPlan.repository)),
        "originalBase" -> input.originalBase.asJson, "candidate" -> input.candidate.asJson,
        "checksReceipt" -> input.checksReceipt.asJson, "review" -> input.review.map(r => Json.obj("changedPaths" -> r.changedPaths.asJson)).getOrElse(Json.Null))),
      submitted, GoalPhaseNativeWork.resultCodec)
  def repositoryReviewBindingC(core: ActionCall.Core, input: RepositorySyncNativeMergeReview.Request,
    submitted: ContinuationResult[RepositorySyncNativeMergeReview.Result]): Consequence[Json] =
    reviewBindingC(core, Json.obj("schemaVersion" -> Json.fromString("sm-workflow.repository-native-merge-review-request.v2"),
      "payload" -> Json.obj("instanceIdentity" -> Json.fromString(input.instanceIdentity), "validatedRevision" -> Json.fromString(input.validatedRevision),
        "localTip" -> Json.fromString(input.localTip), "remoteTip" -> Json.fromString(input.remoteTip), "indexTree" -> Json.fromString(input.indexTree),
        "changedPaths" -> input.changedPaths.asJson, "validationMode" -> Json.fromString(input.validationMode), "selectedChecks" -> input.selectedChecks.asJson,
        "checks" -> Json.fromValues(input.checks.map(r => Json.obj("identity" -> Json.fromString(r.identity),
          "termination" -> Json.fromString(r.termination), "exitCode" -> r.exitCode.asJson))))), submitted, RepositorySyncNativeMergeReview.resultCodec)

  // Actual result correlation and semantic disposition only. No complete
  // transport validation/serialization round-trip at this evidence adapter.
  private def reviewResultJson[A](value: ContinuationResult[A], codec: WorkflowResultJsonV1.PayloadCodec[A]): Json = Json.obj(
    "schemaVersion" -> Json.fromString(org.goldenport.cncf.workflow.WorkflowProtocolV1.schemaVersion), "kind" -> Json.fromString("CONTINUATION_RESULT"),
    "handle" -> Json.obj("componentIdentity" -> Json.fromString(value.handle.componentIdentity.name),
      "workflowIdentity" -> Json.fromString(value.handle.workflowIdentity.value), "workflowRevision" -> Json.fromString(value.handle.workflowRevision.value),
      "instanceIdentity" -> Json.fromString(value.handle.instanceIdentity.value), "protocolVersion" -> Json.fromString(value.handle.protocolVersion)),
    "runId" -> Json.fromString(value.runId.value), "continuationId" -> Json.fromString(value.continuationId.value),
    "result" -> Json.obj("reference" -> Json.obj("identity" -> Json.fromString(value.resultReference.identity), "revision" -> Json.fromString(value.resultReference.revision)),
      "typeIdentity" -> Json.fromString(value.result.typeIdentity), "value" -> codec.encode(value.result.value)),
    "executionEvidence" -> Json.obj("workerIdentity" -> value.evidence.workerIdentity.asJson))
}
