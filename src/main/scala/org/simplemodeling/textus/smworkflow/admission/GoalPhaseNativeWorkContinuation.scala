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
import GoalPhaseDocumentSource as D
import GoalPhaseDocumentProgress as P
import GoalPhaseOrderedHumanDecision as H

/** Ordered implementation, repair and review work and their provided result are installed
  * together. Canonical atomic suspension/completion owns all writes. No result
  * runs Git/SBT or turns implementation completion into Step/Phase acceptance.
  * @since   Oct.  6, 2026
  * @version Oct. 17, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeWorkContinuation {
  val closingType = StateMachineResultTypeReference("GoalPhaseWorkSubmissionResult")
  private final case class Prepared(source: F.Session, input: S.Request,
    intent: Option[WorkflowInstanceAtomicTransitionV1.SuspensionIntent], pending: InstanceRecord,
    work: WorkflowInteraction[S.Request, Nothing], controller: O.State,
    candidate: Option[GoalPhaseNativeCandidate.Saved], documentpolicy: Option[P.Policy] = None)
  private final case class Cursor(from: InstanceRecord, controller: O.State,
    documents: Option[D.Bound], candidate: Option[GoalPhaseNativeCandidate.Saved],
    validation: Option[GoalPhaseNativeValidation.Saved],
    lastresult: Option[(S.Request, ContinuationResult[S.Result])],
    decisioncontext: Option[S.DecisionContext], progress: Option[P.Projection] = None,
    documentpolicy: Option[P.Policy] = None)
  private sealed trait Located
  private final case class Frontier(cursor: Cursor) extends Located
  private final case class Work(prepared: Prepared) extends Located
  private final case class Human(source: H.Source) extends Located
  private final case class Paused(cursor: Cursor, reason: String, repository: Boolean = false) extends Located

  final class Port private[GoalPhaseNativeWorkContinuation](d: F.Declaration, definition: DefinitionBinding,
    acknowledgement: String => Consequence[Unit]) {
    def advanceC(identity: String, core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] =
      F.sessionC(identity, d, definition, core).flatMap(_advance_session_c(_, core))

    /** Pending assignments own their captured input, even if selected files changed. */
    private def _pending_c(source: F.Session, boundary: SuspensionBoundary, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = for {
      human <- H.requiredC
      identity = ContinuationIdentity(boundary.continuationIdentity.value)
      output <- if (boundary.completion.value == human.metadata.completionContract.identity) for {
        reader <- source.storage.bindDecisionC(H.completionOperation, H.inputCodec)
        question <- reader.loadC(identity)(using core.executionContext).flatMap(
          _.map(Consequence.success).getOrElse(F.invalid("pending ordered Human question is absent")))
        output <- NativeWorkflowOperationOutput.goalHumanC(question, core)
      } yield output else for {
        reader <- source.storage.bindIssuedC(F.completionOperation, S.inputCodec)
        work <- reader.loadC(identity)(using core.executionContext).flatMap(
          _.map(Consequence.success).getOrElse(F.invalid("pending ordered work is absent")))
        output <- NativeWorkflowOperationOutput.goalWorkRequestC(work, core)
      } yield output
    } yield output

    private def _initial_cursor_c(source: F.Session): Consequence[Cursor] = for {
      from <- GoalPhaseNativeHistory.prefixC(source.current, source.started.receipt.next.revision)
      controller <- O.startC(source.started.receipt.payload.value.plan, source.started.receipt.payload.value.base)
    } yield Cursor(from, controller, source.started.receipt.payload.value.documents, None, None, None, None)

    /** The sole read-only traversal follows only contiguous actual predecessors. */
    private def _walk_c(source: F.Session, cursor: Cursor, soughtwork: Option[ContinuationIdentity],
      soughthuman: Option[ContinuationIdentity], core: ActionCall.Core): Consequence[Located] = for {
      following <- GoalPhaseNativeHistory.nextC(source.current, cursor.from)
      located <- following match {
        case Some(entry) if Set("ordered-document-progress-intent", "ordered-document-progress-result")(entry.causation.value) =>
          cursor.documents match {
            case None => F.invalid("document progress has no actual selected source")
            case Some(documents) => for {
              retained <- GoalPhaseNativeDocumentProgress.retainedC(source, d, core, cursor.from,
                cursor.controller, documents, cursor.progress).flatMap(_.map(Consequence.success)
                  .getOrElse(F.invalid("actual progress history has no retained intent")))
              result <- retained.payload.outcome match {
                case Some("updated") => for {
                  policy <- P.policyC(cursor.controller.plan, documents, d.candidatePaths)
                  result <- _walk_c(source, cursor.copy(from = retained.record, progress = Some(retained.payload.desired),
                    documentpolicy = Some(policy)), soughtwork, soughthuman, core)
                } yield result
                case Some(conflict) => Consequence.success[Located](Paused(cursor.copy(from = retained.record),
                  "Document progress File conflict recorded: " + conflict + ". No automatic retry or rewrite."))
                case None => _paused_c(source, cursor,
                  "Document progress intent has an unknown File effect. No automatic replay or further work.")
              }
            } yield result
          }
        case Some(entry) if Set("ordered-document-plan-selected", "ordered-document-plan-conflicted")(entry.causation.value) =>
          cursor.documents match {
            case None => F.invalid("document selection has no original selected source")
            case Some(previous) => for {
              selected <- GoalPhaseNativeDocumentPlan.retainedC(source, core, cursor.from, cursor.controller, previous)
                .flatMap(_.map(Consequence.success).getOrElse(F.invalid("actual document transition has no retained business selection")))
              result <- _walk_c(source, _selected_cursor(cursor, selected), soughtwork, soughthuman, core)
            } yield result
          }
        case Some(entry) if entry.suspension.nonEmpty => for {
          pending <- GoalPhaseNativeHistory.prefixC(source.current, entry.revision)
          boundary = entry.suspension.get
          human <- H.requiredC
          result <- if (boundary.completion.value == human.metadata.completionContract.identity) for {
            decision <- _human_source_c(source, cursor)
            _ <- if (boundary.continuationIdentity.value == H.identity(decision).value) Consequence.unit
              else F.invalid("actual Human suspension differs from its Decision source")
            port <- _human_port_c
            result <- if (soughthuman.contains(H.identity(decision))) Consequence.success[Located](Human(decision))
              else port.retainedC(decision, core).flatMap {
                case None => Consequence.success[Located](Human(decision))
                case Some((state, next, answer)) =>
                  val resumed = cursor.copy(from = next, controller = state,
                    decisioncontext = if (cursor.controller.documentDecision) cursor.decisioncontext else Some(
                      S.DecisionContext(decision.result.result.value, answer.result.value.choice, answer.result.value.rationale)))
                  if (answer.result.value.choice == "ResumeAssignedWork")
                    _walk_c(source, resumed, soughtwork, soughthuman, core)
                  else Consequence.success[Located](Paused(resumed, if (answer.result.value.choice == "Defer")
                    "Human deferred this decision. No automatic question, work, command or closure follows."
                    else "Human cancelled this decision path. Repository state and history remain; the Phase is not completed."))
              }
          } yield result else for {
            prepared <- _retained_work_c(source, cursor, pending, core)
            identity = ContinuationIdentity(boundary.continuationIdentity.value)
            result <- if (soughtwork.contains(identity)) Consequence.success[Located](Work(prepared))
              else for {
                native <- _native_c(source.storage)
                retained <- native.loadRetainedC(identity)(using core.executionContext)
                result <- retained match {
                  case None => Consequence.success[Located](Work(prepared))
                  case Some(value) => for {
                    state <- _completed_state_c(prepared, value.completion.result)
                    next <- GoalPhaseNativeHistory.completionC(source.current, pending,
                      value.completion.result.resultReference, state.stage.toString)
                    clear = state.stage == O.Stage.InspectCandidate || state.stage == O.Stage.Repair
                    result <- _walk_c(source, cursor.copy(from = next, controller = state,
                      documents = prepared.input.documents,
                      candidate = if (clear) None else cursor.candidate,
                      validation = if (clear) None else cursor.validation,
                      lastresult = Some(prepared.input -> value.completion.result), decisioncontext = None), soughtwork, soughthuman, core)
                  } yield result
                }
              } yield result
          } yield result
        } yield result
        case None => Consequence.success[Located](Frontier(cursor))
        case Some(entry) => _native_next_c(source, cursor, entry, soughtwork, soughthuman, core)
      }
    } yield located

    private def _native_next_c(source: F.Session, cursor: Cursor, entry: HistoryEntry,
      soughtwork: Option[ContinuationIdentity], soughthuman: Option[ContinuationIdentity],
      core: ActionCall.Core): Consequence[Located] = cursor.controller.stage match {
      case O.Stage.InspectCandidate | O.Stage.PhaseInspection => for {
        retained <- GoalPhaseNativeCandidate.retainedOptionC(source, d, core, cursor.from, cursor.controller, cursor.documentpolicy)
        result <- retained match {
          case None => _paused_c(source, cursor, "Candidate inspection has an actual partial or unknown result; no automatic replay.")
          case Some(value) => for {
            state <- GoalPhaseNativeCandidate.observedStateC(cursor.controller, value.payload, cursor.documentpolicy)
            record <- _native_record_c(source, cursor.from, value.record, "ordered-candidate:", state.stage)
            result <- _walk_c(source, cursor.copy(from = record, controller = state,
              candidate = Some(value.copy(record = record)), validation = None), soughtwork, soughthuman, core)
          } yield result
        }
      } yield result
      case O.Stage.Validation | O.Stage.PhaseValidation => cursor.candidate match {
        case None => F.invalid("actual Validation has no current native candidate")
        case Some(candidate) => for {
          retained <- GoalPhaseNativeValidation.retainedC(source, d, core, candidate, cursor.controller, Some(cursor.from), cursor.documentpolicy)
          result <- retained match {
            case None => _paused_c(source, cursor, "Native checks have an actual partial or unknown result; no automatic replay.")
            case Some(value) => for {
              state <- GoalPhaseNativeValidation.observedStateC(cursor.controller, value.payload,
                value.record.identity.value + ":ordered-validation:" + value.payload.sourceRevision + ":validation")
              record <- _native_record_c(source, cursor.from, value.record, "ordered-validation:", state.stage)
              result <- _walk_c(source, cursor.copy(from = record, controller = state,
                validation = Some(value.copy(record = record))), soughtwork, soughthuman, core)
            } yield result
          }
        } yield result
      }
      case O.Stage.Closing => for {
        retained <- GoalPhaseNativeClosure.retainedOptionC(source, d, core, cursor.from, cursor.controller)
        result <- retained match {
          case None => _paused_c(source, cursor, "Step commit has an actual intent without its completed result; no automatic replay.")
          case Some(value) => for {
            record <- _native_record_c(source, cursor.from, value.record, "ordered-step-closure:", value.controller.stage)
            result <- _walk_c(source, cursor.copy(from = record, controller = value.controller,
              candidate = None, validation = None), soughtwork, soughthuman, core)
          } yield result
        }
      } yield result
      case O.Stage.PhaseClosing if entry.causation.value.startsWith("ordered-repository-intent:") ||
          entry.causation.value.startsWith("ordered-repository-closed:") =>
        Consequence.success[Located](Paused(cursor, "The existing child repository closure owns its retained input and effects.", repository = true))
      case _ => _paused_c(source, cursor, "Unrecognized advanced native predecessor: " + entry.causation.value + ". No automatic replay.")
    }

    private def _paused_c(source: F.Session, cursor: Cursor, reason: String): Consequence[Located] =
      Consequence.success(Paused(cursor.copy(from = source.current), reason))

    private def _native_record_c(source: F.Session, from: InstanceRecord, saved: InstanceRecord,
      cause: String, stage: O.Stage): Consequence[InstanceRecord] = for {
      record <- GoalPhaseNativeHistory.prefixC(source.current, saved.revision)
      entries = record.history.drop(from.history.size)
      _ <- if (saved.identity == from.identity &&
          saved.definition.workflowIdentity == from.definition.workflowIdentity &&
          saved.definition.workflowRevision == from.definition.workflowRevision && entries.nonEmpty &&
          entries.forall(value => value.suspension.isEmpty && value.causation.value.startsWith(cause)) &&
          record.currentProgression.contains(ProgressionReference("phase." + stage.toString))) Consequence.unit
        else F.invalid("retained native result has no corresponding contiguous actual history")
    } yield record

    private def _selected_cursor(cursor: Cursor, selected: GoalPhaseNativeDocumentPlan.Selected): Cursor = {
      val clear = selected.controller.candidate.isEmpty && cursor.controller.candidate.nonEmpty
      cursor.copy(from = selected.record, controller = selected.controller, documents = Some(selected.documents),
        candidate = if (clear) None else cursor.candidate, validation = if (clear) None else cursor.validation)
    }

    private def _retained_work_c(source: F.Session, cursor: Cursor, pending: InstanceRecord,
      core: ActionCall.Core): Consequence[Prepared] = for {
      reader <- source.storage.bindIssuedC(F.completionOperation, S.inputCodec)
      boundary <- pending.suspension.map(Consequence.success).getOrElse(F.invalid("actual work suspension is absent"))
      actual <- reader.loadC(ContinuationIdentity(boundary.continuationIdentity.value))(using core.executionContext)
        .flatMap(_.map(Consequence.success).getOrElse(F.invalid("actual semantic suspension has no saved issue")))
      order <- actual.current match {
        case value: WorkflowContinuation.WorkOrder[S.Request] => Consequence.success(value)
        case _ => F.invalid("actual semantic issue is not a WorkOrder")
      }
      input <- order.request.input.map(value => Consequence.success(value.value)).getOrElse(F.invalid("actual issue has no business input"))
      _ <- GoalPhaseAcceptanceBoundary.savedPlanC(input.selectedPlan)
      controller <- (cursor.documents, input.documents) match {
        case (None, None) => cursor.controller.refreshedC(input.selectedPlan, false).flatMap {
          case Right(value) => Consequence.success(value)
          case Left(labels) => F.invalid("actual issued Plan contradicts its predecessor: " + labels.mkString(", "))
        }
        case (Some(previous), Some(documents)) => GoalPhaseDocumentRefresh.evaluateC(cursor.controller, previous,
          input.selectedPlan, documents, false).flatMap {
          case Right(value) => Consequence.success(value.controller)
          case Left(labels) => F.invalid("actual issued documents contradict their predecessor: " + labels.mkString(", "))
        }
        case _ => F.invalid("actual issued document source option differs from its predecessor")
      }
      semantic <- controller.next match {
        case value: O.Semantic => Consequence.success(value)
        case _ => F.invalid("actual issue has no semantic controller frontier")
      }
      required <- S.requiredC(input.kind)
      context <- _review_context_c(cursor.copy(controller = controller), input.kind)
      checkfailure <- _check_failure_context_c(cursor.copy(controller = controller), input.kind)
      _ <- if (input.instanceIdentity == source.current.identity.value && input.sourceRevision == cursor.from.revision.value.toString &&
          input.kind == semantic.key.kind.toString && input.stepIdentity == semantic.key.step && input.generation == semantic.key.generation &&
          input.originalBase == semantic.originalBase && input.completed == semantic.completed && input.purpose == semantic.purpose &&
          input.candidate == semantic.candidate && input.checksReceipt == semantic.checksReceipt &&
          input.repairDirective == semantic.repairDirective && input.review == context && input.checkFailure == checkfailure &&
          input.decisionContext == cursor.decisioncontext &&
          actual.handle.instanceIdentity.value == source.current.identity.value && order.request.runId.value == source.current.identity.value &&
          order.request.continuationId.value == boundary.continuationIdentity.value &&
          _assigned_id(source, input).value == boundary.continuationIdentity.value &&
          pending.currentProgression == cursor.from.currentProgression &&
          order.request.expectedRevision.value == pending.revision.value.toString && order.request.completionOperation == F.completionOperation &&
          boundary.completion.value == required.metadata.completionContract.identity &&
          order.request.requiredOperation == required.identity) Consequence.unit
        else F.invalid("actual issued business input differs from its contiguous native predecessor")
    } yield Prepared(source, input, None, pending, actual, controller,
      if (_is_review(input)) cursor.candidate else None, cursor.documentpolicy)

    private def _review_context_c(cursor: Cursor, kind: String): Consequence[Option[GoalPhaseNativeReview.Context]] = kind match {
      case "ReviewStep" | "ReviewPhase" => (cursor.candidate, cursor.validation) match {
        case (Some(candidate), Some(validation)) if validation.payload.outcome.contains("passed") && candidate.payload.diff.nonEmpty =>
          Consequence.success(Some(GoalPhaseNativeReview.Context(candidate.payload.changedPaths,
            candidate.payload.diff.get, validation.payload.receipts)))
        case _ => F.invalid("review has no actual current candidate and passed native checks")
      }
      case "RepairStep" if cursor.validation.exists(_.payload.outcome.contains("repair-required")) => Consequence.success(None)
      case "RepairStep" => cursor.lastresult.flatMap(_._1.review).map(value => Consequence.success(Some(value)))
        .getOrElse(F.invalid("repair has no retained independent review context"))
      case _ => Consequence.success(None)
    }

    private def _check_failure_context_c(cursor: Cursor, kind: String): Consequence[Option[S.CheckFailureContext]] =
      if (kind != "RepairStep" || !cursor.validation.exists(_.payload.outcome.contains("repair-required")))
        Consequence.success(None)
      else (cursor.candidate, cursor.validation) match {
        case (Some(candidate), Some(validation)) if candidate.payload.candidate.contains(validation.payload.candidate) &&
            candidate.payload.diff.nonEmpty =>
          S.checkFailureContextC(candidate.payload.changedPaths, candidate.payload.diff.get, validation.payload).map(Some(_))
        case _ => F.invalid("repair has no matching actual retained failed candidate and validation")
      }

    private def _advance_session_c(source: F.Session, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = source.current.suspension match {
      case Some(boundary) => _pending_c(source, boundary, core)
      case None => for {
        initial <- _initial_cursor_c(source)
        located <- _walk_c(source, initial, None, None, core)
        wire <- located match {
          case Work(plan) => NativeWorkflowOperationOutput.goalWorkRequestC(plan.work, core)
          case Human(decision) => _human_port_c.flatMap(_.advanceC(decision, core))
          case Paused(cursor, _, true) => GoalPhaseNativeRepositoryClosure.advanceC(source, d, core,
            cursor.from, cursor.controller, acknowledgement, cursor.documentpolicy)
          case Paused(cursor, reason, false) => _wait_cursor_c(cursor, reason)
          case Frontier(cursor) => _frontier_c(source, cursor, core)
        }
      } yield wire
    }

    private def _frontier_c(source: F.Session, cursor: Cursor, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = for {
      selected <- cursor.documents match {
        case Some(documents) if cursor.controller.stage != O.Stage.Decision && cursor.controller.stage != O.Stage.Closed =>
          GoalPhaseNativeDocumentPlan.advanceC(source, d, core, cursor.from, cursor.controller, documents, cursor.lastresult.nonEmpty)
        case _ => Consequence.success(Option.empty[GoalPhaseNativeDocumentPlan.Selected])
      }
      result <- selected match {
        case Some(_) => F.sessionC(source.current.identity.value, d, definition, core).flatMap(_advance_session_c(_, core))
        case None => _progress_c(source, cursor, core)
      }
    } yield result

    private def _progress_c(source: F.Session, cursor: Cursor, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = cursor.documents match {
      case Some(documents) if cursor.controller.stage != O.Stage.Decision && cursor.controller.stage != O.Stage.Closed => for {
        saved <- GoalPhaseNativeDocumentProgress.advanceC(source, d, core, cursor.from, cursor.controller,
          documents, cursor.progress, acknowledgement)
        result <- saved match {
          case None => _dispatch_c(source, cursor, core)
          case Some(value) => F.waitC(value.record, "Selected document progress",
            "Actual File intent/result is retained; the owning Port reconstructs its outcome before dispatch.")
            .flatMap(_after_native_c(source, cursor, _, core))
        }
      } yield result
      case _ => _dispatch_c(source, cursor, core)
    }

    private def _dispatch_c(source: F.Session, cursor: Cursor, core: ActionCall.Core):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] = cursor.controller.stage match {
      case O.Stage.Implementation | O.Stage.Repair | O.Stage.Review | O.Stage.PhaseReview =>
        if (Set(O.Stage.Review, O.Stage.PhaseReview)(cursor.controller.stage) && d.reviewInstallation.isEmpty)
          _wait_cursor_c(cursor, "Actual native checks are retained; independent review installation remains unavailable.")
        else for {
          semantic <- cursor.controller.next match {
            case value: O.Semantic => Consequence.success(value)
            case _ => F.invalid("current frontier has no assigned semantic work")
          }
          _ <- if (_is_review_kind(semantic.key.kind.toString)) d.reviewInstallation.get.validateC else Consequence.unit
          context <- _review_context_c(cursor, semantic.key.kind.toString)
          checkfailure <- _check_failure_context_c(cursor, semantic.key.kind.toString)
          input = _request(source, cursor, semantic, context, checkfailure)
          draft <- _prepare_assigned_c(source, input, cursor.from, cursor.controller,
            if (_is_review(input)) cursor.candidate else None, cursor.documentpolicy)
          plan <- _issue_assigned_c(draft, core)
          wire <- NativeWorkflowOperationOutput.goalWorkRequestC(plan.work, core)
        } yield wire
      case O.Stage.InspectCandidate | O.Stage.PhaseInspection =>
        if (cursor.controller.stage == O.Stage.InspectCandidate && d.candidatePaths.isEmpty ||
            cursor.controller.stage == O.Stage.PhaseInspection && d.phaseValidationPolicy.isEmpty)
          _wait_cursor_c(cursor, "Native candidate inspection is not installed; selected work and Phase remain open.")
        else GoalPhaseNativeCandidate.advanceC(source, d, core, cursor.from, cursor.controller, acknowledgement,
          runValidation = false, documentPolicy = cursor.documentpolicy).flatMap(_after_native_c(source, cursor, _, core))
      case O.Stage.Validation | O.Stage.PhaseValidation =>
        if ((if (cursor.controller.stage == O.Stage.PhaseValidation) d.phaseValidationPolicy else d.validationPolicy).isEmpty)
          _wait_cursor_c(cursor, "Actual candidate is retained; selected native check policy remains unavailable.")
        else cursor.candidate.map(candidate => GoalPhaseNativeValidation.advanceC(source, d, core, candidate,
          cursor.controller, acknowledgement, Some(cursor.from), cursor.documentpolicy).flatMap(_after_native_c(source, cursor, _, core)))
          .getOrElse(F.invalid("selected checks have no actual current candidate"))
      case O.Stage.Closing => if (d.closureInstallation.isEmpty)
        _wait_cursor_c(cursor, "Selected checks and review are retained; Step closure remains unavailable.")
        else for {
          closed <- GoalPhaseNativeClosure.advanceC(source, d, core, cursor.from, cursor.controller, acknowledgement)
          fallback <- closed match {
            case None => for {
              input <- GoalPhaseNativeClosure.inputC(cursor.from, cursor.controller)
              context <- NativeHostAuthorityContext.stepC(core, input)
              waiting <- _wait_cursor_c(cursor, "Actual Step commit authority is unavailable; the selected Step and Phase remain open.")
            } yield waiting.copy(authorityContext = Some(context))
            case Some(_) => _wait_cursor_c(cursor, "Actual ordered Step commit is retained; whole-Phase closure remains unfinished.")
          }
          wire <- _after_native_c(source, cursor, fallback, core)
        } yield wire
      case O.Stage.PhaseClosing => if (d.repositoryClosure.nonEmpty)
        GoalPhaseNativeRepositoryClosure.advanceC(source, d, core, cursor.from, cursor.controller, acknowledgement, cursor.documentpolicy)
        else _wait_cursor_c(cursor, "Whole-Phase checks and review are retained; repository closure remains unavailable.")
      case O.Stage.Decision =>
        if (d.humanDecision.nonEmpty && (cursor.controller.documentDecision || cursor.controller.decisionSource.exists(
            Set(O.Stage.Implementation, O.Stage.Repair, O.Stage.Review, O.Stage.PhaseReview)))) for {
          decision <- _human_source_c(source, cursor)
          port <- _human_port_c
          wire <- port.advanceC(decision, core)
        } yield wire else _wait_cursor_c(cursor, cursor.controller.decision.getOrElse("Native Decision remains open."))
      case O.Stage.Closed => _wait_cursor_c(cursor, "Actual native Phase closure is retained.")
    }

    private def _after_native_c(source: F.Session, cursor: Cursor,
      fallback: org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult,
      core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] =
      F.sessionC(source.current.identity.value, d, definition, core).flatMap { current =>
        if (current.current.revision.value > cursor.from.revision.value) _advance_session_c(current, core)
        else Consequence.success(fallback)
      }

    private def _request(source: F.Session, cursor: Cursor, semantic: O.Semantic,
      context: Option[GoalPhaseNativeReview.Context], checkfailure: Option[S.CheckFailureContext]): S.Request =
      S.Request(source.current.identity.value, cursor.from.revision.value.toString, semantic.key.kind.toString,
        semantic.key.step, semantic.key.generation, semantic.purpose, semantic.selectedPlan,
        semantic.originalBase, semantic.completed, semantic.candidate, semantic.checksReceipt,
        semantic.repairDirective, context, cursor.decisioncontext, cursor.documents, checkfailure)

    private def _issue_assigned_c(plan: Prepared, core: ActionCall.Core): Consequence[Prepared] = for {
      intent <- plan.intent.map(Consequence.success).getOrElse(F.invalid("retained work cannot be resuspended"))
      issued <- plan.source.storage.bindIssuedC(F.completionOperation, S.inputCodec)
      existing <- issued.loadC(_assigned_id(plan.source, plan.input))(using core.executionContext)
      _ <- if (existing.isEmpty) Consequence.unit else F.invalid("saved issue has no corresponding actual pending frontier")
      _ <- plan.candidate.map(GoalPhaseNativeValidation.preconditionsC(plan.source, d, core, _, plan.controller,
        documentPolicy = plan.documentpolicy)).getOrElse(Consequence.unit)
      work <- GoalPhaseNativeExecution.bindIssuedC(plan.work, d.executionProfiles, d.executionOrigin)
      _ <- issued.suspendC(intent, work)(using core.executionContext)
    } yield plan.copy(work = work)

    private def _human_source_c(source: F.Session, cursor: Cursor): Consequence[H.Source] =
      cursor.lastresult.map { case (input, result) =>
        if (cursor.controller.stage == O.Stage.Decision) Consequence.success(
          H.Source(source, input, cursor.controller, cursor.from, result, cursor.candidate, cursor.documents))
        else F.invalid("Human question has no actual Decision predecessor")
      }.getOrElse(F.invalid("Human question has no genuine preceding semantic result"))

    private def _human_port_c: Consequence[H.Port] =
      d.humanDecision.map(installation => Consequence.success(new H.Port(
        d, definition, installation, _locate_decision_c, _resume_decision_c, acknowledgement)))
        .getOrElse(F.invalid("ordered Human handling is not installed"))

    def bindHumanCompletionC(params: ComponentCreate): Consequence[spec.OperationDefinition] =
      _human_port_c.flatMap(_.bindCompletionC(params))

    private def _resume_decision_c(source: H.Source, state: O.State, from: InstanceRecord,
      answer: H.Answer, core: ActionCall.Core): Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] =
      F.sessionC(source.session.current.identity.value, d, definition, core).flatMap(_advance_session_c(_, core))

    private def _locate_decision_c(identity: String, selected: ContinuationIdentity,
      core: ActionCall.Core): Consequence[H.Source] = for {
      source <- F.sessionC(identity, d, definition, core)
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, None, Some(selected), core)
      decision <- located match {
        case Human(value) if H.identity(value) == selected => Consequence.success(value)
        case _ => F.invalid("submitted Human answer is unreachable through actual contiguous native history")
      }
    } yield decision

    private def _locate_assigned_c(source: F.Session, selected: ContinuationIdentity,
      core: ActionCall.Core): Consequence[Prepared] = for {
      initial <- _initial_cursor_c(source)
      located <- _walk_c(source, initial, Some(selected), None, core)
      prepared <- located match {
        case Work(value) if value.pending.suspension.exists(_.continuationIdentity.value == selected.value) => Consequence.success(value)
        case _ => F.invalid("submitted work is unreachable through actual contiguous native history")
      }
    } yield prepared

    private def _wait_cursor_c(cursor: Cursor, reason: String):
      Consequence[org.simplemodeling.textus.smworkflow.value.GoalPhaseAdvanceResult] =
      F.waitC(cursor.from, "Ordered GoalPhase remains open", reason).map(value => value.copy(
        validation = cursor.validation.map(saved => NativeWorkflowOperationOutput.goalValidation(saved.payload))))

    private def _prepare_assigned_c(source: F.Session, input: S.Request, from: InstanceRecord,
      controller: O.State, candidate: Option[GoalPhaseNativeCandidate.Saved] = None,
      documentpolicy: Option[P.Policy] = None): Consequence[Prepared] = for {
      _ <- GoalPhaseAcceptanceBoundary.savedPlanC(input.selectedPlan)
      required <- S.requiredC(input.kind)
      independent = _is_review(input)
      requirement <- _requirement_c(input, required, independent)
      documents <- GoalPhaseDocumentSource.instructionsC(input.documents, input.stepIdentity)
      bundle = ContextBundle((if (independent) "Independently review the actual candidate and selected native check outcomes"
        else if (input.kind == "RepairStep" && input.checkFailure.nonEmpty)
          "Correct semantic source only within the selected Step to fix its selected failed checks using the retained actual typed diagnostics. Runtime owns commands, fresh candidate observation and revalidation before independent review."
        else if (input.kind == "RepairStep") "Repair only the assigned Step findings from the retained independent review"
        else "Implement only the current Step of the complete selected ordered Phase plan") +
        GoalPhaseAcceptanceBoundary.instructions(input.selectedPlan) + documents,
        Vector(ContextReference(input.selectedPlan.identity, input.selectedPlan.revision)), Vector.empty,
        ContextSnapshot(from.definition.workflowRevision.value, evidenceRevision = Some(input.sourceRevision)))
      continuation = Continuation(StateMachineRunIdentity(from.identity.value), _assigned_id(source, input),
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
      pending <- from.appendC(from.revision, entry)
      handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, from)
      work = WorkflowInteraction[S.Request, Nothing](handle, WorkflowContinuation.WorkOrder(
        ContinuationRequest(continuation.runId, continuation.continuationId, continuation.expectedRevision,
          required.identity, required.operation, Some(TypedValue(S.inputCodec.typeIdentity, input)), required.resultType,
          bundle, required.metadata.completionContract, required.metadata.evidenceContract, F.completionOperation),
        requirement, MinimalPresentation(if (input.kind == "ReviewPhase") "Independent whole-Phase review"
            else if (independent) "Independent selected Step review"
            else if (input.kind == "RepairStep") "Repair selected Phase Step" else "Implement selected Phase Step",
          "Return only the assigned semantic result. Workflow owns commands, validation, Step and Phase closure.")))
    } yield Prepared(source, input, Some(intent), pending, work, controller, candidate, documentpolicy)

    private def _requirement_c(input: S.Request, required: StateMachineRequiredOperation,
      independent: Boolean): Consequence[ExecutionRequirement] = {
      val base = ExecutionRequirement(Vector(CapabilityRequirement(required.identity.capability)),
        RiskLevel("SOURCE_IMPLEMENTATION"), ReasoningLevel.Standard, independent)
      val classification = if (input.kind == "ReviewPhase") input.selectedPlan.phaseReviewClassification
        else input.stepIdentity.flatMap(identity => input.selectedPlan.steps.find(_.identity == identity))
          .flatMap(_.workClassification).map { work => input.kind match {
            case "ImplementStep" => work.implementation
            case "RepairStep" => work.repair
            case _ => work.review
          }}
      classification match {
        // Reconstruct semantic work from its retained selected Plan, never
        // from a later Component profile mapping. Only fresh issue after an
        // absent issued-row lookup binds disposition; reconstruction stays semantic.
        case Some(value) => value.requirementC(base)
        case None => Consequence.success(base) // Current Plan classification is optional business input.
      }
    }

    private[GoalPhaseNativeWorkContinuation] def operationC(core: Option[ActionCall.Core], native: WorkflowManagedCompletion[S.Result],
      result: Option[ContinuationResult[NativeWorkflowResultInput.Value]] = None): Consequence[spec.OperationDefinition] = {
      val provided = S.providedDefinition
      val op = provided.providedOperations.find(o => o.service == F.completionOperation.service && o.name == F.completionOperation.operation)
      if (op.isEmpty) return F.invalid("ordered completion is absent from generated ABI")
      val instances = CallScopedWorkflowInstances.managed(d.configuration, definition, d.instances, d.continuations)
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
        def decodeC(request: ProtocolRequest) = result.map(NativeWorkflowResultInput.typedC(_, S.resultCodec))
          .getOrElse(F.invalid("ordered completion has no resolved semantic result"))
        def encodeC(resume: ContinuationRuntime.Resume) = F.invalid("result acknowledgement requires native committed completion")
        override def encodeAfterCommitC(resume: ContinuationRuntime.Resume, submitted: ContinuationResult[S.Result],
          completed: Option[InstanceRecord]): Consequence[OperationResponse] =
          completed.map(record => Consequence.success(NativeWorkflowOperationOutput.response(NativeWorkflowOperationOutput.accepted(submitted, record))))
            .getOrElse(_uncertain("ordered result has no native committed state"))
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
            draft <- _locate_assigned_c(source, submitted.continuationId, call)
            plan = draft
            _ <- plan.candidate.map(GoalPhaseNativeValidation.preconditionsC(source, d, call, _, plan.controller,
              documentPolicy = plan.documentpolicy)).getOrElse(Consequence.unit)
            _ <- if (plan.input.kind == "RepairStep") GoalPhaseNativeCandidate.ownershipC(source, d, call, plan.controller)
              else Consequence.unit
            _ <- if (_is_review(plan.input)) d.reviewInstallation.get.evidence.admitC(call, plan.input, submitted)
              else Consequence.unit
            state <- _completed_state_c(plan, submitted)
            next <- _completed_c(plan, submitted, state)
          } yield {
            val program = ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], ActionExecution](
              ActionExecution.Completed(StateMachineOperationResult(closingType, submitted.resultReference)))
            WorkflowCompletionServiceOperation.SelectedClosing(program, Some(next))
          }
        }
      }
      val actualInstances = core.fold[WorkflowInstancePersistence](instances)(CallScopedWorkflowInstances.forCall(instances, _))
      WorkflowCompletionServiceOperation.bindC(provided, op.get, SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseWorkResultOperation.specification.request, SmWorkflowComponent.GoalPhaseServiceService.SubmitGoalPhaseWorkResultOperation.specification.response,
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
    native <- _native_c(storage)
    declared <- port.operationC(None, native)
  } yield CallScopedOperationBinding.wrapResult(declared, (core, result) => F.bindC(d, definition, core)
    .flatMap(_native_c).flatMap(n => port.operationC(Some(core), n, Some(result))))

  private def _completed_state_c(plan: Prepared, submitted: ContinuationResult[S.Result]): Consequence[O.State] = for {
    next <- if (_is_review(plan.input))
      GoalPhaseNativeReview.completedStateC(submitted.result.value, submitted.resultReference.identity, plan.controller)
    else for {
      _ <- S.admitSemanticResultC(plan.input, submitted.result.value)
      stage <- if (submitted.result.value.outcome == "completed") plan.controller.next match {
        case value: O.Semantic if value.key.kind.toString == plan.input.kind =>
          plan.controller.acceptC(O.SemanticCompleted(value.key, submitted.result.value.summary))
        case _ => F.invalid("work has no controller-assigned semantic frontier")
      } else plan.controller.acceptC(O.NativeFailure(submitted.result.value.summary))
    } yield stage
  } yield next

  private def _completed_c(plan: Prepared, submitted: ContinuationResult[S.Result], state: O.State): Consequence[InstanceRecord] =
    plan.pending.appendC(plan.pending.revision,
      F.history(plan.pending, "phase." + state.stage.toString, submitted.resultReference.identity))
  private def _continuation_id(source: F.Session) = ContinuationIdentity(source.current.identity.value +
    ":ordered-implementation:" + source.started.receipt.next.revision.value)
  private def _assigned_id(source: F.Session, input: S.Request) = if (input.kind == "ImplementStep" && input.generation == 1) _continuation_id(source)
    else ContinuationIdentity(source.current.identity.value +
      (if (input.kind == "RepairStep") ":ordered-repair:"
        else if (input.kind == "ImplementStep") ":ordered-implementation:" else ":ordered-review:") + input.sourceRevision)
  private def _native_c(storage: WorkflowComponentStorage) = storage.bindCompletionC(F.completionOperation,
    S.inputCodec, S.resultCodec, closingType, (_, _) => Consequence.success(Vector.empty))
  private def _uncertain[A](reason: String): Consequence[A] =
    org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(reason)
  private def _is_review_kind(kind: String): Boolean = kind == "ReviewStep" || kind == "ReviewPhase"
  private def _is_review(input: S.Request): Boolean = _is_review_kind(input.kind)
}
