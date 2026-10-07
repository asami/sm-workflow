package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Json, Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
import RepositorySyncNativeProgressStore as N
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Managed effects after actual paired review acceptance. Native intent is
  * durable before every effect. Retained unknown effects are observed, never
  * repeated. Git and Workflow storage are not one atomic transaction.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncNativeConvergence {
  enum Effect { case CommitPending, Push, FinalFetch }
  trait AuthorityPort {
    // Trusted host reads its current human-selected scope/grants. Review text
    // and a successful Start are not grants for these additional effects.
    def admitC(core: ActionCall.Core, originalCommand: Json, target: Target, effect: Effect): Consequence[Unit]
  }
  final case class Result(instanceIdentity: String, repositoryRoot: String, localBranch: String,
    remote: String, remoteBranch: String, tip: String, tree: String)
  private given Encoder[Result] = deriveEncoder
  private given Decoder[Result] = deriveDecoder
  val resultCodec: WorkflowResultJsonV1.PayloadCodec[Result] = new WorkflowResultJsonV1.PayloadCodec[Result] {
    val typeIdentity = "RepositorySyncNativeConvergenceResult"
    def encode(value: Result): Json = value.asJson
    def decode(value: Json): Either[String, Result] = for {
      fields <- Option(value).flatMap(_.asObject).filter(_.keys.toSet ==
        Set("instanceIdentity", "repositoryRoot", "localBranch", "remote", "remoteBranch", "tip", "tree"))
        .toRight("Native convergence result differs")
      result <- Json.fromJsonObject(fields).as[Result].left.map(_ => "Native convergence result is incomplete")
      _ <- Either.cond(Vector(result.instanceIdentity, result.repositoryRoot, result.localBranch, result.remote,
        result.remoteBranch).forall(v => v != null && v.nonEmpty) &&
        Vector(result.tip, result.tree).forall(v => v != null && v.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")),
        (), "Native convergence identities are incomplete")
    } yield result
  }

  def advanceC(store: N.Store, accepted: N.Stage, authority: AuthorityPort): Consequence[String] = for {
    _ <- if (authority != null && (accepted.payload.stage == "ReviewAccepted" ||
        (accepted.payload.stage == "IntegrationValidated" && accepted.payload.mergeHead.isEmpty &&
          accepted.payload.after.nonEmpty))) Consequence.unit
      else N.invalid("convergence requires actual paired merge acceptance or clean integration validation and host authority")
    complete <- store.loadC("converged")
    finalStage <- complete match {
      case Some(value) => matchesC(accepted, value).map(_ => value)
      case None => for {
        committed <- if (accepted.payload.stage == "IntegrationValidated")
          Consequence.success(accepted.copy(payload = accepted.payload.copy(finalTip = Some(accepted.payload.after.get.head))))
          else commitC(store, accepted, authority)
        pushed <- pushC(store, accepted, committed, authority)
        fetched <- finalFetchC(store, accepted, pushed, authority)
        actual <- convergedC(store, fetched)
        saved <- store.saveC("converged", "native-convergence-observed", fetched.record,
          fetched.payload.copy(stage = "Converged", after = Some(actual)))
        _ <- store.acknowledgeC("convergence-observed")
      } yield saved
    }
    completed <- store.closeC(finalStage)
    _ <- store.acknowledgeC("converged")
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, completed)
    result = Result(store.identity, finalStage.payload.root, store.declaration.target.localBranch,
      store.declaration.target.remote, store.declaration.target.remoteBranch,
      finalStage.payload.finalTip.get, finalStage.payload.indexTree.get)
    terminal <- WorkflowProtocolV1.projectTerminalC(handle, completed, TypedValue(resultCodec.typeIdentity, result),
      MinimalPresentation("Repository synchronized", "Actual selected local, tracking and remote tips converged with a clean repository."))
    wire <- WorkflowTerminalJsonV1.encodeC(terminal, resultCodec)
  } yield wire

  private def commitC(store: N.Store, accepted: N.Stage, authority: AuthorityPort): Consequence[N.Stage] = for {
    observed <- store.loadC("commit-observed")
    committed <- observed match {
      case Some(value) => matchesC(accepted, value).map(_ => value)
      case None => for {
        prior <- store.loadC("commit-intent")
        intent <- prior match {
          case Some(value) => matchesC(accepted, value).map(_ => value)
          case None => for {
            _ <- RepositorySyncNativeMergeValidation.preconditionsC(store, accepted)
            _ <- authorizeC(store, authority, Effect.CommitPending)
            saved <- store.saveC("commit-intent", "native-commit-prepared", accepted.record,
              accepted.payload.copy(stage = "NativeCommitPreparing"))
            _ <- store.acknowledgeC("commit-prepared")
            _ <- RepositorySyncNativeMergeValidation.preconditionsC(store, accepted)
            _ <- authorizeC(store, authority, Effect.CommitPending)
            _ <- E.executeC(store.core, E.Purpose.GitCommitPending, "")
            _ <- store.acknowledgeC("commit-executed")
          } yield saved
        }
        actual <- G.inspectC(store.core)
        _ <- store.scopeC(actual.observed)
        metadata <- G.commitC(store.core, actual.observed.head)
        _ <- if (actual.mergeHead.isEmpty && actual.observed.dirty.isEmpty &&
            actual.observed.head != accepted.payload.before.head &&
            actual.observed.trackingTip == accepted.payload.mergeHead &&
            accepted.payload.indexTree.contains(actual.observed.tree) &&
            metadata.tree == actual.observed.tree && metadata.parents ==
              Vector(accepted.payload.before.head, accepted.payload.mergeHead.get)) Consequence.unit
          else N.uncertain("commit intent has no selected clean two-parent postimage; no automatic commit replay")
        saved <- store.saveC("commit-observed", "native-commit-observed", intent.record,
          accepted.payload.copy(stage = "NativeCommitObserved", finalTip = Some(actual.observed.head), after = Some(actual.observed)))
        _ <- store.acknowledgeC("commit-observed")
      } yield saved
    }
  } yield committed

  private def pushC(store: N.Store, accepted: N.Stage, committed: N.Stage, authority: AuthorityPort): Consequence[N.Stage] = for {
    observed <- store.loadC("push-observed")
    pushed <- observed match {
      case Some(value) => matchesC(accepted, value).map(_ => value)
      case None => for {
        prior <- store.loadC("push-intent")
        intent <- prior match {
          case Some(value) => matchesC(accepted, value).map(_ => value)
          case None => for {
            _ <- localC(store, committed)
            remote <- G.remoteTipC(store.core, store.declaration.target.remoteBranch)
            _ <- if (committed.payload.before.trackingTip.contains(remote) || committed.payload.finalTip.contains(remote)) Consequence.unit
              else N.invalid("remote changed outside selected convergence; no overwrite or force push")
            _ <- authorizeC(store, authority, Effect.Push)
            saved <- store.saveC("push-intent", "native-push-prepared", committed.record,
              committed.payload.copy(stage = "NativePushPreparing"))
            _ <- store.acknowledgeC("push-prepared")
            _ <- localC(store, committed)
            _ <- authorizeC(store, authority, Effect.Push)
            // A remote already at the selected tip needs observation, not another push.
            _ <- if (committed.payload.finalTip.contains(remote)) Consequence.unit else
              E.executeNativeGitC(store.core, E.NativeGitEffect.Push(committed.payload.finalTip.get,
                store.declaration.target.remoteBranch)).map(_ => ())
            _ <- store.acknowledgeC("push-executed")
          } yield saved
        }
        _ <- localC(store, committed)
        remote <- G.remoteTipC(store.core, store.declaration.target.remoteBranch)
        _ <- if (committed.payload.finalTip.contains(remote)) Consequence.unit
          else N.uncertain("push intent has no selected remote postimage; no automatic push replay")
        saved <- store.saveC("push-observed", "native-push-observed", intent.record,
          committed.payload.copy(stage = "NativePushObserved"))
        _ <- store.acknowledgeC("push-observed")
      } yield saved
    }
  } yield pushed

  private def finalFetchC(store: N.Store, accepted: N.Stage, pushed: N.Stage,
    authority: AuthorityPort): Consequence[N.Stage] = for {
    prior <- store.loadC("final-fetch-intent")
    intent <- prior match {
      case Some(value) => matchesC(accepted, value).map(_ => value)
      case None => for {
        _ <- localC(store, pushed)
        remote <- G.remoteTipC(store.core, store.declaration.target.remoteBranch)
        _ <- if (pushed.payload.finalTip.contains(remote)) Consequence.unit
          else N.invalid("remote changed before final fetch")
        _ <- authorizeC(store, authority, Effect.FinalFetch)
        saved <- store.saveC("final-fetch-intent", "native-final-fetch-prepared", pushed.record,
          pushed.payload.copy(stage = "NativeFinalFetching"))
        _ <- store.acknowledgeC("final-fetch-prepared")
        _ <- localC(store, pushed)
        _ <- authorizeC(store, authority, Effect.FinalFetch)
        _ <- E.executeC(store.core, E.Purpose.GitFetch, "")
        _ <- store.acknowledgeC("final-fetch-executed")
      } yield saved
    }
  } yield intent

  private def localC(store: N.Store, stage: N.Stage): Consequence[G.Observed] = for {
    native <- G.inspectC(store.core)
    _ <- store.scopeC(native.observed)
    _ <- if (native.mergeHead.isEmpty && native.observed.dirty.isEmpty &&
        stage.payload.finalTip.contains(native.observed.head) && stage.payload.indexTree.contains(native.observed.tree)) Consequence.unit
      else N.invalid("actual selected committed repository changed before convergence")
  } yield native.observed

  private def convergedC(store: N.Store, stage: N.Stage): Consequence[G.Observed] = for {
    actual <- localC(store, stage)
    remote <- G.remoteTipC(store.core, store.declaration.target.remoteBranch)
    _ <- if (stage.payload.finalTip.contains(remote) && actual.trackingTip.contains(remote) &&
        actual.counts.contains(G.Counts(0, 0)) && actual.mergeBase.contains(remote)) Consequence.unit
      else N.uncertain("final fetch intent has no actual equal clean local/tracking/remote postimage; no replay")
    finalLocal <- localC(store, stage)
    _ <- if (finalLocal == actual) Consequence.unit else N.invalid("repository changed during convergence observation")
  } yield finalLocal

  private def authorizeC(store: N.Store, authority: AuthorityPort, effect: Effect) =
    authority.admitC(store.core, store.original.originalCommand, store.declaration.target, effect)
  private def matchesC(accepted: N.Stage, stage: N.Stage): Consequence[Unit] =
    if (stage.payload.before == accepted.payload.before && stage.payload.indexTree == accepted.payload.indexTree &&
        stage.payload.mergeHead == accepted.payload.mergeHead && stage.payload.validation == accepted.payload.validation &&
        stage.record.history.take(accepted.record.history.size) == accepted.record.history) Consequence.unit
      else N.invalid("retained convergence crosses actual paired accepted candidate")
}
