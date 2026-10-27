package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import org.goldenport.Consequence
import org.goldenport.configuration.ConfigurationValue
import org.goldenport.cncf.config.{ComponentInitializationParameters, ComponentParameterDecoder, ComponentParameterKey}
import org.goldenport.cncf.processexecution.{GitObjectId, GitOperation}
import RepositorySyncNativeProgressStore as N

/** Host-declared document semantics, with consumed inputs taking precedence.
  * Actual native parent/index deltas decide applicability. Neither docs/ nor a
  * changed commit is a no-program-change certificate or a fresh PASS receipt.
  * @since   Oct. 28, 2026
  * @version Oct. 27, 2026
  */
private[smworkflow] object RepositorySyncDocumentInputs {
  final case class Policy(documentPaths: Vector[String], consumedPaths: Vector[String], reason: String)
  final case class Assessment(changedPaths: Vector[String], consumedPaths: Vector[String], unknownPaths: Vector[String],
    binaryPaths: Vector[String], reason: String) {
    def documentOnly: Boolean = consumedPaths.isEmpty && unknownPaths.isEmpty
    def applicableTo(other: Assessment): Boolean = other != null && changedPaths == other.changedPaths &&
      consumedPaths == other.consumedPaths && unknownPaths == other.unknownPaths && binaryPaths == other.binaryPaths
  }
  def applicable(left: Option[Assessment], right: Option[Assessment]): Boolean = (left, right) match {
    case (None, None) => true
    case (Some(a), Some(b)) => a.applicableTo(b)
    case _ => false
  }
  given Encoder[Policy] = deriveEncoder
  given Decoder[Policy] = deriveDecoder
  given Encoder[Assessment] = deriveEncoder
  given Decoder[Assessment] = deriveDecoder

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
        p.reason != null && p.reason.trim.nonEmpty && p.reason.length <= 4096) Consequence.unit
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

  def captureC(store: N.Store, source: N.Stage): Consequence[Assessment] = for {
    _ <- RepositorySyncNativeMergeValidation.preconditionsC(store, source)
    local <- GitObjectId.parseC(source.payload.before.head)
    remote <- source.payload.mergeHead.map(GitObjectId.parseC).getOrElse(invalid("requires an actual pending merge"))
    tree <- source.payload.indexTree.map(GitObjectId.parseC).getOrElse(invalid("requires the actual pending index tree"))
    localDelta <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.TreeChanges(local, tree)).flatMap(_.valueC)
    remoteDelta <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.TreeChanges(remote, tree)).flatMap(_.valueC)
    changes = (localDelta ++ remoteDelta).distinct
    changed = changes.map(_.path.value).distinct.sorted
    policy = store.declaration.documentInputs
    consumed = changed.filter(path => policy.exists(p => contains(p.consumedPaths, path)))
    unknown = changed.filter(path => !policy.exists(p => contains(p.documentPaths, path))) ++
      changes.filter(c => !Set("000000", "100644", "100755")(c.oldMode) ||
        !Set("000000", "100644", "100755")(c.newMode)).map(_.path.value)
    // Blob observations establish ordinary text independently of a diff-size
    // threshold. An unavailable/truncated blob remains unknown semantic input.
    blobs <- changes.flatMap(c => c.oldBlob.toVector ++ c.newBlob.toVector).distinct
      .foldLeft(Consequence.success(Map.empty[String, Option[Boolean]])) { (prior, blob) => for {
        values <- prior
        observed <- WorkflowManagedProcessExecution.executeGitC(store.core, GitOperation.BlobContent(blob))
        text = observed.valueC.toOption.map(_.text.nonEmpty)
      } yield values.updated(blob.value, text) }
    binary = changes.filter(c => (c.oldBlob.toVector ++ c.newBlob.toVector)
      .exists(blob => blobs.get(blob.value).contains(Some(false)))).map(_.path.value).distinct.sorted
    unavailable = changes.filter(c => (c.oldBlob.toVector ++ c.newBlob.toVector)
      .exists(blob => blobs.get(blob.value).flatten.isEmpty)).map(_.path.value)
    _ <- RepositorySyncNativeMergeValidation.preconditionsC(store, source)
    reason = policy.fold("Document/consumed-input mapping is absent; no input equivalence is claimed")(_.reason)
  } yield Assessment(changed, consumed, (unknown ++ unavailable).distinct.sorted, binary, reason)

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
