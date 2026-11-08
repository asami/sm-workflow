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
  * @version Nov.  8, 2026
  */
private[smworkflow] object RepositorySyncDirectoryList {
  val syncOperation = "syncRepositoryDirectories"
  val readOperation = "readRepositoryDirectoryResult"
  val operations = Set(syncOperation, readOperation)
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
    localBranch: String, remote: String, remoteBranch: String, endpoint: String, sessionId: String) {
    def target: Target = Target(Path.of(root), localBranch, remote, remoteBranch, endpoint)
  }
  final case class Work(operation: String, continuationId: String, expectedRevision: String)
  private given Encoder[Work] = deriveEncoder
  private given Decoder[Work] = deriveDecoder
  final case class Result(repositoryIdentity: String, workspace: String, sessionId: String,
    kind: String, outcome: String, condition: Option[String], revision: Option[String],
    tip: Option[String], tree: Option[String], diagnostic: Option[String] = None, work: Option[Work] = None)
  final case class Payload(participant: String, members: Vector[Member], results: Vector[Result],
    stage: String, attempting: Option[String])
  private given Encoder[Member] = deriveEncoder
  private given Decoder[Member] = deriveDecoder
  private given Encoder[Result] = deriveEncoder[Result].mapJson(_.mapObject(_.filter {
    case ("diagnostic" | "work", value) => !value.isNull
    case _ => true
  }))
  private given Decoder[Result] = deriveDecoder
  private given Encoder[Payload] = deriveEncoder
  private given Decoder[Payload] = deriveDecoder

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

  def bindWorkspace(service: spec.ServiceDefinition, workspaceIdentity: String): spec.ServiceDefinition =
    NativeWorkflowDescriptionMetadata.service(service, spec.OperationDefinitionGroup(NonEmptyVector.fromVectorUnsafe(
      service.operations.operations.toVector.map { operation => new spec.OperationDefinition {
        val specification = operation.specification
        def createOperationRequest(request: Request): Consequence[OperationRequest] =
          operation.createOperationRequest(_scoped_request(request, workspaceIdentity))
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
  def resolveC(directories: Vector[String], plan: Json, sessionId: String): Consequence[Vector[Member]] = for {
    paths <- decodeDirectoriesC(directories.asJson)
    workspaces <- plan.hcursor.get[Vector[Json]]("workspaces").fold(e => _invalid(e.message), Consequence.success)
    repositories <- plan.hcursor.get[Vector[Json]]("gitRepositories").fold(e => _invalid(e.message), Consequence.success)
    members <- paths.zipWithIndex.foldLeft(Consequence.success(Vector.empty[Member])) { case (prior, (root, ordinal)) => for {
      values <- prior
      configured <- _one_c(workspaces.filter(workspace =>
        workspace.hcursor.get[String]("root").toOption.exists(value =>
          Try(Path.of(value)).toOption.exists(path => path.isAbsolute && path.normalize.toString == root)) &&
        repositories.exists(repository => repository.hcursor.get[String]("target").toOption ==
          workspace.hcursor.get[String]("identity").toOption)),
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
        new Operation(true, creation, parameters, persistence, repository))))

  private final class Operation(read: Boolean, creation: ComponentCreate,
    parameters: ComponentInitializationParameters, persistence: GoalPhaseRuntimeParameters.Persistence,
    repository: BindRepository) extends spec.OperationDefinition {
    val specification = NativeWorkflowDescriptionMetadata.operation(if (read)
      SmWorkflowComponent.RepositorySyncServiceService.ReadRepositoryDirectoryResultOperation
      else SmWorkflowComponent.RepositorySyncServiceService.SyncRepositoryDirectoriesOperation)
    def createOperationRequest(request: Request): Consequence[OperationRequest] =
      if (request == null || !request.service.contains("RepositorySyncService") || request.operation != specification.name)
        _invalid("directory-list operation differs from its declared service")
      else Consequence.success(Action(request, read, creation, parameters, persistence, repository))
  }
  private final case class Action(request: Request, read: Boolean, creation: ComponentCreate,
    parameters: ComponentInitializationParameters, persistence: GoalPhaseRuntimeParameters.Persistence,
    repository: BindRepository) extends CommandAction {
    override def commandExecutionMode = CommandExecutionMode.Sync
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = for {
        identity <- if (read) NativeWorkflowOperationInput.textC(Action.this.request, "sessionId")
          else NativeWorkflowOperationInput.startSessionC(Action.this.request, core.executionContext)
        context <- NativeWorkflowSessionInput.contextC(core.executionContext, Some(identity))
        call = core.copy(executionContext = context)
        participant = SecuritySubject.from(context.security).subjectId
        _ <- GoalPhaseParticipantAdmission.admitC(participant, context)
        definition <- RepositorySyncDefinitionBinding.bindC
        storage <- WorkflowComponentStorage.bindC(creation, SmWorkflowComponent.componentId, persistence.configuration,
          Vector(definition), persistence.instances, persistence.continuations)
        instances <- storage.bindInstancesC()(using context)
        adapter <- storage.bindMechanicalC(_operation, _codec)
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
          input <- NativeWorkflowOperationInput.recordC(Action.this.request, "input")
          _ <- if (DevelopmentOperationalView.json(input).asObject.exists(_.keys.toSet == Set("syncMode")) &&
              input.getString("syncMode").contains("FULL_STATE")) Consequence.unit
            else _invalid("directory-list input requires only explicit syncMode FULL_STATE")
          loaded <- existing match {
            case Some(current) => for {
              retained <- adapter.loadRetainedC(_reference(identity, current.revision.value.toString))(using context)
                .flatMap(_.map(Consequence.success).getOrElse(_invalid("Session is not a retained directory-list execution")))
              _ <- _admit_c(retained.receipt.payload.value, participant)
              _ <- if (retained.receipt.next == current) Consequence.unit else _invalid("native directory frontier differs")
            } yield current -> retained.receipt.payload.value
            case None => for {
              directories <- parameters.resolve(declaration).flatMap(_.value.map(Consequence.success)
                .getOrElse(_invalid("sm-workflow.repository-sync.directories is not configured")))
              raw <- GoalPhaseInputRecordReadPolicy.storedValue(creation.configuration.configuration.values,
                CncfConfigurationParameterCatalog.SUBSYSTEM_PROCESS_EXECUTION_PLAN_KEY) match {
                case Some(ConfigurationValue.StringValue(value)) => io.circe.parser.parse(value)
                  .fold(e => _invalid(e.message), Consequence.success)
                case _ => _invalid("no managed repository execution profile is configured")
              }
              members <- resolveC(directories, raw, identity)
              initial <- InstanceRecord(InstanceIdentity(identity), definition, initialRevision, Lifecycle.NotStarted,
                None, CorrelationReference("directory-list:" + identity), CausationReference("directory-list-start:" + identity),
                None, None, None, Vector.empty).validateC
              _ <- instances.createC(initial)(using context)
              active <- instances.appendC(initial.identity, initial.revision,
                _entry(initial, "DirectoryListStarted", "directory-list-start:" + identity))(using context)
              saved <- _save_c(adapter, persistence, call, active, Payload(participant, members, Vector.empty, "Ready", None))
            } yield saved
          }
          result <- if (loaded._2.stage == "AttentionRequired")
              _resume_c(loaded._1, loaded._2, adapter, persistence, storage, call, repository)
            else if (loaded._2.stage != "Ready") Consequence.success(loaded)
            else _run_c(loaded._1, loaded._2, adapter, persistence, storage, call, repository)
          response <- _response_c(identity, result._1.revision.value.toString, result._2, storage, call)
        } yield response
      } yield response
    }
  }

  private def _run_c(current: InstanceRecord, payload: Payload, adapter: WorkflowManagedMechanicalTransition[Payload],
    persistence: GoalPhaseRuntimeParameters.Persistence, storage: WorkflowComponentStorage,
    core: ActionCall.Core, repository: BindRepository): Consequence[(InstanceRecord, Payload)] =
    payload.members.drop(payload.results.size).foldLeft(Consequence.success(current -> payload)) { (prior, member) =>
      prior.flatMap { (frontier, state) =>
        if (state.stage != "Ready") Consequence.success(frontier -> state)
        else for {
          intent <- _save_c(adapter, persistence, core, frontier, state.copy(stage = "Attempting", attempting = Some(member.sessionId)))
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
          next = state.copy(results = state.results :+ result,
            stage = if (observed.isLeft) "Indeterminate" else "Ready", attempting = None)
          saved <- _save_c(adapter, persistence, core, intent._1, next)
        } yield saved
      }
    }.flatMap { (frontier, state) =>
      if (state.stage != "Ready") Consequence.success(frontier -> state)
      else _save_c(adapter, persistence, core, frontier, state.copy(stage =
        if (state.results.forall(_.kind == "Terminal")) "Completed" else "AttentionRequired"))
    }

  /** Accepted paired work advances the child frontier. Only those changed
    * outstanding children are called again; terminal, unchanged and unknown
    * attempts are retained. Native Advance owns every check/effect decision.
    */
  private def _resume_c(current: InstanceRecord, payload: Payload, adapter: WorkflowManagedMechanicalTransition[Payload],
    persistence: GoalPhaseRuntimeParameters.Persistence, storage: WorkflowComponentStorage,
    core: ActionCall.Core, repository: BindRepository): Consequence[(InstanceRecord, Payload)] =
    payload.results.zipWithIndex.foldLeft(Consequence.success(current -> payload)) { case (prior, (result, ordinal)) =>
      prior.flatMap { (frontier, state) =>
        if (state.stage == "Indeterminate" || !Set("Request", "Wait", "Human")(result.kind)) Consequence.success(frontier -> state)
        else for {
          member <- state.members.find(_.sessionId == result.sessionId).map(Consequence.success)
            .getOrElse(_invalid("outstanding result has no captured member"))
          instances <- storage.bindInstancesC()(using core.executionContext)
          child <- instances.loadC(InstanceIdentity(member.sessionId))(using core.executionContext)
            .flatMap(_.map(Consequence.success).getOrElse(_invalid("outstanding child Session is unavailable")))
          next <- if (result.revision.contains(child.revision.value.toString)) Consequence.success(frontier -> state)
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
      if (state.stage != "Indeterminate" && state.results.forall(_.kind == "Terminal"))
        _save_c(adapter, persistence, core, frontier, state.copy(stage = "Completed"))
      else Consequence.success(frontier -> state)
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
      request = Request.of(component = SmWorkflowComponent.name, service = "RepositorySyncService", operation = operation,
        properties = properties :+ Property(_workspace_key, Workspace(member.workspace), None))
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
        Property("sessionId", member.sessionId, None), Property("input", Record.data(
          "repositoryIdentity" -> member.repositoryIdentity, "syncMode" -> "FULL_STATE"), None))).map(_ => ())
      result <- _invoke_[V.RepositorySyncAdvanceResult]("advanceRepositorySync", List(Property("sessionId", member.sessionId, None)))
    } yield result
  }

  private final case class ScopeAction(request: Request) extends CommandAction {
    def createCall(c: ActionCall.Core): ActionCall = new ProcedureActionCall {
      val core = c
      def execute(): Consequence[OperationResponse] = _invalid("directory scope data is not an executable operation")
    }
  }

  private val _operation = StateMachineOperationIdentity("RepositorySyncService", syncOperation)
  private val _codec = new WorkflowResultJsonV1.PayloadCodec[Payload] {
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
      _entry(current, "DirectoryList" + payload.stage, reference.identity), TypedValue(_codec.typeIdentity, payload)),
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
        case "submitRepositoryPurposeResult" => load(RepositorySyncPurposeContinuation.completionOperation,
          RepositorySyncPurpose.inputCodec, NativeWorkflowOperationOutput.repositoryPurposeRequestC)
        case "submitRepositorySyncWorkResult" => load(RepositorySyncNativeConflictContinuation.completionOperation,
          RepositorySyncConflictSemantic.inputCodec, NativeWorkflowOperationOutput.repositoryConflictRequestC)
        case "submitNativeMergeReview" => load(RepositorySyncNativeMergeReviewContinuation.completionOperation,
          RepositorySyncNativeMergeReview.inputCodec, work => NativeWorkflowOperationOutput.repositoryReviewRequestC(work, call))
        case "submitNativeMergeRepair" => load(RepositorySyncNativeMergeRepairContinuation.completionOperation,
          RepositorySyncNativeMergeRepair.inputCodec, NativeWorkflowOperationOutput.repositoryRepairRequestC)
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
    } yield values :+ V.RepositoryDirectorySummary(result.repositoryIdentity, result.workspace,
      result.sessionId, result.kind, result.outcome, result.condition, result.revision, result.tip, result.tree, result.diagnostic, work) }
  } yield NativeWorkflowOperationOutput.response(V.RepositoryDirectoryBatchResult(identity,
      if (payload.stage == "Attempting") "Indeterminate" else payload.stage,
      V.RepositoryDirectoryResultReference("RepositorySyncWorkflow", identity,
        _reference(identity, revision).identity, revision, readOperation),
      summaries,
      payload.members.drop(payload.results.size).filterNot(member => payload.attempting.contains(member.sessionId))
        .map(_.repositoryIdentity), payload.attempting))
  private def _one_c(values: Vector[Json], reason: String): Consequence[Json] =
    if (values.size == 1) Consequence.success(values.head) else _invalid(reason)
  private def _text_c(value: Json, name: String): Consequence[String] =
    value.hcursor.get[String](name).toOption.filter(text => text.nonEmpty && text.trim == text && !text.exists(_.isControl))
      .map(Consequence.success).getOrElse(_invalid("configured Git destination requires " + name))
  private def _invalid[A](reason: String): Consequence[A] = Consequence.configurationInvalid("Repository directory list " + reason)
}
