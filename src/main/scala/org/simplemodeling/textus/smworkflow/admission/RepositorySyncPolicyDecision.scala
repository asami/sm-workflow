/**
 * @since   Nov. 17, 2026
 * @version Nov. 17, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncManagedFetch as F
import RepositorySyncManagedGitObservation as G

/** Policy choices use the existing native Decision/completion and independent
  * Human evidence owners. They never carry check, review or Git authority.
  */
private[smworkflow] object RepositorySyncPolicyDecision {
  val purpose = "repository-sync-policy-human-answer"
  val completionOperation = StateMachineOperationIdentity("RepositorySyncService", "submitRepositorySyncPolicyDecision")
  val closingType = RepositorySyncHumanConflict.closingType
  val completion = "repository-policy-decision-completion-v1"
  val canonicalGap = "CanonicalMainSetupRequired: the original Start has no admitted canonical-main participant; no local-first effect or direct fallback is selected"
  final case class Input(sessionId: String, root: String, context: F.StartContext,
    policies: RepositorySyncPolicies.Selection, kind: String, before: G.Observed,
    indexTree: String, choices: Vector[String], limitation: String,
    localMain: Option[RepositorySyncLocalMain.Plan] = None)
  object Input {
    given Encoder[Input] = io.circe.generic.semiauto.deriveEncoder[Input].mapJson(_.mapObject(_.filter {
      case ("localMain", value) => !value.isNull
      case _ => true
    }))
    given Decoder[Input] = io.circe.generic.semiauto.deriveDecoder
  }
  final case class Answer(choice: String, rationale: String)
  private class Codec[A: Encoder: Decoder](val typeIdentity: String) extends WorkflowResultJsonV1.PayloadCodec[A] {
    def encode(value: A): Json = value.asJson
    def decode(value: Json): Either[String, A] = value.as[A].left.map(_.message)
  }
  val inputCodec: WorkflowResultJsonV1.PayloadCodec[Input] = new WorkflowResultJsonV1.PayloadCodec[Input] {
    val typeIdentity = "RepositorySyncPolicyDecisionRequest"
    def encode(value: Input): Json = Json.obj("target" -> Json.fromString(value.asJson.noSpaces))
    def decode(value: Json): Either[String, Input] = value.hcursor.get[String]("target").left.map(_.message)
      .flatMap(raw => io.circe.parser.parse(raw).left.map(_.message))
      .flatMap(_.as[Input].left.map(_.message))
      .flatMap(input => Either.cond(valid(input), input, "Policy question requires exact actual source and choices"))
  }
  val answerCodec: WorkflowResultJsonV1.PayloadCodec[Answer] = new Codec[Answer]("RepositorySyncPolicyDecisionAnswer")
  def source(input: Input): Json = Json.obj("policyDecision" -> input.asJson)
  def inputSource(value: Json): Either[String, Json] = for {
    input <- value.hcursor.get[Input]("policyDecision").left.map(_.message)
    _ <- Either.cond(valid(input), (), "Invalid actual RepositorySync policy question")
  } yield source(input)
  def valid(input: Input): Boolean = input != null && input.context != null &&
    input.sessionId != null && input.sessionId.nonEmpty && input.root != null && input.root.nonEmpty &&
    RepositorySyncPolicies.valid(input.policies) && input.before != null && input.before.branch == input.context.localBranch &&
    input.before.head != null && input.before.head.matches("[0-9a-f]{40}|[0-9a-f]{64}") &&
    input.before.tree != null && input.before.tree.matches("[0-9a-f]{40}|[0-9a-f]{64}") && input.before.dirty != null &&
    input.context.productIterator.forall(value => value.isInstanceOf[String] && value.asInstanceOf[String].nonEmpty) &&
    input.indexTree != null && input.indexTree.matches("[0-9a-f]{40}|[0-9a-f]{64}") &&
    ((input.kind == "route" && input.policies.mainIntegration == "confirm" &&
        input.choices == (if (input.localMain.nonEmpty) Vector("LocalFirst", "Direct", "Defer", "Cancel") else Vector("Direct", "Defer", "Cancel"))) ||
      (input.kind == "uncommitted" && input.policies.uncommittedPolicy == "confirm" && input.before.dirty.nonEmpty &&
        input.choices == Vector("AutoCommitPurposes", "Defer", "Cancel")))
  def admitC(input: Input, answer: Answer): Consequence[Unit] =
    if (valid(input) && answer != null && input.choices.contains(answer.choice) &&
        answer.rationale != null && answer.rationale.trim.nonEmpty && !answer.rationale.exists(_.isControl)) Consequence.unit
    else invalid("answer does not select an actual retained choice with a rationale")
  private def identity(session: String, kind: String) = ContinuationIdentity(session + ":sync-policy:" + kind)
  private def questionKind(kind: String, declaration: F.Declaration): String =
    if (kind == "route") kind else declaration.hop.filter(h => h.cursor.phase != "direct").fold(kind)(h => kind + "-" + h.cursor.phase)
  private def questionsC(session: F.Session) = session.storage.bindDecisionC(completionOperation, inputCodec)
  private def completionC(session: F.Session) = session.storage.bindDecisionCompletionC(completionOperation,
    inputCodec, answerCodec, closingType, (_, _) => Consequence.success(Vector.empty))

  private def sharedRouteC(session: F.Session, declaration: F.Declaration, core: ActionCall.Core): Consequence[Option[String]] =
    declaration.localMain.flatMap(_.cohort).filter(_.routeOwner != session.started.current.identity.value) match {
      case None => Consequence.success(None)
      case Some(cohort) => for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(cohort.routeOwner))
        questions <- questionsC(session)
        completions <- completionC(session)
        id = identity(cohort.routeOwner, "route")
        question <- questions.loadC(id)(using context)
        saved <- completions.loadRetainedC(id)(using context)
        selected <- (question, saved) match {
          case (Some(actual), Some(completed)) => for {
            input <- actual.current match {
              case decision: WorkflowContinuation.Decision[Input] => decision.request.input.map(v => Consequence.success(v.value))
                .getOrElse(invalid("shared route question has no original input"))
              case _ => invalid("shared route is not a native Decision")
            }
            answer = completed.completion.result.result.value
            _ <- admitC(input, answer)
            _ <- if (input.sessionId == cohort.routeOwner && input.kind == "route" &&
                input.context.participantIdentity == session.context.participantIdentity && input.policies == declaration.policies.get &&
                input.localMain.exists(p => p.cohort.contains(cohort) && declaration.localMain.exists(current =>
                  RepositorySyncLocalMain.sameMain(current.main, p.main) && cohort.participants.contains(current.source)))) Consequence.unit
              else invalid("shared route differs from its original participant cohort or actual Human scope")
          } yield Some(answer.choice)
          case _ => Consequence.success(Some("AwaitingOwner"))
        }
      } yield selected
    }

  /** Only the pre-fetch frontier selects a new policy question. Other owners'
    * pending work and all accepted effects continue through their original path.
    */
  def gateC(session: F.Session, declaration: F.Declaration, core: ActionCall.Core):
      Consequence[Option[V.RepositorySyncAdvanceResult]] = {
    val original = session.started.receipt.payload.value
    val current = session.started.current
    original.policies match {
      case None => Consequence.success(None)
      case Some(policies) if current.lifecycle == Lifecycle.Completed ||
          !current.currentProgression.contains(ProgressionReference("sync.BoundaryAssessment")) => Consequence.success(None)
      case Some(policies) => for {
        root <- G.rootC(core)
        _ <- if (root == declaration.target.root.toString && (root == original.repositoryRoot ||
            declaration.hop.exists(h => root == h.plan.main.root))) Consequence.unit
          else invalid("policy selection crosses the original source root")
        observed <- G.observeC(if (declaration.hop.exists(_.cursor.phase == "local")) RepositorySyncLocalMain.withoutHop(core) else core)
        route = RepositorySyncPolicies.route(policies, session.context.localBranch, session.context.trackingBranch)
        shared <- if (route == "confirm") sharedRouteC(session, declaration, core) else Consequence.success(None)
        selected <- if (shared.contains("LocalFirst")) Consequence.success(None)
          else if (shared.contains("Direct") && declaration.hop.exists(_.cursor.phase != "direct")) for {
            _ <- RepositorySyncLocalMain.phaseC(session.storage, current, RepositorySyncLocalMain.Cursor("direct", None, None), declaration, core)
          } yield Some(NativeWorkflowOperationOutput.repositoryWait(current.identity.value,
            "The original batch Human Direct choice applies to this declared participant; ordinary continuation retains its source target", "RouteSelected").copy(kind = "Progress"))
          else if (shared.contains("Direct")) Consequence.success(None)
          else if (shared.nonEmpty) Consequence.success(Some(NativeWorkflowOperationOutput.repositoryWait(current.identity.value,
            "The original batch route remains " + shared.get + "; no second question or automatic route choice", "SharedRouteAwaitingOwner")))
          else if (route == "local-first" && original.localMain.isEmpty) Consequence.success(Some(NativeWorkflowOperationOutput.repositoryWait(
          current.identity.value, original.localMainSetup.getOrElse(canonicalGap), "CanonicalMainSetupRequired")))
          else if (route == "confirm") decideC(session, declaration, core, policies, observed, "route")
          else Consequence.success(None)
        dirty <- selected match {
          case Some(value) => Consequence.success(Some(value))
          case None if observed.dirty.isEmpty || policies.uncommittedPolicy == "auto-commit" => Consequence.success(None)
          case None if policies.uncommittedPolicy == "error" => Consequence.success(Some(
            NativeWorkflowOperationOutput.repositoryWait(current.identity.value,
              "UncommittedChanges: uncommittedPolicy=error preserves " + RepositorySyncDirtyInventory.fromStatus(observed.dirty).description,
              "UncommittedChanges").copy(kind = "Error", outcome = Some("error"))))
          case None => decideC(session, declaration, core, policies, observed, "uncommitted")
        }
      } yield dirty
    }
  }

  private def decideC(session: F.Session, declaration: F.Declaration, core: ActionCall.Core,
    policies: RepositorySyncPolicies.Selection, observed: G.Observed, kind: String): Consequence[Option[V.RepositorySyncAdvanceResult]] = for {
    questions <- questionsC(session)
    native <- completionC(session)
    id = identity(session.started.current.identity.value, questionKind(kind, declaration))
    existing <- questions.loadC(id)(using core.executionContext)
    retained <- native.loadRetainedC(id)(using core.executionContext)
    output <- retained match {
      case Some(saved) => for {
        question <- existing.map(Consequence.success).getOrElse(invalid("completed policy choice has no actual question"))
        input = question.current.asInstanceOf[WorkflowContinuation.Decision[Input]].request.input.get.value
        answer = saved.completion.result.result.value
        _ <- admitC(input, answer)
        _ <- if (input.policies == policies && input.context == session.context) Consequence.unit
          else invalid("retained policy choice crosses this Session's captured selection")
        result <- if (Set("Defer", "Cancel")(answer.choice)) Consequence.success(Some(
          NativeWorkflowOperationOutput.repositoryWait(input.sessionId, "Human selected " + answer.choice + ": " + answer.rationale, answer.choice)))
          else for {
            index <- G.indexTreeC(core)
            _ <- if (observed == input.before && index == input.indexTree) Consequence.unit
              else invalid("source changed since the retained policy question; original Human scope is preserved")
            result <- if (kind == "route" && answer.choice == "Direct" && declaration.hop.exists(_.cursor.phase != "direct")) for {
              _ <- RepositorySyncLocalMain.phaseC(session.storage, session.started.current,
                RepositorySyncLocalMain.Cursor("direct", None, None), declaration, core)
            } yield Some(NativeWorkflowOperationOutput.repositoryWait(input.sessionId,
              "Retained Human direct route selected; ordinary continuation uses the original source checkout", "RouteSelected").copy(kind = "Progress"))
              else Consequence.success(None)
          } yield result
      } yield result
      case None => for {
        question <- existing match {
          case Some(value) => Consequence.success(value)
          case None => issueC(session, declaration, core, policies, observed, kind)
        }
        rendered <- renderC(question, core)
      } yield Some(rendered)
    }
  } yield output

  private def issueC(session: F.Session, declaration: F.Declaration, core: ActionCall.Core,
    policies: RepositorySyncPolicies.Selection, observed: G.Observed, kind: String): Consequence[WorkflowInteraction[Input, Nothing]] = for {
    _ <- declaration.policyHuman.map(_ => Consequence.unit).getOrElse(invalid("independent policy Human evidence owner is not installed"))
    from = session.started.current
    _ <- if (from.suspension.isEmpty) Consequence.unit else invalid("another actual question owns the Session")
    index <- G.indexTreeC(core)
    input = Input(from.identity.value, declaration.target.root.toString, session.context, policies,
      kind, observed, index, if (kind == "route" && declaration.localMain.nonEmpty) Vector("LocalFirst", "Direct", "Defer", "Cancel")
        else if (kind == "route") Vector("Direct", "Defer", "Cancel")
        else Vector("AutoCommitPurposes", "Defer", "Cancel"), if (kind == "route") declaration.localMain.fold(
          session.started.receipt.payload.value.localMainSetup.getOrElse(canonicalGap))(plan =>
          "LocalFirst integrates " + plan.source.root + " into " + plan.main.root + " before " + plan.main.endpoint +
            "; Direct synchronizes only the declared source participants with " + plan.source.endpoint +
            plan.cohort.fold("")(cohort => "; declared batch sources: " + cohort.participants.map(_.root).mkString(", "))) else
          "Authorize purpose-based intended changes only; semantic grouping, excluded work, checks and independent review retain their owners", declaration.localMain)
    _ <- if (valid(input)) Consequence.unit else invalid("policy question has no exact current source")
    required <- RepositorySyncDefinitionBinding.policyDecisionOperationC
    bundle = ContextBundle("Human selection before RepositorySync mutation", Vector.empty, Vector.empty,
      ContextSnapshot(from.definition.workflowRevision.value, evidenceRevision = Some(from.revision.value.toString)))
    id = identity(from.identity.value, questionKind(kind, declaration))
    continuation = Continuation(StateMachineRunIdentity(from.identity.value), id,
      StateMachineRevision(from.revision.next.value.toString), required, bundle)
    entry = HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next, Lifecycle.Active,
      from.currentProgression, from.correlation, CausationReference(id.value + ":issued"), from.derivedCompositeOccurrence,
      from.committedPredecessor, Some(SuspensionBoundary(NativeIdentity(id.value), from.revision.next,
        ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(completion),
        EvidenceReference(required.metadata.evidenceContract.identity), from.correlation)))
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, from)
    question = WorkflowInteraction[Input, Nothing](handle, WorkflowContinuation.Decision(
      ContinuationRequest(continuation.runId, id, continuation.expectedRevision, required.identity, required.operation,
        Some(TypedValue(inputCodec.typeIdentity, input)), required.resultType, bundle,
        required.metadata.completionContract, required.metadata.evidenceContract, completionOperation),
      MinimalPresentation("RepositorySync " + kind + " policy needs Human direction", input.asJson.noSpaces)))
    questions <- questionsC(session)
    saved <- questions.suspendC(WorkflowInstanceAtomicTransitionV1.SuspensionIntent(
      WorkflowInstanceAtomicTransitionV1.schemaVersion, declaration.configuration, from, from.revision, entry,
      ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available)), question)(using core.executionContext)
  } yield question

  def renderC(question: WorkflowInteraction[Input, Nothing], core: ActionCall.Core): Consequence[V.RepositorySyncAdvanceResult] = for {
    input <- question.current match {
      case decision: WorkflowContinuation.Decision[Input] => decision.request.input.map(v => Consequence.success(v.value))
        .getOrElse(invalid("policy question has no input"))
      case _ => invalid("policy question is not an actual Decision")
    }
    context <- NativeHumanDecisionContext.repositoryPolicyC(core, question)
  } yield NativeWorkflowOperationOutput.repositoryWait(input.sessionId, input.limitation, "PolicyDecisionRequired").copy(
    kind = "Human", submitOperation = Some(completionOperation.operation), submitSessionId = Some(input.sessionId),
    resultType = Some(answerCodec.typeIdentity), human = Some(V.RepositorySyncHumanQuestion(None,
      input.limitation, input.choices, Some(context), Some(GoalPhaseNativeMetadata.record(input.asJson)))))

  /** Reuse ordinary atomic decision completion and independently supplied host
    * evidence. Submit never fetches, commits, merges, pushes or calls Advance.
    */
  def bindCompletionC(params: ComponentCreate, declaration: F.Declaration, definition: DefinitionBinding):
      Consequence[spec.OperationDefinition] = for {
    provided <- RepositorySyncDefinitionBinding.providedDefinition.providedOperations.find(o =>
      o.service == completionOperation.service && o.name == completionOperation.operation)
      .map(Consequence.success).getOrElse(invalid("policy completion is not declared"))
  } yield new NativeWorkflowResultInput.Receiver {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositorySyncPolicyDecisionOperation)
    def createResultOperationRequestC(incoming: Request, raw: ContinuationResult[NativeWorkflowResultInput.Value]): Consequence[OperationRequest] =
      NativeWorkflowResultInput.typedC(raw, answerCodec).map { submitted => new CommandAction {
        val request = incoming
        override def commandExecutionMode = CommandExecutionMode.Sync
        def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
          val core = c
          private var delegate = Option.empty[ActionCall]
          def execute(): Consequence[OperationResponse] = for {
            session <- F.sessionC(submitted.handle.instanceIdentity.value, declaration, definition, core)
            questions <- questionsC(session)
            native <- completionC(session)
            question <- questions.loadC(submitted.continuationId)(using core.executionContext)
              .flatMap(_.map(Consequence.success).getOrElse(invalid("policy answer has no actual retained question")))
            input = question.current.asInstanceOf[WorkflowContinuation.Decision[Input]].request.input.get.value
            _ <- admitC(input, submitted.result.value)
            _ <- if (input.sessionId == session.started.current.identity.value && input.context == session.context &&
                session.started.receipt.payload.value.policies.contains(input.policies)) Consequence.unit
              else invalid("answer crosses the original policy selection or Session")
            _ <- GoalPhaseParticipantAdmission.admitC(session.context.participantIdentity, core.executionContext)
            evidence <- declaration.policyHuman.map(Consequence.success).getOrElse(invalid("independent policy Human evidence is unavailable"))
            binding <- NativeHumanDecisionScope.bindingC(core.component.get.core.instanceId.canonicalKey,
              NativeExternalEvidence.Purpose.RepositoryPolicyHuman, question, submitted, session.context.participantIdentity,
              source(input), Json.obj("choice" -> submitted.result.value.choice.asJson))
            _ <- evidence.admitC(core, submitted.resultReference, binding)
            current = session.started.current
            next <- current.appendC(current.revision, HistoryEntry(HistorySequence(current.history.size.toLong + 1),
              current.revision.next, Lifecycle.Active, current.currentProgression, current.correlation,
              CausationReference(submitted.resultReference.identity), current.derivedCompositeOccurrence, current.committedPredecessor, None))
            instances <- session.storage.bindInstancesC()(using core.executionContext)
            codec = new WorkflowCompletionServiceOperation.Codec[Answer] {
              val resultTypeIdentity = answerCodec.typeIdentity
              def decodeC(request: Request) = Consequence.success(submitted)
              def encodeC(resume: ContinuationRuntime.Resume) = invalid("Human acceptance requires committed readback")
              override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, result: ContinuationResult[Answer], completed: Option[InstanceRecord]) =
                completed.map(value => Consequence.success(NativeWorkflowOperationOutput.response(
                  NativeWorkflowOperationOutput.accepted(result, value)))).getOrElse(invalid("Human completion has no committed instance"))
            }
            closing = new WorkflowCompletionServiceOperation.ClosingProgram[Answer] {
              def selectC(result: ContinuationResult[Answer], current: InstanceRecord) = Consequence.success(
                WorkflowCompletionServiceOperation.SelectedClosing(ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
                  ActionExecution.Completed(StateMachineOperationResult(closingType, result.resultReference))), Some(next)))
            }
            bound <- WorkflowDecisionCompletionServiceOperation.bindC(RepositorySyncDefinitionBinding.providedDefinition, provided,
              SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositorySyncPolicyDecisionOperation.specification.request,
              SmWorkflowComponent.RepositorySyncServiceService.SubmitRepositorySyncPolicyDecisionOperation.specification.response,
              codec, questions, instances, closing, native)
            action <- bound.createOperationRequest(core.action.request).flatMap {
              case command: CommandAction => Consequence.success(command)
              case _ => invalid("policy completion is not a CommandAction")
            }
            call = action.createCall(core.copy(action = action))
            result <- { delegate = Some(call); call.execute() }
          } yield result
          override def afterCommitResponseC(response: OperationResponse): Consequence[OperationResponse] = {
            val selected = delegate; delegate = None
            selected.fold(Consequence.success(response))(_.afterCommitResponseC(response))
          }
        }
      } }
  }
  private def invalid[A](message: String): Consequence[A] = Consequence.stateConflict("RepositorySync policy decision " + message)
}
