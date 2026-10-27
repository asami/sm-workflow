/**
 * @since   Oct. 26, 2026
 * @version Oct. 28, 2026
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
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import BoundedWorkRuntime.*
import BoundedWorkRuntime.given
import StandaloneFixExecution as S
import GoalTaskNativeRun as N

/** Exact-target application admission over CNCF's guarded WorkOrder disposition.
  * No operation here executes work or changes the business request. Unknown
  * external effects stay unknown even after a genuine Human permits a new issue.
  */
private[smworkflow] object GoalTaskMalformedIssuedWorkRecovery {
  val purpose = "goal-task-malformed-issued-work-recovery"
  val recoveryOperation = "recoverGoalTaskMalformedIssuedWork"
  val resumeCause = "goal-task-malformed-issued-work-recovered:"
  val choices = Vector("AuthorizeOneNewAttemptAcceptingUnresolvedEffects", "Defer")
  val nonexecutionChoice = "ContinueWithCoveringNonexecution"
  val recoveryChoices = choices :+ nonexecutionChoice
  val limitation = "External effects remain UNKNOWN unless independent host evidence covers the exact original issue and caller sources. " +
    "Available, unclaimed, initial revision and missing logs do not prove nonexecution. " +
    "A known native claim or completed result cannot be overridden. Recovery issues no work; a later ordinary Advance owns one new issue."

  final case class Target(componentInstance: String, sessionId: String, participant: String,
    frontierRevision: Long, oldContinuationId: String, frontier: Json, issued: Json,
    business: Payload, correction: Json, mappingOrigin: Json, continuationRevision: String)
  given Encoder[Target] = deriveEncoder
  given Decoder[Target] = deriveDecoder
  final case class Direction(target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference)
  import io.circe.generic.auto.*
  final case class Checkpoint(direction: Direction, externalEffects: String)
  val checkpointCodec = N.codec[Checkpoint]("GoalTaskMalformedIssuedWorkRecoveryCheckpoint")
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
    def admitNonexecutionC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit] =
      Consequence.securityPermissionDenied("No independent covering nonexecution observation is installed")
  }

  /** The entire issue, native frontier, original caller sources and correction
    * are selected by the owner. Serialization is not observation: only the
    * separately admitted host event can establish this negative execution fact.
    */
  def nonexecutionBindingC(owner: String, target: Target, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] = for {
    _ <- bindingC(owner, target, choices.head, rationale, participant, reference)
    _ <- if (target.business.state.input.sourceReferences.nonEmpty) Consequence.unit
      else N.invalid("Covering nonexecution requires the original caller source locators")
  } yield Json.obj("componentInstance" -> owner.asJson,
    "goalTaskMalformedIssuedWorkNonexecution" -> Json.obj("target" -> target.asJson,
      "observation" -> Json.obj("source" -> "IndependentHostObservation".asJson,
        "outcome" -> "NotExecuted".asJson, "choice" -> nonexecutionChoice.asJson,
        "callerSources" -> target.business.state.input.sourceReferences.asJson,
        "rationale" -> rationale.asJson, "participant" -> participant.asJson,
        "reference" -> reference.asJson)))

  def selectNonexecutionBinding(value: Json): Either[String, Json] = for {
    owner <- value.hcursor.get[String]("componentInstance").left.map(_.message)
    scope = value.hcursor.downField("goalTaskMalformedIssuedWorkNonexecution")
    raw <- scope.get[Json]("target").left.map(_.message)
    target <- decodeTarget(raw)
    observation = scope.downField("observation")
    rationale <- observation.get[String]("rationale").left.map(_.message)
    participant <- observation.get[String]("participant").left.map(_.message)
    reference <- observation.get[ContextReference]("reference").left.map(_.message)
    binding <- nonexecutionBindingC(owner, target, rationale, participant, reference).toOption
      .toRight("Invalid covering nonexecution observation")
    _ <- Either.cond(value == binding, (), "Nonexecution observation crosses its exact sources or contains unsupported fields")
  } yield binding

  def checkpointAdmitted(value: Checkpoint): Boolean = value != null && value.direction != null &&
    ((value.externalEffects == "UNKNOWN" && value.direction.choice == choices.head) ||
      (value.externalEffects == "NOT_EXECUTED" && value.direction.choice == nonexecutionChoice))

  private def exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  def missingBoth(requirement: ExecutionRequirement): Boolean = requirement != null &&
    requirement.reasoningClass.isEmpty && requirement.disposition.isEmpty

  def decodeTarget(value: Json): Either[String, Target] = for {
    target <- value.as[Target].left.map(_.message)
    _ <- Either.cond(target.asJson == value && Vector(target.componentInstance, target.sessionId,
      target.participant, target.oldContinuationId, target.continuationRevision).forall(exact) &&
      target.frontierRevision > 0 && target.business.state.input.task.nonEmpty &&
      target.business.state.participant == target.participant && target.correction.isObject &&
      target.mappingOrigin.isObject && target.frontier.isObject && target.issued.isObject,
      (), "Task recovery target is incomplete or has unsupported fields")
  } yield target
  /** Opaque JSON text preserves native keys and arbitrary profile-control names.
    * Public Record name projection must never rewrite the immutable native copy. */
  def targetC(text: String): Consequence[Target] =
    if (text == null) N.invalid("Recovery target is absent") else parseC(text).flatMap(value =>
      decodeTarget(value).fold(N.invalid(_), Consequence.success))

  def bindingC(owner: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (owner != target.componentInstance || participant != target.participant || !choices.contains(choice) ||
      !exact(rationale) || reference == null || !exact(reference.identity) || !exact(reference.revision))
      N.invalid("Recovery requires its exact owner, participant, choice, rationale and Human reference")
    else Consequence.success(Json.obj("componentInstance" -> owner.asJson,
      "goalTaskMalformedIssuedWorkRecovery" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("source" -> "Human".asJson, "choice" -> choice.asJson, "rationale" -> rationale.asJson,
          "participant" -> participant.asJson, "reference" -> Json.obj("identity" -> reference.identity.asJson,
            "revision" -> reference.revision.asJson)))))

  def selectBinding(value: Json): Either[String, Json] = for {
    owner <- value.hcursor.get[String]("componentInstance").left.map(_.message)
    targetJson <- value.hcursor.downField("goalTaskMalformedIssuedWorkRecovery").get[Json]("target").left.map(_.message)
    target <- decodeTarget(targetJson)
    answer = value.hcursor.downField("goalTaskMalformedIssuedWorkRecovery").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    selected <- bindingC(owner, target, choice, rationale, participant, reference).toOption.toRight("Invalid Task recovery direction")
    _ <- Either.cond(selected == value, (), "Task recovery evidence contains unsupported fields")
  } yield selected

  private def owner(core: ActionCall.Core): String = core.component.get.core.instanceId.canonicalKey
  private def parseC(text: String): Consequence[Json] = io.circe.parser.parse(text).fold(e => N.invalid(e.message), Consequence.success)
  private final case class Observation(target: Target, current: InstanceRecord,
    entityRevision: org.simplemodeling.model.datatype.EntityRevision)
  private def observeC(h: Host, storage: WorkflowComponentStorage, definition: DefinitionBinding,
    session: String, old: String, core: ActionCall.Core): Consequence[Observation] = for {
    adapter <- storage.bindWorkOrderSupersessionC(N.completionOperation, N.inputCodec, checkpointCodec)
    pending <- adapter.inspectC(ContinuationIdentity(old), InstanceIdentity(session))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Exact active pending Task issue is absent")))
    _ <- if (pending.current.definition == definition && pending.current.lifecycle == Lifecycle.Active &&
      pending.continuation.status == ContinuationRuntimePersistence.Status.Available && pending.continuation.claimId.isEmpty &&
      pending.entityRevision.value == 1L)
      Consequence.unit else N.invalid("Known native execution or changed definition requires result collection, not supersession")
    order <- pending.issued.current match {
      case order: WorkflowContinuation.WorkOrder[S.Work] if missingBoth(order.requirement) => Consequence.success(order)
      case _ => N.invalid("Recovery is only for WorkOrders missing BOTH reasoningClass and disposition")
    }
    work <- order.request.input.map(v => Consequence.success(v.value)).getOrElse(N.invalid("Original business input is absent"))
    revision <- scala.util.Try(work.sourceRevision.toLong).toOption.map(Consequence.success).getOrElse(N.invalid("Original source revision is invalid"))
    payload <- N.loadPayloadC(storage, session, revision, core)
    _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, core.executionContext)
    _ <- if (work.session == session && work.state == payload.state && payload.state.input.task.nonEmpty &&
      work.kind == payload.state.stage && Set("Implement", "Repair", "Review")(work.kind) &&
      order.request.completionOperation == N.completionOperation) Consequence.unit
      else N.invalid("Original WorkOrder crosses the retained Task business checkpoint")
    _ <- N.preflightExecutionC(payload.state.input, h.profiles, h.origin)
    requirement <- N.executionRequirementC(payload.state.input, work.kind)
    corrected <- GoalPhaseNativeExecution.bindIssuedC(pending.issued.copy(current = order.copy(requirement = requirement)), h.profiles, h.origin)
    originalJson <- WorkflowWorkOrderJsonV1.encodeC(pending.issued, N.inputCodec).flatMap(parseC)
    correctedJson <- WorkflowWorkOrderJsonV1.encodeC(corrected, N.inputCodec).flatMap(parseC)
    frontier <- WorkflowInstanceRecordJsonV1.encodeC(pending.current).flatMap(parseC)
    mappingOrigin <- mappingOriginC(h, requirement)
  } yield Observation(Target(owner(core), session, payload.state.participant, pending.current.revision.value, old,
    frontier, originalJson, payload, correctedJson.hcursor.downField("requirement").focus.get, mappingOrigin,
    pending.entityRevision.value.toString), pending.current, pending.entityRevision)

  private def mappingOriginC(h: Host, requirement: ExecutionRequirement): Consequence[Json] = for {
    mapping <- h.profiles.map(Consequence.success).getOrElse(N.invalid("Current mapping is absent"))
    origin <- h.origin.map(Consequence.success).getOrElse(N.invalid("Current origin is absent"))
    resolved <- mapping.resolveC(requirement, origin.current, origin.preference)
  } yield Json.obj("mappingIdentity" -> mapping.identity.asJson, "mappingRevision" -> mapping.revision.asJson,
      "contextIdentity" -> origin.current.contextIdentity.asJson, "profileIdentity" -> origin.current.profile.identity.asJson,
      "provider" -> origin.current.profile.provider.asJson, "model" -> origin.current.profile.model.asJson,
      "controls" -> origin.current.profile.controls.asJson, "forceInline" -> origin.preference.forceInline.asJson,
      "isolateContext" -> origin.preference.isolateContext.asJson,
      "resolvedProfile" -> Json.obj("identity" -> resolved.profile.identity.asJson, "provider" -> resolved.profile.provider.asJson,
        "model" -> resolved.profile.model.asJson, "controls" -> resolved.profile.controls.asJson))

  /** A changed mapping never silently reinterprets the admitted correction. */
  def correctionCurrentC(h: Host, storage: WorkflowComponentStorage, old: String, session: String,
    core: ActionCall.Core): Consequence[Unit] = for {
    adapter <- storage.bindWorkOrderSupersessionC(N.completionOperation, N.inputCodec, checkpointCodec)
    retained <- adapter.loadRetainedC(ContinuationIdentity(old), InstanceIdentity(session))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Reissue has no committed predecessor")))
    target = retained.receipt.payload.direction.target
    original <- WorkflowWorkOrderJsonV1.decodeC(target.issued.noSpaces, N.inputCodec)
    order <- original.current match {
      case order: WorkflowContinuation.WorkOrder[S.Work] => Consequence.success(order)
      case _ => N.invalid("Recovery predecessor is not a WorkOrder")
    }
    requirement <- N.executionRequirementC(target.business.state.input, target.business.state.stage)
    corrected <- GoalPhaseNativeExecution.bindIssuedC(original.copy(current = order.copy(requirement = requirement)), h.profiles, h.origin)
    json <- WorkflowWorkOrderJsonV1.encodeC(corrected, N.inputCodec).flatMap(parseC)
    mappingOrigin <- mappingOriginC(h, requirement)
    _ <- if (json.hcursor.downField("requirement").focus.contains(target.correction) &&
      mappingOrigin == target.mappingOrigin) Consequence.unit
      else N.invalid("Current mapping/origin differs from the independently admitted correction")
  } yield ()

  /** Only invoked by ordinary Advance after a committed unsuspension. */
  private[admission] def retainedInputC(storage: WorkflowComponentStorage, current: InstanceRecord,
    core: ActionCall.Core): Consequence[S.Input] = for {
    adapter <- storage.bindWorkOrderSupersessionC(N.completionOperation, N.inputCodec, checkpointCodec)
    retained <- adapter.loadRetainedC(ContinuationIdentity(current.causation.value.stripPrefix(resumeCause)),
      current.identity)(using core.executionContext).flatMap(
      _.map(Consequence.success).getOrElse(N.invalid("Recovery checkpoint is absent")))
  } yield retained.receipt.payload.direction.target.business.state.input

  /** The owning Advance retains all existing recovery authority checks. */
  def resumeC(h: Host, storage: WorkflowComponentStorage, current: InstanceRecord,
    core: ActionCall.Core): Consequence[InstanceRecord] = for {
    adapter <- storage.bindWorkOrderSupersessionC(N.completionOperation, N.inputCodec, checkpointCodec)
    old = current.causation.value.stripPrefix(resumeCause)
    retained <- adapter.loadRetainedC(ContinuationIdentity(old), current.identity)(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Recovery checkpoint is absent")))
    checkpoint = retained.receipt.payload
    _ <- if (retained.receipt.nextRevision == current.revision && checkpointAdmitted(checkpoint) &&
      checkpoint.direction.target.oldContinuationId == old)
      Consequence.unit else N.invalid("Recovery checkpoint does not authorize this one ordinary reissue")
    _ <- GoalPhaseParticipantAdmission.admitC(checkpoint.direction.target.participant, core.executionContext)
    _ <- correctionCurrentC(h, storage, old, current.identity.value, core)
    saved <- N.saveC(h, storage, current, checkpoint.direction.target.business.copy(supersededWork = Some(old)), core)
  } yield saved

  def operations(h: Host, definition: DefinitionBinding, evidence: EvidencePort): Vector[spec.OperationDefinition] =
    Vector(new Operation(false, h, definition, evidence), new Operation(true, h, definition, evidence))

  private final class Operation(recover: Boolean, h: Host, definition: DefinitionBinding,
    evidence: EvidencePort) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (recover)
      SmWorkflowComponent.GoalTaskServiceService.RecoverGoalTaskMalformedIssuedWorkOperation
      else SmWorkflowComponent.GoalTaskServiceService.DescribeGoalTaskMalformedIssuedWorkRecoveryOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          input <- if (recover) NativeWorkflowOperationInput.valueC[V.GoalTaskMalformedIssuedWorkRecoveryInput](incoming, "input")
            .map[Either[V.GoalTaskMalformedIssuedWorkRecoveryInput, V.GoalTaskMalformedIssuedWorkRecoveryDescriptionInput]](Left(_))
            else NativeWorkflowOperationInput.valueC[V.GoalTaskMalformedIssuedWorkRecoveryDescriptionInput](incoming, "input")
              .map[Either[V.GoalTaskMalformedIssuedWorkRecoveryInput, V.GoalTaskMalformedIssuedWorkRecoveryDescriptionInput]](Right(_))
          session = input.fold(_.sessionId, _.sessionId)
          old = input.fold(_.oldContinuationId, _.oldContinuationId)
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
          selected = core.copy(executionContext = context)
          storage <- N.storageC(h, definition, selected)
          adapter <- storage.bindWorkOrderSupersessionC(N.completionOperation, N.inputCodec, checkpointCodec)
          retained <- adapter.loadRetainedC(ContinuationIdentity(old), InstanceIdentity(session))(using context)
          response <- retained match {
            case Some(saved) => for {
              _ <- GoalPhaseParticipantAdmission.admitC(saved.receipt.payload.direction.target.participant, context)
              _ <- input.fold(i => targetC(i.target).flatMap(t => if (t == saved.receipt.payload.direction.target &&
                i.sessionId == t.sessionId && i.oldContinuationId == t.oldContinuationId) Consequence.unit
                else N.invalid("Repeated recovery crosses the committed target")), _ => Consequence.unit)
              successor <- successorC(storage, saved, selected)
              result <- outputC(saved.receipt.payload.direction.target, "already-recovered",
                recoveryMessage(saved.receipt.payload.externalEffects), successor, saved.receipt.payload.externalEffects)
            } yield result
            case None => for {
              observed <- observeC(h, storage, definition, session, old, selected)
              actual = observed.target
              result <- input match {
                case Right(_) => outputC(actual, "decision-required", limitation)
                case Left(input) => for {
                  expected <- targetC(input.target)
                  _ <- if (expected == actual && input.participant == actual.participant) Consequence.unit
                    else N.invalid("Recovery target changed; describe and obtain fresh independent Human direction")
                  reference = ContextReference(input.reference.identity, input.reference.revision)
                  nonexecution = input.choice == nonexecutionChoice
                  binding <- if (nonexecution) nonexecutionBindingC(owner(selected), actual, input.rationale, input.participant, reference)
                    else bindingC(owner(selected), actual, input.choice, input.rationale, input.participant, reference)
                  _ <- if (nonexecution) evidence.admitNonexecutionC(selected, reference, binding)
                    else evidence.admitC(selected, reference, binding)
                  result <- if (input.choice == "Defer") outputC(actual, "deferred", "No new attempt authorized. " + limitation)
                    else for {
                      current <- Consequence.success(observed.current)
                      entry = N.history(current, resumeCause + old)
                      effects = if (nonexecution) "NOT_EXECUTED" else "UNKNOWN"
                      checkpoint = Checkpoint(Direction(actual, input.choice, input.rationale, input.participant, reference), effects)
                      committed <- adapter.executeC(ContinuationIdentity(old), current.revision, observed.entityRevision, current, entry,
                        TypedValue(checkpointCodec.typeIdentity, checkpoint))(using context)
                      response <- outputC(committed.receipt.payload.direction.target, "recovered-awaiting-advance",
                        recoveryMessage(effects), externalEffects = effects)
                    } yield response
                } yield result
              }
            } yield result
          }
        } yield response
      }
    })
  }
  private def successorC(storage: WorkflowComponentStorage,
    saved: WorkflowManagedWorkOrderSupersession.Retained[Checkpoint], core: ActionCall.Core): Consequence[Option[String]] = for {
    issued <- storage.bindIssuedC(N.completionOperation, N.inputCodec)
    // One unchanged-state mechanical save precedes normal issuance. This is a
    // lookup address only; it is never reported unless that actual issue exists.
    id = saved.current.identity.value + ":standalone:" + (saved.receipt.nextRevision.value + 2L)
    actual <- issued.loadC(ContinuationIdentity(id))(using core.executionContext)
    result <- actual match {
      case None => Consequence.success(Option.empty[String])
      case Some(value) => value.current match {
        case order: WorkflowContinuation.WorkOrder[S.Work] if order.request.context.references.contains(
          ContextReference(saved.receipt.identity.value, "1")) && order.request.input.exists(input =>
            input.value.state == saved.receipt.payload.direction.target.business.state) => Consequence.success(Some(id))
        case _ => N.invalid("Committed successor differs from its actual retained predecessor")
      }
    }
  } yield result

  private def recoveryMessage(effects: String): String = if (effects == "NOT_EXECUTED")
    "Independent host evidence covers nonexecution of the exact original issue and caller sources. " +
      "Retirement issues no work; one ordinary Advance continues the unchanged authorized preparation."
    else limitation

  private def outputC(target: Target, outcome: String, message: String, successor: Option[String] = None,
    externalEffects: String = "UNKNOWN"): Consequence[OperationResponse] =
    Consequence.success(NativeWorkflowOperationOutput.response(
      V.GoalTaskMalformedIssuedWorkRecoveryDescriptionResult(target.sessionId, target.componentInstance, outcome,
        message, target.asJson.noSpaces, recoveryChoices, recoveryOperation, externalEffects, successor)))
}
