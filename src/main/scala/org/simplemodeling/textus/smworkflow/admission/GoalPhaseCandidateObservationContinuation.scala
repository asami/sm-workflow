package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json, DecodingFailure}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.processexecution.{GitDiffParameters, GitObjectId, GitOperation}
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.protocol.Request
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.value as V
import GoalPhaseNativeRun as F
import GoalPhaseOrderedProgression as O
import GoalPhaseNativeCandidate as C
import GoalPhaseCandidateRecovery as R
import RepositorySyncManagedGitObservation as G
import GoalPhaseNativeCandidateBoundary as B
import GoalPhaseDependencyValidation as D
import D.given

/** Fresh independent Human direction after a separately authorized observation failed.
  * Historical uncertainty and every completed effect remain immutable.
  * @since   Oct. 22, 2026
  * @version Oct. 25, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseCandidateObservationContinuation {
  private given Encoder[ContextReference] = deriveEncoder[ContextReference]
  private given Decoder[ContextReference] = deriveDecoder[ContextReference]
  val purpose = "goal-phase-candidate-observation-continuation"
  val describeOperation = "describeGoalPhaseCandidateObservationContinuation"
  val continuationOperation = "continueGoalPhaseCandidateObservation"
  val choices = Vector("AuthorizeNewObservation", "ReviseWork", "Defer")
  def choicesFor(target: Target): Vector[String] =
    if (!validTarget(target)) Vector.empty
    else if (observationEligible(target)) Vector("AuthorizeNewObservation", "Defer")
    else if (target.controller.generation < Long.MaxValue) Vector("ReviseWork", "Defer")
    else Vector("Defer")
  val message = "ALL original and separately authorized failed observations remain UNKNOWN with termination-unconfirmed. " +
    "This description observes the current repository and index through typed Git; IndexTree/write-tree may write Git objects and index cache. " +
    "AuthorizeNewObservation grants one new remaining candidate observation on exactly this snapshot and selected diff budget, " +
    "never settlement, retry of an old intent, staging or source-history writes. A separate ordinary Advance first saves a distinct intent. " +
    "ReviseWork grants new semantic reconciliation of current scoped development changes in the same Step through a separate ordinary Advance, " +
    "preserving the index and all old effects. Changed requirements need the existing decision-needed planRevision Human flow. " +
    "Defer grants no observation or work. Engineering approval, earlier Submit and earlier recovery consent are not this fresh independent Human answer."

  final case class Dirty(index: String, worktree: String, path: String, renamedFrom: Option[String])
  object Dirty {
    given Encoder[Dirty] = deriveEncoder[Dirty]
    given Decoder[Dirty] = _closed(Set("index", "worktree", "path", "renamedFrom"), deriveDecoder[Dirty])
  }
  final case class RepositorySnapshot(root: String, branch: String, head: String, tree: String,
    dirty: Vector[Dirty], indexTree: String)
  object RepositorySnapshot {
    given Encoder[RepositorySnapshot] = deriveEncoder[RepositorySnapshot]
    given Decoder[RepositorySnapshot] = _closed(Set("root", "branch", "head", "tree", "dirty", "indexTree"), deriveDecoder[RepositorySnapshot])
  }
  final case class PriorObservation(directionSourceRevision: String, directionRecordRevision: String,
    intentIdentity: String, intentRevision: String, intentRecordRevision: String, intent: C.Payload,
    outcome: String, termination: String, dependencyPrefix: Vector[R.DependencyFact] = Vector.empty)
  object PriorObservation {
    given Encoder[PriorObservation] = deriveEncoder[PriorObservation].mapJson(_.mapObject(_.filter {
      case ("dependencyPrefix", value) => !value.asArray.exists(_.isEmpty)
      case _ => true
    }))
    private val _decoder: Decoder[PriorObservation] = deriveDecoder
    given Decoder[PriorObservation] = Decoder.instance { c =>
      val wire = if (c.downField("dependencyPrefix").focus.isEmpty) c.value.mapObject(_.add("dependencyPrefix", Json.arr())) else c.value
      _decoder(wire.hcursor).flatMap(p =>
        if (c.value.asObject.exists(_.keys.toSet == Set("directionSourceRevision", "directionRecordRevision", "intentIdentity",
          "intentRevision", "intentRecordRevision", "intent", "outcome", "termination") ++
          c.downField("dependencyPrefix").focus.map(_ => "dependencyPrefix"))) Right(p)
        else Left(DecodingFailure("Prior observation fields differ", c.history)))
    }
  }
  final case class Target(original: R.RecoveryTarget, controller: R.Controller,
    originalDirectionIdentity: String, originalDirectionRevision: String, originalDirectionRecordRevision: String,
    directionSourceRevision: String, frontierRevision: String, priorObservations: Vector[PriorObservation],
    maximumDiffBytes: Long, repository: RepositorySnapshot, dependencyRepository: Option[RepositorySnapshot] = None)
  object Target {
    given Encoder[Target] = deriveEncoder[Target].mapJson(_.mapObject(_.filter {
      case ("dependencyRepository", value) => !value.isNull
      case _ => true
    }))
    private val _decoder: Decoder[Target] = deriveDecoder
    given Decoder[Target] = Decoder.instance(c => _decoder(c).flatMap(t =>
      if (c.value.asObject.exists(_.keys.toSet == Set("original", "controller", "originalDirectionIdentity",
        "originalDirectionRevision", "originalDirectionRecordRevision", "directionSourceRevision", "frontierRevision",
        "priorObservations", "maximumDiffBytes", "repository") ++
        c.downField("dependencyRepository").focus.map(_ => "dependencyRepository")) &&
        !c.downField("dependencyRepository").focus.exists(_.isNull) && validTarget(t)) Right(t)
      else Left(DecodingFailure("Observation target fields or subject differ", c.history))))
  }
  final case class Direction(target: Target, reference: ContextReference, participant: String, rationale: String,
    choice: String = "AuthorizeNewObservation")
  object Direction {
    given Encoder[Direction] = deriveEncoder[Direction]
    given Decoder[Direction] = deriveDecoder[Direction]
  }
  trait EvidencePort {
    def admitC(core: ActionCall.Core, reference: ContextReference, binding: Json): Consequence[Unit]
  }
  final case class Installation(evidence: EvidencePort) {
    def validateC: Consequence[Unit] = if (evidence != null) Consequence.unit
      else Consequence.configurationInvalid("Candidate observation continuation requires an independent Human evidence port")
  }
  def targetRecord(value: Target): Record = GoalPhaseNativeMetadata.record(value.asJson)
  def targetC(value: Record): Consequence[Target] =
    if (value == null) invalid("target is absent") else value.toJsonStringC.flatMap(text =>
      io.circe.parser.parse(text).left.map(_.message).flatMap(decodePublicTarget)
        .fold(invalid(_), Consequence.success))

  /** Interpret public omissions without changing the exact durable target codec. */
  def decodePublicTarget(value: Json): Either[String, Target] = for {
    supplied <- Option(value).toRight("Public candidate observation target is absent")
    wire <- GoalPhaseNativeMetadata.normalizePublicJson(supplied)
    original <- R.decodePublicTarget(wire.hcursor.downField("original").focus.getOrElse(Json.Null))
    controller <- R.publicController(wire.hcursor.downField("controller").focus.getOrElse(Json.Null))
    repository <- _public_repository(wire.hcursor.downField("repository").focus.getOrElse(Json.Null))
    dependency <- wire.hcursor.downField("dependencyRepository").focus match {
      case None => Right(None)
      case Some(snapshot) => _public_repository(snapshot).map(Some(_))
    }
    _ <- R.publicMembers(wire, "priorObservations", false)(p => for {
      _ <- R.publicCandidatePayload(p.hcursor.downField("intent").focus.getOrElse(Json.Null))
      _ <- R.publicMembers(p, "dependencyPrefix", true)(fact =>
        R.publicCandidatePayload(fact.hcursor.downField("payload").focus.getOrElse(Json.Null)))
    } yield ())
    canonical = wire.mapObject(fields => {
      val required = fields.add("original", original.asJson).add("controller", controller.asJson)
        .add("repository", repository.asJson)
      dependency.fold(required)(snapshot => required.add("dependencyRepository", snapshot.asJson))
    })
    target <- decodeTarget(canonical)
  } yield target

  private val _public_dirty_decoder: Decoder[Dirty] = deriveDecoder[Dirty]
  private def _public_dirty(value: Json): Either[String, Dirty] = for {
    fields <- value.asObject.toRight("Public dirty entry must be an object")
    _ <- Either.cond(Set("index", "worktree", "path").subsetOf(fields.keys.toSet) &&
      fields.keys.forall(Set("index", "worktree", "path", "renamedFrom").contains) &&
      fields.values.forall(!_.isNull), (), "Public dirty fields are missing, unknown or null")
    dirty <- _public_dirty_decoder(value.hcursor).left.map(_.message)
  } yield dirty

  private def _public_repository(value: Json): Either[String, RepositorySnapshot] = for {
    _ <- R.publicNonNullObject(value, "repository snapshot")
    entries <- value.hcursor.get[Vector[Json]]("dirty").left.map(_.message)
    dirty <- entries.foldLeft[Either[String, Vector[Dirty]]](Right(Vector.empty))((result, item) =>
      for { previous <- result; decoded <- _public_dirty(item) } yield previous :+ decoded)
    repository <- value.mapObject(_.add("dirty", dirty.asJson)).as[RepositorySnapshot].left.map(_.message)
  } yield repository
  def decodeTarget(value: Json): Either[String, Target] = for {
    wire <- Option(value).toRight("Candidate observation continuation target is absent")
    target <- wire.as[Target].left.map(_.message)
    _ <- Either.cond(validTarget(target), (), "Candidate observation continuation target is incomplete or crossed")
  } yield target

  def validTarget(t: Target): Boolean = t != null && R.validTarget(t.original) &&
    t.controller == t.original.controller && t.priorObservations != null && t.priorObservations.nonEmpty &&
    Vector(t.controller.candidate, t.controller.checksReceipt, t.controller.reviewEvidence,
      t.controller.closureIntent, t.controller.decision, t.controller.repositoryReceipt,
      t.controller.decisionSource, t.controller.pendingRepair, t.controller.phaseReviewedCandidate,
      t.controller.candidateParent).forall(value => value != null && value.isEmpty) &&
    !t.controller.documentDecision && t.controller.phaseRecordingPaths != null && t.controller.phaseRecordingPaths.isEmpty &&
    t.priorObservations.size <= 64 && Vector(t.originalDirectionRecordRevision, t.directionSourceRevision,
      t.frontierRevision).forall(_revision) && t.originalDirectionRevision == "1" &&
    t.originalDirectionIdentity == t.original.sessionId + ":ordered-candidate-recovery:" + t.original.sourceRevision &&
    BigInt(t.originalDirectionRecordRevision) == BigInt(t.original.frontierRevision) + 1 &&
    t.priorObservations.zipWithIndex.forall { (p, i) =>
      p != null && Vector(p.directionSourceRevision, p.directionRecordRevision, p.intentRecordRevision).forall(_revision) &&
      p.directionSourceRevision == (if (i == 0) t.original.sourceRevision else t.priorObservations(i - 1).directionRecordRevision) &&
      BigInt(p.directionRecordRevision) == (if (i == 0) BigInt(t.originalDirectionRecordRevision)
        else BigInt(observationFrontier(t.priorObservations(i - 1))) + 1) &&
      p.intentIdentity == t.original.sessionId + ":ordered-candidate:" + p.directionRecordRevision + ":observation-intent" &&
      p.intentRevision == "1" && BigInt(p.intentRecordRevision) == BigInt(p.directionRecordRevision) + 1 &&
      p.intent == (if (i == 0) R.preparedPayload(t.original) else observationPayload(t.priorObservations(i - 1)))
        .copy(sourceRevision = p.directionRecordRevision) && observationPrefixValid(t.original, p) &&
      p.outcome == "UNKNOWN" && p.termination == "termination-unconfirmed"
    } && t.directionSourceRevision == t.priorObservations.last.directionRecordRevision &&
    t.frontierRevision == observationFrontier(t.priorObservations.last) &&
    BigInt(t.frontierRevision) < BigInt(Long.MaxValue) - 2 &&
    t.maximumDiffBytes > 0 && t.maximumDiffBytes <= GitDiffParameters.maximumBytes && validRepository(t.original, t.repository) &&
    t.dependencyRepository != null && (R.preparedPayload(t.original).dependencyAdmission match {
      case None => t.dependencyRepository.isEmpty
      case Some(admission) => t.dependencyRepository.exists(validDependencyRepository(admission, _))
    })

  def validRepository(original: R.RecoveryTarget, snapshot: RepositorySnapshot): Boolean = snapshot != null &&
    snapshot.root == original.root && snapshot.branch == original.branch &&
    Vector(snapshot.head, snapshot.tree, snapshot.indexTree).forall(_object) &&
    B.parentC(original.controller.tip, original.inspection.continuation).toOption.exists(p => p.head == snapshot.head && p.tree == snapshot.tree) &&
    snapshot.dirty != null && snapshot.dirty.forall(p => p != null && p.index != null && p.index.length == 1 && p.worktree != null &&
      p.worktree.length == 1 && RepositorySyncNativeConflictInput.relative(p.path) &&
      p.renamedFrom != null && p.renamedFrom.forall(RepositorySyncNativeConflictInput.relative)) &&
    snapshot.dirty.map(_.path).distinct.size == snapshot.dirty.size &&
    B.scopedDirty(snapshot.dirty.map(p => G.DirtyPath(p.index.head, p.worktree.head, p.path, p.renamedFrom)),
      original.inspection.selectedPaths, false)

  def observationFrontier(value: PriorObservation): String = value.dependencyPrefix.lastOption
    .map(_.recordRevision).getOrElse(value.intentRecordRevision)
  def observationPayload(value: PriorObservation): C.Payload = value.dependencyPrefix.lastOption.map(_.payload).getOrElse(value.intent)
  def preparedPayload(target: Target): C.Payload = observationPayload(target.priorObservations.last)
  def observationPrefixValid(original: R.RecoveryTarget, value: PriorObservation): Boolean =
    value.dependencyPrefix != null && (value.intent.dependencyAdmission match {
      case None => value.dependencyPrefix.isEmpty
      case Some(admission) if value.intent.dependencyStaged == admission.binding.paths =>
        val facts = value.dependencyPrefix
        (value.intent.dependency.nonEmpty && facts.isEmpty) || (value.intent.dependency.isEmpty && facts.size <= 2 &&
          facts.zipWithIndex.forall { (fact, index) =>
            val key = if (index == 0) "dependency-observation-intent" else "dependency-observation-result"
            fact != null && fact.key == key && fact.identity == original.sessionId + ":ordered-candidate:" +
              value.directionRecordRevision + ":" + key && fact.revision == "1" && _revision(fact.recordRevision) &&
              BigInt(fact.recordRevision) == BigInt(value.intentRecordRevision) + index + 1 && fact.payload != null &&
              C.codec.decode(C.codec.encode(fact.payload)).isRight &&
              (if (index == 0) fact.payload == value.intent else fact.payload.dependency.exists(c =>
                D.capturedForC(c, original.sessionId, original.controller.plan, original.inspection.step,
                  original.controller.generation, value.directionRecordRevision, original.controller.base, admission).isSuccess &&
                fact.payload == value.intent.copy(dependency = Some(c), dependencyOrigin = Some(D.CandidateOrigin(
                  original.sourceRevision, value.directionRecordRevision, fact.recordRevision)))))
          })
      case _ => false
    })
  def validDependencyRepository(admission: D.Admission, snapshot: RepositorySnapshot): Boolean =
    D.admissionValid(admission) && _valid_dependency_repository(admission.binding,
      GoalPhaseNativeDependencyValidation.cumulativePaths(admission), snapshot)
  def validDependencyRepository(binding: D.Binding, snapshot: RepositorySnapshot): Boolean =
    _valid_dependency_repository(binding, binding.paths, snapshot)
  private def _valid_dependency_repository(binding: D.Binding, owned: Vector[String], snapshot: RepositorySnapshot): Boolean = snapshot != null &&
    D.bindingValid(binding) && snapshot.root == binding.root && snapshot.branch == binding.branch &&
    snapshot.head == binding.base.head && snapshot.tree == binding.base.tree && _object(snapshot.indexTree) &&
    snapshot.dirty != null && snapshot.dirty.forall(p => p != null && p.index != null && p.index.length == 1 &&
      p.worktree != null && p.worktree.length == 1 && owned.contains(p.path) && p.renamedFrom != null &&
      p.renamedFrom.forall(owned.contains)) && snapshot.dirty.map(_.path).distinct.size == snapshot.dirty.size &&
    B.scopedDirty(snapshot.dirty.map(p => G.DirtyPath(p.index.head, p.worktree.head, p.path, p.renamedFrom)), owned, false)
  def observationEligible(target: Target): Boolean = validTarget(target) && R.observationAllowed(target.original) &&
    B.scopedDirty(target.repository.dirty.map(p => G.DirtyPath(p.index.head, p.worktree.head, p.path, p.renamedFrom)),
      target.original.inspection.selectedPaths, true) && target.dependencyRepository.forall(snapshot =>
        B.scopedDirty(snapshot.dirty.map(p => G.DirtyPath(p.index.head, p.worktree.head, p.path, p.renamedFrom)),
          GoalPhaseNativeDependencyValidation.cumulativePaths(R.preparedPayload(target.original).dependencyAdmission.get), true))

  def snapshot(root: String, observed: G.Observed, indexTree: String): RepositorySnapshot =
    RepositorySnapshot(root, observed.branch, observed.head, observed.tree,
      observed.dirty.map(p => Dirty(p.index.toString, p.worktree.toString, p.path, p.renamedFrom)), indexTree)

  /** Only description calls this typed Git observation. Recover/append/retained decoding never do. */
  def observeRepositoryC(core: ActionCall.Core, original: R.RecoveryTarget): Consequence[RepositorySnapshot] = for {
    root <- G.rootC(core)
    before <- G.observeC(core)
    index <- G.indexTreeC(core)
    parent <- B.parentC(original.controller.tip, original.inspection.continuation)
    objectid <- GitObjectId.parseC(parent.head)
    pending <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.PendingPaths(objectid)).flatMap(_.valueC)
    after <- G.observeC(core)
    afterindex <- G.indexTreeC(core)
    afterroot <- G.rootC(core)
    observed = snapshot(root, before, index)
    _ <- if (before == after && root == afterroot && index == afterindex && validRepository(original, observed) &&
        B.pathsOwned(pending.map(_.value), original.inspection.selectedPaths)) Consequence.unit
      else invalid("repository/index is unstable or crosses its retained parent, branch, root or ownership")
  } yield observed

  def bindingC(component: String, target: Target, choice: String, rationale: String,
    participant: String, reference: ContextReference): Consequence[Json] =
    if (!_exact(component) || !choicesFor(target).contains(choice) || !_text(rationale) ||
        participant != target.original.participant || reference == null || !_exact(reference.identity) || !_exact(reference.revision))
      invalid("Human binding requires the exact target, choice, rationale, participant and reference")
    else Consequence.success(Json.obj("componentInstance" -> component.asJson, "candidateObservationContinuation" -> Json.obj(
      "target" -> target.asJson, "answer" -> Json.obj("choice" -> choice.asJson, "rationale" -> rationale.asJson,
        "participant" -> participant.asJson, "reference" -> reference.asJson))))
  def selectBinding(value: Json): Either[String, Json] = for {
    wire <- Option(value).toRight("Candidate observation continuation binding is absent")
    component <- wire.hcursor.get[String]("componentInstance").left.map(_.message)
    targetwire <- wire.hcursor.downField("candidateObservationContinuation").get[Json]("target").left.map(_.message)
    target <- decodeTarget(targetwire)
    answer = wire.hcursor.downField("candidateObservationContinuation").downField("answer")
    choice <- answer.get[String]("choice").left.map(_.message)
    rationale <- answer.get[String]("rationale").left.map(_.message)
    participant <- answer.get[String]("participant").left.map(_.message)
    reference <- answer.get[ContextReference]("reference").left.map(_.message)
    binding <- bindingC(component, target, choice, rationale, participant, reference)
      .map[Either[String, Json]](Right(_)).fold(error => Left(error.toString), identity)
  } yield binding
  def admitC(d: F.Declaration, core: ActionCall.Core, target: Target,
    input: V.GoalPhaseCandidateObservationContinuationInput, participant: String): Consequence[Unit] = for {
    installation <- d.candidateObservationContinuation.map(Consequence.success).getOrElse(
      Consequence.securityPermissionDenied("Candidate observation continuation has no installed independent Human evidence provider"))
    _ <- installation.validateC
    binding <- bindingC(core.component.get.core.instanceId.canonicalKey, target, input.choice, input.rationale,
      participant, ContextReference(input.reference.identity, input.reference.revision))
    _ <- installation.evidence.admitC(core, ContextReference(input.reference.identity, input.reference.revision), binding)
  } yield ()

  val codec = new WorkflowResultJsonV1.PayloadCodec[Direction] {
    val typeIdentity = "GoalPhaseCandidateObservationContinuationDirection"
    def encode(value: Direction): Json = {
      val legacy = value.choice == "AuthorizeNewObservation" && value.target.original.inspection.dependencyAdmission.isEmpty
      Json.obj("schemaVersion" -> (if (legacy) "sm-workflow.goal-phase-candidate-observation-continuation.v1"
        else "sm-workflow.goal-phase-candidate-observation-continuation.v2").asJson,
        "payload" -> (if (legacy) value.asJson.mapObject(_.remove("choice")) else value.asJson))
    }
    def decode(value: Json): Either[String, Direction] = for {
      wire <- Option(value).toRight("Candidate observation continuation direction is absent")
      schema <- wire.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(wire.asObject.exists(_.keys.toSet == Set("schemaVersion", "payload")), (),
        "Candidate observation continuation envelope fields differ")
      payload <- wire.hcursor.get[Json]("payload").left.map(_.message)
      explicit = payload.hcursor.downField("choice").focus
      _ <- Either.cond(payload.asObject.exists(_.keys.toSet ==
        (Set("target", "reference", "participant", "rationale") ++ explicit.map(_ => "choice"))), (),
        "Candidate observation continuation payload fields differ")
      decoded <- schema match {
        case "sm-workflow.goal-phase-candidate-observation-continuation.v1" if explicit.isEmpty =>
          payload.mapObject(_.add("choice", "AuthorizeNewObservation".asJson)).as[Direction].left.map(_.message)
            .flatMap(d => Either.cond(d.target.original.inspection.dependencyAdmission.isEmpty, d, "Legacy direction cannot authorize a dependency subject"))
        case "sm-workflow.goal-phase-candidate-observation-continuation.v2" if explicit.exists(v => v == "ReviseWork".asJson || v == "AuthorizeNewObservation".asJson) =>
          payload.as[Direction].left.map(_.message)
        case _ => Left("Candidate observation continuation schema or explicit choice differs")
      }
      direction = decoded
      _ <- Either.cond(choicesFor(direction.target).contains(direction.choice) && direction.choice != "Defer" &&
        direction.participant == direction.target.original.participant &&
        direction.reference != null && _exact(direction.reference.identity) && _exact(direction.reference.revision) &&
        _text(direction.rationale), (), "Candidate observation continuation direction is incomplete or crossed")
    } yield direction
  }
  private def _reference(from: InstanceRecord) = WorkflowMechanicalTransition.Reference(from.identity.value +
    ":ordered-candidate-observation-continuation:" + from.revision.value, "1")
  private def _observation_reference(from: InstanceRecord, key: String) = WorkflowMechanicalTransition.Reference(
    from.identity.value + ":ordered-candidate:" + from.revision.value + ":" + key, "1")

  /** Pure retained facts, including the actual adjacent failed intent, determine the chain. */
  def currentTargetC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, original: R.RecoveryTarget, previous: Option[Target], maximumDiffBytes: Long,
    repository: RepositorySnapshot, dependencyRepository: Option[RepositorySnapshot] = None): Consequence[Target] = for {
    _ <- if (R.validTarget(original) && controller.stage == O.Stage.InspectCandidate &&
        R.controllerSnapshot(controller) == original.controller && session.participantIdentity == original.participant &&
        from.identity.value == original.sessionId && from.definition.workflowIdentity.value == original.definitionIdentity &&
        from.definition.workflowRevision.value == original.definitionRevision && d.target.root.toString == original.root &&
        d.target.localBranch == original.branch && d.candidatePaths == original.candidatePaths &&
        previous.forall(p => validTarget(p) && p.original == original) &&
        from.revision.value.toString == previous.map(p => (p.frontierRevision.toLong + 1).toString)
          .getOrElse((original.frontierRevision.toLong + 1).toString) &&
        from.causation.value == previous.map(p => "ordered-candidate-observation-continuation:" + p.directionSourceRevision)
          .getOrElse("ordered-candidate-recovery:" + original.sourceRevision)) Consequence.unit
      else invalid("original recovery, controller, definition, participant or direction lineage changed")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, C.codec)
    intent <- adapter.loadRetainedC(_observation_reference(from, "observation-intent"))(using core.executionContext)
      .flatMap(_.map(Consequence.success).getOrElse(invalid("the authorized observation has no retained unresolved intent")))
    record <- R.adjacentC(session.current, from, intent.receipt.next, "ordered-candidate:observation-intent")
    candidate <- adapter.loadRetainedC(_observation_reference(from, "candidate"))(using core.executionContext)
    _ <- if (candidate.isEmpty) Consequence.unit else invalid("the authorized observation already has a candidate successor")
    dependency <- R.dependencyPrefixC(session, core, from, record)
    prior = previous.toVector.flatMap(_.priorObservations) :+ PriorObservation(
      previous.map(_.directionSourceRevision).getOrElse(original.sourceRevision), from.revision.value.toString,
      _observation_reference(from, "observation-intent").identity, "1", record.revision.value.toString,
      intent.receipt.payload.value, "UNKNOWN", "termination-unconfirmed", dependency._2)
    target = Target(original, R.controllerSnapshot(controller),
      original.sessionId + ":ordered-candidate-recovery:" + original.sourceRevision, "1",
      (original.frontierRevision.toLong + 1).toString, from.revision.value.toString,
      dependency._1.revision.value.toString, prior, maximumDiffBytes, repository, dependencyRepository)
    _ <- if (validTarget(target)) Consequence.unit else invalid("prior observation chain, snapshot or budget is incomplete or crossed")
  } yield target

  def requireFrontierC(session: F.Session, target: Target): Consequence[Unit] =
    if (session.current.revision.value.toString == target.frontierRevision && session.current.suspension.isEmpty &&
        session.current.currentProgression.contains(ProgressionReference("phase.InspectCandidate"))) Consequence.unit
    else invalid("failed observation intent is no longer the exact current frontier")

  def appendC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    target: Target, reference: ContextReference, rationale: String, controller: O.State,
    choice: String): Consequence[Unit] = for {
    _ <- requireFrontierC(session, target)
    _ <- if (target.directionSourceRevision == from.revision.value.toString) Consequence.unit
      else invalid("direction source changed")
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    payload = Direction(target, reference, session.participantIdentity, rationale, choice)
    _ <- codec.decode(codec.encode(payload)).fold(invalid, _ => Consequence.unit)
    state <- _state_c(controller, from, payload)
    _ <- adapter.executeC(WorkflowMechanicalTransition.Intent(d.configuration, _reference(from), session.current,
      session.current.revision, F.history(session.current, "phase." + state.stage.toString,
        "ordered-candidate-observation-continuation:" + from.revision.value), TypedValue(codec.typeIdentity, payload)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield ()
  final case class Resumed(record: InstanceRecord, target: Target, controller: O.State, choice: String)
  private def _state_c(controller: O.State, from: InstanceRecord, direction: Direction): Consequence[O.State] =
    if (direction.choice == "AuthorizeNewObservation") Consequence.success(controller)
    else if (direction.choice == "ReviseWork") controller.acceptC(O.CandidateWorkRevisionAdmitted(
      ContextReference(_reference(from).identity, _reference(from).revision), direction.rationale))
    else invalid("retained direction choice is undeclared")
  /** Reconstruct accepted history with its captured paths; current admission stays strict. */
  private def _captured_target_c(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, original: R.RecoveryTarget, previous: Option[Target], captured: Target): Consequence[Target] = for {
    _ <- if (validTarget(captured) && captured.original == original && d.candidatePaths != null &&
        d.candidatePaths.distinct == d.candidatePaths &&
        d.candidatePaths.forall(path => path != null && RepositorySyncNativeConflictInput.relative(path)) &&
        d.candidatePaths.startsWith(captured.original.candidatePaths)) Consequence.unit
      else invalid("retained direction candidate policy is not a safe ordered extension of its captured original")
    actual <- currentTargetC(session, d.copy(candidatePaths = captured.original.candidatePaths), core, from,
      controller, original, previous, captured.maximumDiffBytes, captured.repository, captured.dependencyRepository)
    _ <- if (actual == captured) Consequence.unit else invalid("retained direction crosses its actual predecessor chain")
  } yield actual
  def retainedC(session: F.Session, d: F.Declaration, core: ActionCall.Core, from: InstanceRecord,
    controller: O.State, original: R.RecoveryTarget, previous: Option[Target]): Consequence[Option[Resumed]] = for {
    adapter <- session.storage.bindMechanicalC(F.advanceOperation, codec)
    retained <- adapter.loadRetainedC(_reference(from))(using core.executionContext)
    result <- retained match {
      case None => Consequence.success(Option.empty[Resumed])
      case Some(value) => for {
        direction <- codec.decode(codec.encode(value.receipt.payload.value)).fold(invalid, Consequence.success)
        actual <- _captured_target_c(session, d, core, from, controller, original, previous, direction.target)
        state <- _state_c(controller, from, direction)
        frontier <- GoalPhaseNativeHistory.prefixC(session.current, InstanceRevision(actual.frontierRevision.toLong))
        next <- GoalPhaseNativeHistory.prefixC(session.current, value.receipt.next.revision)
        _ <- if (next == value.receipt.next && next.identity == frontier.identity && next.definition == frontier.definition &&
            frontier.revision.value < Long.MaxValue && next.revision == frontier.revision.next &&
            next.history.dropRight(1) == frontier.history && next.causation.value ==
              "ordered-candidate-observation-continuation:" + from.revision.value &&
            next.lifecycle == frontier.lifecycle && next.suspension.isEmpty &&
            next.currentProgression.contains(ProgressionReference("phase." + state.stage.toString))) Consequence.unit
          else invalid("retained direction is noncontiguous or crosses its admitted choice progression")
        _ <- if (direction.choice != "ReviseWork" || d.candidatePaths == actual.original.candidatePaths ||
            session.current.revision.value > next.revision.value) Consequence.unit
          else invalid("pending revision work crosses its exact admitted candidate policy")
      } yield Some(Resumed(next, actual, state, direction.choice))
    }
  } yield result

  def operations(port: GoalPhaseNativeWorkContinuation.Port): Vector[spec.OperationDefinition] =
    Vector(new Operation(true, port), new Operation(false, port))
  private final class Operation(describe: Boolean, port: GoalPhaseNativeWorkContinuation.Port) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (describe)
      SmWorkflowComponent.GoalPhaseServiceService.DescribeGoalPhaseCandidateObservationContinuationOperation
      else SmWorkflowComponent.GoalPhaseServiceService.ContinueGoalPhaseCandidateObservationOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("GoalPhaseService") ||
          request.operation != (if (describe) describeOperation else continuationOperation)) invalid("Operation identity differs")
      else if (describe) NativeWorkflowOperationInput.textC(request, "sessionId").map(id => Action(request, id, None, port))
      else NativeWorkflowOperationInput.valueC[V.GoalPhaseCandidateObservationContinuationInput](request, "input").flatMap { input =>
        if (input == null || !_exact(input.sessionId) || input.reference == null || !choices.contains(input.choice) ||
            !_text(input.rationale) || !_exact(input.reference.identity) || !_exact(input.reference.revision))
          invalid("input requires an explicit fresh Human direction and reference")
        else Consequence.success(Action(request, input.sessionId, Some(input), port))
      }
  }
  private final case class Action(request: Request, sessionId: String,
    input: Option[V.GoalPhaseCandidateObservationContinuationInput], port: GoalPhaseNativeWorkContinuation.Port) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(sessionId))
        selected = core.copy(executionContext = context)
        result <- input match {
          case None => port.describeCandidateObservationContinuationC(sessionId, selected).map(NativeWorkflowOperationOutput.response(_))
          case Some(value) => port.continueCandidateObservationC(value, selected).map(NativeWorkflowOperationOutput.response(_))
        }
      } yield result
    }
  }
  private def _closed[A](keys: Set[String], decoder: Decoder[A]): Decoder[A] = Decoder.instance(c =>
    if (c.value.asObject.exists(_.keys.toSet == keys)) decoder(c)
    else Left(DecodingFailure("Observation continuation fields differ", c.history)))
  private def _revision(value: String): Boolean = value != null && value.toLongOption.exists(n => n > 0 && n.toString == value)
  private def _object(value: String): Boolean = value != null && value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")
  private def _exact(value: String): Boolean = value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') &&
    java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65536
  private def invalid[A](reason: String): Consequence[A] = Consequence.stateConflict("Candidate observation continuation: " + reason)
}
