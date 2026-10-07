package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.{ContinuationIdentity, StateMachineOperationIdentity}
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/**
 * Transport admission and retention only; no semantic dispatch, advance or Git.
 * @since   Oct.  1, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object GoalPhaseHumanDecisionServiceOperation {
  import GoalPhaseHumanDecisionContract.*
  val prepareOperationName = "prepareGoalPhaseHumanDecision"
  val answerOperationName = "recordGoalPhaseHumanAnswer"
  val prepareSchema = "sm-workflow.goal-phase-human-decision-preparation-command.v1"
  val answerCommandSchema = "sm-workflow.goal-phase-human-answer-command.v1"
  val questionResultSchema = "sm-workflow.goal-phase-human-decision-question-result.v1"
  val answerResultSchema = "sm-workflow.goal-phase-human-decision-answer-result.v1"
  final case class AnswerCommand(reviewIdentity: ContinuationIdentity, questionIdentity: ContinuationIdentity,
    answer: Answer, interactionReference: String)
  /** Supplied by the host, never read from runtime parameters or command JSON. */
  final case class Host(presentation: GoalPhaseClosureDecisionTemplate, verifier: GoalPhaseHumanInteractionVerification.Verifier) {
    def validateC: Consequence[Unit] =
      if (presentation == null || verifier == null) Consequence.configurationInvalid("Human Decision requires host presentation and verifier")
      else presentation.validateC
  }
  val choices = Vector(Choice("keep-open", "Keep this Phase open"),
    Choice("request-authorization", "Request separate bounded command authorization"))

  def encodePreparationCommand(review: ContinuationIdentity): String = Json.obj(
    "schemaVersion" -> Json.fromString(prepareSchema), "reviewContinuationIdentity" -> Json.fromString(review.value)).noSpaces
  def encodeAnswerCommand(value: AnswerCommand): String = Json.obj("schemaVersion" -> Json.fromString(answerCommandSchema),
    "reviewContinuationIdentity" -> Json.fromString(value.reviewIdentity.value),
    "questionIdentity" -> Json.fromString(value.questionIdentity.value), "choiceIdentity" -> Json.fromString(value.answer.choiceIdentity),
    "interactionReference" -> Json.fromString(value.interactionReference)).noSpaces

  private def fieldsC(wire: String, schema: String, names: Set[String]): Consequence[Map[String, String]] = {
    val decoded = for {
      text <- Option(wire).filter(_.length <= 8192).toRight("Human command is absent or unbounded")
      json <- parse(text).left.map(_ => "Human command JSON is invalid")
      fields <- json.asObject.filter(_.keys.toSet == names + "schemaVersion").toRight("Human command fields differ")
      _ <- Either.cond(fields("schemaVersion").flatMap(_.asString).contains(schema), (), "Human command schema differs")
      values <- names.toVector.foldLeft[Either[String, Map[String, String]]](Right(Map.empty)) { (prior, key) => for {
        accumulated <- prior
        value <- fields(key).flatMap(_.asString).filter(canonical(_, if (key == "interactionReference") 1024 else 256))
          .toRight("Human command identity is noncanonical")
      } yield accumulated.updated(key, value) }
    } yield values
    decoded.fold(Consequence.stateConflict(_), Consequence.success(_))
  }
  def decodePreparationCommandC(wire: String): Consequence[ContinuationIdentity] =
    fieldsC(wire, prepareSchema, Set("reviewContinuationIdentity")).map(value => ContinuationIdentity(value("reviewContinuationIdentity")))
  def decodeAnswerCommandC(wire: String): Consequence[AnswerCommand] =
    fieldsC(wire, answerCommandSchema, Set("reviewContinuationIdentity", "questionIdentity", "choiceIdentity", "interactionReference"))
      .flatMap { value =>
        val answer = Answer(value("choiceIdentity"))
        encodeAnswerC(answer).map(_ => AnswerCommand(ContinuationIdentity(value("reviewContinuationIdentity")),
          ContinuationIdentity(value("questionIdentity")), answer, value("interactionReference")))
      }

  def bindC(declaration: GoalPhaseManagedStartAssembly.Declaration, asOf: () => Instant,
    host: Host): Consequence[Vector[spec.OperationDefinition]] = {
    val prepare = SmWorkflowComponent.GoalPhaseServiceService.PrepareGoalPhaseHumanDecisionOperation.specification
    val answer = SmWorkflowComponent.GoalPhaseServiceService.RecordGoalPhaseHumanAnswerOperation.specification
    if (declaration == null || declaration.configuration == null || declaration.definition == null || host == null || asOf == null ||
        prepare.name != prepareOperationName || prepare.request.parameters.map(_.name).toSet != Set("reviewContinuationIdentity") ||
        answer.name != answerOperationName || answer.request.parameters.map(_.name).toSet !=
          Set("reviewContinuationIdentity", "questionIdentity", "choiceIdentity", "interactionReference"))
      Consequence.configurationInvalid("Human Operation composition differs from generated CML")
    else for {
      _ <- host.validateC
      _ <- declaration.configuration.validateC
      _ <- declaration.definition.validateC
    } yield Vector(new BoundOperation(declaration, asOf, host, prepare, preparing = true),
      new BoundOperation(declaration, asOf, host, answer, preparing = false))
  }

  private final class BoundOperation(declaration: GoalPhaseManagedStartAssembly.Declaration, asOf: () => Instant, host: Host,
    generated: spec.OperationDefinition.Specification, preparing: Boolean) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(generated, generated.response)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || request.operation != generated.name || !request.service.contains("GoalPhaseService"))
        Consequence.stateConflict("Human command differs from CML Operation")
      else request.arguments match {
        case List(Argument("arg1", wire: String, _)) =>
          val parsed = if (preparing) decodePreparationCommandC(wire).map(identity => (identity, Option.empty[AnswerCommand]))
            else decodeAnswerCommandC(wire).map(value => (value.reviewIdentity, Some(value)))
          parsed.map { case (review, command) =>
            val incoming = request
            new CommandAction {
              val request = incoming
              override def commandExecutionMode = CommandExecutionMode.Sync
              def createCall(callCore: ActionCall.Core): ActionCall = new ProcedureActionCall {
                val core = callCore
                def execute(): Consequence[OperationResponse] = command match {
                  case None => GoalPhaseManagedClosureDecisionRegistration.withRetainedIntentC(review, core, declaration) { intent =>
                    for {
                      question <- createC("goal-phase-human:" + review.value, intent,
                        host.presentation.copy(completionOperation = StateMachineOperationIdentity("GoalPhaseService", answerOperationName)), choices)
                      stored <- GoalPhaseManagedHumanQuestionRegistration.registerForCallC(review, question, core, declaration)
                      json <- GoalPhaseHumanQuestionRecord.encodeJsonC(stored)
                      payload <- parse(json).fold(_ => Consequence.stateConflict("Question result encoding failed"), Consequence.success(_))
                    } yield OperationResponse.Scalar(Json.obj("schemaVersion" -> Json.fromString(questionResultSchema), "question" -> payload).noSpaces)
                  }
                  case Some(value) => for {
                    receipt <- GoalPhaseManagedHumanAnswerRegistration.registerForCallC(value.reviewIdentity, value.questionIdentity,
                      value.answer, value.interactionReference, asOf(), core, declaration, host.verifier)
                    json <- GoalPhaseHumanAnswerRecord.encodeJsonC(receipt)
                    payload <- parse(json).fold(_ => Consequence.stateConflict("Answer result encoding failed"), Consequence.success(_))
                  } yield OperationResponse.Scalar(Json.obj("schemaVersion" -> Json.fromString(answerResultSchema), "receipt" -> payload).noSpaces)
                }
              }
            }
          }
        case _ => Consequence.stateConflict("Human command requires one JSON argument")
      }
  }
}
