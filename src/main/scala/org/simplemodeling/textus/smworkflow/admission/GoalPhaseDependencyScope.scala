package org.simplemodeling.textus.smworkflow.admission

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets.UTF_8
import org.goldenport.Consequence
import org.goldenport.cncf.workflow.ContextReference
import org.goldenport.cncf.workspace.{WorkspaceFilePath, WorkspaceId}
import org.goldenport.record.Record

/** Finite dependency-change input, never consent or an effect authority.
  * @since   Oct. 19, 2026
  * @version Oct. 19, 2026
  * @author  ASAMI, Tomoharu
  */
private[smworkflow] object GoalPhaseDependencyScope {
  final case class Component(identity: String, repository: String, workspace: String)
  object Component {
    given Encoder[Component] = deriveEncoder
    given Decoder[Component] = _decoder(deriveDecoder[Component],
      Set("identity", "repository", "workspace"), _component_c)
  }

  // ContextReference remains the CNCF value; these codecs own only this input's shape.
  private given Encoder[ContextReference] = deriveEncoder
  private given Decoder[ContextReference] = _decoder(deriveDecoder[ContextReference],
    Set("identity", "revision"), value => _check(_reference(value), "context reference is invalid"))

  final case class Proposal(
    identity: String,
    revision: String,
    discovery: String,
    requester: Component,
    dependency: Component,
    usageContract: ContextReference,
    reason: String,
    paths: Vector[String],
    acceptance: Vector[String],
    dependentWork: Vector[ContextReference],
    discoveredEdits: Vector[String]
  )
  object Proposal {
    given Encoder[Proposal] = deriveEncoder
    given Decoder[Proposal] = _decoder(deriveDecoder[Proposal], Set(
      "identity", "revision", "discovery", "requester", "dependency", "usageContract",
      "reason", "paths", "acceptance", "dependentWork", "discoveredEdits"), validateProposalC)
  }

  def validateProposalC(value: Proposal): Consequence[Unit] = for {
    _ <- _check(value != null, "proposal is null")
    _ <- _check(_identifier(value.identity) && _identifier(value.revision),
      "proposal identity or revision is invalid")
    _ <- _check(Set("PLANNING", "IMPLEMENTATION", "REPAIR", "REVIEW").contains(value.discovery),
      "discovery must be PLANNING, IMPLEMENTATION, REPAIR or REVIEW")
    _ <- _component_c(value.requester)
    _ <- _component_c(value.dependency)
    _ <- _check(value.requester.identity != value.dependency.identity,
      "requester and dependency must name distinct Components")
    _ <- _check(_reference(value.usageContract), "Usage Contract reference is invalid")
    _ <- _check(_description(value.reason), "reason is invalid")
    _ <- _check(_unique(value.paths, 1, 128)(_path), "paths must be unique literal workspace-relative files")
    _ <- _check(_unique(value.acceptance, 1, 32)(_description), "acceptance must contain unique bounded descriptions")
    _ <- _check(_unique(value.dependentWork, 1, 32)(_reference), "dependent work must contain unique context references")
    _ <- _check(_unique(value.discoveredEdits, 0, 128)(_path), "discovered edits must be unique literal workspace-relative files")
    _ <- _check(value.discoveredEdits.forall(value.paths.contains), "discovered edits must be a subset of requested paths")
    _ <- _check(value.discoveredEdits.isEmpty || value.discovery == "REVIEW",
      "only REVIEW may report discovered edits")
  } yield ()

  def validateOptionC(value: Option[Proposal]): Consequence[Unit] =
    if (value == null) _check(false, "proposal Option is null")
    else value.fold(Consequence.unit)(validateProposalC)

  /** Absence adds no restriction to the existing semantic result owner. */
  def admitResultC(kind: String, outcome: String, value: Option[Proposal]): Consequence[Unit] = for {
    _ <- validateOptionC(value)
    _ <- value.fold(Consequence.unit) { proposal =>
      val matching = kind match {
        case "ImplementStep" => Set("PLANNING", "IMPLEMENTATION").contains(proposal.discovery)
        case "RepairStep" => proposal.discovery == "REPAIR"
        case "ReviewStep" | "ReviewPhase" => proposal.discovery == "REVIEW"
        case _ => false
      }
      _check(outcome == "decision-needed" && matching,
        "a proposal requires decision-needed and discovery matching its semantic kind")
    }
  } yield ()

  /** The caller has validated the input. Equality neither resolves nor authorizes a target. */
  def sameRepository(value: Proposal): Boolean =
    value.requester.repository == value.dependency.repository

  def fromPublicProposalC(value: Option[Record]): Consequence[Option[Proposal]] =
    if (value == null || value.exists(_ == null))
      _invalid("public proposal Option is null")
    else value.fold(Consequence.success(Option.empty[Proposal])) { record => for {
      text <- _public_c(record.toJsonStringC, "public Record JSON conversion failed")
      json <- io.circe.parser.parse(text).fold(error => _invalid(error.message), Consequence.success)
      normalized <- _public_c(GoalPhasePlanRevision.normalizeJsonC(json), "public names are invalid or collide")
      proposal <- normalized.as[Proposal].fold(error => _invalid(error.message), Consequence.success)
    } yield Some(proposal) }

  /** Structured projection of an already validated proposal, without resolution. */
  def publicProposal(value: Option[Proposal]): Option[Record] =
    value.map(proposal => GoalPhaseNativeMetadata.record(proposal.asJson))

  private def _component_c(value: Component): Consequence[Unit] =
    _check(value != null && _identifier(value.identity) && _identifier(value.repository) &&
      WorkspaceId.parseC(value.workspace).isSuccess, "Component identifiers or logical workspace are invalid")

  private def _bounded(value: String, maximum: Int): Boolean =
    value != null && value.nonEmpty && UTF_8.newEncoder().canEncode(value) &&
      value.getBytes(UTF_8).length <= maximum
  private def _identifier(value: String): Boolean = _bounded(value, 1024) &&
    !value.exists(c => c.isControl || c.isWhitespace || Character.isSpaceChar(c) || c == '/' || c == '\\')
  private def _reference(value: ContextReference): Boolean = value != null &&
    Vector(value.identity, value.revision).forall(text =>
      _bounded(text, 1024) && text.trim.nonEmpty && !text.exists(_.isControl))
  private def _description(value: String): Boolean = _bounded(value, 16384) &&
    value.exists(c => !c.isWhitespace && !Character.isSpaceChar(c)) && !value.contains('\u0000')
  private def _path(value: String): Boolean = value != null &&
    !value.exists(c => c.isControl || c == ':' || c == '\\') && WorkspaceFilePath.parseC(value).isSuccess
  private def _unique[T](value: Vector[T], minimum: Int, maximum: Int)(valid: T => Boolean): Boolean =
    value != null && value.size >= minimum && value.size <= maximum &&
      value.forall(item => item != null && valid(item)) && value.distinct.size == value.size

  private def _decoder[T](raw: Decoder[T], keys: Set[String], validate: T => Consequence[Unit]): Decoder[T] =
    Decoder.instance { cursor => for {
      _ <- Either.cond(cursor.value.asObject.exists(fields => fields.keys.toSet == keys && fields.size == keys.size) &&
        _nonnull(cursor.value), (), DecodingFailure(
          "GoalPhase dependency scope: exact non-null business fields are required", cursor.history))
      value <- raw(cursor).left.map(error => DecodingFailure("GoalPhase dependency scope: " + error.message, error.history))
      _ <- Either.cond(validate(value).isSuccess, (),
        DecodingFailure("GoalPhase dependency scope: business input is invalid", cursor.history))
    } yield value }
  private def _nonnull(value: Json): Boolean = !value.isNull &&
    value.asObject.forall(_.values.forall(_nonnull)) && value.asArray.forall(_.forall(_nonnull))
  private def _public_c[T](value: Consequence[T], reason: String): Consequence[T] = value match {
    case Consequence.Success(result) => Consequence.success(result)
    case _ => _invalid(reason)
  }
  private def _invalid[T](reason: String): Consequence[T] =
    Consequence.stateConflict("GoalPhase dependency scope: " + reason)
  private def _check(valid: Boolean, reason: String): Consequence[Unit] =
    if (valid) Consequence.unit else _invalid(reason)
}
