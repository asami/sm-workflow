package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.config.{ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.datastore.{ComponentRecordStorage, DataStore}
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceFilePath, WorkspaceId}
import org.goldenport.cncf.security.SecuritySubject
import org.goldenport.record.Record
import org.simplemodeling.model.datatype.EntityCollectionId
import org.simplemodeling.textus.smworkflow.value as V

/** Host configuration and component-managed facts for the one publication path.
  * There is no shell, external AI execution runner or caller-selected argv here.
  * @since   Oct. 26, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object LocalPublicationRuntime {
  final case class CatalogOutput(targetIdentity: String, path: String, selection: String)
  final case class Policy(storeName: String, collection: DataStore.CollectionId,
    targets: Vector[LocalPublication.Target], catalogOutputs: Vector[CatalogOutput] = Vector.empty)
  private final case class Wire(storeName: String, collection: String, targets: Vector[LocalPublication.Target],
    catalogOutputs: Vector[CatalogOutput])
  val declaration = ComponentParameterKey.optional("sm-workflow.local-publication",
    ComponentParameterDecoder[Policy] { value =>
      def _json_(v: ConfigurationValue): Json = v match {
        case ConfigurationValue.StringValue(s) => Json.fromString(s)
        case ConfigurationValue.NumberValue(n) => Json.fromBigDecimal(n)
        case ConfigurationValue.BooleanValue(b) => Json.fromBoolean(b)
        case ConfigurationValue.ListValue(xs) => Json.fromValues(xs.map(_json_))
        case ConfigurationValue.ObjectValue(xs) => Json.obj(xs.toVector.map((k, v) => k -> _json_(v))*)
        case ConfigurationValue.NullValue => Json.Null
      }
      val parsed = value match {
        case ConfigurationValue.StringValue(s) => io.circe.parser.parse(s).left.map(_.message)
        case v: ConfigurationValue.ObjectValue => Right(_json_(v))
        case _ => Left("Publication configuration requires a structured object")
      }
      parsed.flatMap { raw =>
        if (!raw.asObject.exists(_.keys.forall(Set("storeName", "collection", "targets", "catalogOutputs"))))
          Left("Publication configuration contains an undeclared field")
        else {
          val normalized = if (raw.hcursor.downField("catalogOutputs").focus.isEmpty)
            raw.mapObject(_.add("catalogOutputs", Json.arr())) else raw
          normalized.hcursor.get[Vector[Json]]("catalogOutputs").left.map(_.message).flatMap { outputs =>
            if (outputs.exists(o => !o.asObject.exists(_.keys.toSet == Set("targetIdentity", "path", "selection"))))
              Left("Publication catalog output contains missing or undeclared fields")
            else normalized.hcursor.get[Vector[Json]]("targets").left.map(_.message).flatMap { targets =>
          if (targets.exists(t => !t.asObject.exists(_.keys.forall(Set("identity", "component", "workspace", "dependency", "beforeChecks", "beforeSync")))))
            Left("Publication target contains an undeclared field")
          else normalized.mapObject(_.add("targets", Json.fromValues(targets.map(t =>
            if (t.hcursor.downField("beforeChecks").focus.isEmpty) t.mapObject(_.add("beforeChecks", Json.False)) else t))))
            .as[Wire].left.map(_.message)
            }
          }
        }
      }.fold(Consequence.configurationInvalid(_), w => {
        EntityCollectionId.parse(w.collection).toOption.map(id => Policy(w.storeName, DataStore.CollectionId.EntityStore(id), w.targets, w.catalogOutputs))
          .map(p => validateC(p).map(_ => p)).getOrElse(Consequence.configurationInvalid("Publication collection is invalid"))
      })
    })

  def validateC(policy: Policy): Consequence[Unit] =
    if (policy != null && policy.storeName != null && policy.storeName.nonEmpty && policy.collection != null &&
        policy.targets != null && policy.targets.nonEmpty && policy.targets.forall(t => t != null &&
          t.identity != null && t.identity.nonEmpty && t.component != null && t.component.nonEmpty &&
          t.workspace != null && org.goldenport.cncf.workspace.WorkspaceId.parseC(t.workspace).isSuccess &&
          t.dependency != null) && policy.targets.map(_.identity).distinct.size == policy.targets.size &&
        policy.catalogOutputs != null && policy.catalogOutputs.size <= 16 && policy.catalogOutputs.forall(_ != null) &&
        policy.catalogOutputs.map(_.targetIdentity).distinct.size == policy.catalogOutputs.size &&
        policy.catalogOutputs.forall(o => o != null && o.targetIdentity != null &&
          policy.targets.exists(_.identity == o.targetIdentity) && o.path != null &&
          o.path.startsWith("src/main/catalog/car/") && o.path.endsWith(".yaml") &&
          GitPath.parseC(o.path).isSuccess && WorkspaceFilePath.parseC(o.path).isSuccess &&
          o.selection != null && GitSelectionId.parseC(o.selection).isSuccess))
      Consequence.unit else Consequence.configurationInvalid("Local publication host targets are incomplete or duplicate")

  private def _catalog_output_c(core: ActionCall.Core, target: LocalPublication.Target,
    output: CatalogOutput, indextree: String): Consequence[Boolean] = for {
    tree <- GitObjectId.parseC(indextree)
    selection <- GitSelectionId.parseC(output.selection)
    entries <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.TreeEntries(tree, selection), target.workspace).flatMap(_.valueC)
    _ <- if (entries.isEmpty || (entries.size == 1 && entries.head.path.value == output.path)) Consequence.unit
      else Consequence.stateConflict("Publication catalog selection returned crossed or extra paths")
    projected <- entries.headOption.flatMap(_.entry).filter(e => Set("100644", "100755")(e.mode)) match {
      case None => Consequence.success(false)
      case Some(entry) => for {
        blob <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.BlobContent(entry.blob), target.workspace).flatMap(_.valueC)
        workspace <- WorkspaceId.parseC(target.workspace)
        path <- WorkspaceFilePath.parseC(output.path)
        actual <- WorkflowFileExecution.readTextC(core, FileTarget(workspace), path)
        _ <- if (actual != null && actual.target == FileTarget(workspace) && actual.path == path) Consequence.unit
          else Consequence.stateConflict("Publication catalog File result differs from its target or path")
      } yield blob.text.exists(indexed => indexed.getBytes(java.nio.charset.StandardCharsets.UTF_8).toVector == blob.bytes &&
        !indexed.contains('\u0000') && actual.text.exists(working => !working.contains('\u0000') &&
          LocalPublication.catalogTimestampOnly(target.component, indexed, working)))
    }
  } yield projected

  private def _port(core: ActionCall.Core, policy: Policy): LocalPublication.Port = new LocalPublication.Port {
    private def _bound(key: String) = ComponentRecordStorage.bindForCallC(core, policy.storeName,
      policy.collection, DataStore.StringEntryId(key))
    def loadC(key: String): Consequence[Option[Json]] = _bound(key).flatMap(_.loadC()).flatMap {
      case None => Consequence.success(None)
      case Some(row) => row.getString("localPublicationPayload").map(raw => io.circe.parser.parse(raw)
        .fold(e => Consequence.stateConflict(e.message), j => Consequence.success(Some(j))))
        .getOrElse(Consequence.stateConflict("Retained local publication has no typed payload"))
    }
    def createC(key: String, value: Json): Consequence[Unit] =
      _bound(key).flatMap(_.createC(Record.data("localPublicationPayload" -> value.noSpaces)))
    def newIdentity(): String = core.executionContext.idGeneration.opaqueId("local-publication-continuation")
    def sourceC(target: LocalPublication.Target): Consequence[LocalPublication.Source] = for {
      observed <- RepositorySyncManagedGitObservation.observeInWorkspaceC(core, target.workspace)
      tree <- WorkflowManagedProcessExecution.executeGitC(core, GitOperation.IndexTree, target.workspace).flatMap(_.valueC)
      dirty <- observed.dirty.foldLeft(Consequence.success(Vector.empty[LocalPublication.Change])) { (prior, change) => for {
        saved <- prior
        output = policy.catalogOutputs.find(o => o.targetIdentity == target.identity && o.path == change.path)
        projected <- output.filter(_ => change.index == ' ' && change.worktree == 'M' && change.renamedFrom.isEmpty)
          .fold(Consequence.success(false))(o => _catalog_output_c(core, target, o, tree.value))
      } yield if (projected) saved else saved :+ LocalPublication.Change(change.path, change.index.toString,
        change.worktree.toString, change.renamedFrom) }
      after <- RepositorySyncManagedGitObservation.observeInWorkspaceC(core, target.workspace)
      _ <- if (observed == after) Consequence.unit else Consequence.stateConflict("Publication source changed during observation")
    } yield LocalPublication.Source(observed.head, tree.value, dirty)
    def executeC(target: LocalPublication.Target): Consequence[SbtExecutionAttempt] =
      WorkflowManagedProcessExecution.publishLocalAttemptC(core, target.workspace)
  }

  def ensureC(core: ActionCall.Core, policy: Policy, input: LocalPublication.Input): Consequence[LocalPublication.Result] = for {
    _ <- validateC(policy)
    target <- policy.targets.find(_.identity == input.targetIdentity).map(Consequence.success)
      .getOrElse(Consequence.configurationInvalid("Publication target is not declared by this host"))
    participant = SecuritySubject.from(core.executionContext.security).subjectId
    result <- LocalPublication.ensureC(input, target, participant, _port(core, policy))
  } yield result

  def ensureDeliveryC(core: ActionCall.Core, policy: Policy, input: LocalPublication.Input): Consequence[LocalPublication.Result] = for {
    _ <- validateC(policy)
    target <- policy.targets.find(_.identity == input.targetIdentity).map(Consequence.success)
      .getOrElse(Consequence.configurationInvalid("Publication target is not declared by this host"))
    participant = SecuritySubject.from(core.executionContext.security).subjectId
    result <- LocalPublication.ensureDeliveryC(input, target, participant, _port(core, policy))
  } yield result

  def collectC(core: ActionCall.Core, policy: Policy, input: LocalPublication.Input): Consequence[Option[LocalPublication.Result]] = for {
    _ <- validateC(policy)
    target <- policy.targets.find(_.identity == input.targetIdentity).map(Consequence.success)
      .getOrElse(Consequence.configurationInvalid("Publication target is not declared by this host"))
    participant = SecuritySubject.from(core.executionContext.security).subjectId
    result <- LocalPublication.collectC(input, target, participant, _port(core, policy))
  } yield result

  private def _prerequisite_c(core: ActionCall.Core, policy: Policy,
    input: LocalPublication.Input): Consequence[LocalPublication.Result] = for {
    _ <- validateC(policy)
    target <- policy.targets.find(_.identity == input.targetIdentity).map(Consequence.success)
      .getOrElse(Consequence.configurationInvalid("Publication target is not declared by this host"))
    participant = SecuritySubject.from(core.executionContext.security).subjectId
    result <- LocalPublication.ensurePrerequisiteC(input, target, participant, _port(core, policy))
  } yield result

  def beforeChecksC(session: GoalPhaseNativeRun.Session, d: GoalPhaseNativeRun.Declaration,
    core: ActionCall.Core, candidate: GoalPhaseNativeCandidate.Saved): Consequence[Option[V.GoalPhaseAdvanceResult]] =
    d.localPublication match {
      case None => Consequence.success(None)
      case Some(policy) =>
        val dependency = candidate.payload.dependencyAdmission.map(_.binding.identity)
          .orElse(candidate.payload.dependencySourceCapture.map(_.direction.target.binding.identity))
        val targets = policy.targets.filter(t => t.beforeChecks && t.dependency.forall(dependency.contains))
        targets.foldLeft(Consequence.success(Option.empty[V.GoalPhaseAdvanceResult])) { (prior, target) => prior.flatMap {
          case some @ Some(_) => Consequence.success(some)
          case None =>
            val input = LocalPublication.Input(session.current.identity.value + ":publication:" +
              candidate.payload.sourceRevision + ":" + target.identity, target.identity,
              Some(session.current.identity.value))
            _prerequisite_c(core, policy, input).map { result =>
              Option.unless(result.ready)(NativeWorkflowOperationOutput.goalWait(session.current.identity.value,
                "Runtime-owned local publication: " + result.reason, "local-publication").copy(
                outcome = Some(result.status), localPublication = Some(GoalPhaseNativeMetadata.record(result.asJson))))
            }
        }}
    }
}
