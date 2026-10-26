package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.convert.ValueReader
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeValidation as N
import GoalPhasePlanRevision as R
import GoalPhaseValidationPriorInvocation as P

/** One independently adopted corrective Plan; old incomplete/UNKNOWN facts
  * remain immutable. Adoption is a pure Direction, never execution or proof.
  * @since   Oct. 24, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseValidationRepairAdoption {
  val purpose = "goal-phase-validation-repair-adoption"
  val describeOperation = "describeGoalPhaseValidationRepair"
  val adoptionOperation = "adoptGoalPhaseValidationRepair"
  val historicalChoice = "AdoptHistoricalAssociationAndRepairPlanAcceptingUnresolvedEffects"
  val schema = "sm-workflow.goal-phase-validation-repair-adoption.v1"
  val message = "Adopt only this exact appended corrective Plan. All original acceptance, accepted Steps, commits, reviews, base and repair history/counts remain. " +
    "Native output is a retained prefix. Historical caller association requires explicit independent Human adoption: native causal proof, old result and managed descendants remain unobserved; " +
    "unresolved prior effects may overlap with new work. Caller-retained public API provenance is not machine authenticated. Adoption saves Direction only. " +
    "A separate ordinary Advance applies current scoped candidate/effect admission before new work; fresh checks, independent reviews and ordinary closure remain required."
  val nativeLimitations: Json = Json.obj("schemaVersion" -> "sm-workflow.native-output-limit-limitations.v1".asJson,
    "result" -> "native-output-limited-incomplete".asJson, "output" -> "retained-prefix-not-complete-test-output".asJson)
  val historicalLimitations: Json = Json.obj("schemaVersion" -> "sm-workflow.historical-validation-association-limitations.v1".asJson,
    "association" -> "requires-explicit-human-adoption-not-mechanically-proven".asJson, "oldResult" -> "unobserved".asJson,
    "managedDescendants" -> "unobserved".asJson, "priorEffects" -> "unresolved-and-may-overlap-with-new-work".asJson,
    "callerSource" -> "caller-retained-public-api-source-not-machine-authenticated".asJson)
  final case class ReceiptRef(identity: String, revision: String)
  final case class Target(schemaVersion: String, source: Json, plan: O.Plan, completed: Vector[O.ClosedStep],
    repairProgress: GoalPhaseRepairProgress.Snapshot, generation: Long, candidatePaths: Vector[String],
    closurePolicy: GoalPhaseNativeClosure.Policy, captured: R.Captured, correctiveChecks: Vector[N.Check],
    processPlan: String, limitations: Json)
  object Target {
    given Encoder[Target] = deriveEncoder
    private val _raw: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (value.asJson == cursor.value && validateC(value).isSuccess) Right(value)
      else Left(DecodingFailure("Validation repair target requires its complete exact non-null contract", cursor.history))))
  }
  final case class Direction(target: Target, proposal: R.Proposal, choice: String, rationale: String,
    participant: String, reference: ContextReference)
  final case class Source(session: F.Session, from: InstanceRecord, controller: O.State,
    candidate: GoalPhaseNativeCandidate.Saved, validation: N.Saved, documentBacked: Boolean,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy] = None, recordingPaths: Vector[String] = Vector.empty,
    dependencyChain: Option[GoalPhaseNativeWorkContinuation.DependencyChain] = None)
  final case class Resumed(record: InstanceRecord, controller: O.State, direction: Direction)
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit else F.invalid("Validation repair requires independent actual Human evidence")
  }
  def choices(target: Target): Vector[String] = Vector(if (_historical(target.source)) historicalChoice else "AdoptRepairPlan", "Defer")
  def participant(target: Target): String = target.source.hcursor.get[String]("participant").getOrElse("")
  def sessionId(target: Target): String = target.source.hcursor.get[String]("sessionId").getOrElse("")
  def frontier(target: Target): String = if (_historical(target.source))
    target.source.hcursor.downField("recovery").get[String]("frontierRevision").getOrElse("")
    else target.source.hcursor.get[String]("frontierRevision").getOrElse("")
  def targetRecord(target: Target): Record = GoalPhaseNativeMetadata.record(target.asJson)
  def targetC(record: Record): Consequence[Target] = _json_c(record).flatMap(_.as[Target].fold(e => F.invalid(e.message), Consequence.success))

  def validateC(target: Target): Consequence[Unit] = for {
    _ <- if (target != null && target.schemaVersion == "sm-workflow.goal-phase-validation-repair-target.v1" &&
        target.plan != null && target.plan.steps != null && target.completed != null && target.completed.forall(_ != null) &&
        target.closurePolicy != null && target.captured != null && target.repairProgress != null && target.processPlan != null &&
        target.completed.map(_.step) == target.plan.steps.map(_.identity) &&
        target.generation > 0 && target.generation < Long.MaxValue && target.candidatePaths != null &&
        target.candidatePaths.nonEmpty && target.candidatePaths.size <= 256 && target.candidatePaths.distinct == target.candidatePaths &&
        target.candidatePaths.forall(RepositorySyncNativeConflictInput.relative) && target.correctiveChecks != null &&
        target.correctiveChecks.nonEmpty && target.correctiveChecks.forall(c => c != null && c.dependency.isEmpty && c.metadata.isEmpty) &&
        _source_valid(target.source) && participant(target).nonEmpty &&
        target.limitations == (if (_historical(target.source)) historicalLimitations else nativeLimitations) &&
        io.circe.parser.parse(target.processPlan).toOption.exists(_.asObject.exists(_.nonEmpty))) Consequence.unit
      else F.invalid("Validation repair target source, complete accepted prefix, process plan or fixed limitations differ")
    _ <- O.validatePlanC(target.plan)
    _ <- GoalPhaseAcceptanceBoundary.savedPlanC(target.plan)
    _ <- GoalPhaseRepairProgress.validateC(target.repairProgress)
    _ <- R.validateCapturedC(target.captured)
    _ <- target.closurePolicy.validateC
    _ <- if (target.captured.documents.isEmpty && target.repairProgress.limit == target.plan.repairLimit &&
        target.closurePolicy.programs.exists(_.step == target.captured.plan.steps.last.identity) &&
        target.captured.plan.steps.last.ownedPaths.forall(target.candidatePaths.contains)) Consequence.unit
      else F.invalid("Validation repair has no admitted exact corrective scope or fixed Step commit program")
  } yield ()

  def bindingC(component: String, target: Target, proposal: R.Proposal, choice: String, rationale: String,
    human: String, reference: ContextReference): Consequence[Json] = for {
    _ <- validateC(target)
    _ <- if (_exact(component) && proposal == target.captured.proposal && choices(target).contains(choice) && _text(rationale) &&
        human == participant(target) && reference != null && _exact(reference.identity) && _exact(reference.revision)) Consequence.unit
      else F.invalid("Validation repair requires its exact captured proposal, source-specific choice and actual Human reference")
  } yield Json.obj("componentInstance" -> component.asJson, "validationRepairAdoption" -> Json.obj("target" -> target.asJson,
    "proposal" -> proposal.asJson, "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
      "participant" -> human.asJson, "reference" -> reference.asJson)))
  def selectBinding(value: Json): Either[String, Json] = for {
    supplied <- Option(value).toRight("Validation repair binding is absent")
    component <- supplied.hcursor.get[String]("componentInstance").left.map(_.message)
    body <- supplied.hcursor.get[Json]("validationRepairAdoption").left.map(_.message)
    target <- body.hcursor.get[Target]("target").left.map(_.message)
    proposal <- body.hcursor.get[R.Proposal]("proposal").left.map(_.message)
    answer = body.hcursor.downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    human <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    selected <- bindingC(component, target, proposal, choice, rationale, human, reference)
      .map[Either[String, Json]](Right(_)).fold(e => Left(e.toString), identity)
    _ <- Either.cond(selected == supplied, (), "Validation repair binding has unknown, missing, null or crossed fields")
  } yield selected

  val codec: WorkflowResultJsonV1.PayloadCodec[Direction] = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "GoalPhaseValidationRepairAdoptionDirection"
    def encode(value: Direction): Json = Json.obj("schemaVersion" -> schema.asJson, "payload" -> Json.obj(
      "target" -> value.target.asJson, "proposal" -> value.proposal.asJson, "choice" -> value.choice.asJson,
      "rationale" -> value.rationale.asJson, "participant" -> value.participant.asJson, "reference" -> value.reference.asJson))
    def decode(value: Json): Either[String, Direction] = for {
      wire <- Option(value).toRight("Validation repair Direction is absent")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      payload <- wire.hcursor.get[Json]("payload").left.map(_.message)
      target <- payload.hcursor.get[Target]("target").left.map(_.message)
      proposal <- payload.hcursor.get[R.Proposal]("proposal").left.map(_.message)
      choice <- payload.hcursor.get[String]("choice").left.map(_.message)
      rationale <- payload.hcursor.get[String]("rationale").left.map(_.message)
      human <- payload.hcursor.get[String]("participant").left.map(_.message)
      reference <- payload.hcursor.get[ContextReference]("reference").left.map(_.message)
      result = Direction(target, proposal, choice, rationale, human, reference)
      _ <- Either.cond(version == schema && encode(result) == wire && choice != "Defer" &&
        bindingC("retained-binding", target, proposal, choice, rationale, human, reference).isSuccess,
        (), "Validation repair Direction differs from its exact adopting answer")
    } yield result
  }

  def describeC(source: Source, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhaseValidationRepairDescriptionInput, readText: String => Consequence[String]):
    Consequence[V.GoalPhaseValidationRepairDescriptionResult] = for {
    prior <- input.priorInvocation.fold(Consequence.success(Option.empty[P.PriorInvocationSnapshot])) { raw => for {
      selector <- _json_c(raw).flatMap(_.as[P.PriorInvocationSelector].fold(e => F.invalid(e.message), Consequence.success))
      policy <- d.validationRepairCallerSource.map(Consequence.success).getOrElse(F.invalid("Historical source policy is absent"))
      snapshot <- P.observeC(selector, policy, input.sessionId, core.executionContext.clock.instant(), readText)
    } yield Some(snapshot) }
    target <- currentTargetC(source, d, core, input.proposal, prior)
  } yield V.GoalPhaseValidationRepairDescriptionResult(input.sessionId, "adoption-required", message,
    core.component.get.core.instanceId.canonicalKey, targetRecord(target), choices(target), adoptionOperation)

  def adoptC(source: Source, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhaseValidationRepairAdoptionInput, readText: String => Consequence[String]):
    Consequence[V.GoalPhaseValidationRepairAdoptionResult] = for {
    supplied <- targetC(input.target)
    prior <- _snapshot_c(supplied.source)
    _ <- prior.fold(Consequence.unit) { snapshot =>
      d.validationRepairCallerSource.map(policy => P.revalidateC(snapshot, policy, input.sessionId, readText))
        .getOrElse(F.invalid("Historical source policy is absent"))
    }
    actual <- currentTargetC(source, d, core, input.proposal, prior)
    _ <- if (input.sessionId == sessionId(actual) && actual == supplied) Consequence.unit else F.invalid("Validation repair target drifted")
    installation <- d.validationRepairAdoption.map(Consequence.success).getOrElse(F.invalid("Independent validation repair authority is absent"))
    _ <- installation.validateC
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, actual, actual.captured.proposal,
      input.choice, input.rationale, source.session.participantIdentity, reference)
    _ <- installation.evidence.admitC(core, reference, binding)
    _ <- if (input.choice == "Defer") Consequence.unit else _append_c(source, d, core,
      Direction(actual, actual.captured.proposal, input.choice, input.rationale, source.session.participantIdentity, reference))
  } yield V.GoalPhaseValidationRepairAdoptionResult(input.sessionId, true, if (input.choice == "Defer") "deferred" else "adopted", message)

  def currentTargetC(source: Source, d: F.Declaration, core: ActionCall.Core, proposal: Record,
    prior: Option[P.PriorInvocationSnapshot]): Consequence[Target] = for {
    target <- _target_c(source, d, core, proposal, prior)
    _ <- if (source.from == source.session.current) Consequence.unit else F.invalid("Validation repair requires its exact current frontier")
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    existing <- adapter.loadRetainedC(_reference(source.from))(using core.executionContext)
    _ <- if (existing.isEmpty) Consequence.unit else F.invalid("Validation repair Direction already exists")
  } yield target

  private def _target_c(source: Source, d: F.Declaration, core: ActionCall.Core, proposal: Record,
    prior: Option[P.PriorInvocationSnapshot]): Consequence[Target] = for {
    _ <- if (!source.documentBacked && source.from.suspension.isEmpty && source.controller.completed.map(_.step) ==
        source.controller.plan.steps.map(_.identity) && source.validation.payload.selected.size == 1 &&
        source.validation.payload.selected.forall(c => c.kind == WorkflowSbtValidation.Kind.Test &&
          c.metadata.isEmpty && c.dependency.isEmpty && c.operation.isEmpty && c.hostIdentity.isEmpty) &&
        source.validation.payload.metadata.isEmpty && source.validation.payload.dependency.isEmpty) Consequence.unit
      else F.invalid("Validation repair requires all accepted original Steps and one ordinary native whole-Phase Test")
    proof <- if (source.controller.stage == O.Stage.Decision && N.outputLimited(source.validation.payload)) {
      if (prior.nonEmpty) F.invalid("Output-limited source must omit priorInvocation") else for {
        actual <- N.outputLimitedAttemptC(source.session, d, core, source.candidate, source.controller, source.validation,
          source.dependencyChain)
        stream <- actual.result.payload.receipts.head.processObservation.get.termination match {
          case org.goldenport.cncf.processexecution.ProcessExecutionTermination.OutputLimitExceeded(value) =>
            Consequence.success(if (value == org.goldenport.cncf.processexecution.ProcessExecutionStream.Stdout) "stdout" else "stderr")
          case _ => F.invalid("Output-limited source has a different actual termination")
        }
      } yield _common_source(source, "output-limited").deepMerge(Json.obj(
        "planIdentity" -> source.controller.plan.identity.asJson, "planRevision" -> source.controller.plan.revision.asJson,
        "base" -> source.controller.base.asJson, "candidate" -> source.validation.payload.candidate.asJson,
        "sourceRevision" -> source.validation.payload.sourceRevision.asJson, "frontierRevision" -> source.from.revision.value.toString.asJson,
        "step" -> source.validation.payload.step.asJson, "checkIdentity" -> source.validation.payload.selected.head.identity.asJson,
        "checkOrdinal" -> source.validation.payload.selected.head.ordinal.asJson, "attempt" -> actual.attempt.asJson,
        "intent" -> _receipt_ref(actual.intent.record, source.validation.payload.sourceRevision), "result" -> _receipt_ref(actual.result.record, source.validation.payload.sourceRevision),
        "validation" -> _receipt_ref(actual.validation.record, source.validation.payload.sourceRevision), "termination" -> "output-limit-exceeded".asJson,
        "stream" -> stream.asJson, "outcome" -> "incomplete".asJson))
    } else if (source.controller.stage == O.Stage.PhaseValidation && source.validation.payload.outcome.isEmpty) for {
      snapshot <- prior.map(Consequence.success).getOrElse(F.invalid("Historical source requires its original public caller snapshot"))
      _ <- if (P.snapshotValid(snapshot) && d.validationRepairCallerSource.contains(snapshot.policy)) Consequence.unit
        else F.invalid("Historical comparison policy or source snapshot differs")
      start <- GoalPhaseNativeHistory.prefixC(source.session.current, InstanceRevision(source.validation.payload.sourceRevision.toLong))
      recovery <- N.repairRecoveryTargetC(source.session, d, core, source.candidate, source.controller, start, source.documentPolicy,
        source.recordingPaths, source.dependencyChain)
      _ <- if (recovery.frontierRevision == recovery.intentRevision && recovery.frontierRevision == source.from.revision.value.toString &&
          source.validation.record == source.from && source.validation.payload.uncertainties.isEmpty &&
          source.validation.payload.admissionFailures.isEmpty && source.validation.payload.stopped.isEmpty &&
          recovery.knownFailedPhase.isEmpty && recovery.terminatedTimeoutPhase.isEmpty && recovery.dependency.isEmpty) Consequence.unit
        else F.invalid("Historical source is not the actual latest unresolved intent without native result or successor")
    } yield _common_source(source, "historical-unobserved-result").deepMerge(Json.obj("recovery" -> recovery.asJson,
      "intent" -> _receipt_ref(source.from, source.validation.payload.sourceRevision), "resultDisposition" -> "unobserved".asJson, "priorInvocation" -> snapshot.asJson))
    else F.invalid("Validation repair source is neither actual output-limited incomplete nor a latest unresolved whole-Phase intent")
    raw <- R.fromPublicProposalC(Some(proposal)).flatMap(_.map(Consequence.success).getOrElse(F.invalid("Complete proposal is absent")))
    effective <- GoalPhaseAcceptanceBoundary.captureC(d.projectAcceptance, raw.plan)
    captured = R.Captured(raw, effective)
    _ <- source.controller.adoptValidationRepairRevisionC(effective, "description-eligibility")
    closure <- d.closureInstallation.map(Consequence.success).getOrElse(F.invalid("Corrective Step closure installation is absent"))
    checks <- N.selectedChecksC(d, effective, Some(effective.steps.last.identity))
    policy <- GoalPhaseWholePhaseCandidateRecovery.resolvePolicyC(d, core)
    target = Target("sm-workflow.goal-phase-validation-repair-target.v1", proof, source.controller.plan, source.controller.completed,
      GoalPhaseRepairProgress.snapshot(source.controller), source.controller.generation, d.candidatePaths, closure.policy, captured, checks,
      io.circe.Printer.noSpaces.copy(sortKeys = true).print(policy.processPlan), if (_historical(proof)) historicalLimitations else nativeLimitations)
    _ <- validateC(target)
  } yield target

  private def _append_c(source: Source, d: F.Declaration, core: ActionCall.Core, direction: Direction): Consequence[Unit] = for {
    _ <- codec.decode(codec.encode(direction)).fold(F.invalid, _ => Consequence.unit)
    state <- source.controller.adoptValidationRepairRevisionC(direction.target.captured.plan, direction.reference.identity)
    _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits(source.controller.stage.toString,
        "validation-repair-adopted", state.stage.toString)) Consequence.unit else F.invalid("Validation repair differs from CML lifecycle")
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(source.from), source.from,
      source.from.revision, F.history(source.from, "phase.Implementation", _cause(source.from)), TypedValue(codec.typeIdentity, direction)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield ()

  def retainedC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Resumed] = for {
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    saved <- adapter.loadRetainedC(_reference(source.from))(using core.executionContext).flatMap(_.map(Consequence.success)
      .getOrElse(F.invalid("Validation repair history has no actual Direction")))
    direction = saved.receipt.payload.value
    _ <- codec.decode(codec.encode(direction)).fold(F.invalid, _ => Consequence.unit)
    closure <- d.closureInstallation.map(Consequence.success).getOrElse(F.invalid("Current Step closure is absent"))
    paths <- GoalPhaseFailedPhaseRepairAdoption.retainedCandidatePathsC(direction.target.candidatePaths, d.candidatePaths)
    policy <- GoalPhaseFailedPhaseRepairAdoption.retainedClosurePolicyC(direction.target.closurePolicy, closure.policy)
    prior <- _snapshot_c(direction.target.source)
    historical = d.copy(candidatePaths = paths, closureInstallation = Some(closure.copy(policy = policy)),
      validationRepairCallerSource = prior.map(_.policy))
    // The adopted snapshot is retained authority history; never reread its page during replay.
    actual <- _target_c(source, historical, core, R.publicProposal(Some(direction.proposal)).get, prior)
    _ <- if (actual.copy(processPlan = direction.target.processPlan) == direction.target &&
        direction.participant == source.session.participantIdentity) Consequence.unit
      else F.invalid("Retained validation repair crosses its original native source, captured Plan or policy")
    next = saved.receipt.next
    prefix <- GoalPhaseNativeHistory.prefixC(source.session.current, next.revision)
    _ <- if (prefix == next && next.identity == source.from.identity && next.definition == source.from.definition &&
        next.revision == source.from.revision.next && next.history.dropRight(1) == source.from.history &&
        next.causation.value == _cause(source.from) && next.currentProgression.contains(ProgressionReference("phase.Implementation")) &&
        next.suspension.isEmpty && next.lifecycle == source.from.lifecycle) Consequence.unit
      else F.invalid("Validation repair Direction is not the exact adjacent native successor")
    state <- source.controller.adoptValidationRepairRevisionC(actual.captured.plan, direction.reference.identity)
  } yield Resumed(next, state, direction)

  def beforeFirstWorkC(source: Source, direction: Direction, d: F.Declaration, core: ActionCall.Core): Consequence[Unit] = for {
    prior <- _snapshot_c(direction.target.source)
    _ <- if (prior.forall(p => d.validationRepairCallerSource.contains(p.policy))) Consequence.unit
      else F.invalid("Historical caller policy drifted before first corrective work")
    actual <- _target_c(source, d, core, R.publicProposal(Some(direction.proposal)).get, prior)
    _ <- if (actual == direction.target) Consequence.unit else F.invalid("Validation repair host paths/checks/closure/process policy drifted before first work")
    start <- GoalPhaseNativeHistory.prefixC(source.session.current, InstanceRevision(source.validation.payload.sourceRevision.toLong))
    observed <- RepositorySyncManagedGitObservation.observeC(core)
    _ <- if (GoalPhaseNativeCandidateBoundary.scopedDirty(observed.dirty, d.candidatePaths, true)) Consequence.unit
      else F.invalid("Validation repair candidate worktree drifted within the captured managed paths before first corrective work")
    _ <- N.preconditionsC(source.session, d, core, source.candidate, source.controller, Some(start),
      source.documentPolicy, source.recordingPaths)
  } yield ()

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] = Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseValidationRepairOperation
      else SmWorkflowComponent.GoalPhaseServiceService.AdoptGoalPhaseValidationRepairOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") || request.operation !=
          (if (describe) describeOperation else adoptionOperation)) F.invalid("Validation repair Operation identity differs")
      else NativeWorkflowResourceInput.selectC[Record](request).map(source => Action(request, describe, source, port))
  }
  private final case class Action(request: Request, describe: Boolean, source: NativeWorkflowResourceInput.Source[Record],
    port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        record <- NativeWorkflowResourceInput.readC(source, reference => read_resource_text(reference, Some(java.nio.charset.StandardCharsets.UTF_8)))
        json <- _json_c(record)
        keys = if (describe) Set("sessionId", "proposal") else Set("sessionId", "target", "proposal", "choice", "rationale", "reference")
        _ <- if (json.asObject.exists(fields => keys.subsetOf(fields.keys.toSet) &&
            fields.keys.toSet.subsetOf(keys ++ (if (describe) Set("priorInvocation") else Set.empty[String])) && fields.values.forall(!_.isNull)))
          Consequence.unit else F.invalid("Validation repair input requires exactly its non-null business keys")
        normalized = GoalPhaseNativeMetadata.record(json)
        result <- if (describe) for {
          input <- summon[ValueReader[V.GoalPhaseValidationRepairDescriptionInput]].readC(normalized)
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(input.sessionId))
          value <- port.describeValidationRepairC(input, core.copy(executionContext = context), _read_source_c)
        } yield NativeWorkflowOperationOutput.response(value)
        else for {
          input <- summon[ValueReader[V.GoalPhaseValidationRepairAdoptionInput]].readC(normalized)
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(input.sessionId))
          value <- port.adoptValidationRepairC(input, core.copy(executionContext = context), _read_source_c)
        } yield NativeWorkflowOperationOutput.response(value)
      } yield result
      private def _read_source_c(uri: String): Consequence[String] = NativeWorkflowResourceInput.referenceC(uri)
        .flatMap(reference => read_resource_text(reference, Some(java.nio.charset.StandardCharsets.UTF_8)))
    }
  }

  private def _common_source(source: Source, kind: String): Json = Json.obj("kind" -> kind.asJson,
    "sessionId" -> source.from.identity.value.asJson, "workflowIdentity" -> source.from.definition.workflowIdentity.value.asJson,
    "workflowRevision" -> source.from.definition.workflowRevision.value.asJson, "participant" -> source.session.participantIdentity.asJson)
  private def _receipt_ref(record: InstanceRecord, sourcerevision: String): Json = ReceiptRef(record.identity.value + ":ordered-validation:" + sourcerevision + ":" + record.causation.value.stripPrefix("ordered-validation:"),
    record.revision.value.toString).asJson
  private def _snapshot_c(source: Json): Consequence[Option[P.PriorInvocationSnapshot]] =
    if (_historical(source)) source.hcursor.get[P.PriorInvocationSnapshot]("priorInvocation").fold(e => F.invalid(e.message), p => Consequence.success(Some(p)))
    else Consequence.success(None)
  private def _source_valid(value: Json): Boolean = value != null && {
    val common = Set("kind", "sessionId", "workflowIdentity", "workflowRevision", "participant")
    val cursor = value.hcursor
    val native = Set("planIdentity", "planRevision", "base", "candidate", "sourceRevision", "frontierRevision", "step", "checkIdentity", "checkOrdinal", "attempt",
      "intent", "result", "validation", "termination", "stream", "outcome")
    val historical = Set("recovery", "intent", "resultDisposition", "priorInvocation")
    value.asObject.exists(fields => fields.keys.toSet == common ++ (if (_historical(value)) historical else native) && fields.values.forall(!_.isNull)) &&
      common.filterNot(_ == "kind").forall(key => cursor.get[String](key).toOption.exists(_exact)) &&
      (if (_historical(value)) cursor.get[P.PriorInvocationSnapshot]("priorInvocation").isRight &&
        cursor.get[GoalPhaseValidationRecovery.RecoveryTarget]("recovery").toOption.exists(r =>
          cursor.downField("recovery").focus.contains(r.asJson) && GoalPhaseValidationRecovery.validTarget(r) &&
          r.sessionId == cursor.get[String]("sessionId").getOrElse("") && r.knownFailedPhase.isEmpty && r.dependency.isEmpty &&
          r.terminatedTimeoutPhase.isEmpty && r.frontierRevision == r.intentRevision) &&
        cursor.get[String]("resultDisposition").toOption.contains("unobserved")
      else cursor.get[String]("kind").toOption.contains("output-limited") && cursor.get[String]("termination").toOption.contains("output-limit-exceeded") &&
        Set("planIdentity", "planRevision", "step", "checkIdentity").forall(key => cursor.get[String](key).toOption.exists(_exact)) &&
        Set("sourceRevision", "frontierRevision").forall(key => cursor.get[String](key).toOption.exists(_revision)) &&
        Set("base", "candidate").forall(key => cursor.get[O.Snapshot](key).toOption.exists(snapshot =>
          cursor.downField(key).focus.contains(snapshot.asJson) &&
            Vector(snapshot.head, snapshot.tree).forall(oid => oid != null && oid.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")))) &&
        cursor.get[String]("outcome").toOption.contains("incomplete") && cursor.get[String]("stream").toOption.exists(Set("stdout", "stderr")) &&
        cursor.get[Int]("checkOrdinal").toOption.exists(n => n > 0 && n <= 16) && cursor.get[Int]("attempt").toOption.exists(_ >= 0)) &&
      (if (_historical(value)) Set("intent") else Set("intent", "result", "validation")).forall(key =>
        cursor.get[ReceiptRef](key).toOption.exists(r => r.asJson == cursor.downField(key).focus.get && _exact(r.identity) && _revision(r.revision)))
  }
  private def _historical(source: Json): Boolean = source != null && source.hcursor.get[String]("kind").toOption.contains("historical-unobserved-result")
  private def _json_c(record: Record): Consequence[Json] = if (record == null) F.invalid("Validation repair business record is absent")
    else record.toJsonStringC.flatMap(text => io.circe.parser.parse(text).fold(e => F.invalid(e.message), R.normalizeJsonC))
  private def _reference(from: InstanceRecord) = WorkflowMechanicalTransition.Reference(from.identity.value + ":ordered-validation-repair-adoption:" + from.revision.value, "1")
  private def _cause(from: InstanceRecord) = "ordered-validation-repair-adoption:" + from.revision.value
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536
}
