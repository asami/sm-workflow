package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.convert.ValueReader
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.processexecution.*
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
import GoalPhaseDependencyValidation as D
import GoalPhaseDependencyValidation.given
import GoalPhaseDependencySourceRevision as R
import GoalPhaseNativeCandidate as C
import GoalPhaseNativeDependencyValidation as N
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhasePrerequisitePublicationUnknownRecovery as W

/** One independently directed corrected dependency source at a failed pre-check
  * publication. Direction appends no Git or SBT effect; Advance owns the bounded
  * effect chain and the ordinary prerequisite owner owns publication.
  * @since   Nov.  1, 2026
  * @version Nov.  9, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhasePrerequisitePublicationRecovery {
  val purpose = "goal-phase-prerequisite-publication-recovery"
  val schema = "sm-workflow.goal-phase-prerequisite-publication-recovery.v1"
  val describeOperation = "describeGoalPhasePrerequisitePublicationRecovery"
  val recoveryOperation = "recoverGoalPhasePrerequisitePublication"
  val causationPrefix = "ordered-prerequisite-publication-recovery:"
  val choices = Vector("AdoptCorrectedSourceAndPublishOnce", "Defer")
  val primarySchema = "sm-workflow.goal-phase-prerequisite-publication-recovery.v2"
  val primaryCausationPrefix = causationPrefix + "primary:"
  enum PrimaryPhase { case SourceRequired, ArtifactDecision, PrimaryDecision, PrimaryUncertain }
  object PrimaryPhase {
    given Encoder[PrimaryPhase] = Encoder.encodeString.contramap(_.toString)
    given Decoder[PrimaryPhase] = Decoder.decodeString.emap(value =>
      PrimaryPhase.values.find(_.toString == value).toRight("Unknown primary continuation phase"))
  }
  /** The original Direction and candidate remain immutable causal evidence.
    * Current candidate and capture are separate, source-bound observations. */
  final case class PrimaryTarget(schemaVersion: String, phase: PrimaryPhase,
    componentInstance: String, participant: String, sessionId: String, frontierRevision: String,
    controller: String, plan: O.Plan, base: O.Snapshot, generation: Long, completed: Vector[O.ClosedStep],
    repairProgress: GoalPhaseRepairProgress.Snapshot, candidateRecordRevision: String, candidate: C.Payload,
    originalDirection: W.Direction, originalDirectionRecord: ContextReference,
    requestingResult: ContextReference, targets: Vector[LocalPublication.Target], publicationPolicy: String,
    processPolicy: String, failed: LocalPublication.Result, primarySource: LocalPublication.Source,
    dependencyGuard: R.Guard, sourceCapture: Option[R.Capture], priorDirections: Vector[PrimaryFact],
    scopeApprovals: Vector[D.Approval])
  object PrimaryTarget {
    given Encoder[PrimaryTarget] = Encoder.instance[PrimaryTarget](target => Json.obj(
      "schemaVersion" -> target.schemaVersion.asJson,
      "phase" -> target.phase.asJson,
      "componentInstance" -> target.componentInstance.asJson,
      "participant" -> target.participant.asJson,
      "sessionId" -> target.sessionId.asJson,
      "frontierRevision" -> target.frontierRevision.asJson,
      "controller" -> target.controller.asJson,
      "plan" -> target.plan.asJson,
      "base" -> target.base.asJson,
      "generation" -> target.generation.asJson,
      "completed" -> target.completed.asJson,
      "repairProgress" -> target.repairProgress.asJson,
      "candidateRecordRevision" -> target.candidateRecordRevision.asJson,
      "candidate" -> target.candidate.asJson,
      "originalDirection" -> target.originalDirection.asJson,
      "originalDirectionRecord" -> target.originalDirectionRecord.asJson,
      "requestingResult" -> target.requestingResult.asJson,
      "targets" -> target.targets.asJson,
      "publicationPolicy" -> target.publicationPolicy.asJson,
      "processPolicy" -> target.processPolicy.asJson,
      "failed" -> target.failed.asJson,
      "primarySource" -> target.primarySource.asJson,
      "dependencyGuard" -> target.dependencyGuard.asJson,
      "sourceCapture" -> target.sourceCapture.asJson,
      "priorDirections" -> target.priorDirections.asJson,
      "scopeApprovals" -> target.scopeApprovals.asJson)).mapJson(_public)
    private val _decoder: Decoder[PrimaryTarget] = Decoder.instance { cursor => for {
      schemaversion <- cursor.get[String]("schemaVersion")
      phase <- cursor.get[PrimaryPhase]("phase")
      componentinstance <- cursor.get[String]("componentInstance")
      participant <- cursor.get[String]("participant")
      sessionid <- cursor.get[String]("sessionId")
      frontierrevision <- cursor.get[String]("frontierRevision")
      controller <- cursor.get[String]("controller")
      plan <- cursor.get[O.Plan]("plan")
      base <- cursor.get[O.Snapshot]("base")
      generation <- cursor.get[Long]("generation")
      completed <- cursor.get[Vector[O.ClosedStep]]("completed")
      repairprogress <- cursor.get[GoalPhaseRepairProgress.Snapshot]("repairProgress")
      candidaterecordrevision <- cursor.get[String]("candidateRecordRevision")
      candidate <- cursor.get[C.Payload]("candidate")
      originaldirection <- cursor.get[W.Direction]("originalDirection")
      originaldirectionrecord <- cursor.get[ContextReference]("originalDirectionRecord")
      requestingresult <- cursor.get[ContextReference]("requestingResult")
      targets <- cursor.get[Vector[LocalPublication.Target]]("targets")
      publicationpolicy <- cursor.get[String]("publicationPolicy")
      processpolicy <- cursor.get[String]("processPolicy")
      failed <- cursor.get[LocalPublication.Result]("failed")
      primarysource <- cursor.get[LocalPublication.Source]("primarySource")
      dependencyguard <- cursor.get[R.Guard]("dependencyGuard")
      sourcecapture <- cursor.get[Option[R.Capture]]("sourceCapture")
      priordirections <- cursor.get[Vector[PrimaryFact]]("priorDirections")
      scopeapprovals <- cursor.get[Vector[D.Approval]]("scopeApprovals")
    } yield PrimaryTarget(schemaversion, phase, componentinstance, participant, sessionid, frontierrevision,
      controller, plan, base, generation, completed, repairprogress, candidaterecordrevision, candidate,
      originaldirection, originaldirectionrecord, requestingresult, targets, publicationpolicy, processpolicy,
      failed, primarysource, dependencyguard, sourcecapture, priordirections, scopeapprovals) }
    given Decoder[PrimaryTarget] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (primaryTargetValid(value) && value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Primary continuation target is crossed, incomplete or noncanonical", cursor.history))))
  }
  final case class PrimaryFact(target: PrimaryTarget, reference: ContextReference, rationale: String, choice: String,
    inheritedFrom: Option[ContextReference] = None)
  object PrimaryFact {
    given Encoder[PrimaryFact] = Encoder.instance[PrimaryFact](fact => Json.obj(
      "target" -> fact.target.asJson,
      "reference" -> fact.reference.asJson,
      "rationale" -> fact.rationale.asJson,
      "choice" -> fact.choice.asJson,
      "inheritedFrom" -> fact.inheritedFrom.asJson)).mapJson(_public)
    private val _decoder: Decoder[PrimaryFact] = Decoder.instance { cursor => for {
      target <- cursor.get[PrimaryTarget]("target")
      reference <- cursor.get[ContextReference]("reference")
      rationale <- cursor.get[String]("rationale")
      choice <- cursor.get[String]("choice")
      inheritedfrom <- cursor.get[Option[ContextReference]]("inheritedFrom")
    } yield PrimaryFact(target, reference, rationale, choice, inheritedfrom) }
    given Decoder[PrimaryFact] = Decoder.instance(cursor => _decoder(cursor).flatMap(value =>
      if (primaryFactValid(value) && value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Primary continuation fact is crossed or noncanonical", cursor.history))))
  }
  final case class PrimarySaved(record: InstanceRecord, fact: PrimaryFact)
  final case class PrimarySource(native: Source, original: W.Saved, requestingResult: ContextReference,
    approvals: Vector[D.Approval], capture: Option[W.SourceProgress], directions: Vector[PrimarySaved])
  val primaryCodec: WorkflowResultJsonV1.PayloadCodec[PrimaryFact] = new WorkflowResultJsonV1.PayloadCodec[PrimaryFact] {
    val typeIdentity = "GoalPhasePrerequisitePrimaryContinuationFact"
    def encode(value: PrimaryFact): Json = Json.obj("schemaVersion" -> primarySchema.asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, PrimaryFact] = for {
      version <- value.hcursor.get[String]("schemaVersion").left.map(_.message)
      payload <- value.hcursor.get[PrimaryFact]("payload").left.map(_.message)
      _ <- Either.cond(version == primarySchema && encode(payload) == value, (), "Primary fact schema differs")
    } yield payload
  }
  def primaryChoices(target: PrimaryTarget): Vector[String] = target.phase match {
    case PrimaryPhase.SourceRequired | PrimaryPhase.PrimaryUncertain => Vector("Defer")
    case PrimaryPhase.ArtifactDecision => Vector("AcceptExistingArtifactsWithUnknownEffects", "Defer")
    case PrimaryPhase.PrimaryDecision if target.priorDirections.lastOption.exists(f =>
        f.choice == "AuthorizePrimaryPublicationOnce" &&
          f.target.failed == target.failed && _primary_operation_matches(f.target, target)) => Vector("Defer")
    case PrimaryPhase.PrimaryDecision => Vector("AuthorizePrimaryPublicationOnce", "Defer")
  }
  private def _primary_operation_matches(previous: PrimaryTarget, current: PrimaryTarget): Boolean =
    previous.copy(frontierRevision = current.frontierRevision, priorDirections = current.priorDirections) == current
  def primaryMessage(target: PrimaryTarget): String = target.phase match {
    case PrimaryPhase.SourceRequired => "The primary publication terminated unsuccessfully before checks. Call " +
      R.describeOperation + " to select a declared compatible dependency source. No publication is authorized."
    case PrimaryPhase.ArtifactDecision => "The corrected source is captured. A fresh Human answer must accept existing dependency artifacts despite retained UNKNOWN effects."
    case PrimaryPhase.PrimaryDecision => "A fresh Human answer may authorize one primary publication at this exact source. Ordinary Advance owns execution."
    case PrimaryPhase.PrimaryUncertain => "The primary attempt is stopped or its effects are UNKNOWN. Collect retained observations; Defer grants no new attempt or source cycle."
  }
  def primaryTargetRecord(value: PrimaryTarget): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def primaryTargetC(value: Record): Consequence[PrimaryTarget] = W.jsonC(value).flatMap(_.as[PrimaryTarget]
    .left.map(_.message).fold(F.invalid, Consequence.success))
  def isPrimaryTargetC(value: Record): Consequence[Boolean] = W.jsonC(value).map(_.hcursor.get[String]("schemaVersion").contains(primarySchema))

  def primaryTargetValid(value: PrimaryTarget): Boolean = value != null && value.schemaVersion == primarySchema &&
    value.phase != null && _exact(value.componentInstance) && _exact(value.participant) && _exact(value.sessionId) &&
    _revision(value.frontierRevision) && _revision(value.candidateRecordRevision) &&
    BigInt(value.candidateRecordRevision) <= BigInt(value.frontierRevision) && _json(value.controller) &&
    W.directionValid(value.originalDirection) && value.originalDirection.choice.contains("AcceptExistingArtifactsWithUnknownEffects") &&
    R.referenceValid(value.originalDirectionRecord) && value.originalDirectionRecord.identity == value.sessionId &&
    value.originalDirectionRecord.revision.toLongOption.contains(value.originalDirection.target.frontierRevision.toLong + 1L) &&
    R.referenceValid(value.requestingResult) && value.requestingResult == value.originalDirection.target.requestingResult &&
    value.componentInstance == value.originalDirection.target.componentInstance && value.participant == value.originalDirection.target.participant &&
    value.sessionId == value.originalDirection.target.sessionId && value.plan == value.originalDirection.target.plan &&
    value.base == value.originalDirection.target.base && value.completed == value.originalDirection.target.completed &&
    value.repairProgress == value.originalDirection.target.repairProgress && value.candidate != null &&
    value.candidate.asJson.as[C.Payload].contains(value.candidate) && value.candidate.candidate == value.originalDirection.target.candidate.candidate &&
    value.candidate.diff == value.originalDirection.target.candidate.diff && value.candidate.step == value.originalDirection.target.candidate.step &&
    value.candidate.changedPaths == value.originalDirection.target.candidate.changedPaths &&
    value.targets != null && value.targets.size == 2 && value.targets.head == value.originalDirection.target.unknown.request.target &&
    value.targets(1).dependency.isEmpty && value.targets(1).beforeChecks && value.targets.map(_.identity).distinct.size == 2 &&
    _json(value.publicationPolicy) && _json(value.processPolicy) &&
    value.failed != null && value.failed.request != null && value.failed.request.target == value.targets(1) &&
    value.failed.request.input.ownerSessionId.contains(value.sessionId) && value.failed.request.participant == value.participant &&
    !value.failed.request.input.explicit && value.failed.request.input.answer.isEmpty &&
    value.primarySource != null && value.candidate.candidate.exists(snapshot =>
      value.primarySource.head == snapshot.head && value.primarySource.tree == snapshot.tree) &&
    value.sourceCapture != null && value.priorDirections != null &&
    value.priorDirections.forall(primaryFactValid) && value.priorDirections.map(_.reference).distinct.size == value.priorDirections.size &&
    value.priorDirections.zipWithIndex.forall { (fact, index) => fact.target.priorDirections == value.priorDirections.take(index) &&
      fact.target.originalDirection == value.originalDirection && fact.target.sourceCapture == value.sourceCapture &&
      fact.target.primarySource == value.primarySource && fact.target.targets == value.targets &&
      fact.choice == (if (index == 0) "AcceptExistingArtifactsWithUnknownEffects" else "AuthorizePrimaryPublicationOnce") } &&
    value.scopeApprovals != null && value.scopeApprovals.forall(D.approvalValid) && R.guardValid(value.dependencyGuard) && {
      val old = value.originalDirection.target
      val controller = io.circe.parser.parse(value.controller).toOption.get.hcursor
      val captured = value.sourceCapture.exists(c => R.captureValid(c) && c.schemaVersion == R.primaryCaptureSchema &&
        c.direction.target.primaryFailureOrigin.exists(o => o.phase == PrimaryPhase.SourceRequired &&
          o.originalDirection == value.originalDirection && o.originalDirectionRecord == value.originalDirectionRecord &&
          o.publicationPolicy == old.publicationPolicy) &&
        c.generation == value.generation && c.postStageGuard == value.dependencyGuard &&
        value.candidate.dependencySourceCapture.contains(c) && primaryProgramMatches(value.processPolicy, value.targets(1), R.consumptionOf(c)))
      val sameprimary = value.candidate.copy(sourceRevision = old.candidate.sourceRevision,
        dependencyAdmission = old.candidate.dependencyAdmission, dependencyStaged = old.candidate.dependencyStaged,
        dependency = old.candidate.dependency, dependencyOrigin = old.candidate.dependencyOrigin,
        dependencySourceDirection = old.candidate.dependencySourceDirection, dependencySourceGuard = old.candidate.dependencySourceGuard,
        dependencySourceStages = old.candidate.dependencySourceStages, dependencySourceCapture = old.candidate.dependencySourceCapture,
        dependencySourceScope = old.candidate.dependencySourceScope) == old.candidate
      controller.get[String]("stage").contains("Validation") && controller.get[O.Plan]("plan").contains(value.plan) &&
        controller.get[O.Snapshot]("base").contains(value.base) && controller.get[Long]("generation").contains(value.generation) &&
        controller.get[Vector[O.ClosedStep]]("completed").contains(value.completed) &&
        controller.get[Int]("cursor").contains(value.completed.size) && sameprimary &&
        (value.phase match {
          case PrimaryPhase.SourceRequired => value.sourceCapture.isEmpty && value.priorDirections.isEmpty &&
            value.candidate == old.candidate && value.generation == old.generation &&
            value.dependencyGuard.root == old.admission.binding.root && value.dependencyGuard.branch == old.admission.binding.branch &&
            value.dependencyGuard.head == old.admission.binding.base.head && value.dependencyGuard.committedTree == old.admission.binding.base.tree &&
            value.dependencyGuard.selectedFiles.map(_.path) == old.admission.binding.paths &&
            value.dependencyGuard.excludedFiles.map(_.path) == old.guard.excludedFiles.map(_.path) && confirmedFailure(value.failed) &&
            value.failed.request.source == value.primarySource
          case PrimaryPhase.ArtifactDecision => captured && value.generation == old.generation + 1L &&
            value.priorDirections.isEmpty && confirmedFailure(value.failed)
          case PrimaryPhase.PrimaryDecision => captured && value.generation == old.generation + 1L &&
            value.priorDirections.headOption.exists(_.choice == "AcceptExistingArtifactsWithUnknownEffects") && confirmedFailure(value.failed)
          case PrimaryPhase.PrimaryUncertain => captured && value.generation == old.generation + 1L &&
            value.priorDirections.lastOption.exists(_.choice == "AuthorizePrimaryPublicationOnce") && !confirmedFailure(value.failed) && !value.failed.ready
        })
    }
  def primaryFactValid(value: PrimaryFact): Boolean = value != null && primaryTargetValid(value.target) &&
    R.referenceValid(value.reference) && _rationale(value.rationale) && primaryChoices(value.target).contains(value.choice) && value.choice != "Defer" &&
    value.inheritedFrom != null && value.inheritedFrom.forall(reference => R.referenceValid(reference) &&
      value.target.sourceCapture.exists(c => c.direction.choice.contains(R.combinedPrimaryChoice) && c.direction.directive == reference &&
        c.direction.target.primaryFailureOrigin.exists(origin => origin.failed == value.target.failed &&
          origin.processPolicy == value.target.processPolicy && origin.publicationPolicy == value.target.publicationPolicy &&
          origin.primarySource == value.target.primarySource && origin.targets == value.target.targets)) &&
      (value.choice == "AcceptExistingArtifactsWithUnknownEffects" || value.choice == "AuthorizePrimaryPublicationOnce" &&
        value.target.priorDirections.size == 1 && value.target.priorDirections.head.inheritedFrom.contains(reference)))
  def primaryBindingC(component: String, target: PrimaryTarget, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (primaryTargetValid(target) && component == target.componentInstance && participant == target.participant &&
        primaryChoices(target).contains(choice) && _rationale(rationale) && R.referenceValid(reference)) Consequence.success(Json.obj(
      "componentInstance" -> component.asJson, "prerequisitePublicationRecovery" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson))))
    else F.invalid("Primary continuation answer crosses its phase, source or participant")
  val message = "Independent Human direction selects the exact corrected dependency source for one ordinary prerequisite publication. " +
    "The same Session, Plan, original base, accepted prefix and repair accounting remain. A later ordinary Advance owns effects."

  final case class Target(schemaVersion: String, componentInstance: String, participant: String,
    sessionId: String, frontierRevision: String, controller: Json, plan: O.Plan, base: O.Snapshot,
    generation: Long, completed: Vector[O.ClosedStep], repairProgress: GoalPhaseRepairProgress.Snapshot,
    candidateRecordRevision: String, candidate: C.Payload, admission: D.Admission,
    anchoredAdmission: D.Admission, ancestors: Vector[R.Ancestor], publicationTarget: LocalPublication.Target,
    publicationPolicy: String, failed: LocalPublication.Result, preGuard: R.Guard, processPolicy: String)
  object Target {
    given Encoder[Target] = Encoder.instance { target =>
      val value = Json.obj(
        "schemaVersion" -> target.schemaVersion.asJson,
        "componentInstance" -> target.componentInstance.asJson,
        "participant" -> target.participant.asJson,
        "sessionId" -> target.sessionId.asJson,
        "frontierRevision" -> target.frontierRevision.asJson,
        "controller" -> target.controller,
        "plan" -> target.plan.asJson,
        "base" -> target.base.asJson,
        "generation" -> target.generation.asJson,
        "completed" -> target.completed.asJson,
        "repairProgress" -> target.repairProgress.asJson,
        "candidateRecordRevision" -> target.candidateRecordRevision.asJson,
        "candidate" -> target.candidate.asJson,
        "admission" -> target.admission.asJson,
        "anchoredAdmission" -> target.anchoredAdmission.asJson,
        "ancestors" -> target.ancestors.asJson,
        "publicationTarget" -> target.publicationTarget.asJson,
        "publicationPolicy" -> target.publicationPolicy.asJson,
        "failed" -> target.failed.asJson,
        "preGuard" -> target.preGuard.asJson,
        "processPolicy" -> target.processPolicy.asJson)
      val controller = value.hcursor.get[Json]("controller").toOption.get
      val failed = value.hcursor.get[Json]("failed").toOption.get
      val opaque = failed.hcursor.downField("process").focus.filterNot(_.isNull)
        .fold(failed)(process => failed.mapObject(_.add("process", _sorted(process).asJson)))
      _public(value.mapObject(_.add("controller", _sorted(controller).asJson).add("failed", opaque)))
    }
    private val _raw: Decoder[Target] = Decoder.instance { cursor => for {
      schemaversion <- cursor.get[String]("schemaVersion")
      componentinstance <- cursor.get[String]("componentInstance")
      participant <- cursor.get[String]("participant")
      sessionid <- cursor.get[String]("sessionId")
      frontierrevision <- cursor.get[String]("frontierRevision")
      controller <- cursor.get[Json]("controller")
      plan <- cursor.get[O.Plan]("plan")
      base <- cursor.get[O.Snapshot]("base")
      generation <- cursor.get[Long]("generation")
      completed <- cursor.get[Vector[O.ClosedStep]]("completed")
      repairprogress <- cursor.get[GoalPhaseRepairProgress.Snapshot]("repairProgress")
      candidaterecordrevision <- cursor.get[String]("candidateRecordRevision")
      candidate <- cursor.get[C.Payload]("candidate")
      admission <- cursor.get[D.Admission]("admission")
      anchoredadmission <- cursor.get[D.Admission]("anchoredAdmission")
      ancestors <- cursor.get[Vector[R.Ancestor]]("ancestors")
      publicationtarget <- cursor.get[LocalPublication.Target]("publicationTarget")
      publicationpolicy <- cursor.get[String]("publicationPolicy")
      failed <- cursor.get[LocalPublication.Result]("failed")
      preguard <- cursor.get[R.Guard]("preGuard")
      processpolicy <- cursor.get[String]("processPolicy")
    } yield Target(schemaversion, componentinstance, participant, sessionid, frontierrevision, controller, plan, base,
      generation, completed, repairprogress, candidaterecordrevision, candidate, admission, anchoredadmission, ancestors,
      publicationtarget, publicationpolicy, failed, preguard, processpolicy) }
    given Decoder[Target] = Decoder.instance { cursor =>
      val decoded = for {
        controllertext <- cursor.get[String]("controller").left.map(_.message)
        controller <- _opaque(controllertext)
        failed <- cursor.get[Json]("failed").left.map(_.message)
        process <- failed.hcursor.downField("process").focus match {
          case None => Right(Option.empty[Json])
          case Some(value) => value.asString.toRight("Retained process requires canonical opaque JSON text")
            .flatMap(_opaque).map(Some(_))
        }
        normalized = cursor.value.mapObject(_.add("controller", controller).add("failed",
          process.fold(failed)(value => failed.mapObject(_.add("process", value)))))
        value <- _raw.decodeJson(normalized).left.map(_.message)
        _ <- Either.cond(targetValid(value) && value.asJson == cursor.value, (),
          "Prerequisite recovery target is incomplete, crossed or noncanonical")
      } yield value
      decoded.left.map(message => DecodingFailure(message, cursor.history))
    }
  }
  final case class Direction(target: Target, reference: ContextReference, rationale: String)
  final case class Payload(direction: Direction, stages: Vector[R.StageFact], candidate: Option[C.Payload])
  object Payload {
    given Encoder[Payload] = deriveEncoder[Payload].mapJson(_public)
    private val _raw: Decoder[Payload] = deriveDecoder
    given Decoder[Payload] = Decoder.instance(cursor => _raw(cursor).flatMap(value =>
      if (payloadValid(value) && value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Prerequisite recovery fact is incomplete or noncanonical", cursor.history))))
  }
  val codec: WorkflowResultJsonV1.PayloadCodec[Payload] = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhasePrerequisitePublicationRecoveryFact"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> schema.asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("Prerequisite recovery fact is absent")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
      _ <- Either.cond(version == schema && encode(payload) == wire, (), "Prerequisite recovery schema differs")
    } yield payload
  }
  final case class Source(session: F.Session, from: InstanceRecord, controller: O.State, candidate: C.Saved,
    chain: GoalPhaseNativeWorkContinuation.DependencyChain,
    documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String])
  final case class Saved(record: InstanceRecord, payload: Payload)
  final case class Progress(source: Source, direction: Saved, saved: Saved, complete: Boolean, unknown: Boolean) {
    def candidate: Option[C.Saved] = saved.payload.candidate.map(C.Saved(saved.record, _))
  }
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort,
    observation: Option[GoalPhasePrerequisitePublicationUnknownRecovery.ObservationPort] = None) {
    def validateC: Consequence[Unit] = if (evidence != null && observation != null && observation.forall(_ != null)) Consequence.unit
      else F.invalid("Prerequisite recovery requires an independent Human evidence provider")
  }

  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Prerequisite recovery target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def targetC(value: Record): Consequence[Target] = if (value == null) F.invalid("Prerequisite recovery target is absent")
    else value.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(decodeTarget).fold(F.invalid, Consequence.success))

  private def _exact(value: String): Boolean = value != null && value.nonEmpty &&
    value.trim == value && !value.exists(_.isControl)
  private def _rationale(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value)
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _sorted(value: Json): String = io.circe.Printer.noSpaces.copy(sortKeys = true).print(value)
  private def _opaque(value: String): Either[String, Json] = io.circe.parser.parse(value).left.map(_.message).flatMap(json =>
    Either.cond(json.asObject.exists(_.nonEmpty) && _sorted(json) == value, json, "Opaque recovery JSON is noncanonical"))
  /** Core Record omits absent object fields. Use that one public shape in both
    * native persistence and Human bindings, including opaque controller data. */
  private def _public(value: Json): Json = value.arrayOrObject(value,
    values => Json.fromValues(values.map(_public)), fields => Json.fromFields(fields.toVector.collect {
      case (key, item) if !item.isNull => key -> _public(item)
    }))
  private def _json(value: String): Boolean = value != null && io.circe.parser.parse(value).toOption.exists(j =>
    j.asObject.exists(_.nonEmpty) && _sorted(j) == value)

  def confirmedFailure(value: LocalPublication.Result): Boolean = value != null && value.status == "failed" &&
    value.process != null && value.process.exists(raw => WorkflowSbtCapture.decode(raw).toOption.exists(p =>
      WorkflowSbtCapture.exitCode(p).exists(_ != 0))) && value.nextPreparation != null && value.nextPreparation.isEmpty

  /** The complete controller is opaque comparison evidence, never reconstructed
    * from caller data. Native history reconstructs the actual State. */
  private[admission] def controller(value: O.State): Json = _controller(value)
  private def _controller(value: O.State): Json = Json.obj(
    "plan" -> value.plan.asJson, "stage" -> value.stage.toString.asJson, "cursor" -> value.cursor.asJson,
    "generation" -> value.generation.asJson, "repairs" -> value.repairs.asJson, "base" -> value.base.asJson,
    "completed" -> value.completed.asJson, "candidate" -> value.candidate.asJson,
    "checksReceipt" -> value.checksReceipt.asJson, "reviewEvidence" -> value.reviewEvidence.asJson,
    "closureIntent" -> value.closureIntent.asJson, "decision" -> value.decision.asJson,
    "repositoryReceipt" -> value.repositoryReceipt.asJson, "repairDirective" -> value.repairDirective.asJson,
    "decisionSource" -> value.decisionSource.map(_.toString).asJson, "documentDecision" -> value.documentDecision.asJson,
    "ordinaryRepairs" -> value.ordinaryRepairs.asJson, "pendingRepair" -> value.pendingRepair.asJson,
    "phaseReviewedCandidate" -> value.phaseReviewedCandidate.asJson, "phaseRecordingPaths" -> value.phaseRecordingPaths.asJson,
    "candidateParent" -> value.candidateParent.asJson, "stepAdmission" -> value.stepAdmission.asJson,
    "releaseCommit" -> value.releaseCommit.asJson, "repairControl" -> value.repairControl.asJson,
    "policyRepairPending" -> value.policyRepairPending.asJson, "repairObservations" -> value.repairObservations.asJson,
    "stepReviewRequired" -> value.stepReviewRequired.asJson)

  def publicationPolicy(value: LocalPublicationRuntime.Policy): String = _sorted(Json.obj(
    "storeName" -> value.storeName.asJson, "collection" -> value.collection.print.asJson,
    "targets" -> value.targets.asJson, "catalogOutputs" -> value.catalogOutputs.asJson))

  private[admission] def processPolicyC(d: F.Declaration, core: ActionCall.Core): Consequence[String] = _process_policy_c(d, core)
  private def _process_policy_c(d: F.Declaration, core: ActionCall.Core): Consequence[String] = for {
    budget <- GitDiffParameters.resolveC(core.executionContext)
    raw <- Option(core.executionContext.resolvedParameters)
      .flatMap(_.get(GoalPhaseWholePhaseCandidateRecovery.processPlanKey)).map(_.value)
      .map(Consequence.success).getOrElse(F.invalid("Ordinary resolved process-execution plan is absent"))
    plan <- (raw match {
      case ConfigurationValue.StringValue(text) if text != null => io.circe.parser.parse(text).left.map(_.message)
      case value => GoalPhaseWholePhaseCandidateRecovery.configurationJson(value)
    }).fold(F.invalid, Consequence.success)
    _ <- if (plan.asObject.exists(_.nonEmpty)) Consequence.unit else F.invalid("Ordinary process-execution plan is malformed")
    checks <- d.validationPolicy.map(Consequence.success).getOrElse(F.invalid("Step check policy is absent"))
    _ <- checks.validateC
  } yield _sorted(Json.obj("processPlan" -> plan, "maximumDiffBytes" -> budget.asJson,
    "validation" -> checks.asJson))

  def correctionPaths(target: Target): Vector[String] = target.admission.binding.paths.filter(path =>
    target.preGuard.dirty.exists(d => d.path == path && d.worktree != " "))

  def targetValid(value: Target): Boolean = value != null && value.schemaVersion == schema &&
    _exact(value.componentInstance) && _exact(value.participant) && _exact(value.sessionId) &&
    _revision(value.frontierRevision) && value.candidateRecordRevision == value.frontierRevision &&
    value.plan != null && O.validatePlanC(value.plan).isSuccess && value.base != null &&
    value.generation > 0 && value.generation < Long.MaxValue && value.completed != null &&
    value.completed.map(_.step) == value.plan.steps.take(value.completed.size).map(_.identity) &&
    value.completed.size < value.plan.steps.size && value.repairProgress != null &&
    GoalPhaseRepairProgress.validateC(value.repairProgress).isSuccess &&
    value.repairProgress.limit == value.plan.repairLimit && value.controller != null &&
    value.controller.hcursor.get[String]("stage").contains("Validation") &&
    value.controller.hcursor.get[O.Plan]("plan").contains(value.plan) &&
    value.controller.hcursor.get[O.Snapshot]("base").contains(value.base) &&
    value.controller.hcursor.get[Long]("generation").contains(value.generation) &&
    value.controller.hcursor.get[Int]("cursor").contains(value.completed.size) &&
    value.controller.hcursor.get[Vector[O.ClosedStep]]("completed").contains(value.completed) &&
    value.candidate != null && value.candidate.asJson.as[C.Payload].contains(value.candidate) &&
    value.candidate.candidate.nonEmpty && value.candidate.diff.nonEmpty &&
    value.candidate.step == value.plan.steps(value.completed.size).identity &&
    _revision(value.candidate.sourceRevision) && BigInt(value.candidate.sourceRevision) < BigInt(value.frontierRevision) &&
    D.admissionValid(value.admission) && value.candidate.dependencyAdmission.contains(value.admission) &&
    value.candidate.dependencySourceDirection.isEmpty && value.candidate.dependencySourceCapture.isEmpty &&
    value.candidate.dependencySourceGuard.isEmpty && value.candidate.dependencySourceStages.isEmpty &&
    value.candidate.dependencySourceScope.isEmpty && R.guardValid(value.preGuard) && value.candidate.dependency.exists(c => D.captureValid(c) &&
      c.session == value.sessionId && c.plan == ContextReference(value.plan.identity, value.plan.revision) &&
      c.generation == value.generation && c.primaryBase == value.base && c.admission == value.admission &&
      c.candidate.head == value.preGuard.head && c.candidate.tree == value.preGuard.indexTree) &&
    value.candidate.dependencyStaged == value.admission.binding.paths &&
    D.admissionValid(value.anchoredAdmission) &&
    value.anchoredAdmission.copy(acceptedPrefix = value.admission.acceptedPrefix) == value.admission &&
    value.ancestors != null && value.ancestors.size <= value.completed.size && value.ancestors.forall(R.ancestorValid) &&
    value.publicationTarget != null && value.publicationTarget.beforeChecks &&
    value.publicationTarget.dependency.contains(value.admission.binding.identity) &&
    value.publicationTarget.workspace == value.admission.binding.dependency.workspace &&
    _json(value.publicationPolicy) && _json(value.processPolicy) && confirmedFailure(value.failed) &&
    value.failed.request != null && value.failed.request.target == value.publicationTarget &&
    value.failed.request.input.ownerSessionId.contains(value.sessionId) && !value.failed.request.input.explicit &&
    value.failed.request.input.answer.isEmpty && value.failed.request.input.targetIdentity == value.publicationTarget.identity &&
    value.failed.request.input.requestIdentity.startsWith(LocalPublicationRuntime.prerequisiteInput(
      value.sessionId, value.candidate, value.publicationTarget).requestIdentity + ":prerequisite:") &&
    value.failed.request.source.head == value.preGuard.head && value.failed.request.source.tree == value.preGuard.indexTree &&
    value.failed.request.source.dirty != null && value.failed.request.source.dirty.forall(change =>
      Set("", " ")(change.worktree) && value.admission.binding.paths.contains(change.path) &&
        change.renamedFrom.forall(value.admission.binding.paths.contains)) &&
    R.guardValid(value.preGuard) && value.preGuard.root == value.admission.binding.root &&
    value.preGuard.branch == value.admission.binding.branch && value.preGuard.head == value.admission.binding.base.head &&
    value.preGuard.committedTree == value.admission.binding.base.tree && value.preGuard.excludedFiles.isEmpty &&
    value.preGuard.excludedEntries.isEmpty && value.preGuard.selectedFiles.map(_.path) == value.admission.binding.paths &&
    value.preGuard.dirty.forall(d => value.admission.binding.paths.contains(d.path) &&
      d.renamedFrom.forall(value.admission.binding.paths.contains)) && correctionPaths(value).nonEmpty

  def bindingC(component: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (targetValid(target) && component == target.componentInstance && participant == target.participant &&
        choices.contains(choice) && _rationale(rationale) && R.referenceValid(reference))
      Consequence.success(Json.obj("componentInstance" -> component.asJson, "prerequisitePublicationRecovery" -> Json.obj(
        "target" -> target.asJson, "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson))))
    else F.invalid("Prerequisite recovery requires its exact target, Human participant, choice and reference")

  def selectBinding(value: Json): Either[String, Json] =
    if (Option(value).exists(_.hcursor.downField("prerequisitePublicationRecovery").downField("target")
        .get[String]("schemaVersion").contains(GoalPhaseDependencyPublicationSourceAlignment.targetSchema)))
      GoalPhaseDependencyPublicationSourceAlignment.selectBinding(value)
    else if (Option(value).exists(_.hcursor.downField("prerequisitePublicationRecovery").downField("target")
        .get[String]("schemaVersion").toOption.exists(GoalPhasePrerequisitePublicationHostRecovery.isTargetSchema)))
      GoalPhasePrerequisitePublicationHostRecovery.selectBinding(value)
    else if (Option(value).exists(_.hcursor.downField("prerequisitePublicationRecovery").downField("target")
        .get[String]("schemaVersion").contains(GoalPhasePublishedDependencySourceAdoption.artifactSchema)))
      GoalPhasePublishedDependencySourceAdoption.selectBinding(value)
    else if (Option(value).exists(_.hcursor.downField("prerequisitePublicationRecovery").downField("target")
        .get[String]("schemaVersion").contains(GoalPhaseCurrentSourceArtifactAdoption.targetSchema)))
      GoalPhaseCurrentSourceArtifactAdoption.selectBinding(value)
    else if (Option(value).exists(_.hcursor.downField("prerequisitePublicationRecovery").downField("target")
        .get[String]("schemaVersion").toOption.exists(GoalPhasePrerequisitePrimaryRepair.isTargetSchema)))
      GoalPhasePrerequisitePrimaryRepair.selectBinding(value)
    else if (Option(value).exists(_.hcursor.downField("prerequisitePublicationUnknownRecovery").focus.nonEmpty))
      GoalPhasePrerequisitePublicationUnknownRecovery.selectBinding(value)
    else if (Option(value).exists(_.hcursor.downField("prerequisitePublicationRecovery").downField("target")
        .get[String]("schemaVersion").contains(primarySchema))) _select_primary_binding(value)
    else _select_binding(value)
  private def _select_primary_binding(value: Json): Either[String, Json] = for {
    component <- value.hcursor.get[String]("componentInstance").left.map(_.message)
    target <- value.hcursor.downField("prerequisitePublicationRecovery").get[PrimaryTarget]("target").left.map(_.message)
    answer = value.hcursor.downField("prerequisitePublicationRecovery").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    selected <- primaryBindingC(component, target, choice, rationale, participant, reference)
      .map[Either[String, Json]](Right(_)).fold(error => Left(error.toString), identity)
    _ <- Either.cond(selected == value, (), "Primary binding contains undeclared or crossed fields")
  } yield selected
  private def _select_binding(value: Json): Either[String, Json] = for {
    supplied <- Option(value).toRight("Prerequisite recovery binding is absent")
    component <- supplied.hcursor.get[String]("componentInstance").left.map(_.message)
    body = supplied.hcursor.downField("prerequisitePublicationRecovery")
    target <- body.get[Target]("target").left.map(_.message)
    answer = body.downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    selected <- bindingC(component, target, choice, rationale, participant, reference)
      .map[Either[String, Json]](Right(_)).fold(error => Left(error.toString), identity)
    _ <- Either.cond(selected == supplied, (), "Prerequisite recovery binding has undeclared or contradictory fields")
  } yield selected

  private def _eligible_c(source: Source): Consequence[Unit] =
    if (source != null && source.controller.stage == O.Stage.Validation &&
        source.controller.cursor < source.controller.plan.steps.size && source.controller.pendingRepair.isEmpty &&
        source.controller.closureIntent.isEmpty && source.from == source.candidate.record &&
        source.from.currentProgression.contains(ProgressionReference("phase.Validation")) &&
        source.from.lifecycle == Lifecycle.Active && source.from.suspension.isEmpty &&
        source.candidate.payload.dependencyAdmission.nonEmpty && source.candidate.payload.dependency.nonEmpty &&
        source.candidate.payload.dependencySourceDirection.isEmpty && source.chain.acceptedSources.isEmpty)
      Consequence.unit else F.invalid("Prerequisite recovery requires an unsuspended captured ordinary Step before checks")

  private def _target_c(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Target] = for {
    _ <- _eligible_c(source)
    _ <- if (source.from == source.session.current) Consequence.unit else F.invalid("Prerequisite recovery frontier is stale")
    _ <- GoalPhaseNativeValidation.unstartedPrerequisiteC(source.session, d, core, source.candidate, source.controller,
      source.from, source.documentPolicy, source.recordingPaths, source.chain)
    admission = source.candidate.payload.dependencyAdmission.get
    _ <- source.chain.captureC(source.candidate.payload.dependency.get)
    anchor <- source.chain.anchorC(admission.binding)
    anchored = admission.copy(acceptedPrefix = anchor)
    _ <- source.chain.resolveC(anchored)
    publication <- LocalPublicationRuntime.failedPrerequisiteC(source.session.current.identity.value, d, core, source.candidate.payload)
    guard <- R.observeC(core, admission.binding, Vector.empty)
    policy <- _process_policy_c(d, core)
    target = Target(schema, core.component.get.core.instanceId.canonicalKey, source.session.participantIdentity,
      source.from.identity.value, source.from.revision.value.toString, _controller(source.controller), source.controller.plan,
      source.controller.base, source.controller.generation, source.controller.completed, GoalPhaseRepairProgress.snapshot(source.controller),
      source.candidate.record.revision.value.toString, source.candidate.payload, admission, anchored,
      source.chain.accepted.map(a => R.Ancestor(a.capture, a.closureRevision)), publication._2.request.target,
      publicationPolicy(publication._1), publication._2, guard, policy)
    _ <- if (targetValid(target)) Consequence.unit else F.invalid("Prerequisite correction crosses its original captured source or ownership")
  } yield target

  def describeC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult] =
    _target_c(source, d, core).map(target => V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult(
      target.sessionId, "direction-required", message, target.componentInstance, targetRecord(target), choices, recoveryOperation))

  def recoverC(source: Source, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhasePrerequisitePublicationRecoveryInput): Consequence[V.GoalPhasePrerequisitePublicationRecoveryResult] = for {
    supplied <- targetC(input.target)
    actual <- _target_c(source, d, core)
    _ <- if (supplied == actual && input.sessionId == actual.sessionId && input.reference != null) Consequence.unit
      else F.invalid("Prerequisite recovery target changed or belongs to another Session")
    installation <- d.prerequisitePublicationRecovery.map(Consequence.success).getOrElse(
      Consequence.securityPermissionDenied("Prerequisite recovery has no independent Human provider"))
    _ <- installation.validateC
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(actual.componentInstance, actual, input.choice, input.rationale, source.session.participantIdentity, reference)
    _ <- installation.evidence.admitC(core, reference, binding)
    _ <- if (input.choice == "Defer") Consequence.unit else for {
      adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
      existing <- adapter.loadRetainedC(_reference(source.from, "direction"))(using core.executionContext)
      _ <- if (existing.isEmpty) Consequence.unit else F.invalid("Prerequisite recovery direction already exists")
      _ <- _save_c(source, d, core, "direction", source.from, Payload(Direction(actual, reference, input.rationale), Vector.empty, None))
    } yield ()
  } yield V.GoalPhasePrerequisitePublicationRecoveryResult(input.sessionId, true,
    if (input.choice == "Defer") "deferred" else "corrected-source-adopted", message)

  private def _reference(from: InstanceRecord, key: String) = WorkflowMechanicalTransition.Reference(
    from.identity.value + ":" + causationPrefix + from.revision.value + ":" + key, "1")
  private def _cause(from: InstanceRecord, key: String): String = causationPrefix + from.revision.value +
    (if (key == "direction") "" else ":" + key)
  private def _save_c(source: Source, d: F.Declaration, core: ActionCall.Core, key: String,
    from: InstanceRecord, payload: Payload): Consequence[Saved] = for {
    _ <- if (payloadValid(payload) && from.suspension.isEmpty &&
        from.currentProgression.contains(ProgressionReference("phase.Validation"))) Consequence.unit
      else F.invalid("Prerequisite recovery effect has no exact unsuspended Validation source")
    _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits("Validation", "prerequisite-publication-recovery", "Validation"))
      Consequence.unit else F.invalid("Prerequisite recovery self-transition is absent from CML")
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(source.from, key), from,
      from.revision, F.history(from, "phase.Validation", _cause(source.from, key)), TypedValue(codec.typeIdentity, payload)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield Saved(saved.receipt.next, saved.receipt.payload.value)

  private def _adjacent_c(source: Source, before: InstanceRecord, saved: Saved, key: String): Consequence[Unit] = for {
    actual <- GoalPhaseNativeHistory.prefixC(source.session.current, saved.record.revision)
    _ <- if (actual == saved.record && saved.record.identity == before.identity && saved.record.definition == before.definition &&
        saved.record.revision == before.revision.next && saved.record.history.dropRight(1) == before.history &&
        saved.record.currentProgression == before.currentProgression && saved.record.lifecycle == Lifecycle.Active &&
        saved.record.suspension.isEmpty && saved.record.causation.value == _cause(source.from, key)) Consequence.unit
      else F.invalid("Prerequisite recovery record crosses its actual native predecessor")
  } yield ()

  private def _load_c(source: Source, core: ActionCall.Core, key: String): Consequence[Option[Saved]] = for {
    adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- adapter.loadRetainedC(_reference(source.from, key))(using core.executionContext)
  } yield retained.map(value => Saved(value.receipt.next, value.receipt.payload.value))

  private def _historical_c(source: Source, d: F.Declaration, core: ActionCall.Core, direction: Saved): Consequence[Unit] = for {
    _ <- _eligible_c(source)
    _ <- _adjacent_c(source, source.from, direction, "direction")
    target = direction.payload.direction.target
    admission = source.candidate.payload.dependencyAdmission.get
    _ <- source.chain.captureC(source.candidate.payload.dependency.get)
    anchor <- source.chain.anchorC(admission.binding)
    _ <- source.chain.resolveC(target.anchoredAdmission)
    policy <- d.localPublication.map(Consequence.success).getOrElse(F.invalid("Retained prerequisite publication policy is absent"))
    _ <- if (targetValid(target) && target.componentInstance == core.component.get.core.instanceId.canonicalKey &&
        target.participant == source.session.participantIdentity && target.sessionId == source.from.identity.value &&
        target.frontierRevision == source.from.revision.value.toString && target.controller == _controller(source.controller) &&
        target.plan == source.controller.plan && target.base == source.controller.base && target.generation == source.controller.generation &&
        target.completed == source.controller.completed && target.repairProgress == GoalPhaseRepairProgress.snapshot(source.controller) &&
        target.candidateRecordRevision == source.candidate.record.revision.value.toString && target.candidate == source.candidate.payload &&
        target.admission == admission && target.anchoredAdmission == admission.copy(acceptedPrefix = anchor) &&
        target.ancestors == source.chain.accepted.map(a => R.Ancestor(a.capture, a.closureRevision)) &&
        target.publicationPolicy == publicationPolicy(policy) &&
        direction.payload == Payload(direction.payload.direction, Vector.empty, None)) Consequence.unit
      else F.invalid("Retained prerequisite direction changed its original controller, candidate, binding, prefix or host policy")
    _ <- LocalPublicationRuntime.retainedFailureC(core, policy, target.failed)
    _ <- GoalPhaseNativeValidation.unstartedPrerequisiteC(source.session, d, core, source.candidate, source.controller,
      source.from, source.documentPolicy, source.recordingPaths, source.chain)
  } yield ()

  def payloadValid(value: Payload): Boolean = value != null && value.direction != null && targetValid(value.direction.target) &&
    R.referenceValid(value.direction.reference) && _rationale(value.direction.rationale) && value.stages != null &&
    value.candidate != null && value.stages.forall(R.stageFactValid) && {
      val target = value.direction.target
      val paths = correctionPaths(target)
      val linked = value.stages.map(_.path) == paths.take(value.stages.size) && value.stages.zipWithIndex.forall { (fact, index) =>
        fact.before == (if (index == 0) target.preGuard else value.stages(index - 1).after) &&
          !fact.after.dirty.exists(d => d.path == fact.path && d.worktree != " ")
      }
      linked && value.candidate.forall { candidate =>
        val guard = value.stages.lastOption.map(_.after).getOrElse(target.preGuard)
        value.stages.size == paths.size && candidate.dependency.exists(capture => D.captureValid(capture) &&
          capture.session == target.sessionId && capture.plan == ContextReference(target.plan.identity, target.plan.revision) &&
          capture.step == target.candidate.step && capture.generation == target.generation && capture.primaryBase == target.base &&
          capture.admission == target.anchoredAdmission && capture.sourceRevision == candidate.sourceRevision &&
          capture.candidate == O.Snapshot(guard.head, guard.indexTree) &&
          candidate == target.candidate.copy(sourceRevision = candidate.sourceRevision,
            dependencyAdmission = Some(target.anchoredAdmission), dependency = Some(capture), dependencyOrigin = None))
      }
    }

  /** Reconstruct immutable effects without observing today's source bytes. */
  def retainedC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Progress] = for {
    direction <- _load_c(source, core, "direction").flatMap(_.map(Consequence.success)
      .getOrElse(F.invalid("Prerequisite recovery history has no actual direction")))
    _ <- _historical_c(source, d, core, direction)
    initial = Progress(source, direction, direction, complete = false, unknown = false)
    progress <- reconstructC(initial, key => _load_c(source, core, key),
      (before, saved, key) => _adjacent_c(source, before, saved, key), source.chain.captureC)
    process <- _process_policy_c(d, core)
    _ <- historicalProcessC(progress, process, candidate => d.localPublication.map(policy =>
      LocalPublicationRuntime.correctedPrimaryHistoryC(core, policy, direction.payload.direction.target, candidate))
      .getOrElse(F.invalid("Retained prerequisite publication policy is absent")))
  } yield progress

  /** Only a fully authenticated completed correction can be read across later
    * process grants. This never admits another effect under its old Direction. */
  private[admission] def historicalProcessC(progress: Progress, current: String,
    canonicalPrimary: C.Payload => Consequence[Unit]): Consequence[Unit] =
    if (progress.direction.payload.direction.target.processPolicy == current) Consequence.unit
    else if (progress.complete && !progress.unknown &&
        progress.saved.payload.direction == progress.direction.payload.direction && payloadValid(progress.saved.payload))
      progress.saved.payload.candidate.map(canonicalPrimary)
        .getOrElse(F.invalid("Changed process grants require a complete retained correction candidate"))
    else F.invalid("Incomplete or UNKNOWN prerequisite correction cannot cross changed process grants")

  /** Fold only facts whose historical source has already been authenticated. */
  private[admission] def reconstructC(initial: Progress, loadC: String => Consequence[Option[Saved]],
    adjacentC: (InstanceRecord, Saved, String) => Consequence[Unit],
    captureC: D.Capture => Consequence[Unit]): Consequence[Progress] = {
    val direction = initial.direction
    for {
    staged <- correctionPaths(direction.payload.direction.target).zipWithIndex.foldLeft(Consequence.success(initial)) {
      case (prior, (path, index)) => prior.flatMap { progress =>
        val key = "stage-" + index
        for {
          intent <- loadC(key + "-intent")
          result <- loadC(key + "-result")
          next <- (intent, result) match {
            case (None, None) => Consequence.success(progress)
            case (None, Some(_)) => F.invalid("Prerequisite stage result has no intent")
            case (Some(prepared), actual) => for {
              _ <- if (!progress.unknown && progress.saved.payload.stages.size == index) Consequence.unit
                else F.invalid("Prerequisite stage skips an incomplete or unknown predecessor")
              _ <- adjacentC(progress.saved.record, prepared, key + "-intent")
              _ <- if (prepared.payload == progress.saved.payload) Consequence.unit else F.invalid("Stage intent rewrites its direction")
              next <- actual match {
                case None => Consequence.success(progress.copy(saved = prepared, unknown = true))
                case Some(saved) => for {
                  _ <- adjacentC(prepared.record, saved, key + "-result")
                  _ <- if (saved.payload.stages.size == index + 1 && saved.payload.stages.last.path == path &&
                      saved.payload == prepared.payload.copy(stages = saved.payload.stages) && payloadValid(saved.payload)) Consequence.unit
                    else F.invalid("Stage result rewrites its guarded path prefix")
                } yield progress.copy(saved = saved)
              }
            } yield next
          }
        } yield next
      }
    }
    intent <- loadC("observation-intent")
    result <- loadC("observation-result")
    progress <- (intent, result) match {
      case (None, None) => Consequence.success(staged)
      case (None, Some(_)) => F.invalid("Prerequisite observation result has no intent")
      case (Some(prepared), actual) => for {
        _ <- if (!staged.unknown && staged.saved.payload.stages.size == correctionPaths(direction.payload.direction.target).size)
          Consequence.unit else F.invalid("Prerequisite observation skips an incomplete stage")
        _ <- adjacentC(staged.saved.record, prepared, "observation-intent")
        _ <- if (prepared.payload == staged.saved.payload) Consequence.unit else F.invalid("Observation intent rewrites its guarded stages")
        next <- actual match {
          case None => Consequence.success(staged.copy(saved = prepared, unknown = true))
          case Some(saved) => for {
            _ <- adjacentC(prepared.record, saved, "observation-result")
            candidate <- saved.payload.candidate.map(Consequence.success).getOrElse(F.invalid("Recovery result omitted its actual candidate"))
            _ <- if (saved.payload == prepared.payload.copy(candidate = Some(candidate)) && payloadValid(saved.payload) &&
                candidate.sourceRevision == direction.record.revision.value.toString) Consequence.unit
              else F.invalid("Recovery result changed its direction, stages or actual source revision")
            _ <- captureC(candidate.dependency.get)
          } yield staged.copy(saved = saved, complete = true)
        }
      } yield next
    }
  } yield progress
  }

  def advanceC(progress: Progress, d: F.Declaration, core: ActionCall.Core,
    acknowledge: String => Consequence[Unit]): Consequence[Saved] = {
    val source = progress.source
    val target = progress.direction.payload.direction.target
    val binding = target.admission.binding
    def _observe_ = R.observeC(core, binding, Vector.empty)
    def _git_[A](operation: GitOperation[A]): Consequence[A] =
      WorkflowManagedProcessExecution.executeGitC(core, operation, binding.dependency.workspace).flatMap(_.valueC)
    for {
      _ <- if (!progress.unknown && !progress.complete && source.session.current == progress.saved.record) Consequence.unit
        else F.invalid("Prerequisite recovery cannot replay an unknown, complete or stale effect")
      policy <- _process_policy_c(d, core)
      _ <- if (policy == target.processPolicy) Consequence.unit else F.invalid("Prerequisite recovery process grants changed")
      staged <- correctionPaths(target).zipWithIndex.drop(progress.saved.payload.stages.size)
        .foldLeft(Consequence.success(progress.saved)) { case (prior, (path, index)) => for {
          previous <- prior
          before <- _observe_
          expected = previous.payload.stages.lastOption.map(_.after).getOrElse(target.preGuard)
          _ <- if (before == expected) Consequence.unit else F.invalid("Approved correction changed before staging")
          intent <- _save_c(source, d, core, "stage-" + index + "-intent", previous.record, previous.payload)
          _ <- acknowledge("goal-prerequisite-recovery-stage-prepared:" + path)
          selection <- GitSelectionId.parseC("goal-path-" + (binding.paths.indexOf(path) + 1))
          _ <- _git_(GitOperation.StageSelectedAll(selection))
          after <- _observe_
          oldtree <- GitObjectId.parseC(before.indexTree)
          newtree <- GitObjectId.parseC(after.indexTree)
          changes <- _git_(GitOperation.TreeChanges(oldtree, newtree))
          fact = R.StageFact(path, before, after, changes.map(R.change))
          _ <- if (R.stageFactValid(fact) && !after.dirty.exists(d => d.path == path && d.worktree != " ")) Consequence.unit
            else F.invalid("Prerequisite stage changed unselected bytes, entries or ownership")
          _ <- changes.foldLeft(Consequence.unit)((prior, change) => prior.flatMap(_ => change.newBlob match {
            case None => Consequence.unit
            case Some(blob) => _git_(GitOperation.BlobContent(blob)).flatMap(content =>
              if (after.selectedFiles.find(_.path == path).contains(R.fileIdentity(path, Some(content.bytes)))) Consequence.unit
              else F.invalid("Corrected index blob differs from the approved worktree bytes"))
          }))
          saved <- _save_c(source, d, core, "stage-" + index + "-result", intent.record,
            previous.payload.copy(stages = previous.payload.stages :+ fact))
          _ <- acknowledge("goal-prerequisite-recovery-stage-recorded:" + path)
        } yield saved }
      guard <- _observe_
      expected = staged.payload.stages.lastOption.map(_.after).getOrElse(target.preGuard)
      _ <- if (guard == expected) Consequence.unit else F.invalid("Approved correction changed before dependency observation")
      intent <- _save_c(source, d, core, "observation-intent", staged.record, staged.payload)
      _ <- acknowledge("goal-prerequisite-recovery-observation-prepared")
      admission = target.anchoredAdmission
      before <- N.repositoryC(core, admission, true, Some(source.chain))
      tree <- _git_(GitOperation.IndexTree)
      baseline <- GitObjectId.parseC(N.baseline(admission))
      paths <- _git_(GitOperation.PendingPaths(baseline))
      diff <- _git_(GitOperation.PendingDiff(baseline))
      after <- N.repositoryC(core, admission, true, Some(source.chain))
      aftertree <- _git_(GitOperation.IndexTree)
      finalguard <- _observe_
      _ <- if (before == after && tree == aftertree && finalguard == expected && tree.value == expected.indexTree && !diff.isBinary)
        Consequence.unit else F.invalid("Corrected dependency changed during actual incremental observation")
      revision = progress.direction.record.revision.value.toString
      capture = D.Capture(target.sessionId, ContextReference(target.plan.identity, target.plan.revision), binding.step,
        target.generation, revision, target.base, admission, O.Snapshot(before.head, tree.value), paths.map(_.value), diff.text)
      _ <- source.chain.captureC(capture)
      candidate = target.candidate.copy(sourceRevision = revision, dependencyAdmission = Some(admission),
        dependency = Some(capture), dependencyOrigin = None)
      saved <- _save_c(source, d, core, "observation-result", intent.record, staged.payload.copy(candidate = Some(candidate)))
      _ <- acknowledge("goal-prerequisite-recovery-observation-recorded")
    } yield saved
  }

  /** The fixed host program, rather than ambient environment or caller argv,
    * explicitly selects the dependency ProjectRef root. */
  def primaryProgramMatches(policy: String, target: LocalPublication.Target, consumption: R.ConsumptionRecord): Boolean =
    target != null && R.consumptionValid(consumption) && target.workspace == consumption.primaryWorkspace &&
      io.circe.parser.parse(Option(policy).getOrElse("")).toOption.exists(_.hcursor.downField("processPlan")
        .get[Vector[Json]]("sbtPrograms").toOption.exists(programs => {
          val selected = programs.filter(p => p.hcursor.get[String]("target").contains(target.workspace) &&
            p.hcursor.get[String]("operation").contains("publish-local"))
          selected.size == 1 && selected.head.hcursor.get[Map[String, String]]("environment").toOption.exists(_.exists {
            case (key, value) => _exact(key) && value == consumption.sourceRoot
          })
        }))

  private def _primary_static_c(source: PrimarySource, d: F.Declaration, core: ActionCall.Core): Consequence[Unit] = for {
    _ <- if (source != null && source.original != null && W.directionValid(source.original.direction) &&
        source.original.direction.choice.contains("AcceptExistingArtifactsWithUnknownEffects") &&
        source.native.controller.stage == O.Stage.Validation && source.native.controller.pendingRepair.isEmpty &&
        source.native.controller.closureIntent.isEmpty && source.native.from.suspension.isEmpty &&
        source.native.from.currentProgression.contains(ProgressionReference("phase.Validation")) &&
        source.capture.forall(p => p.complete && !p.unknown && p.saved.contains(source.native.candidate))) Consequence.unit
      else F.invalid("Primary continuation requires its exact post-artifact unstarted Validation lineage")
    original <- GoalPhaseNativeHistory.prefixC(source.native.session.current, source.original.record.revision)
    _ <- if (original == source.original.record && source.requestingResult == source.original.direction.target.requestingResult &&
        source.original.record.revision.value < source.native.from.revision.value + 1L) Consequence.unit
      else F.invalid("Primary continuation lost its actual original UNKNOWN Direction")
    policy <- d.localPublication.map(Consequence.success).getOrElse(F.invalid("Primary publication policy is absent"))
    _ <- LocalPublicationRuntime.retainedFailureC(core, policy, source.original.direction.target.unknown)
  } yield ()

  private def _primary_target_c(source: PrimarySource, d: F.Declaration, core: ActionCall.Core,
    observe: Boolean, retained: Option[PrimaryTarget] = None, sourceselection: Boolean = false): Consequence[PrimaryTarget] = for {
    _ <- _primary_static_c(source, d, core)
    n = source.native
    old = source.original.direction.target
    _ <- if (!observe) Consequence.unit else GoalPhaseNativeValidation.unstartedPrerequisiteC(n.session, d, core, n.candidate,
      n.controller, n.candidate.record, n.documentPolicy, n.recordingPaths, n.chain)
    policy <- d.localPublication.map(Consequence.success).getOrElse(F.invalid("Primary publication policy is absent"))
    _ <- LocalPublicationRuntime.validateC(policy)
    targets = policy.targets.filter(t => t.beforeChecks && t.dependency.forall(Some(old.admission.binding.identity).contains))
    _ <- if (targets.size == 2 && targets.head == old.unknown.request.target && targets(1).dependency.isEmpty &&
        targets(1).workspace == old.admission.binding.requester.workspace) Consequence.unit
      else F.invalid("Primary continuation requires exactly the ordered dependency skip and next primary target")
    process <- if (!observe && retained.nonEmpty) Consequence.success(retained.get.processPolicy) else _process_policy_c(d, core)
    publication = if (!observe && retained.nonEmpty) retained.get.publicationPolicy else publicationPolicy(policy)
    failed <- retained.map(t => Consequence.success(t.failed)).getOrElse(
      LocalPublicationRuntime.primaryPrerequisiteC(n.from.identity.value, d, core, old.candidate, n.candidate.payload,
        targets(1), source.directions.lastOption.filter(_.fact.choice == "AuthorizePrimaryPublicationOnce").map(primaryPermit)))
    _ <- LocalPublicationRuntime.retainedFailureC(core, policy, failed)
    capture = source.capture.flatMap(_.saved).flatMap(_.payload.dependencySourceCapture)
    guard <- if (!observe) retained.map(t => Consequence.success(t.dependencyGuard)).getOrElse(
        Consequence.success(capture.map(_.postStageGuard).getOrElse(old.guard)))
      else primaryDependencyGuardC(old.guard, capture, sourceselection,
        R.observeC(core, old.admission.binding, old.guard.excludedFiles.map(_.path)),
        c => R.freshnessC(core, c).map(_ => c.postStageGuard))
    primary <- if (observe) LocalPublicationRuntime._port(core, policy).sourceC(targets(1))
      else retained.map(t => Consequence.success(t.primarySource)).getOrElse(Consequence.success(failed.request.source))
    phase = if (capture.isEmpty) PrimaryPhase.SourceRequired else if (source.directions.isEmpty) PrimaryPhase.ArtifactDecision
      else if (confirmedFailure(failed)) PrimaryPhase.PrimaryDecision else PrimaryPhase.PrimaryUncertain
    target = PrimaryTarget(primarySchema, phase, core.component.get.core.instanceId.canonicalKey, n.session.participantIdentity,
      n.from.identity.value, n.from.revision.value.toString, _sorted(controller(n.controller)), n.controller.plan, n.controller.base,
      n.controller.generation, n.controller.completed, GoalPhaseRepairProgress.snapshot(n.controller), n.candidate.record.revision.value.toString,
      n.candidate.payload, source.original.direction, ContextReference(source.original.record.identity.value, source.original.record.revision.value.toString),
      source.requestingResult, targets, publication, process, failed, primary, guard, capture, source.directions.map(_.fact), source.approvals)
    _ <- if (primaryTargetValid(target) && (!observe || n.from == n.session.current)) Consequence.unit
      else F.invalid("Primary continuation has stale or crossed candidate, failure, source, programs or native facts")
  } yield target

  def primaryForC(source: PrimarySource, d: F.Declaration, core: ActionCall.Core): Consequence[PrimaryTarget] =
    _primary_target_c(source, d, core, observe = true)
  def primarySourceForC(source: PrimarySource, d: F.Declaration, core: ActionCall.Core): Consequence[PrimaryTarget] =
    _primary_target_c(source, d, core, observe = true, sourceselection = true)
  private[admission] def primaryDependencyGuardC(historical: R.Guard, capture: Option[R.Capture],
    sourceSelection: Boolean, currentOriginal: => Consequence[R.Guard],
    currentCaptured: R.Capture => Consequence[R.Guard]): Consequence[R.Guard] = capture match {
    case None if sourceSelection => if (R.guardValid(historical)) Consequence.success(historical)
      else F.invalid("Source selection lost its authenticated historical dependency guard")
    case None => currentOriginal
    case Some(value) => currentCaptured(value)
  }
  def checkPrimaryOriginC(source: PrimarySource, d: F.Declaration, core: ActionCall.Core,
    target: PrimaryTarget): Consequence[Unit] = _primary_target_c(source, d, core, observe = false, retained = Some(target)).flatMap(actual =>
      if (actual == target && target.phase == PrimaryPhase.SourceRequired) Consequence.unit
      else F.invalid("Primary source origin differs from its exact historical native frontier"))
  def describePrimaryC(source: PrimarySource, d: F.Declaration, core: ActionCall.Core):
    Consequence[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult] = primaryForC(source, d, core).map(target =>
      V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult(target.sessionId,
        if (target.phase == PrimaryPhase.PrimaryUncertain) "observation-required" else "direction-required",
        primaryMessage(target), target.componentInstance, primaryTargetRecord(target), primaryChoices(target), recoveryOperation))

  private def _primary_reference(from: InstanceRecord) = WorkflowMechanicalTransition.Reference(
    from.identity.value + ":" + primaryCausationPrefix + from.revision.value + ":direction", "1")
  private def _save_primary_c(source: PrimarySource, d: F.Declaration, core: ActionCall.Core, fact: PrimaryFact): Consequence[PrimarySaved] = for {
    _ <- if (primaryFactValid(fact) && fact.target.frontierRevision == source.native.from.revision.value.toString &&
        domain.statemachine.GoalPhaseRunLifecycle.permits("Validation", "prerequisite-publication-recovery", "Validation")) Consequence.unit
      else F.invalid("Primary operation fact has no exact Validation predecessor")
    adapter <- source.native.session.storage.bindMechanicalC(F.advanceOperation, primaryCodec)
    saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _primary_reference(source.native.from),
      source.native.from, source.native.from.revision, F.history(source.native.from, "phase.Validation",
        primaryCausationPrefix + source.native.from.revision.value), TypedValue(primaryCodec.typeIdentity, fact)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield PrimarySaved(saved.receipt.next, saved.receipt.payload.value)

  private def _inherited_c(source: PrimarySource, fact: PrimaryFact): Consequence[Unit] =
    if (fact.inheritedFrom.isEmpty) Consequence.unit
    else source.capture.filter(p => p.complete && !p.unknown).map(_.direction).filter(s =>
      s.direction.choice.contains(R.combinedPrimaryChoice) && fact.inheritedFrom.contains(s.direction.directive) &&
        s.direction.target.primaryFailureOrigin.exists(origin => origin.failed == fact.target.failed &&
          origin.originalDirection == fact.target.originalDirection && origin.targets == fact.target.targets &&
          origin.primarySource == fact.target.primarySource && origin.publicationPolicy == fact.target.publicationPolicy &&
          origin.processPolicy == fact.target.processPolicy) &&
        fact.reference == ContextReference(source.native.from.identity.value, source.native.from.revision.next.value.toString))
      .fold[Consequence[Unit]](F.invalid("Inherited operation lacks its authenticated concrete combined source Direction"))(_ => Consequence.unit)

  def retainedPrimaryC(source: PrimarySource, d: F.Declaration, core: ActionCall.Core): Consequence[PrimarySaved] = for {
    adapter <- source.native.session.storage.bindMechanicalC(F.advanceOperation, primaryCodec)
    saved <- adapter.loadRetainedC(_primary_reference(source.native.from))(using core.executionContext)
      .flatMap(_.map(v => Consequence.success(PrimarySaved(v.receipt.next, v.receipt.payload.value)))
        .getOrElse(F.invalid("Primary operation history omitted its actual fact")))
    actual <- GoalPhaseNativeHistory.prefixC(source.native.session.current, saved.record.revision)
    before = source.native.from
    _ <- if (actual == saved.record && saved.record.identity == before.identity && saved.record.definition == before.definition &&
        saved.record.revision == before.revision.next && saved.record.history.dropRight(1) == before.history &&
        saved.record.currentProgression == before.currentProgression && saved.record.lifecycle == Lifecycle.Active &&
        saved.record.suspension.isEmpty && saved.record.causation.value == primaryCausationPrefix + before.revision.value)
      Consequence.unit else F.invalid("Primary operation fact crosses its actual adjacent native predecessor")
    target <- _primary_target_c(source, d, core, observe = false, retained = Some(saved.fact.target))
    _ <- if (target == saved.fact.target) Consequence.unit else F.invalid("Primary operation rewrites retained native provenance")
    _ <- _inherited_c(source, saved.fact)
  } yield saved

  def recoverPrimaryC(source: PrimarySource, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhasePrerequisitePublicationRecoveryInput): Consequence[V.GoalPhasePrerequisitePublicationRecoveryResult] = for {
    supplied <- primaryTargetC(input.target)
    actual <- primaryForC(source, d, core)
    _ <- if (supplied == actual && input.sessionId == actual.sessionId && input.reference != null &&
        primaryChoices(actual).contains(input.choice)) Consequence.unit else F.invalid("Primary answer has a stale phase or target")
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- primaryBindingC(actual.componentInstance, actual, input.choice, input.rationale, actual.participant, reference)
    installation <- d.prerequisitePublicationRecovery.map(Consequence.success).getOrElse(F.invalid("Independent primary Human provider is absent"))
    _ <- installation.validateC
    _ <- installation.evidence.admitC(core, reference, binding)
    _ <- if (input.choice == "Defer") Consequence.unit else _save_primary_c(source, d, core,
      PrimaryFact(actual, reference, input.rationale, input.choice)).map(_ => ())
  } yield V.GoalPhasePrerequisitePublicationRecoveryResult(input.sessionId, input.choice != "Defer",
    if (input.choice == "Defer") "deferred" else "primary-operation-directed", primaryMessage(actual))

  def inheritedPending(source: PrimarySource): Boolean = source.capture.exists(p => p.complete && !p.unknown &&
    p.direction.direction.choice.contains(R.combinedPrimaryChoice)) && source.directions.size < 2
  def inheritedPendingC(source: PrimarySource, d: F.Declaration, core: ActionCall.Core): Consequence[Boolean] =
    if (!inheritedPending(source)) Consequence.success(false) else primaryForC(source, d, core).map(target =>
      primaryChoices(target).find(_ != "Defer").exists(choice => primaryFactValid(PrimaryFact(target,
        ContextReference(source.native.from.identity.value, source.native.from.revision.next.value.toString),
        "Derived ordered operation under the authenticated combined source Direction", choice,
        Some(source.capture.get.direction.direction.directive)))))
  def advanceInheritedC(source: PrimarySource, d: F.Declaration, core: ActionCall.Core): Consequence[PrimarySaved] = for {
    _ <- if (inheritedPending(source)) Consequence.unit else F.invalid("No uncovered operation inherits combined source authority")
    target <- primaryForC(source, d, core)
    choice <- primaryChoices(target).find(_ != "Defer").map(Consequence.success).getOrElse(F.invalid("Combined authority no longer covers this operation"))
    fact = PrimaryFact(target, ContextReference(source.native.from.identity.value, source.native.from.revision.next.value.toString),
      "Derived ordered operation under the authenticated combined source Direction", choice,
      Some(source.capture.get.direction.direction.directive))
    _ <- _inherited_c(source, fact)
    saved <- _save_primary_c(source, d, core, fact)
  } yield saved
  def primaryPermit(saved: PrimarySaved): LocalPublication.ConfirmedFailureSuccessor =
    LocalPublication.ConfirmedFailureSuccessor(ContextReference(saved.record.identity.value, saved.record.revision.value.toString),
      saved.fact.target.failed, saved.fact.target.primarySource, ContextReference(saved.record.identity.value,
        saved.fact.target.candidateRecordRevision), saved.fact.target.processPolicy, saved.fact.target.publicationPolicy)
  def primaryArtifact(saved: PrimarySaved): W.ExistingArtifactAdoption = W.ExistingArtifactAdoption(
    ContextReference(saved.record.identity.value, saved.record.revision.value.toString), saved.fact.target.originalDirection.target.unknown,
    LocalPublication.Source(saved.fact.target.dependencyGuard.head, saved.fact.target.dependencyGuard.indexTree,
      saved.fact.target.dependencyGuard.dirty.map(d => LocalPublication.Change(d.path, d.index, d.worktree, d.renamedFrom))),
    saved.fact.target.dependencyGuard)

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhasePrerequisitePublicationRecoveryOperation
      else SmWorkflowComponent.GoalPhaseServiceService.RecoverGoalPhasePrerequisitePublicationOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") ||
          request.operation != (if (describe) describeOperation else recoveryOperation)) F.invalid("Prerequisite recovery Operation identity differs")
      else if (describe) descriptionSourceC(request).map(source => Action(request, "", true, Some(source), port))
      else resourceInputC(request).map(source => Action(request, "", false, Some(source), port))
  }
  private final case class Action(request: Request, sessionId: String, describe: Boolean,
    recovery: Option[NativeWorkflowResourceInput.Source[Record]], port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = if (describe) for {
        record <- NativeWorkflowResourceInput.readC(recovery.get, reference => read_resource_text(reference,
          Some(java.nio.charset.StandardCharsets.UTF_8)).flatMap(validateBusinessTextC))
        input <- descriptionRecordC(record)
        publication <- descriptionObservationC(input)
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(input.sessionId))
        selected = core.copy(executionContext = context)
        result <- port.describePrerequisitePublicationRecoveryC(input.sessionId, selected, publication).map(NativeWorkflowOperationOutput.response(_))
      } yield result else for {
        record <- NativeWorkflowResourceInput.readC(recovery.get, reference => read_resource_text(reference,
          Some(java.nio.charset.StandardCharsets.UTF_8)).flatMap(validateBusinessTextC))
        input <- recoveryRecordC(record)
        _ <- recoveryInputC(input)
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(input.sessionId))
        result <- port.recoverPrerequisitePublicationC(input, core.copy(executionContext = context)).map(NativeWorkflowOperationOutput.response(_))
      } yield result
    }
  }
  /** Exactly one transport. Legacy session-only calls remain the same public
    * Operation; structured proposals use the existing Resource/UoW reader. */
  private[admission] def descriptionSourceC(request: Request): Consequence[NativeWorkflowResourceInput.Source[Record]] = for {
    _ <- if (request != null && DevelopmentOperationalView.uniqueFields(request.toRecord)) Consequence.unit
      else F.invalid("Description input contains duplicate fields")
    session <- NativeWorkflowOperationInput.optionalTextC(request, "sessionId")
    structured = request.toRecord.getAny("input").nonEmpty || request.toRecord.getAny("inputResource").nonEmpty
    source <- session match {
      case Some(value) if !structured => Consequence.success(NativeWorkflowResourceInput.Inline(Record.data("sessionId" -> value)))
      case Some(_) => F.invalid("Prerequisite description requires exactly one of sessionId, input or inputResource")
      case None => resourceInputC(request)
    }
  } yield source
  private[admission] def descriptionRecordC(record: Record): Consequence[V.GoalPhasePrerequisitePublicationRecoveryDescriptionInput] = for {
    json <- W.jsonC(record)
    keys <- json.asObject.map(v => Consequence.success(v.keys.toSet)).getOrElse(F.invalid("Description input requires an object"))
    _ <- if (keys == Set("sessionId") || keys == Set("sessionId", "publicationObservation")) Consequence.unit
      else F.invalid("Description input contains missing or undeclared fields")
    value <- businessRecordC[V.GoalPhasePrerequisitePublicationRecoveryDescriptionInput](record, keys)
    _ <- if (_exact(value.sessionId) && value.publicationObservation != null) Consequence.unit
      else F.invalid("Description requires its actual non-null session and optional observation")
    _ <- descriptionObservationC(value)
  } yield value
  private[admission] def descriptionObservationC(input: V.GoalPhasePrerequisitePublicationRecoveryDescriptionInput):
    Consequence[Option[GoalPhasePublishedDependencySourceAdoption.PublicationObservation]] = input.publicationObservation match {
    case None => Consequence.success(None)
    case Some(record) => GoalPhaseDependencyPublicationSourceAlignment.observationC(record).map(Some(_))
  }
  def descriptionForCallC(incoming: Request, call: ActionCall.Core): Consequence[V.GoalPhasePrerequisitePublicationRecoveryDescriptionInput] = {
    final class Reader extends ProcedureActionCall {
      val core = call
      def readInputC = for {
        source <- descriptionSourceC(incoming)
        record <- NativeWorkflowResourceInput.readC(source, reference => read_resource_text(reference,
          Some(java.nio.charset.StandardCharsets.UTF_8)).flatMap(validateBusinessTextC))
        input <- descriptionRecordC(record)
      } yield input
      def execute(): Consequence[OperationResponse] = F.invalid("Prerequisite description reader is not an Operation")
    }
    new Reader().readInputC
  }
  /** Owning operations keep raw duplicate validation before Record conversion. */
  def resourceInputC(request: Request): Consequence[NativeWorkflowResourceInput.Source[Record]] = for {
    _ <- if (request != null && DevelopmentOperationalView.uniqueFields(request.toRecord)) Consequence.unit
      else F.invalid("Operation input contains duplicate fields")
    _ <- Option(request).flatMap(_.toRecord.getAny("input")) match {
      case Some(raw: String) => validateBusinessTextC(raw).map(_ => ())
      case Some(record: Record) if !DevelopmentOperationalView.uniqueFields(record) => F.invalid("Business input contains duplicate fields")
      case _ => Consequence.unit
    }
    source <- NativeWorkflowResourceInput.selectC[Record](request)
  } yield source
  def validateBusinessTextC(raw: String): Consequence[String] = {
    def _contains_null_(value: Json): Boolean = value.isNull ||
      value.asArray.exists(_.exists(_contains_null_)) || value.asObject.exists(_.values.exists(_contains_null_))
    val parsed = io.circe.parser.parse(Option(raw).getOrElse(""))
    parsed.left.map(_.message).fold(F.invalid, json => if (_contains_null_(json))
      F.invalid("Business input contains explicit JSON null") else {
      var objects = List.empty[scala.collection.mutable.Set[String]]
      var index = 0
      var duplicate = false
      while (index < raw.length && !duplicate) {
        raw(index) match {
          case '{' => objects = scala.collection.mutable.Set.empty[String] :: objects; index += 1
          case '}' => objects = objects.tail; index += 1
          case '"' =>
            val start = index
            index += 1
            while (raw(index) != '"') { if (raw(index) == '\\') index += 2 else index += 1 }
            index += 1
            val end = index
            while (index < raw.length && raw(index).isWhitespace) index += 1
            if (index < raw.length && raw(index) == ':' && objects.nonEmpty) {
              val key = io.circe.parser.parse(raw.substring(start, end)).toOption.get.asString.get
              duplicate = !objects.head.add(key)
            }
          case _ => index += 1
        }
      }
      if (duplicate) F.invalid("Business input contains duplicate JSON keys") else Consequence.success(raw)
    })
  }
  def businessRecordC[A: ValueReader](record: Record, keys: Set[String]): Consequence[A] = for {
    _ <- if (record != null && DevelopmentOperationalView.uniqueFields(record)) Consequence.unit
      else F.invalid("Business input is absent or has duplicate fields")
    text <- record.toJsonStringC
    json <- io.circe.parser.parse(text).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
      .fold(F.invalid, Consequence.success)
    _ <- if (json.asObject.exists(fields => fields.keys.toSet == keys && fields.values.forall(!_.isNull))) Consequence.unit
      else F.invalid("Business input requires exactly its non-null declared fields")
    _ <- if (!keys("reference") || json.hcursor.get[Json]("reference").toOption.exists(_.asObject.exists(fields =>
        fields.keys.toSet == Set("identity", "revision") && fields.values.forall(_.asString.exists(_exact))))) Consequence.unit
      else F.invalid("Business reference requires exactly its non-null identity and revision")
    value <- summon[ValueReader[A]].readC(record)
  } yield value
  def recoveryInputC(input: V.GoalPhasePrerequisitePublicationRecoveryInput): Consequence[Unit] = for {
    _ <- if (input != null && input.reference != null && _exact(input.reference.identity) && _exact(input.reference.revision) &&
        _rationale(input.rationale)) Consequence.unit else F.invalid("Recovery input requires its independent reference and rationale")
    json <- W.jsonC(input.target)
    host = json.hcursor.get[String]("schemaVersion").toOption.exists(GoalPhasePrerequisitePublicationHostRecovery.isTargetSchema)
    _ <- if (host) GoalPhasePrerequisitePublicationHostRecovery.inputC(input)
      else if (input.hostCorrection != null && input.hostCorrection.isEmpty) Consequence.unit
      else F.invalid("Host correction is not a field of this legacy recovery target")
    expected <- json.hcursor.get[String]("schemaVersion").toOption match {
      case Some(version) if version == GoalPhaseDependencyPublicationSourceAlignment.targetSchema =>
        GoalPhaseDependencyPublicationSourceAlignment.targetC(input.target).map(GoalPhaseDependencyPublicationSourceAlignment.choices)
      case Some(version) if GoalPhasePrerequisitePublicationHostRecovery.isTargetSchema(version) =>
        GoalPhasePrerequisitePublicationHostRecovery.targetC(input.target).map(_ => GoalPhasePrerequisitePublicationHostRecovery.choices)
      case Some(version) if version == GoalPhasePublishedDependencySourceAdoption.artifactSchema =>
        GoalPhasePublishedDependencySourceAdoption.artifactTargetC(input.target).map(_ => GoalPhasePublishedDependencySourceAdoption.artifactChoices)
      case Some(version) if version == GoalPhaseCurrentSourceArtifactAdoption.targetSchema =>
        GoalPhaseCurrentSourceArtifactAdoption.targetC(input.target).map(_ => GoalPhaseCurrentSourceArtifactAdoption.choices)
      case Some(version) if GoalPhasePrerequisitePrimaryRepair.isTargetSchema(version) =>
        GoalPhasePrerequisitePrimaryRepair.targetC(input.target).map(GoalPhasePrerequisitePrimaryRepair.choices)
      case Some(version) if version == primarySchema => primaryTargetC(input.target).map(primaryChoices)
      case Some(version) if version == W.schema => W.targetC(input.target).map(W.choicesFor)
      case Some(version) if version == schema => targetC(input.target).map(_ => choices)
      case _ => F.invalid("Recovery input has an unknown closed target schema")
    }
    _ <- if (expected.contains(input.choice)) Consequence.unit else F.invalid("Recovery choice is not available at this exact phase")
  } yield ()

  /** Optional correction is local to this Action; other exact business records
    * keep their existing required-field contract. Explicit null is refused. */
  private[admission] def recoveryRecordC(record: Record): Consequence[V.GoalPhasePrerequisitePublicationRecoveryInput] = for {
    _ <- if (record != null && DevelopmentOperationalView.uniqueFields(record)) Consequence.unit
      else F.invalid("Recovery business input is absent or has duplicate fields")
    raw <- record.toJsonStringC
    json <- io.circe.parser.parse(raw).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
      .fold(F.invalid, Consequence.success)
    required = Set("sessionId", "target", "choice", "rationale", "reference")
    keys <- json.asObject.map(o => Consequence.success(o.keys.toSet)).getOrElse(F.invalid("Recovery input requires an object"))
    _ <- if (keys == required || keys == required + "hostCorrection") Consequence.unit
      else F.invalid("Recovery input has missing or undeclared fields")
    _ <- json.hcursor.get[Option[Json]]("hostCorrection").toOption.flatten match {
      case None if !keys("hostCorrection") => Consequence.unit
      case Some(correction) if correction.asObject.exists(fields => fields.keys.toSet == Set(
          "failedRequestIdentity", "programIdentity", "observationReference", "observedAt", "summary") &&
          fields.values.forall(!_.isNull)) && correction.hcursor.downField("observationReference").focus.exists(
            _.asObject.exists(fields => fields.keys.toSet == Set("identity", "revision") &&
              fields.values.forall(_.asString.exists(_exact)))) => Consequence.unit
      case _ => F.invalid("Host correction requires exactly its declared non-null fields")
    }
    value <- businessRecordC[V.GoalPhasePrerequisitePublicationRecoveryInput](record, keys)
  } yield value
}
