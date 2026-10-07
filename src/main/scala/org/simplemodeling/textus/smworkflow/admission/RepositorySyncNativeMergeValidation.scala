package org.simplemodeling.textus.smworkflow.admission

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{ProcessExecutionResult, ProcessExecutionTermination}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import RepositorySyncNativeProgressStore as N
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Actual managed checks, with native intent/result boundaries. Nothing here
  * accepts a caller's success claim or runs an unrecorded retained intent again.
  * @since   Oct.  6, 2026
  * @version Oct.  8, 2026
  */
private[smworkflow] object RepositorySyncNativeMergeValidation {
  final case class Check(identity: String, ordinal: Int)
  final case class Policy(identity: String, revision: Int, mode: String, reason: String, checks: Vector[Check]) {
    def validateC: Consequence[Unit] = if (id(identity) && revision > 0 && reason != null &&
        reason.trim.nonEmpty && reason.length <= 4096 && checks != null && checks.size <= 16 &&
        checks.forall(c => c != null && id(c.identity) && !Set("index-local", "index-remote")(c.identity) &&
          c.ordinal > 0 && c.ordinal <= 16) && checks.map(_.identity).distinct.size == checks.size &&
        checks.map(_.ordinal).distinct.size == checks.size &&
        ((mode == "explicit-no-product-tests" && checks.isEmpty) || (mode == "required" && checks.nonEmpty))) Consequence.unit
      else N.invalid("pending-merge validation policy is incomplete or contradictory")
  }
  final case class Receipt(identity: String, termination: String, exitCode: Option[Int], stdout: String, stderr: String)
  final case class State(policy: Policy, sourceRevision: String, receipts: Vector[Receipt])

  def waitC(store: N.Store, pending: N.Stage, policy: Option[Policy],
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port] = None): Consequence[String] = for {
    stage <- policy match {
      case Some(value) => advanceC(store, pending, value)
      case None => Consequence.success(pending)
    }
    handle <- WorkflowHandle.fromRecordC(SmWorkflowComponent.componentId, stage.record)
    detail = stage.payload.stage match {
      case "MergeValidated" => "Actual selected merge checks passed. Independent semantic review, commit, push and convergence remain incomplete."
      case "ValidationChecking" if stage.payload.validation.exists(s => !successful(s.receipts)) =>
        "Actual failed or indeterminate validation is retained. Repair or Human direction is required; checks are not replayed."
      case "ValidationChecking" => "A retained check intent has no recorded result. Validation is indeterminate; no automatic replay."
      case _ => "An explicit host validation policy is required. No product-test skip, review acceptance or convergence is inferred."
    }
    wire <- if (stage.payload.stage == "MergeValidated" && review.nonEmpty) review.get.advanceC(store.identity, store.core)
      else WorkflowWaitJsonV1.encodeC(WorkflowInteraction[Nothing, Nothing](handle,
      WorkflowContinuation.Wait(StateMachineRevision(stage.record.revision.value.toString),
        ContextSnapshot(handle.workflowRevision.value), MinimalPresentation("Pending merge validation", detail))))
  } yield wire

  def advanceC(store: N.Store, pending: N.Stage, policy: Policy): Consequence[N.Stage] = for {
    _ <- policy.validateC
    _ <- if (pending.payload.stage == "PendingMergePrepared" && pending.payload.indexTree.nonEmpty &&
        pending.payload.mergeHead.nonEmpty && pending.payload.after.nonEmpty) Consequence.unit
      else N.invalid("validation requires the genuine selected pending merge")
    selected <- selectedC(store, pending, policy, merge = true)
  } yield selected

  /** Equal, local-ahead and fast-forward integrations introduce no semantic
    * merge candidate. Check the actual clean selected index and host policy;
    * do not issue an unrelated merge review or manufacture a merge commit.
    */
  def advanceIntegratedC(store: N.Store, integrated: N.Stage, policy: Policy): Consequence[N.Stage] = for {
    _ <- policy.validateC
    _ <- if (integrated.payload.stage == "Integrated" && integrated.payload.mergeHead.isEmpty &&
        integrated.payload.method.exists(Set("equal", "local-ahead", "fast-forward")) &&
        integrated.payload.after.nonEmpty) Consequence.unit
      else N.invalid("validation requires the genuine selected clean nonmerge integration")
    source = integrated.copy(payload = integrated.payload.copy(indexTree = Some(integrated.payload.after.get.tree)))
    selected <- selectedC(store, source, policy, merge = false)
  } yield selected

  private def selectedC(store: N.Store, pending: N.Stage, policy: Policy, merge: Boolean): Consequence[N.Stage] = for {
    _ <- preconditionsC(store, pending)
    complete <- store.loadC(if (merge) "merge-validated" else "integration-validated")
    selected <- complete match {
      case Some(value) => for {
        _ <- if (value.payload.validation.exists(s => s.policy == policy &&
            s.sourceRevision == pending.record.revision.value.toString && s.receipts.size == policy.checks.size + (if (merge) 2 else 1) && successful(s.receipts, merge)))
          Consequence.unit else N.invalid("retained merge validation crosses its selected policy or source")
      } yield value
      case None => checksC(store, pending, policy, merge)
    }
  } yield selected

  private def checksC(store: N.Store, pending: N.Stage, policy: Policy, merge: Boolean): Consequence[N.Stage] = {
    val structural = if (merge) 2 else 1
    val checks = Vector(Check("index-local", 0)) ++ (if (merge) Vector(Check("index-remote", 0)) else Vector.empty) ++ policy.checks
    val checking = if (merge) "ValidationChecking" else "IntegrationValidationChecking"
    checks.zipWithIndex.foldLeft(Consequence.success(pending)) { case (prior, (check, index)) => prior.flatMap { current =>
      if (current.payload.validation.exists(s => s.receipts.size < index || !successful(s.receipts, merge))) Consequence.success(current)
      else for {
        before <- store.loadC("validation-intent-" + index)
        recorded <- store.loadC("validation-result-" + index)
        saved <- recorded match {
          case Some(value) => for {
            _ <- matchesC(value, pending, policy, index + 1, checks)
          } yield value
          case None => before match {
            case Some(value) => matchesC(value, pending, policy, index, checks).map(_ => value)
            case None => for {
              _ <- preconditionsC(store, pending)
              receipts = current.payload.validation.map(_.receipts).getOrElse(Vector.empty)
              state = State(policy, pending.record.revision.value.toString, receipts)
              intent <- store.saveC("validation-intent-" + index, "native-check-prepared", current.record,
                pending.payload.copy(stage = checking, validation = Some(state)))
              _ <- store.acknowledgeC("merge-check-prepared:" + check.identity)
              _ <- preconditionsC(store, pending)
              result <- if (index < structural) E.executeNativeGitC(store.core,
                E.NativeGitEffect.CheckPendingIndex(if (index == 0) pending.payload.after.get.head else pending.payload.mergeHead.get))
                else E.repositoryValidationC(store.core, check.ordinal)
              _ <- store.acknowledgeC("merge-check-executed:" + check.identity)
              receipt <- receiptC(check.identity, result)
              _ <- preconditionsC(store, pending)
              saved <- store.saveC("validation-result-" + index, "native-check-recorded", intent.record,
                pending.payload.copy(stage = checking, validation = Some(state.copy(receipts = receipts :+ receipt))))
              _ <- store.acknowledgeC("merge-check-recorded:" + check.identity)
            } yield saved
          }
        }
      } yield saved
    }}.flatMap { current =>
      current.payload.validation match {
        case Some(state) if state.receipts.size == checks.size && successful(state.receipts, merge) => for {
          _ <- preconditionsC(store, pending)
          validated <- store.saveC(if (merge) "merge-validated" else "integration-validated", "native-validation-admitted", current.record,
            pending.payload.copy(stage = if (merge) "MergeValidated" else "IntegrationValidated", validation = Some(state)))
          _ <- store.acknowledgeC(if (merge) "merge-validated" else "integration-validated")
        } yield validated
        case _ => Consequence.success(current)
      }
    }
  }

  private def matchesC(stage: N.Stage, source: N.Stage, policy: Policy, count: Int, checks: Vector[Check]): Consequence[Unit] =
    if (stage.payload.validation.exists(s => s.policy == policy && s.sourceRevision == source.record.revision.value.toString &&
        s.receipts.size == count && s.receipts.map(_.identity) ==
          checks.map(_.identity).take(count)) &&
        stage.payload.indexTree == source.payload.indexTree && stage.payload.mergeHead == source.payload.mergeHead)
      Consequence.unit else N.invalid("retained check crosses its actual policy, plan or pending merge")

  private[admission] def preconditionsC(store: N.Store, pending: N.Stage): Consequence[Unit] = for {
    native <- G.inspectC(store.core)
    _ <- store.scopeC(native.observed)
    tree <- G.indexTreeC(store.core)
    _ <- if (pending.payload.after.contains(native.observed) && pending.payload.indexTree.contains(tree) &&
        pending.payload.mergeHead == native.mergeHead && (if (pending.payload.mergeHead.nonEmpty)
          native.observed.head == pending.payload.before.head && native.observed.trackingTip == pending.payload.mergeHead &&
            native.observed.dirty.forall(p => !p.unmerged && p.worktree == ' ' && p.index != '?' && p.index != '!')
        else pending.payload.method.exists(Set("equal", "local-ahead", "fast-forward")) &&
          native.observed.dirty.isEmpty && native.observed.tree == tree)) Consequence.unit
      else N.invalid("actual pending merge cannot establish validation preconditions")
  } yield ()

  private[admission] def receiptC(identity: String, result: ProcessExecutionResult): Consequence[Receipt] = {
    if (!id(identity) || result == null || result.stdout == null || result.stderr == null ||
        result.stdout.truncated || result.stderr.truncated ||
        result.stdout.byteCount != result.stdout.content.size.toLong || result.stderr.byteCount != result.stderr.content.size.toLong ||
        result.stdout.byteCount > 65536 || result.stderr.byteCount > 65536)
      N.uncertain("validation result lacks complete bounded actual output; retained intent is not replayable")
    else try {
      def text(bytes: Vector[Byte]) = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toArray)).toString
      val out = text(result.stdout.content); val err = text(result.stderr.content)
      if (out.contains('\u0000') || err.contains('\u0000')) N.uncertain("validation capture is not ordinary text")
      else result.termination match {
        case ProcessExecutionTermination.Exited(code) => Consequence.success(Receipt(identity, "exited", Some(code), out, err))
        case _ => Consequence.success(Receipt(identity, "indeterminate", None, out, err))
      }
    } catch { case scala.util.control.NonFatal(_) => N.uncertain("validation capture is not UTF-8") }
  }
  /** Structural failures newly common to both parents block. Findings inherited
    * from only one parent are not newly introduced merge defects.
    */
  private def successful(receipts: Vector[Receipt], merge: Boolean = true): Boolean = {
    def structural(r: Receipt) = r.termination == "exited" && r.stderr.isEmpty &&
      ((r.exitCode.contains(0) && r.stdout.isEmpty) || (r.exitCode.contains(2) && findings(r.stdout).nonEmpty))
    val count = if (merge) 2 else 1
    val first = receipts.take(count)
    first.forall(structural) && (first.size < 2 ||
      (findings(first(0).stdout) intersect findings(first(1).stdout)).isEmpty) &&
      receipts.drop(count).forall(r => r.termination == "exited" && r.exitCode.contains(0))
  }
  private def findings(text: String): Set[String] =
    text.split("\n", -1).filter(line => line.nonEmpty && !line.startsWith("+")).toSet
  private def id(value: String): Boolean = value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
}
