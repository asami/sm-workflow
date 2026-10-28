package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto.*
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.processexecution.*
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.workspace.{FileTarget, WorkspaceId, WorkspaceFilePath}
import org.goldenport.cncf.workflow.*
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.value as V
import RepositorySyncNativeProgressStore as N
import WorkflowManagedProcessExecution as E

/** Purpose proposals consume genuine work records and native content facts.
  * Neither grouping nor an old record establishes candidate acceptance.
  * @since   Oct. 28, 2026
  * @version Oct. 28, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object RepositorySyncPurpose {
  final case class Source(kind: String, identity: String, revision: String)
  final case class SourceRecord(source: Source, status: String, payload: Json)
  final case class Slot(workspace: String, checks: Vector[RepositorySyncNativeMergeValidation.Check],
    actionPolicy: GoalPhaseStepAssessment.Policy, checkWorkspaces: Map[String, String] = Map.empty)
  given Decoder[Slot] = Decoder.instance { cursor =>
    val normalized = cursor.value.mapObject(fields =>
      if (fields("checkWorkspaces").isEmpty) fields.add("checkWorkspaces", Json.obj()) else fields)
    io.circe.generic.semiauto.deriveDecoder[Slot].decodeJson(normalized)
  }
  final case class Policy(slots: Vector[Slot], sources: Vector[Source])
  final case class File(path: String, indexCode: String, worktreeCode: String,
    original: Option[String], indexed: Option[String], worktree: Option[String], unexamined: Option[String])
  final case class Request(repository: String, root: String, branch: String, parent: String, indexTree: String,
    files: Vector[File], records: Vector[SourceRecord])
  final case class Content(path: String, text: Option[String])
  final case class Group(identity: String, purpose: String, message: String, dependencies: Vector[String],
    content: Vector[Content], inseparable: Vector[String], rationale: String)
  final case class Result(decision: String, rationale: String, groups: Vector[Group], excludedPaths: Vector[String])
  final case class Candidate(parent: String, index: String, commit: String, tree: String, projectedIndex: String,
    changes: Vector[Content], expected: Vector[Content], message: String, baselineTree: Option[String] = None,
    worktreeBefore: Option[String] = None, worktreeAfter: Option[String] = None)
  final case class Progress(request: Request, plan: Option[Result] = None, cursor: Int = 0, attempt: Int = 0, slotCursor: Int = 0, baselineTree: Option[String] = None,
    candidate: Option[Candidate] = None, receipts: Vector[RepositorySyncNativeMergeValidation.Receipt] = Vector.empty,
    assessment: Option[GoalPhaseStepAssessment.Result] = None, commits: Vector[String] = Vector.empty,
    selection: Option[RepositorySyncProgramImpact.Selection] = None, review: Option[org.goldenport.cncf.workflow.ContextReference] = None, reusable: Vector[RepositorySyncNativeMergeValidation.Receipt] = Vector.empty, effects: Vector[Json] = Vector.empty)

  private final class Codec[A: Encoder: Decoder](val typeIdentity: String) extends WorkflowResultJsonV1.PayloadCodec[A] {
    def encode(value: A): Json = Json.obj("schemaVersion" -> Json.fromString("sm-workflow.repository-purpose.v1"), "payload" -> value.asJson)
    def decode(value: Json): Either[String, A] = for {
      version <- value.hcursor.get[String]("schemaVersion").left.map(_.message)
      _ <- Either.cond(version == "sm-workflow.repository-purpose.v1", (), "Purpose payload schema differs")
      result <- value.hcursor.get[A]("payload").left.map(_.message)
    } yield result
  }
  val inputCodec: WorkflowResultJsonV1.PayloadCodec[Request] = new Codec("RepositorySyncPurposeRequest")
  val resultCodec: WorkflowResultJsonV1.PayloadCodec[Result] = new Codec("RepositorySyncPurposeResult")
  given Encoder[Progress] = io.circe.generic.semiauto.deriveEncoder
  given Decoder[Progress] = io.circe.generic.semiauto.deriveDecoder

  val declaration: ComponentParameterKey[Policy] = ComponentParameterKey.optional("sm-workflow.repository-sync.dirty-purposes",
    ComponentParameterDecoder {
      case ConfigurationValue.StringValue(raw) => io.circe.parser.parse(raw).fold(
        _ => Consequence.configurationInvalid("Dirty purpose configuration is not JSON"), _decode_policy_c)
      case value: ConfigurationValue.ObjectValue => _decode_policy_c(_configuration_json(value))
      case _ => Consequence.configurationInvalid("Dirty purpose configuration requires an object")
    })
  def resolveC(parameters: ComponentInitializationParameters): Consequence[Option[Policy]] = parameters.resolve(declaration).map(_.value)
  private def _decode_policy_c(json: Json): Consequence[Policy] = json.as[Policy].fold(
    _ => Consequence.configurationInvalid("Dirty purpose policy is incomplete"), value => validatePolicyC(value).map(_ => value))
  def validatePolicyC(value: Policy): Consequence[Unit] = {
    val valid = value != null && value.slots != null && value.slots.size <= 16 && value.sources != null && value.sources.size <= 32 &&
      value.sources.distinct == value.sources && value.sources.forall(source => source != null &&
        Set("Task", "Step", "Finding", "Journal")(source.kind) && _text(source.identity) && _text(source.revision)) &&
      value.slots.forall(slot => slot != null && org.goldenport.cncf.workspace.WorkspaceId.parseC(slot.workspace).isSuccess &&
        slot.checks != null && slot.checks.size <= 16 && slot.checks.map(_.identity).distinct.size == slot.checks.size &&
        slot.actionPolicy != null && GoalPhaseStepAssessment.validatePurposePolicyC(slot.actionPolicy).isSuccess &&
        slot.checkWorkspaces != null && slot.checkWorkspaces.keys.forall(id => slot.checks.exists(_.identity == id)) &&
        slot.checkWorkspaces.values.forall(value => WorkspaceId.parseC(value).isSuccess)) && value.slots.map(_.workspace).distinct.size == value.slots.size
    if (valid) Consequence.unit else Consequence.configurationInvalid("Dirty purpose policy has invalid sources or fixed candidate slots")
  }

  def captureC(store: N.Store, observed: RepositorySyncManagedGitObservation.Observed): Consequence[Request] = for {
    _ <- store.scopeC(observed)
    _ <- if (observed.dirty.nonEmpty && observed.dirty.size <= 256 && observed.dirty.forall(_.renamedFrom.isEmpty)) Consequence.unit
      else N.invalid("dirty purpose inventory requires finite literal non-rename paths; existing work is preserved")
    index <- RepositorySyncManagedGitObservation.indexTreeC(store.core)
    parent <- GitObjectId.parseC(observed.head)
    tree <- GitObjectId.parseC(index)
    id <- GitSelectionId.parseC("purpose-inventory")
    paths <- observed.dirty.foldLeft(Consequence.success(Vector.empty[GitPath]))((prior, entry) => for {
      current <- prior; path <- GitPath.parseC(entry.path)
    } yield current :+ path)
    selection = GitSelectionParameters(id, paths)
    originals <- E.executePurposeC(store.core, GitOperation.TreeEntries(parent, id), selection).flatMap(_.valueC)
    indexed <- E.executePurposeC(store.core, GitOperation.TreeEntries(tree, id), selection).flatMap(_.valueC)
    files <- observed.dirty.zipWithIndex.foldLeft(Consequence.success(Vector.empty[File])) { case (prior, (entry, ordinal)) => for {
      values <- prior
      original <- _blob_c(store, originals(ordinal).entry)
      staged <- _blob_c(store, indexed(ordinal).entry)
      worktree <- _worktree_c(store, entry.path)
    } yield values :+ File(entry.path, entry.index.toString, entry.worktree.toString,
      original._1, staged._1, worktree._1, original._2.orElse(staged._2).orElse(worktree._2)) }
    records <- _records_c(store)
  } yield Request(store.context.repositoryIdentity, store.original.repositoryRoot, observed.branch, observed.head, index, files, records)

  private def _worktree_c(store: N.Store, name: String): Consequence[(Option[String], Option[String])] = for {
    workspace <- WorkspaceId.parseC("sm-workflow-repository")
    path <- WorkspaceFilePath.parseC(name)
    actual <- WorkflowFileExecution.readTextC(store.core, FileTarget(workspace), path)
  } yield actual.text -> (if (actual.text.exists(text => text.contains('\u0000') || text.getBytes(UTF_8).length > 131072))
    Some("binary or oversized worktree text remains unexamined") else None)

  private def _blob_c(store: N.Store, value: Option[GitRegularTreeEntry]): Consequence[(Option[String], Option[String])] = value match {
    case None => Consequence.success(None -> None)
    case Some(entry) => E.executeGitC(store.core, GitOperation.BlobContent(entry.blob)).flatMap(_.valueC).map { blob =>
      blob.text -> (if (blob.text.isEmpty) Some("binary or unavailable native blob remains unexamined") else None) }
  }

  def admitC(request: Request, result: Result): Consequence[Unit] = {
    val files = request.files.map(_.path).toSet
    val valid = result != null && _text(result.rationale) && result.groups != null && result.excludedPaths != null &&
      result.excludedPaths.distinct == result.excludedPaths && result.excludedPaths.forall(files) &&
      ((result.decision == "decision-needed" && result.groups.isEmpty) || (result.decision == "resolved" &&
        result.groups.nonEmpty && result.groups.size <= 16 && result.groups.map(_.identity).distinct.size == result.groups.size &&
        result.groups.forall(group => group != null && group.identity.matches("[a-z][a-z0-9-]{0,63}") &&
          _text(group.purpose) && _text(group.rationale) && _text(group.message) && group.message == group.message.trim &&
          group.message.getBytes(UTF_8).length <= 4096 && group.dependencies != null && group.dependencies.distinct == group.dependencies &&
          group.content != null && group.content.nonEmpty && group.content.size <= 256 &&
          group.content.map(_.path).distinct == group.content.map(_.path) && group.content.forall(content => content != null &&
            files(content.path) && content.text != null &&
            content.text.forall(text => text != null && !text.contains('\u0000') && UTF_8.newEncoder().canEncode(text) && text.getBytes(UTF_8).length <= 131072) &&
            request.files.find(_.path == content.path).exists(_.unexamined.isEmpty)) &&
          group.inseparable != null && group.inseparable.distinct == group.inseparable &&
          (group.inseparable.isEmpty || (group.inseparable.size >= 2 && group.rationale.trim.nonEmpty))) &&
        result.groups.zipWithIndex.forall { case (group, index) => group.dependencies.forall(id =>
          result.groups.take(index).exists(_.identity == id)) } &&
        (result.groups.flatMap(_.content.map(_.path)).toSet ++ result.excludedPaths.toSet) == files))
    if (valid) Consequence.unit else N.invalid("purpose grouping must cover actual inventory or explicit exclusions with ordered dependencies and supported content; no authority is supplied")
  }

  def fromPublicC(value: V.RepositorySyncPurposeResult): Consequence[Result] = for {
    groups <- value.groups.foldLeft(Consequence.success(Vector.empty[Group])) { (prior, record) => for {
      values <- prior
      text <- record.toJsonStringC
      json <- io.circe.parser.parse(text).fold(error => N.invalid(error.message), Consequence.success)
      normalized <- GoalPhasePlanRevision.normalizeJsonC(json)
      group <- normalized.as[Group].fold(error => N.invalid(error.message), Consequence.success)
    } yield values :+ group }
  } yield Result(value.decision, value.rationale, groups, value.excludedPaths)

  def producerC(candidate: Candidate): Consequence[GitPurposeCandidate] = for {
    parent <- GitObjectId.parseC(candidate.parent); index <- GitObjectId.parseC(candidate.index)
    commit <- GitObjectId.parseC(candidate.commit); tree <- GitObjectId.parseC(candidate.tree)
    projected <- GitObjectId.parseC(candidate.projectedIndex)
    changes <- candidate.changes.foldLeft(Consequence.success(Vector.empty[GitPurposeChange]))((prior, content) => for {
      values <- prior; path <- GitPath.parseC(content.path)
      expected <- candidate.expected.find(_.path == content.path).map(Consequence.success).getOrElse(N.invalid("purpose candidate lacks its actual expected content"))
    } yield values :+ GitPurposeChange(path, expected.text, content.text))
    baseline <- candidate.baselineTree.fold(Consequence.success(Option.empty[GitObjectId]))(value => GitObjectId.parseC(value).map(Some(_)))
    before <- candidate.worktreeBefore.fold(Consequence.success(Option.empty[GitObjectId]))(value => GitObjectId.parseC(value).map(Some(_)))
    after <- candidate.worktreeAfter.fold(Consequence.success(Option.empty[GitObjectId]))(value => GitObjectId.parseC(value).map(Some(_)))
  } yield GitPurposeCandidate(GitPurposeRequest(parent, index, changes, baseline), commit, tree, projected, candidate.message, before, after)

  private def _records_c(store: N.Store): Consequence[Vector[SourceRecord]] =
    store.declaration.purposePolicy.toVector.flatMap(_.sources).foldLeft(Consequence.success(Vector.empty[SourceRecord])) { (prior, source) => for {
      values <- prior
      definition <- RepositorySyncDefinitionBinding.bindC
      storage <- RepositorySyncManagedFetch.bindC(store.declaration, definition, store.core)
      loaded <- source.kind match {
        case "Step" => for {
          starts <- storage.bindMechanicalC(GoalPhaseNativeRun.startOperation, GoalPhaseNativeRun.codec)
          actual <- starts.loadRetainedC(WorkflowMechanicalTransition.Reference(source.identity, source.revision))(using store.core.executionContext)
          record <- actual.map(Consequence.success).getOrElse(N.invalid("purpose Step source is absent"))
          payload = record.receipt.payload.value
          _ <- if (payload.root == store.original.repositoryRoot && payload.branch == store.context.localBranch &&
            payload.participantIdentity == store.context.participantIdentity) Consequence.unit else N.invalid("purpose Step source crosses repository/branch/principal")
        } yield SourceRecord(source, "observed", GoalPhaseNativeRun.codec.encode(payload))
        case "Task" => for {
          tasks <- storage.bindMechanicalC(GoalTaskNativeRun.advanceOperation, GoalTaskNativeRun.stateCodec)
          actual <- tasks.loadRetainedC(WorkflowMechanicalTransition.Reference(source.identity, source.revision))(using store.core.executionContext)
          record <- actual.map(Consequence.success).getOrElse(N.invalid("purpose Task source is absent"))
          payload = record.receipt.payload.value
          _ <- if (payload.root == store.original.repositoryRoot && payload.branch == store.context.localBranch &&
            payload.state.participant == store.context.participantIdentity) Consequence.unit else N.invalid("purpose Task source crosses repository/branch/principal")
        } yield SourceRecord(source, "observed", GoalTaskNativeRun.stateCodec.encode(payload))
        case _ => for {
          value <- DevelopmentJournal.readC(store.declaration.journalPolicy, store.core, V.JournalReadInput(source.identity, Some(source.revision)))
          _ <- if (value.entry.forall(entry => entry.repositoryIdentity == store.context.repositoryIdentity)) Consequence.unit
            else N.invalid("purpose Journal/Finding source crosses the actual repository")
          _ <- if (source.kind != "Finding" || value.entry.forall(_.finding.nonEmpty)) Consequence.unit
            else N.invalid("configured Finding source is not an actual journal Finding")
          raw <- value.toRecord().toJsonStringC
          payload <- io.circe.parser.parse(raw).fold(error => N.invalid(error.message), Consequence.success)
        } yield SourceRecord(source, value.status, payload)
      }
    } yield values :+ loaded }

  private def _text(value: String): Boolean = value != null && value.trim.nonEmpty && !value.contains('\u0000') && value.getBytes(UTF_8).length <= 16384
  private def _configuration_json(value: ConfigurationValue): Json = value match {
    case ConfigurationValue.StringValue(text) => Json.fromString(text)
    case ConfigurationValue.ObjectValue(fields) => Json.obj(fields.toVector.map((key, value) => key -> _configuration_json(value))*)
    case ConfigurationValue.ListValue(values) => Json.fromValues(values.map(_configuration_json))
    case ConfigurationValue.NumberValue(value) => Json.fromBigDecimal(value)
    case ConfigurationValue.BooleanValue(value) => Json.fromBoolean(value)
    case _ => Json.Null
  }
}
