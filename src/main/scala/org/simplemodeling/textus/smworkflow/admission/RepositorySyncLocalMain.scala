/**
 * @since   Nov. 17, 2026
 * @version Nov. 17, 2026
 */
package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.file.Path
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, ProcedureActionCall}
import org.goldenport.cncf.processexecution.{GitOperation, GitWorktree, GitObjectId}
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.config.{ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.configuration.ConfigurationValue
import cats.data.NonEmptyVector
import org.goldenport.protocol.spec as spec
import org.goldenport.record.{Record, RecordPresentable}
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target

/** Participant data of the existing RepositorySync Session. Native observations
  * select already admitted workspaces; paths never create an execution grant.
  */
private[smworkflow] object RepositorySyncLocalMain {
  val mappings = ComponentParameterKey.optional("sm-workflow.repository-sync.canonical-main-workspaces",
    ComponentParameterDecoder[Map[String, String]] {
      case ConfigurationValue.StringValue(raw) => io.circe.parser.parse(raw).left.map(_.message)
        .flatMap(_.as[Map[String, String]].left.map(_.message)).fold(invalid, values =>
          if (values.forall((source, main) => org.goldenport.cncf.workspace.WorkspaceId.parseC(source).isSuccess &&
              org.goldenport.cncf.workspace.WorkspaceId.parseC(main).isSuccess)) Consequence.success(values)
          else invalid("CanonicalMappingRequiresLiteralWorkspaceIdentities"))
      case _ => invalid("CanonicalMappingRequiresJsonObject")
    })
  final case class Participant(repository: String, workspace: String, root: String, branch: String,
    remote: String, remoteBranch: String, endpoint: String) {
    def target: Target = Target(Path.of(root), branch, remote, remoteBranch, endpoint)
  }
  object Participant {
    given Encoder[Participant] = deriveEncoder
    given Decoder[Participant] = deriveDecoder
  }
  final case class Cohort(routeOwner: String, participants: Vector[Participant], sessions: Vector[String], members: Vector[String])
  object Cohort {
    given Encoder[Cohort] = deriveEncoder
    given Decoder[Cohort] = deriveDecoder
  }
  final case class Plan(source: Participant, main: Participant, linked: Boolean, transfer: Option[Participant] = None,
    cohort: Option[Cohort] = None)
  object Plan {
    given Encoder[Plan] = deriveEncoder
    given Decoder[Plan] = deriveDecoder
  }
  final case class Cursor(phase: String, sourceTip: Option[String], localTip: Option[String])
  object Cursor {
    given Encoder[Cursor] = deriveEncoder
    given Decoder[Cursor] = deriveDecoder
  }
  final case class Hop(plan: Plan, cursor: Cursor) extends RecordPresentable {
    private[RepositorySyncLocalMain] val ancestry = scala.collection.mutable.Map.empty[(String, String), Vector[String]]
    def participant: Participant = if (cursor.phase == "source" || cursor.phase == "direct") plan.source else plan.main
    def namespace: String = if (cursor.phase == "direct") "" else "local-main-" + cursor.phase
    def toRecord(): Record = Record.data("phase" -> cursor.phase, "workspace" -> participant.workspace)
  }
  private val cursorCodec = new WorkflowResultJsonV1.PayloadCodec[Cursor] {
    val typeIdentity = "RepositorySyncLocalMainPhase"
    def encode(value: Cursor): Json = value.asJson
    def decode(value: Json): Either[String, Cursor] = value.as[Cursor].left.map(_.message).flatMap(c =>
      Either.cond(Set("source", "local", "remote", "direct")(c.phase) &&
        (Set("source", "direct")(c.phase) || c.sourceTip.nonEmpty) &&
        (c.phase != "remote" || c.localTip.nonEmpty), c, "Incomplete local-main participant phase"))
  }
  private def reference(session: String, phase: String) =
    WorkflowMechanicalTransition.Reference(session + ":local-main-phase:" + phase, "1")
  def cursorC(storage: WorkflowComponentStorage, current: InstanceRecord, core: ActionCall.Core): Consequence[Cursor] =
    current.history.reverse.find(_.causation.value.startsWith("repository-local-main-phase:")) match {
      case None => Consequence.success(Cursor("source", None, None))
      case Some(entry) => for {
        owner <- storage.bindMechanicalC(RepositorySyncManagedFetch.advanceOperation, cursorCodec)
        value <- owner.loadRetainedC(reference(current.identity.value, entry.causation.value.stripPrefix("repository-local-main-phase:")))(using core.executionContext)
          .flatMap(_.map(Consequence.success).getOrElse(invalid("RetainedParticipantPhaseUnavailable")))
        prefix <- RepositorySyncPurposeContentContinuation.prefixC(current, entry.revision.value.toString)
        _ <- if (value.receipt.next == prefix) Consequence.unit else invalid("RetainedParticipantPhaseCrossesHistory")
      } yield value.receipt.payload.value
    }
  def phaseC(storage: WorkflowComponentStorage, current: InstanceRecord, cursor: Cursor,
    declaration: RepositorySyncManagedFetch.Declaration, core: ActionCall.Core): Consequence[InstanceRecord] = for {
    event <- cursor.phase match {
      case "local" => Consequence.success("source-contribution-prepared")
      case "remote" => Consequence.success("local-main-integration-completed")
      case "direct" => Consequence.success("route-direct-selected")
      case _ => invalid("ParticipantPhaseCannotBeReinitialized")
    }
    _ <- if (current.currentProgression.exists(p => domain.statemachine.RepositorySyncBoundaryLifecycle.permits(
        p.value.stripPrefix("sync."), event, "BoundaryAssessment"))) Consequence.unit
      else invalid("ParticipantPhaseTransitionNotDeclared")
    owner <- storage.bindMechanicalC(RepositorySyncManagedFetch.advanceOperation, cursorCodec)
    entry = HistoryEntry(HistorySequence(current.history.size.toLong + 1), current.revision.next, Lifecycle.Active,
      Some(ProgressionReference("sync.BoundaryAssessment")), current.correlation,
      CausationReference("repository-local-main-phase:" + cursor.phase), current.derivedCompositeOccurrence,
      current.committedPredecessor, None)
    saved <- owner.executeC(WorkflowMechanicalTransition.Intent(declaration.configuration,
      reference(current.identity.value, cursor.phase), current, current.revision, entry,
      TypedValue(cursorCodec.typeIdentity, cursor)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
  } yield saved.receipt.next
  def phaseHistory(current: InstanceRecord): Vector[HistoryEntry] = {
    val start = current.history.reverse.find(_.causation.value.startsWith("repository-local-main-phase:"))
      .map(_.revision.value).getOrElse(0L)
    current.history.filter(_.revision.value >= start)
  }
  def sourceReadyC(store: RepositorySyncNativeProgressStore.Store, current: InstanceRecord,
    observed: RepositorySyncManagedGitObservation.Observed): Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
    _ <- if (store.declaration.hop.exists(_.cursor.phase == "source") && observed.dirty.isEmpty &&
        observed.branch == store.context.localBranch) Consequence.unit else invalid("SourceContributionNotPrepared")
    _ <- phaseC(store.storage, current, Cursor("local", Some(observed.head), None), store.declaration, store.core)
  } yield NativeWorkflowOperationOutput.repositoryWait(store.identity,
    "Source contribution " + observed.head + " retained; next ordinary continuation integrates canonical local main without pushing the source", "LocalMainSourcePrepared").copy(kind = "Progress")
  def locallyIntegratedC(store: RepositorySyncNativeProgressStore.Store, committed: RepositorySyncNativeProgressStore.Stage):
      Consequence[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult] = for {
    selected <- store.declaration.hop.filter(_.cursor.phase == "local").map(Consequence.success)
      .getOrElse(invalid("LocalIntegrationHasNoCapturedParticipant"))
    native <- RepositorySyncManagedGitObservation.inspectC(store.core)
    _ <- store.scopeC(native.observed)
    _ <- if (native.mergeHead.isEmpty && native.observed.dirty.isEmpty &&
        native.observed.counts.exists(_.remoteAhead == 0) && selected.cursor.sourceTip == native.observed.trackingTip &&
        committed.payload.finalTip.contains(native.observed.head)) Consequence.unit
      else invalid("LocalIntegrationNotEstablished")
    _ <- phaseC(store.storage, committed.record, Cursor("remote", selected.cursor.sourceTip, Some(native.observed.head)),
      store.declaration, store.core)
  } yield NativeWorkflowOperationOutput.repositoryWait(store.identity,
    "Canonical local main integrated " + native.observed.head + "; remote convergence remains unfinished and uses only the retained remote hop", "LocalMainIntegrated").copy(kind = "Progress")

  /** This typed call-local value only carries an already retained participant.
    * It does not alter CNCF target bindings or install authority from wire JSON.
    */
  private final case class Scope(request: Request) extends CommandAction {
    def createCall(c: ActionCall.Core) = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = invalid("Participant scope is not an executable action")
    }
  }
  private val hopKey = "sm-workflow.repository-sync.local-main-hop"
  private val planKey = "sm-workflow.repository-sync.local-main-plan"
  private final case class BatchPlan(value: Plan) extends RecordPresentable {
    def toRecord(): Record = Record.data("source" -> value.source.workspace, "main" -> value.main.workspace)
  }
  def planProperty(value: Plan): Property = Property(planKey, BatchPlan(value), None)
  def batchPlan(core: ActionCall.Core): Option[Plan] = core.action.request.properties.collectFirst {
    case p if p.name == planKey && p.value.isInstanceOf[BatchPlan] => p.value.asInstanceOf[BatchPlan].value
  }
  def scopedCore(core: ActionCall.Core, hop: Option[Hop]): ActionCall.Core = hop match {
    case None => core
    case Some(value) =>
      val scoped = RepositorySyncDirectoryList.scopedCore(core, value.participant.workspace)
      scoped.copy(action = Scope(scoped.action.request.copy(properties = scoped.action.request.properties.filterNot(_.name == hopKey) :+
        Property(hopKey, value, None))))
  }
  def hop(core: ActionCall.Core): Option[Hop] = Option(core.action).flatMap(_.request.properties.collectFirst {
    case p if p.name == hopKey && p.value.isInstanceOf[Hop] => p.value.asInstanceOf[Hop]
  })
  def withoutHop(core: ActionCall.Core): ActionCall.Core =
    core.copy(action = Scope(core.action.request.copy(properties = core.action.request.properties.filterNot(_.name == hopKey))))

  /** Explicit local-source history, observed from typed commit metadata. This
    * is a local-hop relation, never a fabricated GitHub tracking observation.
    */
  def localRelationC(core: ActionCall.Core, head: String, source: String): Consequence[(RepositorySyncManagedGitObservation.Counts, String)] = {
    val workspace = RepositorySyncDirectoryList.workspace(core, "sm-workflow-repository")
    val memo = hop(core).map(_.ancestry).getOrElse(scala.collection.mutable.Map.empty[(String, String), Vector[String]])
    for {
      local <- ancestryC(core, head, workspace, memo)
      incoming <- ancestryC(core, source, workspace, memo)
      common = local.keySet.intersect(incoming.keySet)
      ancestors = common.flatMap(id => local.getOrElse(id, Vector.empty))
      bases = common -- ancestors
      _ <- if (bases.size == 1) Consequence.unit else invalid("LocalHistoryHasNoUnambiguousCommonAncestor")
    } yield RepositorySyncManagedGitObservation.Counts((local.keySet -- incoming.keySet).size.toLong,
      (incoming.keySet -- local.keySet).size.toLong) -> bases.head
  }
  private def ancestryC(core: ActionCall.Core, start: String, workspace: String,
    memo: scala.collection.mutable.Map[(String, String), Vector[String]]): Consequence[Map[String, Vector[String]]] = {
      var todo = List(start)
      var seen = Map.empty[String, Vector[String]]
      var failure = Option.empty[Consequence[Map[String, Vector[String]]]]
      while (todo.nonEmpty && failure.isEmpty) {
        val next = todo.head
        todo = todo.tail
        if (!seen.contains(next)) {
          val observed = if (seen.size >= 100000) invalid("LocalHistoryObservationBoundExceeded") else memo.get(workspace -> next) match {
            case Some(parents) => Consequence.success(parents)
            case None => for {
              oid <- GitObjectId.parseC(next)
              value <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.CommitMetadata(oid), workspace).flatMap(_.valueC)
              _ <- if (value.commit == oid) Consequence.unit else invalid("LocalHistoryObjectDiffers")
              parents = value.parents.map(_.value)
              _ = memo.update(workspace -> next, parents)
            } yield parents
          }
          observed match {
            case Consequence.Success(parents) => seen = seen.updated(next, parents); todo = parents.toList ++ todo
            case other => failure = Some(other.map(_ => seen))
          }
        }
      }
      failure.getOrElse(Consequence.success(seen))
  }
  def bindHop(service: spec.ServiceDefinition, selected: Option[Hop]): spec.ServiceDefinition =
    NativeWorkflowDescriptionMetadata.service(service, spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(
      service.operations.operations.toVector.map { operation => new spec.OperationDefinition {
        val specification = operation.specification
        def createOperationRequest(request: Request) = operation.createOperationRequest(request.copy(properties =
          request.properties.filterNot(_.name == hopKey) ++ selected.toVector.map(value => Property(hopKey, value, None))))
      }})))

  def remoteReadyC(session: RepositorySyncManagedFetch.Session, declaration: RepositorySyncManagedFetch.Declaration,
    core: ActionCall.Core): Consequence[Option[org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult]] =
    declaration.hop.filter(_.cursor.phase == "remote").flatMap(_.plan.cohort) match {
      case None => Consequence.success(None)
      case Some(cohort) => for {
        instances <- session.storage.bindInstancesC()(using core.executionContext)
        ready <- cohort.sessions.foldLeft(Consequence.success(true)) { (prior, identity) => for {
          all <- prior
          current <- instances.loadC(InstanceIdentity(identity))(using core.executionContext)
          cursor <- current.map(value => cursorC(session.storage, value, core).map(Some(_)))
            .getOrElse(Consequence.success(Option.empty[Cursor]))
        } yield all && cursor.exists(c => c.phase == "remote" || c.phase == "direct") }
        settled <- if (cohort.sessions.lastOption.contains(session.started.current.identity.value)) Consequence.success(true)
          else (cohort.sessions.lastOption.toVector ++ cohort.members.filterNot(cohort.sessions.contains)).foldLeft(Consequence.success(true)) {
            (prior, identity) => for {
              all <- prior
              actual <- instances.loadC(InstanceIdentity(identity))(using core.executionContext)
            } yield all && actual.exists(record => record.lifecycle == Lifecycle.Completed &&
              record.causation.value == "native-repository-completed:" + identity)
          }
      } yield Option.when(!ready || !settled)(NativeWorkflowOperationOutput.repositoryWait(session.started.current.identity.value,
        "Local integration is retained; other declared contributions to the same canonical target are unfinished", "SharedTargetAwaitingContributions"))
    }
  def sameMain(left: Participant, right: Participant): Boolean = left.root == right.root &&
    left.branch == right.branch && left.remoteBranch == right.remoteBranch && left.endpoint == right.endpoint
  def report(value: org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult,
    declaration: RepositorySyncManagedFetch.Declaration): org.simplemodeling.textus.smworkflow.value.RepositorySyncAdvanceResult =
    declaration.hop.filter(_.cursor.phase == "remote").fold(value) { h => value.copy(message = value.message +
      "; source contribution " + h.cursor.sourceTip.getOrElse("unavailable") + " from " + h.plan.source.root +
      " (branch " + h.plan.source.branch + " preserved); canonical local integration " + h.cursor.localTip.getOrElse("unavailable") +
      " retained at " + h.plan.main.root + "; remote convergence outcome " + value.kind)
    }

  /** Pure selection keeps missing, ambiguous and prunable metadata distinct.
    * A separate clone can only be named by the caller's explicit mapping.
    */
  def selectMainC(worktrees: Vector[GitWorktree], source: Participant,
    mapped: Option[Participant]): Consequence[(String, Boolean)] = for {
    _ <- if (worktrees != null && source != null && worktrees.forall(_ != null)) Consequence.unit
      else invalid("WorktreeMetadataUnavailable")
    selected = worktrees.filter(_.path == source.root)
    _ <- if (selected.size == 1 && !selected.head.bare && !selected.head.detached && !selected.head.prunable &&
        selected.head.branch.exists(_.value == source.branch) && selected.head.head.nonEmpty) Consequence.unit
      else invalid("SelectedSourceNotInWorktreeMetadata")
    candidates = worktrees.filter(_.branch.exists(_.value == "main"))
    result <- if (candidates.size > 1) invalid("CanonicalMainAmbiguous")
      else candidates.headOption match {
        case Some(value) if value.prunable => invalid("CanonicalMainPrunable")
        case Some(value) if value.bare || value.detached || value.head.isEmpty => invalid("CanonicalMainUnavailable")
        case Some(value) if mapped.exists(_.root != value.path) => invalid("CanonicalMainMappingConflictsWithLinkedMetadata")
        case Some(value) => Consequence.success(value.path -> true)
        case None => mapped.filter(p => p.branch == "main").map(p => Consequence.success(p.root -> false))
          .getOrElse(invalid("CanonicalMainMappingRequired"))
      }
  } yield result

  def participantC(plan: Json, workspace: String): Consequence[Participant] = for {
    roots <- plan.hcursor.get[Vector[Json]]("workspaces").fold(e => invalid(e.message), Consequence.success)
    families <- plan.hcursor.get[Vector[Json]]("gitRepositories").fold(e => invalid(e.message), Consequence.success)
    root <- oneC(roots.filter(_.hcursor.get[String]("identity").contains(workspace)), "CanonicalWorkspaceAmbiguousOrMissing")
    family <- oneC(families.filter(_.hcursor.get[String]("target").contains(workspace)), "CanonicalRepositoryBindingAmbiguousOrMissing")
    path <- root.hcursor.get[String]("root").fold(e => invalid(e.message), Consequence.success)
    _ <- if (Path.of(path).isAbsolute && Path.of(path).normalize.toString == path) Consequence.unit
      else invalid("CanonicalWorkspaceRequiresAbsoluteLiteralRoot")
    identity <- family.hcursor.get[String]("identity").fold(e => invalid(e.message), Consequence.success)
    branch <- family.hcursor.get[String]("localBranch").fold(e => invalid(e.message), Consequence.success)
    remote <- family.hcursor.get[String]("remote").fold(e => invalid(e.message), Consequence.success)
    destination <- family.hcursor.get[String]("remoteBranch").fold(e => invalid(e.message), Consequence.success)
    endpoint <- family.hcursor.get[String]("endpoint").fold(e => invalid(e.message), Consequence.success)
  } yield Participant(identity, workspace, path, branch, remote, destination, endpoint)

  /** Both checkouts are read through their normal CNCF targets. A matching path
    * or an explicit mapping alone establishes neither repository identity nor
    * the actual checked-out branch. No Git/File effect occurs here.
    */
  def captureC(core: ActionCall.Core, configured: Json, source: Participant,
    mapped: Option[Participant]): Consequence[Plan] = for {
    metadata <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.Worktrees, source.workspace).flatMap(_.valueC)
    selected <- selectMainC(metadata, source, mapped)
    roots <- configured.hcursor.get[Vector[Json]]("workspaces").fold(e => invalid(e.message), Consequence.success)
    families <- configured.hcursor.get[Vector[Json]]("gitRepositories").fold(e => invalid(e.message), Consequence.success)
    candidates = roots.filter(r => r.hcursor.get[String]("root").contains(selected._1) &&
      families.exists(f => f.hcursor.get[String]("target").toOption == r.hcursor.get[String]("identity").toOption &&
        f.hcursor.get[String]("localBranch").contains("main") && f.hcursor.get[String]("endpoint").contains(source.endpoint) &&
        f.hcursor.get[String]("remoteBranch").contains(source.remoteBranch)))
    configuredMain <- oneC(candidates.filter(r => mapped.forall(m => r.hcursor.get[String]("identity").contains(m.workspace))),
      "CanonicalMainWorkspaceNotUnambiguouslyAdmitted")
    workspace <- configuredMain.hcursor.get[String]("identity").fold(e => invalid(e.message), Consequence.success)
    main <- participantC(configured, workspace)
    native <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.Worktrees, main.workspace).flatMap(_.valueC)
    _ <- if (native.count(w => w.path == main.root && !w.prunable && !w.bare && !w.detached &&
        w.branch.exists(_.value == "main") && w.head.nonEmpty) == 1) Consequence.unit
      else invalid("CanonicalMainObservationDiffersFromConfiguredCheckout")
    _ <- if (source.remoteBranch == "main" && main.branch == "main" && main.remoteBranch == "main" &&
        main.endpoint == source.endpoint) Consequence.unit else invalid("CanonicalMainRepositoryIdentityDiffers")
    sourceRoot <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.Root, source.workspace).flatMap(_.valueC)
    mainRoot <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.Root, main.workspace).flatMap(_.valueC)
    _ <- if (sourceRoot == source.root && mainRoot == main.root) Consequence.unit
      else invalid("CanonicalParticipantRootDiffers")
    advertisedSource <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.RemoteTip, source.workspace).flatMap(_.valueC)
    advertisedMain <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.RemoteTip, main.workspace).flatMap(_.valueC)
    _ <- if (advertisedSource == advertisedMain) Consequence.unit else invalid("CanonicalParticipantsRemoteIdentityChanged")
    _ <- if (selected._2) Consequence.unit else for {
      sourceState <- RepositorySyncManagedGitObservation.observeInWorkspaceC(core, source.workspace)
      mainState <- RepositorySyncManagedGitObservation.observeInWorkspaceC(core, main.workspace)
      memo = scala.collection.mutable.Map.empty[(String, String), Vector[String]]
      sourceHistory <- ancestryC(core, sourceState.head, source.workspace, memo)
      mainHistory <- ancestryC(core, mainState.head, main.workspace, memo)
      _ <- if (sourceHistory.keySet.intersect(mainHistory.keySet).nonEmpty) Consequence.unit
        else invalid("MappedCanonicalCloneHasNoObservedSharedHistory")
    } yield ()
    transfer <- if (selected._2) Consequence.success(Option.empty[Participant]) else for {
      candidates <- families.filter(f => f.hcursor.get[String]("endpoint").contains(source.root) &&
        f.hcursor.get[String]("remoteBranch").contains(source.branch)).foldLeft(Consequence.success(Vector.empty[Participant])) {
        (prior, row) => for {
          values <- prior
          alias <- row.hcursor.get[String]("target").fold(e => invalid(e.message), Consequence.success)
          participant <- participantC(configured, alias)
        } yield if (participant.root == main.root && participant.branch == "main") values :+ participant else values
      }
      binding <- oneC(candidates, "CanonicalCloneSourceFetchBindingRequired")
    } yield Some(binding)
  } yield Plan(source, main, selected._2, transfer)

  private def oneC[A](values: Vector[A], message: String): Consequence[A] =
    if (values.size == 1) Consequence.success(values.head) else invalid(message)
  private def invalid[A](message: String): Consequence[A] = Consequence.stateConflict("RepositorySync local main: " + message)
}
