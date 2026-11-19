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
  * @since Nov. 12, 2026
  * @version Nov. 19, 2026
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

  private val _prefix = "standalone-completed:"
  val purpose = "delegate-task-semantic-continuation"
  val resumeOperation = "resumeDelegateTaskSemanticContinuation"
  val choices = Vector("ContinueAfterDependencyResolution", "Defer")
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  private def _exact(s: String) = s != null && s.nonEmpty && s.trim == s
  private def _valid_reference(r: ContextReference) = r != null && _exact(r.identity) && _exact(r.revision)

  private def _covered_trend(payload: Payload): Option[DelegateTaskRepairContinuation.Target] =
    payload.delegateRepairContinuation.flatMap { direction =>
      direction.target.as[DelegateTaskRepairContinuation.Target].toOption.filter { target =>
        val before = target.business.state
        val current = payload.state
        target.asJson == direction.target &&
          Vector(target.componentInstance, target.sessionId, target.participant).forall(_exact) &&
          target.frontierRevision > 0 && target.frontier.isObject && before.stage == "Human" &&
          before.repairControl.stop.contains(before.message) &&
          direction.choice == "ContinueRepair" && direction.participant == current.participant &&
          _exact(direction.rationale) && _valid_reference(direction.reference) &&
          target.participant == before.participant && target.participant == current.participant && before.input == current.input &&
          before.repairControl == current.repairControl && before.generation == current.generation &&
          before.automaticCount == current.automaticCount && before.ordinaryCount == current.ordinaryCount &&
          before.candidate == current.candidate && before.receipts.asJson == current.receipts.asJson &&
          before.receipts.size <= before.selected.size && before.receipts.nonEmpty && before.candidate.nonEmpty &&
          before.receipts.zip(before.selected).forall((receipt, check) => _receipt_metadata(receipt, check))
      }
    }

  /** A derived historical trend is covered only by its actual native prior
    * ContinueRepair checkpoint, before the current issued Repair source.
    */
  private def _historical_trend_c(storage: WorkflowComponentStorage, current: InstanceRecord,
    payload: Payload, original: Original, core: ActionCall.Core): Consequence[Unit] =
    if (payload.state.repairControl.stop.isEmpty) Consequence.unit else for {
      target <- _covered_trend(payload).map(Consequence.success)
        .getOrElse(N.invalid("Current repair policy stop has no covering historical direction"))
      _ <- if (original.work.kind == "Repair" && target.sessionId == current.identity.value &&
        target.componentInstance == core.component.get.core.instanceId.canonicalKey &&
        target.frontierRevision + 1 <= original.sourceRevision) Consequence.unit
        else N.invalid("Historical repair direction does not precede this actual semantic Repair")
      before <- N.loadPayloadC(storage, current.identity.value, target.frontierRevision, core)
      admitted <- N.loadPayloadC(storage, current.identity.value, target.frontierRevision + 1, core)
      source <- N.loadPayloadC(storage, current.identity.value, original.sourceRevision, core)
      expected = before.copy(state = before.state.copy(stage = "Repair"),
        delegateRepairContinuation = payload.delegateRepairContinuation)
      _ <- if (DelegateTaskRepairContinuation.eligible(before) && before.asJson == target.business.asJson &&
        admitted.asJson == expected.asJson &&
        source.delegateRepairContinuation == payload.delegateRepairContinuation &&
        source.state.repairControl == before.state.repairControl &&
        source.state.generation == before.state.generation && source.state.automaticCount == before.state.automaticCount &&
        source.state.ordinaryCount == before.state.ordinaryCount) Consequence.unit
        else N.invalid("Historical repair policy direction lacks its exact persisted native association")
    } yield ()

  private def _decode_target(json: Json): Either[String, Target] = for {
    target <- json.as[Target].left.map(_.message)
    _ <- Either.cond(target.asJson == json && Vector(target.componentInstance, target.sessionId, target.participant).forall(_exact) &&
      target.frontierRevision > 0 && target.frontier.isObject && target.participant == target.business.state.participant &&
      _eligible(target.business, metadata = true) && target.original.work.session == target.sessionId &&
      target.original.sourceRevision > 0 && target.original.sourceRevision < target.frontierRevision &&
      target.original.work.sourceRevision == target.original.sourceRevision.toString &&
      target.original.work.state.stage == target.original.work.kind &&
      target.business.state.semanticReference.contains(target.original.resultReference) &&
      Set("Implement", "Repair")(target.original.work.kind) && target.original.result.outcome == "decision-needed" &&
      target.original.result.unresolvedQuestions.nonEmpty && _valid_reference(target.original.resultReference), (),
      "Semantic continuation target is incomplete or unsupported")
  } yield target

  private def _ancestry(payload: Payload, owner: String, session: String,
    revision: Long): Either[String, Vector[(Direction, Target)]] = {
    @scala.annotation.tailrec
    def _walk_(business: Payload, frontier: Long,
      collected: Vector[(Direction, Target)]): Either[String, Vector[(Direction, Target)]] =
      business.delegateSemanticContinuation match {
        case None => Right(collected)
        case Some(direction) => _decode_target(direction.target) match {
          case Left(error) => Left(error)
          case Right(previous) =>
            if (previous.componentInstance != owner || previous.sessionId != session ||
              previous.participant != payload.state.participant || previous.business.state.input != payload.state.input ||
              previous.frontierRevision >= frontier || direction.choice != choices.head ||
              !bindingC(owner, previous, direction).isSuccess)
              Left("Semantic continuation ancestry crosses its owner, request, revision or admitted direction")
            else _walk_(previous.business, previous.frontierRevision, collected :+ (direction -> previous))
        }
      }
    _walk_(payload, revision, Vector.empty)
  }

  def targetC(text: String): Consequence[Target] = (for {
    json <- io.circe.parser.parse(Option(text).getOrElse("")).left.map(_.message)
    target <- _decode_target(json)
    _ <- _ancestry(target.business, target.componentInstance, target.sessionId, target.frontierRevision)
  } yield target).fold(N.invalid(_), Consequence.success)

  private def _receipt_metadata(receipt: RepositorySyncNativeMergeValidation.Receipt,
    check: GoalPhaseNativeValidation.Check): Boolean =
    receipt.identity == check.identity && (receipt.externalCapture match {
      case Some(reference) => WorkflowSbtCapture.externalReferenceValid(reference) && reference.termination == "exited" &&
        receipt.sbt.exists(facts => facts.valid && facts.kind == check.kind &&
          ((reference.exitCode.contains(0) && facts.outcome == WorkflowSbtValidation.Outcome.Succeeded) ||
            (reference.exitCode.exists(_ != 0) && facts.outcome == WorkflowSbtValidation.Outcome.Failed)))
      case None => _receipt_hydrated(receipt, check)
    })

  private def _receipt_hydrated(receipt: RepositorySyncNativeMergeValidation.Receipt,
    check: GoalPhaseNativeValidation.Check): Boolean =
    receipt.identity == check.identity && RepositorySyncNativeMergeValidation.storedProductOutputValid(receipt) &&
      receipt.termination == "exited" && receipt.sbt.exists(facts =>
        facts.kind == check.kind && ((receipt.exitCode.contains(0) && facts.outcome == WorkflowSbtValidation.Outcome.Succeeded) ||
          (receipt.exitCode.exists(_ != 0) && facts.outcome == WorkflowSbtValidation.Outcome.Failed)))

  /** Public targets retain validated reference metadata. Only native hydrated
    * loading can establish the actual capture and executable stop eligibility.
    */
  def eligible(p: Payload): Boolean = _eligible(p, metadata = false)

  private def _eligible(p: Payload, metadata: Boolean): Boolean = {
    val s = p.state
    s.input.task.nonEmpty && s.input.delegation.exists(_.role == "destination") && s.input.kind != "validation" &&
      s.stage == "Human" && s.generation > 0 && s.generation < Long.MaxValue &&
      s.automaticCount >= 0 && s.ordinaryCount >= 0 && s.ordinaryCount <= s.automaticCount &&
      s.automaticCount < s.repairControl.effectiveLimit &&
      s.repairControl.policy == GoalPhaseRuntimeRepairControl.version && s.repairControl.selectedLimit == s.input.repairLimit &&
      s.repairControl.effectiveLimit > 0 && (s.repairControl.stop.isEmpty || _covered_trend(p).nonEmpty) &&
      s.repairControl.observations.forall(o => o.scope == s.input.requestIdentity && _exact(o.reference)) &&
      s.receipts.size <= s.selected.size && (s.receipts.isEmpty || s.candidate.nonEmpty) &&
      s.receipts.zip(s.selected).forall { (receipt, check) =>
        if (metadata) _receipt_metadata(receipt, check) else _receipt_hydrated(receipt, check)
      } && s.pendingCheck.isEmpty && s.commit.isEmpty &&
      s.commitExecution.isEmpty && s.commitReconciliation.isEmpty &&
      s.semanticReference.nonEmpty && p.supersededWork.isEmpty && p.hygiene.isEmpty && p.records.isEmpty &&
      s.recoveredValidation.isEmpty && p.validationRecovery.isEmpty && p.validationCaptureReservation.isEmpty
  }

  def bindingC(owner: String, target: Target, direction: Direction): Consequence[Json] =
    if (owner != target.componentInstance || direction.target != target.asJson ||
      direction.participant != target.participant || !choices.contains(direction.choice) || !_exact(direction.rationale) ||
      !_valid_reference(direction.reference) || direction.upstream == null || direction.upstream.isEmpty ||
      !direction.upstream.forall(_valid_reference) || direction.upstream.distinct != direction.upstream)
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

  /** The current semantic reference selects one actual native completion.
    * Earlier Implement, Repair and Review completions remain immutable history.
    */
  def originalC(storage: WorkflowComponentStorage, current: InstanceRecord,
    core: ActionCall.Core): Consequence[Original] = for {
    payload <- N.loadPayloadC(storage, current.identity.value, current.revision.value, core)
    reference <- payload.state.semanticReference.map(Consequence.success)
      .getOrElse(N.invalid("Current Human has no semantic result reference"))
    completions <- storage.bindCompletionC(N.completionOperation, N.inputCodec, N.resultCodec, N.closingType,
      (_, _) => Consequence.success(Vector.empty))
    issued <- storage.bindIssuedC(N.completionOperation, N.inputCodec)
    causes = current.history.map(_.causation.value).filter(_.startsWith(_prefix)).distinct
    originals <- causes.foldLeft(Consequence.success(Vector.empty[Original])) { (prior, cause) => for {
      found <- prior
      parts = cause.stripPrefix(_prefix).split(":", 2)
      revision <- if (parts.length == 2) parts(0).toLongOption.filter(_ > 0)
        .map(Consequence.success).getOrElse(N.invalid("Original completion revision is malformed"))
        else N.invalid("Original completion causation is malformed")
      identity = ContinuationIdentity(parts(1))
      retained <- completions.loadRetainedC(identity)(using core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Native completion is absent")))
      completion = retained.completion.result
      matching <- if (completion.resultReference != reference) Consequence.success(Vector.empty[Original]) else for {
        interaction <- issued.loadC(identity)(using core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(N.invalid("Original issued work is absent")))
        raw <- NativeWorkflowOperationOutput.workInputC(interaction)
        source <- N.loadPayloadC(storage, current.identity.value, revision, core)
        hydrated <- N.hydrateStateC(raw.state, current.identity.value, core, Some(source))
        work = raw.copy(state = hydrated)
        _ <- if (work.session == current.identity.value && work.sourceRevision == revision.toString &&
          Set("Implement", "Repair")(work.kind) && work.state == source.state && work.state.stage == work.kind &&
          completion.result.value.outcome == "decision-needed" && completion.result.value.unresolvedQuestions.nonEmpty)
          Consequence.unit else N.invalid("Original semantic result crosses its issued Implement/Repair source")
        _ <- S.completedC(work, completion.result.value, completion.resultReference)
      } yield Vector(Original(parts(1), revision, work, completion.result.value, completion.resultReference))
    } yield found ++ matching }
    original <- if (originals.size == 1) Consequence.success(originals.head)
      else N.invalid("Current semantic reference must identify exactly one native Implement/Repair completion")
  } yield original

  def operations(host: Host, definition: DefinitionBinding, evidence: EvidencePort): Vector[spec.OperationDefinition] =
    Vector(new Operation(false, host, definition, evidence), new Operation(true, host, definition, evidence))

  private final class Operation(resume: Boolean, host: Host, definition: DefinitionBinding,
    evidence: EvidencePort) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (resume)
      SmWorkflowComponent.DelegateTaskServiceService.ResumeDelegateTaskSemanticContinuationOperation
      else SmWorkflowComponent.DelegateTaskServiceService.DescribeDelegateTaskSemanticContinuationOperation)
    def createOperationRequest(incoming: Request): Consequence[OperationRequest] =
      NativeWorkflowResourceInput.selectC[org.goldenport.record.Record](incoming).map { source => new CommandAction {
      val request = incoming
      override def commandExecutionMode = CommandExecutionMode.Sync
      def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
        val core = c
        def execute(): Consequence[OperationResponse] = for {
          record <- NativeWorkflowResourceInput.readC(source,
            reference => read_resource_text(reference, Some(java.nio.charset.StandardCharsets.UTF_8)))
          input <- if (resume) summon[org.goldenport.convert.ValueReader[V.DelegateTaskSemanticContinuationInput]].readC(record)
            .map[Either[V.DelegateTaskSemanticContinuationInput, V.DelegateTaskSemanticContinuationDescriptionInput]](Left(_))
            else summon[org.goldenport.convert.ValueReader[V.DelegateTaskSemanticContinuationDescriptionInput]].readC(record)
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
          sourcerevision <- if (current.suspension.nonEmpty) Consequence.success(current.revision.value - 1)
            else if (current.causation.value.startsWith(_prefix)) current.causation.value.stripPrefix(_prefix).takeWhile(_ != ':').toLongOption
              .map(Consequence.success).getOrElse(N.invalid("Completion source revision is malformed"))
            else Consequence.success(current.revision.value)
          payload <- N.loadPayloadC(storage, session, sourcerevision, selected)
          _ <- GoalPhaseParticipantAdmission.admitC(payload.state.participant, context)
          owner = selected.component.get.core.instanceId.canonicalKey
          requested <- input.fold(i => targetC(i.target).map(Some(_)), _ => Consequence.success(None))
          retained <- _ancestry(payload, owner, session, current.revision.value).fold(N.invalid(_), Consequence.success)
          previous = requested.flatMap(target => retained.find(_._2.asJson == target.asJson))
          result <- previous match {
            case Some((direction, target)) => input match {
              case Left(answer) => for {
                proposed <- Consequence.success(Direction(target.asJson, answer.choice, answer.rationale, answer.participant,
                  ContextReference(answer.reference.identity, answer.reference.revision),
                  answer.upstream.map(r => ContextReference(r.identity, r.revision))))
                _ <- if (direction == proposed) Consequence.unit
                  else N.invalid("Repeated semantic continuation differs from its original admission")
              } yield _output(target, "already-resumed", "The original admission is retained; no work was issued")
              case Right(_) => N.invalid("Semantic continuation already admitted; collect with its exact direction")
            }
            case None => for {
              _ <- if (current.suspension.isEmpty) Consequence.unit else N.invalid("Pending work cannot be continued")
              _ <- if (!current.causation.value.startsWith(_prefix)) Consequence.unit
                else N.invalid("Ordinary Advance must first materialize the original Human completion")
              original <- originalC(storage, current, selected)
              _ <- _historical_trend_c(storage, current, payload, original, selected)
              stopped <- S.completedC(original.work, original.result, original.resultReference)
              business = payload
              _ <- if (eligible(business) && business.state.semanticReference.contains(original.resultReference) &&
                business.state.copy(evidenceReferences = stopped.evidenceReferences) == stopped) Consequence.unit
                else N.invalid("Current Human does not match its actual semantic completion")
              _ <- if (!retained.exists(_._2.original.resultReference == original.resultReference)) Consequence.unit
                else N.invalid("This semantic stop already has an admitted direction")
              frontier <- WorkflowInstanceRecordJsonV1.encodeC(current).flatMap(s =>
                io.circe.parser.parse(s).fold(e => N.invalid(e.message), Consequence.success))
              target = Target(owner, session, business.state.participant, current.revision.value, frontier, business, original)
              response <- input match {
                case Right(_) => Consequence.success(_output(target, "decision-required", "Independent Human admission is required"))
                case Left(answer) => for {
                  _ <- if (requested.exists(_.asJson == target.asJson)) Consequence.unit else N.invalid("Semantic target changed; describe it again")
                  direction = Direction(target.asJson, answer.choice, answer.rationale, answer.participant,
                    ContextReference(answer.reference.identity, answer.reference.revision),
                    answer.upstream.map(r => ContextReference(r.identity, r.revision)))
                  binding <- bindingC(owner, target, direction)
                  _ <- evidence.admitC(selected, direction.reference, binding)
                  response <- if (answer.choice == "Defer") Consequence.success(_output(target, "deferred", "Human deferred; state unchanged"))
                    else for {
                      _ <- if (host.root == business.root && host.branch == business.branch &&
                        host.repository == business.state.input.repositoryIdentity) Consequence.unit
                        else N.invalid("Current host crosses the original source boundary")
                      source <- N.semanticSourceAssessmentC(business, selected)
                      assessment = source.copy(questions = original.result.unresolvedQuestions)
                      _ <- WorkflowManagementReconstruction.requireReconstructibleC(assessment)
                      selection <- WorkflowManagementReconstruction.selectedC(assessment, Some(direction.reference), adoptProposed = true)
                      _ <- if (selection.adoptProposed && selection.reasons.isEmpty &&
                        selection.resumePoint != WorkflowManagementReconstruction.ResumePoint.Unresolved) Consequence.unit
                        else N.invalid("Current semantic source remains unresolved")
                      // Both selected logical capture points enter the authenticated
                      // semantic work kind. Ordinary Advance owns source recapture,
                      // and its subsequent Inspect owns fresh checks for that source.
                      stage = selection.resumePoint match {
                        case WorkflowManagementReconstruction.ResumePoint.FreshCapture |
                            WorkflowManagementReconstruction.ResumePoint.CurrentBoundary => original.work.kind
                        case _ => "Human"
                      }
                      _ <- if (Set("Implement", "Repair")(stage)) Consequence.unit
                        else N.invalid("Semantic continuation cannot reuse historical qualification")
                      next = business.copy(state = business.state.copy(stage = stage), delegateSemanticContinuation = Some(direction))
                      _ <- N.saveC(host, storage, current, next, selected)
                    } yield _output(target, "resumed-awaiting-advance", "Separate ordinary Advance owns the successor " + original.work.kind)
                } yield response
              }
            } yield response
          }
        } yield result
      }
    } }
  }
  private def _output(target: Target, outcome: String, message: String): OperationResponse =
    NativeWorkflowOperationOutput.response(V.DelegateTaskSemanticContinuationDescriptionResult(target.sessionId,
      target.componentInstance, outcome, message, target.asJson.noSpaces, choices, resumeOperation))
}
