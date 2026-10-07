package org.simplemodeling.textus.smworkflow.admission

import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as NativeIdentity, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.protocol.{Argument, Request as ProtocolRequest}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import GoalPhaseNativeRun as F
import GoalPhaseNativeWork as S
import GoalPhaseOrderedProgression as O

/** Ordered implementation, repair and review work and their provided result are installed
  * together. Canonical atomic suspension/completion owns all writes. No result
  * runs Git/SBT or turns implementation completion into Step/Phase acceptance.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object GoalPhaseNativeWorkContinuation {
  val closingType = StateMachineResultTypeReference("GoalPhaseWorkSubmissionResult")
  private final case class Prepared(source: F.Session, input: S.Request,
    intent: WorkflowInstanceAtomicTransitionV1.SuspensionIntent, pending: InstanceRecord,
    work: WorkflowInteraction[S.Request, Nothing], controller: O.State,
    candidate: Option[GoalPhaseNativeCandidate.Saved])
  private final case class ReviewSource(source: F.Session, candidate: GoalPhaseNativeCandidate.Saved,
    validation: GoalPhaseNativeValidation.Saved, input: S.Request, controller: O.State)

  final class Port private[GoalPhaseNativeWorkContinuation](d: F.Declaration, definition: DefinitionBinding,
    acknowledgement: String => Consequence[Unit]) {
    def advanceC(identity: String, core: ActionCall.Core): Consequence[String] = for {
      source <- F.sessionC(identity, d, definition, core)
      plan <- prepareC(source)
      issued <- source.storage.bindIssuedC(F.completionOperation, S.inputCodec)
      existing <- issued.loadC(continuationId(source))(using core.executionContext)
      _ <- existing match {
        case Some(value) if value == plan.work && F.prefix(source.current, plan.pending) => Consequence.unit
        case Some(_) => F.invalid("retained ordered issue crosses its original native source")
        case None => for {
          _ <- if (F.same(source.current, source.started.receipt.next)) Consequence.unit
            else F.invalid("advanced ordered instance has no actual issued implementation")
          saved <- issued.suspendC(plan.intent, plan.work)(using core.executionContext)
          _ <- if (F.same(saved.instance, plan.pending)) Consequence.unit
            else uncertain("ordered issue committed readback unavailable; observe without republishing")
          actual <- issued.loadC(continuationId(source))(using core.executionContext)
          _ <- if (actual.contains(plan.work)) Consequence.unit
            else uncertain("ordered work readback unavailable; observe without republishing")
        } yield ()
      }
      wire <- if (F.same(source.current, source.started.receipt.next) || F.same(source.current, plan.pending))
        WorkflowWorkOrderJsonV1.encodeC(plan.work, S.inputCodec)
      else for {
        completion <- nativeC(source.storage)
        retained <- completion.loadRetainedC(continuationId(source))(using core.executionContext).flatMap {
          case Some(value) => Consequence.success(value.completion)
          case None => F.invalid("ordered implementation has no actual native completion")
        }
        state <- completedStateC(plan, retained.result)
        next <- completedC(plan, retained.result)
        _ <- if (F.prefix(source.current, next) && retained.instanceRevision == next.revision &&
            retained.closingResult.contains(StateMachineOperationResult(closingType, retained.result.resultReference)))
          Consequence.unit else F.invalid("ordered completion differs from its native closing successor")
        result <- continueAfterSemanticC(source, state, next, retained.result, plan.input, core)
      } yield result
    } yield wire

    private def prepareC(source: F.Session): Consequence[Prepared] = for {
      input <- S.initialC(source)
      controller <- O.startC(input.selectedPlan, input.originalBase)
      _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits("Idle", "ordered-plan-selected", "Implementation"))
        Consequence.unit else F.invalid("initial plan event is absent from CML")
      prepared <- prepareAssignedC(source, input, source.started.receipt.next, controller)
    } yield prepared

    private def prepareAssignedC(source: F.Session, input: S.Request, from: InstanceRecord,
      controller: O.State, candidate: Option[GoalPhaseNativeCandidate.Saved] = None): Consequence[Prepared] = for {
      required <- S.requiredC(input.kind)
      independent = isReview(input)
      bundle = ContextBundle(if (independent) "Independently review the actual candidate and selected native check outcomes"
        else if (input.kind == "RepairStep") "Repair only the assigned Step findings from the retained independent review"
        else "Implement only the current Step of the complete selected ordered Phase plan",
        Vector(ContextReference(input.selectedPlan.identity, input.selectedPlan.revision)), Vector.empty,
        ContextSnapshot(from.definition.workflowRevision.value, evidenceRevision = Some(input.sourceRevision)))
      continuation = Continuation(StateMachineRunIdentity(from.identity.value), assignedId(source, input),
        StateMachineRevision(from.revision.next.value.toString), required, bundle)
      entry = HistoryEntry(HistorySequence(from.history.size.toLong + 1), from.revision.next, Lifecycle.Active,
        from.currentProgression, from.correlation, CausationReference(
          (if (input.kind == "ImplementStep") "ordered-implementation-issued:"
            else if (input.kind == "RepairStep") "ordered-repair-issued:" else "ordered-review-issued:") + from.identity.value),
        from.derivedCompositeOccurrence, from.committedPredecessor,
        Some(SuspensionBoundary(NativeIdentity(continuation.continuationId.value), from.revision.next,
          ContextSnapshotReference(bundle.snapshot.workflowRevision), CompletionReference(required.metadata.completionContract.identity),
          EvidenceReference(required.metadata.evidenceContract.identity), from.correlation)))
      intent = WorkflowInstanceAtomicTransitionV1.SuspensionIntent(WorkflowInstanceAtomicTransitionV1.schemaVersion,
        d.configuration, from, from.revision, entry,
        ContinuationRuntimePersistence.Record(continuation, ContinuationRuntimePersistence.Status.Available))
      admitted <- WorkflowInstanceAtomicTransitionV1.admitSuspensionC(intent)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, from)
      work = WorkflowInteraction[S.Request, Nothing](handle, WorkflowContinuation.WorkOrder(
        ContinuationRequest(continuation.runId, continuation.continuationId, continuation.expectedRevision,
          required.identity, required.operation, Some(TypedValue(S.inputCodec.typeIdentity, input)), required.resultType,
          bundle, required.metadata.completionContract, required.metadata.evidenceContract, F.completionOperation),
        ExecutionRequirement(Vector(CapabilityRequirement(required.identity.capability)), RiskLevel("SOURCE_IMPLEMENTATION"),
          ReasoningLevel.Standard, independent), MinimalPresentation(if (input.kind == "ReviewPhase") "Independent whole-Phase review"
            else if (independent) "Independent selected Step review"
            else if (input.kind == "RepairStep") "Repair selected Phase Step" else "Implement selected Phase Step",
          "Return only the assigned semantic result. Workflow owns commands, validation, Step and Phase closure.")))
    } yield Prepared(source, input, intent, admitted.next, work, controller, candidate)

    private def reviewSourceC(source: F.Session, candidate: GoalPhaseNativeCandidate.Saved,
      validation: GoalPhaseNativeValidation.Saved, inspected: O.State): Consequence[ReviewSource] = for {
      installation <- d.reviewInstallation.map(Consequence.success).getOrElse(F.invalid("native review installation is missing"))
      _ <- installation.validateC
      whole = inspected.cursor == inspected.plan.steps.size
      _ <- if (validation.payload.outcome.contains("passed") &&
          validation.record.currentProgression.contains(ProgressionReference(if (whole) "phase.PhaseReview" else "phase.Review"))) Consequence.unit
        else F.invalid("review requires actual passed checks, never failed or unknown results")
      observed <- inspected.acceptC(O.CandidateObserved(validation.payload.candidate))
      controller <- observed.acceptC(O.ChecksObserved(validation.payload.candidate,
        if (whole) inspected.plan.phaseChecks else inspected.plan.steps(inspected.cursor).checks,
        validation.record.identity.value + ":ordered-validation:" +
          candidate.record.revision.value + ":validation", true))
      semantic <- semanticC(controller, if (whole) "ReviewPhase" else "ReviewStep")
      input = request(source, validation.record, semantic,
        Some(GoalPhaseNativeReview.Context(installation.policy.identity, installation.policy.revision,
          candidate.payload.changedPaths, candidate.payload.diff.get, validation.payload.receipts)))
    } yield ReviewSource(source, candidate, validation, input, controller)

    private def continueAfterSemanticC(source: F.Session, controller: O.State, from: InstanceRecord,
      submitted: ContinuationResult[S.Result], input: S.Request, core: ActionCall.Core): Consequence[String] =
      if (controller.stage == O.Stage.InspectCandidate && d.candidatePaths.nonEmpty) for {
        inspected <- GoalPhaseNativeCandidate.advanceC(source, d, core, from, controller, acknowledgement)
        current <- F.sessionC(source.current.identity.value, d, definition, core)
        observation <- WorkflowWaitJsonV1.decodeC(inspected)
        unfinished = current.current.currentProgression.contains(ProgressionReference("phase.InspectCandidate")) &&
          observation.current.asInstanceOf[WorkflowContinuation.Wait].expectedRevision.value == current.current.revision.value.toString
        wire <- if (unfinished ||
            d.validationPolicy.isEmpty) Consequence.success(inspected)
          else continueReviewC(current, controller, from, core, inspected)
      } yield wire
      else if (controller.stage == O.Stage.Decision && d.humanDecision.nonEmpty)
        continueDecisionC(source, controller, from, submitted, input, None, core)
      else waitC(from, submitted)

    private def continueReviewC(source: F.Session, controller: O.State, implementation: InstanceRecord,
      core: ActionCall.Core, fallback: String): Consequence[String] = for {
      candidate <- GoalPhaseNativeCandidate.retainedC(source, d, core, implementation, controller)
      validation <- GoalPhaseNativeValidation.retainedC(source, d, core, candidate, controller)
      wire <- validation match {
        case Some(value) if value.payload.outcome.contains("passed") => d.reviewInstallation match {
          case Some(_) => for {
            selected <- reviewSourceC(source, candidate, value, controller)
            plan <- prepareAssignedC(source, selected.input, value.record, selected.controller, Some(candidate))
            _ <- issueAssignedC(plan, value.record, core)
            response <- if (F.same(source.current, value.record) || F.same(source.current, plan.pending))
              WorkflowWorkOrderJsonV1.encodeC(plan.work, S.inputCodec)
            else for {
              retained <- retainedCompletionC(plan, core)
              (state, next, result) = retained
              response <- if (state.stage == O.Stage.Repair) continueRepairC(source, state, next, plan.input.review, core)
                else if (state.stage == O.Stage.Closing && d.closureInstallation.nonEmpty)
                  continueClosureC(source, state, next, core)
                else if (state.stage == O.Stage.PhaseClosing && d.repositoryClosure.nonEmpty)
                  GoalPhaseNativeRepositoryClosure.advanceC(source, d, core, next, state, acknowledgement)
                else if (state.stage == O.Stage.Decision && d.humanDecision.nonEmpty)
                  continueDecisionC(source, state, next, result, plan.input, plan.candidate, core)
                else waitC(next, result)
            } yield response
          } yield response
          case None => if (F.same(source.current, value.record)) Consequence.success(fallback)
            else F.invalid("advanced review has lost its actual independent host installation")
        }
        case _ => Consequence.success(fallback)
      }
    } yield wire

    private def semanticC(controller: O.State, kind: String): Consequence[O.Semantic] = controller.next match {
      case value: O.Semantic if value.key.kind.toString == kind => Consequence.success(value)
      case _ => F.invalid("native controller has no assigned " + kind + " frontier")
    }
    private def request(source: F.Session, from: InstanceRecord, semantic: O.Semantic,
      context: Option[GoalPhaseNativeReview.Context]): S.Request =
      S.Request(source.current.identity.value, from.revision.value.toString, semantic.key.kind.toString,
        semantic.key.step, semantic.key.generation, semantic.purpose, semantic.selectedPlan,
        semantic.originalBase, semantic.completed, semantic.candidate, semantic.checksReceipt,
        semantic.repairDirective, context)

    private def prepareRepairC(source: F.Session, controller: O.State, from: InstanceRecord,
      context: Option[GoalPhaseNativeReview.Context]): Consequence[Prepared] = for {
      semantic <- semanticC(controller, "RepairStep")
      _ <- if (from.currentProgression.contains(ProgressionReference("phase.Repair")) && context.nonEmpty &&
          semantic.repairDirective.nonEmpty && semantic.candidate.isEmpty && semantic.checksReceipt.isEmpty)
        Consequence.unit else F.invalid("repair has no actual independent review directive or native successor")
      prepared <- prepareAssignedC(source, request(source, from, semantic, context), from, controller)
    } yield prepared

    /** Suspension is paired with its actual issued work. Only a new Review
      * checks the current candidate; historical rounds are reconstructed without
      * requiring the old tree to remain live after an authorized repair.
      */
    private def issueAssignedC(plan: Prepared, from: InstanceRecord, core: ActionCall.Core): Consequence[Unit] = for {
      issued <- plan.source.storage.bindIssuedC(F.completionOperation, S.inputCodec)
      existing <- issued.loadC(assignedId(plan.source, plan.input))(using core.executionContext)
      _ <- existing match {
        case Some(work) if work == plan.work && F.prefix(plan.source.current, plan.pending) => Consequence.unit
        case Some(_) => F.invalid("retained work crosses its actual candidate/checks/repair history")
        case None => for {
          _ <- if (F.same(plan.source.current, from)) Consequence.unit else F.invalid("advanced work has no actual issue")
          _ <- plan.candidate.map(GoalPhaseNativeValidation.preconditionsC(plan.source, d, core, _, plan.controller)).getOrElse(Consequence.unit)
          saved <- issued.suspendC(plan.intent, plan.work)(using core.executionContext)
          actual <- issued.loadC(assignedId(plan.source, plan.input))(using core.executionContext)
          _ <- if (F.same(saved.instance, plan.pending) && actual.contains(plan.work)) Consequence.unit
            else uncertain("ordered issue committed readback unavailable; observe without republishing")
        } yield ()
      }
    } yield ()

    private def retainedCompletionC(plan: Prepared, core: ActionCall.Core):
        Consequence[(O.State, InstanceRecord, ContinuationResult[S.Result])] = for {
      native <- nativeC(plan.source.storage)
      retained <- native.loadRetainedC(assignedId(plan.source, plan.input))(using core.executionContext).flatMap {
        case Some(value) => Consequence.success(value.completion)
        case None => F.invalid("ordered work has no actual retained native completion")
      }
      controller <- completedStateC(plan, retained.result)
      next <- completedC(plan, retained.result)
      _ <- if (retained.instanceRevision == next.revision && F.prefix(plan.source.current, next) &&
          retained.closingResult.contains(StateMachineOperationResult(closingType, retained.result.resultReference)))
        Consequence.unit else F.invalid("ordered completion differs from its actual closing successor")
    } yield (controller, next, retained.result)

    private def continueRepairC(source: F.Session, controller: O.State, from: InstanceRecord,
      context: Option[GoalPhaseNativeReview.Context], core: ActionCall.Core): Consequence[String] = for {
      plan <- prepareRepairC(source, controller, from, context)
      _ <- issueAssignedC(plan, from, core)
      wire <- if (F.same(source.current, from) || F.same(source.current, plan.pending))
        WorkflowWorkOrderJsonV1.encodeC(plan.work, S.inputCodec)
      else for {
        retained <- retainedCompletionC(plan, core)
        (state, next, result) = retained
        response <- continueAfterSemanticC(source, state, next, result, plan.input, core)
      } yield response
    } yield wire

    private def prepareNextImplementationC(source: F.Session, controller: O.State,
      from: InstanceRecord): Consequence[Prepared] = for {
      semantic <- semanticC(controller, "ImplementStep")
      _ <- if (from.currentProgression.contains(ProgressionReference("phase.Implementation")) &&
          controller.completed.nonEmpty) Consequence.unit else F.invalid("next Step has no actual committed predecessor")
      plan <- prepareAssignedC(source, request(source, from, semantic, None), from, controller)
    } yield plan

    private def continueClosureC(source: F.Session, controller: O.State, from: InstanceRecord,
      core: ActionCall.Core): Consequence[String] = for {
      closed <- GoalPhaseNativeClosure.advanceC(source, d, core, from, controller, acknowledgement)
      current <- F.sessionC(source.current.identity.value, d, definition, core)
      wire <- closed match {
        case None => F.waitC(current.current, "Ordered Step commit remains open",
          "Actual Step commit authority is unavailable or the prepared commit has no recorded result. No automatic replay; the current Step and Phase remain open.")
        case Some(value) if value.controller.stage == O.Stage.Implementation => for {
          plan <- prepareNextImplementationC(current, value.controller, value.record)
          _ <- issueAssignedC(plan, value.record, core)
          response <- if (F.same(current.current, value.record) || F.same(current.current, plan.pending))
            WorkflowWorkOrderJsonV1.encodeC(plan.work, S.inputCodec)
          else for {
            retained <- retainedCompletionC(plan, core)
            (state, next, result) = retained
            wire <- continueAfterSemanticC(current, state, next, result, plan.input, core)
          } yield wire
        } yield response
        case Some(value) if value.controller.stage == O.Stage.PhaseInspection && d.phaseValidationPolicy.nonEmpty =>
          for {
            inspected <- GoalPhaseNativeCandidate.advanceC(current, d, core, value.record, value.controller, acknowledgement)
            refreshed <- F.sessionC(source.current.identity.value, d, definition, core)
            response <- continueReviewC(refreshed, value.controller, value.record, core, inspected)
          } yield response
        case Some(value) => if (!F.same(current.current, value.record))
          F.invalid("advanced whole-Phase frontier has lost its actual host validation policy")
          else F.waitC(value.record, "All selected Steps committed",
            "The selected ordered Step commits are retained. Whole-Phase inspection, validation, independent review and repository closure remain unfinished; the Phase is not closed.")
      }
    } yield wire

    private def humanPortC: Consequence[GoalPhaseOrderedHumanDecision.Port] =
      d.humanDecision.map(installation => Consequence.success(new GoalPhaseOrderedHumanDecision.Port(
        d, definition, installation, locateDecisionC, resumeDecisionC, acknowledgement)))
        .getOrElse(F.invalid("ordered Human handling is not installed"))

    def bindHumanCompletionC(params: ComponentCreate): Consequence[spec.OperationDefinition] =
      humanPortC.flatMap(_.bindCompletionC(params))

    private def continueDecisionC(source: F.Session, controller: O.State, from: InstanceRecord,
      result: ContinuationResult[S.Result], input: S.Request, candidate: Option[GoalPhaseNativeCandidate.Saved],
      core: ActionCall.Core): Consequence[String] = humanPortC.flatMap(_.advanceC(
        GoalPhaseOrderedHumanDecision.Source(source, input, controller, from, result, candidate), core))

    private def prepareResumedC(source: GoalPhaseOrderedHumanDecision.Source, state: O.State,
      from: InstanceRecord, answer: GoalPhaseOrderedHumanDecision.Answer): Consequence[Prepared] = for {
      semantic <- semanticC(state, source.input.kind)
      input = request(source.session, from, semantic, source.input.review).copy(decisionContext = Some(
        S.DecisionContext(source.result.result.value, answer.choice, answer.rationale)))
      plan <- prepareAssignedC(source.session, input, from, state, source.candidate)
    } yield plan

    private def resumeDecisionC(source: GoalPhaseOrderedHumanDecision.Source, state: O.State,
      from: InstanceRecord, answer: GoalPhaseOrderedHumanDecision.Answer, core: ActionCall.Core): Consequence[String] = for {
      plan <- prepareResumedC(source, state, from, answer)
      _ <- issueAssignedC(plan, from, core)
      wire <- if (F.same(source.session.current, from) || F.same(source.session.current, plan.pending))
        WorkflowWorkOrderJsonV1.encodeC(plan.work, S.inputCodec)
      else for {
        retained <- retainedCompletionC(plan, core)
        (nextState, next, result) = retained
        wire <- if (isReview(plan.input)) {
          if (nextState.stage == O.Stage.Repair) continueRepairC(source.session, nextState, next, plan.input.review, core)
          else if (nextState.stage == O.Stage.Closing && d.closureInstallation.nonEmpty)
            continueClosureC(source.session, nextState, next, core)
          else if (nextState.stage == O.Stage.PhaseClosing && d.repositoryClosure.nonEmpty)
            GoalPhaseNativeRepositoryClosure.advanceC(source.session, d, core, next, nextState, acknowledgement)
          else if (nextState.stage == O.Stage.Decision)
            continueDecisionC(source.session, nextState, next, result, plan.input, plan.candidate, core)
          else waitC(next, result)
        } else continueAfterSemanticC(source.session, nextState, next, result, plan.input, core)
      } yield wire
    } yield wire

    private def nextAfterDecisionC(source: GoalPhaseOrderedHumanDecision.Source,
      core: ActionCall.Core): Consequence[Prepared] = for {
      human <- humanPortC
      answer <- human.retainedC(source, core).flatMap(_.map(Consequence.success)
        .getOrElse(F.invalid("subsequent work has no retained ordered Human answer")))
      (state, from, result) = answer
      _ <- if (result.result.value.choice == "ResumeAssignedWork") Consequence.unit
        else F.invalid("deferred/cancelled ordered Human answer selects no work")
      next <- prepareResumedC(source, state, from, result.result.value)
    } yield next

    private def locateDecisionC(identity: String, selected: ContinuationIdentity,
      core: ActionCall.Core): Consequence[GoalPhaseOrderedHumanDecision.Source] = for {
      source <- F.sessionC(identity, d, definition, core)
      result <- {
        def findC(plan: Prepared): Consequence[GoalPhaseOrderedHumanDecision.Source] = for {
          completed <- retainedCompletionC(plan, core)
          (state, from, result) = completed
          found <- if (state.stage == O.Stage.Decision) {
            val decision = GoalPhaseOrderedHumanDecision.Source(source, plan.input, state, from, result, plan.candidate)
            if (GoalPhaseOrderedHumanDecision.identity(decision) == selected) Consequence.success(decision)
            else nextAfterDecisionC(decision, core).flatMap(findC)
          } else nextAssignedC(source, plan, state, from, result, core).flatMap(findC)
        } yield found
        prepareC(source).flatMap(findC)
      }
    } yield result

    private def nextAssignedC(source: F.Session, plan: Prepared, state: O.State, from: InstanceRecord,
      result: ContinuationResult[S.Result], core: ActionCall.Core): Consequence[Prepared] = state.stage match {
      case O.Stage.InspectCandidate => for {
        candidate <- GoalPhaseNativeCandidate.retainedC(source, d, core, from, state)
        validation <- GoalPhaseNativeValidation.retainedC(source, d, core, candidate, state).flatMap(
          _.map(Consequence.success).getOrElse(F.invalid("assigned review has no actual selected checks")))
        selected <- reviewSourceC(source, candidate, validation, state)
        prepared <- prepareAssignedC(source, selected.input, validation.record, selected.controller, Some(candidate))
      } yield prepared
      case O.Stage.Repair => prepareRepairC(source, state, from, plan.input.review)
      case O.Stage.Closing => GoalPhaseNativeClosure.retainedC(source, d, core, from, state).flatMap { saved =>
        if (saved.controller.stage == O.Stage.Implementation)
          prepareNextImplementationC(source, saved.controller, saved.record)
        else for {
          candidate <- GoalPhaseNativeCandidate.retainedC(source, d, core, saved.record, saved.controller)
          validation <- GoalPhaseNativeValidation.retainedC(source, d, core, candidate, saved.controller).flatMap(
            _.map(Consequence.success).getOrElse(F.invalid("assigned whole-Phase review has no actual selected checks")))
          selected <- reviewSourceC(source, candidate, validation, saved.controller)
          prepared <- prepareAssignedC(source, selected.input, validation.record, selected.controller, Some(candidate))
        } yield prepared
      }
      case O.Stage.Decision => nextAfterDecisionC(GoalPhaseOrderedHumanDecision.Source(
        source, plan.input, state, from, result, plan.candidate), core)
      case _ => F.invalid("submitted work is not reachable from the actual native frontier")
    }

    /** Read-only walk of committed native predecessors. It cannot issue work,
      * run commands, refund repair counts or select a replacement Plan.
      */
    private def locateAssignedC(source: F.Session, input: S.Request, core: ActionCall.Core): Consequence[Prepared] = {
      def findC(plan: Prepared): Consequence[Prepared] =
        if (plan.input == input) Consequence.success(plan)
        else for {
          retained <- retainedCompletionC(plan, core)
          (state, from, result) = retained
          next <- nextAssignedC(source, plan, state, from, result, core)
          found <- findC(next)
        } yield found
      prepareC(source).flatMap(findC)
    }

    private[GoalPhaseNativeWorkContinuation] def operationC(core: Option[ActionCall.Core], native: WorkflowManagedCompletion[S.Result]): Consequence[spec.OperationDefinition] = {
      val provided = S.providedDefinition
      val op = provided.providedOperations.find(o => o.service == F.completionOperation.service && o.name == F.completionOperation.operation)
      if (op.isEmpty) return F.invalid("ordered completion is absent from generated ABI")
      val instances = CallScopedWorkflowInstances.managed(d.configuration, definition, d.instances, d.continuations)
      var selected = Option.empty[(Prepared, ContinuationResult[S.Result], InstanceRecord)]
      val issued = new IssuedWorkOrderPersistence[S.Request] {
        def putIfAbsentC(work: WorkflowInteraction[S.Request, Nothing]) = F.invalid("result submission cannot issue work")
        def loadC(id: ContinuationIdentity) = core match {
          case None => F.invalid("issue lookup requires its current completion call")
          case Some(call) => F.bindC(d, definition, call).flatMap(_.bindIssuedC(F.completionOperation, S.inputCodec))
            .flatMap(_.loadC(id)(using call.executionContext))
        }
      }
      val codec = new WorkflowCompletionServiceOperation.Codec[S.Result] {
        val resultTypeIdentity = S.resultCodec.typeIdentity
        def decodeC(request: ProtocolRequest) = WorkflowJsonCommandRequest.commandArguments(request) match {
          case List(Argument("arg1", raw: String, _)) => WorkflowResultJsonV1.decodeC(raw, S.resultCodec)
          case _ => F.invalid("ordered result requires one complete continuation envelope")
        }
        def encodeC(resume: ContinuationRuntime.Resume) = F.invalid("result acknowledgement requires native committed completion")
        override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, submitted: ContinuationResult[S.Result],
          completed: Option[InstanceRecord]): Consequence[OperationResponse] = {
          val captured = selected
          selected = None
          captured match {
            case Some((plan, result, next)) if core.nonEmpty && result == submitted && completed.exists(F.same(_, next)) &&
                resume != null && resume.result == StateMachineOperationResult(closingType, submitted.resultReference) => for {
              retained <- native.loadRetainedC(submitted.continuationId)(using core.get.executionContext)
              _ <- retained match {
                case Some(value) if value.completion.result == submitted && value.completion.instanceRevision == next.revision &&
                    value.completion.closingResult.contains(resume.result) => Consequence.unit
                case _ => uncertain("ordered result committed readback unavailable; observe without retry")
              }
              wire <- waitC(next, submitted)
            } yield OperationResponse.Scalar(wire)
            case _ => uncertain("ordered result has no matching native closing readback")
          }
        }
      }
      val closing = new WorkflowCompletionServiceOperation.ClosingProgram[S.Result] {
        def selectC(submitted: ContinuationResult[S.Result], current: InstanceRecord) = core match {
          case None => F.invalid("ordered closing requires the current owning call")
          case Some(call) => for {
            actual <- issued.loadC(submitted.continuationId).flatMap {
              case Some(value) => Consequence.success(value)
              case None => F.invalid("result has no actual assigned work")
            }
            source <- F.sessionC(actual.handle.instanceIdentity.value, d, definition, call)
            input <- actual.current match {
              case order: WorkflowContinuation.WorkOrder[S.Request] => order.request.input.map(_.value)
                .map(Consequence.success).getOrElse(F.invalid("issued work has no actual input"))
              case _ => F.invalid("completion has no actual WorkOrder")
            }
            plan <- locateAssignedC(source, input, call)
            _ <- if (actual == plan.work && F.same(current, plan.pending) && F.same(source.current, current))
              Consequence.unit else F.invalid("result crosses its actual original issue or native suspension")
            _ <- plan.candidate.map(GoalPhaseNativeValidation.preconditionsC(source, d, call, _, plan.controller)).getOrElse(Consequence.unit)
            _ <- if (plan.input.kind == "RepairStep") GoalPhaseNativeCandidate.ownershipC(source, d, call, plan.controller)
              else Consequence.unit
            _ <- if (isReview(plan.input)) d.reviewInstallation.get.evidence.admitC(call, plan.input, submitted)
              else Consequence.unit
            next <- completedC(plan, submitted)
          } yield {
            selected = Some((plan, submitted, next))
            val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              ActionExecution.Completed(StateMachineOperationResult(closingType, submitted.resultReference)))
            WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next))
          }
        }
      }
      val actualInstances = core.fold[WorkflowInstancePersistence](instances)(CallScopedWorkflowInstances.forCall(instances, _))
      WorkflowCompletionServiceOperation.bindC(provided, op.get, WorkflowJsonCommandRequest.definition, spec.ResponseDefinition.void,
        codec, ContinuationRuntime.empty, issued, actualInstances, d.configuration,
        () => throw new IllegalStateException("Ordered completion requires canonical managed UoW"), Some(closing), Some(native),
        descriptiveAttributes = SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseWorkResultOperation.specification.content.descriptiveAttributes)
    }
  }

  def bindC(d: F.Declaration, definition: DefinitionBinding,
    acknowledgement: String => Consequence[Unit] = _ => Consequence.unit): Consequence[Port] = F.validateC(d).flatMap(_ =>
    if (definition == null || acknowledgement == null) F.invalid("ordered definition or acknowledgement is missing")
    else Consequence.success(new Port(d, definition, acknowledgement)))
  def bindCompletionC(params: ComponentCreate, d: F.Declaration, definition: DefinitionBinding): Consequence[spec.OperationDefinition] = for {
    port <- bindC(d, definition)
    storage <- WorkflowComponentStorage.bindC(params, SmWorkflowComponent.componentId, d.configuration,
      Vector(definition), d.instances, d.continuations)
    native <- nativeC(storage)
    declared <- port.operationC(None, native)
  } yield CallScopedOperationBinding.wrap(declared, core => F.bindC(d, definition, core)
    .flatMap(nativeC).flatMap(n => port.operationC(Some(core), n)))

  private def completedStateC(plan: Prepared, submitted: ContinuationResult[S.Result]): Consequence[O.State] = for {
    _ <- WorkflowProtocolV1.admitResultC(plan.work, submitted).map(_ => ())
    next <- if (isReview(plan.input))
      GoalPhaseNativeReview.completedStateC(plan.input, submitted.result.value, submitted.resultReference.identity, plan.controller)
    else for {
      _ <- S.admitSemanticResultC(plan.input, submitted.result.value)
      stage <- if (submitted.result.value.outcome == "completed") plan.controller.next match {
        case value: O.Semantic if value.key.kind.toString == plan.input.kind =>
          plan.controller.acceptC(O.SemanticCompleted(value.key, submitted.result.value.summary))
        case _ => F.invalid("work has no controller-assigned semantic frontier")
      } else plan.controller.acceptC(O.NativeFailure(submitted.result.value.summary))
    } yield stage
  } yield next

  private def completedC(plan: Prepared, submitted: ContinuationResult[S.Result]): Consequence[InstanceRecord] = for {
    state <- completedStateC(plan, submitted)
    nextStage = state.stage.toString
    event = if (plan.input.kind == "ReviewPhase")
      if (nextStage == "PhaseClosing") "phase-review-accepted" else "phase-review-decision-needed"
    else if (plan.input.kind == "ReviewStep") nextStage match {
      case "Closing" => "review-accepted"
      case "Repair" => "review-repair-admitted"
      case _ => "review-decision-needed"
    } else if (plan.input.kind == "RepairStep")
      if (nextStage == "InspectCandidate") "repair-result-admitted" else "repair-decision-needed"
    else if (nextStage == "InspectCandidate") "implementation-result-admitted" else "implementation-decision-needed"
    _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits(
        if (plan.input.kind == "ReviewPhase") "PhaseReview"
          else if (plan.input.kind == "ReviewStep") "Review" else if (plan.input.kind == "RepairStep") "Repair" else "Implementation",
        event, nextStage)) Consequence.unit
      else F.invalid("ordered result successor is absent from actual CML")
    next <- plan.pending.appendC(plan.pending.revision, F.history(plan.pending, "phase." + nextStage, submitted.resultReference.identity))
  } yield next
  private def continuationId(source: F.Session) = ContinuationIdentity(source.current.identity.value +
    ":ordered-implementation:" + source.started.receipt.next.revision.value)
  private def assignedId(source: F.Session, input: S.Request) = if (input.kind == "ImplementStep" && input.generation == 1) continuationId(source)
    else ContinuationIdentity(source.current.identity.value +
      (if (input.kind == "RepairStep") ":ordered-repair:"
        else if (input.kind == "ImplementStep") ":ordered-implementation:" else ":ordered-review:") + input.sourceRevision)
  private def nativeC(storage: WorkflowComponentStorage) = storage.bindCompletionC(F.completionOperation,
    S.inputCodec, S.resultCodec, closingType, (_, _) => Consequence.success(Vector.empty))
  private def waitC(record: InstanceRecord, result: ContinuationResult[S.Result]) = {
    val frontier = record.currentProgression.map(_.value).getOrElse("unknown")
    val remaining = frontier match {
      case "phase.Closing" =>
        "Selected native checks and independent semantic review are retained. Step commit and whole-Phase closure remain unfinished."
      case "phase.PhaseClosing" =>
        "Whole-Phase native checks and independent semantic review are retained. Actual authorized repository closure is still required; the Phase is not closed."
      case "phase.Repair" =>
        "Independent review requested repair. Repair execution, fresh candidate checks and fresh review remain unfinished; no Step or Phase is closed."
      case "phase.Decision" =>
        "The assigned result requires a decision. No automatic repair, Step commit or whole-Phase closure is authorized by this result."
      case _ =>
        "Candidate validation, independent review and Step/whole-Phase closure remain unfinished."
    }
    F.waitC(record, "Assigned semantic result retained", "Current frontier: " + frontier + ". " + remaining)
  }
  private def uncertain[A](reason: String): Consequence[A] =
    org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
  private def isReview(input: S.Request) = input.kind == "ReviewStep" || input.kind == "ReviewPhase"
}
