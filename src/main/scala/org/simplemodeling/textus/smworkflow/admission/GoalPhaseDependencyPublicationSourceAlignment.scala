package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json, Printer}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.GitOperation
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseNativeCandidate as C
import GoalPhaseDependencySourceRevision as R
import GoalPhasePrerequisitePublicationRecovery as B
import GoalPhasePrerequisitePublicationUnknownRecovery as W
import GoalPhasePublishedDependencySourceAdoption as P
import GoalPhaseDependencyValidation.given

/** A separate artifact choice for a canonical source whose literal dependency
  * publisher still selects another repository. No program or authority changes.
  * @since   Nov.  9, 2026
  * @version Nov.  9, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseDependencyPublicationSourceAlignment {
  val targetSchema = "sm-workflow.goal-phase-dependency-publication-source-alignment.v1"
  val applicabilityTargetSchema = "sm-workflow.goal-phase-dependency-publication-source-alignment.v2"
  def isTargetSchema(value: String): Boolean = Set(targetSchema, applicabilityTargetSchema)(value)
  val causationPrefix = "ordered-dependency-publication-source-alignment:"
  val adoptionChoice = "AdoptRecollectedPublishedDependencyArtifact"
  val message = "The configured dependency publisher selects another repository. Supply an independently recollected normal publication for the exact current canonical source, then obtain its separate independent Human artifact choice through " +
    B.describeOperation + ". Describe/adopt publishes nothing and grants no primary attempt; ordinary Advance owns continuation."

  final case class TargetRoot(target: LocalPublication.Target, actualRoot: String)
  final case class Target(schemaVersion: String, componentInstance: String, participant: String,
    sessionId: String, frontierRevision: String, controller: String, candidateRecordRevision: String,
    candidate: String, capture: String, targets: Vector[TargetRoot], processPolicy: String,
    publicationPolicy: String, publication: Option[P.PublicationObservation], applicabilityIssues: Vector[String])
  /** Keep a complete but unsuccessful/inapplicable proposal so description can
    * offer Defer. Shape validation remains closed; qualification alone reuses
    * P.observationValid. This does not alter P's successful-observation codec. */
  private val _observation_decoder: Decoder[P.PublicationObservation] = Decoder.instance(cursor => (for {
    result <- cursor.get[Json]("result")
    status <- result.hcursor.get[String]("status")
    reason <- result.hcursor.get[String]("reason")
    publication <- result.hcursor.get[LocalPublication.Result]("publication")
    applicability <- result.hcursor.get[Option[LocalPublicationSourceApplicability.Fact]]("applicability")
    operation <- cursor.get[String]("collectOperation")
    recollection <- cursor.get[ContextReference]("recollectionReference")
    owner <- cursor.get[String]("publicationOwner")
    observed <- cursor.get[String]("observedAt")
    process <- cursor.get[String]("publisherProcessPolicy")
    destination <- cursor.get[String]("destination")
    artifact <- cursor.get[ContextReference]("artifact")
  } yield P.PublicationObservation(P.CollectionResult(status, reason, publication, applicability), operation, recollection,
    owner, observed, process, destination, artifact)).flatMap(value =>
      if (value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Publication proposal has undeclared, null or noncanonical fields", cursor.history))))
  object Target {
    given Encoder[Target] = deriveEncoder[Target].mapJson(P.publicJson)
    private val _decoder: Decoder[Target] = {
      given Decoder[P.PublicationObservation] = _observation_decoder
      deriveDecoder[Target]
    }
    given Decoder[Target] = _closed(_decoder, targetValid)
  }
  final case class Direction(target: Target, choice: String, rationale: String, reference: ContextReference)
  object Direction {
    given Encoder[Direction] = deriveEncoder[Direction].mapJson(P.publicJson)
    given Decoder[Direction] = _closed(deriveDecoder, directionValid)
  }
  final class Saved private[GoalPhaseDependencyPublicationSourceAlignment](val record: InstanceRecord, val direction: Direction)
  final class Permit private[GoalPhaseDependencyPublicationSourceAlignment](val saved: Saved) {
    def skips(target: LocalPublication.Target): Boolean = saved.direction.target.targets.exists(_.target == target)
  }
  private def _closed[A: Encoder](decoder: Decoder[A], valid: A => Boolean): Decoder[A] = Decoder.instance(c =>
    decoder(c).flatMap(v => if (valid(v) && v.asJson == c.value) Right(v)
      else Left(DecodingFailure("Dependency publication source alignment is incomplete, crossed or noncanonical", c.history))))
  private def _sorted(value: Json): String = Printer.noSpaces.copy(sortKeys = true).print(value)
  private def _text(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _opaque(value: String): Option[Json] = Option(value).flatMap(io.circe.parser.parse(_).toOption)
    .filter(j => j.isObject && _sorted(j) == value)
  private def _candidate(value: Target): Option[C.Payload] = _opaque(value.candidate).flatMap(_.as[C.Payload].toOption)
  private def _capture(value: Target): Option[R.Capture] = _opaque(value.capture).flatMap(_.as[R.Capture].toOption)
  def canonicalCapture(candidate: C.Payload): Option[R.Capture] = Option(candidate).flatMap(_.dependencySourceCapture)
    .filter(c => R.isCanonicalCaptureSchema(c.schemaVersion) && R.captureValid(c) && R.candidateValid(candidate))

  def rootC(core: ActionCall.Core, workspace: String): Consequence[String] =
    WorkflowManagedProcessExecution.executeGitC(core, GitOperation.Root, workspace).flatMap(_.valueC)

  /** Complete read-only scan precedes every ensure, including a primary-first policy. */
  def mismatchesC(candidate: C.Payload, targets: Vector[LocalPublication.Target],
    root: String => Consequence[String]): Consequence[Vector[TargetRoot]] = canonicalCapture(candidate) match {
    case None if Option(candidate).flatMap(_.dependencySourceCapture).exists(c => R.isCanonicalCaptureSchema(c.schemaVersion)) =>
      F.invalid("Dependency publication preflight refuses an invalid canonical source capture")
    case None => Consequence.success(Vector.empty)
    case Some(capture) =>
      val binding = R.bindingOf(capture)
      for {
        actual <- root(binding.dependency.workspace)
        _ <- if (actual == binding.root) Consequence.unit else F.invalid("Canonical dependency workspace no longer resolves to its captured root")
        roots <- targets.filter(t => t.dependency.contains(binding.identity)).foldLeft(Consequence.success(Vector.empty[TargetRoot])) {
          (prior, target) => for { saved <- prior; selected <- root(target.workspace) }
            yield saved :+ TargetRoot(target, selected)
        }
      } yield roots.filter(_.actualRoot != actual)
  }

  /** Publisher names are independent of consumer names. Its supplied normal
    * process policy must resolve its own exact target to the canonical root. */
  def observationIssues(capture: R.Capture, observation: Option[P.PublicationObservation]): Vector[String] = observation match {
    case None => Vector("Supply publicationObservation from the actual canonical producer collectLocalPublication result, with its owner, process policy, destination and artifact references")
    case Some(value) if !P.observationValid(value) => Vector("publicationObservation requires a complete successful untruncated normal collect and its exact publisher program")
    case Some(value) =>
      val binding = R.bindingOf(capture)
      val request = value.result.publication.request
      val workspaces = _opaque(value.publisherProcessPolicy).flatMap(_.hcursor.downField("processPlan")
        .get[Vector[Json]]("workspaces").toOption).getOrElse(Vector.empty)
        .filter(_.hcursor.get[String]("identity").contains(request.target.workspace))
      Vector(
        Option.unless(workspaces.size == 1 && workspaces.head.hcursor.get[String]("root").contains(binding.root))(
          "The actual publisher workspace must resolve to the selected canonical dependency root"),
        Option.unless(request.target.component == binding.dependency.identity)("The actual publisher Component must equal the selected dependency Component"),
        Option.unless(value.result.applicability match {
          case None => request.source == P.sourceOf(capture.postStageGuard)
          case Some(fact) => LocalPublicationSourceApplicability.matches(fact, value, P.sourceOf(capture.postStageGuard),
            binding.root, binding.branch, binding.dependency.identity)
        })(
          "The actual publisher source must equal the current fresh canonical source guard")
      ).flatten
  }

  def targetValid(value: Target): Boolean = value != null && isTargetSchema(value.schemaVersion) &&
    Vector(value.componentInstance, value.participant, value.sessionId).forall(_text) &&
    _revision(value.frontierRevision) && _revision(value.candidateRecordRevision) &&
    BigInt(value.candidateRecordRevision) <= BigInt(value.frontierRevision) &&
    _opaque(value.processPolicy).nonEmpty && _opaque(value.publicationPolicy).nonEmpty &&
    value.targets != null && value.targets.nonEmpty && value.targets.distinct == value.targets &&
    value.publication != null && value.applicabilityIssues != null &&
    ((value.schemaVersion == applicabilityTargetSchema) == value.publication.exists(_.result.applicability.nonEmpty)) &&
    (for {
      candidate <- _candidate(value)
      capture <- _capture(value)
      actual <- canonicalCapture(candidate)
      controller <- _opaque(value.controller)
    } yield capture == actual && capture.sessionId == value.sessionId &&
      value.targets.forall(t => t != null && t.target != null && t.target.beforeChecks &&
        t.target.dependency.contains(R.bindingOf(capture).identity) && _text(t.actualRoot) && t.actualRoot != R.bindingOf(capture).root) &&
      controller.hcursor.get[String]("stage").contains("Validation") &&
      controller.hcursor.get[GoalPhaseOrderedProgression.Plan]("plan").toOption.exists(plan =>
        ContextReference(plan.identity, plan.revision) == capture.plan) &&
      controller.hcursor.get[GoalPhaseOrderedProgression.Snapshot]("base").contains(capture.primaryBase) &&
      controller.hcursor.get[Long]("generation").contains(capture.generation) &&
      value.applicabilityIssues == observationIssues(capture, value.publication)).contains(true)
  def choices(target: Target): Vector[String] =
    if (targetValid(target) && target.applicabilityIssues.isEmpty) Vector(adoptionChoice, "Defer") else Vector("Defer")
  def directionValid(value: Direction): Boolean = value != null && targetValid(value.target) &&
    value.target.applicabilityIssues.isEmpty && value.choice == adoptionChoice && value.rationale != null &&
    value.rationale.trim.nonEmpty && !value.rationale.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value.rationale) && R.referenceValid(value.reference) &&
    value.reference != ContextReference(value.target.sessionId, (BigInt(value.target.frontierRevision) + 1).toString)
  def targetC(record: Record): Consequence[Target] = W.jsonC(record).flatMap(_.as[Target].left.map(_.message).fold(F.invalid, Consequence.success))
  def observationC(record: Record): Consequence[P.PublicationObservation] = W.jsonC(record).flatMap(
    _.as[P.PublicationObservation](using _observation_decoder).left.map(_.message).fold(F.invalid, Consequence.success))
  def isTargetC(record: Record): Consequence[Boolean] = W.jsonC(record).map(_.hcursor.get[String]("schemaVersion").exists(isTargetSchema))
  def bindingC(component: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (targetValid(target) && component == target.componentInstance && participant == target.participant &&
        choices(target).contains(choice) && rationale != null && rationale.trim.nonEmpty && R.referenceValid(reference) &&
        (choice == "Defer" || directionValid(Direction(target, choice, rationale, reference))))
      Consequence.success(Json.obj("componentInstance" -> component.asJson, "prerequisitePublicationRecovery" -> Json.obj(
        "target" -> target.asJson, "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson))))
    else F.invalid("Alignment answer requires its exact target and an independent Human reference")
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
    _ <- Either.cond(value == selected, (), "Alignment binding contains undeclared fields")
  } yield selected
  def description(target: Target): V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult =
    V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult(target.sessionId, "direction-required",
      message + target.applicabilityIssues.mkString(" ", "; ", ""), target.componentInstance,
      GoalPhaseNativeMetadata.record(target.asJson), choices(target), B.recoveryOperation)

  def targetForC(native: B.Source, d: F.Declaration, core: ActionCall.Core,
    publication: Option[P.PublicationObservation], observe: Boolean = true, retained: Option[Target] = None): Consequence[Target] = for {
    capture <- canonicalCapture(native.candidate.payload).map(Consequence.success)
      .getOrElse(F.invalid("Alignment requires the actual authenticated canonical candidate capture"))
    _ <- if (native.controller.stage == GoalPhaseOrderedProgression.Stage.Validation &&
        native.candidate.record.revision.value <= native.from.revision.value) Consequence.unit
      else F.invalid("Alignment requires unstarted ordinary Validation, never a pending Repair")
    _ <- native.chain.resolveC(capture.direction.target.predecessor)
    policy <- d.localPublication.map(Consequence.success).getOrElse(F.invalid("Alignment publication policy is absent"))
    _ <- LocalPublicationRuntime.validateC(policy)
    _ <- if (observe) GoalPhaseNativeValidation.unstartedPrimaryPublicationC(native.session, d, core, native.candidate,
      native.controller, native.from, native.documentPolicy, native.recordingPaths, native.chain) else Consequence.unit
    process <- if (observe) B.processPolicyC(d, core) else retained.map(t => Consequence.success(t.processPolicy))
      .getOrElse(F.invalid("Alignment history omitted its process policy"))
    roots <- if (observe) mismatchesC(native.candidate.payload, policy.targets.filter(_.beforeChecks), rootC(core, _))
      else retained.map(t => Consequence.success(t.targets)).getOrElse(F.invalid("Alignment history omitted its literal targets"))
    _ <- if (observe) R.freshnessC(core, capture) else Consequence.unit
    _ <- if (observe && publication.exists(_.result.applicability.nonEmpty) && observationIssues(capture, publication).isEmpty)
      LocalPublicationSourceApplicability.currentC(core, publication.get, R.bindingOf(capture), Some(policy)) else Consequence.unit
    target = Target(if (publication.exists(_.result.applicability.nonEmpty)) applicabilityTargetSchema else targetSchema,
      core.component.get.core.instanceId.canonicalKey, native.session.participantIdentity,
      native.from.identity.value, native.from.revision.value.toString, _sorted(B.controller(native.controller)),
      native.candidate.record.revision.value.toString, _sorted(native.candidate.payload.asJson), _sorted(capture.asJson),
      roots, process, B.publicationPolicy(policy), publication, observationIssues(capture, publication))
    _ <- if (targetValid(target) && roots.forall(r => policy.targets.contains(r.target))) Consequence.unit
      else F.invalid("Alignment has no exact obsolete publisher at this native source frontier")
  } yield target

  val codec: WorkflowResultJsonV1.PayloadCodec[Direction] = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "GoalPhaseDependencyPublicationSourceAlignmentDirection"
    def encode(value: Direction): Json = Json.obj("schemaVersion" -> value.target.schemaVersion.asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, Direction] = Option(value).toRight("Alignment Direction is absent").flatMap(
      _.hcursor.get[Direction]("payload").left.map(_.message)).flatMap(direction =>
      Either.cond(encode(direction) == value, direction, "Alignment Direction envelope is noncanonical"))
  }
  private def _reference(from: InstanceRecord) = WorkflowMechanicalTransition.Reference(
    from.identity.value + ":" + causationPrefix + from.revision.value, "1")
  def recoverC(native: B.Source, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhasePrerequisitePublicationRecoveryInput): Consequence[V.GoalPhasePrerequisitePublicationRecoveryResult] = for {
    supplied <- targetC(input.target)
    actual <- targetForC(native, d, core, supplied.publication)
    _ <- if (actual == supplied && input.sessionId == actual.sessionId && input.reference != null) Consequence.unit
      else F.invalid("Alignment answer has a stale source, policy, target or native frontier")
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(actual.componentInstance, actual, input.choice, input.rationale, actual.participant, reference)
    installation <- d.prerequisitePublicationRecovery.map(Consequence.success).getOrElse(F.invalid("Independent Human evidence provider is absent"))
    _ <- installation.validateC
    _ <- installation.evidence.admitC(core, reference, binding)
    _ <- if (input.choice == "Defer") Consequence.unit else for {
      adapter <- native.session.storage.bindMechanicalC(F.advanceOperation, codec)
      _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(native.from), native.from,
        native.from.revision, F.history(native.from, "phase.Validation", causationPrefix + native.from.revision.value),
        TypedValue(codec.typeIdentity, Direction(actual, input.choice, input.rationale, reference))),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield ()
  } yield V.GoalPhasePrerequisitePublicationRecoveryResult(input.sessionId, input.choice != "Defer",
    if (input.choice == "Defer") "deferred" else "dependency-publication-source-aligned", message)

  def retainedC(native: B.Source, d: F.Declaration, core: ActionCall.Core): Consequence[Saved] = for {
    adapter <- native.session.storage.bindMechanicalC(F.advanceOperation, codec)
    saved <- adapter.loadRetainedC(_reference(native.from))(using core.executionContext)
      .flatMap(_.map(v => Consequence.success(new Saved(v.receipt.next, v.receipt.payload.value)))
        .getOrElse(F.invalid("Alignment history omitted its actual independent Direction")))
    target <- targetForC(native, d, core, saved.direction.target.publication, observe = false, retained = Some(saved.direction.target))
    actual <- GoalPhaseNativeHistory.prefixC(native.session.current, saved.record.revision)
    _ <- if (directionValid(saved.direction) && saved.direction.target == target && actual == saved.record &&
        saved.record.identity == native.from.identity && saved.record.definition == native.from.definition &&
        saved.record.revision == native.from.revision.next && saved.record.history.dropRight(1) == native.from.history &&
        saved.record.currentProgression == native.from.currentProgression && saved.record.lifecycle == Lifecycle.Active &&
        saved.record.suspension.isEmpty && saved.record.causation.value == causationPrefix + native.from.revision.value)
      Consequence.unit else F.invalid("Alignment Direction crosses its exact adjacent native predecessor")
  } yield saved

  def applicable(saved: Saved, candidate: C.Saved): Boolean = directionValid(saved.direction) &&
    saved.direction.target.candidate == _sorted(candidate.payload.asJson) &&
    saved.direction.target.candidateRecordRevision == candidate.record.revision.value.toString
  def permit(saved: Saved, candidate: C.Saved): Option[Permit] = Option.when(applicable(saved, candidate))(new Permit(saved))
  def currentC(permit: Permit, candidate: C.Saved, session: String, participant: String,
    d: F.Declaration, core: ActionCall.Core): Consequence[Unit] = for {
    _ <- if (applicable(permit.saved, candidate) && permit.saved.direction.target.sessionId == session &&
        permit.saved.direction.target.participant == participant) Consequence.unit else F.invalid("Alignment permit crosses its native candidate or owner")
    target = permit.saved.direction.target
    policy <- d.localPublication.map(Consequence.success).getOrElse(F.invalid("Alignment lost its publication policy"))
    process <- B.processPolicyC(d, core)
    roots <- mismatchesC(candidate.payload, policy.targets.filter(_.beforeChecks), rootC(core, _))
    _ <- if (process == target.processPolicy && B.publicationPolicy(policy) == target.publicationPolicy && roots == target.targets)
      Consequence.unit else F.invalid("Alignment publisher, root or execution policy changed")
    capture = canonicalCapture(candidate.payload).get
    _ <- R.freshnessC(core, capture)
    _ <- if (observationIssues(capture, target.publication).isEmpty) Consequence.unit
      else F.invalid("Alignment publication no longer qualifies for the canonical source")
    _ <- target.publication.fold(Consequence.unit)(LocalPublicationSourceApplicability.currentC(core, _, R.bindingOf(capture), Some(policy)))
  } yield ()
}
