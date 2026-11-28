package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation, GitTreeChange}
import RepositorySyncNativeProgressStore as N

/** Host-declared document semantics, with consumed inputs taking precedence.
  * Actual native parent/index deltas decide applicability. Neither docs/ nor a
  * changed commit is a no-program-change certificate or a fresh PASS receipt.
  * @since   Oct. 28, 2026
  * @version Nov. 21, 2026
  */
private[smworkflow] object RepositorySyncDocumentInputs {
  final case class Policy(documentPaths: Vector[String], consumedPaths: Vector[String], reason: String)
  final case class Assessment(changedPaths: Vector[String], consumedPaths: Vector[String], unknownPaths: Vector[String],
    binaryPaths: Vector[String], reason: String, gitlinkPaths: Vector[String] = Vector.empty) {
    def documentOnly: Boolean = consumedPaths.isEmpty && unknownPaths.isEmpty
    def submoduleChanges: Boolean = gitlinkPaths.nonEmpty || changedPaths.contains(_gitmodules)
    def applicableTo(other: Assessment): Boolean = other != null && changedPaths == other.changedPaths &&
      consumedPaths == other.consumedPaths && unknownPaths == other.unknownPaths && binaryPaths == other.binaryPaths &&
      gitlinkPaths == other.gitlinkPaths
  }
  def applicable(left: Option[Assessment], right: Option[Assessment]): Boolean = (left, right) match {
    case (None, None) => true
    case (Some(a), Some(b)) => a.applicableTo(b)
    case _ => false
  }
  given Encoder[Policy] = deriveEncoder
  given Decoder[Policy] = deriveDecoder
  given Encoder[Assessment] = deriveEncoder[Assessment].mapJson(_.mapObject(_.filter {
    case ("gitlinkPaths", values) => !values.asArray.contains(Vector.empty)
    case _ => true
  }))
  private val _assessment_decoder: Decoder[Assessment] = deriveDecoder
  given Decoder[Assessment] = Decoder.instance(cursor => _assessment_decoder.decodeJson(
    cursor.value.mapObject(fields => if (fields.contains("gitlinkPaths")) fields else fields.add("gitlinkPaths", Json.arr()))))

  val declaration: ComponentParameterKey[Policy] = ComponentParameterKey.optional(
    "sm-workflow.repository-sync.document-inputs", ComponentParameterDecoder {
      case value: ConfigurationValue.ObjectValue => decodeC(json(value))
      case ConfigurationValue.StringValue(raw) => io.circe.parser.parse(raw).fold(
        _ => invalid("configuration JSON is invalid"), decodeC)
      case _ => invalid("requires a JSON document/consumption declaration")
    })
  def validateC(p: Policy): Consequence[Unit] =
    if (p != null && p.documentPaths != null && p.documentPaths.nonEmpty && p.documentPaths.size <= 128 &&
        p.consumedPaths != null && p.consumedPaths.size <= 128 &&
        Vector(p.documentPaths, p.consumedPaths).forall(paths => paths.distinct == paths &&
          paths.forall(RepositorySyncNativeConflictInput.relative)) &&
        p.reason != null && p.reason.trim.nonEmpty) Consequence.unit
    else invalid("requires explicit document scopes, consumed-input exclusions and their rationale")
  def decodeC(value: Json): Consequence[Policy] = {
    val parsed = for {
      fields <- value.asObject.toRight("declaration must be an object")
      _ <- Either.cond(fields.keys.toSet == Set("documentPaths", "consumedPaths", "reason"), (), "declaration fields differ")
      policy <- value.as[Policy].left.map(_.message)
    } yield policy
    parsed.fold(invalid, p => validateC(p).map(_ => p))
  }
  def resolveC(parameters: ComponentInitializationParameters): Consequence[Option[Policy]] =
    parameters.resolve(declaration).map(_.value)

  private def contains(paths: Vector[String], path: String): Boolean =
    paths.exists(prefix => path == prefix || path.startsWith(prefix + "/"))

  /** Review proposals may describe only the unresolved inputs. Fill omissions
    * from the original classification before re-observing the actual candidate.
    * Explicit contradictions remain visible to the continuation's validator.
    */
  private[admission] def withRetainedClassification(original: Assessment, proposal: Policy): Policy = {
    val omitted = original.changedPaths.filterNot(path => original.unknownPaths.contains(path) ||
      contains(proposal.documentPaths, path) || contains(proposal.consumedPaths, path))
    val (consumed, documents) = omitted.partition(original.consumedPaths.contains)
    proposal.copy(documentPaths = proposal.documentPaths ++ documents, consumedPaths = proposal.consumedPaths ++ consumed)
  }

  private val _file_modes = Set("000000", "100644", "100755")
  private val _gitlink_mode = "160000"
  private val _gitmodules = ".gitmodules"

  private def _gitlink_change(change: GitTreeChange): Boolean = change.status match {
    case "A" => change.oldMode == "000000" && change.newMode == _gitlink_mode &&
      change.oldBlob.isEmpty && change.newBlob.nonEmpty
    case "M" => change.oldMode == _gitlink_mode && change.newMode == _gitlink_mode &&
      change.oldBlob.nonEmpty && change.newBlob.nonEmpty && change.oldBlob != change.newBlob
    case "D" => change.oldMode == _gitlink_mode && change.newMode == "000000" &&
      change.oldBlob.nonEmpty && change.newBlob.isEmpty
    case _ => false
  }

  private def _blob_ids(change: GitTreeChange): Vector[GitObjectId] =
    change.oldBlob.filter(_ => change.oldMode != _gitlink_mode).toVector ++
      change.newBlob.filter(_ => change.newMode != _gitlink_mode).toVector

  def captureC(store: N.Store, source: N.Stage): Consequence[Assessment] =
    captureC(store, source, store.declaration.documentInputs)

  /** Nonmerge related-workspace candidate: compare actual before/after trees,
    * then add exact changed external envelopes. Git equality alone cannot erase
    * a selected dependency change. Host document scopes do not hide consumption. */
  def captureIntegratedC(store: N.Store, source: N.Stage): Consequence[Assessment] = for {
    _ <- RepositorySyncNativeMergeValidation.preconditionsC(store, source)
    assessment <- retainedIntegrationC(store, source)
    _ <- RepositorySyncNativeMergeValidation.preconditionsC(store, source)
  } yield assessment

  private[admission] def retainedIntegrationC(store: N.Store, source: N.Stage): Consequence[Assessment] = for {
    before <- GitObjectId.parseC(source.payload.before.tree)
    after <- source.payload.indexTree.map(GitObjectId.parseC).getOrElse(invalid("integrated index tree is unavailable"))
    delta <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.TreeChanges(before, after)).flatMap(_.valueC)
    changed = delta.map(_.path.value).distinct.sorted
    policy = store.declaration.documentInputs
    external = RepositorySyncConsumedInputs.changes(store.core)
    consumed = changed.filter(p => policy.exists(v => contains(v.consumedPaths, p))) ++ external
    unknown = changed.filter(p => !policy.exists(v => contains(v.documentPaths, p) || contains(v.consumedPaths, p))) ++
      delta.filter(c => !Set("000000", "100644", "100755")(c.oldMode) || !Set("000000", "100644", "100755")(c.newMode)).map(_.path.value)
    blobs <- delta.flatMap(c => c.oldBlob.toVector ++ c.newBlob.toVector).distinct
      .foldLeft(Consequence.success(Map.empty[String, Option[Boolean]])) { (prior, blob) => for {
        values <- prior
        actual <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.BlobContent(blob))
      } yield values.updated(blob.value, actual.valueC.toOption.map(_.text.nonEmpty)) }
    binary = delta.filter(c => (c.oldBlob.toVector ++ c.newBlob.toVector).exists(b => blobs.get(b.value).contains(Some(false)))).map(_.path.value)
    unavailable = delta.filter(c => (c.oldBlob.toVector ++ c.newBlob.toVector).exists(b => blobs.get(b.value).flatten.isEmpty)).map(_.path.value)
  } yield Assessment((changed ++ external).distinct.sorted, consumed.distinct.sorted, (unknown ++ unavailable).distinct.sorted, binary.distinct.sorted,
    "Actual related integration tree delta and frozen immutable consumer input comparison")

  /** Observe a proposed host mapping without changing any retained assessment. */
  def captureC(store: N.Store, source: N.Stage, policy: Option[Policy]): Consequence[Assessment] = for {
    _ <- RepositorySyncNativeMergeValidation.preconditionsC(store, source)
    local <- GitObjectId.parseC(source.payload.before.head)
    remote <- source.payload.mergeHead.map(GitObjectId.parseC).getOrElse(invalid("requires an actual pending merge"))
    tree <- source.payload.indexTree.map(GitObjectId.parseC).getOrElse(invalid("requires the actual pending index tree"))
    localDelta <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.TreeChanges(local, tree)).flatMap(_.valueC)
    remoteDelta <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.TreeChanges(remote, tree)).flatMap(_.valueC)
    assessment <- assessChangesC((localDelta ++ remoteDelta).distinct, policy, blob =>
      WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.BlobContent(blob))
        .map(_.valueC.toOption.map(_.text.nonEmpty)))
    _ <- RepositorySyncNativeMergeValidation.preconditionsC(store, source)
  } yield assessment

  private[admission] def assessChangesC(changes: Vector[GitTreeChange], policy: Option[Policy],
    readBlob: GitObjectId => Consequence[Option[Boolean]]): Consequence[Assessment] = {
    val changed = changes.map(_.path.value).distinct.sorted
    val gitlinks = changes.groupBy(_.path.value).iterator.collect {
      case (path, deltas) if deltas.forall(_gitlink_change) => path
    }.toSet
    val consumed = changed.filter(path => gitlinks(path) || path == _gitmodules ||
      policy.exists(p => contains(p.consumedPaths, path)))
    val known = consumed.toSet
    val unknown = changed.filterNot(path => known(path) || policy.exists(p => contains(p.documentPaths, path))) ++
      changes.filter(c => !gitlinks(c.path.value) && !(_file_modes(c.oldMode) && _file_modes(c.newMode))).map(_.path.value)
    val files = changes.map(change => change.path.value -> _blob_ids(change))
    val reason = policy.fold("Document/consumed-input mapping is absent; no input equivalence is claimed")(_.reason)
    // Blob observations establish ordinary text independently of a diff-size
    // threshold. An unavailable/truncated blob remains unknown semantic input.
    files.flatMap(_._2).distinct
      .foldLeft(Consequence.success(Map.empty[String, Option[Boolean]])) { (prior, blob) => for {
        values <- prior
        text <- readBlob(blob)
      } yield values.updated(blob.value, text) }.map { blobs =>
      val binary = files.collect { case (path, ids) if ids.exists(blob => blobs.get(blob.value).contains(Some(false))) => path }
      val unavailable = files.collect { case (path, ids) if ids.exists(blob => blobs.get(blob.value).flatten.isEmpty) => path }
      Assessment(changed, consumed, (unknown ++ unavailable).distinct.sorted, binary.distinct.sorted, reason, gitlinks.toVector.sorted)
    }
  }

  private def json(value: ConfigurationValue): Json = value match {
    case ConfigurationValue.StringValue(v) => Json.fromString(v)
    case ConfigurationValue.NumberValue(v) => Json.fromBigDecimal(v)
    case ConfigurationValue.BooleanValue(v) => Json.fromBoolean(v)
    case ConfigurationValue.ListValue(v) => Json.fromValues(v.map(json))
    case ConfigurationValue.ObjectValue(v) => Json.obj(v.toVector.map((key, v) => key -> json(v))*)
    case ConfigurationValue.NullValue => Json.Null
  }

  private def invalid[A](reason: String): Consequence[A] = Consequence.configurationInvalid("RepositorySync document inputs " + reason)
}
