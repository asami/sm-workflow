package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.record.io.RecordDecoder
import org.simplemodeling.textus.smworkflow.value as V

/** Selected review context from the actual owning issued WorkOrder.
  * @since   Oct. 14, 2026
  * @version Oct. 26, 2026
  */
private[smworkflow] object NativeReviewContext {
  def goalC(core: ActionCall.Core, work: WorkflowInteraction[GoalPhaseNativeWork.Request, Nothing]):
    Consequence[Option[V.WorkflowReviewContext]] = for {
    input <- NativeWorkflowOperationOutput.workInputC(work)
    context <- if (input.kind == "ReviewStep" || input.kind == "ReviewPhase")
      _context_c(core, work, NativeExternalEvidence.Purpose.GoalReview,
        ThreeWorkflowExternalEvidencePorts.goalReviewTarget(input)).map(Some(_))
      else Consequence.success(Option.empty[V.WorkflowReviewContext])
  } yield context

  def repositoryC(core: ActionCall.Core, work: WorkflowInteraction[RepositorySyncNativeMergeReview.Request, Nothing]):
    Consequence[V.WorkflowReviewContext] = for {
    input <- NativeWorkflowOperationOutput.workInputC(work)
    context <- _context_c(core, work, NativeExternalEvidence.Purpose.RepositoryReview,
      ThreeWorkflowExternalEvidencePorts.repositoryReviewTarget(input))
  } yield context

  def standaloneC(core: ActionCall.Core, work: WorkflowInteraction[StandaloneFixExecution.Work, Nothing]):
    Consequence[Option[V.WorkflowReviewContext]] = for {
    input <- NativeWorkflowOperationOutput.workInputC(work)
    result <- if (input.kind == "Review") {
      import io.circe.syntax.*
      _context_c(core, work, NativeExternalEvidence.Purpose.StandaloneReview, input.asJson).map(Some(_))
    } else Consequence.success(Option.empty[V.WorkflowReviewContext])
  } yield result

  private def _context_c[A](core: ActionCall.Core, work: WorkflowInteraction[A, Nothing],
    purpose: NativeExternalEvidence.Purpose, target: Json): Consequence[V.WorkflowReviewContext] = for {
    owner <- Option(core).flatMap(_.component).map(_.core.instanceId.canonicalKey)
      .filter(_exact).map(Consequence.success).getOrElse(Consequence.stateConflict("Issued review has no actual owning component"))
    request <- work.current match {
      case order: WorkflowContinuation.WorkOrder[A] if order.request != null => Consequence.success(order.request)
      case _ => Consequence.stateConflict("Issued review is not a WorkOrder")
    }
    resultType <- request.resultType.map(_.value).filter(_exact).map(Consequence.success)
      .getOrElse(Consequence.stateConflict("Issued review has no declared result type"))
    _ <- if (request.runId != null && request.continuationId != null && work.handle != null &&
        _exact(request.runId.value) && _exact(request.continuationId.value)) Consequence.unit
      else Consequence.stateConflict("Issued review has no actual completion identity")
    handleJson = NativeHumanDecisionScope.handleJson(work.handle)
    _ <- if (handleJson.asObject.exists(_.values.forall(_.asString.exists(_exact)))) Consequence.unit
      else Consequence.stateConflict("Issued review has an incomplete actual handle")
    handle <- new RecordDecoder().json(handleJson.noSpaces)
    selected <- new RecordDecoder().json(target.noSpaces)
  } yield V.WorkflowReviewContext(purpose.identity, owner, selected,
    V.WorkflowReviewCompletion(handle, request.runId.value, request.continuationId.value, resultType))

  private def _exact(value: String): Boolean =
    value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
