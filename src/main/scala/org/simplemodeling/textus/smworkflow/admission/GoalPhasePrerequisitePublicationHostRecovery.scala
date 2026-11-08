package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json, Printer}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.time.Instant
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseNativeCandidate as C
import GoalPhaseOrderedProgression as O
import GoalPhasePrerequisitePublicationRecovery as B
import GoalPhaseDependencySourceRevision as R
import GoalPhasePrerequisitePublicationUnknownRecovery as W
import GoalPhasePublishedDependencySourceAdoption as P
import GoalPhaseCanonicalPublicationProgramCorrection as K
import GoalPhaseDependencyValidation.given

/** An independently directed, finite successor of a retained packaging capacity
  * failure. A correction observation is an attestation, never retry authority.
  * @since   Nov.  7, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhasePrerequisitePublicationHostRecovery {
  val schema = "sm-workflow.goal-phase-prerequisite-publication-host-recovery.v1"
  val schemaV2 = "sm-workflow.goal-phase-prerequisite-publication-host-recovery.v2"
  val schemaV3 = "sm-workflow.goal-phase-prerequisite-publication-host-recovery.v3"
  val schemaV4 = "sm-workflow.goal-phase-prerequisite-publication-host-recovery.v4"
  def isCanonicalTargetSchema(value: String): Boolean = value == schemaV3 || value == schemaV4
  def isTargetSchema(value: String): Boolean = value == schema || value == schemaV2 || isCanonicalTargetSchema(value)
  val causationPrefix = "ordered-primary-publication-host-recovery:"
  val authorize = "AuthorizePrimaryPublicationAfterHostCorrectionOnce"
  val choices = Vector(authorize, "Defer")

  final case class Lineage(unknown: Option[W.Direction], primary: Vector[B.PrimaryFact],
    repair: Option[GoalPhasePrerequisitePrimaryRepair.Fact],
    authority: Vector[GoalPhasePrerequisitePrimaryRepair.Direction],
    artifacts: Vector[GoalPhaseCurrentSourceArtifactAdoption.Direction])
  object Lineage {
    given Encoder[Lineage] = deriveEncoder[Lineage].mapJson(_public)
    private val _decoder: Decoder[Lineage] = deriveDecoder
    given Decoder[Lineage] = _closed(_decoder, lineageValid)
  }
  final case class Target(schemaVersion: String, componentInstance: String, participant: String,
    sessionId: String, frontierRevision: String, controller: String, plan: O.Plan, base: O.Snapshot,
    generation: Long, completed: Vector[O.ClosedStep], repairProgress: GoalPhaseRepairProgress.Snapshot,
    candidateRecordRevision: String, candidate: C.Payload, requestingResult: ContextReference,
    targets: Vector[LocalPublication.Target], publicationPolicy: String, processPolicy: String,
    failed: LocalPublication.Result, dependencyPrefix: Vector[LocalPublication.Result],
    priorDirections: Vector[ContextReference], lineage: Lineage,
    publishedDependencyAdoptions: Vector[P.ArtifactFact] = Vector.empty,
    canonicalProgramCorrection: Option[K.Correction] = None)
  object Target {
    given Encoder[Target] = Encoder.instance[Target](value => _public(Json.obj(
      "schemaVersion" -> value.schemaVersion.asJson,
      "componentInstance" -> value.componentInstance.asJson,
      "participant" -> value.participant.asJson,
      "sessionId" -> value.sessionId.asJson,
      "frontierRevision" -> value.frontierRevision.asJson,
      "controller" -> value.controller.asJson,
      "plan" -> value.plan.asJson,
      "base" -> value.base.asJson,
      "generation" -> value.generation.asJson,
      "completed" -> value.completed.asJson,
      "repairProgress" -> value.repairProgress.asJson,
      "candidateRecordRevision" -> value.candidateRecordRevision.asJson,
      "candidate" -> value.candidate.asJson,
      "requestingResult" -> value.requestingResult.asJson,
      "targets" -> value.targets.asJson,
      "publicationPolicy" -> value.publicationPolicy.asJson,
      "processPolicy" -> value.processPolicy.asJson,
      "failed" -> value.failed.asJson,
      "dependencyPrefix" -> value.dependencyPrefix.asJson,
      "priorDirections" -> value.priorDirections.asJson,
      "lineage" -> value.lineage.asJson).mapObject(fields =>
        if (value.schemaVersion == schemaV4) fields.add("canonicalProgramCorrection", value.canonicalProgramCorrection.asJson)
          .add("historicalPublishedDependencyAdoptions", value.publishedDependencyAdoptions.asJson)
        else if (value.schemaVersion == schemaV3) fields.add("canonicalProgramCorrection", value.canonicalProgramCorrection.asJson)
        else if (value.schemaVersion == schemaV2) fields.add("publishedDependencyAdoptions", value.publishedDependencyAdoptions.asJson)
        else fields)))
    private val _decoder: Decoder[Target] = Decoder.instance { cursor => for {
      schemaversion <- cursor.get[String]("schemaVersion")
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
      requestingresult <- cursor.get[ContextReference]("requestingResult")
      targets <- cursor.get[Vector[LocalPublication.Target]]("targets")
      publicationpolicy <- cursor.get[String]("publicationPolicy")
      processpolicy <- cursor.get[String]("processPolicy")
      failed <- cursor.get[LocalPublication.Result]("failed")
      dependencyprefix <- cursor.get[Vector[LocalPublication.Result]]("dependencyPrefix")
      priordirections <- cursor.get[Vector[ContextReference]]("priorDirections")
      lineage <- cursor.get[Lineage]("lineage")
      published <- if (schemaversion == schemaV2) cursor.get[Vector[P.ArtifactFact]]("publishedDependencyAdoptions")
        else if (schemaversion == schemaV4) cursor.get[Vector[P.ArtifactFact]]("historicalPublishedDependencyAdoptions")
        else Right(Vector.empty[P.ArtifactFact])
      canonical <- if (isCanonicalTargetSchema(schemaversion)) cursor.get[K.Correction]("canonicalProgramCorrection").map(Some(_))
        else Right(None)
    } yield Target(schemaversion, componentinstance, participant, sessionid, frontierrevision, controller,
      plan, base, generation, completed, repairprogress, candidaterecordrevision, candidate, requestingresult,
      targets, publicationpolicy, processpolicy, failed, dependencyprefix, priordirections, lineage, published, canonical) }
    given Decoder[Target] = _closed(_decoder, targetValid)
  }
  final case class Correction(failedRequestIdentity: String, programIdentity: String,
    observationReference: ContextReference, observedAt: String, summary: String)
  object Correction {
    given Encoder[Correction] = deriveEncoder
    private val _decoder: Decoder[Correction] = deriveDecoder
    given Decoder[Correction] = _closed(_decoder, correctionValid)
  }
  final case class Direction(target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference, hostCorrection: Option[Correction])
  object Direction {
    given Encoder[Direction] = deriveEncoder[Direction].mapJson(_public)
    private val _decoder: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = _closed(_decoder, directionValid)
  }
  final case class Saved(record: InstanceRecord, direction: Direction) {
    def reference: ContextReference = ContextReference(record.identity.value, record.revision.value.toString)
    def permit: Permit = Permit(direction.target, LocalPublication.ConfirmedFailureSuccessor(reference,
      direction.target.failed, direction.target.failed.request.source,
      ContextReference(direction.target.sessionId, direction.target.candidateRecordRevision),
      direction.target.processPolicy, direction.target.publicationPolicy))
  }
  final case class Permit(target: Target, successor: LocalPublication.ConfirmedFailureSuccessor)
  final case class Source(native: B.Source, requestingResult: ContextReference,
    directions: Vector[Saved], lineage: Lineage, publishedAdoptions: Vector[P.Saved] = Vector.empty)

  private def _public(value: Json): Json = value.arrayOrObject(value,
    xs => Json.fromValues(xs.map(_public)), fields => Json.fromFields(fields.toVector.collect {
      case (key, item) if !item.isNull => key -> _public(item)
    }))
  private def _closed[A: Encoder](decoder: Decoder[A], valid: A => Boolean): Decoder[A] =
    Decoder.instance(cursor => decoder(cursor).flatMap(value =>
      if (valid(value) && value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Host recovery value is incomplete, crossed or noncanonical", cursor.history))))
  private def _sorted(value: Json): String = Printer.noSpaces.copy(sortKeys = true).print(value)
  private def _text(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _rationale(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value)
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _json(value: String): Option[Json] = Option(value).flatMap(io.circe.parser.parse(_).toOption)
    .filter(j => j.asObject.exists(_.nonEmpty) && _sorted(j) == value)
  private def _programs(policy: String, target: LocalPublication.Target): Vector[Json] =
    _json(policy).flatMap(_.hcursor.downField("processPlan").get[Vector[Json]]("sbtPrograms").toOption)
      .getOrElse(Vector.empty).filter(p => p.hcursor.get[String]("target").contains(target.workspace) &&
        p.hcursor.get[String]("operation").contains("publish-local"))

  /** The tiny fixture uses the same explicit packaging summary as the product.
    * No arbitrary nonzero exit or generic host error is admitted. */
  def hostFailure(value: LocalPublication.Result, target: LocalPublication.Target,
    session: String, participant: String, source: LocalPublication.Source, processPolicy: String): Boolean =
    value != null && target != null && target.beforeChecks && target.dependency.isEmpty &&
      value.status == "failed" && value.request != null && value.request.target == target &&
      value.request.participant == participant && value.request.source == source && value.request.input != null &&
      value.request.input.ownerSessionId.contains(session) && value.request.input.targetIdentity == target.identity &&
      !value.request.input.explicit && value.request.input.answer.isEmpty && value.nextPreparation != null &&
      value.nextPreparation.isEmpty && value.process != null && value.process.exists(raw =>
        WorkflowSbtCapture.decode(raw).toOption.exists { process =>
          val stdout = WorkflowSbtCapture.text(process.stdout)
          val stderr = WorkflowSbtCapture.text(process.stderr)
          val text = (stdout.value + "\n" + stderr.value).replaceAll("\u001b\\[[0-9;]*m", "")
          val summaries = text.linesIterator.map(_.trim).filter(_.startsWith("[error] (")).toVector
          val programs = _programs(processPolicy, target)
          WorkflowSbtCapture.exitCode(process).exists(_ != 0) && stdout.textStatus == "complete" &&
            stderr.textStatus == "complete" && !process.stdout.truncated && !process.stderr.truncated &&
            programs.size == 1 && programs.head.hcursor.get[String]("identity").contains(process.safeProgramIdentity) &&
            text.contains("No space left on device") && summaries.nonEmpty &&
            summaries.forall(_.matches("\\[error\\] \\(cozyBuild(Car|Sar)\\) .+")) &&
            !text.linesIterator.exists(line => line.contains("(Compile /") || line.contains("(Test /") ||
              line.contains("(cozyGenerate)") || line.contains("Compilation failed") || line.contains("Error[No input]"))
        })

  private def _dependency(value: C.Payload): Option[String] = value.dependencyAdmission.map(_.binding.identity)
    .orElse(value.dependencySourceCapture.map(_.direction.target.binding.identity))
  private def _applicable(policy: LocalPublicationRuntime.Policy, candidate: C.Payload): Vector[LocalPublication.Target] =
    policy.targets.filter(t => t.beforeChecks && t.dependency.forall(_dependency(candidate).contains))
  private def _request_scope_matches(value: LocalPublication.Result, target: Target): Boolean =
    value != null && value.request != null && value.request.input != null && value.request.input.requestIdentity != null &&
      value.request.participant == target.participant && value.request.input.ownerSessionId.contains(target.sessionId) &&
      !value.request.input.explicit && value.request.input.answer.isEmpty
  private def _dependency_request_matches(value: LocalPublication.Result, target: Target): Boolean =
    _request_scope_matches(value, target) &&
    value.request.input.requestIdentity.startsWith(target.sessionId + ":publication:" +
      target.publishedDependencyAdoptions.headOption.filter(_ => target.schemaVersion != schemaV4)
        .map(_.direction.target.origin.historical.candidate.sourceRevision)
        .getOrElse(target.candidate.sourceRevision) +
      ":" + value.request.target.identity + ":")
  private def _successor_input(target: Target, reference: ContextReference): LocalPublication.Input = {
    val base = LocalPublicationRuntime.prerequisiteInput(target.sessionId, target.candidate, target.failed.request.target)
    base.copy(requestIdentity = base.requestIdentity + ":host:" + reference.identity + ":" + reference.revision)
  }
  private def _primary_request_matches(value: LocalPublication.Result, target: Target): Boolean =
    if (isCanonicalTargetSchema(target.schemaVersion))
      value.request.input == K.firstInput(target.sessionId, target.candidate, value.request.target)
    else if (target.schemaVersion == schema) _dependency_request_matches(value, target)
    else _request_scope_matches(value, target) && target.publishedDependencyAdoptions.headOption.exists { fact =>
      P.factValid(fact) && (value == fact.direction.target.origin.historical.failed ||
        target.priorDirections != null && target.priorDirections.exists(reference =>
          R.referenceValid(reference) && value.request.input == _successor_input(target, reference)))
    }

  def lineageValid(value: Lineage): Boolean = value != null && value.unknown != null && value.unknown.forall(W.directionValid) &&
    value.primary != null && value.primary.forall(B.primaryFactValid) && value.repair != null &&
    value.repair.forall(GoalPhasePrerequisitePrimaryRepair.factValid) && value.authority != null &&
    value.authority.forall(GoalPhasePrerequisitePrimaryRepair.directionValid) && value.artifacts != null &&
    value.artifacts.forall(GoalPhaseCurrentSourceArtifactAdoption.directionValid)
  private val _controller_fields = Set("plan", "stage", "cursor", "generation", "repairs", "base", "completed", "candidate",
    "checksReceipt", "reviewEvidence", "closureIntent", "decision", "repositoryReceipt", "repairDirective", "decisionSource",
    "documentDecision", "ordinaryRepairs", "pendingRepair", "phaseReviewedCandidate", "phaseRecordingPaths", "candidateParent",
    "stepAdmission", "releaseCommit", "repairControl", "policyRepairPending", "repairObservations", "stepReviewRequired")

  private def _published_valid(value: Target): Boolean =
    value.publishedDependencyAdoptions != null && (if (value.schemaVersion == schemaV4) _historical_published_valid(value)
    else if (value.schemaVersion == schema || value.schemaVersion == schemaV3) value.publishedDependencyAdoptions.isEmpty
    else value.schemaVersion == schemaV2 && value.publishedDependencyAdoptions.size == 1 &&
      value.publishedDependencyAdoptions.forall(P.factValid) &&
      value.publishedDependencyAdoptions.forall { fact =>
        val selected = fact.direction.target
        val old = selected.origin.historical
        P.factValid(fact) && selected.componentInstance == value.componentInstance && selected.participant == value.participant &&
          selected.sessionId == value.sessionId && selected.candidateRecordRevision == value.candidateRecordRevision &&
          selected.candidate == value.candidate && selected.controller == value.controller &&
          BigInt(fact.record.revision) <= BigInt(value.frontierRevision) && old.failed.request.source == value.failed.request.source &&
          old.dependencyPrefix == value.dependencyPrefix && old.targets == value.targets && old.plan == value.plan &&
          old.base == value.base && old.completed == value.completed && old.repairProgress == value.repairProgress &&
          old.requestingResult == value.requestingResult && old.lineage == value.lineage &&
          value.generation == old.generation + 1 && old.processPolicy == value.processPolicy && old.publicationPolicy == value.publicationPolicy
      })
  private def _historical_published_valid(value: Target): Boolean = {
    val facts = value.publishedDependencyAdoptions
    facts.nonEmpty && facts.forall(P.factValid) && facts.map(_.record).distinct.size == facts.size &&
      facts.map(f => BigInt(f.record.revision)) == facts.map(f => BigInt(f.record.revision)).sorted &&
      value.candidate.dependencySourceCapture.exists { current =>
        R.captureValid(current) && facts.forall { fact =>
          val selected = fact.direction.target
          selected.componentInstance == value.componentInstance && selected.participant == value.participant &&
            selected.sessionId == value.sessionId && fact.record.identity == value.sessionId &&
            BigInt(fact.record.revision) < BigInt(value.candidateRecordRevision) &&
            BigInt(fact.record.revision) < BigInt(current.sourceRevision) &&
            BigInt(fact.record.revision) < BigInt(value.frontierRevision) &&
            selected.candidate.dependencySourceCapture.exists { old =>
              old.schemaVersion == R.publishedCaptureSchema && R.captureValid(old) &&
                old.sessionId == value.sessionId &&
                selected.sourceDirection == ContextReference(old.sessionId, old.sourceRevision) &&
                old.direction.target.binding.identity == current.direction.target.binding.identity &&
                old.direction.target.binding.dependency.identity == current.direction.target.binding.dependency.identity &&
                old.direction.target.binding.dependency.repository == current.direction.target.binding.dependency.repository &&
                K.hasHistoricalAncestor(current, old.direction)
            }
        }
      }
  }
  private def _clause(label: String)(valid: => Boolean): () => Option[String] =
    () => if (valid) None else Some(label)
  def targetValid(value: Target): Boolean = targetFailure(value).isEmpty
  /** One lazy ordered predicate path serves codecs and description diagnostics. */
  private[admission] def targetFailure(value: Target): Option[String] =
    if (value == null) Some("schema.target") else Vector(
      _clause("schema.version")(isTargetSchema(value.schemaVersion)),
      _clause("schema.actor-fields")(_text(value.componentInstance) && _text(value.participant) && _text(value.sessionId)),
      _clause("native-frontier.revisions")(_revision(value.frontierRevision) && _revision(value.candidateRecordRevision) &&
        BigInt(value.candidateRecordRevision) <= BigInt(value.frontierRevision)),
      _clause("native-plan")(value.plan != null && O.validatePlanC(value.plan).isSuccess && value.base != null),
      _clause("native-prefix")(value.completed != null && value.completed.size < value.plan.steps.size &&
        value.completed.forall(O.validStepAcceptance) && value.completed.map(_.step) ==
          value.plan.steps.take(value.completed.size).map(_.identity) && value.generation > 0),
      _clause("repair-accounting")(value.repairProgress != null && GoalPhaseRepairProgress.validateC(value.repairProgress).isSuccess),
      _clause("requesting-result")(R.referenceValid(value.requestingResult)),
      _clause("candidate.ownership")(value.candidate != null && value.candidate.candidate.nonEmpty &&
        value.candidate.diff.nonEmpty && value.candidate.step == value.plan.steps(value.completed.size).identity &&
        value.candidate.changedPaths.nonEmpty && GoalPhaseNativeCandidateBoundary.pathsOwned(value.candidate.changedPaths,
          value.plan.steps(value.completed.size).ownedPaths)),
      _clause("publication-targets")(value.targets != null && value.targets.nonEmpty &&
        value.targets.map(_.identity).distinct.size == value.targets.size && value.failed != null && value.failed.request != null &&
        value.targets.last == value.failed.request.target && value.targets.dropRight(1).forall(_.dependency.nonEmpty)),
      _clause("schema.adoption-fields")(value.publishedDependencyAdoptions != null),
      _clause("primary.formal-classification")(_failure_valid(value)),
      _clause("primary.current-request")(_primary_request_matches(value.failed, value)),
      _clause("primary.current-source")(value.candidate.candidate.exists(c =>
        c.head == value.failed.request.source.head && c.tree == value.failed.request.source.tree)),
      _clause("dependency-prefix.targets")(value.dependencyPrefix != null &&
        value.dependencyPrefix.forall(p => p != null && p.request != null) &&
        value.dependencyPrefix.map(_.request.target) == value.targets.dropRight(1)),
      _clause("dependency-prefix.completed-result")(value.dependencyPrefix.forall(p => p.status == "published" && p.ready &&
        p.process != null && p.nextPreparation != null && p.process.exists(raw => WorkflowSbtCapture.decode(raw).toOption
          .exists(p => WorkflowSbtCapture.exitCode(p).contains(0))) && p.nextPreparation.isEmpty)),
      _clause("dependency-prefix.current-request")(value.dependencyPrefix.forall(p =>
        p.request.target.dependency == _dependency(value.candidate) && _dependency_request_matches(p, value))),
      _clause("publication-policy")(_json(value.publicationPolicy).flatMap(_.hcursor.get[Vector[LocalPublication.Target]]("targets").toOption)
        .map(_.filter(t => t.beforeChecks && t.dependency.forall(_dependency(value.candidate).contains))).contains(value.targets)),
      _clause("historical-adoption.ancestry")(_published_valid(value)),
      _clause("prior-directions")(value.priorDirections != null && value.priorDirections.forall(r => R.referenceValid(r) &&
        r.identity == value.sessionId && _revision(r.revision) && BigInt(r.revision) <= BigInt(value.frontierRevision)) &&
        value.priorDirections.distinct.size == value.priorDirections.size && value.priorDirections.map(r => BigInt(r.revision)) ==
          value.priorDirections.map(r => BigInt(r.revision)).sorted),
      _clause("lineage")(lineageValid(value.lineage)),
      _clause("controller-projection")(_json(value.controller).exists { raw =>
      val c = raw.hcursor
      raw.asObject.exists(_.keys.toSet == _controller_fields) &&
        c.get[String]("stage").contains("Validation") && c.get[O.Plan]("plan").contains(value.plan) &&
        c.get[O.Snapshot]("base").contains(value.base) && c.get[Long]("generation").contains(value.generation) &&
        c.get[Vector[O.ClosedStep]]("completed").contains(value.completed) && c.get[Int]("cursor").contains(value.completed.size) &&
        c.get[Option[O.Snapshot]]("candidate").contains(value.candidate.candidate) &&
        c.get[Int]("repairs").contains(value.repairProgress.automaticCount) &&
        c.get[Int]("ordinaryRepairs").contains(value.repairProgress.ordinaryCount) && value.plan.repairLimit == value.repairProgress.limit &&
        c.get[Option[GoalPhaseRuntimeRepairControl.Context]]("repairControl").toOption.exists { control =>
          control.map(_.policy) == value.repairProgress.runtimePolicy && control.map(_.selectedLimit) == value.repairProgress.selectedLimit &&
            control.map(_.effectiveLimit) == value.repairProgress.effectiveLimit
        } && c.downField("pendingRepair").focus.contains(Json.Null) && c.downField("closureIntent").focus.contains(Json.Null)
    })).iterator.map(clause => clause()).find(_.nonEmpty).flatten

  private def _failure_valid(value: Target): Boolean = value.canonicalProgramCorrection != null &&
    (if (isCanonicalTargetSchema(value.schemaVersion)) value.canonicalProgramCorrection.exists(c =>
      value.processPolicy == c.correctedProcessPolicy && K.failure(value.failed, value.targets.last, value.sessionId,
        value.participant, value.candidate, c) && value.candidate.dependencySourceCapture.exists(capture =>
          capture.sessionId == value.sessionId && capture.step == value.candidate.step && capture.primaryBase == value.base &&
            capture.plan == ContextReference(value.plan.identity, value.plan.revision) &&
            capture.generation == value.generation && capture.sourceRevision == value.candidate.sourceRevision &&
            capture.direction.target.canonicalOrigin.exists(origin => origin.componentInstance == value.componentInstance &&
              origin.participant == value.participant)))
    else value.canonicalProgramCorrection.isEmpty && hostFailure(value.failed, value.targets.last, value.sessionId,
      value.participant, value.failed.request.source, value.processPolicy))

  def correctionValid(value: Correction): Boolean = value != null && _text(value.failedRequestIdentity) &&
    _text(value.programIdentity) && R.referenceValid(value.observationReference) && _rationale(value.summary) &&
    _text(value.observedAt) && scala.util.Try(Instant.parse(value.observedAt)).isSuccess
  def correctionC(value: Option[V.GoalPhasePublicationHostCorrection]): Consequence[Option[Correction]] =
    if (value == null) F.invalid("Host correction option is absent") else value match {
      case None => Consequence.success(None)
      case Some(v) if v != null && v.observationReference != null =>
        val result = Correction(v.failedRequestIdentity, v.programIdentity,
          ContextReference(v.observationReference.identity, v.observationReference.revision), v.observedAt, v.summary)
        if (correctionValid(result)) Consequence.success(Some(result)) else F.invalid("Host correction is malformed")
      case _ => F.invalid("Host correction is malformed")
    }
  def directionValid(value: Direction): Boolean = value != null && targetValid(value.target) && choices.contains(value.choice) &&
    _rationale(value.rationale) && value.participant == value.target.participant && R.referenceValid(value.reference) &&
    value.hostCorrection != null && (value.choice == "Defer" || value.hostCorrection.nonEmpty) && value.hostCorrection.forall(c =>
      correctionValid(c) && c.failedRequestIdentity == value.target.failed.request.input.requestIdentity &&
        value.target.failed.process.flatMap(WorkflowSbtCapture.decode(_).toOption).exists(_.safeProgramIdentity == c.programIdentity))
  def bindingC(component: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference, correction: Option[Correction]): Consequence[Json] = {
    val direction = Direction(target, choice, rationale, participant, reference, correction)
    if (directionValid(direction) && target.componentInstance == component) Consequence.success(_public(Json.obj(
      "componentInstance" -> component.asJson, "prerequisitePublicationRecovery" -> Json.obj(
        "target" -> target.asJson, "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson, "hostCorrection" -> correction.asJson)))))
    else F.invalid("Host recovery answer crosses its exact failure, program, correction or Human")
  }
  def selectBinding(value: Json): Either[String, Json] = for {
    component <- value.hcursor.get[String]("componentInstance").left.map(_.message)
    body = value.hcursor.downField("prerequisitePublicationRecovery")
    target <- body.get[Target]("target").left.map(_.message)
    answer = body.downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    correction <- answer.get[Option[Correction]]("hostCorrection").left.map(_.message)
    selected <- bindingC(component, target, choice, rationale, participant, reference, correction)
      .map[Either[String, Json]](Right(_)).fold(e => Left(e.toString), identity)
    _ <- Either.cond(selected == value, (), "Host recovery binding contains undeclared or null fields")
  } yield selected
  /** Only public aliases of fields present in the closed typed encoding are
    * accepted. Policy/controller strings and parameter maps stay opaque. */
  private def _public_matches(raw: Json, canonical: Json, path: Vector[String] = Vector.empty): Boolean =
    if (path.lastOption.exists(Set("parameters", "executionParameters"))) raw == canonical
    else (raw.asObject, canonical.asObject, raw.asArray, canonical.asArray) match {
      case (Some(fields), Some(expected), _, _) =>
        def _aliases_(name: String): Set[String] = {
          val words = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT)
          Set(name, words, words.replace('_', '-'))
        }
        fields.size == expected.size && expected.toVector.forall { (key, value) =>
          val selected = fields.toVector.filter((name, _) => _aliases_(key)(name))
          selected.size == 1 && _public_matches(selected.head._2, value, path :+ key)
        }
      case (_, _, Some(values), Some(expected)) => values.size == expected.size &&
        values.zip(expected).forall((value, next) => _public_matches(value, next, path))
      case _ => raw == canonical
    }
  def targetC(value: Record): Consequence[Target] = for {
    raw <- if (value != null) value.toJsonStringC else F.invalid("Host target is absent")
    json <- io.circe.parser.parse(raw).left.map(_.message).fold(F.invalid, Consequence.success)
    normalized <- GoalPhaseNativeMetadata.normalizePublicJson(json).fold(F.invalid, Consequence.success)
    target <- normalized.as[Target].left.map(_.message).fold(F.invalid, Consequence.success)
    _ <- if (_public_matches(json, target.asJson)) Consequence.unit else F.invalid("Host target contains unknown or ambiguous public aliases")
  } yield target
  def isTargetC(value: Record): Consequence[Boolean] = W.jsonC(value).map(_.hcursor.get[String]("schemaVersion").toOption.exists(isTargetSchema))
  def inputC(input: V.GoalPhasePrerequisitePublicationRecoveryInput): Consequence[Unit] = for {
    target <- targetC(input.target)
    correction <- correctionC(input.hostCorrection)
    _ <- if (input.reference != null && input.sessionId == target.sessionId && directionValid(Direction(target,
      input.choice, input.rationale, target.participant, ContextReference(input.reference.identity, input.reference.revision), correction)))
      Consequence.unit else F.invalid("Host recovery input is malformed or crossed")
  } yield ()

  def matches(value: Target, source: Source): Boolean = source != null && value != null && {
    val n = source.native
    value.sessionId == n.from.identity.value && value.participant == n.session.participantIdentity &&
      value.controller == _sorted(B.controller(n.controller)) && value.plan == n.controller.plan && value.base == n.controller.base &&
      value.completed == n.controller.completed && value.generation == n.controller.generation &&
      value.repairProgress == GoalPhaseRepairProgress.snapshot(n.controller) &&
      value.candidateRecordRevision == n.candidate.record.revision.value.toString && value.candidate == n.candidate.payload &&
      value.requestingResult == source.requestingResult && value.lineage == source.lineage &&
      value.publishedDependencyAdoptions == source.publishedAdoptions.map(_.fact)
  }
  private def _eligible_c(source: Source): Consequence[Unit] = {
    val n = source.native
    if (n.controller.stage == O.Stage.Validation && n.controller.cursor < n.controller.plan.steps.size &&
        n.controller.pendingRepair.isEmpty && n.controller.closureIntent.isEmpty && n.from.lifecycle == Lifecycle.Active &&
        n.from.currentProgression.contains(ProgressionReference("phase.Validation")) && n.from.suspension.isEmpty &&
        n.candidate.record.revision.value <= n.from.revision.value)
      Consequence.unit else F.invalid("Host recovery requires its unstarted ordinary Validation frontier")
  }
  def targetForC(source: Source, failed: LocalPublication.Result, d: F.Declaration,
    core: ActionCall.Core, retained: Option[Target] = None): Consequence[Target] =
    _target_for_c(source, failed, d, core, retained, None)
  /** Historical origin construction keeps original requests and latest reads,
    * but deliberately does not equate the selected old source to repaired bytes.
    * The ordinary same-source targetForC/currentC contract is unchanged. */
  def originForSourceC(source: Source, failed: LocalPublication.Result, selected: LocalPublication.Target,
    d: F.Declaration, core: ActionCall.Core, retained: Option[Target] = None): Consequence[Target] =
    if (source.publishedAdoptions.nonEmpty || selected == null || selected.dependency.isEmpty)
      F.invalid("Published source origin requires one original successful dependency")
    else _target_for_c(source, failed, d, core, retained, Some(selected))
  private def _target_for_c(source: Source, failed: LocalPublication.Result, d: F.Declaration,
    core: ActionCall.Core, retained: Option[Target], selected: Option[LocalPublication.Target]): Consequence[Target] = for {
    _ <- _eligible_c(source)
    policy <- d.localPublication.map(Consequence.success).getOrElse(F.invalid("Host recovery publication policy is absent"))
    n = source.native
    targets = _applicable(policy, n.candidate.payload)
    canonical = if (retained.exists(t => !isCanonicalTargetSchema(t.schemaVersion))) None
      else targets.lastOption.flatMap(K.derive(n.candidate.payload, _))
    process <- canonical.map(c => Consequence.success(c.correctedProcessPolicy))
      .orElse(retained.map(t => Consequence.success(t.processPolicy))).getOrElse(B.processPolicyC(d, core))
    _ <- if (canonical.isEmpty) Consequence.unit else n.candidate.payload.dependencySourceCapture
      .map(n.chain.sourceCaptureC).getOrElse(F.invalid("Canonical host correction lost its actual native capture"))
    targetversion = retained.map(_.schemaVersion).getOrElse(if (canonical.nonEmpty && source.publishedAdoptions.nonEmpty) schemaV4
      else if (canonical.nonEmpty) schemaV3 else if (source.publishedAdoptions.nonEmpty) schemaV2 else schema)
    prefix <- retained.orElse(source.publishedAdoptions.headOption.filter(_ => targetversion != schemaV4)
      .map(_.direction.target.origin.historical))
      .map(t => Consequence.success(t.dependencyPrefix)).getOrElse(
      targets.dropRight(1).foldLeft(Consequence.success(Vector.empty[LocalPublication.Result])) { (prior, target) => for {
        values <- prior
        value <- LocalPublication.collectPrerequisiteC(LocalPublicationRuntime.prerequisiteInput(n.from.identity.value,
          n.candidate.payload, target), target, n.session.participantIdentity, LocalPublicationRuntime._port(core, policy))
        actual <- value.map(Consequence.success).getOrElse(F.invalid(
          "Dependency has no genuine completed publication [clause=dependency-prefix.current-collection]"))
      } yield values :+ actual })
    target = Target(targetversion,
      core.component.get.core.instanceId.canonicalKey, n.session.participantIdentity, n.from.identity.value,
      n.from.revision.value.toString, _sorted(B.controller(n.controller)), n.controller.plan, n.controller.base,
      n.controller.generation, n.controller.completed, GoalPhaseRepairProgress.snapshot(n.controller),
      n.candidate.record.revision.value.toString, n.candidate.payload, source.requestingResult, targets,
      B.publicationPolicy(policy), process, failed, prefix, source.directions.map(_.reference), source.lineage,
      source.publishedAdoptions.map(_.fact), canonical)
    _ <- targetFailure(target).orElse(Option.when(!retained.forall(_ == target))("retained-target.exact-reconstruction")) match {
      case None => Consequence.unit
      case Some(clause) => F.invalid("Host recovery crosses its exact candidate, source, dependency prefix or fixed policy [clause=" + clause + "]")
    }
    _ <- if (target.schemaVersion == schemaV2) publishedFailureC(target, source.directions, LocalPublicationRuntime._port(core, policy))
      else Consequence.unit
    _ <- if (retained.nonEmpty) Consequence.unit else for {
      _ <- if (n.from == n.session.current) Consequence.unit else F.invalid("Host recovery frontier is stale")
      _ <- GoalPhaseNativeValidation.unstartedPrimaryPublicationC(n.session, d, core, n.candidate, n.controller,
        n.from, n.documentPolicy, n.recordingPaths, n.chain)
      _ <- selected match {
        case Some(value) if target.dependencyPrefix.exists(_.request.target == value) =>
          B.processPolicyC(d, core).flatMap(actual =>
            if (actual == target.processPolicy) checkOriginPublicationsC(target, value, LocalPublicationRuntime._port(core, policy))
            else F.invalid("Historical publication process policy changed"))
        case Some(_) => F.invalid("Selected historical dependency is absent from the actual successful prefix")
        case None => _current_c(target, d, core, allowold = true)
      }
      latest <- LocalPublication.collectLatestC(LocalPublication.LatestCollectionInput(failed.request.target.identity,
        target.sessionId), failed.request.target, target.participant, LocalPublicationRuntime._port(core, policy))
      _ <- if (latest.contains(failed)) Consequence.unit
        else F.invalid("Host failure is no longer the latest exact primary attempt [clause=primary.latest-result]")
    } yield ()
    _ <- LocalPublicationRuntime.retainedFailureC(core, policy, failed)
    _ <- prefix.foldLeft(Consequence.unit)((prior, p) => prior.flatMap(_ =>
      LocalPublicationRuntime.retainedFailureC(core, policy, p)))
  } yield target

  /** Native traversal supplies the authenticated Saved directions. An arbitrary
    * prior-reference string is insufficient to select a later failed attempt.
    * This proof only collects the existing qualified result; it never executes. */
  private[admission] def publishedFailureC(target: Target, directions: Vector[Saved], port: LocalPublication.Port): Consequence[Unit] = for {
    _ <- if (targetValid(target) && target.schemaVersion == schemaV2 && directions != null) Consequence.unit
      else F.invalid("Published host failure requires its exact current target and actual saved directions")
    original = target.publishedDependencyAdoptions.head.direction.target.origin.historical.failed
    _ <- if (target.failed == original) Consequence.unit else for {
      saved <- directions.filter { saved =>
        saved != null && saved.record != null && directionValid(saved.direction) && saved.direction.choice == authorize &&
          target.priorDirections.contains(saved.reference) && {
            val previous = saved.direction.target
            previous.componentInstance == target.componentInstance && previous.participant == target.participant &&
              previous.sessionId == target.sessionId && previous.controller == target.controller &&
              previous.candidate == target.candidate && previous.candidateRecordRevision == target.candidateRecordRevision &&
              previous.publishedDependencyAdoptions == target.publishedDependencyAdoptions &&
              previous.requestingResult == target.requestingResult && previous.lineage == target.lineage &&
              previous.targets == target.targets && previous.processPolicy == target.processPolicy &&
              previous.publicationPolicy == target.publicationPolicy && input(saved.permit) == target.failed.request.input
          }
      } match {
        case Vector(value) => Consequence.success(value)
        case _ => F.invalid("Later published host failure has no exact actual prior saved authorization")
      }
      collected <- collectC(saved.permit, port)
      _ <- if (collected.contains(target.failed)) Consequence.unit
        else F.invalid("Later published host failure differs from its actual retained qualified result")
    } yield ()
  } yield ()

  /** These reads are also performed immediately before the primary effect. */
  def currentC(target: Target, d: F.Declaration, core: ActionCall.Core): Consequence[Unit] =
    _current_c(target, d, core, allowold = false)
  private def _current_c(target: Target, d: F.Declaration, core: ActionCall.Core,
    allowold: Boolean): Consequence[Unit] = for {
    policy <- d.localPublication.map(Consequence.success).getOrElse(F.invalid("Host recovery publication policy is absent"))
    process <- B.processPolicyC(d, core)
    _ <- if (targetValid(target) && (process == target.processPolicy || allowold &&
        target.canonicalProgramCorrection.exists(_.oldProcessPolicy == process)) && B.publicationPolicy(policy) == target.publicationPolicy &&
        _applicable(policy, target.candidate) == target.targets) Consequence.unit
      else F.invalid("Host recovery fixed program or publication policy changed [clause=current-policy.fixed-program]")
    _ <- target.canonicalProgramCorrection.fold(Consequence.unit) { correction =>
      val capture = target.candidate.dependencySourceCapture.get
      if (K.matches(correction, target.candidate, target.failed.request.target) &&
          d.dependencySourceRevisions.contains(R.bindingOf(capture)) &&
          d.dependencySourceConsumptions.contains(R.consumptionOf(capture))) Consequence.unit
      else F.invalid("Canonical host correction crosses its declared current source binding or consumption [clause=current-policy.source-binding]")
    }
    _ <- target.candidate.dependencySourceCapture.fold(Consequence.unit)(R.freshnessC(core, _))
    _ <- checkPublicationsC(target, LocalPublicationRuntime._port(core, policy))
  } yield ()
  def checkPublicationsC(target: Target, port: LocalPublication.Port): Consequence[Unit] = for {
    _ <- target.dependencyPrefix.foldLeft(Consequence.unit) { (prior, p) => for {
      _ <- prior
      retained <- LocalPublication.collectC(p.request.input, p.request.target, target.participant, port)
      latest <- LocalPublication.collectLatestC(LocalPublication.LatestCollectionInput(p.request.target.identity,
        target.sessionId), p.request.target, target.participant, port)
      source <- port.sourceC(p.request.target)
      adoption = target.publishedDependencyAdoptions.find(f => target.schemaVersion != schemaV4 &&
        f.direction.target.origin.selectedTarget == p.request.target)
      _ <- if (retained.contains(p)) Consequence.unit else F.invalid(
        "Successful dependency publication changed [clause=dependency-prefix.immutable-result]")
      _ <- if (latest.contains(p)) Consequence.unit else F.invalid(
        "Successful dependency publication changed [clause=dependency-prefix.latest-result]")
      _ <- if (p.status == "published" && p.ready &&
          adoption.fold(source == p.request.source)(fact => P.factValid(fact) &&
            fact.direction.target.origin.historical.dependencyPrefix.contains(p) &&
            fact.direction.target.candidate == target.candidate && source == P.sourceOf(fact.direction.target.guard)))
        Consequence.unit else F.invalid("Successful dependency publication changed; no dependency replay is authorized [clause=dependency-prefix.current-source]")
    } yield () }
    source <- port.sourceC(target.failed.request.target)
    _ <- if (source == target.failed.request.source) Consequence.unit
      else F.invalid("Host recovery primary source changed [clause=primary.current-source]")
  } yield ()
  def checkOriginPublicationsC(target: Target, selected: LocalPublication.Target, port: LocalPublication.Port): Consequence[Unit] = for {
    _ <- if (targetValid(target) && target.schemaVersion == schema && target.dependencyPrefix.exists(_.request.target == selected))
      Consequence.unit else F.invalid("Historical publication origin is invalid")
    _ <- target.dependencyPrefix.foldLeft(Consequence.unit) { (prior, p) => for {
      _ <- prior
      retained <- LocalPublication.collectC(p.request.input, p.request.target, target.participant, port)
      latest <- LocalPublication.collectLatestC(LocalPublication.LatestCollectionInput(p.request.target.identity, target.sessionId),
        p.request.target, target.participant, port)
      _ <- if (retained.contains(p) && latest.contains(p) && p.status == "published" && p.ready) Consequence.unit
        else F.invalid("Historical successful dependency request or latest result changed")
      _ <- if (p.request.target == selected) Consequence.unit else port.sourceC(p.request.target).flatMap(source =>
        if (source == p.request.source) Consequence.unit else F.invalid("Unselected dependency source changed"))
    } yield () }
    source <- port.sourceC(target.failed.request.target)
    _ <- if (source == target.failed.request.source) Consequence.unit else F.invalid("Historical primary source changed")
    failed <- LocalPublication.collectC(target.failed.request.input, target.failed.request.target, target.participant, port)
    latest <- LocalPublication.collectLatestC(LocalPublication.LatestCollectionInput(target.failed.request.target.identity,
      target.sessionId), target.failed.request.target, target.participant, port)
    _ <- if (failed.contains(target.failed) && latest.contains(target.failed)) Consequence.unit
      else F.invalid("Historical primary failure is no longer the exact latest attempt")
  } yield ()
  def description(target: Target): V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult =
    V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult(target.sessionId, "direction-required",
      (if (isCanonicalTargetSchema(target.schemaVersion))
        "The retained first primary publication selected the actual previous source root despite an adopted canonical source. Correct exactly the described fixed publisher source environment value externally. "
      else "The retained primary packaging capacity failure may have partial publication effects. ") +
        "An independent Human may authorize one whole-primary successor after an attributable host correction, accepting possible partial effects. Successful dependencies and original failures remain retained. Ordinary Advance owns execution.",
      target.componentInstance, GoalPhaseNativeMetadata.record(target.asJson), choices, B.recoveryOperation)
  private def _reference(from: InstanceRecord) = WorkflowMechanicalTransition.Reference(
    from.identity.value + ":" + causationPrefix + from.revision.value, "1")
  private val _codec = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "GoalPhasePrerequisitePublicationHostRecovery"
    def encode(value: Direction): Json = Json.obj("schemaVersion" -> value.target.schemaVersion.asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, Direction] = value.hcursor.get[Direction]("payload").left.map(_.message).flatMap(d =>
      Either.cond(encode(d) == value, d, "Host recovery Direction envelope is noncanonical"))
  }
  def recoverC(source: Source, actual: Target, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhasePrerequisitePublicationRecoveryInput): Consequence[V.GoalPhasePrerequisitePublicationRecoveryResult] = for {
    _ <- inputC(input)
    supplied <- targetC(input.target)
    _ <- if (supplied == actual && matches(actual, source)) Consequence.unit else F.invalid("Host recovery target is stale or crossed")
    _ <- if (input.choice == "Defer" || !isCanonicalTargetSchema(actual.schemaVersion)) Consequence.unit else currentC(actual, d, core)
    correction <- correctionC(input.hostCorrection)
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(actual.componentInstance, actual, input.choice, input.rationale, actual.participant, reference, correction)
    installation <- d.prerequisitePublicationRecovery.map(Consequence.success).getOrElse(F.invalid("Independent Human evidence provider is absent"))
    _ <- installation.validateC
    _ <- installation.evidence.admitC(core, reference, binding)
    _ <- if (input.choice == "Defer") Consequence.unit else for {
      adapter <- source.native.session.storage.bindMechanicalC(F.advanceOperation, _codec)
      existing <- adapter.loadRetainedC(_reference(source.native.from))(using core.executionContext)
      _ <- if (existing.isEmpty) Consequence.unit else F.invalid("Host recovery Direction already exists")
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(source.native.from),
        source.native.from, source.native.from.revision, F.history(source.native.from, "phase.Validation",
          causationPrefix + source.native.from.revision.value), TypedValue(_codec.typeIdentity,
            Direction(actual, input.choice, input.rationale, actual.participant, reference, correction))),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield ()
  } yield V.GoalPhasePrerequisitePublicationRecoveryResult(input.sessionId, input.choice != "Defer",
    if (input.choice == "Defer") "deferred" else "primary-host-publication-directed", description(actual).message)
  def retainedC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Saved] = for {
    adapter <- source.native.session.storage.bindMechanicalC(F.advanceOperation, _codec)
    saved <- adapter.loadRetainedC(_reference(source.native.from))(using core.executionContext)
      .flatMap(_.map(v => Consequence.success(Saved(v.receipt.next, v.receipt.payload.value)))
        .getOrElse(F.invalid("Host recovery history omitted its exact immutable Direction")))
    target <- targetForC(source, saved.direction.target.failed, d, core, Some(saved.direction.target))
    actual <- GoalPhaseNativeHistory.prefixC(source.native.session.current, saved.record.revision)
    before = source.native.from
    _ <- if (saved.direction.choice == authorize && saved.record == actual && saved.direction.target == target &&
        saved.record.identity == before.identity && saved.record.definition == before.definition &&
        saved.record.revision == before.revision.next && saved.record.history.dropRight(1) == before.history &&
        saved.record.currentProgression == before.currentProgression && saved.record.suspension.isEmpty &&
        saved.record.causation.value == causationPrefix + before.revision.value) Consequence.unit
      else F.invalid("Host recovery history crosses its actual adjacent native predecessor")
  } yield saved
  def applicable(saved: Saved, source: Source): Boolean = saved != null && matches(saved.direction.target, source) &&
    source.directions.lastOption.contains(saved) && source.native.from.revision.value >= saved.record.revision.value
  def permitC(saved: Saved, source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Permit] = for {
    _ <- if (applicable(saved, source)) Consequence.unit else F.invalid("Host recovery grant crosses its current native candidate or controller")
    _ <- currentC(saved.direction.target, d, core)
    permit = saved.permit
    collected <- collectC(permit, LocalPublicationRuntime._port(core, d.localPublication.get))
    _ <- if (collected.nonEmpty) Consequence.unit else GoalPhaseNativeValidation.unstartedPrimaryPublicationC(
      source.native.session, d, core, source.native.candidate, source.native.controller, source.native.from,
      source.native.documentPolicy, source.native.recordingPaths, source.native.chain)
    _ <- latestC(permit, LocalPublicationRuntime._port(core, d.localPublication.get))
  } yield permit
  def input(permit: Permit): LocalPublication.Input = _successor_input(permit.target, permit.successor.direction)
  def collectC(permit: Permit, port: LocalPublication.Port): Consequence[Option[LocalPublication.Result]] =
    LocalPublication.collectConfirmedFailureSuccessorC(input(permit), permit.target.failed.request.target,
      permit.target.participant, permit.successor, port)
  def latestC(permit: Permit, port: LocalPublication.Port): Consequence[Unit] = for {
    collected <- collectC(permit, port)
    latest <- LocalPublication.collectLatestC(LocalPublication.LatestCollectionInput(permit.target.failed.request.target.identity,
      permit.target.sessionId), permit.target.failed.request.target, permit.target.participant, port)
    _ <- if (latest.contains(collected.getOrElse(permit.target.failed))) Consequence.unit
      else F.invalid("Host recovery no longer owns the latest exact primary attempt")
  } yield ()
}
