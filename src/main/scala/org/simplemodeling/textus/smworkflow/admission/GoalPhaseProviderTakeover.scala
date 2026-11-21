/**
 * @since   Nov. 21, 2026
 * @version Nov. 21, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Codec
import org.goldenport.cncf.workflow.WorkflowProtocolV1.{ContinuationResult, ExecutionPlacement}
import GoalPhaseDispatchPolicy.Capture
import GoalPhaseNativeWork.{Request, Result}
import GoalPhaseOrderedProgression as O

/** Select another admitted editor for a newly issued RepairStep. Only
  * authenticated native completion and validation records feed this selection.
  */
private[smworkflow] object GoalPhaseProviderTakeover {
  final case class Scope(session: String, plan: String, revision: String, step: String)

  final case class Failure(scope: Scope, dispatch: Capture, profile: String, executor: String,
    continuation: String, result: String, resultRevision: String, validation: String,
    candidate: O.Snapshot, check: String, kind: String, exit: Int, previousTakeover: Option[Evidence])

  final case class Evidence(fromProfile: String, toProfile: String, departedExecutor: String,
    departedContinuation: String, departedResult: String, departedResultRevision: String,
    previousValidation: String, validation: String, candidateHead: String,
    candidateTree: String, check: String) derives Codec.AsObject

  def observe(controller: O.State, prior: Option[(Request, ContinuationResult[Result])],
    payload: GoalPhaseNativeValidation.Payload, validation: String): Option[Failure] =
    for {
      step <- controller.plan.steps.lift(controller.cursor).map(_.identity)
      (request, completed) <- prior
      profile <- request.operatingProfile
      dispatch <- profile.dispatch
      execution <- completed.evidence.execution
      if Set("ImplementStep", "RepairStep")(request.kind) && completed.result.value.outcome == "completed"
      if request.instanceIdentity == completed.runId.value && request.stepIdentity.contains(step) &&
        request.selectedPlan.identity == controller.plan.identity && request.selectedPlan.revision == controller.plan.revision
      if dispatch.placement == "DELEGATED" && execution.placement == ExecutionPlacement.Delegated &&
        dispatch.candidates.contains(execution.profileIdentity)
      if payload.step == step && payload.outcome.contains("repair-required") &&
        payload.dependency.isEmpty && payload.dependencySource.isEmpty && GoalPhaseNativeValidation.repairable(payload)
      receipt <- payload.receipts.lastOption
      checked <- payload.selected.lift(payload.receipts.size - 1)
      if checked.dependency.isEmpty && Set[WorkflowSbtValidation.Kind](WorkflowSbtValidation.Kind.Compile,
        WorkflowSbtValidation.Kind.TestOnly, WorkflowSbtValidation.Kind.Test)(checked.kind)
      exit <- receipt.exitCode
    } yield Failure(Scope(request.instanceIdentity, controller.plan.identity, controller.plan.revision, step),
      dispatch, execution.profileIdentity, execution.executorContextIdentity, completed.continuationId.value,
      completed.resultReference.identity, completed.resultReference.revision, validation,
      payload.candidate, receipt.identity, checked.kind.wire, exit, profile.providerTakeover)

  def select(fresh: Capture, history: Vector[Failure]): (Capture, Option[Evidence]) = {
    val current = history.lastOption.filter(failure => fresh.placement == "DELEGATED" &&
      fresh.revision == failure.dispatch.revision && fresh.edges == failure.dispatch.edges &&
      fresh.candidates.contains(failure.profile))
    val selected = current.flatMap { failed =>
      val remaining = fresh.candidates.dropWhile(_ != failed.profile)
      val next = for {
        prior <- history.dropRight(1).lastOption.filter(previous => _repeated(previous, failed))
        alternate <- remaining.lift(1).filter(failed.dispatch.candidates.contains)
      } yield fresh.copy(role = alternate, candidates = remaining.drop(1)) ->
        Evidence(failed.profile, alternate, failed.executor, failed.continuation, failed.result, failed.resultRevision,
          prior.validation, failed.validation, failed.candidate.head, failed.candidate.tree, failed.check)
      next.orElse(failed.previousTakeover.map(evidence =>
        fresh.copy(role = failed.profile, candidates = remaining) -> evidence))
    }
    selected.filter { case (dispatch, evidence) => valid(evidence, dispatch) }
      .map { case (dispatch, evidence) => dispatch -> Some(evidence) }.getOrElse(fresh -> None)
  }

  private def _repeated(previous: Failure, current: Failure): Boolean =
    previous.scope == current.scope && previous.profile == current.profile &&
      previous.dispatch.revision == current.dispatch.revision && previous.dispatch.edges == current.dispatch.edges &&
      previous.continuation != current.continuation && previous.validation != current.validation &&
      previous.candidate == current.candidate && previous.check == current.check &&
      previous.kind == current.kind && previous.exit == current.exit

  def retained(capture: GoalPhaseOperatingProfiles.Capture, history: Vector[Failure]): Boolean =
    capture.providerTakeover.forall { evidence =>
      history.lastOption.exists { last =>
        val (dispatch, expected) = select(last.dispatch, history)
        expected.contains(evidence) && capture.dispatch.exists(value => value.copy(nature = dispatch.nature) == dispatch)
      }
    }

  def valid(value: Evidence, dispatch: Capture): Boolean =
    value != null && dispatch != null && dispatch.placement == "DELEGATED" &&
      !dispatch.candidates.contains(value.fromProfile) &&
      GoalPhaseDispatchPolicy.chain(value.fromProfile, dispatch.edges).drop(1).contains(value.toProfile) &&
      GoalPhaseDispatchPolicy.chain(value.toProfile, dispatch.edges).contains(dispatch.role) &&
      Vector(value.departedExecutor, value.departedContinuation, value.departedResult, value.departedResultRevision,
        value.previousValidation, value.validation, value.candidateHead, value.candidateTree, value.check)
        .forall(GoalPhaseRuntimeRepairControl.exact) && value.previousValidation != value.validation
}
