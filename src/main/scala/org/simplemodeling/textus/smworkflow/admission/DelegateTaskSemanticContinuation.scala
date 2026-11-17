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
import DelegateTaskNativeRun as N
import StandaloneFixExecution as S
import StandaloneFixExecution.given

/** Read the original semantic stop from native issued and completion records.
  * The description and admission operations must never infer it from a summary.
  */
private[smworkflow] object DelegateTaskSemanticContinuation {
  final case class Original(continuationId: String, sourceRevision: Long,
    work: S.Work, result: S.Result, resultReference: ContextReference)
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = deriveDecoder
  given Encoder[Original] = deriveEncoder
  given Decoder[Original] = deriveDecoder

  final case class Target(componentInstance: String, sessionId: String, participant: String,
    frontierRevision: Long, frontier: Json, business: Payload, original: Original)
  given Encoder[Target] = deriveEncoder
  given Decoder[Target] = deriveDecoder

  final case class Direction(target: Json, choice: String, rationale: String,
    participant: String, reference: ContextReference, upstream: Vector[ContextReference])
  given Encoder[Direction] = deriveEncoder
  given Decoder[Direction] = deriveDecoder

  private val prefix = "standalone-completed:"
  val purpose = "delegate-task-semantic-continuation"
  val resumeOperation = "resumeDelegateTaskSemanticContinuation"
  val choices = Vector("ContinueAfterDependencyResolution", "Defer")
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  private def exact(s: String) = s != null && s.nonEmpty && s.trim == s
  private def validReference(r: ContextReference) = r != null && exact(r.identity) && exact(r.revision)

  def targetC(text: String): Consequence[Target] = for {
    json <- io.circe.parser.parse(Option(text).getOrElse("")).fold(e => N.invalid(e.message), Consequence.success)
    target <- json.as[Target].fold(e => N.invalid(e.message), Consequence.success)
    _ <- if (target.asJson == json && Vector(target.componentInstance, target.sessionId, target.participant).forall(exact) &&
      target.frontierRevision > 0 && target.frontier.isObject && target.participant == target.business.state.participant &&
      eligible(target.business) && target.original.work.session == target.sessionId &&
      target.original.work.kind == "Implement" && target.original.result.outcome == "decision-needed" &&
      target.original.result.unresolvedQuestions.nonEmpty && validReference(target.original.resultReference)) Consequence.unit
      else N.invalid("Semantic continuation target is incomplete or unsupported")
  } yield target

  def eligible(p: Payload): Boolean = {
    val s = p.state
    s.input.task.nonEmpty && s.input.delegation.exists(_.role == "destination") && s.input.kind != "validation" &&
      s.stage == "Human" && s.generation == 1 && s.automaticCount == 0 && s.ordinaryCount == 0 &&
      s.repairControl.policy == GoalPhaseRuntimeRepairControl.version && s.repairControl.selectedLimit == s.input.repairLimit &&
      s.repairControl.effectiveLimit > 0 && s.repairControl.stop.isEmpty && s.repairControl.observations.isEmpty &&
      s.candidate.isEmpty && s.receipts.isEmpty && s.pendingCheck.isEmpty && s.commit.isEmpty &&
      s.commitExecution.isEmpty && s.commitReconciliation.isEmpty && s.reviewReference.isEmpty && s.priorReview.isEmpty &&
      s.semanticReference.nonEmpty && p.supersededWork.isEmpty && p.hygiene.isEmpty && p.records.isEmpty &&
      p.validationRecovery.isEmpty && p.validationCaptureReservation.isEmpty && p.delegateRepairContinuation.isEmpty &&
      p.delegateSemanticContinuation.isEmpty
  }

  def bindingC(owner: String, target: Target, direction: Direction): Consequence[Json] =
    if (owner != target.componentInstance || direction.target != target.asJson ||
      direction.participant != target.participant || !choices.contains(direction.choice) || !exact(direction.rationale) ||
      !validReference(direction.reference) || direction.upstream == null || direction.upstream.isEmpty ||
      !direction.upstream.forall(validReference) || direction.upstream.distinct != direction.upstream)
      N.invalid("Semantic continuation requires the exact target, covering Human direction and upstream references")
    else Consequence.success(Json.obj("componentInstance" -> owner.asJson,
      "delegateTaskSemanticContinuation" -> Json.obj("target" -> target.asJson,
        "answer" -> Json.obj("source" -> "Human".asJson, "choice" -> direction.choice.asJson,
          "rationale" -> direction.rationale.asJson, "participant" -> direction.participant.asJson,
          "reference" -> direction.reference.asJson, "upstream" -> direction.upstream.asJson))))

  def selectBinding(value: Json): Either[String, Json] = for {
    owner <- value.hcursor.get[String]("componentInstance").left.map(_.message)
    entry = value.hcursor.downField("delegateTaskSemanticContinuation")
    raw <- entry.get[Json]("target").left.map(_.message)
    target <- targetC(raw.noSpaces).toOption.toRight("Invalid semantic continuation target")
    answer = entry.downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    upstream <- answer.get[Vector[ContextReference]]("upstream").left.map(_.message)
    selected <- bindingC(owner, target, Direction(raw, choice, rationale, participant, reference, upstream)).toOption
      .toRight("Invalid semantic continuation direction")
    _ <- Either.cond(selected == value, (), "Semantic continuation evidence contains unsupported fields or source")
  } yield selected

  /** Exactly one original completion must exist in canonical native history. */
  def originalC(storage: WorkflowComponentStorage, current: InstanceRecord,
    core: ActionCall.Core): Consequence[Original] = for {
    causes <- Consequence.success(current.history.map(_.causation.value).filter(_.startsWith(prefix)).distinct)
    cause <- if (causes.size == 1) Consequence.success(causes.head)
      else N.invalid("Semantic continuation requires exactly one original completion")
    parts = cause.stripPrefix(prefix).split(":", 2)
    revision <- if (parts.length == 2) parts(0).toLongOption.filter(_ > 0)
      .map(Consequence.success).getOrElse(N.invalid("Original completion revision is malformed"))
      else N.invalid("Original completion causation is malformed")
    identity = ContinuationIdentity(parts(1))
    issued <- storage.bindIssuedC(N.completionOperation, N.inputCodec)
    interaction <- issued.loadC(identity)(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Original issued work is absent")))
    work <- NativeWorkflowOperationOutput.workInputC(interaction)
    source <- N.loadPayloadC(storage, current.identity.value, revision, core)
    completions <- storage.bindCompletionC(N.completionOperation, N.inputCodec, N.resultCodec, N.closingType,
      (_, _) => Consequence.success(Vector.empty))
    retained <- completions.loadRetainedC(identity)(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Original completion is absent")))
    completion = retained.completion.result
    _ <- if (work.session == current.identity.value && work.sourceRevision == revision.toString &&
      work.kind == "Implement" && work.state == source.state && work.state.stage == "Implement" &&
      work.state.generation == 1 && completion.result.value.outcome == "decision-needed" &&
      completion.result.value.unresolvedQuestions.nonEmpty) Consequence.unit
      else N.invalid("Original work is not an initial unresolved semantic Implement")
    _ <- S.completedC(work, completion.result.value, completion.resultReference)
  } yield Original(parts(1), revision, work, completion.result.value, completion.resultReference)

  def operations(host: Host, definition: DefinitionBinding, evidence: EvidencePort): Vector[spec.OperationDefinition] =
    Vector(new Operation(false, host, definition, evidence), new Operation(true, host, definition, evidence))

  private final class Operation(resume: Boolean, host: Host, definition: DefinitionBinding,
    evidence: EvidencePort) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (resume)
      SmWorkflowComponent.DelegateTaskServiceService.ResumeDelegateTaskSemanticContinuationOperation
      else SmWorkflowComponent.DelegateTaskServiceService.DescribeDelegateTaskSemanticContinuationOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] = Consequence.success(new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          input <- if (resume) NativeWorkflowOperationInput.valueC[V.DelegateTaskSemanticContinuationInput](incoming, "input")
            .map[Either[V.DelegateTaskSemanticContinuationInput, V.DelegateTaskSemanticContinuationDescriptionInput]](Left(_))
            else NativeWorkflowOperationInput.valueC[V.DelegateTaskSemanticContinuationDescriptionInput](incoming, "input")
              .map[Either[V.DelegateTaskSemanticContinuationInput, V.DelegateTaskSemanticContinuationDescriptionInput]](Right(_))
          session = input.fold(_.sessionId, _.sessionId)
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(session))
          selected = core.copy(executionContext = context)
          storage <- N.storageC(host, definition, selected)
          instances <- storage.bindInstancesC()(using context)
          current <- instances.loadC(InstanceIdentity(session))(using context).flatMap(
            _.map(Consequence.success).getOrElse(N.invalid("Semantic continuation Session is absent")))
          _ <- WorkflowForceClosure.requireOpenC(current)
          _ <- if (current.definition == definition && current.lifecycle == Lifecycle.Active) Consequence.unit
            else N.invalid("Semantic continuation requires its active DelegateTask definition")
          sourceRevision <- if (current.suspension.nonEmpty) Consequence.success(current.revision.value - 1)
            else if (current.causation.value.startsWith(prefix)) current.causation.value.stripPrefix(prefix).takeWhile(_ != ':').toLongOption
              .map(Consequence.success).getOrElse(N.invalid("Completion source revision is malformed"))
            else Consequence.success(current.revision.value)
          payload <- N.loadPayloadC(storage, session, sourceRevision, selected)
          _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, context)
          owner = selected.component.get.core.instanceId.canonicalKey
          requested <- input.fold(i => targetC(i.target).map(Some(_)), _ => Consequence.success(None))
          result <- payload.delegateSemanticContinuation match {
            case Some(direction) => input match {
              case Left(answer) => for {
                target <- requested.map(Consequence.success).getOrElse(N.invalid("Original target is required"))
                proposed = Direction(target.asJson, answer.choice, answer.rationale, answer.participant,
                  ContextReference(answer.reference.identity, answer.reference.revision),
                  answer.upstream.map(r => ContextReference(r.identity, r.revision)))
                _ <- if (direction == proposed && target.componentInstance == owner && target.sessionId == session) Consequence.unit
                  else N.invalid("Repeated semantic continuation differs from its original admission")
              } yield output(target, "already-resumed", "The original admission is retained; no work was issued")
              case Right(_) => N.invalid("Semantic continuation already admitted; collect with its exact direction")
            }
            case None => for {
              _ <- if (current.suspension.isEmpty) Consequence.unit else N.invalid("Pending work cannot be continued")
              _ <- if (!current.causation.value.startsWith(prefix)) Consequence.unit
                else N.invalid("Ordinary Advance must first materialize the original Human completion")
              original <- originalC(storage, current, selected)
              stopped <- S.completedC(original.work, original.result, original.resultReference)
              business = payload
              _ <- if (eligible(business) && business.state.semanticReference.contains(original.resultReference) &&
                business.state.copy(evidenceReferences = stopped.evidenceReferences) == stopped) Consequence.unit
                else N.invalid("Current Human does not match the original initial semantic completion")
              frontier <- WorkflowInstanceRecordJsonV1.encodeC(current).flatMap(s =>
                io.circe.parser.parse(s).fold(e => N.invalid(e.message), Consequence.success))
              target = Target(owner, session, business.state.participant, current.revision.value, frontier, business, original)
              response <- input match {
                case Right(_) => Consequence.success(output(target, "decision-required", "Independent Human admission is required"))
                case Left(answer) => for {
                  _ <- if (requested.contains(target)) Consequence.unit else N.invalid("Semantic target changed; describe it again")
                  direction = Direction(target.asJson, answer.choice, answer.rationale, answer.participant,
                    ContextReference(answer.reference.identity, answer.reference.revision),
                    answer.upstream.map(r => ContextReference(r.identity, r.revision)))
                  binding <- bindingC(owner, target, direction)
                  _ <- evidence.admitC(selected, direction.reference, binding)
                  response <- if (answer.choice == "Defer") Consequence.success(output(target, "deferred", "Human deferred; state unchanged"))
                    else for {
                      _ <- if (host.root == business.root && host.branch == business.branch &&
                        host.repository == business.state.input.repositoryIdentity) Consequence.unit
                        else N.invalid("Current host crosses the original source boundary")
                      _ <- N.implementationFreshC(business, selected)
                      next = business.copy(state = business.state.copy(stage = "Implement"), delegateSemanticContinuation = Some(direction))
                      _ <- N.saveC(host, storage, current, next, selected)
                    } yield output(target, "resumed-awaiting-advance", "Separate ordinary Advance owns the successor Implement")
                } yield response
              }
            } yield response
          }
        } yield result
      }
    })
  }
  private def output(target: Target, outcome: String, message: String): OperationResponse =
    NativeWorkflowOperationOutput.response(V.DelegateTaskSemanticContinuationDescriptionResult(target.sessionId,
      target.componentInstance, outcome, message, target.asJson.noSpaces, choices, resumeOperation))
}
