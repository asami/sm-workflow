package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseOrderedProgression as O

/** Explicit HumanNewAttempt direction preserves an unknown historical outcome.
  * Description and authorization neither observe nor execute a selected check.
  * @since   Oct. 20, 2026
  * @version Oct. 20, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseValidationRecovery {
  val purpose = "goal-phase-validation-recovery"
  val describeOperation = "describeGoalPhaseValidationRecovery"
  val recoveryOperation = "recoverGoalPhaseValidation"
  val choices = Vector("AuthorizeNewAttempt", "Defer")
  val message = "HumanNewAttempt decision: the old selected check outcome remains unknown. AuthorizeNewAttempt permits a NEW attempt " +
    "of the SAME fixed selected SBT check using current host permissions; it may repeat effects of the old attempt. " +
    "Original native history, Plan, base, candidate and repair counters remain retained. Authorization only appends direction; " +
    "a later ordinary advanceGoalPhase executes the new attempt. Defer grants no execution. " +
    "This is not a provider observation, semantic Submit, check success, review acceptance or Phase closure."

  final case class RecoveryTarget(sessionId: String, planIdentity: String, planRevision: String,
    base: O.Snapshot, candidate: O.Snapshot, sourceRevision: String, frontierRevision: String,
    step: String, checkIdentity: String, checkOrdinal: Int, attempt: Int, intentRevision: String)
  object RecoveryTarget {
    given Encoder[RecoveryTarget] = deriveEncoder
    given Decoder[RecoveryTarget] = deriveDecoder
  }
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else Consequence.configurationInvalid("Validation recovery requires an independent Human evidence port")
  }

  def targetRecord(value: RecoveryTarget): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def targetC(value: Record): Consequence[RecoveryTarget] =
    if (value == null) _invalid("target is absent")
    else value.toJsonStringC.flatMap(text => io.circe.parser.parse(text)
      .left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
      .flatMap(decodeTarget).fold(_invalid(_), Consequence.success))
  def decodeTarget(value: Json): Either[String, RecoveryTarget] = for {
    supplied <- Option(value).toRight("Validation recovery target is absent")
    _ <- Either.cond(Vector("checkOrdinal", "attempt").forall(key =>
      supplied.hcursor.downField(key).focus.flatMap(_.asNumber).flatMap(_.toInt).isDefined), (),
      "Validation recovery check ordinal and attempt require JSON integers")
    target <- supplied.as[RecoveryTarget].left.map(_.message)
    _ <- Either.cond(validTarget(target), (), "Validation recovery target is incomplete")
  } yield target

  def validTarget(value: RecoveryTarget): Boolean = value != null &&
    Vector(value.sessionId, value.planIdentity, value.planRevision, value.step, value.checkIdentity).forall(exact) &&
    snapshot(value.base) && snapshot(value.candidate) && value.checkOrdinal > 0 && value.checkOrdinal <= 16 &&
    value.attempt >= 0 && value.attempt < Int.MaxValue &&
    Vector(value.sourceRevision, value.frontierRevision, value.intentRevision).forall(revision) &&
    value.sourceRevision.toLong < value.intentRevision.toLong && value.intentRevision.toLong <= value.frontierRevision.toLong

  def bindingC(component: String, target: RecoveryTarget, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (!exact(component) || !validTarget(target) || !choices.contains(choice) || !text(rationale) ||
        !exact(participant) || reference == null || !exact(reference.identity) || !exact(reference.revision))
      _invalid("Human binding requires the exact target, declared choice, rationale, participant and actual reference")
    else Consequence.success(Json.obj("componentInstance" -> Json.fromString(component),
      "validationRecovery" -> Json.obj("target" -> target.asJson, "answer" -> Json.obj(
        "choice" -> Json.fromString(choice), "rationale" -> Json.fromString(rationale),
        "participant" -> Json.fromString(participant), "reference" -> Json.obj(
          "identity" -> Json.fromString(reference.identity), "revision" -> Json.fromString(reference.revision))))))

  /** Select only declared typed fields. An annotation never becomes authority. */
  def selectBinding(value: Json): Either[String, Json] = for {
    supplied <- Option(value).toRight("Validation recovery binding is absent")
    component <- supplied.hcursor.get[String]("componentInstance").left.map(_.message)
    targetwire <- supplied.hcursor.downField("validationRecovery").get[Json]("target").left.map(_.message)
    target <- decodeTarget(targetwire)
    answer = supplied.hcursor.downField("validationRecovery").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    identity <- answer.downField("reference").get[String]("identity").left.map(_.message)
    revision <- answer.downField("reference").get[String]("revision").left.map(_.message)
    _ <- Either.cond(exact(component) && choices.contains(choice) && text(rationale) &&
      Vector(participant, identity, revision).forall(exact), (), "Validation recovery Human direction is invalid")
  } yield Json.obj("componentInstance" -> Json.fromString(component), "validationRecovery" -> Json.obj(
    "target" -> target.asJson, "answer" -> Json.obj("choice" -> Json.fromString(choice),
      "rationale" -> Json.fromString(rationale), "participant" -> Json.fromString(participant),
      "reference" -> Json.obj("identity" -> Json.fromString(identity), "revision" -> Json.fromString(revision)))))

  def admitC(d: GoalPhaseNativeRun.Declaration, core: ActionCall.Core, target: RecoveryTarget,
    input: V.GoalPhaseValidationRecoveryInput, participant: String): Consequence[Unit] = for {
    installation <- d.validationRecovery.map(Consequence.success).getOrElse(
      Consequence.securityPermissionDenied("Validation recovery has no installed independent Human evidence provider"))
    _ <- installation.validateC
    reference = ContextReference(input.reference.identity, input.reference.revision)
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, target, input.choice, input.rationale, participant, reference)
    _ <- installation.evidence.admitC(core, reference, binding)
  } yield ()

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseValidationRecoveryOperation
      else SmWorkflowComponent.GoalPhaseServiceService.RecoverGoalPhaseValidationOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") ||
          request.operation != (if (describe) describeOperation else recoveryOperation)) _invalid("Operation identity differs")
      else if (describe) NativeWorkflowOperationInput.textC(request, "sessionId").map(identity =>
        Action(request, identity, None, port))
      else NativeWorkflowOperationInput.valueC[V.GoalPhaseValidationRecoveryInput](request, "input").flatMap { input =>
        if (input == null || !exact(input.sessionId) || input.reference == null || !choices.contains(input.choice) ||
            !text(input.rationale) || !exact(input.reference.identity) || !exact(input.reference.revision))
          _invalid("input requires a declared Human direction and actual reference")
        else Consequence.success(Action(request, input.sessionId, Some(input), port))
      }
  }
  private final case class Action(request: Request, sessionId: String, input: Option[V.GoalPhaseValidationRecoveryInput],
    port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(sessionId))
        selected = core.copy(executionContext = context)
        result <- input match {
          case None => port.describeValidationRecoveryC(sessionId, selected).map(NativeWorkflowOperationOutput.response(_))
          case Some(value) => port.recoverValidationC(value, selected).map(NativeWorkflowOperationOutput.response(_))
        }
      } yield result
    }
  }
  private def revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def snapshot(value: O.Snapshot): Boolean = value != null && Vector(value.head, value.tree).forall(v =>
    v != null && v.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})"))
  private def exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) &&
    value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536
  private def _invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Validation recovery: " + reason)
}
