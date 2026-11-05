package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import org.goldenport.cncf.cli.InstalledRuntimeDistribution
import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
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
  *  version Oct. 28, 2026
  *  version Nov.  2, 2026
  * @version Nov.  5, 2026
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
  val sourceSchema = "sm-workflow.goal-phase-dependency-source-revision.v5"
  val sourceScopeSchema = "sm-workflow.goal-phase-dependency-source-scope.v5"
  val sourceCaptureSchema = "sm-workflow.goal-phase-dependency-source-capture.v5"
  val waitSchema = "sm-workflow.goal-phase-dependency-source-revision.v6"
  val waitCaptureSchema = "sm-workflow.goal-phase-dependency-source-capture.v6"
  val primarySchema = "sm-workflow.goal-phase-dependency-source-revision.v7"
  val primaryCaptureSchema = "sm-workflow.goal-phase-dependency-source-capture.v7"
  val initialSchema = "sm-workflow.goal-phase-dependency-source-revision.v8"
  val initialCaptureSchema = "sm-workflow.goal-phase-dependency-source-capture.v8"
  val initialChoice = "AdoptInitialDependencySourceRevisionAcceptingUnknownEffects"
  val initialGuardUnavailable = "not-recorded-before-first-dependency-capture"
  val combinedPrimaryChoice = "AdoptDependencySourceRevisionAndContinuePublicationWithUnknownEffects"
  val consumptionSchema = "sm-workflow.goal-phase-dependency-source-consumption.v2"
  val committedBaseSuccessionSchema = "sm-workflow.goal-phase-committed-base-succession.v1"
  val committedInventorySelection = "goal-source-committed-inventory"
  val origin = "dependency-source-revision"
  val choices = Vector("AdoptDependencySourceRevision", "Defer")
  def choicesFor(target: Target): Vector[String] = if (target.initialOrigin.nonEmpty) Vector(initialChoice, "Defer")
    else if (target.primaryFailureOrigin.nonEmpty)
    Vector("AdoptDependencySourceRevisionWithUnknownEffects", combinedPrimaryChoice, "Defer")
    else if (target.waitOrigin.isEmpty) choices else
    Vector("AdoptDependencySourceRevision").filter(_ => target.waitOrigin.exists(_.observation.exists(o =>
      GoalPhasePrerequisitePublicationUnknownRecovery.lifecycleValid(o.lifecycle)))) ++
      Vector("AdoptDependencySourceRevisionWithUnknownEffects", "Defer")
  val message = "Select the exact new dependency source for fresh qualification in the same Session. " +
    "Retain the original primary base, accepted Steps, predecessor admissions, approvals and repair history. " +
    "Direction is not acceptance and runs no check, staging, commit, Submit or Advance."
  def messageFor(target: Target): String = if (target.initialOrigin.isEmpty) message else
    "Explicitly select the CURRENT observed primary candidate together with this exact successor dependency source, " +
      "accepting unresolved prior effects. No predecessor dependency Guard was recorded. Completed primary effect receipts " +
      "do not prove historical byte equality or validation acceptance. The old outcome stays UNKNOWN/termination-unconfirmed. " + message

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
  sealed trait ConsumptionRecord {
    def primaryWorkspace: String
    def sourceRoot: String
    def nonInputExclusions: Vector[String]
    def expectedModes: Vector[ExpectedMode]
    def programs: Vector[CheckProgram]
  }
  object ConsumptionRecord {
    given Encoder[ConsumptionRecord] = Encoder.instance {
      case value: Consumption => value.asJson
      case value: LegacyConsumption => value.asJson
    }
    given Decoder[ConsumptionRecord] = Decoder.instance { cursor =>
      if (cursor.downField("schemaVersion").focus.nonEmpty) cursor.as[Consumption]
      else cursor.as[LegacyConsumption]
    }
  }
  final case class Consumption(primaryWorkspace: String, sourceRoot: String,
    nonInputExclusions: Vector[String], expectedModes: Vector[ExpectedMode],
    programs: Vector[CheckProgram]) extends ConsumptionRecord
  object Consumption {
    given Encoder[Consumption] = deriveEncoder[Consumption].mapJson(_.mapObject(_.add("schemaVersion", consumptionSchema.asJson)))
    private val _decoder: Decoder[Consumption] = closed(Set("primaryWorkspace", "sourceRoot",
      "nonInputExclusions", "expectedModes", "programs"), deriveDecoder, consumptionValid)
    given Decoder[Consumption] = Decoder.instance { cursor =>
      cursor.get[String]("schemaVersion").flatMap { version =>
        if (version == consumptionSchema) _decoder.decodeJson(cursor.value.mapObject(_.remove("schemaVersion")))
        else Left(DecodingFailure("unknown source consumption schema", cursor.history))
      }
    }
  }
  final case class LegacyConsumption(primaryWorkspace: String, sourceRoot: String,
    runtimeImage: String, manifestReference: ContextReference, nonInputExclusions: Vector[String],
    expectedModes: Vector[ExpectedMode], programs: Vector[CheckProgram]) extends ConsumptionRecord
  object LegacyConsumption {
    given Encoder[LegacyConsumption] = deriveEncoder
    given Decoder[LegacyConsumption] = closed(Set("primaryWorkspace", "sourceRoot", "runtimeImage", "manifestReference",
      "nonInputExclusions", "expectedModes", "programs"),
      deriveDecoder, consumptionValid)
  }
  final case class Proposal(schemaVersion: String, binding: ContextReference, mode: String,
    excludedPaths: Vector[String], coverage: Vector[Coverage], consumption: ConsumptionRecord)
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
  final case class SourceSuccession(parent: ContextReference, approvals: Vector[D.Approval], previousDirection: ContextReference)
  object SourceSuccession {
    import D.given
    given Encoder[SourceSuccession] = deriveEncoder
    given Decoder[SourceSuccession] = closed(Set("parent", "approvals", "previousDirection"), deriveDecoder,
      value => value != null && referenceValid(value.parent) && referenceValid(value.previousDirection) &&
        unique(value.approvals, 16)(D.approvalValid) &&
        value.approvals.map(_.decision).distinct.size == value.approvals.size)
  }
  final case class Target(schemaVersion: String, sessionId: String, questionIdentity: String,
    sourceRevision: String, pendingRevision: String, plan: O.Plan, step: String, generation: Long,
    primaryBase: O.Snapshot, completed: Vector[O.ClosedStep], repairProgress: GoalPhaseRepairProgress.Snapshot,
    requestingResult: ContextReference, predecessor: D.Admission, ancestors: Vector[Ancestor],
    proposal: Proposal, binding: D.Binding, predecessorGuard: Option[Guard], preStageGuard: Guard,
    runtime: Option[RuntimeSuccession] = None, exclusionSuccession: Option[ExclusionSuccession] = None,
    committedBaseSuccession: Option[CommittedBaseSuccession] = None, sourceSuccession: Option[SourceSuccession] = None,
    waitOrigin: Option[GoalPhasePrerequisitePublicationUnknownRecovery.Target] = None,
    primaryFailureOrigin: Option[GoalPhasePrerequisitePublicationRecovery.PrimaryTarget] = None,
    initialOrigin: Option[InitialOrigin] = None)

  /** Native traversal supplies this relation from the decoded ReviseWork Direction.
    * It is historical effect provenance, not historical primary byte evidence. */
  final case class InitialLineage(recovery: GoalPhaseCandidateRecovery.RecoveryTarget,
    continuation: Option[GoalPhaseCandidateObservationContinuation.Target],
    revisionDirection: ContextReference, revisionRecord: ContextReference)
  object InitialLineage {
    given Encoder[InitialLineage] = deriveEncoder[InitialLineage].mapJson(_.mapObject(_.filter {
      case ("continuation", value) => !value.isNull
      case _ => true
    }))
    private val _decoder: Decoder[InitialLineage] = closed(Set("recovery", "continuation",
      "revisionDirection", "revisionRecord"), deriveDecoder, initialLineageValid, optional = Set("continuation"))
    given Decoder[InitialLineage] = Decoder.instance(c =>
      if (c.downField("continuation").focus.contains(Json.Null)) Left(DecodingFailure("initial continuation cannot be null", c.history))
      else _decoder(c))
  }
  final case class CurrentPrimaryObservation(observed: String,
    repository: GoalPhaseCandidateObservationContinuation.RepositorySnapshot,
    selectedPaths: Vector[String], ownedPaths: Vector[String], candidatePaths: Vector[String],
    stagedChanges: Vector[StageChange], diff: String, files: Vector[FileIdentity],
    historySelection: WorkflowSourceHistoryRule.Selection, completedEffects: Vector[ContextReference], processPolicy: String)
  object CurrentPrimaryObservation {
    given Encoder[CurrentPrimaryObservation] = Encoder.instance(value => Json.obj(
      "observed" -> value.observed.asJson,
      "repository" -> value.repository.asJson,
      "selectedPaths" -> value.selectedPaths.asJson,
      "ownedPaths" -> value.ownedPaths.asJson,
      "candidatePaths" -> value.candidatePaths.asJson,
      "stagedChanges" -> value.stagedChanges.asJson,
      "diff" -> value.diff.asJson,
      "files" -> value.files.asJson,
      "historySelection" -> value.historySelection.asJson,
      "completedEffects" -> value.completedEffects.asJson,
      "processPolicy" -> value.processPolicy.asJson))
    private val _decoder: Decoder[CurrentPrimaryObservation] = closed(Set("observed", "repository", "selectedPaths", "ownedPaths",
      "candidatePaths", "stagedChanges", "diff", "files", "historySelection", "completedEffects", "processPolicy"),
      Decoder.instance(c => for {
        observed <- c.get[String]("observed")
        repository <- c.get[GoalPhaseCandidateObservationContinuation.RepositorySnapshot]("repository")
        selectedpaths <- c.get[Vector[String]]("selectedPaths")
        ownedpaths <- c.get[Vector[String]]("ownedPaths")
        candidatepaths <- c.get[Vector[String]]("candidatePaths")
        stagedchanges <- c.get[Vector[StageChange]]("stagedChanges")
        diff <- c.get[String]("diff")
        files <- c.get[Vector[FileIdentity]]("files")
        historyselection <- c.get[WorkflowSourceHistoryRule.Selection]("historySelection")
        completedeffects <- c.get[Vector[ContextReference]]("completedEffects")
        processpolicy <- c.get[String]("processPolicy")
      } yield CurrentPrimaryObservation(observed, repository, selectedpaths, ownedpaths, candidatepaths,
        stagedchanges, diff, files, historyselection, completedeffects, processpolicy)), currentPrimaryValid)
    given Decoder[CurrentPrimaryObservation] = Decoder.instance(c => _decoder(c).flatMap(value =>
      if (value.asJson == c.value) Right(value) else Left(DecodingFailure("current primary observation is noncanonical", c.history))))
  }
  final case class InitialOrigin(predecessorGuardAvailability: String, lineage: InitialLineage,
    currentPrimary: CurrentPrimaryObservation, baseLineage: Vector[O.HistoryCommit], baseChanges: Vector[StageChange])
  object InitialOrigin {
    given Encoder[InitialOrigin] = deriveEncoder
    private val _decoder: Decoder[InitialOrigin] = closed(Set("predecessorGuardAvailability", "lineage", "currentPrimary",
      "baseLineage", "baseChanges"), deriveDecoder, initialOriginValid)
    given Decoder[InitialOrigin] = Decoder.instance(c => _decoder(c).flatMap(value =>
      if (value.asJson == c.value) Right(value) else Left(DecodingFailure("initial origin is noncanonical", c.history))))
  }
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
      case ("runtime" | "exclusionSuccession" | "committedBaseSuccession" | "sourceSuccession" | "waitOrigin" | "primaryFailureOrigin" | "initialOrigin" | "predecessorGuard", value) => !value.isNull
      case _ => true
    }))
    private val _decoder: Decoder[Target] = closed(Set("schemaVersion", "sessionId", "questionIdentity", "sourceRevision",
      "pendingRevision", "plan", "step", "generation", "primaryBase", "completed", "repairProgress",
      "requestingResult", "predecessor", "ancestors", "proposal", "binding", "predecessorGuard", "preStageGuard", "runtime", "exclusionSuccession", "committedBaseSuccession", "sourceSuccession", "waitOrigin", "primaryFailureOrigin", "initialOrigin"),
      deriveDecoder, targetValid, optional = Set("predecessorGuard", "runtime", "exclusionSuccession", "committedBaseSuccession", "sourceSuccession", "waitOrigin", "primaryFailureOrigin", "initialOrigin"))
    given Decoder[Target] = Decoder.instance { cursor =>
      if (Vector("predecessorGuard", "runtime", "exclusionSuccession", "committedBaseSuccession", "sourceSuccession", "waitOrigin", "primaryFailureOrigin", "initialOrigin").exists(key => cursor.downField(key).focus.contains(Json.Null)))
        Left(DecodingFailure("runtime must be absent or a closed installed consumption succession", cursor.history))
      else _decoder(cursor)
    }
  }
  final case class Direction(target: Target, directive: ContextReference, participant: String, rationale: String,
    choice: Option[String] = None)
  object Direction {
    given Encoder[Direction] = deriveEncoder[Direction].mapJson(_.mapObject(_.filter {
      case ("choice", value) => !value.isNull
      case _ => true
    }))
    private val _decoder: Decoder[Direction] = closed(Set("target", "directive", "participant", "rationale", "choice"),
      deriveDecoder, directionValid, optional = Set("choice"))
    given Decoder[Direction] = Decoder.instance { cursor =>
      if (cursor.downField("choice").focus.contains(Json.Null))
        Left(DecodingFailure("choice must be absent or an explicit Wait uncertainty direction", cursor.history))
      else _decoder(cursor)
    }
  }
  val scopeSchema = "sm-workflow.goal-phase-dependency-source-scope.v1"
  val scopeCaptureSchema = "sm-workflow.goal-phase-dependency-source-capture.v2"

  /** Structural data is not authority; native traversal authenticates its decisions. */
  final case class ScopeSuccessor(schemaVersion: String, predecessor: ContextReference,
    binding: D.Binding, approvals: Vector[D.Approval], consumption: ConsumptionRecord, coverage: Vector[Coverage],
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
    value.consumptionDirection.forall(d => directionValid(d) && isSuccessor(d.target)) &&
    value.schemaVersion == _scope_schema(value.consumptionDirection, value.consumption) &&
    referenceValid(value.predecessor) && D.bindingValid(value.binding) &&
    unique(value.approvals, 16)(D.approvalValid) &&
    (value.approvals.nonEmpty || _source_scope_direction(value.consumptionDirection)) &&
    value.approvals.map(_.decision).distinct.size == value.approvals.size && consumptionValid(value.consumption) &&
    unique(value.coverage, 16)(coverageValid) && value.coverage.map(_.approval).distinct.size == value.coverage.size

  private def _source_scope_direction(direction: Option[Direction]): Boolean =
    direction.exists(d => directionValid(d) && d.target.schemaVersion == sourceSchema && d.target.sourceSuccession.nonEmpty)

  private def _scope_approvals(direction: Direction, approvals: Vector[D.Approval]): Vector[D.Approval] =
    approvals.filter(a => a.step == direction.target.step &&
      a.proposal.dependency.identity == direction.target.binding.dependency.identity &&
      a.proposal.dependency.repository == direction.target.binding.dependency.repository &&
      !direction.target.proposal.coverage.exists(_.approval == a.decision))

  private def _scope_schema(direction: Option[Direction], consumption: ConsumptionRecord): String =
    if (consumption.isInstanceOf[Consumption]) sourceScopeSchema
    else if (direction.exists(_.target.committedBaseSuccession.nonEmpty)) committedScopeSchema
    else if (direction.exists(_.target.exclusionSuccession.nonEmpty)) exclusionScopeSchema
    else if (direction.nonEmpty) runtimeScopeSchema else scopeSchema

  private def _capture_schema(direction: Direction, scope: Option[ScopeSuccessor]): String =
    if (direction != null && direction.target != null && direction.target.schemaVersion == initialSchema) initialCaptureSchema
    else if (direction != null && direction.target != null && direction.target.schemaVersion == primarySchema) primaryCaptureSchema
    else if (direction != null && direction.target != null && direction.target.schemaVersion == waitSchema) waitCaptureSchema
    else if (direction != null && direction.target != null && (direction.target.schemaVersion == sourceSchema ||
        scope.exists(value => value != null && value.schemaVersion == sourceScopeSchema))) sourceCaptureSchema
    else if (scope.exists(value => value != null && value.schemaVersion == committedScopeSchema)) committedCaptureSchema
    else if (scope.exists(value => value != null && value.schemaVersion == exclusionScopeSchema)) exclusionCaptureSchema
    else if (scope.nonEmpty) scopeCaptureSchema else schema

  private def _with_modes(value: ConsumptionRecord, modes: Vector[ExpectedMode]): ConsumptionRecord = value match {
    case active: Consumption => active.copy(expectedModes = modes)
    case legacy: LegacyConsumption => legacy.copy(expectedModes = modes)
  }

  private def _same_consumption(previous: ConsumptionRecord, next: ConsumptionRecord, exclusions: Vector[String]): Boolean =
    consumptionValid(previous) && consumptionValid(next) &&
      next.primaryWorkspace == previous.primaryWorkspace && next.sourceRoot == previous.sourceRoot &&
      next.programs == previous.programs &&
      next.nonInputExclusions == exclusions && next.expectedModes.startsWith(previous.expectedModes)

  private def _scope_inventory_valid(direction: Direction, binding: D.Binding, approvals: Vector[D.Approval],
    consumption: ConsumptionRecord, coverage: Vector[Coverage], exclusions: Vector[String],
    committed: Option[CommittedBaseSuccession] = None, allowemptyapprovals: Boolean = false): Boolean =
    directionValid(direction) && D.bindingValid(binding) &&
    unique(approvals, 16)(D.approvalValid) && (approvals.nonEmpty || allowemptyapprovals) && consumptionValid(consumption) &&
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
        exclusionsOf(direction, Some(value)), value.consumptionDirection.flatMap(_.target.committedBaseSuccession),
        allowemptyapprovals = _source_scope_direction(value.consumptionDirection))

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
        ((before.proposal.consumption, target.proposal.consumption) match {
          case (old: LegacyConsumption, next: LegacyConsumption) =>
            next.runtimeImage != old.runtimeImage && next.manifestReference != old.manifestReference
          case _ => false
        }) &&
        runtimeMatches(runtime.installation, target.proposal.consumption) &&
        _scope_inventory_valid(original, target.binding, additional, target.proposal.consumption, target.proposal.coverage,
          target.proposal.excludedPaths, target.committedBaseSuccession)
    }

  def runtimeSuccessorValid(original: Direction, next: Direction, approvals: Vector[D.Approval]): Boolean =
    directionValid(original) && directionValid(next) && next.directive != original.directive &&
      next.participant == original.participant && _runtime_target_successor_valid(original, next.target, approvals)

  def isSuccessor(target: Target): Boolean = target.runtime.nonEmpty || target.sourceSuccession.nonEmpty

  private def _source_target_successor_valid(original: Direction, target: Target, approvals: Vector[D.Approval]): Boolean =
    directionValid(original) && original.target.initialOrigin.isEmpty && !isSuccessor(original.target) && targetValid(target) &&
      approvals != null && approvals.forall(D.approvalValid) && target.sourceSuccession.exists { succession =>
        val before = original.target
        val additional = _scope_approvals(original, approvals)
        succession.parent == original.directive && succession.approvals == additional &&
          target.sessionId == before.sessionId && target.plan == before.plan && target.step == before.step &&
          target.primaryBase == before.primaryBase && target.completed == before.completed &&
          target.predecessor == before.predecessor && target.ancestors == before.ancestors &&
          target.predecessorGuard == before.predecessorGuard &&
          (if (target.committedBaseSuccession.nonEmpty) target.proposal.mode == "committed"
            else target.proposal.mode == before.proposal.mode) &&
          (target.exclusionSuccession match {
            case Some(value) => value.parent == original.directive && value.previousExclusions == before.proposal.excludedPaths &&
              target.proposal.excludedPaths == value.previousExclusions ++ value.addedPaths
            case None => target.proposal.excludedPaths == before.proposal.excludedPaths
          }) && target.generation > before.generation && BigInt(target.sourceRevision) > BigInt(before.pendingRevision) &&
          _scope_inventory_valid(original, target.binding, additional, target.proposal.consumption,
            target.proposal.coverage, target.proposal.excludedPaths, target.committedBaseSuccession, allowemptyapprovals = true)
      }

  def sourceSuccessorValid(original: Direction, next: Direction, approvals: Vector[D.Approval]): Boolean =
    directionValid(original) && directionValid(next) && next.directive != original.directive &&
      next.target.sourceSuccession.forall(_.previousDirection != next.directive) &&
      next.participant == original.participant && _source_target_successor_valid(original, next.target, approvals)

  /** The caller supplies the actual traversed predecessor, never a declaration's assertion. */
  def sourcePreviousValid(original: Direction, next: Target, previous: Option[Direction]): Boolean = {
    val actual = previous.getOrElse(original)
    directionValid(actual) && next.sourceSuccession.exists(_.previousDirection == actual.directive) &&
      next.generation > actual.target.generation && BigInt(next.sourceRevision) > BigInt(actual.target.pendingRevision) &&
      (actual.target.proposal.mode != "committed" || next.proposal.mode == "committed") &&
      next.binding.revision > actual.target.binding.revision && next.binding.paths.startsWith(actual.target.binding.paths) &&
      next.proposal.coverage.startsWith(actual.target.proposal.coverage) &&
      next.proposal.excludedPaths.startsWith(actual.target.proposal.excludedPaths) &&
      _same_consumption(actual.target.proposal.consumption, next.proposal.consumption, next.proposal.excludedPaths)
  }

  def successorValid(original: Direction, next: Direction, approvals: Vector[D.Approval]): Boolean =
    if (next != null && next.target != null && next.target.sourceSuccession.nonEmpty)
      sourceSuccessorValid(original, next, approvals)
    else runtimeSuccessorValid(original, next, approvals)

  def scopeValid(direction: Direction, value: ScopeSuccessor): Boolean = _scope_boundary_valid(direction, value) &&
    (value.consumptionDirection match {
      case Some(next) => successorValid(direction, next, value.approvals) &&
        next.target.binding == value.binding && next.target.proposal.consumption == value.consumption &&
        next.target.proposal.coverage == value.coverage
      case None => _with_modes(value.consumption, direction.target.proposal.consumption.expectedModes) ==
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

  def runtimeMatches(selection: RuntimeSelection, consumption: ConsumptionRecord): Boolean = consumption match {
    case legacy: LegacyConsumption => runtimeSelectionValid(selection) && consumptionValid(legacy) &&
      legacy.runtimeImage == selection.installation &&
      legacy.manifestReference == ContextReference("cncf-installed-runtime:" + selection.installation, "1")
    case _ => false
  }

  def consumptionPreflightC(direction: Direction, scope: Option[ScopeSuccessor]): Consequence[Unit] =
    if (!directionValid(direction) || scope == null || !scope.forall(scopeValid(direction, _)))
      D.invalid("source consumption requires a valid effective direction and scope")
    else consumptionOf(direction, scope) match {
      case _: Consumption => Consequence.unit
      case _: LegacyConsumption => D.invalid("legacy image-based source consumption requires migration through existing describe/adopt source revision operations")
    }

  /** Only fresh capture selects current declarations; retained history does not. */
  def scopeC(direction: Direction, approvals: Vector[D.Approval], declaration: F.Declaration,
    consumptionDirection: Option[Direction] = None):
      Consequence[Option[ScopeSuccessor]] = {
    if (!directionValid(direction) || approvals == null || !approvals.forall(D.approvalValid) || consumptionDirection == null ||
        !consumptionDirection.forall(successorValid(direction, _, approvals)))
      D.invalid("source scope requires actual valid native approvals")
    else {
      val additional = _scope_approvals(direction, approvals)
      if (additional.isEmpty && consumptionDirection.isEmpty) Consequence.success(None)
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
            case None => _with_modes(c, direction.target.proposal.consumption.expectedModes) == direction.target.proposal.consumption
          }))
        (bindings, consumptions) match {
          case (Vector(binding), Vector(consumption)) =>
            val scope = ScopeSuccessor(_scope_schema(consumptionDirection, consumption),
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
  def consumptionOf(direction: Direction, scope: Option[ScopeSuccessor]): ConsumptionRecord =
    scope.map(_.consumption).getOrElse(direction.target.proposal.consumption)
  def exclusionsOf(direction: Direction, scope: Option[ScopeSuccessor]): Vector[String] =
    consumptionOf(direction, scope).nonInputExclusions
  def qualificationOf(direction: Direction, scope: Option[ScopeSuccessor]): Direction =
    scope.flatMap(_.consumptionDirection).getOrElse(direction)
  def modeOf(direction: Direction, scope: Option[ScopeSuccessor]): String =
    qualificationOf(direction, scope).target.proposal.mode
  def bindingOf(capture: Capture): D.Binding = bindingOf(capture.direction, capture.scope)
  def consumptionOf(capture: Capture): ConsumptionRecord = consumptionOf(capture.direction, capture.scope)
  def modeOf(capture: Capture): String = modeOf(capture.direction, capture.scope)

  /** Existing framework admission, without a process or raw host JSON access. */
  def preflightC(core: ActionCall.Core, direction: Direction, scope: Option[ScopeSuccessor]): Consequence[Unit] = {
    val binding = bindingOf(direction, scope)
    val paths = stagingPaths(direction, scope, direction.target.preStageGuard)
    paths.foldLeft(consumptionPreflightC(direction, scope)) { (prior, path) =>
      prior.flatMap { _ => for {
        workspace <- WorkspaceId.parseC(binding.dependency.workspace)
        selection <- GitSelectionId.parseC("goal-path-" + (binding.paths.indexOf(path) + 1))
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
  def stagingPaths(direction: Direction, scope: Option[ScopeSuccessor], guard: Guard): Vector[String] =
    if (modeOf(direction, scope) == "committed") Vector.empty
    else if (Set(waitSchema, primarySchema)(direction.target.schemaVersion)) bindingOf(direction, scope).paths.filter(path =>
      guard.dirty.exists(d => d.path == path && d.worktree != " "))
    else bindingOf(direction, scope).paths

  private[admission] def waitCommittedObservationC(core: ActionCall.Core, binding: D.Binding,
    exclusions: Vector[String], parent: ContextReference, previous: O.Snapshot): Consequence[CommittedBaseSuccession] =
    _committed_observation_c(core, binding, exclusions, parent, previous, exclusions, Vector.empty).map(_._2)
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
    original: Option[Direction] = None, approvals: Vector[D.Approval] = Vector.empty,
    previousDirection: Option[Direction] = None, initialLineage: Option[InitialLineage] = None)

  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def targetC(value: Record): Consequence[Target] = recordC[Target](value)
  def proposalC(value: Record): Consequence[Proposal] = recordC[Proposal](value)
  private def recordC[A: Decoder](value: Record): Consequence[A] =
    if (value == null) D.invalid("dependency source revision record is absent")
    else value.toJsonStringC.flatMap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(GoalPhaseNativeMetadata.normalizePublicJson).flatMap(_initial_public_target)
      .flatMap(_.as[A].left.map(_.message))
      .fold(D.invalid(_), Consequence.success))

  /** Public Record omission is interpreted by each existing fragment owner;
    * the exact durable Target decoder still checks the resulting canonical data. */
  private def _initial_public_target(value: Json): Either[String, Json] =
    if (!value.hcursor.get[String]("schemaVersion").contains(initialSchema)) Right(value)
    else for {
      origin <- value.hcursor.get[Json]("initialOrigin").left.map(_.message)
      _ <- GoalPhaseCandidateRecovery.publicNonNullObject(origin, "initial source origin")
      lineage <- origin.hcursor.get[Json]("lineage").left.map(_.message)
      _ <- GoalPhaseCandidateRecovery.publicNonNullObject(lineage, "initial source lineage")
      rawrecovery <- lineage.hcursor.get[Json]("recovery").left.map(_.message)
      recovery <- GoalPhaseCandidateRecovery.decodePublicTarget(rawrecovery)
      canonicalrecovery <- _exact_public_fragment(rawrecovery, recovery.asJson)
      basechanges <- _public_stage_changes(origin.hcursor.downField("baseChanges").focus.getOrElse(Json.Null))
      continuation <- lineage.hcursor.downField("continuation").focus match {
        case None => Right(None)
        case Some(Json.Null) => Left("Public initial source continuation must be absent or non-null")
        case Some(supplied) => GoalPhaseCandidateObservationContinuation.decodePublicTarget(supplied)
          .flatMap(target => _exact_public_fragment(supplied, target.asJson)).map(Some(_))
      }
      primary <- origin.hcursor.get[Json]("currentPrimary").left.map(_.message)
      _ <- GoalPhaseCandidateRecovery.publicNonNullObject(primary, "current primary observation")
      repository <- GoalPhaseCandidateObservationContinuation.publicRepository(
        primary.hcursor.downField("repository").focus.getOrElse(Json.Null))
      stagedchanges <- _public_stage_changes(primary.hcursor.downField("stagedChanges").focus.getOrElse(Json.Null))
      rawselection <- primary.hcursor.get[Json]("historySelection").left.map(_.message)
      _ <- GoalPhaseCandidateRecovery.publicNonNullObject(rawselection, "current primary source-history selection")
      fields <- rawselection.asObject.toRight("Public current primary source-history selection must be an object")
      _ <- Either.cond(fields.contains("style") && fields.keys.forall(Set("style", "rule").contains), (),
        "Public current primary source-history selection fields are missing or unknown")
      selection <- rawselection.as[WorkflowSourceHistoryRule.Selection].left.map(_.message)
      _ <- Either.cond(rawselection.hcursor.downField("rule").focus.forall(rule =>
        selection.rule.exists(_.asJson == rule)), (), "Public current primary source-history rule has unknown or crossed fields")
      canonicallineage = lineage.mapObject(fields => {
        val required = fields.add("recovery", canonicalrecovery)
        continuation.fold(required)(target => required.add("continuation", target))
      })
      canonicalprimary = primary.mapObject(_.add("repository", repository.asJson)
        .add("historySelection", selection.asJson).add("stagedChanges", stagedchanges.asJson))
      canonicalorigin = origin.mapObject(_.add("lineage", canonicallineage)
        .add("currentPrimary", canonicalprimary).add("baseChanges", basechanges.asJson))
    } yield value.mapObject(_.add("initialOrigin", canonicalorigin))

  private def _exact_public_fragment(supplied: Json, canonical: Json): Either[String, Json] = for {
    raw <- GoalPhaseNativeMetadata.record(canonical).toJsonStringC.map[Either[String, String]](Right(_))
      .fold(error => Left(error.toString), identity)
    parsed <- io.circe.parser.parse(raw).left.map(_.message)
    expected <- GoalPhaseNativeMetadata.normalizePublicJson(parsed)
    _ <- Either.cond(supplied == expected, (), "Public initial source fragment has unknown or noncanonical fields")
  } yield canonical

  private def _public_stage_changes(value: Json): Either[String, Vector[StageChange]] = for {
    entries <- value.as[Vector[Json]].left.map(_.message)
    changes <- entries.foldLeft[Either[String, Vector[StageChange]]](Right(Vector.empty)) { (result, entry) => for {
      previous <- result
      _ <- GoalPhaseCandidateRecovery.publicNonNullObject(entry, "initial source stage change")
      change <- entry.as[StageChange].left.map(_.message)
    } yield previous :+ change }
  } yield changes

  def bindingC(component: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (text(component) && targetValid(target) && target.initialOrigin.forall(_.lineage.recovery.participant == participant) && target.primaryFailureOrigin.forall(o =>
        o.componentInstance == component && o.participant == participant) && target.waitOrigin.forall(o =>
        o.componentInstance == component && o.participant == participant) && choicesFor(target).contains(choice) && text(rationale) &&
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
    _ <- if (source != null && D.admissionValid(source.predecessor) && proposalValid(proposal) &&
        proposal.schemaVersion == sourceSchema && proposal.consumption.isInstanceOf[Consumption]) Consequence.unit
      else D.invalid("source revision requires a native standing dependency and closed proposal")
    human = source.human
    _ <- resumedC(human, "source-revision-description-eligibility")
    pending <- pendingC(human)
    _ <- if (source.initialLineage.isEmpty || source.original.isEmpty &&
        source.initialLineage.exists(initialLineageValid)) Consequence.unit
      else D.invalid("initial source succession cannot reinterpret an earlier source Direction")
    _ <- if (source.original match {
      case None => human.result.result.value.dependencyAdmission.contains(source.predecessor)
      case Some(original) => directionValid(original) && !isSuccessor(original.target) &&
        original.target.predecessor == source.predecessor && source.approvals.forall(D.approvalValid) &&
        human.result.result.value.dependencyAdmission.isEmpty
    }) Consequence.unit else D.invalid("source revision predecessor is not the actual native semantic lineage")
    selected <- declaration.dependencySourceRevisions.find(b => b.identity == proposal.binding.identity &&
      b.revision.toString == proposal.binding.revision).map(Consequence.success)
      .getOrElse(D.invalid("dependency source binding is not independently declared by this host"))
    _ <- if (declaration.dependencySourceConsumptions.contains(proposal.consumption)) Consequence.unit
      else D.invalid("primary source/runtime consumption is not the admitted host declaration")
    inherited = (source.predecessor.acceptedPrefix.toVector.flatMap(_.paths) ++ source.predecessor.binding.paths).distinct
    old <- source.initialLineage match {
      case Some(_) => Consequence.success(Option.empty[Guard])
      case None => source.original.map(value => Consequence.success(value.target.predecessorGuard))
        .getOrElse(observeC(core, source.predecessor.binding.copy(paths = inherited), Vector.empty).map(Some(_)))
    }
    fresh <- observeC(core, selected, proposal.excludedPaths)
    initial <- source.initialLineage match {
      case None => Consequence.success(Option.empty[InitialOrigin])
      case Some(lineage) => for {
        primary <- currentPrimaryC(core, declaration, lineage)
        ancestry <- initialBaseC(core, selected, source.predecessor.binding.base)
      } yield Some(InitialOrigin(initialGuardUnavailable, lineage, primary, ancestry._1, ancestry._2))
    }
    step <- human.input.stepIdentity.map(Consequence.success).getOrElse(D.invalid("source revision has no current Step"))
    succession = source.original.map(original => SourceSuccession(original.directive,
      _scope_approvals(original, source.approvals), source.previousDirection.getOrElse(original).directive))
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
    target = Target(if (initial.nonEmpty) initialSchema else sourceSchema,
      human.session.current.identity.value, H.identity(human).value,
      human.from.revision.value.toString, pending.revision.value.toString, human.controller.plan, step,
      human.controller.generation, human.controller.base, human.controller.completed,
      GoalPhaseRepairProgress.snapshot(human.controller), human.result.resultReference,
      source.predecessor, source.ancestors, proposal, selected, old, fresh, None, exclusions, committed, succession,
      initialOrigin = initial)
    _ <- if (targetValid(target) && source.original.forall(original =>
        _source_target_successor_valid(original, target, source.approvals) &&
          sourcePreviousValid(original, target, source.previousDirection))) Consequence.unit
      else D.invalid("source revision crosses inherited source, runtime or acceptance obligations")
    questions <- human.session.storage.bindDecisionC(H.completionOperation, H.inputCodec)
    issued <- questions.loadC(H.identity(human))(using core.executionContext)
    _ <- if (issued.nonEmpty) Consequence.unit else D.invalid("source revision requires the actual issued unanswered question")
  } yield target

  def describeC(source: Source, declaration: F.Declaration, core: ActionCall.Core,
    raw: Record): Consequence[V.GoalPhaseDependencySourceRevisionDescriptionResult] = for {
    proposal <- proposalC(raw)
    target <- targetForC(source, declaration, core, proposal)
  } yield V.GoalPhaseDependencySourceRevisionDescriptionResult(target.sessionId, "direction-required", messageFor(target),
    core.component.get.core.instanceId.canonicalKey, targetRecord(target), choicesFor(target), adoptionOperation)

  def adoptC(source: Source, declaration: F.Declaration, core: ActionCall.Core,
    input: V.GoalPhaseDependencySourceRevisionAdoptionInput): Consequence[V.GoalPhaseDependencySourceRevisionAdoptionResult] = for {
    supplied <- targetC(input.target)
    _ <- if (input.sessionId == supplied.sessionId && choicesFor(supplied).contains(input.choice) && text(input.rationale) &&
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
      direction = Direction(actual, reference, source.human.session.participantIdentity, input.rationale,
        Option.when(actual.initialOrigin.nonEmpty)(input.choice))
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
        (if (isSuccessor(target)) source.result.result.value.dependencyAdmission.isEmpty
          else source.result.result.value.dependencyAdmission.contains(target.predecessor)) &&
        direction.participant == source.session.participantIdentity) Consequence.unit
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
      else GoalPhasePrerequisitePublicationRecovery.resourceInputC(request).map(source => Action(request, describe, source, port))
  }
  private final case class Action(request: Request, describe: Boolean, source: NativeWorkflowResourceInput.Source[Record],
    port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        record <- NativeWorkflowResourceInput.readC(source, reference => read_resource_text(reference,
          Some(java.nio.charset.StandardCharsets.UTF_8)).flatMap(GoalPhasePrerequisitePublicationRecovery.validateBusinessTextC))
        result <- if (describe) for {
          input <- GoalPhasePrerequisitePublicationRecovery.businessRecordC[V.GoalPhaseDependencySourceRevisionDescriptionInput](record, Set("sessionId", "proposal"))
          _ <- proposalC(input.proposal)
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(input.sessionId))
          value <- port.describeDependencySourceRevisionC(input, core.copy(executionContext = context))
        } yield NativeWorkflowOperationOutput.response(value)
        else for {
          input <- GoalPhasePrerequisitePublicationRecovery.businessRecordC[V.GoalPhaseDependencySourceRevisionAdoptionInput](record,
            Set("sessionId", "target", "choice", "rationale", "reference"))
          target <- targetC(input.target)
          _ <- if (choicesFor(target).contains(input.choice) && text(input.rationale)) Consequence.unit
            else D.invalid("Source input crosses its exact target choices or rationale")
          context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(input.sessionId))
          value <- port.adoptDependencySourceRevisionC(input, core.copy(executionContext = context))
        } yield NativeWorkflowOperationOutput.response(value)
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

  private def text(value: String): Boolean = value != null && value.nonEmpty &&
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
  def consumptionValid(value: ConsumptionRecord): Boolean = value != null &&
    WorkspaceId.parseC(value.primaryWorkspace).isSuccess && root(value.sourceRoot) &&
    (value match {
      case legacy: LegacyConsumption => text(legacy.runtimeImage) && referenceValid(legacy.manifestReference)
      case _: Consumption => true
    }) && unique(value.nonInputExclusions, 128)(path) &&
    unique(value.expectedModes, 128)(m => m != null && path(m.path) && Set("100644", "100755", "000000")(m.mode)) &&
    value.expectedModes.nonEmpty && value.expectedModes.map(_.path).distinct.size == value.expectedModes.size &&
    unique(value.programs, 32)(programValid) && value.programs.nonEmpty &&
    value.programs.map(_.check).distinct.size == value.programs.size
  def programValid(value: CheckProgram): Boolean = value != null && text(value.check) &&
    WorkspaceId.parseC(value.workspace).isSuccess && text(value.program) &&
    Set("compile", "test", "test-only", "fix-check")(value.kind)
  def proposalValid(value: Proposal): Boolean = value != null &&
    ((value.schemaVersion == schema && value.consumption.isInstanceOf[LegacyConsumption]) ||
      (value.schemaVersion == sourceSchema && value.consumption.isInstanceOf[Consumption])) &&
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

  def initialLineageValid(value: InitialLineage): Boolean = value != null &&
    GoalPhaseCandidateRecovery.validTarget(value.recovery) && value.continuation != null &&
    value.recovery.dependencyPrefix.isEmpty && value.recovery.inspection.dependencyAdmission.nonEmpty &&
    value.recovery.inspection.dependencySourceDirection.isEmpty && value.recovery.inspection.dependency.isEmpty &&
    value.recovery.inspection.dependencyStaged.isEmpty && value.continuation.forall(c =>
      GoalPhaseCandidateObservationContinuation.validTarget(c) && c.original == value.recovery &&
        c.priorObservations.forall(_.dependencyPrefix.isEmpty)) &&
    referenceValid(value.revisionDirection) && referenceValid(value.revisionRecord) &&
    value.revisionRecord.identity == value.recovery.sessionId && revision(value.revisionRecord.revision) &&
    BigInt(value.revisionRecord.revision) == BigInt(value.continuation.map(_.frontierRevision)
      .getOrElse(value.recovery.frontierRevision)) + 1

  def completedEffects(value: InitialLineage): Vector[ContextReference] =
    value.recovery.historyPrefix.map(p => ContextReference(p.resultIdentity, p.resultRevision)) ++
      value.recovery.stagingPrefix.map(p => ContextReference(p.resultIdentity, p.resultRevision))

  def currentPrimaryValid(value: CurrentPrimaryObservation): Boolean = value != null &&
    value.observed == "current-at-description" && value.repository != null && root(value.repository.root) &&
    GitBranchName.parseC(value.repository.branch).isSuccess &&
    Vector(value.repository.head, value.repository.tree, value.repository.indexTree).forall(oid) &&
    value.selectedPaths != null && value.selectedPaths.nonEmpty && value.selectedPaths.distinct == value.selectedPaths &&
    value.selectedPaths.forall(path) && value.ownedPaths != null && value.ownedPaths.forall(path) &&
    value.candidatePaths != null && value.candidatePaths.forall(path) &&
    value.selectedPaths.forall(value.ownedPaths.contains) && value.ownedPaths.forall(value.candidatePaths.contains) &&
    value.repository.dirty != null && value.repository.dirty.forall(d => d != null &&
      dirtyValid(Dirty(d.index, d.worktree, d.path, d.renamedFrom)) &&
      (if (value.selectedPaths.exists(GoalPhaseNativeCandidateBoundary.covers(_, d.path))) d.worktree == " "
        else d.index == " " || d.index == "?" && d.worktree == "?")) &&
    value.stagedChanges != null && value.stagedChanges.forall(c => changeValid(c) &&
      value.selectedPaths.exists(GoalPhaseNativeCandidateBoundary.covers(_, c.path))) &&
    value.stagedChanges.map(_.path).distinct == value.stagedChanges.map(_.path) &&
    value.diff != null && value.diff.nonEmpty && java.nio.charset.StandardCharsets.UTF_8.newEncoder.canEncode(value.diff) &&
    value.files != null && value.files.forall(fileValid) && value.files.map(_.path) == value.stagedChanges.map(_.path) &&
    value.historySelection != null && WorkflowSourceHistoryRule.selectionC(value.historySelection.style,
      value.historySelection.rule).isSuccess && value.completedEffects != null && value.completedEffects.forall(referenceValid) &&
    text(value.processPolicy) && io.circe.parser.parse(value.processPolicy).toOption.exists(_.isObject)

  def initialOriginValid(value: InitialOrigin): Boolean = value != null &&
    value.predecessorGuardAvailability == initialGuardUnavailable && initialLineageValid(value.lineage) &&
    currentPrimaryValid(value.currentPrimary) && {
      val old = value.lineage.recovery
      val primary = value.currentPrimary
      val parent = GoalPhaseNativeCandidateBoundary.parentC(old.controller.tip, old.inspection.continuation).toOption
      primary.repository.root == old.root && primary.repository.branch == old.branch &&
        parent.contains(O.Snapshot(primary.repository.head, primary.repository.tree)) &&
        primary.selectedPaths == old.inspection.selectedPaths && primary.ownedPaths == old.controller.plan.steps(old.controller.cursor).ownedPaths &&
        primary.candidatePaths == old.candidatePaths && primary.historySelection == old.historySelection &&
        primary.completedEffects == completedEffects(value.lineage)
    } && value.baseLineage != null && value.baseLineage.nonEmpty && value.baseLineage.forall(c =>
      c != null && oid(c.identity) && oid(c.tree) && c.parents != null && c.parents.forall(oid) &&
        c.parents.distinct == c.parents && !c.parents.contains(c.identity)) &&
    value.baseLineage.map(_.identity).distinct.size == value.baseLineage.size &&
    value.baseLineage.sliding(2).forall(pair => pair.size == 1 || pair.head.parents.contains(pair.last.identity)) &&
    value.baseChanges != null && value.baseChanges.forall(changeValid) &&
    value.baseChanges.map(_.path).distinct.size == value.baseChanges.size

  private def _initial_target_valid(value: Target): Boolean = value.initialOrigin.exists { initial =>
    if (!D.admissionValid(value.predecessor) || !D.bindingValid(value.binding) || !proposalValid(value.proposal) ||
        !initialOriginValid(initial) || !revision(value.sourceRevision) || !O.validatePlanC(value.plan).isSuccess) false else {
    val old = initial.lineage.recovery
    val previous = value.predecessor.binding
    val inherited = (value.predecessor.acceptedPrefix.toVector.flatMap(_.paths) ++ previous.paths).distinct
    initialOriginValid(initial) && value.predecessorGuard.isEmpty && value.runtime.isEmpty &&
      value.sourceSuccession.isEmpty && value.exclusionSuccession.isEmpty && value.committedBaseSuccession.isEmpty &&
      value.waitOrigin.isEmpty && value.primaryFailureOrigin.isEmpty && value.proposal.schemaVersion == sourceSchema &&
      value.proposal.mode == "selected-changes" && old.inspection.dependencyAdmission.contains(value.predecessor) &&
      old.sessionId == value.sessionId && old.controller.plan == value.plan && old.controller.base == value.primaryBase &&
      old.controller.completed == value.completed && old.inspection.step == value.step &&
      value.generation > old.controller.generation && BigInt(value.sourceRevision) > BigInt(initial.lineage.revisionRecord.revision) &&
      value.binding.paths == inherited && value.binding.checks == previous.checks &&
      _initial_programs_valid(value) &&
      value.binding.copy(revision = previous.revision, paths = previous.paths, root = previous.root,
        branch = previous.branch, base = previous.base, dependency = previous.dependency) == previous &&
      initial.baseLineage.head.identity == value.binding.base.head && initial.baseLineage.head.tree == value.binding.base.tree &&
      initial.baseLineage.last.identity == previous.base.head && initial.baseLineage.last.tree == previous.base.tree &&
      !initial.baseChanges.exists(c => inherited.exists(p => GoalPhaseNativeCandidateBoundary.covers(p, c.path) ||
        GoalPhaseNativeCandidateBoundary.covers(c.path, p)))
    }
  }

  /** Match the already resolved host policy; this is selection validation only,
    * never a substitute executor or a build receipt. */
  private def _initial_programs_valid(value: Target): Boolean = value.initialOrigin.exists { initial =>
    val parsed = for {
      policy <- io.circe.parser.parse(initial.currentPrimary.processPolicy).toOption
      validation <- policy.hcursor.get[GoalPhaseNativeValidation.Policy]("validation").toOption
      process <- policy.hcursor.get[Json]("processPlan").toOption
      programs <- process.hcursor.get[Vector[Json]]("sbtPrograms").toOption
      workspaces <- process.hcursor.get[Vector[Json]]("workspaces").toOption
      workspacegrants <- process.hcursor.get[Vector[Json]]("workspaceGrants").toOption
      grants <- process.hcursor.get[Vector[Json]]("grants").toOption
      step <- value.plan.steps.find(_.identity == value.step)
    } yield {
      if (!validation.validateC.isSuccess) false else {
      val primary = validation.checks.filter(c => step.checks.contains(c.identity))
      val expected = primary.map(_ -> initial.currentPrimary.repository.root) ++ value.binding.checks.map(_ -> value.binding.root)
      primary.map(_.identity).toSet == step.checks.toSet && expected.forall { (check, root) =>
        val explicit = value.proposal.consumption.programs.find(p => p.check == check.identity && p.kind == check.kind.wire)
        val default = if (!value.binding.checks.contains(check) || value.proposal.consumption.programs.exists(p =>
            value.binding.checks.exists(_.identity == p.check))) None
          else programs.filter(p => p.hcursor.get[String]("target").contains(value.binding.dependency.workspace) &&
            p.hcursor.get[String]("operation").contains(check.kind.wire)) match {
            case Vector(program) => program.hcursor.get[String]("identity").toOption.map(identity =>
              CheckProgram(check.identity, value.binding.dependency.workspace, check.kind.wire, identity))
            case _ => None
          }
        explicit.orElse(default).exists { route =>
          val selected = programs.filter(p => p.hcursor.get[String]("target").contains(route.workspace) &&
            p.hcursor.get[String]("operation").contains(route.kind))
          selected.size == 1 && selected.head.hcursor.get[String]("identity").contains(route.program) &&
            workspaces.exists(w => w.hcursor.get[String]("identity").contains(route.workspace) &&
              w.hcursor.get[String]("root").contains(root)) &&
            workspacegrants.exists(g => g.hcursor.get[String]("componentId").contains(SmWorkflowComponent.componentId.name) &&
              g.hcursor.get[String]("workspaceId").contains(route.workspace) && g.hcursor.get[String]("access").contains("manage")) &&
            selected.head.hcursor.get[String]("capability").toOption.exists(capability => grants.exists(g =>
              g.hcursor.get[String]("componentId").contains(SmWorkflowComponent.componentId.name) &&
                g.hcursor.get[Vector[String]]("capabilities").toOption.exists(_.contains(capability))))
        }
      }
      }
    }
    parsed.contains(true)
  }

  def targetValid(value: Target): Boolean = value != null && value.initialOrigin != null && value.predecessorGuard != null && value.primaryFailureOrigin != null && value.waitOrigin != null && value.runtime != null && value.sourceSuccession != null && value.exclusionSuccession != null &&
    value.committedBaseSuccession != null && value.proposal != null &&
    (if (value.schemaVersion == initialSchema) _initial_target_valid(value)
    else if (value.initialOrigin.nonEmpty || value.predecessorGuard.isEmpty) false
    else if (value.schemaVersion == primarySchema) value.primaryFailureOrigin.exists(GoalPhasePrerequisitePublicationRecovery.primaryTargetValid) &&
      value.waitOrigin.isEmpty && value.runtime.isEmpty && value.sourceSuccession.isEmpty && value.exclusionSuccession.isEmpty &&
      value.proposal.schemaVersion == sourceSchema && _primary_target_valid(value)
    else if (value.primaryFailureOrigin.nonEmpty) false
    else if (value.schemaVersion == waitSchema) value.waitOrigin.exists(GoalPhasePrerequisitePublicationUnknownRecovery.targetValid) &&
      value.runtime.isEmpty && value.sourceSuccession.isEmpty && value.exclusionSuccession.isEmpty &&
      value.proposal.schemaVersion == sourceSchema && _wait_target_valid(value)
    else if (value.schemaVersion == sourceSchema) value.waitOrigin.isEmpty && value.runtime.isEmpty && value.proposal.schemaVersion == sourceSchema &&
      value.sourceSuccession.forall(s => s != null && referenceValid(s.parent) && referenceValid(s.previousDirection) &&
        unique(s.approvals, 16)(D.approvalValid) &&
        s.approvals.map(_.decision).distinct.size == s.approvals.size)
    else value.waitOrigin.isEmpty && value.sourceSuccession.isEmpty && value.proposal.schemaVersion == schema &&
      value.schemaVersion == (if (value.committedBaseSuccession.nonEmpty) committedSchema else if (value.exclusionSuccession.nonEmpty) exclusionSchema else if (value.runtime.nonEmpty) runtimeSchema else schema)) &&
    value.exclusionSuccession.forall(s => _exclusion_succession_valid(s) &&
      (value.runtime.exists(r => r != null && r.parent == s.parent) || value.sourceSuccession.exists(r => r != null && r.parent == s.parent)) && value.proposal.excludedPaths == s.previousExclusions ++ s.addedPaths) &&
    value.runtime.forall(r => r != null && referenceValid(r.parent) && unique(r.approvals, 16)(D.approvalValid) &&
      r.approvals.nonEmpty && r.approvals.map(_.decision).distinct.size == r.approvals.size &&
      runtimeMatches(r.installation, value.proposal.consumption)) &&
    text(value.sessionId) && (if (Set(waitSchema, primarySchema)(value.schemaVersion)) value.questionIdentity == "" else text(value.questionIdentity)) &&
    revision(value.sourceRevision) && revision(value.pendingRevision) &&
    (if (Set(waitSchema, primarySchema)(value.schemaVersion)) value.pendingRevision == value.sourceRevision
      else BigInt(value.pendingRevision) > BigInt(value.sourceRevision)) && value.plan != null && O.validatePlanC(value.plan).isSuccess &&
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
    value.predecessorGuard.forall(g => guardValid(g) && g.root == value.predecessor.binding.root &&
      g.branch == value.predecessor.binding.branch && g.head == value.predecessor.binding.base.head &&
      g.committedTree == value.predecessor.binding.base.tree) &&
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
      (value.runtime.exists(_.parent == s.parent) || value.sourceSuccession.exists(_.parent == s.parent) ||
        value.waitOrigin.exists(_.requestingResult == s.parent) || value.primaryFailureOrigin.exists(_.requestingResult == s.parent)) && s.nextBase == value.binding.base &&
      s.committedEntries.map(_.path) == value.binding.paths &&
      s.committedEntries.zip(value.proposal.consumption.expectedModes).forall((entry, mode) =>
        entry.path == mode.path && entry.entry.map(_.mode).getOrElse("000000") == mode.mode) &&
      s.committedEntries.zip(value.preStageGuard.selectedFiles).forall((entry, file) => entry.entry.nonEmpty == file.present) &&
      _partitions_match(s, value.binding.paths,
        value.exclusionSuccession.map(_.previousExclusions).getOrElse(value.proposal.excludedPaths),
        value.exclusionSuccession.toVector.flatMap(_.addedPaths))) &&
    coverageMatches(value)
  /** The distinct Wait origin is exact native provenance, never a fake Human
    * question. Inherited scope and coverage remain standing obligations. */
  private def _wait_target_valid(value: Target): Boolean = value.waitOrigin.exists { old =>
    old.sessionId == value.sessionId && old.frontierRevision == value.sourceRevision && old.plan == value.plan &&
      old.base == value.primaryBase && old.generation == value.generation && old.completed == value.completed &&
      old.repairProgress == value.repairProgress && old.requestingResult == value.requestingResult &&
      old.admission == value.predecessor && old.ancestors == value.ancestors &&
      value.predecessorGuard.contains(old.guard) && old.retainedGuard == value.predecessorGuard &&
      value.binding.paths == (value.predecessor.binding.paths ++ _wait_approvals(value).flatMap(_.proposal.paths)
        .filterNot(value.predecessor.binding.paths.contains).distinct) &&
      value.binding.copy(revision = value.predecessor.binding.revision, paths = value.predecessor.binding.paths,
        base = if (value.committedBaseSuccession.nonEmpty) value.predecessor.binding.base else value.binding.base) == value.predecessor.binding &&
      value.committedBaseSuccession.forall(_.previousBase == value.predecessor.binding.base)
  }
  private def _wait_approvals(value: Target): Vector[D.Approval] = value.waitOrigin.toVector.flatMap(_.scopeApprovals).filter { approval =>
    val inherited = value.predecessor.approvals ++ value.ancestors.flatMap(_.capture.admission.approvals)
    approval.step == value.step && !inherited.exists(_.decision == approval.decision) &&
      approval.proposal.requester == value.predecessor.binding.requester &&
      approval.proposal.dependency == value.predecessor.binding.dependency &&
      inherited.map(_.proposal.usageContract).distinct == Vector(approval.proposal.usageContract)
  }
  private def _primary_target_valid(value: Target): Boolean = value.primaryFailureOrigin.exists { old =>
    old.phase == GoalPhasePrerequisitePublicationRecovery.PrimaryPhase.SourceRequired &&
      old.sessionId == value.sessionId && old.frontierRevision == value.sourceRevision && old.plan == value.plan &&
      old.base == value.primaryBase && old.generation == value.generation && old.completed == value.completed &&
      old.repairProgress == value.repairProgress && old.requestingResult == value.requestingResult &&
      old.originalDirection.target.admission == value.predecessor && old.originalDirection.target.ancestors == value.ancestors &&
      value.predecessorGuard.contains(old.dependencyGuard) &&
      value.binding.dependency.workspace == value.predecessor.binding.dependency.workspace &&
      value.binding.paths == (value.predecessor.binding.paths ++ _primary_approvals(value).flatMap(_.proposal.paths)
        .filterNot(value.predecessor.binding.paths.contains).distinct) &&
      value.binding.copy(revision = value.predecessor.binding.revision, paths = value.predecessor.binding.paths,
        root = value.predecessor.binding.root, branch = value.predecessor.binding.branch,
        base = value.predecessor.binding.base) == value.predecessor.binding &&
      (value.binding.base == value.predecessor.binding.base || value.committedBaseSuccession.exists(_.previousBase == value.predecessor.binding.base)) &&
      GoalPhasePrerequisitePublicationRecovery.primaryProgramMatches(old.processPolicy, old.targets(1), value.proposal.consumption)
  }
  private def _primary_approvals(value: Target): Vector[D.Approval] = {
    val inherited = value.predecessor.approvals ++ value.ancestors.flatMap(_.capture.admission.approvals)
    value.primaryFailureOrigin.toVector.flatMap(_.scopeApprovals).filter(approval => approval.step == value.step &&
      !inherited.exists(_.decision == approval.decision) && approval.proposal.requester == value.predecessor.binding.requester &&
      approval.proposal.dependency == value.predecessor.binding.dependency &&
      inherited.map(_.proposal.usageContract).distinct == Vector(approval.proposal.usageContract))
  }
  private def coverageMatches(value: Target): Boolean = {
    val approvals = (value.predecessor.approvals ++ value.ancestors.flatMap(_.capture.admission.approvals) ++
      value.runtime.toVector.flatMap(_.approvals) ++ value.sourceSuccession.toVector.flatMap(_.approvals) ++
      (if (value.schemaVersion == waitSchema) _wait_approvals(value) else if (value.schemaVersion == primarySchema) _primary_approvals(value) else Vector.empty))
      .groupBy(_.decision).values.map(_.head).toVector
    value.proposal.coverage.map(_.approval).toSet == approvals.map(_.decision).toSet &&
      value.proposal.coverage.forall(c => approvals.find(_.decision == c.approval).exists(a =>
        c.acceptance == a.proposal.acceptance && c.checks.forall(id => value.binding.checks.exists(_.identity == id)))) &&
      value.binding.checks.forall(check => value.proposal.coverage.exists(_.checks.contains(check.identity)))
  }
  def directionValid(value: Direction): Boolean = value != null && targetValid(value.target) &&
    referenceValid(value.directive) && text(value.participant) && text(value.rationale) && value.choice != null &&
    value.target.initialOrigin.forall(_.lineage.recovery.participant == value.participant) &&
    (value.target.initialOrigin.isEmpty || value.choice.contains(initialChoice)) &&
    value.target.waitOrigin.forall(_.participant == value.participant) && value.target.primaryFailureOrigin.forall(_.participant == value.participant) &&
    (value.target.primaryFailureOrigin.isEmpty || value.choice.exists(choicesFor(value.target).filterNot(_ == "Defer").contains)) &&
    value.choice.fold(true)(choice =>
        choice == "AdoptDependencySourceRevisionWithUnknownEffects" && (value.target.waitOrigin.nonEmpty || value.target.primaryFailureOrigin.nonEmpty) ||
          choice == combinedPrimaryChoice && value.target.primaryFailureOrigin.nonEmpty ||
          choice == initialChoice && value.target.initialOrigin.nonEmpty)
  def captureValid(value: Capture): Boolean = value != null && value.scope != null &&
    value.schemaVersion == _capture_schema(value.direction, value.scope) &&
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
      else value.stagedPaths == stagingPaths(value.direction, value.scope, value.preStageGuard) &&
        !value.postStageGuard.dirty.exists(d => bindingOf(value).paths.contains(d.path) && d.worktree != " ") &&
        value.postStageGuard.copy(indexTree = value.preStageGuard.indexTree,
          dirty = value.preStageGuard.dirty, selectedEntries = value.preStageGuard.selectedEntries) == value.preStageGuard)

  def candidateValid(value: GoalPhaseNativeCandidate.Payload): Boolean = value != null &&
    value.dependencySourceDirection != null &&
    value.initialSourceEffects != null && value.initialSourceEffects.forall(reference =>
      value.dependencySourceDirection.exists(direction => direction.target.initialOrigin.exists { origin =>
        val primary = origin.currentPrimary
        val last = origin.lineage.recovery.stagingPrefix.last
        reference == ContextReference(last.resultIdentity, last.resultRevision) &&
          value.selectedPaths == primary.selectedPaths && value.stagedPaths == primary.selectedPaths &&
          value.continuation == origin.lineage.recovery.inspection.continuation &&
          value.hygiene == origin.lineage.recovery.inspection.hygiene && value.primaryPublicationRepair.isEmpty &&
          value.candidate.forall(_ == O.Snapshot(primary.repository.head, primary.repository.indexTree)) &&
          value.diff.forall(_ == primary.diff) &&
          (value.candidate.isEmpty && value.changedPaths.isEmpty ||
            value.candidate.nonEmpty && value.changedPaths == primary.stagedChanges.map(_.path)) &&
          value.dependencySourceCapture.forall(_.generation == direction.target.generation + 1)
      })) &&
    value.dependencySourceScope != null && value.dependencySourceDirection != null && value.dependencySourceGuard != null && value.dependencySourceCapture != null &&
    value.dependencySourceStages != null && value.dependencySourceStages.forall(stageFactValid) &&
    (value.dependencySourceDirection match {
      case None => value.dependencySourceScope.isEmpty && value.dependencySourceGuard.isEmpty && value.dependencySourceStages.isEmpty && value.dependencySourceCapture.isEmpty
      case Some(direction) => directionValid(direction) && value.dependencySourceScope.forall(scopeValid(direction, _)) &&
        direction.target.primaryFailureOrigin.forall(original => value.sourceRevision.toLongOption.exists(_ > original.frontierRevision.toLong + 1L) ||
          value.copy(sourceRevision = original.candidate.sourceRevision,
          dependencyAdmission = original.candidate.dependencyAdmission, dependencyStaged = original.candidate.dependencyStaged,
          dependency = original.candidate.dependency, dependencyOrigin = original.candidate.dependencyOrigin,
          dependencySourceDirection = original.candidate.dependencySourceDirection, dependencySourceGuard = original.candidate.dependencySourceGuard,
          dependencySourceStages = original.candidate.dependencySourceStages, dependencySourceCapture = original.candidate.dependencySourceCapture,
          dependencySourceScope = original.candidate.dependencySourceScope) == original.candidate) &&
        (modeOf(direction, value.dependencySourceScope) != "committed" || value.dependencySourceStages.isEmpty) &&
        value.dependencySourceStages.forall(stageModeValid(direction, _, value.dependencySourceScope)) &&
        value.dependencyAdmission.isEmpty && value.dependency.isEmpty &&
        value.dependencyStaged.isEmpty && value.dependencyOrigin.isEmpty &&
        value.dependencySourceGuard.forall(guardModesValid(direction, _, value.dependencySourceScope)) &&
        value.dependencySourceStages.map(_.path) == stagingPaths(direction, value.dependencySourceScope,
          value.dependencySourceGuard.getOrElse(direction.target.preStageGuard)).take(value.dependencySourceStages.size) &&
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
        before <- if (direction.target.primaryFailureOrigin.nonEmpty && current.payload.dependencySourceGuard.nonEmpty)
          Consequence.success(current.payload.dependencySourceGuard.get) else observe
        _ <- if (captureBaseValid(direction, controller.generation, before, scope)) Consequence.unit
          else D.invalid("source capture differs from its adopted bytes or permitted ordinary repair")
        existing <- loadC("dependency-source-inspection")
        _ <- if (existing.isEmpty || direction.target.primaryFailureOrigin.nonEmpty && existing.exists(saved =>
            saved.payload.dependencySourceGuard.contains(before) && saved.payload.dependencySourceDirection.contains(direction))) Consequence.unit
          else D.invalid("source capture already has a retained inspection; do not replay")
        inspected <- if (existing.nonEmpty) Consequence.success(current)
          else saveC("dependency-source-inspection", current.record, current.payload.copy(dependencySourceGuard = Some(before)))
        staged <- stagingPaths(direction, scope, before).zipWithIndex
          .drop(if (direction.target.primaryFailureOrigin.nonEmpty) inspected.payload.dependencySourceStages.size else 0)
          .foldLeft(Consequence.success(inspected)) { case (prior, (path, index)) => for {
            previous <- prior
            retained <- loadC("dependency-source-stage-intent-" + index)
            _ <- if (retained.isEmpty) Consequence.unit else D.invalid("source staging intent is retained; its unknown result is not replayed")
            actualBefore <- observe
            expected = previous.payload.dependencySourceStages.lastOption.map(_.after).getOrElse(before)
            _ <- if (actualBefore == expected) Consequence.unit else D.invalid("source changed before its selected stage")
            intent <- saveC("dependency-source-stage-intent-" + index, previous.record, previous.payload)
            _ <- acknowledgeC("goal-dependency-source-stage-prepared:" + path)
            selection <- GitSelectionId.parseC("goal-path-" + (binding.paths.indexOf(path) + 1))
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
        capture = Capture(_capture_schema(direction, scope), current.record.identity.value, ContextReference(controller.plan.identity, controller.plan.revision),
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
      val guard = prefix.headOption.flatMap(_.payload.dependencySourceGuard).getOrElse(d.target.preStageGuard)
      val paths = stagingPaths(d, initial.dependencySourceScope, guard)
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
    else consumptionPreflightC(capture.direction, capture.scope).flatMap(_ => _qualified_observation_c(core, capture.direction, capture.scope)).flatMap(actual =>
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
    val phase = chain.toVector.flatMap(c => c.historicalPhase.map(_.capture) ++ c.activePhaseCapture)
    if (sources.isEmpty) GoalPhaseNativeDependencyValidation.chainFreshnessC(core, chain, legacy)
    else if (phase.exists(p => sources.exists(s => bindingOf(s).dependency.identity == p.admission.binding.dependency.identity)))
      D.invalid("active phase capture contradicts a revised source for the same dependency")
    else sources.foldLeft(Consequence.unit)((prior, capture) => prior.flatMap(_ =>
      chain.map(_.sourceCaptureC(capture)).getOrElse(D.invalid("revised source requires actual native lineage")))).flatMap { _ =>
      val superseded = sources.map(_.direction.target.binding.dependency.identity).toSet
      val retainedLegacy = (legacy ++ phase).distinct.filterNot(c => superseded(c.admission.binding.dependency.identity))
      val acceptedLegacy = chain.toVector.flatMap(_.accepted.map(_.capture))
        .filterNot(c => superseded(c.admission.binding.dependency.identity))
      // Original V1 capture validation remains strict. Proven superseded histories
      // are authenticated, while their new source owns the current byte check.
      val proof = (legacy ++ chain.toVector.flatMap(_.accepted.map(_.capture))).distinct
        .foldLeft(Consequence.unit)((prior, c) => prior.flatMap(_ => chain.map(_.captureC(c))
          .getOrElse(D.invalid("dependency history requires actual native lineage"))))
      val remaining = ((acceptedLegacy ++ retainedLegacy).filterNot(phase.contains) ++ phase).distinct
      val consistent = remaining.groupBy(_.step).values.forall(_.size == 1) &&
        remaining.groupBy(_.sourceRevision).values.forall(_.size == 1)
      proof.flatMap(_ => if (consistent) Consequence.unit else D.invalid("contradictory phase dependency history"))
        .flatMap(_ => phase.foldLeft(Consequence.unit)((prior, c) => prior.flatMap(_ => chain.get.captureC(c))))
        .flatMap(_ => remaining.groupBy(_.admission.binding.dependency.identity).values
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

  /** Fresh current-primary data. Historical receipts prove effects only. No
    * normalization, staging or synthetic candidate result is performed here. */
  private[admission] def currentPrimarySnapshotC(core: ActionCall.Core,
    old: GoalPhaseCandidateRecovery.RecoveryTarget): Consequence[GoalPhaseCandidateObservationContinuation.RepositorySnapshot] = for {
    root <- G.rootC(core)
    before <- G.observeC(core)
    index <- G.indexTreeC(core)
    parent <- GoalPhaseNativeCandidateBoundary.parentC(old.controller.tip, old.inspection.continuation)
    after <- G.observeC(core)
    afterindex <- G.indexTreeC(core)
    afterroot <- G.rootC(core)
    _ <- if (before == after && index == afterindex && root == afterroot && root == old.root &&
        before.branch == old.branch && before.head == parent.head && before.tree == parent.tree) Consequence.unit
      else D.invalid("current primary root, branch, tip or index crossed its original selected context")
  } yield GoalPhaseCandidateObservationContinuation.snapshot(root, before, index)

  private[admission] def currentPrimaryC(core: ActionCall.Core, declaration: F.Declaration,
    lineage: InitialLineage): Consequence[CurrentPrimaryObservation] = for {
    _ <- if (initialLineageValid(lineage) && declaration.candidatePaths == lineage.recovery.candidatePaths &&
        declaration.target.root.toString == lineage.recovery.root && declaration.target.localBranch == lineage.recovery.branch)
      Consequence.unit else D.invalid("initial source current primary crosses its authenticated owned-path policy")
    old = lineage.recovery
    _ <- GoalPhaseNativeSourceHistory.verifyCandidatePrefixC(core, old)
    snapshot <- currentPrimarySnapshotC(core, old)
    _ <- if (snapshot.dirty.forall(d =>
      if (old.inspection.selectedPaths.exists(GoalPhaseNativeCandidateBoundary.covers(_, d.path))) d.worktree == " "
      else d.index == " " || d.index == "?" && d.worktree == "?")) Consequence.unit
      else D.invalid("current primary requires completed scoped staging without new effects")
    parent <- GoalPhaseNativeCandidateBoundary.parentC(old.controller.tip, old.inspection.continuation)
    head <- GitObjectId.parseC(parent.head)
    index <- GitObjectId.parseC(snapshot.indexTree)
    delta <- E.executeGitC(core, GitOperation.TreeChanges(head, index)).flatMap(_.valueC)
    diff <- E.executeGitC(core, GitOperation.PendingDiff(head)).flatMap(_.valueC)
    _ <- if (!diff.isBinary && delta.forall(c => changeValid(change(c)) &&
        old.inspection.selectedPaths.exists(GoalPhaseNativeCandidateBoundary.covers(_, c.path.value)))) Consequence.unit
      else D.invalid("current primary index or diff crosses the completed effect scope")
    files <- filesC(core, "sm-workflow-repository", delta.map(_.path.value))
    _ <- delta.zip(files).foldLeft(Consequence.unit) { case (prior, (entry, file)) => prior.flatMap { _ => for {
      bytes <- entry.newBlob match {
        case None => Consequence.success(Option.empty[Vector[Byte]])
        case Some(blob) => E.executeGitC(core, GitOperation.BlobContent(blob)).flatMap(_.valueC).map(b => Some(b.bytes))
      }
      _ <- if (fileIdentity(entry.path.value, bytes) == file) Consequence.unit
        else D.invalid("current primary worktree bytes differ from its current staged blob")
      included <- old.historySelection.rule.map(WorkflowSourceHistoryRule.includesC(_, entry.path.value))
        .getOrElse(Consequence.success(false))
      _ <- if (!included || old.historyPrefix.exists(_.result.path == entry.path.value)) Consequence.unit
        else D.invalid("current primary normalization has no completed prefix; another write cannot be reused")
    } yield () } }
    process <- GoalPhasePrerequisitePublicationRecovery.processPolicyC(declaration, core)
    selection <- WorkflowSourceHistoryParameters.resolveC(core.executionContext)
    after <- currentPrimarySnapshotC(core, old)
    filesagain <- filesC(core, "sm-workflow-repository", delta.map(_.path.value))
    repeated <- E.executeGitC(core, GitOperation.TreeChanges(head, index)).flatMap(_.valueC)
    diffagain <- E.executeGitC(core, GitOperation.PendingDiff(head)).flatMap(_.valueC)
    _ <- if (snapshot == after && files == filesagain && delta == repeated && diff == diffagain &&
        selection == old.historySelection) Consequence.unit else D.invalid("current primary changed during observation")
    result = CurrentPrimaryObservation("current-at-description", snapshot, old.inspection.selectedPaths,
      old.controller.plan.steps(old.controller.cursor).ownedPaths, declaration.candidatePaths,
      delta.map(change), diff.text, files, selection, completedEffects(lineage), process)
    _ <- if (currentPrimaryValid(result)) Consequence.unit else D.invalid("current primary observation is incomplete")
  } yield result

  /** Native metadata proves an actual ancestry path at the successor workspace;
    * selected pending files must be disjoint from the complete committed delta. */
  private[admission] def initialBaseC(core: ActionCall.Core, binding: D.Binding,
    previous: O.Snapshot): Consequence[(Vector[O.HistoryCommit], Vector[StageChange])] = {
    def _git_[A](operation: GitOperation[A]): Consequence[A] =
      E.executeGitC(core, operation, binding.dependency.workspace).flatMap(_.valueC)
    def _walk_(queue: Vector[Vector[String]], seen: Map[String, O.HistoryCommit]): Consequence[Vector[O.HistoryCommit]] =
      queue.headOption match {
        case None => D.invalid("initial successor does not descend from the original dependency base")
        case Some(path) => for {
          id <- GitObjectId.parseC(path.last)
          metadata <- _git_(GitOperation.CommitMetadata(id))
          _ <- if (metadata.commit == id) Consequence.unit else D.invalid("initial ancestry crossed its selected commit")
          commit = O.HistoryCommit(metadata.commit.value, metadata.tree.value, metadata.parents.map(_.value))
          cache = seen.updated(commit.identity, commit)
          result <- if (commit.identity == previous.head) Consequence.success(path.map(cache))
            else _walk_(queue.tail ++ commit.parents.filterNot(p => cache.contains(p) || path.contains(p) ||
              queue.tail.exists(_.last == p)).map(path :+ _), cache)
        } yield result
      }
    for {
      lineage <- _walk_(Vector(Vector(binding.base.head)), Map.empty)
      _ <- if (lineage.head.tree == binding.base.tree && lineage.last.tree == previous.tree) Consequence.unit
        else D.invalid("initial successor ancestry does not prove both exact commit/tree pairs")
      before <- GitObjectId.parseC(previous.tree)
      after <- GitObjectId.parseC(binding.base.tree)
      changes <- _git_(GitOperation.TreeChanges(before, after))
      _ <- if (changes.forall(c => changeValid(change(c)) && !binding.paths.exists(p =>
        GoalPhaseNativeCandidateBoundary.covers(p, c.path.value) || GoalPhaseNativeCandidateBoundary.covers(c.path.value, p))))
        Consequence.unit else D.invalid("initial successor committed delta overlaps selected pending source")
    } yield (lineage, changes.map(change))
  }

  private[admission] def initialApplicableC(core: ActionCall.Core, declaration: F.Declaration,
    direction: Direction): Consequence[Unit] = direction.target.initialOrigin match {
    case None => Consequence.unit
    case Some(origin) => for {
      actual <- currentPrimaryC(core, declaration, origin.lineage)
      source <- observeC(core, direction.target.binding, direction.target.proposal.excludedPaths)
      ancestry <- initialBaseC(core, direction.target.binding, direction.target.predecessor.binding.base)
      _ <- if (actual == origin.currentPrimary && source == direction.target.preStageGuard &&
          ancestry == (origin.baseLineage, origin.baseChanges)) Consequence.unit
        else D.invalid("initial current-primary or successor source target drifted after Human selection")
    } yield ()
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
