/**
 * @since   Oct. 30, 2026
 * @version Oct. 30, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import org.goldenport.record.Record
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceId, WorkspaceFilePath, FileRepresentation}
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.{SmWorkflowComponent, value as V}
import BoundedWorkRuntime.*
import BoundedWorkRuntime.given
import DelegateTaskNativeRun as N
import RepositorySyncManagedGitObservation as G

/** One independently admitted new observation. The old preparation is never
  * declared successful or unexecuted, and its checkpoint is never overwritten.
  */
private[smworkflow] object DelegateTaskCandidateContinuation {
  val operation = StateMachineOperationIdentity("DelegateTaskService", "recoverDelegateTaskCandidateContinuation")
  val inputType = "DelegateTaskCandidateContinuationInput"
  final case class Content(path: String, bytesBase64: Option[String])
  given Encoder[Content] = deriveEncoder
  given Decoder[Content] = deriveDecoder
  final case class Snapshot(root: String, branch: String, head: String, indexTree: String,
    dirty: Vector[G.DirtyPath], remainingContent: Vector[Content])
  private given Encoder[G.DirtyPath] = deriveEncoder
  private given Decoder[G.DirtyPath] = deriveDecoder
  given Encoder[Snapshot] = deriveEncoder
  given Decoder[Snapshot] = deriveDecoder
  final case class Target(componentInstance: String, sessionId: String, participant: String,
    frontierRevision: Long, frontier: Json, business: Json, observation: Snapshot, policy: Json)
  given Encoder[Target] = deriveEncoder
  given Decoder[Target] = deriveDecoder
  final case class Checkpoint(target: Target, reference: ContextReference)
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  given Encoder[Checkpoint] = deriveEncoder
  given Decoder[Checkpoint] = deriveDecoder

  def targetC(text: String): Consequence[Target] = for {
    json <- parseC(Option(text).getOrElse(""))
    target <- json.as[Target].fold(e => N.invalid(e.message), Consequence.success)
    original <- target.business.as[Payload].fold(e => N.invalid(e.message), Consequence.success)
    _ <- if (target.asJson == json && target.frontierRevision > 0 && target.frontierRevision < Long.MaxValue &&
      Vector(target.componentInstance, target.sessionId, target.participant).forall(s => s != null && s.nonEmpty && s.trim == s) &&
      target.frontier.isObject && target.policy.isObject && original.state.stage == "CandidateIntent" &&
      original.state.input.delegation.exists(_.role == "destination") && original.state.participant == target.participant &&
      original.candidateContinuation.isEmpty && original.state.semanticReference.nonEmpty &&
      original.root == target.observation.root && original.branch == target.observation.branch && original.baseHead == target.observation.head)
      Consequence.unit else N.invalid("Candidate continuation is not the exact retained destination preparation intent")
  } yield target

  def selectionInputC(input: V.DelegateTaskCandidateContinuationInput): Consequence[Json] = for {
    target <- targetC(input.target)
    _ <- if (input.sessionId == target.sessionId && input.participant == target.participant &&
      Vector(input.reference.identity, input.reference.revision).forall(s => s != null && s.nonEmpty && s.trim == s))
      Consequence.unit else N.invalid("Candidate direction crosses its Session, participant or Human reference")
  } yield Json.obj("sessionId" -> input.sessionId.asJson, "target" -> input.target.asJson,
    "participant" -> input.participant.asJson, "reference" -> Json.obj(
      "identity" -> input.reference.identity.asJson, "revision" -> input.reference.revision.asJson))

  private def parseC(text: String): Consequence[Json] =
    io.circe.parser.parse(text).fold(e => N.invalid(e.message), Consequence.success)

  private def policyC(h: Host, core: ActionCall.Core): Consequence[Json] = for {
    value <- core.executionContext.resolvedParameters.get("textus.subsystem.process-execution.plan") match {
      case Some(parameter) => parameter.value match {
        case ConfigurationValue.StringValue(text) => parseC(text)
        case _ => N.invalid("Resolved process execution policy must be an explicit host plan")
      }
      case None => N.invalid("Resolved host execution policy is absent")
    }
  } yield Json.obj("repository" -> h.repository.asJson, "root" -> h.root.asJson,
    "branch" -> h.branch.asJson, "paths" -> h.paths.asJson, "processExecution" -> value)

  private def snapshotC(payload: Payload, core: ActionCall.Core): Consequence[Snapshot] = for {
    root <- G.rootC(core)
    current <- G.observeC(core)
    index <- G.indexTreeC(core)
    workspace <- WorkspaceId.parseC("sm-workflow-repository")
    content <- current.dirty.filter(d => StandaloneFixExecution.owned(d.path, payload.state.input.ownedPaths) && d.worktree != ' ')
      .foldLeft(Consequence.success(Vector.empty[Content])) { (prior, dirty) => for {
        values <- prior
        path <- WorkspaceFilePath.parseC(dirty.path)
        read <- WorkflowFileExecution.readBytesC(core, FileTarget(workspace), path, FileRepresentation.Identity)
        _ <- if (read.bytes.isDefined || dirty.worktree == 'D') Consequence.unit
          else N.invalid("Remaining candidate content disappeared during observation")
      } yield values :+ Content(dirty.path, read.bytes.map(bytes => java.util.Base64.getEncoder.encodeToString(bytes.toArray))) }
    after <- G.observeC(core)
    _ <- if (root == payload.root && current.branch == payload.branch && current.head == payload.baseHead &&
      current == after && current.dirty.filterNot(d => StandaloneFixExecution.owned(d.path, payload.state.input.ownedPaths)) ==
        payload.unrelated.getOrElse(Vector.empty) && current.dirty.forall(d => !d.unmerged) &&
      current.dirty.filter(d => StandaloneFixExecution.owned(d.path, payload.state.input.ownedPaths)).forall(d =>
        StandaloneFixExecution.permitted(d.path, payload.state.input) && d.renamedFrom.forall(StandaloneFixExecution.permitted(_, payload.state.input))))
      Consequence.unit else N.invalid("Candidate continuation crosses its retained root, base, ownership or unrelated work")
  } yield Snapshot(root, current.branch, current.head, index, current.dirty, content)

  private def observeC(h: Host, current: InstanceRecord, payload: Payload, core: ActionCall.Core): Consequence[Target] = for {
    _ <- if (current.suspension.isEmpty && current.lifecycle == Lifecycle.Active && payload.state.stage == "CandidateIntent" &&
      payload.candidateContinuation.isEmpty && payload.state.input.delegation.exists(_.role == "destination") &&
      payload.root == h.root && payload.branch == h.branch && payload.state.input.repositoryIdentity == h.repository &&
      payload.state.input.ownedPaths.forall(h.paths.contains)) Consequence.unit
      else N.invalid("Only the original retained destination CandidateIntent can be continued once")
    _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, core.executionContext)
    observation <- snapshotC(payload, core)
    policy <- policyC(h, core)
    frontier <- WorkflowInstanceRecordJson(current)
  } yield Target(core.component.get.core.instanceId.canonicalKey, current.identity.value, payload.state.participant,
    current.revision.value, frontier, payload.asJson, observation, policy)

  private def WorkflowInstanceRecordJson(current: InstanceRecord): Consequence[Json] =
    WorkflowInstanceRecordJsonV1.encodeC(current).flatMap(parseC)

  /** A selected capability is consumed by the following durable intent before
    * any remaining staging or candidate capture. No loaded intent can retry.
    */
  def remainingPaths(input: StandaloneFixExecution.Input, dirty: Vector[G.DirtyPath]): Vector[String] =
    input.ownedPaths.filter(path => dirty.exists(d => StandaloneFixExecution.owned(d.path, Vector(path)) && d.worktree != ' '))

  def currentC(h: Host, current: InstanceRecord, payload: Payload, core: ActionCall.Core): Consequence[Unit] = for {
    saved <- payload.candidateContinuation.map(Consequence.success).getOrElse(N.invalid("No exact Human candidate continuation is retained"))
    _ <- targetC(saved.target.asJson.noSpaces)
    original <- saved.target.business.as[Payload].fold(e => N.invalid(e.message), Consequence.success)
    expected = original.copy(state = original.state.copy(stage = "CandidateContinuationReady",
      evidenceReferences = (original.state.evidenceReferences :+ saved.reference).distinct), candidateContinuation = Some(saved))
    _ <- GoalPhaseParticipantAdmission.admitC(saved.target.participant, core.executionContext)
    _ <- if (payload == expected && current.revision.value == saved.target.frontierRevision + 1 &&
      current.identity.value == saved.target.sessionId && core.component.get.core.instanceId.canonicalKey == saved.target.componentInstance)
      Consequence.unit else N.invalid("Candidate continuation no longer matches its exact authorized frontier")
    actual <- snapshotC(payload, core)
    policy <- policyC(h, core)
    _ <- if (actual == saved.target.observation && policy == saved.target.policy) Consequence.unit
      else N.invalid("Candidate index, remaining changes or resolved execution policy changed after Human selection")
  } yield ()

  def operations(h: Host, definition: DefinitionBinding,
    selections: ProfileStartAdmission.CallScopedSelectionPort): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, h, definition, selections), new Operation(false, h, definition, selections))

  private final class Operation(describe: Boolean, h: Host, definition: DefinitionBinding,
    selections: ProfileStartAdmission.CallScopedSelectionPort) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.DelegateTaskServiceService.DescribeDelegateTaskCandidateContinuationOperation
      else SmWorkflowComponent.DelegateTaskServiceService.RecoverDelegateTaskCandidateContinuationOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] =
      NativeWorkflowResourceInput.selectC[Record](incoming).map { source => new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          record <- NativeWorkflowResourceInput.readC(source,
            reference => read_resource_text(reference, Some(java.nio.charset.StandardCharsets.UTF_8)))
          input <- if (describe) Consequence.success(Option.empty[V.DelegateTaskCandidateContinuationInput])
            else summon[ValueReader[V.DelegateTaskCandidateContinuationInput]].readC(record).map(Some(_))
          session <- input.map(x => Consequence.success(x.sessionId)).getOrElse(
            summon[ValueReader[V.DelegateTaskCandidateContinuationDescriptionInput]].readC(record).map(_.sessionId))
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
          selected = core.copy(executionContext = context)
          storage <- N.storageC(h, definition, selected)
          instances <- storage.bindInstancesC()(using context)
          current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
            _.filter(_.definition == definition).map(Consequence.success).getOrElse(N.invalid("Exact delegation Session is absent")))
          payload <- N.loadPayloadC(storage, session, current.revision.value, selected)
          _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, context)
          result <- input match {
            case None => payload.candidateContinuation match {
              case Some(saved) => output(saved.target, "already-authorized")
              case None => observeC(h, current, payload, selected).flatMap(output(_, "decision-required"))
            }
            case Some(value) => for {
              target <- targetC(value.target)
              normalized <- selectionInputC(value)
              chosen <- selections.loadForCallC(value.selectionId, selected)
              port = new ProfileStartAdmission.SelectionPort {
                def loadC(id: String) = if (id == value.selectionId) Consequence.success(chosen)
                  else N.invalid("Candidate selection crosses its recorded identity")
              }
              _ <- ProfileStartAdmission.admitC(ProfileStartAdmission.Binding(operation, definition.workflowIdentity,
                definition.workflowRevision, inputType), TypedValue(inputType, normalized), value.selectionId,
                value.participant, value.idempotencyKey, port)
              _ <- if (chosen.flatMap(_.selectedInput).exists(x => x.typeIdentity == inputType && x.value == normalized))
                Consequence.unit else N.invalid("Actual Human selection does not bind the complete candidate continuation")
              reference = ContextReference(value.reference.identity, value.reference.revision)
              response <- payload.candidateContinuation match {
                case Some(saved) if saved == Checkpoint(target, reference) => output(target, "already-authorized")
                case Some(_) => N.invalid("Retained candidate continuation differs; it cannot be replaced")
                case None => for {
                  observed <- observeC(h, current, payload, selected)
                  _ <- if (observed == target) Consequence.unit else N.invalid("Candidate target changed; obtain a current description")
                  next = payload.copy(state = payload.state.copy(stage = "CandidateContinuationReady",
                    evidenceReferences = (payload.state.evidenceReferences :+ reference).distinct), candidateContinuation = Some(Checkpoint(target, reference)))
                  _ <- N.saveC(h, storage, current, next, selected)
                  answer <- output(target, "authorized-awaiting-advance")
                } yield answer
              }
            } yield response
          }
        } yield result
      }
    } }
  }
  private def output(target: Target, outcome: String): Consequence[OperationResponse] = Consequence.success(
    NativeWorkflowOperationOutput.response(V.DelegateTaskCandidateContinuationView(target.sessionId, outcome,
      "The old CandidateIntent and unknown overall outcome remain retained. This operation executes no staging, validation, commit or publication. " +
        "One ordinary Advance may stage only actually remaining owned changes and perform a new candidate observation; a new uncertain intent waits.", target.asJson.noSpaces)))
}
