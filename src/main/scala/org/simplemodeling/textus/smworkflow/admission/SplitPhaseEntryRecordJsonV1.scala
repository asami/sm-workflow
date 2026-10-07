package org.simplemodeling.textus.smworkflow.admission

import io.circe.{JsonObject, parser}
import org.goldenport.Consequence
import SplitPhaseEntryAssessment.{SourceSnapshot, ProposalCandidate}

/**
 * Read-only projection codec. Stored facts are not initial human authority.
 * @since   Oct.  2, 2026
 * @version Oct.  8, 2026
 */
private[smworkflow] object SplitPhaseEntryRecordJsonV1 {
  val sourceSchema = "sm-workflow.split-phase-entry-source.v1"
  val proposalSchema = "sm-workflow.split-phase-entry-proposal.v1"
  val sourceField = "splitPhaseSourcePayload"
  val proposalField = "splitPhaseProposalPayload"
  val sourcePrefix = sourceSchema + ":"
  val proposalPrefix = proposalSchema + ":"
  private val limit = 64 * 1024

  def decodeSourceStorageC(raw: String): Consequence[SourceSnapshot] = result(for {
    root <- fields(raw, sourcePrefix, Set("schemaVersion", "identity", "sourcePhaseIdentity", "authorityReference", "revision", "digest"))
    _ <- schema(root, sourceSchema)
    identity <- string(root, "identity")
    phase <- string(root, "sourcePhaseIdentity")
    authority <- string(root, "authorityReference")
    revision <- string(root, "revision")
    digest <- digestField(root, "digest")
  } yield SourceSnapshot(identity, phase, authority, revision, digest))

  def decodeProposalStorageC(raw: String): Consequence[ProposalCandidate] = result(for {
    root <- fields(raw, proposalPrefix, Set("schemaVersion", "identity", "sourcePhaseIdentity", "authorityReference", "sourceSnapshotDigest", "digest"))
    _ <- schema(root, proposalSchema)
    identity <- string(root, "identity")
    phase <- string(root, "sourcePhaseIdentity")
    authority <- string(root, "authorityReference")
    sourceDigest <- digestField(root, "sourceSnapshotDigest")
    digest <- digestField(root, "digest")
  } yield ProposalCandidate(identity, phase, authority, sourceDigest, digest))

  private def result[A](value: Either[String, A]): Consequence[A] =
    value.fold(Consequence.stateConflict(_), Consequence.success)
  private def fields(raw: String, prefix: String, expected: Set[String]): Either[String, JsonObject] =
    if (raw == null || raw.length > limit || !raw.startsWith(prefix)) Left("SplitPhase entry storage payload is unsupported")
    else parser.parse(raw.substring(prefix.length)).left.map(_ => "SplitPhase entry JSON is invalid")
      .flatMap(_.asObject.filter(_.keys.toSet == expected).toRight("SplitPhase entry record fields differ from schema"))
  private def schema(root: JsonObject, expected: String): Either[String, Unit] =
    string(root, "schemaVersion").flatMap(value => Either.cond(value == expected, (), "SplitPhase entry schema is unsupported"))
  private def string(root: JsonObject, key: String): Either[String, String] =
    root(key).flatMap(_.asString).filter(value => value.nonEmpty && value.trim == value)
      .toRight(s"SplitPhase entry field is invalid: $key")
  private def digestField(root: JsonObject, key: String): Either[String, String] =
    string(root, key).filterOrElse(_.matches("[0-9a-f]{64}"), "SplitPhase entry evidence digest is invalid")
}
