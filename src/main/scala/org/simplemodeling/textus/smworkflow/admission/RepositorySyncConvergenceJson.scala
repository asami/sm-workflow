package org.simplemodeling.textus.smworkflow.admission

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.WorkflowResultJsonV1
import org.simplemodeling.textus.smworkflow.value.RepositorySyncConvergenceResult

/**
 * @since   Sep. 28, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object RepositorySyncConvergenceJson {
  val schema = "sm-workflow.repository-sync-convergence-result.v1"
  private val names = Vector("outcome", "repositoryIdentity", "localBranch", "trackingRemote",
    "trackingBranch", "remoteEndpointIdentity", "tipOid", "treeOid", "treeDigest", "evidenceDigest")

  val payloadCodec: WorkflowResultJsonV1.PayloadCodec[RepositorySyncConvergenceResult] =
    new WorkflowResultJsonV1.PayloadCodec[RepositorySyncConvergenceResult] {
      val typeIdentity = "RepositorySyncConvergenceResult"
      def encode(value: RepositorySyncConvergenceResult): Json =
        if (!_valid(value)) Json.Null
        else Json.obj((Vector("schemaVersion" -> Json.fromString(schema)) ++ names.zip(_values(value))
          .map((name, item) => name -> Json.fromString(item)))*)
      def decode(json: Json): Either[String, RepositorySyncConvergenceResult] = for {
        fields <- Option(json).flatMap(_.asObject).toRight("convergence result must be an object")
        _ <- Either.cond(fields.keys.toSet == names.toSet + "schemaVersion" &&
          fields("schemaVersion").flatMap(_.asString).contains(schema), (), "incompatible convergence result schema")
        values <- names.foldLeft[Either[String, Vector[String]]](Right(Vector.empty)) { (prior, name) =>
          for { result <- prior; value <- fields(name).flatMap(_.asString).filter(_.trim.nonEmpty)
            .toRight(s"invalid convergence result $name") } yield result :+ value
        }
        result = RepositorySyncConvergenceResult(values(0), values(1), values(2), values(3), values(4),
          values(5), values(6), values(7), values(8), values(9))
        _ <- Either.cond(_valid(result), (), "invalid convergence result evidence")
      } yield result
    }

  def validateC(value: RepositorySyncConvergenceResult): Consequence[RepositorySyncConvergenceResult] =
    if (_valid(value)) Consequence.success(value)
    else Consequence.stateConflict("RepositorySync convergence result is invalid")

  private def _values(value: RepositorySyncConvergenceResult): Vector[String] = Vector(value.outcome,
    value.repositoryIdentity, value.localBranch, value.trackingRemote, value.trackingBranch,
    value.remoteEndpointIdentity, value.tipOid, value.treeOid, value.treeDigest, value.evidenceDigest)
  private def _valid(value: RepositorySyncConvergenceResult): Boolean = value != null &&
    _values(value).forall(item => item != null && item.trim.nonEmpty) &&
    Set("ALREADY_SYNCHRONIZED", "SYNCHRONIZED").contains(value.outcome) &&
    value.tipOid.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})") &&
    value.treeOid.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})") && value.tipOid.length == value.treeOid.length &&
    value.treeDigest.matches("[0-9a-f]{64}") && value.evidenceDigest.matches("[0-9a-f]{64}")
}
