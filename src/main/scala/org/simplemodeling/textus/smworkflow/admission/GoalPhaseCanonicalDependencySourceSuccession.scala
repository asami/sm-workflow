package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.{ContinuationIdentity as _, *}
import org.goldenport.cncf.workflow.WorkflowProtocolV1.*
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseDependencySourceRevision as R
import GoalPhaseDependencyValidation as D
import GoalPhaseNativeRun as F
import GoalPhaseNativeWork as S
import GoalPhaseOrderedProgression as O
import GoalPhasePublishedDependencySourceAdoption as P
import RepositorySyncManagedGitObservation as G
import WorkflowManagedProcessExecution as E

/** Committed canonical source selection over the existing source direction API.
  * Native traversal, not these records, supplies historical authority. External
  * publication is provenance; no artifact adoption or publication happens here.
  * @since   Nov.  8, 2026
  * @version Nov.  8, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseCanonicalDependencySourceSuccession {
  val proposalSchema = "sm-workflow.goal-phase-canonical-dependency-source-proposal.v1"
  val causation = "ordered-canonical-dependency-source:"
  val choice = "AdoptCanonicalDependencySourceRevision"
  val pendingChoice = "AdoptCanonicalDependencySourceRevisionAcceptingUnknownEffects"

  final case class WorkerLifecycle(priorWorkerEnded: Boolean, acceptsUnknownExternalEffects: Boolean)
  object WorkerLifecycle {
    given Encoder[WorkerLifecycle] = deriveEncoder
    given Decoder[WorkerLifecycle] = _closed(deriveDecoder,
      v => v != null && v.priorWorkerEnded && v.acceptsUnknownExternalEffects)
  }
  final case class Proposal(schemaVersion: String, source: R.Proposal,
    publication: P.PublicationObservation, adoptedDelivery: ContextReference,
    workerLifecycle: Option[WorkerLifecycle] = None)
  object Proposal {
    given Encoder[Proposal] = deriveEncoder[Proposal].mapJson(P.publicJson)
    given Decoder[Proposal] = _closed(deriveDecoder, proposalValid)
  }
  /** Exact original issued request and native entity revision remain history. */
  final case class Pending(continuation: ContextReference, entityRevision: String, request: S.Request)
  object Pending {
    given Encoder[Pending] = Encoder.instance(v => Json.obj("continuation" -> v.continuation.asJson,
      "entityRevision" -> v.entityRevision.asJson, "request" -> S.inputCodec.encode(v.request).noSpaces.asJson))
    given Decoder[Pending] = _closed(Decoder.instance(c => for {
      continuation <- c.get[ContextReference]("continuation")
      entity <- c.get[String]("entityRevision")
      raw <- c.get[String]("request")
      json <- io.circe.parser.parse(raw).left.map(e => DecodingFailure(e.message, c.history))
      request <- S.inputCodec.decode(json).left.map(DecodingFailure(_, c.history))
    } yield Pending(continuation, entity, request)), v => v != null && R.referenceValid(v.continuation) &&
      _revision(v.entityRevision) && v.request != null && Set("ImplementStep", "RepairStep")(v.request.kind))
  }
  final case class Origin(componentInstance: String, participant: String,
    controller: GoalPhaseCandidateRecovery.Controller, approvals: Vector[D.Approval],
    originalDirection: Option[R.Direction], previousDirection: Option[R.Direction],
    pending: Option[Pending], selection: Proposal, primaryRoot: String, processPolicy: String,
    baseLineage: Vector[O.HistoryCommit], historicalFailure: Option[HistoricalFailure] = None)
  final case class HistoricalFailure(candidate: ContextReference, validation: ContextReference,
    context: S.CheckFailureContext)
  object HistoricalFailure {
    import D.given
    private val _context_encoder: Encoder[S.CheckFailureContext] = deriveEncoder
    private val _context_decoder: Decoder[S.CheckFailureContext] = deriveDecoder
    given Encoder[HistoricalFailure] = Encoder.instance(v => Json.obj("candidate" -> v.candidate.asJson,
      "validation" -> v.validation.asJson, "context" -> _context_encoder(v.context).noSpaces.asJson))
    given Decoder[HistoricalFailure] = _closed(Decoder.instance(c => for {
      candidate <- c.get[ContextReference]("candidate")
      validation <- c.get[ContextReference]("validation")
      raw <- c.get[String]("context")
      json <- io.circe.parser.parse(raw).left.map(e => DecodingFailure(e.message, c.history))
      context <- _context_decoder.decodeJson(json)
    } yield HistoricalFailure(candidate, validation, context)), failureValid)
  }
  def failureValid(v: HistoricalFailure): Boolean = v != null && R.referenceValid(v.candidate) &&
    R.referenceValid(v.validation) && _revision(v.candidate.revision) && _revision(v.validation.revision) &&
    v.candidate.identity == v.validation.identity && BigInt(v.candidate.revision) < BigInt(v.validation.revision) &&
    S.admitCheckFailureContextC(v.context).isSuccess &&
    v.context.validation.outcome.exists(Set("repair-required", "repair-limit-reached"))
  private object NativeOriginCodecs {
    val controllerEncoder: Encoder[GoalPhaseCandidateRecovery.Controller] = summon
    val controllerDecoder: Decoder[GoalPhaseCandidateRecovery.Controller] = summon
    val directionEncoder: Encoder[R.Direction] = summon
    val directionDecoder: Decoder[R.Direction] = summon
  }
  object Origin {
    import D.given
    // Native snapshots retain their exact owner codec inside opaque text. Public
    // Record projection must not erase required native null-valued Option keys.
    private given Encoder[GoalPhaseCandidateRecovery.Controller] = Encoder.encodeString.contramap(v =>
      NativeOriginCodecs.controllerEncoder(v).noSpaces)
    private given Decoder[GoalPhaseCandidateRecovery.Controller] = Decoder.decodeString.emap(raw =>
      io.circe.parser.parse(raw).left.map(_.message).flatMap(_.as[GoalPhaseCandidateRecovery.Controller](
        NativeOriginCodecs.controllerDecoder).left.map(_.message)))
    private given Encoder[R.Direction] = Encoder.encodeString.contramap(v => NativeOriginCodecs.directionEncoder(v).noSpaces)
    private given Decoder[R.Direction] = Decoder.decodeString.emap(raw => io.circe.parser.parse(raw).left.map(_.message)
      .flatMap(_.as[R.Direction](NativeOriginCodecs.directionDecoder).left.map(_.message)))
    given Encoder[Origin] = deriveEncoder[Origin].mapJson(P.publicJson)
    given Decoder[Origin] = _closed(deriveDecoder, originValid)
  }
  final case class Checkpoint(direction: R.Direction)
  object Checkpoint {
    given Encoder[Checkpoint] = deriveEncoder
    given Decoder[Checkpoint] = _closed(deriveDecoder,
      v => v != null && R.directionValid(v.direction) && v.direction.target.canonicalOrigin.nonEmpty)
  }
  val codec: WorkflowResultJsonV1.PayloadCodec[Checkpoint] = new WorkflowResultJsonV1.PayloadCodec[Checkpoint] {
    val typeIdentity = "GoalPhaseCanonicalDependencySourceSuccession"
    def encode(value: Checkpoint): Json = value.asJson
    def decode(value: Json): Either[String, Checkpoint] = value.as[Checkpoint].left.map(_.message)
  }
  final case class Source(session: F.Session, from: InstanceRecord, controller: O.State,
    predecessor: D.Admission, ancestors: Vector[R.Ancestor], approvals: Vector[D.Approval],
    originalDirection: Option[R.Direction], previousDirection: Option[R.Direction],
    requestingResult: ContextReference, pending: Option[Pending], historicalFailure: Option[HistoricalFailure] = None)
  final case class Saved(direction: R.Direction, state: O.State, record: InstanceRecord)

  private def _closed[A: Encoder](decoder: Decoder[A], valid: A => Boolean): Decoder[A] =
    Decoder.instance(c => decoder(c).flatMap(v =>
      if (valid(v) && v.asJson == c.value) Right(v)
      else Left(DecodingFailure("canonical source value is incomplete, crossed or noncanonical", c.history))))
  private def _exact(v: String): Boolean = v != null && v.nonEmpty && v.trim == v && !v.exists(_.isControl)
  private def _revision(v: String): Boolean = v != null && v.toLongOption.exists(n => n > 0 && n.toString == v)
  private def _root(v: String): Boolean = _exact(v) && scala.util.Try(java.nio.file.Path.of(v)).toOption.exists(p =>
    p.isAbsolute && p.normalize.toString == v)
  private def _oid(v: String): Boolean = v != null && GitObjectId.parseC(v).isSuccess
  private def _check(value: Boolean, message: String): Consequence[Unit] =
    if (value) Consequence.unit else D.invalid(message)
  private def _clause(label: String)(valid: => Boolean): () => Option[String] =
    () => if (valid) None else Some(label)
  private def _first_failure(clauses: Vector[() => Option[String]]): Option[String] =
    clauses.iterator.map(clause => clause()).find(_.nonEmpty).flatten

  def proposalValid(v: Proposal): Boolean = v != null && v.schemaVersion == proposalSchema &&
    R.proposalValid(v.source) && v.source.schemaVersion == R.sourceSchema &&
    v.source.mode == "committed" && v.source.consumption.isInstanceOf[R.Consumption] &&
    P.observationValid(v.publication) && R.referenceValid(v.adoptedDelivery) &&
    v.workerLifecycle != null && v.workerLifecycle.forall(w => w != null && w.priorWorkerEnded && w.acceptsUnknownExternalEffects)
  def proposalC(value: Record): Consequence[Proposal] =
    if (value == null) D.invalid("canonical source proposal is absent") else value.toJsonStringC.flatMap(raw =>
      io.circe.parser.parse(raw).left.map(_.message).flatMap(GoalPhaseNativeMetadata.normalizePublicJson)
        .flatMap(_.as[Proposal].left.map(_.message)).fold(D.invalid(_), Consequence.success))
  def isProposal(value: Record): Boolean = value != null && value.toJsonStringC.toOption.exists(raw =>
    io.circe.parser.parse(raw).toOption.exists(_.hcursor.get[String]("schemaVersion").contains(proposalSchema)))
  def choicesFor(v: R.Target): Vector[String] =
    Vector(if (v.canonicalOrigin.exists(_.pending.nonEmpty)) pendingChoice else choice, "Defer")

  /** This representation deliberately has no file identities or changed entries. */
  def guardValid(v: R.Guard): Boolean = v != null && _root(v.root) &&
    GitBranchName.parseC(v.branch).isSuccess && Vector(v.head, v.committedTree, v.indexTree).forall(_oid) &&
    v.indexTree == v.committedTree && v.dirty == Vector.empty && v.selectedFiles == Vector.empty &&
    v.excludedFiles == Vector.empty && v.selectedEntries == Vector.empty && v.excludedEntries == Vector.empty &&
    v.committedInventory != null && v.committedInventory.exists(i => i != null && i.selected != null &&
      i.selected.nonEmpty && i.excluded != null &&
      (i.selected ++ i.excluded).forall(R.committedEntryValid) &&
      (i.selected ++ i.excluded).map(_.path).distinct.size == i.selected.size + i.excluded.size)
  def guardMatches(v: R.Guard, binding: D.Binding, consumption: R.ConsumptionRecord,
    exclusions: Vector[String]): Boolean = guardValid(v) && D.bindingValid(binding) &&
    R.consumptionValid(consumption) && v.root == binding.root && v.branch == binding.branch &&
    v.head == binding.base.head && v.committedTree == binding.base.tree && v.committedInventory.exists(i =>
      i.selected.map(_.path) == binding.paths && i.excluded.map(_.path) == exclusions &&
      i.selected.map(e => R.ExpectedMode(e.path, e.entry.map(_.mode).getOrElse("000000"))) == consumption.expectedModes)

  def originValid(v: Origin): Boolean = _origin_failure(v).isEmpty
  private def _origin_failure(v: Origin): Option[String] =
    if (v == null) Some("native-origin.value") else _first_failure(Vector(
      _clause("native-origin.component")(_exact(v.componentInstance)),
      _clause("native-origin.participant")(_exact(v.participant)),
      _clause("native-origin.controller")(v.controller != null),
      _clause("native-origin.plan")(O.validatePlanC(v.controller.plan).isSuccess),
      _clause("native-origin.stage")(Set("Implementation", "Repair")(v.controller.stage)),
      _clause("native-origin.cursor")(v.controller.cursor == v.controller.completed.size &&
        v.controller.cursor < v.controller.plan.steps.size),
      _clause("native-origin.active-candidate")(v.controller.candidate.isEmpty),
      _clause("native-origin.checks")(v.controller.checksReceipt.isEmpty),
      _clause("native-origin.review")(v.controller.reviewEvidence.isEmpty),
      _clause("native-origin.closure")(v.controller.closureIntent.isEmpty),
      _clause("native-origin.decision")(v.controller.decision.isEmpty),
      _clause("native-origin.repository")(v.controller.repositoryReceipt.isEmpty),
      _clause("native-origin.decision-source")(v.controller.decisionSource.isEmpty),
      _clause("native-origin.document-decision")(!v.controller.documentDecision),
      _clause("native-origin.phase-candidate")(v.controller.phaseReviewedCandidate.isEmpty),
      _clause("native-origin.phase-recording")(v.controller.phaseRecordingPaths.isEmpty),
      _clause("native-origin.candidate-parent")(v.controller.candidateParent.isEmpty),
      _clause("native-origin.prefix.acceptance")(v.controller.completed.forall(O.validStepAcceptance)),
      _clause("native-origin.prefix.steps")(v.controller.completed.map(_.step) == v.controller.plan.steps.take(v.controller.cursor).map(_.identity)),
      _clause("native-origin.approvals")(v.approvals != null && v.approvals.forall(D.approvalValid)),
      _clause("native-origin.approval-identity")(v.approvals.map(_.decision).distinct.size == v.approvals.size),
      _clause("native-origin.original-direction")(v.originalDirection != null && v.originalDirection.forall(R.directionValid)),
      _clause("native-origin.previous-direction")(v.previousDirection != null && v.previousDirection.forall(R.directionValid)),
      _clause("native-origin.direction-relation")(v.previousDirection.isEmpty || v.originalDirection.nonEmpty),
      _clause("native-origin.pending")(v.pending != null && v.pending.forall(p => p != null && R.referenceValid(p.continuation) && _revision(p.entityRevision))),
      _clause("native-origin.historical-failure")(v.historicalFailure != null && v.historicalFailure.forall(failureValid)),
      _clause("native-origin.selection")(proposalValid(v.selection)),
      _clause("native-origin.worker-lifecycle")(v.selection.workerLifecycle.nonEmpty == v.pending.nonEmpty),
      _clause("native-origin.primary-root")(_root(v.primaryRoot)),
      _clause("native-origin.process-policy")(_exact(v.processPolicy)),
      _clause("native-origin.lineage")(v.baseLineage != null && v.baseLineage.nonEmpty),
      _clause("native-origin.lineage.commits")(v.baseLineage.forall(c => c != null && _oid(c.identity) && _oid(c.tree) &&
        c.parents != null && c.parents.forall(_oid) && c.parents.distinct == c.parents && !c.parents.contains(c.identity))),
      _clause("native-origin.lineage.distinct")(v.baseLineage.map(_.identity).distinct.size == v.baseLineage.size),
      _clause("native-origin.lineage.parents")(v.baseLineage.sliding(2).forall(pair => pair.size == 1 || pair.head.parents.contains(pair.last.identity)))))

  def publicationMatches(v: P.PublicationObservation, binding: D.Binding): Boolean =
    P.observationValid(v) && D.bindingValid(binding) && {
      val request = v.result.publication.request
      request.target.component == binding.dependency.identity && request.source.clean &&
        request.source.head == binding.base.head && request.source.tree == binding.base.tree &&
        io.circe.parser.parse(v.publisherProcessPolicy).toOption.exists(policy =>
          policy.hcursor.downField("processPlan").get[Vector[Json]]("workspaces").toOption.exists(workspaces => {
            val selected = workspaces.filter(_.hcursor.get[String]("identity").contains(request.target.workspace))
            selected.size == 1 && selected.head.hcursor.get[String]("root").contains(binding.root)
          }))
    }
  private def _previous(v: R.Target, origin: Origin): D.Binding =
    origin.previousDirection.orElse(origin.originalDirection).map(_.target.binding).getOrElse(v.predecessor.binding)
  private def _boundary_valid(v: R.Target, o: Origin): Boolean = _boundary_failure(v, o).isEmpty
  private def _boundary_failure(v: R.Target, o: Origin): Option[String] = {
    val old = _previous(v, o)
    val oldpaths = (if (o.originalDirection.isEmpty)
      v.predecessor.acceptedPrefix.toVector.flatMap(_.paths) ++ old.paths else old.paths).distinct
    val inherited = v.predecessor.approvals ++ v.ancestors.flatMap(_.capture.admission.approvals)
    val added = o.approvals.filter(a => a.step == v.step && !inherited.exists(_.decision == a.decision))
    val paths = oldpaths ++ added.flatMap(_.proposal.paths).filterNot(oldpaths.contains).distinct
    val coverage = (inherited ++ added).groupBy(_.decision).values.map(_.head).toVector
    val original = o.originalDirection
    val oldcoverage = o.previousDirection.orElse(original).map(_.target.proposal.coverage)
    _first_failure(Vector(
      _clause("scope-boundary.revision")(v.binding.revision > old.revision),
      _clause("scope-boundary.paths")(v.binding.paths == paths),
      _clause("scope-boundary.component")(v.binding.dependency.identity == old.dependency.identity),
      _clause("scope-boundary.repository")(v.binding.dependency.repository == old.dependency.repository),
      _clause("scope-boundary.binding-copy")(v.binding.copy(revision = old.revision, root = old.root, branch = old.branch,
        base = old.base, paths = old.paths, dependency = old.dependency) == old),
      _clause("scope-boundary.added-approvals")(added.forall(a => a.proposal.requester == old.requester &&
        a.proposal.dependency.identity == old.dependency.identity && a.proposal.dependency.repository == old.dependency.repository &&
        inherited.map(_.proposal.usageContract).distinct == Vector(a.proposal.usageContract))),
      _clause("scope-boundary.coverage.approvals")(v.proposal.coverage.map(_.approval).toSet == coverage.map(_.decision).toSet),
      _clause("scope-boundary.coverage.obligations")(coverage.forall(a => v.proposal.coverage.contains(
        R.Coverage(a.decision, a.proposal.acceptance, old.checks.map(_.identity))))),
      _clause("scope-boundary.coverage.prefix")(oldcoverage.forall(prefix => v.proposal.coverage.startsWith(prefix))),
      _clause("scope-boundary.inherited-exclusions")(o.previousDirection.orElse(original).forall(d =>
        v.proposal.excludedPaths == d.target.proposal.excludedPaths)),
      _clause("scope-boundary.original-direction")(original.forall(d => d.target.sessionId == v.sessionId && d.target.predecessor == v.predecessor &&
        d.target.ancestors == v.ancestors && d.target.primaryBase == v.primaryBase && d.target.step == v.step &&
        d.target.plan.identity == v.plan.identity && d.target.plan.phase == v.plan.phase &&
        d.target.plan.repository == v.plan.repository && d.target.plan.steps.take(d.target.completed.size) ==
          v.plan.steps.take(d.target.completed.size) && v.completed.startsWith(d.target.completed))),
      _clause("scope-boundary.previous-direction")(o.previousDirection.forall(d => d.target.sessionId == v.sessionId &&
        d.target.predecessor == v.predecessor && d.target.step == v.step && d.target.primaryBase == v.primaryBase))))
  }
  def targetValid(v: R.Target): Boolean = _target_failure(v).isEmpty

  /** One ordered lazy validation path serves the Boolean codec contract and the
    * generated-target diagnostic. Labels confer no source or execution authority.
    */
  private def _target_failure(v: R.Target): Option[String] =
    if (v == null) Some("native-origin.target")
    else if (v.canonicalOrigin == null) Some("native-origin.field")
    else v.canonicalOrigin match {
      case None => Some("native-origin.missing")
      case Some(o) =>
        val clauses: Vector[() => Option[String]] = Vector(
          () => _origin_failure(o),
          _clause("native-origin.schema")(v.schemaVersion == R.canonicalSchema),
          _clause("native-origin.discriminators")(v.runtime == None && v.sourceSuccession == None &&
            v.exclusionSuccession == None && v.committedBaseSuccession == None && v.waitOrigin == None &&
            v.primaryFailureOrigin == None && v.initialOrigin == None && v.publishedOrigin == None),
          _clause("native-origin.predecessor-guard")(v.predecessorGuard == o.originalDirection.flatMap(_.target.predecessorGuard)),
          _clause("native-origin.frontier")(_exact(v.sessionId) && v.questionIdentity == "" &&
            _revision(v.sourceRevision) && v.pendingRevision == v.sourceRevision),
          _clause("native-plan")(O.validatePlanC(v.plan).isSuccess && v.plan == o.controller.plan &&
            v.primaryBase == o.controller.base),
          _clause("prefix.completed")(v.completed == o.controller.completed),
          _clause("native-plan.generation")(v.generation == o.controller.generation && v.generation > 0),
          _clause("native-plan.step")(v.plan.steps.lift(o.controller.cursor).exists(_.identity == v.step)),
          _clause("native-plan.repair-progress")(GoalPhaseRepairProgress.validateC(v.repairProgress).isSuccess &&
            v.repairProgress.limit == v.plan.repairLimit && v.repairProgress.automaticCount == o.controller.repairs &&
            v.repairProgress.ordinaryCount == o.controller.ordinaryRepairs),
          _clause("standing-admission.requesting-result")(R.referenceValid(v.requestingResult)),
          _clause("standing-admission")(D.admissionValid(v.predecessor)),
          _clause("standing-admission.approvals")(v.predecessor.approvals.forall(o.approvals.contains)),
          _clause("prefix.ancestors")(v.ancestors != null && v.ancestors.forall(R.ancestorValid)),
          _clause("prefix.ancestor-context")(v.ancestors.forall(a => a.capture.session == v.sessionId &&
            a.capture.primaryBase == v.primaryBase && v.completed.exists(_.step == a.capture.step) &&
            BigInt(a.closureRevision) <= BigInt(v.sourceRevision))),
          _clause("scope-boundary.proposal")(R.proposalValid(v.proposal) && v.proposal == o.selection.source),
          _clause("scope-boundary.binding")(D.bindingValid(v.binding)),
          _clause("scope-boundary.binding-reference")(v.proposal.binding == ContextReference(v.binding.identity, v.binding.revision.toString)),
          _clause("scope-boundary.standing-identity")(v.binding.identity == v.predecessor.binding.identity),
          _clause("scope-boundary.requester")(v.binding.requester == v.predecessor.binding.requester),
          _clause("scope-boundary.step")(v.binding.step == v.step),
          _clause("scope-boundary.source-root")(v.binding.root == v.proposal.consumption.sourceRoot),
          _clause("scope-boundary.primary-workspace")(v.binding.requester.workspace == v.proposal.consumption.primaryWorkspace),
          _clause("scope-boundary.exclusions")(v.proposal.excludedPaths == v.proposal.consumption.nonInputExclusions),
          _clause("scope-boundary.disjoint-paths")(v.binding.paths.toSet.intersect(v.proposal.excludedPaths.toSet).isEmpty),
          () => _boundary_failure(v, o),
          _clause("committed-guard")(guardMatches(v.preStageGuard, v.binding, v.proposal.consumption, v.proposal.excludedPaths)),
          _clause("publication")(publicationMatches(o.selection.publication, v.binding)),
          _clause("programs.policy")(R.programsValid(v.plan, v.step, v.binding, v.proposal.consumption, o.processPolicy, o.primaryRoot)),
          _clause("programs.coverage")(v.proposal.consumption.programs.map(_.check).toSet ==
            (v.plan.steps(o.controller.cursor).checks ++ v.binding.checks.map(_.identity)).toSet),
          _clause("programs.workspace")(v.proposal.consumption.programs.filter(p => v.binding.checks.exists(_.identity == p.check))
            .forall(_.workspace == v.binding.dependency.workspace)),
          _clause("lineage.selected-commit")(o.baseLineage.head.identity == v.binding.base.head),
          _clause("lineage.selected-tree")(o.baseLineage.head.tree == v.binding.base.tree),
          _clause("lineage.previous-commit")(o.baseLineage.last.identity == _previous(v, o).base.head),
          _clause("lineage.previous-tree")(o.baseLineage.last.tree == _previous(v, o).base.tree),
          _clause("pending")(o.pending.forall(p => p.continuation.revision == v.pendingRevision &&
            p.request.instanceIdentity == v.sessionId && p.request.selectedPlan == v.plan &&
            p.request.originalBase == v.primaryBase && p.request.completed == v.completed &&
            p.request.generation == v.generation && p.request.stepIdentity.contains(v.step) &&
            p.request.kind == (if (o.controller.stage == "Implementation") "ImplementStep" else "RepairStep") &&
            o.historicalFailure.forall(f => p.request.checkFailure.contains(f.context)))),
          _clause("historical-failure")(o.historicalFailure.forall(f => o.controller.stage == "Repair" &&
            f.candidate.identity == v.sessionId && BigInt(f.validation.revision) <= BigInt(v.sourceRevision) &&
            (o.pending.isEmpty || BigInt(f.validation.revision) < BigInt(v.sourceRevision)) &&
            f.context.validation.sourceRevision.toLongOption.exists(_ < v.sourceRevision.toLong) &&
            f.context.validation.step == v.step)))
        _first_failure(clauses)
    }

  /** Fixed admitted TreeEntries selection; no managed file read or digest. */
  def observeC(core: ActionCall.Core, binding: D.Binding, exclusions: Vector[String]): Consequence[R.Guard] = {
    def _git_[A](operation: GitOperation[A]): Consequence[A] = E.executeGitC(core, operation,
      binding.dependency.workspace).flatMap(_.valueC)
    def _native_(): Consequence[(String, G.Observed, String, Vector[GitSelectedTreeEntry])] = for {
      actualroot <- _git_(GitOperation.Root)
      observed <- G.observeInWorkspaceC(core, binding.dependency.workspace)
      index <- _git_(GitOperation.IndexTree)
      commit <- GitObjectId.parseC(binding.base.head)
      metadata <- _git_(GitOperation.CommitMetadata(commit))
      _ <- _check(metadata.commit == commit && metadata.tree.value == binding.base.tree,
        "canonical binding is not its exact native commit/tree")
      selection <- GitSelectionId.parseC(R.committedInventorySelection)
      entries <- _git_(GitOperation.TreeEntries(metadata.tree, selection))
    } yield (actualroot, observed, index.value, entries)
    for {
      _ <- _check(D.bindingValid(binding) && exclusions != null && exclusions.distinct == exclusions &&
        exclusions.forall(RepositorySyncNativeConflictInput.relative) &&
        binding.paths.toSet.intersect(exclusions.toSet).isEmpty, "canonical inventory is invalid")
      before <- _native_()
      after <- _native_()
      _ <- _check(before == after && before._4.map(_.path.value) == binding.paths ++ exclusions,
        "canonical observation changed or fixed committed inventory differs from the declaration")
      entries = before._4.map(e => R.CommittedEntry(e.path.value, e.entry.map(v => R.CommittedObject(v.mode, v.blob.value))))
      guard = R.Guard(before._1, before._2.branch, before._2.head, before._2.tree, before._3,
        before._2.dirty.map(d => R.Dirty(d.index.toString, d.worktree.toString, d.path, d.renamedFrom)),
        Vector.empty, Vector.empty, Vector.empty, Vector.empty,
        Some(R.CommittedInventory(entries.take(binding.paths.size), entries.drop(binding.paths.size))))
      _ <- _check(guardValid(guard) && guard.root == binding.root && guard.branch == binding.branch &&
        guard.head == binding.base.head && guard.committedTree == binding.base.tree,
        "canonical source requires its exact clean committed root, branch, HEAD, index and tree")
    } yield guard
  }
  private def _ancestry_c(core: ActionCall.Core, binding: D.Binding, previous: O.Snapshot): Consequence[Vector[O.HistoryCommit]] = {
    def _walk_(queue: Vector[Vector[String]], seen: Map[String, O.HistoryCommit]): Consequence[Vector[O.HistoryCommit]] =
      queue.headOption match {
        case None => D.invalid("canonical commit does not descend from the effective previous native base")
        case Some(path) => for {
          id <- GitObjectId.parseC(path.last)
          metadata <- E.executeGitC(core, GitOperation.CommitMetadata(id), binding.dependency.workspace).flatMap(_.valueC)
          _ <- _check(metadata.commit == id, "canonical ancestry crossed its selected commit")
          value = O.HistoryCommit(id.value, metadata.tree.value, metadata.parents.map(_.value))
          cache = seen.updated(value.identity, value)
          result <- if (value.identity == previous.head) Consequence.success(path.map(cache))
            else _walk_(queue.tail ++ value.parents.filterNot(p => cache.contains(p) || path.contains(p) ||
              queue.tail.exists(_.last == p)).map(path :+ _), cache)
        } yield result
      }
    _walk_(Vector(Vector(binding.base.head)), Map.empty).flatMap(lineage =>
      _check(lineage.head.tree == binding.base.tree && lineage.last.tree == previous.tree,
        "canonical ancestry must prove both exact native commit/tree pairs").map(_ => lineage))
  }
  private def _reference(from: InstanceRecord): WorkflowMechanicalTransition.Reference =
    WorkflowMechanicalTransition.Reference(from.identity.value + ":" + causation + from.revision.value, "1")
  private def _cause(source: Source): String = causation + source.pending.map(_.continuation.identity).getOrElse(source.from.revision.value.toString)

  private def _target_for_c(source: Source, d: F.Declaration, core: ActionCall.Core, proposal: Proposal): Consequence[R.Target] = for {
    _ <- _check(proposalValid(proposal) && source.from == source.session.current,
      "canonical source requires its actual latest native frontier and closed proposal")
    _ <- source.controller.adoptCanonicalDependencySourceC(ContextReference("canonical-description-eligibility", "1"))
    _ <- _check(proposal.workerLifecycle.nonEmpty == source.pending.nonEmpty,
      "pending work requires explicit prior-worker termination and acceptance of unknown external effects")
    binding <- d.dependencySourceRevisions.filter(b => ContextReference(b.identity, b.revision.toString) == proposal.source.binding) match {
      case Vector(value) => Consequence.success(value)
      case _ => D.invalid("canonical source requires one exact independently declared binding identity/revision")
    }
    _ <- _check(d.dependencySourceConsumptions.contains(proposal.source.consumption), "canonical consumption is not declared by this host")
    guard <- observeC(core, binding, proposal.source.excludedPaths)
    old = source.previousDirection.orElse(source.originalDirection).map(_.target.binding).getOrElse(source.predecessor.binding)
    lineage <- _ancestry_c(core, binding, old.base)
    process <- GoalPhasePrerequisitePublicationRecovery.processPolicyC(d, core)
    primaryroot <- G.rootC(core)
    _ <- _check(primaryroot == d.target.root.toString, "canonical selection crosses the primary root")
    origin = Origin(core.component.get.core.instanceId.canonicalKey, source.session.participantIdentity,
      GoalPhaseCandidateRecovery.controllerSnapshot(source.controller), source.approvals,
      source.originalDirection, source.previousDirection, source.pending, proposal, primaryroot, process, lineage, source.historicalFailure)
    target = R.Target(R.canonicalSchema, source.session.current.identity.value, "", source.from.revision.value.toString,
      source.from.revision.value.toString, source.controller.plan, source.controller.plan.steps(source.controller.cursor).identity,
      source.controller.generation, source.controller.base, source.controller.completed,
      GoalPhaseRepairProgress.snapshot(source.controller), source.requestingResult, source.predecessor, source.ancestors,
      proposal.source, binding, source.originalDirection.flatMap(_.target.predecessorGuard), guard, canonicalOrigin = Some(origin))
    _ <- _target_failure(target) match {
      case None => Consequence.unit
      case Some(clause) => D.invalid("canonical selection crosses actual source, native Plan, approvals, programs or qualification [clause=" + clause + "]")
    }
  } yield target

  def describeC(source: Source, d: F.Declaration, core: ActionCall.Core, raw: Record):
    Consequence[V.GoalPhaseDependencySourceRevisionDescriptionResult] = for {
    proposal <- proposalC(raw)
    target <- _target_for_c(source, d, core, proposal)
  } yield V.GoalPhaseDependencySourceRevisionDescriptionResult(target.sessionId, "direction-required", R.message,
    core.component.get.core.instanceId.canonicalKey, R.targetRecord(target), choicesFor(target), R.adoptionOperation)

  def currentC(direction: R.Direction, d: F.Declaration, core: ActionCall.Core): Consequence[Unit] =
    _current_c(direction, d, core, None)
  /** The caller supplies an actual native-walked host Direction. This relation
    * carries source-policy compatibility through later ordinary review/closure;
    * it grants no further publication and never compares a closed primary HEAD.
    */
  private[admission] def currentC(direction: R.Direction, d: F.Declaration, core: ActionCall.Core,
    host: GoalPhasePrerequisitePublicationHostRecovery.Saved): Consequence[Unit] =
    _current_c(direction, d, core, Some(host))
  private def _current_c(direction: R.Direction, d: F.Declaration, core: ActionCall.Core,
    host: Option[GoalPhasePrerequisitePublicationHostRecovery.Saved]): Consequence[Unit] = for {
    _ <- _check(R.directionValid(direction) && direction.target.canonicalOrigin.nonEmpty,
      "canonical freshness requires its actual retained direction")
    target = direction.target
    _ <- _check(d.dependencySourceRevisions.contains(target.binding) &&
      d.dependencySourceConsumptions.contains(target.proposal.consumption), "canonical host binding changed")
    guard <- observeC(core, target.binding, target.proposal.excludedPaths)
    process <- GoalPhasePrerequisitePublicationRecovery.processPolicyC(d, core)
    expected <- host match {
      case None => Consequence.success(target.canonicalOrigin.get.processPolicy)
      case Some(saved) =>
        val recovery = saved.direction.target
        val correction = recovery.canonicalProgramCorrection
        _check(saved.record != null && saved.direction.choice == GoalPhasePrerequisitePublicationHostRecovery.authorize &&
          GoalPhasePrerequisitePublicationHostRecovery.isCanonicalTargetSchema(recovery.schemaVersion) &&
          GoalPhasePrerequisitePublicationHostRecovery.directionValid(saved.direction) &&
          recovery.candidate.dependencySourceCapture.exists(_.direction == direction) &&
          correction.exists(c => c.oldProcessPolicy == target.canonicalOrigin.get.processPolicy &&
            GoalPhaseCanonicalPublicationProgramCorrection.matches(c, recovery.candidate, recovery.failed.request.target)),
          "canonical source policy correction lacks its exact native host Direction relation")
          .map(_ => correction.get.correctedProcessPolicy)
    }
    _ <- _check(guard == target.preStageGuard,
      "canonical source or actual consumption policy changed")
    basis <- GoalPhaseCanonicalCheckConsumption.basisC(direction)
    _ <- GoalPhaseCanonicalCheckConsumption.policyC(basis, expected, process,
      d.dependencySourceConsumptions, d.phaseValidationPolicy)
  } yield ()

  def adoptC(source: Source, d: F.Declaration, core: ActionCall.Core, input: V.GoalPhaseDependencySourceRevisionAdoptionInput):
    Consequence[V.GoalPhaseDependencySourceRevisionAdoptionResult] = for {
    supplied <- R.targetC(input.target)
    _ <- _check(supplied.canonicalOrigin.nonEmpty && input.sessionId == supplied.sessionId &&
      choicesFor(supplied).contains(input.choice) && _exact(input.rationale) && input.reference != null,
      "canonical direction input is incomplete or crossed")
    result <- if (input.choice == "Defer") Consequence.success(V.GoalPhaseDependencySourceRevisionAdoptionResult(
      input.sessionId, false, "deferred", "No canonical source direction was stored")) else for {
      actual <- _target_for_c(source, d, core, supplied.canonicalOrigin.get.selection)
      _ <- _check(actual == supplied, "canonical source target is stale or changed")
      installation <- d.dependencySourceRevision.map(Consequence.success).getOrElse(
        Consequence.securityPermissionDenied("canonical source requires independent Human evidence"))
      _ <- installation.validateC
      human = ContextReference(input.reference.identity, input.reference.revision)
      binding <- R.bindingC(actual.canonicalOrigin.get.componentInstance, actual, input.choice, input.rationale,
        source.session.participantIdentity, human)
      _ <- installation.evidence.admitC(core, human, binding)
      state <- source.controller.adoptCanonicalDependencySourceC(human)
      direction = R.Direction(actual, human, source.session.participantIdentity, input.rationale, Some(input.choice))
      entry = F.history(source.from, "phase." + state.stage.toString, _cause(source))
      _ <- source.pending match {
        case None => for {
          adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
          _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(source.from), source.from,
            source.from.revision, entry, TypedValue(codec.typeIdentity, Checkpoint(direction))),
            ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
        } yield ()
        case Some(pending) => for {
          adapter <- source.session.storage.bindWorkOrderSupersessionC(F.completionOperation, S.inputCodec, codec)
          observed <- adapter.inspectC(ContinuationIdentity(pending.continuation.identity), source.from.identity)(using core.executionContext)
            .flatMap(_.map(Consequence.success).getOrElse(D.invalid("canonical pending work is absent")))
          _ <- _check(observed.current == source.from && observed.entityRevision.value.toString == pending.entityRevision &&
            observed.continuation.status == ContinuationRuntimePersistence.Status.Available && observed.continuation.claimId.isEmpty &&
            observed.issued.current.isInstanceOf[WorkflowContinuation.WorkOrder[?]], "canonical pending work is live, claimed or changed")
          _ <- adapter.executeC(ContinuationIdentity(pending.continuation.identity), source.from.revision,
            observed.entityRevision, source.from, entry, TypedValue(codec.typeIdentity, Checkpoint(direction)))(using core.executionContext)
        } yield ()
      }
    } yield V.GoalPhaseDependencySourceRevisionAdoptionResult(input.sessionId, true, "dependency-source-revision-adopted", R.message)
  } yield result

  /** Read-only reconstruction uses historical native facts, never current bytes. */
  def retainedC(source: Source, d: F.Declaration, core: ActionCall.Core): Consequence[Option[Saved]] = for {
    found <- source.pending match {
      case None => for {
        adapter <- source.session.storage.bindMechanicalC(F.advanceOperation, codec)
        retained <- adapter.loadRetainedC(_reference(source.from))(using core.executionContext)
      } yield retained.map(v => (v.receipt.payload.value.direction, v.receipt.next))
      case Some(pending) => for {
        adapter <- source.session.storage.bindWorkOrderSupersessionC(F.completionOperation, S.inputCodec, codec)
        retained <- adapter.loadRetainedC(ContinuationIdentity(pending.continuation.identity), source.from.identity)(using core.executionContext)
        selected <- retained match {
          case None => Consequence.success(Option.empty[(R.Direction, InstanceRecord)])
          case Some(v) => GoalPhaseNativeHistory.prefixC(source.session.current, v.receipt.nextRevision)
            .map(record => Some((v.receipt.payload.direction, record)))
        }
      } yield selected
    }
    result <- found match {
      case None => Consequence.success(Option.empty[Saved])
      case Some((direction, record)) => for {
        _ <- _check(R.directionValid(direction), "retained canonical direction is invalid")
        t = direction.target
        o = t.canonicalOrigin.get
        _ <- _check(t.sessionId == source.from.identity.value && t.sourceRevision == source.from.revision.value.toString &&
          t.plan == source.controller.plan && t.primaryBase == source.controller.base && t.completed == source.controller.completed &&
          t.generation == source.controller.generation && t.repairProgress == GoalPhaseRepairProgress.snapshot(source.controller) &&
          t.requestingResult == source.requestingResult && t.predecessor == source.predecessor && t.ancestors == source.ancestors &&
          o.controller == GoalPhaseCandidateRecovery.controllerSnapshot(source.controller) && o.approvals == source.approvals &&
          o.originalDirection == source.originalDirection && o.previousDirection == source.previousDirection && o.pending == source.pending &&
          o.historicalFailure == source.historicalFailure &&
          o.componentInstance == core.component.get.core.instanceId.canonicalKey && o.participant == source.session.participantIdentity &&
          direction.participant == source.session.participantIdentity, "canonical history crosses its actual native predecessor")
        state <- source.controller.adoptCanonicalDependencySourceC(direction.directive)
        expected <- source.from.appendC(source.from.revision, F.history(source.from, "phase." + state.stage.toString, _cause(source)))
        _ <- _check(record == expected, "canonical history is not the actual contiguous native transition")
      } yield Some(Saved(direction, state, record))
    }
  } yield result
}
