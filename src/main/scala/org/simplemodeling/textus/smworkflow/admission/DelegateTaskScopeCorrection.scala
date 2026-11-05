/**
 * @since   Oct. 30, 2026
 * @version Nov.  5, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as _, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import BoundedWorkRuntime.*
import BoundedWorkRuntime.given
import StandaloneFixExecution as S
import DelegateTaskNativeRun as N

/** Same-purpose intake correction, before native acceptance of implementation.
  * The managed supersession retires an immutable issue; only ordinary Advance
  * issues its successor. Neither retirement nor an unclaimed issue establishes
  * that external effects did not occur.
  */
private[smworkflow] object DelegateTaskScopeCorrection {
  val operation = StateMachineOperationIdentity("DelegateTaskService", "correctDelegateTaskScope")
  val inputType = "DelegateTaskScopeCorrectionInput"
  val resumeCause = "delegate-task-scope-corrected:"
  final case class Target(componentInstance: String, sessionId: String, participant: String,
    frontierRevision: Long, oldContinuationId: String, frontier: Json, issued: Json,
    business: Payload, continuationRevision: String, additionalOwnedPaths: Vector[String], submissionConditions: Option[Vector[String]] = None)
  given Encoder[Target] = deriveEncoder[Target].mapJson(_.mapObject(_.filter { case ("submissionConditions", v) => !v.isNull; case _ => true }))
  given Decoder[Target] = deriveDecoder
  final case class Checkpoint(target: Target, reference: ContextReference)
  import io.circe.generic.auto.*
  val checkpointCodec = N.codec[Checkpoint]("DelegateTaskScopeCorrectionCheckpoint")

  def correctedInputC(input: S.Input, additional: Vector[String], submission: Option[Vector[String]] = None): Consequence[S.Input] = for {
    _ <- if (input != null && input.delegation.exists(_.role == "destination") &&
        additional != null && (additional.nonEmpty || submission.exists(next => !input.submissionConditions.contains(next))) && additional.distinct == additional &&
        !additional.exists(input.ownedPaths.contains)) Consequence.unit
      else N.invalid("Scope correction needs new literal paths or changed condition timing for the same destination Task")
    corrected = input.copy(ownedPaths = input.ownedPaths ++ additional, submissionConditions = submission.orElse(input.submissionConditions))
    _ <- S.inputC(corrected)
    _ <- if (additional.forall(S.permitted(_, corrected))) Consequence.unit
      else N.invalid("Added ownership crosses the retained delegation exclusions")
  } yield corrected

  def targetC(text: String): Consequence[Target] = for {
    json <- io.circe.parser.parse(Option(text).getOrElse("")).fold(e => N.invalid(e.message), Consequence.success)
    target <- json.as[Target].fold(e => N.invalid(e.message), Consequence.success)
    _ <- if (target.asJson == json && target.frontierRevision > 0 &&
        Vector(target.componentInstance, target.sessionId, target.participant, target.oldContinuationId,
          target.continuationRevision).forall(s => s != null && s.nonEmpty && s.trim == s) &&
        target.frontier.isObject && target.issued.isObject && target.business.state.participant == target.participant)
      Consequence.unit else N.invalid("Scope target is incomplete or contains unsupported data")
    _ <- correctedInputC(target.business.state.input, target.additionalOwnedPaths, target.submissionConditions)
  } yield target

  /** Identical normalization is used by passive preparation and current-call
    * admission. Selection metadata alone never authorizes different input.
    */
  def selectionInputC(input: V.DelegateTaskScopeCorrectionInput): Consequence[Json] = for {
    target <- targetC(input.target)
    _ <- if (input.sessionId == target.sessionId && input.oldContinuationId == target.oldContinuationId &&
        input.participant == target.participant && input.reference.identity.nonEmpty && input.reference.revision.nonEmpty)
      Consequence.unit else N.invalid("Scope correction crosses its exact Session, issue, participant or Human reference")
  } yield Json.obj("sessionId" -> input.sessionId.asJson, "oldContinuationId" -> input.oldContinuationId.asJson,
    "target" -> input.target.asJson, "participant" -> input.participant.asJson,
    "reference" -> Json.obj("identity" -> input.reference.identity.asJson, "revision" -> input.reference.revision.asJson))

  private final case class Observation(target: Target, current: InstanceRecord,
    entityrevision: org.simplemodeling.model.datatype.EntityRevision)
  private def _observe_c(h: Host, storage: WorkflowComponentStorage, definition: DefinitionBinding,
    session: String, old: String, additional: Vector[String], submission: Option[Vector[String]], core: ActionCall.Core): Consequence[Observation] = for {
    adapter <- storage.bindWorkOrderSupersessionC(N.completionOperation, N.inputCodec, checkpointCodec)
    pending <- adapter.inspectC(ContinuationIdentity(old), InstanceIdentity(session))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Exact pending Task issue is absent or already claimed")))
    _ <- if (pending.current.definition == definition && pending.current.lifecycle == Lifecycle.Active)
      Consequence.unit else N.invalid("Scope correction crosses the native definition or lifecycle")
    order <- pending.issued.current match {
      case value: WorkflowContinuation.WorkOrder[S.Work] => Consequence.success(value)
      case _ => N.invalid("Scope correction requires an actual pending implementation WorkOrder")
    }
    work <- order.request.input.map(x => Consequence.success(x.value)).getOrElse(N.invalid("Actual business input is absent"))
    revision <- scala.util.Try(work.sourceRevision.toLong).toOption.map(Consequence.success)
      .getOrElse(N.invalid("Work source revision is invalid"))
    payload <- N.loadPayloadC(storage, session, revision, core)
    _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, core.executionContext)
    _ <- if (work.session == session && work.kind == "Implement" && payload.state.stage == "Implement" &&
        work.state == payload.state && order.request.completionOperation == N.completionOperation &&
        payload.root == h.root && payload.branch == h.branch && payload.state.input.repositoryIdentity == h.repository)
      Consequence.unit else N.invalid("Scope correction is limited to the same retained initial implementation")
    _ <- correctedInputC(payload.state.input, additional, submission)
    _ <- if (additional.forall(h.paths.contains)) Consequence.unit
      else N.invalid("Added paths exceed the configured host ownership")
    frontier <- WorkflowInstanceRecordJsonV1.encodeC(pending.current).flatMap(_parse_c)
    issued <- WorkflowWorkOrderJsonV1.encodeC(pending.issued, N.inputCodec).flatMap(_parse_c)
  } yield Observation(Target(core.component.get.core.instanceId.canonicalKey, session, payload.state.participant,
    pending.current.revision.value, old, frontier, issued, payload, pending.entityRevision.value.toString, additional, submission),
    pending.current, pending.entityRevision)

  private def _parse_c(text: String): Consequence[Json] =
    io.circe.parser.parse(text).fold(e => N.invalid(e.message), Consequence.success)

  private def _checkpoint_c(storage: WorkflowComponentStorage, current: InstanceRecord,
    core: ActionCall.Core): Consequence[Checkpoint] = for {
    adapter <- storage.bindWorkOrderSupersessionC(N.completionOperation, N.inputCodec, checkpointCodec)
    old = current.causation.value.stripPrefix(resumeCause)
    retained <- adapter.loadRetainedC(ContinuationIdentity(old), current.identity)(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Scope correction checkpoint is absent")))
    _ <- if (retained.receipt.nextRevision == current.revision &&
        retained.receipt.payload.target.oldContinuationId == old) Consequence.unit
      else N.invalid("Scope checkpoint does not bind the current frontier")
  } yield retained.receipt.payload

  def retainedInputC(storage: WorkflowComponentStorage, current: InstanceRecord,
    core: ActionCall.Core): Consequence[S.Input] = _checkpoint_c(storage, current, core).flatMap { saved =>
    correctedInputC(saved.target.business.state.input, saved.target.additionalOwnedPaths, saved.target.submissionConditions)
  }

  def resumeC(h: Host, storage: WorkflowComponentStorage, current: InstanceRecord,
    core: ActionCall.Core): Consequence[InstanceRecord] = for {
    saved <- _checkpoint_c(storage, current, core)
    _ <- GoalPhaseParticipantAdmission.admitC(saved.target.participant, core.executionContext)
    input <- correctedInputC(saved.target.business.state.input, saved.target.additionalOwnedPaths, saved.target.submissionConditions)
    _ <- if (input.ownedPaths.forall(h.paths.contains)) Consequence.unit else N.invalid("Corrected host ownership changed")
    original = saved.target.business
    // Preserve base, generation, repair counts, results and old source references.
    state = original.state.copy(input = if (saved.target.additionalOwnedPaths.nonEmpty) input.copy(sourceReferences =
      (input.sourceReferences :+ saved.reference).distinct) else input,
      evidenceReferences = if (saved.target.submissionConditions.nonEmpty)
        (original.state.evidenceReferences :+ saved.reference).distinct else original.state.evidenceReferences)
    unrelated = original.unrelated.map(_.filterNot(path => S.owned(path.path, saved.target.additionalOwnedPaths) &&
      path.renamedFrom.forall(S.owned(_, saved.target.additionalOwnedPaths))))
    next <- N.saveC(h, storage, current, original.copy(state = state, unrelated = unrelated), core)
  } yield next

  def operations(h: Host, definition: DefinitionBinding,
    selections: ProfileStartAdmission.CallScopedSelectionPort): Vector[spec.OperationDefinition] =
    Vector(new Operation(false, h, definition, selections), new Operation(true, h, definition, selections))

  private final class Operation(correct: Boolean, h: Host, definition: DefinitionBinding,
    selections: ProfileStartAdmission.CallScopedSelectionPort) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (correct)
      SmWorkflowComponent.DelegateTaskServiceService.CorrectDelegateTaskScopeOperation
      else SmWorkflowComponent.DelegateTaskServiceService.DescribeDelegateTaskScopeCorrectionOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          input <- if (correct) NativeWorkflowOperationInput.valueC[V.DelegateTaskScopeCorrectionInput](incoming, "input")
            .map[Either[V.DelegateTaskScopeCorrectionInput, V.DelegateTaskScopeCorrectionDescriptionInput]](Left(_))
            else NativeWorkflowOperationInput.valueC[V.DelegateTaskScopeCorrectionDescriptionInput](incoming, "input")
              .map[Either[V.DelegateTaskScopeCorrectionInput, V.DelegateTaskScopeCorrectionDescriptionInput]](Right(_))
          session = input.fold(_.sessionId, _.sessionId)
          old = input.fold(_.oldContinuationId, _.oldContinuationId)
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
          selected = core.copy(executionContext = context)
          storage <- N.storageC(h, definition, selected)
          instances <- storage.bindInstancesC()(using context)
          current <- instances.loadC(InstanceIdentity(session))(using context)
            .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Scope correction Session is absent")))
          _ <- WorkflowForceClosure.requireOpenC(current)
          adapter <- storage.bindWorkOrderSupersessionC(N.completionOperation, N.inputCodec, checkpointCodec)
          retained <- adapter.loadRetainedC(ContinuationIdentity(old), InstanceIdentity(session))(using context)
          result <- input match {
            case Right(description) => retained match {
              case Some(saved) if saved.receipt.payload.target.additionalOwnedPaths == description.additionalOwnedPaths.toVector &&
                  saved.receipt.payload.target.submissionConditions == Option.when(description.submissionConditions.nonEmpty)(description.submissionConditions) =>
                GoalPhaseParticipantAdmission.admitC(saved.receipt.payload.target.participant, context)
                  .flatMap(_ => _output_c(saved.receipt.payload.target, "already-corrected"))
              case Some(_) => N.invalid("Description differs from the retained scope correction")
              case None => _observe_c(h, storage, definition, session, old, description.additionalOwnedPaths.toVector, Option.when(description.submissionConditions.nonEmpty)(description.submissionConditions), selected)
                .flatMap(value => _output_c(value.target, "decision-required"))
            }
            case Left(value) => for {
              expected <- targetC(value.target)
              normalized <- selectionInputC(value)
              chosen <- selections.loadForCallC(value.selectionId, selected)
              port = new ProfileStartAdmission.SelectionPort {
                def loadC(id: String) = if (id == value.selectionId) Consequence.success(chosen)
                  else N.invalid("Scope selection crosses its recorded identity")
              }
              _ <- GoalPhaseParticipantAdmission.admitC(value.participant, context)
              _ <- ProfileStartAdmission.admitC(ProfileStartAdmission.Binding(operation,
                definition.workflowIdentity, definition.workflowRevision, inputType), TypedValue(inputType, normalized),
                value.selectionId, value.participant, value.idempotencyKey, port)
              _ <- if (chosen.flatMap(_.selectedInput).exists(v => v.typeIdentity == inputType && v.value == normalized))
                Consequence.unit else N.invalid("Actual Human selection does not bind the complete correction")
              reference = ContextReference(value.reference.identity, value.reference.revision)
              response <- retained match {
                case Some(saved) if saved.receipt.payload == Checkpoint(expected, reference) => _output_c(expected, "already-corrected")
                case Some(_) => N.invalid("Repeated correction differs from its committed target")
                case None => for {
                  observed <- _observe_c(h, storage, definition, session, old, expected.additionalOwnedPaths, expected.submissionConditions, selected)
                  _ <- if (expected == observed.target) Consequence.unit else N.invalid("Scope target changed; obtain a current description")
                  _ <- adapter.executeC(ContinuationIdentity(old), observed.current.revision, observed.entityrevision,
                    observed.current, N.history(observed.current, resumeCause + old),
                    TypedValue(checkpointCodec.typeIdentity, Checkpoint(expected, reference)))(using context)
                  output <- _output_c(expected, "corrected-awaiting-advance")
                } yield output
              }
            } yield response
          }
        } yield result
      }
    })
  }
  private def _output_c(target: Target, outcome: String): Consequence[OperationResponse] = for {
    input <- correctedInputC(target.business.state.input, target.additionalOwnedPaths, target.submissionConditions)
  } yield NativeWorkflowOperationOutput.response(V.DelegateTaskScopeCorrectionView(target.sessionId, outcome,
    "Original issue and partial work are retained. External effects remain unchanged and unknown effects remain UNKNOWN. " +
      "All condition identities remain adopted; later acceptance conditions remain pending. Correction executes no work; ordinary Advance issues the amended same-purpose request.",
    target.asJson.noSpaces, input.ownedPaths))
}
