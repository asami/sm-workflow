package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
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
  * @version Oct. 17, 2026
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
  final case class Payload(input: Input, ordinal: Int, commit: Option[O.Commit])
  final case class Saved(record: InstanceRecord, payload: Payload, controller: O.State)
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
    saved <- result.fold(Consequence.success(Option.empty[Saved]))(value => store.completedC(value).map(Some(_)))
  } yield saved

  def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    from: InstanceRecord, controller: O.State, acknowledge: String => Consequence[Unit]): Consequence[Option[Saved]] = for {
    store <- _store_c(session, d, core, from, controller, acknowledge)
    result <- store.loadC("committed")
    saved <- result match {
      case Some(value) => store.completedC(value).map(Some(_))
      case None => for {
        authority <- store.installation.authority.isGrantedC(core, store.input)
        closed <- authority match {
          case false => Consequence.success(Option.empty[Saved])
          case true => for {
            _ <- store.preconditionsC
            payload = Payload(store.input, store.ordinal, None)
            intent <- store.saveC("intent", from, "Closing", payload)
            _ <- store.ackC("goal-step-commit-prepared:" + store.input.step)
            actual <- E.commitGoalStepC(core, store.ordinal)
            _ <- store.ackC("goal-step-commit-executed:" + store.input.step)
            _ <- actual.valueC
            observed <- G.observeC(core)
            commit <- G.commitC(core, observed.head)
            _ <- if (observed.branch == d.target.localBranch && observed.dirty.isEmpty &&
                observed.tree == store.input.candidate.tree && commit.tree == observed.tree &&
                commit.parents == Vector(store.input.parent.head) && commit.identity != store.input.parent.head)
              Consequence.unit else F.invalid("actual Step commit does not establish the reviewed tree and native parent")
            entered <- controller.acceptC(O.ClosureIntentSaved(store.input.candidate, store.intentIdentity))
            nativeCommit = O.Commit(commit.identity, commit.parents.head, commit.tree)
            next <- entered.acceptC(O.StepCommitObserved(store.intentIdentity, nativeCommit))
            event = if (next.stage == O.Stage.Implementation) "step-next-closed" else "last-step-closed"
            _ <- if (domain.statemachine.GoalPhaseRunLifecycle.permits("Closing", event, next.stage.toString))
              Consequence.unit else F.invalid("Step commit successor is absent from CML")
            recorded <- store.saveC("committed", intent, next.stage.toString, payload.copy(commit = Some(nativeCommit)))
            saved <- store.completedC((recorded, payload.copy(commit = Some(nativeCommit))))
            _ <- store.ackC("goal-step-commit-recorded:" + store.input.step)
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
    val intentIdentity = from.identity.value + ":ordered-step-closure:" + from.revision.value + ":intent"
    def reference(key: String) = WorkflowMechanicalTransition.Reference(from.identity.value +
      ":ordered-step-closure:" + from.revision.value + ":" + key, "1")
    def loadC(key: String): Consequence[Option[(InstanceRecord, Payload)]] =
      adapter.loadRetainedC(reference(key))(using core.executionContext).flatMap {
        case None => Consequence.success(None)
        case Some(value) =>
          val p = value.receipt.payload.value
          Consequence.success(Some((value.receipt.next, p)))
      }
    def saveC(key: String, current: InstanceRecord, stage: String, payload: Payload): Consequence[InstanceRecord] = for {
      _ <- if (current.currentProgression.contains(ProgressionReference("phase.Closing"))) Consequence.unit
        else F.invalid("Step commit persistence has no native Closing predecessor")
      saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(key), current, current.revision,
        F.history(current, "phase." + stage, "ordered-step-closure:" + key), TypedValue(codec.typeIdentity, payload)),
        ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
    } yield saved.receipt.next
    def completedC(result: (InstanceRecord, Payload)): Consequence[Saved] = for {
      _ <- if (result._2 != null && result._2.input == input && result._2.ordinal == ordinal &&
          result._2.commit.nonEmpty) Consequence.unit
        else F.invalid("retained Step closure has no actual selected commit")
      entered <- controller.acceptC(O.ClosureIntentSaved(input.candidate, intentIdentity))
      next <- entered.acceptC(O.StepCommitObserved(intentIdentity, result._2.commit.get))
    } yield Saved(result._1, result._2, next)
    def preconditionsC: Consequence[Unit] = for {
      actual <- G.observeC(core)
      tree <- G.indexTreeC(core)
      step = controller.plan.steps(controller.cursor)
      _ <- if (actual.branch == d.target.localBranch && actual.head == input.parent.head && actual.tree == input.parent.tree &&
          tree == input.candidate.tree && actual.dirty.forall(p => p.worktree == ' ' && !p.unmerged &&
            p.index != '?' && p.index != '!' && step.ownedPaths.exists(covers(_, p.path)) &&
            p.renamedFrom.forall(x => step.ownedPaths.exists(covers(_, x))))) Consequence.unit
        else F.invalid("actual Step commit candidate or selected ownership has changed")
    } yield ()
    def ackC(stage: String): Consequence[Unit] = acknowledge(stage) match {
      case Consequence.Success(_) => Consequence.unit
      case _ => org.goldenport.cncf.datastore.DataStoreConditionalTransitionFailure.transactionIndeterminate(
        "GoalPhase Step commit acknowledgement interrupted; retained intent is not replayed: " + stage)
    }
  }
  private def id(value: String) = value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,255}")
  private def covers(owner: String, path: String) = path == owner || path.startsWith(owner + "/")
}
