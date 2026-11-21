package org.simplemodeling.textus.smworkflow.admission

import cats.data.NonEmptyVector
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.file.Path
import scala.util.Try
import org.goldenport.{Consequence, ConsequenceT}
import org.goldenport.cncf.action.{ActionCall, CommandAction, CommandExecutionMode, ProcedureActionCall}
import org.goldenport.cncf.component.ComponentCreate
import org.goldenport.cncf.job.{ActionId, ActionTask, InMemoryJobEngine, JobEngine, JobId, JobResult, JobRunMode, JobSubmitOption}
import org.goldenport.cncf.config.{CncfConfigurationParameterCatalog, ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.processexecution.GitBranchName
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.cncf.unitofwork.UnitOfWorkOp
import org.goldenport.cncf.workflow.*
import org.goldenport.cncf.workflow.WorkflowInstancePersistence.*
import org.goldenport.cncf.workflow.WorkflowProtocolV1.TypedValue
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.convert.ValueReader
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.{OperationRequest, OperationResponse}
import org.goldenport.protocol.spec as spec
import org.goldenport.record.{Record, RecordPresentable}
import org.simplemodeling.textus.smworkflow.SmWorkflowComponent
import org.simplemodeling.textus.smworkflow.runtime.RepositoryGitRuntimePrograms.Target
import org.simplemodeling.textus.smworkflow.value as V

/** Directory intake composes the existing native single-repository owner.
  * The configured process plan still owns all workspaces, targets and grants.
  * Only small business relations are retained here; sources and execution
  * captures remain with their existing owners.
  * @since   Nov.  5, 2026
  * @version Nov. 21, 2026
  */
private[smworkflow] object RepositorySyncDirectoryList {
  val syncOperation = "syncRepositoryDirectories"
  val readOperation = "readRepositoryDirectoryResult"
  val refreshOperation = "refreshRepositoryDirectoryConfiguration"
  val operations = Set(syncOperation, readOperation, refreshOperation)
  val declaration: ComponentParameterKey[Vector[String]] = ComponentParameterKey.optional(
    "sm-workflow.repository-sync.directories", ComponentParameterDecoder {
      case ConfigurationValue.ListValue(values) => values.foldLeft(Consequence.success(Vector.empty[String])) {
        (prior, value) => prior.flatMap(paths => value match {
          case ConfigurationValue.StringValue(path) => Consequence.success(paths :+ path)
          case _ => _invalid("directories require string checkout paths")
        })
      }.flatMap(paths => decodeDirectoriesC(paths.asJson))
      case ConfigurationValue.StringValue(text) => io.circe.parser.parse(text).fold(
        _ => _invalid("directories must be a JSON array of absolute checkout paths"), decodeDirectoriesC)
      case _ => _invalid("directories must be a JSON array of absolute checkout paths")
    })

  final case class Member(repositoryIdentity: String, workspace: String, root: String,
    localBranch: String, remote: String, remoteBranch: String, endpoint: String, sessionId: String,
    policies: Option[RepositorySyncPolicies.Selection] = None,
    localMain: Option[RepositorySyncLocalMain.Plan] = None) {
    def target: Target = Target(Path.of(root), localBranch, remote, remoteBranch, endpoint)
  }
  final case class Work(operation: String, continuationId: String, expectedRevision: String)
  private given Encoder[Work] = deriveEncoder
  private given Decoder[Work] = deriveDecoder
  final case class Result(repositoryIdentity: String, workspace: String, sessionId: String,
    kind: String, outcome: String, condition: Option[String], revision: Option[String],
    tip: Option[String], tree: Option[String], diagnostic: Option[String] = None, work: Option[Work] = None)
  final case class Setup(directories: Vector[String], diagnostic: String)
  final case class Attempt(job: Option[String], start: Boolean, predecessor: Option[String])
  private given Encoder[Attempt] = deriveEncoder
  private given Decoder[Attempt] = deriveDecoder
  private given Encoder[Setup] = deriveEncoder
  private given Decoder[Setup] = deriveDecoder
  final case class Payload(participant: String, members: Vector[Member], results: Vector[Result],
    stage: String, attempting: Option[String], setup: Option[Setup] = None,
    policies: Option[RepositorySyncPolicies.Selection] = None,
    dependencies: Map[String, Vector[String]] = Map.empty, sourceTrees: Map[String, String] = Map.empty,
    attempts: Map[String, Attempt] = Map.empty,
    consumerInputs: Map[String, RepositorySyncConsumedInputs.Change] = Map.empty)
  private given Encoder[Member] = deriveEncoder[Member].mapJson(_.mapObject(_.filter {
    case ("policies" | "localMain", value) => !value.isNull
    case _ => true
  }))
  private given Decoder[Member] = deriveDecoder
  private given Encoder[Result] = deriveEncoder[Result].mapJson(_.mapObject(_.filter {
    case ("diagnostic" | "work", value) => !value.isNull
    case _ => true
  }))
  private given Decoder[Result] = deriveDecoder
  private given Encoder[Payload] = deriveEncoder[Payload].mapJson(_.mapObject(_.filter {
    case ("setup" | "policies", value) => !value.isNull
    case ("dependencies" | "sourceTrees" | "attempts" | "consumerInputs", value) => !value.asObject.exists(_.isEmpty)
    case _ => true
  }))
  private val payloadDecoder: Decoder[Payload] = deriveDecoder
  private given Decoder[Payload] = Decoder.instance { cursor =>
    // Historical batches have no graph. Missing fields retain ordinary behavior;
    // explicit null remains malformed rather than silently losing a relation.
    val value = cursor.value.mapObject(fields => Vector("dependencies", "sourceTrees", "attempts", "consumerInputs").foldLeft(fields) {
      (current, key) => if (current.contains(key)) current else current.add(key, Json.obj())
    })
    payloadDecoder.decodeJson(value)
  }

  // This typed invocation-local marker is constructed only from a resolved host
  // binding. A JSON/string property cannot install it or grant a workspace.
  private final case class Workspace(identity: String) extends RecordPresentable {
    def toRecord(): Record = Record.data("identity" -> identity)
  }
  private val _workspace_key = "sm-workflow.directory-list.workspace"
  def workspace(core: ActionCall.Core, fallback: String): String =
    if (fallback != "sm-workflow-repository" || core == null || core.action == null) fallback
    else core.action.request.properties.collectFirst {
      case property if property.name == _workspace_key => property.value
    }.collect { case value: Workspace => value.identity }.getOrElse(fallback)

  def scopedCore(core: ActionCall.Core, workspaceIdentity: String): ActionCall.Core =
    core.copy(action = ScopeAction(_scoped_request(core.action.request, workspaceIdentity)))

  private[admission] def replaceRequest(core: ActionCall.Core, request: Request): ActionCall.Core =
    core.copy(action = ScopeAction(request))

  def bindWorkspace(service: spec.ServiceDefinition, workspaceIdentity: String,
    consumedInputs: Option[RepositorySyncConsumedInputs.Binding] = None): spec.ServiceDefinition =
    NativeWorkflowDescriptionMetadata.service(service, spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(
      service.operations.operations.toVector.map { operation => new spec.OperationDefinition {
        val specification = operation.specification
        def createOperationRequest(request: Request): Consequence[OperationRequest] =
          operation.createOperationRequest(RepositorySyncConsumedInputs.withBinding(
            _scoped_request(request, workspaceIdentity), consumedInputs))
      }})))

  private def _scoped_request(request: Request, workspace: String): Request =
    request.copy(properties = request.properties.filterNot(_.name == _workspace_key) :+
      Property(_workspace_key, Workspace(workspace), None))

  def decodeDirectoriesC(value: Json): Consequence[Vector[String]] =
    value.as[Vector[String]].toOption.filter(paths => paths.nonEmpty && paths.size <= 128 &&
      paths.forall(path => path != null && !path.exists(_.isControl) && Try(Path.of(path)).toOption.exists(_.isAbsolute)))
      .map(paths => Consequence.success(paths.map(path => Path.of(path).normalize.toString).distinct))
      .getOrElse(_invalid("directories require 1..128 absolute checkout paths"))

  /** Resolve already admitted host data before the first remote observation.
    * The source work branch and remote destination stay separate, even when
    * the source has no Git upstream. No origin/main or branch-name guess.
    */
  def resolveC(directories: Vector[String], plan: Json, sessionId: String,
    preferredWorkspaces: Set[String] = Set.empty): Consequence[Vector[Member]] = for {
    paths <- decodeDirectoriesC(directories.asJson)
    workspaces <- plan.hcursor.get[Vector[Json]]("workspaces").fold(e => _invalid(e.message), Consequence.success)
    repositories <- plan.hcursor.get[Vector[Json]]("gitRepositories").fold(e => _invalid(e.message), Consequence.success)
    members <- paths.zipWithIndex.foldLeft(Consequence.success(Vector.empty[Member])) { case (prior, (root, ordinal)) => for {
      values <- prior
      candidates = workspaces.filter(workspace =>
        workspace.hcursor.get[String]("root").toOption.exists(value =>
          Try(Path.of(value)).toOption.exists(path => path.isAbsolute && path.normalize.toString == root)) &&
        repositories.exists(repository => repository.hcursor.get[String]("target").toOption ==
          workspace.hcursor.get[String]("identity").toOption))
      preferred = candidates.filter(_.hcursor.get[String]("identity").toOption.exists(preferredWorkspaces))
      configured <- _one_c(if (preferred.nonEmpty) preferred else candidates,
        "checkout has no unique configured workspace: " + root)
      workspace <- _text_c(configured, "identity")
      repository <- _one_c(repositories.filter(_.hcursor.get[String]("target").contains(workspace)),
        "checkout has no unique configured Git destination: " + root)
      identity <- _text_c(repository, "identity")
      branch <- _text_c(repository, "localBranch")
      _ <- GitBranchName.parseC(branch)
      remote <- _text_c(repository, "remote")
      destination <- _text_c(repository, "remoteBranch")
      _ <- GitBranchName.parseC(destination)
      endpoint <- _text_c(repository, "endpoint")
    } yield values :+ Member(identity, workspace, root, branch, remote, destination, endpoint,
      sessionId + ":repository-" + (ordinal + 1)) }
    _ <- if (members.map(_.workspace).distinct.size == members.size) Consequence.unit
      else _invalid("directory entries alias the same configured workspace")
  } yield members

  type BindRepository = (Request, ActionCall.Core, Target) => Consequence[spec.ServiceDefinition]
  def bind(creation: ComponentCreate, parameters: ComponentInitializationParameters,
    persistence: GoalPhaseRuntimeParameters.Persistence, repository: BindRepository): spec.ServiceDefinition =
    NativeWorkflowDescriptionMetadata.service(SmWorkflowComponent.RepositorySyncServiceService,
      spec.OperationDefinitionGroup(NonEmptyVector.of(new Operation(false, creation, parameters, persistence, repository),
        new Operation(true, creation, parameters, persistence, repository),
        new Operation(false, creation, parameters, persistence, repository, refresh = true))))

  private final class Operation(read: Boolean, creation: ComponentCreate,
    parameters: ComponentInitializationParameters, persistence: GoalPhaseRuntimeParameters.Persistence,
    repository: BindRepository, refresh: Boolean = false) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (refresh)
      SmWorkflowComponent.RepositorySyncServiceService.RefreshRepositoryDirectoryConfigurationOperation else if (read)
      SmWorkflowComponent.RepositorySyncServiceService.ReadRepositoryDirectoryResultOperation
      else SmWorkflowComponent.RepositorySyncServiceService.SyncRepositoryDirectoriesOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("RepositorySyncService") || request.operation != specification.name)
        _invalid("directory-list operation differs from its declared service")
      else Consequence.success(Action(request, read, creation, parameters, persistence, repository, refresh))
  }
  private final case class Action(request: Request, read: Boolean, creation: ComponentCreate,
    parameters: ComponentInitializationParameters, persistence: GoalPhaseRuntimeParameters.Persistence,
    repository: BindRepository, refresh: Boolean) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        identity <- if (read || refresh) NativeWorkflowOperationInput.textC(Action.this.request, "sessionId")
          else NativeWorkflowOperationInput.startSessionC(Action.this.request, core.executionContext)
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(identity))
        call = core.copy(executionContext = context)
        participant = SecuritySubject.from(context.security).subjectId
        _ <- GoalPhaseParticipantAdmission.admitC(participant, context)
        definition <- RepositorySyncDefinitionBinding.bindC
        storage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, persistence.configuration,
          Vector(definition), persistence.instances, persistence.continuations)
        instances <- storage.bindInstancesC()(using context)
        adapter <- storage.bindMechanicalC(_operation, payloadCodec)
        existing <- instances.loadC(InstanceIdentity(identity))(using context)
        response <- if (read) for {
          revision <- NativeWorkflowOperationInput.textC(Action.this.request, "revision")
          _ <- if (revision.matches("[1-9][0-9]*")) Consequence.unit else _invalid("exact result revision is required")
          retained <- adapter.loadRetainedC(_reference(identity, revision))(using context)
            .flatMap(_.map(Consequence.success).getOrElse(_invalid("selected directory result is unavailable")))
          value = retained.receipt.payload.value
          _ <- _admit_c(value, participant)
          _ <- if (retained.receipt.next.identity.value == identity && retained.receipt.next.revision.value.toString == revision)
            Consequence.unit else _invalid("selected result crosses its native Session or revision")
          response <- _response_c(identity, revision, value, storage, call)
        } yield response
        else for {
          input <- if (refresh) Consequence.success(Record.data("syncMode" -> "FULL_STATE"))
            else NativeWorkflowOperationInput.recordC(Action.this.request, "input")
          _ <- if (DevelopmentOperationalView.json(input).asObject.exists(_.keys.toSet.subsetOf(Set("syncMode", "mainIntegration", "uncommittedPolicy"))) &&
              input.getString("syncMode").contains("FULL_STATE")) Consequence.unit
            else _invalid("directory-list input requires explicit syncMode FULL_STATE and optional synchronization policies")
          loaded <- existing match {
            case Some(current) => for {
              retained <- adapter.loadRetainedC(_reference(identity, current.revision.value.toString))(using context)
                .flatMap(_.map(Consequence.success).getOrElse(_invalid("Session is not a retained directory-list execution")))
              _ <- _admit_c(retained.receipt.payload.value, participant)
              _ <- if (retained.receipt.next == current) Consequence.unit else _invalid("native directory frontier differs")
              _ <- if (refresh) NativeWorkflowOperationInput.textC(Action.this.request, "revision").flatMap { revision =>
                if (revision == current.revision.value.toString &&
                    Set("AttentionRequired", "ConfigurationRequired")(retained.receipt.payload.value.stage))
                  Consequence.unit else _invalid("configuration refresh requires its exact outstanding aggregate revision")
              } else Consequence.unit
            } yield current -> retained.receipt.payload.value
            case None if refresh => _invalid("configuration refresh requires the existing aggregate Session")
            case None => for {
              policies <- RepositorySyncPolicies.resolveC(parameters, input)
              directories <- parameters.resolve(declaration).map(_.value.getOrElse(Vector.empty))
              resolved <- _configured_c(creation, directories, identity, call, parameters, Some(policies))
              initial <- InstanceRecord(InstanceIdentity(identity), definition, initialRevision, Lifecycle.NotStarted,
                None, CorrelationReference("directory-list:" + identity), CausationReference("directory-list-start:" + identity),
                None, None, None, Vector.empty).validateC
              _ <- instances.createC(initial)(using context)
              active <- instances.appendC(initial.identity, initial.revision,
                _entry(initial, "DirectoryListStarted", "directory-list-start:" + identity))(using context)
              initialpayload = resolved.fold(reason => Payload(participant, Vector.empty, Vector.empty, "ConfigurationRequired", None,
                Some(Setup(directories, reason)), Some(policies)), ordered => Payload(participant,
                ordered._1, Vector.empty, "Ready", None, policies = Some(policies), dependencies = ordered._2))
              saved <- _save_c(adapter, persistence, call, active, initialpayload)
            } yield saved
          }
          configured <- if (refresh && loaded._2.stage == "ConfigurationRequired") for {
            _ <- if (loaded._2.members.isEmpty && loaded._2.results.isEmpty && loaded._2.attempting.isEmpty)
              Consequence.unit else _invalid("target setup cannot replace any started or attempted child")
            setup <- loaded._2.setup.map(Consequence.success).getOrElse(_invalid("configuration wait has no retained setup context"))
            directories <- parameters.resolve(declaration).map(_.value.getOrElse(Vector.empty))
            _ <- if (setup.directories.isEmpty || directories == setup.directories) Consequence.unit
              else _invalid("configuration refresh cannot replace the captured directory selection")
            resolved <- _configured_c(creation, directories, identity, call, parameters, loaded._2.policies)
            payload = resolved.fold(reason => loaded._2.copy(setup = Some(Setup(directories, reason))), ordered =>
              loaded._2.copy(members = ordered._1, dependencies = ordered._2, stage = "Ready", setup = None))
            saved <- _save_c(adapter, persistence, call, loaded._1, payload)
          } yield saved else Consequence.success(loaded)
          resumed <- if (configured._2.stage == "Indeterminate" && configured._2.attempts.valuesIterator.exists(_.job.nonEmpty))
              _parallel_c(configured._1, configured._2, adapter, persistence, storage, call, repository, false)
            else if (configured._2.stage == "AttentionRequired")
              _resume_c(configured._1, configured._2, adapter, persistence, storage, call, repository, refresh)
            else Consequence.success(configured)
          result <- if (resumed._2.stage == "Ready") _run_c(resumed._1, resumed._2, adapter, persistence, storage, call, repository)
            else Consequence.success(resumed)
          response <- _response_c(identity, result._1.revision.value.toString, result._2, storage, call)
        } yield response
      } yield response
    }
  }

  /** Missing configuration is a retained setup outcome before the first child
    * or remote effect. Refresh consumes normal current host bindings; it neither
    * guesses a destination nor changes a previously captured child target.
    */
  private[admission] def sharedTargetSetup(members: Vector[Member], policies: RepositorySyncPolicies.Selection): Option[String] =
    Option.when(policies.mainIntegration != "direct" && members.filter(_.remoteBranch == "main")
      .groupBy(member => member.endpoint -> member.remoteBranch).values.exists { group =>
        group.size > 1 && (group.exists(m => m.localBranch != "main" && m.localMain.isEmpty) ||
          group.flatMap { member =>
            member.localMain.map(_.main).map(p => (p.root, p.branch, p.endpoint, p.workspace)).orElse(
              Option.when(member.localBranch == "main")((member.root, member.localBranch, member.endpoint, member.workspace)))
          }.distinct.size > 1)
      })("SharedTargetSetupRequired: the declared sources do not resolve to one admitted canonical target; no child or route fallback is started")

  private enum CohortBoundary {
    case Contribution(session: String)
    case Terminal(session: String)
  }

  private[admission] def dependencyCohortSetup(members: Vector[Member],
    dependencies: Map[String, Vector[String]]): Option[String] = {
    import CohortBoundary.*
    val cohorts = members.flatMap(_.localMain.flatMap(_.cohort)).distinct
    val bySession = cohorts.flatMap(cohort => cohort.members.map(_ -> cohort)).toMap
    val prerequisites = members.flatMap { member =>
      val id = member.sessionId
      val cohort = bySession.get(id)
      val earlier = cohort.toVector.flatMap(_.members.takeWhile(_ != id))
      val selectedProviders = dependencies.getOrElse(id, Vector.empty)
      val contributors = cohort.toVector.flatMap(_.sessions).filter(_ != id)
      val remoteOwners = cohort.toVector.flatMap { value =>
        if (value.sessions.contains(id) && !value.sessions.lastOption.contains(id))
          value.sessions.lastOption.toVector ++ value.members.filterNot(value.sessions.contains)
        else Vector.empty
      }
      val beforeContribution: Vector[CohortBoundary] = earlier.map(Contribution.apply) ++ selectedProviders.map(Terminal.apply)
      val beforeTerminal: Vector[CohortBoundary] = Vector(Contribution(id)) ++
        Option.when(member.localMain.nonEmpty)(contributors.map(Contribution.apply) ++ remoteOwners.map(Terminal.apply))
          .getOrElse(Vector.empty)
      Vector((Contribution(id): CohortBoundary) -> beforeContribution.toSet,
        (Terminal(id): CohortBoundary) -> beforeTerminal.toSet)
    }.toMap
    @scala.annotation.tailrec
    def blocked(remaining: Map[CohortBoundary, Set[CohortBoundary]]): Boolean = {
      if (remaining.isEmpty) false
      else {
        val ready = remaining.collect { case (boundary, required) if !required.exists(remaining.contains) => boundary }
        if (ready.isEmpty) true else blocked(remaining -- ready)
      }
    }
    if (cohorts.nonEmpty && dependencies.nonEmpty && blocked(prerequisites))
      Some("DependencyTargetSetupRequired: selected dependencies and canonical target contributions cannot converge in the same batch")
    else members.find(member => dependencies.contains(member.sessionId) &&
      member.localMain.exists(_.main.workspace != member.workspace)).map(member =>
        "DependencyTargetSetupRequired: " + member.repositoryIdentity +
          " has a selected consumer input bound to its source workspace, but local-main integration validates another workspace")
  }

  private def _configured_c(creation: ComponentCreate, directories: Vector[String], identity: String,
    core: ActionCall.Core, parameters: ComponentInitializationParameters, policies: Option[RepositorySyncPolicies.Selection]):
      Consequence[Either[String, (Vector[Member], Map[String, Vector[String]])]] = {
    val resolved = for {
      _ <- if (directories.nonEmpty) Consequence.unit else _invalid("sm-workflow.repository-sync.directories is not configured")
      raw <- GoalPhaseInputRecordReadPolicy.storedValue(creation.configuration.configuration.values,
        CncfConfigurationParameterCatalog.SUBSYSTEM_PROCESS_EXECUTION_PLAN_KEY) match {
        case Some(ConfigurationValue.StringValue(value)) => io.circe.parser.parse(value)
          .fold(e => _invalid(e.message), Consequence.success)
        case _ => _invalid("no managed repository execution profile is configured")
      }
      mappings <- parameters.resolve(RepositorySyncLocalMain.mappings).map(_.value.getOrElse(Map.empty))
      development <- parameters.resolve(ManagedDevelopmentRuntime.declaration).map(_.value)
      configuredMain <- parameters.resolve(RepositorySyncPolicies.mainIntegration).map(_.value)
      members <- resolveC(directories, raw, identity, mappings.values.toSet)
      captured <- members.foldLeft(Consequence.success(Vector.empty[Member])) { (prior, member) => for {
        values <- prior
        managed <- ManagedDevelopmentRepositorySync.selectC(development, member.repositoryIdentity, raw,
          Some(member.root), policies.map(_.mainIntegration))
        _ <- if (managed.exists(_.remoteBranch == "main") && configuredMain.exists(_ != "direct"))
          _invalid("Managed dependency remote main conflicts with explicit main-integration before target effects") else Consequence.unit
        plan <- if (policies.exists(p => RepositorySyncPolicies.route(p, member.localBranch, member.remoteBranch) != "direct")) for {
          source <- RepositorySyncLocalMain.participantC(raw, member.workspace)
          mapped <- mappings.get(member.workspace).map(value => RepositorySyncLocalMain.participantC(raw, value).map(Some(_)))
            .getOrElse(Consequence.success(Option.empty[RepositorySyncLocalMain.Participant]))
          selected <- RepositorySyncLocalMain.captureC(scopedCore(core, member.workspace), raw, source, mapped)
        } yield Some(selected) else Consequence.success(Option.empty[RepositorySyncLocalMain.Plan])
      } yield values :+ member.copy(policies = policies, localMain = plan) }
      _ <- policies.flatMap(sharedTargetSetup(captured, _)).map(_invalid[Unit]).getOrElse(Consequence.unit)
      coordinated = captured.map { member => member.localMain.fold(member) { plan =>
        val group = captured.filter(m => m.endpoint == member.endpoint && m.remoteBranch == member.remoteBranch && m.localMain.nonEmpty)
        val participants = group ++ captured.filter(m => m.endpoint == member.endpoint &&
          m.remoteBranch == member.remoteBranch && m.localMain.isEmpty)
        val cohort = RepositorySyncLocalMain.Cohort(group.head.sessionId, group.flatMap(_.localMain.map(_.source)), group.map(_.sessionId),
          participants.map(_.sessionId))
        member.copy(localMain = Some(plan.copy(cohort = Some(cohort))))
      }}
      (localMain, direct) = coordinated.partition(_.localMain.nonEmpty)
      ordered <- ManagedDevelopmentRepositorySync.orderC(development, localMain ++ direct)
      _ <- dependencyCohortSetup(ordered._1, ordered._2).map(_invalid[Unit]).getOrElse(Consequence.unit)
    } yield ordered
    resolved.map[Either[String, (Vector[Member], Map[String, Vector[String]])]](Right(_))
      .recoverWith(conclusion => Consequence.success(Left(conclusion.show)))
  }

  private def _run_c(current: InstanceRecord, payload: Payload, adapter: WorkflowManagedMechanicalTransition[Payload],
    persistence: GoalPhaseRuntimeParameters.Persistence, storage: WorkflowComponentStorage,
    core: ActionCall.Core, repository: BindRepository): Consequence[(InstanceRecord, Payload)] =
    if (payload.dependencies.nonEmpty || payload.attempts.nonEmpty)
      _parallel_c(current, payload, adapter, persistence, storage, core, repository, false)
    else
    payload.members.filterNot(m => payload.results.exists(_.sessionId == m.sessionId)).foldLeft(Consequence.success(current -> payload)) { (prior, member) =>
      prior.flatMap { (frontier, state) =>
        if (state.stage != "Ready") Consequence.success(frontier -> state)
        else dependencyBlock(state, member) match {
          case Some(reason) => _save_c(adapter, persistence, core, frontier, state.copy(stage = "Ready",
            setup = Some(Setup(state.members.map(_.root), reason))))
          case None => for {
          source <- if (!state.dependencies.valuesIterator.flatten.contains(member.sessionId) || state.sourceTrees.contains(member.sessionId))
            Consequence.success(state.sourceTrees)
            else RepositorySyncManagedGitObservation.observeInWorkspaceC(core, member.workspace)
              .map(observed => state.sourceTrees.updated(member.sessionId, observed.tree))
          intent <- _save_c(adapter, persistence, core, frontier, state.copy(stage = "Attempting", attempting = Some(member.sessionId),
            setup = None, sourceTrees = source))
          observed <- _execute_c(member, core, repository).map[Either[String, V.RepositorySyncAdvanceResult]](Right(_))
            .recoverWith(conclusion => Consequence.success(Left(conclusion.show)))
          instances <- storage.bindInstancesC()(using core.executionContext)
          child <- instances.loadC(InstanceIdentity(member.sessionId))(using core.executionContext)
          result = observed match {
            case Right(value) => Result(member.repositoryIdentity, member.workspace, member.sessionId, value.kind,
              value.outcome.getOrElse(if (value.kind == "Terminal") "completed" else "unfinished"), value.condition,
              child.map(_.revision.value.toString), value.convergence.map(_.tip), value.convergence.map(_.tree),
              _diagnostic(value),
              _work_reference(value, child))
            case Left(diagnostic) => Result(member.repositoryIdentity, member.workspace, member.sessionId, "Indeterminate", "indeterminate",
              Some("Native call returned no observed business result; inspect its owning Session before continuation. " + diagnostic),
              child.map(_.revision.value.toString), None, None)
          }
          next = intent._2.copy(results = state.results :+ result,
            stage = if (observed.isLeft) "Indeterminate"
              else if (member.localMain.nonEmpty && result.kind != "Terminal" && !contributionWait(member, result)) "AttentionRequired"
              else "Ready", attempting = None)
          saved <- _save_c(adapter, persistence, core, intent._1, next)
        } yield saved
        }
      }
    }.flatMap { (frontier, state) =>
      if (state.stage != "Ready") Consequence.success(frontier -> state)
      else _save_c(adapter, persistence, core, frontier, state.copy(stage =
        if (state.results.size == state.members.size && state.results.forall(_.kind == "Terminal")) "Completed" else "AttentionRequired"))
    }

  /** Terminal Git convergence does not prove a changed dependency was consumed
    * or checked by A. Keep that unavailable integration subject explicit. */
  private[admission] def dependencyBlock(state: Payload, member: Member): Option[String] = {
    val providers = state.dependencies.getOrElse(member.sessionId, Vector.empty)
    val unresolved = providers.filterNot(id => state.results.exists(r => r.sessionId == id && r.kind == "Terminal" && r.outcome == "converged"))
    if (unresolved.nonEmpty) Some("DependencyValidationPending: " + member.repositoryIdentity +
      " waits for selected provider Sessions " + unresolved.mkString(","))
    else {
      // Even identical provider trees cannot establish unchanged immutable
      // publication selection or the consumer's localProject inputs (P7-TS03/04).
      val unknown = providers.filter(_ => !state.consumerInputs.get(member.sessionId).exists(_.known))
      Option.when(unknown.nonEmpty)("DependencyIntegrationApplicabilityUnknown: consumer " + member.repositoryIdentity +
        " has selected provider Sessions " + unknown.mkString(",") +
        "; exact existing immutable consumer input is unavailable. Provider tree equality or movement does not establish consumed-input applicability; " +
        state.consumerInputs.get(member.sessionId).toVector.flatMap(c => c.before.diagnostic.toVector ++ c.after.toVector.flatMap(_.diagnostic)).mkString("; "))
    }
  }

  /** Explicit setup refresh may also revisit an unchanged pre-effect wait.
    * Every other unchanged or unknown attempt remains retained; Native Advance
    * owns every check/effect decision after the existing admission checks.
    */
  private[admission] def setupRefreshEligible(refreshsetup: Boolean, kind: String,
    condition: Option[String], progression: Option[ProgressionReference]): Boolean =
    refreshsetup && ((kind == "Error" && condition.contains("UncommittedChanges") &&
      progression.contains(ProgressionReference("sync.BoundaryAssessment"))) || kind == "Wait" && (condition.contains("ValidationSetupRequired") ||
      (condition.contains("purpose-materialization-preflight") &&
        progression.contains(ProgressionReference("sync.PurposePrepared")))))

  /** A retained local contribution must let the remaining declared cohort
    * members start; their completion is what releases this native barrier.
    * Human/work/setup and unknown waits do not authorize that progression.
    */
  private[admission] def contributionWait(member: Member, result: Result): Boolean =
    member.sessionId == result.sessionId && result.kind == "Wait" &&
      result.condition.contains("SharedTargetAwaitingContributions") &&
      member.localMain.flatMap(_.cohort).exists(_.sessions.contains(member.sessionId))

  /** Shared-target members may contribute in order while other targets run
    * independently. A contribution wait releases the next member, not a
    * dependent consumer that requires the provider's terminal convergence. */
  private def _cohort_launch_ready(state: Payload, member: Member): Boolean =
    state.members.iterator.flatMap(_.localMain.flatMap(_.cohort)).find(_.members.contains(member.sessionId)).forall { cohort =>
      !cohort.members.exists(state.attempts.contains) &&
        cohort.members.takeWhile(_ != member.sessionId).forall { identity =>
          state.results.exists(result => result.sessionId == identity && (result.kind == "Terminal" ||
            state.members.find(_.sessionId == identity).exists(contributionWait(_, result))))
        }
    }

  private def _cohort_wait_ready(state: Payload, member: Member): Boolean =
    member.localMain.flatMap(_.cohort).exists { cohort =>
      cohort.sessions.forall { identity =>
        state.results.exists(result => result.sessionId == identity && (result.kind == "Terminal" ||
          state.members.find(_.sessionId == identity).exists(contributionWait(_, result))))
      } && (cohort.sessions.lastOption.contains(member.sessionId) ||
        (cohort.sessions.lastOption.toVector ++ cohort.members.filterNot(cohort.sessions.contains)).forall { identity =>
          state.results.exists(result => result.sessionId == identity && result.kind == "Terminal")
        })
    }

  private def _resume_c(current: InstanceRecord, payload: Payload, adapter: WorkflowManagedMechanicalTransition[Payload],
    persistence: GoalPhaseRuntimeParameters.Persistence, storage: WorkflowComponentStorage,
    core: ActionCall.Core, repository: BindRepository, refreshsetup: Boolean = false): Consequence[(InstanceRecord, Payload)] =
    if (payload.dependencies.nonEmpty || payload.attempts.nonEmpty)
      _parallel_c(current, payload, adapter, persistence, storage, core, repository, refreshsetup)
    else
    payload.results.zipWithIndex.foldLeft(Consequence.success(current -> payload)) { case (prior, (result, ordinal)) =>
      prior.flatMap { (frontier, state) =>
        if (state.stage == "Indeterminate" || !Set("Request", "Wait", "Human", "Progress", "Error")(result.kind)) Consequence.success(frontier -> state)
        else for {
          member <- state.members.find(_.sessionId == result.sessionId).map(Consequence.success)
            .getOrElse(_invalid("outstanding result has no captured member"))
          instances <- storage.bindInstancesC()(using core.executionContext)
          child <- instances.loadC(InstanceIdentity(member.sessionId))(using core.executionContext)
            .flatMap(_.map(Consequence.success).getOrElse(_invalid("outstanding child Session is unavailable")))
          next <- if (result.revision.contains(child.revision.value.toString) && result.kind != "Progress" && !result.condition.contains("SharedTargetAwaitingContributions") &&
              !setupRefreshEligible(refreshsetup, result.kind, result.condition, child.currentProgression))
            Consequence.success(frontier -> state)
            else for {
              intent <- _save_c(adapter, persistence, core, frontier, state.copy(stage = "Attempting", attempting = Some(member.sessionId)))
              observed <- _execute_c(member, core, repository, start = false).map[Either[String, V.RepositorySyncAdvanceResult]](Right(_))
                .recoverWith(conclusion => Consequence.success(Left(conclusion.show)))
              after <- instances.loadC(InstanceIdentity(member.sessionId))(using core.executionContext)
              updated = observed match {
                case Right(value) => Result(member.repositoryIdentity, member.workspace, member.sessionId, value.kind,
                  value.outcome.getOrElse(if (value.kind == "Terminal") "completed" else "unfinished"), value.condition,
                  after.map(_.revision.value.toString), value.convergence.map(_.tip), value.convergence.map(_.tree),
                  _diagnostic(value),
                  _work_reference(value, after))
                case Left(diagnostic) => result.copy(kind = "Indeterminate", outcome = "indeterminate", work = None,
                  condition = Some("Native continuation returned no observed business result; inspect its owning Session. " + diagnostic),
                  revision = after.map(_.revision.value.toString))
              }
              saved <- _save_c(adapter, persistence, core, intent._1, state.copy(results = state.results.updated(ordinal, updated),
                stage = if (observed.isLeft) "Indeterminate" else "AttentionRequired", attempting = None))
            } yield saved
        } yield next
      }
    }.flatMap { (frontier, state) =>
      if (state.stage != "Indeterminate" && state.results.forall(result => result.kind == "Terminal" ||
          state.members.find(_.sessionId == result.sessionId).exists(member => contributionWait(member, result))))
        _save_c(adapter, persistence, core, frontier, state.copy(stage =
          if (state.results.size < state.members.size) "Ready"
          else if (state.results.forall(_.kind == "Terminal")) "Completed" else "AttentionRequired"))
      else Consequence.success(frontier -> state)
    }

  /** Each ready child has its own native Job/ActionTask/UoW. Persist launch intent
    * before submit; an intent lacking a confirmed Job ID is never resubmitted.
    * Launch the complete ready wave before awaiting its exact native jobs. Keep
    * this foreground owner alive; a custom ActionTask is not a durable replay
    * contract. Timeout/interruption retains UNKNOWN and never submits again.
    */
  private def _parallel_c(current: InstanceRecord, payload: Payload, adapter: WorkflowManagedMechanicalTransition[Payload],
    persistence: GoalPhaseRuntimeParameters.Persistence, storage: WorkflowComponentStorage,
    core: ActionCall.Core, repository: BindRepository, refresh: Boolean,
    retriedContributions: Set[String] = Set.empty): Consequence[(InstanceRecord, Payload)] = for {
    component <- core.component.map(Consequence.success).getOrElse(_invalid("native child jobs require the owning Component"))
    // A queued child must not wait behind its own outer worker. Unknown engine
    // capacity and the installed single-worker default retain synchronous native
    // execution; neither proves overlap. No new engine or worker pool is created.
    concurrent = component.jobEngine match {
      case engine: InMemoryJobEngine => engine.schedulerConfig.autoStartWorkers &&
        engine.schedulerConfig.workerCount >= (if (core.executionContext.jobContext.jobId.nonEmpty) 3 else 2)
      case _ => false
    }
    initial <- if (payload.consumerInputs.nonEmpty || payload.results.nonEmpty || payload.attempts.nonEmpty)
      Consequence.success(current -> payload)
      else for {
        before <- payload.members.filter(m => payload.dependencies.getOrElse(m.sessionId, Vector.empty).nonEmpty)
          .foldLeft(Consequence.success(Map.empty[String, RepositorySyncConsumedInputs.Change])) { (prior, member) => for {
            values <- prior
            observed <- RepositorySyncConsumedInputs.observeC(core, member)
          } yield values.updated(member.sessionId, RepositorySyncConsumedInputs.Change(observed, None)) }
        saved <- _save_c(adapter, persistence, core, current, payload.copy(consumerInputs = before))
      } yield saved
    collected <- _collect_c(initial._1, initial._2, adapter, persistence, storage, core, component.jobEngine)
    selected <- collected._2.members.foldLeft(Consequence.success(collected)) { (prior, member) => prior.flatMap { (frontier, state) =>
      state.consumerInputs.get(member.sessionId) match {
        case Some(change) if change.after.isEmpty && state.dependencies.getOrElse(member.sessionId, Vector.empty).forall(id =>
            state.results.exists(r => r.sessionId == id && r.kind == "Terminal" && r.outcome == "converged")) => for {
          observed <- RepositorySyncConsumedInputs.observeC(core, member)
          saved <- _save_c(adapter, persistence, core, frontier, state.copy(consumerInputs = state.consumerInputs.updated(member.sessionId, change.copy(after = Some(observed)))))
        } yield saved
        case _ => Consequence.success(frontier -> state)
      }
    }}
    launched <- selected._2.members.foldLeft(Consequence.success(selected)) { (prior, member) => prior.flatMap { (frontier, state) =>
      val previous = state.results.find(_.sessionId == member.sessionId)
      if (state.stage == "Indeterminate" || state.attempts.contains(member.sessionId) ||
          !_cohort_launch_ready(state, member) || dependencyBlock(state, member).nonEmpty ||
          previous.exists(r => !Set("Request", "Wait", "Human", "Progress", "Error")(r.kind)))
        Consequence.success(frontier -> state)
      else for {
        instances <- storage.bindInstancesC()(using core.executionContext)
        child <- instances.loadC(InstanceIdentity(member.sessionId))(using core.executionContext)
        contribution = previous.exists(contributionWait(member, _)) && !retriedContributions.contains(member.sessionId) &&
          _cohort_wait_ready(state, member)
        runnable = previous.isEmpty || child.exists(c => !previous.get.revision.contains(c.revision.value.toString) ||
          contribution || setupRefreshEligible(refresh, previous.get.kind, previous.get.condition, c.currentProgression))
        saved <- if (!runnable) Consequence.success(frontier -> state) else for {
          source <- if (!state.dependencies.valuesIterator.flatten.contains(member.sessionId) || state.sourceTrees.contains(member.sessionId))
            Consequence.success(state.sourceTrees)
            else RepositorySyncManagedGitObservation.observeInWorkspaceC(core, member.workspace).map(v => state.sourceTrees.updated(member.sessionId, v.tree))
          attempt = Attempt(None, previous.isEmpty, child.map(_.revision.value.toString))
          intent <- _save_c(adapter, persistence, core, frontier, state.copy(attempts = state.attempts.updated(member.sessionId, attempt),
            sourceTrees = source, setup = None))
          bound = if (selectedDependencyMember(state, member))
            RepositorySyncConsumedInputs.bind(core, member, state.consumerInputs.get(member.sessionId)) else core
          action = ChildAction(bound.action.request, member, repository, attempt.start)
          task = ActionTask(ActionId.generate(), action, component.actionEngine, Some(component))
          submitted <- component.jobEngine.submit(List(task), core.executionContext,
            JobSubmitOption(runMode = if (concurrent) JobRunMode.Async else JobRunMode.Sync))
            .map[Either[String, JobId]](Right(_)).recoverWith(c => Consequence.success(Left(c.show)))
          recorded <- submitted match {
            case Right(job) => _save_c(adapter, persistence, core, intent._1,
              intent._2.copy(attempts = intent._2.attempts.updated(member.sessionId, attempt.copy(job = Some(job.value)))))
            case Left(reason) => _save_c(adapter, persistence, core, intent._1,
              intent._2.copy(stage = "Indeterminate", setup = Some(Setup(state.members.map(_.root), "Unconfirmed native Job submission; no replay: " + reason))))
          }
        } yield recorded
      } yield saved
    }}
    state = launched._2
    completed = state.attempts.isEmpty && state.results.size == state.members.size &&
      state.results.forall(r => r.kind == "Terminal" && (state.dependencies.isEmpty || r.outcome == "converged"))
    reasons = state.members.filterNot(m => state.results.exists(_.sessionId == m.sessionId) || state.attempts.contains(m.sessionId))
      .flatMap(dependencyBlock(state, _))
    next = state.copy(stage = if (state.stage == "Indeterminate") state.stage else if (completed) "Completed" else "AttentionRequired",
      setup = if (state.stage == "Indeterminate") state.setup else Option.when(reasons.nonEmpty)(Setup(state.members.map(_.root), reasons.mkString("; "))))
    saved <- if (next == state) Consequence.success(launched) else _save_c(adapter, persistence, core, launched._1, next)
    // Uncertainty belongs to its own attempt. Already admitted Jobs still have
    // a foreground owner and must be joined and collected, even after another
    // submit or wait was unconfirmed. Never resubmit an intent without an ID.
    finished <- if (!saved._2.attempts.valuesIterator.exists(_.job.nonEmpty)) Consequence.success(saved)
      else for {
        waited <- joinConfirmedC(saved._2, job => component.jobEngine.awaitResult(job, 3600000L))
        collected <- _collect_c(saved._1, saved._2, adapter, persistence, storage, core, component.jobEngine, waited.results)
        result <- if (waited.diagnostics.nonEmpty) _save_c(adapter, persistence, core, collected._1,
          collected._2.copy(stage = "Indeterminate", setup = Some(Setup(collected._2.members.map(_.root),
            (collected._2.setup.toVector.map(_.diagnostic) ++ waited.diagnostics).distinct.mkString("; ")))))
          else if (collected._2.stage == "Indeterminate") Consequence.success(collected)
          else {
            val tried = saved._2.members.filter(member => saved._2.attempts.get(member.sessionId).exists(!_.start) &&
              saved._2.results.exists(contributionWait(member, _))).map(_.sessionId)
            _parallel_c(collected._1, collected._2, adapter, persistence, storage, core, repository, false,
              retriedContributions ++ tried)
          }
      } yield result
  } yield finished

  private[admission] final case class Joined(results: Map[String, JobResult], diagnostics: Vector[String])

  private[admission] def selectedDependencyMember(state: Payload, member: Member): Boolean =
    state.dependencies.contains(member.sessionId) || state.dependencies.valuesIterator.exists(_.contains(member.sessionId))

  /** Await each confirmed ID independently; an unconfirmed submission or a
    * failed wait must not detach another admitted Job or discard its receipt.
    * Returned failures are actual Job results and still need native collection.
    */
  private[admission] def joinConfirmedC(payload: Payload, await: JobId => Consequence[JobResult]): Consequence[Joined] =
    payload.members.foldLeft(Consequence.success(Joined(Map.empty, Vector.empty))) { (prior, member) => for {
      joined <- prior
      next <- payload.attempts.get(member.sessionId).flatMap(_.job) match {
        case None => Consequence.success(joined)
        case Some(id) =>
          val waiting = for {
            job <- JobId.parse(id)
            actual <- await(job)
          } yield actual
          waiting.map(actual => joined.copy(results = joined.results.updated(member.sessionId, actual)))
            .recoverWith(c => Consequence.success(joined.copy(diagnostics = joined.diagnostics :+
              ("Native child Job " + id + " wait is unconfirmed: " + c.show + "; no replay"))))
      }
    } yield next }

  private def _collect_c(current: InstanceRecord, payload: Payload, adapter: WorkflowManagedMechanicalTransition[Payload],
    persistence: GoalPhaseRuntimeParameters.Persistence, storage: WorkflowComponentStorage,
    core: ActionCall.Core, engine: JobEngine, observed: Map[String, JobResult] = Map.empty): Consequence[(InstanceRecord, Payload)] =
    payload.members.foldLeft(Consequence.success(current -> payload)) { (prior, member) => prior.flatMap { (frontier, state) =>
      state.attempts.get(member.sessionId) match {
        case None => Consequence.success(frontier -> state)
        case Some(attempt) if attempt.job.isEmpty =>
          _save_c(adapter, persistence, core, frontier, state.copy(stage = "Indeterminate",
            setup = Some(Setup(state.members.map(_.root), "Native child submission has an unconfirmed intent: " + member.sessionId + "; no replay"))))
        case Some(attempt) => for {
          job <- JobId.parse(attempt.job.get)
          result <- observed.get(member.sessionId).orElse(engine.getResult(job)) match {
            case None if engine.getStatus(job).isEmpty =>
              _save_c(adapter, persistence, core, frontier, state.copy(stage = "Indeterminate",
                setup = Some(Setup(state.members.map(_.root), "Native child Job is unavailable: " + attempt.job.get + "; no replay"))))
            case None => Consequence.success(frontier -> state)
            case Some(actual) => for {
              value <- actual match {
                case JobResult.Success(OperationResponse.RecordResponse(record)) =>
                  summon[ValueReader[V.RepositorySyncAdvanceResult]].readC(record)
                    .map[Either[String, V.RepositorySyncAdvanceResult]](Right(_))
                    .recoverWith(c => Consequence.success(Left(c.show)))
                case JobResult.Failure(conclusion) => Consequence.success(Left(conclusion.show))
                case _ => Consequence.success(Left("native child Job returned no business record"))
              }
              instances <- storage.bindInstancesC()(using core.executionContext)
              child <- instances.loadC(InstanceIdentity(member.sessionId))(using core.executionContext)
              observed = value match {
                case Right(v) if v.sessionId == member.sessionId => Result(member.repositoryIdentity, member.workspace, member.sessionId,
                  v.kind, v.outcome.getOrElse(if (v.kind == "Terminal") "completed" else "unfinished"), v.condition,
                  child.map(_.revision.value.toString), v.convergence.map(_.tip), v.convergence.map(_.tree), _diagnostic(v), _work_reference(v, child))
                case _ => Result(member.repositoryIdentity, member.workspace, member.sessionId, "Indeterminate", "indeterminate",
                  Some("Native child Job " + attempt.job.get + " has no matching observed result; no replay. " + value.left.toOption.getOrElse("crossed Session")),
                  child.map(_.revision.value.toString), None, None)
              }
              saved <- _save_c(adapter, persistence, core, frontier, state.copy(
                results = state.results.filterNot(_.sessionId == member.sessionId) :+ observed,
                attempts = state.attempts - member.sessionId,
                stage = if (observed.kind == "Indeterminate") "Indeterminate" else state.stage))
            } yield saved
          }
        } yield result
      }
    }}

  private final case class ChildAction(request: Request, member: Member, repository: BindRepository, start: Boolean) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = _execute_c(member, core, repository, start).map(value => NativeWorkflowOperationOutput.response(value))
    }
  }

  /** Keep the actual native refusal visible without expanding captures or work.
    * The condition and exact child revision remain alongside this bounded text.
    */
  private def _diagnostic(value: V.RepositorySyncAdvanceResult): Option[String] =
    if (value.condition.contains("ValidationSetupRequired")) Some(value.message)
    else if (value.kind == "Wait") Some(value.message.take(4096))
    else None

  private def _execute_c(member: Member, core: ActionCall.Core, repository: BindRepository,
    start: Boolean = true): Consequence[V.RepositorySyncAdvanceResult] = {
    def _invoke_[A](operation: String, properties: List[Property])(using reader: ValueReader[A]): Consequence[A] = for {
      context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(member.sessionId))
      request = RepositorySyncConsumedInputs.inherit(core, Request.of(
        component = SmWorkflowComponent.name, service = "RepositorySyncService", operation = operation,
        properties = properties ++ member.localMain.toVector.map(RepositorySyncLocalMain.planProperty) :+
          Property(_workspace_key, Workspace(member.workspace), None)))
      selectedcore = core.copy(action = ScopeAction(request), executionContext = context)
      service <- repository(request, selectedcore, member.target)
      selected <- service.operations.operations.toVector.find(_.name == operation).map(Consequence.success)
        .getOrElse(_invalid("native single-repository operation is unavailable"))
      action <- selected.createOperationRequest(request)
      response <- action match {
        case command: CommandAction => command.createCall(selectedcore.copy(action = command)).execute()
        case _ => _invalid("native repository operation is not a command")
      }
      value <- response match {
        case OperationResponse.RecordResponse(record) => reader.readC(record)
        case _ => _invalid("native repository operation returned no business record")
      }
    } yield value
    for {
      _ <- if (!start) Consequence.unit else _invoke_[V.RepositorySyncStartResult]("startRepositorySync", List(
        Property("sessionId", member.sessionId, None), Property("input", Record.data((
          Vector("repositoryIdentity" -> member.repositoryIdentity, "syncMode" -> "FULL_STATE") ++
            member.policies.toVector.flatMap(selected => Vector("mainIntegration" -> selected.mainIntegration,
              "uncommittedPolicy" -> selected.uncommittedPolicy)))*), None))).map(_ => ())
      result <- {
        def advance(remaining: Int): Consequence[V.RepositorySyncAdvanceResult] =
          _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", List(Property("sessionId", member.sessionId, None))).flatMap { value =>
            if (value.kind == "Progress" && remaining > 0) advance(remaining - 1) else Consequence.success(value)
          }
        advance(16)
      }
    } yield result
  }

  private final case class ScopeAction(request: Request) extends CommandAction {
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = _invalid("directory scope data is not an executable operation")
    }
  }

  private val _operation = StateMachineOperationIdentity("RepositorySyncService", syncOperation)
  private[admission] val payloadCodec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
    val typeIdentity = "RepositorySyncDirectoryListRecord"
    def encode(value: Payload): Json = value.asJson
    def decode(value: Json): Either[String, Payload] = value.as[Payload].left.map(_.message)
  }
  private def _reference(identity: String, revision: String): WorkflowMechanicalTransition.Reference =
    WorkflowMechanicalTransition.Reference(identity + ":directory-list", revision)
  private def _entry(current: InstanceRecord, stage: String, cause: String): HistoryEntry =
    HistoryEntry(HistorySequence(current.history.size.toLong + 1), current.revision.next, Lifecycle.Active,
      Some(ProgressionReference("sync." + stage)), current.correlation, CausationReference(cause), None, None, None)
  private def _save_c(adapter: WorkflowManagedMechanicalTransition[Payload], persistence: GoalPhaseRuntimeParameters.Persistence,
    core: ActionCall.Core, current: InstanceRecord, payload: Payload): Consequence[(InstanceRecord, Payload)] = {
    val reference = _reference(current.identity.value, current.revision.next.value.toString)
    adapter.executeC(WorkflowMechanicalTransition.Intent(persistence.configuration, reference, current, current.revision,
      _entry(current, "DirectoryList" + payload.stage, reference.identity), TypedValue(payloadCodec.typeIdentity, payload)),
      ConsequenceT.pure[[X] =>> org.goldenport.cncf.Program[UnitOfWorkOp, X], Unit](()))(using core.executionContext)
      .map(saved => saved.receipt.next -> saved.receipt.payload.value)
  }
  private def _admit_c(payload: Payload, participant: String): Consequence[Unit] =
    if (payload.participant == participant) Consequence.unit
    else Consequence.securityPermissionDenied("Directory-list result belongs to another participant")
  private def _work_reference(value: V.RepositorySyncAdvanceResult, child: Option[InstanceRecord]): Option[Work] =
    value.submitOperation.flatMap { operation =>
      value.issuedWorkContext.map(context => Work(operation, context.continuationId, context.expectedRevision))
        .orElse(Option.when(value.kind == "Human")(child.flatMap(_.suspension).map(boundary =>
          Work(operation, boundary.continuationIdentity.value, boundary.expectedRevision.value.toString))).flatten)
    }

  /** Retain only the actual issued identity/revision. Sources and candidate
    * bodies stay with their existing native issued-work owner, including when
    * an older aggregate revision is read after the child has progressed.
    */
  private def _work_c(storage: WorkflowComponentStorage, core: ActionCall.Core, member: Member,
    reference: Work): Consequence[V.RepositorySyncAdvanceResult] = for {
    context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(member.sessionId))
    call = scopedCore(core.copy(executionContext = context), member.workspace)
    value <- {
      def load[A](operation: StateMachineOperationIdentity, codec: WorkflowResultJsonV1.PayloadCodec[A],
        render: WorkflowProtocolV1.WorkflowInteraction[A, Nothing] => Consequence[V.RepositorySyncAdvanceResult]):
          Consequence[V.RepositorySyncAdvanceResult] = for {
        issued <- storage.bindIssuedC(operation, codec)
        work <- issued.loadC(org.goldenport.cncf.workflow.ContinuationIdentity(reference.continuationId))(using context)
          .flatMap(_.map(Consequence.success).getOrElse(_invalid("selected native issued work is unavailable")))
        _ <- work.current match {
          case order: WorkflowProtocolV1.WorkflowContinuation.WorkOrder[?] if
              work.handle.instanceIdentity.value == member.sessionId &&
              order.request.expectedRevision.value == reference.expectedRevision &&
              order.request.continuationId.value == reference.continuationId => Consequence.unit
          case _ => _invalid("selected work crosses its native child Session, identity or revision")
        }
        value <- render(work)
      } yield value
      reference.operation match {
        case "submitRepositorySyncPolicyDecision" => for {
          questions <- storage.bindDecisionC(RepositorySyncPolicyDecision.completionOperation, RepositorySyncPolicyDecision.inputCodec)
          question <- questions.loadC(org.goldenport.cncf.workflow.ContinuationIdentity(reference.continuationId))(using context)
            .flatMap(_.map(Consequence.success).getOrElse(_invalid("selected native policy question is unavailable")))
          _ <- question.current match {
            case decision: WorkflowProtocolV1.WorkflowContinuation.Decision[?] if
                question.handle.instanceIdentity.value == member.sessionId &&
                decision.request.expectedRevision.value == reference.expectedRevision => Consequence.unit
            case _ => _invalid("selected policy question crosses its native child Session or revision")
          }
          value <- RepositorySyncPolicyDecision.renderC(question, call)
        } yield value
        case "submitRepositoryPurposeResult" => load(RepositorySyncPurposeContinuation.completionOperation,
          RepositorySyncPurpose.inputCodec, NativeWorkflowOperationOutput.repositoryPurposeRequestC)
        case "submitRepositorySyncWorkResult" => load(RepositorySyncNativeConflictContinuation.completionOperation,
          RepositorySyncConflictSemantic.inputCodec, NativeWorkflowOperationOutput.repositoryConflictRequestC)
        case "submitNativeMergeReview" => load(RepositorySyncNativeMergeReviewContinuation.completionOperation,
          RepositorySyncNativeMergeReview.inputCodec, work => NativeWorkflowOperationOutput.repositoryReviewRequestC(work, call))
        case "submitNativeMergeRepair" => load(RepositorySyncNativeMergeRepairContinuation.completionOperation,
          RepositorySyncNativeMergeRepair.inputCodec, NativeWorkflowOperationOutput.repositoryRepairRequestC)
        case "submitRepositorySyncPolicyDecision" => for {
          questions <- storage.bindDecisionC(RepositorySyncPolicyDecision.completionOperation, RepositorySyncPolicyDecision.inputCodec)
          question <- questions.loadC(org.goldenport.cncf.workflow.ContinuationIdentity(reference.continuationId))(using context)
            .flatMap(_.map(Consequence.success).getOrElse(_invalid("selected native policy question is unavailable")))
          _ <- question.current match {
            case decision: WorkflowProtocolV1.WorkflowContinuation.Decision[?] if
                question.handle.instanceIdentity.value == member.sessionId && decision.request.expectedRevision.value == reference.expectedRevision &&
                decision.request.continuationId.value == reference.continuationId => Consequence.unit
            case _ => _invalid("selected policy question crosses its native child Session, identity or revision")
          }
          value <- RepositorySyncPolicyDecision.renderC(question, call)
        } yield value
        case "submitRepositoryConflictDecision" => for {
          questions <- storage.bindDecisionC(RepositorySyncHumanConflict.completionOperation, RepositorySyncHumanConflict.inputCodec)
          question <- questions.loadC(org.goldenport.cncf.workflow.ContinuationIdentity(reference.continuationId))(using context)
            .flatMap(_.map(Consequence.success).getOrElse(_invalid("selected native Human question is unavailable")))
          _ <- question.current match {
            case decision: WorkflowProtocolV1.WorkflowContinuation.Decision[?] if
                question.handle.instanceIdentity.value == member.sessionId &&
                decision.request.expectedRevision.value == reference.expectedRevision &&
                decision.request.continuationId.value == reference.continuationId => Consequence.unit
            case _ => _invalid("selected Human question crosses its native child Session, identity or revision")
          }
          value <- NativeWorkflowOperationOutput.repositoryHumanC(question, call)
        } yield value
        case _ => _invalid("selected work has no supported native repository owner")
      }
    }
  } yield value

  private def _response_c(identity: String, revision: String, payload: Payload,
    storage: WorkflowComponentStorage, core: ActionCall.Core): Consequence[OperationResponse] = for {
    summaries <- payload.results.foldLeft(Consequence.success(Vector.empty[V.RepositoryDirectorySummary])) { (prior, result) => for {
      values <- prior
      member <- payload.members.find(_.sessionId == result.sessionId).map(Consequence.success)
        .getOrElse(_invalid("result has no captured repository member"))
      work <- result.work.fold(Consequence.success(Option.empty[V.RepositorySyncAdvanceResult]))(reference =>
        _work_c(storage, core, member, reference).map(Some(_)))
      retained <- work match {
        case Some(value) if value.kind == "Human" => WorkflowRetainedWork.confirmedDecisionC(value.toRecord(), core).map(Some(_))
        case _ => work.flatMap(_.issuedWorkContext).fold(Consequence.success(Option.empty[V.WorkflowRetainedWorkReference]))(
          context => WorkflowRetainedWork.confirmedC(context, core).map(Some(_)))
      }
    } yield values :+ V.RepositoryDirectorySummary(result.repositoryIdentity, result.workspace,
      result.sessionId, result.kind, result.outcome, result.condition, result.revision, result.tip, result.tree, result.diagnostic,
      if (retained.nonEmpty) None else work, retained, work.map(value => NativeWorkflowCompactResponse.summary(value.toRecord()))) }
  } yield NativeWorkflowOperationOutput.response(V.RepositoryDirectoryBatchResult(identity,
      if (payload.stage == "Attempting") "Indeterminate" else payload.stage,
      V.RepositoryDirectoryResultReference("RepositorySyncWorkflow", identity,
        _reference(identity, revision).identity, revision, readOperation),
      summaries,
      payload.members.filterNot(member => payload.results.exists(_.sessionId == member.sessionId) || payload.attempting.contains(member.sessionId) || payload.attempts.contains(member.sessionId))
        .map(_.repositoryIdentity), payload.attempting,
      payload.setup.map(setup => V.RepositoryDirectoryConfigurationRequired(setup.directories, setup.diagnostic, refreshOperation))))
  private def _one_c(values: Vector[Json], reason: String): Consequence[Json] =
    if (values.size == 1) Consequence.success(values.head) else _invalid(reason)
  private def _text_c(value: Json, name: String): Consequence[String] =
    value.hcursor.get[String](name).toOption.filter(text => text.nonEmpty && text.trim == text && !text.exists(_.isControl))
      .map(Consequence.success).getOrElse(_invalid("configured Git destination requires " + name))
  private def _invalid[A](reason: String): Consequence[A] = Consequence.configurationInvalid("Repository directory list " + reason)
}
