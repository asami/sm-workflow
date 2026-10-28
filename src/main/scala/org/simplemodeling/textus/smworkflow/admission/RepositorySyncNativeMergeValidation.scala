package org.simplemodeling.textus.smworkflow.admission

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.{GitExecutionOutcome, GitIndexCheck, GitObjectId, GitOperation,
  ProcessExecutionResult, ProcessExecutionTermination, SbtExecutionResult}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import RepositorySyncNativeProgressStore as N
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Actual managed checks, with native intent/result boundaries. Nothing here
  * accepts a caller's success claim or runs an unrecorded retained intent again.
  * @since   Oct.  6, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncNativeMergeValidation {
  final case class Check(identity: String, ordinal: Int, kind: WorkflowSbtValidation.Kind = WorkflowSbtValidation.Kind.Test)
  object Check {
    given Encoder[Check] = Encoder.instance(value => {
      val fields = Json.obj("identity" -> value.identity.asJson, "ordinal" -> value.ordinal.asJson)
      if (value.kind == WorkflowSbtValidation.Kind.Test) fields else fields.mapObject(_.add("kind", value.kind.asJson))
    })
    given Decoder[Check] = Decoder.instance(cursor => for {
      identity <- cursor.get[String]("identity")
      ordinal <- cursor.get[Int]("ordinal")
      kind <- WorkflowSbtValidation.checkKind(cursor)
    } yield Check(identity, ordinal, kind))
  }
  final case class Policy(identity: String, revision: Int, mode: String, reason: String, checks: Vector[Check],
    assessment: Option[RepositorySyncMergeAssessment.Policy] = None,
    impact: Option[RepositorySyncProgramImpact.Policy] = None) {
    def validateC: Consequence[Unit] = if (id(identity) && revision > 0 && reason != null &&
        reason.trim.nonEmpty && reason.length <= 4096 && checks != null && checks.size <= 16 &&
        checks.forall(c => c != null && id(c.identity) && !Set("index-local", "index-remote")(c.identity) &&
          c.kind != null && c.ordinal > 0 && c.ordinal <= 16) && checks.map(_.identity).distinct.size == checks.size &&
        checks.map(_.ordinal).distinct.size == checks.size &&
        assessment != null && impact != null &&
        ((mode == "explicit-no-product-tests" && checks.isEmpty) || (mode == "required" && checks.nonEmpty)))
      assessment.fold(Consequence.unit)(RepositorySyncMergeAssessment.validateC)
      else N.invalid("pending-merge validation policy is incomplete or contradictory")
  }
  object Policy {
    given Encoder[Policy] = deriveEncoder[Policy].mapJson(_.mapObject(_.filter {
      case ("assessment" | "impact", value) => !value.isNull
      case _ => true
    }))
    given Decoder[Policy] = deriveDecoder
  }
  final case class Receipt(identity: String, termination: String, exitCode: Option[Int], stdout: String, stderr: String,
    sbt: Option[WorkflowSbtValidation.Facts] = None,
    processObservation: Option[ProcessExecutionResult] = None)
  object Receipt {
    given Encoder[Receipt] = Encoder.instance(value => {
      if (value.processObservation.nonEmpty) Json.obj("schemaVersion" -> WorkflowSbtCapture.schema.asJson,
        "identity" -> value.identity.asJson, "sbt" -> value.sbt.get.asJson,
        "process" -> WorkflowSbtCapture.encode(value.processObservation.get))
      else {
      val fields = Json.obj("identity" -> value.identity.asJson, "termination" -> value.termination.asJson,
        "exitCode" -> value.exitCode.asJson, "stdout" -> value.stdout.asJson, "stderr" -> value.stderr.asJson)
      value.sbt.fold(fields)(facts => fields.mapObject(_.add("sbt", facts.asJson)))
      }
    })
    given Decoder[Receipt] = Decoder.instance(cursor => if (cursor.downField("schemaVersion").focus.nonEmpty) for {
      schema <- cursor.get[String]("schemaVersion")
      _ <- Either.cond(schema == WorkflowSbtCapture.schema && cursor.keys.exists(_.toSet ==
        Set("schemaVersion", "identity", "sbt", "process")), (), DecodingFailure("Unknown or malformed raw SBT receipt", cursor.history))
      identity <- cursor.get[String]("identity")
      facts <- cursor.get[WorkflowSbtValidation.Facts]("sbt")
      raw <- cursor.get[Json]("process")
      process <- WorkflowSbtCapture.decode(raw).left.map(DecodingFailure(_, cursor.history))
      _ <- Either.cond((id(identity) || (identity.startsWith("metadata:") && GoalPhaseNativeMetadata.runtimeIdentityValid(identity))) &&
        WorkflowSbtCapture.valid(process, facts), (), DecodingFailure("Contradictory raw SBT observation", cursor.history))
    } yield Receipt(identity, WorkflowSbtCapture.termination(process), WorkflowSbtCapture.exitCode(process),
      WorkflowSbtCapture.text(process.stdout).value, WorkflowSbtCapture.text(process.stderr).value, Some(facts), Some(process))
    else for {
      identity <- cursor.get[String]("identity")
      termination <- cursor.get[String]("termination")
      exit <- cursor.get[Option[Int]]("exitCode")
      stdout <- cursor.get[String]("stdout")
      stderr <- cursor.get[String]("stderr")
      sbt <- if (cursor.keys.exists(_.exists(_ == "sbt"))) cursor.get[WorkflowSbtValidation.Facts]("sbt").map(Some(_))
        else Right(None)
    } yield Receipt(identity, termination, exit, stdout, stderr, sbt))

    /** The historical merge-repair parent derived six fields, including sbt:null. */
    val mergeRepairEncoder: Encoder[Receipt] = Encoder.instance(value =>
      if (value.processObservation.nonEmpty) summon[Encoder[Receipt]].apply(value)
      else Json.obj("identity" -> value.identity.asJson, "termination" -> value.termination.asJson,
        "exitCode" -> value.exitCode.asJson, "stdout" -> value.stdout.asJson,
        "stderr" -> value.stderr.asJson, "sbt" -> value.sbt.asJson))

    val mergeRepairDecoder: Decoder[Receipt] = Decoder.instance(cursor =>
      if (cursor.downField("schemaVersion").focus.nonEmpty) summon[Decoder[Receipt]].apply(cursor)
      else for {
        identity <- cursor.get[String]("identity")
        termination <- cursor.get[String]("termination")
        exit <- cursor.get[Option[Int]]("exitCode")
        stdout <- cursor.get[String]("stdout")
        stderr <- cursor.get[String]("stderr")
        sbt <- cursor.get[Option[WorkflowSbtValidation.Facts]]("sbt")
      } yield Receipt(identity, termination, exit, stdout, stderr, sbt))
  }
  final case class State(mode: String, reason: String, selected: Vector[Check], sourceRevision: String, receipts: Vector[Receipt],
    inputs: Option[RepositorySyncDocumentInputs.Assessment] = None,
    impact: Option[RepositorySyncProgramImpact.Selection] = None)
  private given Encoder[State] = deriveEncoder[State].mapJson(_.mapObject(_.filter {
    case ("inputs" | "impact", value) => !value.isNull
    case _ => true
  }))
  private given Decoder[State] = deriveDecoder
  private[admission] val stateCodec = new WorkflowResultJsonV1.PayloadCodec[State] {
    val typeIdentity = "RepositorySyncNativeValidationState"
    def encode(value: State): Json = value.asJson
    def decode(value: Json): Either[String, State] =
      Option(value).toRight("native validation facts are missing").flatMap(_.as[State].left.map(_.message))
  }

  def waitC(store: N.Store, pending: N.Stage, policy: Option[Policy],
    review: Option[RepositorySyncNativeMergeReviewContinuation.Port] = None,
    namespace: String = ""): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
    stage <- policy match {
      case Some(value) => advanceC(store, pending, value, namespace)
      case None => Consequence.success(pending)
    }
    detail = stage.payload.stage match {
      case "MergeValidated" => "Actual selected merge checks passed. Independent semantic review, commit, push and convergence remain incomplete."
      case "ValidationChecking" if stage.payload.validation.exists(s => !successful(s.receipts, selected = s.selected)) =>
        "Actual failed or indeterminate validation is retained. Repair or Human direction is required; checks are not replayed."
      case "ValidationChecking" => "A retained check intent has no recorded result. Validation is indeterminate; no automatic replay."
      case _ => "An explicit host validation policy is required. No product-test skip, review acceptance or convergence is inferred."
    }
    wire <- if (stage.payload.stage == "MergeValidated" && review.nonEmpty) review.get.advanceC(store.identity, store.core)
      else Consequence.success(NativeWorkflowOperationOutput.repositoryWait(stage.record.identity.value, detail, stage.payload.stage))
  } yield wire

  def advanceC(store: N.Store, pending: N.Stage, policy: Policy, namespace: String = ""): Consequence[N.Stage] = for {
    _ <- policy.validateC
    _ <- if (pending.payload.stage == "PendingMergePrepared" && pending.payload.indexTree.nonEmpty &&
        pending.payload.mergeHead.nonEmpty && pending.payload.after.nonEmpty) Consequence.unit
      else N.invalid("validation requires the genuine selected pending merge")
    inputs <- if (store.declaration.runtimeCleanSync) RepositorySyncDocumentInputs.captureC(store, pending).map(Some(_))
      else Consequence.success(None)
    prepared <- if (inputs.forall(_.documentOnly)) Consequence.success(
      (Option.empty[RepositorySyncProgramImpact.Selection], Map.empty[String, Receipt]))
      else policy.impact match {
        case Some(impact) => RepositorySyncProgramImpact.prepareC(store, pending, inputs.get, impact)
          .map((selection, receipts) => (Some(selection), receipts))
        case None => N.invalid("program/unknown consumed inputs require impact-selected validation; consumed=" +
          inputs.get.consumedPaths.mkString(",") + "; unknown=" + inputs.get.unknownPaths.mkString(","))
      }
    selected <- selectedC(store, pending, policy, merge = true, namespace = namespace, inputs = inputs,
      impact = prepared._1, reused = prepared._2)
  } yield selected

  /** Equal, local-ahead and fast-forward integrations introduce no semantic
    * merge candidate. Check the actual clean selected index and host policy;
    * do not issue an unrelated merge review or manufacture a merge commit.
    */
  def advanceIntegratedC(store: N.Store, integrated: N.Stage, policy: Policy): Consequence[N.Stage] = for {
    _ <- policy.validateC
    source <- integrationSourceC(integrated)
    selected <- selectedC(store, source, policy, merge = false, namespace = "")
  } yield selected

  private[admission] def integrationSourceC(integrated: N.Stage): Consequence[N.Stage] =
    if (integrated.payload.stage == "Integrated" && integrated.payload.mergeHead.isEmpty &&
        integrated.payload.method.exists(Set("equal", "local-ahead", "fast-forward")) &&
        integrated.payload.after.nonEmpty) Consequence.success(integrated.copy(payload = integrated.payload.copy(indexTree = Some(integrated.payload.after.get.tree))))
      else N.invalid("validation requires the genuine selected clean nonmerge integration")

  private[admission] def completedC(value: N.Stage, source: N.Stage, policy: Policy, merge: Boolean): Consequence[Unit] = for {
    state <- value.payload.validation.map(Consequence.success).getOrElse(N.invalid("retained validation facts are missing"))
    _ <- if (state.sourceRevision == source.record.revision.value.toString && applies(state, policy)) Consequence.unit
      else N.invalid("retained checks belong to a different candidate or selected policy")
    _ <- if (state.receipts.size == state.selected.size + (if (merge) 2 else 1) && successful(state.receipts, merge, state.selected))
      Consequence.unit else N.invalid("required executed checks are missing or did not pass")
  } yield ()

  private def selectedC(store: N.Store, pending: N.Stage, policy: Policy, merge: Boolean,
    namespace: String, inputs: Option[RepositorySyncDocumentInputs.Assessment] = None,
    impact: Option[RepositorySyncProgramImpact.Selection] = None, reused: Map[String, Receipt] = Map.empty): Consequence[N.Stage] = for {
    _ <- preconditionsC(store, pending)
    complete <- store.loadC(N.candidateKey(namespace, if (merge) "merge-validated" else "integration-validated"))
    selected <- complete match {
      case Some(value) => for {
        _ <- completedC(value, pending, policy, merge)
        _ <- if (value.payload.validation.exists(state => RepositorySyncDocumentInputs.applicable(state.inputs, inputs))) Consequence.unit
          else N.invalid("retained validation crosses its actual document/consumption inputs")
      } yield value
      case None =>
        val prefix = if (!merge) pending.payload.continuation.filter(_.choice == "AuthorizeNewValidation")
          .map(_.target.successfulPrefix).getOrElse(Vector.empty) else Vector.empty
        if (prefix.nonEmpty && !RepositorySyncValidationContinuation.validPrefix(prefix, policy))
          N.invalid("continuation seed is not a successful contiguous selected prefix")
        else checksC(store, pending, policy,
          State(policy.mode, impact.map(_.reason).getOrElse(policy.reason),
            if (store.declaration.runtimeCleanSync && merge) impact.map(_.checks).getOrElse(Vector.empty) else policy.checks,
            pending.record.revision.value.toString, prefix, inputs, impact), merge, namespace, reused)
    }
  } yield selected

  private def checksC(store: N.Store, pending: N.Stage, policy: Policy, selection: State, merge: Boolean,
    namespace: String, reused: Map[String, Receipt] = Map.empty): Consequence[N.Stage] = {
    val structural = if (merge) 2 else 1
    val checks = Vector(Check("index-local", 0)) ++ (if (merge) Vector(Check("index-remote", 0)) else Vector.empty) ++ selection.selected
    val checking = if (merge) "ValidationChecking" else "IntegrationValidationChecking"
    val seeded = if (selection.receipts.nonEmpty) pending.copy(payload = pending.payload.copy(validation = Some(selection))) else pending
    checks.zipWithIndex.foldLeft(Consequence.success(seeded)) { case (prior, (check, index)) => prior.flatMap { current =>
      if (index < selection.receipts.size) Consequence.success(current)
      else if (current.payload.validation.exists(s => s.receipts.size < index || !successful(s.receipts, merge, s.selected))) Consequence.success(current)
      else for {
        retained <- store.loadC(N.candidateKey(namespace, "validation-result-" + index))
        selected <- retained match {
          case Some(value) =>
            if (value.payload.validation.exists(state => state.sourceRevision == selection.sourceRevision &&
                state.receipts.size == index + 1 && applies(state, policy) &&
                state.receipts.last.identity == check.identity && (index < structural ||
                  _receipt_kind(state.receipts.last, check.kind)))) Consequence.success(value)
            else N.invalid("retained check result crosses the selected candidate/check")
          case None => for {
            intent <- store.loadC(N.candidateKey(namespace, "validation-intent-" + index))
            saved <- intent match {
              case Some(value) => Consequence.success(value)
              case None => for {
                _ <- preconditionsC(store, pending)
                receipts = current.payload.validation.map(_.receipts).getOrElse(Vector.empty)
                state = selection.copy(receipts = receipts)
                prepared <- store.saveC(N.candidateKey(namespace, "validation-intent-" + index), "native-check-prepared", current.record,
                  pending.payload.copy(stage = checking, validation = Some(state)))
                _ <- store.acknowledgeC("merge-check-prepared:" + check.identity)
                receipt <- if (index < structural) for {
                  parent <- GitObjectId.parseC(if (index == 0) pending.payload.after.get.head else pending.payload.mergeHead.get)
                  checked <- E.executeGitC(store.core, GitOperation.CheckPendingIndex(parent))
                  // Successful facts pass the typed value gate. Negative actual
                  // captures still become ordinary failed/indeterminate receipts.
                  raw <- checked.outcome match {
                    case GitExecutionOutcome.Succeeded => checked.valueC.map(_ => checked.processResult)
                    case _ => Consequence.success(checked.processResult)
                  }
                  receipt <- receiptC(check.identity, raw)
                } yield receipt
                  else reused.get(check.identity) match {
                    case Some(actual) => Consequence.success(actual)
                    case None => E.repositoryValidationC(store.core, check.ordinal, check.kind)
                    .flatMap(sbtReceiptC(check.identity, check.kind, _))
                  }
                _ <- store.acknowledgeC((if (reused.contains(check.identity)) "merge-check-reused:" else "merge-check-executed:") + check.identity)
                recorded <- store.saveC(N.candidateKey(namespace, "validation-result-" + index), "native-check-recorded", prepared.record,
                  pending.payload.copy(stage = checking, validation = Some(state.copy(receipts = receipts :+ receipt))))
                _ <- store.acknowledgeC("merge-check-recorded:" + check.identity)
              } yield recorded
            }
          } yield saved
        }
      } yield selected
    }}.flatMap { current =>
      current.payload.validation match {
        case Some(state) if state.receipts.size == checks.size && successful(state.receipts, merge, state.selected) => for {
          _ <- preconditionsC(store, pending)
          validated <- store.saveC(N.candidateKey(namespace, if (merge) "merge-validated" else "integration-validated"), "native-validation-admitted", current.record,
            pending.payload.copy(stage = if (merge) "MergeValidated" else "IntegrationValidated", validation = Some(state)))
          _ <- store.acknowledgeC(if (merge) "merge-validated" else "integration-validated")
        } yield validated
        case _ => Consequence.success(current)
      }
    }
  }

  private[admission] def applies(state: State, policy: Policy): Boolean =
    state.mode == policy.mode && state.selected.forall(check => policy.checks.exists(_ == check))

  private[admission] def knownFailure(value: N.Stage, policy: Policy): Boolean =
    value.payload.stage == "ValidationChecking" && value.payload.validation.exists { state =>
      val offset = state.receipts.size - 3
      applies(state, policy) && offset >= 0 && state.selected.lift(offset).exists { check =>
        val failed = state.receipts.last
        successful(state.receipts.dropRight(1), merge = true, selected = state.selected) &&
          failed.identity == check.identity && _receipt_kind(failed, check.kind) &&
          storedProductOutputValid(failed) && failed.termination == "exited" && failed.exitCode.exists(_ != 0)
      }
    }

  /** Read the retained executed prefix only. Incomplete intents, missing results
    * and uncertain terminations cannot become a known-failure repair source. */
  private[admission] def retainedFailureC(store: N.Store, pending: N.Stage, policy: Policy,
    namespace: String): Consequence[Option[N.Stage]] = for {
    results <- (0 until policy.checks.size + 2).foldLeft(Consequence.success(Vector.empty[N.Stage])) { (prior, index) =>
      prior.flatMap(values => store.loadC(N.candidateKey(namespace, "validation-result-" + index))
        .map(found => values ++ found.toVector))
    }
    selected = results.lastOption.filter(value => knownFailure(value, policy) && value.payload.validation.exists(state =>
      state.sourceRevision == pending.record.revision.value.toString && state.receipts.size == results.size))
  } yield selected

  private[admission] def prepareFailureC(store: N.Store, failed: N.Stage, policy: Policy,
    namespace: String): Consequence[N.Stage] = for {
    _ <- if (knownFailure(failed, policy) && policy.assessment.nonEmpty) Consequence.unit
      else N.invalid("check-origin repair requires an actual known failure and explicitly selected policy")
    retained <- store.loadC(N.candidateKey(namespace, "validation-repair-required"))
    selected <- retained match {
      case Some(saved) if saved.payload == failed.payload.copy(stage = "ValidationRepairRequired") => Consequence.success(saved)
      case Some(_) => N.invalid("check-origin repair source crosses retained validation")
      case None => store.saveC(N.candidateKey(namespace, "validation-repair-required"), "native-validation-repair-required",
        failed.record, failed.payload.copy(stage = "ValidationRepairRequired"))
    }
  } yield selected


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

  private[admission] def receiptC(identity: String, result: ProcessExecutionResult): Consequence[Receipt] =
    _receipt_c(identity, result, id(identity), true)

  private def _receipt_c(identity: String, result: ProcessExecutionResult, valididentity: Boolean,
    bounded: Boolean): Consequence[Receipt] = {
    if (!valididentity || result == null || result.stdout == null || result.stderr == null ||
        result.stdout.content == null || result.stderr.content == null ||
        result.stdout.truncated || result.stderr.truncated ||
        result.stdout.byteCount < 0 || result.stderr.byteCount < 0 ||
        result.stdout.byteCount != result.stdout.content.size.toLong || result.stderr.byteCount != result.stderr.content.size.toLong)
      N.uncertain("validation result has invalid identity, missing capture, truncation or byte-count mismatch; retained intent is not replayable")
    else if (bounded && (result.stdout.byteCount > 65536 || result.stderr.byteCount > 65536))
      N.uncertain("structural validation capture exceeds the 65536-byte text bound; retained intent is not replayable")
    else try {
      def _text_(bytes: Vector[Byte]) = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toArray)).toString
      val out = _text_(result.stdout.content); val err = _text_(result.stderr.content)
      if (out.contains('\u0000') || err.contains('\u0000')) N.uncertain("validation capture is not ordinary text")
      else result.termination match {
        case ProcessExecutionTermination.Exited(code) => Consequence.success(Receipt(identity, "exited", Some(code), out, err))
        case ProcessExecutionTermination.NotStarted => Consequence.success(Receipt(identity, "not-started", None, out, err))
        case ProcessExecutionTermination.Cancelled => Consequence.success(Receipt(identity, "cancelled", None, out, err))
        case ProcessExecutionTermination.TimedOut => Consequence.success(Receipt(identity, "timed-out", None, out, err))
        case ProcessExecutionTermination.TerminationUnconfirmed => Consequence.success(Receipt(identity, "termination-unconfirmed", None, out, err))
        case _ => Consequence.success(Receipt(identity, "indeterminate", None, out, err))
      }
    } catch { case scala.util.control.NonFatal(_) => N.uncertain("validation capture is not UTF-8") }
  }
  private[admission] def sbtReceiptC(identity: String, kind: WorkflowSbtValidation.Kind,
    result: SbtExecutionResult): Consequence[Receipt] = for {
    facts <- WorkflowSbtValidation.factsC(kind, result)
    _ <- if ((id(identity) || (identity != null && identity.startsWith("metadata:") &&
        GoalPhaseNativeMetadata.runtimeIdentityValid(identity))) && WorkflowSbtCapture.valid(result.processResult, facts))
      Consequence.unit else N.uncertain("typed SBT observation has invalid identity, capture or contradictory actual facts; intent is not replayable")
    process = result.processResult
  } yield Receipt(identity, WorkflowSbtCapture.termination(process), WorkflowSbtCapture.exitCode(process),
    WorkflowSbtCapture.text(process.stdout).value, WorkflowSbtCapture.text(process.stderr).value, Some(facts), Some(process))

  private def _receipt_kind(receipt: Receipt, expected: WorkflowSbtValidation.Kind): Boolean =
    receipt != null && expected != null && receipt.sbt != null && (receipt.sbt match {
      case None => expected == WorkflowSbtValidation.Kind.Test
      case Some(facts) => facts != null && facts.valid && facts.kind == expected
    })

  private[admission] def productSuccessful(receipt: Receipt,
    expectedKind: WorkflowSbtValidation.Kind = WorkflowSbtValidation.Kind.Test): Boolean =
    _receipt_kind(receipt, expectedKind) && receipt.termination == "exited" && receipt.exitCode != null &&
      receipt.exitCode.contains(0) && storedProductOutputValid(receipt) &&
      receipt.processObservation.forall(p => !p.stdout.truncated && !p.stderr.truncated &&
        WorkflowSbtCapture.text(p.stdout).textStatus == "complete" && WorkflowSbtCapture.text(p.stderr).textStatus == "complete") &&
      receipt.sbt.forall(_.outcome == WorkflowSbtValidation.Outcome.Succeeded)

  /** CNCF bounds actual typed SBT captures; historical untyped text keeps its local bound. */
  private[admission] def storedProductOutputValid(receipt: Receipt): Boolean =
    receipt != null && receipt.sbt != null && receipt.processObservation != null && (receipt.processObservation match {
      case Some(process) => receipt.sbt.exists(facts => WorkflowSbtCapture.valid(process, facts)) &&
        receipt.termination == WorkflowSbtCapture.termination(process) && receipt.exitCode == WorkflowSbtCapture.exitCode(process) &&
        receipt.stdout == WorkflowSbtCapture.text(process.stdout).value && receipt.stderr == WorkflowSbtCapture.text(process.stderr).value
      case None => receipt.sbt match {
      case None => _stored_text(receipt.stdout) && _stored_text(receipt.stderr)
      case Some(facts) => facts != null && facts.valid && _ordinary_text(receipt.stdout) && _ordinary_text(receipt.stderr)
      }
    })

  /** Structural failures newly common to both parents block. Findings inherited
    * from only one parent are not newly introduced merge defects.
    */
  private[admission] def successful(receipts: Vector[Receipt], merge: Boolean = true, selected: Vector[Check] = Vector.empty): Boolean = {
    def structural(r: Receipt): Option[GitIndexCheck] =
      if (r == null || r.termination != "exited" || r.exitCode == null || r.exitCode.isEmpty ||
          !_stored_text(r.stdout) || !_stored_text(r.stderr)) None
      else GitIndexCheck.parseStoredC(r.exitCode.get, r.stdout, r.stderr).toOption
    val count = if (merge) 2 else 1
    val first = receipts.take(count)
    val facts = first.map(structural)
    facts.forall(_.nonEmpty) && (first.size < 2 ||
      (facts(0).get.findingKeys intersect facts(1).get.findingKeys).isEmpty) &&
      receipts.drop(count).zipWithIndex.forall { case (receipt, index) =>
        if (selected.isEmpty) productSuccessful(receipt)
        else selected.lift(index).exists(check => receipt != null && receipt.identity == check.identity &&
          productSuccessful(receipt, check.kind))
      }
  }
  private def _ordinary_text(text: String): Boolean = text != null && !text.contains('\u0000') &&
    StandardCharsets.UTF_8.newEncoder().canEncode(text)
  private def _stored_text(text: String): Boolean = _ordinary_text(text) &&
    text.getBytes(StandardCharsets.UTF_8).length <= 65536
  private def id(value: String): Boolean = value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
}
