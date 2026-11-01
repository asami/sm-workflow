package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json, Printer}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.GitPath
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.record.Record
import GoalPhaseNativeRun as F
import GoalPhaseNativeCandidate as C
import GoalPhaseOrderedProgression as O
import GoalPhasePrerequisitePublicationRecovery as B
import GoalPhaseDependencySourceRevision as R
import GoalPhasePrerequisitePublicationUnknownRecovery as W
import GoalPhaseDependencyValidation.given
import RepositorySyncManagedGitObservation as G
import org.simplemodeling.textus.smworkflow.value as V

/** A primary Compile failure precedes every validation receipt. It reserves an
  * ordinary repair, retaining the actual publication and dependency lineage.
  * Publication authority is evaluated again at the actual repaired candidate.
  * @since   Nov.  2, 2026
  * @version Nov.  2, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhasePrerequisitePrimaryRepair {
  val schema = "sm-workflow.goal-phase-prerequisite-primary-repair.v1"
  val targetSchema = "sm-workflow.goal-phase-prerequisite-publication-recovery.v3"
  val causationPrefix = "ordered-primary-publication-repair:"
  val directionPrefix = "ordered-primary-publication-authority:"

  /** This reference authenticates reuse; it never rewrites a capture's revision
    * or generation and never denotes a second dependency effect. */
  final case class CaptureReuse(repair: ContextReference, candidateRecordRevision: String,
    sourceRevision: String, generation: Long)
  object CaptureReuse {
    given Encoder[CaptureReuse] = deriveEncoder
    private val _decoder: Decoder[CaptureReuse] = deriveDecoder
    given Decoder[CaptureReuse] = _closed(_decoder, reuseValid)
  }
  final case class Failure(componentInstance: String, participant: String, sessionId: String,
    frontierRevision: String, controller: String, plan: O.Plan, base: O.Snapshot,
    generation: Long, completed: Vector[O.ClosedStep], repairProgress: GoalPhaseRepairProgress.Snapshot,
    candidateRecordRevision: String, candidate: C.Payload, requestingResult: ContextReference,
    targets: Vector[LocalPublication.Target], publicationPolicy: String, processPolicy: String,
    failed: LocalPublication.Result, artifactAdoption: Option[W.ExistingArtifactAdoption],
    primaryDirections: Vector[B.PrimaryFact], excludedDirty: Vector[G.DirtyPath])
  object Failure {
    given Encoder[Failure] = Encoder.instance(value => _public(Json.obj(
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
      "artifactAdoption" -> value.artifactAdoption.asJson,
      "primaryDirections" -> value.primaryDirections.asJson,
      "excludedDirty" -> value.excludedDirty.asJson)))
    private val _decoder: Decoder[Failure] = Decoder.instance(cursor => for {
      component <- cursor.get[String]("componentInstance")
      participant <- cursor.get[String]("participant")
      session <- cursor.get[String]("sessionId")
      frontier <- cursor.get[String]("frontierRevision")
      controller <- cursor.get[String]("controller")
      plan <- cursor.get[O.Plan]("plan")
      base <- cursor.get[O.Snapshot]("base")
      generation <- cursor.get[Long]("generation")
      completed <- cursor.get[Vector[O.ClosedStep]]("completed")
      progress <- cursor.get[GoalPhaseRepairProgress.Snapshot]("repairProgress")
      candidaterevision <- cursor.get[String]("candidateRecordRevision")
      candidate <- cursor.get[C.Payload]("candidate")
      result <- cursor.get[ContextReference]("requestingResult")
      targets <- cursor.get[Vector[LocalPublication.Target]]("targets")
      publicationpolicy <- cursor.get[String]("publicationPolicy")
      processpolicy <- cursor.get[String]("processPolicy")
      failed <- cursor.get[LocalPublication.Result]("failed")
      adoption <- cursor.get[Option[W.ExistingArtifactAdoption]]("artifactAdoption")
      directions <- cursor.get[Vector[B.PrimaryFact]]("primaryDirections")
      excluded <- cursor.get[Vector[G.DirtyPath]]("excludedDirty")
    } yield Failure(component, participant, session, frontier, controller, plan, base, generation, completed, progress,
      candidaterevision, candidate, result, targets, publicationpolicy, processpolicy, failed, adoption, directions, excluded))
    given Decoder[Failure] = _closed(_decoder, failureValid)
  }
  final case class Fact(schemaVersion: String, failure: Failure, directive: O.RepairDirective,
    successorController: String)
  object Fact {
    given Encoder[Fact] = deriveEncoder
    private val _decoder: Decoder[Fact] = deriveDecoder
    given Decoder[Fact] = _closed(_decoder, factValid)
  }
  final case class Saved(record: InstanceRecord, fact: Fact) {
    def reference: ContextReference = ContextReference(record.identity.value, record.revision.value.toString)
    def reuse: CaptureReuse = CaptureReuse(reference, fact.failure.candidateRecordRevision,
      fact.failure.candidate.primaryPublicationRepair.map(_.sourceRevision).getOrElse(fact.failure.candidate.sourceRevision),
      fact.failure.generation)
  }
  /** A scope is stable across source-byte corrections inside the selected Step.
    * A one-shot answer instead binds Target.candidate and grants one attempt. */
  final case class Scope(componentInstance: String, participant: String, sessionId: String,
    plan: O.Plan, base: O.Snapshot, completed: Vector[O.ClosedStep], step: O.Step,
    targets: Vector[LocalPublication.Target], publicationPolicy: String, processPolicy: String)
  object Scope {
    given Encoder[Scope] = deriveEncoder
    given Decoder[Scope] = deriveDecoder
  }
  final case class Target(schemaVersion: String, componentInstance: String, participant: String,
    sessionId: String, frontierRevision: String, controller: String, generation: Long,
    candidateRecordRevision: String, candidate: C.Payload, failure: Failure,
    repair: Option[ContextReference], scope: Scope, missingAuthority: String)
  object Target {
    given Encoder[Target] = deriveEncoder[Target].mapJson(_public)
    private val _decoder: Decoder[Target] = deriveDecoder
    given Decoder[Target] = _closed(_decoder, targetValid)
  }
  final case class Direction(target: Target, choice: String, rationale: String, reference: ContextReference)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    private val _decoder: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = _closed(_decoder, directionValid)
  }
  final case class Directed(record: InstanceRecord, direction: Direction)
  final case class Source(native: B.Source, requestingResult: ContextReference,
    artifactAdoption: Option[W.ExistingArtifactAdoption], primaryDirections: Vector[B.PrimaryFact],
    excludedDirty: Option[Vector[G.DirtyPath]] = None)

  private def _closed[A: Encoder](decoder: Decoder[A], valid: A => Boolean): Decoder[A] =
    Decoder.instance(cursor => decoder(cursor).flatMap(value =>
      if (valid(value) && value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Primary repair fact is incomplete, crossed or noncanonical", cursor.history))))
  private def _public(value: Json): Json = value.arrayOrObject(value,
    xs => Json.fromValues(xs.map(_public)), fields => Json.fromFields(fields.toVector.collect {
      case (key, item) if !item.isNull => key -> _public(item)
    }))
  private def _sorted(value: Json): String = Printer.noSpaces.copy(sortKeys = true).print(value)
  private def _json(value: String): Boolean = Option(value).flatMap(io.circe.parser.parse(_).toOption)
    .exists(j => j.asObject.exists(_.nonEmpty) && _sorted(j) == value)
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _text(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _error_lines(value: String): Vector[String] = value.replaceAll("\u001b\\[[0-9;]*m", "")
    .linesIterator.map(_.trim).filter(line => line.startsWith("[error]") && !line.startsWith("[error] Total time:")).toVector

  def reuseValid(value: CaptureReuse): Boolean = value != null && R.referenceValid(value.repair) &&
    _revision(value.repair.revision) && _revision(value.candidateRecordRevision) && _revision(value.sourceRevision) &&
    BigInt(value.repair.revision) > BigInt(value.candidateRecordRevision) &&
    BigInt(value.candidateRecordRevision) > BigInt(value.sourceRevision) && value.generation > 0

  /** Only a completed canonical stream with the authoritative Compile failure
    * and optional parallel Scaladoc failure qualify. An arbitrary exit code,
    * Test failure or another failed task does not establish this classification. */
  def compileFailure(value: LocalPublication.Result, target: LocalPublication.Target,
    session: String, participant: String, source: LocalPublication.Source, processPolicy: String): Boolean =
    value != null && value.status == "failed" && value.request != null && value.request.target == target &&
      target != null && target.beforeChecks && target.dependency.isEmpty && value.request.participant == participant &&
      value.request.input != null && value.request.input.ownerSessionId.contains(session) &&
      !value.request.input.explicit && value.request.input.answer.isEmpty && value.request.source == source &&
      value.nextPreparation != null && value.nextPreparation.isEmpty && value.process != null && value.process.exists(raw =>
        WorkflowSbtCapture.decode(raw).toOption.exists { process =>
          val stdout = WorkflowSbtCapture.text(process.stdout)
          val stderr = WorkflowSbtCapture.text(process.stderr)
          val programs = io.circe.parser.parse(Option(processPolicy).getOrElse("")).toOption.flatMap(
            _.hcursor.downField("processPlan").get[Vector[Json]]("sbtPrograms").toOption).getOrElse(Vector.empty)
            .filter(p => p.hcursor.get[String]("target").contains(target.workspace) &&
              p.hcursor.get[String]("operation").contains("publish-local"))
          val errors = _error_lines(stdout.value + "\n" + stderr.value)
          val compilefailure = "[error] (Compile / compileIncremental) Compilation failed"
          val allowed = Set(compilefailure, "[error] (Compile / doc) Scaladoc generation failed")
          val summaries = errors.filter(_.startsWith("[error] ("))
          WorkflowSbtCapture.exitCode(process).exists(_ != 0) && stdout.textStatus == "complete" &&
            stderr.textStatus == "complete" && !process.stdout.truncated && !process.stderr.truncated &&
            programs.size == 1 && programs.head.hcursor.get[String]("identity").contains(process.safeProgramIdentity) &&
            summaries.nonEmpty && summaries.contains(compilefailure) && summaries.forall(allowed.contains) &&
            errors.lastOption.exists(allowed.contains) &&
            !(stdout.value + "\n" + stderr.value).linesIterator.exists(line =>
              line.contains("published ") || line.contains("[success]") || line.contains("(Test /"))
        })

  def directive(value: Failure): O.RepairDirective = O.RepairDirective(
    "Repair the actual primary publication Compile failure within the selected Step; tests were not executed",
    Vector("PRIMARY-PUBLICATION-COMPILE: " + value.failed.request.target.identity) ++
      value.failed.process.flatMap(WorkflowSbtCapture.decode(_).toOption)
        .toVector.flatMap(p => _error_lines(WorkflowSbtCapture.text(p.stdout).value + "\n" + WorkflowSbtCapture.text(p.stderr).value)).take(127))
  private def _candidate_request(failed: LocalPublication.Result, session: String, candidate: C.Payload): Boolean =
    failed != null && failed.request != null && failed.request.input != null &&
      failed.request.input.requestIdentity.startsWith(session + ":publication:" + candidate.sourceRevision + ":" +
        failed.request.target.identity + ":")
  private def _policy_targets(value: Failure): Option[Vector[LocalPublication.Target]] =
    io.circe.parser.parse(value.publicationPolicy).toOption.flatMap(_.hcursor.get[Vector[LocalPublication.Target]]("targets").toOption)
      .map(_.filter(t => t.beforeChecks && t.dependency.forall(identity =>
        value.candidate.dependencyAdmission.exists(_.binding.identity == identity) ||
          value.candidate.dependencySourceCapture.exists(_.direction.target.binding.identity == identity))))
  def evidence(value: Failure): String = value.sessionId + ":" + causationPrefix + value.frontierRevision + ":repair"
  def scope(value: Failure): Scope = Scope(value.componentInstance, value.participant, value.sessionId,
    value.plan, value.base, value.completed, value.plan.steps(value.completed.size), value.targets,
    value.publicationPolicy, value.processPolicy)
  def failureValid(value: Failure): Boolean = value != null && _text(value.componentInstance) && _text(value.participant) &&
    _text(value.sessionId) && _revision(value.frontierRevision) && _revision(value.candidateRecordRevision) &&
    BigInt(value.candidateRecordRevision) <= BigInt(value.frontierRevision) && _json(value.controller) &&
    value.plan != null && O.validatePlanC(value.plan).isSuccess && value.base != null && value.completed != null &&
    value.completed.size < value.plan.steps.size && value.completed.forall(O.validStepAcceptance) &&
    value.completed.map(_.step) == value.plan.steps.take(value.completed.size).map(_.identity) &&
    value.generation > 0 && value.repairProgress != null && GoalPhaseRepairProgress.validateC(value.repairProgress).isSuccess &&
    R.referenceValid(value.requestingResult) && value.candidate != null && value.candidate.candidate.nonEmpty &&
    value.candidate.diff.nonEmpty && value.candidate.step == value.plan.steps(value.completed.size).identity &&
    value.candidate.changedPaths.nonEmpty && GoalPhaseNativeCandidateBoundary.pathsOwned(value.candidate.changedPaths,
      value.plan.steps(value.completed.size).ownedPaths) && value.targets != null && value.targets.nonEmpty &&
    value.targets.map(_.identity).distinct.size == value.targets.size && value.failed != null && value.failed.request != null &&
    value.targets.contains(value.failed.request.target) &&
    _candidate_request(value.failed, value.sessionId, value.candidate) &&
    (!value.targets.exists(_.dependency.nonEmpty) || value.candidate.dependencySourceCapture.exists(c =>
      R.captureValid(c) && value.candidate.dependencySourceDirection.contains(c.direction))) &&
    _json(value.publicationPolicy) && _json(value.processPolicy) &&
    _policy_targets(value).contains(value.targets) &&
    value.candidate.candidate.exists(c => c.head == value.failed.request.source.head && c.tree == value.failed.request.source.tree) &&
    compileFailure(value.failed, value.failed.request.target, value.sessionId, value.participant, value.failed.request.source, value.processPolicy) &&
    value.artifactAdoption != null && value.primaryDirections != null && value.primaryDirections.forall(f => B.primaryFactValid(f) &&
      f.target.componentInstance == value.componentInstance && f.target.participant == value.participant &&
      f.target.sessionId == value.sessionId && f.target.plan == value.plan && f.target.base == value.base &&
      f.target.completed == value.completed && f.target.sourceCapture == value.candidate.dependencySourceCapture) &&
    value.excludedDirty != null && value.excludedDirty.forall(_ != null) && value.excludedDirty.map(_.path).distinct.size == value.excludedDirty.size &&
    value.excludedDirty.forall(d => d != null && !d.unmerged && _text(d.path) &&
      !GoalPhaseNativeCandidateBoundary.touches(d, value.candidate.selectedPaths)) && {
      val controller = io.circe.parser.parse(value.controller).toOption.get.hcursor
      controller.get[String]("stage").contains("Validation") && controller.get[O.Plan]("plan").contains(value.plan) &&
        controller.get[O.Snapshot]("base").contains(value.base) && controller.get[Long]("generation").contains(value.generation) &&
        controller.get[Vector[O.ClosedStep]]("completed").contains(value.completed) &&
        controller.get[Int]("cursor").contains(value.completed.size)
    }
  def factValid(value: Fact): Boolean = value != null && value.schemaVersion == schema && failureValid(value.failure) &&
    value.directive == directive(value.failure) && _json(value.successorController)
  def narrowed(value: Failure): Boolean = value.primaryDirections.nonEmpty ||
    value.candidate.dependencySourceCapture.exists(_.direction.target.primaryFailureOrigin.nonEmpty)
  def choices(value: Target): Vector[String] = if (value.repair.isEmpty)
    Vector("RepairPrimarySource", "RepairPrimarySourceAndContinuePublication", "Defer")
    else Vector("AuthorizePrimaryPublicationOnce", "AuthorizePrimaryPublicationAndSameScopeRepairs", "Defer")
  def targetValid(value: Target): Boolean = value != null && value.schemaVersion == targetSchema && failureValid(value.failure) &&
    value.componentInstance == value.failure.componentInstance && value.participant == value.failure.participant &&
    value.sessionId == value.failure.sessionId && _revision(value.frontierRevision) && _revision(value.candidateRecordRevision) &&
    _json(value.controller) && value.scope == scope(value.failure) && value.candidate != null && value.candidate.candidate.nonEmpty &&
    value.candidate.step == value.scope.step.identity && value.candidate.changedPaths.nonEmpty &&
    GoalPhaseNativeCandidateBoundary.pathsOwned(value.candidate.changedPaths, value.scope.step.ownedPaths) &&
    value.repair != null && value.repair.forall(R.referenceValid) && _text(value.missingAuthority) &&
    BigInt(value.candidateRecordRevision) <= BigInt(value.frontierRevision) && {
      val controller = io.circe.parser.parse(value.controller).toOption.get.hcursor
      controller.get[String]("stage").contains("Validation") && controller.get[O.Plan]("plan").contains(value.scope.plan) &&
        controller.get[O.Snapshot]("base").contains(value.scope.base) && controller.get[Long]("generation").contains(value.generation) &&
        controller.get[Vector[O.ClosedStep]]("completed").contains(value.scope.completed) &&
        controller.get[Int]("cursor").contains(value.scope.completed.size) &&
        controller.get[O.Snapshot]("candidate").toOption == value.candidate.candidate
    } &&
    (if (value.repair.isEmpty) value.candidate == value.failure.candidate && value.generation == value.failure.generation
      else value.generation > value.failure.generation && value.candidate.primaryPublicationRepair.exists(reuse =>
        value.repair.contains(reuse.repair)) && value.candidate.candidate.map(_.tree) != value.failure.candidate.candidate.map(_.tree))
  def directionValid(value: Direction): Boolean = value != null && targetValid(value.target) && choices(value.target).contains(value.choice) &&
    value.choice != "Defer" && R.referenceValid(value.reference) && value.rationale != null && value.rationale.trim.nonEmpty &&
    !value.rationale.contains('\u0000')
  def broad(value: Direction): Boolean = Set("RepairPrimarySourceAndContinuePublication",
    "AuthorizePrimaryPublicationAndSameScopeRepairs")(value.choice)
  def covered(value: Failure, current: Scope, directions: Vector[Directed], candidate: C.Saved): Boolean =
    authorityCovers(value, current, directions.map(_.direction), candidate.payload, candidate.record.revision.value.toString)
  def authorityCovers(value: Failure, current: Scope, directions: Vector[Direction], candidate: C.Payload,
    candidateRecordRevision: String): Boolean = failureValid(value) && current == scope(value) &&
    candidate != null && candidate.step == current.step.identity &&
    GoalPhaseNativeCandidateBoundary.pathsOwned(candidate.changedPaths, current.step.ownedPaths) &&
    (!narrowed(value) || directions.exists(d => directionValid(d) && d.target.scope == current &&
      (broad(d) || d.choice == "AuthorizePrimaryPublicationOnce" && d.target.candidate == candidate &&
        d.target.candidateRecordRevision == candidateRecordRevision)))

  private val _fact_codec = new WorkflowResultJsonV1.PayloadCodec[Fact] {
    val typeIdentity = "GoalPhasePrerequisitePrimaryRepairFact"
    def encode(value: Fact): Json = Json.obj("schemaVersion" -> schema.asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, Fact] = value.hcursor.get[Fact]("payload").left.map(_.message).flatMap(f =>
      Either.cond(encode(f) == value, f, "Primary repair envelope is noncanonical"))
  }
  val codec: WorkflowResultJsonV1.PayloadCodec[Fact] = _fact_codec

  def stateC(controller: O.State, failure: Failure): Consequence[O.State] =
    if (failureValid(failure) && _sorted(B.controller(controller)) == failure.controller)
      controller.continuePrimaryPublicationRepairC(evidence(failure), directive(failure))
    else F.invalid("Primary repair controller differs from its exact failed publication frontier")

  /** The native walker supplies Saved only after authenticating the original
    * candidate and adjacent repair fact. All dependency data remains identical. */
  def reusedCandidateC(saved: Saved, candidate: C.Payload, controller: O.State,
    from: InstanceRecord): Consequence[Unit] = {
    val failure = saved.fact.failure
    val previous = failure.candidate
    val samecapture = candidate.dependencyAdmission == previous.dependencyAdmission &&
      candidate.dependencyStaged == previous.dependencyStaged && candidate.dependency == previous.dependency &&
      candidate.dependencyOrigin == previous.dependencyOrigin &&
      candidate.dependencySourceDirection == previous.dependencySourceDirection &&
      candidate.dependencySourceGuard == previous.dependencySourceGuard &&
      candidate.dependencySourceStages == previous.dependencySourceStages &&
      candidate.dependencySourceCapture == previous.dependencySourceCapture &&
      candidate.dependencySourceScope == previous.dependencySourceScope
    if (factValid(saved.fact) && candidate.primaryPublicationRepair.contains(saved.reuse) && samecapture &&
        controller.plan == failure.plan && controller.base == failure.base && controller.completed == failure.completed &&
        controller.generation > failure.generation && controller.plan.steps.lift(controller.cursor).exists(_.identity == candidate.step) &&
        from.identity.value == failure.sessionId && from.revision.value > saved.record.revision.value &&
        from.history.take(saved.record.history.size) == saved.record.history &&
        candidate.sourceRevision == from.revision.value.toString && C.sourceCaptureValid(candidate)) Consequence.unit
    else F.invalid("Repaired primary candidate has no exact authenticated dependency-capture continuation")
  }

  def reusableAt(saved: Saved, controller: O.State): Boolean = saved.fact.failure.plan == controller.plan &&
    saved.fact.failure.base == controller.base && saved.fact.failure.completed == controller.completed &&
    controller.plan.steps.lift(controller.cursor).exists(_.identity == saved.fact.failure.candidate.step) &&
    controller.generation > saved.fact.failure.generation

  /** Capture and authority lineage survives later ordinary repairs; the cause
    * belongs to this failure's next Repair generation and retained directive,
    * including its authenticated existing repair-window continuation. */
  def currentCauseAt(fact: Fact, controller: O.State): Boolean =
    controller.plan == fact.failure.plan && controller.base == fact.failure.base &&
      controller.completed == fact.failure.completed &&
      controller.plan.steps.lift(controller.cursor).exists(_.identity == fact.failure.candidate.step) &&
      controller.generation == fact.failure.generation + 1 && controller.stage == O.Stage.Repair &&
      controller.repairDirective.contains(fact.directive)

  def workContextC(fact: Fact, reference: ContextReference, controller: O.State,
    kind: String): Consequence[Option[GoalPhaseNativeWork.PublicationFailureContext]] =
    if (kind != "RepairStep" || !currentCauseAt(fact, controller)) Consequence.success(None)
    else if (factValid(fact)) Consequence.success(Some(GoalPhaseNativeWork.PublicationFailureContext(reference,
      fact.failure.candidate.changedPaths, fact.failure.candidate.diff.get, fact.failure.failed, testsExecuted = false)))
    else F.invalid("Primary repair lost its retained actual Compile failure")

  enum PublicationRoute {
    case MissingAuthority, FirstFailureSuccessor, OrdinaryPrerequisite
  }
  def publicationRoute(failure: Failure, current: Scope, directions: Vector[Direction], candidate: C.Payload,
    candidateRecordRevision: String, generation: Long): PublicationRoute =
    if (generation <= failure.generation || !authorityCovers(failure, current, directions, candidate, candidateRecordRevision)) PublicationRoute.MissingAuthority
    else if (generation == failure.generation + 1) PublicationRoute.FirstFailureSuccessor
    else PublicationRoute.OrdinaryPrerequisite

  final case class PublicationAdmission(route: PublicationRoute,
    successor: Option[LocalPublication.ConfirmedFailureSuccessor])

  def repairSelectionC(candidatePaths: Vector[String], ownedPaths: Vector[String], dirty: Vector[G.DirtyPath],
    excluded: Vector[G.DirtyPath]): Consequence[Vector[String]] = {
    if (candidatePaths == null || ownedPaths == null || dirty == null || excluded == null ||
        !candidatePaths.forall(GitPath.parseC(_).isSuccess) || !ownedPaths.forall(GitPath.parseC(_).isSuccess) ||
        !dirty.forall(_dirty_valid) || !excluded.forall(_dirty_valid) ||
        dirty.map(_.path).distinct.size != dirty.size || excluded.map(_.path).distinct.size != excluded.size ||
        !excludedUnchanged(dirty, excluded))
      F.invalid("Primary repair requires valid actual dirty records and its unchanged excluded baseline")
    else {
      val relevant = dirty.filterNot(excluded.contains).filterNot(d => d.index == '!' && d.worktree == '!')
        .filter(GoalPhaseNativeCandidateBoundary.touches(_, ownedPaths))
      val endpoints = relevant.flatMap(d => d.path +: d.renamedFrom.toVector)
      val selected = candidatePaths.flatMap(owner => endpoints.filter(GoalPhaseNativeCandidateBoundary.covers(owner, _))).distinct
      if (endpoints.distinct.size != endpoints.size || !repairPathsValid(candidatePaths, ownedPaths, endpoints, excluded) ||
          !repairPathsValid(candidatePaths, ownedPaths, selected, excluded))
        F.invalid("Primary repair crosses owned/host coverage or has ambiguous excluded or rename endpoints")
      else Consequence.success(selected)
    }
  }

  /** Concrete children are admitted only by the authenticated primary-repair
    * owner. A selected path can neither cover nor descend from excluded dirt. */
  def repairPathsValid(candidatePaths: Vector[String], ownedPaths: Vector[String], paths: Vector[String],
    excluded: Vector[G.DirtyPath]): Boolean = {
    if (candidatePaths == null || ownedPaths == null || paths == null || excluded == null ||
        !candidatePaths.forall(GitPath.parseC(_).isSuccess) || !ownedPaths.forall(GitPath.parseC(_).isSuccess) ||
        !excluded.forall(_dirty_valid)) false
    else {
      val excludedpaths = excluded.flatMap(d => d.path +: d.renamedFrom.toVector)
      paths.distinct == paths && paths.forall(path => GitPath.parseC(path).isSuccess &&
        candidatePaths.exists(GoalPhaseNativeCandidateBoundary.covers(_, path)) &&
        ownedPaths.exists(GoalPhaseNativeCandidateBoundary.covers(_, path)) &&
        !excludedpaths.exists(p => GoalPhaseNativeCandidateBoundary.covers(path, p) ||
          GoalPhaseNativeCandidateBoundary.covers(p, path))) &&
        !paths.exists(path => paths.exists(p => p != path && GoalPhaseNativeCandidateBoundary.covers(path, p)))
    }
  }

  /** Current typed status can confirm an indexed removal without replaying add
    * for a path already absent from both the index and worktree. The caller
    * authenticates the repair and pins the actual parent and global index. */
  def stagedRemovalObserved(candidatePaths: Vector[String], ownedPaths: Vector[String], selectedPaths: Vector[String],
    dirty: Vector[G.DirtyPath], excluded: Vector[G.DirtyPath], path: String): Boolean =
    repairPathsValid(candidatePaths, ownedPaths, selectedPaths, excluded) && selectedPaths.contains(path) &&
      excludedUnchanged(dirty, excluded) && dirty.map(_.path).distinct.size == dirty.size &&
      (dirty.filter(d => d.path == path || d.renamedFrom.contains(path)) match {
        case Vector(value) if value.path == path =>
          value.index == 'D' && value.worktree == ' ' && value.renamedFrom.isEmpty
        case Vector(value) =>
          value.index == 'R' && value.renamedFrom.contains(path) && selectedPaths.contains(value.path)
        case _ => false
      })

  def excludedUnchanged(dirty: Vector[G.DirtyPath], excluded: Vector[G.DirtyPath]): Boolean = {
    if (dirty == null || excluded == null || !dirty.forall(_dirty_valid) || !excluded.forall(_dirty_valid)) false
    else {
      val paths = excluded.flatMap(d => d.path +: d.renamedFrom.toVector)
      dirty.filter(d => (d.path +: d.renamedFrom.toVector).exists(paths.contains)) == excluded
    }
  }

  private def _dirty_valid(value: G.DirtyPath): Boolean = value != null && value.renamedFrom != null &&
    (value.path +: value.renamedFrom.toVector).forall(GitPath.parseC(_).isSuccess) && !value.unmerged &&
    !value.renamedFrom.contains(value.path) &&
    (value.renamedFrom.nonEmpty == ("RC".contains(value.index) || "RC".contains(value.worktree))) &&
    ((value.index == '?' && value.worktree == '?') || (value.index == '!' && value.worktree == '!') ||
      (" MADRCT".contains(value.index) && " MADRCT".contains(value.worktree) &&
        (value.index != ' ' || value.worktree != ' ')))

  private def _reference(from: InstanceRecord, prefix: String) = WorkflowMechanicalTransition.Reference(
    from.identity.value + ":" + prefix + from.revision.value, "1")
  def collectFailureC(source: Source, d: F.Declaration, core: ActionCall.Core,
    failed: LocalPublication.Result, retained: Option[Failure] = None): Consequence[Failure] = for {
    policy <- d.localPublication.map(Consequence.success).getOrElse(F.invalid("Primary repair requires its current publication policy"))
    process <- retained.map(value => Consequence.success(value.processPolicy)).getOrElse(B.processPolicyC(d, core))
    n = source.native
    dependency = n.candidate.payload.dependencyAdmission.map(_.binding.identity)
      .orElse(n.candidate.payload.dependencySourceCapture.map(_.direction.target.binding.identity))
    targets = policy.targets.filter(t => t.beforeChecks && t.dependency.forall(dependency.contains))
    exclusions <- retained.map(value => Consequence.success(value.excludedDirty))
      .orElse(source.excludedDirty.map(Consequence.success)).getOrElse(G.observeC(core).map(_.dirty.filterNot(
        GoalPhaseNativeCandidateBoundary.touches(_, (n.candidate.payload.selectedPaths ++ n.candidate.payload.changedPaths).distinct))))
    failure = Failure(core.component.get.core.instanceId.canonicalKey, n.session.participantIdentity, n.from.identity.value,
      n.from.revision.value.toString, _sorted(B.controller(n.controller)), n.controller.plan, n.controller.base,
      n.controller.generation, n.controller.completed, GoalPhaseRepairProgress.snapshot(n.controller),
      n.candidate.record.revision.value.toString, n.candidate.payload, source.requestingResult, targets,
      B.publicationPolicy(policy), process, failed, source.artifactAdoption, source.primaryDirections, exclusions)
    _ <- if (failureValid(failure)) Consequence.unit else F.invalid("Primary repair requires an eligible exact current Compile failure")
    _ <- if (targets.filter(_.dependency.isEmpty) == Vector(failed.request.target) &&
        n.candidate.payload.dependencySourceCapture.forall(c =>
          failed.request.target.component == c.direction.target.binding.requester.identity &&
          failed.request.target.workspace == c.direction.target.binding.requester.workspace)) Consequence.unit
      else F.invalid("Primary repair publication crosses the selected Component or workspace")
    _ <- GoalPhaseNativeValidation.unstartedPrimaryPublicationC(n.session, d, core, n.candidate, n.controller,
      n.from, n.documentPolicy, n.recordingPaths, n.chain)
    _ <- LocalPublicationRuntime.retainedFailureC(core, policy, failed)
  } yield failure

  def eligibleC(source: Source, d: F.Declaration, core: ActionCall.Core,
    failed: LocalPublication.Result): Consequence[Boolean] = d.localPublication match {
    case None => Consequence.success(false)
    case Some(policy) => B.processPolicyC(d, core).map { process =>
      val candidate = source.native.candidate.payload
      val dependency = candidate.dependencyAdmission.map(_.binding.identity)
        .orElse(candidate.dependencySourceCapture.map(_.direction.target.binding.identity))
      val targets = policy.targets.filter(t => t.beforeChecks && t.dependency.forall(dependency.contains))
      targets.filter(_.dependency.isEmpty) match {
        case Vector(target) => (!targets.exists(_.dependency.nonEmpty) || candidate.dependencySourceCapture.exists(R.captureValid)) &&
          _candidate_request(failed, source.native.session.current.identity.value, candidate) && candidate.candidate.exists(c =>
            c.head == failed.request.source.head && c.tree == failed.request.source.tree) &&
          compileFailure(failed, target, source.native.session.current.identity.value,
            source.native.session.participantIdentity, failed.request.source, process)
        case _ => false
      }
    }
  }

  def appendC(source: Source, d: F.Declaration, core: ActionCall.Core, failed: LocalPublication.Result): Consequence[Saved] = for {
    failure <- collectFailureC(source, d, core, failed)
    policy = d.localPublication.get
    latest <- LocalPublication.collectLatestC(LocalPublication.LatestCollectionInput(failed.request.target.identity,
      failure.sessionId), failed.request.target, failure.participant, LocalPublicationRuntime._port(core, policy))
    _ <- if (latest.contains(failed)) Consequence.unit else F.invalid("A different latest primary writer blocks repair admission")
    next <- stateC(source.native.controller, failure)
    _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits("Validation",
        if (next.stage == O.Stage.Repair) "checks-repair-required" else "checks-failed", next.stage.toString)) Consequence.unit
      else F.invalid("Primary Compile repair has no existing native lifecycle edge")
    fact = Fact(schema, failure, directive(failure), _sorted(B.controller(next)))
    adapter <- source.native.session.storage.bindMechanicalC(F.advanceOperation, codec)
    saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(source.native.from, causationPrefix),
      source.native.from, source.native.from.revision, F.history(source.native.from, "phase." + next.stage.toString,
        causationPrefix + source.native.from.revision.value), TypedValue(codec.typeIdentity, fact)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    _ <- GoalPhaseRuntimeRepairControl.recordC(d, core, failure.sessionId,
      ContextReference(evidence(failure), "1"), "failed", GoalPhaseRuntimeRepairControl.facts(next))
  } yield Saved(saved.receipt.next, saved.receipt.payload.value)

  def retainedC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[(Saved, O.State)] = for {
    adapter <- source.native.session.storage.bindMechanicalC(F.advanceOperation, codec)
    saved <- adapter.loadRetainedC(_reference(source.native.from, causationPrefix))(using core.executionContext)
      .flatMap(_.map(v => Consequence.success(Saved(v.receipt.next, v.receipt.payload.value)))
        .getOrElse(F.invalid("Primary repair history omitted its actual immutable fact")))
    failure <- collectFailureC(source, d, core, saved.fact.failure.failed, Some(saved.fact.failure))
    next <- stateC(source.native.controller, failure)
    before = source.native.from
    actual <- GoalPhaseNativeHistory.prefixC(source.native.session.current, saved.record.revision)
    _ <- if (failure == saved.fact.failure && saved.fact.directive == directive(failure) &&
        saved.fact.successorController == _sorted(B.controller(next)) && actual == saved.record &&
        saved.record.identity == before.identity && saved.record.definition == before.definition &&
        saved.record.revision == before.revision.next && saved.record.history.dropRight(1) == before.history &&
        saved.record.currentProgression.contains(ProgressionReference("phase." + next.stage.toString)) &&
        saved.record.causation.value == causationPrefix + before.revision.value && saved.record.suspension.isEmpty)
      Consequence.unit else F.invalid("Primary repair crosses its actual adjacent native predecessor")
  } yield saved -> next

  def targetC(value: Record): Consequence[Target] = W.jsonC(value).flatMap(_.as[Target].left.map(_.message).fold(F.invalid, Consequence.success))
  def isTargetC(value: Record): Consequence[Boolean] = W.jsonC(value).map(_.hcursor.get[String]("schemaVersion").contains(targetSchema))
  def bindingC(component: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (targetValid(target) && target.componentInstance == component && target.participant == participant &&
        choices(target).contains(choice) && R.referenceValid(reference) && rationale != null && rationale.trim.nonEmpty && !rationale.contains('\u0000'))
      Consequence.success(Json.obj("componentInstance" -> component.asJson, "prerequisitePublicationRecovery" -> Json.obj(
        "target" -> target.asJson, "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson))))
    else F.invalid("Primary repair answer crosses its exact target, scope, choice or Human reference")
  def selectBinding(value: Json): Either[String, Json] = for {
    component <- value.hcursor.get[String]("componentInstance").left.map(_.message)
    body = value.hcursor.downField("prerequisitePublicationRecovery")
    target <- body.get[Target]("target").left.map(_.message)
    answer = body.downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    selected <- bindingC(component, target, choice, rationale, participant, reference)
      .map[Either[String, Json]](Right(_)).fold(e => Left(e.toString), identity)
    _ <- Either.cond(selected == value, (), "Primary repair binding contains undeclared fields")
  } yield selected

  def publicationTargetC(native: B.Source, failure: Failure, repair: Option[Saved], d: F.Declaration,
    core: ActionCall.Core, observe: Boolean = true): Consequence[Target] = for {
    policy <- d.localPublication.map(Consequence.success).getOrElse(F.invalid("Primary publication policy is absent"))
    process <- if (observe) B.processPolicyC(d, core) else Consequence.success(failure.processPolicy)
    _ <- if (failureValid(failure) && B.publicationPolicy(policy) == failure.publicationPolicy && process == failure.processPolicy &&
        native.controller.plan == failure.plan && native.controller.base == failure.base &&
        native.controller.completed == failure.completed && native.controller.stage == O.Stage.Validation &&
        native.candidate.payload.step == failure.candidate.step && repair.forall(reusableAt(_, native.controller))) Consequence.unit
      else F.invalid("Primary publication scope, fixed program or selected Step changed")
    _ <- if (observe) native.candidate.payload.dependencySourceCapture.fold(Consequence.unit)(R.freshnessC(core, _))
      else Consequence.unit
    _ <- if (observe) LocalPublicationRuntime._port(core, policy).sourceC(failure.failed.request.target).flatMap { current =>
      if (native.candidate.payload.candidate.exists(c => c.head == current.head && c.tree == current.tree)) Consequence.unit
      else F.invalid("Publication authority requires the actual current primary candidate")
    } else Consequence.unit
    target = Target(targetSchema, failure.componentInstance, failure.participant, failure.sessionId,
      native.from.revision.value.toString, _sorted(B.controller(native.controller)), native.controller.generation,
      native.candidate.record.revision.value.toString, native.candidate.payload, failure, repair.map(_.reference),
      scope(failure), if (repair.isEmpty) "primary-source-repair" else "new-primary-publication")
    _ <- if (targetValid(target)) Consequence.unit else F.invalid("Primary repair/publication target is incomplete")
  } yield target

  def description(target: Target): V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult =
    V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult(target.sessionId, "direction-required",
      if (target.repair.isEmpty) "The retained primary Compile failure can issue ordinary in-scope RepairStep. Tests were not executed. A one-shot source/publication direction does not cover a new candidate."
      else "The actual repaired primary candidate needs publication authority. AuthorizePrimaryPublicationOnce covers this candidate only; AuthorizePrimaryPublicationAndSameScopeRepairs covers necessary publication after further owned source repairs under this exact Component, Step, acceptance, programs, targets and destination.",
      target.componentInstance, GoalPhaseNativeMetadata.record(target.asJson), choices(target), B.recoveryOperation)

  private val _direction_codec = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "GoalPhasePrerequisitePrimaryPublicationDirection"
    def encode(value: Direction): Json = Json.obj("schemaVersion" -> targetSchema.asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, Direction] = value.hcursor.get[Direction]("payload").left.map(_.message).flatMap(d =>
      Either.cond(encode(d) == value, d, "Primary publication Direction envelope is noncanonical"))
  }
  def recoverC(native: B.Source, actual: Target, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhasePrerequisitePublicationRecoveryInput): Consequence[V.GoalPhasePrerequisitePublicationRecoveryResult] = for {
    supplied <- targetC(input.target)
    _ <- if (supplied == actual && actual.sessionId == input.sessionId && input.reference != null) Consequence.unit
      else F.invalid("Primary publication answer has a stale actual candidate or repair frontier")
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(actual.componentInstance, actual, input.choice, input.rationale, actual.participant, reference)
    installation <- d.prerequisitePublicationRecovery.map(Consequence.success).getOrElse(F.invalid("Independent Human evidence provider is absent"))
    _ <- installation.validateC
    _ <- installation.evidence.admitC(core, reference, binding)
    _ <- if (input.choice == "Defer") Consequence.unit else for {
      adapter <- native.session.storage.bindMechanicalC(F.advanceOperation, _direction_codec)
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(native.from, directionPrefix),
        native.from, native.from.revision, F.history(native.from, "phase.Validation", directionPrefix + native.from.revision.value),
        TypedValue(_direction_codec.typeIdentity, Direction(actual, input.choice, input.rationale, reference))),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield ()
  } yield V.GoalPhasePrerequisitePublicationRecoveryResult(input.sessionId, input.choice != "Defer",
    if (input.choice == "Defer") "deferred" else "primary-repair-publication-directed", description(actual).message)

  def retainedDirectionC(source: Source, repair: Option[Saved], d: F.Declaration,
    core: ActionCall.Core): Consequence[Directed] = for {
    native <- Consequence.success(source.native)
    adapter <- native.session.storage.bindMechanicalC(F.advanceOperation, _direction_codec)
    saved <- adapter.loadRetainedC(_reference(native.from, directionPrefix))(using core.executionContext)
      .flatMap(_.map(v => Consequence.success(Directed(v.receipt.next, v.receipt.payload.value)))
        .getOrElse(F.invalid("Primary publication authority history omitted its actual independent Direction")))
    failure <- repair.map(s => Consequence.success(s.fact.failure))
      .getOrElse(collectFailureC(source, d, core, saved.direction.target.failure.failed, Some(saved.direction.target.failure)))
    target <- publicationTargetC(native, failure, repair, d, core, observe = false)
    actual <- GoalPhaseNativeHistory.prefixC(native.session.current, saved.record.revision)
    _ <- if (saved.direction.target == target && actual == saved.record && saved.record.identity == native.from.identity &&
        saved.record.definition == native.from.definition && saved.record.revision == native.from.revision.next &&
        saved.record.history.dropRight(1) == native.from.history && saved.record.currentProgression == native.from.currentProgression &&
        saved.record.suspension.isEmpty && saved.record.causation.value == directionPrefix + native.from.revision.value)
      Consequence.unit else F.invalid("Primary publication Direction crosses its actual adjacent predecessor")
  } yield saved

  def permitC(saved: Saved, native: B.Source, directions: Vector[Directed], d: F.Declaration,
    core: ActionCall.Core): Consequence[PublicationAdmission] = for {
    target <- publicationTargetC(native, saved.fact.failure, Some(saved), d, core)
    route = publicationRoute(saved.fact.failure, target.scope, directions.map(_.direction), native.candidate.payload,
      native.candidate.record.revision.value.toString, native.controller.generation)
    source <- if (route == PublicationRoute.FirstFailureSuccessor) LocalPublicationRuntime._port(core, d.localPublication.get).sourceC(saved.fact.failure.failed.request.target)
      .map(Some(_)) else Consequence.success(Option.empty[LocalPublication.Source])
    authority = directions.findLast(d => d.direction.target.scope == target.scope && (broad(d.direction) ||
      d.direction.choice == "AuthorizePrimaryPublicationOnce" && d.direction.target.candidate == native.candidate.payload))
      .map(d => ContextReference(d.record.identity.value, d.record.revision.value.toString)).getOrElse(saved.reference)
  } yield PublicationAdmission(route, source.map(value => LocalPublication.ConfirmedFailureSuccessor(authority, saved.fact.failure.failed, value,
    ContextReference(native.session.current.identity.value, native.candidate.record.revision.value.toString),
    saved.fact.failure.processPolicy, saved.fact.failure.publicationPolicy)))
}
