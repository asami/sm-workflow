package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.goldenport.convert.ValueReader
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhasePlanRevision as R
import GoalPhaseNativeValidation as N
import GoalPhaseInterruptedValidationRepair as I

/** Separate Human direction admitting one corrective Step after a real terminal
  * Failed or confirmed timeout whole-Phase check. Both sources retain their own
  * wire and proof. Description and adoption traverse retained history only.
  * @since   Oct. 23, 2026
  * @version Oct. 24, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseFailedPhaseRepairAdoption {
  val purpose = "goal-phase-failed-phase-repair-adoption"
  val describeOperation = "describeGoalPhaseFailedPhaseRepair"
  val adoptionOperation = "adoptGoalPhaseFailedPhaseRepair"
  val choices = Vector("AdoptRepairPlan", "Defer")
  val schema = "sm-workflow.goal-phase-failed-phase-repair-adoption.v1"
  val message = "The whole-Phase check has an actual terminal Failed or confirmed TimedOut/incomplete result. An independently admitted actual Human direction may append " +
    "exactly one corrective Step under the current host ownership, check and fixed closure policies. Accepted Steps, original base, " +
    "commits, reviews, failed validation and repair counts remain. Adoption saves direction only; a later ordinary " +
    "advanceGoalPhase issues the new Step. Defer writes nothing. This description is not a Human answer."

  final case class Target(recovery: GoalPhaseValidationRecovery.RecoveryTarget, plan: O.Plan,
    completed: Vector[O.ClosedStep], repairProgress: GoalPhaseRepairProgress.Snapshot, generation: Long,
    candidatePaths: Vector[String], closurePolicy: GoalPhaseNativeClosure.Policy,
    captured: R.Captured, correctiveChecks: Vector[N.Check])
  object Target {
    given Encoder[Target] = deriveEncoder
    private val _raw: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (value.asJson == cursor.value && _valid_target_c(value).isSuccess) Right(value)
      else Left(io.circe.DecodingFailure("Failed Phase repair target requires exactly its complete non-null bound contract", cursor.history))))
  }
  final case class Direction(target: Target, reference: ContextReference, participant: String, rationale: String)
  final case class InterruptedDirection(target: I.Target, reference: ContextReference, participant: String, rationale: String)
  final case class Source(session: F.Session, from: InstanceRecord, controller: O.State,
    candidate: GoalPhaseNativeCandidate.Saved, failed: N.Saved, documentBacked: Boolean)
  final case class Resumed(record: InstanceRecord, controller: O.State)
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else F.invalid("Failed Phase repair adoption requires an independent Human evidence provider")
  }

  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def targetRecord(value: I.Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Failed Phase repair target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def targetC(value: Record): Consequence[Target] = if (value == null) F.invalid("Failed Phase repair target is absent")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(decodeTarget).fold(F.invalid, Consequence.success))

  /** Public Record remains unchanged; the closed timeout source selects v2.
    * Failed v1 decoding is never used as an eligibility fallback.
    */
  def decodeBoundTarget(value: Json): Either[String, Either[Target, I.Target]] =
    Option(value).toRight("Phase repair target is absent").flatMap { wire =>
      if (wire.hcursor.downField("source").focus.nonEmpty)
        wire.as[I.Target].left.map(_.message).map(Right(_))
      else decodeTarget(wire).map(Left(_))
    }
  def boundTargetC(value: Record): Consequence[Either[Target, I.Target]] =
    if (value == null) F.invalid("Phase repair target is absent")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(decodeBoundTarget).fold(F.invalid, Consequence.success))

  def bindingC(component: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] = for {
    _ <- _valid_target_c(target)
    _ <- if (_exact(component) && choices.contains(choice) && _text(rationale) && _exact(participant) &&
        target.recovery.knownFailedPhase.exists(_.participant == participant) && reference != null &&
        _exact(reference.identity) && _exact(reference.revision)) Consequence.unit
      else F.invalid("Failed Phase repair direction requires its actual choice, rationale, participant and reference")
  } yield Json.obj("componentInstance" -> Json.fromString(component), "failedPhaseRepairAdoption" -> Json.obj(
    "target" -> target.asJson, "answer" -> Json.obj("choice" -> Json.fromString(choice),
      "rationale" -> Json.fromString(rationale), "participant" -> Json.fromString(participant),
      "reference" -> reference.asJson)))

  def bindingC(component: String, target: I.Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] = for {
    _ <- I.validateC(target)
    _ <- if (_exact(component) && choices.contains(choice) && _text(rationale) && _exact(participant) &&
        target.source.participant == participant && reference != null && _exact(reference.identity) &&
        _exact(reference.revision)) Consequence.unit
      else F.invalid("Timeout repair requires its actual Human choice, rationale, participant and reference")
  } yield Json.obj("componentInstance" -> Json.fromString(component), "failedPhaseRepairAdoption" -> Json.obj(
    "target" -> target.asJson, "answer" -> Json.obj("choice" -> Json.fromString(choice),
      "rationale" -> Json.fromString(rationale), "participant" -> Json.fromString(participant), "reference" -> reference.asJson)))

  /** Exact selected scope: extensions, omissions and explicit nulls refuse. */
  def selectBinding(value: Json): Either[String, Json] = for {
    supplied <- Option(value).toRight("Failed Phase repair binding is absent")
    component <- supplied.hcursor.get[String]("componentInstance").left.map(_.message)
    body <- supplied.hcursor.get[Json]("failedPhaseRepairAdoption").left.map(_.message)
    targetwire <- body.hcursor.get[Json]("target").left.map(_.message)
    target <- decodeBoundTarget(targetwire)
    answer = body.hcursor.downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    selected <- target.fold(bindingC(component, _, choice, rationale, participant, reference),
      bindingC(component, _, choice, rationale, participant, reference))
      .map[Either[String, Json]](Right(_)).fold(error => Left(error.toString), identity)
    _ <- Either.cond(selected == supplied, (), "Failed Phase repair binding fields differ")
  } yield selected

  val codec: WorkflowResultJsonV1.PayloadCodec[Direction] = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "GoalPhaseFailedPhaseRepairAdoptionDirection"
    def encode(value: Direction): Json = Json.obj("schemaVersion" -> Json.fromString(schema), "payload" -> Json.obj(
      "target" -> value.target.asJson, "reference" -> value.reference.asJson,
      "participant" -> Json.fromString(value.participant), "rationale" -> Json.fromString(value.rationale)))
    def decode(value: Json): Either[String, Direction] = for {
      wire <- Option(value).toRight("Failed Phase repair direction is absent")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      payload <- wire.hcursor.get[Json]("payload").left.map(_.message)
      targetwire <- payload.hcursor.get[Json]("target").left.map(_.message)
      target <- decodeTarget(targetwire)
      reference <- payload.hcursor.get[ContextReference]("reference").left.map(_.message)
      participant <- payload.hcursor.get[String]("participant").left.map(_.message)
      rationale <- payload.hcursor.get[String]("rationale").left.map(_.message)
      direction = Direction(target, reference, participant, rationale)
      _ <- Either.cond(version == schema && encode(direction) == wire && reference != null &&
        _exact(reference.identity) && _exact(reference.revision) && _text(rationale) &&
        target.recovery.knownFailedPhase.exists(_.participant == participant), (), "Failed Phase repair direction fields differ")
    } yield direction
  }

  val interruptedCodec: WorkflowResultJsonV1.PayloadCodec[InterruptedDirection] =
    new WorkflowResultJsonV1.PayloadCodec[InterruptedDirection] {
      val typeIdentity = codec.typeIdentity
      def encode(value: InterruptedDirection): Json = Json.obj("schemaVersion" -> Json.fromString(I.schema),
        "payload" -> Json.obj("target" -> value.target.asJson, "reference" -> value.reference.asJson,
          "participant" -> Json.fromString(value.participant), "rationale" -> Json.fromString(value.rationale)))
      def decode(value: Json): Either[String, InterruptedDirection] = for {
        wire <- Option(value).toRight("Timeout repair direction is absent")
        version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
        _ <- Either.cond(version == I.schema, (), "Timeout repair direction schema differs")
        payload <- wire.hcursor.get[Json]("payload").left.map(_.message)
        target <- payload.hcursor.get[I.Target]("target").left.map(_.message)
        reference <- payload.hcursor.get[ContextReference]("reference").left.map(_.message)
        participant <- payload.hcursor.get[String]("participant").left.map(_.message)
        rationale <- payload.hcursor.get[String]("rationale").left.map(_.message)
        direction = InterruptedDirection(target, reference, participant, rationale)
        _ <- Either.cond(encode(direction) == wire && reference != null && _exact(reference.identity) &&
          _exact(reference.revision) && _text(rationale) && target.source.participant == participant,
          (), "Timeout repair direction fields differ")
      } yield direction
    }

  def describeC(source: Source, d: F.Declaration, core: ActionCall.Core, proposal: Record):
      Consequence[V.GoalPhaseFailedPhaseRepairDescriptionResult] = N.terminalOutcome(source.failed.payload) match {
    case Some(N.TerminalOutcome.TerminatedTimeout) => for {
    actual <- _current_interrupted_target_c(source, d, core, proposal)
  } yield V.GoalPhaseFailedPhaseRepairDescriptionResult(source.from.identity.value, "adoption-required", message,
    core.component.get.core.instanceId.canonicalKey, targetRecord(actual), choices, adoptionOperation)
    case Some(N.TerminalOutcome.Failed) => for {
    actual <- _current_target_c(source, d, core, proposal)
  } yield V.GoalPhaseFailedPhaseRepairDescriptionResult(source.from.identity.value, "adoption-required", message,
    core.component.get.core.instanceId.canonicalKey, targetRecord(actual), choices, adoptionOperation)
    case None => F.invalid("Phase repair requires an explicit terminal Failed or confirmed timeout source")
  }

  def adoptC(source: Source, d: F.Declaration, core: ActionCall.Core, input: V.GoalPhaseFailedPhaseRepairAdoptionInput):
      Consequence[V.GoalPhaseFailedPhaseRepairAdoptionResult] = N.terminalOutcome(source.failed.payload) match {
    case Some(N.TerminalOutcome.TerminatedTimeout) => _adopt_interrupted_c(source, d, core, input)
    case Some(N.TerminalOutcome.Failed) => _adopt_failed_c(source, d, core, input)
    case None => F.invalid("Phase repair requires an explicit terminal Failed or confirmed timeout source")
  }

  private def _adopt_failed_c(source: Source, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhaseFailedPhaseRepairAdoptionInput): Consequence[V.GoalPhaseFailedPhaseRepairAdoptionResult] = for {
    supplied <- targetC(input.target)
    actual <- _current_target_c(source, d, core, input.proposal)
    _ <- if (input.sessionId == actual.recovery.sessionId && supplied == actual) Consequence.unit
      else F.invalid("Failed Phase repair adoption crosses its current target, proposal or host policy")
    installation <- d.failedPhaseRepairAdoption.map(Consequence.success).getOrElse(
      Consequence.securityPermissionDenied("Failed Phase repair adoption has no independent Human evidence provider"))
    _ <- installation.validateC
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, actual, input.choice, input.rationale,
      source.session.participantIdentity, reference)
    _ <- installation.evidence.admitC(core, reference, binding)
    _ <- if (input.choice == "Defer") Consequence.unit else _append_c(source, d, core,
      Direction(actual, reference, source.session.participantIdentity, input.rationale))
  } yield V.GoalPhaseFailedPhaseRepairAdoptionResult(input.sessionId, true,
    if (input.choice == "Defer") "deferred" else "failed-phase-repair-adopted", message)

  private def _current_target_c(source: Source, d: F.Declaration, core: ActionCall.Core, proposal: Record): Consequence[Target] = for {
    recovery <- GoalPhaseKnownFailedValidationRecovery.currentTargetC(source.session, d, core, source.from,
      source.controller, source.candidate, source.failed)
    captured <- _capture_c(source, d, proposal)
    target <- _target_c(source, d, recovery, captured)
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    existing <- adapter.loadRetainedC(_reference(source.from))(using core.executionContext)
    _ <- if (existing.isEmpty) Consequence.unit else F.invalid("Failed Phase repair direction already exists")
  } yield target

  private def _capture_c(source: Source, d: F.Declaration, raw: Record): Consequence[R.Captured] = for {
    _ <- if (!source.documentBacked) Consequence.unit else F.invalid("Document-backed failed Phase Plan repair is unsupported")
    proposal <- R.fromPublicProposalC(Some(raw)).flatMap(_.map(Consequence.success)
      .getOrElse(F.invalid("Failed Phase repair proposal is absent")))
    plan <- GoalPhaseAcceptanceBoundary.captureC(d.projectAcceptance, proposal.plan)
    captured = R.Captured(proposal, plan)
    _ <- R.validateCapturedC(captured)
    _ <- source.controller.adoptFailedPhaseRepairRevisionC(plan, "description-eligibility")
  } yield captured

  private def _target_c(source: Source, d: F.Declaration, recovery: GoalPhaseValidationRecovery.RecoveryTarget,
    captured: R.Captured): Consequence[Target] = for {
    contract <- _repair_contract_c(source, d, captured)
    (closure, checks) = contract
    target = Target(recovery, source.controller.plan, source.controller.completed,
      GoalPhaseRepairProgress.snapshot(source.controller), source.controller.generation, d.candidatePaths,
      closure, captured, checks)
    _ <- _valid_target_c(target)
  } yield target

  private def _repair_contract_c(source: Source, d: F.Declaration, captured: R.Captured):
    Consequence[(GoalPhaseNativeClosure.Policy, Vector[N.Check])] = for {
    _ <- if (!source.documentBacked && d.candidatePaths.nonEmpty &&
        d.candidatePaths.forall(path => RepositorySyncNativeConflictInput.relative(path) &&
          !path.exists(c => c == '*' || c == '?' || c == '[' || c == ']')) &&
        captured.plan.steps.last.ownedPaths.forall(d.candidatePaths.contains)) Consequence.unit
      else F.invalid("Every corrective ownership entry requires an exact current host candidatePaths entry and fixed native program; directory coverage alone is insufficient")
    closure <- d.closureInstallation.map(Consequence.success).getOrElse(
      F.invalid("Failed Phase repair requires the current native Step closure installation"))
    _ <- closure.validateC
    _ <- if (closure.policy.programs.exists(_.step == captured.plan.steps.last.identity)) Consequence.unit
      else F.invalid("Corrective Step has no current host-fixed commit program")
    checks <- N.selectedChecksC(d, captured.plan, Some(captured.plan.steps.last.identity))
  } yield (closure.policy, checks)

  private def _interrupted_target_c(source: Source, d: F.Declaration, proof: I.Source,
    captured: R.Captured): Consequence[I.Target] = for {
    contract <- _repair_contract_c(source, d, captured)
    (closure, checks) = contract
    target = I.Target(proof, source.controller.plan, source.controller.completed,
      GoalPhaseRepairProgress.snapshot(source.controller), source.controller.generation, d.candidatePaths,
      closure, captured, checks)
    _ <- I.validateC(target)
  } yield target

  private def _current_interrupted_target_c(source: Source, d: F.Declaration, core: ActionCall.Core,
    proposal: Record): Consequence[I.Target] = for {
    proof <- I.targetC(source, d, core)
    _ <- if (source.session.current == source.from) Consequence.unit
      else F.invalid("Timeout repair requires the exact current Decision")
    captured <- _capture_c(source, d, proposal)
    target <- _interrupted_target_c(source, d, proof, captured)
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, interruptedCodec)
    existing <- adapter.loadRetainedC(_reference(source.from))(using core.executionContext)
    _ <- if (existing.isEmpty) Consequence.unit else F.invalid("Phase repair direction already exists")
  } yield target

  private def _adopt_interrupted_c(source: Source, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhaseFailedPhaseRepairAdoptionInput): Consequence[V.GoalPhaseFailedPhaseRepairAdoptionResult] = for {
    supplied <- boundTargetC(input.target)
    actual <- _current_interrupted_target_c(source, d, core, input.proposal)
    _ <- if (input.sessionId == actual.source.sessionId && supplied == Right(actual)) Consequence.unit
      else F.invalid("Timeout repair crosses its current source, proposal or host policy")
    installation <- d.failedPhaseRepairAdoption.map(Consequence.success).getOrElse(
      Consequence.securityPermissionDenied("Timeout repair has no independent Human evidence provider"))
    _ <- installation.validateC
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, actual, input.choice, input.rationale,
      source.session.participantIdentity, reference)
    _ <- installation.evidence.admitC(core, reference, binding)
    _ <- if (input.choice == "Defer") Consequence.unit else _append_interrupted_c(source, d, core,
      InterruptedDirection(actual, reference, source.session.participantIdentity, input.rationale))
  } yield V.GoalPhaseFailedPhaseRepairAdoptionResult(input.sessionId, true,
    if (input.choice == "Defer") "deferred" else "failed-phase-repair-adopted", message)

  private def _append_interrupted_c(source: Source, d: F.Declaration, core: ActionCall.Core,
    direction: InterruptedDirection): Consequence[Unit] = for {
    _ <- interruptedCodec.decode(interruptedCodec.encode(direction)).fold(F.invalid, _ => Consequence.unit)
    state <- source.controller.adoptFailedPhaseRepairRevisionC(direction.target.captured.plan, direction.reference.identity)
    _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits(source.controller.stage.toString,
        "failed-phase-repair-adopted", state.stage.toString)) Consequence.unit
      else F.invalid("Timeout repair differs from the CML lifecycle")
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, interruptedCodec)
    _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(source.from), source.from,
      source.from.revision, F.history(source.from, "phase.Implementation", _cause(source.from)),
      TypedValue(interruptedCodec.typeIdentity, direction)), ConsequenceT.pure[[X] =>>
        org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield ()

  private def _append_c(source: Source, d: F.Declaration, core: ActionCall.Core, direction: Direction): Consequence[Unit] = for {
    _ <- codec.decode(codec.encode(direction)).fold(F.invalid, _ => Consequence.unit)
    state <- source.controller.adoptFailedPhaseRepairRevisionC(direction.target.captured.plan, direction.reference.identity)
    _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits(source.controller.stage.toString,
        "failed-phase-repair-adopted", state.stage.toString)) Consequence.unit
      else F.invalid("Failed Phase repair adoption differs from the CML lifecycle")
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(source.from), source.from,
      source.from.revision, F.history(source.from, "phase.Implementation", _cause(source.from)),
      TypedValue(codec.typeIdentity, direction)), ConsequenceT.pure[[X] =>>
        org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield ()

  /** Retain the complete bound paths and their ordinals when the host appends paths. */
  private[admission] def retainedCandidatePathsC(recorded: Vector[String],
    current: Vector[String]): Consequence[Vector[String]] = for {
    _ <- if (Vector(recorded, current).forall(paths => paths != null && paths.nonEmpty &&
        paths.size <= 256 && paths.distinct == paths && paths.forall(path =>
          RepositorySyncNativeConflictInput.relative(path) &&
            !path.exists(c => c == '*' || c == '?' || c == '[' || c == ']')))) Consequence.unit
      else F.invalid("Retained failed Phase repair requires complete valid recorded and current candidatePaths")
    _ <- if (current.startsWith(recorded)) Consequence.unit
      else F.invalid("Retained failed Phase repair candidatePaths permit only an appended path suffix")
  } yield recorded

  /** Retain the complete bound policy when the current host only appends programs. */
  private[admission] def retainedClosurePolicyC(recorded: GoalPhaseNativeClosure.Policy,
    current: GoalPhaseNativeClosure.Policy): Consequence[GoalPhaseNativeClosure.Policy] = for {
    _ <- if (recorded != null && current != null) Consequence.unit
      else F.invalid("Retained failed Phase repair requires complete recorded and current closure policies")
    _ <- recorded.validateC
    _ <- current.validateC
    _ <- if (current.identity == recorded.identity && current.revision == recorded.revision &&
        current.programs.startsWith(recorded.programs)) Consequence.unit
      else F.invalid("Retained failed Phase repair closure policy permits only an appended program suffix")
  } yield recorded

  /** Reconstruct the original proof and exact bound proposal at its historical
    * Decision; the new record must be its immediate actual native successor.
    */
  def retainedC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Resumed] =
    N.terminalOutcome(source.failed.payload) match {
      case Some(N.TerminalOutcome.TerminatedTimeout) => _retained_interrupted_c(source, d, core)
      case Some(N.TerminalOutcome.Failed) => _retained_failed_c(source, d, core)
      case None => F.invalid("Retained Phase repair has no explicit terminal Failed or confirmed timeout source")
    }

  private def _retained_interrupted_c(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Resumed] = for {
    proof <- I.targetC(source, d, core)
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, interruptedCodec)
    retained <- adapter.loadRetainedC(_reference(source.from))(using core.executionContext).flatMap(_.map(Consequence.success)
      .getOrElse(F.invalid("Actual timeout repair history has no retained v2 direction")))
    direction = retained.receipt.payload.value
    _ <- interruptedCodec.decode(interruptedCodec.encode(direction)).fold(F.invalid, _ => Consequence.unit)
    closure <- d.closureInstallation.map(Consequence.success).getOrElse(
      F.invalid("Timeout repair requires the current native Step closure installation"))
    _ <- closure.validateC
    recordedpolicy <- retainedClosurePolicyC(direction.target.closurePolicy, closure.policy)
    recordedpaths <- retainedCandidatePathsC(direction.target.candidatePaths, d.candidatePaths)
    historical = d.copy(candidatePaths = recordedpaths, closureInstallation = Some(closure.copy(policy = recordedpolicy)))
    captured <- _capture_c(source, d, R.publicProposal(Some(direction.target.captured.proposal)).get)
    actual <- _interrupted_target_c(source, historical, proof, captured)
    _ <- if (actual == direction.target && direction.participant == source.session.participantIdentity) Consequence.unit
      else F.invalid("Retained timeout repair crosses its original source, Plan, participant, proposal or policy")
    saved = retained.receipt.next
    next <- GoalPhaseNativeHistory.prefixC(source.session.current, saved.revision)
    _ <- if (next == saved && saved.identity == source.from.identity && saved.definition == source.from.definition &&
        source.from.revision.value < Long.MaxValue && saved.revision == source.from.revision.next &&
        saved.history.dropRight(1) == source.from.history && saved.causation.value == _cause(source.from) &&
        saved.currentProgression.contains(ProgressionReference("phase.Implementation")) && saved.suspension.isEmpty &&
        saved.lifecycle == source.from.lifecycle) Consequence.unit
      else F.invalid("Timeout repair direction has a crossed, missing or noncontiguous predecessor")
    state <- source.controller.adoptFailedPhaseRepairRevisionC(actual.captured.plan, direction.reference.identity)
  } yield Resumed(next, state)

  private def _retained_failed_c(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Resumed] = for {
    recovery <- GoalPhaseKnownFailedValidationRecovery.targetC(source.session, d, core, source.from,
      source.controller, source.candidate, source.failed)
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- adapter.loadRetainedC(_reference(source.from))(using core.executionContext).flatMap(_.map(Consequence.success)
      .getOrElse(F.invalid("Actual failed Phase repair history has no retained direction")))
    direction = retained.receipt.payload.value
    _ <- codec.decode(codec.encode(direction)).fold(F.invalid, _ => Consequence.unit)
    closure <- d.closureInstallation.map(Consequence.success).getOrElse(
      F.invalid("Failed Phase repair requires the current native Step closure installation"))
    _ <- closure.validateC
    recordedpolicy <- retainedClosurePolicyC(direction.target.closurePolicy, closure.policy)
    recordedpaths <- retainedCandidatePathsC(direction.target.candidatePaths, d.candidatePaths)
    historical = d.copy(candidatePaths = recordedpaths,
      closureInstallation = Some(closure.copy(policy = recordedpolicy)))
    captured <- _capture_c(source, d, R.publicProposal(Some(direction.target.captured.proposal)).get)
    actual <- _target_c(source, historical, recovery, captured)
    _ <- if (actual == direction.target && direction.participant == source.session.participantIdentity) Consequence.unit
      else F.invalid("Retained failed Phase repair crosses its original Plan, failure, participant, proposal or policy")
    saved = retained.receipt.next
    next <- GoalPhaseNativeHistory.prefixC(source.session.current, saved.revision)
    _ <- if (next == saved && saved.identity == source.from.identity && saved.definition == source.from.definition &&
        source.from.revision.value < Long.MaxValue && saved.revision == source.from.revision.next &&
        saved.history.dropRight(1) == source.from.history && saved.causation.value == _cause(source.from) &&
        saved.currentProgression.contains(ProgressionReference("phase.Implementation")) && saved.suspension.isEmpty &&
        saved.lifecycle == source.from.lifecycle) Consequence.unit
      else F.invalid("Failed Phase repair direction has a crossed, missing or noncontiguous predecessor")
    state <- source.controller.adoptFailedPhaseRepairRevisionC(actual.captured.plan, direction.reference.identity)
  } yield Resumed(next, state)

  private def _valid_target_c(value: Target): Consequence[Unit] = for {
    _ <- if (value != null && GoalPhaseValidationRecovery.validTarget(value.recovery) &&
        value.recovery.knownFailedPhase.nonEmpty && value.plan != null && value.completed != null &&
        value.completed.map(_.step) == value.plan.steps.map(_.identity) &&
        value.recovery.planIdentity == value.plan.identity && value.recovery.planRevision == value.plan.revision &&
        value.recovery.step == "phase:" + value.plan.phase && value.generation > 0 && value.generation < Long.MaxValue &&
        value.candidatePaths != null && value.candidatePaths.nonEmpty && value.candidatePaths.distinct == value.candidatePaths &&
        value.candidatePaths.size <= 256 && value.candidatePaths.forall(RepositorySyncNativeConflictInput.relative) &&
        value.closurePolicy != null && value.correctiveChecks != null && value.correctiveChecks.nonEmpty) Consequence.unit
      else F.invalid("Failed Phase repair target is incomplete")
    _ <- O.validatePlanC(value.plan)
    _ <- GoalPhaseAcceptanceBoundary.savedPlanC(value.plan)
    _ <- GoalPhaseRepairProgress.validateC(value.repairProgress)
    _ <- R.validateCapturedC(value.captured)
    _ <- value.closurePolicy.validateC
    _ <- if (value.closurePolicy.programs.exists(_.step == value.captured.plan.steps.last.identity)) Consequence.unit
      else F.invalid("Failed Phase repair target has no fixed corrective Step commit program")
    _ <- if (value.repairProgress.limit == value.plan.repairLimit && value.captured.documents.isEmpty)
      Consequence.unit else F.invalid("Failed Phase repair target changes repair policy or uses documents")
  } yield ()

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseFailedPhaseRepairOperation
      else SmWorkflowComponent.GoalPhaseServiceService.AdoptGoalPhaseFailedPhaseRepairOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") ||
          request.operation != (if (describe) describeOperation else adoptionOperation)) F.invalid("Failed Phase repair Operation identity differs")
      else if (describe) _input_c[V.GoalPhaseFailedPhaseRepairDescriptionInput](request, Set("sessionId", "proposal"))
        .map(value => Action(request, value.sessionId, Some(value), None, port))
      else _input_c[V.GoalPhaseFailedPhaseRepairAdoptionInput](request,
        Set("sessionId", "target", "proposal", "choice", "rationale", "reference")).flatMap { value =>
        if (value == null || !_exact(value.sessionId) || !choices.contains(value.choice) || !_text(value.rationale) ||
            value.reference == null || !_exact(value.reference.identity) || !_exact(value.reference.revision))
          F.invalid("Failed Phase repair input requires its actual Human choice and reference")
        else Consequence.success(Action(request, value.sessionId, None, Some(value), port))
      }
  }
  private def _input_c[A: ValueReader](request: Request, keys: Set[String]): Consequence[A] = for {
    record <- NativeWorkflowOperationInput.recordC(request, "input")
    text <- record.toJsonStringC
    json <- io.circe.parser.parse(text).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
      .fold(F.invalid, Consequence.success)
    _ <- if (json.asObject.exists(fields => fields.keys.toSet == keys && fields.values.forall(value => !value.isNull)))
      Consequence.unit else F.invalid("Failed Phase repair input requires exactly its non-null business fields")
    value <- summon[ValueReader[A]].readC(record)
  } yield value
  private final case class Action(request: Request, sessionId: String,
    description: Option[V.GoalPhaseFailedPhaseRepairDescriptionInput], adoption: Option[V.GoalPhaseFailedPhaseRepairAdoptionInput],
    port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(sessionId))
        selected = core.copy(executionContext = context)
        result <- description match {
          case Some(value) => port.describeFailedPhaseRepairC(value, selected).map(NativeWorkflowOperationOutput.response(_))
          case None => port.adoptFailedPhaseRepairC(adoption.get, selected).map(NativeWorkflowOperationOutput.response(_))
        }
      } yield result
    }
  }
  private def _reference(from: InstanceRecord) = WorkflowMechanicalTransition.Reference(
    from.identity.value + ":ordered-failed-phase-repair-adoption:" + from.revision.value, "1")
  private def _cause(from: InstanceRecord) = "ordered-failed-phase-repair-adoption:" + from.revision.value
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) &&
    value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536
}
