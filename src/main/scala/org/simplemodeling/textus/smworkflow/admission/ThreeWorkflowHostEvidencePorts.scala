package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.{ContextReference, WorkflowDecisionJsonV1, WorkflowResultJsonV1}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms
import NativeHostEvidence.{Purpose, Observed}

/** Adapters for the existing independent ports. Only an actual host-issued
  * record matching the entire current binding produces a receipt. No factory
  * actor, dummy verifier, success default or reply-decoded authority is used.
  * @since   Oct.  7, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] final class ThreeWorkflowHostEvidencePorts(reader: NativeHostEvidence.Reader) {
  import ThreeWorkflowHostEvidencePorts.*
  private val issuer = reader.declaration.issuer

  val splitStart: SplitPhaseTypedStartHumanVerification.Verifier = new SplitPhaseTypedStartHumanVerification.Verifier {
    def issuerIdentity = issuer
    def verifyForCallC(expected: SplitPhaseTypedStartHumanVerification.Binding, core: ActionCall.Core) = for {
      binding <- splitStartBindingC(expected)
      actual <- reader.loadC(core, Purpose.SplitStart, key(expected.start.reference.identity, expected.start.reference.revision), binding)
    } yield actual.map { observed =>
      val e = observed.evidence
      SplitPhaseTypedStartHumanVerification.Receipt(
        SplitPhaseTypedStartHumanVerification.EvidenceReference(e.reference, e.revision), e.issuer, e.participant,
        e.purpose, expected, observed.occurredAt, observed.validUntil)
    }
  }

  val splitAnswer: SplitPhaseHumanAnswerVerification.Verifier = new SplitPhaseHumanAnswerVerification.Verifier {
    def issuerIdentity = issuer
    def verifyForCallC(reference: SplitPhaseHumanAnswerVerification.EvidenceReference,
      expected: SplitPhaseHumanAnswerVerification.Binding, core: ActionCall.Core) = for {
      binding <- splitAnswerBindingC(expected)
      actual <- reader.loadC(core, Purpose.SplitAnswer, key(reference.identity, reference.revision), binding)
      _ <- referenceC(actual, reference.identity, reference.revision)
    } yield actual.map { o => SplitPhaseHumanAnswerVerification.Receipt(reference, o.evidence.issuer,
      o.evidence.participant, o.evidence.purpose, expected, o.occurredAt, o.validUntil) }
  }

  val repositoryHuman: RepositorySyncHumanConflict.Verifier = new RepositorySyncHumanConflict.Verifier {
    def issuerIdentity = issuer
    def verifyForCallC(reference: ContextReference, expected: RepositorySyncHumanConflict.Binding, core: ActionCall.Core) = for {
      binding <- repositoryHumanBindingC(expected)
      actual <- reader.loadC(core, Purpose.RepositoryHuman, key(reference.identity, reference.revision), binding)
      _ <- referenceC(actual, reference.identity, reference.revision)
    } yield actual.map { o => RepositorySyncHumanConflict.Receipt(reference, o.evidence.issuer,
      o.evidence.participant, o.evidence.purpose, expected, o.occurredAt, o.validUntil) }
  }

  val goalReview: GoalPhaseNativeReview.EvidencePort = new GoalPhaseNativeReview.EvidencePort {
    def admitC(core: ActionCall.Core, input: GoalPhaseNativeWork.Request,
      submitted: ContinuationResult[GoalPhaseNativeWork.Result]) = for {
      binding <- reviewBindingC(core, GoalPhaseNativeWork.inputCodec.encode(input), submitted, GoalPhaseNativeWork.resultCodec)
      _ <- reviewC(core, Purpose.GoalReview, submitted, binding)
    } yield ()
  }
  val goalHuman: GoalPhaseOrderedHumanDecision.EvidencePort = new GoalPhaseOrderedHumanDecision.EvidencePort {
    def admitC(core: ActionCall.Core, question: WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing],
      submitted: ContinuationResult[GoalPhaseOrderedHumanDecision.Answer], participant: String) = for {
      binding <- goalHumanBindingC(core.component.get.core.instanceId.canonicalKey, question, submitted, participant)
      actual <- reader.loadC(core, Purpose.GoalHuman,
        key(submitted.resultReference.identity, submitted.resultReference.revision), binding)
      _ <- referenceC(actual, submitted.resultReference.identity, submitted.resultReference.revision)
      _ <- if (actual.exists(o => submitted.evidence.workerIdentity.contains(o.evidence.participant))) Consequence.unit
        else Consequence.securityPermissionDenied("Ordered Human answer has no independent host evidence for its exact question/result")
    } yield ()
  }
  val repositoryReview: RepositorySyncNativeMergeReview.EvidencePort = new RepositorySyncNativeMergeReview.EvidencePort {
    def admitC(core: ActionCall.Core, input: RepositorySyncNativeMergeReview.Request,
      submitted: ContinuationResult[RepositorySyncNativeMergeReview.Result]) = for {
      binding <- reviewBindingC(core, RepositorySyncNativeMergeReview.inputCodec.encode(input), submitted,
        RepositorySyncNativeMergeReview.resultCodec)
      _ <- reviewC(core, Purpose.RepositoryReview, submitted, binding)
    } yield ()
  }
  private def reviewC[A](core: ActionCall.Core, purpose: Purpose, submitted: ContinuationResult[A], binding: Json) = for {
    actual <- reader.loadC(core, purpose, key(submitted.resultReference.identity, submitted.resultReference.revision), binding)
    _ <- referenceC(actual, submitted.resultReference.identity, submitted.resultReference.revision)
    _ <- if (actual.exists(o => submitted.evidence.workerIdentity.contains(o.evidence.participant))) Consequence.unit
      else Consequence.securityPermissionDenied("Review has no independent host-issued evidence for the actual reviewer/result")
  } yield ()

  val stepClosure: GoalPhaseNativeClosure.AuthorityPort = new GoalPhaseNativeClosure.AuthorityPort {
    def loadForCallC(core: ActionCall.Core, input: GoalPhaseNativeClosure.Input) = for {
      actual <- reader.loadC(core, Purpose.StepClosure, key(input.instance, input.sourceRevision),
        ownedBinding(core, input.asJson))
      revision <- integerRevisionC(actual)
    } yield actual.zip(revision).map { (o, revision) => GoalPhaseNativeClosure.Authorization(o.evidence.reference, revision, input) }
  }
  val phaseClosure: GoalPhaseNativeRepositoryClosure.AuthorityPort = new GoalPhaseNativeRepositoryClosure.AuthorityPort {
    def loadForCallC(core: ActionCall.Core, input: GoalPhaseNativeRepositoryClosure.Input) = for {
      actual <- reader.loadC(core, Purpose.PhaseClosure, key(input.instance, input.sourceRevision),
        ownedBinding(core, input.asJson))
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
      actual <- reader.loadC(core, Purpose.RepositoryEffect, key(selection, invocation, effect.toString),
        ownedBinding(core, Json.obj("originalCommand" -> originalCommand, "target" -> Json.obj(
          "root" -> Json.fromString(target.root.toString), "localBranch" -> Json.fromString(target.localBranch),
          "remote" -> Json.fromString(target.remote), "remoteBranch" -> Json.fromString(target.remoteBranch),
          "endpoint" -> Json.fromString(target.endpoint)), "effect" -> Json.fromString(effect.toString))))
      _ <- if (actual.nonEmpty) Consequence.unit
        else Consequence.securityPermissionDenied("Repository effect has no independently issued host authorization")
    } yield ()
  }

  private def referenceC(actual: Option[Observed], identity: String, revision: String): Consequence[Unit] =
    if (actual.forall(o => o.evidence.reference == identity && o.evidence.revision == revision)) Consequence.unit
    else Consequence.securityPermissionDenied("Host evidence belongs to another result reference")
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
  private def parseC(raw: String): Consequence[Json] = io.circe.parser.parse(raw)
    .fold(_ => Consequence.stateConflict("Host evidence binding could not be encoded"), Consequence.success)
  def splitStartBindingC(value: SplitPhaseTypedStartHumanVerification.Binding): Consequence[Json] =
    SplitPhaseTypedStartJsonV1.encodeC(value.start)
      .flatMap(raw => parseC(raw.substring(SplitPhaseTypedStartJsonV1.schema.length + 1))).map(record => Json.obj(
      "componentInstance" -> Json.fromString(value.componentInstance.canonicalKey), "start" -> record))
  def splitAnswerBindingC(value: SplitPhaseHumanAnswerVerification.Binding): Consequence[Json] = for {
    question <- value.question.current match {
      case decision: WorkflowContinuation.Decision[?] if decision.request.input.exists(_.typeIdentity ==
          SplitPhaseReasoningHumanDirection.inputCodec.typeIdentity) =>
        WorkflowDecisionJsonV1.encodeC(value.question.asInstanceOf[WorkflowInteraction[SplitPhaseReasoningHumanDirection.Input, Nothing]],
          SplitPhaseReasoningHumanDirection.inputCodec).flatMap(parseC)
      case decision: WorkflowContinuation.Decision[?] if decision.request.input.exists(_.typeIdentity ==
          SplitPhaseNativeHumanDirection.inputCodec.typeIdentity) =>
        WorkflowDecisionJsonV1.encodeC(value.question.asInstanceOf[WorkflowInteraction[SplitPhaseNativeHumanDirection.Input, Nothing]],
          SplitPhaseNativeHumanDirection.inputCodec).flatMap(parseC)
      case _ => Consequence.stateConflict("Human evidence binding has no declared scope or reasoning question")
    }
    result <- WorkflowResultJsonV1.encodeC(value.result, SplitPhaseHumanDirectionAnswerContract.payloadCodec).flatMap(parseC)
  } yield Json.obj("componentInstance" -> Json.fromString(value.componentInstance.canonicalKey),
    "question" -> question, "result" -> result, "submittingParticipant" -> Json.fromString(value.submittingParticipant))
  def repositoryHumanBindingC(value: RepositorySyncHumanConflict.Binding): Consequence[Json] = for {
    question <- WorkflowDecisionJsonV1.encodeC(value.question, RepositorySyncHumanConflict.inputCodec).flatMap(parseC)
    result <- WorkflowResultJsonV1.encodeC(value.result, RepositorySyncHumanConflict.answerCodec).flatMap(parseC)
  } yield Json.obj("componentInstance" -> Json.fromString(value.componentInstance.canonicalKey),
    "question" -> question, "result" -> result, "submittingParticipant" -> Json.fromString(value.submittingParticipant))
  def goalHumanBindingC(componentInstance: String,
    question: WorkflowInteraction[GoalPhaseOrderedHumanDecision.Input, Nothing],
    submitted: ContinuationResult[GoalPhaseOrderedHumanDecision.Answer], participant: String): Consequence[Json] = for {
    questionJson <- WorkflowDecisionJsonV1.encodeC(question, GoalPhaseOrderedHumanDecision.inputCodec).flatMap(parseC)
    result <- WorkflowResultJsonV1.encodeC(submitted, GoalPhaseOrderedHumanDecision.answerCodec).flatMap(parseC)
  } yield Json.obj("componentInstance" -> Json.fromString(componentInstance), "question" -> questionJson,
    "result" -> result, "submittingParticipant" -> Json.fromString(participant))
  def reviewBindingC[A](core: ActionCall.Core, input: Json, submitted: ContinuationResult[A],
    codec: WorkflowResultJsonV1.PayloadCodec[A]): Consequence[Json] =
    WorkflowResultJsonV1.encodeC(submitted, codec).flatMap(parseC).map(result =>
      ownedBinding(core, Json.obj("input" -> input, "result" -> result)))
}
