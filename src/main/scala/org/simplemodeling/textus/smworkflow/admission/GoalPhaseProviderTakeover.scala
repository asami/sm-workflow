/**
 * @since   Nov. 21, 2026
 * @version Nov. 21, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.Codec
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.SbtValidationMode
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
    candidate: O.Snapshot, check: String, kind: String, exit: Int, previousTakeover: Option[Evidence],
    diagnosticPolicy: Option[(String, Int)] = None, diagnostics: Option[Vector[String]] = None)

  final case class Evidence(fromProfile: String, toProfile: String, departedExecutor: String,
    departedContinuation: String, departedResult: String, departedResultRevision: String,
    previousValidation: String, validation: String, candidateHead: String,
    candidateTree: String, check: String, trend: Option[Trend] = None)
  object Evidence {
    given Codec.AsObject[Evidence] = Codec.AsObject.from(deriveDecoder[Evidence],
      deriveEncoder[Evidence].mapJsonObject(_.filter { case (_, value) => !value.isNull }))
  }
  final case class Trend(reason: String, validations: Vector[String]) derives Codec.AsObject
  final case class Decline(fromProfile: String, executor: String, continuation: String,
    resultIdentity: String, resultRevision: String, successor: Capture) derives Codec.AsObject

  def declineValid(value: Decline): Boolean =
    value != null && GoalPhaseDispatchPolicy.valid(value.successor) && value.successor.placement == "DELEGATED" &&
      !value.successor.candidates.contains(value.fromProfile) &&
      GoalPhaseDispatchPolicy.chain(value.fromProfile, value.successor.edges).lift(1).contains(value.successor.role) &&
      Vector(value.executor, value.continuation, value.resultIdentity, value.resultRevision)
        .forall(GoalPhaseRuntimeRepairControl.exact)

  /** A returned capability decline can consume only the remainder of its own
    * captured chain. A blocked/failed/uncertain attempt is not this outcome.
    */
  def declineC(request: Request, returned: ContinuationResult[Result]): Consequence[Option[Decline]] = {
    val selected = for {
      profile <- request.operatingProfile
      captured <- profile.dispatch
      actual <- returned.evidence.execution
      if request.instanceIdentity == returned.runId.value && request.stepIdentity.nonEmpty &&
        returned.result.value.outcome == "declined" && captured.placement == "DELEGATED" &&
        actual.placement == ExecutionPlacement.Delegated && captured.candidates.contains(actual.profileIdentity)
    } yield {
      val remaining = captured.candidates.dropWhile(_ != actual.profileIdentity).drop(1)
      remaining.headOption.map(role => Decline(actual.profileIdentity, actual.executorContextIdentity,
        returned.continuationId.value, returned.resultReference.identity, returned.resultReference.revision,
        captured.copy(role = role, candidates = remaining)))
    }
    selected match {
      case Some(next) if next.forall(declineValid) => Consequence.success(next)
      case _ => Consequence.stateConflict("Provider decline has no attributable current delegated completion")
    }
  }

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
      if checked.dependency.isEmpty && (checked.carLint ||
        Set[WorkflowSbtValidation.Kind](WorkflowSbtValidation.Kind.Compile,
          WorkflowSbtValidation.Kind.TestOnly, WorkflowSbtValidation.Kind.Test)(checked.kind))
      exit <- receipt.exitCode
    } yield Failure(Scope(request.instanceIdentity, controller.plan.identity, controller.plan.revision, step),
      dispatch, execution.profileIdentity, execution.executorContextIdentity, completed.continuationId.value,
      completed.resultReference.identity, completed.resultReference.revision, validation,
      payload.candidate, receipt.identity, checked.wireKind, exit, profile.providerTakeover,
      controller.repairControl.filter(_.policy == GoalPhaseRuntimeRepairControl.version)
        .map(c => c.policy -> c.selectedLimit),
      if (checked.carLint) receipt.carLint.flatMap(_.report).map(r =>
        r.blockers.map(f => io.circe.Json.arr(Vector(f.category, f.code, f.path).map(io.circe.Json.fromString)*).noSpaces).distinct.sorted)
      else _diagnostics(IncrementalSbtFailureDiagnosis.extract(receipt, checked.kind, payload.sourceRevision, payload.candidate),
        receipt.sbt.flatMap(_.batch)))

  def select(fresh: Capture, history: Vector[Failure]): (Capture, Option[Evidence]) = {
    val current = history.lastOption.filter(failure => fresh.placement == "DELEGATED" &&
      fresh.revision == failure.dispatch.revision && fresh.edges == failure.dispatch.edges &&
      fresh.candidates.contains(failure.profile))
    val selected = current.flatMap { failed =>
      val remaining = fresh.candidates.dropWhile(_ != failed.profile)
      val next = for {
        basis <- history.dropRight(1).lastOption.filter(previous => _repeated(previous, failed))
          .map(previous => previous -> Option.empty[Trend]).orElse(_trend(history))
        alternate <- remaining.lift(1).filter(failed.dispatch.candidates.contains)
      } yield fresh.copy(role = alternate, candidates = remaining.drop(1)) ->
        Evidence(failed.profile, alternate, failed.executor, failed.continuation, failed.result, failed.resultRevision,
          basis._1.validation, failed.validation, failed.candidate.head, failed.candidate.tree, failed.check, basis._2)
      next.orElse(failed.previousTakeover.map(evidence =>
        fresh.copy(role = failed.profile, candidates = remaining) -> evidence))
    }
    selected.filter { case (dispatch, evidence) => valid(evidence, dispatch) }
      .map { case (dispatch, evidence) => dispatch -> Some(evidence) }.getOrElse {
        // A prior authenticated decline can narrow the assignment without a
        // validation-takeover record. Retain its actual suffix on later work.
        current.filter(f => f.dispatch.role == f.profile && GoalPhaseDispatchPolicy.valid(f.dispatch))
          .flatMap { f =>
            val suffix = fresh.candidates.dropWhile(_ != f.profile)
            Option.when(suffix.nonEmpty && suffix != fresh.candidates && f.dispatch.candidates == suffix)(
              fresh.copy(role = f.profile, candidates = suffix) -> None)
          }.getOrElse(fresh -> None)
      }
  }

  /** Compiler anchors require complete retained captures and source locations.
    * Test failure identity comes from CNCF's exact case results. A preceding
    * rendered suite header cannot attribute interleaved test output.
    */
  private[admission] def _diagnostics(view: IncrementalSbtFailureDiagnosis.View,
    batch: Option[Vector[SbtValidationMode.CaseResult]] = None): Option[Vector[String]] = {
    val tests = batch.filter(_.forall(result => result.status != "unverified" &&
      (result.status == "not-executed" || result.observation.nonEmpty))).map(
      _.filter(_.status == "failed").flatMap(_.observation).map(o => s"test:${o.suite}:${o.test}").distinct)
      .filter(_.nonEmpty)
    val complete = view.sources.nonEmpty && view.sources.forall(s =>
      s.textStatus == "complete" && s.sourceLoss == "none-observed") &&
      view.omittedGroups == 0 && view.groups.forall(g => g.omittedOccurrences == 0 && g.message.length < 512)
    val identities = view.groups.flatMap { group =>
      group.occurrences.map { occurrence => group.kind match {
        case "compiler-error" => occurrence.location.map(loc =>
          s"compiler:${loc.file}:${loc.line}:${loc.column.getOrElse(0)}:${group.message}")
        // This is a task summary, not an independently identified diagnostic.
        case "compilation-failure" => Some("")
        case _ => None
      }}
    }
    Option.when(complete)(tests).flatten.orElse(Option.when(complete && identities.nonEmpty && identities.forall(_.nonEmpty))(
      identities.flatten.filter(_.nonEmpty).distinct).filter(_.nonEmpty))
  }

  private def _trend(history: Vector[Failure]): Option[(Failure, Option[Trend])] = {
    val window = history.takeRight(GoalPhaseRuntimeRepairControl.window)
    val current = window.lastOption
    current.flatMap { last =>
      val comparable = window.size == GoalPhaseRuntimeRepairControl.window &&
        last.diagnosticPolicy.nonEmpty && window.forall(f =>
          f.scope == last.scope && f.profile == last.profile && f.dispatch == last.dispatch &&
          f.check == last.check && f.kind == last.kind && f.diagnosticPolicy == last.diagnosticPolicy &&
          f.diagnostics.exists(_.nonEmpty)) &&
        window.map(_.continuation).distinct.size == window.size &&
        window.map(_.validation).distinct.size == window.size
      if (!comparable) None else {
        val (policy, limit) = last.diagnosticPolicy.get
        val context = window.foldLeft(GoalPhaseRuntimeRepairControl.Context(policy, limit)) { (c, f) =>
          c.observe(f.check, f.validation, f.diagnostics.get)
        }
        context.stop.map(reason => window(window.size - 2) -> Some(Trend(reason, window.map(_.validation))))
      }
    }
  }

  private def _repeated(previous: Failure, current: Failure): Boolean =
    previous.scope == current.scope && previous.profile == current.profile &&
      previous.dispatch.revision == current.dispatch.revision && previous.dispatch.edges == current.dispatch.edges &&
      previous.continuation != current.continuation && previous.validation != current.validation &&
      previous.check == current.check && previous.kind == current.kind && previous.exit == current.exit &&
      (if (current.kind == "car-lint") current.diagnostics.exists(ds => ds.nonEmpty && previous.diagnostics.contains(ds))
       else previous.candidate == current.candidate)

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
        .forall(GoalPhaseRuntimeRepairControl.exact) && value.previousValidation != value.validation &&
      value.trend != null && value.trend.forall(t => t != null && GoalPhaseRuntimeRepairControl.exact(t.reason) &&
        t.validations != null && t.validations.size == GoalPhaseRuntimeRepairControl.window &&
        t.validations.forall(GoalPhaseRuntimeRepairControl.exact) && t.validations.distinct == t.validations &&
        t.validations.takeRight(2) == Vector(value.previousValidation, value.validation))
}
