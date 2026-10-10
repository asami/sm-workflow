package org.simplemodeling.textus.smworkflow.admission

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import io.circe.Json
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
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
  final case class State(mode: String, reason: String, selected: Vector[Check], sourceRevision: String, receipts: Vector[Receipt])
  private given Encoder[Check] = deriveEncoder
  private given Decoder[Check] = deriveDecoder
  private given Encoder[Receipt] = deriveEncoder
  private given Decoder[Receipt] = deriveDecoder
  private given Encoder[State] = deriveEncoder
  private given Decoder[State] = deriveDecoder
  private[admission] val stateCodec = new WorkflowResultJsonV1.PayloadCodec[State] {
    val typeIdentity = "RepositorySyncNativeValidationState"
    def encode(value: State): Json = value.asJson
    def decode(value: Json): Either[String, State] = for {
      fields <- value.asObject.filter(_.keys.toSet == Set("mode", "reason", "selected", "sourceRevision", "receipts"))
        .toRight("native validation facts have undeclared or missing fields")
      _ <- Either.cond(fields("selected").flatMap(_.asArray).exists(_.forall(v =>
        v.asObject.exists(_.keys.toSet == Set("identity", "ordinal")))) &&
        fields("receipts").flatMap(_.asArray).exists(_.forall(v => v.asObject.exists { f =>
          Set("identity", "termination", "stdout", "stderr").subsetOf(f.keys.toSet) &&
            f.keys.toSet.subsetOf(Set("identity", "termination", "stdout", "stderr", "exitCode"))
        })), (), "native validation facts contain undeclared nested fields")
      state <- Json.fromJsonObject(fields).as[State].left.map(_.message)
      _ <- Either.cond(state.sourceRevision.matches("[1-9][0-9]*") && state.reason.nonEmpty &&
        state.reason.length <= 4096 && state.selected.size <= 16 &&
        state.selected.forall(c => id(c.identity) && !Set("index-local", "index-remote")(c.identity) && c.ordinal > 0 && c.ordinal <= 16) &&
        state.selected.map(_.identity).distinct.size == state.selected.size &&
        state.selected.map(_.ordinal).distinct.size == state.selected.size &&
        ((state.mode == "explicit-no-product-tests" && state.selected.isEmpty) || (state.mode == "required" && state.selected.nonEmpty)) &&
        state.receipts.size <= 18 && state.receipts.forall(r => id(r.identity) &&
          r.stdout.getBytes(StandardCharsets.UTF_8).length <= 65536 && r.stderr.getBytes(StandardCharsets.UTF_8).length <= 65536 &&
          !r.stdout.contains('\u0000') && !r.stderr.contains('\u0000') &&
          ((r.termination == "exited" && r.exitCode.nonEmpty) || (r.termination == "indeterminate" && r.exitCode.isEmpty))),
        (), "native validation facts are incomplete or contradictory")
    } yield state
  }
  /** V1 kept the selected execution facts inside a policy copy. Extract only
    * those facts; its identity/revision never becomes a new witness or authority.
    */
  private[admission] def legacyStateJson(value: Json): Either[String, Json] = value.asObject.toRight("legacy validation is missing").flatMap { fields =>
    if (!fields.contains("policy")) Right(value)
    else for {
      _ <- Either.cond(fields.keys.toSet == Set("policy", "sourceRevision", "receipts"), (), "legacy validation has undeclared fields")
      policy <- fields("policy").flatMap(_.asObject).toRight("legacy selected validation facts are missing")
      _ <- Either.cond(policy.keys.toSet.subsetOf(Set("identity", "revision", "mode", "reason", "checks")), (), "legacy validation has undeclared policy fields")
      mode <- policy("mode").toRight("legacy validation mode is missing")
      reason <- policy("reason").toRight("legacy validation reason is missing")
      checks <- policy("checks").toRight("legacy selected check bindings are missing")
    } yield Json.obj("mode" -> mode, "reason" -> reason, "selected" -> checks,
      "sourceRevision" -> fields("sourceRevision").get, "receipts" -> fields("receipts").get)
  }

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
    source <- integrationSourceC(integrated)
    selected <- selectedC(store, source, policy, merge = false)
  } yield selected

  private[admission] def integrationSourceC(integrated: N.Stage): Consequence[N.Stage] =
    if (integrated.payload.stage == "Integrated" && integrated.payload.mergeHead.isEmpty &&
        integrated.payload.method.exists(Set("equal", "local-ahead", "fast-forward")) &&
        integrated.payload.after.nonEmpty) Consequence.success(integrated.copy(payload = integrated.payload.copy(indexTree = Some(integrated.payload.after.get.tree))))
      else N.invalid("validation requires the genuine selected clean nonmerge integration")

  private[admission] def completedC(value: N.Stage, source: N.Stage, policy: Policy, merge: Boolean): Consequence[Unit] = for {
    state <- value.payload.validation.map(Consequence.success).getOrElse(N.invalid("retained validation facts are missing"))
    _ <- matchesC(value, source, policy, state.selected.size + (if (merge) 2 else 1), merge)
    _ <- if (successful(state.receipts, merge)) Consequence.unit else N.invalid("retained validation did not pass")
  } yield ()

  private def selectedC(store: N.Store, pending: N.Stage, policy: Policy, merge: Boolean): Consequence[N.Stage] = for {
    _ <- preconditionsC(store, pending)
    complete <- store.loadC(if (merge) "merge-validated" else "integration-validated")
    selected <- complete match {
      case Some(value) => for {
        _ <- completedC(value, pending, policy, merge)
      } yield value
      case None => for {
        original <- store.loadC("validation-intent-0")
        selection <- original match {
          case Some(value) => matchesC(value, pending, policy, 0, merge).flatMap(_ =>
            value.payload.validation.map(Consequence.success).getOrElse(N.invalid("original validation selection is missing")))
          case None => Consequence.success(State(policy.mode, policy.reason, policy.checks, pending.record.revision.value.toString, Vector.empty))
        }
        checked <- checksC(store, pending, policy, selection, merge)
      } yield checked
    }
  } yield selected

  private def checksC(store: N.Store, pending: N.Stage, policy: Policy, selection: State, merge: Boolean): Consequence[N.Stage] = {
    val structural = if (merge) 2 else 1
    val checks = Vector(Check("index-local", 0)) ++ (if (merge) Vector(Check("index-remote", 0)) else Vector.empty) ++ selection.selected
    val checking = if (merge) "ValidationChecking" else "IntegrationValidationChecking"
    checks.zipWithIndex.foldLeft(Consequence.success(pending)) { case (prior, (check, index)) => prior.flatMap { current =>
      if (current.payload.validation.exists(s => s.receipts.size < index || !successful(s.receipts, merge))) Consequence.success(current)
      else for {
        before <- store.loadC("validation-intent-" + index)
        recorded <- store.loadC("validation-result-" + index)
        saved <- recorded match {
          case Some(value) => for {
            _ <- matchesC(value, pending, policy, index + 1, merge, Some(selection.selected))
          } yield value
          case None => before match {
            case Some(value) => matchesC(value, pending, policy, index, merge, Some(selection.selected)).map(_ => value)
            case None => for {
              _ <- preconditionsC(store, pending)
              receipts = current.payload.validation.map(_.receipts).getOrElse(Vector.empty)
              state = selection.copy(receipts = receipts)
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

  private[admission] def applies(state: State, policy: Policy): Boolean =
    state.mode == policy.mode && state.selected.forall(check => policy.checks.exists(_ == check))

  private[admission] def matchesC(stage: N.Stage, source: N.Stage, policy: Policy, count: Int, merge: Boolean,
    selected: Option[Vector[Check]] = None): Consequence[Unit] =
    if (stage.payload.validation.exists(s => applies(s, policy) && s.sourceRevision == source.record.revision.value.toString &&
        selected.forall(expected => expected.map(c => c.identity -> c.ordinal) == s.selected.map(c => c.identity -> c.ordinal)) &&
        s.receipts.size == count && s.receipts.map(_.identity) ==
          (Vector("index-local") ++ (if (merge) Vector("index-remote") else Vector.empty) ++ s.selected.map(_.identity)).take(count)) &&
        stage.payload.indexTree == source.payload.indexTree && stage.payload.mergeHead == source.payload.mergeHead &&
        stage.payload.before.head == source.payload.before.head && stage.payload.before.tree == source.payload.before.tree &&
        stage.payload.after.map(v => (v.branch, v.head, v.tree, v.trackingTip)) ==
          source.payload.after.map(v => (v.branch, v.head, v.tree, v.trackingTip)) &&
        SplitPhaseDefinitionBinding.includesState(stage.record, source.record))
      Consequence.unit else N.invalid("retained check crosses its selected executable bindings, source or native candidate")

  private[admission] def preconditionsC(store: N.Store, pending: N.Stage): Consequence[Unit] = for {
    native <- G.inspectC(store.core)
    _ <- store.scopeC(native.observed)
    tree <- G.indexTreeC(store.core)
    _ <- if (pending.payload.after.exists(v => v.head == native.observed.head && v.tree == native.observed.tree &&
        v.branch == native.observed.branch && v.trackingTip == native.observed.trackingTip) && pending.payload.indexTree.contains(tree) &&
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
