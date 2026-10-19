package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request as ProtocolRequest}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseNativeRun as F
import GoalPhaseNativeWork as S
import GoalPhaseOrderedProgression as O
import GoalPhasePlanRevision as R

/** Human direction for a genuine retained ordered semantic decision. Answer
  * completion records direction only; owning advance issues any subsequent work.
  * The older closure-Human contract is deliberately not used for ordered work.
  * @since   Oct.  7, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseOrderedHumanDecision {
  val completionOperation = StateMachineOperationIdentity("GoalPhaseService", "submitGoalPhaseOrderedDecision")
  val closingType = StateMachineResultTypeReference("GoalPhaseOrderedDecisionSubmissionResult")
  val purpose = "goal-phase-ordered-human-answer"
  val choices = Vector("ResumeAssignedWork", "ContinueRepairs", "AdoptPlanRevision", "RejectPlanRevision", "ApproveDependencyScope", "RejectDependencyScope", "Defer", "Cancel")
  private val _ordinary_choices = Vector("ResumeAssignedWork", "Defer", "Cancel")
  private val _repair_choices = Vector("ContinueRepairs", "Defer", "Cancel")
  private val _revision_choices = Vector("AdoptPlanRevision", "RejectPlanRevision", "Defer", "Cancel")
  private val _dependency_choices = Vector("ApproveDependencyScope", "RejectDependencyScope", "Defer", "Cancel")
  def choicesFor(controller: O.State): Vector[String] =
    if (controller.pendingRepair.nonEmpty) _repair_choices else _ordinary_choices
  private def _choices(controller: O.State, input: Input): Vector[String] =
    if (input.result.dependencyProposal.nonEmpty) _dependency_choices
    else if (input.planRevision.nonEmpty) _revision_choices else choicesFor(controller)

  /** Display only the exact choices captured by the owning issued question.
    * Neither presentation nor this projection supplies Human consent.
    */
  def issuedChoicesC(question: WorkflowInteraction[Input, Nothing]): Consequence[Vector[String]] = {
    val declared: Either[String, Vector[String]] = Option(question).map(_.current) match {
      case Some(value: WorkflowContinuation.Decision[Input]) => for {
        raw <- Option(value.presentation).flatMap(p => Option(p.currentSituation)).toRight("ordered Human presentation is absent")
        json <- io.circe.parser.parse(raw).left.map(_.message)
        values <- json.hcursor.get[Vector[String]]("choices").left.map(_.message)
        admitted <- Either.cond(values == _ordinary_choices || values == _repair_choices || values == _revision_choices || values == _dependency_choices, values,
          "ordered Human issued choices differ from a declared question")
      } yield admitted
      case _ => Left("ordered Human issued choices require an actual Decision")
    }
    declared.fold(F.invalid, Consequence.success)
  }
  final case class Input(work: S.Request, requestingResult: ContextReference, result: S.Result, reason: String,
    repairProgress: Option[GoalPhaseRepairProgress.Snapshot] = None,
    planRevision: Option[R.Captured] = None)
  final case class Answer(choice: String, rationale: String)
  trait EvidencePort {
    def admitC(core: ActionCall.Core, question: WorkflowInteraction[Input, Nothing],
      result: ContinuationResult[Answer], participant: String): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else F.invalid("ordered Human answer requires its independent host evidence port")
  }
  final case class Source(session: F.Session, input: S.Request, controller: O.State,
    from: InstanceRecord, result: ContinuationResult[S.Result], candidate: Option[GoalPhaseNativeCandidate.Saved],
    documents: Option[GoalPhaseDocumentSource.Bound] = None)
  private final case class Prepared(source: Source, question: WorkflowInteraction[Input, Nothing],
    intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent, pending: InstanceRecord)

  val inputCodec: WorkflowResultJsonV1.PayloadCodec[Input] = new WorkflowResultJsonV1.PayloadCodec[Input] {
    val typeIdentity = "GoalPhaseOrderedDecisionRequest"
    def encode(value: Input) = Json.obj("work" -> S.inputCodec.encode(value.work),
      "requestingResult" -> Json.obj("identity" -> Json.fromString(value.requestingResult.identity),
        "revision" -> Json.fromString(value.requestingResult.revision)),
      "result" -> S.resultCodec.encode(value.result), "reason" -> Json.fromString(value.reason)).mapObject { fields =>
        val progress = value.repairProgress.fold(fields)(progress => fields.add("repairProgress",
          summon[io.circe.Encoder[GoalPhaseRepairProgress.Snapshot]].apply(progress)))
        value.planRevision.fold(progress)(captured => progress.add("planRevision", summon[io.circe.Encoder[R.Captured]].apply(captured)))
      }
    def decode(value: Json): Either[String, Input] = for {
      fields <- Option(value).flatMap(_.asObject)
        .toRight("ordered Decision input fields differ")
      workJson <- fields("work").toRight("ordered Decision work is absent")
      work <- S.inputCodec.decode(workJson)
      reference <- fields("requestingResult").flatMap(_.asObject)
        .toRight("ordered Decision result reference differs")
      identity <- reference("identity").flatMap(_.asString).filter(_text).toRight("requesting result identity is absent")
      revision <- reference("revision").flatMap(_.asString).filter(_text).toRight("requesting result revision is absent")
      resultJson <- fields("result").toRight("ordered Decision result is absent")
      result <- S.resultCodec.decode(resultJson)
      reason <- fields("reason").flatMap(_.asString).filter(_text).toRight("ordered Decision reason is absent")
      progress <- GoalPhaseRepairProgress.decodeOption(fields)
      captured <- fields("planRevision") match {
        case None => Right(None)
        case Some(value) => value.as[R.Captured].left.map(_.message).map(Some(_))
      }
    } yield Input(work, ContextReference(identity, revision), result, reason, progress, captured)
  }
  val answerCodec: WorkflowResultJsonV1.PayloadCodec[Answer] = new WorkflowResultJsonV1.PayloadCodec[Answer] {
    val typeIdentity = "GoalPhaseOrderedDecisionAnswer"
    def encode(value: Answer) = Json.obj("choice" -> Json.fromString(value.choice), "rationale" -> Json.fromString(value.rationale))
    def decode(value: Json): Either[String, Answer] = for {
      fields <- Option(value).flatMap(_.asObject)
        .toRight("ordered Human answer fields differ")
      choice <- fields("choice").flatMap(_.asString).filter(choices.contains).toRight("ordered Human choice is unknown")
      rationale <- fields("rationale").flatMap(_.asString).filter(_text).toRight("ordered Human rationale is absent")
    } yield Answer(choice, rationale)
  }

  def identity(source: Source): ContinuationIdentity =
    ContinuationIdentity(source.from.identity.value + ":ordered-human:" + source.from.revision.value)
  def resumedC(source: Source, result: ContinuationResult[Answer]): Consequence[O.State] =
    if (!GoalPhaseDependencyScope.validateOptionC(source.result.result.value.dependencyProposal).isSuccess ||
        source.result.result.value.dependencyProposal.nonEmpty)
      F.invalid("Dependency scope direction requires its actual saved question and explicit answer")
    else if (!R.validateOptionC(source.result.result.value.planRevision).isSuccess || source.result.result.value.planRevision.nonEmpty)
      F.invalid("Plan revision direction requires its actual saved captured question")
    else if (!choicesFor(source.controller).contains(result.result.value.choice))
      F.invalid("ordered Human choice is not admitted for this actual question")
    else result.result.value.choice match {
      case "ResumeAssignedWork" => source.controller.acceptC(O.DecisionResumptionAdmitted(result.resultReference.identity))
      case "ContinueRepairs" => source.controller.acceptC(O.RepairContinuationAdmitted(result.resultReference.identity))
      case _ => F.invalid("a deferred or cancelled decision cannot select new work")
    }

  final class Port(d: F.Declaration, definition: DefinitionBinding,
    installation: Installation,
    sourceReader: (String, ContinuationIdentity, ActionCall.Core) => Consequence[Source],
    resume: (Source, O.State, InstanceRecord, Answer, ActionCall.Core) => Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult],
    acknowledgement: String => Consequence[Unit]) {
    def advanceC(source: Source, core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = for {
      questions <- source.session.storage.bindDecisionC(completionOperation, inputCodec)
      existing <- questions.loadC(identity(source))(using core.executionContext)
      projected <- _prepare_c(source, existing, core)
      plan <- existing match {
        case Some(_) => Consequence.success(projected)
        case None => for {
          saved <- questions.suspendC(projected.intent, projected.question)(using core.executionContext)
          _ <- acknowledgement("ordered-human-issued")
        } yield projected.copy(pending = saved.instance)
      }
      retained <- _retained_for_plan_c(plan, core)
      wire <- retained match {
        case None => NativeWorkflowOperationOutput.goalHumanC(plan.question, core)
        case Some((state, next, result)) => if (_continues(result.result.value.choice))
          resume(source, state, next, result.result.value, core)
        else _wait_c(next, result)
      }
    } yield wire

    /** Read-only traversal of the actual answer, never publication or effects. */
    def retainedC(source: Source, core: ActionCall.Core):
        Consequence[Option[(O.State, InstanceRecord, ContinuationResult[Answer], Input)]] = for {
      questions <- source.session.storage.bindDecisionC(completionOperation, inputCodec)
      issued <- questions.loadC(identity(source))(using core.executionContext).flatMap {
        case Some(value) => Consequence.success(value)
        case None => F.invalid("ordered Human answer has no actual issued question")
      }
      plan <- _prepare_c(source, Some(issued), core)
      observed <- _retained_for_plan_c(plan, core)
      input <- NativeWorkflowOperationOutput.decisionInputC(plan.question)
    } yield observed.map { (state, next, answer) => (state, next, answer, input) }

    private def _retained_for_plan_c(plan: Prepared, core: ActionCall.Core):
        Consequence[Option[(O.State, InstanceRecord, ContinuationResult[Answer])]] = for {
      native <- _completion_c(plan.source.session.storage)
      saved <- native.loadRetainedC(identity(plan.source))(using core.executionContext)
      observed <- saved match {
        case None => Consequence.success(None)
        case Some(value) => for {
          _ <- _issued_answer_c(plan, value.completion.result)
          state <- _answer_state_c(plan, value.completion.result)
          following <- GoalPhaseNativeHistory.nextC(plan.source.session.current, plan.source.from)
          entry <- following match {
            case Some(value) if value.suspension.exists(boundary =>
                boundary.continuationIdentity.value == identity(plan.source).value &&
                boundary.completion.value == "goal-phase-ordered-human-completion-v1") => Consequence.success(value)
            case _ => F.invalid("ordered Human answer has no exact actual pending predecessor")
          }
          pending <- GoalPhaseNativeHistory.prefixC(plan.source.session.current, entry.revision)
          next <- GoalPhaseNativeHistory.completionC(plan.source.session.current, pending,
            value.completion.result.resultReference, state.stage.toString)
        } yield Some((state, next, value.completion.result))
      }
    } yield observed

    def bindCompletionC(params: ComponentCreate): Consequence[spec.OperationDefinition] = for {
      _ <- installation.validateC
      storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, d.configuration,
        Vector(definition), d.instances, d.continuations)
      _ <- storage.bindDecisionC(completionOperation, inputCodec)
      _ <- _completion_c(storage)
      provided <- S.providedDefinition.providedOperations.find(o => o.service == completionOperation.service &&
        o.name == completionOperation.operation)
        .map(Consequence.success).getOrElse(F.invalid("ordered Human completion Operation is absent"))
    } yield new NativeWorkflowResultInput.Receiver {
      val specification = NativeWorkflowDescriptionMetadata.operation(
        SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseOrderedDecisionOperation)
      def createResultOperationRequestC(incoming: ProtocolRequest,
        result: ContinuationResult[NativeWorkflowResultInput.Value]): Consequence[OperationRequest] = NativeWorkflowResultInput.typedC(result, answerCodec).map { submitted =>
        new CommandAction {
          val request = incoming
          override def commandExecutionMode = CommandExecutionMode.Sync
          def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
            val core = c
            private var delegate = Option.empty[ActionCall]
            def execute(): Consequence[OperationResponse] = for {
              source <- sourceReader(submitted.handle.instanceIdentity.value, submitted.continuationId, core)
              questions <- source.session.storage.bindDecisionC(completionOperation, inputCodec)
              issued <- questions.loadC(submitted.continuationId)(using core.executionContext)
              question <- issued.map(Consequence.success).getOrElse(F.invalid("ordered Human answer has no actual issued question"))
              plan <- _prepare_c(source, Some(question), core)
              response <- for {
                  _ <- GoalPhaseParticipantAdmission.admitC(source.session.participantIdentity, core.executionContext)
                  _ <- installation.evidence.admitC(core, plan.question, submitted, source.session.participantIdentity)
                  bound <- _fresh_c(core, provided, plan, submitted)
                  action <- bound.createOperationRequest(core.action.request).flatMap {
                    case value: CommandAction => Consequence.success(value)
                    case _ => F.invalid("ordered Human completion is not its canonical CommandAction")
                  }
                  call = action.createCall(core.copy(action = action))
                  result <- { delegate = Some(call); call.execute() }
                } yield result
            } yield response
            override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
              val selected = delegate; delegate = None
              selected.fold(Consequence.success(response))(_.afterCommitResponseC(response))
            }
          }
        }
      }
    }

    private def _fresh_c(core: ActionCall.Core, provided: GeneratedProvidedApiAbi.Operation,
      plan: Prepared, submitted: ContinuationResult[Answer]): Consequence[spec.OperationDefinition] = for {
      questions <- plan.source.session.storage.bindDecisionC(completionOperation, inputCodec)
      instances <- plan.source.session.storage.bindInstancesC()(using core.executionContext)
      native <- _completion_c(plan.source.session.storage)
      expected <- _completed_c(plan, submitted)
      bound <- {
        val codec = new WorkflowCompletionServiceOperation.Codec[Answer] {
          val resultTypeIdentity = answerCodec.typeIdentity
          def decodeC(request: ProtocolRequest) = Consequence.success(submitted)
          def encodeC(resume: ContinuationRuntime.Resume) = F.invalid("ordered Human acknowledgement requires committed readback")
          override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, result: ContinuationResult[Answer],
            completed: Option[InstanceRecord]): Consequence[OperationResponse] = for {
            actual <- completed.map(Consequence.success).getOrElse(_uncertain("ordered Human completion has no committed instance"))
            _ <- acknowledgement("ordered-human-completed")
            // Managed completion supplies authoritative committed state. The
            // response performs no second store/body/semantic admission.
          } yield NativeWorkflowOperationOutput.response(NativeWorkflowOperationOutput.accepted(result, actual))
        }
        val closing = new WorkflowCompletionServiceOperation.ClosingProgram[Answer] {
          def selectC(result: ContinuationResult[Answer], current: InstanceRecord) = {
            val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              ActionExecution.Completed(StateMachineOperationResult(closingType, result.resultReference)))
            Consequence.success(WorkflowCompletionServiceOperation.SelectedClosing(program, Some(expected)))
          }
        }
        WorkflowDecisionCompletionServiceOperation.bindC(S.providedDefinition, provided, SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseOrderedDecisionOperation.specification.request,
          SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseOrderedDecisionOperation.specification.response, codec, questions, instances, closing, native,
          SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseOrderedDecisionOperation.specification.content.descriptiveAttributes)
      }
    } yield bound

    private def _prepare_c(source: Source,
      selected: Option[WorkflowInteraction[Input, Nothing]], core: ActionCall.Core): Consequence[Prepared] = for {
      _ <- installation.validateC
      _ <- GoalPhaseRepairProgress.validateOptionC(source.input.repairProgress)
      _ <- GoalPhaseRepairProgress.validateC(GoalPhaseRepairProgress.snapshot(source.controller))
      _ <- if (source.controller.stage == O.Stage.Decision && source.controller.decisionSource.exists(stage =>
          if (source.controller.documentDecision) stage != O.Stage.Decision && stage != O.Stage.Closed
          else (source.controller.pendingRepair.nonEmpty && Set(O.Stage.Validation, O.Stage.Review)(stage)) ||
            Set(O.Stage.Implementation, O.Stage.Repair, O.Stage.Review, O.Stage.PhaseReview)(stage)) &&
          source.from.currentProgression.contains(ProgressionReference("phase.Decision")) && source.from.suspension.isEmpty) Consequence.unit
        else F.invalid("ordered Human source is not a retained semantic decision")
      input <- _input_c(source, selected, core)
      required <- requiredC
      bundle = ContextBundle(if (input.result.dependencyProposal.nonEmpty) "Answer only the saved exact bounded dependency scope proposal"
        else if (input.planRevision.nonEmpty) "Answer only the saved complete current-Step Plan revision proposal"
        else if (source.controller.pendingRepair.nonEmpty) "Answer only the exhausted selected Step repair window"
        else if (source.controller.documentDecision) "Answer only the retained document acceptance conflict"
        else "Answer only the retained ordered semantic decision", Vector(source.result.resultReference), Vector.empty,
        ContextSnapshot(source.from.definition.workflowRevision.value, evidenceRevision = Some(source.from.revision.value.toString)))
      continuation = Continuation(StateMachineRunIdentity(source.from.identity.value), identity(source),
        StateMachineRevision(source.from.revision.next.value.toString), required, bundle)
      entry = HistoryEntry(HistorySequence(source.from.history.size.toLong + 1), source.from.revision.next, Lifecycle.Active,
        source.from.currentProgression, source.from.correlation, CausationReference(identity(source).value + ":issued"),
        source.from.derivedCompositeOccurrence, source.from.committedPredecessor,
        Some(SuspensionBoundary(NativeIdentity(identity(source).value), source.from.revision.next,
          ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(required.metadata.completionContract.identity),
          EvidenceReference(required.metadata.evidenceContract.identity), source.from.correlation)))
      intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
        d.configuration, source.from, source.from.revision, entry,
        ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available))
      pending <- source.from.appendC(source.from.revision, entry)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, source.from)
      question = WorkflowInteraction[Input, Nothing](handle, WorkflowContinuation.Decision(
        ContinuationRequest(continuation.runId, continuation.continuationId, continuation.expectedRevision, required.identity,
          required.operation, Some(TypedValue(inputCodec.typeIdentity, input)), required.resultType, bundle,
          required.metadata.completionContract, required.metadata.evidenceContract, completionOperation),
        MinimalPresentation("Ordered GoalPhase needs Human direction", Json.obj("choices" -> Json.arr(_choices(source.controller, input).map(Json.fromString)*),
          "notice" -> Json.fromString(if (input.result.dependencyProposal.nonEmpty)
            "ApproveDependencyScope selects only this exact proposed linked work unit, its acceptance and dependent work. RejectDependencyScope rejects that proposal. Both resume the same semantic frontier with the actual answer reference; all effect, target and acceptance authorities still apply. Review remains read-only and neither direction executes dependency work."
          else if (input.planRevision.nonEmpty)
            "AdoptPlanRevision selects only this saved complete Plan revision. RejectPlanRevision retains the original Plan and resumes its same semantic work. Both retain history and counts; execution, source permissions, checks, review and closure require their existing authority."
          else if (source.controller.pendingRepair.nonEmpty)
            "ContinueRepairs renews only the same positive Step repair window and reserves its first batch. Original failure evidence and history remain; commands, scope, review acceptance and closure need their existing authority."
          else if (source.controller.documentDecision)
            "Resume rechecks current documents at the same frontier; it grants no amendment, command, review acceptance or repair allowance."
          else "Resume requests the same semantic work; it grants no commands, review acceptance, Plan amendment or repair allowance.")).noSpaces)))
      actual = selected.getOrElse(question)
      offered <- issuedChoicesC(actual)
      _ <- if (offered == _choices(source.controller, input)) Consequence.unit
        else F.invalid("ordered Human issued choices differ from the saved business input")
    } yield Prepared(source, actual, intent, pending)

    private def _input_c(source: Source,
      selected: Option[WorkflowInteraction[Input, Nothing]], core: ActionCall.Core): Consequence[Input] = for {
      _ <- R.validateOptionC(source.result.result.value.planRevision)
      _ <- GoalPhaseDependencyScope.admitResultC(source.input.kind, source.result.result.value.outcome,
        source.result.result.value.dependencyProposal)
      _ <- if (source.result.result.value.planRevision.isEmpty || source.result.result.value.dependencyProposal.isEmpty) Consequence.unit
        else F.invalid("Only one proposal kind may request a decision")
      _ <- if (source.documents != null) Consequence.unit else F.invalid("ordered Human document Option is null")
      input <- selected match {
        case Some(question) => NativeWorkflowOperationOutput.decisionInputC(question)
        case None => for {
          captured <- source.result.result.value.planRevision match {
            case None => Consequence.success(Option.empty[R.Captured])
            case Some(proposal) => for {
              captured <- source.documents match {
                case None => R.captureC(source.controller, proposal, d.projectAcceptance)
                case Some(previous) => for {
                  imported <- GoalPhaseDocumentSource.loadForCallC(previous.source, core)
                  captured <- R.captureDocumentsC(source.controller, proposal, d.projectAcceptance, previous, imported)
                } yield captured
              }
              checks = captured.plan.steps(source.controller.cursor).checks
              _ <- if (checks.nonEmpty && d.validationPolicy.exists(policy =>
                  checks.forall(identity => policy.checks.exists(_.identity == identity)))) Consequence.unit
                else F.invalid("Plan revision checks must match the declared current-Step validation policy")
            } yield Some(captured)
          }
        } yield Input(source.input, source.result.resultReference, source.result.result.value, source.controller.decision.get,
          Some(GoalPhaseRepairProgress.snapshot(source.controller)), captured)
      }
      _ <- if (input != null && input.result != null) Consequence.unit
        else F.invalid("saved Human question has no actual business input")
      _ <- R.validateCapturedOptionC(input.planRevision)
      _ <- GoalPhaseRepairProgress.validateOptionC(input.repairProgress)
      _ <- R.validateOptionC(input.result.planRevision)
      _ <- GoalPhaseDependencyScope.admitResultC(input.work.kind, input.result.outcome, input.result.dependencyProposal)
      _ <- if (input.result.dependencyProposal == source.result.result.value.dependencyProposal &&
          (input.result.dependencyProposal.isEmpty || (input.planRevision.isEmpty && input.result.planRevision.isEmpty &&
            input.work == source.input && input.requestingResult == source.result.resultReference &&
            input.result == source.result.result.value && input.reason == source.controller.decision.get &&
            input.repairProgress == Some(GoalPhaseRepairProgress.snapshot(source.controller))))) Consequence.unit
        else F.invalid("Saved dependency question differs from its requesting semantic result")
      _ <- if (input.planRevision.map(_.proposal) == source.result.result.value.planRevision &&
          input.result.planRevision == source.result.result.value.planRevision &&
          (input.planRevision.isEmpty || (input.work == source.input && input.requestingResult == source.result.resultReference &&
            input.result == source.result.result.value && input.reason == source.controller.decision.get &&
            input.repairProgress == Some(GoalPhaseRepairProgress.snapshot(source.controller))))) Consequence.unit
        else F.invalid("saved Human question differs from its requesting semantic result")
      _ <- input.planRevision.fold(Consequence.unit) { captured => for {
        _ <- if (Set("ImplementStep", "RepairStep")(source.input.kind) && input.result.outcome == "decision-needed") Consequence.unit
          else F.invalid("Plan revision requires an ordinary implementation or repair decision")
        _ <- R.validateDocumentsC(source.controller, captured, source.documents)
        // Saved questions consume their own acceptance selection, never later host defaults/check policy.
        _ <- R.adoptedC(source.controller, captured, "saved-proposal-eligibility")
      } yield () }
    } yield input

    private def _answer_state_c(plan: Prepared, result: ContinuationResult[Answer]): Consequence[O.State] = for {
      _ <- _admit_answer_c(result.result.value)
      input <- NativeWorkflowOperationOutput.decisionInputC(plan.question)
      source = plan.source
      _ <- if (_choices(source.controller, input).contains(result.result.value.choice)) Consequence.unit
        else F.invalid("ordered Human choice is not admitted for this actual question")
      state <- result.result.value.choice match {
        case "AdoptPlanRevision" => input.planRevision.map(R.adoptedC(source.controller, _, result.resultReference.identity))
          .getOrElse(F.invalid("Plan revision adoption has no saved captured proposal"))
        case "RejectPlanRevision" | "ApproveDependencyScope" | "RejectDependencyScope" =>
          source.controller.acceptC(O.DecisionResumptionAdmitted(result.resultReference.identity))
        case "ResumeAssignedWork" | "ContinueRepairs" => resumedC(source, result)
        case _ => Consequence.success(source.controller)
      }
      event = result.result.value.choice match {
        case "AdoptPlanRevision" => "human-plan-revision-adopted"
        case "ResumeAssignedWork" | "ContinueRepairs" | "RejectPlanRevision" | "ApproveDependencyScope" | "RejectDependencyScope" => (if (source.controller.documentDecision) "human-document-resumed-"
          else "human-semantic-resumed-") + state.stage.toString
        case "Defer" => "human-semantic-deferred"
        case _ => "human-semantic-cancelled"
      }
      _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits("Decision", event, state.stage.toString))
        Consequence.unit else F.invalid("ordered Human successor is absent from CML")
    } yield state

    private def _completed_c(plan: Prepared, result: ContinuationResult[Answer]): Consequence[InstanceRecord] = for {
      _ <- _issued_answer_c(plan, result)
      state <- _answer_state_c(plan, result)
      next <- plan.pending.appendC(plan.pending.revision, F.history(plan.pending, "phase." + state.stage.toString,
        result.resultReference.identity))
    } yield next

    private def _issued_answer_c(plan: Prepared, result: ContinuationResult[Answer]): Consequence[Unit] = for {
      actual <- issuedChoicesC(plan.question)
      input <- NativeWorkflowOperationOutput.decisionInputC(plan.question)
      _ <- if (actual == _choices(plan.source.controller, input) && actual.contains(result.result.value.choice)) Consequence.unit
        else F.invalid("ordered Human answer differs from its actual issued question and controller")
    } yield ()
  }

  def requiredC: Consequence[StateMachineRequiredOperation] = {
    val matches = domain.statemachine.workflow.GoalPhaseWorkflowStateMachineWorkflow1.workflow.requiredOperations
      .filter(_.identity.capability == "goal-phase-ordered-human-capability")
    if (matches.size != 1)
      F.invalid("ordered Human Required capability is absent or ambiguous")
    else {
      val r = matches.head
      Consequence.success(StateMachineRequiredOperation(StateMachineRequiredOperationIdentity(r.identity.capability), r.actionIdentity,
        StateMachineOperationIdentity(r.operation.service, r.operation.operation), r.inputType.map(v => StateMachineInputTypeReference(v.value)),
        r.resultType.map(v => StateMachineResultTypeReference(v.value)), StateMachineRequiredOperationMetadata(
          ContextContract("goal-phase-ordered-human-context-v1", Vector.empty, Vector.empty),
          CompletionContract("goal-phase-ordered-human-completion-v1", Vector.empty),
          EvidenceContract("independent-ordered-human-answer-v1", Vector.empty), Vector.empty)))
    }
  }
  private def _completion_c(storage: WorkflowComponentStorage): Consequence[WorkflowManagedDecisionCompletion[Answer]] =
    storage.bindDecisionCompletionC(completionOperation, inputCodec, answerCodec, closingType, (_, _) => Consequence.success(Vector.empty))
  private def _admit_answer_c(answer: Answer): Consequence[Unit] =
    if (answer != null && choices.contains(answer.choice) && _text(answer.rationale)) Consequence.unit
    else F.invalid("ordered Human answer choice or rationale is invalid")
  private def _continues(choice: String): Boolean =
    Set("ResumeAssignedWork", "ContinueRepairs", "AdoptPlanRevision", "RejectPlanRevision", "ApproveDependencyScope", "RejectDependencyScope")(choice)
  private def _wait_c(next: InstanceRecord, result: ContinuationResult[Answer]) = F.waitC(next,
    "Ordered Human answer retained", result.result.value.choice match {
      case "ResumeAssignedWork" => "Direction retained; only owning advance may issue the same semantic work. No command or closure is authorized."
      case "ContinueRepairs" => "Continuation retained; owning advance may issue the first reserved batch of the renewed Step repair window."
      case "AdoptPlanRevision" => "Revision adoption retained; only owning Advance may issue implementation of the saved Plan."
      case "RejectPlanRevision" => "Revision rejection retained; only owning Advance may resume the original Plan with the explicit rejection context."
      case "ApproveDependencyScope" => "Exact dependency scope direction retained; owning Advance resumes the same semantic work. Effect, target and acceptance authorities still apply; review remains read-only."
      case "RejectDependencyScope" => "Dependency proposal rejected; owning Advance resumes the same semantic work with its explicit rejection and actual answer reference."
      case "Defer" => "Human deferred this decision. No automatic question, work, command or closure follows."
      case _ => "Human cancelled this decision path. Repository state and history remain; the Phase is not completed."
    })
  private def _text(value: String) = value != null && value.trim.nonEmpty && value.length <= 16384 && !value.contains('\u0000')
  private def _uncertain[A](reason: String): Consequence[A] =
    org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
}
