package org.simplemodeling.textus.smworkflow.admission

import java.time.Instant
import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.EventRecord
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{Configuration, InstanceRecord, InstanceIdentity, InstanceRevision, HistoryEntry}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value.ReviewStepResult

/** Recover deterministic accepted-review follow-up, never semantic completion.
  * Native policy may publish a missing question or observe its exact successor.
  * @since   Oct.  1, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseReviewRecoveryServiceOperation {
  val operationName = "recoverGoalPhaseReview"
  val wireSchema = "sm-workflow.goal-phase-review-recovery-command.v1"
  val preparationOperationName = "prepareGoalPhaseReviewDecision"
  val preparationWireSchema = "sm-workflow.goal-phase-review-decision-preparation-command.v1"
  final case class Preparation(template: GoalPhaseClosureDecisionTemplate, collection: DataStore.CollectionId)

  def encodeCommand(identity: ContinuationIdentity): String = Json.obj(
    "schemaVersion" -> Json.fromString(wireSchema),
    "continuationIdentity" -> Json.fromString(identity.value)).noSpaces

  def encodePreparationCommand(identity: ContinuationIdentity): String = Json.obj(
    "schemaVersion" -> Json.fromString(preparationWireSchema),
    "continuationIdentity" -> Json.fromString(identity.value)).noSpaces

  def decodeCommandC(wire: String, schema: String = wireSchema): Consequence[ContinuationIdentity] = {
    val decoded = for {
      json <- Option(wire).toRight("Recovery JSON is missing")
        .flatMap(value => parse(value).left.map(_ => "Recovery JSON is invalid"))
      fields <- json.asObject.filter(_.keys.toSet == Set("schemaVersion", "continuationIdentity"))
        .toRight("Recovery fields differ")
      _ <- Either.cond(fields("schemaVersion").flatMap(_.asString).contains(schema), (), "Recovery schema differs")
      identity <- fields("continuationIdentity").flatMap(_.asString)
        .filter(value => value.nonEmpty && value.trim == value).toRight("Recovery identity is noncanonical")
    } yield ContinuationIdentity(identity)
    decoded.fold(Consequence.stateConflict(_), Consequence.success(_))
  }

  def bindC(params: ComponentCreate, declaration: GoalPhaseManagedStartAssembly.Declaration,
    selection: ProfileStartAdmission.CallScopedSelectionPort,
    artifact: CandidateAdmissionProducerAbi.Artifact, asOf: () => Instant,
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort, authority: GoalPhaseClosureAuthority.Ports,
    events: (ContinuationResult[ReviewStepResult], WorkflowCompletionServiceOperation.SelectedClosing) => Consequence[Vector[EventRecord]],
    preparation: Option[Preparation] = None,
    automaticPreparation: Option[GoalPhaseReviewDecisionPreparation.Configuration] = None,
    nativeQuestion: Option[GoalPhaseManagedStartAssembly.Declaration] = None
  )(using ExecutionContext): Consequence[spec.OperationDefinition] =
    bindEntryC(params, declaration, selection, artifact, asOf, snapshot, authority, events,
      preparation, automaticPreparation, nativeQuestion, nativeAdvance = false)

  /** Two declared Operations share one retained-review follow-up implementation.
    * Entry selection belongs to the configured Factory, not transport input.
    */
  def bindNativeAdvanceC(params: ComponentCreate, declaration: GoalPhaseManagedStartAssembly.Declaration,
    selection: ProfileStartAdmission.CallScopedSelectionPort,
    artifact: CandidateAdmissionProducerAbi.Artifact, asOf: () => Instant,
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort, authority: GoalPhaseClosureAuthority.Ports,
    events: (ContinuationResult[ReviewStepResult], WorkflowCompletionServiceOperation.SelectedClosing) => Consequence[Vector[EventRecord]]
  )(using ExecutionContext): Consequence[spec.OperationDefinition] =
    bindEntryC(params, declaration, selection, artifact, asOf, snapshot, authority, events,
      None, None, Some(declaration), nativeAdvance = true)

  private def bindEntryC(params: ComponentCreate, declaration: GoalPhaseManagedStartAssembly.Declaration,
    selection: ProfileStartAdmission.CallScopedSelectionPort,
    artifact: CandidateAdmissionProducerAbi.Artifact, asOf: () => Instant,
    snapshot: GoalPhaseStepClosureBoundary.SnapshotPort, authority: GoalPhaseClosureAuthority.Ports,
    events: (ContinuationResult[ReviewStepResult], WorkflowCompletionServiceOperation.SelectedClosing) => Consequence[Vector[EventRecord]],
    preparation: Option[Preparation],
    automaticPreparation: Option[GoalPhaseReviewDecisionPreparation.Configuration],
    nativeQuestion: Option[GoalPhaseManagedStartAssembly.Declaration], nativeAdvance: Boolean
  )(using ExecutionContext): Consequence[spec.OperationDefinition] = {
    val preparing = preparation != null && preparation.nonEmpty
    val selectedOperationName = if (nativeAdvance) GoalPhaseNativeHumanAdvanceOperation.operationName
      else if (preparing) preparationOperationName else operationName
    val schema = if (preparing) preparationWireSchema else wireSchema
    val generated = if (nativeAdvance)
      SmWorkflowComponent.GoalPhaseServiceService.AdvanceGoalPhaseHumanDecisionOperation.specification
      else if (preparing)
      SmWorkflowComponent.GoalPhaseServiceService.PrepareGoalPhaseReviewDecisionOperation.specification
      else SmWorkflowComponent.GoalPhaseServiceService.RecoverGoalPhaseReviewOperation.specification
    val parameter = if (nativeAdvance) "reviewContinuationIdentity" else "continuationIdentity"
    for {
      _ <- if (selection != null && artifact != null && asOf != null && snapshot != null && authority != null &&
          declaration != null && events != null && preparation != null && automaticPreparation != null && nativeQuestion != null &&
          (!nativeAdvance || nativeQuestion.nonEmpty) &&
          nativeQuestion.forall(value => value != null && value == declaration) &&
          !(nativeQuestion.nonEmpty && (preparation.nonEmpty || automaticPreparation.nonEmpty)) &&
          automaticPreparation.forall(value => value != null && value.validateC.toOption.nonEmpty && value.declaration == declaration) &&
          preparation.forall(value => value != null && value.collection != null && value.template != null &&
            value.template.validateC.toOption.nonEmpty) && generated.name == selectedOperationName &&
          generated.request.parameters.map(_.name).toSet == Set(parameter)) Consequence.unit
        else Consequence.configurationInvalid("Review recovery composition differs from CML")
      _ <- authority.validateC
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId,
        declaration.configuration, Vector(declaration.definition), declaration.instances, declaration.continuations)
      issued <- storage.bindIssuedC(StateMachineOperationIdentity("GoalPhaseService", "submitStepReview"), GoalPhaseStepCloseJson.payloadCodec)
      managed <- storage.bindCompletionC(StateMachineOperationIdentity("GoalPhaseService", "submitStepReview"),
        GoalPhaseStepCloseJson.payloadCodec, GoalPhaseReviewCompletionOperation.payloadCodec,
        StateMachineResultTypeReference("ReviewStepResult"), events)
      binding <- storage.bindInstancesC()
    } yield new spec.OperationDefinition {
      val specification = NativeWorkflowDescriptionMetadata.operation(generated, generated.response)
      def createOperationRequest(request: Request): Consequence[OperationRequest] =
        if (request == null || request.operation != selectedOperationName || !request.service.contains("GoalPhaseService"))
          Consequence.stateConflict("Recovery request differs from CML")
        else WorkflowJsonCommandRequest.commandArguments(request) match {
          case List(Argument("arg1", wire: String, _)) =>
            (if (nativeAdvance) GoalPhaseNativeHumanAdvanceOperation.decodeCommandC(wire)
              else decodeCommandC(wire, schema)).map { identity =>
            val incoming = request
            new CommandAction {
              val request = incoming
              override def commandExecutionMode = CommandExecutionMode.Sync
              def createCall(callCore: ActionCall.Core): ActionCall = new ProcedureActionCall {
                val core = callCore
                def execute(): Consequence[OperationResponse] = {
                  given ExecutionContext = executionContext
                  val instances = new WorkflowInstancePersistence {
                    def create(config: Configuration, record: InstanceRecord) = Consequence.stateConflict("Recovery cannot create an instance")
                    def append(config: Configuration, id: InstanceIdentity, revision: InstanceRevision, entry: HistoryEntry) =
                      Consequence.stateConflict("Recovery cannot append history")
                    def load(config: Configuration, id: InstanceIdentity) =
                      if (config == declaration.configuration) binding.loadC(id)
                      else Consequence.stateConflict("Recovery configuration differs")
                  }
                  for {
                    retained <- managed.loadRetainedC(identity).flatMap {
                      case Some(value) => Consequence.success(value)
                      case _ => Consequence.stateConflict("Recovery has no retained review")
                    }
                    submitted = retained.completion.result
                    current <- instances.load(declaration.configuration, submitted.handle.instanceIdentity).flatMap {
                      case Some(value) if value.revision == retained.completion.instanceRevision ||
                          (nativeQuestion.nonEmpty && value.revision == retained.completion.instanceRevision.next) => Consequence.success(value)
                      case _ => Consequence.stateConflict("Recovery review is no longer current")
                    }
                    selected <- selection.loadForCallC(current.correlation.value, core).flatMap {
                      case Some(value) if value.workflowIdentity == submitted.handle.workflowIdentity &&
                          value.workflowRevision == submitted.handle.workflowRevision => Consequence.success(value)
                      case _ => Consequence.stateConflict("Recovery selection differs from retained Workflow")
                    }
                    _ <- GoalPhaseParticipantAdmission.admitC(selected.participantIdentity, executionContext)
                    response <- if (nativeQuestion.nonEmpty && current.revision != retained.completion.instanceRevision)
                      // Existing adjacent pending question is recovered, not
                      // re-assessed as a fresh revision-three publication.
                      GoalPhaseManagedNativeHumanQuestion.advanceForCallC(identity,
                        GoalPhaseNativeHumanAdvanceOperation.choices, core, declaration)
                        .flatMap(GoalPhaseNativeHumanAdvanceOperation.responseC)
                    else GoalPhaseReviewCompletionOperation.projectAcceptedForCallC(artifact, asOf,
                      issued.issuedPersistence, instances, declaration.configuration, managed, snapshot, authority,
                      submitted, current, core, preparation.map(value => GoalPhaseReviewDecisionPreparation.Configuration(
                        declaration, value.template, value.collection)).orElse(automaticPreparation), nativeQuestion)
                  } yield response
                }
              }
            }
          }
          case _ => Consequence.stateConflict("Recovery requires one JSON argument")
        }
    }
  }
}
