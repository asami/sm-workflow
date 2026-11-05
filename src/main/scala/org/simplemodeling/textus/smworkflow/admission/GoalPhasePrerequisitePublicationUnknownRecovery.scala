package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseDependencyValidation as D
import GoalPhaseDependencyValidation.given
import GoalPhaseDependencySourceRevision as R
import GoalPhaseNativeCandidate as C
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhasePrerequisitePublicationRecovery as P

/** Distinct native continuation of an actual UNKNOWN prerequisite. Independent
  * lifecycle facts and Human direction are separate inputs. Neither replaces
  * the old publication result nor the accepted semantic work.
  * @since   Nov.  1, 2026
  *  version Nov.  2, 2026
  * @version Nov.  5, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhasePrerequisitePublicationUnknownRecovery {
  val schema = "sm-workflow.goal-phase-prerequisite-publication-unknown-recovery.v1"
  val observationSchema = "sm-workflow.goal-phase-prerequisite-publication-observation.v1"
  val observationPurpose = "goal-phase-prerequisite-publication-observation"
  val causationPrefix = "ordered-prerequisite-publication-unknown-recovery:"
  val sourceCausationPrefix = "ordered-prerequisite-wait-source:"
  val primarySourceCausationPrefix = sourceCausationPrefix + "primary:"
  val choices = Vector("AuthorizeQualifiedSuccessorPublication", "AcceptExistingArtifactsWithUnknownEffects",
    "AuthorizeNewPublicationWithUnknownEffects", "Defer")
  val message = "The old publication remains UNKNOWN. AcceptExistingArtifactsWithUnknownEffects skips only this " +
    "prerequisite publication and proceeds to normal checks using existing artifacts. " +
    "AuthorizeNewPublicationWithUnknownEffects permits one new publication through ordinary Advance, " +
    "accepting possible duplicate, concurrent or partial publication effects. Defer changes nothing. " +
    "A current source guard identifies current bytes only; it cannot retrospectively prove publication success or writer termination."
  def choicesFor(target: Target): Vector[String] =
    if (qualified(target)) choices else choices.filterNot(_ == "AuthorizeQualifiedSuccessorPublication")
  def messageFor(target: Target): String = message + " Old request: " + target.unknown.request.asJson.noSpaces +
    "; status: " + target.unknown.status + "; reason: " + target.unknown.reason +
    target.unknown.process.fold("; no retained process result")("; retained process: " + _.noSpaces) +
    target.observation.fold(". Historical writer lifecycle and source qualification remain unknown.") { observation =>
      ". Independently observed writer process: " + observation.lifecycle.process +
        (if (qualified(target)) ". Exact source qualification is retained; AuthorizeQualifiedSuccessorPublication is also available."
         else ". Exact source qualification remains unknown.")
    }

  final case class Scope(componentInstance: String, participant: String, sessionId: String,
    frontierRevision: String, candidateRecordRevision: String, request: LocalPublication.Request,
    binding: D.Binding, guard: R.Guard)
  object Scope {
    given Encoder[Scope] = deriveEncoder[Scope].mapJson(publicJson)
    given Decoder[Scope] = _strict(deriveDecoder, scopeValid)
  }
  /** The complete writer process is retained as opaque canonical CNCF JSON. */
  final case class WriterLifecycle(kind: String, invocation: ContextReference, sourceReference: ContextReference,
    scope: String, process: String)
  object WriterLifecycle {
    given Encoder[WriterLifecycle] = deriveEncoder[WriterLifecycle].mapJson(publicJson)
    given Decoder[WriterLifecycle] = _strict(deriveDecoder, lifecycleValid)
  }
  final case class Qualification(reference: ContextReference, sourceReference: ContextReference,
    consumption: R.Consumption, source: LocalPublication.Source)
  object Qualification {
    given Encoder[Qualification] = deriveEncoder[Qualification].mapJson(publicJson)
    given Decoder[Qualification] = _strict(deriveDecoder, qualificationValid)
  }
  final case class Observation(schemaVersion: String, reference: ContextReference, scope: Scope,
    lifecycle: WriterLifecycle, qualification: Option[Qualification])
  object Observation {
    given Encoder[Observation] = deriveEncoder[Observation].mapJson(publicJson)
    given Decoder[Observation] = _strict(deriveDecoder, observationValid)
  }
  trait ObservationPort {
    def observeC(core: ActionCall.Core, scope: Scope): Consequence[Option[Observation]]
  }
  final case class Source(native: P.Source, requestingResult: ContextReference,
    recovery: Option[P.Progress], approvals: Vector[D.Approval], primaryOrigin: Option[P.PrimaryTarget] = None)
  final case class Target(schemaVersion: String, componentInstance: String, participant: String,
    sessionId: String, frontierRevision: String, controller: String, plan: O.Plan, base: O.Snapshot,
    generation: Long, completed: Vector[O.ClosedStep], repairProgress: GoalPhaseRepairProgress.Snapshot,
    candidateRecordRevision: String, candidate: C.Payload, admission: D.Admission,
    ancestors: Vector[R.Ancestor], unknown: LocalPublication.Result, publicationPolicy: String,
    processPolicy: String, guard: R.Guard, retainedGuard: Option[R.Guard],
    completedRecovery: Option[ContextReference], requestingResult: ContextReference,
    observation: Option[Observation], scopeApprovals: Vector[D.Approval] = Vector.empty)
  object Target {
    given Encoder[Target] = Encoder.instance(v => publicJson(Json.obj(
      "schemaVersion" -> v.schemaVersion.asJson, "componentInstance" -> v.componentInstance.asJson,
      "participant" -> v.participant.asJson, "sessionId" -> v.sessionId.asJson,
      "frontierRevision" -> v.frontierRevision.asJson, "controller" -> v.controller.asJson,
      "plan" -> v.plan.asJson, "base" -> v.base.asJson, "generation" -> v.generation.asJson,
      "completed" -> v.completed.asJson, "repairProgress" -> v.repairProgress.asJson,
      "candidateRecordRevision" -> v.candidateRecordRevision.asJson, "candidate" -> v.candidate.asJson,
      "admission" -> v.admission.asJson, "ancestors" -> v.ancestors.asJson, "unknown" -> v.unknown.asJson,
      "publicationPolicy" -> v.publicationPolicy.asJson, "processPolicy" -> v.processPolicy.asJson,
      "guard" -> v.guard.asJson, "retainedGuard" -> v.retainedGuard.asJson,
      "completedRecovery" -> v.completedRecovery.asJson, "requestingResult" -> v.requestingResult.asJson,
      "observation" -> v.observation.asJson, "scopeApprovals" -> v.scopeApprovals.asJson)))
    private val _decoder: Decoder[Target] = Decoder.instance(c => for {
      schema <- c.get[String]("schemaVersion")
      component <- c.get[String]("componentInstance")
      participant <- c.get[String]("participant")
      session <- c.get[String]("sessionId")
      frontier <- c.get[String]("frontierRevision")
      controller <- c.get[String]("controller")
      plan <- c.get[O.Plan]("plan")
      base <- c.get[O.Snapshot]("base")
      generation <- c.get[Long]("generation")
      completed <- c.get[Vector[O.ClosedStep]]("completed")
      repair <- c.get[GoalPhaseRepairProgress.Snapshot]("repairProgress")
      candidateRevision <- c.get[String]("candidateRecordRevision")
      candidate <- c.get[C.Payload]("candidate")
      admission <- c.get[D.Admission]("admission")
      ancestors <- c.get[Vector[R.Ancestor]]("ancestors")
      unknown <- c.get[LocalPublication.Result]("unknown")
      publication <- c.get[String]("publicationPolicy")
      process <- c.get[String]("processPolicy")
      guard <- c.get[R.Guard]("guard")
      retainedGuard <- c.get[Option[R.Guard]]("retainedGuard")
      recovery <- c.get[Option[ContextReference]]("completedRecovery")
      result <- c.get[ContextReference]("requestingResult")
      observation <- c.get[Option[Observation]]("observation")
      approvals <- c.get[Vector[D.Approval]]("scopeApprovals")
    } yield Target(schema, component, participant, session, frontier, controller, plan, base, generation,
      completed, repair, candidateRevision, candidate, admission, ancestors, unknown, publication, process,
      guard, retainedGuard, recovery, result, observation, approvals))
    given Decoder[Target] = _strict(_decoder, targetValid)
  }
  final case class Direction(target: Target, reference: ContextReference, rationale: String,
    choice: Option[String] = None)
  object Direction {
    given Encoder[Direction] = deriveEncoder[Direction].mapJson(publicJson)
    given Decoder[Direction] = _strict(deriveDecoder, directionValid)
  }
  final case class Saved(record: InstanceRecord, direction: Direction)
  final case class ExistingArtifactAdoption(direction: ContextReference, previous: LocalPublication.Result,
    source: LocalPublication.Source, guard: R.Guard)
  final case class SourceSaved(record: InstanceRecord, direction: R.Direction)
  final case class SourceProgress(source: Source, direction: SourceSaved,
    saved: Option[C.Saved], complete: Boolean, unknown: Boolean) {
    def record: InstanceRecord = saved.map(_.record).getOrElse(direction.record)
  }

  private def _strict[A: Encoder](decoder: Decoder[A], valid: A => Boolean): Decoder[A] =
    Decoder.instance(cursor => decoder(cursor).flatMap(value =>
      if (valid(value) && summon[Encoder[A]].apply(value) == cursor.value) Right(value)
      else Left(DecodingFailure("Noncanonical, crossed or incomplete prerequisite UNKNOWN data", cursor.history))))
  private[smworkflow] def publicJson(value: Json): Json = value.arrayOrObject(value,
    values => Json.fromValues(values.map(publicJson)), fields => Json.fromFields(fields.toVector.collect {
      case (key, item) if !item.isNull => key -> publicJson(item)
    }))
  private def _sorted(value: Json): String = io.circe.Printer.noSpaces.copy(sortKeys = true).print(value)
  private def _text(value: String): Boolean = value != null && value.nonEmpty && value.trim == value &&
    !value.exists(_.isControl)
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _opaque(value: String): Boolean = value != null && io.circe.parser.parse(value).toOption.exists(j =>
    j.asObject.exists(_.nonEmpty) && _sorted(j) == value)
  private def _reference(value: ContextReference): Boolean = R.referenceValid(value)
  private def _source(guard: R.Guard): LocalPublication.Source = LocalPublication.Source(guard.head, guard.indexTree,
    guard.dirty.map(d => LocalPublication.Change(d.path, d.index, d.worktree, d.renamedFrom)))
  def scopeValid(value: Scope): Boolean = value != null && _text(value.componentInstance) && _text(value.participant) &&
    _text(value.sessionId) && _revision(value.frontierRevision) && _revision(value.candidateRecordRevision) &&
    BigInt(value.candidateRecordRevision) <= BigInt(value.frontierRevision) && value.request != null &&
    value.request.input != null && value.request.input.ownerSessionId.contains(value.sessionId) &&
    !value.request.input.explicit && value.request.input.answer.isEmpty && value.request.participant == value.participant &&
    value.request.target != null && value.request.target.beforeChecks &&
    D.bindingValid(value.binding) && value.request.target.dependency.contains(value.binding.identity) &&
    value.request.target.workspace == value.binding.dependency.workspace && R.guardValid(value.guard) &&
    value.guard.root == value.binding.root && value.guard.branch == value.binding.branch &&
    value.guard.head == value.binding.base.head && value.guard.committedTree == value.binding.base.tree &&
    value.guard.selectedFiles.map(_.path) == value.binding.paths
  def lifecycleValid(value: WriterLifecycle): Boolean = value != null && _reference(value.invocation) && _reference(value.sourceReference) &&
    value.scope == "publication-writer-lifecycle-complete" && value.kind == "cncf-writer-exited" &&
    value.process != null && io.circe.parser.parse(value.process).toOption.exists(j => _sorted(j) == value.process &&
      WorkflowSbtCapture.decode(j).toOption.exists(p => WorkflowSbtCapture.exitCode(p).nonEmpty))
  def qualificationValid(value: Qualification): Boolean = value != null && _reference(value.reference) &&
    _reference(value.sourceReference) && R.consumptionValid(value.consumption) && value.source != null &&
    value.source.head != null && value.source.tree != null && value.source.dirty != null
  def observationValid(value: Observation): Boolean = value != null && value.schemaVersion == observationSchema &&
    _reference(value.reference) && scopeValid(value.scope) && lifecycleValid(value.lifecycle) && value.qualification != null &&
    value.qualification.forall(q => qualificationValid(q) && q.source == _source(value.scope.guard) &&
      q.consumption.sourceRoot == value.scope.binding.root &&
      q.consumption.expectedModes.map(_.path) == value.scope.binding.paths &&
      q.consumption.primaryWorkspace == value.scope.binding.requester.workspace)
  def scopeOf(value: Target): Scope = Scope(value.componentInstance, value.participant, value.sessionId,
    value.frontierRevision, value.candidateRecordRevision, value.unknown.request, bindingOf(value.candidate), value.guard)
  def bindingOf(value: C.Payload): D.Binding = value.dependencySourceCapture.map(R.bindingOf)
    .orElse(value.dependencyAdmission.map(_.binding)).get
  def targetValid(value: Target): Boolean = value != null && value.schemaVersion == schema &&
    _text(value.componentInstance) && _text(value.participant) && _text(value.sessionId) && _revision(value.frontierRevision) &&
    _revision(value.candidateRecordRevision) && BigInt(value.candidateRecordRevision) <= BigInt(value.frontierRevision) &&
    _opaque(value.controller) && value.plan != null && O.validatePlanC(value.plan).isSuccess && value.base != null &&
    value.generation > 0 && value.generation < Long.MaxValue && value.completed != null &&
    value.completed.map(_.step) == value.plan.steps.take(value.completed.size).map(_.identity) &&
    value.completed.size < value.plan.steps.size && GoalPhaseRepairProgress.validateC(value.repairProgress).isSuccess &&
    value.repairProgress.limit == value.plan.repairLimit && value.candidate != null &&
    value.candidate.asJson.as[C.Payload].contains(value.candidate) && value.candidate.candidate.nonEmpty &&
    value.candidate.diff.nonEmpty && value.candidate.step == value.plan.steps(value.completed.size).identity &&
    (value.candidate.dependency.nonEmpty || value.candidate.dependencySourceCapture.nonEmpty) &&
    D.admissionValid(value.admission) && (value.candidate.dependencyAdmission.contains(value.admission) ||
      value.candidate.dependencySourceDirection.exists(_.target.predecessor == value.admission)) &&
    value.ancestors != null && value.ancestors.forall(R.ancestorValid) &&
    value.unknown != null && value.unknown.request != null && value.unknown.status == "uncertain" &&
    value.unknown.nextPreparation != null && value.unknown.nextPreparation.isEmpty &&
    scopeValid(scopeOf(value)) && _opaque(value.publicationPolicy) && _opaque(value.processPolicy) &&
    value.retainedGuard != null && value.retainedGuard.forall(R.guardValid) &&
    value.completedRecovery != null && value.completedRecovery.forall(_reference) && _reference(value.requestingResult) &&
    value.scopeApprovals != null && value.scopeApprovals.size <= 16 && value.scopeApprovals.forall(D.approvalValid) &&
    value.scopeApprovals.map(_.decision).distinct.size == value.scopeApprovals.size &&
    value.observation != null && value.observation.forall(o => observationValid(o) && o.scope == scopeOf(value)) && {
      val controller = io.circe.parser.parse(value.controller).toOption.get.hcursor
      controller.get[String]("stage").contains("Validation") && controller.get[O.Plan]("plan").contains(value.plan) &&
        controller.get[O.Snapshot]("base").contains(value.base) && controller.get[Long]("generation").contains(value.generation) &&
        controller.get[Vector[O.ClosedStep]]("completed").contains(value.completed) &&
        controller.get[Int]("cursor").contains(value.completed.size)
    }
  def directionValid(value: Direction): Boolean = value != null && targetValid(value.target) &&
    _reference(value.reference) && _text(value.rationale) && value.choice != null &&
    value.choice.fold(qualified(value.target))(choice =>
      choice == "AcceptExistingArtifactsWithUnknownEffects" || choice == "AuthorizeNewPublicationWithUnknownEffects")
  def qualified(value: Target): Boolean = targetValid(value) && value.observation.exists(_.qualification.exists { q =>
    value.candidate.dependencySourceCapture match {
      case Some(capture) => R.consumptionOf(capture) == q.consumption &&
        capture.postStageGuard == value.guard
      case None => value.candidate.dependency.exists(_.candidate == O.Snapshot(value.guard.head, value.guard.indexTree)) &&
        !value.guard.dirty.exists(d => bindingOf(value.candidate).paths.contains(d.path) && d.worktree != " ")
    }
  })
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def targetC(value: Record): Consequence[Target] = jsonC(value).flatMap(j => j.as[Target].left.map(_.message)
    .fold(F.invalid, Consequence.success))
  def jsonC(value: Record): Consequence[Json] = if (value == null) F.invalid("UNKNOWN target is absent")
    else value.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).fold(F.invalid, Consequence.success))
  def isUnknownTargetC(value: Record): Consequence[Boolean] = jsonC(value).map(_.hcursor.get[String]("schemaVersion").contains(schema))
  def observationKey(scope: Scope): String = ThreeWorkflowExternalEvidencePorts.key(scope.request.input.requestIdentity,
    scope.sessionId, scope.frontierRevision)
  def observationBinding(value: Observation): Json = Json.obj("componentInstance" -> value.scope.componentInstance.asJson,
    "prerequisitePublicationObservation" -> value.asJson)
  def selectObservation(value: Json): Either[String, Json] = for {
    raw <- Option(value).toRight("Publication observation is absent")
    observation <- raw.hcursor.get[Observation]("prerequisitePublicationObservation").left.map(_.message)
    selected = observationBinding(observation)
    _ <- Either.cond(selected == raw, (), "Publication observation has crossed or unknown fields")
  } yield selected
  def bindingC(component: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (targetValid(target) && component == target.componentInstance && participant == target.participant &&
        choicesFor(target).contains(choice) && _text(rationale) && R.referenceValid(reference)) Consequence.success(Json.obj(
      "componentInstance" -> component.asJson, "prerequisitePublicationUnknownRecovery" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson))))
    else F.invalid("UNKNOWN recovery answer crosses its target, choice or participant")
  def selectBinding(value: Json): Either[String, Json] = for {
    raw <- Option(value).toRight("UNKNOWN recovery answer is absent")
    component <- raw.hcursor.get[String]("componentInstance").left.map(_.message)
    body = raw.hcursor.downField("prerequisitePublicationUnknownRecovery")
    target <- body.get[Target]("target").left.map(_.message)
    answer = body.downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    selected <- bindingC(component, target, choice, rationale, participant, reference)
      .map[Either[String, Json]](Right(_)).fold(e => Left(e.toString), identity)
    _ <- Either.cond(selected == raw, (), "UNKNOWN recovery binding has contradictory fields")
  } yield selected

  private def _publication_c(source: Source, d: F.Declaration, core: ActionCall.Core):
      Consequence[(LocalPublicationRuntime.Policy, LocalPublication.Result)] = for {
    policy <- d.localPublication.map(Consequence.success).getOrElse(F.invalid("UNKNOWN prerequisite policy is absent"))
    binding = bindingOf(source.native.candidate.payload)
    target <- policy.targets.filter(t => t.beforeChecks && t.dependency.contains(binding.identity) &&
      t.workspace == binding.dependency.workspace) match {
      case Vector(value) => Consequence.success(value)
      case _ => F.invalid("UNKNOWN recovery requires one exact declared dependency target")
    }
    latest <- LocalPublicationRuntime.collectLatestC(core, policy,
      LocalPublication.LatestCollectionInput(target.identity, source.native.from.identity.value))
    unknown <- latest.filter(r => r.request.target == target &&
      r.request.input.ownerSessionId.contains(source.native.from.identity.value) && !r.request.input.explicit &&
      r.request.participant == source.native.session.participantIdentity &&
      (source.native.candidate.payload.dependencySourceDirection.flatMap(_.target.waitOrigin).exists(_.unknown.request == r.request) ||
        r.request.input.requestIdentity.startsWith(LocalPublicationRuntime.prerequisiteInput(source.native.from.identity.value,
          source.native.candidate.payload, target).requestIdentity + ":prerequisite:"))).map(Consequence.success)
      .getOrElse(F.invalid("The latest actual prerequisite is not this participant's UNKNOWN request"))
  } yield policy -> unknown
  def isUnknownC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Boolean] =
    _publication_c(source, d, core).map(_._2.status == "uncertain")

  private def _retained_guard(source: Source): Option[R.Guard] =
    source.native.candidate.payload.dependencySourceCapture.map(_.postStageGuard).orElse(source.recovery.filter(_.complete)
      .map(p => p.saved.payload.stages.lastOption.map(_.after).getOrElse(p.direction.payload.direction.target.preGuard)))

  /** Metadata and independently supplied evidence share one exact native
    * origin. Only the caller chooses current observation or retained proof. */
  private def _native_target_c(source: Source, d: F.Declaration, core: ActionCall.Core,
    guard: => Consequence[R.Guard]): Consequence[Target] = for {
    _ <- _eligible_c(source)
    native = source.native
    _ <- if (native.from == native.session.current) Consequence.unit else F.invalid("UNKNOWN prerequisite frontier is stale")
    _ <- _unstarted_c(source, d, core)
    publication <- _publication_c(source, d, core)
    selectedguard <- guard
    process <- P.processPolicyC(d, core)
    admission = native.candidate.payload.dependencySourceDirection.map(_.target.predecessor)
      .orElse(native.candidate.payload.dependencyAdmission).get
    retained = _retained_guard(source)
    target = Target(schema, core.component.get.core.instanceId.canonicalKey, native.session.participantIdentity,
      native.from.identity.value, native.from.revision.value.toString, _sorted(P.controller(native.controller)), native.controller.plan,
      native.controller.base, native.controller.generation, native.controller.completed, GoalPhaseRepairProgress.snapshot(native.controller),
      native.candidate.record.revision.value.toString, native.candidate.payload, admission,
      native.chain.accepted.map(a => R.Ancestor(a.capture, a.closureRevision)), publication._2,
      P.publicationPolicy(publication._1), process, selectedguard, retained, source.recovery.filter(_.complete)
        .map(p => ContextReference(p.saved.record.identity.value, p.saved.record.revision.value.toString)), source.requestingResult, None,
      source.approvals)
    observed <- d.prerequisitePublicationRecovery.flatMap(_.observation).fold(Consequence.success(Option.empty[Observation]))(
      _.observeC(core, scopeOf(target)))
    result = target.copy(observation = observed)
    _ <- if (targetValid(result)) Consequence.unit else F.invalid("UNKNOWN target crosses actual native provenance")
  } yield result

  def targetForC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Target] = for {
    target <- _native_target_c(source, d, core, source.native.candidate.payload.dependencySourceCapture match {
      case Some(capture) => R.freshnessC(core, capture).map(_ => capture.postStageGuard)
      case None => R.observeC(core, bindingOf(source.native.candidate.payload), Vector.empty)
    })
    _ <- _qualification_declared_c(target.observation, d, target.plan, target.candidate.step)
  } yield target
  private def _qualification_declared_c(observation: Option[Observation], d: F.Declaration,
    plan: O.Plan, step: String): Consequence[Unit] = observation.flatMap(_.qualification) match {
    case None => Consequence.unit
    case Some(qualified) => for {
      checks <- GoalPhaseNativeValidation.selectedChecksC(d, plan, Some(step))
      _ <- if (d.dependencySourceConsumptions.contains(qualified.consumption) && checks.forall(check =>
          qualified.consumption.programs.exists(p => p.check == check.identity && p.kind == check.kind.wire))) Consequence.unit
        else F.invalid("Independent qualification does not match the declared selected source programs")
    } yield ()
  }
  private def _eligible_c(source: Source): Consequence[Unit] = {
    val n = Option(source).map(_.native)
    if (n.exists(s => s.controller.stage == O.Stage.Validation && s.from.lifecycle == Lifecycle.Active &&
        s.from.suspension.isEmpty && s.controller.cursor < s.controller.plan.steps.size && s.controller.pendingRepair.isEmpty &&
        s.controller.closureIntent.isEmpty && s.candidate.payload.candidate.nonEmpty &&
        (s.candidate.payload.dependency.nonEmpty || s.candidate.payload.dependencySourceCapture.nonEmpty)) &&
        _reference(source.requestingResult) && source.recovery.forall(_.complete)) Consequence.unit
    else F.invalid("UNKNOWN prerequisite recovery requires its actual unstarted ordinary Validation lineage")
  }
  private def _unstarted_c(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Unit] =
    GoalPhaseNativeValidation.unstartedPrerequisiteC(source.native.session, d, core, source.native.candidate,
      source.native.controller, source.native.candidate.record, source.native.documentPolicy,
      source.native.recordingPaths, source.native.chain)
  def describeC(source: Source, d: F.Declaration, core: ActionCall.Core):
      Consequence[V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult] = targetForC(source, d, core).map(t =>
    V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult(t.sessionId,
      "direction-required", messageFor(t), t.componentInstance,
      targetRecord(t), choicesFor(t), P.recoveryOperation))
  def recoverC(source: Source, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhasePrerequisitePublicationRecoveryInput): Consequence[V.GoalPhasePrerequisitePublicationRecoveryResult] = for {
    supplied <- targetC(input.target)
    actual <- targetForC(source, d, core)
    _ <- if (actual == supplied && input.sessionId == actual.sessionId && choicesFor(actual).contains(input.choice) &&
        input.reference != null && _reference(ContextReference(input.reference.identity, input.reference.revision)) && _text(input.rationale))
      Consequence.unit else F.invalid("UNKNOWN target, participant, reference or frontier changed")
    _ <- if (input.choice == "Defer") Consequence.unit else for {
      installation <- d.prerequisitePublicationRecovery.map(Consequence.success).getOrElse(F.invalid("Independent Human provider is absent"))
      reference = ContextReference(input.reference.identity, input.reference.revision)
      binding <- bindingC(actual.componentInstance, actual, input.choice, input.rationale, actual.participant, reference)
      _ <- installation.evidence.admitC(core, reference, binding)
      _ <- _save_direction_c(source, d, core, Direction(actual, reference, input.rationale,
        Option.unless(input.choice == "AuthorizeQualifiedSuccessorPublication")(input.choice)))
    } yield ()
  } yield V.GoalPhasePrerequisitePublicationRecoveryResult(input.sessionId, input.choice != "Defer",
    input.choice match {
      case "Defer" => "deferred"
      case "AcceptExistingArtifactsWithUnknownEffects" => "existing-artifacts-accepted-with-unknown-effects"
      case "AuthorizeNewPublicationWithUnknownEffects" => "new-publication-authorized-with-unknown-effects"
      case _ => "qualified-successor-authorized"
    }, messageFor(actual))

  private def _payload_codec[A: Encoder: Decoder](identity: String): WorkflowResultJsonV1.PayloadCodec[A] =
    new WorkflowResultJsonV1.PayloadCodec[A] {
      val typeIdentity = identity
      def encode(value: A): Json = Json.obj("schemaVersion" -> schema.asJson, "payload" -> value.asJson)
      def decode(value: Json): Either[String, A] = for {
        version <- value.hcursor.get[String]("schemaVersion").left.map(_.message)
        payload <- value.hcursor.get[A]("payload").left.map(_.message)
        _ <- Either.cond(version == schema && encode(payload) == value, (), "UNKNOWN fact has mixed schema or fields")
      } yield payload
    }
  val codec = _payload_codec[Direction]("GoalPhasePrerequisitePublicationUnknownDirection")
  val sourceCodec = _payload_codec[R.Direction]("GoalPhasePrerequisiteWaitSourceDirection")
  val primarySourceCodec: WorkflowResultJsonV1.PayloadCodec[R.Direction] = new WorkflowResultJsonV1.PayloadCodec[R.Direction] {
    val typeIdentity = "GoalPhasePrerequisitePrimarySourceDirection"
    def encode(value: R.Direction): Json = Json.obj("schemaVersion" -> R.primarySchema.asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, R.Direction] = for {
      version <- value.hcursor.get[String]("schemaVersion").left.map(_.message)
      direction <- value.hcursor.get[R.Direction]("payload").left.map(_.message)
      _ <- Either.cond(version == R.primarySchema && direction.target.schemaVersion == R.primarySchema && encode(direction) == value,
        (), "Primary source fact has mixed version or fields")
    } yield direction
  }
  private def _source_prefix(direction: R.Direction): String =
    if (direction.target.primaryFailureOrigin.nonEmpty) primarySourceCausationPrefix else sourceCausationPrefix
  private def _transition_reference(from: InstanceRecord, prefix: String, key: String): WorkflowMechanicalTransition.Reference =
    WorkflowMechanicalTransition.Reference(from.identity.value + ":" + prefix + from.revision.value + ":" + key, "1")
  private def _cause(from: InstanceRecord, prefix: String, key: String): String = prefix + from.revision.value +
    (if (key == "direction") "" else ":" + key)
  private def _save_c[A](source: Source, d: F.Declaration, core: ActionCall.Core, codec: WorkflowResultJsonV1.PayloadCodec[A],
    prefix: String, key: String, from: InstanceRecord, payload: A): Consequence[(InstanceRecord, A)] = for {
    _ <- if (from.suspension.isEmpty && from.currentProgression.contains(ProgressionReference("phase.Validation"))) Consequence.unit
      else F.invalid("Prerequisite mechanical continuation crosses ordinary Validation")
    event = if (prefix == causationPrefix) "prerequisite-publication-unknown-recovery" else "prerequisite-wait-source"
    _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits("Validation", event, "Validation")) Consequence.unit
      else F.invalid("Prerequisite continuation self-transition is absent from CML")
    adapter <- source.native.session.storage.bindMechanicalC(F.advanceOperation, codec)
    saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration,
      _transition_reference(source.native.from, prefix, key), from, from.revision,
      F.history(from, "phase.Validation", _cause(source.native.from, prefix, key)), TypedValue(codec.typeIdentity, payload)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield saved.receipt.next -> saved.receipt.payload.value
  private def _load_c[A](source: Source, core: ActionCall.Core, codec: WorkflowResultJsonV1.PayloadCodec[A],
    prefix: String, key: String): Consequence[Option[(InstanceRecord, A)]] = for {
    adapter <- source.native.session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- adapter.loadRetainedC(_transition_reference(source.native.from, prefix, key))(using core.executionContext)
  } yield retained.map(r => r.receipt.next -> r.receipt.payload.value)
  private def _adjacent_c(source: Source, before: InstanceRecord, next: InstanceRecord, prefix: String, key: String): Consequence[Unit] =
    GoalPhaseNativeHistory.prefixC(source.native.session.current, next.revision).flatMap(actual =>
      if (actual == next && next.identity == before.identity && next.definition == before.definition &&
          next.revision == before.revision.next && next.history.dropRight(1) == before.history &&
          next.lifecycle == Lifecycle.Active && next.suspension.isEmpty && next.currentProgression == before.currentProgression &&
          next.causation.value == _cause(source.native.from, prefix, key)) Consequence.unit
      else F.invalid("Prerequisite continuation is not an actual adjacent native transition"))
  private def _save_direction_c(source: Source, d: F.Declaration, core: ActionCall.Core, direction: Direction): Consequence[Saved] =
    _save_c(source, d, core, codec, causationPrefix, "direction", source.native.from, direction).map((r, p) => Saved(r, p))
  def retainedDirectionC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Saved] = for {
    saved <- _load_c(source, core, codec, causationPrefix, "direction").flatMap(_.map((r, p) => Consequence.success(Saved(r, p)))
      .getOrElse(F.invalid("UNKNOWN prerequisite history has no actual Direction")))
    _ <- _adjacent_c(source, source.native.from, saved.record, causationPrefix, "direction")
    _ <- _historical_c(source, d, core, saved.direction.target,
      primarycontinuation = saved.direction.choice.contains("AcceptExistingArtifactsWithUnknownEffects"))
    _ <- _qualification_declared_c(saved.direction.target.observation, d,
      saved.direction.target.plan, saved.direction.target.candidate.step)
  } yield saved
  /** Historical primary traversal may preserve catalog-output policy history;
    * storage ownership and every complete target definition remain exact. */
  private[admission] def historicalPublicationPolicyCompatible(historical: String,
    current: LocalPublicationRuntime.Policy, primaryContinuation: Boolean): Boolean =
    LocalPublicationRuntime.validateC(current).isSuccess &&
      io.circe.parser.parse(Option(historical).getOrElse("")).toOption.exists { previous =>
        val actual = io.circe.parser.parse(P.publicationPolicy(current)).toOption.get
        previous.asObject.exists(_.keys.toSet == Set("storeName", "collection", "targets", "catalogOutputs")) &&
          _sorted(previous) == historical && Vector("storeName", "collection", "targets").forall(key =>
            previous.hcursor.downField(key).focus == actual.hcursor.downField(key).focus) &&
          previous.hcursor.get[Vector[LocalPublicationRuntime.CatalogOutput]]("catalogOutputs").toOption.exists(outputs =>
            previous.hcursor.downField("catalogOutputs").focus.contains(outputs.asJson) &&
              LocalPublicationRuntime.validateC(current.copy(catalogOutputs = outputs)).isSuccess &&
              (primaryContinuation || previous == actual))
      }

  private[admission] def historicalPoliciesC(target: Target, current: LocalPublicationRuntime.Policy,
    process: String, primaryContinuation: Boolean, primaryHistory: => Consequence[Unit],
    retainedUnknown: => Consequence[Unit]): Consequence[Unit] = for {
    _ <- if (historicalPublicationPolicyCompatible(target.publicationPolicy, current, primaryContinuation) &&
        (target.processPolicy == process || primaryContinuation)) Consequence.unit
      else F.invalid("Retained UNKNOWN Direction rewrites native provenance")
    _ <- if (target.processPolicy == process && target.publicationPolicy == P.publicationPolicy(current)) Consequence.unit
      else primaryHistory
    _ <- retainedUnknown
  } yield ()

  private def _historical_c(source: Source, d: F.Declaration, core: ActionCall.Core, target: Target,
    primarycontinuation: Boolean = false): Consequence[Unit] = for {
    _ <- _eligible_c(source)
    n = source.native
    policy <- d.localPublication.map(Consequence.success).getOrElse(F.invalid("Retained publication policy is absent"))
    process <- P.processPolicyC(d, core)
    _ <- if (targetValid(target) && target.componentInstance == core.component.get.core.instanceId.canonicalKey &&
        target.participant == n.session.participantIdentity && target.sessionId == n.from.identity.value &&
        target.frontierRevision == n.from.revision.value.toString && target.controller == _sorted(P.controller(n.controller)) &&
        target.candidate == n.candidate.payload && target.candidateRecordRevision == n.candidate.record.revision.value.toString &&
        target.plan == n.controller.plan && target.base == n.controller.base && target.generation == n.controller.generation &&
        target.completed == n.controller.completed && target.repairProgress == GoalPhaseRepairProgress.snapshot(n.controller) &&
        target.ancestors == n.chain.accepted.map(a => R.Ancestor(a.capture, a.closureRevision)) &&
        target.requestingResult == source.requestingResult && target.scopeApprovals == source.approvals)
      Consequence.unit else F.invalid("Retained UNKNOWN Direction rewrites native provenance")
    _ <- historicalPoliciesC(target, policy, process, primarycontinuation,
      LocalPublicationRuntime.primaryHistoryC(core, policy, target),
      LocalPublicationRuntime.retainedFailureC(core, policy, target.unknown))
    _ <- n.candidate.payload.dependency.fold(Consequence.unit)(n.chain.captureC)
    _ <- n.candidate.payload.dependencySourceCapture.fold(Consequence.unit)(n.chain.sourceCaptureC)
    _ <- _unstarted_c(source, d, core)
  } yield ()
  def permit(saved: Saved): Option[LocalPublication.QualifiedSuccessor] =
    Option.when(directionValid(saved.direction) &&
      !saved.direction.choice.contains("AcceptExistingArtifactsWithUnknownEffects"))(LocalPublication.QualifiedSuccessor(
        saved.direction.reference, saved.direction.target.unknown.request, _source(saved.direction.target.guard)))
  def existingArtifactAdoption(saved: Saved): Option[ExistingArtifactAdoption] =
    Option.when(directionValid(saved.direction) && saved.direction.choice.contains("AcceptExistingArtifactsWithUnknownEffects"))(
      ExistingArtifactAdoption(saved.direction.reference, saved.direction.target.unknown,
        _source(saved.direction.target.guard), saved.direction.target.guard))

  /** The Wait source branch shares R's proposal, inheritance and capture owners.
    * Its origin is an actual Validation target, never an unanswered question. */
  def sourceTargetC(source: Source, d: F.Declaration, core: ActionCall.Core, proposal: R.Proposal): Consequence[R.Target] =
    if (source.primaryOrigin.nonEmpty) _primary_source_target_c(source, d, core, proposal) else for {
    original <- _native_target_c(source, d, core, _retained_guard(source).map(Consequence.success)
      .getOrElse(F.invalid("Old source bytes have no retained recovery/capture guard")))
    _ <- if (R.proposalValid(proposal) && proposal.schemaVersion == R.sourceSchema &&
        original.candidate.dependencySourceDirection.isEmpty) Consequence.unit
      else F.invalid("Wait source revision requires its original captured dependency and a source proposal")
    selected <- d.dependencySourceRevisions.find(b => b.identity == proposal.binding.identity &&
      b.revision.toString == proposal.binding.revision).map(Consequence.success).getOrElse(F.invalid("New source binding is not declared"))
    _ <- if (d.dependencySourceConsumptions.contains(proposal.consumption)) Consequence.unit else F.invalid("New consumption is not declared")
    target <- _source_selection_target_c(original, proposal, selected,
      (binding, exclusions) => R.observeC(core, binding, exclusions),
      (binding, exclusions, parent, previous) => R.waitCommittedObservationC(core, binding, exclusions, parent, previous))
  } yield target

  private def _primary_source_target_c(source: Source, d: F.Declaration, core: ActionCall.Core,
    proposal: R.Proposal): Consequence[R.Target] = for {
    original <- source.primaryOrigin.filter(o => P.primaryTargetValid(o) && o.phase == P.PrimaryPhase.SourceRequired &&
      o.frontierRevision == source.native.from.revision.value.toString && source.native.from == source.native.session.current)
      .map(Consequence.success).getOrElse(F.invalid("Primary source selection requires its exact SourceRequired frontier"))
    _ <- if (R.proposalValid(proposal) && proposal.schemaVersion == R.sourceSchema &&
        d.dependencySourceConsumptions.contains(proposal.consumption)) Consequence.unit else F.invalid("Primary source consumption is not declared")
    selected <- d.dependencySourceRevisions.find(b => b.identity == proposal.binding.identity &&
      b.revision.toString == proposal.binding.revision).map(Consequence.success).getOrElse(F.invalid("Primary source binding is not declared"))
    fresh <- R.observeC(core, selected, proposal.excludedPaths)
    predecessor = original.originalDirection.target.admission
    committed <- if (selected.base == predecessor.binding.base) Consequence.success(Option.empty[R.CommittedBaseSuccession])
      else R.waitCommittedObservationC(core, selected, proposal.excludedPaths, original.requestingResult, predecessor.binding.base).map(Some(_))
    target = R.Target(R.primarySchema, original.sessionId, "", original.frontierRevision, original.frontierRevision,
      original.plan, original.candidate.step, original.generation, original.base, original.completed, original.repairProgress,
      original.requestingResult, predecessor, original.originalDirection.target.ancestors, proposal, selected,
      Some(original.dependencyGuard), fresh, committedBaseSuccession = committed, primaryFailureOrigin = Some(original))
    _ <- if (R.targetValid(target)) Consequence.unit else F.invalid("Primary source proposal crosses inherited scope, program, identity or coverage")
  } yield target

  /** The production source route and its bounded construction specification
    * share the same selected-source observation order and inventory checks. */
  private[admission] def _source_selection_target_c(original: Target, proposal: R.Proposal, selected: D.Binding,
    observe: (D.Binding, Vector[String]) => Consequence[R.Guard],
    committedinventory: (D.Binding, Vector[String], ContextReference, O.Snapshot) => Consequence[R.CommittedBaseSuccession]):
      Consequence[R.Target] = for {
    _ <- if (targetValid(original) && original.retainedGuard.contains(original.guard) &&
        original.candidate.dependencySourceDirection.isEmpty && R.proposalValid(proposal) &&
        proposal.schemaVersion == R.sourceSchema && D.bindingValid(selected) &&
        proposal.binding == ContextReference(selected.identity, selected.revision.toString)) Consequence.unit
      else F.invalid("Wait source construction requires its exact retained origin and selected declaration")
    fresh <- observe(selected, proposal.excludedPaths)
    committed <- if (selected.base == original.admission.binding.base) Consequence.success(Option.empty[R.CommittedBaseSuccession])
      else committedinventory(selected, proposal.excludedPaths, original.requestingResult,
        original.admission.binding.base).map(Some(_))
    target = R.Target(R.waitSchema, original.sessionId, "", original.frontierRevision, original.frontierRevision,
      original.plan, original.candidate.step, original.generation, original.base, original.completed, original.repairProgress,
      original.requestingResult, original.admission, original.ancestors, proposal, selected, Some(original.guard), fresh,
      committedBaseSuccession = committed, waitOrigin = Some(original))
    _ <- if (R.targetValid(target)) Consequence.unit else F.invalid("Wait source proposal crosses inherited source, scope or coverage")
  } yield target
  def describeSourceC(source: Source, d: F.Declaration, core: ActionCall.Core, raw: Record):
      Consequence[V.GoalPhaseDependencySourceRevisionDescriptionResult] = for {
    proposal <- R.proposalC(raw)
    target <- sourceTargetC(source, d, core, proposal)
  } yield V.GoalPhaseDependencySourceRevisionDescriptionResult(target.sessionId, "direction-required",
    (if (target.primaryFailureOrigin.nonEmpty) "Select source-only authority or the concrete ordered source, artifact and one-primary continuation plan. " +
      "Adoption runs no effects. Separate ordinary Advances capture source, record artifact adoption, record primary authorization, then execute or collect."
    else R.message + " Selecting a source authorizes no publication, artifact adoption or validation; a separate Human publication " +
      "or artifact choice is required afterward. The old effects remain unresolved. " + messageFor(target.waitOrigin.get)),
    target.primaryFailureOrigin.map(_.componentInstance).getOrElse(target.waitOrigin.get.componentInstance), R.targetRecord(target), R.choicesFor(target), R.adoptionOperation)
  def adoptSourceC(source: Source, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhaseDependencySourceRevisionAdoptionInput): Consequence[V.GoalPhaseDependencySourceRevisionAdoptionResult] = for {
    supplied <- R.targetC(input.target)
    actual <- sourceTargetC(source, d, core, supplied.proposal)
    _ <- if (supplied == actual && input.sessionId == actual.sessionId && input.reference != null &&
        R.choicesFor(actual).contains(input.choice) && _text(input.rationale)) Consequence.unit else F.invalid("Wait source target or frontier changed")
    _ <- if (input.choice == "Defer") Consequence.unit else for {
      _ <- if (actual.primaryFailureOrigin.nonEmpty || input.choice == "AdoptDependencySourceRevisionWithUnknownEffects" ||
          actual.waitOrigin.get.observation.exists(o => lifecycleValid(o.lifecycle))) Consequence.unit
        else F.invalid("The old publication writer lifecycle is not independently established")
      installation <- d.dependencySourceRevision.map(Consequence.success).getOrElse(F.invalid("Independent source Human provider is absent"))
      reference = ContextReference(input.reference.identity, input.reference.revision)
      binding <- R.bindingC(actual.primaryFailureOrigin.map(_.componentInstance).getOrElse(actual.waitOrigin.get.componentInstance), actual, input.choice, input.rationale,
        source.native.session.participantIdentity, reference)
      _ <- installation.evidence.admitC(core, reference, binding)
      direction = R.Direction(actual, reference, source.native.session.participantIdentity, input.rationale,
        Option.when(input.choice == "AdoptDependencySourceRevisionWithUnknownEffects" || input.choice == R.combinedPrimaryChoice)(input.choice))
      _ <- _save_c(source, d, core, if (actual.primaryFailureOrigin.nonEmpty) primarySourceCodec else sourceCodec,
        _source_prefix(direction), "direction", source.native.from, direction)
    } yield ()
  } yield V.GoalPhaseDependencySourceRevisionAdoptionResult(input.sessionId, input.choice != "Defer",
    if (input.choice == "Defer") "deferred" else "dependency-source-revision-adopted", R.message)

  private def _source_candidate(direction: R.Direction, revision: String): C.Payload = direction.target.primaryFailureOrigin
    .map(_.candidate).getOrElse(direction.target.waitOrigin.get.candidate).copy(
    sourceRevision = revision, dependencyAdmission = None, dependencyStaged = Vector.empty, dependency = None, dependencyOrigin = None,
    dependencySourceDirection = Some(direction), dependencySourceGuard = None, dependencySourceStages = Vector.empty,
    dependencySourceCapture = None, dependencySourceScope = None)
  def retainedSourceC(source: Source, d: F.Declaration, core: ActionCall.Core,
    primarySource: Option[P.PrimarySource] = None): Consequence[SourceProgress] = for {
    prefix <- Consequence.success(if (primarySource.nonEmpty) primarySourceCausationPrefix else sourceCausationPrefix)
    pair <- _load_c(source, core, if (primarySource.nonEmpty) primarySourceCodec else sourceCodec, prefix, "direction").flatMap(_.map(Consequence.success)
      .getOrElse(F.invalid("Wait source history omitted its native Direction")))
    (record, direction) = pair
    _ <- _adjacent_c(source, source.native.from, record, prefix, "direction")
    _ <- if (R.directionValid(direction) && (direction.target.waitOrigin.nonEmpty || direction.target.primaryFailureOrigin.nonEmpty) &&
        direction.participant == source.native.session.participantIdentity) Consequence.unit else F.invalid("Wait source Direction is crossed")
    _ <- if (primarySource.nonEmpty) P.checkPrimaryOriginC(primarySource.get, d, core, direction.target.primaryFailureOrigin.get)
      else _historical_c(source, d, core, direction.target.waitOrigin.get)
    _ <- if (primarySource.nonEmpty || _retained_guard(source).contains(direction.target.waitOrigin.get.guard) &&
        direction.target.waitOrigin.get.retainedGuard == _retained_guard(source)) Consequence.unit
      else F.invalid("Wait source origin differs from its actual retained source guard")
    initial = SourceProgress(source.copy(primaryOrigin = direction.target.primaryFailureOrigin), SourceSaved(record, direction), None, false, false)
    keys = Vector("capture-start", "dependency-source-inspection") ++
      R.stagingPaths(direction, None, direction.target.preStageGuard).indices.toVector.flatMap(i =>
        Vector("dependency-source-stage-intent-" + i, "dependency-source-stage-result-" + i)) ++
      Vector("dependency-source-observation-intent", "dependency-source-observation-result")
    progress <- keys.foldLeft(Consequence.success(initial)) { (prior, key) => for {
      previous <- prior
      next <- _load_c(source, core, C.codec, prefix, key)
      result <- next match {
        case None => Consequence.success(previous)
        case Some((next, payload)) => for {
          _ <- _adjacent_c(source, previous.record, next, prefix, key)
          _ <- if (payload.asJson.as[C.Payload].contains(payload) && R.candidateValid(payload) &&
              payload.dependencySourceDirection.contains(direction) && payload.sourceRevision == record.revision.value.toString &&
              payload.copy(dependencySourceGuard = None, dependencySourceStages = Vector.empty, dependencySourceCapture = None) ==
                _source_candidate(direction, record.revision.value.toString)) Consequence.unit
            else F.invalid("Wait source capture rewrites primary work or native source direction")
          expected = previous.saved.map(_.payload)
          _ <- if (key == "capture-start" && previous.saved.isEmpty && payload == _source_candidate(direction, record.revision.value.toString) ||
              key == "dependency-source-inspection" && expected.exists(p => payload == p.copy(dependencySourceGuard = payload.dependencySourceGuard)) &&
                payload.dependencySourceGuard.contains(direction.target.preStageGuard) ||
              key.contains("stage-intent-") && expected.contains(payload) ||
              key.contains("stage-result-") && expected.exists(p => payload.dependencySourceStages.size == p.dependencySourceStages.size + 1 &&
                payload == p.copy(dependencySourceStages = payload.dependencySourceStages)) ||
              key == "dependency-source-observation-intent" && expected.contains(payload) ||
              key == "dependency-source-observation-result" && expected.exists(p => payload.dependencySourceCapture.nonEmpty &&
                payload == p.copy(dependencySourceCapture = payload.dependencySourceCapture))) Consequence.unit
            else F.invalid("Wait source stage/capture skips or rewrites its actual retained prefix")
        } yield previous.copy(saved = Some(C.Saved(next, payload)), complete = key == "dependency-source-observation-result",
          unknown = key.contains("intent"))
      }
    } yield result }
    _ <- progress.saved.flatMap(_.payload.dependencySourceCapture).fold(Consequence.unit) { capture =>
      if (capture.generation == direction.target.generation + 1 && capture.sourceRevision == record.revision.value.toString)
        source.native.chain.sourceCaptureC(capture)
      else F.invalid("Wait source capture does not use the actual successor generation and native source revision")
    }
  } yield progress.copy(unknown = progress.unknown || direction.target.primaryFailureOrigin.isEmpty && progress.saved.nonEmpty && !progress.complete)
  def advanceSourceC(progress: SourceProgress, d: F.Declaration, core: ActionCall.Core,
    controller: O.State, acknowledge: String => Consequence[Unit]): Consequence[C.Saved] = for {
    _ <- if (!progress.complete && !progress.unknown &&
        (progress.saved.isEmpty || progress.direction.direction.target.primaryFailureOrigin.nonEmpty) &&
        progress.source.native.session.current == progress.record) Consequence.unit
      else F.invalid("Wait source capture cannot replay an unknown, completed or stale prefix")
    direction = progress.direction.direction
    prefix = _source_prefix(direction)
    initial = _source_candidate(direction, progress.direction.record.revision.value.toString)
    saved <- progress.saved.fold(_save_c(progress.source, d, core, C.codec, prefix, "capture-start", progress.direction.record, initial)
      .map((r, p) => C.Saved(r, p)))(Consequence.success)
    result <- R.captureC(core, controller, saved,
      key => _load_c(progress.source, core, C.codec, prefix, key).map(_.map((r, p) => C.Saved(r, p))),
      (key, from, payload) => _save_c(progress.source, d, core, C.codec, prefix, key, from, payload).map((r, p) => C.Saved(r, p)), acknowledge)
  } yield result
}
