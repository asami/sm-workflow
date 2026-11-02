package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json, Printer}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.record.Record
import GoalPhaseNativeRun as F
import GoalPhasePrerequisitePrimaryRepair as A
import GoalPhasePrerequisitePublicationRecovery as B
import GoalPhasePrerequisitePublicationUnknownRecovery as W
import GoalPhaseDependencySourceRevision as R
import GoalPhaseDependencyValidation.given
import org.simplemodeling.textus.smworkflow.value as V

/** A fresh Human artifact decision for an authenticated source successor.
  * The original UNKNOWN and its old source acceptance remain immutable.
  * @since   Nov.  2, 2026
  * @version Nov.  2, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseCurrentSourceArtifactAdoption {
  val targetSchema = "sm-workflow.goal-phase-prerequisite-publication-recovery.v4"
  val causationPrefix = "ordered-current-source-artifact-adoption:"
  val choices = Vector("AcceptExistingArtifactsWithUnknownEffects", "Defer")
  val message = "The current captured dependency source needs a fresh independent Human artifact decision. " +
    "AcceptExistingArtifactsWithUnknownEffects accepts existing artifacts for this exact source while the original publication remains UNKNOWN. " +
    "This grants no primary publication permission; a later ordinary Advance owns publication and checks."

  final case class Target(schemaVersion: String, publicationTarget: A.Target,
    previous: W.ExistingArtifactAdoption, guard: R.Guard, source: LocalPublication.Source, processPolicy: String) {
    def componentInstance: String = publicationTarget.componentInstance
    def participant: String = publicationTarget.participant
    def sessionId: String = publicationTarget.sessionId
    def frontierRevision: String = publicationTarget.frontierRevision
  }
  object Target {
    private val _adoption_encoder: Encoder[W.ExistingArtifactAdoption] = Encoder.instance(value => Json.obj(
      "direction" -> value.direction.asJson, "previous" -> value.previous.asJson,
      "source" -> value.source.asJson, "guard" -> value.guard.asJson))
    private val _adoption_decoder: Decoder[W.ExistingArtifactAdoption] = Decoder.instance(cursor => for {
      direction <- cursor.get[ContextReference]("direction")
      previous <- cursor.get[LocalPublication.Result]("previous")
      source <- cursor.get[LocalPublication.Source]("source")
      guard <- cursor.get[R.Guard]("guard")
    } yield W.ExistingArtifactAdoption(direction, previous, source, guard))
    given Encoder[Target] = Encoder.instance(value => _public(Json.obj(
      "schemaVersion" -> value.schemaVersion.asJson,
      "publicationTarget" -> value.publicationTarget.asJson,
      "previous" -> _adoption_encoder(value.previous),
      "guard" -> value.guard.asJson,
      "source" -> value.source.asJson,
      "processPolicy" -> value.processPolicy.asJson)))
    private val _decoder: Decoder[Target] = Decoder.instance(cursor => for {
      schema <- cursor.get[String]("schemaVersion")
      publication <- cursor.get[A.Target]("publicationTarget")
      previous <- cursor.get[W.ExistingArtifactAdoption]("previous")(using _adoption_decoder)
      guard <- cursor.get[R.Guard]("guard")
      source <- cursor.get[LocalPublication.Source]("source")
      process <- cursor.get[String]("processPolicy")
    } yield Target(schema, publication, previous, guard, source, process))
    given Decoder[Target] = _closed(_decoder, targetValid)
  }
  final case class Direction(target: Target, choice: String, rationale: String, reference: ContextReference)
  object Direction {
    given Encoder[Direction] = deriveEncoder[Direction].mapJson(_public)
    private val _decoder: Decoder[Direction] = deriveDecoder
    given Decoder[Direction] = _closed(_decoder, directionValid)
  }
  final case class Saved(record: InstanceRecord, direction: Direction) {
    def reference: ContextReference = ContextReference(record.identity.value, record.revision.value.toString)
    def adoption: W.ExistingArtifactAdoption = W.ExistingArtifactAdoption(reference,
      direction.target.previous.previous, direction.target.source, direction.target.guard)
  }
  private def _public(value: Json): Json = value.arrayOrObject(value,
    xs => Json.fromValues(xs.map(_public)), fields => Json.fromFields(fields.toVector.collect {
      case (key, item) if !item.isNull => key -> _public(item)
    }))
  private def _closed[T: Encoder](decoder: Decoder[T], valid: T => Boolean): Decoder[T] =
    Decoder.instance(cursor => decoder(cursor).flatMap(value =>
      if (valid(value) && value.asJson == cursor.value) Right(value)
      else Left(DecodingFailure("Current source artifact decision is incomplete, crossed or noncanonical", cursor.history))))
  private def _rationale(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value)
  private def _policy(value: String): Boolean = Option(value).flatMap(io.circe.parser.parse(_).toOption).exists(j =>
    j.asObject.exists(_.nonEmpty) && Printer.noSpaces.copy(sortKeys = true).print(j) == value)
  def sourceOf(guard: R.Guard): LocalPublication.Source = LocalPublication.Source(guard.head, guard.indexTree,
    guard.dirty.map(d => LocalPublication.Change(d.path, d.index, d.worktree, d.renamedFrom)))

  private def _previous_valid(value: W.ExistingArtifactAdoption, publication: A.Target): Boolean =
    value != null && R.referenceValid(value.direction) && R.guardValid(value.guard) && value.source == sourceOf(value.guard) &&
      value.previous != null && value.previous.status == "uncertain" && value.previous.nextPreparation.isEmpty &&
      value.previous.request != null && value.previous.request.input != null && {
        val request = value.previous.request
        val target = request.target
        val input = request.input
        target != null && target.beforeChecks && target.dependency.nonEmpty && publication.failure.targets.contains(target) &&
          publication.failure.candidate.dependencySourceCapture.exists(c => R.captureValid(c) &&
            c.postStageGuard == value.guard && R.bindingOf(c).dependency.workspace == target.workspace &&
            target.dependency.contains(R.bindingOf(c).identity)) &&
          request.participant == publication.participant && input.ownerSessionId.contains(publication.sessionId) &&
          input.targetIdentity == target.identity && !input.explicit && input.answer.isEmpty &&
          input.requestIdentity != null && input.requestIdentity.nonEmpty
      }

  def targetValid(value: Target): Boolean = value != null && value.schemaVersion == targetSchema &&
    A.targetValid(value.publicationTarget) && value.publicationTarget.repair.nonEmpty && {
      val publication = value.publicationTarget
      val candidate = publication.candidate
      val failure = publication.failure
      failure.artifactAdoption.contains(value.previous) && _previous_valid(value.previous, publication) &&
        GoalPhaseNativeCandidate.sourceCaptureValid(candidate) && candidate.primaryPublicationRepair.contains(
          A.CaptureReuse(publication.repair.get, failure.candidateRecordRevision,
            failure.candidate.primaryPublicationRepair.map(_.sourceRevision).getOrElse(failure.candidate.sourceRevision),
            failure.generation)) &&
        A.currentSourceSuccessorValid(failure.candidate, candidate) && candidate.dependencySourceCapture.exists { capture =>
          val binding = R.bindingOf(capture)
          R.captureValid(capture) && R.candidateValid(candidate) && capture.sessionId == publication.sessionId &&
            capture.plan == ContextReference(publication.scope.plan.identity, publication.scope.plan.revision) &&
            capture.primaryBase == publication.scope.base && capture.step == publication.scope.step.identity &&
            capture.generation == publication.generation && capture.sourceRevision == candidate.sourceRevision &&
            value.previous.previous.request.target.dependency.contains(binding.identity) &&
            value.previous.previous.request.target.workspace == binding.dependency.workspace &&
            value.guard == capture.postStageGuard && R.guardValid(value.guard) && value.source == sourceOf(value.guard) &&
            (value.source != value.previous.source || value.guard != value.previous.guard)
        } && _policy(value.processPolicy) && A.processPolicyMatches(failure.candidate, candidate,
          failure.processPolicy, value.processPolicy)
    }
  def directionValid(value: Direction): Boolean = value != null && targetValid(value.target) &&
    value.choice == choices.head && _rationale(value.rationale) && R.referenceValid(value.reference) &&
    value.reference != ContextReference(value.target.sessionId, (BigInt(value.target.frontierRevision) + 1).toString)

  /** Applicability is exact candidate/capture/repair evidence. Frontier history
    * may subsequently gain an independent publication permission. */
  def applicable(value: Direction, repair: A.Saved, candidate: GoalPhaseNativeCandidate.Saved): Boolean =
    directionValid(value) && value.target.publicationTarget.failure == repair.fact.failure &&
      value.target.publicationTarget.repair.contains(repair.reference) &&
      value.target.publicationTarget.candidate == candidate.payload &&
      value.target.publicationTarget.candidateRecordRevision == candidate.record.revision.value.toString

  def needed(repair: A.Saved, candidate: GoalPhaseNativeCandidate.Saved, directions: Vector[Saved]): Boolean = {
    val failure = repair.fact.failure
    A.currentSourceSuccessorValid(failure.candidate, candidate.payload) &&
      candidate.payload.primaryPublicationRepair.contains(repair.reuse) &&
      candidate.payload.dependencySourceCapture.exists(c => R.captureValid(c) &&
        failure.artifactAdoption.exists(old => old.guard != c.postStageGuard || old.source != sourceOf(c.postStageGuard))) &&
      !directions.exists(d => applicable(d.direction, repair, candidate))
  }
  def targetC(value: Record): Consequence[Target] = W.jsonC(value).flatMap(_.as[Target].left.map(_.message).fold(F.invalid, Consequence.success))
  def isTargetC(value: Record): Consequence[Boolean] = W.jsonC(value).map(_.hcursor.get[String]("schemaVersion").contains(targetSchema))
  def bindingC(component: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (targetValid(target) && component == target.componentInstance && participant == target.participant &&
        choices.contains(choice) && _rationale(rationale) && R.referenceValid(reference)) Consequence.success(Json.obj(
      "componentInstance" -> component.asJson, "prerequisitePublicationRecovery" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> reference.asJson))))
    else F.invalid("Current source artifact answer requires its exact target, participant and independent reference")
  def selectBinding(value: Json): Either[String, Json] = for {
    supplied <- Option(value).toRight("Current artifact binding is absent")
    component <- supplied.hcursor.get[String]("componentInstance").left.map(_.message)
    body = supplied.hcursor.downField("prerequisitePublicationRecovery")
    target <- body.get[Target]("target").left.map(_.message)
    answer = body.downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    selected <- bindingC(component, target, choice, rationale, participant, reference)
      .map[Either[String, Json]](Right(_)).fold(e => Left(e.toString), identity)
    _ <- Either.cond(selected == supplied, (), "Current artifact binding contains undeclared or crossed fields")
  } yield selected

  def description(target: Target): V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult =
    V.GoalPhasePrerequisitePublicationRecoveryDescriptionResult(target.sessionId, "direction-required", message,
      target.componentInstance, GoalPhaseNativeMetadata.record(target.asJson), choices, B.recoveryOperation)

  def targetC(native: B.Source, repair: A.Saved, d: F.Declaration, core: ActionCall.Core,
    observe: Boolean = true, retained: Option[Target] = None): Consequence[Target] = for {
    publication <- A.publicationTargetC(native, repair.fact.failure, Some(repair), d, core, observe)
    previous <- repair.fact.failure.artifactAdoption.map(Consequence.success)
      .getOrElse(F.invalid("Current artifact decision has no immutable original adoption"))
    capture <- native.candidate.payload.dependencySourceCapture.filter(R.captureValid).map(Consequence.success)
      .getOrElse(F.invalid("Current artifact decision requires its complete current capture"))
    process <- if (observe) B.processPolicyC(d, core) else retained.map(t => Consequence.success(t.processPolicy))
      .getOrElse(F.invalid("Historical current artifact direction omitted its retained process policy"))
    guard <- if (observe) R.observeC(core, R.bindingOf(capture), capture.postStageGuard.excludedFiles.map(_.path))
      else Consequence.success(capture.postStageGuard)
    source <- if (observe) LocalPublicationRuntime._port(core, d.localPublication.get).sourceC(previous.previous.request.target)
      else Consequence.success(sourceOf(guard))
    target = Target(targetSchema, publication, previous, guard, source, process)
    _ <- if (targetValid(target) && native.session.participantIdentity == target.participant &&
        native.from.identity.value == target.sessionId &&
        core.component.get.core.instanceId.canonicalKey == target.componentInstance) Consequence.unit
      else F.invalid("Current artifact decision crosses its native candidate, current source or original UNKNOWN")
    _ <- if (observe) currentC(target, core, d) else Consequence.unit
  } yield target

  /** The existing LocalPublication gate is read-only and requires both original
    * request collection and latest-target collection to equal the old UNKNOWN. */
  def currentC(target: Target, core: ActionCall.Core, d: F.Declaration): Consequence[Unit] = for {
    _ <- if (targetValid(target)) Consequence.unit else F.invalid("Current artifact target is invalid")
    process <- B.processPolicyC(d, core)
    _ <- if (process == target.processPolicy && d.localPublication.exists(policy =>
        B.publicationPolicy(policy) == target.publicationTarget.failure.publicationPolicy)) Consequence.unit
      else F.invalid("Current artifact acceptance no longer covers the exact actual execution policy")
    capture = target.publicationTarget.candidate.dependencySourceCapture.get
    _ <- R.freshnessC(core, capture)
    _ <- LocalPublicationRuntime.adoptExistingArtifactsC(W.ExistingArtifactAdoption(target.previous.direction,
      target.previous.previous, target.source, target.guard), target.previous.previous.request.target,
      target.sessionId, target.participant,
      R.observeC(core, R.bindingOf(capture), capture.postStageGuard.excludedFiles.map(_.path)),
      LocalPublicationRuntime._port(core, d.localPublication.get))
  } yield ()

  val codec: WorkflowResultJsonV1.PayloadCodec[Direction] = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "GoalPhaseCurrentSourceArtifactAdoptionDirection"
    def encode(value: Direction): Json = Json.obj("schemaVersion" -> targetSchema.asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, Direction] = Option(value).toRight("Current artifact direction is absent").flatMap(
      _.hcursor.get[Direction]("payload").left.map(_.message)).flatMap(direction =>
      Either.cond(encode(direction) == value, direction, "Current artifact Direction envelope is noncanonical"))
  }
  private def _reference(from: InstanceRecord) = WorkflowMechanicalTransition.Reference(
    from.identity.value + ":" + causationPrefix + from.revision.value, "1")
  def recoverC(native: B.Source, repair: A.Saved, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhasePrerequisitePublicationRecoveryInput): Consequence[V.GoalPhasePrerequisitePublicationRecoveryResult] = for {
    actual <- targetC(native, repair, d, core)
    supplied <- targetC(input.target)
    _ <- if (supplied == actual && actual.sessionId == input.sessionId && input.reference != null) Consequence.unit
      else F.invalid("Current artifact answer has a stale source, candidate or native frontier")
    reference = ContextReference(input.reference.identity, input.reference.revision)
    _ <- if (input.choice == "Defer" || directionValid(Direction(actual, input.choice, input.rationale, reference))) Consequence.unit
      else F.invalid("Current artifact answer cannot use its own future native record as independent Human evidence")
    binding <- bindingC(actual.componentInstance, actual, input.choice, input.rationale, actual.participant, reference)
    installation <- d.prerequisitePublicationRecovery.map(Consequence.success)
      .getOrElse(F.invalid("Independent Human evidence provider is absent"))
    _ <- installation.validateC
    _ <- installation.evidence.admitC(core, reference, binding)
    _ <- if (input.choice == "Defer") Consequence.unit else for {
      _ <- currentC(actual, core, d)
      adapter <- native.session.storage.bindMechanicalC(F.advanceOperation, codec)
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(native.from),
        native.from, native.from.revision, F.history(native.from, "phase.Validation", causationPrefix + native.from.revision.value),
        TypedValue(codec.typeIdentity, Direction(actual, input.choice, input.rationale, reference))),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield ()
  } yield V.GoalPhasePrerequisitePublicationRecoveryResult(input.sessionId, input.choice != "Defer",
    if (input.choice == "Defer") "deferred" else "current-source-artifacts-adopted", message)

  def retainedC(native: B.Source, repair: A.Saved, d: F.Declaration, core: ActionCall.Core): Consequence[Saved] = for {
    adapter <- native.session.storage.bindMechanicalC(F.advanceOperation, codec)
    saved <- adapter.loadRetainedC(_reference(native.from))(using core.executionContext)
      .flatMap(_.map(v => Consequence.success(Saved(v.receipt.next, v.receipt.payload.value)))
        .getOrElse(F.invalid("Current artifact history omitted its actual independent Direction")))
    target <- targetC(native, repair, d, core, observe = false, retained = Some(saved.direction.target))
    actual <- GoalPhaseNativeHistory.prefixC(native.session.current, saved.record.revision)
    _ <- if (directionValid(saved.direction) && saved.direction.target == target && actual == saved.record &&
        saved.record.identity == native.from.identity && saved.record.definition == native.from.definition &&
        saved.record.revision == native.from.revision.next && saved.record.history.dropRight(1) == native.from.history &&
        saved.record.currentProgression == native.from.currentProgression && saved.record.suspension.isEmpty &&
        saved.record.causation.value == causationPrefix + native.from.revision.value && saved.reference != saved.direction.reference)
      Consequence.unit else F.invalid("Current artifact Direction crosses its exact adjacent native predecessor")
  } yield saved
}
