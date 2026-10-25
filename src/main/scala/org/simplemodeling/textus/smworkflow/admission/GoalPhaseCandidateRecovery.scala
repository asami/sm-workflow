package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, Decoder, Encoder, DecodingFailure}
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
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeCandidate as C
import GoalPhaseNativeSourceHistory as H
import GoalPhaseDependencyValidation as D
import D.given

/** Independent Human direction for one new observation after fully recorded staging.
  * Old process settlement remains unknown; no completed effect is replayed.
  * @since   Oct. 22, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseCandidateRecovery {
  val purpose = "goal-phase-candidate-recovery"
  val describeOperation = "describeGoalPhaseCandidateRecovery"
  val recoveryOperation = "recoverGoalPhaseCandidate"
  val choices = Vector("AuthorizeNewObservation", "Defer")
  def choicesFor(target: RecoveryTarget): Vector[String] =
    if (!validTarget(target)) Vector.empty
    else if (observationAllowed(target)) choices
    else if (target.controller.generation < Long.MaxValue) Vector("ReviseWork", "Defer")
    else Vector("Defer")
  val message = "The old candidate observation remains UNKNOWN and its termination is unconfirmed. " +
    "AuthorizeNewObservation permits ONE NEW remaining candidate observation, including Git write-tree, " +
    "without repeating staging or source-history writes. It never establishes NotExecuted or termination. " +
    "Original implementation, Plan, base, accepted work and repair counters remain retained. " +
    "An incomplete or UNKNOWN dependency stage offers only explicit ReviseWork or Defer; " +
    "ReviseWork preserves every old intent and uses the existing same-Step semantic generation transition. " +
    "Recovery appends independent Human direction only; a separate ordinary Advance performs the new observation. " +
    "Defer grants no observation. Prior Submit, support approval, authentication and AI prose are not recovery consent."

  private given Encoder[WorkflowSourceHistoryRule.Rule] = deriveEncoder[WorkflowSourceHistoryRule.Rule]
  private given Decoder[WorkflowSourceHistoryRule.Rule] = deriveDecoder[WorkflowSourceHistoryRule.Rule]
  private given Encoder[WorkflowSourceHistoryRule.Selection] = deriveEncoder[WorkflowSourceHistoryRule.Selection]
  private given Decoder[WorkflowSourceHistoryRule.Selection] = deriveDecoder[WorkflowSourceHistoryRule.Selection]

  final case class Controller(plan: O.Plan, stage: String, cursor: Int, generation: Long, repairs: Int,
    base: O.Snapshot, tip: O.Snapshot, completed: Vector[O.ClosedStep], candidate: Option[O.Snapshot],
    checksReceipt: Option[String], reviewEvidence: Option[String], closureIntent: Option[String],
    decision: Option[String], repositoryReceipt: Option[String], repairDirective: Option[O.RepairDirective],
    decisionSource: Option[String], documentDecision: Boolean, ordinaryRepairs: Int,
    pendingRepair: Option[O.RepairDirective], phaseReviewedCandidate: Option[O.Snapshot],
    phaseRecordingPaths: Vector[String], candidateParent: Option[O.Snapshot])
  object Controller {
    given Encoder[Controller] = deriveEncoder[Controller]
    given Decoder[Controller] = _closed(Set("plan", "stage", "cursor", "generation", "repairs", "base", "tip", "completed",
      "candidate", "checksReceipt", "reviewEvidence", "closureIntent", "decision", "repositoryReceipt", "repairDirective",
      "decisionSource", "documentDecision", "ordinaryRepairs", "pendingRepair", "phaseReviewedCandidate",
      "phaseRecordingPaths", "candidateParent"), deriveDecoder[Controller])
  }
  def controllerSnapshot(value: O.State): Controller = Controller(value.plan, value.stage.toString,
    value.cursor, value.generation, value.repairs, value.base, value.tipSnapshot, value.completed,
    value.candidate, value.checksReceipt, value.reviewEvidence, value.closureIntent, value.decision,
    value.repositoryReceipt, value.repairDirective, value.decisionSource.map(_.toString), value.documentDecision,
    value.ordinaryRepairs, value.pendingRepair, value.phaseReviewedCandidate, value.phaseRecordingPaths, value.candidateParent)
  final case class HistoryPair(index: Int, intentIdentity: String, intentRevision: String,
    intentRecordRevision: String, resultIdentity: String, resultRevision: String, resultRecordRevision: String,
    intent: H.Payload, result: H.Payload)
  object HistoryPair {
    given Encoder[HistoryPair] = deriveEncoder[HistoryPair]
    given Decoder[HistoryPair] = _closed(Set("index", "intentIdentity", "intentRevision", "intentRecordRevision",
      "resultIdentity", "resultRevision", "resultRecordRevision", "intent", "result"), deriveDecoder[HistoryPair])
  }
  final case class StagePair(index: Int, intentIdentity: String, intentRevision: String,
    intentRecordRevision: String, resultIdentity: String, resultRevision: String, resultRecordRevision: String,
    intent: C.Payload, result: C.Payload)
  object StagePair {
    given Encoder[StagePair] = deriveEncoder[StagePair]
    given Decoder[StagePair] = _closed(Set("index", "intentIdentity", "intentRevision", "intentRecordRevision",
      "resultIdentity", "resultRevision", "resultRecordRevision", "intent", "result"), deriveDecoder[StagePair])
  }
  final case class DependencyFact(key: String, identity: String, revision: String,
    recordRevision: String, payload: C.Payload)
  object DependencyFact {
    given Encoder[DependencyFact] = deriveEncoder
    private val _decoder: Decoder[DependencyFact] = deriveDecoder
    given Decoder[DependencyFact] = Decoder.instance(c =>
      if (c.value.asObject.exists(_.keys.toSet == Set("key", "identity", "revision", "recordRevision", "payload"))) _decoder(c)
      else Left(DecodingFailure("Dependency recovery fact fields differ", c.history)))
  }
  final case class RecoveryTarget(sessionId: String, controller: Controller, definitionIdentity: String,
    definitionRevision: String, participant: String, sourceRevision: String, frontierRevision: String,
    root: String, branch: String, candidatePaths: Vector[String], managementPaths: Vector[String],
    inspectionIdentity: String, inspectionRevision: String, inspectionRecordRevision: String,
    inspection: C.Payload, historySelection: WorkflowSourceHistoryRule.Selection,
    historyPrefix: Vector[HistoryPair], stagingPrefix: Vector[StagePair],
    oldObservationOutcome: String, oldObservationTermination: String,
    dependencyPrefix: Vector[DependencyFact] = Vector.empty)
  object RecoveryTarget {
    given Encoder[RecoveryTarget] = deriveEncoder[RecoveryTarget].mapJson(_.mapObject(_.filter {
      case ("dependencyPrefix", value) => !value.asArray.exists(_.isEmpty)
      case _ => true
    }))
    private val _decoder: Decoder[RecoveryTarget] = deriveDecoder
    given Decoder[RecoveryTarget] = Decoder.instance { c =>
      val wire = if (c.downField("dependencyPrefix").focus.isEmpty)
        c.value.mapObject(_.add("dependencyPrefix", Json.arr())) else c.value
      _decoder(wire.hcursor).flatMap(t =>
        if (c.value.asObject.exists(_.keys.toSet == Set("sessionId", "controller", "definitionIdentity", "definitionRevision",
          "participant", "sourceRevision", "frontierRevision", "root", "branch", "candidatePaths", "managementPaths",
          "inspectionIdentity", "inspectionRevision", "inspectionRecordRevision", "inspection", "historySelection",
          "historyPrefix", "stagingPrefix", "oldObservationOutcome", "oldObservationTermination") ++
          c.downField("dependencyPrefix").focus.map(_ => "dependencyPrefix")) && validTarget(t)) Right(t)
        else Left(DecodingFailure("Candidate recovery target fields or dependency prefix differ", c.history)))
    }
  }
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else Consequence.configurationInvalid("Candidate recovery requires an independent Human evidence port")
  }

  def targetRecord(value: RecoveryTarget): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def targetC(value: Record): Consequence[RecoveryTarget] =
    if (value == null) _invalid("target is absent") else value.toJsonStringC.flatMap(text =>
      io.circe.parser.parse(text).left.map(_.message).flatMap(decodePublicTarget)
        .fold(_invalid(_), Consequence.success))

  /** Public optional omission is decoded to typed None; durable codecs stay exact. */
  def decodePublicTarget(value: Json): Either[String, RecoveryTarget] = for {
    supplied <- Option(value).toRight("Public candidate recovery target is absent")
    wire <- GoalPhaseNativeMetadata.normalizePublicJson(supplied)
    controller <- publicController(wire.hcursor.downField("controller").focus.getOrElse(Json.Null))
    _ <- publicCandidatePayload(wire.hcursor.downField("inspection").focus.getOrElse(Json.Null))
    _ <- publicMembers(wire, "stagingPrefix", false)(p => for {
      _ <- publicCandidatePayload(p.hcursor.downField("intent").focus.getOrElse(Json.Null))
      _ <- publicCandidatePayload(p.hcursor.downField("result").focus.getOrElse(Json.Null))
    } yield ())
    _ <- publicMembers(wire, "historyPrefix", false)(p => for {
      _ <- _public_history_payload(p.hcursor.downField("intent").focus.getOrElse(Json.Null))
      _ <- _public_history_payload(p.hcursor.downField("result").focus.getOrElse(Json.Null))
    } yield ())
    _ <- publicMembers(wire, "dependencyPrefix", true)(p =>
      publicCandidatePayload(p.hcursor.downField("payload").focus.getOrElse(Json.Null)))
    target <- decodeTarget(wire.mapObject(_.add("controller", controller.asJson)))
  } yield target

  private val _public_controller_decoder: Decoder[Controller] = deriveDecoder[Controller]
  private val _controller_optional = Set("candidate", "candidateParent", "checksReceipt", "reviewEvidence",
    "closureIntent", "decision", "repositoryReceipt", "repairDirective", "decisionSource", "pendingRepair",
    "phaseReviewedCandidate")
  private val _controller_required = Set("plan", "stage", "cursor", "generation", "repairs", "base", "tip",
    "completed", "documentDecision", "ordinaryRepairs", "phaseRecordingPaths")

  private[admission] def publicController(value: Json): Either[String, Controller] = for {
    fields <- value.asObject.toRight("Public candidate controller must be an object")
    _ <- Either.cond(_controller_required.subsetOf(fields.keys.toSet) &&
      fields.keys.forall((_controller_required ++ _controller_optional).contains) && fields.values.forall(!_.isNull),
      (), "Public candidate controller fields are missing, unknown or null")
    controller <- _public_controller_decoder(value.hcursor).left.map(_.message)
  } yield controller

  private[admission] def publicCandidatePayload(value: Json): Either[String, Unit] = for {
    _ <- publicNonNullObject(value, "candidate payload")
    _ <- value.as[C.Payload].left.map(_.message)
  } yield ()

  private def _public_history_payload(value: Json): Either[String, Unit] = for {
    _ <- publicNonNullObject(value, "source history payload")
    _ <- value.as[H.Payload].left.map(_.message)
  } yield ()

  private[admission] def publicNonNullObject(value: Json, subject: String): Either[String, Unit] =
    Either.cond(value.asObject.exists(_.values.forall(!_.isNull)), (), "Public " + subject + " must have non-null members")

  private[admission] def publicMembers(value: Json, key: String, optional: Boolean)
    (validate: Json => Either[String, Unit]): Either[String, Unit] = value.hcursor.downField(key).focus match {
    case None if optional => Right(())
    case Some(items) => items.asArray.toRight("Public " + key + " must be an array").flatMap(
      _.foldLeft[Either[String, Unit]](Right(()))((result, item) => result.flatMap(_ => validate(item))))
    case None => Left("Public " + key + " is required")
  }
  def decodeTarget(value: Json): Either[String, RecoveryTarget] = for {
    supplied <- Option(value).toRight("Candidate recovery target is absent")
    target <- supplied.as[RecoveryTarget].left.map(_.message)
    _ <- Either.cond(validTarget(target), (), "Candidate recovery target is incomplete or crossed")
  } yield target

  def validTarget(t: RecoveryTarget): Boolean = t != null &&
    Vector(t.sessionId, t.definitionIdentity, t.definitionRevision, t.participant, t.branch, t.root).forall(_exact) &&
    scala.util.Try(java.nio.file.Path.of(t.root)).toOption.exists(p => p.isAbsolute && p.normalize.toString == t.root) &&
    Vector(t.sourceRevision, t.frontierRevision, t.inspectionRecordRevision).forall(_revision) &&
    t.controller != null && O.validatePlanC(t.controller.plan).isSuccess && t.controller.stage == "InspectCandidate" &&
    t.controller.cursor >= 0 && t.controller.cursor < t.controller.plan.steps.size &&
    t.controller.completed != null && t.controller.completed.size == t.controller.cursor &&
    t.controller.completed.map(_.step) == t.controller.plan.steps.take(t.controller.cursor).map(_.identity) &&
    t.controller.generation >= 0 && t.controller.repairs >= 0 && t.controller.ordinaryRepairs >= 0 &&
    t.controller.ordinaryRepairs <= t.controller.repairs && _snapshot(t.controller.base) && _snapshot(t.controller.tip) &&
    t.controller.tip == t.controller.completed.lastOption.map(s => O.Snapshot(s.commit.identity, s.commit.tree)).getOrElse(t.controller.base) &&
    t.inspection != null && C.codec.decode(C.codec.encode(t.inspection)).isRight &&
    t.inspection.sourceRevision == t.sourceRevision && t.inspection.step == t.controller.plan.steps(t.controller.cursor).identity &&
    t.inspection.selectedPaths != null && t.inspection.selectedPaths.nonEmpty &&
    t.inspection.selectedPaths.distinct == t.inspection.selectedPaths && t.inspection.stagedPaths.isEmpty &&
    t.inspection.candidate.isEmpty && t.inspection.changedPaths.isEmpty && t.inspection.diff.isEmpty && t.inspection.stepEvidence.isEmpty &&
    GoalPhaseNativeCandidateBoundary.parentC(t.controller.tip, t.inspection.continuation).isSuccess &&
    t.candidatePaths != null && t.candidatePaths.distinct == t.candidatePaths && t.managementPaths != null &&
    t.managementPaths.distinct == t.managementPaths && (t.candidatePaths ++ t.managementPaths).forall(RepositorySyncNativeConflictInput.relative) &&
    t.controller.plan.steps(t.controller.cursor).ownedPaths.forall(t.candidatePaths.contains) &&
    t.inspection.selectedPaths.forall(p => t.candidatePaths.contains(p) && t.controller.plan.steps(t.controller.cursor).ownedPaths.contains(p)) &&
    t.inspectionRevision == "1" && t.inspectionIdentity == _identity(t, "inspection") &&
    BigInt(t.inspectionRecordRevision) == BigInt(t.sourceRevision) + 1 &&
    t.historySelection != null && t.historySelection.rule != null &&
    WorkflowSourceHistoryRule.selectionC(t.historySelection.style, t.historySelection.rule).isSuccess &&
    t.historyPrefix != null && t.historyPrefix.size <= 65536 && t.stagingPrefix != null &&
    t.stagingPrefix.size == t.inspection.selectedPaths.size &&
    t.historyPrefix.forall(p => p != null && p.intent != null) &&
    t.historyPrefix.map(_.intent.path).distinct.size == t.historyPrefix.size &&
    t.historyPrefix.zipWithIndex.forall { (p, i) =>
      p != null && p.index == i && p.intentRevision == "1" && p.resultRevision == "1" &&
        p.intentIdentity == _identity(t, "source-history-intent-" + i) && p.resultIdentity == _identity(t, "source-history-result-" + i) &&
        Vector(p.intentRecordRevision, p.resultRecordRevision).forall(_revision) &&
        BigInt(p.intentRecordRevision) == BigInt(t.sourceRevision) + 2 + BigInt(i) * 2 &&
        BigInt(p.resultRecordRevision) == BigInt(p.intentRecordRevision) + 1 && p.intent != null && p.result != null &&
        H.codec.decode(H.codec.encode(p.intent)).isRight && H.codec.decode(H.codec.encode(p.result)).isRight &&
        p.intent.sourceRevision == t.sourceRevision && p.intent.step == t.inspection.step && p.intent.outcome.isEmpty &&
        p.result.copy(outcome = None) == p.intent && p.result.outcome.exists(Set("updated", "unchanged")) &&
        t.inspection.selectedPaths.exists(owner => GoalPhaseNativeCandidateBoundary.covers(owner, p.intent.path)) &&
        t.historySelection.style == "goldenport" && t.historySelection.rule.exists(rule =>
          rule.identity == p.intent.providerIdentity && rule.style == p.intent.style &&
            WorkflowSourceHistoryRule.includesC(rule, p.intent.path).toOption.contains(true))
    } && t.stagingPrefix.zipWithIndex.forall { (p, i) =>
      p != null && p.index == i && p.intentRevision == "1" && p.resultRevision == "1" &&
        p.intentIdentity == _identity(t, "stage-intent-" + i) && p.resultIdentity == _identity(t, "stage-result-" + i) &&
        Vector(p.intentRecordRevision, p.resultRecordRevision).forall(_revision) &&
        BigInt(p.intentRecordRevision) == BigInt(t.sourceRevision) + 2 + BigInt(t.historyPrefix.size) * 2 + BigInt(i) * 2 &&
        BigInt(p.resultRecordRevision) == BigInt(p.intentRecordRevision) + 1 &&
        p.intent == t.inspection.copy(stagedPaths = t.inspection.selectedPaths.take(i)) &&
        p.result == t.inspection.copy(stagedPaths = t.inspection.selectedPaths.take(i + 1))
    } && dependencyPrefixValid(t) && BigInt(t.frontierRevision) == BigInt(t.sourceRevision) + 1 + BigInt(t.historyPrefix.size) * 2 + BigInt(t.stagingPrefix.size) * 2 + BigInt(t.dependencyPrefix.size) &&
    BigInt(t.frontierRevision) < BigInt(Long.MaxValue) - 2 &&
    t.oldObservationOutcome == "UNKNOWN" && t.oldObservationTermination == "termination-unconfirmed"

  def preparedPayload(target: RecoveryTarget): C.Payload = {
    val payload = target.dependencyPrefix.lastOption.map(_.payload).getOrElse(target.stagingPrefix.last.result)
    if (payload.dependency.nonEmpty && payload.dependencyOrigin.isEmpty)
      payload.copy(dependencyOrigin = Some(D.CandidateOrigin(target.sourceRevision, target.sourceRevision,
        target.dependencyPrefix.last.recordRevision))) else payload
  }
  def observationAllowed(target: RecoveryTarget): Boolean = {
    val payload = preparedPayload(target)
    payload.dependencyAdmission.forall(a => payload.dependencyStaged == a.binding.paths &&
      !target.dependencyPrefix.lastOption.exists(_.key.startsWith("dependency-stage-intent-")))
  }
  def remainingSubject(payload: C.Payload): String =
    if (payload.dependencyAdmission.nonEmpty && payload.dependency.isEmpty) "dependency" else "primary"

  /** Exact effect receipts, not current index contents, establish completed staging. */
  def dependencyPrefixValid(target: RecoveryTarget): Boolean = {
    val initial = target.stagingPrefix.last.result
    val prefix = target.dependencyPrefix
    if (prefix == null || initial.dependencyStaged.nonEmpty || initial.dependency.nonEmpty || initial.dependencyOrigin.nonEmpty)
      false
    else initial.dependencyAdmission match {
      case None => prefix.isEmpty
      case Some(admission) if D.admissionValid(admission) && admission.binding.step == initial.step &&
          admission.binding.requester.repository == target.controller.plan.repository && admission.binding.root != target.root =>
        val keys = admission.binding.paths.indices.flatMap(i => Vector("dependency-stage-intent-" + i,
          "dependency-stage-result-" + i)).toVector ++ Vector("dependency-observation-intent", "dependency-observation-result")
        prefix.size <= keys.size && prefix.zipWithIndex.forall { (fact, index) =>
          val previous = if (index == 0) initial else prefix(index - 1).payload
          val expected = if (index < admission.binding.paths.size * 2 && index % 2 == 1)
            previous.copy(dependencyStaged = admission.binding.paths.take(index / 2 + 1)) else previous
          fact != null && fact.key == keys(index) && fact.identity == _identity(target, fact.key) && fact.revision == "1" &&
            _revision(fact.recordRevision) && BigInt(fact.recordRevision) ==
              BigInt(target.stagingPrefix.last.resultRecordRevision) + index + 1 && fact.payload != null &&
            C.codec.decode(C.codec.encode(fact.payload)).isRight &&
            (if (fact.key == "dependency-observation-result") fact.payload.dependency.exists(c =>
              D.capturedForC(c, target.sessionId, target.controller.plan, initial.step, target.controller.generation,
                target.sourceRevision, target.controller.base, admission).isSuccess &&
              fact.payload == expected.copy(dependency = Some(c), dependencyOrigin = fact.payload.dependencyOrigin) &&
              fact.payload.dependencyOrigin.forall(o => o == D.CandidateOrigin(target.sourceRevision,
                target.sourceRevision, fact.recordRevision))) else fact.payload == expected)
        }
      case _ => false
    }
  }

  private def _identity(t: RecoveryTarget, key: String): String = t.sessionId + ":ordered-candidate:" + t.sourceRevision + ":" + key
  def bindingC(component: String, target: RecoveryTarget, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (!_exact(component) || !validTarget(target) || !choicesFor(target).contains(choice) || !_text(rationale) ||
        participant != target.participant || reference == null || !_exact(reference.identity) || !_exact(reference.revision))
      _invalid("Human binding requires the exact target, choice, rationale, participant and reference")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson, "candidateRecovery" -> Json.obj(
      "target" -> target.asJson, "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
        "participant" -> participant.asJson, "reference" -> reference.asJson))))
  def selectBinding(value: Json): Either[String, Json] = for {
    wire <- Option(value).toRight("Candidate recovery binding is absent")
    component <- wire.hcursor.get[String]("componentInstance").left.map(_.message)
    targetwire <- wire.hcursor.downField("candidateRecovery").get[Json]("target").left.map(_.message)
    target <- decodeTarget(targetwire)
    answer = wire.hcursor.downField("candidateRecovery").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    selected <- bindingC(component, target, choice, rationale, participant, reference)
      .map[Either[String, Json]](Right(_)).fold(error => Left(error.toString), identity)
  } yield selected
  def admitC(d: F.Declaration, core: ActionCall.Core, target: RecoveryTarget,
    input: V.GoalPhaseCandidateRecoveryInput, participant: String): Consequence[Unit] = for {
    installation <- d.candidateRecovery.map(Consequence.success).getOrElse(
      Consequence.securityPermissionDenied("Candidate recovery has no installed independent Human evidence provider"))
    _ <- installation.validateC
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, target, input.choice, input.rationale, participant, reference)
    _ <- installation.evidence.admitC(core, reference, binding)
  } yield ()

  final case class Direction(target: RecoveryTarget, reference: ContextReference, participant: String, rationale: String,
    choice: String = "AuthorizeNewObservation")
  object Direction {
    given Encoder[Direction] = deriveEncoder[Direction]
    given Decoder[Direction] = deriveDecoder[Direction]
  }
  val codec = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "GoalPhaseCandidateRecoveryDirection"
    def encode(value: Direction): Json = {
      val legacy = value.choice == "AuthorizeNewObservation" && value.target.inspection.dependencyAdmission.isEmpty
      Json.obj("schemaVersion" -> (if (legacy) "sm-workflow.goal-phase-candidate-recovery.v1"
        else "sm-workflow.goal-phase-candidate-recovery.v2").asJson,
        "payload" -> (if (legacy) value.asJson.mapObject(_.remove("choice")) else value.asJson))
    }
    def decode(value: Json): Either[String, Direction] = for {
      wire <- Option(value).toRight("Candidate recovery direction is absent")
      _ <- Either.cond(wire.asObject.exists(_.keys.toSet == Set("schemaVersion", "payload")), (), "Candidate recovery envelope differs")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      payload <- wire.hcursor.get[Json]("payload").left.map(_.message)
      legacy = schema == "sm-workflow.goal-phase-candidate-recovery.v1"
      _ <- Either.cond(payload.asObject.exists(_.keys.toSet == (Set("target", "reference", "participant", "rationale") ++
        (if (legacy) Set.empty[String] else Set("choice")))), (), "Candidate recovery direction fields differ")
      _ <- Either.cond(legacy || schema == "sm-workflow.goal-phase-candidate-recovery.v2", (), "Candidate recovery schema differs")
      direction <- (if (legacy) payload.mapObject(_.add("choice", "AuthorizeNewObservation".asJson)) else payload).as[Direction].left.map(_.message)
      _ <- Either.cond(validTarget(direction.target) && choicesFor(direction.target).contains(direction.choice) &&
        direction.choice != "Defer" && (!legacy || direction.target.inspection.dependencyAdmission.isEmpty) && direction.reference != null &&
        _exact(direction.reference.identity) && _exact(direction.reference.revision) &&
        direction.participant == direction.target.participant && _text(direction.rationale), (), "Candidate recovery direction is invalid")
    } yield direction
  }
  private def _reference(from: InstanceRecord) = WorkflowMechanicalTransition.Reference(
    from.identity.value + ":ordered-candidate-recovery:" + from.revision.value, "1")

  def currentTargetC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String],
    completedCandidates: Vector[C.Payload]): Consequence[RecoveryTarget] = for {
    target <- _target_c(session, d, core, from, controller, documentPolicy, recordingPaths)
    _ <- if (session.current.revision.value.toString == target.frontierRevision) Consequence.unit
      else _invalid("requires the exact retained preparation frontier")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    existing <- adapter.loadRetainedC(_reference(from))(using core.executionContext)
    _ <- if (existing.isEmpty) Consequence.unit else _invalid("direction was already retained")
  } yield target

  private def _target_c(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String]): Consequence[RecoveryTarget] = for {
    _ <- if (controller.stage == O.Stage.InspectCandidate && controller.cursor < controller.plan.steps.size &&
        from.suspension.isEmpty && from.currentProgression.contains(ProgressionReference("phase.InspectCandidate")) &&
        !from.causation.value.startsWith("ordered-candidate:") &&
        !from.causation.value.startsWith("ordered-candidate-recovery:") &&
        !from.causation.value.startsWith("ordered-source-history-recovery:")) Consequence.unit
      else _invalid("supports only the original unsuspended selected-Step inspection source")
    original <- GoalPhaseNativeHistory.prefixC(session.current, from.revision)
    _ <- if (original == from) Consequence.unit else _invalid("original source differs from its native history")
    _ <- documentPolicy.fold(Consequence.unit)(_.validateC(controller.plan, d.candidatePaths))
    _ <- GoalPhaseNativeFindingRecording.validatePathsC(recordingPaths, controller.plan, d.candidatePaths)
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, C.codec)
    inspected <- _load_c(adapter, core, from, "inspection")
    inspectionrecord <- adjacentC(session.current, from, inspected._1, "ordered-candidate:inspection")
    selection <- WorkflowSourceHistoryParameters.resolveC(core.executionContext)
    history <- _history_c(session, core, from, inspectionrecord)
    staged <- inspected._2.selectedPaths.zipWithIndex.foldLeft(Consequence.success((history._1, Vector.empty[StagePair]))) {
      case (prior, (path, index)) => for {
        previous <- prior
        intent <- _load_c(adapter, core, from, "stage-intent-" + index)
        intentrecord <- adjacentC(session.current, previous._1, intent._1, "ordered-candidate:stage-intent-" + index)
        result <- _load_c(adapter, core, from, "stage-result-" + index)
        resultrecord <- adjacentC(session.current, intentrecord, result._1, "ordered-candidate:stage-result-" + index)
      } yield (resultrecord, previous._2 :+ StagePair(index, _native_reference(from, "stage-intent-" + index).identity, "1",
        intentrecord.revision.value.toString, _native_reference(from, "stage-result-" + index).identity, "1",
        resultrecord.revision.value.toString, intent._2, result._2))
    }
    dependency <- dependencyPrefixC(session, core, from, staged._1)
    candidate <- adapter.loadRetainedC(_native_reference(from, "candidate"))(using core.executionContext)
    observation <- adapter.loadRetainedC(_native_reference(from, "observation-intent"))(using core.executionContext)
    _ <- if (candidate.isEmpty && observation.isEmpty) Consequence.unit else _invalid("candidate or observation intent already exists")
    target = RecoveryTarget(from.identity.value, controllerSnapshot(controller), from.definition.workflowIdentity.value,
      from.definition.workflowRevision.value, session.participantIdentity, from.revision.value.toString,
      dependency._1.revision.value.toString, d.target.root.toString, d.target.localBranch, d.candidatePaths,
      (documentPolicy.toVector.flatMap(_.paths) ++ recordingPaths).distinct,
      _native_reference(from, "inspection").identity, "1", inspectionrecord.revision.value.toString,
      inspected._2, selection, history._2, staged._2, "UNKNOWN", "termination-unconfirmed", dependency._2)
    _ <- if (target.controller.base == session.started.receipt.payload.value.base && validTarget(target)) Consequence.unit
      else _invalid("saved inspection, full controller, history or staging prefix is crossed or incomplete")
  } yield target

  def dependencyPrefixC(session: F.Session, core: ActionCall.Core, source: InstanceRecord,
    previous: InstanceRecord): Consequence[(InstanceRecord, Vector[DependencyFact])] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, C.codec)
    result <- {
      def _walk_(prior: InstanceRecord, facts: Vector[DependencyFact]): Consequence[(InstanceRecord, Vector[DependencyFact])] =
        GoalPhaseNativeHistory.nextC(session.current, prior).flatMap {
          case Some(next) if next.causation.value.startsWith("ordered-candidate:dependency-") && facts.size < 258 =>
            val key = next.causation.value.stripPrefix("ordered-candidate:")
            for {
              saved <- _load_c(adapter, core, source, key)
              record <- adjacentC(session.current, prior, saved._1, next.causation.value)
              continued <- _walk_(record, facts :+ DependencyFact(key, _native_reference(source, key).identity,
                "1", record.revision.value.toString, saved._2))
            } yield continued
          case _ => Consequence.success((prior, facts))
        }
      _walk_(previous, Vector.empty)
    }
  } yield result

  private def _native_reference(source: InstanceRecord, key: String) = WorkflowMechanicalTransition.Reference(
    source.identity.value + ":ordered-candidate:" + source.revision.value + ":" + key, "1")
  private def _load_c[A](adapter: WorkflowManagedMechanicalTransition[A], core: ActionCall.Core,
    source: InstanceRecord, key: String): Consequence[(InstanceRecord, A)] =
    adapter.loadRetainedC(_native_reference(source, key))(using core.executionContext).flatMap(_.map(value =>
      Consequence.success((value.receipt.next, value.receipt.payload.value))).getOrElse(_invalid("missing exact native receipt: " + key)))

  /** Actual next causes determine the prefix. A client list never determines eligibility. */
  private def _history_c(session: F.Session, core: ActionCall.Core, source: InstanceRecord,
    inspected: InstanceRecord): Consequence[(InstanceRecord, Vector[HistoryPair])] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, H.codec)
    result <- {
      def _walk_(previous: InstanceRecord, prefix: Vector[HistoryPair]): Consequence[(InstanceRecord, Vector[HistoryPair])] = for {
        next <- GoalPhaseNativeHistory.nextC(session.current, previous)
        result <- next match {
          case Some(entry) if prefix.size < 65536 && entry.causation.value == "ordered-candidate:source-history-intent-" + prefix.size => for {
            intent <- _load_c(adapter, core, source, "source-history-intent-" + prefix.size)
            intentrecord <- adjacentC(session.current, previous, intent._1, entry.causation.value)
            result <- _load_c(adapter, core, source, "source-history-result-" + prefix.size)
            resultrecord <- adjacentC(session.current, intentrecord, result._1, "ordered-candidate:source-history-result-" + prefix.size)
            continued <- _walk_(resultrecord, prefix :+ HistoryPair(prefix.size,
              _native_reference(source, "source-history-intent-" + prefix.size).identity, "1", intentrecord.revision.value.toString,
              _native_reference(source, "source-history-result-" + prefix.size).identity, "1", resultrecord.revision.value.toString,
              intent._2, result._2))
          } yield continued
          case Some(entry) if entry.causation.value == "ordered-candidate:stage-intent-0" => Consequence.success((previous, prefix))
          case _ => _invalid("history prefix has a gap, unresolved result or non-staging successor")
        }
      } yield result
      _walk_(inspected, Vector.empty)
    }
  } yield result

  def appendC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    target: RecoveryTarget, reference: ContextReference, rationale: String,
    controller: O.State, choice: String): Consequence[Unit] = for {
    _ <- if (validTarget(target) && controllerSnapshot(controller) == target.controller &&
        session.current.revision.value.toString == target.frontierRevision &&
        session.current.identity == from.identity) Consequence.unit else _invalid("frontier changed")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    payload = Direction(target, reference, session.participantIdentity, rationale, choice)
    _ <- codec.decode(codec.encode(payload)).fold(_invalid, _ => Consequence.unit)
    state <- directionStateC(controller, from, payload)
    _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(from), session.current,
      session.current.revision, F.history(session.current, "phase." + state.stage.toString, "ordered-candidate-recovery:" + from.revision.value),
      TypedValue(codec.typeIdentity, payload)), ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(
      using core.executionContext)
  } yield ()
  final case class Resumed(record: InstanceRecord, target: RecoveryTarget, controller: O.State, choice: String)
  def directionStateC(controller: O.State, from: InstanceRecord, direction: Direction): Consequence[O.State] =
    if (direction.choice == "AuthorizeNewObservation" && observationAllowed(direction.target)) Consequence.success(controller)
    else if (direction.choice == "ReviseWork" && choicesFor(direction.target).contains("ReviseWork"))
      controller.acceptC(O.CandidateWorkRevisionAdmitted(ContextReference(_reference(from).identity, "1"), direction.rationale))
    else _invalid("choice does not match the exact remaining observation subject")

  /** Read only an accepted immutable target under an ordered append-only policy.
    * New target admission and candidate effects retain the current declaration.
    */
  private def _captured_target_c(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, documentpolicy: Option[GoalPhaseDocumentProgress.Policy], recordingpaths: Vector[String],
    captured: RecoveryTarget): Consequence[RecoveryTarget] = for {
    _ <- if (validTarget(captured) && d.candidatePaths != null &&
        d.candidatePaths.startsWith(captured.candidatePaths) && d.candidatePaths.distinct == d.candidatePaths &&
        d.candidatePaths.forall(RepositorySyncNativeConflictInput.relative)) Consequence.unit
      else _invalid("retained direction requires an append-only candidate path declaration")
    actual <- _target_c(session, d.copy(candidatePaths = captured.candidatePaths), core, from, controller,
      documentpolicy, recordingpaths)
    _ <- if (actual == captured) Consequence.unit else _invalid("retained direction differs from its complete captured target")
  } yield actual

  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String]): Consequence[Option[Resumed]] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- adapter.loadRetainedC(_reference(from))(using core.executionContext)
    result <- retained match {
      case None => Consequence.success(Option.empty[Resumed])
      case Some(value) => for {
        direction <- codec.decode(codec.encode(value.receipt.payload.value)).fold(_invalid, Consequence.success)
        actual <- _captured_target_c(session, d, core, from, controller, documentPolicy, recordingPaths, direction.target)
        _ <- if (direction.target == actual) Consequence.unit
          else _invalid("retained direction crosses its original target")
        frontier <- GoalPhaseNativeHistory.prefixC(session.current, InstanceRevision(actual.frontierRevision.toLong))
        state <- directionStateC(controller, from, direction)
        next <- GoalPhaseNativeHistory.prefixC(session.current, value.receipt.next.revision)
        _ <- if (next == value.receipt.next && next.identity == frontier.identity && next.definition == frontier.definition &&
            next.revision.value == frontier.revision.value + 1 && next.history.dropRight(1) == frontier.history &&
            next.causation.value == "ordered-candidate-recovery:" + from.revision.value && next.suspension.isEmpty &&
            next.lifecycle == frontier.lifecycle && next.currentProgression.contains(ProgressionReference("phase." + state.stage.toString)))
          Consequence.unit else _invalid("retained direction crosses its actual prefix or selected progression")
        _ <- if (direction.choice != "ReviseWork" || d.candidatePaths == actual.candidatePaths ||
            session.current.revision.value > next.revision.value) Consequence.unit
          else _invalid("pending revision work crosses its exact admitted candidate policy")
      } yield Some(Resumed(next, actual, state, direction.choice))
    }
  } yield result

  private[admission] def adjacentC(current: InstanceRecord, previous: InstanceRecord, saved: InstanceRecord,
    cause: String): Consequence[InstanceRecord] = for {
    actual <- GoalPhaseNativeHistory.prefixC(current, saved.revision)
    _ <- if (actual == saved && saved.identity == previous.identity && saved.definition == previous.definition &&
        previous.revision.value < Long.MaxValue && saved.revision == previous.revision.next &&
        saved.history.dropRight(1) == previous.history && saved.causation.value == cause &&
        saved.currentProgression == previous.currentProgression && saved.suspension.isEmpty && saved.lifecycle == previous.lifecycle)
      Consequence.unit else _invalid("missing, crossed or noncontiguous native predecessor")
  } yield actual

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseCandidateRecoveryOperation
      else SmWorkflowComponent.GoalPhaseServiceService.RecoverGoalPhaseCandidateOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") ||
          request.operation != (if (describe) describeOperation else recoveryOperation)) _invalid("Operation identity differs")
      else if (describe) NativeWorkflowOperationInput.textC(request, "sessionId").map(identity => Action(request, identity, None, port))
      else NativeWorkflowOperationInput.valueC[V.GoalPhaseCandidateRecoveryInput](request, "input").flatMap { input =>
        if (input == null || !_exact(input.sessionId) || input.reference == null || !Vector("AuthorizeNewObservation", "ReviseWork", "Defer").contains(input.choice) ||
            !_text(input.rationale) || !_exact(input.reference.identity) || !_exact(input.reference.revision))
          _invalid("input requires an explicit Human direction and reference")
        else Consequence.success(Action(request, input.sessionId, Some(input), port))
      }
  }
  private final case class Action(request: Request, sessionId: String, input: Option[V.GoalPhaseCandidateRecoveryInput],
    port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(sessionId))
        selected = core.copy(executionContext = context)
        result <- input match {
          case None => port.describeCandidateRecoveryC(sessionId, selected).map(NativeWorkflowOperationOutput.response(_))
          case Some(value) => port.recoverCandidateC(value, selected).map(NativeWorkflowOperationOutput.response(_))
        }
      } yield result
    }
  }
  private def _closed[A](keys: Set[String], decoder: Decoder[A]): Decoder[A] = Decoder.instance(c =>
    if (c.value.asObject.exists(_.keys.toSet == keys)) decoder(c)
    else Left(DecodingFailure("Candidate recovery fields differ", c.history)))
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _snapshot(value: O.Snapshot): Boolean = value != null && Vector(value.head, value.tree).forall(v =>
    v != null && v.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})"))
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Candidate recovery: " + reason)
}
