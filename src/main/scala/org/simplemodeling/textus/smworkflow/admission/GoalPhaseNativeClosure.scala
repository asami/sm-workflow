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
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Native ordered Step closure, not a legacy Step Terminal. Host authorization
  * and fixed ProcessProgram grants are separate from AI review disposition.
  * Durable effect intent precedes commit; external Git is not rolled back with
  * storage and an intent without a recorded result is never automatically rerun.
  * @since   Oct.  6, 2026
  * @version Oct. 20, 2026
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
    step: String, generation: Long, parent: O.Snapshot, candidate: O.Snapshot, reviewEvidence: String)
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
    check: Option[Precheck] = None, changes: Vector[Change] = Vector.empty)
  object Payload {
    given Encoder[Payload] = Encoder.instance { value =>
      Json.obj((Vector("input" -> value.input.asJson, "ordinal" -> value.ordinal.asJson,
        "commit" -> value.commit.asJson) ++
        (if (value.attempt == 0) Vector.empty else Vector("attempt" -> value.attempt.asJson)) ++
        value.check.toVector.map(actual => "check" -> actual.asJson) ++
        (if (value.changes.isEmpty) Vector.empty else Vector("changes" -> value.changes.asJson)))*)
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
        _ <- Either.cond(attempt >= 0 && check.forall(_valid_precheck) && _valid_changes(changes), (),
          io.circe.DecodingFailure("invalid native Step closure attempt facts", cursor.history))
      } yield Payload(input, ordinal, commit, attempt, check, changes)
    }
  }
  final case class Saved(record: InstanceRecord, payload: Payload, controller: O.State)
  private[admission] final case class Attempt(check: Option[Saved], intent: Option[Saved])
  private[admission] final case class Progress(record: InstanceRecord, failed: Vector[Saved],
    nextAttempt: Option[Int], pending: Option[Saved])
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseNativeStepClosureReceipt"
    def encode(value: Payload) = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-step-closure.v3"),
      "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("native Step closure is missing")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-native-step-closure.v3", (), "native Step closure schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
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
      } yield if (progress.nextAttempt.nonEmpty) progress.failed.lastOption else None
    }
  } yield saved

  /** Recording Submit reconstructs canonical history without observing live Git. */
  def retainedRecordingHistoryOptionC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State): Consequence[Option[Saved]] = for {
    store <- _store_c(session, d, core, from, controller, _ => Consequence.unit)
    result <- store.loadC("committed")
    saved <- result match {
      case Some(value) => store.completedRecordingHistoryC(value).map(Some(_))
      case None => Consequence.success(Option.empty[Saved])
    }
  } yield saved

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State, acknowledge: String => Consequence[Unit]): Consequence[Option[Saved]] = for {
    store <- _store_c(session, d, core, from, controller, acknowledge)
    result <- store.loadC("committed")
    saved <- result match {
      case Some(value) => store.completedC(value).map(Some(_))
      case None => for {
        progress <- store.progressC
        _ <- if (progress.nextAttempt.nonEmpty) store.latestC(progress.record)
          else F.invalid("Step closure has a pending clean check or unknown commit intent; no automatic replay")
        authority <- store.installation.authority.isGrantedC(core, store.input)
        closed <- authority match {
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
      } yield closed
    }
  } yield saved

  /** The owning Closing frontier supplies the same target to authority lookup
    * and the public missing-authority Wait projection. */
  def inputC(from: InstanceRecord, controller: O.State): Consequence[Input] = for {
    _ <- if (controller.stage == O.Stage.Closing && controller.cursor < controller.plan.steps.size &&
        controller.candidate.nonEmpty && controller.checksReceipt.nonEmpty && controller.reviewEvidence.nonEmpty &&
        from.currentProgression.contains(ProgressionReference("phase.Closing")))
      Consequence.unit else F.invalid("Step closure has no actual accepted review and native Closing frontier")
    step = controller.plan.steps(controller.cursor)
  } yield Input(from.identity.value, from.revision.value.toString, controller.plan.identity, controller.plan.revision,
    step.identity, controller.generation, controller.tipSnapshot, controller.candidate.get, controller.reviewEvidence.get)

  private def _store_c(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State, acknowledge: String => Consequence[Unit]): Consequence[Store] = for {
    input <- inputC(from, controller)
    installation <- d.closureInstallation.map(Consequence.success).getOrElse(F.invalid("Step closure installation is missing"))
    _ <- installation.validateC
    step = controller.plan.steps(controller.cursor)
    ordinal <- installation.policy.programs.find(_.step == step.identity).map(p => Consequence.success(p.ordinal))
      .getOrElse(F.invalid("Step has no host-fixed commit program"))
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
  } yield new Store(adapter, session, d, core, from, controller, installation, ordinal, input, acknowledge)

  private final class Store(adapter: WorkflowManagedMechanicalTransition[Payload], session: F.Session,
    d: F.Declaration, core: ActionCall.Core, from: InstanceRecord, controller: O.State,
    val installation: Installation, val ordinal: Int, val input: Input, acknowledge: String => Consequence[Unit]) {
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
    def progressC: Consequence[Progress] = _scan_c(0, Vector.empty, _verify_retained_changes_c)
    private def _scan_c(attempt: Int, attempts: Vector[Attempt],
      verify: (Saved, Progress) => Consequence[Unit]): Consequence[Progress] = for {
      check <- loadC(_attempt_key("check-result", attempt))
      intent <- loadC(_attempt_key("intent", attempt))
      examined = attempts :+ Attempt(check, intent)
      progress <- reconcileAttemptsC(from, input, ordinal, examined)
      _ <- check.fold(Consequence.unit) { saved => for {
        _ <- if (saved.payload.changes.forall(change => _owned_paths.exists(covers(_, change.path))) &&
            saved.payload.check.forall(_.eofPaths.forall(path => _owned_paths.exists(covers(_, path)))))
          Consequence.unit else F.invalid("retained correction crosses current Step ownership")
        _ <- verify(saved, progress)
      } yield () }
      result <- if (check.nonEmpty && progress.nextAttempt.nonEmpty)
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
      _ <- if (native.identity != input.parent.head && native.parents == Vector(input.parent.head) &&
          native.tree == payload.commit.get.tree && payload.commit.get.parent == input.parent.head)
        Consequence.unit else F.invalid("actual Step commit differs from its native identity or original parent")
      actual <- _tree_changes_c(native.tree)
      admitted <- admitChangesC(failed, actual)
      _ <- if (admitted == payload.changes && (payload.changes.nonEmpty || native.tree == input.candidate.tree))
        Consequence.unit else F.invalid("actual Step commit differs from its exact reviewed tree or bounded EOF correction")
    } yield ()
    private def _closed_state_c(payload: Payload): Consequence[O.State] = for {
      entered <- controller.acceptC(O.ClosureIntentSaved(input.candidate, _intent_identity(payload.attempt)))
      next <- if (payload.changes.isEmpty)
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
      progress <- _scan_c(0, Vector.empty, (_, _) => Consequence.unit)
      saved <- _completed_c(result, progress)
    } yield saved
    private def _completed_c(result: Saved, progress: Progress): Consequence[Saved] = for {
      pending <- progress.pending.filter(saved => saved.record.causation.value ==
        "ordered-step-closure:" + _attempt_key("intent", saved.payload.attempt))
        .map(Consequence.success).getOrElse(F.invalid("completed Step has no exact pending commit intent"))
      _ <- if (result != null && result.payload != null && result.payload.commit.nonEmpty &&
          result.payload.copy(commit = None) == pending.payload) Consequence.unit
        else F.invalid("retained Step closure changes its exact pending payload")
      next <- _closed_state_c(result.payload)
      _ <- _successor_c(pending.record, result.record, "committed", next.stage.toString)
    } yield Saved(result.record, result.payload, next)
    def commitC(checked: InstanceRecord, payload: Payload): Consequence[Saved] = for {
      intent <- saveC(_attempt_key("intent", payload.attempt), checked, "Closing", payload)
      _ <- ackC("goal-step-commit-prepared:" + input.step)
      actual <- E.commitGoalStepC(core, ordinal)
      _ <- ackC("goal-step-commit-executed:" + input.step)
      _ <- actual.valueC
      observed <- G.inspectC(core)
      commit <- G.commitC(core, observed.observed.head)
      _ <- if (observed.mergeHead.isEmpty && observed.observed.branch == d.target.localBranch &&
          observed.observed.tree == commit.tree &&
          !observed.observed.dirty.exists(_touches_owned)) Consequence.unit
        else F.invalid("actual selected Step commit leaves selected dirty data or a different native frontier")
      completed = payload.copy(commit = Some(O.Commit(commit.identity, input.parent.head, commit.tree)))
      progress <- progressC
      _ <- _verified_commit_c(completed, progress.failed)
      next <- _closed_state_c(completed)
      event = if (next.stage == O.Stage.Implementation) "step-next-closed" else "last-step-closed"
      _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits("Closing", event, next.stage.toString))
        Consequence.unit else F.invalid("Step commit successor is absent from CML")
      recorded <- saveC("committed", intent, next.stage.toString, completed)
      saved <- completedC(Saved(recorded, completed, next))
      _ <- ackC("goal-step-commit-recorded:" + input.step)
    } yield saved
    private def _touches_owned(value: G.DirtyPath): Boolean = _owned_paths.exists(owner =>
      covers(owner, value.path) || value.renamedFrom.exists(covers(owner, _)))
    def preconditionsC: Consequence[Unit] = for {
      native <- G.inspectC(core)
      actual = native.observed
      _ <- if (native.mergeHead.isEmpty && actual.branch == d.target.localBranch && actual.head == input.parent.head &&
          actual.tree == input.parent.tree && actual.dirty.filter(_touches_owned).forall(p =>
            p.worktree == ' ' && !p.unmerged && p.index != '?' && p.index != '!' &&
            _owned_paths.exists(covers(_, p.path)) && p.renamedFrom.forall(x => _owned_paths.exists(covers(_, x))))) Consequence.unit
        else F.invalid("actual Step commit candidate or selected ownership has changed")
    } yield ()
    def ackC(stage: String): Consequence[Unit] = acknowledge(stage) match {
      case Consequence.Success(_) => Consequence.unit
      case _ => org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "GoalPhase Step commit acknowledgement interrupted; retained intent is not replayed: " + stage)
    }
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
        if (attempt == null || attempt.check == null || attempt.intent == null || !current.nextAttempt.contains(index))
          F.invalid("retained Step attempts follow a gap or unresolved attempt")
        else attempt match {
          case Attempt(None, None) => if (index == attempts.size - 1) Consequence.success(current)
            else F.invalid("retained Step attempts have a gap before later facts")
          case Attempt(None, Some(intent)) => for {
            _ <- if (index == 0 && intent != null && intent.payload == Payload(input, ordinal, None)) Consequence.unit
              else F.invalid("retained Step intent has no matching check")
            _ <- _successor_c(current.record, intent.record, "intent", "Closing")
          } yield Progress(intent.record, current.failed, None, Some(intent))
          case Attempt(Some(check), intent) => for {
            _ <- if (check != null && check.payload != null && check.payload.input == input &&
                check.payload.ordinal == ordinal && check.payload.attempt == index && check.payload.commit.isEmpty &&
                check.payload.check.exists(_valid_precheck) && _valid_changes(check.payload.changes)) Consequence.unit
              else F.invalid("retained Step check crosses its input, ordinal, attempt or facts")
            _ <- _successor_c(current.record, check.record, _attempt_key("check-result", index), "Closing")
            result <- if (check.payload.check.get.findings.nonEmpty) {
              if (check.payload.changes.nonEmpty || intent.nonEmpty) F.invalid("failed Step check has changes or a commit intent")
              else if (index == Int.MaxValue) F.invalid("retained Step attempt ordinal is exhausted")
              else Consequence.success(Progress(check.record, current.failed :+ check, Some(index + 1), None))
            } else intent match {
              case None => Consequence.success(Progress(check.record, current.failed, None, Some(check)))
              case Some(saved) => for {
                _ <- if (saved != null && saved.payload == check.payload) Consequence.unit
                  else F.invalid("retained Step intent rewrites its exact clean check")
                _ <- _successor_c(check.record, saved.record, _attempt_key("intent", index), "Closing")
              } yield Progress(saved.record, current.failed, None, Some(saved))
            }
          } yield result
        }
      }
    }
  } yield progress

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
    _text(value.reviewEvidence)
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
