package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import scala.util.control.NonFatal
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.protocol.Property
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeRepositoryClosure as R
import GoalPhaseNativeFindingRecording as N
import RepositorySyncManagedGitObservation as G

/** Local completion is a managed result followed by native lifecycle completion.
  * Only the fresh successful caller may attempt its reserved child Start. Reopen
  * projects completion first and passively observes the child's own persistence.
  * @since   Oct. 23, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhasePostCompletionSync {
  val localPolicy = "local-completion-best-effort-sync"
  val strictPolicy = "repository-sync-required"
  val policies = Set(localPolicy, strictPolicy)
  final case class Synchronization(state: String, reason: String, childSessionId: Option[String] = None,
    advanceOperation: Option[String] = None)
  final case class Payload(input: R.Input, policy: String,
    direction: Option[GoalPhaseCompletionPolicyAdoption.Direction], synchronization: Synchronization,
    startReserved: Boolean, findings: N.State, developmentOccurredAt: Option[String] = None)
  private given Encoder[N.Pending] = deriveEncoder
  private given Decoder[N.Pending] = deriveDecoder
  private given Encoder[N.Completed] = deriveEncoder
  private given Decoder[N.Completed] = deriveDecoder
  private given Encoder[N.Journaled] = deriveEncoder
  private given Decoder[N.Journaled] = deriveDecoder
  private given Encoder[N.State] = deriveEncoder
  private given Decoder[N.State] = deriveDecoder
  private given Encoder[Payload] = deriveEncoder[Payload].mapJson(_.mapObject(fields =>
    if (fields("developmentOccurredAt").exists(_.isNull)) fields.remove("developmentOccurredAt") else fields))
  private given Decoder[Payload] = deriveDecoder
  val codec: WorkflowResultJsonV1.PayloadCodec[Payload] = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseLocalCompletionRecord"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-local-completion.v1"),
      "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("Local completion is absent")
      version <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
      _ <- Either.cond(version == "sm-workflow.goal-phase-local-completion.v1" && payload.policy == localPolicy &&
        encode(payload) == wire && payload.synchronization.childSessionId.forall(_exact) &&
        payload.findings != null && payload.findings.pending != null && payload.findings.pending.isEmpty &&
        payload.findings.completed != null && payload.findings.journaled != null &&
        payload.developmentOccurredAt != null && payload.developmentOccurredAt.forall(at => scala.util.Try(java.time.Instant.parse(at)).isSuccess) &&
        (!payload.startReserved || payload.synchronization.childSessionId.nonEmpty), (), "Local completion fields differ")
    } yield payload
  }
  def usesLocalPolicy(session: F.Session, direction: Option[GoalPhaseCompletionPolicyAdoption.Direction]): Boolean =
    direction.nonEmpty || session.started.receipt.payload.value.completionPolicy.contains(localPolicy)

  /** This entry is deliberately before any semantic-history reconstruction or live Git read. */
  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    acknowledge: String => Consequence[Unit]): Consequence[Option[V.GoalPhaseAdvanceResult]] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    saved <- adapter.loadRetainedC(reference(session.current.identity.value))(using core.executionContext)
    result <- saved match {
      case None => Consequence.success(Option.empty[V.GoalPhaseAdvanceResult])
      case Some(value) => for {
        _ <- _validate_saved_c(session, value.receipt.next, value.receipt.payload.value)
        completed <- _complete_c(session, value.receipt.next, d, core, acknowledge)
        observed <- _observe_c(session, d, core, value.receipt.payload.value)
      } yield Some(_output(completed, d, core, value.receipt.payload.value, observed._1, observed._2))
    }
  } yield result

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, direction: Option[GoalPhaseCompletionPolicyAdoption.Direction],
    legacy: Option[R.Payload], findings: N.State,
    acknowledge: String => Consequence[Unit]): Consequence[V.GoalPhaseAdvanceResult] = for {
    _ <- if (usesLocalPolicy(session, direction) && from == session.current &&
        from.lifecycle == Lifecycle.Active && from.suspension.isEmpty) Consequence.unit
      else F.invalid("Local completion requires its actual adopted or newly captured policy and current frontier")
    _ <- if (findings != null && findings.pending != null && findings.pending.isEmpty)
      Consequence.unit else F.invalid("Local completion requires all accepted review observations to reach normal recording or Journal outcomes")
    current <- R.inputC(from, controller)
    selected = direction.map(_.target.input).orElse(legacy.map(_.input)).getOrElse(current)
    _ <- if (selected.plan == current.plan && selected.base == current.base && selected.completed == current.completed &&
        selected.candidate == current.candidate && selected.review == current.review && selected.reviewedCandidate == current.reviewedCandidate)
      Consequence.unit else F.invalid("Local completion crosses the original accepted closure input")
    output <- if (!_committed(controller)) F.waitC(from, "Phase recording commit remains outstanding",
      "The retained candidate tree is not the accepted committed Step tip. Complete the Phase-owned commit explicitly; no RepositorySync Start or work replay occurs.")
    else for {
      closed <- controller.acceptC(O.PhaseLocallyCompleted(selected.candidate, selected.completed.map(_.commit), selected.review))
      _ <- if (closed.stage == O.Stage.Closed && domain.statemachine.GoalPhaseRunLifecycle.permits(
          "PhaseClosing", "phase-locally-completed", "Closed")) Consequence.unit
        else F.invalid("Local completion differs from its CML/controller")
      synchronization <- legacy.map(value => Consequence.success((Synchronization("unknown",
        "The existing child retains its own effects and continuation; it is never started again.", Some(value.childSessionId),
        Some("advanceRepositorySync")), false))).getOrElse(_select_c(session, d, core, selected))
      payload = Payload(selected, localPolicy, direction, synchronization._1, synchronization._2, findings,
        d.developmentObservation.map(_ => core.executionContext.clock.instant().toString))
      adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(from.identity.value), from,
        from.revision, F.history(from, "phase.Closed", "ordered-local-completion:" + from.identity.value),
        TypedValue(codec.typeIdentity, payload)), ConsequenceT.pure[[X] =>>
          org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
      _ <- acknowledge("goal-local-completion-recorded")
      completed <- _complete_c(session, saved.receipt.next, d, core, acknowledge)
      // Every error above is a real completion failure. Follow-up observations are best effort.
      failure = if (!payload.startReserved) None else try {
        acknowledge("goal-local-before-child-start").flatMap(_ => _start_child_c(d, core, payload, acknowledge))
          .map(_ => Option.empty[String]).fold(error => Some(error.toString), identity)
      } catch { case NonFatal(error) => Some(error.toString) }
      observed <- _observe_c(session, d, core, payload)
      synchronization = failure.map(error => payload.synchronization.copy(state = "unknown",
        reason = "Child Start or Advance did not return confirmed completion: " + error)).getOrElse(observed._1)
    } yield _output(completed, d, core, payload, synchronization, if (failure.isEmpty) observed._2 else None)
  } yield output

  private def _committed(controller: O.State): Boolean = controller.candidate.contains(controller.tipSnapshot) &&
    controller.phaseReviewedCandidate.isEmpty && controller.phaseRecordingPaths.isEmpty

  /** A negative safety selection never stages, commits, stashes or requests a new grant. */
  private def _select_c(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    input: R.Input): Consequence[(Synchronization, Boolean)] = {
    def _deferred_(reason: String) = Consequence.success(Synchronization("deferred", reason) -> false)
    d.repositoryClosure match {
      case None => _deferred_("Paired RepositorySync installation is absent.")
      case Some(installation) =>
        try {
        val selected = for {
          _ <- installation.validateC(d)
          authority <- installation.authority.isGrantedC(core, input)
          choice <- if (!authority) _deferred_("No current independently granted Phase closure authority; synchronization was not attempted.")
          else for {
            root <- G.rootC(core)
            actual <- G.inspectC(core)
            reason = if (root != session.started.receipt.payload.value.root || actual.observed.branch != session.started.receipt.payload.value.branch)
              Some("Actual repository root or branch differs from the accepted Phase target.")
              else if (actual.mergeHead.nonEmpty || actual.observed.dirty.nonEmpty)
                Some("Modified, untracked or unresolved work would enter FULL_STATE preservation; synchronization was not attempted.")
              else if (actual.observed.head != input.candidate.head || actual.observed.tree != input.candidate.tree)
                Some("Current HEAD or tree differs from the accepted committed candidate, including a separately accepted descendant; synchronization was not attempted.")
              else None
            safe <- reason match {
              case Some(value) => _deferred_(value)
              case None => Consequence.success(Synchronization("unknown",
                "A single child Start identity is reserved after durable Phase completion; execution is not yet confirmed.",
                Some(core.executionContext.idGeneration.opaqueId("sm-workflow-session")), Some("advanceRepositorySync")) -> true)
            }
          } yield safe
        } yield choice
        selected.recoverWith(error => _deferred_("Safe synchronization selection is unavailable: " + error.toString))
        } catch { case NonFatal(error) => _deferred_("Safe synchronization selection interrupted: " + error.toString) }
    }
  }
  private def _start_child_c(d: F.Declaration, core: ActionCall.Core, payload: Payload,
    acknowledge: String => Consequence[Unit]): Consequence[Unit] = for {
    installation <- d.repositoryClosure.map(Consequence.success).getOrElse(F.invalid("Paired sync is unavailable"))
    _ <- R.invokeC[V.RepositorySyncStartResult](core, RepositorySyncManagedFetch.startOperation.operation,
      List(Property("input", installation.declaration.input, None), Property("sessionId", payload.synchronization.childSessionId.get, None)))
    _ <- acknowledge("goal-local-after-child-start")
    _ <- R.invokeC[V.RepositorySyncAdvanceResult](core, RepositorySyncManagedFetch.advanceOperation.operation,
      List(Property("sessionId", payload.synchronization.childSessionId.get, None)))
  } yield ()

  private def _observe_c(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    payload: Payload): Consequence[(Synchronization, Option[V.RepositorySyncNativeConvergenceResult])] =
    payload.synchronization.childSessionId match {
      case None => Consequence.success(payload.synchronization -> None)
      case Some(identity) =>
        def _unknown_(reason: String) = Consequence.success(payload.synchronization.copy(state = "unknown", reason = reason) ->
          Option.empty[V.RepositorySyncNativeConvergenceResult])
        try RepositorySyncPostCompletionObservation.observeC(session.storage, d, core, identity).recoverWith(
          error => _unknown_("Saved child observation is unavailable: " + error.toString))
        catch { case NonFatal(error) => _unknown_("Saved child observation interrupted: " + error.toString) }
    }
  private def _validate_saved_c(session: F.Session, saved: InstanceRecord, payload: Payload): Consequence[Unit] = for {
    _ <- codec.decode(codec.encode(payload)).fold(F.invalid, _ => Consequence.unit)
    predecessor <- GoalPhaseNativeHistory.prefixC(session.current, InstanceRevision(saved.revision.value - 1))
    actual <- GoalPhaseNativeHistory.prefixC(session.current, saved.revision)
    _ <- if (actual == saved && payload.input.instance == saved.identity.value &&
        payload.input.plan.phase == session.started.receipt.payload.value.plan.phase &&
        payload.input.plan.repository == session.started.receipt.payload.value.plan.repository &&
        predecessor.lifecycle == Lifecycle.Active && predecessor.suspension.isEmpty &&
        predecessor.currentProgression.contains(ProgressionReference("phase.PhaseClosing")) &&
        payload.input.sourceRevision.toLongOption.exists(_ <= predecessor.revision.value) &&
        saved.causation.value == "ordered-local-completion:" + saved.identity.value &&
        (session.started.receipt.payload.value.completionPolicy.contains(localPolicy) || payload.direction.nonEmpty)) Consequence.unit
      else F.invalid("Local completion has no actual selected PhaseClosing predecessor")
  } yield ()
  private def _complete_c(session: F.Session, saved: InstanceRecord, d: F.Declaration, core: ActionCall.Core,
    acknowledge: String => Consequence[Unit]): Consequence[InstanceRecord] = for {
    instances <- session.storage.bindInstancesC()(using core.executionContext)
    current <- instances.loadC(saved.identity)(using core.executionContext).flatMap(
      _.map(Consequence.success).getOrElse(F.invalid("Local completion instance is absent")))
    entry <- completionEntryC(saved, current)
    completed <- entry match {
      case None => Consequence.success(current)
      case Some(value) => for {
        adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
        original <- adapter.loadRetainedC(reference(saved.identity.value))(using core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(F.invalid("Local completion payload is absent")))
        events <- GoalPhaseDevelopmentEvents.recordsC(d.developmentObservation, saved.identity.value,
          value.revision.value, "GoalPhaseCompleted", value.causation.value, core,
          original.receipt.payload.value.developmentOccurredAt.map(java.time.Instant.parse))
        committed <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration,
          WorkflowMechanicalTransition.Reference(saved.identity.value + ":ordered-local-terminal", "1"),
          saved, saved.revision, value, TypedValue(codec.typeIdentity, original.receipt.payload.value)),
          ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()), events)(using core.executionContext)
        _ <- acknowledge("goal-local-completed")
      } yield committed.receipt.next
    }
  } yield completed
  /** Admit only the exact missing completion or its actual already saved successor. */
  private[admission] def completionEntryC(saved: InstanceRecord, current: InstanceRecord): Consequence[Option[HistoryEntry]] = for {
    _ <- WorkflowInstancePersistence.validateC(saved)
    _ <- WorkflowInstancePersistence.validateC(current)
    _ <- if (saved.lifecycle == Lifecycle.Active && saved.suspension.isEmpty &&
        saved.currentProgression.contains(ProgressionReference("phase.Closed")) && saved.identity == current.identity &&
        saved.definition == current.definition && saved.causation.value == "ordered-local-completion:" + saved.identity.value)
      Consequence.unit else F.invalid("Local completion does not own the selected native record")
    entry = F.history(saved, "phase.Closed", "ordered-local-completed:" + saved.identity.value)
      .copy(lifecycle = Lifecycle.Completed, currentProgression = None)
    next <- saved.appendC(saved.revision, entry)
    result <- if (current == saved) Consequence.success(Some(entry))
      else if (current == next) Consequence.success(Option.empty[HistoryEntry])
      else F.invalid("Local completion differs from its exact saved native frontier")
  } yield result
  private def _output(completed: InstanceRecord, declaration: F.Declaration, core: ActionCall.Core,
    payload: Payload, synchronization: Synchronization,
    repository: Option[V.RepositorySyncNativeConvergenceResult]): V.GoalPhaseAdvanceResult = {
    val observation = declaration.evidencePolicy.map { policy =>
      try CommonEvidenceLifecycle.completedC(CommonEvidence.storage(core, policy), declaration.input.repositoryIdentity,
        declaration.input.phaseIdentity, completed, core.executionContext.clock)
      catch { case NonFatal(_) => Consequence.stateConflict("Evidence completion observation interrupted") }
    }
    val result = NativeWorkflowOperationOutput.withFindingRecording(NativeWorkflowOperationOutput.goalLocallyComplete(completed.identity.value,
      payload.input.plan, payload.input.base, payload.input.completed,
      payload.input.reviewedCandidate.getOrElse(payload.input.candidate), synchronization, repository), payload.findings)
    if (observation.exists(!_.isSuccess)) result.copy(message = result.message +
      " Evidence completion/lifecycle observation is unavailable; completed work remains closed.") else result
  }
  private[admission] def reference(identity: String) = WorkflowMechanicalTransition.Reference(identity + ":ordered-local-completion", "1")
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
}
