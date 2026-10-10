package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import domain.statemachine.providedapi.GoalPhaseWorkflowStateMachineProvidedApi1
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent

/** Explicit host opt-in; no built-in human verifier and no legacy answer write.
  * The human evidence reference is the typed resultReference identity, resolved
  * independently by the host verifier rather than decoded as a receipt.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseManagedNativeHumanCompletion {
  import GoalPhaseHumanDecisionContract.*
  val resultSchema = "sm-workflow.goal-phase-native-human-answer-result.v1"
  private val closingType = StateMachineResultTypeReference("GoalPhaseHumanAnswerResult")

  def bindC(params: ComponentCreate, declaration: GoalPhaseManagedStartAssembly.Declaration,
    asOf: () => Instant, verifier: GoalPhaseHumanInteractionVerification.Verifier
  )(using ExecutionContext): Consequence[spec.OperationDefinition] =
    bindForCallC(params, declaration, asOf, verifier, None)

  private def bindForCallC(params: ComponentCreate, declaration: GoalPhaseManagedStartAssembly.Declaration,
    asOf: () => Instant, verifier: GoalPhaseHumanInteractionVerification.Verifier,
    call: Option[ActionCall.Core])(using bindingContext: ExecutionContext): Consequence[spec.OperationDefinition] =
    if (params == null || declaration == null || asOf == null || verifier == null || call == null)
      Consequence.configurationInvalid("Native human completion requires explicit host dependencies")
    else {
      val generated = GoalPhaseWorkflowStateMachineProvidedApi1.workflow
      val definition = GeneratedProvidedApiAbi.Definition(generated.schemaVersion, generated.generator,
        generated.identity, generated.version,
        GeneratedProvidedApiAbi.WorkflowSourceCorrelation(
          GeneratedProvidedApiAbi.SourceIdentity(generated.source.root.line),
          GeneratedProvidedApiAbi.SourceIdentity(generated.source.definition.line)),
        generated.providedOperations.map(value => GeneratedProvidedApiAbi.Operation(value.service, value.name,
          value.inputType, value.resultType, GeneratedProvidedApiAbi.SourceIdentity(value.source.line))))
      val identity = GoalPhaseNativeHumanQuestion.completionOperation
      val operations = definition.providedOperations.filter(value => value.service == identity.service && value.name == identity.operation)
      if (operations.size != 1 || operations.head.inputType != Some(resultType) ||
          operations.head.resultType != Some(closingType.value))
        Consequence.configurationInvalid("Native human completion differs from generated Provided ABI")
      else for {
        storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId,
          declaration.configuration, Vector(declaration.definition), declaration.instances, declaration.continuations)
        issued <- storage.bindDecisionC(identity, GoalPhaseClosureAuthority.inputCodec)
        instances <- storage.bindInstancesC()
        completion <- storage.bindDecisionCompletionC(identity, GoalPhaseClosureAuthority.inputCodec,
          answerPayloadCodec, closingType, (_, _) => Consequence.success(Vector.empty))
        bound <- {
          // One instance per current-call rebind, never shared between requests.
          var verified = Option.empty[GoalPhaseNativeHumanAnswer.Verified]
          var verifiedPredecessor = Option.empty[WorkflowInstancePersistence.InstanceRecord]
          var verifiedResult = Option.empty[ContinuationResult[Answer]]
          val codec = new WorkflowCompletionServiceOperation.Codec[Answer] {
            val resultTypeIdentity = resultType
            def decodeC(request: Request): Consequence[ContinuationResult[Answer]] = request.arguments match {
              case List(Argument("arg1", wire: String, _)) => WorkflowResultJsonV1.decodeC(wire, answerPayloadCodec).flatMap { submitted =>
                verified = None
                verifiedPredecessor = None
                verifiedResult = None
                call match {
                  // Declaration-time decoding is structural only. Rebinding
                  // inside the actual call performs host admission before the
                  // pure closing selection and before any managed mutation.
                  case None => Consequence.success(submitted)
                  case Some(core) =>
                    given ExecutionContext = core.executionContext
                    for {
                      question <- issued.loadC(submitted.continuationId).flatMap {
                        case Some(value) => Consequence.success(value)
                        case _ => Consequence.stateConflict("Native human completion has no issued question")
                      }
                      current <- instances.loadC(submitted.handle.instanceIdentity).flatMap {
                        case Some(value) => Consequence.success(value)
                        case _ => Consequence.stateConflict("Native human completion instance is unavailable")
                      }
                      readers <- GoalPhaseHostRecordReaders.bindC(params.configuration, declaration)
                      selected <- readers.selection.loadForCallC(current.correlation.value, core).flatMap {
                        case Some(value) if value.workflowIdentity == submitted.handle.workflowIdentity &&
                            value.workflowRevision == submitted.handle.workflowRevision => Consequence.success(value)
                        case _ => Consequence.stateConflict("Native human completion selection differs")
                      }
                      admitted <- GoalPhaseNativeHumanAnswer.admitForCallC(question, submitted, current,
                        selected.participantIdentity, submitted.resultReference.identity, asOf(), core, verifier)
                    } yield {
                      verified = Some(admitted)
                      verifiedPredecessor = Some(current)
                      verifiedResult = Some(submitted)
                      submitted
                    }
                }
              }
              case _ => Consequence.stateConflict("Native human completion requires one typed result JSON argument")
            }
            def encodeC(resume: ContinuationRuntime.Resume): Consequence[OperationResponse] =
              Consequence.stateConflict("Native human answer response requires committed instance observation")
            override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, submitted: ContinuationResult[Answer],
              completed: Option[WorkflowInstancePersistence.InstanceRecord]): Consequence[OperationResponse] = for {
              admitted <- verified.map(Consequence.success).getOrElse(
                Consequence.stateConflict("Native human answer has no verified current-call receipt"))
              _ <- completed.map(_ => Consequence.unit).getOrElse(
                Consequence.stateConflict("Native human answer has no committed instance"))
              // CNCF's managed completion owns the actual committed instance
              // and joint readback. Do not serialize and compare whole bodies
              // against the prepared copy merely to certify no change.
              raw <- GoalPhaseHumanAnswerRecord.encodeJsonC(admitted.receipt)
              receipt <- parse(raw).fold(_ => Consequence.stateConflict("Native human receipt encoding failed"), Consequence.success(_))
            } yield OperationResponse.Scalar(Json.obj("schemaVersion" -> Json.fromString(resultSchema), "receipt" -> receipt).noSpaces)
          }
          val closing = new WorkflowCompletionServiceOperation.ClosingProgram[Answer] {
            def selectC(submitted: ContinuationResult[Answer], current: WorkflowInstancePersistence.InstanceRecord) = {
              for {
                admitted <- verified.map(Consequence.success).getOrElse(
                  Consequence.stateConflict("Native human closing has no current-call admission"))
                predecessor <- verifiedPredecessor.map(Consequence.success).getOrElse(
                  Consequence.stateConflict("Native human closing has no selected predecessor"))
                _ <- if (verifiedResult.exists(value => NativeHumanQuestionSelection.sameAnswerTarget(value, submitted) &&
                    value.result.value == submitted.result.value)) Consequence.unit
                  else Consequence.stateConflict("Native human closing result differs from admission")
                _ <- if (GoalPhaseStepClosureBoundary.sameRecord(current, predecessor)) Consequence.unit
                  else Consequence.stateConflict("Native human closing base changed after admission")
                program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
                  ActionExecution.Completed(StateMachineOperationResult(closingType, submitted.resultReference)))
              } yield WorkflowCompletionServiceOperation.SelectedClosing(program, Some(admitted.next))
            }
          }
          WorkflowDecisionCompletionServiceOperation.bindC(definition, operations.head,
            WorkflowJsonCommandRequest.definition, spec.ResponseDefinition.void, codec, issued, instances, closing, completion,
            descriptiveAttributes = SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseHumanDecisionOperation
              .specification.content.descriptiveAttributes)
        }
      } yield if (call.nonEmpty) bound else CallScopedOperationBinding.wrap(bound, core =>
        bindForCallC(params, declaration, asOf, verifier, Some(core))(using core.executionContext))
    }
}
