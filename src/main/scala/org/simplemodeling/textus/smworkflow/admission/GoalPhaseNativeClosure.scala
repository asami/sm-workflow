package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhaseClosureRecovery as R
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E
import GoalPhasePartialCommitClosure as P

/** Native ordered Step closure, not a legacy Step Terminal. Host authorization
  * and fixed ProcessProgram grants are separate from AI review disposition.
  * Durable effect intent precedes commit; external Git is not rolled back with
  * storage and an intent without a recorded result is never automatically rerun.
  * @since   Oct.  6, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeClosure {
  final case class StepProgram(step: String, ordinal: Int)
  final case class Policy(identity: String, revision: Int, programs: Vector[StepProgram]) {
    def validateC: Consequence[Unit] = if (id(identity) && revision > 0 && programs != null &&
        programs.nonEmpty && programs.size <= 64 && programs.forall(p => p != null && id(p.step) &&
          p.ordinal > 0 && p.ordinal <= 64) && programs.map(_.step).distinct.size == programs.size &&
        programs.map(_.ordinal).distinct.size == programs.size) Consequence.unit
      else F.invalid("native Step closure host policy is incomplete")
  }
  final case class Input(instance: String, sourceRevision: String, plan: String, planRevision: String,
    step: String, generation: Long, parent: O.Snapshot, candidate: O.Snapshot, reviewEvidence: Option[String],
    admission: Option[GoalPhaseStepAssessment.Admission] = None)
  object Input {
    def apply(instance: String, sourceRevision: String, plan: String, planRevision: String,
      step: String, generation: Long, parent: O.Snapshot, candidate: O.Snapshot, reviewEvidence: String): Input =
      new Input(instance, sourceRevision, plan, planRevision, step, generation, parent, candidate, Some(reviewEvidence))
    given Encoder[Input] = io.circe.generic.semiauto.deriveEncoder[Input].mapJson(_.mapObject(_.filter {
      case ("admission", value) => !value.isNull
      case _ => true
    }))
    given Decoder[Input] = io.circe.generic.semiauto.deriveDecoder
  }
  trait AuthorityPort {
    /** Observe host-recorded authority for this actual current call. Never
      * synthesize approval from a worker result or a missing record.
      */
    def isGrantedC(core: ActionCall.Core, input: Input): Consequence[Boolean]
  }
  final case class Installation(policy: Policy, authority: AuthorityPort) {
    def validateC = if (policy == null || authority == null) F.invalid("native Step closure authority is missing")
      else policy.validateC
  }
  final case class Precheck(findings: Vector[String], eofPaths: Vector[String], eofOnly: Boolean)
  final case class Change(path: String, mode: String, oldBlob: String, newBlob: String)
  final case class Payload(input: Input, ordinal: Int, commit: Option[O.Commit], attempt: Int = 0,
    check: Option[Precheck] = None, changes: Vector[Change] = Vector.empty,
    partialCompletion: Option[O.StepCommitChain] = None)
  object Payload {
    given Encoder[Payload] = Encoder.instance { value =>
      Json.obj((Vector("input" -> value.input.asJson, "ordinal" -> value.ordinal.asJson,
        "commit" -> value.commit.asJson) ++
        (if (value.attempt == 0) Vector.empty else Vector("attempt" -> value.attempt.asJson)) ++
        value.check.toVector.map(actual => "check" -> actual.asJson) ++
        (if (value.changes.isEmpty) Vector.empty else Vector("changes" -> value.changes.asJson)) ++
        value.partialCompletion.toVector.map(chain => "partialCompletion" -> chain.asJson))*)
    }
    given Decoder[Payload] = Decoder.instance { cursor =>
      for {
        input <- cursor.get[Input]("input")
        ordinal <- cursor.get[Int]("ordinal")
        commit <- cursor.get[Option[O.Commit]]("commit")
        attempt <- cursor.value.asObject.flatMap(_("attempt")).fold[Decoder.Result[Int]](Right(0))(_.as[Int])
        check <- cursor.value.asObject.flatMap(_("check")) match {
          case None => Right(Option.empty[Precheck])
          case Some(actual) => actual.as[Precheck].map(Some(_))
        }
        changes <- cursor.value.asObject.flatMap(_("changes")).fold[Decoder.Result[Vector[Change]]](Right(Vector.empty[Change]))(_.as[Vector[Change]])
        chain <- cursor.get[Option[O.StepCommitChain]]("partialCompletion")
        _ <- Either.cond(attempt >= 0 && check.forall(_valid_precheck) && _valid_changes(changes) &&
          !cursor.downField("partialCompletion").focus.exists(_.isNull) &&
          chain.forall(c => O.validStepCommitChain(c) && c.originalParent == input.parent && commit.contains(c.completion)), (),
          io.circe.DecodingFailure("invalid native Step closure attempt facts", cursor.history))
      } yield Payload(input, ordinal, commit, attempt, check, changes, chain)
    }
  }
  final case class Saved(record: InstanceRecord, payload: Payload, controller: O.State,
    execution: Option[R.Execution] = None)
  private[admission] final case class Attempt(check: Option[Saved], intent: Option[Saved],
    execution: Option[R.ExecutionSaved] = None, direction: Option[R.DirectionSaved] = None,
    continuation: Option[R.ContinuationSaved] = None)
  private[admission] final case class Progress(record: InstanceRecord, failed: Vector[Saved],
    nextAttempt: Option[Int], pending: Option[Saved], execution: Option[R.ExecutionSaved] = None,
    direction: Option[R.DirectionSaved] = None, recovery: Option[R.DirectionSaved] = None,
    continuation: Option[R.ContinuationSaved] = None, automatic: Option[R.ContinuationSaved] = None,
    completionIntent: Option[(InstanceRecord, P.Intent)] = None,
    completionExecution: Option[(InstanceRecord, P.Execution)] = None)
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseNativeStepClosureReceipt"
    def encode(value: Payload) = Json.obj("schemaVersion" -> Json.fromString(
      if (value.partialCompletion.nonEmpty) "sm-workflow.goal-phase-native-step-closure.v4"
      else "sm-workflow.goal-phase-native-step-closure.v3"),
      "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("native Step closure is missing")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(Set("sm-workflow.goal-phase-native-step-closure.v3", "sm-workflow.goal-phase-native-step-closure.v4")(schema), (), "native Step closure schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
      _ <- Either.cond((schema.endsWith(".v4") && payload.partialCompletion.nonEmpty) ||
        (schema.endsWith(".v3") && payload.partialCompletion.isEmpty), (), "native Step closure schema and chain differ")
    } yield payload
  }

  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State): Consequence[Saved] =
    retainedOptionC(session, d, core, from, controller).flatMap(_.map(Consequence.success).getOrElse(
      F.invalid("Step commit result is not recorded")))

  def retainedOptionC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State): Consequence[Option[Saved]] = for {
    store <- _store_c(session, d, core, from, controller, _ => Consequence.unit)
    result <- store.loadC("committed")
    saved <- result match {
      case Some(value) => store.completedC(value).map(Some(_))
      case None => for {
        progress <- store.progressC
        _ <- if (progress.nextAttempt.nonEmpty) store.latestC(progress.record) else Consequence.unit
      } yield if (progress.nextAttempt.nonEmpty || progress.direction.nonEmpty || progress.continuation.nonEmpty ||
          progress.execution.exists(saved => R.terminalExecution(saved.payload)))
        Some(Saved(progress.record, progress.pending.map(_.payload).orElse(progress.failed.lastOption.map(_.payload))
          .getOrElse(Payload(store.input, store.ordinal, None)), controller,
          progress.completionExecution.map(_._2.result).orElse(progress.execution.map(_.payload)))) else None
    }
  } yield saved

  /** Recording Submit reconstructs canonical history without observing live Git. */
  def retainedRecordingHistoryOptionC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State): Consequence[Option[Saved]] = for {
    store <- _store_c(session, d, core, from, controller, _ => Consequence.unit)
    result <- store.loadC("committed")
    saved <- result match {
      case Some(value) => store.completedRecordingHistoryC(value).map(Some(_))
      case None => for {
        progress <- store.recordingProgressC
      } yield if (progress.nextAttempt.nonEmpty || progress.direction.nonEmpty || progress.continuation.nonEmpty)
        Some(Saved(progress.record, progress.pending.map(_.payload).orElse(progress.failed.lastOption.map(_.payload))
          .getOrElse(Payload(store.input, store.ordinal, None)), controller)) else None
    }
  } yield saved

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State, acknowledge: String => Consequence[Unit],
    dependencyPreconditions: () => Consequence[Unit] = () => Consequence.unit): Consequence[Option[Saved]] = for {
    _ <- dependencyPreconditions()
    store <- _store_c(session, d, core, from, controller, acknowledge, dependencyPreconditions)
    result <- store.loadC("committed")
    saved <- result match {
      case Some(value) => store.completedC(value).map(Some(_))
      case None => for {
        retained <- store.progressC
        _ <- store.latestC(retained.record)
        progress <- store.continueC(retained)
        closed <- if (progress.direction.exists(_.payload.choice == "CompletePartialCommit"))
          store.completePartialC(progress).map(Some(_))
        else if (progress.direction.exists(_.payload.choice == "ReconcileCommit") ||
            progress.continuation.exists(_.payload.action == "reconcile-commit"))
          store.reconcileC(progress).map(Some(_))
        else if (progress.nextAttempt.isEmpty) progress.pending.map(saved => Consequence.success(Some(
          saved.copy(record = progress.record, execution = progress.execution.map(_.payload)))))
          .getOrElse(F.invalid("Step closure has no actual pending attempt"))
        else for {
        _ <- store.recheckRecoveryC(progress)
        authority <- store.installation.authority.isGrantedC(core, store.input)
        result <- authority match {
          case false => Consequence.success(Option.empty[Saved])
          case true => for {
            _ <- store.preconditionsC
            differences <- E.goalIndexChangesC(core, store.ordinal, store.input.candidate.tree)
            changes <- store.admitChangesC(progress.failed, differences)
            actual <- E.checkGoalIndexC(core, store.ordinal, store.input.parent.head)
            _ <- if (actual != null && actual.findingKeys != null && actual.eofBlankLinePaths != null &&
                actual.eofBlankLinePaths.forall(_ != null)) Consequence.unit
              else F.invalid("selected Step precheck has no complete typed finding facts")
            check = Precheck(actual.findingKeys.toVector.sorted, actual.eofBlankLinePaths.toVector.map(_.value).sorted,
              actual.onlyEofBlankLines)
            _ <- if (_valid_precheck(check) && (check.findings.isEmpty || changes.isEmpty)) Consequence.unit
              else F.invalid("nonclean selected check cannot retain a changed reviewed candidate")
            attempt = progress.nextAttempt.get
            payload = Payload(store.input, store.ordinal, None, attempt, Some(check), changes)
            checked <- store.saveC(_attempt_key("check-result", attempt), progress.record, "Closing", payload)
            saved <- if (check.findings.nonEmpty) Consequence.success(Saved(checked, payload, controller))
              else store.commitC(checked, payload)
          } yield Some(saved)
        }
        } yield result
      } yield closed
    }
  } yield saved

  def recoveryProgressC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State): Consequence[(Input, Int, Progress)] = for {
    store <- _store_c(session, d, core, from, controller, _ => Consequence.unit)
    committed <- store.loadC("committed")
    _ <- if (committed.isEmpty) Consequence.unit else F.invalid("completed Step closure has no unresolved recovery target")
    progress <- store.recordingProgressC
  } yield (store.input, store.ordinal, progress)

  def executionMessage(value: R.Execution): String = "Actual selected commit outcome: " + value.outcome +
    "; termination: " + value.termination.getOrElse("unknown") + "; exitCode: " + value.exitCode.fold("unknown")(_.toString) +
    ". " + value.stderr + (if (R.terminalExecution(value))
      " Ordinary Advance checks the exact actual Git state and existing Step closure authority for bounded continuation."
      else " Explicit public closure recovery is required; no automatic replay.")
  def waitMessageC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State): Consequence[String] = for {
    store <- _store_c(session, d, core, from, controller, _ => Consequence.unit)
    progress <- store.recordingProgressC
  } yield progress.completionIntent.fold(progress.execution.map(saved => executionMessage(saved.payload)).getOrElse(
    "Step commit has an unresolved clean check or original unknown intent; explicit public closure recovery is required. No automatic replay.")
    )(_ => progress.completionExecution.fold(
      "Partial completion intent is UNKNOWN. The effect is never replayed; completion remains unresolved/Defer.") { (_, result) =>
      "Partial completion actual outcome: " + result.result.outcome + "; termination: " +
        result.result.termination.getOrElse("unknown") + "; exitCode: " + result.result.exitCode.fold("unknown")(_.toString) +
        ". " + result.result.stderr + " No automatic retry is authorized; completion remains unresolved/Defer."
    })

  /** The owning Closing frontier supplies the same target to authority lookup
    * and the public missing-authority Wait projection. */
  def inputC(from: InstanceRecord, controller: O.State): Consequence[Input] = for {
    _ <- if (controller.stage == O.Stage.Closing && controller.cursor < controller.plan.steps.size &&
        controller.candidate.nonEmpty && controller.checksReceipt.nonEmpty &&
        O.validStepAcceptance(O.ClosedStep(controller.plan.steps(controller.cursor).identity,
          O.Commit("", "", ""), controller.reviewEvidence, controller.stepAdmission)) &&
        from.currentProgression.contains(ProgressionReference("phase.Closing")))
      Consequence.unit else F.invalid("Step closure has no actual accepted review or formal Step admission at native Closing")
    _ <- if (controller.stepAdmission.forall(a => a.assessment.candidate == controller.candidate.get &&
        controller.checksReceipt.contains(a.assessment.validationReference))) Consequence.unit
      else F.invalid("formal Step admission differs from the exact closure candidate or checks")
    step = controller.plan.steps(controller.cursor)
  } yield Input(from.identity.value, from.revision.value.toString, controller.plan.identity, controller.plan.revision,
    step.identity, controller.generation, controller.candidateBase, controller.candidate.get, controller.reviewEvidence, controller.stepAdmission)

  private def _store_c(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State, acknowledge: String => Consequence[Unit],
    dependencyPreconditions: () => Consequence[Unit] = () => Consequence.unit): Consequence[Store] = for {
    input <- inputC(from, controller)
    installation <- d.closureInstallation.map(Consequence.success).getOrElse(F.invalid("Step closure installation is missing"))
    _ <- installation.validateC
    step = controller.plan.steps(controller.cursor)
    ordinal <- installation.policy.programs.find(_.step == step.identity).map(p => Consequence.success(p.ordinal))
      .getOrElse(F.invalid("Step has no host-fixed commit program"))
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
  } yield new Store(adapter, session, d, core, from, controller, installation, ordinal, input, acknowledge, dependencyPreconditions)

  private final class Store(adapter: WorkflowManagedMechanicalTransition[Payload], session: F.Session,
    d: F.Declaration, core: ActionCall.Core, from: InstanceRecord, controller: O.State,
    val installation: Installation, val ordinal: Int, val input: Input, acknowledge: String => Consequence[Unit],
    dependencyPreconditions: () => Consequence[Unit]) {
    private val _owned_paths = controller.plan.steps(controller.cursor).ownedPaths
    private def _intent_identity(attempt: Int) = reference(_attempt_key("intent", attempt)).identity
    def reference(key: String) = WorkflowMechanicalTransition.Reference(from.identity.value +
      ":ordered-step-closure:" + from.revision.value + ":" + key, "1")
    private def _actual_record_c: Consequence[InstanceRecord] = for {
      instances <- session.storage.bindInstancesC()(using core.executionContext)
      current <- instances.loadC(from.identity)(using core.executionContext).flatMap(
        _.map(Consequence.success).getOrElse(F.invalid("retained Step closure instance is absent")))
      _ <- current.validateC
    } yield current
    def latestC(record: InstanceRecord): Consequence[Unit] = _actual_record_c.flatMap { actual =>
      if (actual == record) Consequence.unit
      else F.invalid("Step closure retry omits actual later native history")
    }
    def loadC(key: String): Consequence[Option[Saved]] =
      adapter.loadRetainedC(reference(key))(using core.executionContext).flatMap {
        case None => Consequence.success(None)
        case Some(value) => for {
          current <- _actual_record_c
          admitted <- GoalPhaseNativeHistory.prefixC(current, value.receipt.next.revision)
          _ <- if (admitted == value.receipt.next && admitted.identity == from.identity && admitted.definition == from.definition)
            Consequence.unit else F.invalid("retained Step closure is not its full actual native history prefix")
        } yield Some(Saved(admitted, value.receipt.payload.value, controller))
      }
    def progressC: Consequence[Progress] = _scan_c(0, Vector.empty, _verify_retained_changes_c).flatMap(_partial_progress_c)
    def recordingProgressC: Consequence[Progress] = _scan_c(0, Vector.empty, (_, _) => Consequence.unit).flatMap(_partial_progress_c)
    private def _partial_progress_c(progress: Progress): Consequence[Progress] = for {
      intent <- R.loadC(session, core, input, "partial-completion-intent", progress.pending.map(_.payload.attempt).getOrElse(0), P.intentCodec)
      execution <- R.loadC(session, core, input, "partial-completion-execution", progress.pending.map(_.payload.attempt).getOrElse(0), P.executionCodec)
      result <- partialProgressC(progress, intent, execution)
    } yield result
    private def _scan_c(attempt: Int, attempts: Vector[Attempt],
      verify: (Saved, Progress) => Consequence[Unit]): Consequence[Progress] = for {
      check <- loadC(_attempt_key("check-result", attempt))
      intent <- loadC(_attempt_key("intent", attempt))
      execution <- R.loadC(session, core, input, "execution-result", attempt, R.executionCodec)
      direction <- R.loadC(session, core, input, "recovery", attempt, R.directionCodec)
      continuation <- R.loadC(session, core, input, "continuation", attempt, R.continuationCodec)
      examined = attempts :+ Attempt(check, intent, execution.map((record, payload) => R.ExecutionSaved(record, payload)),
        direction.map((record, payload) => R.DirectionSaved(record, payload)),
        continuation.map((record, payload) => R.ContinuationSaved(record, payload)))
      progress <- reconcileAttemptsC(from, input, ordinal, examined)
      _ <- progress.execution.fold(Consequence.unit) { saved =>
        if (saved.payload.selectedPaths.forall(path => _owned_paths.exists(covers(_, path)))) Consequence.unit
        else F.invalid("retained actual commit execution crosses current Step ownership")
      }
      _ <- progress.recovery.fold(Consequence.unit) { saved =>
        if (saved.payload.target.participant == session.participantIdentity &&
            saved.payload.target.ownedPaths == _owned_paths && saved.payload.target.observation.root == d.target.root.toString &&
            saved.payload.target.observation.branch == d.target.localBranch) Consequence.unit
        else F.invalid("retained closure recovery crosses its actual participant, ownership or installed target")
      }
      _ <- progress.automatic.fold(Consequence.unit) { saved =>
        if (saved.payload.target.participant == session.participantIdentity &&
            saved.payload.target.ownedPaths == _owned_paths && saved.payload.target.observation.root == d.target.root.toString &&
            saved.payload.target.observation.branch == d.target.localBranch) Consequence.unit
        else F.invalid("retained closure continuation crosses its actual participant, ownership or installed target")
      }
      _ <- check.fold(Consequence.unit) { saved => for {
        _ <- if (saved.payload.changes.forall(change => _owned_paths.exists(covers(_, change.path))) &&
            saved.payload.check.forall(_.eofPaths.forall(path => _owned_paths.exists(covers(_, path)))))
          Consequence.unit else F.invalid("retained correction crosses current Step ownership")
        _ <- verify(saved, progress)
      } yield () }
      result <- if ((check.nonEmpty || intent.nonEmpty) && progress.nextAttempt.nonEmpty)
        _scan_c(progress.nextAttempt.get, examined, verify) else Consequence.success(progress)
    } yield result
    private def _verify_retained_changes_c(saved: Saved, progress: Progress): Consequence[Unit] = for {
      actual <- _typed_changes_c(saved.payload.changes)
      admitted <- admitChangesC(progress.failed.filter(_.payload.attempt < saved.payload.attempt), actual)
      _ <- if (admitted == saved.payload.changes) Consequence.unit
        else F.invalid("retained correction rewrites the admitted native changes")
    } yield ()
    def saveC(key: String, current: InstanceRecord, stage: String, payload: Payload): Consequence[InstanceRecord] = for {
      _ <- if (current.lifecycle == Lifecycle.Active && current.suspension.isEmpty &&
          current.currentProgression.contains(ProgressionReference("phase.Closing"))) Consequence.unit
        else F.invalid("Step commit persistence has no native Closing predecessor")
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(key), current, current.revision,
        F.history(current, "phase." + stage, "ordered-step-closure:" + key), TypedValue(codec.typeIdentity, payload)),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield saved.receipt.next
    def admitChangesC(failed: Vector[Saved], changes: Vector[GitTreeChange]): Consequence[Vector[Change]] = for {
      blobs <- _blobs_c(changes)
      admitted <- admitEofCorrectionC(input, _owned_paths, failed.flatMap(_.payload.check), changes, blobs)
    } yield admitted
    private def _blobs_c(changes: Vector[GitTreeChange]): Consequence[Map[String, GitBlobContent]] = {
      val identities = Option(changes).toVector.flatten.filter(_ != null).flatMap(change =>
        Option(change.oldBlob).toVector.flatten ++ Option(change.newBlob).toVector.flatten).filter(_ != null).distinct
      identities.foldLeft(Consequence.success(Map.empty[String, GitBlobContent])) { (prior, blob) => for {
        saved <- prior
        actual <- E.executeGitC(core, GitOperation.BlobContent(blob)).flatMap(_.valueC)
      } yield saved.updated(blob.value, actual) }
    }
    private def _tree_changes_c(tree: String): Consequence[Vector[GitTreeChange]] = for {
      before <- GitObjectId.parseC(input.candidate.tree)
      after <- GitObjectId.parseC(tree)
      actual <- E.executeGitC(core, GitOperation.TreeChanges(before, after)).flatMap(_.valueC)
    } yield actual
    private def _verified_commit_c(payload: Payload, failed: Vector[Saved]): Consequence[Unit] = for {
      native <- G.commitC(core, payload.commit.get.identity)
      _ <- payload.partialCompletion match {
        case None => if (native.identity != input.parent.head && native.parents == Vector(input.parent.head) &&
          native.tree == payload.commit.get.tree && payload.commit.get.parent == input.parent.head)
        Consequence.unit else F.invalid("actual Step commit differs from its native identity or original parent")
        case Some(chain) => for {
          parent <- G.commitC(core, chain.originalParent.head)
          partial <- G.commitC(core, chain.partial.identity)
          _ <- if (O.validStepCommitChain(chain) && chain.originalParent == input.parent &&
              parent.tree == input.parent.tree && partial.tree == chain.partial.tree &&
              partial.parents == Vector(input.parent.head) && native.parents == Vector(partial.identity) &&
              native.tree == chain.completion.tree && payload.commit.contains(chain.completion)) Consequence.unit
            else F.invalid("actual partial completion differs from native P -> C -> D metadata")
        } yield ()
      }
      actual <- _tree_changes_c(native.tree)
      admitted <- admitChangesC(failed, actual)
      _ <- if (admitted == payload.changes && (payload.changes.nonEmpty || native.tree == input.candidate.tree))
        Consequence.unit else F.invalid("actual Step commit differs from its exact reviewed tree or bounded EOF correction")
    } yield ()
    private def _closed_state_c(payload: Payload): Consequence[O.State] = for {
      entered <- controller.acceptC(O.ClosureIntentSaved(input.candidate, _intent_identity(payload.attempt)))
      next <- if (payload.partialCompletion.nonEmpty)
        entered.acceptC(O.StepPartialCompletionObserved(_intent_identity(payload.attempt), payload.partialCompletion.get,
          payload.commit.get.tree, payload.changes.map(_.path)))
      else if (payload.changes.isEmpty)
        entered.acceptC(O.StepCommitObserved(_intent_identity(payload.attempt), payload.commit.get))
        else entered.acceptC(O.StepEofCorrectionCommitObserved(_intent_identity(payload.attempt),
          payload.commit.get, payload.changes.map(_.path)))
    } yield next
    def completedC(result: Saved): Consequence[Saved] = for {
      progress <- progressC
      saved <- _completed_c(result, progress)
      _ <- _verified_commit_c(result.payload, progress.failed)
    } yield saved
    def completedRecordingHistoryC(result: Saved): Consequence[Saved] = for {
      progress <- recordingProgressC
      saved <- _completed_c(result, progress)
    } yield saved
    private def _completed_c(result: Saved, progress: Progress): Consequence[Saved] = for {
      pending <- progress.pending.filter(saved => saved.record.causation.value ==
        "ordered-step-closure:" + _attempt_key("intent", saved.payload.attempt))
        .map(Consequence.success).getOrElse(F.invalid("completed Step has no exact pending commit intent"))
      _ <- if (result != null && result.payload != null && result.payload.commit.nonEmpty &&
          result.payload.copy(commit = None, partialCompletion = None) == pending.payload) Consequence.unit
        else F.invalid("retained Step closure changes its exact pending payload")
      _ <- completedPartialC(result.payload, progress)
      next <- _closed_state_c(result.payload)
      _ <- _successor_c(progress.record, result.record, "committed", next.stage.toString)
    } yield Saved(result.record, result.payload, next, progress.execution.map(_.payload))
    def continueC(progress: Progress): Consequence[Progress] = {
      if (progress.nextAttempt.nonEmpty || progress.direction.nonEmpty || progress.continuation.nonEmpty ||
          !progress.execution.exists(saved => R.terminalExecution(saved.payload))) Consequence.success(progress)
      else for {
        target <- R.currentTargetC(session, d, core, from, controller)
        granted <- installation.authority.isGrantedC(core, input)
        next <- if (!granted || R.continuationAction(target).isEmpty) Consequence.success(progress)
          else for {
            _ <- R.continueC(session, d, core, target)
            saved <- progressC
            _ <- latestC(saved.record)
          } yield saved
      } yield next
    }
    def recheckRecoveryC(progress: Progress): Consequence[Unit] = for {
      _ <- progress.recovery.fold(Consequence.unit) { saved => for {
      actual <- R.observeC(d, core, input, ordinal)
      _ <- if (actual == saved.payload.target.observation) Consequence.unit
        else F.invalid("closure recovery Git observation or physical selection drifted before its new effect")
      _ <- R.choiceC(saved.payload.target, saved.payload.choice)
      _ <- if (saved.payload.choice != "CompletePartialCommit") Consequence.unit else for {
        proof <- P.prepareC(core, saved.payload.target.copy(observation = actual), progress.pending.get.payload,
          progress.failed.flatMap(_.payload.check))
        _ <- if (proof == saved.payload.target.partialCompletion) Consequence.unit
          else F.invalid("partial completion native containment or normalization drifted")
      } yield ()
      } yield () }
      _ <- progress.automatic.fold(Consequence.unit) { saved => for {
        actual <- R.observeC(d, core, input, ordinal)
        _ <- if (actual == saved.payload.target.observation &&
            R.continuationAction(saved.payload.target).contains(saved.payload.action)) Consequence.unit
          else F.invalid("closure continuation Git observation or physical selection drifted before its effect")
      } yield () }
    } yield ()
    def reconcileC(progress: Progress): Consequence[Saved] = for {
      target <- progress.continuation.filter(_.payload.action == "reconcile-commit").map(_.payload.target)
        .orElse(progress.direction.filter(_.payload.choice == "ReconcileCommit").map(_.payload.target))
        .map(Consequence.success).getOrElse(F.invalid("closure reconciliation has no exact retained direction or continuation"))
      _ <- recheckRecoveryC(progress)
      granted <- installation.authority.isGrantedC(core, input)
      _ <- if (granted) Consequence.unit else F.invalid("actual Step closure authority is unavailable")
      pending <- progress.pending.map(Consequence.success).getOrElse(F.invalid("reconciliation has no original pending intent"))
      actual = target.observation.commit
      completed = pending.payload.copy(commit = Some(O.Commit(actual.identity, input.parent.head, actual.tree)))
      _ <- _verified_commit_c(completed, progress.failed)
      next <- _closed_state_c(completed)
      recorded <- saveC("committed", progress.record, next.stage.toString, completed)
      saved <- completedC(Saved(recorded, completed, next))
    } yield saved
    def completePartialC(progress: Progress): Consequence[Saved] = {
      val pending = progress.pending.get
      progress.completionIntent match {
        case Some((_, intent)) => progress.completionExecution match {
          case Some((frontier, execution)) if execution.result.outcome == "succeeded" =>
            _finish_partial_c(frontier, intent, execution)
          case _ => Consequence.success(Saved(progress.record, pending.payload, controller,
            progress.completionExecution.map(_._2.result)))
        }
        case None => for {
          _ <- dependencyPreconditions()
          _ <- recheckRecoveryC(progress)
          granted <- installation.authority.isGrantedC(core, input)
          _ <- if (granted) Consequence.unit else F.invalid("actual Step closure authority is unavailable")
          intent = P.Intent(progress.direction.get.payload, pending.payload)
          _ <- if (P.validIntent(intent)) Consequence.unit else F.invalid("partial completion has no exact original payload and direction")
          prepared <- R.saveC(session, d, core, input, "partial-completion-intent", pending.payload.attempt,
            progress.record, intent, P.intentCodec)
          _ <- ackC("goal-step-partial-completion-prepared:" + input.step)
          _ <- dependencyPreconditions()
          _ <- recheckRecoveryC(progress)
          actual <- E.commitGoalStepC(core, ordinal)
          summary <- R.executionC(input, ordinal, pending.payload.attempt, actual)
          execution = P.Execution(intent, summary)
          recorded <- R.saveC(session, d, core, input, "partial-completion-execution", pending.payload.attempt,
            prepared, execution, P.executionCodec)
          _ <- ackC("goal-step-partial-completion-executed:" + input.step)
          saved <- if (summary.outcome == "succeeded") for {
            _ <- actual.valueC
            result <- _finish_partial_c(recorded, intent, execution)
          } yield result else Consequence.success(Saved(recorded, pending.payload, controller, Some(summary)))
        } yield saved
      }
    }
    private def _finish_partial_c(frontier: InstanceRecord, intent: P.Intent, execution: P.Execution): Consequence[Saved] = for {
      proof <- intent.direction.target.partialCompletion.map(Consequence.success).getOrElse(F.invalid("partial completion lost its bound proof"))
      observed <- G.inspectC(core)
      native <- G.commitC(core, observed.observed.head)
      index <- G.indexTreeC(core)
      _ <- if (observed.mergeHead.isEmpty && observed.observed.branch == d.target.localBranch &&
          native.parents == Vector(proof.partial.identity) && native.tree == proof.expectedTree && index == proof.expectedTree &&
          !observed.observed.dirty.exists(_touches_owned) &&
          GoalPhaseNativeCandidateBoundary.scopedDirty(observed.observed.dirty, _owned_paths, true) &&
          execution.result.selectedPaths.toSet == proof.remainder.map(_.path).toSet) Consequence.unit
        else F.invalid("actual completion does not finish the exact accepted remainder")
      commit = O.Commit(native.identity, proof.partial.identity, native.tree)
      chain = O.StepCommitChain(input.parent, proof.partial, commit)
      completed = intent.original.copy(commit = Some(commit), partialCompletion = Some(chain))
      retained <- progressC
      _ <- _verified_commit_c(completed, retained.failed)
      next <- _closed_state_c(completed)
      recorded <- saveC("committed", frontier, next.stage.toString, completed)
      saved <- completedC(Saved(recorded, completed, next))
      _ <- ackC("goal-step-partial-completion-recorded:" + input.step)
    } yield saved
    def commitC(checked: InstanceRecord, payload: Payload): Consequence[Saved] = for {
      selection <- preconditionsC
      differences <- E.goalIndexChangesC(core, ordinal, input.candidate.tree)
      progress <- progressC
      _ <- recheckRecoveryC(progress)
      admitted <- admitChangesC(progress.failed, differences)
      _ <- if (admitted == payload.changes) Consequence.unit
        else F.invalid("selected Step index changed after its admitted precheck")
      intent <- saveC(_attempt_key("intent", payload.attempt), checked, "Closing", payload)
      _ <- ackC("goal-step-commit-prepared:" + input.step)
      repeated <- preconditionsC
      _ <- if (selection == repeated) Consequence.unit else F.invalid("physical selection changed at the commit effect boundary")
      actual <- E.commitGoalStepC(core, ordinal)
      summary <- R.executionC(input, ordinal, payload.attempt, actual)
      recorded <- R.saveC(session, d, core, input, "execution-result", payload.attempt, intent, summary, R.executionCodec)
      _ <- ackC("goal-step-commit-executed:" + input.step)
      saved <- if (summary.outcome == "succeeded") for {
        _ <- actual.valueC
        _ <- if (progress.recovery.forall(_.payload.target.observation.selectedPaths == summary.selectedPaths)) Consequence.unit
          else F.invalid("actual new commit result used a different physical selection")
        _ <- if (progress.automatic.forall(_.payload.target.observation.selectedPaths == summary.selectedPaths)) Consequence.unit
          else F.invalid("actual continued commit result used a different physical selection")
        closed <- _finish_commit_c(recorded, payload)
      } yield closed else Consequence.success(Saved(recorded, payload, controller, Some(summary)))
    } yield saved
    private def _finish_commit_c(frontier: InstanceRecord, payload: Payload): Consequence[Saved] = for {
      observed <- G.inspectC(core)
      commit <- G.commitC(core, observed.observed.head)
      _ <- if (observed.mergeHead.isEmpty && observed.observed.branch == d.target.localBranch &&
          observed.observed.tree == commit.tree &&
          !observed.observed.dirty.exists(_touches_owned) &&
          GoalPhaseNativeCandidateBoundary.scopedDirty(observed.observed.dirty, _owned_paths, true)) Consequence.unit
        else F.invalid("actual selected Step commit leaves selected dirty data or a different native frontier")
      completed = payload.copy(commit = Some(O.Commit(commit.identity, input.parent.head, commit.tree)))
      retained <- progressC
      _ <- _verified_commit_c(completed, retained.failed)
      next <- _closed_state_c(completed)
      event = if (next.stage == O.Stage.Implementation) "step-next-closed" else "last-step-closed"
      _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits("Closing", event, next.stage.toString))
        Consequence.unit else F.invalid("Step commit successor is absent from CML")
      recorded <- saveC("committed", frontier, next.stage.toString, completed)
      saved <- completedC(Saved(recorded, completed, next))
      _ <- ackC("goal-step-commit-recorded:" + input.step)
    } yield saved
    private def _touches_owned(value: G.DirtyPath): Boolean = _owned_paths.exists(owner =>
      covers(owner, value.path) || value.renamedFrom.exists(covers(owner, _)))
    def preconditionsC: Consequence[Vector[String]] = for {
      _ <- dependencyPreconditions()
      root <- G.rootC(core)
      native <- G.inspectC(core)
      actual = native.observed
      _ <- if (root == d.target.root.toString && native.mergeHead.isEmpty && actual.branch == d.target.localBranch &&
          actual.head == input.parent.head && actual.tree == input.parent.tree &&
          GoalPhaseNativeCandidateBoundary.scopedDirty(actual.dirty, _owned_paths, true)) Consequence.unit
        else F.invalid("actual Step commit candidate or selected ownership has changed")
      parent <- GitObjectId.parseC(input.parent.head)
      paths <- E.executeGitC(core, GitOperation.PendingPaths(parent)).flatMap(_.valueC)
      _ <- if (GoalPhaseNativeCandidateBoundary.pathsOwned(paths.map(_.value), _owned_paths)) Consequence.unit
        else F.invalid("actual Step commit includes an unrelated index delta")
      selection <- E.checkGoalIndexPreparationC(core, ordinal, input.parent.head)
      _ <- if (P.coverage(paths.map(_.value), selection.selectedPaths) &&
          actual.dirty.filter(_touches_owned).forall(value => Vector(Some(value.path), value.renamedFrom).flatten.forall(p =>
            _owned_paths.exists(covers(_, p))))) Consequence.unit
        else F.invalid("physical selected Step paths do not cover the complete accepted candidate")
      index <- G.indexTreeC(core)
      differences <- _tree_changes_c(index)
      progress <- progressC
      _ <- admitChangesC(progress.failed, differences)
      after <- G.observeC(core)
      afterindex <- G.indexTreeC(core)
      afterroot <- G.rootC(core)
      _ <- if (afterroot == root && afterindex == index && after.branch == actual.branch && after.head == input.parent.head && after.tree == input.parent.tree &&
          GoalPhaseNativeCandidateBoundary.scopedDirty(after.dirty, _owned_paths, true)) Consequence.unit
        else F.invalid("actual Step parent drifted during closure observation")
    } yield selection.selectedPaths
    def ackC(stage: String): Consequence[Unit] = acknowledge(stage) match {
      case Consequence.Success(_) => Consequence.unit
      case _ => org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "GoalPhase Step commit acknowledgement interrupted; retained intent is not replayed: " + stage)
    }
  }
  /** Completion roles are adjacent to the exact retained Human direction.
    * Recording-only reconstruction uses these same facts without observing Git.
    */
  private[admission] def partialProgressC(progress: Progress, intent: Option[(InstanceRecord, P.Intent)],
    execution: Option[(InstanceRecord, P.Execution)]): Consequence[Progress] = (intent, execution) match {
    case (None, None) => Consequence.success(progress)
    case (None, Some(_)) => F.invalid("partial completion execution has no intent")
    case (Some((record, payload)), result) => for {
      direction <- progress.direction.filter(_.payload.choice == "CompletePartialCommit")
        .map(Consequence.success).getOrElse(F.invalid("partial completion has no bound Human direction"))
      pending <- progress.pending.map(Consequence.success).getOrElse(F.invalid("partial completion has no original intent"))
      _ <- if (P.validIntent(payload) && payload.direction == direction.payload && payload.original == pending.payload &&
          progress.record == direction.record) Consequence.unit
        else F.invalid("partial completion intent crosses its original result or recovery direction")
      _ <- R.adjacentC(direction.record, record, "partial-completion-intent", pending.payload.attempt)
      next <- result match {
        case None => Consequence.success(progress.copy(record = record, completionIntent = intent))
        case Some((saved, actual)) => for {
          _ <- if (P.validExecution(actual) && actual.intent == payload) Consequence.unit
            else F.invalid("partial completion execution crosses its distinct intent")
          _ <- R.adjacentC(record, saved, "partial-completion-execution", pending.payload.attempt)
        } yield progress.copy(record = saved, completionIntent = intent, completionExecution = result)
      }
    } yield next
  }
  private[admission] def completedPartialC(payload: Payload, progress: Progress): Consequence[Unit] =
    payload.partialCompletion match {
      case None => if (progress.completionIntent.isEmpty && progress.completionExecution.isEmpty)
        Consequence.unit else F.invalid("partial completion cannot be flattened into an ordinary closure")
      case Some(chain) => for {
        intent <- progress.completionIntent.map(v => Consequence.success(v._2))
          .getOrElse(F.invalid("completed partial chain has no retained completion intent"))
        execution <- progress.completionExecution.map(v => Consequence.success(v._2))
          .getOrElse(F.invalid("completed partial chain has no terminal completion execution"))
        _ <- if (P.validIntent(intent)) Consequence.unit else F.invalid("completed partial chain has an invalid completion intent")
        proof <- intent.direction.target.partialCompletion.map(Consequence.success)
          .getOrElse(F.invalid("completed partial chain has no original containment proof"))
        _ <- if (P.validIntent(intent) && P.validExecution(execution) && execution.intent == intent &&
            R.terminalExecution(execution.result) && execution.result.outcome == "succeeded" &&
            O.validStepCommitChain(chain) && chain.originalParent == proof.originalParent && chain.partial == proof.partial &&
            chain.completion.tree == proof.expectedTree && payload.commit.contains(chain.completion) &&
            payload.copy(commit = None, partialCompletion = None) == intent.original &&
            execution.result.selectedPaths.toSet == proof.remainder.map(_.path).toSet) Consequence.unit
          else F.invalid("completed partial chain changes its retained accepted facts or actual result")
      } yield ()
    }

  /** Pure reconciliation; the Store separately binds every record to actual history. */
  private[admission] def reconcileAttemptsC(from: InstanceRecord, input: Input, ordinal: Int,
    attempts: Vector[Attempt]): Consequence[Progress] = for {
    _ <- if (from != null && _valid_input(input) && ordinal > 0 && ordinal <= 64 && attempts != null &&
        from.identity.value == input.instance && from.revision.value.toString == input.sourceRevision &&
        from.lifecycle == Lifecycle.Active && from.suspension.isEmpty &&
        from.currentProgression.contains(ProgressionReference("phase.Closing"))) Consequence.unit
      else F.invalid("retained Step attempts have no original accepted Closing input")
    _ <- from.validateC
    progress <- attempts.zipWithIndex.foldLeft(Consequence.success(Progress(from, Vector.empty, Some(0), None))) {
      case (prior, (attempt, index)) => prior.flatMap { current =>
        if (attempt == null || attempt.check == null || attempt.intent == null || attempt.execution == null ||
            attempt.direction == null || attempt.continuation == null || index >= 256 || !current.nextAttempt.contains(index))
          F.invalid("retained Step attempts follow a gap or unresolved attempt")
        else (attempt.check, attempt.intent) match {
          case (None, None) => if (index == attempts.size - 1 && attempt.execution.isEmpty && attempt.direction.isEmpty && attempt.continuation.isEmpty)
            Consequence.success(current)
            else F.invalid("retained Step attempts have a gap before later facts")
          case (None, Some(intent)) => for {
            _ <- if (index == 0 && intent != null && intent.payload == Payload(input, ordinal, None)) Consequence.unit
              else F.invalid("retained Step intent has no matching check")
            _ <- _successor_c(current.record, intent.record, "intent", "Closing")
            base = Progress(intent.record, current.failed, None, Some(intent), recovery = current.recovery, automatic = current.automatic)
            result <- _recovery_progress_c(from, input, ordinal, index, attempt, base)
          } yield result
          case (Some(check), intent) => for {
            _ <- if (check != null && check.payload != null && check.payload.input == input &&
                check.payload.ordinal == ordinal && check.payload.attempt == index && check.payload.commit.isEmpty &&
                check.payload.check.exists(_valid_precheck) && _valid_changes(check.payload.changes)) Consequence.unit
              else F.invalid("retained Step check crosses its input, ordinal, attempt or facts")
            _ <- _successor_c(current.record, check.record, _attempt_key("check-result", index), "Closing")
            result <- if (check.payload.check.get.findings.nonEmpty) {
              if (check.payload.changes.nonEmpty || intent.nonEmpty) F.invalid("failed Step check has changes or a commit intent")
              else if (index == Int.MaxValue) F.invalid("retained Step attempt ordinal is exhausted")
              else Consequence.success(Progress(check.record, current.failed :+ check, Some(index + 1), None,
                recovery = current.recovery, automatic = current.automatic))
            } else intent match {
              case None => Consequence.success(Progress(check.record, current.failed, None, Some(check), recovery = current.recovery, automatic = current.automatic))
              case Some(saved) => for {
                _ <- if (saved != null && saved.payload == check.payload) Consequence.unit
                  else F.invalid("retained Step intent rewrites its exact clean check")
                _ <- _successor_c(check.record, saved.record, _attempt_key("intent", index), "Closing")
              } yield Progress(saved.record, current.failed, None, Some(saved), recovery = current.recovery, automatic = current.automatic)
            }
            continued <- _recovery_progress_c(from, input, ordinal, index, attempt, result)
          } yield continued
        }
      }
    }
  } yield progress

  private def _recovery_progress_c(from: InstanceRecord, input: Input, ordinal: Int, index: Int,
    attempt: Attempt, progress: Progress): Consequence[Progress] = for {
    _ <- if ((attempt.execution.isEmpty && attempt.direction.isEmpty && attempt.continuation.isEmpty) || attempt.intent.nonEmpty) Consequence.unit
      else F.invalid("closure recovery or execution summary has no exact commit intent")
    _ <- if (attempt.direction.isEmpty || attempt.continuation.isEmpty) Consequence.unit
      else F.invalid("Human recovery and automatic continuation cannot coexist for an attempt")
    observed <- attempt.execution match {
      case None => Consequence.success(progress)
      case Some(saved) => for {
        _ <- if (saved != null && R.validExecution(saved.payload) && saved.payload.input == input &&
            saved.payload.ordinal == ordinal && saved.payload.attempt == index) Consequence.unit
          else F.invalid("execution summary crosses its original input or attempt")
        _ <- R.adjacentC(progress.record, saved.record, "execution-result", index)
      } yield progress.copy(record = saved.record, execution = Some(saved))
    }
    directed <- attempt.direction match {
      case None => Consequence.success(observed)
      case Some(saved) => for {
        _ <- if (saved != null && R.directionCodec.decode(R.directionCodec.encode(saved.payload)).isRight &&
            saved.payload.target.input == input && saved.payload.target.ordinal == ordinal && saved.payload.target.attempt == index &&
            saved.payload.target.definitionIdentity == from.definition.workflowIdentity.value &&
            saved.payload.target.definitionRevision == from.definition.workflowRevision.value &&
            saved.payload.target.frontierRevision == observed.record.revision.value.toString &&
            saved.payload.target.intentRecordRevision == attempt.intent.get.record.revision.value.toString &&
            saved.payload.target.execution == observed.execution.map(_.payload)) Consequence.unit
          else F.invalid("recovery direction crosses its original intent, execution summary, target or frontier")
        _ <- R.adjacentC(observed.record, saved.record, "recovery", index)
        _ <- if (index < 255) Consequence.unit else F.invalid("closure recovery attempt bound exhausted")
      } yield observed.copy(record = saved.record,
        nextAttempt = Option.when(saved.payload.choice == "AuthorizeNewAttempt")(index + 1),
        direction = Some(saved), recovery = Some(saved), automatic = None)
    }
    continued <- attempt.continuation match {
      case None => Consequence.success(directed)
      case Some(saved) => for {
        _ <- if (saved != null && R.continuationCodec.decode(R.continuationCodec.encode(saved.payload)).isRight &&
            saved.payload.target.input == input && saved.payload.target.ordinal == ordinal && saved.payload.target.attempt == index &&
            saved.payload.target.definitionIdentity == from.definition.workflowIdentity.value &&
            saved.payload.target.definitionRevision == from.definition.workflowRevision.value &&
            saved.payload.target.frontierRevision == observed.record.revision.value.toString &&
            saved.payload.target.intentRecordRevision == attempt.intent.get.record.revision.value.toString &&
            saved.payload.target.execution == observed.execution.map(_.payload)) Consequence.unit
          else F.invalid("continuation crosses its original intent, actual execution, target or frontier")
        _ <- R.adjacentC(observed.record, saved.record, "continuation", index)
        _ <- if (index < 255) Consequence.unit else F.invalid("closure continuation attempt bound exhausted")
      } yield observed.copy(record = saved.record,
        nextAttempt = Option.when(saved.payload.action == "retry-preparation")(index + 1),
        continuation = Some(saved), automatic = Some(saved), recovery = None)
    }
  } yield continued

  /** Only actual typed blob bytes and an earlier selected EOF failure admit this correction. */
  private[admission] def admitEofCorrectionC(input: Input, ownedPaths: Vector[String], failed: Vector[Precheck],
    changes: Vector[GitTreeChange], blobs: Map[String, GitBlobContent]): Consequence[Vector[Change]] = {
    if (!_valid_input(input) || ownedPaths == null || ownedPaths.isEmpty || ownedPaths.size > 256 ||
        ownedPaths.distinct != ownedPaths || !ownedPaths.forall(_valid_path) || failed == null ||
        !failed.forall(_valid_precheck) || changes == null || blobs == null)
      return F.invalid("EOF correction has no exact original input or actual typed facts")
    if (changes.isEmpty) return Consequence.success(Vector.empty)
    if (!changes.forall(change => change != null && change.path != null &&
        failed.exists(check => check.eofOnly && check.eofPaths.contains(change.path.value))))
      return F.invalid("EOF correction is not covered by an actual earlier EOF-only check")
    admitTerminalLfChangesC(ownedPaths, changes, blobs)
  }

  /** Actual byte rule only. Finding authority remains with each caller. */
  private[admission] def admitTerminalLfChangesC(ownedpaths: Vector[String],
    changes: Vector[GitTreeChange], blobs: Map[String, GitBlobContent]): Consequence[Vector[Change]] = {
    if (ownedpaths == null || ownedpaths.isEmpty || ownedpaths.size > 256 ||
        ownedpaths.distinct != ownedpaths || !ownedpaths.forall(_valid_path) || changes == null || blobs == null)
      return F.invalid("terminal LF correction has no bounded ownership or complete actual facts")
    val admitted = changes.map { change =>
      if (change == null || change.path == null || change.oldBlob == null || change.newBlob == null ||
          change.oldBlob.isEmpty || change.newBlob.isEmpty || change.oldBlob.exists(_ == null) ||
          change.newBlob.exists(_ == null)) None
      else {
        val path = change.path.value
        val oldblob = change.oldBlob.get.value
        val newblob = change.newBlob.get.value
        if (!_valid_path(path) || !ownedpaths.exists(covers(_, path)) || change.status != "M" ||
            change.oldMode != change.newMode || !Set("100644", "100755")(change.oldMode) ||
            !_oid(oldblob) || !_oid(newblob) || oldblob == newblob) None
        else for {
          before <- blobs.get(oldblob).filter(_complete_text)
          after <- blobs.get(newblob).filter(_complete_text)
          content = before.bytes.reverse.dropWhile(_ == 10.toByte).reverse
          if before.bytes.takeRight(2) == Vector(10.toByte, 10.toByte) && content.nonEmpty &&
            after.bytes == content :+ 10.toByte
        } yield Change(path, change.oldMode, oldblob, newblob)
      }
    }
    if (changes.size <= 256 && admitted.forall(_.nonEmpty) &&
        admitted.flatten.map(_.path).distinct.size == changes.size)
      Consequence.success(admitted.flatten.sortBy(_.path))
    else F.invalid("selected changes require affected validation/review; only proven terminal LF removal can reuse original targets")
  }

  private def _complete_text(value: GitBlobContent): Boolean = value != null && value.bytes != null &&
    value.text != null && value.text.exists { text =>
      text != null && !value.bytes.contains(0.toByte) && scala.util.Try {
        val decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
          .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        decoder.decode(java.nio.ByteBuffer.wrap(value.bytes.toArray)).toString == text
      }.getOrElse(false)
    }
  private def _typed_changes_c(changes: Vector[Change]): Consequence[Vector[GitTreeChange]] =
    changes.foldLeft(Consequence.success(Vector.empty[GitTreeChange])) { (prior, change) => for {
      saved <- prior
      path <- GitPath.parseC(change.path)
      before <- GitObjectId.parseC(change.oldBlob)
      after <- GitObjectId.parseC(change.newBlob)
    } yield saved :+ GitTreeChange(change.mode, change.mode, Some(before), Some(after), "M", path) }
  private def _attempt_key(role: String, attempt: Int): String =
    role + (if (attempt == 0) "" else "-retry-" + attempt)
  private def _successor_c(from: InstanceRecord, saved: InstanceRecord, key: String, stage: String): Consequence[Unit] = for {
    _ <- if (from != null && saved != null && from.revision.value < Long.MaxValue &&
        from.lifecycle == Lifecycle.Active && from.suspension.isEmpty &&
        from.currentProgression.contains(ProgressionReference("phase.Closing"))) Consequence.unit
      else F.invalid("Step attempt predecessor is not an unsuspended Closing record")
    _ <- from.validateC
    _ <- saved.validateC
    expected <- from.appendC(from.revision, F.history(from, "phase." + stage, "ordered-step-closure:" + key))
    _ <- if (expected == saved) Consequence.unit
      else F.invalid("retained Step attempt is not its canonical full native history successor")
  } yield ()
  private def _valid_input(value: Input): Boolean = value != null && _text(value.instance) &&
    value.sourceRevision != null && value.sourceRevision.toLongOption.exists(n => n > 0 && n.toString == value.sourceRevision) &&
    _text(value.plan) && _text(value.planRevision) && _text(value.step) && value.generation > 0 &&
    value.parent != null && value.candidate != null && _oid(value.parent.head) && _oid(value.parent.tree) &&
    value.candidate.head == value.parent.head && _oid(value.candidate.tree) &&
    O.validStepAcceptance(O.ClosedStep(value.step, O.Commit("", "", ""), value.reviewEvidence, value.admission)) &&
    value.admission.forall(_.assessment.candidate == value.candidate)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty &&
    !value.exists(c => Character.isISOControl(c))
  private def _valid_precheck(value: Precheck): Boolean = value != null && value.findings != null &&
    value.findings.distinct == value.findings && value.findings.forall(s => s != null && s.nonEmpty &&
      !s.exists(c => c == '\u0000' || c == '\r' || c == '\n')) && value.eofPaths != null &&
    value.eofPaths.distinct == value.eofPaths && value.eofPaths.forall(_valid_path) &&
    (value.findings.nonEmpty || (value.eofPaths.isEmpty && !value.eofOnly)) &&
    (!value.eofOnly || (value.findings.nonEmpty && value.eofPaths.nonEmpty))
  private def _valid_changes(values: Vector[Change]): Boolean = values != null && values.size <= 256 &&
    values.forall(value => value != null && _valid_path(value.path) && Set("100644", "100755")(value.mode) &&
      _oid(value.oldBlob) && _oid(value.newBlob) && value.oldBlob != value.newBlob) &&
    values.map(_.path).distinct.size == values.size && values.sortBy(_.path) == values
  private def _valid_path(value: String): Boolean = value != null && GitPath.parseC(value).isSuccess
  private def _oid(value: String): Boolean = value != null && GitObjectId.parseC(value).isSuccess
  private def id(value: String) = value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}")
  private def covers(owner: String, path: String) = path == owner || path.startsWith(owner + "/")
}
