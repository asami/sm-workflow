package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import org.goldenport.cncf.cli.InstalledRuntimeDistribution
import org.goldenport.cncf.config.RuntimeConfig
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
  * @version Oct. 27, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseDependencySourceRevision {
  val purpose = "goal-phase-dependency-source-revision"
  val describeOperation = "describeGoalPhaseDependencySourceRevision"
  val adoptionOperation = "adoptGoalPhaseDependencySourceRevision"
  val schema = "sm-workflow.goal-phase-dependency-source-revision.v1"
  val runtimeSchema = "sm-workflow.goal-phase-dependency-source-revision.v2"
  val runtimeScopeSchema = "sm-workflow.goal-phase-dependency-source-scope.v2"
  val exclusionSchema = "sm-workflow.goal-phase-dependency-source-revision.v3"
  val exclusionSuccessionSchema = "sm-workflow.goal-phase-noninput-exclusion-succession.v1"
  val exclusionScopeSchema = "sm-workflow.goal-phase-dependency-source-scope.v3"
  val exclusionCaptureSchema = "sm-workflow.goal-phase-dependency-source-capture.v3"
  val committedSchema = "sm-workflow.goal-phase-dependency-source-revision.v4"
  val committedScopeSchema = "sm-workflow.goal-phase-dependency-source-scope.v4"
  val committedCaptureSchema = "sm-workflow.goal-phase-dependency-source-capture.v4"
  val committedBaseSuccessionSchema = "sm-workflow.goal-phase-committed-base-succession.v1"
  val committedInventorySelection = "goal-source-committed-inventory"
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
  /** Current installed profile observation; qualification remains independent evidence. */
  final case class RuntimeArtifact(coordinate: String, path: String, source: String)
  object RuntimeArtifact {
    given Encoder[RuntimeArtifact] = deriveEncoder
    given Decoder[RuntimeArtifact] = closed(Set("coordinate", "path", "source"), deriveDecoder,
      value => value != null && text(value.coordinate) && root(value.path) && root(value.source))
  }
  final case class RuntimeSelection(installation: String, update: String, manifest: String,
    modules: Vector[RuntimeArtifact], componentIdentity: String, component: RuntimeArtifact)
  object RuntimeSelection {
    given Encoder[RuntimeSelection] = deriveEncoder
    given Decoder[RuntimeSelection] = closed(Set("installation", "update", "manifest", "modules",
      "componentIdentity", "component"), deriveDecoder, runtimeSelectionValid)
  }
  final case class RuntimeSuccession(parent: ContextReference, approvals: Vector[D.Approval],
    installation: RuntimeSelection)
  object RuntimeSuccession {
    import D.given
    given Encoder[RuntimeSuccession] = deriveEncoder
    given Decoder[RuntimeSuccession] = closed(Set("parent", "approvals", "installation"), deriveDecoder,
      value => value != null && referenceValid(value.parent) && unique(value.approvals, 16)(D.approvalValid) &&
        value.approvals.nonEmpty && value.approvals.map(_.decision).distinct.size == value.approvals.size &&
        runtimeSelectionValid(value.installation))
  }
  final case class Target(schemaVersion: String, sessionId: String, questionIdentity: String,
    sourceRevision: String, pendingRevision: String, plan: O.Plan, step: String, generation: Long,
    primaryBase: O.Snapshot, completed: Vector[O.ClosedStep], repairProgress: GoalPhaseRepairProgress.Snapshot,
    requestingResult: ContextReference, predecessor: D.Admission, ancestors: Vector[Ancestor],
    proposal: Proposal, binding: D.Binding, predecessorGuard: Guard, preStageGuard: Guard,
    runtime: Option[RuntimeSuccession] = None, exclusionSuccession: Option[ExclusionSuccession] = None,
    committedBaseSuccession: Option[CommittedBaseSuccession] = None)
  final case class CommittedObject(mode: String, blob: String)
  object CommittedObject {
    given Encoder[CommittedObject] = deriveEncoder
    given Decoder[CommittedObject] = closed(Set("mode", "blob"), deriveDecoder,
      value => value != null && Set("100644", "100755")(value.mode) && oid(value.blob))
  }
  final case class CommittedEntry(path: String, entry: Option[CommittedObject])
  object CommittedEntry {
    given Encoder[CommittedEntry] = deriveEncoder[CommittedEntry].mapJson(_.mapObject(_.filter {
      case ("entry", value) => !value.isNull
      case _ => true
    }))
    private val _decoder: Decoder[CommittedEntry] = closed(Set("path", "entry"), deriveDecoder,
      committedEntryValid, optional = Set("entry"))
    given Decoder[CommittedEntry] = Decoder.instance { cursor =>
      if (cursor.downField("entry").focus.contains(Json.Null))
        Left(DecodingFailure("committed entry must be absent or a regular mode/blob", cursor.history))
      else _decoder(cursor)
    }
  }
  final case class CommittedBaseSuccession(schemaVersion: String, parent: ContextReference,
    previousBase: O.Snapshot, nextBase: O.Snapshot, changes: Vector[StageChange],
    phaseChangedPaths: Vector[String], previousExcludedChangedPaths: Vector[String],
    addedExcludedChangedPaths: Vector[String], baselineChangedPaths: Vector[String],
    sourceMode: String, committedEntries: Vector[CommittedEntry])
  object CommittedBaseSuccession {
    import D.given
    private given Decoder[O.Snapshot] = closed(Set("head", "tree"), deriveDecoder[O.Snapshot],
      value => value != null && oid(value.head) && oid(value.tree))
    given Encoder[CommittedBaseSuccession] = deriveEncoder
    given Decoder[CommittedBaseSuccession] = closed(Set("schemaVersion", "parent", "previousBase", "nextBase",
      "changes", "phaseChangedPaths", "previousExcludedChangedPaths", "addedExcludedChangedPaths",
      "baselineChangedPaths", "sourceMode", "committedEntries"), deriveDecoder, committedBaseValid)
  }
  final case class ExclusionSuccession(schemaVersion: String, parent: ContextReference,
    previousExclusions: Vector[String], addedPaths: Vector[String])
  object ExclusionSuccession {
    given Encoder[ExclusionSuccession] = deriveEncoder
    given Decoder[ExclusionSuccession] = closed(Set("schemaVersion", "parent", "previousExclusions", "addedPaths"),
      deriveDecoder, _exclusion_succession_valid)
  }
  private def _exclusion_succession_valid(value: ExclusionSuccession): Boolean = value != null &&
    value.schemaVersion == exclusionSuccessionSchema && referenceValid(value.parent) &&
    unique(value.previousExclusions, 128)(path) && unique(value.addedPaths, 128)(path) && value.addedPaths.nonEmpty &&
    unique(value.previousExclusions ++ value.addedPaths, 128)(path)
  object Target {
    import D.given
    given Encoder[Target] = deriveEncoder[Target].mapJson(_.mapObject(_.filter {
      case ("runtime" | "exclusionSuccession" | "committedBaseSuccession", value) => !value.isNull
      case _ => true
    }))
    private val _decoder: Decoder[Target] = closed(Set("schemaVersion", "sessionId", "questionIdentity", "sourceRevision",
      "pendingRevision", "plan", "step", "generation", "primaryBase", "completed", "repairProgress",
      "requestingResult", "predecessor", "ancestors", "proposal", "binding", "predecessorGuard", "preStageGuard", "runtime", "exclusionSuccession", "committedBaseSuccession"),
      deriveDecoder, targetValid, optional = Set("runtime", "exclusionSuccession", "committedBaseSuccession"))
    given Decoder[Target] = Decoder.instance { cursor =>
      if (Vector("runtime", "exclusionSuccession", "committedBaseSuccession").exists(key => cursor.downField(key).focus.contains(Json.Null)))
        Left(DecodingFailure("runtime must be absent or a closed installed consumption succession", cursor.history))
      else _decoder(cursor)
    }
  }
  final case class Direction(target: Target, directive: ContextReference, participant: String, rationale: String)
  object Direction {
    given Encoder[Direction] = deriveEncoder
    given Decoder[Direction] = closed(Set("target", "directive", "participant", "rationale"), deriveDecoder, directionValid)
  }
  val scopeSchema = "sm-workflow.goal-phase-dependency-source-scope.v1"
  val scopeCaptureSchema = "sm-workflow.goal-phase-dependency-source-capture.v2"

  /** Structural data is not authority; native traversal authenticates its decisions. */
  final case class ScopeSuccessor(schemaVersion: String, predecessor: ContextReference,
    binding: D.Binding, approvals: Vector[D.Approval], consumption: Consumption, coverage: Vector[Coverage],
    consumptionDirection: Option[Direction] = None)
  object ScopeSuccessor {
    import D.given
    given Encoder[ScopeSuccessor] = deriveEncoder[ScopeSuccessor].mapJson(_.mapObject(_.filter {
      case ("consumptionDirection", value) => !value.isNull
      case _ => true
    }))
    private val _decoder: Decoder[ScopeSuccessor] = closed(Set("schemaVersion", "predecessor", "binding", "approvals",
      "consumption", "coverage", "consumptionDirection"), deriveDecoder, _scope_shape_valid,
      optional = Set("consumptionDirection"))
    given Decoder[ScopeSuccessor] = Decoder.instance { cursor =>
      if (cursor.downField("consumptionDirection").focus.contains(Json.Null))
        Left(DecodingFailure("consumptionDirection must be absent or a closed native direction", cursor.history))
      else _decoder(cursor)
    }
  }
  private def _scope_shape_valid(value: ScopeSuccessor): Boolean = value != null &&
    value.consumptionDirection != null &&
    value.consumptionDirection.forall(d => directionValid(d) && d.target.runtime.nonEmpty) &&
    value.schemaVersion == _scope_schema(value.consumptionDirection) &&
    referenceValid(value.predecessor) && D.bindingValid(value.binding) &&
    unique(value.approvals, 16)(D.approvalValid) && value.approvals.nonEmpty &&
    value.approvals.map(_.decision).distinct.size == value.approvals.size && consumptionValid(value.consumption) &&
    unique(value.coverage, 16)(coverageValid) && value.coverage.map(_.approval).distinct.size == value.coverage.size

  private def _scope_approvals(direction: Direction, approvals: Vector[D.Approval]): Vector[D.Approval] =
    approvals.filter(a => a.step == direction.target.step &&
      a.proposal.dependency.identity == direction.target.binding.dependency.identity &&
      a.proposal.dependency.repository == direction.target.binding.dependency.repository &&
      !direction.target.proposal.coverage.exists(_.approval == a.decision))

  private def _scope_schema(direction: Option[Direction]): String =
    if (direction.exists(_.target.committedBaseSuccession.nonEmpty)) committedScopeSchema
    else if (direction.exists(_.target.exclusionSuccession.nonEmpty)) exclusionScopeSchema
    else if (direction.nonEmpty) runtimeScopeSchema else scopeSchema

  private def _capture_schema(scope: Option[ScopeSuccessor]): String =
    if (scope.exists(value => value != null && value.schemaVersion == committedScopeSchema)) committedCaptureSchema
    else if (scope.exists(value => value != null && value.schemaVersion == exclusionScopeSchema)) exclusionCaptureSchema
    else if (scope.nonEmpty) scopeCaptureSchema else schema

  private def _same_consumption(previous: Consumption, next: Consumption, exclusions: Vector[String]): Boolean =
    consumptionValid(previous) && consumptionValid(next) &&
      next.copy(expectedModes = previous.expectedModes, runtimeImage = previous.runtimeImage,
        manifestReference = previous.manifestReference, nonInputExclusions = previous.nonInputExclusions) == previous &&
      next.nonInputExclusions == exclusions && next.expectedModes.startsWith(previous.expectedModes)

  private def _scope_inventory_valid(direction: Direction, binding: D.Binding, approvals: Vector[D.Approval],
    consumption: Consumption, coverage: Vector[Coverage], exclusions: Vector[String],
    committed: Option[CommittedBaseSuccession] = None): Boolean = directionValid(direction) && D.bindingValid(binding) &&
    unique(approvals, 16)(D.approvalValid) && approvals.nonEmpty && consumptionValid(consumption) &&
    unique(coverage, 16)(coverageValid) && {
      val previous = direction.target.binding
      val inherited = direction.target.predecessor.approvals ++ direction.target.ancestors.flatMap(_.capture.admission.approvals)
      val paths = previous.paths ++ approvals.flatMap(_.proposal.paths).filterNot(previous.paths.contains).distinct
      binding.revision > previous.revision &&
        binding.copy(revision = previous.revision, paths = previous.paths,
          base = if (committed.nonEmpty) previous.base else binding.base) == previous && binding.paths == paths &&
        committed.forall(s => committedBaseValid(s) && s.parent == direction.directive &&
          s.previousBase == previous.base && s.nextBase == binding.base &&
          _partitions_match(s, paths, direction.target.proposal.excludedPaths,
            exclusions.filterNot(direction.target.proposal.excludedPaths.contains))) &&
        paths.toSet.intersect(exclusions.toSet).isEmpty &&
        approvals.forall(a => a.step == direction.target.step && a.proposal.requester == previous.requester &&
          Set(previous.dependency, direction.target.predecessor.binding.dependency)(a.proposal.dependency) &&
          inherited.map(_.proposal.usageContract).distinct == Vector(a.proposal.usageContract) &&
          !direction.target.proposal.coverage.exists(_.approval == a.decision)) &&
        _same_consumption(direction.target.proposal.consumption, consumption, exclusions) &&
        consumption.expectedModes.map(_.path) == paths &&
        coverage == direction.target.proposal.coverage ++ approvals.map(a =>
          Coverage(a.decision, a.proposal.acceptance, previous.checks.map(_.identity)))
    }

  private def _scope_boundary_valid(direction: Direction, value: ScopeSuccessor): Boolean =
    _scope_shape_valid(value) && directionValid(direction) && value.predecessor == direction.directive &&
      _scope_inventory_valid(direction, value.binding, value.approvals, value.consumption, value.coverage,
        exclusionsOf(direction, Some(value)), value.consumptionDirection.flatMap(_.target.committedBaseSuccession))

  /** Structural eligibility is not an independent direction or qualification receipt. */
  private def _runtime_target_successor_valid(original: Direction, target: Target, approvals: Vector[D.Approval]): Boolean =
    directionValid(original) && original.target.runtime.isEmpty && targetValid(target) &&
      approvals != null && approvals.forall(D.approvalValid) && target.runtime.exists { runtime =>
      val before = original.target
      val additional = _scope_approvals(original, approvals)
      runtime.parent == original.directive && runtime.approvals == additional &&
        target.sessionId == before.sessionId && target.plan == before.plan && target.step == before.step &&
        target.primaryBase == before.primaryBase && target.completed == before.completed &&
        target.predecessor == before.predecessor && target.ancestors == before.ancestors &&
        target.predecessorGuard == before.predecessorGuard &&
        (if (target.committedBaseSuccession.nonEmpty) target.proposal.mode == "committed"
          else target.proposal.mode == before.proposal.mode) &&
        (target.exclusionSuccession match {
          case Some(succession) => succession.parent == original.directive &&
            succession.previousExclusions == before.proposal.excludedPaths &&
            target.proposal.excludedPaths == succession.previousExclusions ++ succession.addedPaths
          case None => target.proposal.excludedPaths == before.proposal.excludedPaths
        }) &&
        target.generation > before.generation && BigInt(target.sourceRevision) > BigInt(before.pendingRevision) &&
        target.proposal.consumption.runtimeImage != before.proposal.consumption.runtimeImage &&
        target.proposal.consumption.manifestReference != before.proposal.consumption.manifestReference &&
        runtimeMatches(runtime.installation, target.proposal.consumption) &&
        _scope_inventory_valid(original, target.binding, additional, target.proposal.consumption, target.proposal.coverage,
          target.proposal.excludedPaths, target.committedBaseSuccession)
    }

  def runtimeSuccessorValid(original: Direction, next: Direction, approvals: Vector[D.Approval]): Boolean =
    directionValid(original) && directionValid(next) && next.directive != original.directive &&
      next.participant == original.participant && _runtime_target_successor_valid(original, next.target, approvals)

  def scopeValid(direction: Direction, value: ScopeSuccessor): Boolean = _scope_boundary_valid(direction, value) &&
    (value.consumptionDirection match {
      case Some(next) => runtimeSuccessorValid(direction, next, value.approvals) &&
        next.target.binding == value.binding && next.target.proposal.consumption == value.consumption &&
        next.target.proposal.coverage == value.coverage
      case None => value.consumption.copy(expectedModes = direction.target.proposal.consumption.expectedModes) ==
        direction.target.proposal.consumption
    })

  private def _uuid(value: String): Boolean = text(value) && scala.util.Try(UUID.fromString(value)).toOption.exists(_.toString == value)
  def runtimeSelectionValid(value: RuntimeSelection): Boolean = value != null && _uuid(value.installation) && _uuid(value.update) &&
    root(value.manifest) && Path.of(value.manifest).getParent != null &&
    Path.of(value.manifest).getParent.getFileName.toString == value.installation &&
    unique(value.modules, 256)(a => a != null && text(a.coordinate) && root(a.path) && root(a.source) &&
      Path.of(a.path).getParent == Path.of(value.manifest).getParent.resolve("runtime")) &&
    value.modules.nonEmpty && value.modules.map(_.coordinate).distinct.size == value.modules.size &&
    value.modules.map(_.path).distinct.size == value.modules.size &&
    value.modules.exists(_.coordinate == InstalledRuntimeDistribution.ROOT_COORDINATE) &&
    value.componentIdentity == InstalledRuntimeDistribution.COMPONENT_IDENTITY && value.component != null &&
    value.component.coordinate == InstalledRuntimeDistribution.COMPONENT_COORDINATE &&
    value.component.path == Path.of(value.manifest).getParent.resolve("components/textus-sm-workflow.car").toString &&
    root(value.component.source)

  def runtimeMatches(selection: RuntimeSelection, consumption: Consumption): Boolean =
    runtimeSelectionValid(selection) && consumptionValid(consumption) && consumption.runtimeImage == selection.installation &&
      consumption.manifestReference == ContextReference("cncf-installed-runtime:" + selection.installation, "1")

  private[admission] def installedSelectionC(core: ActionCall.Core): Consequence[RuntimeSelection] =
    RuntimeConfig.installedRuntimeDistributionC(core.executionContext.resolvedParameters).flatMap {
      case Some(value) =>
        val selected = RuntimeSelection(value.installationId.toString, value.updateId.toString, value.manifest.toString,
          value.modules.map(m => RuntimeArtifact(m.coordinate, m.path.toString, m.source.toString)),
          value.component.identity, RuntimeArtifact(value.component.coordinate, value.component.path.toString, value.component.source.toString))
        if (runtimeSelectionValid(selected)) Consequence.success(selected)
        else D.invalid("current installed runtime profile is unsupported for source consumption succession")
      case None => D.invalid("source consumption succession requires the actual current immutable installed runtime")
    }

  def runtimeC(core: ActionCall.Core, scope: Option[ScopeSuccessor]): Consequence[Unit] =
    scope.flatMap(_.consumptionDirection).flatMap(_.target.runtime) match {
      case None => Consequence.unit
      case Some(runtime) => installedSelectionC(core).flatMap { current =>
        if (current == runtime.installation && scope.exists(s => runtimeMatches(current, s.consumption))) Consequence.unit
        else D.invalid("source consumption differs from the actual admitted immutable runtime, manifest or module association")
      }
    }

  /** Only fresh capture selects current declarations; retained history does not. */
  def scopeC(direction: Direction, approvals: Vector[D.Approval], declaration: F.Declaration,
    consumptionDirection: Option[Direction] = None):
      Consequence[Option[ScopeSuccessor]] = {
    if (!directionValid(direction) || approvals == null || !approvals.forall(D.approvalValid) || consumptionDirection == null ||
        !consumptionDirection.forall(runtimeSuccessorValid(direction, _, approvals)))
      D.invalid("source scope requires actual valid native approvals")
    else {
      val additional = _scope_approvals(direction, approvals)
      if (additional.isEmpty) Consequence.success(None)
      else {
        val previous = direction.target.binding
        val paths = previous.paths ++ additional.flatMap(_.proposal.paths).filterNot(previous.paths.contains).distinct
        val bindings = declaration.dependencySourceRevisions.filter(b => b.revision > previous.revision &&
          b.paths == paths && b.copy(revision = previous.revision, paths = previous.paths,
            base = if (consumptionDirection.exists(_.target.committedBaseSuccession.nonEmpty)) previous.base else b.base) == previous &&
          consumptionDirection.forall(_.target.binding == b))
        val consumptions = declaration.dependencySourceConsumptions.filter(c =>
          c.expectedModes.map(_.path) == paths && c.expectedModes.startsWith(direction.target.proposal.consumption.expectedModes) &&
          (consumptionDirection match {
            case Some(next) => c == next.target.proposal.consumption
            case None => c.copy(expectedModes = direction.target.proposal.consumption.expectedModes) == direction.target.proposal.consumption
          }))
        (bindings, consumptions) match {
          case (Vector(binding), Vector(consumption)) =>
            val scope = ScopeSuccessor(_scope_schema(consumptionDirection),
              direction.directive, binding, additional, consumption,
              direction.target.proposal.coverage ++ additional.map(a =>
                Coverage(a.decision, a.proposal.acceptance, binding.checks.map(_.identity))), consumptionDirection)
            if (scopeValid(direction, scope)) Consequence.success(Some(scope))
            else D.invalid("source scope crosses its authentic dependency, Usage Contract or acceptance coverage")
          case _ => D.invalid("source scope configuration requires one exact declared inventory and consumption successor")
        }
      }
    }
  }

  def scopeAuthenticated(direction: Direction, scope: Option[ScopeSuccessor], approvals: Vector[D.Approval],
    consumptionDirection: Option[Direction] = None): Boolean =
    directionValid(direction) && scope != null && approvals != null && approvals.forall(D.approvalValid) &&
      consumptionDirection != null && scope.flatMap(_.consumptionDirection) == consumptionDirection &&
      scope.map(_.approvals).getOrElse(Vector.empty) == _scope_approvals(direction, approvals) &&
      scope.forall(scopeValid(direction, _))

  def bindingOf(direction: Direction, scope: Option[ScopeSuccessor]): D.Binding = scope.map(_.binding).getOrElse(direction.target.binding)
  def consumptionOf(direction: Direction, scope: Option[ScopeSuccessor]): Consumption =
    scope.map(_.consumption).getOrElse(direction.target.proposal.consumption)
  def exclusionsOf(direction: Direction, scope: Option[ScopeSuccessor]): Vector[String] =
    consumptionOf(direction, scope).nonInputExclusions
  def qualificationOf(direction: Direction, scope: Option[ScopeSuccessor]): Direction =
    scope.flatMap(_.consumptionDirection).getOrElse(direction)
  def modeOf(direction: Direction, scope: Option[ScopeSuccessor]): String =
    qualificationOf(direction, scope).target.proposal.mode
  def bindingOf(capture: Capture): D.Binding = bindingOf(capture.direction, capture.scope)
  def consumptionOf(capture: Capture): Consumption = consumptionOf(capture.direction, capture.scope)
  def modeOf(capture: Capture): String = modeOf(capture.direction, capture.scope)

  /** Existing framework admission, without a process or raw host JSON access. */
  def preflightC(core: ActionCall.Core, direction: Direction, scope: Option[ScopeSuccessor]): Consequence[Unit] = {
    val binding = bindingOf(direction, scope)
    val paths = if (modeOf(direction, scope) == "committed") Vector.empty else binding.paths
    paths.indices.foldLeft(runtimeC(core, scope)) { (prior, index) =>
      prior.flatMap { _ => for {
        workspace <- WorkspaceId.parseC(binding.dependency.workspace)
        selection <- GitSelectionId.parseC("goal-path-" + (index + 1))
        _ <- GitExecutionPreflight.validateC(core.executionContext, GitTarget(workspace), GitOperation.StageSelectedAll(selection))
      } yield () }
    }
  }

  final case class Capture(schemaVersion: String, sessionId: String, plan: ContextReference, step: String,
    generation: Long, sourceRevision: String, primaryBase: O.Snapshot, direction: Direction,
    postStageGuard: Guard, stagedPaths: Vector[String], preStageGuard: Guard, scope: Option[ScopeSuccessor] = None)
  object Capture {
    given Encoder[Capture] = deriveEncoder[Capture].mapJson(_.mapObject(_.filter {
      case ("scope", value) => !value.isNull
      case _ => true
    }))
    private val _decoder: Decoder[Capture] = closed(Set("schemaVersion", "sessionId", "plan", "step", "generation",
      "sourceRevision", "primaryBase", "direction", "postStageGuard", "stagedPaths", "preStageGuard", "scope"),
      deriveDecoder, captureValid, optional = Set("scope"))
    given Decoder[Capture] = Decoder.instance { cursor =>
      if (cursor.downField("scope").focus.contains(Json.Null))
        Left(DecodingFailure("scope must be absent or a complete versioned successor", cursor.history))
      else _decoder(cursor)
    }
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
  def captureBaseValid(direction: Direction, generation: Long, guard: Guard, scope: Option[ScopeSuccessor] = None): Boolean =
    directionValid(direction) && scope != null && scope.forall(scopeValid(direction, _)) &&
      guardModesValid(direction, guard, scope) && guard.selectedFiles.map(_.path) == bindingOf(direction, scope).paths && {
      scope.flatMap(_.consumptionDirection) match {
        case Some(_) =>
          val current = qualificationOf(direction, scope)
          val original = current.target.preStageGuard
          generation > current.target.generation &&
            guard.selectedFiles == original.selectedFiles &&
            (if (current.target.committedBaseSuccession.nonEmpty || generation == current.target.generation + 1) guard == original
              else guard.copy(indexTree = original.indexTree, dirty = original.dirty,
                selectedEntries = original.selectedEntries) == original)
        case None =>
          val original = direction.target.preStageGuard
          if (generation == direction.target.generation + 1) scope.isEmpty && guard == original
          else generation > direction.target.generation + 1 &&
            guard.copy(indexTree = original.indexTree, dirty = original.dirty,
              selectedFiles = original.selectedFiles, selectedEntries = original.selectedEntries) == original
      }
    }

  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else D.invalid("dependency source revision requires the independent Human evidence provider")
  }
  final case class Source(human: H.Source, predecessor: D.Admission, ancestors: Vector[Ancestor],
    original: Option[Direction] = None, approvals: Vector[D.Approval] = Vector.empty)

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
    _ <- if (source.original match {
      case None => human.result.result.value.dependencyAdmission.contains(source.predecessor)
      case Some(original) => directionValid(original) && original.target.runtime.isEmpty &&
        original.target.predecessor == source.predecessor && source.approvals.forall(D.approvalValid) &&
        _scope_approvals(original, source.approvals).nonEmpty && human.result.result.value.dependencyAdmission.isEmpty
    }) Consequence.unit else D.invalid("source revision predecessor is not the actual native semantic lineage")
    selected <- declaration.dependencySourceRevisions.find(b => b.identity == proposal.binding.identity &&
      b.revision.toString == proposal.binding.revision).map(Consequence.success)
      .getOrElse(D.invalid("dependency source binding is not independently declared by this host"))
    _ <- if (declaration.dependencySourceConsumptions.contains(proposal.consumption)) Consequence.unit
      else D.invalid("primary source/runtime consumption is not the admitted host declaration")
    inherited = (source.predecessor.acceptedPrefix.toVector.flatMap(_.paths) ++ source.predecessor.binding.paths).distinct
    old <- source.original.map(value => Consequence.success(value.target.predecessorGuard))
      .getOrElse(observeC(core, source.predecessor.binding.copy(paths = inherited), Vector.empty))
    fresh <- observeC(core, selected, proposal.excludedPaths)
    step <- human.input.stepIdentity.map(Consequence.success).getOrElse(D.invalid("source revision has no current Step"))
    runtime <- source.original match {
      case Some(original) => installedSelectionC(core).map[Option[RuntimeSuccession]](value => Some(
        RuntimeSuccession(original.directive, _scope_approvals(original, source.approvals), value)))
      case None => Consequence.success(Option.empty[RuntimeSuccession])
    }
    exclusions = source.original.filter(_.target.proposal.excludedPaths != proposal.excludedPaths).map(original =>
      ExclusionSuccession(exclusionSuccessionSchema, original.directive, original.target.proposal.excludedPaths,
        proposal.excludedPaths.drop(original.target.proposal.excludedPaths.size)))
    committed <- source.original match {
      case Some(original) if selected.base != original.target.binding.base =>
        if (proposal.mode != "committed") D.invalid("a new dependency base requires explicit committed source mode")
        else _committed_observation_c(core, selected, proposal.excludedPaths, original.directive,
          original.target.binding.base, original.target.proposal.excludedPaths,
          exclusions.toVector.flatMap(_.addedPaths)).flatMap { (guard, succession) =>
          if (guard == fresh) Consequence.success(Some(succession))
          else D.invalid("committed source changed during its target observation")
        }
      case _ => Consequence.success(Option.empty[CommittedBaseSuccession])
    }
    target = Target(if (committed.nonEmpty) committedSchema else if (exclusions.nonEmpty) exclusionSchema else if (runtime.nonEmpty) runtimeSchema else schema,
      human.session.current.identity.value, H.identity(human).value,
      human.from.revision.value.toString, pending.revision.value.toString, human.controller.plan, step,
      human.controller.generation, human.controller.base, human.controller.completed,
      GoalPhaseRepairProgress.snapshot(human.controller), human.result.resultReference,
      source.predecessor, source.ancestors, proposal, selected, old, fresh, runtime, exclusions, committed)
    _ <- if (targetValid(target) && source.original.forall(original =>
        _runtime_target_successor_valid(original, target, source.approvals))) Consequence.unit
      else D.invalid("source revision crosses inherited source, runtime or acceptance obligations")
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
        (if (target.runtime.nonEmpty) source.result.result.value.dependencyAdmission.isEmpty
          else source.result.result.value.dependencyAdmission.contains(target.predecessor)) &&
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
  def committedEntryValid(value: CommittedEntry): Boolean = value != null && path(value.path) && value.entry != null &&
    value.entry.forall(e => e != null && Set("100644", "100755")(e.mode) && oid(e.blob))
  def committedBaseValid(value: CommittedBaseSuccession): Boolean = value != null &&
    value.schemaVersion == committedBaseSuccessionSchema && referenceValid(value.parent) &&
    value.previousBase != null && value.nextBase != null &&
    Vector(value.previousBase.head, value.previousBase.tree, value.nextBase.head, value.nextBase.tree).forall(oid) &&
    value.previousBase.head != value.nextBase.head && value.sourceMode == "committed" &&
    unique(value.changes, 4096)(changeValid) && value.changes.map(_.path).distinct.size == value.changes.size &&
    Vector(value.phaseChangedPaths, value.previousExcludedChangedPaths, value.addedExcludedChangedPaths,
      value.baselineChangedPaths).forall(unique(_, 4096)(path)) && {
      val partitions = value.phaseChangedPaths ++ value.previousExcludedChangedPaths ++ value.addedExcludedChangedPaths ++ value.baselineChangedPaths
      partitions.distinct == partitions && partitions.toSet == value.changes.map(_.path).toSet &&
        Vector(value.phaseChangedPaths, value.previousExcludedChangedPaths, value.addedExcludedChangedPaths,
          value.baselineChangedPaths).forall(p => value.changes.map(_.path).filter(p.contains) == p)
    } && unique(value.committedEntries, 128)(committedEntryValid) && value.committedEntries.nonEmpty &&
    value.committedEntries.map(_.path).distinct.size == value.committedEntries.size

  private def _partitions_match(value: CommittedBaseSuccession, paths: Vector[String],
    previous: Vector[String], added: Vector[String]): Boolean = {
    val changed = value.changes.map(_.path)
    value.phaseChangedPaths == changed.filter(paths.contains) &&
      value.previousExcludedChangedPaths == changed.filter(previous.contains) &&
      value.addedExcludedChangedPaths == changed.filter(added.contains) &&
      value.baselineChangedPaths == changed.filterNot((paths ++ previous ++ added).contains)
  }

  def targetValid(value: Target): Boolean = value != null && value.runtime != null && value.exclusionSuccession != null &&
    value.committedBaseSuccession != null && value.proposal != null &&
    value.schemaVersion == (if (value.committedBaseSuccession.nonEmpty) committedSchema else if (value.exclusionSuccession.nonEmpty) exclusionSchema else if (value.runtime.nonEmpty) runtimeSchema else schema) &&
    value.exclusionSuccession.forall(s => _exclusion_succession_valid(s) &&
      value.runtime.exists(r => r != null && r.parent == s.parent) && value.proposal.excludedPaths == s.previousExclusions ++ s.addedPaths) &&
    value.runtime.forall(r => r != null && referenceValid(r.parent) && unique(r.approvals, 16)(D.approvalValid) &&
      r.approvals.nonEmpty && r.approvals.map(_.decision).distinct.size == r.approvals.size &&
      runtimeMatches(r.installation, value.proposal.consumption)) &&
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
    value.committedBaseSuccession.forall(s => committedBaseValid(s) && value.proposal.mode == "committed" &&
      value.runtime.exists(_.parent == s.parent) && s.nextBase == value.binding.base &&
      s.committedEntries.map(_.path) == value.binding.paths &&
      s.committedEntries.zip(value.proposal.consumption.expectedModes).forall((entry, mode) =>
        entry.path == mode.path && entry.entry.map(_.mode).getOrElse("000000") == mode.mode) &&
      s.committedEntries.zip(value.preStageGuard.selectedFiles).forall((entry, file) => entry.entry.nonEmpty == file.present) &&
      _partitions_match(s, value.binding.paths,
        value.exclusionSuccession.map(_.previousExclusions).getOrElse(value.proposal.excludedPaths),
        value.exclusionSuccession.toVector.flatMap(_.addedPaths))) &&
    coverageMatches(value)
  private def coverageMatches(value: Target): Boolean = {
    val approvals = (value.predecessor.approvals ++ value.ancestors.flatMap(_.capture.admission.approvals) ++
      value.runtime.toVector.flatMap(_.approvals))
      .groupBy(_.decision).values.map(_.head).toVector
    value.proposal.coverage.map(_.approval).toSet == approvals.map(_.decision).toSet &&
      value.proposal.coverage.forall(c => approvals.find(_.decision == c.approval).exists(a =>
        c.acceptance == a.proposal.acceptance && c.checks.forall(id => value.binding.checks.exists(_.identity == id)))) &&
      value.binding.checks.forall(check => value.proposal.coverage.exists(_.checks.contains(check.identity)))
  }
  def directionValid(value: Direction): Boolean = value != null && targetValid(value.target) &&
    referenceValid(value.directive) && text(value.participant) && text(value.rationale)
  def captureValid(value: Capture): Boolean = value != null && value.scope != null &&
    value.schemaVersion == _capture_schema(value.scope) &&
    value.scope.forall(scopeValid(value.direction, _)) &&
    directionValid(value.direction) && value.sessionId == value.direction.target.sessionId &&
    value.plan != null && value.plan == ContextReference(value.direction.target.plan.identity, value.direction.target.plan.revision) &&
    value.step == value.direction.target.step && value.generation > value.direction.target.generation &&
    revision(value.sourceRevision) && BigInt(value.sourceRevision) > BigInt(value.direction.target.pendingRevision) &&
    value.primaryBase == value.direction.target.primaryBase &&
    captureBaseValid(value.direction, value.generation, value.preStageGuard, value.scope) && guardModesValid(value.direction, value.postStageGuard, value.scope) &&
    value.stagedPaths != null && (if (modeOf(value) == "committed")
      value.stagedPaths.isEmpty && value.postStageGuard == value.preStageGuard &&
        !value.postStageGuard.dirty.exists(d => bindingOf(value).paths.contains(d.path))
      else value.stagedPaths == bindingOf(value).paths &&
        !value.postStageGuard.dirty.exists(d => bindingOf(value).paths.contains(d.path) && d.worktree != " ") &&
        value.postStageGuard.copy(indexTree = value.preStageGuard.indexTree,
          dirty = value.preStageGuard.dirty, selectedEntries = value.preStageGuard.selectedEntries) == value.preStageGuard)

  def candidateValid(value: GoalPhaseNativeCandidate.Payload): Boolean = value != null &&
    value.dependencySourceScope != null && value.dependencySourceDirection != null && value.dependencySourceGuard != null && value.dependencySourceCapture != null &&
    value.dependencySourceStages != null && value.dependencySourceStages.forall(stageFactValid) &&
    (value.dependencySourceDirection match {
      case None => value.dependencySourceScope.isEmpty && value.dependencySourceGuard.isEmpty && value.dependencySourceStages.isEmpty && value.dependencySourceCapture.isEmpty
      case Some(direction) => directionValid(direction) && value.dependencySourceScope.forall(scopeValid(direction, _)) &&
        (modeOf(direction, value.dependencySourceScope) != "committed" || value.dependencySourceStages.isEmpty) &&
        value.dependencySourceStages.forall(stageModeValid(direction, _, value.dependencySourceScope)) &&
        value.dependencyAdmission.isEmpty && value.dependency.isEmpty &&
        value.dependencyStaged.isEmpty && value.dependencyOrigin.isEmpty &&
        value.dependencySourceGuard.forall(guardModesValid(direction, _, value.dependencySourceScope)) &&
        value.dependencySourceStages.map(_.path) == bindingOf(direction, value.dependencySourceScope).paths.take(value.dependencySourceStages.size) &&
        value.dependencySourceStages.zipWithIndex.forall { (fact, index) =>
          (if (index == 0) value.dependencySourceGuard.contains(fact.before)
          else value.dependencySourceStages(index - 1).after == fact.before)
        } && value.dependencySourceCapture.forall(c => captureValid(c) && c.direction == direction && c.scope == value.dependencySourceScope &&
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
      val scope = current.payload.dependencySourceScope
      val binding = bindingOf(direction, scope)
      def observe: Consequence[Guard] = _qualified_observation_c(core, direction, scope)
      def git[A](op: GitOperation[A]): Consequence[A] = E.executeGitC(core, op, binding.dependency.workspace).flatMap(_.valueC)
      for {
        _ <- if (candidateValid(current.payload) && controller.cursor < controller.plan.steps.size &&
            controller.plan.steps(controller.cursor).identity == direction.target.step &&
            controller.base == direction.target.primaryBase && controller.plan == direction.target.plan &&
            current.record.identity.value == direction.target.sessionId) Consequence.unit
          else D.invalid("source capture crosses its native direction, Session, Plan or primary base")
        before <- observe
        _ <- if (captureBaseValid(direction, controller.generation, before, scope)) Consequence.unit
          else D.invalid("source capture differs from its adopted bytes or permitted ordinary repair")
        existing <- loadC("dependency-source-inspection")
        _ <- if (existing.isEmpty) Consequence.unit else D.invalid("source capture already has a retained inspection; do not replay")
        inspected <- saveC("dependency-source-inspection", current.record, current.payload.copy(dependencySourceGuard = Some(before)))
        staged <- (if (modeOf(direction, scope) == "committed") Vector.empty else binding.paths).zipWithIndex
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
            _ <- if (stageFactValid(fact) && stageModeValid(direction, fact, scope)) Consequence.unit
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
        capture = Capture(_capture_schema(scope), current.record.identity.value, ContextReference(controller.plan.identity, controller.plan.revision),
          binding.step, controller.generation, current.payload.sourceRevision, controller.base, direction, after,
          staged.payload.dependencySourceStages.map(_.path), before, scope)
        _ <- if (captureValid(capture) && after == staged.payload.dependencySourceStages.lastOption.map(_.after).getOrElse(before))
          Consequence.unit else D.invalid("source capture changed after its actual stage prefix")
        result <- saveC("dependency-source-observation-result", intent.record, staged.payload.copy(dependencySourceCapture = Some(capture)))
      } yield result
  }

  def guardModesValid(direction: Direction, guard: Guard, scope: Option[ScopeSuccessor] = None): Boolean =
    directionValid(direction) && scope != null && scope.forall(scopeValid(direction, _)) && guardValid(guard) &&
    guard.selectedFiles.map(_.path) == bindingOf(direction, scope).paths &&
    guard.excludedFiles.map(_.path) == exclusionsOf(direction, scope) &&
    guard.root == bindingOf(direction, scope).root && guard.branch == bindingOf(direction, scope).branch &&
    guard.head == bindingOf(direction, scope).base.head && guard.committedTree == bindingOf(direction, scope).base.tree &&
    guard.dirty.forall(p => (bindingOf(direction, scope).paths ++ exclusionsOf(direction, scope)).contains(p.path) &&
      p.renamedFrom.forall((bindingOf(direction, scope).paths ++ exclusionsOf(direction, scope)).contains)) &&
    guard.selectedEntries.forall(entry => consumptionOf(direction, scope).expectedModes.exists(mode =>
      mode.path == entry.path && mode.mode == entry.mode))

  def stageModeValid(direction: Direction, fact: StageFact, scope: Option[ScopeSuccessor] = None): Boolean =
    directionValid(direction) && stageFactValid(fact) &&
    guardModesValid(direction, fact.before, scope) && guardModesValid(direction, fact.after, scope) &&
    fact.changes.forall(change => consumptionOf(direction, scope).expectedModes.exists(m =>
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
      val paths = if (modeOf(d, initial.dependencySourceScope) == "committed") Vector.empty else bindingOf(d, initial.dependencySourceScope).paths
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
            captureBaseValid(d, target.controller.generation, g, initial.dependencySourceScope) && fact.payload == previous.copy(dependencySourceGuard = Some(g)))
          else if (fact.key.startsWith("dependency-source-stage-result-"))
            fact.payload.dependencySourceStages.lastOption.exists(stage => stageModeValid(d, stage, initial.dependencySourceScope) &&
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
      _ <- if (first.payload.dependencySourceDirection.contains(capture.direction) && first.payload.dependencySourceScope == capture.scope && first.payload.dependencySourceCapture.isEmpty &&
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
    else runtimeC(core, capture.scope).flatMap(_ => _qualified_observation_c(core, capture.direction, capture.scope)).flatMap(actual =>
      if (actual == capture.postStageGuard) Consequence.unit
      else D.invalid("dependency source bytes or excluded metadata drifted after capture"))

  def binding(legacy: Option[D.Capture], revised: Option[Capture]): Option[D.Binding] =
    if (legacy.nonEmpty && revised.nonEmpty) None
    else revised.filter(captureValid).map(bindingOf)
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

  private def _qualified_observation_c(core: ActionCall.Core, direction: Direction,
    scope: Option[ScopeSuccessor]): Consequence[Guard] = {
    val target = qualificationOf(direction, scope).target
    target.committedBaseSuccession match {
      case None => observeC(core, bindingOf(direction, scope), exclusionsOf(direction, scope))
      case Some(succession) => _committed_observation_c(core, bindingOf(direction, scope),
        exclusionsOf(direction, scope), succession.parent, succession.previousBase,
        target.exclusionSuccession.map(_.previousExclusions).getOrElse(target.proposal.excludedPaths),
        target.exclusionSuccession.toVector.flatMap(_.addedPaths)).flatMap { (guard, actual) =>
        if (actual == succession) Consequence.success(guard)
        else D.invalid("committed dependency base, complete selected entries or native delta drifted")
      }
    }
  }

  /** Observe the complete selected tree and compare actual blob bytes with managed
    * file bytes. FileIdentity remains legacy metadata, never a substitute for reads.
    */
  private def _committed_observation_c(core: ActionCall.Core, binding: D.Binding,
    excluded: Vector[String], parent: ContextReference, previous: O.Snapshot,
    previousexclusions: Vector[String], addedexclusions: Vector[String]): Consequence[(Guard, CommittedBaseSuccession)] = {
    def _git_[A](operation: GitOperation[A]): Consequence[A] =
      E.executeGitC(core, operation, binding.dependency.workspace).flatMap(_.valueC)
    def _native_ : Consequence[(GitCommitMetadata, GitCommitMetadata, Vector[GitTreeChange], Vector[GitSelectedTreeEntry])] = for {
      old <- GitObjectId.parseC(previous.head)
      next <- GitObjectId.parseC(binding.base.head)
      oldmetadata <- _git_(GitOperation.CommitMetadata(old))
      newmetadata <- _git_(GitOperation.CommitMetadata(next))
      _ <- if (oldmetadata.commit == old && oldmetadata.tree.value == previous.tree &&
          newmetadata.commit == next && newmetadata.tree.value == binding.base.tree) Consequence.unit
        else D.invalid("committed dependency base is not its exact native commit/tree pair")
      delta <- _git_(GitOperation.TreeChanges(oldmetadata.tree, newmetadata.tree))
      selection <- GitSelectionId.parseC(committedInventorySelection)
      entries <- _git_(GitOperation.TreeEntries(newmetadata.tree, selection))
      _ <- if (entries != null && entries.map(_.path.value) == binding.paths &&
          entries.forall(e => committedEntryValid(CommittedEntry(e.path.value,
            e.entry.map(v => CommittedObject(v.mode, v.blob.value)))))) Consequence.unit
        else D.invalid("committed tree entries differ from the exact finite source selection")
    } yield (oldmetadata, newmetadata, delta, entries)
    def _bytes_(entries: Vector[GitSelectedTreeEntry]): Consequence[Vector[FileIdentity]] = for {
      workspace <- WorkspaceId.parseC(binding.dependency.workspace)
      files <- entries.foldLeft(Consequence.success(Vector.empty[FileIdentity])) { (prior, entry) => for {
        result <- prior
        relative <- WorkspaceFilePath.parseC(entry.path.value)
        observed <- WorkflowFileExecution.readBytesC(core, FileTarget(workspace), relative, FileRepresentation.Identity)
        _ <- if (observed != null && observed.target == FileTarget(workspace) && observed.path == relative &&
            observed.representation == FileRepresentation.Identity && observed.bytes != null) Consequence.unit
          else D.invalid("committed source managed byte read crossed its admitted target")
        committed <- entry.entry match {
          case None => Consequence.success(Option.empty[Vector[Byte]])
          case Some(value) => _git_(GitOperation.BlobContent(value.blob)).map(blob => Some(blob.bytes))
        }
        _ <- if (committed == observed.bytes) Consequence.unit
          else D.invalid("committed source blob bytes or absence differ from actual managed files")
      } yield result :+ fileIdentity(entry.path.value, observed.bytes) }
    } yield files
    for {
      guard <- observeC(core, binding, excluded)
      before <- _native_
      files <- _bytes_(before._4)
      middle <- observeC(core, binding, excluded)
      repeated <- _native_
      filesagain <- _bytes_(repeated._4)
      after <- observeC(core, binding, excluded)
      finalnative <- _native_
      _ <- if (guard == middle && middle == after && before == repeated && repeated == finalnative &&
          files == guard.selectedFiles && filesagain == files &&
          !guard.dirty.exists(d => binding.paths.contains(d.path))) Consequence.unit
        else D.invalid("committed source bytes, modes, base or native guards changed during observation")
      changes = before._3.map(change)
      paths = changes.map(_.path)
      succession = CommittedBaseSuccession(committedBaseSuccessionSchema, parent, previous, binding.base, changes,
        paths.filter(binding.paths.contains), paths.filter(previousexclusions.contains), paths.filter(addedexclusions.contains),
        paths.filterNot((binding.paths ++ previousexclusions ++ addedexclusions).contains), "committed",
        before._4.map(e => CommittedEntry(e.path.value, e.entry.map(v => CommittedObject(v.mode, v.blob.value)))))
      _ <- if (committedBaseValid(succession)) Consequence.unit
        else D.invalid("committed source succession has an unsupported delta or incomplete partition")
    } yield (guard, succession)
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
