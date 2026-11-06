package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceFilePath, WorkspaceId}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.convert.ValueReader
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhasePlanRevision as R
import RepositorySyncManagedGitObservation as G

/** Current development selection in the existing native Session. A checkpoint
  * contains business observations and assessment, never another controller or
  * an authority grant. Native history remains the sole progression source.
  * @since   Nov.  5, 2026
  * @version Nov.  7, 2026
  */
private[smworkflow] object GoalPhaseDevelopmentReconstruction {
  val operation = "reconstructGoalPhaseDevelopment"
  val causation = "ordered-development-reconstructed"
  val choices = Vector("AdoptDevelopmentReconstruction", "KeepCurrentRequirements", "Defer", "Cancel")
  val schema = "sm-workflow.goal-phase-development-reconstruction.v1"
  private val _namespace = ":ordered-development-reconstruction:"

  final case class Documents(phasePath: String, checklistPath: String, phaseText: String,
    checklistText: String, captured: R.Captured)
  final case class Observation(root: String, branch: String, head: String, tree: String,
    indexTree: String, dirty: Vector[GoalPhaseCandidateObservationContinuation.Dirty])
  final case class Assessment(incompatible: Vector[String], questions: Vector[String],
    uncertainEffects: Vector[String], freshCapture: Boolean, retainEvidence: Boolean) {
    def reasons: Vector[String] = incompatible ++ questions ++ uncertainEffects
  }
  final case class Payload(sessionId: String, fromRevision: String, participant: String,
    componentInstance: String, definition: ContextReference, documents: Documents,
    observation: Observation, assessment: Assessment, rationale: String,
    sourceTargetGeneration: Option[Long], semanticResult: Option[ContextReference],
    candidateReference: Option[ContextReference], validationReference: Option[ContextReference],
    candidatePaths: Vector[String],
    previousSelection: Option[ContextReference] = None, humanAnswer: Option[ContextReference] = None)
  final case class Saved(record: InstanceRecord, controller: O.State, payload: Payload)

  def choicesFor(value: Payload): Vector[String] =
    if (value.assessment.incompatible.isEmpty && value.assessment.questions.nonEmpty &&
        value.assessment.uncertainEffects.isEmpty) choices
    else if (value.assessment.questions.nonEmpty || value.assessment.incompatible.nonEmpty)
      Vector("KeepCurrentRequirements", "Defer", "Cancel")
    else Vector("Defer", "Cancel")

  val codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "GoalPhaseDevelopmentReconstructionRecord"
    def encode(value: Payload): Json = Json.obj("schemaVersion" -> schema.asJson,
      "payload" -> value.asJson.mapObject(_.filter { case (key, nested) =>
        !Set("sourceTargetGeneration", "semanticResult", "candidateReference", "validationReference",
          "previousSelection", "humanAnswer")(key) || !nested.isNull
      }))
    def decode(value: Json): Either[String, Payload] = for {
      wire <- Option(value).toRight("development reconstruction is absent")
      _ <- Either.cond(wire.asObject.exists(_.keys.toSet == Set("schemaVersion", "payload")) &&
        wire.hcursor.get[String]("schemaVersion").contains(schema), (), "development reconstruction schema differs")
      payload <- wire.hcursor.get[Payload]("payload").left.map(_.message)
      _ <- Either.cond(valid(payload) && encode(payload) == wire, (), "development reconstruction is malformed or has unknown fields")
    } yield payload
  }

  /** Public Record projection can omit the Dirty owner's nullable member. The
    * durable codec remains exact; only this Human context presentation restores it.
    */
  def decodeContext(value: Json): Either[String, Payload] = Option(value)
    .toRight("development reconstruction is absent").flatMap { wire =>
      val restored = wire.mapObject { root => root("payload") match {
        case Some(payload) => root.add("payload", payload.mapObject { fields => fields("observation") match {
          case Some(observation) => fields.add("observation", observation.mapObject { facts => facts("dirty") match {
            case Some(dirty) => facts.add("dirty", dirty.mapArray(_.map(_.mapObject { entry =>
              if (entry("renamedFrom").isEmpty) entry.add("renamedFrom", Json.Null) else entry
            })))
            case None => facts
          } })
          case None => fields
        } })
        case None => root
      } }
      codec.decode(restored)
    }

  def proposalC(input: V.GoalPhaseDevelopmentReconstructionInput): Consequence[R.Proposal] = for {
    _ <- _check(input != null && _exact(input.sessionId) && _text(input.rationale), "business input is incomplete")
    _ <- GoalPhaseDocumentSource.validateC(GoalPhaseDocumentSource.Source(input.phasePath, input.checklistPath))
    selected <- R.fromPublicProposalC(Some(input.proposal))
    proposal <- selected.map(Consequence.success).getOrElse(F.invalid("development reconstruction proposal is absent"))
  } yield proposal

  /** The same ordinary Resource owner is used by deferred binding and execution. */
  def inputForCallC(incoming: Request, call: ActionCall.Core): Consequence[V.GoalPhaseDevelopmentReconstructionInput] = {
    final class Reader extends ProcedureActionCall {
      val core = call
      def readInputC: Consequence[V.GoalPhaseDevelopmentReconstructionInput] =
        NativeWorkflowResourceInput.selectC[org.goldenport.record.Record](incoming).flatMap(source =>
          NativeWorkflowResourceInput.readC(source, reference => read_resource_text(reference, Some(UTF_8))))
          .flatMap(_input_c)
      def execute(): Consequence[OperationResponse] = F.invalid("development reconstruction input reader is not an Operation")
    }
    new Reader().readInputC
  }

  private def _input_c(record: org.goldenport.record.Record): Consequence[V.GoalPhaseDevelopmentReconstructionInput] = for {
    raw <- record.toJsonStringC
    json <- io.circe.parser.parse(raw).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
      .fold(F.invalid, Consequence.success)
    _ <- _check(json.asObject.exists(fields => fields.keys.toSet ==
      Set("sessionId", "phasePath", "checklistPath", "proposal", "rationale") && fields.values.forall(value => !value.isNull)),
      "input requires exactly its non-null business fields")
    input <- summon[ValueReader[V.GoalPhaseDevelopmentReconstructionInput]].readC(record)
    _ <- proposalC(input)
  } yield input

  def documentsC(input: V.GoalPhaseDevelopmentReconstructionInput, d: F.Declaration,
    core: ActionCall.Core): Consequence[Documents] = for {
    proposal <- proposalC(input)
    workspace <- WorkspaceId.parseC("sm-workflow-repository")
    phasepath <- WorkspaceFilePath.parseC(input.phasePath)
    checklistpath <- WorkspaceFilePath.parseC(input.checklistPath)
    phase <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), phasepath)
    phasetext <- phase.text.filter(_text).map(Consequence.success).getOrElse(F.invalid("selected Phase document is missing or empty"))
    checklist <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), checklistpath)
    checklisttext <- checklist.text.filter(_text).map(Consequence.success).getOrElse(F.invalid("selected checklist is missing or empty"))
    items <- GoalPhaseDocumentPlan.checklistItemsC(checklisttext)
    selected <- GoalPhaseDocumentPlan.selectedPlanPresentC(phasetext)
    imported <- if (selected) GoalPhaseDocumentPlan.decodeC(phasetext, checklisttext).map(Some(_))
      else Consequence.success(Option.empty[GoalPhaseDocumentPlan.Imported])
    _ <- _check(imported.forall(_.plan == proposal.plan), "actual sm-workflow-plan differs from the complete raw proposal")
    _ <- _check(proposal.plan.acceptance.toVector.flatMap(_.conditions).forall(condition =>
      items.exists(item => item.identity == condition.identity && item.acceptance == condition.description)),
      "proposed checklist acceptance identities and text must exist in the actual checklist")
    plan <- GoalPhaseAcceptanceBoundary.captureC(d.projectAcceptance, proposal.plan)
    bound = imported.map(value => GoalPhaseDocumentSource.Bound(
      GoalPhaseDocumentSource.Source(input.phasePath, input.checklistPath), value.bindings, value.items))
    captured = R.Captured(proposal, plan, bound)
    _ <- R.validateCapturedC(captured)
  } yield Documents(input.phasePath, input.checklistPath, phasetext, checklisttext, captured)

  def observeC(d: F.Declaration, core: ActionCall.Core): Consequence[Observation] = for {
    root <- G.rootC(core)
    actual <- G.observeC(core)
    index <- G.indexTreeC(core)
    _ <- _check(root == d.target.root.toString && actual.branch == d.target.localBranch,
      "current source crosses its selected root or branch")
  } yield Observation(root, actual.branch, actual.head, actual.tree, index,
    actual.dirty.map(path => GoalPhaseCandidateObservationContinuation.Dirty(path.index.toString,
      path.worktree.toString, path.path, path.renamedFrom)))

  /** Pure compatibility; checked boxes supply no success or completion event. */
  def assess(controller: O.State, proposed: O.Plan, candidatePaths: Vector[String],
    checkIds: Vector[String], phaseCheckIds: Vector[String], observation: Observation,
    candidate: Option[O.Snapshot], unfinishedCapture: Boolean, sourceTargetGeneration: Option[Long],
    uncertainty: Vector[String] = Vector.empty): Assessment = {
    val prior = controller.plan
    val prefix = controller.cursor
    val oldconditions = prior.effectiveAcceptance.toVector.flatMap(_.conditions)
    val newconditions = proposed.effectiveAcceptance.toVector.flatMap(_.conditions)
    val incompatible = Vector(
      Option.when(proposed.identity != prior.identity)("Plan identity changed"),
      Option.when(proposed.phase != prior.phase)("Phase identity changed"),
      Option.when(proposed.repository != prior.repository)("repository identity changed"),
      Option.when(proposed.repairLimit != prior.repairLimit)("selected repair allowance changed"),
      Option.when(controller.reconstructionStoppedRepairReservation && proposed != prior)("stopped repair reservation cannot be amended"),
      Option.when(controller.reconstructionMovesRepairScope(proposed))("remaining Step would move a retained repair scope"),
      Option.when(proposed.steps.take(prefix) != prior.steps.take(prefix) || proposed.steps.size < prefix)("accepted completed Step identity, order or contract diverged"),
      Option.when(!proposed.phaseOwnedPaths.forall(path => candidatePaths.exists(owner => _covers(owner, path))))("proposed inventory exceeds admitted Component candidate coverage"),
      Option.when(!proposed.steps.flatMap(_.checks).forall(checkIds.contains) ||
        !proposed.phaseChecks.forall(phaseCheckIds.contains))("proposed checks exceed current declared host capability"),
      Option.when(!prior.phaseChecks.forall(proposed.phaseChecks.contains) || prior.steps.drop(prefix).exists(step =>
        !proposed.steps.find(_.identity == step.identity).exists(next => step.checks.forall(next.checks.contains))))("required original validation obligations were removed"),
      Option.when(!_preserves_policy_obligations(controller, proposed))("required original action policy obligations were removed or weakened"),
      Option.when(oldconditions.filter(_.required).exists(condition => !newconditions.exists(next =>
        next.identity == condition.identity && next.required && next.scope == condition.scope && next.description == condition.description)))("required original acceptance obligations were removed or redefined"),
      Option.when(controller.stage == O.Stage.Closed || controller.repositoryReceipt.nonEmpty || controller.releaseCommit.nonEmpty)("completed Phase cannot be reopened")
    ).flatten
    val questions = Vector(
      Option.when(prior.effectiveAcceptance != proposed.effectiveAcceptance)("current/proposed Usage Contract or acceptance conditions differ"),
      Option.when(prior.phaseReviewClassification != proposed.phaseReviewClassification)("independent Phase review classification differs"),
      Option.when(prior.steps.drop(prefix).map(_.identity) != proposed.steps.drop(prefix).map(_.identity))("remaining Step identity or order differs"),
      Option.when(prior.steps.drop(prefix).zip(proposed.steps.drop(prefix)).exists { (old, next) =>
        old.dependencies != next.dependencies || old.workClassification != next.workClassification || old.actionPolicy != next.actionPolicy
      })("remaining dependency, work classification or acceptance/review policy differs")
    ).flatten
    val owned = if (prefix == proposed.steps.size) proposed.phaseOwnedPaths else proposed.steps(prefix).ownedPaths
    val clean = observation.dirty.forall(path => !owned.exists(owner => _covers(owner, path.path) ||
      path.renamedFrom.exists(_covers(owner, _))) || (path.worktree == " " && path.index != "?"))
    val applicable = candidate.contains(O.Snapshot(observation.head, observation.indexTree)) && clean && proposed == prior
    val initial = sourceTargetGeneration.exists(generation => controller.generation == generation + 1)
    val fresh = !unfinishedCapture && !applicable && (candidate.nonEmpty || initial || proposed != prior)
    Assessment(incompatible, questions, uncertainty, fresh, applicable)
  }

  /** Absence retains the normal code/review route. Explicit policy obligations
    * can only disappear when that normal route still supplies them.
    */
  private def _preserves_policy_obligations(controller: O.State, proposed: O.Plan): Boolean =
    controller.plan.steps.drop(controller.cursor).forall(old => proposed.steps.find(_.identity == old.identity).exists { next =>
      old.actionPolicy match {
        case None => next.actionPolicy.forall(_.detailedReview == "required")
        case Some(policy) =>
          val selected = next.actionPolicy
          (policy.detailedReview != "required" || selected.forall(_.detailedReview == "required")) &&
            (policy.scalaLint != "required" || selected.exists(_.scalaLint == "required")) &&
            (policy.kind != "code" || selected.forall(_.kind == "code")) &&
            policy.observations.forall(observation => selected.exists(_.observations.contains(observation))) &&
            policy.scenarioInventory.forall(inventory => selected.exists(_.scenarioInventory.contains(inventory))) &&
            (policy.semanticTriage != "conditional" || selected.exists(_.semanticTriage == "conditional"))
      }
    })

  def selectedStateC(controller: O.State, payload: Payload,
    actualAnswer: Option[(String, ContextReference)] = None): Consequence[O.State] = for {
    _ <- _check(actualAnswer != null && payload.humanAnswer == actualAnswer.map(_._2) &&
      actualAnswer.forall(answer => Set("AdoptDevelopmentReconstruction", "KeepCurrentRequirements")(answer._1) &&
        choicesFor(payload).contains(answer._1)), "selection does not match its actual ordered Human choice and reference")
    keeping = actualAnswer.exists(_._1 == "KeepCurrentRequirements")
    adopted = actualAnswer.exists(_._1 == "AdoptDevelopmentReconstruction")
    assessment = if (keeping) assess(controller, controller.plan, payload.candidatePaths,
      controller.plan.steps.flatMap(_.checks), controller.plan.phaseChecks, payload.observation, controller.candidate,
      false,
      payload.sourceTargetGeneration, payload.assessment.uncertainEffects) else payload.assessment
    selected = if (!keeping && assessment.incompatible.isEmpty && (assessment.questions.isEmpty || adopted))
      payload.documents.captured.plan else controller.plan
    _ <- _check(selected == controller.plan || _preserves_policy_obligations(controller, selected),
      "selected requirements remove or weaken required original action policy obligations")
    reasons = assessment.incompatible ++ (if (actualAnswer.nonEmpty) Vector.empty else assessment.questions) ++ assessment.uncertainEffects
    state <- controller.reconstructDevelopmentC(selected, assessment.freshCapture && reasons.isEmpty,
      payload.sourceTargetGeneration, reasons, adoptDecision = actualAnswer.nonEmpty)
  } yield state

  def reference(from: InstanceRecord): WorkflowMechanicalTransition.Reference =
    WorkflowMechanicalTransition.Reference(from.identity.value + _namespace + from.revision.value, "1")
  def contextReference(saved: Saved): ContextReference = ContextReference(
    saved.record.identity.value + _namespace + saved.payload.fromRevision, "1")

  def saveC(session: F.Session, from: InstanceRecord, controller: O.State, d: F.Declaration,
    core: ActionCall.Core, payload: Payload,
    actualAnswer: Option[(String, ContextReference)] = None): Consequence[Saved] = for {
    _ <- _check(from == session.current && from.suspension.isEmpty && valid(payload),
      "selection requires its current unissued canonical native frontier")
    state <- selectedStateC(controller, payload, actualAnswer)
    event = if (state.stage == O.Stage.Decision && state.decision.nonEmpty)
      "development-reconstruction-uncertain" else "development-reconstructed-" + state.stage.toString
    _ <- _check(domain.statemachine.GoalPhaseRunLifecycle.permits(controller.stage.toString, event, state.stage.toString),
      "CML development reconstruction successor is absent")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    saved <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, reference(from), from, from.revision,
      F.history(from, "phase." + state.stage.toString, causation), TypedValue(codec.typeIdentity, payload)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield Saved(saved.receipt.next, state, payload)

  /** Replay only stored selection and actual historical predecessor, never current files/configuration. */
  def retainedC(session: F.Session, from: InstanceRecord, controller: O.State,
    core: ActionCall.Core, actualAnswer: Option[(String, ContextReference)] = None): Consequence[Saved] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    saved <- adapter.loadRetainedC(reference(from))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(F.invalid("actual common reconstruction record is missing")))
    payload = saved.receipt.payload.value
    _ <- _check(valid(payload) && payload.sessionId == from.identity.value && payload.fromRevision == from.revision.value.toString &&
      payload.participant == session.participantIdentity && payload.definition == ContextReference(
        from.definition.workflowIdentity.value, from.definition.workflowRevision.value) &&
      payload.componentInstance == core.component.get.core.instanceId.canonicalKey &&
      payload.observation.root == session.started.receipt.payload.value.root &&
      payload.observation.branch == session.started.receipt.payload.value.branch,
      "stored reconstruction crosses its actual Session, participant, definition or repository")
    state <- selectedStateC(controller, payload, actualAnswer)
    next <- from.appendC(from.revision, F.history(from, "phase." + state.stage.toString, causation))
    _ <- _check(next == saved.receipt.next && next.revision == from.revision.next,
      "stored reconstruction is not its adjacent managed successor")
    actual <- GoalPhaseNativeHistory.prefixC(session.current, next.revision)
    _ <- _check(actual == next, "stored reconstruction is outside actual contiguous history")
  } yield Saved(next, state, payload)

  def valid(value: Payload): Boolean = value != null &&
    Vector(value.sessionId, value.fromRevision, value.participant, value.componentInstance).forall(_exact) &&
    value.fromRevision.toLongOption.exists(_ > 0) && value.definition != null &&
    _exact(value.definition.identity) && _exact(value.definition.revision) && value.documents != null &&
    GoalPhaseDocumentSource.validateC(GoalPhaseDocumentSource.Source(value.documents.phasePath, value.documents.checklistPath)).isSuccess &&
    _text(value.documents.phaseText) && _text(value.documents.checklistText) && R.validateCapturedC(value.documents.captured).isSuccess &&
    value.observation != null && Vector(value.observation.root, value.observation.branch).forall(_exact) &&
    Vector(value.observation.head, value.observation.tree, value.observation.indexTree).forall(value =>
      value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) && value.observation.dirty != null &&
    value.assessment != null && value.assessment.incompatible != null && value.assessment.questions != null &&
    value.assessment.uncertainEffects != null && value.assessment.reasons.forall(_text) && _text(value.rationale) &&
    value.candidatePaths != null && value.candidatePaths.nonEmpty && value.candidatePaths.distinct == value.candidatePaths &&
    value.candidatePaths.forall(path => _exact(path) && RepositorySyncNativeConflictInput.relative(path)) &&
    value.sourceTargetGeneration != null && value.sourceTargetGeneration.forall(_ >= 0) &&
    Vector(value.semanticResult, value.candidateReference, value.validationReference,
      value.previousSelection, value.humanAnswer).forall(value => value != null && value.forall(reference =>
      reference != null && _exact(reference.identity) && _exact(reference.revision)))

  def notice(value: Payload, original: O.Plan): String = {
    val proposed = value.documents.captured.plan
    val oldconditions = original.effectiveAcceptance.toVector.flatMap(_.conditions)
    val newconditions = proposed.effectiveAcceptance.toVector.flatMap(_.conditions)
    val conditions = (oldconditions.map(_.identity) ++ newconditions.map(_.identity)).distinct.flatMap { identity =>
      val old = oldconditions.find(_.identity == identity)
      val next = newconditions.find(_.identity == identity)
      Option.when(old != next)("Condition " + identity + ": " + old.map(_.toString).getOrElse("absent") +
        " -> " + next.map(_.toString).getOrElse("absent"))
    }
    val steps = (original.steps.map(_.identity) ++ proposed.steps.map(_.identity)).distinct.flatMap { identity =>
      val old = original.steps.find(_.identity == identity)
      val next = proposed.steps.find(_.identity == identity)
      (old, next) match {
        case (Some(before), Some(after)) => Vector(
          Option.when(before.purpose != after.purpose)("Step " + identity + " purpose: " + before.purpose + " -> " + after.purpose),
          Option.when(before.ownedPaths != after.ownedPaths)("Step " + identity + " paths: " + before.ownedPaths.mkString(", ") + " -> " + after.ownedPaths.mkString(", ")),
          Option.when(before.checks != after.checks)("Step " + identity + " checks: " + before.checks.mkString(", ") + " -> " + after.checks.mkString(", ")),
          Option.when(before.dependencies != after.dependencies)("Step " + identity + " dependencies: " + before.dependencies.mkString(", ") + " -> " + after.dependencies.mkString(", ")),
          Option.when(before.workClassification != after.workClassification)("Step " + identity + " work classification: " + before.workClassification + " -> " + after.workClassification),
          Option.when(before.actionPolicy != after.actionPolicy)("Step " + identity + " acceptance/review policy: " + before.actionPolicy + " -> " + after.actionPolicy)
        ).flatten
        case _ => Vector("Step " + identity + ": " + old.map(_.toString).getOrElse("absent") + " -> " + next.map(_.toString).getOrElse("absent"))
      }
    }
    val oldusage = original.effectiveAcceptance.flatMap(_.usage)
    val newusage = proposed.effectiveAcceptance.flatMap(_.usage)
    val changes = Vector(
      Option.when(original.phase != proposed.phase)("Phase identity: " + original.phase + " -> " + proposed.phase),
      Option.when(original.repository != proposed.repository)("Repository identity: " + original.repository + " -> " + proposed.repository),
      Option.when(original.repairLimit != proposed.repairLimit)("Repair allowance: " + original.repairLimit + " -> " + proposed.repairLimit),
      Option.when(original.steps.map(_.identity) != proposed.steps.map(_.identity))(
        "Step order: " + original.steps.map(_.identity).mkString(", ") + " -> " + proposed.steps.map(_.identity).mkString(", ")),
      Option.when(original.phaseRepairInventory != proposed.phaseRepairInventory)(
        "Phase repair inventory: " + original.phaseRepairInventory.mkString(", ") + " -> " + proposed.phaseRepairInventory.mkString(", ")),
      Option.when(oldusage != newusage)("Usage Contract: " + oldusage.getOrElse("absent") + " -> " + newusage.getOrElse("absent")),
      Option.when(original.phaseChecks != proposed.phaseChecks)("Phase checks: " + original.phaseChecks.mkString(", ") + " -> " + proposed.phaseChecks.mkString(", ")),
      Option.when(original.phaseReviewClassification != proposed.phaseReviewClassification)("Phase review classification: " + original.phaseReviewClassification + " -> " + proposed.phaseReviewClassification)
    ).flatten ++ conditions ++ steps
    val offered = choicesFor(value)
    val directions = Vector(
      Option.when(offered.contains("AdoptDevelopmentReconstruction"))(
        "Adopt selects only this resolvable requirements ambiguity within the existing Component and host capability."),
      Option.when(offered.contains("KeepCurrentRequirements"))(
        "KeepCurrentRequirements retains original requirements/provenance and actual current source without rollback, resolving only the document projection choice.")
    ).flatten.mkString(" ")
    "Reconstruct current development in the SAME Session. Current Plan " + original.identity + "@" + original.revision +
      "; proposed Plan " + proposed.identity + "@" + proposed.revision + "; actual Phase " + value.documents.phasePath +
      "; actual checklist " + value.documents.checklistPath + "; observed source " + value.observation.head +
      "; index " + value.observation.indexTree + ". Changes: " + changes.mkString("; ") +
      ". Assessment: " + value.assessment.reasons.mkString("; ") + ". Applicable choices: " + offered.mkString(", ") +
      ". " + directions + " Any unresolved worker, File, Git, commit, publication or push effect remains UNKNOWN and requires its existing observation/Human owner. Defer and Cancel retain all history. No effects or repair allowance are authorized by this choice."
  }

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[org.goldenport.protocol.spec.OperationDefinition] =
    Vector(new Operation(port))
  private final class Operation(port: GoalPhaseNativeWorkContinuation.Port) extends org.goldenport.protocol.spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(
      SmWorkflowComponent.GoalPhaseServiceService.ReconstructGoalPhaseDevelopmentOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") || request.operation != operation)
        F.invalid("development reconstruction Operation identity differs")
      else Consequence.success(Action(request, port))
  }
  private final case class Action(request: Request, port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        input <- inputForCallC(Action.this.request, core)
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(input.sessionId))
        result <- port.reconstructDevelopmentC(input, core.copy(executionContext = context))
      } yield NativeWorkflowOperationOutput.response(result)
    }
  }
  private def _covers(owner: String, path: String): Boolean = path == owner || path.startsWith(owner + "/")
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000')
  private def _check(valid: Boolean, reason: String): Consequence[Unit] =
    if (valid) Consequence.unit else F.invalid("Development reconstruction: " + reason)
}
