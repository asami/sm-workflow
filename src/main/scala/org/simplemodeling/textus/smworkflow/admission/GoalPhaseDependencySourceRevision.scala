package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.file.Path
import java.security.MessageDigest
import org.goldenport.Consequence
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.{FileRepresentation, FileTarget, WorkspaceFilePath, WorkspaceId}
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.record.Record
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseOrderedHumanDecision as H
import GoalPhaseDependencyValidation as D
import GoalPhaseOrderedProgression as O
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** A new source direction retains its predecessor and is not source acceptance.
  * Native capture/check/review/closure owners qualify the newly selected bytes.
  * @since   Oct. 26, 2026
  * @version Oct. 26, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseDependencySourceRevision {
  val purpose = "goal-phase-dependency-source-revision"
  val describeOperation = "describeGoalPhaseDependencySourceRevision"
  val adoptionOperation = "adoptGoalPhaseDependencySourceRevision"
  val schema = "sm-workflow.goal-phase-dependency-source-revision.v1"
  val origin = "dependency-source-revision"
  val choices = Vector("AdoptDependencySourceRevision", "Defer")
  val message = "Select the exact new dependency source for fresh qualification in the same Session. " +
    "Retain the original primary base, accepted Steps, predecessor admissions, approvals and repair history. " +
    "Direction is not acceptance and runs no check, staging, commit, Submit or Advance."

  final case class FileIdentity(path: String, present: Boolean, length: Long, sha256: String)
  object FileIdentity {
    given Encoder[FileIdentity] = deriveEncoder
    given Decoder[FileIdentity] = closed(Set("path", "present", "length", "sha256"), deriveDecoder, fileValid)
  }
  final case class Entry(path: String, mode: String, blob: String)
  object Entry {
    given Encoder[Entry] = deriveEncoder
    given Decoder[Entry] = closed(Set("path", "mode", "blob"), deriveDecoder, entryValid)
  }
  final case class Dirty(index: String, worktree: String, path: String, renamedFrom: Option[String])
  object Dirty {
    given Encoder[Dirty] = deriveEncoder
    given Decoder[Dirty] = closed(Set("index", "worktree", "path", "renamedFrom"), deriveDecoder, dirtyValid,
      optional = Set("renamedFrom"))
  }
  final case class Guard(root: String, branch: String, head: String, committedTree: String,
    indexTree: String, dirty: Vector[Dirty], selectedFiles: Vector[FileIdentity], excludedFiles: Vector[FileIdentity],
    selectedEntries: Vector[Entry], excludedEntries: Vector[Entry])
  object Guard {
    given Encoder[Guard] = deriveEncoder
    given Decoder[Guard] = closed(Set("root", "branch", "head", "committedTree", "indexTree", "dirty",
      "selectedFiles", "excludedFiles", "selectedEntries", "excludedEntries"), deriveDecoder, guardValid)
  }
  final case class Coverage(approval: ContextReference, acceptance: Vector[String], checks: Vector[String])
  object Coverage {
    given Encoder[Coverage] = deriveEncoder
    given Decoder[Coverage] = closed(Set("approval", "acceptance", "checks"), deriveDecoder, coverageValid)
  }
  final case class ExpectedMode(path: String, mode: String)
  object ExpectedMode {
    given Encoder[ExpectedMode] = deriveEncoder
    given Decoder[ExpectedMode] = closed(Set("path", "mode"), deriveDecoder,
      value => value != null && path(value.path) && Set("100644", "100755", "000000")(value.mode))
  }
  final case class CheckProgram(check: String, workspace: String, kind: String, program: String)
  object CheckProgram {
    given Encoder[CheckProgram] = deriveEncoder
    given Decoder[CheckProgram] = closed(Set("check", "workspace", "kind", "program"), deriveDecoder, programValid)
  }
  final case class Consumption(primaryWorkspace: String, sourceRoot: String,
    runtimeImage: String, manifestReference: ContextReference, nonInputExclusions: Vector[String],
    expectedModes: Vector[ExpectedMode], programs: Vector[CheckProgram])
  object Consumption {
    given Encoder[Consumption] = deriveEncoder
    given Decoder[Consumption] = closed(Set("primaryWorkspace", "sourceRoot", "runtimeImage", "manifestReference",
      "nonInputExclusions", "expectedModes", "programs"),
      deriveDecoder, consumptionValid)
  }
  final case class Proposal(schemaVersion: String, binding: ContextReference, mode: String,
    excludedPaths: Vector[String], coverage: Vector[Coverage], consumption: Consumption)
  object Proposal {
    given Encoder[Proposal] = deriveEncoder
    given Decoder[Proposal] = closed(Set("schemaVersion", "binding", "mode", "excludedPaths", "coverage", "consumption"),
      deriveDecoder, proposalValid)
  }

  final case class Ancestor(capture: D.Capture, closureRevision: String)
  object Ancestor {
    import D.given
    given Encoder[Ancestor] = deriveEncoder
    given Decoder[Ancestor] = closed(Set("capture", "closureRevision"), deriveDecoder, ancestorValid)
  }
  final case class Target(schemaVersion: String, sessionId: String, questionIdentity: String,
    sourceRevision: String, pendingRevision: String, plan: O.Plan, step: String, generation: Long,
    primaryBase: O.Snapshot, completed: Vector[O.ClosedStep], repairProgress: GoalPhaseRepairProgress.Snapshot,
    requestingResult: ContextReference, predecessor: D.Admission, ancestors: Vector[Ancestor],
    proposal: Proposal, binding: D.Binding, predecessorGuard: Guard, preStageGuard: Guard)
  object Target {
    import D.given
    given Encoder[Target] = deriveEncoder
    given Decoder[Target] = closed(Set("schemaVersion", "sessionId", "questionIdentity", "sourceRevision",
      "pendingRevision", "plan", "step", "generation", "primaryBase", "completed", "repairProgress",
      "requestingResult", "predecessor", "ancestors", "proposal", "binding", "predecessorGuard", "preStageGuard"), deriveDecoder, targetValid)
  }
  final case class Direction(target: Target, directive: ContextReference, participant: String, rationale: String)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    given Decoder[Direction] = closed(Set("target", "directive", "participant", "rationale"), deriveDecoder, directionValid)
  }
  final case class Capture(schemaVersion: String, sessionId: String, plan: ContextReference, step: String,
    generation: Long, sourceRevision: String, primaryBase: O.Snapshot, direction: Direction,
    postStageGuard: Guard, stagedPaths: Vector[String], preStageGuard: Guard)
  object Capture {
    given Encoder[Capture] = deriveEncoder
    given Decoder[Capture] = closed(Set("schemaVersion", "sessionId", "plan", "step", "generation",
      "sourceRevision", "primaryBase", "direction", "postStageGuard", "stagedPaths", "preStageGuard"), deriveDecoder, captureValid)
  }

  final case class StageChange(oldMode: String, newMode: String, oldBlob: Option[String],
    newBlob: Option[String], status: String, path: String)
  object StageChange {
    given Encoder[StageChange] = deriveEncoder
    given Decoder[StageChange] = closed(Set("oldMode", "newMode", "oldBlob", "newBlob", "status", "path"),
      deriveDecoder, changeValid, optional = Set("oldBlob", "newBlob"))
  }
  final case class StageFact(path: String, before: Guard, after: Guard, changes: Vector[StageChange])
  object StageFact {
    given Encoder[StageFact] = deriveEncoder
    given Decoder[StageFact] = closed(Set("path", "before", "after", "changes"), deriveDecoder, stageFactValid)
  }
  def change(value: GitTreeChange): StageChange = StageChange(value.oldMode, value.newMode,
    value.oldBlob.map(_.value), value.newBlob.map(_.value), value.status, value.path.value)
  def changeValid(value: StageChange): Boolean = value != null && path(value.path) &&
    Set("100644", "100755", "000000")(value.oldMode) && Set("100644", "100755", "000000")(value.newMode) &&
    value.oldBlob != null && value.oldBlob.forall(oid) && value.newBlob != null && value.newBlob.forall(oid) &&
    ((value.oldMode == "000000") == value.oldBlob.isEmpty) &&
    ((value.newMode == "000000") == value.newBlob.isEmpty) && Set("A", "M", "D")(value.status)
  def stageFactValid(value: StageFact): Boolean = value != null && value.changes != null &&
    value.changes.forall(changeValid) && stageTransitionValid(value.before, value.after, value.path,
      value.changes.map(c => GitTreeChange(c.oldMode, c.newMode, c.oldBlob.map(b => GitObjectId.parseC(b).toOption.get),
        c.newBlob.map(b => GitObjectId.parseC(b).toOption.get), c.status, GitPath.parseC(c.path).toOption.get)))

  /** A later ordinary repair may change selected bytes, never exclusions or source identity. */
  def captureBaseValid(direction: Direction, generation: Long, guard: Guard): Boolean =
    directionValid(direction) && guardModesValid(direction, guard) && {
      val original = direction.target.preStageGuard
      if (generation == direction.target.generation + 1) guard == original
      else generation > direction.target.generation + 1 &&
        guard.copy(indexTree = original.indexTree, dirty = original.dirty,
          selectedFiles = original.selectedFiles, selectedEntries = original.selectedEntries) == original
    }

  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else D.invalid("dependency source revision requires the independent Human evidence provider")
  }
  final case class Source(human: H.Source, predecessor: D.Admission, ancestors: Vector[Ancestor])

  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def targetC(value: Record): Consequence[Target] = recordC[Target](value)
  def proposalC(value: Record): Consequence[Proposal] = recordC[Proposal](value)
  private def recordC[A: Decoder](value: Record): Consequence[A] =
    if (value == null) D.invalid("dependency source revision record is absent")
    else value.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(_.as[A].left.map(_.message))
      .fold(D.invalid(_), Consequence.success))

  def bindingC(component: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (text(component) && targetValid(target) && choices.contains(choice) && text(rationale) &&
        text(participant) && referenceValid(reference))
      Consequence.success(Json.obj("componentInstance" -> component.asJson, "dependencySourceRevision" -> Json.obj(
        "target" -> target.asJson, "directive" -> Json.obj("choice" -> choice.asJson,
          "rationale" -> rationale.asJson, "participant" -> participant.asJson, "reference" -> reference.asJson))))
    else D.invalid("dependency source direction requires its exact target, actual participant and reference")

  def selectBinding(value: Json): Either[String, Json] = for {
    supplied <- Option(value).toRight("dependency source direction is absent")
    component <- supplied.hcursor.get[String]("componentInstance").left.map(_.message)
    target <- supplied.hcursor.downField("dependencySourceRevision").get[Target]("target").left.map(_.message)
    directive = supplied.hcursor.downField("dependencySourceRevision").downField("directive")
    choice <- directive.get[String]("choice").left.map(_.message)
    rationale <- directive.get[String]("rationale").left.map(_.message)
    participant <- directive.get[String]("participant").left.map(_.message)
    reference <- directive.get[ContextReference]("reference").left.map(_.message)
    selected <- bindingC(component, target, choice, rationale, participant, reference)
      .map[Either[String, Json]](Right(_)).fold(error => Left(error.toString), identity)
    _ <- Either.cond(selected == supplied, (), "dependency source direction has contradictory fields")
  } yield selected

  private def pendingC(source: H.Source): Consequence[InstanceRecord] = for {
    following <- GoalPhaseNativeHistory.nextC(source.session.current, source.from)
    pending <- following match {
      case Some(value) if value.revision == source.session.current.revision && value.suspension.exists(s =>
          s.continuationIdentity.value == H.identity(source).value && s.completion.value == "goal-phase-ordered-human-completion-v1") =>
        GoalPhaseNativeHistory.prefixC(source.session.current, value.revision)
      case _ => D.invalid("dependency source revision requires the exact latest unanswered Human frontier")
    }
  } yield pending

  private def resumedC(source: H.Source, reference: String): Consequence[O.State] = for {
    _ <- if (source.controller.stage == O.Stage.Decision &&
        source.controller.decisionSource.exists(Set(O.Stage.Implementation, O.Stage.Repair)) &&
        source.sourceHistoryRecovery.isEmpty && source.result.result.value.outcome == "decision-needed" &&
        source.controller.pendingRepair.isEmpty && source.controller.closureIntent.isEmpty &&
        source.controller.cursor == source.controller.completed.size &&
        source.controller.cursor < source.controller.plan.steps.size) Consequence.unit
      else D.invalid("dependency source revision is not an arbitrary frontier or unsettled-effect recovery")
    state <- source.controller.acceptC(O.DecisionResumptionAdmitted(reference))
    _ <- if (state.plan == source.controller.plan && state.base == source.controller.base &&
        state.completed == source.controller.completed &&
        GoalPhaseRepairProgress.snapshot(state) == GoalPhaseRepairProgress.snapshot(source.controller)) Consequence.unit
      else D.invalid("dependency source direction cannot change Plan, primary base, prefix or repair accounting")
  } yield state

  private def targetForC(source: Source, declaration: F.Declaration, core: ActionCall.Core,
    proposal: Proposal): Consequence[Target] = for {
    _ <- if (source != null && D.admissionValid(source.predecessor) && proposalValid(proposal)) Consequence.unit
      else D.invalid("source revision requires a native standing dependency and closed proposal")
    human = source.human
    _ <- resumedC(human, "source-revision-description-eligibility")
    pending <- pendingC(human)
    _ <- if (human.result.result.value.dependencyAdmission.contains(source.predecessor)) Consequence.unit
      else D.invalid("source revision predecessor is not the actual native semantic admission")
    selected <- declaration.dependencySourceRevisions.find(b => b.identity == proposal.binding.identity &&
      b.revision.toString == proposal.binding.revision).map(Consequence.success)
      .getOrElse(D.invalid("dependency source binding is not independently declared by this host"))
    _ <- if (declaration.dependencySourceConsumptions.contains(proposal.consumption)) Consequence.unit
      else D.invalid("primary source/runtime consumption is not the admitted host declaration")
    inherited = (source.predecessor.acceptedPrefix.toVector.flatMap(_.paths) ++ source.predecessor.binding.paths).distinct
    old <- observeC(core, source.predecessor.binding.copy(paths = inherited), Vector.empty)
    fresh <- observeC(core, selected, proposal.excludedPaths)
    step <- human.input.stepIdentity.map(Consequence.success).getOrElse(D.invalid("source revision has no current Step"))
    target = Target(schema, human.session.current.identity.value, H.identity(human).value,
      human.from.revision.value.toString, pending.revision.value.toString, human.controller.plan, step,
      human.controller.generation, human.controller.base, human.controller.completed,
      GoalPhaseRepairProgress.snapshot(human.controller), human.result.resultReference,
      source.predecessor, source.ancestors, proposal, selected, old, fresh)
    _ <- if (targetValid(target)) Consequence.unit else D.invalid("source revision crosses inherited source or acceptance obligations")
    questions <- human.session.storage.bindDecisionC(H.completionOperation, H.inputCodec)
    issued <- questions.loadC(H.identity(human))(using core.executionContext)
    _ <- if (issued.nonEmpty) Consequence.unit else D.invalid("source revision requires the actual issued unanswered question")
  } yield target

  def describeC(source: Source, declaration: F.Declaration, core: ActionCall.Core,
    raw: Record): Consequence[V.GoalPhaseDependencySourceRevisionDescriptionResult] = for {
    proposal <- proposalC(raw)
    target <- targetForC(source, declaration, core, proposal)
  } yield V.GoalPhaseDependencySourceRevisionDescriptionResult(target.sessionId, "direction-required", message,
    core.component.get.core.instanceId.canonicalKey, targetRecord(target), choices, adoptionOperation)

  def adoptC(source: Source, declaration: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhaseDependencySourceRevisionAdoptionInput): Consequence[V.GoalPhaseDependencySourceRevisionAdoptionResult] = for {
    supplied <- targetC(input.target)
    _ <- if (input.sessionId == supplied.sessionId && choices.contains(input.choice) && text(input.rationale) &&
        input.reference != null && referenceValid(ContextReference(input.reference.identity, input.reference.revision))) Consequence.unit
      else D.invalid("dependency source direction input is incomplete or crossed")
    result <- if (input.choice == "Defer") Consequence.success(
      V.GoalPhaseDependencySourceRevisionAdoptionResult(input.sessionId, false, "deferred", "No source direction was stored"))
    else for {
      actual <- targetForC(source, declaration, core, supplied.proposal)
      _ <- if (actual == supplied) Consequence.unit else D.invalid("dependency source direction target drifted")
      installation <- declaration.dependencySourceRevision.map(Consequence.success).getOrElse(
        Consequence.securityPermissionDenied("dependency source direction has no independent Human provider"))
      _ <- installation.validateC
      reference = ContextReference(input.reference.identity, input.reference.revision)
      binding <- bindingC(core.component.get.core.instanceId.canonicalKey, actual, input.choice, input.rationale,
        source.human.session.participantIdentity, reference)
      _ <- installation.evidence.admitC(core, reference, binding)
      state <- resumedC(source.human, reference.identity)
      pending <- pendingC(source.human)
      adapter <- source.human.session.storage.bindDecisionSupersessionC(H.completionOperation, H.inputCodec,
        GoalPhaseLegacyPurposeRepairContinuation.storageCodec)
      direction = Direction(actual, reference, source.human.session.participantIdentity, input.rationale)
      disposition: GoalPhaseLegacyPurposeRepairContinuation.StoredDisposition =
        GoalPhaseLegacyPurposeRepairContinuation.DependencySourceRevision(direction)
      _ <- adapter.executeC(H.identity(source.human), pending.revision, pending,
        F.history(pending, "phase." + state.stage.toString, reference.identity),
        TypedValue(GoalPhaseLegacyPurposeRepairContinuation.storageCodec.typeIdentity, disposition))(using core.executionContext)
    } yield V.GoalPhaseDependencySourceRevisionAdoptionResult(input.sessionId, true,
      "dependency-source-revision-adopted", message)
  } yield result

  /** Reconstruct stored direction only; do not substitute current source bytes for
    * its historical guard. The walker separately authenticates the ancestor chain.
    */
  private[admission] def reconstructC(source: H.Source, declaration: F.Declaration, core: ActionCall.Core,
    direction: Direction, sourceRevision: InstanceRevision, nextRevision: InstanceRevision):
      Consequence[GoalPhaseRemainingPlanAdoption.Selected] = for {
    _ <- if (directionValid(direction)) Consequence.unit else D.invalid("retained source direction is invalid")
    target = direction.target
    pending <- GoalPhaseNativeHistory.prefixC(source.session.current, sourceRevision)
    _ <- if (target.sessionId == source.session.current.identity.value && target.questionIdentity == H.identity(source).value &&
        target.sourceRevision == source.from.revision.value.toString && target.pendingRevision == sourceRevision.value.toString &&
        target.plan == source.controller.plan && target.primaryBase == source.controller.base &&
        target.completed == source.controller.completed && target.generation == source.controller.generation &&
        target.repairProgress == GoalPhaseRepairProgress.snapshot(source.controller) &&
        target.requestingResult == source.result.resultReference &&
        source.result.result.value.dependencyAdmission.contains(target.predecessor) &&
        direction.participant == source.session.participantIdentity &&
        declaration.dependencySourceRevisions.contains(target.binding) &&
        declaration.dependencySourceConsumptions.contains(target.proposal.consumption)) Consequence.unit
      else D.invalid("retained source direction crosses its actual native source or host declaration")
    state <- resumedC(source, direction.directive.identity)
    next <- GoalPhaseNativeHistory.completionC(source.session.current, pending, direction.directive, state.stage.toString)
    _ <- if (next.revision == nextRevision && nextRevision == sourceRevision.next) Consequence.unit
      else D.invalid("source direction receipt is not the actual contiguous native transition")
  } yield GoalPhaseRemainingPlanAdoption.Selected(state, next, source.documents,
    dependencySourceDirection = Some(direction))

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseDependencySourceRevisionOperation
      else SmWorkflowComponent.GoalPhaseServiceService.AdoptGoalPhaseDependencySourceRevisionOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") ||
          request.operation != (if (describe) describeOperation else adoptionOperation))
        D.invalid("dependency source operation identity differs")
      else if (describe) NativeWorkflowOperationInput.valueC[V.GoalPhaseDependencySourceRevisionDescriptionInput](request, "input")
        .map(value => Action(request, value.sessionId, Some(value), None, port))
      else NativeWorkflowOperationInput.valueC[V.GoalPhaseDependencySourceRevisionAdoptionInput](request, "input")
        .map(value => Action(request, value.sessionId, None, Some(value), port))
  }
  private final case class Action(request: Request, sessionId: String,
    description: Option[V.GoalPhaseDependencySourceRevisionDescriptionInput],
    adoption: Option[V.GoalPhaseDependencySourceRevisionAdoptionInput],
    port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(sessionId))
        selected = core.copy(executionContext = context)
        result <- description match {
          case Some(value) => port.describeDependencySourceRevisionC(value, selected).map(NativeWorkflowOperationOutput.response(_))
          case None => port.adoptDependencySourceRevisionC(adoption.get, selected).map(NativeWorkflowOperationOutput.response(_))
        }
      } yield result
    }
  }

  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = closed(Set("identity", "revision"), deriveDecoder, referenceValid)

  private def closed[A](keys: Set[String], decoder: Decoder[A], valid: A => Boolean,
    optional: Set[String] = Set.empty): Decoder[A] =
    Decoder.instance(cursor =>
      if (!cursor.value.asObject.exists(obj =>
          (keys -- optional).subsetOf(obj.keys.toSet) && obj.keys.toSet.subsetOf(keys)))
        Left(DecodingFailure("Dependency source revision requires exact versioned fields", cursor.history))
      else decoder.tryDecode(cursor).flatMap(value =>
        if (valid(value)) Right(value)
        else Left(DecodingFailure("Dependency source revision fields are invalid", cursor.history))))

  private def text(value: String): Boolean = value != null && value.nonEmpty && value.length <= 4096 &&
    !value.exists(c => Character.isISOControl(c))
  private def path(value: String): Boolean = value != null && RepositorySyncNativeConflictInput.relative(value)
  private def oid(value: String): Boolean = value != null && GitObjectId.parseC(value).isSuccess
  private def root(value: String): Boolean = text(value) && scala.util.Try(Path.of(value)).toOption.exists(p =>
    p.isAbsolute && p.normalize.toString == value)
  private def unique[A](values: Vector[A], maximum: Int)(valid: A => Boolean): Boolean =
    values != null && values.size <= maximum && values.distinct == values && values.forall(valid)
  def referenceValid(value: ContextReference): Boolean = value != null && text(value.identity) && text(value.revision)
  def fileValid(value: FileIdentity): Boolean = value != null && path(value.path) && value.length >= 0 &&
    value.sha256 != null && (if (value.present) value.sha256.matches("[0-9a-f]{64}")
      else value.length == 0 && value.sha256.isEmpty)
  def entryValid(value: Entry): Boolean = value != null && path(value.path) &&
    Set("100644", "100755")(value.mode) && oid(value.blob)
  def dirtyValid(value: Dirty): Boolean = value != null && path(value.path) && value.renamedFrom != null &&
    value.renamedFrom.forall(path) && value.index != null && value.index.length == 1 &&
    value.worktree != null && value.worktree.length == 1 &&
    G.DirtyPath(value.index.head, value.worktree.head, value.path, value.renamedFrom).unmerged == false &&
    ((value.index == "?" && value.worktree == "?") ||
      (" MADRCT".contains(value.index.head) && " MADRCT".contains(value.worktree.head)))
  def guardValid(value: Guard): Boolean = value != null && root(value.root) &&
    GitBranchName.parseC(value.branch).isSuccess && Vector(value.head, value.committedTree, value.indexTree).forall(oid) &&
    unique(value.dirty, 256)(dirtyValid) && unique(value.selectedFiles, 128)(fileValid) &&
    value.selectedFiles.nonEmpty && unique(value.excludedFiles, 128)(fileValid) &&
    unique(value.selectedEntries, 128)(entryValid) && unique(value.excludedEntries, 128)(entryValid) &&
    value.selectedFiles.map(_.path).distinct.size == value.selectedFiles.size &&
    value.excludedFiles.map(_.path).distinct.size == value.excludedFiles.size &&
    value.selectedFiles.map(_.path).toSet.intersect(value.excludedFiles.map(_.path).toSet).isEmpty &&
    value.selectedEntries.map(_.path).distinct.size == value.selectedEntries.size &&
    value.excludedEntries.map(_.path).distinct.size == value.excludedEntries.size &&
    value.selectedEntries.forall(e => value.selectedFiles.exists(_.path == e.path)) &&
    value.excludedEntries.forall(e => value.excludedFiles.exists(_.path == e.path))
  def coverageValid(value: Coverage): Boolean = value != null && referenceValid(value.approval) &&
    unique(value.acceptance, 32)(text) && value.acceptance.nonEmpty &&
    unique(value.checks, 16)(text) && value.checks.nonEmpty
  def consumptionValid(value: Consumption): Boolean = value != null &&
    WorkspaceId.parseC(value.primaryWorkspace).isSuccess && root(value.sourceRoot) && text(value.runtimeImage) &&
    referenceValid(value.manifestReference) && unique(value.nonInputExclusions, 128)(path) &&
    unique(value.expectedModes, 128)(m => m != null && path(m.path) && Set("100644", "100755", "000000")(m.mode)) &&
    value.expectedModes.nonEmpty && value.expectedModes.map(_.path).distinct.size == value.expectedModes.size &&
    unique(value.programs, 32)(programValid) && value.programs.nonEmpty &&
    value.programs.map(_.check).distinct.size == value.programs.size
  def programValid(value: CheckProgram): Boolean = value != null && text(value.check) &&
    WorkspaceId.parseC(value.workspace).isSuccess && text(value.program) &&
    Set("compile", "test", "test-only", "fix-check")(value.kind)
  def proposalValid(value: Proposal): Boolean = value != null && value.schemaVersion == schema &&
    referenceValid(value.binding) && Set("selected-changes", "committed")(value.mode) &&
    unique(value.excludedPaths, 128)(path) && unique(value.coverage, 16)(coverageValid) &&
    value.coverage.nonEmpty && value.coverage.map(_.approval).distinct.size == value.coverage.size &&
    consumptionValid(value.consumption)

  private def revision(value: String): Boolean = value != null &&
    value.toLongOption.exists(n => n > 0 && n.toString == value)
  def ancestorValid(value: Ancestor): Boolean = value != null && D.captureValid(value.capture) && revision(value.closureRevision)
  def targetValid(value: Target): Boolean = value != null && value.schemaVersion == schema &&
    text(value.sessionId) && text(value.questionIdentity) && revision(value.sourceRevision) && revision(value.pendingRevision) &&
    BigInt(value.pendingRevision) > BigInt(value.sourceRevision) && value.plan != null && O.validatePlanC(value.plan).isSuccess &&
    text(value.step) && value.plan.steps.exists(_.identity == value.step) && value.generation > 0 &&
    value.primaryBase != null && oid(value.primaryBase.head) && oid(value.primaryBase.tree) &&
    value.completed != null && value.completed.forall(_ != null) && value.completed.size < value.plan.steps.size &&
    value.completed.map(_.step) == value.plan.steps.take(value.completed.size).map(_.identity) &&
    value.plan.steps(value.completed.size).identity == value.step &&
    GoalPhaseRepairProgress.validateC(value.repairProgress).isSuccess && value.repairProgress.limit == value.plan.repairLimit &&
    referenceValid(value.requestingResult) && D.admissionValid(value.predecessor) &&
    unique(value.ancestors, 64)(a => ancestorValid(a) && a.capture.session == value.sessionId &&
      a.capture.plan == ContextReference(value.plan.identity, value.plan.revision) && a.capture.primaryBase == value.primaryBase &&
      value.completed.exists(_.step == a.capture.step) && BigInt(a.closureRevision) <= BigInt(value.sourceRevision)) &&
    proposalValid(value.proposal) && D.bindingValid(value.binding) &&
    value.binding.identity == value.proposal.binding.identity && value.binding.revision.toString == value.proposal.binding.revision &&
    value.binding.identity == value.predecessor.binding.identity && value.binding.revision > value.predecessor.binding.revision &&
    value.binding.step == value.step && value.binding.requester == value.predecessor.binding.requester &&
    value.binding.dependency.identity == value.predecessor.binding.dependency.identity &&
    value.binding.dependency.repository == value.predecessor.binding.dependency.repository &&
    value.binding.root == value.proposal.consumption.sourceRoot &&
    value.binding.requester.workspace == value.proposal.consumption.primaryWorkspace &&
    value.proposal.excludedPaths == value.proposal.consumption.nonInputExclusions &&
    value.proposal.consumption.expectedModes.map(_.path) == value.binding.paths &&
    (!value.proposal.consumption.programs.exists(p => value.binding.checks.exists(_.identity == p.check)) ||
      value.binding.checks.forall(check => value.proposal.consumption.programs.exists(p =>
        p.check == check.identity && p.kind == check.kind.wire))) &&
    value.binding.paths.toSet.intersect(value.proposal.excludedPaths.toSet).isEmpty &&
    guardValid(value.predecessorGuard) && value.predecessorGuard.root == value.predecessor.binding.root &&
    value.predecessorGuard.branch == value.predecessor.binding.branch &&
    value.predecessorGuard.head == value.predecessor.binding.base.head &&
    value.predecessorGuard.committedTree == value.predecessor.binding.base.tree &&
    guardValid(value.preStageGuard) && value.preStageGuard.root == value.binding.root &&
    value.preStageGuard.selectedEntries.forall(e => value.proposal.consumption.expectedModes.exists(m => m.path == e.path && m.mode == e.mode)) &&
    value.preStageGuard.branch == value.binding.branch && value.preStageGuard.head == value.binding.base.head &&
    value.preStageGuard.committedTree == value.binding.base.tree &&
    value.preStageGuard.selectedFiles.map(_.path) == value.binding.paths &&
    value.preStageGuard.excludedFiles.map(_.path) == value.proposal.excludedPaths &&
    value.preStageGuard.dirty.forall(p => (value.binding.paths ++ value.proposal.excludedPaths).contains(p.path) &&
      p.renamedFrom.forall((value.binding.paths ++ value.proposal.excludedPaths).contains)) &&
    (value.proposal.mode != "committed" || !value.preStageGuard.dirty.exists(d => value.binding.paths.contains(d.path))) &&
    coverageMatches(value)
  private def coverageMatches(value: Target): Boolean = {
    val approvals = (value.predecessor.approvals ++ value.ancestors.flatMap(_.capture.admission.approvals))
      .groupBy(_.decision).values.map(_.head).toVector
    value.proposal.coverage.map(_.approval).toSet == approvals.map(_.decision).toSet &&
      value.proposal.coverage.forall(c => approvals.find(_.decision == c.approval).exists(a =>
        c.acceptance == a.proposal.acceptance && c.checks.forall(id => value.binding.checks.exists(_.identity == id)))) &&
      value.binding.checks.forall(check => value.proposal.coverage.exists(_.checks.contains(check.identity)))
  }
  def directionValid(value: Direction): Boolean = value != null && targetValid(value.target) &&
    referenceValid(value.directive) && text(value.participant) && text(value.rationale)
  def captureValid(value: Capture): Boolean = value != null && value.schemaVersion == schema &&
    directionValid(value.direction) && value.sessionId == value.direction.target.sessionId &&
    value.plan != null && value.plan == ContextReference(value.direction.target.plan.identity, value.direction.target.plan.revision) &&
    value.step == value.direction.target.step && value.generation > value.direction.target.generation &&
    revision(value.sourceRevision) && BigInt(value.sourceRevision) > BigInt(value.direction.target.pendingRevision) &&
    value.primaryBase == value.direction.target.primaryBase &&
    captureBaseValid(value.direction, value.generation, value.preStageGuard) && guardModesValid(value.direction, value.postStageGuard) &&
    value.stagedPaths != null && (if (value.direction.target.proposal.mode == "committed")
      value.stagedPaths.isEmpty && value.postStageGuard == value.preStageGuard &&
        !value.postStageGuard.dirty.exists(d => value.direction.target.binding.paths.contains(d.path))
      else value.stagedPaths == value.direction.target.binding.paths &&
        !value.postStageGuard.dirty.exists(d => value.direction.target.binding.paths.contains(d.path) && d.worktree != " ") &&
        value.postStageGuard.copy(indexTree = value.preStageGuard.indexTree,
          dirty = value.preStageGuard.dirty, selectedEntries = value.preStageGuard.selectedEntries) == value.preStageGuard)

  def candidateValid(value: GoalPhaseNativeCandidate.Payload): Boolean = value != null &&
    value.dependencySourceDirection != null && value.dependencySourceGuard != null && value.dependencySourceCapture != null &&
    value.dependencySourceStages != null && value.dependencySourceStages.forall(stageFactValid) &&
    (value.dependencySourceDirection match {
      case None => value.dependencySourceGuard.isEmpty && value.dependencySourceStages.isEmpty && value.dependencySourceCapture.isEmpty
      case Some(direction) => directionValid(direction) && value.dependencySourceStages.forall(stageModeValid(direction, _)) &&
        value.dependencyAdmission.isEmpty && value.dependency.isEmpty &&
        value.dependencyStaged.isEmpty && value.dependencyOrigin.isEmpty &&
        value.dependencySourceGuard.forall(guardModesValid(direction, _)) &&
        value.dependencySourceStages.map(_.path) == direction.target.binding.paths.take(value.dependencySourceStages.size) &&
        value.dependencySourceStages.zipWithIndex.forall { (fact, index) =>
          (if (index == 0) value.dependencySourceGuard.contains(fact.before)
          else value.dependencySourceStages(index - 1).after == fact.before)
        } && value.dependencySourceCapture.forall(c => captureValid(c) && c.direction == direction &&
          c.sourceRevision == value.sourceRevision && value.dependencySourceGuard.contains(c.preStageGuard) &&
          c.stagedPaths == value.dependencySourceStages.map(_.path) &&
          c.postStageGuard == value.dependencySourceStages.lastOption.map(_.after).getOrElse(c.preStageGuard))
    })

  /** Uses the existing candidate owner, per-path intent/result and native UoW. */
  def captureC(core: ActionCall.Core, controller: O.State, current: GoalPhaseNativeCandidate.Saved,
    loadC: String => Consequence[Option[GoalPhaseNativeCandidate.Saved]],
    saveC: (String, InstanceRecord, GoalPhaseNativeCandidate.Payload) => Consequence[GoalPhaseNativeCandidate.Saved],
    acknowledgeC: String => Consequence[Unit]): Consequence[GoalPhaseNativeCandidate.Saved] = current.payload.dependencySourceDirection match {
    case None => Consequence.success(current)
    case Some(direction) =>
      val binding = direction.target.binding
      def observe: Consequence[Guard] = observeC(core, binding, direction.target.proposal.excludedPaths)
      def git[A](op: GitOperation[A]): Consequence[A] = E.executeGitC(core, op, binding.dependency.workspace).flatMap(_.valueC)
      for {
        _ <- if (candidateValid(current.payload) && controller.cursor < controller.plan.steps.size &&
            controller.plan.steps(controller.cursor).identity == direction.target.step &&
            controller.base == direction.target.primaryBase && controller.plan == direction.target.plan &&
            current.record.identity.value == direction.target.sessionId) Consequence.unit
          else D.invalid("source capture crosses its native direction, Session, Plan or primary base")
        before <- observe
        _ <- if (captureBaseValid(direction, controller.generation, before)) Consequence.unit
          else D.invalid("source capture differs from its adopted bytes or permitted ordinary repair")
        existing <- loadC("dependency-source-inspection")
        _ <- if (existing.isEmpty) Consequence.unit else D.invalid("source capture already has a retained inspection; do not replay")
        inspected <- saveC("dependency-source-inspection", current.record, current.payload.copy(dependencySourceGuard = Some(before)))
        staged <- (if (direction.target.proposal.mode == "committed") Vector.empty else binding.paths).zipWithIndex
          .foldLeft(Consequence.success(inspected)) { case (prior, (path, index)) => for {
            previous <- prior
            retained <- loadC("dependency-source-stage-intent-" + index)
            _ <- if (retained.isEmpty) Consequence.unit else D.invalid("source staging intent is retained; its unknown result is not replayed")
            actualBefore <- observe
            expected = previous.payload.dependencySourceStages.lastOption.map(_.after).getOrElse(before)
            _ <- if (actualBefore == expected) Consequence.unit else D.invalid("source changed before its selected stage")
            intent <- saveC("dependency-source-stage-intent-" + index, previous.record, previous.payload)
            _ <- acknowledgeC("goal-dependency-source-stage-prepared:" + path)
            selection <- GitSelectionId.parseC("goal-path-" + (index + 1))
            _ <- git(GitOperation.StageSelectedAll(selection))
            after <- observe
            oldTree <- GitObjectId.parseC(actualBefore.indexTree)
            newTree <- GitObjectId.parseC(after.indexTree)
            changes <- git(GitOperation.TreeChanges(oldTree, newTree))
            fact = StageFact(path, actualBefore, after, changes.map(change))
            _ <- if (stageFactValid(fact) && stageModeValid(direction, fact)) Consequence.unit
              else D.invalid("source staging changed an unselected entry, source byte or adopted file mode")
            _ <- changes.foldLeft(Consequence.unit)((p, c) => p.flatMap(_ => c.newBlob match {
              case None => Consequence.unit
              case Some(blob) => git(GitOperation.BlobContent(blob)).flatMap(content =>
                if (after.selectedFiles.find(_.path == path).contains(fileIdentity(path, Some(content.bytes)))) Consequence.unit
                else D.invalid("selected index blob differs from the guarded source bytes"))
            }))
            _ <- if (!after.dirty.exists(d => d.path == path && d.worktree != " ")) Consequence.unit
              else D.invalid("selected source was not fully staged")
            result <- saveC("dependency-source-stage-result-" + index, intent.record,
              previous.payload.copy(dependencySourceStages = previous.payload.dependencySourceStages :+ fact))
            _ <- acknowledgeC("goal-dependency-source-stage-recorded:" + path)
          } yield result }
        intent <- saveC("dependency-source-observation-intent", staged.record, staged.payload)
        after <- observe
        capture = Capture(schema, current.record.identity.value, ContextReference(controller.plan.identity, controller.plan.revision),
          binding.step, controller.generation, current.payload.sourceRevision, controller.base, direction, after,
          staged.payload.dependencySourceStages.map(_.path), before)
        _ <- if (captureValid(capture) && after == staged.payload.dependencySourceStages.lastOption.map(_.after).getOrElse(before))
          Consequence.unit else D.invalid("source capture changed after its actual stage prefix")
        result <- saveC("dependency-source-observation-result", intent.record, staged.payload.copy(dependencySourceCapture = Some(capture)))
      } yield result
  }

  def guardModesValid(direction: Direction, guard: Guard): Boolean = directionValid(direction) && guardValid(guard) &&
    guard.selectedEntries.forall(entry => direction.target.proposal.consumption.expectedModes.exists(mode =>
      mode.path == entry.path && mode.mode == entry.mode))

  def stageModeValid(direction: Direction, fact: StageFact): Boolean = directionValid(direction) && stageFactValid(fact) &&
    guardModesValid(direction, fact.before) && guardModesValid(direction, fact.after) &&
    fact.changes.forall(change => direction.target.proposal.consumption.expectedModes.exists(m =>
      m.path == change.path && m.mode == change.newMode))

  /** Existing recovery retains a source fact prefix and offers explicit ReviseWork.
    * No old source observation is imported into a new capture namespace.
    */
  def recoveryPrefixValid(target: GoalPhaseCandidateRecovery.RecoveryTarget): Boolean = {
    val initial = target.stagingPrefix.last.result
    val direction = initial.dependencySourceDirection
    val prefix = target.dependencyPrefix
    direction.exists(d => directionValid(d) && initial.dependencySourceGuard.isEmpty &&
      initial.dependencySourceStages.isEmpty && initial.dependencySourceCapture.isEmpty && {
      val paths = if (d.target.proposal.mode == "committed") Vector.empty else d.target.binding.paths
      val keys = Vector("dependency-source-inspection") ++ paths.indices.flatMap(i =>
        Vector("dependency-source-stage-intent-" + i, "dependency-source-stage-result-" + i)) ++
        Vector("dependency-source-observation-intent", "dependency-source-observation-result")
      prefix.size <= keys.size && prefix.zipWithIndex.forall { (fact, index) =>
        val previous = if (index == 0) initial else prefix(index - 1).payload
        val identity = target.sessionId + ":ordered-candidate:" + target.sourceRevision + ":" + keys(index)
        fact != null && fact.key == keys(index) && fact.identity == identity && fact.revision == "1" &&
          revision(fact.recordRevision) && BigInt(fact.recordRevision) ==
            BigInt(target.stagingPrefix.last.resultRecordRevision) + index + 1 && candidateValid(fact.payload) &&
          (if (index == 0) fact.payload.dependencySourceGuard.exists(g =>
            captureBaseValid(d, target.controller.generation, g) && fact.payload == previous.copy(dependencySourceGuard = Some(g)))
          else if (fact.key.startsWith("dependency-source-stage-result-"))
            fact.payload.dependencySourceStages.lastOption.exists(stage => stageModeValid(d, stage) &&
              fact.payload == previous.copy(dependencySourceStages = previous.dependencySourceStages :+ stage))
          else if (fact.key == "dependency-source-observation-result") fact.payload.dependencySourceCapture.exists(c =>
            captureValid(c) && c.generation == target.controller.generation && c.primaryBase == target.controller.base &&
            c.sourceRevision == target.sourceRevision && fact.payload == previous.copy(dependencySourceCapture = Some(c)))
          else fact.payload == previous)
      }
    })
  }

  /** Reauthenticate each fact through the original candidate store and contiguous history. */
  def retainedCaptureC(candidate: GoalPhaseNativeCandidate.Saved,
    loadC: String => Consequence[Option[GoalPhaseNativeCandidate.Saved]]): Consequence[Unit] = {
    def required(key: String): Consequence[GoalPhaseNativeCandidate.Saved] = loadC(key).flatMap(_.map(Consequence.success)
      .getOrElse(D.invalid("missing native source fact: " + key)))
    def adjacent(before: GoalPhaseNativeCandidate.Saved, after: GoalPhaseNativeCandidate.Saved, key: String): Consequence[Unit] =
      if (before.record.identity == after.record.identity && before.record.definition == after.record.definition &&
          after.record.revision.value == before.record.revision.value + 1 &&
          after.record.history.dropRight(1) == before.record.history && after.record.causation.value == "ordered-candidate:" + key)
        Consequence.unit else D.invalid("source fact crosses its actual candidate predecessor")
    if (candidate.payload.dependencySourceDirection.isEmpty) Consequence.unit else for {
      capture <- candidate.payload.dependencySourceCapture.filter(captureValid).map(Consequence.success)
        .getOrElse(D.invalid("adopted source lacks its actual completed capture"))
      _ <- if (candidateValid(candidate.payload)) Consequence.unit else D.invalid("invalid retained source candidate")
      first <- required("dependency-source-inspection")
      _ <- if (first.payload.dependencySourceDirection.contains(capture.direction) && first.payload.dependencySourceCapture.isEmpty &&
          first.payload.dependencySourceStages.isEmpty && first.payload.dependencySourceGuard.contains(capture.preStageGuard) &&
          first.payload.sourceRevision == candidate.payload.sourceRevision && first.payload.step == candidate.payload.step)
        Consequence.unit else D.invalid("source inspection differs from its actual direction or candidate generation")
      last <- candidate.payload.dependencySourceStages.zipWithIndex.foldLeft(Consequence.success(first)) {
        case (prior, (fact, index)) => for {
          previous <- prior
          intent <- required("dependency-source-stage-intent-" + index)
          _ <- adjacent(previous, intent, "dependency-source-stage-intent-" + index)
          _ <- if (intent.payload == previous.payload) Consequence.unit else D.invalid("source stage intent rewrites its captured prefix")
          result <- required("dependency-source-stage-result-" + index)
          _ <- adjacent(intent, result, "dependency-source-stage-result-" + index)
          _ <- if (stageFactValid(fact) && result.payload == intent.payload.copy(
              dependencySourceStages = intent.payload.dependencySourceStages :+ fact)) Consequence.unit
            else D.invalid("source stage result is not its actual recorded single-path transition")
        } yield result
      }
      intent <- required("dependency-source-observation-intent")
      _ <- adjacent(last, intent, "dependency-source-observation-intent")
      _ <- if (intent.payload == last.payload) Consequence.unit else D.invalid("source observation rewrites its actual completed stages")
      result <- required("dependency-source-observation-result")
      _ <- adjacent(intent, result, "dependency-source-observation-result")
      _ <- if (result.payload == intent.payload.copy(dependencySourceCapture = Some(capture)) &&
          candidate.record.identity == result.record.identity && candidate.record.definition == result.record.definition &&
          candidate.record.history.take(result.record.history.size) == result.record.history)
        Consequence.unit else D.invalid("source capture has no actual retained observation result")
    } yield ()
  }

  def freshnessC(core: ActionCall.Core, capture: Capture): Consequence[Unit] =
    if (!captureValid(capture)) D.invalid("invalid source capture")
    else observeC(core, capture.direction.target.binding, capture.direction.target.proposal.excludedPaths).flatMap(actual =>
      if (actual == capture.postStageGuard) Consequence.unit
      else D.invalid("dependency source bytes or excluded metadata drifted after capture"))

  def binding(legacy: Option[D.Capture], revised: Option[Capture]): Option[D.Binding] =
    if (legacy.nonEmpty && revised.nonEmpty) None
    else revised.filter(captureValid).map(_.direction.target.binding)
      .orElse(legacy.filter(D.captureValid).map(_.admission.binding))

  /** Only an authenticated new source candidate supersedes a live ancestor comparison. */
  def chainFreshnessC(core: ActionCall.Core, chain: Option[GoalPhaseNativeWorkContinuation.DependencyChain],
    legacy: Vector[D.Capture], revised: Vector[Capture]): Consequence[Unit] = {
    val sources = (chain.toVector.flatMap(_.acceptedSources.map(_.capture)) ++ revised).distinct
    if (sources.isEmpty) GoalPhaseNativeDependencyValidation.chainFreshnessC(core, chain, legacy)
    else sources.foldLeft(Consequence.unit)((prior, capture) => prior.flatMap(_ =>
      chain.map(_.sourceCaptureC(capture)).getOrElse(D.invalid("revised source requires actual native lineage")))).flatMap { _ =>
      val superseded = sources.map(_.direction.target.binding.dependency.identity).toSet
      val retainedLegacy = legacy.filterNot(c => superseded(c.admission.binding.dependency.identity))
      val acceptedLegacy = chain.toVector.flatMap(_.accepted.map(_.capture))
        .filterNot(c => superseded(c.admission.binding.dependency.identity))
      // Original V1 capture validation remains strict. Proven superseded histories
      // are authenticated, while their new source owns the current byte check.
      val proof = (legacy ++ chain.toVector.flatMap(_.accepted.map(_.capture))).distinct
        .foldLeft(Consequence.unit)((prior, c) => prior.flatMap(_ => chain.map(_.captureC(c))
          .getOrElse(D.invalid("dependency history requires actual native lineage"))))
      proof.flatMap(_ => (acceptedLegacy ++ retainedLegacy).distinct.groupBy(_.admission.binding.dependency.identity).values
        .foldLeft(Consequence.unit)((prior, history) => {
          val selected = if (history.last.admission.acceptedPrefix.nonEmpty) Vector(history.last) else history
          selected.foldLeft(prior)((p, c) => p.flatMap(_ => GoalPhaseNativeDependencyValidation.freshnessC(core, c, chain)))
        }))
        .flatMap(_ => sources.groupBy(_.direction.target.binding.dependency.identity).values
          .foldLeft(Consequence.unit)((prior, history) => prior.flatMap(_ => freshnessC(core, history.last))))
    }
  }

  private[admission] def observeC(core: ActionCall.Core, binding: D.Binding,
    excluded: Vector[String]): Consequence[Guard] = {
    def git[A](operation: GitOperation[A]): Consequence[A] =
      E.executeGitC(core, operation, binding.dependency.workspace).flatMap(_.valueC)
    def nativeC: Consequence[(String, G.Observed, String)] = for {
      actualRoot <- git(GitOperation.Root)
      actual <- G.observeInWorkspaceC(core, binding.dependency.workspace)
      index <- git(GitOperation.IndexTree)
    } yield (actualRoot, actual, index.value)
    for {
      _ <- if (D.bindingValid(binding) && unique(excluded, 128)(path) &&
          binding.paths.toSet.intersect(excluded.toSet).isEmpty) Consequence.unit
        else D.invalid("dependency source observation requires exact admitted paths")
      before <- nativeC
      _ <- if (before._1 == binding.root && before._2.branch == binding.branch &&
          before._2.head == binding.base.head && before._2.tree == binding.base.tree &&
          before._2.dirty.forall(p => (binding.paths ++ excluded).contains(p.path) &&
            p.renamedFrom.forall((binding.paths ++ excluded).contains))) Consequence.unit
        else D.invalid("dependency source observation crosses its actual root, base or bounded ownership")
      head <- GitObjectId.parseC(before._2.head)
      index <- GitObjectId.parseC(before._3)
      changes <- git(GitOperation.TreeChanges(head, index))
      _ <- if (changes.forall(c => (binding.paths ++ excluded).contains(c.path.value))) Consequence.unit
        else D.invalid("dependency index includes an unselected build-relevant path")
      selected <- filesC(core, binding.dependency.workspace, binding.paths)
      exclusions <- filesC(core, binding.dependency.workspace, excluded)
      after <- nativeC
      selectedAgain <- filesC(core, binding.dependency.workspace, binding.paths)
      exclusionsAgain <- filesC(core, binding.dependency.workspace, excluded)
      finalNative <- nativeC
      _ <- if (before == after && after == finalNative && selected == selectedAgain && exclusions == exclusionsAgain)
        Consequence.unit else D.invalid("dependency bytes or native guards changed during observation")
      entries = changes.flatMap(c => c.newBlob.map(blob => Entry(c.path.value, c.newMode, blob.value)))
      guard = Guard(before._1, before._2.branch, before._2.head, before._2.tree, before._3,
        before._2.dirty.map(p => Dirty(p.index.toString, p.worktree.toString, p.path, p.renamedFrom)),
        selected, exclusions, entries.filter(e => binding.paths.contains(e.path)), entries.filter(e => excluded.contains(e.path)))
      _ <- if (guardValid(guard)) Consequence.unit else D.invalid("unsupported dependency source entry or mode")
    } yield guard
  }

  def fileIdentity(path: String, bytes: Option[Vector[Byte]]): FileIdentity = bytes match {
    case None => FileIdentity(path, false, 0L, "")
    case Some(value) => FileIdentity(path, true, value.size.toLong,
      MessageDigest.getInstance("SHA-256").digest(value.toArray).map(b => f"${b & 0xff}%02x").mkString)
  }

  /** The managed reader owns bounds, real absence and regular-file/link checks.
    * Only identity metadata for exclusions enters the dependency source record.
    */
  private[admission] def filesC(core: ActionCall.Core, workspace: String, paths: Vector[String]):
      Consequence[Vector[FileIdentity]] = for {
    selected <- WorkspaceId.parseC(workspace)
    files <- paths.foldLeft(Consequence.success(Vector.empty[FileIdentity])) { (prior, name) => for {
      result <- prior
      relative <- WorkspaceFilePath.parseC(name)
      observed <- WorkflowFileExecution.readBytesC(core, FileTarget(selected), relative, FileRepresentation.Identity)
      _ <- if (observed != null && observed.target == FileTarget(selected) && observed.path == relative &&
          observed.representation == FileRepresentation.Identity && observed.bytes != null) Consequence.unit
        else D.invalid("managed source byte observation crossed its admitted target")
    } yield result :+ fileIdentity(name, observed.bytes) }
  } yield files

  /** An exact retained stage result may change only its one selected index entry.
    * Worktree content, the original base and excluded entry identities stay fixed.
    */
  def stageTransitionValid(before: Guard, after: Guard, selectedPath: String,
    changes: Vector[GitTreeChange]): Boolean = guardValid(before) && guardValid(after) && path(selectedPath) &&
    before.selectedFiles.exists(_.path == selectedPath) &&
    before.copy(indexTree = after.indexTree, dirty = after.dirty, selectedEntries = after.selectedEntries) == after &&
    before.dirty.filterNot(_.path == selectedPath) == after.dirty.filterNot(_.path == selectedPath) &&
    before.selectedEntries.filterNot(_.path == selectedPath) == after.selectedEntries.filterNot(_.path == selectedPath) &&
    changes != null && changes.size <= 1 && changes.forall(c => c != null && c.path.value == selectedPath) &&
    (if (changes.isEmpty) before.indexTree == after.indexTree && before.selectedEntries == after.selectedEntries
      else before.indexTree != after.indexTree && changes.forall(c => c.newBlob match {
        case None => !after.selectedEntries.exists(_.path == selectedPath) &&
          after.selectedFiles.find(_.path == selectedPath).exists(!_.present)
        case Some(blob) => after.selectedEntries.exists(e => e.path == selectedPath &&
          e.blob == blob.value && e.mode == c.newMode) || !after.selectedEntries.exists(_.path == selectedPath)
      }))
}
