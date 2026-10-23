package org.simplemodeling.textus.smworkflow.admission

import java.nio.charset.StandardCharsets.UTF_8
import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.record.Record
import GoalPhaseNativeRun as F
import GoalPhaseNativeWork as W
import GoalPhaseOrderedProgression as O
import WorkflowManagedProcessExecution as E

/** Completes only the terminal-LF conditions of an actual original independent
  * Step review. Actual native history selects the source; typed immutable Git
  * facts and fresh selected checks admit ordinary Closing, never a new review.
  * @since   Oct. 19, 2026
  * @version Oct. 21, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseNativeReviewCompletion {
  final case class Conditions(kind: String, paths: Vector[String])
  object Conditions {
    given Encoder[Conditions] = deriveEncoder
    private val _decoder: Decoder[Conditions] = deriveDecoder
    given Decoder[Conditions] = Decoder.instance { cursor =>
      _decoder.tryDecode(cursor).flatMap(value => if (cursor.keys.exists(_.toSet == Set("kind", "paths")) && _conditions_valid(value)) Right(value)
        else Left(DecodingFailure("Invalid bounded terminal-LF completion conditions", cursor.history)))
    }
  }

  def validateOptionC(value: Option[Conditions]): Consequence[Unit] =
    _check(value != null && value.forall(_conditions_valid), "completion conditions must be absent or valid")

  /** The optional ownership argument is absent only for retained original reports,
    * whose selected Step ownership is checked again at their actual use frontier. */
  def admitResultC(kind: String, result: W.Result, ownedpaths: Option[Vector[String]]): Consequence[Unit] = for {
    _ <- _check(result != null && ownedpaths != null, "completion result or ownership option is null")
    _ <- validateOptionC(result.completionConditions)
    _ <- _check(result.completionConditions.isEmpty || (kind == "ReviewStep" && result.outcome == "repair" &&
      result.findings != null && result.findings.nonEmpty && result.findings.forall(_text(_, 16384)) &&
      result.completionConditions.get.paths.size == result.findings.size &&
      result.planRevision != null && result.planRevision.isEmpty &&
      result.dependencyProposal != null && result.dependencyProposal.isEmpty &&
      ownedpaths.forall(paths => paths != null && paths.nonEmpty && paths.forall(_path) &&
        result.completionConditions.get.paths.forall(path => paths.exists(_covers(_, path))))),
      "only a Step repair verdict with exactly owned terminal-LF findings may declare completion conditions")
  } yield ()

  def fromPublicConditionsC(value: Option[Record]): Consequence[Option[Conditions]] =
    if (value == null || value.exists(_ == null)) F.invalid("public completion conditions option is null")
    else value.fold(Consequence.success(Option.empty[Conditions])) { record => for {
      text <- record.toJsonStringC
      json <- io.circe.parser.parse(text).fold(error => F.invalid(error.message), Consequence.success)
      conditions <- json.as[Conditions].fold(error => F.invalid(error.message), Consequence.success)
    } yield Some(conditions) }

  /** Constructed only from the continuation owner's reconstructed native Cursor;
    * no semantic/public input can select eligibility or supply native facts. */
  private[admission] final class Source private[GoalPhaseNativeReviewCompletion](
    val from: InstanceRecord, val controller: O.State, val candidate: GoalPhaseNativeCandidate.Saved,
    val validation: GoalPhaseNativeValidation.Saved, val repair: W.Request,
    val result: ContinuationResult[W.Result], val prior: GoalPhaseNativeReview.Prior,
    val issues: Option[GoalPhaseFixIssues.State]) {
    def conditions: Conditions = prior.result.completionConditions.get
    def reference: WorkflowMechanicalTransition.Reference =
      WorkflowMechanicalTransition.Reference(from.identity.value + ":ordered-review-completion:" + from.revision.value, "1")
  }

  /** Successful absence means ordinary affected ReviewStep remains required.
    * Contradictory actual native material fails explicitly. */
  private[admission] def selectC(from: InstanceRecord, controller: O.State,
    candidate: Option[GoalPhaseNativeCandidate.Saved], validation: Option[GoalPhaseNativeValidation.Saved],
    lastresult: Option[(W.Request, ContinuationResult[W.Result])], issues: Option[GoalPhaseFixIssues.State],
    priorreviews: Vector[GoalPhaseNativeReview.Prior], requiredchecks: Vector[GoalPhaseNativeValidation.Check]): Consequence[Option[Source]] = for {
    _ <- _check(from != null && controller != null && candidate != null && validation != null &&
      lastresult != null && priorreviews != null && requiredchecks != null &&
      candidate.forall(value => value != null && value.record != null && value.payload != null) &&
      validation.forall(value => value != null && value.record != null && value.payload != null) &&
      lastresult.forall(value => value != null && value._1 != null && value._2 != null &&
        value._2.result != null && value._2.result.value != null && value._2.resultReference != null),
      "actual completion source contains null native material")
    _ <- GoalPhaseFixIssues.validateOptionC(issues)
    selected <- if (controller.stage != O.Stage.Review || controller.cursor >= controller.plan.steps.size)
      Consequence.success(Option.empty[Source]) else for {
      _ <- from.validateC
      _ <- _check(from.lifecycle == Lifecycle.Active && from.suspension.isEmpty &&
        from.currentProgression.contains(ProgressionReference("phase.Review")) &&
        controller.candidate.nonEmpty && controller.checksReceipt.nonEmpty && candidate.nonEmpty && validation.nonEmpty,
        "completion source has no unsuspended actual Review frontier")
      step = controller.plan.steps(controller.cursor)
      priors <- GoalPhaseNativeReview.priorReviewsForC(priorreviews, "ReviewStep", Some(step.identity))
      actualcandidate = candidate.get
      actualvalidation = validation.get
      _ <- _check(_revision(actualcandidate.payload.sourceRevision) &&
        actualcandidate.payload.candidate != null && actualvalidation.payload.selected != null &&
        actualvalidation.payload.receipts != null && actualvalidation.payload.metadata != null &&
        actualvalidation.payload.stopped != null && actualvalidation.payload.outcome != null &&
        actualvalidation.payload.receipts.forall(receipt => receipt != null && receipt.identity != null &&
          receipt.termination != null && receipt.exitCode != null && receipt.stdout != null && receipt.stderr != null &&
          receipt.sbt != null && receipt.sbt.forall(facts => facts != null && facts.valid)) &&
        actualvalidation.payload.stopped.forall(receipt => receipt != null && receipt.identity != null &&
          receipt.termination != null && receipt.exitCode != null && receipt.stdout != null && receipt.stderr != null &&
          receipt.sbt != null && receipt.sbt.forall(facts => facts != null && facts.valid)),
        "actual completion checks contain incomplete native payload values")
      _ <- GoalPhaseNativeMetadata.validateRetainedC(requiredchecks, actualvalidation.payload.selected,
        actualvalidation.payload.metadata)
      candidateprefix <- GoalPhaseNativeHistory.prefixC(from, actualcandidate.record.revision)
      validationprefix <- GoalPhaseNativeHistory.prefixC(from, actualvalidation.record.revision)
      _ <- _check(candidateprefix == actualcandidate.record && validationprefix == actualvalidation.record &&
        actualcandidate.payload.candidate == controller.candidate &&
        actualvalidation.payload.candidate == controller.candidate.get &&
        actualcandidate.payload.step == step.identity && actualvalidation.payload.step == step.identity &&
        actualcandidate.record.revision.value < actualvalidation.record.revision.value &&
        controller.checksReceipt.contains(_checks_reference(actualvalidation)) &&
        GoalPhaseNativeValidation.codec.decode(GoalPhaseNativeValidation.codec.encode(actualvalidation.payload))
          .contains(actualvalidation.payload) && actualvalidation.payload.outcome.contains("passed") &&
        actualvalidation.payload.receipts.size == actualvalidation.payload.selected.size &&
        GoalPhaseNativeValidation.successful(actualvalidation.payload.receipts, actualvalidation.payload.selected),
        "completion source changes its actual candidate or complete passed validation")
      prior = priors.reverse.find(value => value.kind == "ReviewStep" && value.step.contains(step.identity))
      selected <- (lastresult, prior) match {
        case (Some((repair, result)), Some(original)) if controller.generation > 1 &&
            repair.kind == "RepairStep" && result.result.value.outcome == "completed" &&
            original.result.completionConditions.nonEmpty => for {
          _ <- admitResultC(original.kind, original.result, Some(step.ownedPaths))
          _ <- _check(repair.candidate != null && repair.checksReceipt != null && repair.stepIdentity != null &&
            repair.repairDirective != null && repair.review != null && repair.checkFailure != null &&
            repair.selectedPlan == controller.plan, "actual repair contains null or changed selected input")
          _ <- W.admitSemanticResultC(repair, result.result.value)
          _ <- _check(repair.instanceIdentity == from.identity.value && repair.selectedPlan == controller.plan &&
            repair.originalBase == controller.base && repair.completed == controller.completed &&
            repair.stepIdentity.contains(step.identity) && repair.generation == controller.generation &&
            original.generation == controller.generation - 1 && original.result.outcome == "repair" &&
            repair.repairDirective.contains(O.RepairDirective(original.result.summary, original.result.findings)) &&
            controller.repairDirective == repair.repairDirective && repair.review != null &&
            repair.review.exists(context => context != null && context.priorReviews != null &&
              context.priorReviews.forall(_ != null) &&
              context.priorReviews.reverse.find(value => value.kind == "ReviewStep" && value.step.contains(step.identity))
                .contains(original)) && repair.checkFailure != null && repair.checkFailure.isEmpty &&
            original.candidate.head == controller.candidate.get.head &&
            controller.candidate.get.head == controller.candidateBase.head &&
            result.result.typeIdentity == W.resultCodec.typeIdentity &&
            _reference(result.resultReference.identity) && _revision(result.resultReference.revision),
            "latest actual repair changes the original Plan, review, directive, generation or target")
          completechecks = actualvalidation.payload.selected.exists(_.kind == WorkflowSbtValidation.Kind.Compile) &&
            actualvalidation.payload.selected.exists(_.kind == WorkflowSbtValidation.Kind.Test) &&
            actualvalidation.payload.receipts.forall(_.sbt.nonEmpty)
          eligible = original.candidate.tree != controller.candidate.get.tree && completechecks &&
            GoalPhaseFixIssues.coveredIssuesC(issues, repair, result.result.value, original).isSuccess
        } yield Option.when(eligible)(new Source(from, controller, actualcandidate, actualvalidation,
          repair, result, original, issues))
        case _ => Consequence.success(Option.empty[Source])
      }
    } yield selected
  } yield selected

  final case class Payload(sourceRevision: String, step: String, generation: Long,
    originalReview: ContextReference, originalGeneration: Long, originalCandidate: O.Snapshot,
    originalChecksReceipt: String, repairResult: ContextReference, currentCandidate: O.Snapshot,
    currentCandidateReference: String, currentChecksReceipt: String, conditionPaths: Vector[String],
    changes: Vector[GoalPhaseNativeClosure.Change])
  object Payload {
    given Encoder[Payload] = deriveEncoder
    private val _decoder: Decoder[Payload] = deriveDecoder
    given Decoder[Payload] = Decoder.instance { cursor =>
      _decoder.tryDecode(cursor).flatMap(value => if (_payload_valid(value)) Right(value)
        else Left(DecodingFailure("Invalid native covered review completion", cursor.history)))
    }
  }
  private[admission] val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseNativeReviewCompletionReceipt"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.goal-phase-native-review-completion.v1"),
      "payload" -> value.asJson)
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("native covered review completion is absent")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(schema == "sm-workflow.goal-phase-native-review-completion.v1", (), "covered review completion schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
    } yield payload
  }
  private[admission] final case class Saved(record: InstanceRecord, payload: Payload,
    controller: O.State, issues: Option[GoalPhaseFixIssues.State])

  /** Complete typed reads fail explicitly; successful uncovered bytes select no reuse. */
  private[admission] def proveC(source: Source, changes: Vector[GitTreeChange],
    blobs: Map[String, GitBlobContent]): Consequence[Option[Vector[GoalPhaseNativeClosure.Change]]] = for {
    _ <- _check(source != null && changes != null && changes.size <= 256 && blobs != null &&
      changes.forall(change => change != null && change.path != null && _path(change.path.value) &&
        change.oldMode != null && change.newMode != null && _text(change.status, 128) &&
        change.oldBlob != null && change.newBlob != null &&
        (change.oldBlob.toVector ++ change.newBlob.toVector).forall(blob => blob != null && _oid(blob.value))) &&
      changes.map(_.path.value).distinct.size == changes.size,
      "typed tree changes are malformed, duplicate or incomplete")
    identities = changes.flatMap(change => change.oldBlob.toVector ++ change.newBlob.toVector).map(_.value).distinct
    _ <- _check(identities.forall(identity => blobs.get(identity).exists(_complete_blob)),
      "typed old/new blob bytes are unavailable, incomplete or inconsistent with their typed text projection")
    admitted <- if (changes.isEmpty || changes.map(_.path.value).toSet != source.conditions.paths.toSet)
      Consequence.success(Option.empty[Vector[GoalPhaseNativeClosure.Change]])
      else GoalPhaseNativeClosure.admitTerminalLfChangesC(source.controller.plan.steps(source.controller.cursor).ownedPaths,
        changes, blobs) match {
        case Consequence.Success(values) => Consequence.success(Some(values))
        case _ => Consequence.success(None)
      }
  } yield admitted

  private def _observe_c(source: Source, core: ActionCall.Core): Consequence[Option[Vector[GoalPhaseNativeClosure.Change]]] = for {
    before <- GitObjectId.parseC(source.prior.candidate.tree)
    after <- GitObjectId.parseC(source.controller.candidate.get.tree)
    changes <- E.executeGitC(core, GitOperation.TreeChanges(before, after)).flatMap(_.valueC)
    _ <- _check(changes != null && changes.size <= 256 && changes.forall(change => change != null &&
      change.oldBlob != null && change.newBlob != null &&
      (change.oldBlob.toVector ++ change.newBlob.toVector).forall(_ != null)), "typed tree change read is incomplete")
    blobs <- changes.flatMap(change => change.oldBlob.toVector ++ change.newBlob.toVector).distinct
      .foldLeft(Consequence.success(Map.empty[String, GitBlobContent])) { (prior, identity) => for {
        values <- prior
        actual <- E.executeGitC(core, GitOperation.BlobContent(identity)).flatMap(_.valueC)
      } yield values.updated(identity.value, actual) }
    admitted <- proveC(source, changes, blobs)
  } yield admitted

  private def _payload(source: Source, changes: Vector[GoalPhaseNativeClosure.Change]): Payload =
    Payload(source.from.revision.value.toString, source.repair.stepIdentity.get, source.controller.generation,
      ContextReference(source.prior.resultIdentity, source.prior.resultRevision), source.prior.generation,
      source.prior.candidate, source.prior.checksReceipt, source.result.resultReference,
      source.controller.candidate.get, source.candidate.record.identity.value + ":ordered-candidate:" +
        source.candidate.payload.sourceRevision + ":candidate", source.controller.checksReceipt.get,
      source.conditions.paths, changes)

  private[admission] def admittedStateC(source: Source, payload: Payload): Consequence[(O.State, Option[GoalPhaseFixIssues.State])] = for {
    _ <- _check(source != null && _payload_valid(payload) && payload == _payload(source, payload.changes),
      "completion payload rewrites its actual source relationships")
    next <- source.controller.acceptC(O.CoveredReviewCompletionObserved(payload.currentCandidate,
      payload.currentChecksReceipt, payload.originalGeneration, payload.originalCandidate,
      payload.originalReview.identity, source.reference.identity))
    issues <- GoalPhaseFixIssues.completeCoveredC(source.issues, source.repair, source.result.result.value,
      source.prior, source.reference.identity)
  } yield (next, issues)

  private[admission] def successorC(from: InstanceRecord, saved: InstanceRecord): Consequence[Unit] = for {
    _ <- _check(from != null && saved != null && from.lifecycle == Lifecycle.Active && from.suspension.isEmpty &&
      from.currentProgression.contains(ProgressionReference("phase.Review")), "completion has no actual Review predecessor")
    _ <- from.validateC
    _ <- saved.validateC
    _ <- _check(domain.statemachine.GoalPhaseRunLifecycle.permits("Review", "review-accepted", "Closing"),
      "ordinary review-accepted edge is absent from CML")
    expected <- from.appendC(from.revision, F.history(from, "phase.Closing", "ordered-review-completion:covered"))
    _ <- _check(expected == saved, "completion is not its canonical full native Review-to-Closing successor")
  } yield ()

  private[admission] def retainedC(session: F.Session, core: ActionCall.Core, source: Source): Consequence[Option[Saved]] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    saved <- _retained_c(session, core, source, adapter)
  } yield saved

  private def _retained_c(session: F.Session, core: ActionCall.Core, source: Source,
    adapter: WorkflowManagedMechanicalTransition[Payload]): Consequence[Option[Saved]] = for {
    retained <- adapter.loadRetainedC(source.reference)(using core.executionContext)
    saved <- retained match {
      case None => Consequence.success(Option.empty[Saved])
      case Some(value) => for {
        prefix <- GoalPhaseNativeHistory.prefixC(session.current, value.receipt.next.revision)
        sourceprefix <- GoalPhaseNativeHistory.prefixC(session.current, source.from.revision)
        _ <- _check(prefix == value.receipt.next && sourceprefix == source.from,
          "retained completion is not an actual full native history prefix")
        _ <- successorC(source.from, prefix)
        actual <- _observe_c(source, core).flatMap(_.map(Consequence.success).getOrElse(
          F.invalid("retained completion has an uncovered actual immutable delta")))
        payload = value.receipt.payload.value
        _ <- _check(payload == _payload(source, actual), "retained completion changes the admitted typed changes or references")
        state <- admittedStateC(source, payload)
      } yield Some(Saved(prefix, payload, state._1, state._2))
    }
  } yield saved

  private[admission] def advanceC(session: F.Session, d: F.Declaration, core: ActionCall.Core,
    source: Source): Consequence[Option[Saved]] = for {
    _ <- _check(session.current == source.from, "fresh completion omits actual later native history")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- _retained_c(session, core, source, adapter)
    saved <- retained match {
      case Some(value) => Consequence.success(Some(value))
      case None => for {
        changes <- _observe_c(source, core)
        saved <- changes match {
          case None => Consequence.success(Option.empty[Saved])
          case Some(actual) => for {
            payload <- Consequence.success(_payload(source, actual))
            state <- admittedStateC(source, payload)
            _ <- _check(domain.statemachine.GoalPhaseRunLifecycle.permits("Review", "review-accepted", "Closing"),
              "ordinary review-accepted edge is absent from CML")
            retained <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, source.reference,
              source.from, source.from.revision, F.history(source.from, "phase.Closing", "ordered-review-completion:covered"),
              TypedValue(codec.typeIdentity, payload)),
              ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
            _ <- successorC(source.from, retained.receipt.next)
          } yield Some(Saved(retained.receipt.next, payload, state._1, state._2))
        }
      } yield saved
    }
  } yield saved

  def message(value: Payload): String = "Original independent review " + value.originalReview.identity +
    "@" + value.originalReview.revision + " completed under current checks " + value.currentChecksReceipt +
    "; fulfilled terminal-LF conditions: " + value.conditionPaths.mkString(", ") + ". Ordinary Closing admitted."

  private def _payload_valid(value: Payload): Boolean = value != null && _revision(value.sourceRevision) &&
    _reference(value.step) && value.generation > 1 && value.originalGeneration == value.generation - 1 &&
    _context_reference(value.originalReview) && _context_reference(value.repairResult) &&
    value.originalReview != value.repairResult && _snapshot(value.originalCandidate) && _snapshot(value.currentCandidate) &&
    value.originalCandidate.head == value.currentCandidate.head && value.originalCandidate.tree != value.currentCandidate.tree &&
    Vector(value.originalChecksReceipt, value.currentCandidateReference, value.currentChecksReceipt).forall(_reference) &&
    value.originalChecksReceipt != value.currentChecksReceipt &&
    _conditions_valid(Conditions("terminal-lf", value.conditionPaths)) && value.changes != null &&
    value.changes.size == value.conditionPaths.size && value.changes.forall(change => change != null && _path(change.path) &&
      Set("100644", "100755")(change.mode) && _oid(change.oldBlob) && _oid(change.newBlob) && change.oldBlob != change.newBlob) &&
    value.changes.map(_.path).distinct.size == value.changes.size && value.changes.sortBy(_.path) == value.changes &&
    value.changes.map(_.path).toSet == value.conditionPaths.toSet
  private def _conditions_valid(value: Conditions): Boolean = value != null && value.kind == "terminal-lf" &&
    value.paths != null && value.paths.nonEmpty && value.paths.size <= 16 && value.paths.distinct == value.paths && value.paths.forall(_path)
  private def _path(value: String): Boolean = _text(value, 4096) && !value.exists(c => Character.isISOControl(c)) &&
    GitPath.parseC(value).isSuccess
  private def _text(value: String, maximum: Int): Boolean = value != null && value.trim.nonEmpty &&
    !value.contains('\u0000') && UTF_8.newEncoder().canEncode(value) && value.getBytes(UTF_8).length <= maximum
  private def _reference(value: String): Boolean = _text(value, 16384) && !value.exists(c => Character.isISOControl(c))
  private def _revision(value: String): Boolean = value != null && value.matches("[1-9][0-9]*") && value.toLongOption.exists(_ > 0)
  private def _context_reference(value: ContextReference): Boolean = value != null && _reference(value.identity) && _revision(value.revision)
  private def _snapshot(value: O.Snapshot): Boolean = value != null && _oid(value.head) && _oid(value.tree)
  private def _oid(value: String): Boolean = value != null && GitObjectId.parseC(value).isSuccess
  private def _covers(owner: String, path: String): Boolean = path == owner || path.startsWith(owner + "/")
  private def _checks_reference(value: GoalPhaseNativeValidation.Saved): String = value.record.identity.value +
    ":ordered-validation:" + value.payload.sourceRevision + ":validation"
  private def _complete_blob(value: GitBlobContent): Boolean = value != null && value.bytes != null &&
    value.text != null && value.text.forall(_ != null) && {
    val projected = scala.util.Try {
      val decoder = UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
      decoder.decode(java.nio.ByteBuffer.wrap(value.bytes.toArray)).toString
    }.toOption.filter(!_.contains('\u0000'))
    projected == value.text
  }
  private def _check(valid: Boolean, reason: String): Consequence[Unit] =
    if (valid) Consequence.unit else F.invalid("covered review completion: " + reason)
}
