package org.simplemodeling.textus.smworkflow.preparation

import io.circe.Json
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.simplemodeling.textus.smworkflow.admission.{GoalPhaseNativeMetadata, NativeExternalEvidence, ThreeWorkflowExternalEvidencePorts}
import org.simplemodeling.textus.smworkflow.value as V

/** Passive preparation of explicit Step/Phase permission input.
  * The responsible operator owns authorization and trusted provisioning.
  * @since   Oct. 14, 2026
  * @version Oct. 23, 2026
  */
object NativeAuthorityPreparation {
  final case class Prepared(recordIdentity: String, record: Record)

  private val _step_names = Vector("sourceRevision" -> "source_revision", "planRevision" -> "plan_revision",
    "reviewEvidence" -> "review_evidence")
  private val _phase_names = Vector("sourceRevision" -> "source_revision", "childInvocation" -> "child_invocation",
    "reviewedCandidate" -> "reviewed_candidate")

  def goalC(context: V.WorkflowAuthorityContext, reference: V.WorkflowArtifactReference,
    participant: String, issuer: String, occurredAt: Instant): Consequence[Prepared] = for {
    _ <- if (context != null && _exact(context.componentInstance) && context.scope != null &&
        reference != null && _exact(reference.identity) && _exact(reference.revision) &&
        _exact(participant) && _exact(issuer) && occurredAt != null) {
      Consequence.unit
    } else { _invalid("requires explicit context, owner, scope, reference, participant, issuer and time") }
    purpose <- Vector(NativeExternalEvidence.Purpose.StepClosure, NativeExternalEvidence.Purpose.PhaseClosure)
      .find(_.identity == context.purpose).map(Consequence.success).getOrElse(_invalid("has an unsupported purpose"))
    text <- context.scope.toJsonStringC
    scopeJson <- io.circe.parser.parse(text).fold(error => _invalid(error.message), Consequence.success)
    projected <- _project_c(scopeJson, if (purpose == NativeExternalEvidence.Purpose.StepClosure) _step_names else _phase_names)
    scope <- if (purpose == NativeExternalEvidence.Purpose.StepClosure) projected.hcursor.downField("admission").focus match {
      case Some(value) if !value.isNull => GoalPhaseNativeMetadata.normalizePublicJson(value)
        .fold(_invalid, actual => Consequence.success(projected.mapObject(_.add("admission", actual))))
      case _ => Consequence.success(projected)
    } else Consequence.success(projected)
    instance <- _string_c(scope, "instance")
    revision <- _string_c(scope, "sourceRevision")
    identity = ThreeWorkflowExternalEvidencePorts.key(instance, revision)
    binding = Json.obj("componentInstance" -> Json.fromString(context.componentInstance), "scope" -> scope)
    evidence = NativeExternalEvidence.Evidence(NativeExternalEvidence.schema, purpose.identity, identity,
      reference.identity, reference.revision, issuer, participant, binding, occurredAt.toString)
    encoded <- NativeExternalEvidence.encodeStorageC(evidence)
  } yield Prepared(NativeExternalEvidence.recordIdentity(purpose, identity), Record.data(NativeExternalEvidence.storageField -> encoded))

  private def _project_c(value: Json, names: Vector[(String, String)]): Consequence[Json] =
    value.asObject.map { fields =>
      names.foldLeft(Consequence.success(fields)) { case (prior, (declaration, wire)) => prior.flatMap { selected =>
        (selected(declaration), selected(wire)) match {
          case (Some(left), Some(right)) if left != right => _invalid("has contradictory names for " + declaration)
          case (_, Some(right)) => Consequence.success(selected.remove(wire).add(declaration, right))
          case _ => Consequence.success(selected)
        }
      }}.map(Json.fromJsonObject)
    }.getOrElse(_invalid("requires a structured scope object"))

  private def _string_c(value: Json, key: String): Consequence[String] = {
    value.hcursor.get[String](key).fold(_ => _invalid("requires String " + key), Consequence.success)
  }
  private def _exact(value: String): Boolean = {
    value != null && value.nonEmpty && value.trim == value && !value.exists(_.isControl)
  }
  private def _invalid[A](reason: String): Consequence[A] = { Consequence.stateConflict("Authority preparation " + reason) }
}
