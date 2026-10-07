package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, ProcedureActionCall, QueryAction}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.workflow.*
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Authorized read of retained native completion; not renewed human approval
  * or reconstruction of an unavailable original verification receipt.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseHumanCompletionQueryOperation {
  import GoalPhaseHumanDecisionContract.*
  val operationName = "getGoalPhaseHumanDecisionResult"
  val schema = "sm-workflow.goal-phase-human-completion-query.v1"

  def encodeQuery(identity: ContinuationIdentity): String = Json.obj(
    "schemaVersion" -> Json.fromString(schema), "questionIdentity" -> Json.fromString(identity.value)).noSpaces

  def decodeQueryC(wire: String): Consequence[ContinuationIdentity] = {
    val decoded = for {
      text <- Option(wire).filter(_.length <= 2048).toRight("Human completion query is missing or unbounded")
      json <- parse(text).left.map(_ => "Human completion query is invalid")
      fields <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "questionIdentity")).toRight("Human query fields differ")
      _ <- Either.cond(fields("schemaVersion").flatMap(_.asString).contains(schema), (), "Human query schema differs")
      identity <- fields("questionIdentity").flatMap(_.asString).filter(value => canonical(value, 256) &&
        value.startsWith("goal-phase-native-human:")).toRight("Human query identity is not native")
    } yield ContinuationIdentity(identity)
    decoded.fold(Consequence.stateConflict(_), Consequence.success(_))
  }

  def bindC(params: ComponentCreate, declaration: GoalPhaseManagedStartAssembly.Declaration)
    (using ExecutionContext): Consequence[spec.OperationDefinition] = {
    val generated = SmWorkflowComponent.GoalPhaseServiceService.GetGoalPhaseHumanDecisionResultOperation.specification
    if (params == null || declaration == null || generated.name != operationName ||
        generated.request.parameters.map(_.name).toSet != Set("schemaVersion", "questionIdentity"))
      Consequence.configurationInvalid("Human completion query differs from generated CML")
    else for {
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, declaration.configuration,
        Vector(declaration.definition), declaration.instances, declaration.continuations)
      issued <- storage.bindDecisionC(GoalPhaseNativeHumanQuestion.completionOperation, GoalPhaseClosureAuthority.inputCodec)
      completion <- storage.bindDecisionCompletionC(GoalPhaseNativeHumanQuestion.completionOperation,
        GoalPhaseClosureAuthority.inputCodec, answerPayloadCodec, StateMachineResultTypeReference("GoalPhaseHumanAnswerResult"),
        (_, _) => Consequence.success(Vector.empty))
      instances <- storage.bindInstancesC()
      readers <- GoalPhaseHostRecordReaders.bindC(params.configuration, declaration)
    } yield new spec.OperationDefinition {
      val specification = NativeWorkflowDescriptionMetadata.operation(generated, generated.response)
      def createOperationRequest(incoming: Request): Consequence[OperationRequest] =
        if (incoming == null || incoming.operation != operationName || !incoming.service.contains("GoalPhaseService"))
          Consequence.stateConflict("Human query differs from its CML Operation")
        else WorkflowJsonCommandRequest.commandArguments(incoming) match {
          case List(Argument("arg1", wire: String, _)) => decodeQueryC(wire).map { identity =>
            new QueryAction() {
              val request = incoming
              def createCall(capsule: ActionCall.Core): ActionCall = new ProcedureActionCall {
                val core = capsule
                def execute(): Consequence[OperationResponse] = {
                  given ExecutionContext = executionContext
                  for {
                    _ <- if (component.exists(_.componentId == SmWorkflowComponent.componentId)) Consequence.unit
                      else Consequence.stateConflict("Human query requires its owning Component")
                    retained <- completion.loadRetainedC(identity).flatMap {
                      case Some(value) => Consequence.success(value.completion)
                      case _ => Consequence.stateConflict("Human query has no retained native completion")
                    }
                    question <- issued.loadC(identity).flatMap {
                      case Some(value) => Consequence.success(value)
                      case _ => Consequence.stateConflict("Human query has no issued native question")
                    }
                    current <- instances.loadC(retained.result.handle.instanceIdentity).flatMap {
                      case Some(value) => Consequence.success(value)
                      case _ => Consequence.stateConflict("Human query instance is unavailable")
                    }
                    selection <- readers.selection.loadForCallC(current.correlation.value, core).flatMap {
                      case Some(value) if value.workflowIdentity == retained.result.handle.workflowIdentity &&
                          value.workflowRevision == retained.result.handle.workflowRevision => Consequence.success(value)
                      case _ => Consequence.stateConflict("Human query selection differs")
                    }
                    _ <- GoalPhaseParticipantAdmission.admitC(selection.participantIdentity, executionContext)
                    result <- GoalPhaseNativeHumanCompletionObservation.projectC(question, retained, current)
                  } yield OperationResponse.Scalar(result)
                }
              }
            }
          }
          case _ => Consequence.stateConflict("Human query requires one closed JSON argument")
        }
    }
  }
}
