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

/** Append-only ordinary repair for a genuinely ended selected Step TestOnly
  * whose complete native report was unverified/InvalidReport. Historical validation
  * remains failed and unverified. No old process is replayed or reclassified.
  * @since   Oct. 26, 2026
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseStepTestReportRepair {
  val schema = "sm-workflow.step-test-report-repair.v1"
  val causation = "ordered-step-test-report-repair:admitted"
  val correctionContract = "sm-workflow.metadata-selected-single-sbt-run.v1"
  private val _namespace = ":ordered-step-test-report-repair:"

  def validReference(value: ContextReference): Boolean = value != null && value.revision == "1" &&
    _text(value.identity) && !value.identity.exists(_.isControl) && {
      val parts = value.identity.split(java.util.regex.Pattern.quote(_namespace), -1)
      parts.length == 2 && parts(0).nonEmpty && _revision(parts(1))
    }

  private[admission] final class Source private[GoalPhaseStepTestReportRepair](
    val from: InstanceRecord, val controller: O.State,
    val candidate: GoalPhaseNativeCandidate.Saved, val validation: V.Saved,
    val directive: O.RepairDirective, val nativeSource: Json) {
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
        !controller.decisionSource.contains(O.Stage.Validation) || controller.documentDecision ||
        !controller.repairControl.exists(_.policy == R.version) || controller.pendingRepair.nonEmpty ||
        controller.closureIntent.nonEmpty || controller.reviewEvidence.nonEmpty ||
        controller.phaseReviewedCandidate.nonEmpty || controller.releaseCommit.nonEmpty ||
        controller.repositoryReceipt.nonEmpty || controller.cursor >= controller.plan.steps.size ||
        controller.completed.map(_.step) != controller.plan.steps.take(controller.cursor).map(_.identity) ||
        !controller.completed.forall(O.validStepAcceptance) || controller.generation == Long.MaxValue ||
        controller.repairs >= controller.effectiveRepairLimit)
      Consequence.success(Option.empty[Source])
    else if (!validation.exists(v => reportRepairFailureValid(v.payload))) Consequence.success(Option.empty[Source])
    else for {
      _ <- from.validateC
      _ <- _check(from.lifecycle == Lifecycle.Active && from.suspension.isEmpty &&
        from.currentProgression.contains(ProgressionReference("phase.Decision")) &&
        candidate.nonEmpty && validation.nonEmpty && controller.candidate.nonEmpty,
        "source has no exact unsuspended native Step Decision candidate and validation")
      actualcandidate = candidate.get
      actualvalidation = validation.get
      _ <- _check(_candidate_valid(actualcandidate.payload) && _roundtrip_candidate(actualcandidate.payload) &&
        _revision(actualvalidation.payload.sourceRevision) && _roundtrip_validation(actualvalidation.payload),
        "source has no complete codec-proven candidate and validation")
      candidateprefix <- GoalPhaseNativeHistory.prefixC(from, actualcandidate.record.revision)
      validationprefix <- GoalPhaseNativeHistory.prefixC(from, actualvalidation.record.revision)
      candidatesource <- GoalPhaseNativeHistory.prefixC(from, InstanceRevision(actualcandidate.payload.sourceRevision.toLong))
      validationsource <- GoalPhaseNativeHistory.prefixC(from, InstanceRevision(actualvalidation.payload.sourceRevision.toLong))
      _ <- _check(candidateprefix == actualcandidate.record && validationprefix == actualvalidation.record &&
        validationprefix == from && candidatesource.currentProgression.contains(ProgressionReference("phase.InspectCandidate")) &&
        candidateprefix.currentProgression.contains(ProgressionReference("phase.Validation")) &&
        validationsource.currentProgression.contains(ProgressionReference("phase.Validation")) &&
        candidatesource.revision.value < candidateprefix.revision.value &&
        candidateprefix.revision.value <= validationsource.revision.value &&
        validationsource.revision.value < validationprefix.revision.value,
        "candidate or validation is not the exact current same-Session historical prefix")
      _ <- _check(actualcandidate.payload.step == controller.plan.steps(controller.cursor).identity &&
        actualvalidation.payload.step == actualcandidate.payload.step &&
        actualcandidate.payload.candidate == controller.candidate &&
        actualcandidate.payload.candidate.contains(actualvalidation.payload.candidate) &&
        controller.decision.contains("Selected native checks failed: " + _checks_reference(actualvalidation)),
        "Decision changes its original candidate, Step or failed receipt identity")
      _ <- _dependency_source_c(from, controller, actualcandidate.payload, actualvalidation.payload)
      _ <- _coverage_c(controller.plan.steps(controller.cursor), requiredchecks, actualvalidation.payload)
      directive = _directive(actualvalidation.payload)
      _ <- controller.continueStepTestReportRepairC(_checks_reference(actualvalidation), directive)
      encoded <- WorkflowInstanceRecordJsonV1.encodeC(from)
      native <- io.circe.parser.parse(encoded).fold(e => F.invalid(e.getMessage), Consequence.success)
    } yield Some(new Source(from, controller, actualcandidate, actualvalidation, directive, native))
  } yield selected

  final case class FrozenDependencyBranch(contract: String, binding: ContextReference,
    captureSourceRevision: String, selected: Vector[V.Check])
  object FrozenDependencyBranch {
    given Encoder[FrozenDependencyBranch] = deriveEncoder
    private val _decoder: Decoder[FrozenDependencyBranch] = deriveDecoder
    given Decoder[FrozenDependencyBranch] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (value.contract == "primary-invalid-report-before-frozen-dependency.v1" &&
          value.binding != null && _text(value.binding.identity) && _revision(value.binding.revision) &&
          _revision(value.captureSourceRevision) && value.selected != null && value.selected.nonEmpty &&
          cursor.value == value.asJson) Right(value)
      else Left(DecodingFailure("Frozen dependency branch requires its exact closed contract", cursor.history))))
  }

  final case class Payload(sourceRevision: String, plan: ContextReference,
    candidateSourceRevision: String, candidateReference: ContextReference, candidate: O.Snapshot,
    validationSourceRevision: String, validationReference: ContextReference,
    policy: String, selectedLimit: Int, effectiveLimit: Int,
    generation: Long, automaticCount: Int, ordinaryCount: Int, directive: O.RepairDirective, step: String, correctionContract: String,
    nativeSource: Json, originalCandidate: Json, originalValidation: Json,
    frozenDependencyBranch: Option[FrozenDependencyBranch] = None)
  object Payload {
    given Encoder[Payload] = deriveEncoder[Payload].mapJson(_.mapObject(_.filter {
      case ("frozenDependencyBranch", value) => !value.isNull
      case _ => true
    }))
    private val _decoder: Decoder[Payload] = deriveDecoder
    given Decoder[Payload] = Decoder.instance { cursor =>
      _decoder.tryDecode(cursor).flatMap { value =>
        if (_payload_valid(value) && cursor.value == value.asJson) Right(value)
        else Left(DecodingFailure("Step test report repair requires exact closed non-null payload fields", cursor.history))
      }
    }
  }
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseStepTestReportRepairReceipt"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("Step test report repair is absent")
      _ <- Either.cond(wire.asObject.exists(_.keys.toSet == Set("schemaVersion", "payload")), (),
        "Step test report repair envelope fields differ")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(version == schema, (), "Step test report repair schema differs")
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
    source.controller.generation, source.controller.repairs, source.controller.ordinaryRepairs, source.directive, source.validation.payload.step, correctionContract,
    source.nativeSource, GoalPhaseNativeCandidate.codec.encode(source.candidate.payload), V.codec.encode(source.validation.payload),
    _frozen_branch(source.validation.payload))

  private[admission] def admittedStateC(source: Source, value: Payload): Consequence[O.State] = for {
    _ <- _check(source != null && _payload_valid(value) && value == payload(source),
      "admission changes its actual failed source, policy, counters or directive")
    state <- source.controller.continueStepTestReportRepairC(value.validationReference.identity, value.directive)
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

  /** Native typed facts only. This does not parse console text, verify tests,
    * grant execution, or make absence of a claim into nonexecution evidence.
    */
  private[admission] def terminalFailureValid(value: V.Payload): Boolean =
    _terminated_report_valid(value) && value.dependency.isEmpty && value.selected.forall(_.dependency.isEmpty)

  /** Distinct retained-source branch only: the failed primary report precedes
    * every selected dependency check. A capture is historical context, never
    * evidence that dependency work passed or that external effects did not run.
    */
  private[admission] def frozenDependencyFailureValid(value: V.Payload): Boolean =
    _terminated_report_valid(value) && value.dependency.exists { capture =>
      val primary = value.selected.takeWhile(_.dependency.isEmpty)
      val dependent = capture.admission.binding.checks.map(_.copy(dependency = Some(capture.admission.binding.identity)))
      GoalPhaseDependencyValidation.captureValid(capture) && primary.nonEmpty &&
        value.receipts.size <= primary.size && value.selected == primary ++ dependent
    }

  private[admission] def reportRepairFailureValid(value: V.Payload): Boolean =
    terminalFailureValid(value) || frozenDependencyFailureValid(value)

  private def _terminated_report_valid(value: V.Payload): Boolean =
    _roundtrip_validation(value) && value.outcome.contains("failed") &&
      value.stopped.isEmpty && value.admissionFailures.isEmpty && value.uncertainties.isEmpty &&
      value.newAttempts.isEmpty && value.dependencySource.isEmpty &&
      value.sourceConsumption.isEmpty && value.dependencyExecutions.isEmpty && value.assessment.isEmpty && value.receipts.nonEmpty &&
      value.receipts.size <= value.selected.size &&
      V.successful(value.receipts.dropRight(1), value.selected) &&
      value.receipts.zip(value.selected).forall { (receipt, check) =>
        receipt.identity == check.identity && receipt.sbt.exists(facts => facts.kind == check.kind && facts.valid &&
          receipt.processObservation.exists(process => WorkflowSbtCapture.valid(process, facts) &&
            WorkflowSbtCapture.text(process.stdout).textStatus == "complete" &&
            WorkflowSbtCapture.text(process.stderr).textStatus == "complete"))
      } && {
        val last = value.receipts.last
        val check = value.selected(value.receipts.size - 1)
        check.kind == WorkflowSbtValidation.Kind.TestOnly && last.termination == "exited" && last.exitCode.contains(0) &&
          last.sbt.contains(WorkflowSbtValidation.Facts(WorkflowSbtValidation.Kind.TestOnly,
            WorkflowSbtValidation.Outcome.Unverified, None, Some("InvalidReport")))
      }

  private def _dependency_source_c(from: InstanceRecord, controller: O.State,
    candidate: GoalPhaseNativeCandidate.Payload, validation: V.Payload): Consequence[Unit] = for {
    _ <- _check(candidate.dependency == validation.dependency && candidate.dependencySourceCapture.isEmpty &&
      candidate.dependencySourceDirection.isEmpty && candidate.dependencySourceGuard.isEmpty && candidate.dependencySourceStages.isEmpty &&
      validation.dependencySource.isEmpty && validation.sourceConsumption.isEmpty,
      "failed report changes its original frozen dependency capture or source-consumption route")
    _ <- validation.dependency.fold(_check(candidate.dependencyAdmission.isEmpty,
      "failed report has an uncaptured dependency admission")) { capture => for {
      _ <- _check(frozenDependencyFailureValid(validation) && candidate.dependencyAdmission.contains(capture.admission) &&
        capture.session == from.identity.value && capture.plan == ContextReference(controller.plan.identity, controller.plan.revision) &&
        capture.step == candidate.step && capture.primaryBase == controller.base &&
        capture.admission.binding.requester.repository == controller.plan.repository && _revision(capture.sourceRevision) &&
        capture.generation <= controller.generation && BigInt(capture.sourceRevision) <= BigInt(candidate.sourceRevision),
        "frozen dependency crosses the original Session, Plan, Step, base, generation or candidate source")
      prefix <- GoalPhaseNativeHistory.prefixC(from, InstanceRevision(capture.sourceRevision.toLong))
      _ <- _check(prefix.currentProgression.contains(ProgressionReference("phase.InspectCandidate")),
        "frozen dependency has no actual original native inspection source")
    } yield () }
  } yield ()

  private def _coverage_c(step: O.Step, required: Vector[V.Check], value: V.Payload): Consequence[Unit] = for {
    _ <- _check(GoalPhaseNativeMetadata.declaredIdentities(value.selected.filter(_.dependency.isEmpty)) == step.checks &&
      required.map(_.identity) == step.checks && required.forall(_.dependency.isEmpty),
      "validation does not cover the unchanged selected Step check metadata")
    combined <- GoalPhaseNativeDependencyValidation.checksC(required, value.dependency)
    _ <- GoalPhaseNativeMetadata.validateRetainedC(combined, value.selected, value.metadata)
  } yield ()

  /** The old candidate is failure context, not a new candidate admission.
    * Allow owned correction bytes before issuing this distinct repair, while
    * retaining the actual root, branch, accepted tip and nonconflicting scope.
    * Fresh candidate/index/check preconditions remain mandatory after Submit.
    */
  private[admission] def beforeIssueC(d: F.Declaration, core: ActionCall.Core, controller: O.State): Consequence[Unit] = for {
    _ <- _check(controller.stage == O.Stage.Repair && controller.cursor < controller.plan.steps.size,
      "repair issuance has no selected Step")
    root <- RepositorySyncManagedGitObservation.rootC(core)
    actual <- RepositorySyncManagedGitObservation.observeC(core)
    parent = controller.tipSnapshot
    owned = controller.plan.steps(controller.cursor).ownedPaths
    _ <- _check(root == d.target.root.toString && actual.branch == d.target.localBranch &&
      actual.head == parent.head && actual.tree == parent.tree &&
      GoalPhaseNativeCandidateBoundary.scopedDirty(actual.dirty, owned, false),
      "repair issuance changes its native root, branch, accepted tip or nonconflicting ownership")
    oid <- org.goldenport.cncf.processexecution.GitObjectId.parseC(parent.head)
    pending <- WorkflowManagedProcessExecution.executeGitC(core,
      org.goldenport.cncf.processexecution.GitOperation.PendingPaths(oid)).flatMap(_.valueC)
    _ <- _check(GoalPhaseNativeCandidateBoundary.pathsOwned(pending.map(_.value), owned),
      "repair issuance includes an unrelated index delta")
    after <- RepositorySyncManagedGitObservation.observeC(core)
    afterroot <- RepositorySyncManagedGitObservation.rootC(core)
    _ <- _check(afterroot == root && after == actual, "repair source drifted during read-only observation")
  } yield ()

  private def _directive(value: V.Payload): O.RepairDirective = {
    val failed = value.selected(value.receipts.size - 1)
    O.RepairDirective("Correct selected Step TestOnly report for " + failed.identity + " after terminated unverified InvalidReport",
      Vector("Retain the original failed/unverified receipt for " + value.step + "; repair the selected test orchestration " +
        "within the same adopted Step scope. Submit fresh semantic work before new candidate checks and applicable independent review. " +
        "The old exit zero does not verify selected tests. Runtime contract: " + correctionContract))
  }
  private def _relation_c: Consequence[Unit] = _check(domain.statemachine.GoalPhaseRunLifecycle.permits(
    "Decision", "step-test-report-repair-admitted", "Repair"), "Step test report repair edge is absent from CML")
  private def _payload_valid(value: Payload): Boolean = value != null && _revision(value.sourceRevision) &&
    value.plan != null && _text(value.plan.identity) && _text(value.plan.revision) &&
    _revision(value.candidateSourceRevision) && _context(value.candidateReference) &&
    value.candidate != null && Vector(value.candidate.head, value.candidate.tree).forall(_oid) &&
    _revision(value.validationSourceRevision) && _context(value.validationReference) &&
    BigInt(value.candidateSourceRevision) < BigInt(value.validationSourceRevision) &&
    BigInt(value.validationSourceRevision) < BigInt(value.sourceRevision) && value.policy == R.version &&
    value.selectedLimit >= 1 && value.selectedLimit <= 64 && value.effectiveLimit == math.min(value.selectedLimit, R.ceiling) &&
    value.generation > 0 && value.generation < Long.MaxValue && value.automaticCount >= 0 && value.automaticCount < value.effectiveLimit && value.ordinaryCount >= 0 &&
    value.ordinaryCount < Int.MaxValue && _text(value.step) && value.correctionContract == correctionContract &&
    value.nativeSource != null && value.nativeSource.isObject && value.originalCandidate != null &&
    GoalPhaseNativeCandidate.codec.decode(value.originalCandidate).isRight && value.originalValidation != null &&
    value.frozenDependencyBranch != null && V.codec.decode(value.originalValidation).exists(validation =>
      if (value.frozenDependencyBranch.isEmpty) terminalFailureValid(validation)
      else frozenDependencyFailureValid(validation) && value.frozenDependencyBranch == _frozen_branch(validation) &&
        GoalPhaseNativeCandidate.codec.decode(value.originalCandidate).exists(candidate =>
          candidate.dependency == validation.dependency && candidate.sourceRevision == value.candidateSourceRevision &&
          candidate.step == value.step && candidate.candidate.contains(value.candidate) &&
          validation.sourceRevision == value.validationSourceRevision && validation.candidate == value.candidate &&
          validation.dependency.exists(capture => candidate.dependencyAdmission.contains(capture.admission) &&
            capture.plan == value.plan && capture.step == value.step && capture.generation <= value.generation &&
            _revision(capture.sourceRevision) && BigInt(capture.sourceRevision) <= BigInt(value.candidateSourceRevision)))) &&
    value.directive != null && _text(value.directive.reason) && value.directive.findings != null &&
    value.directive.findings.nonEmpty && value.directive.findings.size <= 128 && value.directive.findings.forall(_text)
  private def _candidate_valid(value: GoalPhaseNativeCandidate.Payload): Boolean = value != null &&
    _revision(value.sourceRevision) && _text(value.step) && value.selectedPaths != null &&
    value.selectedPaths.distinct == value.selectedPaths && value.selectedPaths.forall(_text) &&
    value.stagedPaths != null && value.stagedPaths == value.selectedPaths && value.changedPaths != null &&
    value.changedPaths.nonEmpty && value.changedPaths.forall(_text) && value.diff != null && value.diff.exists(_diff_text) &&
    value.candidate != null && value.candidate.exists(c => c != null && Vector(c.head, c.tree).forall(_oid))
  private def _frozen_branch(value: V.Payload): Option[FrozenDependencyBranch] = value.dependency.map { capture =>
    FrozenDependencyBranch("primary-invalid-report-before-frozen-dependency.v1",
      ContextReference(capture.admission.binding.identity, capture.admission.binding.revision.toString),
      capture.sourceRevision, value.selected)
  }
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
    if (valid) Consequence.unit else F.invalid("Step test report repair: " + reason)
}
