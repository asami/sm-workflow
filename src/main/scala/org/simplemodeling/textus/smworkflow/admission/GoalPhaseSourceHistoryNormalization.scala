package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.time.LocalDate
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceId}
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseOrderedHumanDecision as H
import GoalPhaseOrderedProgression as O
import GoalPhaseSourceHistoryRecovery as R

/** Append-only adoption of existing comment authority. Neither description nor
  * adoption answers the old Human question or performs a File effect.
  * @since   Oct. 26, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseSourceHistoryNormalization {
  val policy = "sm-workflow.source-history-normalization.v1"
  val purpose = "goal-phase-source-history-normalization"
  val describeOperation = "describeGoalPhaseSourceHistoryNormalization"
  val adoptionOperation = "adoptGoalPhaseSourceHistoryNormalization"
  val choices = Vector("AdoptSameAuthorityNormalization", "Defer")
  val message = "Adopt the existing same-authority source-history normalization policy using its actual policy-direction " +
    "artifact. Independent host evidence must attest that the old invocation ended. The old UNKNOWN and successful " +
    "prefix remain unchanged; an unanswered source-history question is superseded-by-normalization, never answered. " +
    "Adoption performs no File/Git/SBT or Advance. Separate ordinary Advance observes the current postcondition or " +
    "normalizes the one affected comment under its retained rule/date. Defer writes nothing."

  final case class Question(identity: String, pendingRevision: String)
  final case class ResumeFrom(directionIdentity: String, directionRevision: String,
    intentIdentity: String, intentRevision: String, resultIdentity: Option[String] = None,
    resultRevision: Option[String] = None, outcome: Option[String] = None)
  object ResumeFrom {
    given Encoder[ResumeFrom] = deriveEncoder[ResumeFrom].mapJson(_.mapObject(fields =>
      Vector("resultIdentity", "resultRevision", "outcome").foldLeft(fields)((selected, key) =>
        if (selected(key).exists(_.isNull)) selected.remove(key) else selected)))
    given Decoder[ResumeFrom] = deriveDecoder
  }
  final case class Target(recovery: R.RecoveryTarget, frontierRevision: String, frontierCause: String,
    policy: String, pendingQuestion: Option[Question] = None, resumeFrom: Option[ResumeFrom] = None)
  object Target {
    given Encoder[Target] = deriveEncoder[Target].mapJson(_.mapObject { fields =>
      val recovery = fields("recovery").fold(fields)(value => fields.add("recovery", _canonical_recovery(value)))
      Vector("pendingQuestion", "resumeFrom").foldLeft(recovery)((selected, key) =>
        if (selected(key).exists(_.isNull)) selected.remove(key) else selected)
    })
    private def _omit_absent(value: Json, names: Set[String]): Json = value.mapObject(_.filter {
      case (name, field) => !names.contains(name) || !field.isNull
    })
    private def _canonical_recovery(value: Json): Json = value.mapObject { fields =>
      val intent = fields("intent").fold(fields)(value =>
        fields.add("intent", _omit_absent(value, Set("outcome"))))
      val inspection = fields("inspection").fold(intent)(value =>
        intent.add("inspection", _omit_absent(value, Set("candidate", "diff"))))
      fields("completedPrefix").fold(inspection)(value => inspection.add("completedPrefix", value.mapArray(_.map(
        _.mapObject(entry => entry("intent").fold(entry)(value =>
          entry.add("intent", _omit_absent(value, Set("outcome")))))))))
    }
    private val raw: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(cursor => raw(cursor).flatMap(value =>
      if (validTarget(value) && value.asJson == cursor.value) Right(value)
      else Left(io.circe.DecodingFailure("Normalization target requires its exact complete canonical contract", cursor.history))))
  }
  final case class Source(session: F.Session, original: InstanceRecord, controller: O.State,
    recovery: R.RecoveryTarget, human: Option[H.Source] = None, resume: Option[Saved] = None)
  final case class Direction(target: Target, choice: String, rationale: String, participant: String,
    reference: ContextReference, priorExecutionEnded: Boolean)
  final case class Saved(record: InstanceRecord, direction: Direction)
  final case class Observation(target: Target, directionIdentity: String, directionRevision: String,
    originalIntentIdentity: String, originalIntentRevision: String, outcome: Option[String], diagnostic: Option[String])
  final case class Observed(record: InstanceRecord, observation: Observation) {
    def succeeded: Boolean = observation.outcome.exists(Set("observed-current-postcondition", "updated-current"))
  }
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else F.invalid("Source-history normalization requires independent host evidence")
  }
  private def exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536
  def validTarget(value: Target): Boolean = value != null && R.validTarget(value.recovery) &&
    value.policy == policy && exact(value.frontierCause) && value.pendingQuestion != null && value.resumeFrom != null &&
    _revision(value.frontierRevision) && BigInt(value.frontierRevision) < BigInt(Long.MaxValue) - 4 &&
    (value.resumeFrom match {
      case None => value.frontierRevision == (BigInt(value.recovery.frontierRevision) + value.pendingQuestion.size).toString &&
        value.pendingQuestion.forall(question => question != null &&
          question.identity == value.recovery.sessionId + ":ordered-human:" + value.recovery.frontierRevision &&
          question.pendingRevision == value.frontierRevision)
      case Some(prior) => _valid_resume(value, prior)
    })
  private def _revision(value: String): Boolean = value != null && value.matches("[1-9][0-9]*") &&
    BigInt(value) <= BigInt(Long.MaxValue)
  private def _base_identity(target: Target): String = target.recovery.sessionId +
    ":ordered-source-history-normalization:" + target.recovery.sourceRevision
  private def _valid_resume(target: Target, prior: ResumeFrom): Boolean = {
    val base = _base_identity(target)
    val offset = if (prior != null && prior.outcome != null && prior.outcome.contains("failed")) 3 else 2
    val predecessor = Option(prior).map(_.directionIdentity).filter(_ != null).flatMap { identity =>
      if (identity == base) Some(Vector(BigInt(target.recovery.frontierRevision), BigInt(target.recovery.frontierRevision) + 1))
      else if (identity.startsWith(base + ":resume:") && _revision(identity.stripPrefix(base + ":resume:")))
        Some(Vector(BigInt(identity.stripPrefix(base + ":resume:"))))
      else None
    }
    prior != null && target.pendingQuestion.isEmpty && prior.directionRevision == "1" &&
      prior.intentIdentity == prior.directionIdentity + ":reconciliation-intent" && prior.intentRevision == "1" &&
      prior.resultIdentity != null && prior.resultRevision != null && prior.outcome != null &&
      (if (prior.outcome.contains("failed")) prior.resultIdentity.contains(prior.directionIdentity + ":reconciliation-result") &&
        prior.resultRevision.contains("1") && target.frontierCause == observationCause("result")
      else prior.outcome.isEmpty && prior.resultIdentity.isEmpty && prior.resultRevision.isEmpty &&
        target.frontierCause == observationCause("intent")) &&
      predecessor.exists(_.exists(revision => revision + offset == BigInt(target.frontierRevision)))
  }
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def decodeTarget(value: Json): Either[String, Target] = Option(value).toRight("Normalization target is absent")
    .flatMap(_.as[Target].left.map(_.message))
  def decodePublicTarget(value: Json): Either[String, Target] =
    GoalPhaseNativeMetadata.normalizePublicJson(value).flatMap(decodeTarget)
  def targetC(value: Record): Consequence[Target] = if (value == null) F.invalid("Normalization target is absent")
    else value.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(decodePublicTarget).fold(F.invalid, Consequence.success))

  def currentTargetC(source: Source, core: ActionCall.Core): Consequence[Target] = for {
    _ <- if (source != null && R.validTarget(source.recovery) && source.human != null &&
        source.resume != null && source.controller.stage == O.Stage.InspectCandidate && source.session.current.lifecycle == Lifecycle.Active &&
        source.original.revision.value.toString == source.recovery.sourceRevision) Consequence.unit
      else F.invalid("Normalization requires its original selected-Step source")
    current = source.session.current
    resumed <- source.resume match {
      case Some(saved) => for {
        observed <- retainedObservationC(source.session, core, saved)
          .flatMap(_.map(Consequence.success).getOrElse(F.invalid("Normalization resume has no actual preceding reconciliation")))
        _ <- if (observed.record == current && !observed.succeeded && source.human.isEmpty &&
            source.recovery == saved.direction.target.recovery && current.suspension.isEmpty) Consequence.unit
          else F.invalid("Normalization resume requires the latest actual failed or unresolved reconciliation frontier")
        target = _resume_target(saved, observed)
      } yield Some(target)
      case None => Consequence.success(Option.empty[Target])
    }
    _ <- if (resumed.nonEmpty) Consequence.unit else source.human match {
      case None => if (current.revision.value.toString == source.recovery.frontierRevision && current.suspension.isEmpty)
        Consequence.unit else F.invalid("Normalization requires the exact unresolved current intent")
      case Some(human) => if (human.sourceHistoryRecovery.contains(source.recovery) && human.controller == source.controller &&
          human.from.revision.value.toString == source.recovery.frontierRevision && human.from.identity == current.identity &&
          current.revision == human.from.revision.next && current.suspension.exists(boundary =>
            boundary.continuationIdentity.value == H.identity(human).value &&
              boundary.completion.value == "goal-phase-ordered-human-completion-v1")) Consequence.unit
        else F.invalid("Normalization requires the exact unanswered source-history Human; completed, deferred or cancelled questions refuse")
    }
    target = resumed.getOrElse(Target(source.recovery, current.revision.value.toString, current.causation.value, policy,
      source.human.map(human => Question(H.identity(human).value, current.revision.value.toString))))
    _ <- if (validTarget(target)) Consequence.unit else F.invalid("Normalization target is incomplete")
  } yield target

  def bindingC(component: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference, priorExecutionEnded: Boolean): Consequence[Json] =
    if (!exact(component) || !validTarget(target) || !choices.contains(choice) || !text(rationale) ||
        participant != target.recovery.participant || reference == null || !exact(reference.identity) || !exact(reference.revision) ||
        (choice != "Defer" && !priorExecutionEnded)) F.invalid("Normalization requires exact policy direction and independently attested prior execution ended")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson, "sourceHistoryNormalization" -> Json.obj(
      "target" -> target.asJson, "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
        "participant" -> participant.asJson, "reference" -> reference.asJson, "priorExecutionEnded" -> priorExecutionEnded.asJson))))
  def selectBinding(value: Json): Either[String, Json] = for {
    wire <- Option(value).toRight("Normalization binding is absent")
    component <- wire.hcursor.get[String]("componentInstance").left.map(_.message)
    targetwire <- wire.hcursor.downField("sourceHistoryNormalization").get[Json]("target").left.map(_.message)
    target <- decodeTarget(targetwire)
    answer = wire.hcursor.downField("sourceHistoryNormalization").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    ended <- answer.get[Boolean]("priorExecutionEnded").left.map(_.message)
    selected <- bindingC(component, target, choice, rationale, participant, reference, ended)
      .map[Either[String, Json]](Right(_)).fold(error => Left(error.toString), identity)
    _ <- Either.cond(selected == wire, (), "Normalization binding fields differ")
  } yield selected

  val codec: WorkflowResultJsonV1.PayloadCodec[Direction] = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "GoalPhaseSourceHistoryNormalizationDirection"
    def encode(value: Direction): Json = Json.obj("schemaVersion" -> (policy + ".direction").asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, Direction] = for {
      wire <- Option(value).toRight("Normalization direction is absent")
      direction <- wire.hcursor.get[Direction]("payload").left.map(_.message)
      _ <- Either.cond(encode(direction) == wire && direction.choice == choices.head &&
        bindingC("retained", direction.target, direction.choice, direction.rationale, direction.participant,
          direction.reference, direction.priorExecutionEnded).isSuccess, (), "Normalization direction is invalid")
    } yield direction
  }
  val observationCodec: WorkflowResultJsonV1.PayloadCodec[Observation] = new WorkflowResultJsonV1.PayloadCodec[Observation] {
    val typeIdentity = "GoalPhaseSourceHistoryNormalizationObservation"
    def encode(value: Observation): Json = Json.obj("schemaVersion" -> (policy + ".observation").asJson, "payload" -> value.asJson)
    def decode(value: Json): Either[String, Observation] = for {
      wire <- Option(value).toRight("Normalization observation is absent")
      observation <- wire.hcursor.get[Observation]("payload").left.map(_.message)
      _ <- Either.cond(encode(observation) == wire && validTarget(observation.target) &&
        observation.directionIdentity == reference(observation.target).identity && observation.directionRevision == "1" &&
        observation.originalIntentIdentity == observation.target.recovery.intentIdentity &&
        observation.originalIntentRevision == observation.target.recovery.intentRevision && observation.outcome != null &&
        observation.diagnostic != null && observation.outcome.forall(Set("observed-current-postcondition", "updated-current", "failed")) &&
        (if (observation.outcome.contains("failed")) observation.diagnostic.exists(text) else observation.diagnostic.isEmpty), (),
        "Normalization observation is invalid or crossed")
    } yield observation
  }
  def reference(target: Target): WorkflowMechanicalTransition.Reference = WorkflowMechanicalTransition.Reference(
    _base_identity(target) + target.resumeFrom.fold("")(_ => ":resume:" + target.frontierRevision), "1")
  def cause(target: Target): String = "ordered-source-history-normalization:" + target.recovery.sourceRevision +
    (if (target.resumeFrom.nonEmpty) ":resume:" + target.frontierRevision
      else if (target.pendingQuestion.nonEmpty) ":superseded-by-normalization" else ":adopted")

  def describeC(source: Source, core: ActionCall.Core): Consequence[V.GoalPhaseSourceHistoryNormalizationDescriptionResult] = for {
    target <- currentTargetC(source, core)
    _ <- requireQuestionC(source.session, core, target, source.human)
  } yield V.GoalPhaseSourceHistoryNormalizationDescriptionResult(target.recovery.sessionId, "adoption-required",
    message + (if (target.resumeFrom.nonEmpty) " This target binds the latest actual normalization invocation; refresh its independent terminal lifecycle evidence under the same retained policy direction."
      else if (target.pendingQuestion.nonEmpty) " The genuinely saved Human target supplies the full captured rule."
      else " The unsuspended legacy intent retained provider/date only; the complete rule is bound at adoption, and its old full-rule provenance is unavailable."),
    core.component.get.core.instanceId.canonicalKey, targetRecord(target), choices, adoptionOperation)
  def adoptC(source: Source, d: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhaseSourceHistoryNormalizationAdoptionInput): Consequence[V.GoalPhaseSourceHistoryNormalizationAdoptionResult] = for {
    supplied <- targetC(input.target)
    actual <- currentTargetC(source, core)
    _ <- requireQuestionC(source.session, core, actual, source.human)
    _ <- if (input.sessionId == actual.recovery.sessionId && supplied == actual) Consequence.unit
      else F.invalid("Normalization adoption target is stale or crossed")
    installation <- d.sourceHistoryNormalization.map(Consequence.success).getOrElse(
      Consequence.securityPermissionDenied("Normalization has no installed independent host evidence provider"))
    artifact = ContextReference(input.reference.identity, input.reference.revision)
    _ <- if (source.resume.forall(_.direction.reference == artifact)) Consequence.unit
      else F.invalid("Normalization resume must retain the same already-held policy direction authority")
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, actual, input.choice, input.rationale,
      source.session.participantIdentity, artifact, input.priorExecutionEnded)
    _ <- installation.evidence.admitC(core, artifact, binding)
    _ <- if (input.choice == "Defer") Consequence.unit else for {
      _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits("InspectCandidate", "source-history-normalization-adopted", "InspectCandidate"))
        Consequence.unit else F.invalid("Normalization adoption is absent from CML")
      direction = Direction(actual, input.choice, input.rationale, source.session.participantIdentity, artifact, input.priorExecutionEnded)
      _ <- source.human match {
        case Some(human) => for {
          adapter <- source.session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec, codec)
          _ <- adapter.executeC(H.identity(human), source.session.current.revision, source.session.current,
            F.history(source.session.current, "phase.InspectCandidate", cause(actual)), TypedValue(codec.typeIdentity, direction))(using core.executionContext)
        } yield ()
        case None => for {
          adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
          _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(actual), source.session.current,
            source.session.current.revision, F.history(source.session.current, "phase.InspectCandidate", cause(actual)),
            TypedValue(codec.typeIdentity, direction)), pure)(using core.executionContext)
        } yield ()
      }
    } yield ()
  } yield V.GoalPhaseSourceHistoryNormalizationAdoptionResult(input.sessionId, true,
    if (input.choice == "Defer") "deferred" else "adopted",
    if (input.choice == "Defer") "No normalization direction or effect was written."
    else "Same-authority direction retained; the old question remains unanswered. Call separate ordinary advanceGoalPhase.")

  /** Replay reconstructs the original exact target and validates every native edge. */
  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core, original: InstanceRecord,
    controller: O.State, documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String],
    completedCandidates: Vector[GoalPhaseNativeCandidate.Payload], human: Option[H.Source] = None): Consequence[Option[Saved]] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    mechanical <- adapter.loadRetainedC(WorkflowMechanicalTransition.Reference(original.identity.value +
      ":ordered-source-history-normalization:" + original.revision.value, "1"))(using core.executionContext)
    result <- human match {
      case Some(question) => for {
        pendingentry <- GoalPhaseNativeHistory.nextC(session.current, question.from).flatMap(_.map(Consequence.success)
          .getOrElse(F.invalid("Normalization has no actual Human pending record")))
        pending <- GoalPhaseNativeHistory.prefixC(session.current, pendingentry.revision)
        supersession <- session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec, codec)
        retained <- supersession.loadRetainedC(H.identity(question), pending.identity)(using core.executionContext)
        selected <- retained match {
          case None => Consequence.success(Option.empty[Saved])
          case Some(value) => for {
            _ <- if (value.receipt.sourceRevision == pending.revision && value.receipt.nextRevision == pending.revision.next)
              Consequence.unit else F.invalid("Normalization supersession crosses the actual pending revision")
            next <- GoalPhaseNativeHistory.prefixC(session.current, value.receipt.nextRevision)
            saved <- _retained_c(session, d, core, original, controller, documentPolicy, recordingPaths,
              completedCandidates, pending, next, value.receipt.payload, human)
          } yield Some(saved)
        }
      } yield selected
      case None => mechanical match {
        case None => Consequence.success(Option.empty[Saved])
        case Some(value) => for {
          previous <- GoalPhaseNativeHistory.prefixC(session.current, InstanceRevision(value.receipt.payload.value.target.frontierRevision.toLong))
          saved <- _retained_c(session, d, core, original, controller, documentPolicy, recordingPaths,
            completedCandidates, previous, value.receipt.next, value.receipt.payload.value, None)
        } yield Some(saved)
      }
    }
  } yield result
  private def _retained_c(session: F.Session, d: F.Declaration, core: ActionCall.Core, original: InstanceRecord,
    controller: O.State, documentPolicy: Option[GoalPhaseDocumentProgress.Policy], recordingPaths: Vector[String],
    completedCandidates: Vector[GoalPhaseNativeCandidate.Payload], previous: InstanceRecord, next: InstanceRecord,
    payload: Direction, human: Option[H.Source]): Consequence[Saved] = for {
    direction <- codec.decode(codec.encode(payload)).fold(F.invalid, Consequence.success)
    recovery <- R._target_c(session, d, core, original, controller, documentPolicy, recordingPaths, completedCandidates)
    captured = direction.target
    _ <- requireQuestionC(session, core, captured, human)
    _ <- if (captured.recovery == recovery && captured.frontierRevision == previous.revision.value.toString &&
        captured.frontierCause == previous.causation.value && direction.participant == session.participantIdentity &&
        captured.pendingQuestion.map(_.identity) == human.map(H.identity(_).value) &&
        human.forall(h => h.sourceHistoryRecovery.contains(recovery))) Consequence.unit
      else F.invalid("Retained normalization direction crosses its exact original source, question, rule or authority")
    actual <- GoalPhaseNativeHistory.prefixC(session.current, next.revision)
    _ <- if (actual == next && next.revision == previous.revision.next && next.history.dropRight(1) == previous.history &&
        next.identity == previous.identity && next.definition == previous.definition && next.lifecycle == Lifecycle.Active &&
        next.correlation == previous.correlation && next.derivedCompositeOccurrence == previous.derivedCompositeOccurrence &&
        next.committedPredecessor == previous.committedPredecessor &&
        next.currentProgression == previous.currentProgression && next.suspension.isEmpty && next.causation.value == cause(captured))
      Consequence.unit else F.invalid("Normalization direction is not its exact adjacent native successor")
  } yield Saved(actual, direction)

  /** The old question's genuinely saved full policy target is provenance; the
    * provider/date fields of its original intent alone never prove an old full rule. */
  private def requireQuestionC(session: F.Session, core: ActionCall.Core, target: Target,
    human: Option[H.Source]): Consequence[Unit] = human match {
    case None => if (target.pendingQuestion.isEmpty) Consequence.unit else F.invalid("Normalization lost its actual pending Human source")
    case Some(source) => for {
      adapter <- session.storage.bindDecisionC(H.completionOperation, H.inputCodec)
      question <- adapter.loadC(H.identity(source))(using core.executionContext).flatMap(_.map(Consequence.success)
        .getOrElse(F.invalid("Normalization has no genuinely saved source-history question")))
      input <- NativeWorkflowOperationOutput.decisionInputC(question)
      pendingentry <- GoalPhaseNativeHistory.nextC(session.current, source.from).flatMap(_.map(Consequence.success)
        .getOrElse(F.invalid("Normalization has no contiguous pending question revision")))
      pending <- GoalPhaseNativeHistory.prefixC(session.current, pendingentry.revision)
      _ <- if (input.sourceHistoryRecovery.contains(target.recovery) && input.work == source.input &&
          input.requestingResult == source.result.resultReference && input.result == source.result.result.value &&
          input.reason == R.message && target.pendingQuestion.contains(Question(H.identity(source).value, pending.revision.value.toString)) &&
          pending.revision == source.from.revision.next && pending.history.dropRight(1) == source.from.history &&
          pending.causation.value == H.identity(source).value + ":issued" && pending.lifecycle == Lifecycle.Active &&
          pending.currentProgression == source.from.currentProgression && pending.correlation == source.from.correlation &&
          pending.derivedCompositeOccurrence == source.from.derivedCompositeOccurrence &&
          pending.committedPredecessor == source.from.committedPredecessor &&
          pending.suspension.contains(SuspensionBoundary(
            org.goldenport.cncf.workflow.WorkflowInstancePersistence.ContinuationIdentity(H.identity(source).value),
            source.from.revision.next, ContextSnapshotReference(source.from.definition.workflowRevision.value),
            CompletionReference("goal-phase-ordered-human-completion-v1"),
            EvidenceReference("independent-ordered-human-answer-v1"), source.from.correlation))) Consequence.unit
        else F.invalid("Normalization crosses the saved unanswered question, full captured rule or original requesting result")
    } yield ()
  }

  private def observationReference(saved: Saved, kind: String): WorkflowMechanicalTransition.Reference =
    WorkflowMechanicalTransition.Reference(reference(saved.direction.target).identity + ":reconciliation-" + kind, "1")
  private def observationCause(kind: String): String = "ordered-source-history-normalization-reconciliation:" + kind
  private def pure = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](())
  private def initial(saved: Saved): Observation = {
    val target = saved.direction.target
    Observation(target, reference(target).identity, "1", target.recovery.intentIdentity, target.recovery.intentRevision, None, None)
  }
  private def _resume_target(saved: Saved, observed: Observed): Target = {
    val direction = reference(saved.direction.target)
    val intent = observationReference(saved, "intent")
    val result = observed.observation.outcome.map(_ => observationReference(saved, "result"))
    Target(saved.direction.target.recovery, observed.record.revision.value.toString, observed.record.causation.value,
      policy, resumeFrom = Some(ResumeFrom(direction.identity, direction.revision, intent.identity, intent.revision,
        result.map(_.identity), result.map(_.revision), observed.observation.outcome)))
  }

  /** The caller has reconstructed the original direction/question and this
    * observation. Each successor is checked against that actual immediate edge.
    */
  def retainedResumeC(session: F.Session, core: ActionCall.Core, saved: Saved,
    observed: Observed): Consequence[Option[Saved]] = for {
    actual <- retainedObservationC(session, core, saved).flatMap(_.map(Consequence.success)
      .getOrElse(F.invalid("Normalization resume lost its preceding reconciliation")))
    _ <- if (actual == observed && !actual.succeeded) Consequence.unit
      else F.invalid("Normalization resume cannot cross a successful or changed observation")
    target = _resume_target(saved, actual)
    _ <- if (validTarget(target)) Consequence.unit else F.invalid("Normalization resume provenance is malformed")
    following <- GoalPhaseNativeHistory.nextC(session.current, actual.record)
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- adapter.loadRetainedC(reference(target))(using core.executionContext)
    result <- retained match {
      case None => if (following.isEmpty) Consequence.success(Option.empty[Saved])
        else F.invalid("Normalization reconciliation has a successor without its exact retained resume direction")
      case Some(value) => for {
        direction <- codec.decode(codec.encode(value.receipt.payload.value)).fold(F.invalid, Consequence.success)
        _ <- if (direction.target == target && direction.participant == saved.direction.participant &&
            direction.reference == saved.direction.reference && direction.priorExecutionEnded &&
            following.exists(_.revision == value.receipt.next.revision)) Consequence.unit
          else F.invalid("Normalization resume crosses the latest native provenance or retained policy authority")
        next <- R._adjacent_c(session.current, actual.record, value.receipt.next, cause(target))
        _ <- if (next.lifecycle == Lifecycle.Active && next.correlation == actual.record.correlation &&
            next.derivedCompositeOccurrence == actual.record.derivedCompositeOccurrence &&
            next.committedPredecessor == actual.record.committedPredecessor) Consequence.unit
          else F.invalid("Normalization resume changed its native lifecycle or provenance")
      } yield Some(Saved(next, direction))
    }
  } yield result
  def retainedObservationC(session: F.Session, core: ActionCall.Core, saved: Saved): Consequence[Option[Observed]] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, observationCodec)
    intent <- adapter.loadRetainedC(observationReference(saved, "intent"))(using core.executionContext)
    result <- intent match {
      case None => Consequence.success(Option.empty[Observed])
      case Some(value) => for {
        expected <- Consequence.success(initial(saved))
        _ <- if (value.receipt.payload.value == expected) Consequence.unit else F.invalid("Normalization reconciliation intent differs")
        intended <- R._adjacent_c(session.current, saved.record, value.receipt.next, observationCause("intent"))
        result <- adapter.loadRetainedC(observationReference(saved, "result"))(using core.executionContext)
        observed <- result match {
          case None => Consequence.success(Observed(intended, expected))
          case Some(value) => for {
            payload <- observationCodec.decode(observationCodec.encode(value.receipt.payload.value)).fold(F.invalid, Consequence.success)
            _ <- if (payload.copy(outcome = None, diagnostic = None) == expected && payload.outcome.nonEmpty) Consequence.unit
              else F.invalid("Normalization reconciliation result crosses its actual original intent or direction")
            next <- R._adjacent_c(session.current, intended, value.receipt.next, observationCause("result"))
          } yield Observed(next, payload)
        }
      } yield Some(observed)
    }
  } yield result
  def reconcileC(session: F.Session, d: F.Declaration, core: ActionCall.Core, saved: Saved,
    acknowledgement: String => Consequence[Unit]): Consequence[Observed] = for {
    retained <- retainedObservationC(session, core, saved)
    result <- retained match {
      case Some(value) => Consequence.success(value)
      case None => for {
        _ <- if (session.current == saved.record && saved.direction.priorExecutionEnded) Consequence.unit
          else F.invalid("Normalization requires its exact adopted frontier and actual prior-ended evidence")
        adapter <- session.storage.bindMechanicalC(F.advanceOperation, observationCodec)
        intended <- saveC(adapter, d, core, saved, saved.record, "intent", initial(saved))
        _ <- acknowledgement("goal-source-history-normalization-prepared")
        actual <- GoalPhaseNativeSourceHistory.verifyCompletedPrefixC(core, saved.direction.target.recovery)
          .map[Consequence[GoldenportSourceHistoryDriver.Normalization]](_ => normalizeC(core, saved.direction.target))
          .fold(error => Consequence.success(GoldenportSourceHistoryDriver.Normalization("failed", Some(error.toString.take(8192)))), identity)
        _ <- acknowledgement("goal-source-history-normalization-executed")
        payload = initial(saved).copy(outcome = Some(actual.outcome), diagnostic = actual.diagnostic)
        recorded <- saveC(adapter, d, core, saved, intended, "result", payload)
        _ <- recordC(d, core, saved, payload)
        _ <- acknowledgement("goal-source-history-normalization-recorded")
      } yield Observed(recorded, payload)
    }
  } yield result
  private def saveC(adapter: WorkflowManagedMechanicalTransition[Observation], d: F.Declaration, core: ActionCall.Core,
    saved: Saved, from: InstanceRecord, kind: String, payload: Observation): Consequence[InstanceRecord] =
    adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, observationReference(saved, kind), from, from.revision,
      F.history(from, "phase.InspectCandidate", observationCause(kind)), TypedValue(observationCodec.typeIdentity, payload)),
      pure)(using core.executionContext).map(_.receipt.next)
  private def recordC(d: F.Declaration, core: ActionCall.Core, saved: Saved, observation: Observation): Consequence[Unit] =
    d.evidencePolicy match {
      case None => Consequence.unit
      case Some(policy) =>
        val reference = observationReference(saved, "result")
        val source = CommonEvidence.Source(ContextReference(reference.identity, reference.revision), d.input.repositoryIdentity,
          d.input.phaseIdentity, Some(observation.target.recovery.step), observation.target.recovery.sessionId,
          "source-history-normalization", if (observation.outcome.contains("failed")) "failed" else "passed")
        val facts = Json.obj("branch" -> observation.outcome.asJson, "observation" -> observation.asJson,
          "policyDirection" -> saved.direction.reference.asJson, "originalOutcome" -> "UNKNOWN".asJson,
          "causeResolution" -> "unassessed".asJson)
        try CommonEvidence.archiveManagedC(CommonEvidence.storage(core, policy), policy, source, facts.noSpaces,
          "identity", core.executionContext.clock).flatMap {
          case (_, Some((allocation, recorded))) => DevelopmentJournalEvidence.recordC(d.journalPolicy, core, allocation, recorded).map(_ => ())
          case _ => Consequence.unit
        }.recoverWith(_ => Consequence.unit)
        catch { case scala.util.control.NonFatal(_) => Consequence.unit }
    }
  private def normalizeC(core: ActionCall.Core, target: Target): Consequence[GoldenportSourceHistoryDriver.Normalization] = {
    val recovery = target.recovery
    val prepared = for {
      selection <- WorkflowSourceHistoryParameters.resolveC(core.executionContext)
      rule <- selection.rule.filter(rule => selection.style == "goldenport" && rule.asJson == recovery.historyPolicy)
        .map(Consequence.success).getOrElse(F.invalid("Normalization rule changed after policy adoption"))
      workspace <- WorkspaceId.parseC("sm-workflow-repository")
    } yield (rule, FileTarget(workspace))
    prepared.map[Consequence[GoldenportSourceHistoryDriver.Normalization]] { (rule, workspace) =>
      GoldenportSourceHistoryDriver.normalizeC(core, workspace, rule, WorkflowSourceHistory.Source(recovery.intent.path,
        LocalDate.parse(recovery.intent.updatedOn), None, None, recovery.intent.dateUncertain))
    }.fold(error => Consequence.success(GoldenportSourceHistoryDriver.Normalization("failed", Some(error.toString.take(8192)))), identity)
  }
  def verifyAffectedC(core: ActionCall.Core, target: Target): Consequence[String] = for {
    _ <- if (validTarget(target)) Consequence.unit else F.invalid("Normalization continuation target is invalid")
    selection <- WorkflowSourceHistoryParameters.resolveC(core.executionContext)
    rule <- selection.rule.filter(rule => selection.style == "goldenport" && rule.asJson == target.recovery.historyPolicy)
      .map(Consequence.success).getOrElse(F.invalid("Normalization continuation rule changed"))
    workspace <- WorkspaceId.parseC("sm-workflow-repository")
    _ <- GoldenportSourceHistoryDriver.verifyNormalizedC(core, FileTarget(workspace), rule,
      WorkflowSourceHistory.Source(target.recovery.intent.path, LocalDate.parse(target.recovery.intent.updatedOn),
        None, None, target.recovery.intent.dateUncertain))
  } yield target.recovery.intent.path

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseSourceHistoryNormalizationOperation
      else SmWorkflowComponent.GoalPhaseServiceService.AdoptGoalPhaseSourceHistoryNormalizationOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") ||
          request.operation != (if (describe) describeOperation else adoptionOperation)) F.invalid("Normalization Operation identity differs")
      else if (describe) NativeWorkflowOperationInput.textC(request, "sessionId").map(identity => Action(request, identity, None, port))
      else NativeWorkflowOperationInput.valueC[V.GoalPhaseSourceHistoryNormalizationAdoptionInput](request, "input").flatMap { input =>
        if (input == null || !exact(input.sessionId) || input.reference == null || !choices.contains(input.choice) ||
            !text(input.rationale) || !exact(input.reference.identity) || !exact(input.reference.revision))
          F.invalid("Normalization requires an actual policy direction reference and typed answer")
        else targetC(input.target).map(_ => Action(request, input.sessionId, Some(input), port))
      }
  }
  private final case class Action(request: Request, identity: String,
    input: Option[V.GoalPhaseSourceHistoryNormalizationAdoptionInput], port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(identity))
        selected = core.copy(executionContext = context)
        result <- input match {
          case None => port.describeSourceHistoryNormalizationC(identity, selected).map(NativeWorkflowOperationOutput.response(_))
          case Some(value) => port.adoptSourceHistoryNormalizationC(value, selected).map(NativeWorkflowOperationOutput.response(_))
        }
      } yield result
    }
  }
}
