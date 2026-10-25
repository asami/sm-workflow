package org.simplemodeling.textus.smworkflow.admission

import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{InstanceRecord, InstanceRevision}
import GoalPhaseDependencyValidation as D
import GoalPhaseNativeCandidate as C
import GoalPhaseOrderedProgression as O
import WorkflowManagedProcessExecution as E
import RepositorySyncManagedGitObservation as G
import GoalPhaseNativeWorkContinuation.DependencyChain

/** Dependency effects use the existing candidate/check intent owners and UoW.
  * @since   Oct. 24, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeDependencyValidation {
  private def _value_c[A](core: ActionCall.Core, binding: D.Binding, operation: GitOperation[A]): Consequence[A] =
    E.executeGitC(core, operation, binding.dependency.workspace).flatMap(_.valueC)

  def resolvedC(admission: D.Admission, chain: Option[DependencyChain]): Consequence[Unit] = chain match {
    case Some(value) => value.resolveC(admission)
    case _ => D.invalid("accepted prefix requires reconstruction from actual native closures")
  }
  def cumulativePaths(admission: D.Admission): Vector[String] =
    (admission.acceptedPrefix.toVector.flatMap(_.paths) ++ admission.binding.paths).distinct
  def baseline(admission: D.Admission): String = admission.acceptedPrefix.map(_.candidate.tree).getOrElse(admission.binding.base.head)

  def repositoryC(core: ActionCall.Core, binding: D.Binding, staged: Boolean): Consequence[G.Observed] =
    _repository_c(core, binding, binding.paths, staged)
  def repositoryC(core: ActionCall.Core, admission: D.Admission, staged: Boolean,
    chain: Option[DependencyChain]): Consequence[G.Observed] =
    resolvedC(admission, chain).flatMap(_ => _repository_c(core, admission.binding, cumulativePaths(admission), staged))
  private def _repository_c(core: ActionCall.Core, binding: D.Binding, owned: Vector[String], staged: Boolean): Consequence[G.Observed] = for {
    _ <- if (D.bindingValid(binding)) Consequence.unit else D.invalid("invalid captured binding")
    root <- _value_c(core, binding, GitOperation.Root)
    actual <- G.observeInWorkspaceC(core, binding.dependency.workspace)
    base <- GitObjectId.parseC(binding.base.head)
    commit <- _value_c(core, binding, GitOperation.CommitMetadata(base))
    _ <- if (root == binding.root && actual.branch == binding.branch && actual.head == binding.base.head &&
        actual.tree == binding.base.tree && commit.commit == base && commit.tree.value == binding.base.tree &&
        actual.dirty.forall(p => owned.contains(p.path) && p.renamedFrom.forall(owned.contains)) &&
        GoalPhaseNativeCandidateBoundary.scopedDirty(actual.dirty, owned, staged)) Consequence.unit
      else D.invalid("actual dependency root, branch, base, ownership or worktree differs")
    paths <- _value_c(core, binding, GitOperation.PendingPaths(base))
    _ <- if (paths.map(_.value).forall(owned.contains)) Consequence.unit
      else D.invalid("dependency index includes paths outside the exact approval union")
  } yield actual

  /** Existing typed snapshot owner; description effects stay in observation continuation only. */
  def snapshotC(core: ActionCall.Core, binding: D.Binding): Consequence[GoalPhaseCandidateObservationContinuation.RepositorySnapshot] = for {
    before <- repositoryC(core, binding, false)
    tree <- _value_c(core, binding, GitOperation.IndexTree)
    after <- repositoryC(core, binding, false)
    aftertree <- _value_c(core, binding, GitOperation.IndexTree)
    observed = GoalPhaseCandidateObservationContinuation.snapshot(binding.root, before, tree.value)
    _ <- if (before == after && tree == aftertree &&
        GoalPhaseCandidateObservationContinuation.validDependencyRepository(binding, observed)) Consequence.unit
      else D.invalid("dependency repository/index changed during the explicitly requested snapshot")
  } yield observed

  def snapshotC(core: ActionCall.Core, admission: D.Admission, chain: Option[DependencyChain]):
    Consequence[GoalPhaseCandidateObservationContinuation.RepositorySnapshot] = for {
    before <- repositoryC(core, admission, false, chain)
    tree <- _value_c(core, admission.binding, GitOperation.IndexTree)
    after <- repositoryC(core, admission, false, chain)
    aftertree <- _value_c(core, admission.binding, GitOperation.IndexTree)
    observed = GoalPhaseCandidateObservationContinuation.snapshot(admission.binding.root, before, tree.value)
    _ <- if (before == after && tree == aftertree &&
        GoalPhaseCandidateObservationContinuation.validDependencyRepository(admission, observed)) Consequence.unit
      else D.invalid("dependency repository/index changed during the explicitly requested snapshot")
  } yield observed

  def captureC(core: ActionCall.Core, controller: O.State, current: C.Saved,
    loadC: String => Consequence[Option[C.Saved]],
    saveC: (String, InstanceRecord, C.Payload) => Consequence[C.Saved],
    acknowledgeC: String => Consequence[Unit], chain: Option[DependencyChain] = None): Consequence[C.Saved] = current.payload.dependencyAdmission match {
    case None => Consequence.success(current)
    case Some(admission) => for {
      _ <- if (D.admissionValid(admission) && controller.cursor < controller.plan.steps.size &&
          controller.plan.steps(controller.cursor).identity == admission.binding.step) Consequence.unit
        else D.invalid("dependency capture has no current adopted Step binding")
      binding = admission.binding
      _ <- repositoryC(core, admission, false, chain)
      staged <- binding.paths.zipWithIndex.foldLeft(Consequence.success(current)) { case (prior, (path, index)) => for {
        previous <- prior
        retained <- loadC("dependency-stage-intent-" + index)
        _ <- if (retained.isEmpty) Consequence.unit
          else D.invalid("dependency staging already has an intent; retain its outcome without automatic replay")
        _ <- repositoryC(core, admission, false, chain)
        selection <- GitSelectionId.parseC("goal-path-" + (index + 1))
        intent <- saveC("dependency-stage-intent-" + index, previous.record, previous.payload)
        _ <- acknowledgeC("goal-dependency-stage-prepared:" + path)
        _ <- _value_c(core, binding, GitOperation.StageSelectedAll(selection))
        saved <- saveC("dependency-stage-result-" + index, intent.record,
          previous.payload.copy(dependencyStaged = previous.payload.dependencyStaged :+ path))
        _ <- acknowledgeC("goal-dependency-stage-recorded:" + path)
      } yield saved }
      result <- observeC(core, controller, staged, current.payload.sourceRevision, saveC, acknowledgeC, chain)
    } yield result
  }

  /** One newly authorized observation; staging and source normalization are never called here. */
  def observeC(core: ActionCall.Core, controller: O.State, staged: C.Saved, stageSource: String,
    saveC: (String, InstanceRecord, C.Payload) => Consequence[C.Saved],
    acknowledgeC: String => Consequence[Unit] = _ => Consequence.unit,
    chain: Option[DependencyChain] = None): Consequence[C.Saved] = for {
    admission <- staged.payload.dependencyAdmission.map(Consequence.success).getOrElse(D.invalid("remaining dependency has no binding"))
    binding = admission.binding
    _ <- if (D.admissionValid(admission) && staged.payload.dependency.isEmpty &&
        staged.payload.dependencyStaged == binding.paths) Consequence.unit
      else D.invalid("remaining observation requires completed stages and no claimed capture")
    intent <- saveC("dependency-observation-intent", staged.record, staged.payload)
    _ <- acknowledgeC("goal-dependency-observation-prepared")
    before <- repositoryC(core, admission, true, chain)
    tree <- _value_c(core, binding, GitOperation.IndexTree)
    base <- GitObjectId.parseC(baseline(admission))
    paths <- _value_c(core, binding, GitOperation.PendingPaths(base))
    diff <- _value_c(core, binding, GitOperation.PendingDiff(base))
    after <- repositoryC(core, admission, true, chain)
    aftertree <- _value_c(core, binding, GitOperation.IndexTree)
    _ <- if (before == after && tree == aftertree) Consequence.unit
      else D.invalid("dependency repository/index changed while observing the incremental candidate")
    capture = D.Capture(staged.record.identity.value, ContextReference(controller.plan.identity, controller.plan.revision),
      binding.step, controller.generation, staged.payload.sourceRevision, controller.base, admission,
      O.Snapshot(before.head, tree.value), paths.map(_.value), diff.text)
    _ <- if (!diff.isBinary && D.captureValid(capture)) Consequence.unit else D.invalid("invalid actual dependency candidate")
    _ <- freshnessC(core, capture, chain)
    origin = D.CandidateOrigin(stageSource, staged.payload.sourceRevision, (intent.record.revision.value + 1).toString)
    _ <- if (D.originValid(origin)) Consequence.unit else D.invalid("dependency observation provenance is invalid")
    result <- saveC("dependency-observation-result", intent.record,
      staged.payload.copy(dependency = Some(capture), dependencyOrigin = Some(origin)))
    _ <- acknowledgeC("goal-dependency-observation-recorded")
  } yield result

  /** The origin selects actual native namespaces; capture provenance is never rebased. */
  def retainedC(candidate: C.Saved, loadC: String => Consequence[Option[C.Saved]],
    loadSourceC: Option[(String, String) => Consequence[Option[C.Saved]]] = None): Consequence[Unit] =
    candidate.payload.dependency match {
      case None => if (candidate.payload.dependencyAdmission.isEmpty && candidate.payload.dependencyOrigin.isEmpty)
        Consequence.unit else D.invalid("adopted dependency has no complete captured candidate")
      case Some(capture) =>
        val origin = candidate.payload.dependencyOrigin
        def _required_(key: String): Consequence[C.Saved] = {
          val source = origin.map(o => if (key.startsWith("dependency-stage-")) o.stageSourceRevision else o.observationSourceRevision)
          val selected = source match {
            case Some(value) if value != candidate.payload.sourceRevision => loadSourceC.map(_(value, key))
              .getOrElse(D.invalid("cross-direction proof requires the actual source loader"))
            case _ => loadC(key)
          }
          selected.flatMap(_.map(Consequence.success).getOrElse(D.invalid("missing native dependency fact: " + key)))
        }
        def _ancestor_(before: C.Saved, after: C.Saved): Boolean = before.record.identity == after.record.identity &&
          before.record.definition == after.record.definition && before.record.revision.value <= after.record.revision.value &&
          after.record.history.take(before.record.history.size) == before.record.history
        def _adjacent_(before: C.Saved, after: C.Saved, key: String): Consequence[Unit] =
          if (_ancestor_(before, after) && after.record.revision.value == before.record.revision.value + 1 &&
              after.record.causation.value == "ordered-candidate:" + key) Consequence.unit
          else D.invalid("dependency intent/result crosses its retained predecessor")
        for {
          _ <- if (D.captureValid(capture) && origin.forall(o => D.originValid(o) &&
              o.observationSourceRevision == capture.sourceRevision)) Consequence.unit
            else D.invalid("invalid retained dependency origin")
          first <- _required_("dependency-stage-intent-0")
          _ <- if (first.payload.dependencyAdmission.contains(capture.admission) && first.payload.dependency.isEmpty &&
              first.payload.dependencyOrigin.isEmpty && first.payload.dependencyStaged.isEmpty &&
              first.payload.sourceRevision == origin.map(_.stageSourceRevision).getOrElse(candidate.payload.sourceRevision) &&
              first.payload.step == candidate.payload.step && _ancestor_(first, candidate)) Consequence.unit
            else D.invalid("dependency staging has a different original source, binding or history")
          last <- capture.admission.binding.paths.zipWithIndex.foldLeft(Consequence.success(first)) {
            case (prior, (path, index)) => for {
              previous <- prior
              intent <- _required_("dependency-stage-intent-" + index)
              _ <- if (index == 0) Consequence.unit else _adjacent_(previous, intent, "dependency-stage-intent-" + index)
              _ <- if (intent.payload == previous.payload) Consequence.unit else D.invalid("dependency stage intent rewrites its prefix")
              result <- _required_("dependency-stage-result-" + index)
              _ <- _adjacent_(intent, result, "dependency-stage-result-" + index)
              _ <- if (result.payload == intent.payload.copy(dependencyStaged = intent.payload.dependencyStaged :+ path))
                Consequence.unit else D.invalid("dependency stage result changes more than the selected path prefix")
            } yield result
          }
          intent <- _required_("dependency-observation-intent")
          _ <- if (last.payload.sourceRevision == intent.payload.sourceRevision)
            _adjacent_(last, intent, "dependency-observation-intent")
            else for {
              direction <- GoalPhaseNativeHistory.prefixC(candidate.record, InstanceRevision(intent.payload.sourceRevision.toLong))
              _ <- if (_ancestor_(last, intent) && direction.revision.value + 2 == intent.record.revision.value &&
                  intent.record.history.dropRight(2) == direction.history &&
                  (direction.causation.value.startsWith("ordered-candidate-recovery:") ||
                    direction.causation.value.startsWith("ordered-candidate-observation-continuation:"))) Consequence.unit
                else D.invalid("dependency observation has no actual cross-direction predecessor")
            } yield ()
          _ <- if (intent.payload == last.payload.copy(sourceRevision = intent.payload.sourceRevision)) Consequence.unit
            else D.invalid("dependency observation rewrites its completed stage prefix")
          result <- _required_("dependency-observation-result")
          _ <- _adjacent_(intent, result, "dependency-observation-result")
          actualorigin = D.CandidateOrigin(first.payload.sourceRevision, intent.payload.sourceRevision, result.record.revision.value.toString)
          _ <- if (result.payload == intent.payload.copy(dependency = Some(capture), dependencyOrigin = result.payload.dependencyOrigin) &&
              result.payload.dependencyOrigin.forall(_ == actualorigin) && origin.forall(_ == actualorigin) &&
              _ancestor_(result, candidate)) Consequence.unit
            else D.invalid("dependency capture is not its actual retained observation result")
        } yield ()
    }

  def freshnessC(core: ActionCall.Core, capture: D.Capture, chain: Option[DependencyChain] = None): Consequence[Unit] = for {
    _ <- if (D.captureValid(capture)) Consequence.unit else D.invalid("invalid retained dependency capture")
    _ <- chain.fold(resolvedC(capture.admission, None))(_.captureC(capture))
    binding = capture.admission.binding
    actual <- repositoryC(core, capture.admission, true, chain)
    tree <- _value_c(core, binding, GitOperation.IndexTree)
    base <- GitObjectId.parseC(baseline(capture.admission))
    paths <- _value_c(core, binding, GitOperation.PendingPaths(base))
    diff <- _value_c(core, binding, GitOperation.PendingDiff(base))
    after <- repositoryC(core, capture.admission, true, chain)
    aftertree <- _value_c(core, binding, GitOperation.IndexTree)
    _ <- if (actual == after && tree == aftertree && actual.head == capture.candidate.head &&
        tree.value == capture.candidate.tree && paths.map(_.value) == capture.changedPaths &&
        !diff.isBinary && diff.text == capture.diff) Consequence.unit
      else D.invalid("dependency candidate drifted; historical receipts cannot validate changed bytes")
  } yield ()

  /** Ancestors remain proved by the contiguous walker; only a proved successor
    * replaces an ancestor's live-tree comparison. Unrelated histories stay fresh. */
  def chainFreshnessC(core: ActionCall.Core, chain: Option[DependencyChain], current: Vector[D.Capture]): Consequence[Unit] = {
    val accepted = chain.toVector.flatMap(_.accepted.map(_.capture))
    val all = (accepted ++ current).distinct
    all.foldLeft(Consequence.unit)((prior, capture) => prior.flatMap { _ =>
      if (D.captureValid(capture)) chain.fold(resolvedC(capture.admission, None))(_.captureC(capture))
      else D.invalid("invalid dependency chain capture")
    }).flatMap { _ =>
      all.groupBy(_.admission.binding.dependency.identity).values.foldLeft(Consequence.unit) { (prior, history) =>
        // An absent-prefix history retains its original strict full-tree obligation.
        // Only a genuinely reconstructed prefixed successor supersedes live ancestor comparison.
        val selected = if (history.last.admission.acceptedPrefix.nonEmpty) Vector(history.last) else history
        selected.foldLeft(prior)((previous, capture) => previous.flatMap(_ => freshnessC(core, capture, chain)))
      }
    }
  }

  def checksC(primary: Vector[GoalPhaseNativeValidation.Check], capture: Option[D.Capture]):
    Consequence[Vector[GoalPhaseNativeValidation.Check]] = capture match {
    case None => Consequence.success(primary)
    case Some(value) =>
      val combined = primary ++ value.admission.binding.checks.map(_.copy(dependency = Some(value.admission.binding.identity)))
      if (D.captureValid(value) && combined.size <= 16 && combined.map(_.identity).distinct.size == combined.size &&
          combined.map(_.ordinal).distinct.size == combined.size && primary.forall(_.metadata.isEmpty))
        Consequence.success(combined)
      else D.invalid("dependency checks require unique fixed identities/ordinals and an ordinary primary check policy")
  }
  def workspaceC(check: GoalPhaseNativeValidation.Check, capture: Option[D.Capture]): Consequence[Option[String]] =
    check.dependency match {
      case None => Consequence.success(None)
      case Some(identity) => capture.filter(c => D.captureValid(c) && c.admission.binding.identity == identity &&
          c.admission.binding.checks.exists(_.copy(dependency = Some(identity)) == check))
        .map(c => Consequence.success(Some(c.admission.binding.dependency.workspace)))
        .getOrElse(D.invalid("check has no exact captured dependency binding"))
    }
  def publicView(payload: GoalPhaseNativeValidation.Payload): Option[org.goldenport.record.Record] = payload.dependency.map { capture =>
    import D.given
    val indices = payload.selected.zipWithIndex.filter(_._1.dependency.nonEmpty)
    val value = io.circe.Json.obj("capture" -> capture.asJson,
      "selectedChecks" -> indices.map(_._1).asJson,
      "checks" -> indices.flatMap((_, i) => payload.receipts.lift(i)).asJson,
      "executions" -> payload.dependencyExecutions.asJson,
      "stopped" -> payload.stopped.filter(r => indices.exists(_._1.identity == r.identity)).asJson,
      "admissionFailures" -> payload.admissionFailures.filter(r => indices.exists(_._1.identity == r.checkIdentity)).asJson,
      "uncertainties" -> payload.uncertainties.filter(r => indices.exists(_._1.identity == r.checkIdentity)).asJson,
      "newAttempts" -> payload.newAttempts.filter(r => indices.exists(_._1.identity == r.checkIdentity)).asJson)
    GoalPhaseNativeMetadata.record(value)
  }
}
