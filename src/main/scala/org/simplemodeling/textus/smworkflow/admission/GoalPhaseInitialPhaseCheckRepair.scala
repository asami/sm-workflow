package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeValidation as V
import GoalPhaseRuntimeRepairControl as R

/** Append-only admission of the first ordinary failed whole-Phase check under
  * an adopted runtime policy. Historical failed validation remains Decision.
  * This pure native transition grants no command or independent-review authority.
  * @since   Oct. 26, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseInitialPhaseCheckRepair {
  val schema = "sm-workflow.initial-phase-check-repair.v1"
  val causation = "ordered-initial-phase-check-repair:admitted"
  private val _namespace = ":ordered-initial-phase-check-repair:"

  private[admission] final class Source private[GoalPhaseInitialPhaseCheckRepair](
    val from: InstanceRecord, val controller: O.State,
    val candidate: GoalPhaseNativeCandidate.Saved, val validation: V.Saved,
    val directive: O.RepairDirective) {
    def reference: WorkflowMechanicalTransition.Reference =
      WorkflowMechanicalTransition.Reference(from.identity.value + _namespace + from.revision.value, "1")
  }

  /** Only the continuation owner's native Cursor supplies these values. Ordinary
    * ineligibility selects no admission; contradictory claimed native facts fail.
    */
  private[admission] def selectC(from: InstanceRecord, controller: O.State,
    candidate: Option[GoalPhaseNativeCandidate.Saved], validation: Option[V.Saved],
    requiredchecks: Vector[V.Check]): Consequence[Option[Source]] = for {
    _ <- _check(from != null && controller != null && candidate != null && validation != null &&
      requiredchecks != null && requiredchecks.forall(_ != null) &&
      candidate.forall(c => c != null && c.record != null && c.payload != null) &&
      validation.forall(v => v != null && v.record != null && v.payload != null),
      "source contains null native material")
    selected <- if (controller.stage != O.Stage.Decision ||
        !controller.decisionSource.contains(O.Stage.PhaseValidation) || controller.documentDecision ||
        !controller.repairControl.exists(_.policy == R.version) || controller.repairs != 0 ||
        controller.ordinaryRepairs != 0 || controller.repairDirective.nonEmpty ||
        controller.pendingRepair.nonEmpty || controller.closureIntent.nonEmpty ||
        controller.reviewEvidence.nonEmpty || controller.phaseReviewedCandidate.nonEmpty ||
        controller.phaseRecordingPaths.nonEmpty || controller.releaseCommit.nonEmpty ||
        controller.repositoryReceipt.nonEmpty || controller.cursor != controller.plan.steps.size ||
        controller.completed.map(_.step) != controller.plan.steps.map(_.identity) ||
        !controller.completed.forall(O.validStepAcceptance) || controller.generation == Long.MaxValue ||
        controller.repairs >= controller.effectiveRepairLimit)
      Consequence.success(Option.empty[Source])
    else for {
      _ <- from.validateC
      _ <- _check(from.lifecycle == Lifecycle.Active && from.suspension.isEmpty &&
        from.currentProgression.contains(ProgressionReference("phase.Decision")) &&
        candidate.nonEmpty && validation.nonEmpty && controller.candidate.nonEmpty,
        "source has no exact unsuspended native Decision candidate and validation")
      actualcandidate = candidate.get
      actualvalidation = validation.get
      _ <- _check(_candidate_valid(actualcandidate.payload) && _roundtrip_candidate(actualcandidate.payload) &&
        _revision(actualvalidation.payload.sourceRevision) && _roundtrip_validation(actualvalidation.payload),
        "source has no complete codec-proven candidate and validation")
      result <- if (!terminalFailureValid(actualvalidation.payload)) Consequence.success(Option.empty[Source])
      else for {
        candidateprefix <- GoalPhaseNativeHistory.prefixC(from, actualcandidate.record.revision)
        validationprefix <- GoalPhaseNativeHistory.prefixC(from, actualvalidation.record.revision)
        candidatesource <- GoalPhaseNativeHistory.prefixC(from, InstanceRevision(actualcandidate.payload.sourceRevision.toLong))
        validationsource <- GoalPhaseNativeHistory.prefixC(from, InstanceRevision(actualvalidation.payload.sourceRevision.toLong))
        _ <- _check(candidateprefix == actualcandidate.record && validationprefix == actualvalidation.record &&
          candidatesource.currentProgression.contains(ProgressionReference("phase.PhaseInspection")) &&
          candidateprefix.currentProgression.contains(ProgressionReference("phase.PhaseValidation")) &&
          validationsource.currentProgression.contains(ProgressionReference("phase.PhaseValidation")) &&
          validationprefix.currentProgression.contains(ProgressionReference("phase.Decision")) &&
          candidatesource.revision.value < candidateprefix.revision.value &&
          candidateprefix.revision.value <= validationsource.revision.value &&
          validationsource.revision.value < validationprefix.revision.value &&
          from.history.drop(validationprefix.history.size).forall(entry => entry.suspension.isEmpty &&
            entry.currentProgression.contains(ProgressionReference("phase.Decision")) &&
            entry.causation.value.startsWith("ordered-repair-policy-adoption:")),
          "candidate or failed validation is not its actual same-Session historical prefix")
        _ <- _check(actualcandidate.payload.step == "phase:" + controller.plan.phase &&
          actualvalidation.payload.step == actualcandidate.payload.step &&
          actualcandidate.payload.candidate == controller.candidate &&
          actualcandidate.payload.candidate.contains(actualvalidation.payload.candidate) &&
          controller.decision.contains("Selected native checks failed: " + _checks_reference(actualvalidation)),
          "failed Decision changes its original candidate, Phase scope or failed receipt identity")
        _ <- _coverage_c(controller.plan, requiredchecks, actualvalidation.payload)
        directive = _directive(actualvalidation.payload)
        _ <- controller.continueInitialPhaseCheckRepairC(_checks_reference(actualvalidation), directive)
      } yield Some(new Source(from, controller, actualcandidate, actualvalidation, directive))
    } yield result
  } yield selected

  final case class Payload(sourceRevision: String, plan: ContextReference,
    candidateSourceRevision: String, candidateReference: ContextReference, candidate: O.Snapshot,
    validationSourceRevision: String, validationReference: ContextReference,
    policy: String, selectedLimit: Int, effectiveLimit: Int,
    generation: Long, automaticCount: Int, ordinaryCount: Int, directive: O.RepairDirective)
  object Payload {
    given Encoder[Payload] = deriveEncoder
    private val _decoder: Decoder[Payload] = deriveDecoder
    given Decoder[Payload] = Decoder.instance { cursor =>
      _decoder.tryDecode(cursor).flatMap { value =>
        if (_payload_valid(value) && cursor.value == value.asJson) Right(value)
        else Left(DecodingFailure("Initial Phase check repair requires exact closed non-null payload fields", cursor.history))
      }
    }
  }
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseInitialPhaseCheckRepairReceipt"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("initial Phase check repair is absent")
      _ <- Either.cond(wire.asObject.exists(_.keys.toSet == Set("schemaVersion", "payload")), (),
        "initial Phase check repair envelope fields differ")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(version == schema, (), "initial Phase check repair schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
    } yield payload
  }
  private[admission] final case class Saved(record: InstanceRecord, payload: Payload, controller: O.State) {
    def reference: ContextReference = ContextReference(record.identity.value + _namespace + payload.sourceRevision, "1")
  }

  private[admission] def payload(source: Source): Payload = Payload(source.from.revision.value.toString,
    ContextReference(source.controller.plan.identity, source.controller.plan.revision),
    source.candidate.payload.sourceRevision,
    ContextReference(source.from.identity.value + ":ordered-candidate:" + source.candidate.payload.sourceRevision + ":candidate", "1"),
    source.validation.payload.candidate, source.validation.payload.sourceRevision,
    ContextReference(_checks_reference(source.validation), "1"), source.controller.repairControl.get.policy,
    source.controller.selectedRepairLimit, source.controller.effectiveRepairLimit,
    source.controller.generation, source.controller.repairs, source.controller.ordinaryRepairs, source.directive)

  private[admission] def admittedStateC(source: Source, value: Payload): Consequence[O.State] = for {
    _ <- _check(source != null && _payload_valid(value) && value == payload(source),
      "admission changes its actual failed source, policy, counters or directive")
    state <- source.controller.continueInitialPhaseCheckRepairC(value.validationReference.identity, value.directive)
  } yield state

  private[admission] def successorC(from: InstanceRecord, saved: InstanceRecord): Consequence[Unit] = for {
    _ <- _check(from != null && saved != null && from.lifecycle == Lifecycle.Active && from.suspension.isEmpty &&
      from.currentProgression.contains(ProgressionReference("phase.Decision")), "admission has no actual Decision predecessor")
    _ <- from.validateC
    _ <- saved.validateC
    _ <- _relation_c
    expected <- from.appendC(from.revision, F.history(from, "phase.Repair", causation))
    _ <- _check(expected == saved, "admission is not the canonical adjacent full Decision-to-Repair successor")
  } yield ()

  private[admission] def retainedC(session: F.Session, core: ActionCall.Core, source: Source): Consequence[Option[Saved]] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    saved <- _retained_c(session, core, source, adapter)
  } yield saved

  private def _retained_c(session: F.Session, core: ActionCall.Core, source: Source,
    adapter: WorkflowManagedMechanicalTransition[Payload]): Consequence[Option[Saved]] = for {
    retained <- adapter.loadRetainedC(source.reference)(using core.executionContext)
    saved <- retained match {
      case None => Consequence.success(Option.empty[Saved])
      case Some(value) => for {
        sourceprefix <- GoalPhaseNativeHistory.prefixC(session.current, source.from.revision)
        prefix <- GoalPhaseNativeHistory.prefixC(session.current, value.receipt.next.revision)
        _ <- _check(sourceprefix == source.from && prefix == value.receipt.next,
          "retained admission changes its actual full native source or result prefix")
        _ <- successorC(source.from, prefix)
        state <- admittedStateC(source, value.receipt.payload.value)
      } yield Some(Saved(prefix, value.receipt.payload.value, state))
    }
  } yield saved

  private[admission] def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    source: Source): Consequence[Saved] = for {
    _ <- _check(session.current == source.from, "fresh admission omits later actual native history")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- _retained_c(session, core, source, adapter)
    saved <- retained match {
      case Some(value) => Consequence.success(value)
      case None => for {
        value <- Consequence.success(payload(source))
        state <- admittedStateC(source, value)
        _ <- _relation_c
        actual <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, source.reference,
          source.from, source.from.revision, F.history(source.from, "phase.Repair", causation),
          TypedValue(codec.typeIdentity, value)),
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
        _ <- successorC(source.from, actual.receipt.next)
        _ <- _check(actual.receipt.payload.value == value, "fresh admission changes its exact generated payload")
      } yield Saved(actual.receipt.next, value, state)
    }
  } yield saved

  /** Structural context validation only; native history and expected issued
    * Request equality remain the authority for the actual admission reference.
    */
  private[admission] def terminalFailureValid(value: V.Payload): Boolean =
    repairContextValid(value) && value.outcome.contains("failed") &&
      value.stopped.isEmpty && value.admissionFailures.isEmpty && value.uncertainties.isEmpty &&
      value.newAttempts.isEmpty && value.dependency.isEmpty && value.assessment.isEmpty

  /** Later ordinary check failures in the same pre-review chain retain their
    * own real payload and normal budget; the original admission is provenance.
    */
  private[admission] def repairContextValid(value: V.Payload): Boolean =
    _roundtrip_validation(value) && value.outcome.exists(Set("failed", "repair-required", "repair-limit-reached")) &&
      value.step.startsWith("phase:") && V.repairable(value) &&
      value.receipts.forall(receipt => receipt.processObservation.forall(process =>
        !process.stdout.truncated && !process.stderr.truncated &&
        WorkflowSbtCapture.text(process.stdout).textStatus == "complete" &&
        WorkflowSbtCapture.text(process.stderr).textStatus == "complete"))

  private def _coverage_c(plan: O.Plan, required: Vector[V.Check], value: V.Payload): Consequence[Unit] = {
    val declared = GoalPhaseNativeMetadata.declaredIdentities(value.selected)
    val full = declared == plan.phaseChecks
    val prefix = declared.nonEmpty && declared == plan.phaseChecks.take(declared.size) &&
      value.selected.forall(check => check.metadata.isEmpty && check.hostIdentity.isEmpty && check.operation.isEmpty) &&
      required.take(value.selected.size) == value.selected
    if (full) GoalPhaseNativeMetadata.validateRetainedC(required, value.selected, value.metadata)
    else _check(prefix && required.map(_.identity) == plan.phaseChecks,
      "failed checks do not cover the selected Phase checks or their ordinary fail-fast prefix")
  }

  private def _directive(value: V.Payload): O.RepairDirective = {
    val failed = value.selected(value.receipts.size - 1)
    val exit = value.receipts.last.exitCode.get
    O.RepairDirective("Correct first whole-Phase Release check " + failed.identity + " (" + failed.kind.wire +
      ") after actual exit " + exit,
      Vector("Native check " + failed.identity + " (" + failed.kind.wire + ") failed with actual exit " + exit +
        " within selected Release " + value.step + "; use the original actual diagnostics before fresh checks and independent Phase review."))
  }
  private def _relation_c: Consequence[Unit] = _check(domain.statemachine.GoalPhaseRunLifecycle.permits(
    "Decision", "initial-phase-check-repair-admitted", "Repair"), "initial Phase check repair edge is absent from CML")
  private def _payload_valid(value: Payload): Boolean = value != null && _revision(value.sourceRevision) &&
    value.plan != null && _text(value.plan.identity) && _text(value.plan.revision) &&
    _revision(value.candidateSourceRevision) && _context(value.candidateReference) &&
    value.candidate != null && Vector(value.candidate.head, value.candidate.tree).forall(_oid) &&
    _revision(value.validationSourceRevision) && _context(value.validationReference) &&
    BigInt(value.candidateSourceRevision) < BigInt(value.validationSourceRevision) &&
    BigInt(value.validationSourceRevision) < BigInt(value.sourceRevision) && value.policy == R.version &&
    value.selectedLimit >= 1 && value.selectedLimit <= 64 && value.effectiveLimit == math.min(value.selectedLimit, R.ceiling) &&
    value.generation > 0 && value.generation < Long.MaxValue && value.automaticCount == 0 && value.ordinaryCount == 0 &&
    value.directive != null && _text(value.directive.reason) && value.directive.findings != null &&
    value.directive.findings.nonEmpty && value.directive.findings.size <= 128 && value.directive.findings.forall(_text)
  private def _candidate_valid(value: GoalPhaseNativeCandidate.Payload): Boolean = value != null &&
    _revision(value.sourceRevision) && _text(value.step) && value.selectedPaths != null &&
    value.selectedPaths.distinct == value.selectedPaths && value.selectedPaths.forall(_text) &&
    value.stagedPaths != null && value.stagedPaths == value.selectedPaths && value.changedPaths != null &&
    value.changedPaths.nonEmpty && value.changedPaths.forall(_text) && value.diff != null && value.diff.exists(_diff_text) &&
    value.candidate != null && value.candidate.exists(c => c != null && Vector(c.head, c.tree).forall(_oid))
  private def _roundtrip_candidate(value: GoalPhaseNativeCandidate.Payload): Boolean =
    value != null && scala.util.Try(GoalPhaseNativeCandidate.codec.decode(GoalPhaseNativeCandidate.codec.encode(value)))
      .toOption.contains(Right(value))
  private def _roundtrip_validation(value: V.Payload): Boolean =
    value != null && scala.util.Try(V.codec.decode(V.codec.encode(value))).toOption.contains(Right(value))
  private def _checks_reference(value: V.Saved): String = value.record.identity.value + ":ordered-validation:" +
    value.payload.sourceRevision + ":validation"
  private def _context(value: ContextReference): Boolean = value != null && _text(value.identity) && _revision(value.revision)
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _oid(value: String): Boolean = value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def _diff_text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    UTF_8.newEncoder().canEncode(value)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    UTF_8.newEncoder().canEncode(value) && value.getBytes(UTF_8).length <= 16384
  private def _check(valid: Boolean, reason: String): Consequence[Unit] =
    if (valid) Consequence.unit else F.invalid("initial Phase check repair: " + reason)
}
